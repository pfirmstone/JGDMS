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
import au.net.zeus.jgdms.cel.CelType;
import au.net.zeus.jgdms.cel.CelValue;
import au.net.zeus.jgdms.cel.EvalOutcome;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.math.MathProvider;
import au.net.zeus.jgdms.cel.testsupport.FakeCandidateProjection;
import au.net.zeus.jgdms.cel.wire.FunctionRegistry.Name;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static au.net.zeus.jgdms.cel.eval.Ast.b;
import static au.net.zeus.jgdms.cel.eval.Ast.call;
import static au.net.zeus.jgdms.cel.eval.Ast.d;
import static au.net.zeus.jgdms.cel.eval.Ast.div;
import static au.net.zeus.jgdms.cel.eval.Ast.divByZero;
import static au.net.zeus.jgdms.cel.eval.Ast.f;
import static au.net.zeus.jgdms.cel.eval.Ast.i;
import static au.net.zeus.jgdms.cel.eval.Ast.list;
import static au.net.zeus.jgdms.cel.eval.Ast.q;
import static au.net.zeus.jgdms.cel.eval.Ast.s;
import static au.net.zeus.jgdms.cel.eval.Ast.sinOfOne;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Evaluation ORDER and exceptions, the part of the contract an outcome comparison alone cannot show: which
 * operand is looked at first, which is never evaluated, when a type is checked, and what a provider-less
 * transcendental does. {@code MathProvider.none()} is the probe: {@code sin(1.0)} throws
 * {@link IllegalStateException} if and only if an evaluator reaches it, so a missing exception proves an
 * operand was skipped and an unexpected one proves it was evaluated early. Each case states the result the
 * walker gives, and the compiled evaluator runs the same tests through {@code CompiledEvaluationOrderTest}.
 */
class EvaluationOrderTest {

    private static final CandidateProjection EMPTY = FakeCandidateProjection.builder().build();

    /** The tests are written once; the compiled-evaluator subclass overrides this to run them again. */
    EvaluatorUnderTest.Factory evaluators() {
        return EvaluatorUnderTest.WALKER;
    }

    /** A provider is installed. */
    private EvaluatorUnderTest provided;
    /** No provider: any transcendental that is reached throws. */
    private EvaluatorUnderTest bare;

    @BeforeEach
    void createEvaluators() {
        provided = evaluators().create(MathProvider.nonConformantForTestingOnly());
        bare = evaluators().create(MathProvider.none());
    }

    private static void assertError(CelError expected, EvalOutcome actual) {
        assertEquals(EvalOutcome.error(expected), actual);
    }

    private static void assertBool(boolean expected, EvalOutcome actual) {
        assertEquals(EvalOutcome.of(new CelValue.BoolV(expected)), actual);
    }

    private static final List<BiFunction<ExprNode, ExprNode, ExprNode>> STRICT_BINARY = List.of(
            ExprNode.Add::new, ExprNode.Sub::new, ExprNode.Mul::new, ExprNode.Div::new, ExprNode.Mod::new,
            ExprNode.Lt::new, ExprNode.Le::new, ExprNode.Gt::new, ExprNode.Ge::new, ExprNode.Eq::new, ExprNode.Ne::new);

    // ======================================================================
    // List literal (§9 item 1)
    // ======================================================================

    @Test
    void listLiteral_checksEachElementBeforeEvaluatingTheNext() {
        // [1, "a", 1/0]: the type disagreement at element 2 ends it before 1/0 is reached.
        assertError(CelError.TYPE_MISMATCH, bare.evaluate(list(i(1), s("a"), divByZero()), EMPTY));
        // A non-scalar element is rejected at once.
        assertError(CelError.TYPE_MISMATCH, bare.evaluate(list(new ExprNode.LitNull(), divByZero()), EMPTY));
        // The first error returns before the second element is looked at.
        assertError(CelError.DIVISION_BY_ZERO, bare.evaluate(list(divByZero(), s("a")), EMPTY));
        // Nor is a later element evaluated: this one would throw if it were.
        assertError(CelError.TYPE_MISMATCH, bare.evaluate(list(i(1), s("a"), sinOfOne()), EMPTY));
    }

