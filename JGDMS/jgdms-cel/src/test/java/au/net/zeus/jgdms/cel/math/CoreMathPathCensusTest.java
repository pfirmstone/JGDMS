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

package au.net.zeus.jgdms.cel.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import java.util.function.DoubleUnaryOperator;

import org.junit.jupiter.api.Test;

/**
 * How often the fast path of the CORE-MATH sin and cos cannot decide the rounding, by binade of
 * {@code |x|}, and therefore which inputs run the accurate path. This is a MEASUREMENT added after
 * the first run of the sealed tests (declared deviation D2 in the READY file, not part of the
 * sealed predictions): the first run showed sin's fast path undecided for 29.9% of 10,000 inputs
 * with exponent 2^-30..2^33, against 0.6% for cos on the same inputs, and the sealed prediction P8
 * (at most 2%) failed for sin. The explanation the C source suggests (HYPOTHESIS until this
 * table): the fast path's error bound is ABSOLUTE ({@code eps = 0x1.dep-64}), so for small
 * {@code |x|}, where an ulp of {@code sin(x)} is below that bound, it can never decide and CORE-MATH
 * sends such inputs to its cheap {@code sin_small_accurate} (a relative-error polynomial), which
 * is the designed behaviour and not a defect of the port. MEASURED by this test on 2026-11-12 (first
 * run, 2,000 inputs per binade): sin undecided for every input in the binades 2^-26..2^-11, 1820 of
 * 2000 at 2^-10, then roughly halving per binade (894, 466, 226, 107, 60, 23, 13, 5, 5 at
 * 2^-9..2^-1) and 2-26 of 2000 per binade from 2^0 to 2^32; cos undecided for 1-9 of 2000 in every
 * binade of 2^-27..2^-1 and 2-46 of 2000 from 2^0 up (HYPOTHESIS for the highest counts, at 2^0 and 2^2: cos(x)
 * near its zeros has a smaller ulp, so the absolute error bound is relatively larger there).
 * <p>
 * Population: per binade 2^e (e = -30..32), 2,000 seeded random doubles with a random sign and a
 * random fraction, plus 20,000 with the exponent field uniform over [2^31, MAX]. Scoring: the
 * count of {@code fast(x)} that is NaN (undecided). No oracle is needed, so this is quick.
 * Runtime tier: FAST (default suite).
 */
class CoreMathPathCensusTest {

    private static final int PER_BINADE = 2000;

    private static String cat(Object... parts) {
        StringBuilder sb = new StringBuilder(96);
        for (Object p : parts) {
            sb.append(p);
        }
        return sb.toString();
    }

    private static double[] binade(long seed, int e, int n) {
        SplittableRandom r = new SplittableRandom(seed + e);
        double[] xs = new double[n];
        for (int i = 0; i < n; i++) {
            long sign = r.nextBoolean() ? Long.MIN_VALUE : 0L;
            xs[i] = Double.longBitsToDouble(sign | ((long) (0x3ff + e) << 52) | (r.nextLong() & 0xfffffffffffffL));
        }
        return xs;
    }

    private static int undecided(double[] xs, DoubleUnaryOperator fast) {
        int u = 0;
        for (double x : xs) {
            if (Double.isNaN(fast.applyAsDouble(x))) {
                u++;
            }
        }
        return u;
    }

    @Test
    void undecidedFractionByBinade() {
        long[] sinU = new long[64];
        long[] cosU = new long[64];
        StringBuilder table = new StringBuilder(4096);
        table.append("fast-path census, 2000 random doubles per binade 2^e (undecided = fast path returned NaN)\n");
        table.append("   e   sin undecided   cos undecided\n");
        for (int e = -30; e <= 32; e++) {
            double[] xs = binade(20261115L, e, PER_BINADE);
            int idx = e + 30;
            // only inputs in each function's fast-path domain are counted: sin |x| >= 2^-26, cos |x| >= 2^-27
            sinU[idx] = (e >= -26) ? undecided(xs, CoreMathSin::fast) : -1;
            cosU[idx] = (e >= -27) ? undecided(xs, CoreMathCos::fast) : -1;
            table.append(String.format("%4d %8s/%d %8s/%d%n", e, sinU[idx] < 0 ? "n/a" : Long.toString(sinU[idx]), PER_BINADE,
                    cosU[idx] < 0 ? "n/a" : Long.toString(cosU[idx]), PER_BINADE));
        }
        // the large-argument fast path
        SplittableRandom r = new SplittableRandom(20261116L);
        double[] big = new double[20_000];
        for (int i = 0; i < big.length; i++) {
            long sign = r.nextBoolean() ? Long.MIN_VALUE : 0L;
            big[i] = Double.longBitsToDouble(sign | ((long) (0x3ff + 31 + r.nextInt(2046 - (0x3ff + 31) + 1)) << 52) | (r.nextLong() & 0xfffffffffffffL));
        }
        int bigSin = undecided(big, CoreMathSin::fast);
        int bigCos = undecided(big, CoreMathCos::fast);
        table.append(String.format(" >=31 %8d/%d %8d/%d  (exponent field uniform over 2^31..MAX)%n", bigSin, big.length, bigCos, big.length));
        System.out.println(table);

        // Guards, set from the measurement of 2026-11-12 (this table's first run) and the model above:
        // sin cannot decide below about 2^-11 (all inputs of each binade e <= -12 must be undecided), and
        // from 2^0 up both functions decide all but a small fraction.
        for (int e = -26; e <= -12; e++) {
            assertEquals(PER_BINADE, sinU[e + 30], cat("sin: every input of binade 2^", e, " is expected undecided by the absolute error bound"));
        }
        for (int e = 0; e <= 32; e++) {
            assertTrue(sinU[e + 30] < PER_BINADE * 0.03, cat("sin: more than 3% undecided in binade 2^", e, ": ", sinU[e + 30]));
            assertTrue(cosU[e + 30] < PER_BINADE * 0.03, cat("cos: more than 3% undecided in binade 2^", e, ": ", cosU[e + 30]));
        }
        for (int e = -27; e < 0; e++) {
            assertTrue(cosU[e + 30] < PER_BINADE * 0.03, cat("cos: more than 3% undecided in binade 2^", e, ": ", cosU[e + 30]));
        }
        assertTrue(bigSin < big.length * 0.03 && bigCos < big.length * 0.03, "large-argument fast path undecided more than 3%");
    }
}
