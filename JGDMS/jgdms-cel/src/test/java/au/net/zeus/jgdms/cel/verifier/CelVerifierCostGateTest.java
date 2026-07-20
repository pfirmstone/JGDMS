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

package au.net.zeus.jgdms.cel.verifier;

import au.net.zeus.jgdms.cel.testsupport.TestDer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CelVerifier}'s cost gate: the sharpest gap this task found is that
 * {@code CostModel.computeCost} was, before this class existed, never
 * invoked as an admission decision anywhere in this module -- an expression
 * could satisfy every decode-time ceiling and still have an unbounded
 * {@code C(E)}. These tests exercise the gate through the full facade
 * (real wire bytes -> decode -> cost), not just {@code CostModel} directly
 * (already covered by {@code CostModelTest}).
 */
class CelVerifierCostGateTest {

    /**
     * An expression built from one {@code contains} call (cost dominated by
     * {@code S * needleLen}, {@code S = 65536}) and one {@code startsWith}
     * call (cost dominated by {@code min(S, needleLen)}, granularity 1),
     * ANDed together, tuned so the total {@code C(E)} is <em>exactly</em>
     * {@code maxExprCost} (1,000,000):
     * <pre>
     * contains(recv1, "x"*15)     -- own = 1 + 65536*15 = 983041; + receiver(1) + needle(1) = 983043
     * startsWith(recv2, "y"*16953) -- own = 1 + 16953;            + receiver(1) + needle(1) = 16956
     * AND(...)                    -- own = 1; total = 1 + 983043 + 16956 = 1000000
     * </pre>
     */
    private static byte[] atLimitExpr(int startsWithNeedleLen) {
        byte[] contains = TestDer.call(FunctionIds.CONTAINS,
                TestDer.fieldRef("recv1"), TestDer.litString("x".repeat(15)));
        byte[] startsWith = TestDer.call(FunctionIds.STARTS_WITH,
                TestDer.fieldRef("recv2"), TestDer.litString("y".repeat(startsWithNeedleLen)));
        return TestDer.and(contains, startsWith);
    }

    private interface FunctionIds {
        int CONTAINS = 22;
        int STARTS_WITH = 23;
    }

    @Test
    void costExactlyAtCeiling_accepted() {
        byte[] wire = TestDer.wrapAsPredicateRecord(atLimitExpr(16953));
        VerificationResult result = CelVerifier.verify(wire);
        assertTrue(result.accepted(), () -> "expected acceptance at the exact ceiling, got: " + result);
        assertEquals(1_000_000L, result.cost().orElseThrow());
    }

    @Test
    void costOneOverCeiling_rejected() {
        byte[] wire = TestDer.wrapAsPredicateRecord(atLimitExpr(16954)); // +1 to startsWith's own cost -> total 1,000,001
        VerificationResult result = CelVerifier.verify(wire);
        assertFalse(result.accepted());
        assertEquals(VerificationResult.Reason.COST_EXCEEDED, result.reason());
        assertTrue(result.cost().isEmpty(), "no valid cost total is available once the gate rejects");
        assertTrue(result.record().isPresent(), "decode itself succeeded; only the cost gate rejected");
    }

    /**
     * STD-011 §10.6's mandatory 2^32-adjacent regression, exercised through
     * the full facade (real wire bytes) rather than {@code CostModel}
     * directly: {@code contains} with a needle at exactly {@code
     * maxScalarBytes} (65536) makes {@code C(E) = 1 + 65536*65536 + ... >
     * 4.29 billion}, comfortably representable in the 64-bit accumulator but
     * wrapping to a small (possibly sub-ceiling) value in a 32-bit one. The
     * gate MUST reject this, never silently accept it due to a wraparound.
     */
    @Test
    void costWrapAdjacentExpression_rejectedAtTheGate() {
        byte[] receiver = TestDer.fieldRef("someString");
        byte[] needle = TestDer.litString("x".repeat(65536)); // exactly maxScalarBytes -- decodes fine
        byte[] containsCall = TestDer.call(FunctionIds.CONTAINS, receiver, needle);
        byte[] wire = TestDer.wrapAsPredicateRecord(containsCall);

        VerificationResult result = CelVerifier.verify(wire);

        assertFalse(result.accepted(), () -> "a 2^32-adjacent cost MUST be rejected, got: " + result);
        assertEquals(VerificationResult.Reason.COST_EXCEEDED, result.reason());
        assertTrue(result.record().isPresent(), "decode succeeds -- the needle is within maxScalarBytes");
    }

    @Test
    void cheapPredicate_acceptedWithSmallCost() {
        byte[] expr = TestDer.ge(TestDer.fieldRef("pressureHpa"), TestDer.litDouble(950.0));
        byte[] wire = TestDer.wrapAsPredicateRecord(expr);
        VerificationResult result = CelVerifier.verify(wire);
        assertTrue(result.accepted());
        assertTrue(result.cost().orElseThrow() < 20L);
    }
}
