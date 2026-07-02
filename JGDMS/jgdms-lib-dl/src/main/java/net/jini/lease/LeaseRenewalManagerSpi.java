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

import java.rmi.RemoteException;
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;

/**
 * Service provider interface implemented by the lease-renewal implementation
 * that backs the {@link LeaseRenewalManager} facade.
 * <p>
 * This interface mirrors, exactly, the public method surface of
 * {@link LeaseRenewalManager}. The facade holds a delegate of this type,
 * constructed once through a {@link LeaseRenewalManagerFactory} resolved via
 * {@link org.apache.river.resource.Service}, and forwards every public method
 * call to the delegate. The implementation lives in a separate, non-downloadable
 * jar so that the {@code net.jini.lease} package (this download jar) can be
 * compiled for the widest range of client JVMs while the implementation retains
 * access to newer runtime facilities (for example, virtual threads).
 *
 * @see LeaseRenewalManager
 * @see LeaseRenewalManagerFactory
 * @since 3.1.1
 */
public interface LeaseRenewalManagerSpi {

    /**
     * Implements {@link LeaseRenewalManager#renewUntil(Lease, long, LeaseListener)}.
     */
    void renewUntil(Lease lease,
            long desiredExpiration,
            LeaseListener listener);

    /**
     * Implements {@link LeaseRenewalManager#renewUntil(Lease, long, long, LeaseListener)}.
     */
    void renewUntil(Lease lease,
            long desiredExpiration,
            long renewDuration,
            LeaseListener listener);

    /**
     * Implements {@link LeaseRenewalManager#renewFor(Lease, long, LeaseListener)}.
     */
    void renewFor(Lease lease,
            long desiredDuration,
            LeaseListener listener);

    /**
     * Implements {@link LeaseRenewalManager#renewFor(Lease, long, long, LeaseListener)}.
     */
    void renewFor(Lease lease,
            long desiredDuration,
            long renewDuration,
            LeaseListener listener);

    /**
     * Implements {@link LeaseRenewalManager#getExpiration(Lease)}.
     */
    long getExpiration(Lease lease) throws UnknownLeaseException;

    /**
     * Implements {@link LeaseRenewalManager#setExpiration(Lease, long)}.
     */
    void setExpiration(Lease lease, long expiration) throws UnknownLeaseException;

    /**
     * Implements {@link LeaseRenewalManager#cancel(Lease)}.
     */
    void cancel(Lease lease) throws UnknownLeaseException, RemoteException;

    /**
     * Implements {@link LeaseRenewalManager#close()}.
     */
    void close();

    /**
     * Implements {@link LeaseRenewalManager#remove(Lease)}.
     */
    void remove(Lease lease) throws UnknownLeaseException;

    /**
     * Implements {@link LeaseRenewalManager#clear()}.
     */
    void clear();
}
