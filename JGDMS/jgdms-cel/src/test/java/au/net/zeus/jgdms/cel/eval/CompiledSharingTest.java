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
import au.net.zeus.jgdms.cel.math.MathProvider;
import au.net.zeus.jgdms.cel.math.MathProviders;
import au.net.zeus.jgdms.cel.testsupport.FakeCandidateProjection;
import au.net.zeus.jgdms.cel.wire.FunctionRegistry.Name;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static au.net.zeus.jgdms.cel.eval.Ast.b;
import static au.net.zeus.jgdms.cel.eval.Ast.call;
import static au.net.zeus.jgdms.cel.eval.Ast.d;
import static au.net.zeus.jgdms.cel.eval.Ast.div;
import static au.net.zeus.jgdms.cel.eval.Ast.divByZero;
import static au.net.zeus.jgdms.cel.eval.Ast.f;
import static au.net.zeus.jgdms.cel.eval.Ast.i;
import static au.net.zeus.jgdms.cel.eval.Ast.list;
import static au.net.zeus.jgdms.cel.eval.Ast.q;
import static au.net.zeus.jgdms.cel.eval.Ast.s;
import static au.net.zeus.jgdms.cel.testsupport.Msg.msg;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Per-record sharing, and the state a {@link CompiledExpression.Session} carries from one record to the next
 * (design section 3.5 and section 9 items 2-4). Runtime tier: fast.
 * <p>
 * Equivalence with the walker over random ASTs is {@link CompiledEvaluatorDifferentialTest}'s job. These are the
 * deterministic cases it can only hit by luck: an exact memo-hit count on a constructed DAG, a record that
 * throws followed by one that must not see its memo, epochs started on either side of every boundary, the
 * identity {@code size(string)} table's lifetime, the structural key's discriminators, and the API contract.
 */
class CompiledSharingTest {

    private static final CandidateProjection EMPTY = FakeCandidateProjection.builder().build();
    private static final MathProvider PROVIDER = MathProvider.nonConformantForTestingOnly();

    private static CompiledExpression compile(ExprNode e, boolean share) {
        return CompiledExpression.compile(e, PROVIDER, share);
    }

    private static CandidateProjection withInt(long value) {
        return FakeCandidateProjection.builder().field("C", "i", new CelValue.IntV(value)).build();
    }

    private static EvalOutcome intOutcome(long value) {
        return EvalOutcome.of(new CelValue.IntV(value));
    }

    /** {@code (i + 1) * (i + 1)} where the two sums are deep copies, so only merging by structure can share them. */
    private static ExprNode squareOfSuccessor() {
        return new ExprNode.Mul(new ExprNode.Add(f("i"), i(1)), new ExprNode.Add(f("i"), i(1)));
    }

    // ======================================================================
    // The corpus: sharing on against sharing off
    // ======================================================================

    @Test
    void sharingOnAndOff_agreeOverTheCorpus() {
        int cases = 5_000;
        DifferentialHarness.Census census = new DifferentialHarness.Census();
        for (int index = 0; index < cases; index++) {
            DifferentialHarness.runCase(CompiledEvaluatorDifferentialTest.SEED + 17, index, false, census);
        }
        assertTrue(census.astsWithMergedDuplicates > cases / 50, msg("only ", census.astsWithMergedDuplicates,
                " of ", cases, " ASTs had structural duplicates merged"));
        assertTrue(census.memoHits > 0, "the memo was never hit, so sharing on and off were never really different");
        assertTrue(census.evaluations >= (long) cases * CelCorpusGenerator.RECORDS * 2 * 2);
    }

    // ======================================================================
    // A constructed DAG with an exact memo-hit count
    // ======================================================================

