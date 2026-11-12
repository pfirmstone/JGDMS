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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The phase B2 port of CORE-MATH's binary64 {@code tan} ({@link CoreMathTan}) against {@link CrMath} through the PRODUCTION provider
 * ({@code MathProviders.correctlyRounded().get()}), on named inputs, the first 2,000 entries of {@code tan.wc}, and seven seeded random populations of
 * 10,000 inputs; with the accurate path alone and the fast path alone (where it decides) on every population; controls; the delegate census; the
 * special inputs; the hard-case census of the accurate path. Runtime tier: FAST (default suite); the {@code tan.wc} comparison is skipped without the clone and
 * FAILS when the full tier is requested ({@code -Dcore.math.full=true}). The oracle is {@code CrMath}, which is independent of CORE-MATH: a second INSTRUMENT on
 * the same inputs, not a second bed. The independent third witness is the conformance corpus (mpmath), which has only 35 tan rows.
 *
 * <h2>SEALED PREDICTIONS (B2-P1..P15, phase B2; written before the first run of this class; the witness is the git history of this file)</h2>
 * Populations: named = {@link CoreMathTanTestSupport#namedInputs()}; random (seeds 20261321..20261327, 10,000 each): P_full exponent fields 0..2046, P_mod unbiased exponents
 * -28..4, P_mid 5..52, P_large 52..1023, P_lin uniform [0, 8), P_pole52 near multiples of pi/2 with k of at most 52 bits, P_pole1020 the same with at most 1020 bits.
 * Scoring everywhere: {@code Double.doubleToRawLongBits(port) == Double.doubleToRawLongBits(CrMath)}, no tolerance.
 * <ol>
 *   <li><b>B2-P1 named.</b> 0 mismatches on the provider path, the accurate path alone (finite {@code |x| > 0x1.d12ed0af1a27ep-27}) and the fast path alone (where it decides).</li>
 *   <li><b>B2-P2 worst cases.</b> The first 2,000 finite entries of {@code tan.wc}: 0 mismatches on all three paths.</li>
 *   <li><b>B2-P3 random.</b> 0 mismatches on all three paths on each of the seven populations. ELSE-BRANCH: any mismatch is reported, not loosened, and stops the phase.</li>
 *   <li><b>B2-P4 how often the accurate path runs</b> (asserted loosely; the fast path's relative error bound 2^-65.86 against an ulp of 2^-52.5 predicts an undecided
 *       rate near 2^-12.4 = 0.019%): on P_full, P_mod, P_mid, P_large and P_lin the fast path cannot decide at most 10 of the in-domain inputs (of 10,000); on P_pole52
 *       at least 1,500 of 10,000 (the C declines when the reduced argument is below 2^-37 of a turn, which for {@code k} of at most about 16 bits is the rule); on P_pole1020
 *       between 30 and 500. ELSE-BRANCH: a speed finding for Chisel's timing, not a correctness one; the reasoning above is then wrong and READY says where.</li>
 *   <li><b>B2-P5 control.</b> The provider output moved one ulp up on every 1,000th call of 10,000 inputs is rejected by the same comparison: exactly 10 of 10,000, and nothing else.</li>
 *   <li><b>B2-P6 the delegate.</b> With a counting {@code CrMath} installed as the provider's delegate, over the named inputs and the seven populations (about 80,000 finite inputs)
 *       the delegate's {@code tan} is called 0 times and every result equals {@code CrMath}'s; the control (a provider that delegates everything) calls it for every input; NaN,
 *       +Inf and -Inf reach the delegate (3 of 3) and the delegate throws {@code IllegalArgumentException}.</li>
 *   <li><b>B2-P7 symmetry and range.</b> For every named input and every random input, {@code tan(-x)} is the bitwise negation of {@code tan(x)} (0 violations) and the result is finite
 *       (tan has no pole in binary64); for {@code |x|} above the tiny-argument threshold the result is non-zero.</li>
 *   <li><b>B2-P8 zero and tiny.</b> {@code tan(+0.0)} and {@code tan(-0.0)} have the sign bit of the argument; for every named tiny input and 10,000 random inputs in (0, 0x1.d12ed0af1a27ep-27] the provider returns
 *       {@code fma(x, 2^-54, x)} and it equals {@code CrMath}: 0 mismatches.</li>
 *   <li><b>B2-P9 the extremes of tan.wc's comment</b> (tan.wc, last block). The port at {@code x0 = 0x1.6ac5b262ca1ffp+849} returns {@code 0x1.d9ba9a7975636p+60}, at {@code 2*x0} returns
 *       {@code 0x1.14ae72e6ba22fp-60}, both equal to {@code CrMath}; no result of any population exceeds the first in magnitude.</li>
 *   <li><b>B2-P10 the hard cases.</b> The C's 86-ulp test ({@code tan.c:2219}) fires on 0 inputs of the named inputs and the seven populations other than the two exception inputs
 *       of {@code tan_accurate} (and their negations, which are in the named inputs); for those four inputs it fires, and the accurate path returns {@code CrMath}'s value.
 *       ELSE-BRANCH: if it does not fire for an exception input, that table entry is dead for round-to-nearest (a finding for the READY, not a defect).</li>
 *   <li><b>B2-P11 wiring.</b> {@code MathProviders.correctlyRounded()} returns a conformant {@link CoreMath}-backed provider; {@code CorpusRunnerTest} passes with it;
 *       {@code eval-transcendental.json} has 35 tan rows (counted from the file by this test).</li>
 *   <li><b>B2-P12 the tables and constants</b> ({@code CoreMathTanTablesDigestTest}): the digest of the Java tables and the 9 named constants equals the committed digest; a flipped bit
 *       in each of the 22 entries changes it (22 of 22); with the clone, every table equals the C text, every constant equals every place where the C writes it (9 places for 9
 *       constants), 0 mismatches, controls rejected exactly once.</li>
 *   <li><b>B2-P13 committed worst-case goldens</b> ({@code CoreMathTanWorstCaseGoldenTest}): the provider, the accurate path alone and the fast path alone (where it decides) have 0
 *       mismatches on every golden entry; the header carries the MIT text, a 32-hex source md5, the commit and the generator name; the control rejects exactly ceil(N/100).</li>
 *   <li><b>B2-P14 slow tier</b> ({@code CoreMathTanSlowTest}, run once by the producer, clone required, {@code -Dcore.math.full=true}): 0 mismatches over every finite entry of {@code tan.wc}
 *       (counts printed) and over at least 5,000,000 seeded random inputs plus the structured populations, on the provider path, the accurate path alone and the fast path alone;
 *       the hard-case census fires on exactly the inputs of the two exceptions and no others.</li>
 *   <li><b>B2-P15 time.</b> The fast tier of the new classes together runs in under 90 s on this machine (Surefire's report).</li>
 * </ol>
 */
class CoreMathTanDifferentialTest {

    /** One population with the oracle's answers, computed once and shared. */
    private static final class Pop {
        final String name;
        final double[] xs;
        final double[] expected;

        Pop(String name, double[] xs) {
            this.name = name;
            this.xs = xs;
            this.expected = CoreMathTanTestSupport.oracle(xs);
        }
    }

    private static final int N = 10_000;
    private static List<Pop> populations;

    private static synchronized List<Pop> populations() {
        if (populations == null) {
            List<Pop> p = new ArrayList<>();
            p.add(new Pop("named inputs", CoreMathTanTestSupport.namedInputs()));
            p.add(new Pop("P_full exponent fields 0..2046 (20261321)", CoreMathTestSupport.exponentUniform(20261321L, N, 0, 2046)));
            p.add(new Pop("P_mod unbiased exponents -28..4 (20261322)", CoreMathTanTestSupport.binades(20261322L, N, -28, 4)));
            p.add(new Pop("P_mid unbiased exponents 5..52 (20261323)", CoreMathTanTestSupport.binades(20261323L, N, 5, 52)));
            p.add(new Pop("P_large unbiased exponents 52..1023 (20261324)", CoreMathTanTestSupport.binades(20261324L, N, 52, 1023)));
            p.add(new Pop("P_lin uniform [0, 8) (20261325)", CoreMathTanTestSupport.uniformZeroToEight(20261325L, N)));
            p.add(new Pop("P_pole52 near multiples of pi/2, k of at most 52 bits (20261326)", CoreMathTanTestSupport.nearHalfPiMultiples(20261326L, N, 52)));
            p.add(new Pop("P_pole1020 near multiples of pi/2, k of at most 1020 bits (20261327)", CoreMathTanTestSupport.nearHalfPiMultiples(20261327L, N, 1020)));
            populations = p;
        }
        return populations;
    }

    @Test
    void p1NamedInputsAndP3RandomPopulationsAndP4Census() {
        long[] undecided = new long[8];
        int k = 0;
        for (Pop p : populations()) {
            undecided[k++] = CoreMathTanTestSupport.checkUnary(p.name, p.xs, p.expected);
        }
        // B2-P4 (random populations only: indices 1..7)
        for (int i = 1; i <= 5; i++) {
            assertTrue(undecided[i] <= 10, new StringBuilder(80).append("B2-P4: the fast path undecided on ").append(undecided[i]).append(" of ").append(N).append(" in population ").append(i).toString());
        }
        System.out.println(new StringBuilder(120).append("B2-P4 census: fast path undecided on P_pole52 ").append(undecided[6]).append(" of ").append(N).append(", on P_pole1020 ").append(undecided[7]).append(" of ").append(N));
        assertTrue(undecided[6] >= 1500, "B2-P4: P_pole52");
        assertTrue(undecided[7] >= 30 && undecided[7] <= 500, "B2-P4: P_pole1020");
    }

    @Test
    void p2WorstCasesFirst2000() throws IOException {
        CoreMathTestSupport.requireClone(CoreMathTestSupport.CORE_MATH_DIR, CoreMathTestSupport.FULL_TIER, "the first 2,000 tan.wc entries", "tan");
        CoreMathTestSupport.WcData wc = CoreMathTestSupport.readWc(CoreMathTestSupport.wcFile("tan"), 2000);
        assertEquals(2000, wc.values.length);
        double[] expected = CoreMathTanTestSupport.oracle(wc.values);
        CoreMathTanTestSupport.checkUnary("tan.wc first 2,000 finite entries", wc.values, expected);
    }

    @Test
    void p5ControlTheComparisonCanFail() {
        Pop p = populations().get(1);
        CoreMathTestSupport.Diff d = CoreMathTestSupport.compare("tan control, provider moved one ulp up on every 1000th call", p.xs, p.expected,
                CoreMathInverseTrigTestSupport.perturbEvery1000th(CoreMathTestSupport.PROVIDER::tan), x -> true, false, false);
        System.out.println(d.line());
        assertEquals(10L, d.mismatches);
    }

    @Test
    void p6EveryFiniteInputIsHandledByThePortNeverByTheDelegate() {
        CountingDelegate delegate = new CountingDelegate();
        CoreMath provider = new CoreMath(delegate);
        long calls = 0;
        long mismatches = 0;
        for (Pop p : populations()) {
            for (int i = 0; i < p.xs.length; i++) {
                calls++;
                if (Double.doubleToRawLongBits(provider.tan(p.xs[i])) != Double.doubleToRawLongBits(p.expected[i])) {
                    mismatches++;
                }
            }
        }
        System.out.println(new StringBuilder(160).append("T5 tan: provider ").append(calls).append(" finite calls, ").append(mismatches).append(" mismatches; delegate tan calls ").append(delegate.tan.sum()));
        assertEquals(0L, mismatches);
        assertEquals(0L, delegate.tan.sum());
        // control: a provider that delegates everything
        CountingDelegate delegate2 = new CountingDelegate();
        CorrectlyRoundedMath everything = new CorrectlyRoundedMath() {
            @Override public double sin(double x) { return delegate2.sin(x); }
            @Override public double cos(double x) { return delegate2.cos(x); }
            @Override public double tan(double x) { return delegate2.tan(x); }
            @Override public double asin(double x) { return delegate2.asin(x); }
            @Override public double acos(double x) { return delegate2.acos(x); }
            @Override public double atan(double x) { return delegate2.atan(x); }
            @Override public double atan2(double y, double x) { return delegate2.atan2(y, x); }
        };
        long calls2 = 0;
        for (Pop p : populations()) {
            for (double x : p.xs) {
                everything.tan(x);
                calls2++;
            }
        }
        System.out.println(new StringBuilder(120).append("T5 tan control (provider that delegates everything): ").append(calls2).append(" calls, delegate tan calls ").append(delegate2.tan.sum()));
        assertEquals(calls2, delegate2.tan.sum());
        // non-finite arguments reach the delegate, which throws
        double[] bad = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (double x : bad) {
            assertThrows(IllegalArgumentException.class, () -> provider.tan(x));
        }
        assertEquals(bad.length, delegate.tan.sum());
    }

    @Test
    void p7SymmetryRangeAndTheTinyThreshold() {
        long checked = 0;
        long asymmetric = 0;
        long notFinite = 0;
        long zeroAboveThreshold = 0;
        double maxAbs = 0.0;
        for (Pop p : populations()) {
            for (double x : p.xs) {
                double t = CoreMathTestSupport.PROVIDER.tan(x);
                double tn = CoreMathTestSupport.PROVIDER.tan(-x);
                checked++;
                if (Double.doubleToRawLongBits(tn) != (Double.doubleToRawLongBits(t) ^ Long.MIN_VALUE)) {
                    asymmetric++;
                }
                if (!Double.isFinite(t)) {
                    notFinite++;
                }
                if (CoreMathTanTestSupport.PATH_DOMAIN.test(x) && t == 0.0) {
                    zeroAboveThreshold++;
                }
                if (Double.isFinite(t)) {
                    maxAbs = Math.max(maxAbs, Math.abs(t));
                }
            }
        }
        System.out.println(new StringBuilder(160).append("B2-P7: ").append(checked).append(" inputs: asymmetric ").append(asymmetric).append(", not finite ").append(notFinite)
                .append(", zero above the tiny threshold ").append(zeroAboveThreshold).append("; largest |tan| seen ").append(CoreMathTestSupport.hex(maxAbs)));
        assertEquals(0L, asymmetric);
        assertEquals(0L, notFinite);
        assertEquals(0L, zeroAboveThreshold);
        assertTrue(maxAbs <= 0x1.d9ba9a7975636p+60, "B2-P9: a result above the maximum of the C's comment");
    }

    @Test
    void p8ZeroAndTinyInputs() {
        assertEquals(0L, Double.doubleToRawLongBits(CoreMathTestSupport.PROVIDER.tan(0.0)));
        assertEquals(Long.MIN_VALUE, Double.doubleToRawLongBits(CoreMathTestSupport.PROVIDER.tan(-0.0)));
        double tiny = Double.longBitsToDouble(CoreMathTanTables.TINY_BITS);
        double[] xs = new double[10_000 + 2];
        java.util.SplittableRandom r = new java.util.SplittableRandom(20261328L);
        for (int i = 0; i < 10_000; i++) {
            long bits = (r.nextLong() & 0x7fffffffffffffffL) % (CoreMathTanTables.TINY_BITS + 1L);
            double v = Double.longBitsToDouble(bits == 0L ? 1L : bits);
            xs[i] = r.nextBoolean() ? -v : v;
        }
        xs[10_000] = tiny;
        xs[10_001] = -tiny;
        double[] expected = CoreMathTanTestSupport.oracle(xs);
        long mismatches = 0;
        for (int i = 0; i < xs.length; i++) {
            double got = CoreMathTestSupport.PROVIDER.tan(xs[i]);
            if (Double.doubleToRawLongBits(got) != Double.doubleToRawLongBits(expected[i]) || got != Math.fma(xs[i], 0x1p-54, xs[i])) {
                mismatches++;
            }
        }
        System.out.println(new StringBuilder(80).append("B2-P8: ").append(mismatches).append(" mismatches of ").append(xs.length).append(" tiny inputs (|x| <= 0x1.d12ed0af1a27ep-27)"));
        assertEquals(0L, mismatches);
    }

    @Test
    void p9TheExtremesOfTheWorstCaseFileComment() {
        double x0 = 0x1.6ac5b262ca1ffp+849;
        double big = CoreMathTestSupport.PROVIDER.tan(x0);
        double small = CoreMathTestSupport.PROVIDER.tan(2 * x0);
        System.out.println(new StringBuilder(160).append("B2-P9: tan(x0) = ").append(CoreMathTestSupport.hex(big)).append(", tan(2*x0) = ").append(CoreMathTestSupport.hex(small)));
        // the C's comment names the MAXIMAL |tan(x)| and the minimal |tan(x)|: the sign of tan at x0 is not stated (found at the first run: tan(x0) is negative)
        assertEquals(Double.doubleToRawLongBits(0x1.d9ba9a7975636p+60), Double.doubleToRawLongBits(Math.abs(big)));
        assertEquals(Double.doubleToRawLongBits(0x1.14ae72e6ba22fp-60), Double.doubleToRawLongBits(Math.abs(small)));
        assertEquals(Double.doubleToRawLongBits(CoreMathTestSupport.ORACLE.tan(x0)), Double.doubleToRawLongBits(big));
        assertEquals(Double.doubleToRawLongBits(CoreMathTestSupport.ORACLE.tan(2 * x0)), Double.doubleToRawLongBits(small));
    }

    @Test
    void p10TheHardCaseCensusFiresOnlyOnTheTwoExceptions() {
        long inDomain = 0;
        long fired = 0;
        long notException = 0;
        for (Pop p : populations()) {
            long[] c = CoreMathTanTestSupport.ambiguousCensus(p.xs);
            inDomain += c[0];
            fired += c[1];
            notException += c[2];
        }
        System.out.println(new StringBuilder(160).append("B2-P10: the 86-ulp test fired on ").append(fired).append(" of ").append(inDomain).append(" in-domain inputs, ").append(notException).append(" of them not an exception input"));
        assertEquals(0L, notException);
        double[] ex = {CoreMathTanTables.exceptions(0), -CoreMathTanTables.exceptions(0), CoreMathTanTables.exceptions(3), -CoreMathTanTables.exceptions(3)};
        for (double x : ex) {
            boolean[] amb = new boolean[1];
            double got = CoreMathTan.accurateAmbiguous(x, amb);
            System.out.println(new StringBuilder(120).append("exception input ").append(CoreMathTestSupport.hex(x)).append(": 86-ulp test fired ").append(amb[0]).append(", accurate path gives ").append(CoreMathTestSupport.hex(got))
                    .append(", CrMath ").append(CoreMathTestSupport.hex(CoreMathTestSupport.ORACLE.tan(x))));
            assertTrue(amb[0], "B2-P10: the 86-ulp test did not fire for an exception input");
            assertEquals(Double.doubleToRawLongBits(CoreMathTestSupport.ORACLE.tan(x)), Double.doubleToRawLongBits(got));
        }
    }

    @Test
    void p11FactoryReturnsTheCoreMathProviderAndTheCorpusHasThirtyFiveTanRows() throws IOException {
        MathProvider provider = MathProviders.correctlyRounded();
        assertTrue(provider.isConformant());
        assertInstanceOf(CoreMath.class, provider.get());
        String json;
        try (InputStream in = CoreMathTanDifferentialTest.class.getResourceAsStream("/conformance/vectors/eval-transcendental.json")) {
            assertTrue(in != null, "the transcendental corpus resource is missing");
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int tanRows = count(json, "\"id\": \"eval-transcendental/tan-");
        System.out.println(new StringBuilder(100).append("corpus eval-transcendental.json: ").append(tanRows).append(" tan rows (of ").append(count(json, "\"id\": ")).append(" rows)"));
        assertEquals(35, tanRows);
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
