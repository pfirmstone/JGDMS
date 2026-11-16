/*
 * CoreMathAsin: Java port of CORE-MATH's binary64 arcsine, src/binary64/asin/asin.c at commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7,
 * round-to-nearest path only.
 *
 * DERIVATION: this file is a Java port, that is a modified and derived work, of
 * src/binary64/asin/asin.c of the CORE-MATH project at commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7.
 * It is not part of CORE-MATH and is not endorsed by its authors. The copyright and permission notice below are the upstream's, reproduced without change.
 *
 * Copyright (c) 2022-2026 Alexei Sibidanov <sibid@uvic.ca>.
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
 * Correctly-rounded binary64 arcsine, a port of CORE-MATH's {@code cr_asin} ({@code asin.c:116-248}) with its
 * fast path, its accurate path {@code as_asin_refine} ({@code asin.c:250-345}) and its table of exceptional inputs
 * {@code as_asin_database} ({@code asin.c:347-394}). Round-to-nearest only.
 *
 * <h2>Structure</h2>
 * <ul>
 *   <li>{@code |x| > 1}: the C returns a NaN and sets {@code errno = EDOM}; this port returns {@code NaN} and the
 *       provider {@link CoreMath} delegates such inputs to {@code CrMath}, which throws, like every other
 *       out-of-domain input (the evaluator maps them to DOMAIN first). {@code |x| = 1}: {@code +-pi/2} rounded
 *       (C line 202).</li>
 *   <li>{@code |x| < 0x1.7137449123ef6p-26}: {@code asin(x) = fma(2^-55, x, x)} (C line 225; "|asin(x) - x| is less
 *       than half of ulp of asin(x)"); zero returns itself.</li>
 *   <li>{@code |x| <= 0.5}: a polynomial in {@code t = x^2 - j/128}, {@code j = round(128 x^2)}, with 33 coefficient
 *       rows ({@code cc}). {@code |x| > 0.5}: {@code asin(x) = +-pi/2 - 2*asin(sqrt((1-|x|)/2))}, the same polynomial
 *       on the reduced argument.</li>
 *   <li>The fast path returns when its two bounds agree; otherwise {@code as_asin_refine} rotates the angle to bring
 *       the argument near zero and evaluates a Taylor series in double-double arithmetic, and a final test sends the
 *       "hard" results to the exceptional-input table.</li>
 * </ul>
 *
 * <h2>Proof status (recorded 2026-11-12; read, not run)</h2>
 * CORE-MATH publishes a proof: {@code asin.pdf} (Paul Zimmermann, dated 2026-09-21, read on 2026-11-12 by the port's author and by two
 * reviewers). Three facts a maintainer needs, none of which is visible from the code:
 * <ol>
 *   <li><b>The commit its front page names is not the code its text proves.</b> The front page says "We consider the CORE-MATH asin.c file
 *       from commit 3ba8f60" (2026-04-27). That commit has {@code eps = |z*t|*0x1.0ep-52} for {@code |x| <= 0.5}. The text cites the later
 *       constants: section II.B records that with the factor {@code 0x1.76p-52} the input {@code x = 0x1.e4bf4521f41acp-2} "is not correctly
 *       rounded for rounding toward zero and no FMA contraction", so the validated constant is the ported {@code 0x1.77p-52}
 *       ({@link CoreMathInverseTrigTables#ASIN_EPS_LO}); section III says the search for {@code |x| > 0.5} "works with ... 0x1.99p-52"
 *       ({@link CoreMathInverseTrigTables#ASIN_EPS_HI}), also the ported value. Algorithm 1 line 10 prints {@code 0x1.10p-52}, which matches neither
 *       commit. Read the proof as a proof of the ported constants, not of {@code 3ba8f60}: under rounding toward zero the named commit returns the
 *       wrong value the text describes.</li>
 *   <li><b>How each range is covered.</b> {@code [0x1.7137449123ef6p-26, 2^-4]}: analytically (Sollya and Gappa for the fast path, its Theorem 1
 *       for the accurate path, for every x). {@code (2^-4, 0.5]} and {@code (0.5, 1)}: by exhaustive search in four rounding modes, with and
 *       without FMA contraction. Section IV: "for 2^-4 &lt; x &lt; 1 we performed an exhaustive search, which checks the accurate path is correct
 *       when it is called". So above 2^-4 the accurate path is covered only for the inputs that the SEARCHED fast path sends to it; with the searched
 *       (ported) constants that is every input this port sends. A wider constant is safe for what the fast path returns (rounding is monotone: a
 *       wider interval decides a subset of inputs with the same value) but NOT for the accurate path above 2^-4: it would send inputs the search never
 *       checked. Do not change {@code ASIN_EPS_HI} or {@code ASIN_EPS_LO} without redoing the search.</li>
 *   <li><b>Commits later than the proof.</b> {@code git diff 3ba8f60 018fa97c -- src/binary64/asin/asin.c} (128 lines, read in full on 2026-11-12;
 *       12 commits in between) has exactly one compiled-path semantic difference, the {@code eps} constant above. The rest is cosmetic or the generic
 *       fallback: the "hard" test rewritten from signed {@code dn, de} to an unsigned {@code gap} (the same predicate: {@code -2 <= dn <= 0} iff
 *       {@code gap} is 0, 1 or 2; {@code de > 47} iff {@code gap >> 52 > 47}), {@code jf} split into a double and an {@code int64}, {@code pih},
 *       {@code pil}, {@code pis} as names with {@code jt * pil} for {@code pil * jt} (multiplication commutes exactly), and the generic
 *       {@code roundeven_finite} fallback (the Java uses {@link Math#rint}). Three of those commits are later than the proof's date:
 *       {@code 0ce0cbc} (2026-10-20), {@code e168ea1} (2026-10-27) and {@code 7f09878} (2026-11-10, "patch proposed by Eric Hennenhoefer using AI
 *       (slightly modified)"); their net effect is inside that diff, read, not run. The proof's acknowledgements say the exhaustive search was once
 *       found incomplete, so re-record this section when {@code asin.c} changes upstream.</li>
 * </ol>
 * Evidence in this port, beyond the proof: every finite in-domain entry of {@code asin.wc} (249,126), 3,000,000 seeded random inputs, the goldens,
 * with the accurate path alone also run on every input (0 mismatches).
 *
 * <h2>Which error constants the tests can and cannot see by behaviour (Fable review of phase B1, 2026-11-12; DIAGNOSTIC)</h2>
 * Fable (claude-fable-5-2) compiled copies of the port with one constant scaled and compared their bits with the production class (which equals
 * {@code CrMath} on the same entries in the slow tier). Population: every finite in-domain entry of the function's {@code .wc} file and, for atan and
 * atan2, 2,000,000 seeded random inputs. Result ("x 0.5" = the constant halved): 
 * {@code ASIN_EPS_HI} (0x1.99p-52) and {@code ASIN_EPS_LO} (0x1.77p-52): x 0.5 gives 0 of 249,126 for each; x 0.25 gives 2 and 5 of 249,126. A constant
 * halved is invisible to every {@code .wc} entry, so the constants are guarded by {@link CoreMathInverseTrigTables} (named constants, digest, comparison
 * with the C text), not by results. A plausible cause of the margin (HYPOTHESIS): the constants were sized for directed rounding without FMA.
 *
 * <h2>What differs from the C, and why</h2>
 * <ul>
 *   <li>The C falls from the fast path into {@code as_asin_refine} by a call. Here {@link #fast} returns {@code NaN}
 *       when the bounds differ, {@link #accurate} forces the refinement from the same fast-path value, and
 *       {@link #asin} is the C's behaviour: one private method {@code run} holds the C's control flow once.</li>
 *   <li>{@code roundeven_finite} is {@link Math#rint} (ties to even). The C's {@code __asm__} and generic variants
 *       are all round-half-even.</li>
 *   <li>{@code errno} is dropped (Java has none); the C reads no rounding mode in this file, so no directed-rounding
 *       branch is dropped. The C comments "(rndz, no fma)" record failures of smaller error constants under
 *       directed rounding without fma; the Java is round-to-nearest only, and never contracts {@code a*b+c}.</li>
 *   <li>The C compares {@code ax = ix.u<<1} with unsigned 64-bit constants; the Java uses unsigned comparisons.</li>
 * </ul>
 *
 * <h2>Inputs</h2>
 * Finite {@code x}; {@code |x| > 1} and NaN are delegated by the provider.
 */
