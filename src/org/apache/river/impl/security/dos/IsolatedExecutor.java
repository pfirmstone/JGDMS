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

package org.apache.river.impl.security.dos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import au.net.zeus.collection.Ref;
import au.net.zeus.collection.RC;
import au.net.zeus.collection.Referrer;
import java.util.Collections;

/**
 * Performs Callable tasks in an isolated thread, which is terminated
 * if any Errors occur.   The daemon thread priority is minimal.
 * 
 * The caller can give up on the execution of the task by setting a timeout.
 * 
 * @param <T> 
 * @author peter
 */
public class IsolatedExecutor<T> implements ExecutorService {
    /*
     * We could optionally make this multithreaded, however once an Error
     * occurs we'd still need to shut down.
     */
    
    private Lock exRl;
    private Lock exWl;
    private ExecutorService isolateExecutor;
    private volatile State state;
    private volatile byte [] free;
    private final BlockingQueue queue;
    private final RejectedExecutionHandler policy;
    private final ThreadFactory factory;
    private final ExecutorService nullExec;
    private volatile List<Runnable> failedTasks;

    public IsolatedExecutor()
    {
        /* This Executor is single threaded, but that thread is replaced
         * if idle for extended periods.
         * SynchronousQueue has zero capacity, so it cannot create memory problems.
         */
        queue = new SynchronousQueue<Runnable>();
        ReadWriteLock rwl = new ReentrantReadWriteLock();
        exRl = rwl.readLock();
        exWl = rwl.writeLock();
        state = State.RUNNING;
        policy = new AbortPolicy();
        factory = new Factory();
        // Soft reference ok in list.
        failedTasks = RC.list(Collections.synchronizedList(new ArrayList<Referrer<Runnable>>()),Ref.SOFT, 1000L);
        nullExec = new NullExecutor(); // Can't create one lazily if memory low.
	isolateExecutor = new Executor(0, 1,
                                      60L, TimeUnit.SECONDS,
                                      queue,
                                      factory,
                                      policy);
    }
    
    private ExecutorService getExecutor(){
        exRl.lock();
        try {
            if (getIsolateExecutor() != null) return getIsolateExecutor();
        } finally { exRl.unlock(); }
        exWl.lock();
        try {
            if (getIsolateExecutor() != null) return getIsolateExecutor();
            setIsolateExecutor(new Executor(0, 1,
                                               60L, TimeUnit.SECONDS,
                                               queue,
                                               factory,
                                               policy));
            return getIsolateExecutor();
        } finally {  exWl.unlock(); }
    }
    
    /**
     * Process Callable tasks in isolation.
     * If an ExecutionException has been thrown, the task should be abandoned.
     * If an IsolationException has been thrown, an Error has occurred with
     * the IsolatedExecutor and a new object should be created.
     * 
     * @param task
     * @param timeout 
     * @param timeUnit 
     * @return
     * @throws org.apache.river.impl.security.dos.IsolatedExecutor.IsolationException
     * @throws java.util.concurrent.ExecutionException
     */
    public T process(Callable<T> task, long timeout, TimeUnit timeUnit) throws 
            ExecutionException, InterruptedException, TimeoutException {
        Future<T> result = getIsolateExecutor().submit(task);
        return result.get(timeout, timeUnit);
        }
    
    public void shutdown(){
        state = State.SHUTDOWN;
        getIsolateExecutor().shutdown();
    }
    
    public List<Runnable> shutdownNow(){
        state = State.SHUTDOWN;
        return getIsolateExecutor().shutdownNow();
    }
    
    public boolean isShutdown(){
        return getIsolateExecutor().isShutdown();
	}

