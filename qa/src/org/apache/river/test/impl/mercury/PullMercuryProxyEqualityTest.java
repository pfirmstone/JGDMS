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

import net.jini.io.MarshalledInstance;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.ServerException;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;

import org.apache.river.constants.TimeConstants;
import org.apache.river.qa.harness.Test;

import net.jini.event.PullEventMailbox;
import net.jini.event.MailboxPullRegistration;
import net.jini.core.lease.Lease;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;


public class PullMercuryProxyEqualityTest extends EMSTestBase implements TimeConstants {

    //
    // This should be long enough to sensibly run the test.
    // If the service doesn't grant long enough leases, then
    // we might have to resort to using something like the
    // LeaseRenewalManager to keep our leases current.
    //
    private final long DURATION = 3*HOURS;

    public void run() throws Exception {
	logger.log(Level.INFO, "Starting up " + this.getClass().toString()); 

        // Get Mailbox references
	PullEventMailbox[] mbs = getPullMailboxes(2);
	PullEventMailbox mb1 = mbs[0];
	PullEventMailbox mb2 = mbs[1];
	PullEventMailbox mb1_dup =  null;

        // Get Mailbox admin references
	Object admin1 = getMailboxAdmin(mb1);
	Object admin2 = getMailboxAdmin(mb2);
	Object admin1_dup = null;

        // Get Mailbox registration references
	MailboxPullRegistration mr1 = getPullRegistration(mb1, DURATION);
	MailboxPullRegistration mr2 = getPullRegistration(mb2, DURATION);
	MailboxPullRegistration mr1_dup = null;

        // Get Mailbox lease references
	Lease mrl1 = getPullMailboxLease(mr1);
	Lease mrl2 = getPullMailboxLease(mr2);
	Lease mrl1_dup = null;

        // Get Mailbox listener references
	RemoteEventListener listener1 = getPullMailboxListener(mr1);
	RemoteEventListener listener2 = getPullMailboxListener(mr2);
	RemoteEventListener listener1_dup = null;

        // Get Duplicate references
	MarshalledInstance marshObj01 = new MarshalledInstance(mb1);
	mb1_dup = (PullEventMailbox)marshObj01.get(false);
	marshObj01 = new MarshalledInstance(admin1);
	admin1_dup = marshObj01.get(false);
	marshObj01 = new MarshalledInstance(mr1);
	mr1_dup = (MailboxPullRegistration)marshObj01.get(false);
	marshObj01 = new MarshalledInstance(mrl1);
	mrl1_dup = (Lease)marshObj01.get(false);
	marshObj01 = new MarshalledInstance(listener1);
	listener1_dup = (RemoteEventListener)marshObj01.get(false);
	
        // check top-level proxies
        if (!proxiesEqual(mb1, mb1_dup)) {
            throw new TestException( 
                "Duplicate proxies were not equal");
        }
        logger.log(Level.INFO, "Duplicate service proxies were equal");

        if (proxiesEqual(mb1, mb2)) {
            throw new TestException( 
                "Different proxies were equal");
        }
        logger.log(Level.INFO, "Different service proxies were not equal");

        // check admin proxies
        if (!proxiesEqual(admin1, admin1_dup)) {
            throw new TestException( 
                "Duplicate admin proxies were not equal");
        }
        logger.log(Level.INFO, "Duplicate admin proxies were equal");

        if (proxiesEqual(admin1, admin2)) {
            throw new TestException( 
                "Different admin proxies were equal");
        }
        logger.log(Level.INFO, "Different admin proxies were not equal");

        // check registration proxies
        if (!proxiesEqual(mr1, mr1_dup)) {
            throw new TestException( 
                "Duplicate registration proxies were not equal");
        }
        logger.log(Level.INFO, "Duplicate registration proxies were equal");

        if (proxiesEqual(mr1, mr2)) {
            throw new TestException( 
                "Different registration proxies were equal");
        }
        logger.log(Level.INFO, "Different registration proxies were not equal");
        // check registration leases
        if (!proxiesEqual(mrl1, mrl1_dup)) {
            throw new TestException( 
                "Duplicate registration leases were not equal");
        }
        logger.log(Level.INFO, "Duplicate registration leases were equal");

        if (proxiesEqual(mrl1, mrl2)) {
            throw new TestException( 
                "Different registration leases were equal");
        }
        logger.log(Level.INFO, "Different registration leases were not equal");

        // check registration listeners
        if (!proxiesEqual(listener1, listener1_dup)) {
            throw new TestException( 
                "Duplicate registration listeners were not equal");
        }
        logger.log(Level.INFO, "Duplicate registration listeners were equal");

        if (proxiesEqual(listener1, listener2)) {
            throw new TestException( 
                "Different registration listeners were equal");
        }
        logger.log(Level.INFO, 
		   "Different registration listeners were not equal");
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

    private static boolean proxiesEqual(Object a, Object b) {
        //Check straight equality
        if (!a.equals(b))
            return false;
	//System.out.println("A equals B");
        //Check symmetrical equality
        if (!b.equals(a))
            return false;
	//System.out.println("B equals A");
        //Check reflexive equality
        if (!a.equals(a))
            return false;
	//System.out.println("A equals A");
        if (!b.equals(b))
            return false;
	//System.out.println("B equals B");
        //Check consistency
        if (a.equals(null) || b.equals(null))
            return false;
	//System.out.println("B !equals null && A !equals null");

        return true;
    }


}
