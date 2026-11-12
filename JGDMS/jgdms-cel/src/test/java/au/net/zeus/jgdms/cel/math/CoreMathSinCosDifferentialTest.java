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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.DoublePredicate;
import java.util.function.DoubleUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * STD-011 Stage 2a, phase A: differential tests of the CORE-MATH port of binary64 {@code sin} and
 * {@code cos} ({@link CoreMath}, {@link CoreMathSin}, {@link CoreMathCos}) against {@link CrMath}.
 * Runtime tier: FAST (the default suite). The slow tier ({@code @Tag("slow")}) is
 * {@code CoreMathSinCosSlowTest}.
 *
 * <h2>SEALED PREDICTIONS</h2>
 * Written before any test in this change was run; the commit that holds this text is the seal and
 * is not edited afterwards (a later change is a numbered deviation in the READY file, with this text
 * kept). Population and scoring rule common to all: the port's output and {@code CrMath}'s output
 * for the same input, equal iff their {@code Double.doubleToRawLongBits} are equal, no tolerance;
 * the oracle {@code CrMath} is at this tree (it is not modified by this change); each count is
 * the denominator the test itself prints in its {@code port vs CrMath at {...}} line.
 * <ol>
 *   <li><b>P1 named edge inputs</b> ({@link CoreMathTestSupport#namedInputs}: zeros, subnormals,
 *       2^-26/2^-27/2^-16/2^31 thresholds and neighbours, pi/4 and multiples of pi/2 with
 *       neighbours, 1e22, 1e300, MAX_VALUE, CORE-MATH's four cosine exceptions and its two named
 *       failures): sin 0 mismatches of n, cos 0 mismatches of n, n printed.</li>
 *   <li><b>P2 the first 2,000 finite entries of {@code sin.wc} and of {@code cos.wc}</b>: 0
 *       mismatches of 2,000 each. The {@code .wc} files are hard-to-round worst cases, so this is
 *       where a wrong port is most likely to show.</li>
 *   <li><b>P3 10,000 seeded random doubles, exponent field uniform over 0..2046</b> (seed
 *       20261112, both signs; about half are at or above 2^31, so this population is mostly
 *       the large-argument reduction and the tiny-argument early exit): 0 mismatches of 10,000
 *       for sin and for cos.</li>
 *   <li><b>P4 10,000 seeded random doubles, exponent 2^-30 .. 2^33</b> (seed 20261113; an addition
 *       to the brief's list, because P3 puts only about 3% of its inputs on the moderate-argument
 *       fast path): 0 mismatches of 10,000 for sin and for cos.</li>
 *   <li><b>P5 each path alone.</b> The accurate path called directly on every in-domain input of
 *       P1-P4 (sin: |x| >= 2^-26; cos: |x| >= 2^-27): 0 mismatches. The fast path alone: wherever
 *       it returns a result (not NaN) that result equals the oracle: 0 mismatches. ELSE-BRANCH: a
 *       mismatch of the accurate path alone means the accurate-path port is wrong even if the
 *       combined path passes (the combined path reaches it only when the fast path cannot
 *       decide).</li>
 *   <li><b>P6 control (T3).</b> A candidate that is the port with its output moved one ulp up on
 *       every 1,000th call (calls 0, 1000, ..., 9000) is run through the same comparison over 10,000
 *       inputs (the P3 inputs): the comparison rejects exactly 10 of 10,000 and no other.
 *       ELSE-BRANCH: fewer than 10 rejected means the comparison cannot fail and every "0
 *       mismatches" in P1-P5 is void.</li>
 *   <li><b>P7 port path (T5).</b> With a counting {@code CrMath} installed as the provider's
 *       delegate, over the inputs of P1-P4: 0 delegate calls to {@code sin} or {@code cos}
 *       (every finite input is handled by the port); a provider that delegates everything, under
 *       the same counters, makes as many delegate calls as inputs and is rejected by the same
 *       assertion; the five unported functions reach the delegate on every call; NaN, +Inf and
 *       -Inf reach the delegate, which throws {@code IllegalArgumentException}. ELSE-BRANCH: a
 *       delegate call for a finite input means the port did not take that input: name the class
 *       of input.</li>
 *   <li><b>P8 how often the accurate path runs</b> (reported, not asserted): of P3's and P4's
 *       10,000 inputs the fast path cannot decide at most 2% (200) for each function, and of the
 *       first 2,000 entries of each {@code .wc} file it cannot decide at least 1.
 *       ELSE-BRANCH: more than 2% means the fast path rejects too often (a speed defect, not a
 *       correctness one, which Chisel's timing would show); zero of the {@code .wc} entries means P2 never
 *       exercised the accurate path and only P5 covers it.</li>
 *   <li><b>P9 constants</b> ({@code CoreMathConstantsTest}): 0 mismatches of 1,216 values against
 *       {@code sin.c} and 0 of 1,216 against {@code cos.c}; the control (one flipped bit) is
 *       rejected once. Skipped, and reported as skipped, without the CORE-MATH clone.</li>
 *   <li><b>P10 128-bit emulation</b> ({@code U128Test}): 0 mismatches against BigInteger for add,
 *       sub, mul, mhUU, and over every shift count and comparison; the control (an addition that
 *       drops the carry) is rejected on at least one pair.</li>
 *   <li><b>P11 wiring (T4).</b> {@code MathProviders.correctlyRounded()} returns a provider backed
 *       by {@link CoreMath} that reports conformant; the conformance corpus
 *       ({@code CorpusRunnerTest}) passes with it; the corpus's {@code eval-transcendental.json} has
 *       35 sin rows and 35 cos rows (counted from the file by this test).</li>
 *   <li><b>P12 slow tier</b> ({@code CoreMathSinCosSlowTest}, run once by the producer): 0
 *       mismatches over every finite entry of {@code sin.wc} and of {@code cos.wc}, and over
 *       1,000,000 random doubles per function in each of the two exponent ranges of P3 and P4.</li>
 *   <li><b>P13 time.</b> The fast tier of the new test classes together runs in under 15 s on this machine
 *       (the brief's target; measured by Surefire's report).</li>
 * </ol>
 */
class CoreMathSinCosDifferentialTest {

    private static final DoublePredicate ALL = x -> true;

    /** One input population with the oracle's answers, computed once and shared. */
    private static final class Pop {
        final String name;
        final double[] xs;
        final double[] sinExpected;
        final double[] cosExpected;

        Pop(String name, double[] xs) {
            this.name = name;
            this.xs = xs;
            this.sinExpected = CoreMathTestSupport.oracleValues(xs, CoreMathTestSupport.ORACLE::sin);
            this.cosExpected = CoreMathTestSupport.oracleValues(xs, CoreMathTestSupport.ORACLE::cos);
        }
    }

    private static final Map<String, Pop> POPS = new HashMap<>();

    private static synchronized Pop pop(String key) throws IOException {
        Pop p = POPS.get(key);
        if (p == null) {
            double[] xs;
            switch (key) {
                case "named":
                    xs = CoreMathTestSupport.namedInputs();
                    break;
                case "sin.wc first 2000":
                    xs = CoreMathTestSupport.readWc(CoreMathTestSupport.wcFile("sin"), 2000).values;
                    break;
                case "cos.wc first 2000":
                    xs = CoreMathTestSupport.readWc(CoreMathTestSupport.wcFile("cos"), 2000).values;
                    break;
                case "random exponent 0..2046, seed 20261112, 10000":
                    xs = CoreMathTestSupport.finiteRangeRandom(20261112L, 10_000);
                    break;
                case "random exponent 2^-30..2^33, seed 20261113, 10000":
                    xs = CoreMathTestSupport.moderateRangeRandom(20261113L, 10_000);
                    break;
                default:
                    throw new IllegalArgumentException(key);
            }
            p = new Pop(key, xs);
            POPS.put(key, p);
        }
        return p;
    }

    private static String label(String function, String population, String path) {
        StringBuilder sb = new StringBuilder(96);
        sb.append(function).append(", ").append(population);
        if (!path.isEmpty()) {
            sb.append(", ").append(path);
        }
        return sb.toString();
    }

    /** The shared three-comparison check (provider path, accurate alone, fast alone) of one function on one population. */
    private static void checkPopulation(String function, Pop p) {
        CoreMathTestSupport.checkPopulation(function, p.name, p.xs, function.equals("sin") ? p.sinExpected : p.cosExpected);
    }

    private static void checkBoth(String population) throws IOException {
        Pop p = pop(population);
        checkPopulation("sin", p);
        checkPopulation("cos", p);
    }

    // ----------------------------------------------------------------------------------------
    // T1: fast tier
    // ----------------------------------------------------------------------------------------

    @Test
    void t1aNamedInputs() throws IOException {
        checkBoth("named");
    }

    @Test
    void t1bWorstCasesFirst2000() throws IOException {
        CoreMathTestSupport.requireClone("t1bWorstCasesFirst2000");
        checkPopulation("sin", pop("sin.wc first 2000"));
        checkPopulation("cos", pop("cos.wc first 2000"));
        // the wc entries of the other function are also worth running: both files list hard cases of their own function only
        checkPopulation("cos", pop("sin.wc first 2000"));
        checkPopulation("sin", pop("cos.wc first 2000"));
    }

    @Test
    void t1cRandomOverTheFiniteRange() throws IOException {
        checkBoth("random exponent 0..2046, seed 20261112, 10000");
    }

    @Test
    void t1dRandomOverTheModerateRange() throws IOException {
        checkBoth("random exponent 2^-30..2^33, seed 20261113, 10000");
    }

    // ----------------------------------------------------------------------------------------
    // T3: the control
    // ----------------------------------------------------------------------------------------

    /** The port with its output moved one ulp up on every 1,000th call, in call order (so: sequential). */
    private static DoubleUnaryOperator perturbEvery1000th(DoubleUnaryOperator port) {
        AtomicLong calls = new AtomicLong();
        return x -> {
            double r = port.applyAsDouble(x);
            return (calls.getAndIncrement() % 1000L == 0L) ? Math.nextUp(r) : r;
        };
    }

    @Test
    void t3ControlOneUlpEveryThousandthOutputIsRejected() throws IOException {
        Pop p = pop("random exponent 0..2046, seed 20261112, 10000");
        CoreMathTestSupport.Diff ds = CoreMathTestSupport.compare(label("sin", "control", "port + 1 ulp every 1000th"), p.xs, p.sinExpected,
                perturbEvery1000th(CoreMathTestSupport.PROVIDER::sin), ALL, false, false);
        CoreMathTestSupport.Diff dc = CoreMathTestSupport.compare(label("cos", "control", "port + 1 ulp every 1000th"), p.xs, p.cosExpected,
                perturbEvery1000th(CoreMathTestSupport.PROVIDER::cos), ALL, false, false);
        System.out.println(new StringBuilder(160).append("control: the comparison rejected ").append(ds.mismatches).append(" of ")
                .append(ds.applicable).append(" (sin) and ").append(dc.mismatches).append(" of ").append(dc.applicable)
                .append(" (cos) inputs with the output moved one ulp on every 1000th call"));
        assertEquals(10L, ds.mismatches, ds.details());
        assertEquals(10L, dc.mismatches, dc.details());
        assertEquals(10_000L, ds.applicable);
        assertEquals(10_000L, dc.applicable);
    }

    // ----------------------------------------------------------------------------------------
    // T5: the port path is taken
    // ----------------------------------------------------------------------------------------

    /** A {@link CrMath} that counts how often each function is called. */
    private static final class CountingDelegate implements CorrectlyRoundedMath {
        private final CrMath real = new CrMath();
        final LongAdder sin = new LongAdder();
        final LongAdder cos = new LongAdder();
        final LongAdder tan = new LongAdder();
        final LongAdder asin = new LongAdder();
        final LongAdder acos = new LongAdder();
        final LongAdder atan = new LongAdder();
        final LongAdder atan2 = new LongAdder();

        @Override
        public double sin(double x) {
            sin.increment();
            return real.sin(x);
        }

        @Override
        public double cos(double x) {
            cos.increment();
            return real.cos(x);
        }

        @Override
        public double tan(double x) {
            tan.increment();
            return real.tan(x);
        }

        @Override
        public double asin(double x) {
            asin.increment();
            return real.asin(x);
        }

        @Override
        public double acos(double x) {
            acos.increment();
            return real.acos(x);
        }

        @Override
        public double atan(double x) {
            atan.increment();
            return real.atan(x);
        }

        @Override
        public double atan2(double y, double x) {
            atan2.increment();
            return real.atan2(y, x);
        }
    }

    private static final String[] T5_POPS = {
        "named", "random exponent 0..2046, seed 20261112, 10000", "random exponent 2^-30..2^33, seed 20261113, 10000"
    };

    /** Runs {@code provider} over the populations; returns how many sin and cos calls it made. */
    private static long runProvider(CorrectlyRoundedMath provider, boolean includeWc, StringBuilder report) throws IOException {
        long calls = 0;
        long mismatches = 0;
        java.util.List<String> keys = new java.util.ArrayList<>(java.util.List.of(T5_POPS));
        if (includeWc) {
            keys.add("sin.wc first 2000");
            keys.add("cos.wc first 2000");
        }
        for (String k : keys) {
            Pop p = pop(k);
            CoreMathTestSupport.Diff ds = CoreMathTestSupport.compare(label("sin", p.name, "provider"), p.xs, p.sinExpected, provider::sin, ALL, false, true);
            CoreMathTestSupport.Diff dc = CoreMathTestSupport.compare(label("cos", p.name, "provider"), p.xs, p.cosExpected, provider::cos, ALL, false, true);
            calls += ds.applicable + dc.applicable;
            mismatches += ds.mismatches + dc.mismatches;
        }
        report.append(calls).append(" sin/cos calls, ").append(mismatches).append(" mismatches");
        assertEquals(0L, mismatches, report.toString());
        return calls;
    }

    @Test
    void t5EveryFiniteInputIsHandledByThePortNeverByTheDelegate() throws IOException {
        if (CoreMathTestSupport.FULL_TIER) {
            CoreMathTestSupport.requireClone("t5 (the .wc entries)");
        }
        boolean wc = CoreMathTestSupport.wcAvailable("sin") && CoreMathTestSupport.wcAvailable("cos");
        CountingDelegate delegate = new CountingDelegate();
        CoreMath provider = new CoreMath(delegate);
        StringBuilder report = new StringBuilder(96);
        long calls = runProvider(provider, wc, report);
        long delegated = delegate.sin.sum() + delegate.cos.sum();
        System.out.println(new StringBuilder(160).append("T5: provider ").append(report).append("; delegate sin/cos calls ")
                .append(delegated).append(" of ").append(calls).append(" (wc included: ").append(wc).append(')'));
        assertEquals(0L, delegated, "the delegate was called for a finite sin/cos input: the port did not take it");

        // control: a provider that delegates everything is right on every input and is rejected by the same assertion
        CountingDelegate delegate2 = new CountingDelegate();
        CorrectlyRoundedMath allDelegating = new CorrectlyRoundedMath() {
            @Override public double sin(double x) { return delegate2.sin(x); }
            @Override public double cos(double x) { return delegate2.cos(x); }
            @Override public double tan(double x) { return delegate2.tan(x); }
            @Override public double asin(double x) { return delegate2.asin(x); }
            @Override public double acos(double x) { return delegate2.acos(x); }
            @Override public double atan(double x) { return delegate2.atan(x); }
            @Override public double atan2(double y, double x) { return delegate2.atan2(y, x); }
        };
        StringBuilder report2 = new StringBuilder(96);
        long calls2 = runProvider(allDelegating, wc, report2);
        long delegated2 = delegate2.sin.sum() + delegate2.cos.sum();
        System.out.println(new StringBuilder(160).append("T5 control (provider that delegates everything): ").append(report2)
                .append("; delegate sin/cos calls ").append(delegated2).append(" of ").append(calls2));
        assertEquals(calls2, delegated2, "the control must delegate every call");
        assertTrue(delegated2 != 0L, "the T5 assertion cannot fail: an all-delegating provider made no delegate calls");
    }

    /**
     * Phase B1 (declared deviation of the phase A text, which said the five unported functions reach the delegate on every
     * call): only tan is still delegated; asin, acos, atan and atan2 are ported, so for these finite in-domain inputs they must
     * NOT reach the delegate (the phase B1 tests prove that over larger populations).
     */
    @Test
    void providerDelegatesTanAndNonFiniteInputButNotThePortedInverseFunctions() {
        CountingDelegate delegate = new CountingDelegate();
        CoreMath provider = new CoreMath(delegate);
        CrMath oracle = CoreMathTestSupport.ORACLE;
        double[] xs = {0.25, -0.5, 0.9, 1.0, -1.0, 0.1};
        for (double x : xs) {
            assertEquals(Double.doubleToRawLongBits(oracle.tan(x)), Double.doubleToRawLongBits(provider.tan(x)));
            assertEquals(Double.doubleToRawLongBits(oracle.asin(x)), Double.doubleToRawLongBits(provider.asin(x)));
            assertEquals(Double.doubleToRawLongBits(oracle.acos(x)), Double.doubleToRawLongBits(provider.acos(x)));
            assertEquals(Double.doubleToRawLongBits(oracle.atan(x)), Double.doubleToRawLongBits(provider.atan(x)));
            assertEquals(Double.doubleToRawLongBits(oracle.atan2(x, 2.0)), Double.doubleToRawLongBits(provider.atan2(x, 2.0)));
        }
        assertEquals(xs.length, delegate.tan.sum());
        assertEquals(0L, delegate.asin.sum());
        assertEquals(0L, delegate.acos.sum());
        assertEquals(0L, delegate.atan.sum());
        assertEquals(0L, delegate.atan2.sum());

        double[] bad = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (double x : bad) {
            assertThrows(IllegalArgumentException.class, () -> provider.sin(x));
            assertThrows(IllegalArgumentException.class, () -> provider.cos(x));
            assertThrows(IllegalArgumentException.class, () -> oracle.sin(x));
            assertThrows(IllegalArgumentException.class, () -> oracle.cos(x));
        }
        assertEquals(bad.length, delegate.sin.sum());
        assertEquals(bad.length, delegate.cos.sum());
    }

    // ----------------------------------------------------------------------------------------
    // T4 wiring
    // ----------------------------------------------------------------------------------------

    @Test
    void factoryReturnsTheCoreMathProviderAndTheCorpusHasSinAndCosRows() throws IOException {
        MathProvider provider = MathProviders.correctlyRounded();
        assertTrue(provider.isConformant());
        assertInstanceOf(CoreMath.class, provider.get());

        String json;
        try (InputStream in = CoreMathSinCosDifferentialTest.class.getResourceAsStream("/conformance/vectors/eval-transcendental.json")) {
            assertTrue(in != null, "the transcendental corpus resource is missing");
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int sinRows = count(json, "\"id\": \"eval-transcendental/sin-");
        int cosRows = count(json, "\"id\": \"eval-transcendental/cos-");
        System.out.println(new StringBuilder(96).append("corpus eval-transcendental.json: ").append(sinRows).append(" sin rows, ")
                .append(cosRows).append(" cos rows (of ").append(count(json, "\"id\": ")).append(" rows)"));
        assertEquals(35, sinRows);
        assertEquals(35, cosRows);
    }

    private static int count(String text, String needle) {
        Matcher m = Pattern.compile(Pattern.quote(needle)).matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
