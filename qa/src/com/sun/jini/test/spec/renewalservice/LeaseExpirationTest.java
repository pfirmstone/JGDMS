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
import java.io.StreamCorruptedException;

// java.rmi
import java.rmi.ConnectException;
import java.rmi.MarshalException;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.ServerError;
import java.rmi.ServerException;
import java.rmi.UnmarshalException;

// 
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.Test;

// net.jini
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

// com.sun.jini
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.test.share.FailingOpCountingOwner;
import com.sun.jini.test.share.TestLease;
import com.sun.jini.test.share.TestLeaseProvider;

/**
 * <P>
 * LeaseExpirationTest asserts that the expiration of a lease actually
 * results in it being removed from its renewal set and no further
 * action is taken on the lease for a period up to and including its
 * expiration time plus one half.
 * </P>
 * <P>
 * Also the various types of exceptions are tested as specified in the
 * <EM>Introduction to Helper Utilities and Services</EM>, US.2.6.
 * Specifically the following assertions are tested:
 * </P>
 * <UL>
 * <LI>Bad Object Exception:</LI>
 *    <UL>
 *    <LI>Any Instance of a java.rmi.NoSuchObjectException</LI>
 *    <LI>Any instance of a java.rmi.ServerError in which the value of the
 *        exception's detail field is a bad object exception</LI>
 *    <LI>Any instance of a java.lang.RuntimeException</LI>
 *    <LI>Any instance of a java.rmi.ServerException in which the value of
 *        exception's detail field is a bad object exception</LI>
 *    <LI>Any instance of a java.lang.Error unless it is an instance of
          java.lang.LinkageError or java.lang.OutOfMemory</LI>
 *    </UL>
 * <LI>Bad Invocation Exception:</LI>
 *    <UL>
 *    <LI>Any instance of a java.rmi.MarshalException in which the value of
 *        exception's detail field is an instance of 
 *        java.io.ObjectStreamException</LI>
 *    <LI>Any instance of a java.rmi.UnmarshalException in which the value of
 *        exception's detail field is an instance of 
 *        java.io.ObjectStreamException</LI>
 *    <LI>Any instance of a java.rmi.ServerException in which the value of
 *        the exceptions detail field is a bad invocation exception</LI>
 *    </UL>
 * <LI>Indefinite Exception:</LI>
 *    <UL>
 *    <LI>Any instance of a java.rmi.RemoteException except those that can
 *        classified as either a bad invocation exception or a bad object
 *        exception</LI>
 *    <LI>Any instance of a java.lang.OutOfMemoryError</LI>
 *    <LI>Any instance of a java.lang.LinkageError</LI>
 *    </UL>
 * </UL>
 * 

 */
