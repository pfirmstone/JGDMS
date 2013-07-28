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
package com.sun.jini.test.spec.lookupservice.test_set02;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import java.util.logging.Level;
import com.sun.jini.qa.harness.TestException;

import java.rmi.RemoteException;
import java.io.IOException;
import com.sun.jini.test.spec.lookupservice.QATestRegistrar;
import com.sun.jini.test.spec.lookupservice.QATestUtils;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.core.lease.*;
import net.jini.core.event.*;

/** This class is used to test the UnknownLeaseException throws by
 *  Service Lease cancel(). It create nInstances of ServiceLease, then
 *  wait for the leases to expire, then call cancel() on each lease.
 *  verify that we get UnknownLeaseException each time when calling
 *  cancel().
 */
public class ExpiredServiceLeaseCancel extends QATestRegistrar {
    /** the expected number of matches when testing lookup by ID */
    private static int EXPECTED_N_MATCHES = 1;
    /* lease duration to 10 seconds */
    private final static long DEFAULT_LEASEDURATION = 10000;
    private static long leaseDuration = DEFAULT_LEASEDURATION;
    private ServiceRegistration[] srvcRegs ;
    private ServiceItem[] srvcItems ;
    private Lease[] srvcLeases ;
    private ServiceRegistrar proxy;
    private int nInstances = 0;
    private long leaseStartTime;  
    
    public synchronized Test construct(QAConfig sysConfig) throws Exception {
 	super.construct(sysConfig);
        nInstances = super.getNInstances();
	srvcItems = super.createServiceItems(TEST_SRVC_CLASSES);
	proxy = super.getProxy();
 	srvcRegs = registerAll(leaseDuration);
        srvcLeases = new Lease[srvcRegs.length];

	for(int i=0; i<srvcLeases.length; i++) {
	    srvcLeases[i] = getRegistrationLease(srvcRegs[i]);
	}
	leaseStartTime = QATestUtils.getCurTime();
        return this;
    }

    /** Executes the current QA test. */
    public synchronized void run() throws Exception {
	QATestUtils.computeDurAndWait(leaseStartTime, leaseDuration + 1000, 0, this);
	doLeaseCancel();
    }

    private void doLeaseCancel() throws Exception {
	for(int i=0; i<srvcLeases.length; i++) {
	    try {
		srvcLeases[i].cancel();
                throw new TestException("UnknownLeaseException was not thrown");
	    } catch (UnknownLeaseException e) {
	    }
	}
    }
}
