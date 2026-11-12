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

package au.net.zeus.jgdms.bench.cel;

import au.net.zeus.jgdms.cel.math.CorrectlyRoundedMath;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stage-2c population generators produce what they claim, and the correctness gate of {@link TrigFunctionBenchmark} can
 * fail. RUNTIME TIER: about half a minute (the gate compares 1,024 inputs of every population with CrMath, and the near-pole
 * generator multiplies 65,536 high-precision numbers); it reads no file. Every check is one a wrong generator fails: a uniform
 * population where "small" is claimed fails the exponent census, a near-pole generator that is not near a pole fails the
 * pole-distance check (and the control shows the check rejects a uniform population), and a wrong provider fails the gate.
 */
class TrigPopulationsTest {

    private static final Map<String, double[][]> CACHE = new HashMap<>();

    private static synchronized double[][] pops(String function, String population) {
        String key = new StringBuilder(function).append('/').append(population).toString();
        return CACHE.computeIfAbsent(key, k -> TrigPopulations.generate(function, population));
    }

    private static int exponent(double x) {
        return (int) ((Double.doubleToRawLongBits(x) >>> 52) & 0x7ff) - 1023;
    }

    private static int[] exponentCensus(double[] xs, int lo, int hi) {
        int[] counts = new int[hi - lo + 1];
        for (double x : xs) {
            int e = exponent(x);
            assertTrue(e >= lo && e <= hi, () -> new StringBuilder("exponent ").append(e).append(" of ").append(x).append(" outside [")
                    .append(lo).append(", ").append(hi).append(']').toString());
            counts[e - lo]++;
        }
        return counts;
    }

    private static long negatives(double[] xs) {
        long n = 0;
        for (double x : xs) {
            if (x < 0.0) {
                n++;
            }
        }
        return n;
    }

    @Test
    void sizesDeterminismAndDistinctNames() {
        for (String f : new String[] {"sin", "cos", "tan", "asin", "acos", "atan", "atan2"}) {
            for (String p : TrigPopulations.populationsOf(f)) {
                double[][] a = TrigPopulations.generate(f, p);
                assertEquals(f.equals("atan2") ? 2 : 1, a.length, () -> new StringBuilder(f).append('/').append(p).toString());
                for (double[] xs : a) {
                    assertEquals(TrigPopulations.SIZE, xs.length);
                    for (double x : xs) {
                        assertTrue(Double.isFinite(x));
                    }
                }
                if (!p.equals(TrigPopulations.NEAR_POLE)) {          // the near-pole generator is exercised once below (it is slow)
                    assertArrayEquals(a[0], TrigPopulations.generate(f, p)[0], () -> new StringBuilder(f).append('/').append(p)
                            .append(" must be deterministic").toString());
                }
            }
        }
        assertThrows(IllegalArgumentException.class, () -> TrigPopulations.generate("asin", TrigPopulations.LARGE));
        assertThrows(IllegalArgumentException.class, () -> TrigPopulations.generate("atan", TrigPopulations.SMALL));
        // two names must not be one population
        assertFalse(Arrays.equals(pops("sin", TrigPopulations.UNIFORM)[0], pops("sin", TrigPopulations.LARGE)[0]));
        assertFalse(Arrays.equals(pops("sin", TrigPopulations.SMALL)[0], pops("sin", TrigPopulations.TINY)[0]));
        assertFalse(Arrays.equals(pops("asin", TrigPopulations.UNIFORM)[0], pops("asin", TrigPopulations.NEAR_ONE)[0]));
        // sin and cos read the same inputs, as do asin and acos (the brief lists one population set for each pair)
        assertArrayEquals(pops("sin", TrigPopulations.SMALL)[0], pops("cos", TrigPopulations.SMALL)[0]);
        assertArrayEquals(pops("asin", TrigPopulations.NEAR_ONE)[0], pops("acos", TrigPopulations.NEAR_ONE)[0]);
    }

