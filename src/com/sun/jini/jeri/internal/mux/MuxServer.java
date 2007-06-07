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

import com.sun.jini.action.GetIntegerAction;
import com.sun.jini.thread.Executor;
import com.sun.jini.thread.GetThreadPoolAction;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SocketChannel;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.RequestDispatcher;
import net.jini.security.Security;
import net.jini.security.SecurityContext;

/**
 * A MuxServer controls the server side of a multiplexed connection.
 *
 * @author Sun Microsystems, Inc.
 **/
public class MuxServer extends Mux {

    /** initial inbound ration as server, default is 32768 */
    private static final int serverInitialInboundRation =
	((Integer) AccessController.doPrivileged(new GetIntegerAction(
	    "com.sun.jini.jeri.connection.mux.server.initialInboundRation",
	    32768))).intValue();

    /**
     * pool of threads for executing tasks with user code: used for
     * dispatching incoming requests to request dispatchers
     **/
    private static final Executor userThreadPool =
	(Executor) AccessController.doPrivileged(
	    new GetThreadPoolAction(true));

    /** mux logger */
    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.connection.mux");

    /** the request dispatcher to dispatch incoming requests to */
    private final RequestDispatcher requestDispatcher;

    /** the security context to dispatch incoming requests in */
    private final SecurityContext securityContext;

    /**
     * Initiates the server side of a multiplexed connection over the
     * given input/output stream pair.
     *
     * @param out the output stream of the underlying connection
     *
     * @param in the input stream of the underlying connection
     *
     * @param requestDispatcher the request dispatcher to dispatch
     * incoming requests received on this multiplexed connection to
     **/
    public MuxServer(OutputStream out, InputStream in,
		     RequestDispatcher requestDispatcher)
	throws IOException
    {
	super(out, in, Mux.SERVER, serverInitialInboundRation, 1024);

	this.requestDispatcher = requestDispatcher;
	this.securityContext = Security.getContext();
    }

    public MuxServer(SocketChannel channel,
		     RequestDispatcher requestDispatcher)
	throws IOException
    {
	super(channel, Mux.SERVER, serverInitialInboundRation, 1024);

	this.requestDispatcher = requestDispatcher;
	this.securityContext = Security.getContext();
    }

    /**
     * Shuts down this multiplexed connection.  Requests in progress
     * will throw IOException for future I/O operations.
     *
     * On the client side, requests that were in progress will appear
     * to have been aborted with unknown partial execution status.
     *
     * @param message reason for shutdown to be included in
     * IOExceptions thrown from future I/O operations
     **/
    public void shutdown(String message) {
	synchronized (muxLock) {
	    /*
	     * Be graceful, if possible: i.e. if there are no busy sessions.
	     * REMIND: this current implementation is extremely conservative,
	     * because most previously-used sessions will still appear busy!
	     *
	     * We can only send a Shutdown message if we have already sent
	     * the ServerConnectionHeader.  The header may not have been sent
	     * if, for example, the ServerEndpointListener was closed right
	     * as a connection was accepted.  REMIND: should/could we try to
	     * send a ServerConnectionHeader here, in order to be able to
	     * send the Shutdown message?
	     */
	    if (serverConnectionReady && busySessions.isEmpty()) {
		asyncSendShutdown(null);
	    }
	    setDown(message, null);
	}
    }

    /**
     * Shuts down this multiplexed connection only if there are no
     * requests in progress (i.e. requests that have been dispatched
     * to the request dispatcher but that have not been aborted or had
     * their response fully written to the client).
     *
     * @return true if the connection was shut down (because there
     * were no requests in progress), and false otherwise
     **/
    public boolean shutdownGracefully() {
	synchronized (muxLock) {
	    if (busySessions.isEmpty()) {
		asyncSendShutdown(null);
		setDown("mux connection shut down gracefully", null);
		return true;
	    } else {
		return false;
	    }
	}
    }

    /**
     * Verifies that the calling context has all of the security
     * permissions necessary to receive a request on this connection.
     *
     * This method should be overridden by subclasses to implement the
     * desired behavior of the checkPermissions method for
     * InboundRequest instances generated for this connection.
     **/
    protected void checkPermissions() {
    }

    /**
     * Checks that the specified requirements are either fully or
     * partially satisfied by the constraints actually in force for
     * this connection, and returns any constraints that must be fully
     * or partially implemented by higher layers in order to fully
     * satisfy all of the specified requirements.
     *
     * This method should be overridden by subclasses to implement the
     * desired behavior of the checkConstraints method for
     * InboundRequest instances generated for this connection.
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
     * Populates the context collection with information representing
     * this connection (such as the client host).
     *
     * This method should be overridden by subclasses to implement the
     * desired behavior of the populateContext method for
     * InboundRequest instances generated for this connection.
     **/
    protected void populateContext(Collection context) {
    }

    /**
     * Handles message to open a new session over this connection.
     *
     * This method must NOT be invoked while synchronized on muxLock.
     **/
    void handleOpen(int sessionID) throws ProtocolException {
	assert !Thread.holdsLock(muxLock);

	Session session;
	synchronized (muxLock) {
	    if (!busySessions.get(sessionID)) {
		dispatchNewRequest(sessionID);
		return;
	    } else {
		session = (Session) sessions.get(new Integer(sessionID));
		assert session != null;
	    }
	}

	session.handleOpen();

	synchronized (muxLock) {
	    dispatchNewRequest(sessionID);
	}
    }

    private void dispatchNewRequest(int sessionID) throws ProtocolException {
	assert Thread.holdsLock(muxLock);
	if (muxDown) {
	    throw new ProtocolException(
		"connection down, cannot add new session");
	}
	/*
	 * REMIND: Here we might want to decide to reject the session,
	 * if current conditions warrant.
	 */
	final Session session = new Session(this, sessionID, Session.SERVER);
	addSession(sessionID, session);
	try {
	    userThreadPool.execute(new Runnable() {
		public void run() {
		    final InboundRequest request = session.getInboundRequest();
		    try {
			AccessController.doPrivileged(securityContext.wrap(
			    new PrivilegedAction() {
				public Object run() {
				    requestDispatcher.dispatch(request);
				    return null;
				}
			    }), securityContext.getAccessControlContext());
		    } finally {
			request.abort();
		    }
		}
	    }, "mux request dispatch");
	} catch (OutOfMemoryError e) {	// assume out of threads
	    try {
		logger.log(Level.WARNING,
			   "could not create thread for request dispatch", e);
	    } catch (Throwable t) {
	    }
	    // reject request but absorb exception to preserve connection
	    session.abort();
	}
    }
}
