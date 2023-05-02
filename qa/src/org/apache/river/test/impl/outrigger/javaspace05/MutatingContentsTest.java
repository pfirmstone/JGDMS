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
package org.apache.river.test.impl.outrigger.javaspace05;

import org.apache.river.qa.harness.Test;
import java.util.logging.Level;

// Test harness specific classes
import org.apache.river.qa.harness.TestException;

// All other imports

import java.util.Collection;
import java.util.List;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Set;
import java.util.Iterator;
import java.util.Random;

import net.jini.core.lease.Lease;
import net.jini.core.entry.Entry;
import net.jini.lease.LeaseRenewalManager;

import net.jini.space.JavaSpace;
import net.jini.space.JavaSpace05;
import net.jini.space.MatchSet;

import org.apache.river.test.share.TestBase;


/**
 * writes a configurable number of random entries and uses a
 * configurable number of concurrent threads that perform a
 * configurable number of contents calls using a random sets of
 * templates. Additionally one thread is writing and taking random
 * entries from the space concurrently with the contents calls. Each
 * trail makes sure that all of the expected entries and only the
 * expected entries come back - making allowances for entries that
 * were added and removed during the course of the iteration.  
 */
public class MutatingContentsTest extends TestBase implements Test {
    final private static String configNameBase = 
	"org.apache.river.test.impl.outrigger.javaspace05.MutatingContentsTest.";

    /** Space under test */
    public void run() throws Exception {
	// Initialize our selves

        specifyServices(new Class[] { JavaSpace.class });
        final JavaSpace05 space = (JavaSpace05)services[0];

	final int threadCount = 
	    getConfig().getIntConfigVal(configNameBase + "threadCount", 5);
	final int trials =
	    getConfig().getIntConfigVal(configNameBase + "trials", 10);
	final int batchSize =
	    getConfig().getIntConfigVal(configNameBase + "batchSize", 100);
	final int testSetSize =
	    getConfig().getIntConfigVal(configNameBase + "testSetSize", 1500);

	final LeaseRenewalManager lrm = new LeaseRenewalManager();

	logger.log(Level.INFO, "threadCount = " + threadCount);
	logger.log(Level.INFO, "trials = " + trials);
	logger.log(Level.INFO, "batchSize = " + batchSize);
	logger.log(Level.INFO, "testSetSize = " + testSetSize);


	// Initialize the space

	logger.log(Level.INFO, "Writing test entries");

	final List testSet = new java.util.LinkedList();
	for (int i=0; i<testSetSize; i++)
	    testSet.add(TestEntries.newEntry());

	final Long maxValue = new Long(Lease.FOREVER);
	for (Iterator i=testSet.iterator(); i.hasNext();) {
	    final List entries = new LinkedList();
	    final List leases = new ArrayList();
	    for (int j=0; j<100 && i.hasNext(); j++) {
		entries.add(i.next());
		leases.add(maxValue);
	    }

	    space.write(entries, null, leases);
	}

	logger.log(Level.INFO, "Test entries writen");

	
	// Spawn the reader threads
	logger.log(Level.INFO, "Spawning readers");
	
	final ReaderThread[] threads = new ReaderThread[threadCount];
	for (int i=0; i<threads.length; i++) {
	    threads[i] = new ReaderThread(i, trials, batchSize, testSet, lrm, 
					  space);
	    threads[i].start();
	}

	logger.log(Level.INFO, "Readers spawned");

	logger.log(Level.INFO, "Spawning mutator");
	final MutatorThread mutator = new MutatorThread(testSet, threads, space);
	mutator.start();
	logger.log(Level.INFO, "Mutator spawned");
	
	for (int i=0; i<threads.length; i++) {
	    threads[i].join();
	}

	mutator.kill();
	mutator.join();

	for (int i=0; i<threads.length; i++) {
	    final ReaderThread t = threads[i];
	    t.getCompletionState();
	}

	mutator.getCompletionState();
    }

    /**
     * Return a String which describes this test
     */
    public String getDescription() {
        return "Test Name = MuatatingContentsTest.";
    }

    /**
     * Return an array of String whose elements comprise the
     * categories to which this test belongs.
     */
    public String[] getCategories() {
        return new String[] {
            "outrigger" };
    }

    private static class MutatorThread extends Thread {
	final private JavaSpace05 space;
	final private List testSet;
	final private Random random = new Random();
	final private ReaderThread[] readers;
	final private int idealSize;
	private Throwable completionState = null;

	private boolean done = false;

	synchronized void setCompletionState(Throwable v) {
	    if (completionState != null)
		completionState = v;
	}

