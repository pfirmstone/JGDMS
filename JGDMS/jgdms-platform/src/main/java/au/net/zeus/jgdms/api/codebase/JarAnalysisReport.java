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
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * Immutable, serializable analysis report produced by a
 * {@link BytecodeAnalysisEngine} for a single JAR file.
 *
 * <p>A {@code JarAnalysisReport} is the structured output of
 * {@link BytecodeAnalysisEngine#analyzeJar(AnalysisRequest)}.  It contains:
 * <ul>
 *   <li>the SHA-256 content hash of the analysed JAR (from the
 *       {@link AnalysisRequest}),</li>
 *   <li>a per-class {@link ClassAnalysisResult} map keyed by internal
 *       binary class name,</li>
 *   <li>a DER-encoded signature produced by the engine's private key over
 *       the canonical form of the above fields.</li>
 * </ul>
 *
 * <p><strong>Deriving a {@link VerdictType} from a report.</strong>
 * The {@link VerdictRegistry} derives the aggregate {@link VerdictType} from
 * the submitted report via {@link #deriveVerdictType()}.  The mapping is:
 * <ul>
 *   <li>Any {@link ClinitVerdict#BLOCKING}, {@link ClinitVerdict#CYCLE},
 *       or {@link ClinitVerdict#BLOCKING_DECLARED}
 *       result → {@link VerdictType#DANGEROUS}</li>
 *   <li>Any {@link AtomicSerialVerdict} violation
 *       ({@code MISSING_CONSTRUCTOR}, {@code VALIDATION_ORDER},
 *       {@code MISSING_SERIAL_FORM}, {@code UNTYPED_GET}) → {@link VerdictType#DANGEROUS}</li>
 *   <li>Any {@link ClinitVerdict#NATIVE_OPACITY} or
 *       {@link AtomicSerialVerdict#NOT_ANNOTATED} → {@link VerdictType#INCONCLUSIVE}</li>
 *   <li>All results {@link ClinitVerdict#CLEAN} and
 *       ({@link AtomicSerialVerdict#COMPLIANT} or
 *       {@link AtomicSerialVerdict#NA}) → {@link VerdictType#SAFE}</li>
 * </ul>
 *
 * <p>Note: this report is submitted to the {@link VerdictRegistry} via
 * {@link VerdictRegistry#submitReport}, which accepts it directly and
 * verifies the engine signature.
 *
 * <p><strong>Serialization safety.</strong> All fields are validated
 * atomically before construction using the {@link AtomicSerial} protocol.
 *
 * @see BytecodeAnalysisEngine#analyzeJar
 * @see AnalysisRequest
 * @see ClassAnalysisResult
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
@AtomicSerial
public final class JarAnalysisReport implements Serializable {

    private static final long serialVersionUID = 3L;

    private static final String CONTENT_HASH         = "contentHash";
    private static final String CLASS_NAMES           = "classNames";
    private static final String CLASS_RESULTS         = "classResults";
    private static final String ENGINE_SIGNATURE      = "engineSignature";
    private static final String DECLARED_PERMISSIONS  = "declaredPermissions";
    private static final String CODEBASE_URLS         = "codebaseUrls";

    // serialPersistentFields is INDEPENDENT of serialForm() (dual-path JOSS keep, STD-008 sec9.1)
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField(CONTENT_HASH,        String.class),
        new ObjectStreamField(CLASS_NAMES,         String[].class),
        new ObjectStreamField(CLASS_RESULTS,       ClassAnalysisResult[].class),
        new ObjectStreamField(ENGINE_SIGNATURE,    byte[].class),
        new ObjectStreamField(DECLARED_PERMISSIONS, String[].class)
    };

    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm(CONTENT_HASH,        String.class),
            new SerialForm(CLASS_NAMES,         String[].class),
            new SerialForm(CLASS_RESULTS,       ClassAnalysisResult[].class),
            new SerialForm(ENGINE_SIGNATURE,    byte[].class),
            new SerialForm(DECLARED_PERMISSIONS, String[].class),
            new SerialForm(CODEBASE_URLS,       String[].class)
        };
    }

    public static void serialize(PutArg arg, JarAnalysisReport r) throws IOException {
        arg.put(CONTENT_HASH,         r.contentHash);
        arg.put(CLASS_NAMES,          r.classNames.clone());
        arg.put(CLASS_RESULTS,        r.classResults.clone());
        arg.put(ENGINE_SIGNATURE,     r.engineSignature.clone());
        arg.put(DECLARED_PERMISSIONS, r.declaredPermissions.clone());
        arg.put(CODEBASE_URLS,        r.codebaseUrls.clone());
        arg.writeArgs();
    }

    private static boolean check(GetArg arg) throws IOException, ClassNotFoundException {
        String contentHash = (String) arg.get(CONTENT_HASH, null);
        if (contentHash == null || contentHash.isEmpty())
            throw new InvalidObjectException("contentHash must not be null or empty");
        String[] classNames = (String[]) arg.get(CLASS_NAMES, null);
        if (classNames == null)
            throw new InvalidObjectException("classNames must not be null");
        ClassAnalysisResult[] classResults =
                (ClassAnalysisResult[]) arg.get(CLASS_RESULTS, null);
        if (classResults == null)
            throw new InvalidObjectException("classResults must not be null");
        if (classNames.length != classResults.length)
            throw new InvalidObjectException(
                    "classNames.length != classResults.length");
        for (String name : classNames) {
            if (name == null)
                throw new InvalidObjectException("classNames must not contain null");
        }
        for (ClassAnalysisResult cr : classResults) {
            if (cr == null)
                throw new InvalidObjectException("classResults must not contain null");
        }
        byte[] sig = (byte[]) arg.get(ENGINE_SIGNATURE, null);
        if (sig == null || sig.length == 0)
            throw new InvalidObjectException("engineSignature must not be null or empty");
        // declaredPermissions may be absent (old reports) or null → treated as empty
        String[] dp = (String[]) arg.get(DECLARED_PERMISSIONS, null);
        if (dp != null) {
            for (String s : dp) {
                if (s == null)
                    throw new InvalidObjectException(
                            "declaredPermissions must not contain null elements");
            }
        }
        // codebaseUrls may be absent (old reports) or null → treated as empty
        String[] cu = (String[]) arg.get(CODEBASE_URLS, null);
        if (cu != null) {
            for (String s : cu) {
                if (s == null)
                    throw new InvalidObjectException(
                            "codebaseUrls must not contain null elements");
            }
        }
        return true;
    }

    /**
     * SHA-256 hex digest of the analysed JAR.
     *
     * @serial
     */
    private final String contentHash;

    /**
     * Internal binary class names ({@code /}-separated), in insertion order.
     * Parallel to {@link #classResults}; together they form the serialized
     * representation of the per-class results.
     *
     * @serial
     */
    private final String[] classNames;

    /**
     * Per-class analysis results, parallel to {@link #classNames}.
     *
     * @serial
     */
    private final ClassAnalysisResult[] classResults;

    /**
     * Per-class analysis results, keyed by internal binary class name
     * ({@code /}-separated), insertion order preserved.  Runtime-only view
     * derived from {@link #classNames}/{@link #classResults}; not serialized.
     */
    private final transient Map<String, ClassAnalysisResult> results;

    /**
     * DER-encoded signature produced by the engine's private key over the
     * canonical serialized form of {@link #contentHash}, {@link #results},
     * and {@link #declaredPermissions}.
     */
    private final byte[] engineSignature;

    /**
     * Lines read verbatim from {@code META-INF/PERMISSIONS.LIST} inside the
     * analysed JAR.  Each element is a trimmed, non-blank, non-comment line
     * from the file — typically a standard Java security policy permission
     * declaration (e.g.
     * {@code permission java.awt.AWTPermission "showWindowWithoutWarningBanner";}).
     *
     * <p>Empty when the JAR contains no {@code META-INF/PERMISSIONS.LIST} entry.
     * Never {@code null}.
     */
    private final String[] declaredPermissions;

    /**
     * The codebase URI(s) (RFC 3986 URI strings) from which the analysed JAR
     * was downloaded.  Carried for traceability and so the
     * {@link VerdictRegistry} can build a URL-to-hash index for reactive
     * condemnation of a content hash when a crash or carrier-pinning event is
     * later reported against one of these URLs.
     *
     * <p>May be empty when the origin is not tracked; never {@code null}, and
     * never contains {@code null} elements.
     */
    private final String[] codebaseUrls;

    /**
     * {@link AtomicSerial} deserialization constructor.
     *
     * @param arg the deserialization argument bag
     * @throws IOException            if validation fails
     * @throws ClassNotFoundException if a required class is not found
     */
    public JarAnalysisReport(GetArg arg) throws IOException, ClassNotFoundException {
        this(arg, check(arg));
    }

    private JarAnalysisReport(GetArg arg, boolean checked) throws IOException, ClassNotFoundException {
        contentHash      = (String) arg.get(CONTENT_HASH, null);
        classNames       = ((String[]) arg.get(CLASS_NAMES, null)).clone();
        classResults     = ((ClassAnalysisResult[]) arg.get(CLASS_RESULTS, null)).clone();
        results          = buildResults(classNames, classResults);
        engineSignature  = ((byte[]) arg.get(ENGINE_SIGNATURE, null)).clone();
        // declaredPermissions may be absent in older reports → treat as empty
        String[] dp = (String[]) arg.get(DECLARED_PERMISSIONS, null);
        declaredPermissions = (dp != null) ? dp.clone() : new String[0];
        // codebaseUrls may be absent in older reports → treat as empty
        String[] cu = (String[]) arg.get(CODEBASE_URLS, null);
        codebaseUrls = (cu != null) ? cu.clone() : new String[0];
    }

    /** Builds the unmodifiable insertion-ordered results map from the two
     *  parallel serialized arrays. */
    private static Map<String, ClassAnalysisResult> buildResults(
            String[] names, ClassAnalysisResult[] resultsArr) {
        Map<String, ClassAnalysisResult> map =
                new LinkedHashMap<String, ClassAnalysisResult>(names.length * 2);
        for (int i = 0; i < names.length; i++) {
            map.put(names[i], resultsArr[i]);
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Constructs a {@code JarAnalysisReport} with no declared permissions.
     *
     * <p>Equivalent to calling the four-argument constructor with an empty
     * {@code declaredPermissions} array.
     *
     * @param contentHash     SHA-256 hex digest of the analysed JAR; must be
     *                        non-null and non-empty
     * @param results         per-class analysis results; must be non-null
     * @param engineSignature DER-encoded engine signature; must be non-null
     *                        and non-empty
     * @throws IllegalArgumentException if any argument fails a precondition
     * @throws NullPointerException     if any argument is {@code null}
     */
    public JarAnalysisReport(String contentHash,
                              Map<String, ClassAnalysisResult> results,
                              byte[] engineSignature) {
        this(contentHash, results, engineSignature, new String[0], new String[0]);
    }

    /**
     * Constructs a {@code JarAnalysisReport} with declared permissions and no
     * codebase URLs.
     *
     * <p>Backward-compatible convenience constructor; equivalent to calling the
     * five-argument constructor with an empty {@code codebaseUrls} array.
     *
     * @param contentHash          SHA-256 hex digest of the analysed JAR; must
     *                             be non-null and non-empty
     * @param results              per-class analysis results; must be non-null
     * @param engineSignature      DER-encoded engine signature; must be non-null
     *                             and non-empty
     * @param declaredPermissions  lines from {@code META-INF/PERMISSIONS.LIST};
     *                             must be non-null; individual elements must be
     *                             non-null
     * @throws IllegalArgumentException if any argument fails a precondition
     * @throws NullPointerException     if any argument is {@code null}
     */
    public JarAnalysisReport(String contentHash,
                              Map<String, ClassAnalysisResult> results,
                              byte[] engineSignature,
                              String[] declaredPermissions) {
        this(contentHash, results, engineSignature, declaredPermissions, new String[0]);
    }

    /**
     * Constructs a {@code JarAnalysisReport} with declared permissions and
     * codebase URLs.
     *
     * @param contentHash          SHA-256 hex digest of the analysed JAR; must
     *                             be non-null and non-empty
     * @param results              per-class analysis results; must be non-null
     * @param engineSignature      DER-encoded engine signature; must be non-null
     *                             and non-empty
     * @param declaredPermissions  lines from {@code META-INF/PERMISSIONS.LIST};
     *                             must be non-null; individual elements must be
     *                             non-null
     * @param codebaseUrls         RFC 3986 URI strings of the JAR's origin; must
     *                             be non-null; individual elements must be
     *                             non-null; may be empty
     * @throws IllegalArgumentException if any argument fails a precondition
     * @throws NullPointerException     if any argument is {@code null}
     */
    public JarAnalysisReport(String contentHash,
                              Map<String, ClassAnalysisResult> results,
                              byte[] engineSignature,
                              String[] declaredPermissions,
                              String[] codebaseUrls) {
        if (contentHash == null)          throw new NullPointerException("contentHash");
        if (contentHash.isEmpty())        throw new IllegalArgumentException("contentHash must not be empty");
        if (results == null)              throw new NullPointerException("results");
        if (engineSignature == null)      throw new NullPointerException("engineSignature");
        if (engineSignature.length == 0)
            throw new IllegalArgumentException("engineSignature must not be empty");
        if (declaredPermissions == null)  throw new NullPointerException("declaredPermissions");
        for (int i = 0; i < declaredPermissions.length; i++) {
            if (declaredPermissions[i] == null)
                throw new NullPointerException("declaredPermissions[" + i + "]");
        }
        if (codebaseUrls == null)         throw new NullPointerException("codebaseUrls");
        for (int i = 0; i < codebaseUrls.length; i++) {
            if (codebaseUrls[i] == null)
                throw new NullPointerException("codebaseUrls[" + i + "]");
        }

        this.contentHash          = contentHash;
        Map<String, ClassAnalysisResult> ordered =
                new LinkedHashMap<String, ClassAnalysisResult>(results);
        int size = ordered.size();
        this.classNames           = new String[size];
        this.classResults         = new ClassAnalysisResult[size];
        int i = 0;
        for (Map.Entry<String, ClassAnalysisResult> e : ordered.entrySet()) {
            this.classNames[i]   = e.getKey();
            this.classResults[i] = e.getValue();
            i++;
        }
        this.results              = Collections.unmodifiableMap(ordered);
        this.engineSignature      = engineSignature.clone();
        this.declaredPermissions  = declaredPermissions.clone();
        this.codebaseUrls         = codebaseUrls.clone();
    }

    /**
     * Returns the SHA-256 hex digest of the analysed JAR.  This is the
     * primary key in the {@link VerdictRegistry}.
     *
     * @return non-null, non-empty hex string
     */
    public String getContentHash() { return contentHash; }

    /**
     * Returns an unmodifiable map of per-class analysis results, keyed by
     * internal binary class name ({@code /}-separated).
     *
     * @return unmodifiable, never {@code null}
     */
    public Map<String, ClassAnalysisResult> getResults() { return results; }

    /**
     * Returns a copy of the DER-encoded engine signature.
     *
     * @return non-null, non-empty byte array
     */
    public byte[] getEngineSignature() { return engineSignature.clone(); }

    /**
     * Returns a copy of the permission declarations read from
     * {@code META-INF/PERMISSIONS.LIST} in the analysed JAR.
     *
     * <p>Each element is a trimmed, non-blank, non-comment line from that
     * file — typically a standard Java security policy permission declaration
     * (e.g.
     * {@code permission java.awt.AWTPermission "showWindowWithoutWarningBanner";}).
     *
     * <p>Returns an empty array when the JAR contains no
     * {@code META-INF/PERMISSIONS.LIST} entry.
     *
     * @return non-null copy of the declared-permission lines
     */
    public String[] getDeclaredPermissions() { return declaredPermissions.clone(); }

    /**
     * Returns a copy of the codebase URI strings (RFC 3986) from which the
     * analysed JAR was downloaded.
     *
     * <p>Returns an empty array when the origin is not tracked.
     *
     * @return non-null copy of the codebase URI strings; never contains
     *         {@code null} elements
     */
    public String[] getCodebaseUrls() { return codebaseUrls.clone(); }

    /**
     * Returns the exact bytes that the engine signs and the registry verifies
     * for this report — the single source of truth for the report's signed
     * canonical form.
     *
     * <p>The format is a NUL-delimited ({@code 0x00}) UTF-8 byte stream with
     * three section markers ({@code "C"}, {@code "P"}, {@code "U"}) that
     * disambiguate the concatenation of the three variable-length sections:
     * <pre>
     *   contentHash + NUL
     *   "C" + NUL
     *   for each className in sorted(results.keySet()):
     *       className + NUL + clinitVerdict.name() + NUL + atomicVerdict.name() + NUL
     *   "P" + NUL
     *   for each perm in sorted(declaredPermissions):
     *       perm + NUL
     *   "U" + NUL
     *   for each url in sorted(codebaseUrls):
     *       url + NUL
     * </pre>
     *
     * <p>Both the analysis engine (when signing) and the
     * {@link VerdictRegistry} (when verifying) MUST use exactly these bytes, so
     * any change to a report field that affects the signed content is reflected
     * here and only here.
     *
     * @return the canonical signed/verified byte representation of this report
     */
    public byte[] canonicalBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
        try {
            writeField(baos, contentHash);

            // Section "C": per-class results, sorted by class name.
            writeField(baos, "C");
            List<String> sortedNames = new ArrayList<String>(results.keySet());
            Collections.sort(sortedNames);
            for (String name : sortedNames) {
                ClassAnalysisResult cr = results.get(name);
                writeField(baos, name);
                writeField(baos, cr.getClinitVerdict().name());
                writeField(baos, cr.getAtomicVerdict().name());
            }

            // Section "P": declared permissions, sorted.
            writeField(baos, "P");
            String[] sortedPerms = declaredPermissions.clone();
            Arrays.sort(sortedPerms);
            for (String perm : sortedPerms) {
                writeField(baos, perm);
            }

            // Section "U": codebase URLs, sorted.
            writeField(baos, "U");
            String[] sortedUrls = codebaseUrls.clone();
            Arrays.sort(sortedUrls);
            for (String url : sortedUrls) {
                writeField(baos, url);
            }
        } catch (IOException e) {
            // ByteArrayOutputStream.write never throws — unreachable.
            throw new AssertionError("ByteArrayOutputStream threw IOException", e);
        }
        return baos.toByteArray();
    }

    /** Writes {@code s} as UTF-8 bytes followed by a single NUL (0x00). */
    private static void writeField(ByteArrayOutputStream baos, String s)
            throws IOException {
        baos.write(s.getBytes(StandardCharsets.UTF_8));
        baos.write(0);
    }

    /**
     * Derives the aggregate {@link VerdictType} from all per-class results.
     *
     * <p>The derivation rules are:
     * <ol>
     *   <li>Any {@link ClinitVerdict#BLOCKING}, {@link ClinitVerdict#CYCLE},
     *       or {@link ClinitVerdict#BLOCKING_DECLARED}
     *       → {@link VerdictType#DANGEROUS}.
     *       {@code BLOCKING_DECLARED} is dangerous because the JAR's
     *       {@code META-INF/PERMISSIONS.LIST} declares the permission that
     *       guards the blocking path, signalling that the permission is
     *       intended to be granted; granting it enables virtual-thread
     *       carrier-pinning, a potential Denial of Service (DoS).</li>
     *   <li>Any {@link AtomicSerialVerdict} of {@code MISSING_CONSTRUCTOR},
     *       {@code VALIDATION_ORDER}, {@code MISSING_SERIAL_FORM}, or
     *       {@code UNTYPED_GET}
     *       → {@link VerdictType#DANGEROUS}</li>
     *   <li>Any {@link ConstrainableProxyVerdict#JAVA_SERIALIZATION} — a smart
     *       proxy that implements {@code java.io.Serializable}, an unvalidated
     *       deserialization path with no legitimate use on the wire
     *       → {@link VerdictType#DANGEROUS}</li>
     *   <li>Any {@link ClinitVerdict#NATIVE_OPACITY},
     *       {@link ClinitVerdict#BLOCKING_GUARDED}, or
     *       {@link AtomicSerialVerdict#NOT_ANNOTATED}
     *       → {@link VerdictType#INCONCLUSIVE} (unless a DANGEROUS signal was
     *       already found). {@code BLOCKING_GUARDED} is inconclusive because
     *       the blocking path is only reachable when the caller holds the
     *       guarding permission; a policy that denies that permission prevents
     *       the block.</li>
     *   <li>The JAR's {@code META-INF/PERMISSIONS.LIST} requests a raw network
     *       connection ({@link java.net.SocketPermission})
     *       → {@link VerdictType#INCONCLUSIVE} (unless a DANGEROUS signal was
     *       already found).  All JGDMS remote communication goes through JERI,
     *       whose endpoint (platform code) holds the {@code SocketPermission}
     *       under {@code doPrivileged}; a downloaded proxy JAR that asks to open
     *       its own socket is therefore talking to the network outside that
     *       machinery — <em>irrespective of whether it also ships a
     *       constrainable proxy</em>, because a proper proxy leaves the transport
     *       to JERI and would not need the permission itself.  This is not proof
     *       of malice (a <em>trusted</em> codebase may have a legitimate reason,
     *       e.g. an embedded-device integration), so it is not {@code DANGEROUS};
     *       but it is not confirmed safe either and needs a trust decision.</li>
     *   <li>Otherwise → {@link VerdictType#SAFE}</li>
     * </ol>
     *
     * @return the aggregate verdict; never {@code null}
     */
    public VerdictType deriveVerdictType() {
        boolean inconclusive = false;
        for (ClassAnalysisResult r : results.values()) {
            ClinitVerdict cv = r.getClinitVerdict();
            if (cv == ClinitVerdict.BLOCKING
                    || cv == ClinitVerdict.CYCLE
                    || cv == ClinitVerdict.BLOCKING_DECLARED) {
                return VerdictType.DANGEROUS;
            }
            AtomicSerialVerdict av = r.getAtomicVerdict();
            if (av == AtomicSerialVerdict.MISSING_CONSTRUCTOR
                    || av == AtomicSerialVerdict.VALIDATION_ORDER
                    || av == AtomicSerialVerdict.MISSING_SERIAL_FORM
                    || av == AtomicSerialVerdict.UNTYPED_GET) {
                return VerdictType.DANGEROUS;
            }
            // A smart proxy that implements java.io.Serializable is an
            // unvalidated deserialization path (readObject/default) with no
            // legitimate use on the wire — dangerous outright.
            if (r.getConstrainableProxyVerdict() == ConstrainableProxyVerdict.JAVA_SERIALIZATION) {
                return VerdictType.DANGEROUS;
            }
            if (cv == ClinitVerdict.NATIVE_OPACITY
                    || cv == ClinitVerdict.BLOCKING_GUARDED
                    || av == AtomicSerialVerdict.NOT_ANNOTATED) {
                inconclusive = true;
            }
        }
        // A JAR that asks to open its own network connection is talking to the
        // network outside JERI (which normally owns socket access).  Legitimate
        // for a trusted codebase, so not DANGEROUS — but never confirmed safe.
        if (requestsRawNetworkConnection()) {
            inconclusive = true;
        }
        return inconclusive ? VerdictType.INCONCLUSIVE : VerdictType.SAFE;
    }

    /**
     * Returns {@code true} if any {@code META-INF/PERMISSIONS.LIST} line requests
     * a {@link java.net.SocketPermission} — i.e. the JAR asks to open a raw
     * network connection of its own.
     */
    private boolean requestsRawNetworkConnection() {
        final String prefix = "permission java.net.SocketPermission";
        for (String line : declaredPermissions) {
            if (line.startsWith(prefix)) {
                if (line.length() == prefix.length()) return true;
                char c = line.charAt(prefix.length());
                if (c == ' ' || c == '\t' || c == '"' || c == ';') return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "JarAnalysisReport{contentHash='" + contentHash
                + "', classCount=" + results.size()
                + ", verdict=" + deriveVerdictType()
                + ", declaredPermissions=" + declaredPermissions.length
                + ", codebaseUrls=" + codebaseUrls.length + '}';
    }
}
