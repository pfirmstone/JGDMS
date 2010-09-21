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
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistration;

/** This class is used to test that the process of registering a service
 *  that already EXISTS in the Lookup service is an IDEMPOTENT process.
 *  
 *  When a service registration is requested of the Lookup service by a
 *  client process, there are a number of conditions that can cause the
 *  client to view the registration as a failure; and as a result, re-register
 *  the service. Some of those conditions are:
 *  
 *     -- The server or the network could go down before the registration
 *        request arrives on the "server side". In this case, the state of
 *        the server never changes.
 *     -- The server or network could go down after the server receives the
 *        request, but before the server sends a reply. In this case, the
 *        state of the server can change, but the client is never aware of
 *        the change.
 *        
 *  No matter what the cause is, the client will receive a RemoteException
 *  if the registration is not successful; that is, if the invocation of
 *  of the remote registration method does not succeed. When such failures
 *  occur, the client will re-register the service. It is a requirement that
 *  the register() method of the Lookup service be defined "so that it can
 *  be used in an idempotent fashion. Specifically, if a call results in a
 *  RemoteException (in which case the item might or might not have been 
 *  registered), the caller can simply repeat the call with the same
 *  parameters", and the results will be the same as if the registration
 *  succeeded on the first call.
 *  
 *  This test will NOT bring down the network; nor will the server code be
 *  modified to simulate the conditions described above. This test will
 *  register a set of services and then "pretend" that the registration
 *  did not succeed. It will then re-register those same services and 
 *  verify that the second registration produced the same results as the
 *  first registration. It is in this way that the conditions described
 *  above will be simulated and the idempotency of the registration process
 *  will be proven.
 *
 *  Note that the initial registration sets ASSIGN_SERVICE_ID in the service
 *  ID argument. After that, both the registration and re-registration of
 *  the existing services include the corresponding service IDs assigned
 *  during the initial registration. This is done because we are testing
 *  for idempotency of the registration of EXISTING services.
 *
 *  @see com.sun.jini.qa.harness.QATest
 *  @see com.sun.jini.test.spec.lookupservice.QATestRegistrar
 *  @see com.sun.jini.test.spec.lookupservice.QATestUtils
 */
public class IdempotentOldSrvcReg extends QATestRegistrar {

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Creates all the service items. Registers
     *  all service items -- requesting ANY service ID. Retrieves each
     *  service ID returned by the registration process and associates it
     *  with its corresponding service item (this is so that when the service
     *  is re-registered, it is registered with that same service ID). 
     */
    public void setup(QAConfig sysConfig) throws Exception {
        ServiceItem[] srvcItems ;
	super.setup(sysConfig);
	srvcItems = super.createServiceItems(TEST_SRVC_CLASSES);
	ServiceRegistration[] srvcRegs = super.registerAll();
        for ( int i = 0; i < srvcItems.length; i++ ) {
            srvcItems[i].serviceID = srvcRegs[i].getServiceID();
	}
    }

    /** Executes the current QA test.
     *
     *  1. Registers each of the services created and registered above -- 
     *     requesting the SAME service ID that was returned by the initial
     *     registration of the service.
     *  2. RE-registers each of the services registered above -- requesting
     *     the SAME service ID returned by the registration in step 1.
     *  3. Verifies that the number of services registered originally equals
     *     the number of services re-registered.
     *  4. Verifies that the service IDs of each service re-registered equals
     *     the service ID of the corresponding original service.
     */
    public void run() throws Exception {
	/* register the EXISTING services */
	ServiceRegistration[] oldSrvcRegs = super.registerAll();
	/* "pretend" the registration process failed ==> must re-register */
	ServiceRegistration[] newSrvcRegs = super.registerAll();
	if ( oldSrvcRegs.length != newSrvcRegs.length ) {
	    throw new TestException
	              ("Original number of services registered ("
	               +oldSrvcRegs.length+
	               ") != number of services RE-registered ("
	               +newSrvcRegs.length+")");
	} else {
	    for (int i = 0; i < oldSrvcRegs.length; i++ ) {
	        if ( !(oldSrvcRegs[i].getServiceID().equals
	                                     (newSrvcRegs[i].getServiceID())) )
	        {
	            throw new TestException
	                      ("Index " + i + "Service IDs not equal ("
	                       +oldSrvcRegs[i].getServiceID()+
	                       " & "+newSrvcRegs[i].getServiceID()+")");
		}
	    }
	}
    }
}
