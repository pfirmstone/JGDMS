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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

/**
 * The GUARD of {@link CoreMathTan} (operator ruling 2026-11-12 after the phase B2 reviews: the C's 86-ulp window is not the accurate path's error after a quarter-turn fold, so the
 * port widens it by K/R and delegates what falls inside to {@link CrMath}; K = {@link CoreMathTanTables#GUARD_K} = 8, derivation in the javadoc of {@link CoreMathTan}).
 * Runtime tier: FAST (default suite).
 *
 * <h2>SEALED PREDICTIONS (B2F-G1..G6, the fix pass; written before the first run of this class and of the guard code)</h2>
 * Window = 86 + K/R ulps of the 128-bit mantissa; fires iff some multiple of 2^74 (a rounding boundary or midpoint of the 53-bit result) lies in (U - window, U + window].
 * <ol>
 *   <li><b>B2F-G1 the decision, against an exact model.</b> For 200,000 pairs of a folded argument {@code R = i/2^11 + X} (i = 0 with X in [2^-74, 2^-11], and i = 1..255) and a
 *       128-bit result U placed at a chosen distance from a boundary (just inside the exact window, just outside it, at random distances up to three windows, and fully random U),
 *       plus 150 edge cases with R exactly 2^-p (p = 12..80, so the window is exactly 86 + 8 * 2^p up to the +1 of the code), {@code guardFires} agrees with the BigInteger/BigDecimal
 *       model except inside the model's own tolerance band (the window rounded up by 1 and by 2^-100 of itself): 0 violations; at least 50,000 cases fire and at least 50,000 do not
 *       (the test discriminates). ELSE: a violation is a defect of the guard's window or of the shift by 74.</li>
 *   <li><b>B2F-G2 always fires when the window reaches the boundary spacing.</b> For R = 2^-72, 2^-80, 2^-100 and R = 0 (i = 0, X = 0), 10,000 random U each: {@code guardFires} is true 40,000
 *       of 40,000.</li>
 *   <li><b>B2F-G3 the guard fires on inputs constructed in the band the C leaves uncovered (control).</b> For {@code x} in a list of real inputs (the named list: the doubles nearest pi/2, pi, 3 pi/2,
 *       2 pi, 0x1.6ac5b262ca1ffp+849, 2 * that, 0x1.6c6cbc45dc8dep5, 1.58, 3.15; the doubles nearest k pi/2 for k = 1..60 moved by -3..+3 ulps: about 430 inputs, all above the tiny threshold),
 *       {@code fold} gives the real folded argument R; with U placed at distance {@code 86 + floor(7/R)} ulps from a boundary, on either side (the band between the C's 86 and the guard's
 *       86 + 8/R): the C's 86-ulp test does NOT fire and the guard fires, for every input and both sides; at distance {@code 86 + 8.08/R + 3} the guard does not fire. Every R in the list is
 *       below 1/8; at least 100 of the inputs have R below 2^-20.</li>
 *   <li><b>B2F-G4 routing.</b> A {@link CoreMath} whose tan port answers NaN counts every call ({@link CoreMath#tanGuardDelegations}) and answers it from the delegate: 2,000 of 2,000 calls counted,
 *       2,000 of 2,000 delegate calls, every value equal to {@code CrMath}'s. NaN, +Inf and -Inf still reach the delegate directly (which throws) and are not counted.</li>
 *   <li><b>B2F-G5 the window contains the error, measured against an exact tangent</b> ({@link CoreMathTanExactReference}, 800-bit fixed point; about 1,500 inputs: the doubles nearest k pi/2 moved by
 *       +-2^-3, 2^-9, 2^-21, 2^-45 for k = 1..60, the named list, 300 random in (0, 8), 200 random with exponent 3..60): for every input above the tiny threshold the error of the 128-bit
 *       result {@code U} is at most {@code 86 + 8/R} ulps (0 violations); for inputs with no fold (frac(x/(2 pi)) below 1/8) at most 86 (0 violations); the largest value of
 *       (error in ulps) x R over the folded inputs lies between 0.5 and 3.2 (the reviewers measured 2.58 on {@code tan.wc} and 2.23 on random inputs; the rigorous constant is 5.28); and the
 *       accurate path's rounded result equals the exactly rounded tangent for every input (0 wrong).</li>
 *   <li><b>B2F-G6 how often it fires in practice</b> (production path through {@code CoreMath}, counted): 0 delegations over the named inputs and the seven populations of
 *       {@code CoreMathTanDifferentialTest} (about 70,500 calls) and 0 over the 4,260 golden entries (expected about 2*(86 + 8/R)/2^75 per call that reaches the accurate path,
 *       a few times 10^-9 over these populations). ELSE: a non-zero count is reported with its input; it is the guard doing its job (the result still equals {@code CrMath}), not a failure.
 *       The slow tier counts it over {@code tan.wc} (1,098,254 entries, expected well below 1), 6.5 million random and 500,000 near-pole inputs (CoreMathTanSlowTest).</li>
 * </ol>
 * Mutation predictions (a mutant that removes the guard, one that changes K) are in the message of the seal commit that follows this class's own.
 *
 * <h2>SCORING of B2F-G1..G6 (appended 2026-11-12 after the first run; the sealed text above is unchanged)</h2>
 * B2F-G1..G5 HELD at the first run (see the lines printed by this class). B2F-G6 FAILED as sealed: 2 delegations over the 70,512 calls of {@code CoreMathTanDifferentialTest}, not 0. They
 * are x = +-0x1.d12ed0af1a27fp-27, the neighbours of the tiny-argument threshold, whose tangent lies within about 2^22 128-bit ulps of a rounding midpoint (DERIVED, not measured: the threshold is where
 * x + x^3/3 crosses the half-ulp above x, and one step of the input moves x^3/3 by about x^2, 2^-53.6, of an ulp of x, which is 2^21.4 of the 2^75 128-bit ulps in it), so the window 86 + 8/R of R about 2^-28.8 reaches it. The sealed estimate counted only random positions of the result and missed hard cases built
 * into the C's own threshold. Each delegated value equals {@code CrMath}'s (0 mismatches of 70,512); {@code CoreMathTanDifferentialTest#p6EveryFiniteInputIsHandledByThePortNeverByTheDelegate}
 * now asserts exactly these two (deviation D8).
 *
 * <h3>Mutation runs of the guard (DIAGNOSTIC; population: the fast set of 8 classes, 43 tests, {@code CoreMathTanGuardTest, CoreMathTanFoldErrorReproducerTest, CoreMathTanDifferentialTest,
 * CoreMathTanWorstCaseGoldenTest, CoreMathTanTablesDigestTest, CoreMathTanReductionTest, CoreMathDintTest, CoreMathTanPathCensusTest}; one mutant applied to the working tree at a time, tree restored after each;
 * run 2026-11-12 16:02-16:06 at the tree of commit ac2da0c58; predictions sealed in the message of 1bac8f5dc; scoring rule: the failing test methods named against the prediction)</h3>
 * <ul>
 *   <li>Mg1 (guardFires returns false): predicted G1, G2 and G3 fail (at least 3 methods) HELD; predicted that no differential, golden or slow result changes FAILED: 4 methods failed, the
 *       fourth is {@code CoreMathTanDifferentialTest#p6...} (expected 2 delegations, was 0), the consequence of D8 (the guard does fire on the two tiny-threshold neighbours). G1 86,899 violations of
 *       200,148; G2 fired 0 of 40,000; G3 in-band 0 of 858.</li>
 *   <li>Mg2 (GUARD_K 8 to 1): predicted detected by G3 and the digest only, not by G1 and G5: G3 and the digest detected it and G1 and G5 did not (HELD), but G2 detected it too (fired 34,950 of
 *       40,000), so the word "only" FAILED: at R = 2^-72 the window must reach 2^74, which needs K of at least 4, a dependence on K that the prediction overlooked.</li>
 *   <li>Mg3 (GUARD_K 8 to 16): predicted G3 (beyond-band results fire) and the digest: HELD exactly (G3 beyond-band 858 of 858; nothing else failed).</li>
 *   <li>Mg4 (R = X only, the grid index ignored): predicted G1 and G3: HELD exactly (G1 42,809 violations; G3 beyond-band 4).</li>
 *   <li>Mg5 (the +1 dropped from the window): predicted to SURVIVE; FAILED: killed by G1, 13 violations of 200,148 (the cases placed within one ulp inside the exact window, where
 *       floor(K/R) falls short of K/R by up to 1). The tests are sharper than sealed: the +1 is what makes the window at least 86 + K/R.</li>
 * </ul>
 *
 * <h2>SEALED PREDICTIONS (B2F2-*, fix pass 2, 2026-11-12; written in the commit that precedes the first run of the tests below, which also adapts G1..G5 to the narrowed window)</h2>
 * Re-check reviews of 22a20ad5a (Opus MEDIUM-1, Fable M-1 and M-2); operator decision, verbatim: "Narrow it (Recommended)" (the window is F-aware). {@code guardFires} now takes the fold flag. The
 * window is {@code w = 86 + floor(K/R) + 1} after a fold and {@code w = 86 + floor(K) + 1 = 95} without one, on the CLOSED interval {@code [U - w, U + w]} (Opus LOW-2).
 * <ol>
 *   <li><b>B2F2-G1/G2/G3/G5 (adapted; G4 unchanged).</b> G1 (folded) as sealed: 0 violations of 200,148, at least 50,000 fire and 50,000 do not. G2: 50,000 of 50,000 (the four folded R and the
 *       unfolded R = 0). G3 splits by the fold flag, in-band distance {@code 86 + 7/R} after a fold and 93 without: in-band fired n of n, the C's test 0, beyond-band 0; of the real inputs at least 10
 *       are unfolded and at least 300 folded. G5: 0 above the window ({@code 86 + 8/R} after a fold, {@code 86 + 8} without), the fold flag of the code equals "frac(x/(2 pi)) >= 1/8" of the
 *       exact reference on all 1,409 inputs (0 mismatches), the rest of G5 as sealed.</li>
 *   <li><b>B2F2-G1b.</b> Without a fold the window is exactly 95 on the closed interval: on 100,000 cases (any index; U placed exactly at the reach, just inside, just outside, and random) 0
 *       violations with NO tolerance band; at least 10,000 fire and 10,000 do not.</li>
 *   <li><b>B2F2-M1 (Fable M-1 (i)).</b> The real folded {@code tan.wc} entry {@code x = 0x1.92456c9a2452ep282} (cot branch, R = 1.503e-8 turns): the fold flag is 1; {@code accurateAmbiguous} sets
 *       {@code flags[1]} and not {@code flags[0]}; {@code accurateGuarded(x)} is NaN; a {@link CoreMath} whose tan port is {@code accurateGuarded} counts 1, calls the delegate once and answers
 *       with the bits of {@code CrMath}; a {@code CoreMath} with the production port {@code tan} counts 0 and answers with the same bits (the fast path decides it); and {@code guardFires} on the
 *       same {@code U}, {@code i}, {@code X} with the fold flag cleared is false (the unfolded window 95 is far below the 9.2e7 ulps distance): the flag is what makes the guard fire.</li>
 *   <li><b>B2F2-K (Fable M-1 (iii)).</b> With {@code K = 2^54} on 20,000 inputs of the population "the double nearest k pi/2 +-8 ulps, k of at most 52 bits" (seed 20261701):
 *       {@code tanWithK(x, K)} is NaN exactly when the fast path is undecided, x is not an exception and {@code guardFires(accurateU(x), i, xd, fold flag, K)} on the folded pair: 0 mismatches of
 *       20,000; the number of NaN lies in [3,500, 6,200] (expected about 5,300: 30.4% undecided, seven eighths of them folded); at least 300 undecided inputs are unfolded, and at most 3 of those are NaN.</li>
 *   <li><b>B2F2-P (Fable L-4).</b> The 1/(2 pi) words of the exact reference equal {@code tan.c}'s {@code T[0..19]}: 20 of 20.</li>
 * </ol>
 * Delegation counts over the populations (B2F2-D) are sealed in {@code CoreMathTanSlowTest}, {@code CoreMathTanDifferentialTest} and {@code CoreMathTanWorstCaseGoldenTest}; the mutation
 * predictions (RA, RI, MR5 and three more) are in the message of the seal commit of this pass. *
 * <h3>SCORING of B2F2-* (appended 2026-11-12 after the first runs, at 9a5af5536; the sealed text above is unchanged): ALL HELD</h3>
 * <ul>
 *   <li>G1 0 violations of 200,148 (fired 98,515, did not fire 101,633); G2 50,000 of 50,000; G3 858 of 858 in band, 0 beyond, folded 381 and unfolded 48 real inputs; G5 0 above the window of
 *       1,409, largest error x R 1.608, the fold flag equal to the exact reference's on 1,409 of 1,409; G1b 0 violations of 100,000 (fired 56,375).</li>
 *   <li>M1: R = 1.5030e-8 turns, error of U 7.41e6 ulps, window 86 + 8/R = 5.32e8 ulps, forced path flagged, guard-applying path NaN, counted once, equal to CrMath, production 0.</li>
 *   <li>K = 2^54: 20,000 inputs, fast path undecided on 6,015, NaN 5,382, 0 mismatches with the model on the folded pair, undecided and unfolded 632 of which NaN 0. P: 20 of 20.</li>
 *   <li>B2F2-D (the slow tier, {@code logs/fp2_run2_slow.log}): production delegations 0 in every population including 0 of 1,098,254 {@code tan.wc}; forced-path firings 0 in every random and
 *       near-pole population and 1 of 1,098,245 over {@code tan.wc}; golden 0 of 4,260; fast tier 0 of 70,512; 30 comparison lines 0 mismatches.</li>
 *   <li>MUTANTS (DIAGNOSTIC, the 9-class set of 52 tests, then the slow wcEveryFiniteEntry): RA, RI, MR5, RV each exactly 2 failing methods (M1, K) and the slow count pin (1 -> 0); RW exactly 3
 *       (p6, the golden count, K) and the slow pin (0 -> 1,571); RC exactly 1 (G1b, 15,115 violations). All as predicted.</li>
 * </ul>
 * <h2>SEALED PREDICTIONS (B2F3-*, fix pass 3, 2026-11-12; written in the commit that precedes the first run of the tests below)</h2>
 * Re-check 2 of 69639bd15 (Fable M-1, L-1, L-2; Opus LOW-2). {@code tan(x)} is now {@code tanWithK(x, GUARD_K)}.
 * <ol>
 *   <li><b>B2F3-K (the K test with an independent flag).</b> {@code folded} is taken from {@code CoreMathTanExactReference.isUnfolded}: the code's fold flag equals it on every undecided input of the
 *       20,000 (0 mismatches), the production path's NaN equals the model on that flag (0 mismatches), and the counts are those of fix pass 2 (HYPOTHESIS: the flag is exact, so nothing changes): undecided 6,015,
 *       NaN 5,382, undecided and unfolded 632 of which NaN 0.</li>
 *   <li><b>B2F3-G5 (L-1).</b> With {@code Math.nextUp(0x1.921fb54442d18p-1)} (just above pi/4, grid index 256) added, G5 runs on 1,410 inputs (folded 1,224, unfolded 186), the fold flag equals the exact
 *       reference on all, the other predictions as before.</li>
 * </ol>
 * The entry-point pins (B2F3-E) are sealed in {@code CoreMathTanEntryPointTest}; the mutation predictions (PG, PG0, PK, PC and the fold-flag mutants) are in the message of the seal commit of this pass.
 *
 * <h3>SCORING of B2F3-* (appended 2026-11-12 after the first runs, at ac25cfbbe; the sealed text above is unchanged): ALL HELD</h3>
 * K test: the code's fold flag equals the exact reference's on all 6,015 undecided inputs (0 mismatches), 0 mismatches with the model, NaN 5,382, undecided and unfolded 632 of which NaN 0; G5 on 1,410
 * inputs (folded 1,224, unfolded 186), flag equal on all. MUTANTS (DIAGNOSTIC, 55 tests; every prediction held): FA 5 failing methods, FB 5, FC 3 (was 2), FD 1 (was 0), FH 3 (was 2), FK 1, N2 4, FG 1,
 * RA 2, RI 2, MR5 2, PG 1, PG0 1, PK 1, PC 1.
 */
