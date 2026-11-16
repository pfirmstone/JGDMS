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
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * HARNESS, not a test (phase B1): writes the committed resources that {@code CoreMathInverseTrigTablesDigestTest} and
 * {@code CoreMathInverseTrigWorstCaseGoldenTest} read. Tagged {@code harness}, so it is excluded from the default suite; it needs the
 * read-only CORE-MATH clone and an output directory:
 * <pre>
 * mvn ... -pl jgdms-cel test -Dgroups=harness -Djgdms.cel.excludedGroups=none-such \
 *     -Dtest=CoreMathGoldenGeneratorB1 -Dgolden.out.dir=&lt;the test resources directory of this package&gt;
 * </pre>
 * It REFUSES to overwrite an existing file (an accepted artefact is never regenerated; a new generation goes to a new file). The
 * expected results in the golden files are computed by {@link CrMath}, which is independent of CORE-MATH; the digest is written only if
 * the Java tables equal the tables parsed from every C file that carries them.
 * <p>
 * <b>Selection rule of the goldens</b> (stated in every header): the first 2,000 finite entries of the {@code .wc} file that the provider
 * accepts (for asin and acos, {@code |x| <= 1}), then every later such entry whose binary exponent (for atan2, the exponent of either
 * argument) is one of the 3 lowest or the 2 highest distinct exponents present among the file's accepted entries ("extreme-exponent
 * entries": the smallest subnormals and tiniest values, the largest values, and for asin and acos the entries nearest 1).
 * {@code Math.getExponent} is used (it returns -1023 for subnormals).
 */
@Tag("harness")
class CoreMathGoldenGeneratorB1 {

    private static final String COMMIT = "018fa97cbdacf59924b899a9dfa422d532cb2bb7";
    private static final int FIRST = 2000;

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
    void generateTablesDigest() throws IOException {
        CoreMathTestSupport.requireClone(CoreMathTestSupport.CORE_MATH_DIR, true, "CoreMathGoldenGeneratorB1.generateTablesDigest", "atan", "asin", "acos", "atan2");
        Map<String, long[]> java = CoreMathInverseTrigTestSupport.tablesOnly(CoreMathInverseTrigTables.snapshot());
        Map<String, long[]> c = CoreMathInverseTrigTestSupport.parseFromC(CoreMathTestSupport.CORE_MATH_DIR);
        for (Map.Entry<String, long[]> e : c.entrySet()) {
            String name = e.getKey().substring(e.getKey().indexOf(':') + 1);
            assertArrayEquals(java.get(name), e.getValue(), new StringBuilder(80).append("the Java table ").append(name).append(" differs from the C text of ").append(e.getKey()).append(": nothing written").toString());
        }
        String digest = CoreMathInverseTrigTestSupport.sha256(java);
        StringBuilder sb = new StringBuilder(640);
        sb.append("# SHA-256 of the canonical rendering (v1, see CoreMathInverseTrigTestSupport.canonical) of every table of CoreMathInverseTrigTables.\n");
        sb.append("# source: CORE-MATH src/binary64/{atan/atan.c, asin/asin.c, acos/acos.c, atan2/atan2.c, atan2/tint.h} at commit ").append(COMMIT).append(".\n");
        sb.append("# generator: CoreMathGoldenGeneratorB1.generateTablesDigest, ").append(LocalDate.now()).append(" (system clock); the Java tables equalled the tables parsed from every C file that carries them\n");
        sb.append("# (").append(c.size()).append(" table-file pairs) when this file was written.\n");
        sb.append(digest).append('\n');
        write(outDir().resolve(CoreMathInverseTrigTablesDigestTest.RESOURCE), sb.toString());
        System.out.println(new StringBuilder(120).append("digest ").append(digest).append(" over ").append(java.size()).append(" tables, ").append(c.size()).append(" table-file pairs compared with the C text"));
    }

