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
package com.sun.jini.outrigger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.DiscoveryLocatorManagement;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.lookup.JoinManager;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.security.ProxyPreparer;

import com.sun.jini.config.Config;
import com.sun.jini.logging.Levels;

/**
 * <code>JoinStateManager</code> provides a utility that manages
 * a service's join state (optionally persisting that state) and
 * manages the join protocol protocol on behalf of the service.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see JoinManager
 */
// @see JoinAdminState
class JoinStateManager implements StorableObject {
    /** <code>ProxyPreparer</code> for <code>LookupLocators</code> */
    private ProxyPreparer lookupLocatorPreparer;

    /**
     * Object used to find lookups. Has to implement DiscoveryManagement
     * and DiscoveryLocatorManagement as well as DiscoveryGroupManagement.
     */
    private DiscoveryGroupManagement dgm;

    /**
     * <code>JoinManager</code> that is handling the details of binding
     * into Jini lookup services.
     */
    private JoinManager  mgr;

    /**
     * The object that is coordinating our persistent state.
     */
    private LogOps log;

    /**
     * The list of attributes to start with. This field is only used
     * to carry data from the <code>restore</code> method to the
     * <code>startManager</code> method. The current set of attributes
     * is kept by <code>mgr</code>. This field is nulled out by
     * <code>startManager</code>.
     */
    private Entry[]		attributes;

    /**
     * The list of <code>LookupLocator</code>s to start with. This
     * field is only used to carry data from the <code>restore</code>
     * method to the <code>startManager</code> method. The current set
     * of attributes is kept by <code>mgr</code>. This field is nulled
     * out by <code>startManager</code>.  
     */
    private LookupLocator	locators[];


    /**
     * The list of group names to start with. This field is only used
     * to carry data from the <code>restore</code> method to the
     * <code>startManager</code> method. The current set of attributes
     * is kept by <code>mgr</code>. This field is nulled out by
     * <code>startManager</code>.
     */
    private String		groups[];

    /**
     * Conceptually, true if this is the first time this
     * service has come up, implemented as if there was
     * no previous state then this is the first time.
     */
    private boolean initial = true;

    /** Logger for logging join related information */
    private static final Logger logger = 
	Logger.getLogger(OutriggerServerImpl.joinLoggerName);
    
    /**
     * Simple constructor.
     */
    JoinStateManager() { }

