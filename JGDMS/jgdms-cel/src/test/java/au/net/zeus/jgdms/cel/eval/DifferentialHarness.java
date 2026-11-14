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
import au.net.zeus.jgdms.cel.EvalOutcome;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.math.CorrectlyRoundedMath;
import au.net.zeus.jgdms.cel.math.MathProvider;
import au.net.zeus.jgdms.cel.math.MathProviders;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

import static au.net.zeus.jgdms.cel.testsupport.Msg.msg;

/**
 * Runs generated cases through the reference {@link Evaluator} and through the {@link CompiledExpression}
 * with sharing on and off, and fails on the first difference in outcome, in exception, or in the calls made on
 * the projection and the math provider. One case = one generated AST, evaluated over
 * {@link CelCorpusGenerator#RECORDS} records through ONE Session per compiled variant (so memo carry-over
 * between records, including after a record that threw, is under test), once with a conformant provider
 * installed and once with {@code MathProvider.none()}. Test tree only.
 * <p>
 * <b>The comparator is stricter than {@code equals}.</b> {@code CelValue.DoubleV.equals} uses
 * {@code doubleToLongBits}, which merges every NaN, so doubles are compared by raw bits; a list is compared by
 * element type, element count, each element's runtime class and its bits; an exception by class and message;
 * and a result that is a projection-owned string, bytes, list or object must be the projection's own INSTANCE
 * (design section 3.3), while a result that is a literal or a computed value is compared by content, because the
 * compiled evaluator is free to hold its literals pre-built.
 * <p>
 * <b>Trace rule</b> (section 9 item 8). With sharing off the compiled trace must be IDENTICAL to the walker's.
 * With sharing on it must be a SUBSEQUENCE of the walker's with the same first-occurrence order: a merged node
 * is evaluated once where the walker evaluates it at each occurrence, so calls only go missing, never move.
 */
final class DifferentialHarness {

    private DifferentialHarness() {}

    /** One provider for the whole run: CoreMath is stateless apart from its counters, and building one per case costs more than the case. */
    private static final CorrectlyRoundedMath REAL_MATH = MathProviders.correctlyRounded().get();

    /** What the corpus actually reached: the evidence that a pass means something. */
    static final class Census {
        int cases;
        long evaluations;
        final Map<String, Integer> astsWithKind = new TreeMap<>();
        final Map<String, Integer> rootOutcomes = new TreeMap<>();
        int astsWithSharedReference;
        int astsWithMergedDuplicates;
        long memoHits;
        long memoSlots;
        long traceEventsCompared;

        void count(Map<String, Integer> map, String key) {
            map.merge(key, 1, Integer::sum);
        }

        void print(PrintStream out) {
            StringBuilder sb = new StringBuilder();
            sb.append("--- differential census: ").append(cases).append(" ASTs, ").append(evaluations)
                    .append(" evaluations (each AST: ").append(CelCorpusGenerator.RECORDS)
                    .append(" records x 2 provider modes x 3 evaluators), ")
                    .append(traceEventsCompared).append(" trace events compared\n");
            sb.append("ASTs containing each kind:\n");
            astsWithKind.forEach((k, v) -> sb.append("  ").append(k).append(' ').append(v).append('\n'));
            sb.append("compiled (sharing on) root outcomes, summed over records and both provider modes:\n");
            rootOutcomes.forEach((k, v) -> sb.append("  ").append(k).append(' ').append(v).append('\n'));
            sb.append("ASTs with a reference-shared node: ").append(astsWithSharedReference)
                    .append("; with structural duplicates merged by key: ").append(astsWithMergedDuplicates)
                    .append("; memo slots built: ").append(memoSlots).append("; memo hits: ").append(memoHits).append('\n');
            out.print(sb);
        }
    }

    private record Result(EvalOutcome outcome, Throwable thrown) {}

    private static Result run(TraceLog.Sink sink, TraceLog log, Supplier<EvalOutcome> body) {
        sink.current = log;
        try {
            return new Result(body.get(), null);
        } catch (Throwable t) {
            return new Result(null, t);
        } finally {
            sink.current = null;
        }
    }

