/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.bench.cel;

import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.authoring.CelParseException;
import au.net.zeus.jgdms.cel.math.CorrectlyRoundedMath;
import au.net.zeus.jgdms.cel.math.MathProvider;
import au.net.zeus.jgdms.cel.math.MathProviders;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.BenchmarkParams;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * STD-011 stage 1: what one scan record costs the production CEL evaluator, against native code doing the same
 * arithmetic. THE OPERATION IS ONE RECORD (JMH divides each invocation's time by its {@code @OperationsPerInvocation}).
 *
 * <h2>Which records are timed (review F1, Opus 2026-11-11; the first version cycled the sample from index 0 by one)</h2>
 * Every benchmark draws from one fixed sample of {@value #SAMPLE} in-front records spread over the real scan
 * ({@link Sampling}, in file order). Every cursor advances by {@value #STRIDE}, which is odd and so coprime with
 * {@value #SAMPLE}: a cursor visits every sample position before repeating, and consecutive records are a quarter of the
 * file apart rather than neighbours. Each fork starts its cursor at {@code startOffset} (a JMH parameter: 0, 1365, 2731), so
 * the forks of one benchmark draw different records; run with {@code -f 1} and JMH makes one fork per offset.
 * <ul>
 *   <li>The block benchmarks (64 or 8 records per invocation) cover many positions per iteration.</li>
 *   <li>{@code celE2Placeholder} (one record per invocation, tens of milliseconds each) visits the cursor sequence for as long
 *       as it runs: far fewer than {@value #SAMPLE} positions, so its population is the first few hundred of the sequence.</li>
 *   <li>{@code celE2} and {@code nativeE2CrMatched} form the MATCHED PAIR for the headline ratio. Both cycle the same ring of
 *       {@value #E2_RING} sample positions, {@code (startOffset + STRIDE * r) & 4095} for {@code r = 0..3}, one record per
 *       invocation. {@code celE2} is single-shot (exactly one invocation per iteration), so any run of {@value #E2_RING}
 *       consecutive iterations times each ring record once; the annotation's 8 measured iterations time each twice. The matched
 *       native cycles the ring for the whole run, so the two cover the same records with equal weight. Passing {@code -i} or
 *       {@code -wi} on the command line overrides the annotation and breaks that (use a multiple of {@value #E2_RING} measured
 *       iterations).</li>
 *   <li>The records actually timed by the one-record benchmarks are printed to stdout at the end of the fork
 *       ({@code TIMED-RECORDS ...}); keep the console log beside the JSON.</li>
 * </ul>
 * The cost of a correctly-rounded call depends on its argument, so a population matters: {@code celE2} divided by
 * {@code nativeE2Cr} (64-record blocks over the whole cycled sample) mixes two populations; {@code celE2} divided by
 * {@code nativeE2CrMatched} does not.
 *
 * <h2>Benchmarks</h2>
 * <ul>
 *   <li>{@code celE1}, {@code celE2}: the production {@code Evaluator} with the correctly-rounded provider
 *       ({@code MathProviders.correctlyRounded()}); the projection's per-record {@code set} is INSIDE the timed path.
 *       E2 is the tree STD-011 v0.1 would force (every reuse written out) and is REJECTED by the v0.1 ceilings: it is
 *       measured anyway, as the brief requires.</li>
 *   <li>{@code celE1Compiled}, {@code celE2Compiled} (design {@code DESIGN_COMPILED_EVALUATOR.md} section 6.4 and 9 items 12-15,
 *       scoring claim CL-CHISEL-0027): the production {@code CompiledExpression} through its public API
 *       ({@link CompiledRunner}), same correctly-rounded provider, same projection with {@code set} in the timed path.
 *       {@code celE1Compiled} uses {@code celE1}'s cursor and block, so the two cover the same records. {@code celE2Compiled}
 *       is average-time (steady state, NOT single shot) over the SAME 4-record ring as {@code nativeE2CrMatched}, one record per
 *       invocation, so {@code celE2Compiled / nativeE2CrMatched} is over one population. Compile time is measured once per fork
 *       in {@link CelCompiled#build} and printed ({@code COMPILE-TIME}), never per record. Before any timing,
 *       {@link CompiledSelfCheck} checks the compiled result against the walker's bit for bit (E1 on all {@value #SAMPLE}
 *       sample records, E2 on the ring) and counts correctly-rounded calls per record ({@code CALLCOUNT}).</li>
 *   <li>{@code nativeE1Cr}, {@code nativeE2Cr}: native arithmetic with the same correctly-rounded functions (B-CR);
 *       {@code nativeE2CrMatched} is the same code over the ring (see above). {@code nativeE1Strict}, {@code nativeE2Strict}:
 *       with {@code StrictMath} (B-STRICT).</li>
 *   <li>{@code projectionOnly}: {@code set} plus the three {@code fieldValue} reads, no arithmetic. HYPOTHESIS: the field
 *       names are compile-time constants here, so the JIT may fold the {@code switch} in {@code fieldValue}, where the evaluator
 *       passes names from the AST; read it as "{@code set} plus near-free reads".</li>
 *   <li>EXTRA, beyond the brief and labelled so: {@code celE1Placeholder}, {@code celE2Placeholder} run the SAME evaluator
 *       and trees with the production's StrictMath-backed test placeholder instead of the correctly-rounded provider, so
 *       that the tree-walking overhead can be separated from the cost of correct rounding. They are NOT conformant
 *       STD-011 evaluations.</li>
 * </ul>
 * Block sizes differ ({@code @OperationsPerInvocation}) only so that one invocation lasts long enough to time: the
 * written-out E2 under correct rounding makes 156,553 correctly-rounded calls per record (counted, correctness test P9).
 * Results go to a {@link Blackhole}; the evaluator's {@code EvalOutcome} is consumed whole, the native results by value.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Xms2g", "-Xmx4g"})
public class CelEvalBenchmark {

    /** In-front records in the cycled sample (a power of two so the cursor wraps with a mask). */
    static final int SAMPLE = 4096;
    /** Cursor step: odd, hence coprime with {@link #SAMPLE}, so every position is visited before any repeats. */
    static final int STRIDE = 1031;
    /** Records the matched pair shares (a power of two); {@code celE2} times each of them {@code iterations / E2_RING} times. */
    static final int E2_RING = 4;
    static final int BLOCK = 64;
    static final int BLOCK_E1_CR = 8;
    static final int BLOCK_ONE = 1;

    /** Cap on the one-record timed-record log; a run that exceeds it says so rather than growing without bound. */
    private static final int LOG_CAP = 1 << 14;
    private static final int MAX_ITERATIONS_LOGGED = 64;

    /** Sample position of ring slot {@code r} for a fork that starts at {@code startOffset}. */
    static int ringPosition(int startOffset, int r) {
        return (startOffset + STRIDE * r) & (SAMPLE - 1);
    }

    /**
     * The scan columns and the sample, loaded once per fork. Prints the census so a run records what it ran on, and refuses
     * to run when any of the timed records would be timed on an error path (review F7).
     */
    @State(Scope.Benchmark)
    public static class Data {
        /** Where each fork's cursor starts; one fork per value when run with {@code -f 1}. */
        @Param({"0", "1365", "2731"})
        public int startOffset;

        RawScanColumns cols;
        int[] sample;

        @Setup(Level.Trial)
        public void load() throws IOException, CelParseException {
            cols = RawScanColumns.load(Sampling.rawscanPath());
            if (cols.census.crcFailuresRecords() != 0 || cols.census.crcFailuresPage0() != 0) {
                throw new IllegalStateException("the scan has page CRC failures; refusing to time on it");
            }
            Stage1Expressions.Camera cam = Stage1Expressions.synthetic().camera();
            Sampling.Front front = Sampling.inFront(cols, new NativeBaselines(new Trig.Strict(), cam));
            sample = front.evenly(SAMPLE);
            verifyTimedSample(cols.range, cols.azimuth, cols.elevation, sample);
            System.err.println(cols.census.describe());
            System.err.println(new StringBuilder("in-front valid returns: ").append(front.indices().length).append(" of ")
                    .append(front.validReturns()).append(" valid returns of ").append(front.records())
                    .append(" records; cycled sample ").append(SAMPLE).append("; startOffset ").append(startOffset)
                    .append("; stride ").append(STRIDE).toString());
        }

        /**
         * Review F7: a timed record that took an error path (for example a DOMAIN error from a non-finite argument) would be
         * timed as a cheap evaluation and nobody would see it. Every sampled record must give finite native E2 outputs
         * (StrictMath; bit-identity makes that a proxy for the CEL path) and a finite 3-element E1 result under BOTH the
         * correctly-rounded and the placeholder providers ({@code CelRunner.evaluateInto} fails on anything but a 3-element
         * list). Fails the setup, naming the record.
         */
        static void verifyTimedSample(float[] range, float[] azimuth, float[] elevation, int[] sample)
                throws CelParseException {
            Stage1Expressions x = Stage1Expressions.synthetic();
            ExprNode e1 = x.e1();
            RecordProjection projection = new RecordProjection(range, azimuth, elevation);
            CelRunner correct = new CelRunner(e1, MathProviders.correctlyRounded(), projection);
            CelRunner placeholder = new CelRunner(e1, MathProvider.nonConformantForTestingOnly(), projection);
            NativeBaselines strict = new NativeBaselines(new Trig.Strict(), x.camera());
            double[] e2out = new double[2];
            double[] e1out = new double[3];
            for (int i : sample) {
                strict.e2(range[i], azimuth[i], elevation[i], e2out);
                requireFinite(i, "native E2 (StrictMath)", e2out);
                correct.evaluateInto(i, e1out);
                requireFinite(i, "CEL E1 (correctly rounded)", e1out);
                placeholder.evaluateInto(i, e1out);
                requireFinite(i, "CEL E1 (placeholder)", e1out);
            }
        }

        private static void requireFinite(int record, String what, double[] values) {
            for (double v : values) {
                if (!Double.isFinite(v)) {
                    throw new IllegalStateException(new StringBuilder("timed record ").append(record).append(": ").append(what)
                            .append(" gave a non-finite value ").append(v).toString());
                }
            }
        }
    }

    /** The two trees, and one runner per (tree, provider) pair. Per thread: the evaluator and projection are not shared. */
    @State(Scope.Thread)
    public static class Cel {
        RecordProjection projection;
        CelRunner e1Cr;
        CelRunner e2Cr;
        CelRunner e1Ph;
        CelRunner e2Ph;
        /** Position in the sample of the stride cursor (block benchmarks and celE2Placeholder). */
        int cursor;
        /** Ring slot of the next celE2 invocation. */
        int ring;
        String benchmark = "?";
        int startOffset;
        /** The records the one-record benchmarks actually timed, in order, and where each iteration began. */
        final int[] timed = new int[LOG_CAP];
        int timedCount;
        long invocations;
        final int[] iterationStart = new int[MAX_ITERATIONS_LOGGED];
        int iterations;

        @Setup(Level.Trial)
        public void build(Data data, BenchmarkParams params) throws CelParseException {
            Stage1Expressions x = Stage1Expressions.synthetic();
            ExprNode e1 = x.e1();
            ExprNode e2 = x.e2();
            projection = new RecordProjection(data.cols.range, data.cols.azimuth, data.cols.elevation);
            MathProvider cr = MathProviders.correctlyRounded();
            MathProvider placeholder = MathProvider.nonConformantForTestingOnly();
            e1Cr = new CelRunner(e1, cr, projection);
            e2Cr = new CelRunner(e2, cr, projection);
            e1Ph = new CelRunner(e1, placeholder, projection);
            e2Ph = new CelRunner(e2, placeholder, projection);
            startOffset = data.startOffset;
            cursor = startOffset & (SAMPLE - 1);
            benchmark = params.getBenchmark();
        }

        @Setup(Level.Iteration)
        public void markIteration() {
            if (iterations < MAX_ITERATIONS_LOGGED) {
                iterationStart[iterations] = timedCount;
            }
            iterations++;
        }

        /** Records one timed record; a few instructions against an invocation of milliseconds or more. */
        void log(int record) {
            invocations++;
            if (timedCount < LOG_CAP) {
                timed[timedCount++] = record;
            }
        }

        @TearDown(Level.Trial)
        public void printTimedRecords() {
            if (invocations == 0) {
                return;
            }
            StringBuilder sb = new StringBuilder(256);
            sb.append("TIMED-RECORDS benchmark=").append(benchmark).append(" startOffset=").append(startOffset)
                    .append(" stride=").append(STRIDE).append(" invocations=").append(invocations).append(" logged=")
                    .append(timedCount).append(timedCount < invocations ? " (log cap reached)" : "")
                    .append("; iterations in run order, the warm-up ones first\n");
            int shown = Math.min(iterations, MAX_ITERATIONS_LOGGED);
            for (int it = 0; it < shown; it++) {
                int from = iterationStart[it];
                int to = it + 1 < shown ? iterationStart[it + 1] : timedCount;
                sb.append("  iteration ").append(it).append(": records");
                for (int k = from; k < to; k++) {
                    sb.append(k == from ? " " : ",").append(timed[k]);
                }
                sb.append('\n');
            }
            System.out.print(sb);
        }
    }

    /** Both native baselines and their output buffers. */
    @State(Scope.Thread)
    public static class Nat {
        NativeBaselines cr;
        NativeBaselines strict;
        double[] out = new double[3];
        int cursor;
        int ring;
        String benchmark = "?";
        int startOffset;
        long invocations;
        final int[] ringRecords = new int[E2_RING];

        @Setup(Level.Trial)
        public void build(Data data, BenchmarkParams params) {
            Stage1Expressions.Camera cam = Stage1Expressions.synthetic().camera();
            CorrectlyRoundedMath crMath = MathProviders.correctlyRounded().get();
            cr = new NativeBaselines(crMath, cam);
            strict = new NativeBaselines(new Trig.Strict(), cam);
            startOffset = data.startOffset;
            cursor = startOffset & (SAMPLE - 1);
            benchmark = params.getBenchmark();
            for (int r = 0; r < E2_RING; r++) {
                ringRecords[r] = data.sample[ringPosition(startOffset, r)];
            }
        }

        @TearDown(Level.Trial)
        public void printRing() {
            if (invocations == 0) {
                return;
            }
            System.out.println(ringLine(benchmark, startOffset, invocations, ringRecords));
        }
    }

    /** The {@code TIMED-RECORDS} line of a benchmark that cycles the ring (the matched native and the compiled E2). */
    static String ringLine(String benchmark, int startOffset, long invocations, int[] ringRecords) {
        StringBuilder sb = new StringBuilder(160);
        sb.append("TIMED-RECORDS benchmark=").append(benchmark).append(" startOffset=").append(startOffset)
                .append(" stride=").append(STRIDE).append(" invocations=").append(invocations)
                .append(" ring of ").append(E2_RING).append(" records:");
        for (int r = 0; r < E2_RING; r++) {
            sb.append(r == 0 ? " " : ",").append(ringRecords[r]);
        }
        return sb.toString();
    }

    /**
     * The compiled E1 and E2 (design section 6.4), built once per fork. Per thread: a Session is not thread-safe. Compile time
     * is measured here, outside every timed method: the FIRST compile in the fork (cold: class loading and an unwarmed JIT
     * included) and the median of {@value #COMPILE_REPEATS} further compiles of the same tree (warm-ish; DIAGNOSTIC). A
     * "compile" here is the {@link CompiledRunner} constructor: {@code CompiledExpression.compile} plus {@code newSession}.
     * The setup ends with {@link CompiledSelfCheck#run}, on THIS instance, before any iteration.
     */
    @State(Scope.Thread)
    public static class CelCompiled {
        static final int COMPILE_REPEATS = 21;
        RecordProjection projection;
        CompiledRunner e1;
        CompiledRunner e2;
        /** Position in the sample of {@code celE1Compiled}'s stride cursor. */
        int cursor;
        /** Ring slot of the next {@code celE2Compiled} invocation, as in {@link Nat}. */
        int ring;
        String benchmark = "?";
        int startOffset;
        long invocations;
        final int[] ringRecords = new int[E2_RING];
        /** Keeps the repeated compiles observable, so the JIT cannot drop them from the timing loop. */
        CompiledRunner lastRepeat;

        @Setup(Level.Trial)
        public void build(Data data, BenchmarkParams params) throws CelParseException {
            Stage1Expressions x = Stage1Expressions.synthetic();
            ExprNode e1Tree = x.e1();
            ExprNode e2Tree = x.e2();
            projection = new RecordProjection(data.cols.range, data.cols.azimuth, data.cols.elevation);
            MathProvider cr = MathProviders.correctlyRounded();
            long t0 = System.nanoTime();
            e1 = new CompiledRunner(e1Tree, cr, projection);
            long t1 = System.nanoTime();
            e2 = new CompiledRunner(e2Tree, cr, projection);
            long t2 = System.nanoTime();
            long[] again1 = new long[COMPILE_REPEATS];
            long[] again2 = new long[COMPILE_REPEATS];
            for (int k = 0; k < COMPILE_REPEATS; k++) {
                long a = System.nanoTime();
                lastRepeat = new CompiledRunner(e1Tree, cr, projection);
                long b = System.nanoTime();
                lastRepeat = new CompiledRunner(e2Tree, cr, projection);
                long c = System.nanoTime();
                again1[k] = b - a;
                again2[k] = c - b;
            }
            Arrays.sort(again1);
            Arrays.sort(again2);
            startOffset = data.startOffset;
            cursor = startOffset & (SAMPLE - 1);
            benchmark = params.getBenchmark();
            for (int r = 0; r < E2_RING; r++) {
                ringRecords[r] = data.sample[ringPosition(startOffset, r)];
            }
            System.out.println(new StringBuilder("COMPILE-TIME benchmark=").append(benchmark).append(" startOffset=")
                    .append(startOffset).append(' ').append(tag()).append(": E1 first ").append(t1 - t0).append(" ns, E2 first ").append(t2 - t1)
                    .append(" ns (cold, one sample each); then median of ").append(COMPILE_REPEATS).append(": E1 ")
                    .append(again1[COMPILE_REPEATS / 2]).append(" ns (min ").append(again1[0]).append("), E2 ")
                    .append(again2[COMPILE_REPEATS / 2]).append(" ns (min ").append(again2[0]).append(')').toString());
            CompiledSelfCheck.run(data, this, benchmark);
        }

        /** Which instance and thread this is, so the log shows that the self-checked instance is the timed one. */
        String tag() {
            return new StringBuilder("[instance ").append(Integer.toHexString(System.identityHashCode(this)))
                    .append(", thread ").append(Thread.currentThread().getName()).append(']').toString();
        }

        @TearDown(Level.Trial)
        public void printRing() {
            if (invocations == 0) {
                return;
            }
            System.out.println(new StringBuilder(ringLine(benchmark, startOffset, invocations, ringRecords)).append(' ')
                    .append(tag()));
        }
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK)
    public void projectionOnly(Data d, Cel c, Blackhole bh) {
        int[] sample = d.sample;
        int cur = c.cursor;
        for (int b = 0; b < BLOCK; b++) {
            c.projection.set(sample[cur]);
            cur = (cur + STRIDE) & (SAMPLE - 1);
            bh.consume(c.projection.fieldValue(RecordProjection.CLASS_NAME, "range"));
            bh.consume(c.projection.fieldValue(RecordProjection.CLASS_NAME, "azimuth"));
            bh.consume(c.projection.fieldValue(RecordProjection.CLASS_NAME, "elevation"));
        }
        c.cursor = cur;
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK_E1_CR)
    public void celE1(Data d, Cel c, Blackhole bh) {
        int[] sample = d.sample;
        int cur = c.cursor;
        for (int b = 0; b < BLOCK_E1_CR; b++) {
            bh.consume(c.e1Cr.evaluate(sample[cur]));
            cur = (cur + STRIDE) & (SAMPLE - 1);
        }
        c.cursor = cur;
    }

    /**
     * Single shot: exactly one invocation (one record) per iteration, so the records timed are known from the iteration count
     * alone. The annotation values (1 warm-up, 8 measured) make the measured records the ring twice over.
     */
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 1, batchSize = 1)
    @Measurement(iterations = 8, batchSize = 1)
    @OperationsPerInvocation(BLOCK_ONE)
    public void celE2(Data d, Cel c, Blackhole bh) {
        int record = d.sample[ringPosition(c.startOffset, c.ring)];
        c.ring = (c.ring + 1) & (E2_RING - 1);
        c.log(record);
        bh.consume(c.e2Cr.evaluate(record));
    }

    /**
     * The compiled E1: {@code celE1}'s cursor, stride and block, so both cover the same records, with {@code projection.set}
     * inside the timed path.
     */
    @Benchmark
    @OperationsPerInvocation(BLOCK_E1_CR)
    public void celE1Compiled(Data d, CelCompiled c, Blackhole bh) {
        int[] sample = d.sample;
        int cur = c.cursor;
        for (int b = 0; b < BLOCK_E1_CR; b++) {
            bh.consume(c.e1.evaluate(sample[cur]));
            cur = (cur + STRIDE) & (SAMPLE - 1);
        }
        c.cursor = cur;
    }

    /**
     * The compiled E2 in steady-state block mode (design section 9 item 14; NOT single shot): one record per invocation over
     * the SAME ring as {@link #nativeE2CrMatched}, in the same order, so the two are over one population and the scoring ratio
     * {@code celE2Compiled / nativeE2CrMatched} is taken within one run.
     */
    @Benchmark
    @OperationsPerInvocation(BLOCK_ONE)
    public void celE2Compiled(Data d, CelCompiled c, Blackhole bh) {
        int record = d.sample[ringPosition(c.startOffset, c.ring)];
        c.ring = (c.ring + 1) & (E2_RING - 1);
        c.invocations++;
        bh.consume(c.e2.evaluate(record));
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK)
    public void celE1Placeholder(Data d, Cel c, Blackhole bh) {
        int[] sample = d.sample;
        int cur = c.cursor;
        for (int b = 0; b < BLOCK; b++) {
            bh.consume(c.e1Ph.evaluate(sample[cur]));
            cur = (cur + STRIDE) & (SAMPLE - 1);
        }
        c.cursor = cur;
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK_ONE)
    public void celE2Placeholder(Data d, Cel c, Blackhole bh) {
        int cur = c.cursor;
        int record = d.sample[cur];
        c.cursor = (cur + STRIDE) & (SAMPLE - 1);
        c.log(record);
        bh.consume(c.e2Ph.evaluate(record));
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK)
    public void nativeE1Cr(Data d, Nat n, Blackhole bh) {
        nativeE1(d, n, n.cr, bh);
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK)
    public void nativeE1Strict(Data d, Nat n, Blackhole bh) {
        nativeE1(d, n, n.strict, bh);
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK)
    public void nativeE2Cr(Data d, Nat n, Blackhole bh) {
        nativeE2(d, n, n.cr, bh);
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK)
    public void nativeE2Strict(Data d, Nat n, Blackhole bh) {
        nativeE2(d, n, n.strict, bh);
    }

    /** The matched pair's native side: the ring {@code celE2} times, one record per invocation, same order. */
    @Benchmark
    @OperationsPerInvocation(BLOCK_ONE)
    public void nativeE2CrMatched(Data d, Nat n, Blackhole bh) {
        int i = d.sample[ringPosition(n.startOffset, n.ring)];
        n.ring = (n.ring + 1) & (E2_RING - 1);
        n.invocations++;
        n.cr.e2(d.cols.range[i], d.cols.azimuth[i], d.cols.elevation[i], n.out);
        bh.consume(n.out[0]);
        bh.consume(n.out[1]);
    }

    private static void nativeE1(Data d, Nat n, NativeBaselines nb, Blackhole bh) {
        float[] r = d.cols.range;
        float[] a = d.cols.azimuth;
        float[] e = d.cols.elevation;
        int[] sample = d.sample;
        double[] out = n.out;
        int cur = n.cursor;
        for (int b = 0; b < BLOCK; b++) {
            int i = sample[cur];
            cur = (cur + STRIDE) & (SAMPLE - 1);
            nb.e1(r[i], a[i], e[i], out);
            bh.consume(out[0]);
            bh.consume(out[1]);
            bh.consume(out[2]);
        }
        n.cursor = cur;
    }

    private static void nativeE2(Data d, Nat n, NativeBaselines nb, Blackhole bh) {
        float[] r = d.cols.range;
        float[] a = d.cols.azimuth;
        float[] e = d.cols.elevation;
        int[] sample = d.sample;
        double[] out = n.out;
        int cur = n.cursor;
        for (int b = 0; b < BLOCK; b++) {
            int i = sample[cur];
            cur = (cur + STRIDE) & (SAMPLE - 1);
            nb.e2(r[i], a[i], e[i], out);
            bh.consume(out[0]);
            bh.consume(out[1]);
        }
        n.cursor = cur;
    }
}
