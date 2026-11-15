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
 * WITHOUT WARRANTY OF ANY KIND, EXPRESS or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.cel.math;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.SplittableRandom;
import java.util.function.DoubleUnaryOperator;

import org.junit.jupiter.api.Test;

/**
 * How often the fast path of each ported inverse trigonometric function cannot decide the rounding, by binade of the argument (atan, asin,
 * acos) or by binade of the ratio {@code |y/x|} (atan2), and therefore which inputs run the accurate path. A MEASUREMENT (the "path
 * census" of the brief, after {@code CoreMathPathCensusTest}): the tables are printed, and only two structural facts that follow from
 * the C text are asserted, no threshold fitted to a result. Runtime tier: FAST (default suite).
 * <p>
 * Population: per binade 2^e, 2,000 seeded random doubles (seed 20261230 + e, random sign, random fraction) for atan with
 * e = -30..60, for asin and acos with e = -30..-1 and in addition 2,000 near +-1 ({@code |x| = 1 - k*2^-53}); for atan2, per ratio
 * binade r = -64..16, 2,000 pairs with {@code |y| = |x| * 2^r * (1 + f)} and {@code |x|} in 2^-40..2^40 (seed 20261231 + r), random signs.
 * Scoring: the count of {@code fast(x)} that is NaN (undecided); for atan2 the first stage alone, both stages, and the accurate path needed.
 * <p>
 * Asserted: (1) atan for {@code |x| >= 0x1.d02967c31cdb5p+53} is answered by an early exit of the C, never undecided; for
 * {@code |x| < 2^-27} likewise (the C returns {@code fma(-2^-54, x, x)}); (2) atan2 pairs whose exponent fields differ by 53 or more
 * are never decided by the fast path (the C sends them to {@code atan2_accurate}), so the accurate path is needed for every one of them.
 */
class CoreMathInverseTrigPathCensusTest {

    private static final int PER_BINADE = 2000;

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
    void undecidedFractionByBinadeForAtanAsinAcos() {
        StringBuilder table = new StringBuilder(8192);
        table.append("fast-path census (undecided = fast path returned NaN), 2000 random doubles per binade 2^e\n");
        table.append("   e   atan undecided   asin undecided   acos undecided\n");
        long atanEarlyUndecided = 0;
        for (int e = -30; e <= 60; e++) {
            double[] xs = binade(20261230L, e, PER_BINADE);
            int a = undecided(xs, CoreMathAtan::fast);
            if (e < -27 || e >= 54) {
                atanEarlyUndecided += a;
            }
            String s = "n/a";
            String c = "n/a";
            if (e <= -1) {
                s = new StringBuilder(8).append(undecided(xs, CoreMathAsin::fast)).toString();
                c = new StringBuilder(8).append(undecided(xs, CoreMathAcos::fast)).toString();
            }
            table.append(String.format("%4d %9d/%d %10s/%d %10s/%d%n", e, a, PER_BINADE, s, PER_BINADE, c, PER_BINADE));
        }
        double[] near = CoreMathInverseTrigTestSupport.nearOne(20261230L, PER_BINADE);
        table.append(String.format(" 1-k*2^-53 (k up to 2^52): asin %d/%d, acos %d/%d%n", undecided(near, CoreMathAsin::fast), PER_BINADE, undecided(near, CoreMathAcos::fast), PER_BINADE));
        System.out.println(table);
        assertEquals(0L, atanEarlyUndecided, "atan: an input answered by an early exit of the C was reported undecided");
    }

    @Test
    void atan2StagesByRatioBinade() {
        StringBuilder table = new StringBuilder(8192);
        table.append("atan2 census, 2000 random pairs per ratio binade r (|y/x| in [2^r, 2^(r+1))), |x| in 2^-40..2^40\n");
        table.append("   r   first stage decided   both stages decided   accurate path needed\n");
        long gapPairs = 0;
        long gapDecided = 0;
        for (int r = -64; r <= 16; r++) {
            SplittableRandom rnd = new SplittableRandom(20261231L + r);
            double[] ys = new double[PER_BINADE];
            double[] xs = new double[PER_BINADE];
            for (int i = 0; i < PER_BINADE; i++) {
                int ex = 0x3ff - 40 + rnd.nextInt(81);
                double x = Double.longBitsToDouble((rnd.nextBoolean() ? Long.MIN_VALUE : 0L) | ((long) ex << 52) | (rnd.nextLong() & 0xfffffffffffffL));
                double y = Math.scalb(Math.abs(x) * (1.0 + rnd.nextDouble()), r - 1) * (rnd.nextBoolean() ? 1.0 : -1.0);
                xs[i] = x;
                ys[i] = y;
            }
            int stage1 = 0;
            int both = 0;
            for (int i = 0; i < PER_BINADE; i++) {
                if (!Double.isNaN(CoreMathAtan2.fast(ys[i], xs[i], false))) {
                    stage1++;
                }
                double f = CoreMathAtan2.fast(ys[i], xs[i], true);
                if (!Double.isNaN(f)) {
                    both++;
                }
                // the C's gap test, on the exponent fields of the two arguments
                long aiy = Double.doubleToRawLongBits(ys[i]) & 0x7fffffffffffffffL;
                long aix = Double.doubleToRawLongBits(xs[i]) & 0x7fffffffffffffffL;
                long gt = aix < aiy ? 1L : 0L;
                long dxy = (aix - aiy) ^ -gt;
                if (Long.compareUnsigned(dxy, 53L << 52) >= 0) {
                    gapPairs++;
                    if (!Double.isNaN(f)) {
                        gapDecided++;
                    }
                }
            }
            table.append(String.format("%4d %10d/%d %12d/%d %14d/%d%n", r, stage1, PER_BINADE, both, PER_BINADE, PER_BINADE - both, PER_BINADE));
        }
        System.out.println(table);
        System.out.println(new StringBuilder(120).append("atan2 pairs with exponent fields 53 or more binades apart: ").append(gapPairs).append(", decided by the fast path: ").append(gapDecided));
        assertEquals(0L, gapDecided, "atan2: a pair 53 or more binades apart was decided by the fast path; the C sends it to atan2_accurate");
    }
}