    /**
     * Fix pass: the digest of the 26 tables followed by the 31 named scalar constants, in a NEW resource (the phase B1 digest of the 26 tables is an
     * accepted artefact and is not regenerated). Written only if the Java tables equal the tables parsed from the C text and every Java constant equals
     * every place where the C text writes it (42 comparisons).
     */
    @Test
    void generateTablesAndConstantsDigest() throws IOException {
        CoreMathTestSupport.requireClone(CoreMathTestSupport.CORE_MATH_DIR, true, "CoreMathGoldenGeneratorB1.generateTablesAndConstantsDigest", "atan", "asin", "acos", "atan2");
        Map<String, long[]> all = CoreMathInverseTrigTables.snapshot();
        Map<String, long[]> tablesC = CoreMathInverseTrigTestSupport.parseFromC(CoreMathTestSupport.CORE_MATH_DIR);
        for (Map.Entry<String, long[]> e : tablesC.entrySet()) {
            String name = e.getKey().substring(e.getKey().indexOf(':') + 1);
            assertArrayEquals(all.get(name), e.getValue(), new StringBuilder(80).append("the Java table ").append(name).append(" differs from the C text of ").append(e.getKey()).append(": nothing written").toString());
        }
        Map<String, long[]> constantsC = CoreMathInverseTrigTestSupport.constantsFromC(CoreMathTestSupport.CORE_MATH_DIR);
        for (Map.Entry<String, long[]> e : constantsC.entrySet()) {
            String name = new StringBuilder(40).append(CoreMathInverseTrigTestSupport.CONSTANT_PREFIX).append(e.getKey().substring(e.getKey().indexOf(':') + 1, e.getKey().indexOf('@'))).toString();
            assertArrayEquals(all.get(name), e.getValue(), new StringBuilder(80).append("the Java constant ").append(name).append(" differs from the C text at ").append(e.getKey()).append(": nothing written").toString());
        }
        String digest = CoreMathInverseTrigTestSupport.sha256(all);
        StringBuilder sb = new StringBuilder(800);
        sb.append("# SHA-256 of the canonical rendering (v1, see CoreMathInverseTrigTestSupport.canonical) of every entry of CoreMathInverseTrigTables.snapshot():\n");
        sb.append("# the 26 tables of core-math-tables-b1-2026-11-12.sha256 followed by the 31 named scalar constants (keys K_...).\n");
        sb.append("# source: CORE-MATH src/binary64/{atan/atan.c, asin/asin.c, acos/acos.c, atan2/atan2.c, atan2/tint.h} at commit ").append(COMMIT).append(".\n");
        sb.append("# generator: CoreMathGoldenGeneratorB1.generateTablesAndConstantsDigest, ").append(LocalDate.now()).append(" (system clock); the Java tables equalled the tables parsed from every C file that carries them\n");
        sb.append("# (").append(tablesC.size()).append(" table-file pairs) and every Java constant equalled the C text at all ").append(constantsC.size()).append(" places where the C writes one, when this file was written.\n");
        sb.append(digest).append('\n');
        write(outDir().resolve(CoreMathInverseTrigTablesDigestTest.RESOURCE_V2), sb.toString());
        System.out.println(new StringBuilder(120).append("digest ").append(digest).append(" over ").append(all.size()).append(" entries; ").append(tablesC.size())
                .append(" table-file pairs and ").append(constantsC.size()).append(" constant places compared with the C text"));
    }

    private static List<String> header(Path wc) throws IOException {
        List<String> header = new ArrayList<>();
        for (String l : Files.readAllLines(wc, StandardCharsets.UTF_8)) {
            if (!l.startsWith("#")) {
                break;
            }
            header.add(l);
        }
        return header;
    }

