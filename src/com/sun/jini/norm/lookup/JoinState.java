/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sun.jini.norm.lookup;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.rmi.MarshalledObject;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LogRecord;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.NoSuchEntryException;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.DiscoveryLocatorManagement;
import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lookup.JoinManager;
import net.jini.security.ProxyPreparer;

import com.sun.jini.config.Config;
import com.sun.jini.logging.Levels;
import com.sun.jini.reliableLog.LogHandler;
import com.sun.jini.reliableLog.ReliableLog;

/**
 * Utility class that combines <code>JoinManager</code> with persistence.
 *
 * @author Sun Microsystems, Inc.
 */
public class JoinState extends LogHandler implements SubStore {

    /** Logger and configuration component name for Norm */
    private static final String NORM = "com.sun.jini.norm";

    /** Logger for logging messages */
    private static final Logger logger = Logger.getLogger(NORM);

    /** Service we are registering with lookup services */
    final Object service;

    /**
     * Lease renewal manager (if any) that the client wants our
     * <code>JoinManager</code> to use.
     */
    private final LeaseRenewalManager lrm;

    /** Configuration, to supply initial attributes, groups, and locators */
    private final Configuration config;

    /** Attributes supplied by the service */
    private final Entry[] serviceAttributes;

    /** Proxy preparer for recovered lookup locators */
    private final ProxyPreparer recoveredLookupLocatorPreparer;

    /** The service ID, derived from the service's UUID. */
    private final ServiceID serviceID;

    /**
     * Log we are using to persist our state to disk, or null if not
     * persistent.
     */
    private ReliableLog log;

    /**
     * Set to true if there was existing persistent data that was recovered --
     * used to determine if the JoinState is being created for the first time.
     */
    private boolean recoveredData;

    /**
     * Pass through to get attribute array from the log recovery method 
     * to the code that creates the <code>JoinManager</code>
     */
    private Entry[] attributes;

    /**
     * Pass through to get lookup group array from the log
     * recovery method to the code that creates the
     * <code>JoinManager</code> 
     */
    private String groups[] = DiscoveryGroupManagement.NO_GROUPS;
    
    /**
     * Pass through to get lookup locator array from the log
     * recovery method to the code that creates the
     * <code>JoinManager</code>
     */
    private LookupLocator locators[];

    /**
     * The <code>DiscoveryManagement</code> we are using to find lookups, which
     * must also implement <code>DiscoveryGroupManagement</code> and
     * <code>DiscoveryLocatorManagement</code>.
     */
    private DiscoveryManagement dm;

    /** Our join manager */
    private JoinManager joinMgr;

    /**
     * Simple constructor.
     * @param service the object to register with lookup
     * @param lrm a <code>LeaseRenewalManager</code> to pass to the
     *        <code>JoinManager</code>.  May be <code>null</code>.
     * @param config a configuration that supplies initial attributes, groups,
     *	      and locators
     * @param serviceAttributes attributes supplied by the service
     * @param recoveredLookupLocatorPreparer proxy preparer for recovered
     *	      lookup locators
     * @param serviceID the service ID for the service
     */
    public JoinState(Object service,
		     LeaseRenewalManager lrm,
		     Configuration config,
		     Entry[] serviceAttributes,
		     ProxyPreparer recoveredLookupLocatorPreparer,
		     ServiceID serviceID)
	throws IOException
    {
	this.service = service;
	this.lrm = lrm;
	this.config = config;
	this.serviceAttributes = serviceAttributes;
	this.recoveredLookupLocatorPreparer = recoveredLookupLocatorPreparer;
	this.serviceID = serviceID;
    }

    ////////////////////////////////////////////////
    // Methods needed to meet the SubStore interface

    // Inherit JavaDoc from super-type
    public String subDirectory() {
	return "JoinState";
    }

