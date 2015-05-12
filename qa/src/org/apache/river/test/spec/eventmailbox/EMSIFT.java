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
package org.apache.river.test.spec.eventmailbox;

import java.util.logging.Level;

import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;

import java.rmi.RemoteException;

import org.apache.river.constants.TimeConstants;
import org.apache.river.qa.harness.Test;

import net.jini.event.EventMailbox;
import net.jini.event.MailboxRegistration;
import net.jini.core.lease.Lease;

import org.apache.river.test.impl.mercury.MailboxTestBase;

/**
 * EventMailboxServiceInterfaceTest
 * Tests the register() method with various in/valid lease durations.
 * Also checks to see that the regsitrations are unique.
 */
public class EMSIFT extends MailboxTestBase implements TimeConstants {

    private final long DURATION1 = 3*HOURS;
    private final long INVALID_DURATION_NEG = -DURATION1;
    private final long INVALID_DURATION_ZERO = 0;
    private final long VALID_DURATION_ANY = Lease.ANY;

    public void run() throws Exception {
	EventMailbox mb = getConfiguredMailbox();        

	MailboxRegistration mr1 = getRegistration(mb, DURATION1);
	checkLease(getMailboxLease(mr1), DURATION1); 

	MailboxRegistration mr2 = getRegistration(mb, Lease.FOREVER);
	checkLease(getMailboxLease(mr2), Lease.FOREVER); 

	MailboxRegistration mr3 = getRegistration(mb, Lease.ANY);
	checkLease(getMailboxLease(mr3), Lease.ANY); 

	MailboxRegistration mr4 = null;
	try {
	    mr4 =  getRegistration(mb, INVALID_DURATION_NEG);
	    throw new TestException("Illegal negative duration value "
				  + "was accepted");
	} catch (IllegalArgumentException iae) {
	    logger.log(Level.INFO, 
		       "Caught expected invalid duration exception");
	}
	try {
	    mr4 =  getRegistration(mb, INVALID_DURATION_ZERO);
	    throw new TestException("Illegal (zero) duration value "
				  + "was accepted");
	} catch (IllegalArgumentException iae) {
	    logger.log(Level.INFO, 
		       "Caught expected invalid duration exception");
	}
	try {
	    mr4 =  getRegistration(mb, VALID_DURATION_ANY);
	    logger.log(Level.INFO, "Valid (any) duration value was accepted");
	} catch (IllegalArgumentException iae) {
	    throw new TestException("Valid (any) duration value was "
				  + "not accepted");
	}
	if ((mr1 == mr2) || (mr1 == mr3) || (mr2 == mr3)) 
	    throw new TestException("Service returned non-distinct "
				  + "objects in the \"==\" sense");
	else 
	    logger.log(Level.INFO, 
		       "Service returned distinct objects in the \"==\" sense");

	if (mr1.equals(mr2) || mr1.equals(mr3) || 
	    mr2.equals(mr1) || mr2.equals(mr3) ||
	    mr3.equals(mr1) || mr3.equals(mr2))
	    throw new TestException("Service returned non-distinct objects "
				  + "in the \"equals\" sense");
	else
	    logger.log(Level.INFO, "Service returned distinct objects in "
		       + "the \"equals\" sense");

	if (mr1.equals(mr1) && mr2.equals(mr2) && mr3.equals(mr3)) 
	    logger.log(Level.INFO, 
		       "Identity property holds for the registration objects");
	else
	    throw new TestException("Identity property doesn't hold "
				  + "for the registration objects");
    }

    /**
     * Invoke parent's construct and parser
     * @exception TestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public Test construct(QAConfig config) throws Exception {
	super.construct(config);
	parse();
        return this;
    }
}
