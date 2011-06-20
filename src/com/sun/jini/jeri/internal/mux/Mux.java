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

import com.sun.jini.jeri.internal.runtime.HexDumpEncoder;
import com.sun.jini.thread.Executor;
import com.sun.jini.thread.GetThreadPoolAction;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.security.AccessController;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mux is the abstract superclass of both client-side and server-side
 * multiplexed connections.
 *
 * @author Sun Microsystems, Inc.
 **/
abstract class Mux {

    static final int CLIENT = 0;
    static final int SERVER = 1;

    static final int MAX_SESSION_ID = 0x7F;
    public static final int MAX_REQUESTS = MAX_SESSION_ID + 1;

    static final int NoOperation	= 0x00;	// 00000000
    static final int Shutdown		= 0x02; // 00000010
    static final int Ping		= 0x04; // 00000100
    static final int PingAck		= 0x06; // 00000110
    static final int Error		= 0x08; // 00001000
    static final int IncrementRation	= 0x10; // 0001***0
    static final int Abort		= 0x20; // 001000*0
    static final int Close		= 0x30; // 00110000
    static final int Acknowledgment	= 0x40; // 00100000
    static final int Data		= 0x80; // 100****0

    static final int IncrementRation_shift	= 0x0E;
    static final int Abort_partial		= 0x02;
    static final int Data_open			= 0x10;
    static final int Data_close			= 0x08;
    static final int Data_eof			= 0x04;
    static final int Data_ackRequired		= 0x02;

    static final int ClientConnectionHeader_negotiate	= 0x01;

    private static final byte[] magic = {
	(byte) 'J', (byte) 'm', (byte) 'u', (byte) 'x'	// 0x4A6D7578
    };

    private static final int VERSION = 0x01;

    /**
     * pool of threads for executing tasks in system thread group:
     * used for shutting down sessions when a connection goes down
     */
    private static final Executor systemThreadPool =
	(Executor) AccessController.doPrivileged(
	    new GetThreadPoolAction(false));

    /** session shutdown tasks to be executed asynchronously */
    private static final LinkedList sessionShutdownQueue = new LinkedList();

    private static class SessionShutdownTask implements Runnable {
	private final Session[] sessions;
	private final String message;
	private final Throwable cause;

	SessionShutdownTask(Session[] sessions,
			    String message,
			    Throwable cause)
	{
	    this.sessions = sessions;
	    this.message = message;
	    this.cause = cause;
	}

	public void run() {
	    for (int i = 0; i < sessions.length; i++) {
		sessions[i].setDown(message, cause);
	    }
	}
    }

    /** mux logger */
    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.connection.mux");

    final int role;
    final int initialInboundRation;
    final int maxFragmentSize;

    private final ConnectionIO connectionIO;
    private final boolean directBuffersUseful;

    /** lock guarding all mutable instance state (below) */
    final Object muxLock = new Object();

    int initialOutboundRation;		// set from remote connection header
    private boolean clientConnectionReady = false; // server header received
    boolean serverConnectionReady = false;	   // server header sent

    boolean muxDown = false;
    String muxDownMessage;
    Throwable muxDownCause;

    final BitSet busySessions = new BitSet();
    final Map sessions = new HashMap(5);

    private int expectedPingCookie = -1;
    private long startTimeout = 15000; // milliseconds

    /**
     * Constructs a new Mux instance for a connection accessible through
     * standard (blocking) I/O streams.
     */
    Mux(OutputStream out, InputStream in,
	int role, int initialInboundRation, int maxFragmentSize)
	throws IOException
    {
	this.role = role;
	if ((initialInboundRation & ~0x00FFFF00) != 0) {
	    throw new IllegalArgumentException(
		"illegal initial inbound ration: " +
		toHexString(initialInboundRation));
	}
	this.initialInboundRation = initialInboundRation;
	this.maxFragmentSize = maxFragmentSize;

	this.connectionIO = new StreamConnectionIO(this, out, in);
	directBuffersUseful = false;
    }

    Mux(SocketChannel channel,
	int role, int initialInboundRation, int maxFragmentSize)
	throws IOException
    {
	this.role = role;
	if ((initialInboundRation & ~0x00FFFF00) != 0) {
	    throw new IllegalArgumentException(
		"illegal initial inbound ration: " +
		toHexString(initialInboundRation));
	}
	this.initialInboundRation = initialInboundRation;
	this.maxFragmentSize = maxFragmentSize;

	this.connectionIO = new SocketChannelConnectionIO(this, channel);
	directBuffersUseful = true;
    }

