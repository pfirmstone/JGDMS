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
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * STD-011 stage 1: what one scan record costs the production CEL evaluator, against native code doing the same
 * arithmetic. THE OPERATION IS ONE RECORD (JMH divides each invocation's time by its {@code @OperationsPerInvocation}).
 * Records come from a fixed set of {@value #SAMPLE} in-front records spread over the real scan ({@link Sampling}),
 * cycled in file order, so the cost of a correctly-rounded call (which varies with its argument) is averaged over real
 * data rather than one record.
 * <ul>
 *   <li>{@code celE1}, {@code celE2}: the production {@code Evaluator} with the correctly-rounded provider
 *       ({@code MathProviders.correctlyRounded()}); the projection's per-record {@code set} is INSIDE the timed path.
 *       E2 is the tree STD-011 v0.1 would force (every reuse written out) and is REJECTED by the v0.1 ceilings: it is
 *       measured anyway, as the brief requires.</li>
 *   <li>{@code nativeE1Cr}, {@code nativeE2Cr}: native arithmetic with the same correctly-rounded functions (B-CR).
 *       {@code nativeE1Strict}, {@code nativeE2Strict}: with {@code StrictMath} (B-STRICT).</li>
 *   <li>{@code projectionOnly}: {@code set} plus the three {@code fieldValue} reads, no arithmetic.</li>
 *   <li>EXTRA, beyond the brief and labelled so: {@code celE1Placeholder}, {@code celE2Placeholder} run the SAME evaluator
 *       and trees with the production's StrictMath-backed test placeholder instead of the correctly-rounded provider, so
 *       that the tree-walking overhead can be separated from the cost of correct rounding. They are NOT conformant
 *       STD-011 evaluations.</li>
 * </ul>
 * Block sizes differ ({@code @OperationsPerInvocation}) only so that one invocation lasts long enough to time: the
 * written-out E2 under correct rounding makes on the order of a hundred thousand correctly-rounded calls per record.
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
    static final int BLOCK = 64;
    static final int BLOCK_E1_CR = 8;
    static final int BLOCK_ONE = 1;

    /** The scan columns and the sample, loaded once per fork. Prints the census so a run records what it ran on. */
    @State(Scope.Benchmark)
    public static class Data {
        RawScanColumns cols;
        int[] sample;

        @Setup(Level.Trial)
        public void load() throws IOException {
            cols = RawScanColumns.load(Sampling.rawscanPath());
            if (cols.census.crcFailuresRecords() != 0 || cols.census.crcFailuresPage0() != 0) {
                throw new IllegalStateException("the scan has page CRC failures; refusing to time on it");
            }
            Stage1Expressions.Camera cam = Stage1Expressions.synthetic().camera();
            Sampling.Front front = Sampling.inFront(cols, new NativeBaselines(new Trig.Strict(), cam));
            sample = front.evenly(SAMPLE);
            System.err.println(cols.census.describe());
            System.err.println(new StringBuilder("in-front valid returns: ").append(front.indices().length).append(" of ")
                    .append(front.validReturns()).append(" valid returns of ").append(front.records())
                    .append(" records; cycled sample ").append(SAMPLE).toString());
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
        int cursor;

        @Setup(Level.Trial)
        public void build(Data data) throws CelParseException {
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
        }
    }

    /** Both native baselines and their output buffers. */
    @State(Scope.Thread)
    public static class Nat {
        NativeBaselines cr;
        NativeBaselines strict;
        double[] out = new double[3];
        int cursor;

        @Setup(Level.Trial)
        public void build() {
            Stage1Expressions.Camera cam = Stage1Expressions.synthetic().camera();
            CorrectlyRoundedMath crMath = MathProviders.correctlyRounded().get();
            cr = new NativeBaselines(crMath, cam);
            strict = new NativeBaselines(new Trig.Strict(), cam);
        }
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK)
    public void projectionOnly(Data d, Cel c, Blackhole bh) {
        int[] sample = d.sample;
        int cur = c.cursor;
        for (int b = 0; b < BLOCK; b++) {
            c.projection.set(sample[cur]);
            cur = (cur + 1) & (SAMPLE - 1);
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
            cur = (cur + 1) & (SAMPLE - 1);
        }
        c.cursor = cur;
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK_ONE)
    public void celE2(Data d, Cel c, Blackhole bh) {
        int cur = c.cursor;
        bh.consume(c.e2Cr.evaluate(d.sample[cur]));
        c.cursor = (cur + 1) & (SAMPLE - 1);
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK)
    public void celE1Placeholder(Data d, Cel c, Blackhole bh) {
        int[] sample = d.sample;
        int cur = c.cursor;
        for (int b = 0; b < BLOCK; b++) {
            bh.consume(c.e1Ph.evaluate(sample[cur]));
            cur = (cur + 1) & (SAMPLE - 1);
        }
        c.cursor = cur;
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK_ONE)
    public void celE2Placeholder(Data d, Cel c, Blackhole bh) {
        int cur = c.cursor;
        bh.consume(c.e2Ph.evaluate(d.sample[cur]));
        c.cursor = (cur + 1) & (SAMPLE - 1);
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

    private static void nativeE1(Data d, Nat n, NativeBaselines nb, Blackhole bh) {
        float[] r = d.cols.range;
        float[] a = d.cols.azimuth;
        float[] e = d.cols.elevation;
        int[] sample = d.sample;
        double[] out = n.out;
        int cur = n.cursor;
        for (int b = 0; b < BLOCK; b++) {
            int i = sample[cur];
            cur = (cur + 1) & (SAMPLE - 1);
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
            cur = (cur + 1) & (SAMPLE - 1);
            nb.e2(r[i], a[i], e[i], out);
            bh.consume(out[0]);
            bh.consume(out[1]);
        }
        n.cursor = cur;
    }
}