	synchronized Exception getCompletionState() throws Exception {
	    if (completionState == null) return null;
	    
	    if (completionState instanceof Exception)
		throw (Exception)completionState;

	    if (completionState instanceof Error) 
		throw (Error)completionState;

	    throw new AssertionError(completionState);
	}


	private MutatorThread(List testSet, ReaderThread[] readers, 
			      JavaSpace05 space) 
	{
	    super("Mutator");
	    this.space = space;
	    this.testSet = testSet;
	    this.readers = readers;
	    idealSize = testSet.size();
	}

	private synchronized void kill() {
	    done = true;
	}

	public void run() {
	    while (true) {
		try {
		    synchronized (this) {
			if (done)
			    return;
		    }

		    final int size = testSet.size();
		    boolean doWrite;
		    if (size < idealSize * 0.66)
			doWrite = true;
		    else if (size > idealSize * 1.33)
			doWrite = false;
		    else 
			doWrite = random.nextBoolean();

		    if (doWrite) {
			final TestEntry e = TestEntries.newEntry();
			for (int i=0; i<readers.length; i++)
			    readers[i].writen(e);
			space.write((Entry)e, null, Long.MAX_VALUE);
			testSet.add(e);
		    } else {
			final int c = random.nextInt(size);
			final TestEntry e = (TestEntry)testSet.remove(c);
			for (int i=0; i<readers.length; i++)
			    readers[i].removed(e);
			if (null == space.take((Entry)e, null, 0)) {
			    final String msg = getName() + " take returned null";
			    logger.log(Level.INFO, msg);
			    setCompletionState(new TestException(msg));
			    return;
			}
		    }		    
		} catch (Throwable t) {
		    final String msg = getName() + " failure in main loop";
		    logger.log(Level.INFO, msg, t);
		    setCompletionState(new TestException(msg, t));
		    return;
		}
	    }
	}
    }

    private static class ReaderThread extends Thread {
	final private LeaseRenewalManager lrm;
	final private int batchSize;
	final private Collection testSet;
	final private int trails;
	final private JavaSpace05 space;
	private Throwable completionState = null;

	private LinkedList removals = new LinkedList();
	private LinkedList writes = new LinkedList();

	private boolean waitingOnCommit = false;

	// Current trail
	private int trailNumber = -1;
	
	private ReaderThread(int threadNum, int trails, int batchSize,
			     Collection testSet, LeaseRenewalManager lrm,
			     JavaSpace05 space)
	{
	    super("ReaderThread-" + threadNum);
	    this.lrm = lrm;
	    this.batchSize = batchSize;
	    this.trails = trails;
	    this.testSet = new java.util.HashSet(testSet);
	    this.space = space;
	}

	synchronized void setCompletionState(Throwable v) {
	    if (completionState == null)
		completionState = v;
	}

	synchronized Exception getCompletionState() throws Exception {
	    if (completionState == null) return null;
	    
	    if (completionState instanceof Exception)
		throw (Exception)completionState;

	    if (completionState instanceof Error) 
		throw (Error)completionState;

	    throw new AssertionError(completionState);
	}

	private String nameAndTrail() {
	    return getName() + ":trial:" + trailNumber + " ";
	}

	private TestException failure(String msg) throws TestException {
	    msg = nameAndTrail() + msg;
	    logger.log(Level.INFO, msg);
	    throw new TestException(msg);
	}

	private TestException failure(String msg, Throwable t)
	    throws TestException
	{
	    msg = nameAndTrail() + msg;
	    logger.log(Level.INFO, msg, t);
	    throw new TestException(msg, t);
	}

	private synchronized void removed(TestEntry e) {
	    removals.add(e);
	    if (waitingOnCommit) {
		waitingOnCommit = false;
		notifyAll();
	    }
	}

	private synchronized void writen(TestEntry e) {
	    writes.add(e);
	    if (waitingOnCommit) {
		waitingOnCommit = false;
		notifyAll();
	    }
	}

	public void run() {
	    try {
		for (trailNumber=0; trailNumber<trails; trailNumber++) {
		    trail();
		}
	    } catch (TestException t) {
		setCompletionState(t);
	    } catch (Throwable t) {
		logger.log(Level.INFO, nameAndTrail() + "failed with", t);
		setCompletionState(t);
	    }
	}

