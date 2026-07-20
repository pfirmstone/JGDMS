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

package au.net.zeus.jgdms.cel.cost;

import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.wire.FunctionRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * STD-011 §10's static cost model: the §14.3 worked example as a numeric
 * validation of the whole per-node table, and §10.6's mandatory 2^32
 * accumulator-width regression.
 */
class CostModelTest {

    private static ExprNode fieldRef(String name) {
        return new ExprNode.FieldRef(List.of(new ExprNode.SelectorStep.Unqual(name)));
    }

    private static ExprNode call(FunctionRegistry.Name name, ExprNode... args) {
        int id = -1;
        for (int i = 1; i <= 24; i++) {
            FunctionRegistry.Entry e = FunctionRegistry.byId(i);
            if (e != null && e.name() == name) { id = i; break; }
        }
        assertTrue(id > 0, "function not found: " + name);
        return new ExprNode.Call(id, List.of(args));
    }

    /**
     * STD-011 §14.3's worked Survey-zoot transform:
     * <pre>
     * [ slopeDistM * cos(radians(elevationDeg)) * sin(radians(bearingDeg)),
     *   slopeDistM * cos(radians(elevationDeg)) * cos(radians(bearingDeg)),
     *   slopeDistM * sin(radians(elevationDeg)) ]
     * </pre>
     * The spec states {@code C(E) = 196} ("5 trigonometric calls at 32, 5
     * radians at 4, 5 multiplies, 8 field references, list literal 3"). This
     * test builds the exact AST and asserts the cost model reproduces that
     * number precisely -- a strong end-to-end validation of the per-node
     * cost table, independent of the comparison-cost judgment call (this
     * expression has no comparisons at all).
     */
    @Test
    void surveyZootWorkedExample_costIs196() throws Exception {
        ExprNode slopeDistM1 = fieldRef("slopeDistM");
        ExprNode slopeDistM2 = fieldRef("slopeDistM");
        ExprNode slopeDistM3 = fieldRef("slopeDistM");
        ExprNode elevationDeg1 = fieldRef("elevationDeg");
        ExprNode elevationDeg2 = fieldRef("elevationDeg");
        ExprNode elevationDeg3 = fieldRef("elevationDeg");
        ExprNode bearingDeg1 = fieldRef("bearingDeg");
        ExprNode bearingDeg2 = fieldRef("bearingDeg");

        ExprNode cosElev1 = call(FunctionRegistry.Name.COS, call(FunctionRegistry.Name.RADIANS, elevationDeg1));
        ExprNode cosElev2 = call(FunctionRegistry.Name.COS, call(FunctionRegistry.Name.RADIANS, elevationDeg2));
        ExprNode sinBearing = call(FunctionRegistry.Name.SIN, call(FunctionRegistry.Name.RADIANS, bearingDeg1));
        ExprNode cosBearing = call(FunctionRegistry.Name.COS, call(FunctionRegistry.Name.RADIANS, bearingDeg2));
        ExprNode sinElev = call(FunctionRegistry.Name.SIN, call(FunctionRegistry.Name.RADIANS, elevationDeg3));

        ExprNode element1 = new ExprNode.Mul(new ExprNode.Mul(slopeDistM1, cosElev1), sinBearing);
        ExprNode element2 = new ExprNode.Mul(new ExprNode.Mul(slopeDistM2, cosElev2), cosBearing);
        ExprNode element3 = new ExprNode.Mul(slopeDistM3, sinElev);

        ExprNode listLit = new ExprNode.ListLit(List.of(element1, element2, element3));

        long cost = CostModel.computeCost(listLit);
        assertEquals(196L, cost);
    }

