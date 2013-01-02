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
package com.sun.jini.test.spec.security.authenticationpermission;

import java.util.logging.Level;

// java
import java.security.Permission;

// net.jini
import net.jini.security.AuthenticationPermission;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.Test;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     Two instances of this class are equal if each implies the other;
 *     that is, both instances have the same actions, every principal that
 *     matches the local principals of one instance matches the local
 *     principals of the other instance, and (if the instances have any
 *     action besides listen) every principal that matches the
 *     peer principals of one instance matches the peer principals of the
 *     other instance.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     TestPermission - permission which does not extend
 *             AuthenticationPermission
 *
 * Action
 *   The test performs the following steps:
 *     1) construct AuthenticationPermission0 with "abc \"abc\"" target name
 *        and "accept, delegate" actions
 *     2) call 'equals' method of constructed AuthenticationPermission0 with
 *        null as a parameter
 *     3) assert that false will be returned
 *     4) call 'equals' method of constructed AuthenticationPermission0 with
 *        TestPermission as a parameter
 *     5) assert that false will be returned
 *     6) construct AuthenticationPermission1 with "abc \"abc\"" target name
 *        and "accept, connect" actions
 *     7) call 'equals' method of constructed AuthenticationPermission0 with
 *        AuthenticationPermission1 as a parameter
 *     8) assert that false will be returned
 *     9) construct AuthenticationPermission2 with "abc \"abc\"" target name
 *        and "accept, delegate" actions
 *     10) call 'equals' method of constructed AuthenticationPermission0 with
 *         AuthenticationPermission2 as a parameter
 *     11) assert that true will be returned
 *     12) construct AuthenticationPermission3 with "abc \"abc\"" target name
 *         and "listen, connect, accept, delegate" actions
 *     13) call 'equals' method of constructed AuthenticationPermission0 with
 *         AuthenticationPermission3 as a parameter
 *     14) assert that true will be returned
 *
 *     15) construct AuthenticationPermission4 with "abc \"abc\"" target name
 *         and "listen" actions
 *     16) construct AuthenticationPermission-s with "abc \"abc\"" target name
 *         and the following actions:
 *         "accept", "listen, connect", "connect"
 *     17) call 'equals' method of constructed AuthenticationPermission4 with
 *         each of AuthenticationPermission-s as a parameter
 *     18) assert that false will be returned
 *
 *     19) construct AuthenticationPermission5 with "* \"*\"" target name
 *         and "connect" actions
 *     20) construct AuthenticationPermission-s with the following target names
 *         and "connect" actions:
 *         abc "abc",
 *         abc "*",
 *         abc "abc" def "def" ghi "*"
 *         * "*" peer abc "abc"
 *     21) call 'equals' method of constructed AuthenticationPermission5 with
 *         each of AuthenticationPermission-s as a parameter
 *     22) assert that false will be returned
 *
 *     23) construct AuthenticationPermission6 with "abc \"*\"" target name
 *         and "listen" actions
 *     24) construct AuthenticationPermission-s with the following target names
 *         and "listen" actions:
 *         abc "*",
 *         abc "*" abc "def"
 *         abc "*" peer def "def"
 *     25) call 'equals' method of constructed AuthenticationPermission6 with
 *         each of AuthenticationPermission-s as a parameter
 *     26) assert that true will be returned
 *     27) construct AuthenticationPermission-s with the following target names
 *         and "listen" actions:
 *         def "*",
 *         * "*",
 *         abc "abc",
 *         abc "*" def "*"
 *     28) call 'equals' method of constructed AuthenticationPermission6 with
 *         each of AuthenticationPermission-s as a parameter
 *     29) assert that false will be returned
 *
 *     30) construct AuthenticationPermission7 with "abc \"def\"" target name
 *         and "listen" actions
 *     34) construct AuthenticationPermission-s with the following target names
 *         and "listen" actions:
 *         abc "def",
 *         abc "def" peer ghi "ghi"
 *     35) call 'equals' method of constructed AuthenticationPermission7 with
 *         each of AuthenticationPermission-s as a parameter
 *     36) assert that true will be returned
 *     37) construct AuthenticationPermission-s with the following target names
 *         and "listen" actions:
 *         def "def",
 *         abc "ghi",
 *         abc "*",
 *         * "*",
 *         abc "def" abc "abc",
 *         abc "def" def "def"
 *     38) call 'equals' method of constructed AuthenticationPermission7 with
 *         each of AuthenticationPermission-s as a parameter
 *     39) assert that false will be returned
 *
 *     40) construct AuthenticationPermission8 with
 *         "abc \"def\" peer def \"ghi\"" target name and "connect" actions
 *     41) construct AuthenticationPermission-s with the following target names
 *         and "connect" actions:
 *         abc "def" peer def "ghi" def "jkl",
 *         abc "def" peer def "jkl",
 *         abc "def" peer ghi "ghi",
 *         abc "def"
 *     42) call 'equals' method of constructed AuthenticationPermission8 with
 *         each of AuthenticationPermission-s as a parameter
 *     43) assert that false will be returned
 *     44) construct AuthenticationPermission9 with
 *         "abc \"def\" peer def \"ghi\"" target name and "connect" actions
 *     45) call 'equals' method of constructed AuthenticationPermission8 with
 *         AuthenticationPermission9 as a parameter
 *     46) assert that true will be returned
 *     47) call 'equals' method of constructed AuthenticationPermission8 with
 *         the same AuthenticationPermission8 as a parameter
 *     48) assert that true will be returned
 * </pre>
 */
