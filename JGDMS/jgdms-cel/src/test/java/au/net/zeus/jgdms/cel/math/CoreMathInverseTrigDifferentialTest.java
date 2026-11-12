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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * STD-011 Stage 2a, phase B1: differential tests of the CORE-MATH ports of binary64 {@code atan}, {@code asin},
 * {@code acos} and {@code atan2} ({@link CoreMath}, {@link CoreMathAtan}, {@link CoreMathAsin}, {@link CoreMathAcos},
 * {@link CoreMathAtan2}, {@link CoreMathTint}) against {@link CrMath}, through the production provider
 * ({@code MathProviders.correctlyRounded().get()}). Runtime tier: FAST (the default suite); the slow tier is
 * {@code CoreMathInverseTrigSlowTest}.
 *
 * <h2>SEALED PREDICTIONS (phase B1; written and committed before any test of this phase was run)</h2>
 * <b>Pinned statistic, common to all.</b> The mismatch count: the number of inputs for which
 * {@code Double.doubleToRawLongBits(port(input))} differs from {@code Double.doubleToRawLongBits(CrMath(input))}, no
 * tolerance, the oracle {@code CrMath} being this tree's (not modified by this phase). Each denominator is the one the test
 * prints in its {@code port vs CrMath at {...}} line. <b>Scope of every agreement claim:</b> the port and {@code CrMath}
 * computed the same inputs; they are two independent computations (CORE-MATH's algorithm against BigDecimal Ziv), a second
 * instrument on the same inputs, not a second bed. <b>Anti-vacuous clause:</b> a run is INCOMPLETE, not a pass, when a class
 * reports "Tests run: 0", when a skipped test is one of the predicted ones (a clone-dependent test skipped without the
 * clone counts as not run), when a printed denominator is 0, or when a control rejects fewer inputs than predicted.
 * <b>Else-branch common to every "0 mismatches" prediction:</b> a mismatch is either a defect of the port or a defect of
 * the oracle; it is reported with the input in hex and both outputs, the comparison is NOT loosened, the primary reading
 * stays "the port is not yet correct", and the booking of the cause is deferred upward.
 * <ol>
 *   <li><b>B1-P1 named inputs</b> ({@link CoreMathInverseTrigTestSupport#namedInputs}, {@link CoreMathInverseTrigTestSupport#namedPairs}:
 *       zeros, subnormals, the C thresholds and neighbours, the exceptional inputs of the C tables, table nodes,
 *       1 and its neighbours; for atan2 all sign and order combinations of 31 magnitudes plus ratios on the nodes of
 *       T2 and powers of two): atan, asin, acos and atan2 each 0 mismatches of n, n printed, on the provider path, the accurate path
 *       alone (in the path's domain) and the fast path alone (where it decides).</li>
 *   <li><b>B1-P2 the first 2,000 finite entries of {@code atan.wc}, {@code asin.wc}, {@code acos.wc} (in the provider's
 *       domain) and the first 2,000 finite pairs of {@code atan2.wc}</b>: 0 mismatches of 2,000 each. The {@code .wc} files are
 *       hard-to-round worst cases, so this is where a wrong port is most likely to show. (Skipped without the clone.)</li>
 *   <li><b>B1-P3 seeded random populations, 10,000 inputs each:</b> atan with the exponent field uniform over 0..2046
 *       (seed 20261201) and with |x| in 2^-30 .. 2^58 (seed 20261202); asin and acos each with x uniform in [-1,1)
 *       (seed 20261203), with exponent 2^-30 .. 2^-1 (seed 20261204) and with |x| = 1 - k*2^-53 (seed 20261205); atan2 with
 *       exponent fields within 60 of each other (seed 20261206), independent exponent fields over 0..2046 (seed 20261207) and
 *       independent exponent fields over 0..80, the subnormal and tiny range (seed 20261208): 0 mismatches of 10,000 on each
 *       path (provider; accurate alone; fast alone where it decides).</li>
 *   <li><b>B1-P4 each path alone.</b> Part of P1-P3: the accurate path called directly on every in-domain input: 0
 *       mismatches; the fast path alone wherever it returns a result (not NaN): 0 mismatches. ELSE-BRANCH: a mismatch of the
 *       accurate path alone means the accurate-path port is wrong even if the combined path passes.</li>
 *   <li><b>B1-P5 control.</b> A candidate that is the provider with its output moved one ulp up on every 1,000th call (calls
 *       0, 1000, ..., 9000) is run through the same comparison over 10,000 inputs: it is rejected exactly 10 of 10,000, for each of
 *       atan, asin, acos and atan2, and no other input is. ELSE-BRANCH: fewer than 10 means the comparison cannot fail and every
 *       "0 mismatches" of this phase is void.</li>
 *   <li><b>B1-P6 port path.</b> With a counting {@code CrMath} installed as the provider's delegate, over every population of
 *       P1-P3: 0 delegate calls to atan, asin, acos or atan2; a provider that delegates everything, under the same
 *       counters, makes as many delegate calls as inputs and is rejected by the same assertion; tan reaches the delegate on every
 *       call; NaN, +Inf and -Inf reach the delegate for atan, asin, acos and atan2 (as the first argument and as the second)
 *       and the delegate throws {@code IllegalArgumentException}; so do {@code |x| > 1} for asin and acos. ELSE-BRANCH: a
 *       delegate call for a finite in-domain input means the port did not take that input: name the class of input.</li>
 *   <li><b>B1-P7 the atan2 correct-rounding failure branch.</b> Over every population of P1-P3 there are 0 correct-rounding
 *       failures (a {@code NaN} from the accurate path alone) and the production provider routes 0 calls to the delegate. A
 *       provider whose atan2 port reports a failure on every call (the control) routes every call to the delegate, counts
 *       n of n, and returns the delegate's value. {@code CoreMathTint.toD} returns {@code NaN} for a 192-bit value that the C
 *       would print the "Unexpected worst-case" message for (a hand-made value for each of the two conditions of the C).
 *       ELSE-BRANCH: a non-zero count in a population is a finding (report the pair), not a defect: the provider still answers
 *       correctly through the delegate.</li>
 *   <li><b>B1-P8 special inputs.</b> The 49 pairs formed from {@code {+0, -0, +1, -1, +MIN_VALUE, -MIN_VALUE, +MAX_VALUE,
 *       -MAX_VALUE}} ... (all 64 ordered pairs of those eight values) through the provider equal {@code CrMath} and, for the 28 rows where
 *       an argument is a zero, equal {@code StrictMath.atan2} (whose specification lists those rows exactly: a second instrument, on those rows
 *       only); asin and acos at +-0, +-1, +-MIN_VALUE, +-MIN_NORMAL and the neighbours of +-1 equal {@code CrMath}; the
 *       ported infinity rows of {@code CoreMathAtan2.special} equal {@code StrictMath.atan2} (the specification's
 *       "closest to pi/4, 3*pi/4, pi/2" rows).</li>
 *   <li><b>B1-P9 how often the accurate path runs</b> (reported, not asserted): of each 10,000-input random population of P3 the
 *       fast path cannot decide at most 3% for asin and acos, at most 6% for atan on its moderate range, and for atan2 the accurate
 *       (192-bit) path is needed for at most 5% of the near-exponent pairs and for at least 90% of the independent-exponent pairs
 *       (the C sends every pair whose exponent fields differ by 53 binades or more to it, about 95% of independent pairs).
 *       ELSE-BRANCH: more than the bound is a speed finding for Chisel's timing, not a correctness one.</li>
 *   <li><b>B1-P10 the tables</b> ({@code CoreMathInverseTrigTablesDigestTest}, {@code CoreMathInverseTrigConstantsTest}): the digest of the Java
 *       tables equals the committed digest; a flipped bit in each of the 26 tables changes it (26 of 26); a write into the
 *       snapshot is not visible in the tables; with the clone, every table equals the C text of every file that carries it, 0 mismatches
 *       over all values (the count printed), and a control with one flipped bit is rejected.</li>
 *   <li><b>B1-P11 the 192-bit arithmetic</b> ({@code CoreMathTintTest}, BigInteger models written from the C text): multiply, add, the
 *       shifts for every count 0..200, fromD and toD: 0 mismatches; the dropped-carry mutant of multiply (the carry word {@code cm}
 *       never added) is rejected on exactly the inputs where the model's {@code cm} is non-zero, and the other mutants are rejected
 *       on at least one input; the division's relative error is below 2^-185.53 on every input and the mutant without the
 *       Karp-Markstein correction is rejected on at least 99% of them.</li>
 *   <li><b>B1-P12 committed worst-case goldens</b> ({@code CoreMathInverseTrigWorstCaseGoldenTest}): the provider, the accurate path alone and
 *       the fast path alone (where it decides) have 0 mismatches on every golden entry of the four functions; each header carries the
 *       MIT text, a 32-hex source md5, the source commit and the generator name; the control (expected bits moved on every 100th entry)
 *       rejects exactly ceil(N/100) per function.</li>
 *   <li><b>B1-P13 slow tier</b> ({@code CoreMathInverseTrigSlowTest}, run once by the producer, clone required, {@code -Dcore.math.full=true}): 0
 *       mismatches over every finite in-domain entry of the four {@code .wc} files (counts printed) and over 6,000,000 atan inputs
 *       (1,000,000 over the full finite range, 5,000,000 over 2^-30..2^58), 3,000,000 asin and 3,000,000 acos inputs (1,000,000
 *       each of the three populations of P3), and 6,500,000 atan2 pairs (5,000,000 near, 1,000,000 independent, 500,000 tiny),
 *       on the provider path, the accurate path alone and the fast path alone; 0 correct-rounding failures in all of them.</li>
 *   <li><b>B1-P14 time.</b> The fast tier of the new classes together runs in under 30 s on this machine (Surefire's report).</li>
 *   <li><b>B1-P15 wiring.</b> {@code MathProviders.correctlyRounded()} returns a conformant {@link CoreMath}-backed provider; the conformance corpus
 *       ({@code CorpusRunnerTest}) passes with it; {@code eval-transcendental.json} has 30 atan rows, 23 atan2 rows, 15 asin rows
 *       and 15 acos rows (counted from the file by this test).</li>
 * </ol>
 */
class CoreMathInverseTrigDifferentialTest {

    /** One unary population with the oracle's answers, computed once and shared. */
    private static final class Pop {
        final String name;
        final double[] xs;
        final double[] expected;

        Pop(String function, String name, double[] xs) {
            this.name = name;
            this.xs = xs;
            this.expected = CoreMathTestSupport.oracleValues(xs, CoreMathInverseTrigTestSupport.oracle(function));
        }
    }

    /** One pair population with the oracle's answers. */
    private static final class PairPop {
        final String name;
        final double[] ys;
        final double[] xs;
        final double[] expected;

        PairPop(String name, CoreMathInverseTrigTestSupport.Pairs p) {
            this.name = name;
            this.ys = p.ys;
            this.xs = p.xs;
            this.expected = CoreMathInverseTrigTestSupport.oracleValues2(p.ys, p.xs, CoreMathTestSupport.ORACLE::atan2);
        }
    }

    private static final Map<String, Pop> POPS = new HashMap<>();
    private static final Map<String, PairPop> PAIR_POPS = new HashMap<>();

    private static final String POP_NAMED = "named";
    private static final String POP_WC = "wc first 2000";

    /** The random populations of P3 per function, in the order they are run. */
    private static final String[][] RANDOM = {
        {"atan", "exponent field 0..2046, seed 20261201, 10000"},
        {"atan", "|x| 2^-30..2^58, seed 20261202, 10000"},
        {"asin", "uniform [-1,1), seed 20261203, 10000"},
        {"asin", "exponent 2^-30..2^-1, seed 20261204, 10000"},
        {"asin", "|x| = 1 - k*2^-53, seed 20261205, 10000"},
        {"acos", "uniform [-1,1), seed 20261203, 10000"},
        {"acos", "exponent 2^-30..2^-1, seed 20261204, 10000"},
        {"acos", "|x| = 1 - k*2^-53, seed 20261205, 10000"},
    };

    private static final String[] PAIR_RANDOM = {
        "exponent fields within 60, seed 20261206, 10000",
        "independent exponent fields 0..2046, seed 20261207, 10000",
        "independent exponent fields 0..80, seed 20261208, 10000",
    };

    private static double[] unaryInputs(String function, String name) throws IOException {
        if (name.equals(POP_NAMED)) {
            return CoreMathInverseTrigTestSupport.namedInputs(function);
        }
        if (name.equals(POP_WC)) {
            return CoreMathInverseTrigTestSupport.readWcInDomain(function, 2000).values;
        }
        if (name.startsWith("exponent field 0..2046")) {
            return CoreMathTestSupport.finiteRangeRandom(20261201L, 10_000);
        }
        if (name.startsWith("|x| 2^-30..2^58")) {
            return CoreMathInverseTrigTestSupport.binadeRange(20261202L, 10_000, -30, 58);
        }
        if (name.startsWith("uniform")) {
            return CoreMathInverseTrigTestSupport.linearUniform(20261203L, 10_000);
        }
        if (name.startsWith("exponent 2^-30")) {
            return CoreMathInverseTrigTestSupport.binadeRange(20261204L, 10_000, -30, -1);
        }
        if (name.startsWith("|x| = 1")) {
            return CoreMathInverseTrigTestSupport.nearOne(20261205L, 10_000);
        }
        throw new IllegalArgumentException(name);
    }

    private static synchronized Pop pop(String function, String name) throws IOException {
        String key = new StringBuilder(96).append(function).append('|').append(name).toString();
        Pop p = POPS.get(key);
        if (p == null) {
            p = new Pop(function, name, unaryInputs(function, name));
            POPS.put(key, p);
        }
        return p;
    }

    private static synchronized PairPop pairPop(String name) throws IOException {
        PairPop p = PAIR_POPS.get(name);
        if (p == null) {
            CoreMathInverseTrigTestSupport.Pairs pairs;
            if (name.equals(POP_NAMED)) {
                pairs = CoreMathInverseTrigTestSupport.namedPairs();
            } else if (name.equals(POP_WC)) {
                CoreMathInverseTrigTestSupport.WcPairs w = CoreMathInverseTrigTestSupport.readWcPairs(CoreMathTestSupport.wcFile("atan2"), 2000);
                pairs = new CoreMathInverseTrigTestSupport.Pairs(w.ys, w.xs);
            } else if (name.startsWith("exponent fields within 60")) {
                pairs = CoreMathInverseTrigTestSupport.pairsNear(20261206L, 10_000);
            } else if (name.startsWith("independent exponent fields 0..2046")) {
                pairs = CoreMathInverseTrigTestSupport.pairsIndependent(20261207L, 10_000, 0, 2046);
            } else if (name.startsWith("independent exponent fields 0..80")) {
                pairs = CoreMathInverseTrigTestSupport.pairsIndependent(20261208L, 10_000, 0, 80);
            } else {
                throw new IllegalArgumentException(name);
            }
            p = new PairPop(name, pairs);
            PAIR_POPS.put(name, p);
        }
        return p;
    }

    // ----------------------------------------------------------------------------------------
    // P1-P4: the fast tier
    // ----------------------------------------------------------------------------------------

    @Test
    void p1NamedInputs() throws IOException {
        for (String f : new String[] {"atan", "asin", "acos"}) {
            Pop p = pop(f, POP_NAMED);
            CoreMathInverseTrigTestSupport.checkUnary(f, p.name, p.xs, p.expected);
        }
        PairPop q = pairPop(POP_NAMED);
        CoreMathInverseTrigTestSupport.checkPairs(q.name, q.ys, q.xs, q.expected);
    }

    @Test
    void p2WorstCasesFirst2000() throws IOException {
        CoreMathTestSupport.requireClone(CoreMathTestSupport.CORE_MATH_DIR, CoreMathTestSupport.FULL_TIER, "p2WorstCasesFirst2000", "atan", "asin", "acos", "atan2");
        for (String f : new String[] {"atan", "asin", "acos"}) {
            Pop p = pop(f, POP_WC);
            assertEquals(2000, p.xs.length, new StringBuilder(64).append(f).append(".wc has fewer than 2000 in-domain finite entries").toString());
            CoreMathInverseTrigTestSupport.checkUnary(f, new StringBuilder(32).append(f).append(".wc first 2000").toString(), p.xs, p.expected);
        }
        PairPop q = pairPop(POP_WC);
        assertEquals(2000, q.ys.length);
        CoreMathInverseTrigTestSupport.checkPairs("atan2.wc first 2000", q.ys, q.xs, q.expected);
    }

    @Test
    void p3RandomPopulations() throws IOException {
        for (String[] r : RANDOM) {
            Pop p = pop(r[0], r[1]);
            CoreMathInverseTrigTestSupport.checkUnary(r[0], p.name, p.xs, p.expected);
        }
        for (String name : PAIR_RANDOM) {
            PairPop q = pairPop(name);
            CoreMathInverseTrigTestSupport.checkPairs(q.name, q.ys, q.xs, q.expected);
        }
    }

    // ----------------------------------------------------------------------------------------
    // P5: the control
    // ----------------------------------------------------------------------------------------

    @Test
    void p5ControlOneUlpEveryThousandthOutputIsRejected() throws IOException {
        long[] rejected = new long[4];
        String[] fs = {"atan", "asin", "acos"};
        for (int k = 0; k < 3; k++) {
            Pop p = pop(fs[k], RANDOM[k == 0 ? 0 : (k == 1 ? 2 : 5)][1]);
            CoreMathTestSupport.Diff d = CoreMathTestSupport.compare(new StringBuilder(64).append(fs[k]).append(", control, port + 1 ulp every 1000th").toString(),
                    p.xs, p.expected, CoreMathInverseTrigTestSupport.perturbEvery1000th(CoreMathInverseTrigTestSupport.provider(fs[k])),
                    x -> true, false, false);
            rejected[k] = d.mismatches;
            assertEquals(10L, d.mismatches, d.details());
            assertEquals(10_000L, d.applicable);
        }
        PairPop q = pairPop(PAIR_RANDOM[0]);
        CoreMathTestSupport.Diff d = CoreMathTestSupport.compare2("atan2, control, port + 1 ulp every 1000th", q.ys, q.xs, q.expected,
                CoreMathInverseTrigTestSupport.perturbEvery1000th((DoubleBinaryOperator) CoreMathTestSupport.PROVIDER::atan2), (y, x) -> true, false, false);
        rejected[3] = d.mismatches;
        assertEquals(10L, d.mismatches, d.details());
        assertEquals(10_000L, d.applicable);
        System.out.println(new StringBuilder(160).append("control: the comparison rejected ").append(rejected[0]).append(" of 10000 (atan), ").append(rejected[1])
                .append(" (asin), ").append(rejected[2]).append(" (acos), ").append(rejected[3]).append(" (atan2) with the output moved one ulp on every 1000th call"));
    }

    // ----------------------------------------------------------------------------------------
    // P6: the port path is taken
    // ----------------------------------------------------------------------------------------

    /** Runs {@code provider} over the unary and pair populations of P1 and P3; returns the number of calls it made. */
    private static long runAll(CorrectlyRoundedMath provider, StringBuilder report) throws IOException {
        long calls = 0;
        long mismatches = 0;
        for (String f : new String[] {"atan", "asin", "acos"}) {
            DoubleUnaryOperator op = f.equals("atan") ? provider::atan : (f.equals("asin") ? provider::asin : provider::acos);
            java.util.List<String> names = new java.util.ArrayList<>();
            names.add(POP_NAMED);
            for (String[] r : RANDOM) {
                if (r[0].equals(f)) {
                    names.add(r[1]);
                }
            }
            for (String n : names) {
                Pop p = pop(f, n);
                CoreMathTestSupport.Diff d = CoreMathTestSupport.compare(new StringBuilder(64).append(f).append(", ").append(n).append(", provider").toString(),
                        p.xs, p.expected, op, x -> true, false, true);
                calls += d.applicable;
                mismatches += d.mismatches;
            }
        }
        java.util.List<String> pnames = new java.util.ArrayList<>();
        pnames.add(POP_NAMED);
        pnames.addAll(java.util.List.of(PAIR_RANDOM));
        for (String n : pnames) {
            PairPop q = pairPop(n);
            CoreMathTestSupport.Diff d = CoreMathTestSupport.compare2(new StringBuilder(64).append("atan2, ").append(n).append(", provider").toString(),
                    q.ys, q.xs, q.expected, provider::atan2, (y, x) -> true, false, true);
            calls += d.applicable;
            mismatches += d.mismatches;
        }
        report.append(calls).append(" asin/acos/atan/atan2 calls, ").append(mismatches).append(" mismatches");
        assertEquals(0L, mismatches, report.toString());
        return calls;
    }

    @Test
    void p6EveryFiniteInDomainInputIsHandledByThePortNeverByTheDelegate() throws IOException {
        CountingDelegate delegate = new CountingDelegate();
        CoreMath provider = new CoreMath(delegate);
        StringBuilder report = new StringBuilder(96);
        long calls = runAll(provider, report);
        long delegated = delegate.ported();
        System.out.println(new StringBuilder(200).append("T5: provider ").append(report).append("; delegate atan/asin/acos/atan2 calls ").append(delegated)
                .append(" of ").append(calls));
        assertTrue(calls > 0L, "no calls were made");
        assertEquals(0L, delegated, "the delegate was called for a finite in-domain input: the port did not take it");

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
        long calls2 = runAll(allDelegating, report2);
        long delegated2 = delegate2.ported();
        System.out.println(new StringBuilder(200).append("T5 control (provider that delegates everything): ").append(report2)
                .append("; delegate calls ").append(delegated2).append(" of ").append(calls2));
        assertEquals(calls2, delegated2, "the control must delegate every call");
        assertTrue(delegated2 != 0L, "the P6 assertion cannot fail: an all-delegating provider made no delegate calls");
    }

    @Test
    void p6TanAndOutOfDomainInputsReachTheDelegate() {
        CountingDelegate delegate = new CountingDelegate();
        CoreMath provider = new CoreMath(delegate);
        CrMath oracle = CoreMathTestSupport.ORACLE;
        double[] xs = {0.25, -0.5, 0.9, 1.0, -1.0, 0.1, 3.0, 1e10};
        for (double x : xs) {
            assertEquals(Double.doubleToRawLongBits(oracle.tan(x)), Double.doubleToRawLongBits(provider.tan(x)));
        }
        // phase B2 (declared deviation of the sealed B1-P6 text, which said tan reached the delegate on all 8 calls): tan is ported, so a finite
        // argument must not reach the delegate; a non-finite one still does
        assertEquals(0L, delegate.tan.sum(), "phase B2: tan is ported, a finite argument must not reach the delegate");

        double[] bad = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (double x : bad) {
            assertThrows(IllegalArgumentException.class, () -> provider.atan(x));
            assertThrows(IllegalArgumentException.class, () -> provider.asin(x));
            assertThrows(IllegalArgumentException.class, () -> provider.acos(x));
            assertThrows(IllegalArgumentException.class, () -> provider.atan2(x, 1.0));
            assertThrows(IllegalArgumentException.class, () -> provider.atan2(1.0, x));
            assertThrows(IllegalArgumentException.class, () -> oracle.atan(x));
        }
        assertEquals(bad.length, delegate.atan.sum());
        assertEquals(bad.length, delegate.asin.sum());
        assertEquals(bad.length, delegate.acos.sum());
        assertEquals(2 * bad.length, delegate.atan2.sum());
        for (double x : bad) {
            assertThrows(IllegalArgumentException.class, () -> provider.tan(x));
        }
        assertEquals(bad.length, delegate.tan.sum());
        for (double x : new double[] {1.0000000000000002, -1.0000000000000002, 2.0, -1e300}) {
            assertThrows(IllegalArgumentException.class, () -> provider.asin(x));
            assertThrows(IllegalArgumentException.class, () -> provider.acos(x));
        }
        assertEquals(bad.length + 4, delegate.asin.sum());
        assertEquals(bad.length + 4, delegate.acos.sum());
    }

    // ----------------------------------------------------------------------------------------
    // P7: the atan2 correct-rounding failure branch
    // ----------------------------------------------------------------------------------------

    @Test
    void p7AFailureOfTheAtan2PortIsCountedAndAnsweredByTheDelegate() {
        CountingDelegate delegate = new CountingDelegate();
        CoreMath alwaysFailing = new CoreMath(delegate, (y, x) -> Double.NaN);
        double[][] pairs = {{1.0, 2.0}, {-3.0, 0.5}, {1e-300, 1e300}, {-7.0, -7.0}, {0x1p-1074, 1.0}};
        for (double[] p : pairs) {
            double got = alwaysFailing.atan2(p[0], p[1]);
            assertEquals(Double.doubleToRawLongBits(CoreMathTestSupport.ORACLE.atan2(p[0], p[1])), Double.doubleToRawLongBits(got));
        }
        System.out.println(new StringBuilder(120).append("failure routing control: counted ").append(alwaysFailing.atan2CorrectRoundingFailures())
                .append(" of ").append(pairs.length).append(", delegate atan2 calls ").append(delegate.atan2.sum()));
        assertEquals(pairs.length, alwaysFailing.atan2CorrectRoundingFailures());
        assertEquals(pairs.length, delegate.atan2.sum());

        // the production wiring: an ordinary pair is not routed and not counted
        CountingDelegate delegate2 = new CountingDelegate();
        CoreMath production = new CoreMath(delegate2);
        for (double[] p : pairs) {
            production.atan2(p[0], p[1]);
        }
        assertEquals(0L, production.atan2CorrectRoundingFailures());
        assertEquals(0L, delegate2.atan2.sum());
    }

    @Test
    void p7TintToDoubleReturnsNaNWhereTheCPrintsAndExits() {
        // C condition (a): m = 0, the low 10 bits of h zero, l < err
        CoreMathTint.Tint a = new CoreMathTint.Tint();
        a.h = 0x8000000000000000L;
        a.m = 0L;
        a.l = 1L;
        a.ex = 1L;
        a.sgn = 0L;
        assertTrue(Double.isNaN(CoreMathTint.toD(a, 662L)), "condition (a), low 11 bits 0");
        a.h = 0x8000000000000400L;
        assertTrue(Double.isNaN(CoreMathTint.toD(a, 662L)), "condition (a), low 11 bits 0x400");
        a.l = 662L;                                   // l == err: not below it
        assertTrue(!Double.isNaN(CoreMathTint.toD(a, 662L)), "l = err is not a failure");
        // C condition (b): m all ones, the low 10 bits of h ones, ~l < err
        a.h = 0x80000000000007ffL;
        a.m = -1L;
        a.l = -1L;
        assertTrue(Double.isNaN(CoreMathTint.toD(a, 662L)), "condition (b), low 11 bits 0x7ff");
        a.h = 0x80000000000003ffL;
        assertTrue(Double.isNaN(CoreMathTint.toD(a, 662L)), "condition (b), low 11 bits 0x3ff");
        // err = 0 is a conversion of an exact double: never a failure
        a.h = 0x8000000000000000L;
        a.m = 0L;
        a.l = 0L;
        assertTrue(!Double.isNaN(CoreMathTint.toD(a, 0L)));
    }

    // ----------------------------------------------------------------------------------------
    // P8: special inputs
    // ----------------------------------------------------------------------------------------

    @Test
    void p8SpecialInputs() {
        double[] vs = {0.0, -0.0, 1.0, -1.0, Double.MIN_VALUE, -Double.MIN_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE};
        int rows = 0;
        int zeroRows = 0;
        for (double y : vs) {
            for (double x : vs) {
                double got = CoreMathTestSupport.PROVIDER.atan2(y, x);
                double want = CoreMathTestSupport.ORACLE.atan2(y, x);
                assertEquals(Double.doubleToRawLongBits(want), Double.doubleToRawLongBits(got), new StringBuilder(80).append("atan2 y=").append(CoreMathTestSupport.hex(y))
                        .append(" x=").append(CoreMathTestSupport.hex(x)).toString());
                rows++;
                if (y == 0.0 || x == 0.0) {
                    zeroRows++;
                    assertEquals(Double.doubleToRawLongBits(StrictMath.atan2(y, x)), Double.doubleToRawLongBits(got), new StringBuilder(80).append("StrictMath row y=")
                            .append(CoreMathTestSupport.hex(y)).append(" x=").append(CoreMathTestSupport.hex(x)).toString());
                }
            }
        }
        double[] ends = {0.0, -0.0, 1.0, -1.0, Double.MIN_VALUE, -Double.MIN_VALUE, Double.MIN_NORMAL, -Double.MIN_NORMAL, Math.nextDown(1.0), -Math.nextDown(1.0),
            0.5, -0.5, Math.nextUp(0.5), Math.nextDown(0.5)};
        for (double x : ends) {
            assertEquals(Double.doubleToRawLongBits(CoreMathTestSupport.ORACLE.asin(x)), Double.doubleToRawLongBits(CoreMathTestSupport.PROVIDER.asin(x)), new StringBuilder(40).append("asin ").append(CoreMathTestSupport.hex(x)).toString());
            assertEquals(Double.doubleToRawLongBits(CoreMathTestSupport.ORACLE.acos(x)), Double.doubleToRawLongBits(CoreMathTestSupport.PROVIDER.acos(x)), new StringBuilder(40).append("acos ").append(CoreMathTestSupport.hex(x)).toString());
            assertEquals(Double.doubleToRawLongBits(CoreMathTestSupport.ORACLE.atan(x)), Double.doubleToRawLongBits(CoreMathTestSupport.PROVIDER.atan(x)), new StringBuilder(40).append("atan ").append(CoreMathTestSupport.hex(x)).toString());
        }
        // the ported rows for infinities and NaN (the provider delegates them): equal to the specification's, through StrictMath
        double inf = Double.POSITIVE_INFINITY;
        double[] infs = {inf, -inf, 1.0, -1.0, 0.0, -0.0};
        int infRows = 0;
        for (double y : infs) {
            for (double x : infs) {
                if (!Double.isInfinite(y) && !Double.isInfinite(x)) {
                    continue;
                }
                assertEquals(Double.doubleToRawLongBits(StrictMath.atan2(y, x)), Double.doubleToRawLongBits(CoreMathAtan2.special(y, x)), new StringBuilder(80).append("special y=")
                        .append(CoreMathTestSupport.hex(y)).append(" x=").append(CoreMathTestSupport.hex(x)).toString());
                infRows++;
            }
        }
        assertTrue(Double.isNaN(CoreMathAtan2.special(Double.NaN, 1.0)));
        assertTrue(Double.isNaN(CoreMathAtan2.special(1.0, Double.NaN)));
        System.out.println(new StringBuilder(160).append("special inputs: atan2 ").append(rows).append(" ordered pairs of eight values equal CrMath, ").append(zeroRows)
                .append(" zero rows also equal StrictMath.atan2; ").append(infRows).append(" infinity rows of CoreMathAtan2.special equal StrictMath.atan2; asin/acos/atan ")
                .append(ends.length).append(" endpoint and tiny inputs equal CrMath"));
        assertEquals(64, rows);
        assertEquals(28, zeroRows);
    }

    // ----------------------------------------------------------------------------------------
    // P15: wiring
    // ----------------------------------------------------------------------------------------

    @Test
    void p15FactoryReturnsTheCoreMathProviderAndTheCorpusRowCounts() throws IOException {
        MathProvider provider = MathProviders.correctlyRounded();
        assertTrue(provider.isConformant());
        assertInstanceOf(CoreMath.class, provider.get());
        String json;
        try (InputStream in = CoreMathInverseTrigDifferentialTest.class.getResourceAsStream("/conformance/vectors/eval-transcendental.json")) {
            assertTrue(in != null, "the transcendental corpus resource is missing");
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int atanRows = count(json, "\"id\": \"eval-transcendental/atan-");
        int atan2Rows = count(json, "\"id\": \"eval-transcendental/atan2-");
        int asinRows = count(json, "\"id\": \"eval-transcendental/asin-");
        int acosRows = count(json, "\"id\": \"eval-transcendental/acos-");
        System.out.println(new StringBuilder(120).append("corpus eval-transcendental.json: ").append(atanRows).append(" atan rows, ")
                .append(atan2Rows).append(" atan2 rows, ").append(asinRows).append(" asin rows, ").append(acosRows).append(" acos rows (of ").append(count(json, "\"id\": ")).append(" rows)"));
        assertEquals(30, atanRows);
        assertEquals(23, atan2Rows);
        assertEquals(15, asinRows);
        assertEquals(15, acosRows);
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
