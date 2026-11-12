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
            boolean got = CoreMathTan.guardFires(u, i, x);
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
                if (CoreMathTan.guardFires(u, 0, x)) {
                    fired++;
                }
            }
        }
        System.out.println(new StringBuilder(100).append("B2F-G2 R = 2^-72, 2^-80, 2^-100, 0: the guard fired on ").append(fired).append(" of ").append(total));
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
        double maxR = 0;
        for (double x : xs) {
            if (!CoreMathTanTestSupport.PATH_DOMAIN.test(x)) {
                continue;
            }
            CoreMathDint xd = new CoreMathDint();
            int[] st = new int[2];
            int i = CoreMathTan.fold(x, xd, st);
            BigDecimal rExact = exactR(i, xd);
            double rd = rExact.doubleValue();
            assertTrue(rd < 0.125, "the folded argument is below 1/8");
            maxR = Math.max(maxR, rd);
            if (rd < 0x1p-20) {
                tinyR++;
            }
            BigInteger dIn = BigInteger.valueOf(86).add(new BigDecimal(7).divide(rExact, new MathContext(60)).toBigInteger());
            BigInteger dOut = BigInteger.valueOf(86).add(new BigDecimal("8.08").divide(rExact, new MathContext(60)).toBigInteger()).add(BigInteger.valueOf(3));
            for (boolean above : new boolean[] {true, false}) {
                CoreMathDint uIn = placed(r, dIn, above);
                CoreMathDint uOut = placed(r, dOut, above);
                n++;
                if (CoreMathTan.guardFires(uIn, i, xd)) {
                    inFired++;
                }
                if (CoreMathTan.c86TestFires(uIn)) {
                    cFiredInBand++;
                }
                if (CoreMathTan.guardFires(uOut, i, xd)) {
                    outFired++;
                }
            }
        }
        System.out.println(new StringBuilder(220).append("B2F-G3 ").append(xs.length).append(" real inputs, ").append(n).append(" constructed results per side: guard fired inside the band on ").append(inFired)
                .append(" of ").append(n).append(" (the C's 86-ulp test on ").append(cFiredInBand).append("), beyond the band on ").append(outFired).append("; inputs with R below 2^-20: ").append(tinyR)
                .append(", largest R ").append(maxR));
        assertEquals(n, inFired);
        assertEquals(0, cFiredInBand);
        assertEquals(0, outFired);
        assertTrue(tinyR >= 100);
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
        double maxProduct = 0;
        double maxUlps = 0;
        for (double x : xs) {
            if (!CoreMathTanTestSupport.PATH_DOMAIN.test(x)) {
                continue;
            }
            CoreMathDint xd = new CoreMathDint();
            int[] ig = new int[1];
            CoreMathDint u = CoreMathTan.accurateU(x, xd, ig);
            CoreMathTanExactReference.Tan t = CoreMathTanExactReference.tan(x);
            double ulps = CoreMathTanExactReference.errorUlps(u, t);
            double rd = exactR(ig[0], xd).doubleValue();
            n++;
            maxUlps = Math.max(maxUlps, ulps);
            if (ulps > 86 + 8 / rd) {
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
        System.out.println(new StringBuilder(260).append("B2F-G5 ").append(n).append(" inputs against the 800-bit tangent: error above 86 + 8/R ulps on ").append(outsideWindow).append("; unfolded ").append(unfolded)
                .append(" (above 86 ulps: ").append(unfoldedOver86).append("), folded ").append(folded).append(" (above 86 ulps: ").append(foldedOver86).append("); largest error ").append(maxUlps)
                .append(" ulps; largest error x R over the folded ").append(maxProduct).append("; accurate path rounded wrong on ").append(wrongRounded));
        assertEquals(0, outsideWindow);
        assertEquals(0, unfoldedOver86);
        assertTrue(maxProduct >= 0.5 && maxProduct <= 3.2, "error x R over the folded inputs: " + maxProduct);
        assertEquals(0, wrongRounded);
    }
}
