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
 * STD-011 §11.2/§12.4's schema-optional static type checking, exercised
 * through {@link CelVerifier}: the accept/reject/defer-to-dynamic triad for
 * both schema-independent (pure-grammar) and schema-dependent (field
 * reference) type information, plus §8.2 ambiguity and Appendix B §B.6.6
 * list-literal homogeneity (both genuinely additive -- T2 checks neither).
 */
class CelVerifierStaticTypeTest {

    // ---- schema-independent (grammar-only) known-type mismatch ---------------

    @Test
    void notAppliedToKnownNonBoolLiteral_rejectedWithNoSchemaNeeded() {
        byte[] expr = TestDer.notOp(TestDer.litInt(5)); // NOT(5) -- int is never bool, provable from grammar alone
        byte[] wire = TestDer.wrapAsPredicateRecord(expr);
        VerificationResult result = CelVerifier.verify(wire); // no schema at all
        assertFalse(result.accepted());
        assertEquals(VerificationResult.Reason.STATIC_TYPE_MISMATCH, result.reason());
    }

    @Test
    void mixedIntDoubleArithmetic_rejectedWithNoSchemaNeeded() {
        byte[] expr = TestDer.eq(TestDer.add(TestDer.litInt(1), TestDer.litDouble(2.0)), TestDer.litInt(3));
        byte[] wire = TestDer.wrapAsPredicateRecord(expr);
        VerificationResult result = CelVerifier.verify(wire);
        assertFalse(result.accepted());
        assertEquals(VerificationResult.Reason.STATIC_TYPE_MISMATCH, result.reason());
    }

    // ---- list-literal homogeneity (Appendix B §B.6.6: T6's job, not T2's) ----

    @Test
    void listLiteralMixedIntAndNull_decodesButIsRejectedByVerifier() {
        // IN's literalList operand embeds a ListLitNode directly -- decoder does not check homogeneity (§B.6.6).
        byte[] expr = TestDer.inLiteralList(TestDer.litInt(1), TestDer.litInt(2), TestDer.litNull());
        byte[] wire = TestDer.wrapAsPredicateRecord(expr);

        VerificationResult result = CelVerifier.verify(wire);
        assertFalse(result.accepted());
        assertEquals(VerificationResult.Reason.STATIC_TYPE_MISMATCH, result.reason());
        assertTrue(result.record().isPresent(), "T2 decode has no homogeneity opinion (§B.6.6) -- it must succeed here");
    }

    // ---- schema-dependent field-reference type checking: the full triad ------

    private static FakeSchemaView schemaWith(CelType fieldType) {
        return FakeSchemaView.builder().field("Candidate", "f", fieldType).build();
    }

    @Test
    void fieldTypeKnownAndMismatched_rejected() {
        FakeSchemaView schema = schemaWith(CelType.STRING);
        byte[] expr = TestDer.eq(TestDer.fieldRef("f"), TestDer.litInt(5));
        byte[] wire = TestDer.wrapAsPredicateRecord(expr);

        VerificationResult result = CelVerifier.verify(wire, schema);
        assertFalse(result.accepted());
        assertEquals(VerificationResult.Reason.STATIC_TYPE_MISMATCH, result.reason());
    }

    @Test
    void fieldTypeUnknown_definedAsAnyInSchema_deferredAndAccepted() {
        FakeSchemaView schema = FakeSchemaView.builder().unresolvedTypeField("Candidate", "f").build();
        byte[] expr = TestDer.eq(TestDer.fieldRef("f"), TestDer.litInt(5));
        byte[] wire = TestDer.wrapAsPredicateRecord(expr);

        VerificationResult result = CelVerifier.verify(wire, schema);
        assertTrue(result.accepted(), () -> "unknown/Any-typed field must defer, not reject: " + result);
    }

    @Test
    void fieldAbsentFromSchemaEntirely_deferredAndAccepted() {
        FakeSchemaView schema = FakeSchemaView.builder().namespace("Candidate").build(); // "f" not declared at all
        byte[] expr = TestDer.eq(TestDer.fieldRef("f"), TestDer.litInt(5));
        byte[] wire = TestDer.wrapAsPredicateRecord(expr);

        VerificationResult result = CelVerifier.verify(wire, schema);
        assertTrue(result.accepted(), () -> "a field absent from THIS schema snapshot must defer (schema evolution), not reject: " + result);
    }

