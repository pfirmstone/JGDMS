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
import au.net.zeus.jgdms.cel.wire.CelDecoder;
import au.net.zeus.jgdms.cel.wire.CelFilterRecord;
import au.net.zeus.jgdms.cel.wire.FunctionRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The full author loop: text -&gt; AST (parser) -&gt; wire (encoder) -&gt;
 * decode (jgdms-cel oracle) -&gt; equality, plus re-encode idempotence
 * ({@code encode(decode(encode(r))) == encode(r)}, byte-identical). Uses the
 * STD-011 §14 worked-example expressions and a spread of operator/precedence
 * cases.
 */
class TextToWireTest {

    /** parse -> wrap predicate -> encode -> decode; assert the decoded AST equals the parsed AST, and re-encode is byte-identical. */
    private static CelFilterRecord loop(String text) throws Exception {
        ExprNode parsed = CelTextParser.parse(text);
        CelFilterRecord record = CelRecordBuilder.predicate(parsed);
        byte[] wire = CelEncoder.encode(record);
        CelFilterRecord decoded = CelDecoder.decode(wire);
        assertEquals(record, decoded, () -> "decode(encode(parse(\"" + text + "\"))) != parse");
        // idempotence / determinism
        assertArrayEquals(wire, CelEncoder.encode(decoded), () -> "re-encode not byte-identical for \"" + text + "\"");
        return decoded;
    }

    @Test
    void section14WorkedExpressions() throws Exception {
        loop("pressureHpa >= 950.0 && pressureHpa < 1050.0");                                   // §14.1
        loop("status == \"ACTIVE\" && station.startsWith(\"GLS-\") "
                + "&& !note.contains(\"deprecated\") && sampleCount >= 30");                    // §14.2
        loop("status == \"ACTIVE\" && station.startsWith(\"GLS-\") "
                + "&& (!has(note) || !note.contains(\"deprecated\")) && sampleCount >= 30");    // §14.2 guarded
        loop("field(\"com.example.Beta\", \"timestamp\") > 1700000000000");                    // §14.4
        loop("has(batteryMv) && batteryMv < 3300");                                             // §14.5
        loop("!has(batteryMv) || batteryMv < 3300");                                            // §14.5
        loop("double(sampleCount) * 86400000000000.0 > 0.0");                                   // §14.6
    }

    @Test
    void precedenceAndAssociativity() throws Exception {
        // ?: binds loosest and is right-associative; && tighter than ||; arithmetic tighter than relations.
        ExprNode cond = CelTextParser.parse("a ? b : c ? d : e");
        ExprNode.Cond outer = assertInstanceOf(ExprNode.Cond.class, cond);
        assertInstanceOf(ExprNode.Cond.class, outer.elseBranch());   // right-assoc: else is the nested ?:

        ExprNode e = CelTextParser.parse("a || b && c");
        ExprNode.Or or = assertInstanceOf(ExprNode.Or.class, e);      // || is loosest -> root Or
        assertInstanceOf(ExprNode.And.class, or.right());

        ExprNode arith = CelTextParser.parse("x + y * z < 10");
        ExprNode.Lt lt = assertInstanceOf(ExprNode.Lt.class, arith);  // relation is loosest of these
        ExprNode.Add add = assertInstanceOf(ExprNode.Add.class, lt.left());
        assertInstanceOf(ExprNode.Mul.class, add.right());            // * tighter than +

        loop("a ? b : c ? d : e");
        loop("a || b && c");
        loop("(x + y) * z >= 10 - 2");
    }

    @Test
    void membershipBothOperandForms() throws Exception {
        CelFilterRecord litList = loop("status in [\"A\", \"B\", \"C\"]");
        ExprNode.In in1 = assertInstanceOf(ExprNode.In.class, litList.expression());
        assertInstanceOf(ExprNode.InListOperand.LiteralList.class, in1.listOperand());

        CelFilterRecord fieldList = loop("needle in tags");
        ExprNode.In in2 = assertInstanceOf(ExprNode.In.class, fieldList.expression());
        assertInstanceOf(ExprNode.InListOperand.FieldList.class, in2.listOperand());
    }

    @Test
    void unaryMinusAndTheTwoPow63Fold() throws Exception {
        // -5 is Neg(LitInt 5); -9223372036854775808 folds to LitInt(Long.MIN_VALUE).
        assertInstanceOf(ExprNode.Neg.class, CelTextParser.parse("-5"));
        ExprNode min = CelTextParser.parse("-9223372036854775808");
        ExprNode.LitInt lit = assertInstanceOf(ExprNode.LitInt.class, min);
        assertEquals(Long.MIN_VALUE, lit.value());
        loop("-5 < 0");
        loop("-9223372036854775808 < 0");
    }

    @Test
    void nestedFieldSelectionAndQualified() throws Exception {
        ExprNode.Gt gt = assertInstanceOf(ExprNode.Gt.class, CelTextParser.parse("a.b.c > 0"));
        ExprNode.FieldRef fr = assertInstanceOf(ExprNode.FieldRef.class, gt.left());
        assertEquals(3, fr.steps().size());
        loop("p.field(\"com.example.Point\", \"x\") > 0.0");
        loop("a.b.c > 0");
    }

    @Test
    void stringMethodsAndOverloadedFunctionsByLiteralType() throws Exception {
        // size overload resolves from the literal argument type.
        assertEquals(1, ((ExprNode.Call) CelTextParser.parse("size(\"abc\")")).functionId());     // size(string)
        assertEquals(2, ((ExprNode.Call) CelTextParser.parse("size(b\"\\x01\\x02\")")).functionId()); // size(bytes)
        assertEquals(3, ((ExprNode.Call) CelTextParser.parse("size([1, 2, 3])")).functionId());   // size(list)
        assertEquals(6, ((ExprNode.Call) CelTextParser.parse("abs(-3)")).functionId());           // abs(int)
        assertEquals(7, ((ExprNode.Call) CelTextParser.parse("abs(-3.0)")).functionId());         // abs(double)
        assertEquals(8, ((ExprNode.Call) CelTextParser.parse("min(3, 5)")).functionId());         // min(int,int)
        assertEquals(9, ((ExprNode.Call) CelTextParser.parse("min(3.0, 5.0)")).functionId());     // min(double,double)
        assertEquals(FunctionRegistry.ENDS_WITH_ID, ((ExprNode.Call) CelTextParser.parse("s.endsWith(x)")).functionId());
        loop("size(\"abc\") >= 3");           // size over a literal is resolvable and round-trips
    }

    @Test
    void transformExpressionRoundTrips() throws Exception {
        // §14.3-shaped element (double arithmetic over trig), as a scalar transform.
        ExprNode e = CelTextParser.parse("slopeDistM * cos(radians(elevationDeg))");
        CelFilterRecord record = CelRecordBuilder.transformScalar(e, au.net.zeus.jgdms.cel.wire.ScalarType.DOUBLE_T);
        byte[] wire = CelEncoder.encode(record);
        assertEquals(record, CelDecoder.decode(wire));
    }

    @Test
    void commentsAndWhitespaceIgnored() throws Exception {
        loop("a > 0   // trailing comment\n   && b < 10");
        assertTrue(CelTextParser.parse("  a  ") instanceof ExprNode.FieldRef);
    }
}
