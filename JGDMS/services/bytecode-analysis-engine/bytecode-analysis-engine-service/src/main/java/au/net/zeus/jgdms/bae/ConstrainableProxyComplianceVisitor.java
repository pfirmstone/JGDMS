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
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import au.net.zeus.jgdms.api.codebase.ConstrainableProxyVerdict;

/**
 * ASM-based visitor that checks a single class for compliance with the
 * constrainable smart-proxy contract, using facts gathered across the whole JAR.
 *
 * <h2>What is a smart proxy (the gate)</h2>
 * A class is treated as an in-scope smart proxy when it BOTH crosses the wire and
 * holds a remote reference:
 * <ol>
 *   <li><b>Crosses the wire</b> — it is {@code @AtomicSerial}/{@code @Stateless}
 *       (self or a superclass) or implements {@code java.io.Serializable}.  A
 *       class that is neither is not marshalled (e.g. a local-only iterator
 *       wrapper) and is {@link ConstrainableProxyVerdict#NA}.</li>
 *   <li><b>Holds a remote reference</b> — it declares (or inherits) a field whose
 *       type is {@code java.rmi.Remote} or a sub-interface, OR implements
 *       {@code net.jini.export.ProxyAccessor}, OR extends
 *       {@code AbstractSmartProxy}.  A serializable value object with no remote
 *       reference (a connection-less {@code @AtomicSerial} datum, deserialized
 *       from a content-addressed DER stream — there is nothing to authenticate
 *       against) holds no such reference and is {@link ConstrainableProxyVerdict#NA}.</li>
 * </ol>
 *
 * <h2>Contract, once gated in</h2>
 * <ul>
 *   <li>Implements {@code java.io.Serializable} at all (whether or not also
 *       {@code @AtomicSerial}) →
 *       {@link ConstrainableProxyVerdict#JAVA_SERIALIZATION}: the {@code java.io}
 *       path bypasses the validating {@code (GetArg)} constructor, so
 *       {@code @AtomicSerial} must be the sole wire path.</li>
 *   <li>Reaches {@code RemoteMethodControl}: its concrete {@code setConstraints}
 *       must construct and return a <em>new proxy instance</em> with the
 *       constraints applied.  If it returns {@code this}/{@code null}, or never
 *       constructs a proxy-typed instance, that is a silent downgrade →
 *       {@link ConstrainableProxyVerdict#CONSTRAINTS_NOT_APPLIED}.  Missing
 *       {@code setConstraints} → {@link ConstrainableProxyVerdict#SETCONSTRAINTS_MISSING}.
 *       Otherwise {@link ConstrainableProxyVerdict#COMPLIANT}.</li>
 *   <li>Concrete and does not reach {@code RemoteMethodControl} →
 *       {@link ConstrainableProxyVerdict#NON_CONSTRAINABLE}: a client cannot
 *       impose {@code Integrity}/{@code ServerAuthentication}/{@code
 *       Confidentiality}, so the proxy (e.g. one speaking an alien,
 *       constraint-less protocol) cannot be trusted.  The existence of a
 *       constrainable sibling or subclass does <em>not</em> clear it: which
 *       concrete proxy a factory returns is a runtime decision on the server
 *       ref's type ({@code server instanceof RemoteMethodControl}), invisible to
 *       static analysis, so this concrete class can still be the wire instance —
 *       a live downgrade path.</li>
 * </ul>
 *
 * <p>An <b>abstract</b> class is never the runtime type of a deserialized wire
 * instance (only the concrete leaf named in the stream is), so an abstract proxy
 * base is always {@code NA} — its concrete subclasses each carry their own
 * verdict.</p>
 *
 * <p><b>Detection limit:</b> a proxy that hides its connection in a non-{@code
 * Remote} field and does not implement {@code ProxyAccessor} is
 * indistinguishable from a benign value object by a purely structural scan and
 * resolves to {@code NA}.  The backstop for that case is the load gate itself —
 * an unauthorised codebase digest has no {@code LoadClassPermission} and never
 * defines.
 *
 * <p>Analysis is two-pass, like {@link JarAnalyzer}'s clinit call-graph:
 * {@link #extract(byte[])} pulls per-class {@link ProxyClassFacts}; then
 * {@link #verdict(ProxyClassFacts, Map)} resolves them against the whole-JAR
 * facts map.  Type names that live outside the JAR (e.g. framework base classes)
 * are matched by name where it matters ({@code AbstractSmartProxy},
 * {@code ConstrainableSmartProxy}, {@code RemoteMethodControl}, {@code Remote}).
 *
 * <p>Thread safety: a new visitor instance is created per class analysed.
 *
 * @see JarAnalyzer
 * @see AtomicSerialComplianceVisitor
 * @since 3.1.1
 * @author Peter Firmstone
 */
