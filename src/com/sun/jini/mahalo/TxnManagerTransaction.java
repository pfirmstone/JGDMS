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

import com.sun.jini.constants.TimeConstants;
import com.sun.jini.constants.TxnConstants;
import com.sun.jini.landlord.LeasedResource;
import com.sun.jini.logging.Levels;
import com.sun.jini.mahalo.log.ClientLog;
import com.sun.jini.mahalo.log.LogException;
import com.sun.jini.mahalo.log.LogManager;
import com.sun.jini.thread.TaskManager;
import com.sun.jini.thread.WakeupManager;

import java.rmi.RemoteException;
import java.util.Enumeration;

import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Vector;

import net.jini.core.transaction.CannotAbortException;
import net.jini.core.transaction.CannotCommitException;
import net.jini.core.transaction.CannotJoinException;
import net.jini.core.transaction.TimeoutExpiredException;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import net.jini.core.transaction.server.CrashCountException;
import net.jini.core.transaction.server.ServerTransaction;
import net.jini.core.transaction.server.TransactionConstants;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.transaction.server.TransactionParticipant;
import net.jini.id.Uuid;
import net.jini.security.ProxyPreparer;

/**
 * TxnManagerTransaction is a class which
 * captures the internal representation of a transaction
 * in the TxnManagerImpl server.  This class is associated
 * with a transaction id.
 * The information encapsulated includes the list of participants
 * which have joined the transaction, the state of the
 * transaction, the crash.
 *
 * The user of a ParticipantHolder must make the association
 * between an instance of a ParticipantHolder and some sort
 * of key or index.
 *
 * @author Sun Microsystems, Inc.
 *
 */
