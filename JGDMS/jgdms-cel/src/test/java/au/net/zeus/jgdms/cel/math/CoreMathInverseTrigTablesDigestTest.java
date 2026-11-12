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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The tables of the phase B1 ports ({@link CoreMathInverseTrigTables}) under two guards: a hermetic digest, and (with the CORE-MATH
 * clone) a bit-for-bit comparison with the C text. Runtime tier: FAST (default suite); the C comparison is skipped
 * without the clone and FAILS when the full tier is requested ({@code -Dcore.math.full=true}).
 * <p>
 * The digest is the SHA-256 of {@link CoreMathInverseTrigTestSupport#canonical}, committed as the test resource
 * {@code core-math-tables-b1-2026-11-12.sha256} (a new file; the phase A digest {@code core-math-tables.sha256} is not touched).
 * It is written by {@code CoreMathGoldenGeneratorB1.generateTablesDigest} (a harness) only if the digest of the Java tables equals the
 * digest of the tables parsed from every C file that carries them, so the committed digest is a digest of the C source's tables.
 *
 * <h2>SEALED PREDICTIONS (B1-P10, phase B1; written before the first run of these tests)</h2>
 * <ol>
 *   <li>The digest of the Java tables equals the committed digest, and the resource names the source commit
 *       {@code 018fa97cbdacf59924b899a9dfa422d532cb2bb7}.</li>
 *   <li><b>Control.</b> A single flipped bit in a copy of each table (the first element and the last) changes the digest: 26 of 26 tables
 *       rejected for each position. ELSE-BRANCH: fewer means the rendering omits something and the digest is blind to it.</li>
 *   <li><b>Encapsulation.</b> Writing into the snapshot does not change a later snapshot (0 of 26 writes visible).</li>
 *   <li><b>C text</b> (clone present): every table equals the C text of every file that carries it: the 22 tables of the four C files plus
 *       the four constants of {@code tint.h}, with the tables of {@code asin.c} and {@code acos.c} checked against both files, 0 mismatches
 *       over all values (the count is printed); every table of the snapshot has at least one C source (a table added without a check fails);
 *       the control (one flipped bit in a copy) is rejected exactly once.</li>
 * </ol>
 */
class CoreMathInverseTrigTablesDigestTest {

    static final String RESOURCE = "core-math-tables-b1-2026-11-12.sha256";

    private static String[] resourceLines() throws IOException {
        try (InputStream in = CoreMathInverseTrigTablesDigestTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in, new StringBuilder(80).append("the committed digest resource ").append(RESOURCE).append(" is missing").toString());
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R");
        }
    }

    private static String committedDigest() throws IOException {
        for (String l : resourceLines()) {
            String s = l.trim();
            if (!s.isEmpty() && !s.startsWith("#")) {
                return s;
            }
        }
        throw new AssertionError(new StringBuilder(64).append("no digest line in ").append(RESOURCE).toString());
    }

    @Test
    void digestOfTheJavaTablesEqualsTheCommittedDigest() throws IOException {
        String committed = committedDigest();
        assertEquals(64, committed.length(), "the committed digest is not 64 hex digits");
        boolean namesCommit = false;
        for (String l : resourceLines()) {
            if (l.startsWith("#") && l.contains("018fa97cbdacf59924b899a9dfa422d532cb2bb7")) {
                namesCommit = true;
            }
        }
        assertTrue(namesCommit, "the digest resource does not name the CORE-MATH source commit");
        String actual = CoreMathInverseTrigTestSupport.sha256(CoreMathInverseTrigTables.snapshot());
        System.out.println(new StringBuilder(160).append("tables digest (phase B1): Java ").append(actual).append(", committed ").append(committed));
        assertEquals(committed, actual, "a table of CoreMathInverseTrigTables differs from the committed digest");
    }

    @Test
    void controlOneFlippedBitInAnyTableChangesTheDigest() {
        String base = CoreMathInverseTrigTestSupport.sha256(CoreMathInverseTrigTables.snapshot());
        int tables = 0;
        int rejectedFirst = 0;
        int rejectedLast = 0;
        for (String name : CoreMathInverseTrigTables.snapshot().keySet()) {
            tables++;
            Map<String, long[]> first = CoreMathInverseTrigTables.snapshot();
            first.get(name)[0] ^= 1L;
            if (!base.equals(CoreMathInverseTrigTestSupport.sha256(first))) {
                rejectedFirst++;
            }
            Map<String, long[]> last = CoreMathInverseTrigTables.snapshot();
            long[] v = last.get(name);
            v[v.length - 1] ^= 1L << 17;
            if (!base.equals(CoreMathInverseTrigTestSupport.sha256(last))) {
                rejectedLast++;
            }
        }
        System.out.println(new StringBuilder(120).append("tables digest control: a flipped bit in the first element changed the digest for ").append(rejectedFirst)
                .append(" of ").append(tables).append(" tables, in the last element for ").append(rejectedLast).append(" of ").append(tables));
        assertEquals(26, tables);
        assertEquals(tables, rejectedFirst);
        assertEquals(tables, rejectedLast);
    }

    @Test
    void theSnapshotIsACopyAndWritesToItAreNotVisible() {
        String base = CoreMathInverseTrigTestSupport.sha256(CoreMathInverseTrigTables.snapshot());
        int tables = 0;
        int visible = 0;
        for (String name : CoreMathInverseTrigTables.snapshot().keySet()) {
            tables++;
            Map<String, long[]> s = CoreMathInverseTrigTables.snapshot();
            long[] v = s.get(name);
            for (int i = 0; i < v.length; i++) {
                v[i] = ~v[i];
            }
            assertNotEquals(base, CoreMathInverseTrigTestSupport.sha256(s), "the control write changed nothing: the snapshot is not what is hashed");
            if (!base.equals(CoreMathInverseTrigTestSupport.sha256(CoreMathInverseTrigTables.snapshot()))) {
                visible++;
            }
        }
        System.out.println(new StringBuilder(96).append("tables snapshot: writes into ").append(tables).append(" snapshot tables, visible in a later snapshot: ").append(visible));
        assertEquals(26, tables);
        assertEquals(0, visible, "a write into a snapshot reached a table");
    }

    @Test
    void everyTableEqualsTheCText() throws IOException {
        CoreMathTestSupport.requireClone(CoreMathTestSupport.CORE_MATH_DIR, CoreMathTestSupport.FULL_TIER, "the tables-versus-C comparison", "atan", "asin", "acos", "atan2");
        Path clone = CoreMathTestSupport.CORE_MATH_DIR;
        Map<String, long[]> java = CoreMathInverseTrigTables.snapshot();
        Map<String, long[]> c = CoreMathInverseTrigTestSupport.parseFromC(clone);
        long values = 0;
        long mismatches = 0;
        Map<String, Integer> sources = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> e : c.entrySet()) {
            String name = e.getKey().substring(e.getKey().indexOf(':') + 1);
            long[] want = java.get(name);
            assertNotNull(want, new StringBuilder(80).append("the C source has a table the snapshot lacks: ").append(e.getKey()).toString());
            long[] got = e.getValue();
            values += Math.max(want.length, got.length);
            mismatches += Math.abs(want.length - got.length);
            for (int i = 0; i < Math.min(want.length, got.length); i++) {
                if (want[i] != got[i]) {
                    mismatches++;
                }
            }
            sources.merge(name, 1, Integer::sum);
        }
        for (String name : java.keySet()) {
            assertTrue(sources.containsKey(name), new StringBuilder(80).append("table without a C source check: ").append(name).toString());
        }
        System.out.println(new StringBuilder(140).append("tables vs the C text (atan.c, asin.c, acos.c, atan2.c, tint.h): ").append(mismatches).append(" mismatches of ").append(values)
                .append(" values over ").append(c.size()).append(" table-file pairs covering ").append(sources.size()).append(" tables"));
        assertEquals(0L, mismatches);
        assertEquals(java.size(), sources.size());
        assertEquals(2, (int) sources.get("ASINACOS_CC"));
        // control: one flipped bit in a copy is rejected, exactly once
        long[] copy = java.get("ATAN_A").clone();
        copy[7] ^= 1L << 20;
        long rejected = 0;
        long[] original = c.get("atan:ATAN_A");
        for (int i = 0; i < original.length; i++) {
            if (original[i] != copy[i]) {
                rejected++;
            }
        }
        assertEquals(1L, rejected);
    }
}
