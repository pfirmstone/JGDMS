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

import org.apache.river.action.GetLongAction;
import org.apache.river.jeri.internal.mux.MuxClient;
import org.apache.river.logging.Levels;
import org.apache.river.thread.Executor;
import org.apache.river.thread.GetThreadPoolAction;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;

/**
 * Provides client-side connection management using the <a
 * href="{@docRoot}/net/jini/jeri/connection/doc-files/mux.html">Jini extensible
 * remote invocation (Jini ERI) multiplexing protocol</a>
 * to frame and multiplex requests and responses over connections.
 *
 * <p>
 * A <code>ConnectionManager</code> is created by a connection-based
 * {@link net.jini.jeri.Endpoint} implemention to manage connections to a
 * particular {@link ConnectionEndpoint}. The {@link
 * #newRequest newRequest} method is used to send a request to the connection
 * endpoint.
 *
 * <p>
 * Each request attempt is mapped to a new <i>session</i> of the Jini ERI
 * multiplexing protocol on an established connection chosen by the
 * <code>ConnectionEndpoint</code>. Request data is written as the data sent for
 * the session, and response data is read as the data recdeived for the session.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 *
 * @org.apache.river.impl
 *
 * This implementation uses the {@link Logger} named
 * <code>net.jini.jeri.connection.ConnectionManager</code> to log information at
 * the following levels:
 *
 * <p>
 * <table summary="Describes what is logged by ConnectionManager to its logger
 * at various logging levels" border=1 cellpadding=5>
 *
 * <tr> <th> Level <th> Description
 *
 * <tr> <td> {@link Level#FINEST FINEST} <td> connection opened or reused
 *
 * </table>
 *
 * <p>
 * This implementation uses the {@link Logger} named
 * <code>net.jini.jeri.connection.mux</code> to log information at the following
 * levels:
 *
 * <p>
 * <table summary="Describes what is logged by ConnectionManager to the mux
 * logger at various logging levels" border=1 cellpadding=5>
 *
 * <tr> <th> Level <th> Description
 *
 * <tr> <td> {@link Level#WARNING WARNING} <td> unexpected exception during
 * asynchronous I/O processing, or thread creation failure
 *
 * <tr> <td> {@link Levels#HANDLED HANDLED} <td> I/O exception during
 * asynchronous I/O processing
 *
 * <tr> <td> {@link Level#FINEST FINEST} <td> detailed implementation activity
 *
 * </table>
 *
 * <p>
 * This implementation recognizes the following system properties:
 *
 * <p>
 * <ul>
 *
 * <li><code>org.apache.river.jeri.connectionTimeout</code> - Time in milliseconds
 * to leave idle client-side connections around before closing them. The default
 * value is 15000 milliseconds (15 seconds).
 *
 * <li><code>org.apache.river.jeri.handshakeTimeout</code> - Time in milliseconds
 * for client-side connections to wait for the server to acknowledge an opening
 * handshake. The default value is 15000 milliseconds (15 seconds).
 *
 * </ul>
 *
 */
public final class ConnectionManager {

    /**
     * How long to leave idle muxes around before closing them.
     */
    private static final long TIMEOUT
            = AccessController.doPrivileged(new GetLongAction(
                            "org.apache.river.jeri.connectionTimeout",
                            15000)).longValue();
    /**
     * How long to wait for a server to respond to an initial client message.
     */
    private static final long HANDSHAKE_TIMEOUT
            = AccessController.doPrivileged(new GetLongAction(
                            "org.apache.river.jeri.handshakeTimeout",
                            15000)).longValue();
    /**
     * ConnectionManager logger.
     */
    private static final Logger logger
            = Logger.getLogger("net.jini.jeri.connection.ConnectionManager");
    /**
     * Executor that executes tasks in pooled system threads.
     */
    private static final Executor systemThreadPool
            =  AccessController.doPrivileged(
                    new GetThreadPoolAction(false));
    /**
     * Set of connection managers with open or pending muxes (connections), for
     * consideration by the reaper thread.
     */
    private static final Set<ConnectionManager> reaperSet = new HashSet<ConnectionManager>();

