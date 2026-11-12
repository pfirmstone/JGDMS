/*
 * CoreMathSupport: Java port of the helper routines of CORE-MATH's binary64 sine,
 * src/binary64/sin/sin.c at commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7 (the same
 * routines appear, with the same text, in src/binary64/cos/cos.c, which says it is adapted from
 * sin.c; both files' copyright lines are therefore carried).
 *
 * DERIVATION: this file is a Java port, that is a modified and derived work, of routines in
 * src/binary64/sin/sin.c (repeated in src/binary64/cos/cos.c) of the CORE-MATH project at commit
 * 018fa97cbdacf59924b899a9dfa422d532cb2bb7, plus the small Java helpers that stand in for C
 * features Java lacks. It is not part of CORE-MATH and is not endorsed by its authors. The
 * copyright and permission notice below are the upstream's, reproduced without change.
 *
 * Copyright (c) 2026 Paul Zimmermann and Alexei Sibidanov
 * Copyright (c) 2026 Paul Zimmermann
 *
 * This file is part of the CORE-MATH project
 * (https://core-math.gitlabpages.inria.fr/).
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package au.net.zeus.jgdms.cel.math;

/**
 * The helper routines of CORE-MATH's binary64 {@code sin} and {@code cos} that both ports use: the
 * double-double primitives, the Payne-Hanek reduction for large arguments (fast and accurate
 * variants), the fixed-point polynomial evaluators of the accurate path, and the conversion of a
 * 128-bit fixed-point result to a {@code double}.
 * <p>
 * <b>What changed from the C, and why.</b> C returns two values through a pointer
 * ({@code fasttwosum}, {@code fastsum}, {@code muldd}, {@code reduce_large},
 * {@code reduce_large_acc}); Java cannot, so the double-double primitives are split into the
 * high word (written inline by the caller, in the text of the C expression) and a method for the
 * low word, and the reductions return a small value object. {@code unsigned __int128} is
 * {@link U128}. Nothing else is changed; in particular no arithmetic is reassociated, so each
 * Java expression rounds exactly where the C expression does. The one deliberate difference in the
 * arithmetic is that Java never contracts {@code a*b+c} into a fused multiply-add on its own;
 * the C compiler may. The fused operations the C writes explicitly ({@code __builtin_fma}) are
 * {@link Math#fma}, which Java defines to round once, exactly as the C builtin does.
 */
final class CoreMathSupport {

    private CoreMathSupport() {}

    // ------------------------------------------------------------------------------------------
    // Constants both ports use (C: declared separately in cr_sin_moderate/cr_sin_large and in
    // cr_cos_moderate/cr_cos_large). One definition here: an edit of an error bound in one port
    // alone would otherwise diverge silently. The per-C-file method bodies stay in CoreMathSin and
    // CoreMathCos, one Java method per C function, so a reviewer can lay each beside its C text.
    // ------------------------------------------------------------------------------------------

    /** C: sin.c:886 and cos.c:834 {@code invpi}: |invpi/2^14 - 1/pi| < 2^-55.496. */
    static final double INVPI = 0x1.45f306dc9c883p+12;

    /**
     * C: sin.c:914 and cos.c:866 {@code eps}: the ABSOLUTE error bound of the moderate fast path,
     * 0x1.dep-64 = 1.867 * 2^-64, about 2^-63.1. Because it is absolute, the fast path cannot decide
     * a result whose ulp is smaller than about twice this (small {@code sin(x)}).
     */
    static final double EPS_MODERATE = 0x1.dep-64;

    /** C: sin.c:948 and cos.c:936 {@code eps}: the absolute error bound of the large fast path, 0x1.01p-63. */
    static final double EPS_LARGE = 0x1.01p-63;

    /** C: {@code Sgn[] = {1.0, -1.0}} (sin.c:911, :944; cos.c:863, :932): the factor {@code Sgn[sbit]}. */
    static double signMultiplier(int sbit) {
        return (sbit == 0) ? 1.0 : -1.0;
    }