    /**
     * STD-011 §10.6's mandatory regression: an expression whose true cost
     * just exceeds 2^32 MUST be rejected -- and would incorrectly pass with
     * a 32-bit accumulator (since 2^32 wraps to 0 modulo 2^32). Concretely,
     * {@code contains}'s cost term is {@code 1 + S * len(needle literal)}
     * (§10.2); with {@code S = maxScalarBytes = 65536} and a needle literal
     * at exactly the maximum scalar length (also 65536), the term is
     * {@code 1 + 65536 * 65536 = 1 + 2^32 = 4294967297}, comfortably
     * representable in a 64-bit accumulator but wrapping to 1 in a 32-bit one.
     */
    @Test
    void costWrapRegression_justOver2pow32_rejected() {
        String needle = "x".repeat(65536);
        ExprNode receiver = fieldRef("someString");
        ExprNode containsCall = call(FunctionRegistry.Name.CONTAINS, receiver, new ExprNode.LitString(needle));

        CostExceededException ex = assertThrows(CostExceededException.class, () -> CostModel.computeCost(containsCall));
        assertNotNull(ex.getMessage());

        // Sanity-check the arithmetic claim itself: 65536L * 65536L + 1 is just over 2^32,
        // and a 32-bit (int) accumulator of that exact value wraps to a small number --
        // exactly the silent-bypass hazard §10.6 exists to close.
        long trueCost = 1L + 65536L * 65536L;
        assertTrue(trueCost > 4_294_967_296L);
        int wrapped32Bit = (int) trueCost;
        assertNotEquals(trueCost, (long) wrapped32Bit);
        assertTrue(wrapped32Bit < 1_000_000, "a wrapped 32-bit accumulator would have silently passed maxExprCost");
    }

    @Test
    void simpleNumericPredicate_isCheap() throws Exception {
        // STD-011 §14.1: pressureHpa >= 950.0 && pressureHpa < 1050.0 -- ordinary numeric comparisons
        // must NOT be charged at the string/bytes rate (that would make ordinary predicates
        // implausibly expensive; see CostModel's class javadoc for the reasoning).
        ExprNode ge = new ExprNode.Ge(fieldRef("pressureHpa"), new ExprNode.LitDouble(950.0));
        ExprNode lt = new ExprNode.Lt(fieldRef("pressureHpa"), new ExprNode.LitDouble(1050.0));
        ExprNode and = new ExprNode.And(ge, lt);
        long cost = CostModel.computeCost(and);
        assertTrue(cost < 20, "expected a cheap cost for an all-numeric predicate, got " + cost);
    }

    @Test
    void stringEqualityAgainstFieldOfUnknownType_isChargedAtStringRate() throws Exception {
        // Both operands are FIELD_REF (unknown static type) -- cannot rule out string/bytes,
        // so the conservative model must charge the expensive rate.
        ExprNode eq = new ExprNode.Eq(fieldRef("a"), fieldRef("b"));
        long cost = CostModel.computeCost(eq);
        assertTrue(cost > 60000, "expected the string/bytes-rate charge for two unknown-typed field operands, got " + cost);
    }

    /**
     * Board-review regression (MED): a supplementary-plane ("emoji") needle
     * literal must be measured in UTF-8 wire octets, not Unicode code
     * points. 7 supplementary-plane code points are 14 UTF-16 code units
     * (what the Java evaluator itself scans) and 28 UTF-8 octets (what
     * {@code S = maxScalarBytes} is denominated in, and what a Rust,
     * UTF-8-scanning evaluator would measure) -- but only 7 by
     * {@code codePointCount()}, the pre-fix measure. Charging by code point
     * under-charged such a needle by ~4x relative to the wire-octet unit
     * (~2x relative to the UTF-16-unit measure), letting an attacker craft
     * an expression whose real per-evaluation string-scan work the static
     * cost bound under-estimated.
     */
    @Test
    void startsWithSupplementaryPlaneNeedle_costReflectsUtf8OctetLength() throws Exception {
        String singleEmoji = "😀"; // U+1F600 GRINNING FACE: 1 code point, 2 UTF-16 units, 4 UTF-8 octets
        String needle = singleEmoji.repeat(7); // 7 code points

        assertEquals(7, needle.codePointCount(0, needle.length()), "sanity: 7 code points");
        assertEquals(14, needle.length(), "sanity: 14 UTF-16 code units (what the Java evaluator scans)");
        int utf8Octets = needle.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        assertEquals(28, utf8Octets, "sanity: 28 UTF-8 wire octets (the language-neutral upper bound)");

        ExprNode receiver = fieldRef("someString");
        ExprNode startsWithCall = call(FunctionRegistry.Name.STARTS_WITH, receiver, new ExprNode.LitString(needle));
        long cost = CostModel.computeCost(startsWithCall);

        // own cost = 1 + min(S, 28) = 29 (S = 65536 >> 28); + receiver FieldRef (1) + needle literal (1) = 31.
        assertEquals(31L, cost,
                "startsWith cost must charge the needle at its UTF-8 octet length (28), not its code-point count (7)");
        assertTrue(cost >= 1L + 1L + 1L + 14L,
                "cost must be >= the UTF-16-code-unit measure (14) that the Java evaluator itself scans, got " + cost);
    }
}
