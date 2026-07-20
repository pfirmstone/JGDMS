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

import au.net.zeus.jgdms.cel.CelValue;
import au.net.zeus.jgdms.cel.EvalOutcome;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.testsupport.FakeCandidateProjection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JGDMS-STD-011 §4.3.4: NaN comparison rules, signed-zero preservation and
 * ordering, and result-boundary NaN canonicalization -- specifically probing
 * the {@code Double.compare} trap the spec warns against by name.
 */
class NaNSignedZeroTest {

    private static final Evaluator EVAL = new Evaluator();
    private static final CandidateProjection CANDIDATE = FakeCandidateProjection.builder().build();

    private static boolean asBool(EvalOutcome outcome) {
        EvalOutcome.Value v = assertInstanceOf(EvalOutcome.Value.class, outcome);
        return assertInstanceOf(CelValue.BoolV.class, v.value()).value();
    }

    private static EvalOutcome eval(ExprNode node) {
        return EVAL.evaluate(node, CANDIDATE);
    }

    @Test
    void nanNotEqualToItself() {
        ExprNode nan = new ExprNode.LitDouble(Double.NaN);
        assertFalse(asBool(eval(new ExprNode.Eq(nan, nan))));
        assertTrue(asBool(eval(new ExprNode.Ne(nan, nan))));
    }

    @Test
    void everyOrderedComparisonWithNaNIsFalse() {
        ExprNode nan = new ExprNode.LitDouble(Double.NaN);
        ExprNode five = new ExprNode.LitDouble(5.0);
        assertFalse(asBool(eval(new ExprNode.Lt(nan, five))));
        assertFalse(asBool(eval(new ExprNode.Le(nan, five))));
        assertFalse(asBool(eval(new ExprNode.Gt(nan, five))));
        assertFalse(asBool(eval(new ExprNode.Ge(nan, five))));
        assertFalse(asBool(eval(new ExprNode.Lt(five, nan))));
        assertFalse(asBool(eval(new ExprNode.Gt(five, nan))));
    }

    /**
     * The exact trap STD-011 §4.3.4 names: {@code Double.compare(-0.0, 0.0)
     * < 0} is {@code true} in Java (a total order), but this standard
     * requires {@code -0.0 < 0.0} to be {@code false} (IEEE equality/
     * ordering). Demonstrates the evaluator gives the IEEE answer, not the
     * total-order one.
     */
    @Test
    void doubleCompareTrapDemonstration() {
        assertTrue(Double.compare(-0.0, 0.0) < 0, "sanity: Double.compare treats -0.0 as less than 0.0 (the trap)");

        ExprNode negZero = new ExprNode.LitDouble(-0.0);
        ExprNode posZero = new ExprNode.LitDouble(0.0);
        assertFalse(asBool(eval(new ExprNode.Lt(negZero, posZero))), "-0.0 < 0.0 MUST be false (IEEE), not Double.compare's true");
        assertTrue(asBool(eval(new ExprNode.Eq(negZero, posZero))), "-0.0 == 0.0 MUST be true");
    }

    @Test
    void doubleCompareNaNTrapDemonstration() {
        // Double.compare imposes a total order that places NaN as equal to itself and greater than
        // every other value -- both wrong for this standard's NaN comparison rules.
        assertEquals(0, Double.compare(Double.NaN, Double.NaN), "sanity: Double.compare treats NaN as equal to itself (the trap)");
        ExprNode nan = new ExprNode.LitDouble(Double.NaN);
        assertFalse(asBool(eval(new ExprNode.Eq(nan, nan))), "NaN == NaN MUST be false per this standard, unlike Double.compare's total order");
    }

    @Test
    void signedZeroPreservedThroughArithmetic() {
        // -0.0 + 0.0 = 0.0 (IEEE default rounding); but -0.0 * 1.0 preserves the sign.
        ExprNode negZero = new ExprNode.LitDouble(-0.0);
        ExprNode negated = new ExprNode.Neg(new ExprNode.LitDouble(0.0));
        EvalOutcome.Value v = assertInstanceOf(EvalOutcome.Value.class, eval(negated));
        double result = assertInstanceOf(CelValue.DoubleV.class, v.value()).value();
        assertTrue(result == 0.0, "-(0.0) must be numerically zero");
        assertTrue(1.0 / result < 0, "unary minus of +0.0 MUST yield -0.0 (sign preserved)");
    }

    @Test
    void nanCanonicalizationAtResultBoundary() {
        long nonCanonicalPayload = 0x7FF0000000000001L; // a NaN, but not the canonical quiet-NaN bit pattern
        double raw = Double.longBitsToDouble(nonCanonicalPayload);
        assertTrue(Double.isNaN(raw));

        CelValue.DoubleV canonicalized = new CelValue.DoubleV(raw).canonicalizedForResultBoundary();
        assertEquals(CelValue.DoubleV.CANONICAL_NAN_BITS, Double.doubleToRawLongBits(canonicalized.value()));
    }

    @Test
    void nonNaNValuesUnchangedByCanonicalization() {
        CelValue.DoubleV negZero = new CelValue.DoubleV(-0.0);
        assertEquals(Double.doubleToRawLongBits(-0.0),
                Double.doubleToRawLongBits(negZero.canonicalizedForResultBoundary().value()));

        CelValue.DoubleV posInf = new CelValue.DoubleV(Double.POSITIVE_INFINITY);
        assertEquals(Double.POSITIVE_INFINITY, posInf.canonicalizedForResultBoundary().value());
    }

