/* Copyright (c) 2010-2012 Zeus Project Services Pty Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.river.concurrent;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ReferenceProcessor is responsible for creation and collection of References 
 * on behalf of Reference Collection implementations.
 *
 * @param <T> 
 * @author peter
 */
class ReferenceProcessor<T> implements ReferenceQueuingFactory<T, Referrer<T>> {
    
    private final static ScheduledExecutorService garbageCleaner =
            Executors.newScheduledThreadPool(1, new SystemThreadFactory());
    // Map to register newly created object references.
    private final static Map<Reference,ScheduledFuture> finalizerTasks = 
            new ConcurrentHashMap<Reference,ScheduledFuture>();
    // Finalizer queue to advise cancellation of ScheduledFuture's, 
    // when their ReferenceProcessor has been collected.
    private final static ReferenceQueue<Reference> phantomQueue = 
            new ReferenceQueue<Reference>();
    static {
        // Finizer Task to cancel unneeded tasks.
        garbageCleaner.scheduleAtFixedRate(
                new FinalizerTask(phantomQueue, finalizerTasks), 
                5L, 5L, TimeUnit.MINUTES
                );
    }
    
    private final Collection<Referrer<T>> col;
    private final Object colLock;
    private final RefQueue<T> queue;
    private final Ref type;
    private final Lock queueLock;
    private final boolean gcThreads;
    private volatile boolean started = false;
    
    ReferenceProcessor(Collection<Referrer<T>> col, Ref type, RefQueue<T> queue, boolean gcThreads, Object lock){
        if (col == null || type == null ) throw new NullPointerException("collection or reference type cannot be null");
        this.col = col;
        colLock = lock;
        this.type = type;
        this.queue = type == Ref.STRONG ? null : queue;
        this.gcThreads = gcThreads;
        queueLock = new ReentrantLock();
    }
    
    /**
     * Register with executor service and finaliser for cleanup.
     * @param GcInterval time interval between scheduled cleaning runs.
     */
    public void start(long GcInterval){
       if (started) return; // Start once only.
       synchronized (this){
           if (started) return;
           started = true;
       }
       if (queue == null) return;
       long enqDelay = GcInterval * (9L/10L);
       // Enque garbage task preceeds cleaner task slightly, so we don't 
       // consume too much memory with massive collections.
       // It would be more efficient for the enque task to perform removal
       // from the collection while iterating, however it has been left
       // this way in case we want to combine time and soft or weak reference
       // behaviour in some way.
       ScheduledFuture task;
       task =  gcThreads || type.equals(Ref.TIME)? 
	       garbageCleaner.scheduleAtFixedRate(
		       new CleanerTask(col, queue),
		       GcInterval,
		       GcInterval,
		       TimeUnit.MILLISECONDS
	       ) 
	       : null;
       scheduleFinaliserTask(task);
       task = type.equals(Ref.TIME)? 
	       garbageCleaner.scheduleAtFixedRate(
		       new EnqueGarbageTask(col, colLock),
		       enqDelay,
		       GcInterval,
		       TimeUnit.MILLISECONDS
	       )
               : null;
       scheduleFinaliserTask(task);
    }
    
    private void scheduleFinaliserTask(ScheduledFuture task){
        if ( task != null ){
           // Register with finaliser.
            @SuppressWarnings("unchecked")
           Reference r = new PhantomReference(this, phantomQueue);
           finalizerTasks.put(r, task);
       }
    }

    @Override
    public T pseudoReferent(Referrer<T> u) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public Referrer<T> referenced(T w, boolean enque, boolean temporary) {
        if (w == null) return null;
        if (temporary) return ReferenceFactory.singleUseForLookup(w, type);
        return ReferenceFactory.create(w, enque == true ? queue : null, type);
    }

    @Override
    public void processQueue() {
        if (queue == null || gcThreads) return;
        Object t = null;
        /*
         * The reason for using an explicit lock is if another thread is
         * removing the garbage, we don't want to prevent all other threads
         * accessing the underlying collection, when it blocks on poll,
         * this means that some client threads will receive null values 
         * on occassion, but this is a small price to pay.  
         * Might have to employ the null object pattern.
         */
        if ( queueLock.tryLock()){
            try {
                while ( (t = queue.poll()) != null){
                    col.remove(t);
                }
            }finally{
                queueLock.unlock();
            }
        }
    }
    
