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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The worst-case evidence that travels with the code: a committed golden subset of CORE-MATH's
 * {@code sin.wc} and {@code cos.wc} (the first 2,000 finite entries of each, plus every later finite entry
 * whose binary exponent is 1023, the top binade, where the Payne-Hanek reduction reads the last words of its
 * table), each with the expected result bits that {@link CrMath} computed at generation time
 * ({@code CoreMathGoldenGenerator}, a harness). The test compares the production provider with the golden,
 * bit for bit, on every machine: no clone, no oracle calls, a fraction of a second. The resources keep the
 * {@code .wc} MIT header and record the source commit, the source md5 and the generator.
 * <p>
 * Runtime tier: FAST (default suite). The full files are covered by {@code CoreMathSinCosSlowTest}.
 *
 * <h2>SEALED PREDICTIONS (fix pass, written before the golden files were generated or any of these tests run)</h2>
 * Population: the golden entries; scoring rule: {@code Double.doubleToRawLongBits(provider(x))} equals the
 * golden bits, no tolerance.
 * <ol>
 *   <li><b>F4</b> the provider path, the accurate path alone and the fast path alone (where it decides) have
 *       0 mismatches on every golden entry of sin and of cos.</li>
 *   <li><b>F5 counts</b> each golden has 2,000 leading entries and, beyond them, 2,121 (sin) and 1,079 (cos)
 *       entries of binary exponent 1023, none of the leading 2,000 being at exponent 1023 (the counts are
 *       the reviewers' {@code grep -c 'p+1023'} of the two {@code .wc} files; if a line of that exponent is
 *       non-finite or carries other text the generator's count will differ, and the difference is reported as
 *       a deviation, not hidden). Each file's header {@code entries} line equals the number of data lines.</li>
 *   <li><b>F6 provenance</b> each golden header carries the MIT permission text of the {@code .wc} source, a
 *       32-hex source md5, the source commit and the generator name.</li>
 *   <li><b>F7 control</b> with the expected bits of every 100th entry moved by one ulp, the same comparison
 *       rejects exactly ceil(N/100) entries (sin 42 of 4,121; cos 31 of 3,079) and no other.</li>
 * </ol>
 */
class CoreMathWorstCaseGoldenTest {

    /**
     * Entries of exponent 1023 beyond the leading 2000 (the header's third number), and in the whole golden.
     * DEVIATION D7 from sealed prediction F5, declared in READY: F5 said 2,121 beyond the leading 2000 and none inside
     * them for sin; the generator found 1,784 beyond and 337 INSIDE the leading 2000 (sin.wc's block for [2^1023, 2^1024)
     * starts at line 1688 of the file, before the 2000th entry), 2,121 in all, which is the reviewers' grep count. cos is as
     * predicted: 1,079 beyond, none inside.
     */
    private static final int BEYOND_SIN = 1784;
    private static final int TOTAL_SIN = 2121;
    private static final int BEYOND_COS = 1079;
    private static final int TOTAL_COS = 1079;
    private static final Pattern ENTRIES = Pattern.compile("^# entries: (\\d+) \\(first (\\d+); exponent 1023 beyond those: (\\d+)\\)");
    private static final Pattern MD5 = Pattern.compile("^# source-md5: ([0-9a-f]{32})\\s*$");

    private static final class Golden {
        final List<String> header = new ArrayList<>();
        double[] xs;
        double[] expected;
        int entries;
        int first;
        int top;
        String md5;
    }

