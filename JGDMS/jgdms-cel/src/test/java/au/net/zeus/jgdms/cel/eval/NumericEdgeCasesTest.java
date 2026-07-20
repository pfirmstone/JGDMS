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

import static org.junit.jupiter.api.Assertions.*;

/**
 * STD-011 §4.3.1 (checked {@code int} arithmetic) and §4.3.3 (exact
 * {@code int}x{@code double} comparison) edge cases, including the two
 * deliberately pinned divergences from native JVM {@code /}/{@code %}
 * behaviour at {@code Long.MIN_VALUE / -1}.
 */
class NumericEdgeCasesTest {

    private static final Evaluator EVAL = new Evaluator();
    private static final CandidateProjection CANDIDATE = FakeCandidateProjection.builder().build();

    private static EvalOutcome eval(ExprNode node) {
        return EVAL.evaluate(node, CANDIDATE);
    }

    private static void assertError(EvalOutcome outcome, CelError expected) {
        EvalOutcome.Error e = assertInstanceOf(EvalOutcome.Error.class, outcome);
        assertEquals(expected, e.code());
    }

    private static long asInt(EvalOutcome outcome) {
        EvalOutcome.Value v = assertInstanceOf(EvalOutcome.Value.class, outcome);
        return assertInstanceOf(CelValue.IntV.class, v.value()).value();
    }

    private static boolean asBool(EvalOutcome outcome) {
        EvalOutcome.Value v = assertInstanceOf(EvalOutcome.Value.class, outcome);
        return assertInstanceOf(CelValue.BoolV.class, v.value()).value();
    }

    // ---- checked int arithmetic overflow -----------------------------------

    @Test
    void addOverflowAtMax() {
        assertError(eval(new ExprNode.Add(new ExprNode.LitInt(Long.MAX_VALUE), new ExprNode.LitInt(1))), CelError.OVERFLOW);
    }

    @Test
    void subOverflowAtMin() {
        assertError(eval(new ExprNode.Sub(new ExprNode.LitInt(Long.MIN_VALUE), new ExprNode.LitInt(1))), CelError.OVERFLOW);
    }

    @Test
    void mulOverflow() {
        assertError(eval(new ExprNode.Mul(new ExprNode.LitInt(Long.MAX_VALUE), new ExprNode.LitInt(2))), CelError.OVERFLOW);
    }

    @Test
    void negateMinValueOverflows() {
        assertError(eval(new ExprNode.Neg(new ExprNode.LitInt(Long.MIN_VALUE))), CelError.OVERFLOW);
    }

    @Test
    void absMinValueOverflows() {
        ExprNode call = callAbsInt(new ExprNode.LitInt(Long.MIN_VALUE));
        assertError(eval(call), CelError.OVERFLOW);
    }

    // ---- division / modulo ---------------------------------------------------

    @Test
    void divByZero() {
        assertError(eval(new ExprNode.Div(new ExprNode.LitInt(10), new ExprNode.LitInt(0))), CelError.DIVISION_BY_ZERO);
    }

    @Test
    void modByZero() {
        assertError(eval(new ExprNode.Mod(new ExprNode.LitInt(10), new ExprNode.LitInt(0))), CelError.DIVISION_BY_ZERO);
    }

    /** §4.3.1's pinned special case: mathematically 0, and Java's raw {@code /} silently returns Long.MIN_VALUE -- MUST be OVERFLOW here. */
    @Test
    void minValueDividedByNegativeOneIsOverflow() {
        assertError(eval(new ExprNode.Div(new ExprNode.LitInt(Long.MIN_VALUE), new ExprNode.LitInt(-1))), CelError.OVERFLOW);
    }

    /** §4.3.1's pinned special case: mathematically 0, and Java's raw {@code %} silently returns 0 (not an error) -- MUST still be OVERFLOW here, pinned for cross-language (Rust) alignment. */
    @Test
    void minValueModuloNegativeOneIsOverflow() {
        assertError(eval(new ExprNode.Mod(new ExprNode.LitInt(Long.MIN_VALUE), new ExprNode.LitInt(-1))), CelError.OVERFLOW);
    }

