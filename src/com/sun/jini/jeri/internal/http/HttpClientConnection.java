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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.OutboundRequest;
import net.jini.io.context.AcknowledgmentSource;

/**
 * Class representing a client-side HTTP connection used to send HTTP requests.
 *
 * @author Sun Microsystems, Inc.
 * 
 */
public class HttpClientConnection implements TimedConnection {
    
    private static final int HTTP_MAJOR = 1;
    private static final int HTTP_MINOR = 1;
    
    private static final String clientString = (String)
	AccessController.doPrivileged(new PrivilegedAction() {
	    public Object run() {
		return "Java/" + System.getProperty("java.version", "???") +
		       " " + HttpClientConnection.class.getName();
	    }
	});

    /* modes */
    private static final int DIRECT   = 0;
    private static final int PROXIED  = 1;
    private static final int TUNNELED = 2;
    
    /* states */
    private static final int IDLE     = 0;
    private static final int BUSY     = 1;
    private static final int CLOSED   = 2;

    private final int mode;
    private final Object stateLock = new Object();
    private int state = IDLE;

    private final HttpClientManager manager;
    private ServerInfo targetInfo;
    private ServerInfo proxyInfo;
    private final boolean persist;
    private String[] acks;

    private Socket sock;
    private OutputStream out;
    private InputStream in;

    /**
     * Creates HttpClientConnection which sends requests directly to given
     * host/port through a socket obtained from the given socket factory.
     * The createSocket method of the given socket factory may be called
     * more than once in cases where connection establishment involves multiple
     * HTTP message exchanges.
     */
    public HttpClientConnection(String host,
				int port,
				HttpClientSocketFactory factory,
				HttpClientManager manager)
	throws IOException
    {
	this.manager = manager;
	mode = DIRECT;
	targetInfo = manager.getServerInfo(host, port);
	persist = true;
	setupConnection(factory);
    }
    
    /**
     * Creates HttpClientConnection which sends requests to given target
     * host/port through the indicated HTTP proxy over a socket provided by the
     * specified socket factory.  If tunnel is true, requests are tunneled
     * through the proxy over an additional layered socket (also provided by
     * the socket factory).  If persist and tunnel are both false, the
     * connection can only be used for a single request. If persist is true
     * and tunnel is false, a persistent connection is maintained if possible.
     * The createSocket and createTunnelSocket methods of the given socket
     * factory may be called more than once in cases where connection
     * establishment involves multiple HTTP message exchanges.
     */
    public HttpClientConnection(String targetHost,
				int targetPort,
				String proxyHost,
				int proxyPort,
				boolean tunnel,
				boolean persist,
				HttpClientSocketFactory factory,
				HttpClientManager manager)
	throws IOException
    {
	this.manager = manager;
	mode = tunnel ? TUNNELED : PROXIED;
	targetInfo = manager.getServerInfo(targetHost, targetPort);
	proxyInfo = manager.getServerInfo(proxyHost, proxyPort);
	this.persist = persist || tunnel;
	setupConnection(factory);
    }
    
    /**
     * Pings target.  Returns true if ping succeeded, false if it fails
     * "cleanly" (i.e., if a valid HTTP response indicating request failure is
     * received).
     */
    public boolean ping() throws IOException {
	markBusy();
	fetchServerInfo();
	try {
	    return ping(false);
	} finally {
	    markIdle();
	}
    }
    
    /**
     * Initiates new request to connection target.  Throws an IOException if
     * the connection is currently busy.
     */
    public OutboundRequest newRequest() throws IOException {
	OutboundRequest req = null;
	markBusy();
	fetchServerInfo();
	try {
	    req = new OutboundRequestImpl();
	    return req;
	} finally {
	    if (req == null) {
		markIdle();
	    }
	}
    }
    
    /**
     * Upcall indicating that connection has become idle.  Subclasses may
     * override this method to perform an appropriate action, such as
     * scheduling an idle timeout.
     */
    protected void idle() {
    }
    
