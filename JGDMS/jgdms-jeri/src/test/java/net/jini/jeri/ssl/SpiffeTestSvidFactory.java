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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Base64;

/**
 * Test-only utility that generates ephemeral SPIFFE SVIDs at test time using
 * the JDK's {@code keytool} command and the standard {@link KeyStore} API.
 *
 * <p>No certificate material is committed to the repository.  Call
 * {@link #createReggieFixture(Path)} once per test class (e.g. from a
 * {@code @BeforeClass} method) to obtain a {@link Fixture} that contains
 * paths to the generated {@code svid.pem} and {@code svid_key.pem} files,
 * then delete the temporary directory when the test class is done.
 *
 * <p>The generated SVID has the following properties:
 * <ul>
 *   <li>Algorithm: ECDSA P-256 (secp256r1), SHA256withECDSA</li>
 *   <li>Subject DN: {@code CN=Reggie}</li>
 *   <li>SPIFFE URI SAN: {@code spiffe://test.jgdms.local/svc/reggie}</li>
 *   <li>Issuer: ephemeral self-signed test CA ({@code CN=JGDMS Test CA})</li>
 *   <li>Validity: 1 day (sufficient for any single test run)</li>
 * </ul>
 *
 * <p>The {@code svid.pem} file contains both the leaf SVID certificate and
 * the CA certificate (concatenated PEM blocks), matching the two-certificate
 * chain that {@link SpiffeCredentialManager.FileSvidSource} expects.
 */
public final class SpiffeTestSvidFactory {

    /** SPIFFE ID embedded in the generated reggie SVID. */
    public static final String REGGIE_SPIFFE_ID = "spiffe://test.jgdms.local/svc/reggie";

    /** Subject DN embedded in the generated reggie SVID leaf certificate. */
    public static final String REGGIE_SUBJECT_DN = "CN=Reggie";

    private static final String CA_ALIAS      = "ca";
    private static final String REGGIE_ALIAS  = "reggie";
    private static final String STORE_PASS    = "changeit";
    private static final String STORE_TYPE    = "PKCS12";
    private static final String KEY_ALG       = "EC";
    private static final String GROUP_NAME    = "secp256r1";
    private static final String SIG_ALG       = "SHA256withECDSA";
    private static final int    VALIDITY_DAYS = 1;
    private static final String NL            = "\n";
    private static final Base64.Encoder PEM_ENCODER =
            Base64.getMimeEncoder(64, NL.getBytes(StandardCharsets.US_ASCII));

    private SpiffeTestSvidFactory() { }

    /**
     * Holds the paths to the generated {@code svid.pem} and
     * {@code svid_key.pem} files together with the directory that contains
     * them (for easy cleanup).
     */
    public static final class Fixture {
        /** Path to the PEM file containing the SVID leaf cert + CA cert. */
        public final Path svidPem;
        /** Path to the PKCS#8 private-key PEM file. */
        public final Path svidKeyPem;
        /** Directory that contains both PEM files (temp dir). */
        public final Path dir;

        private Fixture(Path dir, Path svidPem, Path svidKeyPem) {
            this.dir        = dir;
            this.svidPem    = svidPem;
            this.svidKeyPem = svidKeyPem;
        }
    }