    // Inherit JavaDoc from super-type
    public void setDirectory(File dir)
	throws IOException, ConfigurationException
    {
	if (dir != null) {
	    try {
		log = new ReliableLog(dir.getCanonicalPath(), this);
		log.recover();
	    } catch (IOException e) {
		IOException e2 = new IOException(
		    "Log is corrupted: " + e.getMessage());
		e2.initCause(e);
		throw e2;
	    }
	}
	if (!recoveredData) {
	    /*
	     * Get initial attributes, groups, and locators, if not retrieved
	     * from persistent storage.
	     */
	    getInitialEntries();
	} else {
	    /*
	     * Prepare recovered lookup locators, dropping ones for which
	     * preparation fails.
	     */
	    List prepared = new LinkedList();
	    for (int i = locators.length; --i >= 0; ) {
		try {
		    prepared.add(
			recoveredLookupLocatorPreparer.prepareProxy(
			    locators[i]));
		} catch (Throwable t) {
		    if (logger.isLoggable(Level.INFO)) {
			logThrow(Level.INFO, "setDirectory",
				 "Problem preparing lookup locator {0} -- " +
				 "discarding",
				 new Object[] { locators[i] },
				 t);
		    }
		}
	    }
	    locators = (LookupLocator[]) prepared.toArray(
		new LookupLocator[prepared.size()]);
	}

	// Create DiscoveryManager
	createDiscoveryManager();

	// Create JoinManager
	try {
	    joinMgr = new JoinManager(service, attributes, serviceID, dm, lrm,
				      config);
	} catch (IOException e) {
	    IOException e2 = new IOException(
		"Problem starting JoinManager: " + e.getMessage());
	    e2.initCause(e2);
	    throw e2;
	}

	// For now we are treating the state of the
	// JoinManager/LookupDiscoveryManager as truth for our
	// log, now that we have a lookup manager, force it into
	// sync with our log
	try {
	    takeSnapshot();
	} catch (IOException e) {
	    logger.log(Level.WARNING,
		       "Ignoring problem creating initial snapshot",
		       e);
	}
    }

    /** Logs a throw */
    private static void logThrow(Level level, String method,
				 String msg, Object[] msgParams, Throwable t)
    {
	LogRecord r = new LogRecord(level, msg);
	r.setLoggerName(logger.getName());
	r.setSourceClassName(JoinState.class.getName());
	r.setSourceMethodName(method);
	r.setParameters(msgParams);
	r.setThrown(t);
	logger.log(r);
    }

    /**
     * Returns a configuration entry that is an array of objects, checking that
     * all the elements of the array are non-null.
     */
    private Object[] getArrayEntry(String name,
				   Class type,
				   Object defaultValue)
	throws ConfigurationException
    {
	Object[] result = (Object[]) config.getEntry(
	    NORM, name, type, defaultValue);
	if (result != null) {
	    for (int i = result.length; --i >= 0; ) {
		if (result[i] == null) {
		    throw new ConfigurationException(
			"Entry for component " + NORM + ", name " + name +
			" must not contain null elements");
		}
	    }
	}
	return result;
    }

    /**
     * Retrieves the initial values for attributes, groups, and locators from
     * the configuration.
     */
    private void getInitialEntries() throws ConfigurationException {
	attributes = (Entry[]) getArrayEntry(
	    "initialLookupAttributes", Entry[].class, null);
	if (attributes == null || attributes.length == 0) {
	    attributes = serviceAttributes;
	} else {
	    Entry[] temp = new Entry[
		serviceAttributes.length + attributes.length];
	    System.arraycopy(serviceAttributes, 0, temp, 0,
			     serviceAttributes.length);
	    System.arraycopy(attributes, 0, temp, serviceAttributes.length,
			     attributes.length);
	    attributes = temp;
	}
	groups = (String[]) getArrayEntry(
	    "initialLookupGroups", String[].class, new String[] { "" });
	locators = (LookupLocator[]) getArrayEntry(
	    "initialLookupLocators", LookupLocator[].class, null);
	if (locators == null) {
	    locators = new LookupLocator[0];
	}
    }

