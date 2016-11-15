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

import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.river.constants.TimeConstants;

/**
 * An abstract class for building tasks that retry on failure after a
 * timeout period.  This builds upon <code>TaskManager</code> for task
 * execution and <code>WakeupManager</code> for retry scheduling.  You
 * extend <code>RetryTask</code>, implementing <code>tryOnce</code> to
 * represent a single attempt at the task.  If <code>tryOnce</code>
 * returns <code>true</code>, the attempt was successful and the task
 * is complete.  If <code>tryOnce</code> returns <code>false</code>,
 * the task is scheduled for a future retry using <code>WakeupManager</code>.
 * <p>
 * The default retry times are defined by this class's implementation
 * of <code>retryTime</code></code>.  You can override this method to
 * change the retry times.
 * <p>
 * It is legal to reuse the same task again and again, but only after
 * the task is complete and <code>reset</code> has been called.
 * Inserting the same task multiple times before it has either been
 * cancelled or completed successfully will generate unpredictable
 * behavior.<p>
 *
 * This class uses the {@link Logger} named
 * <code>org.apache.river.thread.RetryTask</code> to log information at
 * the following logging levels: <p>
 *
 * <table border=1 cellpadding=5
 *       summary="Describes logging performed by RetryTask at different
 *	          logging levels">
 *
 * <tr> <th> Level <th> Description
 *
 * <tr> <td> FINEST <td> after a failed attempt, when should 
 *                       the task be scheduled for a re-try
 *
 * </table>
 *
 * @author Sun Microsystems, Inc.
 *
 * @see TaskManager
 * @see WakeupManager
 */
import org.apache.river.thread.WakeupManager.Ticket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.river.thread.FutureObserver.ObservableFuture;

/**
 *
 * @param <V>
 */
public abstract class RetryTask<V> implements RunnableFuture<V>, ObservableFuture<V>, TimeConstants {
    private final TaskManager	  manager;	// the TaskManager for this task
    private final ExecutorService executor;
    private RetryTime	  retry;	// the retry object for this task
    private volatile boolean	  cancelled;	// have we been cancelled?
    private boolean	  complete;	// have we completed successfully?
    private volatile Ticket	  ticket;	// the WakeupManager ticket
    private final long	  startTime;	// the time when we were created 
    private final AtomicInteger attempt;	// the current attempt number
    private final WakeupManager wakeup;       // WakeupManager for retry scheduling
    private final List<FutureObserver<V>> listeners;

    /**
     * Default delay backoff times.  These are converted from
     * intervals to "time since start" by the static block below.
     *
     * @see #retryTime 
     */
    private static final long[] delays = {
	 0, // First value is never read
	 1 * SECONDS,
	 5 * SECONDS,
	10 * SECONDS,
	 1 * MINUTES,
	 1 * MINUTES,
	 5 * MINUTES,
    };

    /** Logger for this class */
    protected static final Logger logger = 
	Logger.getLogger("org.apache.river.thread.RetryTask");

    /**
     * Create a new <code>RetryTask</code> that will be scheduled with
     * the given task manager, and which will perform retry scheduling 
     * using the given wakeup manager.
     * @deprecated 
     */
    @Deprecated
    public RetryTask(TaskManager manager, WakeupManager wakeupManager) {
	this.manager = manager;
        this.executor = null;
        this.wakeup = wakeupManager;
        attempt = new AtomicInteger();
        listeners = new ArrayList<FutureObserver<V>>();
        startTime = System.currentTimeMillis();
    }
    
    /**
     * Create a new <code>RetryTask</code> that will be scheduled with
     * the given executor service, and which will perform retry scheduling 
     * using the given wakeup manager.
     * 
     * TaskManager's Task.runAfter() is not called when using
     * this constructor.
     * 
     * @param executor
     * @param wakeupManager 
     */
    public RetryTask(ExecutorService executor, WakeupManager wakeupManager){
        this.manager = null;
        this.executor = executor;
        this.wakeup = wakeupManager;
        attempt = new AtomicInteger();
        listeners = new ArrayList<FutureObserver<V>>();
        startTime = System.currentTimeMillis();
    }
    
    public boolean addObserver(FutureObserver<V> listener){
        synchronized (this){
            if (cancelled) return false;
            if (complete) {
                listener.futureCompleted(this);
                return false;
            }
            return listeners.add(listener);
        }
    }

    /**
     * Make a single attempt.  Return <code>true</code> if the attempt
     * was successful.  If the attempt is not successful, the task
     * will be scheduled for a future retry.
     */
    public abstract boolean tryOnce();

