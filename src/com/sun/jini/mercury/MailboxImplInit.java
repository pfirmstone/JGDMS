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

package com.sun.jini.mercury;

import com.sun.jini.config.Config;
import com.sun.jini.constants.TimeConstants;
import com.sun.jini.landlord.FixedLeasePeriodPolicy;
import com.sun.jini.landlord.LeasePeriodPolicy;
import com.sun.jini.logging.Levels;
import com.sun.jini.reliableLog.LogHandler;
import com.sun.jini.reliableLog.ReliableLog;
import com.sun.jini.thread.InterruptedStatusThread;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationSystem;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import net.jini.activation.ActivationExporter;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.discovery.DiscoveryManagement;
import net.jini.export.Exporter;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;

/**
 *
 * @author peter
 */
class MailboxImplInit {
    ActivationID activationID;
    ActivationSystem activationSystem;
    boolean activationPrepared;
    Exporter exporter;
    ProxyPreparer listenerPreparer;
    ProxyPreparer locatorToJoinPreparer;
    LeasePeriodPolicy leasePolicy;
    String persistenceDirectory;
    ProxyPreparer recoveredLocatorToJoinPreparer;
    int logToSnapshotThreshold;
    ReliableLog log;
    Uuid serviceID;
    String[] lookupGroups;
    LookupLocator[] lookupLocators;
    Entry[] lookupAttrs;
    long maxUnexportDelay;
    long unexportRetryDelay;
    DiscoveryManagement lookupDiscMgr;
    TreeMap<ServiceRegistration, ServiceRegistration> regByExpiration = new TreeMap<ServiceRegistration, ServiceRegistration>();
    HashMap<Uuid, ServiceRegistration> regByID = new HashMap<Uuid, ServiceRegistration>();
    Map<Uuid, MailboxImpl.NotifyTask> activeReg = new HashMap<Uuid, MailboxImpl.NotifyTask>();
    /** <code>EventLogIterator</code> generator */
    EventLogFactory eventLogFactory = new EventLogFactory();
    List<Uuid> pendingReg = new ArrayList<Uuid>();
    Thread snapshotter;
    Thread notifier;
    Thread expirer;
    Configuration config;
    AccessControlContext context;

