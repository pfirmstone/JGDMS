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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.SplittableRandom;

/**
 * The fixed-seed input populations of {@link TrigFunctionBenchmark}: arrays of {@value #SIZE} doubles per (function,
 * population), generated once per fork. Every generator is deterministic in its seed, so the correctly-rounded, StrictMath
 * and CrMath forks of one (function, population) read bit-identical inputs (the benchmark prints a checksum of them).
 * <p>
 * WHY these populations (operator 2026-11-11: "as fast as possible, it will be used to operate on large data sets"; Stage 2c
 * brief): the cost of a correctly-rounded function depends on which internal path an argument takes, so a single uniform
 * population would average the paths away. {@code sin} below about 2^-11 is undecided by the fast path and runs the
 * accurate paths (double-double below 2^-16, 128-bit fixed point with {@code U128} objects from 2^-16 up: {@code CoreMathSin}
 * class javadoc), and {@code tan} near a pole is undecided about 30% of the time ({@code CoreMathTan} "What it costs").
 * <p>
 * The near-pole generator and the half-pi constant are COPIED from the jgdms-cel TEST tree at tip 7e1f8315a, because that tree
 * is not published to this module: {@code CoreMathTanTestSupport.nearHalfPiMultiples} (lines 141-166) and its pi/2 by Machin
 * (lines 98-132: {@code PI_DIGITS}, {@code arctanInv}, {@code halfPi}, {@code nearestToMultipleOfHalfPi}), as used by {@code CoreMathTanSlowTest.nearMultiplesOfHalfPiQuarterMillionEach} (line 159: seed 20261451,
 * k of at most 52 bits). With the same seed the first {@value #SIZE} inputs of that slow-tier population are these. The
 * exponent generator follows {@code CoreMathTestSupport.exponentUniform} (line 126) with unbiased exponents.
 */
final class TrigPopulations {

    static final int SIZE = 1 << 16;
    static final int MASK = SIZE - 1;
    static final double TWO_PI = 2.0 * Math.PI;

    static final String UNIFORM = "uniform";
    static final String LARGE = "large";
    static final String SMALL = "small";
    static final String TINY = "tiny";
    static final String NEAR_POLE = "nearpole";
    static final String NEAR_ONE = "nearone";
    static final String WIDE = "wide";
    static final String PAIRS = "pairs";

    /** The tan near-pole seed and bit length of the slow tier's 52-bit population ({@code CoreMathTanSlowTest.java:159}). */
    static final long NEAR_POLE_SEED = 20261451L;
    static final int NEAR_POLE_MAX_BITS = 52;

    private TrigPopulations() {
    }

    /** The populations of one function, in the order the benchmark parameters list them. */
    static String[] populationsOf(String function) {
        return switch (function) {
            case "sin", "cos" -> new String[] {UNIFORM, LARGE, SMALL, TINY};
            case "tan" -> new String[] {UNIFORM, LARGE, NEAR_POLE};
            case "asin", "acos" -> new String[] {UNIFORM, NEAR_ONE};
            case "atan" -> new String[] {WIDE};
            case "atan2" -> new String[] {PAIRS};
            default -> throw new IllegalArgumentException(new StringBuilder("unknown function ").append(function).toString());
        };
    }

    /**
     * The inputs of one (function, population): {@code {xs}}, or {@code {ys, xs}} for {@code atan2} (the first array is y).
     *
     * @throws IllegalArgumentException for a population the function does not have
     */
    static double[][] generate(String function, String population) {
        boolean ok = false;
        for (String p : populationsOf(function)) {
            ok |= p.equals(population);
        }
        if (!ok) {
            throw new IllegalArgumentException(new StringBuilder(function).append(" has no population ").append(population).toString());
        }
        return switch (function) {
            case "sin", "cos", "tan" -> new double[][] {angles(function, population)};
            case "asin", "acos" -> new double[][] {unitInterval(population)};
            case "atan" -> new double[][] {exponentRange(20261207L, SIZE, -30, 60)};
            default -> new double[][] {exponentRange(20261208L, SIZE, -30, 60), exponentRange(20261209L, SIZE, -30, 60)};
        };
    }

    private static double[] angles(String function, String population) {
        return switch (population) {
            case UNIFORM -> uniform(20261201L, SIZE, -TWO_PI, TWO_PI);
            case LARGE -> exponentRange(20261202L, SIZE, 10, 1023);
            // [2^-16, 2^-10): unbiased exponents -16..-11. 2^-10 itself is excluded by construction (exponent -10 is not drawn).
            case SMALL -> exponentRange(20261203L, SIZE, -16, -11);
            // Narrower than the brief's |x| < 2^-26 on purpose: |x| < 2^-27 is below the early exits of BOTH sin (2^-26) and cos (2^-27).
            case TINY -> exponentRange(20261204L, SIZE, -60, -28);
            case NEAR_POLE -> nearHalfPiMultiples(NEAR_POLE_SEED, SIZE, NEAR_POLE_MAX_BITS);
            default -> throw new IllegalArgumentException(new StringBuilder("no ").append(population).append(" population for ").append(function).toString());
        };
    }

