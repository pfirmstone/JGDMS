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

import java.math.BigInteger;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

/**
 * The 192-bit arithmetic of {@link CoreMathTint} (the port of CORE-MATH's {@code tint.h}) against exact
 * {@link BigInteger} models written from the C text, with controls (the same models reject copies of the
 * arithmetic with a carry or a borrow dropped). Hermetic, no clone and no oracle; runtime tier: FAST (default suite).
 * <p>
 * Phase A's reviews (Opus MEDIUM-2 / Fable M2) found that inline 128-bit carries tested only by populations are
 * invisible to a random test: this class is the answer for the 192-bit routines, with inputs chosen to force every
 * carry and every shift count.
 *
 * <h2>SEALED PREDICTIONS (phase B1, B1-P11; written before the first run of this test)</h2>
 * Population, source and scoring rule: for each routine, N random and forced inputs (seeds in the code), the Java
 * result against the model's, field by field ({@code h, m, l, ex, sgn}, or the bits of a double); a mismatch is any
 * difference. N is printed by each assertion line.
 * <ol>
 *   <li><b>multiply</b> over 300,000 inputs (mixed shapes: random, low words zero, low words all ones, all ones): 0
 *       mismatches with the model {@code ah*bh*2^64 + ah*bm + am*bh + floor(ah*bl/2^64) + floor(am*bm/2^64) + floor(al*bh/2^64)},
 *       normalised once. Control: the copy that never adds the carry word {@code cm} differs from the model on exactly the
 *       inputs whose model carry is non-zero (the count is the model's own and is printed); the copy with the same code and
 *       no mutation equals the production routine on all inputs (so the copy is the right copy); the copies that drop one
 *       of the two {@code m -> h} carries differ on at least one input each.</li>
 *   <li><b>shifts</b>: {@code rshift} and {@code lshift} equal {@code (M >> k)} and {@code (M << k) mod 2^192} for every k in 0..200
 *       on 2,000 random mantissas per k: 0 mismatches of 804,000.</li>
 *   <li><b>add</b> over 400,000 inputs (random exponent gaps including 0, 1, 2, 63, 64, 65, 127, 128, 129, 190-193 and
 *       larger; both sign combinations; forced cancellations of 2..190 bits; equal magnitudes; zero operands): 0 mismatches with
 *       the model written from tint.h:255-341. Controls: the addition without the carry out of the low word differs from the
 *       model on exactly the inputs where that carry occurs (the model's own count); the subtraction at {@code sh = 0} without
 *       the borrow out of the low word differs on exactly the inputs where {@code a.l < b.l} (the model's own count).</li>
 *   <li><b>fromD</b>: 0 mismatches over 100,000 doubles (normal and subnormal, both signs) with the exact model
 *       {@code |x| = n * 2^k, ex = k + bitLength(n), M = n << (192 - bitLength(n))}.</li>
 *   <li><b>toD</b>: for 300,000 inputs (random, and forced onto rounding boundaries and midpoints so that the C's failure
 *       conditions occur), the result is the round-to-nearest-even double of the exact value except where the model's
 *       failure condition holds, where it is NaN: 0 mismatches, and the number of NaN results equals the model's number of
 *       failure conditions (both printed). Covers exponents -1073..1024 and the out-of-range exponents.</li>
 *   <li><b>div</b>: over 3,000 random quotients the relative error {@code |r*a - b|/|b|} is below 2^-185.53 (the C's claim,
 *       tint.h:495-533), 0 violations; the control, the quotient without the Karp-Markstein correction, violates the bound on
 *       at least 99% of the inputs.</li>
 * </ol>
 * ELSE-BRANCH (all): a mismatch is a defect of the port or of the model; it is reported with the operands, the comparison is
 * not loosened, and the primary reading stays "not yet correct". A control that rejects fewer inputs than predicted voids the
 * "0 mismatches" it guards.
 */
class CoreMathTintTest {

    private static final BigInteger MASK64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private static final BigInteger MASK192 = BigInteger.ONE.shiftLeft(192).subtract(BigInteger.ONE);

    private static BigInteger u(long v) {
        return new BigInteger(Long.toUnsignedString(v));
    }

    private static BigInteger mant(CoreMathTint.Tint t) {
        return u(t.h).shiftLeft(128).or(u(t.m).shiftLeft(64)).or(u(t.l));
    }

    private static CoreMathTint.Tint tint(BigInteger m, long ex, long sgn) {
        CoreMathTint.Tint t = new CoreMathTint.Tint();
        t.h = m.shiftRight(128).and(MASK64).longValue();
        t.m = m.shiftRight(64).and(MASK64).longValue();
        t.l = m.and(MASK64).longValue();
        t.ex = ex;
        t.sgn = sgn;
        return t;
    }

