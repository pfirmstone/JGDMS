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
package org.apache.river.tool.preferred;

import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * ASM {@link ClassVisitor} that gathers the {@link ClassSignals} for a single
 * class &mdash; the purely local bytecode facts the {@link DecisionEngine} needs
 * (SOW &sect;4).
 *
 * <p>Uses core ASM only (no tree/analysis API), matching the existing
 * {@code bytecode-analysis-engine} visitors.  Stack-shape tracking for the
 * "{@code synchronized} on a static field", "blocking call under a static lock"
 * and "static container mutated at runtime" signals is done with lightweight
 * instruction-sequence heuristics rather than full dataflow; the heuristics are
 * conservative and documented on each method visitor.
 *
 * <p>A scanner instance is stateless and reusable; {@link #scan(byte[])} builds
 * fresh state per class. Not thread-safe per call only in the trivial sense that
 * each {@code scan} call is independent &mdash; use one scanner per thread.
 *
 * @see ClassSignals
 * @see DecisionEngine
 */
public final class ClassSignalScanner {

    private static final int ASM_API = Opcodes.ASM9;

    private static final String ATOMIC_SERIAL_DESC =
            "Lorg/apache/river/api/io/AtomicSerial;";
    private static final String SERIALIZABLE_INTERNAL = "java/io/Serializable";
    private static final String CLINIT = "<clinit>";

    private final AnalyzerConfig config;

    public ClassSignalScanner(AnalyzerConfig config) {
        if (config == null) throw new NullPointerException("config");
        this.config = config;
    }

    /**
     * Parses {@code classBytes} and returns the gathered {@link ClassSignals}.
     *
     * @param classBytes raw bytes of a single {@code .class} entry; non-null
     * @return the signals; or, if ASM cannot parse the class, an empty signals
     *         object whose name is derived best-effort (never null)
     */
    public ClassSignals scan(byte[] classBytes) {
        if (classBytes == null) throw new NullPointerException("classBytes");
        try {
            ClassReader cr = new ClassReader(classBytes);
            Collector c = new Collector();
            cr.accept(c, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            if (c.signals == null) {
                return ClassSignals.unparseable(cr.getClassName());
            }
            return c.signals;
        } catch (RuntimeException parseFailure) {
            return ClassSignals.unparseable("<unparseable>");
        }
    }

    /** Extracts the object element type of a field descriptor, or null for primitives. */
    static String objectInternalType(String descriptor) {
        int i = 0;
        while (i < descriptor.length() && descriptor.charAt(i) == '[') i++;
        if (i >= descriptor.length()) return null;
        if (descriptor.charAt(i) != 'L') return null; // primitive (element)
        int semi = descriptor.indexOf(';', i);
        if (semi < 0) return null;
        return descriptor.substring(i + 1, semi);
    }

    // ------------------------------------------------------------------------

    private final class Collector extends ClassVisitor {

        private ClassSignals signals;
        private String        thisClass;

        Collector() {
            super(ASM_API);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.thisClass = name;
            boolean isPublic    = (access & Opcodes.ACC_PUBLIC)    != 0;
            boolean isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
            signals = new ClassSignals(name, isPublic, isInterface, superName, interfaces);
            if (interfaces != null) {
                for (String iface : interfaces) {
                    if (SERIALIZABLE_INTERNAL.equals(iface)) {
                        signals.setImplementsSerializableDirectly(true);
                    }
                }
            }
            // Member inner classes carry the enclosing name before the last '$'.
            int dollar = name.lastIndexOf('$');
            if (dollar > 0) {
                signals.setEnclosingClass(name.substring(0, dollar));
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (ATOMIC_SERIAL_DESC.equals(descriptor)) {
                signals.setAtomicSerial(true);
            }
            return null;
        }

        @Override
        public void visitOuterClass(String owner, String name, String descriptor) {
            // Local / anonymous classes: the immediately enclosing class.
            if (owner != null && signals.getEnclosingClass() == null) {
                signals.setEnclosingClass(owner);
            }
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            if ((access & Opcodes.ACC_STATIC) != 0) {
                boolean isFinal = (access & Opcodes.ACC_FINAL) != 0;
                signals.addStaticField(new StaticFieldInfo(
                        name, descriptor, objectInternalType(descriptor), isFinal));
            }
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            boolean isStatic = (access & Opcodes.ACC_STATIC)       != 0;
            boolean isSync   = (access & Opcodes.ACC_SYNCHRONIZED) != 0;
            if (isStatic && isSync) {
                signals.addStaticSynchronizedMethod(name + descriptor);
            }
            return new MethodScanner(name, isStatic && isSync);
        }

        // --------------------------------------------------------------------

        /**
         * Per-method instruction scanner.  Tracks:
         * <ul>
         *   <li>{@code PUTSTATIC} of this class's fields outside {@code <clinit>}
         *       ({@link StaticFieldInfo#isWrittenOutsideClinit()});</li>
         *   <li>{@code synchronized(staticField)} via a {@code GETSTATIC} of a
         *       this-class field that survives the {@code DUP}/{@code ASTORE}
         *       monitor dance into a {@code MONITORENTER};</li>
         *   <li>blocking calls made while a static monitor is held (a
         *       {@code static synchronized} method body, or inside such a
         *       block);</li>
         *   <li>a mutating collection/map call on a this-class static container
         *       read in the same non-{@code <clinit>} method
         *       ({@link StaticFieldInfo#isMutatedOutsideClinit()}).</li>
         * </ul>
         */
        private final class MethodScanner extends MethodVisitor {

            private final String  methodName;
            private final boolean isStaticSync;
            private final boolean inClinit;

            /** Depth of held static monitors (this-class fields or class literals). */
            private int staticMonitorDepth;

            /** Name of the this-class static field most recently loaded as a monitor candidate. */
            private String monitorCandidateField;
            /** True if the most recent monitor candidate is a class literal ({@code Foo.class}). */
            private boolean monitorCandidateClassLiteral;

            /** This-class static container fields read (GETSTATIC) in this method body. */
            private final Set<String> readContainers = new HashSet<String>();

            /** In {@code <clinit>}: owner/name of the most recent method invocation. */
            private String lastInvokeOwnerName;

            MethodScanner(String methodName, boolean isStaticSync) {
                super(ASM_API);
                this.methodName   = methodName;
                this.isStaticSync = isStaticSync;
                this.inClinit     = CLINIT.equals(methodName);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                boolean isThisClass = owner.equals(thisClass);

                if (opcode == Opcodes.PUTSTATIC && isThisClass) {
                    StaticFieldInfo f = signals.findStaticField(name);
                    if (f != null) {
                        if (!inClinit) {
                            f.setWrittenOutsideClinit(true);
                        } else if (config.isExecutorFieldType(f.getInternalType())
                                && config.isVirtualThreadExecutorFactory(lastInvokeOwnerName)) {
                            // executor field seeded from a virtual-thread factory
                            signals.addVirtualThreadExecutorField(name);
                        }
                    }
                }

                if (opcode == Opcodes.GETSTATIC && isThisClass) {
                    StaticFieldInfo f = signals.findStaticField(name);
                    monitorCandidateField        = name; // this-class static field
                    monitorCandidateClassLiteral = false;
                    if (!inClinit && f != null
                            && config.isMutableContainerType(f.getInternalType())) {
                        readContainers.add(name);
                    }
                } else {
                    clearMonitorCandidate();
                }
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name,
                                        String descriptor, boolean isInterface) {
                String ownerName = owner + "/" + name;
                lastInvokeOwnerName = ownerName; // for the <clinit> vt-factory check

                if ((isStaticSync || staticMonitorDepth > 0)
                        && config.isBlockingMethod(ownerName)) {
                    signals.addBlockingUnderStaticLock(
                            methodName + " holds static lock and calls " + owner + "." + name);
                }

                if (!inClinit && config.isMutatingMethod(name) && !readContainers.isEmpty()) {
                    for (String fieldName : readContainers) {
                        StaticFieldInfo f = signals.findStaticField(fieldName);
                        if (f != null) f.setMutatedOutsideClinit(true);
                    }
                }

                clearMonitorCandidate();
            }

            @Override
            public void visitInsn(int opcode) {
                if (opcode == Opcodes.MONITORENTER) {
                    if (monitorCandidateField != null) {
                        signals.addSynchronizedOnStaticField(monitorCandidateField);
                        staticMonitorDepth++;
                    } else if (monitorCandidateClassLiteral) {
                        staticMonitorDepth++;
                    }
                    clearMonitorCandidate();
                } else if (opcode == Opcodes.MONITOREXIT) {
                    if (staticMonitorDepth > 0) staticMonitorDepth--;
                    clearMonitorCandidate();
                } else if (opcode != Opcodes.DUP) {
                    // DUP is part of the monitor store dance — preserve the candidate.
                    clearMonitorCandidate();
                }
            }

            @Override
            public void visitVarInsn(int opcode, int var) {
                // (A)STORE / (A)LOAD around the monitor dance — preserve the candidate.
            }

            @Override
            public void visitLdcInsn(Object cst) {
                if (cst instanceof Type) {
                    Type t = (Type) cst;
                    if (t.getSort() == Type.OBJECT || t.getSort() == Type.ARRAY) {
                        monitorCandidateField        = null;
                        monitorCandidateClassLiteral = true; // synchronized(Foo.class)
                        return;
                    }
                }
                clearMonitorCandidate();
            }

            @Override public void visitTypeInsn(int opcode, String type) { clearMonitorCandidate(); }
            @Override public void visitJumpInsn(int opcode, Label label) { clearMonitorCandidate(); }
            @Override public void visitIntInsn(int opcode, int operand) { clearMonitorCandidate(); }
            @Override public void visitIincInsn(int var, int increment) { clearMonitorCandidate(); }

            private void clearMonitorCandidate() {
                monitorCandidateField        = null;
                monitorCandidateClassLiteral = false;
            }
        }
    }
}
