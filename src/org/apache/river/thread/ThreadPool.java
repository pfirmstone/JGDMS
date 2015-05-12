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

package org.apache.river.thread;

import org.apache.river.action.GetLongAction;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
 * <code>org.apache.river.thread.ThreadPool</code> to
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
	    "org.apache.river.thread.idleThreadTimeout", 300000)))
	    .longValue();

    private static final Logger logger =
	Logger.getLogger("org.apache.river.thread.ThreadPool");

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
     * qa suite create too many threads and hang because tasks that need to 
     * respond within a required time cannot.  
     * 
     * 2. Conversely when thread creation takes too long, Javaspace tests that 
     * rely on event propagation to cancel a LeasedResource find that lease still 
     * available after lease expiry.
     * 
     * 3. If no threads are available when JERI needs to start a Mux connection,
     * then a mux writer cannot initiate a client connection, for this reason, a
     * new thread must be created if no waiting threads are available to the caller.
     * 
     * ThreadPool must degrade gracefully when a system is under significant
     * load, but it must also execute tasks as soon as possible.
     * 
     * To address these issues, a SynchronousQueue was originally selected, it has
     * no storage capacity, it hands tasks directly from the calling thread to
     * the task thread, however contention can cause more threads than necessary
     * to be created, a LinkedBlockingQueue eliminates or reduces contention 
     * between caller and worker threads, preventing unnecessary thread creation. 
     * Consider TransferBlockingQueue when Java 6 is no longer supported.
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
    private final AtomicInteger workerCount;
    private final AtomicInteger availableThreads;
    private volatile boolean shutdown = false;
    
    ThreadPool(ThreadGroup threadGroup){
        this(threadGroup, 10);
    }

    /**
     * Creates a new thread group that executes tasks in threads of
     * the given thread group.
     */
    ThreadPool(final ThreadGroup threadGroup, int delayFactor) {
	this.threadGroup = threadGroup;
        queue = new LinkedBlockingQueue<Runnable>();
        workerCount = new AtomicInteger();
        availableThreads = new AtomicInteger();
//         Thread not started until after constructor completes
//         this escaping occurs safely.
        AccessController.doPrivileged(new PrivilegedAction(){

            @Override
            public Object run() {
                Runtime.getRuntime().addShutdownHook(new Thread ("ThreadPool destroy"){
                    @Override
                    public void run (){
                        try {
                            // Allow four seconds prior to shutdown for other
                            // processes to complete.
                            Thread.sleep(4000L);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                        shutdown = true;
                        Thread [] threads = new Thread [workerCount.get() + 1 ];
                        int count = threadGroup.enumerate(threads);
                        for (int i = 0; i < count; i++){
                            threads [i].interrupt();
                        }
                    }
                });
                return null;
            }
        });
    }

    // This method must not block - Executor
    @Override
    public void execute(Runnable runnable, String name) throws RejectedExecutionException {
        if (runnable == null) return;
        if (shutdown) throw new RejectedExecutionException("ThreadPool shutdown");
	Runnable task = new Task(runnable, name);
        /* Startup ramps up very quickly because there are no waiting
         * threads available.
         * 
         * Tasks must not be allowed to build up in the queue, in case
         * of dependencies.
         */
        if ( availableThreads.get() < 1 ) { // need more threads.
            if (shutdown) {
                throw new RejectedExecutionException("ThreadPool shutdown");
            }
            Thread t = AccessController.doPrivileged(
                    new NewThreadAction(threadGroup, new Worker(task), name, false));
            t.start();
        } else {
            try {
                queue.put(task);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void execute(Runnable command) {
        execute(command, "org.apache.river.thread.ThreadPool");
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
        
        @Override
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
                } else if (t instanceof InterruptedException) {
                    // If we've caught an interrupt, we need to make sure it's
                    // set so the while loop stops.
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
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

        @Override
	public void run() {
            workerCount.incrementAndGet();
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
                     * thread.setName is not thread safe, so may not reflect
                     * most up to date state
                     */
                    try {
                        task = null;
                        availableThreads.incrementAndGet();
                        try {
                        task = queue.poll(idleTimeout, TimeUnit.MILLISECONDS);
                        } finally {
                            availableThreads.decrementAndGet();
                        }
                        thread.setName(NewThreadAction.NAME_PREFIX + task);
                        if (task != null) {
                            task.run();
                        } else {
                            break; //Timeout or spurious wakeup.
                        }
                         thread.setName(NewThreadAction.NAME_PREFIX + "Idle");
                    } catch (InterruptedException e){
                        thread.interrupt();
                        break;
                    }
                }
            } finally {
                workerCount.decrementAndGet();
            }
        }
    }
}
