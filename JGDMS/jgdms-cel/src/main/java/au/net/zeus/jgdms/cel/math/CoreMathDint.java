/*
 * CoreMathDint: Java port of the 128-bit "double-integer" arithmetic of CORE-MATH's binary64 tangent, the code that
 * src/binary64/tan/tan.c (lines 41-537, "code copied from dint.h and pow.[ch]") carries, at commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7,
 * round-to-nearest path only.
 *
 * DERIVATION: this file is a Java port, that is a modified and derived work, of
 * the dint64_t routines of src/binary64/tan/tan.c of the CORE-MATH project at commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7.
 * It is not part of CORE-MATH and is not endorsed by its authors. The copyright and permission notice below are the upstream's, reproduced without change.
 *
 * Copyright (c) 2022-2025 Paul Zimmermann and Tom Hubrecht
 * Copyright (c) 2022, 2023 CERN and Inria
 * Copyright (c) 2022-2025 CERN and Inria
 *
 * (The two CERN / Inria lines are those of src/binary64/pow/dint.h and src/binary64/pow/pow.h, pow.c, line 3 of each: tan.c:41 says the dint64_t code is "code copied from
 * dint.h and pow.[ch]", and tan.c does not repeat their copyright lines; pow/dint.h is the dint.h that holds the copied routines. They are carried here, verbatim, as the operator
 * ruled on 2026-11-12. An earlier version of this file also carried the line of src/binary64/log/dint.h, which the copied text does not support; the operator ruled on
 * 2026-11-12 to remove it.)
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
 * A 128-bit floating-point number {@code (-1)^sgn * (hi/2^64 + lo/2^128) * 2^ex} and its arithmetic, a mutable struct like the C's
 * {@code dint64_t} (CORE-MATH's {@code tan.c} writes through pointers and lets the result overlap an operand; each routine here reads every
 * operand it needs before it writes the result, which is the C's order). A normalised number has the top bit of {@code hi} set; zero has
 * {@code hi = 0}.
 *
 * <h2>How the 128-bit integer operations are emulated</h2>
 * The C's {@code u128} is {@link U128} (two {@code long} halves, exact mod 2^128, tested against {@link java.math.BigInteger}).
 * {@code mul_dint} computes {@code a.hi*b.hi + (a.hi*b.lo >> 64) + (a.lo*b.hi >> 64)} in 128 bits, which is exactly
 * {@link U128#mhUU} of the two mantissas (the sine port's truncated high product, the same arithmetic); {@code mul_dint_21} is the same with
 * {@code b.lo = 0}. The Newton start of {@code inv_dint} uses {@link U128#mulFull} and {@link U128#mul}. {@code CoreMathDintTest} compares
 * every routine with an exact {@link java.math.BigInteger} model written from the C text, with a control that drops one carry.
 *
 * <h2>What differs from the C, and why</h2>
 * <ul>
 *   <li>{@code fegetround()}: the C's {@code subnormalize_dint} switches on the rounding mode; only the {@code FE_TONEAREST} case is kept (the
 *       port is round-to-nearest only; Java has no directed rounding to read).</li>
 *   <li>{@code __builtin_clzll(0)} is undefined in the C; Java's {@link Long#numberOfLeadingZeros} gives 64. The C's comments prove the argument
 *       is non-zero at each call.</li>
 *   <li>A shift by 64 or more is undefined in the C; Java masks the count to 6 bits, as the x86 instruction does. The C's guards keep every
 *       shift below 64 (or below 128 for {@code u128}, where {@link U128} handles the whole range).</li>
 *   <li>{@code subnormalize_dint} and the overflow and underflow branches of {@code dint_tod} cannot be reached by {@code tan} (its
 *       results lie in [0x1.14ae72e6ba22fp-60, 0x1.d9ba9a7975636p+60] by the C's own comment in tan.wc, far from the subnormal and overflow ranges); they are ported all the same and {@code CoreMathDintTest} drives them directly.</li>
 * </ul>
 */
final class CoreMathDint {

    /** Upper 64 bits of the mantissa (unsigned). */
    long hi;
    /** Lower 64 bits of the mantissa (unsigned). */
    long lo;
    /** Binary exponent: the value is {@code (hi/2^64 + lo/2^128) * 2^ex}. */
    long ex;
    /** Sign: 0 or 1. */
    long sgn;

