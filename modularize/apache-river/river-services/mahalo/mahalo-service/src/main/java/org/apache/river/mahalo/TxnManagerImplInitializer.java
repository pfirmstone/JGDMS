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

import org.apache.river.config.Config;
import org.apache.river.constants.TimeConstants;
import org.apache.river.landlord.FixedLeasePeriodPolicy;
import org.apache.river.landlord.LeasePeriodPolicy;
import org.apache.river.thread.InterruptedStatusThread;
import org.apache.river.thread.WakeupManager;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationSystem;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.jini.activation.ActivationExporter;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.core.transaction.server.ServerTransaction;
import net.jini.export.Exporter;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;
import org.apache.river.thread.NamedThreadFactory;

/**
 *
 * @author peter
 */
class TxnManagerImplInitializer {
    /* Default tuning parameters for thread pool */
    int cores = Runtime.getRuntime().availableProcessors();
    int settlerthreads = 15*cores;
    long settlertimeout = 1000 * 15;
    int taskthreads = 5*cores;
    long tasktimeout = 1000 * 15;
    ConcurrentMap<Long, TxnManagerTransaction> txns =
            new ConcurrentHashMap<Long, TxnManagerTransaction>();
    /* Retrieve values from properties.          */
    ActivationSystem activationSystem = null;
    boolean activationPrepared = false;
    Exporter exporter = null;
    ProxyPreparer participantPreparer = null;
    LeasePeriodPolicy txnLeasePeriodPolicy = null;
    String persistenceDirectory = null;
    JoinStateManager joinStateManager = null;
    ExecutorService settlerpool = null;
    WakeupManager settlerWakeupMgr = null;
    ExecutorService taskpool = null;
    WakeupManager taskWakeupMgr = null;
    Uuid topUuid = null;
    AccessControlContext context = null;
    InterruptedStatusThread settleThread =null;

