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

import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.river.api.util.Startable;

/**
 * The intent of this Executor is to share a single thread pool among tasks with
 * dependencies that prevent them running concurrently.
 * 
 * @author peter
 */
public class SynchronousExecutors implements Startable {
    private static final Logger logger = Logger.getLogger("org.apache.river.impl");
    private final Lock distributorLock;
    private final Condition workToDo;
    private final List<Queue<Callable>> queues ;
    private final Distributor distributor;
    private final Thread distributorThread;
    private final ScheduledExecutorService pool;
    private final AtomicBoolean distributorWaiting;
    
    public SynchronousExecutors(ScheduledExecutorService pool){
        queues = new ArrayList<Queue<Callable>>(24);
        this.pool = pool;
        distributorLock = new ReentrantLock();
        workToDo = distributorLock.newCondition();
        distributorWaiting = new AtomicBoolean(false);
        distributor = new Distributor(queues, pool, distributorLock, workToDo, distributorWaiting);
        distributorThread = new Thread(distributor ,"SynchronousQueueArray distributor");
        distributorThread.setDaemon(false);
    }
    
    void addQueue(Queue<Callable> queue){
        synchronized (queues){
            queues.add(queue);
        }
    }
    
    boolean removeQueue(Object queue){
        synchronized (queues){
            return queues.remove(queue);
        }
    }

    @Override
    public void start() throws Exception {
        distributorThread.start();
    }
    
    public void shutdown() {
        distributorThread.interrupt();
    }
    
    /**
     * The ExecutorService returned, supports a subset of ExecutorService
     * methods, the intent of this executor is to serialize the execution
     * of tasks, it is up to the BlockingQueue or caller to ensure order, only 
     * one task will execute at a time, that task will be retried if it fails,
     * using a back off strategy of 1, 5 and 10 seconds, followed by 1, 1 and 5
     * minutes thereafter forever, no other task will execute until the task
     * at the head of the queue is completed successfully.
     * 
     * Tasks submitted must implement Callable, Runnable is not supported.
     * 
     * @param <T>
     * @param queue
     * @return 
     */
    public <T> ExecutorService newSerialExecutor(BlockingQueue<Callable<T>> queue){
        QueueWrapper que = new QueueWrapper<T>(queue);
        ExecutorService serv = new SerialExecutor<T>(que, distributorWaiting, distributorLock, workToDo, this);
        addQueue(que);
        return serv;
    }
    
    private static class SerialExecutor<T> implements ExecutorService {
        
        QueueWrapper<T> queue;
        AtomicBoolean waiting;
        final Lock lock;
        final Condition workToDo;
        final SynchronousExecutors parent;
        volatile boolean terminating;
                

        SerialExecutor(QueueWrapper<T> queue, AtomicBoolean waiting, Lock lock, Condition cond, SynchronousExecutors parent){
            this.queue = queue;
            this.waiting = waiting;
            this.lock = lock;
            workToDo = cond;
            terminating = false;
            this.parent = parent;
        }
        @Override
        public void shutdown() {
            terminating = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            terminating = true;
            parent.removeQueue(queue);
            return new ArrayList(queue);
        }

        @Override
        public boolean isShutdown() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public boolean isTerminated() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            if (terminating) throw new RejectedExecutionException("ExecutorService is shutting down");
            if (task == null) throw new NullPointerException("task cannot be null");
            Task t = new Task<T>(task, queue, lock, workToDo);
            if (queue.offer(t)){
                if (waiting.get() && !queue.stalled){
                    lock.lock();
                    try {
                        workToDo.signalAll();
                    } finally {
                        lock.unlock();
                    }
                }
                return t;
            }
            throw new RejectedExecutionException("task rejected, queue likely full");
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            throw new UnsupportedOperationException("Not supported."); 
        }