    private static CoreMathTint.Tint copy(CoreMathTint.Tint a) {
        CoreMathTint.Tint t = new CoreMathTint.Tint();
        CoreMathTint.cp(t, a);
        return t;
    }

    private static boolean same(CoreMathTint.Tint a, BigInteger m, long ex, long sgn) {
        return mant(a).equals(m) && a.ex == ex && a.sgn == sgn;
    }

    private static String show(CoreMathTint.Tint a) {
        return new StringBuilder(120).append("{h=0x").append(Long.toHexString(a.h)).append(" m=0x").append(Long.toHexString(a.m)).append(" l=0x")
                .append(Long.toHexString(a.l)).append(" ex=").append(a.ex).append(" sgn=").append(a.sgn).append('}').toString();
    }

    /** A random normalised mantissa of one of several shapes; the shapes put the carries where random bits rarely do. */
    private static BigInteger shape(SplittableRandom r, int kind) {
        long h = r.nextLong() | Long.MIN_VALUE;
        long m = r.nextLong();
        long l = r.nextLong();
        switch (kind) {
            case 1:
                m = 0L;
                l = 0L;
                break;
            case 2:
                m = -1L;
                l = -1L;
                break;
            case 3:
                h = -1L;
                m = -1L;
                l = -1L;
                break;
            case 4:
                m = r.nextBoolean() ? -1L : 0L;
                break;
            case 5:
                h = Long.MIN_VALUE;
                break;
            case 6:
                l = -1L - (r.nextLong() & 0xffL);
                m = -1L - (r.nextLong() & 0xffL);
                break;
            case 7:
                l = r.nextLong() & 0xffL;
                m = r.nextLong() & 0xffL;
                break;
            default:
                break;
        }
        return u(h).shiftLeft(128).or(u(m).shiftLeft(64)).or(u(l));
    }

    // ----------------------------------------------------------------------------------------
    // multiply
    // ----------------------------------------------------------------------------------------

    /** The exact value of the truncated product, normalised once: {M, ex, sgn}. */
    private static Object[] mulModel(CoreMathTint.Tint a, CoreMathTint.Tint b) {
        BigInteger ah = u(a.h);
        BigInteger am = u(a.m);
        BigInteger al = u(a.l);
        BigInteger bh = u(b.h);
        BigInteger bm = u(b.m);
        BigInteger bl = u(b.l);
        BigInteger r = ah.multiply(bh).shiftLeft(64).add(ah.multiply(bm)).add(am.multiply(bh))
                .add(ah.multiply(bl).shiftRight(64)).add(am.multiply(bm).shiftRight(64)).add(al.multiply(bh).shiftRight(64));
        long ex = a.ex + b.ex;
        if (!r.testBit(191)) {
            r = r.shiftLeft(1);
            ex--;
        }
        return new Object[] {r, ex, a.sgn ^ b.sgn, carryOfLowWord(a, b)};
    }

    /** The model's {@code cm}: the carry out of the low word, which the C adds into the middle word. */
    private static BigInteger carryOfLowWord(CoreMathTint.Tint a, CoreMathTint.Tint b) {
        BigInteger ah = u(a.h);
        BigInteger am = u(a.m);
        BigInteger al = u(a.l);
        BigInteger bh = u(b.h);
        BigInteger bm = u(b.m);
        BigInteger bl = u(b.l);
        BigInteger low = ah.multiply(bm).and(MASK64).add(am.multiply(bh).and(MASK64))
                .add(ah.multiply(bl).shiftRight(64)).add(am.multiply(bm).shiftRight(64)).add(al.multiply(bh).shiftRight(64));
        return low.shiftRight(64);
    }

