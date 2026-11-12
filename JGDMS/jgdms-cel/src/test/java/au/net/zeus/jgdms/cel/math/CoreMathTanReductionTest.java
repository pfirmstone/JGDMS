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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

/**
 * The argument reductions of {@link CoreMathTan} ({@code reduce} of the accurate path, {@code reduce_fast} and {@code set_dd} of the fast path, whose
 * inline 128-bit and 192-bit carries are the only wide-integer arithmetic of {@code tan.c} outside {@link CoreMathDint}) against exact
 * {@link BigInteger} models written from the C text, with dropped-carry controls, and against an independent value of 1/(2 pi)
 * ({@link CoreMathTanTestSupport#halfPi()}, Machin's formula) for the error bounds the C claims. Runtime tier: FAST (default suite).
 *
 * <h2>SEALED PREDICTIONS (B2-R1..R7, phase B2; written before the first run of this class)</h2>
 * Scoring: every output word equals the model's, no tolerance; an error bound is violated if the exact difference exceeds the C's claim.
 * <ol>
 *   <li><b>B2-R1 reduce, model.</b> {@code CoreMathTan.reduce} (tan.c:1543-1666) equals the BigInteger model for every exponent {@code ex} from -30 to 1024
 *       with 40 random mantissas each (and the extreme mantissas 2^63 and all ones): 0 mismatches over the population (about 44,000 inputs). The model's
 *       word chain with no carry dropped equals the exact product of {@code hi} and the table words for every input: 0 differences. <b>Controls:</b> the
 *       model with the carry into {@code c[2]} (the first of three) dropped differs from production on EXACTLY the inputs where dropping it changes the
 *       model's own output, and there are at least 1,000 such inputs; the same for the carry into {@code c[3]} (at least 1,000).
 *       ELSE: a mismatch is a transcription defect of the carry chain; fewer than 1,000 means the population does not exercise the control.</li>
 *   <li><b>B2-R2 reduce, error bound.</b> For about 450 inputs (every 7th exponent from -30 to 1024, three random mantissas each) {@code |Xout - (Xin/(2 pi) mod 1)| < 2^-126.67 * Xout} (the
 *       C's claim, tan.c:1540), checked exactly with 450-digit arithmetic: 0 violations; and {@code Xout} is normalised (top bit of {@code hi} set): 0 violations.</li>
 *   <li><b>B2-R3 set_dd.</b> {@code CoreMathTan.setDd} equals the exact model (h = the top 53 bits of the 128-bit fraction, l = the top 53 significant bits of the
 *       64 bits that follow, both truncated towards zero) for 300,000 random pairs {@code (c1, c0)} with a random number of leading zero bits (0 to 63) in
 *       {@code c1}, 50,000 of them with {@code c1 = 0} (and {@code c0} not 1), and the pair (0, 0): 0 mismatches.</li>
 *   <li><b>B2-R4 reduce_fast, model.</b> For every binary exponent field from 1025 to 2046 and 30 random mantissas each (both branches of tan.c:1787-1842: e &lt;= 1074 and
 *       e &gt; 1074), {@code CoreMathTan.reduceFast} equals the model (the word chain, the shift, the truncation of {@code set_dd}, {@code i = floor(h*2^11)} and the exact
 *       {@code h - i/2^11}): 0 mismatches of about 30,000. <b>Controls:</b> dropping the carry into {@code c[1]} and dropping the carry into {@code c[2]} each
 *       differ from production on exactly the inputs where the model's own output changes, and there are at least 1,000 of each.</li>
 *   <li><b>B2-R5 reduce_fast, error bound and ranges.</b> For 20,000 inputs over the whole range {@code 0x1.7137449123ef6p-26 < x < 2^1024} (both branches of the sign
 *       test {@code x <= 0x1.921fb54442d17p+2}) {@code |i/2^11 + h + l - frac(x/(2 pi))| < 2^-104.815} (tan.c:1743), {@code 0 <= h < 2^-11}, {@code |l| < 2^-52.36},
 *       {@code 0 <= i < 2048}: 0 violations.</li>
 *   <li><b>B2-R6 reduce2.</b> {@code reduce2} splits {@code X = i/2^11 + Xout} exactly with {@code 0 <= Xout < 2^-11} and normalised output, for 100,000 random {@code X} in
 *       [2^-30, 1): 0 mismatches against the BigInteger value.</li>
 *   <li><b>B2-R7 the worst case.</b> The C's comment (tan.c:1656) says the worst case for {@code 2^25 <= x < 2^1024} is {@code X.ex = -61} at {@code |x| = 0x1.6ac5b262ca1ffp+851}:
 *       {@code reduce} of that value gives {@code ex = -61} and no other tested input gives a smaller {@code ex}.</li>
 * </ol>
 */
