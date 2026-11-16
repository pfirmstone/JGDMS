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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * The worst-case evidence that travels with the code, for atan, asin, acos and atan2: a committed golden subset of CORE-MATH's
 * {@code atan.wc}, {@code asin.wc}, {@code acos.wc} and {@code atan2.wc} (the first 2,000 accepted entries of each, plus every later
 * entry at one of the file's extreme exponents; the rule is stated in each header and in {@code CoreMathGoldenGeneratorB1}), each with
 * the expected result bits that {@link CrMath} computed at generation time. The test compares the production provider with the golden,
 * bit for bit, on every machine: no clone, no oracle calls. The resources keep the {@code .wc} MIT header and record the source
 * commit, the source md5 and the generator. Runtime tier: FAST (default suite). The full files are covered by
 * {@code CoreMathInverseTrigSlowTest}.
 *
 * <h2>SEALED PREDICTIONS (B1-P12, phase B1; written before the golden files were generated or any of these tests run)</h2>
 * Population: the golden entries; scoring rule: {@code Double.doubleToRawLongBits(provider(x))} equals the golden bits, no tolerance.
 * <ol>
 *   <li>The provider path, the accurate path alone (in its domain) and the fast path alone (where it decides) have 0 mismatches on every
 *       golden entry of atan, asin, acos and atan2 (for atan2: the provider, the accurate path alone and both fast stages).</li>
 *   <li>Each golden's header carries the MIT permission text of the {@code .wc} source, a 32-hex source md5, the source commit and the
 *       generator name, and the header's {@code entries} count equals the number of data lines.</li>
 *   <li><b>Control.</b> With the expected bits of every 100th entry moved by one ulp, the same comparison rejects exactly ceil(N/100)
 *       entries of each golden (N = the golden's entry count) and no other.</li>
 *   <li>Each golden has at least 2,000 entries (the first 2,000 selected entries and the extreme-exponent entries beyond them).</li>
 * </ol>
 * ELSE-BRANCH (1): a mismatch is a defect of the port or of the oracle; reported with the input and both outputs; the comparison is not
 * loosened.
 */
class CoreMathInverseTrigWorstCaseGoldenTest {

    private static final Pattern ENTRIES = Pattern.compile("^# entries: (\\d+) \\(first (\\d+); extreme-exponent entries beyond those: (\\d+)\\)");
    private static final Pattern MD5 = Pattern.compile("^# source-md5: ([0-9a-f]{32})\\s*$");

    private static final class Golden {
        final List<String> header = new ArrayList<>();
        double[] ys;                                    // null for a unary function
        double[] xs;
        double[] expected;
        int entries;
        int first;
        int extreme;
        String md5;
    }

    private static Golden load(String function) throws IOException {
        String name = new StringBuilder(40).append("core-math-worst-cases-").append(function).append(".txt").toString();
        boolean pairs = function.equals("atan2");
        Golden g = new Golden();
        List<Double> ys = new ArrayList<>();
        List<Double> xs = new ArrayList<>();
        List<Long> bits = new ArrayList<>();
        try (InputStream in = CoreMathInverseTrigWorstCaseGoldenTest.class.getResourceAsStream(name)) {
            assertNotNull(in, new StringBuilder(80).append("the committed golden resource ").append(name).append(" is missing").toString());
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (line.startsWith("#")) {
                    g.header.add(line);
                    Matcher m = ENTRIES.matcher(line);
                    if (m.find()) {
                        g.entries = Integer.parseInt(m.group(1));
                        g.first = Integer.parseInt(m.group(2));
                        g.extreme = Integer.parseInt(m.group(3));
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
                if (pairs) {
                    ys.add(Double.parseDouble(f[0]));
                    xs.add(Double.parseDouble(f[1]));
                    bits.add(Long.parseUnsignedLong(f[2], 16));
                } else {
                    xs.add(Double.parseDouble(f[0]));
                    bits.add(Long.parseUnsignedLong(f[1], 16));
                }
            }
        }
        g.xs = new double[xs.size()];
        g.expected = new double[xs.size()];
        for (int i = 0; i < g.xs.length; i++) {
            g.xs[i] = xs.get(i);
            g.expected[i] = Double.longBitsToDouble(bits.get(i));
        }
        if (pairs) {
            g.ys = new double[ys.size()];
            for (int i = 0; i < g.ys.length; i++) {
                g.ys[i] = ys.get(i);
            }
        }
        return g;
    }

    @Test
    void portAgreesWithTheGoldenWorstCases() throws IOException {
        for (String f : new String[] {"atan", "asin", "acos"}) {
            Golden g = load(f);
            assertEquals(g.xs.length, g.entries, "the header's entries count differs from the data lines");
            assertEquals(2000, g.first);
            assertEquals(g.first + g.extreme, g.xs.length);
            System.out.println(new StringBuilder(120).append("golden worst cases: ").append(f).append(' ').append(g.xs.length).append(" entries (").append(g.first).append(" + ")
                    .append(g.extreme).append(" at extreme exponents)"));
            CoreMathInverseTrigTestSupport.checkUnary(f, "golden worst cases (committed)", g.xs, g.expected);
        }
        Golden g = load("atan2");
        assertEquals(g.xs.length, g.entries);
        assertEquals(2000, g.first);
        assertEquals(g.first + g.extreme, g.xs.length);
        System.out.println(new StringBuilder(120).append("golden worst cases: atan2 ").append(g.xs.length).append(" entries (").append(g.first).append(" + ").append(g.extreme)
                .append(" at extreme exponents)"));
        CoreMathInverseTrigTestSupport.checkPairs("golden worst cases (committed)", g.ys, g.xs, g.expected);
    }

    @Test
    void goldenHeadersCarryTheLicenceAndTheProvenance() throws IOException {
        for (String f : new String[] {"atan", "asin", "acos", "atan2"}) {
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
            assertTrue(text.contains("CoreMathGoldenGeneratorB1"), new StringBuilder(64).append(f).append(": the generator is not named").toString());
            assertTrue(text.contains("# selection:"), new StringBuilder(64).append(f).append(": the selection rule is not stated").toString());
            assertTrue(g.xs.length >= 2000, new StringBuilder(64).append(f).append(": fewer than 2000 entries").toString());
        }
    }

    /** The control: the comparison must be able to fail. Expected bits moved by one ulp on every 100th entry are found. */
    @Test
    void controlExpectedBitsMovedOnEvery100thEntryAreRejected() throws IOException {
        for (String f : new String[] {"atan", "asin", "acos", "atan2"}) {
            Golden g = load(f);
            double[] moved = g.expected.clone();
            int expectedRejections = 0;
            for (int i = 0; i < moved.length; i += 100) {
                moved[i] = Math.nextUp(moved[i]);
                expectedRejections++;
            }
            CoreMathTestSupport.Diff d;
            if (f.equals("atan2")) {
                d = CoreMathTestSupport.compare2("atan2 golden control", g.ys, g.xs, moved, CoreMathTestSupport.PROVIDER::atan2, (y, x) -> true, false, true);
            } else {
                d = CoreMathTestSupport.compare(new StringBuilder(64).append(f).append(" golden control").toString(), g.xs, moved, CoreMathInverseTrigTestSupport.provider(f), x -> true, false, true);
            }
            System.out.println(new StringBuilder(120).append("golden control (").append(f).append("): rejected ").append(d.mismatches).append(" of ").append(d.applicable)
                    .append(" with the expected bits moved on every 100th entry"));
            assertEquals(expectedRejections, d.mismatches, d.details());
            assertEquals(g.xs.length, d.applicable);
        }
    }
}