    /**
     * A copy of {@link CoreMathTint#mul} with one carry dropped, for the controls. {@code mutation} 0: none (must equal the
     * production routine); 1: {@code cm} never added into {@code m}; 2: the carry of the first {@code m} accumulation not added
     * to {@code h}; 3: the final {@code m -> h} carry (of adding {@code cm}) dropped.
     */
    private static void mulCopy(CoreMathTint.Tint r, CoreMathTint.Tint a, CoreMathTint.Tint b, int mutation) {
        long ex = a.ex + b.ex;
        long sgn = a.sgn ^ b.sgn;
        long ah = a.h;
        long am = a.m;
        long al = a.l;
        long bh = b.h;
        long bm = b.m;
        long bl = b.l;
        long rH = Math.unsignedMultiplyHigh(ah, bh);
        long rM = ah * bh;
        long rL = ah * bm;
        long h = Math.unsignedMultiplyHigh(ah, bm);
        rM += h;
        rH += (mutation != 2 && Long.compareUnsigned(rM, h) < 0) ? 1L : 0L;
        long l = am * bh;
        h = Math.unsignedMultiplyHigh(am, bh);
        rL += l;
        long cm = Long.compareUnsigned(rL, l) < 0 ? 1L : 0L;
        rM += h;
        rH += Long.compareUnsigned(rM, h) < 0 ? 1L : 0L;
        long s1 = Math.unsignedMultiplyHigh(ah, bl);
        long s2 = Math.unsignedMultiplyHigh(am, bm);
        long s3 = Math.unsignedMultiplyHigh(al, bh);
        long s12 = s1 + s2;
        long carry = Long.compareUnsigned(s12, s1) < 0 ? 1L : 0L;
        long s123 = s12 + s3;
        carry += Long.compareUnsigned(s123, s12) < 0 ? 1L : 0L;
        l = s123;
        cm += carry;
        rL += l;
        cm += Long.compareUnsigned(rL, l) < 0 ? 1L : 0L;
        if (mutation != 1) {
            rM += cm;
            rH += (mutation != 3 && Long.compareUnsigned(rM, cm) < 0) ? 1L : 0L;
        }
        if ((rH >>> 63) == 0L) {
            rH = (rH << 1) | (rM >>> 63);
            rM = (rM << 1) | (rL >>> 63);
            rL = rL << 1;
            ex--;
        }
        r.ex = ex;
        r.sgn = sgn;
        r.h = rH;
        r.m = rM;
        r.l = rL;
    }

    @Test
    void multiplyEqualsTheExactModelAndTheControlsRejectDroppedCarries() {
        SplittableRandom rnd = new SplittableRandom(20261211L);
        int n = 300_000;
        long mismatches = 0;
        long modelCarryInputs = 0;
        long[] rejected = new long[4];
        long copyDiffersFromProduction = 0;
        String first = null;
        for (int i = 0; i < n; i++) {
            CoreMathTint.Tint a = tint(shape(rnd, rnd.nextInt(8)), rnd.nextInt(41) - 20, rnd.nextInt(2));
            CoreMathTint.Tint b = tint(shape(rnd, rnd.nextInt(8)), rnd.nextInt(41) - 20, rnd.nextInt(2));
            Object[] model = mulModel(a, b);
            CoreMathTint.Tint r = new CoreMathTint.Tint();
            CoreMathTint.mul(r, a, b);
            if (!same(r, (BigInteger) model[0], (Long) model[1], (Long) model[2])) {
                mismatches++;
                if (first == null) {
                    first = new StringBuilder(200).append("a=").append(show(a)).append(" b=").append(show(b)).append(" got ").append(show(r)).toString();
                }
            }
            boolean carry = ((BigInteger) model[3]).signum() != 0;
            if (carry) {
                modelCarryInputs++;
            }
            for (int mu = 0; mu < 4; mu++) {
                CoreMathTint.Tint c = new CoreMathTint.Tint();
                mulCopy(c, a, b, mu);
                boolean differs = !same(c, (BigInteger) model[0], (Long) model[1], (Long) model[2]);
                if (mu == 0) {
                    if (!mant(c).equals(mant(r)) || c.ex != r.ex || c.sgn != r.sgn) {
                        copyDiffersFromProduction++;
                    }
                } else if (differs) {
                    rejected[mu]++;
                }
            }
            // the result may overlap an operand, as in the C: mul(a, a, b) equals mul(r, a, b)
            CoreMathTint.Tint alias = copy(a);
            CoreMathTint.mul(alias, alias, b);
            assertTrue(same(alias, (BigInteger) model[0], (Long) model[1], (Long) model[2]), "overlap r = a");
        }
        System.out.println(new StringBuilder(200).append("multiply vs BigInteger model: ").append(mismatches).append(" mismatches of ").append(n)
                .append("; control (cm never added) rejected ").append(rejected[1]).append(" of ").append(n).append(", model carry inputs ").append(modelCarryInputs)
                .append("; other dropped-carry copies rejected ").append(rejected[2]).append(" and ").append(rejected[3])
                .append("; unmutated copy differs from production on ").append(copyDiffersFromProduction));
        assertEquals(0L, mismatches, first);
        assertEquals(0L, copyDiffersFromProduction);
        assertTrue(modelCarryInputs > 0L, "no input had a carry: the control is vacuous");
        assertEquals(modelCarryInputs, rejected[1]);
        assertTrue(rejected[2] >= 1L, "the first m -> h carry was never exercised");
        assertTrue(rejected[3] >= 1L, "the final m -> h carry was never exercised");
    }

    // ----------------------------------------------------------------------------------------
    // shifts
    // ----------------------------------------------------------------------------------------