class CoreMathTanGuardTest {

    private static final BigInteger TWO74 = BigInteger.ONE.shiftLeft(74);

    private static BigDecimal pow2(long k) {
        if (k >= 0) {
            return new BigDecimal(BigInteger.ONE.shiftLeft((int) k));
        }
        return new BigDecimal(BigInteger.valueOf(5).pow((int) -k)).movePointLeft((int) -k);
    }

    /** R = i/2^11 + X exactly. */
    private static BigDecimal exactR(int i, CoreMathDint x) {
        BigDecimal rx = new BigDecimal(CoreMathDintTest.mant(x)).multiply(pow2(x.ex - 128));
        return new BigDecimal(i).divide(new BigDecimal(2048)).add(rx);
    }

    /** floor(8/R) as a BigInteger (the exact window is 86 + 8/R; the code uses 86 + floor(8/R) + 1). */
    private static BigInteger floorKOverR(BigDecimal r) {
        return new BigDecimal(CoreMathTanTables.GUARD_K).divide(r, new MathContext(80)).toBigInteger();
    }

    private static CoreMathDint dint(BigInteger m128, long ex) {
        return new CoreMathDint(m128.shiftRight(64).longValue(), m128.longValue(), ex, 0L);
    }

    private static CoreMathDint randomX(SplittableRandom r, int exLo, int exHi) {
        CoreMathDint x = new CoreMathDint();
        x.hi = r.nextLong() | Long.MIN_VALUE;
        x.lo = r.nextLong();
        x.ex = exLo + r.nextInt(exHi - exLo + 1);
        return x;
    }

