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
import au.net.zeus.jgdms.api.codebase.BytecodeAnalysisEngine;
import au.net.zeus.jgdms.api.codebase.ClassAnalysisResult;
import au.net.zeus.jgdms.api.codebase.CrashReport;
import au.net.zeus.jgdms.api.codebase.JarAnalysisReport;
import au.net.zeus.jgdms.api.codebase.RegistryVerdict;
import au.net.zeus.jgdms.api.codebase.VerdictEvent;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.lookup.ServiceEvent;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.id.Uuid;
import org.apache.river.discovery.MulticastTimeToLive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Unit tests for {@link AtomicSerialComplianceVisitor}.
 *
 * <p>Each test generates a synthetic class via ASM {@link ClassWriter} that
 * exhibits a specific bytecode pattern and then verifies the expected
 * {@link AtomicSerialVerdict}.  Real JGDMS {@code @AtomicSerial} classes are
 * also loaded from the classpath and checked for
 * {@link AtomicSerialVerdict#COMPLIANT}.
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

    /** {@code org/apache/river/api/io/AtomicSerial$PutArg} */
    private static final String PUT_ARG =
            "org/apache/river/api/io/AtomicSerial$PutArg";

    /** {@code Lorg/apache/river/api/io/AtomicSerial$PutArg;} */
    private static final String PUT_ARG_DESC =
            "L" + PUT_ARG + ";";

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
     * <p>The child class is {@code @Stateless}: it declares no own serialized
     * fields and its {@code (GetArg)} constructor simply delegates to
     * {@code super(arg)}, where {@code AbstractSmartProxy} validates the
     * inherited {@code server}/{@code proxyID} (via {@code checkServer}) before
     * any field is assigned.  Because a {@code @Stateless} class has no fields
     * of its own to assign, the validation-order rule is vacuously satisfied,
     * so this must yield {@link AtomicSerialVerdict#COMPLIANT}.
     */
    //TODO: Update test to use alternative smart proxy.
//    @Test
//    public void testRealClass_BytecodeAnalysisEngineProxy_isCompliant() throws Exception {
//        byte[] classBytes = loadClassBytes(BytecodeAnalysisEngineProxy.class);
//        assertEquals(AtomicSerialVerdict.COMPLIANT,
//                     AtomicSerialComplianceVisitor.analyze(classBytes));
//    }

    /**
     * {@link CrashReport} uses explicit {@code CHECKCAST} for byte-array and
     * String-array fields and the typed 3-argument form for {@code String}
     * (stderr summary).  This must yield {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testRealClass_CrashReport_isCompliant() throws Exception {
        byte[] classBytes = loadClassBytes(CrashReport.class);
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * {@link RegistryVerdict} uses CHECKCAST for arrays and the typed 3-arg
     * form for the verdict enum, and must yield
     * {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testRealClass_RegistryVerdict_isCompliant() throws Exception {
        byte[] classBytes = loadClassBytes(RegistryVerdict.class);
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * {@link RemoteEvent} uses a non-{@code boolean}-returning check method
     * ({@code static Object check(GetArg)}) so the {@code CheckMethodAnalyzer}
     * does not inspect it, but the constructor still calls that static method
     * before the bridge constructor — {@code getArgCtorValidationOk} is
     * {@code true}.  The class also has a {@code serialForm()} method and
     * non-static fields.  Expected result: {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testRealClass_RemoteEvent_isCompliant() throws Exception {
        byte[] classBytes = loadClassBytes(RemoteEvent.class);
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * {@link LookupLocator} uses only typed 3-argument {@code GetArg.get}
     * calls ({@code arg.get("host", null, String.class)} and
     * {@code arg.get("port", 0)}) — no untyped 2-argument form at all.
     * Expected result: {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testRealClass_LookupLocator_isCompliant() throws Exception {
        byte[] classBytes = loadClassBytes(LookupLocator.class);
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * {@link Uuid} uses the standard {@code check(GetArg) boolean} pattern with
     * a bridge constructor {@code Uuid(GetArg, boolean)}.  Its {@code check}
     * method reads only primitive {@code long} fields — no 2-argument
     * {@code GetArg.get(String, Object)} form is used at all.  The class also
     * has a {@code serialForm()} method.
     * Expected result: {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testRealClass_Uuid_isCompliant() throws Exception {
        byte[] classBytes = loadClassBytes(Uuid.class);
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * {@link ServiceID} uses a pair of private static helper methods
     * ({@code mostSig(GetArg)} and {@code leastSig(GetArg)}) that return
     * {@code long} primitives, chaining to {@code this(long, long)}.
     * Because {@code INVOKESTATIC} calls precede the {@code INVOKESPECIAL} in
     * the {@code (GetArg)} constructor, {@code getArgCtorValidationOk} is
     * {@code true}.  The identified "check" method ({@code leastSig}) returns
     * {@code long}, not {@code boolean}, so no {@link CheckMethodAnalyzer} is
     * applied.  The class declares {@code serialForm()} and has only primitive
     * fields.
     * Expected result: {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testRealClass_ServiceID_isCompliant() throws Exception {
        byte[] classBytes = loadClassBytes(ServiceID.class);
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * {@link EventRegistration} has a standard {@code check(GetArg) boolean}
     * pattern with bridge constructor {@code EventRegistration(boolean, GetArg)}.
     *
     * <p>In the check method, {@code Object source = arg.get("source", null)}
     * stores to a local via {@code ASTORE} before the {@code IFNONNULL} null
     * check — the {@code ASTORE} resets {@code pendingUntypedGet} so the
     * conditional is never flagged.  Similarly, {@code arg.get("lease", null)}
     * is stored to a local before the {@code instanceof Lease} check, which
     * uses {@code IFEQ} (not {@code IFNULL}/{@code IFNONNULL}).
     * Expected result: {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testRealClass_EventRegistration_isCompliant() throws Exception {
        byte[] classBytes = loadClassBytes(EventRegistration.class);
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * {@link ServiceEvent} uses {@code static GetArg check(GetArg)} — the
     * check method returns {@code GetArg}, not {@code boolean}.  Because the
     * descriptor does not end with {@code )Z}, no {@link CheckMethodAnalyzer}
     * is created for it; the UNTYPED_GET detector is not applied.
     * Validation ordering is confirmed: {@code INVOKESTATIC check} precedes
     * {@code INVOKESPECIAL super.<init>(GetArg)} in the public constructor.
     * The class has {@code serialForm()} and declares non-transient instance
     * fields.
     * Expected result: {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testRealClass_ServiceEvent_isCompliant() throws Exception {
        byte[] classBytes = loadClassBytes(ServiceEvent.class);
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * {@link MulticastTimeToLive} has a package-private {@code (GetArg)}
     * constructor that chains directly to {@code this(arg.get("ttl", -1))}
     * using an {@code INVOKEVIRTUAL} primitive getter — there is no
     * {@code INVOKESTATIC} call before the {@code INVOKESPECIAL this(int)}.
     * Validation (the {@code check(int)} range guard) happens deeper in the
     * constructor chain, not at the {@code GetArg} constructor level.
     *
     * <p>The analyzer therefore sets {@code getArgCtorValidationOk = false},
     * indicating that the static validation is not confirmed to precede the
     * bridge constructor call in the {@code (GetArg)} constructor itself.
     * Expected result: {@link AtomicSerialVerdict#VALIDATION_ORDER}.
     */
    @Test
    public void testRealClass_MulticastTimeToLive_isValidationOrder() throws Exception {
        byte[] classBytes = loadClassBytes(MulticastTimeToLive.class);
        assertEquals(AtomicSerialVerdict.VALIDATION_ORDER,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * {@link VerdictEvent} extends {@link RemoteEvent} and uses
     * {@code static GetArg check(GetArg)} (returning {@code GetArg}) in the
     * pattern {@code super(check(arg))}.
     *
     * <p>Because {@code INVOKESTATIC check} precedes the {@code INVOKESPECIAL
     * super.<init>(GetArg)}, {@code getArgCtorValidationOk} is {@code true}.
     * The check method descriptor ends with {@code )Lorg/apache/river/api/io/AtomicSerial$GetArg;}
     * (not {@code )Z}), so no {@link CheckMethodAnalyzer} is applied —
     * {@code UNTYPED_GET} detection is skipped.
     * The class declares {@code serialForm()} and has one non-transient field.
     * Expected result: {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testRealClass_VerdictEvent_isCompliant() throws Exception {
        byte[] classBytes = loadClassBytes(VerdictEvent.class);
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * {@link ConstraintAlternatives} uses
     * {@code this(validate(arg.get("constraints", null, InvocationConstraint[].class)), false)}
     * in the {@code (GetArg)} constructor.  The static {@code validate} method
     * is called via {@code INVOKESTATIC} before {@code INVOKESPECIAL this(...)},
     * satisfying the validation-before-construction ordering requirement.
     * The 3-arg typed form of {@code arg.get} is used, so no {@code UNTYPED_GET}
     * issue arises.
     * Expected result: {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testRealClass_ConstraintAlternatives_isCompliant() throws Exception {
        byte[] classBytes = loadClassBytes(ConstraintAlternatives.class);
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * {@link InvocationConstraints} uses the two-step constructor chain:
     * <pre>
     * public InvocationConstraints(GetArg arg) {
     *     this(arg.get("reqs", null, ...), arg.get("prefs", null, ...), true);
     * }
     * </pre>
     * No {@code INVOKESTATIC} call appears before {@code INVOKESPECIAL this(...)}
     * in the {@code (GetArg)} constructor body.  Validation ({@code check(reqs, prefs)})
     * occurs one level deeper in the private {@code (InvocationConstraint[],
     * InvocationConstraint[], boolean)} constructor, not at the {@code GetArg}
     * constructor level.  The {@link GetArgCtorAnalyzer} therefore sets
     * {@code getArgCtorValidationOk = false}.
     * Expected result: {@link AtomicSerialVerdict#VALIDATION_ORDER}.
     */
    @Test
    public void testRealClass_InvocationConstraints_isValidationOrder() throws Exception {
        byte[] classBytes = loadClassBytes(InvocationConstraints.class);
        assertEquals(AtomicSerialVerdict.VALIDATION_ORDER,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * {@link ServiceItem} uses the pattern:
     * <pre>
     * public ServiceItem(GetArg arg) {
     *     this(arg == null ? null : arg.get(SERVICE_ID, null, ServiceID.class),
     *          arg == null ? null : arg.get(SERVICE, null),
     *          arg == null ? null : arg.get(ATTRIBUTE_SETS, null, Entry[].class));
     * }
     * </pre>
     * No static check method is called before {@code this(...)}.  The class
     * relies on the three-argument delegating constructor for null handling.
     * {@code INVOKESTATIC} is absent before {@code INVOKESPECIAL this(...)}
     * in the {@code (GetArg)} constructor, so {@code getArgCtorValidationOk}
     * is {@code false}.
     * Expected result: {@link AtomicSerialVerdict#VALIDATION_ORDER}.
     */
    @Test
    public void testRealClass_ServiceItem_isValidationOrder() throws Exception {
        byte[] classBytes = loadClassBytes(ServiceItem.class);
        assertEquals(AtomicSerialVerdict.VALIDATION_ORDER,
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

    // -----------------------------------------------------------------------
    // Gap 2A tests — genuine validation ordering (not just "some INVOKESTATIC
    // occurred before super()/this()")
    // -----------------------------------------------------------------------

    /**
     * A {@code (GetArg)} constructor whose body is only
     * {@code super(Objects.requireNonNull(arg))} — an {@code INVOKESTATIC}
     * precedes {@code super()}, but it is {@code Objects.requireNonNull},
     * not anything that reads the contents of {@code arg}. Its erased
     * descriptor {@code (Ljava/lang/Object;)Ljava/lang/Object;} does not
     * even mention {@code GetArg}, so it never becomes an ordering
     * candidate at all. Must NOT be graded {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testValidationOrder_innocuousStaticThenSuper() {
        byte[] classBytes = buildInnocuousStaticThenSuperClass("TestInnocuousStatic");
        assertEquals(AtomicSerialVerdict.VALIDATION_ORDER,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * A proper {@code check(GetArg)}-shaped static method is correctly
     * called before {@code super()}/{@code this()}, but an unrelated static
     * call ({@code Objects.requireNonNull}) is <em>also</em> invoked between
     * the {@code check(arg)} call and the terminal bridge-constructor call —
     * i.e. {@code check} is <em>not</em> the last {@code INVOKESTATIC} seen.
     * The analyzer must still find and credit {@code check} (which uses the
     * typed 3-argument {@code get} form and branches on the result — a
     * genuine, real check), rather than only ever considering "whichever
     * call was last". Must yield {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testCompliant_realCheckMethodNotLastPreSuperStatic() {
        byte[] classBytes = buildInterveningUnrelatedStaticsClass("TestInterveningStatics");
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * A {@code check(GetArg)}-shaped static method that takes the parameter
     * but never calls anything on it at all — {@code return true;}
     * unconditionally. The right shape (takes {@code GetArg}, returns
     * {@code boolean}, called before {@code super()}) with zero engagement
     * with its input must not be credited as validation. Must NOT be graded
     * {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testValidationOrder_checkNeverReadsParameter() {
        byte[] classBytes = buildVacuousCheckNeverReadsParamClass("TestVacuousNoRead");
        assertEquals(AtomicSerialVerdict.VALIDATION_ORDER,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * A {@code check(GetArg)}-shaped static method that <em>does</em> call
     * {@code arg.get(...)} (the untyped 2-argument form) but immediately
     * discards the result (a bare statement-expression, compiled to a
     * {@code POP}) and unconditionally returns {@code true} — distinct from
     * {@link #testValidationOrder_checkNeverReadsParameter()}: the parameter
     * <em>is</em> touched, but the outcome is not influenced by what was
     * read. Must NOT be graded {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testValidationOrder_checkCallsGetButIgnoresResult() {
        byte[] classBytes = buildVacuousCheckIgnoresGetResultClass("TestVacuousIgnoresGet");
        assertEquals(AtomicSerialVerdict.VALIDATION_ORDER,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    // -----------------------------------------------------------------------
    // Gap 2A hardening — "consumed by a call" is not, by itself, evidence.
    // An independent reviewer built and ran these exact three bypass shapes
    // against the (at-the-time) compiled analyzer and confirmed each one
    // returned COMPLIANT before this hardening: the analyzer credited a
    // tainted value merely being *passed to* a further call, without
    // checking whether that call's own result went anywhere.
    // -----------------------------------------------------------------------

    /**
     * Originally-proven bypass #1 (dedicated check method, typed
     * {@code get()} with a <em>meaningfully-constraining</em> declared
     * type): {@code check(GetArg arg){ String.valueOf(arg.get("f", null,
     * String.class)); return true; }}.
     *
     * <p><b>Re-classified, not a bypass.</b> The typed 3-argument
     * {@code get(name, default, Class)} call throws {@code
     * InvalidObjectException} internally, as a side effect of the call
     * itself, if field {@code "f"} is present and not a {@code String} —
     * that is real, self-contained validation of field {@code "f"},
     * independent of whatever {@code String.valueOf} then does with the
     * (already safely-typed) result. A follow-up review confirmed several
     * real classes in this codebase rely on exactly this idiom (a typed
     * read whose value is otherwise unused) and were being incorrectly
     * rejected before this was recognised — see
     * {@link AtomicSerialComplianceVisitor.CheckMethodAnalyzer#isSelfValidatingGetForm}.
     * Must now be graded {@link AtomicSerialVerdict#COMPLIANT}.
     *
     * <p>This does <em>not</em> mean "any call is fine as long as some
     * typed read happens somewhere" — see
     * {@link #testValidationOrder_dedicatedCheckMethod_typedGetWithUnconstrainingObjectClass_stillRejected()}
     * for the one declared type ({@code Object.class}) that is
     * deliberately excluded because it can never actually throw, and the
     * disclosed "right field, not just a field" residual limitation this
     * change does not attempt to close.
     *
     * <p><b>Also a Fix 1 no-regression check.</b> Field {@code "f"} is
     * declared by {@link #addSerialFormMethod(ClassWriter)} (used here), so
     * this confirms a declared name paired with a real, meaningfully-
     * constraining type still credits after Fix 1's field-name cross-check
     * landed — see
     * {@link #testValidationOrder_dedicatedCheckMethod_typedGetFieldNameNotDeclared_bypassClosed()}
     * for the companion case where the name is <em>not</em> declared.
     */
    @Test
    public void testCompliant_dedicatedCheckMethod_typedGetSelfValidatesEvenWhenResultDiscarded() {
        byte[] classBytes = buildCheckMethodGetLaunderedThroughExternalCallClass(
                "TestTypedSelfValidatingDiscarded", TYPED_GET, String.class);
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * Same shape as above, but the declared type is {@code Object.class}:
     * {@code check(GetArg arg){ String.valueOf(arg.get("f", null,
     * Object.class)); return true; }}. {@code instanceof Object} is true
     * for every non-null reference, so this call can never throw — it is
     * typed in name only and provides no real constraint, no better than
     * the untyped form. Must NOT be graded
     * {@link AtomicSerialVerdict#COMPLIANT}.
     *
     * <p><b>Also a Fix 1 no-regression check.</b> Field {@code "f"} <em>is</em>
     * declared by {@code serialForm()} here — this confirms the {@code
     * Object.class} exclusion still applies even for a properly-declared
     * field name: the field-name cross-check narrows the bypass, it does
     * not replace the separate {@code Object.class} exclusion.
     */
    @Test
    public void testValidationOrder_dedicatedCheckMethod_typedGetWithUnconstrainingObjectClass_stillRejected() {
        byte[] classBytes = buildCheckMethodGetLaunderedThroughExternalCallClass(
                "TestTypedObjectClassStillRejected", TYPED_GET, Object.class);
        assertEquals(AtomicSerialVerdict.VALIDATION_ORDER,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * Fix 1's proven bypass, closed: {@code check(GetArg arg){
     * String.valueOf(arg.get("totally_bogus_field_name_xyz", null,
     * AtomicLong.class)); return true; }}, where {@code
     * "totally_bogus_field_name_xyz"} is <em>not</em> declared anywhere in
     * this class's own {@code serialForm()} (an empty array here — see
     * {@link #addEmptySerialFormMethod(ClassWriter)}).
     *
     * <p>Before Fix 1, this was graded {@link AtomicSerialVerdict#COMPLIANT}
     * with <em>any</em> declared type, narrow or not: {@code
     * AtomicSerial.GetArg.get(String,T,Class&lt;T&gt;)} returns the supplied
     * default before ever calling {@code type.isInstance(v)} whenever the
     * named field is absent from the incoming stream ({@code v == ABSENT ||
     * v == null}), and a name absent from the class's own declared schema
     * can never be anything <em>but</em> absent — so the call could never
     * throw regardless of the declared type, making the "self-validating"
     * premise categorically false. Must now be graded
     * {@link AtomicSerialVerdict#VALIDATION_ORDER}.
     */
    @Test
    public void testValidationOrder_dedicatedCheckMethod_typedGetFieldNameNotDeclared_bypassClosed() {
        byte[] classBytes = buildCheckMethodGetLaunderedThroughExternalCallClass(
                "TestTypedFieldNameNotDeclared", TYPED_GET, java.util.concurrent.atomic.AtomicLong.class,
                "totally_bogus_field_name_xyz", false);
        assertEquals(AtomicSerialVerdict.VALIDATION_ORDER,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * Same shape as the proven bypass above, but with the untyped 2-argument
     * {@code get()} form: {@code check(GetArg arg){ String.valueOf(arg.get(
     * "f", null)); return true; }}. The untyped form has no built-in throw
     * protection at all (unlike the typed 3-argument form above), so it
     * remains under the stricter reachable-outcome requirement and must
     * still not be graded {@link AtomicSerialVerdict#COMPLIANT}. Confirms
     * the untyped-get detector's "coincidental" catch (it independently
     * flags the untyped read the instant it is consumed by
     * {@code String.valueOf}) does not silently regress into
     * {@code COMPLIANT} either. The overall verdict is
     * {@link AtomicSerialVerdict#VALIDATION_ORDER} (ordering, checked
     * first, correctly rejects this method on its own), which is DANGEROUS
     * in {@link au.net.zeus.jgdms.api.codebase.JarAnalysisReport} the same
     * as {@code UNTYPED_GET} would have been.
     */
    @Test
    public void testValidationOrder_dedicatedCheckMethod_untypedGetLaunderedThroughDiscardedExternalCall() {
        byte[] classBytes = buildCheckMethodGetLaunderedThroughExternalCallClass(
                "TestLaunderedUntypedDedicated", UNTYPED_GET, String.class);
        assertEquals(AtomicSerialVerdict.VALIDATION_ORDER,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * Proven bypass #3 (pure inline extraction, no dedicated check method at
     * all): {@code this(arg, String.valueOf(arg.get("f", null,
     * String.class)))} with a bridge constructor that ignores its second
     * (String) parameter entirely. Unlike the dedicated-check-method shapes
     * above, this path has no untyped-get safety net available at all (that
     * cross-check only ever fires for a separately-declared {@code
     * (GetArg)Z} check-method candidate) — before this hardening it was
     * <em>fully unmitigated</em>. Must NOT be graded
     * {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testValidationOrder_inlineExtraction_typedGetLaunderedThroughDiscardedExternalCall() {
        byte[] classBytes = buildInlineExtractionLaunderedThroughExternalCallClass(
                "TestLaunderedInline");
        assertEquals(AtomicSerialVerdict.VALIDATION_ORDER,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * Regression test for the {@code org.apache.river.lookup.util.ConsistentSet}
     * false positive found by the full real-class corpus rescan: inline
     * extraction where a read value flows through an <em>external</em> call
     * first and only then reaches a genuine <em>internal</em> one, e.g.
     * {@code this(sameClassHelper(java.util.Objects.toString(arg.get("f",
     * null))))}. The external {@code Objects.toString} call must not sever
     * the chain outright — it should propagate the pending value through to
     * the internal {@code sameClassHelper(...)} call that follows, which
     * then finalises genuineness immediately as usual. Must be graded
     * {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testCompliant_inlineExtraction_valueFlowsThroughExternalCallThenInternalCall() {
        byte[] classBytes = buildInlineExtractionExternalThenInternalCallClass(
                "TestExternalThenInternal");
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * Fix 2's {@code isInitCall} false positive, closed: a dedicated check
     * method whose body is {@code new TestFoo(arg.get("f", null,
     * Object.class))} — a genuine, same-class constructor call that
     * consumes a real {@code get()} result — followed by an unconditional
     * {@code return true}:
     * <pre>
     *   private static boolean check(GetArg arg) throws ... {
     *     new TestFoo(arg.get("f", null, Object.class));   // NEW ... INVOKESPECIAL &lt;init&gt;
     *     return true;
     *   }
     *   private TestFoo(Object x) { super(); }
     * </pre>
     * The declared type is deliberately {@code Object.class} so this test
     * isolates Fix 2 from Fix 1: the typed {@code get()} call here can
     * <em>never</em> qualify for Fix 1's self-validating credit ({@code
     * Object.class} is always excluded — see
     * {@link #testValidationOrder_dedicatedCheckMethod_typedGetWithUnconstrainingObjectClass_stillRejected()}),
     * so the <em>only</em> possible route to a genuine dependency here is
     * Fix 2's "consumed by an internal call" credit correctly firing for a
     * constructor call.
     *
     * <p>Before Fix 2, {@code visitMethodInsn}'s {@code isInitCall} branch
     * matched on {@code "<init>".equals(name)} alone, with no owner/receiver
     * check — <em>any</em> constructor call anywhere in the method body
     * (not just the enclosing method's own terminal {@code super(...)}/
     * {@code this(...)} call) was blanket-denied credit, so this class was
     * wrongly graded {@link AtomicSerialVerdict#VALIDATION_ORDER} even
     * though {@code check} genuinely processes what it read. Must now be
     * graded {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testCompliant_dedicatedCheckMethod_internalConstructorCallConsumesGetResult() {
        byte[] classBytes = buildCheckMethodInternalConstructorCallConsumesGetResultClass(
                "TestInternalCtorCallCredits");
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * The critical, general bypass of the depth-counter version of Fix 2,
     * closed: an orphaned {@code NEW java/lang/Object; POP} pair (two fully
     * disconnected instructions — nothing ever calls {@code <init>} on the
     * discarded reference) placed earlier in a {@code (GetArg)} constructor,
     * followed by a bare, unprocessed {@code this(arg, arg.get("ttl",
     * null))} pass-through:
     * <pre>
     *   public Foo(GetArg arg) throws ... {
     *     new Object();           // NEW ... POP, no DUP, no &lt;init&gt; call at all
     *     this(arg, arg.get("ttl", null));
     *   }
     *   private Foo(GetArg arg, Object ignored) { super(); }
     * </pre>
     * A raw {@code NEW}/{@code <init>}-match depth counter is incremented by
     * the {@code NEW} and never decremented (nothing ever matches it), so it
     * wrongly inflates the count seen by the constructor's own genuinely
     * terminal {@code this(...)} call below, making that call look
     * "matched" (i.e. not the terminal call) and crediting the bare
     * pass-through as evidence. Reviewer-confirmed this exact shape loads
     * and runs on a real, unmodified JVM with zero {@code VerifyError} —
     * {@code NEW}-then-immediately-{@code POP} triggers none of the
     * verifier's uninitialized-reference escape hazards (JVMS §4.10.1.4) —
     * so this is a fully practical bypass, not a theoretical one. The fix
     * tracks the terminal call's receiver directly (does it trace back to
     * {@code ALOAD 0}, or not?) rather than counting {@code NEW}s — an
     * orphaned, never-matched {@code NEW} simply resolves to nothing (see
     * {@link AtomicSerialComplianceVisitor.CheckMethodAnalyzer#thisOriginStack}),
     * so it can no longer influence the classification of the unrelated,
     * later terminal call. Must be graded
     * {@link AtomicSerialVerdict#VALIDATION_ORDER}.
     */
    @Test
    public void testValidationOrder_inlineExtraction_orphanedNewPopBeforeBareTerminalPassThrough() {
        byte[] classBytes = buildOrphanedNewPopBeforeBareTerminalPassThroughClass(
                "TestOrphanedNewPopBypass");
        assertEquals(AtomicSerialVerdict.VALIDATION_ORDER,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * Confirms the (already-passing) nested-construction case still works
     * correctly under the receiver-tracing fix: a dedicated check method
     * whose body is {@code new Outer(new Inner(arg.get("x", null)))} — both
     * {@code Outer} and {@code Inner} internal (same-package) classes,
     * properly balanced ({@code NEW}/{@code DUP} pairs each matched by their
     * own {@code <init>} call), consuming an <em>untyped</em> {@code get()}
     * result:
     * <pre>
     *   private static boolean check(GetArg arg) throws ... {
     *     new Outer(new Inner(arg.get("x", null)));
     *     return true;
     *   }
     * </pre>
     * Ordering passes (the untyped read is genuinely consumed by the
     * internal {@code Inner.&lt;init&gt;} call, credited immediately), so
     * the verdict is decided by the separate untyped-get scrutiny, which
     * correctly flags the untyped value reaching {@code Inner.&lt;init&gt;}
     * with no intervening {@code CHECKCAST}/{@code instanceof}. Must be
     * graded {@link AtomicSerialVerdict#UNTYPED_GET}.
     */
    @Test
    public void testUntypedGet_nestedInternalConstructorCallsStillCredited() {
        byte[] classBytes = buildNestedInternalConstructorCallsClass(
                "TestNestedOuterInner");
        assertEquals(AtomicSerialVerdict.UNTYPED_GET,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * Confirms ordering/position of a decoy orphaned {@code NEW; POP} pair
     * does not matter, only correct pairing: the same shape as {@link
     * #testCompliant_dedicatedCheckMethod_internalConstructorCallConsumesGetResult()}
     * (a genuine internal {@code new TestFoo(arg.get("f", null,
     * Object.class))} call, correctly credited), but with an orphaned
     * {@code NEW java/lang/Object; POP} pair placed <em>after</em> the
     * genuine call instead of before it:
     * <pre>
     *   private static boolean check(GetArg arg) throws ... {
     *     new TestFoo(arg.get("f", null, Object.class));
     *     new Object();   // orphaned decoy AFTER the genuine call this time
     *     return true;
     *   }
     * </pre>
     * The genuine call's credit must not depend on where the harmless,
     * fully-resolved decoy sits relative to it. Must be graded
     * {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testCompliant_orphanedNewPopAfterGenuineInternalCall_positionDoesNotMatter() {
        byte[] classBytes = buildOrphanedNewPopAfterGenuineCallClass(
                "TestOrphanedNewPopAfterGenuine");
        assertEquals(AtomicSerialVerdict.COMPLIANT,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    // -----------------------------------------------------------------------
    // Gap 2B tests — serialize(PutArg, T) presence
    // -----------------------------------------------------------------------

    /**
     * A class with a correct {@code (GetArg)} constructor, a genuine check
     * method, {@code serialForm()}, and a non-static field (so it is not
     * exempt) — but no {@code serialize(PutArg, T)} method at all. Must NOT
     * be graded {@link AtomicSerialVerdict#COMPLIANT}.
     */
    @Test
    public void testMissingSerialize() {
        byte[] classBytes = buildMissingSerializeClass("TestMissingSerialize");
        assertEquals(AtomicSerialVerdict.MISSING_SERIALIZE,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    // -----------------------------------------------------------------------
    // Gap 2C tests — broadened UNTYPED_GET dataflow (store/reload, and
    // consumption with no check at all)
    // -----------------------------------------------------------------------

    /**
     * The check method's untyped 2-argument {@code get()} result is stored
     * to a local, reloaded, stored to a <em>second</em> local, reloaded
     * again, and only then null-checked — two store/reload hops away from
     * the original call, not the immediately-adjacent
     * {@code get()}-then-{@code IFNULL} shape the original (narrow)
     * detector recognised. Must be flagged
     * {@link AtomicSerialVerdict#UNTYPED_GET}.
     */
    @Test
    public void testUntypedGet_storedReloadedAcrossTwoLocalsThenNullChecked() {
        byte[] classBytes = buildUntypedGetStoreReloadClass("TestUntypedGetStoreReload");
        assertEquals(AtomicSerialVerdict.UNTYPED_GET,
                     AtomicSerialComplianceVisitor.analyze(classBytes));
    }

    /**
     * The check method's untyped 2-argument {@code get()} result is passed
     * straight into another (void) method call with <em>no</em> null-check
     * and <em>no</em> cast of any kind first — not even the narrow
     * {@code IFNULL}/{@code IFNONNULL} shape the original detector looked
     * for: {@code consume(arg.get("f", null)); return true;}, where
     * {@code consume} is a private static (same-class, hence "internal" —
     * see {@link AtomicSerialComplianceVisitor.CheckMethodAnalyzer#isExternalCallOwner})
     * method of the class under analysis.
     *
     * <p>Because {@code consume} is internal, the Gap-2A-hardened
     * validation-ordering logic genuinely (not coincidentally) credits this
     * check method as satisfying the ordering rule — an internal call
     * consuming a value derived from a real read is treated as sufficient,
     * self-contained evidence regardless of the call being {@code void} (see
     * {@link AtomicSerialComplianceVisitor.CheckMethodAnalyzer#visitMethodInsn}).
     * Ordering therefore passes, and the verdict is decided entirely by the
     * <em>separate</em> untyped-get detector, which independently and
     * correctly flags that the untyped value reached {@code consume}
     * without ever passing through a {@code CHECKCAST}/{@code instanceof}.
     * Must be flagged {@link AtomicSerialVerdict#UNTYPED_GET}.
     */
    @Test
    public void testUntypedGet_consumedByAnotherCallWithNoCheckAtAll() {
        byte[] classBytes = buildUntypedGetImmediateUseNoCheckClass("TestUntypedGetNoCheck");
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
     *       (the typed 3-arg form) and branches on the result — a real,
     *       if trivial, dependency on what it read (a check method that
     *       called {@code get()} and then discarded the result
     *       unconditionally would not be genuine — see
     *       {@link #testValidationOrder_checkCallsGetButIgnoresResult()});</li>
     *   <li>a proper {@code (GetArg)} public constructor that calls
     *       {@code this(arg, check(arg))};</li>
     *   <li>a bridge {@code (GetArg,boolean)} private constructor;</li>
     *   <li>a public static {@code serialForm()} method.</li>
     * </ul>
     */
    private static byte[] buildCompliantClass_typedGet(String simpleName) {
        ClassWriter cw = newAtomicSerialClass(simpleName);

        // static boolean check(GetArg arg) {
        //   return arg.get("f", null, Object.class) != null;  // typed form, branch on result
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
        Label notNull = new Label();
        mv.visitJumpInsn(Opcodes.IFNONNULL, notNull);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitLabel(notNull);
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

        // static boolean check(GetArg arg) throws IOException, ClassNotFoundException {
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

        // check method that uses typed get (so not UNTYPED_GET) and branches
        // on the result, so it is genuinely dependent on its input and this
        // fixture isolates the serialForm()-presence concern rather than
        // tripping VALIDATION_ORDER via a vacuous check body.
        MethodVisitor cv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "check",
                "(L" + GET_ARG + ";)Z",
                null, IOEXC_CNFE);
        cv.visitCode();
        cv.visitVarInsn(Opcodes.ALOAD, 0);          // arg
        cv.visitLdcInsn("f");
        cv.visitInsn(Opcodes.ACONST_NULL);
        cv.visitLdcInsn(org.objectweb.asm.Type.getType(Object.class));
        cv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GET_ARG, "get", TYPED_GET, false);
        Label notNull = new Label();
        cv.visitJumpInsn(Opcodes.IFNONNULL, notNull);
        cv.visitInsn(Opcodes.ICONST_0);
        cv.visitInsn(Opcodes.IRETURN);
        cv.visitLabel(notNull);
        cv.visitInsn(Opcodes.ICONST_1);
        cv.visitInsn(Opcodes.IRETURN);
        cv.visitMaxs(4, 1);
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

    /**
     * Generates a class whose {@code (GetArg)} constructor body is exactly
     * {@code Objects.requireNonNull(arg); super();} — an {@code
     * INVOKESTATIC} precedes {@code super()}, but it does not read arg's
     * contents (its erased descriptor does not even mention {@code GetArg}).
     */
    private static byte[] buildInnocuousStaticThenSuperClass(String simpleName) {
        ClassWriter cw = newAtomicSerialClass(simpleName);

        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", GET_ARG_CTOR_DESC, null, IOEXC_CNFE);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 1);          // arg
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Objects", "requireNonNull",
                "(Ljava/lang/Object;)Ljava/lang/Object;", false);
        mv.visitInsn(Opcodes.POP);
        mv.visitVarInsn(Opcodes.ALOAD, 0);          // this
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();

        addSerialFormMethod(cw);
        addSerializeMethod(cw, simpleName);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class whose {@code (GetArg)} constructor calls a genuine,
     * typed-get-and-branch {@code check(GetArg)Z} method, then <em>also</em>
     * calls the unrelated static {@code Objects.requireNonNull} — in that
     * order — before the terminal bridge-constructor call, so that {@code
     * check} is not the last {@code INVOKESTATIC} seen.
     */
    private static byte[] buildInterveningUnrelatedStaticsClass(String simpleName) {
        ClassWriter cw = newAtomicSerialClass(simpleName);
        String className = "au/net/zeus/jgdms/bae/test/" + simpleName;

        // private static boolean check(GetArg arg) {
        //   return arg.get("f", null, Object.class) != null;
        // }
        MethodVisitor cv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "check", "(L" + GET_ARG + ";)Z", null, IOEXC_CNFE);
        cv.visitCode();
        cv.visitVarInsn(Opcodes.ALOAD, 0);
        cv.visitLdcInsn("f");
        cv.visitInsn(Opcodes.ACONST_NULL);
        cv.visitLdcInsn(org.objectweb.asm.Type.getType(Object.class));
        cv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GET_ARG, "get", TYPED_GET, false);
        Label notNull = new Label();
        cv.visitJumpInsn(Opcodes.IFNONNULL, notNull);
        cv.visitInsn(Opcodes.ICONST_0);
        cv.visitInsn(Opcodes.IRETURN);
        cv.visitLabel(notNull);
        cv.visitInsn(Opcodes.ICONST_1);
        cv.visitInsn(Opcodes.IRETURN);
        cv.visitMaxs(4, 1);
        cv.visitEnd();

        // public Foo(GetArg arg) throws ... {
        //   this(arg, check(arg));      // check() called first ...
        //   Objects.requireNonNull(arg); //  ... an unrelated static call is
        //                                  // *also* made, after check(), but
        //                                  // before the bridge ctor call.
        // }
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", GET_ARG_CTOR_DESC, null, IOEXC_CNFE);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);           // this
        mv.visitVarInsn(Opcodes.ALOAD, 1);           // arg (bridge ctor arg)
        mv.visitVarInsn(Opcodes.ALOAD, 1);           // arg (for check)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, className, "check", "(L" + GET_ARG + ";)Z", false);
        // stack: this, arg, checkResult
        mv.visitVarInsn(Opcodes.ALOAD, 1);           // arg (for requireNonNull)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Objects", "requireNonNull",
                "(Ljava/lang/Object;)Ljava/lang/Object;", false);
        mv.visitInsn(Opcodes.POP);                   // discard, unrelated intervening call
        // stack again: this, arg, checkResult
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, className, "<init>",
                "(L" + GET_ARG + ";Z)V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(4, 2);
        mv.visitEnd();

        addBridgeCtor(cw, simpleName);
        addSerialFormMethod(cw);
        addSerializeMethod(cw, simpleName);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class whose {@code check(GetArg)Z} method is called
     * correctly before {@code super()}/{@code this()} but never reads its
     * parameter at all — {@code return true;} unconditionally.
     */
    private static byte[] buildVacuousCheckNeverReadsParamClass(String simpleName) {
        ClassWriter cw = newAtomicSerialClass(simpleName);

        MethodVisitor cv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "check", "(L" + GET_ARG + ";)Z", null, IOEXC_CNFE);
        cv.visitCode();
        cv.visitInsn(Opcodes.ICONST_1);
        cv.visitInsn(Opcodes.IRETURN);
        cv.visitMaxs(1, 1);
        cv.visitEnd();

        addGetArgCtor(cw, simpleName);
        addSerialFormMethod(cw);
        addSerializeMethod(cw, simpleName);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class whose {@code check(GetArg)Z} method calls the
     * untyped 2-argument {@code get()} but immediately discards the result
     * (a bare statement-expression, compiled to {@code POP}) and
     * unconditionally returns {@code true}.
     */
    private static byte[] buildVacuousCheckIgnoresGetResultClass(String simpleName) {
        ClassWriter cw = newAtomicSerialClass(simpleName);

        MethodVisitor cv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "check", "(L" + GET_ARG + ";)Z", null, IOEXC_CNFE);
        cv.visitCode();
        cv.visitVarInsn(Opcodes.ALOAD, 0);          // arg
        cv.visitLdcInsn("f");
        cv.visitInsn(Opcodes.ACONST_NULL);
        cv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GET_ARG, "get", UNTYPED_GET, false);
        cv.visitInsn(Opcodes.POP);                  // discarded, unconditional return follows
        cv.visitInsn(Opcodes.ICONST_1);
        cv.visitInsn(Opcodes.IRETURN);
        cv.visitMaxs(3, 1);
        cv.visitEnd();

        addGetArgCtor(cw, simpleName);
        addSerialFormMethod(cw);
        addSerializeMethod(cw, simpleName);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class whose {@code check(GetArg)Z} method reads its input
     * via {@code get(...)} (with the given descriptor — typed or untyped)
     * and immediately launders the result through {@code String.valueOf}
     * (an external, JDK-owned call) whose own return value is discarded,
     * then unconditionally returns {@code true}:
     * <pre>
     *   private static boolean check(GetArg arg) {
     *     String.valueOf(arg.get("f", null[, String.class]));
     *     return true;
     *   }
     * </pre>
     * This is proven bypass #1/#2 from the independent review: the read is
     * genuine and the call happens, but nothing about the method's outcome
     * depends on either.
     */
    private static byte[] buildCheckMethodGetLaunderedThroughExternalCallClass(
            String simpleName, String getDescriptor, Class<?> declaredType) {
        return buildCheckMethodGetLaunderedThroughExternalCallClass(
                simpleName, getDescriptor, declaredType, "f", true);
    }

    /**
     * Same shape as the 3-arg overload, but with the field-name literal and
     * whether {@code serialForm()} declares it under caller control — used
     * by the Fix 1 field-name-absent bypass-probe test, where the field
     * name deliberately does <em>not</em> appear in {@code serialForm()} at
     * all.
     */
    private static byte[] buildCheckMethodGetLaunderedThroughExternalCallClass(
            String simpleName, String getDescriptor, Class<?> declaredType,
            String fieldName, boolean declareFieldInSerialForm) {
        ClassWriter cw = newAtomicSerialClass(simpleName);

        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "check", "(L" + GET_ARG + ";)Z", null, IOEXC_CNFE);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);          // arg
        mv.visitLdcInsn(fieldName);
        mv.visitInsn(Opcodes.ACONST_NULL);
        if (TYPED_GET.equals(getDescriptor)) {
            mv.visitLdcInsn(org.objectweb.asm.Type.getType(declaredType));
        }
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GET_ARG, "get", getDescriptor, false);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf",
                "(Ljava/lang/Object;)Ljava/lang/String;", false);
        mv.visitInsn(Opcodes.POP);                  // String.valueOf's result discarded too
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(4, 1);
        mv.visitEnd();

        addGetArgCtor(cw, simpleName);
        if (declareFieldInSerialForm) {
            addSerialFormMethod(cw);          // declares "f" only
        } else {
            addEmptySerialFormMethod(cw);     // declares nothing at all
        }
        addSerializeMethod(cw, simpleName);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class with <em>no</em> dedicated check method at all: the
     * {@code (GetArg)} constructor itself reads {@code arg.get("f", null,
     * String.class)}, launders the result through the external
     * {@code String.valueOf}, and passes that (unused) value straight to a
     * bridge constructor that ignores its second parameter entirely:
     * <pre>
     *   public Foo(GetArg arg) throws ... {
     *     this(arg, String.valueOf(arg.get("f", null, String.class)));
     *   }
     *   private Foo(GetArg arg, String ignored) { }   // no-op bridge
     * </pre>
     * This is proven bypass #3 from the independent review — the most
     * severe of the three, since the inline-extraction path has no
     * untyped-get safety net available at all.
     */
    private static byte[] buildInlineExtractionLaunderedThroughExternalCallClass(String simpleName) {
        ClassWriter cw = newAtomicSerialClass(simpleName);
        String className = "au/net/zeus/jgdms/bae/test/" + simpleName;

        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", GET_ARG_CTOR_DESC, null, IOEXC_CNFE);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);           // this
        mv.visitVarInsn(Opcodes.ALOAD, 1);           // arg (bridge ctor's 1st param)
        mv.visitVarInsn(Opcodes.ALOAD, 1);           // arg (for get)
        mv.visitLdcInsn("f");
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitLdcInsn(org.objectweb.asm.Type.getType(String.class));
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GET_ARG, "get", TYPED_GET, false);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf",
                "(Ljava/lang/Object;)Ljava/lang/String;", false);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, className, "<init>",
                "(L" + GET_ARG + ";Ljava/lang/String;)V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(6, 2);
        mv.visitEnd();

        // private Foo(GetArg arg, String ignored) { super(); }  -- no-op:
        // never loads slot 2 (the String parameter) at all.
        MethodVisitor bv = cw.visitMethod(
                Opcodes.ACC_PRIVATE, "<init>",
                "(L" + GET_ARG + ";Ljava/lang/String;)V", null, IOEXC_CNFE);
        bv.visitCode();
        bv.visitVarInsn(Opcodes.ALOAD, 0);
        bv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        bv.visitInsn(Opcodes.RETURN);
        bv.visitMaxs(1, 3);
        bv.visitEnd();

        addSerialFormMethod(cw);
        addSerializeMethod(cw, simpleName);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class with no dedicated check method: the {@code (GetArg)}
     * constructor reads {@code arg.get("f", null)} (untyped), passes the
     * result through the <em>external</em> {@code Objects.toString(...)}
     * first, and only then to the <em>internal</em>, same-class
     * {@code sameClassHelper(...)} before the terminal call:
     * <pre>
     *   public Foo(GetArg arg) throws ... {
     *     this(sameClassHelper(java.util.Objects.toString(arg.get("f", null))));
     *   }
     *   private static String sameClassHelper(String s) { return s; }
     *   private Foo(String s) { }
     * </pre>
     * Mirrors the real {@code ConsistentSet} false-positive shape found by
     * the full-corpus rescan.
     */
    private static byte[] buildInlineExtractionExternalThenInternalCallClass(String simpleName) {
        ClassWriter cw = newAtomicSerialClass(simpleName);
        String className = "au/net/zeus/jgdms/bae/test/" + simpleName;

        // private static String sameClassHelper(String s) { return s; }
        MethodVisitor hv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "sameClassHelper", "(Ljava/lang/String;)Ljava/lang/String;", null, null);
        hv.visitCode();
        hv.visitVarInsn(Opcodes.ALOAD, 0);
        hv.visitInsn(Opcodes.ARETURN);
        hv.visitMaxs(1, 1);
        hv.visitEnd();

        // public Foo(GetArg arg) throws ... {
        //   this(sameClassHelper(Objects.toString(arg.get("f", null))));
        // }
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", GET_ARG_CTOR_DESC, null, IOEXC_CNFE);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);           // this
        mv.visitVarInsn(Opcodes.ALOAD, 1);           // arg (for get)
        mv.visitLdcInsn("f");
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GET_ARG, "get", UNTYPED_GET, false);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Objects", "toString",
                "(Ljava/lang/Object;)Ljava/lang/String;", false);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, className, "sameClassHelper",
                "(Ljava/lang/String;)Ljava/lang/String;", false);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, className, "<init>",
                "(Ljava/lang/String;)V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(3, 2);
        mv.visitEnd();

        // private Foo(String s) { super(); }
        MethodVisitor bv = cw.visitMethod(
                Opcodes.ACC_PRIVATE, "<init>", "(Ljava/lang/String;)V", null, null);
        bv.visitCode();
        bv.visitVarInsn(Opcodes.ALOAD, 0);
        bv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        bv.visitInsn(Opcodes.RETURN);
        bv.visitMaxs(1, 2);
        bv.visitEnd();

        addSerialFormMethod(cw);
        addSerializeMethod(cw, simpleName);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class with a dedicated check method whose body constructs
     * a genuine, same-class helper object with a real {@code get()} result:
     * <pre>
     *   private static boolean check(GetArg arg) throws ... {
     *     new TestFoo(arg.get("f", null, Object.class));   // NEW ... INVOKESPECIAL &lt;init&gt;
     *     return true;
     *   }
     *   private TestFoo(Object x) { super(); }              // distinct ctor from the (GetArg) one
     * </pre>
     * The declared type is deliberately {@code Object.class} (always
     * excluded from Fix 1's self-validating credit — see
     * {@link CheckMethodAnalyzer}) so the only possible route to a genuine
     * dependency is Fix 2's "consumed by an internal call" credit, applied
     * here to a constructor call rather than a plain method call. See
     * {@link #testCompliant_dedicatedCheckMethod_internalConstructorCallConsumesGetResult()}.
     */
    private static byte[] buildCheckMethodInternalConstructorCallConsumesGetResultClass(String simpleName) {
        ClassWriter cw = newAtomicSerialClass(simpleName);
        String className = "au/net/zeus/jgdms/bae/test/" + simpleName;

        // private static boolean check(GetArg arg) throws ... {
        //   new TestFoo(arg.get("f", null, Object.class));
        //   return true;
        // }
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "check", "(L" + GET_ARG + ";)Z", null, IOEXC_CNFE);
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, className);
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 0);           // arg
        mv.visitLdcInsn("f");
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitLdcInsn(org.objectweb.asm.Type.getType(Object.class));
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GET_ARG, "get", TYPED_GET, false);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, className, "<init>",
                "(Ljava/lang/Object;)V", false);
        mv.visitInsn(Opcodes.POP);                   // discard the constructed-but-unused object
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(6, 1);
        mv.visitEnd();

        // private TestFoo(Object x) { super(); }  -- the internal
        // constructor call `check()` consumes a real get() result with.
        MethodVisitor wv = cw.visitMethod(
                Opcodes.ACC_PRIVATE, "<init>", "(Ljava/lang/Object;)V", null, null);
        wv.visitCode();
        wv.visitVarInsn(Opcodes.ALOAD, 0);
        wv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        wv.visitInsn(Opcodes.RETURN);
        wv.visitMaxs(1, 2);
        wv.visitEnd();

        addGetArgCtor(cw, simpleName);
        addSerialFormMethod(cw);
        addSerializeMethod(cw, simpleName);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class whose {@code (GetArg)} constructor places an
     * orphaned {@code NEW java/lang/Object; POP} pair (no {@code DUP}, no
     * matching {@code <init>} call at all — a fully disconnected, discarded
     * allocation) <em>before</em> a bare, unprocessed
     * {@code this(arg, arg.get("ttl", null))} pass-through:
     * <pre>
     *   public Foo(GetArg arg) throws ... {
     *     new Object();                              // NEW ... POP, orphaned
     *     this(arg, arg.get("ttl", null));            // bare pass-through, the real terminal call
     *   }
     *   private Foo(GetArg arg, Object ignored) { super(); }
     * </pre>
     * See {@link #testValidationOrder_inlineExtraction_orphanedNewPopBeforeBareTerminalPassThrough()}.
     */
    private static byte[] buildOrphanedNewPopBeforeBareTerminalPassThroughClass(String simpleName) {
        ClassWriter cw = newAtomicSerialClass(simpleName);
        String className = "au/net/zeus/jgdms/bae/test/" + simpleName;

        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", GET_ARG_CTOR_DESC, null, IOEXC_CNFE);
        mv.visitCode();
        // Orphaned decoy: NEW ... POP, no DUP, no matching <init> call at all.
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/Object");
        mv.visitInsn(Opcodes.POP);
        // this(arg, arg.get("ttl", null)) -- bare, unprocessed pass-through.
        mv.visitVarInsn(Opcodes.ALOAD, 0);           // this
        mv.visitVarInsn(Opcodes.ALOAD, 1);           // arg (bridge ctor's 1st param)
        mv.visitVarInsn(Opcodes.ALOAD, 1);           // arg (for get)
        mv.visitLdcInsn("ttl");
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GET_ARG, "get", UNTYPED_GET, false);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, className, "<init>",
                "(L" + GET_ARG + ";Ljava/lang/Object;)V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(5, 2);
        mv.visitEnd();

        // private Foo(GetArg arg, Object ignored) { super(); }  -- no-op bridge.
        MethodVisitor bv = cw.visitMethod(
                Opcodes.ACC_PRIVATE, "<init>",
                "(L" + GET_ARG + ";Ljava/lang/Object;)V", null, IOEXC_CNFE);
        bv.visitCode();
        bv.visitVarInsn(Opcodes.ALOAD, 0);
        bv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        bv.visitInsn(Opcodes.RETURN);
        bv.visitMaxs(1, 3);
        bv.visitEnd();

        addSerialFormMethod(cw);
        addSerializeMethod(cw, simpleName);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class with a dedicated check method whose body is a
     * properly-balanced nested construction, {@code new Outer(new
     * Inner(arg.get("x", null)))}, both {@code Outer} and {@code Inner}
     * internal (same-package) classes:
     * <pre>
     *   private static boolean check(GetArg arg) throws ... {
     *     new Outer(new Inner(arg.get("x", null)));
     *     return true;
     *   }
     * </pre>
     * See {@link #testUntypedGet_nestedInternalConstructorCallsStillCredited()}.
     */
    private static byte[] buildNestedInternalConstructorCallsClass(String simpleName) {
        ClassWriter cw = newAtomicSerialClass(simpleName);
        String outerName = "au/net/zeus/jgdms/bae/test/" + simpleName + "Outer";
        String innerName = "au/net/zeus/jgdms/bae/test/" + simpleName + "Inner";

        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "check", "(L" + GET_ARG + ";)Z", null, IOEXC_CNFE);
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, outerName);
        mv.visitInsn(Opcodes.DUP);
        mv.visitTypeInsn(Opcodes.NEW, innerName);
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 0);           // arg
        mv.visitLdcInsn("x");
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GET_ARG, "get", UNTYPED_GET, false);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, innerName, "<init>",
                "(Ljava/lang/Object;)V", false);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, outerName, "<init>",
                "(L" + innerName + ";)V", false);
        mv.visitInsn(Opcodes.POP);                   // discard the constructed-but-unused Outer
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(7, 1);
        mv.visitEnd();

        addGetArgCtor(cw, simpleName);
        addSerialFormMethod(cw);
        addSerializeMethod(cw, simpleName);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Same shape as {@link #buildCheckMethodInternalConstructorCallConsumesGetResultClass}
     * (a genuine internal {@code new TestFoo(arg.get("f", null,
     * Object.class))} call), but with an orphaned {@code NEW
     * java/lang/Object; POP} pair placed <em>after</em> the genuine call
     * instead of before it:
     * <pre>
     *   private static boolean check(GetArg arg) throws ... {
     *     new TestFoo(arg.get("f", null, Object.class));
     *     new Object();   // orphaned decoy AFTER the genuine call
     *     return true;
     *   }
     *   private TestFoo(Object x) { super(); }
     * </pre>
     * See {@link #testCompliant_orphanedNewPopAfterGenuineInternalCall_positionDoesNotMatter()}.
     */
    private static byte[] buildOrphanedNewPopAfterGenuineCallClass(String simpleName) {
        ClassWriter cw = newAtomicSerialClass(simpleName);
        String className = "au/net/zeus/jgdms/bae/test/" + simpleName;

        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "check", "(L" + GET_ARG + ";)Z", null, IOEXC_CNFE);
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, className);
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 0);           // arg
        mv.visitLdcInsn("f");
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitLdcInsn(org.objectweb.asm.Type.getType(Object.class));
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GET_ARG, "get", TYPED_GET, false);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, className, "<init>",
                "(Ljava/lang/Object;)V", false);
        mv.visitInsn(Opcodes.POP);                   // discard the constructed-but-unused object
        // Orphaned decoy, AFTER the genuine call this time.
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/Object");
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(6, 1);
        mv.visitEnd();

        MethodVisitor wv = cw.visitMethod(
                Opcodes.ACC_PRIVATE, "<init>", "(Ljava/lang/Object;)V", null, null);
        wv.visitCode();
        wv.visitVarInsn(Opcodes.ALOAD, 0);
        wv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        wv.visitInsn(Opcodes.RETURN);
        wv.visitMaxs(1, 2);
        wv.visitEnd();

        addGetArgCtor(cw, simpleName);
        addSerialFormMethod(cw);
        addSerializeMethod(cw, simpleName);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class identical in shape to a compliant class (genuine
     * check method, {@code serialForm()}, a non-static field so it is not
     * exemption-eligible) but with no {@code serialize(PutArg, T)} method.
     */
    private static byte[] buildMissingSerializeClass(String simpleName) {
        ClassWriter cw = newAtomicSerialClass(simpleName);

        cw.visitField(Opcodes.ACC_PRIVATE, "value", "Ljava/lang/String;",
                      null, null).visitEnd();

        MethodVisitor cv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "check", "(L" + GET_ARG + ";)Z", null, IOEXC_CNFE);
        cv.visitCode();
        cv.visitVarInsn(Opcodes.ALOAD, 0);
        cv.visitLdcInsn("f");
        cv.visitInsn(Opcodes.ACONST_NULL);
        cv.visitLdcInsn(org.objectweb.asm.Type.getType(Object.class));
        cv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GET_ARG, "get", TYPED_GET, false);
        Label notNull = new Label();
        cv.visitJumpInsn(Opcodes.IFNONNULL, notNull);
        cv.visitInsn(Opcodes.ICONST_0);
        cv.visitInsn(Opcodes.IRETURN);
        cv.visitLabel(notNull);
        cv.visitInsn(Opcodes.ICONST_1);
        cv.visitInsn(Opcodes.IRETURN);
        cv.visitMaxs(4, 1);
        cv.visitEnd();

        addGetArgCtor(cw, simpleName);
        addSerialFormMethod(cw);
        // Deliberately NO addSerializeMethod(cw, simpleName) call.
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class whose {@code check(GetArg)Z} method:
     * <pre>
     *   Object v = arg.get("f", null);   // untyped 2-arg
     *   Object w = v;                    // store/reload hop #1
     *   if (w == null) throw ...;        // reload hop #2, still no CHECKCAST
     *   return true;
     * </pre>
     * — the untyped result crosses two separate local-variable store/reload
     * round trips before being null-checked, never adjacent to the original
     * {@code get()} call.
     */
    private static byte[] buildUntypedGetStoreReloadClass(String simpleName) {
        ClassWriter cw = newAtomicSerialClass(simpleName);

        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "check", "(L" + GET_ARG + ";)Z", null, IOEXC_CNFE);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitLdcInsn("f");
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GET_ARG, "get", UNTYPED_GET, false);
        mv.visitVarInsn(Opcodes.ASTORE, 1);          // v = ... (hop #1 store)
        mv.visitVarInsn(Opcodes.ALOAD, 1);           // v
        mv.visitVarInsn(Opcodes.ASTORE, 2);          // w = v (hop #2 store)
        mv.visitVarInsn(Opcodes.ALOAD, 2);           // w  (still untyped: no CHECKCAST ever occurred)
        Label ok = new Label();
        mv.visitJumpInsn(Opcodes.IFNONNULL, ok);
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalArgumentException");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/IllegalArgumentException", "<init>", "()V", false);
        mv.visitInsn(Opcodes.ATHROW);
        mv.visitLabel(ok);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(3, 3);
        mv.visitEnd();

        addGetArgCtor(cw, simpleName);
        addSerialFormMethod(cw);
        addSerializeMethod(cw, simpleName);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class whose {@code check(GetArg)Z} method passes the
     * untyped 2-argument {@code get()} result straight into another method
     * call with no null-check and no cast of any kind first.
     */
    private static byte[] buildUntypedGetImmediateUseNoCheckClass(String simpleName) {
        ClassWriter cw = newAtomicSerialClass(simpleName);
        String className = "au/net/zeus/jgdms/bae/test/" + simpleName;

        // private static void consume(Object o) { }
        MethodVisitor sink = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "consume", "(Ljava/lang/Object;)V", null, null);
        sink.visitCode();
        sink.visitInsn(Opcodes.RETURN);
        sink.visitMaxs(0, 1);
        sink.visitEnd();

        // private static boolean check(GetArg arg) throws ... {
        //   consume(arg.get("f", null));   // no null-check, no cast at all
        //   return true;
        // }
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "check", "(L" + GET_ARG + ";)Z", null, IOEXC_CNFE);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitLdcInsn("f");
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GET_ARG, "get", UNTYPED_GET, false);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, className, "consume", "(Ljava/lang/Object;)V", false);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(3, 1);
        mv.visitEnd();

        addGetArgCtor(cw, simpleName);
        addSerialFormMethod(cw);
        addSerializeMethod(cw, simpleName);
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
     * that declares a single field, {@code new SerialForm("f", Object.class)}
     * — {@code "f"} is the field name literal essentially every builder in
     * this file uses for its {@code get(...)} calls, so declaring it here is
     * what keeps the Fix 1 field-name cross-check
     * (see {@code AtomicSerialComplianceVisitor.CheckMethodAnalyzer#isSelfValidatingGetForm})
     * from regressing every existing self-validating-typed-get test in this
     * file: only the <em>name</em> is cross-checked, never the declared
     * type, so a single placeholder-typed entry for {@code "f"} is
     * sufficient regardless of what type each individual test's own
     * {@code get(...)} call declares.
     */
    private static void addSerialFormMethod(ClassWriter cw) {
        MethodVisitor sv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "serialForm",
                "()[L" + SERIAL_FORM + ";",
                null, null);
        sv.visitCode();
        sv.visitInsn(Opcodes.ICONST_1);
        sv.visitTypeInsn(Opcodes.ANEWARRAY, SERIAL_FORM);
        sv.visitInsn(Opcodes.DUP);
        sv.visitInsn(Opcodes.ICONST_0);
        sv.visitTypeInsn(Opcodes.NEW, SERIAL_FORM);
        sv.visitInsn(Opcodes.DUP);
        sv.visitLdcInsn("f");
        sv.visitLdcInsn(org.objectweb.asm.Type.getType(Object.class));
        sv.visitMethodInsn(Opcodes.INVOKESPECIAL, SERIAL_FORM, "<init>",
                "(Ljava/lang/String;Ljava/lang/Class;)V", false);
        sv.visitInsn(Opcodes.AASTORE);
        sv.visitInsn(Opcodes.ARETURN);
        sv.visitMaxs(5, 0);
        sv.visitEnd();
    }

    /**
     * Adds a minimal {@code public static SerialForm[] serialForm()} method
     * that returns an <em>empty</em> array declaring no fields at all — used
     * by tests that specifically need the Fix 1 field-name cross-check to
     * fail (no declared name can ever match).
     */
    private static void addEmptySerialFormMethod(ClassWriter cw) {
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

    /**
     * Adds a minimal {@code public static void serialize(PutArg, T)} method
     * (a no-op body — its presence/descriptor is all the visitor checks).
     */
    private static void addSerializeMethod(ClassWriter cw, String simpleName) {
        String className = "au/net/zeus/jgdms/bae/test/" + simpleName;
        MethodVisitor sv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "serialize",
                "(" + PUT_ARG_DESC + "L" + className + ";)V",
                null, new String[]{ "java/io/IOException" });
        sv.visitCode();
        sv.visitInsn(Opcodes.RETURN);
        sv.visitMaxs(0, 2);
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
        addSerializeMethod(cw, simpleName);
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
        addSerializeMethod(cw, simpleName);
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
