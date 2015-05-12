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

/**
 * Utility class to hold a test result.
 */
public class TestResult {
    /** The length of time (in milliseconds) the test took to execute. */
    public long elapsedTime;

    /** The log file. */
    public File logFile;

    /** The informative message describing the success/failure. */
    public String message;

    /** The success/failure state of the test */
    public boolean state;

    /** The failure type (always <code>TEST</code> for success). */
    public int type;

    /** the test description and configTag */
    public TestRun run;

    /**
     * The standard constructor.  This constructor takes most of the arguments
     * to specify the test result.  The elapsed time and log file are
     * set after creation.
     *
     * @param run the test description, configTag pair
     * @param passed   the state of the status: failed=false, passed=true
     * @param failtype the failure type, ignored for a passed test
     * @param message  a detail message for the test result
     */
    public TestResult(TestRun run,
                  boolean passed,
                  int failtype,
                  String message)
    {
        this. run = run;
        this.state = passed;
        this.type = failtype;
        this.message = ((message == null) ? "" : message);
    }

    /**
     * Sets the time (in milliseconds) that the test took to execute.
     *
     * @param elapsedTime the length of time (in milliseconds) the test took
     */
    public void setElapsedTime(long elapsedTime) {
        this.elapsedTime = elapsedTime;
    }

    /**
     * Sets the log file containing the test output.
     *
     * @param logFile the log file containing the test output
     */
    public void setLogFile(File logFile) {
        this.logFile = logFile;
    }

    /**
     * Returns a string representation of this test result object.
     *
     * @return a string representation of the object
     */
    public String toString() {
        StringBuffer outMess = new StringBuffer(run.td.getName() + "\n");
        if (state) {
            outMess.append("Test Passed: ");
        } else if (type == Test.SKIP) {
            outMess.append("Test Skipped: ");
	} else {
            outMess.append("Test Failed");
            if (type == Test.ENV) {
                outMess.append(" (Environment Problem): ");
            } else if (type == Test.INDEF) {
                outMess.append(" (Indefinite Cause): ");
            } else {
                outMess.append(": ");
            }
        }
        return outMess.append(message).toString();
    }

}

