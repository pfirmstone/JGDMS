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
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.concurrent.Callable;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.export.Exporter;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.AtomicDerILFactory;
import net.jini.jeri.AtomicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.Endpoint;
import net.jini.jeri.InvocationLayerFactory;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.http.HttpServerEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.jeri.uds.UdsEndpoint;
import net.jini.jeri.uds.UdsServerEndpoint;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Acceptance gate for the {@code MarshallingFormat} invocation-layer constraint
 * across ALL FOUR JERI transports (tcp, http, uds, ssl) -- JGDMS-STD-008
 * sec.18.3.
 *
 * <h2>The security invariant under test</h2>
 * A required {@link MarshallingFormat#ATOMIC_DER} must PREVENT an Atomic-JOSS
 * codec from serving the call. That enforcement lives at the invocation layer:
 * {@code BasicInvocationHandler.requireMarshallingFormat} (client) and
 * {@code BasicInvocationDispatcher.verifyAndStripMarshallingFormat} (server)
 * throw {@link UnsupportedConstraintException} when the codec's actual format
 * (base handler = {@code JOSS}; {@code AtomicDerInvocationHandler} = {@code
 * ATOMIC_DER}) does not match the required format.
 *
 * <p>For that check to run, the <b>transport must DEFER</b> the constraint
 * (carry it, unfulfilled, up to the invocation layer). If a transport instead
 * claimed to <em>satisfy</em> a {@code MarshallingFormat} (returning a support
 * verdict), the invocation-layer check would be skipped and an Atomic-JOSS codec
 * would silently bypass a required {@code ATOMIC_DER} -- the exact hole this
 * gate guards against. A "full support" (mis)implementation therefore FAILS the
 * JOSS-prevention cases below.
 *
 * <h2>What each transport is exercised at</h2>
 * <ul>
 *   <li><b>tcp, http, ssl</b>: a real exported round trip. POSITIVE: an
 *       {@code AtomicDerILFactory} proxy (codec = {@code ATOMIC_DER}) carrying a
 *       required {@code MarshallingFormat.ATOMIC_DER} is invoked and SUCCEEDS
 *       (so the transport deferred, not rejected, the constraint). JOSS-PREVENTION:
 *       a base {@code AtomicILFactory} proxy (codec = {@code JOSS}) carrying the
 *       same required {@code ATOMIC_DER} THROWS {@link
 *       UnsupportedConstraintException}. ssl authenticates both ends with
 *       runtime-generated SPIFFE SVIDs (as {@link SslEngineRoundTripTest}).</li>
 *   <li><b>uds</b>: the uds round-trip harness is platform-fragile (its
 *       dedicated test is disabled), so uds is exercised at the transport's
 *       constraint-distillation level with NO server:
 *       {@link #uds_defersMarshallingFormatButRejectsUnsupported()} proves a
 *       required {@code MarshallingFormat.ATOMIC_DER} is DEFERRED (it passes
 *       distillation, so the failure is a connect {@link IOException}, never
 *       {@link UnsupportedConstraintException}) while a genuinely unsupported
 *       transport constraint ({@code Confidentiality.YES} over plaintext uds) is
 *       still REJECTED with {@link UnsupportedConstraintException}. A full uds
 *       round trip is additionally attempted but {@link Assume}-skipped when the
 *       platform cannot bind a uds control socket. uds JOSS-prevention itself is
 *       the transport-independent invocation-layer check proven by the other
 *       three transports' JOSS-prevention cases.</li>
 * </ul>
 */
public class MarshallingFormatDeferralTest {

    // ---------------------------------------------------------------- fixtures

    public interface Echo extends Remote {
        String echo(String s) throws RemoteException;
    }

    public static final class EchoImpl implements Echo {
        public String echo(String s) { return "echo:" + s; }
    }

    /** Requires ONLY a MarshallingFormat.ATOMIC_DER (for the plaintext transports). */
    private static final MethodConstraints MC_ATOMIC_DER =
        new BasicMethodConstraints(
            new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null));

    /** Mutual TLS + a required MarshallingFormat.ATOMIC_DER (for the ssl transport). */
    private static final MethodConstraints MC_ATOMIC_DER_MUTUAL_TLS =
        new BasicMethodConstraints(
            new InvocationConstraints(
                new InvocationConstraint[] {
                    ClientAuthentication.YES,
                    ServerAuthentication.YES,
                    Integrity.YES,
                    Confidentiality.YES,
                    MarshallingFormat.ATOMIC_DER
                },
                null));

    // ssl SVID fixtures (mirrors SslEngineRoundTripTest): both ends authenticate
    // with a runtime-generated SPIFFE SVID; a single truststore of the two anchors
    // is installed ONCE for the JVM before any SSLContext is built.
    private static Path sslDir;
    private static SslEngineSvidFactory.Svid sslServer;
    private static SslEngineSvidFactory.Svid sslClient;
    private static String[] savedTrust;

    @BeforeClass
    public static void setUpClass() throws Exception {
        sslDir = Files.createTempDirectory("mfmt-ssl-svid");
        sslServer = SslEngineSvidFactory.generate(
            sslDir, "server", "CN=Reggie", "spiffe://example.org/reggie");
        sslClient = SslEngineSvidFactory.generate(
            sslDir, "client", "CN=Tester", "spiffe://example.org/tester");
        savedTrust = SslEngineSvidFactory.installTrustStore(
            sslDir, sslServer.leaf, sslClient.leaf);
        // FilterX509TrustManager caches its trust manager in a private static
        // built ONCE per JVM from javax.net.ssl.trustStore. Another ssl test
        // class (e.g. SslEngineRoundTripTest) may already have cached ITS
        // truststore; reset the cache so this class's SSLContexts build from the
        // truststore we just installed. We reset again on teardown so the next
        // class rebuilds from its own truststore -- keeping both classes green
        // regardless of run order.
        resetSslTrustManagerCache();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        SslEngineSvidFactory.restoreTrustStore(savedTrust);
        resetSslTrustManagerCache();
        if (sslDir != null) {
            Files.walk(sslDir)
                 .sorted(java.util.Comparator.reverseOrder())
                 .forEach(p -> { try { Files.deleteIfExists(p); }
                                 catch (IOException ignore) { } });
        }
    }

    /**
     * Resets {@code FilterX509TrustManager}'s per-JVM static trust-manager cache
     * so the next SSLContext built by the ssl transport re-reads the current
     * {@code javax.net.ssl.trustStore}. Test-only isolation shim: multiple ssl
     * test classes each install their own JVM-global truststore, but the trust
     * manager is otherwise cached at first use for the whole JVM.
     */
    private static void resetSslTrustManagerCache() throws Exception {
        java.lang.reflect.Field tm =
            FilterX509TrustManager.class.getDeclaredField("tm");
        tm.setAccessible(true);
        tm.set(null, null);
    }

    private static ClassLoader cl() {
        return MarshallingFormatDeferralTest.class.getClassLoader();
    }

    // ------------------------------------------------------------------ tcp

    @Test
    public void tcp_positive_atomicDerRoundTripSucceeds() throws Exception {
        Assert.assertEquals("echo:hi",
            roundTrip(TcpServerEndpoint.getInstance(0), MC_ATOMIC_DER,
                      /* der */ true, null, null));
    }

    @Test
    public void tcp_jossPrevention_atomicJossRejected() throws Exception {
        assertJossPrevented(TcpServerEndpoint.getInstance(0), MC_ATOMIC_DER,
                            null, null, "tcp");
    }

    // ------------------------------------------------------------------ http

    @Test
    public void http_positive_atomicDerRoundTripSucceeds() throws Exception {
        Assert.assertEquals("echo:hi",
            roundTrip(HttpServerEndpoint.getInstance(0), MC_ATOMIC_DER,
                      /* der */ true, null, null));
    }

    @Test
    public void http_jossPrevention_atomicJossRejected() throws Exception {
        assertJossPrevented(HttpServerEndpoint.getInstance(0), MC_ATOMIC_DER,
                            null, null, "http");
    }

    // ------------------------------------------------------------------ ssl

    @Test
    public void ssl_positive_atomicDerRoundTripSucceeds() throws Exception {
        ServerEndpoint se = SslServerEndpoint.getInstance(
            sslServer.subject, new X500Principal[] { sslServer.x500 },
            "localhost", 0);
        Assert.assertEquals("echo:hi",
            roundTrip(se, MC_ATOMIC_DER_MUTUAL_TLS, /* der */ true,
                      sslServer.subject, sslClient.subject));
    }

    @Test
    public void ssl_jossPrevention_atomicJossRejected() throws Exception {
        ServerEndpoint se = SslServerEndpoint.getInstance(
            sslServer.subject, new X500Principal[] { sslServer.x500 },
            "localhost", 0);
        assertJossPrevented(se, MC_ATOMIC_DER_MUTUAL_TLS,
                            sslServer.subject, sslClient.subject, "ssl");
    }

    // ------------------------------------------------------------------ uds

    /**
     * Transport-level (server-less) proof for uds: a required
     * MarshallingFormat.ATOMIC_DER is DEFERRED past constraint distillation
     * (so the client fails to CONNECT -- an IOException -- rather than being
     * rejected with UnsupportedConstraintException), while a genuinely
     * unsupported transport constraint over plaintext uds
     * (Confidentiality.YES) is still REJECTED at distillation with
     * UnsupportedConstraintException. This is the "defer, never claim to
     * satisfy" behaviour the security invariant requires.
     */
    @Test
    public void uds_defersMarshallingFormatButRejectsUnsupported() throws Exception {
        Path dir = Files.createTempDirectory("mfmt-uds");
        String bogus = dir.resolve("no-listener.sock").toString();
        try {
            Endpoint ep = UdsEndpoint.getInstance(bogus);

            Throwable deferred = drive(ep,
                new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null));
            Assert.assertNotNull("connecting to a socket with no listener must fail",
                deferred);
            Assert.assertFalse("uds must DEFER MarshallingFormat (carry it to the "
                + "invocation layer), NOT reject it at distillation: "
                + describe(deferred), hasUnsupportedConstraint(deferred));
            Assert.assertTrue("a deferred MarshallingFormat must let distillation pass "
                + "so the failure is the missing-listener connect IOException, was: "
                + describe(deferred), causeIs(deferred, IOException.class));

            Throwable rejected = drive(ep,
                new InvocationConstraints(Confidentiality.YES, null));
            Assert.assertNotNull(rejected);
            Assert.assertTrue("a genuinely unsupported transport constraint "
                + "(Confidentiality.YES over plaintext uds) must still be REJECTED "
                + "at distillation with UnsupportedConstraintException, was: "
                + describe(rejected), hasUnsupportedConstraint(rejected));
        } finally {
            Files.deleteIfExists(dir);
        }
    }

    /**
     * A full uds round trip, Assume-skipped when the platform cannot bind a uds
     * control socket (the dedicated uds round-trip test is disabled for the same
     * portability reason). When it runs it asserts BOTH the positive
     * (ATOMIC_DER succeeds) and JOSS-prevention (JOSS rejected) cases.
     */
    @Test
    public void uds_roundTripWherePlatformSupportsIt() throws Exception {
        Path dir = Files.createTempDirectory("mfmt-uds-rt");
        String path = dir.resolve("s.sock").toString();
        ServerEndpoint se;
        try {
            se = UdsServerEndpoint.getInstance(path);
        } catch (Throwable t) {
            Files.deleteIfExists(dir);
            Assume.assumeNoException(
                "uds server endpoint unavailable on this platform", t);
            return;
        }
        try {
            String result;
            try {
                result = roundTrip(se, MC_ATOMIC_DER, /* der */ true, null, null);
            } catch (Throwable bindFailure) {
                // Binding/gating a uds control socket is platform-fragile; skip
                // rather than fail if the socket cannot be established here.
                Assume.assumeNoException(
                    "uds round trip not bindable on this platform", bindFailure);
                return;
            }
            Assert.assertEquals("echo:hi", result);
            assertJossPrevented(UdsServerEndpoint.getInstance(path),
                                MC_ATOMIC_DER, null, null, "uds");
        } finally {
            Files.deleteIfExists(java.nio.file.Paths.get(path));
            Files.deleteIfExists(dir);
        }
    }

    // -------------------------------------------------------------- helpers

    /**
     * Exports an EchoImpl over {@code se} with an {@code AtomicDerILFactory}
     * (der=true, codec ATOMIC_DER) or a base {@code AtomicILFactory}
     * (der=false, codec JOSS) declaring {@code mc}, then invokes echo("hi").
     * Server export and the client call run under the given Subjects (or
     * directly when null, for the plaintext transports).
     */
    private String roundTrip(ServerEndpoint se, MethodConstraints mc, boolean der,
                             Subject serverSubject, Subject clientSubject)
        throws Exception
    {
        InvocationLayerFactory ilf = der
            ? new AtomicDerILFactory(mc, null, cl())
            : new AtomicILFactory(mc, null, cl());
        final Exporter exporter = new BasicJeriExporter(se, ilf, false, true);
        final EchoImpl impl = new EchoImpl(); // strong ref (DGC reaper)
        try {
            final Echo proxy = asSubject(serverSubject,
                (Callable<Echo>) () -> (Echo) exporter.export(impl));
            try {
                return asSubject(clientSubject,
                    (Callable<String>) () -> proxy.echo("hi"));
            } finally {
                java.util.Objects.requireNonNull(impl);
            }
        } finally {
            try { exporter.unexport(true); } catch (Exception ignore) { }
        }
    }

    /**
     * Asserts the JOSS-prevention invariant: a base {@code AtomicILFactory}
     * proxy (codec JOSS) carrying a required MarshallingFormat.ATOMIC_DER must
     * fail the call with an UnsupportedConstraintException (client-side
     * requireMarshallingFormat, or server-side verifyAndStripMarshallingFormat).
     */
    private void assertJossPrevented(ServerEndpoint se, MethodConstraints mc,
                                     Subject serverSubject, Subject clientSubject,
                                     String transport)
        throws Exception
    {
        try {
            String r = roundTrip(se, mc, /* der */ false, serverSubject, clientSubject);
            Assert.fail(transport + ": a JOSS codec carrying a required "
                + "MarshallingFormat.ATOMIC_DER must be rejected, but the call "
                + "succeeded with result " + r + " -- the transport must have "
                + "wrongly claimed to satisfy the format (JOSS-bypass hole)");
        } catch (Throwable t) {
            Assert.assertTrue(transport + ": JOSS codec + required ATOMIC_DER must "
                + "throw UnsupportedConstraintException, was: " + describe(t),
                hasUnsupportedConstraint(t));
        }
    }

    private static <T> T asSubject(Subject s, Callable<T> c) throws Exception {
        if (s == null) {
            return c.call();
        }
        return Subject.callAs(s, c);
    }

    /** Runs a client-side request to exhaustion; returns the thrown Throwable or null. */
    private static Throwable drive(Endpoint ep, InvocationConstraints constraints) {
        try {
            OutboundRequestIterator it = ep.newRequest(constraints);
            while (it.hasNext()) {
                it.next();
            }
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    /** True if t or any cause is an UnsupportedConstraintException. */
    private static boolean hasUnsupportedConstraint(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof UnsupportedConstraintException) {
                return true;
            }
        }
        return false;
    }

    /** True if t or any cause is an instance of the given type. */
    private static boolean causeIs(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }

    private static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (sb.length() > 0) sb.append(" <- ");
            sb.append(c.getClass().getName());
            if (c.getMessage() != null) sb.append(": ").append(c.getMessage());
        }
        return sb.toString();
    }
}
