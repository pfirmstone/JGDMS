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
package net.jini.lease;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.core.lease.Lease;

/**
 * Provider that constructs the lease-renewal implementation backing the
 * {@link LeaseRenewalManager} facade.
 * <p>
 * A single {@code LeaseRenewalManagerFactory} is registered (via
 * {@code META-INF/services}) by the non-downloadable implementation jar and
 * resolved once by the facade through
 * {@link org.apache.river.resource.Service#providers(Class, ClassLoader)}. This
 * factory exposes exactly one method for each public constructor of the facade;
 * the parameter lists and declared exceptions mirror those constructors so the
 * facade can forward construction unchanged.
 * <p>
 * Each method also receives the owning {@link LeaseRenewalManager} facade
 * instance so the implementation can use it as the source of any
 * {@link LeaseRenewalEvent} it generates, preserving the contract that the
 * event source is the {@code LeaseRenewalManager} the client holds.
 * <p>
 * The facade never references any implementation class directly; it reaches the
 * implementation only through this factory and the returned SPI objects,
 * preserving the constraint that the download jar must not depend on the
 * implementation jar.
 *
 * @see LeaseRenewalManager
 * @see LeaseRenewalManagerSpi
 * @since 3.1.1
 */
public interface LeaseRenewalManagerFactory {

    /**
     * Mirrors {@link LeaseRenewalManager#LeaseRenewalManager()}.
     *
     * @param owner the facade that will hold the returned delegate; used as the
     *              source of any generated {@link LeaseRenewalEvent}.
     * @return a new lease-renewal implementation.
     */
    LeaseRenewalManagerSpi newLeaseRenewalManager(LeaseRenewalManager owner);

    /**
     * Mirrors {@link LeaseRenewalManager#LeaseRenewalManager(Configuration)}.
     *
     * @param owner the facade that will hold the returned delegate; used as the
     *              source of any generated {@link LeaseRenewalEvent}.
     * @param config supplies entries that control the configuration of the
     *               instance.
     * @return a new lease-renewal implementation.
     * @throws ConfigurationException if a problem occurs when obtaining entries
     *         from the configuration.
     * @throws NullPointerException if the configuration is {@code null}.
     */
    LeaseRenewalManagerSpi newLeaseRenewalManager(LeaseRenewalManager owner,
            Configuration config) throws ConfigurationException;

    /**
     * Mirrors {@link LeaseRenewalManager#LeaseRenewalManager(Lease, long, LeaseListener)}.
     *
     * @param owner the facade that will hold the returned delegate; used as the
     *              source of any generated {@link LeaseRenewalEvent}.
     * @param lease the initial lease to manage.
     * @param desiredExpiration the desired expiration for {@code lease}.
     * @param listener the {@link LeaseListener} to notify, or {@code null}.
     * @return a new lease-renewal implementation managing {@code lease}.
     * @throws NullPointerException if {@code lease} is {@code null}.
     */
    LeaseRenewalManagerSpi newLeaseRenewalManager(LeaseRenewalManager owner,
            Lease lease,
            long desiredExpiration,
            LeaseListener listener);
}
