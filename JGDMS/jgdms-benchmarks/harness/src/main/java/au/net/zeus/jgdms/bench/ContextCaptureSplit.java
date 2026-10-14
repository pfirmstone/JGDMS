/*
 * Copyright (C) 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.bench;

import au.net.zeus.jgdms.bench.workload.RequestHandler;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.BenchmarkParams;

/**
 * Diagnostic: splits {@code AccessController.getContext()} (CheckBreakdown's
 * {@code getContext}) into its stages, each called from the workload domain
 * through {@link RequestHandler#invoke} so the stack holds the same domains.
 *
 * <ul>
 * <li>{@link #invokeBaseline}: a handle returning a constant; the cost of the
 *     invocation plumbing, to subtract from the others.</li>
 * <li>{@link #stackWalk}: the native {@code getStackAccessControlContext()}
 *     alone: the VM stack walk plus allocating the raw context.</li>
 * <li>{@link #stackWalkOptimize}: the walk followed by
 *     {@code AccessControlContext.optimize()} (the inherited-context native,
 *     combining, and the context cache lookup).
 *     {@code stackWalkOptimize - stackWalk} is the cost of {@code optimize()}.</li>
 * <li>{@link #inheritedContext}: the native
 *     {@code getInheritedAccessControlContext()} alone; part of
 *     {@code optimize()}, timed to show the cost of one native transition.</li>
 * <li>{@link #subjectLookup}: {@code SubjectDomainCombiner.currentAll()}, the
 *     Subject (ScopedValue) lookup {@code getContext()} makes after
 *     {@code optimize()}. Measures the plumbing only if this JDK has no such
 *     method; set-up says so.</li>
 * <li>{@link #getContext}: the whole of {@code getContext()} through the same
 *     plumbing, as the reference total.</li>
 * <li>{@link #stackWalkAtDepth}: the stack walk with 0, 32 and 128 extra
 *     frames, separating the walk's fixed cost from its cost per frame.</li>
 * </ul>
 *
 * <p>The private JDK methods are reached through
 * {@code MethodHandles.privateLookupIn}, which needs the {@code --add-opens}
 * options in each subclass's {@code @Fork(jvmArgsPrepend)} and, under the
 * SecurityManager, {@code ReflectPermission "suppressAccessChecks"} for the
 * harness (recorded by the audit). Set-up fails with an explanation if
 * either is missing. Set-up also checks that {@code optimize()} of the raw
 * stack context returns the same cached instance as {@code getContext()},
 * which confirms the split sees the same stack.
 *
 * <p>Not part of the off/on comparison and ignored by summarise.py. Written
 * with AI assistance (Claude); see the README.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Threads(1)
public abstract class ContextCaptureSplit {

    /** Packages whose private members the handles reach; see the subclasses' @Fork. */
    static final String ADD_OPENS_1 = "--add-opens=java.base/java.security=ALL-UNNAMED";
    static final String ADD_OPENS_2 = "--add-opens=java.base/javax.security.auth=ALL-UNNAMED";

    private static final MethodType RETURNS_OBJECT = MethodType.methodType(Object.class);

    @State(Scope.Thread)
    public static class Handles {
        MethodHandle baseline;
        MethodHandle stackWalk;
        MethodHandle stackWalkOptimize;
        MethodHandle inherited;
        MethodHandle subject;
        MethodHandle getContext;

        @Setup(Level.Trial)
        @SuppressWarnings("removal")
        public void setup(BenchmarkParams params) throws Throwable {
            if (System.getSecurityManager() == null) {
                throw new IllegalStateException("ContextCaptureSplit needs a SecurityManager;"
                        + " pass extra fork options with -jvmArgsAppend only if they include"
                        + " -Djava.security.manager=default");
            }
            boolean expectVirtual = params.getBenchmark().contains(".ContextCaptureSplitVirtual.");
            if (expectVirtual != Thread.currentThread().isVirtual()) {
                throw new IllegalStateException(params.getBenchmark() + " expected to run on a "
                        + (expectVirtual ? "virtual" : "platform") + " thread;"
                        + " -jvmArgsAppend on the command line replaced -Djmh.executor=VIRTUAL?");
            }

            MethodHandles.Lookup acLookup;
            MethodHandles.Lookup accLookup;
            try {
                acLookup = MethodHandles.privateLookupIn(AccessController.class, MethodHandles.lookup());
                accLookup = MethodHandles.privateLookupIn(AccessControlContext.class, MethodHandles.lookup());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("java.security is not open to the harness: the fork needs "
                        + ADD_OPENS_1 + " (set in @Fork(jvmArgsPrepend); a command-line"
                        + " -jvmArgsPrepend replaces it)", e);
            } catch (SecurityException e) {
                throw new IllegalStateException("the policy does not grant the harness"
                        + " ReflectPermission \"suppressAccessChecks\"; re-run the audit", e);
            }

            baseline = MethodHandles.constant(Object.class, null);

            MethodHandle walk = acLookup.findStatic(AccessController.class,
                    "getStackAccessControlContext", MethodType.methodType(AccessControlContext.class));
            MethodHandle optimize = accLookup.findVirtual(AccessControlContext.class,
                    "optimize", MethodType.methodType(AccessControlContext.class));
            stackWalk = walk.asType(RETURNS_OBJECT);
            stackWalkOptimize = MethodHandles.filterReturnValue(walk, optimize).asType(RETURNS_OBJECT);

            inherited = acLookup.findStatic(AccessController.class,
                    "getInheritedAccessControlContext", MethodType.methodType(AccessControlContext.class))
                    .asType(RETURNS_OBJECT);

            getContext = MethodHandles.publicLookup().findStatic(AccessController.class,
                    "getContext", MethodType.methodType(AccessControlContext.class))
                    .asType(RETURNS_OBJECT);

            subject = findCurrentAll();
            if (subject == null) {
                System.out.println("# jgdms-bench: SubjectDomainCombiner.currentAll() not found;"
                        + " subjectLookup measures the plumbing only");
                subject = baseline;
            }

            // The split must see the same stack (same domains) as getContext().
            Object raw = RequestHandler.invoke(stackWalk);
            Object optimized = RequestHandler.invoke(stackWalkOptimize);
            Object full = RequestHandler.invoke(getContext);
            Object direct = RequestHandler.context();
            System.out.println("# jgdms-bench: thread=" + (Thread.currentThread().isVirtual() ? "virtual" : "platform")
                    + ", raw stack context " + (raw == null ? "null" : "non-null")
                    + ", optimize(raw) same instance as getContext(): " + (optimized == full)
                    + ", as RequestHandler.context(): " + (optimized == direct)
                    + ", inherited context " + (RequestHandler.invoke(inherited) == null ? "null" : "non-null")
                    + ", frames on the stack at the walk (including hidden): "
                    + RequestHandler.invoke(MethodHandles.lookup().findStatic(ContextCaptureSplit.class,
                            "frameCount", RETURNS_OBJECT)));
        }

        /** DirtyChai's {@code static SubjectDomainCombiner currentAll()}, or null if absent. */
        @SuppressWarnings("removal")
        private static MethodHandle findCurrentAll() throws IllegalAccessException {
            Class<?> c = javax.security.auth.SubjectDomainCombiner.class;
            MethodHandles.Lookup l;
            try {
                l = MethodHandles.privateLookupIn(c, MethodHandles.lookup());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("javax.security.auth is not open to the harness;"
                        + " the fork needs " + ADD_OPENS_2, e);
            }
            try {
                return l.findStatic(c, "currentAll", MethodType.methodType(c)).asType(RETURNS_OBJECT);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }
    }

    /** Invocation plumbing only: subtract from the others. */
    @Benchmark
    public Object invokeBaseline(Handles h) throws Throwable {
        return RequestHandler.invoke(h.baseline);
    }

    /** Native stack walk and raw context allocation. */
    @Benchmark
    public Object stackWalk(Handles h) throws Throwable {
        return RequestHandler.invoke(h.stackWalk);
    }

    /** Stack walk then optimize(): the whole of getContext() except the Subject lookup. */
    @Benchmark
    public Object stackWalkOptimize(Handles h) throws Throwable {
        return RequestHandler.invoke(h.stackWalkOptimize);
    }

    /** The inherited-context native alone. */
    @Benchmark
    public Object inheritedContext(Handles h) throws Throwable {
        return RequestHandler.invoke(h.inherited);
    }

    /** The Subject lookup getContext() makes after optimize(). */
    @Benchmark
    public Object subjectLookup(Handles h) throws Throwable {
        return RequestHandler.invoke(h.subject);
    }

    /** The whole of getContext(), through the same plumbing. */
    @Benchmark
    public Object getContext(Handles h) throws Throwable {
        return RequestHandler.invoke(h.getContext);
    }

    /**
     * Extra harness frames below the workload frame, for
     * {@link #stackWalkAtDepth}. They are all in the harness domain, which is
     * already on the stack, so the context's domains don't change; only the
     * number of frames the walk visits does.
     */
    @State(Scope.Thread)
    public static class Depth {
        @Param({"0", "32", "128"})
        int extraFrames;
    }

    /**
     * The stack walk with {@code extraFrames} more frames on the stack. The
     * slope across depths is the walk's cost per frame; the intercept at 0 is
     * its fixed cost (VM transition, allocation, context creation). Large
     * fixed cost favours memoising the result; large per-frame cost favours
     * making frames cheaper to visit.
     */
    @Benchmark
    public Object stackWalkAtDepth(Handles h, Depth d) throws Throwable {
        return deeper(d.extraFrames, h.stackWalk);
    }

    /** Java frames below this one, counted as the VM's walk counts them. */
    private static Object frameCount() {
        return StackWalker.getInstance(StackWalker.Option.SHOW_HIDDEN_FRAMES).walk(s -> s.count());
    }

    private static Object deeper(int frames, MethodHandle target) throws Throwable {
        if (frames <= 0) {
            return RequestHandler.invoke(target);
        }
        Object result = deeper(frames - 1, target);
        return result; // not a tail call, so every frame stays on the stack
    }
}