    TxnManagerImplInitializer(Configuration config, boolean persistent, ActivationID activationID, InterruptedStatusThread settleThread) throws ConfigurationException, RemoteException, ActivationException, IOException {
        this.settleThread = settleThread;
        context = AccessController.getContext();
        if (TxnManagerImpl.operationsLogger.isLoggable(Level.FINER)) {
            TxnManagerImpl.operationsLogger.entering(TxnManagerImpl.class.getName(), "doInit", config);
        }
        // Get activatable settings, if activated
        if (activationID != null) {
            ProxyPreparer activationSystemPreparer = (ProxyPreparer) 
                    Config.getNonNullEntry(config, TxnManager.MAHALO,
                    "activationSystemPreparer", ProxyPreparer.class, 
                    new BasicProxyPreparer());
            if (TxnManagerImpl.initLogger.isLoggable(Level.CONFIG)) {
                TxnManagerImpl.initLogger.log(Level.CONFIG, 
                        "activationSystemPreparer: {0}", activationSystemPreparer);
            }
            activationSystem = (ActivationSystem) 
                    activationSystemPreparer.prepareProxy(ActivationGroup.getSystem());
            if (TxnManagerImpl.initLogger.isLoggable(Level.CONFIG)) {
                TxnManagerImpl.initLogger.log(Level.CONFIG, 
                        "Prepared activation system is: {0}", activationSystem);
            }
            ProxyPreparer activationIdPreparer = (ProxyPreparer) 
                    Config.getNonNullEntry(config, TxnManager.MAHALO, 
                    "activationIdPreparer", ProxyPreparer.class, new BasicProxyPreparer());
            if (TxnManagerImpl.initLogger.isLoggable(Level.CONFIG)) {
                TxnManagerImpl.initLogger.log(Level.CONFIG, 
                        "activationIdPreparer: {0}", activationIdPreparer);
            }
            activationID = (ActivationID) activationIdPreparer.prepareProxy(activationID);
            if (TxnManagerImpl.initLogger.isLoggable(Level.CONFIG)) {
                TxnManagerImpl.initLogger.log(Level.CONFIG, 
                        "Prepared activationID is: {0}", activationID);
            }
            activationPrepared = true;
            exporter = (Exporter) Config.getNonNullEntry(config, 
                    TxnManager.MAHALO, 
                    "serverExporter", 
                    Exporter.class, 
                    new ActivationExporter(activationID, 
                        new BasicJeriExporter(TcpServerEndpoint.getInstance(0), 
                            new BasicILFactory(), 
                            false, 
                            true)
                        ), 
                    activationID);
            if (TxnManagerImpl.initLogger.isLoggable(Level.CONFIG)) {
                TxnManagerImpl.initLogger.log(Level.CONFIG, "Activatable service exporter is: {0}", exporter);
            }
        } else {
            exporter = (Exporter) Config.getNonNullEntry(config, TxnManager.MAHALO, "serverExporter", Exporter.class, new BasicJeriExporter(TcpServerEndpoint.getInstance(0), new BasicILFactory(), false, true));
            if (TxnManagerImpl.initLogger.isLoggable(Level.CONFIG)) {
                TxnManagerImpl.initLogger.log(Level.CONFIG, "Non-activatable service exporter is: {0}", exporter);
            }
        }
        ProxyPreparer recoveredParticipantPreparer = (ProxyPreparer) Config.getNonNullEntry(config, TxnManager.MAHALO, "recoveredParticipantPreparer", ProxyPreparer.class, new BasicProxyPreparer());
        if (TxnManagerImpl.initLogger.isLoggable(Level.CONFIG)) {
            TxnManagerImpl.initLogger.log(Level.CONFIG, "Recovered participant preparer is: {0}", recoveredParticipantPreparer);
        }
        participantPreparer = (ProxyPreparer) Config.getNonNullEntry(config, TxnManager.MAHALO, "participantPreparer", ProxyPreparer.class, new BasicProxyPreparer());
        if (TxnManagerImpl.initLogger.isLoggable(Level.CONFIG)) {
            TxnManagerImpl.initLogger.log(Level.CONFIG, "Participant preparer is: {0}", participantPreparer);
        }
        // Create lease policy -- used by recovery logic, below??
        txnLeasePeriodPolicy = (LeasePeriodPolicy) Config.getNonNullEntry(config, TxnManager.MAHALO, "leasePeriodPolicy", LeasePeriodPolicy.class, new FixedLeasePeriodPolicy(3 * TimeConstants.HOURS, 1 * TimeConstants.HOURS));
        if (TxnManagerImpl.initLogger.isLoggable(Level.CONFIG)) {
            TxnManagerImpl.initLogger.log(Level.CONFIG, "leasePeriodPolicy is: {0}", txnLeasePeriodPolicy);
        }
        if (persistent) {
            persistenceDirectory = (String) Config.getNonNullEntry(config, TxnManager.MAHALO, "persistenceDirectory", String.class);
            if (TxnManagerImpl.initLogger.isLoggable(Level.CONFIG)) {
                TxnManagerImpl.initLogger.log(Level.CONFIG, "Persistence directory is: {0}", persistenceDirectory);
            }
        } else {
            // just for insurance
            persistenceDirectory = null;
        }
        if (TxnManagerImpl.initLogger.isLoggable(Level.FINEST)) {
            TxnManagerImpl.initLogger.log(Level.FINEST, "Creating JoinStateManager");
        }
        // Note: null persistenceDirectory means no persistence
        joinStateManager = new JoinStateManager(persistenceDirectory);
        if (TxnManagerImpl.initLogger.isLoggable(Level.FINEST)) {
            TxnManagerImpl.initLogger.log(Level.FINEST, "Recovering join state ...");
        }
        joinStateManager.recover();
        // ServiceUuid will be null first time up.
        if (joinStateManager.getServiceUuid() == null) {
            if (TxnManagerImpl.initLogger.isLoggable(Level.FINEST)) {
                TxnManagerImpl.initLogger.log(Level.FINEST, "Generating service Uuid");
            }
            topUuid = UuidFactory.generate();
            // Actual snapshot deferred until JSM is started, below
            joinStateManager.setServiceUuid(topUuid);
        } else {
            // get recovered value for serviceUuid
            if (TxnManagerImpl.initLogger.isLoggable(Level.FINEST)) {
                TxnManagerImpl.initLogger.log(Level.FINEST, "Recovering service Uuid");
            }
            topUuid = joinStateManager.getServiceUuid();
        }
        if (TxnManagerImpl.initLogger.isLoggable(Level.FINEST)) {
            TxnManagerImpl.initLogger.log(Level.FINEST, "Uuid is: {0}", topUuid);
        }
        if (persistent) {
            // Check persistence path for validity, and create if necessary
            org.apache.river.system.FileSystem.ensureDir(persistenceDirectory);
        }
        if (TxnManagerImpl.initLogger.isLoggable(Level.FINEST)) {
            TxnManagerImpl.initLogger.log(Level.FINEST, "Setting up data structures");
        }
        // Used by log recovery logic
        settlerWakeupMgr = new WakeupManager(new WakeupManager.ThreadDesc(null, true));
        taskWakeupMgr = new WakeupManager(new WakeupManager.ThreadDesc(null, true));
        settlerpool = Config.getNonNullEntry(
                config,
                TxnManager.MAHALO, 
                "settlerPool", 
                ExecutorService.class,
                new ThreadPoolExecutor(
                    settlerthreads,
                    settlerthreads, /* Ignored */
                    settlertimeout, 
                    TimeUnit.MILLISECONDS, 
                    new LinkedBlockingQueue<Runnable>(), /* Unbounded Queue */
                    new NamedThreadFactory("TxnMgr settlerPool", false)
                )
        );
        taskpool = Config.getNonNullEntry(
                config, 
                TxnManager.MAHALO,
                "taskPool",
                ExecutorService.class, 
                new ThreadPoolExecutor(
                        taskthreads,
                        taskthreads, /* Ignored */
                        tasktimeout,
                        TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<Runnable>(), /* Unbounded Queue */
                        new NamedThreadFactory("TxnMgr taskPool", false)
                )
        );
        if (TxnManagerImpl.initLogger.isLoggable(Level.FINEST)) {
            TxnManagerImpl.initLogger.log(Level.FINEST, "Recovering state");
        }
        // Restore transient state of recovered transactions
        Iterator iter = txns.values().iterator();
        TxnManagerTransaction txn;
        while (iter.hasNext()) {
            txn = (TxnManagerTransaction) iter.next();
            if (TxnManagerImpl.initLogger.isLoggable(Level.FINEST)) {
                TxnManagerImpl.initLogger.log(Level.FINEST, "Restoring transient state for txn id: {0}", Long.valueOf(((ServerTransaction) txn.getTransaction()).id));
            }
            try {
                txn.restoreTransientState(recoveredParticipantPreparer);
            } catch (RemoteException re) {
                if (TxnManagerImpl.persistenceLogger.isLoggable(Level.WARNING)) {
                    TxnManagerImpl.persistenceLogger.log(Level.WARNING, "Cannot restore the TransactionParticipant", re);
                }
                //TODO - what should happen when participant preparation fails?
            }
        }
    }
    
}
