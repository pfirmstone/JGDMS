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

import org.apache.river.landlord.Landlord;
import org.apache.river.landlord.LandlordUtil;
import org.apache.river.landlord.LeaseFactory;
import org.apache.river.landlord.LeasedResource;
import org.apache.river.landlord.LeasePeriodPolicy;
import org.apache.river.landlord.LeasePeriodPolicy.Result;
import org.apache.river.landlord.LocalLandlord;
import org.apache.river.logging.Levels;
import org.apache.river.mahalo.log.LogException;
import org.apache.river.mahalo.log.LogManager;
import org.apache.river.mahalo.log.LogRecord;
import org.apache.river.mahalo.log.LogRecovery;
import org.apache.river.mahalo.log.MultiLogManager;
import org.apache.river.mahalo.log.MultiLogManagerAdmin;
import org.apache.river.start.LifeCycle;
import org.apache.river.api.util.Startable;
import org.apache.river.thread.InterruptedStatusThread;
import org.apache.river.thread.ReadyState;
import org.apache.river.thread.WakeupManager;

import java.io.File;
import java.io.IOException;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.rmi.activation.Activatable;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationSystem;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceID;
import net.jini.core.transaction.CannotAbortException;
import net.jini.core.transaction.CannotCommitException;
import net.jini.core.transaction.CannotJoinException;
import net.jini.core.transaction.TimeoutExpiredException;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import net.jini.core.transaction.UnknownTransactionException;
import net.jini.core.transaction.server.CrashCountException;
import net.jini.core.transaction.server.ServerTransaction;
import net.jini.core.transaction.server.TransactionConstants;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.transaction.server.TransactionParticipant;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.export.ServiceAttributesAccessor;
import net.jini.export.ServiceIDAccessor;
import net.jini.export.ServiceProxyAccessor;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalledInstance;
import net.jini.lookup.entry.ServiceInfo;
import net.jini.security.ProxyPreparer;
import net.jini.security.proxytrust.ServerProxyTrust;
import net.jini.security.TrustVerifier;
import org.apache.river.thread.ExtensibleExecutorService;
import org.apache.river.thread.ExtensibleExecutorService.RunnableFutureFactory;

/**
 * An implementation of the Jini Transaction Specification.
 *
 * @author Sun Microsystems, Inc.
 *
 */
