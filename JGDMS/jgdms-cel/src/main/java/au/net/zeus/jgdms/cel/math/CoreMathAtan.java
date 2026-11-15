/*
 * CoreMathAtan: Java port of CORE-MATH's binary64 arctangent, src/binary64/atan/atan.c at commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7,
 * round-to-nearest path only.
 *
 * DERIVATION: this file is a Java port, that is a modified and derived work, of
 * src/binary64/atan/atan.c of the CORE-MATH project at commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7.
 * It is not part of CORE-MATH and is not endorsed by its authors. The copyright and permission notice below are the upstream's, reproduced without change.
 *
 * Copyright (c) 2023-2026 Alexei Sibidanov <sibid@uvic.ca>.
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
 * Correctly-rounded binary64 arctangent, a port of CORE-MATH's {@code cr_atan} ({@code atan.c:231-286})
 * with its fast path and its accurate path {@code as_atan_refine2} ({@code atan.c:160-229}).
 * Round-to-nearest only.
 *
 * <h2>Structure</h2>
 * <ul>
 *   <li>{@code |x| < 0x1p-27}: {@code atan(x)} is {@code fma(-2^-54, x, x)} (C line 244); zero returns itself, so
 *       {@code atan(-0.0) = -0.0} (C line 238).</li>
 *   <li>{@code 2^-27 <= |x| < 0x1.b21c475e6362ap-8}: a Taylor polynomial in {@code x} with a relative error bound
 *       (C lines 251-256).</li>
 *   <li>{@code 0x1.b21c475e6362ap-8 <= |x| <= 0x1.2ded8e34a9035p+7}: table reduction by {@code A[i]}, an index found
 *       from a crude piecewise quadratic ({@code c[][3]}, C lines 270-277), then a polynomial in
 *       {@code h = (x - ta)/(1 + x*ta)}.</li>
 *   <li>{@code |x| > 0x1.2ded8e34a9035p+7}: {@code atan(x) = +-pi/2 - atan(1/x)}; for {@code |x| >= 0x1.d02967c31cdb5p+53}
 *       the result is {@code +-pi/2} rounded (C lines 259-267).</li>
 *   <li>When the fast path's two bounds {@code ub} and {@code lb} differ it calls {@code as_atan_refine2}, a
 *       double-double evaluation with a table of exceptional inputs.</li>
 * </ul>
 *
 * <h2>Proof status, and what the evidence is (read 2026-11-12)</h2>
 * CORE-MATH's homepage lists <b>no published correctness proof for atan</b> (Chisel's web check of 2026-11-12).
 * This port's correctness therefore rests on (a) the C authors' exhaustive worst-case search recorded in
 * {@code atan.wc} ({@code CoreMathInverseTrigSlowTest} runs every entry of it), (b) the differential tests against
 * {@link CrMath} on at least 5,000,000 seeded random inputs, and (c) the fidelity of the port to the C.
 * That is weaker evidence than a proof, and it is the reason the random population of this function is larger than
 * sine's.
 *
 * <h2>Which error constants the tests can and cannot see by behaviour (Fable review of phase B1, 2026-11-12; DIAGNOSTIC)</h2>
 * Fable (claude-fable-5-2) compiled copies of the port with one constant scaled and compared their bits with the production class (which equals
 * {@code CrMath} on the same entries in the slow tier). Population: every finite in-domain entry of the function's {@code .wc} file and, for atan and
 * atan2, 2,000,000 seeded random inputs. Result ("x 0.5" = the constant halved): 
 * table branch {@code ATAN_E} (0x3.fp-52): x 0.5 gives 0 of 55,764 and 0 of 2,000,000, the first scale with a mismatch is x 0.40 (2 of 55,764),
 * x 0.25 gives 540 of 55,764 and 447 of 2,000,000; Taylor branch {@code ATAN_EPS_P}/{@code ATAN_EPS_M} (0x1.6p-50, 0x1.6p-51): x 0.5 gives 7 of
 * 55,764 (the first detecting entry, 0x1.2926e52e5d5c7p-11, is line 6,390 of {@code atan.wc}, beyond the committed golden) and 0 of 2,000,000,
 * x 0.25 gives 209 of 55,764 and 0 of 2,000,000. So the tests see a halved table-branch constant not at all and a halved Taylor-branch constant only
 * through the full {@code .wc} run; a constant between about x 0.5 and x 1 is invisible to behaviour. The constants are therefore guarded by
 * {@link CoreMathInverseTrigTables} (named constants, committed digest, comparison with the C text), not by results. A plausible cause of the
 * margin (HYPOTHESIS): CORE-MATH sized its constants for directed rounding without FMA, where per-operation errors are up to twice those of
 * round-to-nearest.
 *
 * <h2>What differs from the C, and why</h2>
 * <ul>
 *   <li>The C falls from the fast path into {@code as_atan_refine2} by a call. Here {@link #fast} returns
 *       {@code NaN} (never a valid result for a finite argument) when the bounds differ, {@link #accurate} forces the
 *       refinement from the same fast-path approximation, and {@link #atan} is the C's behaviour. One private method
 *       {@code run} holds the C's control flow once, so the three differ only in the choice at the single place where the
 *       C decides.</li>
 *   <li>{@code muldd_acc}, {@code adddd}, {@code polydd} return two values in the C (through a pointer); in this cold
 *       path the second is passed in a one-element array. {@code fasttwosum} is the high word inline and
 *       {@link CoreMathSupport#fastTwoSumLow} for the low word.</li>
 *   <li>The C compares {@code ((t0.u>>52)&0x7ff)-((t1.u>>52)&0x7ff) > 103} in unsigned 64-bit arithmetic, so a
 *       negative difference wraps to a huge value and the test is true; the Java uses {@link Long#compareUnsigned}.</li>
 *   <li>{@code errno} (ERANGE on underflow) is dropped: Java has none. The C reads no rounding mode in this file
 *       ({@code grep fegetround}, 0 hits), so no directed-rounding branch is dropped.</li>
 *   <li>{@code Math.fma} is {@code __builtin_fma}; Java never contracts {@code a*b+c} into an fma on its own, the
 *       C compiler may (the CORE-MATH README says the code must work with contraction everywhere or nowhere).</li>
 * </ul>
 *
 * <h2>Inputs</h2>
 * Finite {@code x}, zero included. NaN returns {@code x + x}; the provider {@link CoreMath} never lets NaN or an infinity
 * reach this class (the evaluator's contract excludes them and {@code CrMath} fails loudly on them, so {@code CoreMath}
 * delegates them to it).
 */
