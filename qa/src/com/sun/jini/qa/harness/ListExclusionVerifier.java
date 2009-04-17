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
package com.sun.jini.qa.harness;

import java.util.StringTokenizer;

/**
 * A configuration verifier used by tests which should only be run
 * when the harness is configured for shared VM execution.
 */
public class ListExclusionVerifier implements ConfigurationVerifier {

    /**
     * Return true if the harness is running in shared VM mode.
     *
     * @param td the test description for the test
     * @param config the configuration object
     *
     * @return <code>true</code> if the harness is running in shared mode
     */
    public boolean canRun(TestDescription td, QAConfig config) {
        String exclusionList = config.getStringConfigVal("com.sun.jini.qa.harness.exclusionList", null);
        if (exclusionList == null) {
	    return true;
	}
	StringTokenizer tok = new StringTokenizer(exclusionList, ", \t");
	String testName = td.getName();
	if (!testName.endsWith(".td")) {
	    testName += ".td";
	}
	while (tok.hasMoreTokens()) {
	    String excludeTest = tok.nextToken();
	    if (!excludeTest.endsWith(".td")) {
		excludeTest += ".td";
	    }
	    if (testName.equals(excludeTest)) {
		return false;
	    }
	}
	return true;
    }
}