    /**
     * Start the manager. Start looking for lookup and registering
     * with them.
     * @param config object to use to obtain
     *               <code>DiscoveryManagement</code> object, and if
     *               this is the initial incarnation of this service, 
     *               the object used to get the initial set of groups,
     *               locators, and deployer defined attributes.
     * @param log object used to persist the manager's state, may be
     *            <code>null</code>.
     * @param serviceID The <code>ServiceID</code> to register
     *                  under.
     * @param service The proxy object to register with lookups.
     * @param baseAttributes Any attributes the implementation wants
     *                       attached, only used if this is the
     *                       initial incarnation.
     * @throws IOException if the is problem persisting the 
     *         initial state or in starting discovery.
     * @throws ConfigurationException if the configuration
     *         is invalid.
     * @throws NullPointerException if <code>config</code>, 
     *         or <code>serviceID</code> is  <code>null</code>.
     */
    void startManager(Configuration config, LogOps log, Object service,
		      ServiceID serviceID, Entry[] baseAttributes) 
	throws IOException, ConfigurationException
    {
	// Default do nothing preparer
	final ProxyPreparer defaultPreparer = 
	    new net.jini.security.BasicProxyPreparer();

	if (serviceID == null)
	    throw new NullPointerException("serviceID can't be null");

	this.log = log;

	lookupLocatorPreparer = 
	    (ProxyPreparer)Config.getNonNullEntry(config, 
		OutriggerServerImpl.COMPONENT_NAME, "lookupLocatorPreparer",
		ProxyPreparer.class, defaultPreparer);

	dgm = (DiscoveryGroupManagement)
	    Config.getNonNullEntry(config, 
		OutriggerServerImpl.COMPONENT_NAME, "discoveryManager",
		DiscoveryGroupManagement.class, 
		new LookupDiscoveryManager(
                    DiscoveryGroupManagement.NO_GROUPS, null, null,
		    config));

	if (!(dgm instanceof DiscoveryManagement))
	    throw throwNewConfigurationException("Entry for component " +
		OutriggerServerImpl.COMPONENT_NAME + ", name " +
		"discoveryManager must implement " +
	        "net.jini.discovery.DiscoveryGroupManagement");

	if (!(dgm instanceof DiscoveryLocatorManagement))
	    throw throwNewConfigurationException("Entry for component " +
		OutriggerServerImpl.COMPONENT_NAME + ", name " +
		"discoveryManager must implement " +
		"net.jini.discovery.DiscoveryLocatorManagement");

	final String[] toCheck = dgm.getGroups();
	if (toCheck == null || toCheck.length != 0)
	    throw throwNewConfigurationException("Entry for component " +
		OutriggerServerImpl.COMPONENT_NAME + ", name " +
		"discoveryManager must be initially configured with no " +
                "groups");

	if (((DiscoveryLocatorManagement)dgm).getLocators().length != 0)
	    throw throwNewConfigurationException("Entry for component " +
                OutriggerServerImpl.COMPONENT_NAME + ", name " +
		"discoveryManager must be initially configured with no " +
		"locators");

	// if this is the first incarnation, consult config for groups,
	// locators and attributes.
	if (initial) {
	    groups = (String[])
		config.getEntry(OutriggerServerImpl.COMPONENT_NAME,
		    "initialLookupGroups", String[].class,
		    new String[]{""});

	    locators = (LookupLocator[])
		Config.getNonNullEntry(config, 
		    OutriggerServerImpl.COMPONENT_NAME,
                    "initialLookupLocators", LookupLocator[].class, 
                    new LookupLocator[0]);

	    final Entry[] cAttrs = (Entry[])
		Config.getNonNullEntry(config, 
                    OutriggerServerImpl.COMPONENT_NAME,
		    "initialLookupAttributes", Entry[].class, new Entry[0]);

	    if (cAttrs.length == 0) {
		attributes = baseAttributes;
	    } else {
		attributes = 
		    new Entry[cAttrs.length + baseAttributes.length];
		System.arraycopy(baseAttributes, 0, attributes, 
				 0, baseAttributes.length);
		System.arraycopy(cAttrs, 0, attributes, 
				 baseAttributes.length, cAttrs.length);
	    }
	} else {
	    /* recovery : if there are any locators get and
	     * use recoveredLookupLocatorPreparer
	     */
	    if (locators.length > 0) {
		final ProxyPreparer recoveredLookupLocatorPreparer = 
		    (ProxyPreparer)Config.getNonNullEntry(config, 
		        OutriggerServerImpl.COMPONENT_NAME,
			"recoveredLookupLocatorPreparer", ProxyPreparer.class,
			 defaultPreparer);

		final List prepared = new java.util.LinkedList();
		for (int i=0; i<locators.length; i++) {
		    final LookupLocator locator = locators[i];
		    try {
			prepared.add(recoveredLookupLocatorPreparer.
				     prepareProxy(locator));
		    } catch (Throwable t) {
			logger.log(Level.INFO,
			    "Encountered exception preparing lookup locator " +
			    "for " + locator + ", dropping locator", t);
		    }
		}

		locators = 
		    (LookupLocator[])prepared.toArray(new LookupLocator[0]);
	    }
	}

	// Now that we have groups & locators (either from 
	// a previous incarnation or from the config) start discovery.
	if (logger.isLoggable(Level.CONFIG)) {
	    if (groups == null) {
		logger.log(Level.CONFIG, "joining all groups");
	    } else if (groups.length == 0) {
		logger.log(Level.CONFIG, "joining no groups");	
	    } else {
		final StringBuffer buf = new StringBuffer();
		buf.append("joining groups:");
		for (int i=0; i<groups.length; i++) {
		    if (i != 0)
			buf.append(",");

		    buf.append("\"");
		    buf.append(groups[i]);
		    buf.append("\"");
		}
		logger.log(Level.CONFIG, buf.toString());
	    }

	    if (locators.length == 0) {
		logger.log(Level.CONFIG, "joining no specific registrars");
	    } else {
		final StringBuffer buf = new StringBuffer();
		buf.append("joining the specific registrars:");
		for (int i=0; i<locators.length; i++) {
		    if (i != 0)
			buf.append(", ");

		    buf.append(locators[i]);
		}
		logger.log(Level.CONFIG, buf.toString());
	    }

	    if (attributes.length == 0) {
		logger.log(Level.CONFIG, "registering no attributes");
	    } else {
		final StringBuffer buf = new StringBuffer();
		buf.append("registering the attributes:");
		for (int i=0; i<attributes.length; i++) {
		    if (i != 0)
			buf.append(", ");

		    buf.append(attributes[i]);
		}
		logger.log(Level.CONFIG, buf.toString());		
	    }
	}

	dgm.setGroups(groups);
	((DiscoveryLocatorManagement)dgm).setLocators(locators);

	mgr = new JoinManager(service, attributes, serviceID, 
			      (DiscoveryManagement)dgm, null, config);

	// Once we are running we don't need the attributes,
	// locators, and groups fields, null them out (the
	// state is in the mgr and dgm.
	attributes = null;
	groups = null;
	locators = null;

	// Now that we have state, make sure it is written to disk.
	update();
    }

