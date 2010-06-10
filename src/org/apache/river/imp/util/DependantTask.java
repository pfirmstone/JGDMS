/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.imp.util;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * For simple Task dependencies.
 * 
 * You can utilise this DependantTask to ensure that all tasks that must be
 * run first are.  This is done by implementing a PriorityHander which determines
 * at run time which other tasks must be run first.  The DependantTask will
 * ensure that these other tasks are run first, they may already be complete
 * when retrieved.  DependantTask can be 
 * handed to an Executor such as ThreadPoolExecutor.  The ThreadPoolExecutor
 * may retrieve tasks from it's queue out of order, the DependantTask will
 * ensure order.  The Set passed to the DependantTask by the PriorityHandler
 * will be executed in the natural order of the set.
 * 
 * @author Peter Firmstone.
 */
public class DependantTask<V> extends FutureTask<V> {
    private final PriorityHandler priority;
    private volatile boolean cancel = false;
    private volatile boolean mayInterruptIfRunning = false;
    private volatile RunnableFuture current = new NullRunnableFuture();
    
    @SuppressWarnings("unchecked")
    public DependantTask(Runnable task, V result, PriorityHandler ph ){
        super (task, result);
        priority = ph;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void run(){
        Set<RunnableFuture> temp = priority.getPreceedingTasks(this);
        Set<RunnableFuture> runFirst = new TreeSet();      
        runFirst.addAll(temp);
        Iterator<RunnableFuture> itr = runFirst.iterator();
        while ( !cancel && itr.hasNext()){
            current = itr.next();
            current.run();
        }
        current = new NullRunnableFuture();
        if (cancel) return;
        // Now lets wait until each task is complete.
        itr = runFirst.iterator();
        while ( !cancel && itr.hasNext()){
            try {
                current = itr.next();
                current.get();
            } catch (InterruptedException ex) {
                super.setException(ex);
                return;
            } catch (ExecutionException ex) {
                super.setException(ex);
                return;
            }
        }
        super.run();
    }
    
    @Override
    protected void done(){
        cancel = true;
        current.cancel(mayInterruptIfRunning);
    }
    
    public boolean cancel(boolean mayInterruptIfRunning){
        /* If the thread hasn't reached the super task yet
         * it will have to be cancelled by the currently
         * executing Runnable.
         */ 
        if (cancel){return false;}
        this.mayInterruptIfRunning = mayInterruptIfRunning;
        return super.cancel(mayInterruptIfRunning);
    }

    private static class NullRunnableFuture implements RunnableFuture{

        public NullRunnableFuture() {
        }

        public void run() {
            return;
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        public boolean isCancelled() {
            return true;
        }

        public boolean isDone() {
            return true;
        }

        public Object get() throws InterruptedException, ExecutionException {
            throw new ExecutionException("Null RunnableFuture not Executable", null);
        }

        public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            throw new ExecutionException("Null RunnableFuture not Executable", null);
        }
    }
}
