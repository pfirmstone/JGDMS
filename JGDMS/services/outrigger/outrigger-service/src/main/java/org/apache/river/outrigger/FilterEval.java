/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.outrigger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.river.api.io.EntryV2Codec;
import org.apache.river.outrigger.proxy.EntryRep;

import au.net.zeus.jgdms.cel.CelValue;
import au.net.zeus.jgdms.cel.EvalOutcome;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.eval.Evaluator;

/**
 * The per-candidate CEL predicate evaluation core (design memo B3 &sect;1.4/&sect;3,
 * the security crux of the filter feature). Given an operation's {@link FilterSet}
 * and a candidate {@link EntryRep} the caller has already matched by byte-equality
 * template <em>and</em> gated for transactional entitlement (INV-1), decides
 * "does the predicate hold?" — <b>class-free, fail-closed, total</b>.
 *
 * <p>This class NEVER decides entitlement — that is the caller's job, done before
 * {@link #matches} is ever called (the confirm window's {@code canPerform} gate on
 * the capture path; the {@code transition.getTxn()} gate on the fan-out path). By
 * the time control reaches here the candidate is one the requesting client is
 * already entitled to observe; {@code matches} only answers the predicate.
 *
 * <h3>Per-candidate algorithm (&sect;1.4)</h3>
 * <ol>
 *   <li>An empty {@link FilterSet} is a no-op ⇒ {@code true} (unfiltered path,
 *       byte-for-byte unchanged).</li>
 *   <li>Select the applicable filter(s) via
 *       {@link FilterSet#applicableTo(byte[], byte[])} using the candidate's
 *       <b>stored</b> {@code entrySchemaDigest} (the pre-decode digest, &sect;3
 *       nit) — a wrong-schema concrete filter is not selected without decoding any
 *       field; a subclass candidate resolves to its originating filter
 *       schema-lessly; an unresolvable-multi-schema subclass fails closed.</li>
 *   <li>Project the union of the applicable filters' referenced fields <b>once</b>,
 *       against the candidate's own v2 schema, under the per-candidate
 *       {@link EntryProjection#MAX_PROJECTION_DECODE_BYTES} budget (&sect;4.3
 *       Option&nbsp;B), <b>reusing the candidate rep's already-decoded body</b>
 *       ({@link EntryRep#decoded()} &rarr;
 *       {@link EntryProjection#projectReusing}, the ratified &sect;4.2/&sect;4.4
 *       slice reuse) so no {@code O(body)} structural re-decode runs under the
 *       {@code handle} lock or on the journal thread. An undecodable /
 *       over-budget projection ⇒ fail-closed.</li>
 *   <li>With a FRESH {@link Evaluator} per candidate, evaluate every applicable
 *       filter against that one projection; <b>all must return {@code BoolV(true)}</b>.
 *       Any {@code BoolV(false)} is an honest exclusion; any {@code EvalOutcome.Error}
 *       (undecodable / absent / ambiguous / type-mismatch / ...) is a fail-closed
 *       exclusion; a non-boolean value (a Predicate never yields one) is
 *       fail-closed.</li>
 *   <li>The whole body is wrapped <b>catch-everything except VirtualMachineError</b>
 *       (&sect;2.3): any escaping {@code Throwable} maps to a fail-closed no-match so
 *       one hostile filter can never kill a caller thread (on the fan-out path, the
 *       single {@code OperationJournal} delivery thread).</li>
 * </ol>
 * Every non-{@code true} outcome is <b>counted</b> in the operator-only
 * {@link FilterAdmission} metrics (&sect;7): {@code filter.evaluated} (the
 * denominator — bumped only once a candidate genuinely <em>reaches</em> the
 * evaluator, i.e. post-projection, so the &sect;8.2 happy-path identity
 * {@code evaluated == passed + excludedFalse} holds), {@code filter.passed},
 * {@code filter.excludedFalse} (honest false),
 * {@code filter.failClosedExclusions} (fault-driven), and its broken-out
 * sub-category {@code filter.rejected.projectionBudget}.
 *
 * @since JGDMS 4.0.0
 */
final class FilterEval {

    private FilterEval() { throw new AssertionError("no instances"); }

    // --- Operator-only JFR outcome tags (see FilterEvaluationEvent) ----------
    // These string values are the stable event contract the demo/operator reads;
    // keep them in sync with the counters bumped alongside each return below.
    static final String OUTCOME_PASSED = "PASSED";
    static final String OUTCOME_EXCLUDED_FALSE = "EXCLUDED_FALSE";
    static final String OUTCOME_FAIL_CLOSED = "FAIL_CLOSED";
    static final String OUTCOME_PROJECTION_BUDGET = "PROJECTION_BUDGET";