    /**
     * The endpoint.
     */
    private final ConnectionEndpoint ep;
    /**
     * The OutboundMuxes.
     */
    private final List<OutboundMux> muxes = new ArrayList<OutboundMux>(128);

    /**
     * Number of pending connect calls.
     */
    private int pendingConnects = 0;

    /**
     * Creates a new <code>ConnectionManager</code> that manages client-side
     * connections to the specified connection endpoint.
     *
     * @param ep the connection endpoint
     *
     */
    public ConnectionManager(ConnectionEndpoint ep) {
        this.ep = ep;
    }

    /**
     * Calls connect on the connection endpoint with the active and idle muxes
     * and the specified handle. If no connection is returned, calls connect
     * with the handle. In either case, if a new connection is returned, creates
     * and adds a mux for it. In all cases, bumps the newRequest count for the
     * mux and returns it. Removes any dead muxes along the way.
     */
    OutboundMux connect(OutboundRequestHandle handle) throws IOException {
        synchronized (this) {
            pendingConnects++;
        }
        /**
         * The active muxes (during connect).
         */
        List<Connection> active = new LinkedList<Connection>();
        /**
         * The idle muxes (during connect).
         */
        List<Connection> idle = new LinkedList<Connection>();
        try {
            synchronized (reaperSet) {
                if (reaperSet.isEmpty()) {
                    systemThreadPool.execute(
                            new Reaper(), "ConnectionManager.Reaper");
                }
                reaperSet.add(this);
            }
            Connection c;
            synchronized (this) {
                for (int i = muxes.size(); --i >= 0;) {
                    OutboundMux mux = (OutboundMux) muxes.get(i);
                    try {
                        int n = mux.requestsInProgress();
                        if (n == 0) {
                            idle.add(mux.getConnection());
                        } else if (n < OutboundMux.MAX_REQUESTS) {
                            active.add(mux.getConnection());
                        }
                    } catch (IOException e) {
                        muxes.remove(i);
                    }
                }
                c = ep.connect(handle, active, idle);
                if (c != null) {
                    for (int i = muxes.size(); --i >= 0;) {
                        OutboundMux mux = (OutboundMux) muxes.get(i);
                        if (c == mux.getConnection()) {
                            if (logger.isLoggable(Level.FINEST)) {
                                logger.log(Level.FINEST, "using {0}", c);
                            }
                            mux.newRequestPending();
                            return mux;
                        }
                    }
                    OutboundMux mux = newOutboundMux(c);
                    mux.newRequestPending();
                    muxes.add(mux);
                    return mux;
                }
                c = ep.connect(handle);
                OutboundMux mux = newOutboundMux(c);
                mux.newRequestPending();
                muxes.add(mux);
                return mux;
            }
        } finally {
            synchronized (this) {
                assert pendingConnects > 0;
                pendingConnects--;
            }
        }
    }

    /**
     * For each mux, calls checkIdle on the mux, and if checkIdle returns true,
     * removes the mux and adds it to the idle list. Returns true if no connects
     * are pending and no muxes remain.
     */
    synchronized boolean checkIdle(long now, List<OutboundMux> idle) {
        for (int i = muxes.size(); --i >= 0;) {
            OutboundMux mux = muxes.get(i);
            if (mux.checkIdle(now)) {
                muxes.remove(i);
                idle.add(mux);
            }
        }
        return pendingConnects == 0 && muxes.isEmpty();
    }

    /**
     * Removes and shuts down a mux.
     */
    void remove(OutboundMux mux) {
        synchronized (this) {
            muxes.remove(mux);
        }
        mux.shutdown("writeRequestData failed");
    }

    /**
     * Constructs an OutboundMux instance from the connection.
     */
    private OutboundMux newOutboundMux(Connection c) throws IOException {
        logger.log(Level.FINEST, "opened {0}", c);
        OutboundMux mux = null;
        try {
            mux = (c.getChannel() == null)
                    ? new OutboundMux(this, c) : new OutboundMux(this, c, true);
        } finally {
            if (mux == null) {
                try {
                    c.close();
                } catch (IOException e) {
                }
            }
        }
        return mux;
    }

