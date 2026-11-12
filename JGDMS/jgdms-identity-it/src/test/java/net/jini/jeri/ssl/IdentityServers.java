/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.export.CodebaseAccessor;
import net.jini.export.Exporter;
import net.jini.io.MarshalledInstance;
import net.jini.jeri.AtomicDerILFactory;
import net.jini.jeri.AtomicILFactory;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.InvocationLayerFactory;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.ssl.SslEngineSvidFactory.Svid;
import net.jini.jeri.tcp.TcpServerEndpoint;

/**
 * The servers of the identity tests, run in a JVM of their own.  A JVM with an
 * ambient SPIFFE worker presents that one identity on every endpoint, client
 * and server, so the servers cannot share the client's JVM.  Each server here
 * has its own SVID, given to its endpoint explicitly; this JVM has no ambient
 * worker.
 * <p>
 * Arguments: a directory and the client's leaf certificate (DER) in it.  The
 * servers trust the client's leaf and each other's.  Each exported proxy is
 * written to {@code <name>.mi} as a {@link MarshalledInstance}, and each leaf
 * the client should trust to {@code <name>.cer}; the server {@code stranger}
 * is deliberately not among them.  {@code ready} is written last.  The process
 * runs until its standard input closes.
 */
public final class IdentityServers {

    private IdentityServers() {}

    /** A bootstrap proxy's or a service's server side; getCertFactoryType says which server answered. */
    public static final class Accessor implements CodebaseAccessor {
        private final String annotation;
        private final String who;

        Accessor(String annotation, String who) {
            this.annotation = annotation;
            this.who = who;
        }

        public String getClassAnnotation() { return annotation; }
        public String getCertFactoryType() { return who; }
        public String getCertPathEncoding() { return null; }
        public byte[] getEncodedCerts() { return null; }
    }

    static final String RESOLVE_CODEBASE = "http://localhost/resolve-pin.jar";
    static final String PROBE_CODEBASE = "http://localhost/probe.jar";

    private static final List<Object> KEEP = new ArrayList<Object>();

    public static void main(String[] args) throws Exception {
        Path dir = Paths.get(args[0]);
        X509Certificate client = (X509Certificate) java.security.cert.CertificateFactory.getInstance("X.509")
            .generateCertificate(Files.newInputStream(dir.resolve(args[1])));
        Svid server = SslEngineSvidFactory.generate(dir, "server", "CN=Reggie", "spiffe://example.org/reggie");
        Svid impostor = SslEngineSvidFactory.generate(dir, "impostor", "CN=Reggie", "spiffe://example.org/impostor");
        Svid twoUri = SslEngineSvidFactory.generate(dir, "twouri", "CN=Reggie",
            "spiffe://example.org/alpha,uri:spiffe://example.org/beta");
        Svid other = SslEngineSvidFactory.generate(dir, "other", "CN=Other", "spiffe://example.org/other");
        Svid stranger = SslEngineSvidFactory.generate(dir, "stranger", "CN=Stranger", "spiffe://example.org/stranger");
        Path trust = Files.createDirectories(dir.resolve("servers-trust"));
        SslEngineSvidFactory.installTrustStore(trust,
            client, server.leaf, impostor.leaf, twoUri.leaf, other.leaf, stranger.leaf);

        ClassLoader l = IdentityServers.class.getClassLoader();
        BasicMethodConstraints mutual = new BasicMethodConstraints(AuthenticateServerTest.MUTUAL);

        // ResolvePinsTheProbedIdentityTest
        write(dir, "reggie", ssl(server, new BasicILFactory(mutual, null, l), RESOLVE_CODEBASE, "reggie"));
        write(dir, "reggieDer", ssl(server, new AtomicDerILFactory(mutual, null, l), RESOLVE_CODEBASE, "reggie"));
        write(dir, "sameDn", ssl(impostor, new BasicILFactory(mutual, null, l), RESOLVE_CODEBASE, "impostor"));
        write(dir, "twoUris", ssl(twoUri, new BasicILFactory(mutual, null, l), RESOLVE_CODEBASE, "twoUris"));
        write(dir, "stranger", ssl(stranger, new BasicILFactory(mutual, null, l), RESOLVE_CODEBASE, "stranger"));
        write(dir, "tcp", tcp(new BasicILFactory(null, null, l), RESOLVE_CODEBASE, "tcp"));
        // ServerIdentityPinProbeTest: AtomicILFactory exports (the AtomicInvocationHandler)
        write(dir, "atomicReggie", ssl(server, new AtomicILFactory(mutual, null, l), PROBE_CODEBASE, "reggie"));
        write(dir, "atomicOther", ssl(other, new AtomicILFactory(mutual, null, l), PROBE_CODEBASE, "other"));
        write(dir, "atomicTcp", tcp(new AtomicILFactory(null, null, l), PROBE_CODEBASE, "tcp"));
        // CodebaseFreeProxyIdentityTest: no codebase annotation
        write(dir, "freeReggie", ssl(server, new BasicILFactory(mutual, null, l), null, "reggie"));
        write(dir, "freeImpostor", ssl(impostor, new BasicILFactory(mutual, null, l), null, "impostor"));
        write(dir, "freeTcp", tcp(new BasicILFactory(null, null, l), null, "tcp"));

        for (Svid s : new Svid[]{server, impostor, twoUri, other}) {
            Files.write(dir.resolve(s.spiffeUri.replaceAll("[^A-Za-z0-9]", "_") + ".cer"), s.leaf.getEncoded());
        }
        Files.write(dir.resolve("ready"), new byte[0]);
        while (System.in.read() >= 0) {
            // runs until the parent closes the pipe or ends the process
        }
        System.exit(0);
    }

    private static Object ssl(Svid s, InvocationLayerFactory ilf, String annotation, String who) throws Exception {
        ServerEndpoint se = SslServerEndpoint.getInstance(s.subject, new X500Principal[]{s.x500}, "localhost", 0);
        return export(se, ilf, annotation, who, s.subject);
    }

    private static Object tcp(InvocationLayerFactory ilf, String annotation, String who) throws Exception {
        return export(TcpServerEndpoint.getInstance("localhost", 0), ilf, annotation, who, null);
    }

    private static Object export(ServerEndpoint se, InvocationLayerFactory ilf, String annotation,
            String who, Subject subject) throws Exception {
        final Exporter ex = new BasicJeriExporter(se, ilf, false, true);
        final Accessor impl = new Accessor(annotation, who);
        KEEP.add(ex);
        KEEP.add(impl);
        if (subject == null) {
            return ex.export(impl);
        }
        return Subject.callAs(subject, (Callable<Object>) () -> ex.export(impl));
    }

    /**
     * Writes the proxy in an Atomic stream, as JGDMS transfers proxies: the
     * AtomicDerInvocationHandler restores its client-side limits only through
     * its AtomicSerial constructor, so plain Java serialization would give a
     * handler that cannot make a call.
     */
    private static void write(Path dir, String name, Object proxy) throws Exception {
        try (OutputStream o = Files.newOutputStream(dir.resolve(name + ".mi"));
             ObjectOutputStream out = new ObjectOutputStream(o)) {
            out.writeObject(new org.apache.river.api.io.AtomicMarshalledInstance(proxy));
        }
    }
}