    /**
     * Creates a temporary SPIFFE SVID fixture for the {@code reggie} service.
     *
     * @param tempDir writable directory in which the PEM files and
     *                intermediate keystores are placed; the caller is
     *                responsible for deleting this directory after the
     *                test run
     * @return a {@link Fixture} containing the paths to the generated files
     * @throws Exception if keytool fails or any I/O error occurs
     */
    public static Fixture createReggieFixture(Path tempDir) throws Exception {
        Path caP12      = tempDir.resolve("ca.p12");
        Path reggieP12  = tempDir.resolve("reggie.p12");
        Path csrFile    = tempDir.resolve("reggie.csr");
        Path signedCer  = tempDir.resolve("reggie-signed.cer");
        Path caCerFile  = tempDir.resolve("ca.cer");

        // 1. Generate ephemeral CA key pair + self-signed CA cert
        keytool(
            "-genkeypair",
            "-alias",    CA_ALIAS,
            "-keyalg",   KEY_ALG,
            "-groupname", GROUP_NAME,
            "-dname",    "CN=JGDMS Test CA",
            "-sigalg",   SIG_ALG,
            "-validity", String.valueOf(VALIDITY_DAYS),
            "-keystore", caP12.toString(),
            "-storetype", STORE_TYPE,
            "-storepass", STORE_PASS,
            "-noprompt"
        );

        // 2. Generate SVID key pair (initially self-signed placeholder)
        keytool(
            "-genkeypair",
            "-alias",    REGGIE_ALIAS,
            "-keyalg",   KEY_ALG,
            "-groupname", GROUP_NAME,
            "-dname",    REGGIE_SUBJECT_DN,
            "-sigalg",   SIG_ALG,
            "-validity", String.valueOf(VALIDITY_DAYS),
            "-keystore", reggieP12.toString(),
            "-storetype", STORE_TYPE,
            "-storepass", STORE_PASS,
            "-noprompt"
        );

        // 3. Generate a CSR for the SVID key
        keytool(
            "-certreq",
            "-alias",    REGGIE_ALIAS,
            "-keystore", reggieP12.toString(),
            "-storetype", STORE_TYPE,
            "-storepass", STORE_PASS,
            "-file",     csrFile.toString()
        );

        // 4. Sign the CSR with the CA; embed the SPIFFE URI SAN
        keytool(
            "-gencert",
            "-alias",    CA_ALIAS,
            "-keystore", caP12.toString(),
            "-storetype", STORE_TYPE,
            "-storepass", STORE_PASS,
            "-infile",   csrFile.toString(),
            "-outfile",  signedCer.toString(),
            "-ext",      "san=uri:" + REGGIE_SPIFFE_ID,
            "-validity", String.valueOf(VALIDITY_DAYS),
            "-rfc"
        );

        // 5. Export the CA certificate to a PEM file
        keytool(
            "-exportcert",
            "-alias",    CA_ALIAS,
            "-keystore", caP12.toString(),
            "-storetype", STORE_TYPE,
            "-storepass", STORE_PASS,
            "-rfc",
            "-file",     caCerFile.toString()
        );

        // 6. Import the CA cert into the SVID keystore (required before step 7)
        keytool(
            "-importcert",
            "-alias",    CA_ALIAS,
            "-keystore", reggieP12.toString(),
            "-storetype", STORE_TYPE,
            "-storepass", STORE_PASS,
            "-file",     caCerFile.toString(),
            "-noprompt"
        );

        // 7. Import the signed SVID cert (with chain) into the SVID keystore
        keytool(
            "-importcert",
            "-alias",    REGGIE_ALIAS,
            "-keystore", reggieP12.toString(),
            "-storetype", STORE_TYPE,
            "-storepass", STORE_PASS,
            "-file",     signedCer.toString(),
            "-noprompt"
        );

        // 8. Load the SVID keystore and extract cert chain + private key
        KeyStore ks = KeyStore.getInstance(STORE_TYPE);
        try (InputStream in = Files.newInputStream(reggieP12)) {
            ks.load(in, STORE_PASS.toCharArray());
        }
        PrivateKey key     = (PrivateKey) ks.getKey(REGGIE_ALIAS, STORE_PASS.toCharArray());
        Certificate[] chain = ks.getCertificateChain(REGGIE_ALIAS);

        // 9. Write svid.pem (leaf cert + CA cert concatenated)
        StringBuilder svidPemContent = new StringBuilder();
        for (Certificate c : chain) {
            svidPemContent.append("-----BEGIN CERTIFICATE-----").append(NL);
            svidPemContent.append(PEM_ENCODER.encodeToString(c.getEncoded())).append(NL);
            svidPemContent.append("-----END CERTIFICATE-----").append(NL);
        }
        Path svidPemPath = tempDir.resolve("svid.pem");
        Files.write(svidPemPath, svidPemContent.toString().getBytes(StandardCharsets.US_ASCII));

        // 10. Write svid_key.pem (PKCS#8 format — BEGIN PRIVATE KEY)
        String keyPemContent = "-----BEGIN PRIVATE KEY-----" + NL
                + PEM_ENCODER.encodeToString(key.getEncoded()) + NL
                + "-----END PRIVATE KEY-----" + NL;
        Path svidKeyPemPath = tempDir.resolve("svid_key.pem");
        Files.write(svidKeyPemPath, keyPemContent.getBytes(StandardCharsets.US_ASCII));

        return new Fixture(tempDir, svidPemPath, svidKeyPemPath);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static void keytool(String... args) throws IOException, InterruptedException {
        String[] cmd = new String[args.length + 1];
        cmd[0] = keytoolPath();
        System.arraycopy(args, 0, cmd, 1, args.length);
        Process proc = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
        String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int rc = proc.waitFor();
        if (rc != 0) {
            throw new IOException("keytool failed (exit " + rc + "): " + output);
        }
    }

    private static String keytoolPath() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            // JDK 9+: java.home points to the JDK root
            java.io.File kt = new java.io.File(javaHome, "bin/keytool");
            if (kt.canExecute()) return kt.getAbsolutePath();
            // Try one level up (JDK 8 layout: jre/bin)
            kt = new java.io.File(javaHome, "../bin/keytool");
            if (kt.canExecute()) return kt.getAbsolutePath();
        }
        // Fall back to PATH
        return "keytool";
    }
}
