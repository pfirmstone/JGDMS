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
                      long atan2After, long passChecksum) {

        String describe() {
            return new StringBuilder(192).append("GATE function=").append(function).append(" population=").append(population)
                    .append(" provider=core compared=").append(compared).append(" bit-mismatches-against-CrMath=0")
                    .append(" tanGuardDelegations before=").append(tanBefore).append(" after=").append(tanAfter)
                    .append(" atan2CorrectRoundingFailures before=").append(atan2Before).append(" after=").append(atan2After)
                    .append(" (counters: -1 = not a CoreMath); full-pass checksum=").append(Long.toHexString(passChecksum)).toString();
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
            checksum += Double.doubleToRawLongBits(apply(core, function, as[p], bs == null ? 0.0 : bs[p]));
        }
        return new GateResult(function, population, GATE_SAMPLES, tanBefore, tanGuardDelegations(core), atan2Before,
                atan2Failures(core), checksum);
    }
}
