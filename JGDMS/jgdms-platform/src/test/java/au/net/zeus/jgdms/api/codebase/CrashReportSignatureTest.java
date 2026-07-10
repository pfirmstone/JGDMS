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
package au.net.zeus.jgdms.api.codebase;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import org.apache.river.api.net.Uri;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Verifies the sign-here / verify-there round trip for {@link CrashReport}'s
 * canonical DER <em>to-be-signed</em> content
 * ({@link CrashReport#signedContent(String[], int, long, String)} /
 * {@link CrashReport#signedContent()}).
 *
 * <p>A {@code CrashReport} is signed by a Phoenix crash reporter over the
 * single-source-of-truth canonical DER TBS (STD-006 &sect;7.4 / &sect;7.4.1) and
 * verified by the {@code VerdictRegistry} — which reconstructs the same bytes
 * from the received fields via {@code CrashReport.signedContent()}.  A genuine
 * signature therefore verifies, and any field tamper (exit code, incarnation,
 * stderr, a codebase URL, or URL <em>ordering</em>) changes the DER octets and
 * breaks verification.  This mirrors {@link RegistryVerdictSignatureTest} and
 * closes the CrashReport signature-round-trip coverage gap.
 */
public class CrashReportSignatureTest {

    private static final String SIG_ALG = "SHA256withRSA";

    private static KeyPair phoenixKeys;
    private static KeyPair wrongKeys;

    @BeforeClass
    public static void generateKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        phoenixKeys = kpg.generateKeyPair();
        wrongKeys   = kpg.generateKeyPair();
    }

    /** The Phoenix reporter's authoritative canonical DER TBS (order-significant). */
    private static byte[] canonicalBytes(Uri[] urls, int exitCode,
                                         long incarnation, String stderr) {
        String[] urlStrings = new String[urls.length];
        for (int i = 0; i < urls.length; i++) {
            urlStrings[i] = urls[i].toString();
        }
        return CrashReport.signedContent(urlStrings, exitCode, incarnation, stderr);
    }

    private static byte[] sign(PrivateKey key, byte[] data) throws Exception {
        Signature sig = Signature.getInstance(SIG_ALG);
        sig.initSign(key);
        sig.update(data);
        return sig.sign();
    }

    /**
     * Verifies {@code report}'s inline signature against {@code key} by
     * reconstructing the canonical DER TBS from the report's own stored fields
     * ({@link CrashReport#signedContent()}) — exactly as the registry does.
     * Fail-closed: any cryptographic failure yields {@code false}.
     */
    private static boolean verify(CrashReport report, PublicKey key) {
        try {
            Signature sig = Signature.getInstance(SIG_ALG);
            sig.initVerify(key);
            sig.update(report.signedContent());
            return sig.verify(report.getSignature());
        } catch (Exception e) {
            return false;
        }
    }

    private static CrashReport signedReport(Uri[] urls, int exitCode,
                                            long incarnation, String stderr,
                                            PrivateKey signer) throws Exception {
        byte[] signature = sign(signer, canonicalBytes(urls, exitCode, incarnation, stderr));
        return new CrashReport(urls, exitCode, incarnation, stderr, signature);
    }

    @Test
    public void genuineSignature_verifies() throws Exception {
        Uri[] urls = { new Uri("urn:sha256:abc123"), new Uri("https://host/a.jar") };
        CrashReport cr = signedReport(urls, 134, 7L, "SIGSEGV in group",
                phoenixKeys.getPrivate());

        assertTrue("genuine Phoenix signature must verify against the Phoenix public key",
                verify(cr, phoenixKeys.getPublic()));
    }

    @Test
    public void wrongKey_doesNotVerify() throws Exception {
        Uri[] urls = { new Uri("https://host/a.jar") };
        CrashReport cr = signedReport(urls, 1, 0L, "crash", phoenixKeys.getPrivate());

        assertFalse("signature must NOT verify against an unrelated public key",
                verify(cr, wrongKeys.getPublic()));
    }

    @Test
    public void tamperedExitCode_doesNotVerify() throws Exception {
        Uri[] urls = { new Uri("https://host/a.jar") };
        // Sign over exitCode=1, then present a report claiming exitCode=139.
        byte[] sig = sign(phoenixKeys.getPrivate(),
                canonicalBytes(urls, 1, 0L, "crash"));
        CrashReport forged = new CrashReport(urls, 139, 0L, "crash", sig);

        assertFalse("altering the exit code must invalidate the signature",
                verify(forged, phoenixKeys.getPublic()));
    }

    @Test
    public void tamperedIncarnation_doesNotVerify() throws Exception {
        Uri[] urls = { new Uri("https://host/a.jar") };
        byte[] sig = sign(phoenixKeys.getPrivate(),
                canonicalBytes(urls, 1, 3L, "crash"));
        CrashReport forged = new CrashReport(urls, 1, 4L, "crash", sig);

        assertFalse("altering the incarnation must invalidate the signature",
                verify(forged, phoenixKeys.getPublic()));
    }

    @Test
    public void tamperedStderr_doesNotVerify() throws Exception {
        Uri[] urls = { new Uri("https://host/a.jar") };
        byte[] sig = sign(phoenixKeys.getPrivate(),
                canonicalBytes(urls, 1, 0L, "benign message"));
        CrashReport forged = new CrashReport(urls, 1, 0L, "attacker text", sig);

        assertFalse("altering the stderr summary must invalidate the signature",
                verify(forged, phoenixKeys.getPublic()));
    }

    @Test
    public void tamperedUrl_doesNotVerify() throws Exception {
        Uri[] signedUrls = { new Uri("https://host/a.jar") };
        byte[] sig = sign(phoenixKeys.getPrivate(),
                canonicalBytes(signedUrls, 1, 0L, "crash"));
        Uri[] forgedUrls = { new Uri("https://host/b.jar") };
        CrashReport forged = new CrashReport(forgedUrls, 1, 0L, "crash", sig);

        assertFalse("altering a codebase URL must invalidate the signature",
                verify(forged, phoenixKeys.getPublic()));
    }

    @Test
    public void urlOrderSignificant_reorderingDoesNotVerify() throws Exception {
        // STD-006 §7.4.1: the TBS is the canonical DER of codebaseUrls as a
        // SEQUENCE OF (order-preserving).  A report signed over one URL order and
        // presented in a different construction order must NOT verify — the DER
        // bytes differ, so this is tamper detection, not a false match.
        Uri a = new Uri("https://host/a.jar");
        Uri b = new Uri("https://host/b.jar");
        byte[] sig = sign(phoenixKeys.getPrivate(),
                canonicalBytes(new Uri[]{ a, b }, 1, 0L, "crash"));

        CrashReport reordered = new CrashReport(new Uri[]{ b, a }, 1, 0L, "crash", sig);
        assertFalse("reordering the codebase URLs must invalidate the signature",
                verify(reordered, phoenixKeys.getPublic()));

        // Sanity: the SAME order that was signed still verifies.
        CrashReport inOrder = new CrashReport(new Uri[]{ a, b }, 1, 0L, "crash", sig);
        assertTrue("the signed URL order must verify",
                verify(inOrder, phoenixKeys.getPublic()));
    }

    @Test
    public void garbageSignature_failsClosed() throws Exception {
        Uri[] urls = { new Uri("https://host/a.jar") };
        CrashReport cr = new CrashReport(urls, 1, 0L, "crash", new byte[]{ 9, 9, 9, 9 });

        assertFalse("a garbage signature must fail closed (no exception)",
                verify(cr, phoenixKeys.getPublic()));
    }

    @Test
    public void signedContent_isDeterministic() throws Exception {
        Uri[] urls = { new Uri("https://host/a.jar"), new Uri("urn:sha256:deadbeef") };
        CrashReport cr = signedReport(urls, 42, 9L, "boom", phoenixKeys.getPrivate());

        assertArrayEquals("signedContent() must be deterministic for identical fields",
                cr.signedContent(), cr.signedContent());
        assertArrayEquals("instance signedContent() must equal the static TBS over the same fields",
                canonicalBytes(urls, 42, 9L, "boom"), cr.signedContent());
    }
}
