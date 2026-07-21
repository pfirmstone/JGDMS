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

import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.security.auth.Subject;
import net.jini.security.AccessPermission;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit coverage for the 2026-07-21 ratified redesign of
 * {@link AdminPrincipalAuthenticator}: authorization is a per-operation
 * {@link SubProcessAdminPermission} check (target = operation name) against
 * a synthetic principal-only context, submitted to the installed
 * {@link SecurityManager} &mdash; no longer a principal-name loop against a
 * single configured admin principal.  Covers, per the ratified design:
 * <ol type="a">
 *   <li>grant present &rarr; each of the four operations authorized;</li>
 *   <li>no grant &rarr; refused;</li>
 *   <li>per-operation separation (a grant for {@code getGrants} does not
 *       authorize {@code grant} &mdash; least privilege: {@code grant} is
 *       the dangerous one);</li>
 *   <li>principals delivered via the WorkerSubject/processWorker-style path
 *       (stubbed via {@link AdminPrincipalAuthenticator.CallerIdentity},
 *       matching this suite's established idiom &mdash; see
 *       {@code IsolationSecurityCriticalTest}'s mirror test of the
 *       reflective tier ordering);</li>
 *   <li>the class-gate: an equivalent plain {@link AccessPermission} grant
 *       must NOT satisfy a {@link SubProcessAdminPermission} check.</li>
 * </ol>
 * Scaffolding notes: see {@link AdminAuthTestSupport} &mdash; tests
 * {@code Assume}-skip on JDKs where {@code setSecurityManager} is
 * unsupported (they execute for real on the DirtyChai build JDK this
 * module's tests run under).
 */
public class AdminPermissionAuthorizationTest {

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

    private static Subject subjectWith(Principal... ps) {
        Set<Principal> set = new LinkedHashSet<Principal>(Arrays.asList(ps));
        return new Subject(true, set, Collections.<Object>emptySet(),
                Collections.<Object>emptySet());
    }

    /** Injectable caller identity holder, same shape as the T2 test suite's. */
    static final class Caller implements AdminPrincipalAuthenticator.CallerIdentity {
        volatile Subject subject;
        public Subject current() { return subject; }
    }

    private static final String[] ALL_OPERATIONS = {
        "getSubProcessPolicyAdmin", "grant", "refresh", "getGrants"
    };

    private final Principal admin = name("spiffe://ctrl/admin");

    // ==================================================================
    // (a) Grant present -> each of the four operations authorized.
    // ==================================================================

    @Test
    public void grantPresent_eachOfTheFourOperationsAuthorized() throws Exception {
        AdminAuthTestSupport.withAdminAuthorization(admin, () -> {
            Caller caller = new Caller();
            caller.subject = subjectWith(admin);
            AdminPrincipalAuthenticator auth =
                    new AdminPrincipalAuthenticator(caller);
            for (String op : ALL_OPERATIONS) {
                assertTrue("granted principal must be authorized for " + op,
                        auth.isAdmin(op));
                auth.requireAdmin(op);  // must not throw
            }
        });
    }

    // ==================================================================
    // (b) No grant -> refused.
    // ==================================================================

    @Test
    public void noGrant_refusedForEveryOperation() throws Exception {
        // Policy grants the four operations to the admin principal ONLY; the
        // caller authenticates as a different principal -> no grant applies.
        AdminAuthTestSupport.withAdminAuthorization(admin, () -> {
            Caller caller = new Caller();
            caller.subject = subjectWith(name("spiffe://ctrl/not-admin"));
            AdminPrincipalAuthenticator auth =
                    new AdminPrincipalAuthenticator(caller);
            for (String op : ALL_OPERATIONS) {
                assertFalse("ungranted principal must be refused for " + op,
                        auth.isAdmin(op));
                try {
                    auth.requireAdmin(op);
                    fail("requireAdmin must throw for ungranted principal: " + op);
                } catch (SecurityException expected) {
                    assertTrue("message must name the permission and target",
                            expected.getMessage().contains(
                                    "SubProcessAdminPermission(\"" + op + "\")"));
                }
            }
        });
    }

    @Test
    public void noGrant_unauthenticatedCaller_refused() throws Exception {
        AdminAuthTestSupport.withAdminAuthorization(admin, () -> {
            Caller caller = new Caller();       // subject == null
            AdminPrincipalAuthenticator auth =
                    new AdminPrincipalAuthenticator(caller);
            for (String op : ALL_OPERATIONS) {
                assertFalse("unauthenticated caller must be refused for " + op,
                        auth.isAdmin(op));
            }
        });
    }

    @Test
    public void emptyPolicy_refusesEvenTheAdminPrincipal() throws Exception {
        // A policy with NO SubProcessAdminPermission grants at all refuses
        // even a caller genuinely authenticated as the admin principal:
        // authorization now lives in the policy, not in this class.
        AdminAuthTestSupport.withAuthorization(
                new AdminAuthTestSupport.PrincipalGrantPolicy(admin
                        /* no permissions granted */), () -> {
            Caller caller = new Caller();
            caller.subject = subjectWith(admin);
            AdminPrincipalAuthenticator auth =
                    new AdminPrincipalAuthenticator(caller);
            for (String op : ALL_OPERATIONS) {
                assertFalse("no grant in policy -> refused, even for the"
                        + " admin principal: " + op, auth.isAdmin(op));
            }
        });
    }

    // ==================================================================
    // (c) Per-operation separation: least privilege.
    // ==================================================================

    @Test
    public void grantForGetGrants_doesNotAuthorizeGrant() throws Exception {
        AdminAuthTestSupport.withAuthorization(
                new AdminAuthTestSupport.PrincipalGrantPolicy(admin,
                        new SubProcessAdminPermission("getGrants")), () -> {
            Caller caller = new Caller();
            caller.subject = subjectWith(admin);
            AdminPrincipalAuthenticator auth =
                    new AdminPrincipalAuthenticator(caller);

            assertTrue("the granted read-only operation must be authorized",
                    auth.isAdmin("getGrants"));
            assertFalse("a getGrants-only grant must NOT authorize the"
                    + " dangerous grant operation", auth.isAdmin("grant"));
            assertFalse(auth.isAdmin("refresh"));
            assertFalse(auth.isAdmin("getSubProcessPolicyAdmin"));
            try {
                auth.requireAdmin("grant");
                fail("requireAdmin(\"grant\") must throw under a"
                        + " getGrants-only grant");
            } catch (SecurityException expected) { /* good */ }
        });
    }

    @Test
    public void perOperationSeparation_holdsThroughTheRealAdminSurface()
            throws Exception {
        // Same least-privilege separation, end-to-end through the real
        // SubProcessPolicyAdmin front + re-gating proxy: the accessor and
        // read-only operations are granted, `grant` is not.
        AdminAuthTestSupport.withAuthorization(
                new AdminAuthTestSupport.PrincipalGrantPolicy(admin,
                        new SubProcessAdminPermission("getSubProcessPolicyAdmin"),
                        new SubProcessAdminPermission("refresh"),
                        new SubProcessAdminPermission("getGrants")), () -> {
            Caller caller = new Caller();
            caller.subject = subjectWith(admin);
            final int[] grantCalls = { 0 };
            PolicyAdmin backing = new PolicyAdmin() {
                public void grant(org.apache.river.api.security.PermissionGrant g) {
                    grantCalls[0]++;
                }
                public void refresh() { }
                public org.apache.river.api.security.PermissionGrant[] getGrants() {
                    return new org.apache.river.api.security.PermissionGrant[0];
                }
            };
            SubProcessPolicyAdmin front =
                    new SubProcessPolicyAdmin(admin, backing, caller);

            PolicyAdmin pa = front.getSubProcessPolicyAdmin(); // granted
            pa.refresh();                                       // granted
            assertEquals(0, pa.getGrants().length);             // granted
            try {
                pa.grant(null);
                fail("`grant` must be refused: it was deliberately NOT"
                        + " granted (least privilege per operation)");
            } catch (SecurityException expected) { /* good */ }
            assertEquals("the refused grant() must never reach the backing",
                    0, grantCalls[0]);
        });
    }

    // ==================================================================
    // (d) Principals delivered via the WorkerSubject/processWorker path.
    // ==================================================================

    /**
     * A TLS/SPIFFE-authenticated remote caller's {@code WorkerSubject} is
     * delivered by {@link AdminPrincipalAuthenticator#CURRENT_SUBJECT}'s
     * tier-1 {@code Subject.processWorker()} lookup &mdash; never bound via
     * {@code doAs}/{@code callAs}, so never on the ambient thread stack.
     * The reflective tier ordering itself is covered by
     * {@code IsolationSecurityCriticalTest}'s mirror test; here a
     * {@link AdminPrincipalAuthenticator.CallerIdentity} stub delivers the
     * worker-shaped Subject exactly as that tier would (the suite's
     * established idiom), proving the permission check authorizes on the
     * delivered principals even though nothing about the calling stack or
     * ambient subject carries them.
     */
    @Test
    public void workerSubjectDeliveredPrincipals_authorizeViaPolicyGrant()
            throws Exception {
        final Principal workerSpiffeId = name("spiffe://ctrl/admin");
        AdminAuthTestSupport.withAdminAuthorization(workerSpiffeId, () -> {
            // Read-only stamped worker subject, as the SSLEngine/TLS+SPIFFE
            // keystone mints it: principals only, no credentials here.
            final Subject workerSubject = subjectWith(workerSpiffeId);
            AdminPrincipalAuthenticator auth = new AdminPrincipalAuthenticator(
                    new AdminPrincipalAuthenticator.CallerIdentity() {
                        public Subject current() { return workerSubject; }
                    });
            for (String op : ALL_OPERATIONS) {
                assertTrue("worker-delivered principals must satisfy the"
                        + " policy grant for " + op, auth.isAdmin(op));
            }
        });
    }

    // ==================================================================
    // (e) The class-gate: a plain AccessPermission grant is NOT enough.
    // ==================================================================

    @Test
    public void plainAccessPermissionGrant_doesNotSatisfySubProcessAdminPermissionCheck()
            throws Exception {
        // The policy grants the admin principal plain AccessPermissions with
        // exactly the right target names -- but AccessPermission matching is
        // class-gated, so they must never satisfy a SubProcessAdminPermission
        // check.
        AdminAuthTestSupport.withAuthorization(
                new AdminAuthTestSupport.PrincipalGrantPolicy(admin,
                        new AccessPermission("getSubProcessPolicyAdmin"),
                        new AccessPermission("grant"),
                        new AccessPermission("refresh"),
                        new AccessPermission("getGrants")), () -> {
            Caller caller = new Caller();
            caller.subject = subjectWith(admin);
            AdminPrincipalAuthenticator auth =
                    new AdminPrincipalAuthenticator(caller);
            for (String op : ALL_OPERATIONS) {
                assertFalse("an equivalent plain AccessPermission grant must"
                        + " NOT satisfy the SubProcessAdminPermission check"
                        + " for " + op, auth.isAdmin(op));
            }
        });
    }

    @Test
    public void classGate_impliesSemantics_directly() {
        // The underlying mechanism, pinned directly: same target name, but
        // implies() requires the exact permission class in both directions.
        assertTrue(new SubProcessAdminPermission("grant")
                .implies(new SubProcessAdminPermission("grant")));
        assertFalse("plain AccessPermission must not imply the subclass",
                new AccessPermission("grant")
                        .implies(new SubProcessAdminPermission("grant")));
        assertFalse("the subclass must not imply plain AccessPermission either",
                new SubProcessAdminPermission("grant")
                        .implies(new AccessPermission("grant")));
    }

    // ==================================================================
    // Fail-closed floor: no SecurityManager -> refused (retained semantics).
    // ==================================================================

    @Test
    public void noSecurityManager_isAdminFalse_requireAdminThrows() {
        // Only meaningful when this (possibly shared) test JVM has no
        // SecurityManager installed by an earlier test; mirrors the
        // pre-existing Assume convention in IsolationSecurityCriticalTest.
        org.junit.Assume.assumeTrue(
                "skipped: a SecurityManager is already installed",
                System.getSecurityManager() == null);
        Caller caller = new Caller();
        caller.subject = subjectWith(admin);
        AdminPrincipalAuthenticator auth = new AdminPrincipalAuthenticator(caller);
        assertFalse(auth.isAdmin("grant"));
        try {
            auth.requireAdmin("grant");
            fail("must refuse when no SecurityManager is installed");
        } catch (SecurityException expected) {
            assertTrue(expected.getMessage().contains("SecurityManager"));
        }
    }
}
