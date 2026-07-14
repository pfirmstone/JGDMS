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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import au.net.zeus.jgdms.api.codebase.AtomicSerialVerdict;

/**
 * ASM-based visitor that checks a single class for
 * {@link org.apache.river.api.io.AtomicSerial} protocol compliance.
 *
 * <h2>Compliance rules</h2>
 * <p>For a class to be {@link AtomicSerialVerdict#COMPLIANT}:
 * <ol>
 *   <li>It must be annotated with
 *       {@code @org.apache.river.api.io.AtomicSerial}.</li>
 *   <li>It must have a constructor with descriptor
 *       {@code (Lorg/apache/river/api/io/AtomicSerial$GetArg;)V}.</li>
 *   <li>The {@code (GetArg)} constructor must exhibit <em>genuine</em>
 *       validation before it calls {@code super(...)}/{@code this(...)}: it
 *       is not enough for <em>some</em> {@code INVOKESTATIC} call to appear
 *       before that call (that would accept e.g.
 *       {@code super(Objects.requireNonNull(arg))}, which validates
 *       nothing) — nor is it enough for a value read from {@code GetArg}
 *       <em>via the untyped 2-argument {@code get(String, Object)} form</em>
 *       to merely be <em>touched</em> by some further call whose own result
 *       is then thrown away, e.g.
 *       {@code check(GetArg arg){ String.valueOf(arg.get("f", null)); return
 *       true; }}: the {@code String.valueOf} call happens, but the method's
 *       outcome (a hard-coded {@code true}) does not actually depend on it,
 *       and the untyped form has no built-in type check of its own to fall
 *       back on.  Two evidence forms are accepted, mirroring the two
 *       validation idioms already used by real {@code @AtomicSerial}
 *       classes in this codebase:
 *       <ul>
 *         <li><b>Dedicated check method</b> — a static method whose
 *             descriptor includes the {@code GetArg} type as a parameter
 *             (e.g. {@code check(GetArg)}, regardless of return type) is
 *             called before {@code super(...)}/{@code this(...)}, <em>and</em>
 *             either (a) a value derived from that method's own read of its
 *             {@code GetArg} parameter (a {@code get(...)}/{@code
 *             validateInvariants(...)} call, or a field read from a
 *             privately-constructed copy of the enclosing class) —
 *             possibly after flowing through one or more further calls —
 *             actually reaches a {@code return}, a conditional branch, a
 *             {@code CHECKCAST}, or an {@code instanceof} check (a call
 *             whose own result is immediately discarded contributes
 *             nothing on its own: the taint has to reach an observable
 *             outcome, not just be passed somewhere); <em>or</em> (b) the
 *             read itself is the typed 3-argument
 *             {@code get(name, default, Class)} form with a
 *             meaningfully-constraining declared type (i.e. not
 *             {@code Object.class}) — this throws internally if the field
 *             is present but its runtime class does not match the
 *             declaration, so the call is self-validating <em>on its own</em>
 *             regardless of whether the result is subsequently used.
 *             Deliberately <em>not</em> extended to primitive overloads
 *             ({@code get(String,int)}, {@code get(String,long)}, …): by
 *             the time a check method runs, the wire decoder has already
 *             committed the field to its declared primitive wire type, so
 *             there is no "wrong runtime class" for a primitive read to
 *             protect against the way there is for an Object-typed slot —
 *             see {@link CheckMethodAnalyzer#isSelfValidatingGetForm} for
 *             the exact boundary, the real-class false positive it fixes,
 *             the real-class confirmed bug that ruled out extending it to
 *             primitives, and the disclosed "right field, not just a
 *             field" residual limitation this does <em>not</em> attempt to
 *             close. The field-name argument must additionally be a
 *             compile-time-constant String literal that this class's own
 *             {@code serialForm()} actually declares — a typed 3-argument
 *             call naming a field absent from the declared schema can
 *             <em>never</em> throw regardless of type (see
 *             {@code GetArg.get(String,T,Class&lt;T&gt;)}'s
 *             absent-field-returns-default-before-the-type-check shape),
 *             so it is not self-validating at all; this is closed by
 *             cross-checking against {@link SerialFormFieldNameCollector}'s
 *             output — see {@code isSelfValidatingGetForm}'s javadoc for
 *             the full "field-name-absent" bypass this closes.</li>
 *         <li><b>Inline extraction</b> — the constructor itself calls
 *             {@code GetArg.get(...)} directly and the (possibly
 *             {@code CHECKCAST}-narrowed) result is consumed by at least one
 *             call this codebase could plausibly have authored — any call
 *             whose owner is <em>not</em> a JDK class ({@code java.*}/
 *             {@code javax.*}) or an array type (e.g.
 *             {@code this(validate(arg.get("x", null, T.class)), ...)},
 *             where {@code validate} may be a private static helper of the
 *             class being analysed, <em>or</em> a shared utility declared
 *             elsewhere in this codebase, such as
 *             {@code org.apache.river.api.io.Valid.notNull(...)} — a real
 *             idiom several real classes here use — any call type, not just
 *             {@code INVOKESTATIC}; the value may pass through one or more
 *             external/JDK calls first — e.g. {@code Arrays.asList(...)} —
 *             on its way to that internal call, as long as an internal call
 *             appears <em>somewhere</em> in the chain).  A bare, unprocessed
 *             pass-through straight into {@code super(...)}/{@code
 *             this(...)} — such as {@code this(arg.get("ttl", -1))} — is
 *             <em>not</em> accepted: merely extracting a field is not
 *             validating it, and crediting it would make this rule vacuous
 *             for every {@code (GetArg)} constructor.  Note this is stricter
 *             than the dedicated-check-method form above: inline extraction
 *             never credits a typed/primitive {@code get(...)} call on its
 *             own, however meaningfully typed, precisely because it would
 *             otherwise make the rule vacuous the same way (every
 *             constructor extracts its fields via some {@code get(...)}
 *             overload; only the dedicated-check-method context carries the
 *             independent "written on purpose" signal that justifies
 *             crediting the read alone).  Routing the same bare value
 *             through JDK/array calls <em>only</em>, with no internal call
 *             anywhere in the chain — e.g. {@code this(arg,
 *             String.valueOf(arg.get("f", null, String.class)))} with a
 *             bridge constructor that ignores its second parameter — is
 *             <em>also</em> not accepted: an opaque JDK call cannot be
 *             verified to do anything with its argument (and never contains
 *             project-specific validation logic by construction), so it
 *             must not by itself count as evidence, though it does not
 *             sever the chain either — a genuine internal call later in the
 *             same chain still gets credit.  Evidence from an internal call
 *             is credited the moment the call happens, regardless of
 *             whether its own return value is later used — see
 *             {@link CheckMethodAnalyzer#visitMethodInsn} for why that is
 *             both sufficient to reject the bypass above and necessary to
 *             correctly handle real constructors that pass <em>multiple</em>
 *             independently-computed arguments to their terminal call. An
 *             internal call this codebase could plausibly have authored
 *             includes an object-construction call — e.g. {@code new
 *             ValidatingWrapper(arg.get(...))} — provided it is not the
 *             enclosing method's own terminal {@code super(...)}/{@code
 *             this(...)} call, which is never itself evidence; the two are
 *             told apart structurally, not by owner (the terminal call's
 *             owner is legitimately this class or its own superclass) but
 *             by <em>receiver</em>: the terminal call's receiver is always
 *             {@code ALOAD 0} (the caller-allocated {@code this}), never a
 *             {@code NEW} from within this method's own body — see
 *             {@link CheckMethodAnalyzer#popReceiverIsThis} for the exact
 *             mechanism and the proven, verifier-legal bypass (an orphaned
 *             {@code NEW; POP} pair) that ruled out a simpler counting
 *             approach.</li>
 *       </ul>
 *       See {@link CheckMethodAnalyzer} and {@link GetArgCtorAnalyzer} for
 *       the bytecode-level detail.</li>
 *   <li>Whichever static check method is credited under the "dedicated
 *       check method" form above, <em>if</em> it has descriptor
 *       {@code (GetArg)Z} it is additionally scrutinised for type-safety:
 *       calling the 2-argument {@code GetArg.get(String, Object)} form and
 *       then consuming the result (directly, via a local-variable
 *       store/reload round trip, or by passing it to another call) without
 *       an intervening {@code CHECKCAST}/{@code instanceof} is flagged as
 *       {@link AtomicSerialVerdict#UNTYPED_GET}: the type is only verified
 *       in the bridge constructor, where a CCE can fire <em>during</em>
 *       construction.  The safe patterns are either the typed 3-argument
 *       form {@code arg.get(name, null, MyType.class)} or an explicit cast
 *       {@code (MyType) arg.get(name, null)} within the check method.</li>
 *   <li>It must have a {@code public static SerialForm[] serialForm()}
 *       method (unless it is annotated {@code @Stateless} or declares no
 *       non-static instance fields).</li>
 *   <li>It must have a {@code public static void serialize(PutArg, T)}
 *       method, where {@code T} is the class itself (same exemption as
 *       {@code serialForm()}).</li>
 * </ol>
 *
 * <p>If the class is <em>not</em> {@code Serializable} and does not have a
 * {@code (GetArg)} constructor, the result is
 * {@link AtomicSerialVerdict#NA}.
 *
 * <p>Thread safety: a new {@code AtomicSerialComplianceVisitor} must be
 * created for each class to analyse.
 *
 * @see JarAnalyzer
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
final class AtomicSerialComplianceVisitor extends ClassVisitor {

    private static final int ASM_API = Opcodes.ASM9;

    // ASM internal descriptor for AtomicSerial annotation
    private static final String ATOMIC_SERIAL_DESC =
            "Lorg/apache/river/api/io/AtomicSerial;";
    private static final String STATELESS_DESC =
            "Lorg/apache/river/api/io/AtomicSerial$Stateless;";

    // AtomicSerial GetArg / PutArg internal type descriptors
    static final String GET_ARG_TYPE_DESC =
            "Lorg/apache/river/api/io/AtomicSerial$GetArg;";
    private static final String PUT_ARG_TYPE_DESC =
            "Lorg/apache/river/api/io/AtomicSerial$PutArg;";

    // AtomicSerial GetArg constructor descriptor
    private static final String GET_ARG_CTOR_DESC =
            "(" + GET_ARG_TYPE_DESC + ")V";

    // Serializable internal name
    private static final String SERIALIZABLE_INTERNAL =
            "java/io/Serializable";

    // -------------------------------------------------------------------------
    // State accumulated during visiting
    // -------------------------------------------------------------------------

    private boolean hasAtomicSerialAnnotation      = false;
    private boolean hasStatelessAnnotation          = false;
    private boolean implementsSerializable          = false;
    private boolean hasGetArgConstructor            = false;
    private boolean hasSerialFormMethod             = false;
    private boolean hasSerializeMethod              = false;

    /** Internal (slash-separated) binary name of the class being analysed,
     *  captured from {@link #visit} — needed to build the expected
     *  {@code serialize(PutArg, T)} descriptor, since {@code T} is the
     *  class itself. */
    private String classInternalName = null;

    /**
     * {@code true} if the class declares at least one non-static,
     * non-transient instance field.  A class with no such fields does not
     * need its own {@code serialForm()}/{@code serialize()} because it
     * contributes no new serialized state beyond what its superclass(es)
     * already describe.
     */
    private boolean hasNonStaticInstanceFields      = false;

    /**
     * The {@link GetArgCtorAnalyzer} that visited the {@code (GetArg)}
     * constructor, retained so its results (computed once the whole class
     * has been visited) can be read from {@link #computeVerdict()}.
     */
    private GetArgCtorAnalyzer ctorAnalyzer = null;

    /**
     * Results of running {@link CheckMethodAnalyzer} on every static method
     * whose descriptor includes a {@code GetArg} parameter (candidates for
     * the "dedicated check method" validation-ordering evidence, see class
     * javadoc).  Keyed by {@code name + "\0" + descriptor}.
     */
    private final Map<String, CheckMethodAnalyzer> checkMethodAnalyzers =
            new HashMap<String, CheckMethodAnalyzer>();

    /**
     * Field-name string literals declared by this class's own {@code
     * serialForm()} method — i.e. every {@code name} argument of a {@code
     * new SerialForm(name, type[, unshared])} entry in the array it
     * returns, collected by {@link SerialFormFieldNameCollector}.  Consulted
     * by {@link CheckMethodAnalyzer#isSelfValidatingGetForm} (Fix 1): a
     * typed 3-argument {@code get(name, default, Class)} call is only
     * inherently self-validating for a field this class's own declared
     * schema actually admits — see that method's javadoc for the proven
     * "field-name-absent" bypass this closes. Stays empty (never populated)
     * for a class with no {@code serialForm()} method at all (e.g.
     * {@code @Stateless}), which correctly means the self-validating credit
     * path can never fire for such a class.
     */
    private final Set<String> declaredSerialFormFieldNames = new HashSet<String>();

    // -------------------------------------------------------------------------
    // Public factory
    // -------------------------------------------------------------------------

    private AtomicSerialComplianceVisitor() {
        super(ASM_API);
    }

    /**
     * Analyses {@code classBytes} and returns the
     * {@link AtomicSerialVerdict} for that class.
     *
     * @param classBytes raw bytes of a single {@code .class} entry; must be
     *                   non-null
     * @return the compliance verdict; never {@code null}
     */
    static AtomicSerialVerdict analyze(byte[] classBytes) {
        if (classBytes == null) throw new NullPointerException("classBytes");
        AtomicSerialComplianceVisitor v = new AtomicSerialComplianceVisitor();
        try {
            ClassReader cr = new ClassReader(classBytes);
            cr.accept(v, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        } catch (Throwable e) {
            // ASM parse failure (including Errors such as StackOverflowError
            // from a crafted deeply-nested/recursive class structure) —
            // conservative: treat as MISSING_CONSTRUCTOR since we cannot
            // verify anything.  Fail-secure regardless of failure kind: this
            // method already returns a terminal verdict from the catch block,
            // so widening from Exception to Throwable introduces no
            // partial-state hazard.
            return AtomicSerialVerdict.MISSING_CONSTRUCTOR;
        }
        return v.computeVerdict();
    }

    // -------------------------------------------------------------------------
    // ClassVisitor overrides
    // -------------------------------------------------------------------------

    @Override
    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
        classInternalName = name;
        if (interfaces != null) {
            for (String iface : interfaces) {
                if (SERIALIZABLE_INTERNAL.equals(iface)) {
                    implementsSerializable = true;
                }
            }
        }
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor,
                                                                boolean visible) {
        if (ATOMIC_SERIAL_DESC.equals(descriptor)) {
            hasAtomicSerialAnnotation = true;
        }
        if (STATELESS_DESC.equals(descriptor)) {
            hasStatelessAnnotation = true;
        }
        return super.visitAnnotation(descriptor, visible);
    }

    @Override
    public org.objectweb.asm.FieldVisitor visitField(int access, String name,
                                                      String descriptor,
                                                      String signature,
                                                      Object value) {
        // Track whether this class declares any non-static, non-transient
        // instance fields.  Such a class is expected to supply its own
        // serialForm()/serialize() to declare/encode the serial form of
        // those fields.  A class with no non-static fields (e.g. a
        // delegation-only proxy subclass) inherits its serial form entirely
        // from the superclass and does not need either.
        if ((access & Opcodes.ACC_STATIC)    == 0
                && (access & Opcodes.ACC_TRANSIENT) == 0) {
            hasNonStaticInstanceFields = true;
        }
        return super.visitField(access, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                      String signature, String[] exceptions) {
        if ("<init>".equals(name) && GET_ARG_CTOR_DESC.equals(descriptor)) {
            hasGetArgConstructor = true;
            // (GetArg) constructors are always instance methods: slot 0 is
            // `this`, slot 1 is the GetArg parameter.
            GetArgCtorAnalyzer analyzer = new GetArgCtorAnalyzer(ASM_API);
            ctorAnalyzer = analyzer;
            return analyzer;
        }

        // Check for: public static SerialForm[] serialForm()
        if ("serialForm".equals(name)
                && "()[Lorg/apache/river/api/io/AtomicSerial$SerialForm;".equals(descriptor)
                && (access & Opcodes.ACC_STATIC) != 0
                && (access & Opcodes.ACC_PUBLIC) != 0) {
            hasSerialFormMethod = true;
            // Fix 1: parse the field names this class's own schema actually
            // declares -- consulted by CheckMethodAnalyzer#isSelfValidatingGetForm
            // to reject a typed 3-arg get() call that names a field absent
            // from the declared schema (see SerialFormFieldNameCollector and
            // that method's javadoc for the bypass this closes).
            return new SerialFormFieldNameCollector(ASM_API, declaredSerialFormFieldNames);
        }

        // Check for: public static void serialize(PutArg, T) where T is
        // this class itself — the encode-side counterpart of the (GetArg)
        // constructor.  COMPLIANT's own javadoc requires both.
        if ("serialize".equals(name)
                && classInternalName != null
                && ("(" + PUT_ARG_TYPE_DESC + "L" + classInternalName + ";)V").equals(descriptor)
                && (access & Opcodes.ACC_STATIC) != 0
                && (access & Opcodes.ACC_PUBLIC) != 0) {
            hasSerializeMethod = true;
        }

        // Collect check-method candidates: static methods whose descriptor
        // includes a GetArg parameter, of any return type.  Every one of
        // these is deliberately typed to accept AtomicSerial.GetArg, which
        // is not something written by accident — it is at minimum plausible
        // "check-method-shaped" evidence.  Whether a given candidate is
        // actually *credited* as genuine validation evidence (as opposed to
        // merely being collected here) is decided later, per-candidate, from
        // its own analysed body — see CheckMethodAnalyzer#hasGenuineDependency.
        if ((access & Opcodes.ACC_STATIC) != 0
                && descriptor.contains(GET_ARG_TYPE_DESC)) {
            int trackedSlot = staticGetArgParamSlot(descriptor);
            if (trackedSlot >= 0) {
                CheckMethodAnalyzer cma = new CheckMethodAnalyzer(ASM_API, trackedSlot, false);
                checkMethodAnalyzers.put(name + "\0" + descriptor, cma);
                return cma;
            }
        }

        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
    }

    /**
     * Returns the local-variable slot occupied by the (first) {@code GetArg}
     * parameter of a <em>static</em> method with the given descriptor, or
     * {@code -1} if none is present.  Static methods have no implicit
     * {@code this}, so parameter slots start at 0; wide parameter types
     * (long/double) occupy two slots, hence the use of {@link Type#getSize()}
     * rather than a flat index.
     */
    private static int staticGetArgParamSlot(String descriptor) {
        Type[] argTypes = Type.getArgumentTypes(descriptor);
        int slot = 0;
        for (Type t : argTypes) {
            if (GET_ARG_TYPE_DESC.equals(t.getDescriptor())) {
                return slot;
            }
            slot += t.getSize();
        }
        return -1;
    }

    // -------------------------------------------------------------------------
    // Inner method visitor: collects serialForm()'s declared field names
    // -------------------------------------------------------------------------

    /**
     * Collects the field-name string literals declared by a class's own
     * {@code serialForm()} method — each {@code new SerialForm(name, type[,
     * unshared])} array-initializer entry pushes its {@code name} argument
     * via a single {@code LDC} of a String constant immediately before the
     * matching {@code INVOKESPECIAL} of {@code SerialForm}'s own
     * constructor, e.g. the real compiled shape of
     * {@code net.jini.activation.ActivationGroupDescImpl.serialForm()}:
     * {@code NEW SerialForm; DUP; LDC "className"; LDC String.class;
     * INVOKESPECIAL SerialForm.<init>; AASTORE; ...}. Used by Fix 1's
     * field-name cross-check (see
     * {@link CheckMethodAnalyzer#isSelfValidatingGetForm}): a typed
     * 3-argument {@code get(name, default, Class)} call can only ever throw
     * on a runtime-class mismatch for a field the class's own declared
     * schema actually admits — see that method's javadoc for the full
     * rationale and the proven bypass this closes.
     *
     * <p>Lightweight, single-pass, and deliberately approximate in the same
     * spirit as {@link CheckMethodAnalyzer}: it does not verify the {@code
     * SerialForm} array is actually {@code return}ed, nor does it simulate a
     * real operand stack — it simply records "the most recently pushed
     * String constant" at each {@code SerialForm.<init>} call site, which is
     * exactly right for every real {@code serialForm()} method in this
     * codebase (the name argument is always the sole String literal
     * immediately preceding the constructor call; the type argument is a
     * {@code Class} literal, and the optional {@code unshared} argument is a
     * boolean constant, neither of which is ever itself a String).
     */
    private static final class SerialFormFieldNameCollector extends MethodVisitor {

        private static final String SERIAL_FORM_INTERNAL_NAME =
                "org/apache/river/api/io/AtomicSerial$SerialForm";

        private final Set<String> declaredFieldNames;

        private String pendingNameLiteral = null;

        SerialFormFieldNameCollector(int api, Set<String> declaredFieldNames) {
            super(api);
            this.declaredFieldNames = declaredFieldNames;
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof String) {
                pendingNameLiteral = (String) value;
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                     String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESPECIAL
                    && "<init>".equals(name)
                    && SERIAL_FORM_INTERNAL_NAME.equals(owner)
                    && descriptor.startsWith("(Ljava/lang/String;Ljava/lang/Class;")) {
                if (pendingNameLiteral != null) {
                    declaredFieldNames.add(pendingNameLiteral);
                }
            }
            pendingNameLiteral = null;
        }
    }

    // -------------------------------------------------------------------------
    // Verdict derivation
    // -------------------------------------------------------------------------

    private AtomicSerialVerdict computeVerdict() {
        // If class has a GetArg constructor but no annotation
        if (hasGetArgConstructor && !hasAtomicSerialAnnotation) {
            return AtomicSerialVerdict.NOT_ANNOTATED;
        }

        // Not serializable and not annotated @AtomicSerial -> N/A
        if (!implementsSerializable && !hasAtomicSerialAnnotation) {
            return AtomicSerialVerdict.NA;
        }

        // @AtomicSerial present but no GetArg constructor
        if (hasAtomicSerialAnnotation && !hasGetArgConstructor) {
            return AtomicSerialVerdict.MISSING_CONSTRUCTOR;
        }

        // GetArg constructor exists — check validation ordering.  A @Stateless
        // class declares no own serialized fields, so there is nothing to
        // assign before validation (no finalizer-attack surface); the
        // validation-order rule is vacuously satisfied, exactly as the
        // serialForm()/serialize() requirements below are skipped for
        // @Stateless.  Such a subclass legitimately delegates validation to
        // its (validated) superclass via super(arg).
        String zShapedGenuineCandidateKey = null;
        if (hasGetArgConstructor && !hasStatelessAnnotation) {
            boolean orderingGenuine = ctorAnalyzer != null && isGenuine(ctorAnalyzer);
            if (ctorAnalyzer != null) {
                for (String key : ctorAnalyzer.preSuperCandidateKeys) {
                    CheckMethodAnalyzer cma = checkMethodAnalyzers.get(key);
                    if (cma == null || !isGenuine(cma)) continue;
                    orderingGenuine = true;
                    if (zShapedGenuineCandidateKey == null && descriptorOf(key).endsWith(")Z")) {
                        zShapedGenuineCandidateKey = key;
                    }
                }
            }
            if (!orderingGenuine) {
                return AtomicSerialVerdict.VALIDATION_ORDER;
            }
        }

        // Check the identified (GetArg)Z-shaped, genuinely-validating check
        // method for untyped GetArg access.  Candidates that were credited
        // for ordering purposes but do not have this exact shape (e.g. a
        // `static Object check(GetArg)` or `static long mostSig(GetArg)`
        // idiom used by several real classes in this codebase) are — as
        // before — not scrutinised for UNTYPED_GET at all.
        if (zShapedGenuineCandidateKey != null) {
            CheckMethodAnalyzer cma = checkMethodAnalyzers.get(zShapedGenuineCandidateKey);
            if (cma != null && cma.untypedGetFound) {
                return AtomicSerialVerdict.UNTYPED_GET;
            }
        }

        // Check serialForm() unless @Stateless or the class has no non-static
        // instance fields. A class with no non-static fields (e.g. a
        // delegation-only proxy subclass that adds no new serialized state)
        // inherits its serial form entirely from the superclass and is not
        // required to supply its own serialForm().
        if (hasAtomicSerialAnnotation && !hasStatelessAnnotation
                && !hasSerialFormMethod && hasNonStaticInstanceFields) {
            return AtomicSerialVerdict.MISSING_SERIAL_FORM;
        }

        // Check serialize(PutArg, T) under the identical exemption as
        // serialForm() above — the encode-side counterpart COMPLIANT's own
        // javadoc requires.
        if (hasAtomicSerialAnnotation && !hasStatelessAnnotation
                && !hasSerializeMethod && hasNonStaticInstanceFields) {
            return AtomicSerialVerdict.MISSING_SERIALIZE;
        }

        // All good (or @Stateless)
        if (hasAtomicSerialAnnotation) {
            return AtomicSerialVerdict.COMPLIANT;
        }

        // Serializable but no @AtomicSerial annotation -> N/A
        return AtomicSerialVerdict.NA;
    }

    /** Extracts the descriptor half of a {@code name + "\0" + descriptor} key. */
    private static String descriptorOf(String key) {
        int i = key.indexOf('\0');
        return i < 0 ? "" : key.substring(i + 1);
    }

    /**
     * Returns {@code true} if {@code cma} has genuine validation evidence —
     * either the direct kind ({@link CheckMethodAnalyzer#hasGenuineDependency},
     * decided eagerly while the method body was visited), <em>or</em> Fix
     * 1's deferred self-validating-typed-get credit: a candidate field name
     * from {@link CheckMethodAnalyzer#selfValidatingFieldNameCandidates}
     * that is actually declared by this class's own {@code serialForm()}
     * (see {@link #declaredSerialFormFieldNames}). This second check must
     * be deferred to here, after the whole class has been visited, because
     * {@code serialForm()} may be visited before <em>or</em> after the
     * check method / constructor within the same class file — ASM visits
     * methods in class-file declaration order, not an order this analyser
     * controls.
     */
    private boolean isGenuine(CheckMethodAnalyzer cma) {
        if (cma.hasGenuineDependency) return true;
        for (String candidate : cma.selfValidatingFieldNameCandidates) {
            if (declaredSerialFormFieldNames.contains(candidate)) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Inner method visitor: checks validation-before-super in (GetArg) ctor
    // -------------------------------------------------------------------------

    /**
     * Analyses the {@code (GetArg)} constructor body.
     *
     * <p>It performs two jobs simultaneously:
     * <ol>
     *   <li>It extends {@link CheckMethodAnalyzer} (tracked slot 1, the
     *       {@code GetArg} parameter of this instance method) to look for
     *       "inline extraction" evidence directly in the constructor's own
     *       body — see the class javadoc's "Inline extraction" bullet.  In
     *       strict mode ({@code strictIntermediateCallRequired=true}), a
     *       bare {@code CHECKCAST}, {@code instanceof}, or conditional
     *       branch is <em>never</em> enough by itself (see that flag on
     *       {@link CheckMethodAnalyzer}); the constructor's own {@code
     *       <init>} call is likewise never itself evidence — only a value
     *       consumed by an <em>internal</em> intermediate call (not {@code
     *       java.*}/{@code javax.*}, not an array built-in — see
     *       {@link CheckMethodAnalyzer#isExternalCallOwner}) counts, and
     *       that finalises immediately, exactly like the shared logic in
     *       {@link CheckMethodAnalyzer#visitMethodInsn} — see that method's
     *       comments for why immediate (rather than deferred-to-the-terminal-
     *       call) finalisation is both sufficient to close the proven
     *       bypasses and necessary to avoid losing evidence across
     *       multi-argument constructor calls.  All tracking stops once the
     *       terminal call is reached; nothing after that point is
     *       inspected.</li>
     *   <li>It records the name/descriptor of every {@code INVOKESTATIC}
     *       call, preceding that terminal call, whose descriptor includes a
     *       {@code GetArg} parameter — the "dedicated check method"
     *       candidates evaluated separately by the enclosing visitor's own
     *       {@link CheckMethodAnalyzer} instances (see
     *       {@link #preSuperCandidateKeys}).</li>
     * </ol>
     */
    private static final class GetArgCtorAnalyzer extends CheckMethodAnalyzer {

        private boolean seenTerminalCall = false;

        /** {@code name + "\0" + descriptor} for every pre-terminal-call
         *  {@code INVOKESTATIC} whose descriptor includes a GetArg parameter. */
        final List<String> preSuperCandidateKeys = new ArrayList<String>();

        GetArgCtorAnalyzer(int api) {
            // (GetArg) constructors are instance methods: this=slot 0,
            // arg=slot 1.  strictIntermediateCallRequired=true: see class
            // javadoc — a bare extract-and-pass-through must NOT be credited,
            // only extraction consumed by a genuine internal call.
            // hasImplicitThis=true: slot 0 really is `this` here (unlike a
            // static check method, where slot 0 is just its first ordinary
            // parameter) — see CheckMethodAnalyzer#hasImplicitThis.
            super(api, 1, true, true);
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            if (seenTerminalCall) return;
            super.visitVarInsn(opcode, var);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (seenTerminalCall) return;
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            if (seenTerminalCall) return;
            super.visitJumpInsn(opcode, label);
        }

        @Override
        public void visitInsn(int opcode) {
            if (seenTerminalCall) return;
            super.visitInsn(opcode);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            if (seenTerminalCall) return;
            super.visitFieldInsn(opcode, owner, name, descriptor);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                     String descriptor, boolean isInterface) {
            if (seenTerminalCall) return;
            // Fix 2: the shared superclass logic (CheckMethodAnalyzer#
            // visitMethodInsn) decides, for *every* INVOKESPECIAL <init>
            // call, whether that call's receiver traces back to ALOAD 0
            // (this method's own pre-existing `this`) or not, and records
            // the answer in lastInitCallWasTerminal -- read immediately
            // below, after delegating. An earlier, non-terminal <init> call
            // whose receiver is NOT `this` (e.g. a `new ValidatingWrapper(
            // ...)` this constructor performs before its own terminal call)
            // must not prematurely stop tracking the rest of this
            // constructor's body.
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            if (opcode == Opcodes.INVOKESTATIC && descriptor.contains(GET_ARG_TYPE_DESC)) {
                preSuperCandidateKeys.add(name + "\0" + descriptor);
            }
            if (lastInitCallWasTerminal) {
                // The terminal call itself is handled by the shared
                // isInitCall branch in CheckMethodAnalyzer#visitMethodInsn
                // (never evidence, clears pending state) -- nothing further
                // to do here except stop tracking.
                seenTerminalCall = true;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Inner method visitor: check-method dependency + untyped-GetArg analysis
    // -------------------------------------------------------------------------

    /**
     * Single-pass, method-scoped analyser used for every static method whose
     * descriptor includes a {@code GetArg} parameter (the "dedicated check
     * method" candidates), and — via the {@link GetArgCtorAnalyzer} subclass
     * — for the {@code (GetArg)} constructor's own body.
     *
     * <p>It tracks two independent properties of the method body:
     *
     * <h3>1. Genuine dependency on the {@code GetArg} input
     * ({@link #hasGenuineDependency})</h3>
     * <p>Closes the "vacuous check method" gap: a method with the right
     * shape (takes {@code GetArg}, is called before {@code super(...)}) but
     * whose body never reads its parameter, or reads it and discards the
     * result unconditionally, provides zero real validation and must not be
     * credited as satisfying the constructor's validation-ordering rule.
     *
     * <p>A "read" is a call named {@code get} (any overload — typed,
     * untyped, or a primitive form) or {@code validateInvariants} invoked
     * <em>on</em> the method's tracked {@code GetArg}-typed local variable
     * (approximated via a lightweight receiver-tracking heuristic, see
     * {@link #pendingReceiverVar} — this is not a full operand-stack
     * simulation, see the report accompanying this change for the precise,
     * documented limitation), or a {@code GETFIELD} of an {@code Object}
     * -typed field (the existing "privately-constructed-copy" pattern, see
     * the type-safety section below).
     *
     * <p>A read's result is tracked as "pending dependent" on the (approximated)
     * operand stack and, when stored to a local, taints that local slot until
     * it is overwritten; reloading a tainted slot re-arms the pending state.
     *
     * <p><b>Being consumed by a call is not, by itself, evidence.</b> A
     * value being passed as the receiver or an argument to some further
     * call proves nothing about the method's outcome unless that call is
     * itself trustworthy or its own result is then <em>also</em> traced
     * somewhere meaningful — a call whose result is immediately discarded
     * (e.g. {@code String.valueOf(arg.get(...));} as a bare statement)
     * launders the read without ever influencing anything.  Two kinds of
     * call are recognised as sinks in their own right — see
     * {@link #visitMethodInsn} for the precise rules and the false-bypass/
     * false-regression evidence behind each:
     * <ul>
     *   <li>a call whose owner is <em>not external</em> (see
     *       {@link #isExternalCallOwner}: not {@code java.*}/{@code
     *       javax.*}, not an array built-in like {@code clone()}) —
     *       i.e. code this codebase could plausibly have authored, whether
     *       a private helper of the exact class under analysis or a shared
     *       utility declared elsewhere (this codebase's own real idiom is
     *       {@code org.apache.river.api.io.Valid.notNull(...)}) — finalises
     *       {@link #hasGenuineDependency} <em>immediately</em> and
     *       unconditionally (a monotonic latch: once set, further
     *       unrelated computation in the same method cannot un-set it —
     *       see {@link #visitMethodInsn} for why "immediately" rather than
     *       "only if the call's own result is later used" is both
     *       sufficient to close the proven bypasses and necessary to avoid
     *       losing evidence across multi-argument constructor calls);</li>
     *   <li>an <em>external</em>, non-void call's result still inherits the
     *       pending state (propagates) rather than finalising immediately,
     *       <em>in both modes</em> — so a later <em>internal</em> call
     *       further down the same chain can still pick it up and finalise
     *       (see {@link #isExternalCallOwner}'s real-class evidence —
     *       {@code org.apache.river.lookup.util.ConsistentSet} routes a
     *       read through the external {@code Arrays.asList(...)} before the
     *       internal, genuinely validating {@code elements(...)}), and — in
     *       non-strict mode only — so its own eventual direct sink
     *       (return/branch/cast/instanceof, below) is what decides
     *       genuineness if no internal call ever follows — this is what
     *       correctly rejects {@code String.valueOf(arg.get(...)); return
     *       true;} (the result is discarded before reaching any sink or
     *       internal call) while still accepting
     *       {@code return String.valueOf(arg.get(...)) != null;} (an
     *       admittedly contrived but structurally genuine dependency).
     *       Propagating rather than crediting directly is what keeps this
     *       safe: a chain of external calls only, with no internal call and
     *       no direct sink ever reached, still earns nothing.</li>
     * </ul>
     * Everything else — a void call (no result to propagate; we cannot see
     * what it did with its argument) or an explicit discard (POP) — severs
     * the chain with no credit.  The enclosing method's own {@code <init>}
     * call is never itself evidence either way.
     *
     * <p>Beyond call consumption, the read is also credited once the
     * pending value reaches one of these direct sinks — which of them
     * apply depends on {@code strictIntermediateCallRequired}:
     * <ul>
     *   <li><b>Non-strict mode</b> (separately-declared check methods):
     *       reaching a {@code xRETURN} or a conditional branch finalises
     *       immediately; a {@code CHECKCAST}/{@code instanceof} also
     *       finalises immediately, since the cast/check is itself a
     *       self-contained, observable event (a {@code CHECKCAST} can throw
     *       a CCE on its own, independent of what happens to its result
     *       afterwards).  A separately authored, {@code GetArg}-typed
     *       static method being called from the constructor's pre-super
     *       position is itself already a strong "written on purpose" signal
     *       (nobody accidentally declares a parameter of type
     *       {@code AtomicSerial.GetArg}); requiring only that its outcome
     *       is influenced by what it read is an appropriately-scoped bar
     *       for that context.  For this same reason, non-strict mode also
     *       credits the read itself, immediately, when it is a typed
     *       3-argument {@code get(name, default, Class)} call with a
     *       meaningfully-constraining declared type — <em>not</em>
     *       primitive overloads, which have no equivalent structural
     *       failure mode — see {@link #isSelfValidatingGetForm}.</li>
     *   <li><b>Strict mode</b> (the constructor's own inline extraction):
     *       {@code CHECKCAST}/{@code instanceof}/branches do <em>not</em>
     *       finalise by themselves here — a generic-erasure
     *       {@code CHECKCAST} occurs on essentially every typed
     *       {@code get(...)} call assigned to a concrete field type,
     *       regardless of whether any real validation is happening (see
     *       e.g. {@code ServiceItem}'s ternary null-coalescing extraction,
     *       which must stay non-compliant).  The internal-call rule above
     *       is the <em>only</em> sink in strict mode.</li>
     * </ul>
     * A value that is stored and never reloaded, or explicitly discarded
     * (POP), earns no credit.
     *
     * <p><b>What this does and does not prove.</b> This is a lightweight,
     * single-method, largely linear (non-CFG-sound) taint approximation, not
     * a general dataflow framework — deliberately not a real multi-slot
     * operand-stack simulation, so it cannot independently track "was
     * <em>this specific</em> argument, among several being built for the
     * same call, genuinely processed" — see {@link #hasGenuineDependency}
     * being a whole-method monotonic latch rather than something scoped to
     * one value, which is precisely what makes the multi-argument case
     * above work without that heavier machinery.  It establishes only that
     * the method's outcome is influenced by <em>something</em> it read from
     * its input — it does <em>not</em> establish that the validation logic
     * is semantically correct, nor that an internal call is actually
     * validating anything (it could itself be a no-op/identity private
     * helper declared in the class under analysis or any other non-JDK
     * class reachable from its jar) — that remains out of scope
     * (undecidable in general); it is simply a much stronger, harder-to-fake
     * signal than an arbitrary JDK/array call, which can never be
     * project-authored validation logic by construction.
     *
     * <h3>2. Untyped {@code GetArg.get} / untyped superclass field access
     * ({@link #untypedGetFound})</h3>
     * <p>Calling {@code GetArg.get(String, Object)} (the 2-argument, untyped
     * form) and then consuming the returned {@code Object} — directly, via a
     * local-variable store/reload round trip, or by passing it to another
     * call — in an {@code IFNULL}/{@code IFNONNULL} branch, or otherwise,
     * without an intervening {@code CHECKCAST}/{@code instanceof}, defers the
     * type check to the bridge constructor, where a {@code
     * ClassCastException} can fire <em>during</em> object construction.  The
     * same applies to a {@code GETFIELD} of an inherited {@code Object}-typed
     * field read by a privately-constructed copy of the enclosing class (see
     * the original class javadoc for that pattern's rationale).
     *
     * <p>Safe alternatives: the typed 3-argument form
     * {@code arg.get(name, null, MyType.class)} — performs the type check
     * inside {@code GetArg} before returning — or an immediate
     * {@code CHECKCAST}/{@code instanceof} after the 2-argument form.
     */
    private static class CheckMethodAnalyzer extends MethodVisitor {

        // JVM descriptor of the 2-argument (untyped) GetArg.get form
        private static final String UNTYPED_GET_DESC =
                "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;";

        // JVM descriptor of the 3-argument (typed) GetArg.get form
        private static final String TYPED_GET_DESC =
                "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;";

        // Descriptor of java.lang.Object -- the one declared type for which
        // the typed 3-arg get() form can never throw (every non-null
        // reference is instanceof Object), so it earns no more trust than
        // the untyped form. See isSelfValidatingGetForm.
        private static final String JAVA_LANG_OBJECT_DESC = "Ljava/lang/Object;";

        // JVM descriptor of GetArg.validateInvariants
        private static final String VALIDATE_INVARIANTS_DESC =
                "([Ljava/lang/String;[Ljava/lang/Class;[Z)" +
                "Lorg/apache/river/api/io/AtomicSerial$GetArg;";

        /** Local slot holding the {@code GetArg}-typed value this analyser
         *  is tracking reads/receivers against. */
        private final int trackedSlot;

        /**
         * {@code true} for the {@code (GetArg)} constructor's own inline
         * scan: only "consumed by a further, non-{@code <init>} method call"
         * finalises {@link #hasGenuineDependency}.  {@code false} for a
         * separately-declared static check-method candidate: reaching a
         * return, branch, {@code CHECKCAST}, or {@code instanceof} also
         * finalises it.  See the class javadoc for the rationale.
         */
        private final boolean strictIntermediateCallRequired;

        // ---- type-safety (UNTYPED_GET) tracking ----

        private boolean pendingUntypedGet   = false;
        private boolean pendingUntypedField = false;
        private final Set<Integer> untypedLocalSlots = new HashSet<Integer>();

        /** {@code true} once an untyped/uncast read has been consumed
         *  without an intervening CHECKCAST/instanceof. */
        boolean untypedGetFound = false;

        // ---- genuine-dependency tracking ----

        private boolean pendingDependent = false;
        private final Set<Integer> dependentLocalSlots = new HashSet<Integer>();

        /** {@code true} once a value derived from a read of the tracked
         *  GetArg has been genuinely consumed (see class javadoc). */
        boolean hasGenuineDependency = false;

        /** Local slot most recently loaded via ALOAD, if no intervening
         *  event has invalidated it — a lightweight approximation of "what
         *  is the receiver of the next call", see class javadoc. */
        private int pendingReceiverVar = -1;

        /**
         * Descriptor of the {@code Class} literal most recently pushed via
         * {@code LDC} (e.g. {@code String.class} → {@code "Ljava/lang/String;"}),
         * if nothing has consumed it yet — a lightweight approximation of
         * "what {@code Class} argument is about to be passed to the next
         * call", analogous to {@link #pendingReceiverVar}. Only consulted
         * for the typed 3-argument {@code get(String, Object, Class)} form;
         * see {@link #isSelfValidatingGetForm}.
         */
        private String pendingLdcClassDescriptor = null;

        /**
         * String literal most recently pushed via {@code LDC} while the
         * pending receiver is the tracked {@code GetArg} slot and nothing
         * has consumed it yet — a candidate for the {@code name} argument
         * of the <em>next</em> {@code get(...)} call, analogous to
         * {@link #pendingLdcClassDescriptor}. Reset on every {@code ALOAD}
         * (starts a fresh receiver-tracking window) and once consumed by a
         * {@code get(...)} call. Used by Fix 1 (see
         * {@link #isSelfValidatingGetForm}): if the field-name argument
         * isn't a compile-time-constant String literal at all (e.g. it is
         * computed/concatenated), this stays {@code null} and the
         * self-validating credit path never qualifies.
         */
        private String pendingGetNameLiteral = null;

        /**
         * Field names referenced by a typed 3-argument {@code get(name,
         * default, Class)} call, with a meaningfully-constraining
         * (non-{@code Object.class}) declared type, whose field-name
         * argument was a compile-time-constant String literal — i.e. every
         * syntactic prerequisite for Fix 1's "self-validating" credit is
         * met <em>except</em> confirming the name is actually declared by
         * this class's own {@code serialForm()}. Resolved (or not) once the
         * whole class has been visited, in {@code computeVerdict()}'s
         * {@code isGenuine} helper — see {@link #isSelfValidatingGetForm}'s
         * javadoc for why this must be deferred rather than decided eagerly
         * here.
         */
        final Set<String> selfValidatingFieldNameCandidates = new HashSet<String>();

        /**
         * {@code true} if slot 0 in this method really is {@code this} —
         * i.e. this analyser is tracking an <em>instance</em> method (the
         * {@code (GetArg)} constructor itself, via {@link GetArgCtorAnalyzer})
         * — as opposed to a <em>static</em> check method, where slot 0 is
         * just its first ordinary parameter (commonly {@code arg} itself)
         * and there is no {@code this} at all. Consulted only by {@link
         * #visitVarInsn}'s {@code ALOAD} handling, to decide whether a
         * given {@code ALOAD 0} is evidence of "this is the receiver of the
         * enclosing method's own terminal call" (see {@link
         * #popReceiverIsThis}) or just an ordinary parameter load that
         * happens to use slot 0.
         */
        private final boolean hasImplicitThis;

        /**
         * Fix 2 — origin-tracking stack answering, for each {@code
         * INVOKESPECIAL <init>} call, "does this call's receiver trace back
         * to {@code ALOAD 0} (the enclosing method's own pre-existing
         * {@code this}), or to something else (a {@code NEW} from within
         * this same method, a field/local/call result, ...)?" Each entry is
         * {@code Boolean.TRUE} for a value definitely originating from
         * {@code ALOAD 0} when {@link #hasImplicitThis} is set, {@code
         * Boolean.FALSE} for everything else — pushed and popped alongside
         * the real operand stack's <em>value count</em> (not JVM slot
         * width: a {@code long}/{@code double} is one entry here, exactly
         * like every other value, consistent with how {@link
         * Type#getArgumentTypes} already counts method parameters
         * elsewhere in this file) for every instruction this analyser
         * visits — see {@link #pushOrigin}/{@link #popOrigins}/{@link
         * #popReceiverIsThis}/{@link #duplicateTopOrigin}.
         *
         * <p><b>Why not a raw {@code NEW}/{@code <init>} depth counter</b>
         * (an earlier version of this fix): a counter cannot tell
         * <em>which</em> {@code NEW} a given {@code <init>} call actually
         * belongs to, and is defeated by an orphaned {@code NEW
         * java/lang/Object; POP} pair placed anywhere earlier in the same
         * method — nothing ever calls {@code <init>} on that discarded
         * reference, so the counter is incremented and never decremented,
         * wrongly inflating the count seen by a later, <em>genuinely</em>
         * terminal {@code super(...)}/{@code this(...)} call and crediting
         * a bare, zero-validation pass-through. This bytecode shape loads
         * and runs on a real, unmodified JVM with zero {@code VerifyError}:
         * {@code NEW}-then-immediately-{@code POP} triggers none of the
         * verifier's uninitialized-reference escape hazards (JVMS
         * §4.10.1.4 only cares about a half-constructed reference
         * <em>escaping</em> into general use, never about it being
         * immediately discarded), so this is a fully practical bypass, not
         * a theoretical one. Tracking the receiver's actual origin instead
         * of a count closes it: an orphaned {@code NEW; POP} resolves to
         * nothing (pushed, then immediately popped, zero residual state —
         * see {@link #popOrigins}), so it can never influence the
         * classification of any later, unrelated {@code <init>} call.
         *
         * <p><b>Deliberately conservative, not a full simulation.</b> Any
         * instruction this stack does not specifically model desynchronises
         * it (see {@link #desyncOriginTracking}): the stack is cleared and
         * {@link #originTrackingDesynced} is latched for the rest of this
         * method. While desynced, every subsequent {@code INVOKESPECIAL
         * <init>} call is conservatively treated as if its receiver
         * <em>were</em> confirmed to be {@code this} — i.e. excluded from
         * evidence, exactly like a genuine terminal call — rather than
         * guessed to be a genuine internal construction. This is the safe
         * direction: it can only ever cost a real internal call its credit
         * (a false {@code VALIDATION_ORDER}, the same class of acceptable
         * conservative false positive already documented elsewhere in this
         * file), never wrongly credit a bare pass-through. See {@link
         * #visitInvokeDynamicInsn} for the one call shape (javac string
         * concatenation via {@code StringConcatFactory} and similar) this
         * analyser does not attempt to model at all and always desyncs on.
         *
         * <p><b>Why desync on every conditional branch, not just track
         * both arms.</b> {@link #visitJumpInsn} desyncs unconditionally on
         * every conditional branch ({@code IFEQ}/{@code IFNULL}/{@code
         * IF_ICMPxx}/etc. — everything except {@code GOTO}/{@code JSR},
         * which do not consume a condition at all) rather than merely
         * popping the branch condition's own operand(s), for a specific,
         * proven reason: this analyser is a <em>single linear pass</em>
         * with no notion of control flow at all — ASM visits every
         * instruction in a method exactly once, in bytecode array order,
         * regardless of which path a real execution would actually take.
         * For a branch whose two arms reconverge at a common label (e.g.
         * the extremely common ternary/null-coalescing idiom {@code x ==
         * null ? null : arg.get(name, null, Type.class)}), a real
         * execution runs <em>exactly one</em> arm, but this scan visits
         * <em>both</em>, sequentially, as if they were straight-line code
         * — accumulating entries from the arm that was <em>not</em> taken
         * on top of the ones from the arm that was, so the stack's
         * apparent depth at (and after) the merge point bears no reliable
         * relationship to any real execution's actual stack. This is not
         * hypothetical: an early version of this fix that only popped the
         * branch's own operand(s) — reasoning that a raw depth count would
         * still be branch-insensitive, forgetting that <em>this exact
         * origin-stack</em> is depth/position-sensitive in a way a raw
         * count is not — broke a real class,
         * {@code net.jini.core.lookup.ServiceItem}, whose {@code (GetArg)}
         * constructor computes each of its three fields via exactly this
         * ternary pattern (see the class javadoc's "Strict mode" bullet's
         * reference to it): each {@code IFNONNULL} branch's two arms
         * individually balance to the same net depth (one pushed value),
         * which a real execution preserves correctly, but this linear scan
         * does not, since it processes both arms' instructions in the same
         * pass and never "un-does" the arm not taken. The accumulated
         * phantom entries desynchronised the receiver position by the time
         * the constructor's own genuine terminal call was reached, making
         * it look like its receiver was <em>not</em> {@code this} and
         * wrongly crediting it — regressing {@code ServiceItem} from
         * {@link AtomicSerialVerdict#VALIDATION_ORDER} to a false {@code
         * COMPLIANT}. Desyncing outright on every conditional branch closes
         * this: nothing this lightweight scan cannot reliably reason about
         * survives past a branch, at the cost of conservatively declining
         * to distinguish a genuine internal construction from a terminal
         * call if one happens to sit after a branch in the same method
         * (falls back to the safe default — see above).
         */
        private final Deque<Boolean> thisOriginStack = new ArrayDeque<Boolean>();

        /** Latched {@code true} once {@link #thisOriginStack} can no longer
         *  be trusted — see that field's javadoc. */
        private boolean originTrackingDesynced = false;

        /**
         * Set by {@link #visitMethodInsn} every time it processes an {@code
         * INVOKESPECIAL <init>} call: {@code true} if that call's receiver
         * was confirmed (or conservatively assumed, see {@link
         * #popReceiverIsThis}) to be {@code this} — i.e. it is being
         * treated as the enclosing method's own terminal {@code
         * super(...)}/{@code this(...)} call. {@code false} for every other
         * call. Read by {@link GetArgCtorAnalyzer#visitMethodInsn}
         * immediately after delegating, to decide whether to stop tracking
         * (see {@link GetArgCtorAnalyzer#seenTerminalCall}).
         */
        boolean lastInitCallWasTerminal = false;

        CheckMethodAnalyzer(int api, int trackedSlot, boolean strictIntermediateCallRequired) {
            this(api, trackedSlot, strictIntermediateCallRequired, false);
        }

        /**
         * @param hasImplicitThis {@code true} only for the {@code (GetArg)}
         *                        constructor's own instance-method body (via
         *                        {@link GetArgCtorAnalyzer}), where slot 0
         *                        really is {@code this} — {@code false} for
         *                        every static check-method candidate, where
         *                        slot 0 is just an ordinary parameter and
         *                        there is no {@code this} at all. See
         *                        {@link #hasImplicitThis}.
         */
        CheckMethodAnalyzer(int api, int trackedSlot, boolean strictIntermediateCallRequired,
                             boolean hasImplicitThis) {
            super(api);
            this.trackedSlot = trackedSlot;
            this.strictIntermediateCallRequired = strictIntermediateCallRequired;
            this.hasImplicitThis = hasImplicitThis;
        }

        // ---- Fix 2 origin-tracking-stack helpers ----

        /** Pushes one entry, tagged {@code isThis}. No-op once desynced. */
        private void pushOrigin(boolean isThis) {
            if (originTrackingDesynced) return;
            thisOriginStack.push(Boolean.valueOf(isThis));
        }

        /** Duplicates the top entry (mirrors {@code DUP}'s real stack
         *  effect). Desyncs on underflow. No-op once desynced. */
        private void duplicateTopOrigin() {
            if (originTrackingDesynced) return;
            if (thisOriginStack.isEmpty()) {
                desyncOriginTracking();
                return;
            }
            thisOriginStack.push(thisOriginStack.peek());
        }

        /** Pops and discards {@code count} entries. Desyncs on underflow.
         *  No-op once desynced. */
        private void popOrigins(int count) {
            if (originTrackingDesynced) return;
            for (int i = 0; i < count; i++) {
                if (thisOriginStack.isEmpty()) {
                    desyncOriginTracking();
                    return;
                }
                thisOriginStack.pop();
            }
        }

        /**
         * Pops exactly one entry — the receiver position of the call about
         * to be classified — and returns whether it was confirmed to be
         * {@code this}. If tracking is already desynced, or the stack
         * unexpectedly underflows here, conservatively returns {@code true}
         * (see {@link #thisOriginStack}'s "deliberately conservative"
         * paragraph for why {@code true}, not {@code false}, is the safe
         * default here: it can only cost a real internal call its credit,
         * never wrongly credit a bare pass-through).
         */
        private boolean popReceiverIsThis() {
            if (originTrackingDesynced || thisOriginStack.isEmpty()) {
                desyncOriginTracking();
                return true;
            }
            return thisOriginStack.pop().booleanValue();
        }

        /** A stack-neutral instruction ({@code CHECKCAST}) still requires a
         *  non-empty stack to be well-formed; validates that without
         *  otherwise touching any entry. Desyncs on underflow. */
        private void requireNonEmptyOriginOrDesync() {
            if (originTrackingDesynced) return;
            if (thisOriginStack.isEmpty()) {
                desyncOriginTracking();
            }
        }

        /** Clears the origin-tracking stack and latches {@link
         *  #originTrackingDesynced} — see that field's javadoc. */
        private void desyncOriginTracking() {
            thisOriginStack.clear();
            originTrackingDesynced = true;
        }

        @Override
        public void visitLdcInsn(Object value) {
            // Fix 2: LDC always pushes exactly one value (String, Type/Class
            // literal, boxed primitive, Handle, or ConstantDynamic) -- never
            // `this`. Must be tracked here like every other value-producing
            // instruction, or the origin-tracking stack would silently
            // desynchronise on the very common `arg.get(name, ...)` shape
            // (whose "name" argument is itself an LDC).
            pushOrigin(false);
            // A `Foo.class` literal compiles to an LDC of an ASM Type. This
            // is otherwise a "safe interstitial" push exactly like the LDC
            // of a String literal (field name, which is now tracked below
            // for Fix 1) -- it must not touch any of the other
            // pending/receiver tracking, only record the Class descriptor
            // for isSelfValidatingGetForm to consult if the very next call
            // turns out to be the typed 3-arg get() form.
            if (value instanceof Type) {
                pendingLdcClassDescriptor = ((Type) value).getDescriptor();
                return;
            }
            // Fix 1: capture the *first* String literal pushed while the
            // pending receiver is the tracked GetArg slot as the candidate
            // "name" argument for the next get(...) call. Only the first
            // one is captured (pendingGetNameLiteral == null guard) so that
            // a String-typed *default* value argument -- e.g. arg.get(
            // "name", "default", String.class) -- pushed after the name
            // does not overwrite it.
            if (value instanceof String
                    && pendingReceiverVar == trackedSlot
                    && pendingGetNameLiteral == null) {
                pendingGetNameLiteral = (String) value;
            }
        }

        /**
         * Returns {@code true} if {@code owner} cannot possibly be code this
         * codebase authored: a JDK/platform class ({@code java.*}/
         * {@code javax.*}) or an array type (whose only instance methods are
         * inherited {@code Object} built-ins like {@code clone()} — never
         * validation logic).  Used by {@link #visitMethodInsn} to decide
         * whether a call that consumes a pending value is trustworthy
         * enough to count as evidence; see the class javadoc's "Genuine
         * dependency" section for the false-regression evidence behind this
         * exact boundary (not "same class", which is too narrow — this
         * codebase's own real validation idioms commonly live in a shared,
         * differently-named/-packaged helper, e.g.
         * {@code org.apache.river.api.io.Valid.notNull(...)}).
         *
         * <p>Residual limitation, explicitly not addressed here: this does
         * <em>not</em> verify that an internal call actually does anything
         * with its argument — a downloaded proxy could ship its own
         * same-package no-op/identity helper class purely to satisfy this
         * check. Closing that would require recursively verifying what the
         * callee does with its parameter, which is out of scope for this
         * lightweight, single-pass analysis (see the class's "What this
         * does and does not prove" paragraph); it is flagged here for
         * anyone auditing this boundary later.
         */
        private static boolean isExternalCallOwner(String owner) {
            return owner.startsWith("java/") || owner.startsWith("javax/")
                    || owner.startsWith("[");
        }

        /** Clears every "pending" flag — the operand-stack-top has been
         *  consumed by something that earns no credit (a discard, a void
         *  call, or an external call). */
        private void clearPending() {
            pendingUntypedGet = false;
            pendingUntypedField = false;
            pendingDependent = false;
            pendingLdcClassDescriptor = null;
            pendingGetNameLiteral = null;
        }

        /**
         * Returns {@code true} if a {@code get(...)} call with the given
         * descriptor is <em>inherently</em> validating as a side effect of
         * {@code GetArg}'s own implementation — independent of whether its
         * result is subsequently used — and so may be credited as
         * sufficient evidence on its own (see the "false positive" case in
         * {@link #visitMethodInsn}): the typed 3-argument form,
         * <em>provided</em> the declared type is not {@code Object.class} —
         * {@code instanceof Object} is true for every non-null reference, so
         * {@code arg.get(name, null, Object.class)} can never throw and is
         * typed in name only, no better than the untyped 2-argument form it
         * is meant to be safer than. The typed form's throw-on-mismatch is a
         * genuine structural check: the decoded value's runtime class either
         * matches the declaration or it does not.
         *
         * <p><b>Deliberately excludes primitive overloads</b>
         * ({@code get(String,int)}, {@code get(String,long)},
         * {@code get(String,boolean)}, …), even though they share the "no
         * CCE risk" property with the typed form for the separate,
         * unrelated {@link #untypedGetFound} (Gap-2C) type-safety concern.
         * For *this* concern — genuine validation, not mere type-safety —
         * they are not equivalent: by the time a check method runs, the
         * wire decoder has already committed the field to its declared
         * primitive wire type, so there is no "wrong runtime class" for a
         * primitive read to protect against the way there is for an
         * Object-typed slot with a declared {@code Class}. A primitive
         * overload being read and discarded (e.g.
         * {@code arg.get("committed", false)} in
         * {@code net.jini.core.transaction.TimeoutExpiredException}) is
         * genuinely indistinguishable, at this structural level, from the
         * same shape over a field whose value space has no "any value is
         * fine" carve-out (e.g.
         * {@code org.apache.river.reggie.proxy.EventLease}'s {@code
         * arg.get("eventID", 0L)}, a confirmed real, previously-undetected
         * finding) — "every possible value is semantically valid for this
         * particular field" is a judgement about the field's meaning that
         * this shape-based analyser cannot make, so it does not attempt to;
         * primitive overloads stay under the existing stricter
         * dependency-propagation/sink tracking, unchanged, the same as the
         * untyped 2-argument form.
         *
         * <p><b>Residual, explicitly out-of-scope limitation</b> (same
         * category as {@link #isExternalCallOwner}'s): even for the typed
         * form, this only confirms that <em>some</em> field was
         * type-checked as a side effect of being read — it cannot confirm
         * that field is the class's actual security-sensitive state. A
         * check method that reads and type-validates an unrelated, cosmetic
         * field while leaving the field that actually matters completely
         * unchecked would still satisfy this rule. Telling "the right
         * field" apart from "a field" requires semantic understanding of
         * what each field means, which this single-pass structural analysis
         * does not have and is not attempting to add here. Also not
         * attempted: recognising other declared types that are
         * unconstraining in practice without being literally
         * {@code Object.class} (e.g. a marker interface implemented by
         * effectively every relevant type in a given codebase) —
         * {@code Object.class} is the one case that is unconstraining by
         * construction (true for every reference, unconditionally), so it
         * is the only one excluded here.
         *
         * <p><b>Fix 1 — the field-name-absent bypass.</b> The above
         * "throws on mismatch" premise is only true if the named field is
         * actually <em>present</em> in the incoming stream: {@code
         * AtomicSerial.GetArg.get(String,T,Class&lt;T&gt;)} returns the
         * caller-supplied default <em>before</em> ever calling {@code
         * type.isInstance(v)} whenever the field is absent ({@code v ==
         * ABSENT || v == null}) — so {@code arg.get(
         * "totally_bogus_field_name_xyz", null, AtomicLong.class)}, where
         * that name is not declared by this class's own schema at all, can
         * <em>never</em> throw regardless of the declared type: the type
         * check is unreachable. This method therefore only confirms the
         * <em>shape</em> is right (typed 3-arg form, non-{@code
         * Object.class}); the caller ({@link #visitMethodInsn}) additionally
         * requires the field-name argument to be a compile-time-constant
         * String literal (via {@link #pendingGetNameLiteral} — a
         * computed/concatenated name never qualifies) and records it in
         * {@link #selfValidatingFieldNameCandidates} for deferred
         * cross-checking against this class's own declared {@code
         * serialForm()} field names once the whole class has been visited
         * (see {@code AtomicSerialComplianceVisitor#isGenuine}). Only a name
         * that is both a literal <em>and</em> actually declared closes the
         * bypass; this narrows, but does not fully close, the adjacent
         * "right field, not just a field" residual limitation above (a
         * class's own {@code serialForm()} is authored by the same party as
         * its check method, so a maliciously-crafted class could declare a
         * matching but otherwise-unused decoy field) — see that residual
         * limitation paragraph for why this is inherent to a shape-based,
         * single-pass analysis and out of scope to fully close here.
         */
        private boolean isSelfValidatingGetForm(String getDescriptor) {
            return TYPED_GET_DESC.equals(getDescriptor)
                    && pendingLdcClassDescriptor != null
                    && !JAVA_LANG_OBJECT_DESC.equals(pendingLdcClassDescriptor);
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            if (opcode == Opcodes.ALOAD) {
                // Track the *most recently* loaded reference as the
                // candidate receiver for the next call — not "only if
                // nothing else is pending". A leading `ALOAD 0` (`this`,
                // queued for the constructor's own eventual terminal call)
                // followed by `ALOAD 1` (`arg`, for a *nested* get() call)
                // is the universal shape of every bridge/delegating
                // constructor in this codebase; the inner ALOAD must
                // overwrite, not invalidate, the pending receiver. This is a
                // deliberate approximation — see class javadoc — that holds
                // as long as a get() call's own name/default/type arguments
                // are never themselves loaded via ALOAD (true of every real
                // call site inspected: they are string/class literals or
                // ACONST_NULL).
                pendingReceiverVar = var;
                pendingUntypedGet   = untypedLocalSlots.contains(var);
                pendingUntypedField = false;
                pendingDependent    = dependentLocalSlots.contains(var);
                pendingGetNameLiteral = null;
                // Fix 2: ALOAD 0 is the only way this method's own
                // pre-existing `this` reference is ever pushed -- but only
                // when this method actually *has* an implicit `this` (see
                // hasImplicitThis); for a static check method, slot 0 is
                // just an ordinary parameter.
                pushOrigin(hasImplicitThis && var == 0);
                return;
            }
            pendingReceiverVar = -1;
            if (opcode == Opcodes.ASTORE) {
                if (pendingUntypedGet || pendingUntypedField) {
                    untypedLocalSlots.add(var);
                } else {
                    untypedLocalSlots.remove(var);
                }
                if (pendingDependent) {
                    dependentLocalSlots.add(var);
                } else {
                    dependentLocalSlots.remove(var);
                }
                clearPending();
                popOrigins(1);
                return;
            }
            if (opcode >= Opcodes.ILOAD && opcode <= Opcodes.DLOAD) {
                // Primitive reload (e.g. a `long` extracted via a primitive
                // GetArg.get overload) — only dependency-taint is meaningful
                // for a primitive; type-safety taint never applies to it.
                pendingDependent = dependentLocalSlots.contains(var);
                pendingUntypedGet = false; pendingUntypedField = false;
                pushOrigin(false);
                return;
            }
            if (opcode >= Opcodes.ISTORE && opcode <= Opcodes.DSTORE) {
                if (pendingDependent) {
                    dependentLocalSlots.add(var);
                } else {
                    dependentLocalSlots.remove(var);
                }
                clearPending();
                popOrigins(1);
                return;
            }
            // RET or any other unmodelled var instruction (JSR/RET are
            // disallowed in any class file version this analyser can load
            // to begin with -- JVMS 4.9.1 -- so this is a defensive-only
            // fallback, never expected to trigger in practice).
            clearPending();
            desyncOriginTracking();
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                     String descriptor, boolean isInterface) {
            boolean receiverIsTracked = (pendingReceiverVar == trackedSlot);
            pendingReceiverVar = -1;

            // Fix 2: pop the origin-tracking stack for this call's
            // arguments and (for non-static calls) its receiver --
            // capturing whether that receiver was confirmed to be `this`
            // -- then push a result entry if the call is non-void. Must
            // happen for *every* call (not just <init>), to keep the
            // stack's depth accounting correct for whatever instruction
            // comes next -- see thisOriginStack's javadoc.
            int argCount = Type.getArgumentTypes(descriptor).length;
            boolean callHasReceiver = opcode != Opcodes.INVOKESTATIC;
            popOrigins(argCount);
            boolean receiverIsConfirmedThis = callHasReceiver && popReceiverIsThis();
            if (!descriptor.endsWith(")V")) {
                pushOrigin(false); // a call's own result is never `this`
            }
            lastInitCallWasTerminal = false;

            if ("get".equals(name) && receiverIsTracked) {
                pendingDependent = true;
                if (UNTYPED_GET_DESC.equals(descriptor)) {
                    pendingUntypedGet = true;
                    pendingUntypedField = false;
                } else {
                    // Typed 3-arg form, or a primitive overload: GetArg
                    // itself verifies/produces the type, no CCE risk
                    // (existing Gap-2C logic, unchanged).
                    pendingUntypedGet = false;
                    pendingUntypedField = false;
                    // Gap-2A false-positive fix: a typed 3-arg get() call
                    // with a meaningfully-constraining declared type is
                    // itself inherently validating as a side effect of
                    // GetArg's own implementation (it throws if the field
                    // is present with the wrong runtime class), so it is
                    // credited as sufficient evidence *on its own* --
                    // regardless of whether the result is subsequently
                    // used. Deliberately NOT extended to primitive
                    // overloads (get(String,int)/get(String,long)/...):
                    // unlike an Object-typed slot, a primitive read has no
                    // "wrong runtime class" to protect against by the time
                    // check() runs, so there is no equivalent structural
                    // signal -- see isSelfValidatingGetForm for the exact
                    // boundary, the real-class confirmed bug
                    // (org.apache.river.reggie.proxy.EventLease) that ruled
                    // out extending this to primitives, and the disclosed
                    // "right field, not just a field" limitation.
                    //
                    // Non-strict mode only (dedicated check methods):
                    // crediting this in strict mode (the constructor's own
                    // inline extraction) would make the ordering rule
                    // vacuous again for every (GetArg) constructor, since
                    // *every* one of them has to extract its fields somehow
                    // -- e.g. MulticastTimeToLive's bare
                    // `this(arg.get("ttl", -1))` (itself a primitive
                    // overload) must stay non-compliant; that is exactly
                    // the "bare, unprocessed pass-through" the strict-mode
                    // internal-call requirement exists to reject. The
                    // dedicated-check-method context is different: a
                    // separately authored, GetArg-typed static method being
                    // called pre-super is already a strong "written on
                    // purpose" signal (see the class javadoc's "Dedicated
                    // check method" bullet), so a single inherently-typed
                    // read within it is an appropriately-scoped bar.
                    //
                    // Fix 1: do NOT credit hasGenuineDependency eagerly here.
                    // Record the field name as a *candidate* instead --
                    // only credited once cross-checked against this class's
                    // own serialForm() declared field names, deferred to
                    // computeVerdict()'s isGenuine() helper (serialForm()
                    // may be visited before or after this method within the
                    // same class file). A non-literal field-name argument
                    // (pendingGetNameLiteral == null) never qualifies.
                    if (!strictIntermediateCallRequired
                            && isSelfValidatingGetForm(descriptor)
                            && pendingGetNameLiteral != null) {
                        selfValidatingFieldNameCandidates.add(pendingGetNameLiteral);
                    }
                }
                pendingLdcClassDescriptor = null;
                pendingGetNameLiteral = null;
                return;
            }
            if ("validateInvariants".equals(name)
                    && VALIDATE_INVARIANTS_DESC.equals(descriptor)
                    && receiverIsTracked) {
                // Batch type-validator; covers all fields.
                pendingDependent = true;
                pendingUntypedGet = false;
                pendingUntypedField = false;
                pendingLdcClassDescriptor = null;
                pendingGetNameLiteral = null;
                return;
            }

            // Not a qualifying read: this call *consumes* whatever value was
            // pending (as receiver or argument — single-pass, we do not
            // distinguish which operand position).
            if (pendingUntypedGet || pendingUntypedField) {
                untypedGetFound = true;   // unchanged: existing 2C behaviour,
                                          // independent of everything below.
            }

            boolean isInitCall = opcode == Opcodes.INVOKESPECIAL && "<init>".equals(name);
            if (isInitCall) {
                if (receiverIsConfirmedThis) {
                    // Confirmed (or, if origin-tracking desynced,
                    // conservatively assumed -- see popReceiverIsThis) to be
                    // the enclosing method's own terminal super(...)/
                    // this(...) call: its receiver traces back to ALOAD 0,
                    // never a NEW from within this method's own bytecode
                    // (the object being constructed was already allocated
                    // by the caller / the JVM before this method's code
                    // began executing). Never itself evidence — see class
                    // javadoc.
                    lastInitCallWasTerminal = true;
                    clearPending();
                    return;
                }
                // Fix 2: this <init> call's receiver is confirmed NOT
                // `this` -- it is a `new Foo(args)` expression this method
                // itself constructed (e.g. `new
                // ValidatingWrapper(arg.get(...))`), NOT the enclosing
                // method's terminal call. Fall through to the ordinary
                // internal/external-call evidence rules below instead of
                // the blanket denial that previously treated *every*
                // <init>-named call (regardless of receiver) as
                // never-evidence.
            }

            if (pendingDependent && !isExternalCallOwner(owner)) {
                // Gap-2A hardening: a value derived from a genuine read,
                // consumed by a call this codebase could plausibly have
                // authored, is treated as self-contained, sufficient
                // evidence — mirroring how CHECKCAST is already
                // self-contained evidence below (visitTypeInsn).
                // Finalising *immediately* here (a monotonic latch — see
                // hasGenuineDependency), rather than deferring to whether
                // the call's own result later reaches some further sink,
                // is deliberate for two reasons:
                //  1. it is what actually closes the three proven bypasses:
                //     each specifically routes the read through an
                //     *external* (JDK) call (String.valueOf, array
                //     .clone()), which this branch never reaches
                //     (isExternalCallOwner excludes it — see that method's
                //     javadoc);
                //  2. an earlier version of this fix deferred finalisation
                //     to "does it eventually reach the constructor's
                //     terminal call" instead, but that provides no
                //     additional protection an adversary can't trivially
                //     route around with a same-class identity/pass-through
                //     decoy (its result "reaches" the required sink just as
                //     easily as a real validator's would), while it DID
                //     break real multi-argument constructor calls in this
                //     exact codebase — e.g.
                //     {@code this(checkSerial(arg.get(...)),
                //     arg.get(...).clone())} in
                //     {@code net.jini.constraint.BasicMethodConstraints}:
                //     the first argument is genuinely processed by the
                //     internal, non-void {@code checkSerial(...)}, but the
                //     *second*, unrelated argument's external
                //     {@code .clone()} was overwriting that evidence by the
                //     time the terminal call was reached, because this
                //     analyser tracks a single pending value, not a real
                //     multi-slot operand stack (see the class's "What this
                //     does and does not prove" paragraph). Immediate,
                //     monotonic latching sidesteps that limitation entirely
                //     rather than requiring a heavier multi-value stack
                //     simulation. Confirmed against the real-class corpus:
                //     nine real classes (LeaseMapException,
                //     ServerTransaction, DiscoveryEvent,
                //     BasicMethodConstraints, StringMethodConstraints,
                //     LookupUnmarshalException, BasicProxyPreparer,
                //     StackTraceElementSerializer, ProxySerializer) route
                //     validation through a shared, differently-named/
                //     -packaged helper (typically
                //     {@code org.apache.river.api.io.Valid.notNull(...)}/
                //     {@code Valid.copyMap(...)}) and would otherwise
                //     regress to a false VALIDATION_ORDER.
                // Applies uniformly in both strict (inline extraction) and
                // non-strict (dedicated check method) mode: an internal
                // call is strictly *more* trustworthy than an arbitrary
                // external one, so crediting it immediately here rather
                // than only propagating (as external calls do, below, in
                // *both* modes) is not a relaxation.
                hasGenuineDependency = true;
                pendingUntypedGet = false;
                pendingUntypedField = false;
                return;
            }

            boolean callIsVoid = descriptor.endsWith(")V");
            if (!callIsVoid && pendingDependent) {
                // An external, non-void call's result still inherits the
                // pending state, in *both* modes (fixed from strict-mode-
                // severs-external-calls-outright: that version incorrectly
                // rejected real chains where a genuine internal call
                // follows an external one, e.g.
                // {@code this(elements(Arrays.asList((T[]) arg.get(
                // "elements", null))))} in
                // {@code org.apache.river.lookup.util.ConsistentSet}
                // — {@code Arrays.asList} is external and was severing the
                // taint before it ever reached the internal, genuinely
                // validating {@code elements(...)} call that follows it).
                // This does NOT reopen the external-call-laundering bypass:
                // the terminal {@code <init>} call is excluded from
                // evidence unconditionally, above, in every mode, so an
                // external-call-only chain with no internal call anywhere
                // in it — e.g. the proven bypass
                // {@code this(arg, String.valueOf(arg.get(...)))} — still
                // never finalises {@link #hasGenuineDependency}: it is
                // "propagate through", never "credit directly". In
                // non-strict mode this also remains how a dedicated check
                // method's own further sinks (return/branch/cast/
                // instanceof) get a chance to see a value that passed
                // through an external call first. Internal calls were
                // already handled, immediately, above.
                pendingUntypedGet = false;
                pendingUntypedField = false;
                return;
            }

            // Void call, or nothing was pending to begin with: severs.
            clearPending();
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            pendingReceiverVar = -1;
            if (opcode == Opcodes.NEW) {
                // Fix 2: NEW pushes a fresh, uninitialized reference --
                // never `this` (the object being constructed is allocated
                // by *this* instruction, not the caller). A NEW does not
                // consume anything; the pending value (if any) survives
                // underneath it on the real operand stack, so nothing else
                // is touched. See thisOriginStack's javadoc for why tracking
                // the receiver's actual origin, rather than a raw NEW/<init>
                // depth count, is what closes the orphaned `NEW; POP` bypass.
                pushOrigin(false);
                return;
            }
            if (opcode == Opcodes.CHECKCAST) {
                // The cast itself can throw CCE — fires here, before
                // construction/before this method returns, so this is a
                // meaningful, self-contained validating event.
                pendingUntypedGet = false;
                pendingUntypedField = false;
                if (pendingDependent && !strictIntermediateCallRequired) {
                    hasGenuineDependency = true;
                }
                // In strict (constructor-inline) mode the value survives the
                // cast un-finalised: a CHECKCAST inserted purely by generic
                // erasure (e.g. narrowing GetArg.get's Object-erased return
                // to a concrete field type before an unprocessed
                // pass-through into super(...)/this(...)) must not, by
                // itself, be credited as validation — see class javadoc.
                //
                // Fix 2: CHECKCAST pops and re-pushes the very same
                // value/identity -- stack-neutral, so the origin tag
                // survives unchanged; just validate there IS a top entry.
                requireNonEmptyOriginOrDesync();
            } else if (opcode == Opcodes.INSTANCEOF) {
                // instanceof does not throw by itself; it produces a fresh
                // boolean that must still reach a branch/consumption to
                // count, so pendingDependent survives here in both modes.
                pendingUntypedField = false;
                // Fix 2: pops the reference, pushes a fresh int (never `this`).
                popOrigins(1);
                pushOrigin(false);
            } else {
                // ANEWARRAY: pops the requested length, pushes a new array
                // reference (never `this`).
                popOrigins(1);
                pushOrigin(false);
            }
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            if (opcode == Opcodes.GOTO || opcode == Opcodes.JSR) {
                // Unconditional; does not consume the operand stack.
                return;
            }
            pendingReceiverVar = -1;
            if ((pendingUntypedGet || pendingUntypedField)
                    && (opcode == Opcodes.IFNULL || opcode == Opcodes.IFNONNULL)) {
                untypedGetFound = true;
            }
            if (pendingDependent && !strictIntermediateCallRequired) {
                hasGenuineDependency = true;
            }
            clearPending();
            // Fix 2: a conditional branch is where this analyser's linear,
            // non-CFG-aware scan stops being trustworthy for origin
            // tracking -- see thisOriginStack's "why desync on every
            // conditional branch" paragraph for the real-class regression
            // (ServiceItem's ternary null-coalescing extraction) that
            // proved this necessary. Desync outright rather than merely
            // popping the branch condition's own operand(s): both the
            // "taken" and "not-taken" successors are visited *sequentially*
            // by this single-pass scan (ASM has no notion of "skip to the
            // jump target" — every instruction in the method is visited
            // exactly once, in bytecode array order, regardless of which
            // path a real execution would actually take), so whatever this
            // stack thinks is at a given depth after a branch bears no
            // reliable relationship to any real execution's actual stack.
            desyncOriginTracking();
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.DUP) {
                // Duplicated value stays pending for both copies; receiver
                // tracking is likewise left untouched.
                duplicateTopOrigin();
                return;
            }
            if (isComparison(opcode)) {
                // LCMP/FCMPx/DCMPx: produces a fresh int derived from its
                // (possibly tainted) operands, almost always immediately
                // consumed by the following IF*/branch (e.g. `bits0 == 0`
                // compiles to LLOAD/LCONST_0/LCMP/IFNE). Propagate rather
                // than clear, so the branch rule below can still credit it.
                popOrigins(2);
                pushOrigin(false);
                return;
            }
            if (isNumericConversion(opcode)) {
                // I2L/I2F/.../D2F: an implicit widening/narrowing conversion
                // the compiler inserts between a primitive get() overload
                // and the type its result is actually used as (e.g.
                // `arg.get("time", -1)` returning `int`, then widened via
                // I2L before being passed to a `long`-typed helper). Purely
                // a type-level operation on the single value already on top
                // of the stack — must propagate, not clear, pending taint.
                popOrigins(1);
                pushOrigin(false);
                return;
            }
            // Constant-pushing opcodes (ACONST_NULL/ICONST_*/LCONST_*/
            // FCONST_*/DCONST_*) and NOP are "safe interstitial" pushes that
            // build up a call's other arguments (e.g. the default value in
            // the extremely common `arg.get(name, null)` / `arg.get(name, 0)`
            // shape). They must not clear a pending value that is still
            // buried under them on the real operand stack, nor invalidate
            // in-flight receiver tracking (see visitVarInsn/visitMethodInsn) —
            // exactly like the LDC of the "name" string argument, which is
            // not overridden here at all and so is already a true no-op.
            if (isConstantPush(opcode) || opcode == Opcodes.NOP) {
                if (opcode != Opcodes.NOP) {
                    pushOrigin(false);
                }
                return;
            }
            pendingReceiverVar = -1;
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                if (pendingDependent && !strictIntermediateCallRequired) {
                    hasGenuineDependency = true;
                }
            }
            // Everything else (POP/POP2/arithmetic/array ops/ATHROW/etc.):
            // treated as an explicit discard — no credit either way.
            applyGenericZeroOperandStackEffect(opcode);
            clearPending();
        }

        /**
         * Fix 2 origin-tracking-stack accounting for every zero-operand
         * opcode not already specially handled above (DUP/comparison/
         * numeric-conversion/constant-push all return early). Covers
         * returns, array load/store, {@code POP}/{@code POP2}, arithmetic,
         * {@code ARRAYLENGTH}, {@code ATHROW}, and {@code MONITORENTER}/
         * {@code MONITOREXIT} with their real JVM pop/push counts (values,
         * not slot widths — see {@link #thisOriginStack}); anything not
         * recognised here — the {@code DUP_X1}/{@code DUP_X2}/{@code DUP2}/
         * {@code DUP2_X1}/{@code DUP2_X2}/{@code SWAP} stack-reordering
         * family, deliberately not modelled (rare in constructor/check-method
         * bodies and error-prone to get exactly right by hand) —
         * conservatively desyncs rather than guesses.
         */
        private void applyGenericZeroOperandStackEffect(int opcode) {
            switch (opcode) {
                case Opcodes.RETURN:
                    break; // no operand
                case Opcodes.IRETURN:
                case Opcodes.LRETURN:
                case Opcodes.FRETURN:
                case Opcodes.DRETURN:
                case Opcodes.ARETURN:
                case Opcodes.ATHROW:
                case Opcodes.MONITORENTER:
                case Opcodes.MONITOREXIT:
                case Opcodes.POP:
                    popOrigins(1);
                    break;
                case Opcodes.POP2:
                    // Ambiguous in this value-based (not slot-width) model:
                    // pops either one category-2 value or two category-1
                    // values. POP2 is rare in constructor/check-method
                    // bodies (mostly used to discard an unused long/double);
                    // treated as popping exactly one entry -- a documented,
                    // deliberate approximation consistent with the rest of
                    // this lightweight analyser.
                    popOrigins(1);
                    break;
                case Opcodes.ARRAYLENGTH:
                    popOrigins(1);
                    pushOrigin(false);
                    break;
                case Opcodes.IALOAD: case Opcodes.LALOAD: case Opcodes.FALOAD:
                case Opcodes.DALOAD: case Opcodes.AALOAD: case Opcodes.BALOAD:
                case Opcodes.CALOAD: case Opcodes.SALOAD:
                    popOrigins(2);
                    pushOrigin(false);
                    break;
                case Opcodes.IASTORE: case Opcodes.LASTORE: case Opcodes.FASTORE:
                case Opcodes.DASTORE: case Opcodes.AASTORE: case Opcodes.BASTORE:
                case Opcodes.CASTORE: case Opcodes.SASTORE:
                    popOrigins(3);
                    break;
                case Opcodes.IADD: case Opcodes.LADD: case Opcodes.FADD: case Opcodes.DADD:
                case Opcodes.ISUB: case Opcodes.LSUB: case Opcodes.FSUB: case Opcodes.DSUB:
                case Opcodes.IMUL: case Opcodes.LMUL: case Opcodes.FMUL: case Opcodes.DMUL:
                case Opcodes.IDIV: case Opcodes.LDIV: case Opcodes.FDIV: case Opcodes.DDIV:
                case Opcodes.IREM: case Opcodes.LREM: case Opcodes.FREM: case Opcodes.DREM:
                case Opcodes.ISHL: case Opcodes.LSHL:
                case Opcodes.ISHR: case Opcodes.LSHR:
                case Opcodes.IUSHR: case Opcodes.LUSHR:
                case Opcodes.IAND: case Opcodes.LAND:
                case Opcodes.IOR: case Opcodes.LOR:
                case Opcodes.IXOR: case Opcodes.LXOR:
                    popOrigins(2);
                    pushOrigin(false);
                    break;
                case Opcodes.INEG: case Opcodes.LNEG: case Opcodes.FNEG: case Opcodes.DNEG:
                    popOrigins(1);
                    pushOrigin(false);
                    break;
                default:
                    // DUP_X1/DUP_X2/DUP2/DUP2_X1/DUP2_X2/SWAP and anything
                    // else not modelled: conservatively desync rather than
                    // guess -- see thisOriginStack's javadoc.
                    desyncOriginTracking();
            }
        }

        private static boolean isComparison(int opcode) {
            return opcode == Opcodes.LCMP
                    || opcode == Opcodes.FCMPL || opcode == Opcodes.FCMPG
                    || opcode == Opcodes.DCMPL || opcode == Opcodes.DCMPG;
        }

        private static boolean isNumericConversion(int opcode) {
            return opcode >= Opcodes.I2L && opcode <= Opcodes.I2S;
        }

        private static boolean isConstantPush(int opcode) {
            return opcode == Opcodes.ACONST_NULL
                    || (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5)
                    || opcode == Opcodes.LCONST_0 || opcode == Opcodes.LCONST_1
                    || (opcode >= Opcodes.FCONST_0 && opcode <= Opcodes.FCONST_2)
                    || opcode == Opcodes.DCONST_0 || opcode == Opcodes.DCONST_1;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name,
                                    String descriptor) {
            pendingReceiverVar = -1;
            // GETFIELD on an Object-typed field: the loaded value has no
            // type information at the bytecode level and must be verified
            // by INSTANCEOF or CHECKCAST before being used in a null-check.
            // Any other field access clears the pending flags.
            pendingUntypedGet = false;
            if (opcode == Opcodes.GETFIELD
                    && "Ljava/lang/Object;".equals(descriptor)) {
                pendingUntypedField = true;
                pendingDependent = true;
                // Fix 2: GETFIELD pops the objectref, pushes the field
                // value (never `this`).
                popOrigins(1);
                pushOrigin(false);
                return;
            }
            if ((opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC)
                    && pendingDependent && !strictIntermediateCallRequired) {
                hasGenuineDependency = true;
            }
            pendingUntypedField = false;
            pendingDependent = false;
            // Fix 2 origin-stack accounting for the remaining field-access
            // opcodes (visitFieldInsn is only ever called for these four).
            switch (opcode) {
                case Opcodes.GETFIELD:
                    popOrigins(1);
                    pushOrigin(false);
                    break;
                case Opcodes.GETSTATIC:
                    pushOrigin(false);
                    break;
                case Opcodes.PUTFIELD:
                    popOrigins(2);
                    break;
                case Opcodes.PUTSTATIC:
                    popOrigins(1);
                    break;
                default:
                    desyncOriginTracking();
            }
        }

        // ---- Fix 2: opcodes not otherwise visited by this analyser, but
        // whose stack effect must still be tracked to keep the
        // origin-tracking stack correctly aligned for the calls that come
        // after them ----

        @Override
        public void visitIntInsn(int opcode, int operand) {
            // BIPUSH/SIPUSH push a small int constant; NEWARRAY pops the
            // requested length and pushes a new (never `this`) array
            // reference.
            if (opcode == Opcodes.NEWARRAY) {
                popOrigins(1);
            }
            pushOrigin(false);
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            popOrigins(numDimensions);
            pushOrigin(false);
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            popOrigins(1); // the switch key
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            popOrigins(1); // the switch key
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor,
                                            Handle bootstrapMethodHandle,
                                            Object... bootstrapMethodArguments) {
            // Not modelled by the Fix 2 origin-tracking stack (e.g. javac
            // string concatenation via StringConcatFactory) --
            // conservatively desync *only* that tracking (see
            // desyncOriginTracking), without touching the pre-existing
            // pendingDependent/pendingReceiverVar/etc. approximate
            // tracking, whose behaviour here is unchanged from before this
            // fix (a silent no-op, since invokedynamic was never
            // specifically handled by this analyser at all).
            desyncOriginTracking();
        }
    }
}
