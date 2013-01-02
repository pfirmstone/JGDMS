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
package com.sun.jini.test.impl.outrigger.transaction;

import com.sun.jini.qa.harness.Test;
import java.util.logging.Level;

// Test harness specific classes
import com.sun.jini.qa.harness.TestException;

// All other imports
import java.rmi.*;
import java.io.File;
import net.jini.core.entry.Entry;
import net.jini.core.lease.Lease;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.transaction.TransactionFactory;
import net.jini.core.transaction.Transaction;
import net.jini.space.JavaSpace;
import com.sun.jini.test.share.TestBase;

/**
 * Writes and entry, creates two transactions, T1 and T2, reads the
 * entry under T1, reads the entry under T2, then performs a blocking
 * take/takeIfExists under T1, and then commits/aborts T2. Repeats
 * trying each combination of aborting/committing T1 and using
 * take/takeIfExists.
 */
public class ReadReadTakeTest extends TestBase implements Test {
    /** Space under test */
    protected JavaSpace space;

    /** Transaction Manager we are using */
    protected TransactionManager txnMgr;

    /** Entry to manipulate */
    private SimpleEntry entry = new SimpleEntry("King", 1, 1);

    /**
     * Return a String which describes this test
     */
    public String getDescription() {
        return "Test Name = ReadReadTakeTest.";
    }

    /**
     * Return an array of String whose elements comprise the
     * categories to which this test belongs.
     */
    public String[] getCategories() {
        return new String[] {
            "outrigger" };
    }

    public void run() throws Exception {
        specifyServices(new Class[] {
            TransactionManager.class, JavaSpace.class});
        space = (JavaSpace) services[1];
        txnMgr = (TransactionManager) services[0];

	test(false, true);
	test(false, false);
	test(true,  true);
	test(true,  false);
    }

    private void test(boolean commit, boolean useTake) throws Exception {
	final String action = useTake?"take":"takeIfExists";
	final String resolve = commit?"commit":"abort";

	logger.log(Level.INFO, "Starting " + action + "/" + resolve + " trail");
	
        final Lease el = space.write(entry, null, Lease.FOREVER);
        addOutriggerLease(el, true);
	logger.log(Level.INFO, "wrote entry");

        final Transaction.Created txnHolder1 =
                TransactionFactory.create(txnMgr, 1000 * 60 * 60);
        final Transaction txn1 = txnHolder1.transaction;
        addMahaloLease(txnHolder1.lease, true);

        final Transaction.Created txnHolder2 =
                TransactionFactory.create(txnMgr, 1000 * 60 * 60);
        final Transaction txn2 = txnHolder2.transaction;
        addMahaloLease(txnHolder2.lease, true);

	if (null == space.read(entry, txn1, 0)) {
            throw new TestException("Could not perform read 1");
        }
	logger.log(Level.INFO, "read under txn1");

	if (null == space.read(entry, txn2, 0)) {
	    throw new TestException("Could not perform read 2");
	}
	logger.log(Level.INFO, "read under txn2");

	final TakeThread takeThread = new TakeThread(txn1, useTake);
	takeThread.start();

	logger.log(Level.INFO, "started TakeThread");

	Thread.sleep(15000);
	takeThread.confirmCallInProgress();

	logger.log(Level.INFO, "calling " + resolve);
	if (commit)
	    txn2.commit(60000);
	else 
	    txn2.abort(60000);
	logger.log(Level.INFO, resolve + " returned");

	if (takeThread.waitOnTakeReturn(30000)) {
	    logger.log(Level.INFO, action + "/" + resolve + " trail ok");
	} else {
	    logger.log(Level.INFO, action + "/" + resolve + " trail bad");
	    throw new TestException(action + "/" + resolve + " trail failed");
	}

	// Make the take role forward so we start clean
	txn1.commit(60000);
    }

    private class TakeThread extends Thread {
	private boolean takeCalled = false;
	private boolean takeReturned = false;
	private boolean resultOk;
	private Transaction txn1;
	private final boolean useTake;

	private TakeThread(Transaction txn, boolean useTake) {
	    super("TakeThread");
	    this.useTake = useTake;
	    txn1 = txn;
	}

	private synchronized void confirmCallInProgress() throws TestException {
	    if (!takeCalled)
		throw new 
                    TestException("Advancing to txn2 resolution before take call");
	    
	    if (takeReturned)
		throw new 
                    TestException("Advancing to txn2 resolution after take call");
	}

	private synchronized boolean waitOnTakeReturn(long timeout) 
	    throws InterruptedException 
	{
	    long endTime = System.currentTimeMillis() + timeout;
	    if (endTime > Long.MAX_VALUE)
		endTime = Long.MAX_VALUE;

	    while (!takeReturned) {
		final long waitTime = endTime - System.currentTimeMillis();
		
		if (waitTime > 0)
		    wait(waitTime);
		else 
		    break;
	    }

	    return resultOk;
	}

	public void run() {
	    synchronized (this) {
		takeCalled = true;
	    }

	    Entry e = null;
	    
	    try {
		if (useTake)
		    e = space.take(entry, txn1, Long.MAX_VALUE);
		else 
		    e = space.takeIfExists(entry, txn1, Long.MAX_VALUE);
	    } catch (Throwable t) {
		t.printStackTrace();
	    }
	    
	    synchronized (this) {
		resultOk = (e != null);
		takeReturned = true;
		notifyAll();
	    }
	}
    }
}