    /** U = boundary + d (above = true) or boundary - d, with the boundary a random multiple of 2^74 with the top bit set. */
    private static CoreMathDint placed(SplittableRandom r, BigInteger d, boolean above) {
        long hi = (r.nextLong() | Long.MIN_VALUE) & ~0x3ffL;                     // a multiple of 2^74 with the top bit set
        BigInteger b = CoreMathDintTest.u(hi).shiftLeft(64);
        BigInteger u = above ? b.add(d) : b.subtract(d);
        return dint(u, 1L);
    }

    // ------------------------------------------------------------------------------------------
    // B2F-G1, G2
    // ------------------------------------------------------------------------------------------

    @Test
    void g1TheDecisionEqualsTheExactModelOutsideItsToleranceBand() {
        SplittableRandom r = new SplittableRandom(20261601L);
        int n = 0;
        int violations = 0;
        int fired = 0;
        int quiet = 0;
        int band = 0;
        List<Object[]> cases = new ArrayList<>();
        for (int c = 0; c < 200_000; c++) {
            int i;
            CoreMathDint x;
            if (c % 3 == 0) {
                i = 0;
                x = randomX(r, -74, -11);
            } else {
                i = 1 + r.nextInt(255);
                x = randomX(r, -60, -11);
            }
            cases.add(new Object[] {i, x});
        }
        for (int p = 12; p <= 80; p++) {                                       // edge: R = 2^-p exactly (i = 0, X = 2^-p)
            CoreMathDint x = new CoreMathDint(Long.MIN_VALUE, 0L, 1L - p, 0L);
            cases.add(new Object[] {0, x});
            cases.add(new Object[] {0, x});
        }
        for (int c = 0; c < 10; c++) {
            cases.add(new Object[] {255, new CoreMathDint(Long.MIN_VALUE, 0L, -11L, 0L)});
        }
        for (Object[] cs : cases) {
            int i = (Integer) cs[0];
            CoreMathDint x = (CoreMathDint) cs[1];
            BigDecimal rExact = exactR(i, x);
            BigInteger w = BigInteger.valueOf(86).add(floorKOverR(rExact)).add(BigInteger.ONE);      // the code's window, to within the tolerance below
            BigInteger tol = BigInteger.ONE.add(w.shiftRight(100));
            BigInteger wLo = w.subtract(tol);
            BigInteger wHi = w.add(tol);
            CoreMathDint u;
            int mode = n % 5;
            boolean above = r.nextBoolean();
            if (w.bitLength() > 70) {
                mode = 4;                                                       // d must stay below 2^74: only random placements for huge windows
            }
            switch (mode) {
                case 0:
                    u = placed(r, wLo.subtract(BigInteger.ONE).max(BigInteger.ZERO), above);   // just inside the tolerance band
                    break;
                case 1:
                    u = placed(r, wHi.add(BigInteger.ONE), above);              // just outside it
                    break;
                case 2:
                    u = placed(r, new BigInteger(Math.max(1, w.bitLength() + 2), new java.util.Random(r.nextLong())).mod(w.multiply(BigInteger.valueOf(3)).add(BigInteger.ONE)), above);
                    break;
                case 3:
                    u = placed(r, w.subtract(BigInteger.valueOf(r.nextInt(5))).max(BigInteger.ZERO), above);
                    break;
                default:
                    u = dint(new BigInteger(128, new java.util.Random(r.nextLong())).setBit(127), 1L);
                    break;
            }
            n++;
            BigInteger um = CoreMathDintTest.mant(u);
            BigInteger dLow = um.mod(TWO74);
            BigInteger dUp = TWO74.subtract(dLow);
            boolean mustFire = dLow.compareTo(wLo) < 0 || dUp.compareTo(wLo) <= 0;
            boolean mustNotFire = dLow.compareTo(wHi) >= 0 && dUp.compareTo(wHi) > 0;
            if (w.bitLength() > 74) {
                mustFire = true;
                mustNotFire = false;
            }
            boolean got = CoreMathTan.guardFires(u, i, x, true);
            if (mustFire && !got || mustNotFire && got) {
                violations++;
            }
            if (!mustFire && !mustNotFire) {
                band++;
            }
            if (got) {
                fired++;
            } else {
                quiet++;
            }
        }
        System.out.println(new StringBuilder(200).append("B2F-G1 guard decision vs the exact model: ").append(violations).append(" violations of ").append(n).append(" cases (fired ").append(fired)
                .append(", did not fire ").append(quiet).append(", inside the tolerance band ").append(band).append(")"));
        assertEquals(0, violations);
        assertTrue(fired >= 50_000 && quiet >= 50_000, "the population does not discriminate");
    }