    /**
     * The <code>run</code> method used as a
     * <code>TaskManager&#046;Task</code>.  This invokes
     * <code>tryOnce</code>.  If it is not successful, it schedules
     * the task for a future retry at the time it gets by invoking
     * <code>retryTime</code>.
     *
     * @see #tryOnce
     * @see #startTime 
     */
    public void run() {		// avoid retry if cancelled
        if (cancelled) return;			// do nothing
	boolean success = false;
        try {
            success = tryOnce();
        } catch (Throwable t){
            t.printStackTrace(System.err);
            if (t instanceof Error) throw (Error) t;
            if (t instanceof RuntimeException) throw (RuntimeException) t;
        }
        if (!success) {		// if at first we don't succeed ...
            attempt.incrementAndGet();
            long at = retryTime();	// ... try, try again

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "retry of {0} in {1} ms", 
                    new Object[]{this, 
                        Long.valueOf(at - System.currentTimeMillis())});
            }
            RetryTime time = null;
            synchronized (this){
                // only create it if we need to
                if (retry == null) retry = new RetryTime();	            
                time = retry;
            }
            ticket = wakeup.schedule(at, time);
        } else {
            // Notify was here, however I noticed that during some tests,
            // the wakeup manager task was scheduled after cancelling.
            synchronized (this){
                complete = true;
                notifyAll();	
                Iterator<FutureObserver<V>> it = listeners.iterator();
                while (it.hasNext()){
                it.next().futureCompleted(this);
                }
            } // see waitFor()
        }
    }

    /**
     * Return the next time at which we should make another attempt.
     * This is <em>not</em> an interval, but the actual time.
     * <p>
     * The implementation is free to do as it pleases with the policy
     * here.  The default implementation is to delay using intervals of
     * 1 second, 5 seconds, 10 seconds, 1 minute, and 1 minute between
     * attempts, and then retrying every five minutes forever.
     * <p>
     * The default implementation assumes it is being called from
     * the default <code>run</code> method and that the current thread
     * holds the lock on this object. If the caller does
     * not own the lock the result is undefined and could result in an
     * exception.
     */
    public long retryTime() {
        int attempt = this.attempt.get();
	int index = (attempt < delays.length ? attempt : delays.length - 1); 
	return delays[index] + System.currentTimeMillis();
    }

    /**
     * Return the time this task was created, or the last
     * time {@link #reset reset} was called.
     */
    public long startTime() {
	return startTime;
    }

    /**
     * Return the attempt number, starting with zero.
     */
    public int attempt() {
	return attempt.get();
    }

    /**
     * Cancel the retrying of the task.  This ensures that there will be
     * no further attempts to invoke <code>tryOnce</code>.  It will not
     * interfere with any ongoing invocation of <code>tryOnce</code>
     * unless a subclass overrides this to do so.  Any override of this
     * method should invoke <code>super.cancel()</code>.
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        
        Ticket ticket = this.ticket;
        if (ticket != null) wakeup.cancel(ticket);
        synchronized (this) {
            cancelled = true;
            notifyAll();		// see waitFor()
            Iterator<FutureObserver<V>> it = listeners.iterator();
            while (it.hasNext()){
                it.next().futureCompleted(this);
            }
        }
        return true;
    }

    /**
     * Return <code>true</code> if <code>cancel</code> has been invoked.
     */
    @Override
    public boolean isCancelled() {
	return cancelled;
    }

    /**
     * Return <code>true</code> if <code>tryOnce</code> has returned
     * successfully.
     */
    @Override
    public boolean isDone() {
        synchronized (this){
            return complete;
        }
    }

    public boolean waitFor(long duration) throws InterruptedException {
        if (cancelled) throw new CancellationException("RetryTask was cancelled");
        synchronized (this){
            // Moved inside sync block to make check atomic
            while (!cancelled && !complete) {
                if (duration == 0 )wait();
                else wait(duration);
            }
            return complete;
        }
            
        
    }
    
    @Override
    public V get() throws InterruptedException, ExecutionException {
        waitFor(0L);
        return null;
    }
    
    @Override
    public V get(long time, TimeUnit unit) throws InterruptedException, 
            ExecutionException, TimeoutException
    {
        waitFor(unit.toMillis(time));
        return null;
    }

    /**
     * Reset values for a new use of this task, smells of object pooling
     * reset method removed.
     */
    

    /**
     * This is the runnable class for the <code>WakeupManager</code>,
     * since we need different implementations of
     * <code>WakeupManager&#046;run</code> and
     * <code>TaskManager&#046;run</code>.  
     */
    private class RetryTime implements Runnable {
	/**
	 * Time to retry the task.
	 */
	public void run() {
            ticket = null;
	    if (manager != null ) manager.add(RetryTask.this);
            if (executor != null) executor.submit(RetryTask.this);
	}
    };
}
