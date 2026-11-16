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
 * The tables and the named scalar constants of the phase B1 ports ({@link CoreMathInverseTrigTables}) under two guards: a hermetic digest,
 * and (with the CORE-MATH clone) a bit-for-bit comparison with the C text. Runtime tier: FAST (default suite); the C comparisons are
 * skipped without the clone and FAIL when the full tier is requested ({@code -Dcore.math.full=true}).
 * <p>
 * The digest is the SHA-256 of {@link CoreMathInverseTrigTestSupport#canonical}. Two digests are committed, both as test resources written by
 * {@code CoreMathGoldenGeneratorB1} (a harness) only if the Java values equal those parsed from every C file that carries them:
 * <ul>
 *   <li>{@code core-math-tables-b1-2026-11-12.sha256} (phase B1 proper, never regenerated): the 26 tables. It is still checked here, on the
 *       tables-only view of the snapshot, so the accepted artefact stays in use;</li>
 *   <li>{@code core-math-tables-b1-v2-2026-11-12.sha256} (fix pass, a new file): the 26 tables followed by the 31 named scalar constants
 *       (the fast-path and accurate-path error constants, the {@code err} bounds, the shared scalars; snapshot keys {@code K_...}).</li>
 * </ul>
 * Why the constants are here: Fable's review of phase B1 (2026-11-12) measured that most fast-path error constants could be halved without one
 * mismatch on the full {@code .wc} files, so no behavioural test guards them; a value edit is caught here instead (digest, and the clone
 * comparison against the C text).
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
 * (Wording error in that sealed text, booked: B1-P10 and {@link CoreMathInverseTrigTables} named a class {@code CoreMathInverseTrigConstantsTest};
 * the check is {@link #everyTableEqualsTheCText()} in this class.)
 *
 * <h2>SEALED PREDICTIONS (B1F-P1..P4, phase B1 fix pass; written before the first run of the tests added or changed in the fix pass)</h2>
 * Population: the snapshot of {@link CoreMathInverseTrigTables} = 26 tables + 31 named constants = 57 entries; scoring: a digest differs, or a
 * parsed C value differs from the Java value (raw bits for a double).
 * <ol>
 *   <li><b>B1F-P1 two digests.</b> The digest of the tables-only view equals the digest committed in {@code core-math-tables-b1-2026-11-12.sha256}
 *       (unchanged resource); the digest of the full snapshot equals the one committed in {@code core-math-tables-b1-v2-2026-11-12.sha256}
 *       (written by the generator after the first run, so that test fails by design until the generation commit), and that resource names the
 *       source commit.</li>
 *   <li><b>B1F-P2 control.</b> A single flipped bit in each entry of the full snapshot changes the full digest: <b>57 of 57</b> (26 tables at the first
 *       and at the last element, 31 constants at their one element); the tables-only digest control is unchanged (26 of 26 at both positions);
 *       the write-into-a-snapshot control: 0 of 57 writes visible. ELSE-BRANCH: fewer means the rendering omits an entry and the digest is blind
 *       to it.</li>
 *   <li><b>B1F-P3 C text of the constants</b> (clone present): <b>42 value comparisons covering all 31 constants, 0 mismatches</b> (a constant used in
 *       several places, or in several files, is compared at every place against the one Java definition: PI128_H twice, PI2_H four times, PI2_L
 *       three times, RCP_PI64, MAGIC, PI64_H, PI64_L and PI64_S twice each); every constant of the snapshot has at least one C source (a constant
 *       added without a check fails); the control (one flipped bit in a copy of one constant) is rejected exactly once. ELSE-BRANCH: a mismatch
 *       is a transcription defect of a constant in the ports or a stale regular expression of the test; reported, not loosened.</li>
 *   <li><b>B1F-P4 behaviour unchanged.</b> The ports read the constants from the tables class instead of inline literals; every value is the same
 *       literal text, so every result is bit-identical: the whole module's fast tier (after {@code mvn clean}) has 0 failures, the slow tier has 0
 *       mismatches with the denominators of phase B1's READY section 5.2, and {@code CoreMathTintTest} prints the same counts as before the move of
 *       its multiply copy to an MIT-headered helper (multiply control 221,859 of 300,000 = the model's 221,859 carries).</li>
 * </ol>
 */
class CoreMathInverseTrigTablesDigestTest {

    static final String RESOURCE = "core-math-tables-b1-2026-11-12.sha256";
    static final String RESOURCE_V2 = "core-math-tables-b1-v2-2026-11-12.sha256";

    private static String[] resourceLines(String resource) throws IOException {
        try (InputStream in = CoreMathInverseTrigTablesDigestTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, new StringBuilder(80).append("the committed digest resource ").append(resource).append(" is missing").toString());
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R");
        }
    }

    private static String committedDigest(String resource) throws IOException {
        for (String l : resourceLines(resource)) {
            String s = l.trim();
            if (!s.isEmpty() && !s.startsWith("#")) {
                return s;
            }
        }
        throw new AssertionError(new StringBuilder(64).append("no digest line in ").append(resource).toString());
    }

    private static void assertNamesTheCommit(String resource) throws IOException {
        boolean namesCommit = false;
        for (String l : resourceLines(resource)) {
            if (l.startsWith("#") && l.contains("018fa97cbdacf59924b899a9dfa422d532cb2bb7")) {
                namesCommit = true;
            }
        }
        assertTrue(namesCommit, new StringBuilder(80).append(resource).append(" does not name the CORE-MATH source commit").toString());
    }

    @Test
    void digestOfTheJavaTablesEqualsTheCommittedDigest() throws IOException {
        String committed = committedDigest(RESOURCE);
        assertEquals(64, committed.length(), "the committed digest is not 64 hex digits");
        assertNamesTheCommit(RESOURCE);
        String actual = CoreMathInverseTrigTestSupport.sha256(CoreMathInverseTrigTestSupport.tablesOnly(CoreMathInverseTrigTables.snapshot()));
        System.out.println(new StringBuilder(160).append("tables digest (phase B1, 26 tables): Java ").append(actual).append(", committed ").append(committed));
        assertEquals(committed, actual, "a table of CoreMathInverseTrigTables differs from the committed digest");
    }

    @Test
    void digestOfTheTablesAndTheNamedConstantsEqualsTheCommittedDigest() throws IOException {
        String committed = committedDigest(RESOURCE_V2);
        assertEquals(64, committed.length(), "the committed digest is not 64 hex digits");
        assertNamesTheCommit(RESOURCE_V2);
        Map<String, long[]> all = CoreMathInverseTrigTables.snapshot();
        assertEquals(57, all.size(), "the snapshot is not 26 tables + 31 constants");
        String actual = CoreMathInverseTrigTestSupport.sha256(all);
        System.out.println(new StringBuilder(160).append("tables + constants digest (fix pass, 57 entries): Java ").append(actual).append(", committed ").append(committed));
        assertEquals(committed, actual, "a table or a named constant of CoreMathInverseTrigTables differs from the committed digest");
    }

    @Test
    void controlOneFlippedBitInAnyTableChangesTheDigest() {
        String base = CoreMathInverseTrigTestSupport.sha256(CoreMathInverseTrigTestSupport.tablesOnly(CoreMathInverseTrigTables.snapshot()));
        int tables = 0;
        int rejectedFirst = 0;
        int rejectedLast = 0;
        for (String name : CoreMathInverseTrigTestSupport.tablesOnly(CoreMathInverseTrigTables.snapshot()).keySet()) {
            tables++;
            Map<String, long[]> first = CoreMathInverseTrigTestSupport.tablesOnly(CoreMathInverseTrigTables.snapshot());
            first.get(name)[0] ^= 1L;
            if (!base.equals(CoreMathInverseTrigTestSupport.sha256(first))) {
                rejectedFirst++;
            }
            Map<String, long[]> last = CoreMathInverseTrigTestSupport.tablesOnly(CoreMathInverseTrigTables.snapshot());
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
    void controlOneFlippedBitInAnyEntryOfTheFullSnapshotChangesTheFullDigest() {
        String base = CoreMathInverseTrigTestSupport.sha256(CoreMathInverseTrigTables.snapshot());
        int entries = 0;
        int constants = 0;
        int rejected = 0;
        int rejectedConstants = 0;
        for (String name : CoreMathInverseTrigTables.snapshot().keySet()) {
            entries++;
            Map<String, long[]> copy = CoreMathInverseTrigTables.snapshot();
            long[] v = copy.get(name);
            v[v.length - 1] ^= 1L << 3;
            boolean differs = !base.equals(CoreMathInverseTrigTestSupport.sha256(copy));
            if (differs) {
                rejected++;
            }
            if (name.startsWith(CoreMathInverseTrigTestSupport.CONSTANT_PREFIX)) {
                constants++;
                if (differs) {
                    rejectedConstants++;
                }
            }
        }
        System.out.println(new StringBuilder(140).append("full digest control: a flipped bit changed the digest for ").append(rejected).append(" of ").append(entries)
                .append(" entries (constants: ").append(rejectedConstants).append(" of ").append(constants).append(')'));
        assertEquals(57, entries);
        assertEquals(31, constants);
        assertEquals(entries, rejected);
        assertEquals(constants, rejectedConstants);
    }

    @Test
    void theSnapshotIsACopyAndWritesToItAreNotVisible() {
        String base = CoreMathInverseTrigTestSupport.sha256(CoreMathInverseTrigTables.snapshot());
        int entries = 0;
        int visible = 0;
        for (String name : CoreMathInverseTrigTables.snapshot().keySet()) {
            entries++;
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
        System.out.println(new StringBuilder(96).append("tables snapshot: writes into ").append(entries).append(" snapshot entries, visible in a later snapshot: ").append(visible));
        assertEquals(57, entries);
        assertEquals(0, visible, "a write into a snapshot reached a table");
    }

    @Test
    void everyTableEqualsTheCText() throws IOException {
        CoreMathTestSupport.requireClone(CoreMathTestSupport.CORE_MATH_DIR, CoreMathTestSupport.FULL_TIER, "the tables-versus-C comparison", "atan", "asin", "acos", "atan2");
        Path clone = CoreMathTestSupport.CORE_MATH_DIR;
        Map<String, long[]> java = CoreMathInverseTrigTestSupport.tablesOnly(CoreMathInverseTrigTables.snapshot());
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

    @Test
    void everyNamedConstantEqualsTheCText() throws IOException {
        CoreMathTestSupport.requireClone(CoreMathTestSupport.CORE_MATH_DIR, CoreMathTestSupport.FULL_TIER, "the constants-versus-C comparison", "atan", "asin", "acos", "atan2");
        Path clone = CoreMathTestSupport.CORE_MATH_DIR;
        Map<String, long[]> java = CoreMathInverseTrigTestSupport.constantsOnly(CoreMathInverseTrigTables.snapshot());
        Map<String, long[]> c = CoreMathInverseTrigTestSupport.constantsFromC(clone);
        long compared = 0;
        long mismatches = 0;
        Map<String, Integer> sources = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> e : c.entrySet()) {
            String name = new StringBuilder(40).append(CoreMathInverseTrigTestSupport.CONSTANT_PREFIX)
                    .append(e.getKey().substring(e.getKey().indexOf(':') + 1, e.getKey().indexOf('@'))).toString();
            long[] want = java.get(name);
            assertNotNull(want, new StringBuilder(80).append("the C source has a constant the snapshot lacks: ").append(e.getKey()).toString());
            compared++;
            if (want[0] != e.getValue()[0]) {
                mismatches++;
                System.out.println(new StringBuilder(120).append("constant ").append(e.getKey()).append(": C 0x").append(Long.toHexString(e.getValue()[0])).append(", Java 0x")
                        .append(Long.toHexString(want[0])));
            }
            sources.merge(name, 1, Integer::sum);
        }
        for (String name : java.keySet()) {
            assertTrue(sources.containsKey(name), new StringBuilder(80).append("constant without a C source check: ").append(name).toString());
        }
        System.out.println(new StringBuilder(140).append("named constants vs the C text: ").append(mismatches).append(" mismatches of ").append(compared)
                .append(" value comparisons covering ").append(sources.size()).append(" constants"));
        assertEquals(0L, mismatches);
        assertEquals(42L, compared);
        assertEquals(31, sources.size());
        assertEquals(31, java.size());
        // control: one flipped bit in a copy of one constant is rejected, exactly once
        long rejected = 0;
        for (Map.Entry<String, long[]> e : c.entrySet()) {
            long[] w = java.get(new StringBuilder(40).append(CoreMathInverseTrigTestSupport.CONSTANT_PREFIX)
                    .append(e.getKey().substring(e.getKey().indexOf(':') + 1, e.getKey().indexOf('@'))).toString());
            long copy = w[0];
            if (e.getKey().contains(":ASIN_EPS_LO@")) {
                copy ^= 1L << 40;
            }
            if (copy != e.getValue()[0]) {
                rejected++;
            }
        }
        assertEquals(1L, rejected);
    }
}
