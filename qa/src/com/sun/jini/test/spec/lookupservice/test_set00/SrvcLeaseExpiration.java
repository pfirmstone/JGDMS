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

import com.sun.jini.qa.harness.TestException;
import java.util.logging.Level;

import com.sun.jini.test.spec.lookupservice.QATestRegistrar;
import com.sun.jini.test.spec.lookupservice.QATestUtils;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceTemplate;
import java.rmi.RemoteException;

/** This class is used to test that any service item registered with the Lookup
 *  service can no longer be successfully looked up after the service
 *  item's lease has expired.
 *
 *  @see com.sun.jini.qa.harness.QATest
 *  @see com.sun.jini.test.spec.lookupservice.QATestRegistrar
 *  @see com.sun.jini.test.spec.lookupservice.QATestUtils
 */
public class SrvcLeaseExpiration extends QATestRegistrar {

    /** the expected number of matches when testing lookup by ID */
    private static int EXPECTED_N_MATCHES = 1;
    /* lease duration to 1 minute */
    private final static long DEFAULT_LEASEDURATION = 60000;
    private final static int DEFAULT_LOOP_COUNT = 5;
    private static int  loopCount= DEFAULT_LOOP_COUNT;   
    private static long leaseDuration = DEFAULT_LEASEDURATION;
    private static long leaseWaitTime  = DEFAULT_LEASEDURATION *3/4;
    private static long lookupWaitTime = DEFAULT_LEASEDURATION /2;
    private ServiceItem[] srvcItems ;
    private ServiceRegistration[] srvcRegs ;
    private ServiceTemplate[] srvcIDTmpls;
    private ServiceRegistrar proxy;
    private int nInstances = 0;
    private long leaseStartTime;

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Loads and instantiates all service 
     *  classes. Registers each service class instance with a specified 
     *  lease duration. Retrieves the proxy to the lookup Registrar. 
     *  Creates an array of ServiceTemplates in which each element contains 
     *  the service ID of one of the registered service items. Establishes 
     *  an approximate service lease start time for each service item by 
     *  retrieving the current system time.
     */
    public void setup(QAConfig sysConfig) throws Exception {

	super.setup(sysConfig);

	logger.log(Level.FINE, "in setup() method.");

        nInstances = super.getNInstances();

	srvcItems = super.createServiceItems(TEST_SRVC_CLASSES);
	srvcRegs = super.registerAll(leaseDuration);
	proxy = super.getProxy();

	srvcIDTmpls = new ServiceTemplate[nInstances];
	for(int i=0; i<srvcIDTmpls.length; i++) {
	    srvcIDTmpls[i] = new ServiceTemplate(srvcRegs[i].getServiceID(),
                                                 null,null);
	}
	leaseStartTime = QATestUtils.getCurTime();
    }

    /** Executes the current QA test.
     *
     *  Performs a simple lookup of each registered service item.
     *  Verifies that the set of service items returned by the lookup 
     *  operation equals the expected set of service items.
     *  Waits a specified amount of time so as to guarantee that each
     *  service lease expires.
     *  Performs both a simple and a match lookup of each registered service
     *  item.
     *  For each lookup performed, verifies that a null service, as well as
     *  zero matches, are returned.
     */

    /*  The time-line diagram below shows the steps of this test:
     *
     *           |-------------------------------------------------------|
     *           :         :         ^         
     * |-------------------|         :         
     * 0    ^   0.5        1         :        
     *      :              :         :
     *      :              :         :
     *   Lookup          Lease    Lookup
     *                  Expires
     */
    public void run() throws Exception {
	logger.log(Level.FINE, "SrvcLeaseExpiration : in run() method.");
	logger.log(Level.FINE, "Performing lookup ...");
	QATestUtils.doLookup(srvcItems, srvcIDTmpls, proxy );

	logger.log(Level.FINE, "Waiting " + (leaseDuration*2) + 
			  " milliseconds for service leases to expire.");
	QATestUtils.computeDurAndWait(leaseStartTime, leaseDuration*2);

	logger.log(Level.FINE, "Checking that no services can be found.");
	doLookupNoMatch();
    }
 
    /* Perform both a simple and a match lookup using the ServiceTemplate
     * created during setup. Verifies that every simple lookup returns
     * a null object; and every match lookup returns zero matches.
     */
    private void doLookupNoMatch() throws TestException, RemoteException {
        Object serviceObj = null;
        ServiceMatches matches = null;
        for (int i=0; i<srvcIDTmpls.length; i++) {
	    if(proxy.lookup(srvcIDTmpls[i]) != null) {
		throw new TestException("srvcIDTmpls["+i+"] != null");
	    }
	    matches = proxy.lookup(srvcIDTmpls[i],Integer.MAX_VALUE);
            if ( matches.totalMatches != 0) {
                throw new TestException("totalMatches != 0");
	    }
	}
    }
}
