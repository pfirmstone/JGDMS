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

import au.net.zeus.jgdms.cel.CelType;
import au.net.zeus.jgdms.cel.CelValue;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.ast.ExprNode.SelectorStep;
import au.net.zeus.jgdms.cel.wire.FunctionRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeSet;

/**
 * A seeded, type-directed generator of random ASTs and of the candidates they run against, for the
 * differential test of {@link CompiledExpression} against {@link Evaluator} (design section 9 items 8-10).
 * One instance is one case: one AST and its K records.
 * <p>
 * <b>What it is built to reach</b>, because a differential test only sees what its corpus reaches:
 * <ul>
 *   <li>All 27 AST kinds and function ids 1-24, plus unregistered ids; height at most {@link #MAX_DEPTH}.</li>
 *   <li>Mostly well-typed subtrees (a per-case wrong-type probability, 0 in 40% of cases), so deep values
 *       reach the root instead of every tree dying at its first type error.</li>
 *   <li>Structural duplicates as DEEP COPIES (so merging by key, not by identity, must find them) and as
 *       reference-shared DAGs (so the identity pass must).</li>
 *   <li>Literal and field pools that overlap in content but never in instance, so equality and
 *       {@code size(string)} depend on content while identity is a separate question.</li>
 *   <li>Hand-built NaN with non-canonical payloads, +-Inf, subnormals, and ints beyond 2^53
 *       (2^53+1, Long.MAX_VALUE) paired with the doubles that expose a lossy widening.</li>
 *   <li>Names with dots and dollars, ambiguous names, a class that declares a field without being in the chain,
 *       nested objects, undecodable roots and nested objects, a poisoned field, and one projection object
 *       reused with different content.</li>
 * </ul>
 */
final class CelCorpusGenerator {

    static final int MAX_DEPTH = 8;
    static final int RECORDS = 8;

    /** A static type the generator steers towards; the value actually produced may differ (that is the point). */
    enum Ty { BOOL, INT, DOUBLE, STRING, BYTES, NULL, LIST, OBJECT }

    static final String[] CLASSES = {"a", "a.b", "c", "A$B"};
    static final String[] FIELD_NAMES = {"b", "i", "j", "d", "e", "s", "t", "y", "n", "l", "m", "o", "x", "b.c", "c", "zz"};
    /** Null = a field whose type changes per record. "zz" is never declared. */
    private static final Ty[] FIELD_TYPES = {Ty.BOOL, Ty.INT, Ty.INT, Ty.DOUBLE, Ty.DOUBLE, Ty.STRING, Ty.STRING,
        Ty.BYTES, Ty.NULL, Ty.LIST, Ty.LIST, Ty.OBJECT, null, Ty.INT, Ty.STRING, Ty.INT};

    private static final long[] INTS = {Long.MIN_VALUE, Long.MIN_VALUE + 1, -1L, 0L, 1L, 2L, 3L, 7L, 10L, 255L, 1000L,
        1L << 31, (1L << 53) - 1, 1L << 53, (1L << 53) + 1, -(1L << 53) - 1, 1L << 62, Long.MAX_VALUE - 1, Long.MAX_VALUE};

    private static final double[] DOUBLES = {0.0, -0.0, 1.0, -1.0, 0.5, 2.5, 3.0, 4.5, 100.0, 1e300, -1e300, 1e-320,
        Double.MIN_VALUE, Double.MAX_VALUE, 9007199254740992.0, 9223372036854775808.0, -9223372036854775808.0,
        9223372036854774784.0, 1e19, 0.1, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN,
        Double.longBitsToDouble(0x7FF0000000000001L), Double.longBitsToDouble(0x7FF8000000000001L),
        Double.longBitsToDouble(0xFFF8000000000000L)};

    /** (int, double) pairs where a lossy {@code (double) int} widening gives a different answer from §4.3.3's exact one. */
    private static final long[] HARD_INTS = {(1L << 53) + 1, 1L << 53, Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE,
        -(1L << 53) - 1, 3L};
    private static final double[] HARD_DOUBLES = {9007199254740992.0, 9007199254740992.0, 9223372036854775808.0,
        -9223372036854775808.0, 9223372036854774784.0, -9007199254740992.0, 3.5};

