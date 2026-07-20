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
import au.net.zeus.jgdms.cel.wire.CelCeilings;
import au.net.zeus.jgdms.cel.wire.FunctionRegistry;

import java.util.List;
import java.util.OptionalLong;

/**
 * The static cost model of JGDMS-STD-011 §10.2/§10.4: computes {@code C(E)}
 * for an already-decoded AST from the expression alone (no candidate needed),
 * in checked 64-bit arithmetic (§10.6) -- any intermediate overflow is
 * treated as exceeding {@code maxExprCost}, never allowed to wrap silently.
 *
 * <h3>A necessary, documented judgment call: comparison-node typing without a schema</h3>
 * §10.2's table charges string/bytes {@code ==}/{@code !=}/ordering at
 * {@code 1 + S} but numeric comparisons at {@code 1} -- a distinction that,
 * read literally, requires knowing each comparison operand's <em>static</em>
 * type. STD-011 §12.4 makes schema-based static typing optional ("where a
 * candidate schema is available"); this decoder/cost-model is deliberately
 * schema-decoupled (the SOW's {@code CandidateProjection} boundary), so a
 * bare {@code FIELD_REF} operand's type is generically unknowable here.
 * <p>
 * This implementation resolves that gap soundly rather than by picking an
 * arbitrary default: every node kind other than a bare {@code FIELD_REF} (or
 * a {@code COND} whose branches disagree) has a type that is <em>fully
 * determined by the closed grammar</em> regardless of runtime data --
 * arithmetic operators are only ever numeric-or-{@code TYPE_MISMATCH} (never
 * a string scan), every platform function's return type is pinned by its
 * function id (Appendix B §B.8.3), and so on. A comparison is charged the
 * expensive string/bytes rate <em>only when neither operand's statically
 * determined category rules out {@code string}/{@code bytes}</em> -- because
 * if one operand's type can only ever be numeric/bool/null/list/object, the
 * comparison at evaluation either performs a cheap numeric/typed comparison
 * or fails fast with {@code TYPE_MISMATCH} (§6.1/§6.2): it can never actually
 * perform a string/bytes scan. This keeps the model a true upper bound
 * (§3.1 Claim 3) without requiring a schema, and is tighter than flatly
 * charging every comparison at the string rate (which would make ordinary
 * numeric-heavy predicates like STD-011 §14.1's worked example implausibly
 * expensive). See the accompanying report for this as a flagged judgment
 * call rather than a spec-mandated algorithm -- §10.2 itself is marked
 * "[unit values PROPOSED]".
 *
 * <h3>A second, documented judgment call: tightening {@code 1 + S} to {@code 1 + min(S, known literal length)}</h3>
 * §10.2's table charges every string/bytes {@code ==}/{@code !=}/ordering
 * comparison at exactly {@code 1 + S} regardless of whether either operand
 * happens to be a literal of known length. This implementation instead
 * charges {@code 1 + min(S, known literal octet length)} whenever one (or
 * both) operands is a literal ({@link #comparisonCostByCategory}) --
 * strictly tighter than, never exceeding, the table's {@code 1 + S}, and
 * therefore still a sound upper bound: a string/bytes comparison's actual
 * work is bounded by the shorter operand (Java's {@code String#equals}/
 * lexicographic ordering, and any reasonable byte-array comparison, are both
 * O(min(len)), never O(S) when a shorter length is already known statically).
 * This tightening is ratified per JGDMS-STD-011 §10.2's board note
 * (2026-07-20) rather than merely an implementation liberty.
 *
 * <h3>Tree, not DAG</h3>
 * This model assumes {@code ExprNode} forms a <em>tree</em> -- every node
 * has exactly one parent -- which is what {@code CelDecoder} always
 * produces (there is no wire representation for node sharing). Handed a
 * hand-built {@code ExprNode} graph that is actually a DAG (the same node
 * instance reachable via more than one path), this implementation computes
 * cost per-path, i.e. a shared subexpression is charged once for each path
 * that reaches it (matching §10.2's own "no CSE assumed" syntactic-cost
 * rule for a tree, extended the only sensible way to a DAG). The result can
 * therefore exceed what a CSE-aware accounting would charge, but the
 * {@code maxExprCost} gate still soundly bounds the returned value in every
 * case -- it is never under-counted, only potentially conservative.
 */
public final class CostModel {

    private CostModel() {}

    private static final long S = CelCeilings.MAX_SCALAR_BYTES;
    private static final long K = CelCeilings.MAX_COLLECTION;