    /** 
     * Make a good faith attempt to terminate
     * discovery, and cancel any lookup registrations.  */
    public void destroy() {
	// Unregister with lookup

	// Terminate the JoinManager first so it will not call
	// into the dgm after it has been terminated.
	if (mgr != null)
	    mgr.terminate();

	if (dgm != null) 
	    ((DiscoveryManagement)dgm).terminate();
    }

    /* Basically we are implementing JoinAdmin, for get methods we just
     * delegate to JoinManager, for the set methods we call
     * JoinManager to and then persist the change by calling the
     * appropriate method on our JoinAdminState.  If the call on our
     * JoinAdminState throws an IOException we throw a runtime
     * exception since JoinAdmin methods don't let us throw a
     * IOException 
     */

    /** 
     * Get the current attribute sets for the service. 
     * 
     * @return the current attribute sets for the service
     */
    public Entry[] getLookupAttributes() {
	return mgr.getAttributes();
    }

    /** 
     * Add attribute sets for the service.  The resulting set will be used
     * for all future joins.  The attribute sets are also added to all 
     * currently-joined lookup services.
     *
     * @param attrSets the attribute sets to add
     * @throws java.rmi.RuntimeException if the change can not be persisted.
     */
    public void addLookupAttributes(Entry[] attrSets) {
	mgr.addAttributes(attrSets, true);
	update();
    }

    /**  
     * Modify the current attribute sets, using the same semantics as
     * ServiceRegistration.modifyAttributes.  The resulting set will be used
     * for all future joins.  The same modifications are also made to all 
     * currently-joined lookup services.
     *
     * @param attrSetTemplates the templates for matching attribute sets
     * @param attrSets the modifications to make to matching sets
     *     
     * @throws java.rmi.RuntimeException if the change can not be persisted.
     * @see net.jini.core.lookup.ServiceRegistration#modifyAttributes
     */
    public void modifyLookupAttributes(Entry[] attrSetTemplates,
				       Entry[] attrSets) {
	mgr.modifyAttributes(attrSetTemplates, attrSets, true);
	update();
    }

