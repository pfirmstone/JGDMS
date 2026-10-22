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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.export.Exporter;
import net.jini.jeri.AtomicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.InvocationDispatcher;
import net.jini.jeri.ServerCapabilities;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The accept path of the TLS server endpoint: the accept loop only accepts,
 * the handshake runs in a task of its own, a failed handshake costs nothing
 * but its own connection, and closing the listener ends what is in progress.
 */
public class SslServerAcceptPathTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        S0Fixture.setUp();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        S0Fixture.tearDown();
    }

    /**
     * Twelve connections that fail their handshake at once must not delay the
     * next legitimate peer.  A failed handshake used to count as an accept
     * failure, and ten of them in five seconds put the accept thread to sleep
     * for ten seconds.
     */
    @Test(timeout = 60000)
    public void testJunkConnectionsDoNotStallTheListener() throws Exception {
        try (S0Fixture.Listener l = S0Fixture.listen()) {
            S0Fixture.connectLegit(l); // warm up
            long t0 = System.nanoTime();
            for (int i = 0; i < 12; i++) {
                S0Fixture.sendJunk(l);
            }
            long ms = S0Fixture.connectLegit(l);
            long total = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            Assert.assertTrue("a legitimate handshake after 12 junk "
                + "connections took " + ms + " ms", ms < 1000);
            Assert.assertTrue("12 junk connections and a legitimate handshake "
                + "took " + total + " ms", total < 3000);
            Assert.assertTrue(S0Fixture.await(
                () -> l.handle.counters().getFailed(HandshakeCounters.Kind.JUNK) >= 12,
                5000));
            Assert.assertTrue("the accept thread must still be running",
                              l.handle.acceptThread().isAlive());
        }
    }

    /** Silent peers hold their own handshake tasks, not the accept loop. */
    @Test(timeout = 60000)
    public void testSilentPeersDoNotDelayALegitimateHandshake() throws Exception {
        try (S0Fixture.Listener l = S0Fixture.listen()) {
            S0Fixture.connectLegit(l); // warm up
            List<Socket> silent = S0Fixture.openSilent(l, 20);
            try {
                Assert.assertTrue(S0Fixture.await(
                    () -> l.handle.inProgressCount() == 20, 5000));
                long ms = S0Fixture.connectLegit(l);
                Assert.assertTrue("a legitimate handshake behind 20 silent "
                    + "peers took " + ms + " ms", ms < 1000);
            } finally {
                S0Fixture.closeAll(silent);
            }
            Assert.assertTrue(S0Fixture.await(
                () -> l.handle.inProgressCount() == 0, 10000));
        }
    }

    /**
     * Closing the listener closes the sockets of the handshakes in progress
     * and wakes their tasks; nothing is left running and no descriptor is
     * leaked.  This is the SSLSocket path, whose read ends when the socket is
     * closed.
     */
    @Test(timeout = 60000)
    public void testListenerCloseEndsHandshakesInProgressOnSocketPath()
        throws Exception
    {
        try (S0Fixture.Listener warm = S0Fixture.listenWithSocketFactory()) {
            S0Fixture.connectLegit(warm);
        }
        int baseline = S0Fixture.settledFds(500);
        S0Fixture.Listener l = S0Fixture.listenWithSocketFactory();
        List<Socket> silent = S0Fixture.openSilent(l, 8);
        try {
            Assert.assertTrue(S0Fixture.await(
                () -> l.handle.inProgressCount() == 8, 5000));
            long t0 = System.nanoTime();
            l.close();
            Assert.assertTrue("handshakes in progress must end at close",
                S0Fixture.await(() -> l.handle.inProgressCount() == 0, 1000));
            for (Socket s : silent) {
                s.setSoTimeout(1000);
                int r;
                try {
                    r = s.getInputStream().read();
                } catch (IOException reset) {
                    r = -1;
                }
                Assert.assertEquals("the server must have closed the peer", -1, r);
            }
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            Assert.assertTrue("took " + ms + " ms", ms < 1500);
        } finally {
            S0Fixture.closeAll(silent);
            l.close();
        }
        Assert.assertEquals(0, l.recorder.count());
        Assert.assertEquals(0, l.handle.connectionCount());
        S0Fixture.assumeCounted(S0Fixture.assertFdsBack(baseline, 5000));
    }

    /**
     * A handshake that completes after the listener was closed is closed and
     * never registered or handed on.
     */
    @Test(timeout = 60000)
    public void testNothingIsHandedOnAfterClose() throws Exception {
        try (S0Fixture.Listener l = S0Fixture.listen()) {
            S0Fixture.connectLegit(l); // warm up
            int handled = l.recorder.count();
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            l.handle.beforeComplete = h -> {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };
            AtomicReference<Integer> read = new AtomicReference<>();
            Thread client = Thread.ofPlatform().start(() -> {
                try (SSLSocket s = (SSLSocket) S0Fixture.clientSocketFactory()
                         .createSocket("localhost", l.port))
                {
                    s.setSoTimeout(10_000);
                    s.startHandshake();
                    try {
                        read.set(s.getInputStream().read());
                    } catch (IOException e) {
                        read.set(-1);
                    }
                } catch (IOException e) {
                    read.set(-2);
                }
            });
            Assert.assertTrue(entered.await(10, TimeUnit.SECONDS));
            l.close();
            release.countDown();
            client.join(10_000);
            Assert.assertTrue(S0Fixture.await(
                () -> l.handle.inProgressCount() == 0, 5000));
            Assert.assertEquals("the connection must not be handed on",
                                handled, l.recorder.count());
            Assert.assertEquals(0, l.handle.connectionCount());
            Assert.assertEquals(1, l.handle.counters().getFailed(
                HandshakeCounters.Kind.LISTENER_CLOSED));
            Assert.assertNotNull("the peer must see the connection closed",
                                 read.get());
            Assert.assertTrue(read.get() < 0);
        }
    }

    /** A socket that is accepted just as the listener closes is closed. */
    @Test(timeout = 60000)
    public void testSocketAcceptedAfterCloseIsClosed() throws Exception {
        S0Fixture.Listener l = S0Fixture.listen();
        java.net.ServerSocket other = new java.net.ServerSocket(0);
        try {
            Socket peer = new Socket("localhost", other.getLocalPort());
            Socket accepted = other.accept();
            l.close();
            /* The accept loop takes the permit before it hands the socket on. */
            l.handle.limits().admission.acquire();
            l.handle.startHandshake(accepted);
            Assert.assertTrue("the socket must be closed", accepted.isClosed());
            Assert.assertEquals(0, l.handle.inProgressCount());
            peer.close();
        } finally {
            other.close();
        }
    }

    /**
     * If the record of a handshake cannot be made (out of memory), the socket
     * is closed and the permit that the accept loop took is given back, and
     * the listener goes on accepting.
     */
    @Test(timeout = 60000)
    public void testOutOfMemoryStartingAHandshakeLeaksNothing() throws Exception {
        try (S0Fixture.Listener l = S0Fixture.listen()) {
            S0Fixture.connectLegit(l); // warm up
            HandshakeLimits limits = l.handle.limits();
            /* Idle: the accept loop holds no permit while it waits. */
            Assert.assertTrue(S0Fixture.await(() ->
                l.handle.admittedConnections() == 0
                && l.handle.inProgressCount() == 0, 5000));
            int idle = limits.admission.availablePermits();
            AtomicInteger calls = new AtomicInteger();
            l.handle.beforeHandshake = () -> {
                if (calls.getAndIncrement() == 0) {
                    throw new OutOfMemoryError("injected");
                }
            };
            try (Socket s = new Socket("localhost", l.port)) {
                Assert.assertTrue("the socket must be closed",
                                  S0Fixture.awaitClosed(s, 5000) >= 0);
            }
            Assert.assertTrue("the permit is given back", S0Fixture.await(() ->
                limits.admission.availablePermits() == idle
                && l.handle.admittedConnections() == 0, 5000));
            Assert.assertEquals(1, l.handle.counters().getFailed(
                HandshakeCounters.Kind.INTERNAL));
            Assert.assertTrue("the listener goes on",
                              l.handle.acceptThread().isAlive());
            S0Fixture.connectLegit(l);
            Assert.assertTrue(S0Fixture.await(() ->
                limits.admission.availablePermits() == idle, 5000));
        }
    }

    /**
     * The handshake task is subject-neutral even when the listener was
     * started under the service subject, and the connection is handed on
     * under the service subject as before.
     */
    @Test(timeout = 60000)
    public void testHandshakeThreadHasNoSubject() throws Exception {
        AtomicReference<Subject> seen = new AtomicReference<>(S0Fixture.server.subject);
        AtomicReference<Boolean> virtual = new AtomicReference<>();
        S0Fixture.ConnectionHook hook = (socket, real) -> {
            seen.set(Subject.current());
            virtual.set(Thread.currentThread().isVirtual());
            return real.call();
        };
        S0Fixture.Listener l = Subject.callAs(S0Fixture.server.subject,
            (Callable<S0Fixture.Listener>) () -> S0Fixture.listen(hook));
        try {
            S0Fixture.connectLegit(l);
            Assert.assertNull("Subject.current() on the handshake thread",
                              seen.get());
            Assert.assertEquals(Boolean.TRUE, virtual.get());
            Assert.assertSame("the connection is handed on under the service "
                + "subject", S0Fixture.server.subject,
                l.recorder.handlerSubject);
        } finally {
            l.close();
        }
    }

    public interface Ping extends Remote {
        String ping() throws RemoteException;
    }

    public static final class PingImpl implements Ping {
        public String ping() { return "pong"; }
    }

    /**
     * A thread for a handshake is subject-neutral whichever thread creates it:
     * a thread captures the subject of its creator.
     */
    @Test(timeout = 60000)
    public void testNewThreadIsSubjectNeutral() throws Exception {
        AtomicReference<Subject> seen = new AtomicReference<>(S0Fixture.server.subject);
        Thread t = Subject.callAs(S0Fixture.server.subject,
            (Callable<Thread>) () -> SslServerEndpointImpl.SslListenHandle
                .newThread("subject test", () -> seen.set(Subject.current())));
        t.start();
        t.join(10_000);
        Assert.assertNull(seen.get());
    }

    /**
     * The mux of an accepted connection captures the service subject, as it
     * did when the handshake ran on the accept thread, so a request is
     * dispatched under the service subject although the handshake thread has
     * none.
     */
    @Test(timeout = 60000)
    public void testDispatchRunsUnderTheServiceSubject() throws Exception {
        AtomicReference<Subject> dispatched = new AtomicReference<>();
        AtomicReference<Boolean> dispatchedSet = new AtomicReference<>(false);
        ServerEndpoint se = SslServerEndpoint.getInstance(
            S0Fixture.server.subject,
            new X500Principal[] { S0Fixture.server.x500 }, "localhost", 0);
        final Exporter exporter = new BasicJeriExporter(
            se,
            new AtomicILFactory(
                new BasicMethodConstraints(new InvocationConstraints(
                    new InvocationConstraint[] {
                        ClientAuthentication.YES, ServerAuthentication.YES,
                        Integrity.YES, Confidentiality.YES },
                    null)),
                null, getClass().getClassLoader())
            {
                @Override
                protected InvocationDispatcher createInvocationDispatcher(
                    java.util.Collection methods, Remote impl,
                    ServerCapabilities caps)
                    throws ExportException
                {
                    InvocationDispatcher real =
                        super.createInvocationDispatcher(methods, impl, caps);
                    return (i, request, context) -> {
                        dispatched.set(Subject.current());
                        dispatchedSet.set(true);
                        real.dispatch(i, request, context);
                    };
                }
            },
            false, true);
        final PingImpl impl = new PingImpl(); // keep a strong ref (DGC reaper)
        final Ping proxy = (Ping) Subject.callAs(S0Fixture.server.subject,
            (Callable<Ping>) () -> (Ping) exporter.export(impl));
        try {
            Subject.callAs(S0Fixture.client.subject, (Callable<Void>) () -> {
                Assert.assertEquals("pong", proxy.ping());
                return null;
            });
            Assert.assertTrue(dispatchedSet.get());
            Assert.assertSame("the subject during dispatch",
                              S0Fixture.server.subject, dispatched.get());
        } finally {
            exporter.unexport(true);
        }
        java.util.Objects.requireNonNull(impl);
    }

    /**
     * The server names a peer by its address until the peer has been
     * authenticated.  A reverse lookup of the peer's address belongs to the
     * peer, who controls the reverse zone, and can take as long as the
     * resolver's timeout on the thread that asks.
     */
    @Test(timeout = 60000)
    public void testNoReverseLookupOfAPeerBeforeAuthentication() throws Exception {
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = socketPath
                     ? S0Fixture.listenWithSocketFactory() : S0Fixture.listen())
            {
                S0Fixture.connectLegit(l); // warm up
                int before = CountingResolverProvider.REVERSE_LOOKUPS.get();
                S0Fixture.connectLegit(l);
                S0Fixture.sendJunk(l);
                S0Fixture.connectStranger(l);
                Assert.assertEquals("reverse lookups, socketPath=" + socketPath,
                    before, CountingResolverProvider.REVERSE_LOOKUPS.get());
            }
        }
    }

    /**
     * Only a failure of accept counts towards the throttle that puts the
     * accept thread to sleep, and closing the listener ends that sleep.
     */
    @Test(timeout = 60000)
    public void testThrottleSleepEndsWhenTheListenerCloses() throws Exception {
        AtomicInteger accepts = new AtomicInteger();
        ServerSocketFactory failing = new ServerSocketFactory() {
            public ServerSocket createServerSocket(int port) throws IOException {
                return createServerSocket(port, 50);
            }
            public ServerSocket createServerSocket(int port, int backlog)
                throws IOException
            {
                return new ServerSocket(port, backlog) {
                    @Override
                    public Socket accept() throws IOException {
                        accepts.incrementAndGet();
                        throw new IOException("injected accept failure");
                    }
                };
            }
            public ServerSocket createServerSocket(int port, int backlog,
                                                   InetAddress addr)
                throws IOException
            {
                return createServerSocket(port, backlog);
            }
        };
        S0Fixture.Listener l = new S0Fixture.Listener(
            new S0Fixture.Recorder(), failing, null);
        try {
            Assert.assertTrue("ten failures in a burst put the thread to sleep",
                S0Fixture.await(() -> accepts.get() >= 11, 5000));
            Thread.sleep(200);
            Assert.assertEquals("the accept thread sleeps", 11, accepts.get());
            Thread t = l.handle.acceptThread();
            long t0 = System.nanoTime();
            l.close();
            t.join(2000);
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            Assert.assertFalse("the accept thread must end at close", t.isAlive());
            Assert.assertTrue("took " + ms + " ms", ms < 1500);
        } finally {
            l.close();
        }
    }

    /**
     * After every kind of failure the descriptors are back at the baseline,
     * the permit accounting is intact, the listener is still open and a
     * legitimate peer is served.
     */
    @Test(timeout = 120000)
    public void testEveryFailureKindReleasesEverything() throws Exception {
        boolean counted = true;
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
            S0Fixture.Listener l = socketPath
                ? new S0Fixture.Listener(new S0Fixture.Recorder(),
                                         ServerSocketFactory.getDefault(), hook)
                : S0Fixture.listen(hook);
            try {
                S0Fixture.connectLegit(l); // warm up
                int baseline = S0Fixture.settledFds(300);
                int served = l.recorder.count();

                S0Fixture.sendJunk(l);
                counted &= assertSettled(l, baseline, "junk, socketPath=" + socketPath);

                List<Socket> silent = S0Fixture.openSilent(l, 3);
                Assert.assertTrue(S0Fixture.await(
                    () -> l.handle.inProgressCount() == 3, 5000));
                S0Fixture.closeAll(silent);
                counted &= assertSettled(l, baseline, "silent, socketPath=" + socketPath);

                S0Fixture.connectStranger(l);
                counted &= assertSettled(l, baseline, "untrusted, socketPath=" + socketPath);

                inject.set(new IllegalStateException("injected"));
                S0Fixture.sendHello(l);
                counted &= assertSettled(l, baseline, "exception, socketPath=" + socketPath);

                inject.set(new AssertionError("injected"));
                S0Fixture.sendHello(l);
                counted &= assertSettled(l, baseline, "error, socketPath=" + socketPath);
                inject.set(null);

                Assert.assertTrue("an Error in a handshake must not close the "
                    + "listener", l.handle.acceptThread().isAlive());
                Assert.assertFalse(l.handle.serverSocket.isClosed());
                Assert.assertTrue(
                    l.handle.counters().getFailed(HandshakeCounters.Kind.INTERNAL) >= 2);

                /* An exception or Error after the handshake leaves nothing registered. */
                int registered = l.handle.connectionCount();
                l.recorder.failWith = new IllegalStateException("injected");
                handOnAndExpect(l, registered);
                l.recorder.failWith = null;
                l.recorder.failWithError = new AssertionError("injected");
                handOnAndExpect(l, registered);
                l.recorder.failWithError = null;

                /* The listener serves a legitimate peer after all of that. */
                long ms = S0Fixture.connectLegit(l);
                Assert.assertTrue("took " + ms + " ms", ms < 1000);
                Assert.assertFalse(l.handle.serverSocket.isClosed());
            } finally {
                l.close();
            }
        }
        S0Fixture.assumeCounted(counted);
    }

    private static void handOnAndExpect(S0Fixture.Listener l, int registered)
        throws Exception
    {
        int before = l.recorder.count();
        try (SSLSocket s = (SSLSocket) S0Fixture.clientSocketFactory()
                 .createSocket("localhost", l.port))
        {
            s.setSoTimeout(10_000);
            s.startHandshake();
            s.getOutputStream().write(0);
            Assert.assertTrue(l.recorder.await(before + 1, 10_000));
        }
        Assert.assertTrue("a connection whose hand-on failed must be closed "
            + "and removed from the listener", S0Fixture.await(
                () -> l.handle.connectionCount() == registered, 5000));
    }

    /** @return whether the descriptors were counted */
    private static boolean assertSettled(S0Fixture.Listener l, int baseline,
                                         String what) throws Exception
    {
        Assert.assertTrue("handshakes in progress after " + what,
            S0Fixture.await(() -> l.handle.inProgressCount() == 0, 5000));
        return S0Fixture.assertFdsBack(baseline, 5000);
    }
}
