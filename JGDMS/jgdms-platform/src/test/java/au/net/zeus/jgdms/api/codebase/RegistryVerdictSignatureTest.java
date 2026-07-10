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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.Comparator;
import org.apache.river.api.net.Uri;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Verifies the forge-proof inline-signature check on {@link RegistryVerdict}
 * ({@link RegistryVerdict#verifySignature(PublicKey, String)}).
 *
 * <p>The canonical bytes produced here MUST match, byte-for-byte, the format
 * signed by {@code VerdictRegistryImpl} in the verdict-registry service; if this
 * test's {@link #canonicalBytes} and the production
 * {@code canonicalBytesForRegistryVerdict} ever diverge, a genuine signature
 * would fail to verify — which is exactly what this test guards against.
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

    /** Reproduces the registry's authoritative canonical signing format. */
    private static byte[] canonicalBytes(Uri[] urls, VerdictType type, long ts)
            throws IOException {
        Uri[] sorted = urls.clone();
        Arrays.sort(sorted, new Comparator<Uri>() {
            public int compare(Uri a, Uri b) {
                return a.toString().compareTo(b.toString());
            }
        });
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream      dos  = new DataOutputStream(baos);
        for (Uri uri : sorted) {
            byte[] b = uri.toString().getBytes(StandardCharsets.UTF_8);
            dos.writeInt(b.length);
            dos.write(b);
        }
        dos.writeInt(type.ordinal());
        dos.writeLong(ts);
        dos.flush();
        return baos.toByteArray();
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
    public void urlOrderIndependent_verifies() throws Exception {
        // Signature is computed over the SORTED url order; presenting the URLs
        // in a different construction order must still verify because
        // verifySignature() re-sorts.
        Uri a = new Uri("https://host/a.jar");
        Uri b = new Uri("https://host/b.jar");
        long ts = System.currentTimeMillis();
        byte[] sig = sign(registryKeys.getPrivate(),
                canonicalBytes(new Uri[]{ a, b }, VerdictType.SAFE, ts));

        RegistryVerdict reordered =
                new RegistryVerdict(new Uri[]{ b, a }, VerdictType.SAFE, ts, sig);

        assertTrue("verification must be independent of stored URL order",
                reordered.verifySignature(registryKeys.getPublic(), SIG_ALG));
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