   /**
     * Get the list of groups to join.  An empty array means the service
     * joins no groups (as opposed to "all" groups).
     *
     * @return an array of groups to join. An empty array means the service
     *         joins no groups (as opposed to "all" groups).
     * @see #setLookupGroups
     */
    public String[] getLookupGroups() {
	return dgm.getGroups();
    }

    /**
     * Add new groups to the set to join.  Lookup services in the new
     * groups will be discovered and joined.
     *
     * @param groups groups to join
     * @throws java.rmi.RuntimeException if the change can not be persisted.
     * @see #removeLookupGroups
     */
    public void addLookupGroups(String[] groups) {
 	try {
	    dgm.addGroups(groups);
	} catch (IOException e) {
	    throw propagateIOException("Could not change groups", e);
	}
	update();
    }

    /**
     * Remove groups from the set to join.  Leases are cancelled at lookup
     * services that are not members of any of the remaining groups.
     *
     * @param groups groups to leave
     * @throws java.rmi.RuntimeException if the change can not be persisted.
     * @see #addLookupGroups
     */
    public void removeLookupGroups(String[] groups) {
	dgm.removeGroups(groups);
	update();
    }

    /**
     * Replace the list of groups to join with a new list.  Leases are
     * cancelled at lookup services that are not members of any of the
     * new groups.  Lookup services in the new groups will be discovered
     * and joined.
     *
     * @param groups groups to join
     * @throws java.rmi.RuntimeException if the change can not be persisted.
     * @see #getLookupGroups
     */
    public void setLookupGroups(String[] groups) {
	try {
	    dgm.setGroups(groups);
	} catch (IOException e) {
	    throw propagateIOException("Could not change groups", e);
	}
	update();
    }
    
    /** 
     * Get the list of locators of specific lookup services to join. 
     *
     * @return the list of locators of specific lookup services to join
     * @see #setLookupLocators
     */
    public LookupLocator[] getLookupLocators() {
	return ((DiscoveryLocatorManagement)dgm).getLocators();
    }

    /**
     * Add locators for specific new lookup services to join.  The new
     * lookup services will be discovered and joined.
     *
     * @param locators locators of specific lookup services to join
     * @throws java.rmi.RuntimeException if the change can not be persisted.
     * @see #removeLookupLocators
     */
    public void addLookupLocators(LookupLocator[] locators) 
        throws RemoteException
    {
	prepareLocators(locators);
	((DiscoveryLocatorManagement)dgm).addLocators(locators);
	update();
    }

    /**
     * Remove locators for specific lookup services from the set to join.
     * Any leases held at the lookup services are cancelled.
     *
     * @param locators locators of specific lookup services to leave
     * @throws java.rmi.RuntimeException if the change can not be persisted.
     * @see #addLookupLocators
     */
    public void removeLookupLocators(LookupLocator[] locators) 
        throws RemoteException
    {
	prepareLocators(locators);
	((DiscoveryLocatorManagement)dgm).removeLocators(locators);
	update();
    }

    /**
     * Replace the list of locators of specific lookup services to join
     * with a new list.  Leases are cancelled at lookup services that were
     * in the old list but are not in the new list.  Any new lookup services
     * will be discovered and joined.
     *
     * @param locators locators of specific lookup services to join
     * @throws java.rmi.RuntimeException if the change can not be persisted.
     * @see #getLookupLocators
     */
    public void setLookupLocators(LookupLocator[] locators) 
        throws RemoteException
    {
	prepareLocators(locators);
	((DiscoveryLocatorManagement)dgm).setLocators(locators);
	update();
    }

    private void update() {
	if (log != null)
	    log.joinStateOp(this);
    }

