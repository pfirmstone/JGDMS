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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.cel.math;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/**
 * An EXACT reference for tan(x) of a double, independent of the port, of {@link CrMath} and of CORE-MATH: pi by Machin's formula to 2,400 bits, {@code frac(x/(2 pi))} from the exact
 * value of the double, and the sine and cosine of the folded argument by their Taylor series in 800-bit fixed point (about 700 bits of the quotient are correct).
 * <p>
 * Provenance: this is the evaluator of the Fable review seat's diagnostic {@code ReviewFableTanTest} for the phase B2 reviews (2026-11-12), carried into the repository, reduced to
 * what the guard tests and the fold-error reproducer need (the reviewer verified its pi against the 20 words of {@code tan.c}'s 1/(2 pi) table and against {@code CrMath} on 91,412
 * inputs). It uses no table, constant or routine of the port. Runtime: about 1 ms per call, so tests that use it run on hundreds or thousands of inputs, not millions.
 */
final class CoreMathTanExactReference {

    private CoreMathTanExactReference() {}

    static final int B = 2400;                                   // fixed-point bits of pi and 1/(2 pi)
    static final int F = 800;                                    // fixed-point bits of the working values
    private static final BigInteger PI_B = machinPi(B);
    private static final BigInteger INV2PI_B = BigInteger.ONE.shiftLeft(2 * B).divide(PI_B.shiftLeft(1));
    private static final BigInteger PI_F = PI_B.shiftRight(B - F);
    static final BigInteger ONE_F = BigInteger.ONE.shiftLeft(F);
    private static final BigInteger MASK_F = ONE_F.subtract(BigInteger.ONE);

    /**
     * Word {@code i} of the binary expansion of 1/(2 pi) after the point, 64 bits per word ({@code i = 0} is 0x28be60db9391054a): the SELF-CHECK of this evaluator's pi against
     * the 20 words of {@code tan.c}'s table T (Fable L-4, re-check of 22a20ad5a; {@code CoreMathTanGuardTest#theExactReferenceUsesTheCsPiWords}).
     */
    static long inv2PiWord(int i) {
        return INV2PI_B.shiftRight(B - 64 * (i + 1)).longValue();
    }

    private static BigInteger atanInv(int n, int bits) {
        BigInteger nn = BigInteger.valueOf((long) n * n);
        BigInteger term = BigInteger.ONE.shiftLeft(bits).divide(BigInteger.valueOf(n));
        BigInteger sum = term;
        for (int k = 1; term.signum() != 0; k++) {
            term = term.divide(nn);
            BigInteger t = term.divide(BigInteger.valueOf(2L * k + 1));
            sum = (k & 1) == 1 ? sum.subtract(t) : sum.add(t);
        }
        return sum;
    }

    private static BigInteger machinPi(int bits) {
        int g = 64;
        BigInteger a = atanInv(5, bits + g).multiply(BigInteger.valueOf(16));
        BigInteger b = atanInv(239, bits + g).multiply(BigInteger.valueOf(4));
        return a.subtract(b).shiftRight(g);
    }

    /** frac(|x|/(2 pi)) as an F-bit fixed-point integer in [0, 2^F), from the exact value of the double. */
    private static BigInteger fracF(double ax) {
        long bits = Double.doubleToRawLongBits(ax);
        int e = (int) ((bits >>> 52) & 0x7ff);
        long m = (bits & 0xfffffffffffffL) | (e != 0 ? 1L << 52 : 0L);
        int s = (e != 0 ? e : 1) - 1075;
        BigInteger p = BigInteger.valueOf(m).multiply(INV2PI_B);
        int sh = s - B + F;
        BigInteger y = sh >= 0 ? p.shiftLeft(sh) : p.shiftRight(-sh);
        return y.and(MASK_F);
    }

    private static BigInteger[] sinCos(BigInteger a) {           // a in [0, pi/4], F-bit fixed point
        BigInteger a2 = a.multiply(a).shiftRight(F);
        BigInteger sin = a;
        BigInteger term = a;
        for (int k = 1; term.signum() != 0; k++) {
            term = term.multiply(a2).shiftRight(F).divide(BigInteger.valueOf((2L * k) * (2L * k + 1)));
            sin = (k & 1) == 1 ? sin.subtract(term) : sin.add(term);
        }
        BigInteger cos = ONE_F;
        term = ONE_F;
        for (int k = 1; term.signum() != 0; k++) {
            term = term.multiply(a2).shiftRight(F).divide(BigInteger.valueOf((2L * k - 1) * (2L * k)));
            cos = (k & 1) == 1 ? cos.subtract(term) : cos.add(term);
        }
        return new BigInteger[] {sin, cos};
    }