    // ------------------------------------------------------------------------------------------
    // Double-double primitives (C: sin.c:264-288)
    // ------------------------------------------------------------------------------------------

    /**
     * Low word of {@code fasttwosum(x, y, &e)} (sin.c:264-268): given {@code s = x + y} (the high
     * word, which the caller computes), returns the C's {@code *e = y - (s - x)}.
     */
    static double fastTwoSumLow(double x, double y, double s) {
        double z = s - x;
        return y - z;
    }

    /**
     * Low word of {@code muldd(xh, xl, ch, cl, &l)} (sin.c:276-288): given {@code ahhh = xh * ch}
     * (the high word, which the caller computes), returns the C's {@code *l}. The C has an
     * {@code #if 1} and an {@code #else} form; this is the {@code #if 1} form, the one that is
     * compiled.
     */
    static double mulddLow(double xh, double xl, double ch, double cl, double ahhh) {
        return (xh * cl + xl * ch) + Math.fma(xh, ch, -ahhh);
    }

    // ------------------------------------------------------------------------------------------
    // Payne-Hanek reduction for |x| >= 2^31
    // ------------------------------------------------------------------------------------------

    /**
     * C {@code reduce_large} (sin.c:228-262, identical in cos.c:875-909), the fast-path reduction of
     * a positive {@code x >= 2^31}. The C returns {@code k} and writes {@code r}; both are derived
     * from the 128-bit value {@code u} (after the C's rounding {@code magic} is added) that this
     * method returns. Use {@link #reduceLargeJ} for the C's return value and
     * {@link #reduceLargeR} for the C's {@code *r}.
     * <p>
     * The C's {@code (u128) m * u}, with {@code u = V0:V1} and {@code m < 2^53}, is computed mod 2^128
     * as {@code m*V1 + ((m*V0 mod 2^64) << 64)}; the full 128-bit {@code m*V1} needs the high half
     * of the 64x64 product, {@link Math#unsignedMultiplyHigh}.
     */
    static U128 reduceLarge(double x) {
        long bits = Double.doubleToRawLongBits(x);
        int e = (int) (bits >>> 52) & 0x7ff;                    // 1054 <= e <= 2046
        long m = (1L << 52) | (bits & 0xfffffffffffffL);
        int i = (e - 1011) / 64;                                 // C: i = ceil((e-1074)/64), 0 <= i <= 16
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
        // u = (u128) V1 | ((u128) V0 << 64);  u = (u128) m * u;   (mod 2^128)
        long lo = m * v1;
        long hi = Math.unsignedMultiplyHigh(m, v1) + m * v0;
        // u += magic, magic = ((u128) 1 << 112) + 0x810000000000000 (round r to nearest)
        long lo2 = lo + 0x810000000000000L;
        long carry = Long.compareUnsigned(lo2, lo) < 0 ? 1L : 0L;
        long hi2 = hi + (1L << 48) + carry;
        return U128.of(hi2, lo2);
    }

    /** C {@code return u >> 113} of {@code reduce_large}: the 15 high bits of {@code u}. */
    static long reduceLargeJ(U128 u) {
        return u.hi >>> 49;
    }

    /**
     * C {@code t.f = (uint64_t)((u << 15) >> 75); *r = t.f * 0x1p-68 - 0x1p-16;} of
     * {@code reduce_large}: {@code (u << 15) >> 75} is bits 112..60 of {@code u}, a 53-bit
     * integer, so the conversion to {@code double} is exact.
     */
    static double reduceLargeR(U128 u) {
        long t = ((u.hi << 15) | (u.lo >>> 49)) >>> 11;
        return (double) t * 0x1p-68 - 0x1p-16;
    }

