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
public class SslServerClientHelloTest {

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
        Assert.assertEquals(0, ClientHelloReader.check(new byte[] { 22 }, 1));
        Assert.assertEquals(0, ClientHelloReader.check(new byte[] { 22, 3, 1, 0 }, 4));
        Assert.assertEquals(5 + 300, ClientHelloReader.check(
            record(22, 3, 1, 300, 1, 6), 6));
        Assert.assertEquals(5 + 16384, ClientHelloReader.check(
            record(22, 3, 3, 16384, 1, 5), 5));
        /* A handshake message may be split into records of any size but 0. */
        for (int length = 1; length <= 4; length++) {
            Assert.assertEquals(5 + length, ClientHelloReader.check(
                record(22, 3, 1, length, 1, 6), 6));
            Assert.assertEquals(5 + length, ClientHelloReader.check(
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
                ClientHelloReader.check(junk[i], junk[i].length);
                Assert.fail("junk " + i + " was accepted");
            } catch (ClientHelloReader.NotTlsException expected) {
            }
        }
        /* Junk is seen from its first byte, without waiting for the rest. */
        try {
            ClientHelloReader.check("G".getBytes(), 1);
            Assert.fail("a G is not a handshake record");
        } catch (ClientHelloReader.NotTlsException expected) {
        }
    }

    /** A message of the shape of a ClientHello with {@code body} bytes after its header. */
    private static byte[] messageBytes(int body) {
        byte[] m = new byte[4 + body];
        m[0] = 1;
        m[1] = (byte) (body >> 16);
        m[2] = (byte) (body >> 8);
        m[3] = (byte) body;
        for (int i = 4; i < m.length; i++) {
            m[i] = (byte) i;
        }
        return m;
    }