    @Test
    void deepCopyDuplicates_areMergedByStructure_andComputedOncePerRecord() {
        CompiledExpression shared = compile(squareOfSuccessor(), true);
        // Mul, Add, FieldRef i, LitInt 1: the seven AST objects collapse to four nodes, and only the Add has two parents.
        assertEquals(4, shared.canonicalNodeCountForTesting());
        assertEquals(1, shared.memoSlotCountForTesting(), "the literal and the field are not memoised: only the Add is");

        CompiledExpression.Session session = shared.newSession();
        assertEquals(intOutcome(16), session.evaluate(withInt(3)));
        assertEquals(1, session.memoHitCountForTesting(), "the second (i+1) is a hit");
        assertEquals(intOutcome(25), session.evaluate(withInt(4)));
        assertEquals(2, session.memoHitCountForTesting());

        CompiledExpression plain = compile(squareOfSuccessor(), false);
        assertEquals(7, plain.canonicalNodeCountForTesting(), "sharing off keeps one node per AST object");
        assertEquals(0, plain.memoSlotCountForTesting());
        CompiledExpression.Session plainSession = plain.newSession();
        assertEquals(intOutcome(16), plainSession.evaluate(withInt(3)));
        assertEquals(0, plainSession.memoHitCountForTesting());
    }

    @Test
    void referenceSharedDag_isComputedOncePerRecord_andTheMathProviderIsCalledOnce() {
        TraceLog.Sink sink = new TraceLog.Sink();
        TraceLog calls = new TraceLog();
        sink.current = calls;
        MathProvider counting = MathProvider.conformant(
                new TraceLog.TracingMath(MathProviders.correctlyRounded().get(), sink));

        ExprNode x = call(Name.SIN, f("d")); // one object, reached from both operands of t
        ExprNode t = new ExprNode.Add(x, x); // one object, reached from both operands of the root
        ExprNode root = new ExprNode.Add(t, t);
        CompiledExpression shared = CompiledExpression.compile(root, counting, true);
        assertEquals(4, shared.canonicalNodeCountForTesting(), "Add, Add, sin, d");
        assertEquals(2, shared.memoSlotCountForTesting(), "x and t each have two parents");

        CompiledExpression.Session session = shared.newSession();
        CandidateProjection candidate = FakeCandidateProjection.builder().field("C", "d", new CelValue.DoubleV(0.5)).build();
        EvalOutcome first = session.evaluate(candidate);
        assertEquals(1, calls.size(), "sin is computed once; the walker computes it four times");
        assertEquals(2, session.memoHitCountForTesting(), "the second x inside t, and the second t");
        session.evaluate(candidate);
        assertEquals(2, calls.size(), "one more record, one more sin");
        assertEquals(4, session.memoHitCountForTesting());

        EvalOutcome walker = new Evaluator(counting).evaluate(root, candidate);
        assertEquals(first, walker);
        assertEquals(6, calls.size(), "the walker's four sin calls");
    }

    @Test
    void anErrorResultIsMemoised_andTheLeftErrorStaysPinned() {
        // && evaluates both sides; the right side is a deep copy of the left, so it is a hit that returns the error.
        ExprNode left = new ExprNode.Eq(divByZero(), i(0));
        ExprNode right = new ExprNode.Eq(divByZero(), i(0));
        CompiledExpression shared = compile(new ExprNode.And(left, right), true);
        assertEquals(5, shared.canonicalNodeCountForTesting(), "And, Eq, Div, 1, 0");
        CompiledExpression.Session session = shared.newSession();
        assertEquals(EvalOutcome.error(CelError.DIVISION_BY_ZERO), session.evaluate(EMPTY));
        assertEquals(1, session.memoHitCountForTesting());
    }

    @Test
    void aSharedNodeInTheUntakenBranchIsNeverEvaluated() {
        // The shared node would throw if reached (no provider); only the taken branch may be evaluated.
        ExprNode sin = call(Name.SIN, d(1.0));
        ExprNode root = new ExprNode.Cond(f("flag"), d(7.0), new ExprNode.Add(sin, call(Name.SIN, d(1.0))));
        CompiledExpression shared = CompiledExpression.compile(root, MathProvider.none(), true);
        CompiledExpression.Session session = shared.newSession();
        CandidateProjection taken = FakeCandidateProjection.builder().field("C", "flag", new CelValue.BoolV(true)).build();
        assertEquals(EvalOutcome.of(new CelValue.DoubleV(7.0)), session.evaluate(taken));
        assertEquals(0, session.memoHitCountForTesting());
    }