    CoreMathDint() {}

    CoreMathDint(long hi, long lo, long ex, long sgn) {
        this.hi = hi;
        this.lo = lo;
        this.ex = ex;
        this.sgn = sgn;
    }

    /** C {@code dint_zero_p} (tan.c:115-119): true if {@code a} is zero ({@code hi == 0}). */
    static boolean zeroP(CoreMathDint a) {
        return a.hi == 0L;
    }

    /** C {@code cmpu128} (tan.c:123): unsigned 128-bit three-way comparison of the mantissas. */
    private static int cmpU128(CoreMathDint a, CoreMathDint b) {
        int c = Long.compareUnsigned(a.hi, b.hi);
        if (c == 0) {
            c = Long.compareUnsigned(a.lo, b.lo);
        }
        return Integer.signum(c);
    }

    /** C {@code cmp_dint_abs} (tan.c:138-146): -1 if |a| &lt; |b|, 0 if equal, +1 if |a| &gt; |b|. */
    static int cmpAbs(CoreMathDint a, CoreMathDint b) {
        if (zeroP(a)) {
            return zeroP(b) ? 0 : -1;
        }
        if (zeroP(b)) {
            return 1;
        }
        int c1 = Long.compare(a.ex, b.ex);                           // C cmp(int64_t, int64_t): signed
        return c1 != 0 ? c1 : cmpU128(a, b);
    }

    /** C {@code cp_dint} (tan.c:149-153). */
    static void copy(CoreMathDint r, CoreMathDint a) {
        r.ex = a.ex;
        r.hi = a.hi;
        r.lo = a.lo;
        r.sgn = a.sgn;
    }

    /**
     * C {@code add_dint} (tan.c:155-257): {@code r = a + b}, error bounded by 2 ulps (1 ulp for equal signs), exact when Sterbenz applies (the C's claim, tan.c:157-158;
     * the one exception, MEASURED by the Fable review of 2026-11-12: |a| = 2|b| exactly with both mantissas 2^128 - 1 is off by half an ulp, 29 of 29 such pairs, 0 other inexact Sterbenz
     * cases in 200,000; unreachable from tan, whose {@code MAGIC} and {@code ONE} have mantissa 2^127; the port transcribes the C text). The
     * result may overlap {@code a} or {@code b} ({@code add_dint (q, &ONE, q)} and {@code add_dint (X, &MAGIC, X)} overlap {@code b}); every
     * operand word is read before {@code r} is written, except in the early returns, which copy one operand whole.
     */
    static void add(CoreMathDint r, CoreMathDint a, CoreMathDint b) {
        if ((a.hi | a.lo) == 0L) {
            copy(r, b);
            return;
        }
        switch (cmpAbs(a, b)) {
            case 0:
                if ((a.sgn ^ b.sgn) != 0L) {
                    copy(r, CoreMathTanTables.dintZero());
                    return;
                }
                copy(r, a);
                r.ex++;
                return;
            case -1: {
                CoreMathDint tmp = a;                                // swap operands: from here |a| > |b|
                a = b;
                b = tmp;
                break;
            }
            default:
                break;
        }
        // From now on, |A| > |B| thus a.ex >= b.ex
        U128 bigA = U128.of(a.hi, a.lo);
        U128 bigB = U128.of(b.hi, b.lo);
        long k = a.ex - b.ex;                                        // C uint64_t k
        if (k != 0L) {                                               // k > 0 (unsigned)
            // the right shift is only defined for 0 <= k < 128 (tan.c:191-194)
            bigB = CoreMathSupport.ltu(k, 128L) ? bigB.shr((int) k) : U128.of(0L, 0L);
        }
        U128 c;
        long sgn = a.sgn;
        long rex = a.ex;                                             // tentative exponent for the result
        if ((a.sgn ^ b.sgn) != 0L) {
            // different signs: C = A + (-B)
            c = bigA.sub(bigB);
            long ch = c.hi;
            long ex = ch != 0L ? Long.numberOfLeadingZeros(ch) : 64L + Long.numberOfLeadingZeros(c.lo);
            if (ex > 0L) {
                if (k == 1L) {                                       // Sterbenz case: B is not the truncated value here
                    c = bigA.shl((int) ex).sub(U128.of(b.hi, b.lo).shl((int) (ex - 1L)));
                } else {
                    c = bigA.shl((int) ex).sub(bigB.shl((int) ex));
                }
                rex -= ex;
                ex = Long.numberOfLeadingZeros(c.hi);
                // Fall through with the code for ex = 0.
            }
            c = c.shl((int) ex);
            rex -= ex;
        } else {
            c = bigA.add(bigB);
            if (c.ltU(bigA)) {
                U128 half = c.shr(1);
                c = U128.of(half.hi | (1L << 63), half.lo);          // ((u128) 1 << 127) | (C >> 1)
                rex++;
            }
        }
        r.ex = rex;
        r.sgn = sgn;
        r.hi = c.hi;
        r.lo = c.lo;
    }

