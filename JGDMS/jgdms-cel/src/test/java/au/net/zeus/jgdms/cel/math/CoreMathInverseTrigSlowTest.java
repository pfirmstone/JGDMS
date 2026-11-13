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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The slow tier of phase B1: the CORE-MATH ports of binary64 {@code atan}, {@code asin}, {@code acos} and {@code atan2} against
 * {@link CrMath} on EVERY finite in-domain entry of CORE-MATH's {@code atan.wc}, {@code asin.wc}, {@code acos.wc} and
 * {@code atan2.wc}, and on large seeded random populations: atan 1,000,000 over the full finite range and 5,000,000 over
 * 2^-30..2^58; asin and acos 1,000,000 each of three populations (uniform in [-1,1), exponent 2^-30..2^-1, near +-1); atan2 5,000,000
 * pairs with exponent fields within 60 of each other, 1,000,000 with independent exponent fields over 0..2046 and 500,000 over 0..80.
 * Because CORE-MATH publishes no proof for atan and atan2, their random populations are larger than sine's (5,000,000 against
 * 1,000,000). Excluded from the default suite ({@code @Tag("slow")}); run with
 * {@code -Dgroups=slow -Djgdms.cel.excludedGroups=none-such -Dcore.math.full=true}. The oracle runs on all cores ({@code CrMath} is
 * thread-safe for results, see {@link CoreMathTestSupport}).
 * <p>
 * Predictions are B1-P13 of {@code CoreMathInverseTrigDifferentialTest}'s javadoc, sealed before the first run: 0 mismatches in every
 * population, 0 correct-rounding failures of the atan2 accurate path. Each population is run through the provider, the accurate path
 * alone and the fast path alone, so the accurate path is covered on all of them and not only where the fast path gives up. The comparison,
 * the populations and the control are the code of the fast tier ({@link CoreMathInverseTrigTestSupport}, {@link CoreMathTestSupport}).
 * <p>
 * <b>This tier is part of the correctness argument, not an optional extra.</b> The ports are of CORE-MATH code whose correctness
 * rests on an error analysis (published for asin and acos only) and on a worst-case search whose output is the {@code .wc} files;
 * for atan2 the search is "restricted" (CORE-MATH's own wording). Re-run this tier after ANY change to a port, the tables, the
 * helpers, or the JDK it runs on (the result so far is for the DirtyChai 27-internal build), and keep the result with the commit.
 * Without the CORE-MATH clone the {@code .wc} tests are skipped, and FAIL when the full tier is requested with
 * {@code -Dcore.math.full=true}; the random populations do not need the clone.
 */
@Tag("slow")
class CoreMathInverseTrigSlowTest {

    /** Populations larger than this are run in chunks so that inputs and expected outputs are never all in memory at once. */
    private static final int CHUNK = 1_000_000;

    private static void checkChunked(String function, String population, java.util.function.IntFunction<double[]> chunkInputs, int total) {
        long t0 = System.nanoTime();
        long undecided = 0;
        long done = 0;
        int chunk = 0;
        while (done < total) {
            int n = (int) Math.min(CHUNK, total - done);
            double[] xs = chunkInputs.apply(chunk);
            assertEquals(n, xs.length);
            double[] expected = CoreMathTestSupport.oracleValues(xs, CoreMathInverseTrigTestSupport.oracle(function));
            undecided += CoreMathInverseTrigTestSupport.checkUnary(function, new StringBuilder(96).append(population).append(", chunk ").append(chunk).toString(), xs, expected);
            done += n;
            chunk++;
        }
        System.out.println(new StringBuilder(160).append("timing {").append(function).append(", ").append(population).append("}: ").append(total).append(" inputs in ")
                .append((System.nanoTime() - t0) / 1_000_000L).append(" ms (oracle and ports); fast path undecided on ").append(undecided).append(" in-domain inputs"));
    }

    private static void checkPairsChunked(String population, java.util.function.IntFunction<CoreMathInverseTrigTestSupport.Pairs> chunkInputs, int total) {
        long t0 = System.nanoTime();
        long accurate = 0;
        long failures = 0;
        long routed = 0;
        long done = 0;
        int chunk = 0;
        while (done < total) {
            int n = (int) Math.min(CHUNK, total - done);
            CoreMathInverseTrigTestSupport.Pairs p = chunkInputs.apply(chunk);
            assertEquals(n, p.ys.length);
            double[] expected = CoreMathInverseTrigTestSupport.oracleValues2(p.ys, p.xs, CoreMathTestSupport.ORACLE::atan2);
            CoreMathInverseTrigTestSupport.PairsReport r = CoreMathInverseTrigTestSupport.checkPairs(new StringBuilder(96).append(population).append(", chunk ").append(chunk).toString(), p.ys, p.xs, expected);
            accurate += r.accurateNeeded;
            failures += r.accurateFailures;
            routed += r.providerFailures;
            done += n;
            chunk++;
        }
        System.out.println(new StringBuilder(200).append("timing {atan2, ").append(population).append("}: ").append(total).append(" pairs in ").append((System.nanoTime() - t0) / 1_000_000L)
                .append(" ms (oracle and ports); accurate (192-bit) path needed for ").append(accurate).append("; correct-rounding failures ").append(failures)
                .append("; routed to CrMath by the provider ").append(routed));
        assertEquals(0L, failures, "correct-rounding failures of the atan2 accurate path");
        assertEquals(0L, routed, "atan2 calls routed to CrMath by the provider");
    }

    private static void wcCheck(String function) throws IOException {
        CoreMathTestSupport.requireClone(CoreMathTestSupport.CORE_MATH_DIR, CoreMathTestSupport.FULL_TIER, "the full .wc worst-case run", "atan", "asin", "acos", "atan2");
        if (function.equals("atan2")) {
            CoreMathInverseTrigTestSupport.WcPairs wc = CoreMathInverseTrigTestSupport.readWcPairs(CoreMathTestSupport.wcFile("atan2"), 0);
            System.out.println(new StringBuilder(160).append("atan2.wc: ").append(wc.totalLines).append(" lines, ").append(wc.commentLines).append(" comment lines, ").append(wc.nonFinite)
                    .append(" lines with a NaN/Inf skipped, ").append(wc.ys.length).append(" finite pairs compared"));
            assertEquals(wc.totalLines, wc.commentLines + wc.nonFinite + wc.ys.length, "every line is a comment, a non-finite line or a pair");
            assertTrue(wc.ys.length > 20_000, "the .wc reader lost entries");
            // pairs with a zero argument are in the file (special rows); the accurate and fast paths are defined for non-zero arguments only,
            // so they are compared through the provider, which routes zero rows to the C's special-value routine
            double[] expected = CoreMathInverseTrigTestSupport.oracleValues2(wc.ys, wc.xs, CoreMathTestSupport.ORACLE::atan2);
            int zeroRows = 0;
            java.util.List<Integer> keep = new java.util.ArrayList<>();
            for (int i = 0; i < wc.ys.length; i++) {
                if (wc.ys[i] == 0.0 || wc.xs[i] == 0.0) {
                    zeroRows++;
                } else {
                    keep.add(i);
                }
            }
            double[] ys = new double[keep.size()];
            double[] xs = new double[keep.size()];
            double[] ex = new double[keep.size()];
            for (int i = 0; i < ys.length; i++) {
                ys[i] = wc.ys[keep.get(i)];
                xs[i] = wc.xs[keep.get(i)];
                ex[i] = expected[keep.get(i)];
            }
            CoreMathTestSupport.Diff dz = CoreMathTestSupport.compare2("atan2, atan2.wc zero rows, provider path", wc.ys, wc.xs, expected, CoreMathTestSupport.PROVIDER::atan2, (y, x) -> true, false, true);
            System.out.println(new StringBuilder(120).append(dz.line()).append(" (all ").append(wc.ys.length).append(" pairs, of which ").append(zeroRows).append(" have a zero argument)"));
            assertEquals(0L, dz.mismatches, dz.details());
            CoreMathInverseTrigTestSupport.PairsReport r = CoreMathInverseTrigTestSupport.checkPairs("atan2.wc every finite pair with non-zero arguments", ys, xs, ex);
            assertEquals(0L, r.accurateFailures, "correct-rounding failures of the atan2 accurate path");
            assertEquals(0L, r.providerFailures);
            return;
        }
        CoreMathTestSupport.WcData wc = CoreMathTestSupport.readWc(CoreMathTestSupport.wcFile(function), 0);
        CoreMathInverseTrigTestSupport.WcUnary in = CoreMathInverseTrigTestSupport.readWcInDomain(function, 0);
        System.out.println(new StringBuilder(160).append(function).append(".wc: ").append(wc.totalLines).append(" lines, ").append(wc.commentLines).append(" comment lines, ")
                .append(wc.nonFinite).append(" NaN/Inf lines skipped, ").append(wc.values.length).append(" finite entries, of which ").append(in.outOfDomain)
                .append(" outside the provider's domain (not compared), ").append(in.values.length).append(" compared"));
        assertEquals(wc.totalLines, wc.commentLines + wc.nonFinite + wc.values.length, "every line is a comment, a NaN/Inf line or a value");
        assertEquals(wc.values.length, in.outOfDomain + in.values.length);
        assertTrue(in.values.length > 50_000, "the .wc reader lost entries");
        double[] expected = CoreMathTestSupport.oracleValues(in.values, CoreMathInverseTrigTestSupport.oracle(function));
        CoreMathInverseTrigTestSupport.checkUnary(function, new StringBuilder(48).append(function).append(".wc every finite in-domain entry").toString(), in.values, expected);
    }

    @Test
    void atanWorstCasesEveryEntry() throws IOException {
        wcCheck("atan");
    }

    @Test
    void asinWorstCasesEveryEntry() throws IOException {
        wcCheck("asin");
    }

    @Test
    void acosWorstCasesEveryEntry() throws IOException {
        wcCheck("acos");
    }

    @Test
    void atan2WorstCasesEveryPair() throws IOException {
        wcCheck("atan2");
    }

    @Test
    void atanRandomFiniteRangeOneMillion() {
        checkChunked("atan", "random exponent 0..2046, seed 20261221, 1000000", c -> CoreMathTestSupport.finiteRangeRandom(20261221L, 1_000_000), 1_000_000);
    }

    @Test
    void atanRandomModerateRangeFiveMillion() {
        checkChunked("atan", "random |x| 2^-30..2^58, seed 20261222+chunk, 5000000",
                c -> CoreMathInverseTrigTestSupport.binadeRange(20261222L + c, CHUNK, -30, 58), 5_000_000);
    }

    @Test
    void asinRandomOneMillionEach() {
        checkChunked("asin", "random uniform [-1,1), seed 20261223, 1000000", c -> CoreMathInverseTrigTestSupport.linearUniform(20261223L, 1_000_000), 1_000_000);
        checkChunked("asin", "random exponent 2^-30..2^-1, seed 20261224, 1000000", c -> CoreMathInverseTrigTestSupport.binadeRange(20261224L, 1_000_000, -30, -1), 1_000_000);
        checkChunked("asin", "random |x| = 1 - k*2^-53, seed 20261225, 1000000", c -> CoreMathInverseTrigTestSupport.nearOne(20261225L, 1_000_000), 1_000_000);
    }

    @Test
    void acosRandomOneMillionEach() {
        checkChunked("acos", "random uniform [-1,1), seed 20261223, 1000000", c -> CoreMathInverseTrigTestSupport.linearUniform(20261223L, 1_000_000), 1_000_000);
        checkChunked("acos", "random exponent 2^-30..2^-1, seed 20261224, 1000000", c -> CoreMathInverseTrigTestSupport.binadeRange(20261224L, 1_000_000, -30, -1), 1_000_000);
        checkChunked("acos", "random |x| = 1 - k*2^-53, seed 20261225, 1000000", c -> CoreMathInverseTrigTestSupport.nearOne(20261225L, 1_000_000), 1_000_000);
    }

    @Test
    void atan2RandomNearExponentsFiveMillionPairs() {
        checkPairsChunked("random pairs, exponent fields within 60, seed 20261226+chunk, 5000000", c -> CoreMathInverseTrigTestSupport.pairsNear(20261226L + c, CHUNK), 5_000_000);
    }

    @Test
    void atan2RandomIndependentExponentsOneMillionPairs() {
        checkPairsChunked("random pairs, independent exponent fields 0..2046, seed 20261227, 1000000",
                c -> CoreMathInverseTrigTestSupport.pairsIndependent(20261227L, CHUNK, 0, 2046), 1_000_000);
    }

    @Test
    void atan2RandomTinyHalfMillionPairs() {
        checkPairsChunked("random pairs, independent exponent fields 0..80, seed 20261228, 500000",
                c -> CoreMathInverseTrigTestSupport.pairsIndependent(20261228L, 500_000, 0, 80), 500_000);
    }
}
