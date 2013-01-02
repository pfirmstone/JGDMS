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

// java
import java.security.Permission;

// net.jini
import net.jini.security.AccessPermission;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.test.spec.security.util.TestAccessPermission;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     'equals' method of AccessPermission returns true if the specified object
 *     is an instance of the same class as this permission and has the same
 *     target name as this permission; returns false otherwise.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     TestPermission - permission which does not extend AccessPermission
 *     TestAccessPermission - permission extending AccessPermission
 *
 * Action
 *   The test performs the following steps:
 *     1) construct AccessPermission0 with "abc" target name
 *     2) call 'equals' method of constructed AccessPermission0 with
 *        TestPermission as a parameter
 *     3) assert that false will be returned
 *     4) construct TestAccessPermission with "abc" target name
 *     5) call 'equals' method of constructed AccessPermission0 with
 *        TestAccessPermission as a parameter
 *     6) assert that false will be returned
 *     7) construct wrong AccessPermission-s with the following target names:
 *        "ab", "bc", "*abc", "abc*", "a.abc", "*"
 *     8) call 'equals' method of constructed AccessPermission0 with
 *        constructed wrong AccessPermission-s as parameters
 *     9) assert that false will be returned
 *     10) construct AccessPermission1 with "abc" target name
 *     11) call 'equals' method of constructed AccessPermission0 with
 *         AccessPermission1 as a parameter
 *     12) assert that true will be returned
 *     13) call 'equals' method of constructed AccessPermission0 with
 *         the same AccessPermission0 as a parameter
 *     14) assert that true will be returned
 *
 *     15) construct AccessPermission2 with "*abc" target name
 *     16) construct wrong AccessPermission-s with the following target names:
 *         "abc", "aabc", "abc*", "*abcd", "a.*abc", "*"
 *     17) call 'equals' method of constructed AccessPermission2 with
 *         constructed wrong AccessPermission-s as parameters
 *     18) assert that false will be returned
 *     19) construct AccessPermission3 with "*abc" target name
 *     20) call 'equals' method of constructed AccessPermission2 with
 *         AccessPermission3 as a parameter
 *     21) assert that true will be returned
 *
 *     22) construct AccessPermission4 with "abc*" target name
 *     23) construct wrong AccessPermission-s with the following target names:
 *         "abc", "abcd", "*abc", "abcd*", "a.abc*", "*"
 *     24) call 'equals' method of constructed AccessPermission4 with
 *         constructed wrong AccessPermission-s as parameters
 *     25) assert that false will be returned
 *     26) construct AccessPermission5 with "abc*" target name
 *     27) call 'equals' method of constructed AccessPermission4 with
 *         AccessPermission5 as a parameter
 *     28) assert that true will be returned
 *
 *     29) construct AccessPermission6 with "a.b.cde" target name
 *     30) construct wrong AccessPermission-s with the following target names:
 *         "a.cde", "b.cde", "a.b.c.cde", "a.b.cd", "a.b.cdef", "a.b.cde*",
 *         "a.b.*cde", "a.b.*", "*"
 *     31) call 'equals' method of constructed AccessPermission6 with
 *         constructed wrong AccessPermission-s as parameters
 *     32) assert that false will be returned
 *     33) construct AccessPermission7 with "a.b.cde" target name
 *     34) call 'equals' method of constructed AccessPermission6 with
 *         AccessPermission7 as a parameter
 *     35) assert that true will be returned
 *
 *     36) construct AccessPermission8 with "a.b.*cde" target name
 *     37) construct wrong AccessPermission-s with the following target names:
 *         "a.*cde", "b.*cde", "a.b.c.*cde", "a.b.cde", "a.b.cde*", "a.b.*", "*"
 *     38) call 'equals' method of constructed AccessPermission8 with
 *         constructed wrong AccessPermission-s as parameters
 *     39) assert that false will be returned
 *     40) construct AccessPermission9 with "a.b.*cde" target name
 *     41) call 'equals' method of constructed AccessPermission8 with
 *         AccessPermission9 as a parameter
 *     42) assert that true will be returned
 *
 *     43) construct AccessPermission10 with "a.b.cde*" target name
 *     44) construct wrong AccessPermission-s with the following target names:
 *         "a.cde*", "b.cde*", "a.b.c.cde*", "a.b.cde", "a.b.*cde", "a.b.*", "*"
 *     45) call 'equals' method of constructed AccessPermission10 with
 *         constructed wrong AccessPermission-s as parameters
 *     46) assert that false will be returned
 *     47) construct AccessPermission11 with "a.b.cde*" target name
 *     48) call 'equals' method of constructed AccessPermission10 with
 *         AccessPermission11 as a parameter
 *     49) assert that true will be returned
 *
 *     50) construct AccessPermission12 with "a.b.*" target name
 *     51) construct wrong AccessPermission-s with the following target names:
 *         "a.*", "b.*", "a.b.c.*", "a.b.cde", "a.b.*cde", "a.b.cde*", "*"
 *     52) call 'equals' method of constructed AccessPermission12 with
 *         constructed wrong AccessPermission-s as parameters
 *     53) assert that false will be returned
 *     54) construct AccessPermission13 with "a.b.*" target name
 *     55) call 'equals' method of constructed AccessPermission12 with
 *         AccessPermission13 as a parameter
 *     56) assert that true will be returned
 *
 *     57) construct AccessPermission14 with "*" target name
 *     58) construct wrong AccessPermission-s with the following target names:
 *         "abc", "a.bcd", "abc*", "*abc", "a.*bcd", "a.bcd*", "a.*"
 *     59) call 'equals' method of constructed AccessPermission14 with
 *         constructed wrong AccessPermission-s as parameters
 *     60) assert that false will be returned
 *     61) construct AccessPermission15 with "*" target name
 *     62) call 'equals' method of constructed AccessPermission14 with
 *         AccessPermission15 as a parameter
 *     63) assert that true will be returned
 * </pre>
 */
