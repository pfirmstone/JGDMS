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
import au.net.zeus.jgdms.cel.CelValue;
import au.net.zeus.jgdms.cel.EvalOutcome;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.eval.CompiledNodes.Frame;
import au.net.zeus.jgdms.cel.eval.CompiledNodes.Node;
import au.net.zeus.jgdms.cel.math.MathProvider;

import java.util.Arrays;
import java.util.function.BiFunction;

/**
 * A verified JGDMS-STD-011 expression compiled once and evaluated many times, for filters that run over
 * many candidates. {@link Evaluator} stays the reference: this class must give the identical outcome,
 * make the identical calls on the {@link CandidateProjection} and the {@link MathProvider} (fewer, when
 * sharing skips a repeat), and throw the identical exceptions. The differential test holds it to that.
 * <p>
 * <b>Why it is faster, and what that costs.</b> Results pass through a mutable register (tag, {@code long},
 * {@code Object}) instead of an {@link EvalOutcome} per node, so scalar evaluation allocates nothing and
 * only the root result is boxed. Structurally equal sub-expressions are merged and computed once per
 * record: sound because STD-011 §3.2 makes evaluation side-effect-free and the {@link CandidateProjection}
 * contract returns the identical instance for the identical query within one evaluation. The price is
 * that evaluation is per-thread stateful: a {@link Session} is not thread-safe.
 * <p>
 * <b>Contract.</b> {@link #compile} requires a VERIFIED AST: a call whose argument count does not match
 * {@code FunctionRegistry} throws {@link IllegalArgumentException}, because the walker's behaviour on that
 * input is an index error, not a rule to reproduce. Depth is bounded by the admission ceilings; compile
 * recurses on the AST. An unregistered function id compiles, and evaluates as the walker does
 * (arguments, then TYPE_MISMATCH). A compiled expression is immutable and shared freely between threads.
 */
public final class CompiledExpression {

    private static final EvalOutcome TRUE = EvalOutcome.of(new CelValue.BoolV(true));
    private static final EvalOutcome FALSE = EvalOutcome.of(new CelValue.BoolV(false));
    private static final EvalOutcome NULL = EvalOutcome.of(CelValue.NullV.INSTANCE);
    private static final EvalOutcome[] ERRORS = new EvalOutcome[CelError.values().length];

    static {
        for (CelError e : CelError.values()) ERRORS[e.ordinal()] = EvalOutcome.error(e);
    }

    private final Node root;
    private final int memoSlots;
    private final int sizeStringNodes;
    private final int canonicalNodes;

    private CompiledExpression(CompiledGraph graph) {
        this.root = graph.root();
        this.memoSlots = graph.memoSlots();
        this.sizeStringNodes = graph.sizeStringNodes();
        this.canonicalNodes = graph.canonicalNodes();
    }

    /**
     * Compiles {@code root} for {@code math}. The provider is consulted per transcendental call, as
     * {@link Evaluator} does, so a lazy or absent provider behaves the same.
     *
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if a {@code CALL} has the wrong number of arguments for its function id
     */
    public static CompiledExpression compile(ExprNode root, MathProvider math) {
        return compile(root, math, true);
    }

    /** As {@link #compile(ExprNode, MathProvider)}; {@code share} false turns merging and memoisation off, so evaluation is occurrence by occurrence like the walker's. */
    static CompiledExpression compile(ExprNode root, MathProvider math, boolean share) {
        return compile(root, math, share, null);
    }

    /** As the three-argument form; {@code observer}, when not null, may wrap each executable node (see {@code CompiledGraph.observer}). */
    static CompiledExpression compile(ExprNode root, MathProvider math, boolean share,
                                      BiFunction<ExprNode, Node, Node> observer) {
        if (root == null) throw new NullPointerException("root");
        if (math == null) throw new NullPointerException("mathProvider");
        return new CompiledExpression(CompiledGraph.build(root, math, share, observer));
    }

    /** A new evaluation state for one thread. */
    public Session newSession() {
        return new Session();
    }

    /** The number of memo slots, that is, of sub-expressions reached by two or more parents. */
    int memoSlotCountForTesting() {
        return memoSlots;
    }

    /** The number of distinct nodes after merging. */
    int canonicalNodeCountForTesting() {
        return canonicalNodes;
    }