    private static final String[] STRINGS = {"", "a", "ab", "abc", "b", "z", "é", "hello", "x.y", "$",
        "naïve", "a.b", "c"};
    private static final int[] ASTRAL_CODE_POINTS = {0x1F600, 0x1F601, 0x10000, 0xFFFF};

    private static final byte[][] BYTES = {{}, {0}, {1}, {(byte) 0xFF}, {0, 1}, {1, 2, 3}, {127}, {(byte) 0x80}};

    private static final double[] WRONG_TYPE_PROBABILITY = {0.0, 0.0, 0.0, 0.0, 0.03, 0.03, 0.08, 0.15, 0.4};

    private static final int[] UNKNOWN_FUNCTION_IDS = {0, 25, 99, -1};

    /** A generated subtree with its height (leaf = 0) and its expanded node count. */
    private record G(ExprNode node, int height, int size) {}

    private final SplittableRandom rnd;
    private final TraceLog.Sink sink;
    private final String[] home = new String[FIELD_NAMES.length];
    private final double wrongP;
    private final List<List<G>> pools = new ArrayList<>();
    private final List<String> referenced = new ArrayList<>();
    private int budget;
    private int nextProjectionId;
    private GenProjection previousRoot;

    CelCorpusGenerator(long seed, TraceLog.Sink sink) {
        this.rnd = new SplittableRandom(seed);
        this.sink = sink;
        for (int f = 0; f < home.length; f++) home[f] = CLASSES[rnd.nextInt(CLASSES.length)];
        this.wrongP = WRONG_TYPE_PROBABILITY[rnd.nextInt(WRONG_TYPE_PROBABILITY.length)];
        for (int t = 0; t < Ty.values().length; t++) pools.add(new ArrayList<>());
    }

    static int classIndex(String className) {
        for (int i = 0; i < CLASSES.length; i++) if (CLASSES[i].equals(className)) return i + 1;
        return 0;
    }

    static int fieldIndex(String fieldName) {
        for (int i = 0; i < FIELD_NAMES.length; i++) if (FIELD_NAMES[i].equals(fieldName)) return i + 1;
        return 0;
    }

    // ======================================================================
    // Expressions
    // ======================================================================

    /** The case's AST. Call once. */
    ExprNode expression() {
        budget = 40 + rnd.nextInt(260);
        return gen(randomTy(), 0).node;
    }

    private Ty randomTy() {
        return Ty.values()[rnd.nextInt(Ty.values().length)];
    }

    private Ty scalarTy() {
        return Ty.values()[rnd.nextInt(5)];
    }

    private G gen(Ty want, int depth) {
        Ty t = rnd.nextDouble() < wrongP ? randomTy() : want;
        boolean leaf = depth >= MAX_DEPTH || budget <= 0 || rnd.nextInt(100) < (depth == 0 ? 8 : 10 + depth * 9);
        if (!leaf && rnd.nextInt(100) < 14) {
            G reused = reuse(t, depth);
            if (reused != null) return reused;
        }
        budget--;
        G g = leaf ? leaf(t) : branch(t, depth);
        List<G> pool = pools.get(t.ordinal());
        if (pool.size() < 8) pool.add(g); else pool.set(rnd.nextInt(pool.size()), g);
        return g;
    }

    /** An earlier subtree of this type, as the same object (a DAG) or as a deep copy; never taller than the budget allows. */
    private G reuse(Ty t, int depth) {
        List<G> pool = pools.get(t.ordinal());
        if (pool.isEmpty()) return null;
        G g = pool.get(rnd.nextInt(pool.size()));
        if (depth + g.height > MAX_DEPTH || g.size > 90) return null;
        budget -= g.size;
        return new G(rnd.nextBoolean() ? g.node : deepCopy(g.node), g.height, g.size);
    }

    private static G mk(ExprNode node, G... kids) {
        int height = 0;
        int size = 1;
        for (G k : kids) {
            height = Math.max(height, k.height + 1);
            size += k.size;
        }
        return new G(node, height, size);
    }

