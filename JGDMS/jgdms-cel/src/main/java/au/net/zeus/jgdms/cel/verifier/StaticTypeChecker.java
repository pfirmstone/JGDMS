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

package au.net.zeus.jgdms.cel.verifier;

import au.net.zeus.jgdms.cel.CelType;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.ast.ExprNode.InListOperand;
import au.net.zeus.jgdms.cel.ast.ExprNode.SelectorStep;
import au.net.zeus.jgdms.cel.wire.DeclaredResultType;
import au.net.zeus.jgdms.cel.wire.EvaluationContext;
import au.net.zeus.jgdms.cel.wire.FunctionRegistry;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * STD-011 §11.2/§12.4's schema-optional, bottom-up static type-inference
 * pass over an already-decoded {@code ExprNode} tree -- the engine behind
 * {@link CelVerifier}'s items (b) (schema-dependent static type checking),
 * (c) (declared-result-type consistency), and the list-literal-homogeneity
 * obligation Appendix B §B.6.6 assigns to T6.
 * <p>
 * Every node's type is either a definite {@link CelType} (fully determined
 * by the closed grammar alone, or resolved through a {@link SchemaView}
 * when one is supplied) or {@link Inferred#UNKNOWN} -- never a guess. An
 * operand whose type is {@code UNKNOWN} never by itself triggers a
 * rejection: this checker only rejects a construct it can positively prove
 * is ill-typed from types it actually knows, exactly the soundness
 * discipline {@link SchemaView}'s javadoc documents (STD-011 §12.4's "never
 * license[s] skipping the dynamic check" cuts the other way here -- this
 * checker never rejects what it cannot prove is wrong, so the dynamic layer
 * remains load-bearing for every deferred case).
 * <p>
 * Package-private: reached through {@link CelVerifier} (full registration-time
 * verification) and {@link CelTypeInference} (the public single-node type-query
 * facade, e.g. for authoring-time overload resolution) -- both in this package.
 */
final class StaticTypeChecker {

    private StaticTypeChecker() {}

    /** Thrown for any operator/function/field-reference/list-literal construct this checker can positively prove is ill-typed or ambiguous. */
    static final class Rejected extends Exception {
        private static final long serialVersionUID = 1L;
        Rejected(String message) { super(message); }
    }

    /**
     * A statically inferred type: the {@link CelType}, plus -- only
     * meaningful when {@code type() == CelType.LIST} -- an optionally-known
     * element type. {@link #UNKNOWN} means "not statically determinable
     * here"; {@link #isUnknown()} is the only way to observe that, callers
     * MUST NOT inspect {@link #type()} on an unknown instance.
     */
    record Inferred(CelType type, CelType listElementType) {
        static final Inferred UNKNOWN = new Inferred(null, null);

        static Inferred of(CelType t) {
            if (t == null) throw new NullPointerException("t");
            return new Inferred(t, null);
        }

        /** @param elementTypeOrNull the list's known element type, or {@code null} if not statically known */
        static Inferred list(CelType elementTypeOrNull) {
            return new Inferred(CelType.LIST, elementTypeOrNull);
        }

        boolean isUnknown() {
            return type == null;
        }
    }

    // ======================================================================
    // Entry points (called by CelVerifier)
    // ======================================================================

    /** Infers the root expression's type, throwing {@link Rejected} for any provable ill-typed construct anywhere in the tree. {@code schema} may be {@code null} (no governing schema -- every field reference defers to unknown). */
    static Inferred infer(ExprNode root, SchemaView schema) throws Rejected {
        return inferNode(root, schema);
    }

    /**
     * Checks the expression's declared {@link EvaluationContext} against its
     * already-inferred root type (STD-011 §9.4/§9.5). Returns a diagnostic
     * message on a provable inconsistency, or {@code null} if consistent --
     * which includes the "can't tell" (root type unknown) case, deliberately:
     * an unknown root type is never a static rejection.
     */
    static String checkResultTypeConsistency(EvaluationContext ctx, Inferred root) {
        if (ctx instanceof EvaluationContext.Predicate) {
            if (!root.isUnknown() && root.type() != CelType.BOOL) {
                return "predicate root expression must have type bool, statically inferred " + root.type();
            }
            return null;
        }
        if (ctx instanceof EvaluationContext.Transform t) {
            return checkDeclaredResultType(t.resultType(), root);
        }
        throw new IllegalStateException("unreachable EvaluationContext kind: " + ctx.getClass());
    }

