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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.connection.ServerConnection;
import org.apache.river.jeri.internal.connection.ServerConnManager;

/**
 * Shared set-up for the tests of the TLS server accept path: SPIFFE-style
 * fixtures, a listener on an ephemeral port whose connections are recorded
 * instead of served, raw and TLS clients, and a count of the open
 * descriptors of this process.
 *
 * <p>The listener is a {@code SslListenEndpoint} used directly, so a test can
 * see the {@code SslListenHandle} and replace the construction of the server
 * connection.
 */
final class S0Fixture {

    private S0Fixture() { }

    static Path dir;
    static SslEngineSvidFactory.Svid server;
    static SslEngineSvidFactory.Svid client;
    static SslEngineSvidFactory.Svid stranger;
    private static String[] savedTrust;
    private static SSLContext clientContext;
    private static SSLContext strangerContext;

    /**
     * Generates the fixtures once.  The trust manager caches the truststore
     * for the whole JVM, so it is reset here as the other ssl test classes do.
     */
    static void setUp() throws Exception {
        dir = Files.createTempDirectory("ssl-s0");
        server = SslEngineSvidFactory.generate(
            dir, "server", "CN=Reggie", "spiffe://example.org/reggie");
        client = SslEngineSvidFactory.generate(
            dir, "client", "CN=Tester", "spiffe://example.org/tester");
        stranger = SslEngineSvidFactory.generate(
            dir, "stranger", "CN=Stranger", "spiffe://evil.example/stranger");
        savedTrust = SslEngineSvidFactory.installTrustStore(
            dir, server.leaf, client.leaf);
        resetTrustManagerCache();
        clientContext = newClientContext("client");
        strangerContext = newClientContext("stranger");
    }

    static void tearDown() throws Exception {
        SslEngineSvidFactory.restoreTrustStore(savedTrust);
        resetTrustManagerCache();
        if (dir != null) {
            Files.walk(dir)
                 .sorted(java.util.Comparator.reverseOrder())
                 .forEach(p -> { try { Files.deleteIfExists(p); }
                                 catch (IOException ignore) { } });
        }
    }

    private static void resetTrustManagerCache() throws Exception {
        java.lang.reflect.Field tm =
            FilterX509TrustManager.class.getDeclaredField("tm");
        tm.setAccessible(true);
        tm.set(null, null);
    }

