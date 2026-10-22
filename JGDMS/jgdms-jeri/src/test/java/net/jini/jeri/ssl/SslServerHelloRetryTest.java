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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The SSLSocket path of the server takes TLS 1.3 only, as the engine path
 * does, and a client whose key share is for a group that the server does not
 * enable (so that the server answers with a HelloRetryRequest) is served on
 * both paths with the permit to compute held for the work of the flight that
 * follows its second ClientHello, and for no wait for the peer.
 */
public class SslServerHelloRetryTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        S0Fixture.setUp();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        S0Fixture.tearDown();
    }

    /**
     * Groups of which the first, with the key share that the client sends, is
     * one that the server does not enable, and the second one that it does
     * (it has no share of it, so the server asks for one).
     */
    private static final String[] RETRY = { "secp384r1", "secp256r1" };

    private static final String[] PERMITS = {
        SslServerEndpointImpl.SslListenHandle.SERVER_CPU_PERMITS_PROPERTY, "1",
        S0Fixture.MAX_HANDSHAKES, "64", S0Fixture.HANDSHAKE_TIMEOUT, "20000" };

    private static S0Fixture.Listener listen(boolean socketPath) throws Exception {
        return S0Fixture.withProperties(PERMITS,
            () -> socketPath ? S0Fixture.listenWithSocketFactory() : S0Fixture.listen());
    }

    /**
     * Only TLS 1.3, as on the engine path: a client that offers 1.2 only is
     * refused on either, a client that offers both gets 1.3.
     */
    @Test(timeout = 60000)
    public void testTls12IsRefusedOnBothPaths() throws Exception {
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = listen(socketPath)) {
                S0Fixture.connectLegit(l); // warm up
                int before = l.recorder.count();
                try (SSLSocket s = (SSLSocket) S0Fixture.clientSocketFactory()
                         .createSocket("localhost", l.port))
                {
                    s.setSoTimeout(10_000);
                    SSLParameters p = s.getSSLParameters();
                    p.setProtocols(new String[] { "TLSv1.2" });
                    s.setSSLParameters(p);
                    try {
                        s.startHandshake();
                        s.getOutputStream().write(0);
                        s.getOutputStream().flush();
                        s.getInputStream().read();
                        Assert.fail("TLS 1.2 was served, socketPath=" + socketPath
                                    + ": " + s.getSession().getProtocol());
                    } catch (IOException refused) {
                    }
                }
                Assert.assertFalse("not handed on, socketPath=" + socketPath,
                                   l.recorder.await(before + 1, 1000));
                Assert.assertTrue("counted, socketPath=" + socketPath, S0Fixture.await(
                    () -> l.handle.counters().getFailed() >= 1, 3000));
                try (SSLSocket s = (SSLSocket) S0Fixture.clientSocketFactory()
                         .createSocket("localhost", l.port))
                {
                    s.setSoTimeout(10_000);
                    SSLParameters p = s.getSSLParameters();
                    p.setProtocols(new String[] { "TLSv1.2", "TLSv1.3" });
                    s.setSSLParameters(p);
                    s.startHandshake();
                    Assert.assertEquals("TLSv1.3", s.getSession().getProtocol());
                }
            }
        }
    }

    /** Sends a ClientHello of a client that is made to retry, on a listener, and completes. */
    private static void completeWithRetry(S0Fixture.Listener l) throws Exception {
        int before = l.recorder.count();
        try (S0EngineClient c = new S0EngineClient(l.port, RETRY)) {
            c.sendFlight();
            c.receiveFlight();
            c.sendFlight();
            c.receiveFlight();
            c.complete();
            c.sendApplicationByte();
            Assert.assertTrue("handed on", l.recorder.await(before + 1, 10_000));
        }
    }

    /**
     * A client whose key share the server cannot use is sent a
     * HelloRetryRequest and completes, on both paths, and the server computes
     * each of its two ClientHellos within a permit.
     */
    @Test(timeout = 60000)
    public void testHelloRetryRequestCompletesAndTakesAPermitForEachHello() throws Exception {
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = listen(socketPath)) {
                S0Fixture.connectLegit(l); // warm up
                long entries = socketPath ? l.handle.socketCpuEntries() : l.handle.cpuEntries();
                completeWithRetry(l);
                Assert.assertEquals("a permit for each ClientHello, socketPath=" + socketPath,
                    entries + 2,
                    socketPath ? l.handle.socketCpuEntries() : l.handle.cpuEntries());
                Assert.assertTrue(S0Fixture.await(() -> l.handle.inProgressCount() == 0, 5000));
                Assert.assertEquals(0, l.handle.counters().getFailed());
                Assert.assertEquals("and the permit is back", 1,
                    socketPath ? l.handle.socketCpuPermitsAvailable() : l.handle.cpuPermitsAvailable());
                Assert.assertTrue("a client that offers the group the server enables is served at once",
                    S0Fixture.connectOffering(l, "x25519"));
                Assert.assertTrue("so is one whose key share is for the other group the server refuses",
                    S0Fixture.connectOffering(l, "secp521r1", "x25519"));
            }
        }
    }

    /** Logs each write of the server with the free permits of the SSLSocket path at that moment. */
    private static volatile SslServerEndpointImpl.SslListenHandle handle;
    private static final List<String> EVENTS = Collections.synchronizedList(new ArrayList<>());
    private static final List<Integer> FREE = Collections.synchronizedList(new ArrayList<>());

    private static final class LoggingSocket extends Socket {
        private OutputStream out;

        @Override
        public synchronized OutputStream getOutputStream() throws IOException {
            if (out == null) {
                OutputStream raw = super.getOutputStream();
                out = new FilterOutputStream(raw) {
                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        SslServerEndpointImpl.SslListenHandle h = handle;
                        int free = h == null ? -1 : h.socketCpuPermitsAvailable();
                        EVENTS.add(len + " bytes, " + free + " free");
                        FREE.add(free);
                        raw.write(b, off, len);
                    }
                };
            }
            return out;
        }
    }

    private static ServerSocketFactory loggingFactory() {
        return new ServerSocketFactory() {
            public ServerSocket createServerSocket(int port) throws IOException {
                return createServerSocket(port, 50, null);
            }

            public ServerSocket createServerSocket(int port, int backlog) throws IOException {
                return createServerSocket(port, backlog, null);
            }

            public ServerSocket createServerSocket(int port, int backlog, InetAddress a)
                throws IOException
            {
                return new ServerSocket(port, backlog, a) {
                    @Override
                    public Socket accept() throws IOException {
                        Socket s = new LoggingSocket();
                        implAccept(s);
                        return s;
                    }
                };
            }
        };
    }

    private static S0Fixture.Listener listenLogging() throws Exception {
        EVENTS.clear();
        FREE.clear();
        S0Fixture.Listener l = S0Fixture.withProperties(PERMITS,
            () -> new S0Fixture.Listener(new S0Fixture.Recorder(), loggingFactory(), null));
        handle = l.handle;
        return l;
    }

    /**
     * After a HelloRetryRequest the work is in the second flight, and the
     * SSLSocket path keeps its permit through it: every write of the
     * HelloRetryRequest and of the flight that follows the second ClientHello
     * is made with the only permit taken.  (The window ended at the first read
     * after the first write, and the key exchange and signature of the second
     * flight ran outside every permit.)
     */
    @Test(timeout = 60000)
    public void testTheSocketPathKeepsItsPermitThroughTheFlightAfterARetry() throws Exception {
        try (S0Fixture.Listener l = listenLogging()) {
            S0Fixture.connectLegit(l); // warm up
            EVENTS.clear();
            FREE.clear();
            try (S0EngineClient c = new S0EngineClient(l.port, RETRY)) {
                c.sendFlight();
                c.receiveFlight();
                c.sendFlight();
                c.receiveFlight();
                Assert.assertTrue("the HelloRetryRequest and the flight: " + EVENTS,
                                  FREE.size() >= 2);
                for (int free : FREE) {
                    Assert.assertEquals("a write of the server without the permit: " + EVENTS,
                                        0, free);
                }
                Assert.assertTrue("and it is back when the server waits for the answer",
                    S0Fixture.await(() -> l.handle.socketCpuPermitsAvailable() == 1, 3000));
                c.complete();
                c.sendApplicationByte();
                Assert.assertTrue(l.recorder.await(2, 10_000));
            }
        }
    }

    /**
     * A peer that makes the server retry and then stalls holds no permit
     * while the server waits for its second ClientHello, and one that stalls
     * after the flight holds none after that flight is written: the permit
     * covers the work, not the waiting, and a replayer of either kind costs
     * what the permit allows.
     */
    @Test(timeout = 60000)
    public void testAStallAfterARetryHoldsNoPermit() throws Exception {
        for (boolean socketPath : new boolean[] { false, true }) {
            try (S0Fixture.Listener l = listen(socketPath)) {
                S0Fixture.connectLegit(l); // warm up
                long entries = socketPath ? l.handle.socketCpuEntries() : l.handle.cpuEntries();
                try (S0EngineClient c = new S0EngineClient(l.port, RETRY)) {
                    c.sendFlight();
                    c.receiveFlight();
                    /* The server has written the HelloRetryRequest and waits. */
                    Assert.assertTrue("the permit is back while the server waits for the "
                        + "second ClientHello, socketPath=" + socketPath, S0Fixture.await(() ->
                        (socketPath ? l.handle.socketCpuPermitsAvailable()
                                    : l.handle.cpuPermitsAvailable()) == 1, 3000));
                    Assert.assertEquals(entries + 1,
                        socketPath ? l.handle.socketCpuEntries() : l.handle.cpuEntries());
                    c.sendFlight();
                    c.receiveFlight();
                    /* The server has written its flight and waits again. */
                    Assert.assertTrue("and it is back when the flight is written, socketPath="
                        + socketPath, S0Fixture.await(() ->
                        (socketPath ? l.handle.socketCpuPermitsAvailable()
                                    : l.handle.cpuPermitsAvailable()) == 1, 3000));
                    Assert.assertEquals("the second ClientHello took a permit of its own",
                        entries + 2,
                        socketPath ? l.handle.socketCpuEntries() : l.handle.cpuEntries());
                    Assert.assertEquals("the peer holds its place, not a permit", 1,
                                        l.handle.inProgressCount());
                    long ms = S0Fixture.connectLegit(l);
                    Assert.assertTrue("a legitimate open took " + ms + " ms", ms < 3000);
                }
                Assert.assertTrue(S0Fixture.await(() -> l.handle.inProgressCount() == 0, 5000));
            }
        }
    }
}