    private G leaf(Ty t) {
        return switch (t) {
            case BOOL -> rnd.nextInt(4) == 0 ? field(Ty.BOOL) : new G(new ExprNode.LitBool(rnd.nextBoolean()), 0, 1);
            case INT -> rnd.nextInt(4) == 0 ? field(Ty.INT) : new G(new ExprNode.LitInt(pickInt()), 0, 1);
            case DOUBLE -> rnd.nextInt(4) == 0 ? field(Ty.DOUBLE) : new G(new ExprNode.LitDouble(pickDouble()), 0, 1);
            case STRING -> rnd.nextInt(4) == 0 ? field(Ty.STRING) : new G(new ExprNode.LitString(pickString()), 0, 1);
            case BYTES -> rnd.nextInt(4) == 0 ? field(Ty.BYTES) : new G(new ExprNode.LitBytes(pickBytes()), 0, 1);
            case NULL -> rnd.nextInt(3) == 0 ? field(Ty.NULL) : new G(new ExprNode.LitNull(), 0, 1);
            case LIST -> field(Ty.LIST);
            case OBJECT -> field(Ty.OBJECT);
        };
    }

    private G branch(Ty t, int d) {
        if (rnd.nextInt(100) < 2) return unknownCall(d);
        return switch (t) {
            case BOOL -> genBool(d);
            case INT -> genInt(d);
            case DOUBLE -> genDouble(d);
            case STRING -> choose(d, Ty.STRING, 3);
            case BYTES -> choose(d, Ty.BYTES, 3);
            case NULL -> choose(d, Ty.NULL, 3);
            case LIST -> rnd.nextInt(100) < 55 ? listLit(d) : choose(d, Ty.LIST, 3);
            case OBJECT -> choose(d, Ty.OBJECT, 3);
        };
    }

    /** A conditional, or a field of the type (for types with no operators). */
    private G choose(int d, Ty t, int condPercent) {
        if (rnd.nextInt(100) < 100 / condPercent) return cond(t, d);
        return field(t);
    }

    private G cond(Ty t, int d) {
        G c = gen(Ty.BOOL, d + 1);
        G a = gen(t, d + 1);
        G b = gen(t, d + 1);
        return mk(new ExprNode.Cond(c.node, a.node, b.node), c, a, b);
    }

    private G unknownCall(int d) {
        int arity = 1 + rnd.nextInt(2);
        List<ExprNode> args = new ArrayList<>();
        G[] kids = new G[arity];
        for (int i = 0; i < arity; i++) {
            kids[i] = gen(randomTy(), d + 1);
            args.add(kids[i].node);
        }
        return mk(new ExprNode.Call(UNKNOWN_FUNCTION_IDS[rnd.nextInt(UNKNOWN_FUNCTION_IDS.length)], args), kids);
    }

    private G call(FunctionRegistry.Name fn, int d, Ty... argTypes) {
        List<ExprNode> args = new ArrayList<>();
        G[] kids = new G[argTypes.length];
        for (int i = 0; i < argTypes.length; i++) {
            kids[i] = gen(argTypes[i], d + 1);
            args.add(kids[i].node);
        }
        return mk(new ExprNode.Call(Ast.id(fn), args), kids);
    }

    private G genBool(int d) {
        int r = rnd.nextInt(100);
        if (r < 8) {
            G a = gen(Ty.BOOL, d + 1);
            return mk(new ExprNode.Not(a.node), a);
        }
        if (r < 18) {
            G a = gen(Ty.BOOL, d + 1);
            G b = gen(Ty.BOOL, d + 1);
            return mk(new ExprNode.And(a.node, b.node), a, b);
        }
        if (r < 28) {
            G a = gen(Ty.BOOL, d + 1);
            G b = gen(Ty.BOOL, d + 1);
            return mk(new ExprNode.Or(a.node, b.node), a, b);
        }
        if (r < 52) return comparison(d, r);
        if (r < 60) return inLiteral(d);
        if (r < 65) return inField(d);
        if (r < 73) return has();
        if (r < 79) return cond(Ty.BOOL, d);
        if (r < 97) {
            int id = Ast.id(STRING_PREDICATES[rnd.nextInt(3)]);
            G receiver = gen(Ty.STRING, d + 1);
            G needle = rnd.nextInt(10) < 9
                    ? new G(new ExprNode.LitString(pickString()), 0, 1)
                    : gen(Ty.STRING, d + 1);
            return mk(new ExprNode.Call(id, List.of(receiver.node, needle.node)), receiver, needle);
        }
        return field(Ty.BOOL);
    }

