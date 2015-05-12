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

package org.apache.river.norm;

import org.apache.river.config.Config;
import org.apache.river.landlord.FixedLeasePeriodPolicy;
import org.apache.river.landlord.LeasePeriodPolicy;
import org.apache.river.norm.event.EventTypeGenerator;
import org.apache.river.start.LifeCycle;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.LinkedList;
import java.util.List;
import javax.security.auth.login.LoginContext;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.NoSuchEntryException;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.lease.LeaseRenewalManager;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;

/**
 * NormServerInitializer creates the internal state of NormServerBaseImpl allowing
 * many fields to be final after construction.  This simplifies auditing of
 * synchronization by reducing scope to mutable fields and references 
 * to mutable objects.
 */
class NormServerInitializer {
    boolean persistent;
    LifeCycle lifeCycle;
    LoginContext loginContext;
    String persistenceDirectory;
    List renewedList = new LinkedList();
    float snapshotWt;
    int logToSnapshotThresh;
    ProxyPreparer leasePreparer;
    ProxyPreparer listenerPreparer;
    ProxyPreparer locatorPreparer;
    ProxyPreparer recoveredLeasePreparer;
    ProxyPreparer recoveredListenerPreparer;
    ProxyPreparer recoveredLocatorPreparer;
    LeasePeriodPolicy setLeasePolicy;
    boolean isolateSets;
    LeaseRenewalManager lrm;
    Exporter exporter;
    LeaseExpirationMgr expMgr;
    EventTypeGenerator generator;
    LRMEventListener lrmEventListener;
    NormServerBaseImpl.RenewLogThread renewLogger;
    AccessControlContext context;
    Configuration config;

    /**
     * Initializer object for NormServer implementations.  Can be overridden by
     * subclasses of NormServerBaseImpl
     * 
     * @param persistent true if implementation is persistent
     * @param lifeCycle object to notify when NormServer is destroyed.
     */
    NormServerInitializer(boolean persistent, LifeCycle lifeCycle) {
        this.persistent = persistent;
        this.lifeCycle = lifeCycle;
    }

    /**
     * Common construction for activatable and non-activatable cases, run
     * under the proper Subject.
     */
    void initAsSubject(Configuration config) throws Exception {
        this.config = config;
        /* Get configuration entries first */
        if (persistent) {
            persistenceDirectory = (String) Config.getNonNullEntry(config, NormServerBaseImpl.NORM, "persistenceDirectory", String.class);
            snapshotWt = Config.getFloatEntry(config, NormServerBaseImpl.NORM, "persistenceSnapshotWeight", 10, 0, Float.MAX_VALUE);
            logToSnapshotThresh = Config.getIntEntry(config, NormServerBaseImpl.NORM, "persistenceSnapshotThreshold", 200, 0, Integer.MAX_VALUE);
        }
        leasePreparer = (ProxyPreparer) Config.getNonNullEntry(config, NormServerBaseImpl.NORM, "leasePreparer", ProxyPreparer.class, new BasicProxyPreparer());
        listenerPreparer = (ProxyPreparer) Config.getNonNullEntry(config, NormServerBaseImpl.NORM, "listenerPreparer", ProxyPreparer.class, new BasicProxyPreparer());
        locatorPreparer = (ProxyPreparer) Config.getNonNullEntry(config, NormServerBaseImpl.NORM, "locatorPreparer", ProxyPreparer.class, new BasicProxyPreparer());
        if (persistent) {
            recoveredLeasePreparer = (ProxyPreparer) Config.getNonNullEntry(config, NormServerBaseImpl.NORM, "recoveredLeasePreparer", ProxyPreparer.class, new BasicProxyPreparer());
            recoveredListenerPreparer = (ProxyPreparer) Config.getNonNullEntry(config, NormServerBaseImpl.NORM, "recoveredListenerPreparer", ProxyPreparer.class, new BasicProxyPreparer());
            recoveredLocatorPreparer = (ProxyPreparer) Config.getNonNullEntry(config, NormServerBaseImpl.NORM, "recoveredLocatorPreparer", ProxyPreparer.class, new BasicProxyPreparer());
        }
        setLeasePolicy = (LeasePeriodPolicy) Config.getNonNullEntry(config, NormServerBaseImpl.NORM, "leasePolicy", LeasePeriodPolicy.class, new FixedLeasePeriodPolicy(2 * 60 * 60 * 1000, 60 * 60 * 1000));
        isolateSets = ((Boolean) config.getEntry(NormServerBaseImpl.NORM, "isolateSets", boolean.class, Boolean.FALSE)).booleanValue();
        try {
            lrm = (LeaseRenewalManager) Config.getNonNullEntry(config, NormServerBaseImpl.NORM, "leaseManager", LeaseRenewalManager.class);
        } catch (NoSuchEntryException e) {
            lrm = new LeaseRenewalManager(config);
        }
        exporter = getExporter(config);
        // We use some of these during the recovery process
        expMgr = new LeaseExpirationMgr();
        generator = new EventTypeGenerator();
        lrmEventListener = new LRMEventListener();
        renewLogger = new NormServerBaseImpl.RenewLogThread(renewedList);
        context = AccessController.getContext();
    }

    /**
     * Returns the exporter to use to export this server.
     *
     * @param config the configuration to use for supplying the exporter
     * @return the exporter to use to export this server
     * @throws ConfigurationException if a problem occurs retrieving entries
     *	       from the configuration
     */
    Exporter getExporter(Configuration config) throws ConfigurationException {
        return (Exporter) Config.getNonNullEntry(config, NormServerBaseImpl.NORM, "serverExporter", Exporter.class, new BasicJeriExporter(TcpServerEndpoint.getInstance(0), new BasicILFactory()));
    }
    
}
