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
import java.nio.channels.SocketChannel;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.export.Exporter;
import net.jini.jeri.AtomicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import org.apache.river.jeri.internal.mux.MuxClient;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * More concurrent requests to one endpoint than a single connection can carry
 * (a multiplexed connection has at most 256 sessions, the session id being an
 * unsigned byte) must all succeed: {@link ConnectionManager#connect} has to
 * check that the mux the endpoint chose still has room and reserve its slot in
 * one critical section, and open another connection when it has not.
 *
 * <p>The test fills one connection to 255 of its 256 requests with calls that
 * block in the server, then releases a burst of further callers together. They
 * all find the connection with room for one more, are all handed it by the
 * endpoint, and unless the room is checked again when the slot is reserved the
 * surplus fails with {@code IOException("no free sessions")}.
 */
public class ConnectionManagerCapacityTest {

    /** Requests held on the first connection before the burst: one slot is left. */
    private static final int FILL = 255;
    /** Callers released together once the first connection is nearly full. */
    private static final int BURST = 245;
    private static final int TOTAL = FILL + BURST;
    private static final long TIMEOUT_SECONDS = 60;

    /**
     * Remote contract: blocks in the server until the test releases it
     * (except for a negative argument, which returns at once).
     */
    public interface Gate extends Remote {
        int hold(int i) throws RemoteException;
    }

    public static final class GateImpl implements Gate {
        final AtomicInteger inside = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();
        final CountDownLatch release = new CountDownLatch(1);

        public int hold(int i) {
            if (i < 0) {
                return i;
            }
            int n = inside.incrementAndGet();
            int p;
            while (n > (p = peak.get()) && !peak.compareAndSet(p, n)) {
                // retry
            }
            try {
                release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inside.decrementAndGet();
            }
            return i;
        }
    }

    /** Counts the connections the manager opens (it logs "opened" at FINEST). */
    private static final class OpenedCounter extends Handler {
        final AtomicInteger opened = new AtomicInteger();
        @Override public void publish(LogRecord r) {
            String m = r.getMessage();
            if (m != null && m.startsWith("opened")) {
                opened.incrementAndGet();
            }
        }
        @Override public void flush() { }
        @Override public void close() { }
    }

    private Logger logger;
    private Level oldLevel;
    private final OpenedCounter counter = new OpenedCounter();

    @Before
    public void setUp() {
        logger = Logger.getLogger("net.jini.jeri.connection.ConnectionManager");
        oldLevel = logger.getLevel();
        logger.setLevel(Level.FINEST);
        logger.addHandler(counter);
    }

    @After
    public void tearDown() {
        logger.removeHandler(counter);
        logger.setLevel(oldLevel);
    }

    @Test(timeout = 180000)
    public void testMoreThanOneConnectionsWorthOfConcurrentRequests()
            throws Exception {
        final GateImpl impl = new GateImpl();
        Exporter exporter = new BasicJeriExporter(
                TcpServerEndpoint.getInstance(0),
                new AtomicILFactory(null, null,
                        ConnectionManagerCapacityTest.class.getClassLoader()),
                false, true);
        final Gate proxy = (Gate) exporter.export(impl);
        final List<Throwable> errors = new CopyOnWriteArrayList<Throwable>();
        final AtomicInteger returned = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        List<Thread> threads = new ArrayList<Thread>();
        try {
            // A negative argument returns at once: opens the first
            // connection, which the callers below all reuse.
            Assert.assertEquals(-1, proxy.hold(-1));
            Assert.assertEquals("warm-up should open one connection",
                    1, counter.opened.get());
            final GateImpl run = impl;

            // Phase 1: FILL callers, started one after the other, block in the
            // server on the one connection.
            for (int i = 0; i < FILL; i++) {
                threads.add(caller(proxy, i, null, errors, returned, failed));
            }
            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            while (run.inside.get() + failed.get() < FILL
                    && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            Assert.assertEquals("first connection should hold " + FILL
                    + " requests: " + errors, FILL, run.inside.get());
            Assert.assertEquals("the fill must not have opened a second"
                    + " connection", 1, counter.opened.get());

            // Phase 2: the burst, released together.
            CyclicBarrier barrier = new CyclicBarrier(BURST);
            for (int i = FILL; i < TOTAL; i++) {
                threads.add(caller(proxy, i, barrier, errors, returned, failed));
            }
            // All TOTAL calls are inside the server together, or the failures
            // account for the rest.
            deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            while (run.inside.get() + failed.get() < TOTAL
                    && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            int peak = run.peak.get();
            run.release.countDown();
            for (Thread t : threads) {
                t.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
                Assert.assertFalse("caller did not finish: " + t, t.isAlive());
            }
            Assert.assertEquals("errors (first: "
                    + (errors.isEmpty() ? "none" : errors.get(0)) + ")",
                    0, errors.size());
            Assert.assertEquals("all calls returned", TOTAL, returned.get());
            Assert.assertEquals("calls simultaneously in the server",
                    TOTAL, peak);
            Assert.assertTrue("more than one connection should have been"
                    + " opened, was " + counter.opened.get(),
                    counter.opened.get() > 1);
        } finally {
            exporter.unexport(true);
        }
    }

    // ------------------------------------------------------------- deterministic

    /** A connection that is never started: only the manager's bookkeeping runs. */
    private static final class FakeConnection implements Connection {
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
     * Like the TCP and UDS endpoints it hands out the first active
     * connection (else the first idle one), but, once the connection is
     * nearly full, makes the callers wait for each other inside connect, so
     * that they all choose it before any of them reserves a slot.
     */
    private static final class MeetingEndpoint implements ConnectionEndpoint {
        final CyclicBarrier meeting;
        final AtomicInteger opened = new AtomicInteger();
        volatile boolean meet;

        MeetingEndpoint(int parties) {
            // Once only: callers that go round again do not wait.
            meeting = new CyclicBarrier(parties, new Runnable() {
                public void run() { meet = false; }
            });
        }
        public Connection connect(OutboundRequestHandle h) {
            opened.incrementAndGet();
            return new FakeConnection();
        }
        public Connection connect(OutboundRequestHandle h,
                Collection active, Collection idle) {
            Collection from = !active.isEmpty() ? active : idle;
            if (from.isEmpty()) {
                return null;
            }
            if (meet) {
                try {
                    meeting.await(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // the others are not coming; proceed
                }
            }
            return (Connection) from.iterator().next();
        }
    }

    /**
     * The room on a connection is checked when the slot is reserved, not only
     * when the endpoint is offered the connection: callers that all saw room
     * for one more must not all get it.
     */
    @Test(timeout = 120000)
    public void testReservationNeverExceedsCapacity() throws Exception {
        final int callers = 64;
        final MeetingEndpoint ep = new MeetingEndpoint(callers);
        final ConnectionManager cm = new ConnectionManager(ep);
        final OutboundRequestHandle handle = new OutboundRequestHandle() { };
        final List<MuxClient> muxes = new CopyOnWriteArrayList<MuxClient>();
        try {
            // Reserve all but one slot of the first connection.
            for (int i = 0; i < MuxClient.MAX_REQUESTS - 1; i++) {
                muxes.add(cm.connect(handle));
            }
            Assert.assertEquals(1, ep.opened.get());
            ep.meet = true;
            final List<Throwable> errors = new CopyOnWriteArrayList<Throwable>();
            List<Thread> threads = new ArrayList<Thread>();
            for (int i = 0; i < callers; i++) {
                Thread t = new Thread(new Runnable() {
                    public void run() {
                        try {
                            muxes.add(cm.connect(handle));
                        } catch (Throwable e) {
                            errors.add(e);
                        }
                    }
                }, "connect-" + i);
                t.setDaemon(true);
                threads.add(t);
                t.start();
            }
            for (Thread t : threads) {
                t.join(30000);
                Assert.assertFalse("did not finish: " + t, t.isAlive());
            }
            Assert.assertEquals("errors: " + errors, 0, errors.size());
            Assert.assertEquals(MuxClient.MAX_REQUESTS - 1 + callers, muxes.size());
            int max = 0;
            for (MuxClient m : muxes) {
                max = Math.max(max, m.requestsInProgress());
            }
            Assert.assertTrue("a connection has " + max
                    + " requests reserved, the limit is " + MuxClient.MAX_REQUESTS,
                    max <= MuxClient.MAX_REQUESTS);
            Assert.assertTrue("a second connection should have been opened",
                    ep.opened.get() > 1);
        } finally {
            for (MuxClient m : muxes) {
                m.shutdown("test done");
            }
        }
    }

    private static Thread caller(final Gate proxy, final int i,
            final CyclicBarrier barrier, final List<Throwable> errors,
            final AtomicInteger returned, final AtomicInteger failed) {
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    if (barrier != null) {
                        barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    }
                    int r = proxy.hold(i);
                    if (r != i) {
                        throw new IllegalStateException(
                                "call " + i + " returned " + r);
                    }
                    returned.incrementAndGet();
                } catch (Throwable e) {
                    errors.add(e);
                    failed.incrementAndGet();
                }
            }
        }, "caller-" + i);
        t.setDaemon(true);
        t.start();
        return t;
    }
}
