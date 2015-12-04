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

package net.jini.jeri.connection;

import org.apache.river.jeri.internal.mux.MuxServer;
import org.apache.river.logging.Levels;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.RequestDispatcher;

/**
 * Provides server-side connection management using the <a
 * href="{@docRoot}/net/jini/jeri/connection/doc-files/mux.html">Jini
 * extensible remote invocation (Jini ERI) multiplexing protocol</a>
 * to frame and multiplex requests and responses over connections.
 *
 * <p>A <code>ServerConnectionManager</code> is created by a
 * connection-based {@link net.jini.jeri.ServerEndpoint} implemention to manage
 * connections.  The {@link #handleConnection handleConnection} method
 * is used to manage connections for a particular {@link
 * ServerConnection}.
 *
 * <p>Each <i>session</i> of the Jini ERI multiplexing protocol is
 * mapped to a new request.  Request data is read as the data received
 * for the session, and response data is written as the data sent for
 * the session.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 *
 * @org.apache.river.impl
 *
 * This implementation uses the {@link Logger} named
 * <code>net.jini.jeri.connection.ServerConnectionManager</code> to
 * log information at the following levels:
 *
 * <p><table summary="Describes what is logged by
 * ServerConnectionManager to its logger at various logging levels"
 * border=1 cellpadding=5>
 *
 * <tr> <th> Level <th> Description
 *
 * <tr> <td> {@link Levels#HANDLED HANDLED} <td>
 * I/O exception initiating handling of a new request on a connection
 *
 * <tr> <td> {@link Levels#HANDLED HANDLED} <td>
 * I/O exception initiating multiplexing on a new connection
 *
 * </table>
 *
 * <p>This implementation uses the {@link Logger} named
 * <code>net.jini.jeri.connection.mux</code> to log information at the
 * following levels:
 *
 * <p><table summary="Describes what is logged by
 * ServerConnectionManager to the mux logger at various logging
 * levels" border=1 cellpadding=5>
 *
 * <tr> <th> Level <th> Description
 *
 * <tr> <td> {@link Level#WARNING WARNING} <td> unexpected exception
 * during asynchronous I/O processing, or thread creation failure
 *
 * <tr> <td> {@link Levels#HANDLED HANDLED} <td> I/O exception during
 * asynchronous I/O processing
 *
 * <tr> <td> {@link Level#FINEST FINEST} <td> detailed implementation
 * activity
 *
 * </table>
 **/
public final class ServerConnectionManager {
    /**
     * ServerConnectionManager logger.
     */
    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.connection.ServerConnectionManager");

    /**
     * Creates a new <code>ServerConnectionManager</code>.
     **/
    public ServerConnectionManager() {
    }

    /**
     * Request dispatcher wrapper around the request dispatcher passed to
     * handleConnection (i.e., the request dispatcher created by the runtime).
     */
    private static final class Dispatcher implements RequestDispatcher {
	/**
	 * The request dispatcher passed to handleConnection.
	 */
	private final RequestDispatcher dispatcher;
	/**
	 * The connection passed to handleConnection.
	 */
	private final ServerConnection c;

	Dispatcher(RequestDispatcher dispatcher,
		   ServerConnection c)
	{
	    this.dispatcher = dispatcher;
	    this.c = c;
	}

	/**
	 * Calls processRequestData on the connection, passing the streams
	 * from the specified inbound request, to obtain the request handle,
	 * wraps the mux inbound request, connection and handle in an inbound
	 * request, checks for accept permission, and calls dispatch on the
	 * underlying dispatcher. If an IOException is thrown, catch it. It
	 * is assumed that the caller will always abort the request (even on
	 * normal return).
	 */
	public void dispatch(InboundRequest req) {
	    try {
		InboundRequest sreq =
		    new Inbound(req, c,
				c.processRequestData(
					    req.getRequestInputStream(),
					    req.getResponseOutputStream()));
		sreq.checkPermissions();
		dispatcher.dispatch(sreq);
	    } catch (IOException e) {
		if (logger.isLoggable(Levels.HANDLED)) {
		    logThrow(logger, "dispatchRequest",
			     "{0} throws", new Object[]{c}, e);
		}
	    }
	}
    }

    /**
     * Inbound request wrapper around the inbound request created by the mux.
     */
    private static final class Inbound implements InboundRequest {
	/**
	 * The inbound request created by the mux.
	 */
	private final InboundRequest req;
	/**
	 * The connection on which the inbound request originates.
	 */
	private final ServerConnection c;
	/**
	 * The inbound request handle.
	 */
	private final InboundRequestHandle handle;

	Inbound(InboundRequest req,
		ServerConnection c,
		InboundRequestHandle handle)
	{
	    this.req = req;
	    this.c = c;
	    this.handle = handle;
	}

	/* delegate to both the underlying request and the connection */
	public void populateContext(Collection context) {
	    req.populateContext(context);
	    c.populateContext(handle, context);
	}