    // A never-committed probe instance whose isEnabled() gives a cheap, allocation-free
    // "is any recording capturing FilterEvaluation?" check on the disabled hot path.
    private static final FilterEvaluationEvent ENABLED_PROBE = new FilterEvaluationEvent();

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static String hex(byte[] b) {
        if (b == null) return null;
        final char[] out = new char[b.length * 2];
        for (int i = 0; i < b.length; i++) {
            out[i * 2] = HEX[(b[i] >> 4) & 0xf];
            out[i * 2 + 1] = HEX[b[i] & 0xf];
        }
        return new String(out);
    }

    /**
     * Commit one operator-only {@link FilterEvaluationEvent} for a candidate the
     * predicate actually ran against. A {@code null} outcome (the unfiltered no-op
     * and the VirtualMachineError re-throw) records nothing — "no event" is itself the
     * confused-deputy signal (memo &sect;8.3). Inert and allocation-free unless a
     * recording has explicitly enabled the event.
     */
    private static void emit(String outcome, EntryRep candidate) {
        if (outcome == null || !ENABLED_PROBE.isEnabled()) {
            return; // unfiltered/VME path, or event disabled by default → nothing
        }
        final FilterEvaluationEvent event = new FilterEvaluationEvent();
        event.outcome = outcome;
        try {
            event.schemaDigestHex = hex(candidate.entrySchemaDigest());
        } catch (Throwable ignore) {
            event.schemaDigestHex = null; // never let telemetry perturb the verdict
        }
        event.commit();
    }

    /**
     * Evaluate the operation's filters against one entitled candidate. Convenience
     * overload with no byte-matched-template hint (correct for single-template ops
     * and same-schema multi-template ops; see {@link FilterSet}).
     *
     * @param filters   the operation's {@link FilterSet} (never {@code null};
     *                  {@link FilterSet#EMPTY} for an unfiltered caller)
     * @param candidate the byte-matched, entitlement-gated candidate rep
     * @return {@code true} to let the candidate through, {@code false} to exclude
     *         it (predicate-false or any fail-closed reason)
     */
    static boolean matches(FilterSet filters, EntryRep candidate) {
        return matches(filters, candidate, null);
    }

