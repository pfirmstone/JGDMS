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

import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import au.net.zeus.jgdms.api.codebase.ClinitVerdict;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * Tests for the T2 generalization of {@link ClinitBlockingVisitor}: the
 * whole-invocable-surface reachability entry points
 * ({@link ClinitBlockingVisitor#analyzeInvocableSurfaceReachability},
 * {@link ClinitBlockingVisitor#analyzeReachability}) and the pluggable
 * {@link ClinitBlockingVisitor.SinkPolicy} seam.
 *
 * <p>These are adversarial: each probe constructs a call graph designed to
 * evade whole-surface detection (a sink reachable only from a non-{@code
 * <clinit>} method, an orphan private helper, a class's own native method, a
 * reflection hop into a private method, a chain longer than the depth bound),
 * and asserts the analyzer catches it (or, for reflection, that whole-surface
 * rooting closes the variant that the call graph <em>can</em> resolve).
 */
public class ReachabilitySurfaceTest {

    private static final String CN = "au/net/zeus/jgdms/bae/test/Surface";
    private static final int DEPTH = 20;

    // ---- helpers ------------------------------------------------------------

    private static Map<String, Set<String>> callGraph() { return new HashMap<>(); }

    private static String index(byte[] bytes, Map<String, Set<String>> cg,
                                Map<String, Boolean> nat) {
        String[] owner = { null };
        ClinitBlockingVisitor.indexClass(bytes, cg, nat, owner);
        return owner[0];
    }

    private static ClinitVerdict clinit(String cn, Map<String, Set<String>> cg,
                                        Map<String, Boolean> nat) {
        return ClinitBlockingVisitor.analyzeClinitReachability(cn, cg, nat, DEPTH).verdict;
    }

    private static ClinitBlockingVisitor.ClinitAnalysisResult surface(
            String cn, Map<String, Set<String>> cg, Map<String, Boolean> nat) {
        return ClinitBlockingVisitor.analyzeInvocableSurfaceReachability(
                cn, cg, nat, DEPTH, ClinitBlockingVisitor.BLOCKING_SINK_POLICY);
    }

    private static void emitSleep(MethodVisitor mv) {
        mv.visitInsn(Opcodes.LCONST_0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                "java/lang/Thread", "sleep", "(J)V", false);
    }

    // ---- Probe 1: sink reachable ONLY from a public method, not <clinit> ----

    /**
     * The class has a clean (empty) {@code <clinit>} but a public instance
     * method that blocks.  The historical {@code <clinit>}-only check must
     * report CLEAN (it did before T2, and must still); the whole-surface check
     * must report BLOCKING — this is the entire point of T2.
     */
    @Test
    public void publicMethodSink_clinitClean_surfaceBlocking() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                CN, null, "java/lang/Object", null);
        // empty <clinit>
        MethodVisitor ci = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        ci.visitCode(); ci.visitInsn(Opcodes.RETURN); ci.visitMaxs(0, 0); ci.visitEnd();
        // public void run() { Thread.sleep(0); }
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        mv.visitCode(); emitSleep(mv); mv.visitInsn(Opcodes.RETURN); mv.visitMaxs(2, 1); mv.visitEnd();
        cw.visitEnd();

        Map<String, Set<String>> cg = callGraph();
        Map<String, Boolean> nat = new HashMap<>();
        String cn = index(cw.toByteArray(), cg, nat);

        assertEquals("clinit-only must not regress", ClinitVerdict.CLEAN, clinit(cn, cg, nat));
        assertEquals("whole surface must catch the public-method sink",
                ClinitVerdict.BLOCKING, surface(cn, cg, nat).verdict);
    }

    // ---- Probe 2: multi-hop chain from a public root before the sink --------

    /**
     * public a() -> b() -> c() -> Thread.sleep().  None of these is
     * {@code <clinit>}; the sink is three hops from the public entry.  The
     * surface BFS must traverse the chain and report BLOCKING with the full
     * path.
     */
    @Test
    public void multiHopFromPublicRoot_surfaceBlockingWithPath() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                CN, null, "java/lang/Object", null);
        for (String[] hop : new String[][] { {"a", "b"}, {"b", "c"} }) {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, hop[0], "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CN, hop[1], "()V", false);
            mv.visitInsn(Opcodes.RETURN); mv.visitMaxs(1, 1); mv.visitEnd();
        }
        MethodVisitor c = cw.visitMethod(Opcodes.ACC_PUBLIC, "c", "()V", null, null);
        c.visitCode(); emitSleep(c); c.visitInsn(Opcodes.RETURN); c.visitMaxs(2, 1); c.visitEnd();
        cw.visitEnd();

        Map<String, Set<String>> cg = callGraph();
        Map<String, Boolean> nat = new HashMap<>();
        String cn = index(cw.toByteArray(), cg, nat);

        ClinitBlockingVisitor.ClinitAnalysisResult r = surface(cn, cg, nat);
        assertEquals(ClinitVerdict.BLOCKING, r.verdict);
        assertTrue("path must terminate at the sink",
                r.callPath.get(r.callPath.size() - 1).equals("java/lang/Thread/sleep/(J)V"));
        assertTrue("path must start at a method of the analysed class",
                r.callPath.get(0).startsWith(CN + "/"));
    }

    // ---- Probe 3: orphan private helper (called by nothing) -----------------

    /**
     * A private method blocks but is called by no other method in the class
     * (an "orphan").  A call-graph rooted only at public entry points would
     * never reach it.  Because the whole-surface roots include every declared
     * method, the orphan is itself a root and its sink is caught.
     */
    @Test
    public void orphanPrivateHelper_surfaceBlocking() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                CN, null, "java/lang/Object", null);
        MethodVisitor pub = cw.visitMethod(Opcodes.ACC_PUBLIC, "harmless", "()V", null, null);
        pub.visitCode(); pub.visitInsn(Opcodes.RETURN); pub.visitMaxs(0, 1); pub.visitEnd();
        MethodVisitor prv = cw.visitMethod(Opcodes.ACC_PRIVATE, "orphan", "()V", null, null);
        prv.visitCode(); emitSleep(prv); prv.visitInsn(Opcodes.RETURN); prv.visitMaxs(2, 1); prv.visitEnd();
        cw.visitEnd();

        Map<String, Set<String>> cg = callGraph();
        Map<String, Boolean> nat = new HashMap<>();
        String cn = index(cw.toByteArray(), cg, nat);

        assertEquals(ClinitVerdict.BLOCKING, surface(cn, cg, nat).verdict);
    }

    // ---- Probe 4: class's OWN unregistered native method as a root ----------

    /**
     * The class declares its own {@code native} method (no body, so no
     * out-edges for a callee-only scan to walk).  Such a method is opaque and
     * must be default-denied.  Because root nodes are themselves evaluated,
     * the surface analysis reports NATIVE_OPACITY.
     */
    @Test
    public void ownNativeMethodAsRoot_surfaceNativeOpacity() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                CN, null, "java/lang/Object", null);
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_NATIVE, "doNative", "()V", null, null).visitEnd();
        cw.visitEnd();

        Map<String, Set<String>> cg = callGraph();
        Map<String, Boolean> nat = new HashMap<>();
        String cn = index(cw.toByteArray(), cg, nat);

        ClinitBlockingVisitor.ClinitAnalysisResult r = surface(cn, cg, nat);
        assertEquals(ClinitVerdict.NATIVE_OPACITY, r.verdict);
        assertEquals("path is the native root itself",
                CN + "/doNative/()V", r.callPath.get(r.callPath.size() - 1));
    }

    // ---- Probe 5: reflection into an own private method ----------------------

    /**
     * Adversarial reflection laundering: a public method calls
     * {@code Method.invoke} (opaque to any call graph) to reach a private
     * method that blocks.  The reflection edge itself is unresolvable — but
     * because whole-surface rooting includes the private target as its own
     * root, the sink is still caught.  This is the deliberate defeat of the
     * "route the dangerous call through reflection" evasion for own-class
     * methods.
     */
    @Test
    public void reflectionIntoOwnPrivateMethod_surfaceStillBlocking() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                CN, null, "java/lang/Object", null);
        // public void entry() { getClass().getDeclaredMethod("secret").invoke(this); }
        MethodVisitor e = cw.visitMethod(Opcodes.ACC_PUBLIC, "entry", "()V", null, null);
        e.visitCode();
        e.visitVarInsn(Opcodes.ALOAD, 0);
        e.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass",
                "()Ljava/lang/Class;", false);
        e.visitLdcInsn("secret");
        e.visitInsn(Opcodes.ICONST_0);
        e.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
        e.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getDeclaredMethod",
                "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
        e.visitVarInsn(Opcodes.ALOAD, 0);
        e.visitInsn(Opcodes.ICONST_0);
        e.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        e.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Method", "invoke",
                "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
        e.visitInsn(Opcodes.POP);
        e.visitInsn(Opcodes.RETURN); e.visitMaxs(4, 1); e.visitEnd();
        // private void secret() { Thread.sleep(0); }
        MethodVisitor s = cw.visitMethod(Opcodes.ACC_PRIVATE, "secret", "()V", null, null);
        s.visitCode(); emitSleep(s); s.visitInsn(Opcodes.RETURN); s.visitMaxs(2, 1); s.visitEnd();
        cw.visitEnd();

        Map<String, Set<String>> cg = callGraph();
        Map<String, Boolean> nat = new HashMap<>();
        String cn = index(cw.toByteArray(), cg, nat);

        assertEquals("whole-surface rooting catches the reflection-laundered sink",
                ClinitVerdict.BLOCKING, surface(cn, cg, nat).verdict);
    }

    // ---- Probe 6: depth bound is enforced on the surface traversal ----------

    /**
     * A chain of self-calls longer than {@code maxDepth} must not reach a sink
     * past the bound (fail-open-to-CLEAN by depth is the same posture the
     * {@code <clinit>} check has; the bound exists to cap cost).  Chain length
     * 6, sink at the end, maxDepth 3 => not reached (CLEAN); maxDepth 10 =>
     * reached (BLOCKING).
     */
    @Test
    public void depthBoundEnforcedOnSurface() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                CN, null, "java/lang/Object", null);
        int chain = 6;
        for (int i = 0; i < chain; i++) {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "h" + i, "()V", null, null);
            mv.visitCode();
            if (i < chain - 1) {
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, CN, "h" + (i + 1), "()V", false);
            } else {
                emitSleep(mv);
            }
            mv.visitInsn(Opcodes.RETURN); mv.visitMaxs(2, 1); mv.visitEnd();
        }
        cw.visitEnd();

        Map<String, Set<String>> cg = callGraph();
        Map<String, Boolean> nat = new HashMap<>();
        String cn = index(cw.toByteArray(), cg, nat);

        // Root at h0 only, so the sink is exactly `chain-1` hops away.
        Set<String> h0 = java.util.Collections.singleton(CN + "/h0/()V");
        ClinitVerdict shallow = ClinitBlockingVisitor.analyzeReachability(
                h0, cg, nat, 3, ClinitBlockingVisitor.BLOCKING_SINK_POLICY).verdict;
        ClinitVerdict deep = ClinitBlockingVisitor.analyzeReachability(
                h0, cg, nat, 10, ClinitBlockingVisitor.BLOCKING_SINK_POLICY).verdict;
        assertEquals("sink beyond the bound must not be reached", ClinitVerdict.CLEAN, shallow);
        assertEquals("sink within the bound must be reached", ClinitVerdict.BLOCKING, deep);
    }

    // ---- Probe 7: SinkPolicy seam — a nanoTime-family timing denylist -------

    /**
     * Demonstrates the follow-on reuse the T2 seam exists for: a timing policy
     * that treats {@code System.nanoTime}/{@code currentTimeMillis} (which the
     * default blocking policy treats as known-safe natives) as sinks.  Same
     * BFS, same root generalization, different policy — no machinery
     * duplicated.  The default policy must still see the same class as CLEAN.
     */
    @Test
    public void sinkPolicySeam_timingDenylist() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                CN, null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "measure", "()J", null, null);
        mv.visitCode();
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
        mv.visitInsn(Opcodes.LRETURN); mv.visitMaxs(2, 1); mv.visitEnd();
        cw.visitEnd();

        Map<String, Set<String>> cg = callGraph();
        Map<String, Boolean> nat = new HashMap<>();
        String cn = index(cw.toByteArray(), cg, nat);

        // Default (blocking) policy: nanoTime is a safe native -> CLEAN.
        assertEquals(ClinitVerdict.CLEAN, surface(cn, cg, nat).verdict);

        // Timing policy: nanoTime/currentTimeMillis are sinks.
        ClinitBlockingVisitor.SinkPolicy timing = new ClinitBlockingVisitor.SinkPolicy() {
            @Override public boolean isSink(String o, String n, String d) {
                return "java/lang/System".equals(o)
                        && ("nanoTime".equals(n) || "currentTimeMillis".equals(n));
            }
            @Override public boolean isSafeNative(String o, String n, String d) {
                return BlockingSinkRegistry.isSafeNative(o, n, d);
            }
            @Override public ClinitVerdict sinkVerdict(List<String> path, Map<String, Set<String>> cgp) {
                return ClinitVerdict.BLOCKING; // reuse the enum as the "hit" shape
            }
        };
        ClinitBlockingVisitor.ClinitAnalysisResult r =
                ClinitBlockingVisitor.analyzeInvocableSurfaceReachability(cn, cg, nat, DEPTH, timing);
        assertEquals(ClinitVerdict.BLOCKING, r.verdict);
        assertEquals("java/lang/System/nanoTime/()J", r.callPath.get(r.callPath.size() - 1));
    }

    // ---- Probe 8: guard detection still works from a non-clinit root --------

    /**
     * BLOCKING_GUARDED must be produced when a permission guard sits on a
     * blocking path rooted at a non-{@code <clinit>} method, exactly as it is
     * for {@code <clinit>} — proving the guard heuristic threads through the
     * generalized path unchanged.
     */
    @Test
    public void guardedBlockingFromPublicRoot() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                CN, null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/SecurityManager");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/SecurityManager", "<init>", "()V", false);
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimePermission");
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("test");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/RuntimePermission", "<init>",
                "(Ljava/lang/String;)V", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/SecurityManager", "checkPermission",
                "(Ljava/security/Permission;)V", false);
        emitSleep(mv);
        mv.visitInsn(Opcodes.RETURN); mv.visitMaxs(4, 1); mv.visitEnd();
        cw.visitEnd();

        Map<String, Set<String>> cg = callGraph();
        Map<String, Boolean> nat = new HashMap<>();
        String cn = index(cw.toByteArray(), cg, nat);

        assertEquals(ClinitVerdict.BLOCKING_GUARDED, surface(cn, cg, nat).verdict);
    }

    // ---- Probe 9: absent/empty roots are CLEAN, and clinit isolation --------

    @Test
    public void emptyRootsAndUnknownClassAreClean() {
        Map<String, Set<String>> cg = callGraph();
        Map<String, Boolean> nat = new HashMap<>();
        // no classes indexed
        assertEquals(ClinitVerdict.CLEAN,
                ClinitBlockingVisitor.analyzeInvocableSurfaceReachability(
                        "does/not/Exist", cg, nat, DEPTH,
                        ClinitBlockingVisitor.BLOCKING_SINK_POLICY).verdict);
        assertTrue(ClinitBlockingVisitor.invocableSurfaceRoots("does/not/Exist", cg).isEmpty());
    }

    /**
     * Sibling-prefix isolation: {@code invocableSurfaceRoots("com/Foo")} must
     * NOT capture methods of {@code com/FooBar}.
     */
    @Test
    public void invocableSurfaceRoots_siblingPrefixIsolation() {
        Map<String, Set<String>> cg = callGraph();
        cg.put("com/Foo/m/()V", java.util.Collections.emptySet());
        cg.put("com/FooBar/m/()V", java.util.Collections.emptySet());
        cg.put("com/Foo$Inner/m/()V", java.util.Collections.emptySet());
        Set<String> roots = ClinitBlockingVisitor.invocableSurfaceRoots("com/Foo", cg);
        assertTrue(roots.contains("com/Foo/m/()V"));
        assertFalse("must not capture sibling com/FooBar", roots.contains("com/FooBar/m/()V"));
        assertFalse("must not capture inner class com/Foo$Inner", roots.contains("com/Foo$Inner/m/()V"));
        assertEquals(1, roots.size());
    }
}