    public boolean isTerminated() {
        return getIsolateExecutor().isTerminated();
	}

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return getIsolateExecutor().awaitTermination(timeout, unit);
    }
    
    public <T> Future<T> submit(Callable<T> task) {
        return getIsolateExecutor().submit(task);
    }

    public <T> Future<T> submit(Runnable task, T result) {
        return getIsolateExecutor().submit(task, result);
    }

    public Future<?> submit(Runnable task) {
        return getIsolateExecutor().submit(task);
    }

    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return getIsolateExecutor().invokeAll(tasks);
    }

    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return getIsolateExecutor().invokeAll(tasks, timeout, unit);
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return getIsolateExecutor().invokeAny(tasks);
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return getIsolateExecutor().invokeAny(tasks, timeout, unit);
    }

    public void execute(Runnable command) {
        getIsolateExecutor().execute(command);
    }

    /**
     * @return the isolateExecutor
     */
    public ExecutorService getIsolateExecutor() {
        return isolateExecutor;
    }

    /**
     * @param isolateExecutor the isolateExecutor to set
     */
    public void setIsolateExecutor(ExecutorService isolateExecutor) {
        this.isolateExecutor = isolateExecutor;
    }
    
    private class Factory implements ThreadFactory{
        ThreadGroup group;
	Factory(){
            group = new ThreadGroup("Isolated");
            group.setDaemon(true);
            group.setMaxPriority(Thread.MIN_PRIORITY);
        }
        /*
         * Because we're only single threaded, if the existing
         * Thread exits, the ThreadGroup is destroyed.
         */
	public Thread newThread(Runnable r) {
            // Try to limit the stack size of created Threads; hint to jvm.
	    Thread t = new Thread(group, r, "Isolated", 131072L);
	    t.setUncaughtExceptionHandler(new ExceptionHandler());
            free = new byte[1024]; // assign some memory
            free[0] = 1; // ensure it gets allocated by jit.
	    return t;
	}	
    }
    
    private class ExceptionHandler implements Thread.UncaughtExceptionHandler{
	
	ExceptionHandler(){
	}

	public void uncaughtException(Thread t, Throwable e) {
	    // This is only useful for logging, the jvm ignores any exceptions
            // thrown.
            System.out.println("Thread ExceptionHandler called for Isolate: \n");
            System.out.println(t);
            System.out.println(Thread.currentThread());
	    }
	}
	
    private enum State {
        SHUTDOWN, TERMINATED, RUNNING
    }
    
    private class NullExecutor implements ExecutorService {

        public void shutdown() {
            // Do nothing
}

        public List<Runnable> shutdownNow() {
            return failedTasks;
        }

        public boolean isShutdown() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public boolean isTerminated() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public <T> Future<T> submit(Callable<T> task) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public <T> Future<T> submit(Runnable task, T result) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public Future<?> submit(Runnable task) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void execute(Runnable command) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
        
    }
    
    private class Executor extends ThreadPoolExecutor{
        Executor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler){
            super( corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                    threadFactory, handler);
        }
        
        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            if (t == null && r instanceof Future<?>) {
                try {
                    ((Future<?>) r).get();
                } catch (CancellationException ce) {
                    t = ce;
                } catch (ExecutionException ee) {
                    t = ee.getCause();
                } catch (InterruptedException ie) {
                    // Don't need to interrupt thread, the Executor will do it.
                    shutdownNow();// Ensure the interrupt isn't cleared.
                } finally {
                    //TODO: Implement a shutdown hook for the jvm for other Error's.
                    /*
                     * Even though ThreadPoolExecutor only catches a RuntimeException,
                     * a FutureTask catches a Throwable, so we can get an Error
                     * cause, wrapped in an ExecutionException.
                     */
                     if ( t instanceof OutOfMemoryError || t instanceof StackOverflowError ){
                        /* Do we want to take different actions based on the error?
                         * OutOfMemoryError doesn't mean the jvm is completely devoid
                         * of memory, it indicates that there wasn't enough
                         * memory to create the last object.
                         */
                        free = null; // Free some memory to allow recovery.
                        System.gc();
                        shutdownNow(); // Interrupt all threads in Executor.
                        /* It might be tempting to throw ThreadDeath, it's not required,
                         * instead we just let the thread stack overflow, or run until
                         * no more objects can be created in the jvm.  The low
                         * thread priority ensure that performance isn't impacted
                         * by endless loops, however large memory consumption
                         * will impact performance.
                         */
                    }
                }
            }          
        }
    }
}
