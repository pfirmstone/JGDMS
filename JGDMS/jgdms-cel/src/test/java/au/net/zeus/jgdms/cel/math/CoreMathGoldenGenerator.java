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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * HARNESS, not a test: writes the committed resources that {@code CoreMathTablesDigestTest} and
 * {@code CoreMathWorstCaseGoldenTest} read. Tagged {@code harness}, so it is excluded from the default
 * suite; it needs the read-only CORE-MATH clone and an output directory:
 * <pre>
 * mvn ... -pl jgdms-cel test -Dgroups=harness -Djgdms.cel.excludedGroups=none-such \
 *     -Dtest=CoreMathGoldenGenerator -Dgolden.out.dir=&lt;the test resources directory of this package&gt;
 * </pre>
 * It REFUSES to overwrite an existing file (an accepted artefact is never regenerated; a new generation
 * goes to a new file). The expected results in the golden files are computed by {@link CrMath}, which is
 * independent of CORE-MATH; the digest is written only if the Java tables, {@code sin.c}'s tables and
 * {@code cos.c}'s tables all give the same digest.
 */
@Tag("harness")
class CoreMathGoldenGenerator {

    private static final String COMMIT = "018fa97cbdacf59924b899a9dfa422d532cb2bb7";

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
        CoreMathTestSupport.requireClone(CoreMathTestSupport.CORE_MATH_DIR, true, "CoreMathGoldenGenerator.generateTablesDigest");
        String java = CoreMathTestSupport.TableSet.fromJava().sha256();
        String sin = CoreMathTestSupport.TableSet.fromC(CoreMathTestSupport.cSource(CoreMathTestSupport.CORE_MATH_DIR, "sin")).sha256();
        String cos = CoreMathTestSupport.TableSet.fromC(CoreMathTestSupport.cSource(CoreMathTestSupport.CORE_MATH_DIR, "cos")).sha256();
        System.out.println(new StringBuilder(240).append("digest of the Java tables ").append(java).append("; of sin.c's tables ").append(sin)
                .append("; of cos.c's tables ").append(cos));
        assertEquals(java, sin, "the Java tables and sin.c's tables give different digests: nothing written");
        assertEquals(java, cos, "the Java tables and cos.c's tables give different digests: nothing written");
        StringBuilder sb = new StringBuilder(640);
        sb.append("# SHA-256 of the canonical rendering (v1, see CoreMathTestSupport.TableSet.canonical) of every table of CoreMathTables.\n");
        sb.append("# source: CORE-MATH src/binary64/sin/sin.c at commit ").append(COMMIT).append("; the tables are identical in cos.c.\n");
        sb.append("# generator: CoreMathGoldenGenerator.generateTablesDigest, ").append(LocalDate.now()).append(" (system clock); the digest of the Java tables,\n");
        sb.append("# of the tables parsed from sin.c and of those parsed from cos.c were equal when this file was written.\n");
        sb.append(java).append('\n');
        write(outDir().resolve("core-math-tables.sha256"), sb.toString());
    }

    @Test
    void generateWorstCaseGoldens() throws IOException {
        CoreMathTestSupport.requireClone(CoreMathTestSupport.CORE_MATH_DIR, true, "CoreMathGoldenGenerator.generateWorstCaseGoldens");
        for (String f : new String[] {"sin", "cos"}) {
            Path wc = CoreMathTestSupport.wcFile(f);
            CoreMathTestSupport.WcData data = CoreMathTestSupport.readWc(wc, 0);
            List<String> lines = Files.readAllLines(wc, StandardCharsets.UTF_8);
            List<String> header = new ArrayList<>();
            for (String l : lines) {
                if (!l.startsWith("#")) {
                    break;
                }
                header.add(l);
            }
            List<Double> chosen = new ArrayList<>();
            int top = 0;
            for (int i = 0; i < data.values.length; i++) {
                if (i < 2000) {
                    chosen.add(data.values[i]);
                } else if (Math.getExponent(data.values[i]) == 1023) {
                    chosen.add(data.values[i]);
                    top++;
                }
            }
            double[] xs = new double[chosen.size()];
            for (int i = 0; i < xs.length; i++) {
                xs[i] = chosen.get(i);
            }
            double[] expected = CoreMathTestSupport.oracleValues(xs, f.equals("sin") ? CoreMathTestSupport.ORACLE::sin : CoreMathTestSupport.ORACLE::cos);
            String md5 = CoreMathTestSupport.digestHex("MD5", Files.readAllBytes(wc));

            StringBuilder sb = new StringBuilder(xs.length * 40 + 4096);
            for (String h : header) {
                sb.append(h).append('\n');
            }
            sb.append("#\n");
            sb.append("# GOLDEN SUBSET of CORE-MATH's ").append(f).append(".wc (the notice above is that file's own; the entries below are data taken from it).\n");
            sb.append("# generator: CoreMathGoldenGenerator.generateWorstCaseGoldens (jgdms-cel test tree), ").append(LocalDate.now()).append(" (system clock)\n");
            sb.append("# source: src/binary64/").append(f).append('/').append(f).append(".wc of CORE-MATH at commit ").append(COMMIT).append('\n');
            sb.append("# source-md5: ").append(md5).append('\n');
            sb.append("# selection: the first 2000 finite entries, then every later finite entry whose binary exponent is 1023 (written p+1023)\n");
            sb.append("# line format: <input, hexadecimal floating point> <expected result: 16 hex digits, the raw bits of the correctly rounded ")
                    .append(f).append("(input), computed by CrMath (BigDecimal, Ziv; independent of CORE-MATH) at generation time>\n");
            sb.append("# entries: ").append(xs.length).append(" (first 2000; exponent 1023 beyond those: ").append(top).append(")\n");
            for (int i = 0; i < xs.length; i++) {
                sb.append(Double.toHexString(xs[i])).append(' ').append(CoreMathTestSupport.hex16(Double.doubleToRawLongBits(expected[i]))).append('\n');
            }
            System.out.println(new StringBuilder(160).append(f).append(".wc: ").append(data.values.length).append(" finite entries read, ")
                    .append(xs.length).append(" chosen (2000 + ").append(top).append(" at exponent 1023), source md5 ").append(md5));
            write(outDir().resolve(new StringBuilder(40).append("core-math-worst-cases-").append(f).append(".txt").toString()), sb.toString());
        }
    }
}
