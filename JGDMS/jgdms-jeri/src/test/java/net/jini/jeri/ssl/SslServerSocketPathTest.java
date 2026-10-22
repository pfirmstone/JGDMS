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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ServerSocketFactory;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The server accept path for sockets that have no channel (HTTPS, a custom
 * server socket factory): the processor permit is taken after the peer has
 * sent a ClientHello, not before it has sent a byte, and is given back when
 * the server's first flight has been written.
 */
public class SslServerSocketPathTest {

    private static final String PERMITS = SslServerEndpointImpl
        .SslListenHandle.SERVER_CPU_PERMITS_PROPERTY;

    @BeforeClass
    public static void setUpClass() throws Exception {
        S0Fixture.setUp();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        S0Fixture.tearDown();
    }

    private static S0Fixture.Listener listen(long timeoutMillis, String... more)
        throws Exception
    {
        List<String> p = new ArrayList<>();
        p.add(S0Fixture.HANDSHAKE_TIMEOUT);
        p.add(Long.toString(timeoutMillis));
        p.addAll(java.util.Arrays.asList(more));
        return S0Fixture.withProperties(p.toArray(new String[0]),
            () -> S0Fixture.listenWithSocketFactory());
    }

    /**
     * Peers that connect and send nothing take no permit to compute: more
     * of them than there are permits do not stop a legitimate open.  (With
     * the permit taken before the first byte, two silent connects held both
     * of the default permits until the deadline, and every listener with
     * sockets of this kind in the JVM was stopped with them.)
     */
    @Test(timeout = 60000)
    public void testSilentPeersDoNotStopALegitimateOpen() throws Exception {
        try (S0Fixture.Listener l = listen(8000)) {
            S0Fixture.connectLegit(l); // warm up
            long entries = l.handle.socketCpuEntries();
            List<Socket> silent = S0Fixture.openSilent(l, l.handle.cpuPermits() + 2);
            try {
                Assert.assertTrue(S0Fixture.await(() -> l.handle.admittedConnections()
                    == silent.size(), 5000));
                Thread.sleep(300);
                Assert.assertEquals("a silent peer holds no permit to compute",
                    l.handle.cpuPermits(), l.handle.socketCpuPermitsAvailable());
                long millis = S0Fixture.connectLegit(l);
                Assert.assertTrue("a legitimate open took " + millis + " ms",
                                  millis < 3000);
                Assert.assertEquals("only the legitimate peer took a permit",
                    entries + 1, l.handle.socketCpuEntries());
            } finally {
                S0Fixture.closeAll(silent);
            }
            Assert.assertTrue(S0Fixture.await(
                () -> l.handle.inProgressCount() == 0, 5000));
        }
    }

    /** Bytes that are not TLS take no permit and are closed at once. */
    @Test(timeout = 60000)
    public void testJunkTakesNoPermitAndIsClosedAtOnce() throws Exception {
        try (S0Fixture.Listener l = listen(8000)) {
            S0Fixture.connectLegit(l); // warm up
            long entries = l.handle.socketCpuEntries();
            for (int i = 0; i < l.handle.cpuPermits() + 2; i++) {
                long t0 = System.nanoTime();
                S0Fixture.sendJunk(l);
                long millis = java.util.concurrent.TimeUnit.NANOSECONDS
                    .toMillis(System.nanoTime() - t0);
                Assert.assertTrue("junk was closed after " + millis + " ms",
                                  millis < 3000);
            }
            Assert.assertTrue(S0Fixture.await(() -> l.handle.counters()
                .getFailed(HandshakeCounters.Kind.JUNK) == l.handle.cpuPermits() + 2,
                3000));
            Assert.assertEquals(entries, l.handle.socketCpuEntries());
            Assert.assertEquals(l.handle.cpuPermits(),
                                l.handle.socketCpuPermitsAvailable());
            Assert.assertEquals(0, l.handle.runningHandshakes());
        }
    }

