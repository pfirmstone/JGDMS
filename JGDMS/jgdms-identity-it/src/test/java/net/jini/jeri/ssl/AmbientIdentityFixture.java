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

import java.io.File;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.security.auth.Subject;
import net.jini.io.MarshalledInstance;
import org.junit.Assume;

/**
 * The client side of the identity tests, as a SPIFFE workload is deployed: its
 * identity is ambient.  A mock SPIRE agent serves the client SVID on the Unix
 * socket named by {@code spiffe.workload.socket}, DirtyChai's
 * {@code SpiffeCredentialManager} makes it the process worker
 * ({@code Subject.processWorker()}), and the endpoints use it with no
 * {@code Subject.callAs}, which is reserved for user subjects.  The servers run
 * in a JVM of their own ({@link IdentityServers}), started here once per JVM.
 * <p>
 * Needs DirtyChai (the ambient worker API) and {@code spiffe.workload.socket}
 * set before anything in the JVM uses SSL; otherwise the tests are skipped.
 */
final class AmbientIdentityFixture {

    static final String CLIENT_SPIFFE_ID = "spiffe://example.org/tester";

    private static Map<String, Object> proxies;
    private static Process servers;

    private AmbientIdentityFixture() {}

    /** Skips the calling test unless the ambient worker can be established. */
    static synchronized void start() throws Exception {
        if (proxies != null) {
            return;
        }
        boolean dirtyChai;
        try {
            Subject.class.getMethod("processWorker");
            dirtyChai = true;
        } catch (NoSuchMethodException e) {
            dirtyChai = false;
        }
        String socketProperty = System.getProperty("spiffe.workload.socket");
        if (!dirtyChai && socketProperty != null) {
            // Nothing will use a MockSpireAgentLauncher started for this JVM: stop it.
            Files.write(Paths.get(Paths.get(socketProperty).toAbsolutePath() + ".stop"), new byte[0]);
        }
        Assume.assumeTrue("needs DirtyChai's ambient worker identity", dirtyChai);
        Assume.assumeTrue("needs -Dspiffe.workload.socket set before the JVM uses SSL", socketProperty != null);

        Path dir = Files.createTempDirectory("ambient-identity");
        X509Certificate clientLeaf = leafOf(awaitWorker(2_000));
        if (clientLeaf == null) {
            clientLeaf = startInProcessAgent(dir, Paths.get(socketProperty));
        }

        Files.write(dir.resolve("client.cer"), clientLeaf.getEncoded());
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        ProcessBuilder pb = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
            IdentityServers.class.getName(), dir.toString(), "client.cer");
        pb.redirectErrorStream(true);
        pb.redirectOutput(dir.resolve("servers.log").toFile());
        servers = pb.start();
        final Path stop = Paths.get(Paths.get(socketProperty).toAbsolutePath() + ".stop");
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                servers.destroy();
                try {
                    // Ends a MockSpireAgentLauncher serving this JVM, if one does.
                    Files.write(stop, new byte[0]);
                } catch (java.io.IOException e) {
                    // nothing to stop
                }
            }
        }));
        Path ready = dir.resolve("ready");
        for (int i = 0; i < 1200 && !Files.exists(ready); i++) {
            if (servers.waitFor(100, TimeUnit.MILLISECONDS)) {
                throw new AssertionError("the server JVM exited: " + new String(
                    Files.readAllBytes(dir.resolve("servers.log")), "UTF-8"));
            }
        }
        if (!Files.exists(ready)) {
            throw new AssertionError("the server JVM did not start; see " + dir.resolve("servers.log"));
        }

        List<X509Certificate> anchors = new ArrayList<X509Certificate>();
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Map<String, Object> read = new HashMap<String, Object>();
        try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
            for (Path f : files) {
                String name = f.getFileName().toString();
                if (name.endsWith(".cer") && !name.equals("client.cer")) {
                    try (InputStream in = Files.newInputStream(f)) {
                        anchors.add((X509Certificate) cf.generateCertificate(in));
                    }
                } else if (name.endsWith(".mi")) {
                    try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(f))) {
                        read.put(name.substring(0, name.length() - 3),
                            ((MarshalledInstance) in.readObject()).get(false));
                    }
                }
            }
        }
        Path trust = Files.createDirectories(dir.resolve("client-trust"));
        SslEngineSvidFactory.installTrustStore(trust, anchors.toArray(new X509Certificate[0]));
        java.lang.reflect.Field tm = FilterX509TrustManager.class.getDeclaredField("tm");
        tm.setAccessible(true);
        tm.set(null, null);
        proxies = read;
    }

    /** The proxy the server JVM exported under {@code name}. */
    static Object proxy(String name) {
        Object p = proxies.get(name);
        if (p == null) {
            throw new IllegalArgumentException("no proxy " + name + " in " + proxies.keySet());
        }
        return p;
    }

    /** The ambient worker, or null. */
    static Subject worker() {
        try {
            return (Subject) Subject.class.getMethod("processWorker").invoke(new Subject());
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * Serves a new client SVID from a mock SPIRE agent in this process and
     * waits for the worker.  DirtyChai creates its SpiffeCredentialManager when
     * the JVM starts, so this relies on it connecting to an agent that comes up
     * later.  Where it does not, run the agent (MockSpireAgent's main) as its own
     * process before this JVM starts, as SPIRE is deployed; the worker it serves
     * is then used as found.
     */
    private static X509Certificate startInProcessAgent(Path dir, final Path socket) throws Exception {
        Path p12 = dir.resolve("client.p12");
        keytool("-genkeypair", "-alias", "client", "-keyalg", "EC", "-groupname", "secp256r1",
            "-sigalg", "SHA256withECDSA", "-dname", "CN=Tester", "-ext", "san=uri:" + CLIENT_SPIFFE_ID,
            "-ext", "KeyUsage=digitalSignature", "-validity", "1", "-storetype", "PKCS12",
            "-keystore", p12.toString(), "-storepass", "changeit", "-keypass", "changeit");
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(p12)) {
            ks.load(in, "changeit".toCharArray());
        }
        PrivateKey key = (PrivateKey) ks.getKey("client", "changeit".toCharArray());
        X509Certificate leaf = (X509Certificate) ks.getCertificate("client");
        final byte[] svid = MockSpireAgent.encodeX509SVIDResponse(
            leaf.getEncoded(), key.getEncoded(), null, CLIENT_SPIFFE_ID);
        Files.createDirectories(socket.getParent());
        Files.deleteIfExists(socket);
        Thread agent = new Thread(new Runnable() {
            public void run() {
                try {
                    new MockSpireAgent(socket, svid).serve();
                } catch (Exception e) {
                    System.err.println("mock SPIRE agent ended: " + e);
                }
            }
        }, "mock-spire-agent");
        agent.setDaemon(true);
        agent.start();
        if (awaitWorker(30_000) == null) {
            throw new AssertionError("the ambient worker was not established from the mock SPIRE agent at "
                + socket + " (DirtyChai's SpiffeCredentialManager does not connect to an agent that"
                + " starts after the JVM: run MockSpireAgent as its own process first)");
        }
        return leaf;
    }

    /** The worker's leaf certificate, from its CertPath, or null. */
    private static X509Certificate leafOf(Subject worker) {
        if (worker == null) {
            return null;
        }
        for (Object c : worker.getPublicCredentials()) {
            if (c instanceof java.security.cert.CertPath) {
                List<? extends java.security.cert.Certificate> certs =
                    ((java.security.cert.CertPath) c).getCertificates();
                if (!certs.isEmpty() && certs.get(0) instanceof X509Certificate) {
                    return (X509Certificate) certs.get(0);
                }
            }
        }
        return null;
    }

    private static void keytool(String... args) throws Exception {
        List<String> cmd = new ArrayList<String>();
        cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "keytool");
        for (String a : args) {
            cmd.add(a);
        }
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        byte[] out = p.getInputStream().readAllBytes();
        if (p.waitFor() != 0) {
            throw new IllegalStateException("keytool failed: " + new String(out, "UTF-8"));
        }
    }

    private static Subject awaitWorker(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Subject w = worker();
            if (w != null) {
                for (Principal p : w.getPrincipals()) {
                    if (p.getName() != null && p.getName().startsWith("spiffe://")) {
                        return w;
                    }
                }
            }
            Thread.sleep(150);
        }
        return null;
    }
}
