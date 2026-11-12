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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiPredicate;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoublePredicate;
import java.util.function.DoubleUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;

/**
 * What the phase B1 tests (atan, asin, acos, atan2) share beyond {@link CoreMathTestSupport}: the domain of each
 * path, the input populations (including random PAIRS for atan2), the readers of the CORE-MATH worst-case files
 * ({@code y,x} lines for atan2), the four-comparison check of one population, the control that moves outputs, and
 * the comparison of the Java tables with the C text. The comparison itself is {@code CoreMathTestSupport.compare}
 * and {@code compare2}, one core, so the controls prove the comparison every real check runs.
 */
final class CoreMathInverseTrigTestSupport {

    private CoreMathInverseTrigTestSupport() {}

    // ----------------------------------------------------------------------------------------
    // Operators and domains (all through the production provider, never the classes directly, for the provider path)
    // ----------------------------------------------------------------------------------------

    static DoubleUnaryOperator provider(String function) {
        switch (function) {
            case "atan":
                return CoreMathTestSupport.PROVIDER::atan;
            case "asin":
                return CoreMathTestSupport.PROVIDER::asin;
            case "acos":
                return CoreMathTestSupport.PROVIDER::acos;
            default:
                throw new IllegalArgumentException(function);
        }
    }

    static DoubleUnaryOperator accurate(String function) {
        switch (function) {
            case "atan":
                return CoreMathAtan::accurate;
            case "asin":
                return CoreMathAsin::accurate;
            case "acos":
                return CoreMathAcos::accurate;
            default:
                throw new IllegalArgumentException(function);
        }
    }

    static DoubleUnaryOperator fast(String function) {
        switch (function) {
            case "atan":
                return CoreMathAtan::fast;
            case "asin":
                return CoreMathAsin::fast;
            case "acos":
                return CoreMathAcos::fast;
            default:
                throw new IllegalArgumentException(function);
        }
    }

    static DoubleUnaryOperator oracle(String function) {
        switch (function) {
            case "atan":
                return CoreMathTestSupport.ORACLE::atan;
            case "asin":
                return CoreMathTestSupport.ORACLE::asin;
            case "acos":
                return CoreMathTestSupport.ORACLE::acos;
            default:
                throw new IllegalArgumentException(function);
        }
    }

    /**
     * The domain on which the accurate path alone and the fast path alone are defined by the C: atan
     * {@code 2^-27 <= |x| < 0x1.d02967c31cdb5p+53} (below, {@code fma}; above, +-pi/2); asin
     * {@code 0x1.7137449123ef6p-26 <= |x| < 1}; acos {@code 0x1.cb3b3869747f4p-55 < |x| < 1}
     * (the C: the accurate path is never called for |x| at most that value).
     */
    static DoublePredicate pathDomain(String function) {
        switch (function) {
            case "atan":
                return x -> Math.abs(x) >= 0x1p-27 && Math.abs(x) < 0x1.d02967c31cdb5p+53;
            case "asin":
                return x -> Math.abs(x) >= 0x1.7137449123ef6p-26 && Math.abs(x) < 1.0;
            case "acos":
                return x -> Math.abs(x) > 0x1.cb3b3869747f4p-55 && Math.abs(x) < 1.0;
            default:
                throw new IllegalArgumentException(function);
        }
    }

    /** What the provider accepts: any finite input for atan, {@code |x| <= 1} for asin and acos. */
    static DoublePredicate providerDomain(String function) {
        return function.equals("atan") ? x -> Double.isFinite(x) : x -> Math.abs(x) <= 1.0;
    }

    // ----------------------------------------------------------------------------------------
    // Populations
    // ----------------------------------------------------------------------------------------

