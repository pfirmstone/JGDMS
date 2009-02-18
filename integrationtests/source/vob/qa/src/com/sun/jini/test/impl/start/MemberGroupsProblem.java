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

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

import java.rmi.RemoteException;

/**
 * This class verifies that the <code>ServiceStarter</code> utility correctly
 * handles exceptional conditions related to the setting of the member
 * groups of a lookup service that is started. This test uses a simulated
 * lookup service to purposely cause an exceptional condition at the
 * point in the start process where the member groups of the lookup service
 * are being set. This class then verifies that the exception is the
 * expected exception.
 *
 */
public class MemberGroupsProblem extends AbstractBaseTest {

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
	System.setProperty("com.sun.jini.start.membergroups.problem", "true");
    }//end setup

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> starts a single lookup service and verifies that the exception
     *         occurs
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        try {
            startInitLookups();
            throw new TestException(" -- no Exception");
        } catch(TestException e) { // harness wraps the cause
            String expectedStr = "java.rmi.RemoteException: Problem setting "
                                 +"the member groups for "
                                 +"this lookup service";
	    Exception ex = (Exception) e.getCause();
	    if (! (ex instanceof RemoteException)) {
		throw new TestException("Expected RemoteException, got " 
					+ ex.getClass(), ex);
	    }
	    if( ! (ex.toString()).equals(expectedStr) ) {
		throw new TestException(" -- RemoteException with "
					+"un-expected exception string --> "
					+ex.toString(), ex);
	    }//endif
        }
    }//end run

}//end class MemberGroupsProblem

