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
import au.net.zeus.jgdms.cel.wire.CelCeilings;
import au.net.zeus.jgdms.cel.wire.FunctionRegistry;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Validates a fully-built {@code ExprNode}. Two layers, kept deliberately
 * separate:
 * <ul>
 *   <li>{@link #findWireViolation} -- every invariant the jgdms-cel wire decoder
 *       ({@code CelDecoder}) itself enforces at decode: the {@code CelCeilings}
 *       bounds (node count, depth, selector steps, name byte-lengths, scalar
 *       byte-lengths), non-finite {@code LIT_DOUBLE} rejection, and the
 *       {@code CALL} function-id/arity/{@code contains}-needle checks. An AST
 *       passing this is guaranteed to encode to bytes the decoder accepts, so
 *       {@link CelEncoder} runs exactly this before emitting.</li>
 *   <li>{@link #findAuthoringViolation} -- the source-language well-formedness
 *       rules the wire decoder does <em>not</em> enforce (Appendix B §B.6.6
 *       assigns these to T6/evaluation, not T2): §5.3.2 list-literal
 *       homogeneity / no-null / no-nested-list elements, and the §5.4 item 6
 *       exclusion of string/bytes/list concatenation (statically-provable
 *       cases). The text parser runs this in addition; the encoder does not,
 *       so the encoder stays total over every AST the decoder would accept.</li>
 * </ul>
 * The node-count / depth / selector-step accounting in the wire layer mirrors
 * {@code CelDecoder} exactly (including fenceposts and the rule that each
 * {@code FieldRef} selector step is its own counted node, Appendix B §B.6.2),
 * so the two agree on every boundary case.
 * <p>
 * Each method returns a human-readable violation message, or {@code null} if
 * clean; callers wrap a non-null result in their own typed exception.
 */
final class CelAstValidator {

    private CelAstValidator() {}

    // =====================================================================
    // Wire-layer invariants (decoder-enforced) -- encoder AND parser
    // =====================================================================

    static String findWireViolation(ExprNode root) {
        try {
            visitExpr(root, 1, new Counter());
            return null;
        } catch (Violation v) {
            return v.getMessage();
        }
    }

    private static final class Counter {
        int nodeCount = 0;
    }

    private static final class Violation extends RuntimeException {
        private static final long serialVersionUID = 1L;
        Violation(String m) { super(m); }
    }

    private static void reject(String m) {
        throw new Violation(m);
    }

    private static void consumeNode(Counter c) {
        c.nodeCount++;
        if (c.nodeCount > CelCeilings.MAX_EXPR_NODES) {
            reject("maxExprNodes (" + CelCeilings.MAX_EXPR_NODES + ") exceeded");
        }
    }

    private static void visitExpr(ExprNode node, int depth, Counter c) {
        if (depth > CelCeilings.MAX_EXPR_DEPTH) {
            reject("maxExprDepth (" + CelCeilings.MAX_EXPR_DEPTH + ") exceeded");
        }
        consumeNode(c);
        switch (node) {
            case ExprNode.LitBool b -> { }
            case ExprNode.LitInt i -> { }
            case ExprNode.LitNull n -> { }
            case ExprNode.LitDouble d -> {
                if (!Double.isFinite(d.value())) {
                    reject("non-finite LIT_DOUBLE is not a canonical literal (Appendix B §B.5 item 5)");
                }
            }
            case ExprNode.LitString s -> checkScalarBytes(s.value().getBytes(StandardCharsets.UTF_8).length, "LIT_STRING");
            case ExprNode.LitBytes b -> checkScalarBytes(b.value().length, "LIT_BYTES");
            case ExprNode.ListLit l -> { for (ExprNode e : l.elements()) visitExpr(e, depth + 1, c); }
            case ExprNode.FieldRef f -> visitSteps(f.steps(), c);
            case ExprNode.Has h -> visitFieldRefNode(h.target(), c);
            case ExprNode.Not u -> visitExpr(u.operand(), depth + 1, c);
            case ExprNode.Neg u -> visitExpr(u.operand(), depth + 1, c);
            case ExprNode.And x -> { visitExpr(x.left(), depth + 1, c); visitExpr(x.right(), depth + 1, c); }
            case ExprNode.Or x -> { visitExpr(x.left(), depth + 1, c); visitExpr(x.right(), depth + 1, c); }
            case ExprNode.Eq x -> { visitExpr(x.left(), depth + 1, c); visitExpr(x.right(), depth + 1, c); }
            case ExprNode.Ne x -> { visitExpr(x.left(), depth + 1, c); visitExpr(x.right(), depth + 1, c); }
            case ExprNode.Lt x -> { visitExpr(x.left(), depth + 1, c); visitExpr(x.right(), depth + 1, c); }
            case ExprNode.Le x -> { visitExpr(x.left(), depth + 1, c); visitExpr(x.right(), depth + 1, c); }
            case ExprNode.Gt x -> { visitExpr(x.left(), depth + 1, c); visitExpr(x.right(), depth + 1, c); }
            case ExprNode.Ge x -> { visitExpr(x.left(), depth + 1, c); visitExpr(x.right(), depth + 1, c); }
            case ExprNode.Add x -> { visitExpr(x.left(), depth + 1, c); visitExpr(x.right(), depth + 1, c); }
            case ExprNode.Sub x -> { visitExpr(x.left(), depth + 1, c); visitExpr(x.right(), depth + 1, c); }
            case ExprNode.Mul x -> { visitExpr(x.left(), depth + 1, c); visitExpr(x.right(), depth + 1, c); }
            case ExprNode.Div x -> { visitExpr(x.left(), depth + 1, c); visitExpr(x.right(), depth + 1, c); }
            case ExprNode.Mod x -> { visitExpr(x.left(), depth + 1, c); visitExpr(x.right(), depth + 1, c); }
            case ExprNode.In in -> {
                visitExpr(in.needle(), depth + 1, c);
                switch (in.listOperand()) {
                    case InListOperand.LiteralList ll -> {
                        // The literalList ListLit wrapper is its own node (decoder counts it in
                        // decodeLiteralListOperand) but adds no depth level of its own.
                        consumeNode(c);
                        for (ExprNode e : ll.list().elements()) visitExpr(e, depth + 1, c);
                    }
                    case InListOperand.FieldList fl -> visitFieldRefNode(fl.field(), c);
                }
            }
            case ExprNode.Cond cnd -> {
                visitExpr(cnd.condition(), depth + 1, c);
                visitExpr(cnd.thenBranch(), depth + 1, c);
                visitExpr(cnd.elseBranch(), depth + 1, c);
            }
            case ExprNode.Call call -> {
                checkCall(call);
                for (ExprNode a : call.arguments()) visitExpr(a, depth + 1, c);
            }
        }
    }

    /** A {@code FieldRef} reached NOT via the {@code fieldRef[7]} arm (Has.target, In.fieldList): the node itself is counted here, adds no depth. */
    private static void visitFieldRefNode(ExprNode.FieldRef fr, Counter c) {
        consumeNode(c);
        visitSteps(fr.steps(), c);
    }

    private static void visitSteps(List<SelectorStep> steps, Counter c) {
        if (steps.size() > CelCeilings.MAX_SELECTOR_STEPS) {
            reject("maxSelectorSteps (" + CelCeilings.MAX_SELECTOR_STEPS + ") exceeded");
        }
        for (SelectorStep step : steps) {
            consumeNode(c);
            switch (step) {
                case SelectorStep.Unqual u -> checkName(u.name(), 1, CelCeilings.MAX_UNQUAL_NAME_BYTES, "SelectorStep.unqual");
                case SelectorStep.Qual q -> {
                    checkName(q.className(), 1, CelCeilings.MAX_CLASS_NAME_BYTES, "QualifiedSelector.className");
                    checkName(q.fieldName(), 1, CelCeilings.MAX_UNQUAL_NAME_BYTES, "QualifiedSelector.fieldName");
                }
            }
        }
    }

    private static void checkName(String s, int min, int max, String what) {
        int n = s.getBytes(StandardCharsets.UTF_8).length;
        if (n < min) reject(what + ": empty name (min " + min + " byte)");
        if (n > max) reject(what + ": length " + n + " exceeds ceiling " + max);
    }

    private static void checkScalarBytes(int len, String what) {
        if (len > CelCeilings.MAX_SCALAR_BYTES) {
            reject(what + ": " + len + " content bytes exceed maxScalarBytes (" + CelCeilings.MAX_SCALAR_BYTES + ")");
        }
    }

    private static void checkCall(ExprNode.Call call) {
        FunctionRegistry.Entry entry = FunctionRegistry.byId(call.functionId());
        if (entry == null) {
            reject("CALL.functionId " + call.functionId() + " is not an assigned registry id (Appendix B §B.8.3)");
        }
        if (call.arguments().size() != entry.arity()) {
            reject("arity mismatch for function id " + call.functionId() + " (" + entry.name()
                    + "): expected " + entry.arity() + ", got " + call.arguments().size());
        }
        if (call.functionId() == FunctionRegistry.CONTAINS_ID
                && !(call.arguments().get(1) instanceof ExprNode.LitString)) {
            reject("contains(): needle (arg 2) must be a string literal (STD-011 §5.3.1 item 4)");
        }
    }

    // =====================================================================
    // Authoring-layer well-formedness (NOT decoder-enforced) -- parser only
    // =====================================================================

    static String findAuthoringViolation(ExprNode root) {
        try {
            authoringWalk(root);
            return null;
        } catch (Violation v) {
            return v.getMessage();
        }
    }

    private static void authoringWalk(ExprNode node) {
        switch (node) {
            case ExprNode.ListLit l -> {
                checkHomogeneous(l.elements());
                for (ExprNode e : l.elements()) authoringWalk(e);
            }
            case ExprNode.In in -> {
                authoringWalk(in.needle());
                switch (in.listOperand()) {
                    case InListOperand.LiteralList ll -> {
                        checkHomogeneous(ll.list().elements());
                        for (ExprNode e : ll.list().elements()) authoringWalk(e);
                    }
                    case InListOperand.FieldList fl -> { /* FieldRef: no scalar elements to check */ }
                }
            }
            case ExprNode.Add x -> { checkNumericConcat(x.left(), x.right(), "+"); authoringWalk(x.left()); authoringWalk(x.right()); }
            case ExprNode.Sub x -> { checkNumericConcat(x.left(), x.right(), "-"); authoringWalk(x.left()); authoringWalk(x.right()); }
            case ExprNode.Mul x -> { checkNumericConcat(x.left(), x.right(), "*"); authoringWalk(x.left()); authoringWalk(x.right()); }
            case ExprNode.Div x -> { checkNumericConcat(x.left(), x.right(), "/"); authoringWalk(x.left()); authoringWalk(x.right()); }
            case ExprNode.Mod x -> { checkNumericConcat(x.left(), x.right(), "%"); authoringWalk(x.left()); authoringWalk(x.right()); }
            case ExprNode.Not u -> authoringWalk(u.operand());
            case ExprNode.Neg u -> authoringWalk(u.operand());
            case ExprNode.And x -> { authoringWalk(x.left()); authoringWalk(x.right()); }
            case ExprNode.Or x -> { authoringWalk(x.left()); authoringWalk(x.right()); }
            case ExprNode.Eq x -> { authoringWalk(x.left()); authoringWalk(x.right()); }
            case ExprNode.Ne x -> { authoringWalk(x.left()); authoringWalk(x.right()); }
            case ExprNode.Lt x -> { authoringWalk(x.left()); authoringWalk(x.right()); }
            case ExprNode.Le x -> { authoringWalk(x.left()); authoringWalk(x.right()); }
            case ExprNode.Gt x -> { authoringWalk(x.left()); authoringWalk(x.right()); }
            case ExprNode.Ge x -> { authoringWalk(x.left()); authoringWalk(x.right()); }
            case ExprNode.Cond cnd -> { authoringWalk(cnd.condition()); authoringWalk(cnd.thenBranch()); authoringWalk(cnd.elseBranch()); }
            case ExprNode.Call call -> { for (ExprNode a : call.arguments()) authoringWalk(a); }
            case ExprNode.Has h -> { /* FieldRef target: no scalar elements */ }
            default -> { /* leaves and FieldRef: nothing */ }
        }
    }

    /** The definite half of STD-011 §5.3.2 homogeneity: reject a definitely non-scalar element, or two elements whose known scalar types differ. UNKNOWN (field-typed) defers to T6/eval (Appendix B §B.6.6). */
    private static void checkHomogeneous(List<ExprNode> elements) {
        CelStaticType known = null;
        for (ExprNode e : elements) {
            CelStaticType t = CelStaticType.of(e);
            if (t == CelStaticType.NULL) reject("list literal element is null (§5.3.2: null not admissible)");
            if (t == CelStaticType.LIST) reject("list literal element is a nested list (§5.3.2: nested lists not admissible)");
            if (t == CelStaticType.OBJECT) reject("list literal element is a non-scalar object (§5.3.2)");
            if (t == CelStaticType.UNKNOWN) continue;
            if (known == null) known = t;
            else if (known != t) reject("list literal is not homogeneous: mixes " + known + " and " + t + " (§5.3.2)");
        }
    }

    /** §5.4 item 6: {@code +} (and the other arithmetic operators) are numeric-only; reject a statically non-numeric operand (e.g. string/bytes/list concatenation). */
    private static void checkNumericConcat(ExprNode l, ExprNode r, String op) {
        rejectIfNonNumeric(l, op);
        rejectIfNonNumeric(r, op);
    }

    private static void rejectIfNonNumeric(ExprNode e, String op) {
        CelStaticType t = CelStaticType.of(e);
        switch (t) {
            case STRING, BYTES, LIST ->
                    reject("'" + op + "' is numeric-only; string/bytes/list concatenation is excluded (§5.4 item 6)");
            case BOOL, NULL, OBJECT ->
                    reject("'" + op + "' requires numeric operands; got a " + t + " operand (§6.3)");
            default -> { /* INT / DOUBLE / UNKNOWN: allowed or deferred to eval */ }
        }
    }
}
