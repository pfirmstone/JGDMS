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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    /** queues of tasks to execute */
    private final BlockingQueue<Runnable> queue;
    
    /** 
     * This Executor is used by JERI (and other Jini implementation classes) 
     * to delegate tasks to, the intent is to hand off to a new thread 
     * immediately, however:
     *
     * 1. When ThreadPool creates threads too aggressively, stress tests in the 
     * qa suite create too many threads and hangs because tasks that need to 
     * respond within a required time cannot.  
     * 
     * 2. Conversely when thread creation takes too long, Javaspace tests that 
     * rely on event propagation to cancel a LeasedResource find that lease still 
     * available after lease expiry.
     * 
     * ThreadPool must degrade gracefully when a system is under significant
     * load, but it must also execute tasks as soon as possible.
     * 
     * To address these issues, a SynchronousQueue has been selected, it has
     * no storage capacity, it hands tasks directly from the calling thread to
     * the task thread.  Consider TransferBlockingQueue when Java 6 is no
     * longer supported.
     * 
     * Pool threads block waiting until a task is available or idleTimeout
     * occurs after which the pool thread dies, client threads block waiting 
     * until a task thread is available, or after an computed timeout elapses, 
     * creates a new thread to execute the task.
     * 
     * ThreadGroup is a construct originally intended for applet isolation, 
     * however it was never really successful, AccessControlContext 
     * is a much more effective way of controlling privilege.
     * 
     * We should consider changing this to ensure that each task is executed in the
     * AccessControlContext of the calling thread, to avoid privilege escalation.
     */
    private final AtomicInteger threadCount;
    private final AtomicInteger waitingThreads;
    private final int delayFactor;
    private static final int numberOfCores = Runtime.getRuntime().availableProcessors();
    
    ThreadPool(ThreadGroup threadGroup){
        this(threadGroup, 10);
    }

    /**
     * Creates a new thread group that executes tasks in threads of
     * the given thread group.
     */
    ThreadPool(ThreadGroup threadGroup, int delayFactor) {
	this.threadGroup = threadGroup;
        queue = new SynchronousQueue<Runnable>(); //Non blocking queue.
        threadCount = new AtomicInteger();
        waitingThreads = new AtomicInteger();
        this.delayFactor = delayFactor;
    }

    // This method must not block - Executor
    public void execute(Runnable runnable, String name) {
        if (runnable == null) return;
	Runnable task = new Task(runnable, name);
        boolean accepted = false;
        try {
            // If there are no threads, maxDelay = 0;
            // If the system is highly loaded, it takes longer for waiting
            // threads to wake up and take the task, so this is designed to
            // prevent a heavily loaded system from unnecessarily creating
            // more threads, while also allowing threads to ramp up quickly
//            long maxDelay = (threadCount.get() < 400 && waitingThreads.get() == 0) 
//                    ? 0 : (waitingThreads.get() + 1 ) * (threadCount.get()/ numberOfCores);
//            maxDelay = maxDelay * 700 * delayFactor;
//            accepted = queue.offer(task, maxDelay, TimeUnit.MICROSECONDS);
            accepted = queue.offer(task, waitingThreads.get() * delayFactor *  700, TimeUnit.MICROSECONDS);
        } catch (InterruptedException ex) {
            Logger.getLogger(ThreadPool.class.getName()).log(Level.SEVERE, "Calling thread interrupted", ex);
            // restore interrupt.
            Thread.currentThread().interrupt();
        } finally {
            if (!accepted){
                Thread t = AccessController.doPrivileged(
                    new NewThreadAction(threadGroup, new Worker(task), name, true));
                t.start();
                threadCount.incrementAndGet();
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
                    } else if (t instanceof InterruptedException) {
                        // If we've caught an interrupt, we need to make sure it's
                        // set so the while loop stops.
                        Thread.currentThread().interrupt();
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
            try {
                Runnable task = first;
                first = null; // For garbage collection.
                task.run();
                Thread thread = Thread.currentThread();
                while (!thread.isInterrupted()) {
                    /*
                     * REMIND: What if the task changed this thread's
                     * priority? or context class loader?
                     * 
                     * thread.setName is not thread safe.
                     */
                    try {
                        waitingThreads.getAndIncrement();
                        task = null;
                        task = queue.poll(idleTimeout, TimeUnit.MILLISECONDS);
                        waitingThreads.getAndDecrement();
//                        thread.setName(NewThreadAction.NAME_PREFIX + task);
                        if (task != null) task.run();
//                         thread.setName(NewThreadAction.NAME_PREFIX + "Idle");
                    } catch (InterruptedException e){
                        waitingThreads.getAndDecrement();
                        thread.interrupt();
                        break;
                    }
                }
            } finally {
                threadCount.decrementAndGet();
            }
        }
    }
}