    @Test
    void noSchemaAtAll_fieldReferenceTypeDefersAndAccepted() {
        byte[] expr = TestDer.eq(TestDer.fieldRef("f"), TestDer.litInt(5));
        byte[] wire = TestDer.wrapAsPredicateRecord(expr);

        VerificationResult result = CelVerifier.verify(wire); // no schema parameter at all
        assertTrue(result.accepted());
    }

    @Test
    void fieldTypeKnownAndMatching_accepted() {
        FakeSchemaView schema = schemaWith(CelType.INT);
        byte[] expr = TestDer.eq(TestDer.fieldRef("f"), TestDer.litInt(5));
        byte[] wire = TestDer.wrapAsPredicateRecord(expr);

        VerificationResult result = CelVerifier.verify(wire, schema);
        assertTrue(result.accepted());
    }

    @Test
    void intDoubleCrossTypeComparison_acceptedPerSpecException() {
        FakeSchemaView schema = schemaWith(CelType.INT);
        byte[] expr = TestDer.lt(TestDer.fieldRef("f"), TestDer.litDouble(5.5)); // int < double is the one allowed cross-pair (§6.2)
        byte[] wire = TestDer.wrapAsPredicateRecord(expr);

        VerificationResult result = CelVerifier.verify(wire, schema);
        assertTrue(result.accepted());
    }

    // ---- §8.2 ambiguity: a hard reject, distinct from "unknown" -----------------

    @Test
    void ambiguousUnqualifiedFieldName_rejected() {
        FakeSchemaView schema = FakeSchemaView.builder()
                .field("Leaf", "dup", CelType.INT)
                .field("Base", "dup", CelType.INT)
                .build();
        byte[] expr = TestDer.eq(TestDer.fieldRef("dup"), TestDer.litInt(1));
        byte[] wire = TestDer.wrapAsPredicateRecord(expr);

        VerificationResult result = CelVerifier.verify(wire, schema);
        assertFalse(result.accepted());
        assertEquals(VerificationResult.Reason.STATIC_TYPE_MISMATCH, result.reason());
        assertTrue(result.diagnostic().toLowerCase().contains("ambiguous"), result.diagnostic());
    }

    // ---- §8.4 nested access through a statically known nested schema -----------

    @Test
    void nestedFieldReference_resolvedThroughNestedSchema_matching_accepted() {
        FakeSchemaView nested = FakeSchemaView.builder().field("Nested", "x", CelType.INT).build();
        FakeSchemaView root = FakeSchemaView.builder().objectField("Candidate", "obj", nested).build();

        byte[] expr = TestDer.eq(TestDer.fieldRef("obj", "x"), TestDer.litInt(5));
        byte[] wire = TestDer.wrapAsPredicateRecord(expr);

        VerificationResult result = CelVerifier.verify(wire, root);
        assertTrue(result.accepted());
    }

    @Test
    void nestedFieldReference_resolvedThroughNestedSchema_mismatched_rejected() {
        FakeSchemaView nested = FakeSchemaView.builder().field("Nested", "x", CelType.INT).build();
        FakeSchemaView root = FakeSchemaView.builder().objectField("Candidate", "obj", nested).build();

        byte[] expr = TestDer.eq(TestDer.fieldRef("obj", "x"), TestDer.litString("nope"));
        byte[] wire = TestDer.wrapAsPredicateRecord(expr);

        VerificationResult result = CelVerifier.verify(wire, root);
        assertFalse(result.accepted());
        assertEquals(VerificationResult.Reason.STATIC_TYPE_MISMATCH, result.reason());
    }

    @Test
    void nestedFieldReference_throughUnresolvableNestedSchema_defersAndAccepted() {
        // DerSchemaChainView's real-world case: a plain @AtomicSerial field whose concrete
        // nested class isn't statically known -- fieldType() is OBJECT but nestedSchema() is empty.
        FakeSchemaView root = FakeSchemaView.builder().field("Candidate", "obj", CelType.OBJECT).build();

        byte[] expr = TestDer.eq(TestDer.fieldRef("obj", "x"), TestDer.litInt(5));
        byte[] wire = TestDer.wrapAsPredicateRecord(expr);

        VerificationResult result = CelVerifier.verify(wire, root);
        assertTrue(result.accepted(), () -> "an unresolvable nested schema must defer the rest of the chain, not reject: " + result);
    }
}
