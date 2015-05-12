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
package org.apache.river.qa.harness;

import java.io.File;
import java.util.ArrayList;
import java.util.NoSuchElementException;

/**
 * A data structure that holds the test to run, rerun, and their results.
 *
 * This object relies on these config entries:
 * <ul>
 * <li><code>org.apache.river.qa.harness.rerunFailedTests</code> is a
 *     boolean flag that controls whether or not failed tests should be rerun
 * <li><code>org.apache.river.qa.harness.testMaxRetries</code> specifies 
 *     the maximum number of times to rerun a single failing test
 * </ul>
 */
public class TestList {

    /** the set of test descriptions and configurations to run, 
        may NOT contain duplicates */
    private ArrayList runList = new ArrayList();

    /** the set of test descriptions and configurations to rerun, 
        may contain duplicates */
    private ArrayList rerunList = new ArrayList();

    /** the list of test results, parallel to <code>runList</code> */
    private ArrayList resultList = new ArrayList();

    /** current element of the iteration over the runList */
    int runIndex = 0;

    /** current element of the iteration over the rerunList */
    int rerunIndex = 0;
    
    /** the number of test skipped */
    int numSkipped = 0;

    /** the number of test passed */
    int numPassed = 0;

    /** the number of test failed */
    int numFailed = 0;

    /** the number of test completed */
    int numCompleted = 0;

    /** the number of test rerun */
    int numRerun = 0;

    /** value of <code>org.apache.river.qa.harness.rerunFailedTests</code> entry */
    boolean retriesEnabled;

    /** holds the value of the 
      * <code>org.apache.river.qa.harness.testMaxRetries</code> config entry 
      */
    int maxRetries;

    /** the time (in milliseconds) the entire test run started */
    long startTime;

    /** the time (in milliseconds) the entire test run finished */
    long finishTime;
    
    /** Constructs a TestList with the given config object and given
      * start time.
      *
      * @param config the config object
      * @param startTime the time (in milliseconds) the entire test run started
      */
    public TestList(QAConfig config, long startTime) {
        retriesEnabled = config.getBooleanConfigVal(
            "org.apache.river.qa.harness.rerunFailedTests", false);
        maxRetries = config.getIntConfigVal(
	    "org.apache.river.qa.harness.testMaxRetries", 1);
	this.startTime = startTime;
    }

    /**
      * Add the given TestRun to the list of test to run if it is not
      * already in the test list.  Duplicate TestRun objects are NOT allowed.
      *
      * @param run the TestRun object to add
      * @throws IllegalArgumentException the run object has already been added
      */
    public void add(TestRun run) throws IllegalArgumentException {
	if (runList.contains(run)) {
	    throw new IllegalArgumentException("TestRun " + run 
		+ " already in the test list");
	} else {
            runList.add(run);
            resultList.add(new ArrayList());
	}
    }

    /**
      * Determine if this test list already contains the given
      * TestDescription.
      * 
      * @param td the TestDescription to verify
      */
    public boolean contains(TestDescription td) {
        for (int i = 0; i < runList.size(); i++) {
	    TestRun run = (TestRun) runList.get(i);
	    if (run.td == td) {
		return true;
	    }
	}
	return false;
    }
    
    /**
      * Represents the check for more elements in the iteration over
      * the test list.  Elements added after the iteration has started
      * will still be included in the iteration.  Test list may only
      * be iterated over once.
      */
    public boolean hasMore() {
	// iterate over runList and then the rerunList
        return (runIndex < runList.size() || rerunIndex < rerunList.size());
    }

    /**
      * Represents an iterator over the elements of the test list.
      * If a returned TestRun object represents a rerun, then the
      * isRerun field of the TestRun is set to true; false otherwise.
      * Elements added after the iteration has started
      * will still be included in the iteration.  Test list may only
      * be iterated over once.
      *
      * @throws NoSuchElementException no more elements in the list
      */
    public TestRun next() throws NoSuchElementException {
	// iterate over runList and then the rerunList
        if (runIndex < runList.size()) {
	    TestRun retval = (TestRun) runList.get(runIndex++);
	    retval.isRerun = false;
            return retval;
        } else if (rerunIndex < rerunList.size()) {
	    TestRun retval = (TestRun) rerunList.get(rerunIndex++);
	    retval.isRerun = true;
            return retval;
        } else {
            throw new NoSuchElementException("No more tests");
        }
    }