    /**
     * Runs case {@code index} of the corpus seeded with {@code seed}.
     *
     * @param referenceToo false compares only sharing on against sharing off (the walker is not run)
     */
    static void runCase(long seed, int index, boolean referenceToo, Census census) {
        long caseSeed = seed * 0x9E3779B97F4A7C15L + index;
        TraceLog.Sink sink = new TraceLog.Sink();
        CelCorpusGenerator generator = new CelCorpusGenerator(caseSeed, sink);
        ExprNode ast = generator.expression();

        MathProvider[] providers = {
            MathProvider.conformant(new TraceLog.TracingMath(REAL_MATH, sink)),
            MathProvider.none()};
        String[] modeNames = {"installed", "none"};
        CompiledExpression[] shared = new CompiledExpression[2];
        CompiledExpression[] plain = new CompiledExpression[2];
        CompiledExpression.Session[] sharedSession = new CompiledExpression.Session[2];
        CompiledExpression.Session[] plainSession = new CompiledExpression.Session[2];
        for (int m = 0; m < 2; m++) {
            shared[m] = CompiledExpression.compile(ast, providers[m], true);
            plain[m] = CompiledExpression.compile(ast, providers[m], false);
            sharedSession[m] = shared[m].newSession();
            plainSession[m] = plain[m].newSession();
        }

        for (String kind : CelCorpusGenerator.kindsOf(ast)) census.count(census.astsWithKind, kind);
        int sharedRefs = CelCorpusGenerator.sharedReferences(ast);
        if (sharedRefs > 0) census.astsWithSharedReference++;
        int distinctObjects = distinctObjects(ast);
        if (shared[0].canonicalNodeCountForTesting() < distinctObjects) census.astsWithMergedDuplicates++;
        if (plain[0].canonicalNodeCountForTesting() != distinctObjects) {
            throw new AssertionError(describe(seed, index, -1, "-", ast, msg(
                    "sharing off must keep one node per AST object: ", plain[0].canonicalNodeCountForTesting(),
                    " nodes for ", distinctObjects, " objects")));
        }
        census.memoSlots += shared[0].memoSlotCountForTesting();
        census.cases++;

        for (int r = 0; r < CelCorpusGenerator.RECORDS; r++) {
            GenProjection root = generator.record(r);
            IdentityHashMap<Object, Boolean> owned = new IdentityHashMap<>();
            collectOwned(root, owned, new IdentityHashMap<>());
            for (int m = 0; m < 2; m++) {
                int mode = m;
                TraceLog walkerTrace = new TraceLog();
                TraceLog sharedTrace = new TraceLog();
                TraceLog plainTrace = new TraceLog();
                Result walker = referenceToo
                        ? run(sink, walkerTrace, () -> new Evaluator(providers[mode]).evaluate(ast, root))
                        : null;
                Result compiledShared = run(sink, sharedTrace, () -> sharedSession[mode].evaluate(root));
                Result compiledPlain = run(sink, plainTrace, () -> plainSession[mode].evaluate(root));
                census.evaluations += referenceToo ? 3 : 2;
                census.count(census.rootOutcomes, outcomeKey(modeNames[m], compiledShared));

                String where = modeNames[m];
                String diff = compare(compiledShared, compiledPlain, owned);
                if (diff != null) {
                    throw new AssertionError(describe(seed, index, r, where, ast, msg("sharing on vs off: ", diff)));
                }
                if (!referenceToo) continue;
                diff = compare(walker, compiledPlain, owned);
                if (diff != null) {
                    throw new AssertionError(describe(seed, index, r, where, ast,
                            msg("walker vs compiled (sharing off): ", diff)));
                }
                diff = compare(walker, compiledShared, owned);
                if (diff != null) {
                    throw new AssertionError(describe(seed, index, r, where, ast,
                            msg("walker vs compiled (sharing on): ", diff)));
                }
                if (!walkerTrace.identicalTo(plainTrace)) {
                    throw new AssertionError(describe(seed, index, r, where, ast, msg(
                            "call trace, sharing off, must equal the walker's.\n  walker:   ", walkerTrace.head(40),
                            "\n  compiled: ", plainTrace.head(40))));
                }
                if (!sharedTrace.isSubsequenceOf(walkerTrace)
                        || !new ArrayList<>(sharedTrace.firstOccurrences())
                                .equals(new ArrayList<>(walkerTrace.firstOccurrences()))) {
                    throw new AssertionError(describe(seed, index, r, where, ast, msg(
                            "call trace, sharing on, must be a subsequence of the walker's with the same ",
                            "first-occurrence order.\n  walker:   ", walkerTrace.head(40),
                            "\n  compiled: ", sharedTrace.head(40))));
                }
                census.traceEventsCompared += walkerTrace.size() + sharedTrace.size() + plainTrace.size();
            }
        }
        for (int m = 0; m < 2; m++) census.memoHits += sharedSession[m].memoHitCountForTesting();
    }

