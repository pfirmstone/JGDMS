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

package au.net.zeus.jgdms.cel.eval;

import au.net.zeus.jgdms.cel.CelError;
import au.net.zeus.jgdms.cel.CelValue;
import au.net.zeus.jgdms.cel.EvalOutcome;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.testsupport.FakeCandidateProjection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JGDMS-STD-011 §8 (field resolution: uniqueness-or-error, qualified
 * resolution, nested access) and §6.8/§4.6's null-vs-absent-vs-present
 * matrix and {@code has()} table.
 */
class FieldResolutionTest {

    private static final Evaluator EVAL = new Evaluator();

    private static ExprNode fieldRef(String name) {
        return new ExprNode.FieldRef(List.of(new ExprNode.SelectorStep.Unqual(name)));
    }

    private static ExprNode qualifiedFieldRef(String className, String fieldName) {
        return new ExprNode.FieldRef(List.of(new ExprNode.SelectorStep.Qual(className, fieldName)));
    }

    private static ExprNode has(ExprNode.FieldRef target) {
        return new ExprNode.Has(target);
    }

    // ---- §4.6's three situations, and §8.2's absent case ---------------------

    @Test
    void presentNonNull_referenceYieldsValue_hasIsTrue() {
        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("com.example.Sensor", "pressureHpa", new CelValue.DoubleV(1013.25))
                .build();
        EvalOutcome.Value v = assertInstanceOf(EvalOutcome.Value.class,
                EVAL.evaluate(fieldRef("pressureHpa"), candidate));
        assertEquals(1013.25, ((CelValue.DoubleV) v.value()).value());

        EvalOutcome.Value hasResult = assertInstanceOf(EvalOutcome.Value.class,
                EVAL.evaluate(has((ExprNode.FieldRef) fieldRef("pressureHpa")), candidate));
        assertTrue(((CelValue.BoolV) hasResult.value()).value());
    }

    @Test
    void presentNull_referenceYieldsNull_hasIsFalse() {
        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("com.example.Sensor", "note", CelValue.NullV.INSTANCE)
                .build();
        EvalOutcome.Value v = assertInstanceOf(EvalOutcome.Value.class,
                EVAL.evaluate(fieldRef("note"), candidate));
        assertInstanceOf(CelValue.NullV.class, v.value());

        EvalOutcome.Value hasResult = assertInstanceOf(EvalOutcome.Value.class,
                EVAL.evaluate(has((ExprNode.FieldRef) fieldRef("note")), candidate));
        assertFalse(((CelValue.BoolV) hasResult.value()).value());
    }

    @Test
    void absent_referenceYieldsAbsentFieldError_hasIsFalse() {
        CandidateProjection candidate = FakeCandidateProjection.builder().namespace("com.example.Sensor").build();
        EvalOutcome.Error e = assertInstanceOf(EvalOutcome.Error.class,
                EVAL.evaluate(fieldRef("batteryMv"), candidate));
        assertEquals(CelError.ABSENT_FIELD, e.code());

        EvalOutcome.Value hasResult = assertInstanceOf(EvalOutcome.Value.class,
                EVAL.evaluate(has((ExprNode.FieldRef) fieldRef("batteryMv")), candidate));
        assertFalse(((CelValue.BoolV) hasResult.value()).value());
    }

    // ---- §8.2 ambiguity: two namespaces declaring the same unqualified name ---

    @Test
    void ambiguousUnqualifiedReference_isAmbiguousFieldError() {
        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("com.example.Beta", "timestamp", new CelValue.IntV(1700000000000L))
                .field("com.example.Alpha", "timestamp", new CelValue.IntV(1600000000000L))
                .build();
        EvalOutcome.Error e = assertInstanceOf(EvalOutcome.Error.class,
                EVAL.evaluate(fieldRef("timestamp"), candidate));
        assertEquals(CelError.AMBIGUOUS_FIELD, e.code());
    }

    @Test
    void ambiguousField_hasAlsoErrors() {
        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("com.example.Beta", "timestamp", new CelValue.IntV(1700000000000L))
                .field("com.example.Alpha", "timestamp", new CelValue.IntV(1600000000000L))
                .build();
        EvalOutcome.Error e = assertInstanceOf(EvalOutcome.Error.class,
                EVAL.evaluate(has((ExprNode.FieldRef) fieldRef("timestamp")), candidate));
        assertEquals(CelError.AMBIGUOUS_FIELD, e.code());
    }

    @Test
    void qualifiedResolution_disambiguates() {
        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("com.example.Beta", "timestamp", new CelValue.IntV(1700000000000L))
                .field("com.example.Alpha", "timestamp", new CelValue.IntV(1600000000000L))
                .build();
        EvalOutcome.Value v = assertInstanceOf(EvalOutcome.Value.class,
                EVAL.evaluate(qualifiedFieldRef("com.example.Beta", "timestamp"), candidate));
        assertEquals(1700000000000L, ((CelValue.IntV) v.value()).value());
    }