    /**
     * Time in milliseconds for client-side connections to wait for the server
     * to acknowledge an opening handshake. The default value is 15000
     * milliseconds (15 seconds).
     * 
     * <p>
     * This method is not thread-safe. It is expected to be called immediately
     * after a constructor.
     * 
     * @param timeout
     *            positive value in milliseconds
     */
    public void setStartTimeout(long timeout) {
	if (timeout <= 0)
	    throw new IllegalArgumentException("start timeout must be a positive number of milliseconds");
	this.startTimeout  = timeout;
    }

    /**
     * Starts I/O processing.
     *
     * This method should be invoked only after this instance has
     * been completely initialized, so that subclasses will not
     * see uninitialized state.
     */
    public void start() throws IOException {
	if (role == CLIENT) {
	    readState = READ_SERVER_CONNECTION_HEADER;
	} else {
	    assert role == SERVER;
	    readState = READ_CLIENT_CONNECTION_HEADER;
	}

	try {
	    connectionIO.start();
	} catch (IOException e) {
	    setDown("I/O error starting connection", e);
	    throw e;
	}

	if (role == CLIENT) {
	    asyncSendClientConnectionHeader();
	    synchronized (muxLock) {
		long now = System.currentTimeMillis();
		long endTime = now + this.startTimeout;
		while (!muxDown && !clientConnectionReady) {
		    if (now >= endTime) {
			setDown("timeout waiting for server to respond to handshake", null);
		    } else {
                        try {
                                muxLock.wait(endTime - now);
                                now = System.currentTimeMillis();
                        } catch (InterruptedException e) {
                            setDown("interrupt waiting for connection header", e);
                        }
                    }
		}
		if (muxDown) {
		    IOException ioe = new IOException(muxDownMessage);
		    ioe.initCause(muxDownCause);
		    throw ioe;
		}
	    }
	}
    }

    /**
     * Handles indication that this multiplexed connection has
     * gone down, either through normal operation or failure.
     *
     * This method should be overridden by subclasses that want to
     * implement custom behavior when this connection has gone down.
     */
    protected void handleDown() {
    }

    /**
     * This method is invoked internally and is intended to be
     * overridden by subclasses.
     */
    void handleOpen(int sessionID) throws ProtocolException {
	throw new ProtocolException(
	    "remote endpoint attempted to open session");
    }

    /**
     *
     * This method is intended to be invoked by subclasses only.
     *
     * This method must ONLY be invoked while synchronized on muxLock
     * and while muxDown is false.
     */
    final void addSession(int sessionID, Session session) {
	assert Thread.holdsLock(muxLock);
	assert !muxDown;
	assert !busySessions.get(sessionID);
	assert sessions.get(new Integer(sessionID)) == null;

	busySessions.set(sessionID);
	sessions.put(new Integer(sessionID), session);
    }

    /**
     *
     * This method is intended to be invoked by this class and
     * subclasses only.
     *
     * This method MAY be invoked while synchronized on muxLock.
     */
    final void setDown(final String message, final Throwable cause) {
	synchronized (muxLock) {
	    if (muxDown) {
		return;
	    }
	    muxDown = true;
	    muxDownMessage = message;
	    muxDownCause = cause;
	    muxLock.notifyAll();
	}

	/*
	 * The following should be safe because we just left the
	 * synchonized block, and after setting the muxDown latch
	 * therein, no other thread should ever touch the "sessions"
	 * data structure.
	 *
	 * Sessions are shut down asynchronously in a separate thread
	 * to avoid deadlock, in case our caller holds muxLock,
	 * because individual session locks must never be acquired
	 * while holding muxLock.
	 */
	boolean needWorker = false;
	synchronized (sessionShutdownQueue) {
	    if (!sessions.values().isEmpty()) {
		sessionShutdownQueue.add(new SessionShutdownTask(
		    (Session[]) sessions.values().toArray(
			new Session[sessions.values().size()]),
		    message, cause));
		needWorker = true;
	    } else {
		needWorker = !sessionShutdownQueue.isEmpty();
	    }
	}
	if (needWorker) {
	    try {
		systemThreadPool.execute(new Runnable() {
		    public void run() {
			while (true) {
			    Runnable task;
			    synchronized (sessionShutdownQueue) {
				if (sessionShutdownQueue.isEmpty()) {
				    break;
				}
				task = (Runnable)
				    sessionShutdownQueue.removeFirst();
			    }
			    task.run();
			}
		    }
		}, "mux session shutdown");
	    } catch (OutOfMemoryError e) {	// assume out of threads
		try {
		    logger.log(Level.WARNING,
			"could not create thread for session shutdown", e);
		} catch (Throwable t) {
		}
		// absorb exception to proceed with connection shutdown;
		// session shutdown task will remain on queue for later
	    }
	}

	handleDown();
    }

