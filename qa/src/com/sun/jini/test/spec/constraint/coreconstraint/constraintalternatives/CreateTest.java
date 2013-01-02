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
package com.sun.jini.test.spec.constraint.coreconstraint.constraintalternatives;

import java.util.logging.Level;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.QATestEnvironment;

// java.util
import com.sun.jini.qa.harness.Test;
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Arrays;

// javax.security
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.x500.X500Principal;

// Davis packages
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ClientMaxPrincipal;
import net.jini.core.constraint.ClientMaxPrincipalType;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.ClientMinPrincipalType;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.core.constraint.ConnectionAbsoluteTime;
import net.jini.core.constraint.ConnectionRelativeTime;
import net.jini.core.constraint.DelegationAbsoluteTime;
import net.jini.core.constraint.DelegationRelativeTime;
import net.jini.core.constraint.InvocationConstraint;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of
 *   {@link net.jini.core.constraint.ConstraintAlternatives#create(Collection)}
 *   and
 *   {@link net.jini.core.constraint.ConstraintAlternatives#create(InvocationConstraint[])}
 *   methods. These methods return a constraint representing the specified
 *   alternative constraints, with duplicate constraints removed.
 *   If a single constraint remains after duplicates are removed, then that
 *   constraint is returned, otherwise an instance of
 *   {@link net.jini.core.constraint.ConstraintAlternatives} containing the
 *   remaining constraints is returned.
 *   The argument passed to these methods is neither modified nor retained;
 *   subsequent changes to that argument have no effect on the instance
 *   created.
 *   Parameter for both methods:
 *     c - the alternative constraints
 *   Return value for both methods:
 *     a constraint representing the specified alternative constraints, with
 *     duplicate constraints removed
 *   Both methods throw:
 *     {@link java.lang.NullPointerException}
 *       if the argument is null or any element is null
 *     {@link java.lang.IllegalArgumentException}
 *       if the argument is empty, or if any of the elements are instances of
 *       {@link net.jini.core.constraint.ConstraintAlternatives}
 *   {@link net.jini.core.constraint.ConstraintAlternatives#create(Collection)}
 *   method throws {@link java.lang.IllegalArgumentException} if the
 *   elements are not all instances of
 *   {@link net.jini.core.constraint.InvocationConstraint}
 *
 * Test Cases:
 *   TestCase #1
 *    The alternative constraints specified as argument contains only 2
 *    duplicate constraints that are instances of
 *    {@link net.jini.core.constraint.ClientAuthentication}
 *    (ClientAuthentication.YES). It's verified that both
 *    ConstraintAlternatives.create() methods return single constraint
 *    (ClientAuthentication.YES), i.e. duplicate constraint is removed.
 *    It's verified that the argument passed to this method is neither modified
 *    nor retained; subsequent changes to that argument have no effect on the
 *    instance created.
 *   TestCase #2
 *    The alternative constraints specified as argument contains only 2
 *    duplicate constraints that are instances of
 *    {@link net.jini.core.constraint.ClientAuthentication}
 *    (ClientAuthentication.NO). It's verified that both
 *    ConstraintAlternatives.create() methods return single constraint
 *    (ClientAuthentication.NO), i.e. duplicate constraint is removed.
 *    It's verified that the argument passed to this method is neither modified
 *    nor retained; subsequent changes to that argument have no effect on the
 *    instance created.
 *   TestCase #3
 *    The alternative constraints specified as argument contains only 2
 *    duplicate constraints that are instances of
 *    {@link net.jini.core.constraint.Confidentiality}
 *    (Confidentiality.YES). It's verified that both
 *    ConstraintAlternatives.create() methods return single constraint
 *    (Confidentiality.YES), i.e. duplicate constraint is removed.
 *    It's verified that the argument passed to this method is neither modified
 *    nor retained; subsequent changes to that argument have no effect on the
 *    instance created.
 *   TestCase #4
 *    The alternative constraints specified as argument contains only 2
 *    duplicate constraints that are instances of
 *    {@link net.jini.core.constraint.Confidentiality}
 *    (Confidentiality.NO). It's verified that both
 *    ConstraintAlternatives.create() methods return single constraint
 *    (Confidentiality.NO), i.e. duplicate constraint is removed.
 *    It's verified that the argument passed to this method is neither modified
 *    nor retained; subsequent changes to that argument have no effect on the
 *    instance created.
 *   TestCase #5
 *    The alternative constraints specified as argument contains only 2
 *    duplicate constraints that are instances of
 *    {@link net.jini.core.constraint.Delegation}
 *    (Delegation.YES). It's verified that both
 *    ConstraintAlternatives.create() methods return single constraint
 *    (Delegation.YES), i.e. duplicate constraint is removed.
 *    It's verified that the argument passed to this method is neither modified
 *    nor retained; subsequent changes to that argument have no effect on the
 *    instance created.
 *   TestCase #6
 *    The alternative constraints specified as argument contains only 2
 *    duplicate constraints that are instances of
 *    {@link net.jini.core.constraint.Delegation}
 *    (Delegation.NO). It's verified that both
 *    ConstraintAlternatives.create() methods return single constraint
 *    (Delegation.NO), i.e. duplicate constraint is removed.
 *    It's verified that the argument passed to this method is neither modified
 *    nor retained; subsequent changes to that argument have no effect on the
 *    instance created.
 *   TestCase #7
 *    The alternative constraints specified as argument contains only 2
 *    duplicate constraints that are instances of
 *    {@link net.jini.core.constraint.Integrity}
 *    (Integrity.YES). It's verified that both
 *    ConstraintAlternatives.create() methods return single constraint
 *    (Integrity.YES), i.e. duplicate constraint is removed.
 *    It's verified that the argument passed to this method is neither modified
 *    nor retained; subsequent changes to that argument have no effect on the
 *    instance created.
 *   TestCase #8
 *    The alternative constraints specified as argument contains only 2
 *    duplicate constraints that are instances of
 *    {@link net.jini.core.constraint.Integrity}
 *    (Integrity.NO). It's verified that both
 *    ConstraintAlternatives.create() methods return single constraint
 *    (Integrity.NO), i.e. duplicate constraint is removed.
 *    It's verified that the argument passed to this method is neither modified
 *    nor retained; subsequent changes to that argument have no effect on the
 *    instance created.
 *   TestCase #9
 *    The alternative constraints specified as argument contains only 2
 *    duplicate constraints that are instances of
 *    {@link net.jini.core.constraint.ServerAuthentication}
 *    (ServerAuthentication.YES). It's verified that both
 *    ConstraintAlternatives.create() methods return single constraint
 *    (ServerAuthentication.YES), i.e. duplicate constraint is removed.
 *    It's verified that the argument passed to this method is neither modified
 *    nor retained; subsequent changes to that argument have no effect on the
 *    instance created.
 *   TestCase #10
 *    The alternative constraints specified as argument contains only 2
 *    duplicate constraints that are instances of
 *    {@link net.jini.core.constraint.ServerAuthentication}
 *    (ServerAuthentication.NO). It's verified that both
 *    ConstraintAlternatives.create() methods return single constraint
 *    (ServerAuthentication.NO), i.e. duplicate constraint is removed.
 *    It's verified that the argument passed to this method is neither modified
 *    nor retained; subsequent changes to that argument have no effect on the
 *    instance created.
 *   TestCase #11
 *    The alternative constraints specified as argument contains only 2
 *    duplicate constraints that are instances of
 *    {@link net.jini.core.constraint.ClientMaxPrincipal}. It's verified that
 *    both ConstraintAlternatives.create() methods
 *    return this non-duplicate constraint (ClientMaxPrincipal), i.e. all
 *    duplicates are removed. It's verified that the argument passed to this
 *    method is neither modified nor retained; subsequent changes to that
 *    argument have no effect on the instance created.
 *   TestCase #12
 *    The alternative constraints specified as argument contains only 2
 *    duplicate constraints that are instances of
 *    {@link net.jini.core.constraint.ClientMaxPrincipalType}. It's verified
 *    that both ConstraintAlternatives.create() methods
 *    return this non-duplicate constraint (ClientMaxPrincipalType), i.e. all
 *    duplicates are removed. It's verified that the argument passed to this
 *    method is neither modified nor retained; subsequent changes to that
 *    argument have no effect on the instance created.
 *   TestCase #13
 *    The alternative constraints specified as argument contains only 2
 *    duplicate constraints that are instances of
 *    {@link net.jini.core.constraint.ClientMinPrincipal}. It's verified that
 *    both ConstraintAlternatives.create() methods
 *    return this non-duplicate constraint (ClientMinPrincipal), i.e. all
 *    duplicates are removed. It's verified that the argument passed to this
 *    method is neither modified nor retained; subsequent changes to that
 *    argument have no effect on the instance created.
 *   TestCase #14
 *    The alternative constraints specified as argument contains only 2
 *    duplicate constraints that are instances of
 *    {@link net.jini.core.constraint.ClientMinPrincipalType}. It's verified
 *    that both ConstraintAlternatives.create() methods
 *    return this non-duplicate constraint (ClientMinPrincipalType), i.e. all
 *    duplicates are removed. It's verified that the argument passed to this
 *    method is neither modified nor retained; subsequent changes to that
 *    argument have no effect on the instance created.
 *   TestCase #15
 *    The alternative constraints specified as argument contains only 2
 *    duplicate constraints that are instances of
 *    {@link net.jini.core.constraint.ServerMinPrincipal}. It's verified that
 *    both ConstraintAlternatives.create() methods
 *    return this non-duplicate constraint (ServerMinPrincipal), i.e. all
 *    duplicates are removed. It's verified that the argument passed to this
 *    method is neither modified nor retained; subsequent changes to that
 *    argument have no effect on the instance created.
 *   TestCase #16
 *    The alternative constraints specified as argument contains only 2
 *    duplicate constraints that are instances of
 *    {@link net.jini.core.constraint.ConnectionAbsoluteTime}. It's verified
 *    that both ConstraintAlternatives.create() methods
 *    return this non-duplicate constraint (ConnectionAbsoluteTime), i.e. all
 *    duplicates are removed. It's verified that the argument passed to this
 *    method is neither modified nor retained; subsequent changes to that
 *    argument have no effect on the instance created.
 *   TestCase #17
 *    The alternative constraints specified as argument contains only 2
 *    duplicate constraints that are instances of
 *    {@link net.jini.core.constraint.ConnectionRelativeTime}. It's verified
 *    that both ConstraintAlternatives.create() methods
 *    return this non-duplicate constraint (ConnectionRelativeTime), i.e. all
 *    duplicates are removed. It's verified that the argument passed to this
 *    method is neither modified nor retained; subsequent changes to that
 *    argument have no effect on the instance created.
 *   TestCase #18
 *    The alternative constraints specified as argument contains only 2
 *    duplicate constraints that are instances of
 *    {@link net.jini.core.constraint.DelegationAbsoluteTime}. It's verified
 *    that both ConstraintAlternatives.create() methods
 *    return this non-duplicate constraint (DelegationAbsoluteTime), i.e. all
 *    duplicates are removed. It's verified that the argument passed to this
 *    method is neither modified nor retained; subsequent changes to that
 *    argument have no effect on the instance created.
 *   TestCase #19
 *    The alternative constraints specified as argument contains only 2
 *    duplicate constraints that are instances of
 *    {@link net.jini.core.constraint.DelegationRelativeTime}. It's verified
 *    that both ConstraintAlternatives.create() methods
 *    return this non-duplicate constraint (DelegationRelativeTime), i.e. all
 *    duplicates are removed. It's verified that the argument passed to this
 *    method is neither modified nor retained; subsequent changes to that
 *    argument have no effect on the instance created.
 *   TestCase #20
 *    The alternative constraints specified as argument contains several
 *    duplicate and non-duplicate constraints. It's verified
 *    that both ConstraintAlternatives.create() methods returns an instance of
 *    {@link net.jini.core.constraint.ConstraintAlternatives} containing all
 *    non-duplicate constraint, i.e. all duplicates are removed. It's verified
 *    that the argument passed to this method is neither modified nor retained;
 *    subsequent changes to that argument have no effect on the instance
 *    created.
 *   TestCase #21
 *    The argument passed to ConstraintAlternatives.create() methods is
 *    null. It's verified that both ConstraintAlternatives.create() methods
 *    throw {@link java.lang.NullPointerException}.
 *   TestCase #22
 *    The argument passed to ConstraintAlternatives.create() methods is
 *    non-null, but contains null element. It's verified that
 *    both ConstraintAlternatives.create() methods throw
 *    {@link java.lang.NullPointerException}.
 *   TestCase #23
 *    The argument passed to ConstraintAlternatives.create() methods is empty
 *    collection. It's verified that both ConstraintAlternatives.create()
 *    methods throw {@link java.lang.IllegalArgumentException}.
 *   TestCase #24
 *    The argument passed to ConstraintAlternatives.create() methods contains
 *    element that is an instance of
 *    {@link net.jini.core.constraint.ConstraintAlternatives}. It's verified
 *    that both ConstraintAlternatives.create() methods throw
 *    {@link java.lang.IllegalArgumentException}.
 *   TestCase #25
 *    The argument passed to
 *    {@link net.jini.core.constraint.ConstraintAlternatives#create(Collection)}
 *    method contains element that isn't an instance of
 *    {@link net.jini.core.constraint.InvocationConstraint}. It's verified that
 *    {@link net.jini.core.constraint.ConstraintAlternatives#create(Collection)}
 *    methods throw {@link java.lang.IllegalArgumentException}.
 *
 * Infrastructure:
 *     - {@link CreateTest}
 *         performs actions; this file
 *
 * Actions:
 *   Test performs the following steps in each test case:
 *     - constructing constraint that is used as an argument for the methods to
 *       be tested;
 *     - invoking
 *       {@link net.jini.core.constraint.ConstraintAlternatives#create(Collection)}
 *       method and verifying the result according to the expected result for
 *       the test case;
 *     - invoking
 *       {@link net.jini.core.constraint.ConstraintAlternatives#create(InvocationConstraint[])}
 *       method and verifying the result according to the expected result for
 *       the test case.
 *
 * </pre>
 */
public class CreateTest extends QATestEnvironment implements Test {
    QAConfig config;

    /**
     * This method performs all test cases mentioned in the class description.
     */
    public void run() throws TestException {
        config = getConfig();

        // TestCase #1
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #1:: "
                + "Only duplicate ClientAuthentication.YES constraints exist");
        {
            Collection argColl = new ArrayList();
            argColl.add(ClientAuthentication.YES);
            argColl.add(ClientAuthentication.YES);

            if (!checker(argColl, ClientAuthentication.YES)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        // TestCase #2
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #2:: "
                + "Only duplicate ClientAuthentication.NO constraints exist");
        {
            Collection argColl = new ArrayList();
            argColl.add(ClientAuthentication.NO);
            argColl.add(ClientAuthentication.NO);

            if (!checker(argColl, ClientAuthentication.NO)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        // TestCase #3
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #3:: "
                + "Only duplicate Confidentiality.YES constraints exist");
        {
            Collection argColl = new ArrayList();
            argColl.add(Confidentiality.YES);
            argColl.add(Confidentiality.YES);

            if (!checker(argColl, Confidentiality.YES)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        // TestCase #4
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #4:: "
                + "Only duplicate Confidentiality.NO constraints exist");
        {
            Collection argColl = new ArrayList();
            argColl.add(Confidentiality.NO);
            argColl.add(Confidentiality.NO);

            if (!checker(argColl, Confidentiality.NO)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        // TestCase #5
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #5:: "
                + "Only duplicate Delegation.YES constraints exist");
        {
            Collection argColl = new ArrayList();
            argColl.add(Delegation.YES);
            argColl.add(Delegation.YES);

            if (!checker(argColl, Delegation.YES)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        // TestCase #6
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #6:: "
                + "Only duplicate Delegation.NO constraints exist");
        {
            Collection argColl = new ArrayList();
            argColl.add(Delegation.NO);
            argColl.add(Delegation.NO);

            if (!checker(argColl, Delegation.NO)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        // TestCase #7
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #7:: "
                + "Only duplicate Integrity.YES constraints exist");
        {
            Collection argColl = new ArrayList();
            argColl.add(Integrity.YES);
            argColl.add(Integrity.YES);

            if (!checker(argColl, Integrity.YES)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        // TestCase #8
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #8:: "
                + "Only duplicate Integrity.NO constraints exist");
        {
            Collection argColl = new ArrayList();
            argColl.add(Integrity.NO);
            argColl.add(Integrity.NO);

            if (!checker(argColl, Integrity.NO)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        // TestCase #9
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #9:: "
                + "Only duplicate ServerAuthentication.YES constraints exist");
        {
            Collection argColl = new ArrayList();
            argColl.add(ServerAuthentication.YES);
            argColl.add(ServerAuthentication.YES);

            if (!checker(argColl, ServerAuthentication.YES)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        // TestCase #10
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #10:: "
                + "Only duplicate ServerAuthentication.NO constraints exist");
        {
            Collection argColl = new ArrayList();
            argColl.add(ServerAuthentication.NO);
            argColl.add(ServerAuthentication.NO);

            if (!checker(argColl, ServerAuthentication.NO)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        // TestCase #11
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #11:: "
                + "Only duplicate ClientMaxPrincipal constraints exist");

        try {
            Collection argColl = new ArrayList();
            ClientMaxPrincipal ic = new ClientMaxPrincipal(new
                    KerberosPrincipal("duke@FOO.COM"));
            argColl.add(ic);
            argColl.add(ic);

            if (!checker(argColl, ic)) {
                throw new TestException(
                        "" + " test failed");
            }
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception has been thrown while creating"
                    + " ClientMaxPrincipal object: "
                    + e);
            throw new TestException(
                    "" + " test failed");
        }

        // TestCase #12
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #12:: "
                + "Only duplicate ClientMaxPrincipalType constraints exist");

        try {
            Collection argColl = new ArrayList();
            ClientMaxPrincipalType ic = new
                    ClientMaxPrincipalType(KerberosPrincipal.class);
            argColl.add(ic);
            argColl.add(ic);

            if (!checker(argColl, ic)) {
                throw new TestException(
                        "" + " test failed");
            }
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception has been thrown while creating"
                    + " ClientMaxPrincipalType object: "
                    + e);
            throw new TestException(
                    "" + " test failed");
        }

        // TestCase #13
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #13:: "
                + "Only duplicate ClientMinPrincipal constraints exist");

        try {
            Collection argColl = new ArrayList();
            ClientMinPrincipal ic = new ClientMinPrincipal(new
                    KerberosPrincipal("duke@FOO.COM"));
            argColl.add(ic);
            argColl.add(ic);

            if (!checker(argColl, ic)) {
                throw new TestException(
                        "" + " test failed");
            }
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception has been thrown while creating"
                    + " ClientMinPrincipal object: "
                    + e);
            throw new TestException(
                    "" + " test failed");
        }

        // TestCase #14
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #14:: "
                + "Only duplicate ClientMinPrincipalType constraints exist");

        try {
            Collection argColl = new ArrayList();
            ClientMinPrincipalType ic = new
                    ClientMinPrincipalType(KerberosPrincipal.class);
            argColl.add(ic);
            argColl.add(ic);

            if (!checker(argColl, ic)) {
                throw new TestException(
                        "" + " test failed");
            }
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception has been thrown while creating"
                    + " ClientMinPrincipalType object: "
                    + e);
            throw new TestException(
                    "" + " test failed");
        }

        // TestCase #15
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #15:: "
                + "Only duplicate ServerMinPrincipal constraints exist");

        try {
            Collection argColl = new ArrayList();
            ServerMinPrincipal ic = new ServerMinPrincipal(new
                    KerberosPrincipal("duke@FOO.COM"));
            argColl.add(ic);
            argColl.add(ic);

            if (!checker(argColl, ic)) {
                throw new TestException(
                        "" + " test failed");
            }
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception has been thrown while creating"
                    + " ServerMinPrincipal object: "
                    + e);
            throw new TestException(
                    "" + " test failed");
        }

        // TestCase #16
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #16:: "
                + "Only duplicate ConnectionAbsoluteTime constraints exist");
        {
            Collection argColl = new ArrayList();
            ConnectionAbsoluteTime ic = new ConnectionAbsoluteTime((long) 0);
            argColl.add(ic);
            argColl.add(ic);

            if (!checker(argColl, ic)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        // TestCase #17
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #17:: "
                + "Only duplicate ConnectionRelativeTime constraints exist");
        {
            Collection argColl = new ArrayList();
            ConnectionRelativeTime ic = new ConnectionRelativeTime((long) 0);
            argColl.add(ic);
            argColl.add(ic);

            if (!checker(argColl, ic)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        // TestCase #18
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #18:: "
                + "Only duplicate DelegationAbsoluteTime constraints exist");
        {
            Collection argColl = new ArrayList();
            DelegationAbsoluteTime ic = new DelegationAbsoluteTime((long) 0,
                    (long) 0, (long) 0, (long) 0);
            argColl.add(ic);
            argColl.add(ic);

            if (!checker(argColl, ic)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        // TestCase #19
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #19:: "
                + "Only duplicate DelegationRelativeTime constraints exist");
        {
            Collection argColl = new ArrayList();
            DelegationRelativeTime ic = new DelegationRelativeTime((long) 0,
                    (long) 0, (long) 0, (long) 0);
            argColl.add(ic);
            argColl.add(ic);

            if (!checker(argColl, ic)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        // TestCase #20
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #20:: "
                + "There are several duplicate and non-duplicate constraints");

        try {
            // Argument for ConstraintAlternatives.create() methods
            Collection argColl = new ArrayList();
            argColl.add(ClientAuthentication.YES);
            argColl.add(ClientAuthentication.YES);
            argColl.add(ClientAuthentication.NO);
            argColl.add(Confidentiality.YES);
            argColl.add(Confidentiality.YES);
            argColl.add(Confidentiality.NO);
            argColl.add(Delegation.YES);
            argColl.add(Delegation.YES);
            argColl.add(Delegation.NO);
            argColl.add(Integrity.YES);
            argColl.add(Integrity.YES);
            argColl.add(Integrity.NO);
            argColl.add(ServerAuthentication.YES);
            argColl.add(ServerAuthentication.YES);
            argColl.add(ServerAuthentication.NO);
            argColl.add(new ClientMaxPrincipal(new
                    KerberosPrincipal("duke@FOO.COM")));
            argColl.add(new ClientMaxPrincipal(new
                    KerberosPrincipal("duke@FOO.COM")));
            argColl.add(new ClientMaxPrincipal(new
                    X500Principal(
                    "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US")));
            argColl.add(new ClientMaxPrincipalType(KerberosPrincipal.class));
            argColl.add(new ClientMaxPrincipalType(KerberosPrincipal.class));
            argColl.add(new ClientMaxPrincipalType(X500Principal.class));
            argColl.add(new ClientMinPrincipal(new
                    KerberosPrincipal("duke@FOO.COM")));
            argColl.add(new ClientMinPrincipal(new
                    KerberosPrincipal("duke@FOO.COM")));
            argColl.add(new ClientMinPrincipal(new
                    X500Principal(
                    "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US")));
            argColl.add(new ClientMinPrincipalType(KerberosPrincipal.class));
            argColl.add(new ClientMinPrincipalType(KerberosPrincipal.class));
            argColl.add(new ClientMinPrincipalType(X500Principal.class));
            argColl.add(new ServerMinPrincipal(new
                    KerberosPrincipal("duke@FOO.COM")));
            argColl.add(new ServerMinPrincipal(new
                    KerberosPrincipal("duke@FOO.COM")));
            argColl.add(new ServerMinPrincipal(new
                    X500Principal(
                    "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US")));
            argColl.add(new ConnectionAbsoluteTime((long) 0));
            argColl.add(new ConnectionAbsoluteTime((long) 0));
            argColl.add(new ConnectionAbsoluteTime((long) 1));
            argColl.add(new ConnectionRelativeTime((long) 0));
            argColl.add(new ConnectionRelativeTime((long) 0));
            argColl.add(new ConnectionRelativeTime((long) 1));
            argColl.add(new DelegationAbsoluteTime((long) 0, (long) 0, (long) 0,
                    (long) 0));
            argColl.add(new DelegationAbsoluteTime((long) 0, (long) 0, (long) 0,
                    (long) 0));
            argColl.add(new DelegationAbsoluteTime((long) 1, (long) 1, (long) 1,
                    (long) 1));
            argColl.add(new DelegationRelativeTime((long) 0, (long) 0, (long) 0,
                    (long) 0));
            argColl.add(new DelegationRelativeTime((long) 0, (long) 0, (long) 0,
                    (long) 0));
            argColl.add(new DelegationRelativeTime((long) 1, (long) 1, (long) 1,
                    (long) 1));

            // Expected result of ConstraintAlternatives.create() methods
            Collection expColl = new ArrayList();
            expColl.add(ClientAuthentication.YES);
            expColl.add(ClientAuthentication.NO);
            expColl.add(Confidentiality.YES);
            expColl.add(Confidentiality.NO);
            expColl.add(Delegation.YES);
            expColl.add(Delegation.NO);
            expColl.add(Integrity.YES);
            expColl.add(Integrity.NO);
            expColl.add(ServerAuthentication.YES);
            expColl.add(ServerAuthentication.NO);
            expColl.add(new ClientMaxPrincipal(new
                    KerberosPrincipal("duke@FOO.COM")));
            expColl.add(new ClientMaxPrincipal(new
                    X500Principal(
                    "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US")));
            expColl.add(new ClientMaxPrincipalType(KerberosPrincipal.class));
            expColl.add(new ClientMaxPrincipalType(X500Principal.class));
            expColl.add(new ClientMinPrincipal(new
                    KerberosPrincipal("duke@FOO.COM")));
            expColl.add(new ClientMinPrincipal(new
                    X500Principal(
                    "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US")));
            expColl.add(new ClientMinPrincipalType(KerberosPrincipal.class));
            expColl.add(new ClientMinPrincipalType(X500Principal.class));
            expColl.add(new ServerMinPrincipal(new
                    KerberosPrincipal("duke@FOO.COM")));
            expColl.add(new ServerMinPrincipal(new
                    X500Principal(
                    "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US")));
            expColl.add(new ConnectionAbsoluteTime((long) 0));
            expColl.add(new ConnectionAbsoluteTime((long) 1));
            expColl.add(new ConnectionRelativeTime((long) 0));
            expColl.add(new ConnectionRelativeTime((long) 1));
            expColl.add(new DelegationAbsoluteTime((long) 0, (long) 0, (long) 0,
                    (long) 0));
            expColl.add(new DelegationAbsoluteTime((long) 1, (long) 1, (long) 1,
                    (long) 1));
            expColl.add(new DelegationRelativeTime((long) 0, (long) 0, (long) 0,
                    (long) 0));
            expColl.add(new DelegationRelativeTime((long) 1, (long) 1, (long) 1,
                    (long) 1));

            if (!checker(argColl, new ConstraintAlternatives(expColl))) {
                throw new TestException(
                        "" + " test failed");
            }
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception has been thrown while creating constraint"
                    + " objects: " + e);
            throw new TestException(
                    "" + " test failed");
        }

        // TestCase #21
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #21:: "
                + "Argument passed to ConstraintAlternatives.create() methods"
                + " is null");
        {
            if (!checkException((Collection) null,
                    java.lang.NullPointerException.class)) {
                throw new TestException(
                        "" + " test failed");
            }

            if (!checkException((InvocationConstraint[]) null,
                    java.lang.NullPointerException.class)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        // TestCase #22
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #22:: "
                + "Argument passed to ConstraintAlternatives.create() methods"
                + " is non-null, but contains null element");
        {
            Collection argColl = new ArrayList();
            argColl.add(ClientAuthentication.YES);
            argColl.add(null);

            if (!checkException(argColl,
                    java.lang.NullPointerException.class)) {
                throw new TestException(
                        "" + " test failed");
            }
            InvocationConstraint[] argArray = (InvocationConstraint[])
                    argColl.toArray(new InvocationConstraint[0]);

            if (!checkException(argArray,
                    java.lang.NullPointerException.class)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        // TestCase #23
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #23:: "
                + "Argument passed to ConstraintAlternatives.create() methods"
                + " is empty");
        {
            if (!checkException(Collections.EMPTY_LIST,
                    java.lang.IllegalArgumentException.class)) {
                throw new TestException(
                        "" + " test failed");
            }

            if (!checkException(new InvocationConstraint[0],
                    java.lang.IllegalArgumentException.class)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        // TestCase #24
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #24:: "
                + "Argument passed to ConstraintAlternatives.create() methods"
                + " contain element that is an instance of"
                + " ConstraintAlternatives");

        try {
            // Temporary Collection
            Collection tmpColl = new ArrayList();
            tmpColl.add(ClientAuthentication.YES);
            tmpColl.add(Confidentiality.YES);

            // Arguments
            Collection argColl = new ArrayList();
            argColl.add(ClientAuthentication.YES);
            argColl.add(Confidentiality.YES);
            argColl.add(new ConstraintAlternatives(tmpColl));
            InvocationConstraint[] argArray = (InvocationConstraint[])
                    argColl.toArray(new InvocationConstraint[0]);

            if (!checkException(argColl,
                    java.lang.IllegalArgumentException.class)) {
                throw new TestException(
                        "" + " test failed");
            }

            if (!checkException(argArray,
                    java.lang.IllegalArgumentException.class)) {
                throw new TestException(
                        "" + " test failed");
            }
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception has been thrown while creating"
                    + " ConstraintAlternatives object: "
                    + e);
            throw new TestException(
                    "" + " test failed");
        }

        logger.log(Level.FINE,
                "\n\t+++++ TestCase #25:: "
                + "Argument passed to ConstraintAlternatives.create() method"
                + " contain element that isn't an instance of"
                + " InvocationConstraint");
        {
            Collection argColl = new ArrayList();
            argColl.add(ClientAuthentication.YES);
            argColl.add(Confidentiality.NO);
            argColl.add("Non-instance of InvocationConstraint");

            if (!checkException(argColl,
                    java.lang.IllegalArgumentException.class)) {
                throw new TestException(
                        "" + " test failed");
            }
        }
        return;
    }

    /**
     * Verifies the methods specified in the class description in the conditions
     * when no exception is expected.
     * The following actions/checks are performed:
     * <pre>
     *   - {@link net.jini.core.constraint.ConstraintAlternatives#create(Collection)}
     *     is invoked for the {@link java.util.Collection} specified by the
     *     first argument c; the result is compared with the second argument ic;
     *   - {@link net.jini.core.constraint.ConstraintAlternatives#create(InvocationConstraint[])}
     *     is invoked for the array of
     *     {@link net.jini.core.constraint.InvocationConstraint} objects
     *     obtained from the first argument c; the result is compared with
     *     the second argument ic;
     *   - for both methods it's verified that the arguments are neither
     *     modified nor retained; subsequent changes to those arguments have
     *     no effect on the instances created.
     * </pre>
     *
     * @param c  argument for ConstraintAlternatives.create() methods
     * @param ic the expected result of ConstraintAlternatives.create() methods
     * @return true if all checks for both method are satisfied or
     *         false otherwise
     */
    public boolean checker(Collection c, InvocationConstraint ic) {

        /*
         * Argument for ConstraintAlternatives.create(Collection) method
         */
        Collection argC;

        /*
         * Argument for ConstraintAlternatives.create(InvocationConstraint[])
         * method
         */
        InvocationConstraint[] argA;

        /*
         * Copy of argument for
         * ConstraintAlternatives.create(InvocationConstraint[]) method
         */
        InvocationConstraint[] argACopy;

        try {
            argC = new ArrayList(c);
            logger.log(Level.FINE,
                    "Collection used as an argument for"
                    + " ConstraintAlternatives.create(Collection) method::\n"
                    + argC);
            argA = (InvocationConstraint[]) c.toArray(new
                    InvocationConstraint[0]);
            logger.log(Level.FINE,
                    "Array used as an argument for"
                    + " ConstraintAlternatives.create(InvocationConstraint[])"
                    + " method::\n");

            for (int i = 0; i < argA.length; i++) {
                logger.log(Level.FINE, "argA[" + i + "]:: " + argA[i]);
            }
            argACopy = (InvocationConstraint[]) c.toArray(new
                    InvocationConstraint[0]);
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception has been thrown while preparing arguments for"
                    + " ConstraintAlternatives.create() methods: " + e);
            return false;
        }
        logger.log(Level.FINE, "Expected InvocationConstraint:: " + ic);
        
        // Testing ConstraintAlternatives.create(Collection) method
        logger.log(Level.FINE,
                "+++ Testing ConstraintAlternatives.create(Collection) ...");

        try {
            InvocationConstraint res = ConstraintAlternatives.create(argC);
            logger.log(Level.FINE, "Returned InvocationConstraint:: " + res);
            
            // Compare with the expected InvocationConstraint object
            if (!res.equals(ic)) {
                logger.log(Level.FINE,
                        "Returned InvocationConstraint object"
                        + " isn't equal to the expected one!");
                return false;
            }

            // Verify that the argument hasn't been modified
            if (!argC.equals(c)) {
                logger.log(Level.FINE, "The argument has been modified!");
                return false;
            }

            // Verify that the argument isn't retained, i.e. subsequent changes
            // to the argument have no effect on the instance created
            argC.clear();

            if (!res.equals(ic)) {
                logger.log(Level.FINE,
                        "The argument is retained, i.e. subsequent changes to"
                        + " the argument have an effect on the instance"
                        + " created");
                return false;
            }
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception has been thrown while invoking"
                    + " ConstraintAlternatives.create(Collection) method: "
                    + e);
            return false;
        }

        // Testing ConstraintAlternatives.create(InvocationConstraint[]) method
        logger.log(Level.FINE,
                "+++ Testing"
                + " ConstraintAlternatives.create(InvocationConstraint[]) ...");

        try {
            InvocationConstraint res = ConstraintAlternatives.create(argA);
            logger.log(Level.FINE, "Returned InvocationConstraint:: " + res);
            
            // Compare with the expected InvocationConstraint object
            if (!res.equals(ic)) {
                logger.log(Level.FINE,
                        "Returned InvocationConstraint object"
                        + " isn't equal to the expected one!");
                return false;
            }

            // Verify that the argument hasn't been modified
            if (!Arrays.equals(argA, argACopy)) {
                logger.log(Level.FINE, "The argument has been modified!");
                return false;
            }

            // Verify that the argument isn't retained, i.e. subsequent changes
            // to the argument have no effect on the instance created
            Arrays.fill(argA, null);

            if (!res.equals(ic)) {
                logger.log(Level.FINE,
                        "The argument is retained, i.e. subsequent changes to"
                        + " the argument have an effect on the instance"
                        + " created");
                return false;
            }
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception has been thrown while invoking"
                    + " ConstraintAlternatives.create(Collection) method: "
                    + e);
            return false;
        }
        return true;
    }

    /**
     * Verifies
     * {@link net.jini.core.constraint.ConstraintAlternatives#create(Collection)}
     * method when exception is expected.
     * It's verified that ConstraintAlternatives.create() method invoked with
     * the argument specified by the first parameter c throws an exception
     * type of which is equal to the type specified by the second parameter cl.
     *
     * @param c  argument for the ConstraintAlternatives.create() method
     * @param cl the type of exception that should be thrown by
     *           ConstraintAlternatives.create() method
     * @return true if ConstraintAlternatives.create() method throws exception
     *         type of which is equal to the expected one or false otherwise
     */
    public boolean checkException(Collection c, Class cl) {
        // Testing ConstraintAlternatives.create(Collection) method
        logger.log(Level.FINE,
                "+++ Testing ConstraintAlternatives.create(Collection) ...");
        logger.log(Level.FINE, "Collection::\n" + c);
        logger.log(Level.FINE, "expected type of Exception:: " + cl);

        try {
            ConstraintAlternatives.create(c);
            logger.log(Level.FINE,
                    "No Exceptions while"
                    + " ConstraintAlternatives.create(Collection)");
            return false;
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception has been thrown while invoking"
                    + " ConstraintAlternatives.create(Collection) method: "
                    + e);
            Class retExceptionClass = e.getClass();

            if (!retExceptionClass.equals(cl)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Verifies
     * {@link net.jini.core.constraint.ConstraintAlternatives#create(InvocationConstraint[])}
     * method when exception is expected.
     * It's verified that ConstraintAlternatives.create() method invoked with
     * the argument specified by the first parameter c throws an exception
     * type of which is equal to the type specified by the second parameter cl.
     *
     * @param arr  argument for the ConstraintAlternatives.create() method
     * @param cl the type of exception that should be thrown by
     *           ConstraintAlternatives.create() method
     * @return true if ConstraintAlternatives.create() method throws exception
     *         type of which is equal to the expected one or false otherwise
     */
    public boolean checkException(InvocationConstraint[] arr, Class cl) {
        // Testing ConstraintAlternatives.create(InvocationConstraint[]) method
        logger.log(Level.FINE,
                "+++ Testing"
                + " ConstraintAlternatives.create(InvocationConstraint[]) ...");
        logger.log(Level.FINE, "Array::");

        if (arr == null) {
            logger.log(Level.FINE, "null");
        } else {
            for (int i = 0; i < arr.length; i++) {
                logger.log(Level.FINE, "arr[" + i + "]:: " + arr[i]);
            }
        }
        logger.log(Level.FINE, "Expected type of Exception:: " + cl);

        try {
            ConstraintAlternatives.create(arr);
            logger.log(Level.FINE,
                    "No Exceptions while"
                    + " ConstraintAlternatives.create(InvocationConstraint[])");
            return false;
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception has been thrown while invoking"
                    + " ConstraintAlternatives.create(InvocationConstraint[])"
                    + " method: " + e);
            Class retExceptionClass = e.getClass();

            if (!retExceptionClass.equals(cl)) {
                return false;
            }
        }
        return true;
    }
}