    /** Creates the discovery manager. */
    private void createDiscoveryManager()
	throws ConfigurationException, IOException
    {
	try {
	    dm = (DiscoveryManagement) Config.getNonNullEntry(
		config, NORM, "discoveryManager", DiscoveryManagement.class);
	    if (!(dm instanceof DiscoveryGroupManagement)) {
		throw new ConfigurationException(
		    "Entry for component " + NORM +
		    ", name discoveryManager must implement " +
		    "net.jini.discovery.DiscoveryGroupManagement");
	    }
	    String[] groups = ((DiscoveryGroupManagement) dm).getGroups();
	    if (groups == null || groups.length != 0) {
		throw new ConfigurationException(
		    "Entry for component " + NORM +
		    ", name discoveryManager must be configured with no " +
		    "groups");
	    } else if (!(dm instanceof DiscoveryLocatorManagement)) {
		throw new ConfigurationException(
		    "Entry for component " + NORM +
		    ", name discoveryManager must implement " +
		    "net.jini.discovery.DiscoveryLocatorManagement");
	    } else if (((DiscoveryLocatorManagement) dm).getLocators().length
		       != 0)
	    {
		throw new ConfigurationException(
		    "Entry for component " + NORM +
		    ", name discoveryManager must be configured with no " +
		    "locators");
	    } 
	    ((DiscoveryGroupManagement) dm).setGroups(groups);
	    ((DiscoveryLocatorManagement) dm).setLocators(locators);
	} catch (NoSuchEntryException e) {
	    dm = new LookupDiscoveryManager(groups, locators, null, config);
	}
    }

    // Inherit JavaDoc from super-type
    public void prepareDestroy() {
	try {
	    if (log != null)
		log.close();
	} catch (IOException e) {
	    logger.log(Levels.HANDLED,
		       "Ignoring problem closing log during destroy", e);
	}
    }

    /**
     * Terminate our participation in the Join and discovery
     * Protocols.  Note, this method leaves the logs intact.    
     */
    public void terminateJoin() {
	// Terminate the JoinManager first so it will not call
	// into the dm after it has been terminated.
	if (joinMgr != null)
	    joinMgr.terminate();

	if (dm != null)
	    dm.terminate();
    }

    //////////////////////////////////////////////////
    // Methods needed to meet the LogHandler interface
	
    // Inherit doc comment from super interface
    public void snapshot(OutputStream out) throws IOException {
	ObjectOutputStream oostream = new ObjectOutputStream(out);
	writeAttributes(joinMgr.getAttributes(), oostream);
	oostream.writeObject(((DiscoveryGroupManagement) dm).getGroups());
	oostream.writeObject(((DiscoveryLocatorManagement) dm).getLocators());
	oostream.flush();
    }

    // Inherit doc comment from super interface
    public void recover(InputStream in) throws Exception {
	ObjectInputStream oistream = new ObjectInputStream(in);
	attributes = readAttributes(oistream);
	groups     = (String[]) oistream.readObject();
	locators   = (LookupLocator[]) oistream.readObject();
	recoveredData = true;
    }
    
    /**
     * This method always throws <code>UnsupportedOperationException</code>
     * since <code>JoinState</code> should never update a log.
     */
    public void applyUpdate(Object update) throws Exception {
	throw new UnsupportedOperationException(
	    "Recovering log update -- this should not happen");
    }

    /**
     * Utility method to write out an array of entities to an
     * <code>ObjectOutputStream</code>.  Can be recovered by a call
     * to <code>readAttributes()</code>
     * <p>
     * Packages each attribute in its own <code>MarshalledObject</code> so 
     * a bad codebase on an attribute class will not corrupt the whole array.
     */
    static private void writeAttributes(Entry[] attributes,
					ObjectOutputStream out) 
	throws IOException
    {
	// Need to package each attribute in its own marshalled object.
	// This makes sure that the attribute's codebase is preserved
	// and, when we unpack, that we can discard attributes whose codebase
	// has been lost without throwing away those we can still deal with. 
	
	out.writeInt(attributes.length);
	for (int i=0; i<attributes.length; i++) {
	    out.writeObject(new MarshalledObject(attributes[i]));
	}
    }

