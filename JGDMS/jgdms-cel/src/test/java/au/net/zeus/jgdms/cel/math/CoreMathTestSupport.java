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
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.DoublePredicate;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.IntStream;

/**
 * What the CORE-MATH port tests share: the one comparison routine (so the control that proves it
 * can fail runs the very code the real checks run), the input populations, the reader of the
 * CORE-MATH {@code .wc} worst-case files, and the {@link CrMath} oracle cache.
 * <p>
 * The oracle is {@link CrMath}: a different algorithm (arbitrary-precision decimal arithmetic with
 * Ziv escalation) that was not derived from CORE-MATH, so agreement is two independent
 * computations agreeing, not a port agreeing with itself. {@code CrMath} is thread-safe for
 * results (read 2026-11-12: its mutable statics are one lock-guarded cache and two diagnostic
 * fields), so the oracle runs in parallel.
 */
final class CoreMathTestSupport {

    private CoreMathTestSupport() {}

    /** Where the read-only CORE-MATH clone lives; override with {@code -Dcore.math.dir=...}. */
    static final Path CORE_MATH_DIR = Path.of(System.getProperty("core.math.dir", "J:/std011-speed-2026-11-11/core-math"));

    /** The CrMath oracle shared by every test in a JVM. */
    static final CrMath ORACLE = new CrMath();

    static Path wcFile(String function) {
        return CORE_MATH_DIR.resolve("src").resolve("binary64").resolve(function).resolve(new StringBuilder(8).append(function).append(".wc").toString());
    }

    static boolean wcAvailable(String function) {
        return Files.isReadable(wcFile(function));
    }

    // ----------------------------------------------------------------------------------------
    // Hex rendering
    // ----------------------------------------------------------------------------------------

    /** A double as its hexadecimal literal plus its raw bits, so a report can be pasted into a test. */
    static String hex(double v) {
        StringBuilder sb = new StringBuilder(48);
        sb.append(Double.toHexString(v)).append(" (0x");
        String b = Long.toHexString(Double.doubleToRawLongBits(v));
        for (int i = b.length(); i < 16; i++) {
            sb.append('0');
        }
        sb.append(b).append(')');
        return sb.toString();
    }

    // ----------------------------------------------------------------------------------------
    // Populations
    // ----------------------------------------------------------------------------------------

    /** Random doubles, sign random, binary exponent field uniform in [{@code eLo}, {@code eHi}], fraction random. */
    static double[] exponentUniform(long seed, int n, int eLo, int eHi) {
        SplittableRandom r = new SplittableRandom(seed);
        double[] xs = new double[n];
        for (int i = 0; i < n; i++) {
            long sign = r.nextBoolean() ? Long.MIN_VALUE : 0L;
            long e = eLo + r.nextInt(eHi - eLo + 1);
            long frac = r.nextLong() & 0xfffffffffffffL;
            xs[i] = Double.longBitsToDouble(sign | (e << 52) | frac);
        }
        return xs;
    }

    /** Every finite range: exponent field 0..2046 (field 0 is the subnormals). */
    static double[] finiteRangeRandom(long seed, int n) {
        return exponentUniform(seed, n, 0, 2046);
    }

    /** The binades around the code's thresholds: |x| from 2^-30 up to 2^33 (the moderate path is 2^-26 .. 2^31). */
    static double[] moderateRangeRandom(long seed, int n) {
        return exponentUniform(seed, n, 0x3ff - 30, 0x3ff + 32);
    }

    /** Result of reading a {@code .wc} file. */
    static final class WcData {
        final double[] values;
        final int totalLines;
        final int commentLines;
        final int nonFinite;

        WcData(double[] values, int totalLines, int commentLines, int nonFinite) {
            this.values = values;
            this.totalLines = totalLines;
            this.commentLines = commentLines;
            this.nonFinite = nonFinite;
        }
    }

