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
package com.sun.jini.thread;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ReadersPriorityWriter gives priority to waiting priority writers, then 
 * writers, then readers.  While writers or priority writers are waiting to
 * obtain a lock, new readers will wait until the priority writers and
 * writers have released all locks.
 * 
 * Unlike its parent class, ReadersPriorityWriter ensures waiting writers
 * are notified before readers, avoiding unnecessary awakening of multiple
 * readers, instead of a waiting writer.
 * 
 * @author Peter Firmstone.
 */
public class ReadersPriorityWriter extends ReadersWriter {
    private boolean activeWriter;
    private int waitingWriters;
    private int activeReaders;
    private int waitingPriorityWriters;
    private final Lock lock;
    private final Condition waitingPriorityWriter;
    private final Condition waitingWriter;
    private final Condition waitingReader;
    
    public ReadersPriorityWriter() {
        super();
        lock = new ReentrantLock();
        waitingPriorityWriter = lock.newCondition();
        waitingWriter = lock.newCondition();
        waitingReader = lock.newCondition();
        activeWriter = false;
        waitingWriters = 0;
        activeReaders = 0;
        waitingPriorityWriters = 0;
    }
    
    /** Obtain a read lock.  Multiple concurrent readers allowed. */
    public void readLock() {
        lock.lock();
        try {
            while (activeWriter || waitingWriters != 0) {
                try {
                    waitingReader.await();
                } catch (InterruptedException e) {
                    throw new ConcurrentLockException(
                                           "read lock interrupted in thread");
                }
            }
            activeReaders++;
        } finally {
            lock.unlock();
        }
    }

    /** Release a read lock. */
    public void readUnlock() {
        lock.lock();
        try {
            activeReaders--;
            if (activeReaders == 0){
                if ( waitingPriorityWriters > 0 ) waitingPriorityWriter.signal();
                else if ( waitingWriters > 0 ) waitingWriter.signal();
            }
        } finally {
            lock.unlock();
        } 
    }

    /** Obtain a regular write lock.  Only a single writer allowed at once. */
    public void writeLock() {
        lock.lock();
        try {
            while (activeWriter ||
                   activeReaders != 0 ||
                   waitingPriorityWriters != 0)
            {
                try {
                    waitingWriters++;
                    try {
                        waitingWriter.await();
                    } finally {
                        waitingWriters--;
                    }
                } catch (InterruptedException e) {
                    throw new ConcurrentLockException(
                                          "write lock interrupted in thread");
                }
            }
            activeWriter = true;
        } finally {
            lock.unlock();
        }
    }

    /** Obtain a priority write lock.  Only a single writer allowed at once. */
    public void priorityWriteLock() {
        lock.lock();
        try {
            while (activeWriter || activeReaders != 0) {
                try {
                    waitingWriters++;
                    waitingPriorityWriters++;
                    try {
                        waitingPriorityWriter.await();
                    } finally {
                        waitingWriters--;
                        waitingPriorityWriters--;
                    }
                } catch (InterruptedException e) {
                    throw new ConcurrentLockException(
                                          "write lock interrupted in thread");
                }
            }
            activeWriter = true;
        } finally {
            lock.unlock();
        }
    }

    /** Release a (regular or priority) write lock. */
    public void writeUnlock() {
        lock.lock();
        try {
            activeWriter = false;
            if (waitingWriters > 0) {
                if (waitingPriorityWriters > 0) waitingPriorityWriter.signal();
                else waitingWriter.signal();
            } else waitingReader.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
