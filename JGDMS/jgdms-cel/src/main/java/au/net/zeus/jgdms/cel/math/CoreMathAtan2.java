/*
 * CoreMathAtan2: Java port of CORE-MATH's binary64 atan2, src/binary64/atan2/atan2.c at commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7,
 * round-to-nearest path only.
 *
 * DERIVATION: this file is a Java port, that is a modified and derived work, of
 * src/binary64/atan2/atan2.c of the CORE-MATH project at commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7.
 * It is not part of CORE-MATH and is not endorsed by its authors. The copyright and permission notice below are the upstream's, reproduced without change.
 *
 * Copyright (c) 2024-2025 Paul Zimmermann and Alexei Sibidanov
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
 * Correctly-rounded binary64 {@code atan2(y, x)}, a port of CORE-MATH's {@code cr_atan2}
 * ({@code atan2.c:461-586}) with its two-stage fast path, its accurate path {@code atan2_accurate}
 * ({@code atan2.c:199-395}, 192-bit arithmetic from {@code tint.h}, here {@link CoreMathTint}) and its special-value
 * routine {@code as_atan2_special} ({@code atan2.c:411-459}). Round-to-nearest only.
 *
 * <h2>Structure</h2>
 * <ul>
 *   <li>Zero, infinite or NaN arguments: {@code as_atan2_special}, which implements the IEEE-754 / C99 F.10.1.4 rows.
 *       The rows for zeros are used through {@link CoreMath}; the rows for infinities and NaN are ported but the
 *       provider never lets such arguments reach this class (it delegates them to {@code CrMath}, which throws).</li>
 *   <li>Otherwise {@code ax = |x|, ay = |y|}, {@code (x, y) = (max, min)}; a table index {@code jt} from
 *       {@code y/x + 2 + 1/128}; the first stage evaluates a short polynomial in {@code z = (y - T2[jt]*x)/(x + T2[jt]*y)}
 *       with an absolute error bound; if its two bounds differ, a second stage in double-double arithmetic; if those
 *       still differ, or the exponents of {@code x} and {@code y} differ by 53 or more, {@code atan2_accurate}.</li>
 *   <li>{@code atan2_accurate}: {@code z = min/max} as a 192-bit float, a type [29,29] rational approximation
 *       {@code z*P(z)/Q(z)} of {@code atan(z)} on [0,1] (coefficients in {@link CoreMathInverseTrigTables}), a
 *       quadrant correction by {@code pi/2} or {@code pi}, and {@link CoreMathTint#toD}.</li>
 * </ul>
 *
 * <h2>The correct-rounding failure branch</h2>
 * The C includes {@code <stdio.h>} "needed in case of correct rounding failure": {@code tint_tod} (tint.h:409-416)
 * prints "Unexpected worst-case found, please report to core-math@inria.fr" and calls {@code exit(1)} when the 192-bit
 * value is too close to a rounding boundary or midpoint for its error bound to decide. The Java never prints and never
 * guesses: {@link CoreMathTint#toD} returns {@code NaN}, {@link #accurate} returns it, {@link #atan2} returns it, and
 * {@link CoreMath#atan2} sees the {@code NaN} (never a valid result for finite arguments), counts the event in
 * {@link CoreMath#atan2CorrectRoundingFailures()} and answers by delegating to {@code CrMath}, which is correctly
 * rounded by construction. The tests report the count for every population they run.
 * <p>
 * <b>What the count shows (Opus LOW-4 and Fable Q4, 2026-11-12).</b> It was 0 in 6,571,515 accurate-path runs of phase B1's tests. That is the
 * detector's false-positive rate on genuine inputs, not evidence that failures cannot occur: the condition needs about 128 identical bits after
 * the round bit (about 2^-127.6 per run if the low bits are uniform, a HYPOTHESIS), so a population of any feasible size has essentially no input
 * where it could act. The live evidence is the forced population of {@code CoreMathTintTest} (NaN exactly where a BigInteger model says the C fails),
 * the hand-made values of {@code p7TintToDoubleReturnsNaNWhereTheCPrintsAndExits} and the routing control. A real failure would be answered correctly
 * and silently: the counter is package-private and nothing logs it (the operator may want it exposed, since CORE-MATH asks to be told of such an input).
 *
 * <h2>Proof status, and what the evidence is (read 2026-11-12)</h2>
 * CORE-MATH publishes <b>no correctness proof for atan2</b>: its homepage says of bivariate functions that they are
 * "checked only with respect to known hard-to-round cases", and its worst-case page that "for atan2(y,x), only a
 * restricted set of worst cases has been computed" (Chisel's web check of 2026-11-12). The fast path's absolute error
 * constant (atan2.c:544-546) is empirical: "obtained with 1.1e10 random argument pairs in the roundup mode ... increased
 * by 2.5% for safety". This port's correctness rests on {@code atan2.wc} (27,943 finite pairs, every one run by
 * {@code CoreMathInverseTrigSlowTest}; the file has 28,123 data lines, 180 of which name a NaN or an infinity and are delegated, and it opens with
 * special-value rows that are not hard cases), on at least 5,000,000 seeded random argument pairs against {@link CrMath},
 * and on the fidelity of the port. That is weaker than a proof, and it is NOT validated on any input that the C
 * authors' tests do not also cover.
 *
 * <h2>Which error constants the tests can and cannot see by behaviour (Fable review of phase B1, 2026-11-12; DIAGNOSTIC)</h2>
 * Fable (claude-fable-5-2) compiled copies of the port with one constant scaled and compared their bits with the production class (which equals
 * {@code CrMath} on the same entries in the slow tier). Population: every finite in-domain entry of the function's {@code .wc} file and, for atan and
 * atan2, 2,000,000 seeded random inputs. Result ("x 0.5" = the constant halved): 
 * first stage {@code ATAN2_EPS1} (0x1.051p-51): x 0.5 gives 0 of 27,638 non-zero {@code .wc} pairs and 0 of 2,000,000, x 0.40 gives 5 of 27,638, x 0.25
 * gives 494 of 27,638 and 59 of 2,000,000; second stage {@code ATAN2_EPS2} (0x1.4p-50): x 0.5 gives 0 of 27,638 and 0 of 2,000,000, x 0.25 gives 23 of 27,638
 * and 0 of 2,000,000. Not scaled by Fable: the additive terms {@code ATAN2_EPS1_ADD}, {@code ATAN2_EPS2_ADD} and the {@code err} bounds. A halved constant is
 * invisible to the whole {@code atan2.wc} file and to 2,000,000 random pairs (phase B1's own mutation runs: also to 5,000,000 near-exponent pairs), so the constants
 * are guarded by {@link CoreMathInverseTrigTables} (named constants, digest, comparison with the C text), not by results. For atan2 this matters most: the first-stage
 * constant is empirical ("1.1e10 random argument pairs in the roundup mode") and no proof exists. A plausible cause of the margin (HYPOTHESIS): sized for directed rounding.
 *
 * <h2>What differs from the C, and why</h2>
 * <ul>
 *   <li>Floating-point exception flags and {@code errno} ({@code fenv_t}, {@code feholdexcept}, {@code fetestexcept},
 *       {@code feupdateenv}, {@code feclearexcept}, {@code fegetexceptflag}, {@code ERANGE}) are dropped: Java has
 *       none. The local variables {@code overflow}, {@code underflow}, {@code inexact}, {@code u} and {@code v} of
 *       {@code atan2_accurate} exist only to compute those flags and are dropped with them. The C reads no rounding
 *       mode in this file ({@code fegetround}: 0 hits).</li>
 *   <li>The C falls from the fast path into {@code atan2_accurate} by a call. Here {@link #fast} returns {@code NaN}
 *       when it cannot decide and {@link #atan2} then calls {@link #accurate}; the arithmetic is the same.</li>
 *   <li>{@code z->l -= 2; z->m -= (z->l < 2); z->h -= (z->m < 1);} (atan2.c:283-285) is ported exactly. Its borrow
 *       tests look at the already-updated words, so they are not a correct 192-bit subtraction when {@code l} is 0..3
 *       or {@code m} is 0. That is reached routinely, not rarely: a quotient {@code z} with {@code m = l = 0} (a power of two, or a quotient below
 *       the subnormal grid, for example {@code atan2(3*2^-1074, 2)}, where the double quotient was inexact but the 192-bit one is exact) makes {@code l}
 *       wrap without a borrow and {@code h -= (m < 1)} fire because {@code m} is 0; the net is a small decrease and the rounding is right (Fable's
 *       probe of 2026-11-12 through the provider: 0 mismatches of 19,200 such pairs and of 200,000 quotients in the subnormal range). Inexact
 *       quotients whose low word is 0..3 are the improbable case (about 2^-62). It is a property of the C, left as it is.</li>
 *   <li>{@code Math.fma} is {@code __builtin_fma}; Java never contracts {@code a*b+c}, the C compiler may.</li>
 * </ul>
 */
