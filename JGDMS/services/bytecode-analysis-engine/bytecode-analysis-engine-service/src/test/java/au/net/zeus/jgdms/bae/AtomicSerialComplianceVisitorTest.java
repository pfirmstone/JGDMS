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
import java.io.InputStream;
import org.junit.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import au.net.zeus.jgdms.api.codebase.AnalysisRequest;
import au.net.zeus.jgdms.api.codebase.AtomicSerialVerdict;
import au.net.zeus.jgdms.api.codebase.ClassAnalysisResult;
import au.net.zeus.jgdms.api.codebase.JarAnalysisReport;
import au.net.zeus.jgdms.bae.proxy.BytecodeAnalysisEngineProxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Unit tests for {@link AtomicSerialComplianceVisitor}.
 *
 * <p>Each test generates a synthetic class via ASM {@link ClassWriter} that
 * exhibits a specific bytecode pattern and then verifies the expected
 * {@link AtomicSerialVerdict}.  Two real JGDMS {@code @AtomicSerial}
 * classes ({@link AnalysisRequest} and {@link JarAnalysisReport}) are
 * also loaded from the classpath and checked for {@link AtomicSerialVerdict#COMPLIANT}.
 *
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public class AtomicSerialComplianceVisitorTest {

    // -----------------------------------------------------------------------
    // JVM internal names / descriptors used across generators
    // -----------------------------------------------------------------------

    /** {@code org/apache/river/api/io/AtomicSerial} */
    private static final String ATOMIC_SERIAL =
            "org/apache/river/api/io/AtomicSerial";

    /** {@code Lorg/apache/river/api/io/AtomicSerial;} */
    private static final String ATOMIC_SERIAL_DESC =
            "L" + ATOMIC_SERIAL + ";";

    /** {@code org/apache/river/api/io/AtomicSerial$GetArg} */
    private static final String GET_ARG =
            "org/apache/river/api/io/AtomicSerial$GetArg";

    /** {@code (Lorg/apache/river/api/io/AtomicSerial$GetArg;)V} */
    private static final String GET_ARG_CTOR_DESC =
            "(L" + GET_ARG + ";)V";

    /** {@code org/apache/river/api/io/AtomicSerial$SerialForm} */
    private static final String SERIAL_FORM =
            "org/apache/river/api/io/AtomicSerial$SerialForm";

    private static final String SERIALIZABLE = "java/io/Serializable";

    // GetArg.get descriptors
    private static final String UNTYPED_GET =
            "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;";
    private static final String TYPED_GET =
            "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;";

    // Shorthand for IOE + CNFE thrown clause
    private static final String[] IOEXC_CNFE =
            { "java/io/IOException", "java/lang/ClassNotFoundException" };

    // -----------------------------------------------------------------------
    // Real-class tests (loaded from classpath)
    // -----------------------------------------------------------------------

    /**
     * {@link AnalysisRequest} is a fully compliant {@code @AtomicSerial} class
     * that uses explicit casts (CHECKCAST) for all object-type fields in its
     * static check method — should be {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testRealClass_AnalysisRequest_isCompliant() throws Exception {
        byte[] classBytes = loadClassBytes(AnalysisRequest.class);
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * {@link JarAnalysisReport} uses both explicit casts and typed 3-arg
     * {@code get} calls in its check method — should be
     * {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testRealClass_JarAnalysisReport_isCompliant() throws Exception {
        byte[] classBytes = loadClassBytes(JarAnalysisReport.class);
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * {@link ClassAnalysisResult} previously used the 2-argument
     * {@code GetArg.get(String, Object)} form in a null-check without a
     * preceding CHECKCAST for the {@code className} field, which was an
     * UNTYPED_GET violation.  After fixing the check method to use
     * {@code arg.get(CLASS_NAME, null, String.class)} the class must
     * be {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testRealClass_ClassAnalysisResult_isCompliant() throws Exception {
        byte[] classBytes = loadClassBytes(ClassAnalysisResult.class);
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * {@link BytecodeAnalysisEngineProxy} is an {@code @AtomicSerial} child
     * class that extends {@link au.net.zeus.jgdms.proxy.AbstractSmartProxy},
     * which also implements {@code @AtomicSerial} and has a protected
     * {@code Object server} field.
     *
     * <p>The child class's static {@code check(GetArg)} method uses the
     * correct pattern: it constructs a private copy via the bridge constructor
     * (which calls {@code super(arg)}), then verifies the superclass field
     * with {@code instanceof BytecodeAnalysisEngine} before accepting the
     * object.  This must yield {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testRealClass_BytecodeAnalysisEngineProxy_isCompliant() throws Exception {
        byte[] classBytes = loadClassBytes(BytecodeAnalysisEngineProxy.class);
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    // -----------------------------------------------------------------------
    // Synthetic-class tests
    // -----------------------------------------------------------------------

    /**
     * A class annotated {@code @AtomicSerial} with a proper GetArg constructor
     * and a static check method that uses the <em>typed 3-argument</em>
     * {@code GetArg.get(String, Object, Class)} form.  This is the recommended
     * pattern and must yield {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testCompliant_typedGetInCheckMethod() {
        byte[] classBytes = buildCompliantClass_typedGet("TestTypedGet");
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * A class annotated {@code @AtomicSerial} with a proper GetArg constructor
     * and a static check method that uses the <em>2-argument</em>
     * {@code GetArg.get(String, Object)} form followed by an explicit
     * {@code CHECKCAST} (i.e. {@code (MyType) arg.get(name, null)}).
     * The type exception fires inside the static check method — this is safe
     * and must yield {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testCompliant_untypedGetWithCastInCheckMethod() {
        byte[] classBytes = buildCompliantClass_castInCheck("TestCastInCheck");
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * A class annotated {@code @AtomicSerial} with a proper GetArg constructor
     * and a static check method that uses the <em>2-argument</em>
     * {@code GetArg.get(String, Object)} form and then immediately checks
     * {@code == null} (IFNULL) <em>without</em> a preceding {@code CHECKCAST}.
     *
     * <p>The type of the deserialized object is never verified in the check
     * method; a CCE can fire inside the bridge constructor during object
     * construction.  Must yield {@link AtomicSerialVerdict#UNTYPED_GET}.
     */
    @Test
    public void testUntypedGet_nullCheckWithoutCast() {
        byte[] classBytes = buildUntypedGetClass("TestUntypedGet");
        assertEquals(AtomicSerialVerdict.UNTYPED_GET,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * A class annotated {@code @AtomicSerial} but with no
     * {@code (GetArg)} constructor — must yield
     * {@link AtomicSerialVerdict#MISSING_CONSTRUCTOR}.
     */
    @Test
    public void testMissingConstructor() {
        byte[] classBytes = buildMissingConstructorClass("TestMissingCtor");
        assertEquals(AtomicSerialVerdict.MISSING_CONSTRUCTOR,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * A class with a {@code (GetArg)} constructor but <em>no</em>
     * {@code @AtomicSerial} annotation — must yield
     * {@link AtomicSerialVerdict#NOT_ANNOTATED}.
     */
    @Test
    public void testNotAnnotated() {
        byte[] classBytes = buildNotAnnotatedClass("TestNotAnnotated");
        assertEquals(AtomicSerialVerdict.NOT_ANNOTATED,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * A class with a {@code (GetArg)} constructor where the static validation
     * call occurs <em>after</em> the {@code INVOKESPECIAL <init>} (validation
     * order inverted) — must yield {@link AtomicSerialVerdict#VALIDATION_ORDER}.
     */
    @Test
    public void testValidationOrder_checkAfterSuper() {
        byte[] classBytes = buildValidationOrderClass("TestValidOrder");
        assertEquals(AtomicSerialVerdict.VALIDATION_ORDER,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * A class annotated {@code @AtomicSerial} with a correct GetArg
     * constructor and a non-static field (which means it has serialized state
     * that must be declared), but no {@code serialForm()} method — must yield
     * {@link AtomicSerialVerdict#MISSING_SERIAL_FORM}.
     */
    @Test
    public void testMissingSerialForm() {
        byte[] classBytes = buildMissingSerialFormClass("TestMissingSerialForm");
        assertEquals(AtomicSerialVerdict.MISSING_SERIAL_FORM,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * A plain class that neither implements {@code Serializable} nor is
     * annotated with {@code @AtomicSerial} — must yield
     * {@link AtomicSerialVerdict#NA}.
     */
    @Test
    public void testNA_notSerializable() {
        byte[] classBytes = buildNonSerializableClass("TestNonSerializable");
        assertEquals(AtomicSerialVerdict.NA,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * Synthetic child class whose static check method constructs a private
     * copy, reads an inherited {@code Object}-typed field via GETFIELD, and
     * then uses {@code instanceof} to verify the type before a conditional
     * branch.  The {@code instanceof} instruction clears the pending-untyped
     * flag, so this must yield {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testCompliant_superclassObjectField_instanceofCheck() {
        byte[] classBytes = buildFieldInstanceofCheck("TestFieldInstanceof");
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * Synthetic child class whose static check method constructs a private
     * copy, reads an inherited {@code Object}-typed field via GETFIELD, and
     * then uses {@code CHECKCAST} to verify the type.  The cast fires inside
     * the static check method — before construction — so this is safe and
     * must yield {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testCompliant_superclassObjectField_checkcastInCheckMethod() {
        byte[] classBytes = buildFieldCheckcastCheck("TestFieldCheckcast");
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * Synthetic child class whose static check method constructs a private
     * copy, reads an inherited {@code Object}-typed field via GETFIELD, and
     * then immediately uses {@code IFNULL} <em>without</em> any preceding
     * {@code instanceof} or {@code CHECKCAST}.
     *
     * <p>The actual runtime type of the deserialized value is never verified
     * before the object is fully constructed — the same anti-pattern as
     * {@link #testUntypedGet_nullCheckWithoutCast()} but triggered via
     * GETFIELD access to a superclass protected field rather than a direct
     * {@code GetArg.get} call.  Must yield
     * {@link AtomicSerialVerdict#UNTYPED_GET}.
     */
    @Test
    public void testUntypedField_superclassObjectField_nullCheckOnly() {
        byte[] classBytes = buildFieldNullCheckNoTypeVerify("TestFieldNullCheck");
        assertEquals(AtomicSerialVerdict.UNTYPED_GET,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * Passing {@code null} class bytes to {@code analyze} must throw
     * {@link NullPointerException}.
     */
    @Test(expected = NullPointerException.class)
    public void testAnalyze_nullInput_throwsNPE() {
        AtomicSerialComplianceVisitor.analyze(null);
    }

    /**
     * Passing malformed (unparseable) class bytes must return a fail-secure
     * verdict of {@link AtomicSerialVerdict#MISSING_CONSTRUCTOR}.
     */
    @Test
    public void testAnalyze_malformedBytes_failSecure() {
        byte[] garbage = { 0, 1, 2, 3, 4, 5 };
        assertEquals(AtomicSerialVerdict.MISSING_CONSTRUCTOR,
                     AtomicSerialComplianceVisitor.analyze(garbage));
    }

    // -----------------------------------------------------------------------
    // Bytecode generators
    // -----------------------------------------------------------------------

    /**
     * Generates a minimal {@code @AtomicSerial} class with:
     * <ul>
     *   <li>a static check method that uses {@code GetArg.get(String,Object,Class)}
     *       (the typed 3-arg form) — the returned Object is popped;</li>
     *   <li>a proper {@code (GetArg)} public constructor that calls
     *       {@code this(arg, check(arg))};</li>
     *   <li>a bridge {@code (GetArg,boolean)} private constructor;</li>
     *   <li>a public static {@code serialForm()} method.</li>
     * </ul>
     */
    private static byte[] buildCompliantClass_typedGet(String simpleName) {
        ClassWriter cw = newAtomicSerialClass(simpleName);

        // static boolean check(GetArg arg) {
        //   arg.get("f", null, Object.class);  // typed form, result discarded
        //   return true;
        // }
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "check",
                "(L" + GET_ARG + ";)Z",
                null, IOEXC_CNFE);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);          // arg
        mv.visitLdcInsn("f");
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitLdcInsn(org.objectweb.asm.Type.getType(Object.class));
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GET_ARG, "get", TYPED_GET, false);
        mv.visitInsn(Opcodes.POP);                  // discard result
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(4, 1);
        mv.visitEnd();

        addGetArgCtor(cw, simpleName);
        addSerialFormMethod(cw);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a minimal {@code @AtomicSerial} class whose check method uses
     * {@code (String) arg.get(name, null)} — a 2-arg call followed
     * immediately by CHECKCAST.  The type exception fires in the check method.
     */
    private static byte[] buildCompliantClass_castInCheck(String simpleName) {
        ClassWriter cw = newAtomicSerialClass(simpleName);

        // static boolean check(GetArg arg) {
        //   String s = (String) arg.get("f", null);  // 2-arg + CHECKCAST
        //   return true;
        // }
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "check",
                "(L" + GET_ARG + ";)Z",
                null, IOEXC_CNFE);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);          // arg
        mv.visitLdcInsn("f");
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GET_ARG, "get", UNTYPED_GET, false);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String"); // cast present
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(3, 1);
        mv.visitEnd();

        addGetArgCtor(cw, simpleName);
        addSerialFormMethod(cw);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class whose check method uses
     * {@code if (arg.get("f", null) == null)} — a 2-arg call immediately
     * followed by {@code IFNULL} with NO preceding CHECKCAST.  This is the
     * anti-pattern that defers the type check to the bridge constructor.
     */
    private static byte[] buildUntypedGetClass(String simpleName) {
        ClassWriter cw = newAtomicSerialClass(simpleName);

        // static boolean check(GetArg arg) throws IOException {
        //   if (arg.get("f", null) == null)      // 2-arg + IFNULL, NO cast
        //     throw new IllegalArgumentException();
        //   return true;
        // }
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "check",
                "(L" + GET_ARG + ";)Z",
                null, new String[]{ "java/io/IOException" });
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitLdcInsn("f");
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GET_ARG, "get", UNTYPED_GET, false);
        Label ok = new Label();
        mv.visitJumpInsn(Opcodes.IFNONNULL, ok);  // IFNONNULL after get, NO CHECKCAST
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalArgumentException");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/IllegalArgumentException", "<init>", "()V", false);
        mv.visitInsn(Opcodes.ATHROW);
        mv.visitLabel(ok);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(3, 1);
        mv.visitEnd();

        addGetArgCtor(cw, simpleName);
        addSerialFormMethod(cw);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class annotated {@code @AtomicSerial} with NO
     * {@code (GetArg)} constructor.
     */
    private static byte[] buildMissingConstructorClass(String simpleName) {
        ClassWriter cw = newAtomicSerialClass(simpleName);
        // No GetArg constructor added
        addDefaultCtor(cw);
        addSerialFormMethod(cw);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class with a {@code (GetArg)} constructor but WITHOUT the
     * {@code @AtomicSerial} annotation.
     */
    private static byte[] buildNotAnnotatedClass(String simpleName) {
        // Build without @AtomicSerial
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V11,
                 Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                 "au/net/zeus/jgdms/bae/test/" + simpleName,
                 null,
                 "java/lang/Object",
                 new String[]{ SERIALIZABLE });
        // No @AtomicSerial annotation

        addGetArgCtor(cw, simpleName);
        addSerialFormMethod(cw);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class annotated {@code @AtomicSerial} where the
     * {@code (GetArg)} constructor calls {@code INVOKESPECIAL <init>}
     * <em>before</em> the static check call (inverted order).
     */
    private static byte[] buildValidationOrderClass(String simpleName) {
        ClassWriter cw = newAtomicSerialClass(simpleName);
        addSerialFormMethod(cw);

        // static boolean check(GetArg arg) { return true; }
        MethodVisitor cv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "check",
                "(L" + GET_ARG + ";)Z",
                null, IOEXC_CNFE);
        cv.visitCode();
        cv.visitInsn(Opcodes.ICONST_1);
        cv.visitInsn(Opcodes.IRETURN);
        cv.visitMaxs(1, 1);
        cv.visitEnd();

        // (GetArg) constructor — INVOKESPECIAL <init> BEFORE INVOKESTATIC check
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC,
                "<init>",
                GET_ARG_CTOR_DESC,
                null, IOEXC_CNFE);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        // INVOKESPECIAL bridge ctor first (wrong order)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "au/net/zeus/jgdms/bae/test/" + simpleName,
                "<init>",
                "(L" + GET_ARG + ";Z)V",
                false);
        // INVOKESTATIC check after (too late)
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                "au/net/zeus/jgdms/bae/test/" + simpleName,
                "check",
                "(L" + GET_ARG + ";)Z",
                false);
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(3, 2);
        mv.visitEnd();

        // Bridge constructor (GetArg, boolean)
        addBridgeCtor(cw, simpleName);

        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class annotated {@code @AtomicSerial} with a correct
     * GetArg constructor, a non-static serializable field (which means the
     * class is expected to provide {@code serialForm()}), but <em>no</em>
     * {@code serialForm()} method.
     */
    private static byte[] buildMissingSerialFormClass(String simpleName) {
        ClassWriter cw = newAtomicSerialClass(simpleName);

        // Add a non-static, non-transient instance field so that the class
        // is expected to provide serialForm() to declare its serial state.
        cw.visitField(Opcodes.ACC_PRIVATE, "value", "Ljava/lang/String;",
                      null, null).visitEnd();

        // check method that uses typed get (so not UNTYPED_GET)
        MethodVisitor cv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "check",
                "(L" + GET_ARG + ";)Z",
                null, IOEXC_CNFE);
        cv.visitCode();
        cv.visitInsn(Opcodes.ICONST_1);
        cv.visitInsn(Opcodes.IRETURN);
        cv.visitMaxs(1, 1);
        cv.visitEnd();

        addGetArgCtor(cw, simpleName);
        // NO serialForm() added
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a plain class that is neither {@code Serializable} nor
     * annotated with {@code @AtomicSerial}.
     */
    private static byte[] buildNonSerializableClass(String simpleName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V11,
                 Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                 "au/net/zeus/jgdms/bae/test/" + simpleName,
                 null,
                 "java/lang/Object",
                 null);   // No Serializable
        addDefaultCtor(cw);
        cw.visitEnd();
        return cw.toByteArray();
    }

    // -----------------------------------------------------------------------
    // ASM builder helpers
    // -----------------------------------------------------------------------

    /**
     * Creates a {@link ClassWriter} for a class that implements
     * {@code Serializable} and is annotated with {@code @AtomicSerial}.
     */
    private static ClassWriter newAtomicSerialClass(String simpleName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V11,
                 Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                 "au/net/zeus/jgdms/bae/test/" + simpleName,
                 null,
                 "java/lang/Object",
                 new String[]{ SERIALIZABLE });
        AnnotationVisitor av = cw.visitAnnotation(
                "L" + ATOMIC_SERIAL + ";", true);
        av.visitEnd();
        return cw;
    }

    /**
     * Adds the standard {@code public (GetArg)} constructor that delegates to
     * a private bridge constructor via {@code this(arg, check(arg))}.
     */
    private static void addGetArgCtor(ClassWriter cw, String simpleName) {
        String className = "au/net/zeus/jgdms/bae/test/" + simpleName;

        // public Foo(GetArg arg) throws IOException, ClassNotFoundException {
        //     this(arg, check(arg));
        // }
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC,
                "<init>",
                GET_ARG_CTOR_DESC,
                null, IOEXC_CNFE);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);           // this
        mv.visitVarInsn(Opcodes.ALOAD, 1);           // arg
        mv.visitVarInsn(Opcodes.ALOAD, 1);           // arg (for check call)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                className, "check", "(L" + GET_ARG + ";)Z", false);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                className, "<init>", "(L" + GET_ARG + ";Z)V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(3, 2);
        mv.visitEnd();

        addBridgeCtor(cw, simpleName);
    }

    /** Adds a trivial {@code private (GetArg, boolean)} bridge constructor. */
    private static void addBridgeCtor(ClassWriter cw, String simpleName) {
        String className = "au/net/zeus/jgdms/bae/test/" + simpleName;
        MethodVisitor bv = cw.visitMethod(
                Opcodes.ACC_PRIVATE,
                "<init>",
                "(L" + GET_ARG + ";Z)V",
                null, IOEXC_CNFE);
        bv.visitCode();
        bv.visitVarInsn(Opcodes.ALOAD, 0);
        bv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false);
        bv.visitInsn(Opcodes.RETURN);
        bv.visitMaxs(1, 3);
        bv.visitEnd();
    }

    /** Adds a trivial no-arg public constructor (delegates to Object). */
    private static void addDefaultCtor(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    /**
     * Adds a minimal {@code public static SerialForm[] serialForm()} method
     * that returns an empty array.
     */
    private static void addSerialFormMethod(ClassWriter cw) {
        MethodVisitor sv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "serialForm",
                "()[L" + SERIAL_FORM + ";",
                null, null);
        sv.visitCode();
        sv.visitInsn(Opcodes.ICONST_0);
        sv.visitTypeInsn(Opcodes.ANEWARRAY, SERIAL_FORM);
        sv.visitInsn(Opcodes.ARETURN);
        sv.visitMaxs(1, 0);
        sv.visitEnd();
    }

    // -----------------------------------------------------------------------
    // Classpath loader helper
    // -----------------------------------------------------------------------

    /**
     * Generates a class with an {@code Object}-typed field {@code server}
     * whose static check method:
     * <ol>
     *   <li>constructs a private copy via the bridge constructor;</li>
     *   <li>reads the {@code server} field via {@code GETFIELD};</li>
     *   <li>checks its type with {@code instanceof Serializable}.</li>
     * </ol>
     *
     * <p>The {@code instanceof} instruction clears the pending-untyped-field
     * flag, so the result must be {@link AtomicSerialVerdict#COMPLIANT}.
     */
    private static byte[] buildFieldInstanceofCheck(String simpleName) {
        String className = "au/net/zeus/jgdms/bae/test/" + simpleName;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V11,
                 Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                 className, null, "java/lang/Object",
                 new String[]{ SERIALIZABLE });
        AnnotationVisitor av = cw.visitAnnotation("L" + ATOMIC_SERIAL + ";", true);
        av.visitEnd();

        // protected Object server — simulates an inherited Object-typed field
        cw.visitField(Opcodes.ACC_PROTECTED, "server", "Ljava/lang/Object;",
                      null, null).visitEnd();

        // static boolean check(GetArg arg) {
        //   TestFieldInstanceof sup = new TestFieldInstanceof(arg, true);
        //   Object s = sup.server;
        //   if (!(s instanceof Serializable)) throw new InvalidObjectException("...");
        //   return true;
        // }
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "check", "(L" + GET_ARG + ";)Z", null, IOEXC_CNFE);
        mv.visitCode();
        // new TestFieldInstanceof(arg, true) — private bridge ctor
        mv.visitTypeInsn(Opcodes.NEW, className);
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 0);   // arg
        mv.visitInsn(Opcodes.ICONST_1);      // true
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, className, "<init>",
                "(L" + GET_ARG + ";Z)V", false);
        mv.visitVarInsn(Opcodes.ASTORE, 1);  // sup = private copy
        // sup.server  (GETFIELD, descriptor Ljava/lang/Object;)
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.GETFIELD, className, "server", "Ljava/lang/Object;");
        // instanceof Serializable  — clears pendingUntypedField
        mv.visitTypeInsn(Opcodes.INSTANCEOF, SERIALIZABLE);
        Label ok = new Label();
        mv.visitJumpInsn(Opcodes.IFNE, ok);  // if true, continue
        mv.visitTypeInsn(Opcodes.NEW, "java/io/InvalidObjectException");
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("wrong type");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/io/InvalidObjectException", "<init>",
                "(Ljava/lang/String;)V", false);
        mv.visitInsn(Opcodes.ATHROW);
        mv.visitLabel(ok);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(4, 2);
        mv.visitEnd();

        addGetArgCtor(cw, simpleName);
        addSerialFormMethod(cw);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class with an {@code Object}-typed field {@code server}
     * whose static check method:
     * <ol>
     *   <li>constructs a private copy via the bridge constructor;</li>
     *   <li>reads the {@code server} field via {@code GETFIELD};</li>
     *   <li>immediately applies a {@code CHECKCAST} (i.e.
     *       {@code (Serializable) sup.server}).</li>
     * </ol>
     *
     * <p>The {@code CHECKCAST} fires inside the static check method — before
     * any object construction completes — so this is safe.
     * Must yield {@link AtomicSerialVerdict#COMPLIANT}.
     */
    private static byte[] buildFieldCheckcastCheck(String simpleName) {
        String className = "au/net/zeus/jgdms/bae/test/" + simpleName;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V11,
                 Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                 className, null, "java/lang/Object",
                 new String[]{ SERIALIZABLE });
        AnnotationVisitor av = cw.visitAnnotation("L" + ATOMIC_SERIAL + ";", true);
        av.visitEnd();

        cw.visitField(Opcodes.ACC_PROTECTED, "server", "Ljava/lang/Object;",
                      null, null).visitEnd();

        // static boolean check(GetArg arg) {
        //   TestFieldCheckcast sup = new TestFieldCheckcast(arg, true);
        //   Serializable s = (Serializable) sup.server;  // GETFIELD + CHECKCAST
        //   return true;
        // }
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "check", "(L" + GET_ARG + ";)Z", null, IOEXC_CNFE);
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, className);
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, className, "<init>",
                "(L" + GET_ARG + ";Z)V", false);
        mv.visitVarInsn(Opcodes.ASTORE, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.GETFIELD, className, "server", "Ljava/lang/Object;");
        // CHECKCAST — clears pendingUntypedField
        mv.visitTypeInsn(Opcodes.CHECKCAST, SERIALIZABLE);
        mv.visitInsn(Opcodes.POP);   // discard the cast result
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(4, 2);
        mv.visitEnd();

        addGetArgCtor(cw, simpleName);
        addSerialFormMethod(cw);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class with an {@code Object}-typed field {@code server}
     * whose static check method:
     * <ol>
     *   <li>constructs a private copy via the bridge constructor;</li>
     *   <li>reads the {@code server} field via {@code GETFIELD};</li>
     *   <li>immediately uses {@code IFNULL} with <em>no</em> preceding
     *       {@code instanceof} or {@code CHECKCAST}.</li>
     * </ol>
     *
     * <p>The actual runtime type of the deserialized value is never verified
     * before the object is fully constructed — a CCE can occur during field
     * use in application code.  Must yield
     * {@link AtomicSerialVerdict#UNTYPED_GET}.
     */
    private static byte[] buildFieldNullCheckNoTypeVerify(String simpleName) {
        String className = "au/net/zeus/jgdms/bae/test/" + simpleName;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V11,
                 Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                 className, null, "java/lang/Object",
                 new String[]{ SERIALIZABLE });
        AnnotationVisitor av = cw.visitAnnotation("L" + ATOMIC_SERIAL + ";", true);
        av.visitEnd();

        cw.visitField(Opcodes.ACC_PROTECTED, "server", "Ljava/lang/Object;",
                      null, null).visitEnd();

        // static boolean check(GetArg arg) {
        //   TestFieldNullCheck sup = new TestFieldNullCheck(arg, true);
        //   if (sup.server == null)          // GETFIELD + IFNULL, NO instanceof/CHECKCAST
        //     throw new InvalidObjectException("null server");
        //   return true;
        // }
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "check", "(L" + GET_ARG + ";)Z", null, IOEXC_CNFE);
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, className);
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, className, "<init>",
                "(L" + GET_ARG + ";Z)V", false);
        mv.visitVarInsn(Opcodes.ASTORE, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.GETFIELD, className, "server", "Ljava/lang/Object;");
        // IFNULL without any preceding instanceof or CHECKCAST — anti-pattern!
        Label ok = new Label();
        mv.visitJumpInsn(Opcodes.IFNONNULL, ok);
        mv.visitTypeInsn(Opcodes.NEW, "java/io/InvalidObjectException");
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("null server");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/io/InvalidObjectException", "<init>",
                "(Ljava/lang/String;)V", false);
        mv.visitInsn(Opcodes.ATHROW);
        mv.visitLabel(ok);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(4, 2);
        mv.visitEnd();

        addGetArgCtor(cw, simpleName);
        addSerialFormMethod(cw);
        cw.visitEnd();
        return cw.toByteArray();
    }

    // -----------------------------------------------------------------------
    // Classpath loader helper
    // -----------------------------------------------------------------------

    /**
     * Loads raw {@code .class} bytes for the given class from the classpath.
     *
     * @param cls class to load
     * @return raw class bytes
     * @throws Exception if the resource cannot be found or read
     */
    private static byte[] loadClassBytes(Class<?> cls) throws Exception {
        String resourcePath = "/" + cls.getName().replace('.', '/') + ".class";
        InputStream is = AtomicSerialComplianceVisitorTest.class
                .getResourceAsStream(resourcePath);
        assertNotNull("Cannot find classpath resource: " + resourcePath, is);
        ByteArrayOutputStream buf = new ByteArrayOutputStream(8192);
        byte[] tmp = new byte[8192];
        int n;
        while ((n = is.read(tmp)) != -1) {
            buf.write(tmp, 0, n);
        }
        return buf.toByteArray();
    }
}
