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

package com.sun.jini.jeri.internal.http;

import com.sun.jini.thread.Executor;
import com.sun.jini.thread.GetThreadPoolAction;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.StringTokenizer;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.io.context.AcknowledgmentSource;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.RequestDispatcher;

/**
 * Class representing a server-side HTTP connection used to receive and
 * dispatch incoming HTTP requests.
 *
 * @author Sun Microsystems, Inc.
 * 
 */
public abstract class HttpServerConnection implements TimedConnection {
    
    private static final int HTTP_MAJOR = 1;
    private static final int HTTP_MINOR = 1;
    
    private static final int UNSTARTED = 0;
    private static final int IDLE      = 1;
    private static final int BUSY      = 2;
    private static final int CLOSED    = 3;

    private static final String serverString = (String)
	AccessController.doPrivileged(new PrivilegedAction() {
	    public Object run() {
		return "Java/" + System.getProperty("java.version", "???") +
		       " " + HttpServerConnection.class.getName();
	    }
	});

    private static final Executor userThreadPool = (Executor)
	java.security.AccessController.doPrivileged(
	    new GetThreadPoolAction(true));

    private final Socket sock;
    private final InputStream in;
    private final OutputStream out;
    private final RequestDispatcher dispatcher;
    private final HttpServerManager manager;
    private final Object stateLock = new Object();
    private int state = UNSTARTED;

    /**
     * Creates new HttpServerConnection on top of given socket.
     */
    public HttpServerConnection(Socket sock, 
				RequestDispatcher dispatcher,
				HttpServerManager manager)
	throws IOException
    {
	if (dispatcher == null) {
	    throw new NullPointerException();
	}
	this.sock = sock;
	this.dispatcher = dispatcher;
	this.manager = manager;
	in = new BufferedInputStream(sock.getInputStream());
	out = new BufferedOutputStream(sock.getOutputStream());
    }

    /**
     * Starts request dispatch thread.  Throws IllegalStateException if
     * connection has already been started, or is closed.
     */
    protected void start() {
	synchronized (stateLock) {
	    if (state != UNSTARTED) {
		throw new IllegalStateException();
	    }
	    state = IDLE;
	    userThreadPool.execute(new Dispatcher(), "HTTP dispatcher");
	}
    }

    /**
     * Verifies that calling context has sufficient security permissions to
     * receive a request on this connection.
     */
    protected void checkPermissions() {
    }
    
    /**
     * Checks that the specified requirements are either fully or
     * partially satisfied by the constraints actually in force for
     * this connection, and returns any constraints that must be fully
     * or partially implemented by higher layers in order to fully
     * satisfy all of the specified requirements.
     **/
    protected InvocationConstraints
	checkConstraints(InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	if (constraints.requirements().isEmpty()) {
	    return InvocationConstraints.EMPTY;
	}
	throw new UnsupportedConstraintException(
	    "cannot satisfy constraints: " + constraints);
    }

    /**
     * Populates the given context collection with context information
     * representing this connection.
     */
    protected abstract void populateContext(Collection context);
    
    /**
     * Upcall indicating that connection has become idle.  Subclasses may
     * override this method to perform an appropriate action, such as
     * scheduling an idle timeout.
     */
    protected void idle() {
    }
    
    /**
     * Upcall indicating that connection is about to become busy.  Subclasses
     * may override this method to perform an appropriate action, such as
     * cancelling an idle timeout.
     */
    protected void busy() {
    }
    
    /**
     * Attempts to shut down connection, returning true if connection is
     * closed.  If force is true, connection is always shut down; if force is
     * false, connection is only shut down if idle.
     */
    public boolean shutdown(boolean force) {
	synchronized (stateLock) {
	    if (state == CLOSED) {
		return true;
	    }
	    if (!force && state == BUSY) {
		return false;
	    }
	    state = CLOSED;
	}
	try { sock.close(); } catch (IOException ex) {}
	return true;
    }
    
    /**
     * Incoming request dispatcher.
     */
    private class Dispatcher implements Runnable {

