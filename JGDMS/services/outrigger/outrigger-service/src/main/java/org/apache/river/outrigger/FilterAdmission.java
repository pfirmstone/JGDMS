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

import java.util.concurrent.atomic.AtomicLong;

import org.apache.river.outrigger.proxy.EntryRep;
import org.apache.river.outrigger.proxy.FilterEnvelope;
import net.jini.space.FilterRejectedException;

import au.net.zeus.jgdms.cel.verifier.CelVerifier;
import au.net.zeus.jgdms.cel.verifier.DerSchemaChainView;
import au.net.zeus.jgdms.cel.verifier.SchemaView;
import au.net.zeus.jgdms.cel.verifier.VerificationResult;
import au.net.zeus.jgdms.cel.wire.CelFilterRecord;
import au.net.zeus.jgdms.cel.wire.EvaluationContext;
import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.entry.EntryRepV2Codec;

/**
 * The server-side CEL filter <em>admission seam</em> (SOW Part&nbsp;B,
 * unit&nbsp;B1). Runs at operation entry, BEFORE any matching, and turns an
 * opaque {@code byte[] filterEnvelope} into an immutable, verified
 * {@link CompiledFilter} — or fails the operation LOUDLY with a
 * {@link FilterRejectedException}. A refused filter is never downgraded to an
 * unfiltered query.
 *
 * <h3>Pipeline (fail-closed at every step)</h3>
 * <ol>
 *   <li>Decode the {@link FilterEnvelope} {@code {version, celWire}} — canonical
 *       DER, bounded ({@code ENVELOPE_MALFORMED} on any defect).</li>
 *   <li>Build a typed {@link SchemaView} from the <b>template's own v2 schema</b>:
 *       {@link EntryRepV2Codec#decodeEntrySchemaChain} over the template's wire
 *       body, wrapped in a {@link DerSchemaChainView}. The server never loads the
 *       entry class. A null / match-any template (empty body) yields a
 *       schema-less verification — a legal full scan.</li>
 *   <li>{@code CelVerifier.verify(celWire, schemaView)} — decode + cost + static
 *       type + result-type checks. A non-accepted result is mapped to the
 *       matching {@link FilterRejectedException.Reason} and thrown.</li>
 *   <li>Require the record's evaluation context to be a
 *       {@link EvaluationContext.Predicate}; a {@link EvaluationContext.Transform}
 *       is refused ({@code NOT_A_PREDICATE}).</li>
 *   <li>Construct the immutable {@link CompiledFilter} (verified AST, static
 *       cost, applicability {@code entrySchemaDigest}).</li>
 * </ol>
 *
 * <p>This seam does <b>not</b> evaluate the predicate against any entry — that is
 * unit&nbsp;B3. It stops at "a verified predicate exists".
 *
 * <h3>Observability</h3>
 * Static counters (operator-only by default) distinguish, per outcome, an
 * admitted filter from each rejection reason. B3 will add a separate
 * fail-closed-exclusion counter (a candidate silently excluded because it was
 * undecodable / wrong-format / missing a referenced field) so that a fail-closed
 * no-match is never confused with a genuine "nothing matched"; that counter is
 * declared here ({@link #FAIL_CLOSED_EXCLUSIONS}) and incremented by B3.
 *
 * @since JGDMS 4.0.0
 */
public final class FilterAdmission {

    private FilterAdmission() { throw new AssertionError("no instances"); }

    /* ---- Observability counters (operator-only; snapshot via metrics()) ---- */

