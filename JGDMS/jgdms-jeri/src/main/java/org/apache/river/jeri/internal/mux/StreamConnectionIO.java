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

import org.apache.river.logging.Levels;
import org.apache.river.thread.Executor;
import org.apache.river.thread.GetThreadPoolAction;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.security.AccessController;
import java.util.Deque;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * StreamConnectionIO implements the ConnectionIO abstraction for a
 * connection accessible through standard (blocking) I/O streams, i.e.
 * java.io.OutputStream and java.io.InputStream.
 *
 * @author Sun Microsystems, Inc.
 **/
final class StreamConnectionIO extends ConnectionIO {

    private static final int RECEIVE_BUFFER_SIZE = 2048;

    /**
     * pool of threads for executing tasks in system thread group:
     * used for I/O (reader and writer) threads
     */
    private static final Executor systemThreadPool =
	(Executor) AccessController.doPrivileged(
	    new GetThreadPoolAction(false));

    /** mux logger */
    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.connection.mux");

    /** I/O streams for underlying connection */
    private final OutputStream out;
    private final InputStream in;

    /** channels wrapped around underlying I/O streams */
    private final WritableByteChannel outChannel;
    private final ReadableByteChannel inChannel;

    /**
     * queue of buffers of data to be sent over connection, interspersed
     * with IOFuture objects that need to be notified in sequence
     * 
     * Synchronised on super.mux.muxLock;
     */
    private final Deque sendQueue;

    
    /**
     * Creates a new StreamConnectionIO for the connection represented by
     * the supplied OutputStream and InputStream pair.
     */
    StreamConnectionIO(Mux mux, OutputStream out, InputStream in) {
	super(mux);
	this.out = out;
//	this.out = new BufferedOutputStream(out);
	this.in = in;

	outChannel = newChannel(out);
	inChannel = newChannel(in);
        sendQueue = new LinkedList<Buffer>();
    }

    /**
     * Starts processing connection data.  This method starts
     * asynchronous actions to read and write from the connection.
     */
    @Override
    void start() throws IOException {
	try {
	    systemThreadPool.execute(new Writer(), "mux writer");
	    systemThreadPool.execute(new Reader(), "mux reader");
	} catch (OutOfMemoryError e) {	// assume out of threads
	    try {
		logger.log(Level.WARNING,
			   "could not create thread for request dispatch", e);
	    } catch (Throwable t) {
	    }
	    throw new IOException("could not create I/O threads", e);
	}
    }

    @Override
    void asyncSend(Buffer buffer) {
	synchronized (mux.muxLock) {
	    if (mux.muxDown) {
		return;
	    }
	    sendQueue.addLast(buffer);
	    mux.muxLock.notifyAll();
	}
    }

    @Override
    void asyncSend(Buffer first, Buffer second) {
	synchronized (mux.muxLock) {
	    if (mux.muxDown) {
		return;
	    }
	    sendQueue.addLast(first);
	    sendQueue.addLast(second);
	    mux.muxLock.notifyAll();
	}
    }

    @Override
    IOFuture futureSend(Buffer first, Buffer second) {
	synchronized (mux.muxLock) {
	    IOFuture future = new IOFuture();
	    if (mux.muxDown) {
		IOException ioe = new IOException(mux.muxDownMessage, mux.muxDownCause);
		future.done(ioe);
		return future;
	    }
	    sendQueue.addLast(first);
	    sendQueue.addLast(second);
	    sendQueue.addLast(future);
	    mux.muxLock.notifyAll();
	    return future;
	}
	/*
	 * REMIND: Can/should we implement any sort of
	 * priority inversion avoidance scheme here?
	 */
    }

    private class Writer implements Runnable {
	Writer() { }

        @Override
	public void run() {
	    Deque localQueue = null;
	    try {
		while (true) {
		    synchronized (mux.muxLock) {
			while (!mux.muxDown && sendQueue.isEmpty()) {
			    /*
			     * REMIND: Should we use a timeout here, to send
			     * occasional PING messages during periods of
			     * inactivity, to make sure connection is alive?
			     */
			    mux.muxLock.wait();
			    /*
			     * Let an interrupt during the wait just kill this
			     * thread, because an interrupt during an I/O write
			     * would leave it in an unrecoverable state anyway.
			     */
			}
			if (mux.muxDown && sendQueue.isEmpty()) {
			    logger.log(Level.FINEST,
				       "mux writer thread dying, connection " +
				       "down and nothing more to send");
			    break;
			}
                        /* Clone an unshared copy and clear the queue while synchronized */
			localQueue = new LinkedList(sendQueue);
			sendQueue.clear();
		    }

		    boolean needToFlush = false;
                    Buffer last = null;
                    int lastIndex = Integer.MIN_VALUE;
		    for  ( int i = 0; !localQueue.isEmpty(); i++) {
			Object next = localQueue.getFirst();
			if (next instanceof ByteBuffer) {
                            Buffer buffer = (Buffer) next;
			    outChannel.write((ByteBuffer)buffer);
                            last = buffer;
                            lastIndex = i;
			    needToFlush = true;
			} else {
			    assert next instanceof IOFuture;
			    if (needToFlush) {
				out.flush();
				needToFlush = false;
			    }
                            if (lastIndex == i - 1 && last != null){
                                ((IOFuture) next).done(last.position());
                            } else {
                                ((IOFuture) next).done();
                            }
			}
			localQueue.removeFirst();
		    }
		    if (needToFlush) {
			out.flush();
		    }
		}
	    } catch (InterruptedException e) {
		try {
		    logger.log(Level.WARNING,
			       "mux writer thread dying, interrupted", e);
		} catch (Throwable t) {
		}
		mux.setDown("mux writer thread interrupted", e);
	    } catch (IOException e) {
		try {
		    logger.log(Levels.HANDLED,
			       "mux writer thread dying, I/O error", e);
		} catch (Throwable t) {
		}
		mux.setDown("I/O error writing to mux connection: " +
			    e.toString(), e);
	    } catch (Throwable t) {
		try {
		    logger.log(Level.WARNING,
			"mux writer thread dying, unexpected exception", t);
		} catch (Throwable tt) {
		}
		mux.setDown("unexpected exception in mux writer thread: " +
			    t.toString(), t);
	    } finally {
		synchronized (mux.muxLock) {
		    assert mux.muxDown;
		    if (localQueue != null) {
			drainQueue(localQueue);
		    }
		    drainQueue(sendQueue);
		}
		try {
		    outChannel.close();
		} catch (IOException e) {
		}
	    }
	}
    }

