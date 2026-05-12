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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ThreadPool is a simple thread pool implementation of the Executor
 * interface backed by a virtual-thread-per-task executor.
 *
 * Each submitted task runs immediately in its own virtual thread.
 * There is no minimum warm thread count, nor is there a maximum thread count
 * (back-pressure is provided at higher layers via Semaphore where required).
 *
 * <p>On DirtyChai deployments, virtual thread creation is guarded by
 * {@code RuntimePermission("createVirtualThread")}.
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
 * @author Sun Microsystems, Inc.
 **/
final class ThreadPool implements Executor, java.util.concurrent.Executor {

    private static final Logger logger =
Logger.getLogger("org.apache.river.thread.ThreadPool");

    private volatile boolean shutdown = false;
    private final ExecutorService es;

    ThreadPool() {
        this(Executors.newVirtualThreadPerTaskExecutor());
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                Runtime.getRuntime().addShutdownHook(shutdownHook());
                return null;
            }
        });
    }

    private ThreadPool(ExecutorService es) {
        this.es = es;
    }

    private Thread shutdownHook() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
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
        }, "ThreadPool destroy");
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
    private static class Task implements Runnable {

private final Runnable runnable;
private final String name;

Task(Runnable runnable, String name) {
    this.runnable = runnable;
    this.name = name;
}

        @Override
        public void run() {
            Thread thread = Thread.currentThread();
            try {
                thread.setName(NewThreadAction.NAME_PREFIX + name);
                runnable.run();
            } catch (RuntimeException t) { // Don't catch Error
                logger.log(Level.WARNING, "uncaught exception", t);
                    if (t instanceof SecurityException) {
                        // ignore it will be logged.
                    } else {
                        // Ignorance of RuntimeException is generally bad, bail out.
                        throw (RuntimeException) t;
                    }
            } finally {
                thread.setName(NewThreadAction.NAME_PREFIX + "idle");
            }
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
