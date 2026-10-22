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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * A connection that waits for its turn to work on a handshake reads its first
 * TLS record meanwhile, and takes a place to work only when a ClientHello has
 * arrived: silent peers cost a place in the queue, peers that go away are
 * noticed at once, and bytes that are not TLS are dropped.
 */
public class SslServerFirstRecordTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        S0Fixture.setUp();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        S0Fixture.tearDown();
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

    /** A channel that gives the bytes it is given, in the pieces it is given. */
    private static final class Script implements ReadableByteChannel {
        private final List<byte[]> pieces = new ArrayList<>();
        private int piece;
        private int at;
        boolean eof = true;

        Script(byte[]... pieces) {
            for (byte[] p : pieces) {
                this.pieces.add(p);
            }
        }

        public int read(ByteBuffer dst) throws IOException {
            if (piece >= pieces.size()) {
                if (eof) {
                    return -1;
                }
                throw new AssertionError("read past the end of the script");
            }
            byte[] p = pieces.get(piece);
            int n = Math.min(dst.remaining(), p.length - at);
            dst.put(p, at, n);
            at += n;
            if (at == p.length) {
                piece++;
                at = 0;
            }
            return n;
        }

        int unread() {
            int n = -at;
            for (int i = piece; i < pieces.size(); i++) {
                n += pieces.get(i).length;
            }
            return n;
        }

        public boolean isOpen() {
            return true;
        }

        public void close() { }
    }

    private static byte[] record(int type, int major, int minor, int length, int first, int total) {
        byte[] r = new byte[total];
        r[0] = (byte) type;
        r[1] = (byte) major;
        r[2] = (byte) minor;
        r[3] = (byte) (length >> 8);
        r[4] = (byte) length;
        if (total > 5) {
            r[5] = (byte) first;
        }
        return r;
    }

    /** What the check accepts as the start of a ClientHello, and what it does not. */
    @Test
    public void testCheckOfTheFirstBytes() throws Exception {
        Assert.assertEquals(0, FirstRecord.check(new byte[] { 22 }, 1));
        Assert.assertEquals(0, FirstRecord.check(new byte[] { 22, 3, 1, 0 }, 4));
        Assert.assertEquals(5 + 300, FirstRecord.check(
            record(22, 3, 1, 300, 1, 6), 6));
        Assert.assertEquals(5 + 16384, FirstRecord.check(
            record(22, 3, 3, 16384, 1, 5), 5));
        /* A handshake message may be split into records of any size but 0. */
        for (int length = 1; length <= 4; length++) {
            Assert.assertEquals(5 + length, FirstRecord.check(
                record(22, 3, 1, length, 1, 6), 6));
            Assert.assertEquals(5 + length, FirstRecord.check(
                record(22, 3, 1, length, 1, 5), 5));
        }
        byte[][] junk = {
            record(23, 3, 3, 100, 1, 6),     // application data
            record(21, 3, 3, 2, 1, 6),       // alert
            record(22, 2, 0, 100, 1, 6),     // not SSL 3 or TLS
            record(22, 3, 5, 100, 1, 6),     // a version that does not exist
            record(22, 3, 1, 0, 1, 6),       // no bytes: forbidden for a handshake
            record(22, 3, 1, 0, 1, 5),       // known from the header
            record(22, 3, 1, 16385, 1, 6),   // longer than a record may be
            record(22, 3, 1, 100, 2, 6),     // a ServerHello
            "GET / HTTP/1.1\r\n".getBytes(),
        };
        for (int i = 0; i < junk.length; i++) {
            try {
                FirstRecord.check(junk[i], junk[i].length);
                Assert.fail("junk " + i + " was accepted");
            } catch (FirstRecord.NotTlsException expected) {
            }
        }
        /* Junk is seen from its first byte, without waiting for the rest. */
        try {
            FirstRecord.check("G".getBytes(), 1);
            Assert.fail("a G is not a handshake record");
        } catch (FirstRecord.NotTlsException expected) {
        }
    }

    /** The record is read in whatever pieces, and nothing after it is taken. */
    @Test
    public void testReadTakesOneRecordAndNothingMore() throws Exception {
        for (int length : new int[] { 1, 2, 3, 4, 100, 511, 512, 1500, 16384 }) {
            byte[] hello = record(22, 3, 1, length, 1, 5 + length);
            for (int i = 6; i < hello.length; i++) {
                hello[i] = (byte) i;
            }
            byte[] after = { 20, 3, 3, 0, 1, 1 };
            /* In pieces of every size from one byte up. */
            for (int piece : new int[] { 1, 2, 5, 7, 600, 100000 }) {
                List<byte[]> pieces = new ArrayList<>();
                byte[] all = new byte[hello.length + after.length];
                System.arraycopy(hello, 0, all, 0, hello.length);
                System.arraycopy(after, 0, all, hello.length, after.length);
                for (int at = 0; at < all.length; at += piece) {
                    pieces.add(java.util.Arrays.copyOfRange(all, at,
                        Math.min(all.length, at + piece)));
                }
                Script script = new Script(pieces.toArray(new byte[0][]));
                byte[] got = FirstRecord.read(script);
                Assert.assertArrayEquals("length " + length + " in pieces of "
                    + piece, hello, got);
                Assert.assertEquals("what follows the record is left to be read",
                    after.length, script.unread());
            }
        }
    }

    @Test
    public void testReadEndsAtEndOfFileOrJunk() throws Exception {
        try {
            FirstRecord.read(new Script());
            Assert.fail();
        } catch (EOFException expected) {
        }
        try {
            FirstRecord.read(new Script(new byte[] { 22, 3, 1, 1 }));
            Assert.fail();
        } catch (EOFException expected) {
        }
        try {
            FirstRecord.read(new Script(record(22, 3, 1, 100, 1, 50)));
            Assert.fail();
        } catch (EOFException expected) {
        }
        Script junk = new Script("GET / HTTP/1.1\r\n\r\n".getBytes());
        junk.eof = false;
        try {
            FirstRecord.read(junk);
            Assert.fail();
        } catch (FirstRecord.NotTlsException expected) {
        }
    }

    private static final String[] ONE_PLACE = {
        S0Fixture.MAX_HANDSHAKES, "2", S0Fixture.MAX_QUEUED, "400" };

    private static S0Fixture.Listener listen(S0Fixture.ConnectionHook hook,
                                             boolean socketPath, long timeout,
                                             String... more) throws Exception
    {
        List<String> p = new ArrayList<>(java.util.Arrays.asList(ONE_PLACE));
        p.add(S0Fixture.HANDSHAKE_TIMEOUT);
        p.add(Long.toString(timeout));
        p.addAll(java.util.Arrays.asList(more));
        return S0Fixture.withProperties(p.toArray(new String[0]),
            () -> new S0Fixture.Listener(new S0Fixture.Recorder(),
                socketPath ? javax.net.ServerSocketFactory.getDefault() : null, hook));
    }

    /**
     * Silent peers hold a place in the queue and no place to work: a
     * legitimate peer is served at once although there are many more silent
     * peers than places to work.
     */
    @Test(timeout = 60000)
    public void testSilentPeersTakeNoPlaceToWork() throws Exception {
        Built built = new Built();
        try (S0Fixture.Listener l = listen(built, false, 10_000)) {
            S0Fixture.connectLegit(l); // warm up
            int warm = built.count.get();
            List<Socket> silent = S0Fixture.openSilent(l, 150);
            try {
                Assert.assertTrue(S0Fixture.await(
                    () -> l.handle.admittedConnections() == 150, 10_000));
                Thread.sleep(300);
                Assert.assertEquals("no place to work is taken", 0,
                                    l.handle.runningHandshakes());
                for (int i = 0; i < 3; i++) {
                    long ms = S0Fixture.connectLegit(l);
                    Assert.assertTrue("a legitimate open behind 150 silent peers "
                        + "took " + ms + " ms", ms < 1500);
                }
                Assert.assertEquals("and no engine is built for them", warm + 3,
                                    built.count.get());
            } finally {
                S0Fixture.closeAll(silent);
            }
        }
    }

    /**
     * Connections that send nothing, faster than the places to work could
     * ever be freed by their deadline, cannot keep a legitimate peer out.
     */
    @Test(timeout = 120000)
    public void testSilentConnectionsAtAnAttackRateDoNotStarveLegitimateOpens()
        throws Exception
    {
        try (S0Fixture.Listener l = listen(null, false, 4000)) {
            S0Fixture.connectLegit(l); // warm up
            AtomicBoolean stop = new AtomicBoolean();
            List<Socket> silent = new ArrayList<>();
            Thread attacker = Thread.ofPlatform().start(() -> {
                while (!stop.get()) {
                    try {
                        synchronized (silent) {
                            silent.addAll(S0Fixture.openSilent(l, 1));
                        }
                    } catch (IOException ignore) {
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            });
            try {
                /* Twenty a second, for twice the time that a place to work is held. */
                long worst = 0;
                for (int i = 0; i < 12; i++) {
                    Thread.sleep(500);
                    worst = Math.max(worst, S0Fixture.connectLegit(l));
                }
                Assert.assertTrue("worst legitimate open during the flood: "
                    + worst + " ms", worst < 1500);
                Assert.assertEquals(0, l.handle.runningHandshakes());
            } finally {
                stop.set(true);
                attacker.join(5000);
                synchronized (silent) {
                    S0Fixture.closeAll(silent);
                }
            }
        }
    }

    /**
     * A peer that goes away while it waits is removed at once: its place in
     * the queue and its descriptor are free, and it is counted.
     */
    @Test(timeout = 60000)
    public void testDepartedPeerIsRemovedAtOnce() throws Exception {
        int baseline = -1;
        try (S0Fixture.Listener l = listen(null, false, 30_000)) {
            S0Fixture.connectLegit(l); // warm up
            baseline = S0Fixture.settledFds(300);
            HandshakeLimits limits = l.handle.limits();
            int idle = limits.admission.availablePermits();
            for (boolean reset : new boolean[] { false, true }) {
                long departed = l.handle.counters().getFailed(
                    HandshakeCounters.Kind.PEER_CLOSED);
                Socket s = new Socket("localhost", l.port);
                Assert.assertTrue(S0Fixture.await(
                    () -> l.handle.admittedConnections() == 1, 5000));
                Assert.assertTrue("it holds a place in the queue", S0Fixture.await(
                    () -> limits.admission.availablePermits() == idle - 1, 2000));
                long t0 = System.nanoTime();
                if (reset) {
                    s.setSoLinger(true, 0);
                }
                s.close();
                Assert.assertTrue("removed, reset=" + reset, S0Fixture.await(
                    () -> l.handle.admittedConnections() == 0
                          && l.handle.inProgressCount() == 0, 2000));
                long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
                Assert.assertTrue("removed after " + ms + " ms", ms < 1000);
                Assert.assertTrue("the place in the queue is free",
                    S0Fixture.await(() -> limits.admission.availablePermits() == idle, 2000));
                Assert.assertTrue("counted as a peer that left", S0Fixture.await(
                    () -> l.handle.counters().getFailed(
                        HandshakeCounters.Kind.PEER_CLOSED) == departed + 1, 2000));
            }
            Assert.assertEquals("no place to work was taken", 0,
                                l.handle.runningHandshakes());
        }
        S0Fixture.assumeCounted(S0Fixture.assertFdsBack(baseline, 5000));
    }

    /** What a peer might send that is not a ClientHello. */
    private static byte[][] junk() {
        return new byte[][] {
            "GET / HTTP/1.1\r\nHost: x\r\n\r\n".getBytes(),
            new byte[] { 'G' },
            new byte[] { (byte) 0x80, 0x2e, 1, 0, 2, 0, 0, 0, 0, 0 },     // SSL 2 hello
            record(23, 3, 3, 100, 1, 6),
            record(21, 3, 3, 2, 1, 6),
            record(22, 3, 1, 100, 2, 6),
            record(22, 3, 1, 0, 1, 6),
            record(22, 3, 1, 60000, 1, 6),
            new byte[] { 0, 0, 0, 0, 0, 0, 0, 0 },
            "\u0016\u0003\u0001\u0000".getBytes(),                          // too short; peer waits
        };
    }

    /**
     * Bytes that are not the start of a ClientHello are dropped as soon as
     * they are seen, never take a place to work, and are counted.
     */
    @Test(timeout = 60000)
    public void testJunkNeverTakesAPlaceToWorkAndIsClosedFast() throws Exception {
        Built built = new Built();
        try (S0Fixture.Listener l = listen(built, false, 30_000)) {
            S0Fixture.connectLegit(l); // warm up
            int warm = built.count.get();
            long entries = l.handle.cpuEntries();
            byte[][] junk = junk();
            int expectedJunk = 0;
            for (int i = 0; i < junk.length; i++) {
                boolean closes = i < junk.length - 1;
                try (Socket s = new Socket("localhost", l.port)) {
                    s.getOutputStream().write(junk[i]);
                    s.getOutputStream().flush();
                    if (closes) {
                        long ms = S0Fixture.awaitClosed(s, 3000);
                        Assert.assertTrue("junk " + i + " closed after " + ms
                            + " ms", ms >= 0 && ms < 1000);
                        expectedJunk++;
                    } else {
                        /* A prefix of a record is not junk: it waits. */
                        Assert.assertEquals(-1L, S0Fixture.awaitClosed(s, 300));
                    }
                    Assert.assertEquals("junk " + i, 0, l.handle.runningHandshakes());
                }
            }
            final int junkCount = expectedJunk;
            Assert.assertTrue("counted as junk", S0Fixture.await(
                () -> l.handle.counters().getFailed(HandshakeCounters.Kind.JUNK)
                      == junkCount, 3000));
            Assert.assertEquals("no engine is built for junk", warm, built.count.get());
            Assert.assertEquals("and no processor is spent on it", entries,
                                l.handle.cpuEntries());
            Assert.assertTrue(S0Fixture.await(() -> l.handle.inProgressCount() == 0
                                              && l.handle.admittedConnections() == 0, 3000));
        }
    }

    /**
     * A peer that sends a part of a record and stops holds a place in the
     * queue only, and is cut off when the time that a handshake needs is no
     * longer left.
     */
    @Test(timeout = 60000)
    public void testPartialRecordIsCutOffAtTheDeadline() throws Exception {
        Built built = new Built();
        try (S0Fixture.Listener l = listen(built, false, 2400)) {
            S0Fixture.connectLegit(l); // warm up
            int warm = built.count.get();
            byte[] hello = S0Fixture.hello();
            for (int part : new int[] { 3, 5, 6, 40, hello.length - 1 }) {
                long cutoff = l.handle.counters().getFailed(
                    HandshakeCounters.Kind.QUEUE_TIMEOUT);
                try (Socket s = new Socket("localhost", l.port)) {
                    s.setTcpNoDelay(true);
                    s.getOutputStream().write(hello, 0, part);
                    s.getOutputStream().flush();
                    Assert.assertTrue(S0Fixture.await(
                        () -> l.handle.admittedConnections() == 1, 3000));
                    Assert.assertEquals("no place to work for " + part + " bytes",
                                        0, l.handle.runningHandshakes());
                    long ms = S0Fixture.awaitClosed(s, 10_000);
                    long expected = S0Fixture.queueMillis(2400);
                    Assert.assertTrue(part + " bytes: closed after " + ms + " ms",
                        ms >= expected - 300 && ms < expected + 2000);
                }
                Assert.assertTrue(S0Fixture.await(() -> l.handle.counters()
                    .getFailed(HandshakeCounters.Kind.QUEUE_TIMEOUT) == cutoff + 1, 3000));
            }
            Assert.assertEquals("no engine is built for a partial record", warm,
                                built.count.get());
            Assert.assertEquals(0, l.handle.counters()
                .getFailed(HandshakeCounters.Kind.TIMEOUT));
        }
    }

    /**
     * A connection that would be given its turn with less of the time left
     * than a handshake needs is dropped without a turn and without work, and
     * is counted as one that waited too long, not as one that timed out in its
     * work.
     */
    @Test(timeout = 60000)
    public void testNoTurnIsGivenWithLessThanTheMinimumBudgetLeft() throws Exception {
        Built built = new Built();
        try (S0Fixture.Listener l = listen(built, false, 4000,
                 "org.apache.river.jeri.ssl.serverHandshakeMinBudget", "1500"))
        {
            S0Fixture.connectLegit(l); // warm up
            int warm = built.count.get();
            java.net.ServerSocket other = new java.net.ServerSocket(0);
            try (Socket peer = new Socket("localhost", other.getLocalPort());
                 Socket accepted = other.accept())
            {
                /* 1000 ms left, 1500 needed. */
                SslServerEndpointImpl.SslListenHandle.Handshake h =
                    l.handle.new Handshake(accepted,
                        System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1000));
                try {
                    h.awaitPermit();
                    Assert.fail("a turn was given with too little time left");
                } catch (java.net.SocketTimeoutException expected) {
                }
                Assert.assertTrue(h.isQueueTimedOut());
                Assert.assertEquals("no place to work was taken", 0,
                                    l.handle.runningHandshakes());
                h.release();
                /* 2000 ms left is enough. */
                SslServerEndpointImpl.SslListenHandle.Handshake enough =
                    l.handle.new Handshake(accepted,
                        System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(2000));
                enough.awaitPermit();
                Assert.assertEquals(1, l.handle.runningHandshakes());
                enough.release();
            } finally {
                other.close();
            }
            Assert.assertEquals(warm, built.count.get());
        }
    }

    /**
     * The first bytes that were read while the connection waited are not
     * lost, on either path: a ClientHello that arrives in pieces of a few
     * bytes, split in the header and in the body, is answered, and the
     * handshake completes.
     */
    @Test(timeout = 60000)
    public void testBytesReadAheadAreNotLost() throws Exception {
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = listen(null, socketPath, 10_000)) {
                S0Fixture.connectLegit(l); // warm up
                int before = l.recorder.count();
                try (SplittingProxy proxy = new SplittingProxy(l.port,
                         new int[] { 1, 1, 3, 1, 20, 300, 5, 700 }))
                {
                    try (javax.net.ssl.SSLSocket s = (javax.net.ssl.SSLSocket)
                             S0Fixture.clientSocketFactory()
                                 .createSocket("localhost", proxy.port))
                    {
                        s.setSoTimeout(15_000);
                        s.startHandshake();
                        s.getOutputStream().write(0);
                        s.getOutputStream().flush();
                        Assert.assertTrue("the connection was handed on, socketPath="
                                          + socketPath,
                                          l.recorder.await(before + 1, 15_000));
                    }
                    Assert.assertTrue("the hello was split into pieces: " + proxy.pieces.get(),
                                      proxy.pieces.get() >= 8);
                }
                Assert.assertEquals(0, l.handle.counters().getFailed());
            }
        }
    }

    /** Re-frames the one record of a ClientHello as records of {@code size} bytes. */
    private static byte[] inRecords(byte[] hello, int size) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int at = 5; at < hello.length; at += size) {
            int n = Math.min(size, hello.length - at);
            out.write(hello[0]);
            out.write(hello[1]);
            out.write(hello[2]);
            out.write(n >> 8);
            out.write(n);
            out.write(hello, at, n);
        }
        return out.toByteArray();
    }

    /**
     * A record of 1 to 3 bytes is a legal fragment of a ClientHello and the
     * JDK reads one: the connection is answered with the server's flight, not
     * dropped as junk, on both paths.
     */
    @Test(timeout = 60000)
    public void testClientHelloInRecordsOfOneAndThreeBytes() throws Exception {
        byte[] hello = S0Fixture.captureClientHello("x25519");
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = listen(null, socketPath, 10_000)) {
                S0Fixture.connectLegit(l); // warm up
                for (int size : new int[] { 1, 3 }) {
                    try (Socket s = new Socket("localhost", l.port)) {
                        s.setTcpNoDelay(true);
                        s.getOutputStream().write(inRecords(hello, size));
                        s.getOutputStream().flush();
                        s.setSoTimeout(5000);
                        byte[] flight = new byte[4096];
                        int n = s.getInputStream().read(flight);
                        String what = "records of " + size + ", socketPath=" + socketPath;
                        Assert.assertTrue("a flight, " + what, n > 0);
                        Assert.assertEquals("a handshake record, not an alert, " + what,
                                            22, flight[0]);
                    }
                }
                Assert.assertEquals("none was taken for junk, socketPath=" + socketPath,
                    0, l.handle.counters().getFailed(HandshakeCounters.Kind.JUNK));
                Assert.assertTrue(S0Fixture.await(
                    () -> l.handle.inProgressCount() == 0, 5000));
            }
        }
    }

    /** Forwards a connection, delivering the first bytes of the client in pieces. */
    private static final class SplittingProxy implements AutoCloseable {
        final ServerSocket server;
        final int port;
        final AtomicInteger pieces = new AtomicInteger();
        private final List<Socket> sockets = new ArrayList<>();

        SplittingProxy(int target, int[] cuts) throws IOException {
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
                    Thread.ofPlatform().daemon().start(() -> copy(upstream, client, null));
                    copy(client, upstream, cuts);
                } catch (IOException ignore) {
                }
            });
        }

        private void copy(Socket from, Socket to, int[] cuts) {
            try {
                InputStream in = from.getInputStream();
                OutputStream out = to.getOutputStream();
                byte[] buf = new byte[16384];
                int cut = 0;
                int n;
                while ((n = in.read(buf)) >= 0) {
                    int at = 0;
                    while (cuts != null && cut < cuts.length && at < n) {
                        int piece = Math.min(cuts[cut++], n - at);
                        out.write(buf, at, piece);
                        out.flush();
                        pieces.incrementAndGet();
                        at += piece;
                        Thread.sleep(40);
                    }
                    out.write(buf, at, n - at);
                    out.flush();
                    if (cuts != null && cut >= cuts.length) {
                        cuts = null;
                    }
                }
            } catch (IOException | InterruptedException ignore) {
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

    /**
     * The first record of a connection on the SSLSocket path is read ahead
     * too: silent peers wait their turn and take no place to work.
     */
    @Test(timeout = 60000)
    public void testSilentPeersOnTheSocketPathTakeNoPlaceToWork() throws Exception {
        try (S0Fixture.Listener l = listen(null, true, 6000)) {
            S0Fixture.connectLegit(l); // warm up
            List<Socket> silent = S0Fixture.openSilent(l, 3);
            try {
                Assert.assertTrue(S0Fixture.await(
                    () -> l.handle.admittedConnections() == 3, 5000));
                Thread.sleep(300);
                Assert.assertEquals("no place to work for a silent peer", 0,
                                    l.handle.runningHandshakes());
            } finally {
                S0Fixture.closeAll(silent);
            }
        }
    }
}