    /**
     * A peer that replays a ClientHello and then stalls holds a permit to
     * compute for the work of the first flight, not until the deadline: the
     * permit is back when the server waits for the peer, so stalled peers
     * cannot hold up a legitimate one.
     */
    @Test(timeout = 60000)
    public void testReplayAndStallHoldsThePermitOnlyForTheFirstFlight()
        throws Exception
    {
        byte[] hello = S0Fixture.captureClientHello("x25519");
        try (S0Fixture.Listener l = listen(8000, PERMITS, "1")) {
            S0Fixture.connectLegit(l); // warm up
            long entries = l.handle.socketCpuEntries();
            List<Socket> stalled = new ArrayList<>();
            try {
                for (int i = 0; i < 3; i++) {
                    Socket s = new Socket("localhost", l.port);
                    stalled.add(s);
                    s.getOutputStream().write(hello);
                    s.getOutputStream().flush();
                    s.setSoTimeout(5000);
                    Assert.assertTrue("a flight",
                                      s.getInputStream().read(new byte[4096]) > 0);
                    Assert.assertTrue("the permit is back while the peer stalls",
                        S0Fixture.await(() -> l.handle.socketCpuPermitsAvailable()
                                              == 1, 3000));
                }
                Assert.assertEquals("each took the permit once", entries + 3,
                                    l.handle.socketCpuEntries());
                Assert.assertEquals("and the handshakes wait for the peers", 3,
                                    l.handle.inProgressCount());
                long millis = S0Fixture.connectLegit(l);
                Assert.assertTrue("a legitimate open took " + millis + " ms",
                                  millis < 3000);
            } finally {
                S0Fixture.closeAll(stalled);
            }
            Assert.assertTrue("everything is back", S0Fixture.await(() ->
                l.handle.socketCpuPermitsAvailable() == 1
                && l.handle.inProgressCount() == 0, 10_000));
        }
    }

    /** Permits are back after hellos that are replayed and abandoned. */
    @Test(timeout = 60000)
    public void testPermitsAreRestoredAfterAbandonedHandshakes() throws Exception {
        try (S0Fixture.Listener l = listen(8000, PERMITS, "1")) {
            S0Fixture.connectLegit(l); // warm up
            byte[] hello = S0Fixture.captureClientHello("x25519");
            for (int i = 0; i < 20; i++) {
                try (Socket s = new Socket("localhost", l.port)) {
                    s.getOutputStream().write(hello);
                    s.getOutputStream().flush();
                    if (i % 2 == 0) {
                        s.setSoTimeout(2000);
                        s.getInputStream().read(new byte[4096]);
                    }
                }
            }
            S0Fixture.connectStranger(l);
            Assert.assertTrue("permits after abandoned handshakes", S0Fixture.await(
                () -> l.handle.socketCpuPermitsAvailable() == 1
                      && l.handle.inProgressCount() == 0, 10_000));
            Assert.assertEquals(0, l.handle.runningHandshakes());
        }
    }

    /** A socket whose input stream is a new buffering stream at each call. */
    private static final class BufferingSocket extends Socket {
        final AtomicInteger inputStreams;

        BufferingSocket(AtomicInteger inputStreams) {
            this.inputStreams = inputStreams;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            inputStreams.incrementAndGet();
            return new BufferedInputStream(super.getInputStream(), 8192);
        }
    }

    private static ServerSocketFactory bufferingFactory(AtomicInteger inputStreams) {
        return new ServerSocketFactory() {
            public ServerSocket createServerSocket(int port) throws IOException {
                return createServerSocket(port, 50, null);
            }

            public ServerSocket createServerSocket(int port, int backlog)
                throws IOException
            {
                return createServerSocket(port, backlog, null);
            }

            public ServerSocket createServerSocket(int port, int backlog,
                                                   InetAddress bindAddr)
                throws IOException
            {
                return new ServerSocket(port, backlog, bindAddr) {
                    @Override
                    public Socket accept() throws IOException {
                        Socket s = new BufferingSocket(inputStreams);
                        implAccept(s);
                        return s;
                    }
                };
            }
        };
    }

    /**
     * The input stream of the socket is taken once: a factory whose sockets
     * return a new stream that reads ahead for each call would otherwise lose
     * the bytes that the first one buffered.
     */
    @Test(timeout = 60000)
    public void testTheInputStreamOfASocketIsTakenOnce() throws Exception {
        AtomicInteger streams = new AtomicInteger();
        try (S0Fixture.Listener l = new S0Fixture.Listener(new S0Fixture.Recorder(),
                 bufferingFactory(streams), null))
        {
            for (int i = 0; i < 3; i++) {
                S0Fixture.connectLegit(l);
            }
            Assert.assertEquals("one stream for each connection", 3, streams.get());
            Assert.assertEquals(0, l.handle.counters().getFailed());
        }
    }