    @Test
    void g2AlwaysFiresWhenTheWindowReachesTheSpacingOfTheBoundaries() {
        SplittableRandom r = new SplittableRandom(20261602L);
        int fired = 0;
        int total = 0;
        CoreMathDint[] xs = {
            new CoreMathDint(Long.MIN_VALUE, 0L, 1L - 72L, 0L), new CoreMathDint(Long.MIN_VALUE, 0L, 1L - 80L, 0L), new CoreMathDint(Long.MIN_VALUE, 0L, 1L - 100L, 0L), new CoreMathDint(0L, 0L, -1076L, 0L)};
        for (CoreMathDint x : xs) {
            for (int c = 0; c < 10_000; c++) {
                CoreMathDint u = dint(new BigInteger(128, new java.util.Random(r.nextLong())).setBit(127), 1L);
                total++;
                if (CoreMathTan.guardFires(u, 0, x, true)) {
                    fired++;
                }
            }
        }
        for (int c = 0; c < 10_000; c++) {                                     // fix pass 2: R = 0 without a fold fires too (the code never answers from a zero argument)
            CoreMathDint u = dint(new BigInteger(128, new java.util.Random(r.nextLong())).setBit(127), 1L);
            total++;
            if (CoreMathTan.guardFires(u, 0, new CoreMathDint(0L, 0L, -1076L, 0L), false)) {
                fired++;
            }
        }
        System.out.println(new StringBuilder(100).append("B2F-G2 R = 2^-72, 2^-80, 2^-100, 0 (and 0 unfolded): the guard fired on ").append(fired).append(" of ").append(total));
        assertEquals(total, fired);
    }