    @Test
    void listLiteral_elementsAreScalarValuesWithTheCommonType() {
        EvalOutcome.Value v = assertInstanceOf(EvalOutcome.Value.class,
                provided.evaluate(list(d(1.0), d(2.5), d(-0.0)), EMPTY));
        CelValue.ListV l = assertInstanceOf(CelValue.ListV.class, v.value());
        assertEquals(CelType.DOUBLE, l.elementType());
        assertEquals(3, l.elements().size());
        assertEquals(Double.doubleToRawLongBits(-0.0),
                Double.doubleToRawLongBits(((CelValue.DoubleV) l.elements().get(2)).value()), "signed zero kept");
    }

    @Test
    void listLiteral_nanElementsAreCanonicalised() {
        // On x86 0.0/0.0 is 0xFFF8...: the quiet NaN with the sign bit set, not the canonical 0x7FF8...
        EvalOutcome.Value v = assertInstanceOf(EvalOutcome.Value.class,
                provided.evaluate(list(div(d(0.0), d(0.0))), EMPTY));
        CelValue.ListV l = assertInstanceOf(CelValue.ListV.class, v.value());
        assertEquals(CelValue.DoubleV.CANONICAL_NAN_BITS,
                Double.doubleToRawLongBits(((CelValue.DoubleV) l.elements().get(0)).value()));
    }

    // ======================================================================
    // Strict constructs (§9.3): left, then right, and only then the types
    // ======================================================================

    @Test
    void strictBinary_returnsTheLeftErrorWithoutEvaluatingTheRight() {
        for (BiFunction<ExprNode, ExprNode, ExprNode> op : STRICT_BINARY) {
            ExprNode e = op.apply(divByZero(), sinOfOne());
            assertError(CelError.DIVISION_BY_ZERO, bare.evaluate(e, EMPTY)); // would throw if the right were evaluated
        }
    }

    @Test
    void strictBinary_checksTypesOnlyAfterBothOperandsAreEvaluated() {
        // The left is a bool, wrong for every operator here, but the right errors first.
        for (BiFunction<ExprNode, ExprNode, ExprNode> op : STRICT_BINARY) {
            ExprNode e = op.apply(b(true), divByZero());
            assertError(CelError.DIVISION_BY_ZERO, bare.evaluate(e, EMPTY));
        }
    }

    @Test
    void strictBinary_aWrongTypedLeftStillEvaluatesTheRight() {
        // Reaching the right operand is observable: with no provider it throws.
        for (BiFunction<ExprNode, ExprNode, ExprNode> op : STRICT_BINARY) {
            ExprNode e = op.apply(b(true), sinOfOne());
            assertThrows(IllegalStateException.class, () -> bare.evaluate(e, EMPTY));
        }
    }

    @Test
    void unaryOperatorsPassTheirOperandsErrorThrough() {
        assertError(CelError.DIVISION_BY_ZERO, bare.evaluate(new ExprNode.Not(divByZero()), EMPTY));
        assertError(CelError.DIVISION_BY_ZERO, bare.evaluate(new ExprNode.Neg(divByZero()), EMPTY));
    }

    @Test
    void twoArgumentCalls_evaluateBothArgumentsBeforeCheckingEitherType() {
        assertError(CelError.DIVISION_BY_ZERO, bare.evaluate(call(Name.MIN_INT, b(true), divByZero()), EMPTY));
        assertError(CelError.DIVISION_BY_ZERO, bare.evaluate(call(Name.ATAN2, i(1), divByZero()), EMPTY));
        assertError(CelError.DIVISION_BY_ZERO, bare.evaluate(call(Name.CONTAINS, i(1), divByZero()), EMPTY));
        assertError(CelError.DIVISION_BY_ZERO, bare.evaluate(call(Name.MAX_DOUBLE, divByZero(), sinOfOne()), EMPTY));
    }

    @Test
    void unknownFunctionId_evaluatesItsArgumentsFirst_thenIsATypeMismatch() {
        assertError(CelError.DIVISION_BY_ZERO, bare.evaluate(new ExprNode.Call(99, List.of(divByZero())), EMPTY));
        assertError(CelError.TYPE_MISMATCH, bare.evaluate(new ExprNode.Call(99, List.of(i(1))), EMPTY));
        assertError(CelError.DIVISION_BY_ZERO, bare.evaluate(new ExprNode.Call(0, List.of(i(1), divByZero())), EMPTY));
        assertThrows(IllegalStateException.class,
                () -> bare.evaluate(new ExprNode.Call(25, List.of(i(1), sinOfOne())), EMPTY));
    }