    @Test
    void supplementaryPlaneStringOrdering() {
        // §4.4's Java trap: String#compareTo orders by UTF-16 code UNIT, which disagrees with code-point
        // order for supplementary characters. U+FFFF (BMP) vs U+10000 (first supplementary-plane code point):
        // U+FFFF as a UTF-16 char is 0xFFFF; U+10000 as a surrogate pair starts with 0xD800 (a high
        // surrogate) -- so a naive UTF-16-code-unit compareTo would say U+10000 < U+FFFF (0xD800 < 0xFFFF),
        // which is backwards. Code-point order requires U+FFFF < U+10000.
        String bmpMax = new String(Character.toChars(0xFFFF));
        String firstSupplementary = new String(Character.toChars(0x10000));

        assertTrue(bmpMax.compareTo(firstSupplementary) > 0,
                "sanity: String#compareTo gets this backwards (the §4.4 trap)");

        ExprNode lt = new ExprNode.Lt(new ExprNode.LitString(bmpMax), new ExprNode.LitString(firstSupplementary));
        assertTrue(asBool(eval(lt)), "U+FFFF < U+10000 by code-point order MUST be true");
    }

    @Test
    void bytesUnsignedOrdering() {
        // 0x7F (127) vs 0xFF (255 unsigned, but -1 as a signed Java byte) -- a signed-byte comparison
        // would get this backwards.
        ExprNode lt = new ExprNode.Lt(
                new ExprNode.LitBytes(new byte[] { 0x7F }),
                new ExprNode.LitBytes(new byte[] { (byte) 0xFF }));
        assertTrue(asBool(eval(lt)), "0x7F < 0xFF under unsigned ordering MUST be true");
    }

    /**
     * Board-review regression (HIGH): {@code CelValue.DoubleV#canonicalizedForResultBoundary()}
     * existed but {@code Evaluator.evaluate()} never called it. A candidate
     * field carrying a non-canonical NaN payload (an attacker-controlled bit
     * pattern that is a NaN, but not the canonical quiet NaN) flowed through
     * {@code evaluate()} unchanged -- letting arbitrary NaN payload bits ride
     * out to a DER-encoded transform result, which STD-011 §4.3.4 requires
     * to be impossible. Direct field read: no arithmetic involved at all.
     */
    @Test
    void nanCanonicalizationAppliedAtEvaluateBoundary_directFieldRead() {
        long nonCanonicalPayload = 0x7FF0000000000001L;
        double raw = Double.longBitsToDouble(nonCanonicalPayload);
        assertTrue(Double.isNaN(raw));
        assertNotEquals(CelValue.DoubleV.CANONICAL_NAN_BITS, Double.doubleToRawLongBits(raw),
                "sanity: the probe's bit pattern is a NaN but not already the canonical one");

        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("C", "x", new CelValue.DoubleV(raw))
                .build();
        ExprNode fieldRef = new ExprNode.FieldRef(java.util.List.of(new ExprNode.SelectorStep.Unqual("x")));

        EvalOutcome outcome = EVAL.evaluate(fieldRef, candidate);
        EvalOutcome.Value v = assertInstanceOf(EvalOutcome.Value.class, outcome);
        CelValue.DoubleV d = assertInstanceOf(CelValue.DoubleV.class, v.value());
        assertEquals(CelValue.DoubleV.CANONICAL_NAN_BITS, Double.doubleToRawLongBits(d.value()),
                "a candidate field's non-canonical NaN payload MUST be canonicalized at the evaluate() boundary");
    }

    /**
     * The arithmetic-propagation variant of the same regression: on x86-64,
     * IEEE {@code x + 0.0} propagates a <em>quiet</em> NaN operand's exact
     * payload bits unchanged (verified below as a sanity check) -- so
     * canonicalization MUST apply to the evaluator's own arithmetic results
     * too, not merely to values read straight off a candidate field. (A
     * *signaling* NaN, unlike a quiet one, is quieted by the FPU during
     * arithmetic and does not reliably preserve its payload across an
     * operation -- irrelevant to this fix either way, since canonicalization
     * now applies at the evaluate() boundary regardless of the NaN's
     * quiet/signaling bit or which path produced it.)
     */
    @Test
    void nanCanonicalizationAppliedAtEvaluateBoundary_arithmeticPropagation() {
        long nonCanonicalPayload = 0x7FF8000000000001L; // quiet NaN (bit 51 set), non-canonical payload
        double raw = Double.longBitsToDouble(nonCanonicalPayload);
        assertTrue(Double.isNaN(raw + 0.0), "sanity: NaN + 0.0 is still NaN");
        assertEquals(nonCanonicalPayload, Double.doubleToRawLongBits(raw + 0.0),
                "sanity: x86-64 IEEE addition propagates the NaN payload bits unchanged -- exactly the leak this fix closes");

        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("C", "x", new CelValue.DoubleV(raw))
                .build();
        ExprNode fieldRef = new ExprNode.FieldRef(java.util.List.of(new ExprNode.SelectorStep.Unqual("x")));
        ExprNode addZero = new ExprNode.Add(fieldRef, new ExprNode.LitDouble(0.0));

        EvalOutcome outcome = EVAL.evaluate(addZero, candidate);
        EvalOutcome.Value v = assertInstanceOf(EvalOutcome.Value.class, outcome);
        CelValue.DoubleV d = assertInstanceOf(CelValue.DoubleV.class, v.value());
        assertEquals(CelValue.DoubleV.CANONICAL_NAN_BITS, Double.doubleToRawLongBits(d.value()),
                "NaN payload bits surviving x + 0.0 MUST be canonicalized at the evaluate() boundary");
    }
}
