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

import com.sun.jini.constants.TxnConstants;
import com.sun.jini.constants.ThrowableConstants;
import com.sun.jini.logging.Levels;
import com.sun.jini.thread.RetryTask;
import com.sun.jini.thread.TaskManager;
import com.sun.jini.thread.WakeupManager;

import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import java.rmi.NoSuchObjectException;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Iterator;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.core.transaction.TransactionException;
import net.jini.core.transaction.UnknownTransactionException;
import net.jini.core.transaction.server.ServerTransaction;
import net.jini.core.transaction.server.TransactionConstants;

/**
 * A task that will try to validate the state of a transaction.  This
 * uses weak references a good deal to let the other parts of the system
 * be GC'ed as necessary.
 * <p>
 * The retry mechanism is subtle, so bear with me.  The purpose is
 * to ensure that if any activity is being blocked by a given
 * transaction, that transaction will be tested at some point in
 * the future (if necessary, i.e., if it still is thought to be
 * active).  We assume it to be rare that a transactions that the
 * space thinks is active is, in fact, aborted, so the algorithm is
 * designed to guarantee the detection without a lot of overhead,
 * specifically without a lot of RMI calls.
 * <p>
 * Each task has three values: a <code>nextQuery</code> time, a
 * <code>mustQuery</code> boolean that force the next query to be
 * made, and <code>deltaT</code>, the time at which the following
 * query will be scheduled.  When the task is awakened at its
 * <code>nextQuery</code> time, it checks to see if it must make an
 * actual query to the transaction manager, which it will do if either
 * <code>mustQuery</code> is <code>true</code>, or if we know about
 * any in progress queries on the space that are blocked on the
 * transaction.  Whether or not an actual query is made,
 * <code>deltaT</code> is added to <code>nextQuery</code> to get the
 * <code>nextQuery</code> time, <code>deltaT</code> is doubled, and
 * <code>mustQuery</code> boolean is set to <code>false</code>.
 * <p>
 * There are two kinds of requests that a with which transaction
 * can cause a conflict -- those with long timeouts (such as
 * blocking reads and takes) and those that are under short timeouts
 * (such as reads and takes with zero-length timeouts).  We will
 * treat them separately at several points of the algorithm.  A
 * short timeout is any query whose expiration time is sooner than
 * the <code>nextQuery</code> time.  Any other timeout is long
 * If a short query arrives, <code>mustQuery</code> is set to 
 * <code>true</code>.
 * <p>
 * The result is that any time a transaction causes a conflict, if
 * the query on the space has not ended by the time of the
 * <code>nextQuery</code> we will attempt to poll the transaction manager.  
 * There will also poll the transaction manager if any conflict occurred
 * on a query on the space with a short timeout.
 * <p>
 * The first time a transaction causes a conflict, we schedule a
 * time in the future at which we will poll its status.  We do not
 * poll right away because often a transaction will complete on
 * its own before we get to that time, making the check
 * unnecessary.  An instant poll is, therefore, unnecessarily
 * aggressive, since giving an initial grace time will usually mean
 * no poll is made at all.  So if the first conflict occurs at
 * <i>T</i><sub>0</sub>, the <code>nextQuery</code> value will be
 * <i>T</i><sub>0</sub><code>+INITIAL_GRACE</code>, the boolean
 * will be <code>true</code> to force that poll to happen, and
 * <code>deltaT</code> will be set to <code>INITIAL_GRACE</code>.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see TxnMonitor 
 */
