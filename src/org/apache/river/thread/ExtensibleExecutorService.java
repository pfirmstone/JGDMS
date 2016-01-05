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

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;

/**
 * AbstractExecutorService introduced two protected methods in Java 1.6 to 
 * allow an ExecutorService to use customised RunnableFuture implementations
 * other than the default FutureTask.
 * 
 * This class requires a Factory to create the RunnableFuture, encapsulating
 * any existing ExecutorService.
 * 
 * This allows an ExecutorService to be provided by Configuration or a pool
 * to be shared without requiring that all implementations also share the same
 * type of RunnableFuture<T>.
 * 
 * @author Peter Firmstone
 */
public class ExtensibleExecutorService extends AbstractExecutorService {

    private final ExecutorService executor;
    private final RunnableFutureFactory factory;
    
    public ExtensibleExecutorService(ExecutorService executor, RunnableFutureFactory factory){
        this.executor = executor;
        this.factory = factory;
    }
    
    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable r, T value){
       return factory.newTaskFor(r, value);
    }
    
    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> c){
        return factory.newTaskFor(c);
    }
    
    @Override
    public void shutdown() {
        executor.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return executor.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return executor.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return executor.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        executor.execute(command);
    }
    
    /**
     * Factory for creating custom RunnableFuture implementations.
     */
    public interface RunnableFutureFactory {
        /**
         * Returns a <tt>RunnableFuture</tt> for the given runnable and default
         * value.
         *
         * @param runnable the runnable task being wrapped
         * @param value the default value for the returned future
         * @return a <tt>RunnableFuture</tt> which when run will run the
         * underlying runnable and which, as a <tt>Future</tt>, will yield
         * the given value as its result and provide for cancellation of
         * the underlying task.
         */
        public <T> RunnableFuture<T> newTaskFor(Runnable r, T value);
        /**
         * Returns a <tt>RunnableFuture</tt> for the given callable task.
         *
         * @param callable the callable task being wrapped
         * @return a <tt>RunnableFuture</tt> which when run will call the
         * underlying callable and which, as a <tt>Future</tt>, will yield
         * the callable's result as its result and provide for
         * cancellation of the underlying task.
         */
        public <T> RunnableFuture<T> newTaskFor(Callable<T> c);
    }
    
}