class TxnManagerTransaction
    implements TransactionConstants, TimeConstants, LeasedResource
{
    static final long serialVersionUID = -2088463193687796098L;

   /*
    * Table of valid state transitions which a
    * TransactionManager may make.
    *
    * This represents the following diagram from the
    * Jini Transaction Spec:
    *
    *  ACTIVE ----> VOTING ------->COMMITTED
    *     \           |
    *      \          |
    *       ---------------------->ABORTED
    *
    *
    *		    ACTIVE  VOTING  PREPARED  NOTCHANGED  COMMITTED  ABORTED
    * ----------------------------------------------------------------------
    * ACTIVE	    true     true   false     false       false       true
    * VOTING        false    true   false     false       true        true
    * PREPARED      false    false  false     false       false       false
    * NOTCHANGED    false    false  false     false       false       false
    * COMMITTED     false    false  false     false       true        false
    * ABORTED       false    false  false     false       false       true
    *
    * The table is indexed using the ordered pair
    * <current_state, next_state>.  A value of true means
    * that the transition is possible, while a false
    * means that the transition is not possible.
    *
    * Note:  Some rows are "{false, false, false, false}" as
    * 	     unused filler to account for the fact that the
    *	     TransactionManager's valid states are a subset
    *	     of the TransactionConstants.
    *
    *    <zero>
    *    ACTIVE = 1
    *    VOTING = 2
    *    PREPARED = 3
    *    NOTCHANGED = 4
    *    COMMITTED = 5
    *    ABORTED = 6
    */
    private static final boolean states[][] = {
	/* <zero>    */ {false, false, false, false, false, false, false},
	/* ACTIVE    */ {false, true,  true,  false, false, false, true},
	/* VOTING    */ {false, false, true,  false, false, true,  true},
	/* PREPARED  */ {false, false, false, false, false, false, false},
	/* NOTCHANGED*/ {false, false, false, false, false, false, false},
	/* COMMITTED */ {false, false, false, false, false, true,  false},
	/* ABORTED   */ {false, false, false, false, false, false, true}};

    /**
     * @serial
     */
    private List parts = new Vector();

    /**
     * @serial
     */
    private final ServerTransaction str;

    /**
     * @serial
     */
    private int trstate;

    /**
     * @serial
     */
    private long expires;		//expiration time

    /**
     * @serial
     */
    private LogManager logmgr;


   /**
    * "Parallelizing" the interaction between the manager
    * and participants means using threads to interact with
    * participants on behalf of the manager. In the thread
    * pool model, a TaskManager provides a finite set of
    * threads used to accomplish a variety of tasks.
    * A Job encapsulates a body of work which needs to be
    * performed during the two-phase commit: preparing,
    * committing and aborting.  Each work item is broken
    * into smaller pieces of work- interactions with a
    * single participant- each assigned to a task.
    *
    * When a transaction is committing, a PrepareJob is
    * created and its tasks are scheduled.  After completion,
    * the PrepareJob's outcome is computed. Depending on
    * the outcome, either an AbortJob or CommitJob is
    * scheduled.  When a transaction is aborted, an AbortJob
    * is scheduled.
    *
    * A caller may specify a timeout value for commit/abort.
    * The timeout represents the length of time a caller is
    * willing to wait for participants to be instructed to
    * roll-forward/back.  Should this timeout expire, a
    * TimeoutExpiredException is thrown.  This causes the
    * caller's thread to return back to the caller.  Someone
    * needs to finish contacting all the participants.  This
    * is accomplished by the SettlerTask.  SettlerTasks must
    * use a different thread pool from what is used by the
    * various Job types, otherwise deadlock will occur.
    *
    * @serial
    */
    private TaskManager threadpool;

    /**
     * @serial
     */
    private WakeupManager wm;

    /**
     * @serial
     */
    private TxnSettler settler;

    /**
     * @serial
     */
    private Job job;

    /**
     * @serial
     */
    private Uuid uuid;

   /**
    * Interlock for the expiration time since
    * lease renewal which set it may compete
    * against lease checks which read it.
    *
    * @serial
    */
    private Object leaseLock = new Object();


   /**
    * Interlock for Jobs is needed since many
    * threads on behalf of many clients can
    * simultaneously access or modify the Job
    * associated with this transaction when
    * when attempting to prepare, roll forward
    * or roll back participants.
    *
    * @serial
    */
    private Object jobLock = new Object();


   /**
    * Interlock for transaction state needed
    * since many threads on behalf of many
    * clients can simultaneously access or
    * attempt to modify this transaction's
    * state as a side effect of calling
    * commit or abort.
    *
    * @serial
    */
    private Object stateLock = new Object();


    /** Logger for operation related messages */
    private static final Logger operationsLogger = 
        TxnManagerImpl.operationsLogger;

    /** Logger for transaction related messages */
    private static final Logger transactionsLogger = 
        TxnManagerImpl.transactionsLogger;

    /**
     * Constructs a <code>TxnManagerTransaction</code>
     *
     * @param mgr	<code>TransactionManager</code> which owns
     *			this internal representation.
     * @param logmgr	<code>LogManager</code> responsible for
     *			recording COMMITTED and ABORTED transactions
     *			to stable storage.
     *
     * @param id	The transaction id
     *
     * @param threadpool The <code>TaskManager</code> which provides
     *			 the pool of threads used to interact with
     *			 participants.
     *
     * @param settler	TxnSettler responsible for this transaction if
     *			unsettled.
     */
    TxnManagerTransaction(TransactionManager mgr, LogManager logmgr, long id,
        TaskManager threadpool, WakeupManager wm, TxnSettler settler,
	Uuid uuid) 
    {
	if (logmgr == null)
	    throw new IllegalArgumentException("TxnManagerTransaction: " +
			    "log manager must be non-null");
	if (mgr == null)
	    throw new IllegalArgumentException("TxnManagerTransaction: " +
			    "transaction manager must be non-null");

	if (threadpool == null)
	    throw new IllegalArgumentException("TxnManagerTransaction: " +
			    "threadpool must be non-null");

	if (wm == null)
	    throw new IllegalArgumentException("TxnManagerTransaction: " +
			    "wakeup manager must be non-null");

	if (settler == null)
	    throw new IllegalArgumentException("TxnManagerTransaction: " +
			    "settler must be non-null");

	if (uuid == null)
	    throw new IllegalArgumentException("TxnManagerTransaction: " +
			    "uuid must be non-null");

	this.threadpool = threadpool;
	this.wm = wm;
	this.logmgr = logmgr ;
	str = new ServerTransaction(mgr, id);
	this.settler = settler;
	this.uuid = uuid;

	trstate = ACTIVE;  //this is implied since ACTIVE is initial state
	// Expires is set after object is created when the associated
	// lease is constructed.
    }


    /**
     * Convenience method which adds a given <code>ParticipantHandle</code> 
     * to the set of <code>ParticpantHandle</code>s associated with this
     * transaction.
     *
     * @param handle The added handle
     */
    void add(ParticipantHandle handle)
        throws InternalManagerException
    {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(TxnManagerTransaction.class.getName(), 
	        "add", handle);
	}
	
	if (handle == null)
	    throw new NullPointerException("ParticipantHolder: add: " +
					   "cannot add null handle");

        //NOTE: if the same participant re-joins, then that is
        //      fine.

	try {
            if (transactionsLogger.isLoggable(Level.FINEST)) {
                transactionsLogger.log(Level.FINEST,
                "Adding ParticipantHandle: {0}", handle);
            }
	    parts.add(handle);
	} catch (Exception e) {
            if (transactionsLogger.isLoggable(Level.SEVERE)) {
                transactionsLogger.log(Level.SEVERE,
                "Unable to add ParticipantHandle", e);
	    }
	    throw new InternalManagerException("TxnManagerTransaction: " +
					    	     "add: " + e.getMessage());
	}
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(TxnManagerTransaction.class.getName(), 
	        "add");
	}
    }

    /**
     * Convenience method which allows the caller to modify the
     * prepState associated with a given <code>ParticipantHandle</code>
     *
     * @param handle The <code>ParticipantHandle</code> being modified
     *
     * @param state The new prepstate
     */
    void modifyParticipant(ParticipantHandle handle, int state) {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(TxnManagerTransaction.class.getName(), 
	        "modifyParticipant", new Object[] {handle, new Integer(state)});
	}
	ParticipantHandle ph = null;

	if (handle == null)
	    throw new NullPointerException("ParticipantHolder: " +
			"modifyParticipant: cannot modify null handle");

	if (parts.contains(ph))
	    ph = (ParticipantHandle) parts.get(parts.indexOf(handle));	

	if (ph == null) {
            if (operationsLogger.isLoggable(Level.FINER)) {
                operationsLogger.exiting(
	            TxnManagerTransaction.class.getName(), 
		    "modifyParticipant");
	    }
//TODO - ignore??	    
	    return;
	}

	ph.setPrepState(state);
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(TxnManagerTransaction.class.getName(), 
	        "modifyParticipant");
	}
    }


    /**
     * Changes the manager-side state of the transaction.  This
     * method makes only valid state changes and informs the
     * caller if the change was successful. Calls to this method
     * synchronize around the manager-side state variable appropriately.
     *
     * @param state the new desired state
     */
    boolean modifyTxnState(int state) {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(TxnManagerTransaction.class.getName(), 
	        "modifyTxnState", new Integer(state));
	}
	boolean result = false;
	synchronized (stateLock) {
	    switch (state) {
		case ACTIVE:
		case VOTING:
		case COMMITTED:
		case ABORTED:
	          result = states[trstate][state];
		  break;

	      default:
	        throw new IllegalArgumentException("TxnManagerTransaction: " +
				            "modifyTxnState: invalid state");
	    }

	    if (result)
		trstate = state;
	}
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(TxnManagerTransaction.class.getName(), 
	        "modifyTxnState", Boolean.valueOf(result));
	}
	return result;
    }


    /**
     * Implementation of the join method. 
     *
     * @param preparedPart The joining <code>TransactionParticpant</code>
     *
     * @param crashCount The crashcount associated with the joining
     *			 <code>TransactionParticipant</code>
     *
     * @see net.jini.core.transaction.server.TransactionParticipant
     */
    public void
	join(TransactionParticipant preparedPart, long crashCount)
	    throws CannotJoinException, CrashCountException, RemoteException
    {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(TxnManagerTransaction.class.getName(), 
	        "join", new Object[] {preparedPart, new Long(crashCount)});
	}
	//if the lease has expired, or the state is not
	//amenable there is no need to continue

	if (getState() != ACTIVE)
	    throw new CannotJoinException("not active");

	if ((getState() == ACTIVE) && (ensureCurrent() == false)) {
	    doAbort(0);
	    throw new CannotJoinException("Lease expired");
	}

	//Create a ParticipantHandle for the new participant
	//and mark the transactional state as ACTIVE
	try {
	    ParticipantHandle ph = 
	        new ParticipantHandle(preparedPart, crashCount);
	    ParticipantHandle phtmp = (ParticipantHandle) 	
	        ((parts.contains(ph))?
		    parts.get(parts.indexOf(ph)):
		    null);	

            if (transactionsLogger.isLoggable(Level.FINEST)) {
                transactionsLogger.log(Level.FINEST,
                "Retrieved ParticipantHandle: {0}", phtmp);
            }
	    if (phtmp != null) {
		long oldcount = phtmp.getCrashCount();
		if (oldcount == crashCount) {
		    return;
		} else {
		    throw new CrashCountException("TxnManagerTransaction: " +
					    "join: old = " + oldcount +
					    " new = " + crashCount);
		}
	    }

	    add(ph);

	} catch (InternalManagerException ime) {
            if (transactionsLogger.isLoggable(Level.SEVERE)) {
                transactionsLogger.log(Level.SEVERE,
                "TransactionParticipant unable to join", ime);
            }
	    throw ime;
	} catch (RemoteException re) {
            if (transactionsLogger.isLoggable(Level.FINEST)) {
                transactionsLogger.log(Level.FINEST,
                "TransactionParticipant unable to be stored", re);
            }
	    throw re;
	}
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(TxnManagerTransaction.class.getName(), 
	        "join");
	}
    }


   /**
    * This method returns the state of the transaction.
    * Since the purpose of the set of ParticipantHolders is
    * to associate a Transaction with a group of
    * participants joined the transaction, we would like
    * to get the state of the transaction associated with
    * the aforementioned set.
    *
    */
    public int getState() {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(TxnManagerTransaction.class.getName(), 
	        "getState");
	}
	synchronized (stateLock) {
            if (operationsLogger.isLoggable(Level.FINER)) {
                operationsLogger.exiting(TxnManagerTransaction.class.getName(), 
	            "getState", new Integer(trstate));
	    }
            return trstate;
	}
    }


    /**
     * Commits the transaction.
     * This initiates the two-phase commit protocol.  First,
     * each <code>net.jini.core.transaction.server.TransactionParticipant</code>
     * in the set of participants joined in the
     * <code>net.jini.core.transaction.server.Transaction</code>
     * is instructed to vote and the votes are tallied. This is the
     * first phase (prepare phase).
     *
     * Depending on the outcome of the votes, the transaction
     * is considered committed or aborted.  Once commit/abort
     * status is known, the participants are notified with
     * a message to either roll-forward (commit case) or 
     * roll-back (abort case).  This is the roll-phase.
     * 
     * Since there may be a one-to-many relationship between
     * a transaction and its participants,
     * <code>com.sun.jini.thread.TaskManager</code>s are used
     * as a generic mechanism to provide the threads needed
     * to interact with the participants.
     * 
     */
    void commit(long waitFor)
        throws CannotCommitException, TimeoutExpiredException, RemoteException
    {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(TxnManagerTransaction.class.getName(), 
	        "commit", new Long(waitFor));
	}
	long starttime = System.currentTimeMillis();

	//If the transaction has already expired or the state
	//is not amenable, don't even try to continue

	if ((getState() == ACTIVE) && (ensureCurrent() == false)) {
	    doAbort(0);
	    throw new CannotCommitException("Lease expired");
	}

	if (getState() == ABORTED)
	    throw new CannotCommitException("attempt to commit " +
					    "ABORTED transaction");


	//Check to see if anyone joined the transaction.  Even
	//if no one has joined, at this point, attempt to
	//get to the COMMITTED state through valid state changes

        Vector joinvec = parthandles();
 
        if (joinvec == null) {
	    if (!modifyTxnState(VOTING))
		throw new CannotCommitException("attempt to commit " +
						"ABORTED transaction");

	    if (modifyTxnState(COMMITTED))
                return;
	    else
		throw new CannotCommitException("attempt to commit " +
						"ABORTED transaction");
        }

	try {
	    Enumeration joined = joinvec.elements();
	    int numparts = joinvec.size();
	    ParticipantHandle[] phs = new ParticipantHandle[numparts];
	    joinvec.copyInto(phs);

            long now = starttime;
            long transpired = 0;
            long remainder = 0;

	    ClientLog log = logmgr.logFor(str.id);

            if (transactionsLogger.isLoggable(Level.FINEST)) {
                transactionsLogger.log(Level.FINEST,
                "{0} TransactionParticipants have joined", 
		new Integer(numparts));
            }

	    //If commit is called after recovery, do not
	    //log a CommitRecord since it already exists
	    //exists in the Log file for this transaction.
	    //Remember that a log is not invalidated until
	    //after the transaction is committed.
	    //
	    //Only new occurrences of activities requiring
	    //logging which happen after recovery should
	    //be added to the log file.  So, we add records
	    //for voting and roll forward/back activity for
	    //ACTIVE participants.
	    //
	    //If the state cannot validly transition to VOTING,
	    //it is either because someone already aborted or
	    //committed.  Only throw an exception if  someone
	    //has previously aborted.  In the case of a prior
	    //commit, fall through and wait for the CommitJob
	    //to complete.

	    int oldstate = getState();
	    Integer result = new Integer(ABORTED);
            Exception alternateException = null;


	    //On an ACTIVE to VOTING transition, create
	    //and schedule a Prepare or PrepareAndCommitJob.
	    //If the state transition is VOTING to VOTING,
	    //then the PrepareJob has already been created
	    //in the past, so just fall through and wait
	    //for it to complete.
	    //
	    //Only log the commit on ACTIVE to VOTING
	    //transitions.

	    if (modifyTxnState(VOTING)) {

		if (oldstate == ACTIVE)
		    log.write(new CommitRecord(phs));


		//preparing a participant can never override
		//the other activities (abort or commit),
		//so only set when the job is null.

	        synchronized (jobLock) {
		    if (job == null) {
	                if (phs.length == 1)
		            job = new
			      PrepareAndCommitJob(
				  str, threadpool, wm, log, phs[0]);
	                else
	                    job = new PrepareJob(str, threadpool, wm, log, phs);

	                job.scheduleTasks();
		    }
		}

		//Wait for the PrepareJob to complete.
		//PrepareJobs are given maximum time for
		//completion.  This is required in order to
		//know the transaction's completion status.
		//Remember that the timeout ONLY controls how
		//long the caller is willing to wait to inform
		//participants.  This means that a completion
		//status for the transaction MUST be computed
		//before consulting the timeout.
		//Timeout is ignored until completion status
		//is known.  If extra time is left, wait for
		//the remainder to inform participants.

		//We must explicitly check for Job type
		//because someone else could have aborted
		//the transaction at this point.


                synchronized (jobLock) {
		    if ((job instanceof PrepareJob) ||
			    (job instanceof PrepareAndCommitJob)) {
                        try {
                            if (job.isCompleted(Long.MAX_VALUE)) {
                                result = (Integer) job.computeResult();
                                if (result.intValue() == ABORTED &&
                                    job instanceof PrepareAndCommitJob) {
                                        PrepareAndCommitJob pj = 
                                            (PrepareAndCommitJob)job;
                                        alternateException = 
                                               pj.getAlternateException();
                                }
                            }
                        } catch (JobNotStartedException jnse) {
                            //no participants voted, so do nothing
                            result = new Integer(NOTCHANGED);
                        } catch (ResultNotReadyException rnre) {
                            //consider aborted
                        } catch (JobException je) {
                            //consider aborted
                        }
		    }
		}
	    } else {
		//Cannot be VOTING, so we either have
		//an abort or commit in progress.

		if (getState() == ABORTED)
		    throw new CannotCommitException("transaction ABORTED");

 
                //If a CommitJob is already in progress 
		//(the state is COMMITTED) cause a fall
		//through to the code which waits for
                //the CommitJob to complete.

                if (getState() == COMMITTED)
                    result = new Integer(COMMITTED);
	    }

            if (transactionsLogger.isLoggable(Level.FINEST)) {
                transactionsLogger.log(Level.FINEST,
                "Voting result: {0}", 
		TxnConstants.getName(result.intValue()));
            }

	    switch (result.intValue()) {
	      case NOTCHANGED:
		break;

	      case ABORTED:
                now = System.currentTimeMillis();
                transpired = now - starttime;
                remainder = waitFor - transpired;

		if (remainder >=0)
		    doAbort(remainder);
		else
		    doAbort(0);

                if (alternateException == null) {
                    throw new CannotCommitException(
                        "Unable to commit transaction: "
                        + getParticipantInfo());
                } else {
                    throw new RemoteException(
                        "Problem communicating with participant", 
                        alternateException);
                }

	      case PREPARED:
		//This entrypoint is entered if a PrepareJob
		//tallied the votes with an outcome of
		//PREPARED.  In order to inform participants,
		//a CommitJob must be scheduled.

		if(modifyTxnState(COMMITTED)) {
//TODO - log committed state record?		
                    synchronized (jobLock) {
                        job = new CommitJob(str, threadpool, wm, log, phs);
                        job.scheduleTasks();
                    }
		} else {
		    throw new CannotCommitException("attempt to commit " +
					        "ABORTED transaction");
		}

		//Fall through to code with waits
		//for CommitJob to complete.


	      case COMMITTED:
		//This entrypoint is the starting place for the code
		//which waits for a CommitJob to complete and
		//computes its resulting outcome. In addition,
		//the wait time is enforced.  Should the wait time
		//expire, a SettlerTask is scheduled on a thread 
		//pool.  The SettlerTask is needed to complete
		//the commit (instruct participants to roll-forward)
		//on behalf of the thread which exits when
		//the TimeoutExpiredException is thrown.
		//
                //It is reached when...
		//
		// a) A commit was called on the same transaction twice.
		//    When the thread comes in on the commit call, a
		//    CommitJob already exists and the state is COMMITTED.
		//
		// b) The normal case where a PrepareJob was found to
		//    have prepared the transaction and the tally of
		//    votes resulted in a PREPARED outcome.  This causes
		//    the state to be changed to COMMITTED and a
		//    CommitJob to be created.
		//
		// c) A PrepareAndCommitJob has successfully prepared
		//    a participant which rolled its changes forward.
		//
                //Note:  By checking to see if the CommitJob is already
                //       present, this check allows us to use the same
		//	 wait-for-CommitJob code for the regular
		//       PREPARE/COMMIT, the COMMIT/COMMIT and
		//       the PREPAREANDCOMMIT cases.
 
		  synchronized (jobLock) {
		      //A prepareAndCommitJob is done at this
		      //point since the TransactionParticipant
		      //would have instructed itself to roll
		      //forward.

		      if (job instanceof PrepareAndCommitJob) {
		          if(!modifyTxnState(COMMITTED))
			      throw new CannotCommitException("transaction " +
							    	"ABORTED");	
		          break;
		      }
 

		      //If the abort already arrived, then stop

		      if (job instanceof AbortJob) 
			   throw new CannotCommitException("transaction " +
								"ABORTED");
		  }

                  if (getState() != COMMITTED)
                        throw new
                            InternalManagerException("TxnManagerTransaction: " +
                                    "commit: " + job + " got bad state: " +
                                    TxnConstants.getName(result.intValue()));


		now = System.currentTimeMillis();
		transpired = now - starttime;

		boolean committed = false;

		//If the commit is asynchronous then...
		//
		// a) check to see if the wait time has transpired
		//
		// b) If it hasn't, sleep for what's left from the wait time

		try {
		    remainder = waitFor - transpired;
		    synchronized (jobLock) {
		        if (remainder <= 0 || !job.isCompleted(remainder)) {
/*
 * Note - SettlerTask will kick off another Commit/Abort task for the same txn
 * which will try go through the VOTING->Commit states again.
 */
//TODO - Kill off existing task? Postpone SettlerTask? 			
		            settler.noteUnsettledTxn(str.id);
			    throw new TimeoutExpiredException(
					    "timeout expired", true);
			} else {
			    result = (Integer) job.computeResult();
			    committed = true;
			}
		    }
		} catch (ResultNotReadyException rnre) {
		    //this should not happen, so flag
		    //as an error.
		} catch (JobNotStartedException jnse) {
		    //an error
		} catch (JobException je) {
		    //an error
		}

		if (committed)
		    break;

	      default:
                throw new InternalManagerException("TxnManagerTransaction: " +
	    			    "commit: " + job + " got bad state: " +
				    TxnConstants.getName(result.intValue()));
	    }

	    //We don't care about the result from
	    //the CommitJob
	    log.invalidate();
	} catch (RuntimeException rte) {
            if (transactionsLogger.isLoggable(Level.FINEST)) {
                transactionsLogger.log(Level.FINEST,
                "Problem committing transaction", 
	         rte);
            }
            throw rte;
	} catch (LogException le) {
            if (transactionsLogger.isLoggable(Level.FINEST)) {
                transactionsLogger.log(Level.FINEST,
                "Problem persisting transaction", 
	         le);
            }
	    throw new CannotCommitException("Unable to log");
	}
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(TxnManagerTransaction.class.getName(), 
	        "commit");
	}
    }


    /**
     * Aborts the transaction.
     * This method attempts to set the state of the transaction
     * to the ABORTED state.  If successful, it then creates
     * an AbortJob which schedules tasks executed on a
     * thread pool.  These tasks interact with the participants
     * joined in this transaction to inform them to roll back.
     * 
     * @param waitFor Timeout value which controls how long,
     *		      the caller is willing to wait for the
     *		      participants joined in the transaction 
     *		      to be instructed to roll-back.
     *
     * @see com.sun.jini.mahalo.AbortJob
     * @see com.sun.jini.mahalo.ParticipantTask
     * @see com.sun.jini.thread.TaskManager
     * @see net.jini.core.transaction.server.TransactionParticipant
     */
    void abort(long waitFor)
	throws CannotAbortException, TimeoutExpiredException
    {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(TxnManagerTransaction.class.getName(), 
	        "abort", new Long(waitFor));
	}
	long starttime = System.currentTimeMillis();

	/*
	 * Since lease cancellation process sets expiration to 0 
	 * and then calls abort, can't reliably check expiration 
	 * at this point.
	 */
