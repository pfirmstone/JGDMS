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
import java.security.AllPermission;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.util.SplittableRandom;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.BenchmarkParams;

/**
 * The benchmarks themselves. Concrete subclasses differ only in how their
 * forked JVMs are launched (SecurityManager off or on, platform or virtual
 * benchmark threads), so the same code is measured in each configuration
 * and the results can be compared pairwise:
 *
 * <ul>
 * <li>{@link #guardedRead}: one {@code System.getProperty} call made from
 *     the restricted workload domain. With a SecurityManager this is one
 *     permission check; without one it is a plain property read. The
 *     difference between the two is the cost of a single check.</li>
 * <li>{@link #request}: a small request (parse 16 query parameters, CRC32 a
 *     512-byte body, format a response) that makes
 *     {@code guardedReadsPerRequest} permission-checked calls. The relative
 *     difference between SecurityManager on and off is the policy overhead
 *     for that workload. With 0 guarded reads it should be close to zero,
 *     which is the control.</li>
 * </ul>
 *
 * <p>Before measuring, {@link Environment} checks that each fork is really
 * running in the configuration it claims, so a misconfigured run fails
 * instead of producing plausible numbers.
 *
 * <p>Written with AI assistance (Claude); see the README.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public abstract class PolicyOverhead {

    /** Properties the workload reads; the policy grants it all of these. */
    static final String[] PROPERTIES = {
        "jgdms.bench.p0", "jgdms.bench.p1", "jgdms.bench.p2", "jgdms.bench.p3",
        "jgdms.bench.p4", "jgdms.bench.p5", "jgdms.bench.p6", "jgdms.bench.p7"
    };

    /** Set by the run scripts: the Policy class the SecurityManager forks must use. */
    static final String EXPECT_POLICY = "jgdms.bench.expectedPolicy";

    /**
     * Set by the run scripts in audit mode, when DirtyChai's polpAudit
     * (SecurityPolicyWriter) records the permissions used so it can write
     * them to conf/generated.policy. Nothing is measured in that mode.
     */
    static final String AUDIT = "jgdms.bench.audit";

    /**
     * Once per fork: refuses to run unless the fork's security configuration
     * is the one it claims to be. The workload's properties are set on the
     * command line by the run scripts, so no benchmark thread needs
     * permission to write them.
     */
    @State(Scope.Benchmark)
    public static class Environment {

        @Setup(Level.Trial)
        public void verify(BenchmarkParams params) {
            try {
                check(params.getBenchmark());
            } catch (SecurityException e) {
                throw new IllegalStateException("The benchmark thread " + Thread.currentThread()
                        + " was refused a permission. On DirtyChai, generate the policy"
                        + " first (BENCH_MODE=audit, see README): loading the workload jar"
                        + " needs grants that only polpAudit can enumerate. On stock OpenJDK"
                        + " 21-23, virtual threads run with no permissions under a"
                        + " SecurityManager, so the virtual-thread runs need DirtyChai.", e);
            }
        }

        @SuppressWarnings("removal")
        private void check(String benchmark) {
            // The expected configuration comes from the benchmark's class name,
            // not from a fork argument: a -jvmArgsAppend given on the command
            // line replaces the class's @Fork(jvmArgsAppend), silently dropping
            // -Djava.security.manager and -Djmh.executor. Use -jvmArgsPrepend
            // to add JVM options.
            String className = benchmark.substring(0, benchmark.lastIndexOf('.'));
            boolean expectSecurityManager = className.endsWith("SmOn");
            boolean expectVirtual = className.contains(".PolicyOverheadVirtual");
            SecurityManager sm = System.getSecurityManager();
            if (expectSecurityManager != (sm != null)
                    || expectVirtual != Thread.currentThread().isVirtual()) {
                throw new IllegalStateException(benchmark + " expects SecurityManager "
                        + (expectSecurityManager ? "installed" : "absent")
                        + " on a " + (expectVirtual ? "virtual" : "platform")
                        + " thread, but found SecurityManager " + sm + " on "
                        + Thread.currentThread() + ". If you passed -jvmArgsAppend on"
                        + " the command line, it replaced the benchmark's own fork"
                        + " arguments: use -jvmArgsPrepend instead.");
            }
            if (sm == null) {
                System.out.println("# jgdms-bench: no SecurityManager");
                return;
            }

            // Look these up in audit runs too, before the audit returns, so
            // the audit records every permission the measured runs need.
            Policy installed = Policy.getPolicy();
            String expectedPolicy = System.getProperty(EXPECT_POLICY);
            ProtectionDomain harnessDomain = PolicyOverhead.class.getProtectionDomain();
            ProtectionDomain workloadDomain = RequestHandler.class.getProtectionDomain();
            AllPermission all = new AllPermission();
            boolean harnessHasAll = harnessDomain.implies(all);
            boolean workloadHasAll = workloadDomain.implies(all);

            if (Boolean.getBoolean(AUDIT)) {
                // polpAudit allows everything and records what was used. Exercise
                // the granted path only: the refusal probe below must not run, or
                // the audit would record user.home and grant it to the workload.
                new RequestHandler(PROPERTIES).readGuarded(0);
                System.out.println("# jgdms-bench: AUDIT run with " + sm.getClass().getName()
                        + ": recording permissions for conf/generated.policy;"
                        + " these timings are not results");
                return;
            }

            // Least privilege: AllPermission would let a domain short-cut its
            // checks, and is not how code should be deployed.
            if (harnessHasAll || workloadHasAll) {
                throw new IllegalStateException("The policy grants AllPermission to the "
                        + (harnessHasAll ? "harness" : "workload")
                        + "; remove it and regenerate conf/generated.policy with"
                        + " BENCH_MODE=audit");
            }

            String policy = installed.getClass().getName();
            if (expectedPolicy != null && !expectedPolicy.isEmpty()
                    && !expectedPolicy.equals(policy)) {
                throw new IllegalStateException("Expected Policy " + expectedPolicy
                        + " but found " + policy
                        + "; check -Djava.security.properties in the run script");
            }

            // A granted read must succeed ...
            String value = new RequestHandler(PROPERTIES).readGuarded(0);
            if (value == null) {
                throw new IllegalStateException(PROPERTIES[0] + " is not set; "
                        + "run the benchmarks with run.sh or run.cmd");
            }
            // ... and an ungranted one must be refused, or the workload domain
            // is not restricted and the checks being measured are not real.
            try {
                RequestHandler.readProperty("user.home");
                throw new IllegalStateException("The workload read user.home: its "
                        + "domain is not restricted. Check the codeBase URLs and the "
                        + "workload's grants in the policy file.");
            } catch (SecurityException expected) {
                // correct: the policy is in force for the workload's CodeSource
            }
            System.out.println("# jgdms-bench: SecurityManager=" + sm.getClass().getName()
                    + ", Policy=" + policy);
        }
    }

    /** Per benchmark thread: its own handler and counter, so threads share nothing. */
    @State(Scope.Thread)
    public static class Worker {
        RequestHandler handler;
        int next;

        @Setup(Level.Trial)
        public void setup(Environment environment) {
            handler = new RequestHandler(PROPERTIES);
        }
    }

    /** Per benchmark thread: one fixed request, built from a fixed seed. */
    @State(Scope.Thread)
    public static class Request {

        /** Permission-checked operations per request; 0 is the control. */
        @Param({"0", "1", "4"})
        public int guardedReadsPerRequest;

        String query;
        byte[] body;

        @Setup(Level.Trial)
        public void setup() {
            StringBuilder q = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                if (i > 0) {
                    q.append('&');
                }
                q.append("param").append(i).append('=').append("value-").append(i * 7919);
            }
            query = q.toString();
            body = new byte[512];
            SplittableRandom random = new SplittableRandom(42);
            for (int i = 0; i < body.length; i++) {
                body[i] = (byte) random.nextInt(256);
            }
        }
    }

    /** One permission check (SecurityManager on) or one plain property read (off). */
    @Benchmark
    public String guardedRead(Worker worker) {
        return worker.handler.readGuarded(worker.next++);
    }

    /** One small request containing {@code guardedReadsPerRequest} permission checks. */
    @Benchmark
    public long request(Worker worker, Request request) {
        return worker.handler.handle(request.query, request.body,
                request.guardedReadsPerRequest);
    }
}
