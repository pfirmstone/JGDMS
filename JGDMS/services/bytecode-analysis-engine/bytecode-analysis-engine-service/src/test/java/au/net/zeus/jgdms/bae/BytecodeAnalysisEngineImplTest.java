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
package au.net.zeus.jgdms.bae;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import au.net.zeus.jgdms.api.codebase.AnalysisRequest;
import au.net.zeus.jgdms.api.codebase.JarAnalysisReport;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link BytecodeAnalysisEngineImpl}.
 *
 * <p>The engine supports only the push model: the caller supplies JAR bytes via
 * {@link BytecodeAnalysisEngineImpl#analyzeJar(AnalysisRequest)} and the engine
 * returns a signed {@link JarAnalysisReport} without any network access or
 * registry contact.  These tests cover:
 * <ul>
 *   <li>Constructor argument guards</li>
 *   <li>The push path ({@link BytecodeAnalysisEngineImpl#analyzeJar})</li>
 *   <li>Lifecycle ({@code shutdown}/{@code isShutdown})</li>
 * </ul>
 *
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public class BytecodeAnalysisEngineImplTest {

    private static final String SIG_ALGORITHM = "SHA256withRSA";

    private KeyPair engineKeyPair;

    @Before
    public void setUp() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        engineKeyPair = kpg.generateKeyPair();
    }

    // =========================================================================
    // Constructor guards
    // =========================================================================

    @Test(expected = NullPointerException.class)
    public void testConstructorNullPrivateKeyThrowsNPE() {
        new BytecodeAnalysisEngineImpl(null, SIG_ALGORITHM);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullSigAlgorithmThrowsNPE() {
        new BytecodeAnalysisEngineImpl(engineKeyPair.getPrivate(), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorEmptySigAlgorithmThrowsIAE() {
        new BytecodeAnalysisEngineImpl(engineKeyPair.getPrivate(), "");
    }

    // =========================================================================
    // Push model — analyzeJar
    // =========================================================================

    /**
     * The supported push path must analyse a benign in-memory JAR and return a
     * non-null signed report without any network access or registry contact.
     */
    @Test
    public void testAnalyzeJar_BenignJar_ReturnsReport() throws Exception {
        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM);
        byte[] jarBytes = buildJarWithClass("Safe.class",
                buildClassWithUtf8("Hello", "world"));
        JarAnalysisReport report = engine.analyzeJar(
                new AnalysisRequest(jarBytes, "hash-1", null));
        assertNotNull(report);
    }

    @Test(expected = NullPointerException.class)
    public void testAnalyzeJar_Null_ThrowsNPE() throws Exception {
        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM);
        engine.analyzeJar(null);
    }

    /**
     * After shutdown, {@code analyzeJar} must reject new requests.
     */
    @Test(expected = IllegalStateException.class)
    public void testAnalyzeJar_AfterShutdown_ThrowsIllegalState() throws Exception {
        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM);
        engine.shutdown();
        byte[] jarBytes = buildJarWithClass("Safe.class",
                buildClassWithUtf8("Hello"));
        engine.analyzeJar(new AnalysisRequest(jarBytes, "hash-2", null));
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Test
    public void testShutdown_IsShutdown_ReturnsTrue() {
        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM);
        assertFalse(engine.isShutdown());
        engine.shutdown();
        assertTrue(engine.isShutdown());
    }

    @Test
    public void testShutdown_DoubleShutdown_Idempotent() {
        BytecodeAnalysisEngineImpl engine = new BytecodeAnalysisEngineImpl(
                engineKeyPair.getPrivate(), SIG_ALGORITHM);
        engine.shutdown();
        engine.shutdown();
        assertTrue(engine.isShutdown());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Builds a minimal synthetic class file with the supplied
     * {@code CONSTANT_Utf8} entries in the constant pool (no method table).
     */
    private static byte[] buildClassWithUtf8(String... entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(0xCAFEBABE);
        dos.writeShort(0);
        dos.writeShort(52);
        dos.writeShort(entries.length + 1); // cp_count: one-based
        for (String entry : entries) {
            byte[] utf8 = entry.getBytes(StandardCharsets.UTF_8);
            dos.writeByte(1); // CONSTANT_Utf8 tag
            dos.writeShort(utf8.length);
            dos.write(utf8);
        }
        dos.flush();
        return baos.toByteArray();
    }

    /** Wraps a single {@code .class} file in an in-memory JAR. */
    private static byte[] buildJarWithClass(String entryName, byte[] classBytes)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JarOutputStream jos = new JarOutputStream(baos)) {
            jos.putNextEntry(new JarEntry(entryName));
            jos.write(classBytes);
            jos.closeEntry();
        }
        return baos.toByteArray();
    }
}