    MailboxImplInit(Configuration config, 
                    boolean persistent, 
                    ActivationID activationID, 
                    Entry[] baseLookupAttrs, 
                    LogHandler localLogHandler,
                    Thread snapshotter,
                    Thread notifier,
                    Thread expirer)
            throws ConfigurationException, RemoteException, ActivationException, IOException
    {
        this.notifier = notifier;
        this.expirer = expirer;
        this.config = config;
        context = AccessController.getContext();
        // Get activation specific configuration items, if activated
        if (activationID != null) {
            ProxyPreparer activationSystemPreparer = (ProxyPreparer) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "activationSystemPreparer", ProxyPreparer.class, new BasicProxyPreparer());
            if (MailboxImpl.initLogger.isLoggable(Level.CONFIG)) {
                MailboxImpl.initLogger.log(Level.CONFIG, "activationSystemPreparer: {0}", activationSystemPreparer);
            }
            activationSystem = (ActivationSystem) activationSystemPreparer.prepareProxy(ActivationGroup.getSystem());
            if (MailboxImpl.initLogger.isLoggable(Level.FINEST)) {
                MailboxImpl.initLogger.log(Level.FINEST, "Prepared activation system is: {0}", activationSystem);
            }
            ProxyPreparer activationIdPreparer = (ProxyPreparer) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "activationIdPreparer", ProxyPreparer.class, new BasicProxyPreparer());
            if (MailboxImpl.initLogger.isLoggable(Level.CONFIG)) {
                MailboxImpl.initLogger.log(Level.CONFIG, "activationIdPreparer: {0}", activationIdPreparer);
            }
            activationID = (ActivationID) activationIdPreparer.prepareProxy(activationID);
            if (MailboxImpl.initLogger.isLoggable(Level.FINEST)) {
                MailboxImpl.initLogger.log(Level.FINEST, "Prepared activationID is: {0}", activationID);
            }
            activationPrepared = true;
            exporter = (Exporter) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "serverExporter", Exporter.class, new ActivationExporter(activationID, new BasicJeriExporter(TcpServerEndpoint.getInstance(0), new BasicILFactory(), false, true)), activationID);
            if (MailboxImpl.initLogger.isLoggable(Level.CONFIG)) {
                MailboxImpl.initLogger.log(Level.CONFIG, "Activatable service exporter is: {0}", exporter);
            }
            this.activationID = activationID;
        } else {
            //Get non-activatable configuration items
            exporter = (Exporter) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "serverExporter", Exporter.class, new BasicJeriExporter(TcpServerEndpoint.getInstance(0), new BasicILFactory(), false, true));
            if (MailboxImpl.initLogger.isLoggable(Level.CONFIG)) {
                MailboxImpl.initLogger.log(Level.CONFIG, "Non-activatable service exporter is: {0}", exporter);
            }
        }
        listenerPreparer = (ProxyPreparer) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "listenerPreparer", ProxyPreparer.class, new BasicProxyPreparer());
        if (MailboxImpl.initLogger.isLoggable(Level.CONFIG)) {
            MailboxImpl.initLogger.log(Level.CONFIG, "Listener preparer is: {0}", listenerPreparer);
        }
        /* Get the proxy preparers for the lookup locators to join */
        locatorToJoinPreparer = (ProxyPreparer) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "locatorToJoinPreparer", ProxyPreparer.class, new BasicProxyPreparer());
        if (MailboxImpl.initLogger.isLoggable(Level.CONFIG)) {
            MailboxImpl.initLogger.log(Level.CONFIG, "Locator preparer is: {0}", locatorToJoinPreparer);
        }
        // Create lease policy -- used by recovery logic, below
        leasePolicy = (LeasePeriodPolicy) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "leasePeriodPolicy", LeasePeriodPolicy.class, new FixedLeasePeriodPolicy(3 * TimeConstants.HOURS, 1 * TimeConstants.HOURS));
        if (MailboxImpl.initLogger.isLoggable(Level.CONFIG)) {
            MailboxImpl.initLogger.log(Level.CONFIG, "LeasePeriodPolicy is: {0}", leasePolicy);
        }
        // Note: referenced by recovery logic in rebuildTransientState()
        ProxyPreparer recoveredListenerPreparer = null;
        if (persistent) {
            persistenceDirectory = (String) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "persistenceDirectory", String.class);
            if (MailboxImpl.initLogger.isLoggable(Level.CONFIG)) {
                MailboxImpl.initLogger.log(Level.CONFIG, "Persistence directory is: {0}", persistenceDirectory);
            }
            // Note: referenced by recovery logic in rebuildTransientState()
            recoveredListenerPreparer = (ProxyPreparer) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "recoveredListenerPreparer", ProxyPreparer.class, new BasicProxyPreparer());
            if (MailboxImpl.initLogger.isLoggable(Level.CONFIG)) {
                MailboxImpl.initLogger.log(Level.CONFIG, "Recovered listener preparer is: {0}", recoveredListenerPreparer);
            }
            // Note: referenced by recovery logic, below
            recoveredLocatorToJoinPreparer = (ProxyPreparer) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "recoveredLocatorToJoinPreparer", ProxyPreparer.class, new BasicProxyPreparer());
            if (MailboxImpl.initLogger.isLoggable(Level.CONFIG)) {
                MailboxImpl.initLogger.log(Level.CONFIG, "Recovered locator preparer is: {0}", recoveredLocatorToJoinPreparer);
            }
            logToSnapshotThreshold = Config.getIntEntry(config, MailboxImpl.MERCURY, "logToSnapshotThreshold", 50, 0, Integer.MAX_VALUE);
            log = new ReliableLog(persistenceDirectory, localLogHandler);
            if (MailboxImpl.initLogger.isLoggable(Level.FINEST)) {
                MailboxImpl.initLogger.log(Level.FINEST, "Recovering persistent state");
            }
            log.recover();
        }
        if (serviceID == null) {
            // First time up, get initial values
            if (MailboxImpl.initLogger.isLoggable(Level.FINEST)) {
                MailboxImpl.initLogger.log(Level.FINEST, "Getting initial values.");
            }
            serviceID = UuidFactory.generate();
            if (MailboxImpl.initLogger.isLoggable(Level.FINEST)) {
                MailboxImpl.initLogger.log(Level.FINEST, "ServiceID: {0}", serviceID);
            }
            // Can be null for ALL_GROUPS
            lookupGroups = (String[]) config.getEntry(MailboxImpl.MERCURY, "initialLookupGroups", String[].class, new String[]{""}); //default to public group
            //default to public group
            if (MailboxImpl.initLogger.isLoggable(Level.CONFIG)) {
                MailboxImpl.initLogger.log(Level.CONFIG, "Initial groups:");
                MailboxImpl.dumpGroups(lookupGroups, MailboxImpl.initLogger, Level.CONFIG);
            }
            /*
             * Note: Configuration provided locators are assumed to be
             * prepared already.
             */
            lookupLocators = (LookupLocator[]) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "initialLookupLocators", LookupLocator[].class, new LookupLocator[0]);
            if (MailboxImpl.initLogger.isLoggable(Level.CONFIG)) {
                MailboxImpl.initLogger.log(Level.CONFIG, "Initial locators:");
                MailboxImpl.dumpLocators(lookupLocators, MailboxImpl.initLogger, Level.CONFIG);
            }
            final Entry[] initialAttrs = (Entry[]) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "initialLookupAttributes", Entry[].class, new Entry[0]);
            if (MailboxImpl.initLogger.isLoggable(Level.CONFIG)) {
                MailboxImpl.initLogger.log(Level.CONFIG, "Initial lookup attributes:");
                MailboxImpl.dumpAttrs(initialAttrs, MailboxImpl.initLogger, Level.CONFIG);
            }
            if (initialAttrs.length == 0) {
                lookupAttrs = baseLookupAttrs;
            } else {
                lookupAttrs = new Entry[initialAttrs.length + baseLookupAttrs.length];
                int i = 0;
                for (int j = 0; j < baseLookupAttrs.length; j++, i++) {
                    lookupAttrs[i] = baseLookupAttrs[j];
                }
                for (int j = 0; j < initialAttrs.length; j++, i++) {
                    lookupAttrs[i] = initialAttrs[j];
                }
            }
            if (MailboxImpl.initLogger.isLoggable(Level.FINEST)) {
                MailboxImpl.initLogger.log(Level.FINEST, "Combined lookup attributes:");
                MailboxImpl.dumpAttrs(lookupAttrs, MailboxImpl.initLogger, Level.FINEST);
            }
        } else {
            // recovered logic
            if (MailboxImpl.initLogger.isLoggable(Level.FINEST)) {
                MailboxImpl.initLogger.log(Level.FINEST, "Preparing recovered locators:");
                MailboxImpl.dumpLocators(lookupLocators, MailboxImpl.initLogger, Level.FINEST);
            }
            MailboxImpl.prepareExistingLocators(recoveredLocatorToJoinPreparer, lookupLocators);
            //TODO - Add recovered state debug: groups, locators, etc.
        }
        if (persistent) {
            // Take snapshot of current state.
            if (MailboxImpl.initLogger.isLoggable(Level.FINEST)) {
                MailboxImpl.initLogger.log(Level.FINEST, "Taking snapshot.");
            }
            log.snapshot();
            // Reconstruct any transient state, if necessary.
            //rebuildTransientState(recoveredListenerPreparer);
            if (MailboxImpl.operationsLogger.isLoggable(Level.FINER)) {
                MailboxImpl.operationsLogger.entering(MailboxImpl.mailboxSourceClass, "rebuildTransientState", recoveredListenerPreparer);
            }
            this.snapshotter = snapshotter;
            // Reconstruct regByExpiration and pendingReg data structures,
            // if necessary.
            if (!regByID.isEmpty()) {
                if (MailboxImpl.recoveryLogger.isLoggable(Level.FINEST)) {
                    MailboxImpl.recoveryLogger.log(Level.FINEST, "Rebuilding transient state ...");
                }
                Collection<ServiceRegistration> regs = regByID.values();
                Iterator<ServiceRegistration> iter = regs.iterator();
                ServiceRegistration reg = null;
                Uuid uuid = null;
                EventLogIterator eli = null;
                while (iter.hasNext()) {
                    reg = iter.next(); // get Reg
                    // get Reg
                    uuid = reg.getCookie(); // get its Uuid
                    // get its Uuid
                    if (MailboxImpl.recoveryLogger.isLoggable(Level.FINEST)) {
                        MailboxImpl.recoveryLogger.log(Level.FINEST, "Checking reg : {0}", reg);
                    }
                    // Check if registration is still current
                    if (MailboxImpl.ensureCurrent(reg)) {
                        if (MailboxImpl.recoveryLogger.isLoggable(Level.FINEST)) {
                            MailboxImpl.recoveryLogger.log(Level.FINEST, "Restoring reg transient state ...");
                        }
                        try {
                            reg.restoreTransientState(recoveredListenerPreparer);
                        } catch (Exception e) {
                            if (MailboxImpl.recoveryLogger.isLoggable(Levels.HANDLED)) {
                                MailboxImpl.recoveryLogger.log(Levels.HANDLED, "Trouble restoring reg transient state", e);
                            }
                            try {
                                reg.setEventTarget(null);
                            } catch (IOException ioe) {
                                throw new AssertionError("Setting a null target threw an exception: " + ioe);
                            }
                        }
                        if (MailboxImpl.recoveryLogger.isLoggable(Level.FINEST)) {
                            MailboxImpl.recoveryLogger.log(Level.FINEST, "Reinitializing iterator ...");
                        }
                        // regenerate an EventLogIterator for this Reg
                        // Note that event state is maintained separately
                        // through the event log mechanism.
                        eli = persistent ? eventLogFactory.iterator(uuid, MailboxImpl.getEventLogPath(persistenceDirectory, uuid)) : eventLogFactory.iterator(uuid);
                        reg.setIterator(eli);
                        if (MailboxImpl.recoveryLogger.isLoggable(Level.FINEST)) {
                            MailboxImpl.recoveryLogger.log(Level.FINEST, "Adding registration to expiration watch list");
                        }
                        // Put Reg into time sorted collection
                        regByExpiration.put(reg, reg);
                        // Check if registration needs to be added to the
                        // pending list. Note, we could have processed
                        // an "enabled" log record during recovery, so
                        // only add it if it's not already there.
                        // We don't need to check activeReg since the
                        // the notifier hasn't kicked in yet. Don't call
                        // enableRegistration() since it clears the "unknown
                        // events" list which we want to maintain.
                        if (reg.hasEventTarget() && !pendingReg.contains(uuid)) {
                            if (MailboxImpl.recoveryLogger.isLoggable(Level.FINEST)) {
                                MailboxImpl.recoveryLogger.log(Level.FINEST, "Adding registration to pending task list");
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
                        if (MailboxImpl.recoveryLogger.isLoggable(Level.FINEST)) {
                            MailboxImpl.recoveryLogger.log(Level.FINEST, "Removing expired registration: ");
                        }
                        iter.remove();
                        //	            removeRegistration(uuid, reg, true);
                        /**/
                        // Remove Reg from data structures, if present.
                        // If initializing, don't remove directly from regByID since we
                        // currently traversing it via an iterator. Assumption is that
                        // the caller has already removed it via the iterator.
                        // See Bug 4507320.
                        //                    if (!initializing) {
                        //                        regByID.remove(uuid);
                        //                    }
                        regByExpiration.remove(reg);
                        boolean exists = pendingReg.remove(uuid);
                        MailboxImpl.NotifyTask task = activeReg.remove(uuid);
                        if (task != null) {
                            // cancel active task, if any
                            task.cancel();
                            if (MailboxImpl.deliveryLogger.isLoggable(Level.FINEST)) {
                                MailboxImpl.deliveryLogger.log(Level.FINEST, "Cancelling active notification task for {0}", uuid);
                            }
                        }
                        // Delete any associated resources
                        try {
                            if (MailboxImpl.persistenceLogger.isLoggable(Level.FINEST)) {
                                MailboxImpl.persistenceLogger.log(Level.FINEST, "Removing logs for {0}", reg);
                            }
                            EventLogIterator it = reg.iterator();
                            if (it != null) {
                                it.destroy();
                            }
                        } catch (IOException ioe) {
                            if (MailboxImpl.persistenceLogger.isLoggable(Levels.HANDLED)) {
                                MailboxImpl.persistenceLogger.log(Levels.HANDLED, "Trouble removing logs", ioe);
                            }
                            // Did the best we could ... continue.
                        }
                        // Sanity check
                        if (exists && task != null) {
                            if (MailboxImpl.leaseLogger.isLoggable(Level.SEVERE)) {
                                MailboxImpl.leaseLogger.log(Level.SEVERE, "ERROR: Registration was found " + "on both the active and pending lists");
                            }
                            // TODO (FCS)- throw assertion error
                        }
                        if (MailboxImpl.operationsLogger.isLoggable(Level.FINER)) {
                            MailboxImpl.operationsLogger.exiting(MailboxImpl.mailboxSourceClass, "removeRegistration");
                        }
                        /**/
                    }
                }
            }
            if (MailboxImpl.operationsLogger.isLoggable(Level.FINER)) {
                MailboxImpl.operationsLogger.exiting(MailboxImpl.mailboxSourceClass, "rebuildTransientState");
            }
        }
        maxUnexportDelay = Config.getLongEntry(config, MailboxImpl.MERCURY, "maxUnexportDelay", 2 * TimeConstants.MINUTES, 0, Long.MAX_VALUE);
        unexportRetryDelay = Config.getLongEntry(config, MailboxImpl.MERCURY, "unexportRetryDelay", TimeConstants.SECONDS, 1, Long.MAX_VALUE);
    }
    
}
