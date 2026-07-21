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

import java.io.IOException;
import java.rmi.RemoteException;
import java.security.Permission;
import java.security.Principal;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.security.auth.AuthPermission;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.ServerContext;
import net.jini.security.AdminAuthorizationContext;
import au.net.zeus.jgdms.loader.isolation.AdminAuthTestSupport.MutableContextSource;
import org.apache.river.api.security.PermissionGrant;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Adversarial probes for the two security-critical pieces of task&nbsp;T2:
 * the canonical pooling-key derivation (requirement&nbsp;#2) and the
 * admin-authority binding including reject-on-load (requirement&nbsp;#3).
 */
public class IsolationSecurityCriticalTest {

    // ==================================================================
    // (#2) Canonical pooling-key derivation.
    // ==================================================================

    private static Principal name(final String n) {
        return new Principal() {
            public String getName() { return n; }
            public boolean equals(Object o) {
                return o instanceof Principal && n.equals(((Principal) o).getName());
            }
            public int hashCode() { return n.hashCode(); }
            public String toString() { return n; }
        };
    }

    @Test
    public void singleSpiffe_keyIsSpiffePrefixedName() throws Exception {
        IsolationPoolingKey k = IsolationPoolingKey.derive(
                new Principal[]{ name("spiffe://example/workload/a") });
        assertEquals("spiffe:spiffe://example/workload/a", k.value());
    }

    @Test
    public void zeroSpiffe_singleX500_fallsBackToCanonicalX500() throws Exception {
        X500Principal x = new X500Principal("CN=svc, O=Zeus, C=AU");
        IsolationPoolingKey k = IsolationPoolingKey.derive(new Principal[]{ x });
        assertEquals("x500:" + x.getName(X500Principal.CANONICAL), k.value());
        assertTrue(k.value().startsWith("x500:"));
    }

    @Test
    public void multipleSpiffe_failsClosed_noX500Fallback() {
        // An attacker adds a second spiffe:// SAN to suppress SPIFFE pooling.
        try {
            IsolationPoolingKey.derive(new Principal[]{
                    name("spiffe://example/a"),
                    name("spiffe://example/b") });
            fail("must fail closed on >1 SPIFFE principal");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("conformant")
                    || expected.getMessage().contains("SPIFFE"));
        }
    }

    @Test
    public void multipleSpiffePlusX500_stillFailsClosed_doesNotPickX500() {
        // Adding an X.500 principal must NOT provide an escape hatch when
        // SPIFFE is ambiguous: still fail closed.
        try {
            IsolationPoolingKey.derive(new Principal[]{
                    name("spiffe://example/a"),
                    name("spiffe://example/b"),
                    new X500Principal("CN=svc") });
            fail("must fail closed; must not silently pick the X.500 identity");
        } catch (IOException expected) {
            // good
        }
    }

    @Test
    public void determinism_orderInsensitive_sameKeyRegardlessOfIteration()
            throws Exception {
        // Simulates HashSet reordering: same certificate content, different
        // array order, must always yield the same key.
        Principal spiffe = name("spiffe://example/a");
        X500Principal x = new X500Principal("CN=svc, O=Zeus, C=AU");
        // With a single SPIFFE present, order among the (ignored) extras must
        // not matter.
        IsolationPoolingKey k1 = IsolationPoolingKey.derive(
                new Principal[]{ spiffe, x });
        IsolationPoolingKey k2 = IsolationPoolingKey.derive(
                new Principal[]{ x, spiffe });
        assertEquals(k1, k2);
        assertEquals(k1.value(), k2.value());
        assertEquals(k1.hashCode(), k2.hashCode());
    }

    @Test
    public void determinism_repeatedDerivationStable() throws Exception {
        Principal spiffe = name("spiffe://example/a");
        String first = IsolationPoolingKey.derive(
                new Principal[]{ spiffe }).value();
        for (int i = 0; i < 1000; i++) {
            assertEquals(first, IsolationPoolingKey.derive(
                    new Principal[]{ spiffe }).value());
        }
    }

    @Test
    public void spiffeDetectionIsByName_notInstanceof() throws Exception {
        // A plain Principal (not any SPIFFE class) whose name is a spiffe:// id
        // is treated as SPIFFE purely by name.
        IsolationPoolingKey k = IsolationPoolingKey.derive(
                new Principal[]{ name("spiffe://trust-domain/x") });
        assertEquals("spiffe:spiffe://trust-domain/x", k.value());
    }

    @Test
    public void zeroSpiffe_zeroX500_failsClosed() {
        try {
            IsolationPoolingKey.derive(new Principal[]{ name("plain-cn") });
            fail("no SPIFFE and no X.500 -> no unambiguous identity");
        } catch (IOException expected) { /* good */ }
    }

    @Test
    public void nullOrEmpty_failsClosed() {
        try { IsolationPoolingKey.derive(null); fail(); }
        catch (IOException e) { /* good */ }
        try { IsolationPoolingKey.derive(new Principal[0]); fail(); }
        catch (IOException e) { /* good */ }
    }

    // ==================================================================
    // (#3, layer 3) Reject-on-load: identity, never name.
    // ==================================================================

    interface Business { void doWork(); }
    interface RealSubType extends SubProcessAdministrable { }

    @Test
    public void guard_firesOnRealManagementInterface() {
        try {
            HostedProxyGuard.checkHostable(
                    new Class<?>[]{ Business.class, SubProcessAdministrable.class });
            fail("must reject a proxy declaring the real SubProcessAdministrable");
        } catch (SecurityException expected) {
            assertTrue(expected.getMessage().contains("SubProcessAdministrable"));
        }
    }

    @Test
    public void guard_firesOnRealPolicyAdmin() {
        try {
            HostedProxyGuard.checkHostable(new Class<?>[]{ PolicyAdmin.class });
            fail("must reject a proxy declaring PolicyAdmin");
        } catch (SecurityException expected) { /* good */ }
    }

    @Test
    public void guard_firesOnSubtypeOfManagementInterface() {
        try {
            HostedProxyGuard.checkHostable(new Class<?>[]{ RealSubType.class });
            fail("must reject a subtype of a management interface (isAssignableFrom)");
        } catch (SecurityException expected) { /* good */ }
    }

    @Test
    public void guard_doesNotFireOnBenignInterfaces() {
        // No false positives on ordinary business interfaces.
        HostedProxyGuard.checkHostable(
                new Class<?>[]{ Business.class, Runnable.class });
        HostedProxyGuard.checkHostableClosure(Business.class);
    }

    @Test
    public void guard_identityNotName_decoySameNamedInterfaceIsNotRejected() {
        // A decoy interface LITERALLY named "SubProcessAdministrable" but from
        // a different package (distinct Class identity) is harmless: it cannot
        // be cast to or dispatched as the real management interface.  An
        // identity-based guard must NOT reject it; a name-based guard would.
        Class<?> decoy = au.net.zeus.jgdms.loader.isolation.decoy
                .SubProcessAdministrable.class;
        assertEquals("SubProcessAdministrable", decoy.getSimpleName());
        assertNotSame("decoy must be a different Class identity",
                SubProcessAdministrable.class, decoy);
        // Must not throw: identity-based guard ignores the same-named decoy.
        HostedProxyGuard.checkHostable(new Class<?>[]{ Business.class, decoy });
        HostedProxyGuard.checkHostableClosure(decoy);
    }

    @Test
    public void guard_closureCatchesInheritedManagementInterface() {
        // A business interface that (transitively) extends the real management
        // interface must be caught by the closure check.
        try {
            HostedProxyGuard.checkHostableClosure(RealSubType.class);
            fail("closure must catch an inherited management interface");
        } catch (SecurityException expected) { /* good */ }
    }

    // ==================================================================
    // (#3, S1) Admin-authority binding: authentication is the boundary.
    // ==================================================================

    /** Trusted management object; distinct from any business proxy. */
    static final class TrustedPolicyBacking implements PolicyAdmin {
        volatile int grantCalls;
        public void grant(PermissionGrant g) { grantCalls++; }
        public void refresh() { }
        public PermissionGrant[] getGrants() { return new PermissionGrant[0]; }
    }

    private static Subject subjectWith(Principal... ps) {
        Set<Principal> set = new LinkedHashSet<Principal>(Arrays.asList(ps));
        return new Subject(true, set, Collections.<Object>emptySet(),
                Collections.<Object>emptySet());
    }

    private static AdminAuthorizationContext carrierFor(Principal... ps) {
        return AdminAuthTestSupport.carrierFor(ps);
    }

    private final Principal admin = name("spiffe://ctrl/admin");

    // ==================================================================
    // SecurityManager test scaffolding.
    // 2026-07-20 board finding fix: AdminPrincipalAuthenticator requires an
    // installed SecurityManager before it will trust ANY ambient Subject
    // (see its class javadoc); tests probing the no-SM fail-closed behaviour
    // install none (the JVM default).
    // 2026-07-21 ratified redesign: authorization is now a per-operation
    // SubProcessAdminPermission check against a synthetic principal-only
    // context, submitted to the installed SecurityManager -- so tests
    // exercising the legitimate admin success/refusal paths use
    // AdminAuthTestSupport (context-evaluating SecurityManager + a Policy
    // granting the operation targets to the admin principal only). The
    // AllowAll/DenySubjectRebind doubles below remain for the tests that
    // probe the SecurityManager-level boundaries themselves.
    // ==================================================================

    /** Allows everything: used where the test only needs SM presence. */
    private static final class AllowAllSecurityManager extends SecurityManager {
        @Override public void checkPermission(Permission perm) { }
        @Override public void checkPermission(Permission perm, Object ctx) { }
    }

    /**
     * Denies Subject-rebinding permissions only ({@code AuthPermission}
     * {@code ("callAs")} -- what {@code Subject.callAs}, the JDK&nbsp;18+ API
     * this codebase actually binds through, checks -- and
     * {@code AuthPermission("doAs")}, checked by the deprecated
     * {@code Subject.doAs(Subject, PrivilegedAction)} overload), everything
     * else allowed. Models a deployment where hosted/business code was never
     * granted the authority to rebind the ambient {@link Subject}.
     */
    private static final class DenySubjectRebindSecurityManager extends SecurityManager {
        @Override public void checkPermission(Permission perm) {
            if (perm instanceof AuthPermission
                    && ("callAs".equals(perm.getName()) || "doAs".equals(perm.getName()))) {
                throw new SecurityException(
                        "AuthPermission(\"" + perm.getName() + "\") denied by policy (test SM)");
            }
        }
        @Override public void checkPermission(Permission perm, Object ctx) {
            checkPermission(perm);
        }
    }

    /**
     * Installs {@code sm} for the duration of {@code body}, restoring
     * whatever was previously installed afterwards. Skips (via
     * {@link Assume}) on a JDK where {@code setSecurityManager} is
     * unsupported without {@code -Djava.security.manager=allow} -- mirrors
     * the existing pattern in {@code SubProcessLocalPolicyAdminTest}.
     */
    private static void withSecurityManager(SecurityManager sm, RunnableEx body)
            throws Exception {
        SecurityManager previous = System.getSecurityManager();
        try {
            System.setSecurityManager(sm);
        } catch (UnsupportedOperationException noAllowFlag) {
            Assume.assumeNoException(
                    "needs -Djava.security.manager=allow", noAllowFlag);
            return;
        }
        try {
            body.run();
        } finally {
            // Some JDKs (e.g. the DirtyChai build) refuse to revert an
            // installed SecurityManager back to null once one has been set;
            // only restore when there is something concrete to restore to
            // (mirrors the existing pattern in
            // SubProcessLocalPolicyAdminTest.grant_stillSurfacesInstallCeilingDenial).
            if (previous != null) {
                System.setSecurityManager(previous);
            }
        }
    }

    private interface RunnableEx {
        void run() throws Exception;
    }

    @Test
    public void unauthenticatedCaller_getAdminFailsClosed() throws Exception {
        AdminAuthTestSupport.withAdminAuthorization(admin, () -> {
            MutableContextSource caller = new MutableContextSource();  // ctx == null
            SubProcessPolicyAdmin s = new SubProcessPolicyAdmin(
                    admin, new TrustedPolicyBacking(), caller);
            try {
                s.getSubProcessPolicyAdmin();
                fail("unauthenticated caller must not receive a usable admin proxy");
            } catch (SecurityException expected) { /* good */ }
            catch (RemoteException e) { fail("unexpected: " + e); }
        });
    }

    @Test
    public void wronglyAuthenticatedCaller_getAdminFailsClosed() throws Exception {
        AdminAuthTestSupport.withAdminAuthorization(admin, () -> {
            MutableContextSource caller = new MutableContextSource(
                    carrierFor(name("spiffe://ctrl/not-admin")));
            SubProcessPolicyAdmin s = new SubProcessPolicyAdmin(
                    admin, new TrustedPolicyBacking(), caller);
            try {
                s.getSubProcessPolicyAdmin();
                fail("ungranted principal must not receive a usable admin proxy");
            } catch (SecurityException expected) { /* good */ }
            catch (RemoteException e) { fail("unexpected: " + e); }
        });
    }

    @Test
    public void adminCaller_getsUsableProxy_operationsDispatch() throws Exception {
        AdminAuthTestSupport.withAdminAuthorization(admin, () -> {
            MutableContextSource caller = new MutableContextSource(carrierFor(admin));
            TrustedPolicyBacking backing = new TrustedPolicyBacking();
            SubProcessPolicyAdmin s = new SubProcessPolicyAdmin(admin, backing, caller);

            PolicyAdmin pa = s.getSubProcessPolicyAdmin();
            assertNotNull(pa);
            pa.grant(null);
            assertEquals("admin op must dispatch to the trusted backing object",
                    1, backing.grantCalls);
        });
    }

    @Test
    public void capturedProxy_reGatesOnEveryOperation_forNonAdmin()
            throws Exception {
        // An admin obtains the proxy, then the seam-supplied identity drops to
        // a non-admin (e.g. a subsequent call on the same source carries a
        // different caller's carrier).  Every operation must re-check and fail
        // closed.
        AdminAuthTestSupport.withAdminAuthorization(admin, () -> {
            MutableContextSource caller = new MutableContextSource(carrierFor(admin));
            TrustedPolicyBacking backing = new TrustedPolicyBacking();
            SubProcessPolicyAdmin s = new SubProcessPolicyAdmin(admin, backing, caller);

            PolicyAdmin pa = s.getSubProcessPolicyAdmin();     // captured while admin
            caller.ctx = carrierFor(name("spiffe://ctrl/attacker"));
            try {
                pa.grant(null);
                fail("captured proxy must re-gate and refuse a non-admin caller");
            } catch (SecurityException expected) { /* good */ }
            assertEquals("no operation must have reached the backing object",
                    0, backing.grantCalls);
        });
    }

    @Test
    public void adminProxyCarriesStricterConstraints_integrityAndAdminPrincipal()
            throws Exception {
        AdminAuthTestSupport.withAdminAuthorization(admin, () -> {
            MutableContextSource caller = new MutableContextSource(carrierFor(admin));
            SubProcessPolicyAdmin s = new SubProcessPolicyAdmin(
                    admin, new TrustedPolicyBacking(), caller);

            PolicyAdmin pa = s.getSubProcessPolicyAdmin();
            assertTrue("admin proxy must be a RemoteMethodControl",
                    pa instanceof RemoteMethodControl);
            MethodConstraints mc = ((RemoteMethodControl) pa).getConstraints();
            assertNotNull(mc);
            InvocationConstraints ic = mc.getConstraints(
                    PolicyAdmin.class.getMethod("grant", PermissionGrant.class));
            assertTrue("must require Integrity.YES",
                    ic.requirements().contains(Integrity.YES));
            assertTrue("must require the admin principal via ClientMinPrincipal",
                    ic.requirements().contains(new ClientMinPrincipal(admin)));
        });
    }

    @Test
    public void constraintsCannotBeWeakenedByClient() throws Exception {
        AdminAuthTestSupport.withAdminAuthorization(admin, () -> {
            MutableContextSource caller = new MutableContextSource(carrierFor(admin));
            SubProcessPolicyAdmin s = new SubProcessPolicyAdmin(
                    admin, new TrustedPolicyBacking(), caller);
            RemoteMethodControl pa = (RemoteMethodControl) s.getSubProcessPolicyAdmin();
            // Attempt to relax constraints to empty; the fixed admin floor must hold.
            RemoteMethodControl weakened = pa.setConstraints(
                    new net.jini.constraint.BasicMethodConstraints(
                            InvocationConstraints.EMPTY));
            MethodConstraints mc = weakened.getConstraints();
            InvocationConstraints ic = mc.getConstraints(
                    PolicyAdmin.class.getMethod("refresh"));
            assertTrue("setConstraints must not weaken below the admin floor",
                    ic.requirements().contains(Integrity.YES));
        });
    }

    // ==================================================================
    // (#3, S1) Fail-closed without a SecurityManager, and the Option B
    // treatment of ambient-Subject forgery and SecurityManager boundaries.
    // ==================================================================

    /**
     * The core regression for the 2026-07-20 board finding, retained under
     * Option&nbsp;B: with NO SecurityManager installed
     * {@link AdminPrincipalAuthenticator} must refuse EVERY caller, including
     * one whose seam-assembled carrier genuinely carries the admin principal
     * -- with no SecurityManager there is nothing to submit the permission
     * check to.
     */
    @Test
    public void noSecurityManagerInstalled_refusesEvenAGenuineAdminSubject() {
        // Surefire reuses one JVM across this whole module by default, and some
        // JDKs (e.g. the DirtyChai build) refuse to revert an installed
        // SecurityManager back to null once one has been set -- so an earlier
        // test in this shared JVM may have permanently left one installed.
        Assume.assumeTrue("skipped: a SecurityManager is already installed in"
                + " this (possibly shared/reused) test JVM by an earlier test",
                System.getSecurityManager() == null);
        MutableContextSource caller = new MutableContextSource(carrierFor(admin));
        TrustedPolicyBacking backing = new TrustedPolicyBacking();
        SubProcessPolicyAdmin s = new SubProcessPolicyAdmin(admin, backing, caller);
        try {
            s.getSubProcessPolicyAdmin();
            fail("must refuse even a genuine admin carrier when no"
                    + " SecurityManager is installed (fail-closed)");
        } catch (SecurityException expected) {
            assertTrue("exception must explain the SecurityManager is missing",
                    expected.getMessage().contains("SecurityManager"));
        } catch (RemoteException e) {
            fail("unexpected: " + e);
        }
        assertEquals(0, backing.grantCalls);
    }

    /**
     * Option&nbsp;B security property (stronger than the R2 design it
     * replaces): the pure gate <strong>ignores the ambient {@link Subject}
     * entirely</strong>.  Even a forged ambient subject that names the admin,
     * successfully bound via {@code Subject.callAs} (this test's SM allows the
     * binding), does NOT authorize the caller: the gate authorizes only over
     * the seam-supplied carrier, which the attacker cannot forge.  So under
     * Option&nbsp;B the ambient-forgery vector the R2 design depended on
     * denying {@code AuthPermission("callAs")} to defeat is closed by
     * construction -- forging the ambient subject achieves nothing.  Here the
     * real default source {@link AdminPrincipalAuthenticator#CURRENT_CONTEXT}
     * finds no carrier (no ServerContext for this thread) and fails closed.
     */
    @Test
    public void forgedAmbientSubject_ignoredByPureGate_evenWhenBound()
            throws Exception {
        AdminAuthTestSupport.withAdminAuthorization(admin, () -> {
            TrustedPolicyBacking backing = new TrustedPolicyBacking();
            final SubProcessPolicyAdmin realAdminSurface = new SubProcessPolicyAdmin(
                    admin, backing, AdminPrincipalAuthenticator.CURRENT_CONTEXT);

            Subject forged = subjectWith(admin); // attacker-forged, names the admin
            Object outcome = callAs(forged, new Callable<Object>() {
                public Object call() {
                    try {
                        PolicyAdmin pa = realAdminSurface.getSubProcessPolicyAdmin();
                        pa.grant(null);
                        return "FORGED AMBIENT SUBJECT REACHED grant()";
                    } catch (Exception e) {
                        return e;
                    }
                }
            });
            if (outcome == SKIP) return;   // callAs unavailable on this JDK
            assertTrue("a forged ambient Subject must NOT authorize the pure"
                    + " gate (it authorizes over the seam carrier only): "
                    + outcome, outcome instanceof SecurityException);
            assertEquals("the forged ambient caller must never reach the backing",
                    0, backing.grantCalls);
        });
    }

    /**
     * Boundary, retained under Option&nbsp;B: "a SecurityManager is installed"
     * is NECESSARY but NOT SUFFICIENT.  A permissive {@code SecurityManager}
     * (two-argument {@code checkPermission} a no-op) authorizes EVERY carrier
     * -- even one carrying a non-admin principal -- because the deployed policy
     * is the whole guarantee.  PERMANENT regression: intentionally asserts the
     * check SUCCEEDS for a non-admin, documenting a known boundary (see the
     * class javadoc's "NECESSARY, not SUFFICIENT" section).
     */
    @Test
    public void permissiveSecurityManager_authorizesEveryCarrier()
            throws Exception {
        withSecurityManager(new AllowAllSecurityManager(), () -> {
            TrustedPolicyBacking backing = new TrustedPolicyBacking();
            // A carrier for a principal the (real) admin policy never granted;
            // under a permissive SM it is authorized regardless.
            MutableContextSource caller = new MutableContextSource(
                    carrierFor(name("spiffe://ctrl/not-admin")));
            SubProcessPolicyAdmin s = new SubProcessPolicyAdmin(admin, backing, caller);

            PolicyAdmin pa = s.getSubProcessPolicyAdmin();  // permissive SM -> granted
            pa.grant(null);
            assertEquals("a permissive-but-installed SecurityManager authorizes"
                    + " every carrier -- documented boundary; closing it is the"
                    + " deployed policy's job", 1, backing.grantCalls);
        });
    }

    /**
     * Boundary, retained under Option&nbsp;B: a policy that fails to deny
     * {@code RuntimePermission("setSecurityManager")} to hosted code lets that
     * code replace the restrictive {@code SecurityManager} with a permissive
     * one and authorize every carrier.  PERMANENT regression documenting the
     * board-confirmed boundary.
     */
    @Test
    public void hostedCodeReplacingTheSecurityManager_authorizesEveryCarrier()
            throws Exception {
        withSecurityManager(new DenySubjectRebindSecurityManager(), () -> {
            // "Hosted/business code" replaces the restrictive SM with a
            // permissive one -- succeeds because RuntimePermission
            // ("setSecurityManager") was never denied by the SM being replaced.
            System.setSecurityManager(new AllowAllSecurityManager());

            TrustedPolicyBacking backing = new TrustedPolicyBacking();
            MutableContextSource caller = new MutableContextSource(
                    carrierFor(name("spiffe://ctrl/not-admin")));
            SubProcessPolicyAdmin s = new SubProcessPolicyAdmin(admin, backing, caller);

            PolicyAdmin pa = s.getSubProcessPolicyAdmin();
            pa.grant(null);
            assertEquals("replacing the SecurityManager with a permissive one"
                    + " (RuntimePermission(\"setSecurityManager\") not denied)"
                    + " authorizes every carrier -- documented boundary",
                    1, backing.grantCalls);
        });
    }

    /**
     * Second, independent line of defence: under a correct restrictive policy,
     * the gate refuses a caller whose seam-supplied carrier carries principals
     * the deployed policy has not granted {@code SubProcessAdminPermission} --
     * the per-operation permission grant itself, not a name match.
     */
    @Test
    public void nonAdminCarrier_refused_underRestrictiveSM()
            throws Exception {
        AdminAuthTestSupport.withAdminAuthorization(admin, () -> {
            MutableContextSource caller = new MutableContextSource(
                    carrierFor(name("spiffe://ctrl/attacker")));
            TrustedPolicyBacking backing = new TrustedPolicyBacking();
            SubProcessPolicyAdmin s = new SubProcessPolicyAdmin(admin, backing, caller);
            try {
                s.getSubProcessPolicyAdmin();
                fail("a carrier for an ungranted principal must be refused");
            } catch (SecurityException expected) { /* good */ }
            assertEquals(0, backing.grantCalls);
        });
    }

    /**
     * Binds {@code subject} as the current Subject via {@code Subject.callAs}
     * (JDK 18+ / DirtyChai) reflectively, so this test stays {@code -release 8}
     * source-compatible.  Returns {@link #SKIP} if {@code callAs} is unavailable.
     */
    private static Object callAs(Subject subject, Callable<Object> action)
            throws Exception {
        java.lang.reflect.Method callAs;
        try {
            callAs = Subject.class.getMethod("callAs", Subject.class, Callable.class);
        } catch (NoSuchMethodException e) {
            return SKIP;   // pre-18 JDK: not exercised here
        }
        return callAs.invoke(null, subject, action);
    }
    private static final Object SKIP = new Object();

    // ==================================================================
    // (Option B) The real default source resolves the seam-assembled carrier
    // from the ServerContext -- the production handoff the seam performs via
    // context.add(adminAuthorizationContext) in BasicInvocationDispatcher.
    // ==================================================================

    /**
     * Exercises the real production default
     * {@link AdminPrincipalAuthenticator#CURRENT_CONTEXT} end-to-end through the
     * {@code ServerContext} handoff the JERI seam performs: the seam publishes
     * the assembled {@link AdminAuthorizationContext} as a server-context
     * element, and {@code CURRENT_CONTEXT} resolves it.  An admin carrier in
     * context obtains the proxy; a non-admin carrier fails closed; no context at
     * all fails closed (and never throws from identity acquisition).
     */
    @Test
    public void realDefaultSource_resolvesSeamCarrierFromServerContext()
            throws Exception {
        AdminAuthTestSupport.withAdminAuthorization(admin, () -> {
            final SubProcessPolicyAdmin s = new SubProcessPolicyAdmin(
                    admin, new TrustedPolicyBacking(),
                    AdminPrincipalAuthenticator.CURRENT_CONTEXT);

            // No server context at all -> no carrier -> fail closed.
            try {
                s.getSubProcessPolicyAdmin();
                fail("no seam carrier (no ServerContext) must fail closed");
            } catch (SecurityException expected) { /* good */ }

            // Seam publishes an admin carrier -> resolved -> proxy obtained.
            final Object[] ok = { null };
            ServerContext.doWithServerContext(new Runnable() {
                public void run() {
                    try { ok[0] = s.getSubProcessPolicyAdmin(); }
                    catch (Exception e) { ok[0] = e; }
                }
            }, Collections.singletonList((Object) carrierFor(admin)));
            assertTrue("admin carrier in ServerContext must obtain the proxy: "
                    + ok[0], ok[0] instanceof PolicyAdmin);

            // Seam publishes an ungranted carrier -> fail closed.
            final Object[] bad = { null };
            ServerContext.doWithServerContext(new Runnable() {
                public void run() {
                    try { bad[0] = s.getSubProcessPolicyAdmin(); }
                    catch (Exception e) { bad[0] = e; }
                }
            }, Collections.singletonList(
                    (Object) carrierFor(name("spiffe://ctrl/x"))));
            assertTrue("ungranted carrier in ServerContext must fail closed",
                    bad[0] instanceof SecurityException);
        });
    }

    /**
     * The default source must fail closed (resolve no carrier) and never throw
     * when called outside any remote call (no server context set for the
     * thread) -- graceful degradation on both JDK shapes.
     */
    @Test
    public void defaultSource_outsideRemoteCall_failsClosed_neverThrows()
            throws Exception {
        AdminAuthTestSupport.withAdminAuthorization(admin, () -> {
            // CURRENT_CONTEXT.current() must not throw; must return null.
            assertNull("no ServerContext -> no carrier",
                    AdminPrincipalAuthenticator.CURRENT_CONTEXT.current());
            AdminPrincipalAuthenticator auth = new AdminPrincipalAuthenticator(
                    AdminPrincipalAuthenticator.CURRENT_CONTEXT);
            assertFalse("no carrier -> not admin, fail closed",
                    auth.isAdmin("grant"));
        });
    }
}
