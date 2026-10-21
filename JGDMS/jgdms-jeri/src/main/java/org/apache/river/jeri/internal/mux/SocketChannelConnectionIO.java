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

package org.apache.river.jeri.internal.mux;

import org.apache.river.jeri.internal.runtime.SelectionManager;
import org.apache.river.logging.Levels;
import java.io.EOFException;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SocketChannelConnectionIO implements the ConnectionIO abstraction for a
 * connection accessible through a java.nio.channels.SocketChannel, and thus
 * supports non-blocking I/O.
 *
 * @author Sun Microsystems, Inc.
 **/
final class SocketChannelConnectionIO extends ConnectionIO {

    private static final int RECEIVE_BUFFER_SIZE = 4096;
    private static final int IOV_MAX = 16; // max writev iovcnt on Solaris...

    /** mux logger */
    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.connection.mux");

    /** selection manager used by this implementation */
    private static final SelectionManager selectionManager;
    static {					// REMIND: share more widely?
	try {
	    selectionManager = new SelectionManager();
	} catch (IOException e) {
	    throw new ExceptionInInitializerError(e);
	}
    }

    /** How buffers are written to the channel; a test replaces it. */
    interface Writer {
	long write(ByteBuffer[] bufs, int offset, int length) throws IOException;
    }

    volatile Writer writer;

    /** socket channel for underlying connection */
    private final SocketChannel channel;

    private final SelectionManager.Key key;

    /**
     * queue of buffers of data to be sent over connection
     */
    private final Deque<ByteBuffer> sendQueue = new LinkedList<ByteBuffer>();

    /**
     * queue of alternating buffers (that are in sendQueue) and IOFuture
     * objects that need to be notified when those buffers are written
     */
    private final Deque notifyQueue = new LinkedList();

    /** buffer for reading incoming data from connection */
    private final Buffer inputBuffer =
	ByteBuffer.allocateDirect(RECEIVE_BUFFER_SIZE);	// ready for reading

    private final ByteBuffer[] preallocBufferArray = new ByteBuffer[IOV_MAX];

    /**
     * Creates a new SocketChannelConnectionIO for the connection represented
     * by the supplied SocketChannel.
     */
    SocketChannelConnectionIO(Mux mux, SocketChannel channel)
	throws IOException
    {
	super(mux);
	channel.configureBlocking(false);
	this.channel = channel;
	this.writer = (bufs, off, len) -> channel.write(bufs, off, len);
	key = selectionManager.register(channel, new Handler());
    }

    /**
     * Starts processing connection data.
     */
    @Override
    void start() throws IOException {
	key.renewInterestMask(SelectionKey.OP_READ);
    }

    /**
     * Whether a thread is draining the send queue; guarded by muxLock.  The
     * drainer is the thread that found it clear when it queued a message (or
     * the selection thread, for a write that had to wait for the socket), and
     * it writes without holding muxLock, so that messages queued meanwhile by
     * other threads join the same gathering write instead of each paying for a
     * system call under the lock.
     */
    private boolean writing;

    /**
     * Whether a write has met a full socket buffer and the selection thread has
     * been asked to continue it; callers only queue then.  Guarded by muxLock.
     */
    private boolean awaitingWriteReady;

    /**
     * Rounds of gathering writes one thread performs before leaving the rest
     * to the selection thread, so that one caller is not made to write for
     * ever on behalf of the others.
     */
    private static final int MAX_ROUNDS = 8;

    @Override
    void asyncSend(Buffer buffer) {
	int len;
	synchronized (mux.muxLock) {
	    if (mux.muxDown) {
		return;
	    }
	    sendQueue.addLast((ByteBuffer) buffer);
	    if (writing || awaitingWriteReady) {
		return;
	    }
	    writing = true;
	    len = gather();
	}
	drain(len);
    }

    @Override
    void asyncSend(Buffer first, Buffer second) {
	int len;
	synchronized (mux.muxLock) {
	    if (mux.muxDown) {
		return;
	    }
	    sendQueue.addLast((ByteBuffer) first);
	    sendQueue.addLast((ByteBuffer) second);
	    if (writing || awaitingWriteReady) {
		return;
	    }
	    writing = true;
	    len = gather();
	}
	drain(len);
    }

