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
 *     Constructor with 2 parameters of AuthenticationPermission throws
 *     NullPointerException if the target name or actions string is null.
 *     It throws IllegalArgumentException if the target
 *     name or actions string does not match the following syntax:
 *     Target name is either: LocalPrincipals
 *     or: LocalPrincipals peer PeerPrincipals
 *     where LocalPrincipals and PeerPrincipals are sets of principals.
 *     The syntax of both LocalPrincipals and PeerPrincipals is:
 *     PrincipalClass "PrincipalName" ...
 *     That is, alternating principal classes and principal names, separated by
 *     spaces, with each principal name surrounded by quotes.
 *     For LocalPrincipals, in any given principal specification, a wildcard
 *     value of "*" can be used for both PrincipalClass and PrincipalName or
 *     for just PrincipalName, but it is illegal to use a wildcard value for
 *     just PrincipalClass. Explicit wildcard values cannot be used in
 *     PeerPrincipals.
 *     The syntax of the actions is a comma-separated list of any of the
 *     following (case-insensitive) action names: listen, accept, connect,
 *     delegate.
 *
 * Action
 *   The test performs the following steps:
 *     1) construct AuthenticationPermission with null target name and non-null
 *        actions list
 *     2) assert that NullPointerException will be thrown
 *     3) construct AuthenticationPermission with non-null target name and null
 *        actions list
 *     4) assert that NullPointerException will be thrown
 *     5) construct AuthenticationPermission with null target name and null
 *        actions list
 *     6) assert that NullPointerException will be thrown
 *     7) construct AuthenticationPermission with different wrong target names:
 *        "",
 *        abc abc,
 *        abc "def,
 *        abc "def" peer abc "def,
 *        abc peer def "def",
 *        abc "abc" peer def,
 *        * "abc",
 *        *,
 *        abc "def" peer *,
 *        abc "def" peer abc "*",
 *        abc "def" peer * "*",
 *        abc "def" abc "def,
 *        abc "def" peer abc "def" abc "def,
 *        abc "def" abc peer def "def",
 *        abc "abc" peer def "def" def,
 *        abc "abc" * "abc",
 *        abc "def" peer abc "def" *
 *     8) assert that IllegalArgumentException will be thrown
 *     9) construct AuthenticationPermission with different wrong actions names:
 *        "", "*", "lisSten", "accept, lisSten", "accept listen"
 *     10) assert that IllegalArgumentException will be thrown
 * </pre>
 */
public class Constructor1ExceptionsTest extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        try {
            createAP(null, "listen");

            // FAIL
            throw new TestException(
                    "AuthenticationPermission was created successfully "
                    + "while NullPointerException was expected to be "
                    + "thrown.");
        } catch (NullPointerException npe) {
            // PASS
            logger.fine(npe.toString() + " was thrown as expected.");
        }

        try {
            createAP("abc", null);

            // FAIL
            throw new TestException(
                    "AuthenticationPermission was created successfully "
                    + "while NullPointerException was expected to be "
                    + "thrown.");
        } catch (NullPointerException npe) {
            // PASS
            logger.fine(npe.toString() + " was thrown as expected.");
        }

        try {
            createAP(null, null);

            // FAIL
            throw new TestException(
                    "AuthenticationPermission was created successfully "
                    + "while NullPointerException was expected to be "
                    + "thrown.");
        } catch (NullPointerException npe) {
            // PASS
            logger.fine(npe.toString() + " was thrown as expected.");
        }
        String[] names = new String[] {
            "",
            "abc abc",
            "abc \"def",
            "abc \"def\" peer abc \"def",
            "abc peer def \"def\"",
            "abc \"abc\" peer def",
            "* \"abc\"",
            "*",
            "abc \"def\" peer *",
            "abc \"def\" peer abc \"*\"",
            "abc \"def\" peer * \"*\"",
            "abc \"def\" abc \"def",
            "abc \"def\" peer abc \"def\" abc \"def",
            "abc \"def\" abc peer def \"def\"",
            "abc \"abc\" peer def \"def\" def",
            "abc \"abc\" * \"abc\"",
            "abc \"def\" peer abc \"def\" *" };

        for (int i = 0; i < names.length; ++i) {
            try {
                createAP(names[i], "listen");

                // FAIL
                throw new TestException(
                        "AuthenticationPermission was created successfully "
                        + "while IllegalArgumentException was expected to "
                        + "be thrown.");
            } catch (IllegalArgumentException iae) {
                // PASS
                logger.fine(iae.toString() + " was thrown as expected.");
            }
        }
        String[] actions = new String[] {
            "", "*", "lisSten", "accept, lisSten", "accept listen" };

        for (int i = 0; i < actions.length; ++i) {
            try {
                createAP("abc", actions[i]);

                // FAIL
                throw new TestException(
                        "AuthenticationPermission was created successfully "
                        + "while IllegalArgumentException was expected to "
                        + "be thrown.");
            } catch (IllegalArgumentException iae) {
                // PASS
                logger.fine(iae.toString() + " was thrown as expected.");
            }
        }
    }

    /**
     * Logs parameters specified and calls 2-args AuthenticationPermission
     * constructor with them.
     *
     * @param name 1-st parameter for AuthenticationPermission constructor
     * @param actions 2-nd parameter for AuthenticationPermission constructor
     * @return created AuthenticationPermission instance
     * @throws rethrow any exception thrown by constructor
     */
    public AuthenticationPermission createAP(String name, String actions) {
        logger.fine("Creating AuthenticationPermission with '" + name
                + "' target name and '" + actions + "' actions.");
        return new AuthenticationPermission(name, actions);
    }
}