class TxnManagerImpl /*extends RemoteServer*/
    implements TxnManager, LeaseExpirationMgr.Expirer,
	    LogRecovery, TxnSettler, org.apache.river.constants.TimeConstants,
	    LocalLandlord, ServerProxyTrust, ProxyAccessor, Startable,
	    ServiceProxyAccessor, ServiceAttributesAccessor, ServiceIDAccessor
{
    /** Logger for (successful) service startup message */
    static final Logger startupLogger = 
        Logger.getLogger(TxnManager.MAHALO + ".startup");
    
    /** Logger for service re/initialization related messages */
    static final Logger initLogger = 
        Logger.getLogger(TxnManager.MAHALO + ".init");
	
    /** Logger for service destruction related messages */
    static final Logger destroyLogger = 
        Logger.getLogger(TxnManager.MAHALO + ".destroy");

    /** Logger for service operation messages */
    static final Logger operationsLogger = 
        Logger.getLogger(TxnManager.MAHALO + ".operations");

    /** 
     * Logger for transaction related messages 
     * (creation, destruction, transition, etc.) 
     */
    static final Logger transactionsLogger = 
        Logger.getLogger(TxnManager.MAHALO + ".transactions");

    /** Logger for transaction participant related messages */
    static final Logger participantLogger = 
        Logger.getLogger(TxnManager.MAHALO + ".participant");

    /** Logger for transaction persistence related messages */
    static final Logger persistenceLogger = 
        Logger.getLogger(TxnManager.MAHALO + ".persistence");

    private volatile LogManager logmgr = null;
    /* Retrieve values from properties.          */
    /* Its important here to schedule SettlerTasks on a */
    /* different TaskManager from what is given to      */
    /* TxnManagerTransaction objects.  Tasks on a given */
    /* TaskManager which create Tasks cannot be on the  */
    /* same TaskManager as their child Tasks.		*/
    private final ExecutorService settlerpool;
    /** wakeup manager for <code>SettlerTask</code> */
    private final WakeupManager settlerWakeupMgr;
    private final ExecutorService taskpool;
    /** wakeup manager for <code>ParticipantTask</code> */
    private final WakeupManager taskWakeupMgr;
    /* Map of transaction ids are their associated, internal 
     * transaction representations */
    private final ConcurrentMap<Long,TxnManagerTransaction> txns;
    private final Queue<Long> unsettledtxns = new ConcurrentLinkedQueue<Long>();
    private final InterruptedStatusThread settleThread;
    private final String persistenceDirectory;
    private final ActivationID activationID;
    /** Whether the activation ID has been prepared */
    private final boolean activationPrepared;
    /** The activation system, prepared */
    private final ActivationSystem activationSystem;
    /** Proxy preparer for listeners */
    private volatile ProxyPreparer participantPreparer = null;
    /** The exporter for exporting and unexporting */
    protected final Exporter exporter;
    /** The login context, for logging out */
    protected volatile LoginContext loginContext = null;
    /** The generator for our IDs. */
    private static final SecureRandom idGen = new SecureRandom();
    /** The buffer for generating IDs. */
    private static final byte[] idGenBuf = new byte[8];
    /**<code>LeaseExpirationMgr</code> used by our <code>LeasePolicy</code>.*/
    private volatile LeaseExpirationMgr expMgr = null;
    private final LeasePeriodPolicy txnLeasePeriodPolicy;
    /** <code>LandLordLeaseFactory</code> we use to create leases */
    private volatile LeaseFactory leaseFactory = null;
    private final JoinStateManager joinStateManager;
    /* The <code>Uuid</code> for this service. Used in the
     * <code>TxnMgrProxy</code> and <code>TxnMgrAdminProxy</code> to
     * implement reference equality. We also derive our
     * <code>ServiceID</code> from it.  */
    private final Uuid topUuid;
    /** The outer proxy of this server */
    private volatile TxnMgrProxy txnMgrProxy = null;	
    /** The admin proxy of this server */
    private volatile TxnMgrAdminProxy txnMgrAdminProxy = null;	
    /** Cache of our inner proxy. */
    private volatile TxnManager serverStub = null;
    /** Cache of our <code>LifeCycle</code> object */
    private volatile LifeCycle lifeCycle = null;
    /* Object used to prevent access to this service during the service's
     *  initialization or shutdown processing. */
    private final ReadyState readyState = new ReadyState();
    /* <code>boolean</code> flag used to determine persistence support.
     * Defaulted to true, and overridden in the constructor overload that takes
     * a <code>boolean</code> argument. */
    private final boolean persistent;
    // The following three fields are only required by start, called by the
    // constructor thread and set to null after starting.
    private Configuration config;
    private AccessControlContext context;
    private Throwable thrown;
    // sync on this.
    private boolean started = false;

    /**
     * Constructs a non-activatable transaction manager.
     *
     * @param args Service configuration options
     *
     * @param lc <code>LifeCycle</code> reference used for callback
     */
    TxnManagerImpl(String[] args, LifeCycle lc, boolean persistent)
    {
        this(args, null, persistent, new Object[] {Arrays.asList(args), lc, Boolean.valueOf(persistent)});
	lifeCycle = lc; 
    }
    /**
     * Constructs an activatable transaction manager.
     *
     * @param activationID activation ID passed in by the activation daemon.
     *
     * @param data state data needed to re-activate a transaction manager.
     */
    TxnManagerImpl(ActivationID activationID, MarshalledObject data)
	throws IOException, ClassNotFoundException
    {
        // data.get IOException and ClassNotFoundException are safe from 
        // finalizer attack, because it happens
        // before implicit call to super() Object.
        this((String[]) new MarshalledInstance(data).get(false), activationID, true, new Object[] {activationID, data} );
    }
    

    
    private TxnManagerImpl(String[] configArgs, final ActivationID activID, final boolean persistant, Object [] logMessage) 
    {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(
		TxnManagerImpl.class.getName(), "TxnManagerImpl",logMessage );
	}
        this.persistent = persistant;
        TxnManagerImplInitializer init = null;
        try {
            
            /** initialisation common to both activatable and transient instances. */
            if (operationsLogger.isLoggable(Level.FINER)) {
                operationsLogger.entering(TxnManagerImpl.class.getName(), "init",
                    (Object[])configArgs );
            }
            config =
                ConfigurationProvider.getInstance(
                    configArgs, getClass().getClassLoader());
            loginContext = (LoginContext) config.getEntry(
                TxnManager.MAHALO, "loginContext", LoginContext.class, null);
            if (loginContext != null) {
                // Setup with login context.
                if (operationsLogger.isLoggable(Level.FINER)) {
                    operationsLogger.entering(TxnManagerImpl.class.getName(), 
                        "doInitWithLogin",
                        new Object[] { config, loginContext } );
                }
                loginContext.login();

                try {
                    init = Subject.doAsPrivileged(
                        loginContext.getSubject(),
                        new PrivilegedExceptionAction<TxnManagerImplInitializer>() {
                            public TxnManagerImplInitializer run() throws Exception {
                                return new TxnManagerImplInitializer(
                                    config, 
                                    persistant, 
                                    activID, 
                                    new InterruptedStatusThread("settleThread") {
                                        public void run() {
                                            try {
                                                settleTxns();
                                            } catch (InterruptedException ie) {
                                                if (transactionsLogger.isLoggable(Level.FINEST)) {
                                                    transactionsLogger.log(Level.FINEST,
                                                        "settleThread interrupted -- exiting");
                                                }
                                                return;
                                            }
                                        };
                                    }
                                );
                            }
                        },
                        null);
                } catch (PrivilegedActionException e) {
                    //TODO - move to end of initFailed() so that shutdown still occurs under login 	
                    try {
                        loginContext.logout();
                    } catch (LoginException le) {
                        if( initLogger.isLoggable(Levels.HANDLED) ) {
                            initLogger.log(Levels.HANDLED, "Trouble logging out", le);
                        }
                    }
                    throw e.getException();
                }
                if (operationsLogger.isLoggable(Level.FINER)) {
                    operationsLogger.exiting(TxnManagerImpl.class.getName(), 
                        "doInitWithLogin");
                }
            } else {
                // Setup without login context.
                init =
                new TxnManagerImplInitializer(
                    config, 
                    persistent, 
                    activID, 
                    new InterruptedStatusThread("settleThread") {
                        public void run() {
                            try {
                                settleTxns();
                            } catch (InterruptedException ie) {
                                if (transactionsLogger.isLoggable(Level.FINEST)) {
                                    transactionsLogger.log(Level.FINEST,
                                        "settleThread interrupted -- exiting");
                                }
                                return;
                            }
                        };
                    }
                );
            }
            if (operationsLogger.isLoggable(Level.FINER)) {
                operationsLogger.exiting(
                    TxnManagerImpl.class.getName(), "init");
            }
            
        } catch (Throwable e) {
            thrown = e;
        } finally {
            if (init != null){
                // Assign init fields
                txns = init.txns;
                /* Retrieve values from properties.          */
                activationSystem = init.activationSystem;
                activationID = activID;
                activationPrepared = init.activationPrepared;
                exporter = init.exporter;
                participantPreparer = init.participantPreparer;
                txnLeasePeriodPolicy = init.txnLeasePeriodPolicy;
                persistenceDirectory = init.persistenceDirectory;
                joinStateManager = init.joinStateManager;
                settlerpool 
                        = new ExtensibleExecutorService(
                                init.settlerpool, 
                                new FutureFactory()
                        );
                settlerWakeupMgr = init.settlerWakeupMgr;
                taskpool 
                        = new ExtensibleExecutorService(
                                init.taskpool,
                                new FutureFactory()
                        );
                taskWakeupMgr = init.taskWakeupMgr;
                topUuid = init.topUuid;
                context = init.context;
                settleThread = init.settleThread;
            } else {
                // Assign init fields
                txns = null;
                /* Retrieve values from properties.          */
                activationSystem = null;
                activationID = activID;
                activationPrepared = false;
                exporter = null;
                participantPreparer = null;
                txnLeasePeriodPolicy = null;
                persistenceDirectory = null;
                joinStateManager = null;
                settlerpool = null;
                settlerWakeupMgr = null;
                taskpool = null;
                taskWakeupMgr = null;
                topUuid = null;
                context = null;
                settleThread = null; // Thread hasn't been started let it get collected by gc.
            }
        }
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(
		TxnManagerImpl.class.getName(), "TxnManagerImpl");
	}
    }

    public void start() throws Exception {
        synchronized (this){
            if (started) return;
            started = true;
        }
        try {
            if (thrown != null) throw thrown;
            AccessController.doPrivileged(new PrivilegedExceptionAction<Object>(){

                @Override
                public Object run() throws Exception {
                    if(initLogger.isLoggable(Level.FINEST)) {
                        initLogger.log(Level.FINEST, "Exporting server");
                    }
                    serverStub = (TxnManager)exporter.export(TxnManagerImpl.this);
                    if(initLogger.isLoggable(Level.FINEST)) {
                        initLogger.log(Level.FINEST, "Server stub: {0}", serverStub);
                    }
                    // Create the proxy that will be registered in the lookup service
                    txnMgrProxy = 
                        TxnMgrProxy.create(serverStub, topUuid);
                    if(initLogger.isLoggable(Level.FINEST)) {
                        initLogger.log(Level.FINEST, "Service proxy is: {0}", 
                            txnMgrProxy);		
                    }
                    // Create the admin proxy for this service
                    txnMgrAdminProxy = 
                        TxnMgrAdminProxy.create(serverStub, topUuid);
                    if(initLogger.isLoggable(Level.FINEST)) {
                        initLogger.log(Level.FINEST, "Service admin proxy is: {0}", 
                            txnMgrAdminProxy);		
                    }
                    // Create leaseFactory
                    leaseFactory = new LeaseFactory(serverStub, topUuid);
                    expMgr = new LeaseExpirationMgr(TxnManagerImpl.this);
                    if(initLogger.isLoggable(Level.FINEST)) {
                        initLogger.log(Level.FINEST, "Setting up log manager");
                    }
                    if (persistent) {
                        logmgr = new MultiLogManager(TxnManagerImpl.this, persistenceDirectory);
                    } else {
                        logmgr = new MultiLogManager();
                    }

                    try {
                        logmgr.recover();
                        if(initLogger.isLoggable(Level.FINEST)) {
                            initLogger.log(Level.FINEST, "Settling incomplete transactions");
                        }

                        settleThread.start();
                    } catch (LogException le) {
                        RemoteException re =  
                            new RemoteException("Problem recovering state");
                        initLogger.throwing(TxnManagerImpl.class.getName(), "doInit", re);
                        throw re;
                    }

                    /*
                     * With SecureRandom, the first ID requires generation of a
                     * secure seed, which can take several seconds.  We do it here
                     * so it doesn't affect the first call's time.  (I tried doing
                     * this in a separate thread so some of the startup would occur
                     * during the roundtrip back the client, but it didn't help
                     * much and this is simpler.)
                     */
                    nextID();


                    /*
                     * Create the object that manages and persists our join state
                     */
                    if(initLogger.isLoggable(Level.FINEST)) {
                        initLogger.log(Level.FINEST, "Starting JoinStateManager");
                    }
                    // Starting causes snapshot to occur
                    joinStateManager.startManager(config, txnMgrProxy, 
                        new ServiceID(topUuid.getMostSignificantBits(),
                                topUuid.getLeastSignificantBits()),
                        attributesFor());

                    if (startupLogger.isLoggable(Level.INFO)) {
                        startupLogger.log
                               (Level.INFO, "Mahalo started: {0}", this);
                    }
                    readyState.ready();

                    if (operationsLogger.isLoggable(Level.FINER)) {
                        operationsLogger.exiting(
                            TxnManagerImpl.class.getName(), "doInit");
                    }
                    return null;
                }

            }, context);
        } catch (Throwable e) {
            if (e instanceof PrivilegedActionException){
                e = e.getCause();
            }
            cleanup();
	    initFailed(e); 
        } finally {
            // Clear references.
            config = null;
            context = null;
            thrown = null;
        }
        
    }
    
    //TransactionManager interface method

    public TransactionManager.Created create(long lease)
        throws LeaseDeniedException
    {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(
		TxnManagerImpl.class.getName(), "create", 
	        Long.valueOf(lease));
	}
        readyState.check();
    
	TxnManagerTransaction txntr;
        Lease txnmgrlease = null;
        ServerTransaction str = null;
        // following section must be performed atomically, or duplicate ID might be assigned.
        boolean todoPut = true;
        while (todoPut){
            long tid = nextID();
            Uuid uuid = createLeaseUuid(tid);

            if (transactionsLogger.isLoggable(Level.FINEST)) {
                transactionsLogger.log(Level.FINEST, 
                    "Transaction ID is: {0}", Long.valueOf(tid));
            }

            txntr = new TxnManagerTransaction(
                txnMgrProxy, logmgr, tid, taskpool, 
                taskWakeupMgr, this, uuid);
            try {
                Result r = txnLeasePeriodPolicy.grant(txntr, lease);
                txntr.setExpiration(r.expiration);
                txnmgrlease = 
                    leaseFactory.newLease(
                        uuid,
                        r.expiration);
                		
            } catch (LeaseDeniedException lde) {
                // Should never happen in our implementation.
                throw new AssertionError("Transaction lease was denied" + lde);
            }

            if (transactionsLogger.isLoggable(Level.FINEST)) {
                transactionsLogger.log(Level.FINEST, 
                    "Created new TxnManagerTransaction ID is: {0}", Long.valueOf(tid));
            }

            Transaction tr = txntr.getTransaction();

            try {
                str = serverTransaction(tr);
                TxnManagerTransaction existed = txns.putIfAbsent(Long.valueOf(str.id), txntr);
                /* This should never happen, but in the improbable event we get a collision */
                if (existed == null){ 
                    todoPut = false;
                    expMgr.register(txntr);
                } else {
                    
                }
                if (transactionsLogger.isLoggable(Level.FINEST)) {
                    transactionsLogger.log(Level.FINEST,
                        "recorded new TxnManagerTransaction", txntr);
                }


            } catch(Exception e) {
                if (transactionsLogger.isLoggable(Level.FINEST)) {
                    transactionsLogger.log(Level.FINEST,
                    "Problem creating transaction", e);
                }
                todoPut = false;
                RuntimeException wrap =
                    new RuntimeException("Unable to create transaction", e);
                transactionsLogger.throwing(
                    TxnManagerImpl.class.getName(), "create", wrap);
                throw wrap;
            }
        }

        TransactionManager.Created tmp = 	
	    new TransactionManager.Created(str.id, txnmgrlease);		       
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(
		TxnManagerImpl.class.getName(), "create", tmp);
	}

        return tmp;
    }

    public void
        join(long id, TransactionParticipant part, long crashCount)
        throws UnknownTransactionException, CannotJoinException,
	       CrashCountException, RemoteException
    {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(
		TxnManagerImpl.class.getName(), "join", 
	        new Object[] {Long.valueOf(id), part, Long.valueOf(crashCount) });
	}
        readyState.check();

								
        TransactionParticipant preparedTarget = null;
        preparedTarget =
            (TransactionParticipant)
                participantPreparer.prepareProxy(part);
        
	if (participantLogger.isLoggable(Level.FINEST)) {
            participantLogger.log(Level.FINEST, 
	        "prepared participant: {0}", preparedTarget);
	}

        TxnManagerTransaction txntr = txns.get(Long.valueOf(id));

	if (txntr == null)
	    throw new UnknownTransactionException("unknown transaction");

	// txntr.join does expiration check
	txntr.join(preparedTarget, crashCount);
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(
		TxnManagerImpl.class.getName(), "join");
	}
    }


    public int getState(long id)
        throws UnknownTransactionException
    {
        
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(
		TxnManagerImpl.class.getName(), "getState", 
	        new Object[] {Long.valueOf(id)});
	}
        readyState.check();

        TxnManagerTransaction txntr = null;
                txntr = txns.get(Long.valueOf(id));
	if (txntr == null)
	    throw new UnknownTransactionException("unknown transaction");
        /* Expiration checks are only meaningful for active transactions. */
        /* NOTE: 
	 * 1) Cancellation sets expiration to 0 without changing state 
	 * from Active right away. Clients are supposed to treat 
	 * UnknownTransactionException just like Aborted, so it's OK to send
	 * in this case. 
	 * 2) Might be a small window where client is committing the transaction
	 * close to the expiration time. If the committed transition takes
	 * place between getState() and ensureCurrent then the client could get
	 * a false result.
	 */
