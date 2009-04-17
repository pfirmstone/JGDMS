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
package com.sun.jini.test.spec.security.accesspermission;

import java.util.logging.Level;

// net.jini
import net.jini.security.AccessPermission;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QATest;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     Constructor of AccessPermission throws NullPointerException if the target
 *     name is null. Constructor of AccessPermission throws
 *     IllegalArgumentException if the target name does not match the following
 *     syntax: The target name can be any of the following forms:
 *       Identifier, Suffix, Identifier*, QualifiedIdentifier.*,
 *       QualifiedIdentifier.Identifier, QualifiedIdentifier.*Suffix,
 *       QualifiedIdentifier.Identifier*
 *     where QualifiedIdentifier and Identifier are as defined in The Java(TM)
 *     Language Specification except that whitespace is not permitted, and
 *     Suffix is defined to be one or more characters that may be part of an
 *     Identifier. These forms are defined to match fully qualified names of
 *     the form QualifiedIdentifier.Identifier as follows:
 *     -----------------------------------------------------------------------
 *     | Target Name  | QualifiedIdentifier Match | Identifier Match         |
 *     -----------------------------------------------------------------------
 *     | *            | any                       | any                      |
 *     -----------------------------------------------------------------------
 *     | method       | any                       | method                   |
 *     -----------------------------------------------------------------------
 *     | *suffix      | any                       | any ending with suffix   |
 *     -----------------------------------------------------------------------
 *     | prefix*      | any                       | any starting with prefix |
 *     -----------------------------------------------------------------------
 *     | type.*       | type                      | any                      |
 *     -----------------------------------------------------------------------
 *     | type.method  | type                      | method                   |
 *     -----------------------------------------------------------------------
 *     | type.*suffix | type                      | any ending with suffix   |
 *     -----------------------------------------------------------------------
 *     | type.prefix* | type                      | any starting with prefix |
 *     -----------------------------------------------------------------------
 *
 * Action
 *   The test performs the following steps:
 *     1) construct AccessPermission using null as a parameter
 *     2) assert that NullPointerException will be thrown
 *     3) construct AccessPermission with different wrong target names:
 *        "", "4x", "abc.4x", "abc.4x.def", "*.abc", "abc*.def", "*abc.def",
 *        "a*bc.def", "abc.*.def", "*abc*", "abc*de", "abc.*def*", "abc.d*ef",
 *        "abc..def", ".abc.def", "abc.def.", ".", " ", " abc", "abc ", "a bc",
 *        "abc. def", "abc.def ", "abc.d ef"
 *     4) assert that IllegalArgumentException will be thrown
 * </pre>
 */
public class ConstructorExceptionsTest extends QATest {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        try {
            createAP(null);

            // FAIL
            throw new TestException(
                    "AccessPermission was created successfully while "
                    + "NullPointerException was expected to be thrown.");
        } catch (NullPointerException npe) {
            // PASS
            logger.fine(npe.toString() + " was thrown as expected.");
        }

        String[] wrongNames = new String[] {
            "", "4x", "abc.4x", "abc.4x.def", "*.abc", "abc*.def",
            "*abc.def", "a*bc.def", "abc.*.def", "*abc*", "abc*de",
            "abc.*def*", "abc.d*ef", "abc..def", ".abc.def", "abc.def.",
            ".", " ", " abc", "abc ", "a bc", "abc. def", "abc.def ",
            "abc.d ef" };

        for (int i = 0; i < wrongNames.length; ++i) {
            try {
                createAP(wrongNames[i]);

                // FAIL
                throw new TestException(
                        "AccessPermission was created successfully while "
                        + "IllegalArgumentException was expected to be "
                        + "thrown.");
            } catch (IllegalArgumentException iae) {
                // PASS
                logger.fine(iae.toString() + " was thrown as expected.");
            }
        }
    }

    /**
     * Logs parameter specified and calls AccessPermission constructor with it.
     *
     * @param name parameter for AccessPermission constructor
     * @return created AccessPermission instance
     * @throws rethrow any exception thrown by constructor
     */
    public AccessPermission createAP(String name) {
        logger.fine("Creating AccessPermission with '" + name
                + "' target name.");
        return new AccessPermission(name);
    }
}