final class CoreMathAsin {

    private CoreMathAsin() {}


    /** C {@code cr_asin} ({@code asin.c:116-248}). */
    static double asin(double x) {
        return run(x, MODE_NORMAL);
    }

    /**
     * The fast path alone for finite {@code 0x1.7137449123ef6p-26 <= |x| < 1}: the correctly rounded result, or
     * {@code NaN} if its error bound cannot decide the rounding. Outside that range the C's early exits answer.
     */
    static double fast(double x) {
        return run(x, MODE_FAST);
    }

    /**
     * The accurate path alone: {@code as_asin_refine} run from the fast path's value even when the fast path would
     * have decided, for finite {@code 0x1.7137449123ef6p-26 <= |x| < 1}. Outside that range the C's early exits answer.
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
            f0h = CoreMathInverseTrigTables.asinOff(2 * k);
            f0l = CoreMathInverseTrigTables.asinOff(2 * k + 1);
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
            // for |x| > 0.5 range reduction by the double angle formula:
            // asin(x) = pi/2 - 2*asin(sqrt((1-x)/2)) and for x < -0.5 asin(x) = -pi/2 + 2*asin(sqrt((1-|x|)/2))
            t = 2 - 2 * Math.abs(x);
            jd = Math.rint(t * 0x1p5);
            z = Math.copySign(Math.sqrt(t), -x);
            zl = Math.fma(z, z, -t) * ((-0.5 / t) * z);
            t = 0.25 * t - jd * 0x1p-7;
            // C: "fails with 0x1.98p-52 and x=0x1.3f47056fc030ap-1 (rndz, no fma)"
            eps = Math.abs(z * t) * CoreMathInverseTrigTables.ASIN_EPS_HI;
        } else {                                                            // |x| <= 0.5
            // for |x| < 0x1.7137449123ef6p-26 |asin(x) - x| is less than half of ulp of asin(x)
            if (CoreMathSupport.ltu(ax, 0x7cae26e892247decL)) {
                // The C also sets errno = ERANGE for 0 < |x| < 2^-1022: dropped.
                return Math.fma(0x1p-55, x, x);
            }
            f0h = 0;
            f0l = 0;
            t = x * x;
            jd = Math.rint(t * 0x1p7);
            t = Math.fma(x, x, -0x1p-7 * jd);
            z = x;
            zl = 0;
            // C: "fails for 0x1.76p-52 with x=0x1.e4bf4521f41acp-2 (rndz, no FMA)"
            eps = Math.abs(z * t) * CoreMathInverseTrigTables.ASIN_EPS_LO;
        }
        // asin(xh+xl) = (xh + xl)*(cc[j][0] + (cc[j][1] + t*Poly(t, cc[j]+2))) where t = xh^2 - j/128, j = round(128*xh^2)
        int j8 = 8 * (int) jd;
        double t2 = t * t;
        double d = t * ((CoreMathInverseTrigTables.asinAcosCc(j8 + 2) + t * CoreMathInverseTrigTables.asinAcosCc(j8 + 3))
                + t2 * ((CoreMathInverseTrigTables.asinAcosCc(j8 + 4) + t * CoreMathInverseTrigTables.asinAcosCc(j8 + 5))
                + t2 * (CoreMathInverseTrigTables.asinAcosCc(j8 + 6) + t * CoreMathInverseTrigTables.asinAcosCc(j8 + 7))));
        double fh = CoreMathInverseTrigTables.asinAcosCc(j8);
        double fl = CoreMathInverseTrigTables.asinAcosCc(j8 + 1) + d;
        double ahhh = fh * z;                                               // fh = muldd(z, zl, fh, fl, &fl)
        fl = CoreMathSupport.mulddLow(z, zl, fh, fl, ahhh);
        fh = ahhh;
        double sh = f0h + fh;                                               // fh = fastsum(f0h, f0l, fh, fl, &fl)
        fl = CoreMathSupport.fastSumLow(f0h, f0l, fh, fl, sh);
        fh = sh;
        double lb = fh + (fl - eps);
        double ub = fh + (fl + eps);
        if (mode == MODE_ACCURATE || (lb != ub && mode == MODE_NORMAL)) {
            return refine(x, lb);
        }
        if (lb != ub) {                                                     // MODE_FAST
            return Double.NaN;
        }
        return lb;
    }

    /** C {@code as_asin_refine} ({@code asin.c:250-345}): {@code phi} is the fast path's approximation of {@code asin(x)}. */
    private static double refine(double x, double phi) {
        // Consider x as sin(phi) then cos(phi) is ch + cl = sqrt(1-x^2). Using the angle rotation formula bring the
        // argument close to zero where the asin Taylor expansion works well.
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

        double jf = Math.rint(Math.abs(phi) * CoreMathInverseTrigTables.RCP_PI64);
        int j = (int) jf;                                                  // jf = round(|phi|*64/pi), 0 <= j <= 32
        // sin(pi/64*j) in the double-double format (smallest term first); the same table as in acos.c
        double chh = CoreMathInverseTrigTables.asinAcosS(2 * (32 - j) + 1);
        double chl = CoreMathInverseTrigTables.asinAcosS(2 * (32 - j));
        double sh = CoreMathInverseTrigTables.asinAcosS(2 * j + 1);
        double sl = CoreMathInverseTrigTables.asinAcosS(2 * j);

        double ax = Math.abs(x);
        double dsh = ax - sh;
        double dsl = -sl;
        double dch = ch - chh;
        double dcl = cl - chl;

        final double magic = CoreMathInverseTrigTables.MAGIC;
        double sc = Math.fma(sh, dch, magic) - magic;
        double dSc = Math.fma(sh, dch, -sc);

        double cs = Math.fma(chh, dsh, magic) - magic;
        double dCs = Math.fma(chh, dsh, -cs);

        double v = cs - sc;
        double dv = (chh * dsl + chl * dsh) - (sh * dcl + sl * dch) - (dSc - dCs);
        double vs = v + dv;                                                 // v = fasttwosum(v, dv, &dv)
        dv = CoreMathSupport.fastTwoSumLow(v, dv, vs);
        v = vs;
        double sgn = Math.copySign(1.0, x);
        double jt = jf * sgn;                                               // the signed table index: -32 <= jt <= 32
        double v2 = v * v;                                                  // v2 = muldd(v, dv, v, dv, &dv2)
        double dv2 = CoreMathSupport.mulddLow(v, dv, v, dv, v2);
        v *= sgn;
        dv *= sgn;
        double[] lo = new double[1];
        lo[0] = v2 * (CoreMathInverseTrigTables.asinAcosCt(0)
                + v2 * (CoreMathInverseTrigTables.asinAcosCt(1) + v2 * CoreMathInverseTrigTables.asinAcosCt(2)));   // C: double fl = ...
        double fh = CoreMathSupport.polyddAsinAcos(v2, dv2, lo);
        double fl = lo[0];
        double ahhh = fh * v;                                               // fh = muldd(v, dv, fh, fl, &fl)
        fl = CoreMathSupport.mulddLow(v, dv, fh, fl, ahhh);
        fh = ahhh;

        // pih + pil + pis approximates pi/2^6; for every integer jt the initial ph and pl products are exact
        double ph = jt * CoreMathInverseTrigTables.PI64_H;
        double pl = jt * CoreMathInverseTrigTables.PI64_L;
        double ps = jt * CoreMathInverseTrigTables.PI64_S;

        double plNew = fh + pl;                                             // pl = fastsum(fh, fl, pl, ps, &ps): high word first
        ps = CoreMathSupport.fastSumLow(fh, fl, pl, ps, plNew);
        pl = plNew;
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

        long th = Double.doubleToRawLongBits(ph);
        long tl = Double.doubleToRawLongBits(pl);
        long tn = (th & (0x7ffL << 52)) - (53L << 52);
        tl &= -1L >>> 1;
        long gap = tn - tl;
        // C: "dn=-2 for x=0x1.c373ff4aad79bp-14 (rndn); de=48 for x=0x1.da4e0e6c717a5p-2 (rndz)"
        boolean hard = Long.compareUnsigned(gap, 2L) <= 0 || (gap >>> 52) > 47L;
        double res = ph + pl;
        if (hard) {
            res = database(x, res);
        }
        return res;
    }

    /** C {@code as_asin_database} ({@code asin.c:347-394}): the exceptional inputs, by binary search on {@code |x|}. */
    private static double database(double x, double f) {
        // db[] holds |x| for the exceptional cases, sorted by increasing first values; those marked * in the C are
        // required only without FMA contraction
        double ax = Math.abs(x);
        int a = 0;
        int b = CoreMathInverseTrigTables.asinDbRows();
        while (a + 1 < b) {                                                 // binary search with invariant db[a][0] <= x < db[b][0]
            int m = (a + b) / 2;
            if (CoreMathInverseTrigTables.asinDb(3 * m) <= ax) {
                a = m;
            } else {
                b = m;
            }
        }
        if (CoreMathInverseTrigTables.asinDb(3 * a) == ax) {
            double r1 = CoreMathInverseTrigTables.asinDb(3 * a + 1);
            double r2 = CoreMathInverseTrigTables.asinDb(3 * a + 2);
            return (x > 0) ? r1 + r2 : -r1 - r2;
        }
        return f;
    }
}
