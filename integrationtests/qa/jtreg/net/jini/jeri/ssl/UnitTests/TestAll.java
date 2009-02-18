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

/** Run all tests. */
public class TestAll extends TestUtilities {

    public static Object[] tests = {
	TestVerifier.tests,
	TestServerEndpoint.tests,
	// XXX: Disabled for now.  -tjb[1.May.2003]
	// TestListener.tests,
	TestEndpoint.tests,
	TestTwoEndpoints.tests,
	TestRMI.tests,
	/*
	 * The following tests use reflection to access internal fields and
	 * methods.
	 */
	// TestServerConnection.tests,
	TestWeakSoftTable.tests,
	// TestConnectionEndpoint.tests,
	TestUtilities.tests,
	TestConnectionContext.tests,
	TestEndpointInternal.tests,
	/*
	 * Finally, test performance after warming up HotSpot.
	 */
	TestPerformance.tests
    };

    public static void main(String[] args) {
	test(tests);
    }
}
