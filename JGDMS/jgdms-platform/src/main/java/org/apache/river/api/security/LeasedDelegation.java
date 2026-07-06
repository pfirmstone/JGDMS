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

import java.rmi.RemoteException;
import java.security.Permission;
import java.security.Principal;
import java.time.Clock;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;

/**
 * A bounded, time-limited, attenuating user&rarr;agent authority delegation:
 * the thin control handle for granting an AI agent a <em>subset</em> of the
 * delegating caller's authority that the agent may exercise <em>later, without
 * the user live in scope</em>, and that is revoked by lease expiry, a missed
 * renewal, or explicit {@link #revoke()}.
 *
 * <p>This is Phase&nbsp;3 of {@code SOW-AI-Agent-Authority-Support.md}. It wires
 * three existing pieces together and adds nothing to the trust base:
 * <ol>
 *   <li>a {@code principal}-scoped {@link PermissionGrant} naming the agent's
 *       identity and carrying the delegated permissions (a permission
 *       <em>subset</em> &mdash; <b>not</b> the user's identity, so the agent
 *       gains exactly those permissions and none of the user's other authority);</li>
 *   <li>a {@link LeasedPermissionGrant} wrapper giving it a lease-expiry
 *       dead-man switch and a local {@code cancel()};</li>
 *   <li>installation via {@link RevocablePolicy#grant(PermissionGrant)}.</li>
 * </ol>
 *
 * <h2>Attenuation is enforced at install, by the platform</h2>
 * {@link #grant} must be invoked <em>by the delegating principal</em> (its
 * domain on the call stack). JGDMS runs in production under a JDK that keeps an
 * active authorization {@code SecurityManager} (DirtyChai); there,
 * {@code RevocablePolicy.grant(PermissionGrant)} runs a {@code GrantPermission}
 * guard over the delegated permissions against the caller's context, so a
 * delegation can never exceed what the delegating principal is itself
 * authorised to grant. This class therefore performs <em>no</em> ceiling check
 * of its own; over-broad delegation surfaces as a {@link SecurityException} from
 * {@code grant()}. (On a stock OpenJDK build with no active SecurityManager the
 * guard is a no-op &mdash; JGDMS targets OpenJDK only for compile-time
 * portability, not production.)
 *
 * <h2>Renewal ownership &mdash; the agent cannot extend its own authority</h2>
 * The {@link Lease} is the renewal credential. It is held by <em>this handle</em>,
 * which is returned to the <b>delegator</b> &mdash; it is never handed to the
 * agent. The agent receives only the installed grant's <em>effect</em> (its
 * domain matches the grant); it has no reference to the lease or to this object,
 * so it cannot call {@link #renew(long)} and cannot prolong its own delegation.
 * Renewal authority stays with the delegator (or a separate trusted renewer the
 * delegator entrusts the handle to). This separation is deliberate: a leased
 * delegation can only ever be <em>extended by the grantor</em>, never
 * self-extended by the grantee.
 *
 * <h2>Missed-renewal / dead-man semantics</h2>
 * Renewal is the delegator's responsibility and must happen <em>before</em>
 * expiry. If a renewal is missed or fails (network partition, landlord refusal),
 * the lease's expiration is unchanged and the grant goes void at expiry: the
 * agent goes dark. There is no grace window and no "renewal in flight" state.
 * A late-arriving renewal does not retroactively authorise actions attempted
 * during the void window &mdash; those were already denied; only checks after
 * the renewal lands see authority again. Delegators should renew with a margin
 * well above the worst-case renewal round-trip plus clock skew.
 *
 * <p>Instances are safe for use by a single delegator/renewer; {@link #isVoid()}
 * may be polled from any thread.
 *
 * @author Peter Firmstone
 * @since 3.1.1
 * @see LeasedPermissionGrant
 * @see RevocablePolicy
 */
public final class LeasedDelegation {

    private static final Logger logger =
            Logger.getLogger(LeasedDelegation.class.getName());

    private final Principal agent;
    private final Lease lease;
    private final LeasedPermissionGrant grant;

    private LeasedDelegation(Principal agent, Lease lease, LeasedPermissionGrant grant) {
        this.agent = agent;
        this.lease = lease;
        this.grant = grant;
    }

    /**
     * Delegates {@code permissions} to {@code agent} for the life of {@code lease},
     * installing the leased grant into {@code policy} and returning the control
     * handle to the caller (the delegator).
     *
     * <p>Invoke this <em>as the delegating principal</em>: under an active
     * authorization SecurityManager the install is bounded by the caller's
     * {@code GrantPermission} ceiling (see class docs).
     *
     * @param policy      the revocable dynamic policy to install the grant into
     * @param agent       the agent identity the delegation is scoped to (e.g. a
     *                    {@code SpiffePrincipal} carrying the agent's SVID)
     * @param permissions the subset of authority to delegate (defensively used
     *                    as-is by the underlying grant)
     * @param lease       the lease whose expiry bounds the delegation; must not
     *                    be {@link Lease#FOREVER} or already expired
     * @return the delegator's control handle
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code lease} is {@code Lease.FOREVER}
     *                                  or already expired, or the resulting grant
     *                                  is privileged (see {@link LeasedPermissionGrant})
     * @throws SecurityException        if the caller may not grant {@code permissions}
     *                                  (the install-time {@code GrantPermission} ceiling)
     */
    public static LeasedDelegation grant(RevocablePolicy policy,
                                         Principal agent,
                                         Permission[] permissions,
                                         Lease lease) {
        return grant(policy, agent, permissions, lease, Clock.systemUTC());
    }

