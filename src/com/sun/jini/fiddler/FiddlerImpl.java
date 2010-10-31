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
package com.sun.jini.fiddler;

import com.sun.jini.config.Config;

import com.sun.jini.constants.ThrowableConstants;
import com.sun.jini.constants.TimeConstants;
import com.sun.jini.constants.VersionConstants;

import com.sun.jini.logging.Levels;

import com.sun.jini.lookup.entry.BasicServiceType;
import com.sun.jini.lookup.entry.LookupAttributes;

import com.sun.jini.proxy.ThrowThis;

import com.sun.jini.reliableLog.ReliableLog;
import com.sun.jini.reliableLog.LogHandler;

import com.sun.jini.start.LifeCycle;

import com.sun.jini.thread.InterruptedStatusThread;
import com.sun.jini.thread.ReadersWriter;
import com.sun.jini.thread.ReadersWriter.ConcurrentLockException;
import com.sun.jini.thread.ReadyState;
import com.sun.jini.thread.TaskManager;

import net.jini.activation.ActivationExporter;
import net.jini.activation.ActivationGroup;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import net.jini.config.ConfigurationException;
import net.jini.config.NoSuchEntryException;

import net.jini.discovery.DiscoveryEvent;
import net.jini.discovery.DiscoveryChangeListener;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.DiscoveryLocatorManagement;
import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.discovery.LookupDiscoveryRegistration;
import net.jini.discovery.RemoteDiscoveryEvent;

import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;

import net.jini.id.Uuid;
import net.jini.id.UuidFactory;

import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.InvocationLayerFactory;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;

import net.jini.lookup.JoinManager;
import net.jini.lookup.entry.Comment;
import net.jini.lookup.entry.ServiceInfo;
import net.jini.lookup.entry.Status;
import net.jini.lookup.entry.StatusType;

import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;
import net.jini.security.proxytrust.TrustEquivalence;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceRegistrar;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import java.lang.reflect.Array;

import java.net.InetAddress;

import java.io.InputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;

import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationSystem;
import java.rmi.MarshalledObject;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;

import java.security.PrivilegedExceptionAction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is the server side of an implementation of the lookup
 * discovery service. Multiple client-side proxy classes are used to
 * interact and communicate with this backend server. Those proxy
 * classes are: <code>FiddlerProxy</code> (the proxy for the 
 * <code>LookupDiscoveryService</code> interface which defines how clients
 * register with the lookup discovery service), <code>FiddlerAdminProxy</code>
 * (the proxy for the <code>FiddlerAdmin</code> interface which specifies 
 * the methods through which administration duties such as joining, persistent
 * state logging policy, and shutting down the lookup discovery service can
 * be performed), and <code>FiddlerRegistration</code> (the proxy for the 
 * <code>LookupDiscoveryRegistration</code> interface which defines the
 * methods through which clients can perform duties such as group and
 * locator management, state retrieval, and discarding discovered but
 * unavailable lookup services so they are eligible for re-discovery).
 * <p>
 * It is through the proxies that communicate with this class that clients
 * interact with the lookup discovery service. When a client makes a method
 * invocation on one of the proxies, the proxy makes a corresponding call
 * on the methods specified in the <code>Fiddler</code> interface which
 * are implemented in this class, ultimately executing on the backend server.
 * <p>
 *
 * @com.sun.jini.impl
 *
 * This implementation of the lookup discovery service employs a number of
 * {@link java.util.logging.Logger}s. The details of each such
 * {@link java.util.logging.Logger} are described
 * <a href="./package-summary.html#fiddlerLoggers">here</a>.
 * <p>
 * The configuration entries supported by this implementation are described
 * <a href="./package-summary.html#fiddlerConfigEntries">here</a>.
 *
 * @author Sun Microsystems, Inc.
 */
class FiddlerImpl implements ServerProxyTrust, ProxyAccessor, Fiddler {

    /* Name of this component; used in config entry retrieval and the logger.*/
    private static final String COMPONENT_NAME = "com.sun.jini.fiddler";
    /* Loggers used by this implementation of the service. */
    static final Logger problemLogger      = Logger.getLogger(COMPONENT_NAME+
                                                              ".problem");
    static final Logger startupLogger      = Logger.getLogger(COMPONENT_NAME+
                                                              ".startup");
    static final Logger tasksLogger        = Logger.getLogger(COMPONENT_NAME+
                                                              ".tasks");
    static final Logger eventsLogger       = Logger.getLogger(COMPONENT_NAME+
                                                              ".events");
    static final Logger groupsLogger       = Logger.getLogger(COMPONENT_NAME+
                                                              ".groups");
    static final Logger locatorsLogger     = Logger.getLogger(COMPONENT_NAME+
                                                              ".locators");
    static final Logger discardLogger      = Logger.getLogger(COMPONENT_NAME+
                                                              ".discard");
    static final Logger leaseLogger        = Logger.getLogger(COMPONENT_NAME+
                                                              ".lease");
    static final Logger registrationLogger = Logger.getLogger(COMPONENT_NAME+
                                                              ".registration");
    static final Logger persistLogger      = Logger.getLogger(COMPONENT_NAME+
                                                              ".persist");
    /** Data structure - associated with a <code>ServiceRegistrar</code> -
     *  containing the <code>LookupLocator</code> and the member groups of
     *  the registrar
     */
    protected final class LocatorGroupsStruct {
        public LookupLocator locator;
        public String[]      groups;
        LocatorGroupsStruct(LookupLocator locator, String[] groups) {
            this.locator = locator;
            this.groups  = groups;
        }
    }//end class LocatorGroupsStruct

    /* ServiceInfo values */
    private static final String PRODUCT      = "Lookup Discovery Service";
    private static final String MANUFACTURER = "Sun Microsystems, Inc.";
    private static final String VENDOR       = MANUFACTURER;
    private static final String VERSION      = VersionConstants.SERVER_VERSION;

    /** When re-setting the bound on lease durations, that bound cannot be
     *  set to a value larger than this value */
    private static final long MAX_LEASE = 1000L * 60 * 60 * 24 * 365 * 1000;
    /** Log format version */
    private static final int LOG_VERSION = 2;

    /** The outer (smart) proxy to this server */
    private FiddlerProxy outerProxy;
    /** The inner proxy (stub or dynamic proxy) to this server */
    private Fiddler innerProxy;
    /** The admin proxy to this server */
    private FiddlerAdminProxy adminProxy;
    /** The service ID associated with this service when it is registered
     *  with any lookup service.
     */
    private ServiceID serviceID = null;
    /** The activation id of the current instance of the lookup discovery
     *  service, if it happens to be and activatable instance
     */
    private ActivationID activationID;
    /* Holds the prepared proxy to the ActivationSystem */
    private ActivationSystem activationSystem;
    /** The unique identifier generated (or recovered) when an instance of
     *  this service is constructed. This ID is typically used to determine
     *  equality between the proxies of any two instances of this service.
     */
    private Uuid proxyID = null;
    /** Map from the set of all currently discovered registrars to their 
     *  corresponding [locator,member groups] pairs (locatorGroupsStuct).
     */
    private final HashMap allDiscoveredRegs = new HashMap(11);
    /** Map from registrationID to registrationInfo.  Every registration is in
     *  this map under its registrationID.
     */
    private final HashMap registrationByID = new HashMap(11);
    /** Map from registrationInfo to registrationInfo (that is, to itself),
     *  where the elements of the map are ordered by lease expiration time.
     */
    private final TreeMap registrationByTime = new TreeMap();
    /** Performs all group and locator discovery on behalf of clients */
    private LookupDiscoveryManager discoveryMgr = null; 
    /** The listener registered for both group discovery events and locator
     *  discovery events.
     */
    private final LookupDiscoveryListener discoveryListener 
                                               = new LookupDiscoveryListener();
    /** For each registration created by the lookup discovery service, an
     *  event identifier that uniquely maps the registration to the
     *  registration's listener and managed sets will be generated and
     *  associated with the registration through the EventRegistration
     *  field of the registrationInfo. This event ID is unique across
     *  all registrations with the current instance of the lookup discovery
     *  service.
     */
    private long curEventID = 0;
    /** Earliest expiration time over all active registrations */
    private long minExpiration = Long.MAX_VALUE;
    /** The lookup discovery manager this service's join manager will use */
    private DiscoveryManagement joinMgrLDM;
    /** Manager for discovering and registering with lookup services */
    private JoinManager joinMgr;
    /** Task manager for sending remote discovery events */
    private TaskManager taskMgr;
    /** Registration lease expiration thread */
    private Thread leaseExpireThread;
    /** Snapshot-taking thread */
    private Thread snapshotThread;

    /** Concurrent object to control read and write access */
    private final ReadersWriter concurrentObj = new ReadersWriter();
    /** Object for synchronizing with the registration expire thread */
    private final Object leaseExpireThreadSyncObj = new Object();
    /** Object on which the snapshot-taking thread will synchronize */
    private final Object snapshotThreadSyncObj = new Object();

    /** Reliable log object to hold persistent state of the service.
     *  This object is also used as a flag: non-null ==> persistent service
     *                                      null ==> non-persistent service
     */
    private ReliableLog log = null;
    /** Flag indicating whether system is in a state of recovery */
    private boolean inRecovery;
    /** Current number of records in the Log File since the last snapshot */
    private int logFileSize = 0;
    /** The name of the directory to which the persistent modes of this service
     *  will log the service's state (using ReliableLog).
     */
    private String persistDir;
    /** least upper bound applied to all granted lease durations */
    private long leaseBound = 1000 * 60 * 30;
    /** Weight factor applied to snapshotSize when deciding to take snapshot */
    private float snapshotWt = 10;
    /** Log File must contain this many records before snapshot allowed */
    private int snapshotThresh = 200;
    /** Groups whose members are lookup services this service should join.
     *  Unless configured otherwise, this service will initially join the
     *  un-named public group. The desired join groups for this service can
     *  be set administratively after start up.
     */
    private String[] thisServicesGroups = new String[] {""};
    /** Locators of specific lookup services this service should join.
     *  This service will initially join no lookups found through locator
     *  discovery. The locators of the specific lookup services that are
     *  desired for this service to join should be set administratively
     *  after start up.
     */
    private LookupLocator[] thisServicesLocators = {};
    /** The attributes to use when joining lookup service(s) */
    private Entry[] thisServicesAttrs
            = new Entry[]
                 { new ServiceInfo(PRODUCT,MANUFACTURER,VENDOR,VERSION,"",""),
                   new BasicServiceType("Lookup Discovery Service")
                 };
    /* Object used to obtain the configuration items for this service. */
    private Configuration config;
    /* The JAAS login context to use when performing a JAAS login. */
    private LoginContext loginContext = null;
    /* The exporter used to export this service. */
    private Exporter serverExporter;
    /* Maximum value of upper bound on lease durations.*/
    private long leaseMax = MAX_LEASE;
    /* Flag indicating this service is being started for the very 1st time */
    private boolean initialStartup = true;
    /** Object that, if non-<code>null</code>, will cause the object's
     *  <code>unregister</code> method to be invoked during service shutdown
     *  to notify the service starter framework that the reference to this
     *  service's implementation can be 'released' for garbage collection;
     *  the framework is notified that it does not have to hold on to the
     *  service reference any longer.  Note hat this object is used only 
     *  in the non-activatable case.
     */
    private LifeCycle lifeCycle = null;
    /* Preparer for proxies to remote listeners newly registered with this
     * service.
     */
    private static ProxyPreparer listenerPreparer;
    /* Preparer for proxies to remote listeners, previously prepared, and
     * recovered from this service's persisted state.
     */
    private static ProxyPreparer recoveredListenerPreparer;
    /* Preparer for initial and new lookup locators this service should
     * discover and join.
     */
    private static ProxyPreparer locatorToJoinPreparer;
    /* Preparer for lookup locators this service should discover and join
     * that were previously prepared and which were recovered from this
     * service's persisted state.
     */
    private static ProxyPreparer recoveredLocatorToJoinPreparer;
    /* Preparer for initial and new lookup locators this service should
     * discover on behalf of the clients that register with it.
     */
    private static ProxyPreparer locatorToDiscoverPreparer;
    /* Preparer for lookup locators this service should discover on behalf
     * of its registered clients that were previously prepared and which
     * were recovered from this service's persisted state.
     */
    private static ProxyPreparer recoveredLocatorToDiscoverPreparer;
    /** Object used to prevent access to this service during the service's
     *  initialization or shutdown processing.
     */
    private final ReadyState readyState = new ReadyState();

    /* ************************* BEGIN Constructors ************************ */
    /**
     * Constructs a new instance of FiddlerImpl. This version of the
     * constructor is used to create an activatable instance of the lookup
     * discovery service that logs its state information to persistent storage.
     * <p>
     * A constructor having this signature is required for the class to be
     * activatable. This constructor is automatically called by the 
     * activation group when the lookup discovery service is activated.
     * 
     * @param activationID the activation ID generated by the activation
     *                     system and assigned to the instance of the server
     *                     being activated
     * @param data         state data (represented as a 
     *                     <code>MarshalledObject</code>) which is needed to
     *                     re-activate this server
     *
     * @throws IOException            this exception can occur when there is
     *                                a problem recovering data from disk,
     *                                exporting the server that's being
     *                                activated, or when unmarshalling the
     *                                given <code>data</code> parameter.
     * @throws ConfigurationException this exception can occur when a
     *                                problem occurs while retrieving an item
     *                                from the <code>Configuration</code>
     *                                generated from the contents of the
     *                                given <code>data</code> parameter
     * @throws ActivationException    this exception can occur when a problem
     *                                occurs while activating the service
     * @throws LoginException         this exception occurs when authentication
     *                                fails while performing a JAAS login for
     *                                this service
     * @throws ClassNotFoundException this exception can occur while 
     *                                unmarshalling the given <code>data</code>
     *                                parameter; when a class needed in the
     *                                unmarshalling process cannot be found.
     * @throws ClassCastException     this exception can occur while
     *                                unmarshalling the given <code>data</code>
     *                                parameter; when the contents of that
     *                                parameter is not a <code>String</code>
     *                                array.
     */
    FiddlerImpl(ActivationID activationID,
                MarshalledObject data) throws IOException,
                                              ActivationException,
                                              ConfigurationException,
                                              LoginException,
                                              ClassNotFoundException
    {
        this.activationID = activationID;
        try {
            activationSystem = ActivationGroup.getSystem();
            init( (String[])data.get(), true );//true ==> persistent
        } catch(Throwable e) {
            cleanupInitFailure();
            handleActivatableInitThrowable(e);
        }
    }//end activatable constructor

    /**
     * Constructs a new instance of FiddlerImpl. This version of the
     * constructor is used to create a NON-activatable instance of the
     * lookup discovery service.
     *
     * @param configArgs <code>String</code> array whose elements are
     *                   the arguments to use when creating this version of
     *                   the server
     * @param lifeCycle  instance of <code>LifeCycle</code> that, if 
     *                   non-<code>null</code>, will cause this object's
     *                   <code>unregister</code> method to be invoked during
     *                   shutdown to notify the service starter framework that
     *                   the reference to this service's implementation can be
     *                   'released' for garbage collection. A value of 
     *                   <code>null</code> for this argument is allowed.
     * @param persistent if <code>true</code>, then the service should persist
     *                   its state.
     *
     * @throws IOException            this exception can occur when there is
     *                                a problem recovering data from disk, or
     *                                while exporting the server that's being
     *                                created.
     * @throws ConfigurationException this exception can occur when an
     *                                problem occurs while retrieving an item
     *                                from the <code>Configuration</code>
     *                                generated from the contents of the
     *                                given <code>configArgs</code> parameter
     * @throws LoginException         this exception occurs when authentication
     *                                fails while performing a JAAS login for
     *                                this service
     */
    FiddlerImpl(String[] configArgs, LifeCycle lifeCycle, boolean persistent)
                                             throws IOException,
                                                    ConfigurationException,
                                                    LoginException
    {
        try {
            this.lifeCycle = lifeCycle;
            init(configArgs, persistent);
        } catch(Throwable e) {
            cleanupInitFailure();
            handleInitThrowable(e);
        }
    }//end non-activatable constructor
    /* ************************** END Constructors ************************* */

    /* ******************* BEGIN Inner Class Definitions ******************* */
    /** Class which is used to communicate the status of this service to
     *  interested entities. In particular, when certain errors occur during
     *  operation, an instance of this class will be registered as an 
     *  attribute in all of the lookup services with which this service
     *  is registered. By registering for notification (from the lookup
     *  services) of the existence of this attribute, interested entities
     *  such as administrative clients and clients wishing to use this
     *  service will be informed when this service can not proceed with its
     *  processing, and can take appropriate action.
     */
    private static class FiddlerStatus extends Status {
        private static final long serialVersionUID = -8511826097053446749L;
        public FiddlerStatus(StatusType severity) {
            super(severity);
        }
    }//end class FiddlerStatus

    /** Class whose discovered() method is invoked by threads in the 
     *  LookupDiscovery class whenever a new lookup service is discovered
     *  on behalf of a client registration
     */
    private class LookupDiscoveryListener implements DiscoveryChangeListener {
        public LookupDiscoveryListener() {
            super();
        }
        public void discovered(DiscoveryEvent event) {
            taskMgr.add(new DiscoveredEventTask(event));
        }
        public void discarded(DiscoveryEvent event) {
            taskMgr.add(new DiscardedEventTask(event));
        }
        public void changed(DiscoveryEvent event) {
            taskMgr.add(new ChangedEventTask(event));
        }
    }//end class LookupDiscoveryListener

    /** This class acts as a record of one registration with the lookup
     *  discovery service; containing all of the information about that
     *  registration.
     */
    private final static class RegistrationInfo
                                          implements Comparable, Serializable
    {
        private static final long serialVersionUID = 2L;

        /** The unique identifier assigned to the registration to which the
         *  data in the current implementation of this class corresponds.
         *  This identifier is unique across all other active registrations
         *  generated with the current instance of the lookup discovery
         *  service.
         *  @serial
         */
        public final Uuid registrationID;
        /** Map from the set of instances of the <code>ServiceRegistrar</code>
         *  interface, to the set of marshalled instances of the
         *  <code>ServiceRegistrar</code> interface, where each key and
         *  each value (which is the marshalled form of its corresponding
         *  key) is a proxy to one of the lookup service(s) that have been
         *  discovered for the current registration. The contents of
         *  this set represents the 'remote state' of the registration's
         *  currently discovered lookup service(s).
         *  @serial
         */
        public HashMap discoveredRegsMap;
        /** The managed set containing the names of the groups whose
         *  members are the lookup services the lookup discovery service
         *  should attempt to discover for the current registration.
         *  (HashSet is used to prevent duplicates.)
         *  @serial
         */
        public HashSet groups;
        /** The managed set containing the locators of the specific lookup
         *  services the lookup discovery service should attempt to discover
         *  for the current registration. (HashSet is used to prevent
         *  duplicates.)
         *  @serial
         */
        public HashSet locators;
        /** The ID of the lease placed on the current registration.
         *  @serial
         */
        public final Uuid leaseID;
        /** The absolute expiration time of the current lease.
         *  @serial
         */
        public long leaseExpiration;
        /** The identifier that maps the current registration to the remote
         *  event listener and the managed set of groups and locators.
         */
        public long eventID;
        /** The current sequence number of the set of remote discovery events
         *  sent to the current registration's listener. When a registration
         *  is granted, this class is instantiated to contain the information
         *  related to that particular registration. The event sequence
         *  number is initialized to 0 upon instantiation because the
         *  remote discovery events are sent to the listeners of each 
         *  separate registration. Thus, each registration has its own
         *  sequence of events.
         *  @serial
         */
        public long seqNum;
        /** The handback object returned with every remote discovery event
         *  sent to the current registration's listener.
         *  @serial
         */
        public final MarshalledObject handback;
        /** When the lookup discovery service discards a registrar as a 
         *  result of some internal condition (such as multicast announcements
         *  ceasing) and not as a result of a request from a registration,
         *  every registration configured for group discovery of that discarded
         *  registrar will be sent a remote discarded event. On the other
         *  hand, for the case where a registrar is discarded as a result
         *  of a request from a registration, only those registrations that
         *  actually request that the registrar be discarded will be sent
         *  a remote discarded event. This flag is used to determine whether
         *  to send a remote discarded event to one or multiple listeners.
         */
        public boolean discardFlag;
        /** The remote event listener registered by the client. This field
         *  is transient because it is marshalled separately from the rest
         *  of this class when being serialized. (See the description for
         *  <code>writeObject</code> below.)
         */
        public transient RemoteEventListener listener;

        /** Constructs an instance of this class and stores the information
         *  related to the current registration: IDs, managed sets, lease
         *  information, and event registration information.
         */
        public RegistrationInfo(Uuid registrationID,
                                String[] groups,
                                LookupLocator[] locators,
                                Uuid leaseID,
                                long leaseExpiration,
                                long eventID,
                                MarshalledObject handback,
                                RemoteEventListener listener)
        {
            this.registrationID = registrationID;
            /* Initialize the groups field, removing nulls and duplicates */
            if(groups != null) {
                this.groups = new HashSet();
                for(int i=0;i<groups.length;i++) {
                    if(groups[i] == null) continue;
                    this.groups.add(groups[i]);
                }
            }
            /* Initialize the locators field, removing nulls and duplicates */
            this.locators = new HashSet();
            if( (locators != null) && (locators.length > 0) ) {
                for(int i=0;i<locators.length;i++) {
                    if(locators[i] == null) continue;
                    this.locators.add(locators[i]);
                }
            }
            this.discoveredRegsMap = new HashMap(11);
            this.leaseID = leaseID;
            this.leaseExpiration = leaseExpiration;

            this.eventID = eventID;
            this.seqNum = 0; // initialize to 0
            this.handback = handback;
            this.discardFlag = false;//set true only on first discard request
            this.listener = listener;
        }//end constructor

        /** Attempts to marshal each element of the input set of instances of
         *  the <code>ServiceRegistrar</code> interface and then map the
         *  registrar to its marshalled form, and store the mapping in this
         *  registration's <code>discoveredRegsMap</code> field.
         *  <p>
         *  This method is typically invoked to handle discovered (as opposed
         *  to discarded) registrars. Note that if a particular registrar
         *  cannot be serialized (marshalled), it is not included in the
         *  mapping; nor is it included in the return set.
         * 
         * @param regMapIn mapping in which the key values are the registrars
         *                 to serialize and store, and the map values are data
         *                 structures of type <code>LocatorGroupsStruct</code>
         *                 that contain the locator and member groups of the
         *                 corresponding registrar key
         * 
         *  @return a <code>HashMap</code> whose keys are the registrars
         *          whose marshalled form and un-marshalled form were inserted
         *          as key/value pairs into the <code>discoveredRegsMap</code>
         *          field of this regInfo; and whose values are the member
         *          groups of each corresponding registrar key.
         */
        public HashMap addToDiscoveredRegs(HashMap regMapIn) {
            HashMap regMapOut = new HashMap(regMapIn.size());
            Iterator itr = (regMapIn.entrySet()).iterator();
            nextReg:
            for(int i=0;itr.hasNext();i++) {
                Map.Entry pair = (Map.Entry)itr.next();
                ServiceRegistrar reg = (ServiceRegistrar)pair.getKey();
                /* If reg is already in map, go to next registrar */
                if( discoveredRegsMap.containsKey(reg) ) continue nextReg;
                /* It doesn't contain it, try to marshal it */
                MarshalledObject mReg = null;
                try {
                    mReg = new MarshalledObject(reg);
                } catch(IOException e) { continue nextReg; } //failed, next reg
                /* Succeeded, map registrar to its marshalled form */
                discoveredRegsMap.put(reg,mReg);
                /* Map the registrar to its member groups for the return map */
                regMapOut.put(reg,
                              ((LocatorGroupsStruct)pair.getValue()).groups);
            }//end loop
            return regMapOut;
        }//end addToDiscoveredRegs

        /** Performs a primary sort by leaseExpiration, and a secondary sort
         *  by registrationID. The secondary sort is immaterial, except to
         *  ensure a total order (required by <code>TreeMap</code>).
         */
        public int compareTo(Object obj) {
            RegistrationInfo regInfo = (RegistrationInfo)obj;
            if (this == regInfo)  return 0;
            if (    (leaseExpiration < regInfo.leaseExpiration)
                 || (    (leaseExpiration == regInfo.leaseExpiration)
		      && (eventID < regInfo.eventID) )  )
            {
                return -1;
            }//endif
            return 1;
        }//end compareTo

        /** When a registration is granted to a client, the client registers
         *  a remote listener with the lookup discovery service so that the
         *  lookup discovery service may send remote discovery events to the
         *  client. The client typically annotates the listener with an RMI
         *  codebase from which the backend server can download the remote
         *  listener's proxy (stub). When the current registration is logged
         *  to persistent storage (for example, a snapshot is taken), the
         *  listener is written to the output snapshot or log file through
         *  an <code>ObjectOutputStream</code> which only serializes the
         *  listener; it does not marshal the listener. Thus, when the
         *  listener field of this class is logged, unless special action
         *  is taken, the codebase from which to retrieve the listener will
         *  not be included in the output. 
         *
         *  In order to include the codebase with the listener when saving
         *  state, the following custom <code>writeObject</code> method
         *  is provided which first serializes the current instance of
         *  this class (excluding the transient <code>listener</code> field),
         *  and then explicitly marshals the listener to preserve the
         *  codebase upon writing to the file. In this way, the listener --
         *  along with its codebase -- is persisted through a mechanism that
         *  is separate from the normal mechanism applied to the remaining
         *  fields of this class.
         */
        private void writeObject(ObjectOutputStream stream) throws IOException{
            stream.defaultWriteObject();
            stream.writeObject(new MarshalledObject(listener));
        }//end writeObject

        /** When this class is deserialized, this method is invoked. This
         *  method first deserializes the non-transient elements of this
         *  class, and then unmarshals the remote event listener. (See the
         *  description for <code>writeObject</code> above.)
         */
        private void readObject(ObjectInputStream stream)
                                    throws IOException, ClassNotFoundException
        {
            stream.defaultReadObject();
            MarshalledObject mo = (MarshalledObject)stream.readObject();
            try {
                listener = (RemoteEventListener)mo.get();
                /* Re-prepare the recovered listener */
                listener = 
                 (RemoteEventListener)recoveredListenerPreparer.prepareProxy
                                                                   (listener);
            } catch (Throwable e) {
                problemLogger.log(Level.INFO, "problem recovering listener "
                                  +"for recovered registration", e);
                if((e instanceof Error) && (ThrowableConstants.retryable(e)
                                             == ThrowableConstants.BAD_OBJECT))
                {
                   throw (Error)e;
                }//endif
            }
            /* Prepare the locators recovered from the stream */
            int nUnprepared = (locators).size();
            locators = (HashSet)prepareOldLocators
                                         ( recoveredLocatorToDiscoverPreparer,
                                           locators );
            if( nUnprepared != (locators).size() ) {
                /* Failure occurred when preparing one of the locs. Because
                 * this breaks the contract with the client, this registration
                 * will not be recovered so that the client will eventually
                 * be notified. To facilitate this, the listener is set to
                 * null so that the registration will not be added to the
                 * managed set, and so that when the client eventually attempts
                 * to renew the lease on that registration, an exception will
                 * occur; causing the client to be "notified" that there was
                 * a problem with that registration. The client can then
                 * retry the registration; and if problems still exist, the
                 * exception the client receives may give the client more
                 * useful information from which the client can determine
                 * how to proceed.
                 */
                listener = null;
                if( problemLogger.isLoggable(Level.WARNING) ) {
                    problemLogger.log(Level.WARNING, "failure preparing "
                                      +"locator while recovering registration"
                                      +"... discarding recovered"
                                      +"registration");
                }//endif
            }//endif
        }//end readObject
    }//end class RegistrationInfo

    /** This class represents a <code>Task</code> object that is placed
     *  in the <code>TaskManager</code> queue for processing in the thread
     *  pool. Instances of this class are placed on the task queue when
     *  registrations are granted. 
     *  <p>
     *  The <code>run</code> method of this class will determine if any of
     *  the new registration's desired lookup service(s) have already been
     *  discovered, and will send the appropriate remote discovery event to
     *  the registration's listener.
     */
    private final class NewRegistrationTask implements TaskManager.Task {
        /** The data structure record corresponding to the new registration */
        public final RegistrationInfo regInfo;
        /** Constructs an instance of this class and stores the registration
         *  information.
         */
        public NewRegistrationTask(RegistrationInfo regInfo) {
            this.regInfo = regInfo;
        }//end constructor
        /** This method processes the information associated with the new
         *  registration and determines, based on the current state of the
         *  set of 'already-discovered' lookup service(s), whether to send
         *  a <code>RemoteDiscoveryEvent</code> to the new registration's
         *  listener.
         */
        public void run() {
            concurrentObj.writeLock();
            try {
                logInfoTasks("NewRegistrationTask.run(): "
                             +"new Registration added");
                maybeSendDiscoveredEvent(regInfo,allDiscoveredRegs);
            } finally {
                concurrentObj.writeUnlock();
            }
        }//end run

        /** This method returns true if the current instance of this class
         *  must be run after at least one task in the input task list with
         *  an index less than the <code>size</code> parameter (size may be
         *  less than tasks.size()).
         *  <p>
         *  Note that using List.get will be more efficient than List.iterator.
         *
         *  @param tasks the tasks to consider.  A read-only List, with all
         *         elements being an instanceof Task.
         *  @param size elements with index less than size should be considered
         */
        public boolean runAfter(List tasks, int size) {
            return false;
        }//end runAfter
    }//end class NewRegistrationTask

    /** This class represents a <code>Task</code> object that is placed
     *  in the <code>TaskManager</code> queue for processing in the thread
     *  pool. An instance of this class is placed on the task queue when a
     *  <code>DiscoveryEvent</code> instance indicating a discovered event
     *  is received from the local discovery process. 
     *  <p>
     *  The <code>run</code> method of this class will process discovery
     *  event information and determine to which active registrations the
     *  appropriate <code>RemoteDiscoveryEvent</code> should be sent; and
     *  then sends that event.
     */
    private final class DiscoveredEventTask implements TaskManager.Task {
        /** The local event sent by the discovery manager. */
        public final DiscoveryEvent event;
        /** Constructs an instance of this class and stores the event*/
        public DiscoveredEventTask(DiscoveryEvent event) {
            this.event = event;
        }//end constructor
        /** This method processes the local discovery event information and
         *  determines, based on the current state of each active 
         *  registration, to which such registration the appropriate
         *  <code>RemoteDiscoveryEvent</code> should be sent. After making
         *  the determination, the remote event appropriate for each 
         *  registration is constructed and sent.
         */
        public void run() {
            /* Get locators before sync block (no remote calls in sync block)*/
            Map groupsMap = event.getGroups();
            ServiceRegistrar[] regs = event.getRegistrars();
            HashMap regMap = new HashMap(regs.length);
            for(int i=0;i<regs.length;i++) {
                try {
                    LookupLocator regLoc = regs[i].getLocator();
                    String[] regGroups = (String[])groupsMap.get(regs[i]);
                    LocatorGroupsStruct regLocGroups
                                  = new LocatorGroupsStruct(regLoc,regGroups);
                    regMap.put(regs[i],regLocGroups);
                } catch(Exception e) {
                    problemLogger.log(Levels.FAILED,
                                       "problem retrieving locator "
                                      +"from discovered lookup service ... "
                                      +"discarded the lookup service", e);
                    discoveryMgr.discard(regs[i]);
                }
            }//end loop
            /* Synchronization block -- no remote calls here */
            concurrentObj.writeLock();
            logInfoTasks("DiscoveredEventTask.run(): processing DISCOVERED "
                         +"event from discovery manager");
            try {
                /* Update the global allDiscoveredRegs map with new pairs */
                Set eSet = regMap.entrySet();
                for(Iterator itr = eSet.iterator(); itr.hasNext(); ) {
                    Map.Entry pair = (Map.Entry)itr.next();
                    allDiscoveredRegs.put(pair.getKey(),pair.getValue());
                }//end loop
                /* Loop thru regInfo's, adding only those not already known */
                for( Iterator itr=registrationByID.values().iterator();
                                                              itr.hasNext(); )
                {
                    RegistrationInfo regInfo = (RegistrationInfo)itr.next();
                    /* Build and send the "discovered event" if appropriate */
                    maybeSendDiscoveredEvent(regInfo,regMap);
                }//end loop
            } finally {
                concurrentObj.writeUnlock();
            }
        }//end run

        /** This method returns true if the current instance of this class
         *  must be run after at least one task in the input task list with
         *  an index less than the <code>size</code> parameter (size may be
         *  less than tasks.size()).
         *  <p>
         *  Note that using List.get will be more efficient than List.iterator.
         *
         *  @param tasks the tasks to consider.  A read-only List, with all
         *         elements being an instanceof Task.
         *  @param size elements with index less than size should be considered
         */
        public boolean runAfter(List tasks, int size) {
            return false;
        }//end runAfter
    }//end class DiscoveredEventTask

    /** This class represents a <code>Task</code> object that is placed
     *  in the <code>TaskManager</code> queue for processing in the thread
     *  pool.  An instance of this class is placed on the task queue when a
     *  <code>DiscoveryEvent</code> instance indicating a discarded event
     *  is received from the local discovery process. 
     *  <p>
     *  The <code>run</code> method of this class will process event
     *  information resulting from the "discarding" of one or more
     *  lookup services (registrars), and will determine to which active 
     *  registrations the appropriate <code>RemoteDiscoveryEvent</code>
     *  should be sent; and then sends that event.
     */
    private final class DiscardedEventTask implements TaskManager.Task {
        /** The local event sent by the discovery manager. */
        public final DiscoveryEvent event;
        /** Constructs an instance of this class and stores the event*/
        public DiscardedEventTask(DiscoveryEvent event) {
            this.event = event;
        }//end constructor
        /** This method processes the local discovery event information and
         *  determines, based on the current state of each active 
         *  registration, to which such registration the appropriate
         *  <code>RemoteDiscoveryEvent</code> should be sent. After making
         *  the determination, the remote event appropriate for each 
         *  registration is constructed and sent.
         */
        public void run() {
            concurrentObj.writeLock();
            logInfoTasks("DiscardedEventTask.run(): processing DISCARDED "
                         +"event from discovery manager");
            try {
                /* Get the registrars that were just discarded */
                Map groupsMap = event.getGroups();
                HashSet allDiscardedRegs = new HashSet(groupsMap.size());
                /* Determine if we're here because of an external request for
                 * discard from one of the regInfo's (an active communication
                 * discard), or because the discovery manager has determined
                 * one or more of the discovered registrars has become 
                 * unreachable (a passive communication discard)
                 */
                RegistrationInfo regInfo = externalDiscardRequest();
                /* If an external request, send the discarded event to only
                 * the regInfo that requested the discard; otherwise, send
                 * it to all regInfo's that might be interested.
                 */
                if(regInfo != null) {
                    /* Send discard event to only this one registration */
                    HashSet discardedRegs = maybeSendDiscardedEvent
                                                    (regInfo,groupsMap,true);
                    /* Transfer the just-discarded regs to the summary set */
                    for(Iterator jtr=discardedRegs.iterator();jtr.hasNext(); ){
                        allDiscardedRegs.add(jtr.next());
                    }
                } else {
                    /* Send discard event to each "eligible" registration */
                    for( Iterator itr=registrationByID.values().iterator();
                                                              itr.hasNext(); )
                    {
                        regInfo = (RegistrationInfo)itr.next();
                        HashSet discardedRegs = maybeSendDiscardedEvent
                                                     (regInfo,groupsMap,false);
                        /* Transfer the just-discarded regs to summary set */
                        for(Iterator jtr=discardedRegs.iterator();
                                                             jtr.hasNext(); ) {
                            allDiscardedRegs.add(jtr.next());
                        }
                    }//end loop
                }//endif
                maybeRemoveDiscardedRegsFromGlobalSet(allDiscardedRegs);
            } finally {
                concurrentObj.writeUnlock();
            }
        }//end run

        /** This method determines, based on the current state of the 
         * <code>regInfo</code> parameter, whether or not to send a
         * remote discarded event to the regInfo's listener, and then builds
         * and sends the event if appropriate. This method is called in
         * response to one of the following situations:
         * <p>
         * 1 after invocation of the public <code>discard</code> method
         * 2 after receipt of a "passive" discarded event from the discovery
         *   manager.
         * <p>
         * For case 1, such an event typically indicates what is referred to
         * as an "active, communication" discarded event. This term is used
         * in this situation because the regInfo takes the specific action
         * of requesting that a registrar the client has determined is
         * unreachable be discarded.
         * <p>
         * For case 2, such an event typically indicates what is referred to
         * as a "passive, communication" discarded event. This term is used
         * here because the discovery manager - not the client - has determined
         * that one or more of the previously discovered registrars are now
         * unreachable. In this case, the client remains "passive", and it
         * is the discovery manager that discards the unreachable registrars
         * and notifies the client(s).
         * 
         * @param regInfo    the data structure record corresponding to the 
         *                   registration whose listener will receive the event
         * @param groupsMap  mapping from the registrars referenced in the 
         *                   just-received event to their corresponding set of
         *                   member groups
         * @param active     flag indicating whether the event is an "active"
         *                   or a "passive" discarded event
         *
         * @return set of registrars that were discarded for the given regInfo
         */
        private HashSet maybeSendDiscardedEvent(RegistrationInfo regInfo,
                                                Map groupsMap,
                                                boolean active)
        {
            HashSet discardedRegs = new HashSet(groupsMap.size()); //return val
            /* If no interest in groups or locators, go to next regInfo*/
            if(     (regInfo.groups != null) && ((regInfo.groups).size() == 0)
                 && ((regInfo.locators).size() == 0) )
            {
                return discardedRegs;
            }
            HashMap discardMap = new HashMap(groupsMap.size());
            /* loop thru the (registrar,groups) pairs, find regs to discard */
            Set eSet = groupsMap.entrySet();
            for(Iterator itr = eSet.iterator(); itr.hasNext(); ) {
                Map.Entry pair = (Map.Entry)itr.next();
                ServiceRegistrar reg = (ServiceRegistrar)pair.getKey();
                /* Include the current reg in the discard map only if that
                 * reg is in the regInfo's discovered set.
                 */
                if( (regInfo.discoveredRegsMap).containsKey(reg) ) {
                    /* The groups corresponding to the discarded registrar that
                     * arrived in the event may be more up-to-date than the
                     * groups associated with the registrar in the global map.
                     * Thus, if the event is a passive communication discarded
                     * event, when determining whether the regInfo is still
                     * interested in the discarded registrar, use the old group
                     * info rather than the group info sent in the event.
                     */
                    String[] regGroups = (active ? (String[])pair.getValue() :
                    ((LocatorGroupsStruct)allDiscoveredRegs.get(reg)).groups );

                    if( active || interested(regGroups,regInfo.groups) ) {
                        discardMap.put(reg,regGroups);
                        discardedRegs.add(reg);
                        (regInfo.discoveredRegsMap).remove(reg);
                    }//end if
                }//end if
            }//end loop
            /* Build and send the "discarded event" */
            RemoteDiscoveryEvent event = buildEvent(regInfo,discardMap,true);
            if(event != null) {
                queueEvent(regInfo,event);
                logInfoEvents("DiscardedEventTask.run(): "
                              +"DISCARDED Event SENT to regInfo\n");
            }
            return discardedRegs;
        }//end maybeSendDiscardedEvent

