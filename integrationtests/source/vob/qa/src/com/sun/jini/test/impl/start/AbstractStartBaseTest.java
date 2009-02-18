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

package com.sun.jini.test.impl.start;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.QATest;

/**
 * This class is an abstract class that acts as the base class which
 * most, if not all, tests of the "start" tests
 * class should extend.
 * 
 * <p>
 * This class provides an implementation of the <code>setup</code> method
 * which performs standard functions related to the initialization of the
 * system state necessary to execute the test.
 *
 * Any test class that extends this class is required to implement the 
 * <code>run</code> method which defines the actual functions that must
 * be executed in order to verify the assertions addressed by that test.
 * 
 *
 * @see com.sun.jini.qa.harness.QAConfig
 * @see com.sun.jini.qa.harness.QATest
 * @see com.sun.jini.qa.harness.QATestUtil
 */
abstract public class AbstractStartBaseTest extends QATest {

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p>
     *   <ul>
     *     <li> retrieves configuration values needed by the current test
     *     <li> starts the shared group (and thus the activation system)
     *          if the shared parameter is true
     *   </ul>
     * The shared group is explicitly started because the harness is designed
     * to start it lazily.
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        logger.log(Level.FINE, "AbstractBaseTest:setup()");
        getSetupInfo();
	if (config.getBooleanConfigVal("com.sun.jini.qa.harness.shared",
				       true)) 
	{
	    manager.startService("sharedGroup");
	}
    }//end setup

    /* Retrieve (and display) configuration values for the current test */
    private void getSetupInfo() {
        /* begin harness info */
        logger.log(Level.FINE, ""+": ----- Harness Info ----- ");
        String harnessCodebase = System.getProperty("java.rmi.server.codebase",
                                                    "no codebase");
        logger.log(Level.FINE, ""+": harness codebase      -- "
                                        +harnessCodebase);

        String harnessClasspath = System.getProperty("java.class.path",
                                                     "no classpath");
        logger.log(Level.FINE, ""+": harness classpath     -- "
                                        +harnessClasspath);
        /* end harness info */

    }//end getSetupInfo

    public static boolean verifyClassNotFoundException(Exception actual) {
        Throwable cause = actual;
        while (cause.getCause() != null) {
           cause = cause.getCause();
        }
        return (cause instanceof ClassNotFoundException);
    }

    public static boolean verifyNoSuchMethodException(Exception actual) {
        Throwable cause = actual;
        while (cause.getCause() != null) {
           cause = cause.getCause();
        }
        return (cause instanceof NoSuchMethodException);
    }


} //end class AbstractBaseTest