final class CoreMathAtan2 {

    private CoreMathAtan2() {}

    // PI_H + PI_L approximates pi with error bounded by 2^-108.041; PI_OVER2_*: 2^-109.041; (atan2.c:51-59)
    // (one definition each, in CoreMathInverseTrigTables; the names here are the C's)
    private static final double PI_H = CoreMathInverseTrigTables.PI_H;
    private static final double PI_L = CoreMathInverseTrigTables.PI_L;
    private static final double PI_OVER2_H = CoreMathInverseTrigTables.PI2_H;
    private static final double PI_OVER2_L = CoreMathInverseTrigTables.PI2_L;

    private static final long MASK = 0x7fffffffffffffffL;              // 2^63-1 (mask the sign bit)

    /**
     * C {@code cr_atan2} ({@code atan2.c:461-586}). For finite arguments a {@code NaN} result means the
     * correct-rounding failure branch (see the class comment); it is never a legitimate value there.
     */
    static double atan2(double y0, double x0) {
        long iy = Double.doubleToRawLongBits(y0);
        long ix = Double.doubleToRawLongBits(x0);
        long aiy = iy & MASK;
        if (aiy == 0L || aiy >= (0x7ffL << 52)) {
            return special(y0, x0);
        }
        long aix = ix & MASK;
        if (aix == 0L || aix >= (0x7ffL << 52)) {
            return special(y0, x0);
        }
        double r = fast(y0, x0);
        return (r == r) ? r : accurate(y0, x0);
    }