    private static String outcomeKey(String mode, Result r) {
        StringBuilder sb = new StringBuilder(mode).append(' ');
        if (r.thrown() != null) return sb.append("THROWN ").append(r.thrown().getClass().getSimpleName()).toString();
        EvalOutcome o = r.outcome();
        if (o instanceof EvalOutcome.Error e) return sb.append("ERROR ").append(e.code()).toString();
        CelValue v = ((EvalOutcome.Value) o).value();
        return sb.append("VALUE ").append(v.type()).toString();
    }

    // ======================================================================
    // Comparison
    // ======================================================================

    /** Null when the two results are the same in every respect the contract fixes; otherwise what differs. */
    static String compare(Result expected, Result actual, IdentityHashMap<Object, Boolean> owned) {
        if (expected.thrown() != null || actual.thrown() != null) {
            if (expected.thrown() == null || actual.thrown() == null) {
                return new StringBuilder("one side threw: expected ").append(describe(expected))
                        .append(", actual ").append(describe(actual)).toString();
            }
            if (expected.thrown().getClass() != actual.thrown().getClass()
                    || !Objects.equals(expected.thrown().getMessage(), actual.thrown().getMessage())) {
                return new StringBuilder("different exception: expected ").append(describe(expected))
                        .append(", actual ").append(describe(actual)).toString();
            }
            return null;
        }
        EvalOutcome e = expected.outcome();
        EvalOutcome a = actual.outcome();
        if (e instanceof EvalOutcome.Error ee) {
            if (a instanceof EvalOutcome.Error ae && ae.code() == ee.code()) return null;
            return new StringBuilder("expected error ").append(ee.code()).append(", actual ").append(a).toString();
        }
        if (a instanceof EvalOutcome.Error) {
            return new StringBuilder("expected value ").append(e).append(", actual ").append(a).toString();
        }
        return compareValue(((EvalOutcome.Value) e).value(), ((EvalOutcome.Value) a).value(), owned, "result");
    }

    private static String describe(Result r) {
        return r.thrown() != null
                ? new StringBuilder(r.thrown().getClass().getName()).append('(').append(r.thrown().getMessage()).append(')').toString()
                : String.valueOf(r.outcome());
    }

    private static String compareValue(CelValue e, CelValue a, IdentityHashMap<Object, Boolean> owned, String path) {
        if (e.getClass() != a.getClass()) {
            return new StringBuilder(path).append(": runtime class ").append(e.getClass().getSimpleName())
                    .append(" vs ").append(a.getClass().getSimpleName()).toString();
        }
        boolean mustBeSameInstance = (e instanceof CelValue.StringV || e instanceof CelValue.BytesV
                || e instanceof CelValue.ListV || e instanceof CelValue.ObjectV) && owned.containsKey(e);
        if (mustBeSameInstance && e != a) {
            return new StringBuilder(path).append(": a projection-owned ").append(e.getClass().getSimpleName())
                    .append(" must come back as the same instance").toString();
        }
        if (!mustBeSameInstance && owned.containsKey(a) && !(e instanceof CelValue.NullV)
                && (a instanceof CelValue.StringV || a instanceof CelValue.BytesV
                    || a instanceof CelValue.ListV || a instanceof CelValue.ObjectV)) {
            return new StringBuilder(path).append(": compiled returned a projection-owned instance the walker did not").toString();
        }
        switch (e) {
            case CelValue.BoolV b -> {
                if (b.value() != ((CelValue.BoolV) a).value()) return mismatch(path, e, a);
            }
            case CelValue.IntV i -> {
                if (i.value() != ((CelValue.IntV) a).value()) return mismatch(path, e, a);
            }
            case CelValue.DoubleV d -> {
                if (Double.doubleToRawLongBits(d.value()) != Double.doubleToRawLongBits(((CelValue.DoubleV) a).value())) {
                    return new StringBuilder(path).append(": double bits 0x")
                            .append(Long.toHexString(Double.doubleToRawLongBits(d.value()))).append(" vs 0x")
                            .append(Long.toHexString(Double.doubleToRawLongBits(((CelValue.DoubleV) a).value()))).toString();
                }
            }
            case CelValue.StringV s -> {
                if (!s.value().equals(((CelValue.StringV) a).value())) return mismatch(path, e, a);
            }
            case CelValue.BytesV b -> {
                if (!Arrays.equals(b.value(), ((CelValue.BytesV) a).value())) return mismatch(path, e, a);
            }
            case CelValue.NullV n -> {
                if (e != a) return mismatch(path, e, a);
            }
            case CelValue.ListV l -> {
                CelValue.ListV al = (CelValue.ListV) a;
                if (l.elementType() != al.elementType()) {
                    return new StringBuilder(path).append(": elementType ").append(l.elementType()).append(" vs ")
                            .append(al.elementType()).toString();
                }
                if (l.elements().size() != al.elements().size()) {
                    return new StringBuilder(path).append(": list length ").append(l.elements().size()).append(" vs ")
                            .append(al.elements().size()).toString();
                }
                for (int i = 0; i < l.elements().size(); i++) {
                    String diff = compareValue(l.elements().get(i), al.elements().get(i), owned,
                            new StringBuilder(path).append('[').append(i).append(']').toString());
                    if (diff != null) return diff;
                }
            }
            case CelValue.ObjectV o -> {
                if (o.projection() != ((CelValue.ObjectV) a).projection()) return mismatch(path, e, a);
            }
        }
        return null;
    }

