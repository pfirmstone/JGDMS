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

// java.io
import java.io.IOException;

// java.rmi
import java.rmi.MarshalledObject;

// java.util
import java.util.StringTokenizer;

// net.jini
import net.jini.core.lease.Lease;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;
import net.jini.lease.LeaseUnmarshalException;

// 
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.Test;

// org.apache.river.qa
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.test.share.OpCountingOwner;
import org.apache.river.test.share.TestLease;
import org.apache.river.test.share.TestLeaseProvider;
import org.apache.river.test.share.UnreadableTestLease;
import org.apache.river.test.share.UnreadableTestLeaseFactory;

/**
 * Assert that if none of the leases in the array can be de-serialized
 * a LeaseUnmarhalException is thrown and getMarshalledLeases method
 * returns a 0 length array.
 * 
 */
public class LeaseUnmarshalNoneTest 
          extends AbstractLeaseRenewalServiceTest {
    
    /**
     * Provides leases for this test. 
     */
    private TestLeaseProvider unreadableLeaseProvider = null;

    /**
     *  The owner (aka landlord) of the test leases 
     */
    private OpCountingOwner leaseOwner = null;

    /**
     *  The LeaseRenewalManager used for LRS impls that grant only short leases
     */
    private LeaseRenewalManager lrm = null;

    /**
     * Sets up the testing environment.
     */
    public Test construct(org.apache.river.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.construct(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "LeaseUnmarshalNoneExceptionTest:" +
			 " In setup() method.");

       // instantiate a lease provider
       unreadableLeaseProvider = 
	   new TestLeaseProvider(2, UnreadableTestLeaseFactory.class);

       // create an owner to for testing definite exceptions
       leaseOwner = new OpCountingOwner(Lease.FOREVER);

       // create lease renewal manager for wider use across implementations
       lrm = new LeaseRenewalManager(sysConfig.getConfiguration());
       return this;
    }

    /**
     * Assert that if none of the leases in the array can be de-serialized
     * a LeaseUnmarhalException is thrown and getMarshalledLeases method
     * returns a 0 length array.
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "LeaseUnmarshalNoneExceptionTest:" +
			  " In run() method.");

	// grab the ever popular LRS
	LeaseRenewalService lrs = getLRS();

	// create a renewal set
	logger.log(Level.FINE, "Creating renewal set with lease duration of " +
			  "Lease.FOREVER.");
	long renewSetDur = Lease.FOREVER;
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(renewSetDur);
	set = prepareSet(set);
	lrm.renewFor(prepareLease(set.getRenewalSetLease()), renewSetDur, null);

	// create 2 unreadable test leases to be managed
	logger.log(Level.FINE, "Creating an unreadable lease to be managed.");
	logger.log(Level.FINE, "Duration == Lease.FOREVER");
	TestLease specialLease01 = 
	    unreadableLeaseProvider.createNewLease(leaseOwner, 
						   Lease.FOREVER);

	logger.log(Level.FINE, "Creating an unreadable lease to be managed.");
	logger.log(Level.FINE, "Duration == Lease.FOREVER");
	TestLease specialLease02 = 
	    unreadableLeaseProvider.createNewLease(leaseOwner, 
						   Lease.FOREVER);
	// start managing the unreadable leases forever
	logger.log(Level.FINE, "Adding unreadable lease with membership of " +
			  "Lease.FOREVER");
	set.renewFor(specialLease01, Lease.FOREVER);

	logger.log(Level.FINE, "Adding unreadable lease with membership of " +
			  "Lease.FOREVER");
	set.renewFor(specialLease02, Lease.FOREVER);

	/* Assert that a LeaseUnmarshalException is thrown when getLeases()
	   is called */
	try {
	    UnreadableTestLease.setFailMode(true);
	    Lease[] leaseArray = set.getLeases();
	    UnreadableTestLease.setFailMode(false);

	    /* here we have succeeded to getLeases so something went
	       frightfully wrong */
	    String message = 
		"The getLeases() method succeeded but should\n" +
		"have thrown a LeaseUnMarshalException.";
	    throw new TestException(message);

	} catch (LeaseUnmarshalException ex) {

	    // set failure mode
	    UnreadableTestLease.setFailMode(false);

	    /* assert that the getLeases has exactly the
	       0 leases. */
	    Lease[] goodLeases = ex.getLeases();
	    if (goodLeases.length != 0) {
		String message = "The getLeases method returned " + 
		    goodLeases.length + " leases.\n" + "We were" +
		    " expecting 0.";
		    throw new TestException(message);
	    }

	    /* assert that the getMarshalledLeases has exactly the
	       2 leases we are expecting. */
	    MarshalledObject[] badLeases = ex.getMarshalledLeases();
	    if (badLeases.length != 2) {
		String message = "The getLeases method returned " + 
		    badLeases.length + " marshalled objects.\n" + 
		    "We were expecting 2.";
		    throw new TestException(message);
	    }

	    // are they the two that we expect??
	    if (rstUtil.indexOfLease(specialLease01, badLeases) == -1) {
		String message = "StillMarshalled array is missing" +
		    " special lease #1";
		throw new TestException(message);
	    }

	    if (rstUtil.indexOfLease(specialLease02, badLeases) == -1) {
		String message = "StillMarshalled array is missing" +
		    " special lease #2";
		throw new TestException(message);
	    }


	    /* assert that the getExceptions has exactly the
	       2 two we are expecting. */
	    Throwable[] exception = ex.getExceptions();
	    if (exception.length != 2) {
		String message = "The getExceptions method returned " + 
		    exception.length + " exceptions.\n" + 
		    "We were expecting 2.";
		    throw new TestException(message);
	    }

	    /* the exceptions have the lease id embedded so we can
	       check which exception goes with which lease */
	    int[] exceptionID = new int[exception.length];
	    for (int i = 0; i < exception.length; ++i) {
		String eMessage = exception[i].getMessage();
		StringTokenizer sTok = 
		    new StringTokenizer(eMessage, "=");
		sTok.nextToken(); // skip leading text
		String idStr = sTok.nextToken().trim();
		exceptionID[i] = Integer.parseInt(idStr);
	    }

	    // assert that the ids of the exceptions and leases match
	    int leaseIndex = 
		rstUtil.indexOfLease(specialLease01, badLeases);
	    if (exceptionID[leaseIndex] != specialLease01.id()) {
		String message = "The order of the leases in the\n" +
		    "bad leases array does not correspond to the\n" +
		    "order of the exceptions in the exceptions array.";
		throw new TestException(message);
	    }

	    leaseIndex = rstUtil.indexOfLease(specialLease02, badLeases);
	    if (exceptionID[leaseIndex] != specialLease02.id()) {
		String message = "The order of the leases in the\n" +
		    "bad leases array does not correspond to the\n" +
		    "order of the exceptions in the exceptions array.";
		throw new TestException(message);
	    }

	} // end of catch (LeaseUnmarshalledException ex) block
    }
} // LeaseUnmarshalNoneTest













