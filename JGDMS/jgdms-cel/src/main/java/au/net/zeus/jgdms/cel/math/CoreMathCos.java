/*
 * CoreMathCos: Java port of CORE-MATH's binary64 cosine, src/binary64/cos/cos.c at commit
 * 018fa97cbdacf59924b899a9dfa422d532cb2bb7, round-to-nearest path only.
 *
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
 * Correctly-rounded binary64 cosine, a port of CORE-MATH's {@code cr_cos}
 * ({@code cos.c:942-971}) with its fast path and its accurate path. Round-to-nearest only.
 * It shares its tables and helpers with {@link CoreMathSin} through {@link CoreMathTables} and
 * {@link CoreMathSupport}, as the C does (cos.c says it is adapted from sin.c).
 *
 * <h2>Structure</h2>
 * <ul>
 *   <li>{@code |x| < 2^-27}: {@code cos(x)} is 1 to nearest ({@code cos.c:950-952}).</li>
 *   <li>{@code 2^-27 <= |x| < 2^31}: {@link #fastModerate} ({@code cr_cos_moderate},
 *       {@code cos.c:830-870}).</li>
 *   <li>{@code |x| >= 2^31}: {@link #fastLarge} ({@code cr_cos_large}, {@code cos.c:912-940}).</li>
 *   <li>A fast path that cannot decide the rounding returns {@code NaN} and {@link #cos} runs
 *       {@link #accurate} ({@code cos_large_accurate}, {@code cos.c:765-825}); the C falls through
 *       by a call. The arithmetic is the same; the split lets a test call each path on its own.</li>
 * </ul>
 *
 * <h2>What differs from the C, and why</h2>
 * <ul>
 *   <li>{@code FE_INVALID} and {@code errno} statements are dropped (Java has neither). The C reads
 *       no rounding mode in this file (no {@code fegetround}).</li>
 *   <li>{@code exceptions} ({@code cos.c:749-762}) returns, for four inputs, {@code a + b} where
 *       {@code b} is far below half an ulp of {@code a}: in round-to-nearest that is {@code a}.
 *       The C writes the sum for the directed-rounding modes, which Java does not have; the sum is
 *       kept as written so the transcription stays literal, and {@code javac} folds it to the
 *       same {@code double} the C compiler would in round-to-nearest.</li>
 * </ul>
 *
 * <h2>Inputs</h2>
 * Finite {@code x}, zero included. NaN and infinities return a quiet NaN as the C does; the
 * provider {@link CoreMath} never lets them reach this class (see {@link CoreMathSin}).
 */
final class CoreMathCos {

    private CoreMathCos() {}

    /** C: cos.c:834: |invpi/2^14 - 1/pi| < 2^-55.496. */
    private static final double INVPI = 0x1.45f306dc9c883p+12;
    /** C: cos.c:863 and cos.c:932 {@code Sgn[]}. */
    private static final double[] SGN = {1.0, -1.0};
    /** C: cos.c:866: error bound of the moderate fast path. */
    private static final double EPS_MODERATE = 0x1.dep-64;
    /** C: cos.c:936: error bound of the large fast path. */
    private static final double EPS_LARGE = 0x1.01p-63;

    /** C {@code cr_cos} ({@code cos.c:942-971}). */
    static double cos(double x) {
        double z = Math.abs(x);
        int e = (int) (Double.doubleToRawLongBits(z) >>> 52);
        if (e < 0x3ff - 27) {                          // |x| < 2^-27: cos(x) rounds to 1 to nearest
            return Math.fma(x, -x, 1.0);
        }
        if (e == 0x7ff) {                              // NaN, +Inf, -Inf (C: cos.c:957-967)
            return Double.longBitsToDouble(Double.doubleToRawLongBits(x) | (1L << 51));
        }
        double r = (e < 0x3ff + 31) ? fastModerate(z) : fastLarge(z);
        return (r == r) ? r : accurate(z);
    }

    /**
     * The accurate path alone, for {@code z = |x| >= 2^-27}: C {@code cos_large_accurate}
     * ({@code cos.c:765-825}). The C passes {@code |x|}; so must the caller.
     */
    static double accurate(double z) {
        return cosLargeAccurate(z);
    }