	/**
	 * Dispatch loop.
	 */
	public void run() {
	    try {
		for (;;) {
		    idle();

		    MessageReader reader = new MessageReader(in, false);
		    StartLine sline = reader.readStartLine();
		    
		    busy();
		    synchronized (stateLock) {
			if (state == CLOSED) {
			    return;
			}
			state = BUSY;
		    }

		    Header header = reader.readHeader();
		    String reqType = header.getField("RMI-Request-Type");
		    if (!"POST".equals(sline.method)) {
			handleBadRequest(sline, header, reader);
		    } else if ("standard".equalsIgnoreCase(reqType)) {
			handleRequest(sline, header, reader);
		    } else if ("ping".equalsIgnoreCase(reqType)) {
			handlePing(sline, header, reader);
		    } else {
			handleBadRequest(sline, header, reader);
		    }
		    
		    synchronized (stateLock) {
			if (state == CLOSED) {
			    return;
			}
			state = IDLE;
		    }
		}
	    } catch (IOException ex) {
	    } finally {
		shutdown(true);
	    }
	}

	/**
	 * Handles unacceptable HTTP request.
	 */
	private void handleBadRequest(StartLine inLine,
				      Header inHeader,
				      MessageReader reader)
	    throws IOException
	{
	    inHeader.merge(reader.readTrailer());
	    registerAcks(inHeader.getField("RMI-Response-Ack"));
	    boolean persist = supportsPersist(inLine, inHeader);
	    
	    MessageWriter writer = new MessageWriter(out, false);
	    writer.writeStartLine(new StartLine(HTTP_MAJOR, HTTP_MINOR,
				      HttpURLConnection.HTTP_BAD_REQUEST, 
				      "Bad Request"));
	    writer.writeHeader(createResponseHeader(persist));
	    writer.writeTrailer(null);
	    
	    if (!persist) {
		shutdown(true);
	    }
	}

	/**
	 * Handles ping request.
	 */
	private void handlePing(StartLine inLine, Header inHeader,
				MessageReader reader)
	    throws IOException
	{
	    inHeader.merge(reader.readTrailer());
	    registerAcks(inHeader.getField("RMI-Response-Ack"));
	    boolean persist = supportsPersist(inLine, inHeader);

	    MessageWriter writer = new MessageWriter(out, false);
	    writer.writeStartLine(new StartLine(HTTP_MAJOR, HTTP_MINOR,
				      HttpURLConnection.HTTP_OK, "OK"));
	    writer.writeHeader(createResponseHeader(persist));
	    writer.writeTrailer(null);
	    
	    if (!persist) {
		shutdown(true);
	    }
	}
	
	/**
	 * Handles "standard" (i.e., dispatchable) request.
	 */
	private void handleRequest(StartLine inLine, Header inHeader,
				   MessageReader reader)
	    throws IOException
	{
	    registerAcks(inHeader.getField("RMI-Response-Ack"));
	    boolean persist = supportsPersist(inLine, inHeader);
	    boolean chunk = supportsChunking(inLine, inHeader);

	    MessageWriter writer = new MessageWriter(out, chunk);
	    writer.writeStartLine(new StartLine(HTTP_MAJOR, HTTP_MINOR,
				      HttpURLConnection.HTTP_OK, "OK"));
	    writer.writeHeader(createResponseHeader(persist));
	    
	    InboundRequestImpl req = new InboundRequestImpl(reader, writer);
	    try { dispatcher.dispatch(req); } catch (Throwable th) {}
	    req.finish();
	    
	    if (!persist || req.streamCorrupt()) {
		shutdown(true);
	    }
	}
    }

