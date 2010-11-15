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
import com.sun.jini.mahalo.log.ClientLog;
import com.sun.jini.thread.TaskManager;
import com.sun.jini.thread.WakeupManager;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.UnknownTransactionException;
import net.jini.core.transaction.server.ServerTransaction;
import net.jini.core.transaction.server.TransactionConstants;
import net.jini.core.transaction.server.TransactionParticipant;


/**
 * An implementation of a <code>Job</code> which interacts with
 * a set of <code>TransactionParticipant</code>s to inform them
 * to vote and roll forward/back changes associated with a given
 * <code>Transaction</code>.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see com.sun.jini.mahalo.Job
 * @see com.sun.jini.mahalo.ParticipantTask
 * @see net.jini.core.transaction.Transaction
 * @see net.jini.core.transaction.server.TransactionParticipant
 */
public class PrepareAndCommitJob extends Job implements TransactionConstants {
    ServerTransaction tr;
    ClientLog log;
    ParticipantHandle handle;
    int maxtries = 5;
    
    /*
     * Field that holds the last received remote exception, if any.
     * Used as a flag for retry logic below.
     */
    private volatile RemoteException reCaught = null;
    
    /*
     * Flag used to indicate that client needs to be notified of a possible
     * indeterminate state.
     */
    private volatile boolean notifyClient = false;
    
    /** Logger for operations related messages */
    private static final Logger operationsLogger = 
       TxnManagerImpl.operationsLogger;

    /** Logger for persistence related messages */
    private static final Logger persistenceLogger = 
        TxnManagerImpl.persistenceLogger;

    /**
     * Constructs a <code>PrepareAndCommitJob</code>.
     *
     *
     * @param tr The <code>Transaction</code> whose participants
     *           will be instructed to vote and roll-forward/back.
     *
     * @param pool The <code>TaskManager</code> which provides the
     *             threads used for interacting with participants.
     *
     * @param log  The <code>ClientLog</code> used for recording
     *             recovery data.
     *
     * @param handle The array of participants which will be contacted
     *                and informed to vote and roll-forward/back.
     *
     * @see com.sun.jini.thread.TaskManager
     * @see com.sun.jini.mahalo.log.ClientLog
     * @see net.jini.core.transaction.server.TransactionParticipant
     */
    public PrepareAndCommitJob(Transaction tr, TaskManager pool,
		      WakeupManager wm, ClientLog log,
		      ParticipantHandle handle) {
	super(pool, wm);

	if (log == null)
	    throw new IllegalArgumentException("PrepareAndCommitJob: " +
					"PrepareAndCommitJob: log is null");

	this.log = log;

	if (!(tr instanceof ServerTransaction))
	    throw new IllegalArgumentException("PrepareAndCommitJob: " +
						"PrepareAndCommitJob: " +
					"must be a ServerTransaction");

	this.tr =  (ServerTransaction) tr;

	if (handle == null)
	    throw new IllegalArgumentException("PrepareAndCommitJob: " +
						"PrepareJob: " +
					"must have participants");

	this.handle = handle;
    }


    /**
     * The work to be performed by each <code>TaskManager.Task</code>
     * is provided by the <code>Job</code> that creates it.
     * The work performed by a task belonging to the CommitJob
     * contacts a participant, instructs it to vote, roll-forward/back
     * and log appropriately.
     *
     * @param who The task performing the work
     *
     * @param param A parameter, of the task's choosing, useful
     *              in performing work.
     *
     * @see com.sun.jini.mahalo.Job
     * @see com.sun.jini.thread.TaskManager.Task
     */
    Object doWork(TaskManager.Task who, Object param) {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(PrepareAndCommitJob.class.getName(), 
	        "doWork", new Object[] {who, param});
	}
	ParticipantHandle handle = (ParticipantHandle)param;
	TransactionParticipant par = null;

        //check if a vote already exists because it was
        //recovered from the log. In this situation,
        //we do not need to log this info since it
        //exists in the log which was used for recovery...
 
        int vote = handle.getPrepState();
 
        switch (vote) {
            case COMMITTED:
            case NOTCHANGED:
            case ABORTED:
            case PREPARED:
                if (operationsLogger.isLoggable(Level.FINER)) {
                    operationsLogger.exiting(
		        PrepareAndCommitJob.class.getName(), 
 	                "doWork", new Integer(vote));
		}
		return new Integer(vote);
        }
 
