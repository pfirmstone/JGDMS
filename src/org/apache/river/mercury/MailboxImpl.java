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

package org.apache.river.mercury;

import org.apache.river.config.Config;
import org.apache.river.constants.TimeConstants;
import org.apache.river.constants.ThrowableConstants;
import org.apache.river.landlord.LeasedResource;
import org.apache.river.landlord.LeaseFactory;
import org.apache.river.landlord.LeasePeriodPolicy;
import org.apache.river.landlord.LeasePeriodPolicy.Result;
import org.apache.river.landlord.Landlord.RenewResults;
import org.apache.river.landlord.LocalLandlord;
import org.apache.river.landlord.LandlordUtil;
import org.apache.river.logging.Levels;
import org.apache.river.lookup.entry.BasicServiceType;
import org.apache.river.lookup.entry.LookupAttributes;
import org.apache.river.proxy.ThrowThis;
import org.apache.river.reliableLog.ReliableLog;
import org.apache.river.reliableLog.LogException;
import org.apache.river.reliableLog.LogHandler;
import org.apache.river.start.LifeCycle;
import org.apache.river.api.util.Startable;
import org.apache.river.thread.InterruptedStatusThread;
import org.apache.river.thread.ReadersWriter;
import org.apache.river.thread.ReadersWriter.ConcurrentLockException;
import org.apache.river.thread.ReadyState;
import org.apache.river.thread.RetryTask;
import org.apache.river.thread.WakeupManager;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import net.jini.config.ConfigurationException;
import net.jini.config.NoSuchEntryException;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.security.ProxyPreparer;
import net.jini.security.proxytrust.ServerProxyTrust;
import net.jini.security.TrustVerifier;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.rmi.activation.Activatable;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationGroupID;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationSystem;
import java.rmi.MarshalledObject;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.UnknownEventException;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceID;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.DiscoveryLocatorManagement;
import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.event.InvalidIteratorException;
import net.jini.event.MailboxRegistration;
import net.jini.event.MailboxPullRegistration;
import net.jini.lookup.entry.ServiceInfo;
import net.jini.lookup.JoinManager;
import net.jini.discovery.LookupDiscovery;
import net.jini.export.ServiceAttributesAccessor;
import net.jini.export.ServiceIDAccessor;
import net.jini.export.ServiceProxyAccessor;
import net.jini.io.MarshalledInstance;
import org.apache.river.thread.NamedThreadFactory;

/**
 * <tt>MailboxImpl</tt> implements the server side of the event 
 * mailbox service.
 * <p>
 * Client-side proxies make the appropriate transformation from client
 * requests to the methods of this class.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */

/*
Implementation Details:

The runtime state of this service is basically maintained in the following
data structures:
   * regByID - contains the Uuid to Registration mapping
   * regByExpiration - contains a time sorted ordering of 
       Registration objects
   * pendingReg - contains a list of Uuids that have been
       enabled for event delivery
   * activeReg - contains a list of Uuids that have an
       event delivery task in progress

Note that the latter 3 data structures can be rebuilt from the first, so only
regByID is actually stored to and retrieved from disk. 

See init() for exact details of how transient state gets rebuilt.
See takeSnapshot() for exact details of what gets stored.
See recoverSnapshot() for exact details of what gets retrieved.
*/

class MailboxImpl implements MailboxBackEnd, TimeConstants, 
    ServerProxyTrust, ProxyAccessor, Startable,
    ServiceProxyAccessor, ServiceAttributesAccessor, ServiceIDAccessor
 
