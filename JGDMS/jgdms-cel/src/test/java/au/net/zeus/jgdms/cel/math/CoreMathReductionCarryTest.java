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

import java.math.BigInteger;
import java.util.SplittableRandom;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/**
 * Hermetic test of the two Payne-Hanek reductions, {@link CoreMathSupport#reduceLarge} (fast path, x >=
 * 2^31) and {@link CoreMathSupport#reduceLargeAcc} (accurate path, x >= 2^-27), against exact
 * {@link BigInteger} arithmetic. They do their 128-bit additions with plain {@code long}s and
 * hand-written carries, so {@code U128Test} does not reach them, and a dropped carry changes a rounding
 * only for inputs near a midpoint: the differential tests alone would find it only by luck. Runtime tier:
 * FAST (default suite).
 * <p>
 * <b>The reference</b> is a BigInteger model of the C text ({@code sin.c:228-262} and {@code :571-612}): the
 * 192-bit (or 256-bit) window of the 1/(2*pi) table shifted by {@code f}, the exact product with the
 * mantissa modulo 2^128, the exact addition of {@code magic} or of {@code (m*V2) >> 64}, then the masks and
 * the re-centring. It checks the carries and shifts of the Java, not the C's numerical meaning.
 * <p>
 * <b>The inputs</b> are, for every exponent field of the path's domain (reduceLarge 1054..2046, reduceLargeAcc
 * 996..2046): 30 random mantissas, 5 edge mantissas, and ONE mantissa found by search that forces the carry
 * (the search uses plain long arithmetic as a heuristic; the model then confirms, for every such input,
 * that the carry really occurs, so the heuristic cannot mislead the test).
 *
 * <h2>SEALED PREDICTIONS (fix pass, written before the first run of this test)</h2>
 * <ol>
 *   <li><b>F8</b> {@code reduceLarge}: 0 mismatches of u, j and r against the model over about 35,700 inputs
 *       (993 exponents x 36).</li>
 *   <li><b>F9</b> {@code reduceLargeAcc}: 0 mismatches of k, neg and r against the model over about 37,800
 *       inputs (1,051 exponents x 36).</li>
 *   <li><b>F10 reach</b> the model reports a carry for at least one input at EVERY exponent of each path
 *       (993 and 1,051 forced inputs) and the random inputs alone contain several hundred more; both signs of
 *       {@code neg} occur among the accurate-path inputs (each at least 10%).
 *       ELSE-BRANCH: a missing carry class means the test cannot see that carry.</li>
 *   <li><b>F11 control</b> a copy of {@code reduceLarge} with the carry out of {@code lo + 0x810000000000000}
 *       dropped, and a copy of {@code reduceLargeAcc} with the carry out of {@code lo + vHigh} dropped, are run
 *       through the same comparison: each is rejected on EXACTLY the inputs on which the model has a carry
 *       (its mismatch count equals the model's carry count) and on no other.</li>
 * </ol>
 */
class CoreMathReductionCarryTest {

