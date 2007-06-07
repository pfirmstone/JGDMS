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

import com.sun.jini.thread.Executor;
import com.sun.jini.thread.GetThreadPoolAction;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.io.context.AcknowledgmentSource;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.OutboundRequest;

/**
 * A Session represents a single session of a multiplexed connection,
 * for either client-side and server-side perspective.  The particular
 * role (CLIENT or SERVER) is indicated at construction time.
 *
 * @author Sun Microsystems, Inc.
 **/
final class Session {

    static final int CLIENT = 0;
    static final int SERVER = 1;

    private static final int IDLE	= 0;
    private static final int OPEN	= 1;
    private static final int FINISHED	= 2;
    private static final int TERMINATED	= 3;
    private static final String[] stateNames = {
	"idle", "open", "finished", "terminated"
    };

    /**
     * pool of threads for executing tasks in system thread group: used for
     * I/O (reader and writer) threads and other asynchronous tasks
     **/
    private static final Executor systemThreadPool =
	(Executor) AccessController.doPrivileged(
	    new GetThreadPoolAction(false));

    /** mux logger */
    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.connection.mux");

    private final Mux mux;
    private final int sessionID;
    private final int role;

    private final OutputStream out;
    private final InputStream in;

    /** lock guarding all mutable instance state (below) */
    private final Object sessionLock = new Object();

    private boolean sessionDown = false;
    private String sessionDownMessage;
    private Throwable sessionDownCause;

    private int outState;
    private int outRation;
    private final boolean outRationInfinite;
    private boolean partialDeliveryStatus = false;

    private int inState;
    private int inRation;
    private final boolean inRationInfinite;
    private int inBufRemaining = 0;
    private final LinkedList inBufQueue = new LinkedList();
    private int inBufPos = 0;
    private boolean inEOF = false;
    private boolean inClosed = false;

    private boolean fakeOKtoWrite = false;		// REMIND
    private boolean removeLater = false;		// REMIND

    private boolean receivedAckRequired = false;
    private boolean sentAcknowledgment = false;

    private Collection ackListeners = null;
    private boolean sentAckRequired = false;
    private boolean receivedAcknowledgment = false;

    /**
     *
     */
    Session(Mux mux, int sessionID, int role) {
	this.mux = mux;
	this.sessionID = sessionID;
	this.role = role;

	out = new MuxOutputStream();
	in = new MuxInputStream();

	outState = (role == CLIENT ? IDLE : OPEN);
	outRation = mux.initialOutboundRation;
	outRationInfinite = (outRation == 0);

	inState = (role == CLIENT ? IDLE : OPEN);
	inRation = mux.initialInboundRation;
	inRationInfinite = (inRation == 0);
    }

    /**
     *
     */
    OutboundRequest getOutboundRequest() {
	assert role == CLIENT;
	return new OutboundRequest() {
	    public void populateContext(Collection context) {
		((MuxClient) mux).populateContext(context);
	    }
	    public InvocationConstraints getUnfulfilledConstraints() {
		/*
		 * NYI: We currently have no request-specific hook
		 * back to the transport implementation, so we must
		 * depend on OutboundRequest wrapping for this method.
		 */
		throw new AssertionError();
	    }
	    public OutputStream getRequestOutputStream() { return out; }
	    public InputStream getResponseInputStream() { return in; }
	    public boolean getDeliveryStatus() {
		synchronized (sessionLock) {
		    return partialDeliveryStatus;
		}
	    }
	    public void abort() { Session.this.abort(); }
	};
    }

