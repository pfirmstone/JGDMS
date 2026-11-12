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
 * The worst-case evidence that travels with the code, for tan: a committed golden subset of CORE-MATH's {@code tan.wc} (the first 2,000 finite
 * entries, every later entry at one of the file's extreme exponents, and the 50 entries of the file's final block, the inputs within 2^-48 of a multiple of pi/2, with the
 * largest and the 50 with the smallest {@code |tan|}; the rule is stated in the header and in {@code CoreMathGoldenGeneratorTan}), each with the expected result bits
 * that {@link CrMath} computed at generation time. The test compares the production provider with the golden, bit for bit, on every machine: no clone, no oracle calls. The
 * resource keeps the {@code .wc} MIT header and records the source commit, the source md5 and the generator. Runtime tier: FAST (default suite). The full file is covered by
 * {@code CoreMathTanSlowTest}.
 *
 * <h2>SEALED PREDICTIONS (B2-G1..G4, phase B2; written before the golden file was generated or any of these tests run)</h2>
 * Population: the golden entries; scoring rule: {@code Double.doubleToRawLongBits(provider(x))} equals the golden bits, no tolerance.
 * <ol>
 *   <li><b>B2-G1.</b> The provider path, the accurate path alone (in its domain) and the fast path alone (where it decides) have 0 mismatches on every golden entry.</li>
 *   <li><b>B2-G2.</b> The header carries the MIT permission text of the {@code .wc} source, a 32-hex source md5, the source commit, the generator name and the selection rule, and the
 *       header's {@code entries} count equals the number of data lines; the golden has at least 2,100 entries (2,000 first, 100 pole-block entries, extreme-exponent entries).</li>
 *   <li><b>B2-G3 control.</b> With the expected bits of every 100th entry moved by one ulp, the same comparison rejects exactly ceil(N/100) entries and no other.</li>
 *   <li><b>B2-G4 coverage.</b> The golden contains both exception inputs of {@code tan_accurate} ({@code 0x1.dffffffffff1fp-22} and {@code 0x1.dfffffffffc7cp-21}) IF the first 2,000 entries or the
 *       extreme-exponent rule reach them, and at least 90 of the 100 pole-block entries have an exponent above 2^40 (the block is {@code |x|} in [1, 2^1024), dominated by large ones).
 *       ELSE-BRANCH: the exception inputs are in the slow tier's every-entry run; their absence from the golden is a selection fact, not a defect.</li>
 * </ol>
 * ELSE-BRANCH (G1): a mismatch is a defect of the port or of the oracle; reported with the input and both outputs; the comparison is not loosened.
 *
 * <h2>SEALED PREDICTION B2F-G6b (fix pass; written 2026-11-12 before the first run of {@code guardDelegationsOverTheGolden}, after the slow tier showed that {@code tan.wc} makes the guard delegate)</h2>
 * Population: the golden entries (4,260; the first 2,000 of {@code tan.wc}, 2,160 at the extreme exponents, 100 of the pole block). Scoring rule: the production provider's guard delegations
 * ({@link CoreMath#tanGuardDelegations}) counted with the {@code Math.getExponent} of each delegated input. The slow tier found, on all 1,098,254 entries of {@code tan.wc}, 1,571 delegations
 * (0.14%), all with exponent -27..-17 (the tiny arguments, where R = |x|/(2 pi) is small, so the window 86 + 8/R is wide, and the entries are hard cases by selection). The golden holds
 * the first 2,000 entries, which are the entries at the smallest arguments: PREDICTED, between 900 and 1,571 delegations (at most the file's total), every one with exponent in [-27, -17], and
 * the provider's value equal to the golden for all 4,260 (0 mismatches). ELSE: a count outside the band, or an exponent outside the range, is reported as measured; the comparison is not loosened.
 * <p>
 * SCORING (appended 2026-11-12 after the first run, deviation D10; the sealed text above is unchanged): the count FAILED as sealed: 554 delegations of 4,260, below the predicted 900. The exponent
 * clause HELD (-27 (310, all the entries of tan.wc at that exponent), -25 (70), -24 (64), -23 (57), -22 (30), -21 (11), -20 (8), -19 (3), -17 (1)) and the 0 mismatches HELD. The premise was wrong: the
 * first 2,000 entries of tan.wc are not the entries at the smallest arguments (the golden has no entry at exponent -26, where tan.wc has 677 delegations of its 1,571). The test now asserts the
 * measured count exactly (deterministic: fixed file, fixed code).
 */
class CoreMathTanWorstCaseGoldenTest {

    static final String RESOURCE = "core-math-worst-cases-tan.txt";
    private static final Pattern ENTRIES = Pattern.compile("^# entries: (\\d+) \\(first (\\d+); extreme-exponent entries beyond those: (\\d+); pole-block entries: (\\d+)\\)");
    private static final Pattern MD5 = Pattern.compile("^# source-md5: ([0-9a-f]{32})\\s*$");

    private static final class Golden {
        final List<String> header = new ArrayList<>();
        double[] xs;
        double[] expected;
        int entries;
        int first;
        int extreme;
        int pole;
        String md5;
    }

    private static Golden load() throws IOException {
        Golden g = new Golden();
        List<Double> xs = new ArrayList<>();
        List<Long> bits = new ArrayList<>();
        try (InputStream in = CoreMathTanWorstCaseGoldenTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in, new StringBuilder(80).append("the committed golden resource ").append(RESOURCE).append(" is missing").toString());
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (line.startsWith("#")) {
                    g.header.add(line);
                    Matcher m = ENTRIES.matcher(line);
                    if (m.find()) {
                        g.entries = Integer.parseInt(m.group(1));
                        g.first = Integer.parseInt(m.group(2));
                        g.extreme = Integer.parseInt(m.group(3));
                        g.pole = Integer.parseInt(m.group(4));
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

    @Test
    void portAgreesWithTheGoldenWorstCases() throws IOException {
        Golden g = load();
        assertEquals(g.xs.length, g.entries, "the header's entries count differs from the data lines");
        assertEquals(2000, g.first);
        assertEquals(g.first + g.extreme + g.pole, g.xs.length);
        System.out.println(new StringBuilder(120).append("golden worst cases: tan ").append(g.xs.length).append(" entries (").append(g.first).append(" first + ").append(g.extreme)
                .append(" at extreme exponents + ").append(g.pole).append(" pole-block)"));
        CoreMathTanTestSupport.checkUnary("golden worst cases (committed)", g.xs, g.expected);
    }

    @Test
    void guardDelegationsOverTheGolden() throws IOException {
        Golden g = load();
        java.util.TreeMap<Integer, Long> byExponent = new java.util.TreeMap<>();
        CountingDelegate delegate = new CountingDelegate();
        CoreMath provider = new CoreMath(delegate, CoreMathAtan2::atan2, x -> {
            double r = CoreMathTan.tan(x);
            if (r != r) {
                byExponent.merge(Math.getExponent(x), 1L, Long::sum);
            }
            return r;
        });
        long mismatches = 0;
        for (int i = 0; i < g.xs.length; i++) {
            if (Double.doubleToRawLongBits(provider.tan(g.xs[i])) != Double.doubleToRawLongBits(g.expected[i])) {
                mismatches++;
            }
        }
        long delegated = provider.tanGuardDelegations();
        System.out.println(new StringBuilder(200).append("B2F-G6b golden: the guard delegated ").append(delegated).append(" of ").append(g.xs.length).append(" production calls, ").append(mismatches)
                .append(" mismatches; by Math.getExponent(x): ").append(byExponent));
        assertEquals(0L, mismatches);
        assertEquals(delegated, delegate.tan.sum());
        assertEquals(554L, delegated, "B2F-G6b / D10: the guard's delegations over the golden");
        assertTrue(byExponent.firstKey() >= -27 && byExponent.lastKey() <= -17, "B2F-G6b: exponents " + byExponent);
    }

    @Test
    void goldenHeaderCarriesTheLicenceAndTheProvenance() throws IOException {
        Golden g = load();
        StringBuilder all = new StringBuilder(2048);
        for (String h : g.header) {
            all.append(h).append('\n');
        }
        String text = all.toString();
        assertTrue(text.contains("Permission is hereby granted, free of charge"), "the MIT permission text is missing");
        assertTrue(text.contains("THE SOFTWARE IS PROVIDED \"AS IS\""), "the MIT warranty text is missing");
        assertNotNull(g.md5, "no source md5");
        assertTrue(text.contains("018fa97cbdacf59924b899a9dfa422d532cb2bb7"), "the source commit is not named");
        assertTrue(text.contains("CoreMathGoldenGeneratorTan"), "the generator is not named");
        assertTrue(text.contains("# selection:"), "the selection rule is not stated");
        assertTrue(g.xs.length >= 2100, "fewer than 2100 entries");
    }

    @Test
    void coverageOfTheExceptionInputsAndThePoleBlock() throws IOException {
        Golden g = load();
        boolean e1 = false;
        boolean e2 = false;
        int largePole = 0;
        for (int i = 0; i < g.xs.length; i++) {
            double a = Math.abs(g.xs[i]);
            e1 |= a == CoreMathTanTables.exceptions(0);
            e2 |= a == CoreMathTanTables.exceptions(3);
            if (i >= g.xs.length - g.pole && Math.getExponent(g.xs[i]) > 40) {
                largePole++;
            }
        }
        System.out.println(new StringBuilder(120).append("golden coverage: exception input 1 present ").append(e1).append(", exception input 2 present ").append(e2).append("; pole-block entries above 2^40: ")
                .append(largePole).append(" of ").append(g.pole));
        assertTrue(largePole >= 90);
    }

    /** The control: the comparison must be able to fail. Expected bits moved by one ulp on every 100th entry are found. */
    @Test
    void controlExpectedBitsMovedOnEvery100thEntryAreRejected() throws IOException {
        Golden g = load();
        double[] moved = g.expected.clone();
        int expectedRejections = 0;
        for (int i = 0; i < moved.length; i += 100) {
            moved[i] = Math.nextUp(moved[i]);
            expectedRejections++;
        }
        CoreMathTestSupport.Diff d = CoreMathTestSupport.compare("tan golden control", g.xs, moved, CoreMathTestSupport.PROVIDER::tan, x -> true, false, true);
        System.out.println(new StringBuilder(120).append("golden control (tan): rejected ").append(d.mismatches).append(" of ").append(d.applicable).append(" with the expected bits moved on every 100th entry"));
        assertEquals(expectedRejections, d.mismatches, d.details());
        assertEquals(g.xs.length, d.applicable);
    }
}