    // ======================================================================
    // What carries over between records of one Session
    // ======================================================================

    @Test
    void nothingCarriesOverBetweenRecords_includingAfterAnError() {
        CompiledExpression.Session session = compile(squareOfSuccessor(), true).newSession();
        CandidateProjection three = withInt(3);
        assertEquals(intOutcome(16), session.evaluate(three));
        assertEquals(intOutcome(25), session.evaluate(withInt(4)));
        assertEquals(EvalOutcome.error(CelError.ABSENT_FIELD), session.evaluate(EMPTY));
        assertEquals(intOutcome(36), session.evaluate(withInt(5)), "no stale memo from the record before the error");
        assertEquals(intOutcome(16), session.evaluate(three), "the same projection object again");
    }

    @Test
    void aRecordThatThrows_leavesNoValidMemoForTheNextRecord() {
        // m = i*i is computed and memoised, then the right operand throws when b is true (no provider).
        ExprNode m1 = new ExprNode.Mul(f("i"), f("i"));
        ExprNode m2 = new ExprNode.Mul(f("i"), f("i"));
        ExprNode throwing = new ExprNode.Cond(f("b"),
                call(Name.INT_OF_DOUBLE, call(Name.SIN, f("d"))), i(0));
        ExprNode root = new ExprNode.Add(new ExprNode.Add(m1, m2), throwing);
        CompiledExpression.Session session = CompiledExpression.compile(root, MathProvider.none(), true).newSession();

        CandidateProjection boom = FakeCandidateProjection.builder().field("C", "i", new CelValue.IntV(3))
                .field("C", "b", new CelValue.BoolV(true)).field("C", "d", new CelValue.DoubleV(0.5)).build();
        CandidateProjection fine = FakeCandidateProjection.builder().field("C", "i", new CelValue.IntV(4))
                .field("C", "b", new CelValue.BoolV(false)).field("C", "d", new CelValue.DoubleV(0.5)).build();

        assertThrows(IllegalStateException.class, () -> session.evaluate(boom));
        assertEquals(2, session.memoHitCountForTesting(), "before the throw: the second i inside m, and the second m, were hits");
        assertEquals(intOutcome(32), session.evaluate(fine), "16 + 16 + 0; a stale m from the throwing record would give 18");
    }

    @Test
    void epochBoundaries_neverRevealAStaleMemo() {
        // Values that make a 32-bit epoch wrap, a sign change, zero and the long limits all fall inside the run.
        long[] starts = {0L, -1L, -3L, Integer.MAX_VALUE - 2L, 0xFFFFFFFFL - 2L, (1L << 32) - 2L, Long.MAX_VALUE - 2L,
            Long.MIN_VALUE, Long.MIN_VALUE + 1L, -(1L << 32) - 2L};
        for (long start : starts) {
            CompiledExpression.Session session = compile(squareOfSuccessor(), true).newSession();
            session.startEpochAtForTesting(start);
            for (int record = 0; record < 8; record++) {
                long v = record + 2L;
                assertEquals(intOutcome((v + 1) * (v + 1)), session.evaluate(withInt(v)),
                        msg("epoch started at ", start, ", record ", record));
            }
            assertEquals(8, session.memoHitCountForTesting(), msg("one hit per record, epoch started at ", start));
        }
    }

    /**
     * {@link #epochBoundaries_neverRevealAStaleMemo} cannot fail for an epoch that is too narrow: its seam
     * re-marks every slot and its expression rewrites its only slot on every record, so no slot is ever read
     * at an epoch other than the one that follows its own write. MEASURED 2026-11-14 (mutant pass, M15): an
     * epoch truncated to 32 bits passed all 1,315 tests. Here a slot is written under the taken branch, then
     * 2^32 - 1 records pass that leave it alone, and it must not read as current on the next record.
     */
    @Test
    void aSlotLastWrittenTwoToThe32RecordsAgo_isNotAHit() {
        ExprNode m1 = new ExprNode.Mul(f("i"), f("i"));
        ExprNode m2 = new ExprNode.Mul(f("i"), f("i"));
        ExprNode root = new ExprNode.Cond(f("b"), new ExprNode.Add(m1, m2), i(0));
        CandidateProjection three = FakeCandidateProjection.builder().field("C", "i", new CelValue.IntV(3))
                .field("C", "b", new CelValue.BoolV(true)).build();
        CandidateProjection four = FakeCandidateProjection.builder().field("C", "i", new CelValue.IntV(4))
                .field("C", "b", new CelValue.BoolV(true)).build();
        for (long skipped : new long[] {(1L << 32) - 1L, (1L << 33) - 1L, (1L << 48) - 1L}) {
            CompiledExpression.Session session = compile(root, true).newSession();
            assertEquals(intOutcome(18), session.evaluate(three));
            session.skipEpochsForTesting(skipped);
            assertEquals(intOutcome(32), session.evaluate(four),
                    msg("after ", skipped, " untouched records the slot holding 9 must be recomputed as 16"));
        }
    }

