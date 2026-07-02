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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import au.net.zeus.jgdms.api.codebase.ConstrainableProxyVerdict;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link ConstrainableProxyComplianceVisitor}.
 *
 * <p>The visitor is two-pass: {@link ConstrainableProxyComplianceVisitor#extract
 * extract} gathers per-class facts and
 * {@link ConstrainableProxyComplianceVisitor#verdict verdict} resolves them
 * against the whole-JAR map.  Each test synthesises one or more classes via ASM
 * {@link ClassWriter}, feeds them all into a facts map, and asserts the verdict
 * for the target class — so cross-class resolution (super chains, in-JAR
 * subclasses, the type a {@code setConstraints} override constructs) is
 * exercised the same way {@link JarAnalyzer} exercises it.
 *
 * @author Peter Firmstone
 */
public class ConstrainableProxyComplianceVisitorTest {

    private static final int V = Opcodes.V11;
    private static final String PKG = "au/net/zeus/jgdms/bae/test/";

    private static final String ATOMIC_SERIAL_DESC = "Lorg/apache/river/api/io/AtomicSerial;";
    private static final String RMC = "net/jini/core/constraint/RemoteMethodControl";
    private static final String MC  = "net/jini/core/constraint/MethodConstraints";
    private static final String REMOTE = "java/rmi/Remote";
    private static final String SERIALIZABLE = "java/io/Serializable";
    private static final String PROXY_ACCESSOR = "net/jini/export/ProxyAccessor";
    private static final String CONSTRAINABLE_SMART_PROXY =
            "au/net/zeus/jgdms/proxy/AbstractSmartProxy$ConstrainableSmartProxy";
    private static final String SETC_DESC = "(L" + MC + ";)L" + RMC + ";";

    /** A synthetic {@code Remote} sub-interface, used as a proxy's server-ref field type. */
    private static final String FOO_SERVICE = PKG + "FooService";

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    /**
     * A constrainable {@code @AtomicSerial} proxy whose {@code setConstraints}
     * constructs and returns a new instance of itself (which reaches
     * {@code RemoteMethodControl}) — the correct pattern.
     */
    @Test
    public void testCompliant_returnsNewProxyInstance() {
        String p = PKG + "GoodProxy";
        byte[] proxy = proxyClass(p, "java/lang/Object",
                new String[]{ FOO_SERVICE, RMC }, true, false, FOO_SERVICE,
                Setc.NEW_SELF, p);
        assertEquals(ConstrainableProxyVerdict.COMPLIANT,
                verdictOf(p, fooService(), proxy));
    }

    /**
     * The critical silent-downgrade case the previous visitor accepted: a
     * constrainable proxy whose {@code setConstraints} constructs an unrelated
     * throwaway ({@code new StringBuilder()}) and then {@code return this}.  A
     * naive "does it {@code NEW} anything?" check passes it; the type-aware
     * check must reject it.
     */
    @Test
    public void testDowngrade_newDecoyThenReturnThis() {
        String p = PKG + "DecoyProxy";
        byte[] proxy = proxyClass(p, "java/lang/Object",
                new String[]{ FOO_SERVICE, RMC }, true, false, FOO_SERVICE,
                Setc.RETURN_THIS, null);
        assertEquals(ConstrainableProxyVerdict.CONSTRAINTS_NOT_APPLIED,
                verdictOf(p, fooService(), proxy));
    }

    /** {@code setConstraints} returns {@code null} — a silent downgrade. */
    @Test
    public void testDowngrade_returnsNull() {
        String p = PKG + "NullProxy";
        byte[] proxy = proxyClass(p, "java/lang/Object",
                new String[]{ FOO_SERVICE, RMC }, true, false, FOO_SERVICE,
                Setc.RETURN_NULL, null);
        assertEquals(ConstrainableProxyVerdict.CONSTRAINTS_NOT_APPLIED,
                verdictOf(p, fooService(), proxy));
    }

    /**
     * Implements {@code RemoteMethodControl} but neither it nor any superclass
     * declares a concrete {@code setConstraints}.
     */
    @Test
    public void testSetConstraintsMissing() {
        String p = PKG + "NoSetterProxy";
        byte[] proxy = proxyClass(p, "java/lang/Object",
                new String[]{ FOO_SERVICE, RMC }, true, false, FOO_SERVICE,
                Setc.NONE, null);
        assertEquals(ConstrainableProxyVerdict.SETCONSTRAINTS_MISSING,
                verdictOf(p, fooService(), proxy));
    }

    /**
     * An {@code @AtomicSerial} smart proxy that holds a remote reference but does
     * not reach {@code RemoteMethodControl} and has no constrainable subclass —
     * a client cannot impose constraints, so it is untrusted.  This is the plain
     * proxy / alien-protocol case.
     */
    @Test
    public void testNonConstrainable_plainSmartProxy() {
        String p = PKG + "PlainProxy";
        byte[] proxy = proxyClass(p, "java/lang/Object",
                new String[]{ FOO_SERVICE }, true, false, FOO_SERVICE,
                Setc.NONE, null);
        assertEquals(ConstrainableProxyVerdict.NON_CONSTRAINABLE,
                verdictOf(p, fooService(), proxy));
    }

    /**
     * The downgrade case: a <em>concrete</em> non-constrainable base whose
     * constrainable form is an in-JAR subclass.  Because a factory may hand back
     * either concrete type at runtime (invisible to static analysis), the
     * concrete base is a live downgrade path and must be
     * {@code NON_CONSTRAINABLE} — the constrainable subclass does <em>not</em>
     * clear it.  The subclass itself is {@code COMPLIANT}.  Also exercises
     * Remote-ref detection via an inherited field.
     */
    @Test
    public void testConcreteBaseWithConstrainableSubclass_baseIsNonConstrainable() {
        String base = PKG + "BaseProxy";
        String sub  = PKG + "ConstrainableSubProxy";
        byte[] baseBytes = proxyClass(base, "java/lang/Object",
                new String[]{ FOO_SERVICE }, true, false, FOO_SERVICE,
                Setc.NONE, null);
        byte[] subBytes = proxyClass(sub, base,
                new String[]{ RMC }, true, false, null /* inherits server */,
                Setc.NEW_SELF, sub);
        assertEquals("concrete non-constrainable base is a live downgrade path",
                ConstrainableProxyVerdict.NON_CONSTRAINABLE,
                verdictOf(base, fooService(), baseBytes, subBytes));
        assertEquals("constrainable subclass honours the contract",
                ConstrainableProxyVerdict.COMPLIANT,
                verdictOf(sub, fooService(), baseBytes, subBytes));
    }

    /**
     * An <em>abstract</em> non-constrainable base is never the runtime type of a
     * deserialized wire instance, so it is {@code NA} — only its concrete
     * (constrainable) subclass, which is {@code COMPLIANT}, can be on the wire.
     */
    @Test
    public void testAbstractBaseWithConstrainableSubclass_baseIsNA() {
        String base = PKG + "AbstractBaseProxy";
        String sub  = PKG + "ConstrainableLeafProxy";
        byte[] baseBytes = proxyClass(base, "java/lang/Object",
                new String[]{ FOO_SERVICE }, true, false, FOO_SERVICE,
                Setc.NONE, null, true /* abstract */);
        byte[] subBytes = proxyClass(sub, base,
                new String[]{ RMC }, true, false, null,
                Setc.NEW_SELF, sub);
        assertEquals("abstract base is never a wire instance",
                ConstrainableProxyVerdict.NA,
                verdictOf(base, fooService(), baseBytes, subBytes));
        assertEquals("constrainable leaf honours the contract",
                ConstrainableProxyVerdict.COMPLIANT,
                verdictOf(sub, fooService(), baseBytes, subBytes));
    }

    /**
     * A wire proxy that holds a remote reference and is {@code Serializable} but
     * <em>not</em> {@code @AtomicSerial} — the insecure serialization path.
     */
    @Test
    public void testSerializableOnly() {
        String p = PKG + "SerialProxy";
        byte[] proxy = proxyClass(p, "java/lang/Object",
                new String[]{ FOO_SERVICE }, false, true, FOO_SERVICE,
                Setc.NONE, null);
        assertEquals(ConstrainableProxyVerdict.JAVA_SERIALIZATION,
                verdictOf(p, fooService(), proxy));
    }

    /**
     * The dual-path backdoor: a proxy that IS {@code @AtomicSerial} and
     * constrainable (its {@code setConstraints} builds a new self, so it would
     * otherwise be {@code COMPLIANT}) but ALSO implements {@code Serializable}
     * is still flagged {@code JAVA_SERIALIZATION} — the {@code java.io} path
     * bypasses the {@code (GetArg)} validation, so it is discouraged outright.
     */
    @Test
    public void testAtomicAndSerializable_isSerializableOnly() {
        String p = PKG + "DualPathProxy";
        byte[] proxy = proxyClass(p, "java/lang/Object",
                new String[]{ FOO_SERVICE, RMC }, true, true, FOO_SERVICE,
                Setc.NEW_SELF, p);
        assertEquals(ConstrainableProxyVerdict.JAVA_SERIALIZATION,
                verdictOf(p, fooService(), proxy));
    }

    /**
     * A class that holds a remote reference but is neither {@code @AtomicSerial}
     * nor {@code Serializable} (a local-only wrapper) is not marshalled — the
     * contract does not apply.
     */
    @Test
    public void testNA_notMarshalled() {
        String p = PKG + "LocalWrapper";
        byte[] proxy = proxyClass(p, "java/lang/Object",
                new String[]{ FOO_SERVICE }, false, false, FOO_SERVICE,
                Setc.NONE, null);
        assertEquals(ConstrainableProxyVerdict.NA,
                verdictOf(p, fooService(), proxy));
    }

    /**
     * A connection-less {@code @AtomicSerial} value object: it crosses the wire
     * but holds no remote reference (no Remote-typed field, no
     * {@code ProxyAccessor}, does not extend {@code AbstractSmartProxy}).  It is
     * just deserialized data — not a smart proxy — so {@code NA}.
     */
    @Test
    public void testNA_connectionlessValueObject() {
        String p = PKG + "ValueObject";
        byte[] obj = proxyClass(p, "java/lang/Object",
                new String[]{}, true, false, null /* no remote ref */,
                Setc.NONE, null);
        assertEquals(ConstrainableProxyVerdict.NA, verdictOf(p, obj));
    }

    /** An interface is never a proxy instance — {@code NA}. */
    @Test
    public void testNA_interface() {
        assertEquals(ConstrainableProxyVerdict.NA,
                verdictOf(FOO_SERVICE, fooService()));
    }

    /**
     * A proxy may hold its remote reference through {@code ProxyAccessor} rather
     * than a {@code Remote}-typed field; that still gates it into the contract,
     * so a non-constrainable one is flagged.
     */
    @Test
    public void testNonConstrainable_remoteRefViaProxyAccessor() {
        String p = PKG + "AccessorProxy";
        byte[] proxy = proxyClass(p, "java/lang/Object",
                new String[]{ PROXY_ACCESSOR }, true, false, null /* no Remote field */,
                Setc.NONE, null);
        assertEquals(ConstrainableProxyVerdict.NON_CONSTRAINABLE,
                verdictOf(p, proxy));
    }

    /**
     * A concrete proxy whose superclass is the framework
     * {@code AbstractSmartProxy.ConstrainableSmartProxy} (resolved by name, as it
     * lives in another JAR): it is gated in via the super chain and reaches
     * {@code RemoteMethodControl} through that base, and its {@code setConstraints}
     * returns a new self instance — {@code COMPLIANT}.
     */
    @Test
    public void testCompliant_viaConstrainableSmartProxyFamily() {
        String p = PKG + "FamilyProxy";
        byte[] proxy = proxyClass(p, CONSTRAINABLE_SMART_PROXY,
                new String[]{}, true, false, null /* server inherited from base */,
                Setc.NEW_SELF, p);
        assertEquals(ConstrainableProxyVerdict.COMPLIANT, verdictOf(p, proxy));
    }

    /** Unparseable bytes are reported fail-secure as {@code UNREADABLE}. */
    @Test
    public void testUnreadable_malformedBytes() {
        ConstrainableProxyComplianceVisitor.ProxyClassFacts f =
                ConstrainableProxyComplianceVisitor.extract(new byte[]{ 0, 1, 2, 3, 4, 5 });
        assertEquals(ConstrainableProxyVerdict.UNREADABLE,
                ConstrainableProxyComplianceVisitor.verdict(f,
                        new HashMap<String, ConstrainableProxyComplianceVisitor.ProxyClassFacts>()));
    }

    /** {@code extract(null)} must throw {@link NullPointerException}. */
    @Test(expected = NullPointerException.class)
    public void testExtract_nullInput_throwsNPE() {
        ConstrainableProxyComplianceVisitor.extract(null);
    }

    // -----------------------------------------------------------------------
    // Harness
    // -----------------------------------------------------------------------

    /** Extracts every class into a facts map (keyed by internal name) and resolves the target's verdict. */
    private static ConstrainableProxyVerdict verdictOf(String target, byte[]... classes) {
        Map<String, ConstrainableProxyComplianceVisitor.ProxyClassFacts> all =
                new HashMap<String, ConstrainableProxyComplianceVisitor.ProxyClassFacts>();
        for (byte[] c : classes) {
            ConstrainableProxyComplianceVisitor.ProxyClassFacts f =
                    ConstrainableProxyComplianceVisitor.extract(c);
            all.put(f.internalName, f);
        }
        return ConstrainableProxyComplianceVisitor.verdict(all.get(target), all);
    }

    // -----------------------------------------------------------------------
    // Bytecode generators
    // -----------------------------------------------------------------------

    /** How the generated {@code setConstraints} body behaves (or that it is absent). */
    private enum Setc { NONE, NEW_SELF, RETURN_THIS, RETURN_NULL }

    private static byte[] fooService() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
                FOO_SERVICE, null, "java/lang/Object", new String[]{ REMOTE });
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Builds a proxy class.
     *
     * @param name        internal name
     * @param superName   internal super name
     * @param ifaces      directly-implemented interfaces
     * @param atomic      add {@code @AtomicSerial}
     * @param serializable add {@code java.io.Serializable}
     * @param remoteFieldType internal name of a Remote-ref field type, or {@code null} for none
     * @param setc        the {@code setConstraints} body to emit
     * @param newType     for {@link Setc#NEW_SELF}, the internal name to {@code NEW}
     */
    private static byte[] proxyClass(String name, String superName, String[] ifaces,
                                     boolean atomic, boolean serializable,
                                     String remoteFieldType, Setc setc, String newType) {
        return proxyClass(name, superName, ifaces, atomic, serializable,
                remoteFieldType, setc, newType, false);
    }

    private static byte[] proxyClass(String name, String superName, String[] ifaces,
                                     boolean atomic, boolean serializable,
                                     String remoteFieldType, Setc setc, String newType,
                                     boolean isAbstract) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        List<String> all = new ArrayList<String>(Arrays.asList(ifaces));
        if (serializable) all.add(SERIALIZABLE);
        int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER
                | (isAbstract ? Opcodes.ACC_ABSTRACT : 0);
        cw.visit(V, access, name, null, superName,
                all.toArray(new String[0]));
        if (atomic) {
            AnnotationVisitor av = cw.visitAnnotation(ATOMIC_SERIAL_DESC, true);
            av.visitEnd();
        }
        if (remoteFieldType != null) {
            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "server",
                    "L" + remoteFieldType + ";", null, null).visitEnd();
        }
        // <init>()V -> super.<init>()V; return
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();

        emitSetConstraints(cw, setc, newType);
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void emitSetConstraints(ClassWriter cw, Setc setc, String newType) {
        if (setc == Setc.NONE) return;
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "setConstraints", SETC_DESC, null, null);
        mv.visitCode();
        switch (setc) {
            case NEW_SELF:
                mv.visitTypeInsn(Opcodes.NEW, newType);
                mv.visitInsn(Opcodes.DUP);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, newType, "<init>", "()V", false);
                mv.visitInsn(Opcodes.ARETURN);
                break;
            case RETURN_THIS:
                // decoy: construct an unrelated object, then return this
                mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
                mv.visitInsn(Opcodes.DUP);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                        "java/lang/StringBuilder", "<init>", "()V", false);
                mv.visitInsn(Opcodes.POP);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitInsn(Opcodes.ARETURN);
                break;
            case RETURN_NULL:
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitInsn(Opcodes.ARETURN);
                break;
            default:
                break;
        }
        mv.visitMaxs(2, 2);
        mv.visitEnd();
    }
}