    @Override
    IOFuture futureSend(Buffer first, Buffer second) {
	IOFuture future = new IOFuture();
	int len;
	synchronized (mux.muxLock) {
	    if (mux.muxDown) {
		IOException ioe = new IOException(mux.muxDownMessage, mux.muxDownCause);
		future.done(ioe);
		return future;
	    }
	    sendQueue.addLast((ByteBuffer) first);
	    sendQueue.addLast((ByteBuffer) second);
	    notifyQueue.addLast(second);
	    notifyQueue.addLast(future);
	    if (writing || awaitingWriteReady) {
		return future;
	    }
	    writing = true;
	    len = gather();
	}
	drain(len);
	return future;
	/*
	 * REMIND: Can/should we implement any sort of
	 * priority inversion avoidance scheme here?
	 */
    }

    private void handleWriteReady() {
	int len;
	synchronized (mux.muxLock) {
	    awaitingWriteReady = false;
	    if (writing) {
		return;		// cannot happen: callers only queue while awaiting
	    }
	    writing = true;
	    len = gather();
	}
	drain(len);
    }

    /**
     * Copies up to IOV_MAX buffers from the head of the send queue into
     * {@code preallocBufferArray} and returns how many.  Holds muxLock; the
     * caller owns {@code writing}.
     *
     * <p>Work around 4481573: must manually break the sequence of buffers to
     * write into chunks no larger than IOV_MAX.
     */
    private int gather() {
	assert Thread.holdsLock(mux.muxLock);
	ByteBuffer[] bufs = preallocBufferArray;
	int len = 0;
	Iterator<ByteBuffer> iter = sendQueue.iterator();
	while (iter.hasNext() && len < bufs.length) {
	    bufs[len++] = iter.next();
	}
	return len;
    }

    /**
     * Writes the send queue until it is empty, the socket is full or
     * {@link #MAX_ROUNDS} rounds have been written, then clears
     * {@code writing}.  The caller has set {@code writing}; only that thread
     * touches the head of the queue, the buffers' positions and
     * {@code preallocBufferArray} until it clears it.
     */
    private void drain(int len) {
	ByteBuffer[] bufs = preallocBufferArray;		// IOV_MAX length
	try {
	    for (int round = 0; ; round++) {
		if (len == 0) {
		    synchronized (mux.muxLock) {
			// nothing to write: connection down, or an empty queue
			writing = false;
			if (mux.muxDown) {
			    failPending();
			}
			return;
		    }
		}
		writer.write(bufs, 0, len);
		synchronized (mux.muxLock) {
		    for (int i = 0; i < len; i++) {
			ByteBuffer bb = bufs[i];
			bufs[i] = null;
			assert bb == sendQueue.getFirst();
			if (bb.hasRemaining()) {
			    for (int j = i + 1; j < len; j++) {
				bufs[j] = null;
			    }
			    handOverToSelector();
			    return;
			}
			sendQueue.removeFirst();
			if (!notifyQueue.isEmpty() && bb == notifyQueue.getFirst()) {
			    notifyQueue.removeFirst();
			    IOFuture future = (IOFuture) notifyQueue.removeFirst();
			    future.done(bb.position());
			}
		    }
		    if (mux.muxDown) {
			writing = false;
			failPending();
			return;
		    }
		    if (sendQueue.isEmpty()) {
			writing = false;
			return;
		    }
		    if (round + 1 >= MAX_ROUNDS) {
			handOverToSelector();
			return;
		    }
		    len = gather();
		}
	    }
	} catch (IOException e) {
	    try {
		logger.log(Levels.HANDLED, "mux write, I/O error", e);
	    } catch (Throwable t) {
	    }
	    writeFailed("I/O error writing to mux connection: " + e.toString(), e);
	} catch (Throwable t) {
	    try {
		logger.log(Level.WARNING, "mux write, unexpected exception", t);
	    } catch (Throwable tt) {
	    }
	    writeFailed("unexpected exception in mux write: " + t.toString(), t);
	}
    }

