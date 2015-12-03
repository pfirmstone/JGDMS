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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
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

    private static final Logger logger =
	Logger.getLogger("org.apache.river.thread.ThreadPool");
    
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
     * ThreadGroup is a construct originally intended for applet isolation, 
     * however it was never really successful, AccessControlContext 
     * is a much more effective way of controlling privilege.
     * 
     * We should consider changing this to ensure that each task is executed in the
     * AccessControlContext of the calling thread, to avoid privilege escalation.
     */
    private volatile boolean shutdown = false;
    private final ExecutorService es;
    
    ThreadPool(ThreadGroup threadGroup){
        this(Executors.newCachedThreadPool(new TPThreadFactory(threadGroup))); // Final field freeze
//      Thread not started until after constructor completes
//      this escaping occurs safely anyway because of final field freeze.
        AccessController.doPrivileged(new PrivilegedAction(){

            @Override
            public Object run() {
                Runtime.getRuntime().addShutdownHook(shutdownHook());
                return null;
            }
        });
    }
 
    private ThreadPool(ExecutorService es){
        this.es = es;
    }
    
    private Thread shutdownHook(){
        Thread t = new Thread ( new Runnable(){
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
                es.shutdown();
            }
        },"ThreadPool destroy");
        /**
         * See jtreg sun bug ID:4404702
         * This ensures that this thread doesn't unnecessarily hold 
         * a strong reference to a ClassLoader, thus preventing
         * it from being garbage collected.
         */ 
        t.setContextClassLoader(ClassLoader.getSystemClassLoader());
        return t;
    }

    // This method must not block - Executor
    @Override
    public void execute(Runnable runnable, String name) throws RejectedExecutionException {
        if (runnable == null) return;
        if (shutdown) throw new RejectedExecutionException("ThreadPool shutdown");
        Runnable task = new Task(runnable, name);
        es.submit(task);
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
            Thread thread = Thread.currentThread();
            try {
                thread.setName(NewThreadAction.NAME_PREFIX + name);
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
            } finally {
                thread.setName(NewThreadAction.NAME_PREFIX + "idle");
            }
        }

        @Override
        public String toString(){
            return name;
        }
    }
    
    /**
     * Thread stack size hint given to jvm to minimise memory consumption
     * as this executor can create many threads, tasks executed are relatively
     * simple and don't need much memory.  The jvm is free to ignore this hint.
     */
    private static class TPThreadFactory implements ThreadFactory {
        /** thread group that this pool's threads execute in */
        final ThreadGroup threadGroup;
        
        TPThreadFactory (ThreadGroup group){
            threadGroup = group;
        }

        public Thread newThread(Runnable r) {
            return AccessController.doPrivileged(
                        new NewThreadAction(threadGroup, r, NewThreadAction.NAME_PREFIX, false, 228));
        }
        
    }
}
