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

import au.net.zeus.jgdms.cel.CelError;
import au.net.zeus.jgdms.cel.CelType;
import au.net.zeus.jgdms.cel.CelValue;
import au.net.zeus.jgdms.cel.math.MathProvider;

import java.util.Arrays;
import java.util.List;

/**
 * The executable nodes of a {@link CompiledExpression} and the {@link Frame} register they pass
 * results through. Package-private: {@link CompiledExpression} is the API.
 * <p>
 * <b>Contract every node keeps.</b> {@code eval} leaves exactly the outcome {@link Evaluator} would
 * return for the same sub-expression in the register, it makes the same calls on the
 * {@link CandidateProjection} and the {@link MathProvider} in the same order, and it throws the same
 * exceptions at the same point. Evaluation order is part of the contract, not an optimisation target:
 * with {@code MathProvider.none()} a {@code sin} the walker never reaches must not throw here either.
 * <p>
 * <b>Register discipline</b> (the main correctness risk of this design, design section 7 R2). A child
 * overwrites the register, so a node that needs its left result after evaluating its right copies
 * {@code tag}, {@code bits} and {@code ref} into locals first. Every node that leaves {@code DOUBLE} in
 * the register stores NaN as the canonical quiet NaN (§4.3.4), because the walker canonicalises at every
 * {@code evaluate()} return and a non-canonical payload would otherwise survive a pass-through node.
 * For {@code STRING}, {@code BYTES}, {@code LIST} and {@code OBJECT} the {@code ref} is always the
 * {@link CelValue}, never the bare payload, so a field value reaches the result as the projection's own
 * instance (§9 item 5).
 */
final class CompiledNodes {

    private CompiledNodes() {}

    static final byte BOOL = 0;
    static final byte INT = 1;
    static final byte DOUBLE = 2;
    static final byte STRING = 3;
    static final byte BYTES = 4;
    static final byte NULL = 5;
    static final byte LIST = 6;
    static final byte OBJECT = 7;
    static final byte ERROR = 8;

    private static final int EQUAL = 0;
    private static final int NOT_EQUAL = 1;
    private static final int MISMATCH = 2;

    private static final int LT = 0;
    private static final int LE = 1;
    private static final int GT = 2;
    private static final int GE = 3;

    private static final int ADD = 0;
    private static final int SUB = 1;
    private static final int MUL = 2;
    private static final int DIV = 3;
    private static final int MOD = 4;

    /** Names a class that is ambiguous across the chain; compared by identity, never equal to a real class name. */
    private static final String AMBIGUOUS = new String("<ambiguous>");

    /**
     * The register plus everything else one thread mutates while evaluating: the candidate, the per-record
     * memo slots and the per-record {@code size(string)} identity table. Owned by exactly one
     * {@link CompiledExpression.Session}.
     */
    static final class Frame {
        byte tag;
        long bits;
        Object ref;
        CandidateProjection candidate;

        /**
         * Advanced at the ENTRY of every evaluation, never at its end: a record that ends in an exception
         * (FilterEval swallows Throwable and fails closed) must leave no memo valid for the next record.
         * A long, so it cannot wrap in practice (design section 9 item 2).
         */
        long epoch;
        final long[] memoEpoch;
        final byte[] memoTag;
        final long[] memoBits;
        final Object[] memoRef;
        long memoHits;

        private final String[] sizeKeys;
        private final int[] sizeCounts;
        int sizeFill;
        long sizeScans;

        Frame(int memoSlots, int sizeStringNodes) {
            memoEpoch = new long[memoSlots];
            memoTag = new byte[memoSlots];
            memoBits = new long[memoSlots];
            memoRef = new Object[memoSlots];
            sizeKeys = new String[sizeStringNodes];
            sizeCounts = new int[sizeStringNodes];
        }

        void bool(boolean b) {
            tag = BOOL;
            bits = b ? 1L : 0L;
        }

        void integer(long v) {
            tag = INT;
            bits = v;
        }

        void dbl(double d) {
            tag = DOUBLE;
            bits = (d != d) ? CelValue.DoubleV.CANONICAL_NAN_BITS : Double.doubleToRawLongBits(d);
        }

        void err(CelError e) {
            tag = ERROR;
            ref = e;
        }

        /** Loads a projection's value; a DoubleV is canonicalised exactly as the walker does at the node boundary. */
        void value(CelValue v) {
            switch (v) {
                case CelValue.BoolV b -> bool(b.value());
                case CelValue.IntV i -> integer(i.value());
                case CelValue.DoubleV d -> dbl(d.value());
                case CelValue.StringV s -> { tag = STRING; ref = s; }
                case CelValue.BytesV b -> { tag = BYTES; ref = b; }
                case CelValue.NullV n -> tag = NULL;
                case CelValue.ListV l -> { tag = LIST; ref = l; }
                case CelValue.ObjectV o -> { tag = OBJECT; ref = o; }
            }
        }

