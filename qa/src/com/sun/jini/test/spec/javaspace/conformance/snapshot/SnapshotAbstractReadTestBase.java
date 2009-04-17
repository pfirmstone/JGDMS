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
package com.sun.jini.test.spec.javaspace.conformance.snapshot;

import java.util.logging.Level;

// net.jini
import net.jini.core.entry.Entry;
import net.jini.core.transaction.Transaction;
import net.jini.space.JavaSpace;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.javaspace.conformance.SimpleEntry;


/**
 * Abstract Test Base tests read/readIfExists methods for snapshots.
 *
 * @author Mikhail A. Markov
 */
public abstract class SnapshotAbstractReadTestBase
        extends SnapshotAbstractTestBase {

    /**
     * Main testing method which tests read/readIfExists method
     * with or without transactions, measure time of invocation
     * and check that result entry match specified template.
     *
     * @param template Template to be tested.
     * @param isWrongTempl
     *         true if we tests wrong template, expects no matching
     *         or false if not.
     * @param txn Transaction under wich we test method (may be null).
     * @param timeout Timeout for read/readIfExists operations.
     * @param curIter Iteration number for correct output strings creation.
     * @param ifExistsMethod
     *         Which method to test:
     *         true - means "readIfExists" method,
     *         false - means "read" one.
     *
     * @return
     *        Null if test passes successfully or string with error
     *        if test fails.
     *
     * @exception TestException
     *         Thrown if any exception is catched during testing.
     */
    public String testTemplate(SimpleEntry template, boolean isWrongTempl,
            Transaction txn, long timeout, int curIter, boolean ifExistsMethod)
            throws TestException {
        SimpleEntry result;
        Entry snapshot;
        long curTime1;
        long curTime2;
        String iterStr;
        String txnStr;
        String methodStr;
        String tmplStr;
        String timeoutStr;

        // fill iteration number string
        switch (curIter) {
          case 0:
            {
                iterStr = " ";
                break;
            }
          case 1:
            {
                iterStr = "1-st ";
                break;
            }
          case 2:
            {
                iterStr = "2-nd ";
                break;
            }
          case 3:
            {
                iterStr = "3-rd ";
                break;
            }
          default:
            {
                iterStr = "" + curIter + "-th ";
                break;
            }
        }

        // fill transaction string
        if (txn == null) {
            txnStr = "";
        } else {
            txnStr = " within the non null transaction";
        }

        // fill method string
        if (ifExistsMethod) {
            methodStr = "readIfExists";
        } else {
            methodStr = "read";
        }

        // fill template string
        if (template == null) {
            tmplStr = "snapshot of null template";
        } else {
            tmplStr = "snapshot of template " + template.toString();
        }

        // fill timeout string
        if (timeout == JavaSpace.NO_WAIT) {
            timeoutStr = "JavaSpace.NO_WAIT value for";
        } else {
            timeoutStr = "" + timeout + " ms";
        }

        try {
            snapshot = space.snapshot(template);
            curTime1 = System.currentTimeMillis();

            if (ifExistsMethod) {
                result = (SimpleEntry) space.readIfExists(snapshot, txn,
                        timeout);
            } else {
                result = (SimpleEntry) space.read(snapshot, txn, timeout);
            }
            curTime2 = System.currentTimeMillis();

            // check that it has returned required entry
            if (isWrongTempl) {
                if (result != null) {
                    return "performed " + iterStr + methodStr + " with "
                            + tmplStr + txnStr + " and " + timeoutStr
                            + " timeout, expected null but read " + result;
                }
            } else {
                if (result == null) {
                    return "performed " + iterStr + methodStr + " with "
                        + tmplStr + txnStr + " and " + timeoutStr + " timeout,"
                        + " expected non null but read null result.";
                } else if (template != null && !template.implies(result)) {
                    return "performed " + iterStr + methodStr + " with "
                            + tmplStr + txnStr + " and " + timeoutStr
                            + " timeout, expected matching entry, but read "
                            + result;
                }
            }

            if (ifExistsMethod || (timeout == JavaSpace.NO_WAIT)) {

                // check that operation has returned immediately
                if ((curTime2 - curTime1) > instantTime) {
                    return iterStr + methodStr + " with " + tmplStr + txnStr
                            + " and " + timeoutStr + " timeout has returned in "
                            + (curTime2 - curTime1) + " but expected in "
                            + instantTime;
                }
            } else if (isWrongTempl) {

                /*
                 * check that operation has returned not less
                 * then in specified timeout
                 */
                if ((curTime2 - curTime1) < timeout) {
                    return iterStr + methodStr + " with " + tmplStr + txnStr
                            + " and " + timeoutStr + " timeout has returned in "
                            + (curTime2 - curTime1)
                            + " but expected at least in" + timeout;
                }
            }
        } catch (Exception ex) {
            throw new TestException("The following exception has been thrown"
                    + " while trying to test " + iterStr + methodStr + " with "
                    + tmplStr + txnStr + " and " + timeoutStr + " timeout: "
                    + ex);
        }
        logDebugText(iterStr + methodStr + " with " + tmplStr + txnStr + " and "
                + timeoutStr + " timeout works as expected.");
        return null;
    }

    /**
     * This method just pass it's parameters to main tesing method to test
     * wrong templates
     *
     * @param template Template to be tested.
     * @param txn Transaction under wich we test method (may be null).
     * @param timeout Timeout for read/readIfExists operations.
     * @param curIter Iteration number for correct output strings creation.
     * @param ifExistsMethod
     *         Which method to test:
     *         true - means "readIfExists" method,
     *         false - means "read" one.
     *
     * @return
     *        Null if test passes successfully or string with error
     *        if test fails.
     *
     * @exception TestException
     *         Thrown if any exception is catched during testing.
     *
     * @see #testTemplate
     */
    public String testWrongTemplate(SimpleEntry template, Transaction txn,
            long timeout, int curIter, boolean ifExistsMethod)
            throws TestException {
        return testTemplate(template, true, txn, timeout, curIter,
                ifExistsMethod);
    }

    /**
     * This method just pass it's parameters to main tesing method to test
     * correct templates
     *
     * @param template Template to be tested.
     * @param txn Transaction under wich we test method (may be null).
     * @param timeout Timeout for read/readIfExists operations.
     * @param curIter Iteration number for correct output strings creation.
     * @param ifExistsMethod
     *         Which method to test:
     *         true - means "readIfExists" method,
     *         false - means "read" one.
     *
     * @return
     *        Null if test passes successfully or string with error
     *        if test fails.
     *
     * @exception TestException
     *         Thrown if any exception is catched during testing.
     *
     * @see #testTemplate
     */
    public String testTemplate(SimpleEntry template, Transaction txn,
            long timeout, int curIter, boolean ifExistsMethod)
            throws TestException {
        return testTemplate(template, false, txn, timeout, curIter,
                ifExistsMethod);
    }
}
