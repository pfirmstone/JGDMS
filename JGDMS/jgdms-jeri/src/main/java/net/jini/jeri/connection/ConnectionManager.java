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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * 
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
     * All the OutboundMuxes, in the order they were added. Guarded by this.
     */
    private final Set<OutboundMux> muxes = new LinkedHashSet<OutboundMux>();
    /**
     * The muxes that may have room for another request, the most recently
     * added or freed first, so that requests concentrate on few connections.
     * It is what {@link #connect} offers the endpoint, so that a call does
     * not have to look at every mux. An entry can be stale (the mux has been
     * removed, has gone down or has filled up since); {@link #offer} drops
     * those as it meets them. Guarded by this.
     */
    private final ArrayDeque<OutboundMux> available
            = new ArrayDeque<OutboundMux>();
    /**
     * Muxes that were full and have since lost a request, put here without
     * taking this monitor by the thread that ended the request; {@link
     * #connect} moves them back to {@link #available}. At most one entry
     * per mux.
     */
    private final ConcurrentLinkedQueue<OutboundMux> freed
            = new ConcurrentLinkedQueue<OutboundMux>();

    /**
     * Upper bound on the passes {@link #connect} makes when the mux chosen by
     * the endpoint has filled up or been removed in the meantime, after
     * which it opens a new connection. A safety net: each pass excludes at
     * least one mux.
     */
    private static final int MAX_CONNECT_ATTEMPTS = 16;

    /**
     * The most muxes offered to the endpoint in one pass. The endpoint
     * chooses among the muxes it is offered; the ones that matter are the
     * first few, and the cost of a call must not grow with the number of
     * connections. If the endpoint finds none of them suitable, and there
     * are more, it is offered all of them (see {@link #offer}).
     */
    private static final int OFFER_WINDOW = 4;

    /**
     * Number of pending connect calls.
     */
    private int pendingConnects = 0;

    /**
     * True if this manager is in (or on its way into) reaperSet. Guarded by
     * this; set false by checkIdle when it tells the reaper to drop the
     * manager.
     */
    private boolean registered = false;

    /**
     * Number of new connections being opened by connect calls. Guarded by
     * this. Callers that find no mux with room wait while this is non-zero
     * instead of opening a connection of their own, so that a burst of
     * callers on a cold manager opens a few connections, not one each.
     */
    private int opening = 0;
    /**
     * Number of opens that have finished, successfully or not. Guarded by
     * this.
     */
    private long openEpoch = 0;
    /**
     * Number of opens that have failed. Guarded by this.
     */
    private long openFailures = 0;

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
     * The muxes offered to the endpoint in one pass: the connections in the
     * two lists the endpoint is given, and the muxes they belong to.
     */
    private static final class Offer {
        final List<Connection> active = new ArrayList<Connection>(OFFER_WINDOW);
        final List<Connection> idle = new ArrayList<Connection>(OFFER_WINDOW);
        final List<OutboundMux> offered = new ArrayList<OutboundMux>(OFFER_WINDOW);
        /** True if there were muxes with room that were not offered. */
        boolean truncated;

        /** The mux offered with connection c, or null if c was not offered. */
        OutboundMux find(Connection c) {
            for (int i = 0, n = offered.size(); i < n; i++) {
                if (offered.get(i).getConnection() == c) {
                    return offered.get(i);
                }
            }
            return null;
        }
    }

    /**
     * Chooses a mux for a new request, reserves a slot on it and returns it.
     * Calls connect on the connection endpoint with the active and idle muxes
     * that have room (the first few of them, see {@link #OFFER_WINDOW}) and
     * the specified handle. If no connection is returned, calls connect with
     * the handle. In either case, if a new connection is returned, creates
     * and adds a mux for it. In all cases, bumps the newRequest count for the
     * mux and returns it. Removes any dead muxes along the way.
     *
     * <p>The cost of the common path does not depend on the number of muxes.
     * The monitor is not held while the endpoint is called or while a
     * connection is opened. At most one connection is being opened at a
     * time by callers that find no mux with room: the others wait for it,
     * and use it when it is there (unless the open fails, in which case they
     * open their own, at once and together).
     */
    OutboundMux connect(OutboundRequestHandle handle) throws IOException {
        boolean counted = false;    // pendingConnects includes this call
        boolean interrupted = false;
        try {
            /*
             * Which mux the endpoint is offered is decided under the lock, the
             * endpoint is called outside it, so by the time it has chosen a
             * mux other threads may have filled it, or the reaper may have
             * removed it. The room is therefore checked again, together with
             * the reservation, in one critical section, and only a mux still
             * in the manager is used; if the mux chosen has meanwhile filled
             * up, or is gone, go round again. Do NOT reduce this to checking
             * only when offering: with many concurrent callers all of them
             * pass a check made there (e.g. at 255 of 256), are all handed the
             * same mux, and the surplus fails with "no free sessions". And do
             * NOT make a mux of a connection the endpoint chose that is no
             * longer in the manager: the reaper may have closed it, or be
             * about to.
             */
            int attempts = 0;
            boolean everything = false;     // offer every mux with room
            boolean dontWait = false;       // an open this call waited for failed
            for (;;) {
                Offer offer = null;
                if (attempts < MAX_CONNECT_ATTEMPTS) {
                    boolean register = false;
                    synchronized (this) {
                        if (!counted) {
                            pendingConnects++;
                            counted = true;
                            register = !registered;
                            registered = true;
                        }
                        offer = offer(everything);
                        if (offer.offered.isEmpty() && opening > 0 && !dontWait) {
                            long epoch = openEpoch, failures = openFailures;
                            interrupted |= awaitOpen(epoch);
                            dontWait = openFailures != failures;
                            // the registration, if due, is made below
                            offer = null;
                        }
                    }
                    if (register) {
                        register();
                    }
                    if (offer == null) {
                        continue;
                    }
                    Connection c = ep.connect(handle, offer.active, offer.idle);
                    if (c != null) {
                        OutboundMux mux = offer.find(c);
                        if (mux == null) {
                            // A connection the endpoint opened for this request.
                            OutboundMux added = adopt(newOutboundMux(c));
                            counted = false;
                            return added;
                        }
                        synchronized (this) {
                            if (reserve(mux)) {
                                pendingConnects--;
                                counted = false;
                                if (logger.isLoggable(Level.FINEST)) {
                                    logger.log(Level.FINEST, "using {0}", c);
                                }
                                return mux;
                            }
                        }
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST,
                                    "{0} is full or gone, retrying", c);
                        }
                        attempts++;
                        continue;
                    }
                    if (offer.truncated && !everything) {
                        // None of the first few is suitable: ask about all.
                        everything = true;
                        continue;
                    }
                }
                /*
                 * No mux to use: open a connection. Callers that get here
                 * while another is opening one wait for it, then look again.
                 */
                synchronized (this) {
                    if (opening > 0 && !dontWait) {
                        long epoch = openEpoch, failures = openFailures;
                        interrupted |= awaitOpen(epoch);
                        dontWait = openFailures != failures;
                        continue;
                    }
                    opening++;
                }
                OutboundMux mux = null;
                try {
                    mux = newOutboundMux(ep.connect(handle));
                } finally {
                    synchronized (this) {
                        opening--;
                        openEpoch++;
                        if (mux == null) {
                            openFailures++;
                        }
                        notifyAll();
                    }
                }
                OutboundMux added = adopt(mux);
                counted = false;
                return added;
            }
        } finally {
            if (counted) {
                synchronized (this) {
                    assert pendingConnects > 0;
                    pendingConnects--;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Makes sure the reaper knows of this manager; starts it if need be.
     */
    private void register() {
        boolean ok = false;
        try {
            synchronized (reaperSet) {
                if (reaperSet.isEmpty()) {
                    systemThreadPool.execute(
                            new Reaper(), "ConnectionManager.Reaper");
                }
                reaperSet.add(this);
            }
            ok = true;
        } finally {
            if (!ok) {
                synchronized (this) {
                    registered = false;
                }
            }
        }
    }

    /**
     * Adds a mux for a connection opened for a request, reserves a slot on
     * it for that request, and returns it. The pending connect count of the
     * caller is given up here, in the same critical section.
     */
    private synchronized OutboundMux adopt(OutboundMux mux) {
        mux.newRequestPending();
        muxes.add(mux);
        makeAvailable(mux);
        pendingConnects--;
        return mux;
    }

    /**
     * Waits, uninterruptibly, until an open that was in progress when this
     * was called has finished or none is in progress. Returns true if the
     * thread was interrupted while waiting (the caller restores the flag
     * when it is done, so as not to be woken at once again). Holds this.
     */
    private boolean awaitOpen(long epoch) {
        assert Thread.holdsLock(this);
        boolean interrupted = false;
        while (openEpoch == epoch && opening > 0) {
            try {
                wait();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        return interrupted;
    }

    /**
     * Builds the offer for the endpoint from the muxes that have room, the
     * most recent first, at most OFFER_WINDOW of them unless everything is
     * true. Muxes met on the way that have gone down, were removed or have
     * filled up are dropped from the available muxes. Holds this.
     */
    private Offer offer(boolean everything) {
        assert Thread.holdsLock(this);
        for (OutboundMux m; (m = freed.poll()) != null;) {
            m.freedQueued.set(false);
            if (m.full && !m.removed) {
                try {
                    if (m.requestsInProgress() < OutboundMux.MAX_REQUESTS) {
                        m.full = false;
                        makeAvailable(m);
                    }
                } catch (IOException e) {
                    drop(m);
                }
            }
        }
        Offer offer = new Offer();
        for (Iterator<OutboundMux> it = available.iterator(); it.hasNext();) {
            OutboundMux m = it.next();
            int n;
            try {
                if (m.removed) {
                    it.remove();
                    m.inAvailable = false;
                    continue;
                }
                n = m.requestsInProgress();
                if (n >= OutboundMux.MAX_REQUESTS && confirmFull(m)) {
                    it.remove();
                    m.inAvailable = false;
                    continue;
                }
            } catch (IOException e) {
                it.remove();
                m.inAvailable = false;
                drop(m);
                continue;
            }
            if (!everything && offer.offered.size() == OFFER_WINDOW) {
                offer.truncated = true;
                break;
            }
            offer.offered.add(m);
            (n == 0 ? offer.idle : offer.active).add(m.getConnection());
        }
        return offer;
    }

    /**
     * Reserves a slot for a request on the mux, if it is still in this
     * manager and has room. The check and the reservation are one critical
     * section. A mux that is full leaves the available muxes; one that has
     * gone down is removed. Holds this.
     */
    private boolean reserve(OutboundMux mux) {
        assert Thread.holdsLock(this);
        if (mux.removed) {
            return false;
        }
        try {
            if (mux.requestsInProgress() >= OutboundMux.MAX_REQUESTS) {
                if (confirmFull(mux)) {
                    dropAvailable(mux);
                    return false;
                }
            }
            mux.newRequestPending();
            if (mux.requestsInProgress() >= OutboundMux.MAX_REQUESTS
                    && confirmFull(mux)) {
                dropAvailable(mux);
            }
            return true;
        } catch (IOException e) {
            drop(mux);
            return false;
        }
    }

    /**
     * Marks the mux as full, so that the end of any of its requests puts it
     * on the freed queue, and then checks again whether it is full, in case
     * a request ended before the mark was set (that one would have found the
     * mux not marked, and said nothing). Returns false if it is not full
     * after all (the mark is then cleared). Holds this.
     */
    private boolean confirmFull(OutboundMux mux) throws IOException {
        mux.full = true;
        if (mux.requestsInProgress() < OutboundMux.MAX_REQUESTS) {
            mux.full = false;
            return false;
        }
        return true;
    }

    private void makeAvailable(OutboundMux mux) {
        assert Thread.holdsLock(this);
        if (!mux.inAvailable && !mux.removed) {
            mux.inAvailable = true;
            available.addFirst(mux);
        }
    }

    private void dropAvailable(OutboundMux mux) {
        assert Thread.holdsLock(this);
        if (mux.inAvailable) {
            mux.inAvailable = false;
            available.remove(mux);
        }
    }

    /**
     * Forgets the mux (it has gone down, or is to be closed); the entry in
     * available, if any, is dropped when met. Holds this.
     */
    private void drop(OutboundMux mux) {
        assert Thread.holdsLock(this);
        muxes.remove(mux);
        mux.removed = true;
    }

    /**
     * For each mux, calls checkIdle on the mux, and if checkIdle returns true,
     * removes the mux and adds it to the idle list. Returns true if no connects
     * are pending and no muxes remain.
     */
    synchronized boolean checkIdle(long now, List<OutboundMux> idle) {
        boolean dropped = false;
        for (Iterator<OutboundMux> it = muxes.iterator(); it.hasNext();) {
            OutboundMux mux = it.next();
            if (mux.checkIdle(now)) {
                it.remove();
                mux.removed = true;
                dropped = true;
                idle.add(mux);
            }
        }
        if (dropped) {
            // Rare and off the call path: do not leave the stale entries to
            // the calls.
            for (Iterator<OutboundMux> it = available.iterator(); it.hasNext();) {
                OutboundMux mux = it.next();
                if (mux.removed) {
                    it.remove();
                    mux.inAvailable = false;
                }
            }
        }
        if (pendingConnects == 0 && muxes.isEmpty()) {
            registered = false;
            return true;
        }
        return false;
    }

    /**
     * Removes and shuts down a mux.
     */
    void remove(OutboundMux mux) {
        synchronized (this) {
            drop(mux);
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
         * True once the manager has dropped this mux (idle, down or failed).
         * Guarded by the manager's lock.
         */
        boolean removed;
        /**
         * True if the mux is in the manager's available list. Guarded by the
         * manager's lock.
         */
        boolean inAvailable;
        /**
         * True if the manager found the mux full and took it out of its
         * available list: the end of a request then puts it on the freed
         * queue. Written under the manager's lock, read by any thread.
         */
        volatile boolean full;
        /**
         * True while this mux is on the freed queue.
         */
        final AtomicBoolean freedQueued = new AtomicBoolean();

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
                noteRoom();
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        /**
         * Tells the manager that this mux, which it found full, may have room
         * again. Takes no lock, since the thread that ends a request may hold
         * any; the manager checks the count itself.
         */
        void noteRoom() {
            if (full && freedQueued.compareAndSet(false, true)) {
                manager.freed.add(this);
            }
        }

        /**
         * A request on this mux has ended.
         */
        @Override
        protected void handleSessionRemoved() {
            noteRoom();
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