    private void drainQueue(Deque queue) {
	while (!queue.isEmpty()) {
	    Object next = queue.removeFirst();
	    if (next instanceof IOFuture) {
		IOException ioe = new IOException(mux.muxDownMessage, mux.muxDownCause);
		((IOFuture) next).done(ioe);
	    }
	}
    }

    private class Reader implements Runnable {
        /** buffer for reading incoming data from connection */
        private final Buffer inputBuffer =
            ByteBuffer.allocate(RECEIVE_BUFFER_SIZE);	// ready for reading

	Reader() { }

	public void run() {
	    try {
		while (true) {
		    int n = inChannel.read((ByteBuffer)inputBuffer);
		    if (n == -1) {
			throw new EOFException();
		    }
		    assert n > 0;	// channel is assumed to be blocking
		    mux.processIncomingData(inputBuffer);
		    assert inputBuffer.hasRemaining();
		}
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
				"mux reader thread dying, protocol error", e);
			} catch (Throwable t) {
			}
			future = mux.futureSendError(e.getMessage());
			mux.setDown("protocol violation detected: " +	
				    e.getMessage(), null);
		    } else {
			try {
			    logger.log(Level.FINEST,
				"mux reader thread dying: " + e.getMessage());
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
	    } catch (IOException e) {
		try {
		    logger.log(Levels.HANDLED,
			       "mux reader thread dying, I/O error", e);
		} catch (Throwable t) {
		}
		mux.setDown("I/O error reading from mux connection: " +
			    e.toString(), e);
	    } catch (Throwable t) {
		try {
		    logger.log(Level.WARNING,
			"mux reader thread dying, unexpected exception", t);
		} catch (Throwable tt) {
		}
		mux.setDown("unexpected exception in mux reader thread: " +
			    t.toString(), t);
	    } finally {
		try {
		    inChannel.close();
		} catch (IOException e) {
		}
	    }
	}
    }

    /**
     * The following two methods are modifications of their
     * equivalents in java.nio.channels.Channels with the assumption
     * that the supplied byte buffers are backed by arrays, so no
     * additional copying is required.
     */

    public static ReadableByteChannel newChannel(final InputStream in) {
	return new ReadableByteChannel() {
	    private boolean open = true;

            // must be synchronized as per ReadableByteChannel contract
            @Override
	    public synchronized int read(ByteBuffer dst) throws IOException {
		assert dst.hasArray();
		byte[] array = dst.array();
		int arrayOffset = dst.arrayOffset();

		int totalRead = 0;
		int bytesRead = 0;
		int bytesToRead;
		while ((bytesToRead = dst.remaining()) > 0) {
		    if ((totalRead > 0) && !(in.available() > 0)) {
			break; // block at most once
		    }
		    int pos = dst.position();
		    bytesRead = in.read(array, arrayOffset + pos, bytesToRead);
		    if (bytesRead < 0) {
			break;
		    } else {
                        ((Buffer)dst).position(pos + bytesRead);
			totalRead += bytesRead;
		    }
		}
		if ((bytesRead < 0) && (totalRead == 0)) {
		    return -1;
		}

		return totalRead;
	    }
                
            @Override
	    public synchronized boolean isOpen() {
		return open;
	    }
            
            // Blocking as per Channel contract
            @Override
	    public synchronized void close() throws IOException {
		in.close();
		open = false;
	    }
	};
    }

    public static WritableByteChannel newChannel(final OutputStream out) {
	return new WritableByteChannel() {
	    private volatile boolean open = true;
            
            // This method must block while writing as per WritableByteChannel contract.
            @Override
	    public synchronized int write(ByteBuffer src) throws IOException {
                    assert src.hasArray();

                    int len = src.remaining();
                    if (len > 0) {
                        int pos = src.position();
                        out.write(src.array(), src.arrayOffset() + pos, len);
                        ((Buffer)src).position(pos + len);
                    }
                    return len;
                }
                
            @Override
	    public boolean isOpen() {
		return open;
	    }

            // This method must block as per the Channel contract
            @Override
	    public synchronized void close() throws IOException {
		out.close();
		open = false;
	    }
	};
    }

}
