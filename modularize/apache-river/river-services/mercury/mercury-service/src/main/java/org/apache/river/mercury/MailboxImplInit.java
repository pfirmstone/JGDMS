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
import org.apache.river.landlord.FixedLeasePeriodPolicy;
import org.apache.river.landlord.LeasePeriodPolicy;
import org.apache.river.logging.Levels;
import org.apache.river.reliableLog.LogHandler;
import org.apache.river.reliableLog.ReliableLog;
import org.apache.river.thread.InterruptedStatusThread;
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
import javax.security.auth.login.LoginContext;
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
    ProxyPreparer recoveredListenerPreparer;
    ProxyPreparer locatorToJoinPreparer;
    LeasePeriodPolicy leasePolicy;
    String persistenceDirectory;
    ProxyPreparer recoveredLocatorToJoinPreparer;
    int logToSnapshotThreshold;
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
    Configuration config;
    AccessControlContext context;
    LoginContext loginContext;
    boolean persistent;

    MailboxImplInit(Configuration config, 
                    boolean persistent, 
                    ActivationID activationID, 
                    Entry[] baseLookupAttrs 
                    ) throws ConfigurationException, RemoteException, ActivationException
    {
        this.persistent = persistent;
        this.config = config;
        context = AccessController.getContext();
        // Get activation specific configuration items, if activated
        if (activationID != null) {
            ProxyPreparer activationSystemPreparer = (ProxyPreparer) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "activationSystemPreparer", ProxyPreparer.class, new BasicProxyPreparer());
            if (MailboxImpl.INIT_LOGGER.isLoggable(Level.CONFIG)) {
                MailboxImpl.INIT_LOGGER.log(Level.CONFIG, "activationSystemPreparer: {0}", activationSystemPreparer);
            }
            activationSystem = (ActivationSystem) activationSystemPreparer.prepareProxy(ActivationGroup.getSystem());
            if (MailboxImpl.INIT_LOGGER.isLoggable(Level.FINEST)) {
                MailboxImpl.INIT_LOGGER.log(Level.FINEST, "Prepared activation system is: {0}", activationSystem);
            }
            ProxyPreparer activationIdPreparer = (ProxyPreparer) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "activationIdPreparer", ProxyPreparer.class, new BasicProxyPreparer());
            if (MailboxImpl.INIT_LOGGER.isLoggable(Level.CONFIG)) {
                MailboxImpl.INIT_LOGGER.log(Level.CONFIG, "activationIdPreparer: {0}", activationIdPreparer);
            }
            activationID = (ActivationID) activationIdPreparer.prepareProxy(activationID);
            if (MailboxImpl.INIT_LOGGER.isLoggable(Level.FINEST)) {
                MailboxImpl.INIT_LOGGER.log(Level.FINEST, "Prepared activationID is: {0}", activationID);
            }
            activationPrepared = true;
            exporter = (Exporter) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "serverExporter", Exporter.class, new ActivationExporter(activationID, new BasicJeriExporter(TcpServerEndpoint.getInstance(0), new BasicILFactory(), false, true)), activationID);
            if (MailboxImpl.INIT_LOGGER.isLoggable(Level.CONFIG)) {
                MailboxImpl.INIT_LOGGER.log(Level.CONFIG, "Activatable service exporter is: {0}", exporter);
            }
            this.activationID = activationID;
        } else {
            //Get non-activatable configuration items
            exporter = (Exporter) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "serverExporter", Exporter.class, new BasicJeriExporter(TcpServerEndpoint.getInstance(0), new BasicILFactory(), false, true));
            if (MailboxImpl.INIT_LOGGER.isLoggable(Level.CONFIG)) {
                MailboxImpl.INIT_LOGGER.log(Level.CONFIG, "Non-activatable service exporter is: {0}", exporter);
            }
        }
        listenerPreparer = (ProxyPreparer) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "listenerPreparer", ProxyPreparer.class, new BasicProxyPreparer());
        if (MailboxImpl.INIT_LOGGER.isLoggable(Level.CONFIG)) {
            MailboxImpl.INIT_LOGGER.log(Level.CONFIG, "Listener preparer is: {0}", listenerPreparer);
        }
        /* Get the proxy preparers for the lookup locators to join */
        locatorToJoinPreparer = (ProxyPreparer) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "locatorToJoinPreparer", ProxyPreparer.class, new BasicProxyPreparer());
        if (MailboxImpl.INIT_LOGGER.isLoggable(Level.CONFIG)) {
            MailboxImpl.INIT_LOGGER.log(Level.CONFIG, "Locator preparer is: {0}", locatorToJoinPreparer);
        }
        // Create lease policy -- used by recovery logic, below
        leasePolicy = (LeasePeriodPolicy) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "leasePeriodPolicy", LeasePeriodPolicy.class, new FixedLeasePeriodPolicy(3 * TimeConstants.HOURS, 1 * TimeConstants.HOURS));
        if (MailboxImpl.INIT_LOGGER.isLoggable(Level.CONFIG)) {
            MailboxImpl.INIT_LOGGER.log(Level.CONFIG, "LeasePeriodPolicy is: {0}", leasePolicy);
        }
        // Note: referenced by recovery logic in rebuildTransientState()
        if (persistent) {
            persistenceDirectory = (String) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "persistenceDirectory", String.class);
            if (MailboxImpl.INIT_LOGGER.isLoggable(Level.CONFIG)) {
                MailboxImpl.INIT_LOGGER.log(Level.CONFIG, "Persistence directory is: {0}", persistenceDirectory);
            }
            // Note: referenced by recovery logic in rebuildTransientState()
            recoveredListenerPreparer = (ProxyPreparer) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "recoveredListenerPreparer", ProxyPreparer.class, new BasicProxyPreparer());
            if (MailboxImpl.INIT_LOGGER.isLoggable(Level.CONFIG)) {
                MailboxImpl.INIT_LOGGER.log(Level.CONFIG, "Recovered listener preparer is: {0}", recoveredListenerPreparer);
            }
            // Note: referenced by recovery logic, below
            recoveredLocatorToJoinPreparer = (ProxyPreparer) Config.getNonNullEntry(config, MailboxImpl.MERCURY, "recoveredLocatorToJoinPreparer", ProxyPreparer.class, new BasicProxyPreparer());
            if (MailboxImpl.INIT_LOGGER.isLoggable(Level.CONFIG)) {
                MailboxImpl.INIT_LOGGER.log(Level.CONFIG, "Recovered locator preparer is: {0}", recoveredLocatorToJoinPreparer);
            }
            logToSnapshotThreshold = Config.getIntEntry(config, MailboxImpl.MERCURY, "logToSnapshotThreshold", 50, 0, Integer.MAX_VALUE);
            
        }
        
        maxUnexportDelay = Config.getLongEntry(config, MailboxImpl.MERCURY, "maxUnexportDelay", 2 * TimeConstants.MINUTES, 0, Long.MAX_VALUE);
        unexportRetryDelay = Config.getLongEntry(config, MailboxImpl.MERCURY, "unexportRetryDelay", TimeConstants.SECONDS, 1, Long.MAX_VALUE);
    }
    
}
