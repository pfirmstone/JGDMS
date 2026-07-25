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
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * F1 regression (adversarial re-review): a flat binary-operator chain is parsed
 * in a LOOP (parseAddition/Multiplication/And/Or), so it never hits the
 * MAX_PARSE_FRAMES recursion cap, but it builds a left-nested tree ~N deep. The
 * post-parse walks must NOT recurse that spine unguarded. The fix runs the
 * bounded-by-construction ceiling check (findWireViolation, depth-guarded at
 * entry) BEFORE the authoring walk, so a hostile chain yields a clean
 * {@link CelParseException}, never a {@link StackOverflowError}.
 */
class FlatBinaryChainDosTest {

    private static final int N = 50_000;

    private static void rejectsFast(String text) {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            // Must be a CelParseException (ceiling), never a StackOverflowError.
            assertThrows(CelParseException.class, () -> CelTextParser.parse(text));
        });
    }

    @Test void flatAddChain() { rejectsFast("1" + "+1".repeat(N)); }
    @Test void flatSubChain() { rejectsFast("1" + "-1".repeat(N)); }
    @Test void flatMulChain() { rejectsFast("1" + "*1".repeat(N)); }
    @Test void flatDivChain() { rejectsFast("1" + "/1".repeat(N)); }
    @Test void flatModChain() { rejectsFast("1" + "%1".repeat(N)); }
    @Test void flatAndChain() { rejectsFast("a" + "&&a".repeat(N)); }
    @Test void flatOrChain()  { rejectsFast("a" + "||a".repeat(N)); }
    @Test void flatRelInComparisonMix() { rejectsFast("a" + "&&a".repeat(N) + "||a"); }

    /** A deep argument to a type-dispatched overload must also reject cleanly (inferType is guarded). */
    @Test void deepOverloadArgumentRejectsNotStackOverflow() {
        rejectsFast("size(1" + "+1".repeat(N) + ")");
        rejectsFast("abs(1" + "+1".repeat(N) + ")");
    }

    /** The reorder must not falsely reject a legitimate expression at exactly MAX_EXPR_DEPTH = 32. */
    @Test void legitimateExpressionAtExactlyMaxDepthStillParses() {
        // 31 nested NOTs over a leaf: leaf at depth 32 (the inclusive ceiling), accepted.
        assertDoesNotThrow(() -> CelTextParser.parse("!".repeat(31) + "true"));
        // A flat chain short enough to stay within the node ceiling still parses.
        assertDoesNotThrow(() -> {
            ExprNode n = CelTextParser.parse("1" + "+1".repeat(20));
            assertInstanceOf(ExprNode.Add.class, n);
        });
    }
}