final class ConstrainableProxyComplianceVisitor extends ClassVisitor {

    private static final int ASM_API = Opcodes.ASM9;

    static final String ABSTRACT_SMART_PROXY =
            "au/net/zeus/jgdms/proxy/AbstractSmartProxy";
    static final String CONSTRAINABLE_SMART_PROXY =
            "au/net/zeus/jgdms/proxy/AbstractSmartProxy$ConstrainableSmartProxy";
    private static final String REMOTE_METHOD_CONTROL =
            "net/jini/core/constraint/RemoteMethodControl";
    private static final String REMOTE = "java/rmi/Remote";
    private static final String SERIALIZABLE = "java/io/Serializable";
    private static final String PROXY_ACCESSOR = "net/jini/export/ProxyAccessor";
    private static final String ATOMIC_SERIAL_DESC =
            "Lorg/apache/river/api/io/AtomicSerial;";
    private static final String STATELESS_DESC =
            "Lorg/apache/river/api/io/AtomicSerial$Stateless;";
    private static final String SET_CONSTRAINTS = "setConstraints";
    // setConstraints(MethodConstraints) — any return type (covariant overrides allowed)
    private static final String SET_CONSTRAINTS_PARAMS =
            "(Lnet/jini/core/constraint/MethodConstraints;)";

    /**
     * Immutable-after-extraction bag of the facts a single class contributes to
     * the constrainable-proxy analysis.
     */
    static final class ProxyClassFacts {
        String internalName;
        String superName;
        String[] interfaces = new String[0];
        boolean isInterface;
        boolean isAbstract;
        boolean unreadable;
        /** {@code @AtomicSerial} or {@code @Stateless} is present on this class. */
        boolean atomicSerial;
        /** Internal names of the declared object-typed fields (for Remote-ref detection). */
        final Set<String> objectFieldTypes = new HashSet<String>();
        /** Declares a concrete (non-abstract, non-bridge) setConstraints(MethodConstraints). */
        boolean declaresSetConstraints;
        /** That setConstraints returns {@code this}. */
        boolean setConstraintsReturnsThis;
        /** That setConstraints returns {@code null}. */
        boolean setConstraintsReturnsNull;
        /** Internal names of the types that setConstraints constructs ({@code NEW}). */
        final Set<String> setConstraintsNewedTypes = new HashSet<String>();
    }

    private final ProxyClassFacts facts = new ProxyClassFacts();

    private ConstrainableProxyComplianceVisitor() {
        super(ASM_API);
    }

