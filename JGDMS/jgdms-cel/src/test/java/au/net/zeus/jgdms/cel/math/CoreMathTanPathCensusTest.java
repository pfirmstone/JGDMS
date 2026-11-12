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
 * WITHOUT WARRANTY OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.cel.math;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/**
 * Where the accurate path of the tan port runs: for every binade of {@code |x|} from 2^-26 to 2^1023, 10,000 seeded random doubles (seed 20261500 + the exponent), the number the fast path
 * cannot decide. No oracle: only {@link CoreMathTan#fast} (NaN = undecided) is called, so the whole census (10.5 million calls) takes seconds. Printed per binade for 2^-26..2^12 and aggregated
 * for the rest, plus the grand total, so that Chisel's timing can time the populations that matter.
 *
 * <h2>SEALED PREDICTIONS (B2-C1, phase B2; written before the first run of this class)</h2>
 * The fast path's error bound is 2^-65.864 of the result, against a spacing of the doubles of 2^-52.5 of it, so the chance that the rounding is undecided is about
 * 2 * 2^-65.864 / 2^-52.5 = 2^-12.4, 0.019%. Population: 10,000 random doubles per binade; scoring: the share of inputs the fast path returns NaN for, per group of binades. Prediction: in each
 * of the groups [-26, -1] (260,000 inputs), [0, 12] (130,000), [13, 52] (400,000), [53, 200] (1,480,000) and [201, 1023] (8,230,000) the undecided share lies between 0.005% and 0.06%. ELSE-BRANCH: a
 * share outside the interval means the undecided mechanism is not the one reasoned here (for example a second source of declines such as the {@code h < 2^-37} test, which should only
 * matter near multiples of pi/2); a speed finding for Chisel's timing, not a correctness one.
 */
class CoreMathTanPathCensusTest {

    private static final int PER_BINADE = 10_000;

    private static long undecidedInBinade(int e) {
        SplittableRandom r = new SplittableRandom(20261500L + e);
        long n = 0;
        for (int i = 0; i < PER_BINADE; i++) {
            long frac = r.nextLong() & 0xfffffffffffffL;
            long sign = r.nextBoolean() ? Long.MIN_VALUE : 0L;
            double x = Double.longBitsToDouble(sign | ((long) (0x3ff + e) << 52) | frac);
            if (!CoreMathTanTestSupport.PATH_DOMAIN.test(x)) {
                continue;
            }
            if (Double.isNaN(CoreMathTan.fast(x))) {
                n++;
            }
        }
        return n;
    }

    @Test
    void whereTheAccuratePathRuns() {
        int lo = -26;
        int hi = 1023;
        long[] per = new long[hi - lo + 1];
        IntStream.rangeClosed(lo, hi).parallel().forEach(e -> per[e - lo] = undecidedInBinade(e));
        StringBuilder sb = new StringBuilder(2000);
        sb.append("tan fast path undecided of ").append(PER_BINADE).append(" per binade: ");
        for (int e = lo; e <= 12; e++) {
            sb.append("2^").append(e).append(':').append(per[e - lo]).append(' ');
        }
        System.out.println(sb);
        int[][] groups = {{-26, -1}, {0, 12}, {13, 52}, {53, 200}, {201, 1023}};
        LongAdder total = new LongAdder();
        for (int[] g : groups) {
            long u = 0;
            long inputs = 0;
            for (int e = g[0]; e <= g[1]; e++) {
                u += per[e - lo];
                inputs += PER_BINADE;
            }
            total.add(u);
            double pct = 100.0 * u / inputs;
            System.out.println(new StringBuilder(120).append("tan census group 2^").append(g[0]).append("..2^").append(g[1]).append(": undecided ").append(u).append(" of ").append(inputs).append(" (")
                    .append(String.format(java.util.Locale.ROOT, "%.4f", pct)).append("%)"));
            assertTrue(pct >= 0.005 && pct <= 0.06, new StringBuilder(80).append("B2-C1: group ").append(g[0]).append("..").append(g[1]).append(" undecided share ").append(pct).append('%').toString());
        }
        System.out.println(new StringBuilder(100).append("tan census total: undecided ").append(total.sum()).append(" of ").append((long) PER_BINADE * per.length).append(" inputs"));
    }
}