        /** This method returns true if the current instance of this class
         *  must be run after at least one task in the input task list with
         *  an index less than the <code>size</code> parameter (size may be
         *  less than tasks.size()).
         *  <p>
         *  Note that using List.get will be more efficient than List.iterator.
         *
         *  @param tasks the tasks to consider.  A read-only List, with all
         *         elements being an instanceof Task.
         *  @param size elements with index less than size should be considered
         */
        public boolean runAfter(List tasks, int size) {
            return false;
        }//end runAfter

    }//end class DiscardedEventTask

    /** This class represents a <code>Task</code> object that is placed
     *  in the <code>TaskManager</code> queue for processing in the thread
     *  pool. Instances of this class are placed on the task queue when
     *  registrations request that a given registrar be discarded. 
     *  <p>
     *  The <code>run</code> method of this class will remove the indicated
     *  registrar from the registration's set of discovered registrars and
     *  if successfully removed, will build and send a remote discarded event
     *  to the registration's listener.
     */
    private final class DiscardRegistrarTask implements TaskManager.Task {
        /** Data structure record corresponding to the registration that has
         *  requested to have one of its discovered registrars discarded
         */
        public final RegistrationInfo regInfo;
        /** The registrar to discard */
        public final ServiceRegistrar registrar;
        /** Constructs an instance of this class and stores the registration
         *  information.
         */
        public DiscardRegistrarTask(RegistrationInfo regInfo,
                                    ServiceRegistrar registrar)
        {
            this.regInfo   = regInfo;
            this.registrar = registrar;
        }//end constructor
        /** This method attempts to remove the indicated registrar from
         *  the registration's set of discovered registrars. If successful,
         *  this method builds and sends a remote discarded event to the
         *  registration's listener.
         */
        public void run() {
            concurrentObj.writeLock();
            try {
                logInfoTasks("DiscardRegistrarTask.run(): "
                             +"registrar requested to be discarded");
                /* Remove registrar from regInfo's set and send event */
                if( (regInfo.discoveredRegsMap).remove(registrar) != null) {
                    HashMap groupsMap = mapRegToGroups(registrar,
               ((LocatorGroupsStruct)allDiscoveredRegs.get(registrar)).groups);

                    RemoteDiscoveryEvent event = buildEvent
                                                      (regInfo,groupsMap,true);
                    if(event != null) {
                        queueEvent(regInfo,event);
                        logInfoEvents("DiscardRegistrarTask.run(): "
                                      +"DISCARDED Event was SENT\n");
                    }//endif
                    maybeRemoveDiscardedRegFromGlobalSet(registrar);
                }
            } finally {
                concurrentObj.writeUnlock();
            }
        }//end run

        /** This method returns true if the current instance of this class
         *  must be run after at least one task in the input task list with
         *  an index less than the <code>size</code> parameter (size may be
         *  less than tasks.size()).
         *  <p>
         *  Note that using List.get will be more efficient than List.iterator.
         *
         *  @param tasks the tasks to consider.  A read-only List, with all
         *         elements being an instanceof Task.
         *  @param size elements with index less than size should be considered
         */
        public boolean runAfter(List tasks, int size) {
            return false;
        }//end runAfter
    }//end class DiscardRegistrarTask

    /** This class represents a <code>Task</code> object that is placed
     *  in the <code>TaskManager</code> queue for processing in the thread
     *  pool.  An instance of this class is placed on the task queue when a
     *  <code>DiscoveryEvent</code> instance indicating a changed event
     *  is received from the local discovery process. 
     *  <p>
     *  The <code>run</code> method of this class will process event
     *  information resulting from a change in the state of the member
     *  groups of one or more lookup services (registrars). This task
     *  analyzes the group information in the event and, based on that
     *  information, determines which active registrations are no longer
     *  interested in the registrars referenced in the event. A
     *  <code>RemoteDiscoveryEvent</code> indicating a discarded event
     *  will be sent to each active registration that has lost interest
     *  in any of the registrars of the event.
     */
    private final class ChangedEventTask implements TaskManager.Task {
        /** The local event sent by the discovery manager. */
        public final DiscoveryEvent event;
        /** Constructs an instance of this class and stores the event*/
        public ChangedEventTask(DiscoveryEvent event) {
            this.event = event;
        }//end constructor
        /** This method processes the local discovery event information and
         *  determines, based on the current state of each active 
         *  registration, to which such registration the appropriate
         *  <code>RemoteDiscoveryEvent</code> should be sent. After making
         *  the determination, the remote event appropriate for each 
         *  registration is constructed and sent.
         */
        public void run() {
            concurrentObj.writeLock();
            logInfoTasks("ChangedEventTask.run(): processing CHANGED "
                         +"event from discovery manager");
            try {
                Map groupsMap = event.getGroups();
                HashSet allDiscardedRegs = new HashSet(groupsMap.size());
                HashMap locatorMap = new HashMap(groupsMap.size());
                /* Retrieve the locators of each registrar in the event */
                for(Iterator itr = (groupsMap.keySet()).iterator();
                                                            itr.hasNext(); )
                {
                    ServiceRegistrar reg = (ServiceRegistrar)itr.next();
                    locatorMap.put(reg,
                    ((LocatorGroupsStruct)allDiscoveredRegs.get(reg)).locator);
                }//end loop

                for( Iterator itr=registrationByID.values().iterator();
                                                              itr.hasNext(); )
                {
                    RegistrationInfo regInfo = (RegistrationInfo)itr.next();
                    HashSet discardedRegs = maybeSendDiscardedEvent
                                                (regInfo,groupsMap,locatorMap);
                    /* Transfer the just-discarded regs to the summary set */
                    for(Iterator jtr=discardedRegs.iterator();jtr.hasNext(); ){
                        allDiscardedRegs.add(jtr.next());
                    }//end loop
                }//end loop
                maybeRemoveDiscardedRegsFromGlobalSet(allDiscardedRegs);
                updateGroupsInGlobalSet(groupsMap); //replace with new groups
            } finally {
                concurrentObj.writeUnlock();
            }
        }//end run

        /** This method determines, based on the current state of the 
         * <code>regInfo</code> parameter, whether or not to send a
         * remote discarded event to the regInfo's listener, and then builds
         * and sends the event if appropriate. This method is called in
         * response to the receipt of a changed event from the discovery
         * manager. 
         * <p>
         * Such an event may indicate what is referred to as a
         * "passive, no interest" discard; passive because the event 
         * resulted from action taken by the discovery manager rather than
         * the client, and no interest because the discovery manager sends
         * such an event when it determines that one or more of the 
         * previously discovered registrars - although still reachable -
         * have changed their member groups in such a way that they may
         * now be of no interest to one or more of the client registrations.
         * <p>
         * Note that changed events can be sent for registrars having a
         * locator and member groups that the current regInfo never asked
         * to be discovered. This can happen because some other regInfo
         * asked that the registrar's locator or groups be discovered. 
         * <p>
         * If a particular registrar is contained in the discovered set 
         * of the given regInfo, then we know that that regInfo must
         * have requested discovery of the registrar (through either
         * locator or group discovery). If the registrar is not contained
         * in that set, then there's no need to proceed with the processing
         * of the registrar since we don't want to send a discarded event
         * to a regInfo that was never interested in that registrar in the
         * first place.
         * <p>
         * If the locator of the registrar is contained in the regInfo's
         * set of locators to discover, then that regInfo is considered
         * "still interested" in the registrar; and so no discarded event
         * is sent to the regInfo.
         * <p>
         * Thus, a discarded event is sent to the given regInfo only
         * if the regInfo is not interested in discovering the registrar
         * through locator discovery, and the registrar's member groups have
         * changed in such a way that it now belongs to groups that
         * the regInfo is not interested in discovering and joining.
         *
         * @param regInfo    the data structure record corresponding to the 
         *                   registration whose listener will receive the event
         * @param groupsMap  mapping from the registrars referenced in the 
         *                   just-received event to their corresponding set of
         *                   member groups
         * @param locatorMap mapping from the registrars referenced in the 
         *                   just-received event to their corresponding locator
         *
         * @return the registrars that were discarded for the given regInfo
         */
        private HashSet maybeSendDiscardedEvent(RegistrationInfo regInfo,
                                                Map groupsMap,
                                                Map locatorMap)
        {
            /* For each registrar discard candidate, send a discarded event if:
             *   The candidate is in the discovered set and
             *    a. regInfo is configured for at least group discovery
             *    b. candidate is NOT to be discovered by locator discovery
             *    c. regInfo is no longer interested in the candidate's groups
             */
            HashSet discardedRegs = new HashSet(groupsMap.size()); //return val
            /* If this regInfo isn't interested in groups, go to next regInfo*/
            if( (regInfo.groups != null) && ((regInfo.groups).size() == 0) )
            {
                return discardedRegs;
            }
            HashMap discardMap = new HashMap(groupsMap.size());
            /* loop thru the (registrar,groups) pairs, find regs to discard */
            Set eSet = groupsMap.entrySet();
            for(Iterator itr = eSet.iterator(); itr.hasNext(); ) {
                Map.Entry pair = (Map.Entry)itr.next();
                ServiceRegistrar reg = (ServiceRegistrar)pair.getKey();
                String[] regGroups = (String[])pair.getValue();
                LookupLocator regLoc = (LookupLocator)locatorMap.get(reg);
                /* Include the current reg in the discard map only if that
                 * reg is in the regInfo's discovered set, the regInfo
                 * is not interested in discovering the reg through locator
                 * discovery, and the reg's member groups have changed in
                 * such a way that it now belongs to groups that the regInfo
                 * is not interested in discovering and joining.
                 */
                if(    ( (regInfo.discoveredRegsMap).containsKey(reg) )
                    && (!interested(regLoc,regGroups,
                                         regInfo.locators,regInfo.groups)) )
                {
                    discardMap.put(reg,regGroups);
                    discardedRegs.add(reg);
                    (regInfo.discoveredRegsMap).remove(reg);
                }
            }//end loop
            /* Build and send the "discarded event" */
            RemoteDiscoveryEvent event = buildEvent(regInfo,discardMap,true);
            if(event != null) {
                queueEvent(regInfo,event);
                logInfoEvents("ChangedEventTask.run(): "
                              +"DISCARDED Event was SENT\n");
            }//endif
            return discardedRegs;
        }//end maybeSendDiscardedEvent

        /** This method returns true if the current instance of this class
         *  must be run after at least one task in the input task list with
         *  an index less than the <code>size</code> parameter (size may be
         *  less than tasks.size()).
         *  <p>
         *  Note that using List.get will be more efficient than List.iterator.
         *
         *  @param tasks the tasks to consider.  A read-only List, with all
         *         elements being an instanceof Task.
         *  @param size elements with index less than size should be considered
         */
        public boolean runAfter(List tasks, int size) {
            return false;
        }//end runAfter

    }//end class ChangedEventTask

    /** This class represents a <code>Task</code> object that is placed
     *  in the <code>TaskManager</code> queue for processing in the thread
     *  pool. Instances of this class are placed on the task queue when
     *  a registration has requested the augmentation of the set of groups
     *  that currently will be discovered for it.
     */
    private final class AddGroupsTask implements TaskManager.Task {
        /** Data structure record of the registration that made the request */
        public final RegistrationInfo regInfo;
        /** The group set with which to replace the registration's old set */
        public final String[] groups;
        /** Constructs an instance of this class and stores the input */
        public AddGroupsTask(RegistrationInfo regInfo, String[] groups) {
            this.regInfo = regInfo;
            this.groups  = groups;
        }//end constructor
        public void run() {
            /* For the regInfo associated with the current instance of this
             * task, do the following:
             * a. in the given regInfo data structure, add the new groups,
             *    with duplicates removed, to that regInfo's current set of
             *    desired groups
             * b. from the global mapping of all currently discovered 
             *    registrars to (locator,groups) pairs, retrieve the elements
             *    that contain registrars belonging to groups that regInfo
             *    should now be interested in as a result of the call to
             *    addGroups
             * c. for each registrar-to-(locator,groups) mapping retrieved in
             *    b. above, add that mapping to the given regInfo data
             *    structure's discovered state (these are the registrars that
             *    were previously discovered for OTHER regInfo's, not the
             *    current regInfo)
             * d. for each of the registrars previously discovered for other
             *    registrations that belong to any of the new groups regInfo
             *    is now interested in as a result of the call to addGroups,
             *    queue a remote discovery event to be sent to that regInfo's
             *    listener
             * e. if any of the new groups regInfo is now interested in as
             *    as a result of the call to addGroups were not previously
             *    in the local discovery manager's managed set of groups,
             *    (and the local discovery manager is currently not configured
             *    to discover ALL_GROUPS), add the new groups to the local
             *    discovery manager so that when that manager does discover
             *    one of those groups in the future, a remote discovered
             *    event will be sent to the given regInfo's listener
             */
            concurrentObj.writeLock();
            try {
                HashSet newGroupSet = addRegInfoGroups(regInfo,groups);  // a.
                if(newGroupSet.size() > 0) {
                    logInfoTasks("AddGroupsTask.run(): adding to the "
                                 +"registration's groups");
                    HashMap discoveredRegs = getDesiredRegsByGroup
                                                              (regInfo); // b.
                    HashMap regsAdded = regInfo.addToDiscoveredRegs
                                                       (discoveredRegs); // c.
                    RemoteDiscoveryEvent event = buildEvent
                                              (regInfo,regsAdded,false); // d.
                    if(event != null) {
                        queueEvent(regInfo,event);                       // d.
                        logInfoEvents("AddGroupsTask.run(): DISCOVERED "
                                      +"Event was SENT\n");
                    }//endif
                    updateDiscoveryMgrGroups();                          // e.
                }//endif(newGroupSet.size() > 0)
            } finally {
                concurrentObj.writeUnlock();
            }
        }//end run

        /** Augments the registration's managed set of groups with the new
         *  groups.
         *
         * @return the set of new groups added to regInfo's desired groups
         */
        private HashSet addRegInfoGroups(RegistrationInfo regInfo,
                                         String[] groups)
        {
            /* Build a HashSet (removes duplicates) from the input groups */
            HashSet newGroupSet = new HashSet(1);
            for(int i=0;i<groups.length;i++) {
                newGroupSet.add(groups[i]);
            }//end loop
            /* If the input set was not empty, add the new groups to the 
             * registration's managed set of groups.
             */
            if( newGroupSet.size() > 0 ) {
                (regInfo.groups).addAll(newGroupSet);
            }//endif
            return newGroupSet;
        }//end addRegInfoGroups

        /** This method returns true if the current instance of this class
         *  must be run after at least one task in the input task list with
         *  an index less than the <code>size</code> parameter (size may be
         *  less than tasks.size()).
         *  <p>
         *  Note that using List.get will be more efficient than List.iterator.
         *
         *  @param tasks the tasks to consider.  A read-only List, with all
         *         elements being an instanceof Task.
         *  @param size elements with index less than size should be considered
         */
        public boolean runAfter(List tasks, int size) {
            return false;
        }//end runAfter
    }//end class AddGroupsTask

    /** This class represents a <code>Task</code> object that is placed
     *  in the <code>TaskManager</code> queue for processing in the thread
     *  pool. Instances of this class are placed on the task queue when
     *  a registration has requested the replacement of the set of groups
     *  that currently will be discovered for it.
     */
    private final class SetGroupsTask implements TaskManager.Task {
        /** Data structure record of the registration that made the request */
        public final RegistrationInfo regInfo;
        /** The group set with which to replace the registration's old set */
        public final String[] groups;
        /** Constructs an instance of this class and stores the input */
        public SetGroupsTask(RegistrationInfo regInfo, String[] groups) {
            this.regInfo = regInfo;
            this.groups  = groups;
        }//end constructor
        public void run() {
            /* For the regInfo associated with the current instance of this
             * task, do the following:
             * a. from the global mapping of all currently discovered 
             *    registrars to their (locator,groups) pair, retrieve the
             *    elements that contain registrars belonging to groups that
             *    regInfo was interested in PRIOR to the call to setGroups
             * b. in the given regInfo data structure, replace that regInfo's
             *    current set of desired groups with the new set of desired
             *    groups that resulted from the call to setGroups
             * c. again from the global mapping of all currently discovered 
             *    registrars to (locator,groups) pairs, retrieve the elements
             *    that contain registrars belonging to groups that regInfo
             *    should now be interested in as a result of the call to
             *    setGroups
             * d. for each registrar-to-(locator,groups) mapping retrieved in
             *    c. above, add that mapping to the given regInfo data
             *    structure's state (these are the registrars that were
             *    previously discovered for OTHER regInfo's, not the current
             *    regInfo)
             * e. for each of the registrars previously discovered for other
             *    registrations that belong to any of the new groups regInfo
             *    is now interested in as a result of the call to setGroups,
             *    queue a remote discovery event to be sent to that regInfo's
             *    listener
             * f. from the mapping of already-discovered registrars that 
             *    regInfo was interested in prior to the call to setGroups
             *    (the mapping retrieved in a. above), retrieve the elements
             *    that contain registrars belonging to groups that regInfo is
             *    no longer interested in due to the call to setGroups
             * g. for each registrar-to-(locator,groups) mapping retrieved in
             *    f. above, remove that mapping from the given regInfo data
             *    structure's state, and queue a remote discarded event to be
             *    sent to that regInfo's listener
             * h. if any of the new groups regInfo is now interested in as
             *    as a result of the call to setGroups were not previously
             *    in the local discovery manager's managed set of groups,
             *    add those groups to that discovery manager so that when
             *    that manager does discover one of those groups in the  
             *    future, a remote discovered event will be sent to the given
             *    regInfo's listener
             */
            concurrentObj.writeLock();
            try {
                logInfoTasks("SetGroupsTask.run(): setting the "
                             +"registration's groups");
                Map oldDesiredRegs = getDesiredRegsByGroup(regInfo);     // a.
                setRegInfoGroups(regInfo,groups);                        // b.
                HashMap newDesiredRegs = getDesiredRegsByGroup(regInfo); // c.
                HashMap regsAdded = regInfo.addToDiscoveredRegs
                                                       (newDesiredRegs); // d.
                RemoteDiscoveryEvent event = buildEvent
                                              (regInfo,regsAdded,false); // e.
                if(event != null) {
                    queueEvent(regInfo,event);                           // e.
                    logInfoEvents("SetGroupsTask.run(): DISCOVERED "
                                  +"Event was SENT\n");
                }//endif
                Map discardRegs = getUndesiredRegsByGroup
                                               (oldDesiredRegs,regInfo); // f.
                for(Iterator itr = (discardRegs.keySet()).iterator();
                                                             itr.hasNext(); )
                {
                    (regInfo.discoveredRegsMap).remove(itr.next());     // g.
                }//end loop
                event = buildEvent(regInfo,discardRegs,true);           // g.
                if(event != null) {
                    queueEvent(regInfo,event);                          // g.
                    logInfoEvents("SetGroupsTask.run(): "
                                  +"DISCARDED Event was SENT\n");
                }//endif
                updateDiscoveryMgrGroups();                             // h.
            } finally {
                concurrentObj.writeUnlock();
            }
        }//end run

        /** Replaces the registration's managed set of groups with the new
         *  groups (even if the new set of groups is empty -- this just means
         *  group discovery will be "turned off" for this registration).
         */
        private void setRegInfoGroups(RegistrationInfo regInfo,
                                      String[] groups)
        {
            if(groups == DiscoveryGroupManagement.ALL_GROUPS) {
                regInfo.groups = null;
            } else {
                /* Build a HashSet from the input set */
                HashSet newGroups = new HashSet();
                for(int i=0;i<groups.length;i++) {
                    newGroups.add(groups[i]);
                }//end loop
                /* Prepare the registration's managed set for replacement */
                if(regInfo.groups == null) {
                    regInfo.groups = new HashSet();
                } else {
                    (regInfo.groups).clear();
                }//endif
                /* Replace the registration's managed set with the new set */
                (regInfo.groups).addAll(newGroups);
            }//end if (groups == DiscoveryGroupManagement.ALL_GROUPS)
        }//end setRegInfoGroups

        /** This method returns true if the current instance of this class
         *  must be run after at least one task in the input task list with
         *  an index less than the <code>size</code> parameter (size may be
         *  less than tasks.size()).
         *  <p>
         *  Note that using List.get will be more efficient than List.iterator.
         *
         *  @param tasks the tasks to consider.  A read-only List, with all
         *         elements being an instanceof Task.
         *  @param size elements with index less than size should be considered
         */
        public boolean runAfter(List tasks, int size) {
            return false;
        }//end runAfter
    }//end class SetGroupsTask

    /** This class represents a <code>Task</code> object that is placed
     *  in the <code>TaskManager</code> queue for processing in the thread
     *  pool. Instances of this class are placed on the task queue when
     *  a registration has requested the removal of a set of groups from
     *  the current set of groups to discover for it.
     */
    private final class RemoveGroupsTask implements TaskManager.Task {
        /** Data structure record of the registration that made the request */
        public final RegistrationInfo regInfo;
        /** The groups to remove from the registration's old set */
        public final String[] groups;
        /** Constructs an instance of this class and stores the input */
        public RemoveGroupsTask(RegistrationInfo regInfo, String[] groups) {
            this.regInfo = regInfo;
            this.groups  = groups;
        }//end constructor
        public void run() {
            concurrentObj.writeLock();
            try {
                if(groups.length == 0) return; // nothing from which to remove
               logInfoTasks("RemoveGroupsTask.run(): removing groups from "
                            +"the registration's current group set");
                /* regInfo's discovered regs (by group) previously desired */
                Map oldDesiredRegs = getDesiredRegsByGroup(regInfo);
                /* update regInfo's desired regs */
                removeRegInfoGroups(regInfo,groups);
                /* regInfo's discovered regs (by group) no longer desired */
                Map discardRegs = getUndesiredRegsByGroup(oldDesiredRegs,
                                                          regInfo);
                /* remove regInfo's undesired regs from its discovered map */
                for(Iterator itr = (discardRegs.keySet()).iterator();
                                                              itr.hasNext(); )
                {
                    (regInfo.discoveredRegsMap).remove(itr.next());
                }//end loop
                RemoteDiscoveryEvent event = buildEvent
                                                    (regInfo,discardRegs,true);
                if(event != null) {
                    queueEvent(regInfo,event);
                    logInfoEvents("RemoveGroupsTask.run(): "
                                  +"DISCARDED Event was SENT\n");
                }//endif
                updateDiscoveryMgrGroups(); // may send more discards
            } finally {
                concurrentObj.writeUnlock();
            }
        }//end run

        /** Removes the elements of the given set from the given registration's
         *  current set of groups to discover.
         */
        private void removeRegInfoGroups(RegistrationInfo regInfo,
                                         String[] groups)
        {
            HashSet removeSet = new HashSet();
            for(int i=0;i<groups.length;i++) {
                removeSet.add(groups[i]);
            }//end loop
            (regInfo.groups).removeAll(removeSet);
        }//end setRegInfoGroups

        /** This method returns true if the current instance of this class
         *  must be run after at least one task in the input task list with
         *  an index less than the <code>size</code> parameter (size may be
         *  less than tasks.size()).
         *  <p>
         *  Note that using List.get will be more efficient than List.iterator.
         *
         *  @param tasks the tasks to consider.  A read-only List, with all
         *         elements being an instanceof Task.
         *  @param size elements with index less than size should be considered
         */
        public boolean runAfter(List tasks, int size) {
            return false;
        }//end runAfter
    }//end class RemoveGroupsTask

    /** This class represents a <code>Task</code> object that is placed
     *  in the <code>TaskManager</code> queue for processing in the thread
     *  pool. Instances of this class are placed on the task queue when
     *  a registration has requested the augmentation of the set of locators
     *  that currently will be discovered for it.
     */
    private final class AddLocatorsTask implements TaskManager.Task {
        /** Data structure record of the registration that made the request */
        public final RegistrationInfo regInfo;
        /** The locator set with which to replace the registration's old set */
        public final LookupLocator[] locators;
        /** Constructs an instance of this class and stores the input */
        public AddLocatorsTask(RegistrationInfo regInfo,
                               LookupLocator[]  locators)
        {
            this.regInfo = regInfo;
            this.locators  = locators;
        }//end constructor
        public void run() {
            /* For the regInfo associated with the current instance of this
             * task, do the following:
             * a. in the given regInfo data structure, add the new locators,
             *    with duplicates removed, to that regInfo's current set of
             *    desired locators
             * b. from the global mapping of all currently discovered 
             *    registrars to (locator,groups) pairs, retrieve the elements
             *    that contain registrars having locators that regInfo
             *    should now be interested in as a result of the call to
             *    addLocators
             * c. for each registrar-to-(locator,groups) mapping retrieved in
             *    b. above, add that mapping to the given regInfo data
             *    structure's discovered state (these are the registrars that
             *    were previously discovered for OTHER regInfo's, not the
             *    current regInfo)
             * d. for each of the registrars previously discovered for other
             *    registrations that have locators equal to any of the new
             *    locators regInfo is now interested in as a result of the
             *    call to addLocators, queue a remote discovery event to be
             *    sent to that regInfo's listener
             * e. if any of the new locators regInfo is now interested in as
             *    as a result of the call to addLocators were not previously
             *    in the local discovery manager's managed set of locators,
             *    add the new locators to the local discovery manager so that
             *    when that manager does discover one of those locators in
             *    the future, a remote discovered event will be sent to the
             *    given regInfo's listener
             */
            concurrentObj.writeLock();
            try {
                HashSet newLocSet = addRegInfoLocators(regInfo,locators);// a.
                if(newLocSet.size() > 0) {
                    logInfoTasks("AddLocatorsTask.run(): adding to the "
                                 +"registration's locators");
                    HashMap discoveredRegs = getDesiredRegsByLocator
                                                              (regInfo); // b.
                    HashMap regsAdded = regInfo.addToDiscoveredRegs
                                                       (discoveredRegs); // c.
                    RemoteDiscoveryEvent event = buildEvent
                                              (regInfo,regsAdded,false); // d.
                    if(event != null) {
                        queueEvent(regInfo,event);                       // d.
                        logInfoEvents("AddLocatorsTask.run(): DISCOVERED "
                                      +"Event was SENT\n");
                    }//endif
                    updateDiscoveryMgrLocators();                        // e.
                }//endif(newLocSet.size() > 0)
            } finally {
                concurrentObj.writeUnlock();
            }
        }//end run

        /** Augments the registration's managed set of locators with the new
         *  locators.
         *
         * @return the set of new locators added to regInfo's desired locators
         */
        private HashSet addRegInfoLocators(RegistrationInfo regInfo,
                                           LookupLocator[]  locators)
        {
            /* Build a HashSet (removes duplicates) from the input locators */
            HashSet newLocSet = new HashSet(1);
            for(int i=0;i<locators.length;i++) {
                newLocSet.add(locators[i]);
            }//end loop
            /* If the input set was not empty, add the new locators to the 
             * registration's managed set of locators.
             */
            if( newLocSet.size() > 0 ) {
                (regInfo.locators).addAll(newLocSet);
            }//endif
            return newLocSet;
        }//end addRegInfoLocators

        /** This method returns true if the current instance of this class
         *  must be run after at least one task in the input task list with
         *  an index less than the <code>size</code> parameter (size may be
         *  less than tasks.size()).
         *  <p>
         *  Note that using List.get will be more efficient than List.iterator.
         *
         *  @param tasks the tasks to consider.  A read-only List, with all
         *         elements being an instanceof Task.
         *  @param size elements with index less than size should be considered
         */
        public boolean runAfter(List tasks, int size) {
            return false;
        }//end runAfter
    }//end class AddLocatorsTask

    /** This class represents a <code>Task</code> object that is placed
     *  in the <code>TaskManager</code> queue for processing in the thread
     *  pool. Instances of this class are placed on the task queue when
     *  a registration has requested the replacement of the set of locators
     *  that currently will be discovered for it.
     */
    private final class SetLocatorsTask implements TaskManager.Task {
        /** Data structure record of the registration that made the request */
        public final RegistrationInfo regInfo;
        /** The locator set with which to replace the registration's old set */
        public final LookupLocator[] locators;
        /** Constructs an instance of this class and stores the input */
        public SetLocatorsTask(RegistrationInfo regInfo,
                               LookupLocator[] locators)
        {
            this.regInfo  = regInfo;
            this.locators = locators;
        }//end constructor
        public void run() {
            /* For the regInfo associated with the current instance of this
             * task, do the following:
             * a. from the global mapping of all currently discovered 
             *    registrars to their (locator,groups) pair, retrieve the
             *    elements that contain registrars having locators that
             *    regInfo was interested in PRIOR to the call to setGroups
             * b. in the given regInfo data structure, replace that regInfo's
             *    current set of desired locators with the new set of desired
             *    locators that resulted from the call to setLocators
             * c. again from the global mapping of all currently discovered 
             *    registrars to (locator,groups) pairs, retrieve the elements
             *    that contain registrars having locators that regInfo should
             *    now be interested in as a result of the call to setLocators
             * d. for each registrar-to-(locator,groups) mapping retrieved in
             *    c. above, add that mapping to the given regInfo data
             *    structure's state (these are the registrars that were
             *    previously discovered for OTHER regInfo's, not the current
             *    regInfo)
             * e. for each of the registrars previously discovered for other
             *    registrations that have locators equal to the new locators
             *    regInfo is now interested in as a result of the call to
             *    setLocators, queue a remote discovery event to be sent to
             *    that regInfo's listener
             * f. from the mapping of already-discovered registrars that 
             *    regInfo was interested in prior to the call to setLocators
             *    (the mapping retrieved in a. above), retrieve the elements
             *    that contain registrars having locators that regInfo is
             *    no longer interested in due to the call to setLocators
             * g. for each registrar-to-(locator,groups) mapping retrieved in
             *    f. above, remove that mapping from the given regInfo data
             *    structure's state, and queue a remote discarded event to be
             *    sent to that regInfo's listener
             * h. if any of the new locators regInfo is now interested in as
             *    as a result of the call to setLocators were not previously
             *    in the local discovery manager's managed set of locators,
             *    add those locators to that discovery manager so that when
             *    that manager does discover one of those locators in the  
             *    future, a remote discovery event will be sent to the given
             *    regInfo's listener
             */
            concurrentObj.writeLock();
            try {
                logInfoTasks("SetLocatorsTask.run(): setting the "
                             +"registration's locators");
                Map oldDesiredRegs = getDesiredRegsByLocator(regInfo);    // a.
                setRegInfoLocators(regInfo,locators);                     // b.
                HashMap newDesiredRegs = getDesiredRegsByLocator(regInfo);// c.
                HashMap regsAdded = regInfo.addToDiscoveredRegs
                                                       (newDesiredRegs);  // d.
                RemoteDiscoveryEvent event = buildEvent
                                              (regInfo,regsAdded,false);  // e.
                if(event != null) {
                    queueEvent(regInfo,event);                            // e.
                    logInfoEvents("SetLocatorsTask.run(): DISCOVERED "
                                  +"Event was SENT\n");
                }//endif
                Map undesiredRegs = getUndesiredRegsByLocator
                                               (oldDesiredRegs,regInfo);  // f.
                HashMap discardRegs = new HashMap(undesiredRegs.size());
                for(Iterator itr = (undesiredRegs.keySet()).iterator();
                                                              itr.hasNext(); )
                {
                    ServiceRegistrar reg = (ServiceRegistrar)itr.next();
                    (regInfo.discoveredRegsMap).remove(reg);              // g.
                    discardRegs.put(reg,
                    ((LocatorGroupsStruct)allDiscoveredRegs.get(reg)).groups);

                }//end loop
                event = buildEvent(regInfo,discardRegs,true);             // g.
                if(event != null) {
                    queueEvent(regInfo,event);                            // g.
                    logInfoEvents("SetLocatorsTask.run(): "
                                  +"DISCARDED Event was SENT\n");
                }//endif
                updateDiscoveryMgrLocators();                             // h.
            } finally {
                concurrentObj.writeUnlock();
            }
        }//end run

        /** Replaces the registration's managed set of locators with the new
         *  locators (even if the new set of locators is empty -- this just
         *  means locator discovery will be "turned off" for this registration)
         */
        private void setRegInfoLocators(RegistrationInfo regInfo,
                                        LookupLocator[]  locators)
        {
            /* Build a HashSet from the input set */
            HashSet newLocSet = new HashSet();
            for(int i=0;i<locators.length;i++) {
                newLocSet.add(locators[i]);
            }//end loop
            /* Prepare the registration's managed set for replacement */
            if(regInfo.locators == null) {
                regInfo.locators = new HashSet();
            } else {
                (regInfo.locators).clear();
            }//endif
            /* Replace the registration's managed set with the new set */
            (regInfo.locators).addAll(newLocSet);
        }//end setRegInfoLocators

        /** This method returns true if the current instance of this class
         *  must be run after at least one task in the input task list with
         *  an index less than the <code>size</code> parameter (size may be
         *  less than tasks.size()).
         *  <p>
         *  Note that using List.get will be more efficient than List.iterator.
         *
         *  @param tasks the tasks to consider.  A read-only List, with all
         *         elements being an instanceof Task.
         *  @param size elements with index less than size should be considered
         */
        public boolean runAfter(List tasks, int size) {
            return false;
        }//end runAfter
    }//end class SetLocatorsTask

    /** This class represents a <code>Task</code> object that is placed
     *  in the <code>TaskManager</code> queue for processing in the thread
     *  pool. Instances of this class are placed on the task queue when
     *  a registration has requested the removal of a set of locators
     *  from the current set of locators to discover for it.
     */
    private final class RemoveLocatorsTask implements TaskManager.Task {
        /** Data structure record of the registration that made the request */
        public final RegistrationInfo regInfo;
        /** The locators to remove from the registration's old set */
        public final LookupLocator[] locators;
        /** Constructs an instance of this class and stores the input */
        public RemoveLocatorsTask(RegistrationInfo regInfo,
                                  LookupLocator[] locators)
        {
            this.regInfo  = regInfo;
            this.locators = locators;
        }//end constructor
        public void run() {
            concurrentObj.writeLock();
            try {
                if(locators.length == 0) return; //nothing from which to remove

                logInfoTasks("RemoveLocatorsTask.run(): removing locators "
                             +"from the registration's current locator set");
                /* regInfo's discovered regs (by locator) previously desired */
                Map oldDesiredRegs = getDesiredRegsByLocator(regInfo);
                /* update regInfo's desired regs */
                removeRegInfoLocators(regInfo,locators);
                /* regInfo's discovered regs (by locator) no longer desired */
                Map undesiredRegs = getUndesiredRegsByLocator
                                                    (oldDesiredRegs,regInfo);
                /* remove regInfo's undesired regs from its discovered map,
                 * and construct the registrars-to-groups map for the event
                 */
                HashMap discardRegs = new HashMap(undesiredRegs.size());
                for(Iterator itr = (undesiredRegs.keySet()).iterator();
                                                              itr.hasNext(); )
                {
                    ServiceRegistrar reg = (ServiceRegistrar)itr.next();
                    (regInfo.discoveredRegsMap).remove(reg);
                    discardRegs.put(reg,
                    ((LocatorGroupsStruct)allDiscoveredRegs.get(reg)).groups);
                }//end loop
                /* Construct the registrars-to-groups map for the event */
                RemoteDiscoveryEvent event = buildEvent
                                                    (regInfo,discardRegs,true);
                if(event != null) {
                    queueEvent(regInfo,event);
                    logInfoEvents("SetLocatorsTask.run(): "
                                  +"DISCARDED Event was SENT\n");
                }//endif
                updateDiscoveryMgrLocators(); // may send more discards
            } finally {
                concurrentObj.writeUnlock();
            }
        }//end run

        /** Removes the elements of the given set from the given registration's
         *  current set of locators to discover.
         */
        private void removeRegInfoLocators(RegistrationInfo regInfo,
                                           LookupLocator[]  locators)
        {
            /* Build a HashSet from the input set */
            HashSet removeSet = new HashSet();
            for(int i=0;i<locators.length;i++) {
                removeSet.add(locators[i]);
            }//end loop
            (regInfo.locators).removeAll(removeSet);
        }//end removeRegInfoLocators

        /** This method returns true if the current instance of this class
         *  must be run after at least one task in the input task list with
         *  an index less than the <code>size</code> parameter (size may be
         *  less than tasks.size()).
         *  <p>
         *  Note that using List.get will be more efficient than List.iterator.
         *
         *  @param tasks the tasks to consider.  A read-only List, with all
         *         elements being an instanceof Task.
         *  @param size elements with index less than size should be considered
         */
        public boolean runAfter(List tasks, int size) {
            return false;
        }//end runAfter
    }//end class RemoveLocatorsTask

