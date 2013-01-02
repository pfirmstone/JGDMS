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
package com.sun.jini.test.impl.mercury;

import java.util.logging.Level;

// Test harness specific classes

import com.sun.jini.qa.harness.TestException;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;
import java.util.ArrayList;

import com.sun.jini.constants.TimeConstants;

import net.jini.event.InvalidIteratorException;
import net.jini.event.PullEventMailbox;
import net.jini.event.MailboxPullRegistration;
import net.jini.core.lease.Lease;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.UnknownEventException;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

public class PullRegistrationIFTest extends MailboxTestBase 
    implements TimeConstants 
{
    private final long DURATION1 = 3*HOURS;
    private final long DURATION2 = 2*DURATION1;

    public void run() throws Exception {
	logger.log(Level.INFO, "Starting up " + this.getClass().toString()); 

	PullEventMailbox mb = getPullMailbox();
        logger.log(Level.INFO, "Got mailbox reference: {0}", mb);

	MailboxPullRegistration mr1 = getPullRegistration(mb, DURATION1);
        logger.log(Level.INFO, "Got mailbox registration reference: {0}", mr1);
        
        // Exercises MailboxPullRegistration.getLease()
	checkLease(getPullMailboxLease(mr1), DURATION1); 
        logger.log(Level.INFO, "Checked mailbox lease");
        
	// Exercises MailboxPullRegistration.getListener()
	RemoteEventListener rel = getPullMailboxListener(mr1);
        logger.log(Level.INFO, "Got mailbox listener reference: {0}", rel);
        
        // Exercises mailbox listener re-submission which is not allowed
	try {
            mr1.enableDelivery(rel);
            throw new TestException("Successfully submitted service's "
                                  + "listener back to itself");
	} catch (IllegalArgumentException iae) {
            // ignore -- expected
	}
        logger.log(Level.INFO, "Unable to resubmit listener reference -- OK");
        
        // create new listener object
        rel =  TestUtils.createListener(getManager());
        logger.log(Level.INFO, "Got test listener reference: {0}", rel);
        
        // submit listener
	mr1.enableDelivery(rel);
        logger.log(Level.INFO, "Enabled listener reference");
        
        // Exercise disableDelivery 
	mr1.disableDelivery();
        logger.log(Level.INFO, "Disabled listener reference");
        
        // Exercises getRemoteEvents and next
	net.jini.event.RemoteEventIterator i = mr1.getRemoteEvents();
        logger.log(Level.INFO, "Got event iterator: {0}", i);     
        
	if (i.next(5000L) != null) {
	    throw new TestException("Got event from empty registration");
	}
        logger.log(Level.INFO, "No received events -- OK");
        
        // Exercises addUnknownEvents with empty set
        Collection unkEvts = new ArrayList(3);
        mr1.addUnknownEvents(unkEvts);
        logger.log(Level.INFO, "Called addUnknownEvents with empty set.");
        
        //Exercise iterator close
        i.close();
        logger.log(Level.INFO, "Closed iterator");      
        
        try {
            i.close();
 	    throw new TestException("Successfully re-closed iterator");
        } catch (InvalidIteratorException iie) {
            logger.log(Level.INFO, 
                "Caught expected exception upon re-closing: ", iie);
        }
        logger.log(Level.INFO, "Unable to close iterator again -- OK");
        
        try {
            i.next(5000L);
 	    throw new TestException(
                "Successfully called next on a closed iterator");
        } catch (InvalidIteratorException iie) {
            logger.log(Level.INFO, 
                "Caught expected exception upon next: ", iie);
        }
        logger.log(Level.INFO, "Unable to call next on iterator again -- OK");        
    }

    /**
     * Invoke parent's construct and parser
     * @exception TestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	parse();
        return this;
    }
}
