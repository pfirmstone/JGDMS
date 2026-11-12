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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a mock SPIRE agent as its own process, serving a new client SVID
 * ({@value AmbientIdentityFixture#CLIENT_SPIFFE_ID}), before the test JVM
 * starts, as SPIRE is deployed.  DirtyChai creates its SpiffeCredentialManager
 * when a JVM starts and does not connect to an agent that comes up later.
 * <p>
 * Arguments: the socket path and the longest time to run, in seconds.  It stops
 * earlier when {@code <socket>.stop} appears (the fixture writes it when the
 * test JVM exits).
 */
public final class MockSpireAgentLauncher {

    private MockSpireAgentLauncher() {}

    public static void main(String[] args) throws Exception {
        final Path socket = Paths.get(args[0]).toAbsolutePath();
        long deadline = System.currentTimeMillis() + Long.parseLong(args[1]) * 1000L;
        Path stop = Paths.get(socket + ".stop");
        Files.createDirectories(socket.getParent());
        Files.deleteIfExists(stop);
        Files.deleteIfExists(socket);

        Path dir = Files.createTempDirectory("mock-spire-agent");
        Path p12 = dir.resolve("client.p12");
        keytool("-genkeypair", "-alias", "client", "-keyalg", "EC", "-groupname", "secp256r1",
            "-sigalg", "SHA256withECDSA", "-dname", "CN=Tester",
            "-ext", "san=uri:" + AmbientIdentityFixture.CLIENT_SPIFFE_ID,
            "-ext", "KeyUsage=digitalSignature", "-validity", "1", "-storetype", "PKCS12",
            "-keystore", p12.toString(), "-storepass", "changeit", "-keypass", "changeit");
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(p12)) {
            ks.load(in, "changeit".toCharArray());
        }
        PrivateKey key = (PrivateKey) ks.getKey("client", "changeit".toCharArray());
        X509Certificate leaf = (X509Certificate) ks.getCertificate("client");
        final byte[] svid = MockSpireAgent.encodeX509SVIDResponse(
            leaf.getEncoded(), key.getEncoded(), null, AmbientIdentityFixture.CLIENT_SPIFFE_ID);

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
        while (System.currentTimeMillis() < deadline && !Files.exists(stop)) {
            Thread.sleep(500);
        }
        Files.deleteIfExists(socket);
        Files.deleteIfExists(stop);
        System.exit(0);
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
}
