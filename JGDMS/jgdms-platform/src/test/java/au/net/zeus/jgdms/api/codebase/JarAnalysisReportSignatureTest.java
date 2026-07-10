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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Verifies that {@link JarAnalysisReport#canonicalBytes()} is the canonical DER
 * <em>to-be-signed</em> (TBS) content used by the analysis engine (signer) and
 * the {@link VerdictRegistry} (verifier), per JGDMS-STD-006 &sect;7.4 / &sect;7.4.1.
 *
 * <p>The engine signs {@code canonicalBytes()} and the registry verifies against
 * the same bytes, so these tests mirror that end-to-end: sign with a private key,
 * verify with the matching public key, and confirm that tampering any signed
 * field (content hash, a per-class verdict, a declared permission, or a codebase
 * URL) breaks the signature.
 */
public class JarAnalysisReportSignatureTest {

    private static final String SIG_ALG = "SHA256withRSA";
    private static final byte[] PLACEHOLDER = { 0 };

    private static KeyPair engineKeys;
    private static KeyPair wrongKeys;

    @BeforeClass
    public static void generateKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        engineKeys = kpg.generateKeyPair();
        wrongKeys  = kpg.generateKeyPair();
    }

    private static Map<String, ClassAnalysisResult> results(ClinitVerdict cv,
                                                            AtomicSerialVerdict av) {
        Map<String, ClassAnalysisResult> m = new LinkedHashMap<String, ClassAnalysisResult>();
        m.put("com/example/Foo", new ClassAnalysisResult(
                "com/example/Foo", cv, av, ConstrainableProxyVerdict.NA,
                Collections.<String>emptyList(), Collections.<String>emptyList()));
        m.put("com/example/Bar", new ClassAnalysisResult(
                "com/example/Bar", ClinitVerdict.CLEAN, AtomicSerialVerdict.COMPLIANT,
                ConstrainableProxyVerdict.NA,
                Collections.<String>emptyList(), Collections.<String>emptyList()));
        return m;
    }

    /** Signs a report's canonicalBytes(), returning a signed report. */
    private static JarAnalysisReport signed(String hash,
                                            Map<String, ClassAnalysisResult> res,
                                            String[] perms, String[] urls,
                                            PrivateKey key) throws Exception {
        JarAnalysisReport unsigned =
                new JarAnalysisReport(hash, res, PLACEHOLDER, perms, urls);
        Signature s = Signature.getInstance(SIG_ALG);
        s.initSign(key);
        s.update(unsigned.canonicalBytes());
        return new JarAnalysisReport(hash, res, s.sign(), perms, urls);
    }

    private static boolean verify(JarAnalysisReport r, PublicKey key) throws Exception {
        Signature s = Signature.getInstance(SIG_ALG);
        s.initVerify(key);
        s.update(r.canonicalBytes());
        return s.verify(r.getEngineSignature());
    }

    @Test
    public void canonicalBytes_isDerSequence() {
        JarAnalysisReport r = new JarAnalysisReport(
                "abc123", results(ClinitVerdict.CLEAN, AtomicSerialVerdict.COMPLIANT),
                PLACEHOLDER, new String[]{ "permission java.lang.RuntimePermission \"x\";" },
                new String[]{ "https://host/a.jar" });
        byte[] tbs = r.canonicalBytes();
        assertTrue("TBS must be non-empty", tbs.length > 2);
        assertEquals("TBS must be a DER SEQUENCE (tag 0x30)", 0x30, tbs[0] & 0xFF);
    }

    @Test
    public void canonicalBytes_isDeterministic() {
        JarAnalysisReport r = new JarAnalysisReport(
                "abc123", results(ClinitVerdict.CLEAN, AtomicSerialVerdict.COMPLIANT),
                PLACEHOLDER, new String[]{ "p1", "p2" },
                new String[]{ "https://host/a.jar" });
        assertArrayEquals("canonicalBytes must be deterministic",
                r.canonicalBytes(), r.canonicalBytes());
    }

    @Test
    public void genuineSignature_verifies() throws Exception {
        JarAnalysisReport r = signed(
                "abc123", results(ClinitVerdict.CLEAN, AtomicSerialVerdict.COMPLIANT),
                new String[]{ "permission java.io.FilePermission \"/x\", \"read\";" },
                new String[]{ "https://host/a.jar" }, engineKeys.getPrivate());
        assertTrue("engine signature must verify against the engine public key",
                verify(r, engineKeys.getPublic()));
    }

    @Test
    public void wrongKey_doesNotVerify() throws Exception {
        JarAnalysisReport r = signed(
                "abc123", results(ClinitVerdict.CLEAN, AtomicSerialVerdict.COMPLIANT),
                new String[0], new String[]{ "https://host/a.jar" },
                engineKeys.getPrivate());
        assertFalse("signature must not verify against an unrelated key",
                verify(r, wrongKeys.getPublic()));
    }

    @Test
    public void tamperedContentHash_doesNotVerify() throws Exception {
        Map<String, ClassAnalysisResult> res =
                results(ClinitVerdict.CLEAN, AtomicSerialVerdict.COMPLIANT);
        JarAnalysisReport signed = signed("abc123", res, new String[0],
                new String[]{ "https://host/a.jar" }, engineKeys.getPrivate());
        // Re-present the same signature over a different content hash.
        JarAnalysisReport forged = new JarAnalysisReport(
                "deadbeef", res, signed.getEngineSignature(), new String[0],
                new String[]{ "https://host/a.jar" });
        assertFalse("altering contentHash must invalidate the signature",
                verify(forged, engineKeys.getPublic()));
    }

    @Test
    public void tamperedClassVerdict_doesNotVerify() throws Exception {
        JarAnalysisReport signed = signed("abc123",
                results(ClinitVerdict.CLEAN, AtomicSerialVerdict.COMPLIANT),
                new String[0], new String[]{ "https://host/a.jar" },
                engineKeys.getPrivate());
        // A per-class verdict flipped to a dangerous one, same signature.
        JarAnalysisReport forged = new JarAnalysisReport("abc123",
                results(ClinitVerdict.BLOCKING, AtomicSerialVerdict.MISSING_CONSTRUCTOR),
                signed.getEngineSignature(), new String[0],
                new String[]{ "https://host/a.jar" });
        assertFalse("altering a per-class verdict must invalidate the signature",
                verify(forged, engineKeys.getPublic()));
    }

    @Test
    public void tamperedDeclaredPermission_doesNotVerify() throws Exception {
        Map<String, ClassAnalysisResult> res =
                results(ClinitVerdict.CLEAN, AtomicSerialVerdict.COMPLIANT);
        JarAnalysisReport signed = signed("abc123", res,
                new String[]{ "permission java.io.FilePermission \"/x\", \"read\";" },
                new String[]{ "https://host/a.jar" }, engineKeys.getPrivate());
        JarAnalysisReport forged = new JarAnalysisReport("abc123", res,
                signed.getEngineSignature(),
                new String[]{ "permission java.security.AllPermission;" },
                new String[]{ "https://host/a.jar" });
        assertFalse("altering a declared permission must invalidate the signature",
                verify(forged, engineKeys.getPublic()));
    }

    @Test
    public void tamperedCodebaseUrl_doesNotVerify() throws Exception {
        Map<String, ClassAnalysisResult> res =
                results(ClinitVerdict.CLEAN, AtomicSerialVerdict.COMPLIANT);
        JarAnalysisReport signed = signed("abc123", res, new String[0],
                new String[]{ "https://host/a.jar" }, engineKeys.getPrivate());
        JarAnalysisReport forged = new JarAnalysisReport("abc123", res,
                signed.getEngineSignature(), new String[0],
                new String[]{ "https://evil/a.jar" });
        assertFalse("altering a codebase URL must invalidate the signature",
                verify(forged, engineKeys.getPublic()));
    }
}
