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

import java.security.AccessControlContext;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.security.auth.Subject;
import net.jini.security.AccessPermission;
import net.jini.security.AdminAuthorizationContext;
import org.junit.Assume;

/**
 * Shared scaffolding for tests exercising the 2026-07-21 redesigned
 * {@link AdminPrincipalAuthenticator}: authorization is now a
 * {@link SubProcessAdminPermission} check submitted to the installed
 * {@link SecurityManager}'s two-argument {@code checkPermission(perm, ctx)}
 * against a synthetic principal-only {@link AccessControlContext}, so tests
 * that previously only needed "some SecurityManager installed" now need:
 * <ol>
 *   <li>a {@link SecurityManager} whose two-argument check actually
 *       evaluates the supplied context (mirroring the default
 *       {@code java.lang.SecurityManager} / DirtyChai's
 *       {@code CombinerSecurityManager}), and</li>
 *   <li>a {@link Policy} that grants {@code SubProcessAdminPermission}
 *       targets to the admin principal's protection domains only.</li>
 * </ol>
 * Both are installed/restored together by
 * {@link #withAdminAuthorization(Principal, RunnableEx)} (or the
 * finer-grained {@link #withAuthorization(Policy, RunnableEx)}), preserving
 * the pre-existing suite conventions: {@link Assume}-skip when
 * {@code setSecurityManager} is unsupported (needs
 * {@code -Djava.security.manager=allow} on vanilla JDK 18..23; always
 * available on DirtyChai; never available on vanilla JDK 24+), and never
 * attempting to revert an installed SecurityManager back to {@code null}
 * (some JDKs, e.g. the DirtyChai build, refuse that).
 */
final class AdminAuthTestSupport {

    private AdminAuthTestSupport() { }

    interface RunnableEx {
        void run() throws Exception;
    }

    /**
     * One-argument (ambient/stack) checks allow everything, keeping the
     * scaffold focused on the admin gate; the two-argument check evaluates
     * the supplied context exactly as the default
     * {@code java.lang.SecurityManager} does &mdash; but ONLY for the
     * {@link AccessPermission} family under test (mirroring
     * {@link PrincipalGrantPolicy}'s allow-everything-else stance), which
     * routes the synthetic caller domain's {@code implies} through the
     * installed {@link Policy}.
     *
     * <p>The {@code instanceof} filter is load-bearing, not a convenience
     * (2026-07-21 board round&nbsp;2, R4): the DirtyChai JDK itself routes
     * unrelated two-argument checks through the installed SecurityManager
     * &mdash; e.g. {@code MethodHandles.Lookup}'s {@code ClassDefiner}
     * submits {@code au.zeus.jdk.authorization.guards.DefineClassPermission}
     * with a context over the lookup class's domain during <em>lambda class
     * generation</em> &mdash; and once installed this SecurityManager stays
     * resident for the remainder of the shared surefire JVM (the DirtyChai
     * build refuses reverting an installed SecurityManager to {@code null}).
     * An unconditional context evaluation therefore denied lambda bootstrap
     * and locale-provider initialisation in whatever tests happened to run
     * <em>later</em>, with the restored default {@link Policy} in force; the
     * previously-green suite was JUnit method-order luck, reproduced as
     * {@code AccessControlException: access denied ("...DefineClassPermission"
     * "ALLOW")} the moment the order shifted.
     */
    static class ContextEvaluatingSecurityManager extends SecurityManager {
        @Override public void checkPermission(Permission perm) { }
        @Override public void checkPermission(Permission perm, Object context) {
            if (perm instanceof AccessPermission) {
                ((AccessControlContext) context).checkPermission(perm);
            }
        }
    }

    /**
     * Grants the supplied permissions to any {@link ProtectionDomain}
     * carrying a principal with one of the grantee names (matching the
     * principal-only synthetic domain {@link AdminPrincipalAuthenticator}
     * builds); every non-{@link AccessPermission} check is allowed so
     * unrelated JVM machinery keeps working while this policy is installed.
     * {@link AccessPermission} checks for domains without a matching
     * principal are refused &mdash; the fail-closed default a real deployed
     * policy would have.
     */
    static final class PrincipalGrantPolicy extends Policy {
        private final Set<String> granteeNames;
        private final PermissionCollection granted;

        PrincipalGrantPolicy(Principal grantee, Permission... perms) {
            this.granteeNames = new LinkedHashSet<String>(
                    Arrays.asList(grantee.getName()));
            Permissions p = new Permissions();
            for (Permission perm : perms) {
                p.add(perm);
            }
            p.setReadOnly();
            this.granted = p;
        }

        @Override
        public boolean implies(ProtectionDomain domain, Permission permission) {
            if (!(permission instanceof AccessPermission)) {
                return true;    // unrelated machinery: not under test here
            }
            Principal[] ps = domain == null ? null : domain.getPrincipals();
            if (ps != null) {
                for (Principal p : ps) {
                    if (granteeNames.contains(p.getName())) {
                        return granted.implies(permission);
                    }
                }
            }
            return false;       // fail-closed for the permission under test
        }

        @Override
        public PermissionCollection getPermissions(CodeSource codesource) {
            return new Permissions();
        }

        @Override
        public PermissionCollection getPermissions(ProtectionDomain domain) {
            return new Permissions();
        }

        @Override
        public void refresh() { }
    }

    /** The empty {@link CodeSource} the seam-style caller domains are built over. */
    static final CodeSource EMPTY_CS = new CodeSource(null, (Certificate[]) null);

