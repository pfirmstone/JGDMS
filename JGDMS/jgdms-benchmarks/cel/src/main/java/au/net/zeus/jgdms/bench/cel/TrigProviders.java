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
import au.net.zeus.jgdms.cel.math.MathProviders;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * The three providers {@link TrigFunctionBenchmark} times, the delegation counters of the production provider, and the
 * correctness gate that stands in front of every benchmark of the production provider.
 * <ul>
 *   <li>{@value #CORE}: {@code MathProviders.correctlyRounded().get()}, the object the evaluator calls
 *       ({@code Evaluator.callTranscendental}); the ports of CORE-MATH.</li>
 *   <li>{@value #STRICT}: {@link Trig.Strict} (reused from stage 1): {@code StrictMath}, the speed floor, NOT correctly rounded.</li>
 *   <li>{@value #CR}: {@code CrMath}, the BigDecimal oracle and the old production path.</li>
 * </ul>
 * WHY reflection for {@code CrMath} and the counters: {@code CrMath} and {@code CoreMath} are package-private on purpose
 * ({@code MathProviders} says so) and the counters {@code tanGuardDelegations()} and {@code atan2CorrectRoundingFailures()} are
 * package-private; the stage-2c brief forbids touching the {@code jgdms-cel} main sources, so a hook there was not an option.
 * This works because both jars are on the CLASS PATH (one unnamed module): the shaded benchmark jar and surefire. It would stop
 * working if jgdms-cel became a named module that does not open its package; the failure is loud ({@link IllegalStateException}).
 */
final class TrigProviders {

    static final String CORE = "core";
    static final String STRICT = "strict";
    static final String CR = "cr";

    /** Inputs the gate compares (at least 1,000 per the brief): every 64th of the population's 65,536. */
    static final int GATE_SAMPLES = 1024;

    private TrigProviders() {
    }

    static CorrectlyRoundedMath byName(String provider) {
        return switch (provider) {
            case CORE -> coreMath();
            case STRICT -> new Trig.Strict();
            case CR -> crMath();
            default -> throw new IllegalArgumentException(new StringBuilder("unknown provider ").append(provider).toString());
        };
    }

    static CorrectlyRoundedMath coreMath() {
        return MathProviders.correctlyRounded().get();
    }

    static CorrectlyRoundedMath crMath() {
        try {
            Constructor<?> c = Class.forName("au.net.zeus.jgdms.cel.math.CrMath").getDeclaredConstructor();
            c.setAccessible(true);
            return (CorrectlyRoundedMath) c.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot reach CrMath by reflection (see TrigProviders javadoc)", e);
        }
    }

    /** {@code CoreMath.tanGuardDelegations()}, or -1 when {@code core} is not a CoreMath (a test double). */
    static long tanGuardDelegations(CorrectlyRoundedMath core) {
        return counter(core, "tanGuardDelegations");
    }

    /** {@code CoreMath.atan2CorrectRoundingFailures()}, or -1 when {@code core} is not a CoreMath (a test double). */
    static long atan2Failures(CorrectlyRoundedMath core) {
        return counter(core, "atan2CorrectRoundingFailures");
    }

    private static long counter(CorrectlyRoundedMath core, String method) {
        try {
            Method m = core.getClass().getDeclaredMethod(method);
            m.setAccessible(true);
            return (Long) m.invoke(core);
        } catch (NoSuchMethodException e) {
            return -1L;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(new StringBuilder("cannot read the counter ").append(method).toString(), e);
        }
    }

    static double apply(CorrectlyRoundedMath m, String function, double a, double b) {
        return switch (function) {
            case "sin" -> m.sin(a);
            case "cos" -> m.cos(a);
            case "tan" -> m.tan(a);
            case "asin" -> m.asin(a);
            case "acos" -> m.acos(a);
            case "atan" -> m.atan(a);
            case "atan2" -> m.atan2(a, b);
            default -> throw new IllegalArgumentException(new StringBuilder("unknown function ").append(function).toString());
        };
    }

    /**
     * What the gate found: how many inputs it compared, the delegation counters of the production provider before and after one
     * pass over the WHOLE population (expected 0 and 0; reported, not asserted: the brief), and a checksum of that pass.
     */
    record GateResult(String function, String population, int compared, long tanBefore, long tanAfter, long atan2Before,
                      long atan2After, long passChecksum, Census census) {

        String describe() {
            return new StringBuilder(320).append("GATE function=").append(function).append(" population=").append(population)
                    .append(" provider=core compared=").append(compared).append(" bit-mismatches-against-CrMath=0")
                    .append(" tanGuardDelegations before=").append(tanBefore).append(" after=").append(tanAfter)
                    .append(" atan2CorrectRoundingFailures before=").append(atan2Before).append(" after=").append(atan2After)
                    .append(" full-pass checksum=").append(Long.toHexString(passChecksum)).append(" PATH-CENSUS ")
                    .append(census.describe(function)).toString();
        }
    }

    /**
     * Which path each input of a population takes in the production port (review L1, Opus 2026-11-12: every per-population number is
     * a mixture of paths, and the run must print the mixture). Counted with the port's own {@code fast(...)} entry, by reflection:
     * {@code NaN} means the fast path DECLINED and the call runs the accurate path. Inputs below the early exits of sin (2^-26) and
     * cos (2^-27) are counted apart (the fast entry is not defined there).
     *
     * @param inputs        inputs examined
     * @param earlyExit     answered by the port's early exit (sin below 2^-26, cos below 2^-27)
     * @param declined      the fast path declined: the accurate path runs (an input the early exit answered is not counted here)
     * @param gap53         atan2 only: pairs whose exponents differ by 53 or more, which the fast path refuses by rule; else -1
     * @param below31       one-argument functions: inputs with |x| below 2^31 (the reduction by multiples of pi/2^14) and how many of them declined
     * @param atOrAbove31   the same for |x| at or above 2^31 (the Payne-Hanek reduction)
     */
    record Census(long inputs, long earlyExit, long declined, long gap53, long below31, long declinedBelow31, long atOrAbove31,
                  long declinedAtOrAbove31) {

        String describe(String function) {
            StringBuilder sb = new StringBuilder(160);
            sb.append("accurate-path(fast declined)=").append(declined).append(" of ").append(inputs).append(" (")
                    .append(String.format("%.2f", 100.0 * declined / inputs)).append("%) early-exit=").append(earlyExit);
            if (gap53 >= 0) {
                sb.append(" exponent-gap>=53=").append(gap53);
            }
            if ("tan".equals(function)) {
                sb.append(" below-2^31: ").append(declinedBelow31).append(" of ").append(below31).append("; at-or-above-2^31: ")
                        .append(declinedAtOrAbove31).append(" of ").append(atOrAbove31);
            }
            return sb.toString();
        }
    }

    /** Names a port: {@code CoreMathSin} and so on. */
    private static String portClass(String function) {
        StringBuilder sb = new StringBuilder("au.net.zeus.jgdms.cel.math.CoreMath");
        sb.append(Character.toUpperCase(function.charAt(0))).append(function, 1, function.length());
        return sb.toString();
    }

    /** The census of one population (see {@link Census}); one pass, one reflective call per input. */
    static Census census(String function, double[][] populations) {
        double[] as = populations[0];
        double[] bs = populations.length > 1 ? populations[1] : null;
        try {
            Class<?> port = Class.forName(portClass(function));
            Method fast = bs == null ? port.getDeclaredMethod("fast", double.class)
                    : port.getDeclaredMethod("fast", double.class, double.class);
            fast.setAccessible(true);
            long early = 0;
            long declined = 0;
            long gap53 = bs == null ? -1L : 0L;
            long below = 0;
            long declinedBelow = 0;
            long above = 0;
            long declinedAbove = 0;
            for (int p = 0; p < as.length; p++) {
                double a = as[p];
                int e = (int) ((Double.doubleToRawLongBits(a) >>> 52) & 0x7ff);
                if (bs != null) {
                    int ex = (int) ((Double.doubleToRawLongBits(bs[p]) >>> 52) & 0x7ff);
                    if (Math.abs(e - ex) >= 53) {
                        gap53++;
                    }
                }
                boolean exits = ("sin".equals(function) && e < 0x3ff - 26) || ("cos".equals(function) && e < 0x3ff - 27);
                boolean declines = false;
                if (exits) {
                    early++;
                } else {
                    double r = (Double) (bs == null ? fast.invoke(null, a) : fast.invoke(null, a, bs[p]));
                    declines = r != r;
                    if (declines) {
                        declined++;
                    }
                }
                if (bs == null && !exits) {
                    if (e < 0x3ff + 31) {
                        below++;
                        declinedBelow += declines ? 1 : 0;
                    } else {
                        above++;
                        declinedAbove += declines ? 1 : 0;
                    }
                }
            }
            return new Census(as.length, early, declined, gap53, below, declinedBelow, above, declinedAbove);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(new StringBuilder("cannot take the path census of ").append(function).toString(), e);
        }
    }

    static final String CORE_MATH_CLASS = "au.net.zeus.jgdms.cel.math.CoreMath";

    /**
     * Review M1 (Opus 2026-11-12): the provider labelled {@code core} must BE the production {@code CoreMath}. A StrictMath- or
     * Math-backed provider agrees with CrMath on every sampled input of {@code sin/small}, {@code sin/tiny}, {@code cos/small},
     * {@code cos/tiny} and nearly so on {@code tan/nearpole}, so comparing results cannot tell them apart: the identity is checked.
     *
     * @throws IllegalStateException naming the class found
     */
    static void requireCoreMath(CorrectlyRoundedMath core) {
        if (!CORE_MATH_CLASS.equals(core.getClass().getName()) || tanGuardDelegations(core) < 0 || atan2Failures(core) < 0) {
            throw new IllegalStateException(new StringBuilder("GATE FAILED: the provider labelled core is ")
                    .append(core.getClass().getName()).append(", not ").append(CORE_MATH_CLASS)
                    .append(" (or its delegation counters are unreadable): its timings would be reported as CORE-MATH's").toString());
        }
    }

    /**
     * The correctness gate: {@code core} must equal {@code cr} bit for bit on {@value #GATE_SAMPLES} inputs spread evenly over the
     * population (a fast WRONG benchmark must not run: it throws), then {@code core}'s delegation counters are read before and
     * after one pass over the whole population.
     *
     * @param populations {@code {xs}} or {@code {ys, xs}} as {@link TrigPopulations#generate}
     * @throws IllegalStateException on the first input where the two differ, naming it
     */
    static GateResult gate(String function, String population, double[][] populations, CorrectlyRoundedMath core,
                           CorrectlyRoundedMath cr) {
        requireCoreMath(core);
        double[] as = populations[0];
        double[] bs = populations.length > 1 ? populations[1] : null;
        int step = TrigPopulations.SIZE / GATE_SAMPLES;
        for (int i = 0; i < GATE_SAMPLES; i++) {
            int p = i * step;
            double a = as[p];
            double b = bs == null ? 0.0 : bs[p];
            double got = apply(core, function, a, b);
            double want = apply(cr, function, a, b);
            if (Double.doubleToRawLongBits(got) != Double.doubleToRawLongBits(want)) {
                throw new IllegalStateException(new StringBuilder("GATE FAILED: ").append(function).append(" population ")
                        .append(population).append(" position ").append(p).append(" input ").append(Double.toHexString(a))
                        .append(bs == null ? "" : new StringBuilder(" and ").append(Double.toHexString(b)).toString())
                        .append(": core ").append(Double.toHexString(got)).append(" CrMath ").append(Double.toHexString(want)).toString());
            }
        }
        long tanBefore = tanGuardDelegations(core);
        long atan2Before = atan2Failures(core);
        long checksum = 0L;
        for (int p = 0; p < TrigPopulations.SIZE; p++) {
            // rotate, xor, multiply by an odd constant: NOT linear. The sum failed on cos below 2^-27 (65,536 results of 1.0 sum to 0
            // mod 2^64) and the polynomial c*1000003 + bits failed too (its 2-adic valuation is 69, review L2). Review's mix.
            checksum = Long.rotateLeft(checksum, 5) ^ Double.doubleToRawLongBits(apply(core, function, as[p], bs == null ? 0.0 : bs[p]));
            checksum *= 0x9E3779B97F4A7C15L;
        }
        return new GateResult(function, population, GATE_SAMPLES, tanBefore, tanGuardDelegations(core), atan2Before,
                atan2Failures(core), checksum, census(function, populations));
    }
}