    /**
     * Extracts the {@link ProxyClassFacts} from a single class's bytes.
     * On parse failure the returned facts are flagged {@link ProxyClassFacts#unreadable}.
     *
     * @param classBytes raw bytes of one {@code .class} entry; must be non-null
     * @return the facts; never {@code null}
     */
    static ProxyClassFacts extract(byte[] classBytes) {
        if (classBytes == null) throw new NullPointerException("classBytes");
        ConstrainableProxyComplianceVisitor v = new ConstrainableProxyComplianceVisitor();
        try {
            ClassReader cr = new ClassReader(classBytes);
            cr.accept(v, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        } catch (Exception e) {
            v.facts.unreadable = true;
        }
        return v.facts;
    }

    /**
     * Resolves a class's facts, against the whole-JAR facts map, to a verdict.
     *
     * @param self the class under analysis; must be non-null
     * @param all  facts for every class in the JAR, keyed by internal name
     * @return the verdict; never {@code null}
     */
    static ConstrainableProxyVerdict verdict(ProxyClassFacts self,
                                             Map<String, ProxyClassFacts> all) {
        if (self.unreadable) return ConstrainableProxyVerdict.UNREADABLE;
        if (self.isInterface) return ConstrainableProxyVerdict.NA;
        // An abstract class is never the runtime type of a deserialized wire
        // instance — the concrete leaf named in the stream is, and that leaf is
        // analysed on its own.  So an abstract proxy base (including the
        // framework's AbstractSmartProxy / ConstrainableSmartProxy) carries no
        // verdict of its own.
        if (self.isAbstract) return ConstrainableProxyVerdict.NA;
        // Defensive: the framework base classes are never user proxies even if a
        // future revision were to make one concrete.
        if (ABSTRACT_SMART_PROXY.equals(self.internalName)
                || CONSTRAINABLE_SMART_PROXY.equals(self.internalName)) {
            return ConstrainableProxyVerdict.NA;
        }

        // GATE 1 — does it cross the wire?
        boolean atomic = reachesAtomicSerial(self, all);
        boolean serializable = reaches(self.internalName, SERIALIZABLE, all, null);
        if (!atomic && !serializable) return ConstrainableProxyVerdict.NA;

        // GATE 2 — does it hold a remote reference (i.e. is it a smart proxy, not a value object)?
        boolean holdsRemote = hasRemoteInterfaceField(self, all)
                || reaches(self.internalName, PROXY_ACCESSOR, all, null)
                || reaches(self.internalName, ABSTRACT_SMART_PROXY, all, single(CONSTRAINABLE_SMART_PROXY));
        if (!holdsRemote) return ConstrainableProxyVerdict.NA;

        // In scope.  A smart proxy that implements java.io.Serializable AT ALL is
        // flagged — whether or not it is also @AtomicSerial.  The java.io path
        // (readObject / default deserialization) bypasses the validating (GetArg)
        // constructor, so when both are present it is an unvalidated backdoor; and
        // when @AtomicSerial is absent there is no validation at all.  @AtomicSerial
        // must be the sole wire path, so java.io.Serializable is discouraged outright.
        if (serializable) return ConstrainableProxyVerdict.JAVA_SERIALIZATION;

        // Constrainability.
        boolean constrainable = reaches(self.internalName, REMOTE_METHOD_CONTROL, all,
                single(CONSTRAINABLE_SMART_PROXY));
        if (constrainable) {
            ProxyClassFacts decl = findSetConstraintsDeclarer(self, all);
            if (decl == null) return ConstrainableProxyVerdict.SETCONSTRAINTS_MISSING;
            if (decl.setConstraintsReturnsThis || decl.setConstraintsReturnsNull) {
                return ConstrainableProxyVerdict.CONSTRAINTS_NOT_APPLIED; // silent downgrade
            }
            if (buildsNewProxyInstance(decl, all)) return ConstrainableProxyVerdict.COMPLIANT;
            return ConstrainableProxyVerdict.CONSTRAINTS_NOT_APPLIED;
        }
        // Concrete, crosses the wire, holds a remote reference, but cannot
        // express constraints.  The existence of a constrainable sibling or
        // subclass does NOT clear it: which concrete proxy a factory hands back
        // is a runtime decision on the server ref's type (e.g.
        // {@code server instanceof RemoteMethodControl}), invisible to static
        // analysis — this concrete class can still be the instance on the wire,
        // a live downgrade path.
        return ConstrainableProxyVerdict.NON_CONSTRAINABLE;
    }

    // -------------------------------------------------------------------------
    // Resolution helpers (whole-JAR, verdict-time)
    // -------------------------------------------------------------------------

    private static Set<String> single(String s) {
        Set<String> set = new HashSet<String>(2);
        set.add(s);
        return set;
    }

    /**
     * Reachability over the super + interface graph from {@code start}: true if
     * {@code target} (or any name in {@code alsoTrue}) is {@code start} itself or
     * an ancestor.  Nodes absent from {@code all} (types in other JARs) still
     * match by name but cannot be traversed further.
     */
    private static boolean reaches(String start, String target,
                                   Map<String, ProxyClassFacts> all, Set<String> alsoTrue) {
        if (start == null) return false;
        Set<String> seen = new HashSet<String>();
        Deque<String> stack = new ArrayDeque<String>();
        stack.push(start);
        while (!stack.isEmpty()) {
            String n = stack.pop();
            if (n == null || !seen.add(n)) continue;
            if (target.equals(n) || (alsoTrue != null && alsoTrue.contains(n))) return true;
            ProxyClassFacts f = all.get(n);
            if (f != null) {
                if (f.superName != null) stack.push(f.superName);
                for (String i : f.interfaces) stack.push(i);
            }
        }
        return false;
    }

    /** {@code @AtomicSerial}/{@code @Stateless} on the class or any in-JAR superclass. */
    private static boolean reachesAtomicSerial(ProxyClassFacts self,
                                               Map<String, ProxyClassFacts> all) {
        Set<String> seen = new HashSet<String>();
        ProxyClassFacts cur = self;
        while (cur != null && seen.add(cur.internalName)) {
            if (cur.atomicSerial) return true;
            cur = all.get(cur.superName);
        }
        return false;
    }

    /** A field (own or inherited within the JAR) whose type is {@code Remote} or extends it. */
    private static boolean hasRemoteInterfaceField(ProxyClassFacts self,
                                                   Map<String, ProxyClassFacts> all) {
        Set<String> seen = new HashSet<String>();
        ProxyClassFacts cur = self;
        while (cur != null && seen.add(cur.internalName)) {
            for (String ft : cur.objectFieldTypes) {
                if (reaches(ft, REMOTE, all, null)) return true;
            }
            cur = all.get(cur.superName);
        }
        return false;
    }

    /** Walks the in-JAR super chain to the nearest class that concretely declares setConstraints. */
    private static ProxyClassFacts findSetConstraintsDeclarer(ProxyClassFacts self,
                                                              Map<String, ProxyClassFacts> all) {
        Set<String> seen = new HashSet<String>();
        ProxyClassFacts cur = self;
        while (cur != null && seen.add(cur.internalName)) {
            if (cur.declaresSetConstraints) return cur;
            cur = all.get(cur.superName);
        }
        return null;
    }

    /**
     * True if the declaring class's setConstraints constructs an instance of a
     * proxy type (one that reaches {@code RemoteMethodControl}).  This rejects
     * the "construct an unrelated throwaway ({@code new StringBuilder()}) and
     * return {@code this}" decoy while accepting {@code return new
     * ConstrainableFooProxy(...)}.
     */
    private static boolean buildsNewProxyInstance(ProxyClassFacts decl,
                                                  Map<String, ProxyClassFacts> all) {
        for (String t : decl.setConstraintsNewedTypes) {
            if (reaches(t, REMOTE_METHOD_CONTROL, all, single(CONSTRAINABLE_SMART_PROXY))) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // ClassVisitor overrides
    // -------------------------------------------------------------------------

    @Override
    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
        facts.internalName = name;
        facts.superName = superName;
        facts.interfaces = interfaces != null ? interfaces : new String[0];
        facts.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
        facts.isAbstract = (access & Opcodes.ACC_ABSTRACT) != 0;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (ATOMIC_SERIAL_DESC.equals(descriptor) || STATELESS_DESC.equals(descriptor)) {
            facts.atomicSerial = true;
        }
        return super.visitAnnotation(descriptor, visible);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor,
                                   String signature, Object value) {
        Type t = Type.getType(descriptor);
        if (t.getSort() == Type.OBJECT) {
            facts.objectFieldTypes.add(t.getInternalName());
        }
        return super.visitField(access, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                     String signature, String[] exceptions) {
        boolean bridgeOrSynthetic =
                (access & (Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC)) != 0;
        boolean isAbstractMethod = (access & Opcodes.ACC_ABSTRACT) != 0;
        if (SET_CONSTRAINTS.equals(name)
                && descriptor.startsWith(SET_CONSTRAINTS_PARAMS)
                && !bridgeOrSynthetic && !isAbstractMethod) {
            facts.declaresSetConstraints = true;
            return new SetConstraintsAnalyzer();
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    // -------------------------------------------------------------------------
    // Inner method visitor: inspects a setConstraints body
    // -------------------------------------------------------------------------

    /**
     * Records the types {@code setConstraints} constructs ({@code NEW}) and
     * whether it returns {@code this} ({@code ALOAD_0; ARETURN}) or {@code null}
     * ({@code ACONST_NULL; ARETURN}).
     */
    private final class SetConstraintsAnalyzer extends MethodVisitor {

        private boolean pendingAload0;
        private boolean pendingAconstNull;

        SetConstraintsAnalyzer() {
            super(ASM_API);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == Opcodes.NEW) facts.setConstraintsNewedTypes.add(type);
            clearPending();
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            clearPending();
            if (opcode == Opcodes.ALOAD && var == 0) pendingAload0 = true;
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.ACONST_NULL) {
                clearPending();
                pendingAconstNull = true;
                return;
            }
            if (opcode == Opcodes.ARETURN) {
                if (pendingAload0)     facts.setConstraintsReturnsThis = true;
                if (pendingAconstNull) facts.setConstraintsReturnsNull = true;
            }
            clearPending();
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                    String descriptor, boolean isInterface) {
            clearPending();
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            clearPending();
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            clearPending();
        }

        @Override
        public void visitLdcInsn(Object value) {
            clearPending();
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            clearPending();
        }

        private void clearPending() {
            pendingAload0 = false;
            pendingAconstNull = false;
        }
    }
}
