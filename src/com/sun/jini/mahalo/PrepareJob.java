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

import com.sun.jini.mahalo.log.ClientLog;
import com.sun.jini.thread.TaskManager;
import com.sun.jini.thread.WakeupManager;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import net.jini.core.transaction.server.ServerTransaction;
import net.jini.core.transaction.server.TransactionConstants;
import net.jini.core.transaction.server.TransactionParticipant;

/**
 * An implementation of a <code>com.sun.jini.mahalo.Job</code> which
 * interacts with a set of
 * <code>net.jini.core.transaction.server.TransactionParticipant</code>s
 * to inform them to vote.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see com.sun.jini.mahalo.Job
 * @see com.sun.jini.mahalo.ParticipantTask
 * @see net.jini.core.transaction.Transaction
 * @see net.jini.core.transaction.server.TransactionParticipant
 */
public class PrepareJob extends Job implements TransactionConstants {
    ServerTransaction tr;
    ClientLog log;
    ParticipantHandle[] handles;
    int maxtries = 5;
    /** Logger for operations related messages */
    private static final Logger operationsLogger =
       TxnManagerImpl.operationsLogger;

    /** Logger for persistence related messages */
    private static final Logger persistenceLogger =
        TxnManagerImpl.persistenceLogger;

    /**
     * Constructs an <code>PrepareJob</code>
     *
     *
     * @param tr The <code>Transaction</code> whose participants
     *           will be instructed to vote
     *
     * @param pool The <code>TaskManager</code> which provides the
     *             threads used for interacting with participants.
     *
     * @param log  The <code>ClientLog</code> used for recording
     *             recovery data.
     *
     * @param handles The array of participants which will be contacted
     *                and informed to vote
     *
     * @see com.sun.jini.thread.TaskManager
     * @see com.sun.jini.mahalo.log.ClientLog
     * @see net.jini.core.transaction.server.TransactionParticipant
     */
    public PrepareJob(Transaction tr, TaskManager pool,
		      WakeupManager wm, ClientLog log,
		      ParticipantHandle[] handles) {
	super(pool, wm);

	if (log == null)
	    throw new IllegalArgumentException("PrepareJob: PrepareJob: " +
							"log is null");

	this.log = log;

	if (!(tr instanceof ServerTransaction))
	    throw new IllegalArgumentException("PrepareJob: PrepareJob: " +
					"must be a ServerTransaction");

	this.tr =  (ServerTransaction) tr;

	if (handles == null)
	    throw new IllegalArgumentException("PrepareJob: PrepareJob: " +
					"must have participants");

	if (handles.length == 0)
	    throw new IllegalArgumentException("PrepareJob: PrepareJob: " +
					"must have participants");

	this.handles = handles;
    }


    /**
     * The work to be performed by each <code>TaskManager.Task</code>
     * is provided by the <code>Job</code> that creates it.
     * The work performed by a task belonging to the AbortJob
     * contacts a participant, instructs it to vote and
     * log appropriately.
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
            operationsLogger.entering(PrepareJob.class.getName(),
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
		        PrepareJob.class.getName(), 
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
		        PrepareJob.class.getName(),"doWork",
		        new Integer(ABORTED));
		}
	        return new Integer(ABORTED);
            }
	} catch (JobException je) {
            if (operationsLogger.isLoggable(Level.FINER)) {
                operationsLogger.exiting(
		    PrepareJob.class.getName(),"doWork", null);
	    }
	    return null;
	}
 
 
        //At this point, if participant is null, there
        //must be an error unpacking, so retry later
        if (par == null) {
            if (operationsLogger.isLoggable(Level.FINER)) {
                operationsLogger.exiting(
		    PrepareJob.class.getName(),"doWork", null);
	    }
            return null;
        }
 
        //Here we actually need to ask the participant to
        //prepare.  Note the RemoteException causes a
        //retry. Here we only log info for the cases
        //where a final outcome is available.
 
        Object response = null;
 
        try {
            vote = par.prepare(tr.mgr, tr.id);
            response = new Integer(vote);
        } catch (TransactionException bte) {
            vote = ABORTED;
            response = new Integer(vote);
        } catch (RemoteException re) {
        } catch (RuntimeException rte) {
	    vote = ABORTED;
	    response = new Integer(vote);
	}

        if (response != null) {
	    handle.setPrepState(vote);
            try {
                log.write( new PrepareRecord(handle, vote));
            } catch (com.sun.jini.mahalo.log.LogException le) {
                //the full package name used to disambiguate
                //the LogException
                if (persistenceLogger.isLoggable(Level.WARNING)) {
                    persistenceLogger.log(Level.WARNING,
	            "Problem writing PrepareRecord.", le);
	        }
//TODO - ignore?		
            }
            if (operationsLogger.isLoggable(Level.FINER)) {
                operationsLogger.exiting(
		   PrepareJob.class.getName(),"doWork", response);
	    }

	    return response;
        }
 
	if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(
		PrepareJob.class.getName(),"doWork", null);
	}
	return null;
    }


    /**
     * Creates the <code>TaskManager.Task</code>s necessary to
     * inform participants to vote.
     */
    TaskManager.Task[] createTasks() {
	TaskManager.Task[] tmp = new TaskManager.Task[handles.length];

	for (int i = 0; i < handles.length; i++) {
	    tmp[i] = 
	        new ParticipantTask(getPool(), getMgr(), this, handles[i]);
	}

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
            operationsLogger.entering(PrepareJob.class.getName(),
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
	int tmp = 0;

	checkresults:
	for (int i = 0; i < results.length; i++) {
	    tmp = ((Integer)results[i]).intValue();

	    switch(tmp) {
	      case NOTCHANGED:
		//Does not affect the prepstate
	        break;

	      case ABORTED:
		//Causes all further checks to end
		//while marking ABORTED. A single abort
		//aborts the whole transaction.

		prepstate = ABORTED;
		break checkresults;

	      case PREPARED:
		//changes the state to PREPARED only
		//if currently NOTCHANGED
		if (prepstate == NOTCHANGED)
		    prepstate = PREPARED;
		break;
	    }
	}
	Integer result = new Integer(prepstate);
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(PrepareJob.class.getName(),
                "computeResult", result);
	}
	return result;
    }
}