    // ------------------------------------------------------------------------------------------
    // B2F-G3: real folded arguments, constructed results in the uncovered band
    // ------------------------------------------------------------------------------------------

    static double[] realInputs() {
        List<Double> v = new ArrayList<>();
        double x0 = 0x1.6ac5b262ca1ffp+849;
        double[] named = {0x1.921fb54442d18p+0, 0x1.921fb54442d18p+1, 0x1.2d97c7f3321d2p+2, 0x1.921fb54442d18p+2, x0, 2 * x0, 0x1.6c6cbc45dc8dep5, 1.58, 3.15};
        for (double d : named) {
            v.add(d);
        }
        for (int k = 1; k <= 60; k++) {
            double m = CoreMathTanTestSupport.nearestToMultipleOfHalfPi(BigInteger.valueOf(k));
            for (int d = -3; d <= 3; d++) {
                double x = m;
                for (int s = 0; s < Math.abs(d); s++) {
                    x = d > 0 ? Math.nextUp(x) : Math.nextDown(x);
                }
                v.add(x);
            }
        }
        double[] out = new double[v.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = v.get(i);
        }
        return out;
    }

    @Test
    void g3TheGuardFiresInTheBandTheCLeavesUncoveredAndNotBeyondIt() {
        SplittableRandom r = new SplittableRandom(20261603L);
        double[] xs = realInputs();
        int n = 0;
        int inFired = 0;
        int cFiredInBand = 0;
        int outFired = 0;
        int tinyR = 0;
        int foldedInputs = 0;
        int unfoldedInputs = 0;
        double maxR = 0;
        for (double x : xs) {
            if (!CoreMathTanTestSupport.PATH_DOMAIN.test(x)) {
                continue;
            }
            CoreMathDint xd = new CoreMathDint();
            int[] st = new int[3];
            int i = CoreMathTan.fold(x, xd, st);
            boolean folded = st[2] != 0;
            BigDecimal rExact = exactR(i, xd);
            double rd = rExact.doubleValue();
            assertTrue(rd < 0.125, "the folded argument is below 1/8");
            maxR = Math.max(maxR, rd);
            if (rd < 0x1p-20) {
                tinyR++;
            }
            if (folded) {
                foldedInputs++;
            } else {
                unfoldedInputs++;
            }
            // the window term: K/R after a fold, K without one (fix pass 2); in-band 7 of it, beyond-band 8.08 of it plus 3
            BigDecimal termIn = folded ? new BigDecimal(7).divide(rExact, new MathContext(60)) : new BigDecimal(7);
            BigDecimal termOut = folded ? new BigDecimal("8.08").divide(rExact, new MathContext(60)) : new BigDecimal("8.08");
            BigInteger dIn = BigInteger.valueOf(86).add(termIn.toBigInteger());
            BigInteger dOut = BigInteger.valueOf(86).add(termOut.toBigInteger()).add(BigInteger.valueOf(3));
            for (boolean above : new boolean[] {true, false}) {
                CoreMathDint uIn = placed(r, dIn, above);
                CoreMathDint uOut = placed(r, dOut, above);
                n++;
                if (CoreMathTan.guardFires(uIn, i, xd, folded)) {
                    inFired++;
                }
                if (CoreMathTan.c86TestFires(uIn)) {
                    cFiredInBand++;
                }
                if (CoreMathTan.guardFires(uOut, i, xd, folded)) {
                    outFired++;
                }
            }
        }
        System.out.println(new StringBuilder(260).append("B2F-G3 ").append(xs.length).append(" real inputs (folded ").append(foldedInputs).append(", unfolded ").append(unfoldedInputs).append("), ").append(n)
                .append(" constructed results per side: guard fired inside the band on ").append(inFired).append(" of ").append(n).append(" (the C's 86-ulp test on ").append(cFiredInBand)
                .append("), beyond the band on ").append(outFired).append("; inputs with R below 2^-20: ").append(tinyR).append(", largest R ").append(maxR));
        assertEquals(n, inFired);
        assertEquals(0, cFiredInBand);
        assertEquals(0, outFired);
        assertTrue(tinyR >= 100);
        assertTrue(unfoldedInputs >= 10 && foldedInputs >= 300, CoreMathTestSupport.msg("folded ", foldedInputs, ", unfolded ", unfoldedInputs));
    }