    /**
     * Returns socket over which requests are sent.
     */
    public Socket getSocket() {
	return sock;
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
	disconnect();
	return true;
    }
    
    /**
     * Fetches latest server/proxy HTTP information from cache.
     */
    private void fetchServerInfo() {
	ServerInfo sinfo = 
	    manager.getServerInfo(targetInfo.host, targetInfo.port);
	if (sinfo.timestamp > targetInfo.timestamp) {
	    targetInfo = sinfo;
	}
	if (mode != DIRECT) {
	    sinfo = manager.getServerInfo(proxyInfo.host, proxyInfo.port);
	    if (sinfo.timestamp > proxyInfo.timestamp) {
		proxyInfo = sinfo;
	    }
	}
    }
    
    /**
     * Flushes current copy of server/proxy HTTP information to cache.
     */
    private void flushServerInfo() {
	manager.cacheServerInfo(targetInfo);
	if (mode != DIRECT) {
	    manager.cacheServerInfo(proxyInfo);
	}
    }
    
    /**
     * Marks connection busy.  Throws IOException if connection closed.
     */
    private void markBusy() throws IOException {
	synchronized (stateLock) {
	    if (state == BUSY) {
		throw new IOException("connection busy");
	    } else if (state == CLOSED) {
		throw new IOException("connection closed");
	    }
	    state = BUSY;
	}
    }
    
    /**
     * Marks connection idle.  Does nothing if connection closed.
     */
    private void markIdle() {
	synchronized (stateLock) {
	    if (state == CLOSED) {
		return;
	    }
	    state = IDLE;
	}
	idle();
    }

    /**
     * Establishes connection using sockets from the given socket factory.
     * Throws IOException if connection setup fails.
     */
    private void setupConnection(HttpClientSocketFactory factory)
	throws IOException
    {
	boolean ok = false;
	try {
	    /*
	     * 4 cycles required in worst-case (proxied) scenario:
	     * i = 0: send OPTIONS request to proxy
	     * i = 1: send ping, fails with 407 (proxy auth required)
	     * i = 2: send ping, fails with 401 (unauthorized)
	     * i = 3: return
	     */
	    for (int i = 0; i < 4; i++) {
		if (sock == null) {
		    connect(factory);
		}
		if (mode == PROXIED && 
		    proxyInfo.timestamp == ServerInfo.NO_TIMESTAMP)
		{
		    requestProxyOptions();
		} else if (targetInfo.timestamp == ServerInfo.NO_TIMESTAMP) {
		    ping(true);
		} else {
		    ok = true;
		    return;
		}
	    }
	} finally {
	    if (!ok) {
		disconnect();
	    }
	}
	throw new ConnectException("failed to establish HTTP connection");
    }
    
    /**
     * Opens underlying connection.  If tunneling through an HTTP proxy,
     * attempts CONNECT request.
     */
    private void connect(HttpClientSocketFactory factory) throws IOException {
	disconnect();
	for (int i = 0; i < 2; i++) {
	    if (sock == null) {
		ServerInfo sinfo = (mode == DIRECT) ? targetInfo : proxyInfo;
		sock = factory.createSocket(sinfo.host, sinfo.port);
		out = new BufferedOutputStream(sock.getOutputStream());
		in = new BufferedInputStream(sock.getInputStream());
	    }
	    if (mode != TUNNELED) {
		return;
	    }
	    if (requestProxyConnect()) {
		sock = factory.createTunnelSocket(sock);
		out = new BufferedOutputStream(sock.getOutputStream());
		in = new BufferedInputStream(sock.getInputStream());
		return;
	    }
	}
	throw new ConnectException("failed to establish proxy tunnel");
    }
    
    /**
     * Closes and releases reference to underlying socket.
     */
    private void disconnect() {
	if (sock != null) {
	    try { sock.close(); } catch (IOException ex) {}
	    sock = null;
	    out = null;
	    in = null;
	}
    }

