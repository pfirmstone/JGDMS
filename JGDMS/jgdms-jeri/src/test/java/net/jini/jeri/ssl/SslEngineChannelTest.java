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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.TrustManagerFactory;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link SslEngineChannel} -- the SSLEngine wrap/unwrap loop that
 * is the P1 keystone.  Exercises the driver directly over a loopback
 * {@link SocketChannel} pair, independent of the JERI stack, asserting:
 *
 * <ul>
 *   <li>a real TLS 1.3 handshake completes through the NEED_WRAP / NEED_UNWRAP /
 *       NEED_TASK / FINISHED state machine;</li>
 *   <li>application plaintext round-trips both directions through the
 *       wrap/unwrap streams, including a payload larger than one TLS record;</li>
 *   <li>the getDelegatedTask contract: NEED_TASK tasks are run <em>inline on the
 *       driving (virtual) thread</em>, never offloaded -- the explicit,
 *       virtual-thread-first decision the future QUIC engine loop shares.</li>
 * </ul>
 */
public class SslEngineChannelTest {

    /** Builds a throwaway self-signed RSA SSLContext for the loopback pair. */
    private static SSLContext newContext(Path dir) throws Exception {
        Path ks = dir.resolve("loop.p12");
        String keytool = keytool();
        Process p = new ProcessBuilder(
            keytool, "-genkeypair", "-alias", "loop",
            "-keyalg", "RSA", "-keysize", "2048",
            "-sigalg", "SHA256withRSA",
            "-dname", "CN=loopback", "-validity", "1",
            "-storetype", "PKCS12",
            "-keystore", ks.toString(),
            "-storepass", "changeit", "-keypass", "changeit"
        ).redirectErrorStream(true).start();
        drain(p);
        if (p.waitFor() != 0) {
            throw new IllegalStateException("keytool failed");
        }
        char[] pw = "changeit".toCharArray();
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(ks)) {
            store.load(in, pw);
        }
        KeyManagerFactory kmf =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(store, pw);
        TrustManagerFactory tmf =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(store);
        SSLContext ctx = SSLContext.getInstance("TLSv1.3");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ctx;
    }

    @Test
    public void testHandshakeAndRoundTrip() throws Exception {
        Path dir = Files.createTempDirectory("ssl-engine-unit");
        try {
            SSLContext ctx = newContext(dir);
            try (ServerSocketChannel ssc = ServerSocketChannel.open()) {
                ssc.socket().bind(new InetSocketAddress("localhost", 0));
                int port = ssc.socket().getLocalPort();

                final AtomicReference<Throwable> serverError = new AtomicReference<>();
                final AtomicReference<String> serverThreadInfo = new AtomicReference<>();
                final byte[] big = new byte[100_000];
                Arrays.fill(big, (byte) 'x');

                Thread serverThread = Thread.ofVirtual().start(() -> {
                    try (SocketChannel ch = ssc.accept()) {
                        SSLEngine engine = ctx.createSSLEngine();
                        engine.setUseClientMode(false);
                        SslEngineChannel sec =
                            new SslEngineChannel(engine, ch, "server");
                        sec.handshake();
                        serverThreadInfo.set(Thread.currentThread().toString());
                        InputStream in = sec.getInputStream();
                        OutputStream out = sec.getOutputStream();
                        // Echo the client's greeting.
                        byte[] buf = new byte[64];
                        int n = in.read(buf);
                        out.write(("re:" + new String(buf, 0, n,
                            StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
                        out.flush();
                        // Send a payload larger than one TLS record.
                        out.write(big);
                        out.flush();
                        sec.close();
                    } catch (Throwable t) {
                        serverError.set(t);
                    }
                });

                SocketChannel ch = SocketChannel.open(
                    new InetSocketAddress("localhost", port));
                SSLEngine engine = ctx.createSSLEngine("localhost", port);
                engine.setUseClientMode(true);
                SslEngineChannel client =
                    new SslEngineChannel(engine, ch, "client");
                client.handshake();

                Assert.assertNotNull("handshake produced a session",
                    client.getSession());
                Assert.assertNotEquals("cipher suite negotiated",
                    "SSL_NULL_WITH_NULL_NULL",
                    client.getSession().getCipherSuite());

                InputStream in = client.getInputStream();
                OutputStream out = client.getOutputStream();
                out.write("hi".getBytes(StandardCharsets.UTF_8));
                out.flush();

                byte[] buf = new byte[64];
                int n = in.read(buf);
                Assert.assertEquals("re:hi",
                    new String(buf, 0, n, StandardCharsets.UTF_8));

                // Read the large payload fully across TLS records.
                byte[] got = new byte[big.length];
                int off = 0;
                while (off < got.length) {
                    int r = in.read(got, off, got.length - off);
                    if (r < 0) break;
                    off += r;
                }
                Assert.assertEquals("large payload fully received",
                    big.length, off);
                Assert.assertArrayEquals("large payload intact", big, got);

                client.close();
                serverThread.join(10_000);
                if (serverError.get() != null) {
                    throw new AssertionError("server side failed",
                        serverError.get());
                }
            }
        } finally {
            deleteDir(dir);
        }
    }

    /**
     * Pins the getDelegatedTask-on-virtual-thread decision.  A real TLS 1.3
     * handshake produces NEED_TASK (the key-agreement / signature tasks); this
     * test drives the handshake through {@link SslEngineChannel} on a virtual
     * thread and asserts (a) the handshake completes and (b) the tasks were run
     * <em>inline on that same virtual thread</em> -- no executor hand-off.  This
     * is verified by recording, from inside a delegated task, the thread that
     * ran it, using a hand-driven engine whose delegated tasks are wrapped to
     * capture their runner.
     */
    @Test
    public void testDelegatedTasksRunInlineOnCallingVirtualThread() throws Exception {
        Path dir = Files.createTempDirectory("ssl-engine-task");
        try {
            SSLContext ctx = newContext(dir);
            try (ServerSocketChannel ssc = ServerSocketChannel.open()) {
                ssc.socket().bind(new InetSocketAddress("localhost", 0));
                int port = ssc.socket().getLocalPort();

                final AtomicReference<Throwable> serverError = new AtomicReference<>();
                Thread serverThread = Thread.ofVirtual().start(() -> {
                    try (SocketChannel ch = ssc.accept()) {
                        SSLEngine engine = ctx.createSSLEngine();
                        engine.setUseClientMode(false);
                        new SslEngineChannel(engine, ch, "server").handshake();
                    } catch (Throwable t) {
                        serverError.set(t);
                    }
                });

                // Client side: drive the handshake manually so we can observe
                // that NEED_TASK tasks run on THIS thread when driven inline the
                // same way SslEngineChannel drives them.
                final AtomicReference<Thread> taskThread = new AtomicReference<>();
                final AtomicReference<Thread> driverThread = new AtomicReference<>();
                final AtomicReference<Boolean> sawNeedTask =
                    new AtomicReference<>(false);
                final AtomicReference<Throwable> clientError = new AtomicReference<>();

                Thread clientThread = Thread.ofVirtual().start(() -> {
                    try (SocketChannel ch = SocketChannel.open(
                            new InetSocketAddress("localhost", port))) {
                        driverThread.set(Thread.currentThread());
                        SSLEngine engine = ctx.createSSLEngine("localhost", port);
                        engine.setUseClientMode(true);
                        SslEngineChannel sec =
                            new SslEngineChannel(engine, ch, "client");
                        // The observer wraps the production inline execution:
                        // record the running thread, then run the task inline.
                        sec.handshakeWithTaskObserver(t -> {
                            sawNeedTask.set(true);
                            taskThread.set(Thread.currentThread());
                            t.run();
                        });
                    } catch (Throwable t) {
                        clientError.set(t);
                    }
                });

                clientThread.join(15_000);
                serverThread.join(15_000);
                if (clientError.get() != null) {
                    throw new AssertionError("client failed", clientError.get());
                }
                if (serverError.get() != null) {
                    throw new AssertionError("server failed", serverError.get());
                }

                Assert.assertTrue("TLS 1.3 handshake must surface NEED_TASK",
                    sawNeedTask.get());
                Assert.assertNotNull("a delegated task ran", taskThread.get());
                Assert.assertSame("delegated tasks run INLINE on the driving "
                    + "virtual thread (no executor hand-off)",
                    driverThread.get(), taskThread.get());
                Assert.assertTrue("driving thread is virtual",
                    driverThread.get().isVirtual());
            }
        } finally {
            deleteDir(dir);
        }
    }

    /**
     * Mimics the JERI mux usage: after the handshake, a dedicated writer thread
     * and a dedicated reader thread use the SAME {@link SslEngineChannel}
     * concurrently, exchanging many small framed messages both directions.  This
     * exercises concurrent {@code wrap}/{@code unwrap} plus record re-framing,
     * the pattern that broke the end-to-end round trip.
     */
    @Test
    public void testConcurrentReaderWriterLikeMux() throws Exception {
        Path dir = Files.createTempDirectory("ssl-engine-mux");
        try {
            SSLContext ctx = newContext(dir);
            try (ServerSocketChannel ssc = ServerSocketChannel.open()) {
                ssc.socket().bind(new InetSocketAddress("localhost", 0));
                int port = ssc.socket().getLocalPort();
                final int N = 200;

                final AtomicReference<Throwable> serverError = new AtomicReference<>();
                Thread serverThread = Thread.ofVirtual().start(() -> {
                    try (SocketChannel ch = ssc.accept()) {
                        SSLEngine engine = ctx.createSSLEngine();
                        engine.setUseClientMode(false);
                        SslEngineChannel sec =
                            new SslEngineChannel(engine, ch, "server");
                        sec.handshake();
                        InputStream in = sec.getInputStream();
                        OutputStream out = sec.getOutputStream();
                        // Echo N framed messages: read 8-byte frame, write it back.
                        for (int i = 0; i < N; i++) {
                            byte[] f = readFrame(in);
                            writeFrame(out, f);
                        }
                        sec.close();
                    } catch (Throwable t) {
                        serverError.set(t);
                    }
                });

                SocketChannel ch = SocketChannel.open(
                    new InetSocketAddress("localhost", port));
                SSLEngine engine = ctx.createSSLEngine("localhost", port);
                engine.setUseClientMode(true);
                final SslEngineChannel client =
                    new SslEngineChannel(engine, ch, "client");
                client.handshake();

                final OutputStream out = client.getOutputStream();
                final InputStream in = client.getInputStream();
                final AtomicReference<Throwable> writerError = new AtomicReference<>();

                Thread writer = Thread.ofVirtual().start(() -> {
                    try {
                        for (int i = 0; i < N; i++) {
                            byte[] f = new byte[8];
                            for (int j = 0; j < 8; j++) f[j] = (byte) (i + j);
                            writeFrame(out, f);
                        }
                    } catch (Throwable t) {
                        writerError.set(t);
                    }
                });

                for (int i = 0; i < N; i++) {
                    byte[] f = readFrame(in);
                    for (int j = 0; j < 8; j++) {
                        Assert.assertEquals("frame " + i + " byte " + j,
                            (byte) (i + j), f[j]);
                    }
                }
                writer.join(10_000);
                serverThread.join(10_000);
                client.close();
                if (writerError.get() != null) {
                    throw new AssertionError("writer failed", writerError.get());
                }
                if (serverError.get() != null) {
                    throw new AssertionError("server failed", serverError.get());
                }
            }
        } finally {
            deleteDir(dir);
        }
    }

    private static void writeFrame(OutputStream out, byte[] f) throws java.io.IOException {
        synchronized (out) {
            out.write(f);
            out.flush();
        }
    }

    private static byte[] readFrame(InputStream in) throws java.io.IOException {
        byte[] f = new byte[8];
        int off = 0;
        while (off < 8) {
            int r = in.read(f, off, 8 - off);
            if (r < 0) throw new java.io.EOFException("frame truncated at " + off);
            off += r;
        }
        return f;
    }

    // ------------------------------------------------------------ helpers

    private static String keytool() {
        String javaHome = System.getProperty("java.home");
        String exe = System.getProperty("os.name").toLowerCase().contains("win")
            ? "keytool.exe" : "keytool";
        Path p = Path.of(javaHome, "bin", exe);
        return Files.exists(p) ? p.toString() : "keytool";
    }

    private static void drain(Process p) throws java.io.IOException {
        try (InputStream in = p.getInputStream()) {
            byte[] b = new byte[4096];
            while (in.read(b) >= 0) { }
        }
    }

    private static void deleteDir(Path dir) throws java.io.IOException {
        if (dir == null) return;
        Files.walk(dir)
             .sorted(java.util.Comparator.reverseOrder())
             .forEach(p -> { try { Files.deleteIfExists(p); }
                             catch (java.io.IOException ignore) { } });
    }
}
