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
package com.sun.jini.test.impl.scalability;

import java.util.logging.Level;

// Test harness specific classes
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;

import java.rmi.NoSuchObjectException;
import java.util.StringTokenizer;
import java.util.HashMap;


/** 
 * Tests that an Lease Renweal Service properly grants, renews,
 * cancels, and/or expires leases.  Tests for availably by adding
 * leases to the set.
 */
public class DemoScalabilityTest extends QATestEnvironment implements Test {

    public Test construct(QAConfig config) throws Exception {
	super.construct(config);
        return this;
    }

    public void run() throws Exception {
	// wait for 'all systems go'
	getConfig().suspendRun("Demo test suspended at phase 1"
			  + "<popup>Resume this test when<br>"
			  + "all participating services are ready</popup>");

	// simulate doing some work
	getConfig().setTestStatus("Performing test actions for 10 seconds");
	try {
	    Thread.sleep(10000);
	} catch (InterruptedException ignore) {
	}

	// simulate phase 2 work
	getConfig().suspendRun("Demo test suspended at phase 2"
			  + "<popup>Resume this test when<br>"
			  + "all phase 2 activities are setup</popup>");

	// wait for termination request
	getConfig().suspendRun("Demo test suspended at end of run"
			  + "<popup>Resume this test to terminate</popup>");
    }
}