    private static String checkDeclaredResultType(DeclaredResultType drt, Inferred root) {
        if (drt instanceof DeclaredResultType.Scalar s) {
            CelType declared = s.type().toCelType();
            if (!root.isUnknown() && root.type() != declared) {
                return "transform declares result type " + declared
                        + " but the root expression's statically inferred type is " + root.type();
            }
            return null;
        }
        if (drt instanceof DeclaredResultType.NullType) {
            if (!root.isUnknown() && root.type() != CelType.NULL_T) {
                return "transform declares result type null_t but the root expression's statically inferred type is "
                        + root.type();
            }
            return null;
        }
        if (drt instanceof DeclaredResultType.ListType lt) {
            if (!root.isUnknown() && root.type() != CelType.LIST) {
                return "transform declares a list result type but the root expression's statically inferred type is "
                        + root.type();
            }
            if (!root.isUnknown() && root.listElementType() != null
                    && root.listElementType() != lt.elementType().toCelType()) {
                return "transform declares list<" + lt.elementType().toCelType()
                        + "> but the root expression's statically inferred element type is " + root.listElementType();
            }
            return null;
        }
        if (drt instanceof DeclaredResultType.ObjectType) {
            if (!root.isUnknown() && root.type() != CelType.OBJECT) {
                return "transform declares result type object but the root expression's statically inferred type is "
                        + root.type();
            }
            return null;
        }
        throw new IllegalStateException("unreachable DeclaredResultType kind: " + drt.getClass());
    }

    // ======================================================================
    // Bottom-up node typing (STD-011 §11.1's Typing rule column / §6)
    // ======================================================================

    private static Inferred inferNode(ExprNode node, SchemaView schema) throws Rejected {
        if (node instanceof ExprNode.LitBool) return Inferred.of(CelType.BOOL);
        if (node instanceof ExprNode.LitInt) return Inferred.of(CelType.INT);
        if (node instanceof ExprNode.LitDouble) return Inferred.of(CelType.DOUBLE);
        if (node instanceof ExprNode.LitString) return Inferred.of(CelType.STRING);
        if (node instanceof ExprNode.LitBytes) return Inferred.of(CelType.BYTES);
        if (node instanceof ExprNode.LitNull) return Inferred.of(CelType.NULL_T);
        if (node instanceof ExprNode.ListLit l) return inferListLit(l, schema);
        if (node instanceof ExprNode.FieldRef f) return inferFieldRef(f, schema);
        if (node instanceof ExprNode.Has h) {
            // Resolved for ambiguity/non-object-intermediate side effects (§8.2/§8.4); has() is always bool regardless of the leaf's own type.
            inferFieldRef(h.target(), schema);
            return Inferred.of(CelType.BOOL);
        }
        if (node instanceof ExprNode.Not n) {
            Inferred operand = inferNode(n.operand(), schema);
            if (!operand.isUnknown() && operand.type() != CelType.BOOL) {
                throw new Rejected("'!' requires a bool operand, statically inferred " + operand.type());
            }
            return Inferred.of(CelType.BOOL);
        }
        if (node instanceof ExprNode.Neg n) {
            Inferred operand = inferNode(n.operand(), schema);
            if (!operand.isUnknown() && operand.type() != CelType.INT && operand.type() != CelType.DOUBLE) {
                throw new Rejected("unary '-' requires an int or double operand, statically inferred " + operand.type());
            }
            return operand;
        }
        if (node instanceof ExprNode.And b) return inferBoolBinary(b.left(), b.right(), schema, "&&");
        if (node instanceof ExprNode.Or b) return inferBoolBinary(b.left(), b.right(), schema, "||");
        if (node instanceof ExprNode.Eq b) return inferEqNe(b.left(), b.right(), schema, "==");
        if (node instanceof ExprNode.Ne b) return inferEqNe(b.left(), b.right(), schema, "!=");
        if (node instanceof ExprNode.Lt b) return inferOrdering(b.left(), b.right(), schema, "<");
        if (node instanceof ExprNode.Le b) return inferOrdering(b.left(), b.right(), schema, "<=");
        if (node instanceof ExprNode.Gt b) return inferOrdering(b.left(), b.right(), schema, ">");
        if (node instanceof ExprNode.Ge b) return inferOrdering(b.left(), b.right(), schema, ">=");
        if (node instanceof ExprNode.Add b) return inferArith(b.left(), b.right(), schema, "+");
        if (node instanceof ExprNode.Sub b) return inferArith(b.left(), b.right(), schema, "-");
        if (node instanceof ExprNode.Mul b) return inferArith(b.left(), b.right(), schema, "*");
        if (node instanceof ExprNode.Div b) return inferArith(b.left(), b.right(), schema, "/");
        if (node instanceof ExprNode.Mod b) return inferMod(b.left(), b.right(), schema);
        if (node instanceof ExprNode.In in) return inferIn(in, schema);
        if (node instanceof ExprNode.Cond c) return inferCond(c, schema);
        if (node instanceof ExprNode.Call call) return inferCall(call, schema);
        throw new IllegalStateException("unreachable ExprNode kind: " + node.getClass());
    }

