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

    /**
     * Server threads are virtual threads that run on the carrier threads of
     * the default scheduler; returns the CPU time that they have used.
     */
    private static long carrierCpuNanos() {
        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        long total = 0L;
        long[] ids = mx.getAllThreadIds();
        ThreadInfo[] infos = mx.getThreadInfo(ids);
        for (int i = 0; i < ids.length; i++) {
            if (infos[i] != null && infos[i].getThreadName().startsWith("ForkJoinPool")
                && infos[i].getThreadName().contains("worker"))
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
    private static final class Flood {
        final AtomicBoolean stop = new AtomicBoolean();
        final AtomicLong attempts = new AtomicLong();
        final AtomicLong flights = new AtomicLong();
        final List<Thread> threads = new ArrayList<>();

        Flood(int port, byte[] hello, int n) {
            for (int i = 0; i < n; i++) {
                threads.add(Thread.ofPlatform().name("flood-" + i).start(() -> {
                    byte[] buf = new byte[4096];
                    while (!stop.get()) {
                        attempts.incrementAndGet();
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
    }

    /** What a flood against a listener costs the listener. */
    private static final class FloodResult {
        double cores;
        long attempts;
        long flights;
        long legitMillis;
    }

    private static FloodResult flood(String permits, int seconds) throws Exception {
        byte[] hello = S0Fixture.captureClientHello("x25519");
        try (S0Fixture.Listener l = S0Fixture.withProperty(PERMITS, permits,
                 () -> S0Fixture.listen()))
        {
            Assert.assertEquals(Integer.parseInt(permits), l.handle.cpuPermits());
            S0Fixture.connectLegit(l); // warm up
            S0Fixture.connectLegit(l);
            Flood warm = new Flood(l.port, hello, 4);
            Thread.sleep(1500);
            warm.stop();
            Assert.assertTrue(S0Fixture.await(
                () -> l.handle.inProgressCount() == 0, 10_000));

            Flood flood = new Flood(l.port, hello, 6);
            long cpu0 = carrierCpuNanos();
            long wall0 = System.nanoTime();
            long attempts0 = flood.attempts.get();
            Thread.sleep(seconds * 1000L / 2);
            FloodResult r = new FloodResult();
            /* A legitimate client connects in the middle of the flood. */
            r.legitMillis = S0Fixture.connectLegit(l);
            Thread.sleep(seconds * 1000L / 2);
            long cpu = carrierCpuNanos() - cpu0;
            long wall = System.nanoTime() - wall0;
            r.cores = (double) cpu / wall;
            r.attempts = flood.attempts.get() - attempts0;
            r.flights = flood.flights.get();
            flood.stop();
            Assert.assertTrue(S0Fixture.await(() ->
                l.handle.inProgressCount() == 0
                && l.handle.cpuPermitsAvailable() == l.handle.cpuPermits(), 15_000));
            return r;
        }
    }

    /**
     * A peer that replays a captured ClientHello over and over, reading the
     * server's first flight and closing, makes the server do the key exchange
     * and a signature each time at no cost to itself.  Each connection lasts a
     * few milliseconds, so the limit on the handshakes at once does not stop
     * it; the permits do.
     */
    @Test(timeout = 120000)
    public void testReplayFloodIsBoundedByTheCpuPermits() throws Exception {
        FloodResult one = flood("1", 6);
        System.out.println("replay flood, 1 permit: server CPU " + one.cores
            + " cores, " + one.attempts + " replays, " + one.flights
            + " flights, legitimate open " + one.legitMillis + " ms");
        Assert.assertTrue("a legitimate client must complete during a flood: "
            + one.legitMillis + " ms", one.legitMillis < 5000);
        Assert.assertTrue("server CPU during a replay flood with one permit: "
            + one.cores + " cores", one.cores <= 1.0 + 0.4);
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
