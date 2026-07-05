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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;

/**
 * Test-only helper that mints a SPIFFE X.509 SVID (a self-signed cert carrying a
 * {@code spiffe://} URI Subject Alternative Name plus an X.500 subject DN) by
 * shelling out to {@code keytool}, and assembles it into a
 * {@link javax.security.auth.Subject} usable by the {@code net.jini.jeri.ssl}
 * transport for a mutual-TLS handshake.  No certificate material is committed to
 * the repository; everything is generated at test time in a temp directory.
 *
 * <p>The Subject shape mirrors the production SPIFFE credential machinery (see
 * {@code SpiffeJwtDispatchIntegrationTest.loadWorkerSubject}): principals =
 * {X500Principal, SpiffePrincipal}, public credentials = {CertPath}, private
 * credentials = {X500PrivateCredential}.  This exercises the FULL SPIFFE
 * matching path (X.500 <em>and</em> {@code spiffe://} SAN) through the SSLEngine
 * keystone, over TCP.
 */
final class SslEngineSvidFactory {

    private SslEngineSvidFactory() { }

    /** A generated SVID plus its assembled Subject and truststore. */
    static final class Svid {
        final X500Principal x500;
        final String spiffeUri;
        final Subject subject;
        /** The leaf cert, for use as a truststore trust anchor. */
        final X509Certificate leaf;

        Svid(X500Principal x500, String spiffeUri, Subject subject,
             X509Certificate leaf) {
            this.x500 = x500;
            this.spiffeUri = spiffeUri;
            this.subject = subject;
            this.leaf = leaf;
        }
    }

    /**
     * Generates a self-signed SVID with the given DN and SPIFFE URI and returns
     * the assembled read-only Subject (X.500 + SPIFFE principals, CertPath
     * public credential, X500PrivateCredential private credential).
     *
     * @param dir       temp directory to write the keystore into
     * @param alias     keystore alias
     * @param dname     X.500 distinguished name, e.g. {@code "CN=Reggie"}
     * @param spiffeUri the {@code spiffe://} URI to embed as a URI SAN
     */
    static Svid generate(Path dir, String alias, String dname, String spiffeUri)
        throws Exception
    {
        Path ks = dir.resolve(alias + ".p12");
        char[] pw = "changeit".toCharArray();
        // EC keys (secp256r1) match the JGDMS SPIFFE SVID convention; keytool's
        // -ext san=uri:spiffe://... writes the URI Subject Alternative Name.
        String[] cmd = {
            keytool(),
            "-genkeypair",
            "-alias", alias,
            "-keyalg", "EC",
            "-groupname", "secp256r1",
            "-sigalg", "SHA256withECDSA",
            "-dname", dname,
            "-ext", "san=uri:" + spiffeUri,
            "-ext", "KeyUsage=digitalSignature",
            "-validity", "1",
            "-storetype", "PKCS12",
            "-keystore", ks.toString(),
            "-storepass", "changeit",
            "-keypass", "changeit"
        };
        run(cmd);

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(ks)) {
            keyStore.load(in, pw);
        }
        PrivateKey key = (PrivateKey) keyStore.getKey(alias, pw);
        Certificate[] chain = keyStore.getCertificateChain(alias);
        X509Certificate leaf = (X509Certificate) chain[0];

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        CertPath certPath = cf.generateCertPath(Arrays.asList(chain));

        X500PrivateCredential priv = new X500PrivateCredential(leaf, key);

        Set<Principal> principals = new HashSet<Principal>();
        principals.add(leaf.getSubjectX500Principal());
        // Add SPIFFE principals via the same Utilities path production uses, so
        // the runtime SpiffePrincipal class (from jgdms-lib-dl) is matched.
        Collection<Principal> spiffe =
            Utilities.spiffePrincipalsFromCertificate(leaf);
        principals.addAll(spiffe);

        Set<Object> pub = new HashSet<Object>();
        pub.add(certPath);
        Set<Object> prv = new HashSet<Object>();
        prv.add(priv);

        Subject subject = new Subject(true, principals, pub, prv);
        return new Svid(leaf.getSubjectX500Principal(), spiffeUri, subject, leaf);
    }

    /**
     * Writes a JKS truststore containing the given leaf certs as trust anchors
     * and points {@code javax.net.ssl.trustStore*} system properties at it, so
     * the {@code FilterX509TrustManager}'s default trust manager accepts these
     * self-signed SVIDs.  Returns the previous property values for restoration.
     */
    static String[] installTrustStore(Path dir, X509Certificate... anchors)
        throws Exception
    {
        Path ts = dir.resolve("truststore.p12");
        KeyStore trust = KeyStore.getInstance("PKCS12");
        trust.load(null, null);
        int i = 0;
        for (X509Certificate a : anchors) {
            trust.setCertificateEntry("anchor" + (i++), a);
        }
        try (java.io.OutputStream out = Files.newOutputStream(ts)) {
            trust.store(out, "changeit".toCharArray());
        }
        String[] prev = {
            System.getProperty("javax.net.ssl.trustStore"),
            System.getProperty("javax.net.ssl.trustStorePassword"),
            System.getProperty("javax.net.ssl.trustStoreType")
        };
        System.setProperty("javax.net.ssl.trustStore", ts.toString());
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
        return prev;
    }

    static void restoreTrustStore(String[] prev) {
        restore("javax.net.ssl.trustStore", prev[0]);
        restore("javax.net.ssl.trustStorePassword", prev[1]);
        restore("javax.net.ssl.trustStoreType", prev[2]);
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static String keytool() {
        String javaHome = System.getProperty("java.home");
        String exe = System.getProperty("os.name").toLowerCase().contains("win")
            ? "keytool.exe" : "keytool";
        Path p = Path.of(javaHome, "bin", exe);
        return Files.exists(p) ? p.toString() : "keytool";
    }

    private static void run(String[] cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder out = new StringBuilder();
        try (InputStream in = p.getInputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.append(new String(buf, 0, n));
            }
        }
        int rc = p.waitFor();
        if (rc != 0) {
            throw new IOException("keytool failed (rc=" + rc + "):\n"
                + Arrays.toString(cmd) + "\n" + out);
        }
    }
}