        /**
         * The code-point count of {@code s}, scanned at most once per distinct String instance per record.
         * The table is bounded by the number of {@code size(string)} nodes, so nothing grows with the data and
         * a reset is {@code sizeFill = 0}. Keyed by identity on purpose: the projection contract promises the
         * identical String for the identical query within one evaluation, and equal-content Strings that are
         * different instances are counted independently, exactly as the walker's IdentityHashMap does.
         */
        long sizeStringCount(String s) {
            for (int i = 0; i < sizeFill; i++) {
                if (sizeKeys[i] == s) return sizeCounts[i];
            }
            sizeScans++;
            int count = s.codePointCount(0, s.length());
            if (sizeFill < sizeKeys.length) {
                sizeKeys[sizeFill] = s;
                sizeCounts[sizeFill] = count;
                sizeFill++;
            }
            return count;
        }
    }

    abstract static class Node {
        abstract void eval(Frame f);
    }

    // ======================================================================
    // Shared plumbing
    // ======================================================================

    private static double dbl(long bits) {
        return Double.longBitsToDouble(bits);
    }

    private static String str(Object ref) {
        return ((CelValue.StringV) ref).value();
    }

    private static byte[] bytes(Object ref) {
        return ((CelValue.BytesV) ref).value();
    }

    /** "A non-bool value is first replaced by TYPE_MISMATCH at its own position" (§6.4). */
    private static void coerceToBool(Frame f) {
        if (f.tag != ERROR && f.tag != BOOL) f.err(CelError.TYPE_MISMATCH);
    }

    /**
     * §6.1's equality table over register operands. The null rows come first and apply to any other
     * operand type, including list and object, which are otherwise not comparable.
     */
    private static int equality(byte lt, long lb, Object lr, byte rt, long rb, Object rr) {
        if (lt == NULL) return rt == NULL ? EQUAL : NOT_EQUAL;
        if (rt == NULL) return NOT_EQUAL;
        if (lt == BOOL && rt == BOOL) return lb == rb ? EQUAL : NOT_EQUAL;
        if (lt == INT && rt == INT) return lb == rb ? EQUAL : NOT_EQUAL;
        // Primitive ==, never Double.equals/compare: NaN != NaN and -0.0 == 0.0 (§4.3.4).
        if (lt == DOUBLE && rt == DOUBLE) return dbl(lb) == dbl(rb) ? EQUAL : NOT_EQUAL;
        if (lt == INT && rt == DOUBLE) return CelSemantics.intDoubleEqual(lb, dbl(rb)) ? EQUAL : NOT_EQUAL;
        if (lt == DOUBLE && rt == INT) return CelSemantics.intDoubleEqual(rb, dbl(lb)) ? EQUAL : NOT_EQUAL;
        if (lt == STRING && rt == STRING) return str(lr).equals(str(rr)) ? EQUAL : NOT_EQUAL;
        if (lt == BYTES && rt == BYTES) return Arrays.equals(bytes(lr), bytes(rr)) ? EQUAL : NOT_EQUAL;
        return MISMATCH;
    }

    /** {@link #equality} with the right operand a field list's element, which arrives as a CelValue. */
    private static int equalityWithValue(byte lt, long lb, Object lr, CelValue e) {
        byte rt;
        long rb = 0L;
        switch (e) {
            case CelValue.BoolV b -> { rt = BOOL; rb = b.value() ? 1L : 0L; }
            case CelValue.IntV i -> { rt = INT; rb = i.value(); }
            case CelValue.DoubleV d -> { rt = DOUBLE; rb = Double.doubleToRawLongBits(d.value()); }
            case CelValue.StringV s -> rt = STRING;
            case CelValue.BytesV b -> rt = BYTES;
            case CelValue.NullV n -> rt = NULL;
            case CelValue.ListV l -> rt = LIST;
            case CelValue.ObjectV o -> rt = OBJECT;
        }
        return equality(lt, lb, lr, rt, rb, e);
    }

    private static boolean applyOrder(int cmp, int op) {
        return switch (op) {
            case LT -> cmp < 0;
            case LE -> cmp <= 0;
            case GT -> cmp > 0;
            default -> cmp >= 0;
        };
    }

    /** Primitive comparisons: NaN yields false for every operator, by IEEE definition (§4.3.4). */
    private static boolean applyOrder(double a, double b, int op) {
        return switch (op) {
            case LT -> a < b;
            case LE -> a <= b;
            case GT -> a > b;
            default -> a >= b;
        };
    }

    private static CelValue boxElement(byte tag, long bits, Object ref) {
        return switch (tag) {
            case BOOL -> new CelValue.BoolV(bits != 0L);
            case INT -> new CelValue.IntV(bits);
            case DOUBLE -> new CelValue.DoubleV(dbl(bits));
            default -> (CelValue) ref; // STRING, BYTES
        };
    }

    private static CelType elementType(byte tag) {
        return switch (tag) {
            case BOOL -> CelType.BOOL;
            case INT -> CelType.INT;
            case DOUBLE -> CelType.DOUBLE;
            case STRING -> CelType.STRING;
            default -> CelType.BYTES;
        };
    }

    // ======================================================================
    // Sharing
    // ======================================================================

    /** A literal is cheaper to reload than to look up, so it is never wrapped. */
    static final class Const extends Node {
        private final byte tag;
        private final long bits;
        private final Object ref;

