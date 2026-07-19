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

    /** Injectable caller identity holder. */
    static final class Caller implements AdminPrincipalAuthenticator.CallerIdentity {
        volatile Subject subject;
        public Subject current() { return subject; }
    }

    private final Principal admin = name("spiffe://ctrl/admin");

    // ==================================================================
    // SecurityManager test scaffolding (2026-07-20 board finding fix):
    // AdminPrincipalAuthenticator now requires an installed SecurityManager
    // before it will trust ANY ambient Subject (see its class javadoc). Tests
    // that exercise the legitimate "caller IS the admin" success path must
    // install one; tests that specifically probe the no-SM fail-closed
    // behaviour install none (the JVM default).
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
        withSecurityManager(new AllowAllSecurityManager(), () -> {
            Caller caller = new Caller();       // subject == null
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
        withSecurityManager(new AllowAllSecurityManager(), () -> {
            Caller caller = new Caller();
            caller.subject = subjectWith(name("spiffe://ctrl/not-admin"));
            SubProcessPolicyAdmin s = new SubProcessPolicyAdmin(
                    admin, new TrustedPolicyBacking(), caller);
            try {
                s.getSubProcessPolicyAdmin();
                fail("wrong principal must not receive a usable admin proxy");
            } catch (SecurityException expected) { /* good */ }
            catch (RemoteException e) { fail("unexpected: " + e); }
        });
    }

    @Test
    public void adminCaller_getsUsableProxy_operationsDispatch() throws Exception {
        withSecurityManager(new AllowAllSecurityManager(), () -> {
            Caller caller = new Caller();
            caller.subject = subjectWith(admin);
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
        // An admin obtains the proxy, then the caller identity drops to a
        // non-admin (e.g. the reference leaks / is replayed by another party).
        // Every subsequent operation must re-check and fail closed.
        withSecurityManager(new AllowAllSecurityManager(), () -> {
            Caller caller = new Caller();
            caller.subject = subjectWith(admin);
            TrustedPolicyBacking backing = new TrustedPolicyBacking();
            SubProcessPolicyAdmin s = new SubProcessPolicyAdmin(admin, backing, caller);

            PolicyAdmin pa = s.getSubProcessPolicyAdmin();     // captured while admin
            caller.subject = subjectWith(name("spiffe://ctrl/attacker"));
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
        withSecurityManager(new AllowAllSecurityManager(), () -> {
            Caller caller = new Caller();
            caller.subject = subjectWith(admin);
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
        withSecurityManager(new AllowAllSecurityManager(), () -> {
            Caller caller = new Caller();
            caller.subject = subjectWith(admin);
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
    // (#3, S1, 2026-07-20 board finding) Fail-closed without a
    // SecurityManager, and blocking the forged-Subject exploit.
    // ==================================================================

    /**
     * The core regression for the board finding: with NO SecurityManager
     * installed -- today's default state for, e.g., the subprocess dispatch
     * thread in {@code SubProcessReconstructionServer#dispatchInvoke} --
     * {@link AdminPrincipalAuthenticator} must refuse EVERY caller, including
     * one whose {@link Subject} genuinely (not via any forgery) carries the
     * admin principal. Without an installed SecurityManager this class has
     * no way to tell a genuine binding from a forged one, so it must not
     * trust either.
     */
    @Test
    public void noSecurityManagerInstalled_refusesEvenAGenuineAdminSubject() {
        // Surefire reuses one JVM across this whole module by default, and
        // some JDKs (e.g. the DirtyChai build) refuse to revert an installed
        // SecurityManager back to null once one has been set -- so an
        // earlier test in this shared JVM may have permanently left one
        // installed. Skip rather than force a fragile run-order dependency;
        // the "no SecurityManager installed at all" scenario (today's actual
        // default deployment state) is independently, authoritatively
        // reproduced in a pristine JVM as part of this fix's verification.
        Assume.assumeTrue("skipped: a SecurityManager is already installed in"
                + " this (possibly shared/reused) test JVM by an earlier test",
                System.getSecurityManager() == null);
        Caller caller = new Caller();
        caller.subject = subjectWith(admin);   // genuinely the admin principal
        TrustedPolicyBacking backing = new TrustedPolicyBacking();
        SubProcessPolicyAdmin s = new SubProcessPolicyAdmin(admin, backing, caller);
        try {
            s.getSubProcessPolicyAdmin();
            fail("must refuse even a genuine admin Subject when no"
                    + " SecurityManager is installed (fail-closed on an"
                    + " unverifiable ambient Subject)");
        } catch (SecurityException expected) {
            assertTrue("exception must explain the SecurityManager is missing",
                    expected.getMessage().contains("SecurityManager"));
        } catch (RemoteException e) {
            fail("unexpected: " + e);
        }
        assertEquals(0, backing.grantCalls);
    }

    /**
     * Reproduces the T4 board finding's exploit shape end-to-end through the
     * REAL production wiring -- {@link AdminPrincipalAuthenticator#CURRENT_SUBJECT}
     * (not the injectable test stub) and {@code Subject.callAs} (JDK&nbsp;18+,
     * exactly what a hosted business object's own method body would use) --
     * with a {@link SecurityManager} installed whose policy denies
     * {@code AuthPermission("callAs")}/{@code AuthPermission("doAs")} to the
     * calling code, exactly as a least-privilege policy would for
     * hosted/business protection domains. The forged {@link Subject} must
     * never even successfully bind: {@code Subject.callAs} itself throws
     * before {@link AdminPrincipalAuthenticator} is ever reached.
     */
    @Test
    public void forgedSubjectViaCallAs_blockedBeforeReachingTheGate_whenSMDeniesRebind()
            throws Exception {
        withSecurityManager(new DenySubjectRebindSecurityManager(), () -> {
            TrustedPolicyBacking backing = new TrustedPolicyBacking();
            final SubProcessPolicyAdmin realAdminSurface = new SubProcessPolicyAdmin(
                    admin, backing, AdminPrincipalAuthenticator.CURRENT_SUBJECT);

            Subject forged = subjectWith(admin); // attacker-forged, names the admin
            try {
                Object outcome = callAs(forged, new Callable<Object>() {
                    public Object call() {
                        try {
                            PolicyAdmin pa = realAdminSurface.getSubProcessPolicyAdmin();
                            pa.grant(null);
                            return "SHOULD NOT REACH HERE";
                        } catch (Exception e) {
                            return e;
                        }
                    }
                });
                if (outcome == SKIP) return;   // callAs unavailable on this JDK
                fail("expected Subject.callAs itself to throw (its own"
                        + " AuthPermission(\"callAs\") check denied by the"
                        + " test SM) before the forged Subject ever bound;"
                        + " instead the call completed with: " + outcome);
            } catch (java.lang.reflect.InvocationTargetException expected) {
                // Subject.callAs's own permission check fires before the
                // lambda body (and therefore AdminPrincipalAuthenticator)
                // ever runs -- the forged Subject never even binds.
                Throwable cause = expected.getCause();
                assertTrue("expected a SecurityException from the denied"
                        + " AuthPermission, got: " + cause,
                        cause instanceof SecurityException);
            }
            assertEquals("the hosted backing must never have been reached",
                    0, backing.grantCalls);
        });
    }

    /**
     * Same forged-Subject shape, but proves the SECOND, independent line of
     * defence: even in a hypothetical where {@code Subject.callAs} succeeded
     * in binding the forged Subject (e.g. a misconfigured policy that DOES
     * grant {@code AuthPermission("callAs")} to hosted code), the admin gate
     * itself still requires the caller to be the real admin principal -- the
     * fix does not rest solely on the JDK-level permission check.
     * (Uses the injectable {@link Caller} stub to model "forged Subject
     * successfully bound", since binding one for real requires the
     * permission this test is deliberately NOT about.)
     */
    @Test
    public void evenIfRebindingSucceeded_wrongCallerStillRefused_underSM()
            throws Exception {
        withSecurityManager(new AllowAllSecurityManager(), () -> {
            Caller caller = new Caller();
            TrustedPolicyBacking backing = new TrustedPolicyBacking();
            SubProcessPolicyAdmin s = new SubProcessPolicyAdmin(admin, backing, caller);

            // Not the admin -- even though "binding" (setting caller.subject)
            // trivially "succeeded" here, the gate must still refuse.
            caller.subject = subjectWith(name("spiffe://ctrl/attacker"));
            try {
                s.getSubProcessPolicyAdmin();
                fail("wrong principal must be refused even when a SecurityManager"
                        + " is installed and Subject-rebinding itself succeeded");
            } catch (SecurityException expected) { /* good */ }
            assertEquals(0, backing.grantCalls);
        });
    }

    /**
     * Binds {@code subject} as the current Subject via {@code Subject.callAs}
     * (JDK 18+ / DirtyChai) reflectively, so this test stays {@code -release 8}
     * source-compatible and works on a JDK 25 where {@code Subject.doAs} /
     * {@code getSubject} are unsupported with the SecurityManager disabled.
     * Returns null (skip) if {@code callAs} is unavailable.
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

    @Test
    public void realCurrentSubjectPath_enforcesAdmin() throws Exception {
        // Exercise the DEFAULT mechanism (Subject.current()), not just the
        // injected identity, on the real runtime path. A SecurityManager is
        // installed throughout (AllowAll: this test is not about the
        // callAs/doAs permission check itself, which is covered separately
        // by forgedSubjectViaCallAs_blockedBeforeReachingTheGate_whenSMDeniesRebind)
        // so the "admin subject in context -> succeeds" branch actually
        // exercises the identity-matching logic rather than failing closed
        // for the unrelated reason of no SecurityManager being installed.
        withSecurityManager(new AllowAllSecurityManager(), () -> {
            final SubProcessPolicyAdmin s = new SubProcessPolicyAdmin(
                    admin, new TrustedPolicyBacking(),
                    AdminPrincipalAuthenticator.CURRENT_SUBJECT);

            // No current Subject -> fail closed.
            try {
                s.getSubProcessPolicyAdmin();
                fail("no ambient Subject must fail closed");
            } catch (SecurityException expected) { /* good */ }

            // Running as the admin subject -> succeeds.
            Object ok = callAs(subjectWith(admin), new Callable<Object>() {
                public Object call() {
                    try { return s.getSubProcessPolicyAdmin(); }
                    catch (Exception e) { return e; }
                }
            });
            if (ok == SKIP) return;   // callAs unavailable on this JDK
            assertTrue("admin subject in context must obtain the proxy: " + ok,
                    ok instanceof PolicyAdmin);

            // Running as a non-admin subject -> fail closed.
            Object bad = callAs(subjectWith(name("spiffe://ctrl/x")),
                    new Callable<Object>() {
                public Object call() {
                    try { return s.getSubProcessPolicyAdmin(); }
                    catch (Exception e) { return e; }
                }
            });
            assertTrue("non-admin subject in context must fail closed",
                    bad instanceof SecurityException);
        });
    }
}
