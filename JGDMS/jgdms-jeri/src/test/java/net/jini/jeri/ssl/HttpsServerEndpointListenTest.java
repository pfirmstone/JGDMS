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
import net.jini.core.constraint.InvocationConstraints;
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
 * Listen smoke test for {@link HttpsServerEndpoint}: exports an object over
 * the HTTPS endpoint and makes a mutual TLS call.  The HTTPS listen handle is
 * created by an override of {@code createListenHandle}; if the accept thread is
 * not started for it, no connection is ever accepted and the call cannot
 * complete.
 */
public class HttpsServerEndpointListenTest {

    public interface Echo extends Remote {
        String echo(String s) throws RemoteException;
    }

    public static final class EchoImpl implements Echo {
        public String echo(String s) { return "echo:" + s; }
    }

    // See SslEngineRoundTripTest: the fixtures are built once per class.
    private static Path dir;
    private static SslEngineSvidFactory.Svid server;
    private static SslEngineSvidFactory.Svid client;
    private static String[] savedTrust;

    private static final MethodConstraints MUTUAL_TLS_METHODS =
        new BasicMethodConstraints(new InvocationConstraints(
            new net.jini.core.constraint.InvocationConstraint[] {
                ClientAuthentication.YES,
                ServerAuthentication.YES,
                Integrity.YES,
                Confidentiality.YES
            },
            null));

    @BeforeClass
    public static void setUpClass() throws Exception {
        dir = Files.createTempDirectory("https-listen-svid");
        server = SslEngineSvidFactory.generate(
            dir, "server", "CN=Reggie", "spiffe://example.org/reggie");
        client = SslEngineSvidFactory.generate(
            dir, "client", "CN=Tester", "spiffe://example.org/tester");
        savedTrust = SslEngineSvidFactory.installTrustStore(
            dir, server.leaf, client.leaf);
        resetSslTrustManagerCache();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        SslEngineSvidFactory.restoreTrustStore(savedTrust);
        resetSslTrustManagerCache();
        if (dir != null) {
            Files.walk(dir)
                 .sorted(java.util.Comparator.reverseOrder())
                 .forEach(p -> { try { Files.deleteIfExists(p); }
                                 catch (IOException ignore) { } });
        }
    }

    /**
     * Resets {@code FilterX509TrustManager}'s per-JVM trust-manager cache so
     * this class and the other ssl test classes each build from their own
     * truststore regardless of run order.
     */
    private static void resetSslTrustManagerCache() throws Exception {
        java.lang.reflect.Field tm =
            FilterX509TrustManager.class.getDeclaredField("tm");
        tm.setAccessible(true);
        tm.set(null, null);
    }

    /** A call over an exported HttpsServerEndpoint completes. */
    @Test(timeout = 30000)
    public void testCallOverHttpsEndpointCompletes() throws Exception {
        ServerEndpoint se = HttpsServerEndpoint.getInstance(
            server.subject, new X500Principal[] { server.x500 },
            "localhost", 0);
        final Exporter exporter = new BasicJeriExporter(
            se,
            new AtomicILFactory(MUTUAL_TLS_METHODS, null,
                getClass().getClassLoader()),
            false, true);
        final EchoImpl impl = new EchoImpl(); // keep a strong ref (DGC reaper)
        final Echo proxy = (Echo) Subject.callAs(server.subject,
            (Callable<Echo>) () -> (Echo) exporter.export(impl));
        try {
            Subject.callAs(client.subject, (Callable<Void>) () -> {
                Assert.assertEquals("echo:hi", proxy.echo("hi"));
                return null;
            });
        } finally {
            exporter.unexport(true);
        }
        java.util.Objects.requireNonNull(impl);
    }
}
