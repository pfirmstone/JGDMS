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
/**
 * This class can be used to run a set of tests concurrently, each in
 * its own thread.  Currently it is hard coded to run tests in
 * TestEndpoints, but it can be easily modified to run tests from
 * other sources.
 */
public class ConcurrentTests extends BasicTest implements Test {

    private Test[] tests;
    private String exceptionMsg;
    private int failCount;

    ConcurrentTests(String name, Test[] tests) {
	super(name);
	this.tests = tests;
    }

    public static void main(String[] args) {
	ConcurrentTests conTest = new ConcurrentTests(
	    "concurrentTests", TestEndpoints.tests);
	try {
	    UnitTestUtilities.test(conTest);
	} finally {
	    TestEndpoints.cleanup();
	}
    }

    public Object run() throws Exception {
	final Thread[] threads = new Thread[tests.length];
	final String[] results = new String[tests.length];
	final String[] exceptionMsgs = new String[tests.length];
	for (int i = 0; i < tests.length; i ++) {
	    final int id = i;
	    threads[id] = new Thread() {
		    public void run() {
			try {
			    String fmsg = (String)tests[id].run();
			    if (fmsg != null) {
				results[id] = tests[id].name() +
				    " failed! \n\treason: " + fmsg;
			    }
			} catch (Exception e) {
			    results[id] = tests[id].name() + " failed! " +
				"\n\treason: " + e.getMessage();
			    e.printStackTrace(); // for debug
			}

			try {
			    tests[id].check(results[id]);
			} catch (Exception e) {
			    exceptionMsgs[id] = e.getMessage();
			}
		    }
		};
	    threads[id].start();
	}

	for (int i = 0; i < threads.length; i ++) {
	    try {
		threads[i].join();
	    } catch (InterruptedException e) {}
	}

	String failureMsg = null;
	for (int i = 0; i < threads.length; i ++) {
	    if (results[i] != null) {
		failureMsg = failureMsg == null ? 
		    "\n\t" + results[i] : failureMsg + "\n\t" + results[i];
	    }
	    if (exceptionMsgs[i] != null) {
		exceptionMsg = exceptionMsg == null ? 
		    exceptionMsgs[i] : exceptionMsg + "\n\t" +
		    exceptionMsgs[i];
		failCount++;
	    }
	}

	return failureMsg;
    }

    public void check(Object result) {
	if (result != null) {
	    throw new FailedException(
		name() + " failed! (" + failCount + " sub-test" +
		(failCount > 1 ? "s" : "") + " failed)\n\t" +
		exceptionMsg);
	} else {
	    System.out.println("\n\t" + this.name() + " Passed! (" +
			       tests.length + " sub-tests)");
	}
    }
}
