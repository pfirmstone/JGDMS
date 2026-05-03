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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import au.net.zeus.jgdms.api.codebase.AnalysisException;
import au.net.zeus.jgdms.api.codebase.AnalysisRequest;
import au.net.zeus.jgdms.api.codebase.AtomicSerialVerdict;
import au.net.zeus.jgdms.api.codebase.ClassAnalysisResult;
import au.net.zeus.jgdms.api.codebase.ClinitVerdict;
import au.net.zeus.jgdms.api.codebase.JarAnalysisReport;

/**
 * Coordinates the two-phase analysis of a JAR file using ASM bytecode visitors.
 *
 * <h2>Phase 1 — Indexing</h2>
 * <p>Every {@code .class} entry in the JAR is parsed using
 * {@link ClinitBlockingVisitor#indexClass} to build a shared call-graph map.
 * Classes that ASM cannot parse are recorded as parse failures; they will
 * receive a fail-secure verdict of {@link ClinitVerdict#BLOCKING} +
 * {@link AtomicSerialVerdict#MISSING_CONSTRUCTOR}.
 *
 * <h2>Phase 2 — Analysis</h2>
 * <p>For each class:
 * <ol>
 *   <li>{@link ClinitBlockingVisitor#analyzeClinitReachability} performs a
 *       BFS from the class's {@code <clinit>} up to
 *       {@link AnalysisRequest#getMaxBfsDepth()} hops, looking for blocking
 *       sinks or unregistered native calls.</li>
 *   <li>{@link AtomicSerialComplianceVisitor#analyze} checks the class for
 *       {@code @AtomicSerial} protocol compliance.</li>
 * </ol>
 * <p>After per-class analysis, {@link ClinitBlockingVisitor#detectClinitCycles}
 * is run once to find circular {@code <clinit>} dependency cycles; cycle
 * participants have their verdict upgraded to {@link ClinitVerdict#CYCLE}.
 *
 * <h2>Signing</h2>
 * <p>The assembled {@link JarAnalysisReport} is signed with the engine's
 * private key before being returned.
 *
 * @see ClinitBlockingVisitor
 * @see AtomicSerialComplianceVisitor
 * @see BlockingSinkRegistry
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
final class JarAnalyzer {

    private static final Logger logger =
            Logger.getLogger(JarAnalyzer.class.getName());

    private final PrivateKey enginePrivateKey;
    private final String     sigAlgorithm;

    /**
     * Creates a new {@code JarAnalyzer}.
     *
     * @param enginePrivateKey the engine's private key for signing reports;
     *                         must be non-null
     * @param sigAlgorithm     JCA standard name of the signature algorithm
     *                         (e.g. {@code "SHA256withRSA"}); must be non-null
     */
    JarAnalyzer(PrivateKey enginePrivateKey, String sigAlgorithm) {
        if (enginePrivateKey == null) throw new NullPointerException("enginePrivateKey");
        if (sigAlgorithm == null)     throw new NullPointerException("sigAlgorithm");
        this.enginePrivateKey = enginePrivateKey;
        this.sigAlgorithm     = sigAlgorithm;
    }

    // -------------------------------------------------------------------------
    // Main entry point
    // -------------------------------------------------------------------------

    /**
     * Analyses the JAR described by {@code request} and returns a signed
     * {@link JarAnalysisReport}.
     *
     * @param request the analysis request; must be non-null
     * @return the signed report; never {@code null}
     * @throws AnalysisException if the JAR bytes cannot be parsed, if no
     *                           class entries are found, or if signing fails
     */
    JarAnalysisReport analyze(AnalysisRequest request) throws AnalysisException {
        if (request == null) throw new NullPointerException("request");

        byte[] jarBytes    = request.getJarBytes();
        String contentHash = request.getContentHash();
        int    maxDepth    = request.getMaxBfsDepth();

        // ---- Phase 1: Index all class bytes ---------------------------------
        // callGraph:   methodKey → set of call-site keys
        // isNativeMap: methodKey → true  (only for native methods)
        // rawClasses:  className → raw class bytes (for Phase 2 AtomicSerial check)
        // parseFailures: set of className strings that ASM could not parse

        Map<String, Set<String>> callGraph    = new HashMap<String, Set<String>>();
        Map<String, Boolean>     isNativeMap  = new HashMap<String, Boolean>();
        Map<String, byte[]>      rawClasses   = new LinkedHashMap<String, byte[]>();
        Set<String>              parseFailures = new HashSet<String>();

        try {
            JarInputStream jis = new JarInputStream(
                    new ByteArrayInputStream(jarBytes));
            try {
                JarEntry entry;
                while ((entry = jis.getNextJarEntry()) != null) {
                    if (!entry.getName().endsWith(".class")) continue;
                    byte[] classBytes = readEntry(jis);
                    if (classBytes == null) {
                        // Read failure — treat as parse failure
                        parseFailures.add(entryNameToClassName(entry.getName()));
                        continue;
                    }
                    String[] clinitOwner = new String[1];
                    ClinitBlockingVisitor.indexClass(
                            classBytes, callGraph, isNativeMap, clinitOwner);
                    String className = clinitOwner[0];
                    if (className == null || className.isEmpty()) {
                        // ASM could not determine the class name (parse failure)
                        parseFailures.add(entryNameToClassName(entry.getName()));
                    } else {
                        rawClasses.put(className, classBytes);
                    }
                }
            } finally {
                jis.close();
            }
        } catch (IOException e) {
            throw new AnalysisException("Cannot read JAR bytes as a JAR stream", e);
        }

        if (rawClasses.isEmpty() && parseFailures.isEmpty()) {
            throw new AnalysisException("JAR contains no class entries");
        }

        // ---- Phase 2a: Detect <clinit> cycles --------------------------------
        Set<String> cyclicClasses =
                ClinitBlockingVisitor.detectClinitCycles(callGraph);

        // ---- Phase 2b: Per-class analysis ------------------------------------
        Map<String, ClassAnalysisResult> results =
                new LinkedHashMap<String, ClassAnalysisResult>(
                        rawClasses.size() + parseFailures.size());

        // Parse failures get fail-secure verdicts
        for (String failedClass : parseFailures) {
            results.put(failedClass, new ClassAnalysisResult(
                    failedClass,
                    ClinitVerdict.BLOCKING,
                    AtomicSerialVerdict.MISSING_CONSTRUCTOR,
                    Collections.singletonList(failedClass + " (parse failure)"),
                    Collections.<String>emptyList()));
        }

        for (Map.Entry<String, byte[]> e : rawClasses.entrySet()) {
            String className  = e.getKey();
            byte[] classBytes = e.getValue();

            // Clinit analysis
            ClinitBlockingVisitor.ClinitAnalysisResult clinitResult;
            if (cyclicClasses.contains(className)) {
                // Cycle detection overrides blocking/native-opacity
                clinitResult = new ClinitBlockingVisitor.ClinitAnalysisResult(
                        ClinitVerdict.CYCLE,
                        Collections.<String>emptyList());
            } else {
                clinitResult = ClinitBlockingVisitor.analyzeClinitReachability(
                        className, callGraph, isNativeMap, maxDepth);
            }

            // AtomicSerial compliance
            AtomicSerialVerdict atomicVerdict =
                    AtomicSerialComplianceVisitor.analyze(classBytes);

            // Build cycle participant list for CYCLE verdicts
            List<String> cycleParticipants;
            if (clinitResult.verdict == ClinitVerdict.CYCLE) {
                cycleParticipants = new ArrayList<String>(cyclicClasses);
            } else {
                cycleParticipants = Collections.emptyList();
            }

            results.put(className, new ClassAnalysisResult(
                    className,
                    clinitResult.verdict,
                    atomicVerdict,
                    clinitResult.callPath,
                    cycleParticipants));
        }

        // ---- Sign the report ------------------------------------------------
        byte[] signature;
        try {
            signature = sign(contentHash, results);
        } catch (Exception ex) {
            throw new AnalysisException("Failed to sign JarAnalysisReport", ex);
        }

        return new JarAnalysisReport(contentHash, results, signature);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a JAR entry name (e.g. {@code "com/example/Foo.class"}) to a
     * binary class name (e.g. {@code "com.example.Foo"}).
     */
    private static String entryNameToClassName(String entryName) {
        String slashFixed = entryName.replace('/', '.');
        if (slashFixed.endsWith(".class")) {
            return slashFixed.substring(0, slashFixed.length() - ".class".length());
        }
        return slashFixed;
    }

    /**
     * Reads all bytes from the current JAR entry stream.
     *
     * @return the entry bytes, or {@code null} if an I/O error occurs
     */
    private static byte[] readEntry(JarInputStream jis) {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream(8192);
            byte[] tmp = new byte[8192];
            int n;
            while ((n = jis.read(tmp)) != -1) {
                buf.write(tmp, 0, n);
            }
            return buf.toByteArray();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to read JAR entry bytes", e);
            return null;
        }
    }

    /**
     * Signs the canonical representation of the report fields with the
     * engine's private key.
     *
     * <p>The canonical form is:
     * <pre>
     *   contentHash (UTF-8 bytes) + NUL
     *   for each className (sorted):
     *     className (UTF-8) + NUL
     *     clinitVerdict.name() (UTF-8) + NUL
     *     atomicVerdict.name() (UTF-8) + NUL
     * </pre>
     */
    private byte[] sign(String contentHash,
                        Map<String, ClassAnalysisResult> results)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature signer = Signature.getInstance(sigAlgorithm);
        signer.initSign(enginePrivateKey);

        byte nul = 0;
        byte[] hashBytes = contentHash.getBytes(StandardCharsets.UTF_8);
        signer.update(hashBytes);
        signer.update(nul);

        // Sort class names for determinism
        List<String> sortedNames = new ArrayList<String>(results.keySet());
        Collections.sort(sortedNames);
        for (String name : sortedNames) {
            ClassAnalysisResult cr = results.get(name);
            signer.update(name.getBytes(StandardCharsets.UTF_8));
            signer.update(nul);
            signer.update(cr.getClinitVerdict().name()
                    .getBytes(StandardCharsets.UTF_8));
            signer.update(nul);
            signer.update(cr.getAtomicVerdict().name()
                    .getBytes(StandardCharsets.UTF_8));
            signer.update(nul);
        }
        return signer.sign();
    }
}
