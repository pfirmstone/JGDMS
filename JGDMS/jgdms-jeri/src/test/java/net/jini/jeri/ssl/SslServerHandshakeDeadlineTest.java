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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLSocket;
import javax.security.auth.Subject;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The total deadline of a server handshake, which the watchdog enforces from
 * the accept by closing the raw socket: silent, trickling and non-reading
 * peers on both server paths, the race between the watchdog and a handshake
 * that completes, and the end of a listener with handshakes in progress.
 */
public class SslServerHandshakeDeadlineTest {

    private static final String TIMEOUT_PROPERTY =
        "org.apache.river.jeri.ssl.serverHandshakeTimeout";

    private static SslEngineSvidFactory.Svid bigServer;

    @BeforeClass
    public static void setUpClass() throws Exception {
        S0Fixture.setUp();
        /* A flight of about 16 KB: more than a small socket can hold. */
        bigServer = SslEngineSvidFactory.generate(
            S0Fixture.dir, "bigserver", "CN=Reggie",
            "spiffe://example.org/reggie", 600);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        S0Fixture.tearDown();
    }

    private static S0Fixture.Listener listen(boolean socketPath, long timeoutMillis)
        throws Exception
    {
        return S0Fixture.withProperty(TIMEOUT_PROPERTY,
            Long.toString(timeoutMillis),
            () -> socketPath ? S0Fixture.listenWithSocketFactory()
                             : S0Fixture.listen());
    }

