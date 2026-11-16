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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.DoublePredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Shared helpers of the phase B2 (tan) tests: the domain of the paths, the populations (named inputs, random binades, inputs near
 * multiples of pi/2), the parser of the C text ({@code tan.c}) for the tables and the named constants, and the one population check
 * ({@link CoreMathTestSupport#checkUnary}, the code of every earlier phase, called with the tan port, its two seams and the provider).
 */
final class CoreMathTanTestSupport {

    private CoreMathTanTestSupport() {}

    /** The header line of the canonical rendering that is hashed (see {@link CoreMathInverseTrigTestSupport#canonical(String, Map)}). */
    static final String CANONICAL_HEADER = "CORE-MATH tan.c tables, canonical rendering v1";

    /** The domain of the fast and accurate paths: finite {@code |x| > 0x1.d12ed0af1a27ep-27} (below it the C answers by an early exit). */
    static final DoublePredicate PATH_DOMAIN = x -> Double.isFinite(x) && (Double.doubleToRawLongBits(x) & 0x7fffffffffffffffL) > CoreMathTanTables.TINY_BITS;

    /** The three comparisons of one population against the expected (CrMath) results; returns the number of in-domain inputs the fast path could not decide. */
    static long checkUnary(String population, double[] xs, double[] expected) {
        return CoreMathTestSupport.checkUnary("tan", population, xs, expected, CoreMathTestSupport.PROVIDER::tan, CoreMathTan::accurate, CoreMathTan::fast, PATH_DOMAIN);
    }

    /** {@code CrMath.tan} of every input, in parallel. */
    static double[] oracle(double[] xs) {
        return CoreMathTestSupport.oracleValues(xs, CoreMathTestSupport.ORACLE::tan);
    }

    /**
     * How many in-domain inputs the accurate path (forced) reports as ambiguous (the C's 86-ulp test fired), how many of those are not one of the two exceptions, and how many make the
     * guard of {@link CoreMathTan#guardFires} fire: {@code {inDomain, ambiguous, notException, guard}}.
     */
    static long[] ambiguousCensus(double[] xs) {
        LongAdder inDomain = new LongAdder();
        LongAdder ambiguous = new LongAdder();
        LongAdder notException = new LongAdder();
        LongAdder guard = new LongAdder();
        IntStream.range(0, xs.length).parallel().forEach(i -> {
            double x = xs[i];
            if (!PATH_DOMAIN.test(x)) {
                return;
            }
            inDomain.increment();
            boolean[] amb = new boolean[2];
            CoreMathTan.accurateAmbiguous(x, amb);
            if (amb[1]) {
                guard.increment();
            }
            if (amb[0]) {
                ambiguous.increment();
                double a = Math.abs(x);
                if (a != CoreMathTanTables.exceptions(0) && a != CoreMathTanTables.exceptions(3)) {
                    notException.increment();
                }
            }
        });
        return new long[] {inDomain.sum(), ambiguous.sum(), notException.sum(), guard.sum()};
    }

    // ----------------------------------------------------------------------------------------
    // pi to many digits (Machin), for the near-multiples-of-pi/2 population
    // ----------------------------------------------------------------------------------------

    private static final int PI_DIGITS = 450;
    private static final MathContext MC = new MathContext(PI_DIGITS + 20);
    private static BigDecimal halfPi;

    private static BigDecimal arctanInv(int n) {
        BigDecimal x = BigDecimal.ONE.divide(BigDecimal.valueOf(n), MC);
        BigDecimal x2 = x.multiply(x, MC);
        BigDecimal eps = BigDecimal.ONE.movePointLeft(PI_DIGITS + 15);
        BigDecimal term = x;
        BigDecimal sum = x;
        for (int k = 1; ; k++) {
            term = term.multiply(x2, MC);
            BigDecimal t = term.divide(BigDecimal.valueOf(2L * k + 1L), MC);
            if (t.compareTo(eps) < 0) {
                break;
            }
            sum = (k % 2 == 1) ? sum.subtract(t, MC) : sum.add(t, MC);
        }
        return sum;
    }

    /** pi/2 to about 450 significant digits (Machin: pi = 16 arctan(1/5) - 4 arctan(1/239)). */
    static synchronized BigDecimal halfPi() {
        if (halfPi == null) {
            BigDecimal pi = arctanInv(5).multiply(BigDecimal.valueOf(16), MC).subtract(arctanInv(239).multiply(BigDecimal.valueOf(4), MC), MC);
            halfPi = pi.divide(BigDecimal.valueOf(2), MC);
        }
        return halfPi;
    }

    /** The double nearest to {@code k * pi/2}, or 0 if it is not finite. */
    static double nearestToMultipleOfHalfPi(BigInteger k) {
        double v = new BigDecimal(k).multiply(halfPi(), MC).doubleValue();
        return Double.isFinite(v) ? v : 0.0;
    }

    /**
     * {@code n} inputs: {@code k} has a bit length uniform in [1, {@code maxBits}], the input is the double nearest to
     * {@code k*pi/2} moved by -8..+8 ulps, either sign. For small {@code k} these are near multiples of pi/2, where {@code |tan|} is largest (near odd multiples) or smallest (near even
     * ones) and the argument reduction cancels the most. For {@code k} above about 2^40 an ulp of the input exceeds the spacing of the poles, so those members are ordinary large
     * arguments: of the 250,000 inputs of the 1,020-bit population only about 12,500 are below 2^52 (Opus review L2, 2026-11-12), so the population is about 5% near-pole,
     * about 95% random large arguments; the 52-bit population is about 99% below 2^52.
     */
    static double[] nearHalfPiMultiples(long seed, int n, int maxBits) {
        SplittableRandom r = new SplittableRandom(seed);
        double[] xs = new double[n];
        for (int i = 0; i < n; i++) {
            int bits = 1 + r.nextInt(maxBits);
            BigInteger k = BigInteger.ONE.shiftLeft(bits - 1);
            for (int b = bits - 2; b >= 0; b--) {
                if (r.nextBoolean()) {
                    k = k.setBit(b);
                }
            }
            double x = nearestToMultipleOfHalfPi(k);
            if (x == 0.0) {
                x = 1.0;
            }
            int d = r.nextInt(17) - 8;
            for (int s = 0; s < Math.abs(d); s++) {
                x = d > 0 ? Math.nextUp(x) : Math.nextDown(x);
            }
            if (!Double.isFinite(x)) {
                x = 1.0;
            }
            xs[i] = r.nextBoolean() ? -x : x;
        }
        return xs;
    }

    /** Uniform in [0, 8) as a multiple of 2^-53 times 8, either sign: the argument range of the tables, no exponent spread. */
    static double[] uniformZeroToEight(long seed, int n) {
        SplittableRandom r = new SplittableRandom(seed);
        double[] xs = new double[n];
        for (int i = 0; i < n; i++) {
            double v = (double) (r.nextLong() >>> 11) * 0x1p-53 * 8.0;
            xs[i] = r.nextBoolean() ? -v : v;
        }
        return xs;
    }

    /** Random doubles, either sign, unbiased binary exponent uniform in [{@code eLo}, {@code eHi}]. */
    static double[] binades(long seed, int n, int eLo, int eHi) {
        return CoreMathTestSupport.exponentUniform(seed, n, 0x3ff + eLo, 0x3ff + eHi);
    }

    /**
     * The named edge inputs, both signs: zero, the smallest subnormal and normal, the tiny-argument threshold {@code 0x1.d12ed0af1a27ep-27} and its
     * neighbours, 2^-27 and 2^-26, the largest argument of the single-product reduction ({@code 0x1.921fb54442d17p+2}) and its neighbours, pi/4 and the
     * multiples of pi/2 with neighbours, the two hard-coded exceptions of {@code tan_accurate}, the inputs of the C comments (the largest and the smallest
     * {@code |tan|}), the exponent boundaries of the Payne-Hanek branches (2^51, 2^52, 2^53 and neighbours), 1, 1e22, 1e300, {@code MAX_VALUE}.
     */
    static double[] namedInputs() {
        List<Double> v = new ArrayList<>();
        double[] base = {
            0.0, Double.MIN_VALUE, 3 * Double.MIN_VALUE, Double.MIN_NORMAL, Math.nextDown(Double.MIN_NORMAL), 0x1p-60, 0x1p-30, 0x1p-28, 0x1p-27, 0x1p-26,
            Double.longBitsToDouble(CoreMathTanTables.TINY_BITS), 0x1.7137449123ef6p-26, 0x1p-20, 1e-10, 1e-5, 0.1, 0.5,
            Math.PI / 4, Math.PI / 2, Math.PI, 3 * Math.PI / 2, 2 * Math.PI, CoreMathTanTables.TWO_PI_LIMIT, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 10.0, 100.0, 1000.0,
            0x1p20, 0x1p25, 0x1p30, 0x1p31, 0x1p50, 0x1p51, 0x1p52, 0x1p53, 0x1p54, 1e15, 1e22, 1e100, 1e300, 0x1p1000, 0x1p1023, Double.MAX_VALUE,
            CoreMathTanTables.exceptions(0), CoreMathTanTables.exceptions(3), 0x1.6ac5b262ca1ffp+849, 2 * 0x1.6ac5b262ca1ffp+849, 0x1.921fb54442d16p+2,
        };
        for (double b : base) {
            v.add(b);
            v.add(-b);
            v.add(Math.nextUp(b));
            v.add(Math.nextDown(b));
            v.add(-Math.nextUp(b));
            v.add(-Math.nextDown(b));
        }
        int[] ks = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 16, 100, 355, 1000, 22 * 7 * 113, 1 << 20, 1 << 30};
        for (int k : ks) {
            double m = nearestToMultipleOfHalfPi(BigInteger.valueOf(k));
            for (int d = -3; d <= 3; d++) {
                double x = m;
                for (int i = 0; i < Math.abs(d); i++) {
                    x = d > 0 ? Math.nextUp(x) : Math.nextDown(x);
                }
                v.add(x);
                v.add(-x);
            }
        }
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
    // tan.wc, with the position of the pole block
    // ----------------------------------------------------------------------------------------

    /** The finite entries of {@code tan.wc} in file order, and the index of the first entry of the final block ("|tan(x)| >= 2^48 or |tan(x)| < 2^-48"). */
    static final class WcTan {
        final double[] values;
        final int poleBlockStart;

        WcTan(double[] values, int poleBlockStart) {
            this.values = values;
            this.poleBlockStart = poleBlockStart;
        }
    }

    /** Reads {@code tan.wc} with the reader of every earlier phase and finds the pole block by its comment. */
    static WcTan readWc(Path file) throws IOException {
        CoreMathTestSupport.WcData d = CoreMathTestSupport.readWc(file, 0);
        int start = -1;
        int n = 0;
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String s = raw.trim();
            if (s.isEmpty() || s.charAt(0) == '#') {
                if (start < 0 && s.contains("|tan(x)| >= 2^48")) {
                    start = n;                                  // the next finite entry is the first of the block
                }
                continue;
            }
            String lower = s.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("nan") || lower.contains("inf")) {
                continue;
            }
            n++;
        }
        if (n != d.values.length) {
            throw new AssertionError(new StringBuilder(80).append("tan.wc entry count disagrees: ").append(n).append(" against ").append(d.values.length).toString());
        }
        return new WcTan(d.values, start);
    }

    // ----------------------------------------------------------------------------------------
    // The C text: the tables and the named constants
    // ----------------------------------------------------------------------------------------

    private static final Pattern DINT_ROW = Pattern.compile("\\.hi\\s*=\\s*(0x[0-9a-fA-F]+)\\s*,\\s*\\.lo\\s*=\\s*(0x[0-9a-fA-F]+)\\s*,\\s*\\.ex\\s*=\\s*(-?[0-9]+)\\s*,\\s*\\.sgn\\s*=\\s*(0x[0-9a-fA-F]+|[0-9]+)");

    private static long unsigned(String tok) {
        return tok.startsWith("0x") ? Long.parseUnsignedLong(tok.substring(2), 16) : Long.parseLong(tok);
    }

    private static final String[][] TABLES = {
        {"TAN_TINV", "static uint64_t Tinv[256] = {", "u"},
        {"TAN_T", "static const uint64_t T[20] = {", "u"},
        {"TAN_S", "static const dint64_t S[256] = {", "D"},
        {"TAN_C", "static const dint64_t C[256] = {", "D"},
        {"TAN_PSFAST", "static const  double PSfast[] = {", "d"},
        {"TAN_PCFAST", "static const  double PCfast[] = {", "d"},
        {"TAN_PS", "static const dint64_t PS[] = {", "D"},
        {"TAN_PC", "static const dint64_t PC[] = {", "D"},
        {"TAN_SC", "static const double SC[256][3] = {", "d"},
        {"TAN_EXCEPTIONS", "static const double exceptions[2][3] = {", "d"},
        {"TAN_DINT_ZERO", "static const dint64_t ZERO = {", "D"},
        {"TAN_DINT_ONE", "static const dint64_t ONE = {", "D"},
        {"TAN_DINT_MAGIC", "static const dint64_t MAGIC = {", "D"},
    };

    /** The tables parsed from {@code tan.c}: {@code "tan:<NAME>"} and the values (a double as its raw bits, a dint row as hi, lo, ex, sgn). */
    static Map<String, long[]> parseFromC(Path clone) throws IOException {
        String text = CoreMathInverseTrigTestSupport.stripComments(Files.readString(CoreMathTestSupport.cSource(clone, "tan"), StandardCharsets.UTF_8));
        Map<String, long[]> out = new LinkedHashMap<>();
        for (String[] t : TABLES) {
            String body = bodyOf(text, t[1], t[0]);
            List<Long> vals = new ArrayList<>();
            if (t[2].equals("D")) {
                Matcher m = DINT_ROW.matcher(body);
                while (m.find()) {
                    vals.add(unsigned(m.group(1)));
                    vals.add(unsigned(m.group(2)));
                    vals.add(Long.parseLong(m.group(3)));
                    vals.add(unsigned(m.group(4)));
                }
            } else {
                for (String tok : body.split("[\\s,{}]+")) {
                    if (tok.isEmpty()) {
                        continue;
                    }
                    vals.add(t[2].equals("u") ? unsigned(tok) : Double.doubleToRawLongBits(Double.parseDouble(tok)));
                }
            }
            long[] a = new long[vals.size()];
            for (int i = 0; i < a.length; i++) {
                a[i] = vals.get(i);
            }
            out.put(new StringBuilder(24).append("tan:").append(t[0]).toString(), a);
        }
        return out;
    }

    /** The text between the braces of the declaration that starts with the marker (which must occur exactly once). */
    private static String bodyOf(String text, String marker, String name) {
        int at = text.indexOf(marker);
        if (at < 0) {
            throw new AssertionError(new StringBuilder(96).append("table not found in tan.c (the C text changed?): ").append(name).append(": ").append(marker).toString());
        }
        if (text.indexOf(marker, at + 1) >= 0) {
            throw new AssertionError(new StringBuilder(96).append("table marker occurs twice in tan.c: ").append(marker).toString());
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
        throw new AssertionError("unbalanced braces in tan.c");
    }

    /** One place in the C where a named constant is written: name, regex ({H} = a hexadecimal float), group, kind ('d' double, 'x' hexadecimal integer, 'i' decimal integer). */
    private static final String[][] CONSTANTS = {
        {"TINY_BITS", "ux <= (0x[0-9a-fA-F]+)ull", "1", "x"},
        {"TINY_FMA", "__builtin_fma \\(x, {H}, x\\)", "1", "d"},
        {"TWO_PI_LIMIT", "__builtin_expect\\(x <= {H}, 1\\)", "1", "d"},
        {"CH", "#define CH {H}", "1", "d"},
        {"CL", "#define CL {H}", "1", "d"},
        {"H_MIN", "i==0 && \\*h < {H}, 0\\)\\)\\s*return {H};", "1", "d"},
        {"ERR_UNDECIDED", "i==0 && \\*h < {H}, 0\\)\\)\\s*return {H};", "2", "d"},
        {"FAST_ERR", "return \\*h \\* {H};", "1", "d"},
        {"ACCURATE_ERR", "uint64_t err = (\\d+);", "1", "i"},
    };

    /**
     * The named constants parsed from {@code tan.c}: for each place where the C writes one (matched by a regular expression over the
     * comment-stripped text), the value written there. Keys are {@code "tan:<NAME>@<n>"}.
     */
    static Map<String, long[]> constantsFromC(Path clone) throws IOException {
        String text = CoreMathInverseTrigTestSupport.stripComments(Files.readString(CoreMathTestSupport.cSource(clone, "tan"), StandardCharsets.UTF_8));
        Map<String, long[]> out = new LinkedHashMap<>();
        int n = 0;
        for (String[] c : CONSTANTS) {
            Matcher m = Pattern.compile(c[1].replace("{H}", CoreMathInverseTrigTestSupport.HEX)).matcher(text);
            if (!m.find()) {
                throw new AssertionError(new StringBuilder(120).append("constant ").append(c[0]).append(" not found in tan.c: ").append(c[1]).append(" (the C text changed?)").toString());
            }
            String lit = m.group(Integer.parseInt(c[2]));
            long v = c[3].equals("d") ? Double.doubleToRawLongBits(Double.parseDouble(lit)) : c[3].equals("x") ? Long.parseUnsignedLong(lit.substring(2), 16) : Long.parseLong(lit);
            if (m.find()) {
                throw new AssertionError(new StringBuilder(96).append("constant ").append(c[0]).append(" is matched twice in tan.c: ").append(c[1]).toString());
            }
            out.put(new StringBuilder(40).append("tan:").append(c[0]).append('@').append(n++).toString(), new long[] {v});
        }
        return out;
    }
}
