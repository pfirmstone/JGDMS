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

package org.apache.river.mahalo;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.DiscoveryLocatorManagement;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.id.Uuid;
import net.jini.lookup.JoinManager;
import net.jini.lookup.ServiceIDListener;
import net.jini.security.ProxyPreparer;


import org.apache.river.config.Config;
import org.apache.river.logging.Levels;
import org.apache.river.reliableLog.LogHandler;
import org.apache.river.reliableLog.ReliableLog;
import org.apache.river.mahalo.proxy.*;
import net.jini.io.MarshalledInstance;

/**
 * <code>JoinStateManager</code> provides a utility that manages
 * a service's join state (optionally persisting that state) and
 * manages the join protocol protocol on behalf of the service.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see net.jini.lookup.ServiceIDListener
 * @see org.apache.river.reliableLog.LogHandler
 */
 
class JoinStateManager extends LogHandler {
    /** Logger for logging initialization related messages */
    private static final Logger initlogger = TxnManagerImpl.initLogger;

    /** Logger for logging operations related messages */
    private static final Logger operationsLogger = TxnManagerImpl.operationsLogger;

     /** Logger for transaction persistence related messages */
    private static final Logger persistenceLogger = TxnManagerImpl.persistenceLogger;

    /** <code>ProxyPreparer</code> for <code>LookupLocators</code> */
    private volatile ProxyPreparer lookupLocatorPreparer;

    /**
     * Object used to find lookups. Has to implement DiscoveryManagement
     * and DiscoveryLocatorManagement as well as DiscoveryGroupManagement.
     */
    private volatile DiscoveryManagement dm;

    /**
     * <code>JoinManager</code> that is handling the details of binding
     * into Jini lookup services.
     */
    private volatile JoinManager  mgr;

    /**
     * The object coordinating our persistent state.
     */
    private volatile ReliableLog log;

    /**
     * The join state, this data needs to be persisted between restarts
     */
    private volatile Entry[]		attributes;
    private volatile LookupLocator[]	locators;
    private volatile String[]		groups;

    /** Service's internal <code>Uuid</code> which needs to be persisted */
    private volatile Uuid		serviceUuid;
    
    /**
     * Conceptually, true if this is the first time this
     * service has come up, implemented as if there was
     * no previous state then this is the first time.
     */
    private volatile boolean initial = true;
    
    /**
     * Simple constructor.
     */
    JoinStateManager(String logPath) throws IOException { 
        super(); 
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(JoinStateManager.class.getName(), 
	        "JoinStateManager", logPath);
	}
	this.log = (logPath==null) ? null : new ReliableLog(logPath, this);
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(JoinStateManager.class.getName(), 
	        "JoinStateManager");
	}
    }
    
    void recover() throws IOException {
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(JoinStateManager.class.getName(), 
	        "recover");
	}
	if (log != null) log.recover(JoinStateManager.class.getClassLoader());
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(JoinStateManager.class.getName(), 
	        "recover");
	}
    }
    /**
     * Start the manager. Start looking for lookup and registering
     * with them.
     * @param config object to use to obtain
     *               <code>DiscoveryManagement</code> object, and if
     *               this is the initial incarnation of this service, 
     *               the object used to get the initial set of groups,
     *               locators, and deployer defined attributes.
     * @param service The proxy object to register with lookups.
     * @param baseAttributes Any attributes the implementation wants
     *                       attached, only used if this is the
     *                       initial incarnation.
     * @throws IOException if the is problem persisting the 
     *         initial state or in starting discovery.
     * @throws ConfigurationException if the configuration
     *         is invalid.
     */
    void startManager(Configuration config, Object service,
		      ServiceID serviceID, Entry[] baseAttributes) 
	throws IOException, ConfigurationException
    {
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(JoinStateManager.class.getName(), 
	        "startManager",
	        new Object[] { config, service, serviceID, 
		    Arrays.asList(baseAttributes)});
	}
	if (serviceID == null || serviceUuid == null)
            throw new AssertionError(
	       "serviceID and serviceUuid must be set");
    
        // Default do nothing preparer
        final ProxyPreparer defaultPreparer =
            new net.jini.security.BasicProxyPreparer();
        lookupLocatorPreparer =
            (ProxyPreparer)Config.getNonNullEntry(config,
                TxnManager.MAHALO, "lookupLocatorPreparer",
                ProxyPreparer.class, defaultPreparer);
        if(initlogger.isLoggable(Level.CONFIG)) {
	    initlogger.log(Level.CONFIG, "lookupLocatorPreparer: {0}",
	         lookupLocatorPreparer);
	}