    /** This class represents a <code>Task</code> object that is placed
     *  in the <code>TaskManager</code> queue for processing in the thread
     *  pool. Instances of this class are placed on the task queue when
     *  a remote event is to be sent to a given registration. 
     *  <p>
     *  Remote events are sent in a separate task such as this to avoid
     *  making the remote call to the registration's listener within a
     *  synchronization block.
     */
    private final class SendEventTask implements TaskManager.Task {
        /** Data structure record corresponding to registration to get event */
        public final RegistrationInfo     regInfo;
        /** The remote event to send to the given registration's listener */
        public final RemoteDiscoveryEvent event;
        /** Constructs an instance of this class and stores the registration
         *  information.
         */
        public SendEventTask(RegistrationInfo     regInfo,
                             RemoteDiscoveryEvent event)
        {
            this.regInfo = regInfo;
            this.event   = event;
        }//end constructor
        /** This method sends a <code>RemoteDiscoveryEvent</code> to the
         *  listener of the registration that corresponds to the
         *  <code>regInfo</code> field of this class. This method handles
         *  all exceptions and error conditions in the appropriate manner.
         */
        public void run() {
            try {
                regInfo.listener.notify(event);
            } catch (Throwable e) {
                problemLogger.log(Level.INFO, "Exception in SendEventTask", e);
                switch (ThrowableConstants.retryable(e)) {
                    case ThrowableConstants.BAD_OBJECT:
                        if(e instanceof Error)  throw (Error)e;
                    case ThrowableConstants.BAD_INVOCATION:
                    case ThrowableConstants.UNCATEGORIZED:
                    /* If the listener throws UnknownEvent or some other
                     * definite exception, or the listener is gone, it's
                     * okay to cancel the lease.
                     */
                    concurrentObj.writeLock();
                    try {
                        try {
                            logInfoEvents
                                       ("  Cancelling lease on registration: "
                                        +" (registrationID,leaseID) = ("
                                        +regInfo.registrationID+", "
                                        +regInfo.leaseID+")");
                            cancelLeaseDo(regInfo,regInfo.leaseID);
                            addLogRecord(new LeaseCancelledLogObj
                                                      (regInfo.registrationID,
                                                       regInfo.leaseID));
                        } catch (UnknownLeaseException ee) {
                        } catch (IOException ee) { }
                    } finally {
                        concurrentObj.writeUnlock();
                    }
                }//end switch
            }//end try
        }//end run

        /** This method returns true if the current instance of this class
         *  must be run after at least one task in the input task list with
         *  an index less than the <code>size</code> parameter (size may be
         *  less than tasks.size()).
         *  <p>
         *  Note that using List.get will be more efficient than List.iterator.
         *
         *  @param tasks the tasks to consider.  A read-only List, with all
         *         elements being an instanceof Task.
         *  @param size elements with index less than size should be considered
         */
        public boolean runAfter(List tasks, int size) {
            return false;
        }//end runAfter
    }//end class SendEventTask

    /**
     * Handler class for the persistent storage facility.
     * <p>
     * At any point during processing in this service, there will exist
     * both a 'snapshot' of the service's state and a set of records
     * detailing each significant change that has occurred to the state
     * since the snapshot was taken. The snapshot information and the
     * incremental change information will be stored in separate files
     * called, respectively, the snapshot file and the log file. Together,
     * these files are used to recover the state of the service after a
     * crash or a network outage (or if the service or its ActivationGroup
     * is un-registered and then re-registered through the Activation Daemon).
     * <p>
     * This class contains the methods that are used to record and recover
     * the snapshot of the service's state; as well as the method used to
     * apply the state changes that were recorded in the log file.
     * <p>
     * When the ReliableLog class is instantiated, a new instance of this
     * class is passed to its constructor so that the methods of this
     * class may be invoked by the methods defined in the ReliableLog.
     * Because this class extends the LogHandler class associated with
     * the ReliableLog class, this class must provide implementations of
     * the abstract methods declared in the LogHandler. Also, some of the
     * methods defined in this class override the methods of the LogHandler
     * in order to customize the handling of snapshot creation and
     * retrieval.
     * <p>
     * Each significant change to the service's state is written to the
     * log file as an individual record (when addLogRecord() is invoked).
     * After the number of records logged exceeds a pre-defined threshold,
     * a snapshot of the state is recorded by invoking -- through the
     * ReliableLog and its LogHandler -- the snapshot() method defined in
     * this class. After the snapshot is taken, the log file is cleared
     * and the incremental log process starts over.
     * <p>
     * The contents of the snapshot file reflect the DATA contained in
     * the fields making up the current state of the service. That data
     * represents many changes -- over time -- to the service's state.
     * On the other hand, each record written to the log file is an object
     * that reflects both the data used and the ACTIONS taken to make one
     * change to the service's state at a particular point in time.
     * <p>
     * During recovery, the state of the service at the time of a crash
     * or outage is re-constructed by first retrieving the 'base' state from
     * the snapshot file; and then modifying that base state according to
     * the records retrieved from the log file. The reconstruction of the
     * base state is achieved by invoking the recover() method defined in
     * this class. The modifications recorded in the log file are then
     * applied to the base state by invoking the applyUpdate() method
     * defined in this class. Both recover() and applyUpdate() are invoked
     * through the ReliableLog and its associated LogHandler.
     * <p>
     * NOTE: The following lines must be added to the service's policy file
     * <pre>
     *     permission java.io.FilePermission "dirname",   "read,write,delete";
     *     permission java.io.FilePermission "dirname/-", "read,write,delete";
     * </pre>
     *     where 'dirname' is the name of the directory path (relative or
     *     absolute) where the snapshot and log file will be maintained.
     */
    private class LocalLogHandler extends LogHandler {
        /** No-arg public constructor */
        public LocalLogHandler() { }

        /* Overrides snapshot() defined in ReliableLog's LogHandler class. */
        public void snapshot(OutputStream out) throws IOException {
            takeSnapshot(out);
        }//end snapshot

        /* Overrides recover() defined in ReliableLog's LogHandler class. */
        public void recover(InputStream in)
                            throws IOException, ClassNotFoundException
        {
            recoverSnapshot(in);
        }//end recover

        /**
         * Required method that implements the abstract applyUpdate()
         * defined in ReliableLog's associated LogHandler class.
         * <p>
         * During state recovery, the recover() method defined in the
         * ReliableLog class is invoked. That method invokes the method
         * recoverUpdates() which invokes the method readUpdates(). Both
         * of those methods are defined in ReliableLog. The method
         * readUpdates() retrieves a record from the log file and then
         * invokes this method.
         * <p>
         * This method invokes the version of the method apply() that
         * corresponds to the particular type of 'log record' object
         * that is input as the first argument. The log record object and its
         * corresponding apply() method are defined in one of the so-called
         * LogObj classes. Any instance of one the LogObj classes is an
         * implementation of the LogRecord interface. The particular
         * implementation that is input to this method is dependent on the
         * type of record that was originally logged. The apply() method
         * will then modify the state of the service in a way dictated
         * by the type of record that was retrieved.
         */
        public void applyUpdate(Object logRecObj) {
            ((LogRecord)logRecObj).apply(FiddlerImpl.this);
        }//end applyUpdate
    }//end class LocalLogHandler
    /* ******************* END Inner Class Definitions ********************* */

    /* ******************* BEGIN Thread Class Definitions ****************** */
    /** Thread which is used to monitor the current leases in effect and
     *  cancel (expire) those leases with expiration times that have exceeded
     *  the current time.
     */
    private class LeaseExpireThread extends InterruptedStatusThread {

        /** Create a daemon thread */
        public LeaseExpireThread() {
            super("lease expire");
            setDaemon(true);
        }//end constructor

        public void run() {
            try {
                concurrentObj.writeLock();
            } catch (ConcurrentLockException e) {
                return;
            }
            try {
                while (!hasBeenInterrupted()) {
                    long curTime  = System.currentTimeMillis();
                    minExpiration = Long.MAX_VALUE;
                    /* Loop through registrationByTime removing registrations
                     * with expiration times that are earlier than the current
                     * time. The logic of this loop relies on the fact that 
                     * registrationByTime is a TreeMap in which the elements
                     * are ordered (in ascending order) by the lease expiration
                     * times. Thus, when one registration is encountered with
                     * an expiration time that is later than the current time,
                     * it can be assumed that all remaining registrations have
                     * expiration times that are also later than the current
                     * time; and the loop can be exited. Until such a
                     * registration is encountered, each registration is
                     * removed from its various storage locations.
                     */
                    while (!registrationByTime.isEmpty()) {
                        RegistrationInfo regInfo
                             = (RegistrationInfo)registrationByTime.firstKey();
                        if (regInfo.leaseExpiration > curTime) {
                            minExpiration = regInfo.leaseExpiration;
                            break;
                        }
	                /* The removal of a registration typically involves the
                         * the modification of the managed sets in the
                         * discovery manager, which usually involves starting
                         * the discovery protocol. An IOException can occur
                         * when the discovery protocol fails to start. When
                         * such an exception does occur, register an ERROR
                         * status attribute (along with a Comment attribute
                         * describing the nature of the problem) to all lookup
                         * services with which this service is registered. 
                         *
                         * Administrative clients, as well as clients that use
                         * this service should have registered for notification
                         * of the existence of this attribute.
	                 */
                        try {
                            removeRegistration(regInfo);
                        } catch(IOException e) {
                            String eStr = "Failure while removing "
                                          +"registration (ID = "
                                          +regInfo.registrationID
                                          +") from service state";
                            if( problemLogger.isLoggable(Level.INFO) ) {
                                problemLogger.log(Level.INFO, eStr, e);
                            }//endif
                            Entry[] errorAttrs
                                    = new Entry[]
                                        { new FiddlerStatus(StatusType.ERROR),
                                          new Comment(eStr)
                                        };
                            joinMgr.addAttributes(errorAttrs,true);
                        }
                    }//end while
                    try {
                        concurrentObj.writerWait(leaseExpireThreadSyncObj,
                                                 (minExpiration - curTime));
                    } catch (ConcurrentLockException e) {
                        return;
                    }
                }//end while
            } finally {
                concurrentObj.writeUnlock();
            }
        }//end run
    }//end class LeaseExpireThread

    /**
     * Snapshot-taking thread. 
     * <p>
     * A snapshot is taken when -- after writing a new record to the 
     * log file -- it is determined that the size of the log file has 
     * exceeded a certain threshold. The code which adds the new record 
     * to the log file and which, in turn, decides that a snapshot
     * must be taken is "wrapped" in a writer mutex. That is, synchronization
     * of processing is achieved in this service through a "reader/writer"
     * mutex construct. This construct allows only one writer at any one
     * time; but allows an unlimited number of simultaneous readers as
     * long as no writer has locked the mutex. During steady-state, it is
     * anticipated that far more "read actions" will occur (e.g. discovery
     * events being sent) than "write actions" (e.g. modifying the managed
     * sets). Since the process of taking a snapshot can be time-consuming,
     * if the whole snapshot-taking process occupies that single writer
     * mutex, then a significant number of read actions will be un-necessarily
     * blocked; possibly resulting in an unacceptable degradation in
     * response time. 
     * <p>
     * It is for the above reason that the process of taking a snapshot is
     * performed in a separate thread. The thread waits on the monitor
     * belonging to the snapshotThreadSyncObj instance until it is notified
     * (or "signalled") that a snapshot must be taken. The notification
     * is sent by another thread, created by this service, which determines
     * when the conditions are right for a snapshot. The notification takes
     * the form of an interrupt indicating that the snapshot monitor is
     * available. Although the interrupt is sent while the writer mutex is
     * locked, the act of sending the notification is less time-consuming
     * than the act of taking the snapshot itself. When the thread receives
     * a notification, it awakens and requests a lock on the reader mutex
     * (this is all done in the readerWait() method). Because a reader -- not
     * a writer -- mutex is locked, read-only processes still have access
     * to the system state, so discovery events can be sent and the service's
     * state can be queried; but the reader mutex prevents changes to the
     * state while the snapshot is in progress.  
     * <p>
     * Note that the current snapshot is guaranteed to complete before the
     * next snapshot request is received. This is because even though
     * the act of taking a snapshot can be viewed as a writer process, 
     * the fact that the next snapshot notification will be wrapped in a
     * writer mutex, combined with the fact that a writer mutex can not
     * be locked while a reader mutex is locked, allows the snapshot to
     * be treated as a reader process.
     */
    private class SnapshotThread extends InterruptedStatusThread {

        /** Create a daemon thread */
        public SnapshotThread() {
            super("snapshot thread");
            setDaemon(true);
        }

        public void run() {
            try {
                concurrentObj.readLock();
            } catch (ConcurrentLockException e) {
                return;
            }
            try {
                while (!hasBeenInterrupted()) {
                    try {
                        concurrentObj.readerWait(snapshotThreadSyncObj,
                                                 Long.MAX_VALUE);
                        try {
                            log.snapshot();
                            logFileSize = 0;
                        } catch (Exception e) {
                            if (hasBeenInterrupted())  return;
                            /* If taking the snapshot fails for any reason,
                             * then register an ERROR status attribute (along
                             * with a Comment attribute describing the nature
                             * of the problem) to all lookup services with
                             * which this service is registered. 
                             *
                             * Administrative clients, as well as clients that
                             * use this service should have registered for
                             * notification of the existence of this attribute.
	                     */
                            String eStr = "Failure while taking a snapshot of "
                                          +"the service state";
                            problemLogger.log(Level.INFO, eStr, e);
                            Entry[] errorAttrs
                                    = new Entry[]
                                        { new FiddlerStatus(StatusType.ERROR),
                                          new Comment(eStr)
                                        };
                            joinMgr.addAttributes(errorAttrs,true);
                        }
                    } catch (ConcurrentLockException e) {
                        return;
                    }
                }//end while
            } finally {
                concurrentObj.readUnlock();
            }
        }//end run
    }//end class SnapshotThread

    /** Thread which is used to terminate the current executing instance
     *  of the Fiddler implementation of the lookup discovery service. 
     *  Termination processing is performed in a separate thread (that is,
     *  in an instance of this class) in order to avoid deadlock that 
     *  can occur because ActivationGroup.inactive will block until all 
     *  in-progress RMI calls have completed.
     */
    private class DestroyThread extends InterruptedStatusThread {
        /** Maximum delay for unexport attempts */
        private static final long MAX_UNEXPORT_DELAY = 2*TimeConstants.MINUTES;

	/** Constructor that creates a non-daemon thread */
	public DestroyThread() {
	    super("destroy");
	    /* override inheritance from RMI daemon thread */
	    setDaemon(false);
	}

	public void run() {
	    /* Must unregister before unexporting. Unregistering makes sure
             * that the object corresponding to the given activation ID can
             * no longer be activated through that ID.
             */
	    if (activationID != null) {
		try {
                    activationSystem.unregisterObject(activationID);
		} catch (RemoteException e) {
                    problemLogger.log(Level.WARNING, "aborting shutdown - "
                                     +"could not unregister activation ID", e);
		    return;//give up until we can at least unregister
		} catch (ActivationException e) {
                    problemLogger.log(Levels.HANDLED, "shutdown problem - "
                                     +"could not unregister activation ID", e);
                }
	    }
            readyState.shutdown();
            /* Unexport the object. This removes the object from the RMI
             * runtime so that the object can no longer accept incoming RMI
             * calls. 
             * 
             * An attempt to 'gracefully' unexport the object is initially
             * made. That is, for a finite period of time, an attempt is
             * made to allow all calls to the object that are in progress
             * or pending to complete before the object is unexported. If,
             * after that finite period of time, the object has not been
             * successfully unexported, the object is 'forcibly' unexported;
             * that is, the object is unexported even if there are calls to
             * the object that are in progress or still pending.
             */
            final long endTime = System.currentTimeMillis()+MAX_UNEXPORT_DELAY;
            boolean unexported = false;
                /* Unexport only if there are no pending or in-progress calls*/
                while(!unexported && (System.currentTimeMillis() < endTime)) {
                    unexported = serverExporter.unexport(false);
                    if(!unexported) Thread.yield();
                }//end loop
            if(!unexported) {//Not yet unexported. Forcibly unexport
                serverExporter.unexport(true);
            }//endif
	    /* all daemons must terminate before deleting persistent store */
	    leaseExpireThread.interrupt();
	    if(log != null) snapshotThread.interrupt();
	    taskMgr.terminate();
	    joinMgr.terminate();
            joinMgrLDM.terminate();
            discoveryMgr.terminate();
	    try {
		leaseExpireThread.join();
		if(log != null) snapshotThread.join();
	    } catch (InterruptedException e) { }
	    if(log != null) log.deletePersistentStore();
	    if (activationID != null) {
                /* Inform the activation system that the object corresponding
                 * to the given activation ID is no longer active.
                 */
		try {
		    ActivationGroup.inactive(activationID, serverExporter);
		} catch (RemoteException e) {
		} catch (ActivationException e) { }
            } else {//not activatable, tell starter it's ok to release for gc
                if(lifeCycle != null)  lifeCycle.unregister(FiddlerImpl.this);
            }//endif(activationID != null)
            /* If applicable, logout of the JAAS login session */
            if(loginContext != null) {
                try {
                    loginContext.logout();
                } catch(Exception e) {
                    startupLogger.log(Level.INFO,"Problem logging out of "
                                                 +"JAAS login session",e);
                }
            }//endif
            logInfoShutdown();
        }//end run
    }//end class DestroyThread
    /* ******************* END Thread Class Definitions ******************** */

    /* ************************ BEGIN Public Methods *********************** */
    /* -------------------------------------------------------------------- 
     * BEGIN net.jini.security.proxytrust.ServerProxyTrust
     */
    /** 
     * Returns a <code>TrustVerifier</code> specific to this service which
     * can be used to verify that a given proxy to this service can be
     * trusted.
     * <p>
     * The verifier returned by this method contains the method
     * {@link TrustVerifier#isTrustedObject isTrustedObject}. That method 
     * can be called with a candidate proxy as the first argument, and
     * {@link net.jini.security.TrustVerifier.Context}
     * as the second argument. When called in this way, the 
     * <code>isTrustedObject</code> determines whether or not the input
     * proxy is trusted. Thus, the verifier returned by this method should
     * be able to verify as trusted, all proxies to this service; including
     * proxies such as leases, event registrations, and administrative
     * proxies.
     * 
     * @return a <code>TrustVerifier</code> which can be used to verify that
     *         a given proxy to this service can be trusted.
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws UnsupportedOperationException if the server proxy does not
     *	       implement both
     *         {@link net.jini.core.constraint.RemoteMethodControl}
     *         and {@link TrustEquivalence}
     *
     * @see net.jini.security.proxytrust.ServerProxyTrust#getProxyVerifier
     */
    public TrustVerifier getProxyVerifier() throws NoSuchObjectException {
	readyState.check();
	return new ProxyVerifier(innerProxy, proxyID);
    }//end getProxyVerifier
    /*  END net.jini.security.proxytrust.ServerProxyTrust                   */
    /* -------------------------------------------------------------------- */

    /* -------------------------------------------------------------------- 
     * BEGIN net.jini.export.ProxyAccessor
     */
    /**
     * Public method that facilitates the use of the mechanism provided by
     * {@link com.sun.jini.start.ServiceStarter} to create an activatable
     * instance of this server.
     * 
     * @return the inner proxy (stub or dynamic proxy) for the server
     */
    public Object getProxy() {
        return innerProxy;
    }//end getProxy
    /*  END net.jini.export.ProxyAccessor                                   */
    /* -------------------------------------------------------------------- */

    /* -------------------------------------------------------------------- 
     * BEGIN com.sun.jini.fiddler.Fiddler --> net.jini.admin.Administrable
     */
    /** 
     * Returns a proxy to the current instance of this class through which
     * a client may administer the lookup discovery service
     *
     * @return a proxy object through which the lookup discovery service
     *         may be administered.
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server.
     *
     * see com.sun.jini.fiddler.FiddlerAdminProxy#getAdmin (?)
     * @see net.jini.admin.Administrable#getAdmin
     */
    public Object getAdmin() throws NoSuchObjectException, RemoteException {
	readyState.check();
        return adminProxy;
    }
    /*  END com.sun.jini.fiddler.Fiddler --> net.jini.admin.Administrable   */
    /* -------------------------------------------------------------------- */

    /* -------------------------------------------------------------------- 
     * BEGIN com.sun.jini.fiddler.Fiddler
     *                                --> com.sun.jini.fiddler.FiddlerAdmin
     *                                         --> net.jini.admin.JoinAdmin
     */
    /** 
     * Returns the current attribute sets for the lookup discovery service. 
     * 
     * @return array of net.jini.core.entry.Entry containing the current
     *         attribute sets for the lookup discovery service
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     *
     * @see com.sun.jini.fiddler.FiddlerAdminProxy#getLookupAttributes
     * @see net.jini.admin.JoinAdmin#getLookupAttributes
     */
    public Entry[] getLookupAttributes()
                                 throws NoSuchObjectException, RemoteException
    {
	readyState.check();
        concurrentObj.readLock();
        try {
            return thisServicesAttrs;
        } finally {
            concurrentObj.readUnlock();
        }
    }//end getLookupAttributes