    /**
     * Subclass wrapper around MuxClient for outbound connections.
     */
    private static final class OutboundMux extends MuxClient {

        /**
         * ConnectionManager
         */
        private final ConnectionManager manager;
        /**
         * The outbound connection.
         */
        private final Connection c;
        /**
         * Lock to enforce single start of mux.
         */
        private final Object startLock = new Object();
        /**
         * True if the mux needs to be started.
         */
        private volatile boolean notStarted = true;
        private boolean starting = false;
        /**
         * Number of pending newRequest calls. Guarded by enclosing
         * ConnectionManager's lock.
         */
        private int pendingNewRequests = 0;
        /**
         * The time this mux was found to be idle by the Reaper thread. Set to
         * zero each time a request is initiated. Guarded by enclosing
         * ConnectionManager's lock.
         */
        private long idleTime = 0;

        /**
         * Constructs an instance from the connection's streams.
         */
        OutboundMux(ConnectionManager manager, Connection c) throws IOException {
            super(c.getOutputStream(), c.getInputStream(), HANDSHAKE_TIMEOUT);
            this.c = c;
            this.manager = manager;
        }

        /**
         * Constructs an instance from the connection's channel.
         */
        OutboundMux(ConnectionManager manager, Connection c, boolean ignore) throws IOException {
            super(c.getChannel(), HANDSHAKE_TIMEOUT);
            this.c = c;
            this.manager = manager;
        }

        /**
         * Returns the outbound connection.
         */
        Connection getConnection() {
            return c;
        }

        /**
         * Registers a pending newRequest call.
         */
        void newRequestPending() {
            synchronized (manager) {
                pendingNewRequests++;
            }
        }