    /** {@code n} doubles uniform in [-1, 1): the dominant input of asin and acos in practice. */
    static double[] linearUniform(long seed, int n) {
        SplittableRandom r = new SplittableRandom(seed);
        double[] xs = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = (double) (r.nextLong() >> 10) * 0x1p-53;            // in [-1, 1)
        }
        return xs;
    }

    /**
     * {@code n} doubles with {@code |x| = 1 - k*2^-53}, {@code k} between 1 and 2^e with {@code e} uniform in 1..52,
     * both signs: where {@code 1 - |x|} is small and the arcsine and arc cosine reduce through {@code sqrt((1-|x|)/2)}.
     */
    static double[] nearOne(long seed, int n) {
        SplittableRandom r = new SplittableRandom(seed);
        double[] xs = new double[n];
        for (int i = 0; i < n; i++) {
            int e = 1 + r.nextInt(52);
            long k = 1 + (r.nextLong() & ((1L << e) - 1));
            double v = 1.0 - (double) k * 0x1p-53;
            xs[i] = r.nextBoolean() ? -v : v;
        }
        return xs;
    }

    /** Random doubles with the exponent uniform in [{@code eLo}, {@code eHi}] as unbiased binary exponents, both signs. */
    static double[] binadeRange(long seed, int n, int eLo, int eHi) {
        return CoreMathTestSupport.exponentUniform(seed, n, 0x3ff + eLo, 0x3ff + eHi);
    }

    /** A random-pair population: the y values and the x values. */
    static final class Pairs {
        final double[] ys;
        final double[] xs;

        Pairs(double[] ys, double[] xs) {
            this.ys = ys;
            this.xs = xs;
        }
    }

    /**
     * Random pairs whose exponent fields differ by at most 60 (so most of them go through the fast path of atan2 and
     * the rest through the accurate path), base exponent field uniform in [1, 2046], both signs, random fractions.
     * Pairs whose y exponent would leave [1, 2046] are redrawn.
     */
    static Pairs pairsNear(long seed, int n) {
        SplittableRandom r = new SplittableRandom(seed);
        double[] ys = new double[n];
        double[] xs = new double[n];
        for (int i = 0; i < n; i++) {
            int ex = 1 + r.nextInt(2046);
            int ey;
            do {
                ey = ex - 60 + r.nextInt(121);
            } while (ey < 1 || ey > 2046);
            ys[i] = Double.longBitsToDouble((r.nextBoolean() ? Long.MIN_VALUE : 0L) | ((long) ey << 52) | (r.nextLong() & 0xfffffffffffffL));
            xs[i] = Double.longBitsToDouble((r.nextBoolean() ? Long.MIN_VALUE : 0L) | ((long) ex << 52) | (r.nextLong() & 0xfffffffffffffL));
        }
        return new Pairs(ys, xs);
    }

    /** Random pairs whose exponent fields are independent and uniform in [{@code eLo}, {@code eHi}] (0 is the subnormals). */
    static Pairs pairsIndependent(long seed, int n, int eLo, int eHi) {
        double[] ys = CoreMathTestSupport.exponentUniform(seed, n, eLo, eHi);
        double[] xs = CoreMathTestSupport.exponentUniform(seed + 1, n, eLo, eHi);
        return new Pairs(ys, xs);
    }

    /** The named edge inputs of one unary function, both signs, de-duplicated by bit pattern, finite and in the provider's domain. */
    static double[] namedInputs(String function) {
        List<Double> v = new ArrayList<>();
        List<Double> base = new ArrayList<>(List.of(
                0.0, Double.MIN_VALUE, 3 * Double.MIN_VALUE, Double.MIN_NORMAL, Math.nextDown(Double.MIN_NORMAL), 0x1p-60, 0x1p-30,
                0x1p-27, 0x1p-26, 0x1p-20, 0x1p-16, 0x1p-15, 0x1p-14, 1e-10, 1e-5, 0.01, 0.1, 0.25, 0.5, 0.75, 0.9, 0.99, 1.0,
                Math.nextDown(1.0), Math.nextDown(Math.nextDown(1.0)), 1.0 - 0x1p-30, 1.0 - 0x1p-40, 1.0 - 0x1p-50,
                // thresholds of the C code
                0x1.7137449123ef6p-26, 0x1.7137449123e7bp-26, 0x1.cb3b3869747f4p-55, 0x1.b21c475e6362ap-8, 0x1.2ded8e34a9035p+7,
                0x1.d02967c31cdb5p+53,
                // the five hard-coded cases of acos.c and the inputs it names
                0x1.ffffffffffdc0p-1, 0x1.53ea6c7255e88p-4, 0x1.fd737be914578p-11, 0x1.fffffffffff70p-1, 0x1.390e6939cd1a6p-5,
                0x1.3f47056fc030ap-1, 0x1.e4bf4521f41acp-2, 0x1.c373ff4aad79bp-14, 0x1.da4e0e6c717a5p-2, 0x1.7cb54339263fbp-12,
                0x1.3e827a2cd6d51p-1, 0x1.fda6fee396f8p-2));
        if (function.equals("atan")) {
            base.addAll(List.of(2.0, 3.0, 10.0, 100.0, 1000.0, 1e10, 1e15, 0x1p53, 0x1p54, 1e22, 1e100, 1e300, 0x1p1000, Double.MAX_VALUE,
                    Math.tan(Math.PI / 4), Math.tan(Math.PI / 8), Math.tan(Math.PI / 16), 0x1.45eed6854ce99p+6, 0x1.45e2455e4aaa7p+5));
        }
        // the exceptional inputs the ports carry in their own tables are exactly the inputs most likely to be wrong
        if (function.equals("atan")) {
            for (int j = 0; j < CoreMathInverseTrigTables.atanDbRows(); j++) {
                base.add(CoreMathInverseTrigTables.atanDb(3 * j));
            }
        }
        if (function.equals("asin") || function.equals("acos")) {
            for (int j = 0; j < CoreMathInverseTrigTables.asinDbRows(); j++) {
                base.add(CoreMathInverseTrigTables.asinDb(3 * j));
            }
        }
        // table nodes: tan(pi/256*j) neighbours (atan's reduction), and j/128 thresholds of the asin/acos polynomial index
        if (function.equals("atan")) {
            for (int j = 1; j < 128; j++) {
                base.add(CoreMathInverseTrigTables.atanA(2 * j));
            }
        } else {
            for (int j = 1; j < 128; j += 7) {
                base.add(Math.sqrt(j / 128.0));
            }
        }
        for (double b : base) {
            v.add(b);
            v.add(-b);
            v.add(Math.nextUp(b));
            v.add(Math.nextDown(b));
            v.add(-Math.nextUp(b));
            v.add(-Math.nextDown(b));
        }
        DoublePredicate ok = providerDomain(function);
        List<Double> out = new ArrayList<>();
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        for (double d : v) {
            if (Double.isFinite(d) && ok.test(d) && seen.add(Double.doubleToRawLongBits(d))) {
                out.add(d);
            }
        }
        double[] xs = new double[out.size()];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = out.get(i);
        }
        return xs;
    }

    /**
     * Named pairs for atan2: every combination (both orders, all four sign choices) of a list of magnitudes spanning
     * the subnormals, the thresholds of the code (2^-915, 2^-920, 2^1022, the 53-binade gap), 1 and its neighbours,
     * the largest values; ratios that sit on the table nodes {@code T2[jt]}; and powers-of-two ratios.
     */
    static Pairs namedPairs() {
        List<Double> mags = new ArrayList<>(List.of(
                Double.MIN_VALUE, 3 * Double.MIN_VALUE, 0x1p-1070, Double.MIN_NORMAL, Math.nextDown(Double.MIN_NORMAL), 0x1p-1000, 0x1p-969, 0x1p-921, 0x1p-920,
                0x1p-915, 0x1p-500, 1e-300, 1e-100, 1e-10, 0x1p-27, 0.5, 0.999999, 1.0, Math.nextUp(1.0), 1.5, 3.0, 1e10, 1e100,
                1e300, 0x1p500, 0x1p1000, 0x1p1022, Math.nextUp(0x1p1022), 0x1p1023, 0x1.8p1023, Double.MAX_VALUE));
        List<double[]> pairs = new ArrayList<>();
        for (double a : mags) {
            for (double b : mags) {
                for (int s = 0; s < 4; s++) {
                    pairs.add(new double[] {(s & 1) == 0 ? a : -a, (s & 2) == 0 ? b : -b});
                }
            }
        }
        // ratios on the nodes of T2 (and one ulp each side), x a power of two or an odd value
        double[] xsBase = {1.0, 3.0, 0x1p-30, 0x1p100, 0x1p-1000, 0x1p1000, 0x1.5555555555555p0};
        for (int j = 0; j < 65; j++) {
            double node = CoreMathInverseTrigTables.atan2T2(j);
            if (node == 0.0) {
                continue;
            }
            for (double x : xsBase) {
                double y = node * x;
                for (double yy : new double[] {y, Math.nextUp(y), Math.nextDown(y)}) {
                    if (Double.isFinite(yy) && yy != 0.0) {
                        pairs.add(new double[] {yy, x});
                        pairs.add(new double[] {-yy, x});
                        pairs.add(new double[] {yy, -x});
                        pairs.add(new double[] {-yy, -x});
                    }
                }
            }
        }
        // powers-of-two ratios 2^-60 .. 2^60 and the quotient 1/3 family
        for (int k = -60; k <= 60; k++) {
            double y = Math.scalb(1.0, k);
            pairs.add(new double[] {y, 1.0});
            pairs.add(new double[] {-y, -1.0});
            pairs.add(new double[] {1.0, y});
            pairs.add(new double[] {1.0, -y});
            pairs.add(new double[] {Math.scalb(1.0 / 3.0, k), 1.0});
            pairs.add(new double[] {-Math.scalb(1.0 / 3.0, k), -1.0});
        }
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        List<double[]> keep = new ArrayList<>();
        for (double[] p : pairs) {
            if (!Double.isFinite(p[0]) || !Double.isFinite(p[1]) || p[0] == 0.0 || p[1] == 0.0) {
                continue;
            }
            String key = new StringBuilder(40).append(Double.doubleToRawLongBits(p[0])).append(',').append(Double.doubleToRawLongBits(p[1])).toString();
            if (seen.add(key)) {
                keep.add(p);
            }
        }
        double[] ys = new double[keep.size()];
        double[] xs = new double[keep.size()];
        for (int i = 0; i < ys.length; i++) {
            ys[i] = keep.get(i)[0];
            xs[i] = keep.get(i)[1];
        }
        return new Pairs(ys, xs);
    }

    // ----------------------------------------------------------------------------------------
    // The worst-case files
    // ----------------------------------------------------------------------------------------

    /** Result of reading a {@code .wc} file of pairs ({@code y,x} on each data line). */
    static final class WcPairs {
        final double[] ys;
        final double[] xs;
        final int totalLines;
        final int commentLines;
        final int nonFinite;

        WcPairs(double[] ys, double[] xs, int totalLines, int commentLines, int nonFinite) {
            this.ys = ys;
            this.xs = xs;
            this.totalLines = totalLines;
            this.commentLines = commentLines;
            this.nonFinite = nonFinite;
        }
    }

    private static double parseWcToken(String tok) {
        String t = tok.trim().toLowerCase(Locale.ROOT);
        if (t.contains("nan") || t.contains("inf")) {
            return Double.NaN;
        }
        return Double.parseDouble(t);
    }

    /**
     * Reads {@code atan2.wc}: {@code #} lines are comments, any other line is {@code y,x} (hexadecimal floats or decimals,
     * optional signs, optional blanks around the comma). Lines where either value is a NaN or an infinity are counted and
     * skipped (the evaluator never passes them). {@code limit > 0} stops after that many finite pairs.
     */
    static WcPairs readWcPairs(Path file, int limit) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        double[] ys = new double[limit > 0 ? limit : lines.size()];
        double[] xs = new double[ys.length];
        int n = 0;
        int comments = 0;
        int nonFinite = 0;
        for (String raw : lines) {
            String s = raw.trim();
            if (s.isEmpty() || s.charAt(0) == '#') {
                comments++;
                continue;
            }
            int hash = s.indexOf('#');
            if (hash >= 0) {
                s = s.substring(0, hash).trim();
            }
            int comma = s.indexOf(',');
            if (comma < 0) {
                throw new AssertionError(new StringBuilder(80).append("atan2.wc line without a comma: ").append(raw).toString());
            }
            double y = parseWcToken(s.substring(0, comma));
            double x = parseWcToken(s.substring(comma + 1));
            if (!Double.isFinite(y) || !Double.isFinite(x)) {
                nonFinite++;
                continue;
            }
            ys[n] = y;
            xs[n] = x;
            n++;
            if (limit > 0 && n == limit) {
                break;
            }
        }
        return new WcPairs(java.util.Arrays.copyOf(ys, n), java.util.Arrays.copyOf(xs, n), lines.size(), comments, nonFinite);
    }

    /** The finite entries of a unary {@code .wc} file that the provider accepts, and how many finite entries were outside its domain. */
    static final class WcUnary {
        final double[] values;
        final int outOfDomain;

        WcUnary(double[] values, int outOfDomain) {
            this.values = values;
            this.outOfDomain = outOfDomain;
        }
    }

    /** {@link CoreMathTestSupport#readWc} followed by the removal of entries outside the provider's domain (asin and acos have {@code |x| > 1} entries). */
    static WcUnary readWcInDomain(String function, int limit) throws IOException {
        CoreMathTestSupport.WcData d = CoreMathTestSupport.readWc(CoreMathTestSupport.wcFile(function), 0);
        DoublePredicate ok = providerDomain(function);
        int out = 0;
        List<Double> keep = new ArrayList<>();
        for (double v : d.values) {
            if (ok.test(v)) {
                keep.add(v);
            } else {
                out++;
            }
        }
        int n = (limit > 0) ? Math.min(limit, keep.size()) : keep.size();
        double[] xs = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = keep.get(i);
        }
        return new WcUnary(xs, out);
    }

    // ----------------------------------------------------------------------------------------
    // The checks
    // ----------------------------------------------------------------------------------------

    private static String label(String function, String population, String path) {
        return new StringBuilder(96).append(function).append(", ").append(population).append(", ").append(path).toString();
    }

    /** {@code oracle(x)} for every pair, in parallel. */
    static double[] oracleValues2(double[] ys, double[] xs, DoubleBinaryOperator oracle) {
        double[] out = new double[ys.length];
        IntStream.range(0, ys.length).parallel().forEach(i -> out[i] = oracle.applyAsDouble(ys[i], xs[i]));
        return out;
    }

    /**
     * Four comparisons of one unary function on one population against the expected results: (1) the provider path
     * (what {@code MathProviders.correctlyRounded()} runs); (2) the accurate path alone; (3) the fast path alone where it
     * decides; prints their lines and the path census (how many inputs the fast path decided, and how many needed the
     * accurate path), and asserts there is no mismatch. Returns the number of in-domain inputs the fast path could not decide.
     */
    static long checkUnary(String function, String population, double[] xs, double[] expected) {
        return CoreMathTestSupport.checkUnary(function, population, xs, expected, provider(function), accurate(function), fast(function), pathDomain(function));
    }

    /** What {@link #checkPairs} counted, for the census and the correct-rounding-failure report. */
    static final class PairsReport {
        long stage1Decided;
        long stage2Decided;
        long accurateNeeded;
        long accurateFailures;
        long providerFailures;
    }

    /**
     * The atan2 check of one pair population: (1) the production provider ({@code CoreMath}); (2) the accurate path alone,
     * where a {@code NaN} (the correct-rounding failure branch) is counted and is also a mismatch; (3) the fast path
     * alone, both stages, where it decides; (4) the first stage alone, for the census. Asserts no mismatch and returns the
     * census.
     * <p>
     * <b>A correct-rounding failure FAILS the build here, by design</b> (Fable L5, fix pass of phase B1): the accurate path's NaN is a mismatch,
     * so a population that reaches the C's "Unexpected worst-case" branch stops the run, whoever the caller is (the callers that discard the
     * returned census are covered too). That is intended: such an input is rare and reportable (CORE-MATH asks to be told), and the production
     * provider would still have answered it correctly through {@code CrMath}. The sealed else-branch of B1-P7 said "a finding, not a defect":
     * the finding is exactly this stop; the wording difference is declared.
     */
    static PairsReport checkPairs(String population, double[] ys, double[] xs, double[] expected) {
        BiPredicate<Double, Double> all = (y, x) -> true;
        // DEVIATION D1 (phase B1): the accurate and fast paths are defined for non-zero arguments only (atan2.wc begins with the special rows,
        // zeros among them); the first run called them on the zero rows and reported 14 false mismatches and 2 false failures. The provider path
        // still runs every pair, zero rows included.
        BiPredicate<Double, Double> nonZero = (y, x) -> y != 0.0 && x != 0.0;
        CoreMath prov = (CoreMath) CoreMathTestSupport.PROVIDER;
        long failuresBefore = prov.atan2CorrectRoundingFailures();
        CoreMathTestSupport.Diff dc = CoreMathTestSupport.compare2(label("atan2", population, "provider path"), ys, xs, expected, prov::atan2, all, false, true);
        long providerFailures = prov.atan2CorrectRoundingFailures() - failuresBefore;
        LongAdder nan = new LongAdder();
        DoubleBinaryOperator countingAccurate = (y, x) -> {
            double r = CoreMathAtan2.accurate(y, x);
            if (r != r) {
                nan.increment();
            }
            return r;
        };
        CoreMathTestSupport.Diff da = CoreMathTestSupport.compare2(label("atan2", population, "accurate path alone"), ys, xs, expected, countingAccurate, nonZero, false, true);
        CoreMathTestSupport.Diff df = CoreMathTestSupport.compare2(label("atan2", population, "fast path alone (both stages)"), ys, xs, expected, CoreMathAtan2::fast, nonZero, true, true);
        CoreMathTestSupport.Diff d1 = CoreMathTestSupport.compare2(label("atan2", population, "first stage alone"), ys, xs, expected, (y, x) -> CoreMathAtan2.fast(y, x, false), nonZero, true, true);
        System.out.println(dc.line());
        System.out.println(da.line());
        System.out.println(df.line());
        System.out.println(d1.line());
        PairsReport rep = new PairsReport();
        long inDomain = da.applicable;
        rep.stage1Decided = d1.applicable;
        rep.stage2Decided = df.applicable - d1.applicable;
        rep.accurateNeeded = df.undecided;
        rep.accurateFailures = nan.sum();
        rep.providerFailures = providerFailures;
        StringBuilder census = new StringBuilder(260);
        census.append("path census {atan2, ").append(population).append("}: first stage decided ").append(rep.stage1Decided).append(" of ")
                .append(inDomain).append(" non-zero pairs; second stage decided a further ").append(rep.stage2Decided).append("; accurate path needed for ")
                .append(rep.accurateNeeded).append(" (").append(String.format(Locale.ROOT, "%.3f", 100.0 * rep.accurateNeeded / Math.max(1, inDomain)))
                .append("%); correct-rounding failures (NaN from the accurate path alone): ").append(rep.accurateFailures)
                .append("; routed to CrMath by the provider: ").append(providerFailures);
        System.out.println(census);
        StringBuilder msg = new StringBuilder(512);
        msg.append(dc.details()).append('\n').append(da.details()).append('\n').append(df.details()).append('\n').append(d1.details());
        Assertions.assertEquals(0L, dc.mismatches + da.mismatches + df.mismatches + d1.mismatches, msg.toString());
        Assertions.assertEquals(ys.length, dc.applicable, "the provider-path comparison skipped inputs");
        return rep;
    }

    /** The unary output moved one ulp up on every 1,000th call, in call order (so: run sequentially). */
    static DoubleUnaryOperator perturbEvery1000th(DoubleUnaryOperator port) {
        AtomicLong calls = new AtomicLong();
        return x -> {
            double r = port.applyAsDouble(x);
            return (calls.getAndIncrement() % 1000L == 0L) ? Math.nextUp(r) : r;
        };
    }

    /** The binary output moved one ulp up on every 1,000th call, in call order. */
    static DoubleBinaryOperator perturbEvery1000th(DoubleBinaryOperator port) {
        AtomicLong calls = new AtomicLong();
        return (y, x) -> {
            double r = port.applyAsDouble(y, x);
            return (calls.getAndIncrement() % 1000L == 0L) ? Math.nextUp(r) : r;
        };
    }

    // ----------------------------------------------------------------------------------------
    // The tables: the canonical rendering that is hashed, and the parser of the C source
    // ----------------------------------------------------------------------------------------

    /**
     * The canonical rendering that is hashed: a header line, then for each table in the order of
     * {@link CoreMathInverseTrigTables#snapshot()} a line {@code LEN <name> <count>} and one line per element,
     * {@code <name> <index> <16 lower-case hex digits>} (the raw bits of a double, the value of an int or a long).
     * Lines end with a line feed, the text is UTF-8.
     */
    static String canonical(Map<String, long[]> tables) {
        StringBuilder sb = new StringBuilder(96 * 1024);
        sb.append("CORE-MATH atan.c asin.c acos.c atan2.c tint.h tables, canonical rendering v1\n");
        for (Map.Entry<String, long[]> e : tables.entrySet()) {
            long[] v = e.getValue();
            sb.append("LEN ").append(e.getKey()).append(' ').append(v.length).append('\n');
            for (int i = 0; i < v.length; i++) {
                sb.append(e.getKey()).append(' ').append(i).append(' ').append(CoreMathTestSupport.hex16(v[i])).append('\n');
            }
        }
        return sb.toString();
    }

    static String sha256(Map<String, long[]> tables) {
        return CoreMathTestSupport.sha256Hex(canonical(tables).getBytes(StandardCharsets.UTF_8));
    }

    /** One table in a C source: the text that starts its declaration, and how its values read. */
    private static final class Spec {
        final String name;
        final String file;
        final String marker;
        final char kind;                 // 'd' doubles, 'i' ints, 't' tint_t rows

        Spec(String name, String file, String marker, char kind) {
            this.name = name;
            this.file = file;
            this.marker = marker;
            this.kind = kind;
        }
    }

    private static final Pattern TINT_FIELD = Pattern.compile("\\.(h|m|l|ex|sgn)\\s*=\\s*(-?(?:0x[0-9a-fA-F]+|[0-9]+))");

    /**
     * The tables parsed from the C source: for each table of {@link CoreMathInverseTrigTables#snapshot()}, the values read
     * from the declaration that starts with its marker, in the file(s) that carry it. {@code asin.c} and {@code acos.c}
     * carry the shared tables with identical text, so those are parsed from both and each result is returned under
     * {@code "<file>:<NAME>"}; every other table under {@code "<file>:<NAME>"} too. The caller compares them with the snapshot.
     */
    static Map<String, long[]> parseFromC(Path clone) throws IOException {
        List<Spec> specs = new ArrayList<>();
        specs.add(new Spec("ATAN_A", "atan", "static const double A[][2] = {", 'd'));
        specs.add(new Spec("ATAN_C", "atan", "static const uint16_t c[31][3] = {", 'i'));
        specs.add(new Spec("ATAN_CH", "atan", "static const double ch[] = {", 'd'));
        specs.add(new Spec("ATAN_CH2", "atan", "static const double ch2[] = {", 'd'));
        specs.add(new Spec("ATAN_R2_CH", "atan", "static const double ch[][2] = {", 'd'));
        specs.add(new Spec("ATAN_R2_CL", "atan", "static const double cl[] = {", 'd'));
        specs.add(new Spec("ATAN_DB", "atan", "static const double db[][3] = {", 'd'));
        specs.add(new Spec("ASINACOS_CC", "asin", "static const double cc[][8] = {", 'd'));
        specs.add(new Spec("ASINACOS_CC", "acos", "static const double cc[33][8] = {", 'd'));
        specs.add(new Spec("ASINACOS_S", "asin", "static const double s[33][2] = {", 'd'));
        specs.add(new Spec("ASINACOS_S", "acos", "static const double s[33][2] = {", 'd'));
        specs.add(new Spec("ASINACOS_C", "asin", "static const double c[][2] = {", 'd'));
        specs.add(new Spec("ASINACOS_C", "acos", "static const double c[][2] = {", 'd'));
        specs.add(new Spec("ASINACOS_CT", "asin", "static const double ct[] = {", 'd'));
        specs.add(new Spec("ASINACOS_CT", "acos", "static const double ct[] = {", 'd'));
        specs.add(new Spec("ASIN_OFF", "asin", "static const double off[][2] = {", 'd'));
        specs.add(new Spec("ASIN_DB", "asin", "static const double db[][3] = {", 'd'));
        specs.add(new Spec("ACOS_OFF", "acos", "static const double off[][2] = {", 'd'));
        specs.add(new Spec("ATAN2_T2", "atan2", "static const double T2[] = {", 'd'));
        specs.add(new Spec("ATAN2_F2", "atan2", "static const double f2[][2] = {", 'd'));
        specs.add(new Spec("ATAN2_O", "atan2", "static const double O[8][2] = {", 'd'));
        specs.add(new Spec("ATAN2_B", "atan2", "static const double b[] = {", 'd'));
        specs.add(new Spec("ATAN2_B2", "atan2", "static const double b2[] =", 'd'));
        specs.add(new Spec("ATAN2_FINF", "atan2", "static const double finf[][2] = {", 'd'));
        specs.add(new Spec("ATAN2_P", "atan2", "static const tint_t P[30] = {", 't'));
        specs.add(new Spec("ATAN2_Q", "atan2", "static const tint_t Q[30] = {", 't'));
        specs.add(new Spec("TINT_ZERO", "tint.h", "static const tint_t ZERO = {", 't'));
        specs.add(new Spec("TINT_ONE", "tint.h", "static const tint_t ONE = {", 't'));
        specs.add(new Spec("TINT_PI", "tint.h", "static const tint_t PI = {", 't'));
        specs.add(new Spec("TINT_PI2", "tint.h", "static const tint_t PI2 = {", 't'));
        Map<String, String> texts = new LinkedHashMap<>();
        Map<String, long[]> out = new LinkedHashMap<>();
        for (Spec s : specs) {
            String text = texts.get(s.file);
            if (text == null) {
                Path p = s.file.endsWith(".h")
                        ? clone.resolve("src").resolve("binary64").resolve("atan2").resolve(s.file)
                        : CoreMathTestSupport.cSource(clone, s.file);
                text = stripComments(Files.readString(p, StandardCharsets.UTF_8));
                texts.put(s.file, text);
            }
            out.put(new StringBuilder(32).append(s.file.replace(".h", "")).append(':').append(s.name).toString(), values(body(text, s), s.kind));
        }
        return out;
    }

    private static String stripComments(String c) {
        return c.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\n]*", " ");
    }

    /** The text between the braces of the declaration that starts with the marker (which must occur exactly once). */
    private static String body(String text, Spec s) {
        int at = text.indexOf(s.marker);
        if (at < 0) {
            throw new AssertionError(new StringBuilder(96).append("table not found in the C source (the C text changed?): ").append(s.name)
                    .append(" in ").append(s.file).append(": ").append(s.marker).toString());
        }
        if (text.indexOf(s.marker, at + 1) >= 0) {
            throw new AssertionError(new StringBuilder(96).append("table marker occurs twice: ").append(s.marker).append(" in ").append(s.file).toString());
        }
        int open = text.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(open + 1, i);
                }
            }
        }
        throw new AssertionError("unbalanced braces in the C source");
    }

    private static long[] values(String body, char kind) {
        List<Long> v = new ArrayList<>();
        if (kind == 't') {
            Matcher m = TINT_FIELD.matcher(body);
            while (m.find()) {
                String tok = m.group(2);
                boolean neg = tok.startsWith("-");
                String digits = neg ? tok.substring(1) : tok;
                long x = digits.startsWith("0x") ? Long.parseUnsignedLong(digits.substring(2), 16) : Long.parseLong(digits);
                v.add(neg ? -x : x);
            }
        } else {
            for (String tok : body.split("[\\s,{}]+")) {
                if (tok.isEmpty()) {
                    continue;
                }
                v.add(kind == 'i' ? Long.parseLong(tok) : Double.doubleToRawLongBits(Double.parseDouble(tok)));
            }
        }
        long[] out = new long[v.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = v.get(i);
        }
        return out;
    }

    // ----------------------------------------------------------------------------------------
    // The named scalar constants (phase B1 fix pass): the committed digest covers them through
    // CoreMathInverseTrigTables.snapshot(), and this parser reads the same numbers out of the C text
    // ----------------------------------------------------------------------------------------

    /** The snapshot keys of the named scalar constants start with this; the tables' keys do not. */
    static final String CONSTANT_PREFIX = "K_";

    /** {@code snapshot} restricted to the 26 tables (the entries whose digest was committed before the constants existed). */
    static Map<String, long[]> tablesOnly(Map<String, long[]> snapshot) {
        Map<String, long[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> e : snapshot.entrySet()) {
            if (!e.getKey().startsWith(CONSTANT_PREFIX)) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    /** {@code snapshot} restricted to the named scalar constants. */
    static Map<String, long[]> constantsOnly(Map<String, long[]> snapshot) {
        Map<String, long[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> e : snapshot.entrySet()) {
            if (e.getKey().startsWith(CONSTANT_PREFIX)) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private static final String HEX = "(-?0x[0-9a-fA-F]*\\.?[0-9a-fA-F]*p[-+]?[0-9]+)";

    /**
     * The named scalar constants parsed from the C text: for each place in the C source where one of them is written (the
     * expression that uses it, matched by a regular expression over the comment-stripped text, the n-th match), the value
     * written there. Keys are {@code "<file>:<NAME>@<n>"} with {@code <NAME>} the field name of
     * {@link CoreMathInverseTrigTables} (without the snapshot prefix) and {@code <n>} the index of the place; a double is
     * returned as its raw bits, an integer as itself. A constant used in several places (or in several files) has one entry per
     * place, so every use in the C is compared with the single Java definition.
     */
    static Map<String, long[]> constantsFromC(Path clone) throws IOException {
        List<Object[]> specs = new ArrayList<>();
        // atan.c
        constantSpec(specs, "ATAN_EPS_P", "atan", "epsp = f\\*{H}, epsm = f\\*{H}", 1, 0, false);
        constantSpec(specs, "ATAN_EPS_M", "atan", "epsp = f\\*{H}, epsm = f\\*{H}", 2, 0, false);
        constantSpec(specs, "ATAN_E", "atan", "double e = h\\*{H};", 1, 0, false);
        constantSpec(specs, "PI2_H", "atan", "ah = __builtin_copysign\\({H}, x\\);\\s*al = __builtin_copysign\\({H}, x\\);", 1, 0, false);
        constantSpec(specs, "PI2_L", "atan", "ah = __builtin_copysign\\({H}, x\\);\\s*al = __builtin_copysign\\({H}, x\\);", 2, 0, false);
        constantSpec(specs, "ATAN_AL_FAST", "atan", "A\\[i\\]\\[1\\] \\+ {H}\\*id;", 1, 0, false);
        constantSpec(specs, "ATAN_PI128_H", "atan", "ah = {H}\\*id;", 1, 0, false);
        constantSpec(specs, "ATAN_PI128_H", "atan", "ah = {H}\\*id;", 1, 1, false);
        constantSpec(specs, "ATAN_PI128_L", "atan", "al = {H}\\*id, at = {H}\\*id;", 1, 0, false);
        constantSpec(specs, "ATAN_PI128_S", "atan", "al = {H}\\*id, at = {H}\\*id;", 2, 0, false);
        constantSpec(specs, "ATAN_RCP", "atan", "__builtin_fabs\\(a\\)\\*{H} \\+ 256\\.5", 1, 0, false);
        // asin.c
        constantSpec(specs, "ASIN_EPS_HI", "asin", "eps = __builtin_fabs\\(z\\*t\\)\\*{H};", 1, 0, false);
        constantSpec(specs, "ASIN_EPS_LO", "asin", "eps = __builtin_fabs\\(z\\*t\\)\\*{H};", 1, 1, false);
        constantSpec(specs, "RCP_PI64", "asin", "__builtin_fabs\\(phi\\) \\* {H}\\)", 1, 0, false);
        constantSpec(specs, "MAGIC", "asin", "#define MAGIC {H}", 1, 0, false);
        constantSpec(specs, "PI64_H", "asin", "pih = {H};", 1, 0, false);
        constantSpec(specs, "PI64_L", "asin", "pil = {H};", 1, 0, false);
        constantSpec(specs, "PI64_S", "asin", "pis = {H};", 1, 0, false);
        // acos.c
        constantSpec(specs, "ACOS_EPS_HI", "acos", "eps = __builtin_fabs\\(z\\*t\\)\\*{H} \\+ {H};", 1, 0, false);
        constantSpec(specs, "ACOS_EPS_HI_ADD", "acos", "eps = __builtin_fabs\\(z\\*t\\)\\*{H} \\+ {H};", 2, 0, false);
        constantSpec(specs, "ACOS_EPS1", "acos", "static const double eps1 = {H};", 1, 0, false);
        constantSpec(specs, "ACOS_EPS_LO", "acos", "eps = \\(z\\*t\\)\\*{H};", 1, 0, false);
        constantSpec(specs, "PI2_H", "acos", "f0h = {H};\\s*f0l = {H};", 1, 0, false);
        constantSpec(specs, "PI2_L", "acos", "f0h = {H};\\s*f0l = {H};", 2, 0, false);
        constantSpec(specs, "PI2_H", "acos", "__builtin_fabs\\(phi - {H}\\) \\* {H}\\)", 1, 0, false);
        constantSpec(specs, "RCP_PI64", "acos", "__builtin_fabs\\(phi - {H}\\) \\* {H}\\)", 2, 0, false);
        constantSpec(specs, "MAGIC", "acos", "#define MAGIC {H}", 1, 0, false);
        constantSpec(specs, "PI64_H", "acos", "ph = jt \\* {H}, pl = {H}\\*jt, ps = {H}\\*jt;", 1, 0, false);
        constantSpec(specs, "PI64_L", "acos", "ph = jt \\* {H}, pl = {H}\\*jt, ps = {H}\\*jt;", 2, 0, false);
        constantSpec(specs, "PI64_S", "acos", "ph = jt \\* {H}, pl = {H}\\*jt, ps = {H}\\*jt;", 3, 0, false);
        // atan2.c
        constantSpec(specs, "PI_H", "atan2", "#define\\s+PI_H\\s+{H}", 1, 0, false);
        constantSpec(specs, "PI_L", "atan2", "#define\\s+PI_L\\s+{H}", 1, 0, false);
        constantSpec(specs, "PI2_H", "atan2", "#define\\s+PI_OVER2_H\\s+{H}", 1, 0, false);
        constantSpec(specs, "PI2_L", "atan2", "#define\\s+PI_OVER2_L\\s+{H}", 1, 0, false);
        constantSpec(specs, "ATAN2_EPS1", "atan2", "double eps = __builtin_fabs\\(z\\)\\*{H} \\+ {H};", 1, 0, false);
        constantSpec(specs, "ATAN2_EPS1_ADD", "atan2", "double eps = __builtin_fabs\\(z\\)\\*{H} \\+ {H};", 2, 0, false);
        constantSpec(specs, "ATAN2_EPS2", "atan2", "eps = {H}\\*\\(__builtin_fabs\\(zh\\)\\*z2 \\+ {H}\\);", 1, 0, false);
        constantSpec(specs, "ATAN2_EPS2_ADD", "atan2", "eps = {H}\\*\\(__builtin_fabs\\(zh\\)\\*z2 \\+ {H}\\);", 2, 0, false);
        constantSpec(specs, "ATAN2_ERR_DIV", "atan2", "\\berr = (\\d+);", 1, 0, true);
        constantSpec(specs, "ATAN2_ERR_INV", "atan2", "\\berr = (\\d+);", 1, 1, true);
        constantSpec(specs, "ATAN2_ERR_NEG", "atan2", "\\berr = (\\d+);", 1, 2, true);
        constantSpec(specs, "ATAN2_ERR_SMALL", "atan2", "tint_tod \\(z, (\\d+), y, x\\)", 1, 0, true);
        Map<String, String> texts = new LinkedHashMap<>();
        Map<String, long[]> out = new LinkedHashMap<>();
        int n = 0;
        for (Object[] sp : specs) {
            String name = (String) sp[0];
            String file = (String) sp[1];
            String text = texts.get(file);
            if (text == null) {
                text = stripComments(Files.readString(CoreMathTestSupport.cSource(clone, file), StandardCharsets.UTF_8));
                texts.put(file, text);
            }
            Matcher m = Pattern.compile(((String) sp[2]).replace("{H}", HEX)).matcher(text);
            int want = (Integer) sp[4];
            int seen = 0;
            String lit = null;
            while (m.find()) {
                if (seen++ == want) {
                    lit = m.group((Integer) sp[3]);
                    break;
                }
            }
            if (lit == null) {
                throw new AssertionError(new StringBuilder(120).append("constant ").append(name).append(" not found in ").append(file).append(".c: match ").append(want)
                        .append(" of ").append(sp[2]).append(" (the C text changed?)").toString());
            }
            long v = ((Boolean) sp[5]) ? Long.parseLong(lit) : Double.doubleToRawLongBits(Double.parseDouble(lit));
            out.put(new StringBuilder(40).append(file).append(':').append(name).append('@').append(n++).toString(), new long[] {v});
        }
        return out;
    }

    private static void constantSpec(List<Object[]> specs, String name, String file, String regex, int group, int occurrence, boolean isLong) {
        specs.add(new Object[] {name, file, regex, group, occurrence, isLong});
    }
}