    /**
     * The fast path alone for finite {@code z = |x| >= 2^-27}: the correctly rounded result, or
     * {@code NaN} if its error bound cannot decide the rounding.
     */
    static double fast(double z) {
        int e = (int) (Double.doubleToRawLongBits(z) >>> 52);
        return (e < 0x3ff + 31) ? fastModerate(z) : fastLarge(z);
    }

    /**
     * The table recombination both C fast paths repeat ({@code cos.c:845-868} and
     * {@code cos.c:922-938}); see {@code CoreMathSin.combine}. {@code a} and {@code b} are
     * {@code 4*i1} and {@code 4*i2}.
     */
    private static double combine(int a, int b, double sh, double ch, int sbit, double eps) {
        double[] u1 = CoreMathTables.U1;
        double[] u2 = CoreMathTables.U2;
        // c1h = muldd (U1[i1][2], U1[i1][3], U2[i2][2], U2[i2][3], &c1l): ~ cos(t1)*cos(t2)
        double c1h = u1[a + 2] * u2[b + 2];
        double c1l = CoreMathSupport.mulddLow(u1[a + 2], u1[a + 3], u2[b + 2], u2[b + 3], c1h);
        // c2h = muldd (U2[i2][0], U2[i2][1], U1[i1][0], U1[i1][1], &c2l): ~ sin(t1)*sin(t2)
        double c2h = u2[b] * u1[a];
        double c2l = CoreMathSupport.mulddLow(u2[b], u2[b + 1], u1[a], u1[a + 1], c2h);
        // Ch = fastsum (c1h, c1l, -c2h, -c2l, &Cl): ~ cos(t1+t2)
        double yh = -c2h;
        double Ch = c1h + yh;
        double Cl = (c1l + (-c2l)) + CoreMathSupport.fastTwoSumLow(c1h, yh, Ch);
        // Sh ~ sin(t1+t2) = sin(t1)*cos(t2)+cos(t1)*sin(t2)
        double Sh = u1[a] * u2[b + 2] + u1[a + 2] * u2[b];
        double fh = Ch;
        double fl = Cl + Ch * ch - Sh * sh;
        fh = SGN[sbit] * fh;
        fl = SGN[sbit] * fl;
        double lb = fh + (fl - eps);
        double ub = fh + (fl + eps);
        return (ub == lb) ? lb : Double.NaN;
    }

    /** C {@code cr_cos_moderate} up to its fall-through to the accurate path ({@code cos.c:830-870}); {@code x >= 0}. */
    private static double fastModerate(double x) {
        double k = Math.rint(INVPI * x);               // roundeven_finite
        double rh = Math.fma(k, CoreMathTables.PIH, x);    // rh is exact
        double rl = k * CoreMathTables.PIL;
        double r = rh + rl;                            // |r| < 2^-13.339 (see sin.pdf)
        double r2 = r * r;
        long j = (long) k;
        int sbit = (int) ((j >> 14) & 1);              // reduction by an odd multiple of pi?
        int a = 4 * (int) ((j >> 7) & 0x7f);
        int b = 4 * (int) (j & 0x7f);
        double sh = r * (1.0 - 0x1.55555553068fp-3 * r2);
        double ch = r2 * (-0.5 + 0x1.55555553bfd3p-5 * r2);
        return combine(a, b, sh, ch, sbit, EPS_MODERATE);
    }

    /** C {@code cr_cos_large} up to its fall-through to the accurate path ({@code cos.c:912-940}); {@code x >= 2^31}. */
    private static double fastLarge(double x) {
        U128 u = CoreMathSupport.reduceLarge(x);
        long j = CoreMathSupport.reduceLargeJ(u);      // x/(2pi) ~ k + j/2^15 + r with 0 <= r < 2^-15
        double r = CoreMathSupport.reduceLargeR(u);
        double r2 = r * r;
        int sbit = (int) (j >> 14);                    // reduction by an odd multiple of pi?
        int a = 4 * (int) ((j >> 7) & 0x7f);
        int b = 4 * (int) (j & 0x7f);
        double sh = r * (0x1.921fb54442d18p2 - 0x1.4abbcdb6b26d1p5 * r2);
        double ch = r2 * (-0x1.3bd3cc9be45dep4 + 0x1.03c1eee483083p6 * r2);
        return combine(a, b, sh, ch, sbit, EPS_LARGE);
    }

