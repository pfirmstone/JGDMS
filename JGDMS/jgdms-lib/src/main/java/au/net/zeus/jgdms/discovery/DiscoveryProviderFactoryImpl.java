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
package au.net.zeus.jgdms.discovery;

import java.io.IOException;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.discovery.DiscoveryManagement;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lookup.DiscoveryProviderFactory;
import net.jini.lookup.JoinManagerSpi;
import net.jini.lookup.ServiceDiscoveryManagerSpi;
import net.jini.lookup.ServiceIDListener;

/**
 * Provider that constructs the discovery/join implementations for the
 * {@code net.jini.lookup.ServiceDiscoveryManager} and
 * {@code net.jini.lookup.JoinManager} facades.
 * <p>
 * Registered through {@code META-INF/services/net.jini.lookup.DiscoveryProviderFactory}
 * and resolved by the facades via {@link org.apache.river.resource.Service}.
 *
 * @see ServiceDiscoveryManagerImpl
 * @see JoinManagerImpl
 */
public final class DiscoveryProviderFactoryImpl implements DiscoveryProviderFactory {

    /** Public no-arg constructor required for service loading. */
    public DiscoveryProviderFactoryImpl() {
    }

    @Override
    public ServiceDiscoveryManagerSpi newServiceDiscoveryManager(
            DiscoveryManagement discoveryMgr,
            LeaseRenewalManager leaseMgr)
            throws IOException {
        return new ServiceDiscoveryManagerImpl(discoveryMgr, leaseMgr);
    }

    @Override
    public ServiceDiscoveryManagerSpi newServiceDiscoveryManager(
            DiscoveryManagement discoveryMgr,
            LeaseRenewalManager leaseMgr,
            Configuration config)
            throws IOException, ConfigurationException {
        return new ServiceDiscoveryManagerImpl(discoveryMgr, leaseMgr, config);
    }

    @Override
    public JoinManagerSpi newJoinManager(
            Object serviceProxy,
            Entry[] attrSets,
            ServiceIDListener callback,
            DiscoveryManagement discoveryMgr,
            LeaseRenewalManager leaseMgr)
            throws IOException {
        return new JoinManagerImpl(serviceProxy, attrSets, callback,
                discoveryMgr, leaseMgr);
    }

    @Override
    public JoinManagerSpi newJoinManager(
            Object serviceProxy,
            Entry[] attrSets,
            ServiceIDListener callback,
            DiscoveryManagement discoveryMgr,
            LeaseRenewalManager leaseMgr,
            Configuration config)
            throws IOException, ConfigurationException {
        return new JoinManagerImpl(serviceProxy, attrSets, callback,
                discoveryMgr, leaseMgr, config);
    }

    @Override
    public JoinManagerSpi newJoinManager(
            Object serviceProxy,
            Entry[] attrSets,
            ServiceID serviceID,
            DiscoveryManagement discoveryMgr,
            LeaseRenewalManager leaseMgr)
            throws IOException {
        return new JoinManagerImpl(serviceProxy, attrSets, serviceID,
                discoveryMgr, leaseMgr);
    }

    @Override
    public JoinManagerSpi newJoinManager(
            Object serviceProxy,
            Entry[] attrSets,
            ServiceID serviceID,
            DiscoveryManagement discoveryMgr,
            LeaseRenewalManager leaseMgr,
            Configuration config)
            throws IOException, ConfigurationException {
        return new JoinManagerImpl(serviceProxy, attrSets, serviceID,
                discoveryMgr, leaseMgr, config);
    }
}
