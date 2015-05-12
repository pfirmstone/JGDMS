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
package org.apache.river.test.spec.javaspace.conformance.snapshot;

import java.util.logging.Level;

// net.jini
import net.jini.core.entry.Entry;
import net.jini.core.transaction.Transaction;
import net.jini.space.JavaSpace;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.spec.javaspace.conformance.JavaSpaceTest;
import org.apache.river.test.spec.javaspace.conformance.SimpleEntry;


/**
 * Abstract Test Base tests take/takeIfExists methods for snapshots.
 *
 * @author Mikhail A. Markov
 */
public abstract class SnapshotTakeTestBase
        extends JavaSpaceTest {

    /**
     * Main testing method which tests take/takeIfExists method
     * with or without transactions, measure time of invocation
     * and check that result entry match specified template.
     *
     * @param template Template to be tested.
     * @param isWrongTempl
     *         true if we tests wrong template, expects no matching
     *         or false if not.
     * @param txn Transaction under wich we test method (may be null).
     * @param timeout Timeout for take/takeIfExists operations.
     * @param curIter Iteration number for correct output strings creation.
     * @param iterNum
     *         Whole number of tests:
     *         if current iteration number is equal to this parameter,
     *         then we expected that there will be no matching
     *         entries in the space after tested method invocation,
     *         if no - then at lease one matching entry must remain
     *         in the space.
     * @param ifExistsMethod
     *         Which method to test:
     *         true - means "takeIfExists" method,
     *         false - means "take" one.
     *
     * @return
     *        Null if test passes successfully or string with error
     *        if test fails.
     *
     * @exception TestException
     *         Thrown if any exception is catched during testing.
     */
    public String testTemplate(SimpleEntry template, boolean isWrongTempl,
            Transaction txn, long timeout, int curIter, int iterNum,
            boolean ifExistsMethod) throws TestException {
        SimpleEntry result;
        SimpleEntry takenEntry;
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
            methodStr = "takeIfExists";
        } else {
            methodStr = "take";
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
                result = (SimpleEntry) space.takeIfExists(snapshot, txn,
                        timeout);
            } else {
                result = (SimpleEntry) space.take(snapshot, txn, timeout);
            }
            curTime2 = System.currentTimeMillis();
            takenEntry = result;

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
                            + tmplStr + txnStr + " and " + timeoutStr
                            + " timeout, expected non null but took null"
                            + " result.";
                } else if (template != null && !template.implies(result)) {
                    return "performed " + iterStr + methodStr + " with "
                            + tmplStr + txnStr + " and " + timeoutStr
                            + " timeout, expected matching entry, but took "
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

            // if we test wrong template then test passed
            if (isWrongTempl) {
                logDebugText(iterStr + methodStr + " with " + tmplStr + txnStr
                        + " and " + timeoutStr + " timeout works as expected.");
                return null;
            }

            if (iterNum == 0) {

                /*
                 * we expect, that there are no entries like taken one
                 * are available in the space
                 */
                result = (SimpleEntry) space.read(takenEntry, txn, checkTime);

                if (result != null) {
                    return "performed " + iterStr + methodStr + " with "
                            + tmplStr + txnStr + " and " + timeoutStr
                            + " timeout did not remove taken entry"
                            + " from the space.";
                }
            } else if ((iterNum - curIter) == 0) {

                /*
                 * this is the last checking,
                 * check that there are no matching entries available
                 * in the space
                 */
                result = (SimpleEntry) space.read(template, txn, checkTime);

                if (result != null) {
                    return "performed " + iterStr + methodStr + " with "
                            + tmplStr + txnStr + " and " + timeoutStr
                            + " timeout,"
                            + " expected, that there are no entries available"
                            + " in the space but at least 1 " + result
                            + " is still there.";
                }
            } else {

                /*
                 * check that at least one matching entry is available
                 * in the space
                 */
                result = (SimpleEntry) space.read(template, txn, checkTime);

                if (result == null) {
                    return "performed " + iterStr + methodStr + " with "
                            + tmplStr + txnStr + " and " + timeoutStr
                            + " timeout,"
                            + " there are no entries in the space after"
                            + " tested method invocation but at least 1"
                            + " is expected to remain.";
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
     * @param timeout Timeout for take/takeIfExists operations.
     * @param curIter Iteration number for correct output strings creation.
     * @param iterNum
     *         Whole number of tests:
     *         if current iteration number is equal to this parameter,
     *         then we expected that there will be no matching
     *         entries in the space after tested method invocation,
     *         if no - then at lease one matching entry must remain
     *         in the space.
     * @param ifExistsMethod
     *         Which method to test:
     *         true - means "takeIfExists" method,
     *         false - means "take" one.
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
            long timeout, int curIter, int iterNum, boolean ifExistsMethod)
            throws TestException {
        return testTemplate(template, true, txn, timeout, curIter, iterNum,
                ifExistsMethod);
    }

    /**
     * This method just pass it's parameters to main tesing method to test
     * correct templates
     *
     * @param template Template to be tested.
     * @param txn Transaction under wich we test method (may be null).
     * @param timeout Timeout for take/takeIfExists operations.
     * @param curIter Iteration number for correct output strings creation.
     * @param iterNum
     *         Whole number of tests:
     *         if current iteration number is equal to this parameter,
     *         then we expected that there will be no matching
     *         entries in the space after tested method invocation,
     *         if no - then at lease one matching entry must remain
     *         in the space.
     * @param ifExistsMethod
     *         Which method to test:
     *         true - means "takeIfExists" method,
     *         false - means "take" one.
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
            long timeout, int curIter, int iterNum, boolean ifExistsMethod)
            throws TestException {
        return testTemplate(template, false, txn, timeout, curIter, iterNum,
                ifExistsMethod);
    }
}
