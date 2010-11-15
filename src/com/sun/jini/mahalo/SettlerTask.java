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
package com.sun.jini.mahalo;

import com.sun.jini.logging.Levels;
import com.sun.jini.thread.RetryTask;
import com.sun.jini.thread.TaskManager;
import com.sun.jini.thread.WakeupManager;
import java.rmi.RemoteException;
import java.rmi.NoSuchObjectException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.transaction.TransactionException;
import net.jini.core.transaction.server.TransactionConstants;
import net.jini.core.transaction.server.TransactionManager;

/**
 * A <code>SettlerTask</code> is scheduled task, which
 * causes an unsettled transaction to settle.
 *
 * @author Sun Microsystems, Inc.
 *
 */

public class SettlerTask extends RetryTask implements TransactionConstants {
    private long tid;
    private int attempt;
    private int maxtries = Integer.MAX_VALUE;
    private TransactionManager txnmgr;

    /** Logger for operations related messages */
    private static final Logger operationsLogger = 
        TxnManagerImpl.operationsLogger;
	
    /** Logger for transactions related messages */
    private static final Logger transactionsLogger = 
        TxnManagerImpl.transactionsLogger;

    /**
     * Constructs a <code>SettlerTask</code>.
     *
     * @param manager <code>TaskManager</code> providing the threads
     *                of execution.
     *
     * @param txnmgr <code>TransactionManager</code> which owns the
     *               the transaction.
     * 
     * @param tid transaction ID
     */
    public SettlerTask(TaskManager manager, WakeupManager wm,
			    TransactionManager txnmgr, long tid) {
	super(manager, wm);

	if (txnmgr == null)
	    throw new IllegalArgumentException("SettlerTask: SettlerTask: " +
					    "txnmgr must be non-null");
	this.txnmgr = txnmgr;
	this.tid = tid;
    }

    /**
     * Inherit doc comment from supertype.
     *
     * @see com.sun.jini.thread.RetryTask
     */
    public boolean runAfter(List list, int max) {
        return false;
    }

    public boolean tryOnce() {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(SettlerTask.class.getName(), 
	        "tryOnce");
	}
        try {
	    if (attempt >= maxtries)
		return true;

	    attempt++;

	    if (transactionsLogger.isLoggable(Level.FINEST)) {
                transactionsLogger.log(Level.FINEST,
		"Attempting to settle transaction id: {0}", 
		new Long(tid));
	    }

	    int state = txnmgr.getState(tid);
	    switch(state) {
	      case VOTING:
	      case COMMITTED:
                txnmgr.commit(tid, Long.MAX_VALUE);
		break;
	    
	      case ABORTED:
		txnmgr.abort(tid, Long.MAX_VALUE);
		break;

	      default:
	        if(transactionsLogger.isLoggable(Level.WARNING)) {
		    transactionsLogger.log(Level.WARNING,
		    "Attempting to settle transaction in an invalid state: {0}", 
		    new Integer(state));
	        }
	    }

	} catch (NoSuchObjectException nsoe) {
	    if(transactionsLogger.isLoggable(Level.WARNING)) {
		transactionsLogger.log(Level.WARNING,
		"Unable to settle recovered transaction", nsoe);
	    }
//TODO -ignore?	    
        } catch (TransactionException te) {
	    if(transactionsLogger.isLoggable(Levels.HANDLED)) {
		transactionsLogger.log(Levels.HANDLED,
		"Unable to settle recovered transaction", te);
	    }
//TODO -ignore?	    
        } catch (RemoteException re) {
	    //try again
            if (operationsLogger.isLoggable(Level.FINER)) {
                operationsLogger.exiting(SettlerTask.class.getName(), 
	            "tryOnce", Boolean.valueOf(false));
	    }
	    return false;
	}

	if (transactionsLogger.isLoggable(Level.FINEST)) {
            transactionsLogger.log(Level.FINEST,
	    "Transaction id {0} was settled", 
	    new Long(tid));
	}
	
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(SettlerTask.class.getName(), 
	        "tryOnce", Boolean.TRUE);
	}

	return true;
    }
}