	/* pass-through to the connection */
	public InvocationConstraints checkConstraints(
					    InvocationConstraints constraints)
	    throws UnsupportedConstraintException
	{
	    return c.checkConstraints(handle, constraints);
	}

	/* pass-through to the connection */
	public void checkPermissions() {
	    c.checkPermissions(handle);
	}

	/* pass-through to the underlying request */
	public InputStream getRequestInputStream() {
	    return req.getRequestInputStream();
	}

	/* pass-through to the underlying request */
	public OutputStream getResponseOutputStream() {
	    return req.getResponseOutputStream();
	}

	/* pass-through to underlying request */
	public void abort() {
	    req.abort();
	}
    }

    /**
     * Subclass wrapper around MuxServer for inbound connections.
     */
    private static final class InboundMux extends MuxServer {
	/**
	 * The inbound connection.
	 */
	private final ServerConnection c;

	/**
	 * Constructs an instance from the connection's streams.
	 */
	private InboundMux(ServerConnection c,
			   RequestDispatcher dispatcher)
	    throws IOException
	{
	    super(c.getOutputStream(), c.getInputStream(), dispatcher);
	    this.c = c;
	}

	/**
	 * Constructs an instance from the connection's channel.
	 */
	private InboundMux(ServerConnection c,
			   RequestDispatcher dispatcher,
			   boolean ignore)
	    throws IOException
	{
	    super(c.getChannel(), dispatcher);
	    this.c = c;
	}

	/**
	 * Constructs an instance from the connection.
	 */
	static void create(ServerConnection c,
			   RequestDispatcher dispatcher)
	{
	    RequestDispatcher d = new Dispatcher(dispatcher, c);
	    try {
		if (c.getChannel() == null) {
		    new InboundMux(c, d).start();
		} else {
		    new InboundMux(c, d, true).start();
		}
	    } catch (IOException e) {
		if (logger.isLoggable(Levels.HANDLED)) {
		    logThrow(logger, "handleConnection",
			     "{0} throws", new Object[]{c}, e);
		}
		try {
		    c.close();
		} catch (IOException ee) {
		}
	    }
	}

	/**
	 * Close the connection, so that the provider is notified.
	 */
	protected void handleDown() {
	    try {
		c.close();
	    } catch (IOException e) {
	    }
	}
    }

    /**
     * Log a throw.
     */
    private static void logThrow(Logger logger,
				 String method,
				 String msg,
				 Object[] args,
				 Exception e)
    {
	LogRecord lr = new LogRecord(Levels.HANDLED, msg);
	lr.setLoggerName(logger.getName());
	lr.setSourceClassName(ServerConnectionManager.class.getName());
	lr.setSourceMethodName(method);
	lr.setParameters(args);
	lr.setThrown(e);
	logger.log(lr);
    }

    /**
     * Starts handling requests received on the specified newly
     * accepted connection, dispatching them to the specified request
     * dispatcher asynchronously, and returns immediately.
     *
     * <p>The Jini ERI multiplexing protocol is started on the
     * connection (as the server).  As each request is received, the
     * {@link ServerConnection#processRequestData processRequestData}
     * method of the connection will be invoked with the request input
     * stream and the response output stream of the {@link
     * InboundRequest} created for the request, to obtain a handle for
     * the request.  The {@link ServerConnection#checkPermissions
     * checkPermissions} method of the connection is then invoked with
     * that handle, and if it returns normally,
     * <code>dispatcher</code> is invoked with the
     * <code>InboundRequest</code>.  All of this processing is
     * performed using the same security context in force when this
     * <code>handleConnection</code> method was invoked.  The {@link
     * InboundRequest#checkPermissions checkPermissions}, {@link
     * InboundRequest#checkConstraints checkConstraints}, and {@link
     * InboundRequest#populateContext populateContext} methods of each
     * <code>InboundRequest</code> created are implemented by
     * delegating to the corresponding method of the connection
     * passing the handle for the request and the other arguments (if
     * any).
     *
     * <P>The implementation may close the connection if it determines
     * that the client has closed its side of the connection, if there
     * is an unrecoverable problem with the connection, or for other
     * implementation-specific reasons.  The caller is responsible for
     * closing the connection when the {@link
     * net.jini.jeri.ServerEndpoint.ListenHandle#close close} method
     * of the associated {@link
     * net.jini.jeri.ServerEndpoint.ListenHandle ListenHandle} is
     * invoked.
     *
     * @param conn the server connection
     *
     * @param dispatcher the request dispatcher to use to dispatch
     * requests received on the specified connection
     *
     * @throws NullPointerException if <code>conn</code> or
     * <code>dispatcher</code> is <code>null</code>
     **/
    public void handleConnection(ServerConnection conn,
				 RequestDispatcher dispatcher)
    {
	if (conn == null || dispatcher == null) {
	    throw new NullPointerException();
	}
	InboundMux.create(conn, dispatcher);
    }
}
