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

package com.sun.jini.test.spec.joinmanager;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;

import net.jini.lookup.JoinManager;
import net.jini.config.ConfigurationException;

import java.io.IOException;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that the <code>equals</code> method returns
 * <code>true</code> if and only if two instances of this class refer to
 * the same object. That is, x and y are equal instances of the
 * <code>JoinManager</code> if and only if x == y is true.
 * 
 */
public class Equality extends AbstractBaseTest {

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> creates an instance of the JoinManager using the version of
     *          the constructor that takes ServiceIDListener (callback),
     *          inputting an instance of a test service and null to all
     *          other arguments
     *     <li> creates an instance of the JoinManager using the version of
     *          the constructor that takes a service ID, inputting 
     *          an instance of a test service and null to all other arguments
     *   </ul>
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        logger.log(Level.FINE, "creating a callback join manager ...");
        joinMgrCallback = new JoinManager(testService,
					  serviceAttrs,
					  callback,
                                          discoveryMgr,
					  leaseMgr,
					  sysConfig.getConfiguration());
        joinMgrList.add(joinMgrCallback);
        logger.log(Level.FINE, "creating a service ID join manager ...");
        joinMgrSrvcID = new JoinManager(testService,
					serviceAttrs,
					serviceID,
                                        discoveryMgr,
					leaseMgr,
					sysConfig.getConfiguration());
        joinMgrList.add(joinMgrSrvcID);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p>
     *   For each instance of the <code>JoinManager</code> utility class
     *   created during the construct process, verifies that the 
     *   <code>equals</code> method returns the appropriate result
     *   depending on the particular instances of <code>JoinManager</code>
     *   being compared.
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Callback join manager */
        logger.log(Level.FINE, "testing the callback join manager ...");
        JoinManager newJM = null;
	newJM = new JoinManager(testService,
				serviceAttrs,
				callback,
				discoveryMgr,
				leaseMgr,
				getConfig().getConfiguration());
	joinMgrList.add(newJM);
        if( !satisfiesEqualityTest(joinMgrCallback,newJM) ) { 
            throw new TestException(
                                 "failed equality test -- DIFFERENT instances "
                                 +"of callback join manager ");
        }//endif
        if( !satisfiesEqualityTest(joinMgrCallback,joinMgrCallback) ) { 
            throw new TestException(
                                 "failed equality test -- SAME instance "
                                 +"of callback join manager ");
        }//endif
        logger.log(Level.FINE, "callback join manager passed equality test");
        /* Service ID join manager */
        logger.log(Level.FINE, "testing the service ID join manager ...");
	newJM = new JoinManager(testService,
				serviceAttrs,
				serviceID,
				discoveryMgr,
				leaseMgr,
				getConfig().getConfiguration());
	joinMgrList.add(newJM);
        if( !satisfiesEqualityTest(joinMgrSrvcID,newJM) ) { 
            throw new TestException(
                                 "failed equality test -- DIFFERENT instances "
                                 +"of callback join manager ");
        }//endif
        if( !satisfiesEqualityTest(joinMgrSrvcID,joinMgrSrvcID) ) { 
            throw new TestException(
                                 "failed equality test -- SAME instance "
                                 +"of service ID join manager ");
        }//endif
        logger.log(Level.FINE, "service ID join manager passed equality test");
    }//end run

} //end class Equality