    private static Golden load(String function) throws IOException {
        String name = new StringBuilder(40).append("core-math-worst-cases-").append(function).append(".txt").toString();
        Golden g = new Golden();
        List<Double> xs = new ArrayList<>();
        List<Long> bits = new ArrayList<>();
        try (InputStream in = CoreMathWorstCaseGoldenTest.class.getResourceAsStream(name)) {
            assertNotNull(in, new StringBuilder(80).append("the committed golden resource ").append(name).append(" is missing").toString());
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (line.startsWith("#")) {
                    g.header.add(line);
                    Matcher m = ENTRIES.matcher(line);
                    if (m.find()) {
                        g.entries = Integer.parseInt(m.group(1));
                        g.first = Integer.parseInt(m.group(2));
                        g.top = Integer.parseInt(m.group(3));
                    }
                    Matcher d = MD5.matcher(line);
                    if (d.find()) {
                        g.md5 = d.group(1);
                    }
                    continue;
                }
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] f = line.trim().split(" ");
                xs.add(Double.parseDouble(f[0]));
                bits.add(Long.parseUnsignedLong(f[1], 16));
            }
        }
        g.xs = new double[xs.size()];
        g.expected = new double[xs.size()];
        for (int i = 0; i < g.xs.length; i++) {
            g.xs[i] = xs.get(i);
            g.expected[i] = Double.longBitsToDouble(bits.get(i));
        }
        return g;
    }

    private static void checkCounts(String function, Golden g, int expectedBeyond, int expectedTotal) {
        assertEquals(g.xs.length, g.entries, "the header's entries count differs from the data lines");
        assertEquals(2000, g.first);
        assertEquals(expectedBeyond, g.top, "exponent-1023 entries beyond the first 2000");
        assertEquals(2000 + expectedBeyond, g.xs.length);
        int atTop = 0;
        for (int i = 0; i < g.xs.length; i++) {
            if (Math.getExponent(g.xs[i]) == 1023) {
                atTop++;
            }
        }
        assertEquals(expectedTotal, atTop, new StringBuilder(64).append(function).append(": entries of exponent 1023 in the whole golden").toString());
    }

    @Test
    void portAgreesWithTheGoldenWorstCases() throws IOException {
        Golden s = load("sin");
        Golden c = load("cos");
        checkCounts("sin", s, BEYOND_SIN, TOTAL_SIN);
        checkCounts("cos", c, BEYOND_COS, TOTAL_COS);
        System.out.println(new StringBuilder(120).append("golden worst cases: sin ").append(s.xs.length).append(" entries (2000 + ")
                .append(s.top).append(" at exponent 1023), cos ").append(c.xs.length).append(" entries (2000 + ").append(c.top).append(')'));
        CoreMathTestSupport.checkPopulation("sin", "golden worst cases (committed)", s.xs, s.expected);
        CoreMathTestSupport.checkPopulation("cos", "golden worst cases (committed)", c.xs, c.expected);
    }

    @Test
    void goldenHeadersCarryTheLicenceAndTheProvenance() throws IOException {
        for (String f : new String[] {"sin", "cos"}) {
            Golden g = load(f);
            StringBuilder all = new StringBuilder(2048);
            for (String h : g.header) {
                all.append(h).append('\n');
            }
            String text = all.toString();
            assertTrue(text.contains("Permission is hereby granted, free of charge"), new StringBuilder(64).append(f).append(": the MIT permission text is missing").toString());
            assertTrue(text.contains("THE SOFTWARE IS PROVIDED \"AS IS\""), new StringBuilder(64).append(f).append(": the MIT warranty text is missing").toString());
            assertNotNull(g.md5, new StringBuilder(64).append(f).append(": no source md5").toString());
            assertTrue(text.contains("018fa97cbdacf59924b899a9dfa422d532cb2bb7"), new StringBuilder(64).append(f).append(": the source commit is not named").toString());
            assertTrue(text.contains("CoreMathGoldenGenerator"), new StringBuilder(64).append(f).append(": the generator is not named").toString());
        }
    }

    /** The control: the comparison must be able to fail. Expected bits moved by one ulp on every 100th entry are found. */
    @Test
    void controlExpectedBitsMovedOnEvery100thEntryAreRejected() throws IOException {
        for (String f : new String[] {"sin", "cos"}) {
            Golden g = load(f);
            double[] moved = g.expected.clone();
            int expectedRejections = 0;
            for (int i = 0; i < moved.length; i += 100) {
                moved[i] = Math.nextUp(moved[i]);
                expectedRejections++;
            }
            DoubleUnaryOperator provider = f.equals("sin") ? CoreMathTestSupport.PROVIDER::sin : CoreMathTestSupport.PROVIDER::cos;
            CoreMathTestSupport.Diff d = CoreMathTestSupport.compare(new StringBuilder(64).append(f).append(" golden control").toString(),
                    g.xs, moved, provider, x -> true, false, true);
            System.out.println(new StringBuilder(120).append("golden control (").append(f).append("): rejected ").append(d.mismatches)
                    .append(" of ").append(d.applicable).append(" with the expected bits moved on every 100th entry"));
            assertEquals(expectedRejections, d.mismatches, d.details());
            assertEquals(g.xs.length, d.applicable);
        }
    }

}
