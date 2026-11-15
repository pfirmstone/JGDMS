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

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Checks that every constant in {@link CoreMathTables} is bit-for-bit the constant in the CORE-MATH
 * C sources ({@code sin.c} and {@code cos.c}, which carries its own copy of the same tables). The C
 * text is re-read here by a different mechanism (regular expressions in Java,
 * {@link CoreMathTestSupport.TableSet#fromC}) from the one that produced the Java tables (a
 * {@code sed} transcription): a transcription error would have to be made twice, identically, to pass.
 * Runtime tier: FAST (default suite). It needs the read-only CORE-MATH clone
 * ({@link CoreMathTestSupport#CORE_MATH_DIR}): without it the test is SKIPPED, loudly, unless the full tier
 * is requested with {@code -Dcore.math.full=true}, in which case it FAILS. The hermetic guard that needs no
 * clone is {@code CoreMathTablesDigestTest}.
 * <p>
 * Predictions are P9 of {@code CoreMathSinCosDifferentialTest}'s javadoc, sealed before the first run
 * (this class was changed after the seal only to read the tables through copies, deviation D5).
 */
class CoreMathConstantsTest {

    /** Number of values compared per C file: 20 + 512 + 512 + 4 + 5 + 33 + 64 + 64 + 2. */
    private static final int EXPECTED_VALUES = 1216;

    @Test
    void javaTablesEqualTheCSourcesBitForBit() throws IOException {
        CoreMathTestSupport.requireClone("CoreMathConstantsTest");
        CoreMathTestSupport.TableSet java = CoreMathTestSupport.TableSet.fromJava();
        long[] s = CoreMathTestSupport.TableSet.compare(
                CoreMathTestSupport.TableSet.fromC(CoreMathTestSupport.cSource(CoreMathTestSupport.CORE_MATH_DIR, "sin")), java);
        long[] c = CoreMathTestSupport.TableSet.compare(
                CoreMathTestSupport.TableSet.fromC(CoreMathTestSupport.cSource(CoreMathTestSupport.CORE_MATH_DIR, "cos")), java);
        StringBuilder sb = new StringBuilder(160);
        sb.append("CoreMathTables vs sin.c: mismatches ").append(s[0]).append(" of ").append(s[1])
                .append("; vs cos.c: mismatches ").append(c[0]).append(" of ").append(c[1]);
        System.out.println(sb);
        assertEquals(EXPECTED_VALUES, s[1], new StringBuilder(160).append("the parser found a different number of values in sin.c than expected: ").append(sb).toString());
        assertEquals(EXPECTED_VALUES, c[1], new StringBuilder(160).append("the parser found a different number of values in cos.c than expected: ").append(sb).toString());
        assertEquals(0L, s[0] + c[0], sb.toString());
    }

    /** The control: the comparison must be able to fail. One flipped bit in a copy of U1 is found. */
    @Test
    void controlOneFlippedTableBitIsRejected() throws IOException {
        CoreMathTestSupport.requireClone("CoreMathConstantsTest control");
        CoreMathTestSupport.TableSet java = CoreMathTestSupport.TableSet.fromJava();
        int at = 4 * 77 + 2;
        java.u1[at] = Double.longBitsToDouble(Double.doubleToRawLongBits(java.u1[at]) ^ 1L);
        long[] r = CoreMathTestSupport.TableSet.compare(
                CoreMathTestSupport.TableSet.fromC(CoreMathTestSupport.cSource(CoreMathTestSupport.CORE_MATH_DIR, "sin")), java);
        System.out.println(new StringBuilder(80).append("CoreMathTables control (one U1 bit flipped) rejected ").append(r[0]).append(" of ").append(r[1]));
        assertEquals(1L, r[0], new StringBuilder(96).append("the comparison must reject exactly the one flipped value, got ").append(r[0]).toString());
    }
}
