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

package au.net.zeus.jgdms.cel.wire;

import au.net.zeus.jgdms.cel.testsupport.TestDer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Appendix B §B.6.3's size-bounds-before-allocation checks, and §B.13.7's
 * boundary probes (name-length / {@code maxScalarBytes} ceilings, empty
 * collections) reproduced against the real decoder, including exact-boundary
 * fencepost pairs per §10.3's inclusive convention.
 */
class NameLengthAndScalarCeilingTest {

    private static void assertRejected(byte[] exprBytes) {
        byte[] wire = TestDer.wrapAsPredicateRecord(exprBytes);
        assertThrows(CelDecodeException.class, () -> CelDecoder.decode(wire));
    }

    private static void assertAccepted(byte[] exprBytes) throws Exception {
        byte[] wire = TestDer.wrapAsPredicateRecord(exprBytes);
        assertNotNull(CelDecoder.decode(wire));
    }

    private static String repeat(char c, int n) {
        return String.valueOf(c).repeat(n);
    }

    // ---- unqual / fieldName name length: SIZE(1..255) ----------------------

    @Test
    void unqualAt255BytesAccepted() throws Exception {
        assertAccepted(TestDer.fieldRef(repeat('f', 255)));
    }

    @Test
    void unqualAt256BytesRejected() {
        assertRejected(TestDer.fieldRef(repeat('f', 256)));
    }

    @Test
    void unqualEmptyRejected() {
        assertRejected(TestDer.fieldRef(""));
    }

    // ---- className: SIZE(1..1024) ------------------------------------------

    @Test
    void classNameAt1024BytesAccepted() throws Exception {
        assertAccepted(TestDer.fieldRefQualified(repeat('c', 1024), "f"));
    }

    @Test
    void classNameAt1025BytesRejected() {
        assertRejected(TestDer.fieldRefQualified(repeat('c', 1025), "f"));
    }

    // ---- maxScalarBytes: LIT_STRING / LIT_BYTES SIZE(0..65536) --------------

    @Test
    void litStringAt65536BytesAccepted() throws Exception {
        assertAccepted(TestDer.litString(repeat('x', 65536)));
    }

    @Test
    void litStringAt65537BytesRejected() {
        assertRejected(TestDer.litString(repeat('x', 65537)));
    }

    @Test
    void litBytesAt65536BytesAccepted() throws Exception {
        assertAccepted(TestDer.litBytes(new byte[65536]));
    }

    @Test
    void litBytesAt65537BytesRejected() {
        assertRejected(TestDer.litBytes(new byte[65537]));
    }

    @Test
    void litStringEmptyAccepted() throws Exception {
        assertAccepted(TestDer.litString(""));
    }

    @Test
    void litBytesEmptyAccepted() throws Exception {
        assertAccepted(TestDer.litBytes(new byte[0]));
    }

    // ---- empty-collection floors --------------------------------------------

    @Test
    void emptyListLitRejected() {
        assertRejected(TestDer.listLit());
    }

    @Test
    void emptyFieldRefStepsRejected() {
        assertRejected(TestDer.fieldRefZeroSteps());
    }

    @Test
    void singleElementListLitAccepted() throws Exception {
        assertAccepted(TestDer.listLit(TestDer.litInt(1)));
    }
}