    /**
     * C {@code mul_dint} (tan.c:262-292): {@code r = a * b}, error bounded by 6 ulps. The result may overlap {@code a} or {@code b} (the
     * C reads {@code b.hi}, {@code b.lo} and both words of {@code a} before it writes {@code r}, and reads the exponents and signs in the
     * statements that write them).
     */
    static void mul(CoreMathDint r, CoreMathDint a, CoreMathDint b) {
        U128 p = U128.of(a.hi, a.lo).mhUU(U128.of(b.hi, b.lo));
        long ex = p.hi >>> 63;                                       // ensure that r->hi starts with a 1
        p = p.shl((int) (1L - ex));
        long rex = a.ex + b.ex + ex - 1L;
        long rsgn = a.sgn ^ b.sgn;
        r.hi = p.hi;
        r.lo = p.lo;
        r.ex = rex;
        r.sgn = rsgn;
    }

    /** C {@code mul_dint_21} (tan.c:296-318): {@code r = a * b} assuming {@code b.lo = 0}, error bounded by 2 ulps. */
    static void mul21(CoreMathDint r, CoreMathDint a, CoreMathDint b) {
        long bh = b.hi;
        U128 p = U128.mulFull(a.hi, bh).add(U128.ofU64(Math.unsignedMultiplyHigh(a.lo, bh)));   // hi + (lo >> 64)
        long ex = p.hi >>> 63;
        p = p.shl((int) (1L - ex));
        long rex = a.ex + b.ex + ex - 1L;
        long rsgn = a.sgn ^ b.sgn;
        r.hi = p.hi;
        r.lo = p.lo;
        r.ex = rex;
        r.sgn = rsgn;
    }

    /**
     * C {@code inv_dint} (tan.c:333-425): {@code r} approximates {@code 1/a} (a non-zero), relative error below 2^-124.999 (4.003 ulps): a
     * table start {@code Tinv}, three Newton steps on a 64-bit inverse, then one on the 128-bit value. {@code r} must not overlap {@code a}
     * (the C writes {@code r} before its last read of {@code a}).
     */
    static void inv(CoreMathDint r, CoreMathDint a) {
        long h = a.hi;                                               // 2^63 <= h < 2^64
        int i = (int) ((h >>> 55) & 0xffL);
        long t = CoreMathTanTables.tinv(i);
        U128 one127 = U128.of(1L << 63, 0L);                         // (u128) 1 << 127
        // first Newton iteration
        U128 e = one127.sub(U128.mulFull(h, t));                     // exact, 0 < e < 2^119
        e = U128.ofU64(t).mul(e.shr(55));
        t += e.shr(72).lo;
        // second Newton iteration
        e = one127.sub(U128.mulFull(h, t));                          // 0 <= e < 2^111
        e = U128.ofU64(t).mul(e.shr(47));
        t += e.shr(80).lo;
        // third Newton iteration
        e = one127.sub(U128.mulFull(h, t));                          // 0 <= e < 2^95
        e = U128.ofU64(t).mul(e.shr(31));
        t += e.shr(96).lo;
        CoreMathDint q = new CoreMathDint();
        r.hi = t;
        r.lo = 0L;
        r.ex = 1L - a.ex;
        r.sgn = 1L;
        // Newton's iteration: r -> r + r*(1-a*r)
        mul21(q, a, r);                                              // -a*r, error <= 2 ulps
        r.sgn = 0L;                                                  // restore sign
        add(q, CoreMathTanTables.dintOne(), q);                      // 1-a*r, error <= 2 ulps
        mul(q, r, q);                                                // r*(1-a*r), error <= 6 ulps
        add(r, r, q);                                                // error <= 2 ulps
    }

