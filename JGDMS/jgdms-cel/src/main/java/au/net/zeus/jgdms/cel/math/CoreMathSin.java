/*
 * CoreMathSin: Java port of CORE-MATH's binary64 sine, src/binary64/sin/sin.c at commit
 * 018fa97cbdacf59924b899a9dfa422d532cb2bb7, round-to-nearest path only.
 *
 * Copyright (c) 2026 Paul Zimmermann and Alexei Sibidanov
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
 * Correctly-rounded binary64 sine, a port of CORE-MATH's {@code cr_sin}
 * ({@code sin.c:954-991}) with its fast path and its accurate path. Round-to-nearest only.
 *
 * <h2>Structure</h2>
 * <ul>
 *   <li>{@code |x| < 2^-26}: {@code sin(x)} is {@code x} to nearest ({@code sin.c:960-974}); zero
 *       returns itself, so {@code sin(-0.0) = -0.0}.</li>
 *   <li>{@code 2^-26 <= |x| < 2^31}: {@link #fastModerate} reduces by multiples of pi/2^14 with a
 *       two-word constant ({@code cr_sin_moderate}, {@code sin.c:882-919}).</li>
 *   <li>{@code |x| >= 2^31}: {@link #fastLarge} reduces with the Payne-Hanek table
 *       ({@code cr_sin_large}, {@code sin.c:922-952}).</li>
 *   <li>Both fast paths return a result only when an error bound of 2^-64 (moderate) or
 *       2^-63 (large) cannot change the rounding; otherwise {@link #accurate} runs a
 *       128-bit fixed-point evaluation.</li>
 * </ul>
 *
 * <h2>What differs from the C, and why</h2>
 * <ul>
 *   <li>The C falls from each fast path into the accurate path by a call. Here a fast path that
 *       cannot decide returns {@code NaN}, which is never a valid result for a finite argument,
 *       and {@link #sin} then calls {@link #accurate}. The arithmetic is the same; the split
 *       lets a test call each path on its own (a delegating or fast-path-only defect would
 *       otherwise hide behind the other path).</li>
 *   <li>The C raises {@code FE_INVALID} and sets {@code errno} in places; Java has neither
 *       floating-point flags nor {@code errno}, so those statements are dropped. The C reads no
 *       rounding mode (no {@code fegetround}) in this file, so no directed-rounding branch is
 *       dropped.</li>
 *   <li>{@code Math.rint} is the C's {@code roundeven_finite}; {@link Math#fma} is
 *       {@code __builtin_fma}.</li>
 * </ul>
 *
 * <h2>Inputs</h2>
 * Finite {@code x}, zero included. NaN and infinities return a quiet NaN exactly as the C does;
 * the provider {@link CoreMath} never lets them reach this class (the evaluator's contract
 * excludes them, and {@code CrMath} fails loudly on them, so {@code CoreMath} delegates them to it).
 */
final class CoreMathSin {

    private CoreMathSin() {}

    /** C: sin.c:886: |invpi/2^14 - 1/pi| < 2^-55.496. */
    private static final double INVPI = 0x1.45f306dc9c883p+12;
    /** C: sin.c:911 and sin.c:944 {@code Sgn[]}. */
    private static final double[] SGN = {1.0, -1.0};
    /** C: sin.c:914: error bound of the moderate fast path. */
    private static final double EPS_MODERATE = 0x1.dep-64;
    /** C: sin.c:948: error bound of the large fast path. */
    private static final double EPS_LARGE = 0x1.01p-63;
    /** C: sin.c:866-867: x + (c3h+c3l)*x^3 + c5*x^5, relative error < 2^-112.743 on [0, 2^-16]. */
    private static final double[] C_SMALL = {-0x1.5555555555555p-3, -0x1.55554b00de7e8p-57, 0x1.111111110848p-7};

    /** C {@code cr_sin} ({@code sin.c:954-991}). */
    static double sin(double x) {
        long bits = Double.doubleToRawLongBits(x);
        int e = (int) (bits >>> 52) & 0x7ff;
        if (e < 0x3ff - 26) {                          // |x| < 2^-26
            if ((bits << 1) == 0L) {
                return x;                              // sin(+-0) = +-0
            }
            // Taylor x - x^3/6. Zero is returned above because fma(-0, -2^-54, -0) is +0 (C comment, sin.c:966).
            return Math.fma(x, -0x1p-54, x);
        }
        if (e == 0x7ff) {                              // NaN, +Inf, -Inf (C: sin.c:978-988)
            return Double.longBitsToDouble(0x7ff8000000000000L);
        }
        double r = (e < 0x3ff + 31) ? fastModerate(x, (int) (bits >>> 63)) : fastLarge(x);
        return (r == r) ? r : accurate(x);
    }

