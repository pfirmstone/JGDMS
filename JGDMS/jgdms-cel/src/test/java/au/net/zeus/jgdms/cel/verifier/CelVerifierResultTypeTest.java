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
 * STD-011 §9.4/§9.5's declared-result-type consistency, cross-checked
 * against the expression's own statically inferred root type -- {@code
 * CelDecoder} decodes and structurally validates {@code
 * EvaluationContext}/{@code DeclaredResultType} (Appendix B §B.6.4) but
 * never performs this cross-check; it is wholly {@link CelVerifier}'s own,
 * new obligation.
 */
class CelVerifierResultTypeTest {

    /** {@code DeclaredResultType.scalar[0] IMPLICIT ScalarType(wireValue)}, EXPLICIT-wrapped in {@code context[1]} -- mirrors {@code TestDer.transformContextListDouble()}'s construction for the scalar arm. */
    private static byte[] transformContextScalar(int scalarWireValue) {
        byte[] inner = TestDer.ctxPrim(0, TestDer.minimalIntContent(scalarWireValue));
        return TestDer.tlv(TestDer.contextTag(1, true), inner);
    }

    private static final int BOOL_T = 0;
    private static final int INT_T = 1;

    @Test
    void predicateRootNotBool_rejected() {
        byte[] wire = TestDer.filterRecord(1, TestDer.predicateContext(), TestDer.litInt(5));
        VerificationResult result = CelVerifier.verify(wire);
        assertFalse(result.accepted());
        assertEquals(VerificationResult.Reason.RESULT_TYPE_MISMATCH, result.reason());
        assertTrue(result.cost().isPresent(), "the cost gate must have already passed before this check runs");
    }

    @Test
    void predicateRootBool_accepted() {
        byte[] wire = TestDer.filterRecord(1, TestDer.predicateContext(), TestDer.litBool(true));
        VerificationResult result = CelVerifier.verify(wire);
        assertTrue(result.accepted());
    }

    @Test
    void transformDeclaredVsActualMismatch_rejected() {
        // Declares int, root expression is a string literal.
        byte[] wire = TestDer.filterRecord(1, transformContextScalar(INT_T), TestDer.litString("hello"));
        VerificationResult result = CelVerifier.verify(wire);
        assertFalse(result.accepted());
        assertEquals(VerificationResult.Reason.RESULT_TYPE_MISMATCH, result.reason());
    }

    @Test
    void transformBoolAcceptedWhenDeclaredBool() {
        byte[] wire = TestDer.filterRecord(1, transformContextScalar(BOOL_T), TestDer.litBool(false));
        VerificationResult result = CelVerifier.verify(wire);
        assertTrue(result.accepted());
    }

    @Test
    void transformListDeclared_rootIsListLiteralOfMatchingElementType_accepted() {
        byte[] listLit = TestDer.listLit(TestDer.litDouble(1.0), TestDer.litDouble(2.0));
        byte[] wire = TestDer.filterRecord(1, TestDer.transformContextListDouble(), listLit);
        VerificationResult result = CelVerifier.verify(wire);
        assertTrue(result.accepted());
    }

    @Test
    void transformListDeclared_rootIsNotAList_rejected() {
        byte[] wire = TestDer.filterRecord(1, TestDer.transformContextListDouble(), TestDer.litDouble(1.0));
        VerificationResult result = CelVerifier.verify(wire);
        assertFalse(result.accepted());
        assertEquals(VerificationResult.Reason.RESULT_TYPE_MISMATCH, result.reason());
    }

    @Test
    void transformListDeclared_elementTypeMismatch_rejected() {
        // Declares list<double>; root is a list literal of ints.
        byte[] listLit = TestDer.listLit(TestDer.litInt(1), TestDer.litInt(2));
        byte[] wire = TestDer.filterRecord(1, TestDer.transformContextListDouble(), listLit);
        VerificationResult result = CelVerifier.verify(wire);
        assertFalse(result.accepted());
        assertEquals(VerificationResult.Reason.RESULT_TYPE_MISMATCH, result.reason());
    }
}
