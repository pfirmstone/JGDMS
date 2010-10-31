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

import com.sun.jini.config.Config;
import com.sun.jini.constants.TimeConstants;
import com.sun.jini.landlord.Landlord;
import com.sun.jini.landlord.LandlordUtil;
import com.sun.jini.landlord.LeasedResource;
import com.sun.jini.landlord.LeasePeriodPolicy;
import com.sun.jini.landlord.FixedLeasePeriodPolicy;
import com.sun.jini.landlord.LocalLandlord;
import com.sun.jini.landlord.LeaseFactory;
import com.sun.jini.logging.Levels;
import com.sun.jini.start.LifeCycle;

import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.activation.ActivationGroup;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import net.jini.config.ConfigurationException;

import net.jini.export.Exporter;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.tcp.TcpServerEndpoint;

import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.TrustVerifier;
import net.jini.security.ProxyPreparer;
import net.jini.security.proxytrust.ServerProxyTrust;

import net.jini.activation.ActivationExporter;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.ServiceID;
import net.jini.core.entry.Entry;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.transaction.CannotJoinException;
import net.jini.core.transaction.CannotNestException;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import net.jini.core.transaction.UnknownTransactionException;
import net.jini.core.transaction.server.ServerTransaction;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.lookup.entry.ServiceInfo;
import net.jini.space.InternalSpaceException;
import net.jini.space.JavaSpace;

import java.io.IOException;
import java.rmi.MarshalledObject;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationSystem;
import java.rmi.activation.ActivationException;
import java.security.SecureRandom;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

/**
 * A basic implementation of a JavaSpaces<sup><font size=-2>TM</font></sup> 
 * service. This class is designed for use by both transient and 
 * persistent instances. Persistence is delegated to <code>Store</code>
 * and <code>LogOps</code> objects which handles the details
 * of implementing a particular persistence strategy. If transient
 * a <code>null</code> value is used for the <code>LogOps</code> object.
 * <p>
 * <code>OutriggerServerImpl</code> maintains a list of types of
 * entries it has seen and their subtypes using a
 * <code>TypeTree</code> object.  Each type of entry has an
 * <code>EntryHolder</code> that is stored in the
 * <code>EntryHolderSet</code> object named <code>contents</code>.
 * <p>
 * On <code>write</code>, the written entry's class and superclass are
 * added to the known <code>types</code>, and its <code>EntryRep</code>
 * is added to the space's <code>contents</code>.
 * <p>
 * On <code>read</code>, the <code>find</code> method searches through
 * the entries of its type and subtypes, asking each entry holder if it
 * has an entry that matches the template.  If a match is found, the
 * matching <code>EntryRep</code> is returned.  If none of the
 * appropriate holders has a match, it will return <code>null</code>.
 * <p>
 * On <code>take</code> we also use <code>find</code> with a
 * <code>boolean</code> that says to remove the entry that matches.
 * <p>
 * Notification requires a separate <code>Notifier</code> queue and
 * thread.  When an entry is written, a reference to it is added to the
 * queue of "unexamined entries".  The notifier thread pulls entries
 * off the queue and checks them against registered notification
 * templates.  When it has found all matches for the template, the
 * <code>Notifier</code> thread adds the notifications for this write
 * to its list of undelivered notifications, which it periodically
 * attempts to deliver.
 * <p>
 * On <code>notify</code>, the template is added to the
 * <code>TemplateHolderSet</code> named <code>template</code>.  This
 * stores <code>TemplateHolder</code> objects for each known type.
 * <p>
 * In this implementation, <code>EntryRep</code> ID's are approximate
 * time stamps.
 *
 * @author Sun Microsystems, Inc.
 */