    /** Leaves the rest of the queue to the selection thread. Holds muxLock. */
    private void handOverToSelector() throws ClosedChannelException {
	assert Thread.holdsLock(mux.muxLock);
	writing = false;
	awaitingWriteReady = true;
	key.renewInterestMask(SelectionKey.OP_WRITE);
    }

    private void writeFailed(String message, Throwable cause) {
	synchronized (mux.muxLock) {
	    java.util.Arrays.fill(preallocBufferArray, null);
	    writing = false;
	    awaitingWriteReady = false;
	}
	mux.setDown(message, cause);
	drainNotifyQueue();
	try {
	    channel.close();
	} catch (IOException ignore) {
	}
    }

    /** The connection went down under a drainer: fail what is waiting. Holds muxLock. */
    private void failPending() {
	assert Thread.holdsLock(mux.muxLock);
	drainNotifyQueue();
    }

    private void drainNotifyQueue() {
	synchronized (mux.muxLock) {
	    assert mux.muxDown;
	    while (!notifyQueue.isEmpty()) {
		notifyQueue.removeFirst();
		IOFuture future = (IOFuture) notifyQueue.removeFirst();
		IOException ioe = new IOException(mux.muxDownMessage, mux.muxDownCause);
		future.done(ioe);
	    }
	}
    }

    private void handleReadReady() {
	try {
	    int n = channel.read((ByteBuffer)inputBuffer);
	    if (n == -1) {
		throw new EOFException();
	    }
	    if (n > 0) {
		mux.processIncomingData((ByteBuffer)inputBuffer);
	    }
	    assert inputBuffer.hasRemaining();
	    key.renewInterestMask(SelectionKey.OP_READ);
	} catch (ProtocolException e) {
	    IOFuture future = null;
	    synchronized (mux.muxLock) {
		/*
		 * If mux connection is already down, then we probably got
		 * here because of the receipt of a normal protocol-ending
		 * message, like Shutdown or Error, or else something else
		 * went wrong anyway.  Otherwise, a real protocol violation
		 * was detected, so respond with an Error message before
		 * taking down the whole mux connection.
		 */
		if (!mux.muxDown) {
		    try {
			logger.log(Levels.HANDLED,
				   "mux read handler, protocol error", e);
		    } catch (Throwable t) {
		    }
		    future = mux.futureSendError(e.getMessage());
		    mux.setDown("protocol violation detected: " +
				e.getMessage(), null);
		} else {
		    try {
			logger.log(Level.FINEST, "mux read handler: {0}", e.getMessage());
		    } catch (Throwable t) {
		    }
		}
	    }
	    if (future != null) {
		try {
		    future.waitUntilDone();
		} catch (IOException ignore) {
		} catch (InterruptedException interrupt) {
		    Thread.currentThread().interrupt();
		}
	    }
	    try {
		channel.close();
	    } catch (IOException ignore) {
	    }
	} catch (IOException e) {
	    try {
		logger.log(Levels.HANDLED,
			   "mux read handler, I/O error", e);
	    } catch (Throwable t) {
	    }
	    mux.setDown("I/O error reading from mux connection: " +
			e.toString(), e);
	    try {
		channel.close();
	    } catch (IOException ignore) {
	    }
	} catch (Throwable t) {
	    try {
		logger.log(Level.WARNING,
			   "mux read handler, unexpected exception", t);
	    } catch (Throwable tt) {
	    }
	    mux.setDown("unexpected exception in mux read handler: " +
			t.toString(), t);
	    try {
		channel.close();
	    } catch (IOException ignore) {
	    }
	}
    }

    private class Handler implements SelectionManager.SelectionHandler {
        @Override
	public void handleSelection(int readyMask, SelectionManager.Key key) {
	    if ((readyMask & SelectionKey.OP_WRITE) != 0) {
		handleWriteReady();
	    }
	    if ((readyMask & SelectionKey.OP_READ) != 0) {
		handleReadReady();
	    }
	}
    }
}
