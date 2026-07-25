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
import au.net.zeus.jgdms.cel.wire.CelDecoder;
import au.net.zeus.jgdms.cel.wire.CelFilterRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Canonicality (G1) obligations of the encoder: minimal forms only, byte-exact
 * re-encode idempotence, and the guarantee that the encoder cannot emit a value
 * the decoder would reject (non-finite {@code LIT_DOUBLE}, ceiling violations).
 */
class EncoderCanonicalityTest {

    // ---- minimal forms ----
    @Test void minimalIntegerAndBooleanForms() throws Exception {
        assertEquals("81011e", Hex.toHex(CelEncoder.encodeExpression(new ExprNode.LitInt(30))));
        assertEquals("810100", Hex.toHex(CelEncoder.encodeExpression(new ExprNode.LitInt(0))));
        assertEquals("8001ff", Hex.toHex(CelEncoder.encodeExpression(new ExprNode.LitBool(true))));   // [0] BOOLEAN, TRUE == 0xFF exactly
        assertEquals("800100", Hex.toHex(CelEncoder.encodeExpression(new ExprNode.LitBool(false))));
        // 128 needs a leading 0x00 to stay positive: minimal two's-complement is 00 80.
        assertEquals("81020080", Hex.toHex(CelEncoder.encodeExpression(new ExprNode.LitInt(128))));
        // -128 is a single byte 0x80.
        assertEquals("810180", Hex.toHex(CelEncoder.encodeExpression(new ExprNode.LitInt(-128))));
    }

    // ---- re-encode idempotence over a corpus ----
    @Test void reEncodeIsByteIdentical() throws Exception {
        List<ExprNode> corpus = List.of(
                new ExprNode.LitInt(Long.MIN_VALUE),
                new ExprNode.LitDouble(-0.0),
                new ExprNode.LitString("héllo 𝄞"),
                new ExprNode.And(
                        new ExprNode.Ge(fr("sampleCount"), new ExprNode.LitInt(30)),
                        new ExprNode.Not(new ExprNode.Has(fr("note")))),
                new ExprNode.In(new ExprNode.LitInt(2),
                        new ExprNode.InListOperand.LiteralList(
                                new ExprNode.ListLit(List.of(new ExprNode.LitInt(1), new ExprNode.LitInt(2))))),
                new ExprNode.Call(21, List.of(new ExprNode.LitDouble(1.0), new ExprNode.LitDouble(2.0))));
        for (ExprNode e : corpus) {
            CelFilterRecord r = CelRecordBuilder.predicate(e);
            byte[] once = CelEncoder.encode(r);
            byte[] twice = CelEncoder.encode(CelDecoder.decode(once));   // encode(decode(encode(r)))
            assertArrayEquals(once, twice, () -> "re-encode not byte-identical: " + e);
        }
    }

    // ---- the encoder cannot emit a decoder-rejected value ----
    @Test void nonFiniteDoubleRejected() {
        assertThrows(CelEncodeException.class,
                () -> CelEncoder.encodeExpression(new ExprNode.LitDouble(Double.POSITIVE_INFINITY)));
        assertThrows(CelEncodeException.class,
                () -> CelEncoder.encodeExpression(new ExprNode.LitDouble(Double.NEGATIVE_INFINITY)));
        assertThrows(CelEncodeException.class,
                () -> CelEncoder.encodeExpression(new ExprNode.LitDouble(Double.NaN)));
    }

    @Test void ceilingViolatingAstRejectedAtEncode() {
        // 17 selector steps (over maxSelectorSteps = 16)
        List<SelectorStep> steps = new ArrayList<>();
        for (int i = 0; i < 17; i++) steps.add(new SelectorStep.Unqual("s"));
        assertThrows(CelEncodeException.class,
                () -> CelEncoder.encodeExpression(new ExprNode.FieldRef(steps)));

        // 256-byte unqualified name (over the 255-byte ceiling)
        assertThrows(CelEncodeException.class,
                () -> CelEncoder.encodeExpression(fr("f".repeat(256))));

        // depth 33 (over maxExprDepth = 32): 32 nested NOTs over a leaf
        ExprNode deep = new ExprNode.LitBool(true);
        for (int i = 0; i < 32; i++) deep = new ExprNode.Not(deep);
        ExprNode d = deep;
        assertThrows(CelEncodeException.class, () -> CelEncoder.encodeExpression(d));

        // scalar over maxScalarBytes (65537 bytes)
        assertThrows(CelEncodeException.class,
                () -> CelEncoder.encodeExpression(new ExprNode.LitString("x".repeat(65537))));
    }

    // ---- encoder output is always decoder-acceptable AND round-trips ----
    @Test void everyEncodableAstDecodesToItself() throws Exception {
        // A structurally deep, wide expression touching many arms at once.
        ExprNode e = new ExprNode.Cond(
                new ExprNode.And(
                        new ExprNode.Ge(fr("a"), new ExprNode.LitInt(1)),
                        new ExprNode.Lt(fr("a"), new ExprNode.LitInt(1000))),
                new ExprNode.Call(22, List.of(fr("name"), new ExprNode.LitString("GLS-"))),
                new ExprNode.In(fr("kind"),
                        new ExprNode.InListOperand.LiteralList(
                                new ExprNode.ListLit(List.of(
                                        new ExprNode.LitString("x"), new ExprNode.LitString("y"))))));
        CelFilterRecord r = CelRecordBuilder.predicate(e);
        assertEquals(r, CelDecoder.decode(CelEncoder.encode(r)));
    }

    private static ExprNode.FieldRef fr(String name) {
        return new ExprNode.FieldRef(List.of(new SelectorStep.Unqual(name)));
    }
}