    @Test
    void shiftsEqualTheModelForEveryCount() {
        SplittableRandom rnd = new SplittableRandom(20261212L);
        long mismatches = 0;
        long total = 0;
        for (int k = 0; k <= 200; k++) {
            for (int i = 0; i < 2000; i++) {
                BigInteger m = (i % 4 == 0) ? new BigInteger(192, new java.util.Random(rnd.nextLong())) : shape(rnd, rnd.nextInt(8));
                CoreMathTint.Tint b = tint(m, 0L, 0L);
                CoreMathTint.Tint a = new CoreMathTint.Tint();
                CoreMathTint.rshift(a, b, k);
                BigInteger wantR = k >= 192 ? BigInteger.ZERO : m.shiftRight(k);
                total++;
                if (!mant(a).equals(wantR)) {
                    mismatches++;
                }
                CoreMathTint.Tint c = new CoreMathTint.Tint();
                CoreMathTint.lshift(c, b, k);
                BigInteger wantL = k >= 192 ? BigInteger.ZERO : m.shiftLeft(k).and(MASK192);
                total++;
                if (!mant(c).equals(wantL)) {
                    mismatches++;
                }
            }
        }
        System.out.println(new StringBuilder(96).append("rshift/lshift vs BigInteger: ").append(mismatches).append(" mismatches of ").append(total));
        assertEquals(0L, mismatches);
        assertEquals(804_000L, total);
    }

    // ----------------------------------------------------------------------------------------
    // add
    // ----------------------------------------------------------------------------------------

    private static final class V {
        final BigInteger m;
        final long ex;
        final long sgn;

        V(BigInteger m, long ex, long sgn) {
            this.m = m;
            this.ex = ex;
            this.sgn = sgn;
        }
    }

    private static int cmpAbsModel(CoreMathTint.Tint a, CoreMathTint.Tint b) {
        BigInteger ma = mant(a);
        BigInteger mb = mant(b);
        if (ma.signum() == 0) {
            return mb.signum() == 0 ? 0 : -1;
        }
        if (mb.signum() == 0) {
            return 1;
        }
        if (a.ex != b.ex) {
            return a.ex > b.ex ? 1 : -1;
        }
        return ma.compareTo(mb);
    }

    /** The result of add_tint, from the C text (tint.h:255-341), in BigInteger. */
    private static V addModel(CoreMathTint.Tint a0, CoreMathTint.Tint b0) {
        CoreMathTint.Tint a = a0;
        CoreMathTint.Tint b = b0;
        int c = cmpAbsModel(a, b);
        if (c == 0) {
            if (a.sgn != b.sgn) {
                return new V(BigInteger.ZERO, -1076L, 0L);
            }
            return new V(mant(a), a.ex + 1, a.sgn);
        }
        if (c < 0) {
            CoreMathTint.Tint t = a;
            a = b;
            b = t;
        }
        long sh = a.ex - b.ex;
        BigInteger mb = mant(b);
        BigInteger t = sh >= 192 ? BigInteger.ZERO : mb.shiftRight((int) sh);
        BigInteger ma = mant(a);
        if (a.sgn != b.sgn) {
            BigInteger d = ma.subtract(t);
            int ex = 192 - d.bitLength();
            if (ex <= 1 || sh == 0) {
                return new V(d.shiftLeft(ex).and(MASK192), a.ex - ex, a.sgn);
            }
            BigInteger t2 = mb.shiftLeft(ex - (int) sh).and(MASK192);
            BigInteger a2 = ma.shiftLeft(ex).and(MASK192);
            BigInteger d2 = a2.subtract(t2).and(MASK192);
            int ex1 = 192 - d2.bitLength();
            return new V(d2.shiftLeft(ex1).and(MASK192), a.ex - (ex + ex1), a.sgn);
        }
        BigInteger s = ma.add(t);
        if (s.bitLength() > 192) {
            return new V(s.shiftRight(1), a.ex + 1, a.sgn);
        }
        return new V(s, a.ex, a.sgn);
    }

    /** Same-sign addition of {@code |a| > |b|} with the carry out of the low word dropped (control). */
    private static BigInteger addNoLowCarry(CoreMathTint.Tint a, CoreMathTint.Tint b) {
        long sh = a.ex - b.ex;
        CoreMathTint.Tint t = new CoreMathTint.Tint();
        CoreMathTint.rshift(t, b, (int) sh);
        long rl = a.l + t.l;                                         // cl dropped: the carry is not added into the 128-bit part
        BigInteger hm = u(a.h).shiftLeft(64).or(u(a.m)).add(u(t.h).shiftLeft(64).or(u(t.m)));
        BigInteger s = hm.shiftLeft(64).or(u(rl));
        if (s.bitLength() > 192) {                                   // a carry out of the 128-bit part
            return s.shiftRight(1);
        }
        return s;
    }