        Const(byte tag, long bits, Object ref) {
            this.tag = tag;
            this.bits = bits;
            this.ref = ref;
        }

        @Override
        void eval(Frame f) {
            f.tag = tag;
            f.bits = bits;
            f.ref = ref;
        }
    }

    /**
     * A node with two or more parents, evaluated at most once per record. The ERROR result is memoised
     * too: the walker returns the same error each time it evaluates the same pure sub-expression. A
     * memoised sub-expression is evaluated lazily at its first use, so a shared node under an untaken
     * {@code Cond} branch or after a strict error is never evaluated early.
     */
    static final class Memo extends Node {
        private final Node inner;
        private final int slot;

        Memo(Node inner, int slot) {
            this.inner = inner;
            this.slot = slot;
        }

        @Override
        void eval(Frame f) {
            int s = slot;
            if (f.memoEpoch[s] == f.epoch) {
                f.memoHits++;
                f.tag = f.memoTag[s];
                f.bits = f.memoBits[s];
                f.ref = f.memoRef[s];
                return;
            }
            inner.eval(f);
            f.memoEpoch[s] = f.epoch;
            f.memoTag[s] = f.tag;
            f.memoBits[s] = f.bits;
            f.memoRef[s] = f.ref;
        }
    }

    // ======================================================================
    // Structure: list literal, field reference, has()
    // ======================================================================

    /**
     * Each element's kind and homogeneity are checked BEFORE the next element is evaluated, so
     * {@code [1, "a", 1/0]} is TYPE_MISMATCH, not DIVISION_BY_ZERO (§9 item 1; {@code Evaluator.evalListLit}).
     * The elements go into {@code List.of} so {@code ListV}'s defensive {@code List.copyOf} returns the same
     * instance instead of copying again (§9 item 7).
     */
    static final class ListLit extends Node {
        private final Node[] elements;

        ListLit(Node[] elements) {
            this.elements = elements;
        }

        @Override
        void eval(Frame f) {
            CelValue[] values = new CelValue[elements.length];
            byte common = -1;
            for (int i = 0; i < elements.length; i++) {
                elements[i].eval(f);
                byte t = f.tag;
                if (t == ERROR) return;
                if (t > BYTES) { // null, list, object: not admissible elements (§5.3.2)
                    f.err(CelError.TYPE_MISMATCH);
                    return;
                }
                if (common < 0) {
                    common = t;
                } else if (common != t) {
                    f.err(CelError.TYPE_MISMATCH);
                    return;
                }
                values[i] = boxElement(t, f.bits, f.ref);
            }
            f.tag = LIST;
            f.ref = new CelValue.ListV(elementType(common), List.of(values));
        }
    }

    /**
     * Resolution of one selector step (§8.2), kept apart from {@link Evaluator}'s so that a fault in either
     * shows up as a difference between them. Returns the declaring class, {@code null} for absent, or
     * {@link #AMBIGUOUS}. The calls made on {@code current}, and their order, are the walker's.
     */
    private static String resolveStep(String qualClass, String name, CandidateProjection current) {
        if (qualClass != null) {
            if (current.namespaceChain().contains(qualClass) && current.declaresField(qualClass, name)) {
                return qualClass;
            }
            return null;
        }
        String found = null;
        int count = 0;
        for (String cls : current.namespaceChain()) {
            if (current.declaresField(cls, name)) {
                count++;
                if (found == null) found = cls;
            }
        }
        if (count == 0) return null;
        if (count >= 2) return AMBIGUOUS;
        return found;
    }

    /**
     * Value-reference resolution (§8.2-§8.4). {@code isUndecodable()} is asked of the root once on entry and
     * again at the first step, as the walker does; trace equality with sharing off depends on that.
     */
    static final class FieldRef extends Node {
        private final String[] qualClasses; // null element = unqualified step
        private final String[] names;

        FieldRef(String[] qualClasses, String[] names) {
            this.qualClasses = qualClasses;
            this.names = names;
        }

        @Override
        void eval(Frame f) {
            CandidateProjection current = f.candidate;
            if (current.isUndecodable()) {
                f.err(CelError.CANDIDATE_UNDECODABLE);
                return;
            }
            int last = names.length - 1;
            for (int i = 0; ; i++) {
                if (current.isUndecodable()) {
                    f.err(CelError.CANDIDATE_UNDECODABLE);
                    return;
                }
                String cls = resolveStep(qualClasses[i], names[i], current);
                if (cls == AMBIGUOUS) {
                    f.err(CelError.AMBIGUOUS_FIELD);
                    return;
                }
                if (cls == null) {
                    f.err(CelError.ABSENT_FIELD);
                    return;
                }
                CelValue value = current.fieldValue(cls, names[i]);
                if (i == last) {
                    f.value(value); // the final step's null is a NullV value, not an error (§4.6)
                    return;
                }
                if (value instanceof CelValue.NullV) {
                    f.err(CelError.ABSENT_FIELD); // §8.4
                    return;
                }
                if (!(value instanceof CelValue.ObjectV ov)) {
                    f.err(CelError.TYPE_MISMATCH); // §8.4
                    return;
                }
                current = ov.projection();
            }
        }
    }

