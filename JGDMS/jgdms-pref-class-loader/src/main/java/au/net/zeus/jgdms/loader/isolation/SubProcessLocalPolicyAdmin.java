/*
 * Copyright 2026 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.loader.isolation;

import java.rmi.RemoteException;
import java.security.CodeSource;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.List;
import net.jini.security.policy.DynamicPolicyProvider;
import org.apache.river.api.security.LeasedPermissionGrant;
import org.apache.river.api.security.PermissionGrant;

/**
 * The real grant-application backend behind {@link PolicyAdmin}
 * (task&nbsp;T1 of {@code SOW-Smart-Proxy-Isolation-Remaining-Work.md}, which
 * is {@code SubProcessDynamicPolicy} task&nbsp;T3, part&nbsp;(c)).
 *
 * <p><strong>Layering.</strong> This class is the {@code backing} object
 * {@link SubProcessPolicyAdmin} is constructed with &mdash; it is never
 * itself exported, never itself a {@code Remote}, and carries none of the
 * authentication/dispatch logic that class and {@link
 * AdminPrincipalAuthenticator} already implement and have already been
 * board-reviewed for. The <em>only</em> supported way to reach an instance
 * of this class is {@code method.invoke(backing, args)} inside {@code
 * SubProcessPolicyAdmin}'s {@code GuardedPolicyAdminHandler}, which has
 * already re-checked the caller is authenticated as the orchestrating admin
 * principal before this class's methods are ever entered. This class
 * performs <strong>no authentication of its own</strong> &mdash; it trusts
 * that every call it receives already passed that gate, exactly the shape
 * the test double it replaces ({@code
 * IsolationSecurityCriticalTest.TrustedPolicyBacking}) demonstrated.
 *
 * <p><strong>Verbatim application, no independent judgment.</strong> {@link
 * #grant(PermissionGrant)} installs the given grant into the subprocess's
 * own local {@link DynamicPolicyProvider} exactly as given &mdash; it does
 * not compute, narrow, widen, or otherwise second-guess <em>what</em> is
 * being granted. That judgment already happened in {@code
 * VerdictPermissionMapper} (SubProcessDynamicPolicy T2, landed) and whatever
 * caller-side wiring built the {@link PermissionGrant} being applied here
 * (SubProcessDynamicPolicy T4). The existing {@code
 * DynamicPolicyProvider.grant(PermissionGrant)} still runs its own {@code
 * GrantPermission} ceiling check against the calling context on the stack at
 * the moment this method executes (see {@code RevocablePolicy#grant}); this
 * class neither strengthens nor weakens that check, it simply does not
 * bypass it.
 *
 * <p><strong>Lease-scoped by construction (judgment call, flagged for
 * board review).</strong> {@code SubProcessDynamicPolicy} &sect;2 decided
 * grants delivered by this mechanism should be lease-scoped by default so a
 * stale or misbehaving proxy's ceiling can expire or be revoked without a
 * hard subprocess kill. The {@link PolicyAdmin#grant(PermissionGrant)}
 * signature accepts a fully-built {@link PermissionGrant}, so this class
 * cannot itself decide the lease duration or renewal policy &mdash; that
 * remains the caller-side wiring's decision, exactly as {@code
 * LeasedDelegation} already keeps renewal ownership with the delegator, not
 * the delegate. What this class <em>does</em> enforce, as a structural
 * (shape, not content) fail-closed check: {@link #grant(PermissionGrant)}
 * refuses any grant that is not a {@link LeasedPermissionGrant} (or a
 * subtype, e.g. a one-shot escalation) &mdash; an ungoverned, non-expiring
 * grant is never installed through this remote channel, regardless of what
 * permissions it carries. This is deliberately a check on the grant's
 * <em>shape</em> (does it carry a lease dead-man switch at all), never on
 * its <em>content</em> (which permissions, for which principal) &mdash; the
 * latter remains exclusively {@code VerdictPermissionMapper}'s decision.
 * Callers that need a permanent, unleased grant must use a different
 * mechanism (e.g. the subprocess's own static policy file at bootstrap);
 * this channel is scoped to exactly the lease-revocable case the SOW
 * describes.
 *
 * <p><strong>{@link #refresh()} and {@link #getGrants()}.</strong> Both
 * delegate to the same local {@code DynamicPolicyProvider} &mdash; {@code
 * refresh()} to {@link DynamicPolicyProvider#refresh()} verbatim; {@code
 * getGrants()} to {@link DynamicPolicyProvider#getPermissionGrants(
 * ProtectionDomain)}, queried against a synthetic, codesource-and-loader-
 * free {@link ProtectionDomain} carrying only this backend's configured
 * {@code scopePrincipals} &mdash; the same principal-only-domain query shape
 * {@code LeasedDelegationTest} already exercises against a real {@code
 * DynamicPolicyProvider}. This returns the <em>actual</em> installed {@link
 * PermissionGrant} objects (including any still-live lease wrapper), never a
 * locally re-tracked copy: no parallel grant-tracking structure is
 * maintained by this class, by design.
 *
 * @since 3.1.1
 */