    /**
     * Pings target.  Returns true if succeeded, false if failed "cleanly".
     */
    private boolean ping(boolean setup) throws IOException {
	StartLine outLine = createPostLine();
	Header outHeader = createPostHeader(outLine);
	outHeader.setField("RMI-Request-Type", "ping");
	MessageWriter writer = new MessageWriter(out, false);

	writer.writeStartLine(outLine);
	writer.writeHeader(outHeader);
	writer.writeTrailer(null);

	MessageReader reader;
	StartLine inLine;
	Header inHeader;
	do {
	    reader = new MessageReader(in, false);
	    inLine = reader.readStartLine();
	    inHeader = reader.readHeader();
	    inHeader.merge(reader.readTrailer());
	} while (inLine.status / 100 == 1);

	analyzePostResponse(inLine, inHeader);
	if (!supportsPersist(inLine, inHeader)) {
	    if (setup) {
		disconnect();
	    } else {
		shutdown(true);
	    }
	}
	return (inLine.status / 100) == 2;
    }
    
    /**
     * Sends OPTIONS request to proxy.  Returns true if OPTIONS succeeded,
     * false otherwise.
     */
    private boolean requestProxyOptions() throws IOException {
	MessageWriter writer = new MessageWriter(out, false);
	writer.writeStartLine(
	    new StartLine(HTTP_MAJOR, HTTP_MINOR, "OPTIONS", "*"));
	writer.writeHeader(createOptionsHeader());
	writer.writeTrailer(null);

	MessageReader reader;
	StartLine inLine;
	Header inHeader;
	do {
	    reader = new MessageReader(in, false);
	    inLine = reader.readStartLine();
	    inHeader = reader.readHeader();
	    inHeader.merge(reader.readTrailer());
	} while (inLine.status / 100 == 1);

	analyzeProxyResponse(inLine, inHeader);
	if (!supportsPersist(inLine, inHeader)) {
	    disconnect();
	}
	return (inLine.status / 100) == 2;
    }
    
    /**
     * Sends CONNECT request to proxy.  Returns true if CONNECT succeeded,
     * false otherwise.
     */
    private boolean requestProxyConnect() throws IOException {
	StartLine outLine = new StartLine(
	    HTTP_MAJOR, HTTP_MINOR, "CONNECT",
	    targetInfo.host + ":" + targetInfo.port);

	// REMIND: eliminate hardcoded protocol string
	Header outHeader = createConnectHeader();
	String auth = 
	    proxyInfo.getAuthString("http", outLine.method, outLine.uri);
	if (auth != null) {
	    outHeader.setField("Proxy-Authorization", auth);
	}

	MessageWriter writer = new MessageWriter(out, false);
	writer.writeStartLine(outLine);
	writer.writeHeader(outHeader);
	writer.writeTrailer(null);
	
	MessageReader reader;
	StartLine inLine;
	Header inHeader;
	do {
	    reader = new MessageReader(in, true);
	    inLine = reader.readStartLine();
	    inHeader = reader.readHeader();
	    inHeader.merge(reader.readTrailer());
	} while (inLine.status / 100 == 1);

	analyzeProxyResponse(inLine, inHeader);
	if ((inLine.status / 100) == 2) {
	    return true;
	}
	if (!supportsPersist(inLine, inHeader)) {
	    disconnect();
	}
	return false;
    }
    
    /**
     * Creates start line for outbound HTTP POST message.
     */
    private StartLine createPostLine() {
	String uri = (mode == PROXIED) ?
	    "http://" + targetInfo.host + ":" + targetInfo.port + "/" : "/";
	return new StartLine(HTTP_MAJOR, HTTP_MINOR, "POST", uri);
    }
    
    /**
     * Creates base header containing fields common to all HTTP messages sent
     * by this connection.
     */
    private Header createBaseHeader() {
	Header header = new Header();
	long now = System.currentTimeMillis();
	header.setField("Date", Header.getDateString(now));
	header.setField("User-Agent", clientString);
	return header;
    }