    /**
     * Removes the identified session from the session table.
     *
     * This method is intended to be invoked by the associated Session
     * object only.
     */
    final void removeSession(int sessionID) {
	synchronized (muxLock) {
	    if (muxDown) {
		return;
	    }
	    assert busySessions.get(sessionID);
	    busySessions.clear(sessionID);
	    sessions.remove(new Integer(sessionID));
	}
    }

    /**
     * Returns true if it would be useful to pass direct buffers to
     * this instance's *Send* methods (because the underlying I/O
     * implementation will pass such buffers directly to channel write
     * operations); returns false otherwise.
     */
    final boolean directBuffersUseful() {
	return directBuffersUseful;
    }

    /**
     * Sends the ClientConnectionHeader message for this connection.
     */
    final void asyncSendClientConnectionHeader() {
	assert role == CLIENT;

	ByteBuffer header = ByteBuffer.allocate(8);
	header.put(magic)
	      .put((byte) VERSION)
	      .putShort((short) (initialInboundRation >> 8))
	      .put((byte) 0)
	      .flip();
	connectionIO.asyncSend(header);
    }

    /**
     * Sends the ServerConnectionHeader message for this connection.
     */
    final void asyncSendServerConnectionHeader() {
	assert role == SERVER;

	ByteBuffer header = ByteBuffer.allocate(8);
	header.put(magic)
	      .put((byte) VERSION)
	      .putShort((short) (initialInboundRation >> 8))
	      .put((byte) 0)
	      .flip();
	connectionIO.asyncSend(header);
    }

    /**
     * Sends a NoOperation message with the contents of the supplied buffer
     * as the data.
     *
     * The "length" of the NoOperation message will be the number of bytes
     * remaining in the buffer, and the data sent will be the contents
     * of the buffer between its current position and its limit.  Or if
     * the buffer argument is null, "length" will simply be zero...
     * REMIND: split into two methods instead?
     *
     * The actual writing to the underlying connection, including access to
     * the buffer's content and other state, is asynchronous with the
     * invocation of this method; therefore, the supplied buffer must not
     * be mutated even after this method has returned.
     */
    final void asyncSendNoOperation(ByteBuffer buffer) {
	ByteBuffer header = ByteBuffer.allocate(4);
	header.put((byte) NoOperation)
	      .put((byte) 0);

	if (buffer != null) {
	    assert buffer.remaining() <= 0xFFFF;
	    header.putShort((short) buffer.remaining())
		  .flip();
	    connectionIO.asyncSend(header, buffer);
	} else {
	    header.putShort((short) 0)
		  .flip();
	    connectionIO.asyncSend(header);
	}
    }

    /**
     * Sends a Shutdown message with the UTF-8 encoding of the supplied
     * message as the data.  If message is null, then zero bytes of data
     * will be sent with the message header.
     */
    final void asyncSendShutdown(String message) {
	ByteBuffer data = (message != null ?
			   getUTF8BufferFromString(message) : null);

	ByteBuffer header = ByteBuffer.allocate(4);
	header.put((byte) Shutdown)
	      .put((byte) 0);

	if (data != null) {
	    assert data.remaining() <= 0xFFFF;
	    header.putShort((short) data.remaining())
		  .flip();
	    connectionIO.asyncSend(header, data);
	} else {
	    header.putShort((short) 0)
		  .flip();
	    connectionIO.asyncSend(header);
	}
    }

    /**
     * Sends a Ping message with the specified "cookie".
     */
    final void asyncSendPing(int cookie) {
	assert cookie >= 0 && cookie <= 0xFFFF;

	ByteBuffer header = ByteBuffer.allocate(4);
	header.put((byte) Ping)
              .put((byte) 0)
	      .putShort((short) cookie)
	      .flip();
	connectionIO.asyncSend(header);
    }

    /**
     * Sends a PingAck message with the specified "cookie".
     */
    final void asyncSendPingAck(int cookie) {
	assert cookie >= 0 && cookie <= 0xFFFF;

	ByteBuffer header = ByteBuffer.allocate(4);
	header.put((byte) PingAck)
	      .put((byte) 0)
	      .putShort((short) cookie)
	      .flip();
	connectionIO.asyncSend(header);
    }

    /**
     * Sends an Error message with the UTF-8 encoding of the supplied
     * message as the data.  If message is null, then zero bytes of data
     * will be sent with the message header.
     */
    final void asyncSendError(String message) {
	ByteBuffer data = (message != null ?
			   getUTF8BufferFromString(message) : null);

	ByteBuffer header = ByteBuffer.allocate(4);
	header.put((byte) Error)
	      .put((byte) 0);

	if (data != null) {
	    assert data.remaining() <= 0xFFFF;
	    header.putShort((short) data.remaining())
		  .flip();
	    connectionIO.asyncSend(header, data);
	} else {
	    header.putShort((short) 0)
		  .flip();
	    connectionIO.asyncSend(header);
	}
    }