public final class SubProcessLocalPolicyAdmin implements PolicyAdmin {

    private final DynamicPolicyProvider policy;
    private final Principal[] scopePrincipals;

    /**
     * @param policy          the subprocess's own local dynamic policy
     *        provider; must not be null. This is the one and only authority
     *        this backend ever applies a grant to &mdash; never a remote or
     *        cross-process policy.
     * @param scopePrincipals the principal(s) this subprocess's delivered
     *        grants are scoped under (e.g. the remote SPIFFE identity {@code
     *        IsolationPoolingKey} pooled this subprocess on); used only to
     *        query {@link #getGrants()}, never to filter or judge what
     *        {@link #grant(PermissionGrant)} installs. Defensively copied;
     *        must not be null (may be empty, though an empty scope will
     *        typically see no principal-scoped grants).
     */
    public SubProcessLocalPolicyAdmin(DynamicPolicyProvider policy,
                                      Principal[] scopePrincipals) {
        if (policy == null) throw new NullPointerException("policy");
        if (scopePrincipals == null) {
            throw new NullPointerException("scopePrincipals");
        }
        this.policy = policy;
        this.scopePrincipals = scopePrincipals.clone();
    }

    /**
     * Installs {@code grant} into the subprocess's local policy verbatim.
     *
     * @throws NullPointerException if {@code grant} is null
     * @throws SecurityException if {@code grant} is not a {@link
     *         LeasedPermissionGrant} (structural fail-closed check, see class
     *         docs), or if the underlying {@code DynamicPolicyProvider}'s own
     *         {@code GrantPermission} ceiling check denies the calling
     *         context (an existing, unmodified check this class does not
     *         bypass)
     */
    @Override
    public void grant(PermissionGrant grant) throws RemoteException {
        if (grant == null) throw new NullPointerException("grant");
        if (!(grant instanceof LeasedPermissionGrant)) {
            throw new SecurityException(
                "Refusing to install a non-lease-scoped PermissionGrant via"
                + " SubProcessDynamicPolicy: grants delivered through this"
                + " channel must be lease-scoped (LeasedPermissionGrant or a"
                + " subtype) so a stale or misbehaving proxy's ceiling can"
                + " expire or be revoked without a hard subprocess kill"
                + " (SubProcessDynamicPolicy §2). Grant class was: "
                + grant.getClass().getName());
        }
        // Verbatim install: no independent judgment about *what* is granted.
        // DynamicPolicyProvider.grant(PermissionGrant) still runs its own
        // GrantPermission ceiling check against the current calling context;
        // that check is neither strengthened nor weakened here.
        policy.grant(grant);
    }

    /**
     * Reloads / recomputes the effective subprocess policy by delegating to
     * the local {@code DynamicPolicyProvider}'s own {@code refresh()}
     * verbatim (also sweeps any now-void &mdash; e.g. lease-expired &mdash;
     * dynamic grants, per {@code DynamicPolicyProvider#refresh}).
     */
    @Override
    public void refresh() throws RemoteException {
        policy.refresh();
    }

    /**
     * Returns the grants currently in force for this backend's configured
     * {@code scopePrincipals}, read live from the local {@code
     * DynamicPolicyProvider} &mdash; never from a locally-tracked copy. A
     * grant whose lease has expired reports {@link PermissionGrant#isVoid()}
     * (transitively, {@link LeasedPermissionGrant#isVoid()}) as {@code true}
     * and is excluded from the underlying policy's own live "in force"
     * evaluation the next time a permission check or {@link #refresh()}
     * sweeps it.
     *
     * @return a fresh, defensive-copy snapshot; never null, possibly empty
     */
    @Override
    public PermissionGrant[] getGrants() throws RemoteException {
        ProtectionDomain scopeDomain = new ProtectionDomain(
                new CodeSource(null, (Certificate[]) null),
                null, null, scopePrincipals);
        List<PermissionGrant> grants = policy.getPermissionGrants(scopeDomain);
        return grants.toArray(new PermissionGrant[grants.size()]);
    }
}
