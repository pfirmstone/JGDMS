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

import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.time.Clock;
import net.jini.core.lease.Lease;

/**
 * Decorator that adds a {@link Lease}-driven dead-man switch to a wrapped
 * {@link PermissionGrant}.  The grant is <em>void</em> whenever ANY of the
 * following hold:
 * <ol>
 *   <li>{@link #cancel()} has been called on this wrapper;</li>
 *   <li>{@code clock.millis() >= lease.getExpiration()} (the lease has
 *       expired on the local clock);</li>
 *   <li>the wrapped grant's {@link PermissionGrant#isVoid()} returns
 *       {@code true} (this preserves every existing void condition, including
 *       {@code ProtectionDomainGrant} garbage-collection scoping).</li>
 * </ol>
 *
 * <p>The wrapper therefore provides two independent dead-man switches on top
 * of whatever the wrapped grant already enforces: explicit cancellation and
 * lease expiry.  It is monotone-attenuating &mdash; it can only ever
 * <em>narrow</em> the authority of the wrapped grant, never widen it.
 *
 * <h2>Lease semantics</h2>
 * The expiry comparison uses {@code >=} (fail-secure): at the exact expiry
 * millisecond the grant is already void.  {@link Lease#getExpiration()} is
 * specified relative to the <em>local</em> clock, so this wrapper does
 * nothing to compensate for clock skew between a remote landlord and the
 * holder &mdash; skew compensation is a landlord responsibility.  Deployments
 * using short-TTL leased delegation should keep landlord and holder clocks
 * synchronised (e.g. NTP) and use TTLs well above the worst-case skew.
 *
 * <h2>Renewal</h2>
 * Renewal is entirely the caller's responsibility.  This wrapper holds no
 * heartbeat thread and caches no expiry value &mdash; it re-reads
 * {@link Lease#getExpiration()} on every check, so a successful external
 * {@link Lease#renew(long)} is observed immediately on the next call.  A brief
 * void window during a missed/late renewal is the dead-man switch operating as
 * intended; holders must renew <em>before</em> expiry, never after.
 *
 * <h2>Cancellation</h2>
 * {@link #cancel()} is a <em>local</em> operation only: it flips a volatile
 * flag and does NOT call {@link Lease#cancel()}.  Whether to also notify the
 * landlord is the caller's policy decision.  Cancellation is monotone &mdash;
 * once cancelled, {@link #isVoid()} returns {@code true} forever.
 *
 * <h2>Construction guards</h2>
 * The constructor rejects:
 * <ul>
 *   <li>a {@link Lease#FOREVER} lease &mdash; a forever lease has no expiry
 *       dead-man switch, defeating the purpose of this primitive; install an
 *       undecorated grant instead;</li>
 *   <li>an already-expired lease (including the {@link Lease#ANY}
 *       sentinel {@code -1}) &mdash; born-void grants are almost certainly a
 *       configuration error;</li>
 *   <li>an {@code AllPermission}-privileged wrapped grant &mdash; inherited
 *       from {@link PermissionGrant#PermissionGrant(PermissionGrant)}; leasing
 *       {@code AllPermission} is a contradiction.</li>
 * </ul>
 *
 * <h2>Installation</h2>
 * Install via {@code DynamicPolicyProvider.grant(PermissionGrant)}.  The
 * {@code GrantPermission} ceiling check there still applies: the wrapper's
 * {@link #getPermissions()} is inherited {@code final} from
 * {@link PermissionGrant} and delegates to the wrapped grant, so the ceiling
 * sees the real permission set &mdash; the wrapper cannot conceal, narrow, or
 * widen it.  No change to {@code DynamicPolicyProvider} or its void-grant
 * sweeper is required; an expired wrapper simply reports {@code isVoid() ==
 * true} and is evicted on the next sweep.
 *
 * <h2>Serialization</h2>
 * Like other security-sensitive decorators in this package, this class is
 * <strong>not</strong> {@code Serializable}.  A leased grant is intrinsically
 * local &mdash; its {@link Lease} handle is bound to a particular landlord on a
 * particular network.
 *
 * @author Peter Firmstone
 * @since 3.1.1
 * @see ExternallyVoidablePermissionGrant
 */
public final class LeasedPermissionGrant extends PermissionGrant {

    private final Lease lease;
    private final Clock clock;
    private volatile boolean cancelled;

    /**
     * Wraps {@code wrapped} with a lease-expiry dead-man switch, using the
     * system UTC clock.
     *
     * @param wrapped the grant to decorate
     * @param lease   the lease whose expiry voids the grant
     * @throws NullPointerException     if {@code lease} is {@code null}
     * @throws IllegalArgumentException if {@code wrapped} is privileged
     *         (contains {@code AllPermission}), if {@code lease} is
     *         {@link Lease#FOREVER}, or if the lease is already expired
     * @throws SecurityException        if the caller lacks
     *         {@link RuntimePermission} {@code "getProtectionDomain"} or
     *         {@code "getClassLoader"}
     */
    public LeasedPermissionGrant(PermissionGrant wrapped, Lease lease) {
        this(wrapped, lease, Clock.systemUTC());
    }

