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
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.test.spec.security.util.TestAccessPermission;

/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     'implies' method of AccessPermission returns true if every fully
 *     qualified name that matches the specified permission's name also matches
 *     this permission's name; returns false otherwise.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     TestPermission - permission which does not extend AccessPermission
 *     TestAccessPermission - permission extending AccessPermission
 *
 * Action
 *   The test performs the following steps:
 *     1) construct AccessPermission0 with "abc" target name
 *     2) call 'implies' method of constructed AccessPermission0 with null
 *        as a parameter
 *     3) assert that false will be returned
 *     4) call 'implies' method of constructed AccessPermission0 with
 *        TestPermission as a parameter
 *     5) assert that false will be returned
 *     6) construct TestAccessPermission with "abc" target name
 *     7) call 'implies' method of constructed AccessPermission1 with
 *        TestAccessPermission as a parameter
 *     8) assert that false will be returned
 *     9) construct wrong AccessPermission-s with the following target names:
 *        "ab", "abc1", "*", "a.abc", "aabc", "*abc", "abc*"
 *     10) call 'implies' method of constructed AccessPermission0 with
 *         constructed wrong AccessPermission-s as parameters
 *     11) assert that false will be returned
 *     12) construct AccessPermission-s with the following target names:
 *         "abc", "a.abc"
 *     13) call 'implies' method of constructed AccessPermission1 with
 *         constructed AccessPermission-s as parameters
 *     14) assert that true will be returned
 *
 *     15) construct AccessPermission2 with "*abc" target name
 *     16) construct wrong AccessPermission-s with the following target names:
 *         "bc", "abc1", "*", "abc*"
 *     17) call 'implies' method of constructed AccessPermission2 with
 *         constructed wrong AccessPermission-s as parameters
 *     18) assert that false will be returned
 *     19) construct AccessPermission-s with the following target names:
 *         "aabc", "*abc", "abc", "a.abc", "a.eabc"
 *     20) call 'implies' method of constructed AccessPermission2 with
 *         constructed AccessPermission-s as parameters
 *     21) assert that true will be returned
 *
 *     22) construct AccessPermission3 with "abc*" target name
 *     23) construct wrong AccessPermission-s with the following target names:
 *         "ab", "aabc", "*", "*abc"
 *     24) call 'implies' method of constructed AccessPermission3 with
 *         constructed wrong AccessPermission-s as parameters
 *     25) assert that false will be returned
 *     26) construct AccessPermission-s with the following target names:
 *         "abcd", "abc*", "abc", "a.abc", "a.abcd"
 *     27) call 'implies' method of constructed AccessPermission3 with
 *         constructed AccessPermission-s as parameters
 *     28) assert that true will be returned
 *
 *     29) construct AccessPermission4 with "a.b.cde" target name
 *     30) construct wrong AccessPermission-s with the following target names:
 *         "cde", "a.cde", "b.cde", "a.b.cde1", "a.b.*", "a.b.c.cde",
 *         "a.b.ccde", "a.b.*cde", "a.b.cde*", "*"
 *     31) call 'implies' method of constructed AccessPermission4 with
 *         constructed wrong AccessPermission-s as parameters
 *     32) assert that false will be returned
 *     33) construct AccessPermission5 with "a.b.cde" target name
 *     34) call 'implies' method of constructed AccessPermission4 with
 *         AccessPermission5 as a parameter
 *     35) assert that true will be returned
 *
 *     36) construct AccessPermission6 with "a.b.*cde" target name
 *     37) construct wrong AccessPermission-s with the following target names:
 *         "*cde", "a.*cde", "b.*cde", "a.b.cde1", "a.b.*", "a.b.cde*",
 *         "a.b.c.cde", "*"
 *     38) call 'implies' method of constructed AccessPermission6 with
 *         constructed wrong AccessPermission-s as parameters
 *     39) assert that false will be returned
 *     40) construct AccessPermission-s with the following target names:
 *         "a.b.ccde", "a.b.*cde", "a.b.cde"
 *     41) call 'implies' method of constructed AccessPermission6 with
 *         constructed AccessPermission-s as parameters
 *     42) assert that true will be returned
 *
 *     43) construct AccessPermission7 with "a.b.cde*" target name
 *     44) construct wrong AccessPermission-s with the following target names:
 *         "cde*", "a.cde*", "b.cde*", "a.b.ccde", "a.b.*", "a.b.*cde",
 *         "a.b.c.cde", "*"
 *     45) call 'implies' method of constructed AccessPermission7 with
 *         constructed wrong AccessPermission-s as parameters
 *     46) assert that false will be returned
 *     47) construct AccessPermission-s with the following target names:
 *         "a.b.cdef", "a.b.cde*", "a.b.cde"
 *     48) call 'implies' method of constructed AccessPermission7 with
 *         constructed AccessPermission-s as parameters
 *     49) assert that true will be returned
 *
 *     50) construct AccessPermission8 with "a.b.*" target name
 *     51) construct wrong AccessPermission-s with the following target names:
 *         "a.*", "b.*", "a.b.c.*", "*"
 *     52) call 'implies' method of constructed AccessPermission8 with
 *         constructed wrong AccessPermission-s as parameters
 *     53) assert that false will be returned
 *     54) construct AccessPermission-s with the following target names:
 *         "a.b.cde", "a.b.cde*", "a.b.*cde", "a.b.*"
 *     55) call 'implies' method of constructed AccessPermission8 with
 *         constructed AccessPermission-s as parameters
 *     56) assert that true will be returned
 *
 *     57) construct AccessPermission9 with "*" target name
 *     58) construct AccessPermission-s with the following target names:
 *         "cde", "cde*", "*cde", "a.b.cde", "a.b.cde*", "a.b.*cde", "a.b.*",
 *         "*"
 *     59) call 'implies' method of constructed AccessPermission9 with
 *         constructed AccessPermission-s as parameters
 *     60) assert that true will be returned
 * </pre>
 */