public class OutriggerServerImpl 
    implements OutriggerServer, TimeConstants, LocalLandlord, Recover,
	       ServerProxyTrust 
{	
    /**
     * Component name we use to find items in the configuration and loggers.
     */
    public final static String COMPONENT_NAME = "com.sun.jini.outrigger";

    /** 
     * Logger name for information related to starting/restarting/destroying
     * the service
     */
    static final String lifecycleLoggerName = COMPONENT_NAME + ".lifecycle";
    
    /** Logger name for information related top level operations */
    static final String opsLoggerName = COMPONENT_NAME + ".operations";

    /** Logger name for information related to transactions */
    static final String txnLoggerName = COMPONENT_NAME + ".transactions";

    /** Logger name for information related to leases and leasing */
    static final String leaseLoggerName = COMPONENT_NAME + ".leases";

    /** Logger name for information related to iterators */
    static final String iteratorLoggerName = COMPONENT_NAME + ".iterator";

    /** Logger name for information related to join state */
    static final String joinLoggerName = COMPONENT_NAME + ".join";

    /** Logger name for information related to entry matching */
    static final String matchingLoggerName = COMPONENT_NAME + ".entryMatching";

    /** Logger name for information related to events */
    static final String eventLoggerName = COMPONENT_NAME + ".event";

    /** Logger name for information related to persistence */
    static public final String storeLoggerName = COMPONENT_NAME + ".store";

    /** 
     * Logger for information related to starting/restarting/destroying
     * the service
     */
    static private final Logger lifecycleLogger = 
	Logger.getLogger(lifecycleLoggerName);

    /** Logger for information related to top level operations */
    static private final Logger opsLogger = Logger.getLogger(opsLoggerName);

    /** Logger for information related to transactions */
    static private final Logger txnLogger = Logger.getLogger(txnLoggerName);

    /** Logger for information related to leases and leasing */
    static private final Logger leaseLogger = 
	Logger.getLogger(leaseLoggerName);

    /** Logger for logging information to iterators */
    private static final Logger iteratorLogger = 
	Logger.getLogger(iteratorLoggerName);

    /** Logger for logging information about join state */
    private static final Logger joinLogger = Logger.getLogger(joinLoggerName);

    /**
     * The name of the configuration entry we use to get the
     * the name of the log directory from.
     */
    public static final String PERSISTENCE_DIR_CONFIG_ENTRY =
	"persistenceDirectory";

    /**
     * List of <code>com.sun.jini.outrigger.EntryHolder</code>s for 
     * each specific type.
     */
    private EntryHolderSet	contents;

    /**
     * A list of known subclasses of each class of entry.
     */
    private final TypeTree	types = new TypeTree();

    /**
     * A list of hashes to check against class types.
     */
    private final HashMap	typeHashes = new HashMap();

    /**
     * Templates for which someone has registered interest
     */
    private TransitionWatchers	templates;

    /**
     * A map from event registration cookies to 
     * <code>EventRegistrationRecord</code> instances
     */
    final private Map eventRegistrations =
	Collections.synchronizedMap(new java.util.HashMap());

    /**
     * A map from contents result cookies to <code>ContentsQuery</code> objects.
     */
    final private Map contentsQueries = 
	Collections.synchronizedMap(new java.util.HashMap());

    /**
     * The transactions recovered after restart. This table
     * is discarded after recovery
     */
    private Map 		recoveredTxns;

    /**
     * The transactions in which this space is a participant.
     * Includes broken <code>Txn</code>s.
     */
    private TxnTable            txnTable;

    /**
     * The crash count.  This must be different each boot that forgets
     * (implicitly aborts) pending transactions on shutdown.
     */
    private final long	crashCount = System.currentTimeMillis();

    /**
     * The reaper thread for expired notifications
     *
     * @serial
     */
    private TemplateReaper templateReaperThread;

    /**
     * The reaper thread for removed entries. 
     * Otherwise the entries are not garbage collected after removal
     * since their "prev" links are never traversed which has the side
     * effect of removing the associated object references.
     *
     * @serial
     */
    private EntryReaper	entryReaperThread;

    /**
     * The reaper thread for expired contents queries
     *
     * @serial
     */
    private ContentsQueryReaper contentsQueryReaperThread;

    /**
     * Object that recored operations on entries and makes sure they
     * get seen by the watchers in <code>templates</code>.
     */
    private OperationJournal operationJournal;

    /**
     * The notification object
     *
     * @serial
     */
    private Notifier  	      notifier;

    /** 
     * Object that queues up lease expirations for future logging.
     * Only allocated if we have a store, otherwise left
     * <code>null</code>
     */
    private ExpirationOpQueue expirationOpQueue;

    /**
     * The monitor for ongoing transactions.
     */
    private TxnMonitor txnMonitor;

    /**
     * The wrapper that intercepts incoming remote calls for locking
     * purposes and then delegates to us. This is the object that gets
     * exported.
     */
    private final OutriggerServerWrapper serverGate;

    /** Object to notify if we destroy ourselves, may be <code>null</code> */
    private final LifeCycle lifeCycle;

    /**
     * Object we used to export ourselves.
     */
    private Exporter exporter;

    /**
     * The remote ref (e.g. stub or dynamic proxy) for this
     * server.
     * @serial
     */
    private OutriggerServer ourRemoteRef;

    /**
     * The <code>Uuid</code> for this service. Used in the 
     * <code>SpaceProxy2</code> and <code>AdminProxy</code> to 
     * implement reference equality. We also derive our
     * <code>ServiceID</code> from it.
     */
    private Uuid topUuid = null;

    /**
     * The proxy for this space.
     *
     * @serial
     */
    private SpaceProxy2 spaceProxy;

    /**
     * The admin proxy for this space.
     *
     * @serial
     */
    private AdminProxy adminProxy;

    /**
     * The participant proxy for this space
     *
     * @serial
     */
    private ParticipantProxy participantProxy;

    /**
     * Holds the basis/lower bound for all sequence numbers issued.
     * Sequence numbers are included in the various notifications given
     * by Outrigger. If Outrigger crashes and restarts, the sequence
     * numbers issued by the new invocation must not overlap with those
     * already issued. To ensure this, begin issuing sequence numbers
     * in the new Outrigger process with values far in excess of
     * anything previously issued.
     * <p>
     * For any given invocation of Outrigger, the numbers are "fully
     * ordered."  However, when Outrigger restarts after a crash, the
     * numbers (when compared to the previous invocation) will appear
     * to have a [large] gap.
     * <p>
     * [See the JavaSpaces Service Specification for detail on "fully
     *  ordered".]
     */
    private long sessionId = 0;

    /**
     * Policy used to create and renew leases on entries
     */
    private LeasePeriodPolicy entryLeasePolicy; 

    /**
     * Policy used to create and renew leases on event registrations
     */
    private LeasePeriodPolicy eventLeasePolicy;

    /**
     * Policy used to create and renew leases on event contents queries
     */
    private LeasePeriodPolicy contentsLeasePolicy;

    /**
     * Factory we use to create leases
     */
    private LeaseFactory leaseFactory;

    /**
     * @serial
     */
    private JoinStateManager joinStateManager = new JoinStateManager();

    /** 
     * Our IDs 64 bits secure random numbers, this is 
     * <code>SecureRandom</code> instance we use to create them.
     */
    private static final SecureRandom idGen = new SecureRandom();

    /** Our activation ID, <code>null</code> if we are not activatable */
    private ActivationID activationID;

    /** 
     * A prepared reference to the activation system, <code>null</code> if
     * we are not activatable.
     */
    private ActivationSystem activationSystem;

      
    /**
     * Store - The reference to the persistent store, if any.
     */
    private Store	store; 

    /**
     * Log object to record state and operation information. The
     * store provides this object. Will be <code>null</code> if
     * this is a transient server instance.
     */
    private LogOps	log;

    /** The map of <code>Uuid</code> to active iterations */
    private final Map iterations = 
	Collections.synchronizedMap(new java.util.HashMap());

    /** The login context, for logging out */
    private final LoginContext loginContext;

    /** <code>ProxyPreparer</code> for transaction managers */
    private ProxyPreparer transactionManagerPreparer;

    /** <code>ProxyPreparer</code> for event listeners */
    private ProxyPreparer listenerPreparer;

    /** 
     * <code>ProxyPreparer</code> for transaction managers 
     * that get recovered from the store. <code>null</code> if
     * this is not a persistent space.
     */
    private ProxyPreparer recoveredTransactionManagerPreparer;

    /** 
     * <code>ProxyPreparer</code> for event listeners
     * that get recovered from the store. <code>null</code> if
     * this is not a persistent space.
     */
    private ProxyPreparer recoveredListenerPreparer;

    /** Max number of entries to return in a next call */
    private int nextLimit;

    /** Max number of entries to return in a take multiple call */
    private int takeLimit;

    /**
     * When destroying the space, how long to wait for a clean
     * unexport (which allows the destroy call to return) before
     * giving up calling <code>unexport(true)</code>
     */
    private long maxUnexportDelay;

    /**
     * Length of time to sleep between unexport attempts
     */
    private long unexportRetryDelay;

    /**
     * Create a new <code>OutriggerServerImpl</code> server (possibly a
     * new incarnation of an activatable one).
     * Exports the server as well.
     *
     * @param activationID of the server, may be null.
     * @param lifeCycle the object to notify when this
     *                  service is destroyed, may be null.
     * @param configArgs set of strings to be used to obtain a
     *                   <code>Configuration</code>.
     * @param persistent If <code>true</code> will throw an 
     *                   <code>ConfigurationException</code>
     *                   if there is no persistence directory or
     *                   store specified in the configuration.
     * @param wrapper    the wrapper that intercepts 
     *                   incoming remote calls before delegating
     *                   them to <code>this</code>.
     * @throws IOException if there is problem recovering data
     *         from disk, exporting the server, or unpacking 
     *         <code>data</code>.
     * @throws ConfigurationException if the <code>Configuration</code> is 
     *         malformed.
     * @throws ActivationException if activatable and there
     *         is a problem getting a reference to the activation system.
     * @throws LoginException if the <code>loginContext</code> specified
     *         in the configuration is non-null and throws 
     *         an exception when login is attempted.  
     */
    OutriggerServerImpl(ActivationID activationID, LifeCycle lifeCycle,
			String[] configArgs, final boolean persistent,
			OutriggerServerWrapper wrapper) 
	throws IOException, ConfigurationException, LoginException,
	       ActivationException
    {	
	this.lifeCycle = lifeCycle;
	this.activationID = activationID;
	this.serverGate = wrapper;

	try {
	    final Configuration config = 
		ConfigurationProvider.getInstance(configArgs,
						  getClass().getClassLoader());

	    loginContext = (LoginContext) config.getEntry(
		COMPONENT_NAME, "loginContext", LoginContext.class, null);
	    if (loginContext == null) {
		init(config, persistent);
	    } else {
		loginContext.login();
		try {
		    Subject.doAsPrivileged(
			loginContext.getSubject(),
			new PrivilegedExceptionAction() {
			    public Object run() throws Exception {
				init(config, persistent);
				return null;
			    }
			},
			null);
		} catch (PrivilegedActionException e) {
		    throw e.getCause();
		}
	    }
	} catch (IOException e) {
	    unwindConstructor(e);
	    throw e;
	} catch (ConfigurationException e) {
	    unwindConstructor(e);
	    throw e;
	} catch (LoginException e) {
	    unwindConstructor(e);
	    throw e;	    
	} catch (RuntimeException e) {
	    unwindConstructor(e);
	    throw e;
	} catch (Throwable e) {
	    unwindConstructor(e);
	    throw (Error)e;
	}
	lifecycleLogger.log(Level.INFO, "Outrigger server started: {0}", this);
    }

    /**
     * The bulk of the work for creating an
     * <code>OutriggerServerImpl</code> server.  Anything that needs
     * to be done with a subject is done by this method.  Assumes the
     * <code>serverGate</code> and <code>activationID</code> fields have
     * been set.
     * 
     * @param config     The configuration being used to configure
     *                   this server.
     * @param persistent If <code>true</code> will throw an 
     *                   <code>ConfigurationException</code>
     *                   if there is no persistence directory or
     *                   store specified in the configuration.
     * 
     * @throws IOException if there is problem recovering data
     *         from disk, exporting the server, or unpacking 
     *         <code>data</code>.
     * @throws ConfigurationException if the <code>Configuration</code> is 
     * malformed.  */
    private void init(Configuration config, boolean persistent) 
    	throws IOException, ConfigurationException, ActivationException
    {
	txnMonitor = new TxnMonitor(this, config);

	/* Get the activation related preparers we need */

	// Default do nothing preparer
	final ProxyPreparer defaultPreparer = 
	    new net.jini.security.BasicProxyPreparer();

	if (activationID != null) {
	    final ProxyPreparer aidPreparer = 
		(ProxyPreparer)Config.getNonNullEntry(config,
		    COMPONENT_NAME, "activationIdPreparer",
		    ProxyPreparer.class, defaultPreparer);
                
	    final ProxyPreparer aSysPreparer =
		(ProxyPreparer)Config.getNonNullEntry(config,
		     COMPONENT_NAME, "activationSystemPreparer",
		     ProxyPreparer.class, defaultPreparer);

	    activationID = 
		(ActivationID)aidPreparer.prepareProxy(activationID);
	    activationSystem =
		(ActivationSystem)aSysPreparer.prepareProxy(
		    ActivationGroup.getSystem());
	}


	// The preparers that all outrigger's need
	transactionManagerPreparer = 
	    (ProxyPreparer)Config.getNonNullEntry(config, 
		COMPONENT_NAME, "transactionManagerPreparer",
		ProxyPreparer.class, defaultPreparer);

	listenerPreparer = 
	    (ProxyPreparer)Config.getNonNullEntry(config, 
		COMPONENT_NAME, "listenerPreparer",
		ProxyPreparer.class, defaultPreparer);

	/* Export the server. */

	// Get the exporter

	/* What we use for the default (or in the default activatable case
	 * what we make the underlying exporter).
	 */
	final Exporter basicExporter = 
	    new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
				  new BasicILFactory(), false, true);
	if (activationID == null) {
	    exporter = (Exporter)Config.getNonNullEntry(config,
		COMPONENT_NAME,	"serverExporter", Exporter.class,
		basicExporter);
	} else {
	    exporter = (Exporter)Config.getNonNullEntry(config, 
		COMPONENT_NAME,	"serverExporter", Exporter.class,
		new ActivationExporter(activationID, basicExporter),
		activationID);
	}

	ourRemoteRef = (OutriggerServer)exporter.export(serverGate);

	// Create our top level proxy
	final long maxServerQueryTimeout =
	    Config.getLongEntry(config, COMPONENT_NAME, 
		"maxServerQueryTimeout", Long.MAX_VALUE, 1, Long.MAX_VALUE);

	/* Initialize various fields that will be filled in during
	 * log recovery.
	 */
	contents = new EntryHolderSet(this);
	templates = new TransitionWatchers(this);
	
	// This takes a while the first time, so let's get it going
	new Thread() {
	    public void run() {
		OutriggerServerImpl.nextID();
	    }
	}.start();

	// Use this (trivially) in log recovery
	operationJournal = new OperationJournal(templates);

	// Needs to be null so logging is not performed
	// while store (if any) does recovery.
	log = null;

	// Get store from configuration

	if (persistent) {
	    store = (Store)Config.getNonNullEntry(config,
						  COMPONENT_NAME,
						  "store", Store.class);

	    /* If we have a store we need a ExpirationOpQueue and the
	     * preparers for recovered proxies.
	     */
	    expirationOpQueue = new ExpirationOpQueue(this);
	    expirationOpQueue.setDaemon(true);
	    expirationOpQueue.start();

	    recoveredTransactionManagerPreparer = 
		(ProxyPreparer)Config.getNonNullEntry(config, 
		    COMPONENT_NAME, "recoveredTransactionManagerPreparer", 
		    ProxyPreparer.class, defaultPreparer);

	    recoveredListenerPreparer = 
		(ProxyPreparer)Config.getNonNullEntry(config, 
		    COMPONENT_NAME, "recoveredListenerPreparer", 
                    ProxyPreparer.class, defaultPreparer);
	}

	/* Now that we have a recoveredTransactionManagerPreparer
	 * (if we need one) create the TxnTable
	 */
	txnTable = new TxnTable(recoveredTransactionManagerPreparer);

	// If we have a store, recover the log
	if (store != null) {
	    log = store.setupStore(this);
	    
	    // Record this boot
	    //
	    log.bootOp(System.currentTimeMillis(), getSessionId());
	    recoverTxns();
	} else if (activationID != null || persistent) {
	    /* else we don't have a store, if we need one complain
	     * will be logged by constructor
	     */
	    throw new ConfigurationException("Must provide for a " +
	        "store for component " + COMPONENT_NAME + ", by providing " +
		"valid values for the store or " +
	        PERSISTENCE_DIR_CONFIG_ENTRY + " entries if creating " +
		" a persistent space");
	}

	/* Now that we have recovered any store we have, create a 
	 * Uuid if there was not one in the store.
	 */
	if (topUuid == null) {
	    topUuid = UuidFactory.generate();
	    if (log != null)
		log.uuidOp(topUuid);
	}		

	if (ourRemoteRef instanceof RemoteMethodControl) {
	    spaceProxy = new ConstrainableSpaceProxy2(ourRemoteRef, topUuid,
		maxServerQueryTimeout, null);
	    adminProxy = 
		new ConstrainableAdminProxy(ourRemoteRef, topUuid, null);
	    participantProxy =
		new ConstrainableParticipantProxy(ourRemoteRef, topUuid, null);
	} else {
	    spaceProxy = new SpaceProxy2(ourRemoteRef, topUuid, 
					 maxServerQueryTimeout);
	    adminProxy = new AdminProxy(ourRemoteRef, topUuid);
	    participantProxy = new ParticipantProxy(ourRemoteRef, topUuid);
	}

	leaseFactory = new LeaseFactory(ourRemoteRef, topUuid);

	/* Initialize any non-persistent state we have
	 */

	// Create lease policy objects
	entryLeasePolicy = (LeasePeriodPolicy)Config.getNonNullEntry(
	    config, COMPONENT_NAME, "entryLeasePeriodPolicy",
            LeasePeriodPolicy.class, 
	    new FixedLeasePeriodPolicy(Long.MAX_VALUE, 1 * DAYS));

	eventLeasePolicy = (LeasePeriodPolicy)Config.getNonNullEntry(
	    config, COMPONENT_NAME, "eventLeasePeriodPolicy",
	    LeasePeriodPolicy.class, 
	    new FixedLeasePeriodPolicy(1 * HOURS, 1 * HOURS));

	contentsLeasePolicy = (LeasePeriodPolicy)Config.getNonNullEntry(
	    config, COMPONENT_NAME, "contentsLeasePeriodPolicy",
	    LeasePeriodPolicy.class, 
	    new FixedLeasePeriodPolicy(1 * HOURS, 1 * HOURS));

	nextLimit = Config.getIntEntry(config, COMPONENT_NAME, 
            "iteratorBatchSize", 100, 1, Integer.MAX_VALUE);

	takeLimit = Config.getIntEntry(config, COMPONENT_NAME, 
            "takeMultipleLimit", 100, 1, Integer.MAX_VALUE);

	maxUnexportDelay = Config.getLongEntry(config, COMPONENT_NAME, 
	    "maxUnexportDelay", 2 * MINUTES, 0, Long.MAX_VALUE);

	unexportRetryDelay = Config.getLongEntry(config, COMPONENT_NAME, 
	    "unexportRetryDelay", SECONDS, 1, Long.MAX_VALUE);

	/* Kick off independent threads.
	 */

	// start the JoinStateManager
	joinStateManager.startManager(config, log, spaceProxy,
	    new ServiceID(topUuid.getMostSignificantBits(),
			  topUuid.getLeastSignificantBits()),
	    attributesFor());

	operationJournal.setDaemon(true);
	operationJournal.start();

	notifier = 
	    new Notifier(spaceProxy, recoveredListenerPreparer, config);
 
	final long reapingInterval = 
	    Config.getLongEntry(config, COMPONENT_NAME,	"reapingInterval", 
				10000, 1, Long.MAX_VALUE);

	final int reapingPriority = 
	    Config.getIntEntry(config, COMPONENT_NAME,"reapingPriority", 
		Thread.NORM_PRIORITY, Thread.MIN_PRIORITY, 
		Thread.MAX_PRIORITY);
                
	templateReaperThread = new TemplateReaper(reapingInterval);
	templateReaperThread.setPriority(reapingPriority);
	templateReaperThread.setDaemon(true);
	templateReaperThread.start(); 

	entryReaperThread = new EntryReaper(reapingInterval);
	entryReaperThread.setPriority(reapingPriority);
	entryReaperThread.setDaemon(true);
	entryReaperThread.start(); 

	contentsQueryReaperThread = new ContentsQueryReaper(reapingInterval);
	contentsQueryReaperThread.setPriority(reapingPriority);
	contentsQueryReaperThread.setDaemon(true);
	contentsQueryReaperThread.start();
    }

    /**
     * Undo any work a failed constructor might have done without
     * destroying the service. Has to work even if the 
     * constructor failed part way through.
     * @param cause The exception that caused the 
     *              constructor to fail.
     */
    private void unwindConstructor(Throwable cause) {
	serverGate.rejectCalls(
	    new RemoteException("Constructor failure", cause));

	lifecycleLogger.log(Level.SEVERE, 
	    "exception encountered while (re)starting server", cause);

	// If we created a JoinStateManager,
	try {
	    joinStateManager.destroy();
	} catch (Throwable t) {
	    // Ignore and go on
	}

	// If we exported, unexport, use force=true since no calls we
	// care about should be in progress.
	if (ourRemoteRef != null) {
	    try {
		exporter.unexport(true);
	    } catch (Throwable t) {
		// Ignore and go on
	    }
	}

	if (expirationOpQueue != null)
	    expirationOpQueue.terminate();

	if (txnMonitor != null) {
	    try {
		txnMonitor.destroy();
	    } catch (Throwable t) {
		// Ignore and go on
	    }
	}

	// Interrupt and join independent threads
	if (notifier != null) {
	    try {
		notifier.terminate();
	    } catch (Throwable t) {
		// Ignore and go on
	    }
	}

	if (operationJournal != null) {
	    try {
		operationJournal.terminate();
	    } catch (Throwable t) {
		// Ignore and go on
	    }	       
	}

	unwindReaper(templateReaperThread);
	unwindReaper(entryReaperThread);
	unwindReaper(contentsQueryReaperThread);

	// Close (but do not destroy) the store
	if (store != null) {
	    try {
		store.close();
	    } catch (Throwable t) {
		// Ignore and go on
	    }
	}
    }

    /** Kill the given reaper as quickly and quietly */
    private void unwindReaper(Reaper r) {
	if (r == null)
	    return;

	try {
	    r.kill();
	    r.join();
	} catch (Throwable t) {
	    // Ignore and go on
	}
    }

    /**
     * Process the recovered transactions. This includes forcing the
     * state to PREPARED, placing the txn on the tnxs list (or brokenTxns
     * list if the codebase is broken) and finally monitoring the txns.
     */
    private void recoverTxns() {

	if (recoveredTxns == null)
	    return;

	// Only PREPARED transactions are recovered. Since the txn
	// state is ACTIVE when stored, we must now force the state
	// to PREPARED
	//
	final Collection values = recoveredTxns.values();
	final Iterator t = values.iterator();
	while (t.hasNext()) {
	    // Note, Txns get recovered in the PREPARED state 
	    txnTable.recover((Txn)t.next());
	}

	// Monitor all of the pending transactions
	//
	monitor(values);
	recoveredTxns = null;		// done with this list
    }

    long getSessionId() {
	return sessionId;
    }

    /**
     * Log a "cancel" operation. Called from 
     * <code>EntryHolder</code> and watchers.
     */
    void cancelOp(Uuid cookie, boolean expired) {
	if (log != null) log.cancelOp(cookie, expired);
    }

    /**
     * Schedule a cancelOp.
     * @param cookie The cookie to pass to <code>cancelOp</code> when
     *        it is called.
     */
    void scheduleCancelOp(Uuid cookie) {
	if (expirationOpQueue != null)
	    expirationOpQueue.enqueue(cookie);
    }

    /**
     * Check if an entry (or template) class definition
     * has changed. If so, throw an exception. If it is a new
     * class, then we remember it.
     */
    private void typeCheck(EntryRep rep) throws UnmarshalException {
	if (rep == null)
	    return;

	synchronized (typeHashes) {
 	    // Check the leaf class. If this (or any superclass)
	    // check returns a match (checkClass() == true) we can
	    // exit since each class hash covers its superclasses.
	    //
	    if (checkClass(rep.classFor(), rep.getHash()))
		return;

	    // Check superclasses
	    String[] superclasses = rep.superclasses();
	    long[] hashes = rep.getHashes();
	    for (int i = 0; i < superclasses.length; i++) {
		if (checkClass(superclasses[i], hashes[i]))
		    return;
	    }
	}
    }

    /**
     * Compare the given hash with the hash stored for the given
     * class name, if any. If there is a hash for the class name and
     * it does not match, throw an exception. If it does match return
     * true. Otherwise, record the new hash and class name and return
     * false.
     */
    private boolean checkClass(String className, long value)
	throws UnmarshalException
    {
	Long hash = (Long)typeHashes.get(className);

	if (hash == null) {
	    typeHashes.put(className, new Long(value));
	    return false;	// new class
	} else {
	    if (hash.longValue() != value) {
		final String msg = "Class mismatch: " + className;
		final UnmarshalException ue = new UnmarshalException(msg);
		opsLogger.log(Levels.FAILED, msg, ue);
		throw ue;
	    }
	}
	return true;		// match
    }

    /**
     * Utility method to calculate the lease duration/expiration for
     * a new resource and set the resource's expiration. Handles
     * various error conditions.
     */
    private LeasePeriodPolicy.Result grant(LeasedResource resource,
	long requestedDuration, LeasePeriodPolicy policy, String policyName) 
    {
	final LeasePeriodPolicy.Result r;
	try {
	    r = policy.grant(resource, requestedDuration);
	    resource.setExpiration(r.expiration);
	} catch (LeaseDeniedException e) {
	    // This should never happen, we should not be using a
	    // policy or a factory that could thrown LeaseDenied on lease
	    // creation, re-throw as InternalSpaceException
	    throw logAndThrow(new 
		InternalSpaceException("OutriggerServerImpl:" + policyName +
		    ".grant threw LeaseDeniedException", e),
		opsLogger);
	}

	return r;
    }

    /** 
     * Utility method to check for zero length arrays of entries 
     * and throw an exception if necessary
     */
    private void checkForEmpty(EntryRep[] entries, String msg) {
	if (entries.length == 0)
	    throw logAndThrowIllegalArg(msg);
    }

    /** 
     * Utility method to check for null argument values and throw
     * an exception if necessary
     */
    private void checkForNull(Object value, String msg) {
	if (value == null) 
	    throw logAndThrow(new NullPointerException(msg), opsLogger);
    }

    /** Utility method to check for negative timeouts */
    private void checkTimeout(long timeout) {
	if (timeout < 0) {
	    throw logAndThrowIllegalArg(
		"timeout = " + timeout + "must be a non-negative value");
	}
    }

    /** Utility method to check for non-postive limits */
    private void checkLimit(long limit) {
	if (limit < 1) {
	    throw logAndThrowIllegalArg(
		"limit = " + limit + "must be a positive value");
	}
    }

    // purposefully inherit doc comment from supertype
    public long[] write(EntryRep rep, Transaction tr, long lease)
	throws TransactionException, RemoteException
    {
	opsLogger.entering("OutriggerServerImpl", "write");

	typeCheck(rep);
	rep.pickID();

	if (opsLogger.isLoggable(Level.FINER) && (tr != null)) {
	    ServerTransaction str = serverTransaction(tr);
	    opsLogger.log(Level.FINER, "OutriggerServerImpl: write under " +
                "transaction [mgr:{0} id:{1}]",
		new Object[]{str.mgr, new Long(str.id)});
	}

	Txn txn = enterTxn(tr);
	
	// Set expiration before adding to tables
	final LeasePeriodPolicy.Result r =
	    grant(rep, lease, entryLeasePolicy, "entryLeasePolicy");

	final EntryHolder holder = contents.holderFor(rep);
	final EntryHandle handle = new EntryHandle(rep, txn, holder);

	// Verify that the transaction is still active. Lock it so
	// nobody can change it behind our backs while where making
	// this important determination. If there is no txn, just do it.
	try {
	    if (txn != null) 
		txn.ensureActive();

	    // At this point nothing can go wrong, log this addition.
	    if (log != null)
		log.writeOp(rep, (txn == null) ? null : txn.getId());

	    synchronized (handle) {
		addWrittenRep(handle, holder, txn);
		recordTransition(new EntryTransition(handle, txn, true, true,
						     true));
	    }		    

	} finally {
	    if (txn != null) 
		txn.allowStateChange();
	}

	if (opsLogger.isLoggable(Level.FINEST)) {
	    opsLogger.log(Level.FINEST, "writing {0} (txn = {1})",
			  new Object[]{rep, txn});
	}

	return new long[] {r.duration, 
			   rep.id().getMostSignificantBits(),
			   rep.id().getLeastSignificantBits()};
    }

    public long[] write(EntryRep[] entries, Transaction tr, long[] leaseTimes)
        throws TransactionException, RemoteException
    {
	opsLogger.entering("OutriggerServerImpl", "write<multiple>");
	checkForEmpty(entries, "Must write at least one entry");

	if (entries.length != leaseTimes.length) {
	    throw logAndThrowIllegalArg(
		"Collection of entries and lease times must be same length");
	}

	for (int i=0;i<entries.length;i++) {
	    checkForNull(entries[i], "Can't write null entry");

	    if (leaseTimes[i] < 0 && leaseTimes[i] != Lease.ANY) {
		throw logAndThrowIllegalArg("Bad requested lease length:" +
					    leaseTimes[i]);
	    }
	}

	for (int i=0; i<entries.length; i++) {
	    typeCheck(entries[i]);
	    entries[i].pickID();
	}

	if (opsLogger.isLoggable(Level.FINER) && (tr != null)) {
	    ServerTransaction str = serverTransaction(tr);
	    opsLogger.log(Level.FINER, "OutriggerServerImpl: write<multiple> " +
			  "under transaction [mgr:{0} id:{1}]",
		new Object[]{str.mgr, new Long(str.id)});
	}

	Txn txn = enterTxn(tr);

	final LeasePeriodPolicy.Result[] leaseData = 
	    new LeasePeriodPolicy.Result[entries.length];
	final EntryHolder[] holders = new EntryHolder[entries.length];
	final EntryHandle[] handles = new EntryHandle[entries.length];
    
	// Set expiration before adding to tables
	for (int i=0; i<entries.length; i++) {
	    final EntryRep entry = entries[i];
	    leaseData[i] = 
		grant(entry, leaseTimes[i], entryLeasePolicy, "entryLeasePolicy");
	    holders[i] = contents.holderFor(entry);
	    handles[i] = new EntryHandle(entry, txn, holders[i]);
	}

	// Verify that the transaction is still active. Lock it so
	// nobody can change it behind our backs while where making
	// this important determination. If there is no txn, just do it.
	try {
	    if (txn != null) 
		txn.ensureActive();

	    // At this point nothing can go wrong, log this addition.
	    if (log != null)
		log.writeOp(entries, (txn == null) ? null : txn.getId());

	    for (int i=0; i<handles.length; i++) {
		synchronized (handles[i]) {
		    addWrittenRep(handles[i], holders[i], txn);
		    recordTransition(
			new EntryTransition(handles[i], txn, true, true, true));
		}
	    }		    
	} finally {
	    if (txn != null) 
		txn.allowStateChange();
	}

	if (opsLogger.isLoggable(Level.FINEST)) {
	    opsLogger.log(Level.FINEST, "writing multiples (txn = {0})",
			  new Object[]{txn});
	}

	final long[] rslt = new long[entries.length * 3];
	for (int i=0; i<entries.length; i++) {
	    rslt[i] = leaseData[i].duration;
	    rslt[i+1] = entries[i].id().getMostSignificantBits();
	    rslt[i+2] = entries[i].id().getLeastSignificantBits();
	}
	return rslt;
    }

    /**
     * Add the written rep to the relevant data structures. This is
     * the common code between <code>write</code> and
     * <code>recoverWrite</code>.
     *
     * @see #write
     * @see #recoverWrite 
     */
    private void addWrittenRep(EntryHandle handle, EntryHolder holder, 
			       Txn txn) 
    {
	opsLogger.log(Level.FINEST, "OutriggerServerImpl: addWrittenRep");

	/*
	 * Since we're adding it, update the type information.  Extra
	 * type information isn't very critical, but having an object
	 * in the system which cannot be matched by all its
	 * superclasses is Just Plain Wrong.  We do this first in case
	 * of a crash between the two operations (We could do it all
	 * in one synchronized wad, but that seems unnecessary,
	 * considering the small overhead of extra type info that will
	 * likely reappear as the entry that failed to be successfully
	 * added due to the crash gets added again later).
	 */
	types.addTypes(handle.rep());

	/*
	 * Now we can add the entry, safe in the knowledge that it can be
	 * found by any of its superclasses
	 */
	holder.add(handle, txn);
    }

    /**
     * Records a transition in the visibility of
     * an entry. This method should be called <em>after</em>
     * the transition has been made visible in 
     * <code>contents</code> (including any subsidiary 
     * objects such as the appropriate <code>EntryHandle</code>).
     * <p>
     * Currently we only post transitions that increase the visibility
     * of an entry, or those that resolve a lock.  We don't post
     * transitions that reflect the locking of an entry or straight
     * takes. Some of the watchers exploit this fact, so if we ever
     * start to post transitions take and/or new lock transition
     * the watcher may need to be updated.
     *
     * @param transition an object describing the visibility
     *        transition of an entry.
     * @throws NullPointerException if <code>transition</code> 
     *         is <code>null</code> or if <code>transition.getEntry</code>
     *         is <code>null</code>.  */
    void recordTransition(EntryTransition transition) {
	operationJournal.recordTransition(transition);
    }

    /**
     * Queue up an event for delivery.
     * @param sender An object that on request will
     *               attempt to deliver its event
     *               to the associated listener.
     * @throws NullPointerException if <code>sender</code> is
     * <code>null</code>
     */
    void enqueueDelivery(EventSender sender) {
	notifier.enqueueDelivery(sender);
    }

    /**
     * Atomically check to see if the passed entry can be read/taken by
     * the specified operation using the specified transaction and if
     * it can read/take it and return <code>true</code>, otherwise
     * return <code>false</code>. If the entry can not be read/taken
     * because of transaction conflicts the conflicting transaction(s)
     * will be added to the list of transactions to monitor.
     *
     * @param handle The <code>EntryHandle</code> of the entry
     *               the caller wants to read/take.
     * @param txn    If non-null the transaction to perform
     *               this operation under. Note, if non-null and this
     *               is not active <code>false</code> will be returned.
     * @param takeIt <code>true</code> if the caller is trying
     *               take the passed entry, <code>false</code>
     *               otherwise.
     * @param lockedEntrySet If the entry can not be read/taken
     *               because of a transaction conflict and the value of
     *               this argument is non-null, the ID of the entry
     *               will be added to this set. This method assumes
     *               that any concurrent access to the set is being
     *               arbitrated by the set or by the caller of this method.
     * @param provisionallyRemovedEntrySet If the entry can not be
     *              read/taken because it has been provisionally
     *              removed then its handle will be placed in the
     *              passed <code>WeakHashMap</code> as a key (with
     *              null as the value).  May be <code>null</code> in
     *              which case provisionally removed entries will not
     *              be recorded. This method assumes that any
     *              concurrent access is being arbitrated by the set
     *              or by the caller.
     * @param now    an estimate of the current time in milliseconds
     *               since the beginning of the epoch.
     * @param watcher The <code>QueryWatcher</code> requesting
     *               capture of the entry.
     * @return <code>true</code> if the entry could be read/taken and
     *         <code>false</code> otherwise.
     * @throws NullPointerException if entry is <code>null</code>.  
     */
     boolean attemptCapture(EntryHandle handle, TransactableMgr txn,
          boolean takeIt, Set lockedEntrySet, 
	  WeakHashMap provisionallyRemovedEntrySet, long now,
	  QueryWatcher watcher) 
     {
	 final EntryHolder holder = contents.holderFor(handle.rep());
	 final Set conflictSet = new java.util.HashSet();
	 if (holder.attemptCapture(handle, txn, takeIt,
				   conflictSet, lockedEntrySet, 
				   provisionallyRemovedEntrySet, now))
	     return true;

	 monitor(watcher, conflictSet);
	 return false;
     }

    // purposefully inherit doc comment from supertype
    public EventRegistration
	notify(EntryRep tmpl, Transaction tr, RemoteEventListener listener,
	       long leaseTime, MarshalledObject handback)
	throws TransactionException, RemoteException
    {
	opsLogger.entering("OutriggerServerImpl", "notify");

	typeCheck(tmpl);

	checkForNull(listener, "Passed null listener for event registration");

	listener = 
	    (RemoteEventListener)listenerPreparer.prepareProxy(listener);

	final long currentOrdinal = operationJournal.currentOrdinal();

	// Register a watcher for this client
	tmpl = setupTmpl(tmpl);

        ServerTransaction str = serverTransaction(tr);
	Txn txn = enterTxn(tr);

	final Uuid cookie = UuidFactory.generate();
	final long eventID = nextID();
	final long now = System.currentTimeMillis();

	final EventRegistrationWatcher reg;
	if (txn == null) {
	    reg = new StorableEventWatcher(now, currentOrdinal, cookie,
					   handback, eventID, listener);
	} else {
	    reg = new TransactableEventWatcher(now, currentOrdinal, cookie,
		handback, eventID, listener, txn);
	}

	// Get the expiration times
	grant(reg, leaseTime, eventLeasePolicy, "eventLeasePolicy");
	
	/* Add to eventRegistrations here so reg will be in the map
	 * before it can ever try and remove itself.
	 */
	eventRegistrations.put(cookie, reg);

	/* Prevent transaction state change while we do this. 
	 * If there is no txn, just do it.
	 */
	if (txn != null) {
	    try {
		txn.ensureActive();
		templates.add(reg, tmpl);
		txn.add((Transactable)reg);
	    } finally {
		txn.allowStateChange();
	    }	      
	} else {
	    // log before adding to templates
	    if (log != null)
		log.registerOp((StorableResource)reg, 
			       "StorableEventWatcher",
			       new StorableObject[]{tmpl});

	    templates.add(reg, tmpl);
	}

	return new EventRegistration(eventID, spaceProxy, 
	    leaseFactory.newLease(cookie, reg.getExpiration()),
	    0);
    }


    public EventRegistration registerForAvailabilityEvent(EntryRep[] tmpls,
	    Transaction tr, boolean visibilityOnly, RemoteEventListener listener,
  	    long leaseTime, MarshalledObject handback)
        throws TransactionException, RemoteException
    {
	opsLogger.entering("OutriggerServerImpl", "registeForAvailabilityEvent");

	checkForNull(listener, "Passed null listener for event registration");
	checkForEmpty(tmpls, "Must provide at least one template");

	// eventLeasePolicy.grant allo ws 0 length leases
 	if (leaseTime == 0) {
	    throw logAndThrowIllegalArg("leaseTime must be non-zero");
	}

	listener = 
	    (RemoteEventListener)listenerPreparer.prepareProxy(listener);
	final long currentOrdinal = operationJournal.currentOrdinal();

        ServerTransaction str = serverTransaction(tr);
	Txn txn = enterTxn(tr);

	for (int i=0; i<tmpls.length; i++) {
	    typeCheck(tmpls[i]);
	}

	for (int i=0; i<tmpls.length; i++) {
	    tmpls[i] = setupTmpl(tmpls[i]);
	}

	final Uuid cookie = UuidFactory.generate();
	final long eventID = nextID();
	final long now = System.currentTimeMillis();

	final AvailabilityRegistrationWatcher reg;
	if (txn == null) {
	    reg = new StorableAvailabilityWatcher(now, currentOrdinal, cookie,
                visibilityOnly, handback, eventID, listener);
	} else {
	    reg = new TransactableAvailabilityWatcher(now, currentOrdinal, 
		cookie, visibilityOnly,	handback, eventID, listener, txn);
	}

	// Get the expiration time
	grant(reg, leaseTime, eventLeasePolicy, "eventLeasePolicy");

	/* Add to eventRegistrations here so reg will be in the map
	 * before it can ever try and remove itself.
	 */
	eventRegistrations.put(cookie, reg);

	if (txn != null) {
	    try {
		txn.ensureActive();
		for (int i=0; i<tmpls.length; i++) {
		    templates.add(reg, tmpls[i]);
		}
		txn.add((Transactable)reg);
	    } finally {
		txn.allowStateChange();
	    }	      
	} else {
	    // log before adding to templates
	    if (log != null)
		log.registerOp((StorableResource)reg,
			       "StorableAvailabilityWatcher",
			       tmpls);
	    
	    for (int i=0; i<tmpls.length; i++) {
		templates.add(reg, tmpls[i]);
	    }
	}

	return new EventRegistration(eventID, spaceProxy, 
	    leaseFactory.newLease(cookie, reg.getExpiration()),
	    0);
    }


    /**
     * Remove the passed <code>EventRegistrationRecord</code> from
     * event registrations map.
     * @param reg The <code>EventRegistrationRecord</code> object to remove.
     * @throws NullPointerException if <code>reg</code> is <code>null</code>.
     */
    void removeEventRegistration(EventRegistrationRecord reg) {
	eventRegistrations.remove(reg.getCookie());
    }	    

    private EntryRep setupTmpl(EntryRep tmpl) {
	/*
	 * If the notification is for a null template, use the special
	 * matchAnyEntryRep.  When the notification system adds a
	 * record for this EntryRep, any time any incoming EntryRep
	 * enters the space, the notification system will
	 * successfully match it and send the notification.
	 */
	if (tmpl == null)
	    tmpl = EntryRep.matchAnyEntryRep();

	/*
	 * This entry may be a new type if the notification request
	 * arrives before any entry of the type is written.
	 */
	types.addTypes(tmpl);

	return tmpl;
    }

    // purposefully inherit doc comment from supertype
    public void cancel(Uuid cookie) throws UnknownLeaseException {
	leaseLogger.entering("OutriggerServerImpl","cancel");

	// Look for entry leases first
	final EntryHandle handle = contents.handleFor(cookie);
	if (handle != null) {
	    synchronized (handle) {
		if (handle.removed())
		    throw throwNewUnknownLeaseException(cookie);

		if (handle.isProvisionallyRemoved()) {
		    try {
			handle.waitOnCompleteRemoval();
		    } catch (InterruptedException e) {
			// should never happen
			throw new AssertionError(e);
		    }
		    throw throwNewUnknownLeaseException(cookie);
		}
		handle.provisionallyRemove();
	    }

	    cancelOp(cookie, false);

	    synchronized (handle) {
		contents.remove(handle);
	    }

	    return;
	}


	final EventRegistrationRecord reg = 
	    (EventRegistrationRecord)eventRegistrations.get(cookie);
	if (reg != null && reg.cancel()) {
            return; 
	    /* Think before adding anything here, if you do
	     * other places where registrations get killed will not 
	     * do everything they need.
	     */
	}

	final ContentsQuery contentsQuery = 
	    (ContentsQuery)contentsQueries.get(cookie);
	if (contentsQuery != null && contentsQuery.cancel()) {
	    return;
	}

	throw throwNewUnknownLeaseException(cookie);
    }

    // purposefully inherit doc comment from supertype
    public long renew(Uuid cookie, long extension) 
        throws UnknownLeaseException, LeaseDeniedException
    {
	leaseLogger.entering("OutriggerServerImpl","renew");

	LeasedResource    resource;
	LeasePeriodPolicy policy;
	
	if (null != (resource = contents.getLeasedResource(cookie))) 
	    policy = entryLeasePolicy;
	else if (null != 
		 (resource = (LeasedResource)eventRegistrations.get(cookie)))
	    policy = eventLeasePolicy;
	else if (null != 
		 (resource = (LeasedResource)contentsQueries.get(cookie)))
	    policy = contentsLeasePolicy;
	else 
	    throw throwNewUnknownLeaseException(cookie);

	synchronized (resource) {
	    if (resource.getExpiration() <= System.currentTimeMillis()) {
		// Lease has expired, don't renew
		throw throwNewUnknownLeaseException(cookie);
	    }

	    // No one can expire the lease, so it is safe to update.
	    final LeasePeriodPolicy.Result r =
		policy.renew(resource, extension);

	    if (log != null)
		log.renewOp((Uuid)cookie, r.expiration);

	    resource.setExpiration(r.expiration);

	    if (leaseLogger.isLoggable(Level.FINER)) {
		leaseLogger.log(Level.FINER, "renew({0},{1}) returns {2}",
		    new Object[]{cookie, new Long(extension), 
				 new Long(r.duration)});
	    }

	    return r.duration;
	}
    }

    // purposefully inherit doc comment
    public Landlord.RenewResults renewAll(Uuid[] cookies, 
					  long[] extensions) 
    {
	leaseLogger.entering("OutriggerServerImpl","renewAll");

	if (leaseLogger.isLoggable(Level.FINER)) {
	    leaseLogger.log(Level.FINER, "renewAll:{0} leases",
			    new Long(cookies.length));
	}

	return LandlordUtil.renewAll(this, cookies, extensions);
    }

    // purposefully inherit doc comment
    public Map cancelAll(Uuid[] cookies) {
	leaseLogger.entering("OutriggerServerImpl", "cancelAll");
	return LandlordUtil.cancelAll(this, cookies);	    
    }


    public Object read(EntryRep tmpl, Transaction txn, long timeout,
		       QueryCookie cookie)
	throws TransactionException, RemoteException, InterruptedException
    {
	if (opsLogger.isLoggable(Level.FINER)) {
	    opsLogger.log(Level.FINER, 
		"read:tmpl = {0}, timeout = {1}, cookie = {2}",
		new Object[]{tmpl, new Long(timeout), cookie});
	}
	return getMatch(tmpl, txn, timeout, false, false, cookie);
    }

    public Object take(EntryRep tmpl, Transaction txn, long timeout,
		       QueryCookie cookie)
	throws TransactionException, RemoteException, InterruptedException
    {
	if (opsLogger.isLoggable(Level.FINER)) {
	    opsLogger.log(Level.FINER, 
		"take:tmpl = {0}, timeout = {1}, cookie = {2}",
		new Object[]{tmpl, new Long(timeout), cookie});
	}
	return getMatch(tmpl, txn, timeout, true, false, cookie);
    }

    public Object readIfExists(EntryRep tmpl, Transaction txn, long timeout,
			       QueryCookie cookie)
	throws TransactionException, RemoteException, InterruptedException
    {
	if (opsLogger.isLoggable(Level.FINER)) {
	    opsLogger.log(Level.FINER, 
		"readIfExists:tmpl = {0}, timeout = {1}, cookie = {2}",
		new Object[]{tmpl, new Long(timeout), cookie});
	}
	return getMatch(tmpl, txn, timeout, false, true, cookie);
    }

    public Object takeIfExists(EntryRep tmpl, Transaction txn, long timeout,
			       QueryCookie cookie)
	throws TransactionException, RemoteException, InterruptedException
    {
	if (opsLogger.isLoggable(Level.FINER)) {
	    opsLogger.log(Level.FINER, 
		"takeIfExists:tmpl = {0}, timeout = {1}, cookie = {2}",
		new Object[]{tmpl, new Long(timeout), cookie});
	}
	return getMatch(tmpl, txn, timeout, true, true, cookie);
    }

    public Object take(EntryRep[] tmpls, Transaction tr, long timeout,
		       int limit, QueryCookie queryCookieFromClient)
	throws TransactionException, RemoteException
    {
	if (opsLogger.isLoggable(Level.FINER)) {
	    opsLogger.log(Level.FINER, 
		"take<multiple>:timeout = {1}, limit{2} = cookie = {3}",
		new Object[]{new Long(timeout), new Integer(limit), 
			     queryCookieFromClient});
	}
	
	checkForEmpty(tmpls, "Must provide at least one template");

	for (int i=0; i<tmpls.length; i++) {
	    typeCheck(tmpls[i]);
	    if (tmpls[i] == null)
		tmpls[i] = EntryRep.matchAnyEntryRep();
	}

	checkLimit(limit);
	checkTimeout(timeout);

	ServerTransaction str = serverTransaction(tr);
	Txn txn = enterTxn(tr);

	/* Don't bother if transaction is not active,
	 * synchronize so we get the most recent answer,
	 * not because we need to make anything atomic.
	 */
	if (txn != null) {
	    synchronized(txn) {
		if (txn.getState() != ACTIVE)
		    throw throwNewCannotJoinException();
	    }
	}

	final long start = System.currentTimeMillis();
	final long endTime;
	if (Long.MAX_VALUE - timeout <= start)
	    endTime = Long.MAX_VALUE;  // If we would overflow, pin to MAX_VALUE
	else
	    endTime = start + timeout;

	final OutriggerQueryCookie queryCookie;
	if (queryCookieFromClient == null || 
	    !(queryCookieFromClient instanceof OutriggerQueryCookie))
	{
	    queryCookie = new OutriggerQueryCookie(start);
	} else {
	    queryCookie = (OutriggerQueryCookie)queryCookieFromClient;
	}

	/* This plugs a hole -- if an entry is written while we are
	 * scanning the lists, it may arrive after we have passed that
	 * point in the list.  By capturing the last current element
	 * operationJournal and searching forward from their after we
	 * do the first traversal we can make sure that we get any
	 * entries the initial search missed
	 */
	final OperationJournal.TransitionIterator transitionIterator =
	    operationJournal.newTransitionIterator();

	/* First we do a straight search of the entries currently in the space */
	   
	// Set of classes we need to search
	final Set classes = new java.util.HashSet();
	for (int i=0; i<tmpls.length; i++) {
	    final String whichClass = tmpls[i].classFor();
	    final Iterator subtypes = types.subTypes(whichClass);
	    while (subtypes.hasNext()) {
		classes.add(subtypes.next());
	    }		
	}
	
	limit = Math.min(limit, takeLimit);
	EntryHandle[] handles = new EntryHandle[limit];
	int found = 0;
	final Set conflictSet = new java.util.HashSet();
	final WeakHashMap provisionallyRemovedEntrySet = new WeakHashMap();

	for (Iterator i=classes.iterator(); 
	     i.hasNext() && found < handles.length;) 
        {
	    final String clazz = (String)i.next();
	    final EntryHolder.ContinuingQuery query = 
		createQuery(tmpls, clazz, txn, true, start);

	    if (query == null)
		continue;

	    while (found < handles.length) {
		final EntryHandle handle = 
		    query.next(conflictSet, null, provisionallyRemovedEntrySet);
		if (handle == null) 
		    break;
		handles[found++] = handle;
	    }
	}

	if (found > 0) {
	    // We have something to return
	    return completeTake(handles, found, txn);
	}

	final long time = System.currentTimeMillis();

	if (time >= endTime) {
	    /* Even if this query won't block, check up on any
	     * txns which prevented this query from returning reps
	     */
	    monitor(conflictSet);

	    // need to return `null', but make sure conflicting takes have
	    // completed
	    try {
		waitOnProvisionallyRemovedEntries(provisionallyRemovedEntrySet);
	    } catch (InterruptedException e) {
		// should never happen
		throw new AssertionError(e);
	    }

	    return queryCookie;
	}

	/* Now we look through the transitions that have occurred
	 * since we started.
	 *
	 * First register a watcher.
	 */
	final long startOrdinal = 
	    transitionIterator.currentOrdinalAtCreation();
	final TakeMultipleWatcher watcher = new TakeMultipleWatcher(limit, endTime,
            queryCookie.startTime, startOrdinal, provisionallyRemovedEntrySet, 
	    txn);    

	/* If this query is under a transaction, make sure it still
	 * active and add the watcher to the Txn. Do this before
	 * anything else in case the transaction is no longer active.
	 */
	if (txn != null) {
	    synchronized(txn) {
		if (txn.getState() != ACTIVE)
		    throw throwNewCannotJoinException();

		txn.add((Transactable)watcher);
	    }
	}

	monitor(watcher, conflictSet);
	for (int i=0; i<tmpls.length; i++) {
	    templates.add(watcher, tmpls[i]);
	}

	/* Now we need to search the operation journal for all
	 * the transitions that were added after we started
	 * our initial search and that were processed before
	 * we added our watcher to templates.
	 */
	transitionIterator.watcherRegistered();

    transitions:
	for (EntryTransition i = transitionIterator.next(); 
	     i != null; 
	     i = transitionIterator.next()) 
	{
	    final EntryRep rep = i.getHandle().rep();

	    for (int j=0; j<tmpls.length; j++) {
		final EntryRep tmpl = tmpls[j];
		if (rep.isAtLeastA(tmpl.classFor()) && tmpl.matches(rep)) {
		    // Match! - Need to process
		    if (watcher.catchUp(i, time)) {
			break transitions;
		    }
		}
	    }
	}


	try {
	    watcher.waitOnResolution();
	} catch (InterruptedException e) {
	    // should never happen
	    throw new AssertionError(e);
	}

	handles = watcher.resolvedWithEntries();
	if (handles != null) { // got some
	    return completeTake(handles, handles.length, txn);
	}

	final Throwable t = watcher.resolvedWithThrowable();
	if (t != null) {
	    if (opsLogger.isLoggable(Levels.FAILED))
		opsLogger.log(Levels.FAILED, t.getMessage(), t);

	    if (t instanceof RemoteException)
		throw (RemoteException)t;

	    if (t instanceof TransactionException)
		throw (TransactionException)t;

	    if (t instanceof RuntimeException)
		throw (RuntimeException)t;

	    if (t instanceof Error)
		throw (Error)t;

	    throw new InternalSpaceException(
	        "Query threw unexpected exception", t);	    
	}

	/* Before returning nothing, make sure all pending removals
	 * that we conflicted with have been logged.
	 */
	try {
	    waitOnProvisionallyRemovedEntries(provisionallyRemovedEntrySet);
	} catch (InterruptedException e) {
	    // should never happen
	    throw new AssertionError(e);
	}

	return queryCookie;
    }

    private EntryRep[] completeTake(EntryHandle[] handles, int found, Txn txn) 
        throws TransactionException
    {
	final EntryRep[] reps = new EntryRep[found];

	if (log == null) {
	    for (int i=0; i<found; i++) {
		reps[i] = handles[i].rep();
	    }
	} else {
	    final Uuid[] uuids = new Uuid[found];
	    for (int i=0; i<found; i++) {
		final EntryRep rep = handles[i].rep();
		reps[i] = rep;
		uuids[i] = rep.id();
	    }

	    if (txn == null) {
		log.takeOp(uuids, null);
	    } else {
		/* Make sure the txn is still active, we don't want
		 * to write a transactional take recored after 
		 * writing out the prepare record.
		 */
		try {
		    txn.ensureActive();
		    log.takeOp(uuids, txn.getId());
		} finally {
		    txn.allowStateChange();
		}
	    }
	}

	// Now that takes have been committed to disk, let other queries go
	if (txn == null) {
	    for (int i=0; i<found; i++) {
		synchronized (handles[i]) {
		    contents.remove(handles[i]);
		}
	    }
	}

	return reps;
    }

    private EntryRep completeTake(EntryHandle handle, Txn txn) 
        throws TransactionException
    {
	final EntryRep rep = handle.rep();

	if (log != null) {
	    if (txn == null) {
		log.takeOp(rep.id(), null);
	    } else {
		/* Make sure the txn is still active, we don't want
		 * to write a transactional take recored after 
		 * writing out the prepare record.
		 */
		try {
		    txn.ensureActive();
		    log.takeOp(rep.id(), txn.getId());
		} finally {
		    txn.allowStateChange();
		}
	    }
	}

	// Now that takes have been committed to disk, let other queries go
	if (txn == null) {
	    synchronized (handle) {
		contents.remove(handle);
	    }
	}

	return rep;
    }

    /**
     * Crerate a ContinuingQuery for the holder of the specified class.
     */
    private EntryHolder.ContinuingQuery createQuery(EntryRep[] tmpls,
	String clazz, Txn txn, boolean takeIt, long now) 
    {
	final EntryHolder holder = contents.holderFor(clazz);
	final String[] supertypes = holder.supertypes();

	if (supertypes == null)
	    return null;

	final List tmplsToCheck = new java.util.LinkedList();	
	for (int i=0; i<tmpls.length; i++) {
	    final EntryRep tmpl = tmpls[i];
	    final String tmplClass = tmpl.classFor();
	    if (tmplClass.equals(clazz) || tmpl == EntryRep.matchAnyEntryRep()) {
		tmplsToCheck.add(tmpl);
	    } else {
		for (int j=0; j<supertypes.length; j++) {
		    if (tmplClass.equals(supertypes[j])) {
			tmplsToCheck.add(tmpl);
			break;
		    }
		}						
	    }
	}

	return holder.continuingQuery(
            (EntryRep[])tmplsToCheck.toArray(new EntryRep[tmplsToCheck.size()]),
	    txn, takeIt, now);				       
    }

		
    /**
     * Call <code>waitOnCompleteRemoval</code> on each of the EntryHandles
     * in the passed set.
     */
    private static void waitOnProvisionallyRemovedEntries(
	    WeakHashMap provisionallyRemovedEntrySet) 
	throws InterruptedException
    {
	if (provisionallyRemovedEntrySet.isEmpty())
	    return;
	
	final Set keys = provisionallyRemovedEntrySet.keySet();

	for (Iterator i=keys.iterator(); i.hasNext();) {
	    final EntryHandle handle = (EntryHandle)i.next();
	    if (handle == null)
		continue;
	    synchronized (handle) {
		handle.waitOnCompleteRemoval();
	    }
	}
    }


    /**
     * Do the heavy lifting for queries.  Find a match, 
     * optionally taking it, blocking as appropriate
     * if a match can't be initially found.
     * @param tmpl The template for the query, may be <code>null</code>
     *             if all entries match.
     * @param tr   The transaction the query is being performed under,
     *             or <code>null</code> if there is no transaction.
     * @param timeout Maxium time to block in milliseconds.
     * @param takeIt <code>true</code> if the entry found is
     *             to be removed.
     * @param ifExists <code>true</code> if this query is to follow
     *             the rules for ifExists queries.
     * @param queryCookieFromClient If this call is a continuation of 
     *             an earlier query, the cookie from the 
     *             last sub-query.
     * @throws RemoteException if a network failure occurs.
     * @throws TransactionException if there is a problem
     *         with the specified transaction such as 
     *         it can not be joined, or leaves the active
     *         state before the call is complete.
     * @throws InterruptedException if the thread in the server
     *         is interrupted before the query can be completed.
     * @throws SecurityException if the server decides
     *         the caller has insufficient privilege to carry
     *         out the operation.
     * @throws IllegalArgumentException if a negative timeout value is used
     * @throws InternalSpaceException if there is an internal problem
     *         with the server.  
     */
    private Object
        getMatch(EntryRep tmpl, Transaction tr, long timeout, boolean takeIt,
		 boolean ifExists, QueryCookie queryCookieFromClient)
	throws RemoteException, InterruptedException, TransactionException
    {
	typeCheck(tmpl);
	checkTimeout(timeout);

	// after this time, can stop looking
	final long startTime = System.currentTimeMillis();
	final long endTime;		       // when should we stop?
	if (Long.MAX_VALUE - timeout <= startTime) 
	    endTime = Long.MAX_VALUE;  // If we would overflow, pin to MAX_VALUE
	else
	    endTime = startTime + timeout;

	// We get the txn object here, but we'll not check the validity
	// of the transaction state until later...

        Txn txn = enterTxn(tr);

	// Take a quick peek to see if the transaction has already closed.
	// If it has, then bail. NB: We must do this check again when we've
	// found a candidate match (much deeper in the code).

	if (txn != null) {
	    synchronized(txn) {
		if (txn.getState() != ACTIVE)
		    throw throwNewCannotJoinException();
	    }
	}

	/* This plugs a hole -- if an entry is written while getMatch
	 * is scanning the list, it may arrive after getMatch has
	 * passed that point in the list.  By capturing the last
	 * current element operationJournal and searching forward from
	 * their after we do the first traversal we can make sure that 
	 * we get any entries the initial search missed
	 */
	final OperationJournal.TransitionIterator transitionIterator =
	    operationJournal.newTransitionIterator();

	// We use a distinguished EntryRep for the null template
	if (tmpl == null)
	    tmpl = EntryRep.matchAnyEntryRep();

	EntryHandle handle = null;
	final Set conflictSet = new java.util.HashSet();
	final Set lockedEntrySet = 
	    (ifExists?new java.util.HashSet():null);
	final WeakHashMap provisionallyRemovedEntrySet = 
	    new java.util.WeakHashMap();

	/*
	 * First we do the straight search
	 */
	handle = find(tmpl, txn, takeIt, conflictSet, lockedEntrySet,
		      provisionallyRemovedEntrySet);
	opsLogger.log(Level.FINEST, "getMatch, initial search found {0}", handle);

	if (handle != null) {	// found it
	    if (takeIt)
		return completeTake(handle, txn);
	    else 
		return handle.rep();
	}

	if (opsLogger.isLoggable(Level.FINEST)) {
	    opsLogger.log(Level.FINEST, "{0} conflicts, endTime = {1}",
			  new Object[] {new Integer(conflictSet.size()),
					new Long(endTime)});
	}

	final OutriggerQueryCookie queryCookie;
	if (queryCookieFromClient == null || 
	    !(queryCookieFromClient instanceof OutriggerQueryCookie))
	{
	    queryCookie = new OutriggerQueryCookie(startTime);
	} else {
	    queryCookie = (OutriggerQueryCookie)queryCookieFromClient;
	}
	    

	final long time = System.currentTimeMillis();
	if (time >= endTime) {
	    /* Even if this query won't block, check up on any
	     * txns which prevented this query from returning a rep
	     */
	    monitor(conflictSet);
	    // Make sure the removal of any provisionally removed
	    // entries are committed to disk before failing the query
	    waitOnProvisionallyRemovedEntries(provisionallyRemovedEntrySet);
	    return queryCookie;
	}

	/* Even if there are no conflicts and this is an ifExists
	 * query we still need to go through the transitions that
	 * occurred after the initial search started.  This is because
	 * we allow transitions to occur during the initial search and
	 * because the initial search is does not process the current
	 * state in the order it was created. For example there could
	 * be a match in holder 2, but we search on holder 1 first
	 * where there is no match.  before we get to holder 2 a match
	 * is added to holder 1 and the matching entry is removed from
	 * holder 2. During this time there was always at least one
	 * match in the space, but this initial search will turn up 0.
	 * 
	 * By waiting until we go through the transition queue before
	 * deciding there are no matches we know that any match that
	 * was in the space when started the search, removed before we
	 * got to it and was replaced we will see the
	 * replacement. Once we get caught up we process the transitions
	 * in their natural order.
	 */

	/* Now we look through the transitions that have occurred
	 * since we started.
	 *
	 * First register a watcher.
	 */
	final SingletonQueryWatcher watcher;
	final long startOrdinal = 
	    transitionIterator.currentOrdinalAtCreation();
	if (!ifExists && !takeIt && txn == null) {
	    watcher = 
		new ReadWatcher(endTime, queryCookie.startTime, 
				startOrdinal); 
	} else if (ifExists && !takeIt) {
	    if (txn == null) {
		watcher = new ReadIfExistsWatcher(endTime, queryCookie.startTime,
						  startOrdinal, lockedEntrySet);
	    } else {
		watcher = new TransactableReadIfExistsWatcher(endTime, 
                    queryCookie.startTime, startOrdinal, lockedEntrySet,
                    provisionallyRemovedEntrySet, txn);
	    }		
	} else if (!ifExists && (takeIt || txn != null)) {
	    watcher = new ConsumingWatcher(endTime, queryCookie.startTime,
		startOrdinal, provisionallyRemovedEntrySet, txn, takeIt);
	} else if (ifExists && takeIt) {
	    watcher = new TakeIfExistsWatcher(endTime, 
		queryCookie.startTime, startOrdinal, lockedEntrySet, 
		provisionallyRemovedEntrySet, txn);
	} else {
	    throw new AssertionError("Can't create watcher for query");
	}

	/* If this query is under a transaction, make sure it still
	 * active and add the watcher to the Txn. Do this before
	 * anything else in case the transaction is no longer active.
	 */
	if (txn != null) {
	    synchronized(txn) {
		if (txn.getState() != ACTIVE)
		    throw throwNewCannotJoinException();

		txn.add((Transactable)watcher);
	    }
	}

	monitor(watcher, conflictSet);

	templates.add(watcher, tmpl);

	/* Now we need to search the operation journal for all
	 * the transitions that were added after we started
	 * our initial search and that were processed before
	 * we added our watcher to templates.
	 */
	transitionIterator.watcherRegistered();
	final String tmplClass = tmpl.classFor();

	for (EntryTransition i = transitionIterator.next(); 
	     i != null; 
	     i = transitionIterator.next()) 
	{
	    final EntryRep rep = i.getHandle().rep();
	    if (rep.isAtLeastA(tmplClass) && tmpl.matches(rep)) {
		// Match! - Need to process
		if (watcher.catchUp(i, time)) {
		    break;
		}
	    }
	}
	
	/* If this is an ifExists query need to mark in the journal
	 * that once the current tail of journal is processed an empty
	 * locked entry set means we should return.  
	 */
	if (ifExists)
	    operationJournal.markCaughtUp((IfExistsWatcher)watcher);
	    
	watcher.waitOnResolution();
	handle = watcher.resolvedWithEntry();
	if (handle != null) { // got one
	    if (takeIt) 
		return completeTake(handle, txn);
	    else
		return handle.rep();
	}

	final Throwable t = watcher.resolvedWithThrowable();
	if (t != null) {
	    if (opsLogger.isLoggable(Levels.FAILED))
		opsLogger.log(Levels.FAILED, t.getMessage(), t);

	    if (t instanceof RemoteException)
		throw (RemoteException)t;

	    if (t instanceof InterruptedException)
		throw (InterruptedException)t;		

	    if (t instanceof TransactionException)
		throw (TransactionException)t;

	    if (t instanceof RuntimeException)
		throw (RuntimeException)t;

	    if (t instanceof Error)
		throw (Error)t;

	    throw new InternalSpaceException(
	        "Query threw unexpected exception", t);	    
	}

	// Before returning nothing, make sure all pending removal
	// have been logged.
	waitOnProvisionallyRemovedEntries(provisionallyRemovedEntrySet);
	if (ifExists && ((IfExistsWatcher)watcher).isLockedEntrySetEmpty())
	    return null;

	return queryCookie;
    }

    /**
     * Make sure the transactions listed here are monitored for as
     * long as the given query exists.
     */
    private void monitor(QueryWatcher watcher, Collection toMonitor) {
	if (!toMonitor.isEmpty())	    
	    txnMonitor.add(watcher, toMonitor);
    }

    /**
     * Make sure the transactions listed here are monitored for
     * a reasonable amount of time since they recently caused a conflict,
     * although for a non-leased event.
     */
    void monitor(Collection toMonitor) {
	if (!toMonitor.isEmpty())	    
	    txnMonitor.add(toMonitor);
    }

    /**
     * Debug method: Dump out the bucket for the holder for the given
     * rep.
     */
    void dump(String name, EntryRep rep) {
	dump(contents.holderFor(rep), name, rep);
    }

    /**
     * Debug method:  Dump out the bucket for the given holder, for an
     * operation using the given rep.
     */
    static void dump(EntryHolder holder, String name, EntryRep rep) {
	try {
	    holder.dump(name + " " + rep.entry());
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    /**
     * Find an entry that is at least <code>whichClass</code> that
     * matches the template <code>tmplRep</code> (at least the
     * template's type, and matches in values provided).
     * <code>whichClass</code> is at least the type of the template.
     * If <code>takeIt</code> is <code>true</code> the matching entry
     * is removed (perhaps provisionally).
     */
    private EntryHandle
	find(EntryRep tmplRep, Txn txn, boolean takeIt, Set conflictSet, 
	     Set lockedEntrySet, WeakHashMap provisionallyRemovedEntrySet)
	throws TransactionException
    {
	final String whichClass = tmplRep.classFor();

	/*
	 * The iterator returned by subTypes includes both this class
	 * and all subtypes.
	 */
	Iterator subtypes = types.subTypes(whichClass);
	String className = null;
	EntryHandle result = null;
	boolean foundConflicts = false;
	EntryHolder holder = null;

	while (subtypes.hasNext()) {
	    className = (String) subtypes.next();
	    opsLogger.log(Level.FINEST, 
	        "OutriggerServerImpl: find: className = {0}", className);

	    holder = contents.holderFor(className);
	    result = holder.hasMatch(tmplRep, txn, takeIt, conflictSet,
				     lockedEntrySet, provisionallyRemovedEntrySet);
	    if (result != null) {
		return result;
	    }
	}

	// no result found
	return null;
    }
    
    /**
     * Generate a new ID. IDs are generated from a
     * <code>SecureRandom</code> object so that it is next to
     * impossible to forge an ID and so we don't have
     * to remember/restore a counter cross restarts.
     */
    static long nextID() {
	return idGen.nextLong();
    }

    // ------------------------------------------------------------
    // Contents method and related classes 
    // ------------------------------------------------------------
    public MatchSetData contents(EntryRep[] tmpls, Transaction tr,
				 long leaseTime, long limit)
        throws TransactionException, RemoteException
    {
	if (opsLogger.isLoggable(Level.FINER)) {
	     opsLogger.log(Level.FINER, 
		"contents:tmpls = {0}, tr = {1}, leaseTime = {2}, " +
		"limit = {3}", 
	        new Object[]{tmpls, tr, new Long(leaseTime), new Long(limit)});
	}

	checkForEmpty(tmpls, "Must provide at least one template");
	checkLimit(limit);

	for (int i=0; i<tmpls.length; i++) {
	    typeCheck(tmpls[i]);
	    if (tmpls[i] == null)
		tmpls[i] = EntryRep.matchAnyEntryRep();
	}

	/* Explicit check of leastTime, contentsLeasePolicy.grant
	 * won't check for leaseTime == 0, and we don't call grant at
	 * all if we decide we don't need a lease.
	 */
	if (leaseTime < 1 && leaseTime != Lease.ANY) {
	    throw logAndThrowIllegalArg(
		 "leaseTime = " + leaseTime + ", must be postive or Lease.ANY");
	}

	ServerTransaction str = serverTransaction(tr);
	Txn txn = enterTxn(tr);

	/* Don't bother if transaction is not active,
	 * synchronize so we get the most recent answer,
	 * not because we need to make anything atomic.
	 */
	if (txn != null) {
	    synchronized(txn) {
		if (txn.getState() != ACTIVE)
		    throw throwNewCannotJoinException();
	    }
	}

	final Uuid uuid = UuidFactory.generate();
	final ContentsQuery contentsQuery = new ContentsQuery(uuid, tmpls, 
							      txn, limit);
	final EntryRep[] reps = contentsQuery.nextBatch(null, 
   	    System.currentTimeMillis());

	if (reps[reps.length-1] == null) {
	    // Entire iteration in first batch - no record, no lease
	    return new MatchSetData(null, reps, -1);
	}

	// Need a lease and need to remember query
	final LeasePeriodPolicy.Result r = 
	    grant(contentsQuery, leaseTime, contentsLeasePolicy, 
		  "contentsLeasePolicy");

	contentsQueries.put(uuid, contentsQuery);
	return new MatchSetData(uuid, reps, r.duration);
    }

    public EntryRep[] nextBatch(Uuid contentsQueryUuid, Uuid entryUuid)
        throws NoSuchObjectException
    {
	opsLogger.entering("OutriggerServerImpl", "nextBatch");
	final ContentsQuery contentsQuery = 
	    (ContentsQuery)contentsQueries.get(contentsQueryUuid);
	if (contentsQuery == null)
	    throw throwNewNoSuchObjectException("Unkown MatchSet", opsLogger);

	final long now = System.currentTimeMillis();
	synchronized (contentsQuery) {
	    if (contentsQuery.getExpiration() <= now) {
		contentsQuery.cancel();
		throwNewNoSuchObjectException("Contents query expired", opsLogger);
	    }
	}
	 
	try {
	    return contentsQuery.nextBatch(entryUuid, now);
	} catch (TransactionException e) {
	    synchronized (contentsQuery) {
		contentsQuery.cancel();
	    }
	    throw throwNewNoSuchObjectException("Transaction no longer active",
						e, opsLogger);
	}
    }

    /**
     * Object that keeps the current state of contents queries. Holds
     * the expiration time as well as the current state of the iteration.
     */
    private class ContentsQuery implements LeasedResource {
	/** <code>Uuid</code> associated with this query and its lease */
	final private Uuid uuid;

	/** The <code>Set</code> of classes we need to search */
	final private Set classes = new java.util.HashSet();

	/** An iteration into <code>classes</code> */
	final private Iterator classesIterator;

	/** The complete set of templates */
	final private EntryRep[] tmpls;

	/** The transaction the query is being performed under */
	final private Txn txn;

	/** Lock to prevent concurrent calls to <code>nextBatch</code> */
	final private Object lock = new Object();

	/** The current expiration time */
	private long expiration;
	
	/** The current <code>ContinuingQuery</code> */
	private EntryHolder.ContinuingQuery currentQuery;

	/** 
	 * Number of elements left to return before reaching the 
	 * the client specified limit
	 */
	private long remaining;

	/** Uuid of the last entry returned */
	private Uuid lastEntry;

	/** Last batch of entries returned */
	private EntryRep[] lastBatch;

	/**
	 * Set of entries that we have encountered that have been
	 * provisionally removed
	 */
	final private WeakHashMap provisionallyRemovedEntrySet
	    = new WeakHashMap();

	private ContentsQuery(Uuid uuid, EntryRep[] tmpls, Txn txn, long limit) {
	    this.uuid = uuid;
	    this.tmpls = tmpls;
	    this.txn = txn;
	    remaining = limit;

	    for (int i=0; i<tmpls.length; i++) {
		final String whichClass = tmpls[i].classFor();
		final Iterator subtypes = types.subTypes(whichClass);
		while (subtypes.hasNext()) {
		    classes.add(subtypes.next());
		}		
	    }

	    classesIterator = classes.iterator();
	}
	
	private boolean advanceCurrentQuery(long now) {
	    while (classesIterator.hasNext()) {
		currentQuery = createQuery(tmpls, (String)classesIterator.next(),
					   txn, false, now);
		if (currentQuery == null)
		    continue;

		return true;
	    }

	    return false; // done - no entries left
	}
	
	private EntryRep[] nextBatch(Uuid lastReceived, long now)
	    throws TransactionException 
	{
	    synchronized (lock) {
		if (lastReceived != null && !lastReceived.equals(lastEntry)) {
		    // Last batch must have been lost, send it again
		    return lastBatch;
		}

		// Get a currentQuery if we don't have one and restart it if we do.
		if (currentQuery == null) {
		    if (!advanceCurrentQuery(now)) {
			// Make sure the removal of any provisionally removed
			// entries are committed to disk before signaling
			// we are done.
			try {
			    waitOnProvisionallyRemovedEntries(
				provisionallyRemovedEntrySet);
			} catch (InterruptedException e) {
			    // should never happen
			    throw new AssertionError(e);
			}
			return new EntryRep[1];
		    }
		} else {
		    currentQuery.restart(now);
		}

		final Set conflictSet = new java.util.HashSet();		

		lastBatch = new EntryRep[nextLimit];
		int i = 0;
		while (remaining > 0 && i < lastBatch.length) {
		    final EntryHandle handle = 
			currentQuery.next(conflictSet, null, 
					  provisionallyRemovedEntrySet);
		    
		    if (handle == null) {
			if (advanceCurrentQuery(now)) {
			    // Don't add null to lastBatch...
			    continue;
			} else {
			    // done, but need to return current set. 
			    // Clear currentQuery so next call will return null
			    currentQuery = null;
			    break;
			}
		    }

		    lastBatch[i] = handle.rep();
		    i++;
		    remaining--;		    
		}

		monitor(conflictSet);
		if (i == 0) {
		    // special case of nothing left clear lastEntry
		    // currentQuery==null test will catch end condition
		    lastEntry = null;
		} else {
		    lastEntry = lastBatch[i-1].id();
		}
		return lastBatch;		    
	    }
	}

	/** 
	 * Remove this query, assumes caller holds lock on this
	 * object. Returns <code>true</code> if query has not
	 * been already removed.
	 */
	private boolean cancel() {
	    if (contentsQueries.remove(uuid) == null)
		return false;

	    expiration = Long.MIN_VALUE;	   	    
	    return true;
	}

	public void setExpiration(long newExpiration) {
	    expiration = newExpiration;
	}

	public long getExpiration() {
	    return expiration;
	}

	public Uuid getCookie() {
	    return uuid;
	}
    }

    // ------------------------------------------------------------
    //  ServiceProxyAccessor implementation
    // ------------------------------------------------------------
    public Object getServiceProxy() {
	opsLogger.entering("OutriggerServerImpl", "getServiceProxy");
	return spaceProxy;
    }

    synchronized Object getProxy() {
	return ourRemoteRef;
    }

    // ------------------------------------------------------------
    //			Admin stuff
    // ------------------------------------------------------------

    /**
     * Return a proxy that implements that <code>JavaSpaceAdmin</code>
     * interface.
     *
     * @see JavaSpaceAdmin
     */
    public Object getAdmin() {
	opsLogger.entering("OutriggerServerImpl", "getAdmin");
	return adminProxy;
    }

    // purposefully inherit doc comment
    // Implementation of the OutriggerAdmin interface
    public JavaSpace space() {
	opsLogger.entering("OutriggerServerImpl", "space");
	return spaceProxy;
    }

    // purposefully inherit doc comment
    public Uuid contents(EntryRep tmpl, Transaction tr)
	throws TransactionException, RemoteException
    {
	iteratorLogger.entering("OutriggerServerImpl", "contents");

	typeCheck(tmpl);
	ServerTransaction str = serverTransaction(tr);

	Txn txn = enterTxn(tr);

	/* Don't bother if transaction is not active,
	 * synchronize so we get the most recent answer,
	 * not because we need to make anything atomic.
	 */
	if (txn != null) {
	    synchronized(txn) {
		if (txn.getState() != ACTIVE)
		    throw throwNewCannotJoinException();
	    }
	}

	final Uuid uuid = UuidFactory.generate();
	iterations.put(uuid, new IteratorImpl(tmpl, txn));
	return uuid;
    }

    public EntryRep[] nextReps(Uuid iterationUuid, int max, 
			       Uuid entryUuid) 
	throws NoSuchObjectException 
    {
	iteratorLogger.entering("OutriggerServerImpl", "nextReps");

	final IteratorImpl iterImpl = 
	    (IteratorImpl)iterations.get(iterationUuid);
	if (iterImpl == null)
	    throw throwNewNoSuchObjectException("Unknown iteration", 
						iteratorLogger);

	return iterImpl.nextReps(max, entryUuid);
    }

    public void delete(Uuid iterationUuid, Uuid entryUuid) 
	throws NoSuchObjectException 
    {
	iteratorLogger.entering("OutriggerServerImpl", "delete");

	final IteratorImpl iterImpl = 
	    (IteratorImpl)iterations.get(iterationUuid);
	if (iterImpl == null)
 	    throw throwNewNoSuchObjectException("Unknown iteration", 
						iteratorLogger);

	iterImpl.delete(entryUuid);
    }

    public void close(Uuid iterationUuid) throws NoSuchObjectException {
	iteratorLogger.entering("OutriggerServerImpl", "close");

	final IteratorImpl iterImpl = 
	    (IteratorImpl)iterations.remove(iterationUuid);
	if (iterImpl == null)
	    throw throwNewNoSuchObjectException("Unknown iteration", 
						iteratorLogger);

	iterImpl.close();
    }

    /**
     * Destroy this space, exiting when finished.  This unregisters
     * itself and related objects, and then destroys the persistent
     * state.
     */
    public void destroy() {
	iteratorLogger.entering("OutriggerServerImpl", "destroy");

	serverGate.rejectCalls(
	    new NoSuchObjectException("Service is destroyed"));
	(new DestroyThread()).start();
	lifecycleLogger.log(Level.INFO, 
	    "Outrigger server destroy thread started: {0}", this);
    }

    /**
     * Termination thread code.  We do this in a separate thread
     * so the destroy call has a chance to return normally.
     */
    private class DestroyThread extends Thread {

        /** Create a non-daemon thread */
        public DestroyThread() {
            super("DestroyThread");
            /* override inheritance from RMI daemon thread */
            setDaemon(false);
        }

        public void run() {
	    lifecycleLogger.log(Level.FINE, 
		"Outrigger server destroy thread running: {0}", this);

	    // Do this early so people will stop finding us.

	    try {
		logDestroyPhase("destroying JoinManager");
		joinStateManager.destroy();
	    } catch (Exception t) {
		logDestroyProblem("destroying JoinManager", t);
	    }	

	    // Want to unregister before unexporting so a call can't
	    // sneak in and re-activate us
	    if (activationID != null) {  // In an activation group
		try {
		    //shared VM -- just unregister this object
		    logDestroyPhase("unregistering object");
		    activationSystem.unregisterObject(activationID);
		} catch (Exception t) {
		    logDestroyProblem("unregistering server", t);
		}
	    }
	
	    // Attempt to unexport this object -- nicely first
	    logDestroyPhase("unexporting force = false");
	    long now = System.currentTimeMillis();
	    long end_time = now + maxUnexportDelay;
	    if (end_time < 0) {
		// overflow
		end_time = Long.MAX_VALUE;
	    }
	    
	    boolean unexported = false;
	    try {
		while ((!unexported) && (now < end_time)) {
		    /* wait for any pending operations to complete */
		    unexported = exporter.unexport(false);
		
		    if (!unexported) {
			try {
			    /* Sleep for a finite time instead of yield.
			     * In most VMs yield is a no-op so if 
			     * unexport(false) is not working (say because
			     * there is a blocking query in progress) a
			     * yield here results in a very tight loop
			     * (plus there may be no other runnable threads)
			     */
			    final long sleepTime = 
				Math.min(unexportRetryDelay, end_time - now);

			    /* sleepTime must > 0, unexportRetryDelay is
			     * > 0 and if now >= end_time we would have
			     * fallen out of the loop
			     */
			    sleep(sleepTime);
			    now = System.currentTimeMillis();
			} catch (InterruptedException e) {
			    // should never happen, but if it does break
			    // and fall through to force = true case
			    logDestroyProblem("unexport retry delay sleep", e);
			    break;
			}
		    }
		}
	    } catch (Throwable t) {
		logDestroyProblem(
		    "trying \"nice\" unexport, will try forceful unexport", t);
	    }

	    // Attempt to forcefully unexport this object, if not
	    // already done. We retry even if the nice attempt
	    // failed with an exception on the assumption that
	    // a diffrent codepath in unexport might get further
	    if (!unexported) {
		/* Attempt to forcefully export the service */
		logDestroyPhase("unexporting force = true");
		try {
		    unexported = exporter.unexport(true);
		} catch (Throwable t) {
		    logDestroyProblem("trying forceful unexport", t);
		}
	    }
	

	    //
	    // Attempt to stop all running threads
	    //
	    try {
		logDestroyPhase("destroying txnMonitor");
	        txnMonitor.destroy();
	    } catch (Exception t) {
		logDestroyProblem("destroying txnMonitor", t);
    	    }
	    
	    try {
		logDestroyPhase("terminating notifier");
		notifier.terminate();
	    } catch (Exception t) {
		logDestroyProblem("terminating notifier ", t);
	    }

	    logDestroyPhase("terminating operation journal");
	    operationJournal.terminate();

	    destroyReaper(templateReaperThread);
	    destroyReaper(entryReaperThread);
	    destroyReaper(contentsQueryReaperThread);

	    if (expirationOpQueue != null) {
		logDestroyPhase("terminating expiration op queue");
		expirationOpQueue.terminate();
	    }

	    try {
		logDestroyPhase("joining operation journal");
		operationJournal.join();
	    } catch (InterruptedException ie) {
		logDestroyProblem("joining operation journal", ie);
	    }
	
	    joinThread(operationJournal);
	    joinThread(templateReaperThread);
	    joinThread(entryReaperThread);
	    joinThread(contentsQueryReaperThread);

	    if (expirationOpQueue != null) {
		joinThread(expirationOpQueue);
	    }

	    if (store != null) {
		try {
		    logDestroyPhase("destroying store");
		    store.destroy();
		} catch (Exception t) {
		    logDestroyProblem("destroying store", t);
		}
	    }

            if (activationID != null) {         
		logDestroyPhase("calling ActivationGroup.inactive");
                try {
                    ActivationGroup.inactive(activationID, exporter);
                } catch (RemoteException e) {
		    logDestroyProblem("calling ActivationGroup.inactive", e);
                } catch (ActivationException e) {
		    logDestroyProblem("calling ActivationGroup.inactive", e);
                }	
	    }

	    if (lifeCycle != null) {
		// runtime has a ref to the serverGate, not this
		logDestroyPhase("calling lifeCycle.unregister");
		lifeCycle.unregister(serverGate);
	    }

	    if (loginContext != null) {
		try {
		    logDestroyPhase("logging out");
		    loginContext.logout();
		} catch (Exception e) {
		    logDestroyProblem("logging out", e);
		}
	    }

	    lifecycleLogger.log(Level.INFO, 
	        "Outrigger server destroy thread finished: {0}", this);
        }
    }

    /** Shutdown a reaper as part of a controlled tear-down */
    private void destroyReaper(Reaper r) {
	logDestroyPhase("stopping " + r.getName());
	r.kill();
    }

    /** Join a thread as part of a controlled tear-down */
    private void joinThread(Thread t) {
	try {
	    logDestroyPhase("joining " + t.getName());
	    t.join();
	} catch (InterruptedException ie) {
	    logDestroyProblem("joining " + t.getName(), ie);
	}
    }

    /** log exception encountered in destroy thread */
    private void logDestroyProblem(String part, Throwable t) {
	lifecycleLogger.log(Level.INFO, "exception encountered " + part +
			    ", continuing", t);
    }

    /** log phase of destroy thread */
    private void logDestroyPhase(String part) {
	if (lifecycleLogger.isLoggable(Level.FINER))
	    lifecycleLogger.log(Level.FINER, 
				"outrigger server:" + part + ":" + this);
    }

    // Methods required by JoinAdmin
    // Inherit java doc from super type
    public Entry[] getLookupAttributes() {
	joinLogger.entering("OutriggerServerImpl", "getLookupAttributes");
	return joinStateManager.getLookupAttributes();
    }

    // Inherit java doc from super type
    public void addLookupAttributes(Entry[] attrSets) {
	joinLogger.entering("OutriggerServerImpl", "addLookupAttributes");
	joinStateManager.addLookupAttributes(attrSets);
    }

    // Inherit java doc from super type
    public void modifyLookupAttributes(Entry[] attrSetTemplates, 
				       Entry[] attrSets) 
    {
	joinLogger.entering("OutriggerServerImpl", "modifyLookupAttributes");
	joinStateManager.modifyLookupAttributes(attrSetTemplates, attrSets);
    }
  
    // Inherit java doc from super type
    public String[] getLookupGroups() {
	joinLogger.entering("OutriggerServerImpl", "getLookupGroups");
	return joinStateManager.getLookupGroups();
    }

    // Inherit java doc from super type
    public void addLookupGroups(String[] groups) {
	joinLogger.entering("OutriggerServerImpl", "addLookupGroups");
	joinStateManager.addLookupGroups(groups);
    }

    // Inherit java doc from super type
    public void removeLookupGroups(String[] groups) {
	joinLogger.entering("OutriggerServerImpl", "removeLookupGroups");
	joinStateManager.removeLookupGroups(groups);
    }

    // Inherit java doc from super type
    public void setLookupGroups(String[] groups) {
	joinLogger.entering("OutriggerServerImpl", "setLookupGroups");
	joinStateManager.setLookupGroups(groups);
    }

    // Inherit java doc from super type
    public LookupLocator[] getLookupLocators() {
	joinLogger.entering("OutriggerServerImpl", "getLookupLocators");
	return joinStateManager.getLookupLocators();
    }

    // Inherit java doc from super type
    public void addLookupLocators(LookupLocator[] locators) 
        throws RemoteException
    {
	joinLogger.entering("OutriggerServerImpl", "addLookupLocators");
	joinStateManager.addLookupLocators(locators);
    }

    // Inherit java doc from super type
    public void removeLookupLocators(LookupLocator[] locators) 
        throws RemoteException
    {
	joinLogger.entering("OutriggerServerImpl", "removeLookupLocators");
	joinStateManager.removeLookupLocators(locators);
    }

    // Inherit java doc from super type
    public void setLookupLocators(LookupLocator[] locators) 
        throws RemoteException
    {
	joinLogger.entering("OutriggerServerImpl", "setLookupLocators");
	joinStateManager.setLookupLocators(locators);
    }

    /**
     * An iterator that returns all the reps in the space that are of
     * at least the given class.  It works through the contents of each
     * <code>EntryHolder</code> in turn, exhausting each holder's enumerated
     * contents.
     *
     * @see EntryHolder#contents
     */
    private class AllReps implements RepEnum {
	RepEnum		curEnum;	// current unexhausted enum (or null)
	Stack		toDo;		// classes left to do
	Txn		txn;		// txn under which this is done

	/** Create a new <code>AllReps</code> object for the given class. */
	AllReps(String classFor, Txn txn) {
	    toDo = new Stack();
	    this.txn = txn;
	    setup(classFor);		// start with this class
	}

	/**
	 * Set up the enumerator for the given class, adding any of its
	 * subclasses to the <code>toDo</code> stack.  Only direct subclasses
	 * are added -- the rest will be picked up when the subclasses are
	 * set up.
	 */
	private void setup(String classFor) {
	    if (classFor == null)
		return;

	    /* Get only entries that are the same type (or a subtype)
	     * of the template (types.subTypes() gives us classFor too)
	     * Note, types.subTypes() handles the case of 
	     * classFor being EntryRep.matchAnyClassName()
	     */
	    final Iterator matchingTypes = types.subTypes(classFor);
	    while (matchingTypes.hasNext()) {
		toDo.push((String)matchingTypes.next());
	    }

	    if (!toDo.isEmpty())	     //no holders means nothing to do
	        curEnum = enumFor((String)toDo.pop());
	}

	/** 
	 * Return the <code>RepEnum</code> for the given class.
	 */
	private RepEnum enumFor(String classFor) {
	    EntryHolder holder = contents.holderFor(classFor);
	    return holder.contents(txn);
	}

	// purposefully inherit doc comment
	public EntryRep nextRep() {
	    /*
	     * We loop to handle the case where the list of elements for
	     * a type is empty, which means we have to proceed immediately
	     * onward to the next type.
	     */
	    for (;;) {
		if (curEnum == null) {		     // already exhausted all
		    return null;
		}

		EntryRep rep = curEnum.nextRep();    // get next rep
		if (rep != null) {		     // if there is one
		    return rep;			     // ... just return it
		}

		// current enum is exhausted, try the next
		if (toDo.isEmpty()) {		     // if no more to try
		    curEnum = null;		     // exhausted all
		    return null;
		}

		curEnum = enumFor((String)toDo.pop());	// setup next class
	    }
	}
    }

    /**
     * Implementation of the remote iteration interface.
     */
    private class IteratorImpl {
	/** The template the match */
	private final EntryRep tmpl;

	/** RepEnum for all potential matches */
	private RepEnum repEnum;

	/** <code>true</code> if closed, <code>false</code> otherwise */
	boolean		closed;

	/** 
	 * Last batch of <code>EntryRep</code> objects sent to the
         * client. 
	 */
	private EntryRep lastBatch[] = null;

	/** <code>Uuid</code> of last entry in <code>lastBatch</code>. */
	private Uuid lastId = null;

	/**
	 * Create a <code>RemoteIterImpl</code> object to return all
	 * objects that match the given template.
	 */
	IteratorImpl(EntryRep tmpl, Txn txn) {
	    if (tmpl == null)
		tmpl = EntryRep.matchAnyEntryRep();	      // get all
	    this.tmpl = tmpl;
	    repEnum = new AllReps(tmpl.classFor(), txn);
	}

	/**
	 * Utility to set <code>lastBatch</code> and <code>lastId</code>.
	 */
	private void rememberLast(EntryRep[] newLast) {
	    lastBatch = newLast;
	    if (newLast == null) 
		lastId = null;
	    else
		lastId = lastBatch[lastBatch.length-1].id();
	}
	
	// inherit doc comment
	public EntryRep[] nextReps(int max, Uuid id) {
	    if (closed && id != null && lastId == null)
		// They never got the null
		return null;

	    assertOpen();
	    if (id != null && lastId == null) {
		throw logAndThrow(new InternalSpaceException("First call to " +
		        "RemoteIter.next() should have id == null"),
		     iteratorLogger);
	    }

	    // Did the client get the result of the last call?
	    if (id != null && lastId != null && !id.equals(lastId)) 
		// No, must have been a RemoteException on the 
		// return, re-send the last batch of entries
		return lastBatch;
		
	    /* Otherwise the last call must have gotten through, move
	     * forward.
	     */
	     
	    if (repEnum == null) {		     // already exhausted all
		close();			     // consider it closed
		return null;
	    }

	    if (max <= 0 && max != JavaSpaceAdmin.USE_DEFAULT)
		throw new AssertionError("Invalid iterator proxy");

	    if (max == JavaSpaceAdmin.USE_DEFAULT)
		max = 128;

	    final int limit = Math.min(max, 512);    // no more than 512
	    EntryRep[] reps = new EntryRep[limit];   // assume we'll fill it

	    // look for entries in reps that are at least the right type
	    int i = 0;

	    while (i < reps.length) {
		reps[i] = repEnum.nextRep();

		if (reps[i] == null) {	// reached the end
		    repEnum = null;	// let the GC work -- it may be some
					// time before this object is GCed
		    if (i == 0) {	// found nothing on this round
			close();
			return null;
		    }
		    EntryRep[] r = new EntryRep[i];
		    System.arraycopy(reps, 0, r, 0, r.length);
		    rememberLast(r);
		    return r;
		}

		// if this is a match, add it to the list
		if (tmpl.matches(reps[i])) {
		    i++;
		}
	    }

	    rememberLast(reps);
	    return reps;	// filled up the array -- return it
	}

	/**
	 * Delete the entry of the given class and id.  We use class
	 * to get us to the proper <code>EntryHolder</code> efficiently.
	 */
	public void delete(Uuid id) {
	    assertOpen();
	    try {
		boolean found = false;
		for (int i=0; i<lastBatch.length; i++) {
		    if (lastBatch[i].id().equals(id)) {
			found = true;
			break;
		    }
		}

		if (!found) {
		    throw logAndThrow(
			 new InternalSpaceException("Asked to delete entry " +
			     "not returned by last nextReps() call"),
			 iteratorLogger);
		}

		cancel(id);
	    } catch (UnknownLeaseException e) {
		// doesn't matter if cancel fails, since that means
		// the entry is already gone.
	    }
	}

	/**
	 * Close operations on this iterator.
	 */
	public void close() {
	    closed = true;
	    repEnum = null;
	    rememberLast(null);
	}

	/**
	 * Throw IllegalStateException if operations happen on closed
	 * iterator.
	 */
	private void assertOpen() throws IllegalStateException {
	    if (closed) {
		throw logAndThrow(new IllegalStateException(
		    "closed AdminIterator"), iteratorLogger);
	    }
	}
    }

    // ------------------------------------------------------------
    //			Recover stuff
    // ------------------------------------------------------------

    /**
     * Recover the id from the previous session and determine the new basis
     * value for the seq numbers issued by this session.
     *   
     * The typical usage for this routine is to read from permanent store
     * somehow the previous sessionId (basis) for sequence numbers used
     * by the last invocation of Outrigger. This value is then passed into
     * this routine where a new basis is computed. That newly computed value
     * should then be stored in persistent store so the next time we crash
     * we can bump the value again.
     *   
     * Once the store has recovered the new sessionId should be persisted
     * by calling bootOp(...,sessionId)
     *   
     * @param sessionId Value used by the previous invocation of Outrigger
     *   
     * @see LogOps#bootOp
     */  
    public void recoverSessionId(long sessionId) {
	long    bumpValue = Integer.MAX_VALUE;

	this.sessionId = sessionId + bumpValue;
    }    

    public void recoverJoinState(StoredObject state) throws Exception {
	state.restore(joinStateManager);
    }

    public void recoverWrite(StoredResource entry, Long txnId)
	throws Exception 
    {
	EntryRep rep = new EntryRep();
	Txn txn = getRecoveredTxn(txnId);
	entry.restore(rep);

	/* Restore the type hash map, should never happen, but
	 * will throw exception if we get a hash mismatch
	 */
	typeCheck(rep);

	final EntryHolder holder = contents.holderFor(rep);
	final EntryHandle handle = new EntryHandle(rep, txn, holder);
	addWrittenRep(handle, holder, txn);
    }

    public void recoverTake(Uuid cookie, Long txnId) throws Exception {
	final EntryHandle handle = contents.handleFor(cookie);
	EntryHolder holder = contents.holderFor(handle.rep());
	Txn txn = getRecoveredTxn(txnId);
	holder.recoverTake(handle, txn);
    }

    public void recoverTransaction(Long txnId, StoredObject transaction) 
	throws Exception 
    {
	Txn txn = new Txn(txnId.longValue());
	transaction.restore(txn);

	if (recoveredTxns == null)
	    recoveredTxns = new HashMap();

	recoveredTxns.put(txnId, txn);
    }

    private Txn getRecoveredTxn(Long txnId) {
	if (txnId == null)
	    return null;

	Txn txn;
	if((recoveredTxns == null) || 
	   ((txn = (Txn)recoveredTxns.get(txnId)) == null))
	    throw new InternalSpaceException("recover of write/take with " +
					     "unknown txnId" );
	return txn;
    }

    public void recoverRegister(StoredResource registration, String type,
				StoredObject[] storedTemplates)
	throws Exception 
    {
	final StorableResource reg;
	if (type.equals("StorableEventWatcher")) {
	    assert storedTemplates.length == 1;
	    reg = new StorableEventWatcher(0, operationJournal.currentOrdinal(),
					   getSessionId());
	} else if (type.equals("StorableAvailabilityWatcher")) {
	    reg = new StorableAvailabilityWatcher(0, 
		operationJournal.currentOrdinal(), getSessionId());
	} else {
	    throw new AssertionError("Unknown registration type (" + type +
				     ") while recovering event registration");
	}

	registration.restore(reg);	

	for (int i=0; i<storedTemplates.length; i++) {
	    final EntryRep templ = new EntryRep();
	    storedTemplates[i].restore(templ);
	    templates.add((TransitionWatcher)reg, setupTmpl(templ));
	}

	eventRegistrations.put(reg.getCookie(), reg);
    }

    public void recoverUuid(Uuid uuid) {
	topUuid = uuid;
    }

    // ------------------------------------------------------------
    //                  ServerProxyTrust stuff
    // ------------------------------------------------------------
    public TrustVerifier getProxyVerifier() {
	opsLogger.entering("OutriggerServerImpl", "getProxyVerifier");
	return new ProxyVerifier(ourRemoteRef, topUuid);
    }

    // ------------------------------------------------------------
    //			Transaction stuff
    // ------------------------------------------------------------

    /**
     * This method takes a transactional semantic object
     * <code>baseTr</code> (the transaction object passed to us by the
     * client) and retrieves the associated <code>Txn</code> object
     * (the internal representation for that transaction) from the
     * space's records.  If no <code>Txn</code> object is associated
     * with the given semantic object, a new <code>Txn</code> object is
     * created, associated and recorded.
     */
    private Txn enterTxn(Transaction baseTr) 
	throws TransactionException, RemoteException
    {
	txnLogger.entering("OutriggerServerImpl", "enterTxn");
	if (baseTr == null)
	    return null;

	ServerTransaction tr = serverTransaction(baseTr);
	if (tr.isNested()) {
	    final String msg = "subtransactions not supported";
	    final CannotNestException cne = new CannotNestException(msg); 
	    txnLogger.log(Levels.FAILED, msg, cne); 
	    throw cne;
	}
		

	Txn txn = null;
	try {
	    txn = txnTable.get(tr.mgr, tr.id);
	} catch (IOException e) {
	} catch (ClassNotFoundException e) {
	} catch (SecurityException e) {
	    /* ignore all of these exceptions. These all indicate
	     * that there is at least one broken Txn with the same
	     * id as baseTr. Either these Txns are not associated
	     * with baseTr, in which case joining baseTr is fine, or
	     * one of them is associated with baseTr, in which case
	     * baseTr must be not be active (because only inactive
	     * Txn objects can be broken) and the join bellow will fail
	     */
	}

	/*
	 * If we find the txn with the probe, we're all done. Just return.
	 * Otherwise, we need to join the transaction. Of course, there
	 * is a race condition here. While we are attempting to join,
	 * others might be attempting to join also. We are careful 
	 * to ensure that only one of the racing threads is able to 
	 * update our internal data structures with our internal 
	 * representation of this transaction.
	 * NB: There are better ways of doing this which could ensure
	 *     we never make an unnecessary remote call that we may
	 *     want to explore later.
	 */
	if (txn == null) {
	    final TransactionManager mgr = 
		(TransactionManager)
		    transactionManagerPreparer.prepareProxy(tr.mgr);
	    tr = new ServerTransaction(mgr, tr.id);
	    tr.join(participantProxy, crashCount);
	    txn = txnTable.put(tr);
        }

        return txn;
    }

    /**
     * Look in the table for the <code>Txn</code> object for the given
     * manager/id pair.  If there is one, return it.  If there isn't
     * one, throw <code>UnknownTransactionException</code>.
     */
    private Txn getTxn(TransactionManager mgr, long id)
	throws UnknownTransactionException, UnmarshalException
    {
	Txn txn;
	try {
	    txn = (Txn) txnTable.get(mgr, id);
	} catch (IOException e) {
	    throw brokenTxn(mgr, id, e);
	} catch (ClassNotFoundException e) {
	    throw brokenTxn(mgr, id, e);
	}

	if (txnLogger.isLoggable(Level.FINEST)) {
	    txnLogger.log(Level.FINEST, 
	        "OutriggerServerImpl: getTxn got Txn={0}", txn);
	}

        if (txn == null) {
	    final String msg = "unknown transaction [mgr:" + mgr + 
		", id:" + id + "], passed to abort/prepare/commit";
	    final UnknownTransactionException ute = 
		new UnknownTransactionException(msg);
	    if (txnLogger.isLoggable(Levels.FAILED))
		txnLogger.log(Levels.FAILED, msg, ute);
	    throw ute;
	}
        return txn;
    }

    /**
     * Create, log, and throw a new UnmarshalException to represent
     * a transaction recovery failure 
     */
    private UnmarshalException brokenTxn(TransactionManager mgr, long id,
					 Exception nested)
	throws UnmarshalException
    {
	final UnmarshalException ue = new UnmarshalException(
            "Outrigger has a transaction with this id(" + id + "), but can't" +
	    "unmarshal its copy of manager to confirm it is the same " +
	    "transaction",
	    nested);
	
	final String msg = "the unmarshalling/preparation failure of one " +
	    "or more transaction managers has prevented outrigger from " +
	    "processing an abort/prepare/commit for transaction [mgr:" +
	    mgr + ", id:" + id + "]";
        
	txnLogger.log(Level.INFO, msg, ue);
	throw ue;
    }

    
    /**
     * We assume that each <code>Transaction</code> object sent to the
     * space is actually the <code>ServerTransaction</code> subtype.
     * This method does the downcast, and turns an error into the
     * appropriate <code>UnknownTransactionException</code> exception.
     */
    private ServerTransaction serverTransaction(Transaction baseTr)
        throws UnknownTransactionException
    {
        try {
            return (ServerTransaction) baseTr;
        } catch (ClassCastException e) {
	    final String msg = "unexpected transaction type:" + 
		baseTr.getClass();
	    final UnknownTransactionException ute = 
		new UnknownTransactionException(msg);
	    if (txnLogger.isLoggable(Levels.FAILED))
		txnLogger.log(Levels.FAILED, msg, ute);
	    throw ute;
        }
    }

    //---------------------------------------------------
    // TransactionParticipant methods
    //---------------------------------------------------

    /* There is a set of issues around persistence, locking, and
     * transaction state changes. In general we would like to persist
     * state changes before changing the in memory state - this way a
     * new state won't become visible to clients, and then revert back
     * to the old state if we get an IOException before/while
     * persisting that change. In particular this can be an issue when
     * aborting a prepared take. Say the space has one entry, `E', and
     * E has been taken under the transaction `T' and that T has been
     * prepared. A read with a non-zero timeout using E as the
     * template would block. If an abort came in and we made the abort
     * visible in memory but then crashed before (or while) persisting
     * the abort, the read could return E before the crash - if the
     * space came back up and the client called again read but with a
     * 0 timeout it would most likely get null.
     * 
     * The flip side is that we have to make sure that the record that
     * moves a transaction out of the active state is written after
     * all the operational records associated with that
     * transaction. This could be accomplished by wrapping all writeOp
     * and takeOp calls w/ non null transactions in
     * ensureActive/allowStateChange pairs and writing the
     * prepare/commit/abort record after moving the transaction to the
     * new state in memory. But this conflicts with the previous
     * paragraph.
     * 
     * The final option is to make change of in memory data structures
     * and the writing of prepare/commit/abort record atomic with
     * respect to operations under the transaction. This requires
     * holding some sort of lock while writing to disk, we normally
     * avoid this, but in this case it is ok since it will only effect
     * operations on/under a given transaction and they need to be
     * serialized anyway.
     *
     * The last wrinkle is operations that are blocking on a
     * transaction. A query that is blocked on transaction resolution
     * will get notified of transaction resolution via the
     * OperationJournal. We make sure these operations don't see
     * inconsistent states by posting to the OperationJournal after
     * the appropriate log records have been written to disk and after
     * the appropriate in memory state changes have been made.
     */
    public int prepare(TransactionManager mgr, long id)
	throws UnknownTransactionException, UnmarshalException
    {
	//The Space will make sure that for each action
	//under the given transaction, a record is
	//flushed to the log.

	txnLogger.entering("OutriggerServerImpl", "prepare");

	Txn txn = getTxn(mgr, id);

	// quick check to see if already in prepared state (due
	// to a crash after a prepareOp was written but before
	// a return to the TM
	//
	if (txn.getState() == PREPARED)
	    return PREPARED;

	/* Make all state changes (in memory and on disk) atomic wrt.
	 * to operations trying to use the txn.
	 */
	txn.makeInactive();

	if (log != null)
	    log.prepareOp(txn.getId(), txn);

	int result = txn.prepare(this);
	if (result == NOTCHANGED || result == ABORTED) {

	    // log that the prepare does not need to be done
	    if (log != null)
		log.abortOp(txn.getId());

	    txnTable.remove(mgr, id);
	}
	return result;
    }

    // inherit doc comment
    public void commit(TransactionManager mgr, long id)
	throws UnknownTransactionException, UnmarshalException
    {
	//The changes logged since the last sync point
	//will now be rolled forward.
	txnLogger.entering("OutriggerServerImpl", "commit");

	Txn txn = getTxn(mgr, id);

	/* Make all state changes (in memory and on disk) atomic wrt.
	 * to operations trying to use the txn.
	 */
	txn.makeInactive();
	try {

	    if (log != null)
		log.commitOp(txn.getId());

	    txn.commit(this);
	} finally {	    
	    txnTable.remove(mgr, id);
	}
    }

    // inherit doc comment
    public void abort(TransactionManager mgr, long id)
	throws UnknownTransactionException, UnmarshalException
    {
	txnLogger.entering("OutriggerServerImpl", "abort");

	Txn txn = getTxn(mgr, id);
	/* Make all state changes (in memory and on disk) atomic wrt.
	 * to operations trying to use the txn.
	 */
	txn.makeInactive();
        try {
	    if (log != null)
		log.abortOp(txn.getId());

            txn.abort(this);
        } finally {
	    txnTable.remove(mgr, id);
        }
    }

    // inherit doc comment
    public int prepareAndCommit(TransactionManager mgr, long id)
	throws UnknownTransactionException, UnmarshalException
    {
	txnLogger.entering("OutriggerServerImpl", "prepareAndCommit");
        Txn txn = getTxn(mgr, id);

	/* Make all state changes (in memory and on disk) atomic wrt.
	 * to operations trying to use the txn.
	 */
	txn.makeInactive();

        int result = txn.prepare(this);
	if (result == PREPARED) {

            if (log != null)
                log.commitOp(txn.getId());
 
            txn.commit(this);
	    result = COMMITTED;
	}
	txnTable.remove(mgr, id);
	return result;
    }

    /**
     * Return the proxy preparer for recovered transaction
     * managers, or <code>null</code> if there is none.
     * @return the proxy preparer for recovered transaction
     * managers, or <code>null</code> if there is none.  */
    ProxyPreparer getRecoveredTransactionManagerPreparer() {
	return recoveredTransactionManagerPreparer;
    }

    // ------------------------------------------------------------
    //			Debug stuff
    // ------------------------------------------------------------

    /**
     * Print out a debug description of <code>obj</code>, followed by
     * the given <code>str</code>.
     */
    private final void debug(Object obj, String str) {
	String name = obj.getClass().getName();
	int dollar = name.indexOf('$');
	if (dollar > 0)
	    name = name.substring(dollar + 1);
	System.out.print(name);
	System.out.print(':');
	System.out.println(str);
    }

    /**
     * Create the service-owned attributes for an Outrigger server.
     */
    private static Entry[] attributesFor() {
	final Entry info = new ServiceInfo("JavaSpace", 
	    "Sun Microsystems, Inc.", "Sun Microsystems, Inc.",
	    com.sun.jini.constants.VersionConstants.SERVER_VERSION, "", "");
	
	final Entry type = 
	    new com.sun.jini.lookup.entry.BasicServiceType("JavaSpace");

	return new Entry[]{info, type};
    }

    /** 
     * Base class for our house keeping threads. Provides 
     * a common interface for thread termination that
     * does not rely on the JDK propagating InteruptException
     * properly.
     */
    private abstract class Reaper extends Thread {
	final private long interval;
	private boolean dead = false;

	private Reaper(String name, long interval) {
	    super(name);
	    this.interval = interval;
	}

	public void run() {
	    boolean goOn;
	    synchronized(this) {
		goOn = !dead;
	    } 

	    while (goOn) {
		reap();
		synchronized(this) {
		    try {
			wait(interval);
		    } catch (InterruptedException e) {
			return;
		    }
		    goOn = !dead;
		}
	    }
	}

	abstract void reap();

	private synchronized void kill() {
	    dead = true;
	    notifyAll();
	}
    }

    /** Entry reaping thread class */
    private class EntryReaper extends Reaper {
	/** 
	 * Create a new EntryReaper thread.
	 * @param reapingInterval how long to sleep between reaps
	 */
	private EntryReaper(long reapingInterval) {
	    super("Entry Reaping Thread", reapingInterval);
	}

	protected void reap() {
	    contents.reap();
	}
    }

    /** Template reaping thread class */
    private class TemplateReaper extends Reaper {
	/** 
	 * Create a new TemplateReaper thread.
	 * @param reapingInterval how long to sleep between reaps
	 */
	private TemplateReaper(long reapingInterval) {
	    super("Template Reaping Thread", reapingInterval);
	}

	protected void reap() {
	    templates.reap();
	}
    }

    /** Entry reaping thread class */
    private class ContentsQueryReaper extends Reaper {
	/** 
	 * Create a new EntryReaper thread.
	 * @param reapingInterval how long to sleep between reaps
	 */
	private ContentsQueryReaper(long reapingInterval) {
	    super("Contents Query Reaping Thread", reapingInterval);
	}

	protected void reap() {
	    /* Create a copy of contentsQueries so we can
	     * traverse the data w/o blocking contents 
	     * call and operations on MatchSet objects
	     */
	    ContentsQuery[] queries;
	    synchronized (contentsQueries) {
		Collection c = contentsQueries.values();
		queries = new ContentsQuery[c.size()];
		queries = (ContentsQuery[])c.toArray(queries);
	    }
		    
	    final long now = System.currentTimeMillis();
	    for (int i=0; i< queries.length; i++) {
		final ContentsQuery query = queries[i];
		synchronized (query) {
		    if (query.getExpiration() <= now) {
			query.cancel();
		    }
		}
	    }
	}
    }

    /** Log and throw the passed runtime exception */
    private RuntimeException logAndThrow(RuntimeException e, Logger logger) {
	if (logger.isLoggable(Levels.FAILED))
	    logger.log(Levels.FAILED, e.getMessage(), e);
	throw e;
    }

    /** Log and throw a new IllegalArgumentException. Logs to the opsLogger. */
    private IllegalArgumentException logAndThrowIllegalArg(String msg) {
	final IllegalArgumentException e = new IllegalArgumentException(msg);
	throw (IllegalArgumentException)logAndThrow(e, opsLogger);
    }
	    
    /** Log and throw a new UnknownLeaseException */
    private UnknownLeaseException throwNewUnknownLeaseException(
            Object cookie) 
	throws UnknownLeaseException
    {
	final UnknownLeaseException ule = new UnknownLeaseException();
	if (leaseLogger.isLoggable(Levels.FAILED)) {
	    leaseLogger.log(Levels.FAILED, "unable to find lease for " +
			    cookie, ule);
	}

	throw ule;
    }

    /** Log and throw new CannotJoinException */
    private CannotJoinException throwNewCannotJoinException() 
        throws CannotJoinException
    {
	final String msg = "transaction is not active";
	final CannotJoinException cje = new CannotJoinException(msg);
	txnLogger.log(Levels.FAILED, msg, cje);
	throw cje;
    } 

    /** Log and throw new NoSuchObjectException */
    private NoSuchObjectException throwNewNoSuchObjectException(
	    String msg, Logger logger)
	throws NoSuchObjectException
    {
	throw throwNewNoSuchObjectException(msg, null, logger);
    }

    /** Log and throw new NoSuchObjectException with a nested exception */
    private NoSuchObjectException throwNewNoSuchObjectException(
	    String msg, Throwable t, Logger logger)
	throws NoSuchObjectException
    {
	final NoSuchObjectException nsoe = new NoSuchObjectException(msg);
	nsoe.initCause(t);
	logger.log(Levels.FAILED, msg, nsoe);
	throw nsoe;
    }
}