    /**
     * Sends an Error message with the UTF-8 encoding of the supplied
     * message as the data.  If message is null, then zero bytes of data
     * will be sent with the message header.
     */
    final IOFuture futureSendError(String message) {
	ByteBuffer data = getUTF8BufferFromString(message);

	ByteBuffer header = ByteBuffer.allocate(4);
	header.put((byte) Error)
	      .put((byte) 0);

	assert data.remaining() <= 0xFFFF;
	header.putShort((short) data.remaining())
	      .flip();
	return connectionIO.futureSend(header, data);
    }

    /**
     * Sends an IncrementRation message for the specified "sessionID" and
     * the specified "increment".
     */
    final void asyncSendIncrementRation(/*****int op, *****/int sessionID,
					int increment)
    {
	final int op = IncrementRation;
//	assert (op & 0xF1) == IncrementRation;	// validate operation code
//	assert (op & 0xE0) == 0;		// NYI: support use of shift
	assert sessionID >= 0 && sessionID <= MAX_SESSION_ID;
	assert increment >= 0 && increment <= 0xFFFF;

	ByteBuffer header = ByteBuffer.allocate(4);
	header.put((byte) op)
	      .put((byte) sessionID)
	      .putShort((short) increment)
	      .flip();
	connectionIO.asyncSend(header);
    }

    /**
     * Sends an Abort message for the specified "sessionID" with the contents
     * of the specified buffer as the data.
     *
     * The "length" of the Abort message will be the number of bytes
     * remaining in the buffer, and the data sent will be the contents
     * of the buffer between its current position and its limit.  Or if
     * the buffer argument is null, "length" will simply be zero...
     * REMIND: split into two methods instead?
     *
     * For efficiency, the caller is responsible for pre-computing the first
     * byte of the message, including any control flags if appropriate.
     */
    final void asyncSendAbort(int op, int sessionID, ByteBuffer data) {
	assert (op & 0xFD) == Abort;		// validate operation code
	assert sessionID >= 0 && sessionID <= MAX_SESSION_ID;

	ByteBuffer header = ByteBuffer.allocate(4);
	header.put((byte) op)
	      .put((byte) sessionID);

	if (data != null) {
	    assert data.remaining() <= 0xFFFF;
	    header.putShort((short) data.remaining())
		  .flip();
	    connectionIO.asyncSend(header, data);
	} else {
	    header.putShort((short) 0)
		  .flip();
	    connectionIO.asyncSend(header);
	}
    }

    /**
     * Sends a Close message for the specified "sessionID".
     */
    final void asyncSendClose(int sessionID) {
	assert sessionID >= 0 && sessionID <= MAX_SESSION_ID;

	ByteBuffer header = ByteBuffer.allocate(4);
	header.put((byte) Close)
	      .put((byte) sessionID)
	      .putShort((short) 0)
	      .flip();
	connectionIO.asyncSend(header);
    }

    /**
     * Sends an Acknowledgment message for the specified "sessionID".
     */
    final void asyncSendAcknowledgment(int sessionID) {
	assert sessionID >= 0 && sessionID <= MAX_SESSION_ID;

	ByteBuffer header = ByteBuffer.allocate(4);
	header.put((byte) Acknowledgment)
	      .put((byte) sessionID)
	      .putShort((short) 0)
	      .flip();
	connectionIO.asyncSend(header);
    }

    /**
     * Sends a Data message for the specified "sessionID" with the contents
     * of the supplied buffer as the data.
     *
     * The "length" of the Data message will be the number of bytes
     * remaining in the buffer, and the data sent will be the contents
     * of the buffer between its current position and its limit.  Or if
     * the buffer argument is null, "length" will simply be zero...
     * REMIND: split into two methods instead?
     *
     * For efficiency, the caller is responsible for pre-computing the first
     * byte of the Data message, including any control flags if appropriate.
     *
     * The actual writing to the underlying connection, including access to
     * the buffer's content and other state, is asynchronous with the
     * invocation of this method; therefore, the supplied buffer must not
     * be mutated even after this method has returned.
     */
    final void asyncSendData(int op, int sessionID, ByteBuffer data) {
	assert (op & 0xE1) == Data;	// validate operation code
	assert (op & Data_eof) != 0 ||	// close and ackRequired require eof
	    (op & Data_close & Data_ackRequired) == 0;
	assert sessionID >= 0 && sessionID <= MAX_SESSION_ID;

	ByteBuffer header = ByteBuffer.allocate(4);
	header.put((byte) op)
	      .put((byte) sessionID);

	if (data != null) {
	    assert data.remaining() <= 0xFFFF;
	    header.putShort((short) data.remaining())
		  .flip();
	    connectionIO.asyncSend(header, data);
	} else {
	    header.putShort((short) 0)
		  .flip();
	    connectionIO.asyncSend(header);
	}
    }