    /** Opposite-sign subtraction at {@code sh = 0} with the borrow out of the low word dropped (control). */
    private static BigInteger subNoBorrow(CoreMathTint.Tint a, CoreMathTint.Tint b) {
        long rl = a.l - b.l;
        BigInteger hm = u(a.h).shiftLeft(64).or(u(a.m)).subtract(u(b.h).shiftLeft(64).or(u(b.m)));
        BigInteger d = hm.shiftLeft(64).or(u(rl));
        int ex = 192 - d.bitLength();
        return d.shiftLeft(ex).and(MASK192);
    }

    @Test
    void addEqualsTheExactModelAndTheControlsRejectDroppedCarries() {
        SplittableRandom rnd = new SplittableRandom(20261213L);
        int n = 400_000;
        long mismatches = 0;
        long overlapMismatches = 0;
        long lowCarryInputs = 0;
        long lowCarryRejected = 0;
        long borrowInputs = 0;
        long borrowRejected = 0;
        long cancellations = 0;
        long ex1Branch = 0;
        long carryOutOfAdd = 0;
        String first = null;
        int[] gaps = {0, 1, 2, 3, 5, 63, 64, 65, 100, 127, 128, 129, 150, 190, 191, 192, 193, 250, 400};
        for (int i = 0; i < n; i++) {
            long exA = rnd.nextInt(2001) - 1000;
            long sgnA = rnd.nextInt(2);
            long sgnB = rnd.nextInt(2);
            CoreMathTint.Tint a;
            CoreMathTint.Tint b;
            int mode = i % 10;
            if (mode == 0) {
                // forced cancellation: opposite signs, exponents one apart, mantissas such that a - b/2 is small
                BigInteger small = new BigInteger(rnd.nextInt(188) + 1, new java.util.Random(rnd.nextLong()));
                BigInteger ma = BigInteger.ONE.shiftLeft(191).add(small);
                BigInteger r = small.shiftLeft(1).add(BigInteger.ONE).add(new BigInteger(rnd.nextInt(8) + 1, new java.util.Random(rnd.nextLong())));
                BigInteger mb = ma.shiftLeft(1).subtract(r);
                if (mb.bitLength() != 192) {
                    mb = BigInteger.ONE.shiftLeft(192).subtract(BigInteger.ONE);
                }
                a = tint(ma, exA, sgnA);
                b = tint(mb, exA - 1, sgnA ^ 1);
                cancellations++;
            } else if (mode == 1) {
                // same exponent, mantissas close: a cancellation at sh = 0
                BigInteger ma = shape(rnd, rnd.nextInt(8));
                BigInteger mb = ma.subtract(new BigInteger(rnd.nextInt(100) + 1, new java.util.Random(rnd.nextLong())));
                if (mb.bitLength() != 192) {
                    mb = ma;
                }
                a = tint(ma, exA, sgnA);
                b = tint(mb, exA, sgnA ^ 1);
            } else if (mode == 2) {
                a = tint(shape(rnd, rnd.nextInt(8)), exA, sgnA);
                b = copy(a);
                b.sgn = sgnB;                                        // equal magnitudes
            } else if (mode == 3 && rnd.nextInt(4) == 0) {
                a = tint(BigInteger.ZERO, -1076L, sgnA);              // a zero operand
                b = tint(shape(rnd, rnd.nextInt(8)), exA, sgnB);
                if (rnd.nextBoolean()) {
                    CoreMathTint.Tint t = a;
                    a = b;
                    b = t;
                }
            } else {
                a = tint(shape(rnd, rnd.nextInt(8)), exA, sgnA);
                long gap = gaps[rnd.nextInt(gaps.length)] + (rnd.nextInt(3) == 0 ? rnd.nextInt(4) : 0);
                b = tint(shape(rnd, rnd.nextInt(8)), exA - (rnd.nextBoolean() ? gap : -gap), sgnB);
            }
            V want = addModel(a, b);
            CoreMathTint.Tint r = new CoreMathTint.Tint();
            CoreMathTint.add(r, a, b);
            if (!same(r, want.m, want.ex, want.sgn)) {
                mismatches++;
                if (first == null) {
                    first = new StringBuilder(240).append("a=").append(show(a)).append(" b=").append(show(b)).append(" got ").append(show(r))
                            .append(" want ex=").append(want.ex).append(" sgn=").append(want.sgn).append(" m=").append(want.m.toString(16)).toString();
                }
            }
            // the result may overlap either operand, as in the C
            CoreMathTint.Tint ra = copy(a);
            CoreMathTint.add(ra, ra, b);
            CoreMathTint.Tint rb = copy(b);
            CoreMathTint.add(rb, a, rb);
            if (!same(ra, want.m, want.ex, want.sgn) || !same(rb, want.m, want.ex, want.sgn)) {
                overlapMismatches++;
            }
            // controls, on the inputs where the dropped carry could matter and the branch is the one the control copies
            int cmp = cmpAbsModel(a, b);
            if (cmp != 0) {
                CoreMathTint.Tint big = cmp > 0 ? a : b;
                CoreMathTint.Tint small = cmp > 0 ? b : a;
                long sh = big.ex - small.ex;
                if (big.sgn == small.sgn && mant(big).signum() != 0) {
                    CoreMathTint.Tint t = new CoreMathTint.Tint();
                    CoreMathTint.rshift(t, small, (int) sh);
                    boolean lowCarry = Long.compareUnsigned(big.l + t.l, big.l) < 0;
                    if (lowCarry) {
                        lowCarryInputs++;
                        BigInteger got = addNoLowCarry(big, small);
                        BigInteger expectM = want.m;
                        if (!got.equals(expectM)) {
                            lowCarryRejected++;
                        }
                    }
                } else if (big.sgn != small.sgn && sh == 0L) {
                    boolean borrow = Long.compareUnsigned(big.l, small.l) < 0;
                    if (borrow) {
                        borrowInputs++;
                        BigInteger got = subNoBorrow(big, small);
                        if (!got.equals(want.m)) {
                            borrowRejected++;
                        }
                    }
                }
                if (big.sgn != small.sgn && sh != 0L) {
                    BigInteger d = mant(big).subtract(sh >= 192 ? BigInteger.ZERO : mant(small).shiftRight((int) sh));
                    if (192 - d.bitLength() >= 2) {
                        ex1Branch++;
                    }
                }
                if (big.sgn == small.sgn) {
                    BigInteger t = sh >= 192 ? BigInteger.ZERO : mant(small).shiftRight((int) sh);
                    if (mant(big).add(t).bitLength() > 192) {
                        carryOutOfAdd++;
                    }
                }
            }
        }
        System.out.println(new StringBuilder(260).append("add vs BigInteger model: ").append(mismatches).append(" mismatches of ").append(n).append("; overlap (r = a, r = b) mismatches ")
                .append(overlapMismatches).append("; forced cancellation inputs ").append(cancellations).append(", of which the ex >= 2 branch ran on ").append(ex1Branch)
                .append(" subtractions; additions with a carry out ").append(carryOutOfAdd).append("; control (no low-word carry) rejected ").append(lowCarryRejected)
                .append(" of ").append(lowCarryInputs).append(" model low-word carries; control (no borrow at sh = 0) rejected ").append(borrowRejected).append(" of ").append(borrowInputs));
        assertEquals(0L, mismatches, first);
        assertEquals(0L, overlapMismatches);
        assertTrue(lowCarryInputs > 0L && borrowInputs > 0L, "a control was vacuous");
        assertEquals(lowCarryInputs, lowCarryRejected);
        assertEquals(borrowInputs, borrowRejected);
        assertTrue(ex1Branch > 1000L, "the second subtraction branch (ex >= 2, sh >= 1) was barely exercised");
        assertTrue(carryOutOfAdd > 1000L, "the carry out of the 192-bit addition was barely exercised");
    }

