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
package org.apache.river.test.impl.mercury;

import java.util.logging.Level;

import java.rmi.activation.ActivationDesc;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationSystem;
import java.rmi.RemoteException;

import org.apache.river.constants.TimeConstants;

import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.event.EventMailbox;
import net.jini.event.MailboxRegistration;
import net.jini.event.MailboxPullRegistration;
import net.jini.event.PullEventMailbox;

import org.apache.river.qa.harness.Admin;
import org.apache.river.qa.harness.ActivatableServiceStarterAdmin;
import org.apache.river.qa.harness.TestException;

/*
 * This is a regression test for bug 4507320. The test attempts to 
 * normally create a mailbox and then obtain an implementation 
 * specific interface in order to change the activation state to 
 * on demand launching (i.e. restart = false). The test then tries
 * to create multiple registrations/leases, shutdown the service, 
 * sleep past the lease expiration, and then restart the service.
 * The idea, here, is to see if the mailbox service can properly 
 * recover its state with expired registrations.
 */

public class ExpiredRegRecovery extends MailboxTestBase 
    implements TimeConstants 
{

    private final long DURATION = 1*MINUTES;
    private final long SLEEP_DURATION = DURATION * 2L;
    private final int NUM_REGS = 5;

    public void run() throws Exception {
        int i;
        logger.log(Level.INFO, "Starting up " + this.getClass().toString()); 

	// force mercury to the master for this test
	getConfig().setDynamicParameter("net.jini.event.EventMailbox.host",
					"master");
	String mbType =
            getConfig().getStringConfigVal(
		MAILBOX_PROPERTY_NAME,
		MAILBOX_IF_NAME);

	logger.log(Level.INFO, "Getting ref to " + mbType);
	Object mb = null;
	if (mbType.equals(MAILBOX_IF_NAME)) {
            mb = getMailbox();
  	    logger.log(Level.INFO, "Got Mailbox reference: " + mb);
	} else if (mbType.equals(PULL_MAILBOX_IF_NAME)) {
            mb = getPullMailbox();
  	    logger.log(Level.INFO, "Got Pull Mailbox reference: " + mb);
	} else {
            throw new TestException(
		"Unsupported mailbox type requested" + mbType);
	}
	
        Admin admin = getManager().getAdmin(mb);
        if (! (admin instanceof ActivatableServiceStarterAdmin)) {
            throw new RemoteException("Service is not activatable");
        }

	logger.log(Level.INFO, "Resetting activation data"); 
	resetMailboxToOnDemandActivation(
            ((ActivatableServiceStarterAdmin) admin).getActivationID());

        Lease[] leases = new Lease[NUM_REGS];
	if (mbType.equals(MAILBOX_IF_NAME)) {
	    logger.log(Level.INFO, "Generating " + NUM_REGS + " registrations");
	    long[] durations = new long[NUM_REGS];
	    for (i=0; i < NUM_REGS; i++) {
		durations[i] = DURATION;
	    }
	    MailboxRegistration[] mbrs = getRegistrations((EventMailbox)mb, durations);

	    logger.log(Level.INFO, "Checking leases");
	    for (i=0; i < NUM_REGS; i++) {
		leases[i] = getMailboxLease(mbrs[i]);
		checkLease(leases[i], DURATION); 
	    }
	} else if (mbType.equals(PULL_MAILBOX_IF_NAME)) {
	    logger.log(Level.INFO, "Generating " + NUM_REGS + " pull registrations");
	    long[] durations = new long[NUM_REGS];
	    for (i=0; i < NUM_REGS; i++) {
		durations[i] = DURATION;
	    }
	    MailboxPullRegistration[] mbrs = 
	        getPullRegistrations((PullEventMailbox)mb, durations);

	    logger.log(Level.INFO, "Checking pull leases");
	    for (i=0; i < NUM_REGS; i++) {
		leases[i] = getPullMailboxLease(mbrs[i]);
		checkLease(leases[i], DURATION); 
	    }
	}
	

        // Kill the mailbox service
	logger.log(Level.INFO, "Killing the mailbox"); 
        ((ActivatableServiceStarterAdmin) admin).killVM();

	logger.log(Level.INFO, "Sleeping past lease expiration");
	Thread.sleep(SLEEP_DURATION);
	logger.log(Level.INFO, "Sleeping ... done");

	/* 
	 * The event mailbox should now be started on demand with the
	 * first remote invocation below. The service should now be 
         * processing expired registrations during the recovery phase. 
         * The lease checking below just checks to make sure the service 
         * is responding (i.e. hasn't crashed).
	 */

	logger.log(Level.INFO, "Attempting to cancel all leases");
	int exceptionCount = 0;
	for (i=0; i < NUM_REGS; i++) {
	    try {
	        leases[i].cancel();
	    } catch (UnknownLeaseException ule) {
	        logger.log(Level.INFO, "Expected exception thrown");
	        exceptionCount++;
	    }
	}

	logger.log(Level.INFO, "Checking exception count");
	if (exceptionCount != NUM_REGS) 
	    throw new TestException("Successfully cancelled an expired lease");

	logger.log(Level.INFO, "Done.");
    }

    public static void resetMailboxToOnDemandActivation(ActivationID aid) 
	throws ActivationException, RemoteException
    {
	ActivationSystem sys = net.jini.activation.ActivationGroup.getSystem();
	ActivationDesc adesc = sys.getActivationDesc(aid);
	boolean restart = false;
	ActivationDesc newDesc = 
	    new ActivationDesc(
		adesc.getGroupID(),
		adesc.getClassName(),
		adesc.getLocation(),
		adesc.getData(),
		restart
	    );
        sys.setActivationDesc(aid, newDesc);
    }

}
