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

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
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

    private boolean hasAtomicSerialAnnotation = false;
    private boolean hasStatelessAnnotation     = false;
    private boolean implementsSerializable     = false;
    private boolean hasGetArgConstructor       = false;
    private boolean hasSerialFormMethod        = false;
    private boolean getArgCtorValidationOk     = false;

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
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                      String signature, String[] exceptions) {
        if ("<init>".equals(name) && GET_ARG_CTOR_DESC.equals(descriptor)) {
            hasGetArgConstructor = true;
            // Analyse this constructor for validation-before-super ordering
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
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    @Override
    public void visitEnd() {
        // Collect getArgCtorValidationOk from any pending GetArgCtorAnalyzer.
        // (We set it when visitEnd() on the ctor analyzer is called.)
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

        // Not serializable and not annotated @AtomicSerial → N/A
        if (!implementsSerializable && !hasAtomicSerialAnnotation) {
            return AtomicSerialVerdict.NA;
        }

        // @AtomicSerial present but no GetArg constructor
        if (hasAtomicSerialAnnotation && !hasGetArgConstructor) {
            return AtomicSerialVerdict.MISSING_CONSTRUCTOR;
        }

        // GetArg constructor exists — check validation ordering
        if (hasGetArgConstructor && !getArgCtorValidationOk) {
            return AtomicSerialVerdict.VALIDATION_ORDER;
        }

        // Check serialForm() unless @Stateless
        if (hasAtomicSerialAnnotation && !hasStatelessAnnotation
                && !hasSerialFormMethod) {
            return AtomicSerialVerdict.MISSING_SERIAL_FORM;
        }

        // All good (or @Stateless)
        if (hasAtomicSerialAnnotation) {
            return AtomicSerialVerdict.COMPLIANT;
        }

        // Serializable but no @AtomicSerial annotation → N/A
        return AtomicSerialVerdict.NA;
    }

    // -------------------------------------------------------------------------
    // Inner method visitor: checks validation-before-super in (GetArg) ctor
    // -------------------------------------------------------------------------

    /**
     * Analyses the {@code (GetArg)} constructor body to confirm that a
     * static method call (the validation check) appears before the
     * {@code INVOKESPECIAL <init>} super-constructor call.
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

        private boolean seenInvokeStatic    = false;
        private boolean seenInvokeSpecial   = false;

        GetArgCtorAnalyzer(int api) {
            super(api);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                     String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESTATIC && !seenInvokeSpecial) {
                seenInvokeStatic = true;
            }
            if (opcode == Opcodes.INVOKESPECIAL && "<init>".equals(name)
                    && !seenInvokeSpecial) {
                seenInvokeSpecial = true;
                // Record the ordering result back in the enclosing visitor
                AtomicSerialComplianceVisitor.this.getArgCtorValidationOk =
                        seenInvokeStatic;
            }
        }
    }
}
