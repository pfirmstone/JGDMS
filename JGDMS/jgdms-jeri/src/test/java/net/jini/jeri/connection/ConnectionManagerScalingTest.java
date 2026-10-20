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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.RequestDispatcher;
import org.apache.river.jeri.internal.mux.MuxClient;
import org.apache.river.jeri.internal.mux.MuxServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * What {@link ConnectionManager#connect} costs and how many connections it
 * opens, with fake endpoints and connections (and, where a request has to end,
 * a real multiplexed connection over a loopback socket to a real server mux):
 * <ul>
 * <li>the work of a call does not grow with the number of connections the
 * manager holds: the endpoint is offered a few of them, not all;
 * <li>a mux that was full and loses a request is found again;
 * <li>callers that find no connection with room do not each open one: a burst
 * on a cold manager opens a few, one at a time, and the callers wait for the
 * open in flight and use its connection;
 * <li>a failed open fails its own caller only, and does not stop the others
 * from opening;
 * <li>no connection ever has more than {@code MAX_REQUESTS} requests.
 * </ul>
 */
public class ConnectionManagerScalingTest {

    private static final int MAX = MuxClient.MAX_REQUESTS;
    private static final OutboundRequestHandle HANDLE
            = new OutboundRequestHandle() { };

    private final List<MuxClient> toShutDown = new CopyOnWriteArrayList<MuxClient>();
    private final List<AutoCloseable> toClose = new CopyOnWriteArrayList<AutoCloseable>();

    @After
    public void tearDown() {
        for (MuxClient m : toShutDown) {
            m.shutdown("test done");
        }
        for (AutoCloseable c : toClose) {
            try {
                c.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    /** A connection that is never started: only the manager's bookkeeping runs. */
    static class FakeConnection implements Connection {
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }
        public SocketChannel getChannel() { return null; }
        public void populateContext(OutboundRequestHandle h, Collection c) { }
        public InvocationConstraints getUnfulfilledConstraints(
                OutboundRequestHandle h) {
            return InvocationConstraints.EMPTY;
        }
        public void writeRequestData(OutboundRequestHandle h, OutputStream s) { }
        public IOException readResponseData(OutboundRequestHandle h,
                InputStream s) {
            return null;
        }
        public void close() { }
    }

    /**
     * Behaves as the TCP and UDS endpoints do (the first active connection,
     * else the first idle one) and records what it is offered.
     */
    static class RecordingEndpoint implements ConnectionEndpoint {
        final AtomicInteger opened = new AtomicInteger();
        final AtomicInteger offers = new AtomicInteger();
        final AtomicInteger offeredTotal = new AtomicInteger();
        final AtomicInteger offeredMax = new AtomicInteger();
        /** While true, refuse every offer, so that the manager opens. */
        volatile boolean refuse;

        public Connection connect(OutboundRequestHandle h) throws IOException {
            opened.incrementAndGet();
            return new FakeConnection();
        }
        public Connection connect(OutboundRequestHandle h,
                Collection active, Collection idle) {
            int n = active.size() + idle.size();
            offers.incrementAndGet();
            offeredTotal.addAndGet(n);
            int m;
            while (n > (m = offeredMax.get()) && !offeredMax.compareAndSet(m, n)) {
                // retry
            }
            if (refuse) {
                return null;
            }
            Collection from = !active.isEmpty() ? active : idle;
            return from.isEmpty() ? null : (Connection) from.iterator().next();
        }
    }

    // ------------------------------------------------------------------ cost

    /**
     * With many connections, each with room, a call is offered a small
     * number of them, however many there are: its cost does not grow with
     * the number of connections. (Counted on the endpoint: it iterates what
     * it is offered, so the offered size is the work.)
     */
    @Test(timeout = 120000)
    public void testWorkPerCallDoesNotGrowWithTheNumberOfMuxes() throws Exception {
        final RecordingEndpoint ep = new RecordingEndpoint();
        final ConnectionManager cm = new ConnectionManager(ep);
        final int muxes = 800;
        ep.refuse = true;                         // every call opens a connection
        for (int i = 0; i < muxes; i++) {
            track(cm.connect(HANDLE));
        }
        Assert.assertEquals(muxes, ep.opened.get());
        ep.refuse = false;
        ep.offers.set(0);
        ep.offeredTotal.set(0);
        ep.offeredMax.set(0);

        // 4000 calls: they fill the most recent connections in turn
        // (255 slots are left on each), without touching the other ones.
        final int calls = 4000;
        for (int i = 0; i < calls; i++) {
            track(cm.connect(HANDLE));
        }
        Assert.assertEquals("no connection should have been opened",
                muxes, ep.opened.get());
        Assert.assertTrue("a call was offered " + ep.offeredMax.get()
                + " of " + muxes + " connections; the work of a call must not"
                + " depend on their number", ep.offeredMax.get() <= 8);
        Assert.assertTrue("offered " + ep.offeredTotal.get() + " connections in"
                + " " + ep.offers.get() + " offers for " + muxes
                + " connections",
                ep.offeredTotal.get() <= 8L * ep.offers.get());
        assertWithinCapacity();
    }

    // ------------------------------------------------------------ ending

    /**
     * A connection that was full and loses a request is offered again, ahead
     * of the connection that was opened because it was full.
     */
    @Test(timeout = 120000)
    public void testMuxThatLosesARequestIsFoundAgain() throws Exception {
        LiveEndpoint ep = new LiveEndpoint();
        toClose.add(ep);
        ConnectionManager cm = new ConnectionManager(ep);
        List<OutboundRequest> requests = new ArrayList<OutboundRequest>();
        for (int i = 0; i < MAX; i++) {
            requests.add(next(cm));
        }
        Assert.assertEquals("one connection holds MAX requests",
                1, ep.opened.get());
        OutboundRequest extra = next(cm);      // does not fit: second connection
        Assert.assertEquals(2, ep.opened.get());
        Connection second = ep.connections.get(1);
        Connection first = ep.connections.get(0);

        // The first connection is full and out of the manager's list; end one
        // of its requests: the next call must take that slot rather than
        // add to the second connection (the one the endpoint would pick, as
        // the most recent with room, if the first were not offered).
        requests.get(5).abort();
        OutboundRequest again = next(cm);
        Assert.assertEquals("no connection should be opened for it",
                2, ep.opened.get());
        Assert.assertSame("the freed slot should be used",
                first, ep.lastUsed);
        Assert.assertNotSame(second, ep.lastUsed);
        again.abort();
        extra.abort();
    }

    private static OutboundRequest next(ConnectionManager cm) throws IOException {
        OutboundRequestIterator it = cm.newRequest(HANDLE);
        return it.next();
    }

    /**
     * Connections over a loopback socket to a real server mux (which never
     * sees a request: the client only opens sessions and ends them).
     */
    static final class LiveEndpoint implements ConnectionEndpoint, AutoCloseable {
        final ServerSocket server;
        final AtomicInteger opened = new AtomicInteger();
        final List<Connection> connections = new CopyOnWriteArrayList<Connection>();
        final List<Socket> sockets = new CopyOnWriteArrayList<Socket>();
        volatile Connection lastUsed;

        LiveEndpoint() throws IOException {
            server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        while (true) {
                            Socket s = server.accept();
                            sockets.add(s);
                            new MuxServer(s.getOutputStream(), s.getInputStream(),
                                    new RequestDispatcher() {
                                        public void dispatch(InboundRequest r) { }
                                    }).start();
                        }
                    } catch (IOException e) {
                        // closed
                    }
                }
            }, "LiveEndpoint server");
            t.setDaemon(true);
            t.start();
        }

        public Connection connect(OutboundRequestHandle h) throws IOException {
            final Socket s = new Socket(InetAddress.getLoopbackAddress(),
                    server.getLocalPort());
            sockets.add(s);
            opened.incrementAndGet();
            Connection c = new FakeConnection() {
                @Override public InputStream getInputStream() {
                    try {
                        return s.getInputStream();
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
                @Override public OutputStream getOutputStream() {
                    try {
                        return s.getOutputStream();
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
                @Override public void close() {
                    try {
                        s.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            };
            connections.add(c);
            return c;
        }

        public Connection connect(OutboundRequestHandle h,
                Collection active, Collection idle) {
            Collection from = !active.isEmpty() ? active : idle;
            Connection c = from.isEmpty() ? null
                    : (Connection) from.iterator().next();
            lastUsed = c;
            return c;
        }

        public void close() throws IOException {
            server.close();
            for (Socket s : sockets) {
                s.close();
            }
        }
    }

    // ------------------------------------------------------------- the reaper

    /**
     * The reaper's two passes over a manager (the Reaper thread calls
     * checkIdle on each manager, then shuts down what it was given, without
     * holding any lock). Returns the muxes to shut down.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<MuxClient> reapPasses(ConnectionManager cm) {
        List idle = new ArrayList();
        cm.checkIdle(1L, idle);                 // marks the idle muxes idle
        Assert.assertTrue(idle.isEmpty());
        cm.checkIdle(Long.MAX_VALUE / 2, idle); // long enough: they are idle
        return (List<MuxClient>) idle;
    }

    /**
     * Endpoint that, once armed, has the reaper take the idle connections out
     * of the manager after it has been offered them and before it hands one
     * back: the window in which a call has chosen a connection but not yet
     * reserved it. The shutdown that follows in the reaper is done at once
     * or, when {@code shutdownAtOnce} is false, by the test later (the
     * reaper does it outside the lock, some time after taking the muxes out).
     */
    static final class ReapingEndpoint implements ConnectionEndpoint {
        final LiveEndpoint live;
        volatile ConnectionManager cm;
        volatile boolean armed;
        volatile boolean shutdownAtOnce = true;
        volatile Connection chosen;
        volatile List<MuxClient> reaped = new ArrayList<MuxClient>();

        ReapingEndpoint(LiveEndpoint live) {
            this.live = live;
        }
        public Connection connect(OutboundRequestHandle h) throws IOException {
            return live.connect(h);
        }
        public Connection connect(OutboundRequestHandle h,
                Collection active, Collection idle) {
            Connection c = live.connect(h, active, idle);
            if (armed && c != null) {
                armed = false;
                chosen = c;
                reaped = reapPasses(cm);
                Assert.assertEquals(1, reaped.size());
                if (shutdownAtOnce) {
                    shutdownReaped();
                }
            }
            return c;
        }
        void shutdownReaped() {
            for (MuxClient m : reaped) {
                m.shutdown("idle");             // closes the connection
            }
        }
    }

    /**
     * A connection the endpoint chose from the idle ones, which the reaper
     * has taken out of the manager before the call has reserved it, is not
     * used, whether the reaper has closed it yet or not: the call goes round
     * again, and gets a working connection. (It used to wrap the connection
     * in a new mux, whose first request then failed with "Connection closed"
     * if the reaper had closed it, "channel already registered" or "server
     * sent bad magic number" if it was still in use by the old mux.)
     */
    @Test(timeout = 120000)
    public void testConnectionClosedByTheReaperAfterItWasChosenIsNotUsed()
            throws Exception {
        reapedAfterChosen(true);
    }

    @Test(timeout = 120000)
    public void testConnectionTakenOutByTheReaperAfterItWasChosenIsNotUsed()
            throws Exception {
        reapedAfterChosen(false);
    }

    private void reapedAfterChosen(boolean shutdownAtOnce) throws Exception {
        LiveEndpoint live = new LiveEndpoint();
        toClose.add(live);
        ReapingEndpoint ep = new ReapingEndpoint(live);
        ep.shutdownAtOnce = shutdownAtOnce;
        ConnectionManager cm = new ConnectionManager(ep);
        ep.cm = cm;
        OutboundRequest first = next(cm);       // opens the connection
        first.abort();                          // and leaves it idle
        Assert.assertEquals(1, live.opened.get());
        Connection old = live.connections.get(0);

        ep.armed = true;
        OutboundRequest second = next(cm);      // must not use the reaped connection
        Assert.assertSame("the endpoint chose the idle connection", old, ep.chosen);
        Assert.assertEquals("a new connection was opened", 2, live.opened.get());
        Assert.assertNotSame(old, live.connections.get(1));
        second.getRequestOutputStream().write(1);
        second.abort();
        ep.shutdownReaped();                    // the reaper, a little later
    }

    // ----------------------------------------------------------- concurrency

    /** An endpoint whose opens take a while, and which counts them. */
    static final class SlowOpenEndpoint extends RecordingEndpoint {
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger peakInFlight = new AtomicInteger();
        final long openMillis;
        /** Opens that fail, from the first. */
        final AtomicInteger failures = new AtomicInteger();

        SlowOpenEndpoint(long openMillis) {
            this.openMillis = openMillis;
        }

        @Override
        public Connection connect(OutboundRequestHandle h) throws IOException {
            int n = inFlight.incrementAndGet();
            int p;
            while (n > (p = peakInFlight.get())
                    && !peakInFlight.compareAndSet(p, n)) {
                // retry
            }
            try {
                try {
                    Thread.sleep(openMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (failures.getAndUpdate(f -> f > 0 ? f - 1 : 0) > 0) {
                    throw new IOException("open failed");
                }
                return super.connect(h);
            } finally {
                inFlight.decrementAndGet();
            }
        }
    }

    /** Runs {@code callers} threads together; collects the muxes and errors. */
    private Result burst(final ConnectionManager cm, int callers)
            throws Exception {
        final Result r = new Result();
        final CountDownLatch ready = new CountDownLatch(callers);
        final CountDownLatch go = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < callers; i++) {
            Thread t = new Thread(new Runnable() {
                public void run() {
                    ready.countDown();
                    try {
                        go.await();
                        r.muxes.add(track(cm.connect(HANDLE)));
                    } catch (Throwable e) {
                        r.errors.add(e);
                    }
                }
            }, "burst-" + i);
            t.setDaemon(true);
            threads.add(t);
            t.start();
        }
        Assert.assertTrue(ready.await(30, TimeUnit.SECONDS));
        go.countDown();
        for (Thread t : threads) {
            t.join(60000);
            Assert.assertFalse("did not finish: " + t, t.isAlive());
        }
        return r;
    }

    static final class Result {
        final List<MuxClient> muxes = new CopyOnWriteArrayList<MuxClient>();
        final List<Throwable> errors = new CopyOnWriteArrayList<Throwable>();
    }

    /**
     * A burst of callers on a cold manager: one connection is opened at a
     * time, the callers wait for it and use it, so two connections (512
     * requests) are opened, not one each. No connection is over its limit.
     */
    @Test(timeout = 120000)
    public void testColdBurstOpensFewConnections() throws Exception {
        SlowOpenEndpoint ep = new SlowOpenEndpoint(20);
        ConnectionManager cm = new ConnectionManager(ep);
        int callers = 2 * MAX;
        Result r = burst(cm, callers);
        Assert.assertEquals("errors: " + r.errors, 0, r.errors.size());
        Assert.assertEquals(callers, r.muxes.size());
        Assert.assertEquals("connections being opened at the same time",
                1, ep.peakInFlight.get());
        Assert.assertTrue("a burst of " + callers + " opened "
                + ep.opened.get() + " connections; 2 are enough",
                ep.opened.get() <= 4);
        assertWithinCapacity();
    }

    /**
     * Many threads, many rounds of: reserve, then (some) end: the limit is
     * never exceeded and every call gets a mux.
     */
    @Test(timeout = 180000)
    public void testNoMuxEverHasMoreThanTheLimit() throws Exception {
        SlowOpenEndpoint ep = new SlowOpenEndpoint(2);
        ConnectionManager cm = new ConnectionManager(ep);
        int callers = 6 * MAX;
        Result r = burst(cm, callers);
        Assert.assertEquals("errors: " + r.errors, 0, r.errors.size());
        int max = 0;
        for (MuxClient m : r.muxes) {
            max = Math.max(max, m.requestsInProgress());
        }
        Assert.assertTrue("a connection has " + max + " requests", max <= MAX);
        Assert.assertTrue("opened " + ep.opened.get() + " for " + callers
                + " callers", ep.opened.get() >= 6 && ep.opened.get() <= 10);
        assertWithinCapacity();
    }

    /**
     * An open that fails fails its own caller with its own exception; the
     * callers that waited for it open for themselves, and get their
     * connections. Nothing is left behind from the failed open.
     */
    @Test(timeout = 120000)
    public void testFailedOpenFailsOnlyItsCaller() throws Exception {
        SlowOpenEndpoint ep = new SlowOpenEndpoint(100);
        ep.failures.set(1);
        ConnectionManager cm = new ConnectionManager(ep);
        int callers = 16;
        Result r = burst(cm, callers);
        Assert.assertEquals("exactly the first open fails: " + r.errors,
                1, r.errors.size());
        Assert.assertTrue(r.errors.get(0).toString(),
                r.errors.get(0) instanceof IOException);
        Assert.assertEquals("open failed", r.errors.get(0).getMessage());
        Assert.assertEquals(callers - 1, r.muxes.size());
        Assert.assertTrue("the others should open at once, together, not"
                + " one after the other: " + ep.peakInFlight.get(),
                ep.peakInFlight.get() > 1);
        // The manager is usable.
        track(cm.connect(HANDLE));
        assertWithinCapacity();
    }

    /**
     * A caller that waits for an open in flight is not interrupted by
     * interrupts, and keeps its interrupt status.
     */
    @Test(timeout = 120000)
    public void testWaitingCallerKeepsItsInterruptStatus() throws Exception {
        final SlowOpenEndpoint ep = new SlowOpenEndpoint(300);
        final ConnectionManager cm = new ConnectionManager(ep);
        final List<Throwable> errors = new CopyOnWriteArrayList<Throwable>();
        final AtomicInteger interruptedAtEnd = new AtomicInteger();
        Thread opener = new Thread(new Runnable() {
            public void run() {
                try {
                    track(cm.connect(HANDLE));
                } catch (Throwable e) {
                    errors.add(e);
                }
            }
        });
        opener.setDaemon(true);
        opener.start();
        while (ep.inFlight.get() == 0) {
            Thread.sleep(2);
        }
        Thread waiter = new Thread(new Runnable() {
            public void run() {
                try {
                    track(cm.connect(HANDLE));
                    if (Thread.currentThread().isInterrupted()) {
                        interruptedAtEnd.incrementAndGet();
                    }
                } catch (Throwable e) {
                    errors.add(e);
                }
            }
        });
        waiter.setDaemon(true);
        waiter.start();
        Thread.sleep(50);
        waiter.interrupt();
        waiter.join(30000);
        opener.join(30000);
        Assert.assertEquals("errors: " + errors, 0, errors.size());
        Assert.assertEquals(1, ep.opened.get());
        Assert.assertEquals("interrupt status kept", 1, interruptedAtEnd.get());
    }

    // ---------------------------------------------------------------- helpers

    private final List<MuxClient> reserved = new CopyOnWriteArrayList<MuxClient>();

    private MuxClient track(MuxClient m) {
        reserved.add(m);
        toShutDown.add(m);
        return m;
    }

    /** No mux holds more requests than it can carry. */
    private void assertWithinCapacity() throws IOException {
        for (MuxClient m : reserved) {
            Assert.assertTrue("a connection has " + m.requestsInProgress()
                    + " requests reserved, the limit is " + MAX,
                    m.requestsInProgress() <= MAX);
        }
    }
}
