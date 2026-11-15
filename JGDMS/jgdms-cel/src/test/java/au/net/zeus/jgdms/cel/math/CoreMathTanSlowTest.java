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
import java.util.function.IntFunction;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The slow tier of phase B2: the CORE-MATH port of binary64 {@code tan} against {@link CrMath} on EVERY finite entry of CORE-MATH's {@code tan.wc} (1,098,260 non-comment lines, of which the
 * NaN, infinity and signalling-NaN rows are skipped by the shared reader and counted), and on large seeded random populations: 2,000,000 over the full finite range, 1,500,000 over
 * 2^-28..2^4, 1,000,000 over 2^5..2^52, 1,000,000 over 2^52..2^1023, 1,000,000 uniform in [0, 8) (together 6,500,000 purely random inputs, more than the 5,000,000 the brief
 * requires because CORE-MATH publishes NO proof for tan), and 250,000 each of the structured populations near multiples of pi/2 (k of at most 52 bits, and of at most 1020 bits).
 * Excluded from the default suite ({@code @Tag("slow")}); run with {@code -Dgroups=slow -Djgdms.cel.excludedGroups=none-such -Dcore.math.full=true}.
 * <p>
 * FIX PASS (appended; the text above is the phase B2 seal): B2F-G6 (sealed in {@code CoreMathTanGuardTest}) adds, for every population, the number of production calls the tan guard
 * delegated to {@code CrMath} and the number of in-domain inputs on which it fires on the forced accurate path. Predicted: 0 delegations in every random population, at most 3 in each
 * near-pole population (about 0.05 expected), at most 5 over every {@code tan.wc} entry (about 0.6 expected); each delegated call still equals {@code CrMath} (the comparison tests).
 * The 500,000 near-pole inputs are about 259,000 near a pole and about 241,000 ordinary large arguments (Opus L2). The oracle runs on all cores
 * ({@code CrMath} is thread-safe for results, see {@link CoreMathTestSupport}).
 * <p>
 * FIX PASS 2 (appended 2026-11-12; the sealed text above stands as history, its bounds are superseded; operator decision "Narrow it": the guard's window is {@code 86 + K/R} after a fold and
 * {@code 86 + K} without). <b>SEALED PREDICTIONS B2F2-D, written before the first run:</b> the guard delegates NONE of the production calls of any population, {@code tan.wc} included (the first
 * window delegated 1,571 of its 1,098,254 entries, all unfolded tiny arguments); on the FORCED accurate path it fires on none of the in-domain inputs of any random or near-pole population
 * (the window after a fold is unchanged and the first window already fired on none of them) and on exactly ONE of the 1,098,245 in-domain {@code tan.wc} entries, the folded
 * {@code x = 0x1.92456c9a2452ep282} (Opus O8: 1 beside the two exceptions, which the table answers first; Fable M-1: 2,498 under the first window, 2,497 of them unfolded); 0 mismatches
 * everywhere as before.
 * <p>
 * Predictions are B2-P14 of {@code CoreMathTanDifferentialTest}'s javadoc, sealed before the first run: 0 mismatches in every population on the provider path, the accurate path alone and the fast
 * path alone; the hard-case census (the C's 86-ulp test) fires on no input other than the two exceptions of {@code tan_accurate}. Each population is run through the provider, the
 * accurate path alone and the fast path alone, so the accurate path is covered on all of them and not only where the fast path gives up. The comparison, the populations and the control are the
 * code of the fast tier ({@link CoreMathTanTestSupport}, {@link CoreMathTestSupport}).
 * <p>
 * <b>This tier is part of the correctness argument, not an optional extra.</b> CORE-MATH publishes no correctness proof for tan (homepage, read 2026-11-12), so the port's evidence is the C
 * authors' worst-case search ({@code tan.wc}) and these runs. Re-run this tier after ANY change to the port, the tables, the helpers, or the JDK it runs on (the result so far is for the
 * DirtyChai 27-internal build), and keep the result with the commit. Without the CORE-MATH clone the {@code .wc} test is skipped, and FAILS when the full tier is requested with
 * {@code -Dcore.math.full=true}; the random populations do not need the clone.
 */