    // ----------------------------------------------------------------------------------------
    // fromD and toD
    // ----------------------------------------------------------------------------------------

    @Test
    void fromDEqualsTheExactModel() {
        SplittableRandom rnd = new SplittableRandom(20261214L);
        int n = 100_000;
        long mismatches = 0;
        long roundTrip = 0;
        for (int i = 0; i < n; i++) {
            long bits;
            if (i % 5 == 0) {
                bits = rnd.nextLong() & 0x800fffffffffffffL;           // subnormal: exponent field 0
                if ((bits & 0x7fffffffffffffffL) == 0L) {
                    bits |= 1L;
                }
            } else {
                bits = rnd.nextLong() & 0x800fffffffffffffL | ((long) (1 + rnd.nextInt(2046)) << 52);
            }
            double x = Double.longBitsToDouble(bits);
            CoreMathTint.Tint a = new CoreMathTint.Tint();
            CoreMathTint.fromD(a, x);
            long frac = bits & 0xfffffffffffffL;
            int e = (int) ((bits >>> 52) & 0x7ff);
            long nn = (e == 0) ? frac : (frac | (1L << 52));
            int k = (e == 0) ? -1074 : e - 1075;
            BigInteger bn = BigInteger.valueOf(nn);
            int bl = bn.bitLength();
            BigInteger wantM = bn.shiftLeft(192 - bl);
            if (!same(a, wantM, k + bl, bits >>> 63)) {
                mismatches++;
            }
            if (Double.doubleToRawLongBits(CoreMathTint.toD(a, 0L)) != bits) {
                roundTrip++;
            }
        }
        System.out.println(new StringBuilder(80).append("fromD vs exact model: ").append(mismatches).append(" mismatches of ").append(n).append("; round trip toD(fromD(x), 0) != x on ").append(roundTrip));
        assertEquals(0L, mismatches);
        assertEquals(0L, roundTrip);
    }