{

    /** Logger and configuration component name for Norm */
    static final String MERCURY = "org.apache.river.mercury";

    /** Logger for lease related messages */
    static final Logger LEASE_LOGGER = 
        Logger.getLogger(MERCURY + ".lease");
    
    /** Logger for event delivery related messages */
    static final Logger DELIVERY_LOGGER = 
        Logger.getLogger(MERCURY + ".delivery");
    
    /** Logger for service administration related messages */
    static final Logger ADMIN_LOGGER = 
        Logger.getLogger(MERCURY + ".admin");
    
    /** Logger for service initialization related messages */
    static final Logger INIT_LOGGER = 
        Logger.getLogger(MERCURY + ".init");
    
    /** Logger for event reception related messages */
    static final Logger RECEIVE_LOGGER = 
        Logger.getLogger(MERCURY + ".receive");
    
    /** Logger for lease expiration related messages */
    static final Logger EXPIRATION_LOGGER = 
        Logger.getLogger(MERCURY + ".expiration");
    
    /** Logger for service recovery related messages */
    static final Logger RECOVERY_LOGGER = 
        Logger.getLogger(MERCURY + ".recovery");
    
    /** Logger for service persistence related messages */
    static final Logger PERSISTENCE_LOGGER = 
        Logger.getLogger(MERCURY + ".persistence");
    
    /** Logger for (successful) service startup message */
    static final Logger STARTUP_LOGGER =
        Logger.getLogger(MERCURY + ".startup");
   
    /** Logger for service operation messages */
    static final Logger OPERATIONS_LOGGER =
        Logger.getLogger(MERCURY + ".operations");

    static final String MAILBOX_SOURCE_CLASS = 
	MailboxImpl.class.getName();

    private static final String NOTIFIER_SOURCE_CLASS = 
	Notifier.class.getName();

    private static final String NOTIFY_TASK_SOURCE_CLASS = 
	NotifyTask.class.getName();

    private static final String DESTROY_THREAD_SOURCE_CLASS = 
	DestroyThread.class.getName();

    private static final String EXPIRATION_THREAD_SOURCE_CLASS = 
	ExpirationThread.class.getName();

    private static final String REGISTRATION_LOG_OBJ_SOURCE_CLASS = 
	RegistrationLogObj.class.getName();

    private static final String REGISTRATION_ENABLED_LOG_OBJ_SOURCE_CLASS = 
	RegistrationEnabledLogObj.class.getName();

    private static final String REGISTRATION_DISABLED_LOG_OBJ_SOURCE_CLASS = 
	RegistrationDisabledLogObj.class.getName();

    private static final String REGISTRATION_ITERATOR_ENABLED_LOG_OBJ_SOURCE_CLASS = 
	RegistrationIteratorEnabledLogObj.class.getName();
    
    private static final String LOOKUP_GROUPS_CHANGED_LOG_OBJ_SOURCE_CLASS = 
	LookupGroupsChangedLogObj.class.getName();

    private static final String LOOKUP_LOCATORS_CHANGED_LOG_OBJ_SOURCE_CLASS = 
	LookupLocatorsChangedLogObj.class.getName();

    private static final String ATTRS_ADDED_LOG_OBJ_SOURCE_CLASS = 
	AttrsAddedLogObj.class.getName();

    private static final String ATTRS_MODIFIED_LOG_OBJ_SOURCE_CLASS = 
	AttrsModifiedLogObj.class.getName();

    private static final String REGISTRATION_RENEWED_LOG_OBJ_SOURCE_CLASS = 
	RegistrationRenewedLogObj.class.getName();

    private static final String REGISTRATION_CANCELLED_LOG_OBJ_SOURCE_CLASS = 
	RegistrationCancelledLogObj.class.getName();

    private static final String UNKNOWN_EVENT_EXCEPTION_LOG_OBJ_SOURCE_CLASS = 
	UnknownEventExceptionLogObj.class.getName();

    private static final String SNAPSHOT_THREAD_SOURCE_CLASS = 
	SnapshotThread.class.getName();

    /** ServiceInfo product value */
    private static final String PRODUCT = "EventMailbox";
    /** ServiceInfo manufacturer value */
    private static final String MANUFACTURER = "Sun Microsystems, Inc.";
    /** ServiceInfo vendor value */
    private static final String VENDOR = MANUFACTURER;
    /** ServiceInfo version value */
    private static final String VERSION = 
	org.apache.river.constants.VersionConstants.SERVER_VERSION;
    /** Log format version */
    private static final int LOG_VERSION = 2;
    
    /** The attributes to use when joining lookup services */
    private static final Entry[] BASE_LOOKUP_ATTRS = new Entry[] { 
	    new ServiceInfo(PRODUCT, MANUFACTURER, VENDOR, VERSION, "", ""),
            new BasicServiceType("Event Mailbox")
    };

    /** The inner proxy of this server */
    private /*final*/ volatile MailboxBackEnd serverStub;
    /** The outter proxy of this server */
    private /*final*/ volatile MailboxProxy mailboxProxy;
    /** The admin proxy of this server */
    private /*final*/ volatile MailboxAdminProxy mailboxAdminProxy;
    /** Concurrent object (lock) to control read and write access */
    private final ReadersWriter concurrentObj = new ReadersWriter();
    /** Map from <code>Uuid</code> to <code>ServiceRegistration</code> */
    // HashMap is unsynchronized, but we are performing external
    // synchronization via the <code>concurrentObj</code> field. 
    private final Map<Uuid,ServiceRegistration> regByID; 
    /**
     * Identity map of <tt>ServiceRegistration</tt>, ordered by lease 
     * expiration. This is a parallel data structure to <code>regByID</code>.
     */
    // TreeMap is unsynchronized, but we are performing external
    // synchronization via the <code>concurrentObj</code> field. 
    private final TreeMap<ServiceRegistration,ServiceRegistration> regByExpiration;
    /** 
     * List of <tt>ServiceRegistration</tt>s that have event 
     * delivery enabled, but don't have any event delivery tasks 
     * currently scheduled.
     */
    // Using an ArrayList for random access performance. 
    // ArrayList is unsynchronized, but we are performing external
    // synchronization via the <code>concurrentObj</code> field. 
    private final List<Uuid> pendingReg;
    /**
     * Map of <tt>ServiceRegistration</tt>s that have event 
     * delivery enabled and have event delivery tasks currently 
     * scheduled.
     */
    // HashMap is unsynchronized, but we are performing external
    // synchronization via the <code>concurrentObj</code> field. 
    private final Map<Uuid,NotifyTask> activeReg;
    /** Reliable loG to hold registration state information */
    // Note that event state is kept separately
    private final ReliableLog log;
    /** Flag indicating whether system is in a state of recovery */
    private volatile boolean inRecovery;
    /** Current number of records in the Log File since the last snapshot */
    private int logFileSize = 0;
    /** Log File must contain this many records before a snapshot is allowed */
// TODO (FCS)- allow this to be a user configurable parameter
    private int logToSnapshotThreshold = 50;
    /** Object on which the snapshot-taking thread will synchronize */
    private final Object snapshotNotifier = new Object();
    /** Snapshot-taking thread */
    private final Thread snapshotter;
    /** The login context, for logging out */
    protected final LoginContext loginContext;
    /** Name of persistence directory */
    private final String persistenceDirectory;
    /** Proxy preparer for listeners */
    private final ProxyPreparer listenerPreparer;
    /** Proxy preparer recovered */
    private ProxyPreparer recoveredListenerPreparer;
    /** The exporter for exporting and unexporting */
    protected final Exporter exporter;
   /** ServiceID returned from the lookup registration process */
    private volatile Uuid serviceID;
    /** Our activation ID */
    private final ActivationID activationID;
    /** Whether the activation ID has been prepared */
    private final boolean activationPrepared;
    /** The activation system, prepared */
    private final ActivationSystem activationSystem;
    /** <code>EventLogIterator</code> generator */
    private final EventLogFactory eventLogFactory;
    /** <code>LeasePeriodPolicy</code> for this service */
    private final LeasePeriodPolicy leasePolicy; 
    /** <code>LandLordLeaseFactory</code> we use to create leases */
    private volatile LeaseFactory leaseFactory;
    /** LocalLandlord to use with LandlordUtil calls */
    private final LocalLandlordAdaptor localLandlord = new LocalLandlordAdaptor();
    /** Manager for joining lookup services */
    private JoinManager joiner = null;
    /** 
     * DiscoveryManager for joining lookup services. 
     * This can always be obtained from the JoinManager, so
     * this is just used as a shortcut.
     */
    private DiscoveryManagement lookupDiscMgr = null;
    
    private Entry[] lookupAttrs = new Entry[] {};
    /** 
     * The lookup groups we should join. 
     * Default is to join no groups. 
     */
    private String[] lookupGroups = LookupDiscovery.NO_GROUPS;
    /** 
     * The lookup locators we should join 
     * Default is to join with no locators. 
     */
    private LookupLocator[] lookupLocators = new LookupLocator[0];
    /* Preparer for initial and new lookup locators this service should
     * discover and join.
     */
    private static ProxyPreparer locatorToJoinPreparer;
    /* Preparer for lookup locators this service should discover and join
     * that were previously prepared and which were recovered from this
     * service's persisted state.
     */
    private static ProxyPreparer recoveredLocatorToJoinPreparer;
   /** Event delivery thread */
    private final Thread notifier;
    /** Object for coordinating actions with the notification thread */
    private final Object eventNotifier = new Object();
    /** Registration expireR thread */
    private final Thread expirer;
    /** Earliest expiration time of any registration */
    private long minRegExpiration = Long.MAX_VALUE;
    /** Object for coordinating actions with the expire thread */
    private final Object expirationNotifier = new Object();
    /** Object for coordinating the destroy process */
    private final Object destroyLock = new Object();
    /** 
     * Flag that denotes whether or not destroy has already been called.
     * The variable is guarded by <code>destroyLock</code>.
     */
    private volatile boolean destroySucceeded = false;
    /**
     * When destroying the space, how long to wait for a clean
     * unexport (which allows the destroy call to return) before
     * giving up calling <code>unexport(true)</code>
     */
    private final long maxUnexportDelay;
    /** Length of time to sleep between unexport attempts */
    private final long unexportRetryDelay;
    /** 
     * Object used to prevent access to this service during the service's
     *  initialization or shutdown processing.
     */
    private final ReadyState readyState = new ReadyState();
    /**
     * <code>LifeCycle</code> object used to notify starter framework
     * that this object's implementation reference, if any, should not
     * be held onto any longer. This is only used in the non-activatable case.
     */
    private volatile LifeCycle lifeCycle;
    /**
     * <code>boolean</code> flag used to determine persistence support.
     * Defaulted to true, and overridden in the constructor overload that takes
     * a <code>boolean</code> argument.
     */
    private final boolean persistent;
    
     // The following two fields are only required by start, called by the
    // constructor thread and set to null after starting.
    private Configuration config;
    private Throwable thrown;
    private boolean started = false;
    private AccessControlContext context;
    
    ///////////////////////
    // Activation Methods
    ///////////////////////

    public Object getProxy() {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "getProxy");
	}
	if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "getProxy", serverStub);
	}
	return serverStub;
    }

    /* inherit javadoc */
    public Object getServiceProxy() {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "getServiceProxy");
	}
        readyState.check();
	if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "getServiceProxy", mailboxProxy);
	}
        return mailboxProxy;
    }

    /* inherit javadoc */
    public Object getAdmin() throws RemoteException {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "getAdmin");
	}
        readyState.check();
	if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "getAdmin", mailboxAdminProxy);
	}
        return mailboxAdminProxy;
    }

    /**
     * Activation constructor
     *
     * @param activationID activation ID passed in by the activation daemon.
     * @param data state data needed to re-activate a Mercury server
     */
    MailboxImpl(ActivationID activationID, MarshalledObject data) 
	throws Exception
    {
        this((String[]) new MarshalledInstance(data).get(false), activationID,
                true, new Object[] {activationID, data} );
    }
    
    /////////////////////////
    // Non-Activation Methods
    /////////////////////////
    /** 
     * Constructor for creating transient (i.e. non-activatable) service 
     * instances. 
     * State information is still logged to persistent storage.
     * This method is only intended for debugging purposes at this time.
     *  
     */
    // @param loG directory where persistent state is maintained
    MailboxImpl(String[] configArgs, LifeCycle lc, boolean persistent) 
	throws Exception
    {
        this(configArgs, null, persistent, new Object[] {Arrays.asList(configArgs), lc, Boolean.valueOf(persistent)});
	lifeCycle = lc; 
    }
    
    private static Configuration config(String[] configArgs) 
            throws ConfigurationException
    {
        Configuration config;
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
            OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
                "init", (Object[])configArgs);
        }
        config =
            ConfigurationProvider.getInstance(
                configArgs, MailboxImpl.class.getClassLoader());
        return config;
    }
    
    private static LoginContext loginContext(Configuration config) 
            throws ConfigurationException
    {
        return config.getEntry(MERCURY, "loginContext", LoginContext.class, null);
    }
    
    private static MailboxImplInit init(final Configuration config, LoginContext loginContext, final ActivationID activID, final boolean persistant, Object [] logMessage) throws ConfigurationException, LoginException, RemoteException, ActivationException, IOException, Exception{
        MailboxImplInit init = null;
        if (loginContext != null) {
//                doInitWithLogin(config, loginContext);
            /* */
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
                OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
                    "doInitWithLogin", new Object[] { config, loginContext});
            }
            loginContext.login();
            try {
                init = Subject.doAsPrivileged(loginContext.getSubject(),
                    new PrivilegedExceptionAction<MailboxImplInit>() {
                        public MailboxImplInit run() 
                                throws 
                                ConfigurationException, 
                                RemoteException, 
                                ActivationException, 
                                IOException 
                        {
//                                doInit(config);
                            // Thread need to inherit the
                            // current context.
                            return new MailboxImplInit(config,
                                    persistant, 
                                    activID, 
                                    BASE_LOOKUP_ATTRS);
                        }
                    },
                    null);
            } catch (PrivilegedActionException e) {
                try {
                    loginContext.logout();
                } catch (LoginException le) {
    //TODO - Move to end of cleanup()
                    if (INIT_LOGGER.isLoggable(Levels.HANDLED)) {
                        INIT_LOGGER.log(Levels.HANDLED, "Trouble logging out", le);
                    }
                }
                throw e.getException(); 
            }
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
                OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
                    "doInitWithLogin");
            }
            /* */
        } else {
            init = new MailboxImplInit(config, 
                    persistant, 
                    activID, 
                    BASE_LOOKUP_ATTRS
                    );

        }
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
            OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
                "init");
        }
        return init;
    }
        
    private MailboxImpl( 
            Configuration config,  
            final ActivationID activID, 
            final boolean persistant, 
            Object [] logMessage ) throws LoginException, 
            ActivationException, IOException, RemoteException, Exception
    {
        this(init(config, loginContext(config), activID, persistant, logMessage), activID, persistant, logMessage);
    }
    
    private MailboxImpl(MailboxImplInit init, 
            ActivationID activID,
            final boolean persistent,
            final Object [] logMessage )
    {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
            OPERATIONS_LOGGER.entering(
		MailboxImpl.class.getName(), "MailboxImpl",logMessage );
	}
        this.persistent = persistent; 
        activationID = init.activationID;
        activationSystem = init.activationSystem;
        activationPrepared = init.activationPrepared;
        exporter = init.exporter;
        listenerPreparer = init.listenerPreparer;
        recoveredListenerPreparer = init.recoveredListenerPreparer;
        locatorToJoinPreparer = init.locatorToJoinPreparer;
        leasePolicy = init.leasePolicy;
        persistenceDirectory = init.persistenceDirectory;
        recoveredLocatorToJoinPreparer = init.recoveredLocatorToJoinPreparer;
        logToSnapshotThreshold = init.logToSnapshotThreshold;
        serviceID = init.serviceID;
        lookupGroups = init.lookupGroups;
        lookupLocators = init.lookupLocators;
        lookupAttrs = init.lookupAttrs;
        maxUnexportDelay = init.maxUnexportDelay;
        unexportRetryDelay = init.unexportRetryDelay;
        lookupDiscMgr = init.lookupDiscMgr;
        regByExpiration = init.regByExpiration;
        regByID = init.regByID;
        activeReg = init.activeReg;
        /** <code>EventLogIterator</code> generator */
        eventLogFactory = init.eventLogFactory;
        pendingReg = init.pendingReg;
        this.config = init.config;
        this.loginContext = init.loginContext;
        context = init.context;

        // Assign fields
        
        Thread snapShotter = null;
        Thread notifieR = null;
        Thread expireR = null;
        ReliableLog loG = null;
        try {
            Object [] result
                = AccessController.doPrivileged(new PrivilegedExceptionAction<Object[]>(){
                    public Object [] run() throws Exception {
                        Object [] res = new Object [4];
                        res [0] = persistent ? new SnapshotThread(): null;
                        res [1] = new Notifier(config);
                        res [2] = new ExpirationThread();
                        
                        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
                            OPERATIONS_LOGGER.entering(
                                MailboxImpl.class.getName(), "MailboxImpl",
                                    logMessage );
                        }
                        if(persistent){
                            res [3] = new ReliableLog(persistenceDirectory,
                                    new LocalLogHandler());
                        } else {
                            res [3] = null;
                        }
                        return res;
                    }
                }, context);
            snapShotter = (Thread) result[0];
            notifieR = (Thread) result [1];
            expireR = (Thread) result [2];
            loG = (ReliableLog) result [3];
            thrown = null;
        } catch (PrivilegedActionException ex) {
            thrown = ex.getException();
        }
        this.log = loG;
        this.snapshotter = snapShotter;
        this.notifier = notifieR;
        this.expirer = expireR;
    }
    
    private MailboxImpl(String[] configArgs,
            final ActivationID activID, 
            final boolean persistant, 
            Object [] logMessage) 
        throws ConfigurationException, ActivationException,
            IOException, RemoteException, Exception
    {
        this(config(configArgs), activID, persistant, logMessage);
    }
   
    /* Recovery and starting of threads performed here */
    public void start() throws Exception {
        concurrentObj.writeLock();
        if (started) return;
        started = true; // mutual exclusion
        
        try {
            if (thrown != null) throw thrown;
            AccessController.doPrivileged(new PrivilegedExceptionAction(){

                @Override
                public Object run() throws Exception {
                    if (persistent){
                        if (MailboxImpl.INIT_LOGGER.isLoggable(Level.FINEST)) {
                        MailboxImpl.INIT_LOGGER.log(Level.FINEST, "Recovering persistent state");
                        }
                        log.recover();  
                    }
                    if (serviceID == null) {
                        // First time up, get initial values
                        if (MailboxImpl.INIT_LOGGER.isLoggable(Level.FINEST)) {
                            MailboxImpl.INIT_LOGGER.log(Level.FINEST, "Getting initial values.");
                        }
                        serviceID = UuidFactory.generate();
                        if (MailboxImpl.INIT_LOGGER.isLoggable(Level.FINEST)) {
                            MailboxImpl.INIT_LOGGER.log(Level.FINEST, "ServiceID: {0}", serviceID);
                        }
                        // Can be null for ALL_GROUPS
                        lookupGroups = (String[]) config.getEntry(MailboxImpl.MERCURY, "initialLookupGroups", String[].class, new String[]{""}); //default to public group
                        //default to public group
                        if (MailboxImpl.INIT_LOGGER.isLoggable(Level.CONFIG)) {
                            MailboxImpl.INIT_LOGGER.log(Level.CONFIG, "Initial groups:");
                            MailboxImpl.dumpGroups(lookupGroups, MailboxImpl.INIT_LOGGER, Level.CONFIG);
                        }
                        /*
                         * Note: Configuration provided locators are assumed to be
                         * prepared already.
                         */
                        lookupLocators = (LookupLocator[]) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "initialLookupLocators", LookupLocator[].class, new LookupLocator[0]);
                        if (MailboxImpl.INIT_LOGGER.isLoggable(Level.CONFIG)) {
                            MailboxImpl.INIT_LOGGER.log(Level.CONFIG, "Initial locators:");
                            MailboxImpl.dumpLocators(lookupLocators, MailboxImpl.INIT_LOGGER, Level.CONFIG);
                        }
                        final Entry[] initialAttrs = (Entry[]) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "initialLookupAttributes", Entry[].class, new Entry[0]);
                        if (MailboxImpl.INIT_LOGGER.isLoggable(Level.CONFIG)) {
                            MailboxImpl.INIT_LOGGER.log(Level.CONFIG, "Initial lookup attributes:");
                            MailboxImpl.dumpAttrs(initialAttrs, MailboxImpl.INIT_LOGGER, Level.CONFIG);
                        }
                        if (initialAttrs.length == 0) {
                            lookupAttrs = BASE_LOOKUP_ATTRS;
                        } else {
                            lookupAttrs = new Entry[initialAttrs.length + BASE_LOOKUP_ATTRS.length];
                            int i = 0;
                            for (int j = 0; j < BASE_LOOKUP_ATTRS.length; j++, i++) {
                                lookupAttrs[i] = BASE_LOOKUP_ATTRS[j];
                            }
                            for (int j = 0; j < initialAttrs.length; j++, i++) {
                                lookupAttrs[i] = initialAttrs[j];
                            }
                        }
                        if (MailboxImpl.INIT_LOGGER.isLoggable(Level.FINEST)) {
                            MailboxImpl.INIT_LOGGER.log(Level.FINEST, "Combined lookup attributes:");
                            MailboxImpl.dumpAttrs(lookupAttrs, MailboxImpl.INIT_LOGGER, Level.FINEST);
                        }
                    } else {
                        // recovered logic
                        if (MailboxImpl.INIT_LOGGER.isLoggable(Level.FINEST)) {
                            MailboxImpl.INIT_LOGGER.log(Level.FINEST, "Preparing recovered locators:");
                            MailboxImpl.dumpLocators(lookupLocators, MailboxImpl.INIT_LOGGER, Level.FINEST);
                        }
                        MailboxImpl.prepareExistingLocators(recoveredLocatorToJoinPreparer, lookupLocators);
                        //TODO - Add recovered state debug: groups, locators, etc.
                    }
                    if (persistent) {
                        // Take snapshot of current state.
                        if (MailboxImpl.INIT_LOGGER.isLoggable(Level.FINEST)) {
                            MailboxImpl.INIT_LOGGER.log(Level.FINEST, "Taking snapshot.");
                        }
                        log.snapshot();
                        
                        // Reconstruct any transient state, if necessary.
                        // Rebuilds internal data structures after a restart.
                        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
                            OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
                                "RebuildTransientState", recoveredListenerPreparer);
                        }

                        // Reconstruct regByExpiration and pendingReg data structures,
                        // if necessary.
                        if (!regByID.isEmpty()) {
                            if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                                RECOVERY_LOGGER.log(Level.FINEST, "Rebuilding transient state ...");
                            }
                            Collection regs = regByID.values();
                            Iterator iter = regs.iterator();
                            ServiceRegistration reg = null;
                            Uuid uuid = null;
                            EventLogIterator eli = null; 
                            while (iter.hasNext()) {
                                reg = (ServiceRegistration)iter.next(); // get Reg
                                uuid = (Uuid)reg.getCookie();           // get its Uuid
                                if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                                    RECOVERY_LOGGER.log(Level.FINEST, "Checking reg : {0}", reg);
                                }
                                // Check if registration is still current
                                if (ensureCurrent(reg)) {
                                    if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                                        RECOVERY_LOGGER.log(Level.FINEST,
                                        "Restoring reg transient state ...");
                                    }
                                    try {
                                        concurrentObj.writeUnlock();//release
                                        // Holding a lock while calling this method should be avoided.
                                        reg.restoreTransientState(recoveredListenerPreparer);
                                    } catch (Exception e) {
                                        if (RECOVERY_LOGGER.isLoggable(Levels.HANDLED)) {
                                            RECOVERY_LOGGER.log(Levels.HANDLED,
                                                "Trouble restoring reg transient state", e);
                                        }
                                        try {
                                            reg.setEventTarget(null);
                                        } catch (IOException ioe) {
                                            throw new AssertionError(
                                                "Setting a null target threw an exception: "
                                                + ioe);
                                        }
                                    } finally {
                                        concurrentObj.writeLock();//relock
                                    } 

                                    if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                                        RECOVERY_LOGGER.log(Level.FINEST,
                                            "Reinitializing iterator ...");
                                    }
                                    // regenerate an EventLogIterator for this Reg
                                    // Note that event state is maintained separately
                                    // through the event loG mechanism.
                                    eli = persistent?
                                        eventLogFactory.iterator(uuid, 
                                            getEventLogPath(persistenceDirectory, uuid)):
                                        eventLogFactory.iterator(uuid); 
                                    reg.setIterator(eli);
                                    if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                                        RECOVERY_LOGGER.log(Level.FINEST, 
                                        "Adding registration to expiration watch list");
                                    }
                                    // Put Reg into time sorted collection
                                    regByExpiration.put(reg, reg);

                                    // Check if registration needs to be added to the
                                    // pending list. Note, we could have processed
                                    // an "enabled" log record during recovery, so 
                                    // only add it if it's not already there.
                                    // We don't need to check activeReg since the
                                    // the notifieR hasn't kicked in yet. Don't call
                                    // enableRegistration() since it clears the "unknown
                                    // events" list which we want to maintain.
                                    if (reg.hasEventTarget() &&
                                        !pendingReg.contains(uuid))
                                    {
                                        if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                                            RECOVERY_LOGGER.log(Level.FINEST, 
                                                "Adding registration to pending task list");
                                        }
                                        pendingReg.add(uuid);

                                    }
                                } else {
                                    /* Registration has expired, so remove it via the iterator,
                                     * which is the only "safe" way to do it during a traversal.
                                     * Call the overloaded version of removeRegistration()
                                     * which will avoid directly removing the registration
                                     * from regByID (which would result in a 
                                     * ConcurrentModificationException). See Bug 4507320.
                                     */
                                    if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                                        RECOVERY_LOGGER.log(Level.FINEST,
                                            "Removing expired registration: ");
                                    }
                                    iter.remove();
                                    removeRegistration(uuid, reg, true);
                                }
                            }
                        }
                        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
                            OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
                                "rebuildTransientState");
                        }
                    
                        // Start snapshot thread belongs in start method
                        snapshotter.start();
                    }

                    /*  ---  The following will go into start method --- */

                    // Start threads
                    notifier.start();
                    expirer.start();

                    // Export server instance and get its reference
                    serverStub = (MailboxBackEnd)exporter.export(MailboxImpl.this);
                    if (INIT_LOGGER.isLoggable(Level.FINEST)) {
                        INIT_LOGGER.log(Level.FINEST, "Service stub is: {0}", 
                        serverStub);	
                    }	

                    // Create the proxy that will be registered in the lookup service
                    mailboxProxy = 
                        MailboxProxy.create(serverStub, serviceID);
                    if (INIT_LOGGER.isLoggable(Level.FINEST)) {
                        INIT_LOGGER.log(Level.FINEST, "Service proxy is: {0}", 
                        mailboxProxy);
                    }		

                    // Create the admin proxy for this service
                    mailboxAdminProxy = 
                        MailboxAdminProxy.create(serverStub, serviceID);
                    if (INIT_LOGGER.isLoggable(Level.FINEST)) {
                        INIT_LOGGER.log(Level.FINEST, "Service admin proxy is: {0}", 
                        mailboxAdminProxy);		
                    }

                    // Create leaseFactory
                    leaseFactory = new LeaseFactory(serverStub, serviceID);

                    // Get shorthand reference to the discovery manager
                    try {
                        lookupDiscMgr  = 
                            (DiscoveryManagement)Config.getNonNullEntry(config,
                                MERCURY, "discoveryManager",
                                DiscoveryManagement.class);
                        if(lookupDiscMgr instanceof DiscoveryGroupManagement) {
                             // Verify proper initial state ---> NO_GROUPS
                            String[] groups =
                                ((DiscoveryGroupManagement)lookupDiscMgr).getGroups();
                            if( (groups == DiscoveryGroupManagement.ALL_GROUPS) ||
                                (groups.length != 0) )
                            {
                                throw new ConfigurationException(
                                    "discoveryManager entry must be configured " +
                                    " with no groups.");
                            }//endif
                        } else {
                           throw new ConfigurationException(
                                "discoveryManager entry must implement " +
                                "DiscoveryGroupManagement");
                        }

                        if(lookupDiscMgr instanceof DiscoveryLocatorManagement) {
                            LookupLocator[] locs =
                                    ((DiscoveryLocatorManagement)lookupDiscMgr).getLocators();
                            if( (locs != null) && (locs.length != 0) ) {
                                throw new ConfigurationException(
                                    "discoveryManager entry must be configured " +
                                    "with no locators");
                            }//endif
                        } else {
                            throw new ConfigurationException(
                                "discoveryManager entry must implement " +
                                "DiscoveryLocatorManagement");
                        }  

                        ((DiscoveryGroupManagement)lookupDiscMgr).setGroups(lookupGroups);
                        ((DiscoveryLocatorManagement)lookupDiscMgr).setLocators(lookupLocators);
                    } catch (NoSuchEntryException e) {
                        lookupDiscMgr  =
                            new LookupDiscoveryManager(lookupGroups, lookupLocators,
                                null, config);
                    }
                    if (INIT_LOGGER.isLoggable(Level.FINEST)) {
                        INIT_LOGGER.log(Level.FINEST, "Discovery manager is: {0}", 
                        lookupDiscMgr);
                    }		

                    ServiceID lookupID = new ServiceID(
                        serviceID.getMostSignificantBits(),
                        serviceID.getLeastSignificantBits());

                    if (INIT_LOGGER.isLoggable(Level.FINEST)) {
                        INIT_LOGGER.log(Level.FINEST, "Creating JoinManager.");
                    }
                    joiner = new JoinManager(
                        mailboxProxy,                // service object
                        lookupAttrs,               // service attributes
                        lookupID,                 // Service ID
                        lookupDiscMgr,             // DiscoveryManagement ref - default
                        null,                      // LeaseRenewalManager reference
                        config); 

                    if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
                        OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
                            "doInit");
                    }
                    readyState.ready();

                    if (STARTUP_LOGGER.isLoggable(Level.INFO)) {
                        STARTUP_LOGGER.log
                               (Level.INFO, "Mercury started: {0}", this);
                    }
                    return null;
                }
                
            }, context);
            
        } catch (Throwable t){
            cleanup();
	    initFailed(t);
        } finally {
            config = null;
            thrown = null;
            context = null;
            concurrentObj.writeUnlock();
        }
    }

    /*
     *
     */
    private void cleanup() {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, "cleanup");
	}
        if (serverStub != null) { // implies that exporter != null
	    try {
                if(INIT_LOGGER.isLoggable(Level.FINEST)) {
                    INIT_LOGGER.log(Level.FINEST, "Unexporting service");
		}		    
	        exporter.unexport(true);
	    } catch (Throwable t) {
                if(INIT_LOGGER.isLoggable(Levels.HANDLED)) {
                    INIT_LOGGER.log(Levels.HANDLED, "Trouble unexporting service", t);
		}		    
	    }
	}
	
	if (joiner != null) {
	    try {
                if(INIT_LOGGER.isLoggable(Level.FINEST)) {
                    INIT_LOGGER.log(Level.FINEST, "Terminating join manager");
		}		    
	        joiner.terminate();
	    } catch (Throwable t) {
                if(INIT_LOGGER.isLoggable(Levels.HANDLED)) {
                    INIT_LOGGER.log(Levels.HANDLED, 
		    "Trouble terminating join manager", t);
		}		    
	    }
	}
	if (lookupDiscMgr != null) {
	    try {
                if(INIT_LOGGER.isLoggable(Level.FINEST)) {
                    INIT_LOGGER.log(Level.FINEST, 
		    "Terminating lookup discovery manager");		    
		}
	        lookupDiscMgr.terminate();
	    } catch (Throwable t) {
                if(INIT_LOGGER.isLoggable(Levels.HANDLED)) {
                    INIT_LOGGER.log(Levels.HANDLED, 
		    "Trouble terminating lookup discovery manager", t);	
		}	    
	    }
	}
   	if (notifier != null) {
	    try {
                if(INIT_LOGGER.isLoggable(Level.FINEST)) {
                    INIT_LOGGER.log(Level.FINEST, 
		        "Interrupting notifier");	
		}	    
	        notifier.interrupt();
	    } catch (Throwable t) {
                if(INIT_LOGGER.isLoggable(Levels.HANDLED)) {
                    INIT_LOGGER.log(Levels.HANDLED, 
		        "Trouble interrupting notifier", t);		    
		}
	    }
	}
   	if (expirer != null) {
	    try {
                if(INIT_LOGGER.isLoggable(Level.FINEST)) {
                    INIT_LOGGER.log(Level.FINEST, 
		        "Interrupting expirer");
		}		    
	        expirer.interrupt();
	    } catch (Throwable t) {
                if(INIT_LOGGER.isLoggable(Levels.HANDLED)) {
                    INIT_LOGGER.log(Levels.HANDLED, 
		        "Trouble interrupting expirer", t);	
		}	    
	    }
	}
        if (snapshotter != null) {
	    try {
                if(INIT_LOGGER.isLoggable(Level.FINEST)) {
                    INIT_LOGGER.log(Level.FINEST, 
		        "Interrupting snapshotter");
		}		    
	        snapshotter.interrupt();
	    } catch (Throwable t) {
                if(INIT_LOGGER.isLoggable(Levels.HANDLED)) {
                    INIT_LOGGER.log(Levels.HANDLED, 
		        "Trouble interrupting snapshotter", t);	
		}	    
	    }
	}
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, "cleanup");
	}
    }

    ///////////////////////////
    // MailboxBackEnd Methods
    ///////////////////////////

    // This method's javadoc is inherited from an interface of this class
    public MailboxRegistration register(long leaseDuration) 
	throws RemoteException, LeaseDeniedException
    {
        readyState.check();
	concurrentObj.writeLock();
	try {
	    return registerDo(leaseDuration);
        } finally {
	    concurrentObj.writeUnlock();
	}
    }
    
    // This method's javadoc is inherited from an interface of this class
    public MailboxPullRegistration pullRegister(long leaseDuration) 
	throws RemoteException, LeaseDeniedException
    {
        readyState.check();
	concurrentObj.writeLock();
	try {
	    return registerDo(leaseDuration);
        } finally {
	    concurrentObj.writeUnlock();
	}
    }
    
    // This method's javadoc is inherited from an interface of this class
    public void enableDelivery(Uuid uuid, RemoteEventListener target) 
	throws RemoteException, ThrowThis 
    {
        readyState.check();
        RemoteEventListener preparedTarget = null;
        if (target == null) {
            disableDelivery(uuid);
	    return;
        } else {
            try {
                preparedTarget =
                    (RemoteEventListener) 
		        listenerPreparer.prepareProxy(target);
                if(DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                    DELIVERY_LOGGER.log(Level.FINEST, 
		        "prepared listener: {0}", preparedTarget);
		}
	    } catch (RemoteException e) {
                throw new ThrowThis(e);
            }
	}
	concurrentObj.writeLock();
	try {
	    enableDeliveryDo(uuid, preparedTarget);
        } finally {
	    concurrentObj.writeUnlock();
	}
    }
	
    // This method's javadoc is inherited from an interface of this class
    public void disableDelivery(Uuid uuid) 
	throws RemoteException, ThrowThis 
    {
        readyState.check();
	concurrentObj.writeLock();
	try {
	    disableDeliveryDo(uuid);
        } finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public RemoteEventIteratorData getRemoteEvents(
	Uuid uuid) 
	throws RemoteException, ThrowThis
    {
        readyState.check();
	concurrentObj.writeLock();
	try {
	    return getRemoteEventsDo(uuid);
        } finally {
	    concurrentObj.writeUnlock();
	}
    }	   
    
    // This method's javadoc is inherited from an interface of this class
    public Collection getNextBatch(
	Uuid regId, Uuid iterId, long timeout, Object lastEventCookie) 
	throws InvalidIteratorException, ThrowThis
    {
        readyState.check();
	concurrentObj.writeLock();
	try {
	    return getNextBatchDo(regId, iterId, timeout, lastEventCookie);
        } finally {
	    concurrentObj.writeUnlock();
	}
    }	   
    
    // This method's javadoc is inherited from an interface of this class
    public void addUnknownEvents(
	Uuid uuid, Collection unknownEvents) 
	throws RemoteException, ThrowThis
    {
        readyState.check();
	concurrentObj.writeLock();
	try {
	    addUnknownEventsDo(uuid, unknownEvents);
        } finally {
	    concurrentObj.writeUnlock();
	}
    }	
    
    // This method's javadoc is inherited from an interface of this class
    public void notify(Uuid registrationID, RemoteEvent theEvent) 
	throws UnknownEventException, RemoteException, ThrowThis
    {
        readyState.check();
	concurrentObj.writeLock();
	try {
	    notifyDo(registrationID, theEvent);
        } finally {
	    concurrentObj.writeUnlock();
	}
    }


    ////////////////////////
    // Landlord Methods
    ////////////////////////

    // inherit javadoc from parent
    public long renew(Uuid cookie, long extension)
	    throws LeaseDeniedException, UnknownLeaseException, RemoteException 
    {
        readyState.check();
	concurrentObj.priorityWriteLock();
	try {
	    return renewDo(cookie, extension);
        } finally {
	    concurrentObj.writeUnlock();
	}
    }

    // inherit javadoc from parent
    public void cancel(Uuid cookie)
	    throws UnknownLeaseException, RemoteException 
    {
        readyState.check();
	concurrentObj.writeLock();
	try {
	    cancelDo(cookie);
        } finally {
	    concurrentObj.writeUnlock();
	}
    }

    // inherit javadoc from parent
    public RenewResults renewAll(Uuid[] cookies, long[] extension)
	    throws RemoteException 
    {
        readyState.check();
	concurrentObj.writeLock();
	try {
	    return renewAllDo(cookies, extension);
        } finally {
	    concurrentObj.writeUnlock();
	}
    }

    // inherit javadoc from parent
    public Map cancelAll(Uuid[] cookies) throws RemoteException 
    {
        readyState.check();
	concurrentObj.writeLock();
	try {
	    return cancelAllDo(cookies);
        } finally {
	    concurrentObj.writeUnlock();
	}
    }

    //////////////////////////////////
    // DestroyAdmin methods
    //////////////////////////////////

    // This method's javadoc is inherited from an interface of this class
    public void destroy() {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "destroy");
	}
        readyState.check();
        
        /* 
         * Try to "interrupt" any existing blocking calls so that they return
         * early and don't hold up the subsequent unexporting process.
         * Notes:
         * - there's still a chance for more blocking calls to arrive
         * between "here" and when the unexport actually takes place, so we'll 
         * have do this again later on.
         * - need a lock here since we are notifying write waiters. Could get
         * away with just a readLock since the waiters are "writers", but 
         * getting a writeLock will work should there ever be "readers" waiting 
         * too.
         */
	concurrentObj.writeLock();
	try {
            ServiceRegistration[] regs = 
                (ServiceRegistration[])regByID.values().toArray(
                    new ServiceRegistration[regByID.size()]);
            if (ADMIN_LOGGER.isLoggable(Level.FINEST)) {
                ADMIN_LOGGER.log(Level.FINEST,
                    "Notifying {0} possible registrations",
                     Integer.valueOf(regByID.size()));
            }
            for (int i=0; i < regs.length; i++) {
                //Remove registrations from internal structures.
                removeRegistration(regs[i].getCookie(), regs[i]);
                // Notify any associated iterations so they can return early
                regs[i].getIteratorCondition().signal();           
                if(ADMIN_LOGGER.isLoggable(Level.FINEST)) {
                    ADMIN_LOGGER.log(Level.FINEST, 
                        "Iterator for reg {0} notified",
                        regs[i]);
                }
            }
        } finally {
	    concurrentObj.writeUnlock();
	}        
        
	(new DestroyThread()).start();
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "destroy");
	}
    }


    //////////////////////////////////
    // JoinAdmin methods
    //////////////////////////////////

    // This method's javadoc is inherited from an interface of this class
    public Entry[] getLookupAttributes() throws RemoteException {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "getLookupAttributes");
	}
        readyState.check();
	concurrentObj.readLock();
	try {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	            "getLookupAttributes");
            }
	    return joiner.getAttributes();
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void addLookupAttributes(Entry[] attrSets) throws RemoteException {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "addLookupAttributes");
	}
        readyState.check();
	concurrentObj.writeLock();
	try {
	    // delegate functionality to JoinManager
	    joiner.addAttributes(attrSets, true);
	    lookupAttrs = joiner.getAttributes();
	    // Log the modification
	    addLogRecord(new AttrsAddedLogObj(attrSets));
	} finally {
	    concurrentObj.writeUnlock();
	}
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "addLookupAttributes");
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void modifyLookupAttributes(Entry[] attrSetTemplates,
				       Entry[] attrSets)
	throws RemoteException
    {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "modifyLookupAttributes");
	}
        readyState.check();
	concurrentObj.writeLock();
	try {
	    // delegate functionality to JoinManager
	    joiner.modifyAttributes(attrSetTemplates, attrSets, true);
	    lookupAttrs = joiner.getAttributes();
	    // Log the modification
	    addLogRecord(new AttrsModifiedLogObj(attrSetTemplates, attrSets));
	} finally {
	    concurrentObj.writeUnlock();
	}
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "modifyLookupAttributes");
	}
    }


    // This method's javadoc is inherited from an interface of this class
    public String[] getLookupGroups() throws RemoteException {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "getLookupGroups");
	}
        readyState.check();
	concurrentObj.readLock();
	try {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	            "getLookupGroups");
	    }
	    return lookupGroups;
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void addLookupGroups(String[] groups) throws RemoteException {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "addLookupGroups");
	}
        readyState.check();
	concurrentObj.writeLock();
	try {
	    try {
	        // delegate functionality to discovery manager
		((DiscoveryGroupManagement)lookupDiscMgr).addGroups(groups);
	    } catch (IOException e) {
		throw new RuntimeException(e.toString());
	    }
	    lookupGroups = ((DiscoveryGroupManagement)lookupDiscMgr).getGroups();
	    // Log the modification
            addLogRecord(new LookupGroupsChangedLogObj(lookupGroups));
	} finally {
	    concurrentObj.writeUnlock();
	}
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "addLookupGroups");
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void removeLookupGroups(String[] groups) throws RemoteException {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "removeLookupGroups");
	}
        readyState.check();
	concurrentObj.writeLock();
	try {
	    // delegate functionality to discovery manager
	    ((DiscoveryGroupManagement)lookupDiscMgr).removeGroups(groups);
	    lookupGroups = ((DiscoveryGroupManagement)lookupDiscMgr).getGroups();
	    // Log the modification
            addLogRecord(new LookupGroupsChangedLogObj(lookupGroups));
	} finally {
	    concurrentObj.writeUnlock();
	}
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "removeLookupGroups");
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void setLookupGroups(String[] groups) throws RemoteException {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "setLookupGroups");
	}
        readyState.check();
	concurrentObj.writeLock();
	try {
	    try {
	        // delegate functionality to discovery manager
		((DiscoveryGroupManagement)lookupDiscMgr).setGroups(groups);
	    } catch (IOException e) {
		throw new RuntimeException(e.toString());
	    }
	    lookupGroups = ((DiscoveryGroupManagement)lookupDiscMgr).getGroups();
	    // Log the modification
            addLogRecord(new LookupGroupsChangedLogObj(lookupGroups));
	} finally {
	    concurrentObj.writeUnlock();
	}
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "setLookupGroups");
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public LookupLocator[] getLookupLocators() throws RemoteException {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "getLookupLocators");
	}
        readyState.check();
	concurrentObj.readLock();
	try {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
    	            "getLookupLocators");
            }
	    return lookupLocators;
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void addLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "addLookupLocators");
	}
        readyState.check();
        /* Prepare outside of sync block because of possible remote call */
	prepareNewLocators(locatorToJoinPreparer,locators);
	concurrentObj.writeLock();
	try {
	    // delegate functionality to discovery manager
	    ((DiscoveryLocatorManagement)lookupDiscMgr).addLocators(locators);
	    lookupLocators = 
	        ((DiscoveryLocatorManagement)lookupDiscMgr).getLocators();
	    // Log the modification
	    addLogRecord(new LookupLocatorsChangedLogObj(lookupLocators));
	} finally {
	    concurrentObj.writeUnlock();
	}
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "addLookupLocators");
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void removeLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "removeLookupLocators");
	}
        readyState.check();
        /* Prepare outside of sync block because of possible remote call */
	prepareNewLocators(locatorToJoinPreparer,locators);
	concurrentObj.writeLock();
	try {
	    // delegate functionality to discovery manager
	    ((DiscoveryLocatorManagement)lookupDiscMgr).removeLocators(locators);
	    lookupLocators = 
	        ((DiscoveryLocatorManagement)lookupDiscMgr).getLocators();
	    // Log the modification
	    addLogRecord(new LookupLocatorsChangedLogObj(lookupLocators));
	} finally {
	    concurrentObj.writeUnlock();
	}
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "removeLookupLocators");
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void setLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "setLookupLocators");
	}
        readyState.check();
        /* Prepare outside of sync block because of possible remote call */
	prepareNewLocators(locatorToJoinPreparer,locators);
	concurrentObj.writeLock();
	try {
	    // delegate functionality to discovery manager
	    ((DiscoveryLocatorManagement)lookupDiscMgr).setLocators(locators);
	    lookupLocators = 
	        ((DiscoveryLocatorManagement)lookupDiscMgr).getLocators();
	    // Log the modification
	    addLogRecord(new LookupLocatorsChangedLogObj(lookupLocators));
	} finally {
	    concurrentObj.writeUnlock();
	}
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "setLookupLocators");
	}
    }


    /**
     * Utility method that adds the provided EventID
     * to the given registration's "unknown" event list.
     * If the mailbox receives another event, on this registration's
     * behalf, and its EventID is on this list, the service will 
     * throw a UnknownEventException back to the event sender.
     */
    private void addUnknownEvent(Uuid regID, EventID evid) {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	    "addUnknownEvent", new Object[] {regID, evid});
	}
	concurrentObj.writeLock();
	try {
            addUnknownEventDo(regID, evid);
	} finally {
	    concurrentObj.writeUnlock();
	}
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "addUnknownEvent");
	}
    }
    
    /**
     * Performs the actual logic for adding an unknown event to the
     * given registration's unknown event list. Assumes caller
     * holds a write lock.
     */
    private void addUnknownEventDo(Uuid regID, EventID evid) {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	    "addUnknownEventDo", new Object[] {regID, evid});
	}
        //Ensure that the registration is still valid
        ServiceRegistration reg = (ServiceRegistration)regByID.get(regID);
        if(reg == null || !ensureCurrent(reg)) {
            return; // nothing to do ... registration is gone
        }
        if(DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
            DELIVERY_LOGGER.log(Level.FINEST, "Using reg: {0} ", reg);
        }

        // TODO - check if already there and save a loG record.
        // Add EventID to the list
        reg.getUnknownEvents().put(evid, evid);
        // Log this event
        addLogRecord(new UnknownEventExceptionLogObj(regID, evid));

        if(DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
            DELIVERY_LOGGER.log(Level.FINEST, "UnknownEvents size: {0}", 
                Integer.valueOf(reg.getUnknownEvents().size()));
        }
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "addUnknownEventDo");
	}
    }
    
    /**
     * Utility method that tries to obtain the ServiceRegistration
     * object associated with the given Uuid.  If there is
     * no associated ServiceRegistration object, or it has expired,
     * this method will throw a "wrapped" NoSuchObjectException.
     * Note: If the NoSuchObjectException was not wrapped, then
     * the RMI system would wrap into a ServerException (since it's 
     * a RemoteEvent).  The proxies for this service know how to unwrap
     * the ThrowThis object and re-throw the NoSuchObjectException back on
     * the client-side.
     */
    private ServiceRegistration getServiceRegistration(Uuid regID) 
        throws ThrowThis
    {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "getServiceRegistration", regID);
	}
        ServiceRegistration reg = (ServiceRegistration)regByID.get(regID); 
	if(reg == null) { // either expired or never existed
	    throw new ThrowThis( 
		new NoSuchObjectException(
		    "Not managing requested registration object"
		)
	    );
	} else if(!ensureCurrent(reg)) { // check validity
	    throw new ThrowThis( 
		new NoSuchObjectException(
		    "Requested registration object has expired"
		)
	    );
	}
	// Must be a valid registration at this point
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "getServiceRegistration", reg);
	}
	return reg;
    }

    /**
     * Utility method that calls the overloaded version that accepts a boolean,
     * which is set to false.
     */
    private void removeRegistration(Uuid regID, ServiceRegistration reg) {
        removeRegistration(regID, reg, false);
    }
    
    /**
     * Utility method that consolidates registration removal activities.
     * It's called when registration's are either expired or cancelled.
     */
    private void removeRegistration(Uuid regID, ServiceRegistration reg,
                                    boolean initializing) 
    {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "removeRegistration", 
	        new Object[] {regID, reg, Boolean.valueOf(initializing)});
	}
 
        // Remove Reg from data structures, if present.
	// If initializing, don't remove directly from regByID since we
	// currently traversing it via an iterator. Assumption is that
	// the caller has already removed it via the iterator.
	// See Bug 4507320. 
        if (!initializing) {
	    regByID.remove(regID);
	}
	regByExpiration.remove(reg);
	boolean exists = pendingReg.remove(regID);
	NotifyTask task = (NotifyTask)activeReg.remove(regID);
	if (task != null) { // cancel active task, if any
	    task.cancel(false);
	    if(DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                DELIVERY_LOGGER.log(Level.FINEST, 
		    "Cancelling active notification task for {0}", regID);
	    }
	}

	// Delete any associated resources
	try {
	    if(PERSISTENCE_LOGGER.isLoggable(Level.FINEST)) {
                PERSISTENCE_LOGGER.log(Level.FINEST, 
		    "Removing logs for {0}", reg);
            }
	    EventLogIterator iter = reg.iterator();
	    if (iter != null) // iter is null when recovering state
		iter.destroy();
	} catch (IOException ioe) {
	    if(PERSISTENCE_LOGGER.isLoggable(Levels.HANDLED)) {
                PERSISTENCE_LOGGER.log(Levels.HANDLED, 
		    "Trouble removing logs", ioe);
	    }
	    // Did the best we could ... continue.
	}


	// Sanity check
	if (exists && task != null) {
	    if(LEASE_LOGGER.isLoggable(Level.SEVERE)) {
                LEASE_LOGGER.log(Level.SEVERE, 
		    "ERROR: Registration was found "
	            + "on both the active and pending lists");
	    }
// TODO (FCS)- throw assertion error
	}
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "removeRegistration");
	}
    }

    /**
     * Utility method that returns the associated File object
     * for the given Uuid's persistence directory 
     */
    static File getEventLogPath(String parent, Uuid uuid) {
        return new File(parent, uuid.toString());
    }

    /** Actual implementation of the registration process. */
    private Registration registerDo(long duration) {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "registerDo", Long.valueOf(duration));
	}
        if (duration < 1 && duration != Lease.ANY) 
	    throw new IllegalArgumentException(
	        "Duration must be a positive value");
        // Create new ServiceRegistration object
        Uuid uuid = UuidFactory.generate(); 
        EventLogIterator iter = persistent?
	    eventLogFactory.iterator(uuid, 
	        getEventLogPath(persistenceDirectory, uuid)):
	    eventLogFactory.iterator(uuid);
        ServiceRegistration reg = new ServiceRegistration(uuid, iter, concurrentObj.newCondition());
        Lease l = null;
        try {
	    Result r = 
	        leasePolicy.grant(reg, duration);
	    reg.setExpiration(r.expiration);
            l = leaseFactory.newLease(uuid, r.expiration);
//TODO - destroy iter if leaseFor throws any exception
	} catch (LeaseDeniedException lde) {
	    // Should never happen in our implementation.
	    // Re-throw as an internal exception.
	    throw new InternalMailboxException("Registration lease was denied", 
		                               lde);
	}
        // Add registration to internal data structures 
        addRegistration(reg);

        // loG a record of this event
        addLogRecord(new RegistrationLogObj(reg));

        // Check expiration and notify expiration thread to 
        // wake up earlier than scheduled, if needed.
	if (reg.getExpiration() < minRegExpiration) {
	    minRegExpiration = reg.getExpiration();
            if (EXPIRATION_LOGGER.isLoggable(Level.FINEST)) {
                EXPIRATION_LOGGER.log(Level.FINEST,"Notifying expiration thread");
            }
	    concurrentObj.waiterNotify(expirationNotifier);
	}

	if(LEASE_LOGGER.isLoggable(Level.FINEST)) {
            LEASE_LOGGER.log(Level.FINEST, 
		"Generated new lease for: {0}", reg);
	    reg.dumpInfo(LEASE_LOGGER);
	}

        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "registerDo");
	}
        // Return client-side proxy for the ServiceRegistration
	return Registration.create(uuid, serverStub, l);
    }
    
    /** 
     * Utility method used to add a registration to our state information. 
     * This method is invoked from <code>registerDo()</code> as well as
     * <code> RegistrationLogObj.apply()</code>.  
     */
    private void addRegistration(ServiceRegistration reg) {
	regByID.put(reg.getCookie(), reg);
	regByExpiration.put(reg, reg);
    }

    /** Performs the actual registration renewal logic */
    private long renewDo(Uuid cookie, long extension)
        throws UnknownLeaseException, LeaseDeniedException 
    {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "renewDo", new Object[] { cookie, Long.valueOf(extension)});
	}
        if (extension < 1 && extension != Lease.ANY)
	    throw new IllegalArgumentException(
	        "Duration must be a positive value");
    
        ServiceRegistration reg = null;
	long expiration = 0;
        
	if(LEASE_LOGGER.isLoggable(Level.FINEST)) {
            LEASE_LOGGER.log(Level.FINEST, 
		"Attempting to renew {0}''s lease for {1} sec",
		new Object[] {cookie, Long.valueOf(extension/1000)}); 
	}
	
	// Get registration of interest
        Result r;
	try { 
            // Note: the following method will throw a ThrowThis exception
            // if the registration is invalid (i.e. expired or non-existent)
	    reg = getServiceRegistration(cookie);
	    // delegate renewal to policy object
	    r = leasePolicy.renew(reg, extension);
	    reg.setExpiration(r.expiration);
	    // Log this event
	    addLogRecord(new RegistrationRenewedLogObj(cookie, 
	                                               reg.getExpiration()));

	    // remove then add to cause a resort to occur
	    regByExpiration.remove(reg);
	    regByExpiration.put(reg, reg);
	} catch (ThrowThis tt) {
	    // Registration doesn't exist or has expired
	    if(LEASE_LOGGER.isLoggable(Level.FINEST)) {
                LEASE_LOGGER.log(Level.FINEST, 
		    "Lease for {0} was NOT renewed", cookie);
	    }
	    throw new UnknownLeaseException("Not managing requested lease");
	}

	if(LEASE_LOGGER.isLoggable(Level.FINEST)) {
            LEASE_LOGGER.log(Level.FINEST, 
	        "Lease for {0} was renewed", cookie);
	    reg.dumpInfo(LEASE_LOGGER);
	}
	
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "renewDo", Long.valueOf(r.duration));
	}
	return r.duration;
    }
    
    
    /** Performs the actual registration cancellation logic */
    private void cancelDo(Uuid cookie) throws UnknownLeaseException {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "cancelDo", cookie);
	}
        ServiceRegistration reg = null;

    	if(LEASE_LOGGER.isLoggable(Level.FINEST)) {
            LEASE_LOGGER.log(Level.FINEST, 
	        "Attempting to cancel lease for: {0}", cookie); 
    	}

	try { 
            // Note: the following method will throw a ThrowThis exception
            // if the registration is invalid (i.e. expired or non-existent)
	    reg = getServiceRegistration(cookie);
	    // Remove registration from internal data structures
            removeRegistration(cookie, reg);
            // Log this event
	    addLogRecord(new RegistrationCancelledLogObj(cookie));
	    // Notify expiration thread in case we deleted the registration
	    // nearest to expiration, so it can recompute minRegExpiration.
            if (reg.getExpiration() == minRegExpiration) {
                if(EXPIRATION_LOGGER.isLoggable(Level.FINEST)) {
                    EXPIRATION_LOGGER.log(Level.FINEST, 
	                "Notifying expiration thread");
                }
	        concurrentObj.waiterNotify(expirationNotifier);
	    }
            // Notify any pending iterations
            reg.getIteratorCondition().signal();          
            if(EXPIRATION_LOGGER.isLoggable(Level.FINEST)) {
                EXPIRATION_LOGGER.log(Level.FINEST, "Iterator notified");
            }
            
	} catch (ThrowThis tt) {
	    // Registration doesn't exist or has expired
	    throw new UnknownLeaseException("Not managing requested lease");
	}
    	if(LEASE_LOGGER.isLoggable(Level.FINEST)) {
            LEASE_LOGGER.log(Level.FINEST, 
	        "Lease cancelled for: {0}", cookie); 
    	}
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "cancelDo");
	}
    }
    
    /** Performs the actual renewAll logic */
    private RenewResults renewAllDo(Uuid[] cookie, long[] extension)
	    throws RemoteException 
    {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "renewAllDo");
	}
	int count = cookie.length;

	if(LEASE_LOGGER.isLoggable(Level.FINEST)) {
            LEASE_LOGGER.log(Level.FINEST, 
	        "Attempting to renew a batch of {0} leases", 
		Integer.valueOf(count)); 
	}

	// Delegate functionality to Landlord utility
	final RenewResults rslt = LandlordUtil.renewAll(localLandlord,
							cookie,	extension);

	if(rslt.noneDenied())  {
	    if(LEASE_LOGGER.isLoggable(Level.FINEST)) {
                LEASE_LOGGER.log(Level.FINEST, 
	            "Batch renew totally successful");
	    }
	} else {
	    if(LEASE_LOGGER.isLoggable(Level.FINEST)) {
                LEASE_LOGGER.log(Level.FINEST, 
	            "Batch renew contained exceptions");
	    }
	}

        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "renewAllDo");
	}
	return rslt;
    }	     

    /** Performs the actual cancelAll logic */
    private Map cancelAllDo(Uuid[] cookie) throws RemoteException {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "cancelAllDo");
	}
	int count = cookie.length;
	if(LEASE_LOGGER.isLoggable(Level.FINEST)) {
            LEASE_LOGGER.log(Level.FINEST, 
	        "Attempting to cancel a batch of {0} leases", 
		Integer.valueOf(count)); 
	}

        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "cancelAllDo");
	}
	// Delegate functionality to Landlord utility
	return LandlordUtil.cancelAll(localLandlord, cookie);
    }
	
    /** Performs the actual enable delivery logic */
    private void enableDeliveryDo(Uuid uuid, 
        RemoteEventListener preparedTarget) 
	throws ThrowThis 
    {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "enableDeliveryDo", new Object[] {uuid, preparedTarget});
	}
	// Check for the resubmission of one of our own listeners
	if (preparedTarget instanceof ListenerProxy) {
	    Uuid id = ((ListenerProxy)preparedTarget).getReferentUuid(); 
	    if (regByID.get(id) != null) {
	        if(DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                    DELIVERY_LOGGER.log(Level.FINEST, 
	                "Disallowing resubmission of one of" 
		        +  " this service''s own listeners: {0}", id); 
	        }
	        throw new IllegalArgumentException("Cannot submit a " +
		    "listener that was provided by the same EventMailbox " +
		    "service");
	    }
	}

	// Enable the listener for the given Uuid
	enableRegistration(uuid, preparedTarget);
        
	// Log this event
	addLogRecord(new RegistrationEnabledLogObj(uuid, preparedTarget));

	// Notify the notifieR thread of new "enabled" entry
	concurrentObj.waiterNotify(eventNotifier);
        
        // Note: the following method will throw a ThrowThis exception
        // if the registration is invalid (i.e. expired or non-existent)
        ServiceRegistration reg = getServiceRegistration(uuid); 
        
        // Notify any pending iterations
        reg.getIteratorCondition().signal();          
        if(EXPIRATION_LOGGER.isLoggable(Level.FINEST)) {
            EXPIRATION_LOGGER.log(Level.FINEST, "Iterator notified");
        }

	if(DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
            DELIVERY_LOGGER.log(Level.FINEST, 
	        "Enabling delivery for {0} to {1}",
		new Object[] {uuid, preparedTarget});
	}
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "enableDeliveryDo");
	}
    }

    /** 
     * Utility method that associates the given listener with the
     * associated registration object. 
     */
    private void enableRegistration(Uuid uuid, 
        RemoteEventListener preparedTarget) 
	throws ThrowThis 
    {
        // Note: the following method will throw a ThrowThis exception
        // if the registration is invalid (i.e. expired or non-existent)
        ServiceRegistration reg = getServiceRegistration(uuid); 

	// Set the event target
	try {
	    reg.setEventTarget(preparedTarget); 
	} catch (IOException ioe) {
	    // Problem serializing listener - must be bad argument
	    throw new IllegalArgumentException("Passed a listener " +
                              "that could not be serialized");
	}
        
        // Disable the remote event iterator, if any
        disableRegistrationIterator(uuid);
	
	// Clear unknownEvent collection regardless of whether the same target 
	// is provided. We'll assume that an active re-registration means 
	// that the client's listener has changed (for the better) somehow.
	reg.getUnknownEvents().clear();

        // Add this registration to set of enabled registrations
        // if it's not already there. Note that an enabled registration
        // can be in either the pendingReg or activeReg data structures
        // at any one time.
	if (!pendingReg.contains(uuid) && !activeReg.containsKey(uuid))
	    pendingReg.add(uuid);
    }

    /**
     * Performs the actual logic for disabling event delivery
     * for a particular registration.
     */
    private void disableDeliveryDo(Uuid uuid) throws ThrowThis {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "disableDeliveryDo", uuid);
	}
        // Disable event delivery for the associated registration
        disableRegistration(uuid);
        // Log this event
        addLogRecord(new RegistrationDisabledLogObj(uuid)); 
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "disableDeliveryDo");
	}
    }
        
    /**
     * Utility method that disables event delivery for the
     * registration associated with the given <tt>UUID</tt>
     */
    private void disableRegistration(Uuid uuid) throws ThrowThis {
	if(DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
            DELIVERY_LOGGER.log(Level.FINEST, 
	        "Attempting to disable delivery for {0}", uuid);
	}

        // get associated registration object
        // Note: the following method will throw a ThrowThis exception
        // if the registration is invalid (i.e. expired or non-existent)
        ServiceRegistration reg = getServiceRegistration(uuid); 

        // cache pre-disabled value for debugging purposes
        boolean wasEnabled = reg.hasEventTarget();

        // Set delivery target to null
	try {
	    reg.setEventTarget(null); 
	} catch (IOException ioe) {
	    throw new AssertionError(
	        "Setting a null target threw an exception: " + ioe);
	}

	// Remove registration from pending and active lists
	boolean inPending = pendingReg.remove(uuid);  
	NotifyTask task = (NotifyTask)activeReg.remove(uuid);
	if (task != null) { // cancel active task, if any
	    task.cancel(false);
	    if(DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                DELIVERY_LOGGER.log(Level.FINEST, 
	            "Cancelling active notification task for {0}", uuid);
	    }
	}

        // If wasEnabled == true then the registration was previously enabled.
        // If so, then the its UUID should have appeared in either
        // the active or pending data structures. This can happen 
        // during recovery but otherwise there's a problem.
	if (wasEnabled && !inPending && task == null && !inRecovery) {
	    if(DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                DELIVERY_LOGGER.log(Level.FINEST, 
	            "*** Registration was not found "
	            + "on the active or pending lists");
	    }
	    // TODO (FCS)- throw Assertion exception
	}

	if(DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
            DELIVERY_LOGGER.log(Level.FINEST, 
	        "Disabled delivery for {0}", uuid);
	}
    }

    /** 
     * Utility method that sets the valid iterator id for provided registration.
     */
    private void enableRegistrationIterator(Uuid regId, Uuid iterId) 
	throws ThrowThis 
    {
        
        // Note: the following method will throw a ThrowThis exception
        // if the registration is invalid (i.e. expired or non-existent)
        ServiceRegistration reg = getServiceRegistration(regId); 

	// Set the event iterator id
        reg.setRemoteEventIteratorID(iterId);
        
        // Disable the event target, if any
        disableRegistration(regId);
	
	// Clear unknownEvent collection for each new iteration request
	reg.getUnknownEvents().clear();
    }

    /** 
     * Utility method that sets the valid iterator id for provided registration.
     */
    private void disableRegistrationIterator(Uuid regId) 
	throws ThrowThis 
    {
        
        // Note: the following method will throw a ThrowThis exception
        // if the registration is invalid (i.e. expired or non-existent)
        ServiceRegistration reg = getServiceRegistration(regId); 

	// Set the event iterator id
        reg.setRemoteEventIteratorID(null);
    }

    /**
     * Performs the actual logic for synchronously getting events
     * for a particular registration.
     */
    private void addUnknownEventsDo(
	Uuid uuid, Collection unknownEvents) 
	throws ThrowThis 
    {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "addUnknownEventsDo", new Object[] {uuid, unknownEvents});
	}

        // Note: the following method will throw a ThrowThis exception
        // if the registration is invalid (i.e. expired or non-existent)
        ServiceRegistration reg = getServiceRegistration(uuid); 

