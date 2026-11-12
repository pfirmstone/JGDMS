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
 * WITHOUT WARRANTY OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.cel.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

/**
 * The 128-bit "double-integer" arithmetic of {@link CoreMathDint} (the {@code dint64_t} routines of {@code tan.c}) against exact
 * {@link BigInteger} models written from the C text, with controls. Runtime tier: FAST (default suite).
 *
 * <h2>SEALED PREDICTIONS (B2-D1..D8, phase B2; written before the first run of this class)</h2>
 * Population: random normalised dints (top bit of {@code hi} set, {@code lo} random, {@code ex} in [-200, 200], either sign) and the forced
 * families named below; seeds 20261301.. (in the code). Scoring: all four fields ({@code hi, lo, ex, sgn}) equal the model's, no tolerance.
 * <ol>
 *   <li><b>B2-D1 mul.</b> {@code CoreMathDint.mul} equals the BigInteger model of {@code mul_dint} (tan.c:262-292: the three-term high product,
 *       the one-bit normalisation, the exponent and the sign) on 300,000 pairs: 0 mismatches. <b>Control:</b> the model with the carry from the
 *       low-word sum into the high word dropped differs from production on EXACTLY the inputs where that carry is non-zero (the model counts them
 *       independently), and there are at least 100,000 of them (a carry occurs for about a half of random pairs). ELSE: a mismatch is a
 *       defect of {@code U128.mhUU} use or of the shift; fewer than 100,000 carries means the population does not exercise the carry.</li>
 *   <li><b>B2-D2 mul21.</b> The same for {@code mul_dint_21} (tan.c:296-318, {@code b.lo = 0}) on 300,000 pairs: 0 mismatches, and the model's
 *       carry control the same way; at least 50,000 carries.</li>
 *   <li><b>B2-D3 add.</b> {@code CoreMathDint.add} equals the BigInteger model of {@code add_dint} (tan.c:155-257, every branch: zero operand,
 *       equal magnitudes, same sign with and without carry out, different signs with cancellation of 0 to 127 leading bits, the Sterbenz case
 *       {@code k == 1}, {@code k >= 128}) on 600,000 pairs (random exponent gaps 0..140, forced near-equal magnitudes, forced {@code k = 1},
 *       forced equal magnitudes): 0 mismatches; and the result lies within 2 ulps of the exact sum (1 ulp for equal signs) and is EXACT when the signs
 *       differ and {@code k == 0}: 0 violations. Each branch is exercised (counted; every count at least 1,000, the equal-magnitude branches at
 *       least 100). The result may overlap an operand: {@code r = a} and {@code r = b} give the same fields as a separate result: 0 differences.
 *       <b>Control:</b> the model without the borrow of the low word in the subtraction differs from production on exactly the inputs where
 *       the borrow is 1.</li>
 *   <li><b>B2-D4 inv.</b> {@code CoreMathDint.inv} satisfies {@code |a*r - 1| < 2^-124.999} (tan.c:419-424, exact BigInteger check) for
 *       100,000 positive {@code a}: 0 violations; and {@code CoreMathDint.div} satisfies {@code |r*a/b - 1| < 2^-123.67} (tan.c:439) for 100,000
 *       pairs: 0 violations. <b>Control:</b> the Newton start alone (a table value without the three iterations and the 128-bit step) violates
 *       the bound for 100% of the inputs.</li>
 *   <li><b>B2-D5 fromD.</b> {@code fromD} of 200,000 non-zero doubles (a third subnormal) gives a dint whose exact value equals {@code |x|} and whose
 *       sign is that of {@code x}, normalised: 0 mismatches.</li>
 *   <li><b>B2-D6 toD in the normal range.</b> {@code toD} equals the exact round-to-nearest-even double of the 128-bit value (BigInteger model) for 300,000
 *       dints with {@code ex} in [-1021, 1024], including forced round-bit and sticky-bit patterns (round bit 0/1 x sticky 0/1 x last bit 0/1, and
 *       the maximum {@code hi, lo}): 0 mismatches; at least 10,000 forced half-way cases (round bit 1, nothing below).</li>
 *   <li><b>B2-D7 the dead branches.</b> {@code tan} never reaches {@code subnormalize}, the overflow and the underflow branches of {@code toD}
 *       (its results lie in [2^-27, 2^61]); by hand: a dint equal to the largest double is returned as it is, one at {@code ex = 1025} gives
 *       infinity, {@code ex = 1030} positive infinity even for a negative value (the C's overflow branch assigns a positive {@code r.f}: a property of
 *       unreachable C code, ported verbatim), {@code ex = -1074} with {@code hi = 2^63} (value 2^-1075) gives 0 (a tie rounds to even), and the dints
 *       2^-1022 and 2^-1074 give those doubles. All traced by hand through the C text before the run. ELSE: the C's behaviour, not a port defect, is
 *       documented in READY.</li>
 *   <li><b>B2-D8 normalize.</b> {@code CoreMathTan.normalize} shifts a non-zero value left until the top bit of {@code hi} is set and
 *       lowers {@code ex} by the shift, for 100,000 values incl. {@code hi = 0}: 0 mismatches against the BigInteger value.</li>
 * </ol>
 */