    private static Inferred inferBoolBinary(ExprNode left, ExprNode right, SchemaView schema, String op) throws Rejected {
        Inferred l = inferNode(left, schema);
        Inferred r = inferNode(right, schema);
        if (!l.isUnknown() && l.type() != CelType.BOOL) {
            throw new Rejected("'" + op + "' left operand must be bool, statically inferred " + l.type());
        }
        if (!r.isUnknown() && r.type() != CelType.BOOL) {
            throw new Rejected("'" + op + "' right operand must be bool, statically inferred " + r.type());
        }
        return Inferred.of(CelType.BOOL);
    }

    /** STD-011 §6.1: same scalar type, the int/double cross-pair, or either operand null_t. */
    private static boolean isEqNeCompatible(CelType a, CelType b) {
        if (a == CelType.NULL_T || b == CelType.NULL_T) return true;
        if (a == b) {
            return a == CelType.BOOL || a == CelType.STRING || a == CelType.BYTES
                    || a == CelType.INT || a == CelType.DOUBLE;
        }
        return (a == CelType.INT && b == CelType.DOUBLE) || (a == CelType.DOUBLE && b == CelType.INT);
    }

    private static Inferred inferEqNe(ExprNode left, ExprNode right, SchemaView schema, String op) throws Rejected {
        Inferred l = inferNode(left, schema);
        Inferred r = inferNode(right, schema);
        if (!l.isUnknown() && !r.isUnknown() && !isEqNeCompatible(l.type(), r.type())) {
            throw new Rejected("'" + op + "' is not defined between " + l.type() + " and " + r.type());
        }
        return Inferred.of(CelType.BOOL);
    }

    /** STD-011 §6.2: int/int, double/double, the int/double cross-pair, string/string, bytes/bytes. */
    private static boolean isOrderingCompatible(CelType a, CelType b) {
        if (a == b) return a == CelType.INT || a == CelType.DOUBLE || a == CelType.STRING || a == CelType.BYTES;
        return (a == CelType.INT && b == CelType.DOUBLE) || (a == CelType.DOUBLE && b == CelType.INT);
    }

    private static Inferred inferOrdering(ExprNode left, ExprNode right, SchemaView schema, String op) throws Rejected {
        Inferred l = inferNode(left, schema);
        Inferred r = inferNode(right, schema);
        if (!l.isUnknown() && !r.isUnknown() && !isOrderingCompatible(l.type(), r.type())) {
            throw new Rejected("'" + op + "' is not defined between " + l.type() + " and " + r.type());
        }
        return Inferred.of(CelType.BOOL);
    }

