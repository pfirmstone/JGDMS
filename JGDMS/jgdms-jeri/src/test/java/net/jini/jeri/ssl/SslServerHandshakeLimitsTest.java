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
package net.jini.jeri.ssl;

import java.lang.management.ManagementFactory;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The limit on the TLS handshakes that a server works on at once, the queue
 * of connections that wait for a turn, and the pause of the accept loop when
 * the queue is full.
 */
public class SslServerHandshakeLimitsTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        S0Fixture.setUp();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        S0Fixture.tearDown();
    }

    @Test
    public void testDefaultsFollowTheDescriptorLimit() {
        /* Many processors, so that the descriptors are what limits. */
        assertLimits(512, 4096, HandshakeLimits.forDescriptors(20000, 1000));
        assertLimits(512, 3584, HandshakeLimits.forDescriptors(16384, 1000));
        assertLimits(128, 128, HandshakeLimits.forDescriptors(1024, 1000));
        assertLimits(8, 8, HandshakeLimits.forDescriptors(64, 1000));
        assertLimits(1, 1, HandshakeLimits.forDescriptors(8, 1000));
        assertLimits(1, 0, HandshakeLimits.forDescriptors(1, 1000));
    }

    /**
     * The handshakes at once are at most four for each processor, and at least
     * 16 where the descriptors allow them; the queue takes up what the working
     * handshakes leave of a quarter of the descriptors.
     */
    @Test
    public void testDefaultsFollowTheProcessors() {
        assertLimits(48, 4048, HandshakeLimits.forDescriptors(16384, 12));
        assertLimits(16, 4080, HandshakeLimits.forDescriptors(16384, 4));
        assertLimits(16, 4080, HandshakeLimits.forDescriptors(16384, 1));
        assertLimits(16, 4080, HandshakeLimits.forDescriptors(16384, 0));
        assertLimits(256, 3840, HandshakeLimits.forDescriptors(16384, 64));
        assertLimits(512, 3584, HandshakeLimits.forDescriptors(16384, 128));
        assertLimits(512, 3584, HandshakeLimits.forDescriptors(16384, 4000));
        /* The descriptors still limit a machine with many processors. */
        assertLimits(128, 128, HandshakeLimits.forDescriptors(1024, 64));
        assertLimits(8, 8, HandshakeLimits.forDescriptors(64, 4));
        /* And the machine's own processors are what the default uses. */
        Assert.assertEquals(HandshakeLimits.forDescriptors(16384,
            Runtime.getRuntime().availableProcessors()).maxHandshakes,
            HandshakeLimits.forDescriptors(16384).maxHandshakes);
    }

    @Test
    public void testPropertiesOverrideTheDefaults() throws Exception {
        /* 4000 descriptors allow 500 handshakes; the processors may allow fewer. */
        HandshakeLimits for4000 = HandshakeLimits.forDescriptors(4000);
        S0Fixture.withProperties(new String[] {
                HandshakeLimits.MAX_DESCRIPTORS_PROPERTY, "4000" },
            () -> {
                Assert.assertEquals(4000L, HandshakeLimits.maxDescriptors());
                assertLimits(for4000.maxHandshakes, for4000.maxQueued,
                             HandshakeLimits.get());
                Assert.assertTrue(for4000.maxHandshakes <= 500);
                return null;
            });
        /* Explicit values are used as they are, however many the processors allow. */
        S0Fixture.withProperties(new String[] {
                HandshakeLimits.MAX_DESCRIPTORS_PROPERTY, "4000",
                S0Fixture.MAX_HANDSHAKES, "500", S0Fixture.MAX_QUEUED, "500" },
            () -> {
                assertLimits(500, 500, HandshakeLimits.get());
                return null;
            });
        S0Fixture.withProperties(new String[] {
                S0Fixture.MAX_HANDSHAKES, "7", S0Fixture.MAX_QUEUED, "0" },
            () -> {
                assertLimits(7, 0, HandshakeLimits.get());
                return null;
            });
        /* Values that cannot be used are ignored. */
        S0Fixture.withProperties(new String[] {
                HandshakeLimits.MAX_DESCRIPTORS_PROPERTY, "4000",
                S0Fixture.MAX_HANDSHAKES, "0", S0Fixture.MAX_QUEUED, "-1" },
            () -> {
                assertLimits(for4000.maxHandshakes, for4000.maxQueued,
                             HandshakeLimits.get());
                return null;
            });
        /* What the operating system reports is the default. */
        long reported = HandshakeLimits.maxDescriptors();
        Assert.assertTrue(reported >= 1);
        HandshakeLimits defaults = HandshakeLimits.get();
        assertLimits(HandshakeLimits.forDescriptors(reported).maxHandshakes,
                     HandshakeLimits.forDescriptors(reported).maxQueued,
                     defaults);
    }

    /**
     * The sockets admitted are the working and the waiting together, and the
     * permits for them are an int: a limit near the largest int, with the
     * queue at its default or at its own largest, must not overflow it, which
     * would leave a listener that never accepts.
     */
    @Test(timeout = 60000)
    public void testHugeLimitsDoNotOverflowTheAdmission() throws Exception {
        String max = Integer.toString(Integer.MAX_VALUE);
        for (String queue : new String[] { "-1", "4096", max }) {
            S0Fixture.withProperties(new String[] {
                    S0Fixture.MAX_HANDSHAKES, max, S0Fixture.MAX_QUEUED, queue },
                () -> {
                    HandshakeLimits l = HandshakeLimits.get();
                    Assert.assertEquals(Integer.MAX_VALUE, l.maxHandshakes);
                    Assert.assertTrue("queue " + l.maxQueued, l.maxQueued >= 0);
                    Assert.assertTrue("no overflow: " + l.maxHandshakes + " + "
                        + l.maxQueued, (long) l.maxHandshakes + l.maxQueued
                        <= Integer.MAX_VALUE);
                    Assert.assertEquals((long) l.maxHandshakes + l.maxQueued,
                                        l.admission.availablePermits());
                    return null;
                });
        }
        /* And a listener with them accepts. */
        try (S0Fixture.Listener l = S0Fixture.listen(Integer.MAX_VALUE, -1, 30_000, null, false)) {
            Assert.assertTrue(l.handle.limits().admission.availablePermits() > 0);
            S0Fixture.connectLegit(l);
        }
        /* A low working limit with a huge queue is clamped the same way. */
        S0Fixture.withProperties(new String[] {
                S0Fixture.MAX_HANDSHAKES, "5", S0Fixture.MAX_QUEUED, max },
            () -> {
                HandshakeLimits l = HandshakeLimits.get();
                Assert.assertEquals(5, l.maxHandshakes);
                Assert.assertEquals(Integer.MAX_VALUE - 5, l.maxQueued);
                return null;
            });
    }

    private static void assertLimits(int handshakes, int queued,
                                     HandshakeLimits limits)
    {
        Assert.assertEquals("handshakes", handshakes, limits.maxHandshakes);
        Assert.assertEquals("queued", queued, limits.maxQueued);
    }

    /** Counts the connections that were given a permit and built. */
    private static final class Built implements S0Fixture.ConnectionHook {
        final AtomicInteger count = new AtomicInteger();

        public SslServerEndpointImpl.SslServerConnection serverConnection(
            Socket socket,
            java.util.concurrent.Callable<SslServerEndpointImpl.SslServerConnection> real)
            throws Exception
        {
            count.incrementAndGet();
            return real.call();
        }
    }

    /**
     * With three handshakes at a time, the fourth connection waits, and
     * nothing is allocated for it; it is served when a handshake ends.
     */
    @Test(timeout = 60000)
    public void testTheConnectionAfterTheLimitWaitsForATurn() throws Exception {
        for (boolean socketPath : new boolean[] { false, true }) {
            Built built = new Built();
            try (S0Fixture.Listener l =
                     S0Fixture.listen(3, 10, 30_000, built, socketPath))
            {
                S0Fixture.connectLegit(l); // warm up
                int warm = built.count.get();
                List<Socket> silent = S0Fixture.openStuck(l, 4);
                try {
                    Assert.assertTrue(S0Fixture.await(
                        () -> l.handle.admittedConnections() == 4, 5000));
                    Assert.assertTrue(S0Fixture.await(
                        () -> l.handle.runningHandshakes() == 3, 5000));
                    Thread.sleep(300);
                    Assert.assertEquals("connections built for the three that "
                        + "work, none for the one that waits, socketPath="
                        + socketPath, warm + 3, built.count.get());

                    /* A legitimate peer is the fifth, and waits behind them. */
                    AtomicReference<Throwable> failure = new AtomicReference<>();
                    AtomicReference<Long> took = new AtomicReference<>();
                    Thread legit = Thread.ofPlatform().start(() -> {
                        try {
                            took.set(S0Fixture.connectLegit(l));
                        } catch (Throwable t) {
                            failure.set(t);
                        }
                    });
                    Assert.assertTrue(S0Fixture.await(
                        () -> l.handle.admittedConnections() == 5, 5000));
                    Thread.sleep(300);
                    Assert.assertEquals("the legitimate peer waits", 1,
                        l.recorder.count());
                    /* The handshakes end; the legitimate one takes its turn. */
                    for (Socket peer : silent) {
                        peer.close();
                    }
                    legit.join(20_000);
                    if (failure.get() != null) {
                        throw new AssertionError(failure.get());
                    }
                    Assert.assertFalse(legit.isAlive());
                    Assert.assertEquals(2, l.recorder.count());
                } finally {
                    S0Fixture.closeAll(silent);
                }
                Assert.assertTrue(S0Fixture.await(
                    () -> l.handle.runningHandshakes() == 0
                          && l.handle.admittedConnections() == 0, 5000));
            }
        }
    }

    /**
     * When the queue is full the accept loop stops accepting: the connections
     * that arrive are not accepted and closed, they wait in the operating
     * system's queue and are accepted when there is room.
     */
    @Test(timeout = 60000)
    public void testAcceptPausesWhenTheQueueIsFullAndResumes() throws Exception {
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l =
                     S0Fixture.listen(2, 2, 30_000, null, socketPath))
            {
                S0Fixture.connectLegit(l); // warm up
                long accepted0 = l.handle.counters().getAccepted();
                List<Socket> silent = S0Fixture.openSilent(l, 6);
                try {
                    Assert.assertTrue(S0Fixture.await(() ->
                        l.handle.counters().getAccepted() == accepted0 + 4, 5000));
                    Thread.sleep(500);
                    Assert.assertEquals("accepted while the queue is full, "
                        + "socketPath=" + socketPath, accepted0 + 4,
                        l.handle.counters().getAccepted());
                    Assert.assertEquals(4, l.handle.admittedConnections());
                    Assert.assertTrue(l.handle.acceptThread().isAlive());
                    /* The fifth and sixth are not closed by the server. */
                    Assert.assertEquals("a connection that waits for the "
                        + "accept loop must not be closed", -1L,
                        S0Fixture.awaitClosed(silent.get(4), 300));
                    Assert.assertEquals(-1L,
                        S0Fixture.awaitClosed(silent.get(5), 300));

                    /*
                     * Room appears when handshakes end.  A connection that
                     * waits for a turn does not notice that its peer has
                     * gone until it has one, so close all four: those that
                     * work end at once, and the others follow.
                     */
                    for (int i = 0; i < 4; i++) {
                        silent.get(i).close();
                    }
                    Assert.assertTrue("accepted " + (l.handle.counters().getAccepted() - accepted0)
                        + " admitted " + l.handle.admittedConnections()
                        + " running " + l.handle.runningHandshakes()
                        + " socketPath=" + socketPath, S0Fixture.await(() ->
                        l.handle.counters().getAccepted() == accepted0 + 6, 5000));
                    /* And the accept loop serves a peer again. */
                    for (int i = 4; i < 6; i++) {
                        silent.get(i).close();
                    }
                    long ms = S0Fixture.connectLegit(l);
                    Assert.assertTrue("took " + ms + " ms", ms < 2000);
                } finally {
                    S0Fixture.closeAll(silent);
                }
            }
        }
    }

    /**
     * The deadline counts from the accept: a connection that waited for a turn
     * does not get a new full deadline when it has one.  The time that a
     * handshake needs (here half the deadline of 2400 ms) must be left for it
     * to be given a turn: a connection that waited for the one that works is
     * dropped when that falls short, at no cost, and is not counted as one
     * that timed out in its work.
     */
    @Test(timeout = 60000)
    public void testTheDeadlineIncludesTheWaitForATurn() throws Exception {
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = S0Fixture.listen(1, 5, 2400, null, socketPath)) {
                S0Fixture.connectLegit(l); // warm up
                List<Socket> first = S0Fixture.openStuck(l, 1);
                Assert.assertTrue(S0Fixture.await(
                    () -> l.handle.runningHandshakes() == 1, 5000));
                Thread.sleep(300);
                List<Socket> second = S0Fixture.openStuck(l, 1);
                try {
                    long ms = S0Fixture.awaitClosed(second.get(0), 10_000);
                    /*
                     * The first is cut off 2400 ms after it was accepted.  The
                     * second would then have 300 ms to work in, less than the
                     * 1200 ms that a handshake must have: it is dropped 1200 ms
                     * before its own deadline, 2400 - 1200 = 1200 ms after its
                     * connect, and does not wait for the first.
                     */
                    Assert.assertTrue("the waiting connection was closed after "
                        + ms + " ms, socketPath=" + socketPath,
                        ms >= 900 && ms < 1900);
                } finally {
                    S0Fixture.closeAll(first);
                    S0Fixture.closeAll(second);
                }
                Assert.assertTrue(S0Fixture.await(() -> l.handle.counters()
                    .getFailed(HandshakeCounters.Kind.QUEUE_TIMEOUT) == 1, 3000));
                Assert.assertEquals("nothing that worked timed out", 0,
                    l.handle.counters().getFailed(HandshakeCounters.Kind.TIMEOUT));
            }
        }
    }

    /**
     * After every kind of failure the permits are back: no handshake works,
     * nothing is admitted, and the listener serves the next peer.
     */
    @Test(timeout = 120000)
    public void testPermitsAreRestoredAfterEveryKindOfFailure() throws Exception {
        for (boolean socketPath : new boolean[] { false, true }) {
            AtomicReference<Throwable> inject = new AtomicReference<>();
            S0Fixture.ConnectionHook hook = (socket, real) -> {
                Throwable t = inject.get();
                if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                } else if (t instanceof Error) {
                    throw (Error) t;
                }
                return real.call();
            };
            S0Fixture.Listener l = S0Fixture.listen(4, 4, 1500, hook, socketPath);
            try {
                S0Fixture.connectLegit(l); // warm up
                String what = "socketPath=" + socketPath;

                S0Fixture.sendJunk(l);
                assertPermits(l, "junk, " + what);

                List<Socket> silent = S0Fixture.openSilent(l, 4);
                Assert.assertTrue(S0Fixture.await(
                    () -> l.handle.admittedConnections() == 4, 5000));
                S0Fixture.closeAll(silent);
                assertPermits(l, "peer closed, " + what);

                S0Fixture.connectStranger(l);
                assertPermits(l, "untrusted peer, " + what);

                inject.set(new IllegalStateException("injected"));
                S0Fixture.sendHello(l);
                assertPermits(l, "exception, " + what);
                inject.set(new AssertionError("injected"));
                S0Fixture.sendHello(l);
                assertPermits(l, "error, " + what);
                inject.set(null);

                /*
                 * Six peers that send a ClientHello and wait: four work and
                 * time out, two wait for a turn and are dropped when the time
                 * that is left falls short.
                 */
                List<Socket> stalled = S0Fixture.openStuck(l, 6);
                try {
                    Assert.assertTrue(S0Fixture.await(
                        () -> l.handle.admittedConnections() == 6, 5000));
                    Assert.assertTrue(S0Fixture.await(() -> l.handle.counters()
                        .getFailed(HandshakeCounters.Kind.TIMEOUT) == 4
                        && l.handle.counters()
                        .getFailed(HandshakeCounters.Kind.QUEUE_TIMEOUT) == 2, 8000));
                } finally {
                    S0Fixture.closeAll(stalled);
                }
                assertPermits(l, "timeout, " + what);

                l.recorder.failWith = new IllegalStateException("injected");
                S0Fixture.connectLegit(l);
                l.recorder.failWith = null;
                assertPermits(l, "failed hand-on, " + what);

                /* Handshakes in progress and waiting when the listener closes. */
                List<Socket> open = S0Fixture.openSilent(l, 7);
                Assert.assertTrue(S0Fixture.await(
                    () -> l.handle.admittedConnections() == 7, 5000));
                Thread acceptor = l.handle.acceptThread();
                l.close();
                assertPermits(l, "listener closed, " + what);
                /* The loop ends after the close returns, not before. */
                acceptor.join(10_000);
                Assert.assertFalse("the accept thread must end at close, " + what,
                                   acceptor.isAlive());
                S0Fixture.closeAll(open);
            } finally {
                l.close();
            }
        }
    }

    private static void assertPermits(S0Fixture.Listener l, String what)
        throws Exception
    {
        Assert.assertTrue("permits after " + what + ": running="
            + l.handle.runningHandshakes() + " admitted="
            + l.handle.admittedConnections(), S0Fixture.await(
                () -> l.handle.runningHandshakes() == 0
                      && l.handle.admittedConnections() == 0
                      && l.handle.inProgressCount() == 0, 5000));
    }

    /**
     * Closing a listener whose accept loop waits for room ends the loop.
     */
    @Test(timeout = 60000)
    public void testCloseEndsAPausedAcceptLoop() throws Exception {
        S0Fixture.Listener l = S0Fixture.listen(1, 1, 30_000, null, false);
        List<Socket> silent = S0Fixture.openSilent(l, 4);
        try {
            long accepted = l.handle.counters().getAccepted();
            Assert.assertTrue(S0Fixture.await(
                () -> l.handle.admittedConnections() == 2, 5000));
            Thread.sleep(300);
            Thread t = l.handle.acceptThread();
            Assert.assertTrue(t.isAlive());
            l.close();
            t.join(2000);
            Assert.assertFalse("the paused accept loop must end", t.isAlive());
            Assert.assertTrue(S0Fixture.await(
                () -> l.handle.admittedConnections() == 0, 3000));
        } finally {
            S0Fixture.closeAll(silent);
            l.close();
        }
    }

    /**
     * Closing a listener wakes the accept loop that waits for room and the
     * handshakes that wait for a turn, even while a handshake that works does
     * not end.
     */
    @Test(timeout = 60000)
    public void testCloseWakesWaitersWhileAHandshakeIsStuck() throws Exception {
        for (int queued : new int[] { 0, 2 }) {
            java.util.concurrent.CountDownLatch stuck =
                new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch inHook =
                new java.util.concurrent.CountDownLatch(1);
            S0Fixture.ConnectionHook hook = (socket, real) -> {
                inHook.countDown();
                stuck.await();
                return real.call();
            };
            S0Fixture.Listener l = S0Fixture.listen(1, queued, 0, hook, false);
            List<Socket> peers = new ArrayList<>();
            try {
                /* One works (and is stuck), some wait, the next is not accepted. */
                peers.addAll(S0Fixture.openStuck(l, 4));
                Assert.assertTrue(inHook.await(10, TimeUnit.SECONDS));
                Assert.assertTrue(S0Fixture.await(
                    () -> l.handle.admittedConnections() == 1 + queued, 5000));
                Thread.sleep(300);
                Assert.assertEquals(1 + queued, l.handle.admittedConnections());
                Thread acceptor = l.handle.acceptThread();
                Assert.assertTrue(acceptor.isAlive());
                l.close();
                acceptor.join(2000);
                Assert.assertFalse("the paused accept loop must end at close, "
                    + "queue " + queued, acceptor.isAlive());
                Assert.assertTrue("the handshakes that wait must end at close: "
                    + l.handle.admittedConnections(), S0Fixture.await(
                        () -> l.handle.admittedConnections() == 1, 2000));
            } finally {
                stuck.countDown();
                S0Fixture.closeAll(peers);
                l.close();
            }
            Assert.assertTrue(S0Fixture.await(
                () -> l.handle.admittedConnections() == 0
                      && l.handle.runningHandshakes() == 0, 5000));
        }
    }

    /**
     * A burst of 2000 connections that stall: the platform threads stay
     * few, engines and buffers exist only for the handshakes that work, a
     * waiting connection costs one descriptor, and everything is released when
     * the listener closes.
     */
    @Test(timeout = 120000)
    public void testBurstOfStalledConnectionsIsBounded() throws Exception {
        burst(true);
    }

    /**
     * The same burst of connections that send nothing: they cost a descriptor
     * and a thread each, wait in the queue, and none of them takes a place to
     * work on a handshake or has an engine built.
     */
    @Test(timeout = 120000)
    public void testBurstOfSilentConnectionsTakesNoPlaceToWork() throws Exception {
        burst(false);
    }

    private static void burst(boolean sendHello) throws Exception {
        Built built = new Built();
        try (S0Fixture.Listener warm = S0Fixture.listen()) {
            S0Fixture.connectLegit(warm);
        }
        int fds0 = S0Fixture.settledFds(500);
        System.gc();
        int threads0 = ManagementFactory.getThreadMXBean().getThreadCount();
        long heap0 = usedHeap();
        S0Fixture.Listener l = S0Fixture.withProperties(new String[] {
                S0Fixture.HANDSHAKE_TIMEOUT, "60000" },
            () -> new S0Fixture.Listener(new S0Fixture.Recorder(), null, built));
        List<Socket> silent = new ArrayList<>();
        try {
            int n = 2000;
            silent.addAll(sendHello ? S0Fixture.openStuck(l, n)
                                    : S0Fixture.openSilent(l, n));
            int expectedAdmitted = Math.min(n,
                l.handle.maxHandshakes() + l.handle.maxQueued());
            Assert.assertTrue(S0Fixture.await(() ->
                l.handle.admittedConnections() == expectedAdmitted, 30_000));
            Thread.sleep(500);
            int expectedWorking = sendHello
                ? Math.min(n, l.handle.maxHandshakes()) : 0;
            Assert.assertEquals(expectedWorking, l.handle.runningHandshakes());
            Assert.assertEquals("engines are built for the handshakes that work "
                + "only", expectedWorking, built.count.get());
            int threads = ManagementFactory.getThreadMXBean().getThreadCount();
            Assert.assertTrue("platform threads " + threads0 + " -> " + threads,
                              threads - threads0 < 40);
            int fds = S0Fixture.openFds();
            if (fds0 >= 0) {
                Assert.assertTrue("descriptors " + fds0 + " -> " + fds + " for "
                    + n + " connections", fds - fds0 <= 2 * n + 50);
            }
            System.gc();
            long heap = usedHeap() - heap0;
            System.out.println("burst of " + n + " stalled connections: "
                + expectedWorking + " working, " + (expectedAdmitted - expectedWorking)
                + " waiting, platform threads +" + (threads - threads0)
                + ", descriptors +" + (fds - fds0) + ", heap +"
                + heap / (1024 * 1024) + " MiB");
            Assert.assertTrue("heap grew by " + heap / (1024 * 1024) + " MiB",
                              heap < 250L * 1024 * 1024);
            long t0 = System.nanoTime();
            l.close();
            Assert.assertTrue(S0Fixture.await(() ->
                l.handle.admittedConnections() == 0
                && l.handle.inProgressCount() == 0, 10_000));
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            Assert.assertTrue("released after " + ms + " ms", ms < 5000);
        } finally {
            S0Fixture.closeAll(silent);
            l.close();
        }
        S0Fixture.assumeCounted(S0Fixture.assertFdsBack(fds0, 10_000));
    }

    private static long usedHeap() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
}