    @Test
    void sizeString_tableIsPerRecord_nothingIsRetainedAcrossRecords() {
        CelValue.StringV text = new CelValue.StringV("hello");
        CandidateProjection candidate = FakeCandidateProjection.builder().field("C", "s", text).build();
        ExprNode root = new ExprNode.Add(call(Name.SIZE_STRING, f("s")), call(Name.SIZE_STRING, q("C", "s")));
        CompiledExpression.Session session = compile(root, true).newSession();

        assertEquals(intOutcome(10), session.evaluate(candidate));
        assertEquals(1, session.sizeStringComputationCountForTesting(), "one scan for two spellings of one String");
        assertEquals(intOutcome(10), session.evaluate(candidate));
        assertEquals(2, session.sizeStringComputationCountForTesting(),
                "the same String instance in the next record is scanned again: the table is cleared at entry");
    }

    // ======================================================================
    // The structural key keeps every distinction the walker makes
    // ======================================================================

    private static void assertKeptApart(String label, ExprNode e, int canonicalNodes, CandidateProjection candidate) {
        CompiledExpression shared = compile(e, true);
        assertEquals(canonicalNodes, shared.canonicalNodeCountForTesting(), label);
        EvalOutcome walker = new Evaluator(PROVIDER).evaluate(e, candidate);
        assertEquals(walker, shared.newSession().evaluate(candidate), label);
    }

    @Test
    void structuralKey_keepsApartWhatTheWalkerDistinguishes() {
        // An int literal and a double literal with the same 64 bits.
        assertKeptApart("literal kind", new ExprNode.Eq(i(0x3FF0000000000000L), d(1.0)), 3, EMPTY);
        // 0.0 and -0.0 are different literals: 1/0.0 is +Inf, 1/-0.0 is -Inf.
        assertKeptApart("signed zero", new ExprNode.Eq(div(d(1.0), d(0.0)), div(d(1.0), d(-0.0))), 6, EMPTY);
        // Long.hashCode(0) == Long.hashCode(-1) == 0: equality, not the hash, decides.
        assertKeptApart("hash collision", new ExprNode.Eq(i(0), i(-1)), 3, EMPTY);
        // A delimiter-joined selector would turn both of these into "a.b.c".
        CandidateProjection dotted = FakeCandidateProjection.builder()
                .field("a.b", "c", new CelValue.IntV(1)).field("a", "b.c", new CelValue.IntV(2)).build();
        assertKeptApart("selector delimiter", new ExprNode.Eq(q("a.b", "c"), q("a", "b.c")), 3, dotted);
        // Same children, different operator.
        assertKeptApart("eq against ne",
                new ExprNode.And(new ExprNode.Eq(f("i"), i(1)), new ExprNode.Ne(f("i"), i(1))), 5, withInt(1));
        // Same argument, different function id.
        assertKeptApart("sin against cos", new ExprNode.Eq(call(Name.SIN, d(0.5)), call(Name.COS, d(0.5))), 4, EMPTY);
        // Equal content, different kind.
        assertKeptApart("string against bytes", new ExprNode.Eq(s("a"), new ExprNode.LitBytes(new byte[] {0x61})), 3, EMPTY);
        // Two spellings of the same field are different nodes: only the identity size() table can join their values.
        CandidateProjection one = FakeCandidateProjection.builder().field("C", "x", new CelValue.IntV(1)).build();
        assertKeptApart("qualified against unqualified", new ExprNode.Eq(f("x"), q("C", "x")), 3, one);
    }

