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

package com.sun.jini.jeri.internal.runtime;

import com.sun.jini.thread.NewThreadAction;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This class maintains a thread if necessary, in the wait state, to prevent
 * the jvm shutting down while remote objects hold strong references in the
 * DGC.
 * 
 * This implementation is more about scalability than performance, when a jvm
 * only has a small number of remote objects exported, blocking is not likely
 * to cause a performance problem, access to keepAliveCount blocked.
 * 
 * However with very large numbers of remote objects exported, in a dynamic
 * environment, blocking is unlikely to be an issue, in this case the read locks
 * will remain uncontended as the blocking write lock is only required as
 * the number of exported object approach zero.
 * 
 * If the thread is interrupted, it will pass away, regardless of the number of
 * objects exported.
 * 
 * @since 2.2.0
 * @author Peter Firmstone
 */
public class JvmLifeSupport {
    /** lock guarding keepAliveCount and keeper */
    private final ReadWriteLock rwl;
    private final Lock rl;
    private final Lock wl;

    /** number of objects exported with keepAlive == true */
    private final AtomicInteger keepAliveCount;

    /** thread to keep VM alive while keepAliveCount > 0 */
    private volatile Thread keeper;
    
    JvmLifeSupport(){
        rwl = new ReentrantReadWriteLock();
        rl = rwl.readLock();
        wl = rwl.writeLock();
        keepAliveCount = new AtomicInteger();
        keeper = null;
    }
    
    /**
     * Increments the count of objects exported with keepAlive true,
     * starting a non-daemon thread if necessary.
     * 
     * The old implementation contained in ObjectTable, used synchronization
     * on a single lock for incrementing and decrementing to judge when an
     * idle thread should be created or interrupted.
     * 
     **/
    void incrementKeepAliveCount() {
        int value;
        rl.lock();
        try {
            value = keepAliveCount.getAndIncrement();
        } finally {
            rl.unlock();
        }
        if (value < 3){
            check();
        }     
    }

    /**
     * Decrements the count of objects exported with keepAlive true,
     * stopping the non-daemon thread if decremented to zero.
     **/
    void decrementKeepAliveCount() {
        int value;
        rl.lock();
        try {
            value = keepAliveCount.decrementAndGet();
        } finally {
            rl.unlock();
        }
        if (value < 3){
            check();
        }      
    }
    
    private void check(){
        wl.lock();
        try {
            int count = keepAliveCount.get();
            if (count == 0){
                assert keeper != null;
                AccessController.doPrivileged(new PrivilegedAction() {
                    public Object run() {
                        keeper.interrupt();
                        return null;
                    }
                });
                keeper = null;
            } else if ( count > 0){
                if (keeper == null) {
                    // This thread keeps the jvm alive, while remote objects
                    // exist and all local processes have completed.
                    keeper = (Thread) AccessController.doPrivileged(
                    new NewThreadAction(new Runnable() {
                        public void run() {
                            try {
                                while (!Thread.currentThread().isInterrupted()) {
                                    Thread.sleep(Long.MAX_VALUE);
                                }
                            } catch (InterruptedException e) {
                                // pass away if interrupted
                            }
                        }
                    }, "KeepAlive", false));
                    keeper.start();
                }
            }
        } finally {
            wl.unlock();
        }
    }
}