//TODO - validate entries (i.e. non-null)
	Iterator iter = unknownEvents.iterator();
        RemoteEvent ev;
        while (iter.hasNext()) {
            ev = (RemoteEvent)iter.next();
            addUnknownEventDo(uuid, new EventID(ev));
        }
        
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "addUnknownEventsDo");
	}
        return;
    }
    
    /**
     * Performs the actual logic for synchronously getting events
     * for a particular registration.
     */
    private RemoteEventIteratorData getRemoteEventsDo(
	Uuid uuid) 
	throws ThrowThis 
    {

        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "getRemoteEventsDo", new Object[] {uuid});
	}

        ArrayList<RemoteEventData> events = new ArrayList<RemoteEventData>();

        // Note: the following method will throw a ThrowThis exception
        // if the registration is invalid (i.e. expired or non-existent)
        ServiceRegistration reg = getServiceRegistration(uuid); 
        
        Uuid iteratorId = UuidFactory.generate();
        if(DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
            DELIVERY_LOGGER.log(Level.FINEST, 
                "Generated remote event iterator id {0}", iteratorId);
        }       
	// Log this event
	addLogRecord(new RegistrationIteratorEnabledLogObj(uuid, iteratorId));
        
        // Enable iterator and disable event listener, if any
        enableRegistrationIterator(uuid, iteratorId);
        
        // Retrieve associated events
	try {
	    EventLogIterator eli = reg.iterator();
            if (eli.hasNext()) {
                //TODO - configurable logic for determining initial batch size
                RemoteEventData[] evts = eli.readAhead(Integer.MAX_VALUE);
                RemoteEvent evt = null;
//TODO - try-catch around getRemoteEvent for CNFE? Send anyway?
                for (int i=0; i < evts.length; i++) {
                    evt = evts[i].getRemoteEvent(); 			    
                    if (evt != null && 
                        !reg.getUnknownEvents().containsKey(new EventID(evt))) {
                       events.add(evts[i]);
                    }
                }
            }
	} catch (IOException ioe) {
	    // TODO (FCS)- pick better recovery algorithm
	    // Assumptions: 
	    //    - that any IOExceptions are transient in nature,
	    //      so keep trying.
	    //    - event logging state is still valid in the face
	    //      of an IOException
	} catch (NoSuchElementException nse) {
	    //TODO - throw an assertion error since this shouldn't happen
            // Can also be thrown if reading from empty loG
    	} catch (ClassNotFoundException cnfe) {
	    // TODO - retry?
        }
        
        // Notify any pending iterations
        reg.getIteratorCondition().signal();            
        if(EXPIRATION_LOGGER.isLoggable(Level.FINEST)) {
            EXPIRATION_LOGGER.log(Level.FINEST, "Iterator notified");
        }
        
	// Compact the collection, if possible
	events.trimToSize();
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "getRemoteEventsDo", events);
	}
        
        return new RemoteEventIteratorData(iteratorId, events);
    }

    /**
     * Does the actual logic for obtaining the next set of events for the
     * given registration. Assumes caller holds a write lock.
     */
    private Collection getNextBatchDo(
	Uuid regId, Uuid iterId, long timeout, Object lastEventCookie) 
	throws InvalidIteratorException, ThrowThis 
    {

        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "getNextBatchDo", 
                new Object[] {regId, iterId, Long.valueOf(timeout), lastEventCookie});
	}
        
        if (timeout < 0) {
            throw new 
                IllegalArgumentException("Timeout value must non-negative");
        } 
        
	// after endTime, can stop waiting
	long endTime = System.currentTimeMillis() + timeout;
	if (endTime < 0) 
	    endTime = Long.MAX_VALUE;  // If we would overflow, pin to MAX_VALUE

        long waitFor;
        
        ArrayList events = new ArrayList();

        validateIterator(regId, iterId);
        
        // Retrieve associated events
	try {
            ServiceRegistration reg = getServiceRegistration(regId); 
	    EventLogIterator eli = reg.iterator();
            //TODO - sep try block for "move"
            if (OPERATIONS_LOGGER.isLoggable(Level.FINEST)) {
                OPERATIONS_LOGGER.log(Level.FINEST, "Moving past lastEventCookie {0}", 
                        lastEventCookie);
            }
            
            if (lastEventCookie != null) {
                eli.moveAhead(lastEventCookie);
            }
            
            do {
               if (eli.hasNext()) {
                    //TODO - configurable logic for determining initial batch size
                    RemoteEventData[] evts = eli.readAhead(Integer.MAX_VALUE);
                    RemoteEvent evt = null;
                    for (int i=0; i < evts.length; i++) {
                        try {
                            evt = evts[i].getRemoteEvent(); 			    
                            if (evt != null && 
                                !reg.getUnknownEvents().containsKey(new EventID(evt))) {
                                events.add(evts[i]);
                            } else {
                                if(DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                                    DELIVERY_LOGGER.log(Level.FINEST, 
                                        "Problem with unknown or unrecoverable " +
                                        "RemoteEvent {0} -- skipping", evt);
                                }                                
                            }
                        } catch (ClassNotFoundException cnfe) {
                            //skip event if can't re-create it
                            if(DELIVERY_LOGGER.isLoggable(Levels.HANDLED)) {
                                DELIVERY_LOGGER.log(Levels.HANDLED, 
                                    "Problem accessing RemoteEvent -- skipping", 
                                    cnfe);
                            }
                        }                
                    }
                }
                 /* If no events currently available, wait up to timeout and try
                 * again.
                 * Note: wait(0) means wait until notified (i.e. forever) 
                 */
                waitFor = (endTime - System.currentTimeMillis());
                if ((events.size() == 0) && (waitFor > 0)) {
                    if(DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                        DELIVERY_LOGGER.log(Level.FINEST, 
                            "No available events. Attempting to wait for "
                            + waitFor + " milliseconds.");
                    }   
                   try {
                       reg.getIteratorCondition().await(timeout, TimeUnit.MILLISECONDS);
                   } catch (InterruptedException ex) {
                       Thread.currentThread().interrupt(); // restore
                       // Communication thread interrupted so just return
                       return new ArrayList(0);
                   }
                    validateIterator(regId, iterId);
                }
            } while((System.currentTimeMillis() < endTime) && (events.size() == 0));
          
        } catch (IOException ioe) {
	    // TODO (FCS)- pick better recovery algorithm
	    // Assumptions: 
	    //    - that any IOExceptions are transient in nature,
	    //      so keep trying.
	    //    - event logging state is still valid in the face
	    //      of an IOException
            if(DELIVERY_LOGGER.isLoggable(Levels.HANDLED)) {
                DELIVERY_LOGGER.log(Levels.HANDLED, 
                    "Trouble accessing event state - skipping", ioe);
            }
        } catch (NoSuchElementException nse) {
            // Shouldn't happen at this point. readAhead can throw this.
            if(DELIVERY_LOGGER.isLoggable(Level.WARNING)) {
                DELIVERY_LOGGER.log(Level.WARNING, 
                    "Invalid iterator access", nse);
            }
            throw new InvalidIteratorException("Invalid iterator access: " + nse);                    
        } catch (ClassNotFoundException cnfe) {
            // readAhead can throw this.
            if(DELIVERY_LOGGER.isLoggable(Levels.HANDLED)) {
                DELIVERY_LOGGER.log(Levels.HANDLED, 
                    "Trouble accessing remote event(s) - skipping", cnfe);
            }
        }

	// Compact the collection, if possible
	events.trimToSize();
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "getNextBatchDo", events);
	}
        return events;
    }
    
    private void validateIterator(Uuid regId, Uuid iterId) 
        throws InvalidIteratorException, ThrowThis
    {
        // Note: the following method will throw a ThrowThis exception
        // if the registration is invalid (i.e. expired or non-existent)
        ServiceRegistration reg = getServiceRegistration(regId); 
        Uuid validIterId = reg.getRemoteEventIteratorID();
        if (!iterId.equals(validIterId)) {
	    if(DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                DELIVERY_LOGGER.log(Level.FINEST, 
	            "Provided iterator id " + iterId + 
                    " doesn't match current valid iterator id " +
                    validIterId);
            }
            throw new InvalidIteratorException("Iterator is not valid");
        }        
    }
    
    /**
     * Performs the actual logic of handling received events and 
     * storing them.
     */
    private void notifyDo(Uuid registrationID, RemoteEvent theEvent) 
	throws UnknownEventException, RemoteException, ThrowThis
    {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "notifyDo",
	        new Object[] {registrationID, theEvent});
	}
	if(RECEIVE_LOGGER.isLoggable(Level.FINEST)) {
            RECEIVE_LOGGER.log(Level.FINEST, 
	        "RemoteEvent {0}, ID {1}, Seq# {2}",
	        new Object[] {theEvent, Long.valueOf(theEvent.getID()), 
	        Long.valueOf(theEvent.getSequenceNumber())});
	}


        // Note: the following method will throw a ThrowThis exception
        // if the registration is invalid (i.e. expired or non-existent)
        ServiceRegistration reg = getServiceRegistration(registrationID); 

	// Check if we need to propagate an UnknownEventException back to
	// the event sender.
	// This occurs whenever the mailbox service previously received
	// an UnknownEventException while delivering an event with
	// the same eventID for the same registration,listener pair.
	EventID evtid = new EventID(theEvent);
	if (reg.getUnknownEvents().containsKey(evtid)) {
            if(RECEIVE_LOGGER.isLoggable(Level.FINEST)) {
                RECEIVE_LOGGER.log(Level.FINEST, 
	            "Discarding event -- it is unknown");
	    }
	    // TODO (FCS)- store exception state from event target and retransmit 
	    // to event sender.
	    throw new UnknownEventException();
	}

        // Store event with appropriate registration
	try {
	    reg.iterator().add(theEvent);
	} catch (IOException ioe) {
	    // TODO (FCS)- pick better recovery algorithm
	    // Assumptions: 
	    //    - that any IOExceptions are transient in nature,
	    //      so keep trying.
	    //    - event logging state is still valid in the face
	    //      of an IOException
	}

        // Notify the notifieR if this registration is enabled
	if (reg.hasEventTarget()) {
	    concurrentObj.waiterNotify(eventNotifier);
            if(RECEIVE_LOGGER.isLoggable(Level.FINEST)) {
                RECEIVE_LOGGER.log(Level.FINEST, "Notifier notified");
            }
	} else if (reg.getRemoteEventIteratorID() != null) {
            reg.getIteratorCondition().signal();         
            if(RECEIVE_LOGGER.isLoggable(Level.FINEST)) {
                RECEIVE_LOGGER.log(Level.FINEST, "Iterator notified");
            }
        } else {
            if(RECEIVE_LOGGER.isLoggable(Level.FINEST)) {
                RECEIVE_LOGGER.log(Level.FINEST, "Notification skipped");
            }
        }

        if(RECEIVE_LOGGER.isLoggable(Level.FINEST)) {
            RECEIVE_LOGGER.log(Level.FINEST, "");
	    reg.dumpInfo(RECEIVE_LOGGER);
	}
	
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "notifyDo");
	}
    }

    @Override
    public Entry[] getServiceAttributes() throws IOException {
	return getLookupAttributes();
    }

    @Override
    public ServiceID serviceID() throws IOException {
	return new ServiceID(serviceID.getMostSignificantBits(),
		serviceID.getLeastSignificantBits());
    }

    /**
     * The notifieR thread.  This thread is responsible for delivering events
     * to client supplied notification targets when their associated 
     * registrations are enabled. 
     * <p>
     * The constructor calls <code>start()</code> so there is no need 
     * to explicitly start this thread.
     */
    // Implementation details:
    // Event delivery is done on a per registration basis.  
    // A TaskManager is used
    // to pool event delivery tasks, but there will only be one active task for
    // any one registration. This allows us to ensure "in order" delivery of 
    // events. 
    private class Notifier extends InterruptedStatusThread implements TimeConstants {
        /** 
         * <code>TaskManager</code> that will be handling the 
	 * notification tasks 
         */
        private final ExecutorService	taskManager;	

        /** wakeup manager for <code>NotifyTask</code> */
        private final WakeupManager wakeupMgr =
	    new WakeupManager(new WakeupManager.ThreadDesc(null, true));

        /** 
         * Random number generator that will be used for implementing
         * a simple load balancing scheme. Seed it with the current time.
         */
        private final Random rand = new Random(System.currentTimeMillis());
    
        /** Time to wait between notification checks */
        private final static long	PAUSE_TIME = 5000; // 5 seconds
    
        /**
         * Simple constructor.
         */
        Notifier(Configuration config) throws ConfigurationException {
    	    super("Notifier");
    	    taskManager = Config.getNonNullEntry(config,
	        MERCURY, "notificationsExecutorService",
	        ExecutorService.class, 
                new ThreadPoolExecutor(
                    10,
                    10, /* Ignored */
                    15,
                    TimeUnit.SECONDS, 
                    new LinkedBlockingQueue<Runnable>(), /* Unbounded Queue */
                    new NamedThreadFactory("EventTypeGenerator", false)
                )
            );
//TODO - defer TaskManager() creation to catch block of getEntry()
    	    //start();
        }
    
        /**
         * Schedule delivery tasks for any enabled registrations.
         */
        public void run() {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.entering(NOTIFIER_SOURCE_CLASS, 
	            "run");
            }
	    try {
		concurrentObj.writeLock();
	    } catch (ConcurrentLockException e) {
		return;
	    }

	    try {
	        while (!hasBeenInterrupted()) { 
    	            int count = pendingReg.size();
    	            if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                        DELIVERY_LOGGER.log(Level.FINEST,
			    "Notifier checking {0} possible registrations",
			    Integer.valueOf(count));
		    }
    	            // don't need to check for size() > 0 (below) since
    	            // we hold the lock. 
    	            // TODO: optimize loop to ignore indices w/o available events
    	            // TODO: optimize loop to skip nextInt if only one choice
    	            while (count-- > 0) { 
    	                int index = rand.nextInt(pendingReg.size());
    	                Uuid uuid = (Uuid)pendingReg.get(index);
    	                ServiceRegistration reg = null;
        	        try { 
                            // Note: the following method will throw a 
                            // ThrowThis exception if the registration is 
                            // invalid (i.e. expired or non-existent)
                            reg = getServiceRegistration(uuid); 
                            // Check if the registration has any events to 
                            // be delivered
    	                    if (reg.iterator().hasNext()) {
    	                        if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                                    DELIVERY_LOGGER.log(Level.FINEST,
				    "Scheduling delivery task for reg: {0} ", reg); 
				}
				// Create and schedule a event delivery task
    	                        NotifyTask t = 
				    new NotifyTask(taskManager, wakeupMgr, uuid);
    	                        taskManager.execute(t);
    	                        // Put registration onto active list
    	                        activeReg.put(uuid, t);
    	                        // Remove registration from pending list
    	                        pendingReg.remove(index);
    		            }
			} catch (ThrowThis tt) {
			    // Invalid registration ... skip
    	                    if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                                DELIVERY_LOGGER.log(Level.FINEST,
				    "Notifier: invalid registration for {0}", uuid);
			    }

			} catch (IOException ioe) {
			    // Could not access registration state  ... skip
    	                    if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                                DELIVERY_LOGGER.log(Level.FINEST,
				    "Notifier: inaccessible registration data for {0}", 
				    uuid);
			    }
			}
    		    }
		    try {
                       if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                            DELIVERY_LOGGER.log(Level.FINEST,
                                "Notifier: delayed until {0}", 
                                new Date(PAUSE_TIME + 
				    System.currentTimeMillis()));
			}
			// There is a potential for missing "notify" calls
			// so we can't just use a large timeout value.
			concurrentObj.writerWait(eventNotifier, PAUSE_TIME);
		    } catch (ConcurrentLockException e) {
			return;
		    }
		}

    	    } finally {
    	        concurrentObj.writeUnlock();
		// If exiting because of a thread interrupt,
		// it means we are shutting down so try to clean
		// up after ourselves.
		if (hasBeenInterrupted()) {
                    if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                        DELIVERY_LOGGER.log(Level.FINEST,
			    "Notifier: terminating taskManager");
		    }
		    wakeupMgr.stop();
		    wakeupMgr.cancelAll();
    	            taskManager.shutdownNow();
		} 
                if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                    DELIVERY_LOGGER.log(Level.FINEST,
		        " Notifier: exiting ...");
		}
    	    }
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(NOTIFIER_SOURCE_CLASS, 
	            "run");
	    }
        }
    }
    
    /*
     * Static variables for NotifyTask. Unfortunately, we can't have 
     * static references in an inner class (otherwise they'd be in
     * NotifyTask itself).
     */
    /** The maximum time to maintain a notification task */
    private static final long	MAX_TIME = 1 * HOURS;

    /** The maximum mnumber of times to retry event delivery */
    private final static int	MAX_ATTEMPTS = 5;	// max times to retry
    
    /**
     * A task that represents an event notification task for a particular
     * registration. A NotifyTask will be returned to the task pool after
     * one of the following has occured (which ever comes first):
     * <ul>
     * <li> The event was successfully delivered
     * <li> The task expires after <tt>MAX_TIME</tt> has elapsed
     * <li> The task quits after <tt>MAX_ATTEMPTS</tt> unsuccessful delivery 
     *      attempts
     * <li> The event could not be successfully retrieved from the 
      registration's event loG iterator.
 </ul>
     * 
     */
    class NotifyTask extends RetryTask {
        /** The Uuid of the associated registration */
        private Uuid	regID;

    	/**
    	 * Create an object to represent an event notification task.
    	 */
    	NotifyTask(ExecutorService tm, WakeupManager mgr, Uuid regID) {
    	    super(tm, mgr);
    	    this.regID = regID;
    	}

    	/**
    	 * This utility function tries to obtain the next event for the
    	 * given registration. If it finds one, the event is returned. 
    	 * Otherwise null is returned.
    	 */
    	// Note that next() will return the same event on subsequent calls
    	// unless remove() is called (indicating sucessful delivery).
    	private RemoteEvent getNextEvent(ServiceRegistration reg) {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.entering(NOTIFY_TASK_SOURCE_CLASS, 
	            "getNextEvent", reg);
	    }
    	    RemoteEvent evt = null;
	    try {
    	        if (reg.iterator().hasNext()) {
		    try {
		        evt =  (RemoteEvent)reg.iterator().next();
		    } catch (IOException ioe) {
		        //just return null in this case.
		    } catch (ClassNotFoundException cnfe) {
		        //just return null in this case.
		    }
		}
	    } catch (IOException ioe) {
		//just return null in this case.
	    }
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(NOTIFY_TASK_SOURCE_CLASS, 
	            "getNextEvent", evt);
	    }
	    return evt;
    	}

    	/**
    	 * This utility function attempts to remove the event after
    	 * from the associated event registration's storage after
    	 * 1) a successful delivery attempt
    	 * 2) task has expired
    	 * 3) the event type caused an earlier UnknownEventException
    	 * 4) the event delivery resulted in an UnknownEventException
    	 */
    	private void deleteNextEvent(ServiceRegistration reg) {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.entering(NOTIFY_TASK_SOURCE_CLASS, 
	            "deleteNextEvent", reg);
	    }
	    try {
    	        if (reg.iterator().hasNext()) {
		    try {
		        reg.iterator().remove();
		    } catch (IOException ioe) {
	                if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                            DELIVERY_LOGGER.log(Level.FINEST,
			        "NotifyTask could not "
				+ "deleteNextEvent for reg: {0}",
				reg);
			}
		    }
	        } 
	    } catch (IOException ioe) {
	        if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                    DELIVERY_LOGGER.log(Level.FINEST,
			"NotifyTask could not delete event because "
			+ "state info for {0} was inaccessible",
			reg);
		}
	    }
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(NOTIFY_TASK_SOURCE_CLASS, 
	            "deleteNextEvent");
	    }
    	}    

    	/**
    	 * This utility function attempts to disable the given registration
    	 * because there was a problem with the provided listener.
    	 */
    	private boolean disableRegistration(Uuid regID, RemoteEventListener l) {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.entering(NOTIFY_TASK_SOURCE_CLASS, 
	            "disableRegistration", new Object[] {regID, l});
	    }
    	    boolean disabled = true;
	    concurrentObj.writeLock();
	    try {
	        try {
                    // Note: the following method will throw a ThrowThis exception
                    // if the registration is invalid (i.e. expired or non-existent)
	            ServiceRegistration reg = getServiceRegistration(regID); 

	            // Check to see if the listener has changed since 
	            // the initial call to this method. If so, 
	            // assume that the new listener doesn't need to be
	            // disabled.  If not, continue with the disabling
	            // of the registration.
		    RemoteEventListener currentListener = 
		        reg.getEventTarget();
		    /* 
		     * Identity check used since re-enabling w/ same 
		     * (i.e. equals()) listener is considered a new
		     * listener for this purpose.
		     */
		    if (currentListener == l) {
		        disableDeliveryDo(regID);
		    } else {
	                if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                            DELIVERY_LOGGER.log(Level.FINEST,
			        "Disabling registration for {0}" 
	                        + " skipped due to listener change.",
				regID );
	                }
	                disabled = false;
		    }
		} catch (ThrowThis tt) {
		    // registration is gone ... ignore
		}
	    } finally {
	        concurrentObj.writeUnlock();
	    }
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(NOTIFY_TASK_SOURCE_CLASS, 
	            "disableRegistration", Boolean.valueOf(disabled));
	    }
	    return disabled;
    	}  

    	/**
    	 * Try to notify the target.  Return <code>true</code> if the
    	 * notification was successful. Return <code>false</code> otherwise.
    	 * If we return false, this task will be rescheduled at some
    	 * point in the future.
    	 */
    	public boolean tryOnce() {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.entering(NOTIFY_TASK_SOURCE_CLASS, 
	            "tryOnce");
	    }
    
    	    boolean succeeded = false;           // attempt this task again?      
    	    boolean deleteEvent = false;         // delete the retrieved event? 
    	    boolean doNotify = true;             // attempt notification?
    	    ServiceRegistration reg = null;      // 
    	    RemoteEventListener listener = null; // event delivery target
	    RemoteEvent ev = null;               // event to deliver

    	    if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                DELIVERY_LOGGER.log(Level.FINEST,
		    "Attempting event delivery for: {0} at {1}",
		        new Object[] {
		            regID,
		            new java.util.Date(System.currentTimeMillis())
		        });
	    }
    
            // Put an upper time limit on how long to keep
            // this task on the active list.  This prevents
            // slow/bad event recipients from monopolizing
            // a thread in the thread pool. This will not 
            // prevent a "malicious" event recipient from 
            // hanging up this thread if it never returns
            // from the notify() call.
	    long curTime = System.currentTimeMillis();
    	    if (curTime - startTime() > MAX_TIME) {
		succeeded = true;
		deleteEvent = false;
    	        doNotify = false;
    	        if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                    DELIVERY_LOGGER.log(Level.FINEST,
		        "Cancelling delivery due to time limit expiration.");
		}
	    } else {
                // Get required delivery information
                concurrentObj.readLock();
        	try {
        	    try { 
                        // Note: the following method will throw a 
                        // ThrowThis exception if the registration 
                        // is invalid (i.e. expired or non-existent)
                        reg = getServiceRegistration(regID); 
        		listener = reg.getEventTarget();
        		ev = getNextEvent(reg);
    
                        // Check to see if either the listener or the event 
                        // is null. If so, skip the notify() call.
                        // If the event is not null, make an 
                        // additional check to see if the event is on the 
                        // unknown event list for this registration.
                        // If so, then skip the notify() call as well.
        		if (listener == null) {
 			    succeeded = true;   // don't try again
        		    deleteEvent = false; // don't delete event
        		    doNotify = false;    // skip notify
			    if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                                DELIVERY_LOGGER.log(Level.FINEST,
		                    "Cancelling delivery because of disabled listener");
		            }
       			} else if (ev == null) {
        		    succeeded = true;    // don't try again
        		    // Note that if getNextEvent returned null
        		    // then there was a problem reading or extracting
        		    // the event. If so, the EventLog mechanism
        		    // wil have already advanced past the offending
        		    // event, so we don't have to remove it.
        		    deleteEvent = false; 
        		    doNotify = false;    // skip notify
    	                    if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                                DELIVERY_LOGGER.log(Level.FINEST,
		                    "Cancelling delivery because of null event");
			    }
			} else if (ev != null &&
			           reg.getUnknownEvents().containsKey(
			               new EventID(ev))) {
			    // If this event type caused an 
			    // UnknownEventException in the past, then
			    // delete it since it likely do so again.
        		    succeeded = true;    // don't try again
        		    deleteEvent = true;  // delete event
        		    doNotify = false;    // skip notify
    	                    if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                                DELIVERY_LOGGER.log(Level.FINEST,
		                    "Cancelling delivery because of unknown event");
			    }
        		}
		    } catch (ThrowThis tt) { // reg was not valid ... skip it
    		        succeeded = true;        // don't try again
    		        deleteEvent = false;     // don't remove event
        		doNotify = false;        // skip notify
    	                if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                            DELIVERY_LOGGER.log(Level.FINEST,
		                "Cancelling delivery because of unknown registration");
			}
    		    }
        	} finally {
                    concurrentObj.readUnlock();
        	}
	    } // end else
    	    
    	    // Important - don't hold any locks during a remote invocation
            if (doNotify) { 
	        if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                    DELIVERY_LOGGER.log(Level.FINEST,
		        "Delivering evt: {0}, ID {1}, Seq# {2}",
		            new Object[] {ev, Long.valueOf(ev.getID()), 
		            Long.valueOf(ev.getSequenceNumber())});
	        }
		try {
		    // Notify target listener and note a successful delivery
		    listener.notify(ev);
		    succeeded = true;
    		    deleteEvent = true;
    	            if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                        DELIVERY_LOGGER.log(Level.FINEST,
		            "Delivery was successful");
		    }
		} catch (UnknownEventException e) {
		    // Target wasn't expecting this event, so prevent
		    // future notifications of the same event type.
    	            if (DELIVERY_LOGGER.isLoggable(Levels.HANDLED)) {
                        DELIVERY_LOGGER.log(Levels.HANDLED,
		            "Caught UnknownEventException during notify");
	            }
	            // Add offending event type to this registration's
	            // "unknown" list and move on to the next task.
		    addUnknownEvent(regID, new EventID(ev));
		    succeeded = true;
		    deleteEvent = true;
	        } catch (Throwable t) {
		    // Categorize exception as definite or indefinite.
		    // If its indefinite, then try again (reschedule).
		    // If its definite, then drop the drop the listener
		    // and don't try again.
		    final int cat = ThrowableConstants.retryable(t);
	            if (cat == ThrowableConstants.BAD_OBJECT) {
		        // Definite remote exception means there is
		        // no possibility that a retry attempt will
		        // succeed (ex: NoSuchObjectException).
    	                if (DELIVERY_LOGGER.isLoggable(Levels.HANDLED)) {
                            DELIVERY_LOGGER.log(Levels.HANDLED,
		                "Caught a BAD_OBJECT exception during notify", t);
			}
	                // Disable event delivery for this particular
	                // target listener and move onto the next task
		        disableRegistration(regID, listener);
		        succeeded = true;
		        deleteEvent = false;
		    } else if (cat == ThrowableConstants.INDEFINITE) {
    	                if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                            DELIVERY_LOGGER.log(Level.FINEST,
		                "Caught an INDEFINITE exception during notify", t);
			}
		        // Indefinite remote exception means there is
		        // possibility that a retry attempt will succeed
		        succeeded = false;
		        deleteEvent = false;
		    } else if (cat == ThrowableConstants.BAD_INVOCATION) {
    	                if (DELIVERY_LOGGER.isLoggable(Levels.HANDLED)) {
                            DELIVERY_LOGGER.log(Levels.HANDLED,
		                "Caught a BAD_INVOCATION exception during notify", t);
			}
		        // BAD_INVOCATION exception means there is little 
			// possibility that a retry attempt with the
			// same argument will succeed.
		        succeeded = true;
		        deleteEvent = true;
		    } else { // uncategorized or bad invocation
    	                if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                            DELIVERY_LOGGER.log(Level.FINEST,
		                "Caught an uncategorized exception during notify",t);
			}
		        // uncategorized exception means there is a 
			// possibility that a retry attempt will succeed
			// Note: we've already checked for 
			// UnknownEventException
		        succeeded = false;
		        deleteEvent = false;
		    }
		} // end catch     // end catch    
	    } // end if
    
            // If we still aren't successful after MAX_ATTEMPTS
            // then give up
    	    if (!succeeded && attempt() > MAX_ATTEMPTS) {
    	        if (DELIVERY_LOGGER.isLoggable(Levels.HANDLED)) {
                    DELIVERY_LOGGER.log(Levels.HANDLED,
		        "Maximum delivery attempts reached");
		}
		succeeded = true;		
		deleteEvent = true;
	    }
    
            // Check if data structures need to be modified
            // If so, make the change(s) and notify the notifieR thread.
            if (succeeded || deleteEvent) {
                concurrentObj.writeLock();
        	try {
    		    try { 
                        // Note: the following method will throw a ThrowThis 
                        // exception if the registration is invalid 
                        // (i.e. expired or non-existent)
                        reg = getServiceRegistration(regID); 
                        if (succeeded) { 
                            // If we are still enabled, then move this reg
                            // from active --> pending list
                            if (reg.hasEventTarget()) { 
    	                        if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                                    DELIVERY_LOGGER.log(Level.FINEST,
		                        "Putting task back onto pending list");
				}
        	                activeReg.remove(regID);
        	                pendingReg.add(regID);
			    } else { // reg is disabled 
    	                        if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                                    DELIVERY_LOGGER.log(Level.FINEST,
		                        "Removing task ...");
				}
				// Note: disabling delivery should
				// have taken care of this for us.
        	                if (activeReg.remove(regID) != null ||
        	                    pendingReg.remove(regID) == true  ) {
    	                            if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                                        DELIVERY_LOGGER.log(Level.FINEST,
		                            "ERROR: Found pending/active task for a "
		                            + "disabled registration");
				    } 
// TODO (FCS)- throw AssertionError
				}
			    }
    		        }

        	        if (deleteEvent) {
    	                    if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                                    DELIVERY_LOGGER.log(Level.FINEST,
		                        "Deleting event ...");
			    }
        	            deleteNextEvent(reg); 
    		        }
    		    } catch (ThrowThis tt) { 
    		        // Registration is gone ... nothing to do
    		    }
    	            if (DELIVERY_LOGGER.isLoggable(Level.FINEST)) {
                        DELIVERY_LOGGER.log(Level.FINEST,
                            "Waking up notifier");
		    }
	            concurrentObj.waiterNotify(eventNotifier);
        	} finally {
                    concurrentObj.writeUnlock();
        	}
	    }
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(NOTIFY_TASK_SOURCE_CLASS, 
	            "tryOnce", Boolean.valueOf(succeeded));
	    }

    	    return succeeded;
    	}
    }


    /**
     * Termination thread code.  We do this in a separate thread to
     * avoid deadlock, because Activatable.inactive will block until
     * in-progress RMI calls are finished.
     */
    private class DestroyThread extends Thread {

	/** Create a non-daemon thread */
	public DestroyThread() {
	    super("DestroyThread");
	    /* override inheritance from RMI daemon thread */
	    setDaemon(false);
	}

	public void run() {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.entering(DESTROY_THREAD_SOURCE_CLASS, 
	            "run");
	    }

            synchronized (destroyLock) {

                if (destroySucceeded == true) { // someone got here first
	            if (ADMIN_LOGGER.isLoggable(Level.FINEST)) {
                        ADMIN_LOGGER.log(Level.FINEST,
			    "DestroyThread skipped ...");
	            }
                    return;
                }
/**TODO 
  * - move this block into the destroy() method and let the 
  *   remote ex pass through
  */
   	        /* must unregister before unexport */
   	        if (activationPrepared) {
   		    try {
   		        activationSystem.unregisterObject(activationID);
   		    } catch (RemoteException e) {
   		        if (ADMIN_LOGGER.isLoggable(Level.WARNING)) {
                            ADMIN_LOGGER.log(Level.WARNING, 
                                "aborting shutdown - could not unregister"
                                + " activation ID", e);
                        }
                        /* give up until we can at least unregister */
   		        return;
   		    } catch (ActivationException e) {
                        /*
                         * Activation system is shutting down or this
                         * object has already been unregistered --
                         * ignore in either case.
                         */
   		        if (ADMIN_LOGGER.isLoggable(Levels.HANDLED)) {
                            ADMIN_LOGGER.log(Levels.HANDLED, 
                                "problem shutting down - could not unregister"
                                + " activation ID", e);
                        }
   		    }
   	        }
                
                long now = System.currentTimeMillis();
                long endTime = now + maxUnexportDelay;
                if (endTime < 0) { // Check for overflow
                    endTime = Long.MAX_VALUE;
                }
	        boolean unexported = false;
/**TODO 
  * - trap IllegalStateException from unexport
  */
                while ((!unexported) && (now < endTime)) {
                    /* wait for any pending operations to complete */
                    unexported = exporter.unexport(false);
                    if (!unexported) {
                        if (ADMIN_LOGGER.isLoggable(Level.FINEST)) {
                            ADMIN_LOGGER.log(Level.FINEST, 
                                "Waiting for in-progress calls to complete");
                        }
                        try {
 			    /* Sleep for a finite time instead of yield.
			     * In most VMs yield is a no-op so if 
			     * unexport(false) is not working (say because
			     * there is a blocking query in progress) a
			     * yield here results in a very tight loop
			     * (plus there may be no other runnable threads)
			     */
			    final long sleepTime = 
				Math.min(unexportRetryDelay, endTime - now);

			    /* sleepTime must > 0, unexportRetryDelay is
			     * > 0 and if now >= end_time we would have
			     * fallen out of the loop
			     */
			    sleep(sleepTime);
			    now = System.currentTimeMillis();    
                        } catch (InterruptedException ie) {
                            if (ADMIN_LOGGER.isLoggable(Levels.HANDLED)) {
                                ADMIN_LOGGER.log(Levels.HANDLED, 
                                    "problem unexporting nicely", ie);
                            }
                            break; //fall through to forced unexport
                       }
                    } else {
                        if (ADMIN_LOGGER.isLoggable(Level.FINEST)) {
                            ADMIN_LOGGER.log(Level.FINEST, 
                                "Unexport completed");
                        }
                    }
                }

                if (!unexported) {
                    /* Attempt to forcefully export the service */
                    unexported =
                        exporter.unexport(true);
                    if (ADMIN_LOGGER.isLoggable(Level.FINEST)) {
                        ADMIN_LOGGER.log(Level.FINEST, 
                            "Forced unexport completed");
                    }
                }

   	        /* all daemons must terminate before deleting store */
   
		// Guard against multiple calls (which is undefined)
		if (joiner != null) {
   	            if (ADMIN_LOGGER.isLoggable(Level.FINEST)) {
                        ADMIN_LOGGER.log(Level.FINEST,
			    "Terminating JoinManager ...");
   	            }
   	            joiner.terminate();
		    joiner = null;
		}
		if (lookupDiscMgr != null) {
   	            if (ADMIN_LOGGER.isLoggable(Level.FINEST)) {
                        ADMIN_LOGGER.log(Level.FINEST,
			    "Terminating lookupDiscMgr ...");
   	            }
   	            lookupDiscMgr.terminate();
		    lookupDiscMgr = null;
		}
   
   	        if (ADMIN_LOGGER.isLoggable(Level.FINEST)) {
                        ADMIN_LOGGER.log(Level.FINEST,
			    "Interrupting Notifier ...");
   	        }
   	        notifier.interrupt();
   	    
   	        if (ADMIN_LOGGER.isLoggable(Level.FINEST)) {
                    ADMIN_LOGGER.log(Level.FINEST,
		        "Interrupting Expirer ...");
   	        }
   	        expirer.interrupt();

                if (snapshotter != null) { // == null in non-persistent case
   	            if (ADMIN_LOGGER.isLoggable(Level.FINEST)) {
                        ADMIN_LOGGER.log(Level.FINEST,
		            "Interrupting Snapshotter ...");
   	            }
   	            snapshotter.interrupt();
		}
   
   	        try {
//TODO - Use individual try-catch blocks		
   	            if (ADMIN_LOGGER.isLoggable(Level.FINEST)) {
                        ADMIN_LOGGER.log(Level.FINEST,
		            "Waiting for Notifier ...");
   	            }
   	            notifier.join();
   
   	            if (ADMIN_LOGGER.isLoggable(Level.FINEST)) {
                        ADMIN_LOGGER.log(Level.FINEST,
		            "Waiting for Expirer ...");
   	            }
   	            expirer.join();	        
   
                    if (snapshotter != null) { // == null in non-persistent case
   	                if (ADMIN_LOGGER.isLoggable(Level.FINEST)) {
                            ADMIN_LOGGER.log(Level.FINEST,
		            "Waiting for Snapshotter ...");
   	                }
   	                snapshotter.join();
		    }
   	        } catch (InterruptedException e) {
//TODO - Debug		
   	        }
   

                // Note: blocking getNextBatchDo() threads might still be active
                // so we need to guard against concurrent access to shared state.
                
                concurrentObj.writeLock();
                try {
                    // We don't check for expiration since any regs in the
                    // table that haven't been explicitly expired probably
                    // still have associated resources that need to be freed.
                    ServiceRegistration[] regs = 
                        (ServiceRegistration[])regByID.values().toArray(
                            new ServiceRegistration[regByID.size()]);
                    EventLogIterator logIter = null;
                    if (ADMIN_LOGGER.isLoggable(Level.FINEST)) {
                        ADMIN_LOGGER.log(Level.FINEST,
                            "Destroying {0} registration storage locations",
                             Integer.valueOf(regByID.size()));
                    }
                    for (int i=0; i < regs.length; i++) {
                       try {
                           if (PERSISTENCE_LOGGER.isLoggable(Level.FINEST)) {
                               PERSISTENCE_LOGGER.log(Level.FINEST,
                                   "Destroying logs for -> {0}", regs[i]);
                           }
                           regByID.remove(regs[i].getCookie());
                           logIter = regs[i].iterator();
                           if(logIter != null) 
                                logIter.destroy();
                       } catch (IOException ioe) {
                           if (PERSISTENCE_LOGGER.isLoggable(Levels.HANDLED)) {
                               PERSISTENCE_LOGGER.log(Levels.HANDLED,
                                   "Destroy unsuccessful.", 
                                   ioe);
                            }
                           // Did the best we could ... continue.
                       } catch (Exception de) {
                           if (PERSISTENCE_LOGGER.isLoggable(Levels.HANDLED)) {
                               PERSISTENCE_LOGGER.log(Levels.HANDLED,
                                   "Destroy unsuccessful", 
                                   de);
                           }
                       }
                       // Notify any pending iterations so they can return early.
                       // At this point the associated registration has been 
                       // removed from regByID, so any pending thread 
                       // should just return with a NoSuchObjectException.
                       regs[i].getIteratorCondition().signal();         
                        if(EXPIRATION_LOGGER.isLoggable(Level.FINEST)) {
                            EXPIRATION_LOGGER.log(Level.FINEST, "Iterator notified");
                        }
                    }
                } finally {
                    concurrentObj.writeUnlock();
                }

                // This call actually tries to delete the persistence
   	        // directory, so it has to be last.
     	        if (log != null) log.deletePersistentStore();
   
   	        if (activationID != null) {
   		    /* inactive will set current group ID to null */
   		    ActivationGroupID gid = ActivationGroup.currentGroupID();
   		    try {
   		        Activatable.inactive(activationID);
   		    } catch (RemoteException e) { // ignore
   		    } catch (ActivationException e) { // ignore
   		    }
   	        } else if (lifeCycle != null) {
		    lifeCycle.unregister(MailboxImpl.this);
		}

                if (loginContext != null) {
		    try {
			if (ADMIN_LOGGER.isLoggable(Level.FINEST)) {
			    ADMIN_LOGGER.log(Level.FINEST,
				"Logging out");
			}
			loginContext.logout();
		    } catch (Exception e) {
                        if (ADMIN_LOGGER.isLoggable(Levels.HANDLED)) {
                            ADMIN_LOGGER.log(Levels.HANDLED,
			        "Exception while logging out", 
                                e);
		        }
		    }
		}
			
   	        if (ADMIN_LOGGER.isLoggable(Level.FINEST)) {
                    ADMIN_LOGGER.log(Level.FINEST,
		        "DestroyThread finished ...");
   	        }
                destroySucceeded = true;
                
                readyState.shutdown();

	    }
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(DESTROY_THREAD_SOURCE_CLASS, 
	            "run");
	    }
	}
    }

    /** Registration expiration thread code */
    private class ExpirationThread extends InterruptedStatusThread implements TimeConstants {

	public ExpirationThread() {
	    super("ExpirationThread");
	    setDaemon(false);
            if (EXPIRATION_LOGGER.isLoggable(Level.FINEST)) {
                EXPIRATION_LOGGER.log(Level.FINEST,
		    "ExpirationThread started ...");
            }
	}

	/** 
	 * Loop over time-sorted collection and remove any entries
	 * that have expired.
	 */
	public void run() {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.entering(EXPIRATION_THREAD_SOURCE_CLASS, 
	            "run");
	    }
            ServiceRegistration reg = null;
            long delay = 0;

	    try {
		concurrentObj.writeLock();
	    } catch (ConcurrentLockException e) {
		return;
	    }

	    try {
	        while (!hasBeenInterrupted()) {
	            // Get current time
		    long now = System.currentTimeMillis();
                    if (EXPIRATION_LOGGER.isLoggable(Level.FINEST)) {
                        EXPIRATION_LOGGER.log(Level.FINEST,
			    "ExpirationThread checking regs @ {0}",
			    new Date(now));
		    }
		    // loop over time-sorted collection (by expiration) 
		    while (!regByExpiration.isEmpty()) {
		        // Collection ordered by expiration time
		        // (earliest -> latest)
                        reg = (ServiceRegistration)regByExpiration.firstKey(); 
			minRegExpiration = reg.getExpiration();
                        if (EXPIRATION_LOGGER.isLoggable(Level.FINEST)) {
                            EXPIRATION_LOGGER.log(Level.FINEST,
			        "ExpirationThread checking {0} which expires @ {1}", 
				new Object[] { reg, 
				new Date(minRegExpiration)});
                        }

			// If earliest one is in the future
			// then break out and wait until then
			if (minRegExpiration > now) 
			    break; 
			// Otherwise remove expired registration
                        removeRegistration((Uuid)reg.getCookie(), reg);
                        // Notify any pending iterations
                        reg.getIteratorCondition().signal();         
                        if(EXPIRATION_LOGGER.isLoggable(Level.FINEST)) {
                            EXPIRATION_LOGGER.log(Level.FINEST, "Iterator notified");
                        }
		    }
		    try {
		        // Calculate delay
		        delay = minRegExpiration - now;
		        delay = (delay >= 0) ? delay : Long.MAX_VALUE;
                        if (EXPIRATION_LOGGER.isLoggable(Level.FINEST)) {
                            EXPIRATION_LOGGER.log(Level.FINEST,
			        "ExpirationThread delayed until {0}",
				new Date(delay + now));
			}
			// Wait for delay (or until we get an asynchronous
			// notification from some other thread).
			// This call also releases the lock. 
			concurrentObj.writerWait(expirationNotifier, delay);
		    } catch (ConcurrentLockException e) {
			return;
		    }

		}
	    } finally {
	        concurrentObj.writeUnlock();
                if (EXPIRATION_LOGGER.isLoggable(Level.FINEST)) {
                    EXPIRATION_LOGGER.log(Level.FINEST,
		       "ExpirationThread exiting ...");
	        }
            }

            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(EXPIRATION_THREAD_SOURCE_CLASS, 
	            "run");
	    }
	}
    } // End ExpirationThread


    /** 
     * Adaptor class implementation of LocalLandlord.  We use this
     * adaptor class with LandlordUtil because we want to only acquire
     * the concurrentObj.writeLock() once per batch lease operation.
     */
    private class LocalLandlordAdaptor implements LocalLandlord {
	// inherit javadoc from supertype
	public long renew(Uuid cookie, long extension)
	    throws LeaseDeniedException, UnknownLeaseException 
	{
	    return renewDo(cookie, extension);
	}
       
	// inherit javadoc from supertype
	public void cancel(Uuid cookie) throws UnknownLeaseException {
	    cancelDo(cookie);
	}
    }


    ////////////////////////////////
    // Persistence Methods/Classes
    ////////////////////////////////

    /**
     * Interface defining the method(s) that must be implemented by each of
     * the concrete LogObj classes. This allows for the definition of
     * object-dependent invocations of the appropriate implementation of
     * the method(s) declared in this interface.  
     */
    private static interface LogRecord extends Serializable {
	void apply(MailboxImpl mb);
    }


    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a new registration is created.
     */
    private static class RegistrationLogObj implements LogRecord {

	private static final long serialVersionUID = 1L;

	/**
	 * The registration.
	 *
	 * @serial
	 */
	private ServiceRegistration reg;

	/** Simple constructor */
	public RegistrationLogObj(ServiceRegistration reg) {
	    this.reg = reg;
	}

	/**
	 * Adds the stored registration object to the mailbox's state.
	 */
	public void apply(MailboxImpl mb) {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.entering(REGISTRATION_LOG_OBJ_SOURCE_CLASS, 
	            "apply", mb);
            }
	    if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                RECOVERY_LOGGER.log(Level.FINEST, 
		    "Applying a {0}", getClass().getName());
	        reg.dumpInfo(RECOVERY_LOGGER);
	    }
	    mb.addRegistration(reg);
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(REGISTRATION_LOG_OBJ_SOURCE_CLASS, 
	            "apply");
	    }
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a registration is enabled.
     */
    private static class RegistrationEnabledLogObj implements LogRecord {

	private static final long serialVersionUID = 1L;

	/**
	 * The registration ID.
	 *
	 * @serial
	 */
	private Uuid regID;

	/**
	 * The registration ID.
	 *
	 * @serial
	 */
	private transient RemoteEventListener target;

        /** Simple constructor */
        public RegistrationEnabledLogObj(Uuid id, RemoteEventListener l) {
            regID = id;
            target = l;
        }

	/**
	 * @serialData RemoteEventListener as a MarshalledObject
	 */
	private void writeObject(ObjectOutputStream stream)
	    throws IOException
	{
	    stream.defaultWriteObject();
	    stream.writeObject(new MarshalledInstance(target).convertToMarshalledObject());
	}

	/**
	 * Unmarshals the event listener.
	 */
	private void readObject(ObjectInputStream stream)
	    throws IOException, ClassNotFoundException
	{
	    stream.defaultReadObject();
	    MarshalledObject mo = (MarshalledObject)stream.readObject();
	    try {
		target = (RemoteEventListener) new MarshalledInstance(mo).get(false);
	    } catch (Throwable e) {
		if (e instanceof Error &&
		    !(e instanceof LinkageError ||
		      e instanceof OutOfMemoryError ||
		      e instanceof StackOverflowError))
		    throw (Error)e;
	    }
	}

	/**
	 * Enables the stored RemoteEventListener object with the
	 * associated, stored registration.
	 */
	public void apply(MailboxImpl mb) {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.entering(REGISTRATION_ENABLED_LOG_OBJ_SOURCE_CLASS, 
	            "apply", mb);
            }
	    if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                RECOVERY_LOGGER.log(Level.FINEST, 
		    "Applying a {0}, regID = {1}, target = {2}", 
		    new Object[] {getClass().getName(), regID, target});
	    }
	    try {
	        if (target != null) {
                    /* 
	             * Notes: 
		     * 1. The following method will throw a ThrowThis 
	             * exception if the registration is invalid 
	             * (i.e. expired or non-existent).
		     * 2. It is assumed that subsequent recovery logic
		     * will call restoreTransientState() on the 
		     * associated ServiceRegistration so that the
		     * recovered target gets re-prepared.
	             */
	            mb.enableRegistration(regID, target);
		} else {
	            if (RECOVERY_LOGGER.isLoggable(Levels.HANDLED)) {
                        RECOVERY_LOGGER.log(Levels.HANDLED, 
			"{0} cancelled due to null target",
			getClass().getName());
		    }
		}
		    
	    } catch (ThrowThis tt) {
	        // Ignore - expired registration can occur on recovery
	        if (RECOVERY_LOGGER.isLoggable(Levels.HANDLED)) {
                    RECOVERY_LOGGER.log(Levels.HANDLED,
		    "{0} Null or expired registration entry",
		    getClass().getName());
		}
//TODO - Throw AssertionError if null registration, which shouldn't happen 		
	    }
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(REGISTRATION_ENABLED_LOG_OBJ_SOURCE_CLASS, 
	            "apply");
	    }
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a registration is disabled.
     */
    private static class RegistrationDisabledLogObj implements LogRecord {

	private static final long serialVersionUID = 1L;

	/**
	 * The registration ID.
	 *
	 * @serial
	 */
	private Uuid regID;

	/** Simple constructor */
        public RegistrationDisabledLogObj(Uuid id) {
            regID = id;
        }

	/**
	 * Disables the associated, stored registration.
	 */
	public void apply(MailboxImpl mb) {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.entering(REGISTRATION_DISABLED_LOG_OBJ_SOURCE_CLASS, 
	            "apply", mb);
	    }
	    if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                RECOVERY_LOGGER.log(Level.FINEST, 
                    "Applying a {0}, regID = {1}",
		    new Object[] { getClass().getName(), regID});
	    }
	    try {
	        mb.disableRegistration(regID);
	    } catch (ThrowThis tt) {
// TODO (FCS)- throw internal exception since this shouldn't happen
	    }
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(REGISTRATION_DISABLED_LOG_OBJ_SOURCE_CLASS, 
	            "apply");
            }
	}
    }
    
    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a registration iterator is enabled.
     */
    private static class RegistrationIteratorEnabledLogObj implements LogRecord {

	private static final long serialVersionUID = 1L;

	/**
	 * The registration ID.
	 *
	 * @serial
	 */
	private Uuid regID;

	/**
	 * The iterator ID.
	 *
	 * @serial
	 */
	private Uuid iterID;
        
	/** Simple constructor */
        public RegistrationIteratorEnabledLogObj(Uuid rid, Uuid iid) {
            regID = rid;
            iterID = iid;
        }

	/**
	 * Disables the associated, stored registration.
	 */
	public void apply(MailboxImpl mb) {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.entering(REGISTRATION_ITERATOR_ENABLED_LOG_OBJ_SOURCE_CLASS, 
	            "apply", mb);
	    }
	    if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                RECOVERY_LOGGER.log(Level.FINEST, 
                    "Applying a {0}, regID = {1}, iterID = {2}",
		    new Object[] { getClass().getName(), regID, iterID});
	    }
	    try {
	        mb.enableRegistrationIterator(regID, iterID);
	    } catch (ThrowThis tt) {
// TODO (FCS)- throw internal exception since this shouldn't happen
	    }
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(REGISTRATION_ITERATOR_ENABLED_LOG_OBJ_SOURCE_CLASS, 
	            "apply");
            }
	}
    }
    
    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the set of groups to join is changed.
     */
    private static class LookupGroupsChangedLogObj implements LogRecord {

	private static final long serialVersionUID = 1L;

	/**
	 * The new groups to join.
	 *
	 * @serial
	 */
	private String[] groups;

	/** Simple constructor */
	public LookupGroupsChangedLogObj(String[] groups) {
	    this.groups = groups;
	}

	/**
	 * Sets the value of <code>lookupGroups</code> to the provided
         * set.
	 */
	public void apply(MailboxImpl mb) {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.entering(LOOKUP_GROUPS_CHANGED_LOG_OBJ_SOURCE_CLASS, 
	            "apply", mb);
	    }
	    if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                RECOVERY_LOGGER.log(Level.FINEST, 
                    "Applying a {0}", getClass().getName());
	        mb.dumpGroups(groups, RECOVERY_LOGGER, Level.FINEST);
    	    }
	    mb.lookupGroups = groups;
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(LOOKUP_GROUPS_CHANGED_LOG_OBJ_SOURCE_CLASS, 
	            "apply");
	    }
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the set of locators of lookup services to join is changed.
     */
    private static class LookupLocatorsChangedLogObj implements LogRecord {

	private static final long serialVersionUID = 1L;

	/**
	 * The new locators to join.
	 *
	 * @serial
	 */
	private LookupLocator[] locators;

	/** Simple constructor */
	public LookupLocatorsChangedLogObj(LookupLocator[] locators) {
	    this.locators = locators;
	}

	/**
	 * Sets <code>lookupLocators</code> to value of the provided
	 * set.
	 */
	public void apply(MailboxImpl mb) {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.entering(LOOKUP_LOCATORS_CHANGED_LOG_OBJ_SOURCE_CLASS, 
	            "apply", mb);
	    }
	    if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                RECOVERY_LOGGER.log(Level.FINEST, 
                    "Applying a {0}", getClass().getName());
	        mb.dumpLocators(locators, RECOVERY_LOGGER, Level.FINEST);
	    }
	    mb.lookupLocators = locators;
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(LOOKUP_LOCATORS_CHANGED_LOG_OBJ_SOURCE_CLASS, 
	            "apply");
	    }
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the set of lookup attributes is augmented.
     */
    private static class AttrsAddedLogObj implements LogRecord {
	private static final long serialVersionUID = 1L;

        /** 
         * The attributes to be added to each lookup service with which
         * this service is registered, written out in marshalled form.
         *
         * @serial
         */
        private MarshalledObject[] marshalledAttrs;

        /** Constructs this class and stores the attributes that were added */
        public AttrsAddedLogObj(Entry[] attrs) {
            this.marshalledAttrs = marshalAttributes(attrs);
        }

        /** 
         * Modifies this service's state by adding (after unmarshalling) the
         * elements of marshalledAttrs to the service's existing set of
         * attributes.
         *
         *  @see MailboxImpl.LocalLogHandler#applyUpdate
         */
        public void apply(MailboxImpl mb) {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.entering(ATTRS_ADDED_LOG_OBJ_SOURCE_CLASS, 
	            "apply", mb);
	    }
	    if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                RECOVERY_LOGGER.log(Level.FINEST, 
                    "Applying a {0}", getClass().getName());
	    }
            Entry[] attrs = unmarshalAttributes(marshalledAttrs);
            mb.lookupAttrs = LookupAttributes.add(mb.lookupAttrs,attrs);
	    if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                RECOVERY_LOGGER.log(Level.FINEST, 
                    "Added the attributes:");
	        dumpAttrs(attrs, RECOVERY_LOGGER, Level.FINEST);
	    }
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(ATTRS_ADDED_LOG_OBJ_SOURCE_CLASS, 
	            "apply");
	    }
        }
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the set of lookup attributes is modified.
     */
    private static class AttrsModifiedLogObj implements LogRecord {
	private static final long serialVersionUID = 1L;

	/** 
	 * The set of attribute templates used to select the attributes (from
         * the service's existing set of attributes) that were modified,
         * written out in marshalled form.
         * 
	 * @serial
	 */
        private MarshalledObject[] marshalledAttrTmpls;

        /** 
         * The attributes with which this service's existing attributes 
         * were modified, written out in marshalled form.
         *
         *  @serial
         */
        private MarshalledObject[] marshalledModAttrs;

        /** 
         * Constructs this class and stores the modified attributes 
         * along with the corresponding set of templates.
         */
        public AttrsModifiedLogObj(Entry[] attrTmpls, Entry[] modAttrs) {
            this.marshalledAttrTmpls = marshalAttributes(attrTmpls);
            this.marshalledModAttrs = marshalAttributes(modAttrs);
        }

        /** 
         * Modifies this service's state by modifying (after unmarshalling)
         * the service's existing attributes according to the contents of
         * marshalledAttrTmpls and marshalledModAttrs.
         *
         *  @see MailboxImpl.LocalLogHandler#applyUpdate
         */
        public void apply(MailboxImpl mb) {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.entering(ATTRS_MODIFIED_LOG_OBJ_SOURCE_CLASS, 
	            "apply", mb);
	    }
	    if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                RECOVERY_LOGGER.log(Level.FINEST, 
                    "Applying a {0}", getClass().getName());
	        RECOVERY_LOGGER.log(Level.FINEST, "Attributes (before):");
	        dumpAttrs(mb.lookupAttrs, RECOVERY_LOGGER, Level.FINEST);
	    }
            Entry[] attrTmpls = unmarshalAttributes
                                            (marshalledAttrTmpls);
            Entry[] modAttrs  = unmarshalAttributes
                                            (marshalledModAttrs);
            mb.lookupAttrs = LookupAttributes.modify(mb.lookupAttrs,
                                                     attrTmpls, modAttrs);
	    if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                RECOVERY_LOGGER.log(Level.FINEST, 
                    "Attributes (after):");
	        dumpAttrs(mb.lookupAttrs, RECOVERY_LOGGER, Level.FINEST);
	    }
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(ATTRS_MODIFIED_LOG_OBJ_SOURCE_CLASS, 
	            "apply");
            }
        }
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a registration is renewed.
     */
    private static class RegistrationRenewedLogObj implements LogRecord {

	private static final long serialVersionUID = 1L;

	/**
	 * The unique id of the associated ServiceRegistration. 
	 *
	 * @serial
	 */
	private Uuid regID;

	/**
	 * The absolute time granted for this renewal.
	 *
	 * @serial
	 */
	private long expirationTime;

	/** Simple constructor */
	public RegistrationRenewedLogObj(Uuid id, long time) {
	    regID = id;
	    expirationTime = time;
	}

	/**
	 * Sets the expiration time of the associated registration.
	 */
	public void apply(MailboxImpl mb) {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.entering(REGISTRATION_RENEWED_LOG_OBJ_SOURCE_CLASS, 
	            "apply", mb);
	    }
	    if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                RECOVERY_LOGGER.log(Level.FINEST, 
                    "Applying a {0}", getClass().getName());
	        RECOVERY_LOGGER.log(Level.FINEST, 
                    "Reg: {0} will expire at {1}", 
	            new Object[] {regID, new Date(expirationTime)});
	    }

            // Note: The registration might be expired at this point
            // but we can't be sure until the recovery process is
            // complete (eg there might be another renewal loG for this
            // registration later on in the recovery process).
            ServiceRegistration reg = 
                (ServiceRegistration)mb.regByID.get(regID);
            if (reg != null) { 
                reg.setExpiration(expirationTime);
	    } else {
	        if (RECOVERY_LOGGER.isLoggable(Levels.HANDLED)) {
                    RECOVERY_LOGGER.log(Levels.HANDLED, 
                        "*** Registration not renewed - not found");
		}
// TODO (FCS)- throw internal exception
	    }
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(REGISTRATION_RENEWED_LOG_OBJ_SOURCE_CLASS, 
	            "apply");
	    }
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a registration is cancelled.
     */
    private static class RegistrationCancelledLogObj implements LogRecord {

	private static final long serialVersionUID = 1L;

	/**
	 * The unique id of the associated ServiceRegistration. 
	 *
	 * @serial
	 */
	private Uuid regID;

	/** Simple constructor */
	public RegistrationCancelledLogObj(Uuid id) {
	    regID = id;
	}

	/**
	 * Sets the expiration time of the associated registration.
	 */
	public void apply(MailboxImpl mb) {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.entering(REGISTRATION_CANCELLED_LOG_OBJ_SOURCE_CLASS, 
	            "apply", mb);
	    }
	    if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                RECOVERY_LOGGER.log(Level.FINEST, 
                    "Applying a {0}", getClass().getName());
	        RECOVERY_LOGGER.log(Level.FINEST, 
                    "Cancelling Reg: {0}", regID);
	    }

            ServiceRegistration reg = 
                (ServiceRegistration)mb.regByID.get(regID);
            mb.removeRegistration(regID, reg);
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(REGISTRATION_CANCELLED_LOG_OBJ_SOURCE_CLASS, 
	            "apply");
	    }
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * an UnknownEventException is received during event notification. 
     * The event's EventID is associated with the given registration.
     */
    private static class UnknownEventExceptionLogObj implements LogRecord {

	private static final long serialVersionUID = 1L;

	/**
	 * The unique id of the associated ServiceRegistration. 
	 *
	 * @serial
	 */
	private Uuid regID;

	/**
	 * The internal representation of the remote event which
	 * caused the UnknownEventException.
	 *
	 * @serial
	 */
	private EventID evtID;

        /** Simple constructor */
        public UnknownEventExceptionLogObj(Uuid id, EventID eid) {
            regID = id;
            evtID = eid;
        }

	/**
	 * Adds the given <code>EventID</code> to the set of unknownEvents
	 * for the associated registration.
	 */
	public void apply(MailboxImpl mb) {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.entering(UNKNOWN_EVENT_EXCEPTION_LOG_OBJ_SOURCE_CLASS, 
	            "apply", mb);
	    }
	    if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                RECOVERY_LOGGER.log(Level.FINEST, 
                    "Applying a {0}", getClass().getName());
	        RECOVERY_LOGGER.log(Level.FINEST, 
                    "Adding: {0} to {1}",
		    new Object[] {evtID, regID});
	    }
	    ServiceRegistration reg = 
	        (ServiceRegistration)mb.regByID.get(regID);
	    reg.getUnknownEvents().put(evtID, evtID);
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(UNKNOWN_EVENT_EXCEPTION_LOG_OBJ_SOURCE_CLASS, 
	            "apply");
	    }
	}
	
    }

    /**
     * Marshals each element of the <code>Entry[]</code> array parameter.
     * This method is <code>static</code> so that it may called from
     * the <code>static</code> <code>LogRecord</code> classes when a set
     * of attributes is being logged to persistent storage.
     *
     * @param attrs       <code>Entry[]</code> array consisting of the
     *                    attributes to marshal
     * @return array of <code>MarshalledObject[]</code>, where each element
     *         corresponds to an attribute in marshalled form 
     */
    private static MarshalledObject[] marshalAttributes(Entry[] attrs) {
        if(attrs == null) 
            return new MarshalledObject[0];

        ArrayList marshalledAttrs = new ArrayList();
        for(int i=0; i<attrs.length; i++) {
            /* 
             * Marshalling errors should not prevent the service from
             * working, so just ignore them.
             */
            try {
                marshalledAttrs.add(
                    new MarshalledInstance(attrs[i]).convertToMarshalledObject());
            } catch(Throwable e) {
	        if (RECOVERY_LOGGER.isLoggable(Levels.HANDLED)) {
                    RECOVERY_LOGGER.log(Levels.HANDLED, 
                        "Error while marshalling attribute[{0}]: {1}",
			new Object[] {Integer.valueOf(i), attrs[i]});
                    RECOVERY_LOGGER.log(Levels.HANDLED, 
                        "Marshalling exception",e);
	        }
            }
        }
        return ((MarshalledObject[])(marshalledAttrs.toArray
                             (new MarshalledObject[marshalledAttrs.size()])));
    }

    /**
     * Unmarshals each element of the <code>MarshalledObject</code> array
     * parameter. This method is <code>static</code> so that it may called
     * from the <code>static</code> <code>LogRecord</code> classes when a
     * set of attributes is being recovered from persistent storage.
     *
     * @param marshalledAttrs <code>MarshalledObject</code> array consisting
     *                        of the attributes to unmarshal
     * @return array of <code>Entry[]</code>, where each element corresponds
     *         to an attribute that was successfully unmarshalled
     */
    private static Entry[] unmarshalAttributes( 
	MarshalledObject[] marshalledAttrs)
    {
        if(marshalledAttrs == null) 
            return new Entry[0];

        ArrayList attrs = new ArrayList();
        for(int i=0; i<marshalledAttrs.length; i++) {
            /* 
             * Unmarshalling errors should not prevent the service from
             * working, so just ignore them.
             */
            try {
                attrs.add( (Entry)( new MarshalledInstance(marshalledAttrs[i]).get(false) ) );
            } catch(Throwable e) {
	        if (RECOVERY_LOGGER.isLoggable(Levels.HANDLED)) {
                    RECOVERY_LOGGER.log(Levels.HANDLED, 
                        "Error while unmarshalling attribute[{0}]: {1}",
			new Object [] {
			    Integer.valueOf(i), marshalledAttrs[i]});
                    RECOVERY_LOGGER.log(Levels.HANDLED, 
		        "Exception was", e);
	        }
            }
        }
        return ((Entry[])(attrs.toArray(new Entry[attrs.size()])));
    }

    /**
     * Add a state-change record to persistent storage.
     * <p>
     * Whenever a significant change occurs to the Mercury's state, this
     * method is invoked to record that change in a file called a log file.
     * Each record written to the log file is an object reflecting both
     * the data used and the ACTIONS taken to make one change to the
     * Mercury's state at a particular point in time. If the number of
     * records contained in the log file exceeds the pre-defined threshold,
     * a snapshot of the current state of the Mercury will be recorded.
     * <p>
     * Whenever one of the following state changes occurs, this method
     * will be invoked with the appropriate implementation of the
     * LogRecord interface as the input argument.
     * <ul>
     * <li> registration creation
     * <li> registration renewal
     * <li> registration expired/cancellation
     * <li> registration enabled
     * <li> registration disabled
     * <li> An UnknownEventException is received during event delivery
     * <li> service's set of lookup attributes is modified
     * <li> service's set of lookup groups is modified
     * <li> service's set of lookup locators is modified
     * <li> service's service ID is assigned
     * </ul>
     * 
     * @see MailboxImpl.LocalLogHandler
     */
    private void addLogRecord(LogRecord rec) {
	if(log == null) return;//not persistent, don't log
	
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "addLogRecord", rec);
	}
	try {
	    if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                RECOVERY_LOGGER.log(Level.FINEST, 
                    "Adding log record: {0}", rec);
	    }
	    log.update(rec, true);
	    if (++logFileSize >= logToSnapshotThreshold) {
	        if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                    RECOVERY_LOGGER.log(Level.FINEST, 
                        "Notifying snapshot thread");
	        }
                concurrentObj.waiterNotify(snapshotNotifier);
	    }
	} catch (Exception e) {
	    /* if log updating fails, then one of the following must be done:
	     *   -- output the problem to a separate file and exit()
	     *   -- output the problem to a separate file and continue
	     *   -- set an "I have a problem" attribute & send notification
	     * XXX this issue will be addressed at a later time
	     */
            if (RECOVERY_LOGGER.isLoggable(Levels.HANDLED)) {
                RECOVERY_LOGGER.log(Levels.HANDLED, 
                    "Exception adding LogRecord", e);
	    }
	}
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "addLogRecord");
	}
    }


    /**
     * Write the current state of the Mercury to persistent storage.
     * <p>
     * A 'snapshot' of the Mercury's current state is represented by
     * the data contained in certain fields of the Mercury. That data
     * represents many changes -- over time -- to the Mercury's state.
     * This method will record that data to a file referred to as the
     * snapshot file.
     * <p>
     * The data written by this method to the snapshot file -- as well as
     * the format of the file -- is shown below:
     * <ul>
     * <li> our class name
     * <li> log format version number
     * <li> our service ID
     * <li> current proxy ID
     * <li> current configuration parameters
     * <li> current service attributes
     * <li> current registration state
     * </ul>
     * Each data item is written to the snapshot file in serialized form.
     *
     * Note that event state is kept separately and maintained by the 
     * event logging mechanism.
     */
    //@see Mailbox.LocalLogHandler
    private void takeSnapshot(OutputStream  out) throws IOException {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "takeSnapshot", out);
	}
	if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                RECOVERY_LOGGER.log(Level.FINEST, 
                    "Taking snapshot ...");
	}
	ObjectOutputStream stream = new ObjectOutputStream(out);

	stream.writeUTF(MAILBOX_SOURCE_CLASS);
	stream.writeInt(LOG_VERSION);
	stream.writeObject(serviceID);
	stream.writeObject(lookupGroups);
	stream.writeObject(lookupLocators);
        stream.writeObject(marshalAttributes(lookupAttrs));
	stream.writeObject(regByID);
	stream.flush();
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "takeSnapshot");
	}
    }

    /**
     * Retrieve the contents of the snapshot file and reconstitute the 'base'
     * state of the Mercury from the retrieved data.
     * <p>
     * The data retrieved by this method from the snapshot file is shown
     * below:
     * <ul>
     * <li> our class name
     * <li> loG format version number
 <li> our service ID
     * <li> our proxy ID
     * <li> our configuration parameters
     * <li> our service attributes
     * <li> our registration state
     * </ul>
     * During recovery, the state of the Mailbox at the time of a crash
     * or outage is re-constructed by first reconstituting the 'base state'
     * from the snapshot file; and then modifying that base state according
     * to the records retrieved from the log file. This method is invoked to
     * perform the first step in that reconstruction. 
     */
    // @see Mailbox.LocalLogHandler
    private void recoverSnapshot(InputStream in)
	throws IOException, ClassNotFoundException
    {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "recoverSnapshot", in);
	}
	if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
                RECOVERY_LOGGER.log(Level.FINEST, 
                    "Recovering snapshot ...");
	}
	ObjectInputStream stream = new ObjectInputStream(in);
	if (!MAILBOX_SOURCE_CLASS.equals(stream.readUTF()))
	    throw new IOException("log from wrong implementation");
	if (stream.readInt() != LOG_VERSION)
	    throw new IOException("wrong log format version");
	serviceID = (Uuid)stream.readObject();
	lookupGroups = (String[])stream.readObject();
	lookupLocators = (LookupLocator[])stream.readObject();
        MarshalledObject[] marshalledAttrs
                                    = (MarshalledObject[])stream.readObject();
	lookupAttrs = unmarshalAttributes(marshalledAttrs);
	Map<Uuid, ServiceRegistration> regByID = (HashMap<Uuid, ServiceRegistration>)stream.readObject();
        Iterator<ServiceRegistration> it = regByID.values().iterator();
        while(it.hasNext()){
            ServiceRegistration reg = it.next();
            if (reg != null) reg.setCondition(concurrentObj.newCondition());
        }
        this.regByID.clear();
        this.regByID.putAll(regByID);
	if (RECOVERY_LOGGER.isLoggable(Level.FINEST)) {
            RECOVERY_LOGGER.log(Level.FINEST, 
                "serviceID: {0}", serviceID);
	    RECOVERY_LOGGER.log(Level.FINEST, 
                "lookupGroups:");
	    dumpGroups(lookupGroups, RECOVERY_LOGGER, Level.FINEST);
	    RECOVERY_LOGGER.log(Level.FINEST, 
                "lookupLocators:");
	    dumpLocators(lookupLocators, RECOVERY_LOGGER, Level.FINEST);
	    RECOVERY_LOGGER.log(Level.FINEST, 
                "lookupAttributes:");
	    dumpAttrs(lookupAttrs, RECOVERY_LOGGER, Level.FINEST);
	    Collection regs = regByID.values();
	    Iterator iter = regs.iterator();
	    while (iter.hasNext()) {
	        ServiceRegistration reg = (ServiceRegistration)iter.next();
	        reg.dumpInfo(RECOVERY_LOGGER);
	    }
	}
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "recoverSnapshot");
	}
    }


    /**
     * Handler class for the persistent storage facility.
     * <p>
     * At any point during processing in the mailbox, there will exist
     * both a 'snapshot' of the mailbox's state and a set of records
     * detailing each significant change that has occurred to the state
     * since the snapshot was taken. The snapshot information and the
     * incremental change information will be stored in separate files
     * called, respectively, the snapshot file and the log file. Together,
     * these files are used to recover the state of the mailbox after a
     * crash or a network outage (or if the mailbox or its ActivationGroup
     * is un-registered and then re-registered through the activation daemon).
     * <p>
     * This class contains the methods that are used to record and recover
     * the snapshot of the mailbox's state; as well as the method used to
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
     * Each significant change to the mailbox's state is written to the
     * log file as an individual record (when addLogRecord() is invoked).
     * After the number of records logged exceeds a pre-defined threshold,
     * a snapshot of the state is recorded by invoking -- through the
     * ReliableLog and its LogHandler -- the snapshot() method defined in
     * this class. After the snapshot is taken, the log file is cleared
     * and the incremental log process starts over.
     * <p>
     * The contents of the snapshot file reflect the DATA contained in
     * the fields making up the current state of the mailbox. That data
     * represents many changes -- over time -- to the mailbox's state.
     * On the other hand, each record written to the log file is an object
     * that reflects both the data used and the ACTIONS taken to make one
     * change to the mailbox's state at a particular point in time.
     * <p>
     * During recovery, the state of the mailbox at the time of a crash
     * or outage is re-constructed by first retrieving the 'base' state from
     * the snapshot file; and then modifying that base state according to
     * the records retrieved from the log file. The reconstruction of the
     * base state is achieved by invoking the recover() method defined in
     * this class. The modifications recorded in the log file are then
     * applied to the base state by invoking the applyUpdate() method
     * defined in this class. Both recover() and applyUpdate() are invoked
     * through the ReliableLog and its associated LogHandler.
     * <p>
     * NOTE: The following lines must be added to the mailbox's policy file
     * <pre>
     *     permission java.io.FilePermission "dirname",   "read,write,delete";
     *     permission java.io.FilePermission "dirname/-", "read,write,delete";
     * </pre>
     *     where 'dirname' is the name of the directory path (relative or
     *     absolute) where the snapshot and log file will be maintained.
     */
    private class LocalLogHandler extends LogHandler {
	
	/** Simple constructor */
	public LocalLogHandler() { }

	/* Overrides snapshot() defined in ReliableLog's LogHandler class. */
	public void snapshot(OutputStream out) throws IOException {
	    takeSnapshot(out);
	}

	/* Overrides recover() defined in ReliableLog's LogHandler class. */
	public void recover(InputStream in)
	    throws IOException, ClassNotFoundException
	{
	    recoverSnapshot(in);
	}

	/**
	 * Required method implementing the abstract applyUpdate()
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
	 * will then modify the state of the mailbox in a way dictated
	 * by the type of record that was retrieved.
	 */
	public void applyUpdate(Object logRecObj) {
	    ((LogRecord)logRecObj).apply(MailboxImpl.this);
	}
    }

    /**
     * Snapshot-taking thread. 
     * <p>
     * A snapshot is taken when -- after writing a new record to the 
     * log file -- it is determined that the size of the log file has 
     * exceeded a certain threshold. The code which adds the new record 
     * to the log file and which, in turn, decides that a snapshot
     * must be taken is "wrapped" in a writer mutex. That is, synchronization
     * of processing is achieved in the mailbox through a "reader/writer"
     * mutex construct. This construct allows only one writer at any one
     * time; but allows an unlimited number of simultaneous readers as
     * long as no writer has locked the mutex. During steady-state, it is
     * anticipated that there will be far more readers in use 
     * than writers. Since the process of
     * taking a snapshot can be time-consuming, if the whole snapshot-taking
     * process occupies that single writer mutex, then a significant number
     * of read requests will be un-necessarily blocked; possibly resulting
     * in an unacceptable degradation in response time. 
     * <p>
     * It is for the above reason that the process of taking a snapshot is
     * performed in a separate thread. The thread waits on the monitor
     * belonging to the snapshotNotifier instance until it is notified
     * (or "signaled") that a snapshot must be taken. The notification
     * is sent by another thread, created by the mailbox, which
     * determines when the conditions are right for a snapshot. The
     * notification takes the form of an interrupt indicating that the
     * snapshot monitor is available. Although the interrupt is sent 
     * while the writer mutex is locked, the act of sending the notification
     * is less time-consuming than the act of taking the snapshot itself.
     * When the thread receives a notification, it awakens and requests a
     * lock on the reader mutex (this is all done in the readerWait() method).
     * Because a reader -- not a writer -- mutex is locked, read-only
     * processes still have access to the system state performed; 
     * and the reader mutex prevents changes to the data while
     * the snapshot is in progress.  
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

        /**
         * Ensure thread is non daemon to avoid jvm terminating it during
         * a snapshot.
         */
	public SnapshotThread() {
	    super("SnapshotThread");
	    setDaemon(false);
	}

	public void run() {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.entering(SNAPSHOT_THREAD_SOURCE_CLASS, 
	            "run");
	    }
	    try {
		concurrentObj.readLock();
	    } catch (ConcurrentLockException e) {
		return;
	    }
	    try {
		while (!hasBeenInterrupted()) {
		    try {
			concurrentObj.readerWait(snapshotNotifier,
						 Long.MAX_VALUE);
                        try {
                            // Take snapshot
                            log.snapshot();
                            // Reset loG count
		            logFileSize = 0;
	                } catch (InterruptedIOException e) {
			    return;
	                } catch (Exception e) {
			    if (e instanceof LogException &&
				((LogException)e).detail instanceof
				InterruptedIOException)
				return;
	                    /* XXX
	                     * if taking the snapshot fails for any reason,
                             * then one of the following must be done:
	                     *   -- output the problem to a file and exit
	                     *   -- output the problem to a file and continue
	                     *   -- set an "I have a problem" attribute and
                             *      then send a notification
	                     * this issue will be addressed at a later time
	                     */
			    if (PERSISTENCE_LOGGER.isLoggable(Levels.HANDLED)) {
                                PERSISTENCE_LOGGER.log(Levels.HANDLED, 
				    "Exception taking snapshot", e);
		            }
		        }
		    } catch (ConcurrentLockException e) {
			return;
		    }
		}
	    } finally {
		concurrentObj.readUnlock();
	    }
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(SNAPSHOT_THREAD_SOURCE_CLASS, 
	            "run");
	    }
	}
    }


    /** Utility method for displaying lookup group attributes */
    static void dumpGroups(String[] grps, Logger logger, Level level) {
        if (logger.isLoggable(level)) {
            if (grps == null)
                logger.log(level, "<ALL_GROUPS>");
            else if (grps.length == 0)
                logger.log(level, "<NO_GROUPS>");
            else {
                String[] dups = new String[grps.length];
                for (int i=0; i < grps.length; i++) {
                    if ("".equals(grps[i])) {
                        dups[i] = "<PUBLIC_GROUP>";
                    } else {
                        dups[i] = grps[i];
                    }
                }
                logger.log(level, "Groups: {0}" , Arrays.asList(dups));
            }
        }
    }

    /** Utility method for displaying lookup locator attributes */
    static void dumpLocators(LookupLocator[] locs, Logger logger, Level level) {
        if (logger.isLoggable(level)) {
	    if (locs == null)
	        logger.log(level, "<null>");
	    else if (locs.length == 0)
	        logger.log(level, "<EMPTY>");
	    else {
	        logger.log(level, 
	            "Locators: {0}" ,  Arrays.asList(locs));
	    }
	}
    }

    /** Utility method for displaying lookup service attributes */
    static void dumpAttrs(Entry[] attrs, Logger logger, Level level) {
        if (logger.isLoggable(level)) {
	    if (attrs == null)
	        logger.log(level, "<null>");
	    else if (attrs.length == 0)
	        logger.log(level, "<EMPTY>");
	    else {
	        logger.log(level, 
	            "Attributes: {0}" ,  Arrays.asList(attrs));
	    }
	}
    }

    /**
     * Log information about failing to initialize the service and rethrow the
     * appropriate exception.
     *
     * @param e the exception produced by the failure
     */
    protected void initFailed(Throwable e) throws Exception {
	INIT_LOGGER.log(Level.SEVERE, "Mercury failed to initialize", e);
        if (e instanceof PrivilegedActionException) e = e.getCause();
	if (e instanceof Exception) {
	    throw (Exception) e;
	} else if (e instanceof Error) {
	    throw (Error) e;
	} else {
	    throw new IllegalStateException(e.getMessage(), e);
	}
    }
    
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
        LookupLocator[] locators) throws RemoteException 
    {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "prepareNewLocators");
	}
        for (int i=0; i<locators.length; i++) {
            locators[i] = (LookupLocator)preparer.prepareProxy(locators[i]);
        } 
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "prepareNewLocators");
	}
    } 

    static LookupLocator[] prepareExistingLocators(
        ProxyPreparer preparer, LookupLocator[] lookupLocators) 
    {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "prepareExistingLocators");
	}
        ArrayList locsList = new ArrayList(lookupLocators.length);
        for(int i=0; i< lookupLocators.length; i++) {
            try {
                locsList.add(preparer.prepareProxy(lookupLocators[i]) );
            } catch(Exception e) {
                if(DELIVERY_LOGGER.isLoggable(Level.INFO)) {
                    DELIVERY_LOGGER.log(Level.INFO,
		        "Failure preparing recovered "
                        + "lookup locator: {0}", 
			lookupLocators[i]);
		    DELIVERY_LOGGER.log(Level.INFO, 
		        "Associated exception is: ", e);
                }//endif
            }
        }//end loop
        lookupLocators = (LookupLocator[])locsList.toArray
            (new LookupLocator[locsList.size()]);
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	        "prepareExistingLocators");
	}
        return  lookupLocators;	    
    } 

    //////////////////////////////////////////
    // ProxyTrust Method
    //////////////////////////////////////////
    public TrustVerifier getProxyVerifier( ) {
        if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	    OPERATIONS_LOGGER.entering(MAILBOX_SOURCE_CLASS, 
	        "getProxyVerifier");
	}
        readyState.check();
	/* No verifier if the server isn't secure */
	if (!(mailboxProxy instanceof RemoteMethodControl)) {
	    throw new UnsupportedOperationException();
	} else {
            if (OPERATIONS_LOGGER.isLoggable(Level.FINER)) {
	        OPERATIONS_LOGGER.exiting(MAILBOX_SOURCE_CLASS, 
	            "getProxyVerifier");
	    }
	    return new ProxyVerifier(serverStub, serviceID);
	}
    }

    /**
     * Utility method that check for valid resource
     */
    static boolean ensureCurrent(LeasedResource resource) {
        return resource.getExpiration() > System.currentTimeMillis();
    }
}//end class MailboxImpl
