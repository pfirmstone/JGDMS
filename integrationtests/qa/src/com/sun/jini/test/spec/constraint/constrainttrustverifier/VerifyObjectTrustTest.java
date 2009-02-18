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
package com.sun.jini.test.spec.constraint.constrainttrustverifier;

import java.util.logging.Level;

// java
import java.security.Principal;

// javax
import javax.security.auth.x500.X500Principal;
import javax.security.auth.kerberos.KerberosPrincipal;

// net.jini
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.ClientMaxPrincipalType;
import net.jini.core.constraint.ClientMinPrincipalType;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.DelegationAbsoluteTime;
import net.jini.core.constraint.DelegationRelativeTime;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.ClientMaxPrincipal;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.constraint.ConstraintTrustVerifier;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.security.TrustVerifier;

// com.sun.jini
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.constraint.util.TrueTrustVerifierContext;
import com.sun.jini.test.spec.constraint.util.FalseTrustVerifierContext;
import com.sun.jini.test.spec.constraint.util.PrincipalTrustVerifierContext;
import com.sun.jini.test.spec.constraint.util.ConstraintTrustVerifierContext;
import com.sun.jini.test.spec.constraint.util.ICTrustVerifierContext;
import com.sun.jini.test.spec.constraint.util.TestPrincipal;
import com.sun.jini.test.spec.constraint.util.TestConstraint;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     'isTrustedObject' method of ConstraintTrustVerifier returns true if the
 *     specified object is known to be trusted to correctly implement its
 *     contract; returns false otherwise. Returns true if any of the following
 *     conditions holds, and returns false otherwise:
 *     - The object is an instance of any of the following classes:
 *       ClientAuthentication, ClientMaxPrincipalType, ClientMinPrincipalType,
 *       Confidentiality, DelegationAbsoluteTime, DelegationRelativeTime,
 *       Delegation, Integrity, ServerAuthentication
 *     - The object is an instance of any of the following classes:
 *       ClientMinPrincipal, ClientMaxPrincipal, ServerMinPrincipal
 *       and all of the principals in that object are trusted (determined by
 *       calling the isTrustedObject method on the specified context
 *       with each principal)
 *     - The object is an instance of ConstraintAlternatives and all
 *       of the constraint alternatives in that object are trusted (determined
 *       by calling the isTrustedObject method on the specified context with
 *       each constraint alternative)
 *     - The object is an instance of BasicMethodConstraints and all
 *       the InvocationConstraints instances in that object are trusted
 *       (determined by calling the isTrustedObject method on the
 *       specified context with each instance)
 *     - The object is an instance of InvocationConstraints and
 *       all of the constraints (both requirements and preferences) in that
 *       object are trusted (determined by calling the
 *       isTrustedObject method on the specified context with each constraint)
 *     - The object is an instance of X500Principal or KerberosPrincipal
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     TrueTrustVerifierContext - class implementing TrustVerifier.Context
 *             interface whose 'isTrustedObject' method of this class always
 *             returns true
 *     FalseTrustVerifierContext - class implementing TrustVerifier.Context
 *             interface whose 'isTrustedObject' method of this class always
 *             returns false
 *     PrincipalTrustVerifierContext - class implementing TrustVerifier.Context
 *             interface. 'isTrustedObject' method of this class returns true
 *             if parameter is an instance of TestPrincipal and it's
 *             'isTrusted' method return true
 *     ConstraintTrustVerifierContext - class implementing TrustVerifier.Context
 *             interface. 'isTrustedObject' method of this class returns true
 *             if parameter is an instance of ClientAuthentication,
 *             Delegation, Integrity or ServerAuthentication and false otherwise
 *     ICTrustVerifierContext - class implementing TrustVerifier.Context
 *             interface. 'isTrustedObject' method of this class returns true
 *             if parameter is an instance of InvocationConstraints and
 *             all constraints returned by it's 'requirements' method are
 *             instances of ClientAuthentication, Delegation, Integrity or
 *             ServerAuthentication and false otherwise
 *     TestPrincipal - class implementing Principal interface having
 *             constructor with boolean parameter. This value is returned
 *             by it's 'isTrusted' method.
 *     TestConstraint - class implementing InvocationConstraint interface
 *
 * Action
 *   The test performs the following steps:
 *     1) construct ConstraintTrustVerifier
 *     2) construct ClientAuthentication, Confidentiality, Delegation,
 *        Integrity, ServerAuthentication with both true/false parameters for
 *        constructors, ClientMaxPrincipalType and ClientMinPrincipalType with
 *        any class as a parameter for constructors, DelegationAbsoluteTime and
 *        DelegationRelativeTime with valid minStart, maxStart, minStop &
 *        maxStop parameters for constructors
 *     3) call 'isTrustedObject' method of constructed ConstraintTrustVerifier
 *        with each of constructed constraints and FalseTrustVerifierContext
 *     4) assert that true will be returned
 *     5) construct TestPrincipal1 with true parameter for constructor
 *     6) construct TestPrincipal2 with true parameter for constructor
 *     7) construct ClientMinPrincipal, ClientMaxPrincipal, ServerMinPrincipal
 *        with array of principals containing only TestPrincipal1 &
 *        TestPrincipal2
 *     8) call 'isTrustedObject' method of constructed ConstraintTrustVerifier
 *        with each of constructed constraints and PrincipalTrustVerifierContext
 *     9) assert that 'isTrustedObject' method will return true each time
 *     10) construct TestPrincipal3 with false parameter for constructor
 *     11) construct ClientMinPrincipal, ClientMaxPrincipal, ServerMinPrincipal
 *         with array of principals containing TestPrincipal1, TestPrincipal2
 *         and TestPrincipal3
 *     12) call 'isTrustedObject' method of constructed ConstraintTrustVerifier
 *         with each of constructed constraints and
 *         PrincipalTrustVerifierContext
 *     13) assert that 'isTrustedObject' method will return false
 *     14) construct ConstraintAlternatives1 with the following array of
 *         constraints: Delegation, Integrity and
 *         ClientAuthentication
 *     15) call 'isTrustedObject' method of constructed ConstraintTrustVerifier
 *         with constructed ConstraintAlternatives1 and
 *         ConstraintTrustVerifierContext
 *     16) assert that 'isTrustedObject' method will return true
 *     17) construct ConstraintAlternatives2 with the following array of
 *         constraints: Delegation, Confidentiality and
 *         ClientAuthentication
 *     18) call 'isTrustedObject' method of constructed ConstraintTrustVerifier
 *         with constructed ConstraintAlternatives2 and
 *         ConstraintTrustVerifierContext
 *     19) assert that 'isTrustedObject' method will return false
 *     20) construct InvocationConstraints1 with ClientAuthentication and 
 *         Delegation as requirements constraints
 *     21) construct InvocationConstraints2 with ClientAuthentication and 
 *         Integrity as requirements constraints
 *     22) construct InvocationConstraints3 with ClientAuthentication, 
 *         Confidentiality and Delegation as requirements
 *         constraints
 *     23) construct BasicMethodConstraints.MethodDesc1 with
 *         InvocationConstraints1
 *     24) construct BasicMethodConstraints.MethodDesc2 with
 *         InvocationConstraints2
 *     25) construct BasicMethodConstraints.MethodDesc3 with
 *         InvocationConstraints3
 *     26) construct BasicMethodConstraints1 with array containing constructed
 *         MethodDesc1 and MethodDesc2
 *     27) call 'isTrustedObject' method of constructed ConstraintTrustVerifier
 *         with constructed BasicMethodConstraints1 and ICTrustVerifierContext
 *     28) asserth that 'isTrustedObject' method will return true
 *     29) construct BasicMethodConstraints2 with array containing constructed
 *         MethodDesc1, MethodDesc3 and MethodDesc2
 *     30) call 'isTrustedObject' method of constructed ConstraintTrustVerifier
 *         with constructed BasicMethodConstraints2 and ICTrustVerifierContext
 *     31) asserth that 'isTrustedObject' method will return false
 *     32) construct InvocationConstraints4 with ClientAuthentication and 
 *         Delegation as requirements constraints and Integrity
 *         and ServerAuthentication as preferences constraints
 *     33) call 'isTrustedObject' method of constructed ConstraintTrustVerifier
 *         with constructed InvocationConstraints4 and
 *         ConstraintTrustVerifierContext
 *     34) assert that 'isTrustedObject' method will return true
 *     35) construct InvocationConstraints5 with ClientAuthentication and 
 *         Delegation as requirements constraints and Integrity,
 *         Confidentiality and ServerAuthentication as preferences constraints
 *     36) call 'isTrustedObject' method of constructed ConstraintTrustVerifier
 *         with constructed InvocationConstraints5 and
 *         ConstraintTrustVerifierContext
 *     37) assert that 'isTrustedObject' method will return false
 *     38) construct InvocationConstraints6 with ClientAuthentication,
 *         Confidentiality and  Delegation as requirements
 *         constraints and Integrity and ServerAuthentication as preferences
 *         constraints
 *     39) call 'isTrustedObject' method of constructed ConstraintTrustVerifier
 *         with constructed InvocationConstraints6 and
 *         ConstraintTrustVerifierContext
 *     40) assert that 'isTrustedObject' method will return false
 *     41) call 'isTrustedObject' method of constructed ConstraintTrustVerifier
 *         with X500Principal and FalseTrustVerifierContext
 *     42) assert that 'isTrustedObject' method will return true
 *     43) call 'isTrustedObject' method of constructed ConstraintTrustVerifier
 *         with KerberosPrincipal and FalseTrustVerifierContext
 *     44) assert that 'isTrustedObject' method will return true
 *     45) call 'isTrustedObject' method of constructed ConstraintTrustVerifier
 *         with TestPrincipal and TrueTrustVerifierContext
 *     46) assert that 'isTrustedObject' method will return false
 *     47) call 'isTrustedObject' method of constructed ConstraintTrustVerifier
 *         with TestConstraint and TrueTrustVerifierContext
 *     48) assert that 'isTrustedObject' method will return false
 * </pre>
 */
