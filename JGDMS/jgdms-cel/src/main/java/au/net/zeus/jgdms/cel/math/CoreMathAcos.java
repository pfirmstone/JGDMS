/*
 * CoreMathAcos: Java port of CORE-MATH's binary64 arc cosine, src/binary64/acos/acos.c at commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7,
 * round-to-nearest path only.
 *
 * DERIVATION: this file is a Java port, that is a modified and derived work, of
 * src/binary64/acos/acos.c of the CORE-MATH project at commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7.
 * It is not part of CORE-MATH and is not endorsed by its authors. The copyright and permission notice below are the upstream's, reproduced without change.
 *
 * Copyright (c) 2024-2025 Alexei Sibidanov.
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

import static au.net.zeus.jgdms.cel.math.CoreMathSupport.MODE_ACCURATE;
import static au.net.zeus.jgdms.cel.math.CoreMathSupport.MODE_FAST;
import static au.net.zeus.jgdms.cel.math.CoreMathSupport.MODE_NORMAL;

/**
 * Correctly-rounded binary64 arc cosine, a port of CORE-MATH's {@code cr_acos} ({@code acos.c:140-290}) with its
 * fast path and its accurate path {@code as_acos_refine} ({@code acos.c:292-431}), which carries five hard-coded
 * exceptional inputs. Round-to-nearest only.
 *
 * <h2>Structure</h2>
 * <ul>
 *   <li>{@code |x| > 1}: the C returns a NaN and sets {@code errno = EDOM}; this port returns {@code NaN} and the
 *       provider {@link CoreMath} delegates such inputs to {@code CrMath}, which throws, like every other
 *       out-of-domain input. {@code x = 1}: {@code +0}; {@code x = -1}: pi rounded ({@code acos.c:222}).</li>
 *   <li>{@code |x| > 0.5}: {@code acos(x) = 2*asin(sqrt((1-x)/2))} for positive {@code x} and
 *       {@code pi - 2*asin(sqrt((1+x)/2))} for negative {@code x}, with the polynomial and table of the arcsine port.</li>
 *   <li>{@code |x| < 2^-15}: {@code pi/2 - x - x^3/6} in double-double arithmetic (C lines 246-259).</li>
 *   <li>{@code 2^-15 <= |x| <= 0.5}: {@code acos(x) = pi/2 - asin(x)} with the same polynomial.</li>
 * </ul>
 *
 * <h2>Proof status (recorded 2026-11-12; read, not run)</h2>
 * CORE-MATH publishes a proof: {@code acos.pdf} (Paul Zimmermann, dated 2026-05-29, pages 1 to 3 read on 2026-11-12 by the port's author and by two
 * reviewers): "We consider the CORE-MATH acos.c file from commit 7457819" (2026-04-20). CONFIRMED to apply to the ported code:
 * {@code git diff 7457819 018fa97c -- src/binary64/acos/acos.c} (56 lines, read in full) is a References comment block, comment rewrites, and the fix of
 * the generic {@code roundeven_finite} fallback ({@code ac1993d}, the {@code #else} branch; the Java uses {@link Math#rint}): no change to the compiled
 * path. The proof's constants equal the ported ones: Algorithm 1 has {@code eps = 0x1.34p-79} ({@link CoreMathInverseTrigTables#ACOS_EPS1}) and
 * {@code x0 = 0x1.cb3b3869747f4p-55}, Algorithm 3 line 11 has {@code 0x1.81p-52} ({@link CoreMathInverseTrigTables#ACOS_EPS_LO}). Its accurate path is the
 * one proved in {@code asin.pdf} section IV (see {@link CoreMathAsin}: above 2^-4 that proof's accurate-path cover is an exhaustive search of the
 * searched constants, so do not change the constants without redoing it).
 *
 * <h2>Which error constants the tests can and cannot see by behaviour (Fable review of phase B1, 2026-11-12; DIAGNOSTIC)</h2>
 * Fable (claude-fable-5-2) compiled copies of the port with one constant scaled and compared their bits with the production class (which equals
 * {@code CrMath} on the same entries in the slow tier). Population: every finite in-domain entry of the function's {@code .wc} file and, for atan and
 * atan2, 2,000,000 seeded random inputs. Result ("x 0.5" = the constant halved): 
 * {@code ACOS_EPS_HI} (0x1.8cp-52) and {@code ACOS_EPS_LO} (0x1.81p-52): x 0.5 gives 0 of 264,090 for each; x 0.25 gives 7 and 20 of 264,090. Not scaled by
 * Fable: {@code ACOS_EPS1} (0x1.34p-79) and the additive {@code ACOS_EPS_HI_ADD}. A constant halved is invisible to every {@code .wc} entry, so the constants are guarded
 * by {@link CoreMathInverseTrigTables} (named constants, digest, comparison with the C text), not by results. Fable also measured (2026-11-12) that the
 * six inputs the C comments and the proofs name as "fails with a smaller constant" (three for asin, three for acos) are hard cases for directed rounding
 * only, none near a midpoint, which is why round-to-nearest shows the margin; and that of the five hard-coded exceptions of {@code as_acos_refine} one
 * matters in round-to-nearest (x = 0x1.53ea6c7255e88p-4: 58 identical bits after the mantissa), the other four are no-ops in this arithmetic.
 *
 * <h2>What differs from the C, and why</h2>
 * <ul>
 *   <li>The C falls from the fast path into {@code as_acos_refine} by a call. Here {@link #fast} returns {@code NaN}
 *       when the bounds differ, {@link #accurate} forces the refinement from the same fast-path value, and
 *       {@link #acos} is the C's behaviour: one private method {@code run} holds the C's control flow once.</li>
 *   <li>{@code roundeven_finite} is {@link Math#rint} (ties to even).</li>
 *   <li>{@code errno} is dropped (Java has none); the C reads no rounding mode in this file, so no directed-rounding
 *       branch is dropped; the C comments "(no FMA)" and "(no FMA, rndz)" record failures of smaller error constants
 *       without fma or under directed rounding; Java is round-to-nearest and never contracts {@code a*b+c}.</li>
 *   <li>The C's exceptional-input test in {@code as_acos_refine} computes {@code (t.u + (1 << (e-1))) & m} in
 *       unsigned 64-bit arithmetic on the raw bits of {@code pl}; the Java does the same on {@code long}s.</li>
 * </ul>
 *
 * <h2>Inputs</h2>
 * Finite {@code x}; {@code |x| > 1} and NaN are delegated by the provider.
 */
