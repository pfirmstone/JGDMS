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
package com.sun.jini.test.spec.lookupservice.test_set00;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import java.util.logging.Level;

import com.sun.jini.test.spec.lookupservice.QATestRegistrar;
import com.sun.jini.test.spec.lookupservice.QATestUtils;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceTemplate;
import java.rmi.RemoteException;

/** This class is used to test that every service item registered with
 *  the Lookup service can be successfully looked up using only its service ID.
 *
 *  @see com.sun.jini.qa.harness.TestEnvironment
 *  @see com.sun.jini.test.spec.lookupservice.QATestRegistrar
 *  @see com.sun.jini.test.spec.lookupservice.QATestUtils
 */
public class LookupBySrvcID extends QATestRegistrar {

    private ServiceItem[] srvcItems ;
    private ServiceRegistration[] srvcRegs ;
    private ServiceTemplate[] srvcIDTmpls;
    private ServiceRegistrar proxy;
    private int nInstances = 0;

    /** the expected number of matches when testing lookup by ID */
    private static int EXPECTED_N_MATCHES = 1;

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Loads and instantiates all service 
     *  classes; then registers each service class instance with the maximum
     *  service lease duration. Creates an array of ServiceTemplates in 
     *  which each element contains the service ID of one of the registered
     *  service items.
     */
    public Test construct(QAConfig sysConfig) throws Exception {

	super.construct(sysConfig);

	logger.log(Level.FINE, "in setup() method.");

        nInstances = super.getNInstances();

	srvcItems = super.createServiceItems(TEST_SRVC_CLASSES);
	srvcRegs = super.registerAll();
	proxy = super.getProxy();

	srvcIDTmpls = new ServiceTemplate[nInstances];
	for(int i=0; i<srvcIDTmpls.length; i++) {
	    srvcIDTmpls[i] = new ServiceTemplate(srvcRegs[i].getServiceID(),
                                                 null,null);
	}
        return this;
    }

    /** Executes the current QA test.
     *
     *  For each service registered:  
     *      1. Performs a simple lookup using the corresponding template 
     *         created during construct and then tests that the object returned 
     *         equals the service item that was registered with the 
     *         corresponding service ID.
     *      2. Performs a match lookup using the corresponding template 
     *         created during construct and then tests that the number of matches
     *         found equals 1; and that the object returned equals the 
     *         service item that was registered with the corresponding 
     *         service ID.
     */
    public void run() throws Exception {
	logger.log(Level.FINE, "in run() method.");
	QATestUtils.doLookup(srvcItems,srvcIDTmpls,proxy);
    }
}
