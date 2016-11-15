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
package org.apache.river.reggie;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.rmi.MarshalledObject;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationSystem;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import net.jini.activation.ActivationExporter;
import net.jini.activation.ActivationGroup;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.config.NoSuchEntryException;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.discovery.Constants;
import net.jini.discovery.ConstrainableLookupLocator;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.DiscoveryLocatorManagement;
import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.export.ServiceAttributesAccessor;
import net.jini.export.ServiceCodebaseAccessor;
import net.jini.export.ServiceIDAccessor;
import net.jini.export.ServiceProxyAccessor;
import net.jini.id.ReferentUuid;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalledInstance;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.lookup.JoinManager;
import net.jini.lookup.entry.ServiceInfo;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;
import org.apache.river.api.io.AtomicMarshalledInstance;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.ReadInput;
import org.apache.river.api.io.AtomicSerial.ReadObject;
import org.apache.river.api.util.Startable;
import org.apache.river.config.Config;
import org.apache.river.config.LocalHostLookup;
import org.apache.river.constants.ThrowableConstants;
import org.apache.river.constants.VersionConstants;
import org.apache.river.discovery.ClientSubjectChecker;
import org.apache.river.discovery.Discovery;
import org.apache.river.discovery.DiscoveryConstraints;
import org.apache.river.discovery.DiscoveryProtocolException;
import org.apache.river.discovery.EncodeIterator;
import org.apache.river.discovery.MulticastAnnouncement;
import org.apache.river.discovery.MulticastRequest;
import org.apache.river.discovery.UnicastResponse;
import org.apache.river.discovery.https.DiscoveryUnicastHTTPS;
import org.apache.river.logging.Levels;
import org.apache.river.lookup.entry.BasicServiceType;
import org.apache.river.proxy.CodebaseProvider;
import org.apache.river.proxy.MarshalledWrapper;
import org.apache.river.reliableLog.LogHandler;
import org.apache.river.reliableLog.ReliableLog;
import org.apache.river.start.LifeCycle;
import org.apache.river.thread.InterruptedStatusThread;
import org.apache.river.thread.InterruptedStatusThread.Interruptable;
import org.apache.river.thread.NamedThreadFactory;
import org.apache.river.thread.ReadersWriter;
import org.apache.river.thread.ReadersWriter.ConcurrentLockException;
import org.apache.river.thread.SynchronousExecutors;

/**
 * Base server-side implementation of a lookup service, subclassed by
 * TransientRegistrarImpl and PersistentRegistrarImpl.  Multiple client-side
 * proxy classes are used, for the ServiceRegistrar interface as well as for
 * leases and administration; their methods transform the parameters and then
 * make corresponding calls on the Registrar interface implemented on the
 * server side.
 *
 * @author Sun Microsystems, Inc.
 *
 */
