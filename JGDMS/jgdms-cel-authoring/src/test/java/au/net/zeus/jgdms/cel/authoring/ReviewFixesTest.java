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

package au.net.zeus.jgdms.cel.authoring;

import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.ast.ExprNode.SelectorStep;
import au.net.zeus.jgdms.cel.wire.CelFilterRecord;
import au.net.zeus.jgdms.cel.wire.EvaluationContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/** Regression tests for the two-reviewer MERGE-WITH-FIXES verdict (blockers 1-2, must-fix 3-5). */
class ReviewFixesTest {

    private static ExprNode.FieldRef fr(String name) {
        return new ExprNode.FieldRef(List.of(new SelectorStep.Unqual(name)));
    }

    // ================================================================
    // Fix 1 -- surrogate canonicality break (G1)
    // ================================================================

    @Test void rawUnpairedSurrogateInStringLiteralRejected() {
        String highOnly = "\"" + '\uD800' + "\"";     // a raw unpaired high surrogate inside a string literal
        String lowOnly = "\"" + '\uDC00' + "\"";
        assertThrows(CelParseException.class, () -> CelTextParser.parse(highOnly));
        assertThrows(CelParseException.class, () -> CelTextParser.parse(lowOnly));
        // a valid supplementary character (proper surrogate pair) still parses
        assertDoesNotThrow(() -> CelTextParser.parse("\"clef " + "𝄞" + "\""));
    }

    @Test void unpairedSurrogateInLitStringRejectedPreEncode() {
        assertThrows(CelEncodeException.class,
                () -> CelEncoder.encodeExpression(new ExprNode.LitString("\uD800")));
        assertThrows(CelEncodeException.class,
                () -> CelEncoder.encodeExpression(new ExprNode.LitString("x\uDC00y")));
    }

    @Test void unpairedSurrogateInSelectorNamesRejectedPreEncode() {
        assertThrows(CelEncodeException.class,
                () -> CelEncoder.encodeExpression(new ExprNode.FieldRef(List.of(new SelectorStep.Unqual("a\uD800")))));
        assertThrows(CelEncodeException.class,
                () -> CelEncoder.encodeExpression(new ExprNode.FieldRef(List.of(new SelectorStep.Qual("com.\uD800Bad", "f")))));
        assertThrows(CelEncodeException.class,
                () -> CelEncoder.encodeExpression(new ExprNode.FieldRef(List.of(new SelectorStep.Qual("com.Ok", "f\uDC00")))));
    }

    /** No two distinct ASTs (U+D800, U+DC00, "?") may share one wire form: the surrogate ones never encode at all. */
    @Test void noWireFormCollisionAcrossSurrogatesAndReplacementChar() throws Exception {
        assertThrows(CelEncodeException.class, () -> CelEncoder.encodeExpression(new ExprNode.LitString("\uD800")));
        assertThrows(CelEncodeException.class, () -> CelEncoder.encodeExpression(new ExprNode.LitString("\uDC00")));
        byte[] q = CelEncoder.encodeExpression(new ExprNode.LitString("?"));   // U+003F encodes fine and uniquely
        assertEquals("83013f", Hex.toHex(q));
    }

    // ================================================================
    // Fix 2 -- parser recursion StackOverflow DoS (G10)
    // ================================================================