    private G comparison(int d, int r) {
        Ty operand = switch (rnd.nextInt(8)) {
            case 0, 1 -> Ty.INT;
            case 2, 3 -> Ty.DOUBLE;
            case 4 -> Ty.STRING;
            case 5 -> Ty.BYTES;
            case 6 -> Ty.BOOL;
            default -> randomTy();
        };
        G a;
        G b;
        if ((operand == Ty.INT || operand == Ty.DOUBLE) && rnd.nextInt(100) < 35) {
            // int against double: the pairs where a lossy widening is wrong, in either order
            int k = rnd.nextInt(HARD_INTS.length);
            a = new G(new ExprNode.LitInt(HARD_INTS[k]), 0, 1);
            b = new G(new ExprNode.LitDouble(HARD_DOUBLES[k]), 0, 1);
            if (rnd.nextBoolean()) {
                G swap = a;
                a = b;
                b = swap;
            }
        } else {
            a = gen(operand, d + 1);
            b = gen(rnd.nextInt(100) < 15 ? scalarTy() : operand, d + 1);
        }
        ExprNode n = switch (r % 6) {
            case 0 -> new ExprNode.Eq(a.node, b.node);
            case 1 -> new ExprNode.Ne(a.node, b.node);
            case 2 -> new ExprNode.Lt(a.node, b.node);
            case 3 -> new ExprNode.Le(a.node, b.node);
            case 4 -> new ExprNode.Gt(a.node, b.node);
            default -> new ExprNode.Ge(a.node, b.node);
        };
        return mk(n, a, b);
    }

    private G inLiteral(int d) {
        Ty element = scalarTy();
        G needle = gen(element, d + 1);
        int n = 1 + rnd.nextInt(4);
        List<ExprNode> elements = new ArrayList<>();
        G[] kids = new G[n + 1];
        kids[0] = needle;
        for (int i = 0; i < n; i++) {
            kids[i + 1] = gen(rnd.nextInt(100) < 12 ? randomTy() : element, d + 1);
            elements.add(kids[i + 1].node);
        }
        ExprNode in = new ExprNode.In(needle.node,
                new ExprNode.InListOperand.LiteralList(new ExprNode.ListLit(elements)));
        return mk(in, kids);
    }

    private G inField(int d) {
        G needle = gen(scalarTy(), d + 1);
        ExprNode.FieldRef list = fieldRef(Ty.LIST);
        return mk(new ExprNode.In(needle.node, new ExprNode.InListOperand.FieldList(list)), needle);
    }

    private G has() {
        return new G(new ExprNode.Has(fieldRef(randomTy())), 0, 1);
    }

    private G listLit(int d) {
        Ty element = scalarTy();
        int n = 1 + rnd.nextInt(4);
        List<ExprNode> elements = new ArrayList<>();
        G[] kids = new G[n];
        for (int i = 0; i < n; i++) {
            kids[i] = gen(rnd.nextInt(100) < 15 ? randomTy() : element, d + 1);
            elements.add(kids[i].node);
        }
        return mk(new ExprNode.ListLit(elements), kids);
    }

    private G genInt(int d) {
        int r = rnd.nextInt(100);
        if (r < 6) {
            G a = gen(Ty.INT, d + 1);
            return mk(new ExprNode.Neg(a.node), a);
        }
        if (r < 46) return arith(Ty.INT, d, r);
        if (r < 52) return cond(Ty.INT, d);
        if (r < 58) return call(FunctionRegistry.Name.SIZE_STRING, d, Ty.STRING);
        if (r < 62) return call(FunctionRegistry.Name.SIZE_BYTES, d, Ty.BYTES);
        if (r < 66) return call(FunctionRegistry.Name.SIZE_LIST, d, Ty.LIST);
        if (r < 72) return call(FunctionRegistry.Name.INT_OF_DOUBLE, d, Ty.DOUBLE);
        if (r < 77) return call(FunctionRegistry.Name.ABS_INT, d, Ty.INT);
        if (r < 83) return call(FunctionRegistry.Name.MIN_INT, d, Ty.INT, Ty.INT);
        if (r < 89) return call(FunctionRegistry.Name.MAX_INT, d, Ty.INT, Ty.INT);
        return field(Ty.INT);
    }