    /** Filters admitted (envelope + CEL verified + Predicate confirmed). */
    static final AtomicLong ADMITTED = new AtomicLong();
    /** Rejections: malformed / over-ceiling / wrong-version envelope. */
    static final AtomicLong REJECTED_ENVELOPE = new AtomicLong();
    /** Rejections: a multi-template op exceeded {@link #MAX_TEMPLATES} (DoS ceiling). */
    static final AtomicLong REJECTED_TEMPLATE_COUNT = new AtomicLong();
    /** Rejections: template v2 schema could not be resolved. */
    static final AtomicLong REJECTED_SCHEMA_UNAVAILABLE = new AtomicLong();
    /** Rejections: CEL verifier DECODE_REJECTED. */
    static final AtomicLong REJECTED_DECODE = new AtomicLong();
    /** Rejections: CEL verifier COST_EXCEEDED. */
    static final AtomicLong REJECTED_COST = new AtomicLong();
    /** Rejections: CEL verifier STATIC_TYPE_MISMATCH. */
    static final AtomicLong REJECTED_TYPE = new AtomicLong();
    /** Rejections: CEL verifier RESULT_TYPE_MISMATCH. */
    static final AtomicLong REJECTED_RESULT_TYPE = new AtomicLong();
    /** Rejections: record verified but context was a Transform, not a Predicate. */
    static final AtomicLong REJECTED_NOT_PREDICATE = new AtomicLong();
    /**
     * Filtered operations that failed because the match chokepoint does not yet
     * evaluate filters (unit B3). Incremented by the chokepoint callers, not by
     * {@link #admit}.
     */
    static final AtomicLong EVALUATION_NOT_WIRED = new AtomicLong();
    /**
     * Candidates silently excluded by a fail-closed rule during evaluation
     * (undecodable / wrong-format / missing referenced field / budget / escaping
     * Throwable). Populated by unit&nbsp;B3; declared here so the metric name is
     * fixed as permanent API. Distinguishes a fault-driven exclusion from an
     * honest predicate-false ({@link #EXCLUDED_FALSE}) or a genuine empty result.
     */
    static final AtomicLong FAIL_CLOSED_EXCLUSIONS = new AtomicLong();
    /**
     * Candidates that reached CEL evaluation (post-entitlement, post-byte-match)
     * — the denominator for the two verdict counters below (design memo B3
     * &sect;7). Populated by unit&nbsp;B3.
     */
    static final AtomicLong EVALUATED = new AtomicLong();
    /** Candidates that evaluated to {@code BoolV(true)} — a genuine predicate pass (B3 &sect;7). */
    static final AtomicLong PASSED = new AtomicLong();
    /**
     * Candidates that cleanly evaluated to {@code BoolV(false)} — an HONEST
     * predicate rejection, distinct from a fail-closed exclusion (B3 &sect;7).
     */
    static final AtomicLong EXCLUDED_FALSE = new AtomicLong();
    /**
     * Candidates excluded because they blew the per-candidate
     * {@code MAX_PROJECTION_DECODE_BYTES} budget (a broken-out sub-category of
     * {@link #FAIL_CLOSED_EXCLUSIONS}, so lock-hold-DoS attempts are visible;
     * B3 &sect;4.3/&sect;7). Populated by unit&nbsp;B3.
     */
    static final AtomicLong REJECTED_PROJECTION_BUDGET = new AtomicLong();

    /* ---- Multi-template admission ceiling (fail-closed DoS defence) -------- */

    /**
     * The maximum number of templates a single multi-template filtered operation
     * may carry. A shared, fail-closed admission ceiling for the three
     * multi-template ops — {@code registerForAvailabilityEvent}, filtered
     * {@code contents}, and filtered bulk {@code take} — each of which admits the
     * ONE filter against EACH template's own schema. Without a ceiling, an
     * authenticated-but-hostile client could send an enormous template array and
     * force one per-template schema build + {@code CelVerifier.verify} for every
     * element (the envelope decode is now hoisted out once via {@link #prepare},
     * but the schema-dependent admission genuinely must run per distinct template
     * — so its COUNT is what is bounded here). {@value} is generous for any
     * legitimate multi-type query — realistic entry-type cardinality is a handful
     * to a few dozen — while capping the admission amplification at a fixed factor
     * instead of leaving it unbounded.
     *
     * <p><b>This is a fixed compile-time constant, not a runtime/deployment knob.</b>
     * There is no configuration or system-property override; raising it requires
     * editing this source and recompiling {@code outrigger-service} (the value is
     * additionally inlined into its call sites, so replacing the class alone would
     * not retune it). Worst-case admission work for one operation is therefore
     * bounded by {@code MAX_TEMPLATES} × (one template schema build + one CEL
     * verification of a ≤{@code MAX_CEL_WIRE_BYTES} filter).
     *
     * @see #checkTemplateCount(int)
     */
    public static final int MAX_TEMPLATES = 256;