    /** {@code has(d)} per §6.8's table. */
    static final class Has extends Node {
        private final String[] qualClasses;
        private final String[] names;

        Has(String[] qualClasses, String[] names) {
            this.qualClasses = qualClasses;
            this.names = names;
        }

        @Override
        void eval(Frame f) {
            CandidateProjection current = f.candidate;
            if (current.isUndecodable()) {
                f.err(CelError.CANDIDATE_UNDECODABLE);
                return;
            }
            int last = names.length - 1;
            for (int i = 0; ; i++) {
                if (current.isUndecodable()) {
                    f.err(CelError.CANDIDATE_UNDECODABLE);
                    return;
                }
                String cls = resolveStep(qualClasses[i], names[i], current);
                if (cls == AMBIGUOUS) {
                    f.err(CelError.AMBIGUOUS_FIELD);
                    return;
                }
                if (cls == null) {
                    f.bool(false);
                    return;
                }
                CelValue value = current.fieldValue(cls, names[i]);
                if (i == last) {
                    f.bool(!(value instanceof CelValue.NullV));
                    return;
                }
                if (value instanceof CelValue.NullV) {
                    f.bool(false); // intermediate null -> false
                    return;
                }
                if (!(value instanceof CelValue.ObjectV ov)) {
                    f.err(CelError.TYPE_MISMATCH);
                    return;
                }
                current = ov.projection();
            }
        }
    }

    // ======================================================================
    // Unary and boolean operators (§6.4)
    // ======================================================================

    static final class Not extends Node {
        private final Node operand;

        Not(Node operand) {
            this.operand = operand;
        }

        @Override
        void eval(Frame f) {
            operand.eval(f);
            if (f.tag == ERROR) return;
            if (f.tag != BOOL) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            f.bool(f.bits == 0L);
        }
    }

    static final class Neg extends Node {
        private final Node operand;

        Neg(Node operand) {
            this.operand = operand;
        }

        @Override
        void eval(Frame f) {
            operand.eval(f);
            if (f.tag == ERROR) return;
            if (f.tag == INT) {
                if (f.bits == Long.MIN_VALUE) {
                    f.err(CelError.OVERFLOW); // negate(-2^63)
                } else {
                    f.bits = -f.bits;
                }
                return;
            }
            if (f.tag == DOUBLE) {
                f.dbl(-dbl(f.bits)); // IEEE unary minus: preserves signed zero
                return;
            }
            f.err(CelError.TYPE_MISMATCH);
        }
    }

    /**
     * Both sides are always evaluated, each coerced to bool at its own position, and the left error is
     * pinned unless the right side decides the result ({@code Evaluator.evalAnd}). Short-circuiting here
     * would be outcome-equivalent but observable through the projection trace and through a throwing
     * provider on the right.
     */
    static final class And extends Node {
        private final Node left;
        private final Node right;

        And(Node left, Node right) {
            this.left = left;
            this.right = right;
        }

        @Override
        void eval(Frame f) {
            left.eval(f);
            coerceToBool(f);
            byte lt = f.tag;
            long lb = f.bits;
            Object lr = f.ref;
            right.eval(f);
            coerceToBool(f);
            if (lt != ERROR) {
                if (lb == 0L) f.bool(false); // L=false is determining; otherwise the result is R verbatim
                return;
            }
            if (f.tag != ERROR && f.bits == 0L) { // R=false is determining
                f.bool(false);
                return;
            }
            f.tag = lt; // R=true, or both error: L's error is pinned
            f.bits = lb;
            f.ref = lr;
        }
    }

    static final class Or extends Node {
        private final Node left;
        private final Node right;

        Or(Node left, Node right) {
            this.left = left;
            this.right = right;
        }

        @Override
        void eval(Frame f) {
            left.eval(f);
            coerceToBool(f);
            byte lt = f.tag;
            long lb = f.bits;
            Object lr = f.ref;
            right.eval(f);
            coerceToBool(f);
            if (lt != ERROR) {
                if (lb != 0L) f.bool(true); // L=true is determining; otherwise the result is R verbatim
                return;
            }
            if (f.tag != ERROR && f.bits != 0L) { // R=true is determining
                f.bool(true);
                return;
            }
            f.tag = lt; // R=false, or both error: L's error is pinned
            f.bits = lb;
            f.ref = lr;
        }
    }

    // ======================================================================
    // Equality, ordering, arithmetic (strict constructs: §9.3)
    // ======================================================================

    /**
     * Strict binary constructs evaluate left, return its error, evaluate right, return its error, and only
     * then look at the types. The type check comes last on purpose: a wrong-typed left with a right that
     * errors differently, or throws, must give the right's result.
     */
    static final class Equality extends Node {
        private final Node left;
        private final Node right;
        private final boolean wantEqual;

        Equality(Node left, Node right, boolean wantEqual) {
            this.left = left;
            this.right = right;
            this.wantEqual = wantEqual;
        }