    private static void writeHeader(StringBuilder sb, String function, Path wc, int total, int first, int extreme, boolean pairs, String lowSet, String highSet) throws IOException {
        for (String h : header(wc)) {
            sb.append(h).append('\n');
        }
        String md5 = CoreMathTestSupport.digestHex("MD5", Files.readAllBytes(wc));
        sb.append("#\n");
        sb.append("# GOLDEN SUBSET of CORE-MATH's ").append(function).append(".wc (the notice above is that file's own; the entries below are data taken from it).\n");
        sb.append("# generator: CoreMathGoldenGeneratorB1.generateWorstCaseGoldens (jgdms-cel test tree), ").append(LocalDate.now()).append(" (system clock)\n");
        sb.append("# source: src/binary64/").append(function).append('/').append(function).append(".wc of CORE-MATH at commit ").append(COMMIT).append('\n');
        sb.append("# source-md5: ").append(md5).append('\n');
        sb.append("# selection: the first ").append(FIRST).append(" finite entries that the provider accepts (").append(function.equals("atan") || function.equals("atan2") ? "every finite entry" : "|x| <= 1")
                .append("), then every later accepted entry whose binary exponent (").append(pairs ? "of either argument, " : "").append("Math.getExponent; -1023 = subnormal) is one of the 3 lowest distinct exponents {")
                .append(lowSet).append("} or the 2 highest {").append(highSet).append("} present among the accepted entries\n");
        sb.append("# line format: ").append(pairs ? "<y, hexadecimal floating point> <x, hexadecimal floating point> " : "<input, hexadecimal floating point> ")
                .append("<expected result: 16 hex digits, the raw bits of the correctly rounded ").append(function).append(pairs ? "(y, x)" : "(input)")
                .append(", computed by CrMath (BigDecimal, Ziv; independent of CORE-MATH) at generation time>\n");
        sb.append("# entries: ").append(total).append(" (first ").append(first).append("; extreme-exponent entries beyond those: ").append(extreme).append(")\n");
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

    @Test
    void generateWorstCaseGoldens() throws IOException {
        CoreMathTestSupport.requireClone(CoreMathTestSupport.CORE_MATH_DIR, true, "CoreMathGoldenGeneratorB1.generateWorstCaseGoldens", "atan", "asin", "acos", "atan2");
        for (String f : new String[] {"atan", "asin", "acos"}) {
            Path wc = CoreMathTestSupport.wcFile(f);
            double[] all = CoreMathInverseTrigTestSupport.readWcInDomain(f, 0).values;
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
            List<Double> chosen = new ArrayList<>();
            int extreme = 0;
            for (int i = 0; i < all.length; i++) {
                if (i < FIRST) {
                    chosen.add(all[i]);
                } else {
                    int e = Math.getExponent(all[i]);
                    if (low.contains(e) || high.contains(e)) {
                        chosen.add(all[i]);
                        extreme++;
                    }
                }
            }
            double[] xs = new double[chosen.size()];
            for (int i = 0; i < xs.length; i++) {
                xs[i] = chosen.get(i);
            }
            double[] expected = CoreMathTestSupport.oracleValues(xs, CoreMathInverseTrigTestSupport.oracle(f));
            StringBuilder sb = new StringBuilder(xs.length * 40 + 4096);
            writeHeader(sb, f, wc, xs.length, FIRST, extreme, false, join(low), join(high));
            for (int i = 0; i < xs.length; i++) {
                sb.append(Double.toHexString(xs[i])).append(' ').append(CoreMathTestSupport.hex16(Double.doubleToRawLongBits(expected[i]))).append('\n');
            }
            System.out.println(new StringBuilder(200).append(f).append(".wc: ").append(all.length).append(" accepted finite entries read, ").append(xs.length).append(" chosen (").append(FIRST)
                    .append(" + ").append(extreme).append(" extreme-exponent; low exponents {").append(join(low)).append("}, high {").append(join(high)).append("})"));
            write(outDir().resolve(new StringBuilder(40).append("core-math-worst-cases-").append(f).append(".txt").toString()), sb.toString());
        }
        // atan2: pairs
        Path wc = CoreMathTestSupport.wcFile("atan2");
        CoreMathInverseTrigTestSupport.WcPairs all = CoreMathInverseTrigTestSupport.readWcPairs(wc, 0);
        TreeSet<Integer> exps = new TreeSet<>();
        for (int i = 0; i < all.ys.length; i++) {
            exps.add(Math.getExponent(all.ys[i]));
            exps.add(Math.getExponent(all.xs[i]));
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
        List<double[]> chosen = new ArrayList<>();
        int extreme = 0;
        for (int i = 0; i < all.ys.length; i++) {
            if (all.ys[i] == 0.0 || all.xs[i] == 0.0) {
                continue;                                    // zero rows are covered by CoreMathInverseTrigDifferentialTest.p8SpecialInputs
            }
            if (chosen.size() < FIRST) {
                chosen.add(new double[] {all.ys[i], all.xs[i]});
            } else {
                int ey = Math.getExponent(all.ys[i]);
                int ex = Math.getExponent(all.xs[i]);
                if (low.contains(ey) || high.contains(ey) || low.contains(ex) || high.contains(ex)) {
                    chosen.add(new double[] {all.ys[i], all.xs[i]});
                    extreme++;
                }
            }
        }
        double[] ys = new double[chosen.size()];
        double[] xs = new double[chosen.size()];
        for (int i = 0; i < ys.length; i++) {
            ys[i] = chosen.get(i)[0];
            xs[i] = chosen.get(i)[1];
        }
        double[] expected = CoreMathInverseTrigTestSupport.oracleValues2(ys, xs, CoreMathTestSupport.ORACLE::atan2);
        StringBuilder sb = new StringBuilder(ys.length * 70 + 4096);
        writeHeader(sb, "atan2", wc, ys.length, FIRST, extreme, true, join(low), join(high));
        sb.append("# (pairs with a zero argument, if the file has any, are not in the golden: the zero rows are tested by name)\n");
        for (int i = 0; i < ys.length; i++) {
            sb.append(Double.toHexString(ys[i])).append(' ').append(Double.toHexString(xs[i])).append(' ').append(CoreMathTestSupport.hex16(Double.doubleToRawLongBits(expected[i]))).append('\n');
        }
        System.out.println(new StringBuilder(200).append("atan2.wc: ").append(all.ys.length).append(" finite pairs read, ").append(ys.length).append(" chosen (").append(FIRST)
                .append(" + ").append(extreme).append(" extreme-exponent; low exponents {").append(join(low)).append("}, high {").append(join(high)).append("})"));
        write(outDir().resolve("core-math-worst-cases-atan2.txt"), sb.toString());
    }
}
