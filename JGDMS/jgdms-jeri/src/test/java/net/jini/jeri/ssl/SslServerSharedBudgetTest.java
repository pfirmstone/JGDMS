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

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The permits that bound the handshakes of the server listeners belong to the
 * process: several listeners in one JVM admit no more sockets, and compute on
 * no more processors, than one.
 */
public class SslServerSharedBudgetTest {

    private static final String PERMITS = HandshakeLimits.CPU_PERMITS_PROPERTY;
    private static final String COMPUTE_THREADS = HandshakeCompute.THREADS_PROPERTY;

    @BeforeClass
    public static void setUpClass() throws Exception {
        S0Fixture.setUp();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        S0Fixture.tearDown();
    }

    /** Opens listeners with the given settings. */
    private static List<S0Fixture.Listener> listeners(int n, String... properties)
        throws Exception
    {
        List<S0Fixture.Listener> l = new ArrayList<>();
        try {
            for (int i = 0; i < n; i++) {
                l.add(S0Fixture.withProperties(properties,
                                               () -> S0Fixture.listen()));
            }
        } catch (Throwable t) {
            closeAll(l);
            throw t;
        }
        return l;
    }

    private static void closeAll(List<S0Fixture.Listener> listeners) {
        for (S0Fixture.Listener l : listeners) {
            l.close();
        }
    }

    private static int admitted(List<S0Fixture.Listener> listeners) {
        int n = 0;
        for (S0Fixture.Listener l : listeners) {
            n += l.handle.admittedConnections();
        }
        return n;
    }

    /** The listeners of the JVM share the permits. */
    @Test(timeout = 60000)
    public void testListenersShareOneSetOfPermits() throws Exception {
        List<S0Fixture.Listener> ls = listeners(3,
            S0Fixture.MAX_HANDSHAKES, "5", S0Fixture.MAX_QUEUED, "6");
        try {
            HandshakeLimits first = ls.get(0).handle.limits();
            for (S0Fixture.Listener l : ls) {
                Assert.assertSame(first, l.handle.limits());
            }
            /* One for the accept loop of each. */
            Assert.assertTrue(S0Fixture.await(
                () -> first.admission.availablePermits() == 11 - 3, 5000));
        } finally {
            closeAll(ls);
        }
    }

    /**
     * A replay flood against three listeners at once costs the server what
     * one permit allows, not one permit for each listener.
     */
    @Test(timeout = 120000)
    public void testReplayFloodAcrossListenersIsBoundedByOneSetOfPermits()
        throws Exception
    {
        byte[] hello = S0Fixture.captureClientHello("x25519");
        List<S0Fixture.Listener> ls = listeners(3, PERMITS, "1",
                                                COMPUTE_THREADS, "0");
        try {
            for (S0Fixture.Listener l : ls) {
                S0Fixture.connectLegit(l); // warm up
                S0Fixture.connectLegit(l);
            }
            List<SslServerHandshakeCpuTest.Flood> floods = new ArrayList<>();
            for (S0Fixture.Listener l : ls) {
                floods.add(new SslServerHandshakeCpuTest.Flood(l.port, hello, 2));
            }
            Thread.sleep(1500); // warm the flood
            long cpu0 = SslServerHandshakeCpuTest.carrierCpuNanos();
            long wall0 = System.nanoTime();
            Thread.sleep(4000);
            double cores = (double) (SslServerHandshakeCpuTest.carrierCpuNanos() - cpu0)
                / (System.nanoTime() - wall0);
            long flights = 0L;
            for (SslServerHandshakeCpuTest.Flood f : floods) {
                f.stop();
                flights += f.flights.get();
            }
            System.out.println("replay flood, 3 listeners, 1 permit: server CPU "
                + cores + " cores, " + flights + " flights");
            Assert.assertTrue("the flood got flights: " + flights, flights > 100);
            Assert.assertTrue("server CPU during a replay flood against three "
                + "listeners with one permit: " + cores + " cores",
                cores <= 1.0 + 0.4);
            for (S0Fixture.Listener l : ls) {
                Assert.assertTrue(S0Fixture.await(() ->
                    l.handle.cpuPermitsAvailable() == l.handle.cpuPermits(),
                    15_000));
            }
        } finally {
            closeAll(ls);
        }
    }

    /**
     * Silent peers on three listeners fill one budget: the sockets that the
     * process admits stay within it however many listeners there are.
     */
    @Test(timeout = 60000)
    public void testAdmittedSocketsAreBoundedForTheProcess() throws Exception {
        final int capacity = 7;
        List<S0Fixture.Listener> ls = listeners(3, S0Fixture.MAX_HANDSHAKES, "3",
            S0Fixture.MAX_QUEUED, "4", S0Fixture.HANDSHAKE_TIMEOUT, "60000");
        List<Socket> silent = new ArrayList<>();
        try {
            for (S0Fixture.Listener l : ls) {
                silent.addAll(S0Fixture.openSilent(l, 8));
            }
            /* Each listener's accept loop holds a permit while it waits. */
            Assert.assertTrue("the budget fills: " + admitted(ls), S0Fixture.await(
                () -> admitted(ls) >= capacity - ls.size(), 10_000));
            Thread.sleep(500);
            Assert.assertTrue("admitted " + admitted(ls) + " of " + capacity,
                              admitted(ls) <= capacity);
            Assert.assertTrue("working " + ls.get(0).handle.runningHandshakes(),
                              ls.get(0).handle.runningHandshakes() <= 3);
            Assert.assertEquals(0, ls.get(0).handle.limits().admission.availablePermits());
        } finally {
            S0Fixture.closeAll(silent);
            closeAll(ls);
        }
        HandshakeLimits limits = ls.get(0).handle.limits();
        Assert.assertTrue("all the permits are back: "
            + limits.admission.availablePermits(), S0Fixture.await(
            () -> limits.admission.availablePermits() == capacity, 5000));
    }