    /**
     * Builds a <em>genuinely seam-minted</em> {@link AdminAuthorizationContext}
     * carrier the way {@code net.jini.jeri.AdminAuthorizationAssembler} would: a
     * single reducing domain over the empty {@link CodeSource} stamped with
     * {@code principals} (the union of worker + verified-user principals), plus a
     * matching verified user {@link Subject} so the gate's
     * {@code Subject.callAs} path (recipe step&nbsp;4) is exercised exactly as in
     * production.  It is produced via {@link AdminAuthorizationContext#seamMint}
     * so it carries the provenance token the gate requires; under the test
     * {@code ContextEvaluatingSecurityManager} the mint permission check is a
     * no-op, mirroring the seam holding the grant.  Passing no principals yields
     * an authenticated-but-empty identity (see {@link #emptyCarrier()}).
     */
    static AdminAuthorizationContext carrierFor(Principal... principals) {
        Principal[] ps = (principals == null) ? new Principal[0] : principals.clone();
        ProtectionDomain pd = new ProtectionDomain(EMPTY_CS, null, null, ps);
        Subject user = new Subject(true,
                new LinkedHashSet<Principal>(Arrays.asList(ps)),
                Collections.emptySet(), Collections.emptySet());
        return AdminAuthorizationContext.seamMint(
                new ProtectionDomain[] { pd },
                new Subject[] { user });
    }

    /**
     * Builds a carrier the way <em>hosted code</em> could forge it: through the
     * public {@link AdminAuthorizationContext} constructor, so it carries NO
     * provenance token ({@link AdminAuthorizationContext#isSeamMinted()} is
     * {@code false}).  The gate MUST refuse it however it is conveyed &mdash; the
     * exact prior exploit shape.
     */
    static AdminAuthorizationContext forgedCarrier(Principal... principals) {
        Principal[] ps = (principals == null) ? new Principal[0] : principals.clone();
        ProtectionDomain pd = new ProtectionDomain(EMPTY_CS, null, null, ps);
        Subject user = new Subject(true,
                new LinkedHashSet<Principal>(Arrays.asList(ps)),
                Collections.emptySet(), Collections.emptySet());
        return new AdminAuthorizationContext(
                new ProtectionDomain[] { pd },
                new Subject[] { user });
    }

    /**
     * A <em>seam-minted</em> carrier that carries NO principals at all (empty
     * domain, no user subjects): the seam-shaped equivalent of an authenticated
     * but no-grant caller, used to assert the gate fails closed even though a
     * genuine carrier is present.
     */
    static AdminAuthorizationContext emptyCarrier() {
        ProtectionDomain pd = new ProtectionDomain(EMPTY_CS, null, null, null);
        return AdminAuthorizationContext.seamMint(
                new ProtectionDomain[] { pd }, new Subject[0]);
    }

    /**
     * A mutable {@link AdminPrincipalAuthenticator.AdminContextSource} test
     * double: the Option&nbsp;B replacement for the old {@code CallerIdentity}
     * {@code Caller} stub.  Set {@link #ctx} to the carrier the seam would have
     * supplied (or leave it {@code null} for an unauthenticated caller).
     */
    static final class MutableContextSource
            implements AdminPrincipalAuthenticator.AdminContextSource {
        volatile AdminAuthorizationContext ctx;
        MutableContextSource() { }
        MutableContextSource(AdminAuthorizationContext ctx) { this.ctx = ctx; }
        @Override public AdminAuthorizationContext current() { return ctx; }
    }

    /** The four operation names {@code SubProcessPolicyAdmin} threads through. */
    static SubProcessAdminPermission[] allAdminOperations() {
        return new SubProcessAdminPermission[] {
            new SubProcessAdminPermission("getSubProcessPolicyAdmin"),
            new SubProcessAdminPermission("grant"),
            new SubProcessAdminPermission("refresh"),
            new SubProcessAdminPermission("getGrants"),
        };
    }

    /**
     * Installs a {@link ContextEvaluatingSecurityManager} plus a
     * {@link PrincipalGrantPolicy} granting all four
     * {@link SubProcessAdminPermission} operation targets to
     * {@code adminPrincipal}, runs {@code body}, and restores the previous
     * {@link Policy} (and, where the JDK permits, the previous
     * {@link SecurityManager}) afterwards.
     */
    static void withAdminAuthorization(Principal adminPrincipal, RunnableEx body)
            throws Exception {
        withAuthorization(
                new PrincipalGrantPolicy(adminPrincipal, allAdminOperations()),
                body);
    }

    /**
     * Like {@link #withAdminAuthorization(Principal, RunnableEx)} but with a
     * caller-supplied {@link Policy}, for tests probing partial /
     * wrong-class / absent grants.
     */
    static void withAuthorization(Policy policy, RunnableEx body)
            throws Exception {
        SecurityManager previousSm = System.getSecurityManager();
        try {
            System.setSecurityManager(new ContextEvaluatingSecurityManager());
        } catch (UnsupportedOperationException noAllowFlag) {
            Assume.assumeNoException(
                    "needs -Djava.security.manager=allow", noAllowFlag);
            return;
        }
        Policy previousPolicy = Policy.getPolicy();
        Policy.setPolicy(policy);
        try {
            body.run();
        } finally {
            Policy.setPolicy(previousPolicy);
            // Some JDKs (e.g. the DirtyChai build) refuse to revert an
            // installed SecurityManager back to null once one has been set;
            // only restore when there is something concrete to restore to
            // (pre-existing suite convention).
            if (previousSm != null) {
                System.setSecurityManager(previousSm);
            }
        }
    }
}
