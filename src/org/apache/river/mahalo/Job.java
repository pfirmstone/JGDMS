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

import org.apache.river.thread.WakeupManager;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A <code>Job</code> manages the division of work for a problem
 * whose solution is obtained by assembling partial results to
 * original problem.
 *
 * @author Sun Microsystems, Inc.
 *
 */
abstract class Job {
    private final ExecutorService pool;
    private final WakeupManager wm;
    private final AtomicInteger pend;
    final ConcurrentMap<Integer,Object> results;
    volatile AtomicIntegerArray attempts = null;
    private final ConcurrentMap<Runnable,Integer> tasks;  //used to maintain account
					//of the tasks for which
					//the job is responsible
                                        // sync on tasks.
    static final Logger logger = TxnManagerImpl.participantLogger;
    
    /**
     * Create the <code>Job</code> object giving it the
     * <code>ExecutorService</code> responsible for the pool of
     * threads which perform the necessary work.
     *
     * @param pool the <code>ExecutorService</code> which provides the threads
     */
    Job(ExecutorService pool, WakeupManager wm) {
        this.wm = wm;
	this.pool = pool;
        pend = new AtomicInteger(-1);
        results = new ConcurrentHashMap<Integer,Object>();
        tasks = new ConcurrentHashMap<Runnable,Integer>();
    }


    /**
     * Used by a task to do a piece of work and record the
     * number of attempts.
     *
     * @param who The task which is performing the work
     * @param param A parameter used in performing the work
     */
    boolean performWork(Runnable who, Object param)
        throws JobException
    {
	Integer tmp = tasks.get(who);
	if (tmp == null) throw new UnknownTaskException("Task didn't belong to this job");
	int rank = tmp.intValue();
        attempts.incrementAndGet(rank);

	Object r = doWork(who, param);
        
	if (r == null) return false;

	try {
	    reportDone(who, r);
	} catch (UnknownTaskException e) {
            logger.log(Level.FINER, "trouble reporting job completion", e);
            e.printStackTrace(System.err);
	} catch (PartialResultException e) {
            logger.log(Level.FINER, "trouble reporting job completion", e);
            e.printStackTrace(System.err);
	} catch (JobException e) {
            logger.log(Level.FINER, "trouble reporting job completion", e);
            e.printStackTrace(System.err);
	}

	return true;
    }


    /**
     * Given a <code>Runnable</code>, this method
     * returns the current number of attempts it has made.
     * 
     * @param who The task for which the number of attempts
     *		  is inquired
     */
    int attempt(Runnable who) throws JobException {
	Integer tmp = tasks.get(who);
	if (tmp == null) throw new UnknownTaskException();
	int rank = tmp.intValue();
        return attempts.get(rank);
    }



    /**
     * The work performed is implemented here.
     * A null return value indicates failure
     * while a non-null return value indicates
     * success and contains the result.
     * 
     * @param who The task performing the work
     * @param param A parameter used to do the work
     *
     */
    abstract Object doWork(Runnable who, Object param)
	throws JobException;

    /**
     * Create the tasks required to compute all of the
     * <code>PartialResult</code> objects necessary for the
     * solution to the original problem. 
     */
    abstract Runnable[] createTasks();


    /**
     * Schedules tasks for execution
     */
    public final void scheduleTasks() {
	Runnable[] tmp = createTasks();
        synchronized (this){
            if (tmp != null) {
                int length = tmp.length;
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST,
                        "Job:scheduleTasks with {0} tasks",
                        Integer.valueOf(length));
                }

                results.clear();
                tasks.clear();
                attempts = new AtomicIntegerArray(length);
                setPending(length);

                for (int i = 0; i < length; i++) {
                    //Record the position if each
                    //task for later use when assembling
                    //the partial results
                    tasks.put(tmp[i],Integer.valueOf(i));
                    attempts.set(i,0);
                    pool.submit(tmp[i], (Object) null);
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST,
                            "Job:scheduleTasks added {0} to thread pool",
                            tmp[i]);
                    }
                }
            }
        }
    }


    private void awaitPending(long waitFor) {
        if (pend.get() < 1) return; // 0 or -1

	try {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST,
                    "Job:awaitPending waiting for {0} items",
                    Integer.valueOf(pend.get()));
            }

	    if (waitFor == Long.MAX_VALUE) {
		while (pend.get() > 0) {
                    synchronized (this){
                        wait();
                    }
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST,
                            "Job:awaitPending awoken");
                    }
		}
	    } else {
		//When waiting for a given amount of time,
		//if notified, make sure that the desired
		//wait time has actually transpired.

		long start = System.currentTimeMillis();
		long curr = start;

		while ((pend.get() > 0) && ((curr - start) < waitFor)) {
                    synchronized (this){
                        wait(waitFor - (curr - start));
                    }
		    curr = System.currentTimeMillis();
		}
	    }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void setPending(int num) {
        pend.set(num);

	if (pend.get() <= 0) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST,
                    "Job:setPending notifying, pending = {0}",
		    Integer.valueOf(pend.get()));
            }
            notifyAll();
	}
    }

    private void decrementPending() {
        int pending = pend.decrementAndGet();

	if (pending <= 0) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST,
                    "Job:decrementPending notifying, pending = {0}",
		    Integer.valueOf(pending));
            }
            synchronized (this){
                notifyAll();
            }
	}
    }


    /**
     * Returns a reference to the <code>ExecutorService</code> which
     * supplies the threads used to executed tasks created by
     * this <code>Job</code>
     */
    protected ExecutorService getPool() {
	return pool;
    }

    /**
     * Returns a reference to the <code>WakeupManager</code> which
     * provides the scheduling of tasks created by
     * this <code>Job</code>
     */
    protected WakeupManager getMgr() {
	return wm;
    }

    /*
     * Tasks which perform work on behalf of the <code>Job</code>
     * report in that they are done using this method.
     */
    private void reportDone(Runnable who, Object param)
	throws JobException
    {
	if (param == null) throw new NullPointerException("param must be non-null");
	if (who == null) throw new NullPointerException("task must be non-null");

	Integer position = tasks.get(who);
	if (position == null) throw new UnknownTaskException();
        
        Object exists = results.putIfAbsent(position, param);
        if (exists == null){
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST,
                    "Job:reportDone who = {0}, param = {1}",
                    new Object[] { who, param});
            }
            decrementPending();
        } else {
            throw new PartialResultException("result already set");
        }
    }


    /**
     * Check to see if the <code>Job</code> execution has
     * completed.
     *
     * @param waitFor	The amount of time the caller is willing
     *			to wait for the completion status to arrive.
     */
    public boolean isCompleted(long waitFor) throws JobException {
	//If nothing has started, the
	//task could not have completed.
	//Less than zero means initial value
	//and greater than zero means there
	//are outstanding tasks. In each of
	//these cases, the Job is not done.

	awaitPending(waitFor);
        int pending = pend.get();
        if (pending == 0) return true;
        if (pending < 0) throw new JobNotStartedException("No jobs started");
        return false;
    }


    /**
     * Generate the solution to the original problem.
     * The subclass decides how it computes the final
     * outcome.
     */
    abstract Object computeResult() throws JobException;


    /**
     * Halt all of the work being performed  by
     * the <code>Job</code>
     */
    public final synchronized void stop() {
	Set<Runnable> s = tasks.keySet();
        Iterator<Runnable> it = s.iterator();
        while (it.hasNext()){
            Runnable r = it.next();
            if (r instanceof Future) ((Future)r).cancel(false);
        }
	//Erase record of tasks, results and the
	//counting mechanism
        tasks.clear();
        setPending(-1);
        results.clear();
    }
}
