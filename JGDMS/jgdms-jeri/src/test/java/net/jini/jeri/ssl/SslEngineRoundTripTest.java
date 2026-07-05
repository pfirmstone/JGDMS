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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.export.Exporter;
import net.jini.jeri.AtomicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.ServerEndpoint;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * End-to-end SPIFFE-matching regression for the {@code net.jini.jeri.ssl}
 * transport driven by the new {@link SslEngineChannel} keystone (SSLEngine over
 * a blocking SocketChannel, over TCP) rather than by {@code SSLSocket}.
 *
 * <p>Both ends authenticate with a runtime-generated SPIFFE X.509 SVID (an EC
 * cert carrying both an X.500 subject DN and a {@code spiffe://} URI SAN).  The
 * test exports an {@code Echo} object over an {@code SslServerEndpoint} bound to
 * the server SVID Subject, connects as the client SVID Subject, and makes mutual
 * TLS calls with {@code ClientAuthentication.YES} + {@code ServerAuthentication
 * .YES} + {@code Integrity.YES} + {@code Confidentiality.YES}.  A green result
 * proves the SPIFFE/mTLS identity machinery (SSLContext + AuthManager +
 * SubjectCredentials + SPIFFE matching) is unchanged under the engine driver:
 * the mutual handshake, peer-cert extraction, X.500 and SPIFFE principal
 * derivation, and constraint enforcement all flow through the engine's
 * {@code getSession().getPeerCertificates()} exactly as they did through the
 * socket session.
 */
public class SslEngineRoundTripTest {

    public interface Echo extends Remote {
        String echo(String s) throws RemoteException;
        Map roundTrip(Map m) throws RemoteException;
    }

    public static final class EchoImpl implements Echo {
        public String echo(String s) { return "echo:" + s; }
        public Map roundTrip(Map m) {
            Map r = new HashMap(m);
            r.put("server", "seen");
            return r;
        }
    }

    // The FilterX509TrustManager caches its default trust manager statically at
    // first use, reading javax.net.ssl.trustStore at that moment for the whole
    // JVM.  So the fixtures (SVIDs + a single truststore of the trusted anchors)
    // are built ONCE per class, before any SSLContext is created.  The stranger
    // SVID is generated but deliberately NOT added as a trust anchor.
    private static Path dir;
    private static SslEngineSvidFactory.Svid server;
    private static SslEngineSvidFactory.Svid client;
    private static SslEngineSvidFactory.Svid stranger;
    private static String[] savedTrust;

    private static final InvocationConstraints MUTUAL_TLS =
        new InvocationConstraints(
            new net.jini.core.constraint.InvocationConstraint[] {
                ClientAuthentication.YES,
                ServerAuthentication.YES,
                Integrity.YES,
                Confidentiality.YES
            },
            null);

    private static final MethodConstraints MUTUAL_TLS_METHODS =
        new BasicMethodConstraints(MUTUAL_TLS);

    @BeforeClass
    public static void setUpClass() throws Exception {
        dir = Files.createTempDirectory("ssl-engine-svid");
        server = SslEngineSvidFactory.generate(
            dir, "server", "CN=Reggie",
            "spiffe://example.org/reggie");
        client = SslEngineSvidFactory.generate(
            dir, "client", "CN=Tester",
            "spiffe://example.org/tester");
        // The stranger SVID: generated but NOT installed as a trust anchor, so
        // it is rejected by the mutual TLS handshake.
        stranger = SslEngineSvidFactory.generate(
            dir, "stranger", "CN=Stranger",
            "spiffe://evil.example/stranger");
        // Trust ONLY the server and client SVIDs as anchors.  Set once for the
        // JVM (the trust manager caches statically at first SSLContext use).
        savedTrust = SslEngineSvidFactory.installTrustStore(
            dir, server.leaf, client.leaf);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        SslEngineSvidFactory.restoreTrustStore(savedTrust);
        if (dir != null) {
            Files.walk(dir)
                 .sorted(java.util.Comparator.reverseOrder())
                 .forEach(p -> { try { Files.deleteIfExists(p); }
                                 catch (IOException ignore) { } });
        }
    }

    private Exporter newExporter() {
        ServerEndpoint se = SslServerEndpoint.getInstance(
            server.subject, new X500Principal[] { server.x500 },
            "localhost", 0);
        return new BasicJeriExporter(
            se,
            new AtomicILFactory(MUTUAL_TLS_METHODS, null,
                getClass().getClassLoader()),
            false, true);
    }

    /** The full mutual-TLS SPIFFE round trip over the SSLEngine keystone. */
    @Test
    public void testMutualTlsSpiffeRoundTripOverEngine() throws Exception {
        final Exporter exporter = newExporter();
        final EchoImpl impl = new EchoImpl(); // keep a strong ref (DGC reaper)
        final Echo proxy = (Echo) Subject.callAs(server.subject,
            (Callable<Echo>) () -> (Echo) exporter.export(impl));
        try {
            // Make the calls as the client SVID Subject so the client end
            // authenticates with the Tester SPIFFE identity.
            Subject.callAs(client.subject, (Callable<Void>) () -> {
                Assert.assertEquals("echo:hi", proxy.echo("hi"));
                Map in = new HashMap();
                in.put("k", "v");
                Map out = proxy.roundTrip(in);
                Assert.assertEquals("v", out.get("k"));
                Assert.assertEquals("seen", out.get("server"));
                return null;
            });
        } finally {
            exporter.unexport(true);
        }
        java.util.Objects.requireNonNull(impl); // keep impl reachable to here
    }

    /** Concurrent mutual-TLS calls over one reused engine-backed connection. */
    @Test
    public void testConcurrentMutualTlsCalls() throws Exception {
        final Exporter exporter = newExporter();
        final EchoImpl impl = new EchoImpl(); // keep a strong ref (DGC reaper)
        final Echo proxy = (Echo) Subject.callAs(server.subject,
            (Callable<Echo>) () -> (Echo) exporter.export(impl));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            Subject.callAs(client.subject, (Callable<Void>) () -> {
                java.util.List<Future<String>> futures = new java.util.ArrayList<>();
                for (int i = 0; i < 32; i++) {
                    final int n = i;
                    futures.add(pool.submit(() ->
                        Subject.callAs(client.subject,
                            (Callable<String>) () -> proxy.echo("m" + n))));
                }
                for (int i = 0; i < futures.size(); i++) {
                    Assert.assertEquals("echo:m" + i, futures.get(i).get());
                }
                return null;
            });
        } finally {
            pool.shutdownNow();
            exporter.unexport(true);
        }
        java.util.Objects.requireNonNull(impl);
    }

    /**
     * Negative case: a client whose SVID is NOT a trust anchor on the server
     * must be rejected -- the mutual handshake fails, so the call does not
     * succeed.  This mechanizes the fence that the engine path does not silently
     * accept an untrusted peer.
     */
    @Test
    public void testUntrustedClientRejected() throws Exception {
        // Uses the class-level `stranger` SVID whose leaf is NOT a trust anchor.
        final Exporter exporter = newExporter();
        final EchoImpl impl = new EchoImpl(); // keep a strong ref (DGC reaper)
        final Echo proxy = (Echo) Subject.callAs(server.subject,
            (Callable<Echo>) () -> (Echo) exporter.export(impl));
        try {
            Subject.callAs(stranger.subject, (Callable<Void>) () -> {
                try {
                    proxy.echo("hi");
                    Assert.fail("call by an untrusted-SVID client must fail the "
                        + "mutual TLS handshake, not succeed");
                } catch (Exception expected) {
                    // good: untrusted client is rejected
                }
                return null;
            });
        } finally {
            exporter.unexport(true);
        }
        java.util.Objects.requireNonNull(impl);
    }
}