    /** The exact tangent of a double as a ratio {@code n/d} (about 700 bits), the sign, the folded argument R in turns (F-bit) and frac(|x|/(2 pi)) (F-bit). */
    static final class Tan {
        final BigInteger n;
        final BigInteger d;
        final boolean negative;
        final BigInteger rF;
        final BigInteger fracF;

        Tan(BigInteger n, BigInteger d, boolean negative, BigInteger rF, BigInteger fracF) {
            this.n = n;
            this.d = d;
            this.negative = negative;
            this.rF = rF;
            this.fracF = fracF;
        }

        /** The folded argument R in turns, as a double. */
        double rTurns() {
            return new BigDecimal(rF).divide(new BigDecimal(ONE_F), MathContext.DECIMAL64).doubleValue();
        }

        /** |tan(x)| to {@code digits} significant digits. */
        BigDecimal absTan(int digits) {
            return new BigDecimal(n).divide(new BigDecimal(d), new MathContext(digits));
        }
    }

    /** tan(x) for a finite {@code x} above the tiny threshold. */
    static Tan tan(double x) {
        BigInteger r = fracF(Math.abs(x));
        int q = r.shiftRight(F - 2).intValue();
        BigInteger rho = r.subtract(BigInteger.valueOf(q).shiftLeft(F - 2));
        boolean inv = rho.compareTo(ONE_F.shiftRight(3)) > 0;
        BigInteger a = inv ? ONE_F.shiftRight(2).subtract(rho) : rho;      // turns in [0, 1/8]
        BigInteger ang = a.multiply(PI_F).shiftRight(F - 1);              // 2 pi a
        BigInteger[] sc = sinCos(ang);
        BigInteger n = inv ? sc[1] : sc[0];
        BigInteger d = inv ? sc[0] : sc[1];
        boolean neg = x < 0;
        if ((q & 1) != 0) {
            BigInteger t = n;
            n = d;
            d = t;
            neg = !neg;
        }
        return new Tan(n, d, neg, a, r);
    }

    /** True if the exact frac(|x|/(2 pi)) is below 1/8 of a turn: no fold subtracts, the C's analysis applies. */
    static boolean isUnfolded(Tan t) {
        return t.fracF.compareTo(ONE_F.shiftRight(3)) < 0;
    }

    /** The correctly rounded double of {@code n/d} (round to nearest even), with the sign. */
    static double round(Tan t) {
        BigInteger n = t.n;
        BigInteger d = t.d;
        int e = 53 - (n.bitLength() - d.bitLength());
        BigInteger[] qr;
        BigInteger den;
        while (true) {
            BigInteger num = e >= 0 ? n.shiftLeft(e) : n;
            den = e >= 0 ? d : d.shiftLeft(-e);
            qr = num.divideAndRemainder(den);
            if (qr[0].bitLength() <= 53) {
                break;
            }
            e--;
        }
        long m = qr[0].longValueExact();
        int c = qr[1].shiftLeft(1).compareTo(den);
        if (c > 0 || (c == 0 && (m & 1L) != 0L)) {
            m++;
        }
        double v = Math.scalb((double) m, -e);
        return t.negative ? -v : v;
    }

    private static BigInteger mant(CoreMathDint a) {
        return CoreMathDintTest.u(a.hi).shiftLeft(64).or(CoreMathDintTest.u(a.lo));
    }

    /**
     * The error of a 128-bit result {@code u} (a positive dint: mantissa {@code hi:lo}, exponent {@code ex}) against the exact tangent, in ulps of the 128-bit mantissa
     * (units of 2^(ex-128)).
     */
    static double errorUlps(CoreMathDint u, Tan t) {
        int g = 64;
        int sh = (int) (128 - u.ex + g);
        BigInteger q = t.n.shiftLeft(sh).divide(t.d);                       // |tan| * 2^(128-ex+g)
        return mant(u).shiftLeft(g).subtract(q).abs().doubleValue() / 0x1p64;
    }

    /**
     * The distance, in ulps of the 128-bit mantissa of {@code u}, from the exact tangent to the nearest rounding MIDPOINT of two consecutive doubles (one double ulp is 2^75 such
     * units): the rounding of a 128-bit result that is within this distance of the exact value cannot be wrong.
     */
    static double distanceToMidpointUlps(CoreMathDint u, Tan t) {
        int g = 64;
        int sh = (int) (128 - u.ex + g);
        BigInteger q = t.n.shiftLeft(sh).divide(t.d);
        BigInteger unit = BigInteger.ONE.shiftLeft(75 + g);
        BigInteger rem = q.mod(unit);
        return rem.subtract(unit.shiftRight(1)).abs().doubleValue() / 0x1p64;
    }
}
