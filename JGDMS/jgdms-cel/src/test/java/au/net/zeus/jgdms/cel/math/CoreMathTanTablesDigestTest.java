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
 * The tables and the named scalar constants of the phase B2 port ({@link CoreMathTanTables}) under two guards, from the first commit: a hermetic digest, and (with the
 * CORE-MATH clone) a bit-for-bit comparison with the C text. Runtime tier: FAST (default suite); the C comparisons are skipped without the clone and FAIL when the full tier is
 * requested ({@code -Dcore.math.full=true}).
 * <p>
 * The digest is the SHA-256 of {@link CoreMathInverseTrigTestSupport#canonical(String, Map)} with the tan header, over {@link CoreMathTanTables#snapshot()}: the 13 tables and the 9
 * named constants (22 entries). It is committed as the test resource {@code core-math-tables-tan-2026-11-12.sha256}, written by {@code CoreMathGoldenGeneratorTan} only if the Java values equal
 * those parsed from {@code tan.c}. The lesson of phase B1 (Fable M1: an error bound written inline could be halved without one mismatch on the full {@code .wc} files) is applied from the
 * first commit: every error bound and shared scalar of the tan port is a named constant here, so a value edit is caught by the digest on every machine and by the clone comparison where the
 * clone is.
 *
 * <h2>SEALED PREDICTIONS (B2-T1..T4, phase B2; written before the first run of these tests)</h2>
 * Population: the snapshot = 13 tables + 9 constants = 22 entries; scoring: a digest differs, or a parsed C value differs from the Java value (raw bits for a double).
 * <ol>
 *   <li><b>B2-T1.</b> The digest of the snapshot equals the committed digest (the resource fails by design at the seal commit and passes after the generation commit), and the resource names
 *       the source commit {@code 018fa97cbdacf59924b899a9dfa422d532cb2bb7}.</li>
 *   <li><b>B2-T2 control.</b> A single flipped bit in each entry of a copy changes the digest: 22 of 22 (13 tables at the first and at the last element, 9 constants at their one element); a
 *       write into the snapshot is not visible in a later snapshot (0 of 22). ELSE-BRANCH: fewer means the rendering omits an entry and the digest is blind to it.</li>
 *   <li><b>B2-T3 C text of the tables</b> (clone present): 13 tables, 0 mismatches over all values (the count is printed; 3,168 words expected: Tinv 256, T 20, S 1,024, C 1,024,
 *       PSfast 5, PCfast 5, PS 24, PC 24, SC 768, exceptions 6, ZERO, ONE, MAGIC 4 each); every table of the snapshot has a C source (a table added without a check fails); the control (one flipped
 *       bit in a copy) is rejected exactly once. The 20 words of {@code T} include the 19 that {@link CoreMathTables} holds (read through {@link CoreMathTanTables#t}) and the one it does not.</li>
 *   <li><b>B2-T4 C text of the constants</b> (clone present): 9 value comparisons covering all 9 constants, 0 mismatches; every constant of the snapshot has a C source; the control (one flipped
 *       bit in a copy of one constant) is rejected exactly once.</li>
 * </ol>
 *
 * <h2>SEALED PREDICTIONS (B2F-T1, B2F-T2, the fix pass; written before the first run of the tests that carry them)</h2>
 * Population: the snapshot of {@link CoreMathTanTables} with the guard constant, 13 tables + 10 constants = 23 entries ({@code K_GUARD_K}, the factor of the guard, 8.0, not in the C).
 * <ol>
 *   <li><b>B2F-T1.</b> The digest of the 22-entry view {@link #snapshotV1()} still equals the committed phase B2 digest (the accepted artefact, unchanged), and the digest of the full 23-entry
 *       snapshot equals the one committed in {@code core-math-tables-tan-v2-2026-11-12.sha256} (written by the generator after the first run, so that test fails by design until the generation
 *       commit), which names the source commit; {@code K_GUARD_K} is the bits of 8.0.</li>
 *   <li><b>B2F-T2 control.</b> A single flipped bit in each of the 23 entries changes the v2 digest: 23 of 23.</li>
 * </ol>
 * The tests of B2-T1..T4 above now run on the 22-entry view (their counts 22 and 9 unchanged); the C-text comparisons cover the 22 entries that have a C text.
 */
class CoreMathTanTablesDigestTest {

    static final String RESOURCE = "core-math-tables-tan-2026-11-12.sha256";
    static final String RESOURCE_V2 = "core-math-tables-tan-v2-2026-11-12.sha256";

    /**
     * The snapshot as it was when {@link #RESOURCE} was committed: 13 tables and 9 constants (22 entries). The fix pass added the guard constant {@code K_GUARD_K}, which is NOT in the C and
     * so has no C text to compare with; the accepted artefact of the phase B2 digest is not regenerated and stays in use on this view (as phase B1's tables-only view), and
     * {@link #RESOURCE_V2} covers all 23 entries.
     */
    static Map<String, long[]> snapshotV1() {
        Map<String, long[]> m = CoreMathTanTables.snapshot();
        m.remove("K_GUARD_K");
        return m;
    }

    private static String[] resourceLines(String resource) throws IOException {
        try (InputStream in = CoreMathTanTablesDigestTest.class.getResourceAsStream(resource)) {
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

    static String digest(Map<String, long[]> m) {
        return CoreMathInverseTrigTestSupport.sha256(CoreMathTanTestSupport.CANONICAL_HEADER, m);
    }

    @Test
    void digestOfTheTablesAndTheNamedConstantsEqualsTheCommittedDigest() throws IOException {
        String committed = committedDigest(RESOURCE);
        assertEquals(64, committed.length(), "the committed digest is not 64 hex digits");
        boolean namesCommit = false;
        for (String l : resourceLines(RESOURCE)) {
            if (l.startsWith("#") && l.contains("018fa97cbdacf59924b899a9dfa422d532cb2bb7")) {
                namesCommit = true;
            }
        }
        assertTrue(namesCommit, "the digest resource does not name the CORE-MATH source commit");
        Map<String, long[]> all = snapshotV1();
        assertEquals(22, all.size(), "the snapshot is not 13 tables + 9 constants");
        String actual = digest(all);
        System.out.println(new StringBuilder(160).append("tan tables + constants digest (22 entries): Java ").append(actual).append(", committed ").append(committed));
        assertEquals(committed, actual, "a table or a named constant of CoreMathTanTables differs from the committed digest");
    }

    @Test
    void controlOneFlippedBitInAnyEntryChangesTheDigest() {
        String base = digest(snapshotV1());
        int entries = 0;
        int constants = 0;
        int rejectedFirst = 0;
        int rejectedLast = 0;
        int rejectedConstants = 0;
        for (String name : snapshotV1().keySet()) {
            entries++;
            Map<String, long[]> first = snapshotV1();
            first.get(name)[0] ^= 1L << 3;
            if (!base.equals(digest(first))) {
                rejectedFirst++;
            }
            Map<String, long[]> last = snapshotV1();
            long[] v = last.get(name);
            v[v.length - 1] ^= 1L << 17;
            boolean differs = !base.equals(digest(last));
            if (differs) {
                rejectedLast++;
            }
            if (name.startsWith(CoreMathInverseTrigTestSupport.CONSTANT_PREFIX)) {
                constants++;
                if (differs) {
                    rejectedConstants++;
                }
            }
        }
        System.out.println(new StringBuilder(160).append("tan digest control: a flipped bit changed the digest for ").append(rejectedFirst).append(" of ").append(entries)
                .append(" entries at the first element, ").append(rejectedLast).append(" at the last (constants: ").append(rejectedConstants).append(" of ").append(constants).append(')'));
        assertEquals(22, entries);
        assertEquals(9, constants);
        assertEquals(entries, rejectedFirst);
        assertEquals(entries, rejectedLast);
        assertEquals(constants, rejectedConstants);
    }

    @Test
    void theSnapshotIsACopyAndWritesToItAreNotVisible() {
        String base = digest(snapshotV1());
        int entries = 0;
        int visible = 0;
        for (String name : snapshotV1().keySet()) {
            entries++;
            Map<String, long[]> s = snapshotV1();
            long[] v = s.get(name);
            for (int i = 0; i < v.length; i++) {
                v[i] = ~v[i];
            }
            assertNotEquals(base, digest(s), "the control write changed nothing: the snapshot is not what is hashed");
            if (!base.equals(digest(snapshotV1()))) {
                visible++;
            }
        }
        System.out.println(new StringBuilder(96).append("tan tables snapshot: writes into ").append(entries).append(" snapshot entries, visible in a later snapshot: ").append(visible));
        assertEquals(22, entries);
        assertEquals(0, visible, "a write into a snapshot reached a table");
    }

    @Test
    void everyTableEqualsTheCText() throws IOException {
        CoreMathTestSupport.requireClone(CoreMathTestSupport.CORE_MATH_DIR, CoreMathTestSupport.FULL_TIER, "the tables-versus-C comparison", "tan");
        Path clone = CoreMathTestSupport.CORE_MATH_DIR;
        Map<String, long[]> java = CoreMathInverseTrigTestSupport.tablesOnly(snapshotV1());
        Map<String, long[]> c = CoreMathTanTestSupport.parseFromC(clone);
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
        System.out.println(new StringBuilder(140).append("tan tables vs the C text (tan.c): ").append(mismatches).append(" mismatches of ").append(values).append(" values over ").append(c.size())
                .append(" tables covering ").append(sources.size()).append(" snapshot tables"));
        assertEquals(0L, mismatches);
        assertEquals(java.size(), sources.size());
        assertEquals(13, sources.size());
        assertEquals(256 + 20 + 1024 + 1024 + 5 + 5 + 24 + 24 + 768 + 6 + 12, values);
        // control: one flipped bit in a copy is rejected, exactly once
        long[] copy = java.get("TAN_SC").clone();
        copy[7] ^= 1L << 20;
        long rejected = 0;
        long[] original = c.get("tan:TAN_SC");
        for (int i = 0; i < original.length; i++) {
            if (original[i] != copy[i]) {
                rejected++;
            }
        }
        assertEquals(1L, rejected);
    }

    @Test
    void everyNamedConstantEqualsTheCText() throws IOException {
        CoreMathTestSupport.requireClone(CoreMathTestSupport.CORE_MATH_DIR, CoreMathTestSupport.FULL_TIER, "the constants-versus-C comparison", "tan");
        Path clone = CoreMathTestSupport.CORE_MATH_DIR;
        Map<String, long[]> java = CoreMathInverseTrigTestSupport.constantsOnly(snapshotV1());
        Map<String, long[]> c = CoreMathTanTestSupport.constantsFromC(clone);
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
        System.out.println(new StringBuilder(140).append("tan named constants vs the C text: ").append(mismatches).append(" mismatches of ").append(compared)
                .append(" value comparisons covering ").append(sources.size()).append(" constants"));
        assertEquals(0L, mismatches);
        assertEquals(9L, compared);
        assertEquals(9, sources.size());
        assertEquals(9, java.size());
        // control: one flipped bit in a copy of one constant is rejected, exactly once
        long rejected = 0;
        for (Map.Entry<String, long[]> e : c.entrySet()) {
            long[] w = java.get(new StringBuilder(40).append(CoreMathInverseTrigTestSupport.CONSTANT_PREFIX)
                    .append(e.getKey().substring(e.getKey().indexOf(':') + 1, e.getKey().indexOf('@'))).toString());
            long copy = w[0];
            if (e.getKey().contains(":FAST_ERR@")) {
                copy ^= 1L << 40;
            }
            if (copy != e.getValue()[0]) {
                rejected++;
            }
        }
        assertEquals(1L, rejected);
    }

    @Test
    void digestOfTheFullSnapshotWithTheGuardConstantEqualsTheCommittedV2Digest() throws IOException {
        String committed = committedDigest(RESOURCE_V2);
        assertEquals(64, committed.length(), "the committed digest is not 64 hex digits");
        boolean namesCommit = false;
        for (String l : resourceLines(RESOURCE_V2)) {
            if (l.startsWith("#") && l.contains("018fa97cbdacf59924b899a9dfa422d532cb2bb7")) {
                namesCommit = true;
            }
        }
        assertTrue(namesCommit, "the v2 digest resource does not name the CORE-MATH source commit");
        Map<String, long[]> all = CoreMathTanTables.snapshot();
        assertEquals(23, all.size(), "the snapshot is not 13 tables + 10 constants");
        assertEquals(Double.doubleToRawLongBits(8.0), all.get("K_GUARD_K")[0]);
        String actual = digest(all);
        System.out.println(new StringBuilder(160).append("tan tables + constants + guard digest (23 entries): Java ").append(actual).append(", committed ").append(committed));
        assertEquals(committed, actual, "a table or a named constant of CoreMathTanTables differs from the committed v2 digest");
    }

    @Test
    void controlOneFlippedBitInAnyEntryOfTheFullSnapshotChangesTheV2Digest() {
        String base = digest(CoreMathTanTables.snapshot());
        int entries = 0;
        int rejected = 0;
        for (String name : CoreMathTanTables.snapshot().keySet()) {
            entries++;
            Map<String, long[]> copy = CoreMathTanTables.snapshot();
            long[] v = copy.get(name);
            v[v.length - 1] ^= 1L << 5;
            if (!base.equals(digest(copy))) {
                rejected++;
            }
        }
        System.out.println(new StringBuilder(100).append("tan v2 digest control: a flipped bit changed the digest for ").append(rejected).append(" of ").append(entries).append(" entries"));
        assertEquals(23, entries);
        assertEquals(entries, rejected);
    }
}