        @Override
        public Future<?> submit(Runnable task) {
            throw new UnsupportedOperationException("Not supported."); 
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
            if (terminating) throw new RejectedExecutionException("ExecutorService is shutting down");
            List<Future<T>> result = new ArrayList<Future<T>>(tasks.size());
            Iterator<? extends Callable<T>> it = tasks.iterator();
            while (it.hasNext()){
                result.add(submit(it.next()));
            }
            return result;
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
            throw new UnsupportedOperationException("Not supported yet."); 
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
            throw new UnsupportedOperationException("Not supported yet."); 
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void execute(Runnable command) {
            throw new UnsupportedOperationException("Not supported.");
        }
        
    }
    
    private static class Distributor implements Runnable {
        
//        private final Random selector = new Random();
        private final List<Queue<Callable>> queues;
        private final ScheduledExecutorService executor;
        private final Lock lock;
        private final Condition workToDo;
        private final AtomicBoolean waiting;
        
        Distributor(List<Queue<Callable>> queues, ScheduledExecutorService executor, Lock lock, Condition workToDo, AtomicBoolean waiting){
            this.queues = queues;
            this.executor = executor;
            this.lock = lock;
            this.workToDo = workToDo;
            this.waiting = waiting;
        }

        @Override
        public void run() {
            int nullCount = 0; // sequence of null tasks
            int size = 0;
            List<Callable> tasks = new ArrayList<Callable>(64);
            try {
                while (!Thread.currentThread().isInterrupted()){
                    try {
                        Queue<Callable> queue = null;
                        synchronized (queues){
                            size = queues.size();
//                                if (size > 0){
//                                    int index = selector.nextInt(size);
//                                    queue = queues.get(index);
//                                }
                            for (int i = 0; i < size; i++){
                                queue = queues.get(i);
                                Callable task = queue != null ? queue.peek() : null;
                                if (task != null) tasks.add(task);
                                else nullCount++;
                            }
                        }

                        Iterator <Callable> it = tasks.iterator();
                        while (it.hasNext()){
                            Callable task = it.next();
                            long delay = 0;
                            if (task instanceof Task) delay = ((Task)task).delay();
                            if (delay == 0) executor.submit(task);
                            else executor.schedule(task, delay, TimeUnit.MILLISECONDS);
                            nullCount = 0; // reset null count
                        }
                        tasks.clear();
                        if (nullCount >= size ) {
                            // Time for a nap.
                            lock.lock();
                            try {
                                waiting.set(true);
                                workToDo.await(2, TimeUnit.SECONDS);
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt(); // restore
                            } finally {
                                waiting.set(false);
                                lock.unlock();
                            }
                        }
                    } catch (Exception e){
                        System.out.println(e);
                        logger.log(Level.FINE, "Exception thrown by distributor: {0}", e);
                    }
                }// end while
            } finally {
                executor.shutdown();
            }
        }
    }
    
    private static class QueueWrapper<T> extends AbstractQueue<Callable<T>> implements Queue<Callable<T>>{
        
        final ReentrantLock lock; // lock to control the head of the queue.
        final Queue<Callable<T>> queue;
        Callable<T> peek;// Only ever accessed by distributor thread.
        boolean stalled;
        
        QueueWrapper(Queue<Callable<T>> queue){
            this.queue = queue;
            lock = new ReentrantLock();
            peek = null;
            stalled = false;
        }

        @Override
        public Iterator<Callable<T>> iterator() {
            return queue.iterator();
        }

        @Override
        public int size() {
            return queue.size();
        }

        @Override
        public boolean offer(Callable<T> e) {
            return queue.offer(e);
        }

