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

import com.sun.jini.config.Config;
import com.sun.jini.thread.TaskManager;
import com.sun.jini.thread.WakeupManager;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class provides a driver for monitoring the state of transactions
 * that have blocked progress of other operations recently.  It creates
 * tasks that monitor each transaction by intermittently querying the
 * transaction's state.  If it finds that the transaction has aborted,
 * it makes sure that the local space aborts the transaction, too, so
 * that operations will cease to be blocked by the transaction.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see TxnMonitorTask
 * @see OutriggerServerImpl#monitor
 */
class TxnMonitor implements Runnable {
    /**
     * Each <code>ToMonitor</code> object represents a need to monitor
     * the given transactions, possibly under a lease.
     *
     * @see #pending
     */
    private static class ToMonitor {
	QueryWatcher	query;         // query governing interest in txns
	Collection	txns;	       // the transactions to monitor

	ToMonitor(QueryWatcher query, Collection txns) {
	    this.query = query;
	    this.txns = txns;
	}
    }

    /**
     * This list is used to contain requests to monitor interfering
     * transactions.  We use a list like this so that the
     * <code>getMatch</code> request that detected the conflict
     * doesn't have to wait for all the setup before returning -- it
     * just puts the data on this list and the <code>TxnMonitor</code>
     * pulls it off using its own thread.
     * 
     * @see OutriggerServerImpl#getMatch 
     */
    // @see #ToMonitor
    private LinkedList pending = new LinkedList();

    /** wakeup manager for <code>TxnMonitorTask</code>s */
    private final WakeupManager wakeupMgr = 
	new WakeupManager(new WakeupManager.ThreadDesc(null, true));

    /**
     * The manager for <code>TxnMonitorTask</code> objects.
     */
    private TaskManager taskManager;

    /**
     * The space we belong to.  Needed for aborts.
     */
    private OutriggerServerImpl	space;

    /**
     * The thread running us.
     */
    private Thread ourThread;

    /** Set when we are told to stop */
    private boolean die = false;

    /** Logger for logging transaction related information */
    private static final Logger logger = 
	Logger.getLogger(OutriggerServerImpl.txnLoggerName);

    /**
     * Create a new TxnMonitor.
     */
    TxnMonitor(OutriggerServerImpl space, Configuration config)
	throws ConfigurationException 
    {
	if (space == null)
	    throw new NullPointerException("space must be non-null");
	this.space = space;

	taskManager = (TaskManager)Config.getNonNullEntry(config,
	    OutriggerServerImpl.COMPONENT_NAME, "txnMonitorTaskManager", 
	    TaskManager.class, new TaskManager());

        ourThread = new Thread(this, "TxnMonitor");
	ourThread.setDaemon(true);
        ourThread.start();
    }

    public void destroy() {
        taskManager.terminate();
	wakeupMgr.stop();	

	synchronized (this) {
	    die = true;
	    notifyAll();
	}

        try {
	    ourThread.join();
	} catch(InterruptedException ie) {
	    // ignore
	}
    }

    /**
     * Return the space we're part of.
     */
    OutriggerServerImpl space() {
	return space;
    }

    /**
     * Add a set of <code>transactions</code> to be monitored under the
     * given query.
     */
    synchronized void add(QueryWatcher query, Collection transactions) {
	if (logger.isLoggable(Level.FINEST)) {
	    final StringBuffer buf = new StringBuffer();
	    buf.append("Setting up monitor for ");
	    buf.append(query);
	    buf.append(" toMonitor:");
	    boolean notFirst = false;
	    for (Iterator i=transactions.iterator(); i.hasNext();) {
		if (notFirst) {
		    buf.append(",");
		    notFirst = true;
		}
		buf.append(i.next());
	    }
	    logger.log(Level.FINEST, buf.toString());
	}

	pending.add(new ToMonitor(query, transactions));
	notifyAll();
    }

    /**
     * Add a set of <code>transactions</code> to be monitored under no
     * lease.
     */
    void add(Collection transactions) {
	add(null, transactions);
    }

    /**
     * Take pending monitor requests off the queue, creating the
     * required <code>TxnMonitorTask</code> objects and scheduling them.
     */
    public void run() {
	try {
	    ToMonitor tm;
	    for (;;)  {
		synchronized (this) {
		
		    // Sleep if nothing is pending.
		    while (pending.isEmpty() && !die)
			wait();

		    if (die)
			return;

		    tm = (ToMonitor)pending.removeFirst();
		}

		logger.log(Level.FINER, "creating monitor tasks for {0}",
			   tm.query);

		Iterator it = tm.txns.iterator();
		while (it.hasNext()) {
		    Txn txn = (Txn) it.next();
		    TxnMonitorTask task = taskFor(txn);
		    task.add(tm.query);
		}
	    }
	} catch (InterruptedException e) {
	    return;
	}
    }

    /**
     * Return the monitor task for this transaction, creating it if
     * necessary.
     */
    private TxnMonitorTask taskFor(Txn txn) {
	TxnMonitorTask task = txn.monitorTask();
	if (task == null) {
	    logger.log(Level.FINER, "creating TxnMonitorTask for {0}", 
			   txn);

	    task = new TxnMonitorTask(txn, this, taskManager, wakeupMgr);
	    txn.monitorTask(task);
	    taskManager.add(task);  // add it after we've set it in the txn
	}
	return task;
    }
}