    private static double[] unitInterval(String population) {
        return switch (population) {
            case UNIFORM -> uniform(20261205L, SIZE, -1.0, 1.0);
            case NEAR_ONE -> {
                SplittableRandom r = new SplittableRandom(20261206L);
                double[] xs = new double[SIZE];
                for (int i = 0; i < SIZE; i++) {
                    double v = r.nextDouble(0.99, 1.0);
                    xs[i] = r.nextBoolean() ? -v : v;
                }
                yield xs;
            }
            default -> throw new IllegalArgumentException(new StringBuilder("no ").append(population).append(" unit-interval population").toString());
        };
    }

    /** Uniform in [lo, hi). */
    static double[] uniform(long seed, int n, double lo, double hi) {
        SplittableRandom r = new SplittableRandom(seed);
        double[] xs = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = r.nextDouble(lo, hi);
        }
        return xs;
    }

    /** Random sign and fraction, unbiased binary exponent uniform in [{@code eLo}, {@code eHi}] (normal numbers only). */
    static double[] exponentRange(long seed, int n, int eLo, int eHi) {
        SplittableRandom r = new SplittableRandom(seed);
        double[] xs = new double[n];
        for (int i = 0; i < n; i++) {
            long sign = r.nextBoolean() ? Long.MIN_VALUE : 0L;
            long e = 0x3ffL + eLo + r.nextInt(eHi - eLo + 1);
            long frac = r.nextLong() & 0xfffffffffffffL;
            xs[i] = Double.longBitsToDouble(sign | (e << 52) | frac);
        }
        return xs;
    }

    // ------------------------------------------------------------------------------------------------------------
    // near multiples of pi/2: COPIED from CoreMathTanTestSupport (see the class comment), not invented here
    // ------------------------------------------------------------------------------------------------------------

    private static final int PI_DIGITS = 450;
    private static final MathContext MC = new MathContext(PI_DIGITS + 20);
    private static BigDecimal halfPi;

    private static BigDecimal arctanInv(int n) {
        BigDecimal x = BigDecimal.ONE.divide(BigDecimal.valueOf(n), MC);
        BigDecimal x2 = x.multiply(x, MC);
        BigDecimal eps = BigDecimal.ONE.movePointLeft(PI_DIGITS + 15);
        BigDecimal term = x;
        BigDecimal sum = x;
        for (int k = 1; ; k++) {
            term = term.multiply(x2, MC);
            BigDecimal t = term.divide(BigDecimal.valueOf(2L * k + 1L), MC);
            if (t.compareTo(eps) < 0) {
                break;
            }
            sum = (k % 2 == 1) ? sum.subtract(t, MC) : sum.add(t, MC);
        }
        return sum;
    }

    /** pi/2 to about 450 significant digits (Machin: pi = 16 arctan(1/5) - 4 arctan(1/239)). */
    static synchronized BigDecimal halfPi() {
        if (halfPi == null) {
            BigDecimal pi = arctanInv(5).multiply(BigDecimal.valueOf(16), MC)
                    .subtract(arctanInv(239).multiply(BigDecimal.valueOf(4), MC), MC);
            halfPi = pi.divide(BigDecimal.valueOf(2), MC);
        }
        return halfPi;
    }

    /** The double nearest to {@code k * pi/2}, or 0 if it is not finite. */
    static double nearestToMultipleOfHalfPi(BigInteger k) {
        double v = new BigDecimal(k).multiply(halfPi(), MC).doubleValue();
        return Double.isFinite(v) ? v : 0.0;
    }

    /**
     * {@code n} inputs: {@code k} has a bit length uniform in [1, {@code maxBits}], the input is the double nearest to
     * {@code k*pi/2} moved by -8..+8 ulps, either sign. Of these, the ones with k below about 2^40 are near a pole of tan
     * (where the fast path is undecided about 30% of the time); above that an ulp of the input exceeds the spacing of the
     * poles, so those members are ordinary large arguments. Copied from {@code CoreMathTanTestSupport.nearHalfPiMultiples}.
     */
    static double[] nearHalfPiMultiples(long seed, int n, int maxBits) {
        SplittableRandom r = new SplittableRandom(seed);
        double[] xs = new double[n];
        for (int i = 0; i < n; i++) {
            int bits = 1 + r.nextInt(maxBits);
            BigInteger k = BigInteger.ONE.shiftLeft(bits - 1);
            for (int b = bits - 2; b >= 0; b--) {
                if (r.nextBoolean()) {
                    k = k.setBit(b);
                }
            }
            double x = nearestToMultipleOfHalfPi(k);
            if (x == 0.0) {
                x = 1.0;
            }
            int d = r.nextInt(17) - 8;
            for (int s = 0; s < Math.abs(d); s++) {
                x = d > 0 ? Math.nextUp(x) : Math.nextDown(x);
            }
            if (!Double.isFinite(x)) {
                x = 1.0;
            }
            xs[i] = r.nextBoolean() ? -x : x;
        }
        return xs;
    }
}