final class CoreMathAcos {

    private CoreMathAcos() {}


    /** C {@code cr_acos} ({@code acos.c:140-290}). */
    static double acos(double x) {
        return run(x, MODE_NORMAL);
    }

    /**
     * The fast path alone for finite {@code |x| < 1}: the correctly rounded result, or {@code NaN} if its error bound
     * cannot decide the rounding. Outside that range the C's early exits answer.
     */
    static double fast(double x) {
        return run(x, MODE_FAST);
    }

    /**
     * The accurate path alone: {@code as_acos_refine} run from the fast path's value even when the fast path would
     * have decided, for finite {@code 0x1.cb3b3869747f4p-55 < |x| < 1} (the C's comment: for {@code |x|} at most that
     * value "we always have lb=ub, thus the accurate path is never called").
     */
    static double accurate(double x) {
        return run(x, MODE_ACCURATE);
    }

    private static double run(double x, int mode) {
        long bits = Double.doubleToRawLongBits(x);
        long ax = bits << 1;
        double t;
        double z;
        double zl;
        double jd;
        double f0h;
        double f0l;
        double eps;
        if (CoreMathSupport.ltu(0x7fc0000000000000L, ax)) {                // |x| > 0.5
            int k = (int) (bits >>> 63);
            f0h = CoreMathInverseTrigTables.acosOff(2 * k);
            f0l = CoreMathInverseTrigTables.acosOff(2 * k + 1);
            if (!CoreMathSupport.ltu(ax, 0x7fe0000000000000L)) {            // |x| >= 1
                if (ax == 0x7fe0000000000000L) {
                    return f0h + f0l;                                       // |x| = 1
                }
                if (CoreMathSupport.ltu(0xffe0000000000000L, ax)) {
                    return x + x;                                           // NaN
                }
                // |x| > 1: the C sets errno = EDOM and returns 0./0.; the provider never lets such an x reach here
                return Double.NaN;
            }
            // For x > 0.5 acos(x) = 2*asin(sqrt((1-x)/2)); for x < -0.5 acos(x) = pi - 2*asin(sqrt((1+x)/2)).
            t = 2 - 2 * Math.abs(x);
            jd = Math.rint(t * 0x1p5);
            z = Math.copySign(Math.sqrt(t), x);
            zl = Math.fma(z, z, -t) * ((-0.5 / t) * z);
            t = 0.25 * t - jd * 0x1p-7;
            // C: "fails with 0x1.8bp-52 for x=-0x1.3e827a2cd6d51p-1 (no FMA)"
            eps = Math.abs(z * t) * CoreMathInverseTrigTables.ACOS_EPS_HI + CoreMathInverseTrigTables.ACOS_EPS_HI_ADD;
        } else {                                                            // |x| <= 0.5
            f0h = CoreMathInverseTrigTables.PI2_H;
            f0l = CoreMathInverseTrigTables.PI2_L;
            if (!CoreMathSupport.ltu(0x7e00000000000000L, ax)) {            // |x| < 2^-15 (ax <= 0x7e00000000000000)
                // Avoids a spurious underflow for |x| <= x0 := 0x1.cb3b3869747f4p-55; for |x| <= x0 lb = ub always.
                double c = -0x1.5555555555555p-3;
                double v = (!CoreMathSupport.ltu(0x791967670d2e8fe8L, ax)) ? 0 : (x * x) * (c * x);
                double h = f0h + (-x);                                      // h = fasttwosum(f0h, -x, &w)
                double w = CoreMathSupport.fastTwoSumLow(f0h, -x, h);
                double l = v + (w + f0l);
                final double eps1 = CoreMathInverseTrigTables.ACOS_EPS1;
                double lb = h + (l - eps1);
                double ub = h + (l + eps1);
                return finish(x, lb, ub, mode);
            }
            // for 2^-15 <= |x| <= 0.5 acos(x) = pi/2 - asin(x), so the argument range for asin is the same for both
            // branches and the lookup tables are reused
            t = x * x;
            jd = Math.rint(t * 0x1p7);
            t = Math.fma(x, x, -0x1p-7 * jd);
            z = -x;
            zl = 0;
            // "eps < 0 for x > 0, but the rounding test is still correct" (C comment)
            // C: "for |x| < 2^-4 (case j=0), fails with 0x1.d3p-53 and x=0x1.7cb54339263fbp-12; for 2^-4 <= |x| < 0.5,
            // fails with 0x1.80p-52 and x=-0x1.fda6fee396f8p-2 (no FMA, rndz)"
            eps = (z * t) * CoreMathInverseTrigTables.ACOS_EPS_LO;
        }
        // asin(xh+xl) = (xh + xl)*(cc[j][0] + (cc[j][1] + t*Poly(t, cc[j]+2))) where t = xh^2 - j/128, j = round(128*xh^2)
        int j8 = 8 * (int) jd;
        double t2 = t * t;
        double d = t * ((CoreMathInverseTrigTables.asinAcosCc(j8 + 2) + t * CoreMathInverseTrigTables.asinAcosCc(j8 + 3))
                + t2 * ((CoreMathInverseTrigTables.asinAcosCc(j8 + 4) + t * CoreMathInverseTrigTables.asinAcosCc(j8 + 5))
                + t2 * (CoreMathInverseTrigTables.asinAcosCc(j8 + 6) + t * CoreMathInverseTrigTables.asinAcosCc(j8 + 7))));
        double fh = CoreMathInverseTrigTables.asinAcosCc(j8);
        double fl = CoreMathInverseTrigTables.asinAcosCc(j8 + 1) + d;
        double ahhh = z * fh;                                               // fh = muldd(z, zl, fh, fl, &fl)
        fl = CoreMathSupport.mulddLow(z, zl, fh, fl, ahhh);
        fh = ahhh;
        double sh = f0h + fh;                                               // fh = fastsum(f0h, f0l, fh, fl, &fl)
        fl = CoreMathSupport.fastSumLow(f0h, f0l, fh, fl, sh);
        fh = sh;
        double lb = fh + (fl - eps);
        double ub = fh + (fl + eps);
        return finish(x, lb, ub, mode);
    }

