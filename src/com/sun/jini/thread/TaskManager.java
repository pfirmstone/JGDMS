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
package com.sun.jini.thread;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A task manager manages a single queue of tasks, and some number of
 * worker threads.  New tasks are added to the tail of the queue.  Each
 * thread loops, taking a task from the queue and running it.  Each
 * thread looks for a task by starting at the head of the queue and
 * taking the first task (that is not already being worked on) that is
 * not required to run after any of the tasks that precede it in
 * the queue (including tasks that are currently being worked on).
 * <p>
 * This class uses the {@link Logger} named
 * <code>com.sun.jini.thread.TaskManager</code> to log information at
 * the following logging levels:
 * <p>
 * <table border=1 cellpadding=5
 *       summary="Describes logging performed by TaskManager at different
 *	          logging levels">
 * <caption halign="center" valign="top"><b><code>
 *	   com.sun.jini.thread.TaskManager</code></b></caption>
 * <tr><th>Level<th>Description
 * <tr><td>{@link Level#SEVERE SEVERE}<td>
 * failure to create a worker thread when no other worker threads exist
 * <tr><td>{@link Level#WARNING WARNING}<td>
 * exceptions thrown by {@link TaskManager.Task} methods, and failure
 * to create a worker thread when other worker threads exist
 * </table>
 *
 * @author Sun Microsystems, Inc.
 *
 */
public class TaskManager {

    /** The interface that tasks must implement */
    public interface Task extends Runnable {
	/**
	 * Return true if this task must be run after at least one task
	 * in the given task list with an index less than size (size may be
	 * less then tasks.size()).  Using List.get will be more efficient
	 * than List.iterator.
	 *
	 * @param tasks the tasks to consider.  A read-only List, with all
	 * elements instanceof Task.
	 * @param size elements with index less than size should be considered
	 */
	boolean runAfter(List tasks, int size);
    }

    /** Logger */
    protected static final Logger logger =
	Logger.getLogger("com.sun.jini.thread.TaskManager");

    /** Active and pending tasks */
    protected final ArrayList tasks = new ArrayList();
    /** Index of the first pending task; all earlier tasks are active */
    protected int firstPending = 0;
    /** Read-only view of tasks */
    protected final List roTasks = Collections.unmodifiableList(tasks);
    /** Active threads */
    protected final List threads = new ArrayList();
    /** Maximum number of threads allowed */
    protected final int maxThreads;
    /** Idle time before a thread should exit */
    protected final long timeout;
    /** Threshold for creating new threads */
    protected final float loadFactor;
    /** True if manager has been terminated */
    protected boolean terminated = false;

    /**
     * Create a task manager with maxThreads = 10, timeout = 15 seconds,
     * and loadFactor = 3.0.
     */
    public TaskManager() {
	this(10, 1000 * 15, 3.0f);
    }

    /**
     * Create a task manager.
     *
     * @param maxThreads maximum number of threads to use on tasks
     * @param timeout idle time before a thread exits 
     * @param loadFactor threshold for creating new threads.  A new
     * thread is created if the total number of runnable tasks (both active
     * and pending) exceeds the number of threads times the loadFactor,
     * and the maximum number of threads has not been reached.
     */
    public TaskManager(int maxThreads, long timeout, float loadFactor) {
	this.maxThreads = maxThreads;
	this.timeout = timeout;
	this.loadFactor = loadFactor;
    }

    /**
     * Add a new task if it is not equal to (using the equals method)
     * to any existing active or pending task.
     */
    public synchronized void addIfNew(Task t) {
	if (!tasks.contains(t))
	    add(t);
    }

    /** Add a new task. */
    public synchronized void add(Task t) {
	tasks.add(t);
	boolean poke = true;
	while (threads.size() < maxThreads && needThread()) {
	    Thread th;
	    try {
		th = new TaskThread();
		th.start();
	    } catch (Throwable tt) {
		try {
		    logger.log(threads.isEmpty() ?
			       Level.SEVERE : Level.WARNING,
			       "thread creation exception", tt);
		} catch (Throwable ttt) {
		}
		break;
	    }
	    threads.add(th);
	    poke = false;
	}
	if (poke &&
	    threads.size() > firstPending &&
	    !runAfter(t, tasks.size() - 1))
	{
	    notify();
	}
    }

    /** Add all tasks in a collection, in iterator order. */
    public synchronized void addAll(Collection c) {
	for (Iterator iter = c.iterator(); iter.hasNext(); ) {
	    add((Task)iter.next());
	}
    }

    /** Return true if a new thread should be created (ignoring maxThreads). */
    protected boolean needThread() {
	int bound = (int)(loadFactor * threads.size());
	int max = tasks.size();
	if (max < bound)
	    return false;
	max--;
	if (runAfter((Task)tasks.get(max), max))
	    return false;
	int ready = firstPending + 1;
	if (ready > bound)
	    return true;
	for (int i = firstPending; i < max; i++) {
	    if (!runAfter((Task)tasks.get(i), i)) {
		ready++;
		if (ready > bound)
		    return true;
	    }
	}
	return false;
    }

    /**
     * Returns t.runAfter(i), or false if an exception is thrown.
     */
    private boolean runAfter(Task t, int i) {
	try {
	    return t.runAfter(roTasks, i);
	} catch (Throwable tt) {
	    try {
		logger.log(Level.WARNING, "Task.runAfter exception", tt);
	    } catch (Throwable ttt) {
	    }
	    return false;
	}
    }

    /**
     * Remove a task if it is pending (not active).  Object identity (==)
     * is used, not the equals method.  Returns true if the task was
     * removed.
     */
    public synchronized boolean removeIfPending(Task t) {
	return removeTask(t, firstPending);
    }

    /*
     * Remove a task if it is pending or active.  If it is active and not being
     * executed by the calling thread, interrupt the thread executing the task,
     * but do not wait for the thread to terminate.  Object identity (==) is
     * used, not the equals method.  Returns true if the task was removed.
     */
    public synchronized boolean remove(Task t) {
	return removeTask(t, 0);
    }

    /**
     * Remove a task if it has index >= min.  If it is active and not being
     * executed by the calling thread, interrupt the thread executing the task.
     */
    private boolean removeTask(Task t, int min) {
	for (int i = tasks.size(); --i >= min; ) {
	    if (tasks.get(i) == t) {
		tasks.remove(i);
		if (i < firstPending) {
		    firstPending--;
		    for (int j = threads.size(); --j >= 0; ) {
			TaskThread thread = (TaskThread)threads.get(j);
			if (thread.task == t) {
			    if (thread != Thread.currentThread())
				thread.interrupt();
			    break;
			}
		    }
		}
		return true;
	    }
	}
	return false;
    }

    /**
     * Interrupt all threads, and stop processing tasks.  Only getPending
     * should be used afterwards.
     */
    public synchronized void terminate() {
	terminated = true;
	for (int i = threads.size(); --i >= 0; ) {
	    ((Thread)threads.get(i)).interrupt();
	}
    }

    /** Return all pending tasks.  A new list is returned each time. */
    public synchronized ArrayList getPending() {
	ArrayList tc = (ArrayList)tasks.clone();
	for (int i = firstPending; --i >= 0; ) {
	    tc.remove(0);
	}
	return tc;
    }

    /** Return the maximum number of threads to use on tasks. */
    public int getMaxThreads() {
	return maxThreads;
    }

    private class TaskThread extends Thread {

	/** The task being run, if any */
	public Task task = null;

	public TaskThread() {
	    super("task");
	    setDaemon(true);
	}

	/**
	 * Find the next task that can be run, and mark it taken by
	 * moving firstPending past it (and moving the task in front of
	 * any pending tasks that are skipped due to execution constraints).
	 * If a task is found, set task to it and return true.
	 */
	private boolean takeTask() {
	    int size = tasks.size();
	    for (int i = firstPending; i < size; i++) {
		Task t = (Task)tasks.get(i);
		if (!runAfter(t, i)) {
		    if (i > firstPending) {
			tasks.remove(i);
			tasks.add(firstPending, t);
		    }
		    firstPending++;
		    task = t;
		    return true;
		}
	    }
	    return false;
	}

	public void run() {
	    while (true) {
		synchronized (TaskManager.this) {
		    if (terminated)
			return;
		    if (task != null) {
			for (int i = firstPending; --i >= 0; ) {
			    if (tasks.get(i) == task) {
				tasks.remove(i);
				firstPending--;
				break;
			    }
			}
			task = null;
			interrupted(); // clear interrupt bit
		    }
		    if (!takeTask()) {
			try {
			    TaskManager.this.wait(timeout);
			} catch (InterruptedException e) {
			}
			if (terminated || !takeTask()) {
			    threads.remove(this);
			    return;
			}
		    }
		}
		try {
		    task.run();
		} catch (Throwable t) {
		    try {
			logger.log(Level.WARNING, "Task.run exception", t);
		    } catch (Throwable tt) {
		    }
		}
	    }
	}
    }
}