    /** A client context that has the named credential and trusts any server. */
    private static SSLContext newClientContext(String alias) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(dir.resolve(alias + ".p12"))) {
            ks.load(in, "changeit".toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, "changeit".toCharArray());
        TrustManager[] trustAll = { new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] c, String t) { }
            public void checkServerTrusted(X509Certificate[] c, String t) { }
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        } };
        SSLContext ctx = SSLContext.getInstance("TLSv1.3");
        ctx.init(kmf.getKeyManagers(), trustAll, null);
        return ctx;
    }

    /** Records the connections that a listener hands on. */
    static final class Recorder implements ServerConnManager {
        final List<ServerConnection> handled = new ArrayList<>();
        /** The subject on the thread that handed the connection on. */
        volatile javax.security.auth.Subject handlerSubject;
        volatile RuntimeException failWith;
        volatile Error failWithError;
        private int count;

        public synchronized void handleConnection(
            ServerConnection conn, RequestDispatcher dispatcher)
        {
            handlerSubject = javax.security.auth.Subject.current();
            handled.add(conn);
            count++;
            notifyAll();
            if (failWith != null) {
                throw failWith;
            }
            if (failWithError != null) {
                throw failWithError;
            }
        }

        synchronized int count() {
            return count;
        }

        /** Waits until at least n connections were handed on. */
        synchronized boolean await(int n, long millis)
            throws InterruptedException
        {
            long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
            while (count < n) {
                long left = end - System.nanoTime();
                if (left <= 0L) {
                    return false;
                }
                TimeUnit.NANOSECONDS.timedWait(this, left);
            }
            return true;
        }
    }

    /** Replaces the construction of the server connection in a test. */
    interface ConnectionHook {
        SslServerEndpointImpl.SslServerConnection serverConnection(
            Socket socket,
            Callable<SslServerEndpointImpl.SslServerConnection> real)
            throws Exception;
    }

    /** A listener under test. */
    static final class Listener implements AutoCloseable {
        final SslServerEndpointImpl.SslListenEndpoint endpoint;
        final SslServerEndpointImpl.SslListenHandle handle;
        final Recorder recorder;
        final int port;

        Listener(Recorder recorder, ServerSocketFactory ssf,
                 ConnectionHook hook) throws IOException
        {
            this(recorder, ssf, hook, new RequestDispatcher() {
                public void dispatch(InboundRequest request) { }
            });
        }

        /** A recorder of null leaves the default connection manager. */
        Listener(Recorder recorder, ServerSocketFactory ssf,
                 ConnectionHook hook, RequestDispatcher requestDispatcher)
            throws IOException
        {
            this(recorder, ssf, hook, requestDispatcher, server);
        }

        Listener(Recorder recorder, ServerSocketFactory ssf,
                 ConnectionHook hook, RequestDispatcher requestDispatcher,
                 SslEngineSvidFactory.Svid svid)
            throws IOException
        {
            this.recorder = recorder;
            this.endpoint = new SslServerEndpointImpl.SslListenEndpoint(
                svid.subject, new X500Principal[] { svid.x500 },
                "localhost", 0, null, ssf)
            {
                @Override
                SslServerEndpointImpl.SslListenHandle createListenHandle(
                    RequestDispatcher dispatcher, ServerSocket serverSocket)
                    throws IOException
                {
                    return new SslServerEndpointImpl.SslListenHandle(
                        dispatcher, serverSocket, this)
                    {
                        @Override
                        SslServerEndpointImpl.SslServerConnection
                        serverConnection(Socket socket, long deadline)
                            throws IOException
                        {
                            if (hook == null) {
                                return super.serverConnection(socket, deadline);
                            }
                            try {
                                return hook.serverConnection(
                                    socket,
                                    () -> super.serverConnection(socket, deadline));
                            } catch (IOException | RuntimeException | Error e) {
                                throw e;
                            } catch (Exception e) {
                                throw new IOException(e);
                            }
                        }
                    };
                }
            };
            if (recorder != null) {
                endpoint.setServerConnectionManager(recorder);
            }
            this.handle = (SslServerEndpointImpl.SslListenHandle)
                endpoint.listen(requestDispatcher);
            this.port = handle.serverSocket.getLocalPort();
        }

        public void close() {
            handle.close();
        }
    }

    static Listener listen() throws IOException {
        return new Listener(new Recorder(), null, null);
    }

    /** A listener on the SSLSocket path, as for HTTPS or a socket factory. */
    static Listener listenWithSocketFactory() throws IOException {
        return new Listener(new Recorder(), ServerSocketFactory.getDefault(), null);
    }

    /** A listener that authenticates as the given server identity. */
    static Listener listen(SslEngineSvidFactory.Svid svid) throws IOException {
        return new Listener(new Recorder(), null, null, new RequestDispatcher() {
            public void dispatch(InboundRequest request) { }
        }, svid);
    }

    static Listener listen(ConnectionHook hook) throws IOException {
        return new Listener(new Recorder(), null, hook);
    }

    static javax.net.ssl.SSLSocketFactory clientSocketFactory() {
        return clientContext.getSocketFactory();
    }

    /**
     * Makes a TLS connection as the trusted client, and returns the
     * milliseconds until the listener handed the connection on.
     */
    static long connectLegit(Listener l) throws Exception {
        int before = l.recorder.count();
        long t0 = System.nanoTime();
        try (SSLSocket s = (SSLSocket) clientContext.getSocketFactory()
                 .createSocket("localhost", l.port))
        {
            s.setSoTimeout(30_000);
            s.startHandshake();
            s.getOutputStream().write(0);
            s.getOutputStream().flush();
            if (!l.recorder.await(before + 1, 30_000)) {
                throw new AssertionError("the listener did not hand on the connection");
            }
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        }
    }

    /** Makes a TLS connection as the client that the server does not trust. */
    static void connectStranger(Listener l) throws Exception {
        try (SSLSocket s = (SSLSocket) strangerContext.getSocketFactory()
                 .createSocket("localhost", l.port))
        {
            s.setSoTimeout(10_000);
            try {
                s.startHandshake();
                s.getInputStream().read();
            } catch (IOException expected) {
            }
        }
    }

    /** Sends bytes that are not TLS and waits until the server closes. */
    static void sendJunk(Listener l) throws IOException {
        try (Socket s = new Socket("localhost", l.port)) {
            s.getOutputStream().write("GARBAGE!\r\n".getBytes());
            s.getOutputStream().flush();
            s.setSoTimeout(5_000);
            try {
                s.getInputStream().read();
            } catch (IOException expected) {
            }
        }
    }

    /** Opens connections that send nothing; the caller closes them. */
    static List<Socket> openSilent(Listener l, int n) throws IOException {
        List<Socket> sockets = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Socket s = new Socket();
            s.connect(new InetSocketAddress("localhost", l.port), 5_000);
            sockets.add(s);
        }
        return sockets;
    }

    static void closeAll(List<Socket> sockets) {
        for (Socket s : sockets) {
            try { s.close(); } catch (IOException ignore) { }
        }
    }

    /**
     * Returns the bytes of a ClientHello from a client that holds the trusted
     * client credential.  Any server answers it with its first flight, so
     * replaying it needs no cryptography.
     */
    static byte[] captureClientHello() throws Exception {
        javax.net.ssl.SSLEngine engine = clientContext.createSSLEngine("localhost", 0);
        engine.setUseClientMode(true);
        java.nio.ByteBuffer out = java.nio.ByteBuffer.allocate(
            engine.getSession().getPacketBufferSize());
        engine.beginHandshake();
        engine.wrap(java.nio.ByteBuffer.allocate(0), out);
        out.flip();
        byte[] hello = new byte[out.remaining()];
        out.get(hello);
        return hello;
    }

    /** Returns the process CPU time in nanoseconds. */
    static long processCpuNanos() {
        return ((com.sun.management.OperatingSystemMXBean)
            java.lang.management.ManagementFactory.getOperatingSystemMXBean())
            .getProcessCpuTime();
    }

    /**
     * Waits until the peer closes the connection, by end of file or reset, and
     * returns the milliseconds that took, or -1 if it did not within the
     * limit.
     */
    static long awaitClosed(Socket s, long limitMillis) throws IOException {
        long t0 = System.nanoTime();
        s.setSoTimeout((int) limitMillis);
        byte[] buf = new byte[4096];
        try {
            while (s.getInputStream().read(buf) >= 0) {
                if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0) > limitMillis) {
                    return -1L;
                }
            }
        } catch (java.net.SocketTimeoutException e) {
            return -1L;
        } catch (IOException reset) {
            /* the server closed with unread data: counts as closed */
        }
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
    }

    /** Runs the action with a system property set, then restores it. */
    static <T> T withProperty(String name, String value, Callable<T> action)
        throws Exception
    {
        return withProperties(new String[] { name, value }, action);
    }

    /** Runs the action with system properties set, then restores them. */
    static <T> T withProperties(String[] namesAndValues, Callable<T> action)
        throws Exception
    {
        int n = namesAndValues.length / 2;
        String[] old = new String[n];
        for (int i = 0; i < n; i++) {
            old[i] = System.getProperty(namesAndValues[2 * i]);
            System.setProperty(namesAndValues[2 * i], namesAndValues[2 * i + 1]);
        }
        try {
            return action.call();
        } finally {
            for (int i = 0; i < n; i++) {
                if (old[i] == null) {
                    System.clearProperty(namesAndValues[2 * i]);
                } else {
                    System.setProperty(namesAndValues[2 * i], old[i]);
                }
            }
        }
    }

    static final String MAX_HANDSHAKES = HandshakeLimits.MAX_HANDSHAKES_PROPERTY;
    static final String MAX_QUEUED = HandshakeLimits.MAX_QUEUED_PROPERTY;
    static final String HANDSHAKE_TIMEOUT =
        "org.apache.river.jeri.ssl.serverHandshakeTimeout";

    /** A listener with the given limit, queue and handshake timeout. */
    static Listener listen(int handshakes, int queued, long timeoutMillis,
                           ConnectionHook hook, boolean socketPath)
        throws Exception
    {
        return withProperties(new String[] {
                MAX_HANDSHAKES, Integer.toString(handshakes),
                MAX_QUEUED, Integer.toString(queued),
                HANDSHAKE_TIMEOUT, Long.toString(timeoutMillis) },
            () -> new Listener(new Recorder(),
                socketPath ? ServerSocketFactory.getDefault() : null, hook));
    }

    /** Returns the number of descriptors this process has open. */
    static int openFds() {
        String[] fds = new File("/proc/self/fd").list();
        return fds == null ? -1 : fds.length;
    }

    /** Waits for the count of open descriptors to fall to the baseline. */
    static int awaitFds(int baseline, long millis) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        int now = openFds();
        while (now > baseline && System.nanoTime() < end) {
            Thread.sleep(20);
            now = openFds();
        }
        return now;
    }

    /** Waits for a condition, polling every 10 ms. */
    static boolean await(Callable<Boolean> condition, long millis)
        throws Exception
    {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (!condition.call()) {
            if (System.nanoTime() >= end) {
                return false;
            }
            Thread.sleep(10);
        }
        return true;
    }
}
