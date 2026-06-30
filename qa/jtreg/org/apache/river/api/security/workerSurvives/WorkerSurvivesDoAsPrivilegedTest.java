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
/* @test
 * @summary doAsPrivileged replaces the bound user but the ambient SPIFFE
 *          WorkerSubject survives. A MockSpireAgent serves a keytool-generated
 *          SVID over a Unix-domain socket so DirtyChai's SpiffeCredentialManager
 *          establishes a real ambient worker (Subject.processWorker()). Inside
 *          callAs(USER) the user is bound and the worker is present; inside a
 *          nested doAsPrivileged(plain OTHER) the user is REPLACED (current() is
 *          OTHER, not USER) yet processWorker() still returns the same SVID
 *          worker. Requires the DirtyChai SPIFFE worker API + a JDK with Unix
 *          domain sockets.
 * @build MockSpireAgent WorkerSurvivesDoAsPrivilegedTest
 * @run main/othervm/policy=worker.policy -Djava.security.manager=allow -Dspiffe.workload.socket=/tmp/jgdms-ws-test.sock -Dspiffe.reconnect.initial.backoff.ms=200 WorkerSurvivesDoAsPrivilegedTest
 */
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.PrivilegedAction;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.security.auth.Subject;
import javax.security.auth.UserSubject;
import javax.security.auth.x500.X500Principal;

public class WorkerSurvivesDoAsPrivilegedTest {

    static final String SPIFFE_ID = "spiffe://test.jgdms.local/svc/worker";
    static final X500Principal USER  = new X500Principal("CN=enduser");
    static final X500Principal OTHER = new X500Principal("CN=otheruser");

    static Path tempDir;
    static Path socket;

    public static void main(String[] args) throws Exception {
        socket = Path.of(System.getProperty("spiffe.workload.socket"));
        try {
            startMockSpireAgent();
            Subject worker = awaitWorker(25_000);

            check("ambient worker established (non-null)", worker != null);
            check("ambient worker is a WorkerSubject",
                    Class.forName("javax.security.auth.WorkerSubject").isInstance(worker));
            check("ambient worker carries the SVID SPIFFE id (" + SPIFFE_ID + ")",
                    SPIFFE_ID.equals(spiffeIdOf(worker)));

            // Establish the worker with no SM (so nothing triggers early
            // singleton init before the mock is up), THEN install the SM and
            // run the execute-as assertions under it.
            System.setSecurityManager(new SecurityManager());
            check("SecurityManager installed", System.getSecurityManager() != null);

            Subject.callAs((Callable<Void>) () -> {
                check("callAs(USER): current() carries USER", hasPrincipal(Subject.current(), USER));
                check("callAs(USER): worker still present", SPIFFE_ID.equals(spiffeIdOf(worker())));

                Subject.doAsPrivileged(plain(OTHER), (PrivilegedAction<Void>) () -> {
                    check("doAsPrivileged(OTHER): user REPLACED (current() is OTHER, not USER)",
                            hasPrincipal(Subject.current(), OTHER) && !hasPrincipal(Subject.current(), USER));
                    check("doAsPrivileged(OTHER): WORKER SURVIVES (processWorker() == the SVID worker)",
                            SPIFFE_ID.equals(spiffeIdOf(worker())));
                    return null;
                }, null);
                return null;
            }, user(USER));

            System.out.println("PASSED");
        } finally {
            cleanup();
        }
    }

    // ---- ambient worker access ----
    static Subject worker() { return new Subject().processWorker(); }

    static Subject awaitWorker(long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        Subject w;
        do {
            w = worker();
            if (w != null && spiffeIdOf(w) != null) return w;
            Thread.sleep(150);
        } while (System.currentTimeMillis() < deadline);
        return w;
    }

    static String spiffeIdOf(Subject s) {
        if (s == null) return null;
        for (Principal p : s.getPrincipals()) {
            String n = p.getName();
            if (n != null && n.startsWith("spiffe://")) return n;
        }
        return null;
    }

    static boolean hasPrincipal(Subject s, X500Principal want) {
        return s != null && s.getPrincipals().contains(want);
    }

    static UserSubject user(X500Principal p) { return new UserSubject(true, Set.of(p), Set.of(), Set.of()); }
    static Subject     plain(X500Principal p) { return new Subject(true, Set.of(p), Set.of(), Set.of()); }