    @Test void deepGroupingParensRejectedNotStackOverflow() {
        String deep = "(".repeat(50_000) + "x" + ")".repeat(50_000);
        assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> assertThrows(CelParseException.class, () -> CelTextParser.parse(deep)));
    }

    @Test void deepUnaryChainsRejectedNotStackOverflow() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertThrows(CelParseException.class, () -> CelTextParser.parse("!".repeat(50_000) + "true"));
            assertThrows(CelParseException.class, () -> CelTextParser.parse("-".repeat(50_000) + "1"));
        });
    }

    @Test void legitimatelyDeepButUnderLimitStillParses() {
        // 50 nested grouping parens is well under the parser-frame cap.
        assertDoesNotThrow(() -> CelTextParser.parse("(".repeat(50) + "x" + ")".repeat(50)));
    }

    // ================================================================
    // Fix 3 -- formatVersion != 1 totality break
    // ================================================================

    @Test void encodeRejectsNonPinnedFormatVersion() {
        CelFilterRecord bad = new CelFilterRecord(2, new EvaluationContext.Predicate(), new ExprNode.LitBool(true));
        assertThrows(CelEncodeException.class, () -> CelEncoder.encode(bad));
        // the pinned version 1 still encodes
        assertDoesNotThrow(() -> CelEncoder.encode(new CelFilterRecord(1, new EvaluationContext.Predicate(), new ExprNode.LitBool(true))));
    }

    // ================================================================
    // Fix 4 -- min/max over-permissive + arithmetic TYPE_MISMATCH
    // ================================================================

    @Test void minMaxRejectUnknownOrMixedOperands() {
        assertThrows(CelParseException.class, () -> CelTextParser.parse("min(1, 2.0)"));   // mixed int/double
        assertThrows(CelParseException.class, () -> CelTextParser.parse("min(1, someField)")); // UNKNOWN operand
        assertThrows(CelParseException.class, () -> CelTextParser.parse("max(1, field)"));
        assertThrows(CelParseException.class, () -> CelTextParser.parse("max(a, b)"));      // both UNKNOWN
    }

    @Test void minMaxStillResolveWhenBothOperandsKnownAndMatching() throws Exception {
        assertEquals(8, ((ExprNode.Call) CelTextParser.parse("min(1, 2)")).functionId());
        assertEquals(9, ((ExprNode.Call) CelTextParser.parse("min(2.0, 3.0)")).functionId());
        assertEquals(10, ((ExprNode.Call) CelTextParser.parse("max(1, 2)")).functionId());
        assertEquals(11, ((ExprNode.Call) CelTextParser.parse("max(1.0, 2.0)")).functionId());
    }

    @Test void mixedIntDoubleArithmeticRejected() {
        assertThrows(CelParseException.class, () -> CelTextParser.parse("1 + 2.0"));
        assertThrows(CelParseException.class, () -> CelTextParser.parse("3.0 * 2"));
        assertThrows(CelParseException.class, () -> CelTextParser.parse("1 - 2.5"));
        // same-type arithmetic still fine
        assertDoesNotThrow(() -> CelTextParser.parse("1 + 2"));
        assertDoesNotThrow(() -> CelTextParser.parse("1.0 + 2.0"));
        assertDoesNotThrow(() -> CelTextParser.parse("someField + 2.0"));   // UNKNOWN operand defers to eval
    }

    // ================================================================
    // Fix 5 -- quadratic CPU DoS + lexer over-accept
    // ================================================================

    @Test void giantNumericLiteralRejectedFast() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            assertThrows(CelParseException.class, () -> CelTextParser.parse("9".repeat(100_000) + " > 0"));
            assertThrows(CelParseException.class, () -> CelTextParser.parse("0x" + "f".repeat(100_000)));
        });
    }

    @Test void longDottedChainRejectedFast() {
        assertTimeoutPreemptively(Duration.ofSeconds(3),
                () -> assertThrows(CelParseException.class, () -> CelTextParser.parse("a" + ".a".repeat(50_000))));
    }

    @Test void uppercaseHexPrefixRejected() {
        assertThrows(CelParseException.class, () -> CelTextParser.parse("0XFF"));   // §5.2 pins lowercase 0x only
        assertDoesNotThrow(() -> CelTextParser.parse("0xff"));
        assertDoesNotThrow(() -> CelTextParser.parse("0xFF"));                      // hex DIGITS may be upper; only the prefix is lowercase
    }

    @Test void inputLengthCapRejects() {
        String huge = "a".repeat(1_000_001);
        assertTimeoutPreemptively(Duration.ofSeconds(3),
                () -> assertThrows(CelParseException.class, () -> CelTextParser.parse(huge)));
    }
}