    /**
     * Sends a Data message for the specified sessionID with the contents
     * of the supplied buffer as the data.
     *
     * The "length" of the Data message will be the number of bytes
     * remaining in the buffer, and the data sent will be the contents
     * of the buffer between its current position and its limit.
     *
     * For efficiency, the caller is responsible for pre-computing the first
     * byte of the Data message, including any control flags if appropriate.
     *
     * The actual writing to the underlying connection, including access to
     * the buffer's content and other state, is asynchronous with the
     * invocation of this method; therefore, the supplied buffer must not
     * be mutated even after this method has returned, until it is guaranteed
     * that use of the buffer has completed.
     *
     * The returned IOFuture object can be used to wait until the write has
     * definitely completed (or will definitely not complete due to some
     * failure).  After the write has completed, the buffer's position will
     * have been incremented to its limit (which will not have changed).
     */
    final IOFuture futureSendData(int op, int sessionID, ByteBuffer data) {
	assert (op & 0xE1) == Data;	// verify operation code
	assert (op & Data_eof) != 0 ||	// close and ackRequired require eof
	    (op & Data_close & Data_ackRequired) == 0;
	assert sessionID >= 0 && sessionID <= MAX_SESSION_ID;
	assert data.remaining() <= 0xFFFF;

	ByteBuffer header = ByteBuffer.allocate(4);
	header.put((byte) op)
	      .put((byte) sessionID)
	      .putShort((short) data.remaining())
	      .flip();
	return connectionIO.futureSend(header, data);
    }

    /*
     * read states
     */
    private static final int READ_CLIENT_CONNECTION_HEADER	= 0;
    private static final int READ_SERVER_CONNECTION_HEADER	= 1;
    private static final int READ_MESSAGE_HEADER		= 2;
    private static final int READ_MESSAGE_BODY			= 3;

    /*
     * current read state lock and variables
     */
    private final Object readStateLock = new Object();
    private int readState;
    private int currentOp;
    private int currentSessionID;
    private int currentLengthRemaining;
    private ByteBuffer currentDataBuffer = null;

    void processIncomingData(ByteBuffer buffer) throws ProtocolException {
	buffer.flip();	// process data that has been read into buffer
	assert buffer.hasRemaining();

	synchronized (readStateLock) {
	  stateLoop:
	    do {
		switch (readState) {
		  case READ_CLIENT_CONNECTION_HEADER:
		    if (!readClientConnectionHeader(buffer)) {
			break stateLoop;
		    }
		    break;

		  case READ_SERVER_CONNECTION_HEADER:
		    if (!readServerConnectionHeader(buffer)) {
			break stateLoop;
		    }
		    break;

		  case READ_MESSAGE_HEADER:
		    if (!readMessageHeader(buffer)) {
			break stateLoop;
		    }
		    break;

		  case READ_MESSAGE_BODY:
		    if (!readMessageBody(buffer)) {
			break stateLoop;
		    }
		    break;

		  default:
		    throw new AssertionError();
		}
	    } while (buffer.hasRemaining());
	}

	buffer.compact();
    }

    private boolean readClientConnectionHeader(ByteBuffer buffer)
	throws ProtocolException
    {
	assert role == SERVER;

	validatePartialMagicNumber(buffer);
	if (buffer.remaining() < 8) {
	    return false;		// wait for complete header to arrive
	}
	int headerPosition = buffer.position();
	buffer.position(headerPosition + 4);	// skip header already checked
	int version = (buffer.get() & 0xFF);
	int ration = (buffer.getShort() & 0xFFFF) << 8;
	int flags = (buffer.get() & 0xFF);
	boolean negotiate = (flags & ClientConnectionHeader_negotiate) != 0;

	synchronized (muxLock) {
	    initialOutboundRation = ration;
	    asyncSendServerConnectionHeader();

	    if (version == 0) {
		throw new ProtocolException(
		    "bad protocol version: " + version);
	    }
	    if (version > VERSION) {
		if (!negotiate) {
		    setDown("unsupported protocol version: " + version, null);
		    throw new ProtocolException(
			"unsupported protocol version: " + version);
		}
	    }

	    serverConnectionReady = true;
	}

	readState = READ_MESSAGE_HEADER;
	return true;
    }

