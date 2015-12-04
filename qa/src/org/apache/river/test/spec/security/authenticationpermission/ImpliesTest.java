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
package org.apache.river.test.spec.security.authenticationpermission;

import java.util.logging.Level;

// java
import java.security.Permission;

// net.jini
import net.jini.security.AuthenticationPermission;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     'implies' method of AuthenticationPermission returns true if the
 *     specified permission is an instance of AuthenticationPermission, and
 *     every action included in the specified permission is included as an
 *     action of this permission, and every principal that matches the local
 *     principals of the specified permission also matches the local principals
 *     of this permission, and (if the specified permission has any action
 *     besides listen) every principal that matches the peer principals of this
 *     permission also matches the peer principals of the specified permission;
 *     returns false otherwise.
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
 *     2) call 'implies' method of constructed AuthenticationPermission0 with
 *        null as a parameter
 *     3) assert that false will be returned
 *     4) call 'implies' method of constructed AuthenticationPermission0 with
 *        TestPermission as a parameter
 *     5) assert that false will be returned
 *     6) construct AuthenticationPermission1 with "abc \"abc\"" target name
 *        and "listen, connect" actions
 *     7) call 'implies' method of constructed AuthenticationPermission0 with
 *        AuthenticationPermission1 as a parameter
 *     8) assert that true will be returned
 *     9) construct AuthenticationPermission2 with "abc \"abc\"" target name
 *        and "listen, connect, accept, delegate" actions
 *     10) call 'implies' method of constructed AuthenticationPermission0 with
 *         AuthenticationPermission2 as a parameter
 *     11) assert that true will be returned
 *
 *     12) construct AuthenticationPermission3 with "abc \"abc\"" target name
 *         and "listen" actions
 *     13) construct AuthenticationPermission-s with "abc \"abc\"" target name
 *         and the following actions:
 *         "accept", "listen, connect", "connect"
 *     14) call 'implies' method of constructed AuthenticationPermission3 with
 *         each of AuthenticationPermission-s as a parameter
 *     15) assert that false will be returned
 *     16) construct AuthenticationPermission4 with "abc \"abc\"" target name
 *         and "listen" actions
 *     17) call 'implies' method of constructed AuthenticationPermission3 with
 *         AuthenticationPermission4 as a parameter
 *     18) assert that true will be returned
 *
 *     19) construct AuthenticationPermission5 with "* \"*\"" target name
 *         and "connect" actions
 *     20) construct AuthenticationPermission-s with the following target names
 *         and "connect" actions:
 *         abc "abc",
 *         abc "*",
 *         * "*",
 *         abc "abc" def "def" ghi "*"
 *     21) call 'implies' method of constructed AuthenticationPermission5 with
 *         each of AuthenticationPermission-s as a parameter
 *     22) assert that true will be returned
 *     23) construct AuthenticationPermission6 with "* \"*\" peer abc \"abc\""
 *         target name and "connect" actions
 *     24) call 'implies' method of constructed AuthenticationPermission5 with
 *         AuthenticationPermission6 as a parameter
 *     25) assert that true will be returned
 *
 *     26) construct AuthenticationPermission7 with "abc \"*\"" target name
 *         and "listen" actions
 *     27) construct AuthenticationPermission-s with the following target names
 *         and "listen" actions:
 *         abc "abc",
 *         abc "*",
 *         abc "abc" abc "def"
 *         abc "abc" peer def "def"
 *     28) call 'implies' method of constructed AuthenticationPermission7 with
 *         each of AuthenticationPermission-s as a parameter
 *     29) assert that true will be returned
 *     30) construct AuthenticationPermission-s with the following target names
 *         and "listen" actions:
 *         def "*",
 *         * "*",
 *         abc "abc" def "abc"
 *     31) call 'implies' method of constructed AuthenticationPermission7 with
 *         each of AuthenticationPermission-s as a parameter
 *     32) assert that false will be returned
 *
 *     33) construct AuthenticationPermission8 with "abc \"def\"" target name
 *         and "listen" actions
 *     34) construct AuthenticationPermission-s with the following target names
 *         and "listen" actions:
 *         abc "def",
 *         abc "def" peer ghi "ghi"
 *     35) call 'implies' method of constructed AuthenticationPermission8 with
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
 *     38) call 'implies' method of constructed AuthenticationPermission8 with
 *         each of AuthenticationPermission-s as a parameter
 *     39) assert that false will be returned
 *
 *     40) construct AuthenticationPermission9 with
 *         "abc \"def\" peer def \"ghi\"" target name and "listen" actions
 *     41) construct AuthenticationPermission-s with the following target names
 *         and "listen" actions:
 *         abc "def" peer abc "def",
 *         abc "def",
 *         abc "def" abc "def",
 *     42) call 'implies' method of constructed AuthenticationPermission9 with
 *         each of AuthenticationPermission-s as a parameter
 *     43) assert that true will be returned
 *
 *     44) construct AuthenticationPermission10 with
 *         "abc \"def\" peer def \"ghi\"" target name and "connect" actions
 *     45) construct AuthenticationPermission-s with the following target names
 *         and "connect" actions:
 *         abc "def" peer def "jkl",
 *         abc "def" peer ghi "ghi",
 *         abc "def"
 *     46) call 'implies' method of constructed AuthenticationPermission10 with
 *         each of AuthenticationPermission-s as a parameter
 *     47) assert that false will be returned
 *     48) construct AuthenticationPermission11 with the followint target names
 *         target name and "connect" actions:
 *         abc "def" peer def "ghi",
 *         abc "def" peer def "ghi" def "jkl"
 *     49) call 'implies' method of constructed AuthenticationPermission10 with
 *         AuthenticationPermission11 as a parameter
 *     50) assert that true will be returned
 *     51) call 'implies' method of constructed AuthenticationPermission10 with
 *         the same AuthenticationPermission10 as a parameter
 *     50) assert that true will be returned
 * </pre>
 */