    /** C {@code exceptions} ({@code cos.c:749-762}). */
    private static double exceptions(double x, double ret) {
        double ax = Math.abs(x);
        if (ax == 0x1.8000000000009p-23) {             // cos(x) has 88 identical bits after round bit
            return 0x1.fffffffffff7p-1 + 0x1.b56666666666cp-143;
        }
        if (ax == 0x1.8000000000024p-22) {             // cos(x) has 82 identical bits after round bit
            return 0x1.ffffffffffdcp-1 + 0x1.b56666666667ep-137;
        }
        if (ax == 0x1.800000000009p-21) {              // cos(x) has 76 identical bits after round bit
            return 0x1.ffffffffff7p-1 + 0x1.b5666666666c4p-131;
        }
        if (ax == 0x1.20000000000f3p-20) {             // cos(x) has 72 identical bits after round bit
            return 0x1.fffffffffebcp-1 + 0x1.37642666666fdp-127;
        }
        return ret;
    }

    /** C {@code cos_large_accurate} ({@code cos.c:765-825}), {@code x = |x| >= 2^-27}. */
    private static double cosLargeAccurate(double x) {
        CoreMathSupport.AccReduction red = CoreMathSupport.reduceLargeAcc(x);
        long k = red.k;
        int neg = red.neg;
        U128 r = red.r.shl(3);                         // replace r by 8*r
        U128 u2 = r.mhUU(r);
        U128 u4 = u2.mhUU(u2);
        U128 u2h = u2.shr(64);

        /* x/(2*pi) mod 1 = k/2^13 + r + eps with |r| <= 2^-14 and 0 <= eps < 2^-127.999, so
           cos(x) ~ cos(pi*k/2^12 + 2*pi*r); k = 2^12*s + 2^6*i1 + i2 (C comment, cos.c:775-781) */
        int sbit = (int) ((k >> 12) & 1);
        int i1 = (int) ((k >> 6) & 0x3f);
        int i2 = (int) (k & 0x3f);

        U128[] s1t = CoreMathTables.S1U;
        U128 s1 = (i1 <= 32) ? s1t[i1] : s1t[64 - i1];        // use sin(pi-x) = sin(x)
        U128 c1 = (i1 <= 32) ? s1t[32 - i1] : s1t[i1 - 32];   // |C1u[i1]| = |S1u[(i1+32) mod 64]|
        U128 s1u = s1.mhUU(CoreMathTables.C2U[i2]);
        U128 t = c1.mhUU(CoreMathTables.S2U[i2]);
        s1u = (i1 < 32) ? s1u.add(t) : s1u.sub(t);            // sin(z) >= 0 for 0 <= z < pi

        U128 c1u = c1.mhUU(CoreMathTables.C2U[i2]);
        t = s1.mhUU(CoreMathTables.S2U[i2]);
        c1u = (i1 < 32) ? c1u.sub(t) : c1u.add(t);

        U128 sr = CoreMathSupport.evalPS(r, u2, u2h, u4);     // ~ |sin(2*pi*r)|
        U128 cr = CoreMathSupport.evalPC(u2, u2h, u4);        // ~ cos(2*pi*r)

        // now combine: cos(x) ~ c1*C - s1*S
        c1u = c1u.mhUU(cr);
        s1u = s1u.mhUU(sr);

        if ((((i1 >= 32) ? 1 : 0) ^ neg) != 0) {              // cos(z) * r < 0
            c1u = c1u.add(s1u);
        } else if (c1u.leU(s1u)) {
            c1u = s1u.sub(c1u);
            sbit ^= 1;
        } else {
            c1u = c1u.sub(s1u);
        }
        // if i1 >= 32, cos(z) <= 0 thus we should exchange signs
        double ret = CoreMathSupport.u128ToDouble(c1u, sbit ^ ((i1 >= 32) ? 1 : 0));
        long d = c1u.lo + 5;                                   // C: (uint64_t) c1u + 5
        if (Long.compareUnsigned(d, 3L) <= 0) {
            return exceptions(x, ret);
        }
        return ret;
    }
}