    @Test
    void unambiguousOnSingleNamespaceCandidate() {
        // The same expression is unambiguous against a plain Alpha-only candidate (§8.2's per-candidate note).
        CandidateProjection alphaOnly = FakeCandidateProjection.builder()
                .field("com.example.Alpha", "timestamp", new CelValue.IntV(1600000000000L))
                .build();
        EvalOutcome.Value v = assertInstanceOf(EvalOutcome.Value.class,
                EVAL.evaluate(fieldRef("timestamp"), alphaOnly));
        assertEquals(1600000000000L, ((CelValue.IntV) v.value()).value());
    }

    // ---- §8.4 nested access ----------------------------------------------------

    @Test
    void nestedAccess_throughObject() {
        CandidateProjection nested = FakeCandidateProjection.builder()
                .field("com.example.Point", "x", new CelValue.IntV(5))
                .build();
        CandidateProjection outer = FakeCandidateProjection.builder()
                .field("com.example.Line", "p", new CelValue.ObjectV(nested))
                .build();
        ExprNode.FieldRef ab = new ExprNode.FieldRef(List.of(
                new ExprNode.SelectorStep.Unqual("p"), new ExprNode.SelectorStep.Unqual("x")));
        EvalOutcome.Value v = assertInstanceOf(EvalOutcome.Value.class, EVAL.evaluate(ab, outer));
        assertEquals(5L, ((CelValue.IntV) v.value()).value());
    }

    @Test
    void nestedAccess_nullIntermediate_isAbsentField() {
        CandidateProjection outer = FakeCandidateProjection.builder()
                .field("com.example.Line", "p", CelValue.NullV.INSTANCE)
                .build();
        ExprNode.FieldRef ab = new ExprNode.FieldRef(List.of(
                new ExprNode.SelectorStep.Unqual("p"), new ExprNode.SelectorStep.Unqual("x")));
        EvalOutcome.Error e = assertInstanceOf(EvalOutcome.Error.class, EVAL.evaluate(ab, outer));
        assertEquals(CelError.ABSENT_FIELD, e.code());
    }

    @Test
    void nestedAccess_nullIntermediate_hasIsFalse() {
        CandidateProjection outer = FakeCandidateProjection.builder()
                .field("com.example.Line", "p", CelValue.NullV.INSTANCE)
                .build();
        ExprNode.FieldRef ab = new ExprNode.FieldRef(List.of(
                new ExprNode.SelectorStep.Unqual("p"), new ExprNode.SelectorStep.Unqual("x")));
        EvalOutcome.Value v = assertInstanceOf(EvalOutcome.Value.class, EVAL.evaluate(has(ab), outer));
        assertFalse(((CelValue.BoolV) v.value()).value());
    }

    @Test
    void nestedAccess_nonObjectIntermediate_isTypeMismatch() {
        CandidateProjection outer = FakeCandidateProjection.builder()
                .field("com.example.Line", "p", new CelValue.IntV(5)) // not an object
                .build();
        ExprNode.FieldRef ab = new ExprNode.FieldRef(List.of(
                new ExprNode.SelectorStep.Unqual("p"), new ExprNode.SelectorStep.Unqual("x")));
        EvalOutcome.Error e = assertInstanceOf(EvalOutcome.Error.class, EVAL.evaluate(ab, outer));
        assertEquals(CelError.TYPE_MISMATCH, e.code());
    }

    // ---- candidate undecodable --------------------------------------------------

    @Test
    void undecodableCandidate_everyAccessErrors() {
        CandidateProjection undecodable = FakeCandidateProjection.undecodable();
        EvalOutcome.Error e1 = assertInstanceOf(EvalOutcome.Error.class,
                EVAL.evaluate(fieldRef("anything"), undecodable));
        assertEquals(CelError.CANDIDATE_UNDECODABLE, e1.code());

        EvalOutcome.Error e2 = assertInstanceOf(EvalOutcome.Error.class,
                EVAL.evaluate(has((ExprNode.FieldRef) fieldRef("anything")), undecodable));
        assertEquals(CelError.CANDIDATE_UNDECODABLE, e2.code());
    }

    // ---- null equality is the sole cross-type exception (§4.6) -----------------

    @Test
    void nullEqualsNull() {
        ExprNode eq = new ExprNode.Eq(new ExprNode.LitNull(), new ExprNode.LitNull());
        EvalOutcome.Value v = assertInstanceOf(EvalOutcome.Value.class,
                EVAL.evaluate(eq, FakeCandidateProjection.builder().build()));
        assertTrue(((CelValue.BoolV) v.value()).value());
    }

    @Test
    void nullNeverEqualsNonNullValue_regardlessOfType() {
        ExprNode eq = new ExprNode.Eq(new ExprNode.LitNull(), new ExprNode.LitInt(0));
        EvalOutcome.Value v = assertInstanceOf(EvalOutcome.Value.class,
                EVAL.evaluate(eq, FakeCandidateProjection.builder().build()));
        assertFalse(((CelValue.BoolV) v.value()).value());
    }

    @Test
    void nonNullOperationOnNull_otherThanEqualityIsTypeMismatch() {
        ExprNode lt = new ExprNode.Lt(new ExprNode.LitNull(), new ExprNode.LitInt(5));
        EvalOutcome.Error e = assertInstanceOf(EvalOutcome.Error.class,
                EVAL.evaluate(lt, FakeCandidateProjection.builder().build()));
        assertEquals(CelError.TYPE_MISMATCH, e.code());
    }
}
