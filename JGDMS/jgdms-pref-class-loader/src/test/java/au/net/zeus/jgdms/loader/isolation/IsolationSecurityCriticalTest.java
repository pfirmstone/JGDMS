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
import java.security.Principal;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import org.apache.river.api.security.PermissionGrant;
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

    @Test
    public void unauthenticatedCaller_getAdminFailsClosed() {
        Caller caller = new Caller();       // subject == null
        SubProcessPolicyAdmin s = new SubProcessPolicyAdmin(
                admin, new TrustedPolicyBacking(), caller);
        try {
            s.getSubProcessPolicyAdmin();
            fail("unauthenticated caller must not receive a usable admin proxy");
        } catch (SecurityException expected) { /* good */ }
        catch (RemoteException e) { fail("unexpected: " + e); }
    }

    @Test
    public void wronglyAuthenticatedCaller_getAdminFailsClosed() {
        Caller caller = new Caller();
        caller.subject = subjectWith(name("spiffe://ctrl/not-admin"));
        SubProcessPolicyAdmin s = new SubProcessPolicyAdmin(
                admin, new TrustedPolicyBacking(), caller);
        try {
            s.getSubProcessPolicyAdmin();
            fail("wrong principal must not receive a usable admin proxy");
        } catch (SecurityException expected) { /* good */ }
        catch (RemoteException e) { fail("unexpected: " + e); }
    }

    @Test
    public void adminCaller_getsUsableProxy_operationsDispatch() throws Exception {
        Caller caller = new Caller();
        caller.subject = subjectWith(admin);
        TrustedPolicyBacking backing = new TrustedPolicyBacking();
        SubProcessPolicyAdmin s = new SubProcessPolicyAdmin(admin, backing, caller);

        PolicyAdmin pa = s.getSubProcessPolicyAdmin();
        assertNotNull(pa);
        pa.grant(null);
        assertEquals("admin op must dispatch to the trusted backing object",
                1, backing.grantCalls);
    }

    @Test
    public void capturedProxy_reGatesOnEveryOperation_forNonAdmin()
            throws Exception {
        // An admin obtains the proxy, then the caller identity drops to a
        // non-admin (e.g. the reference leaks / is replayed by another party).
        // Every subsequent operation must re-check and fail closed.
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
    }

    @Test
    public void adminProxyCarriesStricterConstraints_integrityAndAdminPrincipal()
            throws Exception {
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
    }

    @Test
    public void constraintsCannotBeWeakenedByClient() throws Exception {
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
        // injected identity, on the real runtime path.
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
    }
}