    private static double roundToNearestEven(BigInteger m, long ex, long sgn) {
        // value = m * 2^(ex-192), m normalised (bitLength 192): the leading bit has weight 2^(ex-1)
        int e = (int) (ex - 1);
        double sign = sgn != 0L ? -1.0 : 1.0;
        if (e >= 1024) {
            return sign * Double.POSITIVE_INFINITY;
        }
        int prec = (e >= -1022) ? 53 : 1075 + e;
        if (prec < 0) {
            return sign * 0.0;
        }
        if (prec == 0) {
            // value in [2^-1075, 2^-1074): exactly 2^-1075 is a tie (to the even zero), more rounds up to 2^-1074
            return m.equals(BigInteger.ONE.shiftLeft(191)) ? sign * 0.0 : sign * Double.MIN_VALUE;
        }
        int shift = 192 - prec;
        BigInteger q = m.shiftRight(shift);
        BigInteger rem = m.and(BigInteger.ONE.shiftLeft(shift).subtract(BigInteger.ONE));
        BigInteger half = BigInteger.ONE.shiftLeft(shift - 1);
        int c = rem.compareTo(half);
        if (c > 0 || (c == 0 && q.testBit(0))) {
            q = q.add(BigInteger.ONE);
        }
        return sign * Math.scalb(q.doubleValue(), e - prec + 1);
    }

    private static boolean failureModel(BigInteger m, long err) {
        long h = m.shiftRight(128).and(MASK64).longValue();
        long mm = m.shiftRight(64).and(MASK64).longValue();
        long ll = m.and(MASK64).longValue();
        long low = h & 0x7ffL;
        return (mm == 0L && (low == 0L || low == 0x400L) && Long.compareUnsigned(ll, err) < 0)
                || (~mm == 0L && (low == 0x3ffL || low == 0x7ffL) && Long.compareUnsigned(~ll, err) < 0);
    }

    @Test
    void toDEqualsTheRoundToNearestEvenModelAndReturnsNaNExactlyWhereTheCFails() {
        SplittableRandom rnd = new SplittableRandom(20261215L);
        int n = 300_000;
        long mismatches = 0;
        long modelFailures = 0;
        long nanResults = 0;
        long subnormalInputs = 0;
        String first = null;
        long[] errs = {1L, 266L, 524L, 662L};              // err = 0 converts an exact double and is tested by the round trip in fromD
        for (int i = 0; i < n; i++) {
            long ex = i % 7 == 0 ? -1073L - rnd.nextInt(2) + rnd.nextInt(60) : (rnd.nextInt(2098) - 1073);   // mostly -1073..1024
            long err = errs[rnd.nextInt(errs.length)];
            BigInteger m;
            int kind = i % 6;
            if (kind == 0) {
                m = shape(rnd, rnd.nextInt(8));
            } else if (kind == 1) {
                // on a rounding boundary: the top 53 bits random, the rest zero except the last word
                BigInteger q = BigInteger.ONE.shiftLeft(52).or(new BigInteger(52, new java.util.Random(rnd.nextLong())));
                m = q.shiftLeft(139).or(BigInteger.valueOf(rnd.nextInt(3) == 0 ? err : rnd.nextInt(1000)));
            } else if (kind == 2) {
                // exactly on a midpoint: the round bit set, everything below it zero except the last word
                BigInteger q = BigInteger.ONE.shiftLeft(52).or(new BigInteger(52, new java.util.Random(rnd.nextLong())));
                m = q.shiftLeft(139).or(BigInteger.ONE.shiftLeft(138)).or(BigInteger.valueOf(rnd.nextInt(3) == 0 ? err : rnd.nextInt(1000)));
            } else if (kind == 3) {
                // just below a boundary: all ones below the top 53 bits except the last word
                BigInteger q = BigInteger.ONE.shiftLeft(52).or(new BigInteger(52, new java.util.Random(rnd.nextLong())));
                BigInteger low = BigInteger.ONE.shiftLeft(139).subtract(BigInteger.ONE).subtract(BigInteger.valueOf(rnd.nextInt(3) == 0 ? err : rnd.nextInt(1000)));
                m = q.shiftLeft(139).or(low);
            } else if (kind == 4) {
                // just below a midpoint
                BigInteger q = BigInteger.ONE.shiftLeft(52).or(new BigInteger(52, new java.util.Random(rnd.nextLong())));
                BigInteger low = BigInteger.ONE.shiftLeft(138).subtract(BigInteger.ONE).subtract(BigInteger.valueOf(rnd.nextInt(3) == 0 ? err : rnd.nextInt(1000)));
                m = q.shiftLeft(139).or(low);
            } else {
                m = BigInteger.ONE.shiftLeft(191).or(BigInteger.valueOf(rnd.nextInt(3) == 0 ? err : rnd.nextInt(5)));   // a power of two, plus a little
            }
            long sgn = rnd.nextInt(2);
            CoreMathTint.Tint a = tint(m, ex, sgn);
            double got = CoreMathTint.toD(a, err);
            boolean fail = failureModel(m, err) && ex > -1074 && ex < 1025;
            if (ex <= -1022 && ex > -1074) {
                subnormalInputs++;
            }
            double want = fail ? Double.NaN : roundToNearestEven(m, ex, sgn);
            if (fail) {
                modelFailures++;
            }
            if (Double.isNaN(got)) {
                nanResults++;
            }
            boolean ok = fail ? Double.isNaN(got) : (!Double.isNaN(got) && Double.doubleToRawLongBits(got) == Double.doubleToRawLongBits(want));
            if (!ok) {
                mismatches++;
                if (first == null) {
                    first = new StringBuilder(200).append("a=").append(show(a)).append(" err=").append(err).append(" got ").append(Double.toHexString(got))
                            .append(" want ").append(Double.toHexString(want)).toString();
                }
            }
        }
        System.out.println(new StringBuilder(220).append("toD vs round-to-nearest-even model: ").append(mismatches).append(" mismatches of ").append(n)
                .append("; NaN results ").append(nanResults).append(" = model failure conditions ").append(modelFailures)
                .append("; subnormal-range inputs ").append(subnormalInputs));
        assertEquals(0L, mismatches, first);
        assertEquals(modelFailures, nanResults);
        assertTrue(modelFailures > 1000L, "the failure condition was barely exercised");
        assertTrue(subnormalInputs > 1000L, "the subnormal path was barely exercised");
    }

