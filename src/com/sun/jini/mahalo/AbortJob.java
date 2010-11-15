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

import java.rmi.activation.ActivateFailedException;
import java.rmi.RemoteException;
import java.rmi.NoSuchObjectException;
import java.rmi.MarshalException;
import java.rmi.UnknownHostException;
import java.rmi.ConnectIOException;
import java.rmi.AccessException;
import java.rmi.ConnectException;
 
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
 * to inform them to roll back changes associated with a given
 * <code>net.jini.core.transaction.Transaction</code>.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see com.sun.jini.mahalo.Job
 * @see com.sun.jini.mahalo.ParticipantTask
 * @see net.jini.core.transaction.Transaction
 * @see net.jini.core.transaction.server.TransactionParticipant
 *
 */
public class AbortJob extends Job implements TransactionConstants {
    ServerTransaction tr;
    ClientLog log;
    ParticipantHandle[] handles;
    int maxtries = 5;
    static final Logger logger = TxnManagerImpl.participantLogger;
    
    /**
     * Constructs an <code>AbortJob</code>
     *
     *
     * @param tr The <code>Transaction</code> whose participants
     *           will be instructed to roll-back.
     *
     * @param pool The <code>TaskManager</code> which provides the
     *             threads used for interacting with participants.
     *
     * @param log  The <code>ClientLog</code> used for recording
     *             recovery data.
     *
     * @param handles The array of participants which will be contacted
     *                and informed to roll-back.
     *
     * @see com.sun.jini.thread.TaskManager
     * @see com.sun.jini.mahalo.log.ClientLog
     * @see net.jini.core.transaction.server.TransactionParticipant
     */
    public AbortJob(Transaction tr, TaskManager pool,
		      WakeupManager wm, ClientLog log,
		      ParticipantHandle[] handles) {
	super(pool, wm);

	if (log == null)
	    throw new IllegalArgumentException("AbortJob: AbortJob: " +
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
     * Creates the <code>TaskManager.Task</code>s necessary to
     * inform participants to roll-back.
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
     * The work to be performed by each <code>TaskManager.Task</code>
     * is provided by the <code>Job</code> that creates it.
     * The work performed by a task belonging to the AbortJob
     * contacts a participant, instructs it to roll-back and
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
        ParticipantHandle handle = (ParticipantHandle)param;
        TransactionParticipant par = null;
 
        //check if a vote already exists because it was
        //recovered from the log. In this situation,
        //we do not need to log this info since it
        //exists in the log which was used for recovery...

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST,
                "AbortJob:doWork aborting handle: {0}", handle);
	}
	int vote = 0;
 
        vote = handle.getPrepState();
 
        switch (vote) {
            case NOTCHANGED:
            case ABORTED:
                return new Integer(ABORTED);
        }
 
        //Instruct the TransactionParticipant to roll back
        //after unpacking it and checking against the
        //max retry threshold.
 
        if (par == null)
            par = handle.getPreParedParticipant();
 
        //If you have exhausted the max retry threshold
        //stop, so that no further attempts are made.
 
	try {
            if (attempt(who) > maxtries) {
                return new Integer(ABORTED);
            }
	} catch (JobException je) {
	    return null;
	}

 
 
        //At this point, if participant is null, there
        //must be an error unpacking, so retry later
        if (par == null)
            return null;
 
 
        //Here we actually need to instruct the participant to
        //roll back.  Note the RemoteException causes a
        //retry. Here we only log info for the cases
        //where a final outcome is available.
	//
	//Note: handle UnknownHostException and
	//      NoSuchObjectException (activation problem)
 
        Object response = null;
 
        try {
            par.abort(tr.mgr, tr.id);
            response = new Integer(ABORTED);
        } catch (TransactionException bte) {
            //The participant doesn't have record of the
            //transaction, so it must have already rolled
            //back.
            response = new Integer(ABORTED);
        } catch (NoSuchObjectException nsoe) {
            //No definition for object in VM, so stop
            //and consider committed.
            response = new Integer(ABORTED);
        } catch (ConnectException ce) {
            //failure setting up connection, so give
            //participant more time by retrying
        } catch (UnknownHostException uhe) {
            //could not resolve host for participant, so
            //stop and consider committed
            response = new Integer(ABORTED);
        } catch (ConnectIOException cioe) {
            //multiplexed connection or cached
            //connection problem, give participant more time
        } catch (MarshalException me) {
            //cannot send parameters, so stop and consider done
            response = new Integer(ABORTED);
        } catch (AccessException ae) {
            //Access error on registry or rmid consider done
            response = new Integer(ABORTED);
        } catch (ActivateFailedException afe) {
            //Activatable Ref Stub couldn't activate
            //participant, so stop and consider done
            response = new Integer(ABORTED);
        } catch (RemoteException re) {
            //Something happened with the network, so
            //retry at a later time.
        } catch (RuntimeException rte) {
            //Something happened with the participant, so
            //stop retrying
	    response = new Integer(ABORTED);
        }
 
        if (response != null) {
	    handle.setPrepState(ABORTED);
            try {
                log.write( new ParticipantAbortRecord(handle));
            } catch (com.sun.jini.mahalo.log.LogException le) {
                //the full package name used to disambiguate
                //the LogException
            }

	    return response;
        }
 
        return null;
    }

    /**
     * Gathers partial results submitted by tasks and produces
     * a single outcome.
     *
     * @see com.sun.jini.mahalo.Job
     */
    Object computeResult() throws JobException {
	try {
	    if (!isCompleted(0))
	        throw new ResultNotReadyException("Cannot compute result " +
					"since there are jobs pending");
	} catch (JobNotStartedException jnse) {
	    throw new ResultNotReadyException("Cannot compute result since" +
					   " jobs were not created");
	}

	int tmp = 0;
	int count = 0;

	checkresults:
	for (int i = 0; i < results.length; i++) {
	    tmp = ((Integer)results[i]).intValue();

	    if (tmp == ABORTED)
		count++;
	}

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST,
                "AbortJob:computeResult {0} participants ABORTED", 
		new Integer(count));
	}

	return new Integer(ABORTED);
    }
}
