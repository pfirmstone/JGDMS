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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * A handshake that has written its flight waits for the client's answer for
 * a limited time, however much of the total time is left: a peer that replays
 * a ClientHello, reads the flight and stops loses its place to work after the
 * bound and not at the deadline, and so costs the server connections a
 * second at the rate of the places divided by the bound and not by the
 * deadline.
 */
public class SslServerClientFlightTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        S0Fixture.setUp();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        S0Fixture.tearDown();
    }

    private static S0Fixture.Listener listen(boolean socketPath, long timeoutMillis,
                                             long flightMillis) throws Exception
    {
        return S0Fixture.withProperties(new String[] {
                S0Fixture.HANDSHAKE_TIMEOUT, Long.toString(timeoutMillis),
                S0Fixture.FLIGHT_TIMEOUT, Long.toString(flightMillis),
                S0Fixture.MAX_HANDSHAKES, "64" },
            () -> socketPath ? S0Fixture.listenWithSocketFactory() : S0Fixture.listen());
    }

    /** Replays a ClientHello and reads the first of the server's flight. */
    private static Socket replay(S0Fixture.Listener l) throws Exception {
        Socket s = new Socket("localhost", l.port);
        s.setTcpNoDelay(true);
        s.getOutputStream().write(S0Fixture.hello());
        s.getOutputStream().flush();
        s.setSoTimeout(5000);
        Assert.assertTrue("a flight", s.getInputStream().read(new byte[4096]) > 0);
        return s;
    }

    private static long count(S0Fixture.Listener l, HandshakeCounters.Kind kind) {
        return l.handle.counters().getFailed(kind);
    }

    /** Everything a stalled handshake held is free. */
    private static void assertFree(S0Fixture.Listener l, String what) throws Exception {
        Assert.assertTrue(what + ": places and permits are back", S0Fixture.await(
            () -> l.handle.runningHandshakes() == 0
                  && l.handle.admittedConnections() == 0
                  && l.handle.inProgressCount() == 0
                  && l.handle.cpuPermitsAvailable() == l.handle.cpuPermits()
                  && l.handle.socketCpuPermitsAvailable() == l.handle.cpuPermits(), 5000));
    }

    @Test(timeout = 60000)
    public void testTheBoundAndItsDefaultAreProperties() throws Exception {
        try (S0Fixture.Listener l = S0Fixture.listen()) {
            Assert.assertEquals(3000L, l.handle.clientFlightMillis());
        }
        for (String[] c : new String[][] { { "1234", "1234" }, { "0", "0" }, { "-5", "0" } }) {
            try (S0Fixture.Listener l = S0Fixture.withProperty(
                     S0Fixture.FLIGHT_TIMEOUT, c[0], () -> S0Fixture.listen()))
            {
                Assert.assertEquals(c[0], Long.parseLong(c[1]), l.handle.clientFlightMillis());
            }
        }
    }

    /**
     * A peer that replays a ClientHello, reads the flight and stops is cut
     * off after the bound, counted apart from the handshakes that ran out of
     * time, and frees its place to work and its permits: the attack that
     * held a place for the whole deadline now holds it for the bound.
     */
    @Test(timeout = 60000)
    public void testReplayAndStallLosesItsPlaceAfterTheBound() throws Exception {
        long bound = 800;
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = listen(socketPath, 8000, bound)) {
                S0Fixture.connectLegit(l); // warm up
                for (int i = 0; i < 3; i++) {
                    long before = count(l, HandshakeCounters.Kind.FLIGHT_TIMEOUT);
                    try (Socket s = replay(l)) {
                        long ms = S0Fixture.awaitClosed(s, 7000);
                        Assert.assertTrue("socketPath=" + socketPath + ": closed after "
                            + ms + " ms, the bound is " + bound,
                            ms >= bound - 300 && ms < bound + 2000);
                    }
                    Assert.assertTrue("counted, socketPath=" + socketPath, S0Fixture.await(
                        () -> count(l, HandshakeCounters.Kind.FLIGHT_TIMEOUT) == before + 1,
                        3000));
                }
                Assert.assertEquals("not a handshake that ran out of time", 0,
                                    count(l, HandshakeCounters.Kind.TIMEOUT));
                assertFree(l, "socketPath=" + socketPath);
            }
        }
    }

    /**
     * The bound is on the time that the handshake waits for the peer, not on
     * the arrival of its first byte: a peer that sends a byte now and then
     * does not renew it.
     */
    @Test(timeout = 60000)
    public void testTricklingAnswerDoesNotRenewTheBound() throws Exception {
        long bound = 800;
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = listen(socketPath, 8000, bound)) {
                S0Fixture.connectLegit(l); // warm up
                try (Socket s = replay(l)) {
                    /* The header of a handshake record of 16384 bytes, then its bytes, slowly. */
                    s.getOutputStream().write(new byte[] { 22, 3, 3, 0x40, 0 });
                    s.getOutputStream().flush();
                    long t0 = System.nanoTime();
                    Thread trickle = Thread.ofPlatform().start(() -> {
                        try {
                            for (int i = 0; i < 20; i++) {
                                Thread.sleep(200);
                                s.getOutputStream().write(0);
                                s.getOutputStream().flush();
                            }
                        } catch (IOException | InterruptedException ignore) {
                        }
                    });
                    long ms = S0Fixture.awaitClosed(s, 7000);
                    long total = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - t0);
                    trickle.interrupt();
                    trickle.join(2000);
                    Assert.assertTrue("socketPath=" + socketPath + ": closed after " + total
                        + " ms with a byte every 200 ms, the bound is " + bound,
                        ms >= 0 && total < bound + 2000);
                }
                Assert.assertTrue(S0Fixture.await(() -> count(l,
                    HandshakeCounters.Kind.FLIGHT_TIMEOUT) == 1, 3000));
                assertFree(l, "socketPath=" + socketPath);
            }
        }
    }

    /** Delays the first bytes that the client sends after its ClientHello. */
    private static final class DelayingProxy implements AutoCloseable {
        final ServerSocket server;
        final int port;
        private final List<Socket> sockets = new ArrayList<>();

        DelayingProxy(int target, long delayMillis) throws IOException {
            server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
            port = server.getLocalPort();
            Thread.ofPlatform().daemon().start(() -> {
                try {
                    Socket client = server.accept();
                    Socket upstream = new Socket("localhost", target);
                    client.setTcpNoDelay(true);
                    upstream.setTcpNoDelay(true);
                    synchronized (sockets) {
                        sockets.add(client);
                        sockets.add(upstream);
                    }
                    Thread.ofPlatform().daemon().start(() -> copy(upstream, client, 0));
                    copy(client, upstream, delayMillis);
                } catch (IOException ignore) {
                }
            });
        }

        private void copy(Socket from, Socket to, long delayMillis) {
            try {
                InputStream in = from.getInputStream();
                OutputStream out = to.getOutputStream();
                byte[] buf = new byte[16384];
                int n;
                int chunk = 0;
                while ((n = in.read(buf)) >= 0) {
                    if (chunk++ == 1 && delayMillis > 0) {
                        Thread.sleep(delayMillis);
                    }
                    out.write(buf, 0, n);
                    out.flush();
                }
            } catch (IOException | InterruptedException ignore) {
            } finally {
                /* One side has gone: so has the other, as through a real middlebox. */
                try { from.close(); } catch (IOException ignore) { }
                try { to.close(); } catch (IOException ignore) { }
            }
        }

        public void close() throws IOException {
            server.close();
            synchronized (sockets) {
                for (Socket s : sockets) {
                    s.close();
                }
            }
        }
    }

    /** Opens a TLS connection through the proxy; returns whether the listener handed it on. */
    private static boolean connectThrough(S0Fixture.Listener l, long delayMillis)
        throws Exception
    {
        int before = l.recorder.count();
        try (DelayingProxy proxy = new DelayingProxy(l.port, delayMillis);
             javax.net.ssl.SSLSocket s = (javax.net.ssl.SSLSocket)
                 S0Fixture.clientSocketFactory().createSocket("localhost", proxy.port))
        {
            s.setSoTimeout(10_000);
            try {
                s.startHandshake();
                s.getOutputStream().write(0);
                s.getOutputStream().flush();
            } catch (IOException refused) {
                return false;
            }
            return l.recorder.await(before + 1, 5000);
        }
    }

    /**
     * The bound is for a client that answers: one whose second flight is late
     * by less than the bound still completes, and one that is later is cut off.
     */
    @Test(timeout = 60000)
    public void testASlowClientThatAnswersInTimeCompletes() throws Exception {
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = listen(socketPath, 10_000, 1500)) {
                S0Fixture.connectLegit(l); // warm up
                Assert.assertTrue("a client 600 ms late, socketPath=" + socketPath,
                                  connectThrough(l, 600));
                Assert.assertEquals(0, l.handle.counters().getFailed());
            }
            try (S0Fixture.Listener l = listen(socketPath, 10_000, 500)) {
                S0Fixture.connectLegit(l); // warm up
                Assert.assertFalse("a client 1500 ms late, socketPath=" + socketPath,
                                   connectThrough(l, 1500));
                Assert.assertTrue(S0Fixture.await(() -> count(l,
                    HandshakeCounters.Kind.FLIGHT_TIMEOUT) == 1, 3000));
            }
        }
    }

    /**
     * With the bound off (0) the handshake waits as long as the deadline
     * allows; and a deadline that is nearer than the bound ends the wait, as a
     * handshake that ran out of time.
     */
    @Test(timeout = 60000)
    public void testTheTotalDeadlineStillEndsAHandshakeWhenTheBoundIsOffOrFarther()
        throws Exception
    {
        for (boolean socketPath : new boolean[] { false, true }) {
            for (long bound : new long[] { 0, 30_000 }) {
                try (S0Fixture.Listener l = listen(socketPath, 2400, bound)) {
                    S0Fixture.connectLegit(l); // warm up
                    try (Socket s = replay(l)) {
                        long ms = S0Fixture.awaitClosed(s, 7000);
                        Assert.assertTrue("socketPath=" + socketPath + " bound " + bound
                            + ": closed after " + ms + " ms", ms >= 1800 && ms < 4500);
                    }
                    Assert.assertTrue(S0Fixture.await(() -> count(l,
                        HandshakeCounters.Kind.TIMEOUT) == 1, 3000));
                    Assert.assertEquals(0, count(l, HandshakeCounters.Kind.FLIGHT_TIMEOUT));
                    assertFree(l, "socketPath=" + socketPath);
                }
            }
        }
    }
}
