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

import org.junit.jupiter.api.Test;

/**
 * The hermetic guard on the constant tables: the SHA-256 of a canonical rendering of every table of
 * {@link CoreMathTables} ({@link CoreMathTestSupport.TableSet#canonical()}) equals a digest committed
 * as the test resource {@code core-math-tables.sha256}, so a changed bit in any table fails on EVERY
 * machine, with no CORE-MATH clone and no skip. It exists because {@code CoreMathConstantsTest}, which
 * compares against the C text, needs the clone. Runtime tier: FAST (default suite).
 * <p>
 * Where the committed digest comes from: {@code CoreMathGoldenGenerator.generateTablesDigest} (a harness)
 * computes the digest of the Java tables AND of the tables parsed from {@code sin.c} and from
 * {@code cos.c} of the CORE-MATH clone at commit {@code 018fa97c}, refuses to write unless all three
 * agree, and writes the resource. So the committed digest is a digest of the C source's tables, not only
 * of the Java's.
 *
 * <h2>SEALED PREDICTIONS (fix pass, written before the first run of these tests)</h2>
 * <ol>
 *   <li><b>F1</b> the digest of the Java tables equals the committed digest, and the resource names the
 *       source commit {@code 018fa97cbdacf59924b899a9dfa422d532cb2bb7}.</li>
 *   <li><b>F2 control</b> a single flipped bit in a copy of each of ten different tables or scalars
 *       (T, U1, U2, PS, PC, S1U, S2U, C2U, PIH, PIL) changes the digest: 10 of 10 rejected.
 *       ELSE-BRANCH: fewer means the rendering omits something and the digest is blind to it.</li>
 *   <li><b>F3 encapsulation</b> writing into a {@code *Copy()} snapshot does not change what the element
 *       accessors return (0 of 3 writes visible), and the snapshot holds 1,216 values.</li>
 * </ol>
 */
class CoreMathTablesDigestTest {

    private static String[] resourceLines() throws IOException {
        try (InputStream in = CoreMathTablesDigestTest.class.getResourceAsStream("core-math-tables.sha256")) {
            assertNotNull(in, "the committed digest resource core-math-tables.sha256 is missing");
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
        throw new AssertionError("no digest line in core-math-tables.sha256");
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
        assertTrue(namesCommit, "the resource does not name the source commit");
        String actual = CoreMathTestSupport.TableSet.fromJava().sha256();
        System.out.println(new StringBuilder(160).append("tables digest: Java ").append(actual).append(", committed ").append(committed));
        assertEquals(committed, actual, "a table of CoreMathTables changed: its digest no longer equals the committed one");
    }

    private static CoreMathTestSupport.TableSet flipped(int which) {
        CoreMathTestSupport.TableSet s = CoreMathTestSupport.TableSet.fromJava();
        switch (which) {
            case 0:
                s.t[7] ^= 1L << 40;
                break;
            case 1:
                s.u1[100] = Double.longBitsToDouble(Double.doubleToRawLongBits(s.u1[100]) ^ 1L);
                break;
            case 2:
                s.u2[300] = Double.longBitsToDouble(Double.doubleToRawLongBits(s.u2[300]) ^ (1L << 20));
                break;
            case 3:
                s.ps[2] = U128.of(s.ps[2].hi, s.ps[2].lo ^ 1L);
                break;
            case 4:
                s.pc[4] = U128.of(s.pc[4].hi ^ 1L, s.pc[4].lo);
                break;
            case 5:
                s.s1u[5] = U128.of(s.s1u[5].hi ^ (1L << 63), s.s1u[5].lo);
                break;
            case 6:
                s.s2u[40] = U128.of(s.s2u[40].hi, s.s2u[40].lo ^ (1L << 40));
                break;
            case 7:
                s.c2u[63] = U128.of(s.c2u[63].hi, s.c2u[63].lo ^ 1L);
                break;
            case 8:
                s.pih = Double.longBitsToDouble(Double.doubleToRawLongBits(s.pih) ^ 1L);
                break;
            default:
                s.pil = Double.longBitsToDouble(Double.doubleToRawLongBits(s.pil) ^ 1L);
                break;
        }
        return s;
    }

    /** The control: the digest must be able to fail. One flipped bit in a copy of each of ten tables changes it. */
    @Test
    void controlOneFlippedBitInAnyTableChangesTheDigest() throws IOException {
        String committed = committedDigest();
        int rejected = 0;
        for (int i = 0; i < 10; i++) {
            if (!committed.equals(flipped(i).sha256())) {
                rejected++;
            }
        }
        System.out.println(new StringBuilder(80).append("tables digest control: ").append(rejected).append(" of 10 one-bit changes rejected"));
        assertEquals(10, rejected, "the digest is blind to a change in one of the tables");
    }

    @Test
    void tablesCannotBeWrittenThroughTheAccessors() {
        int visible = 0;
        double u1Before = CoreMathTables.u1(5);
        CoreMathTables.u1Copy()[5] = 123.456;
        if (Double.doubleToRawLongBits(CoreMathTables.u1(5)) != Double.doubleToRawLongBits(u1Before)) {
            visible++;
        }
        long tBefore = CoreMathTables.t(3);
        CoreMathTables.tCopy()[3] = -1L;
        if (CoreMathTables.t(3) != tBefore) {
            visible++;
        }
        U128 sBefore = CoreMathTables.s1u(4);
        CoreMathTables.s1uCopy()[4] = U128.of(1L, 2L);
        if (!CoreMathTables.s1u(4).equals(sBefore)) {
            visible++;
        }
        assertEquals(0, visible, "a write into a snapshot reached a table");
        assertNotEquals(0, CoreMathTestSupport.TableSet.fromJava().valueCount());
        assertEquals(1216, CoreMathTestSupport.TableSet.fromJava().valueCount());
    }
}
