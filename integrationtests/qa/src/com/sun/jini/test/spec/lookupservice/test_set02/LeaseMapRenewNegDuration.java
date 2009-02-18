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

import java.util.logging.Level;
import com.sun.jini.qa.harness.TestException;

import com.sun.jini.test.spec.lookupservice.QATestRegistrar;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lease.LeaseMapException;
import net.jini.core.entry.Entry;
import java.rmi.RemoteException;

/**
 * This class is used to verify that doing a LeaseMap.renewAll with a
 * negative lease duration results in an IllegalArgumentException in
 * the LeaseMapException.
 */
public class LeaseMapRenewNegDuration extends QATestRegistrar {

    private ServiceRegistration reg1;
    private ServiceRegistration reg2;
    private LeaseMap lmap;

    public void setup(QAConfig sysConfig) throws Exception {
	super.setup(sysConfig);
	reg1 = registerItem(new ServiceItem(null, new Long(0), null),
			    getProxy());
	reg2 = registerItem(new ServiceItem(null, new Long(1), null),
			    getProxy());
	lmap = prepareRegistrationLeaseMap(getRegistrationLease(reg1).createLeaseMap(-500));
	lmap.put(getRegistrationLease(reg2), new Long(300));
    }

    public void run() throws Exception {
	try {
	    lmap.renewAll();
	    throw new TestException("renewAll did not throw LeaseMapException");
	} catch (LeaseMapException e) {
	    if (!(e.exceptionMap.get(getRegistrationLease(reg1)) instanceof
		  IllegalArgumentException))
		throw new TestException(
			     "renewAll did not return IllegalArgumentException", e);
	    if (e.exceptionMap.size() != 1)
		throw new TestException(
			     "renewAll returned too many exceptions", e);
	    if (lmap.size() != 1 || !lmap.containsKey(getRegistrationLease(reg2)))
		throw new TestException(
			  "renewAll did not update map properly", e);
	}
    }
}