    private static String mismatch(String path, CelValue e, CelValue a) {
        return new StringBuilder(path).append(": expected ").append(e).append(", actual ").append(a).toString();
    }

    /** Every String, bytes, list, object and null value a projection holds, by instance, including nested objects. */
    private static void collectOwned(GenProjection p, IdentityHashMap<Object, Boolean> owned,
                                     IdentityHashMap<GenProjection, Boolean> seen) {
        if (seen.put(p, Boolean.TRUE) != null) return;
        for (Map<String, CelValue> namespace : p.data.values()) {
            for (CelValue v : namespace.values()) {
                if (v instanceof CelValue.StringV || v instanceof CelValue.BytesV || v instanceof CelValue.ListV
                        || v instanceof CelValue.ObjectV) {
                    owned.put(v, Boolean.TRUE);
                }
                if (v instanceof CelValue.ObjectV o && o.projection() instanceof GenProjection nested) {
                    collectOwned(nested, owned, seen);
                }
            }
        }
    }

    private static int distinctObjects(ExprNode root) {
        IdentityHashMap<ExprNode, Boolean> seen = new IdentityHashMap<>();
        countDistinct(root, seen);
        return seen.size();
    }

    private static void countDistinct(ExprNode n, IdentityHashMap<ExprNode, Boolean> seen) {
        if (seen.put(n, Boolean.TRUE) != null) return;
        for (ExprNode c : CelCorpusGenerator.children(n)) countDistinct(c, seen);
    }

    private static String describe(long seed, int index, int record, String mode, ExprNode ast, String what) {
        String text = String.valueOf(ast);
        if (text.length() > 3000) text = msg(text.substring(0, 3000), "...");
        return new StringBuilder("differential FAILED. seed=").append(seed).append(" case=").append(index)
                .append(" record=").append(record).append(" provider=").append(mode).append("\n  ").append(what)
                .append("\n  AST: ").append(text).toString();
    }

    /** The kinds a healthy corpus must reach, so a generator that degenerates fails instead of passing vacuously. */
    static Set<String> requiredKinds() {
        Set<String> kinds = new TreeSet<>();
        for (String k : new String[] {"LitBool", "LitInt", "LitDouble", "LitString", "LitBytes", "LitNull", "ListLit",
            "FieldRef", "Has", "Not", "Neg", "And", "Or", "Eq", "Ne", "Lt", "Le", "Gt", "Ge", "Add", "Sub", "Mul", "Div",
            "Mod", "In", "Cond", "Call", "In:literal", "In:field"}) {
            kinds.add(k);
        }
        for (int id = 1; id <= 24; id++) kinds.add(new StringBuilder("Call:").append(id).toString());
        return kinds;
    }
}
