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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Checks that every constant in {@link CoreMathTables} is bit-for-bit the constant in the CORE-MATH
 * C sources ({@code sin.c} and {@code cos.c}, which carries its own copy of the same tables). The C
 * text is re-read here by a different mechanism (regular expressions in Java) from the one that
 * produced the Java tables (a {@code sed} transcription): a transcription error would have to be
 * made twice, identically, to pass. Runtime tier: FAST (default suite). It is SKIPPED, loudly,
 * on a machine without the read-only CORE-MATH clone ({@link CoreMathTestSupport#CORE_MATH_DIR}).
 * <p>
 * Predictions are P9 of {@code CoreMathSinCosDifferentialTest}'s javadoc, sealed before the first run.
 */
class CoreMathConstantsTest {

    /** Number of values compared per C file: 20 + 512 + 512 + 4 + 5 + 33 + 64 + 64 + 2. */
    private static final int EXPECTED_VALUES = 1216;

    private static final Pattern U128_ENTRY = Pattern.compile("U128\\(\\s*(0x[0-9a-fA-F]+|0)\\s*,\\s*(0x[0-9a-fA-F]+|0)\\s*\\)");
    private static final Pattern SCALAR = Pattern.compile("static const double (pih|pil) = (-?0x[0-9a-fA-F.]+p[-+]?[0-9]+);");

    private static String cat(Object... parts) {
        StringBuilder sb = new StringBuilder(96);
        for (Object p : parts) {
            sb.append(p);
        }
        return sb.toString();
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
        throw new AssertionError(cat("table not found in the C source (the C text changed?): ", header));
    }

    /** Compares one C file's tables to the Java tables; returns {mismatches, compared}. */
    static long[] compareFile(Path c, long[] jT, double[] jU1, double[] jU2, U128[] jPS, U128[] jPC,
                              U128[] jS1, U128[] jS2, U128[] jC2, double jPih, double jPil) throws IOException {
        List<String> lines = Files.readAllLines(c, StandardCharsets.UTF_8);
        long bad = 0;
        long n = 0;

        List<Long> t = new ArrayList<>();
        for (String l : block(lines, "static const uint64_t _T[20]")) {
            String s = l.trim();
            if (s.endsWith(",")) {
                t.add(unsignedHex(s.substring(0, s.length() - 1).trim()));
            }
        }
        n += Math.max(t.size(), jT.length);
        bad += Math.abs(t.size() - jT.length);
        for (int i = 0; i < Math.min(t.size(), jT.length); i++) {
            if (t.get(i) != jT[i]) {
                bad++;
            }
        }

        double[][] jd = {jU1, jU2};
        String[] dh = {"static const double U1[128][4]", "static const double U2[128][4]"};
        for (int k = 0; k < 2; k++) {
            List<Double> d = new ArrayList<>();
            for (String l : block(lines, dh[k])) {
                String s = l.trim();
                if (s.startsWith("{")) {
                    for (String f : s.substring(1, s.indexOf('}')).split(",")) {
                        d.add(Double.parseDouble(f.trim()));
                    }
                }
            }
            n += Math.max(d.size(), jd[k].length);
            bad += Math.abs(d.size() - jd[k].length);
            for (int i = 0; i < Math.min(d.size(), jd[k].length); i++) {
                if (Double.doubleToRawLongBits(d.get(i)) != Double.doubleToRawLongBits(jd[k][i])) {
                    bad++;
                }
            }
        }

        U128[][] ju = {jPS, jPC, jS1, jS2, jC2};
        String[] uh = {"static const u128 PS[]", "static const u128 PC[]", "static const u128 S1u[33]",
                       "static const u128 S2u[64]", "static const u128 C2u[64]"};
        for (int k = 0; k < ju.length; k++) {
            List<U128> u = new ArrayList<>();
            for (String l : block(lines, uh[k])) {
                Matcher m = U128_ENTRY.matcher(l);
                if (m.find()) {
                    u.add(U128.lh(unsignedHex(m.group(1)), unsignedHex(m.group(2))));
                }
            }
            n += Math.max(u.size(), ju[k].length);
            bad += Math.abs(u.size() - ju[k].length);
            for (int i = 0; i < Math.min(u.size(), ju[k].length); i++) {
                if (!u.get(i).equals(ju[k][i])) {
                    bad++;
                }
            }
        }

        double pih = Double.NaN;
        double pil = Double.NaN;
        for (String l : lines) {
            Matcher m = SCALAR.matcher(l);
            if (m.find()) {
                if (m.group(1).equals("pih")) {
                    pih = Double.parseDouble(m.group(2));
                } else {
                    pil = Double.parseDouble(m.group(2));
                }
            }
        }
        n += 2;
        if (Double.doubleToRawLongBits(pih) != Double.doubleToRawLongBits(jPih)) {
            bad++;
        }
        if (Double.doubleToRawLongBits(pil) != Double.doubleToRawLongBits(jPil)) {
            bad++;
        }
        return new long[] {bad, n};
    }

    private static long[] compareProduction(Path c) throws IOException {
        return compareFile(c, CoreMathTables.T, CoreMathTables.U1, CoreMathTables.U2, CoreMathTables.PS, CoreMathTables.PC,
                CoreMathTables.S1U, CoreMathTables.S2U, CoreMathTables.C2U, CoreMathTables.PIH, CoreMathTables.PIL);
    }

    @Test
    void javaTablesEqualTheCSourcesBitForBit() throws IOException {
        Path sin = CoreMathTestSupport.CORE_MATH_DIR.resolve("src/binary64/sin/sin.c");
        Path cos = CoreMathTestSupport.CORE_MATH_DIR.resolve("src/binary64/cos/cos.c");
        Assumptions.assumeTrue(Files.isReadable(sin) && Files.isReadable(cos),
                cat("SKIPPED: the CORE-MATH clone is not at ", CoreMathTestSupport.CORE_MATH_DIR));
        long[] s = compareProduction(sin);
        long[] c = compareProduction(cos);
        StringBuilder sb = new StringBuilder(160);
        sb.append("CoreMathTables vs sin.c: mismatches ").append(s[0]).append(" of ").append(s[1])
                .append("; vs cos.c: mismatches ").append(c[0]).append(" of ").append(c[1]);
        System.out.println(sb);
        assertEquals(EXPECTED_VALUES, s[1], cat("the parser found a different number of values in sin.c than expected: ", sb));
        assertEquals(EXPECTED_VALUES, c[1], cat("the parser found a different number of values in cos.c than expected: ", sb));
        assertEquals(0L, s[0] + c[0], sb.toString());
    }

    /** The control: the comparison must be able to fail. One flipped bit in a copy of U1 is found. */
    @Test
    void controlOneFlippedTableBitIsRejected() throws IOException {
        Path sin = CoreMathTestSupport.CORE_MATH_DIR.resolve("src/binary64/sin/sin.c");
        Assumptions.assumeTrue(Files.isReadable(sin), cat("SKIPPED: the CORE-MATH clone is not at ", CoreMathTestSupport.CORE_MATH_DIR));
        double[] u1 = CoreMathTables.U1.clone();
        int at = 4 * 77 + 2;
        u1[at] = Double.longBitsToDouble(Double.doubleToRawLongBits(u1[at]) ^ 1L);
        long[] r = compareFile(sin, CoreMathTables.T, u1, CoreMathTables.U2, CoreMathTables.PS, CoreMathTables.PC,
                CoreMathTables.S1U, CoreMathTables.S2U, CoreMathTables.C2U, CoreMathTables.PIH, CoreMathTables.PIL);
        System.out.println(new StringBuilder(80).append("CoreMathTables control (one U1 bit flipped) rejected ").append(r[0]).append(" of ").append(r[1]));
        assertTrue(r[0] == 1L, cat("the comparison must reject exactly the one flipped value, got ", r[0]));
    }
}