public class EqualsTest extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        String[] testNames = new String[] {
            "abc", "*abc", "abc*", "a.b.cde", "a.b.*cde", "a.b.cde*",
            "a.b.*", "*" };
        String[][] notEqualNames = new String[][] {
            new String[] {
                "ab", "bc", "*abc", "abc*", "a.abc", "*" },
            new String[] { "abc", "aabc", "abc*", "*abcd", "a.*abc", "*" },
            new String[] { "abc", "abcd", "*abc", "abcd*", "a.abc*", "*" },
            new String[] {
                "a.cde", "b.cde", "a.b.c.cde", "a.b.cd", "a.b.cdef",
                "a.b.cde*", "a.b.*cde", "a.b.*", "*" },
            new String[] {
                "a.*cde", "b.*cde", "a.b.c.*cde", "a.b.cde", "a.b.cde*",
                "a.b.*", "*" },
            new String[] {
                "a.cde*", "b.cde*", "a.b.c.cde*", "a.b.cde", "a.b.*cde",
                "a.b.*", "*" },
            new String[] {
                "a.*", "b.*", "a.b.c.*", "a.b.cde", "a.b.*cde",
                "a.b.cde*", "*" },
            new String[] {
                "abc", "a.bcd", "abc*", "*abc", "a.*bcd", "a.bcd*",
                "a.*" } };
        AccessPermission ap = new AccessPermission("abc");
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
        res = equals(ap, new TestAccessPermission("abc"));

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

        for (int i = 0; i < testNames.length; ++i) {
            ap = new AccessPermission(testNames[i]);

            for (int j = 0; j < notEqualNames[i].length; ++j) {
                if (notEqualNames[i][j] == null) {
                    continue;
                }
                res = equals(ap, new AccessPermission(notEqualNames[i][j]));

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
            res = equals(ap, new AccessPermission(testNames[i]));

            if (!res) {
                // FAIL
                throw new TestException(
                        "Performed 'equals' method call returned "
                        + "false while true was expected.");
            } else {
                // PASS
                logger.fine("Performed 'equals' method call returned "
                        + "true as expected.");
            }
        }
    }

    /**
     * Logs parameters specified and calls 'equals' method of
     * AccessPermission specified.
     *
     * @param ap AccessPermission whose 'equals' method will be called
     * @param param Permission which will be a parameter to 'equals'
     *        method call
     * @return result of 'equals' method call
     */
    public boolean equals(AccessPermission ap, Permission param) {
        logger.fine("Call 'equals' method of " + ap + " with " + param
                + " parameter.");
        return ap.equals(param);
    }
}