    /**
     * Fail-closed guard for the multi-template filtered ops: rejects a template
     * collection larger than {@link #MAX_TEMPLATES} LOUDLY, before any envelope
     * decode or per-template admission runs, so an oversized array can never drive
     * the admission loop. It rejects rather than truncates — silently dropping
     * templates would under-filter the query, exactly the "never downgrade" hazard
     * the whole filter feature exists to prevent.
     *
     * <p>The breach is signalled as a {@link FilterRejectedException} of reason
     * {@link FilterRejectedException.Reason#TEMPLATE_COUNT_EXCEEDED} — uniform with
     * every other filter-admission rejection on the same operation signatures, so a
     * client catching {@code FilterRejectedException} catches this too — and is
     * counted in {@link #REJECTED_TEMPLATE_COUNT} so the very flood this defends
     * against is visible in {@link #metrics()}.
     *
     * @param count the number of templates the operation was invoked with
     * @throws FilterRejectedException {@code TEMPLATE_COUNT_EXCEEDED} if
     *         {@code count > MAX_TEMPLATES} (loud; never a downgrade to a smaller
     *         or unfiltered query)
     */
    public static void checkTemplateCount(int count) throws FilterRejectedException {
        if (count > MAX_TEMPLATES) {
            REJECTED_TEMPLATE_COUNT.incrementAndGet();
            throw new FilterRejectedException(
                    FilterRejectedException.Reason.TEMPLATE_COUNT_EXCEEDED,
                    "filtered operation template count " + count
                    + " exceeds the maximum " + MAX_TEMPLATES
                    + " (admission DoS ceiling); reduce the number of templates");
        }
    }

    /* ---- Decode-once seam -------------------------------------------------- */

    /**
     * A filter envelope decoded EXACTLY ONCE. The opaque {@code byte[]
     * filterEnvelope} is identical across every template of a multi-template
     * operation, so decoding it per template is pure wasted work (and, unbounded,
     * an amplification vector). {@link #prepare} unwraps the envelope a single
     * time into this immutable token; {@link #admit(PreparedFilter, EntryRep)}
     * then admits it against each template's own schema without re-decoding.
     *
     * <p>Holds the inner CEL wire bytes only — it carries no schema and no
     * compiled predicate, because both are per-template. (The CEL AST itself is
     * still decoded per template inside {@code CelVerifier.verify}: reusing the
     * decoded AST across schemas would mean bypassing the verifier's single
     * byte[]-only admission gate, which is out of scope here; the {@link
     * #MAX_TEMPLATES} ceiling bounds that per-template cost instead.)
     */
    public static final class PreparedFilter {
        private final byte[] celWire;
        private PreparedFilter(byte[] celWire) { this.celWire = celWire; }
    }

    /**
     * Decode-once seam: unwrap the opaque {@link FilterEnvelope} a SINGLE time,
     * failing closed with {@code ENVELOPE_MALFORMED} on a null or malformed
     * envelope. A multi-template op calls this once and reuses the result across
     * its template loop via {@link #admit(PreparedFilter, EntryRep)}.
     *
     * @param filterEnvelope the opaque canonical {@link FilterEnvelope} bytes
     *                       (must not be null)
     * @return the decoded, reusable envelope token
     * @throws FilterRejectedException {@code ENVELOPE_MALFORMED} on a null or
     *                                 defective envelope (loud; never a downgrade)
     */
    public static PreparedFilter prepare(byte[] filterEnvelope)
            throws FilterRejectedException {
        if (filterEnvelope == null) {
            REJECTED_ENVELOPE.incrementAndGet();
            throw new FilterRejectedException(
                    FilterRejectedException.Reason.ENVELOPE_MALFORMED,
                    "filter envelope is null");
        }
        // Envelope decode (throws ENVELOPE_MALFORMED on any defect).
        final FilterEnvelope env;
        try {
            env = FilterEnvelope.decode(filterEnvelope);
        } catch (FilterRejectedException e) {
            REJECTED_ENVELOPE.incrementAndGet();
            throw e;
        }
        return new PreparedFilter(env.celWire());
    }

