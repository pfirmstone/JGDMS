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
package org.apache.river.test.spec.constraint.coreconstraint.util;

import java.util.logging.Level;

// org.apache.river.qa
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;

// org.apache.river.qa.harness
import org.apache.river.qa.harness.QAConfig; // base class for QAConfig
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;

// java.util
import java.util.logging.Level;
import java.util.HashSet;

// javax.security
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.x500.X500Principal;

// Davis packages
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.ClientMaxPrincipal;
import net.jini.core.constraint.ClientMaxPrincipalType;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.ClientMinPrincipalType;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.ConnectionAbsoluteTime;
import net.jini.core.constraint.ConnectionRelativeTime;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.DelegationAbsoluteTime;
import net.jini.core.constraint.DelegationRelativeTime;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ServerMinPrincipal;


/**
 * <pre>
 *
 * This is an auxiliary abstract class.
 *
 * This is an auxiliary abstract class to test methods of the following classes:
 *   TestCase #1:  {@link net.jini.core.constraint.ClientAuthentication}
 *   TestCase #2:  {@link net.jini.core.constraint.ClientMaxPrincipal}
 *   TestCase #3:  {@link net.jini.core.constraint.ClientMaxPrincipalType}
 *   TestCase #4:  {@link net.jini.core.constraint.ClientMinPrincipal}
 *   TestCase #5:  {@link net.jini.core.constraint.ClientMinPrincipalType}
 *   TestCase #6:  {@link net.jini.core.constraint.Confidentiality}
 *   TestCase #7:  {@link net.jini.core.constraint.ConnectionAbsoluteTime}
 *   TestCase #8:  {@link net.jini.core.constraint.ConnectionRelativeTime}
 *   TestCase #9:  {@link net.jini.core.constraint.ConstraintAlternatives}
 *   TestCase #10: {@link net.jini.core.constraint.Delegation}
 *   TestCase #11: {@link net.jini.core.constraint.DelegationAbsoluteTime}
 *   TestCase #12: {@link net.jini.core.constraint.DelegationRelativeTime}
 *   TestCase #13: {@link net.jini.core.constraint.Integrity}
 *   TestCase #14: {@link net.jini.core.constraint.InvocationConstraints}
 *   TestCase #15: {@link net.jini.core.constraint.ServerAuthentication}
 *   TestCase #16: {@link net.jini.core.constraint.ServerMinPrincipal}
 *
 * In each Test Case 3 objects of the corresponding type are created:
 *   - 2 equal objects (obj1.equals(obj2) produces true);
 *   - object that is different from obj1 and obj2 (obj1.equals(obj3)
 *     produces false).
 * Then in each test case {@link #checker(Object, Object, Object)} method
 * is invoked. Test passes if {@link #checker(Object, Object, Object)} method
 * returns true in each case.
 *
 * This class is extended by the following tests:
 *   {@link org.apache.river.test.spec.constraint.coreconstraint.ToStringTest}
 *   {@link org.apache.river.test.spec.constraint.coreconstraint.EqualsTest}
 *   {@link org.apache.river.test.spec.constraint.coreconstraint.HashCodeTest}
 * Each of these tests implements {@link #checker(Object, Object, Object)}
 * method according to the assertion to be tested in the test.
 *
 * </pre>
 */
abstract public class AbstractSimpleTest extends QATestEnvironment implements Test {
    QAConfig config;

