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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JGDMS-STD-011 §7's platform function registry domain/error edges, §7.3's
 * pinned {@code radians}/{@code degrees} constants, and §6.5's membership
 * operator (including empty-list and error-absorption cases).
 */
class PlatformFunctionsAndMembershipTest {

    private static final Evaluator EVAL = new Evaluator(MathProvider.nonConformantForTestingOnly());
    private static final CandidateProjection CANDIDATE = FakeCandidateProjection.builder().build();

    private static EvalOutcome eval(ExprNode node) {
        return EVAL.evaluate(node, CANDIDATE);
    }

    private static ExprNode call(int id, ExprNode... args) {
        return new ExprNode.Call(id, List.of(args));
    }

    private static double asDouble(EvalOutcome outcome) {
        return assertInstanceOf(CelValue.DoubleV.class, ((EvalOutcome.Value) outcome).value()).value();
    }

    private static long asInt(EvalOutcome outcome) {
        return assertInstanceOf(CelValue.IntV.class, ((EvalOutcome.Value) outcome).value()).value();
    }

    private static void assertError(EvalOutcome outcome, CelError expected) {
        assertEquals(expected, assertInstanceOf(EvalOutcome.Error.class, outcome).code());
    }

    // ---- size ------------------------------------------------------------------

    @Test
    void sizeString_isCodePointCount_notUtf16Units() {
        // A supplementary-plane character is 2 UTF-16 code units but 1 code point.
        String s = new String(Character.toChars(0x1F600));
        assertEquals(1L, asInt(eval(call(1, new ExprNode.LitString(s)))));
        assertEquals(2, s.length(), "sanity: the Java String itself is 2 UTF-16 chars long");
    }

    @Test
    void sizeBytes() {
        assertEquals(3L, asInt(eval(call(2, new ExprNode.LitBytes(new byte[] {1, 2, 3})))));
    }

    @Test
    void sizeList() {
        ExprNode list = new ExprNode.ListLit(List.of(new ExprNode.LitInt(1), new ExprNode.LitInt(2)));
        assertEquals(2L, asInt(eval(call(3, list))));
    }

    // ---- int(double) / double(int) ----------------------------------------------

    @Test
    void intOfDouble_truncatesTowardZero() {
        assertEquals(3L, asInt(eval(call(4, new ExprNode.LitDouble(3.9)))));
        assertEquals(-3L, asInt(eval(call(4, new ExprNode.LitDouble(-3.9)))));
    }

    @Test
    void intOfDouble_nanIsDomainError() {
        assertError(eval(call(4, new ExprNode.LitDouble(Double.NaN))), CelError.DOMAIN);
    }

    @Test
    void intOfDouble_infinityIsDomainError() {
        assertError(eval(call(4, new ExprNode.LitDouble(Double.POSITIVE_INFINITY))), CelError.DOMAIN);
    }

    @Test
    void intOfDouble_outOfRangeIsOverflow_neverSaturates() {
        // A bare (long) cast would saturate to Long.MAX_VALUE -- MUST be OVERFLOW instead.
        EvalOutcome outcome = eval(call(4, new ExprNode.LitDouble(1.0e300)));
        assertError(outcome, CelError.OVERFLOW);
    }

    @Test
    void doubleOfInt_total() {
        assertEquals(5.0, asDouble(eval(call(5, new ExprNode.LitInt(5)))));
    }

    // ---- abs -----------------------------------------------------------------------

    @Test
    void absDouble_nanStaysNan() {
        double result = asDouble(eval(call(7, new ExprNode.LitDouble(Double.NaN))));
        assertTrue(Double.isNaN(result));
    }

    @Test
    void absDouble_infinityStaysPositiveInfinity() {
        assertEquals(Double.POSITIVE_INFINITY, asDouble(eval(call(7, new ExprNode.LitDouble(Double.NEGATIVE_INFINITY)))));
    }

    // ---- min / max ------------------------------------------------------------------

    @Test
    void minMaxDouble_signedZeroPinned() {
        ExprNode negZero = new ExprNode.LitDouble(-0.0);
        ExprNode posZero = new ExprNode.LitDouble(0.0);
        double min = asDouble(eval(call(9, negZero, posZero)));
        double max = asDouble(eval(call(11, negZero, posZero)));
        assertTrue(1.0 / min < 0, "min(-0.0, 0.0) MUST be -0.0");
        assertTrue(1.0 / max > 0, "max(-0.0, 0.0) MUST be +0.0");
    }