    /** The descriptors that the process spends on waiting sockets are bounded too. */
    @Test(timeout = 60000)
    public void testDescriptorsAreBoundedForTheProcess() throws Exception {
        final int capacity = 7;
        int fdBase = S0Fixture.openFds();
        Assume.assumeTrue("cannot count descriptors here", fdBase >= 0);
        List<S0Fixture.Listener> ls = listeners(3, S0Fixture.MAX_HANDSHAKES, "3",
            S0Fixture.MAX_QUEUED, "4", S0Fixture.HANDSHAKE_TIMEOUT, "60000");
        List<Socket> silent = new ArrayList<>();
        try {
            for (S0Fixture.Listener l : ls) {
                silent.addAll(S0Fixture.openSilent(l, 8));
            }
            Assert.assertTrue(S0Fixture.await(
                () -> admitted(ls) >= capacity - ls.size(), 10_000));
            Thread.sleep(500);
            /* The 24 client sockets are in this process too. */
            int fds = S0Fixture.openFds() - fdBase - silent.size();
            Assert.assertTrue("descriptors of the server side: " + fds
                + " for a budget of " + capacity, fds <= capacity + 4);
        } finally {
            S0Fixture.closeAll(silent);
            closeAll(ls);
        }
    }

    /**
     * When the budget is full a listener pauses its accept, and when it frees
     * the listener is served: the peers that wait in the backlog of one
     * listener are not shut out for ever by those of another.
     */
    @Test(timeout = 60000)
    public void testAListenerPausesForTheBudgetAndIsServedWhenItFrees()
        throws Exception
    {
        List<S0Fixture.Listener> ls = listeners(2, S0Fixture.MAX_HANDSHAKES, "2",
            S0Fixture.MAX_QUEUED, "1", S0Fixture.HANDSHAKE_TIMEOUT, "3000");
        S0Fixture.Listener a = ls.get(0);
        S0Fixture.Listener b = ls.get(1);
        List<Socket> silent = new ArrayList<>();
        try {
            /* Three permits: A's accept loop holds one, B's two silent peers the rest. */
            silent.addAll(S0Fixture.openSilent(b, 2));
            Assert.assertTrue(S0Fixture.await(() ->
                b.handle.admittedConnections() == 2
                && a.handle.limits().admission.availablePermits() == 0, 5000));
            long millis = S0Fixture.connectLegit(b);
            Assert.assertTrue("B paused until its silent peers timed out: "
                + millis + " ms", millis >= 2000);
            Assert.assertTrue("and was served then: " + millis + " ms",
                              millis < 15_000);
            Assert.assertTrue(S0Fixture.await(
                () -> b.handle.inProgressCount() == 0, 10_000));
        } finally {
            S0Fixture.closeAll(silent);
            closeAll(ls);
        }
    }

    /** Closing a listener gives back its permits and nobody else's. */
    @Test(timeout = 60000)
    public void testClosingOneListenerReleasesOnlyItsOwnPermits() throws Exception {
        final int capacity = 7;
        List<S0Fixture.Listener> ls = listeners(2, S0Fixture.MAX_HANDSHAKES, "3",
            S0Fixture.MAX_QUEUED, "4", S0Fixture.HANDSHAKE_TIMEOUT, "60000");
        S0Fixture.Listener a = ls.get(0);
        S0Fixture.Listener b = ls.get(1);
        HandshakeLimits limits = a.handle.limits();
        List<Socket> silent = new ArrayList<>();
        try {
            silent.addAll(S0Fixture.openSilent(a, 2));
            silent.addAll(S0Fixture.openSilent(b, 2));
            Assert.assertTrue(S0Fixture.await(() ->
                a.handle.admittedConnections() == 2
                && b.handle.admittedConnections() == 2
                && limits.admission.availablePermits() == capacity - 4 - 2, 5000));

            a.close();
            a.close();
            Assert.assertTrue("A's permits come back: "
                + limits.admission.availablePermits(), S0Fixture.await(() ->
                a.handle.admittedConnections() == 0
                && limits.admission.availablePermits() == capacity - 2 - 1, 5000));
            Assert.assertEquals("B keeps its connections", 2,
                                b.handle.admittedConnections());
            Assert.assertEquals("B's working permits are not released",
                                3 - limits.running.availablePermits(),
                                b.handle.runningHandshakes());

            b.close();
            Assert.assertTrue("everything is back, nothing twice: "
                + limits.admission.availablePermits(), S0Fixture.await(() ->
                limits.admission.availablePermits() == capacity, 5000));
            Assert.assertEquals(3, limits.running.availablePermits());
        } finally {
            S0Fixture.closeAll(silent);
            closeAll(ls);
        }
    }
}