    private static class EnqueGarbageTask implements Runnable{
        private final Collection col;
        private final Object lock; // This could be the same as col, or a map.
        
        EnqueGarbageTask(Collection c, Object lock){
            col = c;
            this.lock = lock;
        }
        
        public void run() {
            long time = System.nanoTime();
	    if (lock instanceof ConcurrentMap ||
		lock instanceof ConcurrentSkipListSet ||
		lock instanceof CopyOnWriteArrayList ||
		lock instanceof CopyOnWriteArraySet ||
		lock instanceof ConcurrentLinkedQueue ||
		lock instanceof ConcurrentLinkedDeque) 
	    {
		Iterator it = col.iterator();
		while (it.hasNext()){
		    Object r = it.next();
		    if (r instanceof TimeBomb) {
			((TimeBomb)r).updateClock(time);
		    }
		}
	    } else {
		synchronized (lock){
		    Iterator it = col.iterator();
		    while (it.hasNext()){
			Object r = it.next();
			if (r instanceof TimeBomb) {
			    ((TimeBomb)r).updateClock(time);
			}
		    }
		}
	    }
        }
        
    }
    
    private static class CleanerTask implements Runnable {
        
        private final Collection col;
        private final RefQueue queue;
        
        private CleanerTask(Collection c, RefQueue queue){
            col = c;
            this.queue = queue;
        }
        
        @Override
        public void run() {
            try {
                for ( Object t = queue.poll(); t != null; t = queue.poll()){ 
                    col.remove(t);
		    if (t instanceof Referrer){
			Object referent = ((Referrer)t).get();
			if (referent instanceof AutoCloseable){
			   try{
			       // Release any resources held by the referent.
			       ((AutoCloseable) referent).close();
			   } catch (Exception ex){} // Ignore
                }
		    }
                }
            }catch(Exception e){
                e.printStackTrace(System.err);
            }
        }
    
    }
    
    private static class FinalizerTask implements Runnable {
        
        private final ReferenceQueue phantomQueue;
        private final Map<Reference,ScheduledFuture> finalizerTasks ;
        
        private FinalizerTask(ReferenceQueue queue, 
                Map<Reference,ScheduledFuture> tasks){
            phantomQueue = queue;
            finalizerTasks = tasks;
        }

        @Override
        public void run() {
            Reference p;
            while ( (p = phantomQueue.poll()) != null){
                ScheduledFuture sf = finalizerTasks.remove(p);
                if (sf !=null) sf.cancel(true);
                // phantom reference is eligible for gc we don't have to
                // clear it, but might as well.
                p.clear();
            }
        }
        
    }
    
    private static class SystemThreadFactory implements ThreadFactory{
        private static final ThreadGroup g;
        
        static {
            ThreadGroup tg = Thread.currentThread().getThreadGroup();
            g = AccessController.doPrivileged( new ThreadGroupAction(tg));
        }

        private SystemThreadFactory(){
        }
        
        @Override
        public Thread newThread(Runnable r) {
            return AccessController.doPrivileged( new CreateThread(g, r));
        }
        
    }
    
    private static class ThreadGroupAction implements PrivilegedAction<ThreadGroup>{
        private ThreadGroup tg;
        
        ThreadGroupAction(ThreadGroup g){
            tg = g;
        }
        public ThreadGroup run() {
            try {
                ThreadGroup parent = tg.getParent();
                while (parent != null){
                    tg = parent;
                    parent = tg.getParent();
                }
            }catch (SecurityException e){
                Logger.getLogger(ReferenceProcessor.class.getName()).log(Level.FINE, "Unable to get parent thread group", e);
            }
            return tg;
        }
    }
    
    private static class CreateThread implements PrivilegedAction<Thread>{
        private ThreadGroup g;
        private Runnable r;
        
        CreateThread(ThreadGroup g, Runnable r){
            this.g = g;
            this.r = r;
        }
        public Thread run() {
            Thread t = new Thread(g, r, "Reference collection cleaner");
            try {
                t.setContextClassLoader(null);
                t.setPriority(Thread.MAX_PRIORITY);
            } catch (SecurityException e){
                Logger.getLogger(ReferenceProcessor.class.getName()).log(Level.FINE, "Unable to set ContextClassLoader or Priority", e);
            }
            return t;
        }
        
    }
    
}
