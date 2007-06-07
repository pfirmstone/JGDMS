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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SocketChannel;
import java.security.AccessController;
import java.util.Collection;
import net.jini.jeri.OutboundRequest;

/**
 * A MuxClient controls the client side of multiplexed connection.
 *
 * @author Sun Microsystems, Inc.
 **/
public class MuxClient extends Mux {

    /** initial inbound ration as client, default is 32768 */
    private static final int clientInitialInboundRation =
	((Integer) AccessController.doPrivileged(new GetIntegerAction(
	    "com.sun.jini.jeri.connection.mux.client.initialInboundRation",
	    32768))).intValue();

    /**
     * Initiates the client side of the multiplexed connection over
     * the given input/output stream pair.
     *
     * @param out the output stream of the underlying connection
     *
     * @param in the input stream of the underlying connection
     **/
    public MuxClient(OutputStream out, InputStream in) throws IOException {
	super(out, in, Mux.CLIENT, clientInitialInboundRation, 1024);
    }

    public MuxClient(SocketChannel channel) throws IOException {
	super(channel, Mux.CLIENT, clientInitialInboundRation, 1024);
    }

    /**
     * Starts a new request over this connection, returning the
     * corresponding OutboundRequest object.
     *
     * @return the OutboundRequest for the newly created request
     **/
    public OutboundRequest newRequest()	throws IOException {
	synchronized (muxLock) {
	    if (muxDown) {
		IOException ioe = new IOException(muxDownMessage);
		ioe.initCause(muxDownCause);
		throw ioe;
	    }
	    int sessionID = busySessions.nextClearBit(0);
	    if (sessionID > Mux.MAX_SESSION_ID) {
		throw new IOException("no free sessions");
	    }

	    Session session = new Session(this, sessionID, Session.CLIENT);
	    addSession(sessionID, session);
	    return session.getOutboundRequest();
	}
    }

    /**
     * Returns the current number of requests in progress over this
     * connection.
     *
     * The value is guaranteed to not increase until the next
     * invocation of the newRequest method.
     *
     * @return the number of requests in progress over this connection
     *
     * @throws IOException if the multiplexed connection is no longer
     * active
     **/
    public int requestsInProgress() throws IOException {
	synchronized (muxLock) {
	    if (muxDown) {
		IOException ioe = new IOException(muxDownMessage);
		ioe.initCause(muxDownCause);
		throw ioe;
	    }
	    return busySessions.cardinality();
	}
    }

    /**
     * Shuts down this multiplexed connection.  Requests in progress
     * will throw IOException for future I/O operations.
     *
     * @param message reason for shutdown to be included in
     * IOExceptions thrown from future I/O operations
     **/
    public void shutdown(String message) {
	synchronized (muxLock) {
	    setDown(message, null);
	}
    }

    /**
     * Populates the context collection with information representing
     * this connection.
     *
     * This method should be overridden by subclasses to implement the
     * desired behavior of the populateContext method for
     * OutboundRequest instances generated for this connection.
     **/
    protected void populateContext(Collection context) {
    }
}