    // ======================================================================
    // && and ||: both sides, always (§6.4); ?: only the taken branch (§6.7)
    // ======================================================================

    @Test
    void andOr_evaluateTheRightSideEvenWhenTheLeftDecides() {
        ExprNode sinPositive = new ExprNode.Gt(sinOfOne(), d(0.0));
        assertThrows(IllegalStateException.class, () -> bare.evaluate(new ExprNode.And(b(false), sinPositive), EMPTY));
        assertThrows(IllegalStateException.class, () -> bare.evaluate(new ExprNode.Or(b(true), sinPositive), EMPTY));
        // The result is the same with a provider installed.
        assertBool(false, provided.evaluate(new ExprNode.And(b(false), sinPositive), EMPTY));
        assertBool(true, provided.evaluate(new ExprNode.Or(b(true), sinPositive), EMPTY));
    }

    @Test
    void andOr_aNonBoolOperandIsATypeMismatchAtItsOwnPosition() {
        assertError(CelError.TYPE_MISMATCH, bare.evaluate(new ExprNode.And(i(1), b(true)), EMPTY));
        assertBool(false, bare.evaluate(new ExprNode.And(i(1), b(false)), EMPTY)); // a determining right absorbs it
        assertError(CelError.DIVISION_BY_ZERO, bare.evaluate(new ExprNode.Or(divByZero(), i(1)), EMPTY)); // left error pinned
    }

    @Test
    void cond_evaluatesOnlyTheTakenBranch() {
        assertEquals(EvalOutcome.of(new CelValue.DoubleV(1.0)),
                bare.evaluate(new ExprNode.Cond(b(true), d(1.0), sinOfOne()), EMPTY));
        assertEquals(EvalOutcome.of(new CelValue.DoubleV(2.0)),
                bare.evaluate(new ExprNode.Cond(b(false), sinOfOne(), d(2.0)), EMPTY));
        assertError(CelError.TYPE_MISMATCH, bare.evaluate(new ExprNode.Cond(i(1), sinOfOne(), sinOfOne()), EMPTY));
        assertError(CelError.DIVISION_BY_ZERO, bare.evaluate(new ExprNode.Cond(divByZero(), sinOfOne(), sinOfOne()), EMPTY));
    }

    // ======================================================================
    // in (§6.5)
    // ======================================================================

    private static ExprNode inLiteral(ExprNode needle, ExprNode... elements) {
        return new ExprNode.In(needle, new ExprNode.InListOperand.LiteralList(list(elements)));
    }

    @Test
    void inLiteral_rememberTheFirstErrorAndKeepScanning_anEqualReturnsTrueAtOnce() {
        assertBool(true, bare.evaluate(inLiteral(i(1), divByZero(), i(1)), EMPTY));
        assertError(CelError.DIVISION_BY_ZERO, bare.evaluate(inLiteral(i(1), i(2), divByZero()), EMPTY));
        assertError(CelError.TYPE_MISMATCH, bare.evaluate(inLiteral(i(1), s("a"), divByZero()), EMPTY));
        assertError(CelError.DIVISION_BY_ZERO, bare.evaluate(inLiteral(i(1), divByZero(), s("a")), EMPTY));
        assertBool(false, bare.evaluate(inLiteral(i(1), i(2), i(3)), EMPTY));
    }

    @Test
    void inLiteral_stopsAtTheFirstEqualElement() {
        assertBool(true, bare.evaluate(inLiteral(d(1.0), d(1.0), sinOfOne()), EMPTY)); // the sin is never reached
        assertThrows(IllegalStateException.class, () -> bare.evaluate(inLiteral(d(2.0), d(1.0), sinOfOne()), EMPTY));
    }

    @Test
    void inLiteral_theNeedleIsEvaluatedFirstAndItsErrorEndsTheScan() {
        assertError(CelError.DIVISION_BY_ZERO, bare.evaluate(inLiteral(divByZero(), sinOfOne()), EMPTY));
    }