    private G genDouble(int d) {
        int r = rnd.nextInt(100);
        if (r < 5) {
            G a = gen(Ty.DOUBLE, d + 1);
            return mk(new ExprNode.Neg(a.node), a);
        }
        if (r < 35) return arith(Ty.DOUBLE, d, r);
        if (r < 40) return cond(Ty.DOUBLE, d);
        if (r < 44) return call(FunctionRegistry.Name.DOUBLE_OF_INT, d, Ty.INT);
        if (r < 47) return call(FunctionRegistry.Name.ABS_DOUBLE, d, Ty.DOUBLE);
        if (r < 51) return call(FunctionRegistry.Name.MIN_DOUBLE, d, Ty.DOUBLE, Ty.DOUBLE);
        if (r < 55) return call(FunctionRegistry.Name.MAX_DOUBLE, d, Ty.DOUBLE, Ty.DOUBLE);
        if (r < 59) return call(FunctionRegistry.Name.SQRT, d, Ty.DOUBLE);
        if (r < 63) return call(FunctionRegistry.Name.RADIANS, d, Ty.DOUBLE);
        if (r < 67) return call(FunctionRegistry.Name.DEGREES, d, Ty.DOUBLE);
        if (r < 90) {
            return call(UNARY_TRIG[rnd.nextInt(6)], d, Ty.DOUBLE);
        }
        if (r < 96) return call(FunctionRegistry.Name.ATAN2, d, Ty.DOUBLE, Ty.DOUBLE);
        return field(Ty.DOUBLE);
    }

    private G arith(Ty t, int d, int r) {
        G a = gen(t, d + 1);
        G b = gen(t, d + 1);
        ExprNode n = switch (r % 5) {
            case 0 -> new ExprNode.Add(a.node, b.node);
            case 1 -> new ExprNode.Sub(a.node, b.node);
            case 2 -> new ExprNode.Mul(a.node, b.node);
            case 3 -> new ExprNode.Div(a.node, b.node);
            default -> new ExprNode.Mod(a.node, b.node);
        };
        return mk(n, a, b);
    }

    // ======================================================================
    // Field references
    // ======================================================================

    private G field(Ty t) {
        return new G(fieldRef(t), 0, 1);
    }

    private static final String[][] NAMES_BY_TYPE = {
        {"b"}, {"i", "j", "b.c"}, {"d", "e"}, {"s", "t", "c"}, {"y"}, {"n"}, {"l", "m"}, {"o"}};

    private ExprNode.FieldRef fieldRef(Ty t) {
        int roll = rnd.nextInt(100);
        String name;
        if (roll < 12) {
            name = "x";
        } else if (roll < 15) {
            name = "zz";
        } else {
            String[] names = NAMES_BY_TYPE[t.ordinal()];
            name = names[rnd.nextInt(names.length)];
        }
        referenced.add(name);
        List<SelectorStep> steps = new ArrayList<>();
        int shape = rnd.nextInt(100);
        if (shape < 64) {
            steps.add(new SelectorStep.Unqual(name));
        } else if (shape < 84) {
            steps.add(new SelectorStep.Qual(classFor(name), name));
        } else if (shape < 95) {
            steps.add(step("o"));
            if (rnd.nextInt(5) == 0) steps.add(step("o"));
            steps.add(step(name));
        } else {
            String through = new String[] {"i", "s", "n"}[rnd.nextInt(3)];
            steps.add(new SelectorStep.Unqual(through));
            steps.add(new SelectorStep.Unqual(name));
        }
        return new ExprNode.FieldRef(steps);
    }

    private SelectorStep step(String name) {
        return rnd.nextInt(4) == 0 ? new SelectorStep.Qual(classFor(name), name) : new SelectorStep.Unqual(name);
    }

    private String classFor(String name) {
        int f = fieldIndex(name) - 1;
        return (f >= 0 && rnd.nextInt(100) < 75) ? home[f] : CLASSES[rnd.nextInt(CLASSES.length)];
    }

    // ======================================================================
    // Literal pools. A new String or array every time: equal content, distinct instance.
    // ======================================================================

    private long pickInt() {
        return rnd.nextInt(5) == 0 ? rnd.nextInt(-5, 12) : INTS[rnd.nextInt(INTS.length)];
    }

    private double pickDouble() {
        if (rnd.nextInt(6) == 0) return rnd.nextInt(-4, 9) / 2.0;
        return DOUBLES[rnd.nextInt(DOUBLES.length)];
    }