    /** The one place where the C decides between {@code lb} and {@code as_acos_refine} (acos.c:257-258 and :288-289). */
    private static double finish(double x, double lb, double ub, int mode) {
        if (lb != ub) {
            if (mode == MODE_FAST) {
                return Double.NaN;
            }
            return refine(x, lb);
        }
        return (mode == MODE_ACCURATE) ? refine(x, lb) : lb;
    }

    /** C {@code as_acos_refine} ({@code acos.c:292-431}): {@code phi} is the fast path's approximation of {@code acos(x)}. */
    private static double refine(double x, double phi) {
        // Consider x as cos(phi) then sin(phi) is ch + cl = sqrt(1-x^2). Using the angle rotation formula bring the
        // argument close to zero where the asin Taylor expansion works well:
        // acos(x) = asin(sqrt(1-x^2)) for x > 0, acos(x) = pi + asin(-sqrt(1-x^2)) for x < 0
        double s2 = x * x;
        double dx2 = Math.fma(x, x, -s2);                                   // s2 + dx2 = x^2
        double c2h = 1.0 + (-s2);                                           // fasttwosum(1.0, -s2, &c2l)
        double c2l = CoreMathSupport.fastTwoSumLow(1.0, -s2, c2h);
        c2l -= dx2;
        double c2hh = c2h + c2l;                                            // c2h = fasttwosum(c2h, c2l, &c2l)
        c2l = CoreMathSupport.fastTwoSumLow(c2h, c2l, c2hh);
        c2h = c2hh;                                                         // c2h + c2l approximates 1 - x^2

        double ch = Math.sqrt(c2h);
        // let eps = ch^2 - c2h, then sqrt(c2h + c2l) ~ ch + (c2l - eps)/ch/2
        double cl = (c2l - Math.fma(ch, ch, -c2h)) * (0.5 / ch);            // now ch + cl approximates sqrt(1 - x^2)

        int jf = (int) Math.rint(Math.abs(phi - CoreMathInverseTrigTables.PI2_H) * CoreMathInverseTrigTables.RCP_PI64);   // round(|phi - pi/2|*64/pi)
        // sin(pi/64*j) in the double-double format (smallest term first); the same table as in asin.c
        // 0 <= jf <= 32
        double chh = CoreMathInverseTrigTables.asinAcosS(2 * (32 - jf) + 1);
        double chl = CoreMathInverseTrigTables.asinAcosS(2 * (32 - jf));
        double sh = CoreMathInverseTrigTables.asinAcosS(2 * jf + 1);
        double sl = CoreMathInverseTrigTables.asinAcosS(2 * jf);
        // Ch + Cl approximates cos(jf*pi/64), Sh + Sl approximates sin(jf*pi/64)
        double ax = Math.abs(x);
        double dsh = ax - sh;
        double dsl = -sl;
        double dch = ch - chh;
        double dcl = cl - chl;

        final double magic = CoreMathInverseTrigTables.MAGIC;
        double sc = Math.fma(sh, dch, magic) - magic;
        double dSc = Math.fma(sh, dch, -sc);                                // Sc + dSc approximates Sh*dch, Sc a multiple of 2^-56, |Sc| < 2^-5

        double cs = Math.fma(chh, dsh, magic) - magic;
        double dCs = Math.fma(chh, dsh, -cs);                               // Cs + dCs approximates Ch*dsh

        double v = cs - sc;                                                 // exact: |Cs - Sc| a multiple of 2^-56 and < 2^-4
        double dv = (chh * dsl + chl * dsh) - (sh * dcl + sl * dch) - (dSc - dCs);
        double vs = v + dv;                                                 // v = fasttwosum(v, dv, &dv)
        dv = CoreMathSupport.fastTwoSumLow(v, dv, vs);
        v = vs;
        double sgn = Math.copySign(1.0, x);
        double jt = 32 - jf * sgn;                                          // pi/2 -/+ jf*pi/64 = jt*pi/64; 0 <= jt <= 64
        double v2 = v * v;                                                  // v2 = muldd(v, dv, v, dv, &dv2)
        double dv2 = CoreMathSupport.mulddLow(v, dv, v, dv, v2);
        v *= -sgn;
        dv *= -sgn;
        double[] lo = new double[1];
        lo[0] = v2 * (CoreMathInverseTrigTables.asinAcosCt(0)
                + v2 * (CoreMathInverseTrigTables.asinAcosCt(1) + v2 * CoreMathInverseTrigTables.asinAcosCt(2)));   // C: double fl = ...
        double fh = CoreMathSupport.polyddAsinAcos(v2, dv2, lo);
        double fl = lo[0];
        double ahhh = v * fh;                                               // fh = muldd(v, dv, fh, fl, &fl)
        fl = CoreMathSupport.mulddLow(v, dv, fh, fl, ahhh);
        fh = ahhh;                                                          // now fh + fl approximates -delta

        // h + l + s with h = 0x1.921fb54442dp-5, l = 0x1.8469898cc518p-53, s = -0x1.fc8f8cbb5bf6cp-102 approximates pi/64
        // with error bounded by 2^-155; since 0 <= jt <= 64, ph and pl are exact
        double ph = jt * CoreMathInverseTrigTables.PI64_H;
        double pl = CoreMathInverseTrigTables.PI64_L * jt;
        double ps = CoreMathInverseTrigTables.PI64_S * jt;
        double plNew = fh + pl;                                             // pl = sum(fh, fl, pl, ps, &ps): twosum high word first
        double psNew = (fl + ps) + CoreMathSupport.twoSumLow(fh, pl, plNew);
        pl = plNew;
        ps = psNew;
        double p1 = ph + pl;                                                // ph = fasttwosum(ph, pl, &pl)
        pl = CoreMathSupport.fastTwoSumLow(ph, pl, p1);
        ph = p1;
        double p2 = pl + ps;                                                // pl = fasttwosum(pl, ps, &ps)
        ps = CoreMathSupport.fastTwoSumLow(pl, ps, p2);
        pl = p2;
        p1 = ph + pl;                                                       // ph = fasttwosum(ph, pl, &pl)
        pl = CoreMathSupport.fastTwoSumLow(ph, pl, p1);
        ph = p1;
        p2 = pl + ps;                                                       // pl = fasttwosum(pl, ps, &ps)
        ps = CoreMathSupport.fastTwoSumLow(pl, ps, p2);
        pl = p2;
        long t = Double.doubleToRawLongBits(pl);
        long e = ((t >>> 52) & 0x7ffL) - 1023;
        e = 52 - (107 + e);
        e = e < 0 ? 0 : e;
        e = e > 52 ? 52 : e;
        long m = (1L << 52) - (1L << e);
        e = (e == 0) ? 64 : e;
        if (((t + (1L << (e - 1))) & m) == 0L) {
            // exceptional inputs, answered by a constant
            if (x == 0x1.ffffffffffdc0p-1) {
                return 0x1.8000000000024p-22 + 0x1p-76;
            }
            if (x == 0x1.53ea6c7255e88p-4) {
                return 0x1.7cdacb6bbe707p+0 + 0x1p-54;
            }
            if (x == 0x1.fd737be914578p-11) {
                return 0x1.91e006d41d8d8p+0 + 0x1.8p-53;
            }
            if (x == 0x1.fffffffffff70p-1) {
                return 0x1.8000000000009p-23 + 0x1p-77;
            }
            if (x == 0x1.390e6939cd1a6p-5) {
                return 0x1.8856a5d3296a4p+0 - 0x1p-109;
            }
            long w = Double.doubleToRawLongBits(ps);
            if (((w ^ t) >>> 63) != 0L) {
                t--;
            } else {
                t++;
            }
            pl = Double.longBitsToDouble(t);
        }
        return ph + pl;
    }
}
