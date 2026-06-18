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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACC_SYNCHRONIZED;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_8;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

/**
 * Tests for bytecode patterns that the Java 8 fixture compiler cannot produce:
 * the {@code @AtomicSerial} cross-boundary path, and the virtual-thread executor
 * exclusion (SOW &sect;9.2).  These are generated directly with ASM.
 */
public class AsmGeneratedTest {

    private static void defaultCtor(ClassWriter cw) {
        MethodVisitor c = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitCode();
        c.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
        c.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        c.visitInsn(RETURN);
        c.visitMaxs(0, 0);
        c.visitEnd();
    }

    private static ClassDecision classify(String internalName, byte[] bytes) {
        List<ClassDecision> ds = new PreferredAnalyzer()
                .analyze(Collections.singletonMap(internalName, bytes));
        return ds.get(0);
    }

    @Test
    public void atomicSerialWithStaticLockIsConflict() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_8, ACC_PUBLIC | ACC_SUPER, "gen/AtomicConflict",
                null, "java/lang/Object", null);
        AnnotationVisitor av =
                cw.visitAnnotation("Lorg/apache/river/api/io/AtomicSerial;", true);
        av.visitEnd();
        defaultCtor(cw);
        MethodVisitor m = cw.visitMethod(ACC_STATIC | ACC_SYNCHRONIZED, "f", "()V",
                null, null);
        m.visitCode();
        m.visitInsn(RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();

        ClassDecision d = classify("gen/AtomicConflict", cw.toByteArray());
        assertTrue("@AtomicSerial is cross-boundary", d.isCrossBoundary());
        assertEquals(Decision.CONFLICT, d.getAnalysisDecision());
    }

    @Test
    public void virtualThreadExecutorFieldIsNotAHazard() {
        byte[] b = executorHolder("gen/VtHolder", "newVirtualThreadPerTaskExecutor");

        ClassSignals s = new ClassSignalScanner(AnalyzerConfig.defaults()).scan(b);
        assertTrue("vt field recognised",
                s.getVirtualThreadExecutorFields().contains("VT"));

        ClassDecision d = classify("gen/VtHolder", b);
        assertEquals(Decision.SHARE, d.getAnalysisDecision());
        assertFalse(FixtureSupport.hasKind(d, HazardKind.CONTENDED_STATIC_FIELD));
    }

    @Test
    public void plainExecutorFieldIsAHazard() {
        byte[] b = executorHolder("gen/PoolHolder", "newCachedThreadPool");
        ClassDecision d = classify("gen/PoolHolder", b);
        assertTrue(FixtureSupport.hasKind(d, HazardKind.CONTENDED_STATIC_FIELD));
        assertEquals(Decision.PREFER, d.getAnalysisDecision());
    }

    /** A class with a {@code static final ExecutorService VT} seeded from the named factory. */
    private static byte[] executorHolder(String internalName, String factory) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_8, ACC_PUBLIC | ACC_SUPER, internalName, null,
                "java/lang/Object", null);
        cw.visitField(ACC_STATIC | ACC_FINAL, "VT",
                "Ljava/util/concurrent/ExecutorService;", null, null).visitEnd();
        defaultCtor(cw);
        MethodVisitor cl = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        cl.visitCode();
        cl.visitMethodInsn(INVOKESTATIC, "java/util/concurrent/Executors", factory,
                "()Ljava/util/concurrent/ExecutorService;", false);
        cl.visitFieldInsn(PUTSTATIC, internalName, "VT",
                "Ljava/util/concurrent/ExecutorService;");
        cl.visitInsn(RETURN);
        cl.visitMaxs(0, 0);
        cl.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