public class ImpliesTest extends QATest {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        String[] testNames = new String[] {
            "abc", "*abc", "abc*", "a.b.cde", "a.b.*cde", "a.b.cde*",
            "a.b.*", "*" };
        String[][] notImpliedNames = new String[][] {
            new String[] {
                "ab", "abc1", "*", "aabc", "*abc", "abc*" },
            new String[] { "bc", "abc1", "*", "abc*" },
            new String[] { "ab", "aabc", "*", "*abc" },
            new String[] {
                "cde", "a.cde", "b.cde", "a.b.cde1", "a.b.*", "a.b.c.cde",
                "a.b.ccde", "a.b.*cde", "a.b.cde*", "*" },
            new String[] {
                "*cde", "a.*cde", "b.*cde", "a.b.cde1", "a.b.*",
                "a.b.cde*", "a.b.c.cde", "*" },
            new String[] {
                "cde*", "a.cde*", "b.cde*", "a.b.ccde", "a.b.*",
                "a.b.*cde", "a.b.c.cde", "*" },
            new String[] { "a.*", "b.*", "a.b.c.*", "*" },
            new String[] { null } };
        String[][] impliedNames = new String[][] {
            new String[] { "abc", "a.abc" },
            new String[] { "aabc", "*abc", "abc", "a.abc", "a.eabc" },
            new String[] { "abcd", "abc*", "abc", "a.abc", "a.abcd" },
            new String[] { "a.b.cde" },
            new String[] { "a.b.ccde", "a.b.*cde", "a.b.cde" },
            new String[] { "a.b.cdef", "a.b.cde*", "a.b.cde" },
            new String[] { "a.b.cde", "a.b.cde*", "a.b.*cde", "a.b.*" },
            new String[] {
                "cde", "cde*", "*cde", "a.b.cde", "a.b.cde*", "a.b.*cde",
                "a.b.*", "*" } };
        AccessPermission ap = new AccessPermission("abc");
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
        res = implies(ap, new TestAccessPermission("abc"));

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

        for (int i = 0; i < testNames.length; ++i) {
            ap = new AccessPermission(testNames[i]);

            for (int j = 0; j < notImpliedNames[i].length; ++j) {
                if (notImpliedNames[i][j] == null) {
                    continue;
                }
                res = implies(ap, new AccessPermission(notImpliedNames[i][j]));

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

            for (int j = 0; j < impliedNames[i].length; ++j) {
                res = implies(ap, new AccessPermission(impliedNames[i][j]));

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
        }
    }

    /**
     * Logs parameters specified and calls 'implies' method of
     * AccessPermission specified.
     *
     * @param ap AccessPermission whose 'implies' method will be called
     * @param param Permission which will be a parameter to 'implies'
     *        method call
     * @return result of 'implies' method call
     */
    public boolean implies(AccessPermission ap, Permission param) {
        logger.fine("Call 'implies' method of " + ap + " with " + param
                + " parameter.");
        return ap.implies(param);
    }
}