final class CoreMathAtan {

    private CoreMathAtan() {}

    /** The three ways to leave {@link #run}: the C's behaviour, the fast path alone, the accurate path forced. */

    /** C {@code cr_atan} ({@code atan.c:231-286}). */
    static double atan(double x) {
        return run(x, MODE_NORMAL);
    }

    /**
     * The fast path alone for finite {@code |x|} in the range where it ends with a rounding test
     * ({@code 0x1p-27 <= |x| < 0x1.d02967c31cdb5p+53}): the correctly rounded result, or {@code NaN} if its error bound
     * cannot decide the rounding. Outside that range the C's early exits answer, and so does this.
     */
    static double fast(double x) {
        return run(x, MODE_FAST);
    }

    /**
     * The accurate path alone: {@code as_atan_refine2} run from the fast path's approximation even when the fast
     * path would have decided, for {@code 0x1p-27 <= |x| < 0x1.d02967c31cdb5p+53} (the C's comment on
     * {@code as_atan_refine2}: "this routine might be called for 0x1p-27 <= |x|"). Outside that range the C's early
     * exits answer.
     */
    static double accurate(double x) {
        return run(x, MODE_ACCURATE);
    }

    private static double run(double x, int mode) {
        long t = Double.doubleToRawLongBits(x);
        long at = t & 0x7fffffffffffffffL;                          // |x|
        long i = (at >>> 51) - 2030L;                               // -2030 <= i <= 2065
        if (at < 0x3f7b21c475e6362aL) {
            // |x| < 0x1.b21c475e6362ap-8
            if (at == 0L) {
                return x;                                           // atan(+/-0) = +/-0
            }
            if (at < 0x3e40000000000000L) {                         // |x| < 0x1p-27
                // The C also sets errno = ERANGE for 0 < |x| < 2^-1022 (and for |x| = 2^-1022 rounding towards zero): dropped.
                return Math.fma(-0x1p-54, x, x);
            }
            double x2 = x * x;
            double x3 = x * x2;
            double x4 = x2 * x2;
            double f = x3 * ((CoreMathInverseTrigTables.atanCh2(0) + x2 * CoreMathInverseTrigTables.atanCh2(1))
                    + x4 * (CoreMathInverseTrigTables.atanCh2(2) + x2 * CoreMathInverseTrigTables.atanCh2(3)));
            double epsp = f * CoreMathInverseTrigTables.ATAN_EPS_P;
            double epsm = f * CoreMathInverseTrigTables.ATAN_EPS_M;
            double ub = (f + epsp) + x;
            double lb = (f - epsm) + x;
            return finish(x, lb, ub, mode);
        }
        double h;
        double ah;
        double al;
        if (at > 0x4062ded8e34a9035L) {
            // |x| > 0x1.2ded8e34a9035p+7
            ah = Math.copySign(CoreMathInverseTrigTables.PI2_H, x);
            al = Math.copySign(CoreMathInverseTrigTables.PI2_L, x);
            if (at >= 0x434d02967c31cdb5L) {
                // |x| >= 0x1.d02967c31cdb5p+53
                if (at > (0x7ffL << 52)) {
                    return x + x;                                   // NaN
                }
                return ah + al;
            }
            h = -1.0 / x;
        } else {
            // now 0x1.b21c475e6362ap-8 <= |x| <= 0x1.2ded8e34a9035p+7 thus 1 <= i <= 30
            long u = t & (-1L >>> 13);
            long ut = u >>> (51 - 16);
            long ut2 = (ut * ut) >>> 16;
            int ci = (int) i;
            i = (((long) CoreMathInverseTrigTables.atanC(3 * ci) << 16)
                    + ut * CoreMathInverseTrigTables.atanC(3 * ci + 1)
                    - ut2 * CoreMathInverseTrigTables.atanC(3 * ci + 2)) >>> (16 + 9);
            double sg = Math.copySign(1.0, x);
            int ai = (int) i;
            double ta = sg * CoreMathInverseTrigTables.atanA(2 * ai);
            double id = sg * (double) i;
            al = sg * CoreMathInverseTrigTables.atanA(2 * ai + 1) + CoreMathInverseTrigTables.ATAN_AL_FAST * id;
            h = (x - ta) / (1 + x * ta);
            ah = CoreMathInverseTrigTables.ATAN_PI128_H * id;
        }
        double h2 = h * h;
        double h4 = h2 * h2;
        double f = (CoreMathInverseTrigTables.atanCh(0) + h2 * CoreMathInverseTrigTables.atanCh(1))
                + h4 * (CoreMathInverseTrigTables.atanCh(2) + h2 * CoreMathInverseTrigTables.atanCh(3));
        al = Math.fma(h, f, al);
        double e = h * CoreMathInverseTrigTables.ATAN_E;
        double ub = (al + e) + ah;
        double lb = (al - e) + ah;
        return finish(x, lb, ub, mode);
    }