    private String pickString() {
        int r = rnd.nextInt(10);
        if (r < 2) {
            StringBuilder sb = new StringBuilder();
            sb.appendCodePoint(ASTRAL_CODE_POINTS[rnd.nextInt(ASTRAL_CODE_POINTS.length)]);
            if (rnd.nextBoolean()) sb.append(STRINGS[rnd.nextInt(STRINGS.length)]);
            return sb.toString();
        }
        return new String(STRINGS[rnd.nextInt(STRINGS.length)].toCharArray());
    }

    private byte[] pickBytes() {
        return BYTES[rnd.nextInt(BYTES.length)].clone();
    }

    // ======================================================================
    // Duplicates
    // ======================================================================

    /** A structurally equal tree sharing no node, string or array with {@code n}. */
    static ExprNode deepCopy(ExprNode n) {
        return switch (n) {
            case ExprNode.LitBool x -> new ExprNode.LitBool(x.value());
            case ExprNode.LitInt x -> new ExprNode.LitInt(x.value());
            case ExprNode.LitDouble x -> new ExprNode.LitDouble(x.value());
            case ExprNode.LitString x -> new ExprNode.LitString(new String(x.value().toCharArray()));
            case ExprNode.LitBytes x -> new ExprNode.LitBytes(x.value().clone());
            case ExprNode.LitNull x -> new ExprNode.LitNull();
            case ExprNode.ListLit x -> new ExprNode.ListLit(copyAll(x.elements()));
            case ExprNode.FieldRef x -> copyFieldRef(x);
            case ExprNode.Has x -> new ExprNode.Has(copyFieldRef(x.target()));
            case ExprNode.Not x -> new ExprNode.Not(deepCopy(x.operand()));
            case ExprNode.Neg x -> new ExprNode.Neg(deepCopy(x.operand()));
            case ExprNode.And x -> new ExprNode.And(deepCopy(x.left()), deepCopy(x.right()));
            case ExprNode.Or x -> new ExprNode.Or(deepCopy(x.left()), deepCopy(x.right()));
            case ExprNode.Eq x -> new ExprNode.Eq(deepCopy(x.left()), deepCopy(x.right()));
            case ExprNode.Ne x -> new ExprNode.Ne(deepCopy(x.left()), deepCopy(x.right()));
            case ExprNode.Lt x -> new ExprNode.Lt(deepCopy(x.left()), deepCopy(x.right()));
            case ExprNode.Le x -> new ExprNode.Le(deepCopy(x.left()), deepCopy(x.right()));
            case ExprNode.Gt x -> new ExprNode.Gt(deepCopy(x.left()), deepCopy(x.right()));
            case ExprNode.Ge x -> new ExprNode.Ge(deepCopy(x.left()), deepCopy(x.right()));
            case ExprNode.Add x -> new ExprNode.Add(deepCopy(x.left()), deepCopy(x.right()));
            case ExprNode.Sub x -> new ExprNode.Sub(deepCopy(x.left()), deepCopy(x.right()));
            case ExprNode.Mul x -> new ExprNode.Mul(deepCopy(x.left()), deepCopy(x.right()));
            case ExprNode.Div x -> new ExprNode.Div(deepCopy(x.left()), deepCopy(x.right()));
            case ExprNode.Mod x -> new ExprNode.Mod(deepCopy(x.left()), deepCopy(x.right()));
            case ExprNode.In x -> new ExprNode.In(deepCopy(x.needle()), x.listOperand() instanceof ExprNode.InListOperand.LiteralList ll
                    ? new ExprNode.InListOperand.LiteralList(new ExprNode.ListLit(copyAll(ll.list().elements())))
                    : new ExprNode.InListOperand.FieldList(copyFieldRef(((ExprNode.InListOperand.FieldList) x.listOperand()).field())));
            case ExprNode.Cond x -> new ExprNode.Cond(deepCopy(x.condition()), deepCopy(x.thenBranch()), deepCopy(x.elseBranch()));
            case ExprNode.Call x -> new ExprNode.Call(x.functionId(), copyAll(x.arguments()));
        };
    }

    private static List<ExprNode> copyAll(List<ExprNode> nodes) {
        List<ExprNode> out = new ArrayList<>(nodes.size());
        for (ExprNode n : nodes) out.add(deepCopy(n));
        return out;
    }