    private static final BigInteger M64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private static final BigInteger M128 = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);
    private static final BigInteger TWO115 = BigInteger.ONE.shiftLeft(115);
    private static final BigInteger MASK115 = TWO115.subtract(BigInteger.ONE);
    private static final BigInteger MAGIC = BigInteger.ONE.shiftLeft(112).add(new BigInteger("810000000000000", 16));
    private static final long MAGIC_LOW = 0x810000000000000L;

    private static BigInteger ub(long v) {
        return new BigInteger(Long.toUnsignedString(v));
    }

    private static BigInteger big(U128 u) {
        return ub(u.hi).shiftLeft(64).add(ub(u.lo));
    }

    private static BigInteger tw(int i) {
        return ub(CoreMathTables.t(i));
    }

    /** What the BigInteger model says for one input. */
    private static final class Model {
        BigInteger u;      // reduceLarge: u after magic; reduceLargeAcc: r (after masking and re-centring)
        long k;            // reduceLargeAcc only
        int neg;           // reduceLargeAcc only
        boolean carry;     // the low-word carry that the Java does by hand
    }

    /** The mantissa with the implicit bit, as the C writes it. */
    private static long mantissa(long bits) {
        return (1L << 52) | (bits & 0xfffffffffffffL);
    }

    private static Model modelLarge(double x) {
        long bits = Double.doubleToRawLongBits(x);
        int e = (int) (bits >>> 52) & 0x7ff;
        BigInteger m = ub(mantissa(bits));
        int i = (e - 1011) / 64;
        int f = (e - 1011) & 0x3f;
        BigInteger w = tw(i).shiftLeft(128).or(tw(i + 1).shiftLeft(64)).or(tw(i + 2));      // 192 bits
        BigInteger v = w.shiftLeft(f).shiftRight(64).and(M128);                              // V0:V1
        BigInteger mv = m.multiply(v).and(M128);
        Model r = new Model();
        r.carry = mv.and(M64).add(ub(MAGIC_LOW)).testBit(64);
        r.u = mv.add(MAGIC).and(M128);
        return r;
    }

    private static Model modelAcc(double x) {
        long bits = Double.doubleToRawLongBits(x);
        int e = (int) (bits >>> 52) & 0x7ff;
        BigInteger m = ub(mantissa(bits));
        int i = -1 + (e - 947) / 64;
        int f = (e - 1011) & 0x3f;
        BigInteger v0 = (i >= 0) ? tw(i) : BigInteger.ZERO;
        BigInteger a = v0.shiftLeft(192).or(tw(i + 1).shiftLeft(128)).or(tw(i + 2).shiftLeft(64)).or(tw(i + 3));   // 256 bits
        BigInteger s = a.shiftLeft(f).and(BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE));
        BigInteger v01 = s.shiftRight(128);                                                  // V0:V1 (the top 128 bits)
        BigInteger v2 = s.shiftRight(64).and(M64);
        BigInteger mv = m.multiply(v01).and(M128);
        BigInteger vHigh = m.multiply(v2).shiftRight(64);
        Model r = new Model();
        r.carry = mv.and(M64).add(vHigh).testBit(64);
        BigInteger u = mv.add(vHigh).and(M128);
        long k = u.shiftRight(115).longValue();
        u = u.and(MASK115);
        r.neg = u.testBit(114) ? 1 : 0;
        if (r.neg != 0) {
            k = (k + 1) & ((1L << 13) - 1);
            u = TWO115.subtract(u);
        }
        r.k = k;
        r.u = u;
        return r;
    }

    // ---- inputs ----

    private static double make(int e, long frac) {
        return Double.longBitsToDouble(((long) e << 52) | (frac & 0xfffffffffffffL));
    }

    /** A mantissa that forces the carry of the given kind at exponent {@code e}, found with plain long arithmetic. */
    private static long forcedFrac(int e, boolean acc, SplittableRandom rnd) {
        BigInteger v01;
        long v2 = 0;
        if (acc) {
            int i = -1 + (e - 947) / 64;
            int f = (e - 1011) & 0x3f;
            BigInteger v0 = (i >= 0) ? tw(i) : BigInteger.ZERO;
            BigInteger a = v0.shiftLeft(192).or(tw(i + 1).shiftLeft(128)).or(tw(i + 2).shiftLeft(64)).or(tw(i + 3));
            BigInteger s = a.shiftLeft(f).and(BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE));
            v01 = s.shiftRight(128);
            v2 = s.shiftRight(64).and(M64).longValue();
        } else {
            int i = (e - 1011) / 64;
            int f = (e - 1011) & 0x3f;
            BigInteger w = tw(i).shiftLeft(128).or(tw(i + 1).shiftLeft(64)).or(tw(i + 2));
            v01 = w.shiftLeft(f).shiftRight(64).and(M128);
        }
        long v1 = v01.and(M64).longValue();
        for (int trial = 0; trial < 2_000_000; trial++) {
            long frac = rnd.nextLong() & 0xfffffffffffffL;
            long m = (1L << 52) | frac;
            long lo = m * v1;
            long add = acc ? Math.unsignedMultiplyHigh(m, v2) : MAGIC_LOW;
            if (Long.compareUnsigned(lo + add, lo) < 0) {
                return frac;
            }
        }
        throw new AssertionError(new StringBuilder(64).append("no forcing mantissa found at exponent ").append(e).toString());
    }

    private static final long[] EDGE_FRACS = {0L, 1L, 0xfffffffffffffL, 0x8000000000000L, 0x7ffffffffffffL};

    private static double[] inputs(int eLo, int eHi, boolean acc, long seed, int[] forcedCount) {
        SplittableRandom rnd = new SplittableRandom(seed);
        double[] xs = new double[(eHi - eLo + 1) * 36];
        int n = 0;
        int forced = 0;
        for (int e = eLo; e <= eHi; e++) {
            for (int j = 0; j < 30; j++) {
                xs[n++] = make(e, rnd.nextLong());
            }
            for (long fr : EDGE_FRACS) {
                xs[n++] = make(e, fr);
            }
            xs[n++] = make(e, forcedFrac(e, acc, rnd));
            forced++;
        }
        forcedCount[0] = forced;
        return xs;
    }

    // ---- the production methods and the mutants, as functions of x returning comparable values ----

    /** reduceLarge with the carry out of {@code lo + 0x810000000000000} dropped (the control). */
    private static U128 mutantLarge(double x) {
        long bits = Double.doubleToRawLongBits(x);
        int e = (int) (bits >>> 52) & 0x7ff;
        long m = mantissa(bits);
        int i = (e - 1011) / 64;
        int f = (e - 1011) & 0x3f;
        long v0;
        long v1;
        if (f == 0) {
            v0 = CoreMathTables.t(i);
            v1 = CoreMathTables.t(i + 1);
        } else {
            v0 = (CoreMathTables.t(i) << f) | (CoreMathTables.t(i + 1) >>> (64 - f));
            v1 = (CoreMathTables.t(i + 1) << f) | (CoreMathTables.t(i + 2) >>> (64 - f));
        }
        long lo = m * v1;
        long hi = Math.unsignedMultiplyHigh(m, v1) + m * v0;
        return U128.of(hi + (1L << 48), lo + MAGIC_LOW);
    }

    /** reduceLargeAcc with the carry out of {@code lo + vHigh} dropped (the control). */
    private static CoreMathSupport.AccReduction mutantAcc(double x) {
        long bits = Double.doubleToRawLongBits(x);
        int e = (int) (bits >>> 52) & 0x7ff;
        long m = mantissa(bits);
        int i = -1 + (e - 947) / 64;
        int f = (e - 1011) & 0x3f;
        long v0 = (i >= 0) ? CoreMathTables.t(i) : 0L;
        long v1 = CoreMathTables.t(i + 1);
        long v2 = CoreMathTables.t(i + 2);
        if (f != 0) {
            v0 = (v0 << f) | (v1 >>> (64 - f));
            v1 = (v1 << f) | (v2 >>> (64 - f));
            v2 = (v2 << f) | (CoreMathTables.t(i + 3) >>> (64 - f));
        }
        long lo = m * v1;
        long hi = Math.unsignedMultiplyHigh(m, v1) + m * v0;
        long vHigh = Math.unsignedMultiplyHigh(m, v2);
        long lo2 = lo + vHigh;
        long hi2 = hi;                                           // the carry is dropped here
        long k = hi2 >>> 51;
        hi2 &= 0x7ffffffffffffL;
        int neg = (int) ((hi2 >>> 50) & 1L);
        U128 u = U128.of(hi2, lo2);
        if (neg != 0) {
            k = (k + 1) & ((1L << 13) - 1);
            u = U128.of(1L << 51, 0L).sub(u);
        }
        return new CoreMathSupport.AccReduction(k, neg, u);
    }

    private static long mismatchesLarge(double[] xs, Model[] models, Function<Double, U128> impl) {
        long bad = 0;
        for (int i = 0; i < xs.length; i++) {
            U128 u = impl.apply(xs[i]);
            if (!big(u).equals(models[i].u)) {
                bad++;
            }
        }
        return bad;
    }

    private static long mismatchesLargeDerived(double[] xs, Model[] models) {
        long bad = 0;
        for (int i = 0; i < xs.length; i++) {
            U128 u = CoreMathSupport.reduceLarge(xs[i]);
            long j = models[i].u.shiftRight(113).longValue();
            long rInt = models[i].u.shiftLeft(15).and(M128).shiftRight(75).longValue();
            double r = (double) rInt * 0x1p-68 - 0x1p-16;
            if (CoreMathSupport.reduceLargeJ(u) != j
                    || Double.doubleToRawLongBits(CoreMathSupport.reduceLargeR(u)) != Double.doubleToRawLongBits(r)) {
                bad++;
            }
        }
        return bad;
    }

    private static long mismatchesAcc(double[] xs, Model[] models, Function<Double, CoreMathSupport.AccReduction> impl) {
        long bad = 0;
        for (int i = 0; i < xs.length; i++) {
            CoreMathSupport.AccReduction a = impl.apply(xs[i]);
            if (a.k != models[i].k || a.neg != models[i].neg || !big(a.r).equals(models[i].u)) {
                bad++;
            }
        }
        return bad;
    }

    @Test
    void reductionsAgreeWithExactArithmeticAndTheCarriesAreReached() {
        int[] forcedLarge = new int[1];
        int[] forcedAcc = new int[1];
        double[] xl = inputs(1054, 2046, false, 20261201L, forcedLarge);
        double[] xa = inputs(996, 2046, true, 20261202L, forcedAcc);
        Model[] ml = new Model[xl.length];
        long carriesLarge = 0;
        for (int i = 0; i < xl.length; i++) {
            ml[i] = modelLarge(xl[i]);
            if (ml[i].carry) {
                carriesLarge++;
            }
        }
        Model[] ma = new Model[xa.length];
        long carriesAcc = 0;
        long negOnes = 0;
        for (int i = 0; i < xa.length; i++) {
            ma[i] = modelAcc(xa[i]);
            if (ma[i].carry) {
                carriesAcc++;
            }
            if (ma[i].neg != 0) {
                negOnes++;
            }
        }
        // every forced input really forces its carry (the search heuristic is checked by the model)
        long forcedConfirmedLarge = 0;
        long forcedConfirmedAcc = 0;
        for (int i = 35; i < xl.length; i += 36) {
            if (ml[i].carry) {
                forcedConfirmedLarge++;
            }
        }
        for (int i = 35; i < xa.length; i += 36) {
            if (ma[i].carry) {
                forcedConfirmedAcc++;
            }
        }

        long badLarge = mismatchesLarge(xl, ml, CoreMathSupport::reduceLarge) + mismatchesLargeDerived(xl, ml);
        long badAcc = mismatchesAcc(xa, ma, CoreMathSupport::reduceLargeAcc);
        long mutLarge = mismatchesLarge(xl, ml, CoreMathReductionCarryTest::mutantLarge);
        long mutAcc = mismatchesAcc(xa, ma, CoreMathReductionCarryTest::mutantAcc);

        StringBuilder sb = new StringBuilder(480);
        sb.append("reduceLarge vs BigInteger: mismatches ").append(badLarge).append(" of ").append(xl.length)
                .append(" inputs (exponents 1054..2046; model carries ").append(carriesLarge).append(", forced ")
                .append(forcedConfirmedLarge).append(" of ").append(forcedLarge[0]).append(")\n");
        sb.append("reduceLargeAcc vs BigInteger: mismatches ").append(badAcc).append(" of ").append(xa.length)
                .append(" inputs (exponents 996..2046; model carries ").append(carriesAcc).append(", forced ")
                .append(forcedConfirmedAcc).append(" of ").append(forcedAcc[0]).append("; neg=1 on ").append(negOnes).append(")\n");
        sb.append("control: reduceLarge without the magic carry rejected ").append(mutLarge).append(" (model carries ").append(carriesLarge)
                .append("); reduceLargeAcc without the vHigh carry rejected ").append(mutAcc).append(" (model carries ").append(carriesAcc).append(')');
        System.out.println(sb);

        assertEquals(0L, badLarge, sb.toString());
        assertEquals(0L, badAcc, sb.toString());
        assertEquals(forcedLarge[0], forcedConfirmedLarge, "a forced input of reduceLarge does not carry in the model");
        assertEquals(forcedAcc[0], forcedConfirmedAcc, "a forced input of reduceLargeAcc does not carry in the model");
        assertTrue(carriesLarge >= 993 && carriesAcc >= 1051, sb.toString());
        assertTrue(negOnes >= xa.length / 10 && negOnes <= xa.length - xa.length / 10, new StringBuilder(64).append("both signs of neg must occur: ").append(negOnes).toString());
        assertEquals(carriesLarge, mutLarge, "the dropped-carry control must be rejected exactly where the model has a carry");
        assertEquals(carriesAcc, mutAcc, "the dropped-carry control must be rejected exactly where the model has a carry");
    }
}