    /** STD-011 §6.3: int x int -> int, or double x double -> double; no mixed-type arithmetic. */
    private static Inferred inferArith(ExprNode left, ExprNode right, SchemaView schema, String op) throws Rejected {
        Inferred l = inferNode(left, schema);
        Inferred r = inferNode(right, schema);
        if (l.isUnknown() || r.isUnknown()) return Inferred.UNKNOWN;
        if (l.type() == CelType.INT && r.type() == CelType.INT) return Inferred.of(CelType.INT);
        if (l.type() == CelType.DOUBLE && r.type() == CelType.DOUBLE) return Inferred.of(CelType.DOUBLE);
        throw new Rejected("'" + op + "' requires int x int or double x double, statically inferred "
                + l.type() + " and " + r.type());
    }

    /** STD-011 §11.1: MOD is int x int -> int only -- unlike ADD/SUB/MUL/DIV, double x double is also a mismatch. */
    private static Inferred inferMod(ExprNode left, ExprNode right, SchemaView schema) throws Rejected {
        Inferred l = inferNode(left, schema);
        Inferred r = inferNode(right, schema);
        if (l.isUnknown() || r.isUnknown()) return Inferred.UNKNOWN;
        if (l.type() == CelType.INT && r.type() == CelType.INT) return Inferred.of(CelType.INT);
        throw new Rejected("'%' requires int x int only, statically inferred " + l.type() + " and " + r.type());
    }

    /**
     * STD-011 §5.3.2 / Appendix B §B.6.6: list-literal element homogeneity
     * and the scalar-only element-type restriction, checked here because T2
     * deliberately does not (§B.6.6). Elements whose type is unknown are
     * skipped for the pairwise check (defer), never treated as an implicit
     * wildcard match.
     */
    private static Inferred inferListLit(ExprNode.ListLit l, SchemaView schema) throws Rejected {
        CelType common = null;
        boolean commonKnown = false;
        for (ExprNode element : l.elements()) {
            Inferred e = inferNode(element, schema);
            if (e.isUnknown()) continue;
            if (e.type() == CelType.NULL_T || e.type() == CelType.LIST || e.type() == CelType.OBJECT) {
                throw new Rejected("list literal elements must be scalar (bool/int/double/string/bytes), "
                        + "statically inferred " + e.type());
            }
            if (!commonKnown) {
                common = e.type();
                commonKnown = true;
            } else if (common != e.type()) {
                throw new Rejected("list literal is not homogeneous: found both " + common + " and " + e.type());
            }
        }
        return Inferred.list(commonKnown ? common : null);
    }

    /**
     * STD-011 §8.2 (unqualified uniqueness-or-error), §8.3 (qualified), and
     * §8.4 (nested access) applied statically against {@code schema}. Any
     * step that cannot be resolved against the current {@link SchemaView} --
     * absent field, unresolved type, or an unresolvable nested schema --
     * defers the remainder of the chain to {@link Inferred#UNKNOWN} rather
     * than rejecting; only a genuine ambiguity, or a provable non-object
     * intermediate, is a hard reject.
     */
    private static Inferred inferFieldRef(ExprNode.FieldRef ref, SchemaView schema) throws Rejected {
        if (schema == null) return Inferred.UNKNOWN;
        SchemaView current = schema;
        List<SelectorStep> steps = ref.steps();
        for (int i = 0; i < steps.size(); i++) {
            SelectorStep step = steps.get(i);
            boolean last = (i == steps.size() - 1);
            String className;
            String fieldName;
            if (step instanceof SelectorStep.Unqual u) {
                String matched = null;
                for (String candidate : current.namespaceChain()) {
                    if (current.declaresField(candidate, u.name())) {
                        if (matched != null) {
                            throw new Rejected("ambiguous field reference '" + u.name()
                                    + "': declared by more than one class in the namespace chain (STD-011 §8.2)");
                        }
                        matched = candidate;
                    }
                }
                if (matched == null) return Inferred.UNKNOWN; // absent from this schema snapshot -- defer, schema may evolve
                className = matched;
                fieldName = u.name();
            } else if (step instanceof SelectorStep.Qual q) {
                if (!current.namespaceChain().contains(q.className()) || !current.declaresField(q.className(), q.fieldName())) {
                    return Inferred.UNKNOWN; // not present in this schema snapshot -- defer
                }
                className = q.className();
                fieldName = q.fieldName();
            } else {
                throw new IllegalStateException("unreachable SelectorStep kind: " + step.getClass());
            }

            Optional<CelType> fieldType = current.fieldType(className, fieldName);
            if (last) {
                return fieldType.map(Inferred::of).orElse(Inferred.UNKNOWN);
            }
            if (fieldType.isEmpty()) return Inferred.UNKNOWN; // can't confirm it's an object -- defer the rest
            if (fieldType.get() != CelType.OBJECT) {
                throw new Rejected("selector step '" + fieldName + "' is statically typed " + fieldType.get()
                        + ", not object, but the selector chain continues past it");
            }
            Optional<SchemaView> nested = current.nestedSchema(className, fieldName);
            if (nested.isEmpty()) return Inferred.UNKNOWN; // concrete nested schema not statically known -- defer the rest
            current = nested.get();
        }
        throw new IllegalStateException("unreachable: FieldRef.steps is non-empty by construction");
    }

