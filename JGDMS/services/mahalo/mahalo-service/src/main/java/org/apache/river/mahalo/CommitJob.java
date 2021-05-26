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

import org.apache.river.mahalo.log.ClientLog;
import org.apache.river.thread.wakeup.WakeupManager;

import java.rmi.activation.ActivateFailedException;
import java.rmi.RemoteException;
import java.rmi.NoSuchObjectException;
import java.rmi.MarshalException;
import java.rmi.UnknownHostException;
import java.rmi.ConnectIOException;
import java.rmi.AccessException;
import java.rmi.ConnectException;
import java.security.AccessControlContext;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;

import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import net.jini.core.transaction.server.ServerTransaction;
import net.jini.core.transaction.server.TransactionConstants;
import net.jini.core.transaction.server.TransactionParticipant;

/**
 * An implementation of a <code>Job</code> which interacts with
 * a set of <code>TransactionParticipant</code>s to inform them
 * to roll forward changes associated with a given <code>Transaction</code>.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see org.apache.river.mahalo.Job
 * @see org.apache.river.mahalo.ParticipantTask
 * @see net.jini.core.transaction.Transaction
 * @see net.jini.core.transaction.server.TransactionParticipant
 */
class CommitJob extends Job implements TransactionConstants {
    final ServerTransaction tr;
    final ClientLog log;
    final ParticipantHandle[] handles;
    static final int maxtries = Integer.MAX_VALUE;
    static final Logger logger = TxnManagerImpl.participantLogger;

    /**
     * Constructs an <code>CommitJob</code>
     *
     *
     * @param tr The <code>Transaction</code> whose participants
     *           will be instructed to roll-forward.
     *
     * @param pool The <code>TaskManager</code> which provides the
     *             threads used for interacting with participants.
     *
     * @param log  The <code>ClientLog</code> used for recording
     *             recovery data.
     *
     * @param handles The array of participants which will be contacted
     *                and informed to roll-forward.
     *
     * @see org.apache.river.thread.TaskManager
     * @see org.apache.river.mahalo.log.ClientLog
     * @see net.jini.core.transaction.server.TransactionParticipant
     */
    public CommitJob(Transaction tr, 
		    ExecutorService pool,
		    WakeupManager wm,
		    ClientLog log,
		    ParticipantHandle[] handles,
		    AccessControlContext context) 
    {
	super(pool, wm, context);

	if (log == null)
	    throw new IllegalArgumentException("CommitJob: CommitJob: " +
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
     * The work performed by a task belonging to the CommitJob
     * contacts a participant, instructs it to roll-forward and
     * log appropriately.
     *
     * @param who The task performing the work
     *
     * @param param A parameter, of the task's choosing, useful
     *              in performing work.
     *
     * @see org.apache.river.mahalo.Job
     * @see org.apache.river.thread.TaskManager.Task
     */
    Object doWork(Runnable who, Object param) {
        ParticipantHandle handle = (ParticipantHandle)param;
        TransactionParticipant par = null;

        //Check to see if participant has state associated
	//with it on account of being recovered from a log

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST,
                "CommitJob:doWork committing handle: {0}", handle);
	}

        int vote = handle.getPrepState();
 
        switch (vote) {
	    case COMMITTED:
            case NOTCHANGED:
            case ABORTED:
                return Integer.valueOf(vote);
        }

        //Instruct the TransactionParticipant to roll forward
        //after unpacking it and checking against the
        //max retry threshold.

        if (par == null)
            par = handle.getPreParedParticipant();
 
        //If you have exhausted the max retry threshold
        //stop, so that no further attempts are made.
 
	try {
            if (attempt(who) >= maxtries) {
                return Integer.valueOf(COMMITTED);
            }
	} catch (JobException je) {
	    return null;
	}
 
        //At this point, if participant is null, there
        //must be an error unpacking, so retry later
        if (par == null)
            return null;
 
 
        //Here we actually need to instruct the participant to
        //roll forward.  Note the RemoteException causes a
        //retry. Here we only log info for the cases
        //where a final outcome is available.
 
        Object response = null;
 
        try {
            par.commit(tr.mgr, tr.id);
            response = Integer.valueOf(COMMITTED);
        } catch (TransactionException bte) {
            //The participant doesn't have record of the
            //transaction, so it must have already rolled
            //forward.
            response = Integer.valueOf(COMMITTED);
	} catch (NoSuchObjectException nsoe) {
	    //No definition for object in VM, so stop
	    //and consider committed.
	    response = Integer.valueOf(COMMITTED);
	} catch (ConnectException ce) {
	    //failure setting up connection, so give
	    //participant more time by retrying
	} catch (UnknownHostException uhe) {
	    //could not resolve host for participant, so
	    //stop and consider committed
	    response = Integer.valueOf(COMMITTED);
	} catch (ConnectIOException cioe) {
	    //multiplexed connection or cached 
	    //connection problem, give participant more time
	} catch (MarshalException me) {
	    //cannot send parameters, so stop and consider done
	    response = Integer.valueOf(COMMITTED);
	} catch (AccessException ae) {
	    //Access error on registry or rmid consider done
	    response = Integer.valueOf(COMMITTED);
	} catch (ActivateFailedException afe) {
	    //Activatable Ref Stub couldn't activate
	    //participant, so stop and consider done
	    response = Integer.valueOf(COMMITTED);
        } catch (RemoteException re) {
            //Something happened with the network, so
            //return null to retry at a later time.
        } catch (RuntimeException rte) {
            //Something happened with the participant, so
            //stop and consider done
	    response = Integer.valueOf(COMMITTED);
        }
 
 
        if (response != null) {
	    handle.setPrepState(COMMITTED);
            try {
                log.write( new ParticipantCommitRecord(handle));
            } catch (org.apache.river.mahalo.log.LogException le) {
                //the full package name used to disambiguate
                //the LogException
            }

	    return response;
        }
 
        return null;
    }


    /**
     * Creates the <code>TaskManager.Task</code>s necessary to
     * inform participants to roll-back.
     */
    Runnable[] createTasks() {
	Runnable[] tmp = new Runnable[handles.length];

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
     * @see org.apache.river.mahalo.Job
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

        Iterator i = results.values().iterator();
        while (i.hasNext()){
            tmp = ((Integer)i.next()).intValue();
            if (tmp == COMMITTED) count++;
        }

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST,
                "CommitJob:computeResult {0} participants COMMITTED", 
		Integer.valueOf(count));
	}

	return Integer.valueOf(COMMITTED);
    }
}
