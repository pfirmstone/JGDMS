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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.apache.river.api.io.AtomicSerial.SerialForm;
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

    /**
     * Pins the TBS layout of JGDMS-STD-006 &sect;7.4 (RL-JGDMS-0197): every
     * carried {@link ClassAnalysisResult} field is signed, enums by constant
     * name.  The expected octets are hand-encoded DER, not produced by
     * {@code DerTbs}, so a layout drift in either the code or the table fails here.
     */
    @Test
    public void canonicalBytes_goldenLayout_signsEveryCarriedField() {
        Map<String, ClassAnalysisResult> m = new LinkedHashMap<String, ClassAnalysisResult>();
        m.put("A", new ClassAnalysisResult("A", ClinitVerdict.CLEAN,
                AtomicSerialVerdict.COMPLIANT, ConstrainableProxyVerdict.JAVA_SERIALIZATION,
                Collections.singletonList("p"), Collections.singletonList("q")));
        JarAnalysisReport r = new JarAnalysisReport("h", m, PLACEHOLDER,
                new String[0], new String[0]);
        byte[] expected = hex(
                "30 3E",                                    // TBS SEQUENCE
                "0C 01 68",                                 //   contentHash "h"
                "30 35",                                    //   SEQUENCE OF per-class
                "30 33",                                    //     per-class SEQUENCE
                "0C 01 41",                                 //       className "A"
                "0C 05 43 4C 45 41 4E",                     //       "CLEAN"
                "0C 09 43 4F 4D 50 4C 49 41 4E 54",         //       "COMPLIANT"
                "0C 12 4A 41 56 41 5F 53 45 52 49 41 4C 49 5A 41 54 49 4F 4E",
                                                            //       "JAVA_SERIALIZATION"
                "30 03 0C 01 70",                           //       blockingCallPath ["p"]
                "30 03 0C 01 71",                           //       cycleParticipants ["q"]
                "30 00",                                    //   declaredPermissions []
                "30 00");                                   //   codebaseUrls []
        assertArrayEquals(expected, r.canonicalBytes());
    }

    /**
     * The map key is the signed className; a {@link ClassAnalysisResult} that
     * names a different class would carry an unsigned className, so the report
     * refuses it.
     */
    @Test(expected = IllegalArgumentException.class)
    public void classNameDifferentFromKey_isRejected() {
        Map<String, ClassAnalysisResult> m = new LinkedHashMap<String, ClassAnalysisResult>();
        m.put("com/example/Foo", new ClassAnalysisResult("com/example/Evil",
                ClinitVerdict.CLEAN, AtomicSerialVerdict.COMPLIANT,
                ConstrainableProxyVerdict.NA,
                Collections.<String>emptyList(), Collections.<String>emptyList()));
        new JarAnalysisReport("abc123", m, PLACEHOLDER, new String[0], new String[0]);
    }

    private static ClassAnalysisResult car(String name, ClinitVerdict cv,
            AtomicSerialVerdict av, ConstrainableProxyVerdict pv,
            String path, String cycle) {
        return new ClassAnalysisResult(name, cv, av, pv,
                Collections.singletonList(path), Collections.singletonList(cycle));
    }

    /** TBS of a one-class report; the arguments are the report's fields. */
    private static byte[] tbs(String hash, ClassAnalysisResult cr, byte[] signature,
                              String permission, String url) {
        Map<String, ClassAnalysisResult> m = new LinkedHashMap<String, ClassAnalysisResult>();
        m.put(cr.getClassName(), cr);
        return new JarAnalysisReport(hash, m, signature,
                new String[]{ permission }, new String[]{ url }).canonicalBytes();
    }

    /**
     * Every field named in {@code JarAnalysisReport.serialForm()} and in
     * {@code ClassAnalysisResult.serialForm()} must change
     * {@link JarAnalysisReport#canonicalBytes()} when it changes; only
     * {@code engineSignature} may not (JGDMS-STD-006 &sect;7.4, RL-JGDMS-0197).
     * A field added to either form with no variant below fails here, and a
     * field with a variant but missing from the TBS fails here.
     */
    @Test
    public void everySerialFormField_exceptTheSignature_isInTheTbs() {
        ClassAnalysisResult a = car("A", ClinitVerdict.CLEAN, AtomicSerialVerdict.COMPLIANT,
                ConstrainableProxyVerdict.NA, "p", "q");
        byte[] base = tbs("h", a, PLACEHOLDER, "x", "u");

        // The TBS takes the class name from classNames[i], and a result whose
        // className differs from its key is refused, so className and
        // classNames can only be varied together.
        ClassAnalysisResult b = car("B", ClinitVerdict.CLEAN, AtomicSerialVerdict.COMPLIANT,
                ConstrainableProxyVerdict.NA, "p", "q");

        Map<String, byte[]> report = new LinkedHashMap<String, byte[]>();
        report.put("contentHash",         tbs("h2", a, PLACEHOLDER, "x", "u"));
        report.put("classNames",          tbs("h", b, PLACEHOLDER, "x", "u"));
        report.put("classResults",        tbs("h", car("A", ClinitVerdict.BLOCKING,
                AtomicSerialVerdict.COMPLIANT, ConstrainableProxyVerdict.NA, "p", "q"),
                PLACEHOLDER, "x", "u"));
        report.put("engineSignature",     tbs("h", a, new byte[] { 1, 2, 3 }, "x", "u"));
        report.put("declaredPermissions", tbs("h", a, PLACEHOLDER, "x2", "u"));
        report.put("codebaseUrls",        tbs("h", a, PLACEHOLDER, "x", "u2"));

        Map<String, byte[]> perClass = new LinkedHashMap<String, byte[]>();
        perClass.put("className",         tbs("h", b, PLACEHOLDER, "x", "u"));
        perClass.put("clinitVerdict",     tbs("h", car("A", ClinitVerdict.BLOCKING,
                AtomicSerialVerdict.COMPLIANT, ConstrainableProxyVerdict.NA, "p", "q"),
                PLACEHOLDER, "x", "u"));
        perClass.put("atomicVerdict",     tbs("h", car("A", ClinitVerdict.CLEAN,
                AtomicSerialVerdict.MISSING_CONSTRUCTOR, ConstrainableProxyVerdict.NA, "p", "q"),
                PLACEHOLDER, "x", "u"));
        perClass.put("constrainableProxyVerdict", tbs("h", car("A", ClinitVerdict.CLEAN,
                AtomicSerialVerdict.COMPLIANT, ConstrainableProxyVerdict.JAVA_SERIALIZATION,
                "p", "q"), PLACEHOLDER, "x", "u"));
        perClass.put("blockingCallPath",  tbs("h", car("A", ClinitVerdict.CLEAN,
                AtomicSerialVerdict.COMPLIANT, ConstrainableProxyVerdict.NA, "p2", "q"),
                PLACEHOLDER, "x", "u"));
        perClass.put("cycleParticipants", tbs("h", car("A", ClinitVerdict.CLEAN,
                AtomicSerialVerdict.COMPLIANT, ConstrainableProxyVerdict.NA, "p", "q2"),
                PLACEHOLDER, "x", "u"));

        assertCovered("JarAnalysisReport", JarAnalysisReport.serialForm(), report, base);
        assertCovered("ClassAnalysisResult", ClassAnalysisResult.serialForm(), perClass, base);
    }

    private static void assertCovered(String record, SerialForm[] form,
                                      Map<String, byte[]> variants, byte[] base) {
        Set<String> declared = new HashSet<String>();
        for (SerialForm field : form) {
            String name = field.getName();
            declared.add(name);
            byte[] variant = variants.get(name);
            assertNotNull(new StringBuilder().append(record).append(".serialForm() field ")
                    .append(name).append(" has no variant in this test: add one, and sign the field")
                    .toString(), variant);
            if ("engineSignature".equals(name)) {
                assertArrayEquals("the signature must not be part of its own TBS",
                        base, variant);
            } else {
                assertFalse(new StringBuilder().append(record).append('.').append(name)
                        .append(" is in serialForm() but changing it leaves the signed TBS unchanged")
                        .toString(), Arrays.equals(base, variant));
            }
        }
        assertEquals(new StringBuilder().append(record)
                .append(": a variant names a field serialForm() no longer has").toString(),
                declared, variants.keySet());
    }

    /** Parses groups of space-separated hex octets, in order. */
    static byte[] hex(String... groups) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (String group : groups) {
            for (String octet : group.trim().split("\\s+")) {
                out.write(Integer.parseInt(octet, 16));
            }
        }
        return out.toByteArray();
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