class TxnMonitorTask extends RetryTask
    implements TransactionConstants, com.sun.jini.constants.TimeConstants
{
    /** transaction being monitored */
    private final Txn	txn;

    /** the monitor we were made by */
    private final TxnMonitor monitor; 

    /**
     * All the queries on the space (not queries to the transaction
     * manager) waiting for <code>txn</code> to be resolved.
     * <code>null</code> until we have at least one. Represented by
     * <code>QueryWatcher</code> objects.  
     */
    private Map		queries;

    /** count of RemoteExceptions */
    private int		failCnt;

    /** 
     * The next time we need to poll the transaction manager 
     * to get <code>txn</code>'s actual state.
     */
    private long	nextQuery;

    /**
     * When we're given an opportunity to poll the transaction manager
     * for the <code>txn</code>'s state, do so.
     */
    private boolean	mustQuery;

    /** next value added to <code>nextQuery</code> */
    private long	deltaT;

    /**
     * The initial grace period before the first query.
     */
    private static final long	INITIAL_GRACE = 15 * SECONDS;

    /**
     * The retry time when we have an encountered an exception
     */
    private static final long	BETWEEN_EXCEPTIONS = 15 * SECONDS;

    /**
     * The largest value that <code>deltaT</code> will reach.
     */
    private static final long	MAX_DELTA_T = 1 * HOURS;

    /**
     * The maximum number of failures allowed in a row before we simply
     * give up on the transaction and consider it aborted.
     */
    private static final int	MAX_FAILURES = 3;

    /** Logger for logging transaction related information */
    private static final Logger logger = 
	Logger.getLogger(OutriggerServerImpl.txnLoggerName);

    /**
     * Create a new TxnMonitorTask.
     */
    TxnMonitorTask(Txn txn, TxnMonitor monitor,
		   TaskManager manager, WakeupManager wakeupMgr) {
	super(manager, wakeupMgr);
	this.txn = txn;
	this.monitor = monitor;
	nextQuery = startTime();	// retryTime will add INITIAL_GRACE
	deltaT = INITIAL_GRACE;
	mustQuery = true;
    }

    /**
     * Return the time of the next query, bumping <code>deltaT</code> as
     * necessary for the next iteration.  If the transaction has voted
     * <code>PREPARED</code> or the manager has been giving us a
     * <code>RemoteException</code>, we should retry on short times;
     * otherwise we back off quickly.
     */
    public long retryTime() {
	if (failCnt == 0 && txn.getState() != PREPARED) {      // no failures
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "{0} retryTime adds {1}", 
			   new Object[]{this, new Long(deltaT)});
	    }

	    nextQuery += deltaT;
	    if (deltaT < MAX_DELTA_T)
		deltaT = Math.min(deltaT * 2, MAX_DELTA_T);
	} else {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "{0} retryTime adds {1} (for {2})", 
			   new Object[]{this, new Long(BETWEEN_EXCEPTIONS), 
			       (failCnt != 0 ? "failure" : "PREPARED")});
	    }
	    nextQuery += BETWEEN_EXCEPTIONS;
	}
	return nextQuery;
    }

    /**
     * We can run in parallel with any task, so just return
     * <CODE>false</CODE>.  
     */
    public boolean runAfter(java.util.List tasks, int size) {
	return false;
    }

    /**
     * Add a ``sibling'' transaction, one that is now blocking progress
     * on one of the same entries.  For example, if a client is blocked
     * on a <code>read</code>, another transaction can read the same
     * entry, thereby also blocking that same client.  This means that
     * the transaction for the second <code>read</code> must be
     * watched, too.  The list of queries for the second transaction
     * might be less that the list of those in this transaction, but
     * the process of figuring out the subset is too expensive, since
     * we have tried to make the checking process itself cheap,
     * anyway.  So we add all queries this task is currently monitoring
     * to the task monitoring the second transaction.  If there are
     * no queries, then the blocking occurred because of a short query
     * or all the queries have expired, in which case the second transaction
     * isn't blocking the way of anything currently, so this method does
     * nothing.
     * <p>
     * Of course, in order to avoid blocking the thread that is calling
     * this (which is trying to perform a <code>read</code>, after
     * all), we simply add each lease in this task to the monitor's
     * queue.
     *
     */
    // @see TxnEntryHandle#monitor
    //!! Would it be worth the overhead to make TxnEntryHandle.monitor
    //!! search for the transaction with the smallest set of leases?  -arnold
    synchronized void addSibling(Txn txn) {
	if (queries == null || queries.size() == 0)
	    return;
	Collection sibling = Collections.nCopies(1, txn);
	Iterator it = queries.keySet().iterator();
	while (it.hasNext()) {
	    QueryWatcher query = (QueryWatcher)it.next();
	    if (query != null)	// from a weak map, so might be null
		monitor.add(query, sibling);
	}
    }

    /**
     * Try to see if this transaction should be aborted.  This returns
     * <code>true</code> (don't repeat the task) if it knows that the
     * transaction is no longer interesting to anyone.
     */
    public boolean tryOnce() {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "{0} attempt {1} mustQuery:{2}", 
	        new Object[]{this, new Integer(attempt()), 
			     new Boolean(mustQuery) });
	}

	/*
	 * The first time we do nothing, since RetryTask invokes run first,
	 * but we want to wait a bit before testing the transaction.
	 */
	if (attempt() == 0)
	    return false;

	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "{0} txn.getState() = {1}", 
	        new Object[]{this, new Integer(txn.getState())});
	}

	// not active or prepared == no longer blocking
	int txnState = txn.getState();
	if (txnState != ACTIVE && txnState != PREPARED)
	    return true;

	// if we're prepared, test every time -- this shouldn't take long
	mustQuery |= (txnState == PREPARED);

	/*
	 * Go through the resources to see if we can find one still active
	 * that cares.  Must be synchronized since we test, then clear --
	 * another thread that set the flag between the test and clear
	 * would have its requirements lost.
	 */
	synchronized (this) {
	    if (!mustQuery) {		// then try resources
		if (queries == null)	// no resources, so nobody wants it
		    return false;	// try again next time

		Iterator it = queries.keySet().iterator();
		boolean foundNeed = false;

		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "{0} nextQuery {1}", 
			       new Object[]{this, new Long(nextQuery)});
		}

		while (it.hasNext()) {
		    QueryWatcher query = (QueryWatcher)it.next();
		    if (query == null)     // gone -- the map will reap it
			continue;
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(Level.FINEST, 
				   "{0} query.getExpiration() {1}", 
				   new Object[]{this, 
				       new Long(query.getExpiration())});
		    }

		    if (query.getExpiration() < nextQuery || 
			query.isResolved())
			it.remove();	// expired, so we don't care about it
		    else {
			foundNeed = true;
			break;
		    }
		}

		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "{0} foundNeed = {1}", 
			       new Object[]{this, new Boolean(foundNeed)});
		}

		if (!foundNeed)		// nobody wants it
		    return false;	// try again next time
	    }
	    mustQuery = false;		// clear it for next time
	}

	/*
	 * Now we know (a) the transaction itself is alive, and (b) some
	 * lease still cares.  Make sure it's still active as far as the
	 * it knows, and if it is, then ask the manager about it.
	 */
	ServerTransaction tr;
	try {
	    /* This may fix a broken Txn, if it does it won't get moved
	     * from the broken to the unbroken list. It will get
	     * moved eventually, but it does seem unfortunate it does
	     * not happen immediately
	     */
	    tr = txn.getTransaction(
		monitor.space().getRecoveredTransactionManagerPreparer());
	} catch (RemoteException e) {
	    final int cat = ThrowableConstants.retryable(e);

	    if (cat == ThrowableConstants.BAD_INVOCATION ||
		cat == ThrowableConstants.BAD_OBJECT)
	    {
		// Not likely to get better, give up
		logUnpackingFailure("definite exception", Level.INFO,
				    true, e);				    
		return true;
	    } else if (cat == ThrowableConstants.INDEFINITE) {
		// try, try, again
		logUnpackingFailure("indefinite exception", Levels.FAILED,
				    false, e);				    
		mustQuery = true;
		return false;
	    } else if (cat == ThrowableConstants.UNCATEGORIZED) {
		// Same as above but log differently.
		mustQuery = true;
		logUnpackingFailure("uncategorized exception", Level.INFO,
				    false, e);				    
		return false;
	    } else {
		logger.log(Level.WARNING, "ThrowableConstants.retryable " +
			   "returned out of range value, " + cat,
			   new AssertionError(e));
		return false;
	    }
	} catch (IOException e) {
	    // Not likely to get better
	    logUnpackingFailure("IOException", Level.INFO, true, e);
	    return true;
	} catch (RuntimeException e) {
	    // Not likely to get better
	    logUnpackingFailure("RuntimeException", Level.INFO, true, e);
	    return true;
	} catch (ClassNotFoundException e) {
	    // codebase probably down, keep trying
	    logUnpackingFailure("ClassNotFoundException", Levels.FAILED, 
				false, e);
	    mustQuery = true;
	    return false;
	}

	if (logger.isLoggable(Level.FINEST))
	    logger.log(Level.FINEST, "{0} tr = {1}", new Object[]{this, tr});

	int trState;
	try {
	    trState = tr.getState();
	} catch (TransactionException e) {
	    if (logger.isLoggable(Level.INFO))
		logger.log(Level.INFO, "Got TransactionException when " +
		    "calling getState on " + tr + ", dropping transaction " +
		    tr.id, e);
	    trState = ABORTED;
	} catch (NoSuchObjectException e) {
	    /* It would be epsilon better to to give up immediately
	     * if we get a NoSuchObjectException and we are in the
	     * active state, however, the code to do this would
	     * be very complicated since we need to hold a lock to
	     * while reading and acting on the state.
	     */
	    if (++failCnt >= MAX_FAILURES) {
		if (logger.isLoggable(Level.INFO)) {
		    logger.log(Level.INFO, "Got NoSuchObjectException when " +
			"calling getState on " + tr + ", this was the " +
			failCnt + " RemoteException, dropping transaction" +
			tr.id, e);
		}
		trState = ABORTED;
	    } else {
		if (logger.isLoggable(Levels.FAILED)) {
		    logger.log(Levels.FAILED, "Got NoSuchObjectException " +
			"when calling getState on " + tr + ", failCount = " +
			failCnt + ", will retry", e);
		}
		mustQuery = true;      // keep on trying
		return false;	       // try again next time
	    }
	} catch (RemoteException e) {
	    if (++failCnt >= MAX_FAILURES) {
		/* abort if we are not prepared and not already 
		 * aborted. If prepared retry, otherwise
		 * we're done. Check state and make any abort() call
		 * atomically so we can't accidently abort a prepared
		 * transaction.
		 */
		synchronized (txn) {
		    switch (txn.getState()) {
		      case ACTIVE:
			// Safe to abort, give up
			if (logger.isLoggable(Level.INFO)) {
			    logger.log(Level.INFO, "Got RemoteException " +
			        "when calling getState on " + tr + ", this " +
                                "was " + failCnt + " RemoteException, " +
				"dropping active transaction " + tr.id, e);
			}

			try {
			    monitor.space().abort(tr.mgr, tr.id);
			    return true;
			} catch (UnknownTransactionException ute) {
			    throw new AssertionError(ute);
			} catch (UnmarshalException ume) {
			    throw new AssertionError(ume);
			}
		      case PREPARED:
		        final Level l = (failCnt%MAX_FAILURES == 0)?
			    Level.INFO:Levels.FAILED;
			if (logger.isLoggable(l)) {
			    logger.log(l, "Got RemoteException when calling " +
				"getState on " + tr + ", this was " + 
				failCnt + " RemoteException, will keep " +
				"prepared transaction " + tr.id, e);
			}

			// Can't give up, keep on trying to find real state
			mustQuery = true;
			return false;
	 	      case ABORTED:
		      case COMMITTED:
			// done
			return true;
		      default:
			throw new AssertionError("Txn in unreachable state");
		    }
		}
	    } else {
		// Don't know, but not ready to give up
		if (logger.isLoggable(Levels.FAILED)) {
		    logger.log(Levels.FAILED, "Got RemoteException when " +
			"calling getState on " + tr + ", failCount = " +
			failCnt + ", will retry", e);
		}

		mustQuery = true;      // keep on trying
		return false;	       // try again next time
	    }
	}
    
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "{0} trState = {1}", 
		       new Object[]{this, new Integer(trState)});
	}

	failCnt = 0;		       // reset failures -- we got a response

	/*
	 * If the two states aren't the same, the state changed and we
	 * need to account for that locally here by calling the method
	 * that would make the change (the one we should have gotten.
	 * (We use the external forms of abort, commit, etc., because
	 * they are what the manager would call, and therefore these
	 * calls will always do exactly what the incoming manager
	 * calls would have done.  I don't want this to be fragile by
	 * bypassing those calls and going straight to the Txn
	 * object's calls, which might skip something important in the
	 * OutriggerServerImpl calls).
	 */

	if (trState != txnState) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER, 
		    "{0} mgr state[{1}] != local state [{2}]", 
		    new Object[]{this,
				 TxnConstants.getName(trState),
				 TxnConstants.getName(txnState)});
	    }

	    try {
		switch (trState) {
		  case ABORTED:
		    logger.log(Level.FINER, "{0} moving to abort", this);
			  
		    monitor.space().abort(tr.mgr, tr.id);
		    return true;

		  case COMMITTED:
		    logger.log(Level.FINER, "{0} moving to commit", this);

		    monitor.space().commit(tr.mgr, tr.id);
		    return true;
		}
	    } catch (UnknownTransactionException e) {
		// we must somehow have already gotten the abort() or
		// commit(), and have therefore forgotten about the
		// transaction, while this code was executing
		return true;
	    } catch (UnmarshalException ume) {
		throw new AssertionError(ume);
	    }

	    // we can't fake anything else -- the manager will have to call
	    // us
	}

	logger.log(Level.FINEST, "{0} return false", this);

	return false;			// now we know so nothing more to do
    }

    /**
     * Add in a resource.  The lease may already be in, in which case it is
     * ignored, or it may be null, in which case it was a non-leased probe
     * that was blocked and we simply set <code>mustQuery</code> to
     * <code>true</code>.
     */
    synchronized void add(QueryWatcher query) {
	if (query == null || query.getExpiration() <= nextQuery) {
	    if (logger.isLoggable(Level.FINEST))
		logger.log(Level.FINEST, "adding resource to task -- SHORT");
	    mustQuery = true;
	} else {
	    if (logger.isLoggable(Level.FINEST))
		logger.log(Level.FINEST, "adding resource to task -- LONG");
	    if (queries == null)
		queries = new WeakHashMap();// we use it like a WeakHashSet
	    queries.put(query, null);
	}
    }

    /** Log failed unpacking attempt attempt */
    private void logUnpackingFailure(String exceptionDescription, Level level,
				     boolean terminal, Throwable t) 
    {
	if (logger.isLoggable(level)) {
	    logger.log(level, "Encountered " + exceptionDescription +
		"while unpacking exception to check state, " +
		(terminal?"dropping":"keeping") +  " monitoring task", t);
	}
    }

}