    @Test
    void inField_followsTheStoredElementsAndRejectsANonList() {
        CelValue.ListV mixed = new CelValue.ListV(CelType.INT, List.of(new CelValue.IntV(1), new CelValue.StringV("a")));
        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("C", "l", mixed)
                .field("C", "n", new CelValue.IntV(7))
                .build();
        ExprNode.InListOperand field = new ExprNode.InListOperand.FieldList(f("l"));
        assertBool(true, provided.evaluate(new ExprNode.In(i(1), field), candidate));
        assertError(CelError.TYPE_MISMATCH, provided.evaluate(new ExprNode.In(i(2), field), candidate)); // "a" does not compare
        assertBool(true, provided.evaluate(new ExprNode.In(s("a"), field), candidate)); // the first element is the mismatch, then equal
        assertError(CelError.TYPE_MISMATCH,
                provided.evaluate(new ExprNode.In(i(1), new ExprNode.InListOperand.FieldList(f("n"))), candidate));
        assertError(CelError.ABSENT_FIELD,
                provided.evaluate(new ExprNode.In(i(1), new ExprNode.InListOperand.FieldList(f("missing"))), candidate));
    }

    // ======================================================================
    // Transcendental domain checks come before the provider is consulted (§7.2)
    // ======================================================================

    @Test
    void transcendentals_domainIsCheckedBeforeTheProvider() {
        assertError(CelError.DOMAIN, bare.evaluate(call(Name.ASIN, d(1.5)), EMPTY));
        assertError(CelError.DOMAIN, bare.evaluate(call(Name.ACOS, d(-1.5)), EMPTY));
        assertError(CelError.DOMAIN, bare.evaluate(call(Name.SIN, d(Double.NaN)), EMPTY));
        assertError(CelError.DOMAIN, bare.evaluate(call(Name.TAN, d(Double.POSITIVE_INFINITY)), EMPTY));
        assertError(CelError.DOMAIN, bare.evaluate(call(Name.ATAN2, d(1.0), d(Double.NaN)), EMPTY));
        assertError(CelError.TYPE_MISMATCH, bare.evaluate(call(Name.COS, i(1)), EMPTY));
    }

    @Test
    void transcendentals_aValidArgumentWithNoProviderThrows() {
        for (Name fn : new Name[] {Name.SIN, Name.COS, Name.TAN, Name.ATAN}) {
            assertThrows(IllegalStateException.class, () -> bare.evaluate(call(fn, d(0.5)), EMPTY), fn.name());
        }
        assertThrows(IllegalStateException.class, () -> bare.evaluate(call(Name.ASIN, d(0.5)), EMPTY));
        assertThrows(IllegalStateException.class, () -> bare.evaluate(call(Name.ACOS, d(0.5)), EMPTY));
        assertThrows(IllegalStateException.class, () -> bare.evaluate(call(Name.ATAN2, d(0.5), d(0.5)), EMPTY));
    }

    // ======================================================================
    // Fields: what is asked, in which cases, and what comes back
    // ======================================================================

    @Test
    void nullIsEqualOnlyToNull_evenAgainstAListOrAnObject() {
        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("C", "n", CelValue.NullV.INSTANCE)
                .field("C", "l", new CelValue.ListV(CelType.INT, List.of(new CelValue.IntV(1))))
                .field("C", "o", new CelValue.ObjectV(FakeCandidateProjection.builder().build()))
                .build();
        assertBool(false, provided.evaluate(new ExprNode.Eq(f("n"), f("l")), candidate));
        assertBool(false, provided.evaluate(new ExprNode.Eq(f("l"), f("n")), candidate));
        assertBool(true, provided.evaluate(new ExprNode.Ne(f("n"), f("o")), candidate));
        assertBool(true, provided.evaluate(new ExprNode.Eq(f("n"), f("n")), candidate));
        // Two lists, two objects, a list and an object: not comparable.
        assertError(CelError.TYPE_MISMATCH, provided.evaluate(new ExprNode.Eq(f("l"), f("l")), candidate));
        assertError(CelError.TYPE_MISMATCH, provided.evaluate(new ExprNode.Eq(f("o"), f("o")), candidate));
        assertError(CelError.TYPE_MISMATCH, provided.evaluate(new ExprNode.Eq(f("l"), f("o")), candidate));
    }