    /**
     * The accurate path alone, for any finite {@code |x| >= 2^-26}: C {@code sin_small_accurate}
     * ({@code sin.c:860-877}) below 2^-16, C {@code sin_large_accurate} ({@code sin.c:800-857})
     * from 2^-16 up. In the C the small variant is reached only from the moderate path, so
     * {@code |x| < 2^-16} cannot arrive here from a large argument.
     */
    static double accurate(double x) {
        if (Math.abs(x) < 0x1p-16) {
            return sinSmallAccurate(x);
        }
        return sinLargeAccurate(x);
    }

    /**
     * The fast path alone for finite {@code |x| >= 2^-26}: the correctly rounded result, or
     * {@code NaN} if its error bound cannot decide the rounding.
     */
    static double fast(double x) {
        long bits = Double.doubleToRawLongBits(x);
        int e = (int) (bits >>> 52) & 0x7ff;
        return (e < 0x3ff + 31) ? fastModerate(x, (int) (bits >>> 63)) : fastLarge(x);
    }

    /**
     * The table recombination both C fast paths repeat ({@code sin.c:898-916} and
     * {@code sin.c:935-950}): {@code sin(t1+t2)} from the double-double tables and
     * {@code sin(r)}, {@code cos(r)-1} from the caller's polynomials; {@code NaN} when the
     * rounding is not decided. {@code a} and {@code b} are {@code 4*i1} and {@code 4*i2}, the
     * row offsets into the flattened {@code U1} and {@code U2}.
     */
    private static double combine(int a, int b, double sh, double ch, int sbit, double eps) {
        double[] u1 = CoreMathTables.U1;
        double[] u2 = CoreMathTables.U2;
        // s1h = muldd (U1[i1][0], U1[i1][1], U2[i2][2], U2[i2][3], &s1l)
        double s1h = u1[a] * u2[b + 2];
        double s1l = CoreMathSupport.mulddLow(u1[a], u1[a + 1], u2[b + 2], u2[b + 3], s1h);
        // s2h = muldd (U2[i2][0], U2[i2][1], U1[i1][2], U1[i1][3], &s2l)
        double s2h = u2[b] * u1[a + 2];
        double s2l = CoreMathSupport.mulddLow(u2[b], u2[b + 1], u1[a + 2], u1[a + 3], s2h);
        // Sh = fastsum (s1h, s1l, s2h, s2l, &Sl)
        double Sh = s1h + s2h;
        double Sl = (s1l + s2l) + CoreMathSupport.fastTwoSumLow(s1h, s2h, Sh);
        double Ch = u1[a + 2] * u2[b + 2] - u1[a] * u2[b];
        double fh = Sh;
        double fl = Sl + Sh * ch + Ch * sh;
        fh = SGN[sbit] * fh;
        fl = SGN[sbit] * fl;
        double lb = fh + (fl - eps);
        double ub = fh + (fl + eps);
        return (ub == lb) ? lb : Double.NaN;
    }

    /** C {@code cr_sin_moderate} up to its fall-through to the accurate path ({@code sin.c:882-919}). */
    private static double fastModerate(double x, int sbit) {
        double ax = Math.abs(x);
        double k = Math.rint(INVPI * ax);              // roundeven_finite
        // |2^14*(pih + pil) + pi| < 2^-108.041
        double rh = Math.fma(k, CoreMathTables.PIH, ax);   // rh is exact
        double rl = k * CoreMathTables.PIL;
        double r = rh + rl;                            // |r| < 2^-13.339 (see sin.pdf)
        double r2 = r * r;
        long j = (long) k;
        sbit ^= (int) ((j >> 14) & 1);                 // reduction by an odd multiple of pi?
        int a = 4 * (int) ((j >> 7) & 0x7f);
        int b = 4 * (int) (j & 0x7f);
        /* for |r| <= 2^-13.339, r - 0x1.55555553068fp-3 * r^3 approximates sin(r) with absolute
           error < 2^-76.494 and -0.5 * r^2 + 0x1.55555553bfd3p-5 * r^4 approximates cos(r)-1 with
           absolute error < 2^-92.723 (sinmoderate.sollya, quoted from the C) */
        double sh = r * (1.0 - 0x1.55555553068fp-3 * r2);
        double ch = r2 * (-0.5 + 0x1.55555553bfd3p-5 * r2);
        return combine(a, b, sh, ch, sbit, EPS_MODERATE);
    }

