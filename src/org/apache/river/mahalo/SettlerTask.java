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
package org.apache.river.mahalo;

import org.apache.river.logging.Levels;
import org.apache.river.thread.RetryTask;
import org.apache.river.thread.WakeupManager;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.util.concurrent.ExecutorService;
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

class SettlerTask extends RetryTask implements TransactionConstants {
    private final long tid;
    private final TransactionManager txnmgr;

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
    SettlerTask(ExecutorService manager, WakeupManager wm,
			    TransactionManager txnmgr, long tid) {
	super(manager, wm);

	if (txnmgr == null)
	    throw new IllegalArgumentException("SettlerTask: SettlerTask: " +
					    "txnmgr must be non-null");
	this.txnmgr = txnmgr;
	this.tid = tid;
    }

    public boolean tryOnce() {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(SettlerTask.class.getName(), 
	        "tryOnce");
	}
        try {
            // There was previously a check to see if max tries (an int) was
            // greater than Integer.MAX_VALUE that returned true, the condition was never true.

	    if (transactionsLogger.isLoggable(Level.FINEST)) {
                transactionsLogger.log(Level.FINEST,
		"Attempting to settle transaction id: {0}", 
		Long.valueOf(tid));
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
		    Integer.valueOf(state));
	        }
                System.err.println("Attempting to settle transaction in an invalid state:" + 
		    Integer.valueOf(state));
	    }

	} catch (NoSuchObjectException nsoe) {
	    if(transactionsLogger.isLoggable(Level.WARNING)) {
		transactionsLogger.log(Level.WARNING,
		"Unable to settle recovered transaction", nsoe);
	    }
//            nsoe.printStackTrace(System.err);
//TODO -ignore?	    
        } catch (TransactionException te) {
	    if(transactionsLogger.isLoggable(Levels.HANDLED)) {
		transactionsLogger.log(Levels.HANDLED,
		"Unable to settle recovered transaction", te);
	    }
//            te.printStackTrace(System.err);
//TODO -ignore?	    
        } catch (RemoteException re) {
	    //try again
            if (operationsLogger.isLoggable(Level.FINER)) {
                operationsLogger.exiting(SettlerTask.class.getName(), 
	            "tryOnce", Boolean.valueOf(false));
	    }
//            re.printStackTrace(System.err);
	    return false;
	}

	if (transactionsLogger.isLoggable(Level.FINEST)) {
            transactionsLogger.log(Level.FINEST,
	    "Transaction id {0} was settled", 
	    Long.valueOf(tid));
	}
	
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(SettlerTask.class.getName(), 
	        "tryOnce", Boolean.TRUE);
	}

	return true;
    }
}