    /** The three results of {@link #reduceLargeAcc}. */
    static final class AccReduction {
        /** C return value {@code k}: 13 bits. */
        final long k;
        /** C {@code *neg}: 0 if {@code r >= 0}, 1 if {@code r < 0}. */
        final int neg;
        /** C {@code *r}: {@code |r/2^128| <= 2^-14}. */
        final U128 r;

        AccReduction(long k, int neg, U128 r) {
            this.k = k;
            this.neg = neg;
            this.r = r;
        }
    }

    /**
     * C {@code reduce_large_acc} (sin.c:571-612; cos.c:680-721 differs only in omitting the
     * {@code & 0x7ff} on the exponent, which is a no-op there because cos passes {@code |x|}).
     * Valid for {@code |x| >= 2^-16} when called from sin and {@code |x| >= 2^-27} when called from
     * cos; the C's table index {@code i} is -1 for the smaller exponents and the zero first table
     * entry then supplies the missing high word.
     * <p>
     * The C's {@code (u128) m * u} is computed mod 2^128 as in {@link #reduceLarge}; its
     * {@code v = (u128) m * (u128) V2}, of which only {@code v >> 64} is used, is the high half
     * of the 64x64 product.
     */
    static AccReduction reduceLargeAcc(double x) {
        long bits = Double.doubleToRawLongBits(x);
        int e = (int) (bits >>> 52) & 0x7ff;
        long m = (1L << 52) | (bits & 0xfffffffffffffL);
        int i = -1 + (e - 947) / 64;                             // C: i = ceil((e-1074)/64), -1 <= i <= 16
        int f = (e - 1011) & 0x3f;
        long v0 = (i >= 0) ? CoreMathTables.t(i) : 0L;
        long v1 = CoreMathTables.t(i + 1);
        long v2 = CoreMathTables.t(i + 2);
        if (f != 0) {
            v0 = (v0 << f) | (v1 >>> (64 - f));
            v1 = (v1 << f) | (v2 >>> (64 - f));
            v2 = (v2 << f) | (CoreMathTables.t(i + 3) >>> (64 - f));
        }
        // u = (u128) V1 | ((u128) V0 << 64);  u = (u128) m * u;   (mod 2^128)
        long lo = m * v1;
        long hi = Math.unsignedMultiplyHigh(m, v1) + m * v0;
        // u += (m * (u128) V2) >> 64
        long vHigh = Math.unsignedMultiplyHigh(m, v2);
        long lo2 = lo + vHigh;
        long carry = Long.compareUnsigned(lo2, lo) < 0 ? 1L : 0L;
        long hi2 = hi + carry;
        long k = hi2 >>> 51;                                      // C: k = u >> (128-13)
        // C: u &= mask, mask = U128(0xffffffffffffffff, 0x7ffffffffffff) = 2^115 - 1 (ignore leading 13 bits)
        hi2 &= 0x7ffffffffffffL;
        int neg = (int) ((hi2 >>> 50) & 1L);                      // C: *neg = u >> 114 (u < 2^115 here)
        U128 u = U128.of(hi2, lo2);
        if (neg != 0) {
            // C: k = (k+1) & ((1ull<<13)-1); u = mask + 1 - u, and mask + 1 = 2^115
            k = (k + 1) & ((1L << 13) - 1);
            u = U128.of(1L << 51, 0L).sub(u);
        }
        return new AccReduction(k, neg, u);
    }

    // ------------------------------------------------------------------------------------------
    // Accurate-path polynomial evaluation (128-bit fixed point)
    // ------------------------------------------------------------------------------------------

    /**
     * C {@code evalPS} (sin.c:191-209, identical in cos.c:141-160): {@code Sr/2^128 ~ sin2pi(r)} for
     * {@code 0 <= r < 2^-14}; {@code u/2^128 ~ r' = 8r}, {@code u2/2^128 ~ r'^2},
     * {@code u4/2^128 ~ r'^4}, {@code u2h = floor(u2/2^64)}.
     */
    static U128 evalPS(U128 u, U128 u2, U128 u2h, U128 u4) {
        U128 sh = CoreMathTables.ps(2).sub(CoreMathTables.ps(3).mul(u2h));
        sh = sh.mhUU(u4);
        U128 s = CoreMathTables.ps(1).mhUU(u2);
        s = CoreMathTables.ps(0).sub(s);
        s = s.add(sh);
        return s.mhUU(u);
    }