        //...otherwise, explicitly instruct the participant to
        //prepare after unpacking it and checking against the
        //max retry threshold
 
        if (par == null)
            par = handle.getPreParedParticipant();
 
        //If you have exhausted the max retry threshold
        //stop, so that no further attempts are made.
 
	try {
            if (attempt(who) > maxtries) {
	        if (operationsLogger.isLoggable(Level.FINER)) {
                    operationsLogger.exiting(
			PrepareAndCommitJob.class.getName(), 
	                "doWork", new Integer(ABORTED));
		}
	        return new Integer(ABORTED);
            }
	} catch (JobException je) {
	    if (operationsLogger.isLoggable(Level.FINER)) {
                operationsLogger.exiting(PrepareAndCommitJob.class.getName(), 
	            "doWork", null);
	    }
	    return null;
	}
 
 
        //At this point, if participant is null, there
        //must be an error unpacking, so retry later
        if (par == null) {
	    if (operationsLogger.isLoggable(Level.FINER)) {
                operationsLogger.exiting(PrepareAndCommitJob.class.getName(), 
	            "doWork", null);
	    }
            return null;
        }
 
        //Here we actually need to ask the participant to
        //prepare.  Note the RemoteException causes a
        //retry. Here we only log info for the cases
        //where a final outcome is available.
 
        Object response = null;
 
        try {
            vote = par.prepareAndCommit(tr.mgr, tr.id);
            response = new Integer(vote);
        } catch (UnknownTransactionException ute) {
            if (reCaught != null) {
                notifyClient = true;
            }
            vote = ABORTED;
            response = new Integer(vote);
        } catch (RemoteException re) {
            reCaught = re;
            if (operationsLogger.isLoggable(Levels.HANDLED)) {
                operationsLogger.log(Levels.HANDLED,
                    "Ignoring remote exception from participant.", re);
	    }
        } catch (RuntimeException rte) {
	    vote = ABORTED;
	    response = new Integer(vote);
	}

        if (response != null) {
	    handle.setPrepState(vote);
            try {
                log.write( new PrepareAndCommitRecord(handle, vote));
            } catch (com.sun.jini.mahalo.log.LogException le) {
                //the full package name used to disambiguate
                //the LogException
                if (persistenceLogger.isLoggable(Level.WARNING)) {
                    persistenceLogger.log(Level.WARNING,
	            "Problem writing PrepareAndCommitRecord.", le);
	        }
//TODO - ignore?		
            }
            if (operationsLogger.isLoggable(Level.FINER)) {
                operationsLogger.exiting(PrepareAndCommitJob.class.getName(), 
	            "doWork", response);
	    }

	    return response;
        }
	
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(PrepareAndCommitJob.class.getName(), 
	        "doWork", null);
	}

	return null;
    }


    /**
     * Creates the <code>TaskManager.Task</code>s necessary to
     * inform participants to vote and roll-forward/back.
     */
    TaskManager.Task[] createTasks() {
	TaskManager.Task[] tmp = new TaskManager.Task[1];

	tmp[0] = new ParticipantTask(getPool(), getMgr(), this, handle);

	return tmp;
    }


    /**
     * Gathers partial results submitted by tasks and produces
     * a single outcome.
     *
     * @see com.sun.jini.mahalo.Job
     */
    Object computeResult() throws JobException {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(PrepareAndCommitJob.class.getName(), 
	        "computeResult");
	}
	try {
	    if (!isCompleted(0))
	        throw new ResultNotReadyException("Cannot compute result " +
					"since there are jobs pending");
	} catch (JobNotStartedException jnse) {
	    throw new ResultNotReadyException("Cannot compute result since" +
					   " jobs were not created");
	}

	int prepstate = NOTCHANGED;

	prepstate = ((Integer)results[0]).intValue();

        Integer result = new Integer(prepstate);
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(PrepareAndCommitJob.class.getName(), 
	        "computeResult", result);
	}
	return result;
    }
    
    /**
     * Simple accessor that returns the the exception to send back to the
     * client. 
     */
    Exception getAlternateException() { 
        if (notifyClient)
           return reCaught; 
        else 
           return null;
    }
}
