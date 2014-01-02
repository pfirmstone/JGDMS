/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.impl.thread;

import java.rmi.RemoteException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author peter
 */
public class SynchronouExecutorsTest {
    
    
    
    public SynchronouExecutorsTest() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of newExecutor method, of class SynchronousQueueArrayExecutor.
     */
    @Test
    public void testNewExecutor() {
        System.out.println("newExecutor");
        SynchronousExecutors instance = new SynchronousExecutors(new Exec());
        try {
            instance.start();
        } catch (Exception ex) {
            ex.printStackTrace(System.out);
        }
        ExecutorService exec = instance.newExecutor();
        Future future = exec.submit(new Exceptional());
        Object result = null;
        try {
            result = future.get(8, TimeUnit.MINUTES);
        } catch (InterruptedException ex) {
            ex.printStackTrace(System.out);
        } catch (ExecutionException ex) {
            ex.printStackTrace(System.out);
        } catch (TimeoutException ex) {
            ex.printStackTrace(System.out);
        }
        assertEquals("success", result);
        instance.shutdown();
    }
    
    private static class Exec implements ScheduledExecutorService {
        
        private final ScheduledExecutorService ses = Executors.newScheduledThreadPool(1);

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            return ses.schedule(command, delay, unit);
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            System.out.println(System.currentTimeMillis());
            System.out.println("schedule:" + delay + unit);
            return ses.schedule(callable, delay, unit);
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            return ses.scheduleAtFixedRate(command, initialDelay, period, unit);
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            return ses.scheduleWithFixedDelay(command, initialDelay, delay, unit);
        }

        @Override
        public void shutdown() {
            System.out.println("shutdown called at:");
            System.out.println(System.currentTimeMillis());
            ses.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return ses.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return ses.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return ses.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return ses.awaitTermination(timeout, unit);
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            System.out.println("submit called");
            return ses.submit(task);
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            return ses.submit(task, result);
        }

        @Override
        public Future<?> submit(Runnable task) {
            return ses.submit(task);
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
            return ses.invokeAll(tasks);
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
            return ses.invokeAll(tasks, timeout, unit);
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void execute(Runnable command) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
        
    }
    
    private static class Exceptional implements Callable {
        private final AtomicInteger tries = new AtomicInteger(0);
        @Override
        public Object call() throws Exception {
            System.out.println("Task called at:");
            System.out.println(System.currentTimeMillis());
            int tri = tries.incrementAndGet();
            if (tri < 7) throw new RemoteException("Dummy communication problem");
            return "success";
        }
        
    }
}