        @Override
        void eval(Frame f) {
            left.eval(f);
            if (f.tag == ERROR) return;
            byte lt = f.tag;
            long lb = f.bits;
            Object lr = f.ref;
            right.eval(f);
            if (f.tag == ERROR) return;
            int eq = equality(lt, lb, lr, f.tag, f.bits, f.ref);
            if (eq == MISMATCH) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            f.bool(wantEqual == (eq == EQUAL)); // != is exactly !(==), including NaN
        }
    }

    static final class Order extends Node {
        private final Node left;
        private final Node right;
        private final int op;

        Order(Node left, Node right, int op) {
            this.left = left;
            this.right = right;
            this.op = op;
        }

        @Override
        void eval(Frame f) {
            left.eval(f);
            if (f.tag == ERROR) return;
            byte lt = f.tag;
            long lb = f.bits;
            Object lr = f.ref;
            right.eval(f);
            if (f.tag == ERROR) return;
            byte rt = f.tag;
            long rb = f.bits;
            Object rr = f.ref;
            boolean result;
            if (lt == INT && rt == INT) {
                result = applyOrder(Long.compare(lb, rb), op);
            } else if (lt == DOUBLE && rt == DOUBLE) {
                result = applyOrder(dbl(lb), dbl(rb), op);
            } else if (lt == INT && rt == DOUBLE) {
                int c = CelSemantics.compareIntDoubleExact(lb, dbl(rb));
                result = c != CelSemantics.UNORDERED && applyOrder(c, op);
            } else if (lt == DOUBLE && rt == INT) {
                int c = CelSemantics.compareIntDoubleExact(rb, dbl(lb));
                result = c != CelSemantics.UNORDERED && applyOrder(-c, op); // c is (int <=> double); we want the reverse
            } else if (lt == STRING && rt == STRING) {
                result = applyOrder(CelSemantics.compareCodePoints(str(lr), str(rr)), op);
            } else if (lt == BYTES && rt == BYTES) {
                result = applyOrder(CelSemantics.compareUnsignedBytes(bytes(lr), bytes(rr)), op);
            } else {
                f.err(CelError.TYPE_MISMATCH); // bool ordering, null, list, object, or any other pairing
                return;
            }
            f.bool(result);
        }
    }

    static final class Arith extends Node {
        private final Node left;
        private final Node right;
        private final int op;

        Arith(Node left, Node right, int op) {
            this.left = left;
            this.right = right;
            this.op = op;
        }

        @Override
        void eval(Frame f) {
            left.eval(f);
            if (f.tag == ERROR) return;
            byte lt = f.tag;
            long lb = f.bits;
            right.eval(f);
            if (f.tag == ERROR) return;
            if (lt == INT && f.tag == INT) {
                intArith(f, lb, f.bits);
            } else if (lt == DOUBLE && f.tag == DOUBLE) {
                doubleArith(f, dbl(lb), dbl(f.bits));
            } else {
                f.err(CelError.TYPE_MISMATCH); // mixed int/double or non-numeric operands (§4.3.2)
            }
        }

        private void intArith(Frame f, long a, long b) {
            try {
                switch (op) {
                    case ADD -> f.integer(Math.addExact(a, b));
                    case SUB -> f.integer(Math.subtractExact(a, b));
                    case MUL -> f.integer(Math.multiplyExact(a, b));
                    case DIV -> {
                        CelError guard = CelSemantics.intDivModError(a, b);
                        if (guard != null) f.err(guard); else f.integer(a / b); // truncates toward zero
                    }
                    default -> {
                        CelError guard = CelSemantics.intDivModError(a, b);
                        if (guard != null) f.err(guard); else f.integer(a % b);
                    }
                }
            } catch (ArithmeticException e) {
                f.err(CelError.OVERFLOW);
            }
        }

        private void doubleArith(Frame f, double a, double b) {
            switch (op) {
                case ADD -> f.dbl(a + b);
                case SUB -> f.dbl(a - b);
                case MUL -> f.dbl(a * b);
                case DIV -> f.dbl(a / b); // IEEE-total: x/0.0 = +-Inf, 0.0/0.0 = NaN
                default -> f.err(CelError.TYPE_MISMATCH); // "%" is not defined for double (§4.3.2)
            }
        }
    }

    // ======================================================================
    // Membership (§6.5) and conditional (§6.7)
    // ======================================================================

    /**
     * A literal list is scanned in order: the first error is remembered and scanning continues, and an
     * EQUAL returns true at once, so {@code x in [1/0, x]} is true. The list is not a ListLit node, so the
     * §5.3.2 homogeneity check never applies to it.
     */
    static final class InLiteral extends Node {
        private final Node needle;
        private final Node[] elements;

        InLiteral(Node needle, Node[] elements) {
            this.needle = needle;
            this.elements = elements;
        }

        @Override
        void eval(Frame f) {
            needle.eval(f);
            if (f.tag == ERROR) return;
            byte nt = f.tag;
            long nb = f.bits;
            Object nr = f.ref;
            CelError firstError = null;
            for (Node element : elements) {
                element.eval(f);
                if (f.tag == ERROR) {
                    if (firstError == null) firstError = (CelError) f.ref;
                    continue;
                }
                int eq = equality(nt, nb, nr, f.tag, f.bits, f.ref);
                if (eq == EQUAL) {
                    f.bool(true);
                    return;
                }
                if (eq == MISMATCH && firstError == null) firstError = CelError.TYPE_MISMATCH;
            }
            if (firstError != null) f.err(firstError); else f.bool(false);
        }
    }