    /**
     * The evaluation state of one thread: the register, the per-record memo and the per-record
     * {@code size(string)} table. NOT thread-safe and not re-entrant.
     * <p>
     * <b>Retention.</b> Nothing is cleared between records (decision of 2026-11-14; clearing every slot
     * costs O(slots) per record). A memo slot keeps the value it was last written with until that slot is
     * next written, so a slot under a branch that later records do not take can hold a value from a record
     * long past. A Session therefore pins at most one value per memo slot, and the slots are bounded by
     * the node count, at most {@code CelCeilings.MAX_EXPR_NODES} = 1,024. It also holds one String per
     * {@code size(string)} node, the register, and the last candidate. A pinned value can be a whole
     * projected object, so a Session per (filter, thread) that stays idle keeps that data reachable.
     * Design section 9 item 16 accepted "the last record's values", which was narrower than this.
     * <p>
     * <b>Isolation.</b> No stale value is ever visible to a later record: every slot is stamped with the
     * epoch of the record that wrote it, and {@code evaluate} advances the epoch at its entry, so a slot
     * not written by the current record reads as empty (STD-011 section 9.6).
     * <p>
     * The thread that first calls {@link #evaluate} becomes the owner and any other thread trips an
     * {@code assert}: a Session shared across threads corrupts results silently, so the check is
     * enabled in tests and costs nothing when assertions are off. Binding at first use, not at creation,
     * lets a coordinator create Sessions for its workers.
     */
    public final class Session {

        private final Frame frame = new Frame(memoSlots, sizeStringNodes);
        private Thread owner;

        private Session() {}

        /**
         * Evaluates the compiled expression against {@code candidate}, with the contract of
         * {@link Evaluator#evaluate}: a value or one of the closed eight errors, never an escaping exception
         * for a well-formed input, except the {@link IllegalStateException} of a transcendental call with
         * no {@link MathProvider} installed. An exception leaves the Session usable for the next record.
         */
        public EvalOutcome evaluate(CandidateProjection candidate) {
            assert onOwnerThread() : "a CompiledExpression.Session is not thread-safe: one thread per Session";
            Frame f = frame;
            f.epoch++; // at ENTRY: see Frame.epoch
            f.candidate = candidate;
            f.sizeFill = 0;
            root.eval(f);
            return box(f);
        }

        private boolean onOwnerThread() {
            Thread current = Thread.currentThread();
            if (owner == null) owner = current;
            return owner == current;
        }

        /** How many O(len) {@code codePointCount} scans {@code size(string)} has performed in this Session. */
        long sizeStringComputationCountForTesting() {
            return frame.sizeScans;
        }

        /** How many times a memoised sub-expression was reloaded instead of recomputed. */
        long memoHitCountForTesting() {
            return frame.memoHits;
        }

        /**
         * Makes the next evaluation use epoch {@code epoch + 1}, so a test can run records across any
         * boundary. Every memo slot is marked with {@code epoch}, a value no later evaluation will use,
         * so none can read as valid whatever the starting point.
         */
        void startEpochAtForTesting(long epoch) {
            frame.epoch = epoch;
            Arrays.fill(frame.memoEpoch, epoch);
        }

        /**
         * Adds {@code records} to the epoch and leaves every memo slot as it is: the state after that many
         * records which evaluated none of the memoised sub-expressions (an untaken branch). It exists because
         * {@link #startEpochAtForTesting} re-marks every slot, which hides exactly the fault an epoch boundary
         * test is for: a slot that reads as current because the epoch's low bits came round again.
         */
        void skipEpochsForTesting(long records) {
            frame.epoch += records;
        }
    }

    /** The root register becomes an outcome. BOOL, NULL and ERROR share immutable constants: equal under {@code equals} to the walker's fresh ones. The identity of an outcome is not part of the contract, so a caller compares outcomes with {@code equals}. */
    private static EvalOutcome box(Frame f) {
        return switch (f.tag) {
            case CompiledNodes.BOOL -> f.bits != 0L ? TRUE : FALSE;
            case CompiledNodes.INT -> new EvalOutcome.Value(new CelValue.IntV(f.bits));
            case CompiledNodes.DOUBLE -> new EvalOutcome.Value(new CelValue.DoubleV(Double.longBitsToDouble(f.bits)));
            case CompiledNodes.NULL -> NULL;
            case CompiledNodes.ERROR -> ERRORS[((CelError) f.ref).ordinal()];
            default -> new EvalOutcome.Value((CelValue) f.ref); // STRING, BYTES, LIST, OBJECT: the original instance
        };
    }
}