    @Test
    void truncatingDivisionTowardZero() {
        assertEquals(-2L, asInt(eval(new ExprNode.Div(new ExprNode.LitInt(-7), new ExprNode.LitInt(3)))));
        assertEquals(2L, asInt(eval(new ExprNode.Div(new ExprNode.LitInt(7), new ExprNode.LitInt(3)))));
    }

    @Test
    void modSignFollowsDividend() {
        assertEquals(-1L, asInt(eval(new ExprNode.Mod(new ExprNode.LitInt(-7), new ExprNode.LitInt(3)))));
        assertEquals(1L, asInt(eval(new ExprNode.Mod(new ExprNode.LitInt(7), new ExprNode.LitInt(-3)))));
    }

    // ---- exact int x double comparison (§4.3.3's killer cases) --------------

    @Test
    void intDoubleKillerCase_2pow53plus1() {
        // 9007199254740993 > 9007199254740992.0 MUST be true (naive (double) widening would make them equal).
        ExprNode gt = new ExprNode.Gt(new ExprNode.LitInt(9007199254740993L), new ExprNode.LitDouble(9007199254740992.0));
        assertTrue(asBool(eval(gt)));
    }

    @Test
    void intDoubleKillerCase_longMaxVsTwoPow63() {
        // 9223372036854775807 < 9223372036854775808.0 MUST be true.
        ExprNode lt = new ExprNode.Lt(new ExprNode.LitInt(Long.MAX_VALUE), new ExprNode.LitDouble(9223372036854775808.0));
        assertTrue(asBool(eval(lt)));
    }

    @Test
    void intDoubleEqualityExact() {
        ExprNode eq = new ExprNode.Eq(new ExprNode.LitInt(1000000), new ExprNode.LitDouble(1000000.0));
        assertTrue(asBool(eval(eq)));
    }

    @Test
    void intDoubleEqualityExact_fractionalDoubleNeverEqualsInt() {
        ExprNode eq = new ExprNode.Eq(new ExprNode.LitInt(3), new ExprNode.LitDouble(3.5));
        assertFalse(asBool(eval(eq)));
    }

    @Test
    void intDoubleComparison_doubleBelowMinInt64() {
        // A double far below -2^63: any int is larger.
        ExprNode gt = new ExprNode.Gt(new ExprNode.LitInt(Long.MIN_VALUE), new ExprNode.LitDouble(-1.0e300));
        assertTrue(asBool(eval(gt)));
    }

    @Test
    void intDoubleComparison_doubleAboveMaxInt64() {
        ExprNode lt = new ExprNode.Lt(new ExprNode.LitInt(Long.MAX_VALUE), new ExprNode.LitDouble(1.0e300));
        assertTrue(asBool(eval(lt)));
    }

    @Test
    void intDoubleComparison_withNaN_allOrderedFalse() {
        ExprNode base = new ExprNode.LitInt(5);
        ExprNode nan = new ExprNode.LitDouble(Double.NaN);
        assertFalse(asBool(eval(new ExprNode.Lt(base, nan))));
        assertFalse(asBool(eval(new ExprNode.Le(base, nan))));
        assertFalse(asBool(eval(new ExprNode.Gt(base, nan))));
        assertFalse(asBool(eval(new ExprNode.Ge(base, nan))));
        assertFalse(asBool(eval(new ExprNode.Eq(base, nan))));
        assertTrue(asBool(eval(new ExprNode.Ne(base, nan))));
    }

    private static ExprNode callAbsInt(ExprNode arg) {
        // functionId 6 = abs(int) per Appendix B §B.8.3.
        return new ExprNode.Call(6, java.util.List.of(arg));
    }
}