    private static ExprNode.FieldRef copyFieldRef(ExprNode.FieldRef f) {
        List<SelectorStep> steps = new ArrayList<>();
        for (SelectorStep s : f.steps()) {
            steps.add(switch (s) {
                case SelectorStep.Unqual u -> new SelectorStep.Unqual(new String(u.name().toCharArray()));
                case SelectorStep.Qual q -> new SelectorStep.Qual(new String(q.className().toCharArray()),
                        new String(q.fieldName().toCharArray()));
            });
        }
        return new ExprNode.FieldRef(steps);
    }

    // ======================================================================
    // Candidates
    // ======================================================================

    /**
     * Record {@code index} of {@link #RECORDS}. Fixed roles, so every property is present in every case:
     * 0-3 ordinary; 4 reuses record 3's projection object with new content; 5 has a poisoned field that
     * throws; 6 is usually an undecodable root; 7 is ordinary.
     */
    GenProjection record(int index) {
        if (index == 4 && previousRoot != null) {
            reseed(previousRoot, 0);
            return previousRoot;
        }
        GenProjection p = projection(0);
        if (index == 5 && !referenced.isEmpty()) p.poison = referenced.get(rnd.nextInt(referenced.size()));
        if (index == 6 && rnd.nextInt(10) < 3) p.undecodable = true;
        previousRoot = p;
        return p;
    }

    private GenProjection projection(int depth) {
        GenProjection p = new GenProjection(sink, nextProjectionId++);
        p.undecodable = depth > 0 && rnd.nextInt(10) == 0;
        reseed(p, depth);
        return p;
    }

    /** New chain and new values; the same object. */
    private void reseed(GenProjection p, int depth) {
        p.data.clear();
        p.poison = null;
        List<String> classes = new ArrayList<>(List.of(CLASSES));
        Collections.shuffle(classes, new Random(rnd.nextLong()));
        int chainLength = 2 + rnd.nextInt(3);
        p.chain = List.copyOf(classes.subList(0, chainLength));
        String hidden = classes.get(CLASSES.length - 1);
        for (int f = 0; f < FIELD_NAMES.length; f++) {
            String name = FIELD_NAMES[f];
            if (name.equals("zz") || rnd.nextInt(100) < 10) continue; // never declared / absent this time
            String primary = (p.chain.contains(home[f]) && rnd.nextInt(100) < 92) ? home[f]
                    : p.chain.get(rnd.nextInt(chainLength));
            if (rnd.nextInt(100) < 6) primary = hidden;
            declare(p, primary, name, f, depth);
            if (rnd.nextInt(100) < 12) declare(p, p.chain.get(rnd.nextInt(chainLength)), name, f, depth); // ambiguity
        }
    }

    private void declare(GenProjection p, String className, String name, int f, int depth) {
        Ty t = FIELD_TYPES[f];
        p.data.computeIfAbsent(className, k -> new HashMap<>()).put(name, value(t == null ? randomTy() : t, depth));
    }

    private CelValue value(Ty t, int depth) {
        int roll = rnd.nextInt(100);
        if (roll < 6) return CelValue.NullV.INSTANCE;
        if (roll < 12) t = randomTy();
        return switch (t) {
            case BOOL -> new CelValue.BoolV(rnd.nextBoolean());
            case INT -> new CelValue.IntV(pickInt());
            case DOUBLE -> new CelValue.DoubleV(pickDouble());
            case STRING -> new CelValue.StringV(pickString());
            case BYTES -> new CelValue.BytesV(pickBytes());
            case NULL -> CelValue.NullV.INSTANCE;
            case LIST -> listValue();
            case OBJECT -> depth < 2 ? new CelValue.ObjectV(projection(depth + 1)) : CelValue.NullV.INSTANCE;
        };
    }

