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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Stage 2c: what ONE call of each of the seven correctly-rounded functions costs, per input population, for three providers.
 * The operation is ONE CALL ({@code @OperationsPerInvocation(}{@value #BLOCK}{@code )}), so JMH's score is ns per call and
 * {@code -prof gc}'s {@code gc.alloc.rate.norm} is bytes allocated per call.
 * <ul>
 *   <li>{@code core}: {@code MathProviders.correctlyRounded().get()}, the object the evaluator calls (the ports of CORE-MATH);</li>
 *   <li>{@code strict}: {@code StrictMath} through {@link Trig.Strict}, the speed floor, NOT correctly rounded;</li>
 *   <li>{@code cr}: {@code CrMath}, the old BigDecimal path (tens to hundreds of microseconds a call: a small population is
 *       enough, and the iteration time, not a call count, bounds the run).</li>
 * </ul>
 * Run one fork per (function, population, provider, startOffset): the type profile of the interface call is then monomorphic,
 * as in a service that installs one provider. Inputs are pre-generated arrays of {@value TrigPopulations#SIZE} doubles
 * ({@link TrigPopulations}, fixed seeds), read through the stage-1 cursor pattern: the cursor steps by
 * {@value CelEvalBenchmark#STRIDE} (odd, so it visits every position before repeating) from {@code startOffset}, so the JIT cannot
 * fold an input and the population is covered, not a prefix of it. Every result goes to the {@link Blackhole}.
 * <p>
 * The correctness gate and the delegation counters run in {@code @Setup(Level.Trial)} of every {@code core} fork and print one
 * {@code GATE ...} line; the population checksum is printed by every fork (identical across providers). At the end of a fork
 * {@code COVERAGE ...} says how many calls were made and how many distinct positions that touched (min of calls and 65,536).
 *
 * <h2>PREDICTIONS (sealed before the first run of this class or of its test; 2026-11-12, producer Sonnet)</h2>
 * Witness: the dispatcher (Chisel) reads these in the commit that introduces this file. Scoring path: Chisel's JMH timing run
 * ({@code ns/op} = JMH score, {@code B/op} = {@code gc.alloc.rate.norm}, both per CALL, provider {@code core} unless stated); the
 * SMOKE run only checks the gate (0 mismatches, counters 0) and that every configuration runs, and no smoke number is a
 * measurement. Basis for each: the class javadoc of the port named. All are HYPOTHESES about timing; the gate and counter
 * predictions are checkable at once.
 * <ul>
 *   <li><b>sin</b>: SLOWEST = {@code small} (|x| in [2^-16, 2^-10)): the moderate fast path has an ABSOLUTE error bound, so it is
 *       undecided below about 2^-11 and the accurate 128-bit path runs ({@code CoreMathSin}: from 2^-16 up it allocates
 *       {@code U128} objects; the operator's promise). Expected allocation: ABOVE 0, of the order of 1-3 kB per call (about 80
 *       objects of about 32 bytes, UNMEASURED); every other population 0 B. Then {@code large} (Payne-Hanek reduction, 0 B)
 *       above {@code uniform} (0 B), and {@code tiny} fastest (one fused multiply-add below 2^-26, 0 B).</li>
 *   <li><b>cos</b>: SLOWEST = {@code large} (Payne-Hanek). {@code small} is NOT slow: near 1 an ulp of the cosine is 2^-53, so
 *       the same absolute error bound decides almost every input ({@code CoreMathCos} javadoc): {@code small} about as fast as
 *       {@code uniform}. Allocation 0 B on every population; {@code tiny} fastest (returns 1 below 2^-27).</li>
 *   <li><b>tan</b>: SLOWEST = {@code nearpole}: the fast path is undecided for about 30% of inputs within 8 ulps of a multiple
 *       of pi/2 ({@code CoreMathTan} "What it costs") and those run the accurate path with its {@code U128} guard arithmetic.
 *       Allocation ABOVE 0 on {@code nearpole} (the accurate path's objects times the share that reaches it); {@code uniform}
 *       and {@code large} near 0 B (undecided about 0.02% of random inputs), though the small {@code double[]} temporaries of
 *       the fast path may show as a few tens of bytes if the JIT does not scalar-replace them. {@code large} slower than
 *       {@code uniform} (reduction).</li>
 *   <li><b>asin, acos</b>: SLOWEST = {@code nearone} ([0.99, 1) in magnitude), the less certain prediction here: the fast path's
 *       bound is relatively wider near 1, so more calls reach the accurate path. Allocation about 0 B on both populations (the
 *       accurate path is rare). asin and acos within a factor of 1.5 of each other on the same population.</li>
 *   <li><b>atan</b>: one population ({@code wide}); allocation 0 B; no input is slow. <b>atan2</b>: {@code pairs}, 1x to 2x atan
 *       (a division and the quadrant logic before the same core), allocation 0 B; the failure counter 0.</li>
 *   <li><b>providers</b>: on every population {@code strict} < {@code core} < {@code cr} in ns per call, {@code cr} at least 100x
 *       {@code core}; {@code strict} allocates 0 B except possibly for {@code large} sin, cos and tan (the reduction's small
 *       arrays, if escape analysis does not remove them); {@code cr} allocates on EVERY population, at least 10 kB per call.
 *       Exception allowed: {@code core} on {@code tiny} within a factor of 2 of {@code strict}.</li>
 *   <li><b>gate</b>: 0 mismatches of {@value TrigProviders#GATE_SAMPLES} inputs on every population (the ports are verified bit
 *       exact against CrMath); {@code tanGuardDelegations} 0 and {@code atan2CorrectRoundingFailures} 0 before and after a full
 *       pass of every population (measured 0 of 500,000 near-pole inputs by the tan slow tier). ELSE: the gate throws and the
 *       fork does not run; a nonzero counter is reported, not asserted.</li>
 *   <li><b>anti-vacuous</b>: the unit test {@code TrigPopulationsTest} fails on a generator that produces a wrong range, a wrong
 *       exponent spread, a wrong near-pole distance, or a population shared between two names.</li>
 * </ul>
 * Not predicted: absolute ns per call (Chisel's run), and the {@code celE1}/{@code celE2} re-timing (that is the unchanged
 * {@link CelEvalBenchmark}).
 *
 * <h2>PREDICTIONS APPENDED after the Opus review (2026-11-12; the block above is NOT edited; committed BEFORE the smoke of this change)</h2>
 * Review: REVIEW_OPUS_STAGE2C.md (claude-opus-5-5, RUN WITH FIXES). The reviewer measured the share of each timed population that
 * takes the accurate path (his table); these predictions are about THIS harness and are scored by the unit test and the smoke, not
 * by timing, except the last.
 * <ul>
 *   <li><b>A1, the path census (L1)</b>: the {@code PATH-CENSUS} of the GATE line reproduces the reviewer's accurate-path counts EXACTLY on
 *       the fifteen populations he tabled: sin uniform 452, large 528, small 65,536; cos uniform 458, large 518, small 130; tan uniform
 *       12, large 9, nearpole 19,953; asin uniform 540, nearone 50; acos uniform 223, nearone 358; atan wide 2,613; atan2 pairs 12,651
 *       (11,787 of them by the exponent-gap rule), and sin/tiny, cos/tiny are 100% early exit. SCOPE: his loop and mine both call the
 *       port's own {@code fast(...)} by reflection, so agreement validates my loop, not the port.</li>
 *   <li><b>A2, atan2/balanced (M2)</b>: all 65,536 pairs have |ey - ex| at most 2, both exponents stay in [-30, 60], the exponent-gap
 *       count is 0, and the accurate-path share is BELOW 15% and below pairs' 19.30%. Allocation 0 B. Timing (Chisel's run, HYPOTHESIS):
 *       {@code balanced} is faster per call than {@code pairs}, and within 0.7x to 2x of {@code atan/wide}.</li>
 *   <li><b>A3, the identity check (M1)</b>: a provider that is not {@code CoreMath} (the StrictMath placeholder, {@link Trig.Strict})
 *       makes {@code TrigProviders.gate} throw "GATE FAILED ... not ...CoreMath" on {@code sin/small}, which passed before; the reviewer's
 *       mutant M1 (core routed to {@code MathProvider.nonConformantForTestingOnly().get()}) run as a JMH smoke on {@code sin/small}
 *       now ends with a nonzero exit status. A {@code CoreMath} whose tan is one ulp wrong on a sampled input still fails the value
 *       check, and on an unsampled input still passes (the gate is a sample).</li>
 *   <li><b>A4, the checksum (L2)</b>: with {@code c = rotl(c, 5) ^ bits; c *= 0x9E3779B97F4A7C15} the full-pass checksum of
 *       {@code cos/tiny} is not 0 and is the same in every fork of one population.</li>
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Xms512m", "-Xmx1g"})
public class TrigFunctionBenchmark {

    /** Calls per invocation (one operation = one call). */
    static final int BLOCK = 128;

    /**
     * The fixed per-fork state: population, provider, cursor. Subclasses name the function and the populations it has. The
     * {@code @State} is on the abstract class because JMH refuses a {@code @Param} in a class without one.
     */
    @State(Scope.Thread)
    public abstract static class Base {
        @Param({TrigProviders.CORE, TrigProviders.STRICT, TrigProviders.CR})
        public String provider;

        /** Where each fork's cursor starts (two forks per configuration at {@code -f 1}). */
        @Param({"0", "32768"})
        public int startOffset;

        double[] xs;
        double[] ys;
        CorrectlyRoundedMath math;
        int cursor;
        long invocations;
        TrigProviders.GateResult gate;

        abstract String function();

        abstract String population();

        @Setup(Level.Trial)
        public void setup() {
            double[][] pops = TrigPopulations.generate(function(), population());
            xs = pops[0];
            ys = pops.length > 1 ? pops[1] : null;
            math = TrigProviders.byName(provider);
            cursor = startOffset & TrigPopulations.MASK;
            StringBuilder sb = new StringBuilder(160);
            sb.append("POPULATION function=").append(function()).append(" population=").append(population()).append(" provider=")
                    .append(provider).append(" startOffset=").append(startOffset).append(" size=").append(xs.length)
                    .append(" checksum=").append(Long.toHexString(Arrays.hashCode(xs) * 31L + (ys == null ? 0 : Arrays.hashCode(ys))));
            System.out.println(sb);
            if (TrigProviders.CORE.equals(provider)) {
                gate = TrigProviders.gate(function(), population(), pops, math, TrigProviders.crMath());
                System.out.println(gate.describe());
            } else {
                System.out.println(new StringBuilder(96).append("GATE function=").append(function()).append(" population=")
                        .append(population()).append(" skipped: provider ").append(provider)
                        .append(" is not the production port (the core forks gate every population)").toString());
            }
        }

        @TearDown(Level.Trial)
        public void teardown() {
            StringBuilder sb = new StringBuilder(192);
            long calls = invocations * BLOCK;
            sb.append("COVERAGE function=").append(function()).append(" population=").append(population()).append(" provider=")
                    .append(provider).append(" startOffset=").append(startOffset).append(" calls=").append(calls)
                    .append(" distinct-positions=").append(Math.min(calls, (long) TrigPopulations.SIZE));
            if (TrigProviders.CORE.equals(provider)) {
                sb.append(" tanGuardDelegations-after-run=").append(TrigProviders.tanGuardDelegations(math))
                        .append(" atan2CorrectRoundingFailures-after-run=").append(TrigProviders.atan2Failures(math));
            }
            System.out.println(sb);
        }
    }

    @State(Scope.Thread)
    public static class SinState extends Base {
        @Param({TrigPopulations.UNIFORM, TrigPopulations.LARGE, TrigPopulations.SMALL, TrigPopulations.TINY})
        public String population;

        @Override String function() { return "sin"; }

        @Override String population() { return population; }
    }

    @State(Scope.Thread)
    public static class CosState extends Base {
        @Param({TrigPopulations.UNIFORM, TrigPopulations.LARGE, TrigPopulations.SMALL, TrigPopulations.TINY})
        public String population;

        @Override String function() { return "cos"; }

        @Override String population() { return population; }
    }

    @State(Scope.Thread)
    public static class TanState extends Base {
        @Param({TrigPopulations.UNIFORM, TrigPopulations.LARGE, TrigPopulations.NEAR_POLE})
        public String population;

        @Override String function() { return "tan"; }

        @Override String population() { return population; }
    }

    @State(Scope.Thread)
    public static class AsinState extends Base {
        @Param({TrigPopulations.UNIFORM, TrigPopulations.NEAR_ONE})
        public String population;

        @Override String function() { return "asin"; }

        @Override String population() { return population; }
    }

    @State(Scope.Thread)
    public static class AcosState extends Base {
        @Param({TrigPopulations.UNIFORM, TrigPopulations.NEAR_ONE})
        public String population;

        @Override String function() { return "acos"; }

        @Override String population() { return population; }
    }

    @State(Scope.Thread)
    public static class AtanState extends Base {
        @Param({TrigPopulations.WIDE})
        public String population;

        @Override String function() { return "atan"; }

        @Override String population() { return population; }
    }

    @State(Scope.Thread)
    public static class Atan2State extends Base {
        @Param({TrigPopulations.PAIRS, TrigPopulations.BALANCED})
        public String population;

        @Override String function() { return "atan2"; }

        @Override String population() { return population; }
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK)
    public void sin(SinState s, Blackhole bh) {
        double[] xs = s.xs;
        CorrectlyRoundedMath m = s.math;
        int cur = s.cursor;
        for (int b = 0; b < BLOCK; b++) {
            bh.consume(m.sin(xs[cur]));
            cur = (cur + CelEvalBenchmark.STRIDE) & TrigPopulations.MASK;
        }
        s.cursor = cur;
        s.invocations++;
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK)
    public void cos(CosState s, Blackhole bh) {
        double[] xs = s.xs;
        CorrectlyRoundedMath m = s.math;
        int cur = s.cursor;
        for (int b = 0; b < BLOCK; b++) {
            bh.consume(m.cos(xs[cur]));
            cur = (cur + CelEvalBenchmark.STRIDE) & TrigPopulations.MASK;
        }
        s.cursor = cur;
        s.invocations++;
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK)
    public void tan(TanState s, Blackhole bh) {
        double[] xs = s.xs;
        CorrectlyRoundedMath m = s.math;
        int cur = s.cursor;
        for (int b = 0; b < BLOCK; b++) {
            bh.consume(m.tan(xs[cur]));
            cur = (cur + CelEvalBenchmark.STRIDE) & TrigPopulations.MASK;
        }
        s.cursor = cur;
        s.invocations++;
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK)
    public void asin(AsinState s, Blackhole bh) {
        double[] xs = s.xs;
        CorrectlyRoundedMath m = s.math;
        int cur = s.cursor;
        for (int b = 0; b < BLOCK; b++) {
            bh.consume(m.asin(xs[cur]));
            cur = (cur + CelEvalBenchmark.STRIDE) & TrigPopulations.MASK;
        }
        s.cursor = cur;
        s.invocations++;
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK)
    public void acos(AcosState s, Blackhole bh) {
        double[] xs = s.xs;
        CorrectlyRoundedMath m = s.math;
        int cur = s.cursor;
        for (int b = 0; b < BLOCK; b++) {
            bh.consume(m.acos(xs[cur]));
            cur = (cur + CelEvalBenchmark.STRIDE) & TrigPopulations.MASK;
        }
        s.cursor = cur;
        s.invocations++;
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK)
    public void atan(AtanState s, Blackhole bh) {
        double[] xs = s.xs;
        CorrectlyRoundedMath m = s.math;
        int cur = s.cursor;
        for (int b = 0; b < BLOCK; b++) {
            bh.consume(m.atan(xs[cur]));
            cur = (cur + CelEvalBenchmark.STRIDE) & TrigPopulations.MASK;
        }
        s.cursor = cur;
        s.invocations++;
    }

    /** y is the first population array, x the second (both from atan's distribution), read at the same cursor position. */
    @Benchmark
    @OperationsPerInvocation(BLOCK)
    public void atan2(Atan2State s, Blackhole bh) {
        double[] ys = s.xs;
        double[] xs = s.ys;
        CorrectlyRoundedMath m = s.math;
        int cur = s.cursor;
        for (int b = 0; b < BLOCK; b++) {
            bh.consume(m.atan2(ys[cur], xs[cur]));
            cur = (cur + CelEvalBenchmark.STRIDE) & TrigPopulations.MASK;
        }
        s.cursor = cur;
        s.invocations++;
    }
}