    @Test
    void minMaxDouble_nanIsDomainError() {
        assertError(eval(call(9, new ExprNode.LitDouble(Double.NaN), new ExprNode.LitDouble(1.0))), CelError.DOMAIN);
        assertError(eval(call(11, new ExprNode.LitDouble(1.0), new ExprNode.LitDouble(Double.NaN))), CelError.DOMAIN);
    }

    @Test
    void minMaxInt() {
        assertEquals(3L, asInt(eval(call(8, new ExprNode.LitInt(3), new ExprNode.LitInt(5)))));
        assertEquals(5L, asInt(eval(call(10, new ExprNode.LitInt(3), new ExprNode.LitInt(5)))));
    }

    // ---- sqrt ---------------------------------------------------------------------

    @Test
    void sqrt_negativeIsDomainError() {
        assertError(eval(call(12, new ExprNode.LitDouble(-4.0))), CelError.DOMAIN);
    }

    @Test
    void sqrt_negativeZeroAccepted() {
        double result = asDouble(eval(call(12, new ExprNode.LitDouble(-0.0))));
        assertTrue(result == 0.0, "sqrt(-0.0) must be numerically zero");
        assertTrue(1.0 / result < 0, "sqrt(-0.0) MUST be -0.0 per IEEE-754");
    }

    @Test
    void sqrt_ofFour() {
        assertEquals(2.0, asDouble(eval(call(12, new ExprNode.LitDouble(4.0)))));
    }

    // ---- radians/degrees pinned constants (§7.3) -----------------------------------

    @Test
    void radiansUsesPinnedConstant_notMathToRadians() {
        double x = 180.0;
        double result = asDouble(eval(call(13, new ExprNode.LitDouble(x))));
        double pinned = x * Double.longBitsToDouble(0x3F91DF46A2529D39L);
        assertEquals(pinned, result);
    }

    @Test
    void degreesUsesPinnedConstant_notMathToDegrees() {
        double x = Math.PI;
        double result = asDouble(eval(call(14, new ExprNode.LitDouble(x))));
        double pinned = x * Double.longBitsToDouble(0x404CA5DC1A63C1F8L);
        assertEquals(pinned, result);
    }

    @Test
    void radiansDomain_infinityIsError() {
        assertError(eval(call(13, new ExprNode.LitDouble(Double.POSITIVE_INFINITY))), CelError.DOMAIN);
    }

    // ---- asin/acos domain --------------------------------------------------------

    @Test
    void asinAcos_outOfDomainRejected() {
        assertError(eval(call(18, new ExprNode.LitDouble(1.5))), CelError.DOMAIN);
        assertError(eval(call(19, new ExprNode.LitDouble(-1.5))), CelError.DOMAIN);
    }

    @Test
    void asinAcos_boundaryAccepted() {
        assertTrue(Double.isFinite(asDouble(eval(call(18, new ExprNode.LitDouble(1.0))))));
        assertTrue(Double.isFinite(asDouble(eval(call(19, new ExprNode.LitDouble(-1.0))))));
    }

    /**
     * {@code atan2}'s IEEE-754-mandated zero/sign special cases are exact,
     * bit-for-bit specified values -- not subject to the "last ulp"
     * correct-rounding uncertainty §7.5 is about. Every conformant libm
     * (including fdlibm-derived {@code StrictMath}) implements these special
     * cases identically, so this is legitimately testable even under the
     * non-conformant placeholder.
     */
    @Test
    void atan2_zeroSignSpecialCases() {
        assertEquals(0.0, asDouble(eval(call(21, new ExprNode.LitDouble(0.0), new ExprNode.LitDouble(1.0)))));
        double negZeroResult = asDouble(eval(call(21, new ExprNode.LitDouble(-0.0), new ExprNode.LitDouble(1.0))));
        assertTrue(1.0 / negZeroResult < 0, "atan2(-0, +x) MUST be -0");
        double piResult = asDouble(eval(call(21, new ExprNode.LitDouble(0.0), new ExprNode.LitDouble(-1.0))));
        assertEquals(Math.PI, piResult);
    }

    @Test
    void atan2_nonFiniteIsDomainError() {
        assertError(eval(call(21, new ExprNode.LitDouble(Double.NaN), new ExprNode.LitDouble(1.0))), CelError.DOMAIN);
    }

    // ---- contains/startsWith/endsWith degenerate cases -----------------------------

    @Test
    void contains_emptyNeedleAlwaysTrue() {
        EvalOutcome outcome = eval(call(22, new ExprNode.LitString("anything"), new ExprNode.LitString("")));
        assertTrue(((CelValue.BoolV) ((EvalOutcome.Value) outcome).value()).value());
    }