@Tag("slow")
class CoreMathTanSlowTest {

    /** Populations larger than this are run in chunks so that inputs and expected outputs are never all in memory at once. */
    private static final int CHUNK = 1_000_000;

    private static void checkChunked(String population, IntFunction<double[]> chunkInputs, int total) {
        long t0 = System.nanoTime();
        long undecided = 0;
        long done = 0;
        long inDomain = 0;
        long ambiguous = 0;
        long notException = 0;
        long guardSeam = 0;
        long guardDelegations = 0;
        int chunk = 0;
        while (done < total) {
            int n = (int) Math.min(CHUNK, total - done);
            double[] xs = chunkInputs.apply(chunk);
            assertEquals(n, xs.length);
            double[] expected = CoreMathTanTestSupport.oracle(xs);
            undecided += CoreMathTanTestSupport.checkUnary(new StringBuilder(96).append(population).append(", chunk ").append(chunk).toString(), xs, expected);
            long[] c = CoreMathTanTestSupport.ambiguousCensus(xs);
            inDomain += c[0];
            ambiguous += c[1];
            notException += c[2];
            guardSeam += c[3];
            guardDelegations += productionGuardDelegations(xs);
            done += n;
            chunk++;
        }
        System.out.println(new StringBuilder(220).append("timing {tan, ").append(population).append("}: ").append(total).append(" inputs in ").append((System.nanoTime() - t0) / 1_000_000L)
                .append(" ms (oracle and ports); fast path undecided on ").append(undecided).append(" of ").append(inDomain).append(" in-domain inputs; 86-ulp test fired on ").append(ambiguous)
                .append(" (not an exception input: ").append(notException).append("); guard: fires on ").append(guardSeam).append(" of ").append(inDomain)
                .append(" in-domain inputs on the forced accurate path, delegated ").append(guardDelegations).append(" of ").append(total).append(" production calls"));
        assertEquals(0L, notException, "the C's 86-ulp hard-case test fired on an input that is not one of the two exceptions");
        assertEquals(0L, guardDelegations, CoreMathTestSupport.msg("B2F2-D: guard delegations ", guardDelegations, " of ", total, " calls in ", population));
        assertEquals(0L, guardSeam, CoreMathTestSupport.msg("B2F2-D: guard fires on the forced accurate path on ", guardSeam, " inputs of ", population));
    }

    /** How many calls of the production provider over {@code xs} were answered by the delegate because the guard fired (every one must equal CrMath: the comparison tests do that). */
    private static long productionGuardDelegations(double[] xs) {
        CountingDelegate delegate = new CountingDelegate();
        CoreMath provider = new CoreMath(delegate);
        java.util.stream.IntStream.range(0, xs.length).parallel().forEach(j -> provider.tan(xs[j]));
        assertEquals(provider.tanGuardDelegations(), delegate.tan.sum());
        return provider.tanGuardDelegations();
    }

