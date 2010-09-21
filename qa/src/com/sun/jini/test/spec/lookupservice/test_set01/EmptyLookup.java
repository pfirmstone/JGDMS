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
package com.sun.jini.test.spec.lookupservice.test_set01;
import com.sun.jini.qa.harness.QAConfig;

import java.util.logging.Level;
import com.sun.jini.qa.harness.TestException;

import com.sun.jini.test.spec.lookupservice.QATestRegistrar;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceTemplate;
import java.rmi.RemoteException;

/** This class is used to verify that when a lookup is performed using an
 *  "empty" template (a template created with all entries null) and ZERO 
 *  maximum matches requested, the total number of service items registered 
 *  in the Lookup Service will be returned; including the Registrar Service 
 *  itself.
 *
 *  @see com.sun.jini.qa.harness.QATest
 *  @see com.sun.jini.test.spec.lookupservice.QATestRegistrar
 *  @see com.sun.jini.test.spec.lookupservice.QATestUtils
 */
public class EmptyLookup extends QATestRegistrar {

    private ServiceTemplate tmpl;
    private ServiceRegistrar proxy;

    /* The number of matches expected to be returned by lookup */
    private int expectedNMatches;

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Creates all the service items. Registers
     *  all service items -- requesting ANY service ID. Creates a template
     *  with all entries null, that will be used in a lookup; and that will 
     *  return all items registered.
     */
    public void setup(QAConfig sysConfig) throws Exception {
	super.setup(sysConfig);
        /* add 1 to include the Registrar Service itself */
        expectedNMatches = 1+super.getNInstances();
	ServiceItem[] srvcItems = super.createServiceItems(TEST_SRVC_CLASSES);
	ServiceRegistration[] srvcRegs = super.registerAll();
	proxy = super.getProxy();
	tmpl = new ServiceTemplate(null,null,null);
    }

    /** Executes the current QA test.
     *
     *  Performs a match lookup using the template created during setup and 
     *  zero maximum matches. Verifies that the expected number of services
     *  are returned by the call to lookup().
     */
    public void run() throws Exception {
	ServiceMatches matches = null;
	matches = proxy.lookup(tmpl,0);
	if (matches.totalMatches != expectedNMatches) {
	    throw new TestException
	              ("totalMatches ("+matches.totalMatches+
	               ") != expectedNMatches ("+expectedNMatches+")");
	}
    }
}