    /**
     * Admits a filter envelope against a query template, returning a verified
     * immutable predicate or throwing loudly. A single-template convenience:
     * equivalent to {@code admit(prepare(filterEnvelope), tmpl)}.
     *
     * @param filterEnvelope the opaque canonical {@link FilterEnvelope} bytes
     *                       (must not be null)
     * @param tmpl           the query template (may be null / match-any ⇒
     *                       schema-less verification)
     * @return the verified compiled filter
     * @throws FilterRejectedException on any rejection (loud; never a downgrade
     *                                 to an unfiltered query)
     */
    public static CompiledFilter admit(byte[] filterEnvelope, EntryRep tmpl)
            throws FilterRejectedException {
        return admit(prepare(filterEnvelope), tmpl);
    }

    /**
     * Admits an already-decoded filter ({@link #prepare}) against ONE template's
     * own v2 schema — steps 2–5 of the admission pipeline (schema build, CEL
     * verify, {@code Predicate} check, compile). The envelope decode (step 1) has
     * already happened once in {@link #prepare}; the schema-dependent work here
     * genuinely must run per template. Fail-closed at every step.
     *
     * @param prepared the once-decoded envelope (must not be null)
     * @param tmpl     the query template (may be null / match-any ⇒ schema-less
     *                 verification)
     * @return the verified compiled filter
     * @throws FilterRejectedException on any rejection (loud; never a downgrade
     *                                 to an unfiltered query)
     */
    public static CompiledFilter admit(PreparedFilter prepared, EntryRep tmpl)
            throws FilterRejectedException {
        if (prepared == null) throw new NullPointerException("prepared");
        final byte[] celWire = prepared.celWire;

        // 2. Schema view from the template's own v2 wire body (class-free).
        final byte[] body = (tmpl == null) ? new byte[0] : tmpl.bodyBytes();
        final SchemaView schemaView;
        final byte[] applicabilityDigest;
        if (body.length == 0) {
            // Null / match-any template: a legal schema-less full scan.
            schemaView = null;
            applicabilityDigest = null;
        } else {
            final EntryRepV2Codec.EntrySchemaChain esc;
            try {
                esc = EntryRepV2Codec.decodeEntrySchemaChain(body);
            } catch (DerException e) {
                REJECTED_SCHEMA_UNAVAILABLE.incrementAndGet();
                throw new FilterRejectedException(
                        FilterRejectedException.Reason.SCHEMA_UNAVAILABLE,
                        "could not resolve the template's v2 schema to type-check the filter against",
                        e);
            }
            schemaView = new DerSchemaChainView(esc.chain());
            applicabilityDigest = esc.entrySchemaDigest();
        }

        // 3. Verify the CEL wire bytes. CelVerifier.verify is documented to never
        //    throw (it returns a VerificationResult for every outcome), but a
        //    latent bug in the verifier MUST NOT escape admit() as anything other
        //    than a loud FilterRejectedException -- belt-and-braces fail-closed.
        final VerificationResult vr;
        try {
            vr = (schemaView == null)
                    ? CelVerifier.verify(celWire)
                    : CelVerifier.verify(celWire, schemaView);
        } catch (RuntimeException unexpected) {
            REJECTED_DECODE.incrementAndGet();
            throw new FilterRejectedException(
                    FilterRejectedException.Reason.FILTER_DECODE_REJECTED,
                    "CEL verification raised an unexpected runtime exception (fail-closed): "
                    + unexpected, unexpected);
        }
        if (!vr.accepted()) {
            throw rejectVerification(vr);
        }

        // 4. Only a Predicate may gate a query; a Transform is refused.
        final CelFilterRecord record = vr.record().orElseThrow(() -> {
            REJECTED_DECODE.incrementAndGet();
            return new FilterRejectedException(
                    FilterRejectedException.Reason.FILTER_DECODE_REJECTED,
                    "accepted verification carried no record (internal invariant)");
        });
        if (!(record.context() instanceof EvaluationContext.Predicate)) {
            REJECTED_NOT_PREDICATE.incrementAndGet();
            throw new FilterRejectedException(
                    FilterRejectedException.Reason.NOT_A_PREDICATE,
                    "filter evaluation context is a Transform; only a boolean Predicate may gate a query");
        }

        // 5. Compile.
        final long cost = vr.cost().orElse(0L);
        ADMITTED.incrementAndGet();
        return new CompiledFilter(record.expression(), cost, applicabilityDigest);
    }

