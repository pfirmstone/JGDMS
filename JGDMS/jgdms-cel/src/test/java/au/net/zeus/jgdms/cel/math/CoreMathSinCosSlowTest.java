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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.function.DoublePredicate;
import java.util.function.DoubleUnaryOperator;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * T2, the slow tier: the CORE-MATH port of {@code sin} and {@code cos} against {@link CrMath} on
 * EVERY finite entry of CORE-MATH's {@code sin.wc} and {@code cos.wc} worst-case files and on
 * 1,000,000 seeded random doubles per function in each of two exponent ranges. Excluded from the
 * default suite ({@code @Tag("slow")}); run it with
 * {@code -Dgroups=slow -Djgdms.cel.excludedGroups=none-such}. The oracle runs on all cores:
 * {@code CrMath} is thread-safe for results (see {@link CoreMathTestSupport}).
 * <p>
 * Predictions are P12 of {@code CoreMathSinCosDifferentialTest}'s javadoc, sealed before the first
 * run: 0 mismatches in every population. Each population is also run through the accurate path
 * alone and the fast path alone (P5), so the accurate path is covered on all of them and not only
 * on the inputs on which the fast path happens to give up. The comparison, the populations and the
 * control are the code of the fast tier ({@link CoreMathTestSupport}), so the control of
 * {@code CoreMathSinCosDifferentialTest} (T3) applies to these checks too.
 */
@Tag("slow")
class CoreMathSinCosSlowTest {

    private static final DoublePredicate SIN_DOMAIN = x -> Math.abs(x) >= 0x1p-26;
    private static final DoublePredicate COS_DOMAIN = x -> Math.abs(x) >= 0x1p-27;
    private static final DoublePredicate ALL = x -> true;

    private static String label(String function, String population, String path) {
        return new StringBuilder(96).append(function).append(", ").append(population).append(", ").append(path).toString();
    }

    private static void check(String function, String population, double[] xs) {
        boolean isSin = function.equals("sin");
        long t0 = System.nanoTime();
        double[] expected = CoreMathTestSupport.oracleValues(xs, isSin ? CoreMathTestSupport.ORACLE::sin : CoreMathTestSupport.ORACLE::cos);
        long t1 = System.nanoTime();
        DoubleUnaryOperator combined = isSin ? CoreMathSin::sin : CoreMathCos::cos;
        DoubleUnaryOperator accurate = isSin ? CoreMathSin::accurate : CoreMathCos::accurate;
        DoubleUnaryOperator fast = isSin ? CoreMathSin::fast : CoreMathCos::fast;
        DoublePredicate domain = isSin ? SIN_DOMAIN : COS_DOMAIN;

        CoreMathTestSupport.Diff dc = CoreMathTestSupport.compare(label(function, population, "provider path"), xs, expected, combined, ALL, false, true);
        CoreMathTestSupport.Diff da = CoreMathTestSupport.compare(label(function, population, "accurate path alone"), xs, expected, accurate, domain, false, true);
        CoreMathTestSupport.Diff df = CoreMathTestSupport.compare(label(function, population, "fast path alone"), xs, expected, fast, domain, true, true);
        long t2 = System.nanoTime();
        System.out.println(dc.line());
        System.out.println(da.line());
        System.out.println(df.line());
        StringBuilder census = new StringBuilder(160);
        census.append("path census {").append(function).append(", ").append(population).append("}: fast path decided ")
                .append(df.applicable).append(" of ").append(df.applicable + df.undecided).append(" in-domain inputs; accurate path needed for ")
                .append(df.undecided).append("; oracle ").append((t1 - t0) / 1_000_000L).append(" ms, ports ")
                .append((t2 - t1) / 1_000_000L).append(" ms");
        System.out.println(census);
        StringBuilder all = new StringBuilder(512);
        all.append(dc.details()).append('\n').append(da.details()).append('\n').append(df.details());
        assertEquals(0L, dc.mismatches + da.mismatches + df.mismatches, all.toString());
        assertEquals(xs.length, dc.applicable, "the provider-path comparison skipped inputs");
    }

    private static void wcCheck(String function) throws IOException {
        Assumptions.assumeTrue(CoreMathTestSupport.wcAvailable(function),
                new StringBuilder(96).append("SKIPPED: no .wc file under ").append(CoreMathTestSupport.CORE_MATH_DIR).toString());
        CoreMathTestSupport.WcData wc = CoreMathTestSupport.readWc(CoreMathTestSupport.wcFile(function), 0);
        System.out.println(new StringBuilder(160).append(function).append(".wc: ").append(wc.totalLines).append(" lines, ")
                .append(wc.commentLines).append(" comment lines, ").append(wc.nonFinite).append(" NaN/Inf lines skipped, ")
                .append(wc.values.length).append(" finite entries compared"));
        assertEquals(wc.totalLines, wc.commentLines + wc.nonFinite + wc.values.length, "every line is a comment, a NaN/Inf line or a value");
        assertTrue(wc.values.length > 1_000_000, "the .wc reader lost entries");
        check(function, new StringBuilder(48).append(function).append(".wc every finite entry").toString(), wc.values);
    }

    @Test
    void sinWorstCasesEveryEntry() throws IOException {
        wcCheck("sin");
    }

    @Test
    void cosWorstCasesEveryEntry() throws IOException {
        wcCheck("cos");
    }

    @Test
    void sinRandomFiniteRangeOneMillion() {
        check("sin", "random exponent 0..2046, seed 20261121, 1000000", CoreMathTestSupport.finiteRangeRandom(20261121L, 1_000_000));
    }

    @Test
    void cosRandomFiniteRangeOneMillion() {
        check("cos", "random exponent 0..2046, seed 20261121, 1000000", CoreMathTestSupport.finiteRangeRandom(20261121L, 1_000_000));
    }

    @Test
    void sinRandomModerateRangeOneMillion() {
        check("sin", "random exponent 2^-30..2^33, seed 20261122, 1000000", CoreMathTestSupport.moderateRangeRandom(20261122L, 1_000_000));
    }

    @Test
    void cosRandomModerateRangeOneMillion() {
        check("cos", "random exponent 2^-30..2^33, seed 20261122, 1000000", CoreMathTestSupport.moderateRangeRandom(20261122L, 1_000_000));
    }
}