class RegistrarImpl implements Registrar, ProxyAccessor, ServerProxyTrust, Startable,
	ServiceProxyAccessor, ServiceAttributesAccessor, ServiceIDAccessor,
	ServiceCodebaseAccessor
{

    /** Maximum minMax lease duration for both services and events */
    private static final long MAX_LEASE = 1000L * 60 * 60 * 24 * 365 * 1000;
    /** Maximum minimum renewal interval */
    private static final long MAX_RENEW = 1000L * 60 * 60 * 24 * 365;
    /** Default maximum size of multicast packets to send and receive */
    private static final int DEFAULT_MAX_PACKET_SIZE = 512;
    /** Default time to live value to use for sending multicast packets */
    private static final int DEFAULT_MULTICAST_TTL = 15;
    /** Default timeout to set on sockets used for unicast discovery */
    private static final int DEFAULT_SOCKET_TIMEOUT = 1*60*1000;
    /** Log format version */
    private static final int LOG_VERSION = 3;
    /** Logger and configuration component name */
    private static final String COMPONENT = "org.apache.river.reggie";
    /** Lease ID always assigned to self */
    private static final Uuid myLeaseID = UuidFactory.create(0L, 0L);
    /** Logger used by this service */
    private static final Logger logger = Logger.getLogger(COMPONENT);

    /** Base set of initial attributes for self */
    private static final Entry[] baseAttrs = {
	new ServiceInfo(
	    "Lookup", "Sun Microsystems, Inc.", "Sun Microsystems, Inc.",
	    VersionConstants.SERVER_VERSION, "", ""),
	new BasicServiceType("Lookup")
    };
    /** Empty attribute set */
    private static final EntryRep[] emptyAttrs = {};

    /** Proxy for myself */
    private RegistrarProxy proxy;
    /** Exporter for myself */
    private volatile Exporter serverExporter; // accessed without lock from DestroyThread
    /** Remote reference for myself */
    private Registrar myRef;
    /** Our service ID */
    private volatile ServiceID myServiceID; // accessed without lock from DecodeRequestTask
    /** Our activation id, or null if not activatable */
    private final ActivationID activationID;
    /** Associated activation system, or null if not activatable */
    private final ActivationSystem activationSystem;
    /** Our LookupLocator */
    private volatile LookupLocator myLocator; // accessed without lock from Announce
    /** Our login context, for logging out */
    private final LoginContext loginContext;
    /** Shutdown callback object, or null if no callback needed */
    private final LifeCycle lifeCycle;

    /** Unicast socket factories */
    private final ServerSocketFactory serverSocketFactory ;
    private final SocketFactory socketFactory;

    /**
     * Map from ServiceID to SvcReg.  Every service is in this map under
     * its serviceID.
     */
    private final Map<ServiceID,SvcReg> serviceByID = new HashMap<ServiceID,SvcReg>(200);
    /**
     * Identity map from SvcReg to SvcReg, ordered by lease expiration.
     * Every service is in this map.
     */
    private final SortedSet<SvcReg> serviceByTime = new TreeSet<SvcReg>();
    /**
     * Map from String to HashMap mapping ServiceID to SvcReg.  Every service 
     * is in this map under its types.
     */
    private final Map<String,Map<ServiceID,SvcReg>> serviceByTypeName 
            = new HashMap<String,Map<ServiceID,SvcReg>>(200);
    /**
     * Map from EntryClass to HashMap[] where each HashMap is a map from
     * Object (field value) to ArrayList(SvcReg).  The HashMap array has as
     * many elements as the EntryClass has fields (including fields defined
     * by superclasses).  Services are in this map multiple times, once
     * for each field of each entry it has.  The outer map is indexed by the
     * first (highest) superclass that defines the field.  This means that a
     * HashMap[] has null elements for fields defined by superclasses, but
     * this is a small memory hit and is simpler than subtracting off base
     * index values when accessing the arrays.
     */
    private final Map<EntryClass,Map<Object,List<SvcReg>>[]> serviceByAttr 
            = new HashMap<EntryClass,Map<Object,List<SvcReg>>[]>(23);
    /**
     * Map from EntryClass to ArrayList(SvcReg).  Services are in this map
     * multiple times, once for each no-fields entry it has (no fields meaning
     * none of the superclasses have fields either).  The map is indexed by
     * the exact type of the entry.
     */
    private final Map<EntryClass,List<SvcReg>> serviceByEmptyAttr 
            = new HashMap<EntryClass,List<SvcReg>>(11);
    /** All EntryClasses with non-zero numInstances */
    private final List<EntryClass> entryClasses = new ArrayList<EntryClass>();
    /**
     * Map from Long(eventID) to EventReg.  Every event registration is in
     * this map under its eventID.
     */
    private final Map<Long,EventReg> eventByID = new HashMap<Long,EventReg>(200);
    /**
     * Identity map from EventReg to EventReg, ordered by lease expiration.
     * Every event registration is in this map.
     */
    private final Queue<EventReg> eventByTime = new PriorityQueue<EventReg>();
    /**
     * Map from ServiceID to EventReg or EventReg[].  An event
     * registration is in this map if its template matches on (at least)
     * a specific serviceID.
     */
    private final Map<ServiceID,Object> subEventByService = new HashMap<ServiceID,Object>(200);
    /**
     * Map from Long(eventID) to EventReg.  An event registration is in
     * this map if its template matches on ANY_SERVICE_ID.
     */
    private final Map<Long,EventReg> subEventByID = new HashMap<Long,EventReg>(200);

    /** Generator for resource (e.g., registration, lease) Uuids */
    private final UuidGenerator resourceIdGenerator;
    /** Generator for service IDs */
    private final UuidGenerator serviceIdGenerator;
    /** Event ID */
    private long eventID = 0; // protected by concurrentObj
    /** Random number generator for use in lookup */
    private final Random random = new Random();

    /** Preparer for received remote event listeners */
    private final ProxyPreparer listenerPreparer;
    /** Preparer for remote event listeners recovered from state log */
    private final ProxyPreparer recoveredListenerPreparer;
    /** Preparer for received lookup locators */
    private final ProxyPreparer locatorPreparer;
    /** Preparer for lookup locators recovered from state log */
    private final ProxyPreparer recoveredLocatorPreparer;
    
    /** Current maximum service lease duration granted, in milliseconds. */
    private long maxServiceLease;
    /** Current maximum event lease duration granted, in milliseconds. */
    private long maxEventLease;
    /** Earliest expiration time of a SvcReg */
    private long minSvcExpiration = Long.MAX_VALUE;
    /** Earliest expiration time of an EventReg */
    private long minEventExpiration = Long.MAX_VALUE;

    /** Manager for discovering other lookup services */
    private final DiscoveryManagement discoer;
    /** Manager for joining other lookup services */
    private volatile JoinManager joiner; // accessed without lock from DestroyThread
    /** Executors for sending events and discovery responses */
    private final SynchronousExecutors eventNotifierExec;
    private final Map<EventReg,ExecutorService> eventTaskMap;
//    private final EventTaskQueue eventTaskQueue;
    private final ExecutorService discoveryResponseExec;
    /** Service lease expiration thread */
    private final Thread serviceExpirer;
    /** Event lease expiration thread */
    private final Thread eventExpirer;
    /** Unicast discovery request packet receiving thread */
    private volatile Thread unicaster; // accessed without lock from DestroyThread
    private volatile Unicast unicast;
    /** Multicast discovery request packet receiving thread */
    private final Thread multicaster;
    /** Multicast discovery announcement sending thread */
    private final Thread announcer;
    /** Snapshot-taking thread */
    private final Thread snapshotter;

    /** Concurrent object to control read and write access */
    private final ReadersWriter concurrentObj;
    /** Object for synchronizing with the service expire thread */
    private final Condition serviceNotifier;
    /** Object for synchronizing with the event expire thread */
    private final Condition eventNotifier;
    /** Object on which the snapshot-taking thread will synchronize */
    private final Condition snapshotNotifier;

    /** Canonical ServiceType for java.lang.Object */
    private final ServiceType objectServiceType;

    /** Log for recovering/storing state, or null if running as transient */
    private final ReliableLog log;
    /** Flag indicating whether system is in a state of recovery */
    private volatile boolean inRecovery;
    /** Current number of records in the Log File since the last snapshot */
    private final AtomicInteger logFileSize = new AtomicInteger();

    /** Log file must contain this many records before snapshot allowed */
    private final int persistenceSnapshotThreshold ;
    /** Weight factor applied to snapshotSize when deciding to take snapshot */
    private final float persistenceSnapshotWeight;
    /** Minimum value for maxServiceLease. */
    private final long minMaxServiceLease;
    /** Minimum value for maxEventLease. */
    private final long minMaxEventLease;
    /** Minimum average time between lease renewals, in milliseconds. */
    private final long minRenewalInterval;
    /** Port for unicast discovery */
    private volatile int unicastPort;
    private int httpsUnicastPort;
    private boolean enableHttpsUnicast;
    private Discovery httpsDiscovery;
    /** The groups we are a member of */
    private volatile String[] memberGroups; // accessed from DecodeRequestTask and Announce
    /** The groups we should join */
    private volatile String[] lookupGroups;
    /** The locators of other lookups we should join */
    private volatile LookupLocator[] lookupLocators;
    /** The attributes to use when joining (including with myself) */
    private volatile Entry[] lookupAttrs;
    /** Interval to wait in between sending multicast announcements */
    private final long multicastAnnouncementInterval;
    /** Multicast announcement sequence number */
    private final AtomicLong announcementSeqNo = new AtomicLong();

    /** Network interfaces to use for multicast discovery */
    private final NetworkInterface[] multicastInterfaces;
    /** Flag indicating whether network interfaces were explicitly specified */
    private final boolean multicastInterfacesSpecified;
    /** Interval to wait in between retrying failed interfaces */
    private final int multicastInterfaceRetryInterval;
    /** Utility for participating in version 2 of discovery protocols */
    private final Discovery protocol2;
    /** Cached raw constraints associated with unicastDiscovery method*/
    private final InvocationConstraints rawUnicastDiscoveryConstraints;
    /** Constraints specified for incoming multicast requests */
    private final DiscoveryConstraints multicastRequestConstraints;
    /** Constraints specified for outgoing multicast announcements */
    private final DiscoveryConstraints multicastAnnouncementConstraints;
    /** Constraints specified for handling unicast discovery */
    private final DiscoveryConstraints unicastDiscoveryConstraints;
    /** Client subject checker to apply to incoming multicast requests */
    private final ClientSubjectChecker multicastRequestSubjectChecker;
    /** Maximum time to wait for calls to finish before forcing unexport */
    private final long unexportTimeout;
    /** Time to wait between unexport attempts */
    private final long unexportWait;
    /** Client subject checker to apply to unicast discovery attempts */
    private final ClientSubjectChecker unicastDiscoverySubjectChecker;
    
    // Not required after start is called.
    private String unicastDiscoveryHost;
    private Configuration config;
    private Exception constructionException;
    private AccessControlContext context;
    private final String certFactoryType;
    private final String certPathEncoding;
    private final byte[] encodedCerts;
    

    /**
     * Constructs RegistrarImpl based on a configuration obtained using the
     * provided string arguments.  If activationID is non-null, the created
     * RegistrarImpl runs as activatable; if persistent is true, it
     * persists/recovers its state to/from disk.  A RegistrarImpl instance
     * cannot be constructed as both activatable and non-persistent.  If
     * lifeCycle is non-null, its unregister method is invoked during shutdown.
     */
    RegistrarImpl(String[] configArgs,
		  final ActivationID activationID,
		  final boolean persistent,
		  final LifeCycle lifeCycle)
	throws Exception
    {
            this(ConfigurationProvider.getInstance(
		configArgs, RegistrarImpl.class.getClassLoader())
                    ,activationID,persistent,lifeCycle);
    }

    /**
     * Constructs RegistrarImpl based on the
     * Configuration argument.  If activationID is non-null, the created
     * RegistrarImpl runs as activatable; if persistent is true, it
     * persists/recovers its state to/from disk.  A RegistrarImpl instance
     * cannot be constructed as both activatable and non-persistent.  If
     * lifeCycle is non-null, its unregister method is invoked during shutdown.
     */
    RegistrarImpl(final Configuration config,
		  final ActivationID activationID,
		  final boolean persistent,
		  final LifeCycle lifeCycle)
	throws Exception
    {
            this(loginAndRun(config,activationID,persistent,lifeCycle));
    }

   
    
    private static Initializer loginAndRun( final Configuration config, 
                                            final ActivationID activationID, 
                                            final boolean persistent, 
                                            final LifeCycle lifeCycle)
	throws Exception
    {
	
	Initializer result = null;
        try {
            if (activationID != null && !persistent) {
                throw new IllegalArgumentException();
            }
            final LoginContext loginContext = (LoginContext) config.getEntry(
               COMPONENT, "loginContext", LoginContext.class, null);

            PrivilegedExceptionAction<Initializer> init = new PrivilegedExceptionAction<Initializer>() {
                public Initializer run() throws Exception {
                    return new Initializer(config, 
                            activationID, persistent, lifeCycle, loginContext);
                }
            };
            if (loginContext != null) {
                loginContext.login();
                try {
                    result = Subject.doAsPrivileged(
                        loginContext.getSubject(), init, null);
                } catch (PrivilegedActionException e) {
                    throw e.getCause();
                }
            } else {
                result = init.run();
            }
            return result;
        } catch (Throwable t) {
	    logger.log(Level.SEVERE, "Reggie initialization failed", t);
	    if (t instanceof Exception) {
		throw (Exception) t;
	    } else {
		throw (Error) t;
	    }
	}
    }
    
     private RegistrarImpl(Initializer init){
        this.concurrentObj = new ReadersWriter();
        this.snapshotNotifier = concurrentObj.newCondition();
        this.eventNotifier = concurrentObj.newCondition();
        this.serviceNotifier = concurrentObj.newCondition();
	this.certFactoryType = init.certFactoryType;
	this.certPathEncoding = init.certPathEncoding;
	this.encodedCerts = init.encodedCerts.clone();
        lifeCycle = init.lifeCycle;
        serverSocketFactory = init.serverSocketFactory;
        persistenceSnapshotThreshold = init.persistenceSnapshotThreshold;
        socketFactory = init.socketFactory;
        recoveredListenerPreparer = init.recoveredListenerPreparer;
        persistenceSnapshotWeight = init.persistenceSnapshotWeight;
        recoveredLocatorPreparer = init.recoveredLocatorPreparer;
        inRecovery = init.inRecovery;
        activationID = init.activationID;
        activationSystem = init.activationSystem;
        serverExporter = init.serverExporter;
        lookupGroups = init.lookupGroups;
        lookupLocators = init.lookupLocators;
        memberGroups = init.memberGroups;
        unicastPort = init.unicastPort;
	httpsUnicastPort = init.httpsUnicastPort;
	enableHttpsUnicast = init.enableHttpsUnicast;
        lookupAttrs = init.lookupAttrs;
        discoer = init.discoer;
        listenerPreparer = init.listenerPreparer;
        locatorPreparer = init.locatorPreparer;
        minMaxEventLease = init.minMaxEventLease;
        minMaxServiceLease = init.minMaxServiceLease;
        minRenewalInterval = init.minRenewalInterval;
        multicastAnnouncementInterval = init.multicastAnnouncementInterval;
        multicastInterfaceRetryInterval = init.multicastInterfaceRetryInterval;
        multicastInterfaces = init.multicastInterfaces;
        multicastInterfacesSpecified = init.multicastInterfacesSpecified;
        resourceIdGenerator = init.resourceIdGenerator;
        serviceIdGenerator = init.serviceIdGenerator;
        unexportTimeout = init.unexportTimeout;
        unexportWait = init.unexportWait;
        objectServiceType = init.objectServiceType;
        unicastDiscoverySubjectChecker = init.unicastDiscoverySubjectChecker;
        protocol2 = init.protocol2;
        rawUnicastDiscoveryConstraints = init.rawUnicastDiscoveryConstraints;
        multicastRequestConstraints = init.multicastRequestConstraints;
        multicastAnnouncementConstraints = init.multicastAnnouncementConstraints;
        unicastDiscoveryConstraints = init.unicastDiscoveryConstraints;
        context = init.context;
        eventNotifierExec = new SynchronousExecutors(init.scheduledExecutor);
        eventTaskMap = new HashMap<EventReg,ExecutorService>(200);
        discoveryResponseExec = init.executor;
        ReliableLog log = null;
        Thread serviceExpirer = null;
        Thread eventExpirer = null;
        Thread unicaster = null;
        Thread multicaster = null;
        Thread announcer = null;
        Thread snapshotter = null;
        
        try {
            // Create threads with correct login context.
            List<Thread> threads = AccessController.doPrivileged(new PrivilegedExceptionAction<List<Thread>>(){

                        @Override
                        public List<Thread> run() throws Exception {
                            Thread t;
                            List<Thread> list = new ArrayList<Thread>(6);
                            list.add(newThread(new ServiceExpire(RegistrarImpl.this), "service expire"));
                            list.add(newThread(new EventExpire(RegistrarImpl.this),"event expire"));
                            unicast = new Unicast(RegistrarImpl.this, unicastPort);
                            list.add(newInterruptStatusThread(unicast, "unicast request"));
                            list.add(newInterruptStatusThread(new Multicast(RegistrarImpl.this), "multicast request"));
                            list.add(newThread(new Announce(RegistrarImpl.this),"discovery announcement"));
                            list.add(newThread(new Snapshot(RegistrarImpl.this),"snapshot thread"));
                            return list;
                        }
                        
                        private Thread newThread(Runnable r, String name){
                            Thread t = new Thread(r,name);
                            t.setDaemon(false);
                            return t;
                        }
                        
                        private Thread newInterruptStatusThread(Runnable r, String name){
                            Thread t = new InterruptedStatusThread(r,name);
                            t.setDaemon(false);
                            return t;
                        }
                
                    }, context);
            serviceExpirer = threads.get(0);
            eventExpirer = threads.get(1);
            unicaster =  threads.get(2);
            multicaster = threads.get(3);
            announcer = threads.get(4);
            snapshotter = threads.get(5);
            if (init.persistent){
                log = new ReliableLog(init.persistenceDirectory, new LocalLogHandler(this));
                if (logger.isLoggable(Level.CONFIG)) {
                    logger.log(Level.CONFIG, "using persistence directory {0}",
                               new Object[]{ init.persistenceDirectory });
                }
            } else {
                log = null;
            }
            
            constructionException = null;
        } catch (PrivilegedActionException ex) {
            constructionException = ex.getException();
        } catch (IOException ex) {
            constructionException = ex;
        } finally {
            this.log = log;
            this.serviceExpirer = serviceExpirer;
            this.eventExpirer = eventExpirer;
            this.unicaster = unicaster;
            this.multicaster = multicaster;
            this.announcer = announcer;
            this.snapshotter = snapshotter;
        }
        multicastRequestSubjectChecker = init.multicastRequestSubjectChecker;
        loginContext = init.loginContext;
        unicastDiscoveryHost = init.unicastDiscoveryHost;
        config = init.config;
    }

    @Override
    public Entry[] getServiceAttributes() throws IOException {
	return getLookupAttributes();
    }

    @Override
    public ServiceID serviceID() throws IOException {
	return myServiceID;
    }

    @Override
    public String getClassAnnotation() throws IOException {
	return CodebaseProvider.getClassAnnotation(RegistrarProxy.class);
    }

    @Override
    public String getCertFactoryType() throws IOException {
	return certFactoryType;
    }

    @Override
    public String getCertPathEncoding() throws IOException {
	return certPathEncoding;
    }

    @Override
    public byte[] getEncodedCerts() throws IOException {
	return encodedCerts.clone();
    }

    /** A service item registration record. */
    @AtomicSerial
    private final static class SvcReg implements Comparable, Serializable {

	private static final long serialVersionUID = 2L;

	/**
	 * The service item.
	 *
	 * @serial
	 */
	public final Item item;
	/**
	 * The lease id.
	 *
	 * @serial
	 */
	public final Uuid leaseID;
	/**
	 * The lease expiration time.
	 *
	 * @serial
	 */
	public volatile long leaseExpiration;

	public SvcReg(GetArg arg) throws IOException{
	    this( (Item) arg.get("item", null),
		(Uuid) arg.get("leaseID", null),
		arg.get("leaseExpiration", 0L)
	    );
	}

	/** Simple constructor */
	public SvcReg(Item item, Uuid leaseID, long leaseExpiration) {
	    this.item = item;
	    this.leaseID = leaseID;
	    this.leaseExpiration = leaseExpiration;
	}

	/**
	 * Primary sort by leaseExpiration, secondary by leaseID.  The
	 * secondary sort is immaterial, except to ensure a total order
	 * (required by TreeMap).
	 */
	public int compareTo(Object obj) {
	    SvcReg reg = (SvcReg)obj;
	    if (this == reg)
		return 0;
	    int i = compare(leaseExpiration, reg.leaseExpiration);
	    if (i != 0) {
		return i;
	    }
	    i = compare(leaseID.getMostSignificantBits(),
			reg.leaseID.getMostSignificantBits());
	    if (i != 0) {
		return i;
	    }
	    return compare(leaseID.getLeastSignificantBits(),
			   reg.leaseID.getLeastSignificantBits());
	}

	/**
	 * Compares long values, returning -1, 0, or 1 if l1 is less than,
	 * equal to or greater than l2, respectively.
	 */
	private static int compare(long l1, long l2) {
	    return (l1 < l2) ? -1 : ((l1 > l2) ? 1 : 0);
	}
    }

    /** An event registration record. */
    @AtomicSerial
    private final static class EventReg implements Comparable, Serializable {

	private static final long serialVersionUID = 2L;

	/**
	 * The event id.
	 * @serial
	 */
	public final long eventID;
	/**
	 * The lease id.
	 * @serial
	 */
	public final Uuid leaseID;
	/**
	 * The template to match.
	 * @serial
	 */
	public final Template tmpl;
	/**
	 * The transitions.
	 *
	 * @serial
	 */
	public final int transitions;
	/**
	 * The current sequence number.
	 *
	 * @serial
	 */
	public long seqNo;
	/**
	 * The event listener.
	 */
	public transient RemoteEventListener listener;
	/**
	 * The handback object.
	 *
	 * @serial
	 */
	public final Object handback;
	/**
	 * The lease expiration time.
	 *
	 * @serial
	 */
	private long leaseExpiration;
	/**
	 * 
	 *  
	 */
	transient boolean newNotify;
	
	public EventReg(GetArg arg) throws IOException {
	    this(arg.get("eventID", 0L),
		    arg.get("leaseID", null, Uuid.class),
		    arg.get("tmpl", null, Template.class),
		    arg.get("transitions", 0),
		    ((RO)arg.getReader()).listener,
		    arg.get("handback", null),
		    arg.get("leaseExpiration", 0L),
		    true
		    );
	    seqNo = arg.get("seqNo", 0L);
	}
	
	@ReadInput
	static ReadObject getReader(){
	    return new RO();
	}
	
	private static class RO implements ReadObject {
	    
	    RemoteEventListener listener;

	    @Override
	    public void read(ObjectInput input) throws IOException, ClassNotFoundException {
		
		MarshalledInstance mi = (MarshalledInstance) input.readObject();
		try {
		    listener = (RemoteEventListener) mi.get(false);
		} catch (Throwable e) {
		    if (e instanceof Error &&
			ThrowableConstants.retryable(e) ==
			    ThrowableConstants.BAD_OBJECT)
		    {
			throw (Error) e;
		    }
		    logger.log(Level.WARNING,
			       "failed to recover event listener", e);
		}
	    }
	    
	}
	

	/** Simple constructor */
	public EventReg(long eventID, Uuid leaseID, Template tmpl,
			int transitions, RemoteEventListener listener,
			Object handback, long leaseExpiration, boolean newNotify) {
	    if (listener == null) throw new NullPointerException("Listener cannot be null");
	    this.eventID = eventID;
	    this.leaseID = leaseID;
	    this.tmpl = tmpl;
	    this.transitions = transitions;
	    this.seqNo = 0;
	    this.listener = listener;
	    this.handback = handback;
	    this.leaseExpiration = leaseExpiration;
	    this.newNotify = newNotify;
	}
        
        long incrementAndGetSeqNo(){
            return ++seqNo;
        }
        
        long getSeqNo(){
            return seqNo;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + (int) (this.eventID ^ (this.eventID >>> 32));
            hash = 97 * hash + (this.leaseID != null ? this.leaseID.hashCode() : 0);
            hash = 97 * hash + this.transitions;
            hash = 97 * hash + (this.handback != null ? this.handback.hashCode() : 0);
            return hash;
        }
        
        @Override
        public boolean equals(Object o){
            if (this == o) return true;
            if (!(o instanceof EventReg)) return false;
            EventReg that = (EventReg) o;
            if (this.eventID != that.eventID) return false;
            if (this.transitions != that.transitions) return false;
            if (!this.leaseID.equals(that.leaseID)) return false; // leaseID never null
            return this.handback.equals(that.handback);
        }

	/**
	 * Primary sort by leaseExpiration, secondary by eventID.  The
	 * secondary sort is immaterial, except to ensure a total order
	 * (required by TreeMap).
	 */
	public int compareTo(Object obj) {
	    if (equals(obj)) return 0;
	    EventReg reg = (EventReg)obj;
	    if (getLeaseExpiration() < reg.getLeaseExpiration() ||
		(getLeaseExpiration() == reg.getLeaseExpiration() &&
		 eventID < reg.eventID))
		return -1;
	    return 1;
	}

	/**
	 * Prepares listener (if non-null) using the given proxy preparer.  If
	 * preparation fails, the listener field is set to null.
	 */
	void prepareListener(ProxyPreparer preparer) {
	    if (listener != null) {
		try {
		    listener =
			(RemoteEventListener) preparer.prepareProxy(listener);
		} catch (Exception e) {
		    if (logger.isLoggable(Level.WARNING)) {
			logThrow(
			    Level.WARNING,
			    getClass().getName(),
			    "prepareListener",
			    "failed to prepare event listener {0}",
			    new Object[]{ listener },
			    e);
		    }
		    listener = null;
		}
                seqNo += Integer.MAX_VALUE;
	    }
	}

	/**
	 * @serialData RemoteEventListener as a MarshalledInstance
	 */
	private void writeObject(ObjectOutputStream stream)
	    throws IOException
	{
	    stream.defaultWriteObject();
	    stream.writeObject(new AtomicMarshalledInstance(listener));
	}

	/**
	 * Unmarshals the event listener.
	 */
	private void readObject(ObjectInputStream stream)
	    throws IOException, ClassNotFoundException
	{
	    stream.defaultReadObject();
	    MarshalledInstance mi = (MarshalledInstance) stream.readObject();
	    try {
		listener = (RemoteEventListener) mi.get(false);
	    } catch (Throwable e) {
		if (e instanceof Error &&
		    ThrowableConstants.retryable(e) ==
			ThrowableConstants.BAD_OBJECT)
		{
		    throw (Error) e;
		}
		logger.log(Level.WARNING,
			   "failed to recover event listener", e);
	    }
	}

        /**
         * @return the leaseExpiration
         */
        synchronized long getLeaseExpiration() {
            return leaseExpiration;
        }

        /**
         * @param leaseExpiration the leaseExpiration to set
         */
        synchronized void setLeaseExpiration(long leaseExpiration) {
            this.leaseExpiration = leaseExpiration;
        }
    }

    /**
     * Interface defining the method(s) that must be implemented by each of
     * the concrete LogObj classes. This allows for the definition of
     * object-dependent invocations of the appropriate implementation of
     * the method(s) declared in this interface.
     */
    private static interface LogRecord extends Serializable {
	void apply(RegistrarImpl regImpl);
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a new service is registered.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class SvcRegisteredLogObj implements LogRecord {

	private static final long serialVersionUID = 2L;

	/**
	 * The service registration.
	 *
	 * @serial
	 */
	private final SvcReg reg;

	/** Simple constructor */
	public SvcRegisteredLogObj(SvcReg reg) {
	    this.reg = reg;
	}
	
	/**
	 * Modifies the state of the Registrar by registering the service
         * stored in the reg object. Also needs to delete any existing
	 * service with the same serviceID; this can happen if a service
	 * re-registers while an existing registration is in effect, because
	 * we don't log a separate lease cancellation record for the existing
	 * registration in that case.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
            regImpl.concurrentObj.writeLock();
            try {
                SvcReg oldReg =
                    (SvcReg)regImpl.serviceByID.get(reg.item.getServiceID());
                if (oldReg != null)
                    regImpl.deleteService(oldReg, 0);
                regImpl.addService(reg);
            } finally {
                regImpl.concurrentObj.writeUnlock();
            }
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * new attributes are added to an existing service in the Registrar.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class AttrsAddedLogObj implements LogRecord {

	private static final long serialVersionUID = 2L;

	/**
	 * The service id.
	 *
	 * @serial
	 */
	private final ServiceID serviceID;
	/**
	 * The lease id.
	 *
	 * @serial
	 */
	private final Uuid leaseID;
	/**
	 * The attributes added.
	 *
	 * @serial
	 */
	private final EntryRep[] attrSets;

	/** Simple constructor */
	public AttrsAddedLogObj(ServiceID serviceID,
				Uuid leaseID,
				EntryRep[] attrSets)
	{
	    this.serviceID = serviceID;
	    this.leaseID = leaseID;
	    this.attrSets = attrSets;
	}
	
	/**
	 * Modifies the state of the Registrar by adding to all of the
         * services matching the template, the attributes stored in
         * attributeSets.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
            regImpl.concurrentObj.writeLock();
	    try {
		regImpl.addAttributesDo(serviceID, leaseID, attrSets);
            } catch (UnknownLeaseException e) {
		/* this exception should never occur when recovering  */
		throw new AssertionError("an UnknownLeaseException should"
					 + " never occur during recovery");
	    } finally {
                regImpl.concurrentObj.writeUnlock();
            }   
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * existing attributes of an existing service in the Registrar are
     * modified.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class AttrsModifiedLogObj implements LogRecord {

	private static final long serialVersionUID = 2L;

	/**
	 * The service id.
	 *
	 * @serial
	 */
	private final ServiceID serviceID;
	/**
	 * The lease id.
	 *
	 * @serial
	 */
	private final Uuid leaseID;
	/**
	 * The templates to match.
	 * @serial
	 */
	private final EntryRep[] attrSetTmpls;
	/**
	 * The new attributes.
	 *
	 * @serial
	 */
	private final EntryRep[] attrSets;

	/** Simple constructor */
	public AttrsModifiedLogObj(ServiceID serviceID,
				   Uuid leaseID,
				   EntryRep[] attrSetTmpls,
				   EntryRep[] attrSets)
	{
	    this.serviceID = serviceID;
	    this.leaseID = leaseID;
	    this.attrSetTmpls = attrSetTmpls;
	    this.attrSets = attrSets;
	}

	/**
	 * Modifies the state of the Registrar by modifying the attributes
         * of the services that match the template with the attributes 
         * stored in attributeSets.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
            regImpl.concurrentObj.writeLock();
	    try {
		regImpl.modifyAttributesDo(serviceID, leaseID,
					   attrSetTmpls, attrSets);
	    } catch (UnknownLeaseException e) {
		/* this exception should never occur when recovering  */
		throw new AssertionError("an UnknownLeaseException should"
					 + " never occur during recovery");
	    } finally {
                regImpl.concurrentObj.writeUnlock();
            }
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * new attributes are set on an existing service in the Registrar.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class AttrsSetLogObj implements LogRecord {

	private static final long serialVersionUID = 2L;

	/**
	 * The service id.
	 *
	 * @serial
	 */
	private final ServiceID serviceID;
	/**
	 * The lease id.
	 *
	 * @serial
	 */
	private final Uuid leaseID;
	/**
	 * The new attributes.
	 *
	 * @serial
	 */
	private final EntryRep[] attrSets;

	/** Simple constructor */
	public AttrsSetLogObj(ServiceID serviceID,
			      Uuid leaseID,
			      EntryRep[] attrSets)
	{
	    this.serviceID = serviceID;
	    this.leaseID = leaseID;
	    this.attrSets = attrSets;
	}
	
	/**
	 * Modifies the state of the Registrar by replacing the attributes
         * of the services matching the template with the attributes stored
         * in attributeSets.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
            regImpl.concurrentObj.writeLock();
	    try {
		regImpl.setAttributesDo(serviceID, leaseID, attrSets);
	    } catch (UnknownLeaseException e) {
		/* this exception should never occur when recovering  */
	    } finally {
                regImpl.concurrentObj.writeUnlock();
            }
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a new event is registered.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class EventRegisteredLogObj implements LogRecord {

	private static final long serialVersionUID = 2L;

	/**
	 * The event registration.
	 *
	 * @serial
	 */
	private final EventReg eventReg;

	/** Simple constructor */
	public EventRegisteredLogObj(EventReg eventReg) {
	    this.eventReg = eventReg;
	}
	
	/**
	 * Modifies the state of the Registrar by registering the event
         * stored in the eventReg object; and by updating both the event
         * sequence number and the event ID.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
            regImpl.concurrentObj.writeLock();
            try{
                eventReg.prepareListener(regImpl.recoveredListenerPreparer);
                regImpl.addEvent(eventReg);
                regImpl.eventID++;
            } finally {
                regImpl.concurrentObj.writeUnlock();
            }
	}
    }
    
    

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a lease on an existing service in the Registrar is cancelled.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class ServiceLeaseCancelledLogObj implements LogRecord {

	private static final long serialVersionUID = 2L;

	/**
	 * The service id.
	 *
	 * @serial
	 */
	private final ServiceID serviceID;
	/**
	 * The lease id.
	 *
	 * @serial
	 */
	private final Uuid leaseID;

	/** Simple constructor */
	public ServiceLeaseCancelledLogObj(ServiceID serviceID, Uuid leaseID) {
	    this.serviceID = serviceID;
	    this.leaseID = leaseID;
	}
	
	/**
	 * Modifies the state of the Registrar by cancelling the lease
         * having ID equal to the contents of the leaseID field; and
         * corresponding to the service with ID equal to the contents of
         * the serviceID field.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
            regImpl.concurrentObj.writeLock();
	    try {
		regImpl.cancelServiceLeaseDo(serviceID, leaseID);
	    } catch (UnknownLeaseException e) {
		/* this exception should never occur when recovering  */
	    } finally {
                regImpl.concurrentObj.writeUnlock();
            }
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a lease on an existing service in the Registrar is renewed.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class ServiceLeaseRenewedLogObj implements LogRecord {

	private static final long serialVersionUID = 2L;

	/**
	 * The service id.
	 *
	 * @serial
	 */
	private final ServiceID serviceID;
	/**
	 * The lease id.
	 *
	 * @serial
	 */
	private final Uuid leaseID;
	/**
	 * The new lease expiration time.
	 *
	 * @serial
	 */
	private final long leaseExpTime;

	/** Simple constructor */
	public ServiceLeaseRenewedLogObj(ServiceID serviceID,
					 Uuid leaseID,
					 long leaseExpTime)
	{
	    this.serviceID = serviceID;
	    this.leaseID = leaseID;
	    this.leaseExpTime = leaseExpTime;
	}
	
	/**
	 * Modifies the state of the Registrar by renewing the lease
         * having ID equal to the contents of the leaseID field; and
         * corresponding to the service with ID equal to the contents
         * of the serviceID field.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    regImpl.renewServiceLeaseAbs(serviceID, leaseID, leaseExpTime);
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a lease on a registered event is cancelled.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class EventLeaseCancelledLogObj implements LogRecord {

	private static final long serialVersionUID = 2L;

	/**
	 * The event id.
	 *
	 * @serial
	 */
	private final long eventID;
	/**
	 * The lease id.
	 *
	 * @serial
	 */
	private final Uuid leaseID;

	/** Simple constructor */
	public EventLeaseCancelledLogObj(long eventID, Uuid leaseID) {
	    this.eventID = eventID;
	    this.leaseID = leaseID;
	}
	
	/**
	 * Modifies the state of the Registrar by cancelling the lease
         * corresponding to the event with ID equal to the contents of
         * the eventID field.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
            regImpl.concurrentObj.writeLock();
	    try {
		regImpl.cancelEventLeaseDo(eventID, leaseID);
	    } catch (UnknownLeaseException e) {
		/* this exception should never occur when recovering */
	    } finally {
                regImpl.concurrentObj.writeUnlock();
            }
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a lease on a registered event is renewed.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class EventLeaseRenewedLogObj implements LogRecord {

	private static final long serialVersionUID = 2L;

	/**
	 * The event id.
	 *
	 * @serial
	 */
	private final long eventID;
	/**
	 * The lease id.
	 *
	 * @serial
	 */
	private final Uuid leaseID;
	/**
	 * The new lease expiration time.
	 *
	 * @serial
	 */
	private final long leaseExpTime;

	/** Simple constructor */
	public EventLeaseRenewedLogObj(long eventID,
				       Uuid leaseID,
				       long leaseExpTime)
	{
	    this.eventID = eventID;
	    this.leaseID = leaseID;
	    this.leaseExpTime = leaseExpTime;
	}

	/**
	 * Modifies the state of the Registrar by renewing the lease
         * corresponding to the event with ID equal to the contents of
         * the eventID field.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    regImpl.renewEventLeaseAbs(eventID, leaseID, leaseExpTime);
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a leases in the Registrar is renewed via a LeaseMap.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class LeasesRenewedLogObj implements LogRecord {

	private static final long serialVersionUID = 2L;

	/**
	 * The service and event ids.
	 *
	 * @serial
	 */
	private final Object[] regIDs;
	/**
	 * The lease ids.
	 *
	 * @serial
	 */
	private final Uuid[] leaseIDs;
	/**
	 * The new lease expiration times.
	 *
	 * @serial
	 */
	private final long[] leaseExpTimes;

	/** Simple constructor */
	public LeasesRenewedLogObj(Object[] regIDs,
				   Uuid[] leaseIDs,
				   long[] leaseExpTimes)
	{
	    this.regIDs = regIDs;
	    this.leaseIDs = leaseIDs;
	    this.leaseExpTimes = leaseExpTimes;
	}
	
	/**
	 * Modifies the state of the Registrar by renewing the specified
         * leases.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    regImpl.renewLeasesAbs(regIDs, leaseIDs, leaseExpTimes);
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * lease are cancelled via a LeaseMap.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class LeasesCancelledLogObj implements LogRecord {

	private static final long serialVersionUID = 2L;

	/**
	 * The service and event ids.
	 *
	 * @serial
	 */
	private final Object[] regIDs;
	/**
	 * The lease ids.
	 *
	 * @serial
	 */
	private final Uuid[] leaseIDs;

	/** Simple constructor */
	public LeasesCancelledLogObj(Object[] regIDs, Uuid[] leaseIDs) {
	    this.regIDs = regIDs;
	    this.leaseIDs = leaseIDs;
	}
	
	/**
	 * Modifies the state of the Registrar by cancelling the specified
         * leases.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    /* Exceptions can be returned, since we didn't weed out unknown
	     * leases before logging, but we can just ignore them anyway.
	     */
            regImpl.concurrentObj.writeLock();
            try {
                regImpl.cancelLeasesDo(regIDs, leaseIDs);
            } finally {
                regImpl.concurrentObj.writeUnlock();
            }
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the Unicast Port Number is set to a new value.
     * <p>
     * Note: the apply() method of this class merely sets the private field
     *       unicastPort. This means that during a recovery, the unicaster
     *       thread will be created with this new port number ONLY IF that
     *       thread is created AFTER recovery is complete. Thus, it is 
     *       important that at re-initialization during a re-activation
     *       of the Registrar, the recovery() method is invoked before
     *       the unicaster thread is created.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class UnicastPortSetLogObj implements LogRecord {

	private static final long serialVersionUID = 2L;

	/**
	 * The new port number.
	 *
	 * @serial
	 */
	private final int newPort;

	/** Simple constructor */
	public UnicastPortSetLogObj(int newPort) {
	    this.newPort = newPort;
	}

	/**
	 * Modifies the state of the Registrar by setting the value of the
         * private unicastPort field to the value of the newPort field.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    regImpl.unicastPort = newPort;
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the set of groups to join is changed.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class LookupGroupsChangedLogObj implements LogRecord {

	private static final long serialVersionUID = 2L;

	/**
	 * The new groups to join.
	 *
	 * @serial
	 */
	private final String[] groups;

	/** Simple constructor */
	public LookupGroupsChangedLogObj(String[] groups) {
	    this.groups = groups;
	}

	/**
	 * Modifies the state of the Registrar by setting the private
         * field lookupGroups to the reference to the groups field.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    regImpl.lookupGroups = groups;
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the set of locators of lookup services to join is changed.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class LookupLocatorsChangedLogObj implements LogRecord {

	private static final long serialVersionUID = 2L;

	/**
	 * The new locators to join.
	 */
	private transient LookupLocator[] locators;

	/** Simple constructor */
	public LookupLocatorsChangedLogObj(LookupLocator[] locators) {
	    this.locators = locators;
	}

	/**
	 * Modifies the state of the Registrar by setting the private
	 * field lookupLocators to the reference to the locators field.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    try {
		regImpl.lookupLocators = prepareLocators(
		    locators, regImpl.recoveredLocatorPreparer, true);
	    } catch (RemoteException e) {
		throw new AssertionError(e);
	    }
	}

	/**
	 * Writes locators as a null-terminated list of MarshalledInstances.
	 */
	private void writeObject(ObjectOutputStream stream)
	    throws IOException
	{
	    stream.defaultWriteObject();
	    marshalLocators(locators, stream);
	}

	/**
	 * Reads in null-terminated list of MarshalledInstances, from which
	 * locators are unmarshalled.
	 */
	private void readObject(ObjectInputStream stream)
	    throws IOException, ClassNotFoundException
	{
	    stream.defaultReadObject();
	    locators = unmarshalLocators(stream);
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the memberGroups array is set to reference a new array of strings.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class MemberGroupsChangedLogObj implements LogRecord {

	private static final long serialVersionUID = 2L;

	/**
	 * The new groups to be a member of.
	 *
	 * @serial
	 */
	private final String[] groups;

	/** Simple constructor */
	public MemberGroupsChangedLogObj(String[] groups) {
	    this.groups = groups;
	}

	/**
	 * Modifies the state of the Registrar by setting the private
         * memberGroups field to the reference to the groups field.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
            regImpl.concurrentObj.writeLock();
            try {
                regImpl.memberGroups = groups;
            } finally {
                regImpl.concurrentObj.writeUnlock();
            }
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the attributes for the lookup service are changed.
     *
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class LookupAttributesChangedLogObj implements LogRecord {

        private static final long serialVersionUID = 1L;

	/**
	 * The new lookup service attributes.
	 */
	private transient Entry[] attrs;

	/** Simple constructor */
	public LookupAttributesChangedLogObj(Entry[] attrs) {
	    this.attrs = attrs;
	}

	/**
	 * Modifies the state of the Registrar by setting the private
	 * field lookupAttrs to the reference to the attrs field.
	 *
	 * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
                regImpl.lookupAttrs = attrs;
        }

	/**
	 * Writes attributes as a null-terminated list of MarshalledInstances.
	 */
	private void writeObject(ObjectOutputStream stream)
	    throws IOException
	{
	    stream.defaultWriteObject();
	    marshalAttributes(attrs, stream);
	}

	/**
	 * Reads in null-terminated list of MarshalledInstances, from which
	 * attributes are unmarshalled.
	 */
	private void readObject(ObjectInputStream stream)
	    throws IOException, ClassNotFoundException
	{
	    stream.defaultReadObject();
	    attrs = unmarshalAttributes(stream);
	}
    }

    /**
     * Handler class for the persistent storage facility.
     * <p>
     * At any point during processing in a persistent Registrar instance, there
     * will exist both a 'snapshot' of the Registrar's state and a set of
     * records detailing each significant change that has occurred to the state
     * since the snapshot was taken. The snapshot information and the
     * incremental change information will be stored in separate files called,
     * respectively, the snapshot file and the log file. Together, these files
     * are used to recover the state of the Registrar when it is restarted or
     * reactivated (for example, after a crash or network outage).
     * <p>
     * This class contains the methods that are used to record and recover
     * the snapshot of the Registrar's state; as well as the method used to
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
     * Each significant change to the persistent Registrar's state is written
     * to the log file as an individual record (when addLogRecord() is
     * invoked).  After the number of records logged exceeds a pre-defined
     * threshold, a snapshot of the state is recorded by invoking -- through
     * the ReliableLog and its LogHandler -- the snapshot() method defined in
     * this class. After the snapshot is taken, the log file is cleared and the
     * incremental log process starts over.
     * <p>
     * The contents of the snapshot file reflect the DATA contained in
     * the fields making up the current state of the Registrar. That data
     * represents many changes -- over time -- to the Registrar's state.
     * On the other hand, each record written to the log file is an object
     * that reflects both the data used and the ACTIONS taken to make one
     * change to the Registrar's state at a particular point in time.
     * <p>
     * The data written to the snapshot file is shown below:
     * <ul>
     * <li> our service ID
     * <li> current event ID
     * <li> current values of administrable parameters
     * <li> contents of the container holding the current registered services
     * <li> null (termination 'marker' for the set of registered services)
     * <li> contents of the container holding the current registered events
     * <li> null (termination 'marker' for the set of registered events)
     * </ul>
     * The type of state changes that will generate a new record in the log
     * file are:
     * <ul>
     * <li> a new service was registered
     * <li> a new event was registered
     * <li> new attributes were added to an existing service
     * <li> existing attributes of a service were modified
     * <li> a service lease was cancelled
     * <li> a service lease was renewed
     * <li> an event lease was cancelled
     * <li> an event lease was renewed
     * <li> an administrable parameter was changed
     * </ul>
     * During recovery, the state of the Registrar at the time of a crash
     * or outage is re-constructed by first retrieving the 'base' state from
     * the snapshot file; and then modifying that base state according to
     * the records retrieved from the log file. The reconstruction of the
     * base state is achieved by invoking the recover() method defined in
     * this class. The modifications recorded in the log file are then
     * applied to the base state by invoking the applyUpdate() method
     * defined in this class. Both recover() and applyUpdate() are invoked
     * through the ReliableLog and its associated LogHandler.
     * <p>
     * NOTE: The following lines must be added to the Registrar's policy file
     * <pre>
     *     permission java.io.FilePermission "dirname",   "read,write,delete";
     *     permission java.io.FilePermission "dirname/-", "read,write,delete";
     * </pre>
     *     where 'dirname' is the name of the directory path (relative or
     *     absolute) where the snapshot and log file will be maintained.
     */
    private static class LocalLogHandler extends LogHandler {
	private final RegistrarImpl reggie;
	/** Simple constructor */
	public LocalLogHandler(RegistrarImpl reggie) {
            this.reggie = reggie;
        }
        
	/* Overrides snapshot() defined in ReliableLog's LogHandler class. */
	public void snapshot(OutputStream out) throws IOException {
            reggie.concurrentObj.readLock();
            try {
                reggie.takeSnapshot(out);
            } finally {
                reggie.concurrentObj.readUnlock();
            }
	}

	/* Overrides recover() defined in ReliableLog's LogHandler class. */
	public void recover(InputStream in)
	    throws IOException, ClassNotFoundException
	{
            reggie.concurrentObj.writeLock();
            try {
                reggie.recoverSnapshot(in);
            } finally {
                reggie.concurrentObj.writeUnlock();
            }
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
	 * will then modify the state of the Registrar in a way dictated
	 * by the type of record that was retrieved.
	 */
	public void applyUpdate(Object logRecObj) {
	    ((LogRecord)logRecObj).apply(reggie);
	}
    }

    /** Base class for iterating over all Items that match a Template. */
    private static abstract class ItemIter {
	/** Current time */
	public final long now = System.currentTimeMillis();
	/** True means duplicate items are possible */
	public boolean dupsPossible = false;
	/** Template to match */
	protected final Template tmpl;
	/** Next item to return */
	protected SvcReg reg;

	/** Subclass constructors must initialize reg */
	protected ItemIter(Template tmpl) {
	    this.tmpl = tmpl;
	}

	/** Returns true if the iteration has more elements. */
	public boolean hasNext() {
	    return reg != null;
	}

	/** Returns the next element in the iteration as an Item. */
	public Item next() {
	    if (reg == null)
		throw new NoSuchElementException();
	    Item item = reg.item;
	    step();
	    return item;
	}

	/** Returns the next element in the iteration as a SvcReg. */
	public SvcReg nextReg() {
	    if (reg == null)
		throw new NoSuchElementException();
	    SvcReg cur = reg;
	    step();
	    return cur;
	}

	/** Set reg to the next matching element, or null if none */
	protected abstract void step();
    }

    /** Iterate over all Items. */
    private static class AllItemIter extends ItemIter {
	/** Iterator over serviceByID */
	private final Iterator<SvcReg> iter;

	/** Assumes the empty template */
	public AllItemIter(Iterator<SvcReg> it) {
	    super(null);
	    iter = it;
	    step();
	}

	/** Set reg to the next matching element, or null if none */
        @Override
	protected void step() {
	    while (iter.hasNext()) {
		reg = (SvcReg)iter.next();
		if (reg.leaseExpiration > now)
		    return;
	    }
	    reg = null;
	}
    }

    /** Iterates over all services that match template's service types */
    private static class SvcIterator extends ItemIter {
	/** Iterator for list of matching services. */
        private final Iterator<SvcReg> services;
	
	/**
	 * tmpl.serviceID == null and
	 * tmpl.serviceTypes is not empty
	 */
        public SvcIterator(Template tmpl, Iterator<SvcReg> it) {
	    super(tmpl);
	    services = it;
	    step();
	}
	
        /** Set reg to the next matching element, or null if none. */
        protected final void step() {
	    if (tmpl.serviceTypes.length > 1) {
		while (services.hasNext()) {
		    reg = (SvcReg) services.next();
		    if (reg.leaseExpiration > now &&
			matchType(tmpl.serviceTypes, reg.item.serviceType) &&
			matchAttributes(tmpl, reg.item))
			return;
		}		
	    } else {
		while (services.hasNext()) {
		    reg = (SvcReg) services.next();
		    if (reg.leaseExpiration > now &&
			matchAttributes(tmpl, reg.item))
			return;
		}
	    }
	    reg = null;
	}
    }

    /** Iterate over all matching Items by attribute value. */
    private static class AttrItemIter extends ItemIter {
	/** SvcRegs obtained from serviceByAttr for chosen attr */
	private final List<SvcReg> svcs;
	/** Current index into svcs */
	private int svcidx;

	/**
	 * tmpl.serviceID == null and
	 * tmpl.serviceTypes is empty and
	 * tmpl.attributeSetTemplates[setidx].fields[fldidx] != null
	 */
	public AttrItemIter(Template tmpl, List<SvcReg> svcs) {
	    super(tmpl);
	    this.svcs = svcs;
            if (svcs != null) {
                svcidx = svcs.size();
                step();
            }
	}

	/** Set reg to the next matching element, or null if none. */
	protected void step() {
	    while (--svcidx >= 0) {
		reg = svcs.get(svcidx);
		if (reg.leaseExpiration > now 
                    && matchAttributes(tmpl, reg.item)) {
                        return;
                }
	    }
	    reg = null;
	}
    }

    /** Iterate over all matching Items by entry class, dups possible. */
    private class ClassItemIter extends ItemIter {
	/** Entry class to match on */
	private final EntryClass eclass;
	/** Current index into entryClasses */
	private int classidx;
	/** Values iterator for current HashMap */
	private Iterator<List<SvcReg>> iter;
	/** SvcRegs obtained from iter or serviceByEmptyAttr */
	private List<SvcReg> svcs;
	/** Current index into svcs */
	private int svcidx = 0;

	/**
	 * tmpl.serviceID == null and
	 * tmpl.serviceTypes is empty and
	 * tmpl.attributeSetTemplates is non-empty
	 */
	public ClassItemIter(Template tmpl) {
	    super(tmpl);
	    dupsPossible = true;
	    eclass = tmpl.attributeSetTemplates[0].eclass;
	    classidx = entryClasses.size();
	    step();
	}

	/** Set reg to the next matching element, or null if none */
	protected void step() {
	    do {
		while (--svcidx >= 0) {
		    reg = svcs.get(svcidx);
		    if (reg.leaseExpiration > now &&
			matchAttributes(tmpl, reg.item))
			return;
		}
	    } while (stepValue());
	    reg = null;
	}

	/**
	 * Step to the next HashMap value, if any, reset svcs and svcidx,
	 * and return false if everything exhausted.
	 */
	private boolean stepValue() {
	    while (true) {
		if (iter != null && iter.hasNext()) {
		    svcs = iter.next();
		    svcidx = svcs.size();
		    return true;
		}
		if (!stepClass())
		    return false;
		if (iter == null)
		    return true;
	    }
	}

	/**
	 * Step to the next matching entry class, if any, reset iter
	 * using the HashMap for the last field of the class (and reset
	 * (svcs and svcidx if the entry class has no fields), and
	 * return false if everything exhausted.
	 */
	private boolean stepClass() {
	    while (--classidx >= 0) {
		EntryClass cand = entryClasses.get(classidx);
		if (!eclass.isAssignableFrom(cand))
		    continue;
		if (cand.getNumFields() > 0) {
		    cand = getDefiningClass(cand, cand.getNumFields() - 1);
		    Map<Object,List<SvcReg>>[] attrMaps = serviceByAttr.get(cand);
		    iter = attrMaps[attrMaps.length - 1].values().iterator();
		} else {
		    iter = null;
		    svcs = serviceByEmptyAttr.get(cand);
		    svcidx = svcs.size();
		}
		return true;
	    }
	    return false;
	}
    }

    /** Iterate over a singleton matching Item by serviceID. */
    private static class IDItemIter extends ItemIter {

	/** tmpl.serviceID != null */
	public IDItemIter(Template tmpl, SvcReg reg) {
	    super(tmpl);
	    if (reg != null &&
		(reg.leaseExpiration <= now || !matchItem(tmpl, reg.item))) {
		reg = null;
            }
            this.reg = reg;
	}

	/** Set reg to null */
	protected void step() {
	    reg = null;
	}
    }

    /** An event to be sent, and the listener to send it to. */
    private static final class EventTask implements Callable<Boolean>, Comparable<EventTask> {

	/** The event registration */
	private final EventReg reg;
	/** The sequence number of this event */
	private final long seqNo;
	/** The service id */
	private final ServiceID sid;
	/** The new state of the item, or null if deleted */
	private final Item item;
	/** The transition that fired */
	private final int transition;
        
        private final RegistrarProxy proxy;
        private final Registrar registrar;
        /* the time of the event */
        private final long now;
        /* The listener */
        private final RemoteEventListener listener;
	private final boolean newNotify;

	/** Simple constructor, except increments reg.seqNo. */
	public EventTask(EventReg reg,
			ServiceID sid,
			Item item,
			int transition,
			RegistrarProxy proxy,
			Registrar registrar,
			long now)
	{
	    this.reg = reg;
            this.listener = reg.listener;
            seqNo = reg.incrementAndGetSeqNo();
	    this.sid = sid;
	    this.item = item;
	    this.transition = transition;
            this.proxy = proxy;
            this.registrar = registrar;
            this.now = now;
	    this.newNotify = reg.newNotify;
	}

	/** Send the event */
        @Override
	public Boolean call() throws Exception {
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(
		    Level.FINE,
		    "notifying listener {0} of event {1}",
		    new Object[]{ listener, Long.valueOf(reg.eventID) });
	    }
	    try {
		if (!newNotify){
		    listener.notify(
			new RegistrarEvent(proxy,
			    reg.eventID,
			    seqNo,
			    (MarshalledObject) reg.handback,
			    sid,
			    transition,
			    item
			)
		    );
		} else {
		    listener.notify(
			new RegistrarEvent(proxy,
			    reg.eventID,
			    seqNo,
			    (MarshalledInstance) reg.handback,
			    sid,
			    transition,
			    item
			)
		    );
		}
                return Boolean.TRUE;
	    } catch (Throwable e) {
		switch (ThrowableConstants.retryable(e)) {
		case ThrowableConstants.BAD_OBJECT:
		    if (e instanceof Error) {
			logger.log(
			    Levels.HANDLED, "exception sending event", e);
			throw (Error) e;
		    }
		case ThrowableConstants.BAD_INVOCATION:
		case ThrowableConstants.UNCATEGORIZED:
		    /* If the listener throws UnknownEvent or some other
		     * definite exception, we can cancel the lease.
		     */
		    logger.log(Level.INFO, "exception sending event", e);
		    try {
			registrar.cancelEventLease(reg.eventID, reg.leaseID);
		    } catch (UnknownLeaseException ee) {
			logger.log(
			    Levels.HANDLED,
			    "exception canceling event lease",
			    e);
		    } catch (RemoteException ee) {
			logger.log(
			    Levels.HANDLED,
			    "The server has been shutdown",
			    e);
		    }
		}
                return Boolean.FALSE;
	    }
	}
        
        /**
         * Keep events going to the same listener ordered. 
         * This is inconsistent with Object.equals, it is simply intended to
         * order tasks by priority.
         * @param o
         * @return 
         */
        @Override
        public int compareTo(EventTask o) {
            if (this.now < o.now) return -1;
            if (this.now > o.now) return 1;
            if (this.seqNo < o.seqNo) return -1;
            if (this.seqNo > o.seqNo) return 1;
            return 0;
        }
    }

    /** Task for decoding multicast request packets. */
    private static final class DecodeRequestTask implements Runnable {
	/** The multicast packet to decode */
	private final DatagramPacket datagram;
	/** The decoder for parsing the packet */
	private final Discovery decoder;
        private final RegistrarImpl reggie;
        /* Ensures that no duplicate AddressTask is running */
        private final Set<AddressTask> runningTasks;

	public DecodeRequestTask(
                DatagramPacket datagram, Discovery decoder, RegistrarImpl reggie, Set<AddressTask> runningTasks) 
        {
	    this.datagram = datagram;
	    this.decoder = decoder;
            this.reggie = reggie;
            this.runningTasks = runningTasks;
	}

	/**
	 * Decodes this task's multicast request packet, spawning an
	 * AddressTask if the packet satisfies the configured constraints,
	 * matches this registrar's groups, and does not already contain this
	 * registrar's service ID in its list of known registrars.  This method
	 * assumes that the protocol version of the request has already been
	 * checked.
	 */
	public void run() {
	    MulticastRequest req;
	    try {
		req = decoder.decodeMulticastRequest(
		    datagram,
		    reggie.multicastRequestConstraints.getUnfulfilledConstraints(),
		    reggie.multicastRequestSubjectChecker, true);
	    } catch (Exception e) {
		if (!(e instanceof InterruptedIOException) &&
		    logger.isLoggable(Levels.HANDLED))
		{
		    logThrow(
			Levels.HANDLED,
			getClass().getName(),
			"run",
			"exception decoding multicast request from {0}:{1}",
			new Object[]{
			    datagram.getAddress(),
			    Integer.valueOf(datagram.getPort()) },
			e);
		}
		return;
	    }
	    String[] groups = req.getGroups();
	    if ((groups.length == 0 || overlap(reggie.memberGroups, groups)) &&
		indexOf(req.getServiceIDs(), reggie.myServiceID) < 0)
	    {
		try {
		    req.checkConstraints();
		} catch (Exception e) {
		    if (!(e instanceof InterruptedIOException) &&
			logger.isLoggable(Levels.HANDLED))
		    {
			logThrow(
			    Levels.HANDLED,
			    getClass().getName(),
			    "run",
			    "exception decoding multicast request from {0}:{1}",
			    new Object[]{
				datagram.getAddress(),
				Integer.valueOf(datagram.getPort()) },
			    e);
		    }
		    return;
		}
		AddressTask task = 
                        new AddressTask(req.getHost(), req.getPort(), reggie);
                if (runningTasks.add(task)) {
                    try {
                        task.run();
                    } finally {
                        runningTasks.remove(task);
                    }
                }
	    }
	}
    }

    /** Address for unicast discovery response. */
    private static final class AddressTask implements Runnable, Comparable<AddressTask> {

	/** The address */
	private final String host;
	/** The port */
	private final int port;
        
        private final RegistrarImpl reggie;
                
        private final int hash;
        

	/** Simple constructor */
	public AddressTask(
                String host, int port, RegistrarImpl reggie) 
        {
            this.reggie = reggie;
	    this.host = host;
	    this.port = port;
            int hash = 3;
            hash = 37 * hash + (this.host != null ? this.host.hashCode() : 0);
            hash = 37 * hash + this.port;
            this.hash = hash;
        }

	/** Two tasks are equal if they have the same address and port */
        @Override
	public int hashCode() {
	    return hash;
	}

	/** Two tasks are equal if they have the same address and port */
	public boolean equals(Object obj) {
	    if (!(obj instanceof AddressTask))
		return false;
	    AddressTask ua = (AddressTask)obj;
	    return host.equals(ua.host) && port == ua.port;
	}

	/** Connect and then process a unicast discovery request */
	public void run() {
	    InetAddress[] addr = new InetAddress[]{};
	    try {
		try {
		    addr = InetAddress.getAllByName(host);
		    if (addr == null)
			addr = new InetAddress[]{};
		} catch (UnknownHostException e) {
		    if (logger.isLoggable(Level.INFO)) {
			logThrow(
			    Level.INFO,
			    getClass().getName(),
			    "run",
			    "failed to resolve host {0};"
			    + " connection will still be attempted",
			    new Object[]{ host },
			    e);
		    }
		}
	        long deadline = DiscoveryConstraints.process(
	            reggie.rawUnicastDiscoveryConstraints).getConnectionDeadline(
	                Long.MAX_VALUE);
		long now = System.currentTimeMillis();
		if (deadline <= now)
		    throw new SocketTimeoutException("timeout expired before"
						     + " connection attempt");
		long timeLeft = deadline - now;
		int timeout = timeLeft >= Integer.MAX_VALUE ? 
		              Integer.MAX_VALUE : (int) timeLeft;
		// attempt connection even if host name was not resolved
                int len = addr.length;
		if (len == 0) {
		    attemptResponse(
                        new InetSocketAddress(host, port), timeout);
		    return;
		}
		for (int i = 0; i < len; i++) {
		    try {
			attemptResponse(
			     new InetSocketAddress(addr[i], port), timeout);
			return;
		    } catch (Exception e) {
			if (logger.isLoggable(Levels.HANDLED)) {
			    logThrow(Levels.HANDLED, getClass().getName(),
				     "run", "exception responding to {0}:{1}",
				     new Object[] {addr[i], Integer.valueOf(port)}
				     , e);
			}
		    }
		    timeLeft = deadline - System.currentTimeMillis();
		    timeout = timeLeft >= Integer.MAX_VALUE ? 
			      Integer.MAX_VALUE : (int) timeLeft;
		    if (timeLeft <= 0)
			throw new SocketTimeoutException("timeout expired"
		            + " before successful response");
		}
	    } catch (Exception e) {
		if (logger.isLoggable(Level.INFO)) {
		    logThrow(
			Level.INFO,
			getClass().getName(),
			"run",
			"failed to respond to {0} on port {1}",
			new Object[]{Arrays.asList(addr), Integer.valueOf(port)},
			e);
		}
	    }
	}

        /** attempt a connection to multicast request client */
        private void attemptResponse(InetSocketAddress addr, int timeout) 
            throws Exception 
        {
            Socket s = reggie.socketFactory.createSocket();
            try {
                s.connect(addr, timeout);
                reggie.respond(s);
            } finally {
                try {
                    s.close();
                } catch (IOException e) {
                    logger.log(Levels.HANDLED, "exception closing socket", e);
                }
            }
        }

        @Override
        public int compareTo(AddressTask o) {
            int hostCompare = host.compareTo(o.host);
            if ( hostCompare == -1) return -1;
            if ( hostCompare == 1) return 1;
            if (port < o.port) return -1;
            if (port > o.port) return 1;
            return 0;
        }
    }

    /** Socket for unicast discovery response. */
    private static final class SocketTask implements Runnable {

	/** The socket */
	public final Socket socket;
        public final RegistrarImpl reggie;

	/** Simple constructor */
	public SocketTask(Socket socket, RegistrarImpl reggie) {
	    this.socket = socket;
            this.reggie = reggie;
	}

	/** Process a unicast discovery request */
	public void run() {
	    try {
		reggie.respond(socket);
	    } catch (Exception e) {
	        if (logger.isLoggable(Levels.HANDLED)) {
		    logThrow(
			Levels.HANDLED,
			getClass().getName(),
			"run",
			"exception handling unicast discovery from {0}:{1}",
			new Object[] {
			socket.getInetAddress(),
			Integer.valueOf(socket.getPort())}
			,
			e);
		}
	    }
	}
    }

    /** Service lease expiration thread code */
    private static class ServiceExpire implements Runnable {
        final RegistrarImpl reggie;
        
	public ServiceExpire(RegistrarImpl reggie) {
	    this.reggie = reggie;
	}

	public void run() {
	    try {
		reggie.concurrentObj.writeLock();
	    } catch (ConcurrentLockException e) {
		return;
	    }
	    try {
		while (!Thread.currentThread().isInterrupted()) {
		    long now = System.currentTimeMillis();
		    while (true) {
			SvcReg reg = reggie.serviceByTime.first();
			reggie.minSvcExpiration = reg.leaseExpiration;
			if (reggie.minSvcExpiration > now)
			    break;
			reggie.deleteService(reg, now);
			reggie.addLogRecord(new ServiceLeaseCancelledLogObj(
					    reg.item.getServiceID(), reg.leaseID));
			if (logger.isLoggable(Level.FINE)) {
			    logger.log(
				Level.FINE,
				"expired service registration {0}",
				new Object[]{ reg.item.getServiceID() });
			}
		    }
                    try {
                        reggie.serviceNotifier.await(reggie.minSvcExpiration - now, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt(); //Restore interrupt.
                        return;
                    }
		}
	     } finally {
		 reggie.concurrentObj.writeUnlock();
	     }
	}
    }

    /** Event lease expiration thread code */
    private static class EventExpire implements Runnable {
        private final RegistrarImpl reggie;
	/** Create a daemon thread */
	public EventExpire(RegistrarImpl reggie) {
	    this.reggie = reggie;
	}

	public void run() {
	    try {
		reggie.concurrentObj.writeLock();
	    } catch (ConcurrentLockException e) {
		return;
	    }
	    try {
		while (!Thread.currentThread().isInterrupted()) {
		    long now = System.currentTimeMillis();
		    reggie.minEventExpiration = Long.MAX_VALUE;
		    for (EventReg reg = reggie.eventByTime.poll();
                            reg != null; reg = reggie.eventByTime.poll()) {
			if (reg.getLeaseExpiration() > now) {
			    reggie.minEventExpiration = reg.getLeaseExpiration();
			    break;
			}
			reggie.deleteEvent(reg);
			if (logger.isLoggable(Level.FINE)) {
			    logger.log(
				Level.FINE,
				"expired event registration {0} for {1}",
				new Object[]{ reg.leaseID, reg.listener });
			}
		    }
                    try {
                        reggie.eventNotifier.await(reggie.minEventExpiration - now, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt(); // restore
                        return;
                    }
		}
	     } finally {
		 reggie.concurrentObj.writeUnlock();
	     }
	}
    }

    /**
     * Termination thread code.  We do this in a separate thread to
     * avoid deadlock, because ActivationGroup.inactive will block until
     * in-progress RMI calls are finished.
     */
    private static class Destroy implements Runnable {
        private final RegistrarImpl reggie;
	/** Create a non-daemon thread */
	public Destroy(RegistrarImpl reggie) {
            this.reggie = reggie;
	}

	public void run() {
	    long now = System.currentTimeMillis();
	    long endTime = now + reggie.unexportTimeout;
	    if (endTime < 0)
		endTime = Long.MAX_VALUE;
	    boolean unexported = false;
	    /* first try unexporting politely */
	    while(!unexported && (now < endTime)) {
		unexported = reggie.serverExporter.unexport(false);
		if (!unexported) {
		    try {
			final long sleepTime = 
			    Math.min(reggie.unexportWait, endTime - now);
			Thread.currentThread().sleep(sleepTime);
			now = System.currentTimeMillis();
		    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // restore
			logger.log(
			     Levels.HANDLED, "exception during unexport wait", e);
		    }
		}
	    }
	    /* if still not unexported, forcibly unexport */
            if(!unexported) {
		reggie.serverExporter.unexport(true);
            }

	    /* all threads must terminate before deleting persistent store */
	    reggie.serviceExpirer.interrupt();
	    reggie.eventExpirer.interrupt();
	    reggie.unicaster.interrupt();
	    reggie.multicaster.interrupt();
	    reggie.announcer.interrupt();
	    reggie.snapshotter.interrupt();
            reggie.eventNotifierExec.shutdown();
            List<Runnable> cancelledTasks = reggie.discoveryResponseExec.shutdownNow();
	    reggie.joiner.terminate();
	    reggie.discoer.terminate();
	    try {
		reggie.serviceExpirer.join();
		reggie.eventExpirer.join();
		reggie.unicaster.join();
		reggie.multicaster.join();
		reggie.announcer.join();
		reggie.snapshotter.join();
	    } catch (InterruptedException e) {
	    }
	    reggie.closeRequestSockets(cancelledTasks);
	    if (reggie.log != null) {
		reggie.log.deletePersistentStore();
		logger.finer("deleted persistence directory");
	    }
	    if (reggie.activationID != null) {
		try {
		    ActivationGroup.inactive(reggie.activationID, reggie.serverExporter);
		} catch (Exception e) {
		    logger.log(Level.INFO, "exception going inactive", e);
		}
	    }
	    if (reggie.lifeCycle != null) {
		reggie.lifeCycle.unregister(reggie);
	    }
	    if (reggie.loginContext != null) {
		try {
		    reggie.loginContext.logout();
		} catch (LoginException e) {
		    logger.log(Level.INFO, "logout failed", e);
		}
	    }
	    logger.info("Reggie shutdown completed");
	}
    }

    /** Multicast discovery request thread code. */
    private static class Multicast implements Runnable, Interruptable {
        private final RegistrarImpl reggie;
	/** Multicast group address used by multicast requests */
	private final InetAddress requestAddr;
	/** Multicast socket to receive packets */
	private final MulticastSocket socket;
	/** Interfaces for which configuration failed */
	private final List<NetworkInterface> failedInterfaces;
        
        private final Set<AddressTask> runningTasks;
        
        private volatile boolean interrupted = false;

	/**
	 * Create a high priority daemon thread.  Set up the socket now
	 * rather than in run, so that we get any exception up front.
	 */
	public Multicast(RegistrarImpl reggie) throws IOException {
            this.runningTasks = new ConcurrentSkipListSet<AddressTask>();
            this.reggie = reggie;
            List<NetworkInterface> failedInterfaces = new ArrayList<NetworkInterface>();
	    if (reggie.multicastInterfaces != null && reggie.multicastInterfaces.length == 0)
	    {
		requestAddr = null;
		socket = null;
                this.failedInterfaces = failedInterfaces;
		return;
	    }
	    requestAddr = Constants.getRequestAddress();
	    socket = new MulticastSocket(Constants.discoveryPort);
	    if (reggie.multicastInterfaces != null) {
		Level failureLogLevel = reggie.multicastInterfacesSpecified ?
		    Level.WARNING : Levels.HANDLED;
                int l = reggie.multicastInterfaces.length;
		for (int i = 0; i < l; i++) {
		    NetworkInterface nic = reggie.multicastInterfaces[i];
		    try {
			socket.setNetworkInterface(nic);
			socket.joinGroup(requestAddr);
		    } catch (IOException e) {
			failedInterfaces.add(nic);
			if (logger.isLoggable(failureLogLevel)) {
			    logThrow(
				failureLogLevel,
				getClass().getName(),
				"<init>",
				"exception enabling {0}",
				new Object[]{ nic },
				e);
			}
		    }
		}
	    } else {
		try {
		    socket.joinGroup(requestAddr);
		} catch (IOException e) {
		    failedInterfaces.add(null);
		    logger.log(
			Level.WARNING,
			"exception enabling default interface", e);
		}
	    }
            this.failedInterfaces = failedInterfaces;
	}

	public void run() {
	    if (reggie.multicastInterfaces != null && reggie.multicastInterfaces.length == 0)
	    {
		return;
	    }
	    byte[] buf = new byte[
		reggie.multicastRequestConstraints.getMulticastMaxPacketSize(
		    DEFAULT_MAX_PACKET_SIZE)];
	    DatagramPacket dgram = new DatagramPacket(buf, buf.length);
	    long retryTime =
		System.currentTimeMillis() + reggie.multicastInterfaceRetryInterval;
	    while (!interrupted) {
		try {
		    int timeout = 0;
		    if (!failedInterfaces.isEmpty()) {
			timeout =
			    (int) (retryTime - System.currentTimeMillis());
			if (timeout <= 0) {
			    retryFailedInterfaces();
			    if (failedInterfaces.isEmpty()) {
				timeout = 0;
			    } else {
				timeout = reggie.multicastInterfaceRetryInterval;
				retryTime =
				    System.currentTimeMillis() + timeout;
			    }
			}
		    }
		    socket.setSoTimeout(timeout);
		    dgram.setLength(buf.length);
		    try {
			socket.receive(dgram);
		    } catch (NullPointerException e) {
			break; // workaround for bug 4190513
		    }

		    int pv;
		    try {
			pv = ByteBuffer.wrap(dgram.getData(),
					     dgram.getOffset(),
					     dgram.getLength()).getInt();
		    } catch (BufferUnderflowException e) {
			throw new DiscoveryProtocolException(null, e);
		    }
		    reggie.multicastRequestConstraints.checkProtocolVersion(pv);
		    reggie.discoveryResponseExec.execute(
                            new DecodeRequestTask(
                                    dgram, 
                                    reggie.getDiscovery(pv), 
                                    reggie,
                                    runningTasks
                            )
                    );

		    buf = new byte[buf.length];
		    dgram = new DatagramPacket(buf, buf.length);

		} catch (SocketTimeoutException e) {
		    // retry failed network interfaces in next iteration
		} catch (InterruptedIOException e) {
		    break;
		} catch (Exception e) {
		    if (interrupted) {
			break;
		    }
		    logger.log(Levels.HANDLED,
			       "exception receiving multicast request", e);
		}
	    }
	    socket.close();
	}

	public void interrupt() {
	    // close socket to interrupt MulticastSocket.receive operation
            interrupted = true;
	    if (socket != null) socket.close();
	}

	/**
	 * Attempts to configure each interface contained in the
	 * failedInterfaces list, removing it from the list if configuration
	 * succeeds.  The null value is used to indicate the default network
	 * interface.
	 */
	private void retryFailedInterfaces() {
	    for (Iterator<NetworkInterface> i = failedInterfaces.iterator(); i.hasNext(); ) {
		NetworkInterface nic = i.next();
		try {
		    if (nic != null) {
			socket.setNetworkInterface(nic);
		    }
		    socket.joinGroup(requestAddr);
		    i.remove();

		    Level l = reggie.multicastInterfacesSpecified ?
			Level.INFO : Level.FINE;
		    if (logger.isLoggable(l)) {
			if (nic != null) {
			    logger.log(l, "enabled {0}", new Object[]{ nic });
			} else {
			    logger.log(l, "enabled default interface");
			}
		    }
		} catch (IOException e) {
		    // keep nic in failedInterfaces
		}
	    }
	}
    }

    /** Unicast discovery request thread code. */
    private static class Unicast implements Runnable, Interruptable {
        private static final Boolean arbitraryPort;
        
        static {
            arbitraryPort = AccessController.doPrivileged(
                new PrivilegedAction<Boolean>(){

                    @Override
                    public Boolean run() {
                        return Boolean.valueOf(
                            Boolean.getBoolean(
                                "net.jini.core.lookup.ServiceRegistrar.portAbitraryIfInUse"
                            )
                        );
                    }
                
                }
            );
        }
        private final RegistrarImpl reggie;
	/** Server socket to accepts connections on. */
	private final ServerSocket listen;
	/** Listen port */
	public final int port;
        
        private volatile boolean interrupted = false;
        
	/**
	 * Create a daemon thread.  Set up the socket now rather than in run,
	 * so that we get any exception up front.
	 */
	public Unicast(RegistrarImpl reggie, int port) throws IOException {
            this.reggie = reggie;
            ServerSocket listen = null;
            boolean ephemeral = false;
            if (port == 0) {
		try {
		    listen = reggie.serverSocketFactory.createServerSocket(Constants.discoveryPort);
                    port = Constants.discoveryPort;
		} catch (IOException e) {
		    logger.log(
			Levels.HANDLED, "failed to bind to default port", e);
		}
	    }
	    if (listen == null) {
                try {
                    listen = reggie.serverSocketFactory.createServerSocket(port);
                } catch (IOException e){
                    logger.log(Level.INFO, "failed to bind to port " + port, e);
                    if (arbitraryPort){
                        listen = reggie.serverSocketFactory.createServerSocket(0);
                        ephemeral = true;
                    } else {
                        throw e;
                    }
                }
	    }
            port = listen.getLocalPort();
            logger.log(Level.INFO, "Reggie Unicast Discovery listening on port {0}", port);
            this.listen = listen;
	    this.port = port;
            if (ephemeral) reggie.unicastPort = port;
        }

	public void run() {
	    while (!interrupted) {
		try {
		    Socket socket = listen.accept();
		    if (interrupted) {
			try {
			    socket.close();
			} catch (IOException e) {
			    logger.log(
				Levels.HANDLED, "exception closing socket", e);
			}
			break;
		    }
		    reggie.discoveryResponseExec.execute(new SocketTask(socket, reggie));
		} catch (InterruptedIOException e) {
		    break;
		} catch (Exception e) {
		    logger.log(
			Levels.HANDLED, "exception listening on socket", e);
		}
		/* if we fail in any way, just forget about it */
	    }
	    try {
		listen.close();
	    } catch (IOException e) {
		logger.log(
		    Levels.HANDLED, "exception closing server socket", e);
	    }
	}

	/* This is a workaround for Thread.interrupt not working on
	 * ServerSocket.accept on all platforms.  ServerSocket.close
	 * can't be used as a workaround, because it also doesn't work
	 * on all platforms.
	 */
	public void interrupt() {
                interrupted = true;
            AccessController.doPrivileged( new PrivilegedAction(){
                public Object run(){
                    try {
                Socket s = reggie.socketFactory.createSocket(LocalHostLookup.getLocalHost(), port);
                s.close();
	    } catch (IOException e) {
                    } finally {
                        return null;
	    }
	}
            });
    }
    }

    /** Multicast discovery announcement thread code. */
    private static class Announce implements Runnable {
        private final RegistrarImpl reggie;
	/** Multicast socket to send packets on */
	private final MulticastSocket socket;
	/** Cached datagram packets */
	private DatagramPacket[] dataPackets = null;
	/** LookupLocator associated with cached datagram packets */
	private LookupLocator lastLocator;
	/** Groups associated with cached datagram packets */
	private String[] lastGroups;

	/**
	 * Create a daemon thread.  Set up the socket now rather than in run,
	 * so that we get any exception up front.
	 */
	public Announce(RegistrarImpl reggie) throws IOException {
            this.reggie = reggie;
	    if (reggie.multicastInterfaces == null || reggie.multicastInterfaces.length > 0)
	    {
		socket = new MulticastSocket();
		if (Constants.GLOBAL_ANNOUNCE){
		    socket.setTimeToLive(
			reggie.multicastAnnouncementConstraints.getMulticastTimeToLive(
			    255));
		} else {
		    socket.setTimeToLive(
			reggie.multicastAnnouncementConstraints.getMulticastTimeToLive(
			    DEFAULT_MULTICAST_TTL));
		    
		}
	    } else {
		socket = null;
	    }
	}

	public void run() {
            Thread currentThread = Thread.currentThread();
            synchronized (currentThread){
                if (reggie.multicastInterfaces != null && reggie.multicastInterfaces.length == 0)
                {
                    return;
                }
                try {
                    while (!currentThread.isInterrupted() && announce(reggie.memberGroups)) {
                        currentThread.wait(reggie.multicastAnnouncementInterval);
                    }
                } catch (InterruptedException e) {
                    currentThread.interrupt(); //restore
                    return;
                } finally {
                    if (reggie.memberGroups.length > 0)
                        announce(new String[0]);//send NO_GROUPS just before shutdown
                    socket.close();
                }
            }
	}

	/**
	 * Announce membership in the specified groups, and return false if
	 * interrupted, otherwise return true.  This method is run from
	 * synchronized run method in thread.
	 */
	private boolean announce(String[] groups) {
	    if (dataPackets == null || !lastLocator.equals(reggie.myLocator) ||
	        !Arrays.equals(lastGroups, groups))
	    {
	        List packets = new ArrayList();
	        Discovery disco;
	        try {
		    disco = reggie.getDiscovery(reggie.multicastAnnouncementConstraints
					 .chooseProtocolVersion());
	        } catch (DiscoveryProtocolException e) {
		    throw new AssertionError(e);
	        }
                LookupLocator myLocator = reggie.myLocator;// Atomic
	        EncodeIterator ei = disco.encodeMulticastAnnouncement(
		    new MulticastAnnouncement(reggie.announcementSeqNo.getAndIncrement(),
					  myLocator.getHost(),
					  myLocator.getPort(),
					  groups,
					  reggie.myServiceID),
		    reggie.multicastAnnouncementConstraints
		    .getMulticastMaxPacketSize(DEFAULT_MAX_PACKET_SIZE),
		    reggie.multicastAnnouncementConstraints
		    .getUnfulfilledConstraints());
	        while (ei.hasNext()) {
		    try {
		        packets.addAll(Arrays.asList(ei.next()));
		    } catch (Exception e) {
			logger.log( (e instanceof 
				     UnsupportedConstraintException)
				    ? Levels.HANDLED : Level.INFO,
				    "exception encoding multicast"
				    + " announcement", e);
		    }
		}
		lastLocator = myLocator;
		lastGroups = groups;
		dataPackets = (DatagramPacket[]) packets.toArray(
		    new DatagramPacket[packets.size()]);
	    }
	    try {
	        send(dataPackets);
	    } catch (InterruptedIOException e) {
		return false;
	    } catch (IOException e) {
		logger.log(
		    Level.INFO, "exception sending multicast announcement", e);
	    }
	    return true;
	}

	/**
	 * Attempts to multicast the given packets on each of the configured
	 * network interfaces.
	 */
	private void send(DatagramPacket[] packets)
	    throws InterruptedIOException
	{
	    if (reggie.multicastInterfaces != null) {
		Level failureLogLevel = reggie.multicastInterfacesSpecified ?
		    Level.WARNING : Levels.HANDLED;
                int l = reggie.multicastInterfaces.length;
		for (int i = 0; i < l; i++) {
		    send(packets, reggie.multicastInterfaces[i], failureLogLevel);
		}
	    } else {
		send(packets, null, Level.WARNING);
	    }
	}

	/**
	 * Attempts to multicast the given packets on the specified network
	 * interface, logging failures at the given logging level.  If the
	 * specified network interface is null, then the default interface is
	 * used.
	 */
	private void send(DatagramPacket[] packets,
			  NetworkInterface nic,
			  Level failureLogLevel)
	    throws InterruptedIOException
	{
	    if (nic != null) {
		try {
		    socket.setNetworkInterface(nic);
		} catch (SocketException e) {
		    if (logger.isLoggable(failureLogLevel)) {
			logThrow(
			    failureLogLevel,
			    getClass().getName(),
			    "send",
			    "exception setting {0}",
			    new Object[]{ nic },
			    e);
		    }
		    return;
		}
	    }
	    for (int i = 0; i < packets.length; i++) {
		try {
		    socket.send(packets[i]);
		} catch (InterruptedIOException e) {
		    throw e;
		} catch (IOException e) {
		    if (nic != null) {
			if (logger.isLoggable(failureLogLevel)) {
			    logThrow(
				failureLogLevel,
				getClass().getName(),
				"send",
				"exception sending packet on {0}",
				new Object[]{ nic },
				e);
			}
		    } else {
			logger.log(
			    failureLogLevel,
			    "exception sending packet on default interface",
			    e);
		    }
		}
	    }
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
     * of processing is achieved in the Registrar through a "reader/writer"
     * mutex construct. This construct allows only one writer at any one
     * time; but allows an unlimited number of simultaneous readers as
     * long as no writer has locked the mutex. During steady-state, it is
     * anticipated that there will be far more readers (e.g. lookups) in use 
     * than writers (e.g. add/mod/del Attributes). Since the process of
     * taking a snapshot can be time-consuming, if the whole snapshot-taking
     * process occupies that single writer mutex, then a significant number
     * of read requests will be un-necessarily blocked; possibly resulting
     * in an unacceptable degradation in response time. 
     * <p>
     * It is for the above reason that the process of taking a snapshot is
     * performed in a separate thread. The thread waits on the monitor
     * belonging to the snapshotNotifier instance until it is notified
     * (or "signalled") that a snapshot must be taken. The notification
     * is sent by another thread, created by the Registrar, which
     * determines when the conditions are right for a snapshot. The
     * notification takes the form of an interrupt indicating that the
     * Snapshot monitor is available. Although the interrupt is sent 
     * while the writer mutex is locked, the act of sending the notification
     * is less time-consuming than the act of taking the snapshot itself.
     * When the thread receives a notification, it awakens and requests a
     * lock on the reader mutex (this is all done in the readerWait() method).
     * Because a reader -- not a writer -- mutex is locked, read-only
     * processes still have access to the system state, so lookups can be
     * performed; and the reader mutex prevents changes to the data while
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
    private static class Snapshot implements Runnable {
        RegistrarImpl reggie;
	/** Create a daemon thread */
	public Snapshot(RegistrarImpl reggie) {
            this.reggie = reggie;
	}

	public void run() {
	    if (reggie.log == null) {
		return;
	    }
            reggie.concurrentObj.readLock();
	    try {
		while (!Thread.currentThread().isInterrupted()) {
                    try {
                        reggie.snapshotNotifier.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt(); // restore
                        return;
                    }
                    try {
                        reggie.log.snapshot();
                        reggie.logFileSize.set(0);
                    } catch (Exception e) {
                        // InterruptedException is never thrown in try
                        if (Thread.currentThread().isInterrupted())
                            return;
                        logger.log(Level.WARNING, "snapshot failed", e);
                    }
		}
	    } finally {
		reggie.concurrentObj.readUnlock();
	    }
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public Object getServiceProxy() throws NoSuchObjectException {
        concurrentObj.readLock();
        try {
            return proxy;
        } finally {
            concurrentObj.readUnlock();
        }
    }

    // This method's javadoc is inherited from an interface of this class
    public Object getProxy() {
        concurrentObj.readLock();
        try {
            return myRef;
        } finally {
            concurrentObj.readUnlock();
        }
    }

    // This method's javadoc is inherited from an interface of this class
    public TrustVerifier getProxyVerifier() throws NoSuchObjectException {
        concurrentObj.readLock();
        try {
            return new ProxyVerifier(myRef, myServiceID);
        } finally {
            concurrentObj.readUnlock();
        }
    }

    // This method's javadoc is inherited from an interface of this class
    public ServiceRegistration register(Item nitem, long leaseDuration)
        throws NoSuchObjectException
    {	
	concurrentObj.writeLock();
	try {
	    ServiceRegistration reg = registerDo(nitem, leaseDuration);
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(
		    Level.FINE,
		    "registered instance of {0} as {1}",
		    new Object[]{
			nitem.serviceType.getName(), reg.getServiceID() });
	    }
	    return reg;
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public MarshalledWrapper lookup(Template tmpl) throws NoSuchObjectException
    {	
	concurrentObj.readLock();
	try {
	    return lookupDo(tmpl);
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public Matches lookup(Template tmpl, int maxMatches)
	throws NoSuchObjectException
    {	
	concurrentObj.readLock();
	try {
	    return lookupDo(tmpl, maxMatches);
	} finally {
	    concurrentObj.readUnlock();
	}
    }
    
    // This method's javadoc is inherited from an interface of this class
    public Object [] lookUp(Template tmpl, int maxProxys)
	throws NoSuchObjectException
    {	
	concurrentObj.readLock();
	try {
	    return lookupDo(tmpl, maxProxys).getProxys();
	} finally {
	    concurrentObj.readUnlock();
	}
    }
    
    // This method's javadoc is inherited from an interface of this class
    public EventRegistration notify(Template tmpl,
				    int transitions,
				    RemoteEventListener listener,
				    MarshalledObject handback,
				    long leaseDuration)
	throws RemoteException
    {	
	concurrentObj.writeLock();
	try {
	    EventRegistration reg = notifyDo(
		tmpl, transitions, listener, handback, leaseDuration, false);
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(
		    Level.FINE,
		    "registered event listener {0} as {1}",
		    new Object[]{
			listener,
			((ReferentUuid) reg.getLease()).getReferentUuid()
		    });
	    }
	    return reg;
	} finally {
	    concurrentObj.writeUnlock();
	}
    }
    
    // This method's javadoc is inherited from an interface of this class
    public EventRegistration notiFy(Template tmpl,
				    int transitions,
				    RemoteEventListener listener,
				    MarshalledInstance handback,
				    long leaseDuration)
	throws RemoteException
    {	
	concurrentObj.writeLock();
	try {
	    EventRegistration reg = notifyDo(
		tmpl, transitions, listener, handback, leaseDuration, true);
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(
		    Level.FINE,
		    "registered event listener {0} as {1}",
		    new Object[]{
			listener,
			((ReferentUuid) reg.getLease()).getReferentUuid()
		    });
	    }
	    return reg;
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public EntryClassBase[] getEntryClasses(Template tmpl)
        throws NoSuchObjectException
    {
	concurrentObj.readLock();
	try {
	    return getEntryClassesDo(tmpl);
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public Object[] getFieldValues(Template tmpl, int setIndex, int field)
        throws NoSuchObjectException
    {
	concurrentObj.readLock();
	try {
	    return getFieldValuesDo(tmpl, setIndex, field);
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public ServiceTypeBase[] getServiceTypes(Template tmpl, String prefix)
        throws NoSuchObjectException
    {
	concurrentObj.readLock();
	try {
	    return getServiceTypesDo(tmpl, prefix);
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public LookupLocator getLocator() throws NoSuchObjectException {
        concurrentObj.readLock();
        try {
            return myLocator;
        } finally {
            concurrentObj.readUnlock();
        }
    }

    // This method's javadoc is inherited from an interface of this class
    public Object getAdmin() throws NoSuchObjectException {
        concurrentObj.readLock();
        try {
            return AdminProxy.getInstance(myRef, myServiceID);
        } finally {
            concurrentObj.readUnlock();
        }
    }

    // This method's javadoc is inherited from an interface of this class
    public void addAttributes(ServiceID serviceID,
			      Uuid leaseID,
			      EntryRep[] attrSets)
	throws NoSuchObjectException, UnknownLeaseException
    {
	concurrentObj.writeLock();
	try {
	    if (serviceID.equals(myServiceID))
	        throw new SecurityException("privileged service id");
	    addAttributesDo(serviceID, leaseID, attrSets);
	    addLogRecord(new AttrsAddedLogObj(serviceID, leaseID, attrSets));
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void modifyAttributes(ServiceID serviceID,
				 Uuid leaseID,
				 EntryRep[] attrSetTmpls,
				 EntryRep[] attrSets)
	throws NoSuchObjectException, UnknownLeaseException
    {
	concurrentObj.writeLock();
	try {
	    if (serviceID.equals(myServiceID))
	        throw new SecurityException("privileged service id");
	    modifyAttributesDo(serviceID, leaseID, attrSetTmpls, attrSets);
	    addLogRecord(new AttrsModifiedLogObj(serviceID, leaseID,
						 attrSetTmpls, attrSets));
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void setAttributes(ServiceID serviceID,
			      Uuid leaseID,
			      EntryRep[] attrSets)
	throws NoSuchObjectException, UnknownLeaseException
    {
	concurrentObj.writeLock();
	try {
	    if (serviceID.equals(myServiceID))
	        throw new SecurityException("privileged service id");
	    setAttributesDo(serviceID, leaseID, attrSets);
	    addLogRecord(new AttrsSetLogObj(serviceID, leaseID, attrSets));
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void cancelServiceLease(ServiceID serviceID, Uuid leaseID)
	throws NoSuchObjectException, UnknownLeaseException
    {
	concurrentObj.writeLock();
	try {
	    cancelServiceLeaseDo(serviceID, leaseID);
	    addLogRecord(new ServiceLeaseCancelledLogObj(serviceID, leaseID));
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(
		    Level.FINE,
		    "cancelled service registration {0}",
		    new Object[]{ serviceID });
	    }
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public long renewServiceLease(ServiceID serviceID,
				  Uuid leaseID,
				  long renewDuration)
	throws NoSuchObjectException, UnknownLeaseException
    {	
	concurrentObj.priorityWriteLock();
	try {
	    return renewServiceLeaseDo(serviceID, leaseID, renewDuration);
	    /* addLogRecord is in renewServiceLeaseDo */
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void cancelEventLease(long eventID, Uuid leaseID)
	throws NoSuchObjectException, UnknownLeaseException
    {
	concurrentObj.writeLock();
	try {
	    cancelEventLeaseDo(eventID, leaseID);
	    addLogRecord(new EventLeaseCancelledLogObj(eventID, leaseID));
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(
		    Level.FINE,
		    "cancelled event registration {0}",
		    new Object[]{ leaseID });
	    }
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public long renewEventLease(long eventID, Uuid leaseID, long renewDuration)
	throws NoSuchObjectException, UnknownLeaseException
    {	
	concurrentObj.priorityWriteLock();
	try {
	    return renewEventLeaseDo(eventID, leaseID, renewDuration);
	    /* addLogRecord is in renewEventLeaseDo */
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public RenewResults renewLeases(Object[] regIDs,
				    Uuid[] leaseIDs,
				    long[] renewDurations)
        throws NoSuchObjectException
    {	
	concurrentObj.priorityWriteLock();
	try {
	    return renewLeasesDo(regIDs, leaseIDs, renewDurations);
	    /* addLogRecord is in renewLeasesDo */
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public Exception[] cancelLeases(Object[] regIDs, Uuid[] leaseIDs)
        throws NoSuchObjectException
    {
	concurrentObj.writeLock();
	try {
	    Exception[] exceptions = cancelLeasesDo(regIDs, leaseIDs);
	    addLogRecord(new LeasesCancelledLogObj(regIDs, leaseIDs));
	    if (logger.isLoggable(Level.FINE)) {
		for (int i = 0; i < regIDs.length; i++) {
		    if (exceptions != null && exceptions[i] != null) {
			continue;
		    }
		    if (regIDs[i] instanceof ServiceID) {
			logger.log(
			    Level.FINE,
			    "cancelled service registration {0}",
			    new Object[]{ regIDs[i] });
		    } else {
			logger.log(
			    Level.FINE,
			    "cancelled event registration {0}",
			    new Object[]{ leaseIDs[i] });
		    }
		}
	    }
	    return exceptions;
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public Entry[] getLookupAttributes() throws NoSuchObjectException {
	concurrentObj.readLock();
	try {
	    /* no need to clone, never modified once created */
	    return lookupAttrs;
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void addLookupAttributes(Entry[] attrSets) throws RemoteException {
	concurrentObj.writeLock();
	try {
	    EntryRep[] attrs = EntryRep.toEntryRep(attrSets, true);
	    addAttributesDo(myServiceID, myLeaseID, attrs);
	    joiner.addAttributes(attrSets);
	    lookupAttrs = joiner.getAttributes();
	    addLogRecord(new LookupAttributesChangedLogObj(lookupAttrs));
	} catch (UnknownLeaseException e) {
	    throw new AssertionError("Self-registration never expires");
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void modifyLookupAttributes(Entry[] attrSetTemplates,
				       Entry[] attrSets)
	throws RemoteException
    {
	concurrentObj.writeLock();
	try {
	    EntryRep[] tmpls = EntryRep.toEntryRep(attrSetTemplates, false);
	    EntryRep[] attrs = EntryRep.toEntryRep(attrSets, false);
	    modifyAttributesDo(myServiceID, myLeaseID, tmpls, attrs);
	    joiner.modifyAttributes(attrSetTemplates, attrSets, true);
	    lookupAttrs = joiner.getAttributes();
	    addLogRecord(new LookupAttributesChangedLogObj(lookupAttrs));
	} catch (UnknownLeaseException e) {
	    throw new AssertionError("Self-registration never expires");
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public String[] getLookupGroups() throws NoSuchObjectException {
	concurrentObj.readLock();
	try {
	    /* no need to clone, never modified once created */
	    return lookupGroups;
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void addLookupGroups(String[] groups) throws NoSuchObjectException {
	concurrentObj.writeLock();
	try {
	    DiscoveryGroupManagement dgm = (DiscoveryGroupManagement) discoer;
	    try {
		dgm.addGroups(groups);
	    } catch (IOException e) {
		throw new RuntimeException(e.toString());
	    }
	    lookupGroups = dgm.getGroups();
	    addLogRecord(new LookupGroupsChangedLogObj(lookupGroups));
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.log(
		    Level.CONFIG,
		    "added lookup groups {0}",
		    new Object[]{ Arrays.asList(groups) });
	    }
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void removeLookupGroups(String[] groups)
	throws NoSuchObjectException
    {
	concurrentObj.writeLock();
	try {
	    DiscoveryGroupManagement dgm = (DiscoveryGroupManagement) discoer;
	    dgm.removeGroups(groups);
	    lookupGroups = dgm.getGroups();
	    addLogRecord(new LookupGroupsChangedLogObj(lookupGroups));
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.log(
		    Level.CONFIG,
		    "removed lookup groups {0}",
		    new Object[]{ Arrays.asList(groups) });
	    }
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void setLookupGroups(String[] groups) throws NoSuchObjectException {
	concurrentObj.writeLock();
	try {
	    DiscoveryGroupManagement dgm = (DiscoveryGroupManagement) discoer;
	    try {
		dgm.setGroups(groups);
	    } catch (IOException e) {
		throw new RuntimeException(e.toString());
	    }
	    lookupGroups = dgm.getGroups();
	    addLogRecord(new LookupGroupsChangedLogObj(lookupGroups));
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.log(
		    Level.CONFIG,
		    "set lookup groups {0}",
		    new Object[]{ 
			(groups != null) ? Arrays.asList(groups) : null });
	    }
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public LookupLocator[] getLookupLocators() throws NoSuchObjectException {
	concurrentObj.readLock();
	try {
	    /* no need to clone, never modified once created */
	    return lookupLocators;
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void addLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {	
	locators = prepareLocators(locators, locatorPreparer, false);
	concurrentObj.writeLock();
	try {	    
	    DiscoveryLocatorManagement dlm = 
		(DiscoveryLocatorManagement) discoer;
	    dlm.addLocators(locators);
	    lookupLocators = dlm.getLocators();
	    addLogRecord(new LookupLocatorsChangedLogObj(lookupLocators));
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.log(
		    Level.CONFIG,
		    "added lookup locators {0}",
		    new Object[]{ Arrays.asList(locators) });
	    }
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void removeLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {	
    	locators = prepareLocators(locators, locatorPreparer, false);
	concurrentObj.writeLock();
	try {	    
	    DiscoveryLocatorManagement dlm = 
		(DiscoveryLocatorManagement) discoer;
	    dlm.removeLocators(locators);
	    lookupLocators = dlm.getLocators();
	    addLogRecord(new LookupLocatorsChangedLogObj(lookupLocators));
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.log(
		    Level.CONFIG,
		    "removed lookup locators {0}",
		    new Object[]{ Arrays.asList(locators) });
	    }
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void setLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
    	locators = prepareLocators(locators, locatorPreparer, false);
	concurrentObj.writeLock();
	try {
	    DiscoveryLocatorManagement dlm = 
		(DiscoveryLocatorManagement) discoer;
	    dlm.setLocators(locators);
	    lookupLocators = dlm.getLocators();
	    addLogRecord(new LookupLocatorsChangedLogObj(lookupLocators));
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.log(
		    Level.CONFIG,
		    "set lookup locators {0}",
		    new Object[]{ Arrays.asList(locators) });
	    }
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void addMemberGroups(String[] groups) throws NoSuchObjectException {
	concurrentObj.writeLock();
	try {
	    for (int i = 0; i < groups.length; i++) {
	        if (indexOf(memberGroups, groups[i]) < 0)
		    memberGroups = (String[])arrayAdd(memberGroups, groups[i]);
	    }
	    synchronized (announcer) {
		announcer.notify();
	    }
	    addLogRecord(new MemberGroupsChangedLogObj(memberGroups));
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.log(
		    Level.CONFIG,
		    "added member groups {0}",
		    new Object[]{ Arrays.asList(groups) });
	    }
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void removeMemberGroups(String[] groups)
	throws NoSuchObjectException
    {
	concurrentObj.writeLock();
	try {
	    for (int i = 0; i < groups.length; i++) {
                int j = indexOf(memberGroups, groups[i]);
	        if (j >= 0)
		    memberGroups = (String[])arrayDel(memberGroups, j);
	    }
	    synchronized (announcer) {
		announcer.notify();
	    }
	    addLogRecord(new MemberGroupsChangedLogObj(memberGroups));
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.log(
		    Level.CONFIG,
		    "removed member groups {0}",
		    new Object[]{ Arrays.asList(groups) });
	    }
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public String[] getMemberGroups() throws NoSuchObjectException {
	concurrentObj.readLock();
	try {
	    /* no need to clone, never modified once created */
	    return memberGroups;
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void setMemberGroups(String[] groups) throws NoSuchObjectException {
	concurrentObj.writeLock();
	try {
	    memberGroups = (String[])removeDups(groups);
	    addLogRecord(new MemberGroupsChangedLogObj(memberGroups));
	    synchronized (announcer) {
		announcer.notify();
	    }
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.log(
		    Level.CONFIG,
		    "set member groups {0}",
		    new Object[]{ Arrays.asList(groups) });
	    }
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public int getUnicastPort() throws NoSuchObjectException {
	concurrentObj.readLock();
	try {
	    return unicastPort;
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void setUnicastPort(int port) throws IOException,RemoteException {
	concurrentObj.writeLock();
	try {
	    if (port == unicastPort)
		return;
	    if ((port == 0 && unicast.port == Constants.discoveryPort) ||
		port == unicast.port)
	    {
		unicastPort = port;
		addLogRecord(new UnicastPortSetLogObj(port));
		return;
	    }
	    /* create a UnicastThread that listens on the new port */
            unicast = new Unicast(this, port);
	    Thread newUnicaster = new InterruptedStatusThread( unicast , "unicast request");
            newUnicaster.setDaemon(false);
	    /* terminate the current UnicastThread listening on the old port */
	    unicaster.interrupt();
	    try {
		unicaster.join();
	    } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore the interrupt.
            }
	    /* start the UnicastThread listening on the new port */
	    unicaster = newUnicaster;
	    unicaster.start();
	    unicastPort = port;
	    myLocator = (proxy instanceof RemoteMethodControl) ?
		new ConstrainableLookupLocator(
		    myLocator.getHost(), unicast.port, null) :
		new LookupLocator(myLocator.getHost(), unicast.port);
	    synchronized (announcer) {
		announcer.notify();
	    }
	    addLogRecord(new UnicastPortSetLogObj(port));
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.log(
		    Level.CONFIG,
		    "changed unicast discovery port to {0}",
		    new Object[]{ Integer.valueOf(unicast.port) });
	    }
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void destroy() throws RemoteException {
	concurrentObj.priorityWriteLock();
	try {
	    logger.info("starting Reggie shutdown");
	    /* unregister with activation system if activatable */
	    if (activationID != null) {
		try {
		    activationSystem.unregisterObject(activationID);
		} catch (ActivationException e) {
		    logger.log(Levels.HANDLED,
			       "exception unregistering activation ID", e);
		} catch (RemoteException e) {
		    logger.log(Level.WARNING,
			       "aborting Reggie shutdown", e);
		    throw e;
		}
	    }	    
	    Thread destroy = new Thread(new Destroy(this), "destroy");
            destroy.setDaemon(false);
            destroy.start();
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    /**
     * Return a new array containing the elements of the given array
     * plus the given element added to the end.
     */
    private static Object[] arrayAdd(Object[] array, Object elt) {
	int len = array.length;
	Object[] narray =
	    (Object[])Array.newInstance(array.getClass().getComponentType(),
					len + 1);
	System.arraycopy(array, 0, narray, 0, len);
	narray[len] = elt;
	return narray;
    }

    /**
     * Return a new array containing all the elements of the given array
     * except the one at the specified index.
     */
    private static Object[] arrayDel(Object[] array, int i) {
	int len = array.length - 1;
	Object[] narray =
	    (Object[])Array.newInstance(array.getClass().getComponentType(),
					len);
	System.arraycopy(array, 0, narray, 0, i);
	System.arraycopy(array, i + 1, narray, i, len - i);
	return narray;
    }

    /** Returns the first index of elt in the array, else -1. */
    private static int indexOf(Object[] array, Object elt) {
	return indexOf(array, array.length, elt);
    }

    /** Returns the first index of elt in the array if < len, else -1. */
    private static int indexOf(Object[] array, int len, Object elt) {
	for (int i = 0; i < len; i++) {
	    if (elt.equals(array[i]))
		return i;
	}
	return -1;
    }

    /** Return true if the array is null or zero length */
    private static boolean isEmpty(Object[] array) {
	return (array == null || array.length == 0);
    }

    /** Return true if some object is an element of both arrays */
    private static boolean overlap(Object[] arr1, Object[] arr2) {
	for (int i = arr1.length; --i >= 0; ) {
	    if (indexOf(arr2, arr1[i]) >= 0)
		return true;
	}
	return false;
    }

    /** Test if all elements of the array are null. */
    private static boolean allNull(Object[] array) {
	for (int i = array.length; --i >= 0; ) {
	    if (array[i] != null)
		return false;
	}
	return true;
    }
    
    /** Test if all elements of the list are null. */
    private static boolean allNull(List array) {
	for (int i = array.size(); --i >= 0; ) {
	    if (array.get(i) != null)
		return false;
	}
	return true;
    }

    /** Weed out duplicates. */
    private static Object[] removeDups(Object[] arr) {
	for (int i = arr.length; --i >= 0; ) {
	    if (indexOf(arr, i, arr[i]) >= 0)
		arr = arrayDel(arr, i);
	}
	return arr;
    }

    /** Delete item.attributeSets[i] and return the new array. */
    private static EntryRep[] deleteSet(Item item, int i) {
	item.setAttributeSets((EntryRep[]) arrayDel(item.getAttributeSets(), i));
	return item.getAttributeSets();
    }

    /**
     * Do a deep copy of the item, and substitute replacements for all
     * embedded EntryClass instances and null for the ServiceType and
     * codebase (since they aren't needed on the client side).
     */
    private static Item copyItem(Item item) {
	EntryRep[] attrSets = item.getAttributeSets().clone();
	for (int i = attrSets.length; --i >= 0; ) {
            attrSets[i] = new EntryRep(attrSets[i], true);
	}
	return new Item(item.getServiceID(), null, null, item.service, attrSets, item.bootstrapProxy );
    }

    /**
     * Return the first (highest) class that defines the given field.  This
     * would be a method on EntryClass, but we want to minimize code
     * downloaded into the client.
     */
    private static EntryClass getDefiningClass(EntryClass eclass, int fldidx) {
	while (true) {
	    EntryClass sup = eclass.getSuperclass();
	    if (sup.getNumFields() <= fldidx)
		return eclass;
	    eclass = sup;
	}
    }

    /** Adds a service registration to types in its hierarchy */
    private void addServiceByTypes(ServiceType type, SvcReg reg) {
	Map<ServiceID,SvcReg> map = serviceByTypeName.get(type.getName());
	if (map == null) {
	    map = new HashMap<ServiceID,SvcReg>();
	    serviceByTypeName.put(type.getName(), map);
	}
	map.put(reg.item.getServiceID(), reg);	
	ServiceType[] ifaces = type.getInterfaces();
	for (int i = ifaces.length; --i >= 0; ) {
	    addServiceByTypes(ifaces[i], reg);
	}
	ServiceType sup = type.getSuperclass();
	if (sup != null)
	    addServiceByTypes(sup, reg);
    }

    /** Deletes a service registration from types in its hierarchy */
    private void deleteServiceFromTypes(ServiceType type, SvcReg reg)
    {
	Map<ServiceID,SvcReg> map = serviceByTypeName.get(type.getName());
	if (map != null) {
	    map.remove(reg.item.getServiceID());
	    if ((map.isEmpty()) && !type.equals(objectServiceType))
		serviceByTypeName.remove(type.getName());
	    ServiceType[] ifaces = type.getInterfaces();
	    for (int j = ifaces.length; --j >= 0; ) {
		deleteServiceFromTypes(ifaces[j], reg);
	    }
	    ServiceType sup = type.getSuperclass();
	    if (sup != null)
		deleteServiceFromTypes(sup, reg);
	}
    }

    /**
     * Test if an item matches a template.  This would be a method on
     * Template, but we want to minimize code downloaded into the client.
     */
    private static boolean matchItem(Template tmpl, Item item) {
	return ((tmpl.serviceID == null ||
		 tmpl.serviceID.equals(item.getServiceID())) &&
		matchType(tmpl.serviceTypes, item.serviceType) &&
		matchAttributes(tmpl, item));
    }

    /** Test if a type is equal to or a subtype of every type in an array. */
    private static boolean matchType(ServiceType[] types, ServiceType type) {
	if (types != null) {
	    for (int i = types.length; --i >= 0; ) {
		if (!types[i].isAssignableFrom(type))
		    return false;
	    }
	}
	return true;
    }

    /**
     * Test if an entry matches a template.  This would be a method on
     * EntryRep, but we want to minimize code downloaded into the client.
     */
    private static boolean matchEntry(EntryRep tmpl, EntryRep entry) {
	return entry.matchEntry(tmpl);
    }

    /**
     * Test if there is at least one matching entry in the Item for
     * each entry template in the Template.
     */
    private static boolean matchAttributes(Template tmpl, Item item) {
	EntryRep[] tmpls = tmpl.attributeSetTemplates;
	if (tmpls != null) {
	    EntryRep[] entries = item.getAttributeSets();
	outer:
	    for (int i = tmpls.length; --i >= 0; ) {
		EntryRep etmpl = tmpls[i];
		for (int j = entries.length; --j >= 0; ) {
		    if (matchEntry(etmpl, entries[j]))
			continue outer;
		}
		return false;
	    }
	}
	return true;
    }

    /**
     * Test if an entry either doesn't match any template in an array,
     * or matches a template but is a subclass of the template type.
     */
    private static boolean attrMatch(EntryRep[] tmpls, EntryRep attrSet) {
	boolean good = true;
	if (tmpls != null) {
	    for (int i = tmpls.length; --i >= 0; ) {
		EntryRep tmpl = tmpls[i];
		if (matchEntry(tmpl, attrSet)) {
		    if (tmpl.eclass.isAssignableFrom(attrSet.eclass) &&
			!tmpl.eclass.equals(attrSet.eclass))
			return true;
		    good = false;
		}
	    }
	}
	return good;
    }

    /**
     * Test if the service has an entry of the given class or subclass
     * with a field of the given value.
     */
    private static boolean hasAttr(SvcReg reg,
				   EntryClass eclass,
				   int fldidx,
				   Object value)
    {
	EntryRep[] sets = reg.item.getAttributeSets();
	for (int i = sets.length; --i >= 0; ) {
	    EntryRep set = sets[i];
	    if (eclass.isAssignableFrom(set.eclass) &&
		((value == null && set.flds.get(fldidx) == null) ||
		 (value != null && value.equals(set.flds.get(fldidx)))))
		return true;
	}
	return false;
    }

    /**
     * Test if the service has an entry of the exact given class (assumed
     * to have no fields).
     */
    private static boolean hasEmptyAttr(SvcReg reg, EntryClass eclass) {
	EntryRep[] sets = reg.item.getAttributeSets();
	for (int i = sets.length; --i >= 0; ) {
	    if (eclass.equals(sets[i].eclass))
		return true;
	}
	return false;
    }

    /**
     * Find the most specific types (of type) that don't match prefix and
     * aren't equal to or a supertype of any types in bases, and add them
     * to types.
     */
    private static void addTypes(List types,
				 List codebases,
				 ServiceType[] bases,
				 String prefix,
				 ServiceType type,
				 String codebase)
    {
	if (types.contains(type))
	    return;
	if (bases != null) {
	    for (int i = bases.length; --i >= 0; ) {
		if (type.isAssignableFrom(bases[i]))
		    return;
	    }
	}
	if (prefix == null || type.getName().startsWith(prefix)) {
	    types.add(type);
	    codebases.add(codebase);
	    return;
	}
	ServiceType[] ifs = type.getInterfaces();
	for (int i = ifs.length; --i >= 0; ) {
	    addTypes(types, codebases, bases, prefix, ifs[i], codebase);
	}
	ServiceType sup = type.getSuperclass();
	if (sup != null)
	    addTypes(types, codebases, bases, prefix, sup, codebase);
    }

    /** Limit leaseDuration by limit, and check for negative value. */
    private static long limitDuration(long leaseDuration, long limit) {
	if (leaseDuration == Lease.ANY || leaseDuration > limit)
	    leaseDuration = limit;
	else if (leaseDuration < 0)
	    throw new IllegalArgumentException("negative lease duration");
	return leaseDuration;
    }

    /**
     * Writes reggie's attributes to ObjectOutputStream as a
     * null-terminated list of MarshalledInstances.
     */
    private static void marshalAttributes(Entry[] attrs,
					  ObjectOutputStream out)
	throws IOException
    {
        int len = attrs.length;
	for (int i=0; i < len; i++) {
	    out.writeObject(new MarshalledInstance(attrs[i]));
	}
	out.writeObject(null);
    }

    /**
     * Returns reggie's attributes unmarshalled from a null-terminated list of
     * MarshalledInstances read from the given stream, logging (but tolerating)
     * unmarshalling failures.
     */
    private static Entry[] unmarshalAttributes(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	List<Entry> attributes = new LinkedList<Entry>();
	MarshalledInstance mi = null;
	while ((mi = (MarshalledInstance) in.readObject()) != null) {
	    try {
		attributes.add((Entry) mi.get(false));
	    } catch (Throwable e) {
		if (e instanceof Error &&
		    ThrowableConstants.retryable(e) ==
			ThrowableConstants.BAD_OBJECT)
		{
		    throw (Error) e;
		}
		logger.log(
		    Level.WARNING, "failed to recover LUS attribute", e);
	    }
	}
	Entry[] attrs = new Entry[attributes.size()];
	return attributes.toArray(attrs);

    }


    /**
     * Writes locators to the given stream as a null-terminated list of
     * MarshalledInstances.
     */
    private static void marshalLocators(LookupLocator[] locators,
					ObjectOutputStream out)
	throws IOException
    {
        int len = locators.length;
	for (int i = 0; i < len; i++) {
	    out.writeObject(new MarshalledInstance(locators[i]));
	}
	out.writeObject(null);
    }

    /**
     * Returns locators unmarshalled from a null-terminated list of
     * MarshalledInstances read from the given stream, logging (but tolerating)
     * unmarshalling failures.
     */
    private static LookupLocator[] unmarshalLocators(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	List<LookupLocator> l = new LinkedList<LookupLocator>();
	MarshalledInstance mi;
	while ((mi = (MarshalledInstance) in.readObject()) != null) {
	    try {
		l.add((LookupLocator) mi.get(false));
	    } catch (Throwable e) {
		if (e instanceof Error &&
		    ThrowableConstants.retryable(e) ==
			ThrowableConstants.BAD_OBJECT)
		{
		    throw (Error) e;
		}
		logger.log(
		    Level.WARNING, "failed to recover lookup locator", e);
	    }
	}
	return l.toArray(new LookupLocator[l.size()]);
    }
				
    /**
     * Returns new array containing locators from the given non-null array
     * prepared using the given proxy preparer.  If tolerateFailures is false,
     * then any proxy preparation exception is propagated to the caller.
     * Otherwise, such exceptions are logged, and only successfully prepared
     * locators are included in the returned array.
     */
    private static LookupLocator[] prepareLocators(LookupLocator[] locators,
						   ProxyPreparer preparer,
						   boolean tolerateFailures)
	throws RemoteException
    {
        int len = locators.length;
	List<LookupLocator> l = new ArrayList<LookupLocator>(len);
	for (int i = 0; i < len; i++) {
	    try {
		l.add((LookupLocator) preparer.prepareProxy(locators[i]));
	    } catch (Exception e) {
		if (!tolerateFailures) {
		    if (e instanceof RemoteException) {
			throw (RemoteException) e;
		    } else {
			throw (RuntimeException) e;
		    }
		}
		if (logger.isLoggable(Level.WARNING)) {
		    logThrow(
			Level.WARNING,
			RegistrarImpl.class.getName(),
			"prepareLocators",
			"failed to prepare lookup locator {0}",
			new Object[]{ locators[i] },
			e);
		}
	    }
	}
	return l.toArray(new LookupLocator[l.size()]);
    }

    /**
     * Logs a thrown exception.
     */
    private static void logThrow(Level level,
				 String className,
				 String methodName,
				 String message,
				 Object[] args,
				 Throwable thrown)
    {
	java.util.logging.LogRecord lr =
	    new java.util.logging.LogRecord(level, message);
	lr.setLoggerName(logger.getName());
	lr.setSourceClassName(className);
	lr.setSourceMethodName(methodName);
	lr.setParameters(args);
	lr.setThrown(thrown);
	logger.log(lr);
    }

    /**
     * Add a service to our state.  This includes putting it in the
     * serviceByID map under the serviceID, in the serviceByTime map,
     * in the serviceByType map under the service's most-specific
     * ServiceType, and in the serviceByAttr map under all of its
     * attribute values, incrementing the number of instances of each
     * EntryClass, and updating entryClasses as necessary.  If this is
     * the first instance of that ServiceType registered, then we need
     * to add concrete class information to the type and all supertypes.
     */
    private void addService(SvcReg reg) {
	serviceByID.put(reg.item.getServiceID(), reg);
	serviceByTime.add(reg);
	addServiceByTypes(reg.item.serviceType, reg);
	EntryRep[] entries = reg.item.getAttributeSets();
	for (int i = entries.length; --i >= 0; ) {
	    addAttrs(reg, entries[i]);
	}
	computeMaxLeases();
    }

    /**
     * Delete given service, generating events as necessary.  This includes
     * deleting from the serviceByID, serviceByTime, serviceByType, and
     * serviceByAttr maps, decrementing the number of instances of each
     * EntryClass, and updating entryClasses as necessary.  If this is the
     * last registered instance of the service type, then we delete the
     * concrete class information from the type and all supertypes.
     */
    private void deleteService(SvcReg reg, long now) {
	Item item = reg.item;
	generateEvents(item, null, now);
	serviceByID.remove(item.getServiceID());
	serviceByTime.remove(reg);
	deleteServiceFromTypes(item.serviceType, reg);
	EntryRep[] entries = item.getAttributeSets();
	for (int i = entries.length; --i >= 0; ) {
	    deleteAttrs(reg, entries[i], false);
	}
	computeMaxLeases();
    }

    /**
     * Add an event registration to our state.  This includes adding a
     * template of each EntryClass, putting the registration in the
     * eventByID map, in the eventByTime map, and in either
     * subEventByService (if the template is for a specific service id)
     * or subEventByID.  Since we expect in most cases there will only
     * ever be a single event registration for a given service id, we
     * avoid creating a singleton array in that case.
     */
    private void addEvent(EventReg reg) {
	if (reg.listener == null)
	    return; /* failed to recover from log */
	EntryRep[] tmpls = reg.tmpl.attributeSetTemplates;
	if (tmpls != null) {
	    for (int i = tmpls.length; --i >= 0; ) {
		EntryClass eclass = tmpls[i].eclass;
		eclass.setNumTemplates(eclass.getNumTemplates() + 1);
	    }
	}
	Long id = Long.valueOf(reg.eventID);
	eventByID.put(id, reg);
	eventByTime.offer(reg);
        eventTaskMap.put(reg, eventNotifierExec.newSerialExecutor(new PriorityBlockingQueue()) );
	if (reg.tmpl.serviceID != null) {
	    Object val = subEventByService.get(reg.tmpl.serviceID);
	    if (val == null)
		val = reg;
	    else if (val instanceof EventReg)
		val = new EventReg[]{(EventReg)val, reg};
	    else
		val = arrayAdd((EventReg[])val, reg);
	    subEventByService.put(reg.tmpl.serviceID, val);
	} else {
	    subEventByID.put(id, reg);
	}
	computeMaxLeases();
    }

    /**
     * Remove an event registration from our state.  This includes deleting
     * a template of each EntryClass, deleting the registration from the
     * eventByID map, the eventByTime map, and either the subEventByService
     * or subEventByID map.
     */
    private void deleteEvent(EventReg reg) {
	EntryRep[] tmpls = reg.tmpl.attributeSetTemplates;
	if (tmpls != null) {
	    for (int i = tmpls.length; --i >= 0; ) {
		EntryClass eclass = tmpls[i].eclass;
		eclass.setNumTemplates(eclass.getNumTemplates() - 1);
	    }
	}
	Long id = Long.valueOf(reg.eventID);
	eventByID.remove(id);
	eventByTime.remove(reg);
        eventTaskMap.remove(reg);
	if (reg.tmpl.serviceID != null) {
	    Object val = subEventByService.get(reg.tmpl.serviceID);
	    if (val == reg) {
		subEventByService.remove(reg.tmpl.serviceID);
	    } else {
		Object[] array = (EventReg[])val;
		array = arrayDel(array, indexOf(array, reg));
		if (array.length == 1)
		    val = array[0];
		else
		    val = array;
		subEventByService.put(reg.tmpl.serviceID, val);
	    }
	} else {
	    subEventByID.remove(id);
	}
	computeMaxLeases();
    }

    /**
     * Put the service in the serviceByAttr map under all attribute values
     * in the given entry, or in the serviceByEmptyAttr map if the entry
     * has no attributes, add a new instance of the EntryClass, and update
     * entryClasses as necessary.
     */
    private void addAttrs(SvcReg reg, EntryRep entry) {
	EntryClass eclass = entry.eclass;
	addInstance(eclass);
	List fields = entry.flds;
	if ( fields.size() > 0) {
	    /* walk backwards to make getDefiningClass more efficient */
	    for (int i = fields.size(); --i >= 0; ) {
		eclass = getDefiningClass(eclass, i);
		addAttr(reg, eclass, i, fields.get(i));
	    }
	    return;
	}
	List regs = serviceByEmptyAttr.get(eclass);
	if (regs == null) {
	    regs = new ArrayList(2);
	    regs.add(reg);
	    serviceByEmptyAttr.put(eclass, regs);
	} else if (!regs.contains(reg)) {
	    regs.add(reg);
	}
    }

    /**
     * If checkDups is false, delete the service (if present) from
     * serviceByAttr under all attribute values of the given entry or
     * from serviceByEmptyAttr if the entry has no attributes.
     * If checkDups is true, only delete for a given attribute value if the
     * service has no other entries of similar type that still have the
     * same value.  Either way, delete an instance of the EntryClass,
     * and update entryClasses as necessary.
     */
    private void deleteAttrs(SvcReg reg, EntryRep entry, boolean checkDups) {
	EntryClass eclass = entry.eclass;
	deleteInstance(eclass);
	List fields = entry.flds;
	if ( fields.isEmpty()) {
	    List regs = serviceByEmptyAttr.get(eclass);
	    if (regs == null || (checkDups && hasEmptyAttr(reg, eclass)))
		return;
	    int idx = regs.indexOf(reg);
	    if (idx >= 0) {
		regs.remove(idx);
		if (regs.isEmpty())
		    serviceByEmptyAttr.remove(eclass);
	    }
	    return;
	}
	/* walk backwards to make getDefiningClass more efficient */
	for (int fldidx = fields.size(); --fldidx >= 0; ) {
	    eclass = getDefiningClass(eclass, fldidx);
	    Map<Object,List<SvcReg>>[] attrMaps = serviceByAttr.get(eclass);
	    if (attrMaps == null ||
		attrMaps[fldidx] == null ||
		(checkDups && hasAttr(reg, eclass, fldidx, fields.get(fldidx))))
		continue;
	    Map<Object,List<SvcReg>> map = attrMaps[fldidx];
	    Object value = fields.get(fldidx);
	    List<SvcReg> regs = map.get(value);
	    if (regs == null)
		continue;
	    int idx = regs.indexOf(reg);
	    if (idx < 0)
		continue;
	    regs.remove(idx);
	    if (!regs.isEmpty())
		continue;
	    map.remove(value);
	    if (!map.isEmpty())
		continue;
	    attrMaps[fldidx] = null;
	    if (allNull(attrMaps))
		serviceByAttr.remove(eclass);
	}
    }

    /**
     * Store all non-null elements of values into the given entry,
     * and update serviceByAttr to match.
     */
    private void updateAttrs(SvcReg reg, EntryRep entry, List values)
    {
	EntryClass eclass = entry.eclass;
	/* walk backwards to make getDefiningClass more efficient */
	for (int fldidx = values.size(); --fldidx >= 0; ) {
	    Object oval = entry.flds.get(fldidx);
	    Object nval = values.get(fldidx);
	    if (nval != null && !nval.equals(oval)) {
		eclass = getDefiningClass(eclass, fldidx);
		Map<Object,List<SvcReg>> map = addAttr(reg, eclass, fldidx, nval);
		entry.flds.set(fldidx, nval);
		if (hasAttr(reg, eclass, fldidx, oval))
		    continue;
		List regs = map.get(oval);
		regs.remove(regs.indexOf(reg));
		if (regs.isEmpty())
		    map.remove(oval); /* map cannot become empty */
	    }
	}
    }

    /**
     * Put the service under the given attribute value for the given
     * defining class and field, if it isn't already there.  Return
     * the HashMap for the given class and field.
     */
    private Map<Object,List<SvcReg>> addAttr(SvcReg reg,
			    EntryClass eclass,
			    int fldidx,
			    Object value)
    {
	Map<Object,List<SvcReg>>[] attrMaps = serviceByAttr.get(eclass);
	if (attrMaps == null) {
	    attrMaps = new Map[eclass.getNumFields()];
	    serviceByAttr.put(eclass, attrMaps);
	}
	Map<Object,List<SvcReg>> map = attrMaps[fldidx];
	if (map == null) {
	    map = new HashMap(11);
	    attrMaps[fldidx] = map;
	}
	List<SvcReg> regs = map.get(value);  //REMIND: Null field value ok?
	if (regs == null) {
	    regs = new ArrayList(3);
	    map.put(value, regs);
	} else if (regs.contains(reg))
	    return map;
	regs.add(reg);
	return map;
    }

    /**
     * Add an instance of the EntryClass, and add the class to entryClasses
     * if this is the first such instance.
     */
     private void addInstance(EntryClass eclass) {
	int idx = entryClasses.indexOf(eclass);
	if (idx < 0) {
	    entryClasses.add(eclass);
	    idx = entryClasses.size() - 1;
	}
	eclass = (EntryClass) entryClasses.get(idx);
	eclass.setNumInstances(eclass.getNumInstances() + 1);	
    }

    /**
     * Delete an instance of the EntryClass, and remove the class from
     * entryClasses if this is the last such instance.
     */
    private void deleteInstance(EntryClass eclass) {
	int idx = entryClasses.indexOf(eclass);
	eclass = (EntryClass) entryClasses.get(idx);
	int num = eclass.getNumInstances() - 1;
	if (num == 0)
	    entryClasses.remove(idx);
	eclass.setNumInstances(num);
    }

    /** Return an appropriate iterator for Items matching the Template. */
    private ItemIter matchingItems(Template tmpl) {
	if (tmpl.serviceID != null)
	    return new IDItemIter(tmpl, serviceByID.get(tmpl.serviceID));
	if (!isEmpty(tmpl.serviceTypes)){
            Map<ServiceID,SvcReg> map = serviceByTypeName.get(
	       tmpl.serviceTypes[0].getName());
	    Iterator<SvcReg> services = map != null ? map.values().iterator() :
		Collections.EMPTY_LIST.iterator();
	    return new SvcIterator(tmpl, services);
        }
	EntryRep[] sets = tmpl.attributeSetTemplates;
	if (isEmpty(sets))
	    return new AllItemIter(serviceByID.values().iterator());
	for (int i = sets.length; --i >= 0; ) {
	    List fields = sets[i].flds;
	    if (fields.isEmpty()) {
		EntryClass eclass = getEmptyEntryClass(sets[i].eclass);
		if (eclass != null)
		    return new AttrItemIter(tmpl, serviceByEmptyAttr.get(eclass));
	    } else {
		/* try subclass fields before superclass fields */
		for (int j = fields.size(); --j >= 0; ) {
		    if (fields.get(j) != null){
                        EntryRep set = tmpl.attributeSetTemplates[i];
                        Map<Object,List<SvcReg>>[] attrMaps =
                            serviceByAttr.get(getDefiningClass(set.eclass,j));
                        List<SvcReg> svcs = null;
                        if (attrMaps != null && attrMaps[j] != null) {
                            svcs = attrMaps[j].get(set.flds.get(j));
                        }                   
			return new AttrItemIter(tmpl, svcs);
                    }
		}
	    }
	}
	return new ClassItemIter(tmpl);
    }

    /**
     * Return member of entryClasses that is equal to or a subclass of
     * the specified class, provided there is exactly one such member
     * and it has no fields.
     */
    private EntryClass getEmptyEntryClass(EntryClass eclass) {
	EntryClass match = null;
	for (int i = entryClasses.size(); --i >= 0; ) {
	    EntryClass cand = (EntryClass)entryClasses.get(i);
	    if (eclass.isAssignableFrom(cand)) {
		if (cand.getNumFields() != 0 || match != null)
		    return null;
		match = cand;
	    }
	}
	return match;
    }

    /** Returns a list of services that match all types passed in */
    private List<SvcReg> matchingServices(ServiceType[] types) {
	List<SvcReg> matches = new LinkedList<SvcReg>();
	if (isEmpty(types)) {
	    Map<ServiceID,SvcReg> map = serviceByTypeName.get(objectServiceType.getName());
	    matches.addAll(map.values());
	} else {
	    Map map = (Map) serviceByTypeName.get(types[0].getName());
	    if (map != null)
	        matches.addAll(map.values());
	    if (types.length > 1) {
	        for (Iterator<SvcReg> it = matches.iterator(); it.hasNext(); ) {
		    SvcReg reg = it.next();
		    if (!matchType(types, reg.item.serviceType))
		        it.remove();
	        }
	    }
	}
	return matches;
    }

    /** Return any valid codebase for an entry class that has instances. */
    private String pickCodebase(EntryClass eclass, long now)
	throws ClassNotFoundException
    {
	if (eclass.getNumFields() == 0)
	    return pickCodebase(eclass,
				serviceByEmptyAttr.get(eclass),
				now);
	int fldidx = eclass.getNumFields() - 1;
	Map<Object,List<SvcReg>>[] attrMaps =
	    serviceByAttr.get(getDefiningClass(eclass, fldidx));
	for (Iterator<List<SvcReg>> iter = attrMaps[fldidx].values().iterator();
	     iter.hasNext(); )
	{
	    try {
		return pickCodebase(eclass, iter.next(), now);
	    } catch (ClassNotFoundException e) {
	    }
	}
	throw new ClassNotFoundException();
    }

    /** Return any valid codebase for an entry of the exact given class. */
    private String pickCodebase(EntryClass eclass, List<SvcReg> svcs, long now)
	throws ClassNotFoundException
    {
	for (int i = svcs.size(); --i >= 0; ) {
	    SvcReg reg = svcs.get(i);
	    if (reg.leaseExpiration <= now)
		continue;
	    EntryRep[] sets = reg.item.getAttributeSets();
	    for (int j = sets.length; --j >= 0; ) {
		if (eclass.equals(sets[j].eclass))
		    return sets[j].codebase;
	    }
	}
	throw new ClassNotFoundException();
    }

    /**
     * Compute new maxServiceLease and maxEventLease values.  This needs to
     * be called whenever the number of services (#S) or number of events
     * (#E) changes, or whenever any of the configuration parameters change.
     * The two base equations driving the computation are:
     *     #S/maxServiceLease + #E/maxEventLease <= 1/minRenewalInterval
     *     maxServiceLease/maxEventLease = minMaxServiceLease/minMaxEventLease
     */
    private void computeMaxLeases() {
	if (inRecovery)
	    return;
	maxServiceLease =
	    Math.max(minMaxServiceLease,
		     minRenewalInterval *
		     (serviceByID.size() +
		      ((eventByID.size() * minMaxServiceLease) /
		       minMaxEventLease)));
	maxEventLease = Math.max(minMaxEventLease,
				 ((maxServiceLease * minMaxEventLease) /
				  minMaxServiceLease));
    }

    private void respondHttps(Socket socket) throws Exception {
	try {
	    try {
		socket.setTcpNoDelay(true);
		socket.setKeepAlive(true);		
	    } catch (SocketException e) {
		if (logger.isLoggable(Levels.HANDLED))
		    logger.log(Levels.HANDLED,
			       "problem setting socket options", e);
	    }
	    socket.setSoTimeout(
	       unicastDiscoveryConstraints.getUnicastSocketTimeout(
	           DEFAULT_SOCKET_TIMEOUT));
	    
	    httpsDiscovery.handleUnicastDiscovery(
		new UnicastResponse(myLocator.getHost(),
				    myLocator.getPort(),
				    memberGroups,
				    proxy),
		socket,
		unicastDiscoveryConstraints.getUnfulfilledConstraints(),
		unicastDiscoverySubjectChecker,
		Collections.EMPTY_LIST);	
	} finally {
	    try {
		socket.close();
	    } catch (IOException e) {
		logger.log(Levels.HANDLED, "exception closing socket", e);
	    }
	}
    }
    
    /** Process a unicast discovery request, and respond. */
    private void respond(Socket socket) throws Exception {
	try {
	    try {
		socket.setTcpNoDelay(true);
		socket.setKeepAlive(true);		
	    } catch (SocketException e) {
		if (logger.isLoggable(Levels.HANDLED))
		    logger.log(Levels.HANDLED,
			       "problem setting socket options", e);
	    }
	    socket.setSoTimeout(
	       unicastDiscoveryConstraints.getUnicastSocketTimeout(
	           DEFAULT_SOCKET_TIMEOUT));
	    int pv = new DataInputStream(socket.getInputStream()).readInt();
	    unicastDiscoveryConstraints.checkProtocolVersion(pv);
	    getDiscovery(pv).handleUnicastDiscovery(
		new UnicastResponse(myLocator.getHost(),
				    myLocator.getPort(),
				    memberGroups,
				    proxy),
		socket,
		unicastDiscoveryConstraints.getUnfulfilledConstraints(),
		unicastDiscoverySubjectChecker,
		Collections.EMPTY_LIST);	
	} finally {
	    try {
		socket.close();
	    } catch (IOException e) {
		logger.log(Levels.HANDLED, "exception closing socket", e);
	    }
	}
    }

    /** Returns Discovery instance implementing the given protocol version */
    private Discovery getDiscovery(int version)
	throws DiscoveryProtocolException
    {
	switch (version) {
	    case Discovery.PROTOCOL_VERSION_1:
		return Discovery.getProtocol1();
	    case Discovery.PROTOCOL_VERSION_2:
		return protocol2;
	    default:
		throw new DiscoveryProtocolException(
		    "unsupported protocol version: " + version);
	}
    }

    /** Close any sockets that were sitting in the task queue. */
    private void closeRequestSockets(List<Runnable> tasks) {
	for (int i = tasks.size(); --i >= 0; ) {
	    Runnable obj = tasks.get(i);
	    if (obj instanceof SocketTask) {
		try {
		    ((SocketTask)obj).socket.close();
		} catch (IOException e) {
		}
	    }
	}
    }
    
    /**
     * Reggie Initializer
     */
    private static final class Initializer{
         LifeCycle lifeCycle;
         ServerSocketFactory serverSocketFactory;
         int persistenceSnapshotThreshold;
         SocketFactory socketFactory;
         ProxyPreparer recoveredListenerPreparer;
         float persistenceSnapshotWeight;
         ProxyPreparer recoveredLocatorPreparer;
         boolean inRecovery;
         ActivationID activationID;
         ActivationSystem activationSystem;
         Exporter serverExporter;
         String[] lookupGroups = DiscoveryGroupManagement.NO_GROUPS;
         LookupLocator[] lookupLocators = {};
         String[] memberGroups = {""};
         int unicastPort = 0;
         Entry[] lookupAttrs;
         DiscoveryManagement discoer;
         ProxyPreparer listenerPreparer;
         ProxyPreparer locatorPreparer;
         long minMaxEventLease;
         long minMaxServiceLease;
         long minRenewalInterval;
         long multicastAnnouncementInterval;
         int multicastInterfaceRetryInterval;
         NetworkInterface[] multicastInterfaces;
         boolean multicastInterfacesSpecified;
         UuidGenerator resourceIdGenerator;
         UuidGenerator serviceIdGenerator;
         long unexportTimeout;
         long unexportWait;
         ServiceType objectServiceType;
         ClientSubjectChecker unicastDiscoverySubjectChecker;
         Discovery protocol2;
         InvocationConstraints rawUnicastDiscoveryConstraints;
         DiscoveryConstraints multicastRequestConstraints;
         DiscoveryConstraints multicastAnnouncementConstraints;
         DiscoveryConstraints unicastDiscoveryConstraints;
         ClientSubjectChecker multicastRequestSubjectChecker;
         LoginContext loginContext;
         String persistenceDirectory = null;
         boolean persistent;
         String unicastDiscoveryHost;
         Configuration config;
         AccessControlContext context;
         ExecutorService executor;
         ScheduledExecutorService scheduledExecutor;
	 String certFactoryType;
	 String certPathEncoding;
	 byte [] encodedCerts;
	 private int httpsUnicastPort;
	 boolean enableHttpsUnicast;
	 Discovery httpsDiscovery;
        
        
        
        Initializer ( Configuration config, ActivationID activationID, 
                boolean persistent, LifeCycle lifeCycle, LoginContext loginContext)
	throws IOException, ConfigurationException, ActivationException {
            if (activationID != null && !persistent) {
                throw new IllegalArgumentException();
            }
            this.lifeCycle = lifeCycle;
            this.loginContext = loginContext;
            this.persistent = persistent;
            this.config = config;
            context = AccessController.getContext();
            ProxyPreparer p = new BasicProxyPreparer();

            this.serverSocketFactory = (ServerSocketFactory) config.getEntry(
                    COMPONENT, "serverSocketFactory", ServerSocketFactory.class,
                    ServerSocketFactory.getDefault(), Configuration.NO_DATA);
            this.socketFactory = (SocketFactory) config.getEntry(
                    COMPONENT, "socketFactory", SocketFactory.class,
                    SocketFactory.getDefault(), Configuration.NO_DATA);

            /* persistence-specific initialization */
            if (persistent) {
                this.persistenceSnapshotThreshold = Config.getIntEntry(
                    config, COMPONENT, "persistenceSnapshotThreshold",
                    200, 0, Integer.MAX_VALUE);
                this.persistenceDirectory = (String) config.getEntry(
                    COMPONENT, "persistenceDirectory", String.class);
                this.recoveredListenerPreparer = (ProxyPreparer) Config.getNonNullEntry(
                    config, COMPONENT, "recoveredListenerPreparer",
                    ProxyPreparer.class, p);
                this.recoveredLocatorPreparer = (ProxyPreparer) Config.getNonNullEntry(
                    config, COMPONENT, "recoveredLocatorPreparer",
                    ProxyPreparer.class, p);
                this.persistenceSnapshotWeight = Config.getFloatEntry(
                    config, COMPONENT, "persistenceSnapshotWeight",
                    10, 0F, Float.MAX_VALUE);
            }

            /* activation-specific initialization */
            if (activationID != null) {
                ProxyPreparer activationIdPreparer = (ProxyPreparer)
                    Config.getNonNullEntry(
                        config, COMPONENT, "activationIdPreparer",
                        ProxyPreparer.class, new BasicProxyPreparer());
                ProxyPreparer activationSystemPreparer = (ProxyPreparer)
                    Config.getNonNullEntry(
                        config, COMPONENT, "activationSystemPreparer",
                        ProxyPreparer.class, new BasicProxyPreparer());

                this.activationID = (ActivationID)
                    activationIdPreparer.prepareProxy(activationID);
                this.activationSystem = (ActivationSystem)
                    activationSystemPreparer.prepareProxy(
                        ActivationGroup.getSystem());

                this.serverExporter = (Exporter) Config.getNonNullEntry(
                    config, COMPONENT, "serverExporter", Exporter.class,
                    new ActivationExporter(
                        this.activationID,
                        new BasicJeriExporter(
                            TcpServerEndpoint.getInstance(0),
                            new BasicILFactory())),
                    this.activationID);
            } else {
                this.activationID = null;
                activationSystem = null;

                serverExporter = (Exporter) Config.getNonNullEntry(
                    config, COMPONENT, "serverExporter", Exporter.class,
                    new BasicJeriExporter(
                        TcpServerEndpoint.getInstance(0),
                        new BasicILFactory()));
            }

            /* fetch "initial*" config entries, first time starting up */
            Entry[] initialLookupAttributes = (Entry[]) config.getEntry(
                COMPONENT, "initialLookupAttributes", Entry[].class,
                new Entry[0]);
            this.lookupGroups = (String[]) config.getEntry(
                COMPONENT, "initialLookupGroups", String[].class,
                DiscoveryGroupManagement.NO_GROUPS);
            this.lookupLocators = (LookupLocator[]) config.getEntry(
                COMPONENT, "initialLookupLocators", LookupLocator[].class,
                lookupLocators);
            this.memberGroups = (String[]) config.getEntry(
                COMPONENT, "initialMemberGroups", String[].class,
                memberGroups);
            if (memberGroups == null) {
                throw new ConfigurationException(
                    "member groups cannot be ALL_GROUPS (null)");
            }
            memberGroups = (String[]) removeDups(memberGroups);
            this.unicastPort = Config.getIntEntry(
                config, COMPONENT, "initialUnicastDiscoveryPort",
                0, 0, 0xFFFF);
	    
	    this.httpsUnicastPort = Config.getIntEntry(
		    config, COMPONENT, "httpsUnicastDiscoveryPort",
		    443, 0, 0xFFFF);
	    this.enableHttpsUnicast = config.getEntry(COMPONENT, 
		    "enableHttpsUnicast" ,Boolean.class , Boolean.FALSE);	    
            if (initialLookupAttributes != null && 
                initialLookupAttributes.length > 0)
            {
                List l = new ArrayList(Arrays.asList(baseAttrs));
                l.addAll(Arrays.asList(initialLookupAttributes));
                this.lookupAttrs = (Entry[]) l.toArray(new Entry[l.size()]);
            } else {
                lookupAttrs = baseAttrs;
            }

            /* fetch remaining config entries */
            MethodConstraints discoveryConstraints = 
                (MethodConstraints) config.getEntry(COMPONENT, 
                                                    "discoveryConstraints",
                                                    MethodConstraints.class, null);
            if (discoveryConstraints == null) {
                discoveryConstraints = 
                    new BasicMethodConstraints(InvocationConstraints.EMPTY);
            }
            try {
                this.discoer = (DiscoveryManagement) config.getEntry(
                    COMPONENT, "discoveryManager", DiscoveryManagement.class);
            } catch (NoSuchEntryException e) {
                discoer = new LookupDiscoveryManager(
                    DiscoveryGroupManagement.NO_GROUPS, null, null, config);
            }
            this.listenerPreparer = (ProxyPreparer) Config.getNonNullEntry(
                config, COMPONENT, "listenerPreparer", ProxyPreparer.class, p);
            this.locatorPreparer = (ProxyPreparer) Config.getNonNullEntry(
                config, COMPONENT, "locatorPreparer", ProxyPreparer.class, p);
            this.minMaxEventLease = Config.getLongEntry(
                config, COMPONENT, "minMaxEventLease",
                1000 * 60 * 30 , 1, MAX_LEASE);
            this.minMaxServiceLease = Config.getLongEntry(
                config, COMPONENT, "minMaxServiceLease",
                1000 * 60 * 5 , 1, MAX_LEASE);
            this.minRenewalInterval = Config.getLongEntry(
                config, COMPONENT, "minRenewalInterval",
                100, 0, MAX_RENEW);
            this.multicastAnnouncementInterval = Config.getLongEntry(
                config, COMPONENT, "multicastAnnouncementInterval",
                1000 * 60 * 2 , 1, Long.MAX_VALUE);

            this.multicastInterfaceRetryInterval = Config.getIntEntry(
                config, COMPONENT, "multicastInterfaceRetryInterval",
                1000 * 60 * 5 , 1, Integer.MAX_VALUE);
            try {
                this.multicastInterfaces = (NetworkInterface[]) config.getEntry(
                    COMPONENT, "multicastInterfaces", NetworkInterface[].class);
                this.multicastInterfacesSpecified = true;
            } catch (NoSuchEntryException e) {
                Enumeration en = NetworkInterface.getNetworkInterfaces();
                List l = (en != null) ?
                    Collections.list(en) : Collections.EMPTY_LIST;
                multicastInterfaces = (NetworkInterface[])
                    l.toArray(new NetworkInterface[l.size()]);
                multicastInterfacesSpecified = false;
            }
            if (multicastInterfaces == null) {
                logger.config("using system default interface for multicast");
            } else if (multicastInterfaces.length == 0) {
                if (multicastInterfacesSpecified) {
                    logger.config("multicast disabled");
                } else {
                    logger.severe("no network interfaces detected");
                }
            } else if (logger.isLoggable(Level.CONFIG)) {
                logger.log(Level.CONFIG, "multicasting on interfaces {0}",
                           new Object[]{ Arrays.asList(multicastInterfaces) });
            }

            try {
                this.multicastRequestSubjectChecker =
                    (ClientSubjectChecker) Config.getNonNullEntry(
                        config, COMPONENT, "multicastRequestSubjectChecker",
                        ClientSubjectChecker.class);
            } catch (NoSuchEntryException e) {
                // leave null
            }
            UuidGenerator u = new UuidGenerator();
            this.resourceIdGenerator = (UuidGenerator) Config.getNonNullEntry(
                config, COMPONENT, "resourceIdGenerator", UuidGenerator.class,
                u);
            this.serviceIdGenerator = (UuidGenerator) Config.getNonNullEntry(
                config, COMPONENT, "serviceIdGenerator", UuidGenerator.class,
                u);
            // Set up Executor to perform remote event notifications
            double blocking_coefficient = 0.7; // 0 CPU intensive to 0.9 IO intensive
            int numberOfCores = Runtime.getRuntime().availableProcessors();
            int poolSizeLimit = (int) (numberOfCores / ( 1 - blocking_coefficient));
            this.scheduledExecutor = Config.getNonNullEntry(
                config, 
                COMPONENT, 
                "eventNotifierExecutor",
                ScheduledExecutorService.class, 
                new ScheduledThreadPoolExecutor(
                    poolSizeLimit,
                    new NamedThreadFactory("Reggie_Event_Notifier", false)   
                )
            );
            // Set up Executor to perform discovery responses
            this.executor = Config.getNonNullEntry(
                config, 
                COMPONENT, 
                "discoveryResponseExecutor", 
                ExecutorService.class, 
                new ThreadPoolExecutor(
                    poolSizeLimit, 
                    poolSizeLimit, /* Ignored */
                    15L, 
                    TimeUnit.MINUTES, 
                    new LinkedBlockingQueue(), /* Unbounded Queue */
                    new NamedThreadFactory("Reggie_Discovery_Response", false)
                ) 
            );
	    this.certFactoryType = Config.getNonNullEntry(config, COMPONENT,
		    "Codebase_CertFactoryType", String.class, "X.509");
	    this.certPathEncoding = Config.getNonNullEntry(config, COMPONENT,
		    "Codebase_CertPathEncoding", String.class, "PkiPath");
	    this.encodedCerts = Config.getNonNullEntry(config, COMPONENT,
		    "Codebase_Certs", byte[].class, new byte[0]);
            this.unexportTimeout = Config.getLongEntry(
                   config, COMPONENT, "unexportTimeout", 20000L,
                   0, Long.MAX_VALUE);
            this.unexportWait = Config.getLongEntry(
                   config, COMPONENT, "unexportWait", 10000L,
                   0, Long.MAX_VALUE);
            try {
                unicastDiscoveryHost = (String) Config.getNonNullEntry(
                    config, COMPONENT, "unicastDiscoveryHost", String.class);
            } catch (NoSuchEntryException e) {
                // fix for 4906732: only invoke getCanonicalHostName if needed
                unicastDiscoveryHost =
                    LocalHostLookup.getLocalHost().getCanonicalHostName();
            }
            try {
                this.unicastDiscoverySubjectChecker =
                    (ClientSubjectChecker) Config.getNonNullEntry(
                        config, COMPONENT, "unicastDiscoverySubjectChecker",
                        ClientSubjectChecker.class);
            } catch (NoSuchEntryException e) {
                // leave null
            }

            /* initialize state based on recovered/configured values */
            this.objectServiceType = new ServiceType(Object.class, null, null);
            this.protocol2 = Discovery.getProtocol2(null);
            /* cache unprocessed unicastDiscovery constraints to handle
               reprocessing of time constraints associated with that method */
            this.rawUnicastDiscoveryConstraints = discoveryConstraints.getConstraints(
                   DiscoveryConstraints.unicastDiscoveryMethod);    
            this.multicastRequestConstraints = DiscoveryConstraints.process(
                discoveryConstraints.getConstraints(
                    DiscoveryConstraints.multicastRequestMethod));
           this.multicastAnnouncementConstraints = DiscoveryConstraints.process(
                discoveryConstraints.getConstraints(
                    DiscoveryConstraints.multicastAnnouncementMethod));
            this.unicastDiscoveryConstraints = DiscoveryConstraints.process(
                this.rawUnicastDiscoveryConstraints);
        }
    }
    
    public void start() throws Exception {
        if (constructionException != null) throw constructionException;
        concurrentObj.writeLock();
        try {
            if (log != null) {
                inRecovery = true;
                log.recover();
                inRecovery = false;
            }
	    if (enableHttpsUnicast){
		httpsDiscovery = new DiscoveryUnicastHTTPS();
	    }
            // log snapshot recovers myServiceID
            if (myServiceID == null) {
                myServiceID = newServiceID();
            }
            computeMaxLeases();
            // Make sure we're exporting with correct login context.
            AccessController.doPrivileged(new PrivilegedExceptionAction<Object>(){

                public Object run() throws Exception {
                    myRef = (Registrar) serverExporter.export(RegistrarImpl.this);
                    proxy = RegistrarProxy.getInstance(myRef, myServiceID);
		    String uri;
		    if (enableHttpsUnicast){
			uri = "https://" + unicastDiscoveryHost + ":"+ httpsUnicastPort; 
			myLocator = (proxy instanceof RemoteMethodControl) ?
			    new ConstrainableLookupLocator(uri, null) :
			    new LookupLocator(uri);
		    } else {
			myLocator = (proxy instanceof RemoteMethodControl) ?
			    new ConstrainableLookupLocator(
				unicastDiscoveryHost, unicast.port, null) :
			    new LookupLocator(unicastDiscoveryHost, unicast.port);
		    }
                    /* register myself */
                    Item item = new Item(new ServiceItem(myServiceID,
                                                         proxy,
                                                         lookupAttrs));
                    SvcReg reg = new SvcReg(item, myLeaseID, Long.MAX_VALUE);
                    addService(reg);
                    if (log != null) {
                        log.snapshot();
                    }

                    try {
                        DiscoveryGroupManagement dgm = (DiscoveryGroupManagement) discoer;
                        String[] groups = dgm.getGroups();
                        if (groups == null || groups.length > 0) {
                            throw new ConfigurationException(
                                "discoveryManager must be initially configured with " +
                                "no groups");
                        }
                        DiscoveryLocatorManagement dlm =
                            (DiscoveryLocatorManagement) discoer;
                        if (dlm.getLocators().length > 0) {
                            throw new ConfigurationException(
                                "discoveryManager must be initially configured with " +
                                "no locators");
                        }
                        dgm.setGroups(lookupGroups);
                        dlm.setLocators(lookupLocators);
                    } catch (ClassCastException e) {
                        throw new ConfigurationException(null, e);
                    }
                    joiner = new JoinManager(proxy, lookupAttrs, myServiceID,
                                             discoer, null, config);

                    /* start up all the daemon threads */
                    serviceExpirer.start();
                    eventExpirer.start();
                    unicaster.start();
                    multicaster.start();
                    announcer.start();
                    eventNotifierExec.start();

                    /* Shutdown hook so reggie sends a final announcement
                     * packet if VM is terminated.  If reggie is terminated
                     * through DestroyAdmin.destroy() this hook will have no effect.
                     * A timeout on announcer.join() was considered but not deemed
                     * necessary at this point in time.  
                     */
                    Runtime.getRuntime().addShutdownHook(new Thread( new Runnable() {
                        public void run() {
                            try {
                                announcer.interrupt();
                                announcer.join();
                            } catch (Throwable t) {
                                logThrow(Level.FINEST, getClass().getName(), 
                                    "run", "exception shutting announcer down",
                                    new Object[]{}, t);
                            }
                        }
                    }));

                    snapshotter.start();
                    if (logger.isLoggable(Level.INFO)) {
                        logger.log(Level.INFO, "started Reggie: {0}, {1}, {2}",
                                   new Object[]{ myServiceID,
                                                 Arrays.asList(memberGroups),
                                                 myLocator });
                    }
                    return null;
                }
                
                    }, context);
        } catch (PrivilegedActionException ex) {
            throw ex.getException();
        } finally {
            // These object no longer needed, set free.
            config = null;
            unicastDiscoveryHost = null;
            context = null;
            concurrentObj.writeUnlock();
        }
    
    }

    /** The code that does the real work of register. */
    private ServiceRegistration registerDo(Item nitem, long leaseDuration)
    {
	if (nitem.service == null)
	    throw new NullPointerException("null service");
	if (myServiceID.equals(nitem.getServiceID()))
	    throw new IllegalArgumentException("reserved service id");
	if (nitem.getAttributeSets() == null)
	    nitem.setAttributeSets(emptyAttrs);
	else
	    nitem.setAttributeSets((EntryRep[]) removeDups(nitem.getAttributeSets()));
	leaseDuration = limitDuration(leaseDuration, maxServiceLease);
	long now = System.currentTimeMillis();
	if (nitem.getServiceID() == null) {
	    /* new service, match on service object */
	    Map<ServiceID,SvcReg> svcs = serviceByTypeName.get(nitem.serviceType.getName());
	    if (svcs != null) {
		for (Iterator<SvcReg> it = svcs.values().iterator(); it.hasNext(); ) {
		    SvcReg reg = it.next();
		    if (nitem.service.equals(reg.item.service)) {
			nitem.setServiceID(reg.item.getServiceID());
			deleteService(reg, now);
			break;
		    }
		}
	    }
	    if (nitem.getServiceID() == null) // TODO: atomicity
		nitem.setServiceID(newServiceID());
	} else {
	    /* existing service, match on service id */
	    SvcReg reg = serviceByID.get(nitem.getServiceID());
	    if (reg != null)
		deleteService(reg, now);
	}
	Util.checkRegistrantServiceID(nitem.getServiceID(), logger, Level.FINE);
	SvcReg reg = new SvcReg(nitem, newLeaseID(), now + leaseDuration);
	addService(reg);
	generateEvents(null, nitem, now);
	addLogRecord(new SvcRegisteredLogObj(reg));
	/* see if the expire thread needs to wake up earlier */
	if (reg.leaseExpiration < minSvcExpiration) {
	    minSvcExpiration = reg.leaseExpiration;
            serviceNotifier.signal();
	}
	return Registration.getInstance(
	    myRef,
	    ServiceLease.getInstance(
		myRef,
		myServiceID, 
		nitem.getServiceID(),
		reg.leaseID,
		reg.leaseExpiration));
    }

   /**
    * The code that does the real work of lookup.  As a special case,
    * if the template specifies at least one service type to match,
    * and there are multiple items that match the template, then we
    * make a random pick among them, in an attempt to load balance
    * use of "equivalent" services and avoid starving any of them.
    */
    private MarshalledWrapper lookupDo(Template tmpl)
    {
	if (isEmpty(tmpl.serviceTypes) || tmpl.serviceID != null)
	{
	    ItemIter iter = matchingItems(tmpl);
	    if (iter.hasNext())
		return iter.next().service;
	    return null;

	}
	List<SvcReg> services = matchingServices(tmpl.serviceTypes);
	long now = System.currentTimeMillis();
	int slen = services.size();
	if (slen == 0)
	    return null;
	int srand = random.nextInt(Integer.MAX_VALUE) % slen;
	for (int i = 0; i < slen; i++) {
	    SvcReg reg = services.get((i + srand) % slen);
	    if (reg.leaseExpiration > now && matchAttributes(tmpl, reg.item))
		    return reg.item.service;
	}
	return null;
    }

    /**
     * The code that does the real work of lookup.  We do a deep copy of the
     * items being returned, both to avoid having them modified while being
     * marshalled (by a concurrent update method), and to substitute
     * replacements for embedded EntryClass and ServiceType instances, to
     * minimize data sent back to the client.  If duplicates are possible
     * from the iterator, we save all matches, weeding out duplicates as we
     * go, then trim to maxMatches and deep copy.
     */
    private Matches lookupDo(Template tmpl, int maxMatches)
    {
	if (maxMatches < 0)
	    throw new IllegalArgumentException("negative maxMatches");
	int totalMatches = 0;
	List matches = null;
	ItemIter iter = matchingItems(tmpl);
	if (maxMatches > 0 || iter.dupsPossible)
	    matches = new LinkedList();
	if (iter.dupsPossible) {
	    while (iter.hasNext()) {
		Item item = iter.next();
		if (!matches.contains(item))
		    matches.add(item);
	    }
	    totalMatches = matches.size();
	    if (maxMatches > 0) {
		for (int i = matches.size(); --i >= maxMatches; )
		    matches.remove(i);
		for (int i = matches.size(); --i >= 0; ) {
		    matches.set(i, copyItem((Item)matches.get(i)));
		}
	    } else {
		matches = null;
	    }
	} else {
	    while (iter.hasNext()) {
		Item item = iter.next();
		totalMatches++;
		if (--maxMatches >= 0)
		    matches.add(copyItem(item));
	    }
	}
	return new Matches(matches, totalMatches);
    }

    /**
     * The code that does the real work of notify.
     * Every registration is given a unique event id.  The event id
     * can thus also serve as a lease id.
     *
     */
    private EventRegistration notifyDo(Template tmpl,
				       int transitions,
				       RemoteEventListener listener,
				       Object handback,
				       long leaseDuration,
				       boolean newNotify)
	throws RemoteException
    {
	if (transitions == 0 ||
	    transitions !=
	    (transitions & (ServiceRegistrar.TRANSITION_MATCH_NOMATCH |
			    ServiceRegistrar.TRANSITION_NOMATCH_MATCH |
			    ServiceRegistrar.TRANSITION_MATCH_MATCH)))
	    throw new IllegalArgumentException("invalid transitions");
	if (listener == null)
	    throw new NullPointerException("listener");
	listener =
	    (RemoteEventListener) listenerPreparer.prepareProxy(listener);
	leaseDuration = limitDuration(leaseDuration, maxEventLease);
	long now = System.currentTimeMillis();
	EventReg reg = new EventReg(eventID, newLeaseID(), tmpl, transitions,
			    listener, handback, now + leaseDuration, newNotify);
	eventID++;
	addEvent(reg);
	addLogRecord(new EventRegisteredLogObj(reg));
	/* see if the expire thread needs to wake up earlier */
	if (reg.getLeaseExpiration() < minEventExpiration) {
	    minEventExpiration = reg.getLeaseExpiration();
            eventNotifier.signal();
	}
	return new EventRegistration(
	    reg.eventID,
	    proxy,
	    EventLease.getInstance(
		myRef,
		myServiceID,
		reg.eventID,
		reg.leaseID, reg.getLeaseExpiration()),
	    reg.getSeqNo());
    }

    /**
     * The code that does the real work of getEntryClasses. If the
     * template is empty, then we can just use entryClasses, without
     * having to iterate over items, but we have to work harder to
     * get codebases.
     */
    private EntryClassBase[] getEntryClassesDo(Template tmpl)
    {
	List<EntryClass> classes = new LinkedList<EntryClass>();
	List<String> codebases = new LinkedList<String>();
	if (tmpl.serviceID == null &&
	    isEmpty(tmpl.serviceTypes) &&
	    isEmpty(tmpl.attributeSetTemplates)) {
	    long now = System.currentTimeMillis();
	    for (int i = entryClasses.size(); --i >= 0; ) {
		EntryClass eclass = entryClasses.get(i);
		try {
		    codebases.add(pickCodebase(eclass, now));
		    classes.add(eclass);
		} catch (ClassNotFoundException e) {
		}
	    }
	} else {
	    for (ItemIter iter = matchingItems(tmpl); iter.hasNext(); ) {
		Item item = iter.next();
		for (int i = item.getAttributeSets().length; --i >= 0; ) {
		    EntryRep attrSet = item.getAttributeSets()[i];
		    if (attrMatch(tmpl.attributeSetTemplates, attrSet) &&
			!classes.contains(attrSet.eclass)) {
			classes.add(attrSet.eclass);
			codebases.add(attrSet.codebase);
		    }
		}
	    }
	}
	if (classes.isEmpty())
	    return null; /* spec says null */
	EntryClassBase[] vals = new EntryClassBase[classes.size()];
	for (int i = vals.length; --i >= 0; ) {
	    vals[i] = new EntryClassBase(
				classes.get(i).getReplacement(),
				codebases.get(i));
	}
	return vals;
    }

    /**
     * The code that does the real work of getFieldValues.  If the
     * template is just a singleton entry with all null fields, then
     * we can do a faster computation by iterating over keys in the
     * given attribute's serviceByAttr map, rather than iterating
     * over items.
     */
    private Object[] getFieldValuesDo(Template tmpl, int setidx, int fldidx)
    {
	List values = new LinkedList();
	EntryRep etmpl = tmpl.attributeSetTemplates[setidx];
	boolean allNull = false;
	if (tmpl.serviceID == null &&
	    isEmpty(tmpl.serviceTypes) &&
	    tmpl.attributeSetTemplates.length == 1)
	{
	    allNull = allNull(etmpl.flds);
	}
	if (allNull) {
	    long now = System.currentTimeMillis();
	    EntryClass eclass = getDefiningClass(etmpl.eclass, fldidx);
	    boolean checkAttr = !eclass.equals(etmpl.eclass);
	    Map<Object,List<SvcReg>>[] attrMaps = serviceByAttr.get(eclass);
	    if (attrMaps != null && attrMaps[fldidx] != null) {
		for (Iterator<Map.Entry<Object,List<SvcReg>>> iter = attrMaps[fldidx].entrySet().iterator();
		     iter.hasNext(); )
		{
		    Map.Entry<Object,List<SvcReg>> ent = iter.next();
		    List<SvcReg> regs = ent.getValue();
		    Object value = ent.getKey();
		    for (int i = regs.size(); --i >= 0; ) {
			SvcReg reg = regs.get(i);
			if (reg.leaseExpiration > now &&
			    (!checkAttr ||
			     hasAttr(reg, etmpl.eclass, fldidx, value))) {
			    values.add(value);
			    break;
			}
		    }
		}
	    }
	} else {
	    for (ItemIter iter = matchingItems(tmpl); iter.hasNext(); ) {
		Item item = iter.next();
		EntryRep [] attributeSets = item.getAttributeSets();
		for (int j = attributeSets.length; --j >= 0; ) {
		    if (matchEntry(etmpl, attributeSets[j])) {
			Object value = attributeSets[j].flds.get(fldidx);
			if (!values.contains(value)) values.add(value);
		    }
		}
	    }
	}
	if (values.isEmpty())
	    return null;
	return values.toArray();
    }

    /**
     * The code that does the real work of getServiceTypes.  If the
     * template has at most service types, then we can do a fast
     * computation based solely on concrete classes, without having
     * to iterate over items, but we have to work a bit harder to
     * get codebases.
     */
    private ServiceTypeBase[] getServiceTypesDo(Template tmpl, String prefix)
    {
	List<ServiceType> classes = new LinkedList<ServiceType>();
	List<String> codebases = new LinkedList<String>();
	if (tmpl.serviceID == null && isEmpty(tmpl.attributeSetTemplates)) {
	    List services = matchingServices(tmpl.serviceTypes);
	    for (Iterator it = services.iterator(); it.hasNext(); ) {
		Item item = ((SvcReg)it.next()).item;
		addTypes(classes, codebases, tmpl.serviceTypes, prefix,
			 item.serviceType, item.codebase);
	    }
	} else {
	    for (ItemIter iter = matchingItems(tmpl); iter.hasNext(); ) {
		Item item = iter.next();
		addTypes(classes, codebases, tmpl.serviceTypes, prefix,
			 item.serviceType, item.codebase);
	    }
	}
	if (classes.isEmpty())
	    return null; /* spec says null */
	ServiceTypeBase[] vals = new ServiceTypeBase[classes.size()];
	for (int i = vals.length; --i >= 0; ) {
	    vals[i] = new ServiceTypeBase(
			       classes.get(i).getReplacement(),
			       codebases.get(i));
	}
	return vals;
    }

    /**
     * The code that does the real work of addAttributes.
     * Add every element of attrSets to item, updating serviceByAttr as
     * necessary, incrementing the number of EntryClass instances, and
     * updating entryClasses as necessary.
     */
    private void addAttributesDo(ServiceID serviceID,
				 Uuid leaseID,
				 EntryRep[] attrSets)
	throws UnknownLeaseException
    {
	long now = System.currentTimeMillis();
	SvcReg reg = checkLease(serviceID, leaseID, now);
	Item pre = (Item)reg.item.clone();
	EntryRep[] sets = reg.item.getAttributeSets();
	int i = 0;
	/* don't add if it's a duplicate */
	for (int j = 0; j < attrSets.length; j++) {
	    EntryRep set = attrSets[j];
	    if (indexOf(sets, set) < 0 && indexOf(attrSets, j, set) < 0) {
		attrSets[i++] = set;
		addAttrs(reg, set);
	    }
	}
	if (i > 0) {
	    int len = sets.length;
	    EntryRep[] nsets = new EntryRep[len + i];
	    System.arraycopy(sets, 0, nsets, 0, len);
	    System.arraycopy(attrSets, 0, nsets, len, i);
	    reg.item.setAttributeSets(nsets);
	}
	generateEvents(pre, reg.item, now);
    }

    /**
     * The code that does the real work of modifyAttributes.
     * Modify the attribute sets that match attrSetTmpls, updating
     * or deleting based on attrSets, updating serviceByAttr as necessary,
     * decrementing the number of EntryClass instances, and updating
     * entryClasses as necessary.
     */
    private void modifyAttributesDo(ServiceID serviceID,
				    Uuid leaseID,
				    EntryRep[] attrSetTmpls,
				    EntryRep[] attrSets)
	throws UnknownLeaseException
    {
	if (attrSetTmpls.length != attrSets.length)
	    throw new IllegalArgumentException(
				       "attribute set length mismatch");
	for (int i = attrSets.length; --i >= 0; ) {
	    if (attrSets[i] != null &&
		!attrSets[i].eclass.isAssignableFrom(attrSetTmpls[i].eclass))
		throw new IllegalArgumentException(
					   "attribute set type mismatch");
	}
	long now = System.currentTimeMillis();
	SvcReg reg = checkLease(serviceID, leaseID, now);
	Item pre = (Item)reg.item.clone();
	EntryRep[] preSets = pre.getAttributeSets();
	EntryRep[] sets = reg.item.getAttributeSets();
	for (int i = preSets.length; --i >= 0; ) {
	    EntryRep preSet = preSets[i];
	    EntryRep set = sets[i];
	    for (int j = attrSetTmpls.length; --j >= 0; ) {
		if (matchEntry(attrSetTmpls[j], preSet)) {
		    EntryRep attrs = attrSets[j];
		    if (attrs == null) {
			sets = deleteSet(reg.item, i);
			deleteAttrs(reg, set, true);
			break;
		    } else {
			updateAttrs(reg, set, attrs.flds);
		    }
		}
	    }
	}
	for (int i = sets.length; --i >= 0; ) {
	    EntryRep set = sets[i];
	    if (indexOf(sets, i, set) >= 0) {
		sets = deleteSet(reg.item, i);
		deleteInstance(set.eclass);
	    }
	}
	reg.item.setAttributeSets(sets);
	generateEvents(pre, reg.item, now);
    }

    /**
     * The code that does the real work of setAttributes.
     * Replace all attributes of item with attrSets, updating serviceByAttr
     * as necessary, incrementing the number of EntryClass instances, and
     * updating entryClasses as necessary.
     */
    private void setAttributesDo(ServiceID serviceID,
				 Uuid leaseID,
				 EntryRep[] attrSets)
	throws UnknownLeaseException
    {
	if (attrSets == null)
	    attrSets = emptyAttrs;
	else
	    attrSets = (EntryRep[])removeDups(attrSets);
	long now = System.currentTimeMillis();
	SvcReg reg = checkLease(serviceID, leaseID, now);
	Item pre = (Item)reg.item.clone();
	EntryRep[] entries = reg.item.getAttributeSets();
	for (int i = entries.length; --i >= 0; ) {
	    deleteAttrs(reg, entries[i], false);
	}
	reg.item.setAttributeSets(attrSets);
	for (int i = attrSets.length; --i >= 0; ) {
	    addAttrs(reg, attrSets[i]);
	}
	generateEvents(pre, reg.item, now);
    }

    /** The code that does the real work of cancelServiceLease. */
    private void cancelServiceLeaseDo(ServiceID serviceID, Uuid leaseID)
	throws UnknownLeaseException
    {
	if (serviceID.equals(myServiceID))
	    throw new SecurityException("privileged service id");
	long now = System.currentTimeMillis();
	SvcReg reg = checkLease(serviceID, leaseID, now);
	deleteService(reg, now);
	/* wake up thread if this might be the (only) earliest time */
	if (reg.leaseExpiration == minSvcExpiration)
            serviceNotifier.signal();
    }

    /** The code that does the real work of renewServiceLease. */
    private long renewServiceLeaseDo(ServiceID serviceID,
				     Uuid leaseID,
				     long renewDuration)
	throws UnknownLeaseException
    {
	long now = System.currentTimeMillis();
	long renewExpiration = renewServiceLeaseInt(serviceID, leaseID,
						    renewDuration, now);
	addLogRecord(new ServiceLeaseRenewedLogObj(serviceID, leaseID,
						   renewExpiration));
	return renewExpiration - now;
    }
    
    private SvcReg checkLease(ServiceID serviceID, Uuid leaseID, long now) 
            throws UnknownLeaseException
    {
        
	SvcReg reg = serviceByID.get(serviceID);
        if (reg == null) throw new UnknownLeaseException("No service recorded for ID: " + serviceID);
        if (!reg.leaseID.equals(leaseID)) throw new UnknownLeaseException("Incorrect lease ID: " + leaseID + " not equal to reg lease ID: " + reg.leaseID);
	if (reg.leaseExpiration <= now) throw new UnknownLeaseException("Lease expired");
        return reg;
    }
    
    private EventReg checkEvent(Uuid leaseID, long eventID, long now)
            throws UnknownLeaseException
    {
        EventReg reg = eventByID.get(Long.valueOf(eventID));
	if (reg == null) throw new UnknownLeaseException("No event recorded for ID: " + eventID);
        if (!reg.leaseID.equals(leaseID)) throw new UnknownLeaseException("Incorrect lease ID: " + eventID + " not equal to reg lease ID: " + reg.leaseID);
	if (reg.getLeaseExpiration() <= now) throw new UnknownLeaseException("Lease expired");
        return reg;
    }

    /** Renew a service lease for a relative duration from now. */
    private long renewServiceLeaseInt(ServiceID serviceID,
				      Uuid leaseID,
				      long renewDuration,
				      long now)
	throws UnknownLeaseException
    {
	if (serviceID.equals(myServiceID))
	    throw new SecurityException("privileged service id");
	if (renewDuration == Lease.ANY)
	    renewDuration = maxServiceLease;
	else if (renewDuration < 0)
	    throw new IllegalArgumentException("negative lease duration");
        SvcReg reg = checkLease(serviceID, leaseID, now);
	if (renewDuration > maxServiceLease &&
	    renewDuration > reg.leaseExpiration - now)
	    renewDuration = Math.max(reg.leaseExpiration - now,
				     maxServiceLease);
	long renewExpiration = now + renewDuration;
	/* force a re-sort: must remove before changing, then reinsert */
	serviceByTime.remove(reg);
	reg.leaseExpiration = renewExpiration;
	serviceByTime.add(reg);
	/* see if the expire thread needs to wake up earlier */
	if (renewExpiration < minSvcExpiration) {
	    minSvcExpiration = renewExpiration;
            serviceNotifier.signal();
	}
	return renewExpiration;
    }

    /** Renew the service lease for an absolute expiration time. */
    private void renewServiceLeaseAbs(ServiceID serviceID,
				      Uuid leaseID,
				      long renewExpiration)
    {
        concurrentObj.writeLock();
        try {
            SvcReg reg = serviceByID.get(serviceID);
            if (reg == null || !reg.leaseID.equals(leaseID))
                return;
            /* force a re-sort: must remove before changing, then reinsert */
            serviceByTime.remove(reg);
            reg.leaseExpiration = renewExpiration;
            serviceByTime.add(reg);
        } finally {
            concurrentObj.writeUnlock();
        }
    }

    /** The code that does the real work of cancelEventLease. */
    private void cancelEventLeaseDo(long eventID, Uuid leaseID)
	throws UnknownLeaseException
    {
	long now = System.currentTimeMillis();
	EventReg reg = checkEvent(leaseID, eventID, now);
	deleteEvent(reg);
	/* wake up thread if this might be the (only) earliest time */
	if (reg.getLeaseExpiration() == minEventExpiration)
            eventNotifier.signal();
    }

    /** The code that does the real work of renewEventLease. */
    private long renewEventLeaseDo(long eventID,
				   Uuid leaseID,
				   long renewDuration)
	throws UnknownLeaseException
    {
	long now = System.currentTimeMillis();
	long renewExpiration = renewEventLeaseInt(eventID, leaseID,
						  renewDuration, now);
	addLogRecord(new EventLeaseRenewedLogObj(eventID, leaseID,
						 renewExpiration));
	return renewExpiration - now;
    }

    private long renewEventLeaseInt(long eventID,
				    Uuid leaseID,
				    long renewDuration,
				    long now)
	throws UnknownLeaseException
    {
	if (renewDuration == Lease.ANY)
	    renewDuration = maxEventLease;
	else if (renewDuration < 0)
	    throw new IllegalArgumentException("negative lease duration");
	EventReg reg = checkEvent(leaseID, eventID, now);
	if (renewDuration > maxEventLease &&
	    renewDuration > reg.getLeaseExpiration() - now)
	    renewDuration = Math.max(reg.getLeaseExpiration() - now, maxEventLease);
	long renewExpiration = now + renewDuration;
	/* force a re-sort: must remove before changing, then reinsert */
	eventByTime.remove(reg);
	reg.setLeaseExpiration(renewExpiration);
	eventByTime.offer(reg);
	/* see if the expire thread needs to wake up earlier */
	if (renewExpiration < minEventExpiration) {
	    minEventExpiration = renewExpiration;
            eventNotifier.signal();
	}
	return renewExpiration;
    }

    /** Renew the event lease for an absolute expiration time. */
    private void renewEventLeaseAbs(long eventID,
				    Uuid leaseID,
				    long renewExpiration)
    {
        concurrentObj.writeLock();
        try {
            EventReg reg = eventByID.get(Long.valueOf(eventID));
            if (reg == null || !reg.leaseID.equals(leaseID))
                return;
            /* force a re-sort: must remove before changing, then reinsert */
            eventByTime.remove(reg);
            reg.setLeaseExpiration(renewExpiration);
            eventByTime.offer(reg);
        } finally {
            concurrentObj.writeUnlock();
        }
    }

    /**
     * The code that does the real work of renewLeases.  Each element of
     * regIDs must either be a ServiceID (for a service lease) or a Long
     * (for an event lease).  Renewals contains durations.  All three
     * arrays must be the same length.
     */
    private RenewResults renewLeasesDo(Object[] regIDs,
				       Uuid[] leaseIDs,
				       long[] renewals)
    {
	long now = System.currentTimeMillis();
	Exception[] exceptions = null;
        int l = regIDs.length;
	for (int i = 0; i < l; i++) {
	    Object id = regIDs[i];
	    try {
		if (id instanceof ServiceID)
		    renewals[i] = renewServiceLeaseInt((ServiceID)id,
						       leaseIDs[i],
						       renewals[i], now);
		else
		    renewals[i] = renewEventLeaseInt(((Long)id).longValue(),
						     leaseIDs[i], renewals[i],
						     now);
	    } catch (Exception e) {
		renewals[i] = -1;
		if (exceptions == null)
		    exceptions = new Exception[]{e};
		else
		    exceptions = (Exception[])arrayAdd(exceptions, e);
	    }
	}
	/* don't bother to weed out problem leases */
	addLogRecord(new LeasesRenewedLogObj(regIDs, leaseIDs, renewals));
	for (int i = regIDs.length; --i >= 0; ) {
	    if (renewals[i] >= 0)
		renewals[i] -= now;
	}
	return new RenewResults(renewals, exceptions);
    }

    /**
     * Renew the leases for absolute expiration times.  Skip any leases
     * with negative expiration times.
     */
    private void renewLeasesAbs(Object[] regIDs,
				Uuid[] leaseIDs,
				long[] renewExpirations)
    {
	for (int i = regIDs.length; --i >= 0; ) {
	    long expiration = renewExpirations[i];
	    if (expiration < 0)
		continue;
	    Object id = regIDs[i];
	    if (id instanceof ServiceID)
		renewServiceLeaseAbs((ServiceID)id, leaseIDs[i], expiration);
	    else
		renewEventLeaseAbs(((Long)id).longValue(), leaseIDs[i],
				   expiration);
	}
    }

    /**
     * The code that does the real work of cancelLeases.  Each element of
     * regIDs must either be a ServiceID (for a service lease) or a Long
     * (for an event lease).  The two arrays must be the same length.
     * If there are no exceptions, the return value is null.  Otherwise,
     * the return value has the same length as regIDs, and has nulls for
     * leases that successfully renewed.
     */
    private Exception[] cancelLeasesDo(Object[] regIDs, Uuid[] leaseIDs) {
	Exception[] exceptions = null;
	for (int i = regIDs.length; --i >= 0; ) {
	    Object id = regIDs[i];
	    try {
		if (id instanceof ServiceID)
		    cancelServiceLeaseDo((ServiceID)id, leaseIDs[i]);
		else
		    cancelEventLeaseDo(((Long)id).longValue(), leaseIDs[i]);
	    } catch (Exception e) {
		if (exceptions == null)
		    exceptions = new Exception[regIDs.length];
		exceptions[i] = e;
	    }
	}
	return exceptions;
    }

    /**
     * Generate events for all matching event registrations.  A null pre
     * represents creation of a new item, a null post represents deletion
     * of an item.
     */
    private void generateEvents(Item pre, Item post, long now) {
	if (inRecovery)
	    return;
	ServiceID sid = (pre != null) ? pre.getServiceID() : post.getServiceID();
	Object val = subEventByService.get(sid);
	if (val instanceof EventReg) {
	    generateEvent((EventReg)val, pre, post, sid, now);
	} else if (val instanceof EventReg[]) {
	    EventReg[] regs = (EventReg[])val;
	    for (int i = regs.length; --i >= 0; ) {
		generateEvent(regs[i], pre, post, sid, now);
	    }
	}
	for (Iterator iter = subEventByID.values().iterator();
	     iter.hasNext(); )
	{
	    generateEvent((EventReg)iter.next(), pre, post, sid, now);
	}
    }

    /**
     * Generate an event if the event registration matches.  A null pre
     * represents creation of a new item, a null post represents deletion
     * of an item.  sid is the serviceID of the item.
     */
    private void generateEvent(EventReg reg,
			       Item pre,
			       Item post,
			       ServiceID sid,
			       long now)
    {
	if (reg.getLeaseExpiration() <= now)
	    return;
	if ((reg.transitions &
		  ServiceRegistrar.TRANSITION_NOMATCH_MATCH) != 0 &&
		 (pre == null || !matchItem(reg.tmpl, pre)) &&
		 (post != null && matchItem(reg.tmpl, post)))
	    pendingEvent(reg, sid, post,
			 ServiceRegistrar.TRANSITION_NOMATCH_MATCH, now);
	else if ((reg.transitions &
		  ServiceRegistrar.TRANSITION_MATCH_NOMATCH) != 0 &&
		 (pre != null && matchItem(reg.tmpl, pre)) &&
		 (post == null || !matchItem(reg.tmpl, post)))
	    pendingEvent(reg, sid, post,
			 ServiceRegistrar.TRANSITION_MATCH_NOMATCH, now);
	else if ((reg.transitions &
		  ServiceRegistrar.TRANSITION_MATCH_MATCH) != 0 &&
		 (pre != null && matchItem(reg.tmpl, pre)) &&
		 (post != null && matchItem(reg.tmpl, post)))
	    pendingEvent(reg, sid, post,
			 ServiceRegistrar.TRANSITION_MATCH_MATCH, now);
    }

    /** Add a pending EventTask for this event registration. */
    private void pendingEvent(EventReg reg, ServiceID sid, Item item, int transition, long now)
    {
	if (item != null)
	    item = copyItem(item);
        // Should never be null.
	eventTaskMap.get(reg).submit(new EventTask(reg, sid, item, transition, proxy, this, now));
    }

    /** Generate a new service ID */
    private ServiceID newServiceID() {
	Uuid uuid = serviceIdGenerator.generate();
	return new ServiceID(
	    uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
    }

    /** Generate a new lease ID */
    private Uuid newLeaseID() {
	return resourceIdGenerator.generate();
    }

    /**
     * Write the current state of the Registrar to persistent storage.
     * <p>
     * A 'snapshot' of the Registrar's current state is represented by
     * the data contained in certain fields of the Registrar. That data
     * represents many changes -- over time -- to the Registrar's state.
     * This method will record that data to a file referred to as the
     * snapshot file.
     * <p>
     * The data written by this method to the snapshot file -- as well as
     * the format of the file -- is shown below:
     * <ul>
     * <li> our class name
     * <li> log format version number
     * <li> our service ID
     * <li> current event ID
     * <li> current values of administrable parameters and current multicast
     * announcement sequence number
     * <li> contents of the container holding the current registered services
     * <li> null (termination 'marker' for the set of registered services)
     * <li> contents of the container holding the current registered events
     * <li> null (termination 'marker' for the set of registered events)
     * </ul>
     * Each data item is written to the snapshot file in serialized form.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private void takeSnapshot(OutputStream  out) throws IOException {
	ObjectOutputStream stream = new ObjectOutputStream(out);

	stream.writeUTF(getClass().getName());
	stream.writeInt(LOG_VERSION);
	stream.writeObject(myServiceID);
	stream.writeLong(eventID);
	stream.writeInt(unicastPort);
	stream.writeObject(memberGroups);
	stream.writeObject(lookupGroups);
	stream.writeLong(announcementSeqNo.get());
	marshalAttributes(lookupAttrs, stream);
	marshalLocators(lookupLocators, stream);
	for (Iterator iter = serviceByID.entrySet().iterator(); 
	     iter.hasNext(); )
	{
	    Map.Entry entry = (Map.Entry) iter.next();
	    if (myServiceID != entry.getKey())
		stream.writeObject(entry.getValue());
	}
	stream.writeObject(null);
	for (Iterator iter = eventByID.values().iterator(); iter.hasNext(); )
	{
	    stream.writeObject(iter.next());
	}
	stream.writeObject(null);
	stream.writeInt(httpsUnicastPort);
	stream.writeBoolean(enableHttpsUnicast);
	stream.flush();
	logger.finer("wrote state snapshot");
    }

    /**
     * Retrieve the contents of the snapshot file and reconstitute the 'base'
     * state of the Registrar from the retrieved data.
     * <p>
     * The data retrieved by this method from the snapshot file is shown
     * below:
     * <ul>
     * <li> our class name
     * <li> log format version number
     * <li> our service ID
     * <li> current event ID
     * <li> current values of administrable parameters and multicast
     * announcement sequence number at time of last snapshot
     * <li> contents of the container holding the current registered services
     * <li> contents of the container holding the current registered events
     * </ul>
     * During recovery, the state of the Registrar at the time of a crash
     * or outage is re-constructed by first reconstituting the 'base state'
     * from the snapshot file; and then modifying that base state according
     * to the records retrieved from the log file. This method is invoked to
     * perform the first step in that reconstruction. As each registered
     * service or event is retrieved, it is resolved and then inserted into
     * its appropriate container object.
     * <p>
     * Because events can be generated before the next snapshot is taken,
     * the event sequence numbers must be incremented. This is because the
     * event specification requires that set of event sequence numbers
     * be monotonically increasing. Since the number of events that might
     * have been sent is arbitrary, each sequence number will be incremented
     * by a 'large' number so as to guarantee adherence to the specification.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private void recoverSnapshot(InputStream in)
	throws IOException, ClassNotFoundException
    {
	ObjectInputStream stream = new ObjectInputStream(in);
	if (!getClass().getName().equals(stream.readUTF()))
	    throw new IOException("log from wrong implementation");
	int logVersion = stream.readInt();
	if (logVersion != LOG_VERSION)
	    throw new IOException("wrong log format version");
	myServiceID = (ServiceID)stream.readObject();
	eventID = stream.readLong();
	unicastPort = stream.readInt();
	memberGroups = (String[])stream.readObject();
	lookupGroups = (String[])stream.readObject();
	announcementSeqNo.set( stream.readLong() + Integer.MAX_VALUE);
	lookupAttrs = unmarshalAttributes(stream);
	lookupLocators = prepareLocators(
	    unmarshalLocators(stream), recoveredLocatorPreparer, true);
	recoverServiceRegistrations(stream, logVersion);
	recoverEventRegistrations(stream);
	httpsUnicastPort = stream.readInt();
	enableHttpsUnicast = stream.readBoolean();
	logger.finer("recovered state from snapshot");
    }

    /** Recovers service registrations and reggie's lookup attributes */
    private void recoverServiceRegistrations(ObjectInputStream stream,
					     int logVersion)
	throws IOException, ClassNotFoundException
    {
	SvcReg sReg;
	while ((sReg = (SvcReg)stream.readObject()) != null) {
	    addService(sReg);
	}
    }

    /** Recovers event registrations */
    private void recoverEventRegistrations(ObjectInputStream stream)
	throws IOException, ClassNotFoundException
    {
	EventReg eReg;
	while ((eReg = (EventReg)stream.readObject()) != null) {
	    eReg.prepareListener(recoveredListenerPreparer);
	    addEvent(eReg);
	}
    }

    /**
     * Add a state-change record to persistent storage.
     * <p>
     * Whenever a significant change occurs to the Registrar's state, this
     * method is invoked to record that change in a file called a log file.
     * Each record written to the log file is an object reflecting both
     * the data used and the ACTIONS taken to make one change to the
     * Registrar's state at a particular point in time. If the number of
     * records contained in the log file exceeds the pre-defined threshold,
     * a snapshot of the current state of the Registrar will be recorded.
     * <p>
     * Whenever one of the following state changes occurs, this method
     * will be invoked with the appropriate implementation of the
     * LogRecord interface as the input argument.
     * <ul>
     * <li> a new service was registered
     * <li> a new event was registered
     * <li> new attributes were added to an existing service
     * <li> existing attributes of a service were modified
     * <li> new attributes were set in an existing service
     * <li> a single service lease was renewed
     * <li> a single service lease was cancelled
     * <li> a single event lease was renewed
     * <li> a single event lease was cancelled
     * <li> a set of service leases were renewed
     * <li> a set of service leases were cancelled
     * <li> a set of event leases were renewed
     * <li> a set of event leases were cancelled
     * <li> the unicast port number was set
     * <li> the set of Lookup Groups were changed
     * <li> the set of Lookup Locators were changed
     * <li> the set of Member Groups were changed
     * </ul>
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private void addLogRecord(LogRecord rec) {
	if (log == null) {
	    return;
	}
	try {
	    log.update(rec, true);
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER, "wrote log record {0}",
			   new Object[]{ rec });
	    }
	    if (logFileSize.incrementAndGet() >= persistenceSnapshotThreshold) {
		int snapshotSize = serviceByID.size() + eventByID.size();
		if (logFileSize.get() >= persistenceSnapshotWeight * snapshotSize) {
                    snapshotNotifier.signal();
		}
	    }
	} catch (Exception e) {
	    if (!Thread.currentThread().isInterrupted()) {
		logger.log(Level.WARNING, "log update failed", e);
	    }
	}
    }
}