    /**
     * Utility method to read in an array of entities from a
     * <code>ObjectInputStream</code>.  Array should have been written 
     * by a call to <code>writeAttributes()</code>.
     * <p>
     * Will try and recover as many attributes as possible.
     * Attributes which can't be recovered won't be returned but they
     * will remain in the log (though they will be lost when the next
     * snapshot is taken).
     */
    static private Entry[] readAttributes(ObjectInputStream in) 
	throws IOException, ClassNotFoundException
    {
	final List entries = new LinkedList();
	final int objectCount = in.readInt();
	for (int i=0; i<objectCount; i++) {
	    try {
		MarshalledObject mo = (MarshalledObject) in.readObject();
		entries.add(mo.get());
	    } catch (IOException e) {
		logger.log(Level.INFO,
			   "Problem recovering attribute -- discarding",
			   e);
	    } catch (ClassNotFoundException e) {
		logger.log(Level.INFO,
			   "Problem recovering attribute -- discarding",
			   e);
	    }
	}
	return (Entry[]) entries.toArray(new Entry[0]);
    }

    /**
     * Used by all the methods that change persistent state to
     * commit the change to disk
     */
    private void takeSnapshot() throws IOException {
	if (log == null) {
	    return;
	}
	synchronized (log) {
	    log.snapshot();
	}
    }

    /////////////////////////////////////////////////////////////////
    // Effectively a clone of the JoinAdmin interface, used by
    // the client to implement JoinAdmin and to set its attributes

    /**
     * Get the list of groups to join.  An empty array means the service
     * joins no groups (as opposed to "all" groups).
     *
     * @return an array of groups to join. An empty array means the service
     *         joins no groups (as opposed to "all" groups).
     */
    public String[] getGroups() {
	return ((DiscoveryGroupManagement) dm).getGroups();
    }

    /**
     * Add new groups to the set to join.  Lookup services in the new
     * groups will be discovered and joined.
     *
     * @param groups groups to join
     */
    public void addGroups(String[] groups) {
 	try {
	    ((DiscoveryGroupManagement) dm).addGroups(groups);
	} catch (IOException e) {
	    throw new RuntimeException(
		"Could not change groups: " + e.getMessage(), e);
	}

 	try {
	    takeSnapshot();
	} catch (IOException e) {
	    throw new RuntimeException(
		"Could not log change: " + e.getMessage(), e);
	}
    }

    /**
     * Remove groups from the set to join.  Leases are cancelled at lookup
     * services that are not members of any of the remaining groups.
     *
     * @param groups groups to leave
     */
    public void removeGroups(String[] groups) {
	((DiscoveryGroupManagement) dm).removeGroups(groups);

	try {
	    takeSnapshot();
	} catch (IOException e) {
	    throw new RuntimeException(
		"Could not log change: " + e.getMessage(), e);
	}
    }

    /**
     * Replace the list of groups to join with a new list.  Leases are
     * cancelled at lookup services that are not members of any of the
     * new groups.  Lookup services in the new groups will be discovered
     * and joined.
     *
     * @param groups groups to join
     */
    public void setGroups(String[] groups) {
	try {
	    ((DiscoveryGroupManagement) dm).setGroups(groups);
	} catch (IOException e) {
	    throw new RuntimeException(
		"Could not change groups: " + e.getMessage(), e);
	}

	try {
	    takeSnapshot();
	} catch (IOException e) {
	    throw new RuntimeException(
		"Could not log change: " + e.getMessage(), e);
	}
    }

