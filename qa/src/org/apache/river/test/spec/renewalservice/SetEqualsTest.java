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


package org.apache.river.test.spec.renewalservice;

import java.util.logging.Level;

// java.rmi
import net.jini.io.MarshalledInstance;

// net.jini
import net.jini.core.lease.Lease;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

// 
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.Test;

// com.sun.qa.
import org.apache.river.qa.harness.QATestEnvironment;

/**
 * SetEqualsTest asserts that two lease renewal set proxies are equal
 * only if they are proxies for the same set created by the same
 * LeaseRenewalService.
 * 
 */
public class SetEqualsTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * the LRS proxies for creating renewal sets
     */
    private  LeaseRenewalService lrs01 = null;
    private  LeaseRenewalService lrs02 = null;

    /**
     * the renewal sets for equality comparison
     */
    private  LeaseRenewalSet set1 = null;
    private  LeaseRenewalSet set2 = null;
    private  LeaseRenewalSet set3 = null;
    private  LeaseRenewalSet set4 = null;

    /**
     * Sets up the testing environment.
     */
    public Test construct(org.apache.river.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.construct(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "SetEqualsTest: In setup() method.");

       // grab the LRS proxies
       lrs01 = getLRS(0);
       lrs02 = getLRS(1);

       // create the necessary sets for testing
       set1 = lrs01.createLeaseRenewalSet(Lease.FOREVER);
       set1 = prepareSet(set1);
       set2 = lrs02.createLeaseRenewalSet(Lease.FOREVER);
       set2 = prepareSet(set2);
       set3 = lrs01.createLeaseRenewalSet(Lease.FOREVER);
       set3 = prepareSet(set3);
       MarshalledInstance marshObj = new MarshalledInstance(set1);
       set4 = (LeaseRenewalSet) marshObj.get(false);
       set4 = prepareSet(set4);
       return this;
    }

    /**
     * This method asserts that two lease renewal set proxies are equal
     * only if they are proxies for the same set created by the same
     * LeaseRenewalService.
     * 
     * <P>Notes:<BR>For more information see the LRS specification 
     * section 9.3 page 107.</P>
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "SetEqualsTest: In run() method.");

	// assert that two sets from different services are different
	logger.log(Level.FINE, "Assert sets from different LRSs are different");
	if (set1.equals(set2) == true) {
	    String message = "Sets from different LRS match for";
	    message += " equality";
	    throw new TestException(message);
	}

	// assert that two sets from the same services are different
	logger.log(Level.FINE, "Assert sets from the same LRSs are different");
	if (set1.equals(set3) == true) {
	    String message = "Two sets from same LRS match for";
	    message += " equality";
	    throw new TestException(message);
	}

	// assert that same set from the same service is equal to itself
	logger.log(Level.FINE, "Assert one set from the same LRS is " +
			  " equal to itself.");
	if (set1.equals(set1) == false) {
	    String message = "Same set from same LRS fails match for";
	    message += " equality";
	    throw new TestException(message);
	}

	// assert that set equality is preserved across marshalling
	logger.log(Level.FINE, "Assert one set from the same LRS is " +
			  " equal to itself after it is unmarshalled.");
	if (set1.equals(set1) == false) {
	    String message = "Set equality is not preserved across the";
	    message += " marshalling process.";
	    throw new TestException(message);
	}
    }
} // SetEqualsTest