    /**
     * Both stages of the fast path for finite non-zero {@code y0, x0}: the correctly rounded result, or {@code NaN} when
     * the exponents of the arguments differ by 53 or more or when the second stage cannot decide either; the caller
     * then runs {@link #accurate}.
     */
    static double fast(double y0, double x0) {
        return fast(y0, x0, true);
    }

    /**
     * The first stage alone ({@code stage2 = false}: {@code NaN} as soon as the first pair of bounds differs) or both
     * stages ({@code stage2 = true}).
     */
    static double fast(double y0, double x0, boolean stage2) {
        long iy = Double.doubleToRawLongBits(y0);
        long ix = Double.doubleToRawLongBits(x0);
        long aiy = iy & MASK;
        long aix = ix & MASK;
        double ax = Math.abs(x0);
        double ay = Math.abs(y0);
        double x = Math.max(ax, ay);
        double y = Math.min(ax, ay);
        long sy = iy >>> 63;
        long sx = ix >>> 63;
        long gt = (aix < aiy) ? 1L : 0L;
        long dxy = (aix - aiy) ^ -gt;
        if (Long.compareUnsigned(dxy, 53L << 52) >= 0) {
            return Double.NaN;                                          // C: return atan2_accurate(y0, x0)
        }
        double sgn = ((gt ^ sx ^ sy) == 0L) ? 1.0 : -1.0;               // C: copysign(1, asgn[GT^sx^sy])
        int kw = (int) ((sx << 2) | (sy << 1) | gt);
        double jj = y / x + (2 + 1 / 128.);
        int jt = (int) ((Double.doubleToRawLongBits(jj) >>> (52 - 7)) & 127L);
        double fh = CoreMathInverseTrigTables.atan2F2(2 * jt + 1) * sgn;
        double fl = CoreMathInverseTrigTables.atan2F2(2 * jt) * sgn;
        fh += CoreMathInverseTrigTables.atan2O(2 * kw);
        fl += CoreMathInverseTrigTables.atan2O(2 * kw + 1);
        if (x < 0x1p-920) {
            x *= 0x1p920;
            y *= 0x1p920;
        }
        if (x > 0x1p1022) {
            if (jt != 0) {
                x *= 0x1p-1;
                y *= 0x1p-1;
            }
        }
        double t0 = CoreMathInverseTrigTables.atan2T2(jt);
        double zn = Math.fma(-t0, x, y);
        double zd = Math.fma(t0, y, x);
        double z = zn / zd;
        double z2 = z * z;
        z *= sgn;
        double dz = (z * z2) * (CoreMathInverseTrigTables.atan2B(0)
                + z2 * (CoreMathInverseTrigTables.atan2B(1) + z2 * CoreMathInverseTrigTables.atan2B(2)));
        // this bound is obtained with 1.1e10 random argument pairs in the roundup mode; the obtained maximal
        // difference is increased by 2.5% for safety (C comment)
        double eps = Math.abs(z) * CoreMathInverseTrigTables.ATAN2_EPS1 + CoreMathInverseTrigTables.ATAN2_EPS1_ADD;
        double rh = fh + z;                                             // rh = fasttwosum(fh, z, &z)
        z = CoreMathSupport.fastTwoSumLow(fh, z, rh);
        double rl = (fl + dz) + z;
        double lb = rh + (rl - eps);
        double ub = rh + (rl + eps);
        if (lb != ub) {
            if (!stage2) {
                return Double.NaN;
            }
            double dh = y * t0;
            double dl = Math.fma(y, t0, -dh);
            double dh2 = x + dh;                                        // dh = fasttwosum(x, dh, &e)
            double e = CoreMathSupport.fastTwoSumLow(x, dh, dh2);
            dh = dh2;
            // the C avoids a spurious underflow flag in 1/dh when |dh| > 2^1022 by saving and restoring the flag;
            // Java has no flags, so both branches are the same division
            double rdh = 1 / dh;
            dl += e;
            double nh = x * t0;
            double nl = Math.fma(x, t0, -nh);
            double dt = y - nh;
            double y1 = dt + nh;
            if (y1 == y) {
                double nh2 = dt + (-nl);                                // nh = fasttwosum(dt, -nl, &nl)
                nl = CoreMathSupport.fastTwoSumLow(dt, -nl, nh2);
                nh = nh2;
            } else {
                double arg = (y - y1) - nl;                             // nh = fasttwosum(dt, (y - y1) - nl, &nl)
                double nh2 = dt + arg;
                nl = CoreMathSupport.fastTwoSumLow(dt, arg, nh2);
                nh = nh2;
            }
            double zh = nh * rdh;
            z2 = zh * zh;
            double zl = rdh * (Math.fma(dh, -zh, nh) + (nl - (nh * rdh) * dl));
            zl += zh * z2 * ((CoreMathInverseTrigTables.atan2B2(0) + z2 * CoreMathInverseTrigTables.atan2B2(1))
                    + (z2 * z2) * (CoreMathInverseTrigTables.atan2B2(2) + z2 * CoreMathInverseTrigTables.atan2B2(3)));
            zh *= sgn;
            zl *= sgn;
            eps = CoreMathInverseTrigTables.ATAN2_EPS2 * (Math.abs(zh) * z2 + CoreMathInverseTrigTables.ATAN2_EPS2_ADD);
            double fh2 = fh + zh;                                       // fh = fastsum(fh, fl, zh, zl, &fl)
            fl = CoreMathSupport.fastSumLow(fh, fl, zh, zl, fh2);
            fh = fh2;
            lb = fh + (fl - eps);
            ub = fh + (fl + eps);
            if (lb != ub) {
                return Double.NaN;                                      // C: return atan2_accurate(y0, x0)
            }
        }
        return ub;
    }