    /**
     * Evaluate the operation's filters against one entitled candidate, with the
     * applicability digest of the template the candidate byte-matched (lets the
     * multi-distinct-schema subclass case resolve to the originating filter).
     *
     * @param filters               the operation's {@link FilterSet}
     * @param candidate             the byte-matched, entitlement-gated candidate rep
     * @param matchedTemplateDigest applicability digest of the byte-matched
     *        template, or {@code null} if unknown/schema-less to the caller
     * @return {@code true} to let the candidate through, {@code false} to exclude it
     */
    static boolean matches(FilterSet filters, EntryRep candidate, byte[] matchedTemplateDigest) {
        if (filters == null || filters.isEmpty()) {
            return true; // unfiltered no-op — no evaluation, no event
        }
        // Terminal outcome tag for the operator-only JFR event, set on every real
        // verdict below. Stays null on the VirtualMachineError re-throw (not a verdict)
        // so no event is emitted there; emitted once in the finally otherwise.
        String outcome = null;
        try {
            // Applicability selection off the STORED (pre-decode) digest — no field
            // is decoded to drop a wrong-schema concrete filter (§3 nit).
            final byte[] storedDigest = candidate.entrySchemaDigest();
            final List<CompiledFilter> applicable =
                    filters.applicableTo(storedDigest, matchedTemplateDigest);
            if (applicable == null) {
                // Ambiguous subclass / cross-schema — cannot recover the originating
                // filter; fail closed (over-exclusion is safe, §5/§7).
                FilterAdmission.FAIL_CLOSED_EXCLUSIONS.incrementAndGet();
                outcome = OUTCOME_FAIL_CLOSED;
                return false;
            }
            if (applicable.isEmpty()) {
                // DEFENSIVE DEFAULT — must EXCLUDE, never admit (board FIX 3).
                // Unreachable through the public API today: an empty FilterSet
                // short-circuits above, and FilterSet.applicableTo already maps its own
                // empty result to null. But this is a fail-closed component, and "no
                // filter was selected" is a SELECTION FAULT, not a licence to let the
                // candidate through unfiltered. The prior `return true` meant a future
                // applicableTo change that returned an empty list would SILENTLY admit
                // every candidate with no predicate ever running — a fail-OPEN reachable
                // by editing a different file. Exclude, and count it as a fail-closed
                // exclusion so the fault is VISIBLE (§7: every non-pass outcome is
                // counted) rather than a silent drop.
                FilterAdmission.FAIL_CLOSED_EXCLUSIONS.incrementAndGet();
                outcome = OUTCOME_FAIL_CLOSED;
                return false;
            }

            // Project the UNION of referenced fields once, one shared per-candidate
            // budget (§4.3).
            final Set<String> referenced = new HashSet<>();
            for (CompiledFilter f : applicable) {
                referenced.addAll(EntryProjection.referencedFieldNames(f.expr()));
            }
            // §4.2/§4.4 SLICE REUSE (ratified). Every server-side candidate reaches here
            // having been decode-constructed — the @AtomicSerial GetArg unmarshal on the
            // write path, or restore() on the recovery path — each of which already ran the
            // one full, fully-validating EntryRepV2Codec.decode of the body and retained its
            // result. Consuming that here removes an O(body) STRUCTURAL re-decode per
            // candidate from inside EntryHolder.confirmAvailability's synchronized(handle)
            // window and from the single OperationJournal fan-out thread. That term was
            // charged against nothing: MAX_PROJECTION_DECODE_BYTES (64 KiB) bounds per-field
            // VALUE decode only, so the structural term was bounded solely by the 8 MiB
            // EntryRep-v2 body ceiling — attacker-controlled at write time.
            //
            // FALLBACK: a rep that was never decode-constructed (decoded() == null) — the
            // client write path's locally-built rep, the schema-less match-any stand-in, and
            // unit tests that construct an EntryRep straight from an Entry. Those still pay
            // the structural decode of bodyBytes(), which remains bounded only by the 8 MiB
            // body ceiling (noted in the design memo §4.4). No fail-open either way: the
            // fallback is the pre-existing, identically fail-closed factory.
            final EntryV2Codec.Decoded reusable = candidate.decoded();
            final EntryProjection projection = (reusable != null)
                    ? EntryProjection.projectReusing(reusable, referenced)
                    : EntryProjection.projectFields(candidate.bodyBytes(), referenced);
            if (projection.isUndecodable()) {
                if (projection.budgetExceeded()) {
                    FilterAdmission.REJECTED_PROJECTION_BUDGET.incrementAndGet();
                    outcome = OUTCOME_PROJECTION_BUDGET;
                } else {
                    outcome = OUTCOME_FAIL_CLOSED;
                }
                FilterAdmission.FAIL_CLOSED_EXCLUSIONS.incrementAndGet();
                return false;
            }

            // N-4 (§7 counter placement). `filter.evaluated` is DEFINED as "a candidate
            // reached CEL evaluation" — the denominator for filter.passed and
            // filter.excludedFalse. It is therefore incremented HERE, immediately before
            // the Evaluator loop and only after a SUCCESSFUL projection, never earlier:
            // counting it before projection folded in candidates that never reached CEL
            // at all (projection-undecodable, over-budget, applicability faults),
            // inflating the denominator and breaking the §8.2 happy-path identity
            // `evaluated == passed + excludedFalse` that demo7 asserts against. Every
            // exclusion taken before this point is already counted in
            // filter.failClosedExclusions (and, for the budget case, additionally in
            // filter.rejected.projectionBudget), so nothing goes uncounted — the
            // exclusion simply stops being miscounted as an evaluation.
            FilterAdmission.EVALUATED.incrementAndGet();

            // Fresh Evaluator per candidate (§1.4 step c). All applicable filters
            // must return BoolV(true); short-circuit on the first non-pass.
            final Evaluator evaluator = new Evaluator();
            for (CompiledFilter f : applicable) {
                final EvalOutcome evalOutcome = evaluator.evaluate(f.expr(), projection);
                if (evalOutcome instanceof EvalOutcome.Value v) {
                    if (v.value() instanceof CelValue.BoolV b) {
                        if (!b.value()) {
                            FilterAdmission.EXCLUDED_FALSE.incrementAndGet();
                            outcome = OUTCOME_EXCLUDED_FALSE;
                            return false; // honest predicate-false
                        }
                        // this filter passed; continue to the next
                    } else {
                        // A Predicate is verified to yield boolean; a non-bool here is
                        // an internal inconsistency — fail closed, never let through.
                        FilterAdmission.FAIL_CLOSED_EXCLUSIONS.incrementAndGet();
                        outcome = OUTCOME_FAIL_CLOSED;
                        return false;
                    }
                } else {
                    // EvalOutcome.Error: undecodable / absent / ambiguous / type /
                    // arithmetic — the closed error set, uniformly fail-closed (§7).
                    FilterAdmission.FAIL_CLOSED_EXCLUSIONS.incrementAndGet();
                    outcome = OUTCOME_FAIL_CLOSED;
                    return false;
                }
            }
            FilterAdmission.PASSED.incrementAndGet();
            outcome = OUTCOME_PASSED;
            return true;
        } catch (VirtualMachineError vme) {
            // OOME / StackOverflow is not a candidate verdict — never swallow it, and
            // never record it as one (outcome stays null ⇒ no event).
            throw vme;
        } catch (Throwable t) {
            // Any escaping Throwable (a latent projection/evaluator bug, the
            // Evaluator's IllegalStateException for a transcendental CALL with no
            // MathProvider, ...) => fail-closed no-match + counted, so it can never
            // kill the caller thread (§2.3).
            FilterAdmission.FAIL_CLOSED_EXCLUSIONS.incrementAndGet();
            outcome = OUTCOME_FAIL_CLOSED;
            return false;
        } finally {
            // One operator-only event per candidate the predicate actually ran against.
            emit(outcome, candidate);
        }
    }
}