    @Test
    void wcEveryFiniteEntry() throws IOException {
        CoreMathTestSupport.requireClone(CoreMathTestSupport.CORE_MATH_DIR, CoreMathTestSupport.FULL_TIER, "the full tan.wc worst-case run", "tan");
        CoreMathTestSupport.WcData wc = CoreMathTestSupport.readWc(CoreMathTestSupport.wcFile("tan"), 0);
        System.out.println(new StringBuilder(160).append("tan.wc: ").append(wc.totalLines).append(" lines, ").append(wc.commentLines).append(" comment lines, ").append(wc.nonFinite)
                .append(" lines with a NaN/Inf skipped, ").append(wc.values.length).append(" finite entries compared"));
        assertEquals(wc.totalLines, wc.commentLines + wc.nonFinite + wc.values.length, "every line is a comment, a non-finite line or an entry");
        assertTrue(wc.values.length > 1_000_000, "the .wc reader lost entries");
        double[] expected = CoreMathTanTestSupport.oracle(wc.values);
        CoreMathTanTestSupport.checkUnary("tan.wc every finite entry", wc.values, expected);
        long guardDelegationsWc = productionGuardDelegations(wc.values);
        long[] gc = CoreMathTanTestSupport.ambiguousCensus(wc.values);
        System.out.println(new StringBuilder(200).append("tan.wc guard: delegated ").append(guardDelegationsWc).append(" of ").append(wc.values.length).append(" production calls; fires on ").append(gc[3])
                .append(" of ").append(gc[0]).append(" in-domain entries on the forced accurate path"));
        // B2F2-D (fix pass 2, narrowed window): no production delegation, and ONE forced-path firing, the folded x = 0x1.92456c9a2452ep282 (the fast path decides it, so production does not
        // delegate it). History: the first window (86 + K/R on every input) delegated 1,571 entries (B2F-G6 had sealed at most 5: deviation FD2) and fired on 2,498 on the forced path; 2,497 of those were
        // unfolded tiny arguments on which the fold gap does not exist (re-check reviews of 22a20ad5a). The counts are deterministic (fixed file, fixed code): asserted exactly.
        assertEquals(0L, guardDelegationsWc, "B2F2-D: guard delegations over tan.wc");
        assertEquals(1L, gc[3], "B2F2-D: guard firings on the forced accurate path over tan.wc");
        long[] c = CoreMathTanTestSupport.ambiguousCensus(wc.values);
        System.out.println(new StringBuilder(160).append("tan.wc hard-case census: ").append(c[1]).append(" of ").append(c[0]).append(" in-domain entries reach the 86-ulp test, ").append(c[2]).append(" of them not an exception input"));
        assertEquals(0L, c[2]);
    }

    @Test
    void randomFullRangeTwoMillion() {
        checkChunked("random exponent field 0..2046 (20261401+chunk)", chunk -> CoreMathTestSupport.exponentUniform(20261401L + chunk, CHUNK, 0, 2046), 2_000_000);
    }

    @Test
    void randomModerateOneAndAHalfMillion() {
        checkChunked("random unbiased exponent -28..4 (20261411+chunk)", chunk -> CoreMathTanTestSupport.binades(20261411L + chunk, chunk == 1 ? 500_000 : CHUNK, -28, 4), 1_500_000);
    }

    @Test
    void randomMidOneMillion() {
        checkChunked("random unbiased exponent 5..52 (20261421)", chunk -> CoreMathTanTestSupport.binades(20261421L + chunk, CHUNK, 5, 52), 1_000_000);
    }

    @Test
    void randomLargeOneMillion() {
        checkChunked("random unbiased exponent 52..1023 (20261431)", chunk -> CoreMathTanTestSupport.binades(20261431L + chunk, CHUNK, 52, 1023), 1_000_000);
    }

    @Test
    void randomUniformZeroToEightOneMillion() {
        checkChunked("random uniform [0, 8) (20261441)", chunk -> CoreMathTanTestSupport.uniformZeroToEight(20261441L + chunk, CHUNK), 1_000_000);
    }

    @Test
    void nearMultiplesOfHalfPiQuarterMillionEach() {
        checkChunked("the double nearest k pi/2 +-8 ulps, k of at most 52 bits (20261451)", chunk -> CoreMathTanTestSupport.nearHalfPiMultiples(20261451L, 250_000, 52), 250_000);
        checkChunked("the double nearest k pi/2 +-8 ulps, k of at most 1020 bits (20261452; about 5% near a pole)", chunk -> CoreMathTanTestSupport.nearHalfPiMultiples(20261452L, 250_000, 1020), 250_000);
    }
}
