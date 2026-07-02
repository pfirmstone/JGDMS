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
package au.net.zeus.jgdms.lease;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.core.lease.Lease;
import net.jini.lease.LeaseListener;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lease.LeaseRenewalManagerFactory;
import net.jini.lease.LeaseRenewalManagerSpi;

/**
 * Provider that constructs the lease-renewal implementation for the
 * {@code net.jini.lease.LeaseRenewalManager} facade.
 * <p>
 * Registered through {@code META-INF/services/net.jini.lease.LeaseRenewalManagerFactory}
 * and resolved by the facade via {@link org.apache.river.resource.Service}.
 *
 * @see LeaseRenewalManagerImpl
 */
public final class LeaseRenewalManagerFactoryImpl implements LeaseRenewalManagerFactory {

    /** Public no-arg constructor required for service loading. */
    public LeaseRenewalManagerFactoryImpl() {
    }

    @Override
    public LeaseRenewalManagerSpi newLeaseRenewalManager(LeaseRenewalManager owner) {
        return new LeaseRenewalManagerImpl(owner);
    }

    @Override
    public LeaseRenewalManagerSpi newLeaseRenewalManager(LeaseRenewalManager owner,
            Configuration config) throws ConfigurationException {
        return new LeaseRenewalManagerImpl(owner, config);
    }

    @Override
    public LeaseRenewalManagerSpi newLeaseRenewalManager(LeaseRenewalManager owner,
            Lease lease,
            long desiredExpiration,
            LeaseListener listener) {
        return new LeaseRenewalManagerImpl(owner, lease, desiredExpiration, listener);
    }
}