    /** STD-011 §6.5: the list operand must be list<T>; the needle must be §6.1-comparable to T when both are known. */
    private static Inferred inferIn(ExprNode.In in, SchemaView schema) throws Rejected {
        Inferred needle = inferNode(in.needle(), schema);
        Inferred listType;
        if (in.listOperand() instanceof InListOperand.LiteralList ll) {
            listType = inferListLit(ll.list(), schema);
        } else if (in.listOperand() instanceof InListOperand.FieldList fl) {
            listType = inferFieldRef(fl.field(), schema);
            if (!listType.isUnknown() && listType.type() != CelType.LIST) {
                throw new Rejected("'in' right operand must be list<T>, statically inferred " + listType.type());
            }
        } else {
            throw new IllegalStateException("unreachable InListOperand kind: " + in.listOperand().getClass());
        }
        if (!needle.isUnknown() && !listType.isUnknown() && listType.listElementType() != null
                && !isEqNeCompatible(needle.type(), listType.listElementType())) {
            throw new Rejected("'in' needle type " + needle.type() + " is not comparable to list element type "
                    + listType.listElementType());
        }
        return Inferred.of(CelType.BOOL);
    }

    /** STD-011 §6.7: condition must be bool; branches unified by type equality (no coercion). */
    private static Inferred inferCond(ExprNode.Cond c, SchemaView schema) throws Rejected {
        Inferred cond = inferNode(c.condition(), schema);
        if (!cond.isUnknown() && cond.type() != CelType.BOOL) {
            throw new Rejected("'?:' condition must be bool, statically inferred " + cond.type());
        }
        Inferred then = inferNode(c.thenBranch(), schema);
        Inferred els = inferNode(c.elseBranch(), schema);
        if (then.isUnknown() || els.isUnknown()) return Inferred.UNKNOWN;
        if (then.type() != els.type()) {
            throw new Rejected("'?:' branches must have the same type, statically inferred "
                    + then.type() + " and " + els.type());
        }
        if (then.type() == CelType.LIST) {
            if (then.listElementType() != null && els.listElementType() != null
                    && then.listElementType() != els.listElementType()) {
                throw new Rejected("'?:' branches must have the same list element type, statically inferred list<"
                        + then.listElementType() + "> and list<" + els.listElementType() + ">");
            }
            CelType elem = then.listElementType() != null ? then.listElementType() : els.listElementType();
            return Inferred.list(elem);
        }
        return then;
    }

    // ======================================================================
    // Function signatures (Appendix B §B.8.3's pinned table)
    // ======================================================================

    private record Signature(List<CelType> argTypes, CelType returnType) {}

    private static final Map<FunctionRegistry.Name, Signature> SIGNATURES = buildSignatures();