    /**
      * Add the given TestResult for the given TestRun to the test list.
      *
      * @param run the TestRun object for which the test result applies
      * @param result the test result for the given run
      * @throws IllegalArgumentException the run object is not in the test list
      */
    public void add(TestRun run, TestResult result) 
	throws IllegalArgumentException 
    {
	if (! runList.contains(run)) {
	    throw new IllegalArgumentException("TestRun " + run 
		+ " not in the test list");
	}
        ArrayList formerResults = 
	    (ArrayList) resultList.get(runList.indexOf(run));

	// update statistics
	if (result.type == Test.SKIP) {
	    numSkipped++;
	} else if (result.state) {
            if (formerResults.isEmpty()) {
                // initial run of test passes
                numCompleted++;
                numPassed++;
            } else {
                // rerun of failed test now passes
                numRerun++;
                numPassed++;
                numFailed--;
            }
        } else {
            if (formerResults.isEmpty()) {
                // initial run of test fails
                numCompleted++;
                numFailed++;
            } else {
                // rerun of failed test still fails
                numRerun++;
            }
        }

	// add result to resultList and determine if test needs to be rerun
        formerResults.add(result);
        determineRetry(run, result);
    }

    /**
      * Determine if the test run whould be repeated.
      * 
      * @param run the test to possibly rerun
      * @param result the last result of the test run
      */
    private void determineRetry(TestRun run, TestResult result) {
        if (retriesEnabled && result.type == Test.RERUN) {
            ArrayList curResults = 
		(ArrayList) resultList.get(runList.indexOf(run));
	    // the number of completed retries is one less than 
	    // the current size of the results array
            int numCompletedRetries = curResults.size() - 1;
            if (numCompletedRetries >= maxRetries) {
                return;
            }
            switch (numCompletedRetries) {
                case 0 :
		    // first rerun is unconditional
                    addRerun(run);
                    break;
                case 1 :
		    // second rerun only if test took < 20 minutes to run
                    if (result.elapsedTime < (20 * 60 * 1000)) { // 20 mins
                        addRerun(run);
                    }
                    break;
                default :
		    // subsequent reruns (up to maxRetries) only if 
		    // test took < 10 minutes to run
                    if (result.elapsedTime < (10 * 60 * 1000)) { // 10 mins
                        addRerun(run);
                    }
                    break;
            }
        }
    }
    
    /**
      * A a test run to the rerunList.
      */
    private void addRerun(TestRun run) {
        rerunList.add(run);
    }
    
    public void setFinishTime(long finishTime) {
	this.finishTime = finishTime;
    }

    public long getFinishTime() {
	return finishTime;
    }

    public long getStartTime() {
	return startTime;
    }

    public long getDurationTime() {
	return finishTime - startTime;
    }

    public int getNumStarted() {
        return runList.size() - numSkipped;
    }

    public int getNumPassed() {
        return numPassed;
    }

    public int getNumFailed() {
        return numFailed;
    }

    public int getNumCompleted() {
        return numCompleted;
    }

    public int getNumSkipped() {
        return numSkipped;
    }

    public int getNumRerun() {
        return numRerun;
    }

    /**
      * Creates an iterator over the test results.
      */
    public TestResultIterator createTestResultIterator() {
	return new TestResultIterator();
    }

    /**
      * An iterator for the list of test results.
      */
    class TestResultIterator {
	/** current element of the iteration over the resultList */
        int resultIndex = 0;
        
	/**
	  * Determines if there are more elements in the iteration over
	  * the test results list.  Elements added after the iteration 
	  * has started will still be included in the iteration.
	  */
        public boolean hasMore() {
            return (resultIndex < resultList.size());
        }
   	   
	/**
	  * Iterates over the elements of the test results list.
	  * Elements added after the iteration has started
	  * will still be included in the iteration. 
	  *
	  * @return all the test results (as an array) for a single TestRun
	  *         (TestRun may be run more than once)
	  * @throws NoSuchElementException no more elements in the list
	  */
        public TestResult[] next() throws NoSuchElementException {
            if (hasMore()) {
                ArrayList results = (ArrayList) resultList.get(resultIndex++);
                return (TestResult[]) results.toArray(new TestResult[0]);
            } else {
                throw new NoSuchElementException("No more results");
            }
        }
    }

    int getTestTotal() {
	return runList.size() + rerunList.size();
    }

    int getTestNumber() { 
	return runIndex + rerunIndex;
    }

}
