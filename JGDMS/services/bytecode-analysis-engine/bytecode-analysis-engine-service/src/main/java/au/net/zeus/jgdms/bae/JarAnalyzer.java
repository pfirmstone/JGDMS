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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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
 * The optional {@code META-INF/PERMISSIONS.LIST} entry is also read during
 * this phase; its non-blank, non-comment lines are collected and included in
 * the {@link JarAnalysisReport} as {@link JarAnalysisReport#getDeclaredPermissions()
 * declared permissions}.
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

    /**
     * Non-empty placeholder signature used only to build the intermediate
     * {@link JarAnalysisReport} whose {@link JarAnalysisReport#canonicalBytes()}
     * is signed.  The signature field is excluded from the canonical form, so
     * its value never affects the signed/verified bytes.
     */
    private static final byte[] PLACEHOLDER_SIGNATURE = new byte[]{ 0 };

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
        // permissionLines: lines from META-INF/PERMISSIONS.LIST (if present)

        Map<String, Set<String>> callGraph    = new HashMap<String, Set<String>>();
        Map<String, Boolean>     isNativeMap  = new HashMap<String, Boolean>();
        Map<String, byte[]>      rawClasses   = new LinkedHashMap<String, byte[]>();
        Set<String>              parseFailures = new HashSet<String>();
        List<String>             permissionLines = new ArrayList<String>();

        try {
            JarInputStream jis = new JarInputStream(
                    new ByteArrayInputStream(jarBytes));
            try {
                JarEntry entry;
                while ((entry = jis.getNextJarEntry()) != null) {
                    String entryName = entry.getName();
                    if ("META-INF/PERMISSIONS.LIST".equals(entryName)) {
                        byte[] data = readEntry(jis);
                        if (data != null) {
                            parsePermissionsList(data, permissionLines);
                        }
                        continue;
                    }
                    if (!entryName.endsWith(".class")) continue;
                    byte[] classBytes = readEntry(jis);
                    if (classBytes == null) {
                        // Read failure — treat as parse failure
                        parseFailures.add(entryNameToClassName(entryName));
                        continue;
                    }
                    String[] clinitOwner = new String[1];
                    ClinitBlockingVisitor.indexClass(
                            classBytes, callGraph, isNativeMap, clinitOwner);
                    String className = clinitOwner[0];
                    if (className == null || className.isEmpty()) {
                        // ASM could not determine the class name (parse failure)
                        parseFailures.add(entryNameToClassName(entryName));
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

        String[] declaredPermissions = permissionLines.toArray(new String[0]);

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

            // Upgrade BLOCKING_GUARDED to BLOCKING_DECLARED when the blocking
            // sink's required permission is explicitly declared in PERMISSIONS.LIST.
            // Such a declaration signals that the developer intends the permission
            // to be granted; granting it enables the blocking path and creates a
            // potential Denial of Service (DoS) via virtual-thread carrier pinning.
            // For dual-guard sinks (e.g. SocketChannel.connect) the verdict is
            // promoted when ANY of the sink's guards is declared.
            if (clinitResult.verdict == ClinitVerdict.BLOCKING_GUARDED
                    && declaredPermissions.length > 0) {
                List<String> path = clinitResult.callPath;
                if (!path.isEmpty()) {
                    String sinkKey = path.get(path.size() - 1);
                    Set<String> requiredPermClasses =
                            BlockingSinkRegistry.getRequiredPermissionClasses(sinkKey);
                    if (requiredPermClasses != null) {
                        for (String permEntry : requiredPermClasses) {
                            if (declaresPermissionClass(declaredPermissions, permEntry)) {
                                clinitResult = new ClinitBlockingVisitor.ClinitAnalysisResult(
                                        ClinitVerdict.BLOCKING_DECLARED, path);
                                break;
                            }
                        }
                    }
                }
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

        // ---- Codebase URLs for traceability / reactive condemnation ---------
        String[] codebaseUrls = (request.getOriginalUri() != null)
                ? new String[]{ request.getOriginalUri().toString() }
                : new String[0];

        // ---- Sign the report ------------------------------------------------
        // The signed bytes are exactly JarAnalysisReport.canonicalBytes() — the
        // single source of truth shared with the VerdictRegistry verifier.  We
        // sign a placeholder-signature report's canonicalBytes() (the signature
        // field is excluded from the canonical form), then rebuild the final
        // report carrying the real signature.
        byte[] signature;
        try {
            JarAnalysisReport unsigned = new JarAnalysisReport(
                    contentHash, results, PLACEHOLDER_SIGNATURE,
                    declaredPermissions, codebaseUrls);
            signature = sign(unsigned.canonicalBytes());
        } catch (Exception ex) {
            throw new AnalysisException("Failed to sign JarAnalysisReport", ex);
        }

        return new JarAnalysisReport(
                contentHash, results, signature, declaredPermissions, codebaseUrls);
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
     * Signs {@code canonicalBytes} with the engine's private key.
     *
     * <p>The bytes passed here MUST be exactly
     * {@link JarAnalysisReport#canonicalBytes()} so that the registry, which
     * verifies against the same single-source-of-truth canonical form, accepts
     * the signature.
     *
     * @param canonicalBytes the report's canonical byte form; must be non-null
     * @return the DER-encoded signature
     */
    private byte[] sign(byte[] canonicalBytes)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature signer = Signature.getInstance(sigAlgorithm);
        signer.initSign(enginePrivateKey);
        signer.update(canonicalBytes);
        return signer.sign();
    }

    /**
     * Returns {@code true} if any line in {@code declaredPermissions}
     * declares the given permission entry.
     *
     * <p>The {@code permEntry} parameter uses one of two encodings:
     * <ul>
     *   <li><em>{@code "className"}</em> — a line is considered a match if
     *       it starts with {@code "permission <className>"} followed by a
     *       non-identifier character (space, tab, {@code "}, or
     *       end-of-line).  This prevents a class name that is a prefix of
     *       another (e.g. {@code java.net.Socket} matching
     *       {@code java.net.SocketPermission}) from producing a false
     *       positive.</li>
     *   <li><em>{@code "className#action"}</em> — a line must both start
     *       with {@code "permission <className>"} (same prefix rule as above)
     *       <em>and</em> contain the quoted action string
     *       ({@code '"' + action + '"'}).  Used for broad permission classes
     *       such as {@code java.lang.RuntimePermission} where different action
     *       names have unrelated security semantics, preventing false
     *       positives from unrelated grants of the same class.</li>
     * </ul>
     *
     * @param declaredPermissions lines from {@code META-INF/PERMISSIONS.LIST}
     *                            (already trimmed, non-blank, non-comment)
     * @param permEntry           either a fully qualified permission class name
     *                            (e.g. {@code "java.net.SocketPermission"}) or
     *                            a {@code "className#action"} pair (e.g.
     *                            {@code "java.lang.RuntimePermission#createVirtualThread"})
     * @return {@code true} if at least one line satisfies the match criteria
     */
    private static boolean declaresPermissionClass(String[] declaredPermissions,
                                                   String permEntry) {
        int hashIdx = permEntry.indexOf('#');
        if (hashIdx < 0) {
            // class-only match (original behaviour)
            String prefix = "permission " + permEntry;
            for (String line : declaredPermissions) {
                if (line.startsWith(prefix)) {
                    int len = prefix.length();
                    if (len >= line.length()) return true;
                    char next = line.charAt(len);
                    if (next == ' ' || next == '\t' || next == '"' || next == ';') {
                        return true;
                    }
                }
            }
            return false;
        }
        // class#action match: the class name AND the quoted action must both
        // appear on the same PERMISSIONS.LIST line.
        String className    = permEntry.substring(0, hashIdx);
        String action       = permEntry.substring(hashIdx + 1);
        String classPrefix  = "permission " + className;
        String quotedAction = "\"" + action + "\"";
        for (String line : declaredPermissions) {
            if (line.startsWith(classPrefix)) {
                int len = classPrefix.length();
                if (len < line.length()) {
                    char next = line.charAt(len);
                    if ((next == ' ' || next == '\t' || next == '"' || next == ';')
                            && line.contains(quotedAction)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Reads and parses the content of a {@code META-INF/PERMISSIONS.LIST}
     * JAR entry into {@code target}.
     *
     * <p>Each line is trimmed; blank lines and lines whose first non-whitespace
     * character is {@code #} (comments) are discarded.  All other lines are
     * added to {@code target} in the order they appear in the file.
     *
     * @param data   raw bytes of the entry; must be non-null
     * @param target list to which non-blank, non-comment lines are appended
     */
    private static void parsePermissionsList(byte[] data, List<String> target) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(data),
                        StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    target.add(line);
                }
            }
        } catch (IOException e) {
            // ByteArrayInputStream never throws — this branch is unreachable
            logger.log(Level.WARNING, "Unexpected I/O error reading PERMISSIONS.LIST", e);
        }
    }
}