        /**
         * Initiates a new request on the mux and returns it, and sets the idle
         * time to zero. Starts the mux if necessary, and decrements the pending
         * newRequest count.
         */
        @Override
        public OutboundRequest newRequest() throws IOException {
//	    assert !Thread.holdsLock(ConnectionManager.this);
            boolean interrupted = false;
            try {
                boolean start = false;
                if (notStarted) {
                    synchronized (startLock) {
                        while (starting) {
                            try {
                                startLock.wait();
                            } catch (InterruptedException ex) {
                                interrupted = true;
                            }
                        }
                        if (notStarted) {
                            starting = true;
                            start = true;
                        }
                    }
                    if (start) {
                        try {
                            start();
                        } finally {
                            synchronized (startLock) {
                                notStarted = false;
                                starting = false;
                                startLock.notifyAll();
                            }
                        }
                    }
                }
                synchronized (manager) {
                    idleTime = 0;
                    return super.newRequest();
                }
            } finally {
                synchronized (manager) {
                    assert pendingNewRequests > 0;
                    pendingNewRequests--;
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        /**
         * Returns the number of active and pending requests.
         */
        @Override
        public int requestsInProgress() throws IOException {
            synchronized (manager) {
                return super.requestsInProgress() + pendingNewRequests;
            }
        }

        /**
         * Returns true if the mux is dead, or the mux is idle and the recorded
         * idle time is more than TIMEOUT milliseconds before now, and returns
         * false otherwise. If the mux is idle and the recorded idle time is
         * zero, sets the recorded idle time to now.
         */
        boolean checkIdle(long now) {
            try {
                synchronized (manager) {
                    if (requestsInProgress() == 0) {
                        if (idleTime == 0) {
                            idleTime = now;
                        } else {
                            return now - idleTime > TIMEOUT;
                        }
                    }
                    return false;
                }
            } catch (IOException e) {
                return true;
            }
        }

        /**
         * Close the connection, so that the provider is notified.
         */
        @Override
        protected void handleDown() {
            try {
                c.close();
            } catch (IOException e) {
            }
        }

        boolean shouldRetry() {
            return false; // XXX
        }
    }

    /**
     * Outbound request wrapper around the outbound request created by the mux.
     */
    private static final class Outbound implements OutboundRequest {

        /**
         * The outbound request created by the mux.
         */
        private final OutboundRequest req;
        /**
         * The connection on which the outbound request originates.
         */
        private final Connection c;
        /**
         * The outbound request handle.
         */
        private final OutboundRequestHandle handle;
        /**
         * Wrapper around the mux's response input stream.
         */
        private final InputStream in;
        /**
         * Delivery status override from readResponseData.
         */
        private volatile boolean status = true;

        Outbound(OutboundRequest req,
                Connection c,
                OutboundRequestHandle handle) {
            this.req = req;
            this.c = c;
            this.handle = handle;
            in = new Input(handle);
        }

        /* pass-through to the underlying request */
        @Override
        public OutputStream getRequestOutputStream() {
            return req.getRequestOutputStream();
        }

        /* return the wrapper */
        @Override
        public InputStream getResponseInputStream() {
            return in;
        }

        /* delegate to the connection */
        @Override
        public void populateContext(Collection context) {
            c.populateContext(handle, context);
        }

        /* delegate to the connection */
        @Override
        public InvocationConstraints getUnfulfilledConstraints() {
            return c.getUnfulfilledConstraints(handle);
        }

        /**
         * False if readResponseData returned an exception, otherwise
         * pass-through to the underlying request.
         */
        @Override
        public boolean getDeliveryStatus() {
            return status && req.getDeliveryStatus();
        }

        /* pass-through to the underlying request */
        @Override
        public void abort() {
            req.abort();
        }

        /**
         * Wrapper for the response input stream of an outbound request, used to
         * call readResponseData on the underlying connection before subsequent
         * data is read by higher levels. Note that this class does not support
         * mark/reset.
         */
        private final class Input extends InputStream {

            /**
             * The underlying input stream from the outbound request.
             */
            private final InputStream in;
            /**
             * The handle, or null if readResponseData has been called.
             */
            private OutboundRequestHandle handle;

            Input(OutboundRequestHandle handle) {
                in = req.getResponseInputStream();
                this.handle = handle;
            }

            /**
             * Calls readResponseData on the connection, exactly once. Sets the
             * handle to null to indicate that it has been called.
             */
            private void readFirst() throws IOException {
                if (handle != null) {
                    try {
                        IOException e = c.readResponseData(handle, in);
                        if (e != null) {
                            status = false;
                            throw e;
                        }
                    } finally {
                        handle = null;
                    }
                }
            }

            /**
             * Call readFirst, then pass through.
             */
            @Override
            public int read() throws IOException {
                readFirst();
                return in.read();
            }

            /**
             * Call readFirst, then pass through.
             */
            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                readFirst();
                return in.read(b, off, len);
            }

            /**
             * Call readFirst, then pass through.
             */
            @Override
            public long skip(long n) throws IOException {
                readFirst();
                return in.skip(n);
            }

            /**
             * Call readFirst, then pass through.
             */
            @Override
            public int available() throws IOException {
                readFirst();
                return in.available();
            }

            /**
             * pass-through
             */
            @Override
            public void close() throws IOException {
                in.close();
            }
        }
    }

    /**
     * Records idle times in muxes and shuts down muxes that have been idle for
     * at least TIMEOUT milliseconds.
     */
    private static final class Reaper implements Runnable {

        Reaper() {
        }

        /**
         * Sleep for TIMEOUT milliseconds. Then call checkIdle on each manager
         * with open muxes, shutdown all of idle muxes that have been collected,
         * and if no managers with open muxes remain terminate, else repeat (go
         * back to sleep).
         */
        @Override
        public void run() {
            List idle = new ArrayList(1);
            boolean done;
            do {
                try {
                    Thread.sleep(TIMEOUT);
                } catch (InterruptedException e) {
                    return;
                }
                long now = System.currentTimeMillis();
                synchronized (reaperSet) {
                    for (Iterator iter = reaperSet.iterator();
                            iter.hasNext();) {
                        ConnectionManager mgr
                                = (ConnectionManager) iter.next();
                        if (mgr.checkIdle(now, idle)) {
                            iter.remove();
                        }
                    }
                    done = reaperSet.isEmpty();
                }
                for (int i = idle.size(); --i >= 0;) {
                    ((OutboundMux) idle.get(i)).shutdown("idle");
                }
                idle.clear();
            } while (!done);
        }
    }

    /**
     * Outbound request iterator returned by newRequest.
     */
    private static final class ReqIterator implements OutboundRequestIterator {

        /**
         * ConnectionManager
         */
        private final ConnectionManager manager;
        /**
         * The request handle.
         */
        private final OutboundRequestHandle handle;
        /**
         * True if next has not yet been called.
         */
        private boolean first = true;
        /**
         * The outbound mux from the last call to next, if any.
         */
        private OutboundMux mux;

        ReqIterator(OutboundRequestHandle handle, ConnectionManager cm) {
            this.handle = handle;
            manager = cm;
        }

        /**
         * Returns true if next has not yet been called or if the last mux
         * returned had an asynchronous close.
         */
        @Override
        public boolean hasNext() {
            return first || (mux != null && mux.shouldRetry());
        }

        /**
         * If hasNext returns true, finds the entry (if any) for the connection
         * endpoint. If no entry is found, creates one and spawns a Reaper if
         * this is the only entry. Either way, bumps the connect count for the
         * entry. Calls connect on the entry to get a mux, then calls newRequest
         * on the mux, calls writeRequestData on the connection, and returns a
         * new outbound request wrapper.
         */
        @Override
        public OutboundRequest next() throws IOException {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            first = false;
            mux = manager.connect(handle);
            OutboundRequest req = mux.newRequest();
            OutboundRequest sreq = null;
            try {
                Connection c = mux.getConnection();
                c.writeRequestData(handle, req.getRequestOutputStream());
                sreq = new Outbound(req, c, handle);
            } finally {
                if (sreq == null) {
                    manager.remove(mux);
                }
            }
            return sreq;
        }
    }

    /**
     * Returns an <code>OutboundRequestIterator</code> to use to send a new
     * request for the specified handle to this connection manager's
     * <code>ConnectionEndpoint</code>.
     *
     * <p>
     * If the <code>hasNext</code> method of the returned iterator returns
     * <code>true</code>, the <code>next</code> method behaves as follows:
     *
     * <blockquote>
     *
     * The connection endpoint's {@link
     * ConnectionEndpoint#connect(OutboundRequestHandle,Collection,Collection)
     * connect} method is invoked with any active connections that have not
     * reached their maximum number of in-progress requests, any idle
     * connections, and <code>handle</code>. If that returns <code>null</code>,
     * the endpoint's {@link
     * ConnectionEndpoint#connect(OutboundRequestHandle) connect} method is
     * invoked with <code>handle</code>. In either case, if a new connection is
     * returned, the Jini ERI multiplexing protocol is started on the connection
     * (as the client). Finally, the {@link Connection#writeRequestData
     * writeRequestData} method of the connection is invoked with
     * <code>handle</code> and the request output stream of the {@link
     * OutboundRequest} that is created for the request. If any exception is
     * thrown while obtaining a connection from the endpoint or writing the
     * request data, that exception is thrown to the caller. The
     * <code>OutboundRequest</code> returned by <code>next</code> will invoke
     * the {@link
     * Connection#readResponseData readResponseData} method of the connection
     * with the specified handle and the response input stream before any other
     * data is read from the response input stream. The {@link OutboundRequest#populateContext
     * populateContext} and {@link
     * OutboundRequest#getUnfulfilledConstraints
     * getUnfulfilledConstraints} methods of the <code>OutboundRequest</code>
     * are implemented by delegating to the corresponding method of the
     * connection passing <code>handle</code> and the other arguments (if any).
     *
     * </blockquote>
     *
     * <p>
     * The returned iterator might allow continued iteration if the connection
     * used for the most recent request attempt was shut down gracefully by the
     * server.
     *
     * @param handle a handle to identify the request in later invocations on
     * the connection endpoint and its connections
     *
     * @return an <code>OutboundRequestIterator</code> to use to send a new
     * request for the specified handle to this connection manager's
     * <code>ConnectionEndpoint</code>
     *
     * @throws NullPointerException if <code>handle</code> is <code>null</code>
     *
     */
    public OutboundRequestIterator newRequest(OutboundRequestHandle handle) {
        return new ReqIterator(handle, this);
    }
}
