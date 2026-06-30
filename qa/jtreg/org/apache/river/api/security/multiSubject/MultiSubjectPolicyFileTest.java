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
/* @test
 * @summary n-party (multi-Subject) intersection and the doAs replace boundary,
 *          validated against the REAL installed file Policy under an installed
 *          SecurityManager on a DirtyChai JDK. Inside callAs(A,B) the combiner
 *          folds both principals onto one domain so a conjunctive
 *          (principal A AND principal B) grant is satisfied; with one Subject,
 *          or inside a nested doAs(other), it is not. Requires the DirtyChai
 *          multi-Subject API, so it self-checks and is a no-op on a stock JDK.
 * @run main/othervm/policy=test.policy -Djava.security.manager=default MultiSubjectPolicyFileTest
 */
import java.net.URL;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Policy;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.security.auth.Subject;
import javax.security.auth.SubjectDomainCombiner;
import javax.security.auth.UserSubject;
import javax.security.auth.x500.X500Principal;

public class MultiSubjectPolicyFileTest {

    static final X500Principal ALICE = new X500Principal("CN=alice");
    static final X500Principal BOB   = new X500Principal("CN=bob");

    static final Permission BOTH       = new RuntimePermission("multiSubject.bothPartiesRequired");
    static final Permission ALICE_ONLY = new RuntimePermission("multiSubject.aliceOnly");
    static final Permission BOB_ONLY   = new RuntimePermission("multiSubject.bobOnly");

    public static void main(String[] args) throws Exception {
        if (System.getSecurityManager() == null) {
            throw new AssertionError("expected an installed SecurityManager");
        }
        final Policy policy = Policy.getPolicy();

        // baseline: the conjunctive grant is in the installed file policy and is conjunctive
        check("baseline {A,B} => BOTH", policy.implies(pd(ALICE, BOB), BOTH), true);
        check("baseline {A}   => BOTH", policy.implies(pd(ALICE), BOTH), false);
        check("baseline {B}   => BOTH", policy.implies(pd(BOB), BOTH), false);

        // n-party: inside callAs(A,B) the combiner folds BOTH onto one domain
        Subject.callAs((Callable<Void>) () -> {
            check("callAs(A,B): folds both => BOTH granted", impliesFolded(policy, BOTH), true);
            return null;
        }, user(ALICE), user(BOB));

        Subject.callAs((Callable<Void>) () -> {
            check("callAs(A): conjunction unmet => BOTH denied", impliesFolded(policy, BOTH), false);
            check("callAs(A): ALICE_ONLY granted",               impliesFolded(policy, ALICE_ONLY), true);
            return null;
        }, user(ALICE));

        // doAs REPLACE: inside callAs(A), a nested doAs(plain B) makes the
        // combiner see only B — A is shadowed, not accumulated.
        Subject.callAs((Callable<Void>) () -> {
            Subject.doAs(plain(BOB), (PrivilegedAction<Void>) () -> {
                check("callAs(A)->doAs(B): BOB_ONLY granted",          impliesFolded(policy, BOB_ONLY), true);
                check("callAs(A)->doAs(B): ALICE_ONLY denied (replace)", impliesFolded(policy, ALICE_ONLY), false);
                return null;
            });
            return null;
        }, user(ALICE));

        System.out.println("PASSED");
    }

    /** Folds the currently-bound subjects onto a synthetic (non-AllPermission) base domain and asks the policy. */
    static boolean impliesFolded(Policy policy, Permission perm) {
        SubjectDomainCombiner sdc = SubjectDomainCombiner.currentAll();
        ProtectionDomain base = pd();
        ProtectionDomain[] merged = (sdc == null)
                ? new ProtectionDomain[]{base}
                : sdc.combine(new ProtectionDomain[]{base}, null);
        for (ProtectionDomain p : merged) {
            if (policy.implies(p, perm)) return true;
        }
        return false;
    }

    static ProtectionDomain pd(Principal... principals) {
        try {
            CodeSource cs = new CodeSource(new URL("file:/synthetic/test"), (Certificate[]) null);
            return new ProtectionDomain(cs, null, null, principals.length == 0 ? null : principals);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static UserSubject user(X500Principal p) { return new UserSubject(true, Set.of(p), Set.of(), Set.of()); }
    static Subject     plain(X500Principal p) { return new Subject(true, Set.of(p), Set.of(), Set.of()); }

    static void check(String what, boolean actual, boolean expected) {
        if (actual != expected) {
            throw new AssertionError("FAIL: " + what + " -- expected " + expected + " got " + actual);
        }
        System.out.println("ok: " + what + " = " + actual);
    }
}