class CoreMathTanReductionTest {

    private static final BigInteger M64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private static final BigInteger M128 = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);
    private static final MathContext MC = new MathContext(480);

    private static BigInteger u(long v) {
        return CoreMathDintTest.u(v);
    }

    private static BigInteger tw(int j) {
        return u(CoreMathTanTables.t(j));
    }

    /** Dropped-carry flags of the word chain: which of the carries into c[1], c[2], c[3], c[4] (0..3) is dropped, or -1 for none. */
    private static BigInteger chain5(long xh, int i, int dropped) {
        BigInteger x = u(xh);
        BigInteger u3 = x.multiply(tw(i + 3));
        BigInteger c0 = u3.and(M64);
        BigInteger c1 = u3.shiftRight(64);
        BigInteger u2 = x.multiply(tw(i + 2));
        BigInteger lo = u2.and(M64);
        c1 = c1.add(lo).and(M64);
        boolean carryA = c1.compareTo(lo) < 0;
        BigInteger c2 = u2.shiftRight(64).add(dropped == 1 || !carryA ? BigInteger.ZERO : BigInteger.ONE);
        BigInteger u1 = x.multiply(tw(i + 1));
        lo = u1.and(M64);
        c2 = c2.add(lo).and(M64);
        boolean carryB = c2.compareTo(lo) < 0;
        BigInteger c3 = u1.shiftRight(64).add(dropped == 2 || !carryB ? BigInteger.ZERO : BigInteger.ONE);
        BigInteger u0 = x.multiply(tw(i));
        lo = u0.and(M64);
        c3 = c3.add(lo).and(M64);
        boolean carryC = c3.compareTo(lo) < 0;
        BigInteger c4 = u0.shiftRight(64).add(dropped == 3 || !carryC ? BigInteger.ZERO : BigInteger.ONE);
        return c4.shiftLeft(256).or(c3.shiftLeft(192)).or(c2.shiftLeft(128)).or(c1.shiftLeft(64)).or(c0);
    }

    /** Model of reduce for ex >= 2; returns {hi, lo, ex}. {@code dropped} as in {@link #chain5}. */
    private static long[] modelReduce(CoreMathDint x, int dropped) {
        long e = x.ex;
        BigInteger hi;
        BigInteger lo;
        BigInteger tiny;
        if (e <= 1) {
            BigInteger p = u(x.hi).multiply(tw(0).shiftLeft(64).or(tw(1)));
            tiny = p.and(M64);
            BigInteger xl = p.shiftRight(64).and(M128);
            hi = xl.shiftRight(64);
            lo = xl.and(M64);
            return finish(hi, lo, tiny, e);
        }
        int i = (e < 127) ? 0 : (int) ((e - 127 + 64 - 1) / 64);
        BigInteger p = chain5(x.hi, i, dropped);
        int f = (int) (e - 64L * i);
        if (f <= 64) {
            hi = p.shiftRight(256 - f).and(M64);
            lo = p.shiftRight(192 - f).and(M64);
            tiny = p.shiftRight(128 - f).and(M64);
        } else {
            int g = f - 64;
            BigInteger extra = u(x.hi).multiply(tw(i + 4)).shiftRight(64);
            BigInteger p2 = p.add(extra);
            hi = p2.shiftRight(192 - g).and(M64);
            lo = p2.shiftRight(128 - g).and(M64);
            tiny = p2.shiftRight(64 - g).and(M64);
        }
        return finish(hi, lo, tiny, 0L);
    }

    private static long[] finish(BigInteger hi, BigInteger lo, BigInteger tiny, long ex) {
        BigInteger m = hi.shiftLeft(64).or(lo);
        long exOut = ex;
        if (m.signum() != 0) {
            int cnt = 128 - m.bitLength();
            assertTrue(cnt < 64, "the model's normalisation shift is 64 or more");
            m = m.shiftLeft(cnt).and(M128);
            exOut = ex - cnt;
            if (cnt != 0) {
                m = m.or(tiny.shiftRight(64 - cnt));
            }
        }
        return new long[] {m.shiftRight(64).longValue(), m.and(M64).longValue(), exOut};
    }

    private static boolean sameReduce(CoreMathDint d, long[] want) {
        return d.hi == want[0] && d.lo == want[1] && d.ex == want[2];
    }

    @Test
    void reduceEqualsTheBigIntegerModelAndTheCarryControlsAreExact() {
        SplittableRandom r = new SplittableRandom(20261310L);
        int n = 0;
        int mismatches = 0;
        int chainDiffs = 0;
        int[] visible = new int[4];
        int[] rejected = new int[4];
        int[] carriesSeen = new int[4];
        for (int e = -30; e <= 1024; e++) {
            for (int s = 0; s < 42; s++) {
                long hi = s == 0 ? Long.MIN_VALUE : s == 1 ? -1L : (r.nextLong() | Long.MIN_VALUE);
                CoreMathDint x = new CoreMathDint(hi, 0L, e, 0L);
                n++;
                long[] want = modelReduce(x, -1);
                CoreMathDint got = new CoreMathDint(x.hi, x.lo, x.ex, x.sgn);
                CoreMathTan.reduce(got);
                if (!sameReduce(got, want)) {
                    mismatches++;
                }
                if (e >= 2) {
                    int i = (e < 127) ? 0 : (int) ((e - 127 + 64 - 1) / 64);
                    BigInteger w = tw(i).shiftLeft(192).or(tw(i + 1).shiftLeft(128)).or(tw(i + 2).shiftLeft(64)).or(tw(i + 3));
                    if (!chain5(hi, i, -1).equals(u(hi).multiply(w))) {
                        chainDiffs++;
                    }
                    for (int d = 1; d <= 3; d++) {
                        long[] dropped = modelReduce(x, d);
                        boolean changes = !java.util.Arrays.equals(dropped, want);
                        boolean differs = !sameReduce(got, dropped);
                        if (changes) {
                            visible[d]++;
                        }
                        if (differs) {
                            rejected[d]++;
                        }
                        if (!chain5(hi, i, d).equals(chain5(hi, i, -1))) {
                            carriesSeen[d]++;
                        }
                    }
                }
            }
        }
        System.out.println(new StringBuilder(300).append("B2-R1 reduce: ").append(mismatches).append(" mismatches of ").append(n).append(" inputs; word chain without a dropped carry differs from the exact product on ")
                .append(chainDiffs).append("; carries into c[2], c[3], c[4] present on ").append(carriesSeen[1]).append(", ").append(carriesSeen[2]).append(", ").append(carriesSeen[3])
                .append("; dropping each changes the model's output on ").append(visible[1]).append(", ").append(visible[2]).append(", ").append(visible[3])
                .append(" and production differs from the dropped model on ").append(rejected[1]).append(", ").append(rejected[2]).append(", ").append(rejected[3]));
        assertEquals(0, mismatches);
        assertEquals(0, chainDiffs);
        assertEquals(visible[1], rejected[1]);
        assertEquals(visible[2], rejected[2]);
        assertTrue(visible[1] >= 1000 && visible[2] >= 1000, "the population does not exercise the carry controls");
    }

    @Test
    void reduceMeetsTheErrorBoundOfTheCText() {
        SplittableRandom r = new SplittableRandom(20261311L);
        BigDecimal twoPiInv = BigDecimal.ONE.divide(CoreMathTanTestSupport.halfPi().multiply(BigDecimal.valueOf(4), MC), MC);
        BigDecimal bound = new BigDecimal("1.2570").divide(new BigDecimal(BigInteger.ONE.shiftLeft(127)), MC);       // 2^-126.67 < 1.2570 * 2^-127 (stricter)
        // Found at the first run (DEVIATION from the sealed B2-R2 text): for ex <= 1 the C's own analysis (tan.c:1570-1574) takes the approximation error of the multiplier,
        // |T0/2^64 + T1/2^128 - 1/(2 pi)| < 2^-130.22, as if it were RELATIVE; it is absolute, so relative to 1/(2 pi) = 2^-2.65 it is 2^-127.57. The provable bound for that
        // branch is (1 + 2^-127.57)(1 + 2^-127) - 1 < 1.675 * 2^-127 = 2^-126.26; the sealed claim 2^-126.67 (tan.c:1540) holds for ex >= 2 only.
        BigDecimal boundSmall = new BigDecimal("1.6760").divide(new BigDecimal(BigInteger.ONE.shiftLeft(127)), MC);
        int violationsLarge = 0;
        int violationsSmallOwn = 0;
        int exceedCommentSmall = 0;
        int notNormalised = 0;
        int n = 0;
        int nSmall = 0;
        long minEx = 0;
        double worstSmall = Double.NEGATIVE_INFINITY;
        for (int e = -30; e <= 1024; e += (e <= 1 ? 1 : 7)) {
            for (int s = 0; s < 3; s++) {
                long hi = r.nextLong() | Long.MIN_VALUE;
                CoreMathDint x = new CoreMathDint(hi, 0L, e, 0L);
                BigDecimal xv = new BigDecimal(u(hi)).multiply(pow2(e - 64), MC);
                BigDecimal y = xv.multiply(twoPiInv, MC);
                BigDecimal frac = y.subtract(new BigDecimal(y.toBigInteger()));
                CoreMathTan.reduce(x);
                n++;
                if (x.hi >= 0) {
                    notNormalised++;
                }
                BigDecimal out = new BigDecimal(CoreMathDintTest.mant(x)).multiply(pow2(x.ex - 128), MC);
                BigDecimal err = frac.subtract(out).abs();
                if (e <= 1) {
                    nSmall++;
                    worstSmall = Math.max(worstSmall, Math.log(err.divide(out, MC).doubleValue()) / Math.log(2));
                    if (err.compareTo(out.multiply(bound, MC)) >= 0) {
                        exceedCommentSmall++;
                    }
                    if (err.compareTo(out.multiply(boundSmall, MC)) >= 0) {
                        violationsSmallOwn++;
                    }
                } else if (err.compareTo(out.multiply(bound, MC)) >= 0) {
                    violationsLarge++;
                }
                if (e >= 26) {
                    minEx = Math.min(minEx, x.ex);
                }
            }
        }
        System.out.println(new StringBuilder(260).append("B2-R2 reduce: for ex >= 2 the error bound 2^-126.67 is violated on ").append(violationsLarge).append(" of ").append(n - nSmall).append(" inputs; for ex <= 1 (")
                .append(nSmall).append(" inputs) the C's stated bound 2^-126.67 is exceeded on ").append(exceedCommentSmall).append(", the provable bound 2^-126.26 on ").append(violationsSmallOwn).append(", worst observed 2^")
                .append(worstSmall).append("; not normalised ").append(notNormalised).append("; smallest output exponent for x >= 2^25: ").append(minEx));
        assertEquals(0, violationsLarge);
        assertEquals(0, violationsSmallOwn);
        assertEquals(0, notNormalised);
        assertTrue(minEx >= -61, "the C claims ex >= -61 for 2^25 <= x");
    }

    @Test
    void reduceOfTheWorstCaseHasExponentMinus61() {
        double x = 0x1.6ac5b262ca1ffp+851;
        CoreMathDint d = new CoreMathDint();
        CoreMathDint.fromD(d, x);
        CoreMathTan.reduce(d);
        System.out.println(new StringBuilder(80).append("B2-R7 reduce(0x1.6ac5b262ca1ffp+851): ex = ").append(d.ex));
        assertEquals(-61L, d.ex);
    }

    private static BigDecimal pow2(long k) {
        if (k >= 0) {
            return new BigDecimal(BigInteger.ONE.shiftLeft((int) k));
        }
        return new BigDecimal(BigInteger.valueOf(5).pow((int) -k)).movePointLeft((int) -k);
    }

    // ------------------------------------------------------------------------------------------
    // set_dd
    // ------------------------------------------------------------------------------------------

    /** Exact model of set_dd: {h, l} for the fraction Y/2^128, Y = c1*2^64 + c0. */
    private static double[] modelSetDd(long c1, long c0) {
        BigInteger y = u(c1).shiftLeft(64).or(u(c0));
        if (y.signum() == 0) {
            return new double[] {0.0, 0.0};
        }
        int n = y.bitLength();
        int below = n - 53;                                            // bits of Y below the 53 kept for h (may be negative)
        double h;
        BigInteger rem;
        if (below > 0) {
            h = Math.scalb((double) y.shiftRight(below).longValueExact(), below - 128);
            rem = y.and(BigInteger.ONE.shiftLeft(below).subtract(BigInteger.ONE));
        } else {
            h = Math.scalb((double) y.shiftLeft(-below).longValueExact(), below - 128);
            rem = BigInteger.ZERO;
        }
        if (rem.signum() == 0) {
            return new double[] {h, 0.0};
        }
        // the 64 bits right below h's last bit, top-justified
        BigInteger r64 = below >= 64 ? rem.shiftRight(below - 64) : rem.shiftLeft(64 - below);
        if (r64.signum() == 0) {
            return new double[] {h, 0.0};
        }
        int bl = r64.bitLength();
        double l;
        if (bl > 53) {
            l = Math.scalb((double) r64.shiftRight(bl - 53).longValueExact(), (bl - 53) + (below - 64) - 128);
        } else {
            l = Math.scalb((double) r64.longValueExact(), (below - 64) - 128);
        }
        return new double[] {h, l};
    }

    @Test
    void setDdEqualsTheExactTruncationModel() {
        SplittableRandom r = new SplittableRandom(20261312L);
        int n = 300_000;
        int mismatches = 0;
        int zeroC1 = 0;
        int zeroC1Mismatches = 0;
        int zeroC1ScaleMismatches = 0;
        double[] out = new double[2];
        for (int i = 0; i < n; i++) {
            long c1;
            long c0 = r.nextLong();
            if (i < 50_000) {
                c1 = 0L;
                c0 = c0 >>> r.nextInt(62);                             // leading zeros 0..61; c0 == 1 (a shift by 64 in the C) is excluded below
                if (c0 == 1L) {
                    c0 = 3L;
                }
            } else {
                c1 = r.nextLong() >>> r.nextInt(64);
                if (c1 == 0L) {
                    c1 = 1L;
                }
            }
            CoreMathTan.setDd(out, c1, c0);
            double[] want = modelSetDd(c1, c0);
            boolean hEq = Double.doubleToRawLongBits(out[0]) == Double.doubleToRawLongBits(want[0]);
            boolean lEq = Double.doubleToRawLongBits(out[1]) == Double.doubleToRawLongBits(want[1]);
            if (c1 != 0L) {
                if (!hEq || !lEq) {
                    mismatches++;
                }
            } else {
                // Found at the first run (DEVIATION from the sealed B2-R3 text): in the c1 = 0 branch the C writes the exponent field of l as f - 64 - g where the c1 != 0 branch
                // (and the exact value) has f - 53 - g, so l comes out 2^-11 times the exact truncation. The branch is unreachable from tan (it needs frac(x/(2 pi)) < 2^-64; the
                // smallest possible is about 2^-62). The port transcribes the C text (f - 64 - g), which this test checks against a model written from that text (the C was not run): h is exact and l equals the exact value times 2^-11.
                zeroC1++;
                if (!hEq) {
                    zeroC1Mismatches++;
                }
                if (Double.doubleToRawLongBits(out[1]) != Double.doubleToRawLongBits(Math.scalb(want[1], -11))) {
                    zeroC1ScaleMismatches++;
                }
            }
        }
        CoreMathTan.setDd(out, 0L, 0L);
        System.out.println(new StringBuilder(220).append("B2-R3 set_dd: c1 != 0: ").append(mismatches).append(" mismatches of ").append(n - zeroC1).append(" pairs; c1 = 0: h differs on ").append(zeroC1Mismatches)
                .append(" of ").append(zeroC1).append(", l differs from (exact value x 2^-11) on ").append(zeroC1ScaleMismatches).append("; (0, 0) gives (").append(out[0]).append(", ").append(out[1]).append(')'));
        assertEquals(0, mismatches);
        assertEquals(0, zeroC1Mismatches);
        assertEquals(0, zeroC1ScaleMismatches);
        assertEquals(0.0, out[0]);
        assertEquals(0.0, out[1]);
    }

    // ------------------------------------------------------------------------------------------
    // reduce_fast
    // ------------------------------------------------------------------------------------------

    private static final class FastModel {
        double h;
        double l;
        int i;
    }

    /** Model of the large branch of reduce_fast (x > 2 pi): the word chain with an optionally dropped carry (1 = into c[1], 2 = into c[2]), the shift, set_dd, the grid split. */
    private static FastModel modelReduceFast(double x, int dropped) {
        long tb = Double.doubleToRawLongBits(x);
        int e = (int) ((tb >>> 52) & 0x7ff);
        BigInteger m = u((1L << 52) | (tb & 0xfffffffffffffL));
        BigInteger c0;
        BigInteger c1;
        BigInteger c2;
        int shift;
        if (e <= 1074) {
            BigInteger v = m.multiply(tw(2));
            BigInteger uu = m.multiply(tw(1));
            c0 = uu.and(M64).add(v.shiftRight(64)).and(M64);
            boolean carry1 = c0.compareTo(uu.and(M64)) < 0;
            c1 = uu.shiftRight(64).add(dropped == 1 || !carry1 ? BigInteger.ZERO : BigInteger.ONE);
            uu = m.multiply(tw(0));
            c1 = c1.add(uu.and(M64)).and(M64);
            boolean carry2 = c1.compareTo(uu.and(M64)) < 0;
            c2 = uu.shiftRight(64).add(dropped == 2 || !carry2 ? BigInteger.ZERO : BigInteger.ONE);
            shift = 1075 - e;
        } else {
            int i = (e - 1138 + 63) / 64;
            BigInteger v = m.multiply(tw(i + 3));
            BigInteger uu = m.multiply(tw(i + 2));
            c0 = uu.and(M64).add(v.shiftRight(64)).and(M64);
            boolean carry1 = c0.compareTo(uu.and(M64)) < 0;
            c1 = uu.shiftRight(64).add(dropped == 1 || !carry1 ? BigInteger.ZERO : BigInteger.ONE);
            uu = m.multiply(tw(i + 1));
            c1 = c1.add(uu.and(M64)).and(M64);
            boolean carry2 = c1.compareTo(uu.and(M64)) < 0;
            c2 = uu.shiftRight(64).add(dropped == 2 || !carry2 ? BigInteger.ZERO : BigInteger.ONE);
            uu = m.multiply(tw(i));
            c2 = c2.add(uu.and(M64)).and(M64);
            shift = 1139 + (i << 6) - e;
        }
        BigInteger s = c2.shiftLeft(128).or(c1.shiftLeft(64)).or(c0);
        BigInteger y = s.shiftRight(shift).and(M128);
        double[] dd = modelSetDd(y.shiftRight(64).longValue(), y.longValue());
        FastModel fm = new FastModel();
        double i = Math.floor(dd[0] * 0x1p11);
        fm.h = new BigDecimal(dd[0]).subtract(new BigDecimal(i).divide(BigDecimal.valueOf(2048))).doubleValue();
        fm.l = dd[1];
        fm.i = (int) i;
        return fm;
    }

    private static boolean sameFast(FastModel fm, int i, double[] hl) {
        return fm.i == i && Double.doubleToRawLongBits(fm.h) == Double.doubleToRawLongBits(hl[0]) && Double.doubleToRawLongBits(fm.l) == Double.doubleToRawLongBits(hl[1]);
    }

    /**
     * A 52-bit fraction whose mantissa makes the C's carry {@code which} (1 = the one into c[1], 2 = the one into c[2]) happen for exponent field {@code e}, found by random search with
     * the same unsigned 64-bit arithmetic as the C (the carries occur with probability 2^-11 only, because m has 53 bits and so hi(m*T) is below 2^53); -1 if none is found.
     */
    private static long forcedCarryFraction(SplittableRandom r, int e, int which) {
        int ia;
        int ib;
        int ic;
        if (e <= 1074) {
            ia = 1;
            ib = 2;
            ic = 0;
        } else {
            int i = (e - 1138 + 63) / 64;
            ia = i + 2;
            ib = i + 3;
            ic = i + 1;
        }
        long ta = CoreMathTanTables.t(ia);
        long tb = CoreMathTanTables.t(ib);
        long tc = CoreMathTanTables.t(ic);
        for (int trial = 0; trial < 400_000; trial++) {
            long frac = r.nextLong() & 0xfffffffffffffL;
            long m = (1L << 52) | frac;
            long uLow = m * ta;
            long c0 = uLow + Math.unsignedMultiplyHigh(m, tb);
            boolean carry1 = Long.compareUnsigned(c0, uLow) < 0;
            if (which == 1 && carry1) {
                return frac;
            }
            long c1 = Math.unsignedMultiplyHigh(m, ta) + (carry1 ? 1L : 0L);
            long lowC = m * tc;
            c1 += lowC;
            if (which == 2 && Long.compareUnsigned(c1, lowC) < 0) {
                return frac;
            }
        }
        return -1L;
    }

    @Test
    void reduceFastEqualsTheBigIntegerModelAndTheCarryControlsAreExact() {
        SplittableRandom r = new SplittableRandom(20261313L);
        int n = 0;
        int skippedBelowTwoPi = 0;
        int mismatches = 0;
        int forcedFound = 0;
        int forcedWanted = 0;
        int[] visible = new int[3];
        int[] rejected = new int[3];
        for (int e = 1025; e <= 2046; e++) {
            for (int s = 0; s < 56; s++) {
                long frac;
                if (s < 40) {
                    frac = r.nextLong() & 0xfffffffffffffL;
                    if (s == 0) {
                        frac = 0L;
                    } else if (s == 1) {
                        frac = 0xfffffffffffffL;
                    }
                } else {
                    forcedWanted++;
                    frac = forcedCarryFraction(r, e, s < 48 ? 1 : 2);
                    if (frac < 0) {
                        continue;
                    }
                    forcedFound++;
                }
                double x = Double.longBitsToDouble(((long) e << 52) | frac);
                if (x <= CoreMathTanTables.TWO_PI_LIMIT) {
                    skippedBelowTwoPi++;                              // found at the first run: [4, 2 pi] is the single-product branch, which the large-branch model does not describe
                    continue;
                }
                n++;
                double[] hl = new double[2];
                int i = CoreMathTan.reduceFast(hl, x);
                FastModel want = modelReduceFast(x, -1);
                if (!sameFast(want, i, hl)) {
                    mismatches++;
                    if (mismatches <= 5) {
                        System.out.println(new StringBuilder(260).append("  B2-R4 mismatch: x=").append(CoreMathTestSupport.hex(x)).append(" port i=").append(i).append(" h=").append(CoreMathTestSupport.hex(hl[0]))
                                .append(" l=").append(CoreMathTestSupport.hex(hl[1])).append(" model i=").append(want.i).append(" h=").append(CoreMathTestSupport.hex(want.h)).append(" l=").append(CoreMathTestSupport.hex(want.l)));
                    }
                }
                for (int d = 1; d <= 2; d++) {
                    FastModel dropped = modelReduceFast(x, d);
                    boolean changes = !(dropped.i == want.i && dropped.h == want.h && dropped.l == want.l);
                    if (changes) {
                        visible[d]++;
                    }
                    if (!sameFast(dropped, i, hl)) {
                        rejected[d]++;
                    }
                }
            }
        }
        System.out.println(new StringBuilder(260).append("B2-R4 reduce_fast (x > 2 pi, large branch): ").append(mismatches).append(" mismatches of ").append(n).append(" inputs (").append(skippedBelowTwoPi)
                .append(" skipped: x in [4, 2 pi]); forced-carry mantissas found ").append(forcedFound).append(" of ").append(forcedWanted).append("; dropping the carry into c[1] or c[2] changes the model's output on ")
                .append(visible[1]).append(" and ").append(visible[2]).append(", production differs from the dropped model on ").append(rejected[1]).append(" and ").append(rejected[2]));
        assertEquals(0, mismatches);
        assertEquals(visible[1], rejected[1]);
        assertEquals(visible[2], rejected[2]);
        assertTrue(visible[1] >= 1000 && visible[2] >= 1000, "the population does not exercise the carry controls");
    }

    @Test
    void reduceFastMeetsTheAbsoluteErrorBoundAndTheRanges() {
        SplittableRandom r = new SplittableRandom(20261314L);
        BigDecimal twoPiInv = BigDecimal.ONE.divide(CoreMathTanTestSupport.halfPi().multiply(BigDecimal.valueOf(4), MC), MC);
        BigDecimal bound = new BigDecimal("1.1360").divide(new BigDecimal(BigInteger.ONE.shiftLeft(105)), MC);        // 2^-104.815 = 1.13681 * 2^-105: 1.1360 is stricter
        int n = 20_000;
        int violations = 0;
        int rangeViolations = 0;
        int small = 0;
        int large = 0;
        for (int k = 0; k < n; k++) {
            double x;
            if (k % 4 == 0) {
                x = Math.abs(CoreMathTestSupport.exponentUniform(r.nextLong(), 1, 0x3ff - 25, 0x3ff + 2)[0]);
                if (x <= 0x1.7137449123ef6p-26) {
                    x = 0x1p-24;
                }
                if (x > CoreMathTanTables.TWO_PI_LIMIT) {
                    x = CoreMathTanTables.TWO_PI_LIMIT;
                }
                small++;
            } else {
                x = Math.abs(CoreMathTestSupport.exponentUniform(r.nextLong(), 1, 0x3ff + 2, 2046)[0]);
                if (x > CoreMathTanTables.TWO_PI_LIMIT) {
                    large++;
                }
            }
            double[] hl = new double[2];
            int i = CoreMathTan.reduceFast(hl, x);
            BigDecimal y = new BigDecimal(x).multiply(twoPiInv, MC);
            BigDecimal frac = y.subtract(new BigDecimal(y.toBigInteger()));
            BigDecimal approx = new BigDecimal(i).divide(BigDecimal.valueOf(2048)).add(new BigDecimal(hl[0])).add(new BigDecimal(hl[1]));
            if (frac.subtract(approx).abs().compareTo(bound) >= 0) {
                violations++;
            }
            if (!(hl[0] >= 0.0 && hl[0] < 0x1p-11 && Math.abs(hl[1]) < Math.pow(2.0, -52.36) && i >= 0 && i < 2048)) {
                rangeViolations++;
            }
        }
        System.out.println(new StringBuilder(160).append("B2-R5 reduce_fast: absolute error bound 2^-104.815 violated on ").append(violations).append(" of ").append(n).append(" inputs (").append(large)
                .append(" above 2 pi); range violations ").append(rangeViolations));
        assertEquals(0, violations);
        assertEquals(0, rangeViolations);
        assertTrue(large >= 10_000 && small >= 4_000);
    }

    @Test
    void reduce2SplitsTheGridExactly() {
        SplittableRandom r = new SplittableRandom(20261315L);
        int n = 100_000;
        int mismatches = 0;
        for (int k = 0; k < n; k++) {
            long hi = r.nextLong() | Long.MIN_VALUE;
            long lo = k % 3 == 0 ? 0L : r.nextLong();
            long ex = -r.nextInt(31);
            CoreMathDint x = new CoreMathDint(hi, lo, ex, 0L);
            BigInteger m = CoreMathDintTest.mant(x);
            int got = CoreMathTan.reduce2(x);
            boolean ok;
            if (ex <= -11) {
                ok = got == 0 && x.hi == hi && x.lo == lo && x.ex == ex;
            } else {
                int sh = 117 - (int) ex;                                  // X * 2^11 = M * 2^(ex + 11 - 128): the integer part is M >> (117 - ex)
                int i = m.shiftRight(sh).intValueExact();
                BigInteger rem = m.and(BigInteger.ONE.shiftLeft(sh).subtract(BigInteger.ONE));
                ok = got == i && normalisedEqual(x, rem, ex);
            }
            if (!ok) {
                mismatches++;
            }
        }
        System.out.println(new StringBuilder(80).append("B2-R6 reduce2: ").append(mismatches).append(" mismatches of ").append(n).append(" values"));
        assertEquals(0, mismatches);
    }

    /** True if {@code x} is the normalised form of the 128-bit value {@code rem} at exponent {@code ex}. */
    private static boolean normalisedEqual(CoreMathDint x, BigInteger rem, long ex) {
        if (rem.signum() == 0) {
            return x.hi == 0L && x.lo == 0L && x.ex == ex;
        }
        int z = 128 - rem.bitLength();
        BigInteger want = rem.shiftLeft(z).and(M128);
        return CoreMathDintTest.mant(x).equals(want) && x.ex == ex - z;
    }
}