    /**
     * C {@code atan2_accurate} ({@code atan2.c:199-395}) for finite non-zero {@code y, x}: a type [29,29] rational
     * approximation of {@code atan(z)} for {@code 0 <= z <= 1} in 192-bit arithmetic. Returns {@code NaN} in the
     * correct-rounding failure case (class comment).
     */
    static double accurate(double y, double x) {
        // First check when t = y/x is small and exact and x > 0, since for |t| <= 0x1.d12ed0af1a27fp-27, atan(t) rounds
        // to t (to nearest). If t = y/x underflowed for x > 0 then atan(y/x) underflows too; the C records that for
        // errno and the underflow flag only, which Java does not have.
        double t = y / x;
        if (t == 0) {
            if (x > 0) {
                return t;
            }
            return (y > 0) ? PI_H + PI_L : -PI_H - PI_L;
        }
        double corr = Math.fma(t, x, -y);
        if (corr == 0 && x > 0) {                                       // t is exact
            if (Math.abs(t) <= 0x1.d12ed0af1a27fp-27) {
                // Warning (C): if y is in the subnormal range, t might differ from y/x.
                // If |y| >= 2^-969, then since t*x has at most 106 significant bits, and t*x ~ y, the lower bit of t*x is
                // >= 2^-1074, thus there is no underflow in t*x-y.
                if (Math.abs(y) >= 0x1p-915) {
                    return Math.fma(t, -0x1p-54, t);
                }
                // Now |y| < 2^-969, since x >= 2^-1074, then t <= 2^105, thus we can scale y and t by 2^105, which will
                // ensure t*x-y does not underflow.
                corr = Math.fma(t * 0x1p105, x, -y * 0x1p105);
                if (corr == 0) {
                    return Math.fma(t, -0x1p-54, t);
                }
            }
        }
        boolean inv = Math.abs(y) > Math.abs(x);
        CoreMathTint.Tint z = new CoreMathTint.Tint();
        CoreMathTint.Tint p = new CoreMathTint.Tint();
        CoreMathTint.Tint q = new CoreMathTint.Tint();
        if (inv) {
            CoreMathTint.divD(z, x, y);
        } else {
            CoreMathTint.divD(z, y, x);
        }

        // When |y/x| < 2^-27, x > 0, atan(y/x) rounds to the same value as y/x perturbed by a small amount towards zero
        // (here we subtract 2 to z->l). The Taylor expansion t - t^3/3 + O(t^5) gives a relative error bounded by t^2/2,
        // so |y/x| < 2^-96 makes it less than 1 ulp.
        if (!inv && x > 0 && z.ex <= -96) {
            z.l -= 2;
            z.m -= CoreMathSupport.ltu(z.l, 2L) ? 1L : 0L;
            z.h -= CoreMathSupport.ltu(z.m, 1L) ? 1L : 0L;
            return CoreMathTint.toD(z, CoreMathInverseTrigTables.ATAN2_ERR_SMALL);
        }

        // below when we write y/x it should be read x/y when |x/y| < 1;
        // |z - y/x| < 2^-185.53 * |z| (relative error from div_tint_d); the rational approximation is only for z > 0,
        // it is not antisymmetric
        long sz = z.sgn;
        z.sgn = 0L;
        CoreMathTint.Tint tmp = new CoreMathTint.Tint();
        CoreMathInverseTrigTables.atan2P(29, p);
        CoreMathInverseTrigTables.atan2Q(29, q);
        for (int i = 28; i >= 0; i--) {
            CoreMathTint.mul(p, p, z);
            CoreMathTint.mul(q, q, z);
            CoreMathInverseTrigTables.atan2P(i, tmp);
            CoreMathTint.add(p, p, tmp);
            CoreMathInverseTrigTables.atan2Q(i, tmp);
            CoreMathTint.add(q, q, tmp);
        }
        CoreMathTint.mul(p, p, z);                                      // multiply p by z
        // The relative error of div_tint() is <= 2^-185.53; the error analysis of the C (atan2.c:306-331) gives a
        // maximal error of 2^-182.63*2^192 <= 662 ulps for inv = 0 and x > 0.
        CoreMathTint.div(z, p, q);
        long err = CoreMathInverseTrigTables.ATAN2_ERR_DIV;
        z.sgn = sz;                                                     // restore sign
        // Now z approximates atan(y/x) for inv = 0, and atan(x/y) for inv = 1, with -pi/4 < z < pi/4.
        if (inv) {
            // if x/y > 0 thus atan(x/y) > 0 we apply pi/2 - atan(x/y); if x/y < 0 we apply -pi/2 - atan(x/y)
            if (z.sgn == 0L) {                                          // 0 < atan(x/y) < pi/4
                z.sgn = 1L;
                CoreMathTint.addPi2(z);
                // Now pi/4 < z < pi/2; relative to ulp(pi/4) the total error is less than 524 (C comment)
            } else {                                                    // -pi/4 < atan(x/y) < 0
                CoreMathTint.addPi2(z);
                z.sgn = 1L;
                // Now -pi/2 < z < -pi/4: the same bound of 524 ulps
            }
            err = CoreMathInverseTrigTables.ATAN2_ERR_INV;
        }
        // now -pi/2 < z < pi/2; if x is negative we go to the opposite quadrant
        if (x < 0) {
            if (z.sgn == 0L) {                                          // 1st quadrant -> 3rd quadrant (subtract pi)
                z.sgn = 1L;
                CoreMathTint.addPi(z);
                z.sgn = 1L;
                // We had 0 < z < pi/2 thus now -pi < z < -pi/2: relative to ulp(pi/2) less than 266 ulps (C comment)
            } else {                                                    // 4th quadrant -> 2nd quadrant (add pi)
                CoreMathTint.addPi(z);
                // If inv = 0 we had -pi/4 < z < 0 thus now 3pi/4 < z < pi; if inv = 1, pi/2 < z < 3pi/4: 266 ulps again
            }
            err = CoreMathInverseTrigTables.ATAN2_ERR_NEG;
        }
        return CoreMathTint.toD(z, err);
    }