    /** A ListV; one in ten is heterogeneous, which the constructor does not forbid and {@code in} must handle. */
    private CelValue listValue() {
        Ty element = scalarTy();
        int n = rnd.nextInt(5);
        List<CelValue> elements = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Ty t = rnd.nextInt(10) == 0 ? scalarTy() : element;
            elements.add(switch (t) {
                case BOOL -> new CelValue.BoolV(rnd.nextBoolean());
                case INT -> new CelValue.IntV(pickInt());
                case DOUBLE -> new CelValue.DoubleV(pickDouble());
                case STRING -> new CelValue.StringV(pickString());
                default -> new CelValue.BytesV(pickBytes());
            });
        }
        return new CelValue.ListV(CelType.valueOf(element.name()), elements);
    }

    // ======================================================================
    // Census
    // ======================================================================

    /** The kinds present in {@code root}: AST kind names, {@code Call:<id>}, and the two {@code In} operand shapes. Shared nodes are visited once. */
    static Set<String> kindsOf(ExprNode root) {
        Set<String> out = new TreeSet<>();
        collect(root, new IdentityHashMap<>(), out);
        return out;
    }

    /** The number of ExprNode objects reached more than once: reference sharing. */
    static int sharedReferences(ExprNode root) {
        IdentityHashMap<ExprNode, Boolean> seen = new IdentityHashMap<>();
        int[] shared = {0};
        count(root, seen, shared);
        return shared[0];
    }

    private static void collect(ExprNode n, IdentityHashMap<ExprNode, Boolean> seen, Set<String> out) {
        if (seen.put(n, Boolean.TRUE) != null) return;
        out.add(n.getClass().getSimpleName());
        for (ExprNode c : children(n)) collect(c, seen, out);
        if (n instanceof ExprNode.Call c) out.add(new StringBuilder("Call:").append(c.functionId()).toString());
        if (n instanceof ExprNode.In in) {
            out.add(in.listOperand() instanceof ExprNode.InListOperand.LiteralList ? "In:literal" : "In:field");
        }
    }

    private static void count(ExprNode n, IdentityHashMap<ExprNode, Boolean> seen, int[] shared) {
        if (seen.put(n, Boolean.TRUE) != null) {
            shared[0]++;
            return;
        }
        for (ExprNode c : children(n)) count(c, seen, shared);
    }

    /** The expression children; an {@code in} field list's FieldRef counts as a child too. */
    static List<ExprNode> children(ExprNode n) {
        return switch (n) {
            case ExprNode.ListLit x -> x.elements();
            case ExprNode.Not x -> List.of(x.operand());
            case ExprNode.Neg x -> List.of(x.operand());
            case ExprNode.And x -> List.of(x.left(), x.right());
            case ExprNode.Or x -> List.of(x.left(), x.right());
            case ExprNode.Eq x -> List.of(x.left(), x.right());
            case ExprNode.Ne x -> List.of(x.left(), x.right());
            case ExprNode.Lt x -> List.of(x.left(), x.right());
            case ExprNode.Le x -> List.of(x.left(), x.right());
            case ExprNode.Gt x -> List.of(x.left(), x.right());
            case ExprNode.Ge x -> List.of(x.left(), x.right());
            case ExprNode.Add x -> List.of(x.left(), x.right());
            case ExprNode.Sub x -> List.of(x.left(), x.right());
            case ExprNode.Mul x -> List.of(x.left(), x.right());
            case ExprNode.Div x -> List.of(x.left(), x.right());
            case ExprNode.Mod x -> List.of(x.left(), x.right());
            case ExprNode.In x -> {
                List<ExprNode> out = new ArrayList<>();
                out.add(x.needle());
                if (x.listOperand() instanceof ExprNode.InListOperand.LiteralList ll) {
                    out.addAll(ll.list().elements());
                } else {
                    out.add(((ExprNode.InListOperand.FieldList) x.listOperand()).field());
                }
                yield out;
            }
            case ExprNode.Cond x -> List.of(x.condition(), x.thenBranch(), x.elseBranch());
            case ExprNode.Call x -> x.arguments();
            default -> List.of(); // literals, FieldRef, Has
        };
    }

    private static final FunctionRegistry.Name[] STRING_PREDICATES = {FunctionRegistry.Name.CONTAINS,
        FunctionRegistry.Name.STARTS_WITH, FunctionRegistry.Name.ENDS_WITH};

    private static final FunctionRegistry.Name[] UNARY_TRIG = {FunctionRegistry.Name.SIN, FunctionRegistry.Name.COS,
        FunctionRegistry.Name.TAN, FunctionRegistry.Name.ASIN, FunctionRegistry.Name.ACOS, FunctionRegistry.Name.ATAN};
}
