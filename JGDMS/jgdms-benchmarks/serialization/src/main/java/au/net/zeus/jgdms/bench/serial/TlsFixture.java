/*
 * Copyright (C) 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.bench.serial;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;

/**
 * The credentials of the TLS benchmarks: a server and a client identity (EC P-256 certificates made with
 * {@code keytool}) held as {@link Subject}s with an {@link X500Principal}, the certificate path and the private
 * credential, and one trust store holding both certificates. The same identities and trust store serve JERI's
 * {@code SslEndpoint} and the RMI TLS socket factories, so both stacks authenticate each other with the same
 * mutual-TLS credentials. (SPIFFE identities are used in production; plain X.500 identities are enough here.)
 *
 * <p>{@code -Dbench.tls.dir=<directory>} names a directory made by {@code main("generate", dir)}, so that the
 * benchmark JVM and a separate server JVM use the same credentials; without it a fixture is generated into a
 * temporary directory. {@link #current()} also points the JVM's trust store at the fixture's, and must run before
 * the first SSL context is created.
 */
public final class TlsFixture {

    private static final char[] PASSWORD = "changeit".toCharArray();
    private static TlsFixture current;

    public final Subject server;
    public final X500Principal serverPrincipal;
    public final Subject client;
    public final X500Principal clientPrincipal;
    public final Path dir;

    private TlsFixture(Path dir, Subject server, X500Principal serverPrincipal, Subject client,
                       X500Principal clientPrincipal) {
        this.dir = dir;
        this.server = server;
        this.serverPrincipal = serverPrincipal;
        this.client = client;
        this.clientPrincipal = clientPrincipal;
    }

    /** The fixture of this JVM: from {@code bench.tls.dir} if set, else generated once. */
    public static synchronized TlsFixture current() throws Exception {
        if (current == null) {
            String dir = System.getProperty("bench.tls.dir");
            Path path = dir != null ? Path.of(dir) : Files.createTempDirectory("bench-tls");
            if (dir == null || !Files.exists(path.resolve("truststore.p12"))) {
                generate(path);
            }
            current = load(path);
            System.setProperty("javax.net.ssl.trustStore", path.resolve("truststore.p12").toString());
            System.setProperty("javax.net.ssl.trustStorePassword", new String(PASSWORD));
            System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
        }
        return current;
    }

    /** Makes server.p12, client.p12 and truststore.p12 in {@code dir}. */
    public static void generate(Path dir) throws Exception {
        Files.createDirectories(dir);
        keytool(dir.resolve("server.p12"), "server", "CN=bench-server");
        keytool(dir.resolve("client.p12"), "client", "CN=bench-client");
        KeyStore trust = KeyStore.getInstance("PKCS12");
        trust.load(null, null);
        trust.setCertificateEntry("server", certificate(dir.resolve("server.p12"), "server"));
        trust.setCertificateEntry("client", certificate(dir.resolve("client.p12"), "client"));
        try (OutputStream out = Files.newOutputStream(dir.resolve("truststore.p12"))) {
            trust.store(out, PASSWORD);
        }
    }

    static TlsFixture load(Path dir) throws Exception {
        Subject[] s = new Subject[2];
        X500Principal[] p = new X500Principal[2];
        String[] names = {"server", "client"};
        for (int i = 0; i < 2; i++) {
            KeyStore ks = open(dir.resolve(names[i] + ".p12"));
            PrivateKey key = (PrivateKey) ks.getKey(names[i], PASSWORD);
            Certificate[] chain = ks.getCertificateChain(names[i]);
            X509Certificate leaf = (X509Certificate) chain[0];
            CertPath certPath = CertificateFactory.getInstance("X.509").generateCertPath(Arrays.asList(chain));
            Set<Principal> principals = new HashSet<>();
            principals.add(leaf.getSubjectX500Principal());
            Set<Object> pub = new HashSet<>();
            pub.add(certPath);
            Set<Object> prv = new HashSet<>();
            prv.add(new X500PrivateCredential(leaf, key));
            s[i] = new Subject(true, principals, pub, prv);
            p[i] = leaf.getSubjectX500Principal();
        }
        return new TlsFixture(dir, s[0], p[0], s[1], p[1]);
    }

    private static KeyStore open(Path file) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(file)) {
            ks.load(in, PASSWORD);
        }
        return ks;
    }

    private static X509Certificate certificate(Path file, String alias) throws Exception {
        return (X509Certificate) open(file).getCertificate(alias);
    }

    private static void keytool(Path keystore, String alias, String dname) throws Exception {
        String exe = System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool";
        Path tool = Path.of(System.getProperty("java.home"), "bin", exe);
        String[] cmd = {
            Files.exists(tool) ? tool.toString() : "keytool", "-genkeypair", "-alias", alias, "-keyalg", "EC",
            "-groupname", "secp256r1", "-sigalg", "SHA256withECDSA", "-dname", dname,
            "-ext", "KeyUsage=digitalSignature", "-validity", "30", "-storetype", "PKCS12",
            "-keystore", keystore.toString(), "-storepass", new String(PASSWORD), "-keypass", new String(PASSWORD)
        };
        Process pr = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        byte[] out;
        try (InputStream in = pr.getInputStream()) {
            out = in.readAllBytes();
        }
        if (pr.waitFor() != 0) {
            throw new IOException("keytool failed:\n" + new String(out));
        }
    }

    /** {@code generate <dir>}: makes the fixture files for a benchmark and a server JVM to share. */
    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !args[0].equals("generate")) {
            System.err.println("usage: TlsFixture generate <directory>");
            System.exit(2);
        }
        generate(Path.of(args[1]));
    }
}
