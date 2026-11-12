/*
 * CoreMathTint: Java port of CORE-MATH's 192-bit arithmetic, src/binary64/atan2/tint.h at commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7.
 *
 * DERIVATION: this file is a Java port, that is a modified and derived work, of
 * src/binary64/atan2/tint.h of the CORE-MATH project at commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7.
 * It is not part of CORE-MATH and is not endorsed by its authors. The copyright and permission notice below are the upstream's, reproduced without change.
 *
 * Copyright (c) 2023-2024 Paul Zimmermann and Alexei Sibidanov
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

import static au.net.zeus.jgdms.cel.math.CoreMathSupport.ltu;

/**
 * The 192-bit floating-point arithmetic of CORE-MATH's {@code tint.h} that its binary64 {@code atan2}
 * uses for its accurate path: multiply, add, divide (by Newton and Karp-Markstein steps), and the
 * conversion of a 192-bit value to a {@code double}. A value is
 * {@code (-1)^sgn * (h/2^64 + m/2^128 + l/2^192) * 2^ex} with the top bit of {@code h} set, or zero.
 * <p>
 * <b>What changed from the C, and why.</b> {@code unsigned __int128} products and sums are written
 * with {@link Math#unsignedMultiplyHigh} and explicit unsigned carries on {@code long}s (the
 * mantissa is kept as the three words {@code h, m, l}; the C's {@code _h} is the pair {@code h:m}
 * read as one 128-bit integer, high word {@code h}). {@link Tint} is mutable, like the C struct, and the
 * operations take the result as their first argument and allow it to overlap an operand exactly as the
 * C does ("Overlap between r and a or b is allowed"); every function reads its operands into locals
 * before it writes the result words, in the C's order, so an overlap behaves as it does in the C.
 * <p>
 * <b>The correct-rounding failure branch.</b> {@code tint_tod} prints a message and calls
 * {@code exit(1)} when the 192-bit value lies within {@code err} units of the last word of a
 * rounding boundary or a midpoint (tint.h:409-416: "Unexpected worst-case found, please report to
 * core-math@inria.fr"). A Java library must not print and exit, and it must not guess: {@link #toD}
 * returns {@code NaN}, which is never a valid result of the conversion, and the caller
 * ({@code CoreMathAtan2}) returns it to {@code CoreMath}, which routes the call to the
 * correctly-rounded {@code CrMath} and counts it.
 * <p>
 * <b>Not ported</b> because {@code atan2.c} does not use them (grep, 2026-11-12): {@code print_tint},
 * {@code is_normalized}, {@code tint_zero_p} as a function (inlined as {@code h == 0}), {@code cmp},
 * {@code cmpu64}, {@code cmpu128} (inlined in {@link #cmpAbs}), {@code as_ldexp} (inlined in {@link #toD}).
 * <p>
 * Thread safety: stateless apart from four constants that are never passed as a result operand.
 */
final class CoreMathTint {

    private CoreMathTint() {}

    /** C {@code tint_t}: a mutable 192-bit float. {@code ex} and {@code sgn} are 64-bit as in the C. */
    static final class Tint {
        long h;
        long m;
        long l;
        long ex;
        long sgn;

        Tint() {}
    }

    private static Tint of(long[] v) {
        Tint t = new Tint();
        t.h = v[0];
        t.m = v[1];
        t.l = v[2];
        t.ex = v[3];
        t.sgn = v[4];
        return t;
    }

    /** C: tint.h:62 {@code ZERO}. Read-only: never the result operand of an operation. */
    private static final Tint ZERO = of(CoreMathInverseTrigTables.tintZero());
    /** C: tint.h:65 {@code ONE}. */
    private static final Tint ONE = of(CoreMathInverseTrigTables.tintOne());
    /** C: tint.h:69 {@code PI}, error < 2^-196.96. */
    private static final Tint PI = of(CoreMathInverseTrigTables.tintPi());
    /** C: tint.h:74 {@code PI2}, pi/2, error < 2^-197.96. */
    private static final Tint PI2 = of(CoreMathInverseTrigTables.tintPi2());

