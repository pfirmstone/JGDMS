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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import au.net.zeus.jgdms.api.codebase.AnalysisException;
import au.net.zeus.jgdms.api.codebase.AnalysisRequest;
import au.net.zeus.jgdms.api.codebase.AtomicSerialVerdict;
import au.net.zeus.jgdms.api.codebase.ClassAnalysisResult;
import au.net.zeus.jgdms.api.codebase.ClinitVerdict;
import au.net.zeus.jgdms.api.codebase.ConstrainableProxyVerdict;
import au.net.zeus.jgdms.api.codebase.JarAnalysisReport;
import au.net.zeus.jgdms.api.codebase.VerdictType;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link JarAnalyzer}, focusing on the
 * {@code META-INF/PERMISSIONS.LIST} parsing feature.
 *
 * <p>Each test constructs a minimal in-memory JAR (using {@link JarOutputStream}
 * with ASM-generated class bytes) and verifies that
 * {@link JarAnalysisReport#getDeclaredPermissions()} returns the expected
 * content.
 *
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public class JarAnalyzerTest {

    private static final String SIG_ALGORITHM = "SHA256withRSA";
    private static final String HASH = "abc123";

    private JarAnalyzer analyzer;

    @Before
    public void setUp() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        KeyPair kp = kpg.generateKeyPair();
        analyzer = new JarAnalyzer(kp.getPrivate(), SIG_ALGORITHM);
    }

    // =========================================================================
    // Constructor guards
    // =========================================================================

    @Test(expected = NullPointerException.class)
    public void testConstructorNullPrivateKeyThrowsNPE() {
        new JarAnalyzer(null, SIG_ALGORITHM);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullAlgorithmThrowsNPE() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        new JarAnalyzer(kpg.generateKeyPair().getPrivate(), null);
    }

    // =========================================================================
    // PERMISSIONS.LIST — absent
    // =========================================================================

    /**
     * When the JAR contains no {@code META-INF/PERMISSIONS.LIST} entry, the
     * report's {@link JarAnalysisReport#getDeclaredPermissions()} must return
     * an empty array.
     */
    @Test
    public void testNoDeclaredPermissions_WhenPermissionsListAbsent()
            throws IOException, AnalysisException {
        byte[] jarBytes = buildJar(minimalClassBytes(), null);
        JarAnalysisReport report = analyzer.analyze(
                new AnalysisRequest(jarBytes, HASH, null));
        assertNotNull(report.getDeclaredPermissions());
        assertEquals(0, report.getDeclaredPermissions().length);
    }

    // =========================================================================
    // PERMISSIONS.LIST — present with permission lines
    // =========================================================================

    /**
     * When the JAR contains a {@code META-INF/PERMISSIONS.LIST} with two
     * permission declarations, both must appear in
     * {@link JarAnalysisReport#getDeclaredPermissions()}.
     */
    @Test
    public void testDeclaredPermissions_TwoLines()
            throws IOException, AnalysisException {
        String content =
                "permission java.awt.AWTPermission \"showWindowWithoutWarningBanner\";\n"
                + "permission java.net.SocketPermission \"db.internal:5432\", \"connect\";\n";
        byte[] jarBytes = buildJar(minimalClassBytes(), content);
        JarAnalysisReport report = analyzer.analyze(
                new AnalysisRequest(jarBytes, HASH, null));
        String[] perms = report.getDeclaredPermissions();
        assertEquals(2, perms.length);
        assertEquals(
                "permission java.awt.AWTPermission \"showWindowWithoutWarningBanner\";",
                perms[0]);
        assertEquals(
                "permission java.net.SocketPermission \"db.internal:5432\", \"connect\";",
                perms[1]);
    }

    // =========================================================================
    // PERMISSIONS.LIST — blank lines and comments filtered
    // =========================================================================

    /**
     * Blank lines and lines starting with {@code #} must be silently
     * discarded; only non-blank, non-comment lines are returned.
     */
    @Test
    public void testDeclaredPermissions_BlankAndCommentLinesFiltered()
            throws IOException, AnalysisException {
        String content =
                "# This is a comment\n"
                + "\n"
                + "   \n"
                + "permission javax.security.auth.AuthPermission \"getSubject\";\n"
                + "# another comment\n"
                + "permission java.lang.RuntimePermission \"createVirtualThread\";\n";
        byte[] jarBytes = buildJar(minimalClassBytes(), content);
        JarAnalysisReport report = analyzer.analyze(
                new AnalysisRequest(jarBytes, HASH, null));
        String[] perms = report.getDeclaredPermissions();
        assertEquals(2, perms.length);
        assertEquals(
                "permission javax.security.auth.AuthPermission \"getSubject\";",
                perms[0]);
        assertEquals(
                "permission java.lang.RuntimePermission \"createVirtualThread\";",
                perms[1]);
    }

    // =========================================================================
    // PERMISSIONS.LIST — empty file
    // =========================================================================

    /**
     * A {@code META-INF/PERMISSIONS.LIST} that contains only blank lines and
     * comments must yield an empty {@code declaredPermissions} array — as if
     * the file were absent.
     */
    @Test
    public void testDeclaredPermissions_EmptyPermissionsListFile()
            throws IOException, AnalysisException {
        String content = "# empty\n\n";
        byte[] jarBytes = buildJar(minimalClassBytes(), content);
        JarAnalysisReport report = analyzer.analyze(
                new AnalysisRequest(jarBytes, HASH, null));
        assertEquals(0, report.getDeclaredPermissions().length);
    }

    // =========================================================================
    // PERMISSIONS.LIST — verdict unaffected
    // =========================================================================

    /**
     * The presence of {@code META-INF/PERMISSIONS.LIST} must not change the
     * {@link VerdictType} derived from the class analysis — it is purely
     * informational.  A JAR with only a clean class should still be
     * {@link VerdictType#SAFE} regardless of what the PERMISSIONS.LIST contains.
     */
    @Test
    public void testDeclaredPermissions_DoesNotAffectVerdict()
            throws IOException, AnalysisException {
        String content =
                "permission java.awt.AWTPermission \"showWindowWithoutWarningBanner\";\n";
        byte[] jarBytes = buildJar(minimalClassBytes(), content);
        JarAnalysisReport report = analyzer.analyze(
                new AnalysisRequest(jarBytes, HASH, null));
        assertEquals(VerdictType.SAFE, report.deriveVerdictType());
        assertEquals(1, report.getDeclaredPermissions().length);
    }

    // =========================================================================
    // PERMISSIONS.LIST — raw network connection without a constrainable proxy
    // =========================================================================

    /**
     * A JAR whose {@code PERMISSIONS.LIST} requests a raw
     * {@link java.net.SocketPermission} but contains no constrainable proxy is
     * talking to the network outside JERI.  It is not proof of malice (a trusted
     * codebase might legitimately do this), so the verdict is not
     * {@link VerdictType#DANGEROUS} — but it is not confirmed safe either, so it
     * must be {@link VerdictType#INCONCLUSIVE}.
     */
    @Test
    public void testSocketPermission_NoConstrainableProxy_isInconclusive()
            throws IOException, AnalysisException {
        String content =
                "permission java.net.SocketPermission \"host:1234\", \"connect\";\n";
        byte[] jarBytes = buildJar(minimalClassBytes(), content);
        JarAnalysisReport report = analyzer.analyze(
                new AnalysisRequest(jarBytes, HASH, null));
        assertEquals(VerdictType.INCONCLUSIVE, report.deriveVerdictType());
    }

    /**
     * A {@link java.net.SocketPermission} request stays
     * {@link VerdictType#INCONCLUSIVE} <em>even when</em> the JAR contains a
     * constrainable proxy ({@link ConstrainableProxyVerdict#COMPLIANT}): JERI
     * normally owns socket access under {@code doPrivileged}, so a proper proxy
     * would leave the transport to JERI and not need the permission itself — the
     * request is anomalous regardless of the proxy.  Verified directly against
     * {@link JarAnalysisReport#deriveVerdictType()}.
     */
    @Test
    public void testSocketPermission_EvenWithConstrainableProxy_isInconclusive() {
        Map<String, ClassAnalysisResult> results =
                new LinkedHashMap<String, ClassAnalysisResult>();
        results.put("p/GoodProxy", new ClassAnalysisResult(
                "p/GoodProxy",
                ClinitVerdict.CLEAN,
                AtomicSerialVerdict.COMPLIANT,
                ConstrainableProxyVerdict.COMPLIANT,
                Collections.<String>emptyList(),
                Collections.<String>emptyList()));
        JarAnalysisReport report = new JarAnalysisReport(
                HASH, results, new byte[]{ 0 },
                new String[]{ "permission java.net.SocketPermission \"*\", \"connect\";" },
                new String[0]);
        assertEquals(VerdictType.INCONCLUSIVE, report.deriveVerdictType());
    }

    /**
     * A smart proxy that uses java.io serialization
     * ({@link ConstrainableProxyVerdict#JAVA_SERIALIZATION}) escalates the whole
     * report to {@link VerdictType#DANGEROUS} — an unvalidated deserialization
     * path with no legitimate use, stronger than the raw-network signal
     * (INCONCLUSIVE).  Verified directly against
     * {@link JarAnalysisReport#deriveVerdictType()}.
     */
    @Test
    public void testJavaSerializationProxy_isDangerous() {
        Map<String, ClassAnalysisResult> results =
                new LinkedHashMap<String, ClassAnalysisResult>();
        results.put("p/SerialProxy", new ClassAnalysisResult(
                "p/SerialProxy",
                ClinitVerdict.CLEAN,
                AtomicSerialVerdict.COMPLIANT,
                ConstrainableProxyVerdict.JAVA_SERIALIZATION,
                Collections.<String>emptyList(),
                Collections.<String>emptyList()));
        JarAnalysisReport report = new JarAnalysisReport(
                HASH, results, new byte[]{ 0 }, new String[0], new String[0]);
        assertEquals(VerdictType.DANGEROUS, report.deriveVerdictType());
    }

    // =========================================================================
    // PERMISSIONS.LIST — signature covers declared permissions
    // =========================================================================

    /**
     * The engine signature must differ between a JAR with no
     * {@code META-INF/PERMISSIONS.LIST} and a JAR with one, because the
     * declared permissions are included in the canonical signed bytes.
     */
    @Test
    public void testSignatureDiffersWithAndWithoutPermissionsList()
            throws IOException, AnalysisException {
        byte[] classBytes = minimalClassBytes();
        byte[] jarNoList  = buildJar(classBytes, null);
        byte[] jarWithList = buildJar(classBytes,
                "permission java.awt.AWTPermission \"showWindowWithoutWarningBanner\";\n");

        JarAnalysisReport reportA = analyzer.analyze(
                new AnalysisRequest(jarNoList,  HASH, null));
        JarAnalysisReport reportB = analyzer.analyze(
                new AnalysisRequest(jarWithList, HASH, null));

        // Signatures must differ because the canonical form covers
        // declaredPermissions.
        byte[] sigA = reportA.getEngineSignature();
        byte[] sigB = reportB.getEngineSignature();
        // It is astronomically unlikely they are identical with different inputs
        // (different permission content) — assert they differ.
        boolean same = Arrays.equals(sigA, sigB);
        assertEquals("Signature must differ when PERMISSIONS.LIST content differs",
                false, same);
    }

    // =========================================================================
    // getDeclaredPermissions — returns a defensive copy
    // =========================================================================

    /**
     * Each call to {@link JarAnalysisReport#getDeclaredPermissions()} must
     * return a fresh array; mutating the returned array must not affect
     * subsequent calls.
     */
    @Test
    public void testGetDeclaredPermissions_ReturnsCopy()
            throws IOException, AnalysisException {
        String content = "permission java.awt.AWTPermission \"all\";\n";
        byte[] jarBytes = buildJar(minimalClassBytes(), content);
        JarAnalysisReport report = analyzer.analyze(
                new AnalysisRequest(jarBytes, HASH, null));
        String[] first = report.getDeclaredPermissions();
        first[0] = "mutated";
        String[] second = report.getDeclaredPermissions();
        assertEquals(
                "permission java.awt.AWTPermission \"all\";",
                second[0]);
    }

    // =========================================================================
    // Decompression caps (Fix 3)
    // =========================================================================

    /**
     * A single {@code .class} entry whose uncompressed size exceeds the
     * per-entry decompression cap (64 MiB) must abort the whole
     * {@code analyze()} call with an {@link AnalysisException} — not be
     * silently truncated or skipped.  All-zero content compresses to a tiny
     * DEFLATE size, so the JAR bytes themselves stay small even though the
     * entry claims to decompress far past the cap.
     */
    @Test
    public void testAnalyze_OversizedClassEntry_RejectedAsAnalysisException()
            throws IOException {
        byte[] jarBytes = buildJarWithOversizedEntry(
                "au/net/zeus/jgdms/bae/test/Big.class");
        try {
            analyzer.analyze(new AnalysisRequest(jarBytes, HASH, null));
            fail("Expected AnalysisException for an oversized .class entry");
        } catch (AnalysisException expected) {
            // expected — per-entry decompression cap enforced
        }
    }

    /**
     * Bypass 1/2 regression: an oversized entry that is <em>not</em> a
     * {@code .class} file (and is therefore only drained, never handed to
     * the ASM visitors) must still be rejected.  Before the fix, entries
     * skipped via {@code continue} were inflated-and-discarded with no size
     * check at all by the next {@code getNextEntry()} call — an
     * unbounded-CPU-time bypass even though the bytes were immediately
     * thrown away.
     */
    @Test
    public void testAnalyze_OversizedNonClassEntry_RejectedAsAnalysisException()
            throws IOException {
        byte[] jarBytes = buildJarWithOversizedEntry("resources/data.bin");
        try {
            analyzer.analyze(new AnalysisRequest(jarBytes, HASH, null));
            fail("Expected AnalysisException for an oversized skipped entry");
        } catch (AnalysisException expected) {
            // expected — drained entries are capped too (Bypass 2 closed)
        }
    }

    /**
     * Bypass 1 regression: switching from {@code JarInputStream} (which
     * eagerly inflates {@code META-INF/MANIFEST.MF} inside its constructor,
     * before any cap can fire) to {@code ZipInputStream} means the manifest
     * is just another entry, subject to the same caps as everything else.
     * An oversized manifest must be rejected exactly like any other
     * oversized entry.
     */
    @Test
    public void testAnalyze_OversizedManifest_RejectedAsAnalysisException()
            throws IOException {
        byte[] jarBytes = buildJarWithOversizedEntry("META-INF/MANIFEST.MF");
        try {
            analyzer.analyze(new AnalysisRequest(jarBytes, HASH, null));
            fail("Expected AnalysisException for an oversized manifest entry");
        } catch (AnalysisException expected) {
            // expected — Bypass 1 closed: the manifest is no longer eagerly
            // (and unboundedly) parsed before caps can apply
        }
    }

    /**
     * A JAR with more entries than {@code MAX_ENTRIES} (10,000) must be
     * rejected, independent of any individual entry's size.
     */
    @Test
    public void testAnalyze_TooManyEntries_RejectedAsAnalysisException()
            throws IOException {
        byte[] jarBytes = buildJarWithManyEntries(10_001);
        try {
            analyzer.analyze(new AnalysisRequest(jarBytes, HASH, null));
            fail("Expected AnalysisException for too many JAR entries");
        } catch (AnalysisException expected) {
            // expected — MAX_ENTRIES cap enforced
        }
    }

    /**
     * A small, normal JAR (well under every cap) must continue to analyze
     * successfully — the caps must not false-positive on ordinary input.
     */
    @Test
    public void testAnalyze_SmallNormalJar_NotAffectedByCaps()
            throws IOException, AnalysisException {
        byte[] jarBytes = buildJar(minimalClassBytes(), null);
        JarAnalysisReport report = analyzer.analyze(
                new AnalysisRequest(jarBytes, HASH, null));
        assertNotNull(report);
        assertEquals(VerdictType.SAFE, report.deriveVerdictType());
    }

    /** Builds a JAR with a single entry whose uncompressed size is ~65 MiB
     * (all zeros, so it compresses to a tiny DEFLATE size) — 1 MiB over the
     * 64 MiB per-entry cap. */
    private static byte[] buildJarWithOversizedEntry(String entryName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JarOutputStream jos = new JarOutputStream(baos)) {
            JarEntry entry = new JarEntry(entryName);
            jos.putNextEntry(entry);
            byte[] chunk = new byte[1024 * 1024]; // 1 MiB of zeros
            for (int i = 0; i < 65; i++) { // 65 MiB > 64 MiB per-entry cap
                jos.write(chunk);
            }
            jos.closeEntry();
        }
        return baos.toByteArray();
    }

    /** Builds a JAR with {@code count} tiny empty entries. */
    private static byte[] buildJarWithManyEntries(int count) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JarOutputStream jos = new JarOutputStream(baos)) {
            for (int i = 0; i < count; i++) {
                JarEntry entry = new JarEntry("e" + i + ".txt");
                jos.putNextEntry(entry);
                jos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    // =========================================================================
    // BLOCKING_DECLARED — guarded blocking sink + permission declared
    // =========================================================================

    /**
     * When a class's {@code <clinit>} contains a permission guard (e.g.
     * {@code SecurityManager.checkPermission}) before a blocking
     * {@code Socket.connect} call, <em>and</em> the JAR's
     * {@code PERMISSIONS.LIST} declares {@code java.net.SocketPermission},
     * the per-class verdict must be
     * {@link ClinitVerdict#BLOCKING_DECLARED} and the aggregate
     * {@link VerdictType} must be {@link VerdictType#DANGEROUS}.
     *
     * <p>The rationale: the declaration signals that the developer intends
     * {@code SocketPermission} to be granted.  Granting it allows the JDK's
     * internal {@code SecurityManager.checkConnect()} to pass, enabling the
     * blocking {@code Socket.connect} to execute during {@code <clinit>} and
     * pin the virtual-thread carrier — a potential Denial of Service (DoS).
     */
    @Test
    public void testBlockingDeclared_SocketConnectGuarded_SocketPermDeclared_isDangerous()
            throws IOException, AnalysisException {
        byte[] classBytes = buildGuardedClinitCallingMethod(
                "java/net/Socket", "connect",
                "(Ljava/net/SocketAddress;)V",
                Opcodes.INVOKEVIRTUAL);
        String permContent =
                "permission java.net.SocketPermission \"*\", \"connect\";\n";
        byte[] jarBytes = buildJar(classBytes,
                "au/net/zeus/jgdms/bae/test/Synthetic.class", permContent);
        JarAnalysisReport report = analyzer.analyze(
                new AnalysisRequest(jarBytes, HASH, null));

        ClassAnalysisResult car =
                report.getResults().get("au/net/zeus/jgdms/bae/test/Synthetic");
        assertNotNull("Expected result for Synthetic class", car);
        assertEquals(ClinitVerdict.BLOCKING_DECLARED, car.getClinitVerdict());
        assertEquals(VerdictType.DANGEROUS, report.deriveVerdictType());
    }

    /**
     * When a class's {@code <clinit>} is {@link ClinitVerdict#BLOCKING_GUARDED}
     * for {@code Socket.connect} but the JAR declares <em>no</em>
     * {@code META-INF/PERMISSIONS.LIST}, the verdict must remain
     * {@link ClinitVerdict#BLOCKING_GUARDED} and the aggregate type must be
     * {@link VerdictType#INCONCLUSIVE}.
     */
    @Test
    public void testBlockingGuarded_SocketConnectGuarded_NoPermDeclared_isInconclusive()
            throws IOException, AnalysisException {
        byte[] classBytes = buildGuardedClinitCallingMethod(
                "java/net/Socket", "connect",
                "(Ljava/net/SocketAddress;)V",
                Opcodes.INVOKEVIRTUAL);
        byte[] jarBytes = buildJar(classBytes,
                "au/net/zeus/jgdms/bae/test/Synthetic.class", null);
        JarAnalysisReport report = analyzer.analyze(
                new AnalysisRequest(jarBytes, HASH, null));

        ClassAnalysisResult car =
                report.getResults().get("au/net/zeus/jgdms/bae/test/Synthetic");
        assertNotNull("Expected result for Synthetic class", car);
        assertEquals(ClinitVerdict.BLOCKING_GUARDED, car.getClinitVerdict());
        assertEquals(VerdictType.INCONCLUSIVE, report.deriveVerdictType());
    }

    /**
     * {@code Thread.sleep} has no JDK-internal per-call
     * {@code SecurityManager.checkXxx()}, so it has no entry in
     * {@link BlockingSinkRegistry#SINK_TO_PERMISSION_CLASS}.  Even if a
     * {@code RuntimePermission} is declared in {@code PERMISSIONS.LIST}, the
     * verdict must remain {@link ClinitVerdict#BLOCKING_GUARDED} (not
     * {@code BLOCKING_DECLARED}) and the aggregate type
     * {@link VerdictType#INCONCLUSIVE}.
     */
    @Test
    public void testBlockingGuarded_ThreadSleepGuarded_AnyPermDeclared_staysInconclusive()
            throws IOException, AnalysisException {
        byte[] classBytes = buildGuardedClinitCallingMethod(
                "java/lang/Thread", "sleep", "(J)V",
                Opcodes.INVOKESTATIC);
        String permContent =
                "permission java.lang.RuntimePermission \"modifyThread\";\n";
        byte[] jarBytes = buildJar(classBytes,
                "au/net/zeus/jgdms/bae/test/Synthetic.class", permContent);
        JarAnalysisReport report = analyzer.analyze(
                new AnalysisRequest(jarBytes, HASH, null));

        ClassAnalysisResult car =
                report.getResults().get("au/net/zeus/jgdms/bae/test/Synthetic");
        assertNotNull("Expected result for Synthetic class", car);
        assertEquals(ClinitVerdict.BLOCKING_GUARDED, car.getClinitVerdict());
        assertEquals(VerdictType.INCONCLUSIVE, report.deriveVerdictType());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Builds a minimal, structurally valid class file using ASM.
     * The class has no fields, no methods other than an implicit default
     * constructor supplied by the JVM, and no {@code <clinit>}.
     */
    private static byte[] minimalClassBytes() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11,
                 Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                 "au/net/zeus/jgdms/bae/test/MinimalClass",
                 null, "java/lang/Object", null);
        // Default constructor
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Builds an in-memory JAR containing the supplied class bytes under the
     * entry {@code au/net/zeus/jgdms/bae/test/MinimalClass.class}, and
     * optionally a {@code META-INF/PERMISSIONS.LIST} entry.
     *
     * @param classBytes        bytes of the compiled class to include
     * @param permissionsContent text content of PERMISSIONS.LIST, or
     *                           {@code null} to omit the entry
     * @return raw JAR bytes
     */
    private static byte[] buildJar(byte[] classBytes, String permissionsContent)
            throws IOException {
        return buildJar(classBytes,
                "au/net/zeus/jgdms/bae/test/MinimalClass.class",
                permissionsContent);
    }

    /**
     * Builds an in-memory JAR containing the supplied class bytes under the
     * given entry name, and optionally a {@code META-INF/PERMISSIONS.LIST}
     * entry.
     *
     * @param classBytes        bytes of the compiled class to include
     * @param classEntryName    JAR entry name for the class (e.g.
     *                          {@code "com/example/Foo.class"})
     * @param permissionsContent text content of PERMISSIONS.LIST, or
     *                           {@code null} to omit the entry
     * @return raw JAR bytes
     */
    private static byte[] buildJar(byte[] classBytes,
                                   String classEntryName,
                                   String permissionsContent)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JarOutputStream jos = new JarOutputStream(baos)) {
            if (permissionsContent != null) {
                JarEntry plEntry = new JarEntry("META-INF/PERMISSIONS.LIST");
                jos.putNextEntry(plEntry);
                jos.write(permissionsContent.getBytes(StandardCharsets.UTF_8));
                jos.closeEntry();
            }
            JarEntry classEntry = new JarEntry(classEntryName);
            jos.putNextEntry(classEntry);
            jos.write(classBytes);
            jos.closeEntry();
        }
        return baos.toByteArray();
    }

    /**
     * Builds a class named {@code au/net/zeus/jgdms/bae/test/Synthetic} whose
     * {@code <clinit>} calls {@code SecurityManager.checkPermission} (a
     * permission guard) and then calls the specified blocking method.
     *
     * <p>The generated bytecode is structurally equivalent to:
     * <pre>
     *   static {
     *       new SecurityManager().checkPermission(new RuntimePermission("test"));
     *       blockingMethod(...);
     *   }
     * </pre>
     *
     * <p>The bytecode is intentionally minimal (arguments are omitted) because
     * it is analysed statically — not executed.
     */
    private static byte[] buildGuardedClinitCallingMethod(
            String blockingOwner, String blockingName, String blockingDescriptor,
            int invokeOpcode) {

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V11,
                 Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                 "au/net/zeus/jgdms/bae/test/Synthetic",
                 null, "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();

        // Emit: new SecurityManager().checkPermission(new RuntimePermission("test"))
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/SecurityManager");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/SecurityManager", "<init>", "()V", false);
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimePermission");
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("test");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/RuntimePermission", "<init>",
                "(Ljava/lang/String;)V", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                "java/lang/SecurityManager", "checkPermission",
                "(Ljava/security/Permission;)V", false);

        // Emit the blocking call
        mv.visitMethodInsn(invokeOpcode,
                blockingOwner, blockingName, blockingDescriptor, false);

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(4, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