    @Test
    void uniformAnglesCoverMinusTwoPiToTwoPi() {
        for (String f : new String[] {"sin", "cos", "tan"}) {
            double[] xs = pops(f, TrigPopulations.UNIFORM)[0];
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            double sumAbs = 0.0;
            for (double x : xs) {
                assertTrue(x >= -TrigPopulations.TWO_PI && x <= TrigPopulations.TWO_PI);
                min = Math.min(min, x);
                max = Math.max(max, x);
                sumAbs += Math.abs(x);
            }
            assertTrue(min < -0.999 * TrigPopulations.TWO_PI && max > 0.999 * TrigPopulations.TWO_PI, "both ends of [-2pi, 2pi] reached");
            double meanAbs = sumAbs / xs.length;
            assertTrue(meanAbs > 3.0 && meanAbs < 3.3, () -> new StringBuilder("mean |x| of a uniform [-2pi, 2pi] is pi, got ").append(meanAbs).toString());
            long neg = negatives(xs);
            assertTrue(neg > 0.45 * xs.length && neg < 0.55 * xs.length);
        }
    }

    @Test
    void largeAnglesSpreadTheExponentFrom10To1023() {
        double[] xs = pops("sin", TrigPopulations.LARGE)[0];
        int[] c = exponentCensus(xs, 10, 1023);
        long n = xs.length;
        // 1,014 exponents over 65,536 inputs: about 65 each; every block of 100 exponents must be populated near its share
        for (int block = 0; block < 10; block++) {
            long inBlock = 0;
            for (int e = block * 100; e < Math.min(c.length, block * 100 + 100); e++) {
                inBlock += c[e];
            }
            int width = Math.min(c.length, block * 100 + 100) - block * 100;
            double share = (double) inBlock / n;
            double expected = (double) width / c.length;
            assertTrue(Math.abs(share - expected) < 0.02, () -> new StringBuilder("block share ").append(share).append(" expected ").append(expected).toString());
        }
        assertTrue(c[1023 - 10] > 10 && c[1010 - 10] > 10, "the top binades (1010 and 1023) are populated");
        long neg = negatives(xs);
        assertTrue(neg > 0.45 * n && neg < 0.55 * n);
    }

    @Test
    void smallSinCosAreInTwoToTheMinus16UpToTwoToTheMinus10() {
        double[] xs = pops("sin", TrigPopulations.SMALL)[0];
        for (double x : xs) {
            double a = Math.abs(x);
            assertTrue(a >= 0x1p-16 && a < 0x1p-10, () -> new StringBuilder("|x| = ").append(Double.toHexString(a)).append(" outside [2^-16, 2^-10)").toString());
        }
        int[] c = exponentCensus(xs, -16, -11);
        for (int e = 0; e < c.length; e++) {
            int bin = e;
            assertTrue(c[e] > 9000 && c[e] < 12800, () -> new StringBuilder("exponent ").append(bin - 16).append(" holds ").append(c[bin])
                    .append(" of 65,536; about 10,923 each expected").toString());
        }
        long neg = negatives(xs);
        assertTrue(neg > 0.45 * xs.length && neg < 0.55 * xs.length);
    }

    @Test
    void tinySinCosAreBelowTheEarlyExitsOfBothFunctions() {
        double[] xs = pops("sin", TrigPopulations.TINY)[0];
        for (double x : xs) {
            double a = Math.abs(x);
            assertTrue(a < 0x1p-27 && a < 0x1p-26 && a >= 0x1p-60, () -> new StringBuilder("|x| = ").append(Double.toHexString(a)).toString());
        }
        int[] c = exponentCensus(xs, -60, -28);
        for (int n : c) {
            assertTrue(n > 1200 && n < 2900, "33 exponents over 65,536 inputs: about 1,986 each");
        }
    }

    // ---------------------------------------------------------------------------------------- the near-pole population

    /** pi to 100 decimals, written down independently of the generator's Machin series; the anchor of the half-pi copy. */
    private static final String PI_100 = "3.1415926535897932384626433832795028841971693993751058209749445923078164062862089986280348253421170679";