    // ----------------------------------------------------------------------------------------
    // div
    // ----------------------------------------------------------------------------------------

    /** True iff {@code |r*a - b| / |b| < 2^-185.53} (the C's claim for div_tint), exactly. */
    private static boolean withinTheClaimedBound(CoreMathTint.Tint r, CoreMathTint.Tint a, CoreMathTint.Tint b) {
        // r*a - b, exactly, with the exponents aligned
        BigInteger ra = mant(r).multiply(mant(a));                   // exponent r.ex + a.ex - 384
        long e1 = r.ex + a.ex - 384;
        BigInteger bm = mant(b);                                      // exponent b.ex - 192
        long e2 = b.ex - 192;
        long emin = Math.min(e1, e2);
        BigInteger x = ra.shiftLeft((int) (e1 - emin));
        BigInteger y = bm.shiftLeft((int) (e2 - emin));
        if ((r.sgn ^ a.sgn) != b.sgn) {
            y = y.negate();
        }
        BigInteger d = x.subtract(y).abs();
        // |d| / |y| < 2^-185.53  <=>  |d| * 2^186 * 1000 < 1385 * |y|   (2^0.47 = 1.385)
        return d.shiftLeft(186).multiply(BigInteger.valueOf(1000)).compareTo(y.abs().multiply(BigInteger.valueOf(1385))) < 0;
    }

    @Test
    void divisionMeetsTheClaimedRelativeErrorAndTheControlWithoutTheCorrectionDoesNot() {
        SplittableRandom rnd = new SplittableRandom(20261216L);
        int n = 3000;
        long violations = 0;
        long controlViolations = 0;
        for (int i = 0; i < n; i++) {
            CoreMathTint.Tint a = tint(shape(rnd, rnd.nextInt(8)), rnd.nextInt(2001) - 1000, rnd.nextInt(2));
            CoreMathTint.Tint b = tint(shape(rnd, rnd.nextInt(8)), rnd.nextInt(2001) - 1000, rnd.nextInt(2));
            CoreMathTint.Tint r = new CoreMathTint.Tint();
            CoreMathTint.div(r, b, a);
            if (!withinTheClaimedBound(r, a, b)) {
                violations++;
            }
            // control: b/a by r = (1/a)*b only, without the Karp-Markstein correction
            CoreMathTint.Tint y = new CoreMathTint.Tint();
            CoreMathTint.inv(y, a);
            CoreMathTint.Tint c = new CoreMathTint.Tint();
            CoreMathTint.mul(c, y, b);
            if (!withinTheClaimedBound(c, a, b)) {
                controlViolations++;
            }
        }
        System.out.println(new StringBuilder(160).append("div: ").append(violations).append(" of ").append(n).append(" quotients outside the claimed relative error 2^-185.53; control (no correction) outside on ")
                .append(controlViolations).append(" of ").append(n));
        assertEquals(0L, violations);
        assertTrue(controlViolations >= n * 99L / 100L, "the bound check cannot fail: the uncorrected quotient passed it");
    }
}