    /** A peer that sends nothing is cut off at the deadline, on both paths. */
    @Test(timeout = 60000)
    public void testSilentPeerIsCutOffAtTheDeadline() throws Exception {
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = listen(socketPath, 1000)) {
                S0Fixture.connectLegit(l); // warm up
                try (Socket s = new Socket("localhost", l.port)) {
                    long ms = S0Fixture.awaitClosed(s, 10_000);
                    Assert.assertTrue("silent peer, socketPath=" + socketPath
                        + ": closed after " + ms + " ms", ms >= 800 && ms < 3000);
                }
                Assert.assertTrue(S0Fixture.await(() -> l.handle.counters()
                    .getFailed(HandshakeCounters.Kind.TIMEOUT) == 1, 3000));
                Assert.assertEquals(0, l.handle.inProgressCount());
            }
        }
    }

    /**
     * A peer that sends a byte at a time keeps every read alive, but not the
     * handshake: the deadline is total.
     */
    @Test(timeout = 60000)
    public void testTricklingPeerIsCutOffAtTheTotalDeadline() throws Exception {
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = listen(socketPath, 1500)) {
                S0Fixture.connectLegit(l); // warm up
                try (Socket s = new Socket("localhost", l.port)) {
                    long t0 = System.nanoTime();
                    /* The header of a handshake record of 16 KB, then bytes. */
                    s.getOutputStream().write(new byte[] { 0x16, 3, 3, 0x40, 0 });
                    s.getOutputStream().flush();
                    s.setSoTimeout(300);
                    long ms = -1L;
                    byte[] one = new byte[1];
                    while (ms < 0) {
                        long elapsed = TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime() - t0);
                        if (elapsed > 8000) {
                            break;
                        }
                        try {
                            if (s.getInputStream().read(one) < 0) {
                                ms = elapsed;
                            }
                            continue;
                        } catch (java.net.SocketTimeoutException tick) {
                            // nothing yet: trickle the next byte
                        } catch (IOException reset) {
                            ms = elapsed;
                            continue;
                        }
                        try {
                            s.getOutputStream().write(1);
                            s.getOutputStream().flush();
                        } catch (IOException closed) {
                            ms = TimeUnit.NANOSECONDS.toMillis(
                                System.nanoTime() - t0);
                        }
                    }
                    Assert.assertTrue("trickling peer, socketPath=" + socketPath
                        + ": closed after " + ms + " ms", ms >= 1200 && ms < 3500);
                }
                Assert.assertTrue(S0Fixture.await(() -> l.handle.counters()
                    .getFailed(HandshakeCounters.Kind.TIMEOUT) == 1, 3000));
            }
        }
    }

    /**
     * A peer that never reads leaves the server blocked in a write of its
     * first flight, which no read deadline can end.  The listener's send
     * buffer and the peer's receive buffer are small and the flight is not.
     */
    @Test(timeout = 60000)
    public void testPeerThatNeverReadsIsCutOffAtTheDeadline() throws Exception {
        S0Fixture.ConnectionHook small = (socket, real) -> {
            socket.setSendBufferSize(2048);
            return real.call();
        };
        byte[] hello = S0Fixture.captureClientHello();
        S0Fixture.Listener l = S0Fixture.withProperty(TIMEOUT_PROPERTY, "1500",
            () -> new S0Fixture.Listener(new S0Fixture.Recorder(), null, small,
                request -> { }, bigServer));
        try {
            Socket s = new Socket();
            s.setReceiveBufferSize(2048);
            s.connect(new InetSocketAddress("localhost", l.port));
            try {
                s.getOutputStream().write(hello);
                s.getOutputStream().flush();
                Assert.assertTrue("the server must be in the handshake",
                    S0Fixture.await(() -> l.handle.inProgressCount() == 1, 3000));
                long t0 = System.nanoTime();
                Assert.assertTrue(S0Fixture.await(() -> l.handle.counters()
                    .getFailed(HandshakeCounters.Kind.TIMEOUT) == 1, 5000));
                long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
                Assert.assertTrue("cut off " + ms + " ms after it was seen "
                    + "stalled", ms < 3000);
                Assert.assertTrue(S0Fixture.await(
                    () -> l.handle.inProgressCount() == 0, 2000));
                Assert.assertEquals(0, l.recorder.count());
            } finally {
                s.close();
            }
        } finally {
            l.close();
        }
    }

    /**
     * A peer that completes the handshake and then says nothing is not the
     * watchdog's business: the connection is handed on, and stays open.
     */
    @Test(timeout = 60000)
    public void testPeerSilentAfterTlsIsNotCutOffByTheWatchdog() throws Exception {
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = listen(socketPath, 500)) {
                S0Fixture.connectLegit(l); // warm up
                try (SSLSocket s = (SSLSocket) S0Fixture.clientSocketFactory()
                         .createSocket("localhost", l.port))
                {
                    s.setSoTimeout(1500);
                    s.startHandshake();
                    s.getOutputStream().write(0);
                    try {
                        int r = s.getInputStream().read();
                        Assert.fail("the server closed a connection that had "
                            + "completed its handshake: " + r);
                    } catch (java.net.SocketTimeoutException expected) {
                        // still open after three times the deadline
                    }
                }
                Assert.assertEquals(0, l.handle.counters().getFailed());
            }
        }
    }

    /**
     * The watchdog that fires after the handshake completed cannot close the
     * connection: the task has won the race.
     */
    @Test(timeout = 60000)
    public void testLateWatchdogCannotCloseAHandedOnConnection() throws Exception {
        try (S0Fixture.Listener l = listen(false, 30_000)) {
            S0Fixture.connectLegit(l); // warm up
            AtomicReference<Boolean> late = new AtomicReference<>();
            AtomicReference<Boolean> socketClosed = new AtomicReference<>();
            l.handle.afterComplete = h -> {
                late.set(h.timedOut());
                socketClosed.set(h.socket.isClosed());
            };
            int before = l.recorder.count();
            S0Fixture.connectLegit(l);
            Assert.assertEquals(Boolean.FALSE, late.get());
            Assert.assertEquals(Boolean.FALSE, socketClosed.get());
            Assert.assertEquals(before + 1, l.recorder.count());
            SslServerEndpointImpl.SslServerConnection handed =
                (SslServerEndpointImpl.SslServerConnection)
                    l.recorder.handled.get(before);
            Assert.assertFalse("the connection must be open", handed.closed);
            Assert.assertEquals(0, l.handle.counters().getFailed());
        }
    }

    /**
     * The watchdog that fires while the handshake is completing wins: the
     * connection is not handed on and its socket is closed.
     */
    @Test(timeout = 60000)
    public void testWatchdogWinsWhenTheTimeRunsOutBeforeCompletion()
        throws Exception
    {
        try (S0Fixture.Listener l = listen(false, 800)) {
            S0Fixture.connectLegit(l); // warm up
            l.handle.beforeComplete = h -> {
                long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!h.isTimedOut() && System.nanoTime() < end) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            };
            int before = l.recorder.count();
            int registered = l.handle.connectionCount();
            try (SSLSocket s = (SSLSocket) S0Fixture.clientSocketFactory()
                     .createSocket("localhost", l.port))
            {
                s.setSoTimeout(10_000);
                s.startHandshake();
                s.getOutputStream().write(0);
                long ms = S0Fixture.awaitClosed(s, 10_000);
                Assert.assertTrue("the peer must see the connection closed", ms >= 0);
            }
            Assert.assertTrue(S0Fixture.await(() -> l.handle.counters()
                .getFailed(HandshakeCounters.Kind.TIMEOUT) == 1, 3000));
            Assert.assertEquals("the connection must not be handed on",
                                before, l.recorder.count());
            Assert.assertEquals(registered, l.handle.connectionCount());
            Assert.assertEquals(0, l.handle.inProgressCount());
        }
    }

    /**
     * Closing a listener ends handshakes that wait on silent peers within a
     * moment, leaves no descriptor open, and costs no CPU while they wait.
     */
    @Test(timeout = 60000)
    public void testListenerCloseEndsEngineHandshakesAndWaitingCostsNothing()
        throws Exception
    {
        try (S0Fixture.Listener warm = listen(false, 30_000)) {
            S0Fixture.connectLegit(warm);
        }
        S0Fixture.awaitFds(0, 500);
        int baseline = S0Fixture.openFds();
        S0Fixture.Listener l = listen(false, 30_000);
        List<Socket> silent = new ArrayList<>();
        try {
            silent.addAll(S0Fixture.openSilent(l, 100));
            Assert.assertTrue(S0Fixture.await(
                () -> l.handle.inProgressCount() == 100, 5000));
            long cpu0 = S0Fixture.processCpuNanos();
            Thread.sleep(1000);
            long cpuMs = TimeUnit.NANOSECONDS.toMillis(
                S0Fixture.processCpuNanos() - cpu0);
            Assert.assertTrue("100 silent handshakes used " + cpuMs
                + " ms of CPU in a second", cpuMs < 300);
            long t0 = System.nanoTime();
            l.close();
            Assert.assertTrue("handshakes must end at close", S0Fixture.await(
                () -> l.handle.inProgressCount() == 0, 1000));
            for (Socket s : silent) {
                Assert.assertTrue("the peer must see the connection closed",
                                  S0Fixture.awaitClosed(s, 1000) >= 0);
            }
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            Assert.assertTrue("took " + ms + " ms", ms < 1500);
        } finally {
            S0Fixture.closeAll(silent);
            l.close();
        }
        Assert.assertEquals("descriptors", baseline,
                            S0Fixture.awaitFds(baseline, 5000));
        Assert.assertEquals(100, l.handle.counters().getFailed(
            HandshakeCounters.Kind.LISTENER_CLOSED));
    }

    /**
     * On the SSLSocket path the read timeout, set from the time that is left,
     * ends a handshake that waits for a silent peer, without the watchdog.
     */
    @Test(timeout = 60000)
    public void testSocketPathReadTimeoutBoundsASilentPeer() throws Exception {
        try (S0Fixture.Listener l = S0Fixture.listenWithSocketFactory();
             java.net.ServerSocket other = new java.net.ServerSocket(0);
             Socket silent = new Socket("localhost", other.getLocalPort());
             Socket accepted = other.accept())
        {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            long t0 = System.nanoTime();
            Thread t = Thread.ofPlatform().start(() -> {
                try {
                    l.handle.serverConnection(accepted, l.handle.new Handshake(
                        accepted,
                        System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(700)));
                } catch (Throwable e) {
                    failure.set(e);
                }
            });
            t.join(5000);
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            boolean alive = t.isAlive();
            if (alive) {
                accepted.close(); // release the thread before failing
                t.join(5000);
            }
            Assert.assertFalse("the handshake must be bounded by the read "
                + "timeout", alive);
            Assert.assertTrue("ended after " + ms + " ms", ms >= 600 && ms < 3000);
            Assert.assertTrue(String.valueOf(failure.get()),
                              failure.get() instanceof IOException);
        }
    }

    /** The watchdog drops its entry for a handshake when the handshake ends. */
    @Test(timeout = 60000)
    public void testWatchdogEntriesAreRemovedWhenHandshakesEnd() throws Exception {
        try (S0Fixture.Listener l = listen(false, 60_000)) {
            S0Fixture.connectLegit(l); // warm up
            Assert.assertTrue(S0Fixture.await(
                () -> l.handle.inProgressCount() == 0, 3000));
            int baseline = HandshakeWatchdog.pending();
            for (int i = 0; i < 5; i++) {
                S0Fixture.connectLegit(l);
                S0Fixture.sendJunk(l);
            }
            Assert.assertTrue(S0Fixture.await(
                () -> l.handle.inProgressCount() == 0, 3000));
            Assert.assertEquals(baseline, HandshakeWatchdog.pending());
        }
    }

    /**
     * The watchdog thread is a daemon thread, whatever created it first, and
     * has no subject: it must not keep the subject of the thread that first
     * armed a handshake.  The class is loaded afresh, as the first use in a JVM
     * would load it.
     */
    @Test(timeout = 60000)
    public void testWatchdogThreadIsSubjectNeutral() throws Exception {
        String[] cp = System.getProperty("java.class.path")
            .split(java.io.File.pathSeparator);
        URL[] urls = new URL[cp.length];
        for (int i = 0; i < cp.length; i++) {
            urls[i] = Path.of(cp[i]).toUri().toURL();
        }
        AtomicReference<Subject> seen = new AtomicReference<>(S0Fixture.server.subject);
        AtomicReference<Thread> thread = new AtomicReference<>();
        CountDownLatch ran = new CountDownLatch(1);
        try (URLClassLoader loader =
                 new URLClassLoader(urls, ClassLoader.getPlatformClassLoader()))
        {
            Class<?> watchdog = Class.forName(
                "net.jini.jeri.ssl.HandshakeWatchdog", false, loader);
            java.lang.reflect.Method schedule = watchdog.getDeclaredMethod(
                "schedule", Runnable.class, long.class);
            schedule.setAccessible(true);
            /* Initialised by the first call, below, under the subject. */
            Subject.callAs(S0Fixture.server.subject, (Callable<Void>) () -> {
                schedule.invoke(null, (Runnable) () -> {
                    seen.set(Subject.current());
                    thread.set(Thread.currentThread());
                    ran.countDown();
                }, 1_000_000L);
                return null;
            });
            Assert.assertTrue(ran.await(10, TimeUnit.SECONDS));
            Assert.assertNull("Subject.current() on the watchdog thread", seen.get());
            Assert.assertTrue(thread.get().isDaemon());
            Assert.assertFalse(thread.get().isVirtual());
            Assert.assertSame("the context class loader of the watchdog thread",
                              loader, thread.get().getContextClassLoader());
        }
    }
}