    /** The message in records of the given sizes, the last taking what is left. */
    private static byte[] frame(byte[] m, int... sizes) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int at = 0;
        for (int i = 0; at < m.length; i++) {
            int n = i < sizes.length ? Math.min(sizes[i], m.length - at) : m.length - at;
            out.write(22);
            out.write(3);
            out.write(1);
            out.write(n >> 8);
            out.write(n);
            out.write(m, at, n);
            at += n;
        }
        return out.toByteArray();
    }

    /** The records of a ClientHello-shaped message in records of the given sizes. */
    private static byte[] message(int body, int... sizes) {
        return frame(messageBytes(body), sizes);
    }

    private static byte[] concat(byte[]... parts) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.write(p, 0, p.length);
        }
        return out.toByteArray();
    }

    /** The bytes in pieces of the given size. */
    private static Script inPieces(byte[] all, int piece) {
        List<byte[]> pieces = new ArrayList<>();
        for (int at = 0; at < all.length; at += piece) {
            pieces.add(java.util.Arrays.copyOfRange(all, at,
                Math.min(all.length, at + piece)));
        }
        return new Script(pieces.toArray(new byte[0][]));
    }

    /**
     * The whole ClientHello is read, in whatever records it comes in (up to
     * the most) and in whatever pieces, and nothing after the last of them is
     * taken.
     */
    @Test
    public void testReadTakesTheWholeClientHelloAndNothingMore() throws Exception {
        byte[] after = { 20, 3, 3, 0, 1, 1 };
        int[][] shapes = {
            { },                                    // one record
            { 1 }, { 2 }, { 3 }, { 4 }, { 5 },      // the header of the message is split
            { 1, 1, 1, 1 }, { 1, 3 }, { 2, 2, 100 },
            { 1, 1, 1, 1, 1, 1, 1 },                // the most records: 8
            { 100, 200 }, { 16384 }, { 16384, 16384 },
        };
        for (int body : new int[] { 0, 1, 2, 3, 100, 511, 512, 1500, 16380, 32768 }) {
            for (int[] shape : shapes) {
                byte[] hello = message(body, shape);
                int records = 0;
                boolean fits = true;
                for (int at = 0; at < hello.length; ) {
                    int n = ((hello[at + 3] & 0xff) << 8) | (hello[at + 4] & 0xff);
                    fits &= n <= ClientHelloReader.MAX_LENGTH;
                    at += 5 + n;
                    records++;
                }
                if (!fits || records > ClientHelloReader.MAX_RECORDS) {
                    continue;
                }
                for (int piece : new int[] { 1, 2, 5, 7, 600, 100000 }) {
                    Script script = inPieces(concat(hello, after), piece);
                    String what = "body " + body + " in " + records + " records, pieces of "
                        + piece;
                    Assert.assertArrayEquals(what, hello, ClientHelloReader.read(script));
                    Assert.assertEquals("what follows the message is left to be read: "
                        + what, after.length, script.unread());
                }
            }
        }
    }

    @Test
    public void testReadEndsAtEndOfFileOrJunk() throws Exception {
        try {
            ClientHelloReader.read(new Script());
            Assert.fail();
        } catch (EOFException expected) {
        }
        try {
            ClientHelloReader.read(new Script(new byte[] { 22, 3, 1, 1 }));
            Assert.fail();
        } catch (EOFException expected) {
        }
        try {
            ClientHelloReader.read(new Script(record(22, 3, 1, 100, 1, 50)));
            Assert.fail();
        } catch (EOFException expected) {
        }
        /* A part of the message: the records end, the message does not. */
        byte[] hello = message(300, 100);
        try {
            ClientHelloReader.read(new Script(
                java.util.Arrays.copyOf(hello, 5 + 100)));
            Assert.fail();
        } catch (EOFException expected) {
        }
        Script junk = new Script("GET / HTTP/1.1\r\n\r\n".getBytes());
        junk.eof = false;
        try {
            ClientHelloReader.read(junk);
            Assert.fail();
        } catch (ClientHelloReader.NotTlsException expected) {
        }
    }

    /**
     * What is not the whole of one ClientHello is refused as soon as it is
     * seen, and no more is read than the refusal needs.
     */
    @Test
    public void testReadRefusesWhatIsNotAClientHello() throws Exception {
        byte[] ccs = { 20, 3, 3, 0, 1, 1 };
        byte[] first = message(400, 100);
        int firstRecord = 5 + 100;
        /* A message of 14 bytes in a record of 16. */
        byte[] trailing = concat(message(10), new byte[2]);
        trailing[4] = 16;
        byte[][] junk = {
            /* More records than the most: 1 byte in each. */
            message(400, 1, 1, 1, 1, 1, 1, 1, 1),
            /* A message longer than the JDK takes. */
            message(32769, 16384),
            message(1 << 20, 16384),
            /* A record of another type before the message is complete. */
            concat(java.util.Arrays.copyOf(first, firstRecord), ccs),
            /* A record of a handshake that is not in version 3. */
            concat(java.util.Arrays.copyOf(first, firstRecord),
                   record(22, 2, 0, 100, 1, 105)),
            /* An empty record in the middle. */
            concat(java.util.Arrays.copyOf(first, firstRecord),
                   new byte[] { 22, 3, 1, 0, 0 }),
            /* A record longer than a record may be. */
            concat(java.util.Arrays.copyOf(first, firstRecord),
                   new byte[] { 22, 3, 1, (byte) 0x40, 1 }),
            /* A record with more than the message needs, seen from its header. */
            concat(java.util.Arrays.copyOf(first, firstRecord),
                   new byte[] { 22, 3, 1, 2, 0 }),
            /* A record that goes on after the end of the message. */
            trailing,
        };
        for (int i = 0; i < junk.length; i++) {
            Script script = inPieces(junk[i], 1000);
            script.eof = false;
            try {
                ClientHelloReader.read(script);
                Assert.fail("junk " + i + " was accepted");
            } catch (ClientHelloReader.NotTlsException expected) {
            } catch (EOFException e) {
                Assert.fail("junk " + i + " was not refused, the read went on: " + e);
            }
        }
        /* Exactly the most records is taken; one more is not. */
        byte[] most = message(400, 50, 50, 50, 50, 50, 50, 50);
        Assert.assertArrayEquals(most, ClientHelloReader.read(inPieces(most, 4000)));
    }

    /**
     * The second ClientHello, after a HelloRetryRequest, may come behind the
     * compatibility ChangeCipherSpec of TLS 1.3, once, and then is read as
     * the first is; the first never may.
     */
    @Test
    public void testReadAfterRetryTakesAChangeCipherSpecBeforeTheHello() throws Exception {
        byte[] ccs = { 20, 3, 3, 0, 1, 1 };
        byte[] after = { 20, 3, 3, 0, 1, 1 };
        for (int[] shape : new int[][] { { }, { 1, 3 }, { 100, 100 } }) {
            byte[] hello = message(300, shape);
            for (int piece : new int[] { 1, 5, 6, 7, 100000 }) {
                Script script = inPieces(concat(ccs, hello, after), piece);
                Assert.assertArrayEquals("with a ChangeCipherSpec, pieces of " + piece,
                    concat(ccs, hello), ClientHelloReader.readAfterRetry(asStream(script)));
                Assert.assertEquals(after.length, script.unread());
                script = inPieces(concat(hello, after), piece);
                Assert.assertArrayEquals("without one, pieces of " + piece,
                    hello, ClientHelloReader.readAfterRetry(asStream(script)));
                Assert.assertEquals(after.length, script.unread());
            }
        }
        byte[] hello = message(300);
        byte[][] junk = {
            concat(ccs, ccs, hello),                              // a second one
            concat(new byte[] { 20, 3, 3, 0, 2, 1, 1 }, hello),   // two bytes
            concat(new byte[] { 20, 3, 3, 0, 1, 2 }, hello),      // not the value 1
            concat(new byte[] { 20, 2, 0, 0, 1, 1 }, hello),      // not version 3
            concat(new byte[] { 20, 3, 3, 1, 1, 1 }, hello),      // a length of 257
        };
        for (int i = 0; i < junk.length; i++) {
            Script script = inPieces(junk[i], 1000);
            script.eof = false;
            try {
                ClientHelloReader.readAfterRetry(asStream(script));
                Assert.fail("junk " + i + " was accepted");
            } catch (ClientHelloReader.NotTlsException expected) {
            }
        }
        /* The first hello may not be preceded by one. */
        Script first = inPieces(concat(ccs, hello), 1000);
        first.eof = false;
        try {
            ClientHelloReader.read(first);
            Assert.fail("a ChangeCipherSpec before the first ClientHello");
        } catch (ClientHelloReader.NotTlsException expected) {
        }
        /* A ChangeCipherSpec alone is not a hello. */
        try {
            ClientHelloReader.readAfterRetry(asStream(new Script(ccs)));
            Assert.fail();
        } catch (EOFException expected) {
        }
    }

    /** The script as a stream. */
    private static InputStream asStream(Script script) {
        return new InputStream() {
            public int read() throws IOException {
                byte[] one = new byte[1];
                int n = read(one, 0, 1);
                return n < 0 ? -1 : one[0] & 0xff;
            }

            public int read(byte[] b, int off, int len) throws IOException {
                return script.read(ByteBuffer.wrap(b, off, len));
            }
        };
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

    /**
     * Re-frames the one record of a ClientHello as {@code records} records of
     * about the same size, the last taking what is left.
     */
    private static byte[] inRecordCount(byte[] hello, int records) {
        int[] sizes = new int[records - 1];
        java.util.Arrays.fill(sizes, (hello.length - 5 + records - 1) / records);
        return inRecords(hello, sizes);
    }

    /** Re-frames the one record of a ClientHello as records of the given sizes. */
    private static byte[] inRecords(byte[] hello, int... sizes) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int at = 5;
        for (int i = 0; at < hello.length; i++) {
            int n = Math.min(i < sizes.length ? sizes[i] : Integer.MAX_VALUE,
                             hello.length - at);
            out.write(hello[0]);
            out.write(hello[1]);
            out.write(hello[2]);
            out.write(n >> 8);
            out.write(n);
            out.write(hello, at, n);
            at += n;
        }
        return out.toByteArray();
    }

    private static int[] ones(int n) {
        int[] ones = new int[n];
        java.util.Arrays.fill(ones, 1);
        return ones;
    }

    /** Sends the bytes in the given pieces, with a pause after each, and returns the server's first read. */
    private static int sendInPieces(Socket s, byte[] bytes, int... cuts) throws Exception {
        int at = 0;
        for (int i = 0; at < bytes.length; i++) {
            int n = Math.min(i < cuts.length ? cuts[i] : Integer.MAX_VALUE, bytes.length - at);
            s.getOutputStream().write(bytes, at, n);
            s.getOutputStream().flush();
            at += n;
            Thread.sleep(20);
        }
        s.setSoTimeout(5000);
        return s.getInputStream().read(new byte[4096]);
    }

    /**
     * A ClientHello that comes in a few records, whole or in pieces of the
     * connection, is answered with the server's flight on both paths: a
     * handshake message may be split over records, down to the most that the
     * server takes, with the 1-byte records that the header of the message
     * can be split by among them.
     */
    @Test(timeout = 60000)
    public void testClientHelloInSeveralRecords() throws Exception {
        byte[] hello = S0Fixture.captureClientHello("x25519");
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = listen(null, socketPath, 10_000)) {
                S0Fixture.connectLegit(l); // warm up
                List<byte[]> framings = new ArrayList<>();
                framings.add(inRecordCount(hello, 2));
                framings.add(inRecordCount(hello, 3));
                framings.add(inRecordCount(hello, ClientHelloReader.MAX_RECORDS));
                /* The most records, the first seven of one byte. */
                framings.add(inRecords(hello, 1, 1, 1, 1, 1, 1, 1));
                framings.add(inRecords(hello, 1, 3));
                for (int i = 0; i < framings.size(); i++) {
                    byte[] wire = framings.get(i);
                    for (boolean pieces : new boolean[] { false, true }) {
                        try (Socket s = new Socket("localhost", l.port)) {
                            s.setTcpNoDelay(true);
                            byte[] flight = new byte[4096];
                            int n;
                            if (pieces) {
                                n = sendInPieces(s, wire, 1, 2, 3, 40, 200);
                            } else {
                                s.getOutputStream().write(wire);
                                s.getOutputStream().flush();
                                s.setSoTimeout(5000);
                                n = s.getInputStream().read(flight);
                            }
                            String what = "framing " + i + ", in pieces " + pieces
                                + ", socketPath=" + socketPath;
                            Assert.assertTrue("a flight, " + what, n > 0);
                        }
                    }
                }
                Assert.assertEquals("none was taken for junk, socketPath=" + socketPath,
                    0, l.handle.counters().getFailed(HandshakeCounters.Kind.JUNK));
                Assert.assertTrue(S0Fixture.await(
                    () -> l.handle.inProgressCount() == 0, 5000));
            }
        }
    }

    /**
     * A ClientHello in more records than the most is dropped as junk as soon
     * as the record over the most is seen, never takes a place to work or a
     * permit to compute, and is not handed to the JDK.
     */
    @Test(timeout = 60000)
    public void testClientHelloInTooManyRecordsIsRefusedAtOnce() throws Exception {
        byte[] hello = S0Fixture.captureClientHello("x25519");
        int tooMany = ClientHelloReader.MAX_RECORDS + 1;
        for (boolean socketPath : new boolean[] { false, true }) {
            Built built = new Built();
            try (S0Fixture.Listener l = listen(built, socketPath, 30_000)) {
                S0Fixture.connectLegit(l); // warm up
                int warm = built.count.get();
                long entries = l.handle.cpuEntries() + l.handle.socketCpuEntries();
                byte[][] wires = { inRecordCount(hello, tooMany),
                                   inRecords(hello, 1, 1, 1, 1, 1, 1, 1, 1),
                                   inRecords(hello, ones(hello.length)) };
                for (int i = 0; i < wires.length; i++) {
                    try (Socket s = new Socket("localhost", l.port)) {
                        s.setTcpNoDelay(true);
                        s.getOutputStream().write(wires[i]);
                        s.getOutputStream().flush();
                        long ms = S0Fixture.awaitClosed(s, 3000);
                        Assert.assertTrue("framing " + i + " closed after " + ms
                            + " ms, socketPath=" + socketPath, ms >= 0 && ms < 1000);
                    }
                }
                final int junk = wires.length;
                Assert.assertTrue("counted as junk, socketPath=" + socketPath,
                    S0Fixture.await(() -> l.handle.counters().getFailed(
                        HandshakeCounters.Kind.JUNK) == junk, 3000));
                Assert.assertEquals("no place to work, socketPath=" + socketPath,
                                    0, l.handle.runningHandshakes());
                Assert.assertEquals("no engine or socket is built for it", warm,
                                    built.count.get());
                Assert.assertEquals("and no processor is spent on it", entries,
                    l.handle.cpuEntries() + l.handle.socketCpuEntries());
                Assert.assertTrue(S0Fixture.await(() -> l.handle.inProgressCount() == 0
                                                  && l.handle.admittedConnections() == 0, 3000));
            }
        }
    }

    /** Server-side CPU of one connection that sends the bytes, best of a few. */
    private static long cpuOfSending(S0Fixture.Listener l, byte[] wire, int reps)
        throws Exception
    {
        long best = Long.MAX_VALUE;
        for (int rep = 0; rep < reps; rep++) {
            long cpu0 = S0Fixture.processCpuNanos();
            try (Socket s = new Socket("localhost", l.port)) {
                s.setTcpNoDelay(true);
                Thread writer = Thread.ofPlatform().start(() -> {
                    try {
                        s.getOutputStream().write(wire);
                        s.getOutputStream().flush();
                    } catch (IOException refused) {
                    }
                });
                S0Fixture.awaitClosed(s, 20_000);
                writer.join(5000);
            }
            Thread.sleep(20);
            best = Math.min(best, S0Fixture.processCpuNanos() - cpu0);
        }
        return best;
    }

    /**
     * The JDK copies a fragmented handshake message for every record that
     * follows, so 32 KiB in records of one byte cost a server about 190 ms
     * of processor time before this server's gate.  The same bytes in
     * records of 16 KiB cost nothing to speak of, and so must the same bytes
     * in records of one byte.  The bound has a margin of 40 ms for a machine
     * and a timer that are slow; the cost without the gate is several times
     * that.
     */
    @Test(timeout = 120000)
    public void testAHelloInRecordsOfOneByteCostsTheServerLittle() throws Exception {
        int body = 32000;
        byte[] flat = message(body, 16384, 16384);
        int[] ones = new int[body + 4];
        java.util.Arrays.fill(ones, 1);
        byte[] oneByteRecords = frame(messageBytes(body), ones);
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = S0Fixture.withProperties(new String[] {
                    S0Fixture.HANDSHAKE_TIMEOUT, "20000" },
                    () -> socketPath ? S0Fixture.listenWithSocketFactory() : S0Fixture.listen()))
            {
                S0Fixture.connectLegit(l); // warm up
                cpuOfSending(l, flat, 2);
                long flatCost = cpuOfSending(l, flat, 5);
                long fragmented = cpuOfSending(l, oneByteRecords, 5);
                System.out.println("one-byte records, socketPath=" + socketPath + ": "
                    + fragmented / 1_000_000 + " ms of CPU for " + oneByteRecords.length
                    + " bytes, " + flatCost / 1_000_000 + " ms for the same in 3 records");
                Assert.assertTrue("a 32 KiB ClientHello in records of one byte cost "
                    + fragmented / 1_000_000 + " ms of CPU, in 3 records "
                    + flatCost / 1_000_000 + " ms, socketPath=" + socketPath,
                    fragmented <= 3 * flatCost + 40_000_000L);
            }
        }
    }

    /**
     * A record that follows the ClientHello in the same write, as the
     * compatibility ChangeCipherSpec of a client that offers early data does,
     * is not taken as a part of the ClientHello, and is not lost: the
     * handshake completes on both paths.
     */
    @Test(timeout = 60000)
    public void testARecordRightAfterTheClientHelloIsNotLost() throws Exception {
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = listen(null, socketPath, 10_000)) {
                S0Fixture.connectLegit(l); // warm up
                int before = l.recorder.count();
                handshakeWithChangeCipherSpecAfterTheHello(l);
                Assert.assertTrue("the connection was handed on, socketPath=" + socketPath,
                                  l.recorder.await(before + 1, 10_000));
                Assert.assertEquals(0, l.handle.counters().getFailed());
            }
        }
    }

    private static final byte[] CCS = { 20, 3, 3, 0, 1, 1 };

    /**
     * Drives a client handshake by hand so that the client's ChangeCipherSpec
     * goes out right behind its ClientHello, in the same write, and not in
     * its second flight.
     */
    private static void handshakeWithChangeCipherSpecAfterTheHello(S0Fixture.Listener l)
        throws Exception
    {
        javax.net.ssl.SSLEngine engine = S0Fixture.clientEngine();
        try (Socket s = new Socket("localhost", l.port)) {
            s.setTcpNoDelay(true);
            s.setSoTimeout(10_000);
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            ByteBuffer net = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
            ByteBuffer app = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
            ByteBuffer rx = ByteBuffer.allocate(1 << 16);
            rx.limit(0);
            boolean first = true;
            engine.beginHandshake();
            javax.net.ssl.SSLEngineResult.HandshakeStatus st = engine.getHandshakeStatus();
            while (st != javax.net.ssl.SSLEngineResult.HandshakeStatus.FINISHED
                   && st != javax.net.ssl.SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING)
            {
                switch (st) {
                    case NEED_TASK:
                        Runnable task;
                        while ((task = engine.getDelegatedTask()) != null) {
                            task.run();
                        }
                        st = engine.getHandshakeStatus();
                        break;
                    case NEED_WRAP: {
                        net.clear();
                        javax.net.ssl.SSLEngineResult r = engine.wrap(ByteBuffer.allocate(0), net);
                        net.flip();
                        byte[] bytes = new byte[net.remaining()];
                        net.get(bytes);
                        if (first) {
                            first = false;
                            bytes = concat(bytes, CCS);
                        } else {
                            bytes = withoutChangeCipherSpec(bytes);
                        }
                        out.write(bytes);
                        out.flush();
                        st = r.getHandshakeStatus();
                        break;
                    }
                    case NEED_UNWRAP: {
                        javax.net.ssl.SSLEngineResult r = engine.unwrap(rx, app);
                        if (r.getStatus() == javax.net.ssl.SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                            rx.compact();
                            int n = in.read(rx.array(), rx.position(), rx.remaining());
                            if (n < 0) {
                                throw new EOFException("the server closed the connection");
                            }
                            rx.position(rx.position() + n);
                            rx.flip();
                        } else {
                            st = r.getHandshakeStatus();
                        }
                        break;
                    }
                    default:
                        throw new AssertionError("handshake status " + st);
                }
            }
            /* The server has the client's Finished when it sees application data. */
            net.clear();
            engine.wrap(ByteBuffer.wrap(new byte[] { 0 }), net);
            net.flip();
            byte[] bytes = new byte[net.remaining()];
            net.get(bytes);
            out.write(bytes);
            out.flush();
            Assert.assertTrue(S0Fixture.await(() -> l.recorder.count() > 0, 10_000));
        }
    }

    /** The records of the bytes without ChangeCipherSpec records. */
    private static byte[] withoutChangeCipherSpec(byte[] bytes) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int at = 0; at < bytes.length; ) {
            int n = 5 + (((bytes[at + 3] & 0xff) << 8) | (bytes[at + 4] & 0xff));
            if (bytes[at] != 20) {
                out.write(bytes, at, n);
            }
            at += n;
        }
        return out.toByteArray();
    }

    /**
     * What a peer might send of a ClientHello before it stops: a record that
     * is a fragment of one, so that the record is complete and the message is
     * not.
     */
    private static byte[][] fragments() throws Exception {
        byte[] hello = S0Fixture.captureClientHello("x25519");
        return new byte[][] {
            /* A record of 1 byte, the type of a ClientHello. */
            { 22, 3, 1, 0, 1, 1 },
            /* A record of 4 bytes, the header of a message of 256 bytes. */
            { 22, 3, 1, 0, 4, 1, 0, 1, 0 },
            /* The first 40 bytes of a real ClientHello, in a record of their own. */
            java.util.Arrays.copyOf(inRecords(hello, new int[] { 40 }), 5 + 40),
            /* All of a real ClientHello but its last byte, in two records. */
            java.util.Arrays.copyOf(inRecords(hello, new int[] { hello.length - 6 }),
                                    hello.length - 1 + 5),
        };
    }

    /** Opens connections that send the bytes and then wait; the caller closes them. */
    private static List<Socket> openSending(S0Fixture.Listener l, int n, byte[] bytes)
        throws Exception
    {
        List<Socket> sockets = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Socket s = new Socket("localhost", l.port);
            s.setTcpNoDelay(true);
            s.getOutputStream().write(bytes);
            s.getOutputStream().flush();
            sockets.add(s);
        }
        return sockets;
    }

    /**
     * Peers that send a part of a ClientHello, a complete record of it, and
     * stop take a place in the queue and nothing else, on the SSLSocket path:
     * as many of them as there are permits to do a handshake leave every permit
     * free, and a legitimate open is served at once.  (The permit was taken
     * when one record had arrived, and the JDK then waited for the rest of the
     * message with the permit held until the deadline.)
     */
    @Test(timeout = 120000)
    public void testPartialClientHelloHoldsNoPermitOnTheSocketPath() throws Exception {
        for (byte[] fragment : fragments()) {
            try (S0Fixture.Listener l = listen(null, true, 8000,
                     S0Fixture.MAX_HANDSHAKES, "64"))
            {
                S0Fixture.connectLegit(l); // warm up
                int permits = l.handle.cpuPermits();
                long entries = l.handle.socketCpuEntries();
                List<Socket> stuck = openSending(l, permits, fragment);
                try {
                    Assert.assertTrue(S0Fixture.await(
                        () -> l.handle.admittedConnections() == permits, 5000));
                    Thread.sleep(300);
                    Assert.assertEquals("every permit is free, " + fragment.length + " bytes",
                        permits, l.handle.socketCpuPermitsAvailable());
                    Assert.assertEquals("no place to work, " + fragment.length + " bytes",
                        0, l.handle.runningHandshakes());
                    for (int i = 0; i < 3; i++) {
                        long ms = S0Fixture.connectLegit(l);
                        Assert.assertTrue("a legitimate open behind " + permits
                            + " peers of " + fragment.length + " bytes took " + ms + " ms",
                            ms < 1000);
                    }
                    Assert.assertEquals("and the stuck peers were given no permit",
                        entries + 3, l.handle.socketCpuEntries());
                } finally {
                    S0Fixture.closeAll(stuck);
                }
            }
        }
    }

    /**
     * The same on the engine path, with as many peers as there are places to
     * work: they hold places in the queue only, and a legitimate open is
     * served at once.  (A place to work was given when one record had
     * arrived, so these peers held every place, and the permit that bounds
     * the first flight was never in question.)
     */
    @Test(timeout = 120000)
    public void testPartialClientHelloHoldsNoPlaceToWorkOnTheEnginePath() throws Exception {
        for (byte[] fragment : fragments()) {
            try (S0Fixture.Listener l = listen(null, false, 8000)) {
                S0Fixture.connectLegit(l); // warm up
                int places = l.handle.maxHandshakes();
                List<Socket> stuck = openSending(l, places, fragment);
                try {
                    Assert.assertTrue(S0Fixture.await(
                        () -> l.handle.admittedConnections() == places, 5000));
                    Thread.sleep(300);
                    Assert.assertEquals("no place to work, " + fragment.length + " bytes",
                        0, l.handle.runningHandshakes());
                    for (int i = 0; i < 3; i++) {
                        long ms = S0Fixture.connectLegit(l);
                        Assert.assertTrue("a legitimate open behind " + places
                            + " peers of " + fragment.length + " bytes took " + ms + " ms",
                            ms < 1000);
                    }
                } finally {
                    S0Fixture.closeAll(stuck);
                }
            }
        }
    }

    /**
     * Such peers are cut off when the wait ends, counted as having waited too
     * long and not as working timeouts, and do not outlast it.
     */
    @Test(timeout = 60000)
    public void testPartialClientHelloIsCutOffAtTheEndOfTheWait() throws Exception {
        byte[] fragment = fragments()[3];
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = listen(null, socketPath, 2400)) {
                S0Fixture.connectLegit(l); // warm up
                long cutoff = l.handle.counters().getFailed(
                    HandshakeCounters.Kind.QUEUE_TIMEOUT);
                try (Socket s = new Socket("localhost", l.port)) {
                    s.getOutputStream().write(fragment);
                    s.getOutputStream().flush();
                    long ms = S0Fixture.awaitClosed(s, 10_000);
                    long expected = S0Fixture.queueMillis(2400);
                    Assert.assertTrue("socketPath=" + socketPath + ": closed after " + ms
                        + " ms", ms >= expected - 300 && ms < expected + 2000);
                }
                Assert.assertTrue(S0Fixture.await(() -> l.handle.counters()
                    .getFailed(HandshakeCounters.Kind.QUEUE_TIMEOUT) == cutoff + 1, 3000));
                Assert.assertEquals(0, l.handle.counters()
                    .getFailed(HandshakeCounters.Kind.TIMEOUT));
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