    /**
     * The pairs in {@link #structuralKey_keepsApartWhatTheWalkerDistinguishes} are hand-picked (== against !=,
     * sin against cos). MEASURED 2026-11-14 (mutant pass): a key that gave {@code <} and {@code <=} one node
     * (MG5) and a key without the {@code in} operand kind (MG4) each passed all 1,315 tests. So the kinds are
     * enumerated: every two binary kinds over the same operands, the two unary kinds, has() against the value
     * reference, the two {@code in} operand kinds, and every two function ids of one arity.
     */
    @Test
    void structuralKey_keepsEveryPairOfKindsApart() {
        List<java.util.function.BinaryOperator<ExprNode>> binary = List.<java.util.function.BinaryOperator<ExprNode>>of(
                ExprNode.And::new, ExprNode.Or::new, ExprNode.Eq::new, ExprNode.Ne::new, ExprNode.Lt::new,
                ExprNode.Le::new, ExprNode.Gt::new, ExprNode.Ge::new, ExprNode.Add::new, ExprNode.Sub::new,
                ExprNode.Mul::new, ExprNode.Div::new, ExprNode.Mod::new);
        for (int first = 0; first < binary.size(); first++) {
            for (int second = first + 1; second < binary.size(); second++) {
                for (long value : new long[] {1L, 2L}) {
                    ExprNode root = new ExprNode.Eq(binary.get(first).apply(f("i"), i(1)),
                            binary.get(second).apply(f("i"), i(1)));
                    assertKeptApart(msg("binary kinds ", first, " and ", second, ", i = ", value), root, 5, withInt(value));
                }
            }
        }
        assertKeptApart("! against unary -",
                new ExprNode.Eq(new ExprNode.Not(f("i")), new ExprNode.Neg(f("i"))), 4, withInt(1));
        assertKeptApart("has() against the value reference", new ExprNode.Eq(new ExprNode.Has(f("i")), f("i")), 3, withInt(1));

        // 1 in l is true; 1 in [l] compares an int with a list. && then returns the right side's TYPE_MISMATCH.
        CandidateProjection withList = FakeCandidateProjection.builder()
                .field("C", "l", new CelValue.ListV(CelType.INT, List.of(new CelValue.IntV(1)))).build();
        ExprNode inField = new ExprNode.In(i(1), new ExprNode.InListOperand.FieldList(f("l")));
        ExprNode inLiteral = new ExprNode.In(i(1), new ExprNode.InListOperand.LiteralList(list(f("l"))));
        ExprNode both = new ExprNode.And(inField, inLiteral);
        assertKeptApart("in a field list against in a literal list", both, 5, withList);
        assertEquals(EvalOutcome.error(CelError.TYPE_MISMATCH), compile(both, true).newSession().evaluate(withList));

        for (int id1 = 1; id1 <= 24; id1++) {
            for (int id2 = id1 + 1; id2 <= 24; id2++) {
                int arity = au.net.zeus.jgdms.cel.wire.FunctionRegistry.byId(id1).arity();
                if (arity != au.net.zeus.jgdms.cel.wire.FunctionRegistry.byId(id2).arity()) continue;
                List<ExprNode> args1 = arity == 1 ? List.of(d(0.5)) : List.of(d(0.5), d(0.25));
                List<ExprNode> args2 = arity == 1 ? List.of(d(0.5)) : List.of(d(0.5), d(0.25));
                ExprNode root = new ExprNode.Eq(new ExprNode.Call(id1, args1), new ExprNode.Call(id2, args2));
                assertKeptApart(msg("function ids ", id1, " and ", id2), root, arity == 1 ? 4 : 5, EMPTY);
            }
        }
    }