    /** {@code x in field}: "L must be a list<T> ... anything else is TYPE_MISMATCH" (§6.5). */
    static final class InField extends Node {
        private final Node needle;
        private final Node list;

        InField(Node needle, Node list) {
            this.needle = needle;
            this.list = list;
        }

        @Override
        void eval(Frame f) {
            needle.eval(f);
            if (f.tag == ERROR) return;
            byte nt = f.tag;
            long nb = f.bits;
            Object nr = f.ref;
            list.eval(f);
            if (f.tag == ERROR) return;
            if (f.tag != LIST) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            CelError firstError = null;
            for (CelValue element : ((CelValue.ListV) f.ref).elements()) {
                int eq = equalityWithValue(nt, nb, nr, element);
                if (eq == EQUAL) {
                    f.bool(true);
                    return;
                }
                if (eq == MISMATCH && firstError == null) firstError = CelError.TYPE_MISMATCH;
            }
            if (firstError != null) f.err(firstError); else f.bool(false);
        }
    }

    /** Only the taken branch is evaluated. */
    static final class Cond extends Node {
        private final Node condition;
        private final Node thenBranch;
        private final Node elseBranch;

        Cond(Node condition, Node thenBranch, Node elseBranch) {
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override
        void eval(Frame f) {
            condition.eval(f);
            if (f.tag == ERROR) return;
            if (f.tag != BOOL) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            (f.bits != 0L ? thenBranch : elseBranch).eval(f);
        }
    }

    // ======================================================================
    // Platform function calls (§7): one class per function id, so a call has no argument list
    // to allocate and no function-name dispatch. Arguments are evaluated in order, each error
    // returned at once, BEFORE any argument is type-checked.
    // ======================================================================

    /** A function id outside the registry: arguments first, then TYPE_MISMATCH, as {@code Evaluator.evalCall}. */
    static final class CallUnknown extends Node {
        private final Node[] arguments;

        CallUnknown(Node[] arguments) {
            this.arguments = arguments;
        }

        @Override
        void eval(Frame f) {
            for (Node argument : arguments) {
                argument.eval(f);
                if (f.tag == ERROR) return;
            }
            f.err(CelError.TYPE_MISMATCH);
        }
    }

    static final class SizeString extends Node {
        private final Node a;

        SizeString(Node a) {
            this.a = a;
        }

        @Override
        void eval(Frame f) {
            a.eval(f);
            if (f.tag == ERROR) return;
            if (f.tag != STRING) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            f.integer(f.sizeStringCount(str(f.ref)));
        }
    }

    static final class SizeBytes extends Node {
        private final Node a;

        SizeBytes(Node a) {
            this.a = a;
        }

        @Override
        void eval(Frame f) {
            a.eval(f);
            if (f.tag == ERROR) return;
            if (f.tag != BYTES) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            f.integer(bytes(f.ref).length);
        }
    }

    static final class SizeList extends Node {
        private final Node a;

        SizeList(Node a) {
            this.a = a;
        }

        @Override
        void eval(Frame f) {
            a.eval(f);
            if (f.tag == ERROR) return;
            if (f.tag != LIST) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            f.integer(((CelValue.ListV) f.ref).elements().size());
        }
    }

    static final class IntOfDouble extends Node {
        private final Node a;

        IntOfDouble(Node a) {
            this.a = a;
        }

        @Override
        void eval(Frame f) {
            a.eval(f);
            if (f.tag == ERROR) return;
            if (f.tag != DOUBLE) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            double x = dbl(f.bits);
            CelError guard = CelSemantics.intOfDoubleError(x);
            if (guard != null) f.err(guard); else f.integer(CelSemantics.intOfDoubleValue(x));
        }
    }

    static final class DoubleOfInt extends Node {
        private final Node a;

        DoubleOfInt(Node a) {
            this.a = a;
        }

        @Override
        void eval(Frame f) {
            a.eval(f);
            if (f.tag == ERROR) return;
            if (f.tag != INT) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            f.dbl((double) f.bits); // round-to-nearest-even, total
        }
    }

    static final class AbsInt extends Node {
        private final Node a;

        AbsInt(Node a) {
            this.a = a;
        }

        @Override
        void eval(Frame f) {
            a.eval(f);
            if (f.tag == ERROR) return;
            if (f.tag != INT) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            try {
                f.integer(Math.absExact(f.bits));
            } catch (ArithmeticException e) {
                f.err(CelError.OVERFLOW); // abs(-2^63)
            }
        }
    }

    static final class AbsDouble extends Node {
        private final Node a;

        AbsDouble(Node a) {
            this.a = a;
        }

        @Override
        void eval(Frame f) {
            a.eval(f);
            if (f.tag == ERROR) return;
            if (f.tag != DOUBLE) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            f.dbl(Math.abs(dbl(f.bits))); // total: NaN stays NaN, abs(+-Inf) = +Inf
        }
    }

