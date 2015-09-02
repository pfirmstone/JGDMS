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
import java.util.Set;
import java.util.HashSet;

// net.jini
import net.jini.security.AuthenticationPermission;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import org.apache.river.test.spec.security.util.FakePrincipal;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     Constructor with 3 parameters of AuthenticationPermission throws
 *     NullPointerException if the local principals set or the actions string
 *     is null. It throws IllegalArgumentException if the local principals set
 *     is empty, or either set contains objects that are not
 *     java.security.Principal instances, or the actions string does not match
 *     the syntax specified in the comments at the beginning of this class.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     NonPrincipal - class which does not extend java.security.Principal
 *
 * Action
 *   The test performs the following steps:
 *     1) construct AuthenticationPermission using 3-args constructor with null
 *        set of local principals and non-null actions list
 *     2) assert that NullPointerException will be thrown
 *     3) construct AuthenticationPermission using 3-args constructor with
 *        non-null set of local principals and null actions list
 *     4) assert that NullPointerException will be thrown
 *     5) construct AuthenticationPermission using 3-args constructor with null
 *        set of local principals and null actions list
 *     6) assert that NullPointerException will be thrown
 *     7) construct AuthenticationPermission using 3-args constructor with empty
 *        set of local principals
 *     8) assert that IllegalArgumentException will be thrown
 *     9) construct AuthenticationPermission using 3-args constructor with
 *        set of local principals containing NonPrincipal class instance
 *     10) assert that IllegalArgumentException will be thrown
 *     11) construct AuthenticationPermission using 3-args constructor with
 *         different wrong actions names:
 *         "", "*", "lisSten", "accept, lisSten", "accept listen"
 *     12) assert that IllegalArgumentException will be thrown
 * </pre>
 */
public class Constructor2ExceptionsTest extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        Set prin = new HashSet();
        prin.add(new FakePrincipal());

        try {
            createAP(null, prin, "listen");

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
            createAP(prin, prin, null);

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
            createAP(null, prin, null);

            // FAIL
            throw new TestException(
                    "AuthenticationPermission was created successfully "
                    + "while NullPointerException was expected to be "
                    + "thrown.");
        } catch (NullPointerException npe) {
            // PASS
            logger.fine(npe.toString() + " was thrown as expected.");
        }
        Set testPrin = new HashSet();

        try {
            createAP(testPrin, prin, "listen");

            // FAIL
            throw new TestException(
                    "AuthenticationPermission was created successfully "
                    + "while IllegalArgumentException was expected to be "
                    + "thrown.");
        } catch (IllegalArgumentException iae) {
            // PASS
            logger.fine(iae.toString() + " was thrown as expected.");
        }
        testPrin.add(new FakePrincipal());
        testPrin.add(new Object());

        try {
            createAP(testPrin, prin, "listen");

            // FAIL
            throw new TestException(
                    "AuthenticationPermission was created successfully "
                    + "while IllegalArgumentException was expected to be "
                    + "thrown.");
        } catch (IllegalArgumentException iae) {
            // PASS
            logger.fine(iae.toString() + " was thrown as expected.");
        }
        String[] actions = new String[] {
            "", "*", "lisSten", "accept, lisSten", "accept listen" };

        for (int i = 0; i < actions.length; ++i) {
            try {
                createAP(prin, prin, actions[i]);

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
     * Logs parameters specified and calls 3-args AuthenticationPermission
     * constructor with them.
     *
     * @param local 1-st parameter for AuthenticationPermission constructor
     * @param peer 2-nd parameter for AuthenticationPermission constructor
     * @param actions 2-rd parameter for AuthenticationPermission constructor
     * @return created AuthenticationPermission instance
     * @throws rethrow any exception thrown by constructor
     */
    public AuthenticationPermission createAP(Set local, Set peer,
            String actions) {
        logger.fine("Creating AuthenticationPermission with '" + local
                + "' set of local principals, '" + peer
                + "' set of peer principals and '"+ actions + "' actions.");
        return new AuthenticationPermission(local, peer, actions);
    }
}