    /** C {@code as_atan2_special} ({@code atan2.c:411-459}): the rows of the IEEE-754 / C99 F.10.1.4 table for zeros, infinities and NaN. */
    static double special(double y0, double x0) {
        long iy = Double.doubleToRawLongBits(y0);
        long ix = Double.doubleToRawLongBits(x0);
        long aiy = iy << 1;
        long aix = ix << 1;
        final long inf2 = 0x7ffL << 53;                                 // |x| = Inf, doubled
        if (!CoreMathSupport.ltu(aiy, inf2) || !CoreMathSupport.ltu(aix, inf2)) {   // NaN or Inf
            if (CoreMathSupport.ltu(inf2, aiy) || CoreMathSupport.ltu(inf2, aix)) {
                return y0 + x0;                                         // if y or x is sNaN, returns qNaN and raises invalid
            }
            // Now neither y nor x is NaN, but at least one is +Inf or -Inf
            if (aiy == inf2 && aix == inf2) {                           // both y and x are +/-Inf
                // atan2(+/-Inf, -Inf) = +/-3pi/4; atan2(+/-Inf, +Inf) = +/-pi/4
                int k = (int) (ix >>> 63);
                return Math.copySign(CoreMathInverseTrigTables.atan2Finf(2 * k + 1), y0)
                        + Math.copySign(CoreMathInverseTrigTables.atan2Finf(2 * k), y0);
            }
            // now only one of y and x is +/-Inf
            if (aix == inf2) {
                if (x0 < 0) {
                    return Math.copySign(PI_H, y0) + Math.copySign(PI_L, y0);
                }
                // atan2(+/-0, x) = +/-0 for x > 0; atan2(+/-y, +Inf) = +/-0 for finite y > 0
                return Math.copySign(0.0, y0);
            }
            // now y = +/-Inf: atan2(+/-Inf, x) = +/-pi/2 for finite x
            return Math.copySign(PI_OVER2_H, y0) + Math.copySign(PI_OVER2_L, y0);
        }
        if (aiy == 0L || aix == 0L) {
            if (aiy == 0L && aix == 0L) {
                if (ix == 0L) {                                         // atan2(+/-0, +0) = +/-0
                    return y0;
                }
                // atan2(+/-0, -0) = +/-pi
                return (iy == 0L) ? PI_H + PI_L : -PI_H - PI_L;
            }
            // only one of y and x is zero
            if (aiy == 0L) {
                // atan2(+/-0, x) = +/-0 for x > 0
                if (x0 > 0) {
                    return y0 / x0;
                }
                // atan2(+/-0, x) = +/-pi for x < 0
                return ((iy >>> 63) == 0L) ? PI_H + PI_L : -PI_H - PI_L;
            }
            // now only x is zero: atan2(y, +/-0) = -pi/2 for y < 0, +pi/2 for y > 0
            return (y0 > 0) ? PI_OVER2_H + PI_OVER2_L : -PI_OVER2_H - PI_OVER2_L;
        }
        return 0;
    }
}