    /** C {@code div_dint} (tan.c:429-440): {@code r} approximates {@code b/a} (a non-zero), relative error below 2^-123.67. */
    static void div(CoreMathDint r, CoreMathDint b, CoreMathDint a) {
        inv(r, a);                                                   // relative error bounded by 2^-124.999
        mul(r, r, b);                                                // error bounded by 6 ulps
    }

    /** C {@code dint_fromd} (tan.c:443-455): the dint of a NON-ZERO double. */
    static void fromD(CoreMathDint a, double b) {
        long bits = Double.doubleToRawLongBits(b);
        long e = (bits >>> 52) & 0x7ffL;                             // C fast_extract (tan.c:106-112)
        long m = (bits & (-1L >>> 12)) + (e != 0L ? (1L << 52) : 0L);
        long ex = e - 0x3feL;
        // |b| = 2^(ex-52)*hi
        long t = Long.numberOfLeadingZeros(m);                       // C uint32_t t
        a.sgn = b < 0.0 ? 1L : 0L;
        a.hi = m << t;
        a.ex = ex - (t > 11L ? t - 12L : 0L);
        // b = 2^ex*hi/2^64 where 1/2 <= hi/2^64 < 1
        a.lo = 0L;
    }

    /**
     * C {@code subnormalize_dint} (tan.c:457-486): rounds {@code a} to the subnormal grid when {@code a.ex <= -1023}, ties to even
     * ({@code FE_TONEAREST}; the C's downward and upward cases are not ported). The shift {@code ex} is below 64 for {@code a.ex >= -1074},
     * the C's domain.
     */
    static void subnormalize(CoreMathDint a) {
        if (a.ex > -1023L) {
            return;
        }
        long ex = -(1011L + a.ex);
        long hi = a.hi >>> ex;
        long md = (a.hi >>> (ex - 1L)) & 1L;
        long lo = ((a.hi & (-1L >>> ex)) != 0L || a.lo != 0L) ? 1L : 0L;
        hi += lo != 0L ? md : (hi & md);                             // case FE_TONEAREST
        a.hi = hi << ex;
        a.lo = 0L;
        if (a.hi == 0L) {
            a.ex++;
            a.hi = 1L << 63;
        }
    }

    /**
     * C {@code dint_tod} (tan.c:489-535): the double nearest to {@code a} (which {@link #subnormalize} may modify). The 53-bit significand is
     * built, the round bit and the sticky bit are added as {@code 2^-53} and {@code 2^-54} (so a half-way case rounds to even in the addition
     * itself), and the result is scaled by a power of two, in two factors so that the subnormal and overflow ranges round correctly.
     */
    static double toD(CoreMathDint a) {
        subnormalize(a);
        long ru = (a.hi >>> 11) | (0x3ffL << 52);
        double rd = 0.0;
        if (((a.hi >>> 10) & 1L) != 0L) {
            rd += 0x1p-53;
        }
        if ((a.hi & 0x3ffL) != 0L || a.lo != 0L) {
            rd += 0x1p-54;
        }
        if (a.sgn != 0L) {
            rd = -rd;
        }
        ru = ru | (a.sgn << 63);
        double rf = Double.longBitsToDouble(ru) + rd;
        double ef;
        if (a.ex > -1022L) {                                         // the result is a normal double
            if (a.ex > 1024L) {
                if (a.ex == 1025L) {
                    rf = rf * 0x1p+1;
                    ef = 0x1p+1023;
                } else {
                    rf = 0x1.fffffffffffffp+1023;
                    ef = 0x1.fffffffffffffp+1023;
                }
            } else {
                ef = Double.longBitsToDouble(((a.ex + 1022L) & 0x7ffL) << 52);
            }
        } else {
            if (a.ex < -1073L) {
                if (a.ex == -1074L) {
                    rf = rf * 0x1p-1;
                    ef = 0x1p-1074;
                } else {
                    rf = 0x0.0000000000001p-1022;
                    ef = 0x0.0000000000001p-1022;
                }
            } else {
                ef = Double.longBitsToDouble(1L << (a.ex + 1073L));
            }
        }
        return rf * ef;
    }
}