//TODO - Change internal, lease logic to call overload w/o expiration check
//TODO - Add expiration check to abort for external clients     
	try {
            Vector joinvec = parthandles();

	    if (joinvec == null) {
		if (modifyTxnState(ABORTED))
		    return;
		else 
		    throw new
			CannotAbortException("Transaction already COMMITTED");
	    }

            int numparts = joinvec.size();
            ParticipantHandle[] phs = new ParticipantHandle[numparts];
            joinvec.copyInto(phs);

            ClientLog log = logmgr.logFor(str.id);


	    //When attempting to abort, if someone has already
	    //committed the transaction, then throw an Exception.
	    //
	    //If an abort is possible, and you find that an AbortJob
	    //is already in progress, let the existing AbortJob
	    //proceed.
	    //
	    //If an abort is possible, but a PrepareJob is in progress,
	    //go ahead an halt the PrepareJob and replace it with
	    //an AbortJob.

	    if (modifyTxnState(ABORTED)) {
                log.write(new AbortRecord(phs));

	        synchronized (jobLock) {
	            if (!(job instanceof AbortJob)) {
		        if (job != null)
		            job.stop();
	                job = new AbortJob(str, threadpool, wm, log, phs);
	                job.scheduleTasks();
	            }
	        }
	    } else {
		throw new CannotAbortException("Transaction already COMMITTED");
	    }


	    //This code waits for an AbortJob to complete and
	    //computes its resulting outcome. In addition,
	    //the wait time is enforced.  Should the wait time
	    //expire, a SettlerTask is scheduled on a thread
	    //pool.  The SettlerTask is needed to complete
	    //the abort (instruct participants to roll-back)
	    //on behalf of the thread which exits when
	    //the TimeoutExpiredException is thrown.

	    long now = System.currentTimeMillis();
	    long transpired = now - starttime;

	    Integer result = new Integer(ACTIVE);
	    boolean aborted = false;

	    long remainder = waitFor - transpired;

	    try {
		synchronized (jobLock) {
	            if (remainder<= 0 || !job.isCompleted(remainder)) {
		        settler.noteUnsettledTxn(str.id);
		        throw new TimeoutExpiredException(
				        "timeout expired",false);
		    } else {
	    	       	result = (Integer) job.computeResult();
	    	       	aborted = true;
		    }
		}
	    }  catch (ResultNotReadyException rnre) {
		//should not happen, so flag as error
	    } catch (JobNotStartedException jnse) {
		//error
	    } catch (JobException je) {
	        settler.noteUnsettledTxn(str.id);
		throw new TimeoutExpiredException("timeout expired", false);
	    }


	    if (!aborted)
                throw new InternalManagerException("TxnManagerTransaction: " +
                                "abort: AbortJob got bad state: " +
                		TxnConstants.getName(result.intValue()));

	    log.invalidate();
        } catch (RuntimeException rte) {
            if (transactionsLogger.isLoggable(Level.SEVERE)) {
                transactionsLogger.log(Level.SEVERE,
                "Problem aborting transaction", 
	         rte);
            }
	    throw new InternalManagerException("TxnManagerTransaction: " +
						    "abort: fatal error");
        } catch (LogException le) {
            if (transactionsLogger.isLoggable(Level.FINEST)) {
                transactionsLogger.log(Level.FINEST,
                "Problem persisting transaction", 
	         le);
            }
            throw new CannotAbortException("Unable to log");
        }
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(TxnManagerTransaction.class.getName(), 
	        "abort");
	}
    }

    public Transaction getTransaction() {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(TxnManagerTransaction.class.getName(), 
	        "getTransaction");
	}
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(TxnManagerTransaction.class.getName(), 
	        "getTransaction", str);
	}
	return str;
    }

    public long getExpiration() {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(TxnManagerTransaction.class.getName(), 
	        "getExpiration");
	}
	synchronized (leaseLock) {
            if (operationsLogger.isLoggable(Level.FINER)) {
                operationsLogger.exiting(TxnManagerTransaction.class.getName(), 
	            "getExpiration", new Date(expires));
	    }
            return expires;
	}
    }

    public void setExpiration(long newExpiration) {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(TxnManagerTransaction.class.getName(), 
	        "setExpiration", new Date(newExpiration));
	}
	synchronized (leaseLock) {
	    expires = newExpiration;
	}
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(TxnManagerTransaction.class.getName(), 
	        "setExpiration");
	}
    }

    public Uuid getCookie() {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(TxnManagerTransaction.class.getName(), 
	        "getCookie");
	}
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(TxnManagerTransaction.class.getName(), 
	        "getCookie", uuid);
	}
	return uuid;
    }

    private void doAbort(long timeout) {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(TxnManagerTransaction.class.getName(), 
	        "doAbort", new Long(timeout));
	}
        try {
            str.abort(timeout);
        } catch (RemoteException re) {
            //abort must have happened, so ignore
            if (transactionsLogger.isLoggable(Levels.HANDLED)) {
                transactionsLogger.log(Levels.HANDLED,
                "Trouble aborting  transaction", re);
	    }
        } catch (TimeoutExpiredException te) {
	    //Swallow this because we really only
	    //care about a scheduling a SettlerTask
            if (transactionsLogger.isLoggable(Levels.HANDLED)) {
                transactionsLogger.log(Levels.HANDLED,
                "Trouble aborting  transaction", te);
	    }
        } catch (TransactionException bte) {
            //If abort has problems, swallow
            //it because the abort must have
            //happened
            if (transactionsLogger.isLoggable(Levels.HANDLED)) {
                transactionsLogger.log(Levels.HANDLED,
                "Trouble aborting  transaction", bte);
	    }
	}
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(TxnManagerTransaction.class.getName(), 
	        "doAbort");
	}

    }

    synchronized boolean ensureCurrent() {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(TxnManagerTransaction.class.getName(), 
	        "ensureCurrent");
	}
	long cur = System.currentTimeMillis();
	boolean result = false;
	long useby = getExpiration();

	if (useby > cur)
	    result = true;
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(TxnManagerTransaction.class.getName(), 
	        "ensureCurrent", Boolean.valueOf(result));
	}
	return result;
    }


    private Vector parthandles() {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(TxnManagerTransaction.class.getName(), 
	        "parthandles");
	}
	if ( (parts == null ) || ( parts.size() == 0 ) )
	    return null;

        Vector vect = new Vector(parts);
 
        if (transactionsLogger.isLoggable(Level.FINEST)) {
            transactionsLogger.log(Level.FINEST,
            "Retrieved {0} participants", 
	     new Integer(vect.size()));
        }
	
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(TxnManagerTransaction.class.getName(), 
	        "parthandles");
	}

	return vect;
    }
    
    private String getParticipantInfo() {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(TxnManagerTransaction.class.getName(), 
	        "getParticipantInfo");
	}
        if ( (parts == null ) || ( parts.size() == 0 ) )
	    return "No participants";

        if (transactionsLogger.isLoggable(Level.FINEST)) {
            transactionsLogger.log(Level.FINEST,
            "{0} participants joined", new Integer(parts.size()));
        }
	StringBuffer sb = new StringBuffer(parts.size() + " Participants: ");
        ParticipantHandle ph;
        for (int i=0; i < parts.size(); i++) {
            ph = (ParticipantHandle)parts.get(i);
            sb.append(
                "{" + i + ", " 
                + ph.getPreParedParticipant().toString() + ", " 
                + TxnConstants.getName(ph.getPrepState())
                + "} ");
        }
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(TxnManagerTransaction.class.getName(), 
	        "getParticipantInfo", sb.toString());
	}

	return sb.toString();
    }
    
    void restoreTransientState(ProxyPreparer preparer) 
        throws RemoteException
    {
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.entering(TxnManagerTransaction.class.getName(), 
	        "restoreTransientState");
	}
    	if ( (parts == null ) || ( parts.size() == 0 ) )
	    return;

	ParticipantHandle[] handles = (ParticipantHandle[])
	    parts.toArray(new ParticipantHandle[parts.size()]);
        for (int i=0; i < handles.length; i++) {
	    handles[i].restoreTransientState(preparer);
            if (transactionsLogger.isLoggable(Level.FINEST)) {
                transactionsLogger.log(Level.FINEST,
                "Restored transient state for {0}", 
	         handles[i]);
            }
	}
	
        if (operationsLogger.isLoggable(Level.FINER)) {
            operationsLogger.exiting(TxnManagerTransaction.class.getName(), 
	        "restoreTransientState");
	}
    }
}