public class VerifyObjectTrustTest extends QATest {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        TrustVerifier.Context falseTvc = new FalseTrustVerifierContext();
        TrustVerifier.Context trueTvc = new TrueTrustVerifierContext();
        TrustVerifier.Context prinTvc = new PrincipalTrustVerifierContext();
        TrustVerifier.Context conTvc = new ConstraintTrustVerifierContext();
        TrustVerifier.Context icTvc = new ICTrustVerifierContext();
        TestPrincipal tp1 = new TestPrincipal("true TP", true);
        TestPrincipal tp2 = new TestPrincipal("true TP1", true);
        TestPrincipal tp3 = new TestPrincipal("false TP", false);
        BasicMethodConstraints.MethodDesc md1 =
                new BasicMethodConstraints.MethodDesc("Foo",
                    new InvocationConstraints(
                        new InvocationConstraint[] {
                            ClientAuthentication.YES,
                            Delegation.YES }, null));
        BasicMethodConstraints.MethodDesc md2 =
                new BasicMethodConstraints.MethodDesc("Foo1",
                    new InvocationConstraints(
                        new InvocationConstraint[] {
                            ClientAuthentication.YES,
                            Integrity.YES }, null));
        BasicMethodConstraints.MethodDesc md3 =
                new BasicMethodConstraints.MethodDesc("Foo2",
                    new InvocationConstraints(
                        new InvocationConstraint[] {
                            ClientAuthentication.YES,
                            Confidentiality.YES,
                            Delegation.YES }, null));
        Object[] testObjs = new Object[] {
            ClientAuthentication.YES,
            Confidentiality.YES,
            Delegation.YES,
            Integrity.YES,
            ServerAuthentication.YES,
            ClientAuthentication.NO,
            Confidentiality.NO,
            Delegation.NO,
            Integrity.NO,
            ServerAuthentication.NO,
            new ClientMaxPrincipalType(TestPrincipal.class),
            new ClientMinPrincipalType(TestPrincipal.class),
            new DelegationRelativeTime(1000, 2000, 3000, 4000),
            new DelegationAbsoluteTime(1000, 2000, 3000, 4000),
            new ClientMinPrincipal(new Principal[] { tp1, tp2 }),
            new ClientMaxPrincipal(new Principal[] { tp1, tp2 }),
            new ServerMinPrincipal(new Principal[] { tp1, tp2 }),
            new ClientMinPrincipal(new Principal[] { tp1, tp3, tp2 }),
            new ClientMaxPrincipal(new Principal[] { tp1, tp3, tp2 }),
            new ServerMinPrincipal(new Principal[] { tp1, tp3, tp2 }),
            new ConstraintAlternatives(new InvocationConstraint[] {
                Delegation.YES,
                Integrity.YES,
                ClientAuthentication.YES }),
            new ConstraintAlternatives(new InvocationConstraint[] {
                Delegation.YES,
                Confidentiality.YES,
                ClientAuthentication.YES }),
            new BasicMethodConstraints(
                    new BasicMethodConstraints.MethodDesc[] { md1, md2 }),
            new BasicMethodConstraints(
                    new BasicMethodConstraints.MethodDesc[] { md1, md3, md2 }),
            new InvocationConstraints(
                new InvocationConstraint[] {
                    ClientAuthentication.YES, Delegation.YES },
                new InvocationConstraint[] {
                    Integrity.YES, ServerAuthentication.YES }),
            new InvocationConstraints(
                new InvocationConstraint[] {
                    ClientAuthentication.YES, Delegation.YES },
                new InvocationConstraint[] {
                    Integrity.YES, Confidentiality.YES,
                    ServerAuthentication.YES }),
            new InvocationConstraints(
                new InvocationConstraint[] {
                    ClientAuthentication.YES, Confidentiality.YES,
                    Delegation.YES },
                new InvocationConstraint[] {
                    Integrity.YES, ServerAuthentication.YES }),
            new X500Principal("CN=Test, OU=JINI, O=Sun Microsystems, C=US"),
            new KerberosPrincipal("Test@test.com"),
            new TestPrincipal("TEST", true),
            new TestConstraint()
        };
        TrustVerifier.Context[] testCtxs = new TrustVerifier.Context[] {
            falseTvc, falseTvc, falseTvc, falseTvc, falseTvc, falseTvc,
            falseTvc, falseTvc, falseTvc, falseTvc, falseTvc, falseTvc,
            falseTvc, falseTvc,
            prinTvc, prinTvc, prinTvc, prinTvc, prinTvc, prinTvc,
            conTvc, conTvc, icTvc, icTvc,
            conTvc, conTvc, conTvc,
            falseTvc, falseTvc, trueTvc, trueTvc
        };
        boolean[] expRes = new boolean[] {
            true, true, true, true, true, true,
            true, true, true, true, true, true,
            true, true,
            true, true, true, false, false, false,
            true, false, true, false,
            true, false, false,
            true, true, false, false
        };
        ConstraintTrustVerifier ctv = new ConstraintTrustVerifier();
        boolean res;

        for (int i = 0; i < testObjs.length; ++i) {
            logger.fine("Calling 'isTrustedObject' method with the "
                    + "following parameters:");
            logger.fine("  object: " + testObjs[i]);
            logger.fine("  context: " + testCtxs[i]);
            res = ctv.isTrustedObject(testObjs[i], testCtxs[i]);

            if (res != expRes[i]) {
                // FAIL
                throw new TestException(
                        "'isTrustedObject' method returned " + res
                        + " while " + expRes[i] + " was expected.");
            } else {
                // PASS
                logger.fine("'isTrustedObject' method returned " + res
                        + " as expected.");
            }
        }
    }
}
