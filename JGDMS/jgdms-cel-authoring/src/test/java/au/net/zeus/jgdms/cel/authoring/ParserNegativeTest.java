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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Every §5.4 exclusion, §5.3.1 well-formedness constraint, lexical rule, and
 * ceiling violation is rejected wholesale with a {@link CelParseException}
 * (never a partial AST, §5.1).
 */
class ParserNegativeTest {

    private static void rejects(String text) {
        assertThrows(CelParseException.class, () -> CelTextParser.parse(text), () -> "should reject: " + text);
    }

    // ---- §5.3: relations do not chain ----
    @Test void relationChainingRejected() {
        rejects("a < b < c");
        rejects("1 == 2 == 3");
        rejects("a < b > c");
    }

    // ---- §5.2: integer literal range and the 2^63 special case ----
    @Test void intOutOfRangeRejected() {
        rejects("99999999999999999999999999 > 0");         // way over 2^63
        rejects("9223372036854775809");                    // 2^63 + 1
        rejects("9223372036854775808 > 0");                // 2^63 not under unary minus
        rejects("-(9223372036854775808)");                 // parenthesised: not the IMMEDIATE operand of '-'
    }

    @Test void twoPow63FoldsOnlyUnderUnaryMinus() {
        assertDoesNotThrow(() -> CelTextParser.parse("-9223372036854775808"));   // accepted
        rejects("9223372036854775808");                                          // rejected bare
    }

    // ---- §5.2: string escapes ----
    @Test void surrogateAndOverlargeEscapesRejected() {
        rejects("\"\\uD800\"");        // lone high surrogate (backslash-u escape)
        rejects("\"\\uDC00\"");        // low surrogate
        rejects("\"\\U00110000\"");    // beyond U+10FFFF
        rejects("\"\\U0000D800\"");    // surrogate (backslash-U escape)
        rejects("\"\\q\"");            // invalid escape
        rejects("\"unterminated");     // unterminated string
    }

    // ---- §5.2: reserved words are not bare field identifiers ----
    @Test void reservedWordAsFieldRejected() {
        rejects("let > 0");
        rejects("for < 3");
        rejects("has > 0");            // has is reserved (only has(...) form)
        rejects("field == 1");        // field is reserved (only field(...) form)
        rejects("return + 1");
    }

    // ---- §5.4: comprehension macros / matches / user functions & methods ----
    @Test void excludedConstructsRejected() {
        rejects("items.all(x, x > 0)");       // comprehension macro
        rejects("items.exists(x, x > 0)");
        rejects("items.map(x, x)");
        rejects("s.matches(\"a.*\")");        // regex
        rejects("foo(1)");                    // user-defined function
        rejects("x.bar()");                   // user-defined method
        rejects("s.size()");                  // string size-as-method (use global size())
        rejects("x = 1");                     // assignment
        rejects("let x = 1");                 // let binding
    }

    // ---- §5.4 item 6: + is numeric-only ----
    @Test void concatenationRejected() {
        rejects("\"a\" + \"b\"");
        rejects("\"a\" + x");                 // known string operand
        rejects("[1] + [2]");                 // list concatenation
        rejects("b\"\\x01\" + b\"\\x02\"");   // bytes concatenation
        rejects("true + 1");                  // non-numeric operand
    }

    // ---- §5.3.1 item 2/3/4: has / field / contains argument shapes ----
    @Test void callArgumentShapeConstraints() {
        rejects("has(1 + 2)");                        // has arg must be a field designator
        rejects("has(x + 1)");
        rejects("field(x, \"y\")");                   // field() args must be string literals
        rejects("field(\"a\", b)");
        rejects("s.contains(x)");                     // contains needle must be a string literal
        rejects("s.contains(1)");
    }

    // ---- §5.3.1 item 6: in operand shape ----
    @Test void inOperandShape() {
        rejects("x in 5");
        rejects("x in (1 + 2)");
        rejects("x in \"abc\"");
        assertDoesNotThrow(() -> CelTextParser.parse("x in [1, 2, 3]"));
        assertDoesNotThrow(() -> CelTextParser.parse("x in myList"));
    }

    // ---- §5.3.2: list literals non-empty and homogeneous ----
    @Test void listLiteralConstraints() {
        rejects("[]");                        // empty
        rejects("[1, \"a\"]");                // mixed int/string
        rejects("[1, 2.0]");                  // int vs double are different scalar types
        rejects("[1, null]");                 // null not admissible
        rejects("[[1], [2]]");                // nested lists not admissible
        assertDoesNotThrow(() -> CelTextParser.parse("[1, 2, 3]"));
        assertDoesNotThrow(() -> CelTextParser.parse("[\"a\", \"b\"]"));
    }

    // ---- ceilings (CelCeilings) ----
    @Test void depthCeilingRejected() {
        assertDoesNotThrow(() -> CelTextParser.parse("!".repeat(31) + "true"));  // leaf at depth 32 (accept)
        rejects("!".repeat(32) + "true");                                        // leaf at depth 33 (reject)
    }

    @Test void selectorStepCeilingRejected() {
        StringBuilder ok = new StringBuilder("a");
        for (int i = 1; i < 16; i++) ok.append(".a");    // 16 steps
        assertDoesNotThrow(() -> CelTextParser.parse(ok.toString()));
        StringBuilder bad = new StringBuilder("a");
        for (int i = 1; i < 17; i++) bad.append(".a");   // 17 steps
        rejects(bad.toString());
    }

    // ---- schema-free overload limitation (documented OPEN QUESTION) ----
    @Test void unresolvableOverloadRejected() {
        rejects("size(someField)");          // size over a field: type not statically known
        rejects("abs(someField)");
        rejects("min(a, b)");                // both fields
    }

    // ---- misc syntax ----
    @Test void miscSyntaxErrors() {
        rejects("");                         // empty input
        rejects("(a");                       // unclosed paren
        rejects("a &&");                     // dangling operator
        rejects("a[0]");                     // index operator excluded
        rejects("{1: 2}");                   // map literal excluded
        rejects("123u");                     // uint suffix excluded
        rejects("a ? b");                    // conditional missing ':'
    }
}