    // ---- mock SPIRE agent driving a real ambient SpiffeSubject ----
    static void startMockSpireAgent() throws Exception {
        tempDir = Files.createTempDirectory("ws");
        Path caP12 = tempDir.resolve("ca.p12");
        Path caCer = tempDir.resolve("ca.cer");
        keytool("-genkeypair", "-alias", "ca", "-keyalg", "EC", "-groupname", "secp256r1",
                "-dname", "CN=JGDMS Test CA", "-sigalg", "SHA256withECDSA", "-validity", "1",
                "-keystore", caP12.toString(), "-storetype", "PKCS12", "-storepass", "changeit", "-noprompt");
        keytool("-exportcert", "-alias", "ca", "-keystore", caP12.toString(), "-storetype", "PKCS12",
                "-storepass", "changeit", "-rfc", "-file", caCer.toString());

        Path p12  = tempDir.resolve("worker.p12");
        Path csr  = tempDir.resolve("worker.csr");
        Path scer = tempDir.resolve("worker-signed.cer");
        keytool("-genkeypair", "-alias", "worker", "-keyalg", "EC", "-groupname", "secp256r1",
                "-dname", "CN=Worker", "-sigalg", "SHA256withECDSA", "-validity", "1",
                "-keystore", p12.toString(), "-storetype", "PKCS12", "-storepass", "changeit", "-noprompt");
        keytool("-certreq", "-alias", "worker", "-keystore", p12.toString(), "-storetype", "PKCS12",
                "-storepass", "changeit", "-file", csr.toString());
        keytool("-gencert", "-alias", "ca", "-keystore", caP12.toString(), "-storetype", "PKCS12",
                "-storepass", "changeit", "-infile", csr.toString(), "-outfile", scer.toString(),
                "-ext", "san=uri:" + SPIFFE_ID, "-validity", "1", "-rfc");
        keytool("-importcert", "-alias", "ca", "-keystore", p12.toString(), "-storetype", "PKCS12",
                "-storepass", "changeit", "-file", caCer.toString(), "-noprompt");
        keytool("-importcert", "-alias", "worker", "-keystore", p12.toString(), "-storetype", "PKCS12",
                "-storepass", "changeit", "-file", scer.toString(), "-noprompt");

        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(p12)) { ks.load(in, "changeit".toCharArray()); }
        PrivateKey key = (PrivateKey) ks.getKey("worker", "changeit".toCharArray());
        Certificate[] chain = ks.getCertificateChain("worker");

        Base64.Encoder enc = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII));
        StringBuilder certPem = new StringBuilder();
        for (Certificate c : chain) {
            certPem.append("-----BEGIN CERTIFICATE-----\n")
                   .append(enc.encodeToString(c.getEncoded())).append("\n-----END CERTIFICATE-----\n");
        }
        Path certFile = tempDir.resolve("svid.pem");
        Path keyFile  = tempDir.resolve("svid_key.pem");
        Files.write(certFile, certPem.toString().getBytes(StandardCharsets.US_ASCII));
        Files.write(keyFile, ("-----BEGIN PRIVATE KEY-----\n" + enc.encodeToString(key.getEncoded())
                + "\n-----END PRIVATE KEY-----\n").getBytes(StandardCharsets.US_ASCII));

        byte[] resp = MockSpireAgent.encodeX509SVIDResponse(
                MockSpireAgent.pemBlocksToDer(certFile, "CERTIFICATE"),
                MockSpireAgent.pemBlocksToDer(keyFile, "PRIVATE KEY"),
                null, SPIFFE_ID);
        Files.deleteIfExists(socket);
        Thread agent = new Thread(() -> {
            try { new MockSpireAgent(socket, resp).serve(); }
            catch (Exception e) { System.err.println("mock agent ended: " + e); }
        }, "mock-spire-agent");
        agent.setDaemon(true);
        agent.start();
        for (int i = 0; i < 100 && !Files.exists(socket); i++) Thread.sleep(50);
    }

    static void keytool(String... args) throws Exception {
        String javaHome = System.getProperty("java.home");
        String kt = new File(javaHome, "bin/keytool").getAbsolutePath();
        String[] cmd = new String[args.length + 1];
        cmd[0] = kt;
        System.arraycopy(args, 0, cmd, 1, args.length);
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) throw new RuntimeException("keytool failed: " + out);
    }

    static void cleanup() {
        try { if (socket != null) Files.deleteIfExists(socket); } catch (Exception ignore) {}
        try {
            if (tempDir != null) Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignore) {} });
        } catch (Exception ignore) {}
    }

    static void check(String what, boolean ok) {
        if (!ok) throw new AssertionError("FAIL: " + what);
        System.out.println("ok: " + what);
    }
}
