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

package au.net.zeus.jgdms.cel.wire;

import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.testsupport.TestDer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Appendix B §B.10's worked examples, decoded byte-exact from the printed
 * hex in the specification.
 */
class WorkedExamplesDecodeTest {

    /** §B.10.1: {@code sampleCount >= 30} (predicate context). Full CelFilterRecord hex, printed verbatim. */
    @Test
    void b10_1_literalIntComparison() throws Exception {
        byte[] wire = TestDer.hexToBytes("301b0201018000b214a70f300d800b73616d706c65436f756e7481011e");
        assertEquals(29, wire.length);

        CelFilterRecord record = CelDecoder.decode(wire);
        assertEquals(1, record.formatVersion());
        assertInstanceOf(EvaluationContext.Predicate.class, record.context());

        ExprNode.Ge ge = assertInstanceOf(ExprNode.Ge.class, record.expression());
        ExprNode.FieldRef fieldRef = assertInstanceOf(ExprNode.FieldRef.class, ge.left());
        assertEquals(1, fieldRef.steps().size());
        ExprNode.SelectorStep.Unqual step = assertInstanceOf(ExprNode.SelectorStep.Unqual.class, fieldRef.steps().get(0));
        assertEquals("sampleCount", step.name());
        ExprNode.LitInt lit = assertInstanceOf(ExprNode.LitInt.class, ge.right());
        assertEquals(30L, lit.value());
    }

    /**
     * §B.10.2: {@code x > 0 && x < 100} -- the printed hex is the bare
     * {@code ExprNode}, embedded here (byte-for-byte, unmodified) inside a
     * synthetic envelope so the public decoder (which decodes a complete
     * {@code CelFilterRecord}) can be exercised against it.
     */
    @Test
    void b10_2_compoundAndPredicate() throws Exception {
        byte[] exprBytes = TestDer.hexToBytes("ab18b10aa7053003800178810100af0aa7053003800178810164");
        assertEquals(26, exprBytes.length);
        byte[] wire = TestDer.filterRecord(1, TestDer.predicateContext(), exprBytes);

        CelFilterRecord record = CelDecoder.decode(wire);
        ExprNode.And and = assertInstanceOf(ExprNode.And.class, record.expression());

        ExprNode.Gt gt = assertInstanceOf(ExprNode.Gt.class, and.left());
        ExprNode.FieldRef xRefLeft = assertInstanceOf(ExprNode.FieldRef.class, gt.left());
        assertEquals("x", unqual(xRefLeft));
        assertEquals(0L, assertInstanceOf(ExprNode.LitInt.class, gt.right()).value());

        ExprNode.Lt lt = assertInstanceOf(ExprNode.Lt.class, and.right());
        ExprNode.FieldRef xRefRight = assertInstanceOf(ExprNode.FieldRef.class, lt.left());
        assertEquals("x", unqual(xRefRight));
        assertEquals(100L, assertInstanceOf(ExprNode.LitInt.class, lt.right()).value());
    }

    /** §B.10.3: {@code a.b} -- a 2-step selector chain, embedded the same way as B.10.2. */
    @Test
    void b10_3_twoStepFieldRef() throws Exception {
        byte[] exprBytes = TestDer.hexToBytes("a7083006800161800162");
        assertEquals(10, exprBytes.length);
        byte[] wire = TestDer.filterRecord(1, TestDer.predicateContext(), exprBytes);

        CelFilterRecord record = CelDecoder.decode(wire);
        ExprNode.FieldRef fr = assertInstanceOf(ExprNode.FieldRef.class, record.expression());
        assertEquals(2, fr.steps().size());
        assertEquals("a", ((ExprNode.SelectorStep.Unqual) fr.steps().get(0)).name());
        assertEquals("b", ((ExprNode.SelectorStep.Unqual) fr.steps().get(1)).name());
    }

    /**
     * §B.10.4: the Survey-zoot transform's outer structure -- {@code context
     * = transform}, declared result type {@code list<double>}, wrapping a
     * 3-element {@code LIST_LIT} of (placeholder, zero-valued) {@code
     * LIT_DOUBLE}s. Printed hex, decoded byte-exact.
     */
    @Test
    void b10_4_transformOuterStructure() throws Exception {
        byte[] wire = TestDer.hexToBytes(
                "3028020101a103820102a61e820800000000000000008208000000000000000082080000000000000000");
        assertEquals(42, wire.length);

        CelFilterRecord record = CelDecoder.decode(wire);
        assertEquals(1, record.formatVersion());

        EvaluationContext.Transform transform = assertInstanceOf(EvaluationContext.Transform.class, record.context());
        DeclaredResultType.ListType listType = assertInstanceOf(DeclaredResultType.ListType.class, transform.resultType());
        assertEquals(ScalarType.DOUBLE_T, listType.elementType());

        ExprNode.ListLit listLit = assertInstanceOf(ExprNode.ListLit.class, record.expression());
        assertEquals(3, listLit.elements().size());
        for (ExprNode element : listLit.elements()) {
            ExprNode.LitDouble d = assertInstanceOf(ExprNode.LitDouble.class, element);
            assertEquals(0.0, d.value());
        }
    }

    private static String unqual(ExprNode.FieldRef fr) {
        assertEquals(1, fr.steps().size());
        return ((ExprNode.SelectorStep.Unqual) fr.steps().get(0)).name();
    }
}