    /**
     * Computes {@code C(E)} for the given AST root.
     *
     * @throws CostExceededException if the computed cost exceeds {@code
     *         maxExprCost}, or if any intermediate checked-arithmetic term
     *         overflows 64 bits (treated identically, per §10.6)
     */
    public static long computeCost(ExprNode root) throws CostExceededException {
        long total = cost(root);
        if (total > CelCeilings.MAX_EXPR_COST) {
            throw new CostExceededException("C(E) = " + total + " exceeds maxExprCost ("
                    + CelCeilings.MAX_EXPR_COST + ")");
        }
        return total;
    }

    // ======================================================================
    // Checked accumulation (STD-011 §10.6)
    // ======================================================================

    private static long add(long a, long b) throws CostExceededException {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException e) {
            throw new CostExceededException("cost accumulator overflow on addition (" + a + " + " + b + ")");
        }
    }

    private static long mul(long a, long b) throws CostExceededException {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            throw new CostExceededException("cost accumulator overflow on multiplication (" + a + " * " + b + ")");
        }
    }

    // ======================================================================
    // Per-node cost (STD-011 §10.2's table)
    // ======================================================================

    private static long cost(ExprNode node) throws CostExceededException {
        if (node instanceof ExprNode.LitBool
                || node instanceof ExprNode.LitInt
                || node instanceof ExprNode.LitDouble
                || node instanceof ExprNode.LitString
                || node instanceof ExprNode.LitBytes
                || node instanceof ExprNode.LitNull) {
            return 1L;
        }
        if (node instanceof ExprNode.ListLit l) {
            long own = l.elements().size(); // "list literal of n elements | n"
            long sum = own;
            for (ExprNode e : l.elements()) {
                sum = add(sum, cost(e));
            }
            return sum;
        }
        if (node instanceof ExprNode.FieldRef f) {
            return f.steps().size(); // "field reference (per selector step) | 1" each
        }
        if (node instanceof ExprNode.Has h) {
            return add(1L, cost(h.target()));
        }
        if (node instanceof ExprNode.Not n) {
            return add(1L, cost(n.operand()));
        }
        if (node instanceof ExprNode.Neg n) {
            return add(1L, cost(n.operand()));
        }
        if (node instanceof ExprNode.And b) {
            return add(1L, add(cost(b.left()), cost(b.right())));
        }
        if (node instanceof ExprNode.Or b) {
            return add(1L, add(cost(b.left()), cost(b.right())));
        }
        if (node instanceof ExprNode.Eq b) {
            return comparisonNodeCost(b.left(), b.right());
        }
        if (node instanceof ExprNode.Ne b) {
            return comparisonNodeCost(b.left(), b.right());
        }
        if (node instanceof ExprNode.Lt b) {
            return comparisonNodeCost(b.left(), b.right());
        }
        if (node instanceof ExprNode.Le b) {
            return comparisonNodeCost(b.left(), b.right());
        }
        if (node instanceof ExprNode.Gt b) {
            return comparisonNodeCost(b.left(), b.right());
        }
        if (node instanceof ExprNode.Ge b) {
            return comparisonNodeCost(b.left(), b.right());
        }
        if (node instanceof ExprNode.Add b) {
            return add(1L, add(cost(b.left()), cost(b.right())));
        }
        if (node instanceof ExprNode.Sub b) {
            return add(1L, add(cost(b.left()), cost(b.right())));
        }
        if (node instanceof ExprNode.Mul b) {
            return add(1L, add(cost(b.left()), cost(b.right())));
        }
        if (node instanceof ExprNode.Div b) {
            return add(1L, add(cost(b.left()), cost(b.right())));
        }
        if (node instanceof ExprNode.Mod b) {
            return add(1L, add(cost(b.left()), cost(b.right())));
        }
        if (node instanceof ExprNode.In in) {
            return inCost(in);
        }
        if (node instanceof ExprNode.Cond c) {
            long own = 1L; // "?:" is charged like the other O(1) control constructs
            long sum = add(own, cost(c.condition()));
            sum = add(sum, cost(c.thenBranch()));
            sum = add(sum, cost(c.elseBranch()));
            return sum;
        }
        if (node instanceof ExprNode.Call call) {
            return callCost(call);
        }
        throw new IllegalStateException("unreachable ExprNode kind: " + node.getClass());
    }

    private static long comparisonNodeCost(ExprNode left, ExprNode right) throws CostExceededException {
        long own = comparisonCost(left, right);
        return add(own, add(cost(left), cost(right)));
    }

    /** The comparison-cost <em>contribution</em> of comparing two operands (excludes their own child costs). */
    private static long comparisonCost(ExprNode a, ExprNode b) throws CostExceededException {
        return comparisonCostByCategory(categoryOf(a), staticLen(a), categoryOf(b), staticLen(b));
    }

    private static long comparisonCostByCategory(CostCategory catA, OptionalLong lenA,
            CostCategory catB, OptionalLong lenB) throws CostExceededException {
        if (catA.excludesStringOrBytes() || catB.excludesStringOrBytes()) {
            return 1L; // cheap numeric/bool/null/list/object comparison, or an O(1) TYPE_MISMATCH
        }
        long bound = S;
        if (lenA.isPresent()) bound = Math.min(bound, lenA.getAsLong());
        if (lenB.isPresent()) bound = Math.min(bound, lenB.getAsLong());
        return add(1L, bound);
    }

    private static long inCost(ExprNode.In in) throws CostExceededException {
        long total = cost(in.needle());
        if (in.listOperand() instanceof ExprNode.InListOperand.LiteralList ll) {
            for (ExprNode element : ll.list().elements()) {
                total = add(total, cost(element));
                total = add(total, comparisonCost(in.needle(), element));
            }
            return total;
        } else if (in.listOperand() instanceof ExprNode.InListOperand.FieldList fl) {
            total = add(total, fl.field().steps().size()); // the FieldRef's own cost
            // A field-sourced collection's element type is not statically known here.
            long perElement = comparisonCostByCategory(
                    categoryOf(in.needle()), staticLen(in.needle()),
                    CostCategory.UNKNOWN, OptionalLong.empty());
            total = add(total, add(1L, mul(K, perElement)));
            return total;
        }
        throw new IllegalStateException("unreachable InListOperand kind");
    }

    private static long callCost(ExprNode.Call call) throws CostExceededException {
        FunctionRegistry.Entry entry = FunctionRegistry.byId(call.functionId());
        if (entry == null) {
            // Decoder already rejects unregistered ids; defensive fallback only.
            throw new IllegalStateException("cost model given an unregistered functionId " + call.functionId());
        }
        long own = switch (entry.name()) {
            case SIZE_STRING, SIZE_BYTES, SIZE_LIST,
                 INT_OF_DOUBLE, DOUBLE_OF_INT,
                 ABS_INT, ABS_DOUBLE,
                 MIN_INT, MIN_DOUBLE, MAX_INT, MAX_DOUBLE -> 1L;
            case SQRT, RADIANS, DEGREES -> 4L;
            case SIN, COS, TAN, ASIN, ACOS, ATAN, ATAN2 -> 32L;
            case CONTAINS -> {
                // functionId 22; decoder guarantees arguments.get(1) is a LIT_STRING.
                ExprNode needle = call.arguments().get(1);
                long needleLen = staticLen(needle).orElseThrow(
                        () -> new IllegalStateException("contains(): needle was not a literal despite decoder guarantee"));
                yield add(1L, mul(S, needleLen));
            }
            case STARTS_WITH, ENDS_WITH -> {
                ExprNode needle = call.arguments().get(1);
                long bound = staticLen(needle).orElse(S);
                yield add(1L, Math.min(S, bound));
            }
        };
        long sum = own;
        for (ExprNode arg : call.arguments()) {
            sum = add(sum, cost(arg));
        }
        return sum;
    }

    // ======================================================================
    // Static type category inference (cost-model-only; NOT the evaluator's
    // dynamic typing, which always additionally applies per STD-011 §6-§9)
    // ======================================================================

    private enum CostCategory {
        NUMERIC, BOOL, STRING, BYTES, NULL_T, LIST, OBJECT, UNKNOWN;

        /** True if this category can never be {@code string}/{@code bytes} at evaluation. */
        boolean excludesStringOrBytes() {
            return this != STRING && this != BYTES && this != UNKNOWN;
        }
    }

    private static CostCategory categoryOf(ExprNode node) {
        if (node instanceof ExprNode.LitBool) return CostCategory.BOOL;
        if (node instanceof ExprNode.LitInt) return CostCategory.NUMERIC;
        if (node instanceof ExprNode.LitDouble) return CostCategory.NUMERIC;
        if (node instanceof ExprNode.LitString) return CostCategory.STRING;
        if (node instanceof ExprNode.LitBytes) return CostCategory.BYTES;
        if (node instanceof ExprNode.LitNull) return CostCategory.NULL_T;
        if (node instanceof ExprNode.ListLit) return CostCategory.LIST;
        if (node instanceof ExprNode.FieldRef) return CostCategory.UNKNOWN;
        if (node instanceof ExprNode.Has) return CostCategory.BOOL;
        if (node instanceof ExprNode.Not) return CostCategory.BOOL;
        if (node instanceof ExprNode.Neg) return CostCategory.NUMERIC;
        if (node instanceof ExprNode.And) return CostCategory.BOOL;
        if (node instanceof ExprNode.Or) return CostCategory.BOOL;
        if (node instanceof ExprNode.Eq) return CostCategory.BOOL;
        if (node instanceof ExprNode.Ne) return CostCategory.BOOL;
        if (node instanceof ExprNode.Lt) return CostCategory.BOOL;
        if (node instanceof ExprNode.Le) return CostCategory.BOOL;
        if (node instanceof ExprNode.Gt) return CostCategory.BOOL;
        if (node instanceof ExprNode.Ge) return CostCategory.BOOL;
        if (node instanceof ExprNode.Add) return CostCategory.NUMERIC;
        if (node instanceof ExprNode.Sub) return CostCategory.NUMERIC;
        if (node instanceof ExprNode.Mul) return CostCategory.NUMERIC;
        if (node instanceof ExprNode.Div) return CostCategory.NUMERIC;
        if (node instanceof ExprNode.Mod) return CostCategory.NUMERIC;
        if (node instanceof ExprNode.In) return CostCategory.BOOL;
        if (node instanceof ExprNode.Cond c) {
            CostCategory t = categoryOf(c.thenBranch());
            CostCategory e = categoryOf(c.elseBranch());
            return t == e ? t : CostCategory.UNKNOWN;
        }
        if (node instanceof ExprNode.Call call) {
            FunctionRegistry.Entry entry = FunctionRegistry.byId(call.functionId());
            if (entry == null) return CostCategory.UNKNOWN;
            return switch (entry.name()) {
                case SIZE_STRING, SIZE_BYTES, SIZE_LIST, INT_OF_DOUBLE, ABS_INT, MIN_INT, MAX_INT -> CostCategory.NUMERIC;
                case DOUBLE_OF_INT, ABS_DOUBLE, MIN_DOUBLE, MAX_DOUBLE,
                     SQRT, RADIANS, DEGREES, SIN, COS, TAN, ASIN, ACOS, ATAN, ATAN2 -> CostCategory.NUMERIC;
                case CONTAINS, STARTS_WITH, ENDS_WITH -> CostCategory.BOOL;
            };
        }
        return CostCategory.UNKNOWN;
    }

    private static OptionalLong staticLen(ExprNode node) {
        if (node instanceof ExprNode.LitString s) {
            return OptionalLong.of(utf8OctetLength(s.value()));
        }
        if (node instanceof ExprNode.LitBytes b) {
            return OptionalLong.of(b.value().length);
        }
        return OptionalLong.empty();
    }

    /**
     * The UTF-8 wire-octet length of a well-formed Java string (no lone
     * surrogates -- the same precondition {@code CandidateProjection} and the
     * DER decode path already guarantee for every string this cost model
     * ever measures). Every string-literal length term in §10.2's table
     * (e.g. {@code contains}'s {@code len(needle literal)}, {@code
     * startsWith}/{@code endsWith}'s "needle bound") MUST use this unit, not
     * UTF-16 code units and not code points: it is the language-neutral
     * upper bound consistent with {@code S = maxScalarBytes} being an octet
     * count, and matches what a Rust (UTF-8-scanning) evaluator would
     * actually measure. Using {@code codePointCount()} here previously
     * under-charged supplementary-plane needles by up to ~2x relative to the
     * Java evaluator's own UTF-16 scan, and relative to a UTF-8 scan by more.
     */
    private static long utf8OctetLength(String s) {
        long len = 0;
        int n = s.length();
        for (int offset = 0; offset < n; ) {
            int cp = s.codePointAt(offset);
            offset += Character.charCount(cp);
            if (cp <= 0x7F) len += 1;
            else if (cp <= 0x7FF) len += 2;
            else if (cp <= 0xFFFF) len += 3;
            else len += 4;
        }
        return len;
    }
}