    private boolean readServerConnectionHeader(ByteBuffer buffer)
	throws ProtocolException
    {
	assert role == CLIENT;

	validatePartialMagicNumber(buffer);

	if (buffer.remaining() < 8) {
	    return false;
	}
	int headerPosition = buffer.position();
	buffer.position(headerPosition + 4);	// skip header already checked
	int version = (buffer.get() & 0xFF);
	int ration = (buffer.getShort() & 0xFFFF) << 8;
	int flags = (buffer.get() & 0xFF);

	synchronized (muxLock) {
	    initialOutboundRation = ration;

	    if (version == 0) {
		throw new ProtocolException(
		    "bad protocol version: " + version);
	    }
	    if (version > VERSION) {
		throw new ProtocolException(
		    "unexpected protocol version: " + version);
	    }

	    clientConnectionReady = true;
	    muxLock.notifyAll();
	}

	readState = READ_MESSAGE_HEADER;
	return true;
    }

    private void validatePartialMagicNumber(ByteBuffer buffer)
	throws ProtocolException
    {
	if (buffer.remaining() > 0) {
	    byte[] temp = new byte[Math.min(buffer.remaining(), magic.length)];
	    buffer.mark();
	    buffer.get(temp);
	    buffer.reset();
	    for (int i = 0; i < temp.length; i++) {
		if (temp[i] != magic[i]) {
		    setDown((role == CLIENT ? "server" : "client") +
			" sent bad magic number: " + toHexString(temp), null);
		    throw new ProtocolException("bad magic number: " +
						toHexString(temp));
		}
	    }
	}
    }