    /**
     * The unwired-chokepoint loud reject (unit B1). Called by a filtered
     * operation after its filter has been successfully admitted, because
     * predicate evaluation at the match chokepoints is unit&nbsp;B3: rather than
     * run the query unfiltered, the operation fails loudly.
     *
     * @param op     the operation name, for the diagnostic
     * @param filter the successfully admitted filter (unused here beyond
     *               signalling that admission did occur; retained for B3, which
     *               will replace this call with real evaluation)
     * @return the exception to throw
     */
    public static FilterRejectedException evaluationNotWired(String op, CompiledFilter filter) {
        EVALUATION_NOT_WIRED.incrementAndGet();
        return new FilterRejectedException(
                FilterRejectedException.Reason.EVALUATION_NOT_WIRED,
                "filter admitted for '" + op + "' but predicate evaluation at the match "
                + "chokepoint is not yet implemented (SOW Part B, unit B3); refusing to run "
                + "the query unfiltered");
    }

    /** Maps a non-accepted VerificationResult onto a loud rejection + counter. */
    private static FilterRejectedException rejectVerification(VerificationResult vr) {
        final VerificationResult.Reason r = vr.reason();
        final String diag = vr.diagnostic();
        switch (r) {
            case DECODE_REJECTED:
                REJECTED_DECODE.incrementAndGet();
                return new FilterRejectedException(
                        FilterRejectedException.Reason.FILTER_DECODE_REJECTED,
                        "CEL wire decode rejected: " + diag);
            case COST_EXCEEDED:
                REJECTED_COST.incrementAndGet();
                return new FilterRejectedException(
                        FilterRejectedException.Reason.FILTER_COST_EXCEEDED,
                        "CEL cost ceiling exceeded: " + diag);
            case STATIC_TYPE_MISMATCH:
                REJECTED_TYPE.incrementAndGet();
                return new FilterRejectedException(
                        FilterRejectedException.Reason.FILTER_TYPE_MISMATCH,
                        "CEL static type mismatch against the template schema: " + diag);
            case RESULT_TYPE_MISMATCH:
                REJECTED_RESULT_TYPE.incrementAndGet();
                return new FilterRejectedException(
                        FilterRejectedException.Reason.FILTER_RESULT_TYPE_MISMATCH,
                        "CEL declared result-type inconsistency: " + diag);
            default:
                // Defensive: an unknown reason still fails closed.
                REJECTED_DECODE.incrementAndGet();
                return new FilterRejectedException(
                        FilterRejectedException.Reason.FILTER_DECODE_REJECTED,
                        "CEL verification rejected (" + r + "): " + diag);
        }
    }

    /**
     * A point-in-time snapshot of the admission counters, for operator metrics.
     * The order matches {@link #METRIC_NAMES}.
     *
     * @return a fresh {@code long[]} snapshot
     */
    public static long[] metrics() {
        return new long[] {
            ADMITTED.get(),
            REJECTED_ENVELOPE.get(),
            REJECTED_TEMPLATE_COUNT.get(),
            REJECTED_SCHEMA_UNAVAILABLE.get(),
            REJECTED_DECODE.get(),
            REJECTED_COST.get(),
            REJECTED_TYPE.get(),
            REJECTED_RESULT_TYPE.get(),
            REJECTED_NOT_PREDICATE.get(),
            EVALUATION_NOT_WIRED.get(),
            FAIL_CLOSED_EXCLUSIONS.get(),
            EVALUATED.get(),
            PASSED.get(),
            EXCLUDED_FALSE.get(),
            REJECTED_PROJECTION_BUDGET.get()
        };
    }

    /** Names for the {@link #metrics()} slots, in order. */
    public static final String[] METRIC_NAMES = {
        "filter.admitted",
        "filter.rejected.envelope",
        "filter.rejected.templateCount",
        "filter.rejected.schemaUnavailable",
        "filter.rejected.decode",
        "filter.rejected.cost",
        "filter.rejected.typeMismatch",
        "filter.rejected.resultTypeMismatch",
        "filter.rejected.notPredicate",
        "filter.evaluationNotWired",
        "filter.failClosedExclusions",
        "filter.evaluated",
        "filter.passed",
        "filter.excludedFalse",
        "filter.rejected.projectionBudget"
    };
}
