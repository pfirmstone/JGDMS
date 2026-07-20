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
import au.net.zeus.jgdms.cel.EvalOutcome;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.ast.ExprNode.SelectorStep;
import au.net.zeus.jgdms.cel.math.MathProvider;
import au.net.zeus.jgdms.cel.wire.FunctionRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Tree-walking, total evaluator over the JGDMS-STD-011 AST (§6-§9). Bounded
 * and side-effect-free by construction (§3.2): every method here is either a
 * pure function of its arguments, or a read-only query against a {@link
 * CandidateProjection}.
 * <p>
 * <b>Numeric implementation traps this class deliberately avoids</b> (each
 * named at its definition site in STD-011, restated here because getting any
 * one of them wrong silently breaks determinism, §9.1):
 * <ul>
 *   <li>{@code int} overflow: every checked path uses {@code Math.*Exact}
 *       or an explicit {@code Long.MIN_VALUE}/{@code -1} guard -- never a
 *       raw {@code +}/{@code -}/{@code *}/{@code /}/{@code %}/unary {@code -}
 *       on {@code long} (§4.3.1).</li>
 *   <li>{@code double} ordering/equality: implemented with primitive {@code
 *       ==}/{@code <}/{@code <=}/{@code >}/{@code >=}, never {@code
 *       Double.compare} or boxed {@code Double.equals} (§4.3.4) -- Java's
 *       primitive double operators already give exactly IEEE-754's NaN and
 *       signed-zero semantics, which is precisely why they, and not the
 *       total-order comparator, are correct here.</li>
 *   <li>{@code int}x{@code double} comparison: the exact algorithm of
 *       §4.3.3, never a lossy {@code (double) intValue} widening.</li>
 *   <li>String ordering: by Unicode code point via {@code
 *       String#codePoints()}, never {@code String#compareTo} (§4.4).</li>
 *   <li>{@code int(x: double)}: NaN/±Inf and out-of-range magnitude checked
 *       explicitly before narrowing, never a bare {@code (long) x} cast
 *       (§7.2 row 4's saturating-cast trap).</li>
 *   <li>{@code radians}/{@code degrees}: a single multiply by the pinned
 *       64-bit constant, never {@code Math.toRadians}/{@code toDegrees}
 *       (§7.3).</li>
 * </ul>
 */
public final class Evaluator {

    private final MathProvider mathProvider;

    /** An evaluator with no transcendental provider installed -- any {@code sin}/{@code cos}/... CALL throws. */
    public Evaluator() {
        this(MathProvider.none());
    }

    public Evaluator(MathProvider mathProvider) {
        if (mathProvider == null) throw new NullPointerException("mathProvider");
        this.mathProvider = mathProvider;
    }

    /**
     * Evaluates {@code node} against {@code candidate}, total: yields a
     * value or one of the closed eight errors (§9.1), never an escaping
     * exception for well-formed input -- <em>except</em> for a transcendental
     * {@code CALL} with no {@link MathProvider} installed, which is a
     * deployment/configuration failure the SOW requires to fail loudly
     * (an {@link IllegalStateException}), not to be laundered into a value
     * of the closed error set.
     */
    public EvalOutcome evaluate(ExprNode node, CandidateProjection candidate) {
        EvalOutcome outcome = switch (node) {
            case ExprNode.LitBool b -> EvalOutcome.of(new CelValue.BoolV(b.value()));
            case ExprNode.LitInt i -> EvalOutcome.of(new CelValue.IntV(i.value()));
            case ExprNode.LitDouble d -> EvalOutcome.of(new CelValue.DoubleV(d.value()));
            case ExprNode.LitString s -> EvalOutcome.of(new CelValue.StringV(s.value()));
            case ExprNode.LitBytes by -> EvalOutcome.of(new CelValue.BytesV(by.value()));
            case ExprNode.LitNull ignored -> EvalOutcome.of(CelValue.NullV.INSTANCE);
            case ExprNode.ListLit l -> evalListLit(l, candidate);
            case ExprNode.FieldRef f -> resolveFieldValue(f, candidate);
            case ExprNode.Has h -> evalHas(h, candidate);
            case ExprNode.Not n -> evalNot(n, candidate);
            case ExprNode.Neg n -> evalNeg(n, candidate);
            case ExprNode.And a -> evalAnd(a, candidate);
            case ExprNode.Or o -> evalOr(o, candidate);
            case ExprNode.Eq e -> evalEquality(e.left(), e.right(), candidate, true);
            case ExprNode.Ne e -> evalEquality(e.left(), e.right(), candidate, false);
            case ExprNode.Lt c -> evalOrdering(c.left(), c.right(), candidate, OrderOp.LT);
            case ExprNode.Le c -> evalOrdering(c.left(), c.right(), candidate, OrderOp.LE);
            case ExprNode.Gt c -> evalOrdering(c.left(), c.right(), candidate, OrderOp.GT);
            case ExprNode.Ge c -> evalOrdering(c.left(), c.right(), candidate, OrderOp.GE);
            case ExprNode.Add a -> evalArith(a.left(), a.right(), candidate, ArithOp.ADD);
            case ExprNode.Sub s -> evalArith(s.left(), s.right(), candidate, ArithOp.SUB);
            case ExprNode.Mul m -> evalArith(m.left(), m.right(), candidate, ArithOp.MUL);
            case ExprNode.Div d -> evalArith(d.left(), d.right(), candidate, ArithOp.DIV);
            case ExprNode.Mod m -> evalArith(m.left(), m.right(), candidate, ArithOp.MOD);
            case ExprNode.In in -> evalIn(in, candidate);
            case ExprNode.Cond c -> evalCond(c, candidate);
            case ExprNode.Call c -> evalCall(c, candidate);
        };
        return canonicalizeDoubleResult(outcome);
    }

    /**
     * STD-011 §4.3.4's NaN-canonicalization boundary, applied at every {@code
     * evaluate()} return (i.e. every AST node, not merely the outermost
     * call): a {@code DoubleV} carrying any NaN bit pattern is replaced with
     * the canonical quiet NaN ({@code 0x7FF8000000000000}) before the value
     * is handed back to the caller. This is safe to do this early -- not
     * only at the transform's final result -- because NaN payload bits are
     * unobservable by any operator this evaluator implements: IEEE {@code
     * ==}/ordering treat every NaN identically regardless of payload (§4.3.4),
     * and arithmetic on a NaN operand produces a NaN result whose exact
     * payload is unspecified anyway. Canonicalizing here closes every path a
     * non-canonical NaN payload (e.g. attacker-controlled bits from a
     * candidate field) could otherwise ride out to a DER-encoded result
     * unchanged.
     */
    private static EvalOutcome canonicalizeDoubleResult(EvalOutcome outcome) {
        if (outcome instanceof EvalOutcome.Value v && v.value() instanceof CelValue.DoubleV d) {
            CelValue.DoubleV canonical = d.canonicalizedForResultBoundary();
            if (canonical != d) return EvalOutcome.of(canonical);
        }
        return outcome;
    }

    // ======================================================================
    // Field resolution (§8) and has() (§6.8)
    // ======================================================================

    private record ResolvedField(String className, String fieldName, boolean ambiguous, boolean absent) {
        static ResolvedField found(String c, String f) { return new ResolvedField(c, f, false, false); }
        static final ResolvedField AMBIGUOUS = new ResolvedField(null, null, true, false);
        static final ResolvedField ABSENT = new ResolvedField(null, null, false, true);
    }

    private static ResolvedField resolveStep(SelectorStep step, CandidateProjection current) {
        if (step instanceof SelectorStep.Qual q) {
            if (current.namespaceChain().contains(q.className()) && current.declaresField(q.className(), q.fieldName())) {
                return ResolvedField.found(q.className(), q.fieldName());
            }
            return ResolvedField.ABSENT;
        } else if (step instanceof SelectorStep.Unqual u) {
            String foundClass = null;
            int count = 0;
            for (String cls : current.namespaceChain()) {
                if (current.declaresField(cls, u.name())) {
                    count++;
                    if (foundClass == null) foundClass = cls;
                }
            }
            if (count == 0) return ResolvedField.ABSENT;
            if (count >= 2) return ResolvedField.AMBIGUOUS;
            return ResolvedField.found(foundClass, u.name());
        }
        throw new IllegalStateException("unreachable SelectorStep kind");
    }

    /** Value-reference resolution (§8.2-§8.4): an absent/null intermediate is {@code ABSENT_FIELD}; the final step's null value is returned as {@code NullV}, not an error (§4.6). */
    private EvalOutcome resolveFieldValue(ExprNode.FieldRef fieldRef, CandidateProjection root) {
        if (root.isUndecodable()) return EvalOutcome.error(CelError.CANDIDATE_UNDECODABLE);
        CandidateProjection current = root;
        List<SelectorStep> steps = fieldRef.steps();
        for (int i = 0; i < steps.size(); i++) {
            if (current.isUndecodable()) return EvalOutcome.error(CelError.CANDIDATE_UNDECODABLE);
            ResolvedField rf = resolveStep(steps.get(i), current);
            if (rf.ambiguous()) return EvalOutcome.error(CelError.AMBIGUOUS_FIELD);
            if (rf.absent()) return EvalOutcome.error(CelError.ABSENT_FIELD);
            CelValue value = current.fieldValue(rf.className(), rf.fieldName());
            boolean lastStep = (i == steps.size() - 1);
            if (lastStep) {
                return EvalOutcome.of(value);
            }
            if (value instanceof CelValue.NullV) {
                return EvalOutcome.error(CelError.ABSENT_FIELD); // §8.4: absent/null intermediate -> ABSENT_FIELD
            }
            if (!(value instanceof CelValue.ObjectV ov)) {
                return EvalOutcome.error(CelError.TYPE_MISMATCH); // §8.4: non-object intermediate -> TYPE_MISMATCH
            }
            current = ov.projection();
        }
        throw new IllegalStateException("unreachable: FieldRef.steps is non-empty");
    }

    /** {@code has(d)} per §6.8's full table. */
    private EvalOutcome evalHas(ExprNode.Has has, CandidateProjection root) {
        if (root.isUndecodable()) return EvalOutcome.error(CelError.CANDIDATE_UNDECODABLE);
        CandidateProjection current = root;
        List<SelectorStep> steps = has.target().steps();
        for (int i = 0; i < steps.size(); i++) {
            if (current.isUndecodable()) return EvalOutcome.error(CelError.CANDIDATE_UNDECODABLE);
            ResolvedField rf = resolveStep(steps.get(i), current);
            if (rf.ambiguous()) return EvalOutcome.error(CelError.AMBIGUOUS_FIELD);
            if (rf.absent()) return EvalOutcome.of(new CelValue.BoolV(false));
            CelValue value = current.fieldValue(rf.className(), rf.fieldName());
            boolean lastStep = (i == steps.size() - 1);
            if (lastStep) {
                return EvalOutcome.of(new CelValue.BoolV(!(value instanceof CelValue.NullV)));
            }
            if (value instanceof CelValue.NullV) {
                return EvalOutcome.of(new CelValue.BoolV(false)); // intermediate null -> false
            }
            if (!(value instanceof CelValue.ObjectV ov)) {
                return EvalOutcome.error(CelError.TYPE_MISMATCH); // intermediate non-object -> TYPE_MISMATCH
            }
            current = ov.projection();
        }
        throw new IllegalStateException("unreachable: FieldRef.steps is non-empty");
    }

    // ======================================================================
    // List literal (as a value; §4.7/§5.3.2 dynamic homogeneity check --
    // Appendix B §B.6.6 explicitly assigns this to evaluation-time, not T2)
    // ======================================================================

    private EvalOutcome evalListLit(ExprNode.ListLit listLit, CandidateProjection candidate) {
        List<CelValue> values = new ArrayList<>(listLit.elements().size());
        CelType commonType = null;
        for (ExprNode element : listLit.elements()) {
            EvalOutcome r = evaluate(element, candidate);
            if (r instanceof EvalOutcome.Error) return r; // strict construct (§9.3): first error, no further elements
            CelValue v = ((EvalOutcome.Value) r).value();
            CelType t = v.type();
            if (t != CelType.BOOL && t != CelType.INT && t != CelType.DOUBLE
                    && t != CelType.STRING && t != CelType.BYTES) {
                return EvalOutcome.error(CelError.TYPE_MISMATCH); // null/list/object: not admissible elements (§5.3.2)
            }
            if (commonType == null) {
                commonType = t;
            } else if (commonType != t) {
                return EvalOutcome.error(CelError.TYPE_MISMATCH); // heterogeneous
            }
            values.add(v);
        }
        return EvalOutcome.of(new CelValue.ListV(commonType, values));
    }

    // ======================================================================
    // Boolean operators (§6.4)
    // ======================================================================

    private EvalOutcome evalNot(ExprNode.Not not, CandidateProjection candidate) {
        EvalOutcome r = evaluate(not.operand(), candidate);
        if (r instanceof EvalOutcome.Error) return r;
        CelValue v = ((EvalOutcome.Value) r).value();
        if (v instanceof CelValue.BoolV b) return EvalOutcome.of(new CelValue.BoolV(!b.value()));
        return EvalOutcome.error(CelError.TYPE_MISMATCH);
    }

    private static EvalOutcome coerceToBool(EvalOutcome outcome) {
        if (outcome instanceof EvalOutcome.Value v) {
            if (v.value() instanceof CelValue.BoolV) return outcome;
            return EvalOutcome.error(CelError.TYPE_MISMATCH); // "a non-bool value is first replaced by TYPE_MISMATCH at its own position"
        }
        return outcome;
    }

    /**
     * Both operands are always evaluated (a valid choice within §6.4's "MAY
     * short-circuit" permission -- purity makes the choice unobservable, and
     * always evaluating both avoids any risk of a subtly wrong short-circuit
     * implementation).
     */
    private EvalOutcome evalAnd(ExprNode.And and, CandidateProjection candidate) {
        EvalOutcome l = coerceToBool(evaluate(and.left(), candidate));
        EvalOutcome r = coerceToBool(evaluate(and.right(), candidate));
        if (l instanceof EvalOutcome.Value lv) {
            boolean lb = ((CelValue.BoolV) lv.value()).value();
            if (!lb) return EvalOutcome.of(new CelValue.BoolV(false)); // L=false is determining
            return r; // L=true: result is R verbatim (true/false/error e2)
        }
        // L = error e1
        if (r instanceof EvalOutcome.Value rv) {
            boolean rb = ((CelValue.BoolV) rv.value()).value();
            if (!rb) return EvalOutcome.of(new CelValue.BoolV(false)); // R=false is determining
            return l; // R=true: result is L's error e1
        }
        return l; // both error: left (e1) is pinned
    }

    private EvalOutcome evalOr(ExprNode.Or or, CandidateProjection candidate) {
        EvalOutcome l = coerceToBool(evaluate(or.left(), candidate));
        EvalOutcome r = coerceToBool(evaluate(or.right(), candidate));
        if (l instanceof EvalOutcome.Value lv) {
            boolean lb = ((CelValue.BoolV) lv.value()).value();
            if (lb) return EvalOutcome.of(new CelValue.BoolV(true)); // L=true is determining
            return r; // L=false: result is R verbatim
        }
        // L = error e1
        if (r instanceof EvalOutcome.Value rv) {
            boolean rb = ((CelValue.BoolV) rv.value()).value();
            if (rb) return EvalOutcome.of(new CelValue.BoolV(true)); // R=true is determining
            return l; // R=false: result is L's error e1
        }
        return l; // both error: left (e1) is pinned
    }

    // ======================================================================
    // Equality (§6.1) and ordering (§6.2), incl. exact int x double (§4.3.3)
    // and IEEE NaN/signed-zero handling (§4.3.4)
    // ======================================================================

    private enum EqResult { EQUAL, NOT_EQUAL, TYPE_MISMATCH }

    private static EqResult computeEquals(CelValue l, CelValue r) {
        if (l instanceof CelValue.NullV) {
            return (r instanceof CelValue.NullV) ? EqResult.EQUAL : EqResult.NOT_EQUAL;
        }
        if (r instanceof CelValue.NullV) {
            return EqResult.NOT_EQUAL;
        }
        if (l instanceof CelValue.BoolV lb && r instanceof CelValue.BoolV rb) {
            return lb.value() == rb.value() ? EqResult.EQUAL : EqResult.NOT_EQUAL;
        }
        if (l instanceof CelValue.IntV li && r instanceof CelValue.IntV ri) {
            return li.value() == ri.value() ? EqResult.EQUAL : EqResult.NOT_EQUAL;
        }
        if (l instanceof CelValue.DoubleV ld && r instanceof CelValue.DoubleV rd) {
            // Primitive == : IEEE-correct (NaN==NaN is false; -0.0==0.0 is true). Never Double.equals/compare.
            return ld.value() == rd.value() ? EqResult.EQUAL : EqResult.NOT_EQUAL;
        }
        if (l instanceof CelValue.IntV li2 && r instanceof CelValue.DoubleV rd2) {
            return intDoubleEqual(li2.value(), rd2.value()) ? EqResult.EQUAL : EqResult.NOT_EQUAL;
        }
        if (l instanceof CelValue.DoubleV ld2 && r instanceof CelValue.IntV ri2) {
            return intDoubleEqual(ri2.value(), ld2.value()) ? EqResult.EQUAL : EqResult.NOT_EQUAL;
        }
        if (l instanceof CelValue.StringV ls && r instanceof CelValue.StringV rs) {
            return ls.value().equals(rs.value()) ? EqResult.EQUAL : EqResult.NOT_EQUAL;
        }
        if (l instanceof CelValue.BytesV lby && r instanceof CelValue.BytesV rby) {
            return java.util.Arrays.equals(lby.value(), rby.value()) ? EqResult.EQUAL : EqResult.NOT_EQUAL;
        }
        return EqResult.TYPE_MISMATCH;
    }

    private EvalOutcome evalEquality(ExprNode leftNode, ExprNode rightNode, CandidateProjection candidate, boolean wantEqual) {
        EvalOutcome l = evaluate(leftNode, candidate);
        if (l instanceof EvalOutcome.Error) return l; // strict construct (§9.3)
        EvalOutcome r = evaluate(rightNode, candidate);
        if (r instanceof EvalOutcome.Error) return r;
        CelValue lv = ((EvalOutcome.Value) l).value();
        CelValue rv = ((EvalOutcome.Value) r).value();
        EqResult eq = computeEquals(lv, rv);
        if (eq == EqResult.TYPE_MISMATCH) return EvalOutcome.error(CelError.TYPE_MISMATCH);
        boolean equal = (eq == EqResult.EQUAL);
        return EvalOutcome.of(new CelValue.BoolV(wantEqual == equal)); // != is exactly !(==), incl. NaN
    }

    private enum OrderOp { LT, LE, GT, GE }

    private EvalOutcome evalOrdering(ExprNode leftNode, ExprNode rightNode, CandidateProjection candidate, OrderOp op) {
        EvalOutcome l = evaluate(leftNode, candidate);
        if (l instanceof EvalOutcome.Error) return l;
        EvalOutcome r = evaluate(rightNode, candidate);
        if (r instanceof EvalOutcome.Error) return r;
        CelValue lv = ((EvalOutcome.Value) l).value();
        CelValue rv = ((EvalOutcome.Value) r).value();
        Boolean result = computeOrdering(lv, rv, op);
        if (result == null) return EvalOutcome.error(CelError.TYPE_MISMATCH);
        return EvalOutcome.of(new CelValue.BoolV(result));
    }

    private static Boolean computeOrdering(CelValue l, CelValue r, OrderOp op) {
        if (l instanceof CelValue.IntV li && r instanceof CelValue.IntV ri) {
            return applyOrder(Long.compare(li.value(), ri.value()), op);
        }
        if (l instanceof CelValue.DoubleV ld && r instanceof CelValue.DoubleV rd) {
            double a = ld.value(), b = rd.value();
            return switch (op) { // primitive comparisons: NaN yields false for every one, by IEEE definition
                case LT -> a < b;
                case LE -> a <= b;
                case GT -> a > b;
                case GE -> a >= b;
            };
        }
        if (l instanceof CelValue.IntV li2 && r instanceof CelValue.DoubleV rd2) {
            Integer c = compareIntDoubleExact(li2.value(), rd2.value());
            if (c == null) return false; // rd2 is NaN
            return applyOrder(c, op);
        }
        if (l instanceof CelValue.DoubleV ld2 && r instanceof CelValue.IntV ri2) {
            Integer c = compareIntDoubleExact(ri2.value(), ld2.value());
            if (c == null) return false; // ld2 is NaN
            return applyOrder(-c, op); // c is (int <=> double); we want (double <=> int)
        }
        if (l instanceof CelValue.StringV ls && r instanceof CelValue.StringV rs) {
            return applyOrder(compareCodePoints(ls.value(), rs.value()), op);
        }
        if (l instanceof CelValue.BytesV lb && r instanceof CelValue.BytesV rb) {
            return applyOrder(compareUnsignedBytes(lb.value(), rb.value()), op);
        }
        return null; // TYPE_MISMATCH: bool ordering, null, list, object, or any other unlisted pairing
    }

    private static boolean applyOrder(int cmp, OrderOp op) {
        return switch (op) {
            case LT -> cmp < 0;
            case LE -> cmp <= 0;
            case GT -> cmp > 0;
            case GE -> cmp >= 0;
        };
    }

    /**
     * Exact int64-vs-double comparison per §4.3.3's pinned algorithm.
     * Returns {@code null} if {@code d} is NaN (caller maps that to
     * "not equal" / "every ordered comparison false"); otherwise returns
     * negative/zero/positive as {@code i <=> d}.
     */
    private static Integer compareIntDoubleExact(long i, double d) {
        if (Double.isNaN(d)) return null;
        if (d >= 9223372036854775808.0) return -1; // d >= 2^63: i (<= 2^63-1) is always smaller
        if (d < -9223372036854775808.0) return 1;  // d < -2^63: i (>= -2^63) is always larger
        double floorD = Math.floor(d);
        long floorLong = (long) floorD; // safe: floorD is within [-2^63, 2^63) here
        int cmp = Long.compare(i, floorLong);
        if (cmp != 0) return cmp;
        double frac = d - floorD;
        return (frac > 0.0) ? -1 : 0; // i == floor(d); a positive fractional part makes d strictly greater
    }

    private static boolean intDoubleEqual(long i, double d) {
        Integer c = compareIntDoubleExact(i, d);
        return c != null && c == 0;
    }

    /** Lexicographic Unicode code-point comparison (§4.4) -- never {@code String#compareTo} (UTF-16 code-unit order). */
    private static int compareCodePoints(String a, String b) {
        int[] ca = a.codePoints().toArray();
        int[] cb = b.codePoints().toArray();
        int n = Math.min(ca.length, cb.length);
        for (int i = 0; i < n; i++) {
            if (ca[i] != cb[i]) return Integer.compare(ca[i], cb[i]);
        }
        return Integer.compare(ca.length, cb.length);
    }

    /** Unsigned-octet lexicographic comparison (§4.5). */
    private static int compareUnsignedBytes(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int ai = a[i] & 0xFF;
            int bi = b[i] & 0xFF;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return Integer.compare(a.length, b.length);
    }

    // ======================================================================
    // Arithmetic (§6.3, §4.3.1 checked int, §4.3.2 IEEE double)
    // ======================================================================

    private enum ArithOp { ADD, SUB, MUL, DIV, MOD }

    private EvalOutcome evalArith(ExprNode leftNode, ExprNode rightNode, CandidateProjection candidate, ArithOp op) {
        EvalOutcome l = evaluate(leftNode, candidate);
        if (l instanceof EvalOutcome.Error) return l;
        EvalOutcome r = evaluate(rightNode, candidate);
        if (r instanceof EvalOutcome.Error) return r;
        CelValue lv = ((EvalOutcome.Value) l).value();
        CelValue rv = ((EvalOutcome.Value) r).value();
        if (lv instanceof CelValue.IntV li && rv instanceof CelValue.IntV ri) {
            return intArith(li.value(), ri.value(), op);
        }
        if (lv instanceof CelValue.DoubleV ld && rv instanceof CelValue.DoubleV rd) {
            return doubleArith(ld.value(), rd.value(), op);
        }
        return EvalOutcome.error(CelError.TYPE_MISMATCH); // mixed int/double or non-numeric operands (§4.3.2)
    }

    private static EvalOutcome intArith(long a, long b, ArithOp op) {
        try {
            return switch (op) {
                case ADD -> EvalOutcome.of(new CelValue.IntV(Math.addExact(a, b)));
                case SUB -> EvalOutcome.of(new CelValue.IntV(Math.subtractExact(a, b)));
                case MUL -> EvalOutcome.of(new CelValue.IntV(Math.multiplyExact(a, b)));
                case DIV -> {
                    if (b == 0L) yield EvalOutcome.error(CelError.DIVISION_BY_ZERO);
                    if (a == Long.MIN_VALUE && b == -1L) yield EvalOutcome.error(CelError.OVERFLOW);
                    yield EvalOutcome.of(new CelValue.IntV(a / b)); // truncates toward zero
                }
                case MOD -> {
                    if (b == 0L) yield EvalOutcome.error(CelError.DIVISION_BY_ZERO);
                    // Pinned deliberately (§4.3.1): mathematically 0 and Java's raw % agrees,
                    // but STD-011 pins this case to OVERFLOW for cross-language (Rust) alignment.
                    if (a == Long.MIN_VALUE && b == -1L) yield EvalOutcome.error(CelError.OVERFLOW);
                    yield EvalOutcome.of(new CelValue.IntV(a % b));
                }
            };
        } catch (ArithmeticException e) {
            return EvalOutcome.error(CelError.OVERFLOW);
        }
    }

    private static EvalOutcome doubleArith(double a, double b, ArithOp op) {
        return switch (op) {
            case ADD -> EvalOutcome.of(new CelValue.DoubleV(a + b));
            case SUB -> EvalOutcome.of(new CelValue.DoubleV(a - b));
            case MUL -> EvalOutcome.of(new CelValue.DoubleV(a * b));
            case DIV -> EvalOutcome.of(new CelValue.DoubleV(a / b)); // IEEE-total: x/0.0 = +-Inf, 0.0/0.0 = NaN
            case MOD -> EvalOutcome.error(CelError.TYPE_MISMATCH); // "%" is not defined for double (§4.3.2)
        };
    }

    private EvalOutcome evalNeg(ExprNode.Neg neg, CandidateProjection candidate) {
        EvalOutcome r = evaluate(neg.operand(), candidate);
        if (r instanceof EvalOutcome.Error) return r;
        CelValue v = ((EvalOutcome.Value) r).value();
        if (v instanceof CelValue.IntV iv) {
            try {
                return EvalOutcome.of(new CelValue.IntV(Math.negateExact(iv.value())));
            } catch (ArithmeticException e) {
                return EvalOutcome.error(CelError.OVERFLOW); // negate(-2^63)
            }
        }
        if (v instanceof CelValue.DoubleV dv) {
            return EvalOutcome.of(new CelValue.DoubleV(-dv.value())); // IEEE unary minus: total, preserves signed zero
        }
        return EvalOutcome.error(CelError.TYPE_MISMATCH);
    }

    // ======================================================================
    // Membership (§6.5)
    // ======================================================================

    private EvalOutcome evalIn(ExprNode.In in, CandidateProjection candidate) {
        EvalOutcome needleR = evaluate(in.needle(), candidate);
        if (needleR instanceof EvalOutcome.Error) return needleR;
        CelValue needle = ((EvalOutcome.Value) needleR).value();

        if (in.listOperand() instanceof ExprNode.InListOperand.LiteralList ll) {
            return scanMembershipExpressions(needle, ll.list().elements(), candidate);
        } else if (in.listOperand() instanceof ExprNode.InListOperand.FieldList fl) {
            EvalOutcome listR = resolveFieldValue(fl.field(), candidate);
            if (listR instanceof EvalOutcome.Error) return listR;
            CelValue listVal = ((EvalOutcome.Value) listR).value();
            if (!(listVal instanceof CelValue.ListV lv)) {
                return EvalOutcome.error(CelError.TYPE_MISMATCH); // "L must be a list<T> ... anything else is TYPE_MISMATCH"
            }
            return scanMembershipValues(needle, lv.elements());
        }
        throw new IllegalStateException("unreachable InListOperand kind");
    }

    private EvalOutcome scanMembershipExpressions(CelValue needle, List<ExprNode> elementNodes, CandidateProjection candidate) {
        CelError firstError = null;
        for (ExprNode elementNode : elementNodes) {
            EvalOutcome er = evaluate(elementNode, candidate);
            if (er instanceof EvalOutcome.Error ee) {
                if (firstError == null) firstError = ee.code();
                continue;
            }
            CelValue elementValue = ((EvalOutcome.Value) er).value();
            EqResult eq = computeEquals(needle, elementValue);
            if (eq == EqResult.EQUAL) return EvalOutcome.of(new CelValue.BoolV(true));
            if (eq == EqResult.TYPE_MISMATCH && firstError == null) firstError = CelError.TYPE_MISMATCH;
        }
        return firstError != null ? EvalOutcome.error(firstError) : EvalOutcome.of(new CelValue.BoolV(false));
    }

    private EvalOutcome scanMembershipValues(CelValue needle, List<CelValue> elements) {
        CelError firstError = null;
        for (CelValue elementValue : elements) {
            EqResult eq = computeEquals(needle, elementValue);
            if (eq == EqResult.EQUAL) return EvalOutcome.of(new CelValue.BoolV(true));
            if (eq == EqResult.TYPE_MISMATCH && firstError == null) firstError = CelError.TYPE_MISMATCH;
        }
        return firstError != null ? EvalOutcome.error(firstError) : EvalOutcome.of(new CelValue.BoolV(false));
    }

    // ======================================================================
    // Conditional (§6.7)
    // ======================================================================

    private EvalOutcome evalCond(ExprNode.Cond cond, CandidateProjection candidate) {
        EvalOutcome c = evaluate(cond.condition(), candidate);
        if (c instanceof EvalOutcome.Error) return c;
        CelValue cv = ((EvalOutcome.Value) c).value();
        if (!(cv instanceof CelValue.BoolV bv)) return EvalOutcome.error(CelError.TYPE_MISMATCH);
        return bv.value() ? evaluate(cond.thenBranch(), candidate) : evaluate(cond.elseBranch(), candidate);
    }

    // ======================================================================
    // Platform function calls (§7)
    // ======================================================================

    private static final long C_DEG2RAD_BITS = 0x3F91DF46A2529D39L;
    private static final long C_RAD2DEG_BITS = 0x404CA5DC1A63C1F8L;
    private static final double C_DEG2RAD = Double.longBitsToDouble(C_DEG2RAD_BITS);
    private static final double C_RAD2DEG = Double.longBitsToDouble(C_RAD2DEG_BITS);

    private EvalOutcome evalCall(ExprNode.Call call, CandidateProjection candidate) {
        List<CelValue> args = new ArrayList<>(call.arguments().size());
        for (ExprNode argNode : call.arguments()) {
            EvalOutcome r = evaluate(argNode, candidate);
            if (r instanceof EvalOutcome.Error) return r; // strict construct (§9.3)
            args.add(((EvalOutcome.Value) r).value());
        }
        FunctionRegistry.Entry entry = FunctionRegistry.byId(call.functionId());
        if (entry == null) {
            // Unreachable for a decoder-produced AST (the decoder already validated the
            // id); defensive only, in case an AST is constructed by hand (e.g. in tests).
            return EvalOutcome.error(CelError.TYPE_MISMATCH);
        }
        return switch (entry.name()) {
            case SIZE_STRING -> callSizeString(args.get(0));
            case SIZE_BYTES -> callSizeBytes(args.get(0));
            case SIZE_LIST -> callSizeList(args.get(0));
            case INT_OF_DOUBLE -> callIntOfDouble(args.get(0));
            case DOUBLE_OF_INT -> callDoubleOfInt(args.get(0));
            case ABS_INT -> callAbsInt(args.get(0));
            case ABS_DOUBLE -> callAbsDouble(args.get(0));
            case MIN_INT -> callMinInt(args.get(0), args.get(1));
            case MIN_DOUBLE -> callMinDouble(args.get(0), args.get(1));
            case MAX_INT -> callMaxInt(args.get(0), args.get(1));
            case MAX_DOUBLE -> callMaxDouble(args.get(0), args.get(1));
            case SQRT -> callSqrt(args.get(0));
            case RADIANS -> callRadians(args.get(0));
            case DEGREES -> callDegrees(args.get(0));
            case SIN -> callTranscendental(args.get(0), mathProvider.get()::sin);
            case COS -> callTranscendental(args.get(0), mathProvider.get()::cos);
            case TAN -> callTranscendental(args.get(0), mathProvider.get()::tan);
            case ASIN -> callAsinAcos(args.get(0), mathProvider.get()::asin);
            case ACOS -> callAsinAcos(args.get(0), mathProvider.get()::acos);
            case ATAN -> callTranscendental(args.get(0), mathProvider.get()::atan);
            case ATAN2 -> callAtan2(args.get(0), args.get(1));
            case CONTAINS -> callContains(args.get(0), args.get(1));
            case STARTS_WITH -> callStartsWith(args.get(0), args.get(1));
            case ENDS_WITH -> callEndsWith(args.get(0), args.get(1));
        };
    }

    private static EvalOutcome callSizeString(CelValue v) {
        if (!(v instanceof CelValue.StringV s)) return EvalOutcome.error(CelError.TYPE_MISMATCH);
        return EvalOutcome.of(new CelValue.IntV(s.value().codePointCount(0, s.value().length())));
    }

    private static EvalOutcome callSizeBytes(CelValue v) {
        if (!(v instanceof CelValue.BytesV b)) return EvalOutcome.error(CelError.TYPE_MISMATCH);
        return EvalOutcome.of(new CelValue.IntV(b.value().length));
    }

    private static EvalOutcome callSizeList(CelValue v) {
        if (!(v instanceof CelValue.ListV l)) return EvalOutcome.error(CelError.TYPE_MISMATCH);
        return EvalOutcome.of(new CelValue.IntV(l.elements().size()));
    }

    private static final double LONG_MIN_AS_DOUBLE = -9223372036854775808.0; // -2^63, exact
    private static final double LONG_MAX_EXCLUSIVE_AS_DOUBLE = 9223372036854775808.0; // 2^63, exact

    private static EvalOutcome callIntOfDouble(CelValue v) {
        if (!(v instanceof CelValue.DoubleV d)) return EvalOutcome.error(CelError.TYPE_MISMATCH);
        double x = d.value();
        if (!Double.isFinite(x)) return EvalOutcome.error(CelError.DOMAIN); // never rely on a saturating cast
        double truncated = x < 0.0 ? Math.ceil(x) : Math.floor(x); // truncate toward zero
        if (truncated < LONG_MIN_AS_DOUBLE || truncated >= LONG_MAX_EXCLUSIVE_AS_DOUBLE) {
            return EvalOutcome.error(CelError.OVERFLOW);
        }
        return EvalOutcome.of(new CelValue.IntV((long) truncated));
    }

    private static EvalOutcome callDoubleOfInt(CelValue v) {
        if (!(v instanceof CelValue.IntV i)) return EvalOutcome.error(CelError.TYPE_MISMATCH);
        return EvalOutcome.of(new CelValue.DoubleV((double) i.value())); // round-to-nearest-even, total
    }

    private static EvalOutcome callAbsInt(CelValue v) {
        if (!(v instanceof CelValue.IntV i)) return EvalOutcome.error(CelError.TYPE_MISMATCH);
        try {
            return EvalOutcome.of(new CelValue.IntV(Math.absExact(i.value())));
        } catch (ArithmeticException e) {
            return EvalOutcome.error(CelError.OVERFLOW); // abs(-2^63)
        }
    }

    private static EvalOutcome callAbsDouble(CelValue v) {
        if (!(v instanceof CelValue.DoubleV d)) return EvalOutcome.error(CelError.TYPE_MISMATCH);
        return EvalOutcome.of(new CelValue.DoubleV(Math.abs(d.value()))); // total: NaN stays NaN, abs(+-Inf) = +Inf
    }

    private static EvalOutcome callMinInt(CelValue a, CelValue b) {
        if (!(a instanceof CelValue.IntV ai) || !(b instanceof CelValue.IntV bi)) return EvalOutcome.error(CelError.TYPE_MISMATCH);
        return EvalOutcome.of(new CelValue.IntV(Math.min(ai.value(), bi.value())));
    }

    private static EvalOutcome callMaxInt(CelValue a, CelValue b) {
        if (!(a instanceof CelValue.IntV ai) || !(b instanceof CelValue.IntV bi)) return EvalOutcome.error(CelError.TYPE_MISMATCH);
        return EvalOutcome.of(new CelValue.IntV(Math.max(ai.value(), bi.value())));
    }

    private static EvalOutcome callMinDouble(CelValue a, CelValue b) {
        if (!(a instanceof CelValue.DoubleV ad) || !(b instanceof CelValue.DoubleV bd)) return EvalOutcome.error(CelError.TYPE_MISMATCH);
        if (Double.isNaN(ad.value()) || Double.isNaN(bd.value())) return EvalOutcome.error(CelError.DOMAIN);
        // java.lang.Math.min(double,double) is specified to return -0.0 for min(-0.0, 0.0) -- matches §7.2 row 8 exactly.
        return EvalOutcome.of(new CelValue.DoubleV(Math.min(ad.value(), bd.value())));
    }

    private static EvalOutcome callMaxDouble(CelValue a, CelValue b) {
        if (!(a instanceof CelValue.DoubleV ad) || !(b instanceof CelValue.DoubleV bd)) return EvalOutcome.error(CelError.TYPE_MISMATCH);
        if (Double.isNaN(ad.value()) || Double.isNaN(bd.value())) return EvalOutcome.error(CelError.DOMAIN);
        return EvalOutcome.of(new CelValue.DoubleV(Math.max(ad.value(), bd.value())));
    }

    private static EvalOutcome callSqrt(CelValue v) {
        if (!(v instanceof CelValue.DoubleV d)) return EvalOutcome.error(CelError.TYPE_MISMATCH);
        double x = d.value();
        if (!Double.isFinite(x)) return EvalOutcome.error(CelError.DOMAIN);
        if (x < 0.0) return EvalOutcome.error(CelError.DOMAIN); // -0.0 < 0.0 is false, so sqrt(-0.0) proceeds
        return EvalOutcome.of(new CelValue.DoubleV(Math.sqrt(x))); // IEEE-mandated correctly rounded
    }

    private static EvalOutcome callRadians(CelValue v) {
        if (!(v instanceof CelValue.DoubleV d)) return EvalOutcome.error(CelError.TYPE_MISMATCH);
        double x = d.value();
        if (!Double.isFinite(x)) return EvalOutcome.error(CelError.DOMAIN);
        return EvalOutcome.of(new CelValue.DoubleV(x * C_DEG2RAD)); // pinned single multiply, never Math.toRadians
    }

    private static EvalOutcome callDegrees(CelValue v) {
        if (!(v instanceof CelValue.DoubleV d)) return EvalOutcome.error(CelError.TYPE_MISMATCH);
        double x = d.value();
        if (!Double.isFinite(x)) return EvalOutcome.error(CelError.DOMAIN);
        return EvalOutcome.of(new CelValue.DoubleV(x * C_RAD2DEG)); // pinned single multiply, never Math.toDegrees
    }

    private static EvalOutcome callTranscendental(CelValue v, DoubleUnaryOperator fn) {
        if (!(v instanceof CelValue.DoubleV d)) return EvalOutcome.error(CelError.TYPE_MISMATCH);
        double x = d.value();
        if (!Double.isFinite(x)) return EvalOutcome.error(CelError.DOMAIN);
        return EvalOutcome.of(new CelValue.DoubleV(fn.applyAsDouble(x)));
    }

    private static EvalOutcome callAsinAcos(CelValue v, DoubleUnaryOperator fn) {
        if (!(v instanceof CelValue.DoubleV d)) return EvalOutcome.error(CelError.TYPE_MISMATCH);
        double x = d.value();
        if (!Double.isFinite(x)) return EvalOutcome.error(CelError.DOMAIN);
        if (Math.abs(x) > 1.0) return EvalOutcome.error(CelError.DOMAIN);
        return EvalOutcome.of(new CelValue.DoubleV(fn.applyAsDouble(x)));
    }

    private EvalOutcome callAtan2(CelValue y, CelValue x) {
        if (!(y instanceof CelValue.DoubleV yd) || !(x instanceof CelValue.DoubleV xd)) return EvalOutcome.error(CelError.TYPE_MISMATCH);
        double yv = yd.value();
        double xv = xd.value();
        if (!Double.isFinite(yv) || !Double.isFinite(xv)) return EvalOutcome.error(CelError.DOMAIN);
        return EvalOutcome.of(new CelValue.DoubleV(mathProvider.get().atan2(yv, xv)));
    }

    private static EvalOutcome callContains(CelValue receiver, CelValue needle) {
        if (!(receiver instanceof CelValue.StringV s) || !(needle instanceof CelValue.StringV n)) {
            return EvalOutcome.error(CelError.TYPE_MISMATCH);
        }
        // Both operands are validated well-formed (no lone surrogates), so a UTF-16
        // char-sequence substring test coincides exactly with a code-point-sequence
        // one (§4.4); String#contains also correctly reports true for an empty needle.
        return EvalOutcome.of(new CelValue.BoolV(s.value().contains(n.value())));
    }

    private static EvalOutcome callStartsWith(CelValue receiver, CelValue prefix) {
        if (!(receiver instanceof CelValue.StringV s) || !(prefix instanceof CelValue.StringV p)) {
            return EvalOutcome.error(CelError.TYPE_MISMATCH);
        }
        return EvalOutcome.of(new CelValue.BoolV(s.value().startsWith(p.value())));
    }

    private static EvalOutcome callEndsWith(CelValue receiver, CelValue suffix) {
        if (!(receiver instanceof CelValue.StringV s) || !(suffix instanceof CelValue.StringV suf)) {
            return EvalOutcome.error(CelError.TYPE_MISMATCH);
        }
        return EvalOutcome.of(new CelValue.BoolV(s.value().endsWith(suf.value())));
    }
}