    /** C {@code cr_sin_large} up to its fall-through to the accurate path ({@code sin.c:922-952}). */
    private static double fastLarge(double x) {
        double ax = Math.abs(x);
        U128 u = CoreMathSupport.reduceLarge(ax);
        long j = CoreMathSupport.reduceLargeJ(u);      // x/(2pi) ~ k + j/2^15 + r with 0 <= r < 2^-15
        double r = CoreMathSupport.reduceLargeR(u);
        int sbit = (x > 0) ? 0 : 1;
        double r2 = r * r;
        sbit = sbit ^ (int) (j >> 14);                 // reduction by an odd multiple of pi?
        int a = 4 * (int) ((j >> 7) & 0x7f);
        int b = 4 * (int) (j & 0x7f);
        double sh = r * (0x1.921fb54442d18p2 - 0x1.4abbcdb6b26d1p5 * r2);
        double ch = r2 * (-0x1.3bd3cc9be45dep4 + 0x1.03c1eee483083p6 * r2);
        return combine(a, b, sh, ch, sbit, EPS_LARGE);
    }

    /** C {@code sin_large_accurate} ({@code sin.c:800-857}), |x| >= 2^-16, signed x. */
    static double sinLargeAccurate(double x) {
        CoreMathSupport.AccReduction red = CoreMathSupport.reduceLargeAcc(x);
        long k = red.k;
        int neg = red.neg;
        U128 r = red.r.shl(3);                         // replace r by 8*r
        U128 u2 = r.mhUU(r);
        U128 u4 = u2.mhUU(u2);
        U128 u2h = u2.shr(64);

        /* x/(2*pi) mod 1 = k/2^13 + r + eps with |r| <= 2^-14 and 0 <= eps < 2^-127.999, so
           sin(x) ~ sin(pi*k/2^12 + 2*pi*r); k = 2^12*s + 2^6*i1 + i2 (C comment, sin.c:810-815) */
        int sbit = (x > 0) ? 0 : 1;
        sbit = sbit ^ (int) (k >> 12);
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

        // now combine: sin(x) ~ s1*C + c1*S
        s1u = s1u.mhUU(cr);
        c1u = c1u.mhUU(sr);

        if ((((i1 < 32) ? 1 : 0) ^ neg) != 0) {               // cos(z) * r > 0
            s1u = s1u.add(c1u);
        } else if (s1u.ltU(c1u)) {
            s1u = c1u.sub(s1u);
            sbit ^= 1;
        } else {
            s1u = s1u.sub(c1u);
        }
        return CoreMathSupport.u128ToDouble(s1u, sbit);
    }

    /** C {@code sin_small_accurate} ({@code sin.c:860-877}), {@code |x| < 2^-16}. */
    static double sinSmallAccurate(double x) {
        double x2h = x * x;
        double x2l = Math.fma(x, x, -x2h);
        double h = C_SMALL[2] * x2h;                   // relative error less than ulp(c5*x^4)/ulp(x) ~ 2^-123
        h += C_SMALL[1];                               // relative error less than ulp(c3l*x^2)/ulp(x) ~ 2^-141
        double s = C_SMALL[0] + h;                     // h = fasttwosum (c[0], h, &l)
        double l = CoreMathSupport.fastTwoSumLow(C_SMALL[0], h, s);
        h = s;
        double p = h * x2h;                            // h = muldd (h, l, x2h, x2l, &l)
        double pl = CoreMathSupport.mulddLow(h, l, x2h, x2l, p);
        h = p;
        l = pl;
        p = h * x;                                     // h = muldd (h, l, x, 0, &l)
        pl = CoreMathSupport.mulddLow(h, l, x, 0.0, p);
        h = p;
        l = pl;
        s = x + h;                                     // h = fasttwosum (x, h, &t)
        double t = CoreMathSupport.fastTwoSumLow(x, h, s);
        h = s;
        l += t;
        return h + l;
    }
}
