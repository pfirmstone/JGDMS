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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * HARNESS, not a test (phase B2): writes the committed resources that {@code CoreMathTanTablesDigestTest} and {@code CoreMathTanWorstCaseGoldenTest} read. Tagged
 * {@code harness}, so it is excluded from the default suite; it needs the read-only CORE-MATH clone and an output directory:
 * <pre>
 * mvn ... -pl jgdms-cel test -Dgroups=harness -Djgdms.cel.excludedGroups=none-such \
 *     -Dtest=CoreMathGoldenGeneratorTan -Dgolden.out.dir=&lt;the test resources directory of this package&gt;
 * </pre>
 * It REFUSES to overwrite an existing file (an accepted artefact is never regenerated; a new generation goes to a new file). The expected results in the golden file are computed
 * by {@link CrMath}, which is independent of CORE-MATH; the digest is written only if the Java tables equal the tables parsed from {@code tan.c} and every Java constant equals every
 * place where {@code tan.c} writes it.
 * <p>
 * <b>Selection rule of the golden</b> (stated in its header): the first 2,000 finite entries of {@code tan.wc}, then every later entry whose binary exponent
 * ({@code Math.getExponent}; -1023 = subnormal) is one of the 3 lowest or the 2 highest distinct exponents present among the finite entries ("extreme-exponent entries"), then,
 * from the file's final block (the inputs in [1, 2^1024) within 2^-48 of a multiple of pi/2, which the file announces by a comment), the 50 entries with the largest and the 50
 * with the smallest {@code |tan(x)|} (by {@link CrMath}) that are not already chosen.
 */
@Tag("harness")
class CoreMathGoldenGeneratorTan {

    private static final String COMMIT = "018fa97cbdacf59924b899a9dfa422d532cb2bb7";
    private static final int FIRST = 2000;
    private static final int POLE_EACH = 50;

    private static Path outDir() {
        String d = System.getProperty("golden.out.dir");
        if (d == null || d.isEmpty()) {
            fail("-Dgolden.out.dir=<directory> is required");
        }
        return Path.of(d);
    }

