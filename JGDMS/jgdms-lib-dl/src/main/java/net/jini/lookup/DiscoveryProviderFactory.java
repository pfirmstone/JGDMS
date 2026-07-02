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
package net.jini.lookup;

import java.io.IOException;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.discovery.DiscoveryManagement;
import net.jini.lease.LeaseRenewalManager;

/**
 * Provider that constructs the discovery/join implementations backing the
 * {@link ServiceDiscoveryManager} and {@link JoinManager} facades.
 * <p>
 * A single {@code DiscoveryProviderFactory} is registered (via
 * {@code META-INF/services}) by the non-downloadable implementation jar and
 * resolved once by each facade through
 * {@link org.apache.river.resource.Service#providers(Class, ClassLoader)}. This
 * factory exposes exactly one method for each public constructor of the two
 * facades; the parameter lists and declared exceptions mirror those
 * constructors verbatim so the facade can forward construction unchanged.
 * <p>
 * The facade never references any implementation class directly; it reaches the
 * implementation only through this factory and the returned SPI objects,
 * preserving the constraint that the download jar must not depend on the
 * implementation jar.
 *
 * @see ServiceDiscoveryManager
 * @see JoinManager
 * @see ServiceDiscoveryManagerSpi
 * @see JoinManagerSpi
 * @since 3.1.1
 */
public interface DiscoveryProviderFactory {

    /**
     * Mirrors {@link ServiceDiscoveryManager#ServiceDiscoveryManager(DiscoveryManagement, LeaseRenewalManager)}.
     */
    ServiceDiscoveryManagerSpi newServiceDiscoveryManager(
            DiscoveryManagement discoveryMgr,
            LeaseRenewalManager leaseMgr)
            throws IOException;

    /**
     * Mirrors {@link ServiceDiscoveryManager#ServiceDiscoveryManager(DiscoveryManagement, LeaseRenewalManager, Configuration)}.
     */
    ServiceDiscoveryManagerSpi newServiceDiscoveryManager(
            DiscoveryManagement discoveryMgr,
            LeaseRenewalManager leaseMgr,
            Configuration config)
            throws IOException, ConfigurationException;

    /**
     * Mirrors {@link JoinManager#JoinManager(Object, Entry[], ServiceIDListener, DiscoveryManagement, LeaseRenewalManager)}.
     */
    JoinManagerSpi newJoinManager(
            Object serviceProxy,
            Entry[] attrSets,
            ServiceIDListener callback,
            DiscoveryManagement discoveryMgr,
            LeaseRenewalManager leaseMgr)
            throws IOException;

    /**
     * Mirrors {@link JoinManager#JoinManager(Object, Entry[], ServiceIDListener, DiscoveryManagement, LeaseRenewalManager, Configuration)}.
     */
    JoinManagerSpi newJoinManager(
            Object serviceProxy,
            Entry[] attrSets,
            ServiceIDListener callback,
            DiscoveryManagement discoveryMgr,
            LeaseRenewalManager leaseMgr,
            Configuration config)
            throws IOException, ConfigurationException;

    /**
     * Mirrors {@link JoinManager#JoinManager(Object, Entry[], ServiceID, DiscoveryManagement, LeaseRenewalManager)}.
     */
    JoinManagerSpi newJoinManager(
            Object serviceProxy,
            Entry[] attrSets,
            ServiceID serviceID,
            DiscoveryManagement discoveryMgr,
            LeaseRenewalManager leaseMgr)
            throws IOException;

    /**
     * Mirrors {@link JoinManager#JoinManager(Object, Entry[], ServiceID, DiscoveryManagement, LeaseRenewalManager, Configuration)}.
     */
    JoinManagerSpi newJoinManager(
            Object serviceProxy,
            Entry[] attrSets,
            ServiceID serviceID,
            DiscoveryManagement discoveryMgr,
            LeaseRenewalManager leaseMgr,
            Configuration config)
            throws IOException, ConfigurationException;
}
