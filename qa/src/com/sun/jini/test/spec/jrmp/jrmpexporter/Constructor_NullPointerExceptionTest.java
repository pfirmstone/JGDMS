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
package com.sun.jini.test.spec.jrmp.jrmpexporter;

// net.jini
import net.jini.jrmp.JrmpExporter;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.jrmp.util.AbstractTestBase;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     If ActivationID parameter in JrmpExporter's constructor is null
 *     then NullPointerException will be thrown.
 *
 * Test Cases
 *   This test uses constructors of JrmpExporter accepting ActivationID
 *   parameter.
 *
 * Action
 *   For each test case the test performs the following steps:
 *     1) construct a JrmpExporter using null ActivationID parameter
 *     2) assert that NullPointerException will be thrown
 * </pre>
 */
public class Constructor_NullPointerExceptionTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        cId = null;
	
	try {
            createJrmpExporter();

	    // FAIL
	    throw new TestException(
	            "No exceptions were thrown while constructing "
		    + "JrmpExporter with null ActivationID parameter "
		    + "while NullPointerException was expected "
		    + "to be thrown.");
        } catch (NullPointerException npe) {
	    // PASS
	    logger.fine("NullPointerException was thrown during JrmpExporter "
	            + "construction with null ActivationID parameter "
		    + "as expected.");
        }
    }
}
