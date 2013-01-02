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
import net.jini.core.lease.*;
import java.rmi.RemoteException;

/** This class is used to test that service lease renewal works as expected for
 *  N (currently N = 5) successive lease renewal attempts.
 *
 *  @see com.sun.jini.qa.harness.QATest
 *  @see com.sun.jini.test.spec.lookupservice.QATestRegistrar
 *  @see com.sun.jini.test.spec.lookupservice.QATestUtils
 */
public class MultipleSrvcLeaseRenewals extends QATestRegistrar {
    /** the expected number of matches when testing lookup by ID */
    private static int EXPECTED_N_MATCHES = 1;
    /* set lease duration to 3 minute */
   private final static long DEFAULT_LEASEDURATION
                                                = (3*QATestUtils.N_MS_PER_MIN);
    private final static int DEFAULT_LOOP_COUNT = 5;
    private static int  loopCount= DEFAULT_LOOP_COUNT;   
    private static long leaseDuration = DEFAULT_LEASEDURATION;
    private static long leaseWaitTime  = DEFAULT_LEASEDURATION *3/4;
    private static long halfDurationTime = DEFAULT_LEASEDURATION /2;
    private ServiceItem[] srvcItems ;
    private ServiceRegistration[] srvcRegs ;
    private Lease[] srvcLeases ;
    private ServiceTemplate[] srvcIDTmpls;
    private ServiceRegistrar proxy;
    private int nInstances = 0;
    private long leaseStartTime;

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Loads and instantiates all service 
     *  classes. Retrieves the proxy to the lookup Registrar. Establishes 
     *  an approximate service lease start time for each service item by 
     *  retrieving the current system time. Registers each service class 
     *  instance with a specified lease duration. Creates an array of 
     *  Leases in which each element contains the service lease of one 
     *  of the registered services. Creates an array of ServiceTemplates 
     *  in which each element contains the service ID of one of the 
     *  registered service items.
     *  @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        int i;
	super.construct(sysConfig);

	logger.log(Level.FINE, "MultipleSrvcLeaseRenewals : in setup() method.");

        nInstances = super.getNInstances();
	srvcItems = super.createServiceItems(TEST_SRVC_CLASSES);
	proxy = super.getProxy();
	srvcIDTmpls = new ServiceTemplate[nInstances];
	leaseStartTime = QATestUtils.getCurTime();
 	srvcRegs = registerAll(leaseDuration);
        srvcLeases = new Lease[srvcRegs.length];
	for(i=0; i<srvcLeases.length; i++) {
	    srvcLeases[i] = getRegistrationLease(srvcRegs[i]);
	}
	for(i=0; i<srvcIDTmpls.length; i++) {
	    srvcIDTmpls[i] = new ServiceTemplate(srvcRegs[i].getServiceID(),
                                                 null,null);
	}
        return this;
    }

    /** Executes the current QA test.
     *
     *  Repeats the following steps N times:
     *     Waits for three-fourths of the current lease duration time.
     *     Sets the new (approximate) lease start time to the current time.
     *     Renews all service leases; requesting the new lease duration.
     *     Verifies that the lease duration returned is the duration requested.
     *     Waits for one-half of the current lease duration time.
     *     Performs a simple lookup of each registered service item.
     *     Verifies that the set of service items returned by the lookup 
     *     operation equals the expected set of service items.
     *  @exception QATestException usually indicates test failure
     */

    /*  The time-line diagram below shows the steps of this test:
     *
     *                                                     Renewal
     *                                                     R4 |---------------|
     *                                         Renewal        :        ^      .
     *                                         R3 |---------------|    :      .
     *                             Renewal        :       ^       .    :      .
     *                             R2 |---------------|   :       .    :      .
     *                 Renewal        :       ^       .   :       .    :      .
     *                 R1 |---------------|   :       .   :       .    :      .
     *     Renewal        :       ^       .   :       .   :       .    :      .
     *     R0 |---------------|   :       .   :       .   :       .    :      .
     *        :       ^       .   :       .   :       .   :       .    :      .
  |---------------|   :       .   :       .   :       .   :       .    :      .
  0      0.5      1   :      1.5  :       2   :      2.5  :       3    :    3.5
     *                :           :           :           :            :
     *                :           :           :           :            :
     *               L0          L1          L2          L3           L4
     */
    public void run() throws Exception {
	logger.log(Level.FINE, "MultipleSrvcLeaseRenewals : in run() method.");
	logger.log(Level.FINE, "# of trials = " + loopCount);
	for(int i =0; i<loopCount; i++) {
	    logger.log(Level.FINE, "\n**** Start trial #" + i + "****");
	    logger.log(Level.FINE, "Waiting 3/4 of lease duration time.");
	    QATestUtils.computeDurAndWait(leaseStartTime, leaseWaitTime);
	    leaseStartTime = QATestUtils.getCurTime();
	    logger.log(Level.FINE, "Renewing leases ...");
	    QATestUtils.doRenewLease(srvcLeases, leaseDuration);
	    logger.log(Level.FINE, "Verifying leases against minimum " +
			      "expiration time.");
	    QATestUtils.verifyLeases(srvcLeases,
				     leaseStartTime + leaseDuration);
	    logger.log(Level.FINE, "Waiting 1/2 of the lease duration time.");
	    QATestUtils.computeDurAndWait(leaseStartTime, 
					  halfDurationTime);
	    logger.log(Level.FINE, "Asserting that each service proxy " +
			      "can still be found.");
	    QATestUtils.doLookup(srvcItems, srvcIDTmpls, proxy ); 
	}
    }
}
