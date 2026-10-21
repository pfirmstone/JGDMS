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
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The server's choice of named groups and the bound on the CPU that a peer
 * can make a server spend before it is authenticated.
 */
public class SslServerHandshakeCpuTest {

    private static final String GROUPS = SslServerEndpointImpl
        .SslListenHandle.SERVER_NAMED_GROUPS_PROPERTY;
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

    @Test
    public void testParseNamedGroups() {
        Assert.assertArrayEquals(new String[] { "a", "b" },
            SslServerEndpointImpl.SslListenHandle.parseNamedGroups(" a, b ,,"));
        Assert.assertNull(SslServerEndpointImpl.SslListenHandle.parseNamedGroups(""));
        Assert.assertNull(SslServerEndpointImpl.SslListenHandle.parseNamedGroups(" , "));
        Assert.assertNull(SslServerEndpointImpl.SslListenHandle.parseNamedGroups(null));
    }

    /**
     * The groups that the server negotiates by default are accepted, those the
     * JVM also enables but that cost the server several times as much are
     * refused, on both server paths.
     */
    @Test(timeout = 60000)
    public void testDefaultNamedGroups() throws Exception {
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = socketPath
                     ? S0Fixture.listenWithSocketFactory() : S0Fixture.listen())
            {
                String what = "socketPath=" + socketPath;
                Assert.assertTrue("default offer, " + what,
                                  S0Fixture.connectOffering(l, new String[0]));
                Assert.assertTrue("x25519, " + what,
                                  S0Fixture.connectOffering(l, "x25519"));
                Assert.assertTrue("secp256r1, " + what,
                                  S0Fixture.connectOffering(l, "secp256r1"));
                Assert.assertTrue("X25519MLKEM768, " + what,
                                  S0Fixture.connectOffering(l, "X25519MLKEM768"));
                int handed = l.recorder.count();
                Assert.assertFalse("secp521r1 only, " + what,
                                   S0Fixture.connectOffering(l, "secp521r1"));
                Assert.assertFalse("ffdhe8192 only, " + what,
                                   S0Fixture.connectOffering(l, "ffdhe8192"));
                Assert.assertFalse("secp384r1 only, " + what,
                                   S0Fixture.connectOffering(l, "secp384r1"));
                Assert.assertEquals(handed, l.recorder.count());
                Assert.assertTrue(S0Fixture.await(() -> l.handle.counters()
                    .getFailed(HandshakeCounters.Kind.TLS) >= 3, 3000));
            }
        }
    }

    /** The groups are set by a property, and an empty value pins nothing. */
    @Test(timeout = 60000)
    public void testNamedGroupsProperty() throws Exception {
        try (S0Fixture.Listener l = S0Fixture.withProperty(GROUPS, "secp521r1",
                 () -> S0Fixture.listen()))
        {
            Assert.assertTrue(S0Fixture.connectOffering(l, "secp521r1"));
            Assert.assertFalse(S0Fixture.connectOffering(l, "x25519"));
            Assert.assertFalse(S0Fixture.connectOffering(l, "secp256r1"));
        }
        try (S0Fixture.Listener l = S0Fixture.withProperty(GROUPS, "",
                 () -> S0Fixture.listen()))
        {
            Assert.assertTrue("not pinned: secp521r1",
                              S0Fixture.connectOffering(l, "secp521r1"));
            Assert.assertTrue("not pinned: ffdhe8192",
                              S0Fixture.connectOffering(l, "ffdhe8192"));
        }
    }

    /** Records the records of a logger while it runs. */
    private static final class Records extends java.util.logging.Handler {
        final List<java.util.logging.LogRecord> records = new ArrayList<>();

        public synchronized void publish(java.util.logging.LogRecord r) {
            records.add(r);
        }

        public void flush() { }

        public void close() { }

        synchronized String text() {
            StringBuilder sb = new StringBuilder();
            for (java.util.logging.LogRecord r : records) {
                if (r.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()) {
                    sb.append(new java.util.logging.SimpleFormatter().formatMessage(r))
                      .append('\n');
                }
            }
            return sb.toString();
        }
    }

    /**
     * A value that names no group the engine supports must not leave a
     * listener at which every handshake fails: the groups are not pinned, and
     * a warning says so.  Names that are not supported are dropped from a list
     * that has others, with a warning; the spelling of a name may differ in
     * case.
     */
    @Test(timeout = 60000)
    public void testNamedGroupsThatTheEngineDoesNotSupportAreNotPinned() throws Exception {
        Records records = new Records();
        Utilities.SERVER_LOGGER.addHandler(records);
        try {
            try (S0Fixture.Listener l = S0Fixture.withProperty(GROUPS, "x25519mlkem",
                     () -> S0Fixture.listen()))
            {
                Assert.assertTrue("a typo does not refuse every client",
                                  S0Fixture.connectOffering(l, new String[0]));
                Assert.assertTrue("and pins nothing",
                                  S0Fixture.connectOffering(l, "ffdhe8192"));
                Assert.assertTrue(records.text(), records.text().contains("x25519mlkem"));
                Assert.assertTrue(records.text(), records.text().contains("does not restrict"));
            }
            try (S0Fixture.Listener l = S0Fixture.withProperty(GROUPS, "SECP256R1, bogus",
                     () -> S0Fixture.listen()))
            {
                Assert.assertTrue("the group that is supported",
                                  S0Fixture.connectOffering(l, "secp256r1"));
                Assert.assertFalse("others are not offered",
                                   S0Fixture.connectOffering(l, "x25519"));
                Assert.assertTrue(records.text(), records.text().contains("bogus"));
            }
            for (boolean socketPath : new boolean[] { false, true }) {
                try (S0Fixture.Listener l = S0Fixture.withProperty(GROUPS, "nonsense",
                         () -> socketPath ? S0Fixture.listenWithSocketFactory()
                                          : S0Fixture.listen()))
                {
                    Assert.assertTrue("socketPath=" + socketPath,
                                      S0Fixture.connectOffering(l, "secp521r1"));
                }
            }
        } finally {
            Utilities.SERVER_LOGGER.removeHandler(records);
        }
    }

    /**
     * Server threads are virtual threads that run on the carrier threads of
     * the default scheduler, and the handshake compute threads do their
     * cryptography; returns the CPU time that they have used.
     */
    static long carrierCpuNanos() {
        return cpuNanos(true, true);
    }

    /**
     * The CPU time that the handshake compute threads have used: the work
     * that the permits gate, when the cryptography is not done inline.
     */
    static long computePoolCpuNanos() {
        return cpuNanos(false, true);
    }

    private static long cpuNanos(boolean carriers, boolean pool) {
        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        long total = 0L;
        long[] ids = mx.getAllThreadIds();
        ThreadInfo[] infos = mx.getThreadInfo(ids);
        for (int i = 0; i < ids.length; i++) {
            if (infos[i] != null && (carriers
                && infos[i].getThreadName().startsWith("ForkJoinPool")
                && infos[i].getThreadName().contains("worker")
                || pool && infos[i].getThreadName().startsWith("TLS handshake compute")))
            {
                long cpu = mx.getThreadCpuTime(ids[i]);
                if (cpu > 0) {
                    total += cpu;
                }
            }
        }
        return total;
    }

    /** Replays a ClientHello as fast as it can, as an attacker would. */
    static final class Flood {
        final AtomicBoolean stop = new AtomicBoolean();
        final AtomicLong attempts = new AtomicLong();
        final AtomicLong flights = new AtomicLong();
        final List<Thread> threads = new ArrayList<>();

        Flood(int port, byte[] hello, int n) {
            this(port, hello, n, Long.MAX_VALUE);
        }

        /**
         * Replays no more than {@code maxAttempts} times in all, so that a
         * flood against a listener without limits, which on a fast machine
         * replays several thousand times a second, does not use up the
         * ephemeral ports of the machine.
         */
        Flood(int port, byte[] hello, int n, long maxAttempts) {
            for (int i = 0; i < n; i++) {
                threads.add(Thread.ofPlatform().name("flood-" + i).start(() -> {
                    byte[] buf = new byte[4096];
                    while (!stop.get() && attempts.incrementAndGet() <= maxAttempts) {
                        try (Socket s = new Socket()) {
                            s.connect(new InetSocketAddress("localhost", port), 2000);
                            s.setSoTimeout(2000);
                            OutputStream out = s.getOutputStream();
                            out.write(hello);
                            out.flush();
                            if (s.getInputStream().read(buf) > 0) {
                                flights.incrementAndGet();
                            }
                        } catch (IOException ignore) {
                        }
                    }
                }));
            }
        }

        void stop() throws InterruptedException {
            stop.set(true);
            for (Thread t : threads) {
                t.join(10_000);
            }
        }

        /** Whether it has made all the replays that it may. */
        boolean done() {
            for (Thread t : threads) {
                if (t.isAlive()) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Sleeps until the time given by {@link System#nanoTime}, or until all of
     * the floods have made their replays, whichever is first.
     */
    static void sleepUntil(long nanoTime, List<Flood> floods)
        throws InterruptedException
    {
        for (;;) {
            boolean done = true;
            for (Flood f : floods) {
                done &= f.done();
            }
            long left = nanoTime - System.nanoTime();
            if (done || left <= 0L) {
                return;
            }
            Thread.sleep(Math.min(20L, left / 1_000_000L + 1L));
        }
    }

    /** What a flood against some listeners costs the server, over its windows. */
    static final class FloodResult {
        long cpuNanos;
        long poolCpuNanos;
        long wallNanos;
        long attempts;
        long flights;
        long legitMillis;

        /** The server's CPU, in cores, while it was flooded. */
        double cores() {
            return wallNanos > 0L ? (double) cpuNanos / wallNanos : 0.0;
        }

        /** The CPU of the handshake compute threads alone, in cores. */
        double poolCores() {
            return wallNanos > 0L ? (double) poolCpuNanos / wallNanos : 0.0;
        }
    }

    /** The replays that one window of a flood makes at most, over all of its listeners. */
    private static final long WINDOW_REPLAYS = 2500L;
    private static final long WINDOW_NANOS = 1_500_000_000L;
    private static final int ROUNDS = 4;

    /**
     * A number of permits that gates nothing: more than a flood has threads.
     * What a flood costs with so many is what it costs without the permits.
     */
    static final String UNGATED = "64";

    /**
     * The threads that do the cryptography of the engine path in the floods:
     * more than a flood has permits, so that the permits are what limits it.
     */
    private static final String COMPUTE_THREADS = "8";

    /**
     * The most that a flood may cost with the permits, as a fraction of what
     * the same flood costs without them, in the same run on the same machine.
     * The permits bound the time that the flood spends in the work they gate,
     * the key exchange and signature of the first flight, to one holder at a
     * time.  The rest (reading the record, starting the thread, the system
     * calls, the close) is done at the rate of the flights and is not gated,
     * so what a flood costs with the permits is not their number of cores but
     * that and an amount that follows the rate, which differs by machine and
     * operating system; a limit on cores that was fixed in the test was met on
     * the machine that it was tuned on and missed on another.  On four
     * processors the fraction was 0.4 to 0.7, and with the permits removed
     * 0.95 to 1.0.  The bound that does not depend on the rate is the
     * one on the gated work alone, {@link #assertGatedWorkWithinPermits}.
     */
    static final double GATED_FRACTION = 0.8;

    /**
     * Whether a flood without the permits used enough for a comparison to
     * show anything.  With one permit a flood has cost up to 1.4 cores, so a
     * flood that costs less than three quarters of a core more than the
     * permits without them cannot be told from one that they held back.
     */
    static boolean ungatedShows(double ungatedCores, int permits) {
        return ungatedCores >= permits + 0.75;
    }

    /** The times that the comparison is made again, if the machine was too busy to show it. */
    private static final int ATTEMPTS = 3;

    /**
     * Asserts that the permits limit what a replay flood costs the server:
     * the flood costs clearly less with them than without, on this machine.
     * Where the flood without them does not use more than the permits allow
     * (a machine with so few processors, or so busy, that the flood and the
     * server fill them) it shows nothing, and the test is skipped.
     */
    static void assertGatedCostsLess(String what, int permits,
                                     FloodResult gated, FloodResult ungated)
    {
        System.out.println(what + ": " + permits + " permits " + gated.cores()
            + " cores, " + gated.flights + " flights; without " + ungated.cores()
            + " cores, " + ungated.flights + " flights; ratio "
            + gated.cores() / ungated.cores());
        Assert.assertTrue("the flood got flights: " + gated.flights
            + " and " + ungated.flights,
            gated.flights > 100 && ungated.flights > 100);
        Assume.assumeTrue("a flood without the permits used " + ungated.cores()
            + " cores here, which shows nothing about " + permits + " permits",
            ungatedShows(ungated.cores(), permits));
        Assert.assertTrue(what + " with " + permits + " permits: "
            + gated.cores() + " cores, without them " + ungated.cores()
            + " (more than " + GATED_FRACTION + " of it)",
            gated.cores() <= GATED_FRACTION * ungated.cores());
    }

    /**
     * Asserts that the work the permits gate, which on the engine path is
     * done by the compute threads while the handshake holds a permit and
     * waits for it, never used more processors than there are permits.  This
     * holds on every machine however fast the flood comes: the holders take
     * turns, so the compute threads cannot use more than a processor for
     * each, whatever else the server does at the rate of the flights.  (A
     * little over allows for the legitimate client's second flight, which is
     * not gated.)
     */
    static void assertGatedWorkWithinPermits(String what, int permits,
                                             FloodResult gated)
    {
        System.out.println(what + ": compute threads " + gated.poolCores()
            + " cores of " + gated.cores() + " with " + permits + " permits");
        Assert.assertTrue(what + ": the compute threads did the gated work: "
            + gated.poolCores(), gated.poolCores() > 0.0);
        Assert.assertTrue(what + ": the gated work used " + gated.poolCores()
            + " cores with " + permits + " permits",
            gated.poolCores() <= permits + 0.15);
    }

    /** Floods the listeners for one window and adds what it cost to the result. */
    private static void window(List<S0Fixture.Listener> listeners, byte[] hello,
                               int threads, FloodResult result, boolean legit)
        throws Exception
    {
        List<Flood> floods = new ArrayList<>();
        for (S0Fixture.Listener l : listeners) {
            floods.add(new Flood(l.port, hello, threads,
                                 WINDOW_REPLAYS / listeners.size()));
        }
        long cpu0 = carrierCpuNanos();
        long pool0 = computePoolCpuNanos();
        long wall0 = System.nanoTime();
        if (legit) {
            /* A legitimate client connects in the middle of the flood. */
            sleepUntil(wall0 + WINDOW_NANOS / 2, floods);
            result.legitMillis = S0Fixture.connectLegit(listeners.get(0));
        }
        sleepUntil(wall0 + WINDOW_NANOS, floods);
        result.cpuNanos += carrierCpuNanos() - cpu0;
        result.poolCpuNanos += computePoolCpuNanos() - pool0;
        result.wallNanos += System.nanoTime() - wall0;
        for (Flood f : floods) {
            f.stop();
            result.attempts += f.attempts.get();
            result.flights += f.flights.get();
        }
        for (S0Fixture.Listener l : listeners) {
            Assert.assertTrue(S0Fixture.await(
                () -> l.handle.inProgressCount() == 0, 15_000));
        }
    }

    /**
     * Floods listeners that have the given number of permits and listeners
     * that have so many that they gate nothing, one set after the other in
     * short windows, so that both are flooded in the same JVM, with the same
     * compiled code, and while the machine does whatever else it does.  On
     * the engine path the cryptography is done by eight compute threads, more
     * than the default pool (one on four processors) and than the permits, so
     * that the pool does not bound what a flood can use and the permits do;
     * the SSLSocket path does it on the handshake's own thread.
     *
     * @param threads the number of threads of the flood against each listener
     * @return what it cost with the permits, and without them
     */
    static FloodResult[] floodWithAndWithoutPermits(
        String permits, int listeners, int threads, boolean socketPath)
        throws Exception
    {
        Assume.assumeTrue("a flood cannot be shown to use more than " + permits
            + " permits allow on " + Runtime.getRuntime().availableProcessors()
            + " processors", Runtime.getRuntime().availableProcessors()
                              >= Integer.parseInt(permits) + 2);
        byte[] hello = S0Fixture.captureClientHello("x25519");
        String[] settings = { permits, UNGATED };
        List<List<S0Fixture.Listener>> sets = new ArrayList<>();
        try {
            for (String setting : settings) {
                List<S0Fixture.Listener> set = new ArrayList<>();
                sets.add(set);
                for (int i = 0; i < listeners; i++) {
                    S0Fixture.Listener l = S0Fixture.withProperties(new String[] {
                            PERMITS, setting, HandshakeCompute.THREADS_PROPERTY,
                            socketPath ? "0" : COMPUTE_THREADS },
                        () -> socketPath ? S0Fixture.listenWithSocketFactory()
                                         : S0Fixture.listen());
                    set.add(l);
                    Assert.assertEquals(Integer.parseInt(setting), l.handle.cpuPermits());
                    S0Fixture.connectLegit(l); // warm up
                    S0Fixture.connectLegit(l);
                }
            }
            for (int i = 0; i < settings.length; i++) {
                window(sets.get(i), hello, threads, new FloodResult(), false);
            }
            FloodResult[] results = null;
            long legitMillis = 0L;
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                results = new FloodResult[] { new FloodResult(), new FloodResult() };
                for (int round = 0; round < ROUNDS; round++) {
                    for (int k = 0; k < settings.length; k++) {
                        int i = round % 2 == 0 ? k : settings.length - 1 - k;
                        window(sets.get(i), hello, threads, results[i],
                               i == 0 && round == 0 && attempt == 0);
                    }
                }
                if (attempt == 0) {
                    legitMillis = results[0].legitMillis;
                }
                if (ungatedShows(results[1].cores(), Integer.parseInt(permits))) {
                    break;
                }
            }
            results[0].legitMillis = legitMillis;
            for (List<S0Fixture.Listener> set : sets) {
                for (S0Fixture.Listener l : set) {
                    Assert.assertTrue(S0Fixture.await(() ->
                        l.handle.inProgressCount() == 0
                        && l.handle.cpuPermitsAvailable() == l.handle.cpuPermits()
                        && l.handle.socketCpuPermitsAvailable()
                           == l.handle.cpuPermits(), 15_000));
                    Assert.assertTrue("the permits of the path in use were taken",
                        socketPath
                        ? l.handle.socketCpuEntries() > 0 && l.handle.cpuEntries() == 0
                        : l.handle.cpuEntries() > 0 && l.handle.socketCpuEntries() == 0);
                }
            }
            return results;
        } finally {
            for (List<S0Fixture.Listener> set : sets) {
                for (S0Fixture.Listener l : set) {
                    l.close();
                }
            }
        }
    }

    /**
     * A peer that replays a captured ClientHello over and over, reading the
     * server's first flight and closing, makes the server do the key exchange
     * and a signature each time at no cost to itself.  Each connection lasts a
     * few milliseconds, so the limit on the handshakes at once does not stop
     * it; the permits do, and the flood costs clearly less with them than
     * without.
     */
    @Test(timeout = 180000)
    public void testReplayFloodIsBoundedByTheCpuPermits() throws Exception {
        FloodResult[] r = floodWithAndWithoutPermits("1", 1, 6, false);
        System.out.println("replay flood, 1 permit: " + r[0].attempts
            + " replays, legitimate open " + r[0].legitMillis);
        Assert.assertTrue("a legitimate client must complete during a flood: "
            + r[0].legitMillis + " ms", r[0].legitMillis < 5000);
        assertGatedWorkWithinPermits("replay flood", 1, r[0]);
        assertGatedCostsLess("replay flood", 1, r[0], r[1]);
    }

    /**
     * The same flood against an endpoint that uses SSLSockets, as HTTPS and
     * endpoints with a server socket factory do, costs less with one permit
     * than without: the whole of the handshake is inside the gate.
     */
    @Test(timeout = 180000)
    public void testReplayFloodOnTheSocketPathIsBoundedByTheCpuPermits() throws Exception {
        FloodResult[] r = floodWithAndWithoutPermits("1", 1, 6, true);
        System.out.println("replay flood, socket path, 1 permit: " + r[0].attempts
            + " replays, legitimate open " + r[0].legitMillis);
        Assert.assertTrue("a legitimate client must complete during a flood: "
            + r[0].legitMillis + " ms", r[0].legitMillis < 5000);
        assertGatedCostsLess("replay flood, socket path", 1, r[0], r[1]);
    }

    /**
     * Peers that send a ClientHello to an endpoint that uses SSLSockets and
     * then wait hold its permits until they time out, since the JDK does the
     * waiting inside the handshake; they must not hold up the endpoints that
     * use SSLEngines, which have permits of their own.
     */
    @Test(timeout = 60000)
    public void testStalledSocketPathPeersDoNotStarveTheEnginePath() throws Exception {
        byte[] hello = S0Fixture.captureClientHello("x25519");
        S0Fixture.Listener socketListener = S0Fixture.withProperties(
            new String[] { PERMITS, "1", S0Fixture.HANDSHAKE_TIMEOUT, "8000" },
            () -> S0Fixture.listenWithSocketFactory());
        S0Fixture.Listener engineListener = S0Fixture.withProperties(
            new String[] { PERMITS, "1", S0Fixture.HANDSHAKE_TIMEOUT, "8000" },
            () -> S0Fixture.listen());
        try (S0Fixture.Listener sl = socketListener; S0Fixture.Listener el = engineListener) {
            S0Fixture.connectLegit(el); // warm up
            S0Fixture.connectLegit(sl);
            List<Socket> stalled = new ArrayList<>();
            try {
                Socket s = new Socket("localhost", sl.port);
                stalled.add(s);
                s.getOutputStream().write(hello);
                s.getOutputStream().flush();
                s.setSoTimeout(5000);
                Assert.assertTrue("a flight", s.getInputStream().read(new byte[4096]) > 0);
                Assert.assertEquals("the permit is held while it waits", 0,
                                    sl.handle.socketCpuPermitsAvailable());
                long millis = S0Fixture.connectLegit(el);
                Assert.assertTrue("the engine path was served at once: " + millis
                    + " ms", millis < 3000);
                Assert.assertEquals(el.handle.cpuPermits(),
                                    el.handle.cpuPermitsAvailable());
            } finally {
                S0Fixture.closeAll(stalled);
            }
            Assert.assertTrue("the permit comes back", S0Fixture.await(() ->
                sl.handle.socketCpuPermitsAvailable() == sl.handle.cpuPermits()
                && sl.handle.inProgressCount() == 0, 10_000));
        }
    }

    /**
     * The permit to compute is given back when the server's first flight is
     * written, not when the handshake ends: a peer that reads the flight and
     * then waits holds a handshake, not the processor.
     */
    @Test(timeout = 60000)
    public void testPermitIsReleasedWhenTheFlightIsWritten() throws Exception {
        byte[] hello = S0Fixture.captureClientHello("x25519");
        try (S0Fixture.Listener l = S0Fixture.listen()) {
            S0Fixture.connectLegit(l); // warm up
            long entries = l.handle.cpuEntries();
            try (Socket s = new Socket("localhost", l.port)) {
                s.getOutputStream().write(hello);
                s.getOutputStream().flush();
                s.setSoTimeout(5000);
                Assert.assertTrue("a flight", s.getInputStream().read(new byte[4096]) > 0);
                Thread.sleep(300);
                Assert.assertEquals("the handshake waits for the client", 1,
                                    l.handle.inProgressCount());
                Assert.assertEquals("permits while it waits",
                    l.handle.cpuPermits(), l.handle.cpuPermitsAvailable());
                Assert.assertEquals("entered once", entries + 1,
                                    l.handle.cpuEntries());
            }
            Assert.assertTrue(S0Fixture.await(
                () -> l.handle.inProgressCount() == 0, 5000));
        }
    }

    /**
     * The permit is not taken while the server waits for the rest of a
     * ClientHello: a peer that sends the hello a few bytes at a time holds a
     * handshake, not the processor.
     */
    @Test(timeout = 60000)
    public void testPermitIsNotHeldWhileWaitingForTheHello() throws Exception {
        byte[] hello = S0Fixture.captureClientHello("x25519");
        try (S0Fixture.Listener l = S0Fixture.listen()) {
            S0Fixture.connectLegit(l); // warm up
            long entries = l.handle.cpuEntries();
            try (Socket s = new Socket("localhost", l.port)) {
                s.setTcpNoDelay(true);
                int[] cuts = { 3, 6, 40, hello.length - 1 };
                int from = 0;
                for (int cut : cuts) {
                    s.getOutputStream().write(hello, from, cut - from);
                    s.getOutputStream().flush();
                    from = cut;
                    Thread.sleep(250);
                    Assert.assertEquals("permits after " + cut + " bytes of "
                        + hello.length, l.handle.cpuPermits(),
                        l.handle.cpuPermitsAvailable());
                    Assert.assertEquals(entries, l.handle.cpuEntries());
                }
                s.getOutputStream().write(hello, from, hello.length - from);
                s.getOutputStream().flush();
                s.setSoTimeout(5000);
                Assert.assertTrue("a flight", s.getInputStream().read(new byte[4096]) > 0);
                Assert.assertEquals(entries + 1, l.handle.cpuEntries());
            }
        }
    }

    /**
     * A full handshake enters the gate once: the client's second flight, with
     * its certificate to verify, is not inside it.  A second ClientHello that
     * answers a HelloRetryRequest is.
     */
    @Test(timeout = 60000)
    public void testGateIsEnteredOncePerClientHello() throws Exception {
        for (boolean socketPath : new boolean[] { false }) {
            try (S0Fixture.Listener l = S0Fixture.withProperty(GROUPS, "secp256r1",
                     () -> S0Fixture.listen()))
            {
                Assert.assertTrue(S0Fixture.connectOffering(l, "secp256r1"));
                long entries = l.handle.cpuEntries();
                Assert.assertTrue(S0Fixture.connectOffering(l, "secp256r1"));
                Assert.assertEquals("a full handshake", entries + 1,
                                    l.handle.cpuEntries());
                entries = l.handle.cpuEntries();
                /* x25519 is preferred and has a key share; the server wants P-256. */
                Assert.assertTrue(S0Fixture.connectOffering(l,
                    "x25519", "secp384r1", "secp256r1"));
                Assert.assertEquals("after a HelloRetryRequest", entries + 2,
                                    l.handle.cpuEntries());
                Assert.assertTrue(S0Fixture.await(() ->
                    l.handle.cpuPermitsAvailable() == l.handle.cpuPermits(), 3000));
            }
        }
    }

    /** Permits are back after hellos that are replayed and abandoned. */
    @Test(timeout = 60000)
    public void testPermitsAreRestoredAfterAbandonedHandshakes() throws Exception {
        try (S0Fixture.Listener l = S0Fixture.listen()) {
            S0Fixture.connectLegit(l); // warm up
            byte[] hello = S0Fixture.captureClientHello("x25519");
            for (int i = 0; i < 30; i++) {
                try (Socket s = new Socket("localhost", l.port)) {
                    s.getOutputStream().write(hello);
                    s.getOutputStream().flush();
                    if (i % 2 == 0) {
                        s.setSoTimeout(2000);
                        s.getInputStream().read(new byte[4096]);
                    }
                }
            }
            S0Fixture.sendJunk(l);
            Assert.assertTrue("permits after abandoned handshakes", S0Fixture.await(
                () -> l.handle.cpuPermitsAvailable() == l.handle.cpuPermits()
                      && l.handle.inProgressCount() == 0, 10_000));
        }
    }
}
