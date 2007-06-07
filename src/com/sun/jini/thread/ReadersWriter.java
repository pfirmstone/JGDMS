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

/**
 * An Object to control the concurrent state.  Allows multiple readers or
 * a single writer.  Waiting writers have priority over new readers.
 * Waiting priority writers have priority over waiting regular writers.
 * A single thread cannot hold a lock more than once.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public class ReadersWriter {
    /** The number of active readers */
    private int activeReaders = 0;
    /** The number of waiting writers (both regular and priority) */
    private int waitingWriters = 0;
    /** The number of waiting priority writers */
    private int waitingPriorityWriters = 0;
    /** True if there is an active writer */
    private boolean activeWriter = false;

    public ReadersWriter() {}

    /** Obtain a read lock.  Multiple concurrent readers allowed. */
    public synchronized void readLock() {
	while (activeWriter || waitingWriters != 0) {
	    try {
		wait();
	    } catch (InterruptedException e) {
		throw new ConcurrentLockException(
				       "read lock interrupted in thread");
	    }
	}
	activeReaders++;
    }

    /** Release a read lock. */
    public synchronized void readUnlock() {
	activeReaders--;
	if (activeReaders == 0)
	    notifyAll();
    }

    /** Obtain a regular write lock.  Only a single writer allowed at once. */
    public synchronized void writeLock() {
	while (activeWriter ||
	       activeReaders != 0 ||
	       waitingPriorityWriters != 0)
	{
	    try {
		waitingWriters++;
		try {
		    wait();
		} finally {
		    waitingWriters--;
		}
	    } catch (InterruptedException e) {
		throw new ConcurrentLockException(
				      "write lock interrupted in thread");
	    }
	}
	activeWriter = true;
    }

    /** Obtain a priority write lock.  Only a single writer allowed at once. */
    public synchronized void priorityWriteLock() {
	while (activeWriter || activeReaders != 0) {
	    try {
		waitingWriters++;
		waitingPriorityWriters++;
		try {
		    wait();
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
    }

    /** Release a (regular or priority) write lock. */
    public synchronized void writeUnlock() {
	activeWriter = false;
	notifyAll();
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
     */
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
