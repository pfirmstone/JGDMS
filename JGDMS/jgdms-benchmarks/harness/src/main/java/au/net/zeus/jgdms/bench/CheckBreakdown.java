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
import java.security.AccessControlContext;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.PropertyPermission;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Diagnostic: splits the cost of one permission check (PolicyOverheadSmOn's
 * {@code guardedRead}) into its stages, under the same SecurityManager and
 * policy, from the same workload domain.
 *
 * <ul>
 * <li>{@link #getContext}: capturing the access control context from the
 *     workload ({@code AccessController.getContext()}: the stack walk, the
 *     context cache lookups and {@code optimize()}).</li>
 * <li>{@link #smCheckFixedContext}: {@code SecurityManager.checkPermission}
 *     with a context captured once in set-up, so the SecurityManager's own
 *     caches keyed by that context should hit every time.</li>
 * <li>{@link #accCheckFixedContext}: {@code AccessControlContext.checkPermission}
 *     on that context, which evaluates the domains against the policy with no
 *     SecurityManager caching.</li>
 * </ul>
 *
 * <p>If {@code getContext} accounts for most of {@code guardedRead}, the
 * context capture is the cost; if {@code smCheckFixedContext} does, the
 * SecurityManager's checked-permission cache is not hitting.
 *
 * <p>Set-up also reports how many distinct context instances 100,000
 * captures from the workload produced, which a working context cache keeps
 * to a handful.
 *
 * <p>Platform thread only. Not part of the off/on comparison and ignored by
 * summarise.py. Written with AI assistance (Claude); see the README.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = "-Djava.security.manager=default")
@Threads(1)
public class CheckBreakdown {

    private static final int IDENTITY_SAMPLES = 100_000;

    @State(Scope.Thread)
    public static class Fixture {
        SecurityManager sm;
        AccessControlContext context;
        PropertyPermission permission;

        @Setup(Level.Trial)
        @SuppressWarnings("removal")
        public void setup() {
            sm = System.getSecurityManager();
            if (sm == null) {
                throw new IllegalStateException("CheckBreakdown needs a SecurityManager;"
                        + " do not override its fork arguments with -jvmArgsAppend");
            }
            permission = new PropertyPermission(PolicyOverhead.PROPERTIES[0], "read");
            context = RequestHandler.context();
            // Granted from the workload's context, as in guardedRead.
            sm.checkPermission(permission, context);

            Set<Object> instances = Collections.newSetFromMap(new IdentityHashMap<>());
            for (int i = 0; i < IDENTITY_SAMPLES; i++) {
                instances.add(RequestHandler.context());
            }
            System.out.println("# jgdms-bench: " + IDENTITY_SAMPLES
                    + " getContext() calls from the workload returned "
                    + instances.size() + " distinct context instance(s); "
                    + "SecurityManager=" + sm.getClass().getName());
            // The policy should see DigestCodeSources (which carry a parsed Uri);
            // anything else makes URI-based grants re-parse the location per check.
            System.out.println("# jgdms-bench: workload CodeSource="
                    + codeSourceClass(RequestHandler.class)
                    + ", harness CodeSource=" + codeSourceClass(CheckBreakdown.class));
        }

        private static String codeSourceClass(Class<?> c) {
            java.security.CodeSource cs = c.getProtectionDomain().getCodeSource();
            return cs == null ? "null" : cs.getClass().getName();
        }
    }

    /** Context capture alone, from the workload domain. */
    @Benchmark
    public AccessControlContext getContext() {
        return RequestHandler.context();
    }

    /** The SecurityManager's check against a context captured once. */
    @Benchmark
    @SuppressWarnings("removal")
    public void smCheckFixedContext(Fixture f) {
        f.sm.checkPermission(f.permission, f.context);
    }

    /** Policy evaluation of the context's domains, without SecurityManager caching. */
    @Benchmark
    @SuppressWarnings("removal")
    public void accCheckFixedContext(Fixture f) {
        f.context.checkPermission(f.permission);
    }
}