//TODO - defer creation of default LDM
	dm = (DiscoveryManagement)
	    Config.getNonNullEntry(config, TxnManager.MAHALO,
		"discoveryManager", DiscoveryManagement.class, 
		new LookupDiscoveryManager(
                    DiscoveryGroupManagement.NO_GROUPS, null, null, config));
        if(initlogger.isLoggable(Level.CONFIG)) {
	    initlogger.log(Level.CONFIG, "discoveryManager: {0}", dm);
	}

	if (dm instanceof DiscoveryGroupManagement) {
	    // Verify proper initial state ---> NO_GROUPS
            String[] groups =
                       ((DiscoveryGroupManagement)dm).getGroups();
            if( (groups == DiscoveryGroupManagement.ALL_GROUPS) || 
	        (groups.length != 0) )
            {
                throw new ConfigurationException(
		    "discoveryManager entry must be configured " +
		    "to initially discover/join NO_GROUPS");
            }//endif
    
        } else { 
            throw new ConfigurationException("Entry for component " +
		TxnManager.MAHALO + ", name " +
		"discoveryManager must implement " +
	        "net.jini.discovery.DiscoveryGroupManagement");
	}
	
	if (dm instanceof DiscoveryLocatorManagement) {
            LookupLocator[] locs =
                    ((DiscoveryLocatorManagement)dm).getLocators();
            if( (locs != null) && (locs.length != 0) ) {
                throw new ConfigurationException
                    ("discoveryManager entry must be initially"
		    + "configured with no locators");
            }//endif
	} else {
	    throw new ConfigurationException("Entry for component " +
		TxnManager.MAHALO + ", name " +
		"discoveryManager must implement " +
		"net.jini.discovery.DiscoveryLocatorManagement");
	}

	// if this is the first incarnation, consult config for groups,
	// locators and attributes.
	if (initial) {
            if(initlogger.isLoggable(Level.FINEST)) {
	        initlogger.log(Level.FINEST, "Obtaining initial values");
	    }
	    groups = (String[])
		config.getEntry(TxnManager.MAHALO,
		    "initialLookupGroups", String[].class, 
		    new String[] { "" }); //default to public group
            if(initlogger.isLoggable(Level.CONFIG)) {
	        initlogger.log(Level.CONFIG, "Obtaining initial groups: {0}",
		(groups==null?
		    Arrays.asList(new String[] {"<ALL_GROUPS>"}):
		    Arrays.asList(groups)));
	    }
	    locators = (LookupLocator[])
		Config.getNonNullEntry(config, TxnManager.MAHALO,
		     "initialLookupLocators", LookupLocator[].class, 
                     new LookupLocator[0]);
            if(initlogger.isLoggable(Level.CONFIG)) {
	        initlogger.log(Level.CONFIG, "Obtaining initial locators: {0}",
		Arrays.asList(locators));
	    }
	    final Entry[] cAttrs = (Entry[])
		Config.getNonNullEntry(config, TxnManager.MAHALO,
		    "initialLookupAttributes", Entry[].class, new Entry[0]);
            if(initlogger.isLoggable(Level.CONFIG)) {
	        initlogger.log(Level.CONFIG, "Obtaining initial attributes: {0}",
		Arrays.asList(cAttrs));
	    }
	    if (cAttrs.length == 0) {
		attributes = baseAttributes;
	    } else {
		attributes = new Entry[cAttrs.length + baseAttributes.length];
                System.arraycopy(baseAttributes, 0, attributes,
                                 0, baseAttributes.length);
                System.arraycopy(cAttrs, 0, attributes,
                                 baseAttributes.length, cAttrs.length);
	    }
            if(initlogger.isLoggable(Level.FINEST)) {
	        initlogger.log(Level.FINEST, "Combined attributes: {0}",
		Arrays.asList(attributes));
	    }

        } else {
            /* recovery : if there are any locators get and
             * use recoveredLookupLocatorPreparer
             */
            if(initlogger.isLoggable(Level.FINEST)) {
	        initlogger.log(Level.FINEST, "Recovered locators: {0}",
		Arrays.asList(locators));
	    }
            if (locators.length > 0) {
                final ProxyPreparer recoveredLookupLocatorPreparer =
                    (ProxyPreparer)Config.getNonNullEntry(config,
                        TxnManager.MAHALO,
                        "recoveredLookupLocatorPreparer", ProxyPreparer.class,
                         defaultPreparer);
                if(initlogger.isLoggable(Level.CONFIG)) {
	            initlogger.log(Level.CONFIG, "recoveredLookupLocatorPreparer: {0}",
		    recoveredLookupLocatorPreparer);
                }
		final List prepared = new java.util.LinkedList();
                for (int i=0; i<locators.length; i++) {
                    try {
                        prepared.add(recoveredLookupLocatorPreparer.
                                     prepareProxy(locators[i]));
                    } catch (Throwable t) {
                        if(initlogger.isLoggable(Levels.HANDLED)) {
	                    initlogger.log(Levels.HANDLED, 
			    "Exception re-preparing LookupLocator: {0}. " 
			        + "Dropping locator.",
			    locators[i]);
			}
                        if(initlogger.isLoggable(Levels.HANDLED)) {
	                    initlogger.log(Levels.HANDLED, 
			    "Preparer exception: ", t);
			}
                    }
                }
                locators =
                    (LookupLocator[])prepared.toArray(new LookupLocator[0]);
	    }
	}

	// Now that we have groups & locators (either from 
	// a previous incarnation or from the config) start discovery.
        if(initlogger.isLoggable(Level.FINEST)) {
	    initlogger.log(Level.FINEST, "Setting groups and locators");
	}
	((DiscoveryGroupManagement)dm).setGroups(groups);
	((DiscoveryLocatorManagement)dm).setLocators(locators);

        if(initlogger.isLoggable(Level.FINEST)) {
	    initlogger.log(Level.FINEST, "Creating JoinManager");
        }
	mgr = new JoinManager(service, attributes, serviceID, 
            dm, null, config);
        // Once we are running we don't need the attributes,
        // locators, and groups fields, null them out (the
        // state is in the mgr and dm.
        attributes = null;
        groups = null;
        locators = null;

	// Now that we have state, make sure it is written to disk.
        if(initlogger.isLoggable(Level.FINEST)) {
	    initlogger.log(Level.FINEST, "Taking snapshot");
	}
	update();
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(JoinStateManager.class.getName(), 
	        "startManager");
	}
    }

    public void setServiceUuid(Uuid serviceUuid) {
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(JoinStateManager.class.getName(), 
	        "setServiceUuid", serviceUuid);
	}
	if (serviceUuid == null)
            throw new NullPointerException("serviceUuid can't be null");
        this.serviceUuid = serviceUuid;
	// Can't update until mgr & dm are started.
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(JoinStateManager.class.getName(), 
	        "setServiceUuid");
	}
    }
    
    public Uuid getServiceUuid() {
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(JoinStateManager.class.getName(), 
	        "getServiceUuid");
	}
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(JoinStateManager.class.getName(), 
	        "getServiceUuid", serviceUuid);
	}
        return serviceUuid;
    }

    /** 
     * Make a good faith attempt to terminate
     * discovery, and cancel any lookup registrations.  */
    public void stop() {
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(JoinStateManager.class.getName(), 
	        "stop");
	}
	// Unregister with lookup

	// Terminate the JoinManager first so it will not call
	// into the dm after it has been terminated.
	if (mgr != null)
	    mgr.terminate();

	if (dm != null) 
	    dm.terminate();
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(JoinStateManager.class.getName(), 
	        "stop");
	}
    }

    public void destroy() {
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(JoinStateManager.class.getName(), 
	        "destroy");
	}
	stop();
	if (log != null)
	    log.deletePersistentStore();
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(JoinStateManager.class.getName(), 
	        "destroy");
	}
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
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(JoinStateManager.class.getName(), 
	        "getLookupAttributes");
	}
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(JoinStateManager.class.getName(), 
	        "getLookupAttributes");
	}
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
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(JoinStateManager.class.getName(), 
	        "addLookupAttributes");
	}
	mgr.addAttributes(attrSets, true);
	update();
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(JoinStateManager.class.getName(), 
	        "addLookupAttributes");
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
     *     
     * @throws java.rmi.RuntimeException if the change can not be persisted.
     * @see net.jini.core.lookup.ServiceRegistration#modifyAttributes
     */
    public void modifyLookupAttributes(Entry[] attrSetTemplates,
				       Entry[] attrSets) {
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(JoinStateManager.class.getName(), 
	        "modifyLookupAttributes");
	}
	mgr.modifyAttributes(attrSetTemplates, attrSets, true);
	update();
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(JoinStateManager.class.getName(), 
	        "modifyLookupAttributes");
	}
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
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(JoinStateManager.class.getName(), 
	        "getLookupGroups");
	}
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(JoinStateManager.class.getName(), 
	        "getLookupGroups");
	}
	return ((DiscoveryGroupManagement)dm).getGroups();
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
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(JoinStateManager.class.getName(), 
	        "addLookupGroups");
	}
 	try {
	    ((DiscoveryGroupManagement)dm).addGroups(groups);
	} catch (IOException e) {
	    throw new RuntimeException("Could not change groups");
	}
	update();
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(JoinStateManager.class.getName(), 
	        "addLookupGroups");
	}
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
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(JoinStateManager.class.getName(), 
	        "removeLookupGroups");
	}
        ((DiscoveryGroupManagement)dm).removeGroups(groups);
	update();
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(JoinStateManager.class.getName(), 
	        "removeLookupGroups");    
	}
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
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(JoinStateManager.class.getName(), 
	        "setLookupGroups");
	}
	try {
	    ((DiscoveryGroupManagement)dm).setGroups(groups);
	} catch (IOException e) {
	    throw new RuntimeException("Could not change groups");
	}
	update();
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(JoinStateManager.class.getName(), 
	        "setLookupGroups");
	}
    }
    
    /** 
     * Get the list of locators of specific lookup services to join. 
     *
     * @return the list of locators of specific lookup services to join
     * @see #setLookupLocators
     */
    public LookupLocator[] getLookupLocators() {
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(JoinStateManager.class.getName(), 
	        "getLookupLocators");
	}
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(JoinStateManager.class.getName(), 
	        "getLookupLocators");
	}
    	return ((DiscoveryLocatorManagement)dm).getLocators();
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
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(JoinStateManager.class.getName(), 
	        "addLookupLocators");
	}
        prepareLocators(locators);
	((DiscoveryLocatorManagement)dm).addLocators(locators);
	update();
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(JoinStateManager.class.getName(), 
	        "addLookupLocators");
	}
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
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(JoinStateManager.class.getName(), 
	        "removeLookupLocators");
	}
        prepareLocators(locators);
	((DiscoveryLocatorManagement)dm).removeLocators(locators);
	update();
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(JoinStateManager.class.getName(), 
	        "removeLookupLocators");
	}
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
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(JoinStateManager.class.getName(), 
	        "setLookupLocators");
	}
        prepareLocators(locators);
	((DiscoveryLocatorManagement)dm).setLocators(locators);
	update();
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(JoinStateManager.class.getName(), 
	        "setLookupLocators");
	}
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
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(JoinStateManager.class.getName(), 
	        "prepareLocators");
	}
        for (int i = 0; i<locators.length; i++) {
            locators[i] = (LookupLocator)lookupLocatorPreparer.prepareProxy(
                locators[i]);
	}
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(JoinStateManager.class.getName(), 
	        "prepareLocators");
	}
    }

    private void update() {
	if (log != null) {
	    synchronized (log) {
		try {
	            log.snapshot();
	        } catch (IOException e) {
	            if (persistenceLogger.isLoggable(Level.WARNING)) {
                        persistenceLogger.log(Level.WARNING,
	                    "Failed to persist join state", e);
	            }
//TODO - need a better strategy here
	            throw new RuntimeException("Problem persisting state.", e);
	        }
	    }
	}
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
                                        ObjectOutput out)
        throws IOException
    {
        // Need to package each attribute in its own marshaled object,
        // this makes sure that the attribute's code base is preserved
        // and when we unpack to discard attributes who's codebase
        // has been lost without throwing away those we can still deal with.
         
        out.writeInt(attributes.length);
        for (int i=0; i<attributes.length; i++) {
            out.writeObject(
                new MarshalledInstance(attributes[i]).convertToMarshalledObject());
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
     * 
     */
    // @see JoinAdminActivationState#writeAttributes
    static private Entry[] readAttributes(ObjectInput in)
        throws IOException, ClassNotFoundException
    {
        final List entries = new java.util.LinkedList();
        final int objectCount = in.readInt();
        for (int i=0; i<objectCount; i++) {
            try {
                MarshalledObject mo = (MarshalledObject)in.readObject();
                entries.add(new MarshalledInstance(mo).get(false));
            } catch (IOException e) {
                if(initlogger.isLoggable(Levels.HANDLED)) {
	            initlogger.log(Levels.HANDLED, 
		    "Exception getting service attribute ... skipping", e);
		}
            } catch (ClassNotFoundException e) {
                if(initlogger.isLoggable(Levels.HANDLED)) {
	            initlogger.log(Levels.HANDLED, 
		    "Exception getting service attribute ... skipping", e);
		}
            }
        }
 
        return (Entry[])entries.toArray(new Entry[0]);
    }

    // -----------------------------------
    //  Methods required by LogHandler
    // -----------------------------------
 
    // inherit doc comment
    public void snapshot(OutputStream out) throws IOException {
	ObjectOutputStream oostream = new ObjectOutputStream(out);
        oostream.writeObject(serviceUuid);
        writeAttributes(mgr.getAttributes(), oostream);
        oostream.writeObject(((DiscoveryLocatorManagement)dm).getLocators());
        oostream.writeObject(((DiscoveryGroupManagement)dm).getGroups());
	oostream.flush();
    }

    // inherit doc comment
    public void recover(InputStream in) 
	throws Exception 
    {
	initial = false;
	ObjectInputStream oistream = new ObjectInputStream(in);
	serviceUuid = (Uuid)oistream.readObject();
        attributes  = readAttributes(oistream);
        locators    = (LookupLocator [])oistream.readObject();
        groups      = (String [])oistream.readObject();
    }
    /**
     * This method always throws
     * <code>UnsupportedOperationException</code> since
     * <code>FileJoinAdminState</code> should never update a
     * log.
     */
    public void applyUpdate(Object update) throws Exception {
        throw new UnsupportedOperationException(
                "JoinStateManager:Updating log" +
                ", this should not happen");
    }

}