    static final class MinInt extends Node {
        private final Node a;
        private final Node b;

        MinInt(Node a, Node b) {
            this.a = a;
            this.b = b;
        }

        @Override
        void eval(Frame f) {
            a.eval(f);
            if (f.tag == ERROR) return;
            byte at = f.tag;
            long ab = f.bits;
            b.eval(f);
            if (f.tag == ERROR) return;
            if (at != INT || f.tag != INT) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            f.integer(Math.min(ab, f.bits));
        }
    }

    static final class MaxInt extends Node {
        private final Node a;
        private final Node b;

        MaxInt(Node a, Node b) {
            this.a = a;
            this.b = b;
        }

        @Override
        void eval(Frame f) {
            a.eval(f);
            if (f.tag == ERROR) return;
            byte at = f.tag;
            long ab = f.bits;
            b.eval(f);
            if (f.tag == ERROR) return;
            if (at != INT || f.tag != INT) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            f.integer(Math.max(ab, f.bits));
        }
    }

    static final class MinDouble extends Node {
        private final Node a;
        private final Node b;

        MinDouble(Node a, Node b) {
            this.a = a;
            this.b = b;
        }

        @Override
        void eval(Frame f) {
            a.eval(f);
            if (f.tag == ERROR) return;
            byte at = f.tag;
            long ab = f.bits;
            b.eval(f);
            if (f.tag == ERROR) return;
            if (at != DOUBLE || f.tag != DOUBLE) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            double x = dbl(ab);
            double y = dbl(f.bits);
            if (Double.isNaN(x) || Double.isNaN(y)) {
                f.err(CelError.DOMAIN);
                return;
            }
            f.dbl(Math.min(x, y)); // Math.min(-0.0, 0.0) is -0.0, matching §7.2 row 8
        }
    }

    static final class MaxDouble extends Node {
        private final Node a;
        private final Node b;

        MaxDouble(Node a, Node b) {
            this.a = a;
            this.b = b;
        }

        @Override
        void eval(Frame f) {
            a.eval(f);
            if (f.tag == ERROR) return;
            byte at = f.tag;
            long ab = f.bits;
            b.eval(f);
            if (f.tag == ERROR) return;
            if (at != DOUBLE || f.tag != DOUBLE) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            double x = dbl(ab);
            double y = dbl(f.bits);
            if (Double.isNaN(x) || Double.isNaN(y)) {
                f.err(CelError.DOMAIN);
                return;
            }
            f.dbl(Math.max(x, y));
        }
    }

    static final class Sqrt extends Node {
        private final Node a;

        Sqrt(Node a) {
            this.a = a;
        }

        @Override
        void eval(Frame f) {
            a.eval(f);
            if (f.tag == ERROR) return;
            if (f.tag != DOUBLE) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            double x = dbl(f.bits);
            if (!Double.isFinite(x) || x < 0.0) { // -0.0 < 0.0 is false, so sqrt(-0.0) proceeds
                f.err(CelError.DOMAIN);
                return;
            }
            f.dbl(Math.sqrt(x));
        }
    }

    static final class Radians extends Node {
        private final Node a;

        Radians(Node a) {
            this.a = a;
        }

        @Override
        void eval(Frame f) {
            a.eval(f);
            if (f.tag == ERROR) return;
            if (f.tag != DOUBLE) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            double x = dbl(f.bits);
            if (!Double.isFinite(x)) f.err(CelError.DOMAIN); else f.dbl(CelSemantics.radians(x));
        }
    }

    static final class Degrees extends Node {
        private final Node a;

        Degrees(Node a) {
            this.a = a;
        }

        @Override
        void eval(Frame f) {
            a.eval(f);
            if (f.tag == ERROR) return;
            if (f.tag != DOUBLE) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            double x = dbl(f.bits);
            if (!Double.isFinite(x)) f.err(CelError.DOMAIN); else f.dbl(CelSemantics.degrees(x));
        }
    }

    /**
     * The four finite-only unary transcendentals. The domain check runs BEFORE {@code MathProvider.get()},
     * which is called once per evaluation as the walker does, so an absent or lazy provider behaves
     * identically: {@code asin(1.5)} is DOMAIN even with no provider installed.
     */
    static final class Sin extends Node {
        private final Node a;
        private final MathProvider math;

        Sin(Node a, MathProvider math) {
            this.a = a;
            this.math = math;
        }

        @Override
        void eval(Frame f) {
            a.eval(f);
            if (f.tag == ERROR) return;
            if (f.tag != DOUBLE) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            double x = dbl(f.bits);
            if (!Double.isFinite(x)) {
                f.err(CelError.DOMAIN);
                return;
            }
            f.dbl(math.get().sin(x));
        }
    }

    static final class Cos extends Node {
        private final Node a;
        private final MathProvider math;

        Cos(Node a, MathProvider math) {
            this.a = a;
            this.math = math;
        }

        @Override
        void eval(Frame f) {
            a.eval(f);
            if (f.tag == ERROR) return;
            if (f.tag != DOUBLE) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            double x = dbl(f.bits);
            if (!Double.isFinite(x)) {
                f.err(CelError.DOMAIN);
                return;
            }
            f.dbl(math.get().cos(x));
        }
    }