    /** C {@code evalPC} (sin.c:214-223, identical in cos.c:164-173): {@code Cr/2^128 ~ cos2pi(r)}. */
    static U128 evalPC(U128 u2, U128 u2h, U128 u4) {
        U128 sh = CoreMathTables.pc(3).sub(u2h.mul(CoreMathTables.pc(4)));
        sh = CoreMathTables.pc(2).sub(sh.mhUU(u2));
        U128 s = CoreMathTables.pc(1).mhUU(u2);
        s = CoreMathTables.pc(0).sub(s);
        return s.add(sh.mhUU(u4));
    }

    private static final double[] SGN = {0x1p-53, -0x1p-53};
    private static final double[] LOW = {0x1.8p-2, 0x1.8p-1};

    /**
     * C {@code u128_tod} (sin.c:100-122, identical in cos.c:725-747): rounds
     * {@code (-1)^s * r / 2^128} to a {@code double}, for {@code r != 0} outside the subnormal
     * range. The result is {@code a + b} where {@code a} is the top 53 non-zero bits of {@code r}
     * and {@code b} is 3/8 or 3/4 of an ulp of {@code a} according to the round bit; the bits below
     * the round bit are not looked at. That is correct only because the accurate path's own error
     * bound keeps the true value away from a rounding boundary except for the hard cases that
     * {@code sin.wc}/{@code cos.wc} list.
     * <p>
     * <b>Added, not in the C:</b> a {@code r} whose upper word is zero throws. The C calls
     * {@code __builtin_clzll(0)}, which is undefined; its comment proves the case cannot occur
     * (the closest a binary64 comes to a multiple of pi/2 is 2^-60.888). If that proof were wrong
     * the Java would otherwise return a plausible wrong number silently.
     * <p>
     * <b>What the exception does downstream (read 2026-11-12).</b> It propagates out of
     * {@code CoreMath.sin}/{@code cos} through {@code Evaluator.callTranscendental}, which has no
     * {@code catch}, and out of {@code Evaluator.evaluate}: loud, not a value and not one of the closed
     * eight errors ({@code evaluate}'s javadoc promises no escaping exception for well-formed input,
     * except the missing-provider {@code IllegalStateException}). The Outrigger caller
     * {@code FilterEval.matches} catches every {@code Throwable} but {@code VirtualMachineError} and
     * answers a fail-closed no-match, counted in {@code FilterAdmission.FAIL_CLOSED_EXCLUSIONS}; it builds
     * {@code new Evaluator()}, which per {@code docs/SURVEY-lazy-statics-untrusted-input-2026-10-31.md}
     * uses {@code MathProvider.none()}, so that service reaches no transcendental provider today.
     * {@code CoreMathSupportGuardTest} drives this branch.
     */
    static double u128ToDouble(U128 r, int s) {
        if (r.hi == 0L) {
            StringBuilder msg = new StringBuilder(96);
            msg.append("CORE-MATH u128_tod: upper word of the fixed-point result is zero (r=").append(r).append(')');
            throw new IllegalStateException(msg.toString());
        }
        long sh = Long.numberOfLeadingZeros(r.hi);
        long h = r.shr((int) (75 - sh)).lo;                          // upper 53 non-zero bits
        int rbit = r.bit((int) (74 - sh));                            // round bit
        long vBits = Double.doubleToRawLongBits(SGN[s]) - (sh << 52); // scale by 2^-sh
        double vf = Double.longBitsToDouble(vBits);
        double a = (double) h * vf;
        double b = LOW[rbit] * vf;
        return a + b;
    }
}
