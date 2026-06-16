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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
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
 * The caller (typically the Codebase Downloader, Host 4) converts a
 * {@code JarAnalysisReport} to a {@link SignedVerdict} before submitting
 * to the {@link VerdictRegistry}.  The mapping is:
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

    private static final long serialVersionUID = 2L;

    private static final String CONTENT_HASH         = "contentHash";
    private static final String CLASS_NAMES           = "classNames";
    private static final String CLASS_RESULTS         = "classResults";
    private static final String ENGINE_SIGNATURE      = "engineSignature";
    private static final String DECLARED_PERMISSIONS  = "declaredPermissions";

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
            new SerialForm(DECLARED_PERMISSIONS, String[].class)
        };
    }

    public static void serialize(PutArg arg, JarAnalysisReport r) throws IOException {
        int size = r.results.size();
        String[] classNames   = new String[size];
        ClassAnalysisResult[] classResults = new ClassAnalysisResult[size];
        int i = 0;
        for (Map.Entry<String, ClassAnalysisResult> e : r.results.entrySet()) {
            classNames[i]   = e.getKey();
            classResults[i] = e.getValue();
            i++;
        }
        arg.put(CONTENT_HASH,         r.contentHash);
        arg.put(CLASS_NAMES,          classNames);
        arg.put(CLASS_RESULTS,        classResults);
        arg.put(ENGINE_SIGNATURE,     r.engineSignature.clone());
        arg.put(DECLARED_PERMISSIONS, r.declaredPermissions.clone());
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
        return true;
    }

    /** SHA-256 hex digest of the analysed JAR. */
    private final String contentHash;

    /**
     * Per-class analysis results, keyed by internal binary class name
     * ({@code /}-separated).  Insertion order is preserved.
     */
    private final Map<String, ClassAnalysisResult> results;

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
        String[] classNames   = (String[]) arg.get(CLASS_NAMES, null);
        ClassAnalysisResult[] classResults =
                (ClassAnalysisResult[]) arg.get(CLASS_RESULTS, null);
        Map<String, ClassAnalysisResult> map =
                new LinkedHashMap<String, ClassAnalysisResult>(classNames.length * 2);
        for (int i = 0; i < classNames.length; i++) {
            map.put(classNames[i], classResults[i]);
        }
        results          = Collections.unmodifiableMap(map);
        engineSignature  = ((byte[]) arg.get(ENGINE_SIGNATURE, null)).clone();
        // declaredPermissions may be absent in older reports → treat as empty
        String[] dp = (String[]) arg.get(DECLARED_PERMISSIONS, null);
        declaredPermissions = (dp != null) ? dp.clone() : new String[0];
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
        this(contentHash, results, engineSignature, new String[0]);
    }

    /**
     * Constructs a {@code JarAnalysisReport} with declared permissions.
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

        this.contentHash          = contentHash;
        this.results              = Collections.unmodifiableMap(
                new LinkedHashMap<String, ClassAnalysisResult>(results));
        this.engineSignature      = engineSignature.clone();
        this.declaredPermissions  = declaredPermissions.clone();
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
     *   <li>Any {@link ClinitVerdict#NATIVE_OPACITY},
     *       {@link ClinitVerdict#BLOCKING_GUARDED}, or
     *       {@link AtomicSerialVerdict#NOT_ANNOTATED}
     *       → {@link VerdictType#INCONCLUSIVE} (unless a DANGEROUS signal was
     *       already found). {@code BLOCKING_GUARDED} is inconclusive because
     *       the blocking path is only reachable when the caller holds the
     *       guarding permission; a policy that denies that permission prevents
     *       the block.</li>
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
            if (cv == ClinitVerdict.NATIVE_OPACITY
                    || cv == ClinitVerdict.BLOCKING_GUARDED
                    || av == AtomicSerialVerdict.NOT_ANNOTATED) {
                inconclusive = true;
            }
        }
        return inconclusive ? VerdictType.INCONCLUSIVE : VerdictType.SAFE;
    }

    @Override
    public String toString() {
        return "JarAnalysisReport{contentHash='" + contentHash
                + "', classCount=" + results.size()
                + ", verdict=" + deriveVerdictType()
                + ", declaredPermissions=" + declaredPermissions.length + '}';
    }
}