public class EqualsTest extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        AuthenticationPermission ap = createAP("abc \"abc\"",
                "accept, delegate");
        boolean res = equals(ap, null);

        if (res) {
            // FAIL
            throw new TestException(
                    "Performed 'equals' method call returned true while "
                    + "false was expected.");
        } else {
            // PASS
            logger.fine("Performed 'equals' method call returned false "
                    + "as expected.");
        }
        res = equals(ap, new RuntimePermission("getClassLoader"));

        if (res) {
            // FAIL
            throw new TestException(
                    "Performed 'equals' method call returned true while "
                    + "false was expected.");
        } else {
            // PASS
            logger.fine("Performed 'equals' method returned false "
                    + "as expected.");
        }
        AuthenticationPermission[] testPerms =
            new AuthenticationPermission[] {
                createAP("abc \"abc\"", "accept, delegate"),
                createAP("abc \"abc\"", "listen"),
                createAP("* \"*\"", "connect"),
                createAP("abc \"*\"", "listen"),
                createAP("abc \"def\"", "listen"),
                createAP("abc \"def\" peer def \"ghi\"", "connect") };
        AuthenticationPermission[][] notEqualPerms =
            new AuthenticationPermission[][] {
                new AuthenticationPermission[] {
                    createAP("abc \"abc\"", "accept, connect") },
                new AuthenticationPermission[] {
                    createAP("abc \"abc\"", "accept"),
                    createAP("abc \"abc\"", "listen, connect"),
                    createAP("abc \"abc\"", "connect") },
                new AuthenticationPermission[] {
                    createAP("abc \"abc\"", "connect"),
                    createAP("abc \"*\"", "connect"),
                    createAP("abc \"abc\" def \"def\" ghi \"*\"", "connect"),
                    createAP("* \"*\" peer abc \"abc\"", "connect") },
                new AuthenticationPermission[] {
                    createAP("def \"*\"", "listen"),
                    createAP("* \"*\"", "listen"),
                    createAP("abc \"abc\"", "listen"),
                    createAP("abc \"*\" def \"*\"", "listen") },
                new AuthenticationPermission[] {
                    createAP("def \"def\"", "listen"),
                    createAP("abc \"ghi\"", "listen"),
                    createAP("abc \"*\"", "listen"),
                    createAP("* \"*\"", "listen"),
                    createAP("abc \"def\" abc \"abc\"", "listen"),
                    createAP("abc \"def\" def \"def\"", "listen") },
                new AuthenticationPermission[] {
                    createAP("abc \"def\" peer def \"ghi\" def \"jkl\"",
                            "connect"),
                    createAP("abc \"def\" peer def \"jkl\"", "connect"),
                    createAP("abc \"def\" peer ghi \"ghi\"", "connect"),
                    createAP("abc \"def\"", "connect") } };
        AuthenticationPermission[][] equalPerms =
            new AuthenticationPermission[][] {
                new AuthenticationPermission[] {
                    createAP("abc \"abc\"",
                            "listen, connect, accept, delegate") },
                new AuthenticationPermission[] { null },
                new AuthenticationPermission[] { null },
                new AuthenticationPermission[] {
                    createAP("abc \"*\"", "listen"),
                    createAP("abc \"*\" abc \"def\"", "listen"),
                    createAP("abc \"*\" peer def \"def\"", "listen") },
                new AuthenticationPermission[] {
                    createAP("abc \"def\"", "listen"),
                    createAP("abc \"def\" peer ghi \"ghi\"", "listen") },
                new AuthenticationPermission[] { null } };

        for (int i = 0; i < testPerms.length; ++i) {
            for (int j = 0; j < notEqualPerms[i].length; ++j) {
                if (notEqualPerms[i][j] == null) {
                    continue;
                }
                res = equals(testPerms[i], notEqualPerms[i][j]);

                if (res) {
                    // FAIL
                    throw new TestException(
                            "Performed 'equals' method call returned true "
                            + "while false was expected.");
                } else {
                    // PASS
                    logger.fine("Performed 'equals' method call returned "
                            + "false as expected.");
                }
            }

            for (int j = 0; j < equalPerms[i].length; ++j) {
                if (equalPerms[i][j] == null) {
                    continue;
                }
                res = equals(testPerms[i], equalPerms[i][j]);

                if (!res) {
                    // FAIL
                    throw new TestException(
                            "Performed 'equals' method call returned false "
                            + "while true was expected.");
                } else {
                    // PASS
                    logger.fine("Performed 'equals' method call returned "
                            + "true as expected.");
                }
            }
            res = equals(testPerms[i], createAP(testPerms[i].getName(),
                    testPerms[i].getActions()));

            if (!res) {
                // FAIL
                throw new TestException(
                        "Performed 'equals' method call returned false "
                        + "while true was expected.");
            } else {
                // PASS
                logger.fine("Performed 'equals' method call returned true "
                        + "as expected.");
            }
            res = equals(testPerms[i], testPerms[i]);

            if (!res) {
                // FAIL
                throw new TestException(
                        "Performed 'equals' method call returned false "
                        + "while true was expected.");
            } else {
                // PASS
                logger.fine("Performed 'equals' method call returned true "
                        + "as expected.");
            }
        }
    }

    /**
     * Logs parameters specified and calls 'equals' method of
     * AuthenticationPermission specified.
     *
     * @param ap AuthenticationPermission whose 'equals' method will be called
     * @param param Permission which will be a parameter to 'equals'
     *        method call
     * @return result of 'equals' method call
     */
    public boolean equals(AuthenticationPermission ap, Permission param) {
        if (ap != param) {
            logger.fine("Call 'equals' method of " + ap + " with " + param
                    + " parameter.");
        } else {
            logger.fine("Call 'equals' method of " + ap
                    + " with itself as a parameter.");
        }
        return ap.equals(param);
    }

    /**
     * Creates AuthenticationPermission with parameters specified.
     *
     * @param name 1-st parameter for AuthenticationPermission constructor
     * @param actions 2-nd parameter for AuthenticationPermission constructor
     * @return created AuthenticationPermission instance
     * @throws rethrow any exception thrown by constructor
     */
    public AuthenticationPermission createAP(String name, String actions) {
        return new AuthenticationPermission(name, actions);
    }
}
