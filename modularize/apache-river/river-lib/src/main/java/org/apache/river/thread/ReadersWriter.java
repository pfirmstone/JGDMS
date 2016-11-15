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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * An Object to control the concurrent state.  Allows multiple readers or
 * a single writer.
 * Waiting priority writers have priority over waiting regular writers and 
 * waiting readers.
 * A single thread cannot hold a lock more than once.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public class ReadersWriter {
    private int waitingWriters;
    private final AtomicInteger waitingPriorityWriters;
    private final ReadWriteLock lock;
    private final Lock readLock;
    private final Lock writeLock;
    private final Condition waitingPriorityWriter;
    private final Condition waitingWriter;

    public ReadersWriter() {
        super();
        lock = new ReentrantReadWriteLock();
        readLock = lock.readLock();
        writeLock = lock.writeLock();
        waitingPriorityWriter = writeLock.newCondition();
        waitingWriter = writeLock.newCondition();
        waitingWriters = 0;
        waitingPriorityWriters = new AtomicInteger();
    }
    
    /**
     * Condition for use with writeLock()
     * @return 
     */
    public Condition newCondition(){
        return writeLock.newCondition();
    }

    /** Obtain a read lock.  Multiple concurrent readers allowed. */
    public void readLock() {
	// Stop new readers from obtaining read lock if priority writer
        // is waiting for current readers to finish.  Prevents writer lock 
        // starvation.
        while (waitingPriorityWriters.get() > 0){
            try {
                Thread.sleep(50L);
            } catch (InterruptedException ex) {
                // reestablish interrupted status.
                Thread.currentThread().interrupt();
            }
        }
        readLock.lock();
    }

    /** Release a read lock. */
    public void readUnlock() {
	readLock.unlock();
    }

    /** Obtain a regular write lock.  Only a single writer allowed at once. */
    public void writeLock() {
	writeLock.lock();
        while (waitingPriorityWriters.get() > 0)
        {
            try {
                waitingWriters++;
                try {
                    waitingPriorityWriter.signal();
                    waitingWriter.await();
                } finally {
                    waitingWriters--;
                }
            } catch (InterruptedException e) {
                throw new ConcurrentLockException(
                                      "write lock interrupted in thread");
            }
        }
    }

    /** Obtain a priority write lock.  Only a single writer allowed at once. */
    public void priorityWriteLock() {
	waitingPriorityWriters.getAndIncrement();
        try {
            writeLock.lock();
        } finally {
            waitingPriorityWriters.getAndDecrement();
        }
    }

    /** Release a (regular or priority) write lock. */
    public void writeUnlock() {
	// should be in a locked state or an exception will be thrown.
        try {
            if (waitingPriorityWriters.get() > 0) waitingPriorityWriter.signal();
            else if (waitingWriters > 0) waitingWriter.signal();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Release a read lock, wait the given period of time or until
     * notified by notifier, then obtain a read lock again.
     * Throws ConcurrentLockException if the thread gets interrupted;
     * in that case, the read lock is still held.
     */
    public void readerWait(Object notifier, long time) {
	try {
	    synchronized (notifier) {
		readUnlock();
		notifier.wait(time);
	    }
	} catch (InterruptedException e) {
	    throw new ConcurrentLockException(
				       "read wait interrupted in thread");
	} finally {
	    readLock();
	}
    }

    /**
     * Release a write lock, wait the given period of time or until
     * notified by notifier, then obtain a regular write lock again.
     * Throws ConcurrentLockException if the thread gets interrupted;
     * in that case, the write lock is still held.
     * @deprecated use newCondition() and {@link Condition} instead
     */
    @Deprecated
    public void writerWait(Object notifier, long time) {
	try {
	    synchronized (notifier) {
		writeUnlock();
		notifier.wait(time);
	    }
	} catch (InterruptedException e) {
	    throw new ConcurrentLockException(
				       "write wait interrupted in thread");
	} finally {
	    writeLock();
	}
    }

    /**
     * Wake up any threads waiting on this notifier.  In general, because
     * there is no wakeup-waiting flag, this method must be called from a
     * thread that holds a lock that conflicts with the lock that the waiter
     * was holding.  If the waiter calls writerWait, then waiterNotify
     * can be called either under a readLock or a writeLock, but if the
     * waiter calls readerWait, then waiterNotify should only be called
     * under a writeLock.
     */
    public void waiterNotify(Object notifier) {
	synchronized (notifier) {
	    notifier.notifyAll();
	}
    }

    /** InterruptedException transformed to a runtime exception. */
    public static class ConcurrentLockException extends RuntimeException
    {
	private static final long serialVersionUID = 7027246653257040584L;

	public ConcurrentLockException() {
	    super();
	}

	public ConcurrentLockException(String s) {
	    super(s);
	}	
    }
}