//TODO - need better locking here. getState and expiration need to be checked atomically	
        try{
            int state = txntr.atomicCheckExpirationIfActive();
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(
		TxnManagerImpl.class.getName(), "getState", 
	        Integer.valueOf(state));
	}
            return state;
        } catch (TransactionException ex) {
            throw new UnknownTransactionException("Transaction expired", ex);
        }
    }


    public void commit(long id)
        throws UnknownTransactionException, CannotCommitException,
	RemoteException
    {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(
		TxnManagerImpl.class.getName(), "commit", 
	        Long.valueOf(id));
	}
        readyState.check();

        try {
            commit(id, 0);
        } catch(TimeoutExpiredException tee) {
	    //This exception is swallowed because the
	    //commit with no timeout only schedules a
	    //roll-forward to happen
        }
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(
		TxnManagerImpl.class.getName(), "commit");
	}
    }

    public void commit(long id, long waitFor)
        throws UnknownTransactionException, CannotCommitException,
	       TimeoutExpiredException, RemoteException
    {
        //!! No early return when not synchronous
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(
		TxnManagerImpl.class.getName(), "commit", 
	        new Object[] {Long.valueOf(id), Long.valueOf(waitFor)});
	}
        readyState.check();

	TxnManagerTransaction txntr = txns.get(Long.valueOf(id));

	if (transactionsLogger.isLoggable(Level.FINEST)) {
            transactionsLogger.log(Level.FINEST,
	        "Retrieved TxnManagerTransaction: {0}", txntr);
	}

	if (txntr == null)
	    throw new UnknownTransactionException("Unknown transaction");

	// txntr.commit does expiration check
	txntr.commit(waitFor);
        txns.remove(Long.valueOf(id), txntr); // Only removed if commit doesn't throw exception.

	if (transactionsLogger.isLoggable(Level.FINEST)) {
            transactionsLogger.log(Level.FINEST,
                "Committed transaction id {0}", Long.valueOf(id));
	}
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(
		TxnManagerImpl.class.getName(), "commit");
	}
    }
    
    public void abort(long id)
    throws UnknownTransactionException, CannotAbortException {
    	abort(id, true);
    }
    
    private void abort(long id, final boolean doExpiryCheck)
	throws UnknownTransactionException, CannotAbortException
    {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(
		TxnManagerImpl.class.getName(), "abort", 
	        new Object[] {Long.valueOf(id)});
	}
        readyState.check();
        try {
            abort(id, 0, doExpiryCheck);
        } catch(TimeoutExpiredException tee) {
	    //Swallow this exception because we only want to
	    //schedule a settler task
        }
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(
		TxnManagerImpl.class.getName(), "abort");
	}
    }

    public void abort(long id, long waitFor)
    throws UnknownTransactionException, CannotAbortException,
       TimeoutExpiredException {
    	abort(id, waitFor, true);
    }
    
    private void abort(long id, long waitFor, final boolean doExpiryCheck)
        throws UnknownTransactionException, CannotAbortException,
	       TimeoutExpiredException
    {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(
		TxnManagerImpl.class.getName(), "abort", 
	        new Object[] {Long.valueOf(id), Long.valueOf(waitFor)});
	}
        readyState.check();
        
        //!! Multi-participants not supported
        //!! No early return when not synchronous


        // At this point, ask the Participants associated
	// with the Transaction to prepare

	TxnManagerTransaction txntr = 
	        (TxnManagerTransaction) txns.get(Long.valueOf(id));

	if (transactionsLogger.isLoggable(Level.FINEST)) {
            transactionsLogger.log(Level.FINEST,
            "Retrieved TxnManagerTransaction: {0}", txntr);
	}
	
	if(txntr != null) {
		if(txntr.getState() == TransactionConstants.COMMITTED) {
			if(doExpiryCheck && !ensureCurrent(txntr)) {
				throw new TimeoutExpiredException("Cannot abort, transaction probably expired", true);
			} else {
				throw new CannotAbortException("Already committed");
			}
		}
	} else {	
	    throw new UnknownTransactionException("No such transaction ["+id+"]");
	}
        try {
            txntr.abort(waitFor);
        } catch (Throwable t){
//            t.printStackTrace(System.err);
            if (t instanceof Error) throw (Error) t;
            if (t instanceof RuntimeException) throw (RuntimeException) t;
        }
	txns.remove(Long.valueOf(id), txntr);

	if (transactionsLogger.isLoggable(Level.FINEST)) {
            transactionsLogger.log(Level.FINEST,
                "aborted transaction id {0}", Long.valueOf(id));
	}
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(
		TxnManagerImpl.class.getName(), "abort");
	}
    }

    //Satisfies the LogRecovery interface so that the
    //TransactionManager can recover it's non-transient
    //state in the face of process failure.

	/**
     *  This method recovers state changes resulting from
     *  committing a transaction.  This re-creates the
     *  internal representation of the transaction.
     * 
     * @param cookie the transaction's ID
     *
     * @param rec the <code>LogRecord</code>
     */
    public void recover(long cookie, LogRecord rec) throws LogException {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(
		TxnManagerImpl.class.getName(), "recover", 
	        new Object[] {Long.valueOf(cookie), rec});
	}
	TxnManagerTransaction tmt = enterTMT(cookie);
	TxnLogRecord trec = (TxnLogRecord) rec;
	trec.recover(tmt);
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(
		TxnManagerImpl.class.getName(), "recover");
	}
    }


    /**
     * Informs the transaction manager to attempt to
     * settle a given transaction.
     *
     * @param tid the transaction's ID
     */
    public void noteUnsettledTxn(long tid) {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(TxnManagerImpl.class.getName(), 
	        "noteUnsettledTxn", new Object[] {Long.valueOf(tid)});
	}
	unsettledtxns.add(Long.valueOf(tid));
        synchronized (this){
            notifyAll();
        }
        
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(TxnManagerImpl.class.getName(), 
	        "noteUnsettledTxn");
	}
    }

    private void settleTxns() throws InterruptedException {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(TxnManagerImpl.class.getName(), 
	        "settleTxns");
	}
	if (transactionsLogger.isLoggable(Level.FINEST)) {
            transactionsLogger.log(Level.FINEST,
                "Settling {0} transactions.", 
		Integer.valueOf(unsettledtxns.size())); //O(n)
	}

	Long first = null;
	long tid = 0;

	while (true) {
            first = unsettledtxns.poll();

	    if (first == null) {
	        if (transactionsLogger.isLoggable(Level.FINEST)) {
                    transactionsLogger.log(Level.FINEST,
                        "Settler waiting");
	        }
                // Don't wait forever, in case we're not notified, break out
                // early and check condition.
                synchronized (this){
                    wait(10000L); 
                }
                // Due to spurious wakeup and break out after ten seconds, the following log message is inaccurate.
//	        if (transactionsLogger.isLoggable(Level.FINEST)) {
//                    transactionsLogger.log(Level.FINEST,
//                        "Settler notified");
//	        }
		continue;
	    }

	    tid = first.longValue();

	    SettlerTask task = 
	        new SettlerTask(settlerpool, settlerWakeupMgr, this, tid);
	    settlerpool.execute(task);

            if (settleThread.hasBeenInterrupted()) 
	        throw new InterruptedException("settleTxns interrupted");
		
	    if (transactionsLogger.isLoggable(Level.FINEST)) {
                transactionsLogger.log(Level.FINEST,
                    "Added SettlerTask for tid {0}", Long.valueOf(tid));
	    }
	}
    }


    //TransactionParticipant interface go here
    //when I implement nested transactions



    /**
     *  Method from <code>TxnManager</code> which produces
     *  a <code>Transaction</code> from its ID.
     *
     * @param id the ID
     *
     * @see net.jini.core.transaction.Transaction
     * @see org.apache.river.mahalo.TxnManager
     */
    public Transaction getTransaction(long id)
        throws UnknownTransactionException {
            
        readyState.check();


        if (id == ((long)-1))
            return null;

        // First consult the hashtable for the Object
        // containing all actions performed under a
        // particular transaction

        TxnManagerTransaction txntr =
	        (TxnManagerTransaction) txns.get(Long.valueOf(id));

	if (txntr == null)
	    throw new UnknownTransactionException("unknown transaction");

        Transaction tn = (Transaction) txntr.getTransaction();
        ServerTransaction tr = serverTransaction(tn);

        if (tr == null)
           throw new UnknownTransactionException(
					     "TxnManagerImpl: getTransaction: "
                                             + "unable to find transaction(" +
					     id + ")");
//TODO - use IDs vs equals					     
        if (!tr.mgr.equals(this))
            throw new UnknownTransactionException("wrong manager (" + tr.mgr +
						  " instead of " + this + ")");

        return tr;
    }


    /**
     * Requests the renewal of  a lease on a <code>Transaction</code>.
     *
     * @param uuid identifies the leased resource
     * @param extension requested lease extension
     *
     * @see net.jini.core.lease.Lease
     * @see org.apache.river.landlord.LeasedResource
     * @see org.apache.river.mahalo.LeaseManager
     */
    public long renew(Uuid uuid, long extension)
	        throws UnknownLeaseException, LeaseDeniedException
    {

        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(TxnManagerImpl.class.getName(), "renew", 
	        new Object[] {uuid, Long.valueOf(extension)});
	}
        readyState.check();

        verifyLeaseUuid(uuid);
	Long tid = getLeaseTid(uuid);
	TxnManagerTransaction txntr =
	        (TxnManagerTransaction)txns.get(tid);

	if (txntr == null)
	    throw new UnknownLeaseException();

	// synchronize on the resource so there is not a race condition
	// between renew and expiration
        /* TODO check if synchronization correct */
	Result r;
	synchronized (txntr) {
//TODO - check for ACTIVE too?
//TODO - if post-ACTIVE, do anything?	
	    if (!ensureCurrent(txntr))
		throw new UnknownLeaseException("Lease already expired");
	    long oldExpiration = txntr.getExpiration();
            r = txnLeasePeriodPolicy.renew(txntr, extension);
	    txntr.setExpiration(r.expiration);
	    expMgr.renewed(txntr);
            if (operationsLogger.isLoggable(Level.FINER)) {
                operationsLogger.exiting(
		    TxnManagerImpl.class.getName(), "renew", 
	            new Object[] {Long.valueOf(r.duration)});
	    }
	    return r.duration;
	}
    }

    /**
     * Cancels the lease on a <code>Transaction</code>.
     *
     * @param uuid identifies the leased resource
     *
     * @see net.jini.core.lease.Lease
     * @see org.apache.river.landlord.LeasedResource
     * @see org.apache.river.mahalo.LeaseManager
     */
    public void cancel(Uuid uuid) throws UnknownLeaseException {
    	
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(
		TxnManagerImpl.class.getName(), "cancel", 
	        new Object[] {uuid});
	}
        readyState.check();
	
        verifyLeaseUuid(uuid);
	Long tid = getLeaseTid(uuid);
	TxnManagerTransaction txntr =
	        (TxnManagerTransaction)txns.get(tid);

	if (txntr == null)
	    throw new UnknownLeaseException();

        
        try {
            // getState and expiration checked atomically
            txntr.atomicCheckStateActive(); // throws TransactionException if state not ACTIVE.
            // State is still active, make it expire.
            txntr.setExpiration(0);	// Mark as done
            abort(((Long)tid).longValue(), false);
        } catch (TransactionException e) {
            throw new
                UnknownLeaseException("When canceling abort threw:" +
            e.getClass().getName() + ":" + e.getLocalizedMessage());
        }
	
	
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(
		TxnManagerImpl.class.getName(), "cancel");
	}
    }

    /**
     * Bulk renewal request of leases on <code>Transaction</code>s.
     *
     * @param cookies identifies the leased resources
     *
     * @param extensions requested lease extensions
     *
     * @see net.jini.core.lease.Lease
     * @see org.apache.river.landlord.LeasedResource
     * @see org.apache.river.mahalo.LeaseManager
     */
    public Landlord.RenewResults renewAll(Uuid[] cookies, long[] extensions) {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(
		TxnManagerImpl.class.getName(), "renewAll");
	}
        readyState.check();

	Landlord.RenewResults results =
	    LandlordUtil.renewAll(this, cookies, extensions);
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(
		TxnManagerImpl.class.getName(), "renewAll");
	}
	return results;
    }


    /**
     * Bulk cancel of leases on <code>Transaction</code>s.
     *
     * @param cookies identifies the leased resources
     *
     * @see net.jini.core.lease.Lease
     * @see org.apache.river.landlord.LeasedResource
     * @see org.apache.river.mahalo.LeaseManager
     */
    public Map cancelAll(Uuid[] cookies) {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(
		TxnManagerImpl.class.getName(), "cancelAll");
	}
        readyState.check();

	Map results = LandlordUtil.cancelAll(this, cookies);
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(
		TxnManagerImpl.class.getName(), "cancelAll");
	}
	return results;
    }

    // local methods

    /**
     * gets the next available transaction ID.
     */
    static long nextID() {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(
		TxnManagerImpl.class.getName(), "nextID");
	}
	long id;
	synchronized (idGen) {
	    do {
		id = 0;
		idGen.nextBytes(idGenBuf);
		for (int i = 0; i < 8; i++)
		    id = (id << 8) | (idGenBuf[i] & 0xFF);
	    } while (id == 0);				// skip flag value
	}
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(TxnManagerImpl.class.getName(), "nextID",
	        Long.valueOf(id));
	}
	return id;
    }



    private ServerTransaction serverTransaction(Transaction baseTr)
        throws UnknownTransactionException
    {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(
		TxnManagerImpl.class.getName(), 
	        "serverTransaction", baseTr);
	}
        try {
            if (operationsLogger.isLoggable(Level.FINER)) {
                operationsLogger.exiting(TxnManagerImpl.class.getName(), 
   	            "serverTransaction", baseTr);
	    }
            return (ServerTransaction) baseTr;
        } catch (ClassCastException e) {
            throw new UnknownTransactionException("unexpected transaction type");
        }
    }


    /**
     * Returns a reference to the <code>TransactionManager</code>
     * interface.
     *
     * @see net.jini.core.transaction.server.TransactionManager
     */
    public TransactionManager manager() {
        readyState.check();

        return txnMgrProxy;
    }


    private TxnManagerTransaction enterTMT(long cookie) {
	Long key = Long.valueOf(cookie);
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(TxnManagerImpl.class.getName(), 
	        "enterTMT", key);
	}
	TxnManagerTransaction tmt =
	        (TxnManagerTransaction) txns.get(key);

	if (tmt == null) {
            Uuid uuid = createLeaseUuid(cookie);
	    tmt = new TxnManagerTransaction(
	        txnMgrProxy, logmgr, cookie, taskpool, 
		taskWakeupMgr, this, uuid);
	    noteUnsettledTxn(cookie);
	    /* Since only aborted or committed txns are persisted,
	     * their expirations are irrelevant. Therefore, any recovered
	     * transactions are effectively lease.FOREVER. 
	     */
            
            TxnManagerTransaction existed = txns.putIfAbsent(key, tmt);
            if (existed != null) tmt = existed;
	}

        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(TxnManagerImpl.class.getName(), 
	        "enterTMT", tmt);
	}
	return tmt;
    }

    //***********************************************************
    // Admin

    // Methods required by DestroyAdmin

    /**
     * Cleans up and exits the transaction manager.
     */
    public void destroy() {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(
		TxnManagerImpl.class.getName(), "destroy");
	}
        readyState.check();

        (new DestroyThread()).start();
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(
		TxnManagerImpl.class.getName(), "destroy");
	}
    }
 
    /** Maximum delay for unexport attempts */
    private final static long MAX_UNEXPORT_DELAY = 2 * MINUTES;

    @Override
    public Entry[] getServiceAttributes() throws IOException {
	return getLookupAttributes();
    }

    @Override
    public ServiceID serviceID() throws IOException {
	return new ServiceID(topUuid.getMostSignificantBits(), 
		topUuid.getLeastSignificantBits());
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
            if (operationsLogger.isLoggable(Level.FINER)) {
               operationsLogger.entering(
		   DestroyThread.class.getName(), "run");
	    }

            Exception failed = null;

/**TODO 
  * - move this block into the destroy() method and let the 
  *   remote ex pass through
  */
            if (activationPrepared) {	    
	        try {
                    if(destroyLogger.isLoggable(Level.FINEST)) {
	                destroyLogger.log(Level.FINEST,
			    "Unregistering object.");
                    }
		    if (activationID != null)
                        activationSystem.unregisterObject(activationID);
   		} catch (RemoteException e) {
   		    /* give up until we can at least unregister */
                    if(destroyLogger.isLoggable(Level.WARNING)) {
	                destroyLogger.log(Level.WARNING,
			    "Trouble unregistering object -- aborting.", e);
                    }
   		    return;
   		} catch (ActivationException e) {
                    /*
                     * Activation system is shutting down or this
                     * object has already been unregistered --
                     * ignore in either case.
                     */
                    if(destroyLogger.isLoggable(Levels.HANDLED)) {
	                destroyLogger.log(Levels.HANDLED,
			    "Trouble unregistering object -- ignoring.", e);
                    }
   		}
	    }

            // Attempt to unexport this object -- nicely first
            if(destroyLogger.isLoggable(Level.FINEST)) {
	        destroyLogger.log(Level.FINEST,
		    "Attempting unforced unexport.");
            }
            long endTime =
                System.currentTimeMillis() + MAX_UNEXPORT_DELAY;
            if (endTime < 0) { // Check for overflow
                endTime = Long.MAX_VALUE;
            }
            boolean unexported = false;
/**TODO 
  * - trap IllegalStateException from unexport
  */
            while ((!unexported) &&
                   (System.currentTimeMillis() < endTime)) {
                /* wait for any pending operations to complete */
                unexported = exporter.unexport(false);
                if (!unexported) {
                    if (destroyLogger.isLoggable(Level.FINEST)) {
                        destroyLogger.log(Level.FINEST, 
                            "Waiting for in-progress calls to complete");
                    }
                    try {
                        sleep(1000);
                    } catch (InterruptedException ie) {
                        if (destroyLogger.isLoggable(Levels.HANDLED)) {
                            destroyLogger.log(Levels.HANDLED, 
                                "problem unexporting nicely", ie);
                        }
                        break; //fall through to forced unexport
                   }
                } else {
                    if (destroyLogger.isLoggable(Level.FINEST)) {
                        destroyLogger.log(Level.FINEST, 
                            "Unexport completed");
                    }
                }      
            }

            // Attempt to forcefully unexport this object, if not already done
            if (!unexported) {
                if(destroyLogger.isLoggable(Level.FINEST)) {
	            destroyLogger.log(Level.FINEST,
		        "Attempting forced unexport.");
                }
		/* Attempt to forcefully export the service */
                unexported = exporter.unexport(true);
            }

            if(destroyLogger.isLoggable(Level.FINEST)) {
	        destroyLogger.log(Level.FINEST,"Destroying JoinStateManager.");
	    }
	    try {
	        joinStateManager.destroy();
            } catch (Exception t) {
                if(destroyLogger.isLoggable(Levels.HANDLED)) {
	            destroyLogger.log(Levels.HANDLED, 
		        "Problem destroying JoinStateManager", t);
		}
            }

	    //
            // Attempt to stop all running threads
            //
            if(destroyLogger.isLoggable(Level.FINEST)) {
	        destroyLogger.log(Level.FINEST,"Terminating lease expiration manager.");
	    }
	    expMgr.terminate();

            if(destroyLogger.isLoggable(Level.FINEST)) {
	        destroyLogger.log(Level.FINEST,"Interrupting settleThread.");
            }
	    settleThread.interrupt();
            try {
                settleThread.join();
            } catch (InterruptedException ie) {
                if(destroyLogger.isLoggable(Levels.HANDLED)) {
	            destroyLogger.log(Levels.HANDLED, 
		        "Problem stopping settleThread", ie);
		}
            }

            if(destroyLogger.isLoggable(Level.FINEST)) {
	        destroyLogger.log(Level.FINEST,"Terminating settlerpool.");
            }
	    settlerpool.shutdown();
	    settlerWakeupMgr.stop();
	    settlerWakeupMgr.cancelAll();

            if(destroyLogger.isLoggable(Level.FINEST)) {
	        destroyLogger.log(Level.FINEST,"Terminating taskpool.");
            }
	    taskpool.shutdown();
	    taskWakeupMgr.stop();
            taskWakeupMgr.cancelAll();


	    // Remove persistent store- ask LogManager to clean
	    // itself up, then clean up the persistence path.
            if(destroyLogger.isLoggable(Level.FINEST)) {
	        destroyLogger.log(Level.FINEST,"Destroying transaction logs.");
	    }
	    MultiLogManagerAdmin logadmin =
	        (MultiLogManagerAdmin) logmgr.getAdmin();
	
	    logadmin.destroy();

	    if (persistent) {	    
                if(destroyLogger.isLoggable(Level.FINEST)) {
	            destroyLogger.log(Level.FINEST,"Destroying persistence directory.");
  	        }
	        try {
	            org.apache.river.system.FileSystem.destroy(
		        new File(persistenceDirectory), true);
	        } catch (IOException e) {
                    if(destroyLogger.isLoggable(Levels.HANDLED)) {
	                destroyLogger.log(Levels.HANDLED, 
		            "Problem destroying persistence directory", e);
		    }
    	        }
	    }
	    
            if(activationID != null) {
	        if(destroyLogger.isLoggable(Level.FINEST)) {
	            destroyLogger.log(Level.FINEST,"Calling Activatable.inactive.");
                }
	        try {
                    Activatable.inactive(activationID);
                } catch (RemoteException e) { // ignore
                    if(destroyLogger.isLoggable(Levels.HANDLED)) {
	                destroyLogger.log(Levels.HANDLED, 
		            "Problem inactivating service", e);
		    }
                } catch (ActivationException e) { // ignore
                    if(destroyLogger.isLoggable(Levels.HANDLED)) {
	                destroyLogger.log(Levels.HANDLED, 
		            "Problem inactivating service", e);
		    }
                }
            }

	    if (lifeCycle != null) {
	        if(destroyLogger.isLoggable(Level.FINEST)) {
	            destroyLogger.log(Level.FINEST,
		        "Unregistering with LifeCycle.");
                }
		lifeCycle.unregister(TxnManagerImpl.this);
	    }
	    
	    if (loginContext != null) {
	        try {
		    if (destroyLogger.isLoggable(Level.FINEST)) {
		        destroyLogger.log(Level.FINEST,
			    "Logging out");
		    }
		    loginContext.logout();
	        } catch (Exception e) {
		    if (destroyLogger.isLoggable(Levels.HANDLED)) {
		        destroyLogger.log(Levels.HANDLED,
			    "Exception while logging out", 
			    e);
		    }
	        }
	    }
            readyState.shutdown();
			
            if (operationsLogger.isLoggable(Level.FINER)) {
                operationsLogger.exiting(
		    DestroyThread.class.getName(), "run");
	    }
	}
    }

    /**
     * Returns the administration object for the
     * transaction manager.
     */
    public Object getAdmin() {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(
	        TxnManagerImpl.class.getName(), "getAdmin");
	}
        readyState.check();

        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(
	        TxnManagerImpl.class.getName(), "getAdmin", txnMgrAdminProxy);
	}
	return txnMgrAdminProxy;
    }

    // Methods required by JoinAdmin
    // Inherit java doc from super type
    public Entry[] getLookupAttributes() {
        readyState.check();

	return joinStateManager.getLookupAttributes();
    }

    // Inherit java doc from super type
    public void addLookupAttributes(Entry[] attrSets) {
        readyState.check();
        
	joinStateManager.addLookupAttributes(attrSets);
    }

    // Inherit java doc from super type
    public void modifyLookupAttributes(Entry[] attrSetTemplates, 
				       Entry[] attrSets) 
    {
        readyState.check();

	joinStateManager.modifyLookupAttributes(attrSetTemplates, attrSets);
    }
  
    // Inherit java doc from super type
    public String[] getLookupGroups() {
        readyState.check();

	return joinStateManager.getLookupGroups();
    }

    // Inherit java doc from super type
    public void addLookupGroups(String[] groups) {
        readyState.check();

	joinStateManager.addLookupGroups(groups);
    }

    // Inherit java doc from super type
    public void removeLookupGroups(String[] groups) {
        readyState.check();

	joinStateManager.removeLookupGroups(groups);
    }

    // Inherit java doc from super type
    public void setLookupGroups(String[] groups) {
        readyState.check();

	joinStateManager.setLookupGroups(groups);
    }

    // Inherit java doc from super type
    public LookupLocator[] getLookupLocators() {
        readyState.check();

	return joinStateManager.getLookupLocators();
    }

    // Inherit java doc from super type
    public void addLookupLocators(LookupLocator[] locators) 
        throws RemoteException
    {
        readyState.check();

	joinStateManager.addLookupLocators(locators);
    }

    // Inherit java doc from super type
    public void removeLookupLocators(LookupLocator[] locators)  
        throws RemoteException
    {
        readyState.check();

        joinStateManager.removeLookupLocators(locators);
    }

    // Inherit java doc from super type
    public void setLookupLocators(LookupLocator[] locators)  
        throws RemoteException
    {
        readyState.check();

	joinStateManager.setLookupLocators(locators);
    }


    //***********************************************************
    // Startup

    /**
     * Create the service owned attributes for an Mahalo server
     */
    private static Entry[] attributesFor() {
	final Entry info = new ServiceInfo("Transaction Manager", 
	    "Sun Microsystems, Inc.",  "Sun Microsystems, Inc.", 
	    org.apache.river.constants.VersionConstants.SERVER_VERSION,
	    "", "");
	
	final Entry type = 
	    new org.apache.river.lookup.entry.BasicServiceType("Transaction Manager");

	return new Entry[]{info, type};
    }

    public Object getProxy() {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(
	        TxnManagerImpl.class.getName(), "getProxy");
	}
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(
	        TxnManagerImpl.class.getName(), "getProxy", serverStub);
	}
	return serverStub;
    }
    
    /* inherit javadoc */
    public Object getServiceProxy() {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(
	        TxnManagerImpl.class.getName(), "getServiceProxy");
	}
        readyState.check();

        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(
	        TxnManagerImpl.class.getName(), "getServiceProxy", 
	        txnMgrProxy);
	}
        return txnMgrProxy;
    }
 
    /**
     * Log information about failing to initialize the service and rethrow the
     * appropriate exception.
     *
     * @param e the exception produced by the failure
     */
    protected void initFailed(Throwable e) throws Exception {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(
	        TxnManagerImpl.class.getName(), "initFailed");
	}
        if(initLogger.isLoggable(Level.SEVERE)) {
            initLogger.log(Level.SEVERE, "Mahalo failed to initialize", e);
        }
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(
	        TxnManagerImpl.class.getName(), "initFailed");
	}
        if (e instanceof PrivilegedActionException){
            e = e.getCause();
        }
	if (e instanceof Exception) {
            throw (Exception) e;
        } else if (e instanceof Error) {
            throw (Error) e;
        } else {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
    
    /*
     *
     */
    private void cleanup() {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(
		TxnManagerImpl.class.getName(), "cleanup");
	}
        //TODO - add custom logic	
        if (serverStub != null) { // implies that exporter != null
	    try {
                if(initLogger.isLoggable(Level.FINEST)) {
	            initLogger.log(Level.FINEST, "Unexporting service");		    
	        }
		exporter.unexport(true);
	    } catch (Throwable t) {
                if(initLogger.isLoggable(Levels.HANDLED)) {
	            initLogger.log(Levels.HANDLED, "Trouble unexporting service", t);
		}		    
	    }
	}
	
	if (settlerpool != null)  {
             if(initLogger.isLoggable(Level.FINEST)) {
	         initLogger.log(Level.FINEST, "Terminating settlerpool.");
             }
	     try {
                settlerpool.shutdown();
                try {
                    if (!settlerpool.awaitTermination(1, TimeUnit.MINUTES)){
                        if(initLogger.isLoggable(Level.FINEST)) {
                             initLogger.log(Level.FINEST, 
                                 "Termination of settlerpool timed out after 1 minute.");
                         }
                    };
                } catch (InterruptedException e){
                    Thread.currentThread().interrupt();// restore interrupt.
                    if(initLogger.isLoggable(Level.FINEST)) {
                         initLogger.log(Level.FINEST, 
                             "Thread interrupted while waiting for orderly settlerPool termination.");
                     }
                }
	        if (settlerWakeupMgr != null)  {
                    if(initLogger.isLoggable(Level.FINEST)) {
	                 initLogger.log(Level.FINEST, 
			     "Terminating settlerWakeupMgr.");
	            }
		    settlerWakeupMgr.stop();
	            settlerWakeupMgr.cancelAll();
		}
            } catch (Throwable t) {
                if(initLogger.isLoggable(Levels.HANDLED)) {
	            initLogger.log(Levels.HANDLED, 
		        "Trouble terminating settlerpool", t);
		}		    
            }
        }
	
	if (taskpool != null)  {
             if(initLogger.isLoggable(Level.FINEST)) {
	        initLogger.log(Level.FINEST,"Terminating taskpool.");
	     }
	     try {
                taskpool.shutdown();
	        if (taskWakeupMgr != null)  {
                    if(initLogger.isLoggable(Level.FINEST)) {
	                 initLogger.log(Level.FINEST,
			     "Terminating taskWakeupMgr.");
		    }
		    taskWakeupMgr.stop();
		    taskWakeupMgr.cancelAll();
		}
            } catch (Throwable t) {
                if(initLogger.isLoggable(Levels.HANDLED)) {
	            initLogger.log(Levels.HANDLED, 
		        "Trouble terminating taskpool", t);
		}		    
            }
        }
	
	if (settleThread != null) {
            if(initLogger.isLoggable(Level.FINEST)) {
	        initLogger.log(Level.FINEST, "Interrupting settleThread.");
            }
	    try {
                settleThread.interrupt();
            } catch (Throwable t) {
                if(initLogger.isLoggable(Levels.HANDLED)) {
	            initLogger.log(Levels.HANDLED, 
		        "Trouble terminating settleThread", t);	
		}	    
            }
	}
	
        if (expMgr != null)  {
            if(initLogger.isLoggable(Level.FINEST)) {
	        initLogger.log(Level.FINEST, 
		    "Terminating lease expiration manager.");
            }
	    expMgr.terminate();
	}

        if(initLogger.isLoggable(Level.FINEST)) {
	    initLogger.log(Level.FINEST,"Destroying JoinStateManager.");
        }
	try {
	    if (joinStateManager != null) {
		joinStateManager.stop();
	    }
        } catch (Exception t) {
            if(initLogger.isLoggable(Levels.HANDLED)) {
	        initLogger.log(Levels.HANDLED, 
	            "Problem destroying JoinStateManager", t);
            }
	}
	
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(
		TxnManagerImpl.class.getName(), "cleanup");
	}
    }
    
    //////////////////////////////////////////
    // ProxyTrust Method
    //////////////////////////////////////////
    public TrustVerifier getProxyVerifier( ) {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(TxnManagerImpl.class.getName(), 
	        "getProxyVerifier");
	}
        readyState.check();

        /* No verifier if the server isn't secure */
        if (!(txnMgrProxy instanceof RemoteMethodControl)) {
            throw new UnsupportedOperationException();
        } else {
            if (operationsLogger.isLoggable(Level.FINER)) {
                operationsLogger.exiting(TxnManagerImpl.class.getName(), 
   	            "getProxyVerifier");
	    }
            return new ProxyVerifier(serverStub, topUuid);
        }
    }


    /**
     * Utility method that check for valid resource
     */
    private static boolean ensureCurrent(LeasedResource resource) {
        return resource.getExpiration() > System.currentTimeMillis();
    }

    /*
     * Attempt to build "real" Uuid from 
     * topUuid.getLeastSignificantBits(), which contains
     * the variant field, and the transaction id, which
     * should be unique for this service. Between the two 
     * of these, the Uuid should be unique.
     */
    private Uuid createLeaseUuid(long txnId) {
        return UuidFactory.create(
	    topUuid.getLeastSignificantBits(),
	    txnId);
    }

    private void verifyLeaseUuid(Uuid uuid) throws UnknownLeaseException {
	/*
	 * Note: Lease Uuid contains
	 * - Most Sig => the least sig bits of topUuid
	 * - Least Sig => the txn id
	 */
	// Check to if this server granted the resource
	if (uuid.getMostSignificantBits() != 
	    topUuid.getLeastSignificantBits()) 
	{
	    throw new UnknownLeaseException();
	}

    }

    private Long getLeaseTid(Uuid uuid) {
	// Extract the txn id from the lower bits of the uuid
        return Long.valueOf(uuid.getLeastSignificantBits());
    }
    
    private static class FutureFactory implements RunnableFutureFactory{

        @Override
        public <T> RunnableFuture<T> newTaskFor(Runnable r, T value) {
            if (r instanceof RunnableFuture) return (RunnableFuture<T>) r;
            return new FutureTask<T>(r, value);
        }

        @Override
        public <T> RunnableFuture<T> newTaskFor(Callable<T> c) {
            if (c instanceof RunnableFuture) return (RunnableFuture<T>) c;
            return new FutureTask<T>(c);
        }
        
    }
}
