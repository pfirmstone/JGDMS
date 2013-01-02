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
package com.sun.jini.test.spec.lookupservice.test_set03;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import java.util.logging.Level;
import com.sun.jini.qa.harness.TestException;

import com.sun.jini.test.spec.lookupservice.QATestRegistrar;
import com.sun.jini.test.spec.lookupservice.QATestUtils;
import java.rmi.RemoteException;
import java.io.IOException;
import net.jini.lookup.DiscoveryAdmin;

/** This class is used to perform a simple verification of the following
 *  methods from the DiscoveryAdmin interface: getUnicastPort() and
 *  setUnicastPort(). This class simply sets the value of the unicast port
 *  number to a new value and then retrieves that value to verfiy that 
 *  the original port number was indeed modified in the Registrar.
 *
 *  @see net.jini.lookup.DiscoveryAdmin
 *  @see com.sun.jini.qa.harness.QATest
 *  @see com.sun.jini.test.spec.lookupservice.QATestRegistrar
 *  @see com.sun.jini.test.spec.lookupservice.QATestUtils
 */
public class SimpleUnicastPort extends QATestRegistrar {

    private static final int EXPECTED_OLD_PORT_NUMBER = 0;
    private static final int EXPECTED_NEW_PORT_NUMBER = 9998;
    private DiscoveryAdmin adminProxy;

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Retrieves the proxy to the remote
     *  methods of the DiscoveryAdmin interface.
     *  @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	adminProxy = (DiscoveryAdmin) super.getAdminProxy();
        return this;
    }

    /** Executes the current QA test.
     *
     *  Retrieves the current value of the unicast port number and 
     *  verifies that it equals the expected port number. Sets the  
     *  unicast port number to a new value. Retrieves the new port number
     *  and verifies that it equals the expected value.
     *  @exception QATestException usually indicates test failure
     */
    public void run() throws Exception {
	int unicastPort = 0;
	unicastPort = adminProxy.getUnicastPort();
	if (unicastPort != EXPECTED_OLD_PORT_NUMBER) {
	    throw new TestException("OLD Port Number ("+unicastPort+
				    ") != Expected OLD Port Number ("
				    +EXPECTED_OLD_PORT_NUMBER+")");
	}
	adminProxy.setUnicastPort(EXPECTED_NEW_PORT_NUMBER);
	unicastPort = adminProxy.getUnicastPort();
	if (unicastPort != EXPECTED_NEW_PORT_NUMBER) {
	    throw new TestException("NEW Port Number ("+unicastPort+
				    ") != Expected NEW Port Number ("
				    +EXPECTED_NEW_PORT_NUMBER+")");
	}
    }
}