    private static void write(Path file, String text) throws IOException {
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            w.write(text);
        }
    }

    @Test
    void generateTablesAndConstantsDigest() throws IOException {
        CoreMathTestSupport.requireClone(CoreMathTestSupport.CORE_MATH_DIR, true, "CoreMathGoldenGeneratorTan.generateTablesAndConstantsDigest", "tan");
        Map<String, long[]> all = CoreMathTanTables.snapshot();
        Map<String, long[]> tablesC = CoreMathTanTestSupport.parseFromC(CoreMathTestSupport.CORE_MATH_DIR);
        for (Map.Entry<String, long[]> e : tablesC.entrySet()) {
            String name = e.getKey().substring(e.getKey().indexOf(':') + 1);
            assertArrayEquals(all.get(name), e.getValue(), new StringBuilder(80).append("the Java table ").append(name).append(" differs from the C text of ").append(e.getKey()).append(": nothing written").toString());
        }
        Map<String, long[]> constantsC = CoreMathTanTestSupport.constantsFromC(CoreMathTestSupport.CORE_MATH_DIR);
        for (Map.Entry<String, long[]> e : constantsC.entrySet()) {
            String name = new StringBuilder(40).append(CoreMathInverseTrigTestSupport.CONSTANT_PREFIX).append(e.getKey().substring(e.getKey().indexOf(':') + 1, e.getKey().indexOf('@'))).toString();
            assertArrayEquals(all.get(name), e.getValue(), new StringBuilder(80).append("the Java constant ").append(name).append(" differs from the C text at ").append(e.getKey()).append(": nothing written").toString());
        }
        String digest = CoreMathInverseTrigTestSupport.sha256(CoreMathTanTestSupport.CANONICAL_HEADER, all);
        StringBuilder sb = new StringBuilder(800);
        sb.append("# SHA-256 of the canonical rendering (v1, CoreMathInverseTrigTestSupport.canonical with the header \"").append(CoreMathTanTestSupport.CANONICAL_HEADER)
                .append("\") of every entry of CoreMathTanTables.snapshot():\n");
        sb.append("# the 13 tables of tan.c followed by the 9 named scalar constants (keys K_...).\n");
        sb.append("# source: CORE-MATH src/binary64/tan/tan.c at commit ").append(COMMIT).append(".\n");
        sb.append("# generator: CoreMathGoldenGeneratorTan.generateTablesAndConstantsDigest, ").append(LocalDate.now()).append(" (system clock); the Java tables equalled the tables parsed from tan.c\n");
        sb.append("# (").append(tablesC.size()).append(" tables) and every Java constant equalled the C text at all ").append(constantsC.size()).append(" places where the C writes one, when this file was written.\n");
        sb.append(digest).append('\n');
        write(outDir().resolve(CoreMathTanTablesDigestTest.RESOURCE), sb.toString());
        System.out.println(new StringBuilder(120).append("digest ").append(digest).append(" over ").append(all.size()).append(" entries; ").append(tablesC.size()).append(" tables and ")
                .append(constantsC.size()).append(" constant places compared with the C text"));
    }

    @Test
    void generateWorstCaseGolden() throws IOException {
        CoreMathTestSupport.requireClone(CoreMathTestSupport.CORE_MATH_DIR, true, "CoreMathGoldenGeneratorTan.generateWorstCaseGolden", "tan");
        Path wc = CoreMathTestSupport.wcFile("tan");
        CoreMathTanTestSupport.WcTan data = CoreMathTanTestSupport.readWc(wc);
        double[] all = data.values;
        TreeSet<Integer> exps = new TreeSet<>();
        for (double x : all) {
            exps.add(Math.getExponent(x));
        }
        TreeSet<Integer> low = new TreeSet<>();
        TreeSet<Integer> high = new TreeSet<>();
        int k = 0;
        for (int e : exps) {
            if (k++ < 3) {
                low.add(e);
            }
        }
        k = 0;
        for (int e : exps.descendingSet()) {
            if (k++ < 2) {
                high.add(e);
            }
        }
        boolean[] chosen = new boolean[all.length];
        int first = 0;
        int extreme = 0;
        for (int i = 0; i < all.length; i++) {
            if (i < FIRST) {
                chosen[i] = true;
                first++;
            } else {
                int e = Math.getExponent(all[i]);
                if (low.contains(e) || high.contains(e)) {
                    chosen[i] = true;
                    extreme++;
                }
            }
        }
        // the pole block: the 50 largest and the 50 smallest |tan| not already chosen
        int start = data.poleBlockStart;
        if (start < 0) {
            fail("the pole block was not found in tan.wc");
        }
        int blockLen = all.length - start;
        double[] blockXs = Arrays.copyOfRange(all, start, all.length);
        double[] blockTan = CoreMathTanTestSupport.oracle(blockXs);
        Integer[] order = new Integer[blockLen];
        for (int i = 0; i < blockLen; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            int c = Double.compare(Math.abs(blockTan[a]), Math.abs(blockTan[b]));
            return c != 0 ? c : Integer.compare(a, b);
        });
        int pole = 0;
        int taken = 0;
        for (int j = 0; j < blockLen && taken < POLE_EACH; j++) {
            int idx = start + order[j];
            if (!chosen[idx]) {
                chosen[idx] = true;
                pole++;
                taken++;
            }
        }
        taken = 0;
        for (int j = blockLen - 1; j >= 0 && taken < POLE_EACH; j--) {
            int idx = start + order[j];
            if (!chosen[idx]) {
                chosen[idx] = true;
                pole++;
                taken++;
            }
        }
        List<Double> xsList = new ArrayList<>();
        for (int i = 0; i < all.length; i++) {
            if (chosen[i]) {
                xsList.add(all[i]);
            }
        }
        double[] xs = new double[xsList.size()];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = xsList.get(i);
        }
        double[] expected = CoreMathTanTestSupport.oracle(xs);
        StringBuilder sb = new StringBuilder(xs.length * 40 + 4096);
        for (String l : Files.readAllLines(wc, StandardCharsets.UTF_8)) {
            if (!l.startsWith("#")) {
                break;
            }
            sb.append(l).append('\n');
        }
        String md5 = CoreMathTestSupport.digestHex("MD5", Files.readAllBytes(wc));
        sb.append("#\n");
        sb.append("# GOLDEN SUBSET of CORE-MATH's tan.wc (the notice above is that file's own; the entries below are data taken from it).\n");
        sb.append("# generator: CoreMathGoldenGeneratorTan.generateWorstCaseGolden (jgdms-cel test tree), ").append(LocalDate.now()).append(" (system clock)\n");
        sb.append("# source: src/binary64/tan/tan.wc of CORE-MATH at commit ").append(COMMIT).append('\n');
        sb.append("# source-md5: ").append(md5).append('\n');
        sb.append("# selection: the first ").append(FIRST).append(" finite entries, then every later entry whose binary exponent (Math.getExponent; -1023 = subnormal) is one of the 3 lowest distinct exponents {")
                .append(join(low)).append("} or the 2 highest {").append(join(high)).append("} present among the finite entries, then from the final block of the file (the inputs in [1, 2^1024) within 2^-48 of a multiple of pi/2: ")
                .append(blockLen).append(" entries) the ").append(POLE_EACH).append(" with the largest and the ").append(POLE_EACH).append(" with the smallest |tan(x)| (by CrMath) not already chosen\n");
        sb.append("# line format: <input, hexadecimal floating point> <expected result: 16 hex digits, the raw bits of the correctly rounded tan(input), computed by CrMath (BigDecimal, Ziv; independent of CORE-MATH) at generation time>\n");
        sb.append("# entries: ").append(xs.length).append(" (first ").append(first).append("; extreme-exponent entries beyond those: ").append(extreme).append("; pole-block entries: ").append(pole).append(")\n");
        for (int i = 0; i < xs.length; i++) {
            sb.append(Double.toHexString(xs[i])).append(' ').append(CoreMathTestSupport.hex16(Double.doubleToRawLongBits(expected[i]))).append('\n');
        }
        System.out.println(new StringBuilder(200).append("tan.wc: ").append(all.length).append(" finite entries read, ").append(xs.length).append(" chosen (").append(first).append(" first + ").append(extreme)
                .append(" extreme-exponent [low {").append(join(low)).append("}, high {").append(join(high)).append("}] + ").append(pole).append(" pole-block of ").append(blockLen).append(")"));
        write(outDir().resolve(CoreMathTanWorstCaseGoldenTest.RESOURCE), sb.toString());
    }

    private static String join(TreeSet<Integer> s) {
        StringBuilder sb = new StringBuilder(32);
        for (int v : s) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(v);
        }
        return sb.toString();
    }
}