    private static Map<FunctionRegistry.Name, Signature> buildSignatures() {
        Map<FunctionRegistry.Name, Signature> m = new EnumMap<>(FunctionRegistry.Name.class);
        m.put(FunctionRegistry.Name.SIZE_STRING, new Signature(List.of(CelType.STRING), CelType.INT));
        m.put(FunctionRegistry.Name.SIZE_BYTES, new Signature(List.of(CelType.BYTES), CelType.INT));
        m.put(FunctionRegistry.Name.SIZE_LIST, new Signature(List.of(CelType.LIST), CelType.INT));
        m.put(FunctionRegistry.Name.INT_OF_DOUBLE, new Signature(List.of(CelType.DOUBLE), CelType.INT));
        m.put(FunctionRegistry.Name.DOUBLE_OF_INT, new Signature(List.of(CelType.INT), CelType.DOUBLE));
        m.put(FunctionRegistry.Name.ABS_INT, new Signature(List.of(CelType.INT), CelType.INT));
        m.put(FunctionRegistry.Name.ABS_DOUBLE, new Signature(List.of(CelType.DOUBLE), CelType.DOUBLE));
        m.put(FunctionRegistry.Name.MIN_INT, new Signature(List.of(CelType.INT, CelType.INT), CelType.INT));
        m.put(FunctionRegistry.Name.MIN_DOUBLE, new Signature(List.of(CelType.DOUBLE, CelType.DOUBLE), CelType.DOUBLE));
        m.put(FunctionRegistry.Name.MAX_INT, new Signature(List.of(CelType.INT, CelType.INT), CelType.INT));
        m.put(FunctionRegistry.Name.MAX_DOUBLE, new Signature(List.of(CelType.DOUBLE, CelType.DOUBLE), CelType.DOUBLE));
        m.put(FunctionRegistry.Name.SQRT, new Signature(List.of(CelType.DOUBLE), CelType.DOUBLE));
        m.put(FunctionRegistry.Name.RADIANS, new Signature(List.of(CelType.DOUBLE), CelType.DOUBLE));
        m.put(FunctionRegistry.Name.DEGREES, new Signature(List.of(CelType.DOUBLE), CelType.DOUBLE));
        m.put(FunctionRegistry.Name.SIN, new Signature(List.of(CelType.DOUBLE), CelType.DOUBLE));
        m.put(FunctionRegistry.Name.COS, new Signature(List.of(CelType.DOUBLE), CelType.DOUBLE));
        m.put(FunctionRegistry.Name.TAN, new Signature(List.of(CelType.DOUBLE), CelType.DOUBLE));
        m.put(FunctionRegistry.Name.ASIN, new Signature(List.of(CelType.DOUBLE), CelType.DOUBLE));
        m.put(FunctionRegistry.Name.ACOS, new Signature(List.of(CelType.DOUBLE), CelType.DOUBLE));
        m.put(FunctionRegistry.Name.ATAN, new Signature(List.of(CelType.DOUBLE), CelType.DOUBLE));
        m.put(FunctionRegistry.Name.ATAN2, new Signature(List.of(CelType.DOUBLE, CelType.DOUBLE), CelType.DOUBLE));
        m.put(FunctionRegistry.Name.CONTAINS, new Signature(List.of(CelType.STRING, CelType.STRING), CelType.BOOL));
        m.put(FunctionRegistry.Name.STARTS_WITH, new Signature(List.of(CelType.STRING, CelType.STRING), CelType.BOOL));
        m.put(FunctionRegistry.Name.ENDS_WITH, new Signature(List.of(CelType.STRING, CelType.STRING), CelType.BOOL));
        return Map.copyOf(m);
    }

    private static Inferred inferCall(ExprNode.Call call, SchemaView schema) throws Rejected {
        FunctionRegistry.Entry entry = FunctionRegistry.byId(call.functionId());
        if (entry == null) {
            // Decoder already rejects unregistered ids; defensive fallback only (same posture as CostModel.callCost).
            throw new IllegalStateException("static type checker given an unregistered functionId " + call.functionId());
        }
        Signature sig = SIGNATURES.get(entry.name());
        List<ExprNode> args = call.arguments();
        for (int i = 0; i < args.size() && i < sig.argTypes().size(); i++) {
            Inferred argType = inferNode(args.get(i), schema);
            CelType expected = sig.argTypes().get(i);
            if (!argType.isUnknown() && argType.type() != expected) {
                throw new Rejected(entry.name() + "() argument " + (i + 1) + " must be " + expected
                        + ", statically inferred " + argType.type());
            }
        }
        return Inferred.of(sig.returnType());
    }
}