        @Override
        public Callable<T> poll() {
            lock.lock();
            try {
                return queue.poll();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public Callable<T> peek() {
            boolean locked = lock.tryLock();
            if (!locked) return null; // Pretend queue empty so director tries another queue.
            try {
                if (peek == null) {
                    peek = queue.peek();
                    return peek;
                } else {
                    stalled = true;
                    return null; // Pretend queue empty so director tries another queue.
                }
            } finally {
                lock.unlock();
            }
        }
        
    }
    
    private static class Task<T> implements Callable<T>, Future<T>, Comparable<Task> {
        
        /**
         * Default delay backoff times. 
         *
         * @see #retryTime 
         */
        private static final long[] delays = {
             0, // First value is never read
             TimeUnit.SECONDS.toMillis(1),
             TimeUnit.SECONDS.toMillis(5),
             TimeUnit.SECONDS.toMillis(10),
             TimeUnit.MINUTES.toMillis(1),
             TimeUnit.MINUTES.toMillis(1),
             TimeUnit.MINUTES.toMillis(5)
        };
        
        
        volatile boolean complete = false;
        volatile boolean cancelled = false;
        volatile T result = null;
        volatile Exception exception = null;
        volatile Thread executorThread;
        private final Callable<T> task;
        private final QueueWrapper queue;
        private final Lock executorLock;
        private final Condition waiting;
        private final Condition resultAwait;
        private final boolean comparable;
        private int attempt;
        private volatile long retryTime;
        
        Task(Callable<T> c, QueueWrapper wrapper, Lock executorLock, Condition distributorWaiting){
            task = c;
            comparable = task instanceof Comparable;
            queue = wrapper;
            this.waiting = distributorWaiting;
            resultAwait = queue.lock.newCondition();
            attempt = 0;
            this.executorLock = executorLock;
        }
        
        /**
         * Return the next time at which we should make another attempt.
         * This is <em>not</em> an interval, but the actual time.
         * <p>
         * The implementation is free to do as it pleases with the policy
         * here.  The default implementation is to delay using intervals of
         * 1 second, 5 seconds, 10 seconds, and 1 minute between
         * attempts, and then retrying every five minutes forever.
         * <p>
         * The default implementation assumes it is being called from
         * the default <code>run</code> method and that the current thread
         * holds the lock on this object. If the caller does
         * not own the lock the result is undefined and could result in an
         * exception.
         */
        long delay() {
            int index = (attempt < delays.length ? attempt : delays.length - 1); 
            return delays[index];
        }

        public T call() throws Exception {
            if (cancelled) return null;
            boolean reschedule = false;
            queue.lock.lock();
            try {
                result = task.call();
                if (((Task)queue.peek).task == task 
                        && task == ((Task)queue.queue.peek()).task)
                {
                    queue.queue.poll(); // Remove successfully completed task from queue.
                } 
                queue.peek = null; // set peek to null to unblock queue.
                queue.stalled = false;
                executorLock.lock();
                try {
                    waiting.signalAll();
                } finally {
                    executorLock.unlock();
                }
                complete = true;
                resultAwait.signalAll();
                return result;
            } catch (Exception e) {
                exception = e;
                reschedule = true;
                throw e;
            } finally {
                try {
                    if (reschedule) {
                        attempt++;
                        queue.peek = null; // set peek to null to unblock queue.
                        executorLock.lock();
                        try {
                            waiting.signalAll();
                        } finally {
                            executorLock.unlock();
                        }
                    }
                } finally {
                    queue.lock.unlock();
                }
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return complete;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            if (complete) {
                if (exception != null) throw new ExecutionException(exception);
                return result;
            }
            queue.lock.lock();
            try {
                while (!complete){
                    resultAwait.await();
                }
                return result;
            } finally {
                queue.lock.unlock();
            }
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            if (complete) return result;
            long begin = System.currentTimeMillis();
            if (queue.lock.tryLock(timeout, unit)){
                try {
                    while (!complete){
                        long remain = unit.toMillis(timeout) - (System.currentTimeMillis() - begin);
                        if ( 1L > remain ) {
                            if (exception != null) throw new ExecutionException(exception);
                            throw new TimeoutException(
                                    "Timed out while waiting for result");
                        }
                        resultAwait.await(remain, TimeUnit.MILLISECONDS);
                    }
                    return result;
                } finally {
                    queue.lock.unlock();
                }
            } else {
                throw new TimeoutException("Timed out while waiting for lock");
            }
        }

        @Override
        public int compareTo(Task o) {
            if (comparable) {
                return ((Comparable) task).compareTo(o.task);
            }
            int h1 = task.hashCode();
            int h2 = o.task.hashCode();
            if ( h1 < h2 ) return -1;
            if ( h1 > h2 ) return 1;
            return 0;
        }

        
    
    }
    
}