public class ImpliesTest extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        AuthenticationPermission ap = createAP("abc \"abc\"",
                "accept, delegate");
        boolean res = implies(ap, null);

        if (res) {
            // FAIL
            throw new TestException(
                    "Performed 'implies' method call returned true while "
                    + "false was expected.");
        } else {
            // PASS
            logger.fine("Performed 'implies' method call returned false "
                    + "as expected.");
        }
        res = implies(ap, new RuntimePermission("getClassLoader"));

        if (res) {
            // FAIL
            throw new TestException(
                    "Performed 'implies' method call returned true while "
                    + "false was expected.");
        } else {
            // PASS
            logger.fine("Performed 'implies' method returned false "
                    + "as expected.");
        }
        AuthenticationPermission[] testPerms =
            new AuthenticationPermission[] {
                createAP("abc \"abc\"", "accept, delegate"),
                createAP("abc \"abc\"", "listen"),
                createAP("* \"*\"", "connect"),
                createAP("abc \"*\"", "listen"),
                createAP("abc \"def\"", "listen"),
                createAP("abc \"def\" peer def \"ghi\"", "listen"),
                createAP("abc \"def\" peer def \"ghi\"", "connect") };
        AuthenticationPermission[][] notImpliedPerms =
            new AuthenticationPermission[][] {
                new AuthenticationPermission[] { null },
                new AuthenticationPermission[] {
                    createAP("abc \"abc\"", "accept"),
                    createAP("abc \"abc\"", "listen, connect"),
                    createAP("abc \"abc\"", "connect") },
                new AuthenticationPermission[] { null },
                new AuthenticationPermission[] {
                    createAP("def \"*\"", "listen"),
                    createAP("* \"*\"", "listen"),
                    createAP("abc \"abc\" def \"abc\"", "listen") },
                new AuthenticationPermission[] {
                    createAP("def \"def\"", "listen"),
                    createAP("abc \"ghi\"", "listen"),
                    createAP("abc \"*\"", "listen"),
                    createAP("* \"*\"", "listen"),
                    createAP("abc \"def\" abc \"abc\"", "listen"),
                    createAP("abc \"def\" def \"def\"", "listen") },
                new AuthenticationPermission[] { null },
                new AuthenticationPermission[] {
                    createAP("abc \"def\" peer def \"jkl\"", "connect"),
                    createAP("abc \"def\" peer ghi \"ghi\"", "connect"),
                    createAP("abc \"def\"", "connect") } };
        AuthenticationPermission[][] impliedPerms =
            new AuthenticationPermission[][] {
                new AuthenticationPermission[] {
                    createAP("abc \"abc\"", "listen, connect"),
                    createAP("abc \"abc\"",
                            "listen, connect, accept, delegate") },
                new AuthenticationPermission[] {
                    createAP("abc \"abc\"", "listen") },
                new AuthenticationPermission[] {
                    createAP("abc \"abc\"", "connect"),
                    createAP("abc \"*\"", "connect"),
                    createAP("* \"*\"", "connect"),
                    createAP("abc \"abc\" def \"def\" ghi \"*\"", "connect"),
                    createAP("* \"*\" peer abc \"abc\"", "connect") },
                new AuthenticationPermission[] {
                    createAP("abc \"abc\"", "listen"),
                    createAP("abc \"*\"", "listen"),
                    createAP("abc \"abc\" abc \"def\"", "listen"),
                    createAP("abc \"abc\" peer def \"def\"", "listen") },
                new AuthenticationPermission[] {
                    createAP("abc \"def\"", "listen"),
                    createAP("abc \"def\" peer ghi \"ghi\"", "listen") },
                new AuthenticationPermission[] {
                    createAP("abc \"def\" peer abc \"def\"", "listen"),
                    createAP("abc \"def\"", "listen"),
                    createAP("abc \"def\" abc \"def\"", "listen") },
                new AuthenticationPermission[] {
                    createAP("abc \"def\" peer def \"ghi\" def \"jkl\"",
                            "connect"),
                    createAP("abc \"def\" peer def \"ghi\"", "connect") } };

        for (int i = 0; i < testPerms.length; ++i) {
            for (int j = 0; j < notImpliedPerms[i].length; ++j) {
                if (notImpliedPerms[i][j] == null) {
                    continue;
                }
                res = implies(testPerms[i], notImpliedPerms[i][j]);

                if (res) {
                    // FAIL
                    throw new TestException(
                            "Performed 'implies' method call returned true "
                            + "while false was expected.");
                } else {
                    // PASS
                    logger.fine("Performed 'implies' method call returned "
                            + "false as expected.");
                }
            }

            for (int j = 0; j < impliedPerms[i].length; ++j) {
                res = implies(testPerms[i], impliedPerms[i][j]);

                if (!res) {
                    // FAIL
                    throw new TestException(
                            "Performed 'implies' method call returned "
                            + "false while true was expected.");
                } else {
                    // PASS
                    logger.fine("Performed 'implies' method call returned "
                            + "true as expected.");
                }
            }
            res = implies(testPerms[i], testPerms[i]);

            if (!res) {
                // FAIL
                throw new TestException(
                        "Performed 'implies' method call returned false "
                        + "while true was expected.");
            } else {
                // PASS
                logger.fine("Performed 'implies' method call returned true "
                        + "as expected.");
            }
        }
    }

    /**
     * Logs parameters specified and calls 'implies' method of
     * AuthenticationPermission specified.
     *
     * @param ap AuthenticationPermission whose 'implies' method will be called
     * @param param Permission which will be a parameter to 'implies'
     *        method call
     * @return result of 'implies' method call
     */
    public boolean implies(AuthenticationPermission ap, Permission param) {
        if (ap != param) {
            logger.fine("Call 'implies' method of " + ap + " with " + param
                    + " parameter.");
        } else {
            logger.fine("Call 'implies' method of " + ap
                    + " with itself as a parameter.");
        }
        return ap.implies(param);
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