    private static BigInteger nearestMultiple(double a) {
        BigDecimal q = new BigDecimal(a).divide(TrigPopulations.halfPi(), new MathContext(60));
        return q.setScale(0, RoundingMode.HALF_EVEN).toBigInteger();
    }

    /** Distance in ulps from {@code |x|} to the double nearest the nearest multiple of pi/2 (both positive, so raw bits are ordered). */
    private static long poleDistanceUlps(double x) {
        double a = Math.abs(x);
        BigInteger k = nearestMultiple(a);
        if (k.signum() == 0) {
            return Long.MAX_VALUE;
        }
        double v = TrigPopulations.nearestToMultipleOfHalfPi(k);
        return Math.abs(Double.doubleToRawLongBits(a) - Double.doubleToRawLongBits(v));
    }

    @Test
    void nearPoleTanInputsAreWithinEightUlpsOfAMultipleOfHalfPi() {
        BigDecimal piError = TrigPopulations.halfPi().multiply(BigDecimal.valueOf(2)).subtract(new BigDecimal(PI_100)).abs();
        assertTrue(piError.compareTo(new BigDecimal("1e-98")) < 0, () -> new StringBuilder("the copied pi/2 disagrees with pi at ").append(piError).toString());

        double[] xs = pops("tan", TrigPopulations.NEAR_POLE)[0];
        assertArrayEquals(xs, TrigPopulations.nearHalfPiMultiples(TrigPopulations.NEAR_POLE_SEED, TrigPopulations.SIZE,
                TrigPopulations.NEAR_POLE_MAX_BITS), "deterministic in its seed");
        long within = 0;
        long checked = 0;
        long below27 = 0;
        for (int p = 0; p < xs.length; p += 16) {
            checked++;
            if (poleDistanceUlps(xs[p]) <= 8L) {
                within++;
            }
            if (Math.abs(xs[p]) < 0x1p27) {
                below27++;
            }
        }
        assertEquals(checked, within, "every near-pole input is within 8 ulps of the double nearest a multiple of pi/2");
        // k has a bit length uniform in [1, 52], so x = k pi/2 is below 2^27 for bit lengths up to about 26 or 27: about half
        double share = (double) below27 / checked;
        assertTrue(share > 0.45 && share < 0.62, () -> new StringBuilder("share of near-pole inputs below 2^27 is ").append(share).toString());
        long neg = negatives(xs);
        assertTrue(neg > 0.45 * xs.length && neg < 0.55 * xs.length);

        // the check can fail: uniform angles are essentially never within 8 ulps of a multiple of pi/2
        double[] uniform = pops("tan", TrigPopulations.UNIFORM)[0];
        long uniformWithin = 0;
        long uniformChecked = 0;
        for (int p = 0; p < uniform.length; p += 16) {
            uniformChecked++;
            if (poleDistanceUlps(uniform[p]) <= 8L) {
                uniformWithin++;
            }
        }
        final long withinFinal = uniformWithin;
        final long checkedFinal = uniformChecked;
        assertTrue(withinFinal * 100 < checkedFinal, () -> new StringBuilder(String.valueOf(withinFinal)).append(" of ")
                .append(checkedFinal).append(" uniform angles are within 8 ulps of a pole").toString());
    }

    // ---------------------------------------------------------------------------------------- inverse trig and atan

    @Test
    void asinAcosPopulationsAreTheUnitIntervalAndItsEdge() {
        for (String f : new String[] {"asin", "acos"}) {
            double[] u = pops(f, TrigPopulations.UNIFORM)[0];
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (double x : u) {
                assertTrue(x >= -1.0 && x <= 1.0);
                min = Math.min(min, x);
                max = Math.max(max, x);
            }
            assertTrue(min < -0.999 && max > 0.999);
            double[] n = pops(f, TrigPopulations.NEAR_ONE)[0];
            for (double x : n) {
                double a = Math.abs(x);
                assertTrue(a >= 0.99 && a < 1.0, () -> new StringBuilder("|x| = ").append(a).append(" outside [0.99, 1)").toString());
            }
            long neg = negatives(n);
            assertTrue(neg > 0.45 * n.length && neg < 0.55 * n.length);
        }
    }

