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

import java.rmi.RemoteException;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.discovery.DiscoveryManagement;
import net.jini.lease.LeaseRenewalManager;

/**
 * Service provider interface implemented by the discovery implementation that
 * backs the {@link ServiceDiscoveryManager} facade.
 * <p>
 * This interface mirrors, exactly, the public method surface of
 * {@link ServiceDiscoveryManager}. The facade holds a delegate of this type,
 * obtained once through a {@link DiscoveryProviderFactory} resolved via
 * {@link org.apache.river.resource.Service}, and forwards every public method
 * call to the delegate. The implementation lives in a separate, non-downloadable
 * jar so that the {@code net.jini.lookup} package (this download jar) can be
 * compiled for the widest range of client JVMs while the implementation retains
 * access to newer runtime facilities (for example, virtual threads).
 *
 * @see ServiceDiscoveryManager
 * @see DiscoveryProviderFactory
 * @since 3.1.1
 */
public interface ServiceDiscoveryManagerSpi {

    /**
     * Implements {@link ServiceDiscoveryManager#lookup(ServiceTemplate, ServiceItemFilter)}.
     */
    ServiceItem lookup(ServiceTemplate tmpl, ServiceItemFilter filter);

    /**
     * Implements {@link ServiceDiscoveryManager#lookup(ServiceTemplate, ServiceItemFilter, long)}.
     */
    ServiceItem lookup(ServiceTemplate tmpl,
            ServiceItemFilter filter,
            long waitDur) throws InterruptedException, RemoteException;

    /**
     * Implements {@link ServiceDiscoveryManager#lookup(ServiceTemplate, int, ServiceItemFilter)}.
     */
    ServiceItem[] lookup(ServiceTemplate tmpl,
            int maxMatches,
            ServiceItemFilter filter);

    /**
     * Implements {@link ServiceDiscoveryManager#lookup(ServiceTemplate, int, int, ServiceItemFilter, long)}.
     */
    ServiceItem[] lookup(ServiceTemplate tmpl,
            int minMatches,
            int maxMatches,
            ServiceItemFilter filter,
            long waitDur) throws InterruptedException, RemoteException;

    /**
     * Implements {@link ServiceDiscoveryManager#createLookupCache(ServiceTemplate, ServiceItemFilter, ServiceDiscoveryListener)}.
     */
    LookupCache createLookupCache(ServiceTemplate tmpl,
            ServiceItemFilter filter,
            ServiceDiscoveryListener listener) throws RemoteException;

    /**
     * Implements {@link ServiceDiscoveryManager#getDiscoveryManager()}.
     */
    DiscoveryManagement getDiscoveryManager();

    /**
     * Implements {@link ServiceDiscoveryManager#getLeaseRenewalManager()}.
     */
    LeaseRenewalManager getLeaseRenewalManager();

    /**
     * Implements {@link ServiceDiscoveryManager#terminate()}.
     */
    void terminate();
}
