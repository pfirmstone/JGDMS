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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.Valid;

/**
 * Immutable, serializable analysis result for a single class within a JAR.
 *
 * <p>A {@code ClassAnalysisResult} records:
 * <ul>
 *   <li>the internal binary class name (using {@code /} separators),</li>
 *   <li>the {@link ClinitVerdict} from the static-initializer blocking
 *       analysis,</li>
 *   <li>the {@link AtomicSerialVerdict} from the {@code @AtomicSerial}
 *       compliance check,</li>
 *   <li>if {@link ClinitVerdict#BLOCKING}: the call chain from
 *       {@code <clinit>} to the blocking sink,</li>
 *   <li>if {@link ClinitVerdict#CYCLE}: the set of class names that form the
 *       initialisation cycle.</li>
 * </ul>
 *
 * <p>These per-class results are aggregated into a {@link JarAnalysisReport}
 * by the {@link BytecodeAnalysisEngine}.
 *
 * @see JarAnalysisReport
 * @see ClinitVerdict
 * @see AtomicSerialVerdict
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
@AtomicSerial
public final class ClassAnalysisResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String CLASS_NAME        = "className";
    private static final String CLINIT_VERDICT     = "clinitVerdict";
    private static final String ATOMIC_VERDICT     = "atomicVerdict";
    private static final String BLOCKING_CALL_PATH = "blockingCallPath";
    private static final String CYCLE_PARTICIPANTS  = "cycleParticipants";

    @SuppressWarnings("unused")
    private static final ObjectStreamField[] serialPersistentFields = serialForm();

    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm(CLASS_NAME,        String.class),
            new SerialForm(CLINIT_VERDICT,     ClinitVerdict.class),
            new SerialForm(ATOMIC_VERDICT,     AtomicSerialVerdict.class),
            new SerialForm(BLOCKING_CALL_PATH, String[].class),
            new SerialForm(CYCLE_PARTICIPANTS,  String[].class)
        };
    }

    public static void serialize(PutArg arg, ClassAnalysisResult r) throws IOException {
        arg.put(CLASS_NAME,        r.className);
        arg.put(CLINIT_VERDICT,     r.clinitVerdict);
        arg.put(ATOMIC_VERDICT,     r.atomicVerdict);
        arg.put(BLOCKING_CALL_PATH, r.blockingCallPath.toArray(new String[0]));
        arg.put(CYCLE_PARTICIPANTS,  r.cycleParticipants.toArray(new String[0]));
        arg.writeArgs();
    }

    private static boolean check(GetArg arg) throws IOException, ClassNotFoundException {
        if (arg.get(CLASS_NAME, null, String.class) == null)
            throw new InvalidObjectException("className must not be null");
        if (arg.get(CLINIT_VERDICT, null, ClinitVerdict.class) == null)
            throw new InvalidObjectException("clinitVerdict must not be null");
        if (arg.get(ATOMIC_VERDICT, null, AtomicSerialVerdict.class) == null)
            throw new InvalidObjectException("atomicVerdict must not be null");
        String[] bcp = (String[]) arg.get(BLOCKING_CALL_PATH, null);
        if (bcp != null) Valid.nullElement(bcp, "blockingCallPath must not contain null elements");
        String[] cp = (String[]) arg.get(CYCLE_PARTICIPANTS, null);
        if (cp != null) Valid.nullElement(cp, "cycleParticipants must not contain null elements");
        return true;
    }

    /** Internal binary class name, using {@code /} separators. */
    private final String className;

    /** Verdict from the {@code <clinit>} blocking analysis. */
    private final ClinitVerdict clinitVerdict;

    /** Verdict from the {@code @AtomicSerial} compliance check. */
    private final AtomicSerialVerdict atomicVerdict;

    /**
     * Call chain from {@code <clinit>} to the blocking sink, expressed as
     * {@code "owner/name/descriptor"} triples.  Non-empty when
     * {@link #clinitVerdict} is {@link ClinitVerdict#BLOCKING} or
     * {@link ClinitVerdict#BLOCKING_GUARDED}.
     */
    private final List<String> blockingCallPath;

    /**
     * Set of internal class names that form the {@code <clinit>} cycle.
     * Non-empty only when {@link #clinitVerdict} is {@link ClinitVerdict#CYCLE}.
     */
    private final List<String> cycleParticipants;

    /**
     * {@link AtomicSerial} deserialization constructor.
     *
     * @param arg the deserialization argument bag
     * @throws IOException            if validation fails
     * @throws ClassNotFoundException if a required class is not found
     */
    public ClassAnalysisResult(GetArg arg) throws IOException, ClassNotFoundException {
        this(arg, check(arg));
    }

    private ClassAnalysisResult(GetArg arg, boolean checked) throws IOException, ClassNotFoundException {
        className      = (String) arg.get(CLASS_NAME, null);
        clinitVerdict  = arg.get(CLINIT_VERDICT, null, ClinitVerdict.class);
        atomicVerdict  = arg.get(ATOMIC_VERDICT, null, AtomicSerialVerdict.class);
        String[] bcp   = (String[]) arg.get(BLOCKING_CALL_PATH, null);
        blockingCallPath = (bcp != null && bcp.length > 0)
                ? Collections.unmodifiableList(toList(bcp))
                : Collections.<String>emptyList();
        String[] cp    = (String[]) arg.get(CYCLE_PARTICIPANTS, null);
        cycleParticipants = (cp != null && cp.length > 0)
                ? Collections.unmodifiableList(toList(cp))
                : Collections.<String>emptyList();
    }

    /**
     * Constructs a {@code ClassAnalysisResult}.
     *
     * @param className        internal binary class name; must be non-null
     * @param clinitVerdict    {@code <clinit>} blocking verdict; must be non-null
     * @param atomicVerdict    {@code @AtomicSerial} compliance verdict; must be non-null
     * @param blockingCallPath call chain to the blocking sink, or empty list;
     *                         must be non-null
     * @param cycleParticipants class names in the {@code <clinit>} cycle, or
     *                         empty list; must be non-null
     * @throws NullPointerException if any argument is {@code null}
     */
    public ClassAnalysisResult(String className,
                                ClinitVerdict clinitVerdict,
                                AtomicSerialVerdict atomicVerdict,
                                List<String> blockingCallPath,
                                List<String> cycleParticipants) {
        if (className == null)         throw new NullPointerException("className");
        if (clinitVerdict == null)     throw new NullPointerException("clinitVerdict");
        if (atomicVerdict == null)     throw new NullPointerException("atomicVerdict");
        if (blockingCallPath == null)  throw new NullPointerException("blockingCallPath");
        if (cycleParticipants == null) throw new NullPointerException("cycleParticipants");

        this.className         = className;
        this.clinitVerdict     = clinitVerdict;
        this.atomicVerdict     = atomicVerdict;
        this.blockingCallPath  = Collections.unmodifiableList(new ArrayList<String>(blockingCallPath));
        this.cycleParticipants = Collections.unmodifiableList(new ArrayList<String>(cycleParticipants));
    }

    /** Returns the internal binary class name (with {@code /} separators). */
    public String getClassName() { return className; }

    /**
     * Returns the verdict from the {@code <clinit>} blocking analysis.
     *
     * @return never {@code null}
     */
    public ClinitVerdict getClinitVerdict() { return clinitVerdict; }

    /**
     * Returns the verdict from the {@code @AtomicSerial} compliance check.
     *
     * @return never {@code null}
     */
    public AtomicSerialVerdict getAtomicVerdict() { return atomicVerdict; }

    /**
     * Returns the call chain from {@code <clinit>} to the blocking sink.
     * Each element is an {@code "owner/name/descriptor"} triple.
     * Non-empty only when {@link #getClinitVerdict()} is
     * {@link ClinitVerdict#BLOCKING}.
     *
     * @return unmodifiable, possibly empty list; never {@code null}
     */
    public List<String> getBlockingCallPath() { return blockingCallPath; }

    /**
     * Returns the set of class names that form the {@code <clinit>}
     * initialisation cycle.  Non-empty only when {@link #getClinitVerdict()}
     * is {@link ClinitVerdict#CYCLE}.
     *
     * @return unmodifiable, possibly empty list; never {@code null}
     */
    public List<String> getCycleParticipants() { return cycleParticipants; }

    @Override
    public String toString() {
        return "ClassAnalysisResult{className='" + className
                + "', clinit=" + clinitVerdict
                + ", atomic=" + atomicVerdict + '}';
    }

    private static List<String> toList(String[] arr) {
        List<String> list = new ArrayList<String>(arr.length);
        for (String s : arr) list.add(s);
        return list;
    }
}