    /**
     * Reads a {@code .wc} file: {@code #} lines are comments, any other line is one value (a
     * hexadecimal float or a decimal such as {@code +0}, optionally followed by a {@code # ...} remark or by
     * whitespace and a number, which are ignored). Lines that name a NaN or an
     * infinity are counted and skipped (they are never passed to a provider). {@code limit > 0}
     * stops after that many finite values.
     */
    static WcData readWc(Path file, int limit) throws IOException {
        double[] buf = new double[limit > 0 ? limit : 4_000_000];
        int n = 0;
        int total = 0;
        int comments = 0;
        int nonFinite = 0;
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (String raw : lines) {
            total++;
            String s = raw.trim();
            if (s.isEmpty() || s.charAt(0) == '#') {
                comments++;
                continue;
            }
            int hash = s.indexOf('#');
            if (hash >= 0) {
                s = s.substring(0, hash).trim();
            }
            int blank = 0;
            while (blank < s.length() && !Character.isWhitespace(s.charAt(blank))) {
                blank++;
            }
            s = s.substring(0, blank);                     // sin.wc has 3 lines like "0x1.4f1d73be27a31p+1 44": the value is the first token
            String lower = s.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("nan") || lower.contains("inf")) {
                nonFinite++;
                continue;
            }
            double v = Double.parseDouble(s);
            if (!Double.isFinite(v)) {
                nonFinite++;
                continue;
            }
            if (n == buf.length) {
                buf = java.util.Arrays.copyOf(buf, buf.length * 2);
            }
            buf[n++] = v;
            if (limit > 0 && n == limit) {
                break;
            }
        }
        return new WcData(java.util.Arrays.copyOf(buf, n), total, comments, nonFinite);
    }

    /**
     * The named edge inputs, with both signs: zero, the smallest subnormal and normal, the 2^-26 /
     * 2^-27 / 2^-16 / 2^31 thresholds of the C code and their neighbours, pi/4 and multiples of pi/2
     * with neighbours, 1, 1e22, 1e300, MAX_VALUE, the four inputs of CORE-MATH's cosine
     * {@code exceptions} ({@code cos.c:753-760}) and the two inputs its comments name as having failed a
     * smaller error bound ({@code sin.c:947}, {@code cos.c:935}).
     */
    static double[] namedInputs() {
        List<Double> v = new ArrayList<>();
        double[] base = {
            0.0, Double.MIN_VALUE, 3 * Double.MIN_VALUE, Double.MIN_NORMAL, Math.nextDown(Double.MIN_NORMAL),
            0x1p-60, 0x1p-30, 0x1p-27, 0x1p-26, 0x1p-20, 0x1p-16, 1e-10, 1e-5, 0.1, 0.5,
            Math.PI / 4, Math.PI / 2, Math.PI, 3 * Math.PI / 2, 2 * Math.PI, 1.0, 2.0, 3.0, 10.0, 100.0, 1000.0,
            0x1p20, 0x1p30, 0x1p31, 0x1.fffffffffffffp30, 0x1p31 + 1.0, 0x1p53, 1e15, 1e22, 1e100, 1e300,
            0x1p1000, Double.MAX_VALUE,
            0x1.8000000000009p-23, 0x1.8000000000024p-22, 0x1.800000000009p-21, 0x1.20000000000f3p-20,
            0x1.54a9ad28f0a25p+225, 0x1.64dd7934d0fdfp+953
        };
        for (double b : base) {
            v.add(b);
            v.add(-b);
            v.add(Math.nextUp(b));
            v.add(Math.nextDown(b));
            v.add(-Math.nextUp(b));
            v.add(-Math.nextDown(b));
        }
        // values near multiples of pi/2 (the double nearest k*pi/2 and its neighbours)
        int[] ks = {1, 2, 3, 4, 5, 6, 7, 8, 10, 16, 100, 355, 1000, 22 * 7 * 113, 1 << 20};
        for (int k : ks) {
            double m = k * (Math.PI / 2);
            for (int d = -3; d <= 3; d++) {
                double x = m;
                for (int i = 0; i < Math.abs(d); i++) {
                    x = d > 0 ? Math.nextUp(x) : Math.nextDown(x);
                }
                v.add(x);
                v.add(-x);
            }
        }
        // drop non-finite (nextUp(MAX_VALUE) is +Inf) and de-duplicate by bit pattern
        List<Double> out = new ArrayList<>();
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        for (double d : v) {
            if (Double.isFinite(d) && seen.add(Double.doubleToRawLongBits(d))) {
                out.add(d);
            }
        }
        double[] xs = new double[out.size()];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = out.get(i);
        }
        return xs;
    }

    // ----------------------------------------------------------------------------------------
    // The oracle cache and the comparison
    // ----------------------------------------------------------------------------------------

    /** {@code oracle(x)} for every input, in parallel; the array is the expected output. */
    static double[] oracleValues(double[] xs, DoubleUnaryOperator oracle) {
        double[] out = new double[xs.length];
        IntStream.range(0, xs.length).parallel().forEach(i -> out[i] = oracle.applyAsDouble(xs[i]));
        return out;
    }

    /** What {@link #compare} found. */
    static final class Diff {
        final String label;
        final long applicable;
        final long notApplicable;
        final long undecided;
        final long mismatches;
        final List<String> examples;

        Diff(String label, long applicable, long notApplicable, long undecided, long mismatches, List<String> examples) {
            this.label = label;
            this.applicable = applicable;
            this.notApplicable = notApplicable;
            this.undecided = undecided;
            this.mismatches = mismatches;
            this.examples = examples;
        }

        /** The brief's report line. */
        String line() {
            StringBuilder sb = new StringBuilder(160);
            sb.append("port vs CrMath at {").append(label).append("}: mismatches ").append(mismatches)
                    .append(" of ").append(applicable);
            if (notApplicable > 0) {
                sb.append(" (").append(notApplicable).append(" inputs outside this path's domain not run)");
            }
            if (undecided > 0) {
                sb.append(" (").append(undecided).append(" undecided by the fast path, not compared)");
            }
            return sb.toString();
        }

        /** Every mismatch example (at most the first 10) with the input in hex and both outputs. */
        String details() {
            StringBuilder sb = new StringBuilder(256);
            sb.append(line());
            for (String e : examples) {
                sb.append("\n    ").append(e);
            }
            return sb.toString();
        }
    }

    /**
     * The one comparison: for every input in the domain, the candidate's {@code doubleToRawLongBits}
     * must equal the oracle's. No tolerance. If {@code nanMeansUndecided}, a candidate result of
     * NaN is counted as "undecided" and not compared (the fast paths return NaN when their error
     * bound cannot decide the rounding); otherwise a NaN candidate is a mismatch like any other.
     *
     * @param inDomain inputs outside the candidate's domain are counted and not run
     * @param parallel the candidate and {@code inDomain} must be thread-safe when true; a stateful
     *                 candidate (the control) runs with false, in input order
     */
    static Diff compare(String label, double[] xs, double[] expected, DoubleUnaryOperator candidate,
                        DoublePredicate inDomain, boolean nanMeansUndecided, boolean parallel) {
        if (xs.length != expected.length) {
            throw new IllegalArgumentException("inputs and expected outputs differ in length");
        }
        LongAdder applicable = new LongAdder();
        LongAdder notApplicable = new LongAdder();
        LongAdder undecided = new LongAdder();
        LongAdder mismatches = new LongAdder();
        ConcurrentLinkedQueue<String> examples = new ConcurrentLinkedQueue<>();
        IntStream range = IntStream.range(0, xs.length);
        if (parallel) {
            range = range.parallel();
        }
        range.forEach(i -> {
            double x = xs[i];
            if (!inDomain.test(x)) {
                notApplicable.increment();
                return;
            }
            double got = candidate.applyAsDouble(x);
            if (nanMeansUndecided && Double.isNaN(got)) {
                undecided.increment();
                return;
            }
            applicable.increment();
            if (Double.doubleToRawLongBits(got) != Double.doubleToRawLongBits(expected[i])) {
                mismatches.increment();
                if (examples.size() < 10) {
                    StringBuilder sb = new StringBuilder(160);
                    sb.append("x=").append(hex(x)).append("  port=").append(hex(got)).append("  CrMath=").append(hex(expected[i]));
                    examples.add(sb.toString());
                }
            }
        });
        return new Diff(label, applicable.sum(), notApplicable.sum(), undecided.sum(), mismatches.sum(), new ArrayList<>(examples));
    }
}