	private void trail() throws TestException {
	    synchronized (this) {
		while (waitingOnCommit) {
		    try {
			wait();
		    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
			failure("interrupted", e);
			return;
		    }
		}
	    }
		
	    logger.log(Level.INFO, nameAndTrail() + " starting trial ");
	    final Collection tmpls = TestEntries.newTemplates();
	    final Set tbf = new java.util.HashSet();
	    for (Iterator j=testSet.iterator(); j.hasNext();) {
		final TestEntry e = (TestEntry)j.next();
		if (TestEntries.isMatch(tmpls, e)) {
		    tbf.add(e);
		}
	    }
	    
	    logger.log(Level.INFO, 
		       nameAndTrail() + "looking for " + tbf.size() + " entries");
	    logger.log(Level.INFO, 
		       nameAndTrail() + "using:" + TestEntries.toString(tmpls));

	    final MatchSet cr;
	    final Lease l;
	    TestEntry e;
	    try {
		cr = space.contents(tmpls, null, 60000, Long.MAX_VALUE);

		l = cr.getLease();
		if (tbf.size() < batchSize && l != null) {
		    throw failure(
		        "failed! got lease, but should not have. tbf.size:" + 
			tbf.size());
		} else if (tbf.size() >= batchSize && l == null) {
		    throw failure(
                        "failed! did not get lease, but should have. tbf.size:"
			+ tbf.size());
		}
		    
		if (l != null) {
		    lrm.renewFor(l, Long.MAX_VALUE, 60000, null);
		}

		e = (TestEntry)cr.next();
	    } catch (Throwable t) {
		throw failure("failed with " + t + " during contents call phase",
			      t);
	    }

	    final LinkedList notInTBF = new LinkedList();
	    
	    while (e != null) {
		if (!tbf.remove(e)) {
		    notInTBF.add(e);
		    
		    // Make sure all the extras match
		    boolean found = TestEntries.isMatch(tmpls, e);
		    if (!found) {
			throw failure("failed. " + TestEntries.toString(e) +
				      " did not match any templates");
		    }		       
		}

		try {
		    e = (TestEntry)cr.next();
		} catch (Throwable t) {			
		    throw failure("failed with " + t + " during next call", t);
		}
	    }

	    final LinkedList removals;
	    final LinkedList writes;
	    synchronized (this) {
		removals = this.removals;
		writes = this.writes;
		this.removals = new LinkedList();
		this.writes = new LinkedList();
		waitingOnCommit = true;
	    }

	    final int extras = notInTBF.size();
	    final int missing = tbf.size();

	    /* Go through the entries that were written while we were
	     * going through the match set to see if any of them match
	     * the `extras' entries we found (all of the extra entries
	     * are in notInTBF). Also update the testSet 
	     */
	    while (!writes.isEmpty()) {
		final TestEntry we = (TestEntry)writes.removeFirst();
		
		// Make sure there are no dups in the ones we did
		// not know were coming
		if (writes.contains(we)) {
		    throw failure("failed." + TestEntries.toString(we) +
				  " returned by contents twice");
		}
		    
		testSet.add(we);
		notInTBF.remove(we);
	    }
		
	    // After accounting for the concurrent writes, if notInTBF
	    // is still non-empty, we got something we should not have
	    if (!notInTBF.isEmpty()) {
		for (Iterator j=notInTBF.iterator(); j.hasNext();) {
		    TestEntry ee = (TestEntry)j.next();
		    logger.log(Level.INFO, nameAndTrail() + 
			       TestEntries.toString(ee) + " not in tbf");
		    }
		
		throw failure("Set of entries not found in TBF non-empty after " +
			      "accounting for concurrent writes");
	    }

	    /* Go through the entries that were taken while we were
	     * going through the match set to see if any of them match
	     * the missing matches that were in testSet, but we could
	     * not find. Along the way update testSet 
	     */
	    while (!removals.isEmpty()) {
		final TestEntry re = (TestEntry)removals.removeFirst();
		if (!testSet.remove(re)) {
		    throw failure("Removed entry not in testSet", 
				  new AssertionError());
		}

		tbf.remove(re);
	    }


	    // After account for concurrent takes, if tbf is still
	    // non-empty we did not get something we should have
	    if (!tbf.isEmpty()) {
		final String msg = nameAndTrail() + "failed. tbf non-empty";
		logger.log(Level.INFO, msg);
		logger.log(Level.INFO, TestEntries.toString(tbf));
		throw new TestException(msg);
	    } else {
		logger.log(Level.INFO, nameAndTrail() + "success(" +
			   extras + " extras, " + missing + " missing)");
	    }

	    if (l != null) {
		try {
		    lrm.cancel(l);
		} catch (Throwable t) {
		    throw failure("failed with " + t + 
				  " while canceling MatchSet lease", t);
		}
	    }
	}
    }
}