    // ------------------------------------------------------------------------------------------
    // B2F-G4: routing through CoreMath
    // ------------------------------------------------------------------------------------------

    @Test
    void g4ARoutedUndecidedCallIsCountedAndAnsweredByTheDelegate() {
        CountingDelegate delegate = new CountingDelegate();
        CoreMath always = new CoreMath(delegate, CoreMathAtan2::atan2, x -> Double.NaN);
        SplittableRandom r = new SplittableRandom(20261604L);
        int mismatches = 0;
        int n = 2000;
        for (int i = 0; i < n; i++) {
            double x = CoreMathTestSupport.exponentUniform(r.nextLong(), 1, 980, 1100)[0];
            double got = always.tan(x);
            if (Double.doubleToRawLongBits(got) != Double.doubleToRawLongBits(CoreMathTestSupport.ORACLE.tan(x))) {
                mismatches++;
            }
        }
        System.out.println(new StringBuilder(120).append("B2F-G4 routing: ").append(n).append(" calls, counted ").append(always.tanGuardDelegations()).append(", delegate tan calls ").append(delegate.tan.sum())
                .append(", mismatches ").append(mismatches));
        assertEquals(n, always.tanGuardDelegations());
        assertEquals(n, delegate.tan.sum());
        assertEquals(0, mismatches);
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> always.tan(bad));          // CrMath rejects non-finite arguments; not counted
        }
        assertEquals(n, always.tanGuardDelegations());
    }

    // ------------------------------------------------------------------------------------------
    // B2F-G5: the window contains the error (exact tangent)
    // ------------------------------------------------------------------------------------------

    @Test
    void g5TheWindowContainsTheMeasuredErrorAndTheLawIsAboutTwoPointSixOverR() {
        List<Double> xs = new ArrayList<>();
        for (double d : realInputs()) {
            xs.add(d);
        }
        xs.add(Math.nextUp(0x1.921fb54442d18p-1));            // just above pi/4: frac(x/(2 pi)) = 1/8 + 1.3e-17, grid index 256, the first folded cell (Fable L-1)
        for (int k = 1; k <= 60; k++) {
            double m = CoreMathTanTestSupport.nearestToMultipleOfHalfPi(BigInteger.valueOf(k));
            for (int j : new int[] {-3, -9, -21, -45}) {
                xs.add(m + Math.scalb(1.0, j));
                xs.add(m - Math.scalb(1.0, j));
            }
        }
        SplittableRandom r = new SplittableRandom(20261605L);
        for (double v : CoreMathTanTestSupport.uniformZeroToEight(r.nextLong(), 300)) {
            xs.add(v);
        }
        for (double v : CoreMathTanTestSupport.binades(r.nextLong(), 200, 3, 60)) {
            xs.add(v);
        }
        int n = 0;
        int outsideWindow = 0;
        int unfolded = 0;
        int unfoldedOver86 = 0;
        int folded = 0;
        int foldedOver86 = 0;
        int wrongRounded = 0;
        int flagMismatches = 0;
        double maxProduct = 0;
        double maxUlps = 0;
        for (double x : xs) {
            if (!CoreMathTanTestSupport.PATH_DOMAIN.test(x)) {
                continue;
            }
            CoreMathDint xd = new CoreMathDint();
            int[] ig = new int[2];
            CoreMathDint u = CoreMathTan.accurateU(x, xd, ig);
            CoreMathTanExactReference.Tan t = CoreMathTanExactReference.tan(x);
            double ulps = CoreMathTanExactReference.errorUlps(u, t);
            double rd = exactR(ig[0], xd).doubleValue();
            n++;
            maxUlps = Math.max(maxUlps, ulps);
            boolean foldedByCode = ig[1] != 0;
            if (foldedByCode != !CoreMathTanExactReference.isUnfolded(t)) {
                flagMismatches++;                                           // the code's fold flag against frac(x/(2 pi)) >= 1/8 of the exact reference
            }
            if (ulps > 86 + (foldedByCode ? 8 / rd : 8)) {                  // the narrowed window (fix pass 2): 86 + K/R after a fold, 86 + K without
                outsideWindow++;
            }
            if (CoreMathTanExactReference.isUnfolded(t)) {
                unfolded++;
                if (ulps > 86) {
                    unfoldedOver86++;
                }
            } else {
                folded++;
                if (ulps > 86) {
                    foldedOver86++;
                }
                maxProduct = Math.max(maxProduct, ulps * rd);
            }
            if (Double.doubleToRawLongBits(CoreMathTan.accurate(x)) != Double.doubleToRawLongBits(CoreMathTanExactReference.round(t))) {
                wrongRounded++;
            }
        }
        System.out.println(new StringBuilder(260).append("B2F-G5 ").append(n).append(" inputs against the 800-bit tangent: error above the window (86 + 8/R after a fold, 86 + 8 without) on ").append(outsideWindow).append("; unfolded ").append(unfolded)
                .append(" (above 86 ulps: ").append(unfoldedOver86).append("), folded ").append(folded).append(" (above 86 ulps: ").append(foldedOver86).append("); largest error ").append(maxUlps)
                .append(" ulps; largest error x R over the folded ").append(maxProduct).append("; accurate path rounded wrong on ").append(wrongRounded)
                .append("; fold flag of the code against the exact reference: ").append(flagMismatches).append(" mismatches"));
        assertEquals(0, flagMismatches);
        assertEquals(0, outsideWindow);
        assertEquals(0, unfoldedOver86);
        assertTrue(maxProduct >= 0.5 && maxProduct <= 3.2, CoreMathTestSupport.msg("error x R over the folded inputs: ", maxProduct));
        assertEquals(0, wrongRounded);
    }

    // ------------------------------------------------------------------------------------------
    // Fix pass 2: B2F2-G1b, M1, K, P
    // ------------------------------------------------------------------------------------------

    @Test
    void g1bWithoutAFoldTheWindowIsExactly95OnTheClosedInterval() {
        SplittableRandom r = new SplittableRandom(20261611L);
        BigInteger w = BigInteger.valueOf(86).add(BigInteger.valueOf((long) CoreMathTanTables.GUARD_K)).add(BigInteger.ONE);
        int n = 100_000;
        int violations = 0;
        int fired = 0;
        for (int c = 0; c < n; c++) {
            int i = r.nextBoolean() ? 0 : r.nextInt(256);
            CoreMathDint x = randomX(r, -74, -11);
            boolean above = r.nextBoolean();
            BigInteger d;
            switch (c % 4) {
                case 0:
                    d = w;                                                  // exactly at the reach: the closed interval fires
                    break;
                case 1:
                    d = w.subtract(BigInteger.valueOf(r.nextInt(5)));
                    break;
                case 2:
                    d = w.add(BigInteger.ONE).add(BigInteger.valueOf(r.nextInt(5)));
                    break;
                default:
                    d = BigInteger.valueOf(r.nextInt(4 * w.intValue()));
                    break;
            }
            CoreMathDint u = placed(r, d, above);
            BigInteger um = CoreMathDintTest.mant(u);
            BigInteger dLow = um.mod(TWO74);
            BigInteger dUp = TWO74.subtract(dLow);
            boolean mustFire = dLow.compareTo(w) <= 0 || dUp.compareTo(w) <= 0;
            boolean got = CoreMathTan.guardFires(u, i, x, false);
            if (got != mustFire) {
                violations++;
            }
            if (got) {
                fired++;
            }
        }
        System.out.println(new StringBuilder(160).append("B2F2-G1b without a fold (window ").append(w).append(", closed interval): ").append(violations).append(" violations of ").append(n)
                .append(" cases (fired ").append(fired).append(", did not fire ").append(n - fired).append(")"));
        assertEquals(0, violations);
        assertTrue(fired >= 10_000 && n - fired >= 10_000, "the population does not discriminate");
    }

    private static long bits(double v) {
        return Double.doubleToRawLongBits(v);
    }

    @Test
    void aRealFoldedTanWcEntryIsCaughtOnTheForcedPathAndThroughTheGuardApplyingPath() {
        double x = 0x1.92456c9a2452ep282;                                   // a real, folded tan.wc entry (cot branch); the fast path decides it, so production never reaches the guard
        CoreMathDint xd = new CoreMathDint();
        int[] ig = new int[2];
        CoreMathDint u = CoreMathTan.accurateU(x, xd, ig);
        double rd = exactR(ig[0], xd).doubleValue();
        assertEquals(1, ig[1], "the entry is folded");
        assertTrue(rd > 1.5e-8 && rd < 1.51e-8, CoreMathTestSupport.msg("R = ", rd));
        boolean[] flags = new boolean[2];
        CoreMathTan.accurateAmbiguous(x, flags);
        assertTrue(flags[1], "the forced accurate path flags the guard");
        assertFalse(flags[0], "the C's own 86-ulp test does not fire");
        assertTrue(Double.isNaN(CoreMathTan.accurateGuarded(x)), "the guard-applying accurate path answers undecided");
        double expected = CoreMathTestSupport.ORACLE.tan(x);
        CountingDelegate d1 = new CountingDelegate();
        CoreMath viaGuarded = new CoreMath(d1, CoreMathAtan2::atan2, CoreMathTan::accurateGuarded);
        assertEquals(bits(expected), bits(viaGuarded.tan(x)));
        assertEquals(1L, viaGuarded.tanGuardDelegations());
        assertEquals(1L, d1.tan.sum());
        CountingDelegate d2 = new CountingDelegate();
        CoreMath production = new CoreMath(d2, CoreMathAtan2::atan2, CoreMathTan::tan);
        assertEquals(bits(expected), bits(production.tan(x)));
        assertEquals(0L, production.tanGuardDelegations());
        assertEquals(0L, d2.tan.sum());
        assertTrue(CoreMathTan.guardFires(u, ig[0], xd, true), "folded: the window 86 + K/R reaches the midpoint");
        assertFalse(CoreMathTan.guardFires(u, ig[0], xd, false), "treated as unfolded the window is 95 ulps and does not: the fold flag is what makes the guard fire");
        CoreMathTanExactReference.Tan t = CoreMathTanExactReference.tan(x);
        System.out.println(new StringBuilder(260).append("B2F2-M1 x = ").append(Double.toHexString(x)).append(": R = ").append(rd).append(" turns, error of U ").append(CoreMathTanExactReference.errorUlps(u, t))
                .append(" ulps, distance of tan x to the nearest midpoint ").append(CoreMathTanExactReference.distanceToMidpointUlps(u, t)).append(" ulps, window 86 + 8/R = ").append(86 + 8 / rd)
                .append(" ulps; guard-applying path NaN, counted ").append(viaGuarded.tanGuardDelegations()).append(", result equal to CrMath; production delegations ").append(production.tanGuardDelegations()));
    }

    @Test
    void aLargeKFiresOnRealFoldedInputsThroughTheProductionPathExactlyAsTheModelSays() {
        double kBig = 0x1p54;
        double[] xs = CoreMathTanTestSupport.nearHalfPiMultiples(20261701L, 20_000, 52);
        int inputs = 0;
        int mismatches = 0;
        int nan = 0;
        int undecided = 0;
        int undecidedUnfolded = 0;
        int undecidedUnfoldedNan = 0;
        int flagMismatches = 0;
        for (double x : xs) {
            if (!CoreMathTanTestSupport.PATH_DOMAIN.test(x)) {
                continue;
            }
            inputs++;
            double got = CoreMathTan.tanWithK(x, kBig);
            boolean expectNaN = false;
            boolean folded = true;
            if (Double.isNaN(CoreMathTan.fast(x))) {
                undecided++;
                CoreMathDint xd = new CoreMathDint();
                int[] ig = new int[2];
                CoreMathDint u = CoreMathTan.accurateU(x, xd, ig);
                folded = !CoreMathTanExactReference.isUnfolded(CoreMathTanExactReference.tan(x));   // from the exact reference, NOT from the code under test (Opus LOW-2, Fable L-2)
                if (folded != (ig[1] != 0)) {
                    flagMismatches++;
                }
                boolean exception = Math.abs(x) == CoreMathTanTables.exceptions(0) || Math.abs(x) == CoreMathTanTables.exceptions(3);
                expectNaN = !exception && CoreMathTan.guardFires(u, ig[0], xd, folded, kBig);
                if (!folded) {
                    undecidedUnfolded++;
                }
            }
            if (Double.isNaN(got) != expectNaN) {
                mismatches++;
            }
            if (Double.isNaN(got)) {
                nan++;
                if (!folded) {
                    undecidedUnfoldedNan++;
                }
            }
        }
        System.out.println(new StringBuilder(220).append("B2F2-K K = 2^54 on ").append(inputs).append(" inputs near multiples of pi/2: fast path undecided on ").append(undecided).append(", NaN ").append(nan)
                .append(", mismatches with the model on the folded pair ").append(mismatches).append("; undecided and unfolded ").append(undecidedUnfolded).append(" of which NaN ").append(undecidedUnfoldedNan)
                .append("; the code's fold flag against the exact reference's on the undecided inputs: ").append(flagMismatches).append(" mismatches"));
        assertEquals(0, flagMismatches);
        assertEquals(0, mismatches);
        assertTrue(nan >= 3_500 && nan <= 6_200, CoreMathTestSupport.msg("NaN ", nan));
        assertTrue(undecidedUnfolded >= 300, CoreMathTestSupport.msg("undecided and unfolded ", undecidedUnfolded));
        assertTrue(undecidedUnfoldedNan <= 3, CoreMathTestSupport.msg("unfolded NaN ", undecidedUnfoldedNan));
    }

    @Test
    void theExactReferenceUsesTheCsPiWords() {
        for (int i = 0; i < 20; i++) {
            assertEquals(CoreMathTanTables.t(i), CoreMathTanExactReference.inv2PiWord(i), CoreMathTestSupport.msg("word ", i, " of 1/(2 pi)"));
        }
    }
}