    /**
     * This method performs all actions mentioned in the class description.
     */
    public void run() throws Exception {
        config = getConfig();

        /*
         * TestCase #1: net.jini.core.constraint.ClientAuthentication objects
         */
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #1:"
                + "  net.jini.core.constraint.ClientAuthentication");
        {
            ClientAuthentication obj1 = ClientAuthentication.YES;
            ClientAuthentication obj2 = ClientAuthentication.YES;
            ClientAuthentication obj3 = ClientAuthentication.NO;

            if (!checker(obj1, obj2, obj3)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        /*
         * TestCase #2: net.jini.core.constraint.ClientMaxPrincipal objects
         */
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #2:"
                + "  net.jini.core.constraint.ClientMaxPrincipal");

        try {
            HashSet set = new HashSet();
            set.add(new KerberosPrincipal("duke@FOO.COM"));
            ClientMaxPrincipal obj1 = new ClientMaxPrincipal(set);
            ClientMaxPrincipal obj2 = new ClientMaxPrincipal(set);
            set.add(new
                    X500Principal(
                    "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US"));
            ClientMaxPrincipal obj3 = new ClientMaxPrincipal(set);

            if (!checker(obj1, obj2, obj3)) {
                throw new TestException(
                        "" + " test failed");
            }
        } catch (Exception e) {
            logger.log(Level.FINE, e + " has been thrown while creating"
                    + " ClientMaxPrincipal objects");
            throw new TestException(
                    "" + " test failed");
        }

        /*
         * TestCase #3: net.jini.core.constraint.ClientMaxPrincipalType objects
         */
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #3:"
                + "  net.jini.core.constraint.ClientMaxPrincipalType");

        try {
            ClientMaxPrincipalType obj1 = new
                    ClientMaxPrincipalType(KerberosPrincipal.class);
            ClientMaxPrincipalType obj2 = new
                    ClientMaxPrincipalType(KerberosPrincipal.class);
            ClientMaxPrincipalType obj3 = new
                    ClientMaxPrincipalType(X500Principal.class);

            if (!checker(obj1, obj2, obj3)) {
                throw new TestException(
                        "" + " test failed");
            }
        } catch (Exception e) {
            logger.log(Level.FINE, e + " has been thrown while creating"
                    + " ClientMaxPrincipalType objects");
            throw new TestException(
                    "" + " test failed");
        }

