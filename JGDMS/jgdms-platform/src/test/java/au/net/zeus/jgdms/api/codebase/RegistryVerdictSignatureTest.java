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
 * Verifies the forge-proof inline-signature check on {@link RegistryVerdict}
 * ({@link RegistryVerdict#verifySignature(PublicKey, String)}).
 *
 * <p>Signing here uses the single-source-of-truth canonical DER TBS
 * ({@link RegistryVerdict#signedContent(String[], VerdictType, long)}), the same
 * bytes the {@code VerdictRegistryImpl} signer and every verifier use, so a
 * genuine registry signature verifies and any field tamper (including URL
 * reordering) breaks it — which is exactly what these tests guard (STD-006
 * &sect;7.4).
 */
public class RegistryVerdictSignatureTest {

    private static final String SIG_ALG = "SHA256withRSA";

    private static KeyPair registryKeys;
    private static KeyPair wrongKeys;

    @BeforeClass
    public static void generateKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        registryKeys = kpg.generateKeyPair();
        wrongKeys    = kpg.generateKeyPair();
    }

    /** The registry's authoritative canonical DER TBS (order-significant). */
    private static byte[] canonicalBytes(Uri[] urls, VerdictType type, long ts) {
        String[] urlStrings = new String[urls.length];
        for (int i = 0; i < urls.length; i++) {
            urlStrings[i] = urls[i].toString();
        }
        return RegistryVerdict.signedContent(urlStrings, type, ts);
    }

    private static byte[] sign(PrivateKey key, byte[] data) throws Exception {
        Signature sig = Signature.getInstance(SIG_ALG);
        sig.initSign(key);
        sig.update(data);
        return sig.sign();
    }

    private static RegistryVerdict signedVerdict(Uri[] urls, VerdictType type,
                                                 long ts, PrivateKey signer)
            throws Exception {
        byte[] signature = sign(signer, canonicalBytes(urls, type, ts));
        return new RegistryVerdict(urls, type, ts, signature);
    }

    @Test
    public void genuineSignature_verifies() throws Exception {
        Uri[] urls = { new Uri("urn:sha256:abc123"), new Uri("https://host/a.jar") };
        long ts = System.currentTimeMillis();
        RegistryVerdict rv = signedVerdict(urls, VerdictType.SAFE, ts,
                registryKeys.getPrivate());

        assertTrue("genuine signature must verify against the registry public key",
                rv.verifySignature(registryKeys.getPublic(), SIG_ALG));
    }

    @Test
    public void wrongKey_doesNotVerify() throws Exception {
        Uri[] urls = { new Uri("https://host/a.jar") };
        long ts = System.currentTimeMillis();
        RegistryVerdict rv = signedVerdict(urls, VerdictType.SAFE, ts,
                registryKeys.getPrivate());

        assertFalse("signature must NOT verify against an unrelated public key",
                rv.verifySignature(wrongKeys.getPublic(), SIG_ALG));
    }

    @Test
    public void tamperedVerdictType_doesNotVerify() throws Exception {
        // Sign as DANGEROUS, then present a verdict object that claims SAFE with
        // the DANGEROUS signature — the classic "flip the verdict" forgery.
        Uri[] urls = { new Uri("https://host/evil.jar") };
        long ts = System.currentTimeMillis();
        byte[] dangerousSig = sign(registryKeys.getPrivate(),
                canonicalBytes(urls, VerdictType.DANGEROUS, ts));
        RegistryVerdict forged =
                new RegistryVerdict(urls, VerdictType.SAFE, ts, dangerousSig);

        assertFalse("a SAFE verdict bearing a DANGEROUS signature must be rejected",
                forged.verifySignature(registryKeys.getPublic(), SIG_ALG));
    }

    @Test
    public void tamperedTimestamp_doesNotVerify() throws Exception {
        Uri[] urls = { new Uri("https://host/a.jar") };
        long ts = System.currentTimeMillis();
        byte[] sig = sign(registryKeys.getPrivate(),
                canonicalBytes(urls, VerdictType.SAFE, ts));
        RegistryVerdict forged =
                new RegistryVerdict(urls, VerdictType.SAFE, ts + 1000L, sig);

        assertFalse("altering the timestamp must invalidate the signature",
                forged.verifySignature(registryKeys.getPublic(), SIG_ALG));
    }

    @Test
    public void tamperedUrl_doesNotVerify() throws Exception {
        Uri[] signedUrls = { new Uri("https://host/a.jar") };
        long ts = System.currentTimeMillis();
        byte[] sig = sign(registryKeys.getPrivate(),
                canonicalBytes(signedUrls, VerdictType.SAFE, ts));
        // Present a DIFFERENT url set with the signature over the original.
        Uri[] forgedUrls = { new Uri("https://host/b.jar") };
        RegistryVerdict forged =
                new RegistryVerdict(forgedUrls, VerdictType.SAFE, ts, sig);

        assertFalse("altering a codebase URL must invalidate the signature",
                forged.verifySignature(registryKeys.getPublic(), SIG_ALG));
    }

    @Test
    public void garbageSignature_failsClosed() throws Exception {
        Uri[] urls = { new Uri("https://host/a.jar") };
        RegistryVerdict rv = new RegistryVerdict(urls, VerdictType.SAFE,
                System.currentTimeMillis(), new byte[]{ 9, 9, 9, 9 });

        assertFalse("a garbage signature must fail closed (no exception)",
                rv.verifySignature(registryKeys.getPublic(), SIG_ALG));
    }

    @Test
    public void urlOrderSignificant_reorderingDoesNotVerify() throws Exception {
        // STD-006 §7.4.1: the TBS is the canonical DER of codebaseUrls as a
        // SEQUENCE OF (order-preserving).  A verdict signed over one URL order
        // and presented in a different construction order must NOT verify —
        // the DER bytes differ, so this is tamper detection, not a false match.
        Uri a = new Uri("https://host/a.jar");
        Uri b = new Uri("https://host/b.jar");
        long ts = System.currentTimeMillis();
        byte[] sig = sign(registryKeys.getPrivate(),
                canonicalBytes(new Uri[]{ a, b }, VerdictType.SAFE, ts));

        RegistryVerdict reordered =
                new RegistryVerdict(new Uri[]{ b, a }, VerdictType.SAFE, ts, sig);

        assertFalse("reordering the codebase URLs must invalidate the signature",
                reordered.verifySignature(registryKeys.getPublic(), SIG_ALG));

        // Sanity: the SAME order that was signed still verifies.
        RegistryVerdict inOrder =
                new RegistryVerdict(new Uri[]{ a, b }, VerdictType.SAFE, ts, sig);
        assertTrue("the signed URL order must verify",
                inOrder.verifySignature(registryKeys.getPublic(), SIG_ALG));
    }

    @Test(expected = NullPointerException.class)
    public void nullKey_throwsNpe() throws Exception {
        RegistryVerdict rv = signedVerdict(
                new Uri[]{ new Uri("https://host/a.jar") },
                VerdictType.SAFE, System.currentTimeMillis(),
                registryKeys.getPrivate());
        rv.verifySignature(null, SIG_ALG);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyAlgorithm_throwsIae() throws Exception {
        RegistryVerdict rv = signedVerdict(
                new Uri[]{ new Uri("https://host/a.jar") },
                VerdictType.SAFE, System.currentTimeMillis(),
                registryKeys.getPrivate());
        rv.verifySignature(registryKeys.getPublic(), "");
    }
}
