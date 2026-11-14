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

package au.net.zeus.jgdms.cel.eval;

import au.net.zeus.jgdms.cel.CelValue;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.ast.ExprNode.SelectorStep;
import au.net.zeus.jgdms.cel.eval.CompiledNodes.Node;
import au.net.zeus.jgdms.cel.math.MathProvider;
import au.net.zeus.jgdms.cel.wire.FunctionRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Turns an AST into the {@link CompiledNodes} of a {@link CompiledExpression}, merging structurally
 * equal sub-expressions so each is computed once per record (design section 3.5).
 * <p>
 * Two passes decide what is merged, and the order matters:
 * <ol>
 *   <li>An {@link IdentityHashMap} from AST object to canonical node, so an AST built by Java reference
 *       sharing is visited once per distinct object. {@link ExprNode#hashCode()} and {@code equals} are
 *       structural record methods: called on an inner node of a shared AST each call walks the whole
 *       expanded tree (1.2M nodes for the E2 benchmark expression), so they are NEVER called on a node.
 *       The only structural hashing is on the {@link Key}, whose children are already canonical ids.</li>
 *   <li>A {@link Key} map from (kind, canonical child ids, payload) to canonical node. A key is compared
 *       by full equality, never by hash alone.</li>
 * </ol>
 * A canonical node with two or more IN-EDGES (not distinct parents: {@code x + x} is two) gets a
 * {@link CompiledNodes.Memo}. With {@code share} false nothing is merged or memoised beyond reuse of one
 * Node for one AST object, which evaluates exactly as the walker does, occurrence by occurrence.
 */
final class CompiledGraph {

    /** The distinguishing data of a node that its children do not already carry. */
    private record Key(Class<? extends ExprNode> kind, List<Integer> children, Object payload) {}

    private static final class Canon {
        final int id;
        final ExprNode ast; // the first node seen with this key: only its payload is read
        final Canon[] children;
        int inEdges;
        Node node;

        Canon(int id, ExprNode ast, Canon[] children) {
            this.id = id;
            this.ast = ast;
            this.children = children;
        }
    }

    private static final Canon[] NO_CHILDREN = new Canon[0];

    private final MathProvider math;
    private final boolean share;

    /**
     * Test hook, null in production: given a canonical node's AST and the executable node just made for
     * it, returns the node to use. It runs INSIDE the {@link CompiledNodes.Memo} wrapper, so a memo hit
     * does not reach it, and after the decision to memoise, so a wrapper cannot change what is merged.
     * It exists so the differential census can count the rules that ran, not only the kinds generated.
     */
    private final BiFunction<ExprNode, Node, Node> observer;
    private final IdentityHashMap<ExprNode, Canon> byIdentity = new IdentityHashMap<>();
    private final HashMap<Key, Canon> byKey = new HashMap<>();
    private int nextId;
    private int memoSlots;
    private int sizeStringNodes;

    private final Node root;

    private CompiledGraph(ExprNode root, MathProvider math, boolean share, BiFunction<ExprNode, Node, Node> observer) {
        this.math = math;
        this.share = share;
        this.observer = observer;
        this.root = nodeFor(intern(root));
    }

    static CompiledGraph build(ExprNode root, MathProvider math, boolean share, BiFunction<ExprNode, Node, Node> observer) {
        return new CompiledGraph(root, math, share, observer);
    }

    Node root() {
        return root;
    }

    int memoSlots() {
        return memoSlots;
    }

    int sizeStringNodes() {
        return sizeStringNodes;
    }

    /** The number of distinct nodes after merging (equal to the number of distinct AST objects when {@code share} is false). */
    int canonicalNodes() {
        return nextId;
    }

    // ======================================================================
    // Passes 1 and 2: canonical nodes
    // ======================================================================

    private Canon intern(ExprNode n) {
        Canon seen = byIdentity.get(n);
        if (seen != null) return seen;
        Canon[] children = internChildren(n);
        Canon canon = null;
        Key key = null;
        if (share) {
            key = keyOf(n, children);
            canon = byKey.get(key);
        }
        if (canon == null) {
            canon = new Canon(nextId++, n, children);
            if (share) {
                for (Canon child : children) child.inEdges++;
                byKey.put(key, canon);
            }
        }
        byIdentity.put(n, canon);
        return canon;
    }

    private Canon[] internAll(List<ExprNode> nodes) {
        Canon[] out = new Canon[nodes.size()];
        for (int i = 0; i < out.length; i++) out[i] = intern(nodes.get(i));
        return out;
    }

    /**
     * The evaluation-ordered children of {@code n}. The list under {@code in} contributes its ELEMENTS, not a
     * ListLit node, so it can never merge with an expression list literal: the two have different
     * evaluation rules (an expression ListLit enforces §5.3.2 homogeneity, the {@code in} list does not).
     * A field list is a child because the walker resolves it as a value, which is sound to share.
     */
    private Canon[] internChildren(ExprNode n) {
        return switch (n) {
            case ExprNode.LitBool x -> NO_CHILDREN;
            case ExprNode.LitInt x -> NO_CHILDREN;
            case ExprNode.LitDouble x -> NO_CHILDREN;
            case ExprNode.LitString x -> NO_CHILDREN;
            case ExprNode.LitBytes x -> NO_CHILDREN;
            case ExprNode.LitNull x -> NO_CHILDREN;
            case ExprNode.FieldRef x -> NO_CHILDREN;
            case ExprNode.Has x -> NO_CHILDREN;
            case ExprNode.ListLit x -> internAll(x.elements());
            case ExprNode.Not x -> internAll(List.of(x.operand()));
            case ExprNode.Neg x -> internAll(List.of(x.operand()));
            case ExprNode.And x -> internAll(List.of(x.left(), x.right()));
            case ExprNode.Or x -> internAll(List.of(x.left(), x.right()));
            case ExprNode.Eq x -> internAll(List.of(x.left(), x.right()));
            case ExprNode.Ne x -> internAll(List.of(x.left(), x.right()));
            case ExprNode.Lt x -> internAll(List.of(x.left(), x.right()));
            case ExprNode.Le x -> internAll(List.of(x.left(), x.right()));
            case ExprNode.Gt x -> internAll(List.of(x.left(), x.right()));
            case ExprNode.Ge x -> internAll(List.of(x.left(), x.right()));
            case ExprNode.Add x -> internAll(List.of(x.left(), x.right()));
            case ExprNode.Sub x -> internAll(List.of(x.left(), x.right()));
            case ExprNode.Mul x -> internAll(List.of(x.left(), x.right()));
            case ExprNode.Div x -> internAll(List.of(x.left(), x.right()));
            case ExprNode.Mod x -> internAll(List.of(x.left(), x.right()));
            case ExprNode.In x -> {
                List<ExprNode> parts = new ArrayList<>();
                parts.add(x.needle());
                if (x.listOperand() instanceof ExprNode.InListOperand.LiteralList ll) {
                    parts.addAll(ll.list().elements());
                } else {
                    parts.add(((ExprNode.InListOperand.FieldList) x.listOperand()).field());
                }
                yield internAll(parts);
            }
            case ExprNode.Cond x -> internAll(List.of(x.condition(), x.thenBranch(), x.elseBranch()));
            case ExprNode.Call x -> {
                FunctionRegistry.Entry entry = FunctionRegistry.byId(x.functionId());
                if (entry != null && entry.arity() != x.arguments().size()) {
                    // The decoder and the authoring validator enforce arity (CelDecoder.java:420,
                    // CelAstValidator.java:218), so a mismatch means an unverified AST: the walker would
                    // index out of bounds or ignore an argument, and there is nothing to reproduce.
                    throw new IllegalArgumentException(new StringBuilder("function id ")
                            .append(x.functionId()).append(" (").append(entry.name()).append(") takes ")
                            .append(entry.arity()).append(" argument(s), got ").append(x.arguments().size())
                            .append(": compile() requires a verified AST").toString());
                }
                yield internAll(x.arguments());
            }
        };
    }

    private static Key keyOf(ExprNode n, Canon[] children) {
        List<Integer> ids = new ArrayList<>(children.length);
        for (Canon child : children) ids.add(child.id);
        return new Key(n.getClass(), ids, payloadOf(n));
    }

    /**
     * What distinguishes two nodes of one kind with equal children. The literal's KIND is in the key (so
     * {@code LitInt(0x3FF0000000000000)} never merges with {@code LitDouble(1.0)}), a double is its raw bits
     * (so {@code 0.0} and {@code -0.0} stay apart), selector steps are the step list itself (a joined string
     * would merge {@code Qual("a.b","c")} with {@code Qual("a","b.c")}), and the {@code in} operand kind is
     * its own discriminator because a field list and a literal list share their children's shape.
     */
    private static Object payloadOf(ExprNode n) {
        return switch (n) {
            case ExprNode.LitBool x -> x.value();
            case ExprNode.LitInt x -> x.value();
            case ExprNode.LitDouble x -> Double.doubleToRawLongBits(x.value());
            case ExprNode.LitString x -> x.value();
            case ExprNode.LitBytes x -> new CelValue.BytesV(x.value()); // content equality, unlike byte[]
            case ExprNode.FieldRef x -> x.steps();
            case ExprNode.Has x -> x.target().steps();
            case ExprNode.Call x -> x.functionId();
            case ExprNode.In x -> x.listOperand().getClass();
            default -> null;
        };
    }

    // ======================================================================
    // Executable nodes
    // ======================================================================

    private Node nodeFor(Canon c) {
        if (c.node != null) return c.node;
        Node node = create(c);
        boolean memoise = share && c.inEdges >= 2 && !(node instanceof CompiledNodes.Const);
        if (observer != null) node = observer.apply(c.ast, node);
        if (memoise) node = new CompiledNodes.Memo(node, memoSlots++);
        c.node = node;
        return node;
    }

    private Node[] nodes(Canon[] canons, int from) {
        Node[] out = new Node[canons.length - from];
        for (int i = 0; i < out.length; i++) out[i] = nodeFor(canons[from + i]);
        return out;
    }

    private Node create(Canon c) {
        Canon[] k = c.children;
        return switch (c.ast) {
            case ExprNode.LitBool x -> new CompiledNodes.Const(CompiledNodes.BOOL, x.value() ? 1L : 0L, null);
            case ExprNode.LitInt x -> new CompiledNodes.Const(CompiledNodes.INT, x.value(), null);
            case ExprNode.LitDouble x -> {
                double d = x.value();
                long bits = (d != d) ? CelValue.DoubleV.CANONICAL_NAN_BITS : Double.doubleToRawLongBits(d);
                yield new CompiledNodes.Const(CompiledNodes.DOUBLE, bits, null);
            }
            case ExprNode.LitString x -> new CompiledNodes.Const(CompiledNodes.STRING, 0L, new CelValue.StringV(x.value()));
            case ExprNode.LitBytes x -> new CompiledNodes.Const(CompiledNodes.BYTES, 0L, new CelValue.BytesV(x.value()));
            case ExprNode.LitNull x -> new CompiledNodes.Const(CompiledNodes.NULL, 0L, null);
            case ExprNode.ListLit x -> new CompiledNodes.ListLit(nodes(k, 0));
            case ExprNode.FieldRef x -> fieldRef(x.steps());
            case ExprNode.Has x -> has(x.target().steps());
            case ExprNode.Not x -> new CompiledNodes.Not(nodeFor(k[0]));
            case ExprNode.Neg x -> new CompiledNodes.Neg(nodeFor(k[0]));
            case ExprNode.And x -> new CompiledNodes.And(nodeFor(k[0]), nodeFor(k[1]));
            case ExprNode.Or x -> new CompiledNodes.Or(nodeFor(k[0]), nodeFor(k[1]));
            case ExprNode.Eq x -> new CompiledNodes.Equality(nodeFor(k[0]), nodeFor(k[1]), true);
            case ExprNode.Ne x -> new CompiledNodes.Equality(nodeFor(k[0]), nodeFor(k[1]), false);
            case ExprNode.Lt x -> new CompiledNodes.Order(nodeFor(k[0]), nodeFor(k[1]), 0);
            case ExprNode.Le x -> new CompiledNodes.Order(nodeFor(k[0]), nodeFor(k[1]), 1);
            case ExprNode.Gt x -> new CompiledNodes.Order(nodeFor(k[0]), nodeFor(k[1]), 2);
            case ExprNode.Ge x -> new CompiledNodes.Order(nodeFor(k[0]), nodeFor(k[1]), 3);
            case ExprNode.Add x -> new CompiledNodes.Arith(nodeFor(k[0]), nodeFor(k[1]), 0);
            case ExprNode.Sub x -> new CompiledNodes.Arith(nodeFor(k[0]), nodeFor(k[1]), 1);
            case ExprNode.Mul x -> new CompiledNodes.Arith(nodeFor(k[0]), nodeFor(k[1]), 2);
            case ExprNode.Div x -> new CompiledNodes.Arith(nodeFor(k[0]), nodeFor(k[1]), 3);
            case ExprNode.Mod x -> new CompiledNodes.Arith(nodeFor(k[0]), nodeFor(k[1]), 4);
            case ExprNode.In x -> x.listOperand() instanceof ExprNode.InListOperand.LiteralList
                    ? new CompiledNodes.InLiteral(nodeFor(k[0]), nodes(k, 1))
                    : new CompiledNodes.InField(nodeFor(k[0]), nodeFor(k[1]));
            case ExprNode.Cond x -> new CompiledNodes.Cond(nodeFor(k[0]), nodeFor(k[1]), nodeFor(k[2]));
            case ExprNode.Call x -> call(x.functionId(), nodes(k, 0));
        };
    }

    private static CompiledNodes.FieldRef fieldRef(List<SelectorStep> steps) {
        String[] qual = new String[steps.size()];
        String[] names = new String[steps.size()];
        fill(steps, qual, names);
        return new CompiledNodes.FieldRef(qual, names);
    }

    private static CompiledNodes.Has has(List<SelectorStep> steps) {
        String[] qual = new String[steps.size()];
        String[] names = new String[steps.size()];
        fill(steps, qual, names);
        return new CompiledNodes.Has(qual, names);
    }

    private static void fill(List<SelectorStep> steps, String[] qual, String[] names) {
        for (int i = 0; i < names.length; i++) {
            switch (steps.get(i)) {
                case SelectorStep.Qual q -> {
                    qual[i] = q.className();
                    names[i] = q.fieldName();
                }
                case SelectorStep.Unqual u -> names[i] = u.name();
            }
        }
    }

    private Node call(int functionId, Node[] a) {
        FunctionRegistry.Entry entry = FunctionRegistry.byId(functionId);
        if (entry == null) return new CompiledNodes.CallUnknown(a);
        return switch (entry.name()) {
            case SIZE_STRING -> {
                sizeStringNodes++;
                yield new CompiledNodes.SizeString(a[0]);
            }
            case SIZE_BYTES -> new CompiledNodes.SizeBytes(a[0]);
            case SIZE_LIST -> new CompiledNodes.SizeList(a[0]);
            case INT_OF_DOUBLE -> new CompiledNodes.IntOfDouble(a[0]);
            case DOUBLE_OF_INT -> new CompiledNodes.DoubleOfInt(a[0]);
            case ABS_INT -> new CompiledNodes.AbsInt(a[0]);
            case ABS_DOUBLE -> new CompiledNodes.AbsDouble(a[0]);
            case MIN_INT -> new CompiledNodes.MinInt(a[0], a[1]);
            case MIN_DOUBLE -> new CompiledNodes.MinDouble(a[0], a[1]);
            case MAX_INT -> new CompiledNodes.MaxInt(a[0], a[1]);
            case MAX_DOUBLE -> new CompiledNodes.MaxDouble(a[0], a[1]);
            case SQRT -> new CompiledNodes.Sqrt(a[0]);
            case RADIANS -> new CompiledNodes.Radians(a[0]);
            case DEGREES -> new CompiledNodes.Degrees(a[0]);
            case SIN -> new CompiledNodes.Sin(a[0], math);
            case COS -> new CompiledNodes.Cos(a[0], math);
            case TAN -> new CompiledNodes.Tan(a[0], math);
            case ASIN -> new CompiledNodes.Asin(a[0], math);
            case ACOS -> new CompiledNodes.Acos(a[0], math);
            case ATAN -> new CompiledNodes.Atan(a[0], math);
            case ATAN2 -> new CompiledNodes.Atan2(a[0], a[1], math);
            case CONTAINS -> new CompiledNodes.Contains(a[0], a[1]);
            case STARTS_WITH -> new CompiledNodes.StartsWith(a[0], a[1]);
            case ENDS_WITH -> new CompiledNodes.EndsWith(a[0], a[1]);
        };
    }
}
