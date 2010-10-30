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

package com.sun.jini.jeri.internal.mux;

import com.sun.jini.jeri.internal.runtime.SelectionManager;
import com.sun.jini.logging.Levels;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
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

    /*
     * Work around 4496906: sun.nio.ch.IOVecWrapper.<clinit> requires
     * permission to read the system property "sun.arch.data.model".
     */
    static {
	java.security.AccessController.doPrivileged(
	new java.security.PrivilegedAction() { public Object run() {
	    try {
		Class.forName("sun.nio.ch.IOVecWrapper");
	    } catch (ClassNotFoundException e) {
	    }
	    return null;
	} });
    }

    /** detail message of IOException thrown when 4854354 occurs */
    private static final String detailMessage4854354 =
	"A non-blocking socket operation could not be completed immediately";

    /** socket channel for underlying connection */
    private final SocketChannel channel;

    private final SelectionManager.Key key;

    /**
     * queue of buffers of data to be sent over connection
     */
    private final LinkedList sendQueue = new LinkedList();

    /**
     * queue of alternating buffers (that are in sendQueue) and IOFuture
     * objects that need to be notified when those buffers are written
     */
    private final LinkedList notifyQueue = new LinkedList();

    /** buffer for reading incoming data from connection */
    private final ByteBuffer inputBuffer =
	ByteBuffer.allocateDirect(RECEIVE_BUFFER_SIZE);	// ready for reading

    private final ByteBuffer[] bufferPair = new ByteBuffer[2];

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
	key = selectionManager.register(channel, new Handler());
    }

    /**
     * Starts processing connection data.
     */
    void start() throws IOException {
	key.renewInterestMask(SelectionKey.OP_READ);
    }

    void asyncSend(ByteBuffer buffer) {
	synchronized (mux.muxLock) {
	    if (mux.muxDown) {
		return;
	    }
	    try {
		if (sendQueue.isEmpty()) {
		    channel.write(buffer);
		}
		if (buffer.hasRemaining()) {
		    sendQueue.addLast(buffer);
		    key.renewInterestMask(SelectionKey.OP_WRITE);	// ###
		}
	    } catch (IOException e) {
		mux.setDown("I/O error writing to mux connection: " +
			    e.toString(), e);
		try {
		    channel.close();
		} catch (IOException ignore) {
		}
	    }
	}
    }

    void asyncSend(ByteBuffer first, ByteBuffer second) {
	synchronized (mux.muxLock) {
	    if (mux.muxDown) {
		return;
	    }
	    try {
		if (sendQueue.isEmpty()) {
		    bufferPair[0] = first;
		    bufferPair[1] = second;
		    try {
			channel.write(bufferPair);
		    } catch (IOException e) {
			// work around 4854354
			String message = e.getMessage();
			if (message != null &&
			    message.indexOf(detailMessage4854354) != -1)
			{
			    logger.log(Levels.HANDLED,
				       "ignoring to work around 4854354", e);
			} else {
			    throw e;
			}
		    }
		}
		if (!first.hasRemaining()) {
		    if (second.hasRemaining()) {
			sendQueue.addLast(second);
			key.renewInterestMask(SelectionKey.OP_WRITE);	// ###
		    }
		} else {
		    sendQueue.addLast(first);
		    sendQueue.addLast(second);
		    key.renewInterestMask(SelectionKey.OP_WRITE);	// ###
		}
	    } catch (IOException e) {
		mux.setDown("I/O error writing to mux connection: " +
			    e.toString(), e);
		try {
		    channel.close();
		} catch (IOException ignore) {
		}
	    } finally {
		bufferPair[0] = null;
		bufferPair[1] = null;
	    }
	}
    }

    IOFuture futureSend(ByteBuffer first, ByteBuffer second) {
	synchronized (mux.muxLock) {
	    IOFuture future = new IOFuture();
	    if (mux.muxDown) {
		IOException ioe = new IOException(mux.muxDownMessage);
		ioe.initCause(mux.muxDownCause);
		future.done(ioe);
		return future;
	    }
	    try {
		if (sendQueue.isEmpty()) {
		    bufferPair[0] = first;
		    bufferPair[1] = second;
		    try {
			channel.write(bufferPair);
		    } catch (IOException e) {
			// work around 4854354
			String message = e.getMessage();
			if (message != null &&
			    message.indexOf(detailMessage4854354) != -1)
			{
			    logger.log(Levels.HANDLED,
				       "ignoring to work around 4854354", e);
			} else {
			    throw e;
			}
		    }
		}
		if (!first.hasRemaining()) {
		    if (second.hasRemaining()) {
			sendQueue.addLast(second);
			key.renewInterestMask(SelectionKey.OP_WRITE);	// ###
			notifyQueue.addLast(second);
			notifyQueue.addLast(future);
		    } else {
			future.done();
		    }
		} else {
		    sendQueue.addLast(first);
		    sendQueue.addLast(second);
		    key.renewInterestMask(SelectionKey.OP_WRITE);	// ###
		    notifyQueue.addLast(second);
		    notifyQueue.addLast(future);
		}
	    } catch (IOException e) {
		mux.setDown("I/O error writing to mux connection: " +
			    e.toString(), e);
		future.done(e);
		try {
		    channel.close();
		} catch (IOException ignore) {
		}
	    } finally {
		bufferPair[0] = first;
		bufferPair[1] = second;
	    }
	    return future;
	}
	/*
	 * REMIND: Can/should we implement any sort of
	 * priority inversion avoidance scheme here?
	 */
    }

    private void handleWriteReady() {
	try {
	    synchronized (mux.muxLock) {
//		ByteBuffer[] buffers =
//		    (ByteBuffer[]) sendQueue.toArray(preallocBufferArray);
//		channel.write(buffers);
//		while (!sendQueue.isEmpty()) {
//		    ByteBuffer bb = (ByteBuffer) sendQueue.getFirst();
//		    if (!bb.hasRemaining()) {
//			sendQueue.removeFirst();
//			if (!notifyQueue.isEmpty() &&
//			    bb == notifyQueue.getFirst())
//			{
//			    notifyQueue.removeFirst();
//			    IOFuture future =
//				(IOFuture) notifyQueue.removeFirst();
//			    future.done();
//			}
//		    } else {
//			key.renewInterestMask(SelectionKey.OP_WRITE);	// ###
//			break;
//		    }
//		}

		/*
		 * Work around 4481573: must manually break sequence of
		 * buffers to write into chunks no larger than IOV_MAX.
		 */
	      gatherLoop:
		while (!sendQueue.isEmpty()) {
		    /*
		     * Copy up to the first IOV_MAX buffers of the send queue
		     * into the preallocated ByteBuffer array.
		     */
		    ByteBuffer[] bufs = preallocBufferArray; // IOV_MAX length
		    int len = sendQueue.size();
		    if (len <= bufs.length) {			// optimization
			bufs = (ByteBuffer[]) sendQueue.toArray(bufs);
		    } else {
			Iterator iter = sendQueue.iterator();	// sufficient
			len = 0;
			while (iter.hasNext() && len < bufs.length) {
			    bufs[len++] = (ByteBuffer) iter.next();
			}
		    }
		    try {
			channel.write(bufs, 0, len);
		    } catch (IOException e) {
			// work around 4854354
			String message = e.getMessage();
			if (message != null &&
			    message.indexOf(detailMessage4854354) != -1)
			{
			    logger.log(Levels.HANDLED,
				       "ignoring to work around 4854354", e);
			} else {
			    throw e;
			}
		    }
		    for (int i = 0; i < len; i++) {
			ByteBuffer bb = bufs[i];
			assert bb == sendQueue.getFirst();
			if (!bb.hasRemaining()) {
			    sendQueue.removeFirst();
			    if (!notifyQueue.isEmpty() &&
				bb == notifyQueue.getFirst())
			    {
				notifyQueue.removeFirst();
				IOFuture future =
				    (IOFuture) notifyQueue.removeFirst();
				future.done();
			    }
			} else {
			    key.renewInterestMask(SelectionKey.OP_WRITE);// ###
			    break gatherLoop;
			}
		    }
		}
	    }
	} catch (IOException e) {
	    try {
		logger.log(Levels.HANDLED,
			   "mux write handler, I/O error", e);
	    } catch (Throwable t) {
	    }
	    mux.setDown("I/O error writing to mux connection: " +
			e.toString(), e);
	    drainNotifyQueue();
	    try {
		channel.close();
	    } catch (IOException ignore) {
	    }
	} catch (Throwable t) {
	    try {
		logger.log(Level.WARNING,
			   "mux write handler, unexpected exception", t);
	    } catch (Throwable tt) {
	    }
	    mux.setDown("unexpected exception in mux write handler: " +
			t.toString(), t);
	    drainNotifyQueue();
	    try {
		channel.close();
	    } catch (IOException ignore) {
	    }
	}
    }

    private void drainNotifyQueue() {
	synchronized (mux.muxLock) {
	    assert mux.muxDown;
	    while (!notifyQueue.isEmpty()) {
		notifyQueue.removeFirst();
		IOFuture future = (IOFuture) notifyQueue.removeFirst();
		IOException ioe = new IOException(mux.muxDownMessage);
		ioe.initCause(mux.muxDownCause);
		future.done(ioe);
	    }
	}
    }

    private void handleReadReady() {
	try {
	    int n = channel.read(inputBuffer);
	    if (n == -1) {
		throw new EOFException();
	    }
	    if (n > 0) {
		mux.processIncomingData(inputBuffer);
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
			logger.log(Level.FINEST,
				   "mux read handler: " + e.getMessage());
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