    private boolean readMessageHeader(ByteBuffer buffer)
	throws ProtocolException
    {
	if (buffer.remaining() < 4) {
	    return false;		// wait for complete header to arrive
	}
	int headerPosition = buffer.position();
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		       "message header: " +
		       toHexString(buffer.getInt(headerPosition)));
	}

	int op = (buffer.get() & 0xFF);
	if ((op & 0xE1) == Data) {
	    int sessionID = (buffer.get() & 0xFF);
	    if (sessionID > MAX_SESSION_ID) {
		throw new ProtocolException("bad message header: " +
		    toHexString(buffer.getInt(headerPosition)));
	    }
	    currentOp = op;
	    currentSessionID = sessionID;
	    currentLengthRemaining = (buffer.getShort() & 0xFFFF);
	    if (currentLengthRemaining > 0) {
		currentDataBuffer =
		    ByteBuffer.allocate(currentLengthRemaining);
		readState = READ_MESSAGE_BODY;
	    } else {
		dispatchCurrentMessage();
	    }
	    return true;

	} else if ((op & 0xF1) == IncrementRation) {
	    int sessionID = (buffer.get() & 0xFF);
	    if (sessionID > MAX_SESSION_ID) {
		throw new ProtocolException("bad message header: " +
		    toHexString(buffer.getInt(headerPosition)));
	    }
	    int increment = (buffer.getShort() & 0xFFFF);
	    int shift = op & IncrementRation_shift;
	    increment <<= shift;
	    handleIncrementRation(sessionID, increment);
	    return true;

	} else if ((op & 0xFD) == Abort) {
	    int sessionID = (buffer.get() & 0xFF);
	    if (sessionID > MAX_SESSION_ID) {
		throw new ProtocolException("bad message header: " +
		    toHexString(buffer.getInt(headerPosition)));
	    }
	    currentOp = op;
	    currentSessionID = sessionID;
	    currentLengthRemaining = (buffer.getShort() & 0xFFFF);
	    if (currentLengthRemaining > 0) {
		currentDataBuffer =
		    ByteBuffer.allocate(currentLengthRemaining);
		readState = READ_MESSAGE_BODY;
	    } else {
		dispatchCurrentMessage();
	    }
	    return true;

	}
	switch (op) {
	  case NoOperation: {
	    if (buffer.get() != 0) {	// ignore sign extension
		throw new ProtocolException("bad message header: " +
		    toHexString(buffer.getInt(headerPosition)));
	    }
	    currentOp = op;
	    currentLengthRemaining = (buffer.getShort() & 0xFFFF);
	    currentDataBuffer = null;	// ignore data for NoOperation
	    if (currentLengthRemaining > 0) {
		readState = READ_MESSAGE_BODY;
	    } else {
		dispatchCurrentMessage();
	    }
	    return true;
	  }

	  case Shutdown: {
	    if (buffer.get() != 0) {	// ignore sign extension
		throw new ProtocolException("bad message header: " +
		    toHexString(buffer.getInt(headerPosition)));
	    }
	    currentOp = op;
	    currentLengthRemaining = (buffer.getShort() & 0xFFFF);
	    if (currentLengthRemaining > 0) {
		currentDataBuffer =
		    ByteBuffer.allocate(currentLengthRemaining);
		readState = READ_MESSAGE_BODY;
	    } else {
		dispatchCurrentMessage();
	    }
	    return true;
	  }

	  case Ping: {
	    if (buffer.get() != 0) {	// ignore sign extension
		throw new ProtocolException("bad message header: " +
		    toHexString(buffer.getInt(headerPosition)));
	    }
	    int cookie = (buffer.getShort() & 0xFFFF);
	    handlePing(cookie);
	    return true;
	  }

	  case PingAck: {
	    if (buffer.get() != 0) {	// ignore sign extension
		throw new ProtocolException("bad message header: " +
		    toHexString(buffer.getInt(headerPosition)));
	    }
	    int cookie = (buffer.getShort() & 0xFFFF);
	    handlePingAck(cookie);
	    return true;
	  }

	  case Error: {
	    if (buffer.get() != 0) {	// ignore sign extension
		throw new ProtocolException("bad message header: " +
		    toHexString(buffer.getInt(headerPosition)));
	    }
	    currentOp = op;
	    currentLengthRemaining = (buffer.getShort() & 0xFFFF);
	    if (currentLengthRemaining > 0) {
		currentDataBuffer =
		    ByteBuffer.allocate(currentLengthRemaining);
		readState = READ_MESSAGE_BODY;
	    } else {
		dispatchCurrentMessage();
	    }
	    return true;
	  }

	  case Close: {
	    int sessionID = (buffer.get() & 0xFF);
	    if (sessionID > MAX_SESSION_ID ||
		buffer.getShort() != 0)		// ignore sign extension
	    {
		throw new ProtocolException("bad message header: " +
		    toHexString(buffer.getInt(headerPosition)));
	    }
	    handleClose(sessionID);
	    return true;
	  }

	  case Acknowledgment: {
	    int sessionID = (buffer.get() & 0xFF);
	    if (sessionID > MAX_SESSION_ID ||
		buffer.getShort() != 0)		// ignore sign extension
	    {
		throw new ProtocolException("bad message header: " +
		    toHexString(buffer.getInt(headerPosition)));
	    }
	    handleAcknowledgment(sessionID);
	    return true;
	  }

	  default:
	    throw new ProtocolException("bad message header: " +
		toHexString(buffer.getInt(headerPosition)));
	}
    }

    private boolean readMessageBody(ByteBuffer buffer)
	throws ProtocolException
    {
	assert currentLengthRemaining > 0;
	assert currentDataBuffer == null ||
	    currentDataBuffer.remaining() == currentLengthRemaining;

	if (buffer.remaining() > currentLengthRemaining) {
	    int origLimit = buffer.limit();
	    buffer.limit(buffer.position() + currentLengthRemaining);
	    if (currentDataBuffer != null) {
		currentDataBuffer.put(buffer);
	    } else {
		buffer.position(buffer.position() + currentLengthRemaining);
	    }
	    currentLengthRemaining = 0;
	    buffer.limit(origLimit);
	} else {
	    currentLengthRemaining -= buffer.remaining();
	    if (currentDataBuffer != null) {
		currentDataBuffer.put(buffer);
	    } else {
		buffer.position(buffer.limit());
	    }
	}

	if (currentLengthRemaining > 0) {
	    return false;
	} else {
	    currentDataBuffer.flip();
	    dispatchCurrentMessage();
	    currentDataBuffer = null;		// don't let this linger
	    readState = READ_MESSAGE_HEADER;
	    return true;
	}
    }

    private void dispatchCurrentMessage() throws ProtocolException {
	assert currentDataBuffer == null || currentDataBuffer.hasRemaining();

	int op = currentOp;
	if ((op & 0xE1) == Data) {
	    boolean open	= (op & Data_open) != 0;
	    boolean close	= (op & Data_close) != 0;
	    boolean eof		= (op & Data_eof) != 0;
	    boolean ackRequired	= (op & Data_ackRequired) != 0;
	    handleData(currentSessionID, open, close, eof, ackRequired,
		       (currentDataBuffer != null ?
			currentDataBuffer : ByteBuffer.allocate(0)));
	    return;

	} else if ((op & 0xFD) == Abort) {
	    boolean partial = (op & Abort_partial) != 0;
	    handleAbort(currentSessionID, partial,
			(currentDataBuffer != null ?
			 getStringFromUTF8Buffer(currentDataBuffer) : ""));
	    return;

	}
	switch (op) {
	  case NoOperation:
	    handleNoOperation();
	    return;

	  case Shutdown:
	    handleShutdown(currentDataBuffer != null ?
			   getStringFromUTF8Buffer(currentDataBuffer) : "");
	    return;

	  case Error:
	    handleError(currentDataBuffer != null ?
			getStringFromUTF8Buffer(currentDataBuffer) : "");
	    return;

	  default:
	    throw new AssertionError(Integer.toHexString((byte) op));
	}
    }

    private void handleNoOperation() throws ProtocolException {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "NoOperation");
	}

	// do nothing
    }

    private void handleShutdown(String message) throws ProtocolException {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "Shutdown");
	}

	if (role != CLIENT) {
	    throw new ProtocolException("Shutdown sent by client");
	}
	setDown("mux connection shut down gracefully", null);
	throw new ProtocolException("received Shutdown message");
    }

    private void handlePing(int cookie) throws ProtocolException {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "Ping: cookie=" + cookie);
	}

	asyncSendPingAck(cookie);
    }

    private void handlePingAck(int cookie) throws ProtocolException {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "PingAck: cookie=" + cookie);
	}

	synchronized (muxLock) {
	    if (cookie != expectedPingCookie) {
		throw new ProtocolException(
		    "unexpected ping cookie: " + cookie);
	    } else {
		expectedPingCookie = -1;
		// NYI: rest of ping machinery
	    }
	}
    }

    private void handleError(String message) throws ProtocolException {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "Error");
	}

	setDown((role == CLIENT ? "server" : "client") +
		" reported protocol error: " + message, null);
	throw new ProtocolException("received Error message");
    }

    private void handleIncrementRation(int sessionID, int increment)
	throws ProtocolException
    {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		"IncrementRation: sessionID=" + sessionID +
		",increment=" + increment);
	}

	getSession(sessionID).handleIncrementRation(increment);
    }

    private void handleAbort(int sessionID, boolean partial, String message)
	throws ProtocolException
    {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		"Abort: sessionID=" + sessionID +
		",partial=" + partial);
	}

	getSession(sessionID).handleAbort(partial);
    }

    private void handleClose(int sessionID) throws ProtocolException {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "Close: sessionID=" + sessionID);
	}

	getSession(sessionID).handleClose();
    }

    private void handleAcknowledgment(int sessionID) throws ProtocolException {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "Acknowledgment: sessionID=" + sessionID);
	}

	getSession(sessionID).handleAcknowledgment();
    }

    private void handleData(int sessionID, boolean open, boolean close,
			    boolean eof, boolean ackRequired, ByteBuffer data)
	throws ProtocolException
    {
	if (logger.isLoggable(Level.FINEST)) {
	    int length = data.remaining();
	    HexDumpEncoder encoder = new HexDumpEncoder();
	    byte[] bytes = new byte[data.remaining()];
	    data.mark();
	    data.get(bytes);
	    data.reset();
	    logger.log(Level.FINEST,
		"Data: sessionID=" + sessionID +
		(open ? ",open" : "") +
		(close ? ",close" : "") +
		(eof ? ",eof" : "") +
		(ackRequired ? ",ackRequired" : "") +
		",length=" + length +
		(length > 0 ? ",data=\n" + encoder.encode(bytes) : ""));
	}

	if (!eof && (close || ackRequired)) {
	    throw new ProtocolException("Data: eof=" + eof +
					",close=" + close +
					",ackRequired=" + ackRequired);
	}

	if (open) {
	    handleOpen(sessionID);
	}

	getSession(sessionID).handleData(data, eof, close, ackRequired);
    }

    private Session getSession(int sessionID) throws ProtocolException {
	synchronized (muxLock) {
	    if (!busySessions.get(sessionID)) {
		throw new ProtocolException(
		    "inactive sessionID: " + sessionID);
	    }
	    return (Session) sessions.get(new Integer(sessionID));
	}
    }

    private static ByteBuffer getUTF8BufferFromString(String s) {
	CharsetEncoder encoder = Charset.forName("UTF-8").newEncoder();
	try {
	    return encoder.encode(CharBuffer.wrap(s));
	} catch (CharacterCodingException e) {
	    return null;
	}
    }

    private static String getStringFromUTF8Buffer(ByteBuffer buffer) {
	CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
	try {
	    return decoder.decode(buffer).toString();
	} catch (CharacterCodingException e) {
	    return "(error decoding UTF-8 message: " + e.toString() + ")";
	}
    }

    private static String toHexString(byte x) {
	char[] buf = new char[2];
	buf[0] = toHexChar((x >> 4) & 0xF);
	buf[1] = toHexChar(x & 0xF);
	return new String(buf);
    }

    private static String toHexString(int x) {
	char[] buf = new char[8];
	for (int i = 0; i < 8; i++) {
	    buf[i] = toHexChar((x >> ((7 - i) * 4)) & 0xF);
	}
	return new String(buf);
    }

    private static String toHexString(byte[] b) {
	char[] buf = new char[b.length * 2];
	int j = 0;
	for (int i = 0; i < b.length; i++) {
	    buf[j++] = toHexChar((b[i] >> 4) & 0xF);
	    buf[j++] = toHexChar(b[i] & 0xF);
	}
	return new String(buf);
    }

    private static char toHexChar(int x) {
	return x < 10 ? (char) ('0' + x) : (char) ('A' - 10 + x);
    }
}
