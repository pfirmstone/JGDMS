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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.DomainCombiner;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.security.auth.Subject;
import javax.security.auth.SubjectDomainCombiner;
import javax.security.auth.x500.X500Principal;
import net.jini.security.policy.DynamicPolicyProvider;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Runtime validation of n-party (multi-{@code Subject}) authorization on a
 * DirtyChai JDK: binding several user Subjects for the dynamic scope of a call
 * via {@code Subject.callAs(Callable, UserSubject...)} must make the principals
 * of <em>every</em> bound Subject visible to the authorization decision at once.
 * A policy grant that lists several principals is a conjunction, so it is
 * satisfied only when all of those principals are co-present.
 *
 * <p>This is the "intersection, not union" semantics for n-party authority: the
 * authority available to {@code callAs(action, A, B)} is the set of grants whose
 * principal terms are all met by {@code A} <em>and</em> {@code B} together. The
 * mechanism is {@link SubjectDomainCombiner} merging the bound Subjects'
 * principals onto the {@link ProtectionDomain}s, which
 * {@link java.security.AccessController#getContext()} then consults.
 *
 * <p>Two layers are checked:
 * <ul>
 *   <li>{@link #policyGrantsOnlyWhenAllPrincipalsPresent()} — the policy half
 *       (a multi-principal grant is conjunctive). JDK-agnostic; always runs.
 *       This isolates "does the policy match these X500 principals
 *       conjunctively" from the runtime unification below, and mirrors
 *       {@link AgentAuthorityWalkingSkeletonTest} scenario 2.</li>
 *   <li>{@link #multiSubjectCallAsUnifiesPrincipals()} — the runtime half:
 *       inside {@code callAs(A, B)}, the combiner derived from the bound
 *       Subjects (the same {@code SubjectDomainCombiner.currentAll()} that
 *       {@code getContext()} uses) merges <em>both</em> principals onto a
 *       domain. DirtyChai-only; self-skips on a stock JDK.</li>
 * </ul>
 *
 * <p>The DirtyChai-only API ({@code Subject.callAs(Callable, UserSubject...)} and
 * {@code SubjectDomainCombiner.currentAll()}) is reached reflectively so this
 * test compiles on a stock JDK and self-skips there via {@link Assume}.
 */
public class MultiSubjectGrantAuthorizationTest {

    private static final X500Principal ALICE = new X500Principal("CN=alice");
    private static final X500Principal BOB   = new X500Principal("CN=bob");
    private static final X500Principal CAROL = new X500Principal("CN=carol");

    /** A capability the policy grants only to ALICE and BOB acting together. */
    private static final Permission BOTH_REQUIRED =
            new RuntimePermission("multiSubject.bothPartiesRequired");

    /** Capabilities the policy grants to exactly one principal each (doAs replace tests). */
    private static final Permission ALICE_ONLY = new RuntimePermission("multiSubject.aliceOnly");
    private static final Permission BOB_ONLY   = new RuntimePermission("multiSubject.bobOnly");

    // ---- the policy half (JDK-agnostic, always runs) ------------------------

    /**
     * A two-principal grant is conjunctive: a domain carrying both principals is
     * granted, a domain carrying only one is denied, and a superset is still
     * granted. This isolates the policy's principal matching from the runtime
     * {@code callAs} unification exercised below.
     */
    @Test
    public void policyGrantsOnlyWhenAllPrincipalsPresent() {
        DynamicPolicyProvider policy = newPolicy();
        policy.grant(principalGrant(new Principal[]{ALICE, BOB}, BOTH_REQUIRED));

        assertTrue("both principals present => granted",
                policy.implies(domain(ALICE, BOB), BOTH_REQUIRED));
        assertFalse("only ALICE present => denied",
                policy.implies(domain(ALICE), BOTH_REQUIRED));
        assertFalse("only BOB present => denied",
                policy.implies(domain(BOB), BOTH_REQUIRED));
        assertTrue("superset {ALICE,BOB,CAROL} still satisfies ALICE and BOB",
                policy.implies(domain(ALICE, BOB, CAROL), BOTH_REQUIRED));
    }

    // ---- the runtime half: callAs unifies all bound Subjects' principals ----

    /**
     * The clincher, exercised through the real fixed mechanism
     * ({@code SubjectDomainCombiner.currentAll().combine(...)} — the same call
     * {@code AccessController.getContext()} makes): inside a
     * {@code callAs(action, A, B)} the combiner derived from the currently bound
     * Subjects merges <em>both</em> principals onto a domain, so the conjunctive
     * grant is satisfied; with only one Subject bound it is not; with a superset
     * it still is; and the merge is order-independent.
     *
     * <p>{@code combine(current, null)} is used deliberately (no
     * {@code assignedDomains}) so the assertion turns on a single, combiner-
     * enriched domain.
     */
    @Test
    public void multiSubjectCallAsUnifiesPrincipals() throws Exception {
        Method callAs = multiSubjectCallAs();
        Method currentAll = currentAllCombiner();
        Assume.assumeTrue("requires the DirtyChai multi-Subject API"
                + " (Subject.callAs(Callable, UserSubject...) +"
                + " SubjectDomainCombiner.currentAll())",
                callAs != null && currentAll != null);

        final DynamicPolicyProvider policy = newPolicy();
        policy.grant(principalGrant(new Principal[]{ALICE, BOB}, BOTH_REQUIRED));

        Object a = newReadOnlyUserSubject(ALICE);
        Object b = newReadOnlyUserSubject(BOB);
        Object c = newReadOnlyUserSubject(CAROL);

        // From the combiner over the currently bound Subjects, does the merged
        // domain satisfy the (ALICE and BOB) grant under this policy?
        Callable<Boolean> grantedByMergedDomain = () -> {
            for (ProtectionDomain pd : mergedDomains(currentAll)) {
                if (policy.implies(pd, BOTH_REQUIRED)) return Boolean.TRUE;
            }
            return Boolean.FALSE;
        };

        assertTrue("callAs(A,B): both principals merged => granted",
                (Boolean) runCallAs(callAs, grantedByMergedDomain, a, b));
        assertFalse("callAs(A): only one principal => denied",
                (Boolean) runCallAs(callAs, grantedByMergedDomain, a));
        assertFalse("callAs(B): only one principal => denied",
                (Boolean) runCallAs(callAs, grantedByMergedDomain, b));
        assertTrue("callAs(A,B,C): superset still satisfies ALICE and BOB",
                (Boolean) runCallAs(callAs, grantedByMergedDomain, a, b, c));
        assertTrue("callAs(B,A): the merge is order-independent",
                (Boolean) runCallAs(callAs, grantedByMergedDomain, b, a));

        // And the merge is explicit: both principals land on the domain.
        Callable<Set<Principal>> mergedPrincipals = () -> {
            Set<Principal> all = new LinkedHashSet<>();
            for (ProtectionDomain pd : mergedDomains(currentAll)) {
                all.addAll(Arrays.asList(pd.getPrincipals()));
            }
            return all;
        };
        @SuppressWarnings("unchecked")
        Set<Principal> seen = (Set<Principal>) runCallAs(callAs, mergedPrincipals, a, b);
        assertTrue("ALICE merged onto the domain", seen.contains(ALICE));
        assertTrue("BOB merged onto the domain", seen.contains(BOB));
    }

    /**
     * The same n-party unification, but driven through the <em>production</em>
     * entry point ({@link UserSubjectSupport#callAsAll}) instead of a reflective
     * handle derived locally by this test.
     *
     * <p>This is precisely the gap the original defect fell through: the test's
     * own (correct) {@code callAs(Callable, UserSubject...)} lookup kept passing,
     * while production's (wrong) {@code callAs(Callable, Subject[])} lookup never
     * resolved, so every dispatch silently bound only the first Subject.  An
     * assertion about the JDK's method is not an assertion about production's
     * use of it.
     */
    @Test
    public void productionCallAsAllUnifiesPrincipals() throws Exception {
        Method currentAll = currentAllCombiner();
        Assume.assumeTrue("requires the DirtyChai multi-Subject API",
                UserSubjectSupport.isMultiSubjectSupported() && currentAll != null);

        final DynamicPolicyProvider policy = newPolicy();
        policy.grant(principalGrant(new Principal[]{ALICE, BOB}, BOTH_REQUIRED));

        final Callable<Boolean> grantedByMergedDomain = () -> {
            for (ProtectionDomain pd : mergedDomains(currentAll)) {
                if (policy.implies(pd, BOTH_REQUIRED)) return Boolean.TRUE;
            }
            return Boolean.FALSE;
        };

        Subject alice = productionUserSubject(ALICE);
        Subject bob   = productionUserSubject(BOB);
        Subject carol = productionUserSubject(CAROL);

        assertTrue("production callAsAll(A,B): both principals merged => granted",
                UserSubjectSupport.callAsAll(new Subject[]{alice, bob}, grantedByMergedDomain));
        assertFalse("production callAsAll(A): only one principal => denied",
                UserSubjectSupport.callAsAll(new Subject[]{alice}, grantedByMergedDomain));
        assertTrue("production callAsAll(A,B,C): superset still satisfies ALICE and BOB",
                UserSubjectSupport.callAsAll(new Subject[]{alice, bob, carol},
                        grantedByMergedDomain));
        assertTrue("production callAsAll(B,A): the merge is order-independent",
                UserSubjectSupport.callAsAll(new Subject[]{bob, alice}, grantedByMergedDomain));
    }

    /** A read-only user Subject built the way production builds them. */
    private static Subject productionUserSubject(Principal... principals) {
        Set<Principal> ps = new LinkedHashSet<>(Arrays.asList(principals));
        return UserSubjectSupport.newUserSubject(
                true, ps, Collections.emptySet(), Collections.emptySet());
    }

    // ---- three-axis conjunction: verified code AND process AND user ---------

    /**
     * The full model: a grant is satisfied only by the conjunction of all three
     * axes — verified code (codesource/URI) AND the (stamped) process principal
     * AND the (callAs-folded) user principal. Dropping any single axis denies.
     * The user axis is exercised through the real runtime fold
     * ({@code currentAll().combine(...)}); the process principal stands in for
     * the SPIFFE workload SVID stamped onto the domain; the codesource stands in
     * for cryptographically verified code.
     */
    @Test
    public void grantRequiresVerifiedCodeProcessAndUser() throws Exception {
        Method callAs = multiSubjectCallAs();
        Method currentAll = currentAllCombiner();
        Assume.assumeTrue("requires the DirtyChai multi-Subject API",
                callAs != null && currentAll != null);

        final X500Principal PROCESS = new X500Principal("CN=spiffe-workload"); // stamped SVID
        final CodeSource VERIFIED   = new CodeSource(new URL("file:/verified/app/app.jar"), (Certificate[]) null);
        final CodeSource UNVERIFIED = new CodeSource(new URL("file:/attacker/evil.jar"),    (Certificate[]) null);
        final Permission SENSITIVE  = new RuntimePermission("threeAxis.sensitiveOp");

        final DynamicPolicyProvider policy = newPolicy();
        policy.grant(PermissionGrantBuilder.newBuilder()
                .uri("file:/verified/app/app.jar")                 // WHAT: verified code
                .principals(new Principal[]{PROCESS, ALICE})       // WHERE: process  +  WHO: user
                .permissions(new Permission[]{SENSITIVE})
                .context(PermissionGrantBuilder.URI)
                .build());

        Object alice = newReadOnlyUserSubject(ALICE);

        assertTrue("verified code AND process AND user => granted",
                (Boolean) runCallAs(callAs, impliesAfterFold(policy, currentAll,
                        new ProtectionDomain(VERIFIED, null, null, new Principal[]{PROCESS}), SENSITIVE),
                        alice));

        assertFalse("user axis absent (no Subject bound) => denied",
                policy.implies(new ProtectionDomain(VERIFIED, null, null, new Principal[]{PROCESS}), SENSITIVE));

        assertFalse("process axis absent (no stamped principal) => denied",
                (Boolean) runCallAs(callAs, impliesAfterFold(policy, currentAll,
                        new ProtectionDomain(VERIFIED, null, null, new Principal[0]), SENSITIVE),
                        alice));

        assertFalse("code axis wrong (unverified codesource) => denied",
                (Boolean) runCallAs(callAs, impliesAfterFold(policy, currentAll,
                        new ProtectionDomain(UNVERIFIED, null, null, new Principal[]{PROCESS}), SENSITIVE),
                        alice));
    }

    // ---- the unification: the stock primitive folds the bound subjects ------

    /**
     * After routing {@code AccessController.checkPermission} through
     * {@code getContext()}, the stock enforcement primitive folds the bound
     * subjects just as CombinerSecurityManager does — so a principal-gated
     * permission is granted inside {@code callAs(A,B)} and denied with only one
     * bound. (Before the unification this primitive denied even with both bound.)
     * The check runs inside {@code doPrivileged} so it turns on the test's own
     * combiner-enriched domain rather than the whole surefire stack.
     */
    @Test
    public void stockCheckPermissionPrimitiveFoldsBoundSubjects() throws Exception {
        Method callAs = multiSubjectCallAs();
        Assume.assumeTrue("requires the DirtyChai multi-Subject callAs", callAs != null);

        DynamicPolicyProvider policy = newPolicy();
        policy.grant(principalGrant(new Principal[]{ALICE, BOB}, BOTH_REQUIRED));

        Object a = newReadOnlyUserSubject(ALICE);
        Object b = newReadOnlyUserSubject(BOB);

        Callable<Boolean> check = () -> AccessController.doPrivileged((PrivilegedAction<Boolean>) () -> {
            try {
                AccessController.checkPermission(BOTH_REQUIRED);
                return Boolean.TRUE;
            } catch (AccessControlException denied) {
                return Boolean.FALSE;
            }
        });

        Policy previous = Policy.getPolicy();
        Policy.setPolicy(policy);
        try {
            assertTrue("callAs(A,B): the primitive folds both subjects => granted",
                    (Boolean) runCallAs(callAs, check, a, b));
            assertFalse("callAs(A): conjunction unmet => denied",
                    (Boolean) runCallAs(callAs, check, a));
        } finally {
            Policy.setPolicy(previous);
        }
    }

    // ---- the doAs boundary: replace (not accumulate) + the WorkerSubject seal --

    /**
     * The structural seal on the ambient WorkerSubject. Neither {@code doAs} nor
     * {@code doAsPrivileged} will carry a {@code WorkerSubject} (it is
     * established only by SPIRE infrastructure, so it can be neither injected nor
     * stripped through the execute-as APIs) or a {@code UserSubject} (a user must
     * be bound through {@code callAs}). A plain legacy {@code Subject} is still
     * accepted. This is the doAs-side complement of "callAs cannot strip the
     * ambient WorkerSubject": the worker channel is sealed on every side.
     */
    @Test
    public void doAsAndDoAsPrivilegedRejectWorkerAndUserSubjects() throws Exception {
        Object worker = newWorkerSubject();
        Object user   = (multiSubjectCallAs() == null) ? null : newReadOnlyUserSubject(ALICE);
        Assume.assumeTrue("requires the DirtyChai Worker/UserSubject types",
                worker != null && user != null);

        assertDoAsRejects((Subject) worker, "SPIRE infrastructure");
        assertDoAsRejects((Subject) user,   "callAs");
        assertDoAsPrivilegedRejects((Subject) worker, "SPIRE infrastructure");
        assertDoAsPrivilegedRejects((Subject) user,   "callAs");

        // a plain legacy Subject is accepted by both
        Subject plain = newPlainSubject(BOB);
        assertEquals("doAs accepts a plain Subject", "ran",
                Subject.doAs(plain, (PrivilegedAction<String>) () -> "ran"));
        assertEquals("doAsPrivileged accepts a plain Subject", "ran",
                Subject.doAsPrivileged(plain, (PrivilegedAction<String>) () -> "ran", null));
    }

    /**
     * {@code doAs} REPLACES the enclosing user rather than accumulating it: inside
     * {@code callAs(ALICE)}, a nested {@code doAs(plain BOB)} makes the n-party
     * combiner ({@code SubjectDomainCombiner.currentAll()}) see only BOB, so a
     * grant requiring BOB fires on the merged domain while ALICE's does not. The
     * enclosing ALICE is shadowed, not unioned — joint n-party authority must be
     * expressed with the explicit {@code callAs(action, A, B)}, never by stacking
     * doAs. (Validated through the same combiner the enforcement path uses, on a
     * synthetic base domain, to avoid the surefire stack.)
     */
    @Test
    public void doAsBindsOnlyTheNewSubjectNotEnclosingUser() throws Exception {
        Method callAs = multiSubjectCallAs();
        Method currentAll = currentAllCombiner();
        Assume.assumeTrue("requires the DirtyChai multi-Subject API",
                callAs != null && currentAll != null);

        final DynamicPolicyProvider policy = newPolicy();
        policy.grant(principalGrant(new Principal[]{ALICE}, ALICE_ONLY));
        policy.grant(principalGrant(new Principal[]{BOB},   BOB_ONLY));

        final Subject plainBob = newPlainSubject(BOB);

        // Inside callAs(ALICE) -> doAs(BOB): report the combiner's merged
        // principals and which single-principal grant fires on the merged domain.
        Callable<Object[]> probe = () -> Subject.doAs(plainBob, (PrivilegedAction<Object[]>) () -> {
            try {
                Set<Principal> merged = new LinkedHashSet<>();
                boolean bob = false, alice = false;
                for (ProtectionDomain pd : mergedDomains(currentAll)) {
                    merged.addAll(Arrays.asList(pd.getPrincipals()));
                    if (policy.implies(pd, BOB_ONLY))   bob = true;
                    if (policy.implies(pd, ALICE_ONLY)) alice = true;
                }
                return new Object[]{merged, bob, alice};
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Object[] r = (Object[]) runCallAs(callAs, probe, newReadOnlyUserSubject(ALICE));
        @SuppressWarnings("unchecked")
        Set<Principal> merged = (Set<Principal>) r[0];

        assertTrue("doAs(BOB) binds BOB into the combiner view", merged.contains(BOB));
        assertFalse("doAs REPLACES: enclosing ALICE is not accumulated", merged.contains(ALICE));
        assertTrue("BOB's grant fires under doAs(BOB)", (Boolean) r[1]);
        assertFalse("ALICE's grant does NOT fire under doAs(BOB)", (Boolean) r[2]);
    }

    private static void assertDoAsRejects(Subject subject, String expectFragment) {
        try {
            Subject.doAs(subject, (PrivilegedAction<Void>) () -> null);
            fail("doAs must reject this Subject (expected IllegalArgumentException ~ \""
                    + expectFragment + "\")");
        } catch (IllegalArgumentException expected) {
            assertTrue("IAE should explain why (\"" + expectFragment + "\"): " + expected.getMessage(),
                    expected.getMessage() != null && expected.getMessage().contains(expectFragment));
        }
    }

    private static void assertDoAsPrivilegedRejects(Subject subject, String expectFragment) {
        try {
            Subject.doAsPrivileged(subject, (PrivilegedAction<Void>) () -> null, null);
            fail("doAsPrivileged must reject this Subject (expected IllegalArgumentException ~ \""
                    + expectFragment + "\")");
        } catch (IllegalArgumentException expected) {
            assertTrue("IAE should explain why (\"" + expectFragment + "\"): " + expected.getMessage(),
                    expected.getMessage() != null && expected.getMessage().contains(expectFragment));
        }
    }

    /** Combines {@code base} with the currently-bound subjects and asks the policy. */
    private static Callable<Boolean> impliesAfterFold(DynamicPolicyProvider policy,
            Method currentAll, ProtectionDomain base, Permission perm) {
        return () -> {
            DomainCombiner combiner = (DomainCombiner) currentAll.invoke(null);
            ProtectionDomain[] merged = (combiner == null)
                    ? new ProtectionDomain[]{base}
                    : combiner.combine(new ProtectionDomain[]{base}, null);
            for (ProtectionDomain pd : merged) {
                if (policy.implies(pd, perm)) return Boolean.TRUE;
            }
            return Boolean.FALSE;
        };
    }

    // ---- harness ------------------------------------------------------------

    private static DynamicPolicyProvider newPolicy() {
        return new DynamicPolicyProvider(new DenyAllBasePolicy());
    }

    private static PermissionGrant principalGrant(Principal[] pals, Permission... perms) {
        return PermissionGrantBuilder.newBuilder()
                .principals(pals)
                .permissions(perms)
                .context(PermissionGrantBuilder.PRINCIPAL)
                .build();
    }

    /** A ProtectionDomain carrying the given principals (as callAs/SVID-stamping would). */
    private static ProtectionDomain domain(Principal... pals) {
        return new ProtectionDomain(
                new CodeSource(null, (Certificate[]) null), null, null, pals);
    }

    /**
     * Drives {@code SubjectDomainCombiner.currentAll().combine(base, null)} over
     * a single principal-free base domain and returns the combiner's output —
     * the principal-enriched domain(s) for the currently bound Subjects. Must be
     * called from within a {@code callAs} so that {@code currentAll()} sees the
     * bound Subjects.
     */
    private static ProtectionDomain[] mergedDomains(Method currentAll) throws Exception {
        DomainCombiner combiner = (DomainCombiner) currentAll.invoke(null);
        if (combiner == null) return new ProtectionDomain[0];
        ProtectionDomain base = new ProtectionDomain(
                new CodeSource(null, (Certificate[]) null), null, null, new Principal[0]);
        ProtectionDomain[] merged = combiner.combine(new ProtectionDomain[]{base}, null);
        return merged == null ? new ProtectionDomain[0] : merged;
    }

    /** Reflective handle to {@code Subject.callAs(Callable, UserSubject...)}, or null on a stock JDK. */
    private static Method multiSubjectCallAs() {
        try {
            Class<?> userSubject = Class.forName("javax.security.auth.UserSubject");
            Class<?> userSubjectArray = Array.newInstance(userSubject, 0).getClass();
            return Subject.class.getMethod("callAs", Callable.class, userSubjectArray);
        } catch (ClassNotFoundException | NoSuchMethodException notDirtyChai) {
            return null;
        }
    }

    /** Reflective handle to {@code SubjectDomainCombiner.currentAll()}, or null on a stock JDK. */
    private static Method currentAllCombiner() {
        try {
            return SubjectDomainCombiner.class.getMethod("currentAll");
        } catch (NoSuchMethodException notDirtyChai) {
            return null;
        }
    }

    /** Constructs a read-only {@code UserSubject} carrying the given principals, reflectively. */
    private static Object newReadOnlyUserSubject(Principal... principals) throws Exception {
        Class<?> userSubject = Class.forName("javax.security.auth.UserSubject");
        Constructor<?> ctor = userSubject.getConstructor(
                boolean.class, Set.class, Set.class, Set.class);
        Set<Principal> ps = new LinkedHashSet<>(Arrays.asList(principals));
        return ctor.newInstance(true, ps, Collections.emptySet(), Collections.emptySet());
    }

    /** A plain (legacy JAAS) {@code Subject} — neither User nor Worker — accepted by doAs. */
    private static Subject newPlainSubject(Principal... principals) {
        Set<Principal> ps = new LinkedHashSet<>(Arrays.asList(principals));
        return new Subject(true, ps, Collections.emptySet(), Collections.emptySet());
    }

    /** A read-only {@code WorkerSubject} reflectively, or null on a stock JDK (or if it becomes abstract). */
    private static Object newWorkerSubject() {
        try {
            Class<?> ws = Class.forName("javax.security.auth.WorkerSubject");
            return ws.getConstructor(boolean.class, Set.class, Set.class, Set.class)
                     .newInstance(true, Collections.emptySet(),
                             Collections.emptySet(), Collections.emptySet());
        } catch (ReflectiveOperationException notConstructible) {
            return null;
        }
    }

    /** Invokes the varargs {@code callAs(Callable, UserSubject...)} with the given Subjects. */
    private static Object runCallAs(Method callAs, Callable<?> action, Object... userSubjects)
            throws Exception {
        Class<?> userSubject = Class.forName("javax.security.auth.UserSubject");
        Object arr = Array.newInstance(userSubject, userSubjects.length);
        for (int i = 0; i < userSubjects.length; i++) {
            Array.set(arr, i, userSubjects[i]);
        }
        try {
            return callAs.invoke(null, action, arr);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw e;
        }
    }

    /** Base policy that grants nothing, so all authority comes from the dynamic grants. */
    private static final class DenyAllBasePolicy extends Policy {
        @Override public PermissionCollection getPermissions(CodeSource codesource) {
            return new Permissions();
        }
        @Override public PermissionCollection getPermissions(ProtectionDomain domain) {
            return new Permissions();
        }
        @Override public boolean implies(ProtectionDomain domain, Permission permission) {
            return false;
        }
        @Override public void refresh() {
        }
    }
}