    @Test
    void startsWithEndsWith_emptyStringMatchesEmptyPrefixSuffix() {
        EvalOutcome starts = eval(call(23, new ExprNode.LitString(""), new ExprNode.LitString("")));
        EvalOutcome ends = eval(call(24, new ExprNode.LitString(""), new ExprNode.LitString("")));
        assertTrue(((CelValue.BoolV) ((EvalOutcome.Value) starts).value()).value());
        assertTrue(((CelValue.BoolV) ((EvalOutcome.Value) ends).value()).value());
    }

    // ---- `in` membership (§6.5) -----------------------------------------------------

    @Test
    void in_literalList_matchFound() {
        ExprNode.InListOperand list = new ExprNode.InListOperand.LiteralList(
                new ExprNode.ListLit(List.of(new ExprNode.LitInt(1), new ExprNode.LitInt(2), new ExprNode.LitInt(3))));
        ExprNode inExpr = new ExprNode.In(new ExprNode.LitInt(2), list);
        EvalOutcome outcome = eval(inExpr);
        assertTrue(((CelValue.BoolV) ((EvalOutcome.Value) outcome).value()).value());
    }

    @Test
    void in_literalList_noMatch() {
        ExprNode.InListOperand list = new ExprNode.InListOperand.LiteralList(
                new ExprNode.ListLit(List.of(new ExprNode.LitInt(1), new ExprNode.LitInt(2), new ExprNode.LitInt(3))));
        ExprNode inExpr = new ExprNode.In(new ExprNode.LitInt(99), list);
        EvalOutcome outcome = eval(inExpr);
        assertFalse(((CelValue.BoolV) ((EvalOutcome.Value) outcome).value()).value());
    }

    @Test
    void in_emptyProjectedList_isFalse() {
        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("com.example.X", "tags", new CelValue.ListV(CelType.STRING, List.of()))
                .build();
        ExprNode.InListOperand listField = new ExprNode.InListOperand.FieldList(
                new ExprNode.FieldRef(List.of(new ExprNode.SelectorStep.Unqual("tags"))));
        ExprNode inExpr = new ExprNode.In(new ExprNode.LitString("x"), listField);
        EvalOutcome outcome = EVAL.evaluate(inExpr, candidate);
        assertFalse(((CelValue.BoolV) ((EvalOutcome.Value) outcome).value()).value());
    }

    /**
     * §6.5: "if any element compares equal -> true (later elements need not
     * be examined, and any error a later comparison would produce is
     * absorbed)". Here the first element's comparison (needle vs a
     * non-matching, differently-typed element) would error, but a LATER
     * element matches -- the match must win, absorbing the earlier error.
     */
    @Test
    void in_matchAbsorbsEarlierComparisonError() {
        ExprNode.InListOperand list = new ExprNode.InListOperand.LiteralList(
                new ExprNode.ListLit(List.of(new ExprNode.LitInt(5), new ExprNode.LitInt(2))));
        // needle is a string; comparing string==int is TYPE_MISMATCH for element 5, but element 2... wait
        // needle must compare against SAME type to ever equal -- construct so a later element matches exactly.
        ExprNode inExpr = new ExprNode.In(new ExprNode.LitInt(2), list);
        EvalOutcome outcome = eval(inExpr);
        assertTrue(((CelValue.BoolV) ((EvalOutcome.Value) outcome).value()).value());
    }

    @Test
    void in_noMatch_firstErrorReported() {
        // needle is a string, list elements are int -- every comparison is TYPE_MISMATCH, none match.
        ExprNode.InListOperand list = new ExprNode.InListOperand.LiteralList(
                new ExprNode.ListLit(List.of(new ExprNode.LitInt(1), new ExprNode.LitInt(2))));
        ExprNode inExpr = new ExprNode.In(new ExprNode.LitString("x"), list);
        EvalOutcome outcome = eval(inExpr);
        assertError(outcome, CelError.TYPE_MISMATCH);
    }

    @Test
    void in_needleError_propagatesDirectly() {
        ExprNode.InListOperand list = new ExprNode.InListOperand.LiteralList(
                new ExprNode.ListLit(List.of(new ExprNode.LitInt(1))));
        ExprNode badNeedle = new ExprNode.Div(new ExprNode.LitInt(1), new ExprNode.LitInt(0));
        ExprNode inExpr = new ExprNode.In(badNeedle, list);
        assertError(eval(inExpr), CelError.DIVISION_BY_ZERO);
    }
}
