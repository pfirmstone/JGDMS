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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiPredicate;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoublePredicate;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;

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
        return compareCore(label, xs.length, expected,
                i -> inDomain.test(xs[i]),
                i -> candidate.applyAsDouble(xs[i]),
                i -> new StringBuilder(32).append("x=").append(hex(xs[i])).toString(),
                nanMeansUndecided, parallel);
    }

    /**
     * {@link #compare} for a function of two arguments (atan2): input {@code i} is the pair
     * {@code (ys[i], xs[i])}, in the order of {@code atan2(y, x)}. The same core runs, so the control that proves
     * the unary comparison can fail proves this one too.
     */
    static Diff compare2(String label, double[] ys, double[] xs, double[] expected, DoubleBinaryOperator candidate,
                         BiPredicate<Double, Double> inDomain, boolean nanMeansUndecided, boolean parallel) {
        if (ys.length != expected.length || xs.length != expected.length) {
            throw new IllegalArgumentException("inputs and expected outputs differ in length");
        }
        return compareCore(label, xs.length, expected,
                i -> inDomain.test(ys[i], xs[i]),
                i -> candidate.applyAsDouble(ys[i], xs[i]),
                i -> new StringBuilder(80).append("y=").append(hex(ys[i])).append(" x=").append(hex(xs[i])).toString(),
                nanMeansUndecided, parallel);
    }

    /** The one comparison loop: scoring is {@code doubleToRawLongBits(candidate(i)) == doubleToRawLongBits(expected[i])}, no tolerance. */
    private static Diff compareCore(String label, int n, double[] expected, IntPredicate inDomain, IntToDoubleFunction candidate,
                                    IntFunction<String> describe, boolean nanMeansUndecided, boolean parallel) {
        LongAdder applicable = new LongAdder();
        LongAdder notApplicable = new LongAdder();
        LongAdder undecided = new LongAdder();
        LongAdder mismatches = new LongAdder();
        ConcurrentLinkedQueue<String> examples = new ConcurrentLinkedQueue<>();
        IntStream range = IntStream.range(0, n);
        if (parallel) {
            range = range.parallel();
        }
        range.forEach(i -> {
            if (!inDomain.test(i)) {
                notApplicable.increment();
                return;
            }
            double got = candidate.applyAsDouble(i);
            if (nanMeansUndecided && Double.isNaN(got)) {
                undecided.increment();
                return;
            }
            applicable.increment();
            if (Double.doubleToRawLongBits(got) != Double.doubleToRawLongBits(expected[i])) {
                mismatches.increment();
                if (examples.size() < 10) {
                    StringBuilder sb = new StringBuilder(160);
                    sb.append(describe.apply(i)).append("  port=").append(hex(got)).append("  CrMath=").append(hex(expected[i]));
                    examples.add(sb.toString());
                }
            }
        });
        return new Diff(label, applicable.sum(), notApplicable.sum(), undecided.sum(), mismatches.sum(), new ArrayList<>(examples));
    }
    // ----------------------------------------------------------------------------------------
    // The production provider, and the clone requirement
    // ----------------------------------------------------------------------------------------

    /**
     * The provider the production entry point returns, {@code MathProviders.correctlyRounded().get()}.
     * The "provider path" comparisons call this, so a guard added to {@code CoreMath} (or a change of
     * what the factory returns) is seen by every one of them.
     */
    static final CorrectlyRoundedMath PROVIDER = MathProviders.correctlyRounded().get();

    /** True when the full tier was asked for with {@code -Dcore.math.full=true}. */
    static final boolean FULL_TIER = Boolean.getBoolean("core.math.full");

    /** Where the C source of one function lives under the clone. */
    static Path cSource(Path cloneDir, String function) {
        return cloneDir.resolve("src").resolve("binary64").resolve(function)
                .resolve(new StringBuilder(8).append(function).append(".c").toString());
    }

    /**
     * What a test that needs the CORE-MATH clone does when it is absent from {@code dir}: if the full
     * tier was requested ({@code full}) it FAILS, loudly, because a requested tier that did not run is
     * not a pass; otherwise it is aborted (reported as skipped). Present: returns.
     */
    static void requireClone(Path dir, boolean full, String what) {
        requireClone(dir, full, what, "sin", "cos");
    }

    /** {@link #requireClone(Path, boolean, String)} for the C sources {@code functions} (phase B1: atan, asin, acos, atan2). */
    static void requireClone(Path dir, boolean full, String what, String... functions) {
        boolean present = true;
        for (String f : functions) {
            present &= Files.isReadable(cSource(dir, f));
        }
        if (present) {
            return;
        }
        StringBuilder msg = new StringBuilder(160);
        msg.append(full ? "FAILED: the full tier was requested (-Dcore.math.full=true) but " : "SKIPPED: ")
                .append("the CORE-MATH clone is not at ").append(dir).append(" (needed by ").append(what).append(')');
        if (full) {
            Assertions.fail(msg.toString());
        }
        Assumptions.abort(msg.toString());
    }

    /** {@link #requireClone(Path, boolean, String)} with this JVM's clone path and tier request. */
    static void requireClone(String what) {
        requireClone(CORE_MATH_DIR, FULL_TIER, what);
    }

    // ----------------------------------------------------------------------------------------
    // One population check, shared by the fast and the slow tier
    // ----------------------------------------------------------------------------------------

    private static final DoublePredicate SIN_DOMAIN = x -> Math.abs(x) >= 0x1p-26;
    private static final DoublePredicate COS_DOMAIN = x -> Math.abs(x) >= 0x1p-27;

    /**
     * Runs the three comparisons of one function on one population against the expected results,
     * prints their report lines and the path census, and asserts none mismatches: (1) the provider
     * path, {@link #PROVIDER} (what {@code MathProviders.correctlyRounded()} runs); (2) the accurate
     * path alone; (3) the fast path alone, where it decides. The seams (2) and (3) take the same
     * {@code x} as the provider.
     */
    static void checkPopulation(String function, String population, double[] xs, double[] expected) {
        boolean isSin = function.equals("sin");
        DoubleUnaryOperator provider = isSin ? PROVIDER::sin : PROVIDER::cos;
        DoubleUnaryOperator accurate = isSin ? CoreMathSin::accurate : CoreMathCos::accurate;
        DoubleUnaryOperator fast = isSin ? CoreMathSin::fast : CoreMathCos::fast;
        DoublePredicate domain = isSin ? SIN_DOMAIN : COS_DOMAIN;
        Diff dc = compare(label(function, population, "provider path"), xs, expected, provider, x -> true, false, true);
        Diff da = compare(label(function, population, "accurate path alone"), xs, expected, accurate, domain, false, true);
        Diff df = compare(label(function, population, "fast path alone"), xs, expected, fast, domain, true, true);
        System.out.println(dc.line());
        System.out.println(da.line());
        System.out.println(df.line());
        StringBuilder census = new StringBuilder(160);
        census.append("path census {").append(function).append(", ").append(population).append("}: fast path decided ")
                .append(df.applicable).append(" of ").append(df.applicable + df.undecided)
                .append(" in-domain inputs; accurate path needed for ").append(df.undecided);
        System.out.println(census);
        StringBuilder all = new StringBuilder(512);
        all.append(dc.details()).append('\n').append(da.details()).append('\n').append(df.details());
        Assertions.assertEquals(0L, dc.mismatches + da.mismatches + df.mismatches, all.toString());
        Assertions.assertEquals(xs.length, dc.applicable, "the provider-path comparison skipped inputs");
    }

    private static String label(String function, String population, String path) {
        return new StringBuilder(96).append(function).append(", ").append(population).append(", ").append(path).toString();
    }

    // ----------------------------------------------------------------------------------------
    // The constant tables: a snapshot, a parser of the C source, a canonical rendering, a digest
    // ----------------------------------------------------------------------------------------

    private static final Pattern U128_ENTRY = Pattern.compile("U128\\(\\s*(0x[0-9a-fA-F]+|0)\\s*,\\s*(0x[0-9a-fA-F]+|0)\\s*\\)");
    private static final Pattern SCALAR = Pattern.compile("static const double (pih|pil) = (-?0x[0-9a-fA-F.]+p[-+]?[0-9]+);");

    /**
     * Every table of {@link CoreMathTables}, as a value that is a COPY: either of the Java tables
     * ({@link #fromJava}) or parsed from a CORE-MATH C source file ({@link #fromC}), so the two can be
     * compared and both can be rendered canonically and hashed.
     */
    static final class TableSet {
        long[] t;
        double[] u1;
        double[] u2;
        U128[] ps;
        U128[] pc;
        U128[] s1u;
        U128[] s2u;
        U128[] c2u;
        double pih;
        double pil;

        static TableSet fromJava() {
            TableSet s = new TableSet();
            s.t = CoreMathTables.tCopy();
            s.u1 = CoreMathTables.u1Copy();
            s.u2 = CoreMathTables.u2Copy();
            s.ps = CoreMathTables.psCopy();
            s.pc = CoreMathTables.pcCopy();
            s.s1u = CoreMathTables.s1uCopy();
            s.s2u = CoreMathTables.s2uCopy();
            s.c2u = CoreMathTables.c2uCopy();
            s.pih = CoreMathTables.PIH;
            s.pil = CoreMathTables.PIL;
            return s;
        }

        /** Parses the tables from {@code sin.c} or {@code cos.c} (regular expressions over the C text). */
        static TableSet fromC(Path cFile) throws IOException {
            List<String> lines = Files.readAllLines(cFile, StandardCharsets.UTF_8);
            TableSet s = new TableSet();
            List<Long> tl = new ArrayList<>();
            for (String l : block(lines, "static const uint64_t _T[20]")) {
                String x = l.trim();
                if (x.endsWith(",")) {
                    tl.add(unsignedHex(x.substring(0, x.length() - 1).trim()));
                }
            }
            s.t = new long[tl.size()];
            for (int i = 0; i < s.t.length; i++) {
                s.t[i] = tl.get(i);
            }
            s.u1 = parseDoubles(block(lines, "static const double U1[128][4]"));
            s.u2 = parseDoubles(block(lines, "static const double U2[128][4]"));
            s.ps = parseU128(block(lines, "static const u128 PS[]"));
            s.pc = parseU128(block(lines, "static const u128 PC[]"));
            s.s1u = parseU128(block(lines, "static const u128 S1u[33]"));
            s.s2u = parseU128(block(lines, "static const u128 S2u[64]"));
            s.c2u = parseU128(block(lines, "static const u128 C2u[64]"));
            s.pih = Double.NaN;
            s.pil = Double.NaN;
            for (String l : lines) {
                Matcher m = SCALAR.matcher(l);
                if (m.find()) {
                    if (m.group(1).equals("pih")) {
                        s.pih = Double.parseDouble(m.group(2));
                    } else {
                        s.pil = Double.parseDouble(m.group(2));
                    }
                }
            }
            return s;
        }

        private static long unsignedHex(String s) {
            return s.equals("0") ? 0L : Long.parseUnsignedLong(s.substring(2), 16);
        }

        /** Lines after the line containing {@code header} up to the closing {@code };}, with {@code //} remarks removed. */
        private static List<String> block(List<String> lines, String header) {
            List<String> out = new ArrayList<>();
            boolean in = false;
            for (String line : lines) {
                if (!in) {
                    if (line.contains(header)) {
                        in = true;
                    }
                    continue;
                }
                if (line.startsWith("};")) {
                    return out;
                }
                int c = line.indexOf("//");
                out.add(c >= 0 ? line.substring(0, c) : line);
            }
            throw new AssertionError(new StringBuilder(96).append("table not found in the C source (the C text changed?): ").append(header).toString());
        }

        private static double[] parseDoubles(List<String> block) {
            List<Double> d = new ArrayList<>();
            for (String l : block) {
                String s = l.trim();
                if (s.startsWith("{")) {
                    for (String f : s.substring(1, s.indexOf('}')).split(",")) {
                        d.add(Double.parseDouble(f.trim()));
                    }
                }
            }
            double[] out = new double[d.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = d.get(i);
            }
            return out;
        }

        private static U128[] parseU128(List<String> block) {
            List<U128> u = new ArrayList<>();
            for (String l : block) {
                Matcher m = U128_ENTRY.matcher(l);
                if (m.find()) {
                    u.add(U128.lh(unsignedHex(m.group(1)), unsignedHex(m.group(2))));
                }
            }
            return u.toArray(new U128[0]);
        }

        /** Number of values in this set as the tests count them: a u128 counts once, the two scalars count 2. */
        int valueCount() {
            return t.length + u1.length + u2.length + ps.length + pc.length + s1u.length + s2u.length + c2u.length + 2;
        }

        /** Returns {mismatching values, values compared}; a length difference counts as that many mismatches. */
        static long[] compare(TableSet a, TableSet b) {
            long bad = 0;
            long n = 0;
            long[] r = compareLongs(a.t, b.t);
            bad += r[0];
            n += r[1];
            r = compareDoubles(a.u1, b.u1);
            bad += r[0];
            n += r[1];
            r = compareDoubles(a.u2, b.u2);
            bad += r[0];
            n += r[1];
            U128[][] aa = {a.ps, a.pc, a.s1u, a.s2u, a.c2u};
            U128[][] bb = {b.ps, b.pc, b.s1u, b.s2u, b.c2u};
            for (int k = 0; k < aa.length; k++) {
                n += Math.max(aa[k].length, bb[k].length);
                bad += Math.abs(aa[k].length - bb[k].length);
                for (int i = 0; i < Math.min(aa[k].length, bb[k].length); i++) {
                    if (!aa[k][i].equals(bb[k][i])) {
                        bad++;
                    }
                }
            }
            n += 2;
            if (Double.doubleToRawLongBits(a.pih) != Double.doubleToRawLongBits(b.pih)) {
                bad++;
            }
            if (Double.doubleToRawLongBits(a.pil) != Double.doubleToRawLongBits(b.pil)) {
                bad++;
            }
            return new long[] {bad, n};
        }

        private static long[] compareLongs(long[] a, long[] b) {
            long bad = Math.abs(a.length - b.length);
            for (int i = 0; i < Math.min(a.length, b.length); i++) {
                if (a[i] != b[i]) {
                    bad++;
                }
            }
            return new long[] {bad, Math.max(a.length, b.length)};
        }

        private static long[] compareDoubles(double[] a, double[] b) {
            long bad = Math.abs(a.length - b.length);
            for (int i = 0; i < Math.min(a.length, b.length); i++) {
                if (Double.doubleToRawLongBits(a[i]) != Double.doubleToRawLongBits(b[i])) {
                    bad++;
                }
            }
            return new long[] {bad, Math.max(a.length, b.length)};
        }

        /**
         * The canonical rendering that is hashed: a header line, then for each table in the fixed order
         * T, U1, U2, PS, PC, S1U, S2U, C2U a line {@code LEN <name> <count>} and one line per element,
         * {@code <name> <index> <hex>}: 16 lower-case hex digits of the raw bits of a {@code long} or
         * {@code double}, 32 digits (high word first) of a {@code u128}; finally {@code PIH} and
         * {@code PIL}. Lines end with a line feed (no carriage return), the text is UTF-8.
         */
        String canonical() {
            StringBuilder sb = new StringBuilder(64 * 1024);
            sb.append("CORE-MATH sin.c tables, canonical rendering v1\n");
            sb.append("LEN T ").append(t.length).append('\n');
            for (int i = 0; i < t.length; i++) {
                sb.append("T ").append(i).append(' ').append(hex16(t[i])).append('\n');
            }
            appendDoubles(sb, "U1", u1);
            appendDoubles(sb, "U2", u2);
            appendU128(sb, "PS", ps);
            appendU128(sb, "PC", pc);
            appendU128(sb, "S1U", s1u);
            appendU128(sb, "S2U", s2u);
            appendU128(sb, "C2U", c2u);
            sb.append("PIH ").append(hex16(Double.doubleToRawLongBits(pih))).append('\n');
            sb.append("PIL ").append(hex16(Double.doubleToRawLongBits(pil))).append('\n');
            return sb.toString();
        }

        private static void appendDoubles(StringBuilder sb, String name, double[] d) {
            sb.append("LEN ").append(name).append(' ').append(d.length).append('\n');
            for (int i = 0; i < d.length; i++) {
                sb.append(name).append(' ').append(i).append(' ').append(hex16(Double.doubleToRawLongBits(d[i]))).append('\n');
            }
        }

        private static void appendU128(StringBuilder sb, String name, U128[] u) {
            sb.append("LEN ").append(name).append(' ').append(u.length).append('\n');
            for (int i = 0; i < u.length; i++) {
                sb.append(name).append(' ').append(i).append(' ').append(hex16(u[i].hi)).append(hex16(u[i].lo)).append('\n');
            }
        }

        /** SHA-256 of {@link #canonical()} (UTF-8), 64 lower-case hex digits. */
        String sha256() {
            return CoreMathTestSupport.sha256Hex(canonical().getBytes(StandardCharsets.UTF_8));
        }
    }

    /** 16 lower-case hex digits. */
    static String hex16(long v) {
        String h = Long.toHexString(v);
        StringBuilder sb = new StringBuilder(16);
        for (int i = h.length(); i < 16; i++) {
            sb.append('0');
        }
        return sb.append(h).toString();
    }

    static String sha256Hex(byte[] data) {
        return digestHex("SHA-256", data);
    }

    static String digestHex(String algorithm, byte[] data) {
        try {
            byte[] d = MessageDigest.getInstance(algorithm).digest(data);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