    /**
     * HTTP-based implementation of InboundRequest abstraction.
     */
    private class InboundRequestImpl 
	extends Request implements InboundRequest 
    {
	private final MessageReader reader;
	private final MessageWriter writer;
	private String cookie;
	private boolean corrupt = false;

	/**
	 * Creates new InboundRequestImpl which uses given MessageReader and
	 * MessageWriter instances to read/write request content.
	 */
	InboundRequestImpl(MessageReader reader, MessageWriter writer) {
	    this.reader = reader;
	    this.writer = writer;
	}
	
	public void checkPermissions() {
	    HttpServerConnection.this.checkPermissions();
	}
	
	public InvocationConstraints
	    checkConstraints(InvocationConstraints constraints)
	    throws UnsupportedConstraintException
	{
	    return HttpServerConnection.this.checkConstraints(constraints);
	}

	public void populateContext(Collection context) {
	    context.add(new AcknowledgmentSource() {
		public boolean addAcknowledgmentListener(
		    AcknowledgmentSource.Listener listener)
		{
		    if (listener == null) {
			throw new NullPointerException();
		    }
		    return InboundRequestImpl.this.addAcknowledgmentListener(
			listener);
		}
	    });
	    HttpServerConnection.this.populateContext(context);
	}
	
	public InputStream getRequestInputStream() {
	    return getInputStream();
	}
	
	public OutputStream getResponseOutputStream() {
	    return getOutputStream();
	}
	
	/**
	 * Returns true if stream corrupted, false if stream ok.
	 */
	boolean streamCorrupt() {
	    return corrupt;
	}

	void startOutput() throws IOException {
	    // start line, header already written
	}

	void write(byte[] b, int off, int len) throws IOException {
	    writer.writeContent(b, off, len);
	}

	void endOutput() throws IOException {
	    if (cookie != null) {
		Header trailer = new Header();
		trailer.setField("RMI-Response-Cookie", cookie);
		writer.writeTrailer(trailer);
	    } else {
		writer.writeTrailer(null);
	    }
	}

	boolean startInput() throws IOException {
	    return true;	// header already read
	}

	int read(byte[] b, int off, int len) throws IOException {
	    return reader.readContent(b, off, len);
	}

	int available() throws IOException {
	    return reader.availableContent();
	}

	void endInput() throws IOException {
	    Header trailer = reader.readTrailer();
	    if (trailer != null) {
		registerAcks(trailer.getField("RMI-Response-Ack"));
	    }
	}

	void addAckListener(AcknowledgmentSource.Listener listener) {
	    if (cookie == null) {
		cookie = manager.newCookie();
	    }
	    manager.addAckListener(cookie, listener);
	}

	void done(boolean corrupt) {
	    this.corrupt = corrupt;
	}
    }
    
    /**
     * Notifies listeners for response ack cookies parsed from (possibly null)
     * comma-separated cookie list string.
     */
    private void registerAcks(String ackList) {
	if (ackList != null) {
	    StringTokenizer tok = new StringTokenizer(ackList, ",");
	    while (tok.hasMoreTokens()) {
		manager.notifyAckListeners(tok.nextToken().trim());
	    }
	}
    }

    /**
     * Returns true if the received message start line and header indicate that
     * the connection can be persisted.
     */
    private static boolean supportsPersist(StartLine sline, Header header) {
	if (header.containsValue("Connection", "close", true)) {
	    return false;
	} else if (header.containsValue("Connection", "Keep-Alive", true)) {
	    return true;
	} else {
	    int c = StartLine.compareVersions(sline.major, sline.minor, 1, 1);
	    return c >= 0;
	}
    }

    /**
     * Returns true if the received message start line indicates that the
     * sender understands chunked transfer coding.
     */
    private static boolean supportsChunking(StartLine sline, Header header) {
	int c = StartLine.compareVersions(sline.major, sline.minor, 1, 1);
	// REMIND: is requiring "TE: trailers" too strict?
	return c >= 0 && header.containsValue("TE", "trailers", true);
    }
    
    /**
     * Creates base header to be used in response message.  If persist is true,
     * adds fields indicating a persistent connection.
     */
    private static Header createResponseHeader(boolean persist) {
	Header header = new Header();
	long now = System.currentTimeMillis();
	header.setField("Date", Header.getDateString(now));
	header.setField("Server", serverString);
	header.setField("Connection", persist ? "Keep-Alive" : "close");
	return header;
    }
}