    @Test
    void undecodableRoot_isNotAskedAboutByAnExpressionThatReadsNoField() {
        EvalOutcome outcome = provided.evaluate(new ExprNode.Add(i(1), i(2)), FakeCandidateProjection.undecodable());
        assertEquals(EvalOutcome.of(new CelValue.IntV(3)), outcome);
        assertError(CelError.CANDIDATE_UNDECODABLE, provided.evaluate(f("x"), FakeCandidateProjection.undecodable()));
    }

    @Test
    void undecodableNestedObject_isReturnedAsAValueButNotEnteredByAStep() {
        CandidateProjection nested = FakeCandidateProjection.undecodable();
        CelValue.ObjectV object = new CelValue.ObjectV(nested);
        CandidateProjection candidate = FakeCandidateProjection.builder().field("C", "o", object).build();
        ExprNode.FieldRef through = new ExprNode.FieldRef(List.of(
                new ExprNode.SelectorStep.Unqual("o"), new ExprNode.SelectorStep.Unqual("x")));

        EvalOutcome.Value atRoot = assertInstanceOf(EvalOutcome.Value.class, provided.evaluate(f("o"), candidate));
        assertSame(object, atRoot.value(), "the final step's object is returned unchecked, as the same instance");
        assertError(CelError.CANDIDATE_UNDECODABLE, provided.evaluate(through, candidate));
        assertError(CelError.CANDIDATE_UNDECODABLE, provided.evaluate(new ExprNode.Has(through), candidate));
    }

    @Test
    void qualifiedReference_toAClassOutsideTheChain_isAbsent_evenIfThatClassDeclaresTheField() {
        // FakeCandidateProjection answers false for a class outside the chain, so it cannot show a dropped
        // chain check; this projection declares the field in a class that is not in the chain.
        GenProjection p = new GenProjection(new TraceLog.Sink(), 0);
        p.chain = List.of("a");
        p.data.put("a", Map.of("other", new CelValue.IntV(1)));
        p.data.put("hidden", Map.of("x", new CelValue.IntV(2)));
        assertError(CelError.ABSENT_FIELD, provided.evaluate(q("hidden", "x"), p));
        assertError(CelError.ABSENT_FIELD, provided.evaluate(f("x"), p)); // unqualified resolution only visits the chain
        assertBool(false, provided.evaluate(new ExprNode.Has(q("hidden", "x")), p));
    }

    @Test
    void fieldValuesComeBackAsTheProjectionsOwnInstance() {
        CelValue.StringV text = new CelValue.StringV(new String("abc".toCharArray()));
        CelValue.BytesV bytes = new CelValue.BytesV(new byte[] {1, 2});
        CelValue.ListV items = new CelValue.ListV(CelType.INT, List.of(new CelValue.IntV(5)));
        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("C", "s", text).field("C", "y", bytes).field("C", "l", items).build();
        assertSame(text, assertInstanceOf(EvalOutcome.Value.class, provided.evaluate(f("s"), candidate)).value());
        assertSame(bytes, assertInstanceOf(EvalOutcome.Value.class, provided.evaluate(f("y"), candidate)).value());
        assertSame(items, assertInstanceOf(EvalOutcome.Value.class, provided.evaluate(f("l"), candidate)).value());
    }

    /**
     * RECORDED WALKER BEHAVIOUR, not an endorsement: a {@code ListV} field's elements are not canonicalised
     * (the walker canonicalises only a top-level DoubleV), which may breach §4.3.4 / §9.1. The compiled
     * evaluator reproduces it, and the question goes to the operator as a spec or defect question (design
     * section 9 item 19). If the walker is fixed, this test changes with it.
     */
    @Test
    void listFieldElements_keepANonCanonicalNaNPayload_asTheWalkerDoes() {
        long payload = 0x7FF8000000000001L;
        CelValue.ListV items = new CelValue.ListV(CelType.DOUBLE,
                List.of(new CelValue.DoubleV(Double.longBitsToDouble(payload))));
        CandidateProjection candidate = FakeCandidateProjection.builder().field("C", "l", items).build();
        CelValue.ListV result = (CelValue.ListV) assertInstanceOf(EvalOutcome.Value.class,
                provided.evaluate(f("l"), candidate)).value();
        assertEquals(payload, Double.doubleToRawLongBits(((CelValue.DoubleV) result.elements().get(0)).value()));
        assertTrue(Double.isNaN(((CelValue.DoubleV) result.elements().get(0)).value()));
    }
}
