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

import au.net.zeus.jgdms.cel.CelType;
import au.net.zeus.jgdms.cel.testsupport.FakeSchemaView;
import au.net.zeus.jgdms.cel.testsupport.TestDer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CelVerifier}'s fail-closed composition order (decode -> cost gate
 * -> static type-check -> declared-result-type consistency), asserted
 * <em>via each stage's distinct diagnostic prefix and via how far {@link
 * VerificationResult#record()}/{@link VerificationResult#cost()} were
 * populated</em> -- not merely via the final accept/reject verdict, so a
 * regression that runs stages out of order (e.g. type-checking before the
 * cost gate) would be caught even if it happened to reject for the "right"
 * reason.
 */
class CelVerifierCompositionTest {

    @Test
    void decodeFailure_neverReachesCostGate() {
        byte[] malformedWire = new byte[0]; // no SEQUENCE tag at all -- guaranteed decode failure
        VerificationResult result = CelVerifier.verify(malformedWire);

        assertFalse(result.accepted());
        assertEquals(VerificationResult.Reason.DECODE_REJECTED, result.reason());
        assertTrue(result.diagnostic().startsWith("decode rejected:"), result.diagnostic());
        assertTrue(result.record().isEmpty(), "decode failed -- there is no record to have reached the cost gate with");
        assertTrue(result.cost().isEmpty());
    }

    @Test
    void costGateFailure_neverReachesStaticTypeCheck() {
        // The 2^32-adjacent contains() expression: decodes fine, fails only the cost gate.
        // Its root type (bool, from `contains`) would pass static type-checking and predicate
        // result-type consistency if the pipeline ever reached those stages -- it must not.
        byte[] receiver = TestDer.fieldRef("someString");
        byte[] needle = TestDer.litString("x".repeat(65536));
        byte[] containsCall = TestDer.call(22, receiver, needle);
        byte[] wire = TestDer.wrapAsPredicateRecord(containsCall);

        VerificationResult result = CelVerifier.verify(wire);

        assertFalse(result.accepted());
        assertEquals(VerificationResult.Reason.COST_EXCEEDED, result.reason());
        assertTrue(result.diagnostic().startsWith("cost gate rejected:"), result.diagnostic());
        assertTrue(result.record().isPresent(), "decode succeeded");
        assertTrue(result.cost().isEmpty(), "no valid cost total -- the gate itself is what rejected");
    }

    @Test
    void staticTypeCheckFailure_neverReachesResultTypeConsistency() {
        // NOT(5): fails static type-checking (int is never bool). Its "root type" for consistency
        // purposes would be bool (NOT's fixed return type) if the pipeline ever reached that stage --
        // it must not get there; the rejection must be attributed to the type-check stage, not result-type.
        byte[] expr = TestDer.notOp(TestDer.litInt(5));
        byte[] wire = TestDer.wrapAsPredicateRecord(expr);

        VerificationResult result = CelVerifier.verify(wire);

        assertFalse(result.accepted());
        assertEquals(VerificationResult.Reason.STATIC_TYPE_MISMATCH, result.reason());
        assertTrue(result.diagnostic().startsWith("static type check rejected:"), result.diagnostic());
        assertTrue(result.record().isPresent(), "decode succeeded");
        assertTrue(result.cost().isPresent(), "the cost gate must have already passed");
    }

    @Test
    void resultTypeConsistencyFailure_isTheLastStage() {
        byte[] wire = TestDer.filterRecord(1, TestDer.predicateContext(), TestDer.litInt(5));
        VerificationResult result = CelVerifier.verify(wire);

        assertFalse(result.accepted());
        assertEquals(VerificationResult.Reason.RESULT_TYPE_MISMATCH, result.reason());
        assertTrue(result.diagnostic().startsWith("declared-result-type check rejected:"), result.diagnostic());
        assertTrue(result.record().isPresent());
        assertTrue(result.cost().isPresent(), "the cost gate and static type-check must have already passed");
    }

    @Test
    void allStagesPass_accepted() {
        FakeSchemaView schema = FakeSchemaView.builder().field("Candidate", "f", CelType.INT).build();
        byte[] expr = TestDer.gt(TestDer.fieldRef("f"), TestDer.litInt(0));
        byte[] wire = TestDer.wrapAsPredicateRecord(expr);

        VerificationResult result = CelVerifier.verify(wire, schema);

        assertTrue(result.accepted());
        assertTrue(result.record().isPresent());
        assertTrue(result.cost().isPresent());
    }

    @Test
    void verifyWithNullWire_throwsNpe() {
        assertThrows(NullPointerException.class, () -> CelVerifier.verify(null));
    }
}