    /**
     * Test/SPI constructor accepting a pluggable {@link Clock} so boundary
     * behaviour can be exercised deterministically without {@code
     * Thread.sleep}.  Production callers should use the two-argument
     * constructor.
     *
     * @param wrapped the grant to decorate
     * @param lease   the lease whose expiry voids the grant
     * @param clock   the clock used to evaluate expiry
     */
    LeasedPermissionGrant(PermissionGrant wrapped, Lease lease, Clock clock) {
        super(wrapped); // PD/CL guard + rejects privileged wrapped grant (NPE if wrapped null)
        if (lease == null) throw new NullPointerException("lease cannot be null");
        if (clock == null) throw new NullPointerException("clock cannot be null");
        long expiration = lease.getExpiration();
        if (expiration == Lease.FOREVER) {
            throw new IllegalArgumentException(
                "LeasedPermissionGrant rejects Lease.FOREVER: a forever lease has no "
                + "expiry dead-man switch; install an undecorated grant instead");
        }
        if (expiration <= clock.millis()) {
            // Covers Lease.ANY (-1), negative, zero and any past expiry.
            throw new IllegalArgumentException(
                "lease already expired at construction (expiration=" + expiration + ")");
        }
        this.lease = lease;
        this.clock = clock;
        this.cancelled = false;
    }

    /**
     * Returns {@code true} if this wrapper is void on the lease axis &mdash;
     * cancelled or expired &mdash; independent of the wrapped grant.
     */
    private boolean isLeaseVoid() {
        return cancelled || clock.millis() >= lease.getExpiration();
    }

    /**
     * Marks this grant void locally and immediately.  Idempotent and monotone:
     * once cancelled the grant can never become live again.  Does NOT call
     * {@link Lease#cancel()} &mdash; notifying the landlord, if desired, is the
     * caller's responsibility.
     */
    public void cancel() {
        cancelled = true;
    }

    /**
     * Returns the lease backing this grant, for diagnostics and sweeper
     * logging only.  Policy decisions should use {@link #isVoid()} rather than
     * inspecting the lease directly.
     *
     * @return the lease (never {@code null})
     */
    public Lease getLease() {
        return lease;
    }

    @Override
    public boolean implies(ProtectionDomain pd) {
        return !isLeaseVoid() && decorated().implies(pd);
    }

    @Override
    public boolean implies(ClassLoader cl, Principal[] pal) {
        return !isLeaseVoid() && decorated().implies(cl, pal);
    }

    @Override
    public boolean implies(CodeSource codeSource, Principal[] pal) {
        return !isLeaseVoid() && decorated().implies(codeSource, pal);
    }

    /**
     * Two leased grants are equivalent only when both are leased by the
     * <em>same</em> lease and their wrapped grants are equivalent.  A leased
     * grant is deliberately NOT equivalent to its undecorated wrapped grant:
     * treating them as equivalent would let external permission consolidation
     * strip the lease constraint, widening authority past expiry.
     */
    @Override
    public boolean impliesEquivalent(PermissionGrant grant) {
        if (!(grant instanceof LeasedPermissionGrant)) return false;
        LeasedPermissionGrant that = (LeasedPermissionGrant) grant;
        return this.lease == that.lease && decorated().impliesEquivalent(that.decorated());
    }

    @Override
    public boolean isDyanamic() {
        return decorated().isDyanamic();
    }

    @Override
    public boolean isVoid() {
        return isLeaseVoid() || decorated().isVoid();
    }

    @Override
    public PermissionGrantBuilder getBuilderTemplate() {
        // A leased grant is not Serializable and the lease is intrinsically
        // local, so the template simply reproduces the wrapped grant — matching
        // ExternallyVoidablePermissionGrant. Callers re-apply a lease by
        // wrapping the rebuilt grant in a new LeasedPermissionGrant.
        return decorated().getBuilderTemplate();
    }

    /**
     * Equality includes lease identity: two wrappers around the same wrapped
     * grant but with different {@link Lease} instances are distinct, because
     * different leases carry different (and non-interchangeable) expiries.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LeasedPermissionGrant)) return false;
        LeasedPermissionGrant that = (LeasedPermissionGrant) o;
        return this.lease == that.lease && decorated().equals(that.decorated());
    }

    @Override
    public int hashCode() {
        return 31 * decorated().hashCode() + System.identityHashCode(lease);
    }

    @Override
    public String toString() {
        return "LeasedPermissionGrant{decorated=" + decorated()
                + ", expiration=" + lease.getExpiration()
                + ", cancelled=" + cancelled + "}";
    }
}
