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
package net.jini.jeri;

import java.security.Principal;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.security.auth.Subject;
import net.jini.security.AdminAuthorizationContext;
import org.junit.Assert;
import org.junit.Test;

/**
 * Seam-side coverage of {@link AdminAuthorizationAssembler} (Option B, recipe
 * steps&nbsp;1&ndash;3), asserting the board MUST-address conditions the gate
 * relies on:
 * <ul>
 *   <li>MUST-2: the assembled carrier authorizes over <em>exactly</em> the
 *       principals the seam supplied (worker &cup; verified user), no more;</li>
 *   <li>MUST-3: a user {@link Subject} carrying no (verified) principals
 *       contributes ZERO principals &mdash; the no-verified-token shape that
 *       {@code BasicInvocationDispatcher.readUserSubjects} produces after
 *       dropping every unverified wire principal;</li>
 *   <li>MUST-4: the worker SPIFFE principal in the carrier is exactly the
 *       connection-derived worker {@link Subject} passed in, never re-read from
 *       process/ambient state.</li>
 * </ul>
 */
public class AdminAuthorizationAssemblerTest {

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
        return new Subject(true, set, Collections.emptySet(), Collections.emptySet());
    }

    /** Union of every carrier domain's principals. */
    private static Set<Principal> domainPrincipals(AdminAuthorizationContext c) {
        Set<Principal> all = new HashSet<Principal>();
        for (ProtectionDomain d : c.domains()) {
            Principal[] ps = d.getPrincipals();
            if (ps != null) all.addAll(Arrays.asList(ps));
        }
        return all;
    }

    // MUST-2 + MUST-4: exactly worker ∪ user, worker is the supplied one.
    @Test
    public void carriesExactlyWorkerAndUserPrincipals() {
        Principal worker = name("spiffe://ctrl/worker");
        Principal user = name("spiffe://ctrl/user");
        AdminAuthorizationContext c = AdminAuthorizationAssembler.assemble(
                null, subjectWith(worker),
                Collections.singletonList(subjectWith(user)));

        Set<Principal> expected = new HashSet<Principal>(Arrays.asList(worker, user));
        Assert.assertEquals("carrier authorizes over exactly worker+user",
                expected, domainPrincipals(c));
        // The verified user Subject is carried for callAs (step 4).
        Assert.assertEquals(1, c.userSubjects().length);
        Assert.assertTrue(c.userSubjects()[0].getPrincipals().contains(user));
    }

    // MUST-4: with no user subjects, only the connection-derived worker.
    @Test
    public void workerOnly_carriesOnlyConnectionDerivedWorker() {
        Principal worker = name("spiffe://ctrl/worker");
        AdminAuthorizationContext c = AdminAuthorizationAssembler.assemble(
                null, subjectWith(worker), Collections.<Subject>emptyList());
        Assert.assertEquals(Collections.singleton(worker), domainPrincipals(c));
        Assert.assertEquals(0, c.userSubjects().length);
    }

    // MUST-3: a user Subject with no principals (no verified token) contributes
    // ZERO principals; with no worker either, the carrier is principal-empty.
    @Test
    public void noVerifiedToken_contributesZeroPrincipals() {
        Subject emptyUser = subjectWith(); // readUserSubjects shape: nothing verified
        AdminAuthorizationContext c = AdminAuthorizationAssembler.assemble(
                null, null, Collections.singletonList(emptyUser));
        Assert.assertTrue("no verified token -> zero principals in the carrier",
                domainPrincipals(c).isEmpty());
    }

    // MUST-3 (mixed): a verified worker plus an empty (unverified) user Subject
    // yields exactly the worker -- the empty user adds nothing it did not verify.
    @Test
    public void emptyUserSubject_addsNothingToWorker() {
        Principal worker = name("spiffe://ctrl/worker");
        AdminAuthorizationContext c = AdminAuthorizationAssembler.assemble(
                null, subjectWith(worker),
                Collections.singletonList(subjectWith()));
        Assert.assertEquals(Collections.singleton(worker), domainPrincipals(c));
    }

    // Step 1: captured code sources are preserved (one carrier domain per
    // captured domain), each stamped with the union principals.
    @Test
    public void capturedCodeSourcesArePreserved_stampedWithUnionPrincipals() {
        Principal worker = name("spiffe://ctrl/worker");
        java.security.CodeSource csA = new java.security.CodeSource(
                null, (java.security.cert.Certificate[]) null);
        ProtectionDomain codeDomain = new ProtectionDomain(csA, null, null, null);
        AdminAuthorizationContext c = AdminAuthorizationAssembler.assemble(
                new ProtectionDomain[] { codeDomain }, subjectWith(worker),
                Collections.<Subject>emptyList());
        ProtectionDomain[] domains = c.domains();
        Assert.assertEquals(1, domains.length);
        Assert.assertEquals(Collections.singleton(worker),
                new HashSet<Principal>(Arrays.asList(domains[0].getPrincipals())));
    }

    // Empty input still yields a usable (principal-empty) carrier, never null.
    @Test
    public void emptyEverything_yieldsPrincipalEmptyCarrier() {
        AdminAuthorizationContext c = AdminAuthorizationAssembler.assemble(
                null, null, null);
        Assert.assertNotNull(c);
        Assert.assertTrue(c.domains().length >= 1);
        Assert.assertTrue(domainPrincipals(c).isEmpty());
        Assert.assertEquals(0, c.userSubjects().length);
    }
}
