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

/**
 * What runs once per fork, before any iteration, on the compiled evaluators the timing is about to use (design section 9 items
 * 12 and 15). It is called from {@link CelEvalBenchmark.CelCompiled}'s trial setup and not made a JMH state of its own: JMH hands a
 * state passed to another state's {@code @Setup} a SEPARATE instance (MEASURED in the smoke run: two {@code CelCompiled}
 * instances, the check on the first, the timing on the second), so a separate state would check a compiled evaluator that is
 * never timed.
 * <ol>
 *   <li>Bit identity, stopping on the first mismatch by raw bits: the walker against the TIMED compiled runner, E1 on every
 *       record of the sample, E2 on the {@value CelEvalBenchmark#E2_RING} ring records only (the walker's E2 over all 4,096 records
 *       takes about 150 s: design section 9 item 12, a reviewers' reading).</li>
 *   <li>Correctly-rounded calls per record, on SEPARATE counting instances (the provider is fixed at compile time) around the
 *       same correctly-rounded source: walker and compiled E1 on every sample record, E2 on the ring. The counts are PRINTED
 *       with their verdict and do not stop the fork: the gate is "compiled E1 = 4 and E2 = 6 on every record", and a count
 *       that misses is a result to report, not a reason to lose the timing run.</li>
 * </ol>
 */
final class CompiledSelfCheck {

    static final int EXPECT_E1_COMPILED = 4;
    static final int EXPECT_E2_COMPILED = 6;

    private CompiledSelfCheck() {
    }

    static void run(CelEvalBenchmark.Data data, CelEvalBenchmark.CelCompiled compiled, String benchmark)
            throws CelParseException {
        Stage1Expressions x = Stage1Expressions.synthetic();
        ExprNode e1Tree = x.e1();
        ExprNode e2Tree = x.e2();
        RecordProjection projection = new RecordProjection(data.cols.range, data.cols.azimuth, data.cols.elevation);
        MathProvider cr = MathProviders.correctlyRounded();
        CelRunner walkE1 = new CelRunner(e1Tree, cr, projection);
        CelRunner walkE2 = new CelRunner(e2Tree, cr, projection);
        int[] ringSample = new int[CelEvalBenchmark.E2_RING];
        for (int r = 0; r < ringSample.length; r++) {
            ringSample[r] = data.sample[CelEvalBenchmark.ringPosition(data.startOffset, r)];
        }
        long t0 = System.nanoTime();
        long e1Values = compareBits("E1", data.sample, walkE1, compiled.e1, 3);
        long e2Values = compareBits("E2", ringSample, walkE2, compiled.e2, 2);
        long t1 = System.nanoTime();
        System.out.println(new StringBuilder("SELFCHECK benchmark=").append(benchmark).append(" startOffset=")
                .append(data.startOffset).append(' ').append(compiled.tag()).append(": walker vs compiled, raw bits: E1 ")
                .append(data.sample.length).append(" records ").append(e1Values).append(" values, E2 ring of ")
                .append(ringSample.length).append(" records ").append(e2Values)
                .append(" values: 0 mismatches (a mismatch stops the fork); ").append((t1 - t0) / 1_000_000L).append(" ms")
                .toString());

        CorrectlyRoundedMath source = MathProviders.correctlyRounded().get();
        Trig.Counting wc1 = new Trig.Counting(source);
        Trig.Counting cc1 = new Trig.Counting(source);
        Trig.Counting wc2 = new Trig.Counting(source);
        Trig.Counting cc2 = new Trig.Counting(source);
        long[] w1 = countPerRecord(data.sample, wc1, new CelRunner(e1Tree, MathProvider.conformant(wc1), projection)::evaluateInto, 3);
        long[] c1 = countPerRecord(data.sample, cc1, new CompiledRunner(e1Tree, MathProvider.conformant(cc1), projection)::evaluateInto, 3);
        long[] w2 = countPerRecord(ringSample, wc2, new CelRunner(e2Tree, MathProvider.conformant(wc2), projection)::evaluateInto, 2);
        long[] c2 = countPerRecord(ringSample, cc2, new CompiledRunner(e2Tree, MathProvider.conformant(cc2), projection)::evaluateInto, 2);
        boolean e1Holds = c1[0] == EXPECT_E1_COMPILED && c1[1] == EXPECT_E1_COMPILED;
        boolean e2Holds = c2[0] == EXPECT_E2_COMPILED && c2[1] == EXPECT_E2_COMPILED;
        System.out.println(new StringBuilder("CALLCOUNT benchmark=").append(benchmark).append(" startOffset=")
                .append(data.startOffset).append(": correctly-rounded calls per record, min..max. E1 over ")
                .append(data.sample.length).append(" sample records: walker ").append(w1[0]).append("..").append(w1[1])
                .append(" (asserted 5), compiled ").append(c1[0]).append("..").append(c1[1]).append(" (gate = ")
                .append(EXPECT_E1_COMPILED).append("): ").append(e1Holds ? "HOLDS" : "FAILS")
                .append(". E2 over the ring of ").append(ringSample.length).append(": walker ").append(w2[0]).append("..")
                .append(w2[1]).append(" (asserted 156553), compiled ").append(c2[0]).append("..").append(c2[1])
                .append(" (gate = ").append(EXPECT_E2_COMPILED).append("): ").append(e2Holds ? "HOLDS" : "FAILS").toString());
    }

    /** Walker against compiled, value by value, by raw bits; throws on the first difference. Returns the values compared. */
    private static long compareBits(String what, int[] records, CelRunner walker, CompiledRunner compiled, int width) {
        double[] a = new double[width];
        double[] b = new double[width];
        long values = 0;
        for (int i : records) {
            walker.evaluateInto(i, a);
            compiled.evaluateInto(i, b);
            for (int j = 0; j < width; j++) {
                if (Double.doubleToRawLongBits(a[j]) != Double.doubleToRawLongBits(b[j])) {
                    throw new IllegalStateException(new StringBuilder("self-check ").append(what).append(" record ").append(i)
                            .append(" value ").append(j).append(": walker ").append(a[j]).append(" (")
                            .append(Long.toHexString(Double.doubleToRawLongBits(a[j]))).append(") compiled ").append(b[j])
                            .append(" (").append(Long.toHexString(Double.doubleToRawLongBits(b[j]))).append(')').toString());
                }
                values++;
            }
        }
        return values;
    }

    /** One evaluation per record on an instance whose provider is {@code counter}; returns {min, max} of the per-record call counts. */
    private static long[] countPerRecord(int[] records, Trig.Counting counter, RecordSink sink, int width) {
        double[] out = new double[width];
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (int i : records) {
            long before = counter.total();
            sink.evaluateInto(i, out);
            long calls = counter.total() - before;
            min = Math.min(min, calls);
            max = Math.max(max, calls);
        }
        return new long[] {min, max};
    }

    /** The one method the walker's and the compiled runner share. */
    @FunctionalInterface
    private interface RecordSink {
        void evaluateInto(int i, double[] out);
    }
}
