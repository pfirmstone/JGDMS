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
import au.net.zeus.jgdms.cel.wire.ScalarType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The strongest G1 canonicality check: every byte-exact worked example in
 * Appendix B §B.10 (and STD-011 §14) is reproduced by {@link CelEncoder} from
 * the AST -- both from a hand-built AST and, where the spec also prints text,
 * from the {@link CelTextParser}-parsed AST. If any spec byte string cannot be
 * reproduced, that is a real finding, surfaced by a failing assertion here.
 */
class WorkedExamplesEncodeTest {

    /** §B.10.1: {@code sampleCount >= 30} (predicate). */
    @Test
    void b10_1_fromAst() throws Exception {
        ExprNode ge = new ExprNode.Ge(
                new ExprNode.FieldRef(List.of(new SelectorStep.Unqual("sampleCount"))),
                new ExprNode.LitInt(30));
        CelFilterRecord record = CelRecordBuilder.predicate(ge);
        assertEquals("301b0201018000b214a70f300d800b73616d706c65436f756e7481011e",
                Hex.toHex(CelEncoder.encode(record)));
    }

    @Test
    void b10_1_fromText() throws Exception {
        CelFilterRecord record = CelRecordBuilder.predicate(CelTextParser.parse("sampleCount >= 30"));
        assertEquals("301b0201018000b214a70f300d800b73616d706c65436f756e7481011e",
                Hex.toHex(CelEncoder.encode(record)));
    }

    /** §B.10.2: {@code x > 0 && x < 100} (bare ExprNode hex). */
    @Test
    void b10_2_fromAst() throws Exception {
        ExprNode x1 = new ExprNode.FieldRef(List.of(new SelectorStep.Unqual("x")));
        ExprNode x2 = new ExprNode.FieldRef(List.of(new SelectorStep.Unqual("x")));
        ExprNode and = new ExprNode.And(
                new ExprNode.Gt(x1, new ExprNode.LitInt(0)),
                new ExprNode.Lt(x2, new ExprNode.LitInt(100)));
        assertEquals("ab18b10aa7053003800178810100af0aa7053003800178810164",
                Hex.toHex(CelEncoder.encodeExpression(and)));
    }

    @Test
    void b10_2_fromText() throws Exception {
        assertEquals("ab18b10aa7053003800178810100af0aa7053003800178810164",
                Hex.toHex(CelEncoder.encodeExpression(CelTextParser.parse("x > 0 && x < 100"))));
    }

    /** §B.10.3: {@code a.b} (bare ExprNode hex). */
    @Test
    void b10_3_fromAst() throws Exception {
        ExprNode fr = new ExprNode.FieldRef(List.of(
                new SelectorStep.Unqual("a"), new SelectorStep.Unqual("b")));
        assertEquals("a7083006800161800162", Hex.toHex(CelEncoder.encodeExpression(fr)));
    }

    @Test
    void b10_3_fromText() throws Exception {
        assertEquals("a7083006800161800162", Hex.toHex(CelEncoder.encodeExpression(CelTextParser.parse("a.b"))));
    }

    /** §B.10.4: the Survey-zoot transform outer structure -- transform list&lt;double&gt; wrapping 3 zero LIT_DOUBLE. */
    @Test
    void b10_4_fromAst() throws Exception {
        ExprNode list = new ExprNode.ListLit(List.of(
                new ExprNode.LitDouble(0.0), new ExprNode.LitDouble(0.0), new ExprNode.LitDouble(0.0)));
        CelFilterRecord record = CelRecordBuilder.transformList(list, ScalarType.DOUBLE_T);
        assertEquals("3028020101a103820102a61e820800000000000000008208000000000000000082080000000000000000",
                Hex.toHex(CelEncoder.encode(record)));
    }

    @Test
    void b10_4_fromText() throws Exception {
        CelFilterRecord record = CelRecordBuilder.transformList(
                CelTextParser.parse("[0.0, 0.0, 0.0]"), ScalarType.DOUBLE_T);
        assertEquals("3028020101a103820102a61e820800000000000000008208000000000000000082080000000000000000",
                Hex.toHex(CelEncoder.encode(record)));
    }
}
