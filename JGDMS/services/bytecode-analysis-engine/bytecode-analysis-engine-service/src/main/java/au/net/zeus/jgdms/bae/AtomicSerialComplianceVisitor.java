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

import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
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
 *   <li>The {@code (GetArg)} constructor must call a static validation method
 *       (<em>before</em> calling {@code super(...)}), detected as an
 *       {@code INVOKESTATIC} whose return type is used as the sole argument
 *       to {@code INVOKESPECIAL <init>} — i.e. the pattern
 *       {@code this(arg, check(arg))} with a private bridge constructor.</li>
 *   <li>The static validation method must type-check every object-type field
 *       it retrieves from {@code GetArg}.  Specifically, calling the 2-argument
 *       {@code GetArg.get(String, Object)} form and then immediately using the
 *       result in an {@code IFNULL}/{@code IFNONNULL} branch without a
 *       preceding {@code CHECKCAST} is flagged as
 *       {@link AtomicSerialVerdict#UNTYPED_GET}: the type is only verified
 *       in the bridge constructor, where a CCE can fire <em>during</em>
 *       construction.  The safe patterns are either the typed 3-argument form
 *       {@code arg.get(name, null, MyType.class)} or an explicit cast
 *       {@code (MyType) arg.get(name, null)} within the check method.</li>
 *   <li>It must have a {@code public static SerialForm[] serialForm()} method
 *       (unless it is annotated {@code @Stateless}).</li>
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

    // AtomicSerial GetArg constructor descriptor
    private static final String GET_ARG_CTOR_DESC =
            "(Lorg/apache/river/api/io/AtomicSerial$GetArg;)V";

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
    private boolean getArgCtorValidationOk          = false;
    /**
     * {@code true} if the class declares at least one non-static,
     * non-transient instance field.  A class with no such fields does not
     * need its own {@code serialForm()} method because it contributes no
     * new serialized state beyond what its superclass(es) already describe.
     */
    private boolean hasNonStaticInstanceFields      = false;

    /**
     * The name and descriptor of the static check method as identified by
     * {@link GetArgCtorAnalyzer}.  Set when the (GetArg) constructor is
     * visited, may remain {@code null} if no INVOKESTATIC was seen.
     */
    private String identifiedCheckMethodName = null;
    private String identifiedCheckMethodDesc = null;

    /**
     * Results of running {@link CheckMethodAnalyzer} on every static method
     * that looks like a check method (static, returns {@code Z}, has a
     * {@code GetArg} parameter).  Keyed by {@code name + "\0" + descriptor}.
     */
    private final Map<String, CheckMethodAnalyzer> checkMethodAnalyzers =
            new HashMap<String, CheckMethodAnalyzer>();

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
        } catch (Exception e) {
            // ASM parse failure — conservative: treat as MISSING_CONSTRUCTOR
            // since we cannot verify anything
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
        // serialForm() to declare the serial form of those fields.  A class
        // with no non-static fields (e.g. a delegation-only proxy subclass)
        // inherits its serial form entirely from the superclass and does not
        // need its own serialForm().
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
            GetArgCtorAnalyzer ctorAnalyzer = new GetArgCtorAnalyzer(ASM_API);
            return ctorAnalyzer;
        }

        // Check for: public static SerialForm[] serialForm()
        if ("serialForm".equals(name)
                && "()[Lorg/apache/river/api/io/AtomicSerial$SerialForm;".equals(descriptor)
                && (access & Opcodes.ACC_STATIC) != 0
                && (access & Opcodes.ACC_PUBLIC) != 0) {
            hasSerialFormMethod = true;
        }

        // Collect check-method candidates: static methods whose descriptor
        // includes a GetArg parameter and returns boolean (Z).
        if ((access & Opcodes.ACC_STATIC) != 0
                && descriptor.contains("Lorg/apache/river/api/io/AtomicSerial$GetArg;")
                && descriptor.endsWith(")Z")) {
            CheckMethodAnalyzer cma = new CheckMethodAnalyzer(ASM_API);
            checkMethodAnalyzers.put(name + "\0" + descriptor, cma);
            return cma;
        }

        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
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
        // serialForm() requirement below is skipped for @Stateless.  Such a
        // subclass legitimately delegates validation to its (validated)
        // superclass via super(arg).
        if (hasGetArgConstructor && !hasStatelessAnnotation && !getArgCtorValidationOk) {
            return AtomicSerialVerdict.VALIDATION_ORDER;
        }

        // Check the identified check method for untyped GetArg access.
        // identifiedCheckMethodName is set by GetArgCtorAnalyzer.
        if (identifiedCheckMethodName != null) {
            String key = identifiedCheckMethodName + "\0" + identifiedCheckMethodDesc;
            CheckMethodAnalyzer cma = checkMethodAnalyzers.get(key);
            if (cma != null && cma.untypedGetFound) {
                return AtomicSerialVerdict.UNTYPED_GET;
            }
        }

        // Check serialForm() unless @Stateless or the class has no non-static
        // instance fields.  A class with no non-static fields (e.g. a
        // delegation-only proxy subclass that adds no new serialized state)
        // inherits its serial form entirely from the superclass and is not
        // required to supply its own serialForm().
        if (hasAtomicSerialAnnotation && !hasStatelessAnnotation
                && !hasSerialFormMethod && hasNonStaticInstanceFields) {
            return AtomicSerialVerdict.MISSING_SERIAL_FORM;
        }

        // All good (or @Stateless)
        if (hasAtomicSerialAnnotation) {
            return AtomicSerialVerdict.COMPLIANT;
        }

        // Serializable but no @AtomicSerial annotation -> N/A
        return AtomicSerialVerdict.NA;
    }

    // -------------------------------------------------------------------------
    // Inner method visitor: checks validation-before-super in (GetArg) ctor
    // -------------------------------------------------------------------------

    /**
     * Analyses the {@code (GetArg)} constructor body to confirm that a
     * static method call (the validation check) appears before the
     * {@code INVOKESPECIAL <init>} super-constructor call, and records the
     * name and descriptor of that static method so it can be analysed for
     * type safety by {@link CheckMethodAnalyzer}.
     *
     * <p>The expected bytecode pattern for {@code this(arg, check(arg))} is:
     * <pre>
     *   ALOAD_0
     *   ALOAD_1            // arg
     *   INVOKESTATIC       // check(GetArg) — validation
     *   INVOKESPECIAL      // this(GetArg, boolean) bridge ctor
     * </pre>
     *
     * <p>We accept the constructor as valid if an {@code INVOKESTATIC}
     * precedes the first {@code INVOKESPECIAL &lt;init&gt;}.
     */
    private final class GetArgCtorAnalyzer extends MethodVisitor {

        private boolean seenInvokeStatic  = false;
        private boolean seenInvokeSpecial = false;

        // Name and descriptor of the last INVOKESTATIC seen before INVOKESPECIAL
        private String lastStaticName = null;
        private String lastStaticDesc = null;

        GetArgCtorAnalyzer(int api) {
            super(api);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                     String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESTATIC && !seenInvokeSpecial) {
                seenInvokeStatic = true;
                lastStaticName   = name;
                lastStaticDesc   = descriptor;
            }
            if (opcode == Opcodes.INVOKESPECIAL && "<init>".equals(name)
                    && !seenInvokeSpecial) {
                seenInvokeSpecial = true;
                // Record the ordering result back in the enclosing visitor
                AtomicSerialComplianceVisitor.this.getArgCtorValidationOk =
                        seenInvokeStatic;
                // Record the identified check method for later type analysis
                AtomicSerialComplianceVisitor.this.identifiedCheckMethodName =
                        lastStaticName;
                AtomicSerialComplianceVisitor.this.identifiedCheckMethodDesc =
                        lastStaticDesc;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Inner method visitor: checks check-method for untyped GetArg access
    // -------------------------------------------------------------------------

    /**
     * Analyses a static check method for two anti-patterns:
     *
     * <h3>1. Untyped {@code GetArg.get}</h3>
     * <p>Calling {@code GetArg.get(String, Object)} (the 2-argument, untyped
     * form) and then using the returned {@code Object} directly in an
     * {@code IFNULL}/{@code IFNONNULL} branch without a preceding
     * {@code CHECKCAST}.  This defers the type-check to the bridge
     * constructor, where a {@code ClassCastException} can fire <em>during</em>
     * object construction rather than safely in the static check method
     * before construction begins.
     *
     * <p>Safe alternatives that this detector accepts:
     * <ul>
     *   <li>The typed 3-argument form
     *       {@code arg.get(name, null, MyType.class)} — performs the type
     *       check inside {@code GetArg} before returning.</li>
     *   <li>An immediate {@code CHECKCAST} after the 2-argument form —
     *       {@code (MyType) arg.get(name, null)} — type exception fires in
     *       the check method.</li>
     * </ul>
     *
     * <h3>2. Untyped access to superclass {@code Object}-typed fields</h3>
     * <p>When a child class's static check method constructs a private copy
     * of itself (via a private bridge constructor that calls {@code super(arg)})
     * in order to read and verify protected {@code Object}-typed fields
     * inherited from a superclass, it must type-check those fields with
     * {@code instanceof} or {@code CHECKCAST} before accepting them.  Reading
     * the field with a {@code GETFIELD} and then immediately checking
     * {@code == null} (IFNULL/IFNONNULL) <em>without</em> a preceding
     * {@code instanceof} or {@code CHECKCAST} is flagged because the actual
     * runtime type of the deserialized value is never verified before the
     * object is fully constructed.
     *
     * <p>An {@code instanceof} check (or explicit {@code CHECKCAST}) makes
     * the type exception fire in the static check method — before construction
     * — which is safe.  A bare null-check with no type verification is
     * flagged as {@link AtomicSerialVerdict#UNTYPED_GET}.
     */
    static final class CheckMethodAnalyzer extends MethodVisitor {

        // JVM descriptor of the 2-argument (untyped) GetArg.get form
        private static final String UNTYPED_GET_DESC =
                "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;";

        // JVM descriptor of the 3-argument (typed) GetArg.get form
        private static final String TYPED_GET_DESC =
                "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;";

        // JVM descriptor of GetArg.validateInvariants
        private static final String VALIDATE_INVARIANTS_DESC =
                "([Ljava/lang/String;[Ljava/lang/Class;[Z)" +
                "Lorg/apache/river/api/io/AtomicSerial$GetArg;";

        /**
         * {@code true} if the last instruction was a 2-argument
         * {@code GetArg.get} call whose result has not yet been type-verified
         * by a {@code CHECKCAST} in this method.
         */
        private boolean pendingUntypedGet = false;

        /**
         * {@code true} if the last instruction was a {@code GETFIELD} whose
         * declared field descriptor is {@code Ljava/lang/Object;} and the
         * loaded value has not yet been type-verified by a {@code CHECKCAST}
         * or {@code instanceof} in this method.
         *
         * <p>This flag is used to detect the pattern where a child class's
         * static check method constructs a private copy of itself (via a
         * private bridge constructor that calls {@code super(arg)}) and then
         * reads a protected {@code Object}-typed field from the superclass
         * using {@code GETFIELD} without subsequently type-checking the
         * deserialized value.
         */
        private boolean pendingUntypedField = false;

        /**
         * {@code true} if at least one 2-argument {@code GetArg.get} result
         * (or a {@code GETFIELD} on an {@code Object}-typed field) was used
         * in an {@code IFNULL}/{@code IFNONNULL} branch without a preceding
         * {@code CHECKCAST} or {@code instanceof} — i.e. the type is never
         * verified in this check method.
         */
        boolean untypedGetFound = false;

        CheckMethodAnalyzer(int api) {
            super(api);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                     String descriptor, boolean isInterface) {
            if ("get".equals(name)) {
                if (UNTYPED_GET_DESC.equals(descriptor)) {
                    // 2-arg form: result is raw Object, type not yet verified
                    pendingUntypedGet = true;
                    pendingUntypedField = false;
                    return;
                }
                if (TYPED_GET_DESC.equals(descriptor)) {
                    // 3-arg form: GetArg performs the type check internally
                    pendingUntypedGet = false;
                    pendingUntypedField = false;
                    return;
                }
            }
            if ("validateInvariants".equals(name)
                    && VALIDATE_INVARIANTS_DESC.equals(descriptor)) {
                // Batch type-validator; covers all fields
                pendingUntypedGet = false;
                pendingUntypedField = false;
                return;
            }
            // Any other method call means the get result was consumed
            pendingUntypedGet = false;
            pendingUntypedField = false;
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == Opcodes.CHECKCAST) {
                // Explicit cast in the check method — type is verified here
                // (fires before construction, so safe for both patterns)
                pendingUntypedGet = false;
                pendingUntypedField = false;
            } else if (opcode == Opcodes.INSTANCEOF) {
                // instanceof check in the check method — type is safely verified
                // (no ClassCastException can result from instanceof)
                pendingUntypedField = false;
            }
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            if ((pendingUntypedGet || pendingUntypedField)
                    && (opcode == Opcodes.IFNULL
                        || opcode == Opcodes.IFNONNULL)) {
                // Object used as null check without any CHECKCAST or instanceof —
                // the type of the deserialized value is never verified in this
                // method (applies to both GetArg.get results and GETFIELD access
                // on Object-typed superclass fields)
                untypedGetFound = true;
            }
            pendingUntypedGet = false;
            pendingUntypedField = false;
        }

        @Override
        public void visitInsn(int opcode) {
            // Any other instruction that consumes or discards the result
            pendingUntypedGet = false;
            pendingUntypedField = false;
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            // ASTORE/ALOAD etc. — result consumed; no CHECKCAST seen yet.
            // We do NOT flag here (the cast may come later in the method),
            // but we do reset so we do not accidentally flag a later IFNULL
            // that is unrelated to this get call.
            pendingUntypedGet = false;
            pendingUntypedField = false;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name,
                                    String descriptor) {
            // GETFIELD on an Object-typed field: the loaded value has no
            // type information at the bytecode level and must be verified
            // by INSTANCEOF or CHECKCAST before being used in a null-check.
            // Any other field access clears the pending flags.
            pendingUntypedGet = false;
            if (opcode == Opcodes.GETFIELD
                    && "Ljava/lang/Object;".equals(descriptor)) {
                pendingUntypedField = true;
            } else {
                pendingUntypedField = false;
            }
        }
    }
}
