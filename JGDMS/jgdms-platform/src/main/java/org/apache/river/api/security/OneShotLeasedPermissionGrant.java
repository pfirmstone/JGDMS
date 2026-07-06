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

package org.apache.river.api.security;

import java.time.Clock;
import net.jini.core.lease.Lease;

/**
 * A {@link LeasedPermissionGrant} that is also {@link OneShot}: a human-gated escalation grant
 * whose authority a one-shot-aware policy reports <strong>only</strong> via
 * {@code Policy.impliesOnce} (never {@code Policy.implies}), so it is never cached by a
 * {@code CachingSecurityManager} and never recorded into generated policy by polpAudit.
 *
 * <p>It adds no behaviour over {@link LeasedPermissionGrant} beyond carrying the {@link OneShot}
 * marker &mdash; the lease dead-man switch, {@link #cancel()}, the {@code AllPermission} and
 * already-expired/{@code FOREVER} construction guards, and the {@code GrantPermission} ceiling on
 * install are all inherited unchanged. The one-shot-ness is a distinct <em>type</em>, not a flag,
 * so a renewable {@link LeasedPermissionGrant} can never be mistaken for one-shot.
 *
 * <p>Install via {@link LeasedDelegation#grantOneShot}.
 *
 * @author Peter Firmstone
 * @since 3.1.1
 * @see OneShot
 * @see LeasedDelegation#grantOneShot
 */
public final class OneShotLeasedPermissionGrant extends LeasedPermissionGrant implements OneShot {

    /**
     * Wraps {@code wrapped} with a lease-expiry dead-man switch and the one-shot marker, using
     * the system UTC clock. See {@link LeasedPermissionGrant#LeasedPermissionGrant(PermissionGrant, Lease)}
     * for the construction guards.
     */
    public OneShotLeasedPermissionGrant(PermissionGrant wrapped, Lease lease) {
        super(wrapped, lease);
    }

    /**
     * Test/SPI constructor accepting a pluggable {@link Clock}; production callers use the
     * two-argument constructor.
     */
    OneShotLeasedPermissionGrant(PermissionGrant wrapped, Lease lease, Clock clock) {
        super(wrapped, lease, clock);
    }

    // Equality is inherited from LeasedPermissionGrant, whose exact-class (getClass())
    // test already makes a one-shot grant unequal to a plain leased grant symmetrically —
    // no equals/hashCode override is needed or wanted here.
}
