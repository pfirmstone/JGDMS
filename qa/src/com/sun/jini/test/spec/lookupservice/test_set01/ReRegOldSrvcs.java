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
import com.sun.jini.test.spec.lookupservice.QATestUtils;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceTemplate;
import java.rmi.RemoteException;

/** This class is used to verify that when an existing service is re-registered
 *  in the Lookup Service, the registration of that service does not affect
 *  any other services already registered. That is, after the existing service
 *  is re-registered, we should still be able to successfully lookup all 
 *  services in the Lookup Service.
 *
 *  @see com.sun.jini.qa.harness.QATest
 *  @see com.sun.jini.test.spec.lookupservice.QATestRegistrar
 *  @see com.sun.jini.test.spec.lookupservice.QATestUtils
 */
public class ReRegOldSrvcs extends QATestRegistrar {

    private ServiceItem[] srvcItems;
    private ServiceRegistration[] srvcRegs;
    private ServiceTemplate[] tmpls;
    private ServiceRegistrar proxy;
    private int nInstances = 0;

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Creates all the service items. Registers
     *  all service items -- requesting ANY service ID. Creates a set of
     *  templates with which to perform lookups (each should be created
     *  with one of the service IDs of the registered services; the service
     *  types and attribute templates should be left null). Retrieves each
     *  service ID returned by the registration process and associate it
     *  with its corresponding service item (this is so that when the service
     *  is re-registered, it is registered with that same service ID).
     *  @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public void setup(QAConfig sysConfig) throws Exception {
        int i;
	super.setup(sysConfig);
        nInstances = super.getNInstances();
	srvcItems = super.createServiceItems(TEST_SRVC_CLASSES);
	srvcRegs = super.registerAll();
        for (i=0; i<srvcItems.length; i++ ) {
            srvcItems[i].serviceID = srvcRegs[i].getServiceID();
	}
	proxy = super.getProxy();
	tmpls = new ServiceTemplate[nInstances];
	for(i=0; i<tmpls.length; i++) {
	    tmpls[i] = new ServiceTemplate(srvcRegs[i].getServiceID(),
                                           null,null);
	}
    }

    /** Executes the current QA test.
     *
     *  For each service registered, re-registers the service.
     *     For each service re-registered:
     *       1. Performs a simple lookup by service ID and verifies that 
     *          what is returned is what is expected.
     *       2. Performs a "match" lookup and verifies that what is returned is
     *          what is expected.
     *  @exception QATestException usually indicates test failure
     */
    public void run() throws Exception {
	for (int i=0; i<srvcItems.length; i++ ) {
	    srvcRegs[i] = registerItem(srvcItems[i],Long.MAX_VALUE, proxy);
	}
	QATestUtils.doLookup(srvcItems,tmpls,proxy);
    }
}