    /**
     * {@code ExprNode.hashCode} and {@code equals} are structural record methods: on a shared AST one call walks
     * the whole EXPANDED tree. This DAG expands to 2^41 nodes, so hashing or comparing any inner node of it does
     * not return in this lifetime, and compile() finishing at all is the evidence that it visits each object once
     * (identity map) and hashes only keys made of canonical ids. The timeout turns a regression into a failure.
     */
    @Test
    void compile_neverHashesOrComparesAnInnerNode() {
        ExprNode x = i(1);
        for (int level = 0; level < 40; level++) x = new ExprNode.Add(x, x);
        ExprNode root = x;
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            CompiledExpression shared = compile(root, true);
            assertEquals(41, shared.canonicalNodeCountForTesting(), "the literal and 40 sums");
            assertEquals(39, shared.memoSlotCountForTesting(),
                    "every sum below the root has two parents; the literal is never memoised");
            CompiledExpression.Session session = shared.newSession();
            assertEquals(intOutcome(1L << 40), session.evaluate(EMPTY), "2^40, computed in 40 steps, not 2^40");
            assertEquals(39, session.memoHitCountForTesting());
            assertEquals(41, compile(root, false).canonicalNodeCountForTesting(), "sharing off still visits each object once");
        });
    }

    @Test
    void anInLiteralList_isNeverMergedWithAnExpressionListLiteral() {
        // The in-list [1, 2.5] is heterogeneous and fine for `in`; as a value it is TYPE_MISMATCH (§5.3.2).
        // Merging them would make `1 in [1, 2.5]` fail too, and `||` would then return the error instead of true.
        ExprNode asOperand = new ExprNode.In(i(1), new ExprNode.InListOperand.LiteralList(list(i(1), d(2.5))));
        ExprNode asValue = new ExprNode.Gt(call(Name.SIZE_LIST, list(i(1), d(2.5))), i(0));
        ExprNode root = new ExprNode.Or(asOperand, asValue);
        assertKeptApart("in operand against ListLit", root, 8, EMPTY);
        assertEquals(EvalOutcome.of(new CelValue.BoolV(true)), compile(root, true).newSession().evaluate(EMPTY));
    }

    // ======================================================================
    // Root boxing: the register becomes the walker's outcome
    // ======================================================================

    @Test
    void rootBoxing_givesTheWalkersOutcomeForEveryKind() {
        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("C", "n", CelValue.NullV.INSTANCE).field("C", "i", new CelValue.IntV(Long.MIN_VALUE)).build();
        List<ExprNode> roots = List.of(b(true), b(false), i(Long.MAX_VALUE), d(-0.0), d(Double.NaN), s("x"),
                new ExprNode.LitBytes(new byte[] {1, 2}), new ExprNode.LitNull(), f("n"), list(s("a"), s("b")),
                divByZero(), new ExprNode.Neg(f("i")), f("missing"));
        for (ExprNode root : roots) {
            EvalOutcome walker = new Evaluator(PROVIDER).evaluate(root, candidate);
            EvalOutcome compiled = compile(root, true).newSession().evaluate(candidate);
            assertEquals(walker, compiled, String.valueOf(root));
        }
        // DoubleV.equals compares doubleToLongBits, which merges every NaN: check the raw bits directly.
        EvalOutcome.Value negativeZero = (EvalOutcome.Value) compile(d(-0.0), true).newSession().evaluate(EMPTY);
        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(((CelValue.DoubleV) negativeZero.value()).value()));
        EvalOutcome.Value nan = (EvalOutcome.Value) compile(
                new ExprNode.LitDouble(Double.longBitsToDouble(0x7FF0000000000001L)), true).newSession().evaluate(EMPTY);
        assertEquals(CelValue.DoubleV.CANONICAL_NAN_BITS,
                Double.doubleToRawLongBits(((CelValue.DoubleV) nan.value()).value()), "a literal NaN payload is canonicalised");
    }

    @Test
    void rootList_hasTheLiteralsElementTypeAndAnImmutableList() {
        EvalOutcome.Value v = (EvalOutcome.Value) compile(list(i(1), i(2)), true).newSession().evaluate(EMPTY);
        CelValue.ListV l = assertInstanceOf(CelValue.ListV.class, v.value());
        assertEquals(CelType.INT, l.elementType());
        assertEquals(List.of(new CelValue.IntV(1), new CelValue.IntV(2)), l.elements());
        assertThrows(UnsupportedOperationException.class, () -> l.elements().add(new CelValue.IntV(3)));
    }

    // ======================================================================
    // The API contract
    // ======================================================================

    @Test
    void compile_requiresAVerifiedAst_wrongArityIsRejected() {
        int sizeString = Ast.id(Name.SIZE_STRING);
        int minInt = Ast.id(Name.MIN_INT);
        IllegalArgumentException none = assertThrows(IllegalArgumentException.class,
                () -> compile(new ExprNode.Call(sizeString, List.of()), true));
        assertTrue(none.getMessage().contains(msg("function id ", sizeString)), none.getMessage());
        assertThrows(IllegalArgumentException.class, () -> compile(new ExprNode.Call(minInt, List.of(i(1))), true));
        assertThrows(IllegalArgumentException.class, () -> compile(new ExprNode.Call(sizeString, List.of(s("a"), s("b"))), true));
        assertThrows(IllegalArgumentException.class, () -> compile(new ExprNode.Call(minInt, List.of(i(1))), false),
                "sharing off must refuse it too");
        // Nested: the whole AST is checked, not only the root.
        assertThrows(IllegalArgumentException.class, () -> compile(
                new ExprNode.Add(i(1), new ExprNode.Call(minInt, List.of(i(1)))), true));
        // An unregistered id has no arity to check: it compiles and evaluates as the walker does.
        compile(new ExprNode.Call(99, List.of()), true);
    }

    @Test
    void compile_rejectsNull() {
        assertThrows(NullPointerException.class, () -> CompiledExpression.compile(null, PROVIDER));
        assertThrows(NullPointerException.class, () -> CompiledExpression.compile(i(1), null));
    }

    @Test
    void aCompiledExpression_servesSeveralSessionsIndependently() {
        CompiledExpression shared = compile(squareOfSuccessor(), true);
        CompiledExpression.Session a = shared.newSession();
        CompiledExpression.Session b2 = shared.newSession();
        assertEquals(intOutcome(16), a.evaluate(withInt(3)));
        assertEquals(intOutcome(25), b2.evaluate(withInt(4)));
        assertEquals(intOutcome(16), a.evaluate(withInt(3)));
        assertEquals(2, a.memoHitCountForTesting());
        assertEquals(1, b2.memoHitCountForTesting());
    }

    @Test
    void aSessionUsedFromASecondThread_tripsTheOwnerAssertion() throws InterruptedException {
        assumeTrue(CompiledExpression.class.desiredAssertionStatus(), "assertions are disabled: the owner check is off");
        CompiledExpression.Session session = compile(i(1), true).newSession();
        session.evaluate(EMPTY); // this thread becomes the owner
        assertEquals(EvalOutcome.of(new CelValue.IntV(1)), session.evaluate(EMPTY), "the owner may keep using it");

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread other = new Thread(() -> {
            try {
                session.evaluate(EMPTY);
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        other.start();
        other.join();
        assertInstanceOf(AssertionError.class, thrown.get(), "a second thread must be refused, not silently corrupt the register");
    }

    @Test
    void aSessionCreatedOnOneThread_mayBeFirstUsedOnAnother() throws InterruptedException {
        CompiledExpression.Session session = compile(i(1), true).newSession();
        AtomicReference<Object> result = new AtomicReference<>();
        Thread worker = new Thread(() -> result.set(session.evaluate(EMPTY)));
        worker.start();
        worker.join();
        assertEquals(EvalOutcome.of(new CelValue.IntV(1)), result.get());
    }

    @Test
    void anExceptionLeavesTheSessionUsable() {
        CompiledExpression.Session session = CompiledExpression.compile(
                call(Name.SIN, f("d")), MathProvider.none(), true).newSession();
        CandidateProjection withD = FakeCandidateProjection.builder().field("C", "d", new CelValue.DoubleV(0.5)).build();
        assertThrows(IllegalStateException.class, () -> session.evaluate(withD));
        assertEquals(EvalOutcome.error(CelError.ABSENT_FIELD), session.evaluate(EMPTY));
    }
}
