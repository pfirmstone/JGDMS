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

package au.net.zeus.jgdms.cel.conformance;

import au.net.zeus.jgdms.cel.CelError;
import au.net.zeus.jgdms.cel.CelValue;
import au.net.zeus.jgdms.cel.EvalOutcome;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.eval.CandidateProjection;
import au.net.zeus.jgdms.cel.eval.Evaluator;
import au.net.zeus.jgdms.cel.math.MathProvider;
import au.net.zeus.jgdms.cel.verifier.CelVerifier;
import au.net.zeus.jgdms.cel.verifier.VerificationResult;
import au.net.zeus.jgdms.cel.wire.CelDecodeException;
import au.net.zeus.jgdms.cel.wire.CelDecoder;
import au.net.zeus.jgdms.cel.wire.CelFilterRecord;
import au.net.zeus.jgdms.cel.wire.EvaluationContext;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * T5 of {@code SOW-CEL-Filter-Format.md}: discovers every vector file under
 * the classpath resource {@code conformance/vectors/}, dispatches each
 * vector by its own {@code category} through the REAL public API
 * ({@link CelDecoder}, {@link CelVerifier}, {@link Evaluator}) -- never a
 * reimplementation of decode/verify/eval logic -- and asserts the exact
 * outcome the corpus's {@code expected} field pins.
 * <p>
 * This class is deliberately thin: {@code conformance/CORPUS-FORMAT.md} is
 * the actual specification a Rust runner would build against; this class is
 * one (of, eventually, at least two) runners consuming that same format.
 * Nothing vector-specific lives here -- adding, removing, or editing a
 * vector never requires touching this file.
 * <p>
 * Fails the whole run (not merely a single test) on: an unknown
 * {@code category}, a malformed vector file (bad JSON, wrong top-level
 * shape, a vector missing a required field), a vector {@code id} duplicated
 * anywhere else in the corpus (CORPUS-FORMAT.md Sec 2 -- ids MUST be unique
 * across the entire corpus, not merely per file), or a per-category vector
 * count below this class's pinned minimum (G6 -- corpus truncation
 * protection, {@code CORPUS-FORMAT.md} Sec 4). Every one of those is thrown
 * from {@link #conformanceCorpus()} itself, before any {@link DynamicTest} is
 * even constructed, so it surfaces as a factory-level failure, not lost among
 * per-vector results.
 * <p>
 * <b>Caveat on the pinned-minimum truncation detector (CORPUS-FORMAT.md Sec
 * 4):</b> it is reliable only on a clean build. {@link #discoverVectorFiles()}
 * reads whichever {@code conformance/vectors/*.json} resources are on the
 * test classpath, which on an <em>incremental</em> build can be a stale copy
 * under {@code target/test-classes} left over from before a vector file was
 * pruned -- silently masking exactly the truncation this detector exists to
 * catch. Always run {@code mvn clean test} (or otherwise force a resource
 * re-copy) before trusting a passing minimum-count check as proof nothing
 * was silently deleted.
 */
class CorpusRunnerTest {

    private static final Set<String> KNOWN_CATEGORIES = Set.of(
            "decode-accept", "decode-reject", "verify-reject", "verify-accept-defer", "verify-accept", "eval");

    // Pinned per-category minimums (CORPUS-FORMAT.md Sec 4, G6).
    private static final Map<String, Integer> MIN_COUNTS = Map.of(
            "decode-accept", 50,
            "decode-reject", 30,
            "verify-reject", 9,
            "verify-accept-defer", 3,
            "verify-accept", 1);
    private static final int MIN_EVAL_UNCONDITIONAL = 100;
    private static final int MIN_EVAL_TRANSCENDENTAL = 150;

    private static final String REQUIRES_CONFORMANT_TRANSCENDENTALS = "conformant-transcendentals";

    /**
     * The complete set of {@code requires} gate names this runner knows how
     * to evaluate. CORPUS-FORMAT.md Sec 2's rule: any gate name a vector
     * carries that is NOT in this set is unsatisfiable by construction -- an
     * unrecognized gate is never silently run and never hard-fails the whole
     * suite, exactly like a known-but-currently-unavailable gate (Sec 5's
     * {@code conformant-transcendentals} with no provider installed); it is
     * skipped loudly, naming the gate, same as any other skip.
     */
    private static final Set<String> KNOWN_GATE_NAMES = Set.of(REQUIRES_CONFORMANT_TRANSCENDENTALS);

    @TestFactory
    List<DynamicNode> conformanceCorpus() throws IOException {
        List<Path> files = discoverVectorFiles();
        assertFalse(files.isEmpty(),
                "no corpus vector files discovered under classpath resource conformance/vectors/ "
                        + "-- resource wiring is broken, not merely an empty corpus");

        List<Map<String, Object>> allVectors = new ArrayList<>();
        for (Path file : files) {
            allVectors.addAll(loadVectorFile(file));
        }

        Set<String> seenIds = new java.util.HashSet<>();
        for (Map<String, Object> vector : allVectors) {
            Object category = vector.get("category");
            if (!(category instanceof String) || !KNOWN_CATEGORIES.contains(category)) {
                throw new AssertionError("Unknown/missing category '" + category + "' in vector id="
                        + vector.get("id") + " -- every vector MUST declare one of " + KNOWN_CATEGORIES);
            }
            if (!(vector.get("id") instanceof String) || !(vector.get("wireHex") instanceof String)
                    || !(vector.get("expected") instanceof Map)) {
                throw new AssertionError("Malformed vector (missing id/wireHex/expected): " + vector);
            }
            String id = (String) vector.get("id");
            if (!seenIds.add(id)) {
                // CORPUS-FORMAT.md Sec 2: ids MUST be unique across the ENTIRE corpus, not
                // merely within the file that declares them -- a per-file-only uniqueness
                // check would silently miss a duplicate introduced across two vector files.
                throw new AssertionError("Duplicate vector id across the corpus (ids MUST be "
                        + "unique corpus-wide, not merely per file, CORPUS-FORMAT.md Sec 2): " + id);
            }
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        int evalUnconditional = 0;
        int evalTranscendental = 0;
        int evalOtherGated = 0;
        for (Map<String, Object> vector : allVectors) {
            String category = (String) vector.get("category");
            counts.merge(category, 1, Integer::sum);
            if (category.equals("eval")) {
                List<String> gates = requiresList(vector);
                if (gates.isEmpty()) {
                    evalUnconditional++;
                } else if (gates.contains(REQUIRES_CONFORMANT_TRANSCENDENTALS)) {
                    evalTranscendental++;
                } else {
                    // Gated on something other than conformant-transcendentals (today:
                    // always an as-yet-unrecognized gate name, since that is the only
                    // defined gate) -- tracked separately so it is never miscounted as
                    // "unconditional" (CORPUS-FORMAT.md Sec 2/Sec 5).
                    evalOtherGated++;
                }
            }
        }

        List<String> minimumFailures = new ArrayList<>();
        for (Map.Entry<String, Integer> e : MIN_COUNTS.entrySet()) {
            int actual = counts.getOrDefault(e.getKey(), 0);
            if (actual < e.getValue()) {
                minimumFailures.add(e.getKey() + ": " + actual + " < pinned minimum " + e.getValue());
            }
        }
        if (evalUnconditional < MIN_EVAL_UNCONDITIONAL) {
            minimumFailures.add("eval (unconditional): " + evalUnconditional + " < pinned minimum " + MIN_EVAL_UNCONDITIONAL);
        }
        if (evalTranscendental < MIN_EVAL_TRANSCENDENTAL) {
            minimumFailures.add("eval (requires conformant-transcendentals): " + evalTranscendental
                    + " < pinned minimum " + MIN_EVAL_TRANSCENDENTAL);
        }
        if (!minimumFailures.isEmpty()) {
            throw new AssertionError(
                    "Conformance corpus vector count(s) below pinned minimum -- possible silent "
                            + "truncation (G6). Raise the corpus, or if this is a deliberate change, "
                            + "the minimum in CorpusRunnerTest AND CORPUS-FORMAT.md Sec 4 together:\n  "
                            + String.join("\n  ", minimumFailures));
        }

        Optional<MathProvider> conformantProvider = conformantProviderIfAvailable();
        conformantProvider.ifPresent(p -> assertTrue(p.isConformant(),
                "conformantProviderIfAvailable() returned a MathProvider not built via MathProvider.conformant(...)"));
        Evaluator plainEvaluator = new Evaluator();
        Evaluator conformantEvaluator = conformantProvider.map(Evaluator::new).orElse(null);
        boolean conformantAvailable = conformantProvider.isPresent();

        int[] skippedTranscendental = {0};
        int[] skippedOther = {0};
        List<DynamicNode> nodes = new ArrayList<>();
        for (Map<String, Object> vector : allVectors) {
            String id = (String) vector.get("id");
            List<String> unsatisfied = unsatisfiedGates(vector, conformantAvailable);
            if (!unsatisfied.isEmpty()) {
                boolean onlyTranscendental = unsatisfied.equals(List.of(REQUIRES_CONFORMANT_TRANSCENDENTALS));
                if (onlyTranscendental) {
                    skippedTranscendental[0]++;
                } else {
                    skippedOther[0]++;
                }
                nodes.add(DynamicTest.dynamicTest(id, () -> Assumptions.assumeTrue(false,
                        "SKIPPED: unsatisfiable requires gate(s) " + unsatisfied + " -- CORPUS-FORMAT.md Sec 2/5: "
                                + "an unknown or currently-unavailable gate is always skipped loudly, never run, "
                                + "never a hard suite failure"
                                + (onlyTranscendental
                                        ? " (awaiting T3 phase 2 -- correctly-rounded sin/cos/tan/asin/acos/atan/atan2)"
                                        : ""))));
                continue;
            }
            boolean usesConformant = requiresList(vector).contains(REQUIRES_CONFORMANT_TRANSCENDENTALS);
            Evaluator evaluatorForThisVector = usesConformant ? conformantEvaluator : plainEvaluator;
            nodes.add(DynamicTest.dynamicTest(id, () -> runVector(vector, evaluatorForThisVector)));
        }

        System.out.println("=== DETERMINISTIC CEL conformance corpus: discovered " + allVectors.size() + " vectors ===");
        counts.forEach((k, v) -> System.out.println("  " + k + ": " + v));
        System.out.println("  eval (unconditional): " + evalUnconditional);
        System.out.println("  eval (requires conformant-transcendentals): " + evalTranscendental);
        if (evalOtherGated > 0) {
            System.out.println("  eval (requires an unrecognized/other gate): " + evalOtherGated);
        }
        if (conformantProvider.isPresent()) {
            System.out.println("MathProvider.conformant(...) IS installed -- all " + evalTranscendental
                    + " conformant-transcendentals vectors will be RUN, not skipped.");
        } else {
            System.out.println("SKIPPED " + skippedTranscendental[0] + " eval vectors requiring conformant-transcendentals: "
                    + "MathProvider.none() in effect, no MathProvider.conformant(...) provider available "
                    + "(awaiting T3 phase 2).");
        }
        if (skippedOther[0] > 0) {
            System.out.println("SKIPPED " + skippedOther[0] + " vector(s) requiring an unrecognized/unsatisfiable "
                    + "gate (CORPUS-FORMAT.md Sec 2) -- see each vector's own SKIPPED assumption message for the gate name.");
        }

        return nodes;
    }

    /**
     * The T3-phase-2 extension point: once a genuinely correctly-rounded
     * {@code CorrectlyRoundedMath} implementation exists (STD-011 Sec 7.5),
     * install it here via {@link MathProvider#conformant}. Until then this
     * always returns empty, and every {@code conformant-transcendentals}
     * vector is skipped loudly (never silently passed, never silently
     * dropped) per this class's own report line above.
     */
    private static Optional<MathProvider> conformantProviderIfAvailable() {
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static List<String> requiresList(Map<String, Object> vector) {
        Object requires = vector.get("requires");
        if (!(requires instanceof List)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object g : (List<Object>) requires) {
            out.add((String) g);
        }
        return out;
    }

    /**
     * True iff {@code gate} is a gate name this runner can actually satisfy
     * right now. {@code conformant-transcendentals} is satisfiable iff a
     * conformant {@link MathProvider} is installed; every other gate name
     * (there are none defined yet -- CORPUS-FORMAT.md Sec 2 reserves this for
     * gates not yet invented) is never satisfiable by this runner, by
     * construction, until a future gate is both defined in CORPUS-FORMAT.md
     * AND added to {@link #KNOWN_GATE_NAMES} with real satisfiability logic
     * here.
     */
    private static boolean gateSatisfied(String gate, boolean conformantProviderAvailable) {
        if (!KNOWN_GATE_NAMES.contains(gate)) {
            return false; // unrecognized gate name -- unsatisfiable by definition
        }
        if (REQUIRES_CONFORMANT_TRANSCENDENTALS.equals(gate)) {
            return conformantProviderAvailable;
        }
        throw new IllegalStateException("KNOWN_GATE_NAMES contains a gate with no satisfiability rule wired up: " + gate);
    }

    /**
     * Every gate name {@code vector} declares in {@code requires} that this
     * runner cannot currently satisfy -- empty means "runs for real".
     * CORPUS-FORMAT.md Sec 2's rule, implemented generically rather than as a
     * single hardcoded {@code conformant-transcendentals} special case: an
     * unrecognized gate name is exactly as unsatisfiable as a known-but-
     * unavailable one, and is handled by the identical code path (loud skip,
     * naming the gate, never a hard failure of the whole suite).
     */
    static List<String> unsatisfiedGates(Map<String, Object> vector, boolean conformantProviderAvailable) {
        List<String> gates = requiresList(vector);
        if (gates.isEmpty()) return List.of();
        List<String> unsatisfied = new ArrayList<>();
        for (String gate : gates) {
            if (!gateSatisfied(gate, conformantProviderAvailable)) {
                unsatisfied.add(gate);
            }
        }
        return unsatisfied;
    }

    /**
     * Direct regression coverage for CORPUS-FORMAT.md Sec 2's unknown-gate
     * rule (board-review fix list item 8a), independent of whether the
     * checked-in corpus happens to contain a vector exercising it (it
     * deliberately does not -- manufacturing a permanently-unsatisfiable
     * corpus vector purely to test this rule would itself be a dishonest
     * vector, G12/CORPUS-FORMAT.md Sec 7's honesty-ledger spirit). Exercises {@link
     * #unsatisfiedGates} -- the exact function {@link #conformanceCorpus()}
     * calls per vector -- against synthetic, non-corpus vector maps.
     */
    @Test
    void unknownRequiresGate_isUnsatisfiable_regardlessOfProviderAvailability() {
        Map<String, Object> vector = Map.of("requires", List.of("some-future-gate-nobody-has-defined-yet"));
        assertEquals(List.of("some-future-gate-nobody-has-defined-yet"), unsatisfiedGates(vector, false));
        assertEquals(List.of("some-future-gate-nobody-has-defined-yet"), unsatisfiedGates(vector, true),
                "an unrecognized gate name must stay unsatisfiable even when a conformant "
                        + "MathProvider happens to be installed -- satisfiability is per-gate-name, not global");
    }

    @Test
    void conformantTranscendentalsGate_satisfiabilityTracksProviderAvailability() {
        Map<String, Object> vector = Map.of("requires", List.of(REQUIRES_CONFORMANT_TRANSCENDENTALS));
        assertEquals(List.of(REQUIRES_CONFORMANT_TRANSCENDENTALS), unsatisfiedGates(vector, false));
        assertTrue(unsatisfiedGates(vector, true).isEmpty(), "must be satisfied once a conformant provider is available");
    }

    @Test
    void noRequiresAtAll_isAlwaysSatisfied() {
        assertTrue(unsatisfiedGates(Map.of(), false).isEmpty());
        assertTrue(unsatisfiedGates(Map.of("requires", List.of()), true).isEmpty());
    }

    // ==========================================================================
    // Corpus file discovery / loading
    // ==========================================================================

    private static List<Path> discoverVectorFiles() throws IOException {
        URL url = CorpusRunnerTest.class.getClassLoader().getResource("conformance/vectors");
        if (url == null) {
            throw new AssertionError("classpath resource \"conformance/vectors\" was not found -- "
                    + "test-resource wiring is broken (expected under src/test/resources/conformance/vectors)");
        }
        Path dir;
        try {
            dir = Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        try (Stream<Path> listing = Files.list(dir)) {
            return listing.filter(p -> p.toString().endsWith(".json"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> loadVectorFile(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Object parsed;
        try {
            parsed = MiniJson.parse(text);
        } catch (RuntimeException e) {
            throw new AssertionError("Malformed corpus JSON in " + file + ": " + e.getMessage(), e);
        }
        if (!(parsed instanceof List)) {
            throw new AssertionError("Malformed corpus file " + file + ": top-level JSON value must be an array");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object element : (List<Object>) parsed) {
            if (!(element instanceof Map)) {
                throw new AssertionError("Malformed vector in " + file + ": array element is not a JSON object: " + element);
            }
            result.add((Map<String, Object>) element);
        }
        return result;
    }

    // ==========================================================================
    // Per-vector dispatch
    // ==========================================================================

    @SuppressWarnings("unchecked")
    private void runVector(Map<String, Object> vector, Evaluator evaluatorForEval) throws Exception {
        String category = (String) vector.get("category");
        byte[] wire = Bits.hexToBytes((String) vector.get("wireHex"));
        Map<String, Object> expected = (Map<String, Object>) vector.get("expected");
        switch (category) {
            case "decode-accept" -> runDecodeAccept(wire, expected);
            case "decode-reject" -> runDecodeReject(wire, expected);
            case "verify-reject" -> runVerify(wire, vector, expected);
            case "verify-accept-defer" -> runVerify(wire, vector, expected);
            case "verify-accept" -> runVerify(wire, vector, expected);
            case "eval" -> runEval(wire, vector, expected, evaluatorForEval);
            default -> throw new AssertionError("unknown category (should have been caught earlier): " + category);
        }
    }

    private void runDecodeAccept(byte[] wire, Map<String, Object> expected) throws CelDecodeException {
        CelFilterRecord record;
        try {
            record = CelDecoder.decode(wire);
        } catch (CelDecodeException e) {
            throw new AssertionError("expected decode-accept but CelDecoder rejected: " + e.getMessage(), e);
        }
        assertEquals("accept", expected.get("outcome"));
        Object expectedContext = expected.get("context");
        if (expectedContext != null) {
            String actual = (record.context() instanceof EvaluationContext.Predicate) ? "predicate" : "transform";
            assertEquals(expectedContext, actual, "EvaluationContext mismatch");
        }
        Object expectedRootKind = expected.get("rootNodeKind");
        if (expectedRootKind != null) {
            assertEquals(expectedRootKind, nodeKindName(record.expression()), "root ExprNode kind mismatch");
        }
    }

    private void runDecodeReject(byte[] wire, Map<String, Object> expected) {
        assertEquals("reject", expected.get("outcome"));
        assertThrows(CelDecodeException.class, () -> CelDecoder.decode(wire),
                "expected decode-reject (reasonClass=" + expected.get("reasonClass") + ") but decode succeeded");
    }

    @SuppressWarnings("unchecked")
    private void runVerify(byte[] wire, Map<String, Object> vector, Map<String, Object> expected) {
        Object schemaJson = vector.get("schema");
        VerificationResult result = (schemaJson instanceof Map)
                ? CelVerifier.verify(wire, PureSchemaView.fromJson((Map<String, Object>) schemaJson))
                : CelVerifier.verify(wire);

        String outcome = (String) expected.get("outcome");
        switch (outcome) {
            case "accept" -> assertTrue(result.accepted(), () -> "expected accept but rejected: "
                    + (result.accepted() ? "" : result.reason() + " -- " + result.diagnostic()));
            case "reject" -> {
                assertFalse(result.accepted(), "expected reject but accepted (cost=" + result.cost() + ")");
                assertEquals(expected.get("reason"), result.reason().name(), "verify rejection reason mismatch");
            }
            default -> fail("unknown verify expected outcome: " + outcome);
        }
    }

    @SuppressWarnings("unchecked")
    private void runEval(byte[] wire, Map<String, Object> vector, Map<String, Object> expected, Evaluator evaluator)
            throws CelDecodeException {
        CelFilterRecord record;
        try {
            record = CelDecoder.decode(wire);
        } catch (CelDecodeException e) {
            throw new AssertionError("eval vector's own wireHex failed to decode -- malformed corpus vector "
                    + "(id=" + vector.get("id") + "): " + e.getMessage(), e);
        }
        CandidateProjection candidate = PureCandidateProjection.fromJson((Map<String, Object>) vector.get("candidate"));
        EvalOutcome outcome = evaluator.evaluate(record.expression(), candidate);

        String expOutcome = (String) expected.get("outcome");
        switch (expOutcome) {
            case "value" -> {
                assertTrue(outcome.isValue(), () -> "expected a value but got error "
                        + (outcome.isError() ? outcome.errorOrThrow() : ""));
                assertValueMatches(expected, outcome.valueOrThrow());
                assertNeverCostBoundWhenVerifiedAccepted(wire, outcome);
            }
            case "error" -> {
                assertTrue(outcome.isError(), () -> "expected error " + expected.get("code") + " but got value "
                        + (outcome.isValue() ? outcome.valueOrThrow() : ""));
                CelError expectedCode = CelError.valueOf((String) expected.get("code"));
                assertEquals(expectedCode, outcome.errorOrThrow(), "eval error code mismatch");
            }
            default -> fail("unknown eval expected outcome: " + expOutcome);
        }
    }

    /**
     * STD-011 Sec 10.5's mandated corpus assertion (board-review fix list
     * item 7): a wire whose {@code CelVerifier.verify} accepts it (no
     * governing schema -- the same no-schema overload every eval vector's
     * candidate is otherwise independent of) MUST NOT evaluate to {@code
     * COST_BOUND} -- Sec 10.5's guarantee is that a verified expression's
     * cost was already statically bounded under {@code maxExprCost}, so a
     * runtime cost meter (if one exists at all; {@code Evaluator} does not
     * meter cost today) can never have anything left to trip. Scoped to
     * value-outcome vectors per the fix list; harmless to call on every one
     * (verification is read-only and side-effect-free) and never changes any
     * vector's own expected outcome -- this is purely an additional
     * assertion layered on top of the existing eval-outcome check.
     */
    private void assertNeverCostBoundWhenVerifiedAccepted(byte[] wire, EvalOutcome outcome) {
        VerificationResult verification = CelVerifier.verify(wire);
        if (verification.accepted()) {
            assertFalse(outcome.isError() && outcome.errorOrThrow() == CelError.COST_BOUND,
                    "STD-011 Sec 10.5 violation: CelVerifier.verify(wire) accepted this expression "
                            + "(cost statically bounded under maxExprCost) but evaluation yielded COST_BOUND anyway");
        }
    }

    private void assertValueMatches(Map<String, Object> expected, CelValue actual) {
        String type = (String) expected.get("type");
        switch (type) {
            case "BOOL" -> {
                assertTrue(actual instanceof CelValue.BoolV, () -> "expected BOOL, got " + actual);
                assertEquals(expected.get("value"), ((CelValue.BoolV) actual).value());
            }
            case "INT" -> {
                assertTrue(actual instanceof CelValue.IntV, () -> "expected INT, got " + actual);
                long expectedValue = Long.parseLong((String) expected.get("value"));
                assertEquals(expectedValue, ((CelValue.IntV) actual).value());
            }
            case "DOUBLE" -> {
                assertTrue(actual instanceof CelValue.DoubleV, () -> "expected DOUBLE, got " + actual);
                long expectedBits = Bits.parseUnsignedHexLong((String) expected.get("value"));
                long actualBits = Double.doubleToRawLongBits(((CelValue.DoubleV) actual).value());
                assertEquals(expectedBits, actualBits, () -> String.format(
                        "double bit-pattern mismatch: expected 0x%016x, got 0x%016x", expectedBits, actualBits));
            }
            case "STRING" -> {
                assertTrue(actual instanceof CelValue.StringV, () -> "expected STRING, got " + actual);
                assertEquals(expected.get("value"), ((CelValue.StringV) actual).value());
            }
            case "BYTES" -> {
                assertTrue(actual instanceof CelValue.BytesV, () -> "expected BYTES, got " + actual);
                assertArrayEquals(Bits.hexToBytes((String) expected.get("value")), ((CelValue.BytesV) actual).value());
            }
            case "NULL" -> assertTrue(actual instanceof CelValue.NullV, () -> "expected NULL, got " + actual);
            case "LIST" -> assertListMatches(expected, actual);
            case "OBJECT" -> assertObjectMatches(expected, actual);
            default -> fail("unsupported expected value \"type\" in eval vector: " + type);
        }
    }

    /**
     * CORPUS-FORMAT.md Sec 3.6's LIST expected shape (board-review fix list
     * item 1): {@code elementType} (one of the five scalar types) plus
     * {@code elements}, an ordered array of bare values in that element
     * type's own convention (Sec 2.1/2.3) -- exactly {@code CelValue.ListV}'s
     * own shape. Each element is re-dispatched through {@link
     * #assertValueMatches} itself (wrapped as a one-off {@code
     * {type, value}} typed value), so every scalar type's own comparison
     * logic (bit-exact double, unsigned-hex bytes, etc.) is reused verbatim
     * rather than re-implemented here.
     */
    @SuppressWarnings("unchecked")
    private void assertListMatches(Map<String, Object> expected, CelValue actual) {
        assertTrue(actual instanceof CelValue.ListV, () -> "expected LIST, got " + actual);
        CelValue.ListV listValue = (CelValue.ListV) actual;
        String elementType = (String) expected.get("elementType");
        assertEquals(elementType, listValue.elementType().name(), "list elementType mismatch");
        List<Object> rawElements = (List<Object>) expected.getOrDefault("elements", List.of());
        assertEquals(rawElements.size(), listValue.elements().size(), "list length mismatch");
        for (int i = 0; i < rawElements.size(); i++) {
            Map<String, Object> elementTypedValue = new LinkedHashMap<>();
            elementTypedValue.put("type", elementType);
            elementTypedValue.put("value", rawElements.get(i));
            assertValueMatches(elementTypedValue, listValue.elements().get(i));
        }
    }

    /**
     * CORPUS-FORMAT.md Sec 3.6's OBJECT expected shape (board-review fix
     * list item 1): a {@code projection} field in exactly Sec 2.3's own
     * nested candidate-projection shape ({@code namespaceChain} + {@code
     * fields}). Checked field-by-field against the actual {@link
     * CandidateProjection} the evaluated {@code CelValue.ObjectV} wraps --
     * every field the expected projection declares MUST be present and MUST
     * match; fields the expected projection omits are simply not checked
     * (this is an assertion of "at least this shape", not a full projection
     * equality -- there is no {@code CandidateProjection} enumeration API to
     * compare against exhaustively, by design, see {@code CandidateProjection}'s
     * own javadoc).
     */
    @SuppressWarnings("unchecked")
    private void assertObjectMatches(Map<String, Object> expected, CelValue actual) {
        assertTrue(actual instanceof CelValue.ObjectV, () -> "expected OBJECT, got " + actual);
        CandidateProjection projection = ((CelValue.ObjectV) actual).projection();
        Map<String, Object> projectionJson = (Map<String, Object>) expected.get("projection");
        List<Object> expectedChain = (List<Object>) projectionJson.getOrDefault("namespaceChain", List.of());
        assertEquals(expectedChain, projection.namespaceChain(), "nested object namespaceChain mismatch");
        Map<String, Object> fieldsJson = (Map<String, Object>) projectionJson.getOrDefault("fields", Map.of());
        for (Map.Entry<String, Object> classEntry : fieldsJson.entrySet()) {
            String className = classEntry.getKey();
            Map<String, Object> perClass = (Map<String, Object>) classEntry.getValue();
            for (Map.Entry<String, Object> fieldEntry : perClass.entrySet()) {
                String fieldName = fieldEntry.getKey();
                assertTrue(projection.declaresField(className, fieldName),
                        () -> "expected nested field " + className + "." + fieldName + " to be declared");
                Map<String, Object> fieldTypedValue = (Map<String, Object>) fieldEntry.getValue();
                assertValueMatches(fieldTypedValue, projection.fieldValue(className, fieldName));
            }
        }
    }

    private static String nodeKindName(ExprNode node) {
        return switch (node) {
            case ExprNode.LitBool ignored -> "LIT_BOOL";
            case ExprNode.LitInt ignored -> "LIT_INT";
            case ExprNode.LitDouble ignored -> "LIT_DOUBLE";
            case ExprNode.LitString ignored -> "LIT_STRING";
            case ExprNode.LitBytes ignored -> "LIT_BYTES";
            case ExprNode.LitNull ignored -> "LIT_NULL";
            case ExprNode.ListLit ignored -> "LIST_LIT";
            case ExprNode.FieldRef ignored -> "FIELD_REF";
            case ExprNode.Has ignored -> "HAS";
            case ExprNode.Not ignored -> "NOT";
            case ExprNode.Neg ignored -> "NEG";
            case ExprNode.And ignored -> "AND";
            case ExprNode.Or ignored -> "OR";
            case ExprNode.Eq ignored -> "EQ";
            case ExprNode.Ne ignored -> "NE";
            case ExprNode.Lt ignored -> "LT";
            case ExprNode.Le ignored -> "LE";
            case ExprNode.Gt ignored -> "GT";
            case ExprNode.Ge ignored -> "GE";
            case ExprNode.Add ignored -> "ADD";
            case ExprNode.Sub ignored -> "SUB";
            case ExprNode.Mul ignored -> "MUL";
            case ExprNode.Div ignored -> "DIV";
            case ExprNode.Mod ignored -> "MOD";
            case ExprNode.In ignored -> "IN";
            case ExprNode.Cond ignored -> "COND";
            case ExprNode.Call ignored -> "CALL";
        };
    }
}