    /** C {@code cp_tint} (tint.h:84-89). */
    static void cp(Tint r, Tint a) {
        r.h = a.h;
        r.m = a.m;
        r.l = a.l;
        r.ex = a.ex;
        r.sgn = a.sgn;
    }

    /**
     * C {@code add_tint(z, &PI2, z)} (atan2.c:343, :352): {@code z = PI2 + z}, a method of its own so
     * that the shared constant is never handed out as an operand that could be written.
     */
    static void addPi2(Tint z) {
        add(z, PI2, z);
    }

    /** C {@code add_tint(z, &PI, z)} (atan2.c:364, :375). */
    static void addPi(Tint z) {
        add(z, PI, z);
    }

    /**
     * C {@code mul_tint} (tint.h:99-154): {@code r = a * b} with error under 10 ulps. The exact value
     * of the truncated product, which {@code CoreMathTintTest} checks against BigInteger, is
     * {@code ah*bh*2^64 + ah*bm + am*bh + floor(ah*bl/2^64) + floor(am*bm/2^64) + floor(al*bh/2^64)}
     * as a 192-bit integer in the units of {@code l}, normalised so that the top bit is set.
     */
    static void mul(Tint r, Tint a, Tint b) {
        long ex = a.ex + b.ex;
        long sgn = a.sgn ^ b.sgn;
        long ah = a.h;
        long am = a.m;
        long al = a.l;
        long bh = b.h;
        long bm = b.m;
        long bl = b.l;
        // rh = ah*bh (128 bits): r->h = rh >> 64, r->m = low 64 bits of rh
        long rH = Math.unsignedMultiplyHigh(ah, bh);
        long rM = ah * bh;
        // accumulate rm1 = ah*bm: r->l = low 64 bits, h = high 64 bits
        long rL = ah * bm;
        long h = Math.unsignedMultiplyHigh(ah, bm);
        rM += h;
        rH += ltu(rM, h) ? 1L : 0L;                                  // no overflow possible (C comment)
        // accumulate rm2 = am*bh
        long l = am * bh;
        h = Math.unsignedMultiplyHigh(am, bh);
        rL += l;
        long cm = ltu(rL, l) ? 1L : 0L;                              // carry at r->m
        rM += h;
        rH += ltu(rM, h) ? 1L : 0L;
        // accumulate (rl1 >> 64) + (rl2 >> 64) + (rl3 >> 64), each term below 2^64, so the sum is below 3*2^64
        long s1 = Math.unsignedMultiplyHigh(ah, bl);
        long s2 = Math.unsignedMultiplyHigh(am, bm);
        long s3 = Math.unsignedMultiplyHigh(al, bh);
        long s12 = s1 + s2;
        long carry = ltu(s12, s1) ? 1L : 0L;
        long s123 = s12 + s3;
        carry += ltu(s123, s12) ? 1L : 0L;
        l = s123;                                                    // C: l = (uint64_t) rl1
        cm += carry;                                                 // C: cm += rl1 >> 64
        rL += l;
        cm += ltu(rL, l) ? 1L : 0L;                                  // carry at r->m
        // accumulate cm
        rM += cm;
        rH += ltu(rM, cm) ? 1L : 0L;
        if ((rH >>> 63) == 0L) {                                     // normalize
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

    /**
     * C {@code cmp_tint_abs} (tint.h:171-185): -1, 0 or +1 as {@code |a|} is below, equal to or above
     * {@code |b|}.
     */
    static int cmpAbs(Tint a, Tint b) {
        if (a.h == 0L) {                                             // C: tint_zero_p
            return (b.h == 0L) ? 0 : -1;
        }
        if (b.h == 0L) {
            return +1;
        }
        if (a.ex != b.ex) {
            return (a.ex > b.ex) ? 1 : -1;
        }
        if (a.h != b.h) {                                            // C: cmpu128 of (h:m), high word first
            return ltu(b.h, a.h) ? 1 : -1;
        }
        if (a.m != b.m) {
            return ltu(b.m, a.m) ? 1 : -1;
        }
        if (a.l != b.l) {
            return ltu(b.l, a.l) ? 1 : -1;
        }
        return 0;
    }

    /** C {@code rshift} (tint.h:188-219): {@code a = b >> k} on the 192-bit significand (not the exponent). */
    static void rshift(Tint a, Tint b, int k) {
        long bh = b.h;
        long bm = b.m;
        long bl = b.l;
        if (k == 0) {
            a.h = bh;
            a.m = bm;
            a.l = bl;
        } else if (k < 64) {
            a.h = bh >>> k;
            a.m = (bh << (64 - k)) | (bm >>> k);
            a.l = (bm << (64 - k)) | (bl >>> k);                     // C: low 64 bits of (_h << (64-k)) | (_l >> k)
        } else if (k == 64) {
            a.h = 0L;
            a.m = bh;
            a.l = bm;
        } else if (k < 128) {
            int s = k - 64;                                          // 1..63
            a.h = 0L;
            a.m = bh >>> s;
            a.l = (bm >>> s) | (bh << (64 - s));                     // C: low 64 bits of _h >> (k-64)
        } else if (k < 192) {
            a.h = 0L;
            a.m = 0L;
            a.l = bh >>> (k - 128);                                  // C: _h >> (k-64), low 64 bits
        } else {
            a.h = 0L;
            a.m = 0L;
            a.l = 0L;
        }
    }

    /** C {@code lshift} (tint.h:222-252): {@code a = b << k} on the 192-bit significand, bits shifted out are lost. */
    static void lshift(Tint a, Tint b, int k) {
        long bh = b.h;
        long bm = b.m;
        long bl = b.l;
        if (k == 0) {
            a.h = bh;
            a.m = bm;
            a.l = bl;
        } else if (k < 64) {
            a.h = (bh << k) | (bm >>> (64 - k));
            a.m = (bm << k) | (bl >>> (64 - k));                     // C: _h = (_h << k) | (_l >> (64-k))
            a.l = bl << k;
        } else if (k == 64) {
            a.h = bm;
            a.m = bl;                                                // C: (_h << 64) | (u128) _l
            a.l = 0L;
        } else if (k < 128) {
            int s = k - 64;                                          // 1..63
            a.h = (bm << s) | (bl >>> (64 - s));
            a.m = bl << s;
            a.l = 0L;
        } else if (k < 192) {
            a.h = bl << (k - 128);                                   // C: (u128) _l << (k-64)
            a.m = 0L;
            a.l = 0L;
        } else {
            a.h = 0L;
            a.m = 0L;
            a.l = 0L;
        }
    }

    /** Leading zeros of the 192-bit value (h:m:l), as the C's nested conditional (tint.h:286-288). */
    private static long leading(long h, long m, long l) {
        if (h != 0L) {
            return Long.numberOfLeadingZeros(h);
        }
        if (m != 0L) {
            return 64 + Long.numberOfLeadingZeros(m);
        }
        return 128 + Long.numberOfLeadingZeros(l);
    }

    /**
     * C {@code add_tint} (tint.h:255-341): {@code r = a + b} with error bounded by 2 ulps for a
     * subtraction and by 1 ulp for an addition. {@code r} may overlap {@code a} or {@code b}.
     */
    static void add(Tint r, Tint a, Tint b) {
        int c = cmpAbs(a, b);
        if (c == 0) {                                                // |a| = |b|: 0 or 2*a, exact
            if ((a.sgn ^ b.sgn) != 0L) {
                cp(r, ZERO);
                return;
            }
            cp(r, a);
            r.ex++;
            return;
        }
        if (c < 0) {                                                 // swap operands
            Tint tmp = a;
            a = b;
            b = tmp;
        }
        // From now on |a| > |b|, thus a->ex >= b->ex
        Tint t = new Tint();
        long sh = a.ex - b.ex;
        rshift(t, b, (int) sh);
        if ((a.sgn ^ b.sgn) != 0L) {                                 // opposite signs: a subtraction
            t.l = a.l - t.l;
            long borrow = ltu(a.l, t.l) ? 1L : 0L;                   // C: t->_l > a->_l (after the update)
            long mDiff = a.m - t.m;
            long b1 = ltu(a.m, t.m) ? 1L : 0L;
            long b2 = ltu(mDiff, borrow) ? 1L : 0L;
            t.m = mDiff - borrow;
            t.h = a.h - t.h - b1 - b2;
            long ex = leading(t.h, t.m, t.l);
            if (Long.compareUnsigned(ex, 1L) <= 0 || sh == 0L) {
                lshift(r, t, (int) ex);
                r.ex = a.ex - ex;
            } else {                                                 // ex >= 2 and sh >= 1
                lshift(t, b, (int) (ex - sh));
                lshift(r, a, (int) ex);
                t.l = r.l - t.l;
                borrow = ltu(r.l, t.l) ? 1L : 0L;
                mDiff = r.m - t.m;
                b1 = ltu(r.m, t.m) ? 1L : 0L;
                b2 = ltu(mDiff, borrow) ? 1L : 0L;
                t.m = mDiff - borrow;
                t.h = r.h - t.h - b1 - b2;
                long ex1 = leading(t.h, t.m, t.l);
                lshift(r, t, (int) ex1);
                r.ex = a.ex - (ex + ex1);
            }
        } else {                                                     // same signs: an addition
            long ah = a.h;
            long am = a.m;
            long al = a.l;                                           // saved in case r = a
            long rl = al + t.l;
            long cl = ltu(rl, al) ? 1L : 0L;
            long sM = am + t.m;
            long cM = ltu(sM, am) ? 1L : 0L;
            long sH = ah + t.h;
            long ch = ltu(sH, ah) ? 1L : 0L;                         // carry out of the 128-bit sum
            long sH2 = sH + cM;
            ch += ltu(sH2, sH) ? 1L : 0L;
            long rM = sM + cl;                                       // C: r->_h += cl
            long cM2 = ltu(rM, sM) ? 1L : 0L;
            long rH = sH2 + cM2;
            ch += ltu(rH, sH2) ? 1L : 0L;                            // C: ch += r->_h < cl
            long ex;
            if (ch != 0L) {                                          // can be at most 1
                ex = a.ex + 1;
                rl = (rM << 63) | (rl >>> 1);
                long nM = (rH << 63) | (rM >>> 1);
                rH = (1L << 63) | (rH >>> 1);
                rM = nM;
            } else {
                ex = a.ex;
            }
            r.h = rH;
            r.m = rM;
            r.l = rl;
            r.ex = ex;
        }
        r.sgn = a.sgn;
    }

    /** C {@code tint_fromd} (tint.h:345-363): {@code a = x} exactly, for finite non-zero {@code x}. */
    static void fromD(Tint a, double x) {
        long u = Double.doubleToRawLongBits(x);
        a.sgn = u >>> 63;
        long ax = u & 0x7fffffffffffffffL;
        long e = ax >>> 52;
        if (e != 0L) {                                               // normal
            a.ex = e - 0x3fe;
            a.h = (1L << 63) | (ax << 11);
        } else {                                                     // subnormal
            e = Long.numberOfLeadingZeros(ax);
            a.ex = -0x3f2 - e;
            a.h = ax << e;
        }
        a.m = 0L;
        a.l = 0L;
    }

    /**
     * C {@code tint_tod} (tint.h:387-448): the {@code double} nearest {@code a}, assuming the value is within
     * {@code err} units of the last word {@code l} of the true value; {@code err = 0} converts a value that is a
     * {@code double} exactly. Returns {@code NaN} where the C prints "Unexpected worst-case found" and exits:
     * the 192-bit value is then too close to a rounding boundary or a midpoint for its error bound to decide
     * the rounding. {@code NaN} is never a valid result of this conversion (the exponent range is checked
     * first, and {@code h * 2^(ex-1)} is finite). The caller must treat it as "not correctly rounded" and
     * recompute with another method.
     */
    static double toD(Tint a, long err) {
        if (a.ex >= 1025) {                                          // overflow: |a| >= 2^1024
            return (a.sgn != 0L) ? -0x1p1023 - 0x1p1023 : 0x1p1023 + 0x1p1023;
        }
        if (a.ex <= -1074) {                                         // underflow: |a| < 2^-1074
            if (a.ex < -1074) {                                      // |a| < 2^-1075
                return ((a.sgn != 0L) ? -0x1p-1074 : 0x1p-1074) * 0.5;
            }
            // 2^-1075 <= |a| < 2^-1074; mid: |a| = 2^-1075
            boolean mid = a.h == (1L << 63) && a.m == 0L && a.l == 0L;
            return ((a.sgn != 0L) ? -0x1p-1074 : 0x1p-1074) * (mid ? 0.5 : 0.75);
        }
        long hh = a.h;
        long mm = a.m;
        long ll = a.l;
        int ex = (int) a.ex;
        long low = hh & 0x7ffL;                                      // low 11 bits of the top word
        // Cannot determine the rounding when (a) m = 0, the low 10 bits of h are zero and l < err, or
        // (b) m = 111...111, the low 10 bits of h are 1 and l > 2^64 - err.
        if (mm == 0L || ~mm == 0L) {
            if ((mm == 0L && (low == 0L || low == 0x400L) && ltu(ll, err))
                    || (~mm == 0L && (low == 0x3ffL || low == 0x7ffL) && ltu(~ll, err))) {
                return Double.NaN;
            }
        }
        if (ex <= -1022) {                                           // subnormal case
            int sh = -1021 - ex;                                     // 1 <= sh <= 52
            ll = (mm << (64 - sh)) | (ll >>> sh) | ((ll != 0L) ? 1L : 0L);
            mm = (hh << (64 - sh)) | (mm >>> sh);
            hh = hh >>> sh;
            low = hh & 0x7ffL;
            ex += sh;
        }
        double h = (double) (hh >>> 11);                             // significant bits from the top word, exact
        double l;
        if (err == 0L) {
            l = 0.0;                                                 // a double: low = 0, exact
        } else if (low < 0x400L) {
            l = 0.25;                                                // round to zero
        } else if (low > 0x400L) {
            l = 0.75;                                                // round away
        } else if (mm == 0L && ll == 0L) {                           // low = 0x400
            l = 0.5;                                                 // middle case
        } else {
            l = 0.75;                                                // round away
        }
        double s = (a.sgn != 0L) ? -1.0 : 1.0;                       // C: S[a->sgn], S = {1.0, -1.0}
        h = Math.fma(l, s, s * h);
        h *= 0x1p-52;
        // now -1021 <= ex <= 1024, so 2^(ex-1) neither underflows nor overflows; C: as_ldexp(1.0, ex - 1)
        return h * Double.longBitsToDouble(0x3ff0000000000000L + ((long) (ex - 1) << 52));
    }

    /**
     * C {@code inv_tint} (tint.h:453-492): {@code r ~ 1/A}, {@code A != 0}; for {@code 1 <= r <= 2}
     * the absolute error is below 2^-103.9.
     */
    static void inv(Tint r, Tint aa) {
        Tint q = new Tint();
        double a = toD(aa, 0L);                                      // exact
        boolean subnormal = Math.abs(a) < 0x1p-1022;                 // to simplify the error analysis, 0.5 <= a < 1
        if (subnormal) {
            a *= 0x1p53;
        }
        fromD(r, 1.0 / a);                                           // accurate to about 53 bits
        if (subnormal) {
            r.ex += 53;
        }
        mul(q, aa, r);                                               // approximates a*r
        q.sgn = 1L - q.sgn;                                          // -a*r
        add(q, ONE, q);                                              // approximates 1-a*r
        mul(q, r, q);                                                // approximates r*(1-a*r)
        add(r, r, q);
    }

    /** C {@code div_tint} (tint.h:496-533): {@code r ~ b/a} with relative error below 2^-185.53. */
    static void div(Tint r, Tint b, Tint a) {
        Tint y = new Tint();
        Tint z = new Tint();
        inv(y, a);                                                   // |Y - 1/a| < 2^-103.9
        mul(r, y, b);                                                // r approximates b/a
        mul(z, a, r);                                                // approximates a*r
        z.sgn = 1L - z.sgn;                                          // -a*r
        add(z, b, z);                                                // approximates b-a*r
        mul(z, y, z);                                                // approximates y*(b-a*r)
        add(r, r, z);
    }

    /** C {@code div_tint_d} (tint.h:536-543): {@code r ~ b/a} for two finite non-zero doubles. */
    static void divD(Tint r, double b, double a) {
        Tint aa = new Tint();
        Tint bb = new Tint();
        fromD(aa, a);
        fromD(bb, b);
        div(r, bb, aa);
    }
}
