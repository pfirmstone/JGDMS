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

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Set;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.security.auth.x500.X500Principal;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * ADVICE "item D" — the QUIC analogue of {@link SslEngineRoundTripTest}: drives
 * TWO {@code jdk.internal.net.quic.QuicTLSEngine} instances against each other
 * <em>in memory</em> (server + client), pumping handshake CRYPTO bytes per
 * {@code KeySpace} until the TLS handshake completes, to validate <b>server-side
 * client-certificate mutual auth</b> ({@code SSLParameters.setNeedClientAuth(true)})
 * driven by the JGDMS {@code AuthManager} + a SPIFFE SVID.
 *
 * <p>NO Kwik transport, NO UDP, NO packet/AEAD path — only the RFC 9001 CRYPTO
 * handshake seam. This exercises the DirtyChai B1/B2/B3 trust-dispatch
 * relaxations (commit #228) end-to-end at the SPIFFE level: a JGDMS custom
 * {@code X509ExtendedTrustManager} ({@code AuthManager}/{@code FilterX509TrustManager})
 * passes {@code isUsableWithQuic} (B1), its client-cert validation routes through
 * the 3-arg {@code SSLEngine}-adapter path (B2), and an unrecognised transport is
 * fail-secure rejected (B3).
 *
 * <p><b>Reflection note:</b> {@code jdk.internal.net.quic} is an internal package.
 * The whole engine surface is reached reflectively so the test compiles on a
 * vanilla build JDK (where the package is not exported) and runs only on DirtyChai
 * with {@code --add-exports java.base/jdk.internal.net.quic=ALL-UNNAMED}. When the
 * package or a facade is unavailable, the tests {@code Assume}-skip rather than
 * fail, so a normal build is never broken.
 *
 * <p><b>Launch:</b> run with
 * {@code --add-exports java.base/jdk.internal.net.quic=ALL-UNNAMED} on the
 * DirtyChai product image (JEP 517 QUIC-TLS engine). The surefire {@code quic}
 * profile supplies the flag; the default build excludes this class.
 */
public class QuicEngineMtlsTest {

    // The FilterX509TrustManager caches its default trust manager statically at
    // first use, reading javax.net.ssl.trustStore then for the whole JVM. So all
    // SVIDs + one truststore of the trusted anchors are built ONCE per class,
    // before any SSLContext is created (identical constraint to SslEngineRoundTripTest).
    private static Path dir;
    private static SslEngineSvidFactory.Svid server;
    private static SslEngineSvidFactory.Svid client;
    private static SslEngineSvidFactory.Svid stranger;   // NOT a trust anchor
    private static String[] savedTrust;
    private static boolean quicAvailable;

    @BeforeClass
    public static void setUpClass() throws Exception {
        quicAvailable = QuicEngine.isAvailable();
        if (!quicAvailable) {
            return; // tests self-skip via Assume
        }
        dir = Files.createTempDirectory("quic-mtls-svid");
        server = SslEngineSvidFactory.generate(
            dir, "server", "CN=Reggie", "spiffe://example.org/reggie");
        client = SslEngineSvidFactory.generate(
            dir, "client", "CN=Tester", "spiffe://example.org/tester");
        stranger = SslEngineSvidFactory.generate(
            dir, "stranger", "CN=Stranger", "spiffe://evil.example/stranger");
        // Trust ONLY the server and client SVIDs -- the stranger's leaf is NOT an anchor.
        savedTrust = SslEngineSvidFactory.installTrustStore(dir, server.leaf, client.leaf);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (savedTrust != null) SslEngineSvidFactory.restoreTrustStore(savedTrust);
        if (dir != null) {
            Files.walk(dir).sorted(java.util.Comparator.reverseOrder())
                 .forEach(p -> { try { Files.deleteIfExists(p); }
                                 catch (Exception ignore) { } });
        }
    }

    /** Builds the server-side JGDMS SSLContext (ServerAuthManager) bound to the server SVID. */
    private static SSLContext serverSslContext() throws Exception {
        Utilities.SSLContextInfo info = Utilities.getServerSSLContextInfo(
            server.subject, Collections.singleton(server.x500));
        return info.sslContext;
    }

    /**
     * Builds the client-side JGDMS SSLContext (ClientAuthManager) for the given
     * SVID subject; {@code permittedLocal} = the client's own principal.
     */
    private static SSLContext clientSslContext(SslEngineSvidFactory.Svid svid) throws Exception {
        ClientAuthManager am = new ClientAuthManager(
            svid.subject, Collections.singleton(svid.x500), null);
        SSLContext ctx = SSLContext.getInstance("TLSv1.3");
        ctx.init(new KeyManager[]{ am }, new TrustManager[]{ am }, null);
        return ctx;
    }

    // ---------------------------------------------------------------- POSITIVE

    /**
     * A valid client SVID mutual-TLS handshake over the engine pair completes,
     * and the server sees the client's SVID cert (SPIFFE-SAN trust check ran and
     * accepted).
     */
    @Test
    public void testMutualTlsSpiffeHandshakeCompletesOverEnginePair() throws Exception {
        org.junit.Assume.assumeTrue("QUIC-TLS engine not available (needs DirtyChai + "
            + "--add-exports java.base/jdk.internal.net.quic=ALL-UNNAMED)", quicAvailable);

        QuicEngine se = QuicEngine.server(serverSslContext());       // needClientAuth=true
        QuicEngine ce = QuicEngine.client(clientSslContext(client), "localhost", 4433);

        QuicEngine.Result r = QuicEngine.driveHandshake(ce, se);
        Assert.assertNull("mutual-TLS SPIFFE handshake must complete without error: "
            + r.failure, r.failure);
        Assert.assertTrue("client engine reports TLS handshake complete", ce.isComplete());
        Assert.assertTrue("server engine reports TLS handshake complete", se.isComplete());

        // The server must have the CLIENT's SVID as its peer certificate -> client
        // mutual auth happened AND the JGDMS SPIFFE-SAN trust check accepted it.
        SSLSession serverSession = se.getSession();
        Certificate[] peer = serverSession.getPeerCertificates();
        Assert.assertNotNull("server has client peer certificates (mTLS)", peer);
        Assert.assertTrue("server peer cert chain non-empty", peer.length > 0);
        Assert.assertEquals("server's peer cert is the client SVID leaf",
            client.leaf, peer[0]);

        X509Certificate leaf = (X509Certificate) peer[0];
        Assert.assertEquals("client SVID X.500 identity",
            client.x500, leaf.getSubjectX500Principal());
        Set<String> sans = Utilities.spiffeSanUris(leaf);
        Assert.assertTrue("client SVID carries its spiffe:// URI SAN",
            sans.contains("spiffe://example.org/tester"));

        // And the client saw the server's SVID.
        Certificate[] serverPeer = ce.getSession().getPeerCertificates();
        Assert.assertEquals("client's peer cert is the server SVID leaf",
            server.leaf, serverPeer[0]);
    }

    // ---------------------------------------------------------------- NEGATIVE

    /**
     * A client whose SVID is NOT a trust anchor on the server is rejected: the
     * server's JGDMS trust check fails during the client-cert validation, so the
     * handshake does NOT complete and the untrusted client is NOT admitted.
     */
    @Test
    public void testUntrustedClientSvidRejectedByServerTrustCheck() throws Exception {
        org.junit.Assume.assumeTrue("QUIC-TLS engine not available", quicAvailable);

        QuicEngine se = QuicEngine.server(serverSslContext());       // needClientAuth=true
        QuicEngine ce = QuicEngine.client(clientSslContext(stranger), "localhost", 4433);

        QuicEngine.Result r = QuicEngine.driveHandshake(ce, se);
        Assert.assertNotNull("an untrusted-SVID client must be REJECTED by the server "
            + "trust check, not admitted", r.failure);
        Assert.assertFalse("server must NOT report the handshake complete for an "
            + "untrusted client", se.isComplete());

        // The rejection must be a certificate/trust failure (the JGDMS trust check
        // is the teeth), not an unrelated mechanics stall. Walk the cause chain for
        // the fatal alert / CertificateException the server raises when the stranger
        // SVID fails FilterX509TrustManager's PKIX + SPIFFE/X.500 principal check.
        String chain = causeChainString(r.failure);
        Assert.assertTrue("rejection must be a certificate/trust failure (was: " + chain + ")",
            chain.toLowerCase().contains("certificate")
                || chain.toLowerCase().contains("trust")
                || chain.toLowerCase().contains("unable to find valid certification path")
                || chain.contains("CERTIFICATE"));
    }

    private static String causeChainString(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c.getClass().getName());
            if (c.getMessage() != null) sb.append(": ").append(c.getMessage());
            sb.append(" | ");
            if (c.getCause() == c) break;
        }
        return sb.toString();
    }

    // =====================================================================
    // QuicEngine: a reflective driver over jdk.internal.net.quic.QuicTLSEngine.
    // Kept inside the test (not a shared fixture) so it is self-contained and the
    // reflection is isolated. All internal types are reached by name.
    // =====================================================================
    static final class QuicEngine {

        private static final String PKG = "jdk.internal.net.quic.";
        private static Class<?> CTX;        // QuicTLSContext
        private static Class<?> ENGINE;     // QuicTLSEngine
        private static Class<?> KEYSPACE;   // QuicTLSEngine.KeySpace
        private static Class<?> VERSION;    // QuicVersion
        private static Object QUIC_V1;
        private static Object KS_INITIAL, KS_HANDSHAKE, KS_ONE_RTT;
        private static boolean available;

        static {
            boolean ok = false;
            try {
                CTX = Class.forName(PKG + "QuicTLSContext");
                ENGINE = Class.forName(PKG + "QuicTLSEngine");
                KEYSPACE = Class.forName(PKG + "QuicTLSEngine$KeySpace");
                VERSION = Class.forName(PKG + "QuicVersion");
                QUIC_V1 = enumConst(VERSION, "QUIC_V1");
                KS_INITIAL = enumConst(KEYSPACE, "INITIAL");
                KS_HANDSHAKE = enumConst(KEYSPACE, "HANDSHAKE");
                KS_ONE_RTT = enumConst(KEYSPACE, "ONE_RTT");
                // Class.forName succeeds even when jdk.internal.net.quic is NOT
                // exported; only an actual reflective *invocation* throws
                // IllegalAccessException. Probe a real construction so a JVM
                // missing --add-exports self-skips (Assume) instead of erroring.
                CTX.getConstructor(SSLContext.class)
                   .newInstance(SSLContext.getInstance("TLSv1.3"));
                ok = true;
            } catch (Throwable t) {
                ok = !(rootCause(t) instanceof IllegalAccessException)
                        && CTX != null && ENGINE != null && KEYSPACE != null
                        && VERSION != null;
            }
            available = ok;
        }

        private static Throwable rootCause(Throwable t) {
            Throwable c = t;
            while (c.getCause() != null && c.getCause() != c) c = c.getCause();
            return c;
        }

        static boolean isAvailable() { return available; }

        private final Object engine;
        private QuicEngine(Object engine) { this.engine = engine; }

        static QuicEngine server(SSLContext sslContext) throws Exception {
            Object qctx = CTX.getConstructor(SSLContext.class).newInstance(sslContext);
            Object eng = CTX.getMethod("createEngine").invoke(qctx);
            configure(eng, /*client*/ false, /*needClientAuth*/ true);
            return new QuicEngine(eng);
        }

        static QuicEngine client(SSLContext sslContext, String host, int port) throws Exception {
            Object qctx = CTX.getConstructor(SSLContext.class).newInstance(sslContext);
            Object eng = CTX.getMethod("createEngine", String.class, int.class)
                            .invoke(qctx, host, port);
            configure(eng, /*client*/ true, /*needClientAuth*/ false);
            return new QuicEngine(eng);
        }

        private static void configure(Object eng, boolean clientMode, boolean needClientAuth)
                throws Exception {
            ENGINE.getMethod("setUseClientMode", boolean.class).invoke(eng, clientMode);
            SSLParameters p = (SSLParameters) ENGINE.getMethod("getSSLParameters").invoke(eng);
            p.setApplicationProtocols(new String[]{ "jgdms-quic-mtls" });
            p.setProtocols(new String[]{ "TLSv1.3" });
            if (needClientAuth) {
                p.setNeedClientAuth(true);   // the server mTLS gate
            }
            ENGINE.getMethod("setSSLParameters", SSLParameters.class).invoke(eng, p);
            // Register a (caching) consumer for the peer's transport params (required).
            Class<?> consumerType = Class.forName(PKG + "QuicTransportParametersConsumer");
            Object consumer = java.lang.reflect.Proxy.newProxyInstance(
                consumerType.getClassLoader(), new Class<?>[]{ consumerType },
                (proxy, method, args) -> null); // accept(ByteBuffer) -> no-op
            ENGINE.getMethod("setRemoteQuicTransportParametersConsumer", consumerType)
                  .invoke(eng, consumer);
            ENGINE.getMethod("versionNegotiated", VERSION).invoke(eng, QUIC_V1);
            // Minimal valid (present, empty) local transport parameters.
            ENGINE.getMethod("setLocalQuicTransportParameters", ByteBuffer.class)
                  .invoke(eng, ByteBuffer.allocate(0));
        }

        boolean isComplete() throws Exception {
            return (Boolean) ENGINE.getMethod("isTLSHandshakeComplete").invoke(engine);
        }

        /**
         * Simulates the QUIC transport-level HANDSHAKE_DONE frame, which the real
         * transport (not this CRYPTO-only harness) would carry: the server marks
         * it sent, the client records it received. After the TLS Finished
         * messages have been exchanged the server sits at NEED_SEND_HANDSHAKE_DONE
         * (TLS complete but not CONFIRMED until it sends this frame); the client
         * sits at NEED_RECV_HANDSHAKE_DONE. This models that transport step so
         * both engines reach HANDSHAKE_CONFIRMED.
         */
        boolean tryMarkHandshakeDone() throws Exception {
            try {
                return (Boolean) ENGINE.getMethod("tryMarkHandshakeDone").invoke(engine);
            } catch (java.lang.reflect.InvocationTargetException e) {
                return false; // wrong role / state -> no-op
            }
        }

        boolean tryReceiveHandshakeDone() throws Exception {
            try {
                return (Boolean) ENGINE.getMethod("tryReceiveHandshakeDone").invoke(engine);
            } catch (java.lang.reflect.InvocationTargetException e) {
                return false;
            }
        }

        SSLSession getSession() throws Exception {
            return (SSLSession) ENGINE.getMethod("getSession").invoke(engine);
        }

        private Object currentSendKeySpace() throws Exception {
            return ENGINE.getMethod("getCurrentSendKeySpace").invoke(engine);
        }

        private ByteBuffer getHandshakeBytes(Object keySpace) throws Exception {
            return (ByteBuffer) ENGINE.getMethod("getHandshakeBytes", KEYSPACE)
                                      .invoke(engine, keySpace);
        }

        private void consumeHandshakeBytes(Object keySpace, ByteBuffer buf) throws Exception {
            ENGINE.getMethod("consumeHandshakeBytes", KEYSPACE, ByteBuffer.class)
                  .invoke(engine, keySpace, buf);
        }

        private void pumpDelegatedTasks() throws Exception {
            // GAP-3: getDelegatedTask() is a no-op (returns null); trust/cert
            // validation runs INLINE during consumeHandshakeBytes. We drain
            // defensively so the driver is correct if a future engine hands tasks back.
            Method get = ENGINE.getMethod("getDelegatedTask");
            Runnable task;
            while ((task = (Runnable) get.invoke(engine)) != null) {
                task.run();
            }
        }

        /** Holds the terminal outcome: {@code failure == null} means both completed. */
        static final class Result {
            final Throwable failure;
            Result(Throwable failure) { this.failure = failure; }
        }

        /**
         * Bidirectionally pumps CRYPTO handshake bytes between the two engines
         * until both report {@code isTLSHandshakeComplete()} or one throws. A
         * throw (e.g. a fatal alert from the server's client-cert trust check) is
         * the negative-path outcome and is returned as {@code Result.failure}.
         */
        static Result driveHandshake(QuicEngine client, QuicEngine server) {
            try {
                // Kickstart: pull the ClientHello.
                final int MAX_ROUNDS = 40;
                for (int round = 0; round < MAX_ROUNDS; round++) {
                    boolean progressed = false;
                    progressed |= transfer(client, server); // client -> server
                    progressed |= transfer(server, client); // server -> client
                    client.pumpDelegatedTasks();
                    server.pumpDelegatedTasks();
                    if (client.isComplete() && server.isComplete()) {
                        return new Result(null);
                    }
                    if (!progressed) {
                        // CRYPTO exchange has stalled. If the TLS Finished messages
                        // have been exchanged, the only thing left is the QUIC
                        // transport-level HANDSHAKE_DONE frame (not CRYPTO). Model
                        // that transport step: server sends it, client receives it.
                        boolean advanced = server.tryMarkHandshakeDone();
                        advanced |= client.tryReceiveHandshakeDone();
                        if (client.isComplete() && server.isComplete()) {
                            return new Result(null);
                        }
                        if (!advanced) {
                            return new Result(new IllegalStateException(
                                "handshake stalled without completing (round " + round + ")"));
                        }
                    }
                }
                return new Result(new IllegalStateException(
                    "handshake did not complete within " + MAX_ROUNDS + " rounds"));
            } catch (Throwable t) {
                // Unwrap reflection wrappers to surface the real fatal alert /
                // CertificateException from the trust check.
                Throwable cause = t;
                while (cause instanceof java.lang.reflect.InvocationTargetException
                        && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                return new Result(cause);
            }
        }

        /**
         * Moves all currently-available handshake bytes from {@code from} to
         * {@code to}, across whatever send key spaces {@code from} is producing.
         * Returns {@code true} if any bytes were transferred.
         */
        private static boolean transfer(QuicEngine from, QuicEngine to) throws Exception {
            boolean moved = false;
            // A single engine may have INITIAL then HANDSHAKE bytes queued; loop
            // until getHandshakeBytes returns null/empty for the current space.
            for (int i = 0; i < 8; i++) {
                Object space;
                try {
                    space = from.currentSendKeySpace();
                } catch (Exception e) {
                    break;
                }
                ByteBuffer out;
                try {
                    out = from.getHandshakeBytes(space);
                } catch (Exception e) {
                    // getHandshakeBytes throws IllegalStateException if the space
                    // isn't the current send space, or IOException; treat as "no
                    // more to send this pass".
                    break;
                }
                if (out == null || !out.hasRemaining()) {
                    break;
                }
                to.consumeHandshakeBytes(space, out);
                moved = true;
            }
            return moved;
        }

        private static Object enumConst(Class<?> enumType, String name) throws Exception {
            for (Object c : enumType.getEnumConstants()) {
                if (c.toString().equals(name)) return c;
            }
            throw new IllegalStateException("no enum constant " + name + " in " + enumType);
        }
    }
}
