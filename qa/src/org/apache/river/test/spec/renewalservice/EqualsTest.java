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

// net.jini
import net.jini.lease.LeaseRenewalService;

// java.rmi
import java.rmi.MarshalledObject;

// 
import org.apache.river.qa.harness.TestException;

// com.sun.qa.
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;

/**
 * EqualsTest asserts that LRS equals method for proxies works as expected.
 * Two proxy objects are equal if they are proxies for the same renewal
 * service.
 * 
 */
public class EqualsTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * Sets up the testing environment.
     */
    public Test construct(org.apache.river.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.construct(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "EqualsTest: In setup() method.");

       // capture an instance of the Properties file.
       QAConfig config = (QAConfig) getConfig();
       return this;
    }

    /**
     * This method asserts that two proxies for the same LRS service are
     * equal and two proxies for different LRS services are not.
     * 
     * <P>Notes:<BR>For more information see the LRS specification 
     * section 9.3 page 107.</P>
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "EqualsTest: In run() method.");

	/*
	 * To perform all equals tests we require 4 different proxy
	 * instances. Two from each service.
	 */
	MarshalledObject marshObj01 = new MarshalledObject(getLRS(0));
	LeaseRenewalService lrs01 = (LeaseRenewalService) marshObj01.get();
	LeaseRenewalService lrs03 = (LeaseRenewalService) marshObj01.get();

	MarshalledObject marshObj02 = new MarshalledObject(getLRS(1));
	LeaseRenewalService lrs02 = (LeaseRenewalService) marshObj02.get();

	/* ensure that comparing two proxies from different LRS services
	 * comes back false.
	 */
	if (lrs01.equals(lrs02)) {
	    String message = "Proxies for two different LR services ";
	    message += "match.";
	    throw new TestException(message);
	}

	// and test equals reflexive property
	if (lrs02.equals(lrs01)) {
	    String message = "The equals method() is not reflexive!";
	    throw new TestException(message);
	}

	/* ensure that comparing two proxies from the same LRS service
	 * comes back true.
	 */

	// service proxies from the same service must be equal
	if (lrs01.equals(lrs03) == false) {
	    String message = "Two proxies for the same LR service ";
	    message += "do not match.";
	    throw new TestException(message);
	}

	// and test the reflexive property
	if (lrs03.equals(lrs01) == false) {
	    String message = "The equals method() is not reflexive!";
	    throw new TestException(message);
	}

	// test hashCode implementation
	if (lrs01.hashCode() != lrs03.hashCode()) {
	    String message = "LRS bad hashCode() implementation!";
	    throw new TestException(message);
	}

	// and just to be absolutely certain test identity
	if (lrs01.equals(lrs01) == false) {
	    String message = "The equals method() is not enforce ";
	    message += "the identity property.";
	    throw new TestException(message);
	}
    }


} // EqualsTest