    /**
     * Apply <code>lookupLocatorPreparer</code> to each locator in the
     * array, replacing the original locator with the result of the
     * <code>prepareProxy</code> call. If call fails with an exception
     * throw that exception.  
     * @param locators the <code>LookupLocator</code>s to be prepared.
     * @throws RemoteException if preparation of any of the locators
     *         does.
     * @throws SecurityException if preparation of any of the locators
     *         does.
     */
    private void prepareLocators(LookupLocator[] locators) 
        throws RemoteException 
    {
	for (int i = 0; i<locators.length; i++) 
	    locators[i] = (LookupLocator)lookupLocatorPreparer.prepareProxy(
		locators[i]);
    }

    /**
     * Utility method to write out an array of entities to an
     * <code>ObjectOutputStream</code>.  Can be recovered by a call
     * to <code>readAttributes()</code>
     * <p>
     * Packages each attribute in its own <code>MarshalledObject</code> so
     * a bad codebase on an attribute class will not corrupt the whole array.
     */
    // @see JoinAdminActivationState#readAttributes
    static private void writeAttributes(Entry[] attributes,
                                        ObjectOutputStream out)
        throws IOException
    {
        // Need to package each attribute in its own marshaled object,
        // this makes sure that the attribute's code base is preserved
        // and when we unpack to discard attributes who's codebase
        // has been lost without throwing away those we can still deal with.
         
        out.writeInt(attributes.length);
        for (int i=0; i<attributes.length; i++) {
            out.writeObject(new MarshalledObject(attributes[i]));
	}
    }
 
    /**
     * Utility method to read in an array of entities from a
     * <code>ObjectInputStream</code>.  Array should have been written
     * by a call to <code>writeAttributes()</code>
     * <p>
     *   
     * Will try and recover as many attributes as possible.
     * Attributes which can't be recovered won't be returned but they
     * will remain in the log.
     */
    // @see JoinAdminActivationState#writeAttributes
    static private Entry[] readAttributes(ObjectInputStream in)
        throws IOException, ClassNotFoundException
    {
        final List entries = new java.util.LinkedList();
        final int objectCount = in.readInt();
        for (int i=0; i<objectCount; i++) {
            try {
                MarshalledObject mo = (MarshalledObject)in.readObject();
                entries.add(mo.get());
            } catch (IOException e) {
		logger.log(Level.INFO, "Encountered IOException recovering " +
                    "attribute, dropping attribute", e);
            } catch (ClassNotFoundException e) {
		logger.log(Level.INFO, "Encountered ClassNotFoundException " +
		    "recovering attribute, dropping attribute", e);
            }
        }
 
        return (Entry[])entries.toArray(new Entry[0]);
    }

    // -----------------------------------
    //  Methods required by StorableObject
    // -----------------------------------
 
    // inherit doc comment
    public void store(ObjectOutputStream out) throws IOException {
        writeAttributes(mgr.getAttributes(), out);
        out.writeObject(((DiscoveryLocatorManagement)dgm).getLocators());
        out.writeObject(dgm.getGroups());
    }

    // inherit doc comment
    public void restore(ObjectInputStream in) 
	throws IOException, ClassNotFoundException 
    {
	initial = false;
        attributes = readAttributes(in);
        locators   = (LookupLocator [])in.readObject();
        groups     = (String [])in.readObject();
    }

    /**
     * Construct, log, and throw a new ConfigurationException with
     * the given message.
     */
    private static ConfigurationException throwNewConfigurationException(
            String msg) 
	throws ConfigurationException
    {
	final ConfigurationException e = new ConfigurationException(msg);

	if (logger.isLoggable(Levels.FAILED)) {
	    logger.log(Levels.FAILED, msg, e);
	}

	throw e;
    }	

    /** 
     * Propagate an IOException by wrapping it in a RuntimeException.
     * Performs appropriate logging.
     */
    private static RuntimeException propagateIOException(String msg,
							 IOException nested)
    {
	final RuntimeException e = new RuntimeException(msg, nested);
	if (logger.isLoggable(Levels.FAILED)) {
	    logger.log(Levels.FAILED, msg, e);
	}

	throw e;
    }
}