    /** 
     * Adds attribute sets to the current set of attributes associated
     * with the lookup discovery service. The resulting set will be used
     * for all future registrations with lookup services. The new attribute
     * sets are also added to the lookup discovery service's attributes
     * on each lookup service with which the lookup discovery service
     * is currently registered.
     *
     * @param  attrSets array of net.jini.core.entry.Entry containing the
     *         attribute sets to add
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         attributes may or may not have been added successfully.
     *
     * @see com.sun.jini.fiddler.FiddlerAdminProxy#addLookupAttributes
     * @see net.jini.admin.JoinAdmin#addLookupAttributes
     */
    public void addLookupAttributes(Entry[] attrSets)
                                 throws NoSuchObjectException, RemoteException
    {
	readyState.check();
        concurrentObj.writeLock();
        try {
            joinMgr.addAttributes(attrSets, true);
            thisServicesAttrs = joinMgr.getAttributes();
	    addLogRecord(new LookupAttrsAddedLogObj(this,attrSets));
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end addLookupAttributes

    /** 
     * Modifies the current set of attributes associated with the lookup
     * discovery service. The resulting set will be used for all future
     * registrations with lookup services. The same modifications are 
     * also made to the lookup discovery service's attributes on each
     * lookup service with which the lookup discovery service is currently
     * registered.
     *
     * @param  attrSetTemplates  array of net.jini.core.entry.Entry containing
     *         the templates to use for selecting the attributes (contained
     *         within the set of existing attributes) that are to be
     *         modified
     * @param  attrSets array of net.jini.core.entry.Entry containing the
     *         modifications to make to matching sets
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         attributes may or may not have been modified successfully.
     *
     * @see com.sun.jini.fiddler.FiddlerAdminProxy#modifyLookupAttributes
     * @see net.jini.admin.JoinAdmin#modifyLookupAttributes
     */
    public void modifyLookupAttributes(Entry[] attrSetTemplates,
				       Entry[] attrSets)
                                 throws NoSuchObjectException, RemoteException
    {
	readyState.check();
        concurrentObj.writeLock();
        try {
            joinMgr.modifyAttributes(attrSetTemplates, attrSets, true);
            thisServicesAttrs = joinMgr.getAttributes();
            addLogRecord
               (new LookupAttrsModifiedLogObj(this,attrSetTemplates,attrSets));
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end modifyLookupAttributes

    /** 
     * Get the names of the groups whose members are lookup services the
     * lookup discovery services wishes to register with (join).
     * 
     * @return String array containing the names of the groups whose members
     *         are lookup services the lookup discovery service wishes to
     *         join.
     * <p>
     *         If the array returned is empty, the lookup discovery service
     *         is configured to join no groups. If null is returned, the
     *         lookup discovery service is configured to join all groups.
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     *
     * @see com.sun.jini.fiddler.FiddlerAdminProxy#getLookupGroups
     * @see net.jini.admin.JoinAdmin#getLookupGroups
     */
    public String[] getLookupGroups()
                                 throws NoSuchObjectException, RemoteException
    {
	readyState.check();
        concurrentObj.readLock();
        try {
            return thisServicesGroups;
        } finally {
            concurrentObj.readUnlock();
        }
    }//end getLookupGroups

    /** 
     * Add new names to the set consisting of the names of groups whose
     * members are lookup services the lookup discovery service wishes
     * to register with (join). Any lookup services belonging to the
     * new groups that the lookup discovery service has not yet registered
     * with, will be discovered and joined.
     *
     * @param  groups String array containing the names of the groups to add
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         group names may or may not have been added successfully.
     *
     * @see com.sun.jini.fiddler.FiddlerAdminProxy#addLookupGroups
     * @see net.jini.admin.JoinAdmin#addLookupGroups
     */
    public void addLookupGroups(String[] groups)
                                 throws NoSuchObjectException, RemoteException
    {
	readyState.check();
        concurrentObj.writeLock();
        try {
            try {
                ((DiscoveryGroupManagement)joinMgrLDM).addGroups(groups);
            } catch (IOException e) {
                throw new RuntimeException(e.toString());
            }
            thisServicesGroups
                         = ((DiscoveryGroupManagement)joinMgrLDM).getGroups();
            addLogRecord(new LookupGroupsChangedLogObj(thisServicesGroups));
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end addLookupGroups

    /** 
     * Remove a set of group names from lookup discovery service's managed
     * set of groups (the set consisting of the names of groups whose
     * members are lookup services the lookup discovery service wishes
     * to join). Any leases granted to the lookup discovery service by
     * lookup services that are not members of the groups whose names 
     * remain in the managed set will be cancelled at those lookup services.
     *
     * @param  groups String array containing the names of the groups to remove
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         group names may or may not have been removed successfully.
     *
     * @see com.sun.jini.fiddler.FiddlerAdminProxy#removeLookupGroups
     * @see net.jini.admin.JoinAdmin#removeLookupGroups
     */
    public void removeLookupGroups(String[] groups)
                                 throws NoSuchObjectException, RemoteException
    {
	readyState.check();
        concurrentObj.writeLock();
        try {
            ((DiscoveryGroupManagement)joinMgrLDM).removeGroups(groups);
            thisServicesGroups
                         = ((DiscoveryGroupManagement)joinMgrLDM).getGroups();
            addLogRecord(new LookupGroupsChangedLogObj(thisServicesGroups));
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end removeLookupGroups

    /** 
     * Replace the lookup discovery service's managed set of groups with a
     * new set of group names. Any leases granted to the lookup discovery
     * service by lookup services that are not members of the groups whose
     * names are in the new managed set will be cancelled at those lookup
     * services. Lookup services that are members of groups reflected in
     * the new managed set will be discovered and joined.
     *
     * @param  groups String array containing the names of the new groups
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         group names may or may not have been replaced successfully.
     *
     * @see com.sun.jini.fiddler.FiddlerAdminProxy#setLookupGroups
     * @see net.jini.admin.JoinAdmin#setLookupGroups
     */
    public void setLookupGroups(String[] groups)
                                 throws NoSuchObjectException, RemoteException
    {
	readyState.check();
        concurrentObj.writeLock();
        try {
            try {
                ((DiscoveryGroupManagement)joinMgrLDM).setGroups(groups);
            } catch (IOException e) {
                throw new RuntimeException(e.toString());
            }
            thisServicesGroups 
                         = ((DiscoveryGroupManagement)joinMgrLDM).getGroups();
            addLogRecord(new LookupGroupsChangedLogObj(thisServicesGroups));
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end setLookupGroups

    /** 
     * Get the lookup discovery service's managed set of locators. The
     * managed set of locators is the set of LookupLocator objects
     * corresponding to the specific lookup services with which the lookup
     * discovery service wishes to register (join).
     * 
     * @return array of objects of type net.jini.core.discovery.LookupLocator,
     *         each of which corresponds to a specific lookup service the
     *         lookup discovery service wishes to join.
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     *
     * @see com.sun.jini.fiddler.FiddlerAdminProxy#getLookupLocators
     * @see net.jini.admin.JoinAdmin#getLookupLocators
     */
    public LookupLocator[] getLookupLocators()
                                 throws NoSuchObjectException, RemoteException
    {
	readyState.check();
        concurrentObj.readLock();
        try {
            return thisServicesLocators;
        } finally {
            concurrentObj.readUnlock();
        }
    }//end getLookupLocators

    /** 
     * Add a set of LookupLocator objects to the lookup discovery service's
     * managed set of locators. The managed set of locators is the set of
     * LookupLocator objects corresponding to the specific lookup services
     * with which the lookup discovery service wishes to register (join).
     * <p>
     * Any lookup services corresponding to the new locators that the lookup
     * discovery service has not yet joined, will be discovered and joined.
     *
     * @param  locators array of net.jini.core.discovery.LookupLocator objects to add
     *         to the managed set of locators
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         new locators may or may not have been added successfully.
     *
     * @see com.sun.jini.fiddler.FiddlerAdminProxy#addLookupLocators
     * @see net.jini.admin.JoinAdmin#addLookupLocators
     */
    public void addLookupLocators(LookupLocator[] locators)
                                 throws NoSuchObjectException, RemoteException
    {
	readyState.check();
        /* Prepare outside of sync block because of possible remote call */
        prepareNewLocators(locatorToJoinPreparer,locators);
        concurrentObj.writeLock();
        try {
            ((DiscoveryLocatorManagement)joinMgrLDM).addLocators(locators);
            thisServicesLocators 
                     = ((DiscoveryLocatorManagement)joinMgrLDM).getLocators();
            addLogRecord
                     (new LookupLocatorsChangedLogObj(thisServicesLocators));
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end addLookupLocators

    /** 
     * Remove a set of LookupLocator objects from the lookup discovery
     * service's managed set of locators. The managed set of locators is the
     * set of LookupLocator objects corresponding to the specific lookup
     * services with which the lookup discovery service wishes to register
     * (join).
     * <p>
     * Note that any leases granted to the lookup discovery service by
     * lookup services that do not correspond to any of the locators
     * remaining in the managed set will be cancelled at those lookup
     * services.
     *
     * @param  locators array of net.jini.core.discovery.LookupLocator objects to
     *         remove from the managed set of locators
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         new locators may or may not have been removed successfully.
     *
     * @see com.sun.jini.fiddler.FiddlerAdminProxy#removeLookupLocators
     * @see net.jini.admin.JoinAdmin#removeLookupLocators
     */
    public void removeLookupLocators(LookupLocator[] locators)
                                 throws NoSuchObjectException, RemoteException
    {
	readyState.check();
        /* Prepare outside of sync block because of possible remote call */
        prepareNewLocators(locatorToJoinPreparer,locators);
        concurrentObj.writeLock();
        try {
            ((DiscoveryLocatorManagement)joinMgrLDM).removeLocators(locators);
            thisServicesLocators 
                     = ((DiscoveryLocatorManagement)joinMgrLDM).getLocators();
            addLogRecord
                     (new LookupLocatorsChangedLogObj(thisServicesLocators));
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end removeLookupLocators

    /** 
     * Replace the lookup discovery service's managed set of locators with
     * a new set of locators. The managed set of locators is the set of
     * LookupLocator objects corresponding to the specific lookup services
     * with which the lookup discovery service wishes to register (join).
     * <p>
     * Note that any leases granted to the lookup discovery service by
     * lookup services whose corresponding locator is removed from the
     * managed set will be cancelled at those lookup services. The lookup
     * services corresponding to the new locators in the managed set
     * will be discovered and joined.
     *
     * @param  locators array of net.jini.core.discovery.LookupLocator objects with
     *         which to replace the current managed set of locators
     *         remove from the managed set of locators
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         locators in the managed set may or may not have been replaced
     *         successfully.
     *
     * @see com.sun.jini.fiddler.FiddlerAdminProxy#setLookupLocators
     * @see net.jini.admin.JoinAdmin#setLookupLocators
     */
    public void setLookupLocators(LookupLocator[] locators)
                                 throws NoSuchObjectException, RemoteException
    {
	readyState.check();
        /* Prepare outside of sync block because of possible remote call */
        prepareNewLocators(locatorToJoinPreparer,locators);
        concurrentObj.writeLock();
        try {
            ((DiscoveryLocatorManagement)joinMgrLDM).setLocators(locators);
            thisServicesLocators 
                    = ((DiscoveryLocatorManagement)joinMgrLDM).getLocators();
            addLogRecord
                     (new LookupLocatorsChangedLogObj(thisServicesLocators));
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end setLookupLocators
    /* END com.sun.jini.fiddler.Fiddler --> com.sun.jini.fiddler.FiddlerAdmin
     *                                           --> net.jini.admin.JoinAdmin
     * -------------------------------------------------------------------- */

    /* -------------------------------------------------------------------- 
     * BEGIN com.sun.jini.fiddler.Fiddler
     *                            --> com.sun.jini.fiddler.FiddlerAdmin
     *                                  --> com.sun.jini.admin.DestroyAdmin
     */
    /**
     * Destroy the lookup discovery service, if possible, including its
     * persistent storage. This method will typically spawn a separate
     * thread to do the actual work asynchronously, so a successful
     * return from this method usually does not mean that the service
     * has been destroyed.
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         lookup discovery service may or may not have been successfully
     *         destroyed.
     *
     * @see com.sun.jini.fiddler.FiddlerAdminProxy#destroy
     * @see com.sun.jini.admin.DestroyAdmin#destroy
     */
    public void destroy() throws NoSuchObjectException, RemoteException {
	readyState.check();
        destroyDo();
    }//end destroy
    /* END com.sun.jini.fiddler.Fiddler
     *                          --> com.sun.jini.fiddler.FiddlerAdmin
     *                                  --> com.sun.jini.admin.DestroyAdmin
     * -------------------------------------------------------------------- */

    /* -------------------------------------------------------------------- 
     * BEGIN com.sun.jini.fiddler.Fiddler
     *                                --> com.sun.jini.fiddler.FiddlerAdmin
     */
    /**
     * Changes the least upper bound applied to all lease durations granted
     * by the lookup discovery service.
     * <p>
     * This method is a mechanism for an entity with the appropriate
     * privileges to administratively change the value of the least upper
     * bound that will be applied by the Fiddler implementation of the lookup
     * discovery service when determining the duration to assign to the lease
     * on a requested registration.
     *
     * @param newBound <code>long</code> value representing the new least
     *        upper bound (in milliseconds) on the set of all possible
     *        lease durations that may be granted
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         bound value may or may not have been changed successfully.
     *
     * @see com.sun.jini.fiddler.FiddlerAdminProxy#setLeaseBound
     * @see com.sun.jini.fiddler.FiddlerAdmin#setLeaseBound
     */
    public void setLeaseBound(long newBound)
                                 throws NoSuchObjectException, RemoteException
    {
	readyState.check();
        concurrentObj.writeLock();
        try {
	    if (newBound > leaseMax) {
                throw new IllegalArgumentException("max duration exceeded");
            }//endif
            leaseBound = newBound;
            addLogRecord(new LeaseBoundSetLogObj(newBound));
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end setLeaseBound

    /**
     * Retrieves the least upper bound applied to all lease durations granted
     * by the lookup discovery service.
     *
     * @return <code>long</code> value representing the current least
     *         upper bound (in milliseconds) on the set of all possible
     *         lease durations that may be granted
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     *
     * @see com.sun.jini.fiddler.FiddlerAdminProxy#getLeaseBound
     * @see com.sun.jini.fiddler.FiddlerAdmin#getLeaseBound
     */
    public long getLeaseBound() throws NoSuchObjectException, RemoteException {
	readyState.check();
        concurrentObj.readLock();
        try {
            return leaseBound;
        } finally {
            concurrentObj.readUnlock();
        }
    }//end getLeaseBound

    /**
     * Change the weight factor applied by the lookup discovery service
     * to the snapshot size during the test to determine whether or not
     * to take a "snapshot" of the system state.
     *
     * @param weight weight factor for snapshot size
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         weight factor may or may not have been changed successfully.
     *
     * @see com.sun.jini.fiddler.FiddlerAdminProxy#setPersistenceSnapshotWeight
     * @see com.sun.jini.fiddler.FiddlerAdmin#setPersistenceSnapshotWeight
     */
    public void setPersistenceSnapshotWeight(float weight)
                               throws NoSuchObjectException, RemoteException
    {
	readyState.check();
        concurrentObj.writeLock();
        try {
            snapshotWt = weight;
            addLogRecord(new SnapshotWeightSetLogObj(weight));
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end setPersistenceSnapshotWeight

    /**
     * Retrieve the weight factor applied by the lookup discovery service
     * to the snapshot size during the test to determine whether or not to
     * take a "snapshot" of the system state.
     * 
     * @return float value corresponding to the weight factor for snapshot
     *         size
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     *
     * @see com.sun.jini.fiddler.FiddlerAdminProxy#getPersistenceSnapshotWeight
     * @see com.sun.jini.fiddler.FiddlerAdmin#getPersistenceSnapshotWeight
     */
    public float getPersistenceSnapshotWeight()
                                 throws NoSuchObjectException, RemoteException
    {
	readyState.check();
        concurrentObj.readLock();
        try {
            return snapshotWt;
        } finally {
            concurrentObj.readUnlock();
        }
    }//end getPersistenceSnapshotWeight

    /**
     * Change the value of the size threshold of the snapshot; which is
     * employed by the lookup discovery service in the test to determine
     * whether or not to take a "snapshot" of the system state.
     *
     * @param threshold size threshold for taking a snapshot
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         threshold may or may not have been changed successfully.
     *
     * @see com.sun.jini.fiddler.FiddlerAdminProxy
     *                                       #setPersistenceSnapshotThreshold
     * @see com.sun.jini.fiddler.FiddlerAdmin#setPersistenceSnapshotThreshold
     */
    public void setPersistenceSnapshotThreshold(int threshold)
                                 throws NoSuchObjectException, RemoteException
    {
	readyState.check();
        concurrentObj.writeLock();
        try {
            snapshotThresh = threshold;
            addLogRecord(new SnapshotThresholdSetLogObj(threshold));
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end setPersistenceSnapshotThreshold

    /**
     * Retrieve the value of the size threshold of the snapshot; which is
     * employed by the lookup discovery service in the test to determine
     * whether or not to take a "snapshot" of the system state.
     * 
     * @return int value corresponding to the size threshold of the snapshot
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     *
     * @see com.sun.jini.fiddler.FiddlerAdminProxy
     *                                       #getPersistenceSnapshotThreshold
     * @see com.sun.jini.fiddler.FiddlerAdmin#getPersistenceSnapshotThreshold
     */
    public int getPersistenceSnapshotThreshold()
                                 throws NoSuchObjectException, RemoteException
    {
	readyState.check();
        concurrentObj.readLock();
        try {
	    return snapshotThresh;
        } finally {
            concurrentObj.readUnlock();
        }
    }//end getPersistenceSnapshotThreshold

    /* END com.sun.jini.fiddler.Fiddler --> com.sun.jini.fiddler.FiddlerAdmin
     * -------------------------------------------------------------------- */

    /* -------------------------------------------------------------------- 
     * BEGIN com.sun.jini.fiddler.Fiddler
     *                          --> com.sun.jini.start.ServiceProxyAccessor
     */
    /**
     * Public method that facilitates the use of the mechanism provided by
     * {@link com.sun.jini.start.ServiceStarter} to create an activatable
     * instance of this server.
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @return the outer (smart) proxy for the server
     */
    public Object getServiceProxy() throws NoSuchObjectException {
	readyState.check();
        return outerProxy;
    }//end getServiceProxy

    /*  END com.sun.jini.fiddler.Fiddler 
     *                        --> com.sun.jini.start.ServiceProxyAccessor   */
    /* -------------------------------------------------------------------- */

    /* -------------------------------------------------------------------- 
     * BEGIN com.sun.jini.fiddler.Fiddler
     */
    /**
     * Returns the unique identifier generated (or recovered) by the backend
     * implementation of the lookup discovery service when an instance of
     * that service is constructed. This ID is typically used to determine
     * equality between the proxies of any two instances of the lookup
     * discovery service.
     * 
     * @return the unique ID that was generated (or recovered) by the
     *         backend implementation of the lookup discovery service
     *         at creation time
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server. When this exception does occur, the registration may
     *         or may not have completed successfully.
     */
    public Uuid getProxyID() throws NoSuchObjectException, RemoteException {
	readyState.check();
        return proxyID;
    }//end getProxyID

    /**
     * Registers with the lookup discovery service. When a client invokes
     * this method, it requests that the lookup discovery service perform
     * discovery processing on its behalf.
     *
     * @param groups        String array, none of whose elements may be null,
     *                      consisting of zero or more names of groups to
     *                      which lookup services to discover belong.
     *                      A null value or an empty array
     *                      (DiscoveryGroupManagement.ALL_GROUPS or 
     *                      DiscoveryGroupManagement.NO_GROUPS) are both
     *                      acceptable.
     * @param locators      array of zero or more non-null LookupLocator
     *                      objects, each corresponding to a specific lookup
     *                      service to discover. If either the empty array
     *                      or null is passed to this argument, then no
     *                      locator discovery will be performed for the
     *                      associated registration.
     * @param listener      a non-null instance of RemoteEventListener. This 
     *                      argument specifies the entity that will receive
     *                      events notifying the registration that a lookup
     *                      service of interest has been discovered. A 
     *                      non-null value must be passed to this argument,
     *                      otherwise a NullPointerException will be thrown
     *                      and the registration.
     * @param handback      null or an instance of MarshalledObject. This
     *                      argument specifies an object that will be 
     *                      included in the notification event that the
     *                      lookup discovery service sends to the registered
     *                      listener.
     * @param leaseDuration long value representing the amount of time (in
     *                      milliseconds) for which the resources of the
     *                      lookup discovery service are being requested.
     *
     * @return an instance of FiddlerRegistration which implements the
     *         LookupDiscoveryRegistration interface, and acts as a proxy
     *         to the registration-related methods of the backend server
     *         of the Fiddler implementation of the lookup discovery service
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server. When this exception does occur, the registration may
     *         or may not have completed successfully.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         null is input to the <code>listener</code> parameter, as well
     *         as when one or more of the elements of the <code>groups</code>
     *         parameter is null.
     *
     * @throws java.lang.IllegalArgumentException this exception occurs when
     *         the value input to the <code>leaseDuration</code> parameter
     *         is neither positive, Lease.FOREVER, nor Lease.ANY.
     *
     * @see net.jini.discovery.LookupDiscoveryService
     */
    public LookupDiscoveryRegistration register(String[] groups,
                                                LookupLocator[] locators,
                                                RemoteEventListener listener,
                                                MarshalledObject handback,
                                                long leaseDuration)
                                                  throws NoSuchObjectException,
                                                         RemoteException
    {
	readyState.check();
        /* The spec says that a null locators array implies no loc discovery */
        if( locators == null) {
            locators = new LookupLocator[0];
        }//endif
        if(containsNullElement(groups)) {
            throw new NullPointerException(" on call to register() method, at "
                                          +"least one null element in groups");
        } else if (containsNullElement(locators)) {
            throw new NullPointerException(" on call to register() method, at "
                                        +"least one null element in locators");
        } else if (listener == null) {
            throw new NullPointerException(" null listener input to "
                                           +"register() method");
        }//endif
        /* Prepare the locators associated with the requested registration 
         * outside of the sync block because of possible remote call.
         */
        prepareNewLocators(locatorToDiscoverPreparer,locators);
        LookupDiscoveryRegistration reg = null;
        concurrentObj.writeLock();
        try {
            /*  Grant the registration request and add the registration to
             *  to this service's state.
             *
             *  The addition of a registration to this service's state
             *  typically involves the modification of the managed sets in 
             *  the discovery manager, which usually involves starting the 
             *  discovery protocol. An IOException can occur when the 
             *  discovery protocol fails to start. When such an exception 
             *  does occur, register an ERROR status attribute (along with 
             *  a Comment attribute describing the nature of the problem) to 
             *  all lookup services with which this service is registered. 
             *
             *  Administrative clients, as well as clients that use this 
             *  service should have registered for notification of the 
             *  existence of this attribute.
             */
            reg = registerDo(groups,locators,listener,handback,leaseDuration);
        } catch(RemoteException e) {
            /* Catch, log, and rethrow so RemoteException is not included
             * in the catch block for IOException below.
             */
            problemLogger.log(Level.INFO,
                              "cannot grant registration request", e);
            throw e;
        } catch(IOException e) {
            problemLogger.log(Level.INFO, "cannot grant registration "
                              +"request - multicast problem", e);
            Entry[] errorAttrs 
                  = new Entry[] { new FiddlerStatus(StatusType.ERROR),
                                  new Comment("Failure during registration")
                                };
            joinMgr.addAttributes(errorAttrs,true);
        } finally {
            concurrentObj.writeUnlock();
        }
        return reg;
    }//end register

    /**
     * This method is the "backend" server counterpart to the method of
     * the same name provided by the <code>LookupDiscoveryRegistration</code> 
     * proxy (an instance of <code>FiddlerRegistration</code>) that is
     * returned by this service when a client requests a registration.
     * <p>
     * This method returns an array consisting of proxies to the lookup
     * service(s) that have already been discovered for the registration
     * corresponding to the <code>registrationID</code> input parameter.
     * Each element of the return set is a marshalled instance of the
     * <code>ServiceRegistrar</code> interface.
     *
     * @param registrationID unique identifier assigned to the registration
     *                       from which the set of registrars is being 
     *                       retrieved
     * 
     * @return an array of MarshalledObject objects where each element is
     *         is a marshalled instance of ServiceRegistrar.
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     * 
     * @throws com.sun.jini.proxy.ThrowThis which is a non-remote "wrapper"
     *         class used to wrap various remote exceptions (for example,
     *         NoSuchObjectException) that this method wishes to throw.
     *         When a service is implemented as a smart proxy with a
     *         backend server, and a method on the backend which was invoked
     *         through the proxy wishes to explicitly throw a particular
     *         remote exception, it cannot simply throw that exception if
     *         it wishes that exception to be visible to the proxy running
     *         on the "client side". This is because when the backend throws
     *         any remote exception, the RMI sub-system automatically wraps
     *         that exception in a java.rmi.ServerException. Thus, the proxy
     *         will only be able to "see" the ServerException (the actual
     *         exception that the backend tried to throw is "buried" in the
     *         detail field of the ServerException). Thus, in order to allow
     *         the proxy access to the actual remote exception this method
     *         throws, that exception wraps the desired remote exception in
     *         the non-remote exception ThrowThis; which will not be wrapped
     *         in a ServerException.
     *
     *         This method throws a NoSuchObjectException wrapped in a
     *         ThrowThis exception whenever the <code>registrationID</code>
     *         parameter references an invalid or non-existent registration.
     *
     * @see com.sun.jini.fiddler.FiddlerRegistration#getRegistrars
     * @see net.jini.discovery.LookupDiscoveryRegistration#getRegistrars
     */
    public MarshalledObject[] getRegistrars(Uuid registrationID)
                       throws NoSuchObjectException, RemoteException, ThrowThis
    {
	readyState.check();
        concurrentObj.readLock();
        try {
            RegistrationInfo regInfo
                   = (RegistrationInfo)(registrationByID.get(registrationID));
            if(regInfo == null) {
                throw new ThrowThis
                        (new NoSuchObjectException("Invalid registration "
                                                   +"ID on call to "
                                                   +"getRegistrars() method"));
            }//endif
            Collection mVals = (regInfo.discoveredRegsMap).values(); 
            return ( (MarshalledObject[])(mVals).toArray
                                        (new MarshalledObject[mVals.size()]) );
        } finally {
            concurrentObj.readUnlock();
        }
    }//end getRegistrars

    /**
     * This method is the "backend" server counterpart to the method of
     * the same name provided by the <code>LookupDiscoveryRegistration</code> 
     * proxy (an instance of <code>FiddlerRegistration</code>) that is
     * returned by this service when a client requests a registration.
     * <p>
     * This method returns an array consisting of the names of the groups
     * whose members are lookup services the lookup discovery service will
     * attempt to discover for the registration corresponding to the current
     * instance of this class. This set of group names is referred to as the
     * registration's 'managed set of groups'.
     * <p>
     * If the registration's managed set of groups is currently empty, then
     * the empty array is returned. If the lookup discovery service currently
     * has no managed set of groups for the registration through which the
     * request is being made, then null will be returned.
     *
     * @param registrationID unique identifier assigned to the registration
     *                       from which the set of groups is being retrieved
     * 
     * @return a String array containing the elements of the managed set of
     *         groups for the registration.
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     * 
     * @throws java.rmi.NoSuchObjectException wrapped in an instance of
     *         com.sun.jini.proxy.ThrowThis exception whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration. Refer to the description of the
     *         <code>getRegistrars</code> method for more information on
     *         this exception.
     *
     * @see com.sun.jini.fiddler.FiddlerRegistration#getGroups
     * @see net.jini.discovery.LookupDiscoveryRegistration#getGroups
     */
    public String[] getGroups(Uuid registrationID) 
                       throws NoSuchObjectException, RemoteException, ThrowThis
    {
	readyState.check();
        concurrentObj.readLock();
        try {
            RegistrationInfo regInfo
                   = (RegistrationInfo)(registrationByID.get(registrationID));
            if(regInfo == null) {
                throw new ThrowThis
                            (new NoSuchObjectException("Invalid registration "
                                                       +"ID on call to "
                                                       +"getGroups() method"));
            }//endif
            String[] groups = null;
            if(regInfo.groups == null) {
                groups = DiscoveryGroupManagement.ALL_GROUPS;
            } else {
                groups = (String[])(regInfo.groups).toArray
                                           (new String[regInfo.groups.size()]);
            }//endif (regInfo.groups == null)
            return groups;
        } finally {
            concurrentObj.readUnlock();
        }
    }//end getGroups

    /**
     * This method is the "backend" server counterpart to the method of
     * the same name provided by the <code>LookupDiscoveryRegistration</code> 
     * proxy (an instance of <code>FiddlerRegistration</code>) that is
     * returned by this service when a client requests a registration.
     * <p>
     * This method returns an array consisting of the the LookupLocator
     * objects corresponding to specific lookup services the lookup discovery
     * service will attempt to discover for for the registration
     * corresponding to the current instance of this class. This set of
     * locators is referred to as the registration's 'managed set of locators'.
     * <p>
     * If the registration's managed set of locators is currently empty, then
     * the empty array is returned. If the lookup discovery service currently
     * has no managed set of locators for the registration through which the
     * request is being made, then null will be returned.
     *
     * @param registrationID unique identifier assigned to the registration
     *                       from which the set of locators is being retrieved
     * 
     * @return array consisting of net.jini.core.discovery.LookupLocator
     *         objects corresponding to the elements of the managed set of
     *         locators for the registration.
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     * 
     * @throws java.rmi.NoSuchObjectException wrapped in an instance of
     *         com.sun.jini.proxy.ThrowThis exception whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration. Refer to the description of the
     *         <code>getRegistrars</code> method for more information on
     *         this exception.
     *
     * @see com.sun.jini.fiddler.FiddlerRegistration#getLocators
     * @see net.jini.discovery.LookupDiscoveryRegistration#getLocators
     */
    public LookupLocator[] getLocators(Uuid registrationID)
                       throws NoSuchObjectException, RemoteException, ThrowThis
    {
	readyState.check();
        concurrentObj.readLock();
        try {
            RegistrationInfo regInfo
                   = (RegistrationInfo)(registrationByID.get(registrationID));
            if(regInfo == null) {
                throw new ThrowThis
                        (new NoSuchObjectException("Invalid registration "
                                                   +"ID on call to "
                                                   +"getLocators() method"));
            }//endif
            return (LookupLocator[])(regInfo.locators).toArray
                                  (new LookupLocator[regInfo.locators.size()]);
        } finally {
            concurrentObj.readUnlock();
        }
    }//end getLocators

    /**
     * This method is the "backend" server counterpart to the method of
     * the same name provided by the <code>LookupDiscoveryRegistration</code> 
     * proxy (an instance of <code>FiddlerRegistration</code>) that is
     * returned by this service when a client requests a registration.
     * <p>
     * This method first tests the input set of group names for validity and 
     * throws the appropriate exception should any irregularities be found.
     * It then adds the input set of group names to the managed set of groups
     * associated with the registration.
     * 
     * @param registrationID unique identifier assigned to the registration
     *                       to which the set of groups being augmented
     *                       corresponds
     * @param groups         a String array, none of whose elements may be
     *                       null, consisting of the group names with which to
     *                       augment the registration's managed set of groups.
     * <p>
     *                       If any element of this parameter duplicates any
     *                       other element of this parameter, the duplicate
     *                       will be ignored. If any element of this parameter
     *                       duplicates any element of the registration's
     *                       current managed set of groups, the duplicate will
     *                       be ignored.
     * <p>
     *                       If the empty set is input, then the registration's
     *                       managed set of groups will not change. If null is
     *                       input, this method will throw a
     *                       <code>NullPointerException</code>.
     * 
     * @throws java.lang.IllegalStateException this exception occurs when
     *         the <code>addGroups</code> method of the discovery
     *         manager is invoked after the <code>terminate</code> method 
     *         of that manager is called. When this happens, in addition to
     *         propagating this exception, this method also registers an 
     *         ERROR status attribute (along with a Comment attribute
     *         describing the nature of the problem) with all lookup services
     *         with which this service is registered. Administrative clients,
     *         as well as clients that use this service should register
     *         for notification of the existence of this attribute.
     * 
     * @throws java.lang.UnsupportedOperationException this exception 
     *         occurs when the registration corresponding to the
     *         <code>registrationID</code> parameter has no managed set of
     *         groups to which to add the elements of the input parameter.
     *         That is, the registration's current managed set of groups is
     *         null. When a registration's managed set of groups is null,
     *         it means that all groups are being discovered for that 
     *         registration; thus, requesting that a set of groups be added
     *         to the set of all groups makes no sense.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         either null is input to the <code>groups</code> parameter, 
     *         or one or more of the elements of the <code>groups</code> 
     *         parameter is null. If a null <code>groups</code> parameter 
     *         is input, the registration is requesting that all groups be 
     *         added to its current managed set of groups; which is not 
     *         allowed. (Note that if a registration wishes to change its 
     *         managed set of groups from a finite set of names to "all 
     *         groups", it should invoke setGroups with a null input.)
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         registration's managed set of groups may or may not have been
     *         successfully augmented.
     *
     * @throws java.rmi.NoSuchObjectException wrapped in an instance of
     *         com.sun.jini.proxy.ThrowThis exception whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration. Refer to the description of the
     *         <code>getRegistrars</code> method for more information on
     *         this exception.
     *
     * @see com.sun.jini.fiddler.FiddlerRegistration#addGroups
     * @see net.jini.discovery.LookupDiscoveryRegistration#addGroups
     */
    public void addGroups(Uuid registrationID, String[] groups)
                       throws NoSuchObjectException, RemoteException, ThrowThis
    {
	readyState.check();
        concurrentObj.writeLock();
        try {
            RegistrationInfo regInfo
                   = (RegistrationInfo)(registrationByID.get(registrationID));
            if(regInfo == null) {
                throw new ThrowThis
                        (new NoSuchObjectException("Invalid registration "
                                                   +"ID on call to "
                                                   +"addGroups() method"));
            }//endif
            /* Check the input for validity */
            if(groups == null) { // asking that all groups be added
                throw new NullPointerException(" on call to addGroups() "
                                               +"method, cannot add "
                                               +"'ALL_GROUPS' (the null set) "
                                               +"to a registration's set of "
                                               +"groups to discover");
            } else if(containsNullElement(groups)) { // null element
                throw new NullPointerException(" on call to addGroups() "
                                               +"method, at least one null "
                                               +"element in groups parameter");
            } else if (regInfo.groups == null) { // all groups being discovered
                throw new UnsupportedOperationException
                                              (" on call to addGroups() "
                                               +"method, cannot add a set of"
                                               +"groups to a set already "
                                               +"configured for 'ALL_GROUPS' "
                                               +"(the null set)");
            }//endif
            /* Augment the current set of groups with the input set */
            addGroupsDo(regInfo, groups);
            addLogRecord(new GroupsAddedToRegistrationLogObj
                                             (regInfo.registrationID,groups));
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end addGroups

    /**
     * This method is the "backend" server counterpart to the method of
     * the same name provided by the <code>LookupDiscoveryRegistration</code> 
     * proxy (an instance of <code>FiddlerRegistration</code>) that is
     * returned by this service when a client requests a registration.
     * <p>
     * This method first tests the input set of group names for validity and 
     * throws the appropriate exception should any irregularities be found.
     * It then queues a <code>SetGroupsTask</code> which performs the 
     * actual replacement.
     * <p>
     * 
     * @param registrationID unique identifier assigned to the registration
     *                       to which the set of groups being replaced
     *                       corresponds
     * @param groups         a String array, none of whose elements may be 
     *                       null, consisting of the group names with which to 
     *                       replace the names in this registration's managed 
     *                       set of groups.
     * <p>
     *                       If any element of this parameter duplicates any 
     *                       other element of this parameter, the duplicate 
     *                       will be ignored.
     * <p>
     *                       If the empty set is input, then group discovery 
     *                       for the registration will cease. If null is input,
     *                       the lookup discovery service will attempt to 
     *                       discover all as yet undiscovered lookup services 
     *                       located within its multicast radius and, upon 
     *                       discovery of any such lookup service, will send 
     *                       to the registration's listener an event signaling
     *                       that discovery.
     * 
     * @throws java.lang.NullPointerException this exception occurs when one
     *         or more of the elements of the groups parameter is null.
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         registration's managed set of groups may or may not have been
     *         successfully replaced.
     *
     * @throws java.rmi.NoSuchObjectException wrapped in an instance of
     *         com.sun.jini.proxy.ThrowThis exception whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration. Refer to the description of the
     *         <code>getRegistrars</code> method for more information on
     *         this exception.
     *
     * @see com.sun.jini.fiddler.FiddlerRegistration#setGroups
     * @see net.jini.discovery.LookupDiscoveryRegistration#setGroups
     */
    public void setGroups(Uuid registrationID, String[] groups)
                       throws NoSuchObjectException, RemoteException, ThrowThis
    {
	readyState.check();
        concurrentObj.writeLock();
        try {
            RegistrationInfo regInfo
                   = (RegistrationInfo)(registrationByID.get(registrationID));
            if(regInfo == null) {
                throw new ThrowThis
                        (new NoSuchObjectException("Invalid registration "
                                                   +"ID on call to "
                                                   +"setGroups() method"));
            }//endif
            /* Check the input for validity */
            if(containsNullElement(groups)) { // null element
                throw new NullPointerException(" on call to setGroups() "
                                               +"method, at least one null "
                                               +"element in groups parameter");
            } else if ((groups == null) && (regInfo.groups == null)) {
                /* null input, but already set to ALL groups; do nothing */
                return;
            }//endif
            /* Replace the current groups with the current groups */
            setGroupsDo(regInfo, groups);
            addLogRecord(new GroupsSetInRegistrationLogObj
                                             (regInfo.registrationID,groups));
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end setGroups

    /**
     * This method is the "backend" server counterpart to the method of
     * the same name provided by the <code>LookupDiscoveryRegistration</code> 
     * proxy (an instance of <code>FiddlerRegistration</code>) that is
     * returned by this service when a client requests a registration.
     * <p>
     * This method first tests the input set of group names for validity and 
     * throws the appropriate exception should any irregularities be found.
     * It then queues a <code>RemoveGroupsTask</code> which performs the 
     * actual removal.
     * 
     * @param registrationID unique identifier assigned to the registration
     *                       to which the set of groups being removed
     *                       corresponds
     * @param groups         a String array, none of whose elements may be
     *                       null, consisting of the group names to delete 
     *                       from the registration's managed set of groups.
     * <p>
     *                       If any element of this parameter duplicates any 
     *                       other element of this parameter, the duplicate 
     *                       will be ignored. If any element of this parameter
     *                       is not currently contained in the registration's
     *                       managed set, no action is taken with respect to
     *                       that element.
     * <p>
     *                       If the empty set is input, the registration's 
     *                       managed set of groups will not change. If null is 
     *                       input, this method will throw a 
     *                       <code>NullPointerException</code>.
     * 
     * @throws java.lang.UnsupportedOperationException this exception 
     *         occurs when the registration corresponding to the
     *         <code>registrationID</code> parameter has no managed set 
     *         of groups from which to remove elements of the input parameter.
     *         That is, the registration's current managed set of groups is
     *         null. Thus, requesting that a set of groups be removed from
     *         null set makes no sense. 
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         either null is input to the <code>groups</code> parameter, 
     *         or one or more of the elements of the <code>groups</code> 
     *         parameter is null. If a null <code>groups</code> parameter 
     *         is input, the registration is requesting that all groups be 
     *         removed from its current managed set of groups; which is not 
     *         allowed. (Note that if a registration wishes to change its 
     *         managed set of groups from "all groups" to "no groups", it
     *         it should invoke setGroups with a zero length 
     *         <code>String</code> input.)
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         registration's managed set of groups may or may not have been
     *         successfully modified.
     * 
     * @throws java.rmi.NoSuchObjectException wrapped in an instance of
     *         com.sun.jini.proxy.ThrowThis exception whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration. Refer to the description of the
     *         <code>getRegistrars</code> method for more information on
     *         this exception.
     *
     * @see com.sun.jini.fiddler.FiddlerRegistration#removeGroups
     * @see net.jini.discovery.LookupDiscoveryRegistration#removeGroups
     */
    public void removeGroups(Uuid registrationID, String[] groups)
                       throws NoSuchObjectException, RemoteException, ThrowThis
    {
	readyState.check();
        concurrentObj.writeLock();
        try {
            RegistrationInfo regInfo
                   = (RegistrationInfo)(registrationByID.get(registrationID));
            if(regInfo == null) {
                throw new ThrowThis
                        (new NoSuchObjectException("Invalid registration "
                                                   +"ID on call to "
                                                   +"removeGroups() method"));
            }//endif
            /* Check the input for validity */
            if(groups == null) { // asking that all groups be removed
                throw new NullPointerException(" on call to removeGroups() "
                                               +"method, cannot remove "
                                               +"'ALL_GROUPS' (the null set) "
                                               +"from a registration's set of "
                                               +"groups to discover");
            } else if(containsNullElement(groups)) { // null element
                throw new NullPointerException(" on call to removeGroups() "
                                               +"method, at least one null "
                                               +"element in groups parameter");
            } else if (regInfo.groups == null) { // all groups being discovered
                throw new UnsupportedOperationException
                                            (" on call to removeGroups() "
                                             +"method, cannot remove a set of"
                                             +"groups from a set already "
                                             +"configured for 'ALL_GROUPS' "
                                             +"(the null set)");
            }//endif
            /* Remove the requested groups */
            removeGroupsDo(regInfo, groups);
            logInfoGroups("\nAfter Group Removal --");
            addLogRecord(new GroupsRemovedFromRegistrationLogObj
                                             (regInfo.registrationID,groups));
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end removeGroups

    /**
     * This method is the "backend" server counterpart to the method of
     * the same name provided by the <code>LookupDiscoveryRegistration</code> 
     * proxy (an instance of <code>FiddlerRegistration</code>) that is
     * returned by this service when a client requests a registration.
     * <p>
     * This method first tests the input set of group names for validity and 
     * throws the appropriate exception should any irregularities be found.
     * It then adds the input set of LookupLocator objects to the managed set
     * of locators associated with the registration.
     * 
     * @param registrationID unique identifier assigned to the registration
     *                       to which the set of locators being augmented
     *                       corresponds
     * @param locators       an array, none of whose elements may be null, 
     *                       consisting of the LookupLocator objects with 
     *                       which to augment the registration's managed set 
     *                       of locators.
     * <p>
     *                       If any element of this parameter duplicates any 
     *                       other element of this parameter, the duplicate 
     *                       will be ignored. If any element of this parameter 
     *                       duplicates any element of the registration's 
     *                       managed set of locators, the duplicate will be 
     *                       ignored.
     * <p>
     *                       If the empty set is input, then the registration's
     *                       managed set of locators will not change. If null
     *                       is input, this method will throw a
     *                       <code>NullPointerException</code>.
     * 
     * @throws java.lang.IllegalStateException this exception occurs when
     *         the <code>addLocators</code> method of the discovery
     *         manager is invoked after the <code>terminate</code> method 
     *         of that manager is called. When this happens, in addition to
     *         propagating this exception, this method also registers an 
     *         ERROR status attribute (along with a Comment attribute
     *         describing the nature of the problem) with all lookup services
     *         with which this service is registered. Administrative clients,
     *         as well as clients that use this service should register
     *         for notification of the existence of this attribute.
     * 
     * @throws java.lang.NullPointerException this exception occurs when
     *         either null is input to the <code>locators</code> parameter, 
     *         or one or more of the elements of the <code>locators</code> 
     *         parameter is null.
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         registration's managed set of locators may or may not have
     *         been successfully augmented.
     * 
     * @throws java.rmi.NoSuchObjectException wrapped in an instance of
     *         com.sun.jini.proxy.ThrowThis exception whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration. Refer to the description of the
     *         <code>getRegistrars</code> method for more information on
     *         this exception.
     *
     * @see com.sun.jini.fiddler.FiddlerRegistration#addLocators
     * @see net.jini.discovery.LookupDiscoveryRegistration#addLocators
     */
    public void addLocators(Uuid registrationID, LookupLocator[] locators)
                       throws NoSuchObjectException, RemoteException, ThrowThis

    {
	readyState.check();
        /* Prepare outside of sync block because of possible remote call */
        prepareNewLocators(locatorToDiscoverPreparer,locators);
        concurrentObj.writeLock();
        try {
            RegistrationInfo regInfo
                   = (RegistrationInfo)(registrationByID.get(registrationID));
            if(regInfo == null) {
                throw new ThrowThis
                        (new NoSuchObjectException("Invalid registration "
                                                   +"ID on call to "
                                                   +"addLocators() method"));
            }//endif
            /* Check the input for validity */
            if(locators == null) {
                throw new NullPointerException(" on call to addLocators() "
                                               +"method, cannot add null "
                                               +"to a registration's set of "
                                               +"locators to discover");
            } else if(containsNullElement(locators)) { // null element
                throw new NullPointerException(" on call to addLocators() "
                                             +"method, at least one null "
                                             +"element in locators parameter");
            }//endif(locators == null)
            /* Augment the current set of locators with the input set */
            addLocatorsDo(regInfo, locators);
            addLogRecord(new LocsAddedToRegistrationLogObj
                                           (regInfo.registrationID,locators));
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end addLocators

    /**
     * This method is the "backend" server counterpart to the method of
     * the same name provided by the <code>LookupDiscoveryRegistration</code> 
     * proxy (an instance of <code>FiddlerRegistration</code>) that is
     * returned by this service when a client requests a registration.
     * <p>
     * This method first tests the input set of locators for validity and 
     * throws the appropriate exception should any irregularities be found.
     * It then queues a <code>SetLocatorsTask</code> which performs the 
     * actual replacement.
     * 
     * @param registrationID unique identifier assigned to the registration
     *                       to which the set of locators being replaced
     *                       corresponds
     * @param locators       an array, none of whose elements may be null,
     *                       consisting of the LookupLocator objects with 
     *                       which to replace the locators in the 
     *                       registration's managed set of locators.
     * <p>
     *                       If any element of this parameter duplicates any 
     *                       other element of this parameter, the duplicate 
     *                       will be ignored.
     * <p>
     *                       If the empty array is input, then locator 
     *                       discovery for the registration will cease. If 
     *                       null is input, this method will throw a 
     *                       <code>NullPointerException</code>.
     * 
     * @throws java.lang.NullPointerException this exception occurs when
     *         either null is input to the <code>locators</code> parameter, 
     *         or one or more of the elements of the <code>locators</code> 
     *         parameter is null.
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         registration's managed set of locators may or may not have
     *         been successfully replaced.
     * 
     * @throws java.rmi.NoSuchObjectException wrapped in an instance of
     *         com.sun.jini.proxy.ThrowThis exception whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration. Refer to the description of the
     *         <code>getRegistrars</code> method for more information on
     *         this exception.
     *
     * @see com.sun.jini.fiddler.FiddlerRegistration#setLocators
     * @see net.jini.discovery.LookupDiscoveryRegistration#setLocators
     */
    public void setLocators(Uuid registrationID, LookupLocator[] locators)
                       throws NoSuchObjectException, RemoteException, ThrowThis
    {
	readyState.check();
        /* Prepare outside of sync block because of possible remote call */
        prepareNewLocators(locatorToDiscoverPreparer,locators);
        concurrentObj.writeLock();
        try {
            RegistrationInfo regInfo
                   = (RegistrationInfo)(registrationByID.get(registrationID));
            if(regInfo == null) {
                throw new ThrowThis
                        (new NoSuchObjectException("Invalid registration "
                                                   +"ID on call to "
                                                   +"setLocators() method"));
            }//endif
            /* Check the input for validity */
            if(locators == null) {
                throw new NullPointerException(" on call to setLocators() "
                                               +"method, cannot replace a "
                                               +"registration's current set "
                                               +"of locators with null");
            } else if(containsNullElement(locators)) { // null element
                throw new NullPointerException(" on call to setLocators() "
                                             +"method, at least one null "
                                             +"element in locators parameter");
            }//endif(locators == null)
            setLocatorsDo(regInfo, locators);
            addLogRecord(new LocsSetInRegistrationLogObj
                                           (regInfo.registrationID,locators));
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end setLocators

    /**
     * This method is the "backend" server counterpart to the method of
     * the same name provided by the <code>LookupDiscoveryRegistration</code> 
     * proxy (an instance of <code>FiddlerRegistration</code>) that is
     * returned by this service when a client requests a registration.
     * <p>
     * This method first tests the input set of locators for validity and 
     * throws the appropriate exception should any irregularities be found.
     * It then queues a <code>RemoveLocatorsTask</code> which performs the 
     * actual removal.
     * 
     * @param registrationID unique identifier assigned to the registration
     *                       to which the set of locators being removed
     *                       corresponds
     * @param locators       an array, none of whose elements may be null,
     *                       consisting of the LookupLocator objects to remove
     *                       from the registration's managed set of locators.
     * <p>
     *                       If any element of this parameter duplicates any
     *                       other element of this parameter, the duplicate
     *                       will be ignored.
     * <p>
     *                       If the empty set is input, the managed set of
     *                       locators will not change. If null is input,
     *                       this method will throw a 
     *                       <code>NullPointerException</code>.
     * 
     * @throws java.lang.NullPointerException this exception occurs when
     *         either null is input to the <code>locators</code> parameter, 
     *         or one or more of the elements of the <code>locators</code> 
     *         parameter is null.
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         registration's managed set of locators may or may not have
     *         been successfully modified.
     * 
     * @throws java.rmi.NoSuchObjectException wrapped in an instance of
     *         com.sun.jini.proxy.ThrowThis exception whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration. Refer to the description of the
     *         <code>getRegistrars</code> method for more information on
     *         this exception.
     *
     * @see com.sun.jini.fiddler.FiddlerRegistration#removeLocators
     * @see net.jini.discovery.LookupDiscoveryRegistration#removeLocators
     */
    public void removeLocators(Uuid registrationID, LookupLocator[] locators)
                       throws NoSuchObjectException, RemoteException, ThrowThis
    {
	readyState.check();
        /* Prepare outside of sync block because of possible remote call */
        prepareNewLocators(locatorToDiscoverPreparer,locators);
        concurrentObj.writeLock();
        try {
            RegistrationInfo regInfo
                   = (RegistrationInfo)(registrationByID.get(registrationID));
            if(regInfo == null) {
                throw new ThrowThis
                       (new NoSuchObjectException("Invalid registration "
                                                  +"ID on call to "
                                                  +"removeLocators() method"));
            }//endif
            /* Check the input for validity */
            if(locators == null) {
                throw new NullPointerException(" on call to removeLocators() "
                                               +"method, cannot remove null "
                                               +"from a registration's set of "
                                               +"locators to discover");
            } else if(containsNullElement(locators)) { // null element
                throw new NullPointerException(" on call to removeLocators() "
                                             +"method, at least one null "
                                             +"element in locators parameter");
            }//endif(locators == null)
            /* Remove the requested set of locators from the current set */
            removeLocatorsDo(regInfo, locators);
            addLogRecord(new LocsRemovedFromRegistrationLogObj
                                           (regInfo.registrationID,locators));
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end removeLocators

    /**
     * This method is the "backend" server counterpart to the method of
     * the same name provided by the <code>LookupDiscoveryRegistration</code> 
     * proxy (an instance of <code>FiddlerRegistration</code>) that is
     * returned by this service when a client requests a registration.
     * <p>
     * This method informs the lookup discovery service of the existence of
     * an unavailable lookup service and requests that the lookup discovery
     * service discard the unavailable lookup service and make it eligible
     * to be re-discovered.
     * 
     * @param registrationID unique identifier assigned to the registration
     *                       making the current discard request
     * @param registrar      a reference to the lookup service that the lookup
     *                       discovery service is being asked to discard.
     * <p>
     *                       If this parameter equals none of the lookup
     *                       services contained in the managed set of lookup
     *                       services for this registration, no action will
     *                       be taken.
     * 
     * @throws java.lang.NullPointerException this exception occurs when
     *         null is input to the registrar parameter.
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, 
     *         the lookup service may or may not have been successfully
     *         discarded.
     * 
     * @throws java.rmi.NoSuchObjectException wrapped in an instance of
     *         com.sun.jini.proxy.ThrowThis exception whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration. Refer to the description of the
     *         <code>getRegistrars</code> method for more information on
     *         this exception.
     *
     * @see com.sun.jini.fiddler.FiddlerRegistration#discard
     * @see net.jini.discovery.LookupDiscoveryRegistration#discard
     */
    public void discard(Uuid registrationID, ServiceRegistrar registrar)
                       throws NoSuchObjectException, RemoteException, ThrowThis
    {
	readyState.check();
        concurrentObj.writeLock();
        try {
            logInfoDiscard("\ndiscard: ",registrationID);
            RegistrationInfo regInfo
                   = (RegistrationInfo)(registrationByID.get(registrationID));
            if(regInfo == null) {
                throw new ThrowThis
                            (new NoSuchObjectException("Invalid registration "
                                                       +"ID on call to "
                                                       +"discard() method"));
            }//endif
            if(registrar == null) {
                throw new NullPointerException(" on call to discard() "
                                               +"method, null input for "
                                               +"registrar to discard");
            }//endif
            if( regIsElementOfRegSet(registrar,discoveryMgr.getRegistrars()) ){
                /* This must be the first discard request for this registrar 
                 * because the discovery manager has not discarded it yet.
                 * When the discovery manager discards the registrar, a
                 * local discarded event is sent to the listener.discarded
                 * method which queues a DiscardedEventTask which will 
                 * remove the discarded registrar from the registration's
                 * set of discovered registrars and then send a remote
                 * discarded event to the registration's listener.
                 */
                logInfoDiscard("  Registrar IS an element of Mgr's "
                               +"discovered registrars ... discarding "
                               +"from discovery manager");
                regInfo.discardFlag = true; //discard due to external request
                discoveryMgr.discard(registrar);
            } else {
                logInfoDiscard("  Registrar NOT an element of Mgr's "
                               +"discovered registrars ... queuing "
                               +"new DiscardRegistrarTask");
                /* For all subsequent discard requests, remove the registrar
                 * from the registration's set of discovered registrars and
                 * send a remote discarded event, but don't ask the discovery
                 * manager to discard the registrar.
                 */
                taskMgr.add(new DiscardRegistrarTask(regInfo,registrar));
            }//endif
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end discard

    /**
     * This method is the "backend" server counterpart to the 
     * <code>renew</code> method specified by the <code>Lease</code> interface,
     * implemented in the <code>com.sun.jini.lease.AbstractLease</code> class,
     * and invoked by way of the <code>doRenew</code> method of the
     * <code>FiddlerLease</code> class; an instance of which is
     * returned by the <code>getLease</code> method of the
     * <code>LookupDiscoveryRegistration</code> proxy (an instance of
     * <code>FiddlerRegistration</code>) that is returned by this service
     * when a client requests a registration.
     * <p>
     * This method renews the lease corresponding to the given 
     * <code>registrationID</code> and <code>leaseID</code> parameters,
     * granting a new duration that is less than or equal to the requested
     * duration value contained in the <code>duration</code> parameter.
     *
     * @param registrationID unique identifier assigned to the registration
     *                       to which the lease being renewed corresponds
     * @param leaseID        identifier assigned by the lease grantor to the
     *                       lease being renewed
     * @param duration       the requested duration for the lease being renewed
     *
     * @return <code>long</code> value representing the actual duration that
     *         was granted for the renewed lease. Note that the actual
     *         duration granted and returned by this method may be less than
     *         the duration requested.
     *
     * @throws net.jini.core.lease.UnknownLeaseException this exception occurs
     *         when the lease being renewed does not exist, or is unknown
     *         to the lease grantor; typically because the lease has expired.
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     *         
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server. When this exception does occur, the lease may or may
     *         not have been renewed successfully.
     *
     * @see net.jini.core.lease.Lease#renew
     * @see com.sun.jini.lease.AbstractLease#renew
     * @see com.sun.jini.lease.AbstractLease#doRenew
     * @see com.sun.jini.fiddler.FiddlerLease#doRenew
     */
    public long renewLease(Uuid registrationID,
                           Uuid leaseID,
                           long duration)
           throws UnknownLeaseException, NoSuchObjectException, RemoteException
    {
	readyState.check();
        concurrentObj.priorityWriteLock();
        try {
            RegistrationInfo regInfo
                   = (RegistrationInfo)(registrationByID.get(registrationID));
            if(regInfo == null) {
                throw new UnknownLeaseException
                                 ("\n    Invalid registration ID on call to "
                                  +"cancelLease() method"
                                  +"\n    The lease may have expired or been "
                                  +"cancelled");
            }//endif
            /* Renew the lease */
            long newDuration = 0;
            newDuration = renewLeaseDo(regInfo, leaseID, duration);
            logInfoLease("Renewed lease: ",registrationID,leaseID);
            /* The call to addLogRecord is in renewLeaseDo */
            return newDuration;
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end renewLease

    /**
     * This methods renews all leases from a <code>LeaseMap</code>, 
     * where each element of the map is a lease on a registration with
     * ID corresponding to an element of the <code>registrationIDs</code> 
     * parameter.
     * <p>
     * This method is the "backend" server counterpart to the 
     * <code>renewAll</code> method specified by the
     * <code>LeaseMap</code> interface, implemented in the 
     * <code>com.sun.jini.lease.AbstractLeaseMap</code> class, and 
     * invoked by way of the <code>renewAll</code> method of the
     * <code>FiddlerLease</code> class; an instance of which is
     * returned by the <code>getLease</code> method of the
     * <code>LookupDiscoveryRegistration</code> proxy (an instance of
     * <code>FiddlerRegistration</code>) that is returned by this service
     * when a client requests a registration.
     *
     * @param registrationIDs array containing the unique identifiers assigned
     *                        to the each registration to which each lease 
     *                        to be renewed corresponds
     * @param leaseIDs        array containing the identifiers assigned by the
     *                        lease grantor to each lease being renewed
     * @param durations       array containing the requested durations for 
     *                        each lease being renewed
     * 
     * @return an instance of FiddlerRenewResults containing data corresponding
     *         to the results (granted durations or exceptions) of each
     *         renewal attempt
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server. When this exception does occur, this method may or
     *         may not have complete its processing successfully.
     *
     * @see net.jini.core.lease.LeaseMap#renewAll
     */
    public FiddlerRenewResults renewLeases(Uuid[] registrationIDs,
                                           Uuid[] leaseIDs,
                                           long[] durations)
                                 throws NoSuchObjectException, RemoteException
    {
	readyState.check();
        concurrentObj.priorityWriteLock();
        try {
            return renewLeasesDo(registrationIDs, leaseIDs, durations);
            /* The call to addLogRecord is in renewLeasesDo */
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end renewLeases

    /**
     * This method is the "backend" server counterpart to the 
     * <code>cancel</code> method specified by the <code>Lease</code>
     * interface and implemented in the <code>FiddlerLease</code> class; an
     * instance of which is returned by the <code>getLease</code> method
     * of the <code>LookupDiscoveryRegistration</code> proxy (an instance of
     * <code>FiddlerRegistration</code>) that is returned by this service
     * when a client requests a registration.
     * <p>
     * This method cancels the lease corresponding to the given 
     * <code>registrationID</code> and <code>leaseID</code> parameters.
     *
     * The cancellation of a lease typically involves the modification of the
     * managed sets in the discovery manager, which usually involves starting
     * the discovery protocol.  An IOException can occur when the discovery
     * protocol fails to start. When such an exception does occur, this 
     * method registers an ERROR status attribute (along with a Comment
     * attribute describing the nature of the problem) to all lookup services
     * with which this service is registered. 
     *
     * Administrative clients, as well as clients that use this service should
     * have registered for notification of the existence of this attribute.
     *
     * @param registrationID unique identifier assigned to the registration
     *                       to which the lease being cancelled corresponds
     * @param leaseID        identifier assigned by the lease grantor to the
     *                       lease that is to be cancelled
     *
     * @throws net.jini.core.lease.UnknownLeaseException this exception occurs
     *         when the lease being cancelled is unknown to the lease grantor.
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server. When this exception does occur, the lease may or may
     *         not have been cancelled successfully.
     *
     * @see net.jini.core.lease.Lease#cancel
     */
    public void cancelLease(Uuid registrationID,
                            Uuid leaseID)
           throws UnknownLeaseException, NoSuchObjectException, RemoteException
    {
	readyState.check();
        concurrentObj.writeLock();
        try {
            RegistrationInfo regInfo
                   = (RegistrationInfo)(registrationByID.get(registrationID));
            if(regInfo == null) {
                throw new UnknownLeaseException
                                 ("\n    Invalid registration ID on call to "
                                  +"cancelLease() method"
                                  +"\n    The lease may have expired or been "
                                  +"cancelled");
            }//endif
            /* Cancel the lease */
            try {
                cancelLeaseDo(regInfo, leaseID);
                logInfoLease("Cancelled lease: ",registrationID,leaseID);
	    } catch(IOException e) {
                String eStr = "Failure while cancelling the lease on "
                              +"registration with ID = "+registrationID;
                if( problemLogger.isLoggable(Level.INFO) ) {
                    problemLogger.log(Level.INFO, eStr, e);
                }//endif
                Entry[] errorAttrs = 
                           new Entry[] { new FiddlerStatus(StatusType.ERROR),
                                         new Comment(eStr)
                                       };
                joinMgr.addAttributes(errorAttrs,true);
	    }
            addLogRecord(new LeaseCancelledLogObj
                                          (regInfo.registrationID, leaseID));
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end cancelLease

    /**
     * Cancels all leases from a <code>LeaseMap</code>.
     * <p>
     * For each element in the <code>registrationIDs</code> parameter,
     * this method will cancel the corresponding element in the
     * <code>leaseIDs</code> parameter.
     *
     * @param registrationIDs array containing the unique identifiers assigned
     *                        to the each registration to which each lease 
     *                        to be cancelled corresponds
     * @param leaseIDs        array containing the identifiers assigned by the
     *                        lease grantor to each lease being cancelled
     * 
     * @throws java.rmi.NoSuchObjectException if this method is called during
     *         service initialization or shutdown processing
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server. When this exception does occur, this method may or
     *         may not have complete its processing successfully.
     * 
     * @return array consisting of any exceptions that may have occurred 
     *         while attempting to cancel one of the leases in the map.
     *
     * @see net.jini.core.lease.LeaseMap#cancelAll
     */
    public Exception[] cancelLeases(Uuid[] registrationIDs,
                                    Uuid[] leaseIDs)
                                  throws NoSuchObjectException, RemoteException
    {
	readyState.check();
        concurrentObj.writeLock();
        try {
            /* don't bother to weed out unknown leases, so log first */
            addLogRecord( new LeasesCancelledLogObj(registrationIDs,leaseIDs));
            return cancelLeasesDo(registrationIDs, leaseIDs);
        } finally {
            concurrentObj.writeUnlock();
        }
    }//end cancelLeases
    /* END com.sun.jini.fiddler.Fiddler ----------------------------------- */
    /* ************************* END Public Methods *********************** */

    /* **************** BEGIN Private Static Utility Methods ************** */
    /** Return a new array containing the elements of the input array parameter
     *  with the input element parameter appended to the end of the array.
     */
    private static Object[] appendArray(Object[] array, Object elt) {
	int len = array.length;
	Object[] newArray =
	    (Object[])Array.newInstance(array.getClass().getComponentType(),
					len + 1);
	System.arraycopy(array, 0, newArray, 0, len);
	newArray[len] = elt;
	return newArray;
    }//end appendArray

    /** Bounds the duration by the value of the <code>bound</code> parameter,
     *  and checks for negative value.
     */
    private static long applyBoundToLeaseDuration(long leaseDuration,
                                                  long bound)
    {
        long newLeaseDuration = leaseDuration;
        if ( (leaseDuration == Lease.ANY) || (leaseDuration > bound) ) {
            newLeaseDuration = bound;
        } else if (leaseDuration < 0) {
            throw new IllegalArgumentException("negative lease duration");
        }//endif
        return newLeaseDuration;
    }//end applyBoundToLeaseDuration

    /** Determines if any element in the input array is null.
     *  @param arr Object array to examine for null elements
     *  @return true if any element is found to be null, false otherwise.
     *          Note that this means that if the array itself is null
     *          or has a non-positive length, false is returned (because
     *          the input parameter still does not contain a null element).
     */
    private static boolean containsNullElement(Object[] arr) {
        if( (arr == null) || (arr.length == 0) ) return false;
        for(int i=0;i<arr.length;i++) {
            if(arr[i] == null) return true;
        }//end loop
        return false;
    }//end containsNullElement

    /** This method determines if a particular registration (regInfo) is
     *  interested in discovering, through group discovery, the registrar
     *  belonging to a given set of member groups.
     *
     *  @param regGroups     array of the member groups from the registrar
     *                       (cannot be null)
     *  @param desiredGroups groups the registration wishes to discover
     *                       (can be null = ALL_GROUPS)
     * 
     *  @return <code>true</code> if at least one of the registrar's member
     *          groups is contained in the registration's set of groups to
     *          discover; <code>false</code> otherwise
     */
    private static boolean interested(String[] regGroups, Set desiredGroups) {
        if(desiredGroups == null) return true;
        if(desiredGroups.size() == 0) return false;
	for(int i=0;i<regGroups.length;i++) {
            if( desiredGroups.contains(regGroups[i]) ) return true;
        }//end loop
	return false;
    }//end interested

    /** This method determines if a particular registration (regInfo) is
     *  interested in discovering, through either locator discovery
     *  or group discovery, the registrar having a given locator and 
     *  belonging to a given set of member groups.
     *
     * @param regLoc          locator of the registrar (cannot be null)
     * @param regGroups       array of the member groups from the registrar
     *                        (cannot be null)
     * @param desiredLocators locators the registration wishes to discover
     * @param desiredGroups   groups the registration wishes to discover
     *                        (can be null = ALL_GROUPS)
     * 
     *  @return <code>true</code> if either the registrar's locator is
     *          contained in the registration's set of locators to discover,
     *          or at least one of the registrar's member groups is contained
     *          in the registration's set of groups to discover;
     *          <code>false</code> otherwise
     */
    private static boolean interested(LookupLocator regLoc,
                                      String[] regGroups,
                                      Set desiredLocators,
                                      Set desiredGroups)
    {
        if(locSetContainsLoc(desiredLocators,regLoc)) return true;
        return interested(regGroups,desiredGroups);
    }//end interested

    /** This method returns a mapping in which the key values are registrars,
     *  and the map values are the member groups of the corresponding
     *  registrar key. The registrar and member groups from the input map
     *  are selected to be included in the returned mapping if and only if
     *  the key value under consideration is a registrar that belongs to none
     *  of the desired groups of the given registration (<code>regInfo</code>).
     *  That is, the registrars referenced in the returned mapping are the
     *  registrars that are no longer of interest - through group discovery
     *  - to the given registration.
     *
     * @param regMap map whose key values are registrars, and whose map
     *                values are data structures of type
     *                <code>LocatorGroupsStruct</code> that contain the
     *                associated locator and member groups of the
     *                corresponding registrar key; the elements of the
     *                return map are selected from this mapping
     * @param regInfo the data structure record corresponding to the 
     *                registration whose groups-to-discover will be used
     *                to select the elements from <code>regMap</code> to
     *                include in the return mapping
     * 
     *  @return a registrar-to-groups map in which each registrar in the map
     *          is from the <code>regMap</code> parameter, and belongs to none
     *          of the desired groups referenced in the <code>regInfo</code>
     *          parameter
     */
    private static HashMap getUndesiredRegsByGroup(Map regMap, 
                                                   RegistrationInfo regInfo)
    {
        HashSet desiredGroups = regInfo.groups;
        HashMap undesiredRegMap = new HashMap(regMap.size());
        Set eSet = regMap.entrySet();
        for(Iterator itr = eSet.iterator(); itr.hasNext(); ) {
            Map.Entry pair = (Map.Entry)itr.next();
            String[] regGroups = ((LocatorGroupsStruct)pair.getValue()).groups;
            if( !interested(regGroups,desiredGroups) ) {
                undesiredRegMap.put((ServiceRegistrar)pair.getKey(),regGroups);
            }//endif
        }//end loop
        return undesiredRegMap;
    }//end getUndesiredRegsByGroup

    /** This method returns a subset of the given registrar-to-locators
     *  mapping (<code>regMap</code>). An element of the given mapping is
     *  selected to be included in the returned mapping if and only if
     *  the key value of the element is a registrar whose locator equals
     *  none of the desired locators of the given registration
     *  (<code>regInfo</code>). That is, the registrars referenced in
     *  the returned mapping are the registrars that are no longer of
     *  interest - through locator discovery - to the given registration.
     *
     *  This method returns a mapping in which the key values are registrars,
     *  and the map values are the locators of the corresponding registrar
     *  key. The registrar and locators from the input map are selected to
     *  be included in the returned mapping if and only if the key value
     *  under consideration is a registrar whose locator equals none of the
     *  desired locators of the given registration (<code>regInfo</code>).
     *  That is, the registrars referenced in the returned mapping are the
     *  registrars that are no longer of interest - through locator discovery
     *  - to the given registration.
     *
     * @param regMap map whose key values are registrars, and whose map
     *                values are data structures of type
     *                <code>LocatorGroupsStruct</code> that contain the
     *                associated locator and member groups of the
     *                corresponding registrar key; the elements of the
     *                return map are selected from this mapping
     * @param regInfo the data structure record corresponding to the 
     *                registration whose locators-to-discover will be used
     *                to select the elements from <code>regMap</code> to
     *                include in the return mapping
     * 
     *  @return a registrars-to-locators map in which each registrar key in
     *          the map is from the <code>regMap</code> parameter, and has
     *          a locator equal to none of the desired locators referenced
     *          in the <code>regInfo</code> parameter
     */
    private static Map getUndesiredRegsByLocator(Map regMap, 
                                                 RegistrationInfo regInfo)
    {
        HashSet desiredLocators = regInfo.locators;
        HashMap undesiredRegMap = new HashMap(regMap.size());
        Set eSet = regMap.entrySet();
        for(Iterator itr = eSet.iterator(); itr.hasNext(); ) {
            Map.Entry pair = (Map.Entry)itr.next();
            LookupLocator regLocator
                             = ((LocatorGroupsStruct)pair.getValue()).locator;
            if(!locSetContainsLoc(desiredLocators,regLocator)) {
                undesiredRegMap.put((ServiceRegistrar)pair.getKey(),
                                    regLocator);
            }//endif
        }//end loop
        return undesiredRegMap;
    }//end getUndesiredRegsByLocator

    /**
     * Marshals each element of the <code>Entry[]</code> array parameter.
     * This method is <code>static</code> so that it may called from
     * the <code>static</code> <code>LogRecord</code> classes when a set
     * of attributes is being logged to persistent storage.
     *
     * @param fiddlerImpl reference to the current instance of this service
     * @param attrs       <code>Entry[]</code> array consisting of the
     *                    attributes to marshal
     * @return array of <code>MarshalledObject[]</code>, where each element
     *         corresponds to an attribute in marshalled form 
     */
    private static MarshalledObject[] marshalAttributes
                                                   (FiddlerImpl fiddlerImpl,
                                                    Entry[] attrs)
    {
        if(attrs == null) return new MarshalledObject[0];
        ArrayList marshalledAttrs = new ArrayList();
        for(int i=0;i<attrs.length;i++) {
            /* Do not let an attribute problem prevent the service from
             * continuing to operate
             */
            try {
                marshalledAttrs.add(new MarshalledObject(attrs[i]));
            } catch(Throwable e) {
                if( problemLogger.isLoggable(Level.INFO) ) {
                    problemLogger.log(Level.INFO,
                                      "Error while marshalling attribute["+i
                                      +"] ("+attrs[i]+")", e);
                }//endif
            }
        }//end loop
        return ((MarshalledObject[])(marshalledAttrs.toArray
                             (new MarshalledObject[marshalledAttrs.size()])));
    }//end marshalAttributes

    /**
     * Unmarshals each element of the <code>MarshalledObject[]</code> array
     * parameter. This method is <code>static</code> so that it may called
     * from the <code>static</code> <code>LogRecord</code> classes when a
     * set of attributes is being recovered from persistent storage.
     *
     * @param fiddlerImpl     reference to the current instance of this service
     * @param marshalledAttrs <code>MarshalledObject[]</code> array consisting
     *                        of the attributes to unmarshal
     * @return array of <code>Entry[]</code>, where each element corresponds
     *         to an attribute that was successfully unmarshalled
     */
    private static Entry[] unmarshalAttributes
                                          (FiddlerImpl fiddlerImpl,
                                           MarshalledObject[] marshalledAttrs)
    {
        if(marshalledAttrs == null) return new Entry[0];
        ArrayList attrs = new ArrayList();
        for(int i=0;i<marshalledAttrs.length;i++) {
            /* Do not let an attribute problem prevent the service from
             * continuing to operate
             */
            try {
                attrs.add( (Entry)( marshalledAttrs[i].get() ) );
            } catch(Throwable e) {
                if( problemLogger.isLoggable(Level.INFO) ) {
                    problemLogger.log(Level.INFO,
                                      "Error while unmarshalling attribute["+i
                                      +"]", e);
                }//endif
            }
        }//end loop
        return ((Entry[])(attrs.toArray(new Entry[attrs.size()])));
    }//end unmarshalAttributes

    /** Using the given <code>ProxyPreparer</code>, attempts to prepare each
     *  element of the given <code>LookupLocator</code> array; replacing the
     *  original element of the array with the result of the call to the
     *  method <code>ProxyPreparer.prepareProxy</code>. If any attempt to
     *  prepare an element of the given array fails due to an exception,
     *  this method will propagate that exception.
     *
     *  This method is a convenience method that is typically used to
     *  prepare new locators the service should discover and join that
     *  are inserted into, or removed from, the service's state through
     *  the use of one of the following methods:
     *  <ul><li> <code>addLookupLocators</code>
     *      <li> <code>setLookupLocators</code>
     *      <li> <code>removeLookupLocators</code>
     *      <li> <code>addLocators</code>
     *      <li> <code>setLocators</code>
     *      <li> <code>removeLocators</code>
     *  </ul>
     * 
     * @param preparer the preparer to use to prepare each element of the
     *                 input array
     * @param locators array of <code>LookupLocator</code> instances in which
     *                 each element will be prepared.
     * 
     * @throws RemoteException   when preparation of any of the elements
     *                           of the input array fails because of a
     *                           <code>RemoteException</code>
     * @throws SecurityException when preparation of any of the elements
     *                           of the input array fails because of a
     *                           <code>SecurityException</code>
     */
    private static void prepareNewLocators(ProxyPreparer preparer,
                                           LookupLocator[] locators) 
                                                       throws RemoteException 
    {
        for (int i=0; i<locators.length; i++) {
            locators[i] = (LookupLocator)preparer.prepareProxy(locators[i]);
        }//end loop
    }//end prepareNewLocators

    /** Using the given <code>ProxyPreparer</code>, attempts to prepare each
     *  element of the given <code>LookupLocator</code> array; and returns
     *  a new array containing the prepared locators. If any attempt to
     *  prepare an element of the given array fails due to an exception,
     *  this method will skip to the next locator in that input array.
     *
     *  This method is a convenience method that is typically used to
     *  re-prepare the previously prepared locators that are retrieved
     *  from the service's persisted state during recovery.
     * 
     * @param preparer the preparer to use to prepare each element of the
     *                 input array
     * @param locators array of <code>LookupLocator</code> instances in which
     *                 each element will be prepared.
     * 
     * @return array of <code>LookupLocator</code> instances in which each
     *         element of the returned array is the result of successful proxy
     *         preparation of the corresponding element of the input array
     */
    private static LookupLocator[] prepareOldLocators(ProxyPreparer preparer,
                                                      LookupLocator[] locators)
    {
        ArrayList locsList = new ArrayList(locators.length);
        for(int i=0; i<locators.length; i++) {
            try {
                locsList.add(preparer.prepareProxy(locators[i]) );
            } catch(Throwable e) {
                if( problemLogger.isLoggable(Level.INFO) ) {
                    problemLogger.log(Level.INFO,"failure preparing recovered "
                                                 +"lookup locator["+i+"]", e);
                }//endif
            }
        }//end loop
        if(locators.length != locsList.size()) {
            if( problemLogger.isLoggable(Levels.HANDLED) ) {
                problemLogger.log(Levels.HANDLED,
                                  "number of requested recovered "
                                  +"lookup locators = "+locators.length);
                problemLogger.log(Levels.HANDLED, "number of successfully "
                                  +"prepared recovered lookup locators = "
                                  +locsList.size());
                for(int i=0; i<locsList.size(); i++) {
                    problemLogger.log(Levels.HANDLED, "successfully prepared "
                                      +"recovered lookup locator = "
                                      +locsList.get(i));
                }//end loop
            }//endif
        }//endif
        return ( (LookupLocator[])locsList.toArray
                                       (new LookupLocator[locsList.size()]) );
    }//end prepareOldLocators

    /** Using the given <code>ProxyPreparer</code>, attempts to prepare each
     *  element of the given <code>Set</code> of <code>LookupLocator</code>
     *  instances; and returns a new <code>Set</code> containing the prepared
     *  locators. If any attempt to prepare an element of the given 
     *  <code>Set</code> fails due to an exception, this method will skip
     *  to the next locator in that input <code>Set</code>.
     *
     *  This method is a convenience method that is typically used to
     *  re-prepare the previously prepared locators that are retrieved
     *  from the service's persisted state during recovery.
     * 
     * @param preparer the preparer to use to prepare each element of the
     *                 input array
     * @param locators <code>Set</code> of <code>LookupLocator</code>
     *                 instances in which each element will be prepared.
     * 
     * @return <code>Set</code> of <code>LookupLocator</code> instances in
     *         which each element of the returned <code>Set</code> is the
     *         result of successful proxy preparation of the corresponding
     *         element of the input <code>Set</code>
     */
    private static Set prepareOldLocators(ProxyPreparer preparer,
                                          Set locators)
    {
        Set locSet = new HashSet(locators.size());
        LookupLocator[] locsArray =
             prepareOldLocators( preparer,
                                (LookupLocator[])locators.toArray
                                        (new LookupLocator[locators.size()]) );
        for(int i=0; i<locsArray.length; i++) {
            locSet.add(locsArray[i]);
        }//end loop
        return locSet;
    }//end prepareOldLocators

    /** Searches the given set of locators for the given individual locator,
     *  returning <code>true</code> if the indicated locator is found in the
     *  set; <code>false</code> otherwise.
     *
     *  This method is a convenience method that is called instead of calling
     *  only the <code>contains</code> method on the <code>Set</code>
     *  parameter. This is necessary because the <code>equals</code> method
     *  on <code>LookupLocator</code> performs a simple <code>String</code>
     *  compare of the host names referenced by the locators being compared.
     *  Such a comparison can result in a "false negative" when the hostname
     *  returned by a remote system provides a fully-qualified hostname
     *  (ex. "myhost.subdomain.mycompany.com"), but clients of this service
     *  indicate interest in a locator using only the unqualified hostname
     *  (ex. "myhost"). In this case, both host names are legal and 
     *  functionally equivalent, but the <code>equals</code> method on 
     *  <code>LookupLocator</code> will interpret them as unequal; resulting
     *  in failure to discover locators that actually should be discovered.
     *
     *  To address the problem described above, this method will do the 
     *  following when attempting to determine whether the given locator
     *  is contained in the given set of locators:
     *
     *    1. Apply <code>Set</code>.<code>contains</code> which uses
     *       <code>LookupLocator</code>.<code>equals</code> to determine
     *       if the given locator is an element of the given set of locators.
     *    2. If the <code>Set</code>.<code>contains</code> method returns
     *       <code>false</code>, then iterate through the elements of the
     *       given set, retrieving and comparing the port and
     *       <code>InetAddress</code> of each element to the port and
     *       <code>InetAddress</code> of the given locator.
     * 
     * @param locSet this method will determine whether or not the given
     *               locator is contained in this <code>Set</code> of
     *               <code>LookupLocator</code>s.
     * @param loc    this method will determine whether or not this
     *               <code>LookupLocator</code> is contained in the given set.
     * 
     * @return <code>true</code> if the given set of locators contains the
     *         given locator; <code>false</code> otherwise.
     */
    private static boolean locSetContainsLoc(Set locSet, LookupLocator loc) {
        if( locSet.contains(loc) ) return true;//try LookupLocator.equals first
        /* Set containment test failed. Iterate through the set. */
        int port0 = loc.getPort();
        InetAddress addr0 = null;
        for(Iterator itr = locSet.iterator(); itr.hasNext(); ) {
            LookupLocator nextLoc = (LookupLocator)itr.next();
            if(nextLoc.getPort() != port0) continue;//try next port in set
            if(addr0 == null) {//only need to retrieve addr0 once
                try {
                    addr0 = InetAddress.getByName(loc.getHost());
                } catch(Exception e) {
                    problemLogger.log(Levels.HANDLED,
                                      "problem retrieving address by name", e);
                    return false;
                }
            }//endif
            InetAddress addr1 = null;
            try {
                addr1 = InetAddress.getByName(nextLoc.getHost());
            } catch(Exception e) {
                problemLogger.log(Level.FINEST,
                                  "problem retrieving address by name", e);
                continue;//try next address in set
            }
            if( addr1.equals(addr0) ) return true;
        }//end loop
        return false;
    }//end locSetContainsLoc

    /* **************** END Private Static Utility Methods ***************** */

    /* ************** BEGIN Private NON-Static Utility Methods ************* */
    /* BEGIN Private Startup Methods --------------------------------------- */
    /** Common entry point for initialization of the service in any of its
     *  possible modes: transient, non-activatable-persistent, or 
     *  activatable-persistent; with or without performing a JAAS login.
     */
    private void init(String[] configArgs, boolean persistent)
                                           throws IOException,
                                                  ConfigurationException,
                                                  LoginException
    {
        config = ConfigurationProvider.getInstance
                                       ( configArgs,
                                         (this.getClass()).getClassLoader() );

        loginContext = (LoginContext)config.getEntry(COMPONENT_NAME,
                                                     "loginContext",
                                                     LoginContext.class,
                                                     null);
        if(loginContext != null) {
            initWithLogin(config, persistent, loginContext);
        } else {
            doInit(config, persistent);
        }//endif
    }//end init

    /** Initialization with JAAS login as the <code>Subject</code> referenced
     *  in the given <code>loginContext</code>.
     */
    private void initWithLogin(final Configuration config, 
                               final boolean persistent,
                                     LoginContext loginContext)
                                                 throws IOException,
                                                        ConfigurationException,
                                                        LoginException
    {
        loginContext.login();
        try {
            Subject.doAsPrivileged( loginContext.getSubject(),
                                    new PrivilegedExceptionAction() {
                                        public Object run() throws Exception {
                                            doInit(config, persistent);
                                            return null;
                                        }//end run
                                    },
                                    null );//end doAsPrivileged
        } catch (Throwable e) {
            if(e instanceof PrivilegedExceptionAction)  e = e.getCause();
            if(e instanceof IOException)  throw (IOException)e;
            if(e instanceof ConfigurationException) 
                                          throw (ConfigurationException)e;
            throw new RuntimeException(e);
        }
    }//end initWithLogin

    /** Initialization common to all modes in which instances of this service
     *  runs: activatable/persistent, non-activatable/persistent, and
     *  transient (non-activatable /non-persistent).
     */
    private void doInit(final Configuration config, final boolean persistent)
                                                 throws IOException,
                                                        ConfigurationException
    {
        /* Note that two discovery managers are retrieved/created in this
         * method. One manager is used internally by this service to provide
         * the lookup discovery capabilities offered to the clients that
         * register to use this service. That manager (discoveryMgr) is
         * considered part of this service's implementation, and thus is
         * created rather than retrieved from the configuration. When
         * created here, this manager is configured to initially discover
         * NO_GROUPS and NO LOCATORS. The sets of groups and locators to
         * discover on behalf of the registered clients will be populated
         * during recovery; and then the discovery mechanism provided by
         * this manager will be started later, after recovery and
         * configuration retrieval has occurred, and after this service
         * has been exported. Thus, it is important that this manager be
         * created prior to recovery; as is done below.
         * 
         * The second discovery manager (joinMgrLDM) is passed to the join
         * manager that is employed by this service to advertise itself to
         * clients through lookup services. This discovery manager is
         * retrieved from the configuration, and must satisfy the following
         * requirements: must be an instance of both DiscoveryGroupManagement
         * and DiscoveryLocatorManagement, and should be initially configured
         * to discover NO_GROUPS and NO LOCATORS. This discovery manager is
         * retrieved from the configuration after recovery of any persistent
         * state, and after retrieval of any initial configuration items.
         */
        discoveryMgr = new LookupDiscoveryManager
                                          (DiscoveryGroupManagement.NO_GROUPS,
                                           new LookupLocator[0],
                                           null,
                                           config);

        /* Get the proxy preparers for the remote event listeners */
        listenerPreparer = (ProxyPreparer)Config.getNonNullEntry
                                                   (config,
                                                    COMPONENT_NAME,
                                                    "listenerPreparer",
                                                    ProxyPreparer.class,
                                                    new BasicProxyPreparer());
        /* Get the proxy preparers for the lookup locators to join */
        locatorToJoinPreparer = (ProxyPreparer)Config.getNonNullEntry
                                                   (config,
                                                    COMPONENT_NAME,
                                                    "locatorToJoinPreparer",
                                                    ProxyPreparer.class,
                                                    new BasicProxyPreparer());
        /* Get the proxy preparers for the lookup locators to discover */
        locatorToDiscoverPreparer = (ProxyPreparer)Config.getNonNullEntry
                                                 (config,
                                                  COMPONENT_NAME,
                                                  "locatorToDiscoverPreparer",
                                                  ProxyPreparer.class,
                                                  new BasicProxyPreparer());
        if(persistent) {
            /* Retrieve the proxy preparers that will only be applied during
             * the state recovery process below, when any previously stored
             * listeners or locators are recovered from persistent storage.
             */
            recoveredListenerPreparer = 
             (ProxyPreparer)Config.getNonNullEntry(config,
                                                   COMPONENT_NAME,
                                                   "recoveredListenerPreparer",
                                                   ProxyPreparer.class,
                                                   new BasicProxyPreparer());
            recoveredLocatorToJoinPreparer =
             (ProxyPreparer)Config.getNonNullEntry
                                             (config,
                                              COMPONENT_NAME,
                                              "recoveredLocatorToJoinPreparer",
                                              ProxyPreparer.class,
                                              new BasicProxyPreparer());
            recoveredLocatorToDiscoverPreparer = 
             (ProxyPreparer)Config.getNonNullEntry
                                         (config,
                                          COMPONENT_NAME,
                                          "recoveredLocatorToDiscoverPreparer",
                                          ProxyPreparer.class,
                                          new BasicProxyPreparer());


            /* Get the log directory for persisting this service's state */
            persistDir = (String)Config.getNonNullEntry(config,
                                                        COMPONENT_NAME,
                                                        "persistenceDirectory",
                                                        String.class);
            /* Recover the state that was persisted on prior runs (if any) */
            log = new ReliableLog(persistDir, new LocalLogHandler());
            inRecovery = true;
            log.recover();
            inRecovery = false;
        }//endif(persistent)

        /* For the two persistent versions of this service (activatable and
         * non-activatable), state recovery is complete. For the non-persistent
         * version of this service, no state recovery occurred (because it
         * wasn't necessary).
         *
         * For the two persistent versions, there is a circumstance in which
         * 'one time', initial items must be retrieved from the configuration:
         * when the service is started for the very first time. For the
         * non-persistent version, those items will be retrieved every time
         * the service is started.
         *
         * The flag 'initialStartup' is used below to determine whether
         * or not to retrieve the initial configuration items. This is the
         * only purpose for that flag. 
         *
         * For either persistent version of the service, the flag's value
         * will be changed to false during the startup process only when
         * there already exists a 'snapshot' of the service's state from
         * a previous run. This is because the flag's value is only
         * changed during the recovery of the snapshot (see the method
         * recoverSnapshot()). Note that the only time such a snapshot
         * should NOT already exist at startup, is when the service is
         * being started for the very first time. Thus, when either
         * persistent version of the service is started for the first
         * time, the service's configuration is consulted for the initial
         * values of the items below; otherwise, when the service is being
         * re-started (after a crash for example), the values used for
         * those items will be the values retrieved above during recovery
         * of the service's persistent state.
         * 
         * With respect to the non-persistent version of the service, the
         * values of the items below will always be retrieved at startup.
         * This is because the non-persistent version of the service never
         * attempts to recover previously stored state; thus, the flag's
         * value will never change. Note that this will be true even if a
         * snapshot exists from a previous run of one of the persistent
         * versions of the service. 
         *
         * The service's Uuid is also handled here.
         */
        if(initialStartup) {
            if(log != null) {
                snapshotWt = ((Float)config.getEntry
                                         (COMPONENT_NAME,
                                          "initialPersistenceSnapshotWeight",
                                          float.class,
                                          new Float(snapshotWt))).floatValue();
                snapshotThresh =
                       Config.getIntEntry
                                  (config,
                                   COMPONENT_NAME,
                                   "initialPersistenceSnapshotThreshold",
                                   snapshotThresh, 0, Integer.MAX_VALUE);
            }//endif(log != null)
            leaseBound = Config.getLongEntry(config,
                                             COMPONENT_NAME,
                                             "initialLeaseBound",
                                             leaseBound, 0, Long.MAX_VALUE);
            /* Get any additional attributes with which to associate this
             * service when registering it with any lookup services.
             */
            Entry[] initAttrs = (Entry[])config.getEntry
                                                   (COMPONENT_NAME,
                                                    "initialLookupAttributes",
                                                    Entry[].class,
                                                    null );
            if(initAttrs != null) {
                ArrayList attrsList
                   = new ArrayList(thisServicesAttrs.length+initAttrs.length);
                for(int i=0;i<thisServicesAttrs.length;i++) {
                    attrsList.add(thisServicesAttrs[i]);
                }//end loop
                for(int i=0;i<initAttrs.length;i++) {
                    attrsList.add(initAttrs[i]);
                }//end loop
                thisServicesAttrs = (Entry[])attrsList.toArray
                                                (new Entry[attrsList.size()]);
            }//endif(initAttrs != null)

            /* Get the initial groups this service should join. */
            thisServicesGroups =
                 (String[])config.getEntry(COMPONENT_NAME, 
                                           "initialLookupGroups", 
                                           String[].class, 
                                           thisServicesGroups);
            /* Get the initial locators this service should join. */
            thisServicesLocators =
                     (LookupLocator[])config.getEntry(COMPONENT_NAME, 
                                                      "initialLookupLocators", 
                                                      LookupLocator[].class, 
                                                      new LookupLocator[0]);
            if(thisServicesLocators == null) {
                thisServicesLocators = new LookupLocator[0];
            }//endif

            /* Generate the private, universally unique (over space and time)
             * ID that will be used by the outer proxy to test for equality
             * with other proxies.
             */
            proxyID = UuidFactory.generate();
        }//endif(initialStartup)

        /* The proxyID should never be null at this point. It should have
         * been either recovered from the persisted state, or generated above.
         */
        if(proxyID == null) throw new NullPointerException("proxyID == null");

        /* Get the various configurable constants */
        leaseMax = Config.getLongEntry(config,
                                       COMPONENT_NAME,
                                       "leaseMax",
                                       leaseMax, 0, Long.MAX_VALUE);
        /* Take a snapshot of the current state to "clean up" the log file,
         * and to record the items set above.
         */
	if(log != null) log.snapshot();
        /* The service ID used to register this service with lookup services
         * is always derived from the proxyID that is associated with the
         * service for the lifetime of the service.
         */
        serviceID = new ServiceID(proxyID.getMostSignificantBits(),
                                  proxyID.getLeastSignificantBits());
        /* Get a general-purpose task manager for this service */
        taskMgr = (TaskManager)Config.getNonNullEntry
                                          (config,
                                           COMPONENT_NAME,
                                           "taskManager",
                                           TaskManager.class,
                                           new TaskManager(10,1000*15,1.0f) );
        /* Get the discovery manager to pass to this service's join manager. */
        try {
            joinMgrLDM  = 
                (DiscoveryManagement)Config.getNonNullEntry
                                                  (config,
                                                   COMPONENT_NAME,
                                                   "discoveryManager",
                                                   DiscoveryManagement.class);
            if( joinMgrLDM instanceof DiscoveryGroupManagement ) {
                String[] groups0 =
                           ((DiscoveryGroupManagement)joinMgrLDM).getGroups();
                if(    (groups0 == DiscoveryGroupManagement.ALL_GROUPS)
                    || (groups0.length != 0) )
                {
                    throw new ConfigurationException
                                 ("discoveryManager entry must be configured "
                                  +"to initially discover/join NO_GROUPS");
                }//endif
            } else {// !(joinMgrLDM instanceof DiscoveryGroupManagement)
                throw new ConfigurationException
                                       ("discoveryManager entry must "
                                        +"implement DiscoveryGroupManagement");
            }//endif
            if( joinMgrLDM instanceof DiscoveryLocatorManagement ) {
                LookupLocator[] locs0 =
                        ((DiscoveryLocatorManagement)joinMgrLDM).getLocators();
                if( (locs0 != null) && (locs0.length != 0) ) {
                    throw new ConfigurationException
                                 ("discoveryManager entry must be configured "
                                  +"to initially discover/join no locators");
                }//endif
            } else {// !(joinMgrLDM instanceof DiscoveryLocatorManagement)
                throw new ConfigurationException
                                     ("discoveryManager entry must "
                                      +"implement DiscoveryLocatorManagement");
            }//endif
        } catch (NoSuchEntryException e) {
            joinMgrLDM
               = new LookupDiscoveryManager(DiscoveryGroupManagement.NO_GROUPS,
                                            new LookupLocator[0], null,config);
        }

        /* Handle items and duties related to exporting this service. */
        ServerEndpoint endpoint = TcpServerEndpoint.getInstance(0);
        InvocationLayerFactory ilFactory = new BasicILFactory();
        Exporter defaultExporter = new BasicJeriExporter(endpoint,
                                                         ilFactory,
                                                         false,
                                                         true);
        /* For the activatable server */
        if(activationID != null) {
            ProxyPreparer aidPreparer =
              (ProxyPreparer)Config.getNonNullEntry(config,
                                                    COMPONENT_NAME,
                                                    "activationIdPreparer",
                                                    ProxyPreparer.class,
                                                    new BasicProxyPreparer());
            ProxyPreparer aSysPreparer = 
              (ProxyPreparer)Config.getNonNullEntry(config,
                                                    COMPONENT_NAME,
                                                    "activationSystemPreparer",
                                                    ProxyPreparer.class,
                                                    new BasicProxyPreparer());
            activationID = (ActivationID)aidPreparer.prepareProxy
                                                               (activationID);
            activationSystem = (ActivationSystem)aSysPreparer.prepareProxy
                                                            (activationSystem);
            defaultExporter = new ActivationExporter(activationID,
                                                     defaultExporter);
        }//endif(activationID != null)

        /* Get the exporter that will be used to export this service */
        try {
            serverExporter = (Exporter)Config.getNonNullEntry(config,
                                                              COMPONENT_NAME,
                                                              "serverExporter",
                                                              Exporter.class,
                                                              defaultExporter,
                                                              activationID);
        } catch(ConfigurationException e) {// exception, use default
            throw new ExportException("Configuration exception while "
                                      +"retrieving service's exporter",
                                      e);
        }
        /* Export this service */
        innerProxy = (Fiddler)serverExporter.export(this);

        /* Create the outer (smart) proxy that is registered with lookups */
        outerProxy = FiddlerProxy.createServiceProxy(innerProxy, proxyID);
        /* Create the proxy that can be used to administer this service */
        adminProxy = FiddlerAdminProxy.createAdminProxy(innerProxy, proxyID);

        /* Create the following threads here, after a possible JAAS login,
         * rather than in the constructor, before the login. This must
         * be done so that the threads will have the correct subject.
         */
        leaseExpireThread = new LeaseExpireThread();
        if(log != null) snapshotThread = new SnapshotThread();

        /* Start the discovery mechanism for all recovered registrations */
        discoveryMgr.addDiscoveryListener(discoveryListener);

        /* Advertise the services provided by this entity */
	joinMgr = new JoinManager(outerProxy, thisServicesAttrs,
                                  serviceID, joinMgrLDM, null,
                                  config);
        ((DiscoveryLocatorManagement)joinMgrLDM).setLocators
                                                        (thisServicesLocators);
        ((DiscoveryGroupManagement)joinMgrLDM).setGroups(thisServicesGroups);

	/* start up all the daemon threads */
	leaseExpireThread.start();
        if(log != null) snapshotThread.start();
        logInfoStartup();
	readyState.ready();
    }//end doInit
    /* END Private Startup Methods ----------------------------------------- */

    /* BEGIN Private Shutdown Methods -------------------------------------- */
    /* Called in the constructor when failure occurs during the initialization
     * process. Un-does any work that may have already been completed; for
     * example, un-exports the service if it has already been exported,
     * terminates any threads that may have been started, etc.
     */
    private void cleanupInitFailure() {
        if(innerProxy != null)  {
            try {
                serverExporter.unexport(true);
            } catch(Throwable t) { }
        }//endif

        if(taskMgr != null)  {
            try {
                taskMgr.terminate();
            } catch(Throwable t) { }
        }//endif

        if(joinMgr != null)  {
            try {
                joinMgr.terminate();
            } catch(Throwable t) { }
        }//endif

        if(joinMgrLDM != null)  {
            try {
                joinMgrLDM.terminate();
            } catch(Throwable t) { }
        }//endif

        if(discoveryMgr != null)  {
            try {
                discoveryMgr.terminate();
            } catch(Throwable t) { }
        }//endif

        if(leaseExpireThread != null)  {
            try {
                leaseExpireThread.interrupt();
                leaseExpireThread.join();
            } catch(Throwable t) { }
        }//endif

        if(snapshotThread != null)  {
            try {
                snapshotThread.interrupt();
                snapshotThread.join();
            } catch(Throwable t) { }
        }//endif
    }//end cleanupInitFailure

    /* Convenience method called in the constructor or the activatable version
     * of this service when failure occurs during the initialization process.
     * Logs and rethrows the given <code>Throwable</code> so the constructor
     * doesn't have to.
     */
    private void handleActivatableInitThrowable(Throwable t) 
                                            throws IOException,
                                                   ActivationException,
                                                   ConfigurationException,
                                                   LoginException,
                                                   ClassNotFoundException
    {
        handleInitThrowable(t);
        if (t instanceof ActivationException) {
            throw (ActivationException)t;
        } else if (t instanceof LoginException) {
            throw (ClassNotFoundException)t;
        } else {
            throw new AssertionError(t);
        }//endif
    }//end handleInitThrowable

    /* Convenience method called in the constructor or the non-activatable 
     * version of this service when failure occurs during the initialization
     * process. Logs and rethrows the given <code>Throwable</code> so the
     * constructor doesn't have to.
     */
    private void handleInitThrowable(Throwable t) 
                                            throws IOException,
                                                   ConfigurationException,
                                                   LoginException
    {
        problemLogger.log(Level.SEVERE, "cannot initialize the service", t);
        if (t instanceof IOException) {
            throw (IOException)t;
        } else if (t instanceof ConfigurationException) {
            throw (ConfigurationException)t;
        } else if (t instanceof LoginException) {
            throw (LoginException)t;
        } else if (t instanceof RuntimeException) {
            throw (RuntimeException)t;
        } else if (t instanceof Error) {
            throw (Error)t;
        }//endif
    }//end handleInitThrowable

    /**
     * Called by the public method <code>destroy</code> as well as by the
     * <code>apply</code> method in the various LogObj classes that,
     * during recovery, modify the managed sets of the discovery manager;
     * and, while doing so, experience an IOException when the multicast
     * request protocol fails to start during recovery (an un-recoverable
     * exception).
     *
     * This method destroys the lookup discovery service, if possible,
     * including its persistent storage. This method spawns a separate
     * thread to do the actual work asynchronously, so a successful
     * return from this method usually does not mean that the service
     * has been destroyed.
     *
     * @see com.sun.jini.fiddler.FiddlerImpl#destroy
     */
    private void destroyDo() {
        (new DestroyThread()).start();
    }//end destroyDo
    /* END Private Shutdown Methods ---------------------------------------- */

    /* BEGIN Private Registration Methods ---------------------------------- */
    /**
     * This method is called by the public method <code>register</code>.
     * This method creates a registration object that is an instance of
     * <code>FiddlerRegistration</code> which implements the interface
     * <code>LookupDiscoveryRegistration</code>, and acts as a proxy to the
     * registration-related methods of the backend server of the Fiddler
     * implementation of the lookup discovery service.
     * <p>
     * This method also associates with the registration all information
     * needed by the registration to participate in the lookup discovery
     * service, information such as: IDs, lease information, event information.
     * 
     * @param groups        names of groups whose members are the lookup
     *                      services to discover.
     * @param locators      instances of LookupLocator, each corresponding to
     *                      a specific lookup service to discover.
     * @param listener      the entity that will receive events notifying the
     *                      registration that a lookup service of interest has
     *                      been discovered.
     * @param handback      the object that will be included in every
     *                      notification event sent to the registered listener.
     * @param leaseDuration long value representing the amount of time (in
     *                      milliseconds) for which the resources of the
     *                      lookup discovery service are being requested.
     *                      
     * @return an instance of FiddlerRegistration which implements the
     *         LookupDiscoveryRegistration interface, and acts as a proxy
     *         to the registration-related methods of the backend server
     *         of the Fiddler implementation of the lookup discovery service
     *
     * @throws java.io.IOException this exception occurs when the multicast
     *         request protocol fails to start.
     *
     * @throws java.rmi.RemoteException this exception occurs when the
     *         attempt to prepare the listener fails due to a
     *         <code>RemoteException</code>
     * 
     * @throws java.lang.SecurityException this exception occurs when the
     *         attempt to prepare the listener fails due to a
     *         <code>SecurityException</code>
     *
     * @see com.sun.jini.fiddler.FiddlerImpl#register
     */
    private LookupDiscoveryRegistration registerDo
                                             (String[] groups,
                                              LookupLocator[] locators,
                                              RemoteEventListener listener,
                                              MarshalledObject handback,
                                              long leaseDuration)
                                                       throws RemoteException,
                                                              IOException
    {
        /* Input okay. Create the registration and associated information */
	long curTime    = System.currentTimeMillis();
        leaseDuration   = applyBoundToLeaseDuration(leaseDuration,
                                                    leaseBound);
        Uuid regID      = UuidFactory.generate();
        Uuid leaseID    = regID;//use same ID since Reg "wraps" the lease
        long expiration = curTime + leaseDuration;

        /* Prepare the new listener */
        listener = (RemoteEventListener)listenerPreparer.prepareProxy
                                                                   (listener);
        RegistrationInfo regInfo = new RegistrationInfo( regID,
                                                         groups,locators,
                                                         leaseID,expiration,
                                                         curEventID,handback,
                                                         listener );
        curEventID++;
	addRegistration(regInfo);
        logInfoRegistration("\nadded registration:  registrationID = ",regID);
        addLogRecord(new RegistrationGrantedLogObj(regInfo));
        /* Queue task for sending a discovered event */
        taskMgr.add(new NewRegistrationTask(regInfo));
	/* See if the expire thread needs to wake up earlier */
	if (expiration < minExpiration) {
	    minExpiration = expiration;
	    concurrentObj.waiterNotify(leaseExpireThreadSyncObj);
	}
        FiddlerLease regLease =
                   FiddlerLease.createLease
                           (innerProxy, proxyID, regID, leaseID, expiration);
        EventRegistration eventReg = new EventRegistration( regInfo.eventID,
                                                            outerProxy,
                                                            regLease,
                                                            regInfo.seqNum );
        logInfoGroups();
        logInfoLocators();
        FiddlerRegistration regObj = FiddlerRegistration.createRegistration
                                                (innerProxy, regID, eventReg);
        logInfoRegistration("\ncreated registration: registrationID = ",
                            regObj);
        return regObj;
    }//end registerDo

    /** 
     * Places the registration corresponding to the <code>regInfo</code> 
     * parameter in both the <code>registrationByID</code> map and the 
     * <code>registrationByTime</code> map. This method also updates the 
     * managed sets in the discovery manager in the appropriate way. This 
     * should be called whenever a new registration has been created 
     * and needs to be added to the data base (for example, the methods
     * <code>registerDo</code>, <code>recoverSnapshot</code> and the 
     * <code>apply</code> method of the <code>RegistrationGrantedLogObj</code>
     * class all call this method).
     * 
     * @param regInfo  the data structure record corresponding to the
     *                 registration that is to be added to the data base
     *
     * @throws java.io.IOException this exception occurs when the multicast
     *         request protocol fails to start.
     *
     * @see com.sun.jini.fiddler.FiddlerImpl#registerDo
     * @see com.sun.jini.fiddler.FiddlerImpl#recoverSnapshot
     */
    private void addRegistration(RegistrationInfo regInfo) throws IOException {
        if (regInfo.listener == null) {
	    /* failed to recover from log */
            if( problemLogger.isLoggable(Level.INFO) ) {
                problemLogger.log(Level.INFO, "cannot add registration (ID = "
                                  +regInfo.registrationID+"); failed to "
                                  +"unmarshal listener during recovery");
            }//endif
            return;
        }//endif
        /* First add the indicated registration */
        registrationByID.put(regInfo.registrationID, regInfo);
        registrationByTime.put(regInfo,regInfo);
        /* Update the set of groups managed by the discovery manager */
        updateDiscoveryMgrGroups();
        /* Update the set of locators managed by the discovery manager */
        updateDiscoveryMgrLocators();
    }//end addRegistration

    /** 
     * Removes the registration corresponding to the <code>regInfo</code> 
     * parameter from this service's state. Removes the registration from 
     * both the <code>registrationByID</code> map and the 
     * <code>registrationByTime</code> map. This method also updates the 
     * managed sets in the discovery manager in the appropriate way. This 
     * should be called whenever a current registration needs to be removed
     * from the data base (for example, when the registration's lease is
     * is expired in the <code>LeaseExpireThread</code>, and when the
     * registration's lease is cancelled in the <code>cancelLeaseDo</code>
     * method).
     * 
     * @param regInfo  the data structure record corresponding to the
     *                 registration that is to be removed from the data base
     *
     * @throws java.io.IOException this exception occurs when the multicast
     *         request protocol fails to start.
     *
     * @see com.sun.jini.fiddler.FiddlerImpl#cancelLeaseDo
     */
    private void removeRegistration(RegistrationInfo regInfo)
                                                         throws IOException
    {
        /* First remove the current registration */
        registrationByID.remove(regInfo.registrationID);
        registrationByTime.remove(regInfo);
        logInfoRegistration("\nremoved registration: registrationID = ",
                            regInfo.registrationID);
        /* Update the set of groups managed by the discovery manager */
        updateDiscoveryMgrGroups();
        /* Update the set of locators managed by the discovery manager */
        updateDiscoveryMgrLocators();
        logInfoGroups();
        logInfoLocators();
    }//end removeRegistration
    /* END Private Registration Methods ------------------------------------ */

    /* BEGIN Private Group Management Methods ------------------------------ */
    /**
     * Called by the public method <code>addGroups</code>. This method
     * queues an <code>AddGroupsTask</code> which performs the actual
     * augmentation of the given registration's desired groups.
     * 
     * @param regInfo the data structure record corresponding to the
     *                registration whose managed set of groups is to be
     *                augmented
     * @param groups  a String array, none of whose elements may be null,
     *                consisting of the group names with which to augment the
     *                registration's managed set of groups.
     *
     * @see com.sun.jini.fiddler.FiddlerImpl#addGroups
     * @see com.sun.jini.fiddler.FiddlerRegistration#addGroups
     * @see net.jini.discovery.LookupDiscoveryRegistration#addGroups
     */
    private void addGroupsDo(RegistrationInfo regInfo, String[] groups) {
        taskMgr.add(new AddGroupsTask(regInfo,groups));
    }//end addGroupsDo

    /**
     * Called by the <code>apply</code> method of the class
     * <code>GroupsAddedToRegistrationLogObj</code> (which is invoked 
     * during state recovery). This method queues an
     * <code>AddGroupsTask</code> which performs the actual augmentation
     * of the given registration's desired groups.
     * <p>
     * @param registrationID   the ID of the data structure record
     *                         corresponding to the registration whose
     *                         managed set of groups is to be augmented
     * @param registrationByID the map containing all active registrations
     *                         managed by this service
     * @param groups           a <code>String</code> array, none of whose 
     *                         elements may be null, consisting of the group
     *                         names with which to augment the registration's
     *                         managed set of groups.
     * 
     * @see com.sun.jini.fiddler.FiddlerImpl#addGroupsDo
     * @see com.sun.jini.fiddler.FiddlerImpl#addGroups
     * @see com.sun.jini.fiddler.FiddlerRegistration#addGroups
     * @see net.jini.discovery.LookupDiscoveryRegistration#addGroups
     */
    private void addGroupsDo(Uuid registrationID,
                             HashMap registrationByID,
                             String[] groups)
    {
        addGroupsDo((RegistrationInfo)(registrationByID.get(registrationID)),
                     groups);
    }//end addGroupsDo

    /**
     * Called by the public method <code>setGroups</code>. This method
     * queues a <code>SetGroupsTask</code> which performs the actual
     * replacement.
     * 
     * @param regInfo the data structure record corresponding to the
     *                registration whose managed set of groups is to be
     *                replaced
     * @param groups  a String array, none of whose elements may be null, 
     *                consisting of the group names with which to replace the
     *                registration's managed set of groups.
     * 
     * @see com.sun.jini.fiddler.FiddlerImpl#setGroups
     * @see com.sun.jini.fiddler.FiddlerRegistration#setGroups
     * @see net.jini.discovery.LookupDiscoveryRegistration#setGroups
     */
    private void setGroupsDo(RegistrationInfo regInfo, String[] groups) {
        taskMgr.add(new SetGroupsTask(regInfo,groups));
    }//end setGroupsDo

    /**
     * Called by the <code>apply</code> method of the class
     * <code>GroupsSetInRegistrationLogObj</code> (which is invoked 
     * during state recovery). This method queues a
     * <code>SetGroupsTask</code> which performs the actual replacement.
     * <p>
     * @param registrationID   the ID of the data structure record
     *                         corresponding to the registration whose
     *                         managed set of groups is to be replaced
     * @param registrationByID the map containing all active registrations
     *                         managed by this service
     * @param groups           a <code>String</code> array, none of whose 
     *                         elements may be null, consisting of the group
     *                         names with which to replace the registration's
     *                         managed set of groups.
     * 
     * @see com.sun.jini.fiddler.FiddlerImpl#setGroupsDo
     * @see com.sun.jini.fiddler.FiddlerImpl#setGroups
     * @see com.sun.jini.fiddler.FiddlerRegistration#setGroups
     * @see net.jini.discovery.LookupDiscoveryRegistration#setGroups
     */
    private void setGroupsDo(Uuid registrationID,
                             HashMap registrationByID,
                             String[] groups)
    {
        setGroupsDo((RegistrationInfo)(registrationByID.get(registrationID)),
                     groups);
    }//end setGroupsDo

    /**
     * Called by the public method <code>removeGroups</code>. This method
     * queues a <code>RemoveGroupsTask</code> which performs the actual
     * removal.
     * 
     * @param regInfo the data structure record corresponding to the
     *                registration from whose managed set of groups the input
     *                set of groups is to be removed
     * @param groups  a String array, none of whose elements may be null,
     *                consisting of the group names to remove from the 
     *                registration's managed set of groups
     * 
     * @see com.sun.jini.fiddler.FiddlerImpl#removeGroups
     * @see com.sun.jini.fiddler.FiddlerRegistration#removeGroups
     * @see net.jini.discovery.LookupDiscoveryRegistration#removeGroups
     */
    private void removeGroupsDo(RegistrationInfo regInfo, String[] groups) {
        taskMgr.add(new RemoveGroupsTask(regInfo,groups));
    }//end removeGroupsDo

    /**
     * Called by the <code>apply</code> method of the class
     * <code>GroupsRemovedFromRegistrationLogObj</code> (which is
     * invoked during state recovery). This method queues a
     * <code>RemoveGroupsTask</code> which performs the actual removal.
     * <p>
     * @param registrationID   the ID of the data structure record
     *                         corresponding to the registration from whose
     *                         managed set of groups the input set of groups
     *                         is to be removed
     * @param registrationByID the map containing all active registrations
     *                         managed by this service
     * @param groups           a <code>String</code> array, none of whose 
     *                         elements may be null, consisting of the group
     *                         names to remove from the registration's managed
     *                         set of groups
     * 
     * @see com.sun.jini.fiddler.FiddlerImpl#removeGroupsDo
     * @see com.sun.jini.fiddler.FiddlerImpl#removeGroups
     * @see com.sun.jini.fiddler.FiddlerRegistration#removeGroups
     * @see net.jini.discovery.LookupDiscoveryRegistration#removeGroups
     */
    private void removeGroupsDo(Uuid registrationID,
                                HashMap registrationByID,
                                String[] groups)
    {
        removeGroupsDo
                 ((RegistrationInfo)(registrationByID.get(registrationID)),
                   groups);
    }//end removeGroupsDo

    /** Builds a set containing the groups from all registrations. 
     *
     *  Iterates through all the active registrations, retrieving the groups
     *  to discover for each registration (minus duplicates). If at least one
     *  registration has requested that all groups be discovered, stops
     *  iterating (because the set must be ALL_GROUPS) and returns null.
     */
    private String[] getGroupsFromAllRegs() {
        HashSet  groupSet = new HashSet();
        for(Iterator itr=registrationByID.values().iterator();itr.hasNext();) {
            RegistrationInfo rInfo = (RegistrationInfo)itr.next();
            if(rInfo.groups == null) {
                return DiscoveryGroupManagement.ALL_GROUPS;
            } else {
                groupSet.addAll(rInfo.groups);
            }//end if
        }//end loop
        return (String[])groupSet.toArray(new String[groupSet.size()]);
    }//end getGroupsFromAllRegs

    /** This method returns a registrar-to-data-structure map in which each
     *  registrar key in the returned map is one of the keys from the
     *  the global map <code>allDiscoveredRegs</code>, and the corresponding 
     *  value is the (locator,groups) pair that corresponds to that registrar
     *  key in <code>allDiscoveredRegs</code>
     *  
     *  An element of <code>allDiscoveredRegs</code> is selected to have
     *  its registrar and associated (locator,groups) pair be included in the
     *  returned mapping if and only if the key value of the element is a
     *  registrar that belongs to at least one of the desired groups of the
     *  given registration (<code>regInfo</code> parameter). That is, the
     *  registrars referenced in the returned mapping are the registrars that
     *  are of interest - through group discovery - to the given registration.
     *
     *  Note that this method must be called from within a synchronization
     *  block.
     *
     * @param regInfo the data structure record corresponding to the
     *                registration whose groups-to-discover will be used to
     *                select the elements from <code>allDiscoveredRegs</code>
     *                to include in the return mapping
     * 
     *  @return a mapping in which the key values are registrars that,
     *          in addition to being elements of the global map
     *          <code>allDiscoveredRegs</code>, also belong to at least one
     *          of the desired groups referenced in the <code>regInfo</code>
     *          parameter; and whose map values are data structures of type
     *          <code>LocatorGroupsStruct</code> that contain the associated
     *          locator and member groups of the corresponding registrar key
     */
    private HashMap getDesiredRegsByGroup(RegistrationInfo regInfo) {
        HashSet desiredGroups = regInfo.groups;
        HashMap desiredRegMap = new HashMap(allDiscoveredRegs.size());
        Set eSet = allDiscoveredRegs.entrySet();
        for(Iterator itr = eSet.iterator(); itr.hasNext(); ) {
            Map.Entry pair = (Map.Entry)itr.next();
            LocatorGroupsStruct locGroupsStruct
                                       = (LocatorGroupsStruct)pair.getValue();
            if( interested(locGroupsStruct.groups,desiredGroups) ) {
                desiredRegMap.put((ServiceRegistrar)pair.getKey(),
                                  locGroupsStruct);
            }//endif
        }//end loop
        return desiredRegMap;
    }//end getDesiredRegsByGroup

    /** Modifies the discovery manager's managed set of groups in the
     *  appropriate way; that is, adds to the discovery manager's managed
     *  set of groups, any new groups-of-interest, across all registrations,
     *  and removes any groups that are no longer of interest to any of the
     *  registrations. This method is typically called after the groups of
     *  a particular registration have been modified.
     *
     *  Note that this method must be called from within a synchronization
     *  block.
     */
    private void updateDiscoveryMgrGroups() {
        /* Build the set containing the groups from all registrations. */
        String[] groupsAcrossAllRegs = getGroupsFromAllRegs();
        /* Update the discovery manager's set of groups to discover. Let
         * the manager sort out duplicates and any already-discovered groups.
         */
        try {
            discoveryMgr.setGroups( groupsAcrossAllRegs );
        } catch (IOException e) {
            String warnStr = "IOException: on call to setGroups() "
                             +"method of discovery manager";
            problemLogger.log(Level.INFO, warnStr, e);
            Entry[] warnAttrs = new Entry[]
                                      { new FiddlerStatus(StatusType.WARNING),
                                        new Comment(warnStr)
                                      };
            joinMgr.addAttributes(warnAttrs,true);
        } catch(IllegalStateException e) {
            String errStr = "IllegalStateException: discovery "
                            +"manager's setGroups() method was called "
                            +"after the manager was terminated";
            problemLogger.log(Level.INFO, errStr, e);
            Entry[] errorAttrs = new Entry[]
                                        { new FiddlerStatus(StatusType.ERROR),
                                          new Comment(errStr)
                                        };
            joinMgr.addAttributes(errorAttrs,true);
            throw new IllegalStateException(" discovery manager's "
                                            +"setGroups() method was "
                                            +"called after the manager "
                                            +"was terminated");
        }//end try
    }//end updateDiscoveryMgrGroups
    /* END Private Group Management Methods -------------------------------- */

    /* BEGIN Private Locator Management Methods ---------------------------- */
    /**
     * Called by the public method <code>addLocators</code>. This method
     * queues an <code>AddLocatorsTask</code> which performs the actual
     * augmentation of the given registration's desired locators.
     * 
     * @param regInfo  the data structure record corresponding to the
     *                 registration whose managed set of locators is to be
     *                 augmented
     * @param locators an array, none of whose elements may be null,
     *                 consisting of the <code>LookupLocator</code> objects
     *                 with which to augment the registration's managed set
     *                 of locators.
     * 
     * @see com.sun.jini.fiddler.FiddlerImpl#addLocators
     * @see com.sun.jini.fiddler.FiddlerRegistration#addLocators
     * @see net.jini.discovery.LookupDiscoveryRegistration#addLocators
     */
    private void addLocatorsDo(RegistrationInfo regInfo,
                               LookupLocator[]  locators)
    {
        taskMgr.add(new AddLocatorsTask(regInfo,locators));
    }//end addLocatorsDo

    /**
     * Called by the <code>apply</code> method of the class
     * <code>LocsAddedToRegistrationLogObj</code> (which is invoked 
     * during state recovery). This method queues an
     * <code>AddLocatorsTask</code> which performs the actual augmentation
     * of the given registration's desired locators.
     * <p>
     * @param registrationID   the ID of the data structure record
     *                         corresponding to the registration whose
     *                         managed set of locators is to be augmented
     * @param registrationByID the map containing all active registrations
     *                         managed by this service
     * @param locators         an array, none of whose elements may be null,
     *                         consisting of the <code>LookupLocator</code>
     *                         objects with which to augment the
     *                         registration's managed set of locators.
     * 
     * @see com.sun.jini.fiddler.FiddlerImpl#addLocatorsDo
     * @see com.sun.jini.fiddler.FiddlerImpl#addLocators
     * @see com.sun.jini.fiddler.FiddlerRegistration#addLocators
     * @see net.jini.discovery.LookupDiscoveryRegistration#addLocators
     */
    private void addLocatorsDo(Uuid registrationID,
                               HashMap registrationByID,
                               LookupLocator[] locators)
    {
        addLocatorsDo((RegistrationInfo)(registrationByID.get(registrationID)),
                      locators);
    }//end addLocatorsDo

    /**
     * Called by the public method <code>setLocators</code>. This method
     * queues a <code>SetLocatorsTask</code> which performs the 
     * actual replacement.
     * 
     * @param regInfo  the data structure record corresponding to the
     *                 registration whose managed set of locators is to be
     *                 replaced
     * @param locators an array, none of whose elements may be null,
     *                 consisting of the <code>LookupLocator</code> objects
     *                 with which to replace the registration's managed set
     *                 of locators.
     * 
     * @see com.sun.jini.fiddler.FiddlerImpl#setLocators
     * @see com.sun.jini.fiddler.FiddlerRegistration#setLocators
     * @see net.jini.discovery.LookupDiscoveryRegistration#setLocators
     */
    private void setLocatorsDo(RegistrationInfo regInfo,
                               LookupLocator[]  locators)
    {
        taskMgr.add(new SetLocatorsTask(regInfo,locators));
    }//end setLocatorsDo

    /**
     * Called by the <code>apply</code> method of the class
     * <code>LocsSetInRegistrationLogObj</code> (which is invoked 
     * during state recovery). This method queues a
     * <code>SetLocatorsTask</code> which performs the actual replacement.
     * <p>
     * @param registrationID   the ID of the data structure record
     *                         corresponding to the registration whose
     *                         managed set of locators is to be replaced
     * @param registrationByID the map containing all active registrations
     *                         managed by this service
     * @param locators         an array, none of whose elements may be null,
     *                         consisting of the <code>LookupLocator</code>
     *                         objects with which to replace the
     *                         registration's managed set of locators.
     *
     * @see com.sun.jini.fiddler.FiddlerImpl#setLocatorsDo
     * @see com.sun.jini.fiddler.FiddlerImpl#setLocators
     * @see com.sun.jini.fiddler.FiddlerRegistration#setLocators
     * @see net.jini.discovery.LookupDiscoveryRegistration#setLocators
     */
    private void setLocatorsDo(Uuid registrationID,
                               HashMap registrationByID,
                               LookupLocator[] locators)
    {
        setLocatorsDo((RegistrationInfo)(registrationByID.get(registrationID)),
                      locators);
    }//end setLocatorsDo

    /**
     * Called by the public method <code>removeLocators</code>. This method
     * queues a <code>RemoveLocatorsTask</code> which performs the 
     * actual removal.
     * 
     * @param regInfo  the data structure record corresponding to the
     *                 registration from whose managed set of locators the
     *                 input set of locators is to be removed
     * @param locators an array, none of whose elements may be null,
     *                 consisting of the <code>LookupLocator</code> objects
     *                 to remove from the registration's managed set of 
     *                 locators.
     *
     * @see com.sun.jini.fiddler.FiddlerImpl#removeLocators
     * @see com.sun.jini.fiddler.FiddlerRegistration#removeLocators
     * @see net.jini.discovery.LookupDiscoveryRegistration#removeLocators
     */
    private void removeLocatorsDo(RegistrationInfo regInfo,
                                  LookupLocator[]  locators)
    {
        taskMgr.add(new RemoveLocatorsTask(regInfo,locators));
    }//end removeLocatorsDo

    /**
     * Called by the <code>apply</code> method of the class
     * <code>LocsRemovedFromRegistrationLogObj</code> (which is
     * invoked during state recovery). This method queues a
     * <code>RemoveLocatorsTask</code> which performs the actual removal.
     * <p>
     * @param registrationID   the ID of the data structure record
     *                         corresponding to the registration from whose
     *                         managed set of groups the input set of locators
     *                         is to be removed
     * @param registrationByID the map containing all active registrations
     *                         managed by this service
     * @param locators         an array, none of whose elements may be null,
     *                         consisting of the <code>LookupLocator</code>
     *                         objects to remove from the registration's
     *                         managed set of locators.
     *
     * @see com.sun.jini.fiddler.FiddlerImpl#removeLocatorsDo
     * @see com.sun.jini.fiddler.FiddlerImpl#removeLocators
     * @see com.sun.jini.fiddler.FiddlerRegistration#removeLocators
     * @see net.jini.discovery.LookupDiscoveryRegistration#removeLocators
     */
    private void removeLocatorsDo(Uuid registrationID,
                                  HashMap registrationByID,
                                  LookupLocator[] locators)
    {
        removeLocatorsDo
                   ((RegistrationInfo)(registrationByID.get(registrationID)),
                    locators);
    }//end removeLocatorsDo

    /** Builds a set containing the locators from all registrations. 
     *
     *  Iterates through all the active registrations, retrieving the locators
     *  to discover for each registration (minus duplicates).
     */
    private LookupLocator[] getLocatorsFromAllRegs() {
        HashSet locatorSet = new HashSet();
        for(Iterator itr=registrationByID.values().iterator();itr.hasNext();) {
            RegistrationInfo rInfo = (RegistrationInfo)itr.next();
            if(rInfo.locators == null) {
                throw new AssertionError
                            ("registration contains a null set of locators");
            } else {
                locatorSet.addAll(rInfo.locators);
            }//end if
        }//end loop
        return (LookupLocator[])locatorSet.toArray
                                        (new LookupLocator[locatorSet.size()]);
    }//end getLocatorsFromAllRegs

    /** This method returns a registrar-to-data-structure map in which each
     *  registrar key in the returned map is one of the keys from the
     *  the global map <code>allDiscoveredRegs</code>, and the corresponding 
     *  value is the (locator,groups) pair that corresponds to that registrar
     *  key in <code>allDiscoveredRegs</code>
     *  
     *  An element of <code>allDiscoveredRegs</code> is selected to have
     *  its registrar and associated (locator,groups) pair be included in the
     *  returned mapping if and only if the key value under consideration is
     *  a registrar whose locator equals one of the desired locators of the
     *  given registration (<code>regInfo</code> parameter). That is, the
     *  registrars referenced in the returned mapping are the registrars that
     *  are of interest - through locator discovery - to the given
     *  registration.
     *
     * @param regInfo the data structure record corresponding to the
     *                registration whose locators-to-discover will be used to
     *                select the elements from <code>allDiscoveredRegs</code>
     *                to include in the return mapping
     * 
     *  @return a mapping in which the key values are registrars that,
     *          in addition to being elements of the global map
     *          <code>allDiscoveredRegs</code>, have locators that are
     *          elements of the set of desired locators referenced in the
     *          <code>regInfo</code> parameter; and whose map values are
     *          data structures of type <code>LocatorGroupsStruct</code> that
     *          contain the associated locator and member groups of the
     *          corresponding registrar key
     */
    private HashMap getDesiredRegsByLocator(RegistrationInfo regInfo) {
        HashSet desiredLocators = regInfo.locators;
        HashMap desiredRegMap = new HashMap(allDiscoveredRegs.size());
        Set eSet = allDiscoveredRegs.entrySet();
        for(Iterator itr = eSet.iterator(); itr.hasNext(); ) {
            Map.Entry pair = (Map.Entry)itr.next();
            LocatorGroupsStruct locGroupsStruct
                                      = (LocatorGroupsStruct)pair.getValue();
            if(locSetContainsLoc(desiredLocators,locGroupsStruct.locator)) {
                desiredRegMap.put((ServiceRegistrar)pair.getKey(),
                                  locGroupsStruct);
            }//endif
        }//end loop
        return desiredRegMap;
    }//end getDesiredRegsByLocator

    /** Modifies the discovery manager's managed set of locators in the
     *  appropriate way; that is, adds to the discovery manager's managed
     *  set of locators, any new locators-of-interest, across all
     *  registrations, and removes any locators that are no longer of
     *  interest to any of the registrations. This method is typically
     *  called after the locators of a particular registration have been
     *  modified.
     *
     *  Note that this method must be called from within a synchronization
     *  block.
     */
    private void updateDiscoveryMgrLocators() {
        /* Build the set containing the locators from all registrations. */
        LookupLocator[] locsAcrossAllRegs = getLocatorsFromAllRegs();
        /* Update the discovery manager's set of locators to discover. Let
         * the manager sort out duplicates and any already-discovered locators.
         */
        try {
            discoveryMgr.setLocators( locsAcrossAllRegs );
        } catch(IllegalStateException e) {
            String errStr = "IllegalStateException: discovery "
                            +"manager's setLocators() method was called "
                            +"after the manager was terminated";
            problemLogger.log(Level.INFO, errStr, e);
            Entry[] errorAttrs = new Entry[]
                                        { new FiddlerStatus(StatusType.ERROR),
                                          new Comment(errStr)
                                        };
            joinMgr.addAttributes(errorAttrs,true);
            throw new IllegalStateException(" discovery manager's "
                                            +"setLocators() method was "
                                            +"called after the manager "
                                            +"was terminated");
        }//end try
    }//end updateDiscoveryMgrLocators
    /* END Private Locator Management Methods ------------------------------ */

    /* BEGIN Private Lease Renewal Methods --------------------------------- */
    /**
     * Called by the public method <code>renewLease</code>. This method renews
     * the lease corresponding to the <code>registrationID</code> and 
     * <code>leaseID</code> parameters, granting a new duration that is
     * less than or equal to the requested duration value contained in
     * the <code>duration</code> parameter.
     * 
     * @param regInfo  the data structure record corresponding to the
     *                 registration whose lease is to be renewed
     * @param leaseID  identifier assigned by the lease grantor to the lease
     *                 that is to be renewed
     * @param duration the requested duration for the lease being renewed
     *
     * @throws net.jini.core.lease.UnknownLeaseException this exception occurs
     *         when the lease being renewed is unknown to the lease grantor.
     *
     * @return <code>long</code> value representing the actual duration that
     *         was granted for the renewed lease. Note that the actual
     *         duration granted and returned by this method may be less than
     *         the duration requested.
     *
     * @see com.sun.jini.fiddler.FiddlerImpl#renewLease
     */
    private long renewLeaseDo(RegistrationInfo regInfo,
                              Uuid leaseID,
                              long duration) throws UnknownLeaseException
    {
        long curTime       = System.currentTimeMillis();
        long newExpiration = renewLeaseInt(regInfo,leaseID,duration,curTime);
        addLogRecord(new LeaseRenewedLogObj
                           (regInfo.registrationID, leaseID, newExpiration));
        return (newExpiration - curTime);
    }//end renewLeaseDo

    /**
     * Called by the method <code>renewLeaseDo</code>. This method takes
     * a requested duration for the lease corresponding to the
     * <code>registrationID</code> and <code>leaseID</code> parameters,
     * converts that duration to an absolute expiration (based on the local
     * clock) with the appropriate bound applied, and attempts to renew 
     * the lease, granting the new expiration time on the lease.
     * 
     * @param regInfo  the data structure record corresponding to the
     *                 registration whose lease is to be renewed
     * @param leaseID  identifier assigned by the lease grantor to the lease
     *                 that is to be renewed
     * @param duration the requested duration for the lease being renewed
     *
     * @return <code>long</code> value representing the new absolute
     *         expiration time granted for the renewed lease.
     *
     * @throws net.jini.core.lease.UnknownLeaseException this exception occurs
     *         when the lease being renewed is unknown to the lease grantor.
     *
     * @see com.sun.jini.fiddler.FiddlerImpl#renewLeaseDo
     */
    private long renewLeaseInt(RegistrationInfo regInfo,
                               Uuid leaseID,
                               long duration,
                               long curTime)  throws UnknownLeaseException
    {
        if(duration == Lease.ANY) {
            duration = leaseBound;
        } else if(duration <= 0) {
            throw new IllegalArgumentException("non-positive lease duration");
        }//endif
        if (    (regInfo == null) 
             || ( !(regInfo.leaseID).equals(leaseID) )
             || (regInfo.leaseExpiration <= curTime) ) {
            throw new UnknownLeaseException();
        }//endif
        if(    (duration > leaseBound)
	    && (duration > (regInfo.leaseExpiration - curTime)) ) {
            duration = Math.max( (regInfo.leaseExpiration - curTime),
                                 leaseBound );
        }//endif
        long newExpiration = curTime + duration;
        /* Force a re-sort in the registrationByTime map */
        registrationByTime.remove(regInfo);       // force first re-sort
        regInfo.leaseExpiration = newExpiration;  // modify outside of map
        registrationByTime.put(regInfo, regInfo); // insert & force new sort
        /* see if the expire thread needs to wake up earlier */
        if (newExpiration < minExpiration) {
            minExpiration = newExpiration;
            concurrentObj.waiterNotify(leaseExpireThreadSyncObj);
        }//endif
        return newExpiration;
    }//end renewLeaseInt

    /**
     * This method performs the final steps in the process of renewing the
     * lease on the registration corresponding to the <code>regInfo</code>
     * and <code>leaseID</code> parameters, granting a requested absolute
     * expiration time for that lease.
     * 
     * @param regInfo    the data structure record corresponding to the
     *                   registration whose lease is to be renewed
     * @param leaseID    identifier assigned by the lease grantor to the lease
     *                   that is to be renewed
     * @param expiration the requested absolute expiration time for the lease
     *                   being renewed
     */
    private void renewLeaseAbs(RegistrationInfo regInfo,
                               Uuid leaseID,
                               long expiration)
    {
        if ( (regInfo == null) || (regInfo.leaseID != leaseID) ) return;
        /* The act of renewing a registration's lease simply involves 
         * changing the expiration time stored in the registration's
         * data structure. But because the registrationByTime sorts its
         * elements by time, changing the value of the leaseExpiration field
         * of the regInfo parameter "in place" would typically result in 
         * the registrationByTime map being out of order. Contrast this with
         * the modification of the registration's non-time-dependent fields
         * (for example, adding, replacing or removing groups or locators),
         * where modifying the input regInfo parameter will modify the
         * appropriate field of the corresponding element of both the
         * registrationByID and registrationByTime maps; and the order 
         * will still be valid in the registrationByTime map because the
         * time-dependent data has not changed.
         * 
         * Thus, in order to maintain a valid ordering in the 
         * registrationByTime map, a re-sort is forced by first removing
         * from that map the element corresponding to regInfo, resetting
         * the leaseExpiration field of that object, and then re-inserting
         * the modified regInfo into the map, which forces another sort.
         */
        registrationByTime.remove(regInfo);       // force first re-sort
        regInfo.leaseExpiration = expiration;     // modify outside of map
        registrationByTime.put(regInfo, regInfo); // insert & force new sort
    }//end renewLeaseAbs

    /**
     * Called by the <code>apply</code> method of the class
     * <code>LeaseRenewedLogObj</code> (which is invoked during state
     * recovery). This method performs the final steps in the process
     * of renewing the lease on the registration corresponding to the
     * <code>registrationID</code> and <code>leaseID</code> parameters,
     * granting a requested absolute expiration time for that lease.
     * 
     * @param registrationID   the ID of the data structure record
     *                         corresponding to the registration whose
     *                         lease is to be renewed
     * @param registrationByID the map containing all active registrations
     *                         managed by this service
     * @param leaseID          identifier assigned by the lease grantor to
     *                         the lease that is to be renewed
     * @param expiration       the requested absolute expiration time for the
     *                         lease being renewed
     *
     * @see com.sun.jini.fiddler.FiddlerImpl#renewLeaseDo
     */
    private void renewLeaseAbs(Uuid registrationID,
                               HashMap registrationByID,
                               Uuid leaseID,
                               long expiration)
    {
        renewLeaseAbs((RegistrationInfo)(registrationByID.get(registrationID)),
                      leaseID,expiration);
    }//end renewLeaseAbs

    /**
     * Called by the public method renewLeases to renew all of
     * the leases from a <code>LeaseMap</code> that correspond to the
     * elements of the <code>registrationIDs</code> and <code>leaseIDs</code>
     * parameters, granting as new durations the corresponding elements
     * of the <code>duration</code> parameter.
     * <p>
     * Note that all of the input parameters must be the same length.
     *
     * @param registrationIDs array containing the unique identifiers assigned
     *                        to the each registration to which each lease 
     *                        to be renewed corresponds
     * @param leaseIDs        array containing the identifiers assigned by the
     *                        lease grantor to each lease being renewed
     * @param durations       array containing the requested durations for 
     *                        each lease being renewed
     * 
     * @return an instance of FiddlerRenewResults containing data corresponding
     *         to the results (granted durations or exceptions) of each
     *         renewal attempt
     *
     * @see com.sun.jini.fiddler.FiddlerImpl#renewLeases
     */
    private FiddlerRenewResults renewLeasesDo(Uuid[] registrationIDs,
				              Uuid[] leaseIDs,
				              long[] durations)
    {
        long curTime = System.currentTimeMillis();
        Exception[] exceptions = null;
        for (int i = 0; i < registrationIDs.length; i++) {
            RegistrationInfo regInfo
                = (RegistrationInfo)(registrationByID.get(registrationIDs[i]));
            try {
                durations[i] = renewLeaseInt(regInfo, leaseIDs[i],
                                             durations[i], curTime);
            } catch (Exception e) {
                durations[i] = -1;
                if (exceptions == null) {
                    exceptions = new Exception[]{e};
                } else {
                    exceptions = (Exception[])appendArray(exceptions, e);
                }//endif
            }
	}//end loop
        /* don't bother to weed out problem leases */
        addLogRecord
             ( new LeasesRenewedLogObj(registrationIDs,leaseIDs,durations) );
        for (int i = registrationIDs.length; --i >= 0; ) {
            if (durations[i] >= 0) {
                durations[i] -= curTime;
            }//endif
        }//end loop
        return new FiddlerRenewResults(durations, exceptions);
    }//end renewLeasesDo

    /**
     * Using the absolute expiration times contained in the
     * <code>expirations</code> parameter, renews all of the leases 
     * from a <code>LeaseMap</code> that correspond to the elements 
     * of the <code>registrationIDs</code> and <code>leaseIDs</code>
     * parameters; skipping any negative expiration times.
     * <p>
     * Note that all of the input parameters must be the same length.
     *
     * @param registrationIDs array containing the unique identifiers assigned
     *                        to the each registration to which each lease 
     *                        to be renewed corresponds
     * @param leaseIDs        array containing the identifiers assigned by the
     *                        lease grantor to each lease being renewed
     * @param expirations     array containing the absolute expiration times
     *                        to which to renew each lease
     *
     * @see com.sun.jini.fiddler.FiddlerImpl#renewLeases
     * @see com.sun.jini.fiddler.FiddlerImpl#renewLeaseAbs
     */
    private void renewLeasesAbs(Uuid[] registrationIDs,
                                Uuid[] leaseIDs,
                                long[] expirations)
    {
        for (int i = registrationIDs.length; --i >= 0; ) {
            long expiration = expirations[i];
            if (expiration < 0)  continue;
            RegistrationInfo regInfo
                = (RegistrationInfo)(registrationByID.get(registrationIDs[i]));
            renewLeaseAbs(regInfo, leaseIDs[i], expiration);
        }//end loop
    }//end renewLeasesAbs
    /* END Private Lease Renewal Methods ----------------------------------- */

    /* BEGIN Private Lease Cancellation Methods ---------------------------- */
    /**
     * Called by the public method <code>cancelLease</code>. This method
     * cancels the lease on the registration corresponding to the 
     * <code>regInfo</code> and <code>leaseID</code> parameters.
     * 
     * @param regInfo the data structure record corresponding to the
     *                registration whose lease is to be cancelled
     * @param leaseID identifier assigned by the lease grantor to the lease
     *                that is to be cancelled
     *
     * @throws net.jini.core.lease.UnknownLeaseException this exception occurs
     *         when the lease being cancelled is unknown to the lease grantor.
     * 
     * @throws java.io.IOException this exception occurs when the multicast
     *         request protocol experiences a failure.
     *
     * @see com.sun.jini.fiddler.FiddlerImpl#cancelLease
     */
    private void cancelLeaseDo(RegistrationInfo regInfo, Uuid leaseID)
                                     throws UnknownLeaseException, IOException
    {
        long curTime = System.currentTimeMillis();
        if ( (regInfo == null) || (regInfo.leaseExpiration <= curTime) ) {
            throw new UnknownLeaseException();
        }//endif
        removeRegistration(regInfo);
        /* wake up thread if this might be the (only) earliest time */
        if (regInfo.leaseExpiration == minExpiration) {
            concurrentObj.waiterNotify(leaseExpireThreadSyncObj);
        }//endif
    }//end cancelLeaseDo

    /**
     * Called by the <code>apply</code> method of the class
     * <code>LeaseCancelledLogObj</code> (which is invoked during state
     * recovery). This method cancels the lease on the registration
     * corresponding to the <code>registrationID</code> and
     * <code>leaseID</code> parameters.
     * 
     * @param registrationID   the ID of the data structure record
     *                         corresponding to the registration whose
     *                         lease is to be cancelled
     * @param registrationByID the map containing all active registrations
     *                         managed by this service
     * @param leaseID          identifier assigned by the lease grantor to
     *                         the lease that is to be cancelled
     *
     * @throws net.jini.core.lease.UnknownLeaseException this exception occurs
     *         when the lease being cancelled is unknown to the lease grantor.
     * 
     * @throws java.io.IOException this exception occurs when the multicast
     *         request protocol experiences a failure.
     *
     * @see com.sun.jini.fiddler.FiddlerImpl#cancelLeaseDo
     * @see com.sun.jini.fiddler.FiddlerImpl#cancelLease
     */
    private void cancelLeaseDo(Uuid registrationID,
                               HashMap registrationByID,
                               Uuid leaseID)
                                     throws UnknownLeaseException, IOException
    {
        cancelLeaseDo((RegistrationInfo)(registrationByID.get(registrationID)),
                      leaseID);
    }//end cancelLeaseDo

    /**
     * Called by the public method cancelLeases to the cancel all of
     * the leases from a <code>LeaseMap</code> that correspond to the
     * elements of the <code>registrationIDs</code> and <code>leaseIDs</code>
     * parameters.
     * <p>
     * The two input arrays must be the same length. If there are no
     * exceptions, the return value is null. Otherwise, the return value
     * has the same length as the <code>registrationIDs</code> parameter,
     * and has nulls for the leases that were successfully renewed.
     * 
     * @return an array containing the instances of Exception (or null for
     *         successfully renewed leases), each corresponding to the 
     *         exception that occurred upon attempting renewal.
     *
     * @see com.sun.jini.fiddler.FiddlerImpl#cancelLeases
     */
    private Exception[] cancelLeasesDo(Uuid[] registrationIDs,
                                       Uuid[] leaseIDs)
    {
        Exception[] exceptions = null;
        for (int i = registrationIDs.length; --i >= 0; ) {
            try {
                RegistrationInfo regInfo =
                     (RegistrationInfo)(registrationByID.get
                                                         (registrationIDs[i]));
                cancelLeaseDo(regInfo, leaseIDs[i]);
            } catch (Exception e) {
                if (exceptions == null) {
                    exceptions = new Exception[registrationIDs.length];
                }//endif
                exceptions[i] = e;
            }
        }//end loop
        return exceptions;
    }//end cancelLeasesDo
    /* END Private Lease Cancellation Methods ------------------------------ */

    /* BEGIN Private Event-Related Methods --------------------------------- */
    /** Returns the set of registrars common to both input parameters */
    private ServiceRegistrar[] intersectRegSets(ServiceRegistrar[] regs0,
                                                ServiceRegistrar[] regs1)
    {
        HashSet regSet = new HashSet(); //no duplicates
        /* Compare each input element of regs0 with each element of regs1,
         * storing matches along the way
         */
        nextRegistrar:
        for(int i=0;i<regs0.length;i++) {
            for(int j=0;j<regs1.length;j++) {
                if( (regs0[i]).equals(regs1[j]) ) {
                    regSet.add(regs0[i]);
                    continue nextRegistrar; // match found, next reg
                }//endif
            }//end loop(j)
        }//end loop(i)
        return ( (ServiceRegistrar[])(regSet).toArray
                                      (new ServiceRegistrar[regSet.size()]) );
    }//end intersectRegSets

    /** Returns true if the input registrar is an element of the given array */
    private boolean regIsElementOfRegSet(ServiceRegistrar   reg,
                                         ServiceRegistrar[] regSet)
    {
        for(int i=0;i<regSet.length;i++) {
            if( (regSet[i]).equals(reg) )  return true;
        }//end loop
        return false;
    }//end regIsElementOfRegSet

    /**
     * This method determines which of the registrars in the
     * <code>regsMap</code> parameter belong to the set of registrars
     * the given <code>regInfo</code> parameter wishes to discover. It
     * then adds those registrars to the regInfo's map of discovered
     * registrars (including only those registrars that can be successfully
     * serialized). Finally, a discovered event containing the discovery
     * information that is of interest to the regInfo is built and sent
     * to the regInfo's listener.
     * 
     * @param regInfo the data structure record corresponding to the
     *                registration whose listener will receive the event
     * @param regsMap mapping in which the key values are previously discovered
     *                registrars, and the map values are data structures of
     *                type <code>LocatorGroupsStruct</code> that contain the
     *                locator and member groups of the corresponding 
     *                registrar key
     */
    private void maybeSendDiscoveredEvent(RegistrationInfo regInfo,
                                          Map regsMap)
    {
        /* Determine the registrars that are common to both the input set of
         * registrars (the currently or newly discovered registrars) and the
         * registration's set of desired registrars. These are the registrars
         * to send in the event.
         */
        HashMap regsToAdd = new HashMap(regsMap.size());
        Set eSet = regsMap.entrySet();
        for(Iterator itr = eSet.iterator(); itr.hasNext(); ) {
            Map.Entry pair = (Map.Entry)itr.next();
            ServiceRegistrar reg = (ServiceRegistrar)pair.getKey();
            LocatorGroupsStruct curStructVal
                                      = (LocatorGroupsStruct)pair.getValue();
            /* If regInfo wants ALL_GROUPS, then every registrar is desired */
            if(regInfo.groups == null) { // ALL_GROUPS
                regsToAdd.put(reg,curStructVal);
            } else {//not ALL_GROUPS, reg's locator/groups desired by regInfo?
                if( interested(curStructVal.locator,curStructVal.groups,
                               regInfo.locators,regInfo.groups) )
                {
                    regsToAdd.put(reg,curStructVal);
		}//endif
	    }//endif
        }//end loop
        /* Add the registrars that are both desired and discovered to the
         * registration's map of discovered registrars. If any registrars
         * cannot be serialized, drop them.
         */
        HashMap regsAdded = regInfo.addToDiscoveredRegs(regsToAdd);
        /* Build and send a "discovered event" */
        RemoteDiscoveryEvent event = buildEvent(regInfo,regsAdded,false);
        if(event != null) {
            queueEvent(regInfo,event);
            logInfoEvents("NewReg/Discovered EventTask.run(): "
                          +"DISCOVERED Event was SENT\n");
        }//endif
    }//end maybeSendDiscoveredEvent

    /** 
     * Examines the <code>discardFlag</code> for each active registration
     * until a value of <code>true</code> is encountered or the set of
     * registrations is exhausted. If that flag is <code>false</code> for
     * all registrations, this method will return <code>null</code>;
     * otherwise, it will return the registration found, but with the
     * the value of the flag set back to <code>false</code>.
     *
     * @return the first instance of <code>RegistrationInfo</code> whose 
     *         <code>discardFlag</code> is set to <code>true</code>;
     *         returns <code>null</code> if no such registration exists.
     */
    private RegistrationInfo externalDiscardRequest() {
        /* Loop thru regInfo's, until one is found with a true flag*/
        for(Iterator itr=registrationByID.values().iterator();itr.hasNext();) {
            RegistrationInfo regInfo = (RegistrationInfo)itr.next();
            if(regInfo.discardFlag == true) {
                logInfoDiscard("\nexternalDiscardRequest: "
                               +"discardFlag == true\n");
                regInfo.discardFlag = false;
                return regInfo;
            }//endif
        }//end loop
        return null;
    }//end externalDiscardRequest

    /**
     * Convenience method that creates and returns a mapping of a single
     * <code>ServiceRegistrar</code> instance to a set of groups.
     * 
     * @param reg       instance of <code>ServiceRegistrar</code> 
     *                  corresponding to the registrar to use as the key
     *                  to the mapping
     * @param groups    <code>String</code> array containing the current
     *                  member groups of the registrar referenced by the 
     *                  <code>reg</code> parameter; and which is used
     *                  as the value of the mapping
     *
     * @return <code>Map</code> instance containing a single mapping from
     *         a given registrar to its current member groups
     */
    private HashMap mapRegToGroups(ServiceRegistrar reg, String[] groups) {
        HashMap groupsMap = new HashMap(1);
        groupsMap.put(reg,groups);
        return groupsMap;
    }//end mapRegToGroups

    /** Given a set of registrars that have just been discarded, this method
     *  determines which of those registrars are contained in none of the
     *  discovered sets of the active registrations; and then removes
     *  those registrars from the global maps of registrars that are
     *  maintained across all registrations.
     *
     *  Note that this method must be called from within a synchronization
     *  block.
     *
     * @param discardedRegs set of registrars that were just discarded
     */
    private void maybeRemoveDiscardedRegsFromGlobalSet(Set discardedRegs) {
        for(Iterator itr = discardedRegs.iterator();itr.hasNext(); ) {
            maybeRemoveDiscardedRegFromGlobalSet(itr.next());
        }//end loop(itr)
    }//end maybeRemoveDiscardedRegsFromGlobalSet

    /** Given a registrar that has just been discarded, this method
     *  determines if that registrar is contained in none of the discovered
     *  sets of the active registrations; and then removes that registrar
     *  from the global maps of registrars that are maintained across all
     *  registrations.
     *
     *  Note that this method must be called from within a synchronization
     *  block.
     *
     * @param dReg set of registrars that were just discarded
     */
    private void maybeRemoveDiscardedRegFromGlobalSet(Object dReg) {
        for(Iterator jtr=registrationByID.values().iterator();jtr.hasNext();){
            RegistrationInfo regInfo = (RegistrationInfo)jtr.next();
            if( (regInfo.discoveredRegsMap).containsKey(dReg) ) {
                return; // this dReg is in at least 1 regInfo, goto next reg
            }//endif
        }//end loop(jtr)
        /* discarded reg contained in no regInfo, remove it from map */
        allDiscoveredRegs.remove(dReg);
    }//end maybeRemoveDiscardedRegFromGlobalSet

    /** For each registrar referenced in the global map allDiscoveredRegs,
     *  this method replaces the associated set of member groups with the
     *  corresponding set in the given registrars-to-groups map. This
     *  method should be called only after a changed event is received 
     *  from the discovery manager.
     *
     *  Note that this method must be called from within a synchronization
     *  block.
     *
     * @param groupsMap map containing the registrars and their new groups
     */
    private void updateGroupsInGlobalSet(Map groupsMap) {
        Set eSet = groupsMap.entrySet();
        for(Iterator itr = eSet.iterator(); itr.hasNext(); ) {
            Map.Entry pair = (Map.Entry)itr.next();
            ServiceRegistrar reg = (ServiceRegistrar)pair.getKey();
            if(allDiscoveredRegs.containsKey(reg)) {
                LookupLocator loc
                  = ((LocatorGroupsStruct)allDiscoveredRegs.get(reg)).locator;
                String[] newGroups = (String[])pair.getValue();
                LocatorGroupsStruct locGroups
                                     = new LocatorGroupsStruct(loc,newGroups);
                allDiscoveredRegs.put(reg,locGroups);
            }//endif
        }//end loop
    }//end updateGroupsInGlobalSet

    /**
     * This method constructs the appropriate remote discovery event from the
     * information contained in the input parameters. This method encapsulates
     * common exception-handling functionality. If the remote discovery
     * event cannot be successfully constructed, null will be returned.
     * 
     * @param regInfo   the data structure record corresponding to the 
     *                  registration whose listener will receive the event
     * @param groupsMap map containing the registrars, and their corresponding
     *                  member groups, to include in the event
     * @param discarded flag indicating whether the registrars included in
     *                  the event have been discarded or discovered
     *
     * @return instance of <code>RemoteDiscoveryEvent</code> containing the
     *         appropriate information corresponding to the input registration
     *         record
     */
    private RemoteDiscoveryEvent buildEvent(RegistrationInfo regInfo,
                                            Map groupsMap,
                                            boolean discarded)
    {
        RemoteDiscoveryEvent newEvent = null;
        if(groupsMap.size() > 0) {
            try {
                newEvent = new RemoteDiscoveryEvent(outerProxy,
                                                    regInfo.eventID,
                                                    ++regInfo.seqNum,
                                                    regInfo.handback,
                                                    discarded,
                                                    groupsMap);
                logInfoEvents(groupsMap,regInfo.eventID,regInfo.seqNum,
                              regInfo.handback,discarded,
                              eventsLogger, Level.FINE);
            } catch (IOException e) {
                /* The constructor for <code>RemoteDiscoveryEvent</code>
                 * throws an <code>IOException</code> if the constructor
                 * fails to successfully serialize even one registrar from
                 * the input set (which means there is no need to send the
                 * event since there are no marshalled registrars to send).
                 * When such a situation occurs, register an ERROR status
                 * attribute (along with a Comment attribute describing the
                 * nature of the problem) to all lookup services with which
                 * this service is registered. 
                 *
                 * Administrative clients, as well as clients that use this 
                 * service should have registered for notification of the 
                 * existence of this attribute.
                 */
                String eStr = "Failed to serialize ALL registrars during "
                              +"event construction ... could not send event";
                problemLogger.log(Level.INFO, eStr, e);
                Entry[] errorAttrs =
                new Entry[] { new FiddlerStatus(StatusType.WARNING),
                              new Comment(eStr)
                            };
                joinMgr.addAttributes(errorAttrs,true);
            }//end try
        }//endif
        return newEvent;
    }//end buildEvent

    /**
     * This method simply queues a new <code>SendEventTask</code> instance
     * that will send the given remote event to the given registration's
     * listener.
     * 
     * @param regInfo the data structure record corresponding to the 
     *                registration whose listener will receive the event
     * 
     * @param event   the instance of <code>RemoteDiscoveryEvent</code> to
     *                send to the registration's listener
     */
    private void queueEvent(RegistrationInfo regInfo,
                           RemoteDiscoveryEvent event)
    {
        taskMgr.add(new SendEventTask(regInfo,event));
    }//end queueEvent
    /* END Private Event-Related Methods ----------------------------------- */

    /* BEGIN Persistent State Logging Interfaces & Classes ----------------- */
    /**
     * Writes the current state of this service to persistent storage.
     * <p>
     * A 'snapshot' of the service's current state is represented by
     * the data contained in certain fields defined in the service. 
     * That data represents many changes -- over time -- to the service's
     * state. This method will record that data to a file referred to as
     * the snapshot file.
     * <p>
     * The data written by this method to the snapshot file -- as well as
     * the format of the file -- is shown below:
     * <ul>
     * <li> the service's class name
     * <li> log format version number
     * <li> the unique ID of the proxy to the current instance of this service
     * <li> the current event ID
     * <li> the current join state of this service
     * <li> the current configuration parameters of this service
     * <li> contents of the container holding the current set of registrations
     * <li> null (termination 'marker' for the set of registrations)
     * </ul>
     * Each data item is written to the snapshot file in serialized form.
     * 
     * @see FiddlerImpl.LocalLogHandler
     */
    private void takeSnapshot(OutputStream  out) throws IOException {
        ObjectOutputStream stream = new ObjectOutputStream(out);
        /* Stable information about the current instance of this service */
        stream.writeUTF(FiddlerImpl.class.getName());
        stream.writeInt(LOG_VERSION);
        stream.writeObject(proxyID);
        /* The current event ID assigned to the discovery events being sent */
        stream.writeLong(curEventID);
        /* The current join state of this service */
        stream.writeObject(thisServicesGroups);
        stream.writeObject(thisServicesLocators);
        stream.writeObject( marshalAttributes(this,thisServicesAttrs) );
        /* The current configuration parameters of this service */
        stream.writeLong(leaseBound);
        stream.writeInt(snapshotThresh);
        stream.writeFloat(snapshotWt);
        /* The current set of registrations */
        for(Iterator iter=registrationByID.values().iterator();iter.hasNext();)
        {
            stream.writeObject(iter.next());
        }//end loop
        stream.writeObject(null); //termination marker
        stream.flush();
    }//end takeSnapshot

    /**
     * Retrieve the contents of the snapshot file and reconstitute the 'base'
     * state of this service from the retrieved data.
     * <p>
     * Refer to <code>takeSnapshot</code> for a description of the data
     * retrieved by this method from the snapshot file.
     * <p>
     * During recovery, the state of the service at the time of a crash
     * or outage is re-constructed by first reconstituting the 'base state'
     * from the snapshot file; and then modifying that base state according
     * to the records retrieved from the log file. This method is invoked to
     * perform the first step in that reconstruction. As each registered
     * service or event is retrieved, it is inserted into its appropriate
     * container object.
     * <p>
     * Because events can be generated before the next snapshot is taken,
     * the event sequence numbers must be incremented. This is because the
     * event specification requires that a set of event sequence numbers
     * corresponding to an particular event type (a particular event ID)
     * be monotonically increasing. Since the number of events that might
     * have been sent is arbitrary, each sequence number will be incremented
     * by a 'large' number so as to guarantee adherence to the specification.
     * 
     * @see FiddlerImpl.LocalLogHandler#takeSnapshot
     * @see FiddlerImpl.LocalLogHandler
     */
    private void recoverSnapshot(InputStream in)
                                 throws IOException, ClassNotFoundException
    {
        ObjectInputStream stream = new ObjectInputStream(in);
        /* Retrieve the service's stable information */
        if (!FiddlerImpl.class.getName().equals(stream.readUTF())) {
            throw new IOException("log from wrong implementation");
        }//endif
        if (stream.readInt() != LOG_VERSION) {
            throw new IOException("wrong log format version");
        }//endif
        proxyID = (Uuid)stream.readObject();
        /* Retrieve the current event ID */
        curEventID = stream.readLong();
        /* Retrieve the current join state of this service. Groups first. */
        thisServicesGroups   = (String[])stream.readObject();
        /* Retrieve and re-prepare the locators to join (drop bad locs) */
        thisServicesLocators = prepareOldLocators
                                     ( recoveredLocatorToJoinPreparer,
                                       (LookupLocator[])stream.readObject() );
        /* Retrieve the attributes to register with each lookup service. */
        MarshalledObject[] marshalledAttrs
                                    = (MarshalledObject[])stream.readObject();
        thisServicesAttrs = unmarshalAttributes(this, marshalledAttrs);
        /* Retrieve the current configuration parameters of this service */
        leaseBound     = stream.readLong();
        snapshotThresh = stream.readInt();
        snapshotWt     = stream.readFloat();
        RegistrationInfo regInfo;
        while ((regInfo = (RegistrationInfo)stream.readObject()) != null) {
            regInfo.seqNum += Integer.MAX_VALUE;
            addRegistration(regInfo);
        }//end loop
        initialStartup = false;
    }//end recoverSnapshot

    /**
     * Add a state-change record to persistent storage.
     * <p>
     * Whenever a significant change occurs to Fiddler's state, this
     * method is invoked to record that change in a file called a log file.
     * Each record written to the log file is an object reflecting both
     * the data used and the ACTIONS taken to make one change to that
     * state at a particular point in time. If the number of records
     * contained in the log file exceeds the pre-defined threshold,
     * a snapshot of Fiddler's current state will be recorded.
     * <p>
     * Whenever one of the following state changes occurs, this method
     * will be invoked with the appropriate implementation of the
     * LogRecord interface as the input argument.
     * <ul>
     * <li> new attributes were added to Fiddler's attributes with lookup
     * <li> Fiddler's existing attributes with lookup were modified
     *
     * <li> new groups were added to the set of groups Fiddler should join
     * <li> Fiddler's existing groups to join were replaced with a new set
     * <li> Fiddler's existing groups to join were removed
     *
     * <li> new locators were added to the set of locators Fiddler should join
     * <li> Fiddler's existing locators to join were replaced with a new set
     * <li> Fiddler's existing locators to join were removed
     *
     * <li> the bound on granted lease durations was set to a new value
     * <li> weight factor used to determine when to take a snapshot was changed
     * <li> threshold used to determine when to take a snapshot was changed
     *
     * <li> a registration request for use of Fiddler's resources was granted
     *
     * <li> groups were added to a registration's set of groups to discover
     * <li> the set of groups to discover for a registration was replaced
     * <li> the set of groups to discover for a registration was removed
     *
     * <li> locators were added to a registration's set of locators to discover
     * <li> the set of locators to discover for a registration was replaced
     * <li> the set of locators to discover for a registration was removed
     *
     * <li> a lease on a registration with Fiddler was renewed
     * <li> a set of registration leases with Fiddler were renewed
     * <li> a lease on a registration with Fiddler was cancelled
     * <li> a set of registration leases with Fiddler were cancelled
     *
     * </ul>
     * 
     * @see com.sun.jini.reggie.RegistrarImpl.LocalLogHandler
     */
    private void addLogRecord(LogRecord rec) {
        if(log == null) return;//not persistent, don't log

        logInfoAddLogRecord(rec);
        try {
            log.update(rec, true);
            if (++logFileSize >= snapshotThresh) {
                int snapshotSize = registrationByID.size();
                if ((float)logFileSize >= snapshotWt*((float)snapshotSize)) {
                    /* take snapshot */
                    concurrentObj.waiterNotify(snapshotThreadSyncObj);
                }//endif
            }//endif
        } catch (Exception e) {
            if (!Thread.currentThread().isInterrupted()) {
                /* If log updating fails, then register an ERROR status
                 * attribute (along with a Comment attribute describing the
                 * nature of the problem) to all lookup services with which
                 * this service is registered. 
                 *
                 * Administrative clients, as well as clients that use this
                 * service should have registered for notification of the
                 * existence of this attribute.
                 */
                String eStr = "Failure while updating the persistent log "
                              +"containing the service state";
                problemLogger.log(Level.INFO, eStr, e);
                Entry[] errorAttrs = new Entry[]
                      { new FiddlerStatus(StatusType.ERROR),
                        new Comment(eStr)
                      };
                joinMgr.addAttributes(errorAttrs,true);
            }//endif
        }
    }//end addLogRecord

    /**
     * Interface defining the method(s) that must be implemented by each of
     * the concrete LogObj classes. This allows for the definition of
     * object-dependent invocations of the appropriate implementation of
     * the method(s) declared in this interface.
     */
    private static interface LogRecord extends Serializable {
        void apply(FiddlerImpl fiddlerImpl);
    }

    /* 1. Definitions related to state changes generated by JoinAdmin */

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * new attributes are added to this service's existing set of attributes.
     * 
     * @see FiddlerImpl.LocalLogHandler
     */
    private static class LookupAttrsAddedLogObj implements LogRecord {
        private static final long serialVersionUID = 4983778026976938585L;
        /** The attributes that were added to each lookup service with which
         *  this service is registered, written out in marshalled form.
         *  @serial
         */
        private MarshalledObject[] marshalledAttrs;
        /** Constructs this class and stores the attributes that were added */
        public LookupAttrsAddedLogObj(FiddlerImpl fiddlerImpl, Entry[] attrs) {
            this.marshalledAttrs = marshalAttributes(fiddlerImpl,attrs);
        }
        /** Modifies this service's state by adding (after unmarshalling) the
         *  elements of marshalledAttrs to the service's existing set of
         *  attributes.
         *  @see FiddlerImpl.LocalLogHandler#applyUpdate
         */
        public void apply(FiddlerImpl fiddlerImpl) {
            logInfoPersist("Log recovery: apply adding lookup attributes");
            Entry[] attrs = unmarshalAttributes(fiddlerImpl, marshalledAttrs);
            fiddlerImpl.thisServicesAttrs 
                  = LookupAttributes.add(fiddlerImpl.thisServicesAttrs,attrs);
        }
    }//end LookupAttrsAddedLogObj

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the attributes currently associated with this service in the lookup
     * services with which it is registered are modified.
     * 
     * @see FiddlerImpl.LocalLogHandler
     */
    private static class LookupAttrsModifiedLogObj implements LogRecord {
        private static final long serialVersionUID = 2671139518188470469L;
	/** The attribute set templates used to select the attributes (from
         *  the service's existing set of attributes) that were modified,
         *  written out in marshalled form.
	 *  @serial
	 */
        private MarshalledObject[] marshalledAttrTmpls;
        /** The attributes with which this service's existing attributes 
         *  were modified, written out in marshalled form.
         *  @serial
         */
        private MarshalledObject[] marshalledModAttrs;
        /** Constructs this class and stores the modified attributes */
        public LookupAttrsModifiedLogObj(FiddlerImpl fiddlerImpl,
                                         Entry[] attrTmpls,
                                         Entry[] modAttrs)
        {
            this.marshalledAttrTmpls = marshalAttributes
                                                       (fiddlerImpl,attrTmpls);
            this.marshalledModAttrs = marshalAttributes(fiddlerImpl,modAttrs);
        }
        /** Modifies this service's state by modifying (after unmarshalling)
         *  the service's existing attributes according to the contents of
         *  marshalledAttrTmpls and marshalledModAttrs.
         *  @see FiddlerImpl.LocalLogHandler#applyUpdate
         */
        public void apply(FiddlerImpl fiddlerImpl) {
            logInfoPersist("Log recovery: apply modifying lookup attributes");
            Entry[] attrTmpls = unmarshalAttributes
                                            (fiddlerImpl, marshalledAttrTmpls);
            Entry[] modAttrs  = unmarshalAttributes
                                            (fiddlerImpl, marshalledModAttrs);
            fiddlerImpl.thisServicesAttrs 
                       = LookupAttributes.modify(fiddlerImpl.thisServicesAttrs,
                                                 attrTmpls, modAttrs);
        }
    }//end LookupAttrsModifiedLogObj

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the set containing the names of the groups whose members are lookup
     * services the lookup discovery service wishes to register with (join)
     * is modified in some way; for example, through the invocation of:
     * <code>JoinAdmin.addLookupGroups</code>,
     * <code>JoinAdmin.removeLookupGroups</code> or
     * <code>JoinAdmin.setLookupGroups</code>.
     * 
     * @see FiddlerImpl.LocalLogHandler
     */
    private static class LookupGroupsChangedLogObj implements LogRecord {
        private static final long serialVersionUID = -6973676200404539919L;
        /** The new groups whose members are the lookup services this
         *  service should join.
         *  @serial
         */
        private String[] groups;
	/** Constructs this class and stores the new groups */
        public LookupGroupsChangedLogObj(String[] groups) {
            this.groups = groups;
        }
        /** Modifies this service's state by modifying this service's existing
         *  set of 'groups to join'.
         *  @see FiddlerImpl.LocalLogHandler#applyUpdate
         */
        public void apply(FiddlerImpl fiddlerImpl) {
            logInfoPersist
                       ("Log recovery: apply changing lookup groups to join");
            fiddlerImpl.thisServicesGroups = groups; 
        }
    }//end LookupGroupsChangedLogObj

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the set containing the instances of <code>LookupLocator</code> that
     * correspond to specific lookup services the lookup discovery service
     * wishes to register with (join) is modified in some way; for example,
     * through the invocation of:
     * <code>JoinAdmin.addLookupLocators</code>,
     * <code>JoinAdmin.removeLookupLocators</code> or
     * <code>JoinAdmin.setLookupLocators</code>.
     *
     *  @see FiddlerImpl.LocalLogHandler
     */
    private static class LookupLocatorsChangedLogObj implements LogRecord {
        private static final long serialVersionUID = 6448427261140043291L;
        /** The locators that correspond to the new lookup services this
         *  service should join.
         *  @serial
         */
        private LookupLocator[] locators;
        /** Constructs this class and stores the new locators */
        public LookupLocatorsChangedLogObj(LookupLocator[] locators) {
            this.locators = locators;
        }
        /** Modifies this service's state by modifying this service's existing
         *  set of 'locators to join'.
         *  @see FiddlerImpl.LocalLogHandler#applyUpdate
         */
        public void apply(FiddlerImpl fiddlerImpl) {
            logInfoPersist
                     ("Log recovery: apply changing lookup locators to join");
            fiddlerImpl.thisServicesLocators =
                  prepareOldLocators(recoveredLocatorToJoinPreparer,locators);
        }
    }//end LookupLocatorsChangedLogObj

    /* 2. Definitions related to state changes generated by FiddlerAdmin */

    /**
     * LogObj class whose instances are recorded to the log file whenever a
     * new value is assigned to the least upper bound applied during the
     * determination of the duration of the lease on a requested registration.
     * 
     * @see FiddlerImpl.LocalLogHandler
     */
    private static class LeaseBoundSetLogObj implements LogRecord {
        private static final long serialVersionUID = 6084059678114714281L;
        /** The new least upper bound used to determine a lease's duration.
         *  @serial
         */
        private long newLeaseBound;
        /** Constructs this class and stores the new lease duration bound */
        public LeaseBoundSetLogObj(long newLeaseBound) {
            this.newLeaseBound = newLeaseBound;
        }
        /** Modifies this service's state by setting the value of the private
         *  leaseBound field to the value of the new bound.
         *  @see FiddlerImpl.LocalLogHandler#applyUpdate
         */
        public void apply(FiddlerImpl fiddlerImpl) {
            logInfoPersist
                         ("Log recovery: apply changing lease duration bound");
            fiddlerImpl.leaseBound = newLeaseBound; 
        }
    }//end LeaseBoundSetLogObj

    /**
     * LogObj class whose instances are recorded to the log file whenever a
     * new value is assigned to the Weight Factor applied to the Snapshot File
     * size used in the "time-to-take-a-snapshot determination" expression.
     * 
     * @see FiddlerImpl.LocalLogHandler
     */
    private static class SnapshotWeightSetLogObj implements LogRecord {
        private static final long serialVersionUID = -4079318959709953285L;
        /** The new snapshot weight factor.
         * @serial
         */
        private float newWeight;
	/** Constructs this class and stores the new weight factor */
        public SnapshotWeightSetLogObj(float newWeight) {
            this.newWeight = newWeight;
        }
        /** Modifies this service's state by setting the value of the private
         *  snapshotWt field to the value of the new weight factor.
         *  @see FiddlerImpl.LocalLogHandler#applyUpdate
         */
        public void apply(FiddlerImpl fiddlerImpl) {
            logInfoPersist
                      ("Log recovery: apply changing snapshot weight factor");
            fiddlerImpl.snapshotWt = newWeight; 
        }
    }//end SnapshotWeightSetLogObj

    /**
     * LogObj class whose instances are recorded to the log file 
     * whenever a new value is assigned to the Threshold used in the
     * "time-to-take-a-snapshot determination" expression.
     * 
     * @see FiddlerImpl.LocalLogHandler
     */
    private static class SnapshotThresholdSetLogObj implements LogRecord {
        private static final long serialVersionUID = 2952948867001823559L;
        /** The new snapshot threshold.
         *  @serial
         */
        private int newThreshold;
        /** Constructs this class and stores the new snapshot threshold */
        public SnapshotThresholdSetLogObj(int newThreshold) {
            this.newThreshold = newThreshold;
        }
        /** Modifies this service's state by setting the value of the private
         *  snapshotThresh field to the value of the new threshold.
         *  @see FiddlerImpl.LocalLogHandler#applyUpdate
         */
        public void apply(FiddlerImpl fiddlerImpl) {
            logInfoPersist
                    ("Log recovery: apply changing log-to-snapshot threshold");
            fiddlerImpl.snapshotThresh = newThreshold; 
        }
    }//end SnapshotThresholdSetLogObj

    /* 3. Definitions related to state changes made to the set of active
     *    registrations, generated by client requests made through the
     *    <code>LookupDiscoveryRegistration</code> interface.
     */
    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a registration is created and returned to a client.
     * 
     * @see FiddlerImpl.LocalLogHandler
     */
    private static class RegistrationGrantedLogObj implements LogRecord {
        private static final long serialVersionUID = 3983963008572188308L;
        /** Object which acts as a record of the current registration with the
         *  lookup discovery service; containing all of the information about
         *  that registration: IDs, managed sets, lease information, and
         *  event registration information.
         *  @serial
         */
        private RegistrationInfo regInfo;
        /** Constructs this class and stores the registration information */
        public RegistrationGrantedLogObj(RegistrationInfo regInfo) {
            this.regInfo = regInfo;
        }
        /** Modifies this service's state by registering the information
         *  stored in the regInfo parameter; and by updating both the event
         *  sequence number and the event ID for the registration.
         *
         *  Note that the granting of a registration to a client typically
         *  involves the modification of the managed sets in the discovery
         *  manager, which usually involves starting the discovery protocol.
         *  Since an IOException can occur when the discovery protocol fails
         *  to start, and since such a situation is un-recoverable, this
         *  method does the following: catches the exception, informs this
         *  service's administrator by displaying the stack trace, and exits.
         *  @see FiddlerImpl.LocalLogHandler#applyUpdate
         */
        public void apply(FiddlerImpl fiddlerImpl) {
            logInfoPersist("Log recovery: apply recording granted registration"
                           +" info\n              (ID = "
                           +regInfo.registrationID+")");
            regInfo.seqNum += Integer.MAX_VALUE;
            try {
                /* Note that the locators of this recovered registration
                 * were already successfully prepared in the method
                 * RegistrationInfo.readObject() when this object was
                 * deserialized during recovery.
                 */
                fiddlerImpl.addRegistration(regInfo);
            } catch(IOException e) {
                if( problemLogger.isLoggable(Level.SEVERE) ) {
                    problemLogger.log(Level.SEVERE, "During log recovery "
                                      +"(apply addRegistration) -- failure in "
                                      +"multicast request protocol\n", e);
                }//endif
                fiddlerImpl.destroyDo();
            }
            fiddlerImpl.curEventID++;
        }
    }//end RegistrationGrantedLogObj

    /* 4. Definitions related to state changes made to the set of active
     *    registrations, generated by client requests made through the
     *    <code>LookupDiscoveryService</code> interface.
     */

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the managed set of groups corresponding to a registration is 
     * is augmented with new elements.
     * 
     * @see FiddlerImpl.LocalLogHandler
     */
    private static class GroupsAddedToRegistrationLogObj implements LogRecord {
        private static final long serialVersionUID = 2L;
        /** The ID of the data structure record corresponding to the
         *  registration whose managed set of groups was augmented.
         *  @serial
         */
        private Uuid registrationID;
        /** The set of groups added to the registration's managed set of groups
         *  @serial
         */
        private String[] groups;
        /** Constructs this class and stores the ID and new set of groups */
        public GroupsAddedToRegistrationLogObj(Uuid registrationID,
                                               String[] groups)
        {
            this.registrationID = registrationID;
            this.groups         = groups;
        }
        /** Modifies this service's state by adding the set of group names to
         *  registration's managed set of groups, as well as by updating the
         *  set of all groups (across all registrations) to discover.
         *
         *  Note that the augmentation of a registration's set of groups
         *  typically involves the modification of the managed sets in the
         *  discovery manager, which usually involves starting the discovery
         *  protocol. Since an IOException can occur when the discovery
         *  protocol fails to start, and since such a situation is
         *  un-recoverable, this method does the following: catches the
         *  exception, informs this service's administrator by displaying
         *  the stack trace, and exits.
         *  @see FiddlerImpl.LocalLogHandler#applyUpdate
         */
        public void apply(FiddlerImpl fiddlerImpl) {
            logInfoPersist("Log recovery: apply adding to groups to discover "
                           +"for registration\n              (ID = "
                           +registrationID+")");
            try {
                fiddlerImpl.addGroupsDo(registrationID,
                                        fiddlerImpl.registrationByID,
                                        groups);
            } catch(Exception e) {
                if( problemLogger.isLoggable(Level.SEVERE) ) {
                    problemLogger.log(Level.SEVERE, "During log recovery "
                                      +"(apply addGroupsDO) -- failure in "
                                      +"multicast request protocol\n", e);
                }//endif
                fiddlerImpl.destroyDo();
            }
        }//end apply
    }//end GroupsAddedToRegistrationLogObj

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a the managed set of groups corresponding to a registration is 
     * is replaced (set) with a new set of group names.
     * 
     * @see FiddlerImpl.LocalLogHandler
     */
    private static class GroupsSetInRegistrationLogObj implements LogRecord {
        static final long serialVersionUID = 2L;
        /** The ID of the data structure record corresponding to the
         *  registration whose managed set of groups was replaced.
         *  @serial
         */
        private Uuid registrationID;
        /** The set of groups that replaced the registration's current
         *  managed set of groups.
         *  @serial
         */
        private String[] groups;
        /** Constructs this class and stores the ID and new set of groups */
        public GroupsSetInRegistrationLogObj(Uuid registrationID,
                                             String[] groups)
        {
            this.registrationID = registrationID;
            this.groups         = groups;
        }
        /** Modifies this service's state by replacing the registration's
         *  current managed set of groups with the set of group names 
         *  stored in this class by the constructor, as well as by updating
         *  the set of all groups (across all registrations) to discover.
         *
         *  Note that the replacement of a registration's set of groups
         *  typically involves the modification of the managed sets in the
         *  discovery manager, which usually involves starting the discovery
         *  protocol. Since an IOException can occur when the discovery
         *  protocol fails to start, and since such a situation is
         *  un-recoverable, this method does the following: catches the
         *  exception, informs this service's administrator by displaying
         *  the stack trace, and exits.
         *  @see FiddlerImpl.LocalLogHandler#applyUpdate
         */
        public void apply(FiddlerImpl fiddlerImpl) {
            logInfoPersist("Log recovery: apply replacing groups to discover "
                           +"for registration\n              (ID = "
                           +registrationID+") ...");
            try {
                fiddlerImpl.setGroupsDo(registrationID,
                                        fiddlerImpl.registrationByID,
                                        groups);
            } catch(Exception e) {
                if( problemLogger.isLoggable(Level.SEVERE) ) {
                    problemLogger.log(Level.SEVERE, "Failure during log "
                                      +"recovery (apply setGroups) -- \n", e);
                }//endif
                fiddlerImpl.destroyDo();
            }
        }
    }//end GroupsSetInRegistrationLogObj

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * one or more elements of the managed set of groups corresponding to a
     * registration are removed.
     * 
     * @see FiddlerImpl.LocalLogHandler
     */
    private static class GroupsRemovedFromRegistrationLogObj
                                                        implements LogRecord
    {
        private static final long serialVersionUID = 2L;
        /** The ID of the data structure record corresponding to the
         *  registration with managed set from which groups were removed.
         *  @serial
         */
        private Uuid registrationID;
        /** The set of groups removed from the registration's managed set 
         *  of groups.
         *  @serial
         */
        private String[] groups;
        /** Constructs this class and stores the ID and groups to remove */
        public GroupsRemovedFromRegistrationLogObj(Uuid registrationID,
                                                   String[] groups)
        {
            this.registrationID = registrationID;
            this.groups         = groups;
        }
        /** Modifies this service's state by removing the set of group names
         *  from registration's managed set of groups, as well as by updating
         *  the set of all groups (across all registrations) to discover.
         *
         *  Note that the removal of one or more group names from a
         *  registration's set of groups typically involves the modification 
         *  of the managed sets in the discovery manager, which usually 
         *  involves starting the discovery protocol. Since an IOException 
         *  can occur when the discovery protocol fails to start, and since 
         *  such a situation is un-recoverable, this method does the 
         *  following: catches the exception, informs this service's 
         *  administrator by displaying the stack trace, and exits.
         *  @see FiddlerImpl.LocalLogHandler#applyUpdate
         */
        public void apply(FiddlerImpl fiddlerImpl) {
            logInfoPersist("Log recovery: apply removing from groups to "
                           +"discover for registration\n              (ID = "
                           +registrationID+")");
            try {
                fiddlerImpl.removeGroupsDo(registrationID,
                                           fiddlerImpl.registrationByID,
                                           groups);
	    } catch(Exception e) {
                if( problemLogger.isLoggable(Level.SEVERE) ) {
                    problemLogger.log(Level.SEVERE, "Failure during log "
                                    +"recovery (apply removeGroups) -- \n", e);
                }//endif
	        fiddlerImpl.destroyDo();
	    }
        }
    }//end GroupsRemovedFromRegistrationLogObj

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the managed set of locators corresponding to a registration is 
     * is augmented with new elements.
     * 
     * @see FiddlerImpl.LocalLogHandler
     */
    private static class LocsAddedToRegistrationLogObj implements LogRecord {
        private static final long serialVersionUID = 2L;
        /** The ID of the data structure record corresponding to the
         *  registration whose managed set of locators was augmented.
         *  @serial
         */
        private Uuid registrationID;
        /** The set of locators added to the registration's managed set
         *  @serial
         */
        private LookupLocator[] locators;
        /** Constructs this class and stores the ID and new set of locators */
        public LocsAddedToRegistrationLogObj(Uuid registrationID,
                                             LookupLocator[] locators)
        {
            this.registrationID = registrationID;
            this.locators       = locators;
        }
        /** Modifies this service's state by adding the set of locators to
         *  registration's managed set of locators, as well as by updating the
         *  set of all locators (across all registrations) to discover.
         *  @see FiddlerImpl.LocalLogHandler#applyUpdate
         */
        public void apply(FiddlerImpl fiddlerImpl) {
            logInfoPersist("Log recovery: apply adding to locators to discover"
                           +" for registration\n              (ID = "
                           +registrationID+")");
            int nUnprepared = locators.length;
            /* Prepare the recovered locators */
            locators = 
              prepareOldLocators(recoveredLocatorToDiscoverPreparer, locators);
            /* If all the locs were successfully prepared, add them to the
             * associated registration; otherwise, remove the registration
             * from the managed set. (For more information, see the comment
             * in RegistrationInfo.readObject()).
             */
            if(nUnprepared == locators.length) {
                fiddlerImpl.addLocatorsDo( registrationID,
                                           fiddlerImpl.registrationByID,
                                           locators );
            } else {
                if( problemLogger.isLoggable(Level.WARNING) ) {
                    problemLogger.log(Level.WARNING, "failure preparing "
                                      +"locator while recovering "
                                      +"LocsAddedToRegistrationLogObj "
                                      +"... removing registration with ID = "
                                      +registrationID);
                }//endif
                try {
                    fiddlerImpl.removeRegistration
                       ( (RegistrationInfo)(fiddlerImpl.registrationByID.get
                                                           (registrationID)) );
                } catch(IOException e) {
                    String eStr = "failure removing registration (ID = "
                                  +registrationID
                                  +") after locator preparation failure";
                    if( problemLogger.isLoggable(Level.INFO) ) {
                        problemLogger.log(Level.INFO, eStr, e);
                    }//endif
                    Entry[] errorAttrs
                            = new Entry[]
                                        { new FiddlerStatus(StatusType.ERROR),
                                          new Comment(eStr)
                                        };
                    fiddlerImpl.joinMgr.addAttributes(errorAttrs,true);
                }
            }//endif
        }//end apply
    }//end LocsAddedToRegistrationLogObj

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the managed set of locators corresponding to a registration is 
     * is replaced (set) with a new set of locators.
     * 
     * @see FiddlerImpl.LocalLogHandler
     */
    private static class LocsSetInRegistrationLogObj implements LogRecord {
        private static final long serialVersionUID = 2L;
        /** The ID of the data structure record corresponding to the
         *  registration whose managed set of locators was replaced.
         *  @serial
         */
        private Uuid registrationID;
        /** The set of locators that replaced the registration's current
         *  managed set of locators.
         *  @serial
         */
        private LookupLocator[] locators;
        /** Constructs this class and stores the ID and new set of locators */
        public LocsSetInRegistrationLogObj(Uuid registrationID,
                                           LookupLocator[] locators)
        {
            this.registrationID = registrationID;
            this.locators       = locators;
        }
        /** Modifies this service's state by replacing the registration's
         *  current managed set of locators with the set of locators 
         *  stored in this class by the constructor, as well as by updating
         *  the set of all locators (across all registrations) to discover.
         *  @see FiddlerImpl.LocalLogHandler#applyUpdate
         */
        public void apply(FiddlerImpl fiddlerImpl) {
            logInfoPersist("Log recovery: apply replacing locators to discover"
                           +" for registration\n              (ID = "
                           +registrationID+")");
            int nUnprepared = locators.length;
            /* Prepare the recovered locators */
            locators = 
              prepareOldLocators(recoveredLocatorToDiscoverPreparer, locators);
            /* If all the locs were successfully prepared, set them in the
             * associated registration; otherwise, remove the registration
             * from the managed set. (For more information, see the comment
             * in RegistrationInfo.readObject()).
             */
            if(nUnprepared == locators.length) {
                fiddlerImpl.setLocatorsDo( registrationID,
                                           fiddlerImpl.registrationByID,
                                           locators );
            } else {
                if( problemLogger.isLoggable(Level.WARNING) ) {
                    problemLogger.log(Level.WARNING,
                                      "failure preparing locator while "
                                      +"recovering LocsSetInRegistrationLogObj"
                                      +" ... removing registration with ID = "
                                      +registrationID);
                }//endif
                try {
                    fiddlerImpl.removeRegistration
                       ( (RegistrationInfo)(fiddlerImpl.registrationByID.get
                                                           (registrationID)) );
                } catch(IOException e) {
                    String eStr = "failure removing registration (ID = "
                                  +registrationID
                                  +") after locator preparation failure";
                    if( problemLogger.isLoggable(Level.WARNING) ) {
                        problemLogger.log(Level.WARNING, eStr, e);
                    }//endif
                    Entry[] errorAttrs
                            = new Entry[]
                                        { new FiddlerStatus(StatusType.ERROR),
                                          new Comment(eStr)
                                        };
                    fiddlerImpl.joinMgr.addAttributes(errorAttrs,true);
                }
            }//endif
        }
    }//end LocsSetInRegistrationLogObj

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * one or more elements of the managed set of locators corresponding to a
     * registration are removed.
     * 
     * @see FiddlerImpl.LocalLogHandler
     */
    private static class LocsRemovedFromRegistrationLogObj
                                                         implements LogRecord 
    {
        private static final long serialVersionUID = 2L;
        /** The ID of the data structure record corresponding to the
         *  registration with managed set from which locators were removed.
         *  @serial
         */
        private Uuid registrationID;
        /** The set of locators removed from the registration's managed set 
         *  of locators.
         *  @serial
         */
        private LookupLocator[] locators;
        /** Constructs this class and stores the ID and locators to remove */
        public LocsRemovedFromRegistrationLogObj(Uuid registrationID,
                                                 LookupLocator[] locators)
        {
            this.registrationID = registrationID;
            this.locators       = locators;
        }
        /** Modifies this service's state by removing the set of locators
         *  from registration's managed set of locators, as well as by updating
         *  the set of all locators (across all registrations) to discover.
         *  @see FiddlerImpl.LocalLogHandler#applyUpdate
         */
        public void apply(FiddlerImpl fiddlerImpl) {
            logInfoPersist("Log recovery: apply removing from locators to "
                           +"discover for registration\n              (ID = "
                           +registrationID+") ...");
            int nUnprepared = locators.length;
            /* Prepare the recovered locators */
            locators = 
              prepareOldLocators(recoveredLocatorToDiscoverPreparer, locators);
            /* If all the locs were successfully prepared, remove them from
             * the associated registration; otherwise, remove the registration
             * from the managed set. (For more information, see the comment
             * in RegistrationInfo.readObject()).
             */
            if(nUnprepared == locators.length) {
                fiddlerImpl.removeLocatorsDo( registrationID,
                                              fiddlerImpl.registrationByID,
                                              locators );
            } else {
                if( problemLogger.isLoggable(Level.WARNING) ) {
                    problemLogger.log(Level.WARNING, "failure preparing "
                                      +"locator while recovering"
                                      +"LocsRemovedFromRegistrationLogObj "
                                      +"... removing registration with ID = "
                                      +registrationID);
                }//endif
                try {
                    fiddlerImpl.removeRegistration
                       ( (RegistrationInfo)(fiddlerImpl.registrationByID.get
                                                           (registrationID)) );
                } catch(IOException e) {
                    String eStr = "failure removing registration (ID = "
                                  +registrationID
                                  +") after locator preparation failure";
                    if( problemLogger.isLoggable(Level.WARNING) ) {
                        problemLogger.log(Level.WARNING, eStr, e);
                    }//endif
                    Entry[] errorAttrs
                            = new Entry[]
                                        { new FiddlerStatus(StatusType.ERROR),
                                          new Comment(eStr)
                                        };
                    fiddlerImpl.joinMgr.addAttributes(errorAttrs,true);
                }
            }//endif
        }
    }//end LocsRemovedFromRegistrationLogObj

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a lease on an existing registration (granted by the current backend
     * server of the lookup discovery service -- the lease grantor) is renewed.
     * 
     * @see FiddlerImpl.LocalLogHandler
     */
    private static class LeaseRenewedLogObj implements LogRecord {
        private static final long serialVersionUID = 2L;
        /** The ID of the data structure record corresponding to the
         *  registration whose lease was renewed.
         *  @serial
         */
        private Uuid registrationID;
        /** The identifier assigned by the lease grantor to the lease that was
         *  renewed.
         *  @serial
         */
        private Uuid leaseID;
        /** The new absolute time of expiration of the lease that was renewed.
         *  @serial
         */
        private long expiration;
        /** Constructs this class and stores the IDs and the expiration time */
        public LeaseRenewedLogObj(Uuid registrationID,
                                  Uuid leaseID,
                                  long expiration)
        {
            this.registrationID = registrationID;
            this.leaseID        = leaseID;
            this.expiration     = expiration;
        }
        /** Modifies this service's state by renewing the lease with ID equal
         *  to this class' leaseID field, and which corresponds to the 
         *  regInfo record. The lease will be renewed to have a new expiration
         *  time equal to the value of the expiration field.
         *  @see FiddlerImpl.LocalLogHandler#applyUpdate
         */
        public void apply(FiddlerImpl fiddlerImpl) {
            logInfoPersist("Log recovery: apply renewing lease for "
                           +"registration\n              (ID = "
                           +registrationID+")");
            fiddlerImpl.renewLeaseAbs(registrationID, 
                                      fiddlerImpl.registrationByID,
                                      leaseID, expiration);
        }
    }//end LeaseRenewedLogObj

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a set of leases from a <code>LeaseMap</code> are renewed.
     * 
     * @see FiddlerImpl.LocalLogHandler
     */
    private static class LeasesRenewedLogObj implements LogRecord {
        private static final long serialVersionUID = 2L;
        /** The set of unique identifiers each assigned to a registration that
         *  corresponds to one of the leases that was renewed.
         *  @serial
         */
        private Uuid[] registrationIDs;
        /** The set of identifiers each assigned by the lease grantor to one
         *  of the leases that was renewed.
         *  @serial
         */
        private Uuid[] leaseIDs;
        /** The set of new absolute expiration times of each lease that was
         *  renewed.
         *  @serial
         */
        private long[] expirations;
        /** Constructs this class and stores the sets of IDs and the set of
         *  expiration times.
         */
        public LeasesRenewedLogObj(Uuid[] registrationIDs,
                                   Uuid[] leaseIDs,
                                   long[] expirations)
        {
            this.registrationIDs = registrationIDs;
            this.leaseIDs        = leaseIDs;
            this.expirations     = expirations;
        }
        /** Modifies this service's state by renewing, with the corresponding
         *  expiration time, each of the leases specified by the stored IDs.
         *  @see FiddlerImpl.LocalLogHandler#applyUpdate
         */
        public void apply(FiddlerImpl fiddlerImpl) {
            logInfoPersist("Log recovery: apply renewing leases corresponding "
                           +"to "+registrationIDs.length
                           +" registration IDs");
            fiddlerImpl.renewLeasesAbs(registrationIDs, leaseIDs, expirations);
        }
    }//end LeasesRenewedLogObj

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a lease on an existing registration (granted by the current backend
     * server of the lookup discovery service -- the lease grantor) is
     * cancelled.
     * 
     * @see FiddlerImpl.LocalLogHandler
     */
    private static class LeaseCancelledLogObj implements LogRecord {
        private static final long serialVersionUID = 2L;
        /** The ID of the data structure record corresponding to the
         *  registration whose lease was cancelled.
         *  @serial
         */
        private Uuid registrationID;
        /** The identifier assigned by the lease grantor to the lease that was
         *  cancelled.
         *  @serial
         */
        private Uuid leaseID;
        /** Constructs this class and stores the IDs corresponding to the
         *  lease that was cancelled.
         */
        public LeaseCancelledLogObj(Uuid registrationID,
                                    Uuid leaseID) {
            this.registrationID = registrationID;
            this.leaseID        = leaseID;
        }
        /** Modifies this service's state by canceling the lease on the
         *  registration with ID equal to that stored by the constructor,
         *  as well as by updating the managed set of groups and locators 
         *  (across all registrations) in the appropriate way.
         *
         *  Note that the cancellation of a lease typically involves the
         *  modification of the managed sets in the discovery manager, which
         *  usually involves starting the discovery protocol. Since an
         *  IOException can occur when the discovery protocol fails to start,
         *  and since such a situation is un-recoverable, this method does
         *  the following: catches the exception, informs this service's
         *  administrator by displaying the stack trace, and exits.
         *  @see FiddlerImpl.LocalLogHandler#applyUpdate
         */
        public void apply(FiddlerImpl fiddlerImpl) {
            try {
                logInfoPersist("Log recovery: apply cancelling lease for "
                               +"registration\n              (ID = "
                               +registrationID+")");
                fiddlerImpl.cancelLeaseDo(registrationID, 
                                          fiddlerImpl.registrationByID,
                                          leaseID);
            } catch(IOException e) {
                if( problemLogger.isLoggable(Level.SEVERE) ) {
                    problemLogger.log(Level.SEVERE, "During log recovery "
                                      +"(apply cancelLease) -- failure in "
                                      +"multicast request protocol\n", e);
                }//endif
                fiddlerImpl.destroyDo();
            } catch (UnknownLeaseException e) {
                /* this exception should never occur when recovering */
            }
        }
    }//end LeaseCancelledLogObj

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a set of leases from a <code>LeaseMap</code> are cancelled.
     * 
     * @see FiddlerImpl.LocalLogHandler
     */
    private static class LeasesCancelledLogObj implements LogRecord {
        private static final long serialVersionUID = 2L;
        /** The set of unique identifiers each assigned to a registration that
         *  corresponds to one of the leases that was cancelled.
         *  @serial
         */
        private Uuid[] registrationIDs;
        /** The set of identifiers each assigned by the lease grantor to one
         *  of the leases that was cancelled.
         *  @serial
         */
        private Uuid[] leaseIDs;
        /** Constructs this class and stores the IDs corresponding to the
         *  leases that were cancelled.
         */
        public LeasesCancelledLogObj(Uuid[] registrationIDs,
                                     Uuid[] leaseIDs)
        {
            this.registrationIDs = registrationIDs;
            this.leaseIDs        = leaseIDs;
        }
        /** Modifies this service's state by canceling each of the leases
         *  specified by the stored IDs.
         *  @see FiddlerImpl.LocalLogHandler#applyUpdate
         */
        public void apply(FiddlerImpl fiddlerImpl) {
            logInfoPersist("Log recovery: apply cancelling leases "
                           +"corresponding to "+registrationIDs.length
                           +" registration IDs");
            /* Because unknown leases were not weeded out, exceptions can
             * occur and be propagated upward, but they can be ignored.
             */
            fiddlerImpl.cancelLeasesDo(registrationIDs, leaseIDs);
        }
    }//end LeasesCancelledLogObj
    /* END Persistent State Logging Interfaces & Classes ------------------- */

    /* BEGIN Private Logging Facility Methods ------------------------------ */
    /* Returns a String containing the elements of the array */
    private static String writeArrayElementsToString(Object[] arr) {
        if(arr == null) return new String("[]");
        if(arr.length <= 0) {
            return new String("[]");
        }//endif
        StringBuffer strBuf = new StringBuffer("["+arr[0]);
        for(int i=1;i<arr.length;i++){
            strBuf.append(", ").append(arr[i]);
        }//end loop
        strBuf.append("]");
        return strBuf.toString();
    }//end writeArrayElementsToString

    /* Logs the elements of the array to a single line of output */
    private static void writeArrayElements(Object[] arr,
                                           Logger   logger,
                                           Level    level)
    {
        if((arr == null) || (logger == null) || !(logger.isLoggable(level))) {
            return;
        }
        String writeStr = writeArrayElementsToString(arr);
        logger.log(level,writeStr);
    }//end writeArrayElements

    /* Returns a String containing the group names in writable form */
    private static String writeGroupArrayToString(String[] groups) {
        if(groups == null) {
            return new String("[ALL_GROUPS]");
        }//endif
        if(groups.length <= 0) {
            return new String("[]");
        }//endif
        StringBuffer strBuf = null;
        if(groups[0].compareTo("") == 0) {
            strBuf = new StringBuffer("[The PUBLIC Group");
        } else {
            strBuf = new StringBuffer("["+groups[0]);
        }//endif
        for(int i=1;i<groups.length;i++) {
            if(groups[i].compareTo("") == 0) {
                strBuf.append(", The PUBLIC Group");
            } else {
                strBuf.append(", ").append(groups[i]);
            }//endif
        }//end loop
        strBuf.append("]");
        return strBuf.toString();
    }//end writeGroupArrayToString

    /* Logs the group names to a single line of output */
    private static void writeGroupArray(String[] groups,
                                        Logger   logger,
                                        Level    level)
    {
        if( (logger == null) || !(logger.isLoggable(level)) ) return;
        String writeStr = writeGroupArrayToString(groups);
        logger.log(level,writeStr);
    }//end writeGroupArray

    /* Logs each registrar's locator on a single line of output */
    private static void writeRegistrarsArray(ServiceRegistrar[] regs,
                                             Logger             logger,
                                             Level              level)
    {
        if((regs == null) || (logger == null) || !(logger.isLoggable(level))) {
            return;
        }
        if(regs.length == 0) {
            logger.log(level, "[NO REGISTRARS for Event]");
        }//endif
        if(regs.length == 1) {
            try{
                LookupLocator loc = regs[0].getLocator();
                logger.log(level, "["+loc+"]");
            }catch(SecurityException e){
                logger.log(level, "[SecurityException]");
            }catch(Exception e){
                logger.log(level, "[Exception]");
            }
        }//endif(regs.length == 1)
        if(regs.length > 1) {
            try{
                LookupLocator loc = regs[0].getLocator();
                logger.log(level, "["+loc+",");
            }catch(SecurityException e){
                logger.log(level, "[SecurityException,");
            }catch(Exception e){
                logger.log(level, "[Exception,");
            }
            for(int i=1;i<(regs.length-1);i++){
                try{
                    LookupLocator loc = regs[i].getLocator();
                    logger.log(level, loc+",");
                }catch(SecurityException e){
                    logger.log(level, "SecurityException,");
                }catch(Exception e){
                    logger.log(level, "Exception,");
                }
            }//end loop
            try{
                LookupLocator loc = regs[regs.length-1].getLocator();
                logger.log(level, loc+"]");
            }catch(SecurityException e){
                logger.log(level, "SecurityException]");
            }catch(Exception e){
                logger.log(level, "Exception]");
            }
        }//endif(regs.length > 1)
    }//end writeRegistrarsArray

    /* Logs data on a single attribute to a file or standard output */
    private static void writeAttribute(Entry  attr,
                                       Logger logger,
                                       Level  level)
    {
        if((attr == null) || (logger == null) || !(logger.isLoggable(level))) {
            return;
        }
        if(attr instanceof BasicServiceType) {
            logger.log(level, "  attribute = BasicServiceType");
            logger.log(level, "    Display Name = "
                               +((BasicServiceType)(attr)).getDisplayName());
            logger.log(level, "    Description  = "
                          +((BasicServiceType)(attr)).getShortDescription());
        } else if(attr instanceof ServiceInfo) {
            logger.log(level, "  attribute = ServiceInfo");
            logger.log(level, "    Service Name         = "
                                       +((ServiceInfo)(attr)).name);
            logger.log(level, "    Service Manufacturer = "
                                      +((ServiceInfo)(attr)).manufacturer);
            logger.log(level, "    Service Vendor       = "
                                       +((ServiceInfo)(attr)).vendor);
            logger.log(level, "    Service Version      = "
                                       +((ServiceInfo)(attr)).version);
            logger.log(level, "    Service Model        = "
                                       +((ServiceInfo)(attr)).model);
            logger.log(level, "    Service Serial #     = "
                                      +((ServiceInfo)(attr)).serialNumber);
        } else {
            logger.log(level, "  attribute = "+attr);
        }//endif
    }//end writeAttribute

    /* Logs data on each attribute in a set to a file or standard output */
    private static void writeAttributes(Entry[] attrs,
                                        Logger  logger,
                                        Level   level)
    {
        if((attrs == null) || (logger == null) || !(logger.isLoggable(level))){
            return;
        }
        for(int i=0;i<attrs.length;i++) {
            if(attrs[i] == null) continue;
            writeAttribute(attrs[i],logger,level);
        }//end loop
        logger.log(level,"");
    }//end writeAttributes

    /* Logs startup data to file or standard output */
    private void logInfoStartup() {
        if (startupLogger.isLoggable(Level.INFO)) {
            startupLogger.log
                   (Level.INFO, "Fiddler started: {0}, {1}, {2}",
                    new Object[]
                        { (FiddlerImpl.this.serviceID).toString(),
                          writeGroupArrayToString(thisServicesGroups),
                          writeArrayElementsToString(thisServicesLocators) } );
        }//endif
        if( startupLogger.isLoggable(Level.CONFIG) ) {
            if(persistDir != null) {
                startupLogger.log(Level.CONFIG,
                                  " Persistent state directory:  {0}",
                                  persistDir);
            }//endif
            startupLogger.log(Level.CONFIG,
                            "Attributes to register in each lookup service: ");
            writeAttributes(thisServicesAttrs,startupLogger,Level.CONFIG);
        }//endif
    }//end logInfoStartup

    /* Logs shutdown/destroy data to file or standard output */
    private void logInfoShutdown() {
        if (startupLogger.isLoggable(Level.INFO)) {
            startupLogger.log
                   (Level.INFO, "Fiddler destroyed: {0}, {1}, {2}",
                    new Object[]
                        { (FiddlerImpl.this.serviceID).toString(),
                          writeGroupArrayToString(thisServicesGroups),
                          writeArrayElementsToString(thisServicesLocators) } );
        }//endif
    }//end logInfoShutdown

    /* Logs information related to the run method of a particular task */
    private void logInfoTasks(String str) {
        if( tasksLogger.isLoggable(Level.FINEST) ) {
            tasksLogger.log(Level.FINEST, str);
        }//endif
    }//end logInfoTasks

    /* Logs information about events that are sent */
    private void logInfoEvents(String str) {
        if( eventsLogger.isLoggable(Level.FINE) ) {
            eventsLogger.log(Level.FINE, str);
        }//endif
    }//end logInfoEvents

    /* Logs information about events that are sent */
    private void logInfoEvents(Map  groupsMap,
                               long eventID,
                               long seqNum,
                               MarshalledObject handback,
                               boolean discarded,
                               Logger logger,
                               Level  level)
    {
        if( (logger == null) || !(logger.isLoggable(level)) ) {
            return;
        }
        String discardedStr = (discarded == true ? "DISCARDED":"DISCOVERED");
        Object hb = null;
        if(handback != null) {
            try {
                hb = handback.get();
            } catch (ClassNotFoundException e) {
                problemLogger.log(Levels.HANDLED,
                                  "ClassNotFoundException when "
                                  +"unmarshalling handback",e);
            } catch (IOException e) {
                problemLogger.log(Levels.HANDLED,
                                  "IOException when unmarshalling "
                                  +"handback",e);
            }
        }//endif
        logger.log(level, "\n"+discardedStr+" Event:");
        logger.log(level, "  EventID  = "+eventID);
        logger.log(level, "  SeqNum   = "+seqNum);
        logger.log(level, "  handback = "+hb);
        ServiceRegistrar[] regs =
                          (ServiceRegistrar[])(groupsMap.keySet()).toArray
                                     (new ServiceRegistrar[groupsMap.size()]);
        logger.log(level, "  Registrars = ");
        writeRegistrarsArray(regs,logger,level);
        for(int i=0;i<regs.length;i++){
            String[] curGroups = (String[])groupsMap.get(regs[i]);
            logger.log(level, "  member groups ["+i+"] = ");
            writeGroupArray(curGroups,logger,level);
        }//end loop
        logger.log(level, "");
    }//end logInfoEvents

    /* Logs group state information over all active registrations */
    private void logInfoGroups() {
        String[] allGroups = discoveryMgr.getGroups();
        if( groupsLogger.isLoggable(Level.FINER) ) {
            groupsLogger.log(Level.FINER, 
                             "Group(s) over all registrations: ");
            writeGroupArray(allGroups,groupsLogger,Level.FINER);
        }//endif
    }//end logInfoGroups

    /* Logs group state information over all active registrations */
    private void logInfoGroups(String headerStr) {
        if( (headerStr != null) && (groupsLogger.isLoggable(Level.FINER)) ) {
            groupsLogger.log(Level.FINER, headerStr);
        }//endif
        logInfoGroups();
    }//end logInfoGroups

    /* Logs locator state information over all active registrations */
    private void logInfoLocators() {
        LookupLocator[] allLocators = discoveryMgr.getLocators();
        if( locatorsLogger.isLoggable(Level.FINER) ) {
            locatorsLogger.log(Level.FINER, 
                               "Locator(s) over all registrations: ");
            writeArrayElements(allLocators,locatorsLogger,Level.FINER);
        }//endif
    }//end logInfoLocators

    /* Logs information useful to debugging the discard process */
    private void logInfoDiscard(String str, Uuid regID) {
        if(    (str != null) && (regID != null)
            && (discardLogger.isLoggable(Level.FINE)) )
        {
            discardLogger.log(Level.FINE, str+" registrationID = "+regID);
        }//endif
    }//end logInfoDiscard

    /* Logs information useful to debugging the discard process */
    private void logInfoDiscard(String str) {
        if( discardLogger.isLoggable(Level.FINE) ) {
            discardLogger.log(Level.FINE, str);
        }//endif
    }//end logInfoDiscard

    /* Logs information useful to debugging the leasing mechanism */
    private void logInfoLease(String str, Uuid regID, Uuid leaseID) {
        if(    (str != null) && (regID != null)
            && (leaseLogger.isLoggable(Level.FINER)) )
        {
            leaseLogger.log(Level.FINER, str+" (registrationID,leaseID) = ("
                                         +regID+", "+leaseID+")");
        }//endif
    }//end logInfoLease

    /* Logs information useful to debugging the registration mechanism */
    private void logInfoRegistration(String str, Object regInfo) {
        if(    (str != null) && (regInfo != null)
            && (registrationLogger.isLoggable(Level.FINER)) )
        {
            registrationLogger.log(Level.FINER, str+" {0}", regInfo);
        }//endif
    }//end logInfoRegistration

    /* Logs information useful to debugging the logging mechanism */
    private static void logInfoPersist(String str) {
        if( persistLogger.isLoggable(Level.FINEST) ) {
            persistLogger.log(Level.FINEST, str);
        }//endif
    }//end logInfoPersist

    /* Logs information useful to debugging the addLogRecord method */
    private void logInfoAddLogRecord(LogRecord rec) {
        if( !(persistLogger.isLoggable(Level.FINEST)) )  return;
        String logStr = "Logging a state change: Unknown log record instance";
        /* JoinAdmin */
        if(rec instanceof LookupAttrsAddedLogObj) {
            logStr = "Logging state change: lookup attributes added";
        } else if (rec instanceof LookupAttrsModifiedLogObj) {
            logStr = "Logging state change: lookup attributes modified";
        } else if (rec instanceof LookupGroupsChangedLogObj) {
            logStr = "Logging state change: groups to join changed to "
                     +writeGroupArrayToString(thisServicesGroups);
        } else if (rec instanceof LookupLocatorsChangedLogObj) {
            logStr = "Logging state change: locators to join changed to "
                     +writeArrayElementsToString(thisServicesLocators);
        /* FiddlerAdmin */
        } else if (rec instanceof LeaseBoundSetLogObj) {
            logStr = "Logging state change: lease duration bound changed";
        } else if (rec instanceof SnapshotWeightSetLogObj) {
            logStr = "Logging state change: snapshot weight factor changed";
        } else if (rec instanceof SnapshotThresholdSetLogObj) {
            logStr = "Logging state change: log-to-snapshot threshold changed";
        /* LookupDiscoveryService */
        } else if (rec instanceof RegistrationGrantedLogObj) {
            logStr = "Logging state change: new registration granted";
        /* LookupDiscoveryRegistration */
        } else if (rec instanceof GroupsAddedToRegistrationLogObj) {
            logStr = "Logging state change: added new groups to "
                     +"registration's set of groups to discover";
        } else if (rec instanceof GroupsSetInRegistrationLogObj) {
            logStr = "Logging state change: replaced registration's set of "
                     +"groups to discover";
        } else if (rec instanceof GroupsRemovedFromRegistrationLogObj) {
            logStr = "Logging state change: removed groups from "
                     +"registration's set of groups to discover";
        } else if (rec instanceof LocsAddedToRegistrationLogObj) {
            logStr = "Logging state change: added new locators to "
                     +"registration's set of locators to discover";
        } else if (rec instanceof LocsSetInRegistrationLogObj) {
            logStr = "Logging state change: replaced registration's set of "
                     +"locators to discover";
        } else if (rec instanceof LocsRemovedFromRegistrationLogObj) {
            logStr = "Logging state change: removed locators from "
                     +"registration's set of locators to discover";
        } else if (rec instanceof LeaseRenewedLogObj) {
            logStr = "Logging state change: registration's lease renewed";
        } else if (rec instanceof LeasesRenewedLogObj) {
            logStr = "Logging state change: set of leases renewed for a "
                     +"set of registrations";
        } else if (rec instanceof LeaseCancelledLogObj) {
            logStr = "Logging state change: registration's lease cancelled";
        } else if (rec instanceof LeasesCancelledLogObj) {
            logStr = "Logging state change: set of leases cancelled for a "
                     +"set of registrations";
        }//endif
        logInfoPersist(logStr);
    }//end logInfoAddLogRecord
    /* END Private Logging Facility Methods -------------------------------- */
    /* *************** END Private NON-Static Utility Methods ************** */

}/*end class FiddlerImpl */
