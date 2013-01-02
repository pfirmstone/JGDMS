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

package com.sun.jini.test.spec.renewalservice;

import java.util.logging.Level;

// java.io
import java.io.PrintWriter;

// java.rmi
import java.rmi.RemoteException;

// net.jini
import net.jini.config.ConfigurationException;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.UnknownEventException;
import net.jini.core.lease.Lease;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

// 
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.Test;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.test.share.FailingOpCountingOwner;
import com.sun.jini.test.share.RememberingRemoteListener;
import com.sun.jini.test.share.TestLease;
import com.sun.jini.test.share.TestLeaseProvider;

/**
 * Assert that if an event listener is replaced, and one or more event
 * delivery attempts on the original listener failed or were
 * indeterminate, implementations may chose to send some ot all of
 * these events to the new listener.
 *
 * <P>NOTE:<BR>This test is implementation specific to Norm.</P>
 * 
 */
public class NewListenerEventTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * The default value time for which client leases are renewed
     */
    private final long DEFAULT_RENEW_GRANT = 40 * 1000; // 40 seconds

    /**
     * The "land lord" for the leases. Defines lease method behavior.
     */
    private FailingOpCountingOwner failingOwner = null;

    /**
     * listener that will log events as they arrive 
     */
    private RememberingRemoteListener normalListener = null;
    private FailingRemoteEventListener failingListener = null;

    /**
     * Provides leases for this test. 
     */
    private TestLeaseProvider leaseProvider = null;

    /**
     * The maximum time granted for a lease by a renew operation. 
     */
    private long renewGrant = 0;

    /**
     * Sets up the testing environment.
     */
    public Test construct(com.sun.jini.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.construct(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "NewListenerEventTest: In setup() method.");

       // logs events as they arrive
       normalListener = new RememberingRemoteListener(getExporter());
       failingListener = new FailingRemoteEventListener();

       // capture the renewal time
       String property = "com.sun.jini.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, DEFAULT_RENEW_GRANT);

       // instantiate a lease provider
       leaseProvider = new TestLeaseProvider(3);

       // create an owner to for testing definite exceptions
       Exception definiteException = 
	   new IllegalArgumentException("NewListenerEventTest");
       failingOwner = 
	   new FailingOpCountingOwner(definiteException, 0, renewGrant);
       return this;
    }


    /**
     * Assert that if an event listener is replaced, and one or more event
     * delivery attempts on the original listener failed or were
     * indeterminate, implementations may chose to send some ot all of
     * these events to the new listener.
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "NewListenerEventTest: In run() method.");

	// get the service for test
	LeaseRenewalService lrs = getLRS();

	// create a lease renewal set that hangs around a long time
	logger.log(Level.FINE, "Creating set with lease duration of " +
			  "Lease.FOREVER.");
	long renewSetDur = Lease.FOREVER;
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(renewSetDur);
	set = prepareSet(set);

	// register the listeners to receive renewal failure events
	set.setRenewalFailureListener(failingListener, null);

	// create 3 leases that will fail during renewal attempts
	logger.log(Level.FINE, "Creating failing lease #1 with duration of " +
			  renewGrant + " milliseconds.");
	TestLease lease01 = 
	    leaseProvider.createNewLease(failingOwner, 
					 rstUtil.durToExp(renewGrant));
	
	logger.log(Level.FINE, "Creating failing lease #2 with duration of " +
			  renewGrant + " milliseconds.");
	TestLease lease02 = 
	    leaseProvider.createNewLease(failingOwner, 
					 rstUtil.durToExp(renewGrant));
	
	logger.log(Level.FINE, "Creating failing lease #3 with duration of " +
			  renewGrant + " milliseconds.");
	TestLease lease03 = 
	    leaseProvider.createNewLease(failingOwner, 
					 rstUtil.durToExp(renewGrant));
	
	// add all the leases to the set
	logger.log(Level.FINE, "Adding client leases to renewal set.");
	set.renewFor(lease01, Long.MAX_VALUE);
	set.renewFor(lease02, Long.MAX_VALUE);
	set.renewFor(lease03, Long.MAX_VALUE);

	// wait for the failures to roll in ...
	rstUtil.waitForRemoteEvents(failingListener, 3, renewGrant * 2);

	// assert that the three events have arrived
	long numberOfFailedEvents = failingListener.getEvents().length;
	if (numberOfFailedEvents < 3) {
	    String message = "The failing remote listener received " +
		numberOfFailedEvents + " events but was required to " +
		"receive at least 3.";
	    throw new TestException(message);
	}

	// Replace the failing listener with a normal one
	logger.log(Level.FINE, "Replacing failing listener with a" +
			  " succeeding one.");
	set.setRenewalFailureListener(normalListener, null);

	// wait for events to get forwarded
	rstUtil.waitForRemoteEvents(normalListener, 3, renewGrant * 2);
	
	// assert that the normal listener received at least one event
	long numberOfNormalEvents = normalListener.getEvents().length;
	logger.log(Level.FINE, numberOfNormalEvents + " events forwarded to " +
			  "normal listener.");
	if (numberOfNormalEvents == 0) {
	    String message = "Normal Listener received " + 
		"no events but is required to\n" +
		"receive at least 1. (per Norm implementation).";
	    throw new TestException(message);
	}

	logger.log(Level.FINE, "Implementation forwarded " + 
			  numberOfNormalEvents + " events.");
    }

    /**
     * Special RemoteListener will throw an UnknownEventException
     */
    class FailingRemoteEventListener extends RememberingRemoteListener 
    {

	public FailingRemoteEventListener() 
	        throws  ConfigurationException, RemoteException 
	{
	    super(getExporter());
	}

	public synchronized void notify(RemoteEvent event) 
	        throws UnknownEventException, RemoteException {
	    super.notify(event);
	    throw new RemoteException("NewListenerEventTest");
	}
    }

} // NewListenerEventTest

