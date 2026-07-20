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
 * JGDMS-STD-011 §6.4's {@code &&}/{@code ||} error-absorption matrices,
 * exhaustively (all 3x3 cells: {@code true}/{@code false}/error for each
 * operand), including the pinned left-error identity when both operands
 * error with <em>different</em> codes.
 */
class BooleanMatrixTest {

    private static final Evaluator EVAL = new Evaluator();
    private static final CandidateProjection CANDIDATE = FakeCandidateProjection.builder().build();

    private static final ExprNode TRUE = new ExprNode.LitBool(true);
    private static final ExprNode FALSE = new ExprNode.LitBool(false);
    /** Evaluates to DIVISION_BY_ZERO -- used as the "left-side" error so it's distinguishable from ERROR_B. */
    private static final ExprNode ERROR_A = new ExprNode.Div(new ExprNode.LitInt(1), new ExprNode.LitInt(0));
    /** Evaluates to TYPE_MISMATCH -- used as the "right-side" error, distinct from ERROR_A. */
    private static final ExprNode ERROR_B = new ExprNode.Not(new ExprNode.LitInt(1));

    private enum Cell { TRUE, FALSE, ERROR_A, ERROR_B }

    private static ExprNode nodeFor(Cell c) {
        return switch (c) {
            case TRUE -> TRUE;
            case FALSE -> FALSE;
            case ERROR_A -> ERROR_A;
            case ERROR_B -> ERROR_B;
        };
    }

    private EvalOutcome evalAnd(Cell l, Cell r) {
        return EVAL.evaluate(new ExprNode.And(nodeFor(l), nodeFor(r)), CANDIDATE);
    }

    private EvalOutcome evalOr(Cell l, Cell r) {
        return EVAL.evaluate(new ExprNode.Or(nodeFor(l), nodeFor(r)), CANDIDATE);
    }

    private static void assertBool(EvalOutcome outcome, boolean expected) {
        EvalOutcome.Value v = assertInstanceOf(EvalOutcome.Value.class, outcome);
        CelValue.BoolV b = assertInstanceOf(CelValue.BoolV.class, v.value());
        assertEquals(expected, b.value());
    }

    private static void assertErrorCode(EvalOutcome outcome, CelError expected) {
        EvalOutcome.Error e = assertInstanceOf(EvalOutcome.Error.class, outcome);
        assertEquals(expected, e.code());
    }

    // ======================================================================
    // && matrix (§6.4's first table)
    // ======================================================================

    @Test void and_true_true()    { assertBool(evalAnd(Cell.TRUE, Cell.TRUE), true); }
    @Test void and_true_false()   { assertBool(evalAnd(Cell.TRUE, Cell.FALSE), false); }
    @Test void and_true_errorA()  { assertErrorCode(evalAnd(Cell.TRUE, Cell.ERROR_A), CelError.DIVISION_BY_ZERO); }

    @Test void and_false_true()   { assertBool(evalAnd(Cell.FALSE, Cell.TRUE), false); }
    @Test void and_false_false()  { assertBool(evalAnd(Cell.FALSE, Cell.FALSE), false); }
    @Test void and_false_errorB() { assertBool(evalAnd(Cell.FALSE, Cell.ERROR_B), false); }

    @Test void and_errorA_true()  { assertErrorCode(evalAnd(Cell.ERROR_A, Cell.TRUE), CelError.DIVISION_BY_ZERO); }
    @Test void and_errorA_false() { assertBool(evalAnd(Cell.ERROR_A, Cell.FALSE), false); }
    @Test void and_errorA_errorB_leftWins() {
        // Both operands error, with DIFFERENT codes: the pinned rule reports the LEFT (A).
        assertErrorCode(evalAnd(Cell.ERROR_A, Cell.ERROR_B), CelError.DIVISION_BY_ZERO);
    }

    // ======================================================================
    // || matrix (§6.4's second table)
    // ======================================================================

    @Test void or_true_true()    { assertBool(evalOr(Cell.TRUE, Cell.TRUE), true); }
    @Test void or_true_false()   { assertBool(evalOr(Cell.TRUE, Cell.FALSE), true); }
    @Test void or_true_errorB()  { assertBool(evalOr(Cell.TRUE, Cell.ERROR_B), true); }

    @Test void or_false_true()   { assertBool(evalOr(Cell.FALSE, Cell.TRUE), true); }
    @Test void or_false_false()  { assertBool(evalOr(Cell.FALSE, Cell.FALSE), false); }
    @Test void or_false_errorB() { assertErrorCode(evalOr(Cell.FALSE, Cell.ERROR_B), CelError.TYPE_MISMATCH); }

    @Test void or_errorA_true()  { assertBool(evalOr(Cell.ERROR_A, Cell.TRUE), true); }
    @Test void or_errorA_false() { assertErrorCode(evalOr(Cell.ERROR_A, Cell.FALSE), CelError.DIVISION_BY_ZERO); }
    @Test void or_errorA_errorB_leftWins() {
        assertErrorCode(evalOr(Cell.ERROR_A, Cell.ERROR_B), CelError.DIVISION_BY_ZERO);
    }

    // ======================================================================
    // ! and non-bool coercion
    // ======================================================================

    @Test
    void not_ofError_isThatError() {
        ExprNode not = new ExprNode.Not(ERROR_A);
        assertErrorCode(EVAL.evaluate(not, CANDIDATE), CelError.DIVISION_BY_ZERO);
    }

    @Test
    void and_leftNonBool_isTypeMismatch_absorbedByRightFalse() {
        // A non-bool operand is replaced by TYPE_MISMATCH "at its own position" (§6.4); a determining
        // R=false still absorbs it, exactly like any other error would.
        ExprNode nonBool = new ExprNode.LitInt(5);
        EvalOutcome result = EVAL.evaluate(new ExprNode.And(nonBool, FALSE), CANDIDATE);
        assertBool(result, false);
    }

    @Test
    void and_leftNonBool_notAbsorbed_whenRightTrue() {
        ExprNode nonBool = new ExprNode.LitInt(5);
        EvalOutcome result = EVAL.evaluate(new ExprNode.And(nonBool, TRUE), CANDIDATE);
        assertErrorCode(result, CelError.TYPE_MISMATCH);
    }
}