    static final class Tan extends Node {
        private final Node a;
        private final MathProvider math;

        Tan(Node a, MathProvider math) {
            this.a = a;
            this.math = math;
        }

        @Override
        void eval(Frame f) {
            a.eval(f);
            if (f.tag == ERROR) return;
            if (f.tag != DOUBLE) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            double x = dbl(f.bits);
            if (!Double.isFinite(x)) {
                f.err(CelError.DOMAIN);
                return;
            }
            f.dbl(math.get().tan(x));
        }
    }

    static final class Atan extends Node {
        private final Node a;
        private final MathProvider math;

        Atan(Node a, MathProvider math) {
            this.a = a;
            this.math = math;
        }

        @Override
        void eval(Frame f) {
            a.eval(f);
            if (f.tag == ERROR) return;
            if (f.tag != DOUBLE) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            double x = dbl(f.bits);
            if (!Double.isFinite(x)) {
                f.err(CelError.DOMAIN);
                return;
            }
            f.dbl(math.get().atan(x));
        }
    }

    /** {@code asin}/{@code acos}: both the finite check and the {@code |x| > 1} range check precede {@code get()} (§7.2 row 13). */
    static final class Asin extends Node {
        private final Node a;
        private final MathProvider math;

        Asin(Node a, MathProvider math) {
            this.a = a;
            this.math = math;
        }

        @Override
        void eval(Frame f) {
            a.eval(f);
            if (f.tag == ERROR) return;
            if (f.tag != DOUBLE) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            double x = dbl(f.bits);
            if (!Double.isFinite(x) || Math.abs(x) > 1.0) {
                f.err(CelError.DOMAIN);
                return;
            }
            f.dbl(math.get().asin(x));
        }
    }

    static final class Acos extends Node {
        private final Node a;
        private final MathProvider math;

        Acos(Node a, MathProvider math) {
            this.a = a;
            this.math = math;
        }

        @Override
        void eval(Frame f) {
            a.eval(f);
            if (f.tag == ERROR) return;
            if (f.tag != DOUBLE) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            double x = dbl(f.bits);
            if (!Double.isFinite(x) || Math.abs(x) > 1.0) {
                f.err(CelError.DOMAIN);
                return;
            }
            f.dbl(math.get().acos(x));
        }
    }

    static final class Atan2 extends Node {
        private final Node y;
        private final Node x;
        private final MathProvider math;

        Atan2(Node y, Node x, MathProvider math) {
            this.y = y;
            this.x = x;
            this.math = math;
        }

        @Override
        void eval(Frame f) {
            y.eval(f);
            if (f.tag == ERROR) return;
            byte yt = f.tag;
            long yb = f.bits;
            x.eval(f);
            if (f.tag == ERROR) return;
            if (yt != DOUBLE || f.tag != DOUBLE) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            double yv = dbl(yb);
            double xv = dbl(f.bits);
            if (!Double.isFinite(yv) || !Double.isFinite(xv)) {
                f.err(CelError.DOMAIN);
                return;
            }
            f.dbl(math.get().atan2(yv, xv));
        }
    }

    /**
     * {@code contains}/{@code startsWith}/{@code endsWith}: operands are validated well-formed, so a UTF-16
     * char-sequence test coincides with the code-point one (§4.4), and {@code contains("")} is true.
     */
    static final class Contains extends Node {
        private final Node receiver;
        private final Node needle;

        Contains(Node receiver, Node needle) {
            this.receiver = receiver;
            this.needle = needle;
        }

        @Override
        void eval(Frame f) {
            receiver.eval(f);
            if (f.tag == ERROR) return;
            byte rt = f.tag;
            Object rr = f.ref;
            needle.eval(f);
            if (f.tag == ERROR) return;
            if (rt != STRING || f.tag != STRING) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            f.bool(str(rr).contains(str(f.ref)));
        }
    }

    static final class StartsWith extends Node {
        private final Node receiver;
        private final Node prefix;

        StartsWith(Node receiver, Node prefix) {
            this.receiver = receiver;
            this.prefix = prefix;
        }

        @Override
        void eval(Frame f) {
            receiver.eval(f);
            if (f.tag == ERROR) return;
            byte rt = f.tag;
            Object rr = f.ref;
            prefix.eval(f);
            if (f.tag == ERROR) return;
            if (rt != STRING || f.tag != STRING) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            f.bool(str(rr).startsWith(str(f.ref)));
        }
    }

    static final class EndsWith extends Node {
        private final Node receiver;
        private final Node suffix;

        EndsWith(Node receiver, Node suffix) {
            this.receiver = receiver;
            this.suffix = suffix;
        }

        @Override
        void eval(Frame f) {
            receiver.eval(f);
            if (f.tag == ERROR) return;
            byte rt = f.tag;
            Object rr = f.ref;
            suffix.eval(f);
            if (f.tag == ERROR) return;
            if (rt != STRING || f.tag != STRING) {
                f.err(CelError.TYPE_MISMATCH);
                return;
            }
            f.bool(str(rr).endsWith(str(f.ref)));
        }
    }
}
