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

import com.sun.jini.logging.Levels;
import com.sun.jini.thread.Executor;
import com.sun.jini.thread.GetThreadPoolAction;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.Pipe;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.security.AccessController;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A SelectionManager provides an event dispatching layer on top of the
 * java.nio.Selector and java.nio.SelectableChannel abstractions; it manages
 * one-shot registrations of interest in I/O readiness events and
 * dispatching notifications of such events to registered callback objects.
 *
 * SelectionManager is designed to support multiple select/dispatch threads
 * to allow for improved I/O concurrency on symmetric multiprocessor systems.
 *
 * If interest in a particular I/O event is (re-)registered while there is a
 * blocking select operation in progress, that select operation must be woken
 * up so that it can take the new interest into account; this wakeup is
 * achieved by writing a byte to an internal pipe that is registered with the
 * underlying selector.
 *
 * A current limitation of this API is that it does not allow an executing
 * callback to yield control in such a way that it will be scheduled to
 * execute again without another I/O readiness event occurring.  Therefore,
 * data that gets read during a callback must also get processed, unless such
 * processing depends on more data that has not yet been read (like the
 * remainder of a partial message).
 *
 * <p>This implementation uses the {@link Logger} named
 * <code>com.sun.jini.jeri.internal.runtime.SelectionManager</code> to
 * log information at the following levels:
 *
 * <p><table summary="Describes what is logged by SelectionManager at
 * various logging levels" border=1 cellpadding=5>
 *
 * <tr> <th> Level <th> Description
 *
 * <tr> <td> {@link Levels#HANDLED HANDLED} <td> I/O exception caught
 * from select operation
 *
 * </table>
 *
 * @author Sun Microsystems, Inc.
 **/
public final class SelectionManager {

    /** number of concurrent I/O processing threads */
    private static final int concurrency = 1;	// REMIND: get from property?

    private static final Logger logger = Logger.getLogger(
	"com.sun.jini.jeri.internal.runtime.SelectionManager");

    /** pool of threads for executing tasks in system thread group */
    private static final Executor systemThreadPool = (Executor)
	AccessController.doPrivileged(new GetThreadPoolAction(false));

    /** shared Selector used by this SelectionManager */
    private final Selector selector;

    /** internal pipe used to wake up a blocked select operation */
    private final Pipe.SinkChannel wakeupPipeSink;
    private final Pipe.SourceChannel wakeupPipeSource;
    private final SelectionKey wakeupPipeKey;
    private final ByteBuffer wakeupBuffer = ByteBuffer.allocate(2);

    /** set of registered channels, to detect duplicate registrations */
    private final Map registeredChannels = 
	Collections.synchronizedMap(new WeakHashMap());

    /**
     * lock guarding selectingThread, wakeupPending, renewQueue, readyQueue,
     * renewMaskRef, and mutable state of all Key instances.
     */
    private final Object lock = new Object();

    /** thread with exclusive right to perform a select operation, if any */
    private Thread selectingThread = null;

    /** true if a wakeup has been requested but not yet processed */
    private boolean wakeupPending = false;

    /*
     * The following two queues of Key objects are implemented as LIFO
     * linked lists with internally threaded links for fast addition and
     * removal of elements (no memory allocation overhead) and so that
     * multiple entries for the same channel get implicitly combined.
     * LIFO ordering is OK because the entire queue always gets drained
     * and processed at once, and in between such processing, addition
     * order is arbitrary anyway.
     */

    /** queue of keys that need to have interest masks updated */
    private Key renewQueue = null;

    /** queue of keys that have I/O operations ready to be handled */
    private Key readyQueue = null;

    /** holder used for pass-by-reference invocations */
    private final int[] renewMaskRef = new int[1];

    /**
     * Creates a new SelectionManager.
     *
     * REMIND: Is this necessary, or should we just provide access to
     * a singleton instance?
     */
    public SelectionManager() throws IOException {

	// REMIND: create threads and other resources lazily?

	selector = Selector.open();

	Pipe pipe = Pipe.open();
	wakeupPipeSink = pipe.sink();
	wakeupPipeSource = pipe.source();
	wakeupPipeSource.configureBlocking(false);
	wakeupPipeKey = wakeupPipeSource.register(selector,
						  SelectionKey.OP_READ);

	for (int i = 0; i < concurrency; i++) {
	    systemThreadPool.execute(new SelectLoop(),
				     "I/O SelectionManager-" + i);
	}

	// REMIND: How do these threads and other resources get cleaned up?
	// REMIND: Should there be an explicit close method?
    }

    /**
     * Registers the given SelectableChannel with this SelectionManager.
     * After registration, the returned Key's renewInterestMask method may
     * be used to register one-shot interest in particular I/O events.
     */
    public Key register(SelectableChannel channel, SelectionHandler handler) {
	if (registeredChannels.containsKey(channel)) {
	    throw new IllegalStateException("channel already registered");
	}
	Key key = new Key(channel, handler);
	registeredChannels.put(channel, null);
	return key;
    }

    /**
     * SelectionHandler is the callback interface for an object that will
     * process an I/O readiness event that has been detected by a
     * SelectionManager.
     */
    public interface SelectionHandler {
	void handleSelection(int readyMask, Key key);
    }

    /**
     * A Key represents a given SelectableChannel's registration with this
     * SelectionManager.  Externally, this object is used to re-register
     * interest in I/O readiness events that have been previously detected
     * and dispatched.
     */
    public final class Key {

	/** the channel that this Key represents a registration for */
	final SelectableChannel channel;
	/** the supplied callback object for dispatching I/O events */
	final SelectionHandler handler;

	// mutable instance state guarded by enclosing SelectionManager's lock:

	/**
	 * the SelectionKey representing this Key's registration with the
	 * internal Selector, or null if it hasn't yet been registered
	 */
	SelectionKey selectionKey = null;

	/** the current interest mask established with the SelectionKey */
	int interestMask = 0;

	boolean onRenewQueue = false;	// invariant: == (renewMask != 0)
	Key renewQueueNext = null;	// null if !onRenewQueue
	int renewMask = 0;

	boolean onReadyQueue = false;	// invariant: == (readyMask != 0)
	Key readyQueueNext = null;	// null if !onReadyQueue
	int readyMask = 0;

	/*
	 * other invariants:
	 *
	 * (renewMask & interestMask) == 0
	 * (interestMask & readyMask) == 0
	 * (renewMask & readyMask) == 0
	 */

	Key(SelectableChannel channel, SelectionHandler handler) {
	    this.channel = channel;
	    this.handler = handler;
	}

	/**
	 * Renews interest in receiving notifications when the I/O operations
	 * identified by the specified mask are ready for the associated
	 * SelectableChannel.  The specified mask identifies I/O operations
	 * with the same bit values as would a java.nio.SelectionKey for the
	 * same SelectableChannel.
	 *
	 * Some time after one of the operations specified in the mask is
	 * detected to be ready, the previously-registered SelectionHandler
	 * callback object will be invoked to handle the readiness event.
	 *
	 * An event for each operation specified will only be dispatched to the
	 * callback handler once for the invocation of this method; to
	 * re-register interest in subsequent readiness of the same operation
	 * for the given channel, this method must be invoked again.
	 */
	public void renewInterestMask(int mask)
	    throws ClosedChannelException
	{
	    if (!channel.isOpen()) {
		throw new ClosedChannelException();
	    }
	    if ((mask & ~channel.validOps()) != 0) {
		throw new IllegalArgumentException(
		    "invalid mask " + mask +
		    " (valid mask " + channel.validOps() + ")");
	    }
	    if (channel.isBlocking()) {
		throw new IllegalBlockingModeException();
	    }
	    synchronized (lock) {
		int delta = mask & ~(renewMask | interestMask | readyMask);
		if (delta != 0) {
		    addOrUpdateRenewQueue(this, delta);
		    if (selectingThread != null && !wakeupPending) {
			wakeupSelector();
			wakeupPending = true;
		    }
		}
	    }
	}
    }

    /**
     * SelectLoop provides the main loop for each I/O processing thread.
     */
    private class SelectLoop implements Runnable {

	private long lastExceptionTime = 0L;	// local to select thread
	private int recentExceptionCount;	// local to select thread

	public void run() {
	    int[] readyMaskRef = new int[1];
	    while (true) {
		try {
		    Key readyKey = waitForReadyKey(readyMaskRef);
		    readyKey.handler.handleSelection(readyMaskRef[0],
						     readyKey);
		} catch (Throwable t) {
		    try {
			logger.log(Level.WARNING, "select loop throws", t);
		    } catch (Throwable tt) {
		    }
		    throttleLoopOnException();
		}
	    }
	}

	/**
	 * Throttles the select loop after an exception has been
	 * caught: if a burst of 10 exceptions in 5 seconds occurs,
	 * then wait for 10 seconds to curb busy CPU usage.
	 **/
	private void throttleLoopOnException() {
	    long now = System.currentTimeMillis();
	    if (lastExceptionTime == 0L || (now - lastExceptionTime) > 5000) {
		// last exception was long ago (or this is the first)
		lastExceptionTime = now;
		recentExceptionCount = 0;
	    } else {
		// exception burst window was started recently
		if (++recentExceptionCount >= 10) {
		    try {
			Thread.sleep(10000);
		    } catch (InterruptedException ignore) {
		    }
		}
	    }
	}
    }

    /**
     * Waits until one of the registered channels is ready for one or more
     * I/O operations.  The Key for the ready channel is returned, and the
     * first element of the supplied array is set to the mask of the
     * channel's ready operations.
     *
     * If there is a ready channel available, then its key is returned.
     * If another thread is already performing a select operation, then the
     * current thread waits for that thread to complete and then begins
     * again.  Otherwise, the current thread assumes the responsibility of
     * performing the next select operation.
     */
    private Key waitForReadyKey(int[] readyMaskOut)
	throws InterruptedException
    {
	assert !Thread.holdsLock(lock);
	assert readyMaskOut != null && readyMaskOut.length == 1;

	boolean needToClearSelectingThread = false;
	Set selectedKeys = selector.selectedKeys();

	try {
	    synchronized (lock) {
		while (isReadyQueueEmpty() && selectingThread != null) {
		    lock.wait();
		}
		if (!isReadyQueueEmpty()) {
		    Key readyKey = removeFromReadyQueue(readyMaskOut);
		    lock.notify();
		    return readyKey;
		}

		assert selectingThread == null;
		selectingThread = Thread.currentThread();
		needToClearSelectingThread = true;

		processRenewQueue();
	    }						// wakeup allowed

	    while (true) {
		try {
		    int n = selector.select();
		    if (Thread.interrupted()) {
			throw new InterruptedException();
		    }
		} catch (Error e) {
		    String message = e.getMessage();
		    if (message != null && message.startsWith("POLLNVAL")) {
			Thread.yield();
			continue;		// work around 4458268
		    } else {
			throw e;
		    }
		} catch (CancelledKeyException e) {
		    continue;			// work around 4458268
		} catch (NullPointerException e) {
		    continue;			// work around 4729342
		} catch (IOException e) {
		    logger.log(Levels.HANDLED,
			       "thrown by select, continuing", e);
		    continue;			// work around 4504001
		}

		synchronized (lock) {
		    if (wakeupPending &&
			selectedKeys.contains(wakeupPipeKey))
		    {
			drainWakeupPipe();		// clear wakeup state
			wakeupPending = false;
			selectedKeys.remove(wakeupPipeKey);
		    }
		    if (selectedKeys.isEmpty()) {
			processRenewQueue();
			continue;
		    }

		    selectingThread = null;
		    needToClearSelectingThread = false;
		    lock.notify();

		    Iterator iter = selectedKeys.iterator();
		    assert iter.hasNext();	// there must be at least one
		    while (iter.hasNext()) {
			SelectionKey selectionKey = (SelectionKey) iter.next();
			Key key = (Key) selectionKey.attachment();

			int readyMask = 0;
			try {
			    readyMask = selectionKey.readyOps();
			    assert readyMask != 0;
			    assert (key.interestMask & readyMask) == readyMask;

			    /*
			     * Remove interest in I/O events detected to be
			     * ready; interest must be renewed after each
			     * notification.
			     */
			    int newInterestMask =
				key.interestMask & ~readyMask;
			    assert key.interestMask ==
				selectionKey.interestOps();
			    key.selectionKey.interestOps(newInterestMask);
			    key.interestMask = newInterestMask;
			} catch (CancelledKeyException e) {
			    /*
			     * If channel is closed, then all interested events
			     * become considered ready immediately.
			     */
			    readyMask |= key.interestMask;
			    key.interestMask = 0;
			}
			addOrUpdateReadyQueue(key, readyMask);

			iter.remove();
		    }

		    return removeFromReadyQueue(readyMaskOut);
		}					// wakeup NOT allowed
	    }
	} finally {
	    if (needToClearSelectingThread) {
		synchronized (lock) {
		    if (wakeupPending &&
			selectedKeys.contains(wakeupPipeKey))
		    {
			drainWakeupPipe();		// clear wakeup state
			wakeupPending = false;
			selectedKeys.remove(wakeupPipeKey);
		    }
		    selectingThread = null;
		    needToClearSelectingThread = false;
		    lock.notify();
		}					// wakeup NOT allowed
	    }
	}
    }

    private void wakeupSelector() {
	assert Thread.holdsLock(lock);
	assert wakeupPending == false;

	wakeupBuffer.clear().limit(1);
	try {
	    wakeupPipeSink.write(wakeupBuffer);
	} catch (IOException e) {
	    // REMIND: what if thread was interrupted?
	    Error error = new AssertionError("unexpected I/O exception");
	    error.initCause(e);
	    throw error;
	}
    }

    private void drainWakeupPipe() {
	assert Thread.holdsLock(lock);
	assert selectingThread != null;

	do {
	    wakeupBuffer.clear();
	    try {
		wakeupPipeSource.read(wakeupBuffer);
	    } catch (IOException e) {
		// REMIND: what if thread was interrupted?
		Error error = new AssertionError("unexpected I/O exception");
		error.initCause(e);
		throw error;
	    }
	} while (!wakeupBuffer.hasRemaining());
    }

    /**
     * In preparation for performing a select operation, process all new
     * and renewed interest registrations so that current SelectionKey
     * interest masks are up to date.
     *
     * This method must not be invoked while there is a select operation in
     * progress (because otherwise it could block indefinitely); therefore,
     * it must be invoked only by a thread that has the exclusive right to
     * perform a select operation.
     */
    private void processRenewQueue() {
	assert Thread.holdsLock(lock);
	assert selectingThread != null;

	while (!isRenewQueueEmpty()) {
	    Key key = removeFromRenewQueue(renewMaskRef);
	    int renewMask = renewMaskRef[0];
	    assert renewMask != 0;

	    if (key.selectionKey == null) {
		assert key.interestMask == 0 && key.readyMask == 0;

		try {
		    key.selectionKey = key.channel.register(selector,
							    renewMask);
		    key.selectionKey.attach(key);
		    key.interestMask = renewMask;
		} catch (ClosedChannelException e) {
		    addOrUpdateReadyQueue(key, renewMask);
		} catch (IllegalBlockingModeException e) {
		    addOrUpdateReadyQueue(key, renewMask);
		}
	    } else {
		assert (key.interestMask & renewMask) == 0;

		int newInterestMask = key.interestMask | renewMask;
		try {
		    assert key.interestMask == key.selectionKey.interestOps();
		    key.selectionKey.interestOps(newInterestMask);
		    key.interestMask = newInterestMask;
		} catch (CancelledKeyException e) {
		    addOrUpdateReadyQueue(key, newInterestMask);
		    key.interestMask = 0;
		}

		assert (key.interestMask & key.readyMask) == 0;
	    }
	}
    }

    /*
     * Queue manipulation utilities:
     */

    private boolean isRenewQueueEmpty() {
	assert Thread.holdsLock(lock);
	return renewQueue == null;
    }

    private Key removeFromRenewQueue(int[] renewMaskOut) {
	assert renewMaskOut != null && renewMaskOut.length == 1;
	assert Thread.holdsLock(lock);

	Key key = renewQueue;
	assert key != null;

	assert key.onRenewQueue;
	assert key.renewMask != 0;
	renewMaskOut[0] = key.renewMask;
	key.renewMask = 0;
	renewQueue = key.renewQueueNext;
	key.renewQueueNext = null;
	key.onRenewQueue = false;
	return key;
    }

    private void addOrUpdateRenewQueue(Key key, int newRenewMask) {
	assert newRenewMask != 0;
	assert Thread.holdsLock(lock);

	if (!key.onRenewQueue) {
	    assert key.renewMask == 0;
	    assert key.renewQueueNext == null;
	    key.renewMask = newRenewMask;
	    key.renewQueueNext = renewQueue;
	    renewQueue = key;
	    key.onRenewQueue = true;
	} else {
	    assert key.renewMask != 0;
	    assert (key.renewMask & newRenewMask) == 0;
	    key.renewMask |= newRenewMask;
	}
    }

    private boolean isReadyQueueEmpty() {
	assert Thread.holdsLock(lock);
	return readyQueue == null;
    }

    private Key removeFromReadyQueue(int[] readyMaskOut) {
	assert readyMaskOut != null && readyMaskOut.length == 1;
	assert Thread.holdsLock(lock);

	Key key = readyQueue;
	assert key != null;

	assert key.onReadyQueue;
	assert key.readyMask != 0;
	readyMaskOut[0] = key.readyMask;
	key.readyMask = 0;
	readyQueue = key.readyQueueNext;
	key.readyQueueNext = null;
	key.onReadyQueue = false;
	return key;
    }

    private void addOrUpdateReadyQueue(Key key, int newReadyMask) {
	assert newReadyMask != 0;
	assert Thread.holdsLock(lock);

	if (!key.onReadyQueue) {
	    assert key.readyMask == 0;
	    assert key.readyQueueNext == null;
	    key.readyMask = newReadyMask;
	    key.readyQueueNext = readyQueue;
	    readyQueue = key;
	    key.onReadyQueue = true;
	} else {
	    assert key.readyMask != 0;
	    assert (key.readyMask & newReadyMask) == 0;
	    key.readyMask |= newReadyMask;
	}
    }
}
