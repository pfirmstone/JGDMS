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

import com.sun.jini.thread.TaskManager;
import com.sun.jini.thread.WakeupManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
public abstract class Job {
    private TaskManager pool;
    private WakeupManager wm;
    private int pending = -1;
    Object[] results;
    int[] attempts;
    private Map tasks = new HashMap();  //used to maintain account
					//of the tasks for which
					//the job is responsible

    static final Logger logger = TxnManagerImpl.participantLogger;
    
    /**
     * Create the <code>Job</code> object giving it the
     * <code>TaskManager</code> responsible for the pool of
     * threads which perform the necessary work.
     *
     * @param pool the <code>TaskManager</code> which provides the threads
     */
    public Job(TaskManager pool, WakeupManager wm) {
        this.wm = wm;
	this.pool = pool;
    }


    /**
     * Used by a task to do a piece of work and record the
     * number of attempts.
     *
     * @param who The task which is performing the work
     * @param param A parameter used in performing the work
     */
    boolean performWork(TaskManager.Task who, Object param)
        throws JobException
    {
	Integer tmp = null;
	
	synchronized (tasks) {
	    tmp = (Integer)tasks.get(who);
	}

	if (tmp == null)
	    throw new UnknownTaskException();

	int rank = tmp.intValue();

	synchronized (attempts) {
	    attempts[rank]++;
	}

	Object result = doWork(who, param);
	if (result == null)
	    return false;

	try {
	    reportDone(who, result);
	} catch (UnknownTaskException e) {
	} catch (PartialResultException e) {
	} catch (JobException e) {
	}

	return true;
    }


    /**
     * Given a <code>TaskManager.Task</code>, this method
     * returns the current number of attempts it has made.
     * 
     * @param who The task for which the number of attempts
     *		  is inquired
     */
    int attempt(TaskManager.Task who) throws JobException {
	Integer tmp = null;

	synchronized(tasks) {
	    tmp = (Integer)tasks.get(who);
	}

	if (tmp == null)
	    throw new UnknownTaskException();

	int rank = tmp.intValue();

	synchronized(attempts)  {
	    return attempts[rank];
	}
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
    abstract Object doWork(TaskManager.Task who, Object param)
	throws JobException;

    /**
     * Create the tasks required to compute all of the
     * <code>PartialResult</code> objects necessary for the
     * solution to the original problem. 
     */
    abstract TaskManager.Task[] createTasks();


    /**
     * Schedules tasks for execution
     */
    public void scheduleTasks() {
	TaskManager.Task[] tmp = createTasks();

	if (tmp != null) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST,
                    "Job:scheduleTasks with {0} tasks",
                    new Integer(tmp.length));
            }

	    results = new Object[tmp.length];
	    attempts = new int[tmp.length];
	    setPending(tmp.length);

	    for (int i = 0; i < tmp.length; i++) {

		//Record the position if each
		//task for later use when assembling
		//the partial results

		synchronized(tasks) {
		    tasks.put(tmp[i],new Integer(i));
		    pool.add(tmp[i]);
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST,
                            "Job:scheduleTasks added {0} to thread pool",
                            tmp[i]);
                    }
		    attempts[i] = 0;
		}
	    }
	}
    }


    private synchronized void awaitPending(long waitFor) {
	if (pending < 0)
	    return;

	if (pending == 0)
	    return;

	try {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST,
                    "Job:awaitPending waiting for {0} items",
                    new Integer(pending));
            }

	    if (waitFor == Long.MAX_VALUE) {
		while (pending > 0) {
		    wait();
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

		while ((pending > 0) && ((curr - start) < waitFor)) {
                    wait(waitFor - (curr - start));
		    curr = System.currentTimeMillis();
		}
	    }
        } catch (InterruptedException ie) {
        }
    }

    private synchronized void setPending(int num) {
        pending = num;

	if (pending <= 0) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST,
                    "Job:setPending notifying, pending = {0}",
		    new Integer(pending));
            }
	    notifyAll();
	}
    }

    private synchronized void decrementPending() {
	pending--;

	if (pending <= 0) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST,
                    "Job:decrementPending notifying, pending = {0}",
		    new Integer(pending));
            }
	    notifyAll();
	}
    }


    /**
     * Returns a reference to the <code>TaskManager</code> which
     * supplies the threads used to executed tasks created by
     * this <code>Job</code>
     */
    protected TaskManager getPool() {
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
    private void reportDone(TaskManager.Task who, Object param)
	throws JobException
    {
	if (param == null)
	    throw new NullPointerException("param must be non-null");

	if (who == null)
	    throw new NullPointerException("task must be non-null");

	Integer position = null;
	
	synchronized(tasks) {
	    position = (Integer) tasks.get(who);
	}

	if (position == null) 
	    throw new UnknownTaskException();

	synchronized(results) {
	    if (results[position.intValue()] == null) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST,
                        "Job:reportDone who = {0}, param = {1}",
		        new Object[] { who, param});
                }
	        results[position.intValue()] = param;
	        decrementPending();
	    } else {
	        throw new PartialResultException("result already set");
	    }
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

	synchronized(this) {
	    if (pending == 0)
		return true;

	    if (pending < 0)
		throw new JobNotStartedException("No jobs started");

	    return false;
	} 
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
    public void stop() {
	Set s = tasks.keySet();
	Object[] vals = s.toArray();

	//Remove and interrupt all tasks

	for (int i = 0; i < vals.length; i++) {
	    TaskManager.Task t = (TaskManager.Task) vals[i];
	    pool.remove(t);
	}

	//Erase record of tasks, results and the
	//counting mechanism

	tasks = new HashMap();
	setPending(-1);
	results = null;
    }
}
