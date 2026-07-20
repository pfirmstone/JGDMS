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
 * Appendix B §B.6.1 (function-id allowlist, per-id arity) and §B.6.5
 * (the {@code contains} needle-literal check) -- §B.13.4's probes reproduced
 * against the real decoder.
 */
class FunctionArityAllowlistTest {

    private static void assertRejected(byte[] exprBytes) {
        byte[] wire = TestDer.wrapAsPredicateRecord(exprBytes);
        assertThrows(CelDecodeException.class, () -> CelDecoder.decode(wire));
    }

    private static void assertAccepted(byte[] exprBytes) throws Exception {
        byte[] wire = TestDer.wrapAsPredicateRecord(exprBytes);
        assertNotNull(CelDecoder.decode(wire));
    }

    @Test
    void functionId0Rejected() {
        assertRejected(TestDer.call(0, TestDer.litInt(1)));
    }

    @Test
    void functionId25Rejected() {
        assertRejected(TestDer.call(25, TestDer.litInt(1)));
    }

    @Test
    void functionId1000Rejected() {
        assertRejected(TestDer.call(1000, TestDer.litInt(1)));
    }

    /** §B.13.4's exact probe: {@code functionId = 99} is accepted by the bare grammar (`INTEGER (1..24)` notwithstanding) but MUST be rejected by the decoder's own explicit range/table check. */
    @Test
    void functionId99Rejected() {
        assertRejected(TestDer.call(99, TestDer.litInt(1)));
    }

    @Test
    void functionId12SqrtWithOneArgAccepted() throws Exception {
        // sqrt(x: double), arity 1.
        assertAccepted(TestDer.call(12, TestDer.litDouble(4.0)));
    }

    /** §B.13.4: the generic grammar bound (arguments SIZE(1..2)) admits 2 args for sqrt (arity 1) -- the decoder MUST still reject via its own explicit per-id arity check. */
    @Test
    void functionId12SqrtWithTwoArgsRejected() {
        assertRejected(TestDer.call(12, TestDer.litDouble(4.0), TestDer.litDouble(9.0)));
    }

    @Test
    void containsWithLiteralNeedleAccepted() throws Exception {
        assertAccepted(TestDer.call(22, TestDer.litString("hello world"), TestDer.litString("world")));
    }

    @Test
    void containsWithNonLiteralNeedleRejected() {
        // needle is a FIELD_REF, not a string literal -- STD-011 §5.3.1 item 4 / §6.6.
        assertRejected(TestDer.call(22, TestDer.litString("hello world"), TestDer.fieldRef("someField")));
    }

    @Test
    void startsWithAcceptsNonLiteralSecondArgument() throws Exception {
        // startsWith/endsWith have no literal restriction on their second argument (§6.6).
        assertAccepted(TestDer.call(23, TestDer.litString("hello"), TestDer.fieldRef("prefixField")));
    }

    @Test
    void arityMismatchTwoArgFunctionGivenOne() {
        // atan2 (id 21) requires 2 arguments.
        assertRejected(TestDer.call(21, TestDer.litDouble(1.0)));
    }
}