        /*
         * TestCase #4: net.jini.core.constraint.ClientMinPrincipal objects
         */
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #4:"
                + "  net.jini.core.constraint.ClientMinPrincipal");

        try {
            HashSet set = new HashSet();
            set.add(new KerberosPrincipal("duke@FOO.COM"));
            ClientMinPrincipal obj1 = new ClientMinPrincipal(set);
            ClientMinPrincipal obj2 = new ClientMinPrincipal(set);
            set.add(new
                    X500Principal(
                    "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US"));
            ClientMinPrincipal obj3 = new ClientMinPrincipal(set);

            if (!checker(obj1, obj2, obj3)) {
                throw new TestException(
                        "" + " test failed");
            }
        } catch (Exception e) {
            logger.log(Level.FINE, e + " has been thrown while creating"
                    + " ClientMinPrincipal objects");
            throw new TestException(
                    "" + " test failed");
        }

        /*
         * TestCase #5: net.jini.core.constraint.ClientMinPrincipalType objects
         */
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #5:"
                + "  net.jini.core.constraint.ClientMinPrincipalType");

        try {
            ClientMinPrincipalType obj1 = new
                    ClientMinPrincipalType(KerberosPrincipal.class);
            ClientMinPrincipalType obj2 = new
                    ClientMinPrincipalType(KerberosPrincipal.class);
            ClientMinPrincipalType obj3 = new
                    ClientMinPrincipalType(X500Principal.class);

            if (!checker(obj1, obj2, obj3)) {
                throw new TestException(
                        "" + " test failed");
            }
        } catch (Exception e) {
            logger.log(Level.FINE, e + " has been thrown while creating"
                    + " ClientMinPrincipalType objects");
            throw new TestException(
                    "" + " test failed");
        }

        /*
         * TestCase #6: net.jini.core.constraint.Confidentiality objects
         */
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #6:"
                + "  net.jini.core.constraint.Confidentiality");
        {
            Confidentiality obj1 = Confidentiality.YES;
            Confidentiality obj2 = Confidentiality.YES;
            Confidentiality obj3 = Confidentiality.NO;

            if (!checker(obj1, obj2, obj3)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        /*
         * TestCase #7: net.jini.core.constraint.ConnectionAbsoluteTime objects
         */
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #7:"
                + "  net.jini.core.constraint.ConnectionAbsoluteTime");
        {
            ConnectionAbsoluteTime obj1 = new
                    ConnectionAbsoluteTime(Long.MAX_VALUE);
            ConnectionAbsoluteTime obj2 = new
                    ConnectionAbsoluteTime(Long.MAX_VALUE);
            ConnectionAbsoluteTime obj3 = new ConnectionAbsoluteTime((long) 0);

            if (!checker(obj1, obj2, obj3)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        /*
         * TestCase #8: net.jini.core.constraint.ConnectionRelativeTime objects
         */
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #8:"
                + "  net.jini.core.constraint.ConnectionRelativeTime");

        try {
            ConnectionRelativeTime obj1 = new
                    ConnectionRelativeTime(Long.MAX_VALUE);
            ConnectionRelativeTime obj2 = new
                    ConnectionRelativeTime(Long.MAX_VALUE);
            ConnectionRelativeTime obj3 = new ConnectionRelativeTime((long) 0);

            if (!checker(obj1, obj2, obj3)) {
                throw new TestException(
                        "" + " test failed");
            }
        } catch (Exception e) {
            logger.log(Level.FINE, e + " has been thrown while creating"
                    + " ConnectionRelativeTime objects");
            throw new TestException(
                    "" + " test failed");
        }

        /*
         * TestCase #9: net.jini.core.constraint.ConstraintAlternatives objects
         */
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #9:"
                + "  net.jini.core.constraint.ConstraintAlternatives");

        try {
            HashSet set = new HashSet();
            set.add(ClientAuthentication.YES);
            set.add(ClientAuthentication.NO);
            ConstraintAlternatives obj1 = new ConstraintAlternatives(set);
            ConstraintAlternatives obj2 = new ConstraintAlternatives(set);
            set.add(Confidentiality.YES);
            ConstraintAlternatives obj3 = new ConstraintAlternatives(set);

            if (!checker(obj1, obj2, obj3)) {
                throw new TestException(
                        "" + " test failed");
            }
        } catch (Exception e) {
            logger.log(Level.FINE, e + " has been thrown while creating"
                    + " ConstraintAlternatives objects");
            throw new TestException(
                    "" + " test failed");
        }

        /*
         * TestCase #10: net.jini.core.constraint.Delegation objects
         */
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #10:"
                + "  net.jini.core.constraint.Delegation");
        {
            Delegation obj1 = Delegation.YES;
            Delegation obj2 = Delegation.YES;
            Delegation obj3 = Delegation.NO;

            if (!checker(obj1, obj2, obj3)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        /*
         * TestCase #11: net.jini.core.constraint.DelegationAbsoluteTime objects
         */
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #11:"
                + "  net.jini.core.constraint.DelegationAbsoluteTime");
        {
            DelegationAbsoluteTime obj1 = new
                    DelegationAbsoluteTime(Long.MAX_VALUE, Long.MAX_VALUE,
                    Long.MAX_VALUE, Long.MAX_VALUE);
            DelegationAbsoluteTime obj2 = new
                    DelegationAbsoluteTime(Long.MAX_VALUE, Long.MAX_VALUE,
                    Long.MAX_VALUE, Long.MAX_VALUE);
            DelegationAbsoluteTime obj3 = new DelegationAbsoluteTime((long) 0,
                    (long) 0, (long) 0, (long) 0);

            if (!checker(obj1, obj2, obj3)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        /*
         * TestCase #12: net.jini.core.constraint.DelegationRelativeTime objects
         */
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #12:"
                + "  net.jini.core.constraint.DelegationRelativeTime");

        try {
            DelegationRelativeTime obj1 = new
                    DelegationRelativeTime(Long.MAX_VALUE, Long.MAX_VALUE,
                    Long.MAX_VALUE, Long.MAX_VALUE);
            DelegationRelativeTime obj2 = new
                    DelegationRelativeTime(Long.MAX_VALUE, Long.MAX_VALUE,
                    Long.MAX_VALUE, Long.MAX_VALUE);
            DelegationRelativeTime obj3 = new DelegationRelativeTime((long) 0,
                    (long) 0, (long) 0, (long) 0);

            if (!checker(obj1, obj2, obj3)) {
                throw new TestException(
                        "" + " test failed");
            }
        } catch (Exception e) {
            logger.log(Level.FINE, e + " has been thrown while creating"
                    + " DelegationRelativeTime objects");
            throw new TestException(
                    "" + " test failed");
        }

        /*
         * TestCase #13: net.jini.core.constraint.Integrity objects
         */
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #13:"
                + "  net.jini.core.constraint.Integrity");
        {
            Integrity obj1 = Integrity.YES;
            Integrity obj2 = Integrity.YES;
            Integrity obj3 = Integrity.NO;

            if (!checker(obj1, obj2, obj3)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        /*
         * TestCase #14: net.jini.core.constraint.InvocationConstraints objects
         */
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #14:"
                + "  net.jini.core.constraint.InvocationConstraints");

        try {
            HashSet reqs = new HashSet();
            HashSet prefs = new HashSet();
            reqs.add(ClientAuthentication.YES);
            reqs.add(ClientAuthentication.NO);
            prefs.add(ServerAuthentication.YES);
            prefs.add(ServerAuthentication.NO);
            InvocationConstraints obj1 = new InvocationConstraints(reqs, prefs);
            InvocationConstraints obj2 = new InvocationConstraints(reqs, prefs);
            reqs.add(Confidentiality.YES);
            prefs.add(Confidentiality.NO);
            InvocationConstraints obj3 = new InvocationConstraints(reqs, prefs);

            if (!checker(obj1, obj2, obj3)) {
                throw new TestException(
                        "" + " test failed");
            }
        } catch (Exception e) {
            logger.log(Level.FINE, e + " has been thrown while creating"
                    + " InvocationConstraints objects");
            throw new TestException(
                    "" + " test failed");
        }

        /*
         * TestCase #15: net.jini.core.constraint.ServerAuthentication objects
         */
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #15:"
                + "  net.jini.core.constraint.ServerAuthentication");
        {
            ServerAuthentication obj1 = ServerAuthentication.YES;
            ServerAuthentication obj2 = ServerAuthentication.YES;
            ServerAuthentication obj3 = ServerAuthentication.NO;

            if (!checker(obj1, obj2, obj3)) {
                throw new TestException(
                        "" + " test failed");
            }
        }

        /*
         * TestCase #16: net.jini.core.constraint.ServerMinPrincipal objects
         */
        logger.log(Level.FINE,
                "\n\t+++++ TestCase #16:"
                + "  net.jini.core.constraint.ServerMinPrincipal");

        try {
            HashSet set = new HashSet();
            set.add(new KerberosPrincipal("duke@FOO.COM"));
            ServerMinPrincipal obj1 = new ServerMinPrincipal(set);
            ServerMinPrincipal obj2 = new ServerMinPrincipal(set);
            set.add(new
                    X500Principal(
                    "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US"));
            ServerMinPrincipal obj3 = new ServerMinPrincipal(set);

            if (!checker(obj1, obj2, obj3)) {
                throw new TestException(
                        "" + " test failed");
            }
        } catch (Exception e) {
            logger.log(Level.FINE, e + " has been thrown while creating"
                    + " ServerMinPrincipal objects");
            throw new TestException(
                    "" + " test failed");
        }
        return;
    }

    /**
     * Abstract method to test objects of the same type.
     * Subclasses override this method to implement specific tests.
     * This method returns true if all conditions/checks of the specific test
     * are satisfied or false otherwise.
     *
     * @param primObj  the primary object
     * @param equalObj object that is equal to the primary object
     * @param diffObj  object that isn't equal to the primary object
     * @return true if all conditions of the specific test are satisfied or
     *         false otherwise
     */
    abstract public boolean checker(Object primObj, Object equalObj,
            Object diffObj);
}
