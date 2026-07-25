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
import au.net.zeus.jgdms.cel.ast.ExprNode.InListOperand;
import au.net.zeus.jgdms.cel.ast.ExprNode.SelectorStep;
import au.net.zeus.jgdms.cel.wire.CelDecoder;
import au.net.zeus.jgdms.cel.wire.CelFilterRecord;
import au.net.zeus.jgdms.cel.wire.DeclaredResultType;
import au.net.zeus.jgdms.cel.wire.EvaluationContext;
import au.net.zeus.jgdms.cel.wire.ScalarType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AST -&gt; wire -&gt; decode round-trip over a broad corpus covering all 27
 * {@code ExprNode} kinds, both {@code SelectorStep} arms, both
 * {@code InListOperand} arms, all four {@code DeclaredResultType} arms, both
 * {@code EvaluationContext} arms, and adversarial/boundary scalar values. The
 * jgdms-cel wire decoder is the oracle: {@code decode(encode(r)).equals(r)}.
 */
class RoundTripTest {

    private static ExprNode.FieldRef fr(String... names) {
        List<SelectorStep> steps = new ArrayList<>();
        for (String n : names) steps.add(new SelectorStep.Unqual(n));
        return new ExprNode.FieldRef(steps);
    }

    private static void roundTrip(ExprNode expr) throws Exception {
        CelFilterRecord in = CelRecordBuilder.predicate(expr);
        byte[] wire = CelEncoder.encode(in);
        assertEquals(in, CelDecoder.decode(wire), () -> "round-trip mismatch for " + expr);
    }

    private static void roundTrip(CelFilterRecord in) throws Exception {
        byte[] wire = CelEncoder.encode(in);
        assertEquals(in, CelDecoder.decode(wire));
    }

    @Test
    void allNodeKinds() throws Exception {
        ExprNode a = fr("a");
        ExprNode b = fr("b");
        List<ExprNode> corpus = List.of(
                new ExprNode.LitBool(true),
                new ExprNode.LitBool(false),
                new ExprNode.LitInt(0),
                new ExprNode.LitInt(30),
                new ExprNode.LitInt(-1),
                new ExprNode.LitInt(Long.MAX_VALUE),
                new ExprNode.LitInt(Long.MIN_VALUE),
                new ExprNode.LitDouble(0.0),
                new ExprNode.LitDouble(-0.0),
                new ExprNode.LitDouble(3.141592653589793),
                new ExprNode.LitString("hello"),
                new ExprNode.LitString(""),
                new ExprNode.LitBytes(new byte[]{ 0, 1, 2, (byte) 0xFF }),
                new ExprNode.LitBytes(new byte[0]),
                new ExprNode.LitNull(),
                new ExprNode.ListLit(List.of(new ExprNode.LitInt(1), new ExprNode.LitInt(2), new ExprNode.LitInt(3))),
                fr("x"),
                new ExprNode.FieldRef(List.of(new SelectorStep.Qual("com.example.Point", "x"))),
                new ExprNode.FieldRef(List.of(new SelectorStep.Unqual("p"), new SelectorStep.Qual("com.example.Point", "y"))),
                new ExprNode.Has(fr("note")),
                new ExprNode.Not(new ExprNode.LitBool(true)),
                new ExprNode.Neg(new ExprNode.LitInt(5)),
                new ExprNode.And(a, b),
                new ExprNode.Or(a, b),
                new ExprNode.Eq(a, b),
                new ExprNode.Ne(a, b),
                new ExprNode.Lt(a, b),
                new ExprNode.Le(a, b),
                new ExprNode.Gt(a, b),
                new ExprNode.Ge(a, b),
                new ExprNode.Add(a, b),
                new ExprNode.Sub(a, b),
                new ExprNode.Mul(a, b),
                new ExprNode.Div(a, b),
                new ExprNode.Mod(a, b),
                new ExprNode.In(new ExprNode.LitInt(2),
                        new InListOperand.LiteralList(new ExprNode.ListLit(List.of(new ExprNode.LitInt(1), new ExprNode.LitInt(2))))),
                new ExprNode.In(new ExprNode.LitString("z"), new InListOperand.FieldList(fr("tags"))),
                new ExprNode.Cond(fr("c"), new ExprNode.LitInt(1), new ExprNode.LitInt(2)),
                new ExprNode.Call(1, List.of(new ExprNode.LitString("s"))),                       // size(string)
                new ExprNode.Call(12, List.of(new ExprNode.LitDouble(2.0))),                      // sqrt
                new ExprNode.Call(21, List.of(new ExprNode.LitDouble(1.0), new ExprNode.LitDouble(2.0))), // atan2
                new ExprNode.Call(6, List.of(new ExprNode.LitInt(-3))),                           // abs(int)
                new ExprNode.Call(8, List.of(new ExprNode.LitInt(3), new ExprNode.LitInt(5))),    // min(int,int)
                new ExprNode.Call(22, List.of(fr("station"), new ExprNode.LitString("GLS-"))));   // contains
        for (ExprNode e : corpus) roundTrip(e);
    }

    @Test
    void supplementaryPlaneString() throws Exception {
        // U+1D11E MUSICAL SYMBOL G CLEF -- a supplementary-plane scalar (surrogate pair in Java).
        roundTrip(new ExprNode.LitString("clef=𝄞 end"));
        roundTrip(new ExprNode.LitString("accent é and é"));  // precomposed vs decomposed
    }

    @Test
    void allDeclaredResultTypeArms() throws Exception {
        ExprNode e = fr("v");
        for (ScalarType st : ScalarType.values()) {
            roundTrip(CelRecordBuilder.transformScalar(e, st));
            roundTrip(CelRecordBuilder.transformList(e, st));
        }
        roundTrip(CelRecordBuilder.transform(e, new DeclaredResultType.NullType()));
        roundTrip(CelRecordBuilder.transform(e, new DeclaredResultType.ObjectType()));
        roundTrip(new CelFilterRecord(1, new EvaluationContext.Predicate(), e));
    }

    @Test
    void nestingNearMaxDepth() throws Exception {
        // Root ExprNode counts as depth 1; each NOT adds one. 31 NOTs over a leaf = depth 32 (the ceiling, accepted).
        ExprNode e = new ExprNode.LitBool(true);
        for (int i = 0; i < 31; i++) e = new ExprNode.Not(e);
        roundTrip(e);
    }

    @Test
    void maxLengthUnqualName() throws Exception {
        roundTrip(fr("f".repeat(255)));                                   // exactly the 255-byte ceiling
    }

    @Test
    void maxSelectorSteps() throws Exception {
        List<SelectorStep> steps = new ArrayList<>();
        for (int i = 0; i < 16; i++) steps.add(new SelectorStep.Unqual("s" + i));  // exactly 16 (the ceiling)
        roundTrip(new ExprNode.FieldRef(steps));
    }

    @Test
    void singleElementListAndBoundaryInts() throws Exception {
        roundTrip(new ExprNode.ListLit(List.of(new ExprNode.LitInt(42))));         // 1-element list
        for (long v : new long[]{ 127, 128, 255, 256, -128, -129, 32767, 65535, Long.MIN_VALUE, Long.MAX_VALUE }) {
            roundTrip(new ExprNode.LitInt(v));
        }
    }
}
