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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * What the peer sends after the server's first flight is bounded on both
 * paths: the second ClientHello that follows a HelloRetryRequest is read whole
 * and checked like the first, so that the engine is never given a message in
 * many small records to put together, and the records of the client's own
 * flight are counted, so that a client that has the keys of the handshake and
 * frames its Certificate message in records of a few bytes costs the server
 * no more than one that does not.
 */
public class SslServerSecondFlightTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        S0Fixture.setUp();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        S0Fixture.tearDown();
    }

    /** Groups of which the first, with the key share, is one that the server does not enable. */
    private static final String[] RETRY = { "secp384r1", "secp256r1" };

    /** A ChangeCipherSpec record, as a client sends it for middleboxes. */
    private static final byte[] CCS = { 20, 3, 3, 0, 1, 1 };

    private static final String[] PERMITS = {
        SslServerEndpointImpl.SslListenHandle.SERVER_CPU_PERMITS_PROPERTY, "1",
        S0Fixture.MAX_HANDSHAKES, "64", S0Fixture.HANDSHAKE_TIMEOUT, "20000" };

    private static S0Fixture.Listener listen(boolean socketPath) throws Exception {
        return S0Fixture.withProperties(PERMITS,
            () -> socketPath ? S0Fixture.listenWithSocketFactory() : S0Fixture.listen());
    }

    private static long entries(S0Fixture.Listener l, boolean socketPath) {
        return socketPath ? l.handle.socketCpuEntries() : l.handle.cpuEntries();
    }

    private static int permits(S0Fixture.Listener l, boolean socketPath) {
        return socketPath ? l.handle.socketCpuPermitsAvailable()
                          : l.handle.cpuPermitsAvailable();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toByteArray();
    }

    /** The number of records in the bytes. */
    private static int records(byte[] bytes) {
        int n = 0;
        for (int i = 0; i + 5 <= bytes.length;
             i += 5 + (((bytes[i + 3] & 0xff) << 8) | (bytes[i + 4] & 0xff)))
        {
            n++;
        }
        return n;
    }

    /**
     * The handshake records of the bytes, each split into {@code pieces}
     * records (fewer if it has fewer bytes); other records are kept as they are.
     */
    private static byte[] reframe(byte[] bytes, int pieces) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int i = 0;
        while (i + 5 <= bytes.length) {
            int length = ((bytes[i + 3] & 0xff) << 8) | (bytes[i + 4] & 0xff);
            if (bytes[i] != 22) {
                out.write(bytes, i, 5 + length);
            } else {
                int at = 0;
                for (int p = 0; p < pieces && at < length; p++) {
                    int n = p == pieces - 1 ? length - at
                                            : Math.max(1, Math.min(length / pieces, length - at));
                    out.write(22);
                    out.write(bytes[i + 1]);
                    out.write(bytes[i + 2]);
                    out.write(n >>> 8);
                    out.write(n);
                    out.write(bytes, i + 5 + at, n);
                    at += n;
                }
            }
            i += 5 + length;
        }
        return out.toByteArray();
    }

    /** A handshake message of type 1 and the given body length, in records of {@code size} bytes. */
    private static byte[] junkHello(int body, int size) {
        byte[] message = new byte[4 + body];
        message[0] = 1;
        message[1] = (byte) (body >>> 16);
        message[2] = (byte) (body >>> 8);
        message[3] = (byte) body;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < message.length; i += size) {
            int n = Math.min(size, message.length - i);
            out.write(22);
            out.write(3);
            out.write(3);
            out.write(n >>> 8);
            out.write(n);
            out.write(message, i, n);
        }
        return out.toByteArray();
    }

    /**
     * Server-side CPU of one connection of a client that is made to retry and
     * then sends the bytes in place of its second ClientHello.
     */
    private static long cpuOfRetrySending(S0Fixture.Listener l, byte[] wire) throws Exception {
        try (S0EngineClient c = new S0EngineClient(l.port, RETRY)) {
            c.sendFlight();
            c.receiveFlight();
            Thread.sleep(100);
            long cpu0 = S0Fixture.processCpuNanos();
            try {
                c.send(wire);
            } catch (IOException refused) {
            }
            S0Fixture.awaitClosed(c.socket, 20_000);
            Thread.sleep(20);
            return S0Fixture.processCpuNanos() - cpu0;
        }
    }

    private static long bestCpuOfRetrySending(S0Fixture.Listener l, byte[] wire, int reps)
        throws Exception
    {
        long best = Long.MAX_VALUE;
        for (int rep = 0; rep < reps; rep++) {
            best = Math.min(best, cpuOfRetrySending(l, wire));
        }
        return best;
    }

    /** Waits until the listener holds nothing for the connections that were refused. */
    private static void assertNothingHeld(S0Fixture.Listener l, boolean socketPath)
        throws Exception
    {
        Assert.assertTrue("nothing in progress, socketPath=" + socketPath,
            S0Fixture.await(() -> l.handle.inProgressCount() == 0
                                  && l.handle.admittedConnections() == 0, 5000));
        Assert.assertEquals("no permit held, socketPath=" + socketPath,
                            1, permits(l, socketPath));
    }

    /**
     * After a HelloRetryRequest the second ClientHello arrives in records of
     * one byte.  The engine put such a message together a record at a time,
     * before the gate, and a message of 32 KB cost 200 ms to 3 s of processor
     * time, where the same bytes in records of 16 KiB cost nothing; the
     * second ClientHello is now read whole, like the first, and refused at
     * the ninth record as not a ClientHello that this server takes, on both
     * paths, without the processor of the gate or a permit, and at no cost.
     */
    @Test(timeout = 180000)
    public void testASecondClientHelloInRecordsOfOneByteIsRefusedAtOnce() throws Exception {
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = listen(socketPath)) {
                S0Fixture.connectLegit(l); // warm up
                byte[] flat = junkHello(32000, 16384);
                byte[] ones = junkHello(32000, 1);
                bestCpuOfRetrySending(l, flat, 2);
                long flatCost = bestCpuOfRetrySending(l, flat, 5);
                long entries = entries(l, socketPath);
                long junk = l.handle.counters().getFailed(HandshakeCounters.Kind.JUNK);
                long oneByteCost = bestCpuOfRetrySending(l, ones, 5);
                System.out.println("second ClientHello, socketPath=" + socketPath + ": "
                    + oneByteCost / 1_000_000 + " ms of CPU in records of one byte, "
                    + flatCost / 1_000_000 + " ms in records of 16 KiB");
                Assert.assertTrue("a second ClientHello of 32 KB in records of one byte cost "
                    + oneByteCost / 1_000_000 + " ms of CPU, in records of 16 KiB "
                    + flatCost / 1_000_000 + " ms, socketPath=" + socketPath,
                    oneByteCost <= 3 * flatCost + 200_000_000L);
                Assert.assertTrue("each is counted as junk, socketPath=" + socketPath,
                    S0Fixture.await(() -> l.handle.counters().getFailed(
                        HandshakeCounters.Kind.JUNK) == junk + 5, 5000));
                Assert.assertEquals("each took the gate for its first flight and no more, "
                    + "socketPath=" + socketPath, entries + 5, entries(l, socketPath));
                assertNothingHeld(l, socketPath);
            }
        }
    }

    /**
     * A second ClientHello in eight records is a ClientHello that the server
     * takes, as the first is, and in nine it is not: on the engine path as on
     * the SSLSocket path, whatever the engine would have made of it.
     */
    @Test(timeout = 120000)
    public void testASecondClientHelloInNineRecordsIsRefusedAndInEightIsServed()
        throws Exception
    {
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = listen(socketPath)) {
                S0Fixture.connectLegit(l); // warm up
                for (int pieces : new int[] { ClientHelloReader.MAX_RECORDS,
                                              ClientHelloReader.MAX_RECORDS + 1 })
                {
                    boolean served = pieces <= ClientHelloReader.MAX_RECORDS;
                    long entries = entries(l, socketPath);
                    long junk = l.handle.counters().getFailed(HandshakeCounters.Kind.JUNK);
                    int before = l.recorder.count();
                    try (S0EngineClient c = new S0EngineClient(l.port, RETRY)) {
                        c.sendFlight();
                        c.receiveFlight();
                        byte[] second = reframe(c.flight(), pieces);
                        Assert.assertEquals(pieces, records(second));
                        c.send(second);
                        if (served) {
                            c.receiveFlight();
                            c.complete();
                            c.sendApplicationByte();
                            Assert.assertTrue("served in " + pieces + " records, socketPath="
                                + socketPath, l.recorder.await(before + 1, 10_000));
                            Assert.assertEquals("a gate entry for each ClientHello",
                                                entries + 2, entries(l, socketPath));
                        } else {
                            long ms = S0Fixture.awaitClosed(c.socket, 5000);
                            Assert.assertTrue("refused in " + pieces + " records, socketPath="
                                + socketPath + ", closed after " + ms + " ms", ms >= 0);
                            Assert.assertTrue("counted as junk, socketPath=" + socketPath,
                                S0Fixture.await(() -> l.handle.counters().getFailed(
                                    HandshakeCounters.Kind.JUNK) == junk + 1, 5000));
                            Assert.assertEquals("no gate entry for it, socketPath=" + socketPath,
                                                entries + 1, entries(l, socketPath));
                            Assert.assertEquals(before, l.recorder.count());
                        }
                    }
                    assertNothingHeld(l, socketPath);
                }
            }
        }
    }

    /**
     * A client that is sent a HelloRetryRequest is served however it sends
     * its second ClientHello and the records round it: whole, in several
     * records, a few bytes at a time, with the ChangeCipherSpec before it in
     * the same write or in one of its own, or sent right behind the first
     * ClientHello, as some clients do.  Nothing is lost, repeated or put out
     * of order, and each ClientHello takes the gate once.
     */
    @Test(timeout = 180000)
    public void testASecondClientHelloIsServedHoweverItIsSent() throws Exception {
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = listen(socketPath)) {
                S0Fixture.connectLegit(l); // warm up
                for (int variant = 0; variant < 6; variant++) {
                    long entries = entries(l, socketPath);
                    int before = l.recorder.count();
                    String how;
                    try (S0EngineClient c = new S0EngineClient(l.port, RETRY)) {
                        byte[] first = c.flight();
                        c.send(variant == 5 ? concat(first, CCS) : first);
                        c.receiveFlight();
                        byte[] second = c.flight();
                        switch (variant) {
                            case 0:
                                how = "whole";
                                c.send(second);
                                break;
                            case 1:
                                how = "in three records";
                                c.send(reframe(second, 3));
                                break;
                            case 2:
                                how = "with a ChangeCipherSpec before it, in one write";
                                c.send(concat(CCS, second));
                                break;
                            case 3:
                                how = "with a ChangeCipherSpec before it, in a write of its own";
                                c.send(CCS);
                                Thread.sleep(150);
                                c.send(second);
                                break;
                            case 4:
                                how = "a few bytes at a time";
                                byte[] framed = reframe(second, ClientHelloReader.MAX_RECORDS);
                                for (int at = 0; at < framed.length; at += 7) {
                                    c.send(Arrays.copyOfRange(framed, at,
                                        Math.min(framed.length, at + 7)));
                                    if (at % 70 == 0) {
                                        Thread.sleep(2);
                                    }
                                }
                                break;
                            default:
                                how = "a ChangeCipherSpec right behind the first ClientHello";
                                c.send(second);
                                break;
                        }
                        c.receiveFlight();
                        c.complete();
                        c.sendApplicationByte();
                        Assert.assertTrue("served with the second ClientHello " + how
                            + ", socketPath=" + socketPath, l.recorder.await(before + 1, 10_000));
                        Assert.assertEquals("a gate entry for each ClientHello, " + how,
                                            entries + 2, entries(l, socketPath));
                    }
                    Assert.assertEquals("nothing failed, " + how, 0,
                                        l.handle.counters().getFailed());
                    assertNothingHeld(l, socketPath);
                }
            }
        }
    }

    /**
     * A client's flight in records of a few bytes each, which it can frame as
     * it likes because it has the keys of the handshake, was put together by
     * the engine, a record at a time, with nothing to bound it: 5000 records
     * of a Certificate message of 30 KB cost the server several hundred
     * milliseconds of processor time on either path.  The records are
     * counted, as they arrive, and a flight in many more records than its
     * bytes could need is refused at once as junk; the same flight in records
     * of the usual size, and in records of the smallest size a client may ask
     * for, is not.
     */
    @Test(timeout = 180000)
    public void testAClientFlightInTinyRecordsIsRefused() throws Exception {
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = listen(socketPath)) {
                S0Fixture.connectLegit(l); // warm up
                /*
                 * 80 more certificates of 380 bytes make a message of 30 KB, the
                 * most that the JDK takes.  The server does not accept the
                 * chain, but it reads all of it first.
                 */
                for (int packet : new int[] { 0, 534, 44 }) {
                    long junk = l.handle.counters().getFailed(HandshakeCounters.Kind.JUNK);
                    long failed = l.handle.counters().getFailed();
                    try (S0EngineClient c = new S0EngineClient(l.port,
                             S0Fixture.clientEngineWithLongChain(80)))
                    {
                        c.sendAll();
                        c.receiveFlight();
                        c.sendAll();
                        c.receiveFlight();
                        if (packet > 0) {
                            c.setMaximumPacketSize(packet);
                        }
                        byte[] flight = c.flight();
                        if (packet == 44) {
                            Assert.assertTrue("records of a few bytes: " + records(flight),
                                              records(flight) > 3000);
                        } else if (packet == 534) {
                            Assert.assertTrue("records of the smallest size: " + records(flight),
                                              records(flight) > 50 && records(flight) < 200);
                        }
                        try {
                            c.send(flight);
                        } catch (IOException refused) {
                        }
                        Assert.assertTrue("the server ended the handshake, packet=" + packet
                            + ", socketPath=" + socketPath, S0Fixture.await(
                                () -> l.handle.counters().getFailed() > failed, 10_000));
                    }
                    long expected = packet == 44 ? junk + 1 : junk;
                    Assert.assertEquals("refused as junk only the tiny records, packet=" + packet
                        + ", socketPath=" + socketPath, expected,
                        l.handle.counters().getFailed(HandshakeCounters.Kind.JUNK));
                    assertNothingHeld(l, socketPath);
                }
            }
        }
    }

    /**
     * The wire bytes of {@code n} records of application data of one byte
     * each, as the client's engine makes them, which are 39 bytes each.
     */
    private static byte[] applicationRecords(S0EngineClient c, int n) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        java.nio.ByteBuffer net = java.nio.ByteBuffer.allocate(
            c.engine.getSession().getPacketBufferSize());
        for (int i = 0; i < n; i++) {
            net.clear();
            c.engine.wrap(java.nio.ByteBuffer.wrap(new byte[] { (byte) i }), net);
            net.flip();
            byte[] record = new byte[net.remaining()];
            net.get(record);
            out.write(record);
        }
        return out.toByteArray();
    }

    /**
     * After the ClientHello has been sent: everything that the client sends
     * until it has sent its Finished, with what the server sends in between
     * read, and nothing of it sent.
     */
    private static byte[] restOfTheHandshake(S0EngineClient c) throws Exception {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        while (!c.finished()) {
            if (c.engine.getHandshakeStatus()
                == javax.net.ssl.SSLEngineResult.HandshakeStatus.NEED_UNWRAP)
            {
                c.receiveFlight();
            } else {
                all.write(c.flight());
            }
        }
        return all.toByteArray();
    }

    /**
     * A client that sends its application data right behind its Finished, in
     * the same write, is served however many records that is: the records of
     * the handshake are counted, and the engine path counted every byte that a
     * read of the channel brought in, so that twenty records of one byte
     * (39 bytes each) behind the Finished were refused as junk, where the
     * SSLSocket path, which counts what the JDK takes for the handshake,
     * served them.  The bound is on the records the handshake consumes.
     */
    @Test(timeout = 180000)
    public void testApplicationRecordsPipelinedBehindTheFinishedAreServed() throws Exception {
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = listen(socketPath)) {
                S0Fixture.connectLegit(l); // warm up
                for (int n : new int[] { 1, 14, 20, 40, 200 }) {
                    long junk = l.handle.counters().getFailed(HandshakeCounters.Kind.JUNK);
                    long failed = l.handle.counters().getFailed();
                    int before = l.recorder.count();
                    try (S0EngineClient c = new S0EngineClient(l.port)) {
                        c.sendFlight();
                        byte[] flight = restOfTheHandshake(c);
                        byte[] application = applicationRecords(c, n);
                        Assert.assertEquals("records of one byte are 39 bytes",
                                            n * 39, application.length);
                        c.send(concat(flight, application));
                        Assert.assertTrue(n + " records of application data behind the Finished"
                            + " were not served, socketPath=" + socketPath,
                            l.recorder.await(before + 1, 10_000));
                        Thread.sleep(100);
                    }
                    Assert.assertEquals("none of them is junk, n=" + n + ", socketPath="
                        + socketPath, junk,
                        l.handle.counters().getFailed(HandshakeCounters.Kind.JUNK));
                    Assert.assertEquals("nothing failed, n=" + n + ", socketPath=" + socketPath,
                        failed, l.handle.counters().getFailed());
                    assertNothingHeld(l, socketPath);
                }
            }
        }
    }

    /**
     * The engine path reads the second ClientHello from the channel alone and
     * puts it after what its buffer holds, which is nothing when the
     * HelloRetryRequest has been written.  If bytes were there, they would
     * be put in front of the ClientHello, outside the checks of its reader, so
     * the handshake fails instead.  Nothing sends such bytes today; the seam is
     * {@code prime}, called from the gate when the flight is out.
     */
    @Test(timeout = 60000)
    public void testBytesAheadOfTheSecondClientHelloFailTheHandshake() throws Exception {
        try (S0Fixture.Listener l = listen(false);
             ServerSocketChannel ssc = ServerSocketChannel.open())
        {
            ssc.bind(new InetSocketAddress("localhost", 0));
            AtomicReference<Throwable> outcome = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            Thread server = new Thread(() -> {
                try (SocketChannel ch = ssc.accept()) {
                    SSLEngine engine = l.endpoint.getSSLContext().createSSLEngine();
                    engine.setUseClientMode(false);
                    engine.setNeedClientAuth(true);
                    SSLParameters p = engine.getSSLParameters();
                    p.setProtocols(new String[] { "TLSv1.3" });
                    p.setNamedGroups(new String[] { "secp256r1" });
                    engine.setSSLParameters(p);
                    SslEngineChannel sec = new SslEngineChannel(engine, ch, "test");
                    sec.prime(ClientHelloReader.read(ch));
                    boolean[] primed = new boolean[1];
                    sec.handshakeServer(new SslEngineChannel.ServerGate() {
                        public void begin() { }
                        public void end() { }
                        public void flightWritten() {
                            if (!primed[0]) {
                                primed[0] = true;
                                sec.prime(new byte[] { 23 });
                            }
                        }
                    });
                    outcome.set(new AssertionError("the handshake went on"));
                } catch (Throwable t) {
                    outcome.set(t);
                } finally {
                    done.countDown();
                }
            });
            server.start();
            try (S0EngineClient c = new S0EngineClient(ssc.socket().getLocalPort(), RETRY)) {
                c.sendFlight();
                c.receiveFlight();
                c.sendAll();
                Assert.assertTrue("the handshake ended", done.await(20, TimeUnit.SECONDS));
            }
            Throwable t = outcome.get();
            Assert.assertTrue("an SSLException: " + t, t instanceof SSLException);
            Assert.assertTrue(t.getMessage(), t.getMessage().contains(
                "1 bytes of the peer are already buffered ahead of its second ClientHello"));
        }
    }

    /** A client that asks for the smallest records is served, on both paths. */
    @Test(timeout = 120000)
    public void testAClientThatAsksForSmallRecordsIsServed() throws Exception {
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = listen(socketPath)) {
                S0Fixture.connectLegit(l); // warm up
                int before = l.recorder.count();
                try (S0EngineClient c = new S0EngineClient(l.port)) {
                    c.setMaximumPacketSize(534);
                    c.sendFlight();
                    c.receiveFlight();
                    c.complete();
                    c.sendApplicationByte();
                    Assert.assertTrue("served, socketPath=" + socketPath,
                                      l.recorder.await(before + 1, 10_000));
                }
                Assert.assertEquals(0, l.handle.counters().getFailed());
                assertNothingHeld(l, socketPath);
            }
        }
    }

    private static byte[] record(int type, int body) {
        byte[] record = new byte[5 + body];
        record[0] = (byte) type;
        record[1] = 3;
        record[2] = 3;
        record[3] = (byte) (body >>> 8);
        record[4] = (byte) body;
        return record;
    }

    /** The records are counted wherever the chunks of bytes that carry them end. */
    @Test
    public void testRecordsAreCountedWhereverTheChunksEnd() throws Exception {
        int free = RecordCounter.FREE;
        byte[] empty = record(23, 0);
        byte[] wire = new byte[empty.length * (free + 1)];
        for (int i = 0; i <= free; i++) {
            System.arraycopy(empty, 0, wire, i * empty.length, empty.length);
        }
        for (int chunk : new int[] { 1, 2, 4, 5, 6, 7, 13, wire.length }) {
            RecordCounter counter = new RecordCounter();
            counter.count(wire, 0, free * empty.length);
            try {
                for (int at = free * empty.length; at < wire.length; at += chunk) {
                    counter.count(wire, at, Math.min(chunk, wire.length - at));
                }
                Assert.fail("the record after the free ones was accepted, chunk=" + chunk);
            } catch (ClientHelloReader.NotTlsException refused) {
                Assert.assertTrue(refused.getMessage(), refused.getMessage().contains(
                    (free + 1) + " in " + wire.length + " bytes"));
            }
            RecordCounter within = new RecordCounter();
            for (int at = 0; at < free * empty.length; at += chunk) {
                within.count(wire, at, Math.min(chunk, free * empty.length - at));
            }
        }
    }

    /**
     * Every framing that the standard allows is within the bound: records of
     * the longest size, and of the smallest that a client may ask for, 512
     * bytes and what the cipher adds to them, any number of them, as well as
     * the records of a small message; and records that carry little more than
     * their headers are not.
     */
    @Test
    public void testRecordsOfEveryUsualSizeAreWithinTheBound() throws Exception {
        RecordCounter big = new RecordCounter();
        byte[] sixteen = record(23, 16384 + 256);
        for (int i = 0; i < 200; i++) {
            big.count(sixteen, 0, sixteen.length);
        }
        RecordCounter small = new RecordCounter();
        byte[] smallest = record(23, 512 + 22);
        for (int i = 0; i < 5000; i++) {
            small.count(smallest, 0, smallest.length);
        }
        RecordCounter flight = new RecordCounter();
        flight.count(record(20, 1), 0, 6);
        flight.count(record(23, 40), 0, 45);
        flight.count(record(23, 700), 0, 705);
        flight.count(record(21, 2), 0, 7);
        RecordCounter tiny = new RecordCounter();
        byte[] few = record(23, 20);
        try {
            for (int i = 0; i < 200; i++) {
                tiny.count(few, 0, few.length);
            }
            Assert.fail("200 records of 25 bytes");
        } catch (ClientHelloReader.NotTlsException refused) {
        }
    }
}
