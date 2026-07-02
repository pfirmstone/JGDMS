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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import au.net.zeus.jgdms.api.codebase.ConstrainableProxyVerdict;

/**
 * ASM-based visitor that checks a single class for compliance with the
 * constrainable smart-proxy contract.
 *
 * <p>Because the check spans the superclass/interface graph (is the class a
 * smart proxy? does it reach {@link net.jini.core.constraint.RemoteMethodControl}?
 * where is its concrete {@code setConstraints}?), analysis is two-pass — exactly
 * like {@link JarAnalyzer}'s clinit call-graph:
 * <ol>
 *   <li>{@link #extract(byte[])} pulls the per-class facts
 *       ({@link ProxyClassFacts}) from every class in the JAR;</li>
 *   <li>{@link #verdict(ProxyClassFacts, Map)} resolves the facts across the
 *       whole JAR to a {@link ConstrainableProxyVerdict}.</li>
 * </ol>
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>A concrete subclass of {@code AbstractSmartProxy} that is not
 *       constrainable is {@link ConstrainableProxyVerdict#NON_CONSTRAINABLE}.</li>
 *   <li>A constrainable proxy (reaches {@code RemoteMethodControl}) whose
 *       concrete {@code setConstraints} does not construct and return a new
 *       instance — it returns {@code this}/{@code null} or never does a
 *       {@code NEW}+{@code INVOKESPECIAL <init>} — is
 *       {@link ConstrainableProxyVerdict#CONSTRAINTS_NOT_APPLIED} (silent
 *       downgrade).</li>
 * </ul>
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
        /** Declares a concrete (non-abstract, non-bridge) setConstraints(MethodConstraints). */
        boolean declaresSetConstraints;
        /** That setConstraints does NEW + INVOKESPECIAL &lt;init&gt; (builds a new instance). */
        boolean setConstraintsBuildsNew;
        /** That setConstraints returns {@code this}. */
        boolean setConstraintsReturnsThis;
        /** That setConstraints returns {@code null}. */
        boolean setConstraintsReturnsNull;
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
        // The framework base classes themselves are not user proxies.
        if (ABSTRACT_SMART_PROXY.equals(self.internalName)
                || CONSTRAINABLE_SMART_PROXY.equals(self.internalName)) {
            return ConstrainableProxyVerdict.NA;
        }

        boolean constrainable = reachesRemoteMethodControl(self, all);
        boolean smartProxy = reachesAbstractSmartProxy(self, all);
        if (!constrainable && !smartProxy) return ConstrainableProxyVerdict.NA;

        // Only concrete, instantiable proxies matter; abstract bases/intermediates
        // are covered when their concrete subclasses are analysed.
        if (self.isAbstract) return ConstrainableProxyVerdict.NA;

        // A concrete smart proxy that cannot be constrained.
        if (!constrainable) return ConstrainableProxyVerdict.NON_CONSTRAINABLE;

        // Constrainable & concrete — inspect its (possibly inherited) setConstraints.
        ProxyClassFacts decl = findSetConstraintsDeclarer(self, all);
        if (decl == null) return ConstrainableProxyVerdict.SETCONSTRAINTS_MISSING;
        if (decl.setConstraintsBuildsNew) return ConstrainableProxyVerdict.COMPLIANT;
        return ConstrainableProxyVerdict.CONSTRAINTS_NOT_APPLIED;
    }

    private static boolean reachesRemoteMethodControl(ProxyClassFacts self,
                                                      Map<String, ProxyClassFacts> all) {
        for (String i : self.interfaces) {
            if (REMOTE_METHOD_CONTROL.equals(i)) return true;
        }
        Set<String> seen = new HashSet<String>();
        String cur = self.superName;
        while (cur != null && seen.add(cur)) {
            if (CONSTRAINABLE_SMART_PROXY.equals(cur)) return true;
            if (ABSTRACT_SMART_PROXY.equals(cur)) return false; // super is Object, not RMC
            ProxyClassFacts f = all.get(cur);
            if (f == null) return false;
            for (String i : f.interfaces) {
                if (REMOTE_METHOD_CONTROL.equals(i)) return true;
            }
            cur = f.superName;
        }
        return false;
    }

    private static boolean reachesAbstractSmartProxy(ProxyClassFacts self,
                                                     Map<String, ProxyClassFacts> all) {
        Set<String> seen = new HashSet<String>();
        String cur = self.superName;
        while (cur != null && seen.add(cur)) {
            if (ABSTRACT_SMART_PROXY.equals(cur) || CONSTRAINABLE_SMART_PROXY.equals(cur)) {
                return true;
            }
            ProxyClassFacts f = all.get(cur);
            if (f == null) return false;
            cur = f.superName;
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
     * Records whether {@code setConstraints} constructs a new instance
     * ({@code NEW} + {@code INVOKESPECIAL <init>}) and whether it returns
     * {@code this} ({@code ALOAD_0; ARETURN}) or {@code null}
     * ({@code ACONST_NULL; ARETURN}).
     */
    private final class SetConstraintsAnalyzer extends MethodVisitor {

        private boolean sawNew;
        private boolean sawInvokeSpecialInit;
        private boolean pendingAload0;
        private boolean pendingAconstNull;

        SetConstraintsAnalyzer() {
            super(ASM_API);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == Opcodes.NEW) sawNew = true;
            clearPending();
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                    String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESPECIAL && "<init>".equals(name)) {
                sawInvokeSpecialInit = true;
            }
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
                if (pendingAload0)    facts.setConstraintsReturnsThis = true;
                if (pendingAconstNull) facts.setConstraintsReturnsNull = true;
            }
            clearPending();
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            clearPending();
        }

        @Override
        public void visitJumpInsn(int opcode, org.objectweb.asm.Label label) {
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

        @Override
        public void visitEnd() {
            if (sawNew && sawInvokeSpecialInit) {
                facts.setConstraintsBuildsNew = true;
            }
            super.visitEnd();
        }

        private void clearPending() {
            pendingAload0 = false;
            pendingAconstNull = false;
        }
    }
}