    /**
     * Creates header for use in CONNECT messages sent to proxies.
     */
    private Header createConnectHeader() {
	Header header = createBaseHeader();
	header.setField("Host", targetInfo.host + ":" + targetInfo.port);
	return header;
    }
    
    /**
     * Creates header for use in OPTIONS messages sent to proxies.
     */
    private Header createOptionsHeader() {
	Header header = createBaseHeader();
	header.setField("Host", proxyInfo.host + ":" + proxyInfo.port);
	if (!persist) {
	    header.setField("Connection", "close");
	}
	return header;
    }

    /**
     * Creates header for outbound HTTP POST message with given start line.
     */
    private Header createPostHeader(StartLine sline) {
	Header header = createBaseHeader();
	header.setField("Host", targetInfo.host + ":" + targetInfo.port);
	header.setField("Connection", persist ? "TE" : "close, TE");
	header.setField("TE", "trailers");
	
	// REMIND: eliminate hardcoded protocol string
	String auth = 
	    targetInfo.getAuthString("http", sline.method, sline.uri);
	if (auth != null) {
	    header.setField("Authorization", auth);
	}
	if (mode == PROXIED) {
	    auth = proxyInfo.getAuthString("http", sline.method, sline.uri);
	    if (auth != null) {
		header.setField("Proxy-Authorization", auth);
	    }
	}

	acks = manager.getUnsentAcks(targetInfo.host, targetInfo.port);
	if (acks.length > 0) {
	    String ackList = acks[0];
	    for (int i = 1; i < acks.length; i++) {
		ackList += ", " + acks[i];
	    }
	    header.setField("RMI-Response-Ack", ackList);
	}
	return header;
    }
    
    /**
     * Analyzes POST response message start line and header, updating cached
     * target/proxy server information if necessary.
     */
    private void analyzePostResponse(StartLine inLine, Header inHeader) {
	String str;
	long now = System.currentTimeMillis();
	
	if ((str = inHeader.getField("WWW-Authenticate")) != null) {
	    try {
		targetInfo.setAuthInfo(str);
	    } catch (HttpParseException ex) {
	    }
	    targetInfo.timestamp = now;
	} else if ((str = inHeader.getField("Authentication-Info")) != null) {
	    try {
		targetInfo.updateAuthInfo(str);
	    } catch (HttpParseException ex) {
	    }
	    targetInfo.timestamp = now;
	}
	
	if (mode != DIRECT) {
	    if ((str = inHeader.getField("Proxy-Authenticate")) != null) {
		try {
		    proxyInfo.setAuthInfo(str);
		} catch (HttpParseException ex) {
		}
		proxyInfo.timestamp = now;
	    } else if ((str = inHeader.getField(
			    "Proxy-Authentication-Info")) != null)
	    {
		try {
		    proxyInfo.updateAuthInfo(str);
		} catch (HttpParseException ex) {
		}
		proxyInfo.timestamp = now;
	    }
	}

	if (mode != PROXIED) {
	    targetInfo.major = inLine.major;
	    targetInfo.minor = inLine.minor;
	    targetInfo.timestamp = now;
	} else {
	    /* Return message was sent by proxy; however, since some proxies
	     * incorrectly relay the target server's version numbers instead of
	     * their own, we can only rely on version numbers which could not
	     * have been sent from target server.
	     */
	    if (inLine.status == HttpURLConnection.HTTP_PROXY_AUTH) {
		proxyInfo.major = inLine.major;
		proxyInfo.minor = inLine.minor;
	    }
	    proxyInfo.timestamp = now;
	}
	
	if ((inLine.status / 100) == 2) {
	    manager.clearUnsentAcks(targetInfo.host, targetInfo.port, acks);
	    targetInfo.timestamp = now;
	}

	flushServerInfo();
    }
    