    /**
     * Package-private variant accepting a pluggable {@link Clock} for
     * deterministic testing of expiry/renewal; production callers use
     * {@link #grant(RevocablePolicy, Principal, Permission[], Lease)}.
     */
    static LeasedDelegation grant(RevocablePolicy policy,
                                  Principal agent,
                                  Permission[] permissions,
                                  Lease lease,
                                  Clock clock) {
        if (policy == null) throw new NullPointerException("policy");
        if (agent == null) throw new NullPointerException("agent");
        if (permissions == null) throw new NullPointerException("permissions");
        // lease/clock null and Lease.FOREVER/expired are validated by the wrapper.
        PermissionGrant scoped = PermissionGrantBuilder.newBuilder()
                .principals(new Principal[]{agent})
                .permissions(permissions)
                .context(PermissionGrantBuilder.PRINCIPAL)
                .build();
        LeasedPermissionGrant leased = new LeasedPermissionGrant(scoped, lease, clock);
        policy.grant(leased); // GrantPermission ceiling enforced here under an active SM
        return new LeasedDelegation(agent, lease, leased);
    }

    /**
     * Like {@link #grant}, but installs a <em>one-shot</em> ({@link OneShot}) delegation: a
     * one-shot-aware policy ({@code DynamicPolicyProvider}) reports it <strong>only</strong> via
     * {@code Policy.impliesOnce}, never {@code Policy.implies}, so the agent's escalated authority
     * is never cached by a {@code CachingSecurityManager} and never recorded into generated policy
     * by polpAudit. Intended for a human-gated escalation (Phase&nbsp;2 {@code EscalationGate}); a
     * renewable delegation should use {@link #grant} instead.
     *
     * <p>Semantics are otherwise identical to {@link #grant}: agent-principal subset grant,
     * lease-expiry dead-man switch, install-time {@code GrantPermission} ceiling, renewal/revoke
     * owned by the returned handle (never the agent). Because the grant is never cached, expiry or
     * {@link #revoke()} denies on the next check with no cache to clear.
     *
     * @see #grant(RevocablePolicy, Principal, Permission[], Lease)
     */
    public static LeasedDelegation grantOneShot(RevocablePolicy policy,
                                                Principal agent,
                                                Permission[] permissions,
                                                Lease lease) {
        return grantOneShot(policy, agent, permissions, lease, Clock.systemUTC());
    }

    /**
     * Package-private variant of {@link #grantOneShot} accepting a pluggable {@link Clock} for
     * deterministic testing of expiry; production callers use the four-argument form.
     */
    static LeasedDelegation grantOneShot(RevocablePolicy policy,
                                         Principal agent,
                                         Permission[] permissions,
                                         Lease lease,
                                         Clock clock) {
        if (policy == null) throw new NullPointerException("policy");
        if (agent == null) throw new NullPointerException("agent");
        if (permissions == null) throw new NullPointerException("permissions");
        // lease/clock null and Lease.FOREVER/expired are validated by the wrapper.
        PermissionGrant scoped = PermissionGrantBuilder.newBuilder()
                .principals(new Principal[]{agent})
                .permissions(permissions)
                .context(PermissionGrantBuilder.PRINCIPAL)
                .build();
        OneShotLeasedPermissionGrant leased = new OneShotLeasedPermissionGrant(scoped, lease, clock);
        policy.grant(leased); // GrantPermission ceiling enforced here under an active SM
        return new LeasedDelegation(agent, lease, leased);
    }

    /**
     * Returns {@code true} when the delegated authority is no longer in force —
     * the lease has expired, {@link #revoke()} has been called, or the underlying
     * grant has otherwise become void. Once void, always void.
     *
     * @return whether the delegation is void
     */
    public boolean isVoid() {
        return grant.isVoid();
    }

    /**
     * Renews the delegation for an additional {@code duration} (milliseconds from
     * now, per {@link Lease#renew(long)}). On success the delegation's lifetime is
     * extended and observed on the next authority check; on failure the lease's
     * expiration is unchanged and the delegation goes void at the existing expiry.
     *
     * <p>Renewal authority belongs to the delegator that holds this handle; the
     * agent has no access to it and cannot renew its own delegation.
     *
     * @param duration requested additional duration in milliseconds
     * @throws LeaseDeniedException  if the landlord refuses renewal
     * @throws UnknownLeaseException if the lease is unknown to the landlord
     * @throws RemoteException       on a communication failure with the landlord
     */
    public void renew(long duration)
            throws LeaseDeniedException, UnknownLeaseException, RemoteException {
        lease.renew(duration);
    }

    /**
     * Revokes the delegation immediately and permanently. The grant is voided
     * locally at once (subsequent authority checks deny), then the lease is
     * cancelled on a best-effort basis to notify the landlord and release
     * resources. Local revocation is monotone and does not depend on the lease
     * cancellation succeeding; a failure to reach the landlord is logged and
     * swallowed because authority is already withdrawn locally.
     */
    public void revoke() {
        grant.cancel(); // immediate, monotone, local — authority withdrawn now
        try {
            lease.cancel();
        } catch (UnknownLeaseException | RemoteException e) {
            logger.log(Level.FINE,
                    "Local revocation done; landlord lease cancel was best-effort and failed", e);
        }
    }

    /**
     * The lease backing this delegation &mdash; the renewal credential. Exposed to
     * the delegator/renewer for renewal scheduling only; it MUST NOT be handed to
     * the agent, or the agent could extend its own authority.
     *
     * @return the backing lease (never {@code null})
     */
    public Lease lease() {
        return lease;
    }

    /**
     * The agent identity this delegation is scoped to.
     *
     * @return the agent principal (never {@code null})
     */
    public Principal agent() {
        return agent;
    }

    @Override
    public String toString() {
        return "LeasedDelegation{agent=" + agent
                + ", expiration=" + lease.getExpiration()
                + ", void=" + grant.isVoid() + "}";
    }
}
