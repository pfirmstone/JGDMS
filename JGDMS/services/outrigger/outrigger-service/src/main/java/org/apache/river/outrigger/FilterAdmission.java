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
import org.apache.river.outrigger.proxy.FilterRejectedException;

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
     * (undecodable / wrong-format / missing referenced field). Populated by
     * unit&nbsp;B3; declared here so the metric name is fixed as permanent API.
     */
    static final AtomicLong FAIL_CLOSED_EXCLUSIONS = new AtomicLong();

    /**
     * Admits a filter envelope against a query template, returning a verified
     * immutable predicate or throwing loudly.
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
        if (filterEnvelope == null) {
            REJECTED_ENVELOPE.incrementAndGet();
            throw new FilterRejectedException(
                    FilterRejectedException.Reason.ENVELOPE_MALFORMED,
                    "filter envelope is null");
        }

        // 1. Envelope (throws ENVELOPE_MALFORMED on any defect).
        final FilterEnvelope env;
        try {
            env = FilterEnvelope.decode(filterEnvelope);
        } catch (FilterRejectedException e) {
            REJECTED_ENVELOPE.incrementAndGet();
            throw e;
        }
        final byte[] celWire = env.celWire();

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

        // 3. Verify the CEL wire bytes (never throws; returns a VerificationResult).
        final VerificationResult vr = (schemaView == null)
                ? CelVerifier.verify(celWire)
                : CelVerifier.verify(celWire, schemaView);
        if (!vr.accepted()) {
            throw rejectVerification(vr);
        }

        // 4. Only a Predicate may gate a query; a Transform is refused.
        final CelFilterRecord record = vr.record().orElseThrow(
                () -> new FilterRejectedException(
                        FilterRejectedException.Reason.FILTER_DECODE_REJECTED,
                        "accepted verification carried no record (internal invariant)"));
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
            REJECTED_SCHEMA_UNAVAILABLE.get(),
            REJECTED_DECODE.get(),
            REJECTED_COST.get(),
            REJECTED_TYPE.get(),
            REJECTED_RESULT_TYPE.get(),
            REJECTED_NOT_PREDICATE.get(),
            EVALUATION_NOT_WIRED.get(),
            FAIL_CLOSED_EXCLUSIONS.get()
        };
    }

    /** Names for the {@link #metrics()} slots, in order. */
    public static final String[] METRIC_NAMES = {
        "filter.admitted",
        "filter.rejected.envelope",
        "filter.rejected.schemaUnavailable",
        "filter.rejected.decode",
        "filter.rejected.cost",
        "filter.rejected.typeMismatch",
        "filter.rejected.resultTypeMismatch",
        "filter.rejected.notPredicate",
        "filter.evaluationNotWired",
        "filter.failClosedExclusions"
    };
}