    /** 
     * Get the list of locators of specific lookup services to join. 
     *
     * @return the list of locators of specific lookup services to join
     */    
    public LookupLocator[] getLocators() {
	return ((DiscoveryLocatorManagement) dm).getLocators();
    }

    /**
     * Add locators for specific new lookup services to join.  The new
     * lookup services will be discovered and joined.
     *
     * @param locators locators of specific lookup services to join
     */
    public void addLocators(LookupLocator[] locators) {
	((DiscoveryLocatorManagement) dm).addLocators(locators);
	try {
	    takeSnapshot();
	} catch (IOException e) {
	    throw new RuntimeException(
		"Could not log change: " + e.getMessage(), e);
	}
    }

    /**
     * Remove locators for specific lookup services from the set to join.
     * Any leases held at the lookup services are cancelled.
     *
     * @param locators locators of specific lookup services to leave
     */   
    public void removeLocators(LookupLocator[] locators) {
	((DiscoveryLocatorManagement) dm).removeLocators(locators);
	try {
	    takeSnapshot();
	} catch (IOException e) {
	    throw new RuntimeException(
		"Could not log change: " + e.getMessage(), e);
	}
    }

   /**
     * Replace the list of locators of specific lookup services to join
     * with a new list.  Leases are cancelled at lookup services that were
     * in the old list but are not in the new list.  Any new lookup services
     * will be discovered and joined.
     *
     * @param locators locators of specific lookup services to join
     */
    public void setLocators(LookupLocator[] locators) {
	((DiscoveryLocatorManagement) dm).setLocators(locators);
	try {
	    takeSnapshot();
	} catch (IOException e) {
	    throw new RuntimeException(
		"Could not log change: " + e.getMessage(), e);
	}
    }

    /** 
     * Get the current attribute sets for the service. 
     * 
     * @return the current attribute sets for the service
     */
    public Entry[] getAttributes() {
	return joinMgr.getAttributes();
    }


    /** 
     * Add attribute sets for the service.  The resulting set will be used
     * for all future joins.  The attribute sets are also added to all 
     * currently-joined lookup services.
     *
     * @param attrSets the attribute sets to add
     * @param checkSC  <code>boolean</code> flag indicating whether the
     *                 elements of the set of attributes to add should be
     *                 checked to determine if they are service controlled
     * @throws SecurityException when the <code>checkSC</code> parameter is
     *         <code>true</code>, and at least one of the attributes to be
     *         added is an instance of the <code>ServiceControlled</code>
     *         marker interface
     */
    public void addAttributes(Entry[] attrSets, boolean checkSC) {
	joinMgr.addAttributes(attrSets, checkSC);
	try {
	    takeSnapshot();
	} catch (IOException e) {
	    throw new RuntimeException(
		"Could not log change: " + e.getMessage(), e);
	}
    }

    /**  
     * Modify the current attribute sets, using the same semantics as
     * ServiceRegistration.modifyAttributes.  The resulting set will be used
     * for all future joins.  The same modifications are also made to all 
     * currently-joined lookup services.
     *
     * @param attrSetTemplates the templates for matching attribute sets
     * @param attrSets the modifications to make to matching sets
     * @param checkSC <code>boolean</code> flag indicating whether
     *	      elements of the set of attributes to add should be checked
     *	      to determine if they are service controlled
     * @throws SecurityException when the <code>checkSC</code> parameter
     *         is <code>true</code>, and at least one of the attributes
     *         to be added is an instance of the
     *         <code>ServiceControlled</code> marker interface
     * @see net.jini.core.lookup.ServiceRegistration#modifyAttributes
     */
    public void modifyAttributes(Entry[] attrSetTemplates,
				 Entry[] attrSets,
				 boolean checkSC)
    {
	joinMgr.modifyAttributes(attrSetTemplates, attrSets, checkSC);

	try {
	    takeSnapshot();
	} catch (IOException e) {
	    throw new RuntimeException(
		"Could not log change: " + e.getMessage(), e);
	}
    }
}