    /**
     * Analyzes CONNECT or OPTIONS response message start line and header sent
     * by proxy, updating proxy server information if necessary.
     */
    private void analyzeProxyResponse(StartLine inLine, Header inHeader) {
	proxyInfo.major = inLine.major;
	proxyInfo.minor = inLine.minor;
	proxyInfo.timestamp = System.currentTimeMillis();
	
	String str;
	if ((str = inHeader.getField("Proxy-Authenticate")) != null) {
	    try {
		proxyInfo.setAuthInfo(str);
	    } catch (HttpParseException ex) {
	    }
	} else if ((str = inHeader.getField(
			"Proxy-Authentication-Info")) != null)
	{
	    try {
		proxyInfo.updateAuthInfo(str);
	    } catch (HttpParseException ex) {
	    }
	}

	flushServerInfo();
    }

    /**
     * Returns true if requests sent over this connection should chunk output.
     */
    private boolean supportsChunking() {
	ServerInfo si = (mode == PROXIED) ? proxyInfo : targetInfo;
	return StartLine.compareVersions(si.major, si.minor, 1, 1) >= 0;
    }
    
    /**
     * Returns true if the given response line and header indicate that the
     * connection can be persisted, and use of persistent connections has
     * not been disabled.
     */
    private boolean supportsPersist(StartLine sline, Header header) {
	if (header.containsValue("Connection", "close", true)) {
	    return false;
	} else if (!persist) {
	    return false;
	} else if (header.containsValue("Connection", "Keep-Alive", true)) {
	    return true;
	} else {
	    int c = StartLine.compareVersions(sline.major, sline.minor, 1, 1);
	    return c >= 0;
	}
    }

    /**
     * HTTP-based implementation of OutboundRequest abstraction.
     */
    private class OutboundRequestImpl 
	extends Request implements OutboundRequest
    {
	private final MessageWriter writer;
	private MessageReader reader;
	private StartLine inLine;
	private Header inHeader;
	private boolean persist = false;
	
	OutboundRequestImpl() throws IOException {
	    StartLine outLine = createPostLine();
	    Header outHeader = createPostHeader(outLine);
	    outHeader.setField("RMI-Request-Type", "standard");

	    writer = new MessageWriter(out, supportsChunking());
	    writer.writeStartLine(outLine);
	    writer.writeHeader(outHeader);
	    writer.flush();
	}

	public void populateContext(Collection context) {
	    if (context == null) {
		throw new NullPointerException();
	    }
	}

	public InvocationConstraints getUnfulfilledConstraints() {
	    /*
	     * NYI: With no request-specific hook, we depend on
	     * OutboundRequest wrapping for this method.
	     */
	    throw new AssertionError();
	}

	public OutputStream getRequestOutputStream() {
	    return getOutputStream();
	}
	
	public InputStream getResponseInputStream() {
	    return getInputStream();
	}

	void startOutput() throws IOException {
	    // start line, header already written
	}

	void write(byte[] b, int off, int len) throws IOException {
	    writer.writeContent(b, off, len);
	}

	void endOutput() throws IOException {
	    writer.writeTrailer(null);
	}

	boolean startInput() throws IOException {
	    for (;;) {
		reader = new MessageReader(in, false);
		inLine = reader.readStartLine();
		inHeader = reader.readHeader();
		if (inLine.status / 100 != 1) {
		    return inLine.status / 100 == 2;
		}
		reader.readTrailer();
	    }
	}

	int read(byte[] b, int off, int len) throws IOException {
	    return reader.readContent(b, off, len);
	}

	int available() throws IOException {
	    return reader.availableContent();
	}

	void endInput() throws IOException {
	    inHeader.merge(reader.readTrailer());
	    analyzePostResponse(inLine, inHeader);
	    persist = supportsPersist(inLine, inHeader);
	}

	void addAckListener(AcknowledgmentSource.Listener listener) {
	    throw new UnsupportedOperationException();
	}

	void done(boolean corrupt) {
	    if (corrupt || !persist) {
		shutdown(true);
	    } else {
		markIdle();
	    }
	}
    }
}