class CoreMathDintTest {

    private static final BigInteger M64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private static final BigInteger M128 = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);

    static BigInteger u(long v) {
        return new BigInteger(Long.toUnsignedString(v));
    }

    static BigInteger mant(CoreMathDint d) {
        return u(d.hi).shiftLeft(64).or(u(d.lo));
    }

    private static boolean same(CoreMathDint d, long hi, long lo, long ex, long sgn) {
        return d.hi == hi && d.lo == lo && d.ex == ex && d.sgn == sgn;
    }

    private static CoreMathDint random(SplittableRandom r, boolean lowZero) {
        CoreMathDint d = new CoreMathDint();
        d.hi = r.nextLong() | Long.MIN_VALUE;
        d.lo = lowZero ? 0L : r.nextLong();
        d.ex = r.nextInt(401) - 200;
        d.sgn = r.nextInt(2);
        return d;
    }

    private static long[] fields(CoreMathDint d) {
        return new long[] {d.hi, d.lo, d.ex, d.sgn};
    }

    private static CoreMathDint copyOf(CoreMathDint d) {
        return new CoreMathDint(d.hi, d.lo, d.ex, d.sgn);
    }

    /** The exact value as a numerator over 2^k with k = 128 - ex (BigInteger, sign included), so values of different exponents can be compared. */
    private static BigInteger scaled(CoreMathDint d, long baseEx) {
        BigInteger m = mant(d);
        BigInteger v = d.ex >= baseEx ? m.shiftLeft((int) (d.ex - baseEx)) : m.shiftRight((int) (baseEx - d.ex));
        return d.sgn != 0 ? v.negate() : v;
    }

    // ------------------------------------------------------------------------------------------
    // mul, mul21
    // ------------------------------------------------------------------------------------------

    /** Model of mul_dint / mul_dint_21: returns {hi, lo, ex, sgn} and, in carry[0], the carry of the low-word sum into the high word, in carry[1] the dropped-carry result's hi. */
    private static long[] modelMul(CoreMathDint a, CoreMathDint b, boolean dropCarry, int[] carryOut, boolean is21) {
        BigInteger ah = u(a.hi);
        BigInteger al = u(a.lo);
        BigInteger bh = u(b.hi);
        BigInteger bl = is21 ? BigInteger.ZERO : u(b.lo);
        BigInteger m1 = ah.multiply(bl);
        BigInteger m2 = al.multiply(bh);
        BigInteger p = ah.multiply(bh);
        BigInteger lowSum = p.and(M64).add(m1.shiftRight(64)).add(m2.shiftRight(64));
        int carry = lowSum.shiftRight(64).intValueExact();
        carryOut[0] = carry;
        BigInteger r = dropCarry ? p.shiftRight(64).shiftLeft(64).or(lowSum.and(M64)) : p.add(m1.shiftRight(64)).add(m2.shiftRight(64));
        r = r.and(M128);
        int ex = r.testBit(127) ? 1 : 0;
        r = r.shiftLeft(1 - ex).and(M128);
        long[] out = new long[4];
        out[0] = r.shiftRight(64).longValue();
        out[1] = r.longValue();
        out[2] = a.ex + b.ex + ex - 1;
        out[3] = a.sgn ^ b.sgn;
        return out;
    }

    private void mulFamily(boolean is21, long seed, int n, int minCarries, String what) {
        SplittableRandom r = new SplittableRandom(seed);
        int mismatches = 0;
        int carries = 0;
        int controlRejected = 0;
        int overlapDiffs = 0;
        int[] c = new int[1];
        for (int i = 0; i < n; i++) {
            CoreMathDint a = random(r, false);
            CoreMathDint b = random(r, is21);
            long[] want = modelMul(a, b, false, c, is21);
            if (c[0] != 0) {
                carries++;
            }
            long[] dropped = modelMul(a, b, true, new int[1], is21);
            CoreMathDint got = new CoreMathDint();
            if (is21) {
                CoreMathDint.mul21(got, a, b);
            } else {
                CoreMathDint.mul(got, a, b);
            }
            if (!same(got, want[0], want[1], want[2], want[3])) {
                mismatches++;
            }
            if (!same(got, dropped[0], dropped[1], dropped[2], dropped[3])) {
                controlRejected++;
            }
            // overlap: r = a, r = b
            CoreMathDint ra = copyOf(a);
            CoreMathDint rb = copyOf(b);
            if (is21) {
                CoreMathDint.mul21(ra, ra, b);
                CoreMathDint.mul21(rb, a, rb);
            } else {
                CoreMathDint.mul(ra, ra, b);
                CoreMathDint.mul(rb, a, rb);
            }
            if (!same(ra, want[0], want[1], want[2], want[3]) || !same(rb, want[0], want[1], want[2], want[3])) {
                overlapDiffs++;
            }
        }
        System.out.println(new StringBuilder(220).append(what).append(": ").append(mismatches).append(" mismatches of ").append(n).append(" pairs; overlap (r = a, r = b) differences ")
                .append(overlapDiffs).append("; model carries ").append(carries).append("; control (carry dropped) rejected by production comparison on ").append(controlRejected));
        assertEquals(0, mismatches);
        assertEquals(0, overlapDiffs);
        assertTrue(carries >= minCarries, "the population does not exercise the carry");
        assertEquals(carries, controlRejected, "the dropped-carry control must differ on exactly the inputs where the model has a carry");
    }

    @Test
    void mulEqualsTheBigIntegerModelAndTheCarryControlIsExact() {
        mulFamily(false, 20261301L, 300_000, 100_000, "B2-D1 mul_dint");
    }

    @Test
    void mul21EqualsTheBigIntegerModelAndTheCarryControlIsExact() {
        mulFamily(true, 20261302L, 300_000, 50_000, "B2-D2 mul_dint_21");
    }

    // ------------------------------------------------------------------------------------------
    // add
    // ------------------------------------------------------------------------------------------

    private static final class AddBranches {
        int same;
        int sameCarry;
        int diff;
        int diffCancel;
        int sterbenz;
        int kZero;
        int kBig;
        int equalAbs;
        int equalAbsCancel;
        int zeroOperand;
    }

    /** Model of add_dint; {@code noBorrow} drops the low-word borrow of the subtraction (control); {@code borrowOut[0]} is 1 if the C's A - B has that borrow. */
    private static long[] modelAdd(CoreMathDint a, CoreMathDint b, boolean noBorrow, int[] borrowOut, AddBranches br) {
        borrowOut[0] = 0;
        if (a.hi == 0L && a.lo == 0L) {
            if (br != null) {
                br.zeroOperand++;
            }
            return fields(b);
        }
        int c = a.ex != b.ex ? Long.signum(a.ex - b.ex) : mant(a).compareTo(mant(b));   // |a| against |b| (both normalised here)
        if (c == 0) {
            if (br != null) {
                br.equalAbs++;
            }
            if (a.sgn != b.sgn) {
                if (br != null) {
                    br.equalAbsCancel++;
                }
                return new long[] {0L, 0L, -1076L, 0L};
            }
            return new long[] {a.hi, a.lo, a.ex + 1, a.sgn};
        }
        if (c < 0) {
            CoreMathDint t = a;
            a = b;
            b = t;
        }
        BigInteger bigA = mant(a);
        BigInteger bigB = mant(b);
        long k = a.ex - b.ex;
        BigInteger bt = k == 0 ? bigB : (k < 128 ? bigB.shiftRight((int) k) : BigInteger.ZERO);
        if (br != null) {
            if (k == 0) {
                br.kZero++;
            }
            if (k >= 128) {
                br.kBig++;
            }
        }
        long rex = a.ex;
        BigInteger cc;
        if (a.sgn != b.sgn) {
            if (br != null) {
                br.diff++;
            }
            cc = bigA.subtract(bt);
            borrowOut[0] = (bigA.and(M64).compareTo(bt.and(M64)) < 0) ? 1 : 0;
            if (noBorrow) {
                BigInteger lo = bigA.and(M64).subtract(bt.and(M64)).and(M64);
                BigInteger hi = bigA.shiftRight(64).subtract(bt.shiftRight(64));
                cc = hi.shiftLeft(64).or(lo);
            }
            int z = 128 - cc.bitLength();
            int z2 = 0;
            if (z > 0) {
                if (br != null) {
                    br.diffCancel++;
                }
                if (k == 1) {
                    if (br != null) {
                        br.sterbenz++;
                    }
                    cc = bigA.shiftLeft(z).subtract(bigB.shiftLeft(z - 1)).and(M128);
                } else {
                    cc = cc.shiftLeft(z).and(M128);
                }
                rex -= z;
                z2 = 64 - cc.shiftRight(64).bitLength();
            }
            cc = cc.shiftLeft(z2).and(M128);
            rex -= z2;
        } else {
            if (br != null) {
                br.same++;
            }
            cc = bigA.add(bt);
            if (cc.bitLength() > 128) {
                if (br != null) {
                    br.sameCarry++;
                }
                cc = cc.shiftRight(1);
                rex++;
            }
        }
        return new long[] {cc.shiftRight(64).longValue(), cc.longValue(), rex, a.sgn};
    }

    private static CoreMathDint randomForAdd(SplittableRandom r, CoreMathDint a, int family) {
        CoreMathDint b = new CoreMathDint();
        b.sgn = r.nextInt(2);
        switch (family) {
            case 0:                                                   // independent, exponent gap 0..140 either way
                b.hi = r.nextLong() | Long.MIN_VALUE;
                b.lo = r.nextLong();
                b.ex = a.ex + r.nextInt(141) - (r.nextBoolean() ? 140 : 0);
                break;
            case 1:                                                   // near-equal magnitude, opposite sign: deep cancellation, k = 0
                b.hi = a.hi ^ (1L << r.nextInt(64)) >>> r.nextInt(64);
                b.hi |= Long.MIN_VALUE;
                b.lo = a.lo ^ (r.nextBoolean() ? r.nextLong() >>> (1 + r.nextInt(63)) : 0L);
                b.ex = a.ex;
                b.sgn = a.sgn ^ 1;
                break;
            case 2:                                                   // k = 1, opposite sign, A just above 2^127 and B just below 2^128: A - B/2 cancels deeply (Sterbenz)
                if (a.hi != 0L) {
                    a.hi = Long.MIN_VALUE | (r.nextLong() >>> (1 + r.nextInt(63)));
                }
                b.hi = -1L ^ (r.nextLong() >>> (1 + r.nextInt(63)));
                b.lo = r.nextLong();
                b.ex = a.ex - 1;
                b.sgn = a.sgn ^ 1;
                break;
            case 3:                                                   // equal magnitude
                b.hi = a.hi;
                b.lo = a.lo;
                b.ex = a.ex;
                break;
            default:                                                  // gap 1..3 with opposite sign
                b.hi = r.nextLong() | Long.MIN_VALUE;
                b.lo = r.nextLong();
                b.ex = a.ex - 1 - r.nextInt(3);
                b.sgn = a.sgn ^ 1;
                break;
        }
        return b;
    }

    @Test
    void addEqualsTheBigIntegerModelOnEveryBranchWithinTheErrorBoundAndTheControlIsExact() {
        SplittableRandom r = new SplittableRandom(20261303L);
        AddBranches br = new AddBranches();
        int n = 600_000;
        int mismatches = 0;
        int overlapDiffs = 0;
        int boundViolations = 0;
        int sterbenzNotExact = 0;
        int borrows = 0;
        int controlRejected = 0;
        int controlVisible = 0;
        int[] bo = new int[1];
        for (int i = 0; i < n; i++) {
            CoreMathDint a = random(r, false);
            if (i % 5000 == 4999) {
                a.hi = 0L;                                            // a zero operand now and then
                a.lo = 0L;
                a.ex = -1076L;
            }
            CoreMathDint b = randomForAdd(r, a, i % 5);
            long[] want = modelAdd(a, b, false, bo, br);
            if (bo[0] != 0) {
                borrows++;
            }
            long[] dropped = modelAdd(a, b, true, new int[1], null);
            CoreMathDint got = new CoreMathDint();
            CoreMathDint.add(got, a, b);
            if (!same(got, want[0], want[1], want[2], want[3])) {
                mismatches++;
            }
            if (!same(got, dropped[0], dropped[1], dropped[2], dropped[3])) {
                controlRejected++;
            }
            if (!java.util.Arrays.equals(want, dropped)) {
                controlVisible++;                                     // the borrow matters to the model's own result (not when the Sterbenz branch recomputes the difference exactly)
            }
            CoreMathDint ra = copyOf(a);
            CoreMathDint rb = copyOf(b);
            CoreMathDint.add(ra, ra, b);
            CoreMathDint.add(rb, a, rb);
            if (!same(ra, want[0], want[1], want[2], want[3]) || !same(rb, want[0], want[1], want[2], want[3])) {
                overlapDiffs++;
            }
            // error bound: |r - (a + b)| <= 2 ulp(r) (1 ulp for equal signs); exact when the signs differ and the exponents are equal
            if (!(a.hi == 0L && a.lo == 0L) && !(got.hi == 0L && got.lo == 0L)) {
                long base = Math.min(Math.min(a.ex, b.ex), got.ex) - 128;
                BigInteger exact = scaled128(a, base).add(scaled128(b, base));
                BigInteger res = scaled128(got, base);
                BigInteger err = exact.subtract(res).abs();
                BigInteger ulp = BigInteger.ONE.shiftLeft((int) (got.ex - 128 - base));
                int limit = a.sgn == b.sgn ? 1 : 2;
                if (err.compareTo(ulp.multiply(BigInteger.valueOf(limit))) > 0) {
                    boundViolations++;
                }
                if (a.sgn != b.sgn && a.ex == b.ex && err.signum() != 0) {
                    sterbenzNotExact++;
                }
            }
        }
        System.out.println(new StringBuilder(400).append("B2-D3 add_dint: ").append(mismatches).append(" mismatches of ").append(n).append(" pairs; overlap differences ").append(overlapDiffs)
                .append("; error-bound violations ").append(boundViolations).append("; same-sign k=0 diff-sign inexact ").append(sterbenzNotExact).append("; branches: same ").append(br.same).append(" (carry out ").append(br.sameCarry)
                .append("), different signs ").append(br.diff).append(" (cancelling ").append(br.diffCancel).append(", Sterbenz k=1 ").append(br.sterbenz).append("), k=0 ").append(br.kZero).append(", k>=128 ")
                .append(br.kBig).append(", equal magnitudes ").append(br.equalAbs).append(" (cancelling ").append(br.equalAbsCancel).append("), zero operand ").append(br.zeroOperand)
                .append("; model borrows ").append(borrows).append(", dropping the borrow changes the model's own result on ").append(controlVisible).append(", control (borrow dropped) rejected on ").append(controlRejected));
        assertEquals(0, mismatches);
        assertEquals(0, overlapDiffs);
        assertEquals(0, boundViolations);
        assertEquals(0, sterbenzNotExact);
        assertTrue(br.sameCarry >= 1000 && br.diffCancel >= 1000 && br.sterbenz >= 1000 && br.kZero >= 1000 && br.kBig >= 1000, "a branch is not exercised");
        assertTrue(br.equalAbs >= 100 && br.equalAbsCancel >= 50 && br.zeroOperand >= 100, "an edge branch is not exercised");
        // found at the first run: the identity borrows == rejected is false by construction (the Sterbenz branch k == 1 recomputes the difference from the untruncated operands,
        // so the borrow of the first difference does not reach its result); the exact statement is rejected == (inputs where dropping changes the model's own result)
        assertEquals(controlVisible, controlRejected, "the dropped-borrow control must differ on exactly the inputs where dropping the borrow changes the model's result");
        assertTrue(controlVisible >= 1000, "the population does not exercise the borrow");
    }

    private static BigInteger scaled128(CoreMathDint d, long base) {
        BigInteger m = mant(d);
        BigInteger v = m.shiftLeft((int) (d.ex - 128 - base));
        return d.sgn != 0 ? v.negate() : v;
    }

    // ------------------------------------------------------------------------------------------
    // inv, div
    // ------------------------------------------------------------------------------------------

    /** |X * 2^s - 2^e| as a multiple of 2^e: true if below 2^-bits (bits as a rational 2^-(num/1000)). */
    private static boolean relativeErrorBelow(BigInteger product, long productExp, long thousandthsOfBits) {
        // value = product * 2^productExp; compare |value - 1| < 2^(-thousandths/1000)
        BigInteger one = BigInteger.ONE.shiftLeft((int) -productExp);
        BigInteger diff = product.subtract(one).abs();
        // diff / one < 2^(-t/1000)  <=>  diff * 2^(t/1000) < one: use a rational bound with 2^(1/1000) ~ 1.000693387 (rounded down: stricter)
        long whole = thousandthsOfBits / 1000;
        long frac = thousandthsOfBits % 1000;
        BigInteger lhs = diff.shiftLeft((int) whole).multiply(BigInteger.valueOf(1_000_000_000L));
        double f = Math.pow(2.0, frac / 1000.0);
        BigInteger factor = BigInteger.valueOf((long) Math.floor(f * 1_000_000_000L));
        return lhs.compareTo(one.multiply(factor)) < 0 || diff.signum() == 0;
    }

    @Test
    void invAndDivMeetTheErrorBoundsOfTheCText() {
        SplittableRandom r = new SplittableRandom(20261304L);
        int n = 100_000;
        int invViolations = 0;
        int divViolations = 0;
        int controlViolations = 0;
        for (int i = 0; i < n; i++) {
            CoreMathDint a = random(r, i % 4 == 0);
            a.sgn = 0L;
            CoreMathDint inv = new CoreMathDint();
            CoreMathDint.inv(inv, a);
            // a * inv: value = A*R * 2^(ea + er - 256)
            BigInteger prod = mant(a).multiply(mant(inv));
            long exp = a.ex + inv.ex - 256;
            if (!relativeErrorBelow(prod, exp, 124_999L)) {
                invViolations++;
            }
            // control: the table start alone (a 64-bit inverse good to 8 bits) violates the bound
            long t = CoreMathTanTables.tinv((int) ((a.hi >>> 55) & 0xffL));
            if (!relativeErrorBelow(u(a.hi).multiply(u(t)), -127, 124_999L)) {   // h * t / 2^127 ~ 1 to about 8 bits
                controlViolations++;
            }
            CoreMathDint b = random(r, false);
            CoreMathDint q = new CoreMathDint();
            CoreMathDint.div(q, b, a);
            // q * a / b = 1: (Q * A) / B * 2^(eq + ea - eb - 128)
            BigInteger num = mant(q).multiply(mant(a));
            BigInteger den = mant(b);
            // |num * 2^(eq+ea-eb-128) / den - 1| < 2^-123.67: scale num to compare with den exactly
            long sh = q.ex + a.ex - b.ex - 128;                       // q*a/b = Q*A*2^sh / B
            BigInteger lhsNum = sh >= 0 ? num.shiftLeft((int) sh) : num;
            BigInteger lhsDen = sh >= 0 ? den : den.shiftLeft((int) -sh);
            BigInteger diff = lhsNum.subtract(lhsDen).abs();
            // diff / lhsDen < 2^-123.67 (2^-123.67 > 2^-124 * 1.2559; use 2^-124 * 1.2559 = 2^-123.67 with rounding down)
            if (diff.shiftLeft(124).multiply(BigInteger.valueOf(1_000_000)).compareTo(lhsDen.multiply(BigInteger.valueOf(1_255_000))) >= 0) {
                divViolations++;
            }
        }
        System.out.println(new StringBuilder(220).append("B2-D4 inv_dint relative error >= 2^-124.999 on ").append(invViolations).append(" of ").append(n).append("; div_dint relative error >= 2^-123.67 on ")
                .append(divViolations).append(" of ").append(n).append("; control (Newton start alone) violates the inv bound on ").append(controlViolations).append(" of ").append(n));
        assertEquals(0, invViolations);
        assertEquals(0, divViolations);
        assertEquals(n, controlViolations);
    }

    // ------------------------------------------------------------------------------------------
    // fromD, toD, normalize
    // ------------------------------------------------------------------------------------------

    @Test
    void fromDGivesTheExactValueNormalisedWithTheSign() {
        SplittableRandom r = new SplittableRandom(20261305L);
        int n = 200_000;
        int mismatches = 0;
        int subnormal = 0;
        for (int i = 0; i < n; i++) {
            long bits;
            if (i % 3 == 0) {
                bits = r.nextLong() & 0x000fffffffffffffL;            // subnormal
                if (bits == 0L) {
                    bits = 1L;
                }
                subnormal++;
            } else {
                bits = r.nextLong() & 0x7fffffffffffffffL;
                if ((bits >>> 52) == 0x7ffL || bits == 0L) {
                    bits = 0x3ff0000000000001L;
                }
            }
            double x = Double.longBitsToDouble(bits);
            if (r.nextBoolean()) {
                x = -x;
            }
            CoreMathDint d = new CoreMathDint();
            CoreMathDint.fromD(d, x);
            // the exact value |x| = significand * 2^(e - 1074 ...)
            long raw = Double.doubleToRawLongBits(Math.abs(x));
            long eField = raw >>> 52;
            long frac = raw & 0x000fffffffffffffL;
            BigInteger sig = BigInteger.valueOf(eField == 0 ? frac : (frac | (1L << 52)));
            long unit = (eField == 0 ? 1 : eField) - 1075;             // |x| = sig * 2^unit
            // d = mant * 2^(ex - 128): equal iff sig * 2^(unit) == mant * 2^(ex-128), with mant = hi*2^64 (lo = 0)
            BigInteger left = sig.shiftLeft(128);
            BigInteger right = mant(d).shiftLeft((int) (d.ex - unit));
            boolean ok = d.hi < 0 && d.lo == 0L && d.sgn == (x < 0 ? 1L : 0L) && d.ex - unit >= -1000 && left.equals(right);
            if (!ok) {
                mismatches++;
            }
        }
        System.out.println(new StringBuilder(120).append("B2-D5 dint_fromd: ").append(mismatches).append(" mismatches of ").append(n).append(" doubles (").append(subnormal).append(" subnormal)"));
        assertEquals(0, mismatches);
    }

    private static double modelToD(long hi, long lo, long ex, long sgn) {
        BigInteger m = u(hi).shiftLeft(64).or(u(lo));
        BigInteger q = m.shiftRight(75);
        BigInteger rem = m.and(BigInteger.ONE.shiftLeft(75).subtract(BigInteger.ONE));
        BigInteger half = BigInteger.ONE.shiftLeft(74);
        int c = rem.compareTo(half);
        if (c > 0 || (c == 0 && q.testBit(0))) {
            q = q.add(BigInteger.ONE);
        }
        double v = Math.scalb((double) q.longValueExact(), (int) (ex - 53));
        return sgn != 0 ? -v : v;
    }

    @Test
    void toDEqualsTheExactRoundToNearestEvenInTheNormalRange() {
        SplittableRandom r = new SplittableRandom(20261306L);
        int n = 300_000;
        int mismatches = 0;
        int halfway = 0;
        for (int i = 0; i < n; i++) {
            long hi = r.nextLong() | Long.MIN_VALUE;
            long lo = r.nextLong();
            long ex = -1021 + r.nextInt(1024 + 1021 + 1);
            long sgn = r.nextInt(2);
            switch (i % 8) {
                case 0:
                case 1:                                               // forced round bit and sticky pattern: hi bit 10 = round bit, hi bits 9..0 and lo = sticky
                    hi = (hi & ~0x7ffL) | ((long) r.nextInt(2) << 10) | (r.nextBoolean() ? (r.nextLong() & 0x3ffL) : 0L);
                    lo = r.nextBoolean() ? 0L : lo;
                    break;
                case 2:                                               // half-way: round bit 1, nothing below, last bit random
                    hi = (hi & ~0x7ffL) | (1L << 10);
                    lo = 0L;
                    halfway++;
                    break;
                case 3:
                    hi = -1L;
                    lo = -1L;
                    break;
                case 4:                                               // just below half-way (round bit 0, all sticky)
                    hi = (hi & ~0x7ffL) | 0x3ffL;
                    lo = -1L;
                    break;
                default:
                    break;
            }
            CoreMathDint d = new CoreMathDint(hi, lo, ex, sgn);
            double want = modelToD(hi, lo, ex, sgn);
            double got = CoreMathDint.toD(d);
            if (Double.doubleToRawLongBits(got) != Double.doubleToRawLongBits(want)) {
                mismatches++;
            }
        }
        System.out.println(new StringBuilder(120).append("B2-D6 dint_tod normal range: ").append(mismatches).append(" mismatches of ").append(n).append(" dints; forced half-way cases ").append(halfway));
        assertEquals(0, mismatches);
        assertTrue(halfway >= 10_000);
    }

    @Test
    void theBranchesTanNeverReachesAreDrivenByHand() {
        // the largest double: hi = 0xfffffffffffff800 (53 ones), ex = 1024
        assertEquals(Double.MAX_VALUE, CoreMathDint.toD(new CoreMathDint(0xfffffffffffff800L, 0L, 1024L, 0L)));
        assertEquals(Double.POSITIVE_INFINITY, CoreMathDint.toD(new CoreMathDint(Long.MIN_VALUE, 0L, 1025L, 0L)));
        // a property of the C's unreachable code, ported verbatim: the overflow branch for ex > 1025 assigns a positive MAX_VALUE to r.f, so the sign is lost
        assertEquals(Double.POSITIVE_INFINITY, CoreMathDint.toD(new CoreMathDint(Long.MIN_VALUE, 0L, 1030L, 1L)));
        assertEquals(0.0, CoreMathDint.toD(new CoreMathDint(Long.MIN_VALUE, 0L, -1074L, 0L)));
        assertEquals(0x1p-1022, CoreMathDint.toD(new CoreMathDint(Long.MIN_VALUE, 0L, -1021L, 0L)));
        // the exactly representable subnormal 2^-1074 (hi = 2^63 at ex = -1073)
        assertEquals(Double.MIN_VALUE, CoreMathDint.toD(new CoreMathDint(Long.MIN_VALUE, 0L, -1073L, 0L)));
    }

    @Test
    void normalizeShiftsLeftUntilTheTopBitIsSet() {
        SplittableRandom r = new SplittableRandom(20261307L);
        int n = 100_000;
        int mismatches = 0;
        for (int i = 0; i < n; i++) {
            long hi = r.nextLong() >>> r.nextInt(64);
            long lo = r.nextLong() >>> (i % 7 == 0 ? 0 : r.nextInt(64));
            if (hi == 0L && lo == 0L) {
                lo = 1L;
            }
            if (i % 11 == 0) {
                hi = 0L;
            }
            if (hi == 0L && lo == 0L) {
                lo = 1L;                                              // (found at the first run: zero has nothing to normalise; the population produced it by the hi = 0 forcing)
            }
            long ex = r.nextInt(300) - 150;
            CoreMathDint d = new CoreMathDint(hi, lo, ex, 0L);
            BigInteger m = u(hi).shiftLeft(64).or(u(lo));
            int z = 128 - m.bitLength();
            CoreMathTan.normalize(d);
            BigInteger want = m.shiftLeft(z).and(M128);
            if (d.hi != want.shiftRight(64).longValue() || d.lo != want.longValue() || d.ex != ex - z) {
                mismatches++;
            }
        }
        System.out.println(new StringBuilder(80).append("B2-D8 normalize: ").append(mismatches).append(" mismatches of ").append(n));
        assertEquals(0, mismatches);
    }
}
