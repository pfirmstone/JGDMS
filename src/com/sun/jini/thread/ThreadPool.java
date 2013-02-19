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

import com.sun.jini.action.GetLongAction;
import java.security.AccessController;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ThreadPool is a simple thread pool implementation of the Executor
 * interface.
 *
 * A new task is always given to an idle thread, if one is available;
 * otherwise, a new thread is always created.  There is no minimum
 * warm thread count, nor is there a maximum thread count (tasks are
 * never queued unless there are sufficient idle threads to execute
 * them).
 *
 * New threads are created as daemon threads in the thread group that
 * was passed to the ThreadPool instance's constructor.  Each thread's
 * name is the prefix NewThreadAction.NAME_PREFIX followed by the name
 * of the task it is currently executing, or "Idle" if it is currently
 * idle.
 *
 * <p>This implementation uses the {@link Logger} named
 * <code>com.sun.jini.thread.ThreadPool</code> to
 * log information at the following levels:
 *
 * <p><table summary="Describes what is logged by ThreadPool at
 * various logging levels" border=1 cellpadding=5>
 *
 * <tr> <th> Level <th> Description
 *
 * <tr> <td> {@link Level#WARNING WARNING} <td> uncaught exception in
 * worker thread
 *
 * </table>
 *
 * @author	Sun Microsystems, Inc.
 **/
final class ThreadPool implements Executor, java.util.concurrent.Executor {

    /** how long a thread waits in the idle state before passing away */
    private static final long idleTimeout =		// default 5 minutes
	((Long) AccessController.doPrivileged(new GetLongAction(
	    "com.sun.jini.thread.idleThreadTimeout", 300000)))
	    .longValue();

    private static final Logger logger =
	Logger.getLogger("com.sun.jini.thread.ThreadPool");

    /** thread group that this pool's threads execute in */
    private final ThreadGroup threadGroup;

    /** Lock used to wake idle threads and synchronize writes to idleThreads */
    private final Lock lock;
    private final Condition wakeup;

    /** threads definitely available to take new tasks */
    private volatile int idleThreads;

    /** queues of tasks to execute */
    private final Queue<Runnable> queue;

    /**
     * Creates a new thread group that executes tasks in threads of
     * the given thread group.
     */
    ThreadPool(ThreadGroup threadGroup) {
	this.threadGroup = threadGroup;
        idleThreads = 0;
        queue = new ConcurrentLinkedQueue<Runnable>(); //Non blocking queue.
        lock = new ReentrantLock();
        wakeup = lock.newCondition();
    }

    // This method must not block - Executor
    public void execute(Runnable runnable, String name) {
	Runnable task = new Task(runnable, name);
        if (idleThreads < 3){ // create a new thread, non blocking approximate
            Thread t = AccessController.doPrivileged(
	    new NewThreadAction(threadGroup, new Worker(task), name, true));
	t.start();
        } else {
            boolean accepted = queue.offer(task); //non blocking.
            if (accepted) { 
                lock.lock(); // blocking.
                try {
                    wakeup.signal(); 
                } finally {
                    lock.unlock();
	    }
            } else { // Should never happen.
                Thread t = AccessController.doPrivileged(
	    new NewThreadAction(threadGroup, new Worker(task), name, true));
	t.start();
    }
        }
    }

    public void execute(Runnable command) {
        execute(command, "com.sun.jini.thread.ThreadPool");
    }

    /**
     * Task simply encapsulates a task's Runnable object with its name.
     */
    private static class Task implements Runnable{

	private final Runnable runnable;
	private final String name;

	Task(Runnable runnable, String name) {
	    this.runnable = runnable;
	    this.name = name;
	}
        
        public void run(){
            try {
                runnable.run();
            } catch (Exception t) { // Don't catch Error
                logger.log(Level.WARNING, "uncaught exception", t);
                if (t instanceof RuntimeException){
                    if (t instanceof SecurityException){
                        // ignore it will be logged.
                    } else {
                        // Ignorance of RuntimeException is generally bad, bail out.
                        throw (RuntimeException) t;
    }
                }
            }
        }

        public String toString(){
            return name;
        }
    }

    /**
     * Worker executes an initial task, and then it executes tasks from the
     * queue, passing away if ever idle for more than the idle timeout value.
     */
    private class Worker implements Runnable {

	private volatile Runnable first;

	Worker(Runnable first) {
	    this.first = first;
	}

	public void run() {
	    Runnable task = first;
	    first = null; // For garbage collection.
            task.run();
            Thread thread = Thread.currentThread();
	    while (!thread.isInterrupted()) {
		/*
		 * REMIND: What if the task changed this thread's
		 * priority? or context class loader?
		 */
                for ( task = queue.poll(); task != null; task = queue.poll()){
                    // Keep executing while tasks are available.
                    thread.setName(NewThreadAction.NAME_PREFIX + task);
                    task.run();
                }
                // queue is empty;
                thread.setName(NewThreadAction.NAME_PREFIX + "Idle");
                lock.lock();
			try {
                    idleThreads++;
                    wakeup.await(idleTimeout, TimeUnit.MILLISECONDS);// releases lock and obtains when woken.
                    // Allow thread to expire if queue empty after waking.
                    if (queue.peek() == null) thread.interrupt();
                } catch (InterruptedException ex) {
                    // Interrupt thread, another thread can pick up tasks.
                    thread.interrupt();
			} finally {
			    idleThreads--;
                    lock.unlock();
			}
			}
		    }
		}
	}
