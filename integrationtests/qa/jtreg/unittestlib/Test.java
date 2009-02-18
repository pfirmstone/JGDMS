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

/** Defines the interface that a unit test should implement. */
public interface Test {

    /**
     * Returns the name of the test.  Assumes that the caller will include the
     * name of this class when displaying the test results, so the name only
     * needs to identify the test relative to other tests of the same class.
     *
     * @return the name of the test.
     */
    String name();

    /**
     * Runs the test and returns a result.
     *
     * @return the result of the test
     * @throws Exception any exception thrown when running the test.  The test
     *	       is assumed to have failed if an exception is thrown.
     */
    Object run() throws Exception;

    /**
     * Checks to see if the test was successful.
     *
     * @param result the test result returned by run
     * @throws Exception any exception thrown when checking the test.  The test
     *	       is assumed to have failed if any exception is thrown.
     */
    void check(Object result) throws Exception;

    /**
     * The exception thrown by run or check if they determine that the test has
     * failed.
     */
    public static class FailedException extends RuntimeException {
	public FailedException(String message) {
	    super(message);
	}
    }
}
