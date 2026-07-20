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

import au.net.zeus.jgdms.cel.testsupport.TestDer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * STD-011 §10.3's inclusive fencepost convention (a value exactly at a
 * ceiling is accepted; ceiling+1 is rejected), exercised against the real
 * decoder for {@code maxExprNodes}, {@code maxExprDepth}, and {@code
 * maxSelectorSteps}, plus Appendix B §B.6.2's node-counting regression (a
 * naive per-{@code ExprNode}-arm-only counter undercounts selector steps).
 */
class CeilingFencepostTest {

    private static void assertRejected(byte[] exprBytes) {
        byte[] wire = TestDer.wrapAsPredicateRecord(exprBytes);
        assertThrows(CelDecodeException.class, () -> CelDecoder.decode(wire));
    }

    private static void assertAccepted(byte[] exprBytes) throws Exception {
        byte[] wire = TestDer.wrapAsPredicateRecord(exprBytes);
        assertNotNull(CelDecoder.decode(wire));
    }

    private static byte[] nestedNot(int depth) {
        // depth == 1: just the innermost literal. depth == k: (k-1) NOT wrappers around it.
        byte[] node = TestDer.litBool(true);
        for (int i = 1; i < depth; i++) {
            node = TestDer.notOp(node);
        }
        return node;
    }

    private static byte[] listOfLitInts(int n) {
        byte[][] elements = new byte[n][];
        for (int i = 0; i < n; i++) elements[i] = TestDer.litInt(i);
        return TestDer.listLit(elements);
    }

    // ---- maxExprNodes = 1024 ------------------------------------------------

    @Test
    void nodeCountExactly1024Accepted() throws Exception {
        // ListLit wrapper (1 node) + 1023 LitInt elements (1023 nodes) = 1024 total.
        assertAccepted(listOfLitInts(1023));
    }

    @Test
    void nodeCountExactly1025Rejected() {
        // 1 + 1024 = 1025 -- one past the ceiling.
        assertRejected(listOfLitInts(1024));
    }

    // ---- maxExprDepth = 32 (root counts as depth 1) -------------------------

    @Test
    void depthExactly32Accepted() throws Exception {
        assertAccepted(nestedNot(32));
    }

    @Test
    void depthExactly33Rejected() {
        assertRejected(nestedNot(33));
    }

    // ---- maxSelectorSteps = 16 -----------------------------------------------

    @Test
    void selectorStepsExactly16Accepted() throws Exception {
        assertAccepted(TestDer.fieldRefNSteps(16, "f"));
    }

    @Test
    void selectorStepsExactly17Rejected() {
        assertRejected(TestDer.fieldRefNSteps(17, "f"));
    }

    // ---- Appendix B §B.6.2: the step-inclusive node-counting regression -----

    /**
     * §B.6.2's illustrative attack, reproduced at scale: 512 {@code HAS}
     * nodes, each wrapping a {@code FieldRefNode} with the maximum 16
     * selector steps -- 512 x 16 = 8192 total selector steps. A decoder that
     * counts only {@code ExprNode} arms (ignoring that every selector step is
     * itself a node, per STD-011 §10.3's own definition) would compute a
     * count of roughly 513 (1 {@code ListLit} wrapper + 512 {@code HAS}
     * arms) -- comfortably under {@code maxExprNodes} (1024) -- and wrongly
     * accept. The corrected, step-inclusive count is 1 + 512 x (1 HAS + 1
     * embedded FIELD_REF + 16 steps) = 9217, far over the ceiling, and MUST
     * be rejected.
     */
    @Test
    void stepInclusiveNodeCountRegression_8192Steps() {
        int hasCount = 512;
        byte[][] hasNodes = new byte[hasCount][];
        for (int i = 0; i < hasCount; i++) {
            hasNodes[i] = TestDer.has(fullSteps16("f" + i + "_"));
        }
        byte[] expr = TestDer.listLit(hasNodes);
        assertRejected(expr);
    }

    private static String[] fullSteps16(String prefix) {
        String[] steps = new String[16];
        for (int i = 0; i < 16; i++) steps[i] = prefix + i;
        return steps;
    }
}