    /** The one place where the C decides between {@code ub} and {@code as_atan_refine2} (atan.c:255-256 and :284-285). */
    private static double finish(double x, double lb, double ub, int mode) {
        if (ub == lb) {
            if (mode != MODE_ACCURATE) {
                return ub;
            }
        } else if (mode == MODE_FAST) {
            return Double.NaN;
        }
        return refine2(x, ub);
    }

    /** C {@code adddd} ({@code atan.c:49-53}): returns the high word, stores the low word in {@code l[0]}. */
    private static double adddd(double xh, double xl, double ch, double cl, double[] l) {
        double s = xh + ch;
        double d = s - xh;
        l[0] = ((ch - d) + (xh + (d - s))) + (xl + cl);
        return s;
    }

    /** C {@code muldd_acc} ({@code atan.c:55-59}): returns the high word, stores the low word in {@code l[0]}. */
    private static double mulddAcc(double xh, double xl, double ch, double cl, double[] l) {
        double ahlh = ch * xl;
        double alhh = cl * xh;
        double ahhh = ch * xh;
        double ahhl = Math.fma(ch, xh, -ahhh);
        ahhl += alhh + ahlh;
        double s = ahhh + ahhl;                                     // fasttwosum(ahhh, ahhl, l)
        l[0] = CoreMathSupport.fastTwoSumLow(ahhh, ahhl, s);
        return s;
    }

    /**
     * C {@code polydd} of atan.c ({@code atan.c:61-72}) with the table {@code ch[][2]} of {@code as_atan_refine2}
     * and {@code n = 3}: {@code l[0]} is the C's {@code *l} on entry and the low word of the result on exit.
     */
    private static double polydd(double xh, double xl, double[] l) {
        int i = 2;                                                  // n - 1
        double c0 = CoreMathInverseTrigTables.atanR2Ch(2 * i);
        double c1 = CoreMathInverseTrigTables.atanR2Ch(2 * i + 1);
        double ch = c0 + l[0];
        double cl = (c0 - ch) + l[0] + c1;
        double[] cll = new double[1];
        while (--i >= 0) {
            cll[0] = cl;
            ch = mulddAcc(xh, xl, ch, cl, cll);
            cl = cll[0];
            double a0 = CoreMathInverseTrigTables.atanR2Ch(2 * i);
            double a1 = CoreMathInverseTrigTables.atanR2Ch(2 * i + 1);
            double th = ch + a0;
            double tl = (a0 - th) + ch;
            ch = th;
            cl += tl + a1;
        }
        l[0] = cl;
        return ch;
    }