    /**
     *
     */
    InboundRequest getInboundRequest() {
	assert role == SERVER;
	return new InboundRequest() {
	    public void checkPermissions() {
		((MuxServer) mux).checkPermissions();
	    }
	    public InvocationConstraints
		checkConstraints(InvocationConstraints constraints)
		throws UnsupportedConstraintException
	    {
		return ((MuxServer) mux).checkConstraints(constraints);
	    }
	    public void populateContext(Collection context) {
		context.add(new AcknowledgmentSource() {
		    public boolean addAcknowledgmentListener(
			AcknowledgmentSource.Listener listener)
		    {
			if (listener == null) {
			    throw new NullPointerException();
			}
			synchronized (sessionLock) {
			    if (outState < FINISHED) {
				if (ackListeners == null) {
				    ackListeners = new ArrayList(3);
				}
				ackListeners.add(listener);
				return true;
			    } else {
				return false;
			    }
			}
		    }
		});
		((MuxServer) mux).populateContext(context);
	    }
	    public InputStream getRequestInputStream() { return in; }
	    public OutputStream getResponseOutputStream() { return out; }
	    public void abort() { Session.this.abort(); }
	};
    }

    /**
     *
     */
    void abort() {
	synchronized (sessionLock) {
	    if (!sessionDown) {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST,
			"outState=" + stateNames[outState] +
			",inState=" + stateNames[inState] +
			",role=" + (role == CLIENT ? "CLIENT" : "SERVER"));
		}

		if (outState == IDLE) {
		    mux.removeSession(sessionID);
		} else if (outState < TERMINATED) {
		    if (role == SERVER && outState == FINISHED) {
			/*
			 * In this case, send Close rather than Abort, so that
			 * a client that still hasn't finished writing will not
			 * get an unnecessary failure and will be able to read
			 * the complete response as intended (still permitting
			 * server-side defensive abort() invocation).
			 */
			mux.asyncSendClose(sessionID);
		    } else {
			mux.asyncSendAbort(Mux.Abort | (role == SERVER ?
							Mux.Abort_partial : 0),
					   sessionID, null);
		    }
		    setOutState(TERMINATED);
		}

		setDown("request aborted", null);
	    }
	    /*
	     * After the application has invoked abort() on the request, we
	     * must no longer try to "fake" an OK session.
	     */
	    fakeOKtoWrite = false;