    /** Counts what the layered socket tells the handshake. */
    private static final class Told implements HandshakeSocket.Watch {
        final AtomicInteger flights = new AtomicInteger();
        final AtomicInteger reads = new AtomicInteger();
        final AtomicInteger returned = new AtomicInteger();

        public void flightWritten() {
            flights.incrementAndGet();
        }

        public void beforePeerRead() {
            reads.incrementAndGet();
        }

        public void afterPeerRead() {
            returned.incrementAndGet();
        }
    }

    /**
     * What the layered socket gives to the JDK is the socket's own, and what
     * it tells the handshake is each flight, once, by the first read after
     * the writes, and each read that waits for the peer.
     */
    @Test(timeout = 60000)
    public void testHandshakeSocketDelegatesAndTellsTheFlights() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
             Socket client = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
             Socket accepted = server.accept())
        {
            Told told = new Told();
            HandshakeSocket h = new HandshakeSocket(accepted,
                accepted.getInputStream(), accepted.getOutputStream(), told);
            Assert.assertTrue(h.isConnected());
            Assert.assertNull("no channel", h.getChannel());
            Assert.assertEquals(accepted.getPort(), h.getPort());
            Assert.assertEquals(accepted.getLocalPort(), h.getLocalPort());
            Assert.assertEquals(accepted.getInetAddress(), h.getInetAddress());
            Assert.assertSame(h.getInputStream(), h.getInputStream());
            Assert.assertSame(h.getOutputStream(), h.getOutputStream());
            h.setSoTimeout(1234);
            Assert.assertEquals(1234, accepted.getSoTimeout());
            Assert.assertEquals(1234, h.getSoTimeout());
            h.setTcpNoDelay(true);
            Assert.assertTrue(accepted.getTcpNoDelay());

            /* A read before any write is a wait for the peer, but no flight. */
            client.getOutputStream().write(new byte[] { 1, 2, 3 });
            client.getOutputStream().flush();
            Assert.assertEquals(1, h.getInputStream().read());
            Assert.assertEquals("a read before the first write", 0, told.flights.get());
            Assert.assertEquals(1, told.reads.get());
            Assert.assertEquals(1, told.returned.get());

            h.getOutputStream().write(new byte[] { 9 });
            h.getOutputStream().flush();
            Assert.assertEquals("a write is not told", 0, told.flights.get());
            client.setSoTimeout(5000);
            Assert.assertEquals(9, client.getInputStream().read());

            Assert.assertEquals(2, h.getInputStream().read());
            Assert.assertEquals("the first read after the write", 1, told.flights.get());
            Assert.assertEquals(3, h.getInputStream().read());
            Assert.assertEquals("and no more for the reads that follow", 1, told.flights.get());
            h.getOutputStream().write(new byte[] { 8 });
            h.getOutputStream().write(new byte[] { 7 });
            Assert.assertEquals(8, client.getInputStream().read());
            Assert.assertEquals(7, client.getInputStream().read());
            client.getOutputStream().write(4);
            Assert.assertEquals(4, h.getInputStream().read());
            Assert.assertEquals("a second flight", 2, told.flights.get());
            Assert.assertEquals(told.reads.get(), told.returned.get());

            h.finish();
            client.getOutputStream().write(5);
            int reads = told.reads.get();
            Assert.assertEquals(5, h.getInputStream().read());
            h.getOutputStream().write(6);
            client.getOutputStream().write(6);
            Assert.assertEquals(6, h.getInputStream().read());
            Assert.assertEquals("after the handshake nothing is told", reads, told.reads.get());
            Assert.assertEquals(2, told.flights.get());

            h.shutdownOutput();
            Assert.assertTrue(accepted.isOutputShutdown());
            Assert.assertTrue(h.isOutputShutdown());
            h.close();
            Assert.assertTrue("closing the layered socket closes the socket",
                              accepted.isClosed());
            Assert.assertTrue(h.isClosed());
        }
    }
}