    /** C {@code as_atan_refine2} ({@code atan.c:160-229}): {@code a} is the fast path's approximation of {@code atan(x)}. */
    private static double refine2(double x, double a) {
        double phi = Math.abs(a) * CoreMathInverseTrigTables.ATAN_RCP + 256.5;     // 256.5 < phi < 384.5
        long i = (Double.doubleToRawLongBits(phi) >>> (52 - 8)) & 0xffL;   // 0 <= i <= 128
        double h;
        double hl;
        if (i == 128L) {
            h = -1.0 / x;
            hl = Math.fma(h, x, 1) * h;
        } else {
            double ta = Math.copySign(CoreMathInverseTrigTables.atanA(2 * (int) i), x);
            double zta = x * ta;
            double ztal = Math.fma(x, ta, -zta);
            double zmta = x - ta;
            double v = 1 + zta;
            double d = 1 - v;
            double ev = (d + zta) - ((d + v) - 1) + ztal;
            double r = 1.0 / v;
            double rl = (Math.fma(r, -v, 1.0) - ev * r) * r;
            h = r * zmta;
            hl = Math.fma(r, zmta, -h) + rl * zmta;
        }
        double[] lo = new double[1];
        double h2 = mulddAcc(h, hl, h, hl, lo);
        double h2l = lo[0];
        double h4 = h2 * h2;
        double h3 = mulddAcc(h, hl, h2, h2l, lo);
        double h3l = lo[0];
        lo[0] = h2 * ((CoreMathInverseTrigTables.atanR2Cl(0) + h2 * CoreMathInverseTrigTables.atanR2Cl(1))
                + h4 * (CoreMathInverseTrigTables.atanR2Cl(2) + h2 * CoreMathInverseTrigTables.atanR2Cl(3)));   // C: double fl = ...
        double f = polydd(h2, h2l, lo);
        double fl = lo[0];
        lo[0] = fl;
        f = mulddAcc(h3, h3l, f, fl, lo);
        fl = lo[0];
        double ah;
        double al;
        double at;
        if (i == 0L) {
            ah = h;
            al = f;
            at = fl;
        } else {
            double df = 0;
            if (i < 128L) {
                df = Math.copySign(1.0, x) * CoreMathInverseTrigTables.atanA(2 * (int) i + 1);
            }
            double id = Math.copySign((double) i, x);
            ah = CoreMathInverseTrigTables.ATAN_PI128_H * id;
            al = CoreMathInverseTrigTables.ATAN_PI128_L * id;
            at = CoreMathInverseTrigTables.ATAN_PI128_S * id;
            al = adddd(al, at, df, 0, lo);
            at = lo[0];
            al = adddd(al, at, h, hl, lo);
            at = lo[0];
            al = adddd(al, at, f, fl, lo);
            at = lo[0];
        }
        double v0 = ah + al;                                        // fasttwosum(ah, al, &v2)
        double v2 = CoreMathSupport.fastTwoSumLow(ah, al, v0);
        double v1 = v2 + at;                                        // fasttwosum(v2, at, &v2)
        v2 = CoreMathSupport.fastTwoSumLow(v2, at, v1);
        double ax = Math.abs(x);
        long t0 = Double.doubleToRawLongBits(v0);
        long t1 = Double.doubleToRawLongBits(v1);
        if (((t1 + 1) & (-1L >>> 12)) <= 2L
                || Long.compareUnsigned(((t0 >>> 52) & 0x7ffL) - ((t1 >>> 52) & 0x7ffL), 103L) > 0) {
            // The unsigned spelling is the C's (atan.c:202, u64 arithmetic). It cannot be told from a signed one by any input of the domain
            // (Opus LOW-3 and Fable Q2, 2026-11-12; an equivalent mutant, not a test gap): the two differ only when the exponent field of v1
            // exceeds that of v0, but v1 = RN(v2 + at) with v2 the rounding residue of v0 = RN(ah + al) (at most half an ulp of v0, since
            // |al| < |ah| on every branch) and at a third-order tail, so |v1| < 2^-50 |v0|. Fable measured the smallest e(v0) - e(v1) = 52 over
            // 6,216,144 calls of this routine, 0 negative (a DIAGNOSTIC probe copy). Keep the unsigned compare: it is what the C does.
            for (int j = 0; j < CoreMathInverseTrigTables.atanDbRows(); j++) {
                if (ax == CoreMathInverseTrigTables.atanDb(3 * j)) {
                    return Math.copySign(CoreMathInverseTrigTables.atanDb(3 * j + 1), x)
                            + Math.copySign(1.0, x) * CoreMathInverseTrigTables.atanDb(3 * j + 2);
                }
            }
            if ((t1 & (-1L >>> 12)) == 0L) {
                long w = Double.doubleToRawLongBits(v2);
                if (((w ^ t1) >>> 63) != 0L) {
                    t1--;
                } else {
                    t1++;
                }
                v1 = Double.longBitsToDouble(t1);
            }
        }
        return v1 + v0;
    }
}