    @Test
    void atanAndAtan2PopulationsSpreadTheExponentFromMinus30To60() {
        double[] a = pops("atan", TrigPopulations.WIDE)[0];
        int[] c = exponentCensus(a, -30, 60);
        for (int n : c) {
            assertTrue(n > 400 && n < 1100, "91 exponents over 65,536 inputs: about 720 each");
        }
        long neg = negatives(a);
        assertTrue(neg > 0.45 * a.length && neg < 0.55 * a.length);
        double[][] pair = pops("atan2", TrigPopulations.PAIRS);
        exponentCensus(pair[0], -30, 60);
        exponentCensus(pair[1], -30, 60);
        assertFalse(Arrays.equals(pair[0], pair[1]), "y and x are independent draws");
        assertFalse(Arrays.equals(pair[0], a), "atan2's y is not atan's population");
    }

    // ---------------------------------------------------------------------------------------- the gate

    /** A provider that is the production one except that sin is one ulp up on one input: the gate must refuse it. */
    private static final class OneUlpWrong implements CorrectlyRoundedMath {
        private final CorrectlyRoundedMath delegate = TrigProviders.coreMath();
        private final double wrongAt;

        OneUlpWrong(double wrongAt) {
            this.wrongAt = wrongAt;
        }

        @Override public double sin(double x) { return x == wrongAt ? Math.nextUp(delegate.sin(x)) : delegate.sin(x); }
        @Override public double cos(double x) { return delegate.cos(x); }
        @Override public double tan(double x) { return delegate.tan(x); }
        @Override public double asin(double x) { return delegate.asin(x); }
        @Override public double acos(double x) { return delegate.acos(x); }
        @Override public double atan(double x) { return delegate.atan(x); }
        @Override public double atan2(double y, double x) { return delegate.atan2(y, x); }
    }

    @Test
    void everyPopulationPassesTheGateAndTheGateCanFail() {
        CorrectlyRoundedMath core = TrigProviders.coreMath();
        CorrectlyRoundedMath cr = TrigProviders.crMath();
        int gates = 0;
        for (String f : new String[] {"sin", "cos", "tan", "asin", "acos", "atan", "atan2"}) {
            for (String p : TrigPopulations.populationsOf(f)) {
                TrigProviders.GateResult r = TrigProviders.gate(f, p, pops(f, p), core, cr);
                System.out.println(r.describe());
                assertEquals(TrigProviders.GATE_SAMPLES, r.compared());
                assertTrue(r.compared() >= 1000, "the brief asks for at least 1,000 sampled inputs");
                assertTrue(r.tanBefore() >= 0 && r.atan2Before() >= 0, "the counters of a CoreMath are readable by reflection");
                gates++;
            }
        }
        assertEquals(17, gates, "seven functions: sin 4, cos 4, tan 3, asin 2, acos 2, atan 1, atan2 1 populations");

        // the control: a sin that is one ulp wrong on the 3rd gate input must be refused, naming the input
        double[] uniform = pops("sin", TrigPopulations.UNIFORM)[0];
        int gatePosition = 3 * (TrigPopulations.SIZE / TrigProviders.GATE_SAMPLES);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> TrigProviders.gate("sin", TrigPopulations.UNIFORM, pops("sin", TrigPopulations.UNIFORM), new OneUlpWrong(uniform[gatePosition]), cr));
        assertTrue(e.getMessage().startsWith("GATE FAILED"), e.getMessage());
        // and a wrong input OUTSIDE the gate's sample is, by design, not seen: the gate is a sample, not a proof
        int unsampled = gatePosition + 1;
        TrigProviders.gate("sin", TrigPopulations.UNIFORM, pops("sin", TrigPopulations.UNIFORM), new OneUlpWrong(uniform[unsampled]), cr);
    }
}
