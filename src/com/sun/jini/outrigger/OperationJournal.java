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
package com.sun.jini.outrigger;

import java.util.SortedSet;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**   
 * Maintain a journal of operations on entries (writes, takes, and
 * resolution of locks) and a thread that will process the
 * journal. This journal is kept for three reasons. One is so events
 * can be generated asynchronously from the writes and transaction
 * commits that cause them. Second so blocked queries can be alerted
 * to changes that will allow them to be resolved.
 * <p>
 * Third to make sure that transitions occur between the time a query
 * starts its initial search and the time it adds its watcher to the
 * <code>TransitionWatchers</code> object do not get missed.  This
 * last goal is accomplished by queries calling
 * <code>newTransitionIterator</code> before initial search, doing the
 * initial search, adding their watcher to the
 * <code>TransitionWatchers</code> object and then calling
 * <code>watcherRegistered</code> on the
 * <code>TransitionIterator</code> and then feeding their watcher the
 * chain of transitions yielded by the
 * <code>TransitionIterator</code>.  (skipping any transitions with
 * entries that don't match).
 * <p>
 * Each operation that is recored in the journal is assigned an
 * <em>ordinal</em>. Operations with higher ordinals must be considered
 * to have taken place after operations with lower ordinals.
 */
class OperationJournal extends Thread {
    /** The object to ask about who is interested in a transition */
    private final TransitionWatchers watchers;

    /** The current tail of the transitions list. */
    private JournalNode tail;

    /**
     * The <code>JournalNode</code> we are currently processing or if
     * none are in process the last one we processed.
     */
    private JournalNode lastProcessed;

    /** If <code>true</code> stop thread */
    private boolean dead = false;

    /** The last ordinal value used */
    private long lastOrdinalUsed = 1;

    /** Logger for logging exceptions */
    private static final Logger logger = 
	Logger.getLogger(OutriggerServerImpl.opsLoggerName);

    /**
     * The nodes of our Journal
     */
    private class JournalNode {
	/** The next node in the journal */
	private JournalNode next;

	/** The sequence number of this journal entry */
	private final long ordinal;

	/** The contents of this journal entry */
	private final Object payload;

	/**
	 * Create a new <code>JournalNode</code> with the specified
	 * value for the <code>payload</code>, <code>null</code> for
	 * <code>next</code>, and the appropriate value for
	 * <code>ordinal</code>.
	 * <p>
	 * Assumes the lock on the <code>OperationJournal</code> is held,
	 * @param payload The value for the payload field.
	 */
	private JournalNode(Object payload) {
	    ordinal = ++lastOrdinalUsed;
	    this.payload = payload;
	}

	/**
	 * Set the next element in the list.
	 * @param n the new value for the next field.
	 * @throws IllegalStateException if the next field has
	 * already been set.
	 * @throws NullPointerException if <code>n</code> is 
	 * <code>null</code>.
	 */
	private synchronized void setNext(JournalNode n) {
	    if (next != null)
		throw new IllegalStateException("Already has next");

	    if (n == null)
		throw new NullPointerException("n must be non-null");

	    next = n;
	}

	/**
	 * Get the next element in the journal.
	 * @return the next element in the list, or <code>null</code>
	 * if there is currently no next element.
	 */
	private synchronized JournalNode getNext() {
	    return next;
	}
    }

    /**
     * <code>JournalNode</code> payload value used for 
     * caught up markers.
     */
    private class CaughtUpMarker {
	/** The watcher to notify */
	private final IfExistsWatcher watcher;

	/** 
	 * Create a new <code>CaughtUpMarker</code> that
	 * will notify the given <code>watcher</code>.
	 * @throws NullPointerException if <code>watcher</code> is 
	 *         <code>null</code>.     
	 */
	private CaughtUpMarker(IfExistsWatcher watcher) {
	    if (watcher == null)
		throw new NullPointerException("watcher must not be null");
	    this.watcher = watcher;
	}
    }

    /**
     * An iterator that will yield (in the order they were posted) all the
     * <code>EntryTransition</code>s added after the iterator was
     * created and processed before <code>watcherRegistered</code> was
     * called. This call assumes it is only used by a single thread.
     */
    class TransitionIterator {
	/** 
	 * The place to end, <code>null</code> if 
	 * <code>watcherRegistered</code> has not yet 
	 * been called.
	 */
	private JournalNode end;

	/** Our current position in the journal */
	private JournalNode current;

	/**
	 * Create a new <code>TransitionIterator</code> that will
	 * start with the first <code>EntryTransition</code> that
	 * appears in the journal after the passed
	 * <code>JournalNode</code>.
	 * @param node Start the iteration with the first
	 *        <code>JournalNode</code> after node
	 *        that is for a <code>EntryTransition</code>.
	 * 
	 * @throws NullPointerException if node is <code>null</code>.
	 */
	private TransitionIterator(JournalNode node) {
	    if (node == null)
		throw new NullPointerException("node must be non-null");

	    current = node;
	}

	/**
	 * Return the next <code>EntryTransition</code> in the
	 * sequence, or <code>null</code> if the end
	 * of the sequence has been reached.
	 * @return The next <code>EntryTransition</code> in the
	 *         sequence, or <code>null</code> if the
	 *         end of the sequence has been reached.
	 * @throws IllegalStateException if 
	 *         <code>watcherRegistered</code> has not yet 
	 *         been called.
	 */
	EntryTransition next() { 
	    if (end == null)
		throw new IllegalStateException(
		    "watcherRegistered() not yet called");

	    /* We don't normally go off the end, but
	     * we set current equal to null when we are done
	     */
	    if (current == null)
		return null;

	    /* Note, the logic in here is a bit tricky since
	     * current is pre-incremented.
	     */

	    // Skip if payload is not an EntryTransition
	    Object payload = current.payload;
	    while (true) {
		if (current == end) {
		    /* This is the last one...still need to return 
		     * it's payload if applicable.
		     */
		    current = null;
		    if (payload instanceof EntryTransition)	
			// Might be null, but that's ok
			return (EntryTransition)payload;
		    
		    return null;
		}

		current = current.getNext();	    
		assert current != null : "Iteration when off end";

		if ((payload != null) && 
		    (payload instanceof EntryTransition)) 
		{
		    return (EntryTransition)payload;
		}		    

		payload = current.payload;
	    }
	}	

	/**
	 * Set the end of the iteration to ensure that
	 * any <code>EntryTransition</code> added after 
	 * this iterator was created will either be returned by
	 * this iterator, or passed to the process() method
	 * of any watcher that was added to the watcher associated
	 * with the <code>OperationJournal</code> before
	 * this method was called.
	 * @throws IllegalStateException if 
	 *         <code>watcherRegistered</code> has been called.
	 */
	void watcherRegistered() {
	    if (end != null)
		throw new IllegalStateException(
		    "watcherRegistered() called more than once");

	    end = lastProcessed(current);

	    if (current == end) {
		/* Noting has been processed since we were created.
		 * There are no elements in the iteration.
		 */
		current = null;
		return;
	    }

	    /* Skip the tail when we were created, we don't
	     * need to return it since it was in the journal before
	     * we were created.
	     */
	    current = current.getNext();
	}

	/**
	 * Return the ordinal of the last operation posted
	 * when this iterator was created. All the 
	 * <code>EntryTransition</code>s yielded by this
	 * iterator will have higher ordinals.
	 * @return the current ordinal when this 
	 *         iterator was created.
	 * @throws IllegalStateException if 
	 *         <code>watcherRegistered</code> has been called.
	 */
	long currentOrdinalAtCreation() {
	    if (end != null)
		throw new IllegalStateException(
		    "watcherRegistered() has been called");

	    return current.ordinal;
	}
    }

    /**
     * Create a new <code>OperationJournal</code>.
     * @param watchers Set of watchers that need
     *                 to be asked if they are interested in the transitions.
     * @throws NullPointerException if watchers is <code>null</code>.
     */
    OperationJournal(TransitionWatchers watchers) {
	super("OperationJournal");
	if (watchers == null)
	    throw new NullPointerException("watchers must be non-null");
	this.watchers = watchers;
	synchronized (this) {
	    tail = new JournalNode(null);
	}
	lastProcessed = tail;	
    }

    /**
     * Records an operation on an entry. This method should be called
     * <em>after</em> the transition has been made visible in 
     * <code>contents</code> (including any subsidiary 
     * objects such as the appropriate <code>EntryHandle</code>).
     * @param transition an object describing the visibility
     *        transition of an entry.
     * @throws NullPointerException if <code>transition</code> is
     *         <code>null</code>.
     */
    synchronized void recordTransition(EntryTransition transition) {
	if (transition == null)
	    throw new NullPointerException("transition must be non-null");

	post(new JournalNode(transition));
    }

    /**
     * Schedules a job that will call the <code>caughtUp</code> method
     * on the passed <code>IfExistsWatcher</code> after the last
     * posted <code>EntryTransition</code> is processed.
     * @param watcher The watcher to notify when it is caught up.
     * @throws NullPointerException if <code>watcher</code> is 
     *         <code>null</code>.     
     */
    synchronized void markCaughtUp(IfExistsWatcher watcher) {
	post(new JournalNode(new CaughtUpMarker(watcher)));
    }

    /**
     * Return the ordinal of the last operation posted.
     * Any operations with a higher ordinal should be considered
     * to have taken place after this point in time.
     * @return the ordinal of the last operation posted.
     */
    synchronized long currentOrdinal() {
	return lastOrdinalUsed;
    }

    /**
     * Post a <code>JournalNode</code> 
     * @param node The node to post.
     */
    private void post(JournalNode node) {
	tail.next = node;
	tail = node;
	notifyAll();
    }

    /**
     * Return an iterator that can latter be used to 
     * get all the <code>EntryTransition</code>s added after 
     * this point that have been processed.
     * <p>
     * We synchronize to make sure the initial state
     * of the returned transition has been fetched from main memory.
     * @return An iterator that can latter be used to 
     * get all the <code>EntryTransition</code>s added after 
     * this point that have been processed.
     */
    synchronized TransitionIterator newTransitionIterator() {
	return new TransitionIterator(tail);
    }

    /**
     * Return the node currently being processed, or
     * if no entry is currently being processed the 
     * last one that was processed.  A watcher
     * that was added to the <code>TransitionWatchers</code> object
     * associated with this object before this call was made is
     * guaranteed to be asked by the journal about any transition posted
     * after the node returned by this method was posted. 
     * @param noEarlierThan The returned <code>JournalNode</code>
     *        is guaranteed to have not been posted before
     *        <code>noEarlierThan</code>. If the last
     *        node processes was posted before
     *        <code>noEarlierThan</code>, then 
     *        <code>noEarlierThan</code> will be returned.
     * @return The last node that has been at least
     *         partially processed.
     */
    private synchronized JournalNode lastProcessed(
	JournalNode noEarlierThan) 
    {
	if (lastProcessed.ordinal < noEarlierThan.ordinal) {
	    return noEarlierThan;
	}

	return lastProcessed;
    }

    /**
     * Terminate queue processing.
     */
    synchronized void terminate() {
	dead = true;
	notifyAll();
    }

    /**
     * Loop pulling transitions off the queue and
     * process them by getting the set of interested watchers
     * from the <code>TransitionWatchers</code> object associated
     * with this object and then calling process on each
     * of the watchers.
     */
    public void run() {
	while (!dead) {
	    try {
		// Wait until there is something to process
		synchronized (this) {
		    JournalNode n = lastProcessed.getNext();
		    while (n == null && !dead) {
			wait();
			n = lastProcessed.getNext();
		    }

		    if (dead)
			return;

		    lastProcessed = n;
		}

		// Process based on payload
		final Object payload = lastProcessed.payload;

		if (payload == null) {
		    throw new 
			AssertionError("JournalNode with null payload");
		} else if (payload instanceof EntryTransition) {
		    final EntryTransition t = (EntryTransition)payload;
		    final SortedSet set = 
			watchers.allMatches(t, lastProcessed.ordinal);
		    final long now = System.currentTimeMillis();

		    for (Iterator i=set.iterator(); i.hasNext() && !dead; ) {
			final TransitionWatcher watcher = 
			    (TransitionWatcher)i.next();
			watcher.process(t, now);
		    }
		} else if (payload instanceof CaughtUpMarker) {
		    ((CaughtUpMarker)payload).watcher.caughtUp();
		} else {
		    throw new 
			AssertionError("JournalNode with unknown payload:" +
				       payload.getClass());
		}
	    } catch (InterruptedException e) {
		// fin
		return;
	    } catch (Throwable t) {
		try {
		    logger.log(Level.INFO,
			       "OperationJournal.run encountered " +
			           t.getClass().getName() + ", continuing",
			       t);
		} catch (Throwable tt) {
		    // don't let a problem in logging kill the thread
		}
	    }
	}
    }
}