	    /*
	     * If removing this session from the connection's table
	     * was delayed in order to be able to send an
	     * Acknowledgment message, then we remove it on local
	     * abort in order to clean up resources.  Also make sure
	     * that our state is considered terminated so that no
	     * future Acknowledgment message will be sent.
	     */
	    if (removeLater) {
		if (outState < TERMINATED) {
		    setOutState(TERMINATED);
		}
		mux.removeSession(sessionID);
		removeLater = false;
	    }
	}
    }

    /**
     *
     */
    void setDown(String message, Throwable cause) {
	synchronized (sessionLock) {
	    if (!sessionDown) {
		sessionDown = true;
		sessionDownMessage = message;
		sessionDownCause = cause;
		sessionLock.notifyAll();
	    }
	}
    }

    /**
     *
     */
    void handleIncrementRation(int increment) throws ProtocolException {
	synchronized (sessionLock) {
	    if (inState == IDLE || inState == TERMINATED) {
		throw new ProtocolException("IncrementRation on " +
		    stateNames[inState] + " session: " + sessionID);
	    }
	    if (!outRationInfinite) {
		if (outRation + increment < outRation) {
		    throw new ProtocolException("ration overflow");
		}
		if (outState == OPEN) {
		    if (increment > 0) {
			if (outRation == 0) {
			    sessionLock.notifyAll();
			}
			outRation += increment;
		    }
		}
	    } // ignore message if outbound ration is infinite
	}
    }

    /**
     *
     */
    void handleAbort(boolean partial) throws ProtocolException {
	synchronized (sessionLock) {
	    if (inState == IDLE || inState == TERMINATED) {
		throw new ProtocolException("Abort on " +
		    stateNames[inState] + " session: " + sessionID);
	    }

	    setInState(TERMINATED);
	    partialDeliveryStatus = partial;

	    /*
	     * Respond with an abort of this side of the session, if it's
	     * still open.
	     */
	    /*
	     * REMIND: Technically, the client should not have to send
	     * an Abort message here if it is already in the finished
	     * state, although the spec would seem to suggest that it
	     * should do so regardless.  A particular reason that we
	     * send it here in that case, though, is that it should be
	     * a cheap way to avoid 4827402 for that case-- to ensure
	     * that no late Acknowledgment message gets sent after the
	     * session has been removed.
	     */
	    if (outState < TERMINATED) {
		mux.asyncSendAbort(Mux.Abort | (role == SERVER ?
						Mux.Abort_partial : 0),
				   sessionID, null);
		setOutState(TERMINATED);
	    }

	    setDown("request aborted by remote endpoint", null);

	    if (sentAckRequired && !receivedAcknowledgment) {
		notifyAcknowledgmentListeners(false);
	    }	// REMIND: what about other dangling acknowledgments?

	    mux.removeSession(sessionID);
	}
    }

    /**
     *
     */
    void handleClose() throws ProtocolException {
	if (role != CLIENT) {
	    throw new ProtocolException("Close sent by client");
	}

	synchronized (sessionLock) {
	    if (inState != FINISHED) {
		throw new ProtocolException("Close on " +
		    stateNames[inState] + " session: " + sessionID);
	    }
	    if (outState < FINISHED) {
		/*
		 * From a protocol perspective, we need to terminate the
		 * session at this point (because we're not finished, but
		 * we don't want to hold on to it unnecessarily).  But we
		 * also don't want the session to appear failed while the
		 * client is still writing-- instead, we want the client
		 * to be able to successfully read the complete response
		 * that was received-- so this flag is set to
		 * (temporarily) fake that the session is still in OK
		 * shape (but not send any more data for it).
		 */
		fakeOKtoWrite = true;
		mux.asyncSendAbort(Mux.Abort, sessionID, null);
		setOutState(TERMINATED);
		/*
		 * REMIND: This approach causes a premature negative
		 * acknowledgment to the server.  It seems that
		 * ideally, if receivedAckRequired is true, we should
		 * delay sending the Abort message until the response
		 * input stream is closed and an Acknowledgment has
		 * been sent-- although that would be somewhat at odds
		 * with the "timely fashion" prescription of the Close
		 * message specification.
		 */
	    }

	    setInState(TERMINATED);

	    setDown("request closed by server", null);

	    /*
	     * If we still (might) need to send an Acknowledgment,
	     * then we must delay removing this session from the
	     * connection's table now, to prevent the sessionID being
	     * reused before the Acknowledgment message is sent.
	     */
	    if (outState == TERMINATED ||
		!receivedAckRequired || sentAcknowledgment)
	    {
		mux.removeSession(sessionID);
	    } else {
		removeLater = true;
	    }
	}
    }

    /**
     *
     */
    void handleAcknowledgment() throws ProtocolException {
	if (role != SERVER) {
	    throw new ProtocolException("Acknowledgment sent by server");
	}

	synchronized (sessionLock) {
	    if (inState == IDLE || inState == TERMINATED) {
		throw new ProtocolException("Acknowledgment on " +
		    stateNames[inState] + " session: " + sessionID);
	    }
	    if (outState < FINISHED) {
		throw new ProtocolException(
		    "acknowledgment received before EOF sent");
	    }
	    if (!sentAckRequired) {
		throw new ProtocolException("acknowledgment not requested");
	    }
	    if (receivedAcknowledgment) {
		throw new ProtocolException("duplicate acknowledgment");
	    }
	    receivedAcknowledgment = true;

	    notifyAcknowledgmentListeners(true);
	}
    }

    /**
     *
     */
    void handleData(ByteBuffer data,
		    boolean eof, boolean close, boolean ackRequired)
	throws ProtocolException
    {
	assert eof || (!close && !ackRequired);

	if (ackRequired && role != CLIENT) {
	    throw new ProtocolException("Data/ackRequired sent by client");
	}

	synchronized (sessionLock) {
	    boolean notified = close;	// close always causes notification

	    if (inState != OPEN) {
		throw new ProtocolException("Data on " +
		    stateNames[inState] + " session: " + sessionID);
	    }
	    int length = data.remaining();
	    if (!inRationInfinite && length > inRation) {
		throw new ProtocolException("input ration exceeded");
	    }
	    if (!inClosed && outState < TERMINATED) {
		if (length > 0) {
		    if (inBufRemaining == 0) {
			sessionLock.notifyAll();
			notified = true;
		    }
		    inBufQueue.addLast(data);
		    inBufRemaining += length;
		    if (!inRationInfinite) {
			inRation -= length;
		    }
		}
	    }

	    if (eof) {
		inEOF = true;
		setInState(FINISHED);
		if (!notified) {
		    sessionLock.notifyAll();
		}

		if (ackRequired) {
		    receivedAckRequired = true;
		    // send acknowledgment if input stream already closed?
		}

		if (close) {
		    handleClose();
		}
		// REMIND: send Close if appropriate?
	    }
	}
    }

    /**
     *
     */
    void handleOpen() throws ProtocolException {
	assert role == SERVER;
	synchronized (sessionLock) {
	    if (inState < FINISHED || outState < TERMINATED) {
		throw new ProtocolException(
                    inState < FINISHED ?
		    ("Data/open on " +
		     stateNames[inState] + " session: " + sessionID) :
		    ("Data/open before previous session terminated"));
	    }

	    setInState(TERMINATED);
	    // REMIND: process dangling acknowledgments here?

	    setDown("old request", null);	// extraneous?
	    sessionLock.notifyAll();

	    mux.removeSession(sessionID);
	}
    }

    /**
     *
     */
    private void setOutState(int newState) {
	assert newState > outState;
	outState = newState;
    }

    /**
     *
     */
    private void setInState(int newState) {
	assert newState > inState;
	inState = newState;
    }

    private void notifyAcknowledgmentListeners(final boolean received) {
	if (ackListeners != null) {
	    systemThreadPool.execute(new Runnable() {
		public void run() {
		    Iterator iter = ackListeners.iterator();
		    while (iter.hasNext()) {
			AcknowledgmentSource.Listener listener =
			    (AcknowledgmentSource.Listener) iter.next();
			listener.acknowledgmentReceived(received);
		    }
		}
	    }, "Mux ack notifier");
	}
    }

    /**
     * Output stream returned by OutboundRequests and InboundRequests for
     * a session of a multiplexed connection.
     */
    private class MuxOutputStream extends OutputStream {

	private ByteBuffer buffer = mux.directBuffersUseful() ?
	    ByteBuffer.allocateDirect(mux.maxFragmentSize) :
	    ByteBuffer.allocate(mux.maxFragmentSize);

	MuxOutputStream() { }

	public synchronized void write(int b) throws IOException {
	    if (!buffer.hasRemaining()) {
		writeBuffer(false);
	    } else {
		synchronized (sessionLock) {	// REMIND: necessary?
		    ensureOpen();
		}
	    }
	    buffer.put((byte) b);
	}

	public synchronized void write(byte[] b, int off, int len)
	    throws IOException
	{
	    if (b == null) {
		throw new NullPointerException();
	    } else if ((off < 0) || (off > b.length) || (len < 0) ||
		       ((off + len) > b.length) || ((off + len) < 0))
	    {
		throw new IndexOutOfBoundsException();
	    } else if (len == 0) {
		synchronized (sessionLock) {
		    ensureOpen();
		}
		return;
	    }

	    while (len > 0) {
		int avail = buffer.remaining();
		if (len <= avail) {
		    synchronized (sessionLock) {
			ensureOpen();
		    }
		    buffer.put(b, off, len);
		    return;
		}

		buffer.put(b, off, avail);
		off += avail;
		len -= avail;
		writeBuffer(false);
	    }
	}

	public synchronized void flush() throws IOException {
//	    synchronized (sessionLock) {
//		ensureOpen();
//	    }
//
//	    while (buffer.hasRemaining()) {
//		writeBuffer(false);
//	    }
	}

	public synchronized void close() throws IOException {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "STACK TRACE", new Throwable("STACK TRACE"));
	    }

	    synchronized (sessionLock) {
		ensureOpen();
	    }

	    while (!writeBuffer(true)) { }
	}

	/**
	 *
	 * This method must ONLY be invoked while synchronized on
	 * this session's lock.
	 */
	private void ensureOpen() throws IOException {
	    assert Thread.holdsLock(sessionLock);

	    /*
	     * While we're faking that the session is still OK when it really
	     * isn't (see above comments), return silently from here.
	     */
	    if (fakeOKtoWrite) {
		return;
	    }

	    if (outState > OPEN) {
		if (outState == FINISHED) {
		    throw new IOException("stream closed");
		} else {
		    throw new IOException("session terminated");
		}
	    } else if (sessionDown) {
		IOException ioe = new IOException(sessionDownMessage);
		if (sessionDownCause != null) {
		    ioe.initCause(sessionDownCause);
		}
		throw ioe;
	    }
	}

	/**
	 * Writes as much of the contents of this stream's output buffer
	 * as is allowed by the current output ration.  Upon normal return,
	 * at least one byte will have been transferred from the buffer to
	 * the multiplexed connection output queue, and the buffer will have
	 * been compacted, ready to be filled at the current position.
	 *
	 * Returns true if closeIfComplete and session was marked EOF (with
	 * complete buffer written); if true, stream's output buffer should
	 * no longer be accessed (because this method will not wait for
	 * actual writing of the message).
	 */
	private boolean writeBuffer(boolean closeIfComplete)
	    throws IOException
	{
	    buffer.flip();
	    int origLimit = buffer.limit();

	    int toSend;
	    IOFuture future = null;
	    boolean eofSent = false;
	    synchronized (sessionLock) {
		while (buffer.remaining() > 0 &&
		       !outRationInfinite && outRation < 1 &&
		       !sessionDown && outState == OPEN)
		{
		    try {
			sessionLock.wait();	// REMIND: timeout?
		    } catch (InterruptedException e) {
			String message = "request I/O interrupted";
			setDown(message, e);
			IOException ioe = new IOException(message);
			ioe.initCause(e);
			throw ioe;
		    }
		}
		ensureOpen();
		assert buffer.remaining() == 0 || outRationInfinite ||
		    outRation > 0 || fakeOKtoWrite;

		/*
		 * If we're just faking that the session is OK when it really
		 * isn't, then we need to stop the writing from proceeding
		 * past this barrier-- and if a close was requested, then
		 * satisfy it right away.
		 */
		if (fakeOKtoWrite) {
		    assert role == CLIENT && inState == TERMINATED;
		    if (closeIfComplete) {
			fakeOKtoWrite = false;
		    }
		    buffer.position(origLimit);
		    buffer.compact();
		    return closeIfComplete;
		}

		boolean complete;
		if (outRationInfinite || buffer.remaining() <= outRation) {
		    toSend = buffer.remaining();
		    complete = true;
		} else {
		    toSend = outRation;
		    buffer.limit(toSend);
		    complete = false;
		}

		if (!outRationInfinite) {
		    outRation -= toSend;
		}
		partialDeliveryStatus = true;

		boolean open = outState == IDLE;
		boolean eof = closeIfComplete && complete;
		boolean close = role == SERVER && eof && inState > OPEN;
		boolean ackRequired = role == SERVER && eof &&
		    (ackListeners != null && !ackListeners.isEmpty());

		int op = Mux.Data |
		    (open ? Mux.Data_open : 0) |
		    (eof ? Mux.Data_eof : 0) |
		    (close ? Mux.Data_close : 0) |
		    (ackRequired ? Mux.Data_ackRequired : 0);

		/*
		 * If we are the server-side, send even the final Data message
		 * for this session synchronously with this method, so that the
		 * VM will not exit before it gets delivered.  Otherwise, let
		 * final Data messages (those with eof true) be sent after this
		 * method completes.
		 */
		if (!eof || role == SERVER) {
		    future = mux.futureSendData(op, sessionID, buffer);
		} else {
		    mux.asyncSendData(op, sessionID, buffer);
		}

		if (outState == IDLE) {
		    setOutState(OPEN);
		    setInState(OPEN);
		}

		if (eof) {
		    eofSent = true;
		    setOutState(close ? TERMINATED : FINISHED);
		    if (ackRequired) {
			sentAckRequired = true;
		    }
		    sessionLock.notifyAll();
		}
	    }

	    if (future != null) {
		waitForIO(future);
		buffer.limit(origLimit);		// REMIND: finally?
		buffer.compact();
	    }

	    return eofSent;
	}

	/**
	 *
	 * This method must NOT be invoked while synchronized on
	 * this session's lock.
	 */
	private void waitForIO(IOFuture future) throws IOException {
	    assert !Thread.holdsLock(sessionLock);

	    try {
		future.waitUntilDone();
	    } catch (InterruptedException e) {
		String message = "request I/O interrupted";
		setDown(message, e);
		IOException ioe = new IOException(message);
		ioe.initCause(e);
		throw ioe;
	    } catch (IOException e) {
		setDown(e.getMessage(), e.getCause());
		throw e;
	    }
	}
    }

    /**
     * Output stream returned by OutboundRequests and InboundRequests for
     * a session of a multiplexed connection.
     */
    private class MuxInputStream extends InputStream {

	MuxInputStream() { }

	public int read() throws IOException {
	    synchronized (sessionLock) {
		if (inClosed) {
		    throw new IOException("stream closed");
		}

		while (inBufRemaining == 0 &&
		       !sessionDown && inState <= OPEN && !inClosed)
		{
		    if (inState == IDLE) {
			assert outState == IDLE;
			mux.asyncSendData(Mux.Data | Mux.Data_open,
					  sessionID, null);
			setOutState(OPEN);
			setInState(OPEN);
		    }
		    if (!inRationInfinite && inRation == 0) {
			int inc = mux.initialInboundRation;
			mux.asyncSendIncrementRation(sessionID, inc);
			inRation += inc;
		    }
		    try {
			sessionLock.wait();	// REMIND: timeout?
		    } catch (InterruptedException e) {
			String message = "request I/O interrupted";
			setDown(message, e);
			IOException ioe = new IOException(message);
			ioe.initCause(e);
			throw ioe;
		    }
		}

		if (inClosed) {
		    throw new IOException("stream closed");
		}

		if (inBufRemaining == 0) {
		    if (inEOF) {
			return -1;
		    } else {
			if (inState == TERMINATED) {
			    throw new IOException(
				"request aborted by remote endpoint");
			}
			assert sessionDown;
			IOException ioe = new IOException(sessionDownMessage);
			if (sessionDownCause != null) {
			    ioe.initCause(sessionDownCause);
			}
			throw ioe;
		    }
		}

		assert inBufQueue.size() > 0;
		int result = -1;
		while (result == -1) {
		    ByteBuffer buf = (ByteBuffer) inBufQueue.getFirst();
		    if (inBufPos < buf.limit()) {
			result = (buf.get() & 0xFF);
			inBufPos++;
			inBufRemaining--;
		    }
		    if (inBufPos == buf.limit()) {
			inBufQueue.removeFirst();
			inBufPos = 0;
		    }
		}

		if (!inRationInfinite) {
		    checkInboundRation();
		}

		return result;
	    }
	}

	public int read(byte b[], int off, int len) throws IOException {
	    if (b == null) {
		throw new NullPointerException();
	    } else if ((off < 0) || (off > b.length) || (len < 0) ||
		       ((off + len) > b.length) || ((off + len) < 0))
	    {
		throw new IndexOutOfBoundsException();
	    }

	    synchronized (sessionLock) {
		if (inClosed) {
		    throw new IOException("stream closed");
		} else if (len == 0) {
		    /*
		     * REMIND: What if
		     *     - stream is at EOF?
		     *     - session was aborted?
		     */
		    return 0;
		}

		while (inBufRemaining == 0 &&
		       !sessionDown && inState <= OPEN && !inClosed)
		{
		    if (inState == IDLE) {
			assert outState == IDLE;
			mux.asyncSendData(Mux.Data | Mux.Data_open,
					  sessionID, null);
			setOutState(OPEN);
			setInState(OPEN);
		    }
		    if (!inRationInfinite && inRation == 0) {
			int inc = mux.initialInboundRation;
			mux.asyncSendIncrementRation(sessionID, inc);
			inRation += inc;
		    }
		    try {
			sessionLock.wait();	// REMIND: timeout?
		    } catch (InterruptedException e) {
			String message = "request I/O interrupted";
			setDown(message, e);
			IOException ioe = new IOException(message);
			ioe.initCause(e);
			throw ioe;
		    }
		}

		if (inClosed) {
		    throw new IOException("stream closed");
		}

		if (inBufRemaining == 0) {
		    if (inEOF) {
			return -1;
		    } else {
			if (inState == TERMINATED) {
			    throw new IOException(
				"request aborted by remote endpoint");
			}
			assert sessionDown;
			IOException ioe = new IOException(sessionDownMessage);
			if (sessionDownCause != null) {
			    ioe.initCause(sessionDownCause);
			}
			throw ioe;
		    }
		}

		assert inBufQueue.size() > 0;
		int remaining = len;
		while (remaining > 0 && inBufRemaining > 0) {
		    ByteBuffer buf = (ByteBuffer) inBufQueue.getFirst();
		    if (inBufPos < buf.limit()) {
			int toCopy = Math.min(buf.limit() - inBufPos,
					      remaining);
			buf.get(b, off, toCopy);
			inBufPos += toCopy;
			inBufRemaining -= toCopy;
			off += toCopy;
			remaining -= toCopy;
		    }
		    if (inBufPos == buf.limit()) {
			inBufQueue.removeFirst();
			inBufPos = 0;
		    }
		}

		if (!inRationInfinite) {
		    checkInboundRation();
		}

		return len - remaining;
	    }
	}

	/**
	 * Sends ration increment, if read drained buffers below
	 * a certain mark.
	 *
	 * This method must NOT be invoked if the inbound ration in
	 * infinite, and it must ONLY be invoked while synchronized on
	 * this session's lock.
	 *
	 * REMIND: The implementation of this action will be a
	 * significant area for performance tuning.
	 */
	private void checkInboundRation() {
	    assert Thread.holdsLock(sessionLock);
	    assert !inRationInfinite;

	    if (inState >= FINISHED) {
		return;
	    }
	    int mark = mux.initialInboundRation / 2;
	    int used = inBufRemaining + inRation;
	    if (used <= mark) {
		int inc = mux.initialInboundRation - used;
		mux.asyncSendIncrementRation(sessionID, inc);
		inRation += inc;
	    }
	}

	public int available() throws IOException {
	    synchronized (sessionLock) {
		if (inClosed) {
		    throw new IOException("stream closed");
		}
		/*
		 * REMIND: What if
		 *     - stream is at EOF?
		 *     - session was aborted?
		 */
		return inBufRemaining;
	    }
	}

	public void close() {
	    synchronized (sessionLock) {
		if (inClosed) {
		    return;
		}
		
		inClosed = true;
		inBufQueue.clear();		// allow GC of unread data

		if (role == CLIENT && !sentAcknowledgment &&
		    receivedAckRequired && outState < TERMINATED)
		{
		    mux.asyncSendAcknowledgment(sessionID);
		    sentAcknowledgment = true;
		    /*
		     * If removing this session from the connection's
		     * table was delayed in order to be able to send
		     * an Acknowledgment message, then take care of
		     * removing it now.
		     */
		    if (removeLater) {
			setOutState(TERMINATED);
			mux.removeSession(sessionID);
			removeLater = false;
		    }
		}

		sessionLock.notifyAll();
	    }
	}
    }
}