public class LeaseExpirationTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * Provides leases for this test. 
     */
    private TestLeaseProvider leaseProvider = null;

    /**
     * The "land lord" for the leases. Defines lease method behavior.
     * for bad object exception testing.
     */
    private FailingOpCountingOwner badObjOwner01 = null;
    private FailingOpCountingOwner badObjOwner02 = null;
    private FailingOpCountingOwner badObjOwner03 = null;
    private FailingOpCountingOwner badObjOwner04 = null;
    private FailingOpCountingOwner badObjOwner05 = null;

    /**
     * The exceptions thrown by the above owners to test bad object exceptions
     */
    private NoSuchObjectException badObjException01 = null;
    private ServerError badObjException02 = null;
    private NullPointerException badObjException03 = null;
    private ServerException badObjException04 = null;
    private StackOverflowError badObjException05 = null;

    /**
     * The "land lord" for the leases. Defines lease method behavior.
     * for bad invocation exception testing.
     */
    private FailingOpCountingOwner badInvOwner01 = null;
    private FailingOpCountingOwner badInvOwner02 = null;
    private FailingOpCountingOwner badInvOwner03 = null;

    /**
     * The exceptions thrown by the above owners to test bad invocation
     * exceptions
     */
    private MarshalException badInvException01 = null;
    private UnmarshalException badInvException02 = null;
    private ServerException badInvException03 = null;
    
    /**
     * The "land lord" for the leases. Defines lease method behavior.
     * for indefinite exception testing.
     */
    private FailingOpCountingOwner indefiniteOwner01 = null;
    private FailingOpCountingOwner indefiniteOwner02 = null;
    private FailingOpCountingOwner indefiniteOwner03 = null;
    private FailingOpCountingOwner indefiniteOwner04 = null;
    private FailingOpCountingOwner indefiniteOwner05 = null;
    private FailingOpCountingOwner indefiniteOwner06 = null;

    /**
     * The exceptions thrown by the above owners to test indefinite exceptions
     */
    private ConnectException indefiniteException01 = null;
    private OutOfMemoryError indefiniteException02 = null;
    private NoSuchFieldError indefiniteException03 = null;
    private ServerError indefiniteException04 = null;
    private ServerError indefiniteException05 = null;
    private ServerException indefiniteException06 = null;

    /**
     * The maximum time granted for a lease by a renew operation. 
     */
    private long renewGrant = 0;

    /**
     * The default value renewGrant 
     */
    private final long DEFAULT_RENEW_GRANT = 30 * 1000; // 30 seconds

    /**
     * The LeaseRenewalSet used throught entire test
     */
    private LeaseRenewalSet set = null;

    /**
     *  The LeaseRenewalManager used for LRS impls that grant only short leases
     */
    private LeaseRenewalManager lrm = null;

    /**
     * Sets up the testing environment.
     */
    public Test construct(com.sun.jini.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.construct(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "LeaseExpirationTest: In setup() method.");

       // instantiate a lease provider
       leaseProvider = new TestLeaseProvider(20);

       // capture the renewal time
       String property = "com.sun.jini.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, DEFAULT_RENEW_GRANT);

       // create owners for testing bad object exceptions

       // 01) any instance of a NoSuchObjectException
       badObjException01 = 
	   new NoSuchObjectException("LeaseExpirationTest");
       badObjOwner01 = 
	   new FailingOpCountingOwner(badObjException01, 0, renewGrant);

       /* 02) any instance of a java.rmi.ServerError in which the value 
	  of the exception's detail field is a bad object exception */
       badObjException02 = 
	   new ServerError("LeaseExpirationTest", 
			   new StackOverflowError("LeaseExpirationTest"));
       badObjOwner02 = 
	   new FailingOpCountingOwner(badObjException02, 0, renewGrant);

       // 03) any instance of java.lang.RuntimeException
       badObjException03 = 
	   new NullPointerException("LeaseExpirationTest");
       badObjOwner03 = 
	   new FailingOpCountingOwner(badObjException01, 0, renewGrant);

       /* 04) any instance of a java.rmi.ServerException in which the value 
	  of the exception's detail field is a bad object invocation */
       badObjException04 = 
	   new ServerException("LeaseExpirationTest", badObjException02);
       badObjOwner04 = 
	   new FailingOpCountingOwner(badObjException04, 0, renewGrant);

       /* 05) any instance of a java.lang.Error unless it is an instance of
	  java.lang.LinkageError or java.lang.OutOfMemoryError */
       badObjException05 = 
	   new StackOverflowError("LeaseExpirationTest");
       badObjOwner05 = 
	   new FailingOpCountingOwner(badObjException05, 0, renewGrant);

       // create owners for testing bad invocation exceptions
       
       /* 01) any instance of java.rmi.MarshalException in which the
          value of the exception's detail field is an instance of
          java.io.ObjectStreamException */
       badInvException01 = 
	   new MarshalException("LeaseExpirationTest",
			new StreamCorruptedException("LeaseExpirationTest"));
       badInvOwner01 = 
	   new FailingOpCountingOwner(badInvException01, 0, renewGrant);

       /* 02) any instance of java.rmi.UnmarshalException in which the
          value of the exception's detail field is an instance of
          java.io.ObjectStreamException */
       badInvException02 = 
	   new UnmarshalException("LeaseExpirationTest",
			new StreamCorruptedException("LeaseExpirationTest"));
       badInvOwner02 = 
	   new FailingOpCountingOwner(badInvException02, 0, renewGrant);

       /* 03) any instance of java.rmi.ServerException in which the
          value of the exception's detail field is a bad invocation
          exception. */
       badInvException03 = 
	   new ServerException("LeaseExpirationTest", badInvException01);
       badInvOwner03 = 
	   new FailingOpCountingOwner(badInvException03, 0, renewGrant);

       // create owners for testing bad indefinite exceptions

       /* 01) any instance of a java.rmi.RemoteException except those
          that can be classified as either a bad invocation or bad
          object exception. */
       indefiniteException01 = 
	   new ConnectException("LeaseExpirationTest");
       indefiniteOwner01 = 
	   new FailingOpCountingOwner(indefiniteException01, 0, renewGrant);

       // 02) any instance of a java.lang.OutOfMemoryError
       indefiniteException02 = new OutOfMemoryError("LeaseExpirationTest");
       indefiniteOwner02 = 
	   new FailingOpCountingOwner(indefiniteException02, 0, renewGrant);
       
       // 03) any instance of a java.lang.LinkageError
       indefiniteException03 = new NoSuchFieldError("LeaseExpirationTest");
       indefiniteOwner03 = 
	   new FailingOpCountingOwner(indefiniteException03, 0, renewGrant);
       
       // 04) assert that 02 is true inside of a ServerError
       indefiniteException04 = new ServerError("LeaseExpirationTest",
					       indefiniteException02);
       indefiniteOwner04 = 
	   new FailingOpCountingOwner(indefiniteException04, 0, renewGrant);

       // 05) assert that 03 is true inside of a ServerError
       indefiniteException05 = new ServerError("LeaseExpirationTest",
					       indefiniteException03);
       indefiniteOwner05 = 
	   new FailingOpCountingOwner(indefiniteException05, 0, renewGrant);

       // 06) assert that 01 is true inside of a ServerException
       indefiniteException06 = new ServerException("LeaseExpirationTest",
						   indefiniteException01);
       indefiniteOwner06 = 
	   new FailingOpCountingOwner(indefiniteException06, 0, renewGrant);

       // create lease renewal manager for wider use across implementations
       lrm = new LeaseRenewalManager(sysConfig.getConfiguration());
       return this;
    }

    /**
     * This method asserts that the expiration of a lease actually
     * results in it being removed from its renewal set and no further
     * action is taken on the lease for a period up to and including
     * its expiration time plus one half.
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "LeaseExpirationTest: In run() method.");

	// get a lease renewal set w/ duration of as long as possible
	logger.log(Level.FINE, "Creating the lease renewal set.");
	logger.log(Level.FINE, "Duration = Lease.FOREVER.");
	LeaseRenewalService lrs = getLRS();
	set = lrs.createLeaseRenewalSet(Lease.FOREVER);
	set = prepareSet(set);
	lrm.renewFor(prepareLease(set.getRenewalSetLease()), Lease.FOREVER, null);
	
	/**************** Bad Object Exception Tests ***************/

	
	/* ------------------------------------------- *
	 * 01) any instance of a NoSuchObjectException *
	 * ------------------------------------------- */
	logger.log(Level.FINE, "Testing bad object exception assertion 01.");
	String message = testBadObjectException(badObjOwner01);
	if (message != null) {
	    throw new TestException(message);
	}

	// announce success
	logger.log(Level.FINE, "Bad object exception assertion 01 passed.");

	/* ------------------------------------------------------------- *
	 * 02) any instance of a java.rmi.ServerError in which the value *
	 * of the exception's detail field is a bad object exception     *
	 * ------------------------------------------------------------- */
	logger.log(Level.FINE, "Testing bad object exception assertion 02.");

	message = testBadObjectException(badObjOwner02);
	if (message != null) {
	    throw new TestException(message);
	}

	// announce success
	logger.log(Level.FINE, "Bad object exception assertion 02 passed.");

	/* ------------------------------------------------------------- *
	 * 03) Any instance of a java.lang.RuntimeException              *
	 * ------------------------------------------------------------- */
	logger.log(Level.FINE, "Testing bad object exception assertion 03.");

	message = testBadObjectException(badObjOwner03);
	if (message != null) {
	    throw new TestException(message);
	}

	// announce success
	logger.log(Level.FINE, "Bad Object Exception assertion 03 passed.");

	/* ------------------------------------------------------------- *
	 * 04) Any instance of a java.rmi.ServerException in which the   *
	 * value of the exception's detail field is a bad object         *
	 * exception                                                     *
	 * ------------------------------------------------------------- */
	logger.log(Level.FINE, "Testing bad object exception assertion 04.");

	message = testBadObjectException(badObjOwner04);
	if (message != null) {
	    throw new TestException(message);
	}

	// announce success
	logger.log(Level.FINE, "Bad object exception assertion 04 passed.");

	/* ------------------------------------------------------------- *
	 * 05) Any instance of a java.lang.Error unless it is an         *
	 * instance of java.lang.LinkageError or                         *
	 * java.lang.OutOfMemoryError                                    *
	 * ------------------------------------------------------------- */
	logger.log(Level.FINE, "Testing bad object exception assertion 05.");

	message = testBadObjectException(badObjOwner05);
	if (message != null) {
	    throw new TestException(message);
	}

	// announce success
	logger.log(Level.FINE, "Bad object exception assertion 05 passed.");

	/**************** Bad Invocation Exception Tests ***************
	 * Note: essentially these are treated exactly the same as bad *
	 * object exceptions by the LRS.                               *
	 ***************************************************************

	/* ------------------------------------------------------------ *
	 * 01) any instance of a java.rmi.MarshalException in which the *
	 * value of the exception's detail field is a bad object        *
	 * exception.                                                   *
	 * ------------------------------------------------------------ */
	logger.log(Level.FINE, 
		   "Testing bad invocation exception assertion 01.");
	message = testBadObjectException(badInvOwner01);
	if (message != null) {
	    throw new TestException(message);
	}

	// announce success
	logger.log(Level.FINE, "Bad invocation exception assertion 01 passed.");

	
	/* ------------------------------------------------------------ *
	 * 02) any instance of a java.rmi.UnmarshalException in which   *
	 * the value of the exception's detail field is a bad object    *
	 * exception.                                                   *
	 * ------------------------------------------------------------ */
	logger.log(Level.FINE, 
		   "Testing bad invocation exception assertion 02.");
	message = testBadObjectException(badInvOwner02);
	if (message != null) {
	    throw new TestException(message);
	}

	// announce success
	logger.log(Level.FINE, "Bad invocation exception assertion 02 passed.");

	
	/* ------------------------------------------------------------ *
	 * 03) Any instance of java.rmi.ServerException in which the    *
	 * value of the exception's detail field is a bad invocation    *
	 * exception.                                                   *
	 * ------------------------------------------------------------ */
	logger.log(Level.FINE, 
		   "Testing bad invocation exception assertion 03.");
	message = testBadObjectException(badInvOwner03);
	if (message != null) {
	    throw new TestException(message);
	}

	// announce success
	logger.log(Level.FINE, "Bad invocation exception assertion 03 passed.");


	/**************** Indefinite Exception Tests ***************/

	/* ------------------------------------------------------------ *
	 * 01) any instance of a java.rmi.RemoteException except those  *
	 * that can be classified as either a bad object or bad         *
	 * invocation exception.                                        *
	 * ------------------------------------------------------------ */
	logger.log(Level.FINE, 
		   "Testing indefinite exception assertion 01.");
	message = testIndefiniteException(indefiniteOwner01);
	if (message != null) {
	    throw new TestException(message);
	}

	// announce success
	logger.log(Level.FINE, "Indefinite exception assertion 01 passed.");

	
	/* ------------------------------------------------------------ *
	 * 02) any instance of a java.lang.OutOfMemoryError             *
	 * ------------------------------------------------------------ */
	logger.log(Level.FINE, 
		   "Testing indefinite exception assertion 02.");
	message = testIndefiniteException(indefiniteOwner02);
	if (message != null) {
	    throw new TestException(message);
	}

	// announce success
	logger.log(Level.FINE, "Indefinite exception assertion 02 passed.");

	
	/* ------------------------------------------------------------ *
	 * 03) any instance of a java.lang.LinkageError                 *
	 * ------------------------------------------------------------ */
	logger.log(Level.FINE, 
		   "Testing indefinite exception assertion 03.");
	message = testIndefiniteException(indefiniteOwner03);
	if (message != null) {
	    throw new TestException(message);
	}

	// announce success
	logger.log(Level.FINE, "Indefinite exception assertion 03 passed.");

	/* ------------------------------------------------------------ *
	 * 04) assert that 02 is true inside of a ServerError           *
	 * ------------------------------------------------------------ */
	logger.log(Level.FINE, 
		   "Testing indefinite exception assertion 04.");
	message = testIndefiniteException(indefiniteOwner04);
	if (message != null) {
	    throw new TestException(message);
	}

	// announce success
	logger.log(Level.FINE, "Indefinite exception assertion 04 passed.");

	/* ------------------------------------------------------------ *
	 * 05) assert that 03 is true inside of a ServerError           *
	 * ------------------------------------------------------------ */
	logger.log(Level.FINE, 
		   "Testing indefinite exception assertion 05.");
	message = testIndefiniteException(indefiniteOwner05);
	if (message != null) {
	    throw new TestException(message);
	}

	// announce success
	logger.log(Level.FINE, "Indefinite exception assertion 05 passed.");

	/* ------------------------------------------------------------ *
	 * 06) assert that 01 is true inside of a ServerException       *
	 * ------------------------------------------------------------ */
	logger.log(Level.FINE, 
		   "Testing indefinite exception assertion 06.");
	message = testIndefiniteException(indefiniteOwner06);
	if (message != null) {
	    throw new TestException(message);
	}

	// announce success
	logger.log(Level.FINE, "Indefinite exception assertion 06 passed.");


	/* now that a significant amount of time has passed re-check
	   each owner to ensure the LRS didn't sneak in a call behind
	   our backs */
	message = definiteOwnerFinalCheck(badObjOwner01,
					  "bad object owner 01");
	if (message != null) {
	    throw new TestException(message);
	}

	message = definiteOwnerFinalCheck(badObjOwner02,
					  "bad object owner 02");
	if (message != null) {
	    throw new TestException(message);
	}

	message = definiteOwnerFinalCheck(badObjOwner03,
					  "bad object owner 03");
	if (message != null) {
	    throw new TestException(message);
	}

	message = definiteOwnerFinalCheck(badObjOwner04,
					  "bad object owner 04");
	if (message != null) {
	    throw new TestException(message);
	}

	message = definiteOwnerFinalCheck(badObjOwner05,
					  "bad object owner 05");
	if (message != null) {
	    throw new TestException(message);
	}

	message = definiteOwnerFinalCheck(badInvOwner01,
					  "bad invocation owner 01");
	if (message != null) {
	    throw new TestException(message);
	}

	message = definiteOwnerFinalCheck(badInvOwner02,
					  "bad invocation owner 02");
	if (message != null) {
	    throw new TestException(message);
	}

	message = definiteOwnerFinalCheck(badInvOwner03,
					  "bad invocation owner 03");
	if (message != null) {
	    throw new TestException(message);
	}

	// indefinite owners ...
	message = indefiniteOwnerFinalCheck(indefiniteOwner01,
					    "indefinite owner 01");
	if (message != null) {
	    throw new TestException(message);
	}

	message = indefiniteOwnerFinalCheck(indefiniteOwner02,
					    "indefinite owner 02");
	if (message != null) {
	    throw new TestException(message);
	}

	message = indefiniteOwnerFinalCheck(indefiniteOwner03,
					    "indefinite owner 03");
	if (message != null) {
	    throw new TestException(message);
	}

	message = indefiniteOwnerFinalCheck(indefiniteOwner04,
					    "indefinite owner 04");
	if (message != null) {
	    throw new TestException(message);
	}

	message = indefiniteOwnerFinalCheck(indefiniteOwner05,
					    "indefinite owner 05");
	if (message != null) {
	    throw new TestException(message);
	}

	message = indefiniteOwnerFinalCheck(indefiniteOwner06,
					    "indefinite owner 06");
	if (message != null) {
	    throw new TestException(message);
	}
    }

    /**
     * Routine test for all bad object and bad invocation exceptions.
     * 
     * <P>Notes:</P>
     * 
     * @param owner the failing owner for test lease creation.
     * 
     * @return a String specifying an error message or null if no error occured
     * 
     */
    private String testBadObjectException(FailingOpCountingOwner owner) 
          throws UnknownLeaseException, InterruptedException, RemoteException {

	logger.log(Level.FINE, "Creating the lease to be managed.");
	logger.log(Level.FINE, "Duration == " + renewGrant);
	TestLease testLease = 
	    leaseProvider.createNewLease(owner, rstUtil.durToExp(renewGrant));

	// start managing the lease for as long as we can
	logger.log(Level.FINE, "Adding managed lease to lease renewal set.");
	logger.log(Level.FINE, "Membership duration = Long.MAX_VALUE");
	set.renewFor(testLease, Long.MAX_VALUE);
	    
	// wait the lease to expire
	rstUtil.waitForLeaseExpiration(testLease, 
				       "for client lease to expire.");

	// assert that the lease is no longer in the renewal set
	Lease managedLease = set.remove(testLease);
	if (managedLease != null) {
	    String message = "The lease that expired due to a definite";
	    message += " exception was never removed from the\n";
	    message += "renewal set.";
	    return message;
	}

	// waiting to ensure that no further action is taken
	rstUtil.sleepAndTell(renewGrant, 
			     "to ensure that no further action is taken.");

	// assert that renew was only called once
	logger.log(Level.FINE, "Checking # of calls to renew.");
	if (owner.getRenewCalls() != 1) {
	    String message = "An invalid call to renew was made on \n";
	    message += "a lease that supposedly expired due to\n";
	    message += "a definite exception.";
	    return message;
	}

	// assert that cancel was never called
	logger.log(Level.FINE, "Checking # of calls to cancel.");
	if (owner.getCancelCalls() != 0) {
	    String message = "An invalid call to cancel was made on \n";
	    message += "a lease that supposedly expired due to\n";
	    message += "a definite exception.";
	    return message;
	}

	return null;
    }

    /**
     * Routine test for all indefinite exceptions.
     * 
     * <P>Notes:</P>
     * 
     * @param owner the failing owner for test lease creation.
     * 
     * @return a String specifying an error message or null if no error occured
     * 
     */
    private String testIndefiniteException(FailingOpCountingOwner owner) 
        throws RemoteException, UnknownLeaseException, InterruptedException {

	// get a lease for testing indefinite exceptions
	logger.log(Level.FINE, "Creating the lease to be managed.");
	logger.log(Level.FINE, "Duration == " + renewGrant);
	TestLease testLease = 
	    leaseProvider.createNewLease(owner, 
					 rstUtil.durToExp(renewGrant));

	// start managing the lease for as long as we can
	logger.log(Level.FINE, "Adding managed lease to lease renewal set.");
	logger.log(Level.FINE, "Membership duration = Long.MAX_VALUE");
	set.renewFor(testLease, Long.MAX_VALUE);
	    
	// wait for the lease to expire
	rstUtil.waitForLeaseExpiration(testLease, 
				       "for client lease to expire.");

	/* we need to sleep a bit longer because the LRS implementation
	   might still be busy re-trying the renew operation */
	rstUtil.sleepAndTell(renewGrant/2, "to allow for lease removal.");

	// assert that the lease is no longer in the renewal set
	Lease managedLease = set.remove(testLease);
	if (managedLease != null) {
	    String message = "The lease that expired due to a indefinite";
	    message += " exception was never removed from the\n";
	    message += "renewal set.";
	    return message;
	}

	/* NOTE : The check for the number of calls to renew is eliminated
	   here because for an indefinite exception it is impossible to
	   determine any "correct" number of calls to the renew method. */

	// waiting to ensure that no further action is taken
	owner.resetCallCounts();
	rstUtil.sleepAndTell(renewGrant, 
			     "to ensure that no further action is taken.");

	// assert that renew was never called again
	logger.log(Level.FINE, "Checking # of calls to renew.");
	if (owner.getRenewCalls() != 0) {
	    String message = "An invalid call to renew was made on \n";
	    message += "a lease after it was removed from the set.\n";
	    return message;
	}

	// assert that cancel was never called
	logger.log(Level.FINE, "Checking # of calls to cancel.");
	if (owner.getCancelCalls() != 0) {
	    String message = "An invalid call to cancel was made on \n";
	    message += "a lease that supposedly expired due to\n";
	    message += "an indefinite exception.";
	    return message;
	}

	return null;
    }

    /**
     * check a definitely failing owner to ensure no extra calls 
     * have been made.
     * 
     * <P>Notes:</P>
     * 
     * @param owner  the FailingOpCountingOwner to be checked
     * @param ownerName a String representing an identifier for the owner
     *
     * @return a String indicating the nature of check failure or null if 
     *         check succeeded.
     * 
     */
    private String definiteOwnerFinalCheck(FailingOpCountingOwner owner,
					   String ownerID) {
	
	if (owner.getRenewCalls() != 1) {
	    String message = "Extra call to renew detected for " + ownerID;
	    return message;
	}

	if (owner.getCancelCalls() != 0) {
	    String message = "Call to cancel detected by " + ownerID + "\n";
	    message += "The LRS should never call cancel.";
	    return message;
	}

	return null;
    }

    /**
     * check an indefinitely failing owner to ensure no extra calls 
     * have been made.
     * 
     * <P>Notes:</P>
     * 
     * @param owner  the FailingOpCountingOwner to be checked
     * @param ownerName a String representing an identifier for the owner
     *
     * @return a String indicating the nature of check failure or null if 
     *         check succeeded.
     * 
     */
    private String indefiniteOwnerFinalCheck(FailingOpCountingOwner owner,
					     String ownerID) {
	
	if (owner.getRenewCalls() != 0) {
	    String message = "Extra call to renew detected for " + ownerID;
	    return message;
	}

	if (owner.getCancelCalls() != 0) {
	    String message = "Call to cancel detected by " + ownerID + "\n";
	    message += "The LRS should never call cancel.";
	    return message;
	}

	return null;
    }

} // LeaseExpirationTest
