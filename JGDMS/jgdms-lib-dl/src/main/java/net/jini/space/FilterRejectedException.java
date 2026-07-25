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
package net.jini.space;

/**
 * Thrown, LOUDLY, when a CEL filter attached to a filtered space operation is
 * refused. A refused filter is <em>never</em> downgraded to an unfiltered
 * query: every rejection path throws this exception so that a caller can never
 * silently receive more entries than the predicate asked for.
 *
 * <p>This is the single, defined, client-facing rejection type for the
 * JavaSpaces CEL filter-pushdown feature (JGDMS-STD-011 / SOW Part B, unit B1).
 * It is a <em>checked</em> exception declared on every filtered operation
 * signature (both the {@code OutriggerServer} backend's remote methods and the
 * client-facing {@link FilteredJavaSpace} interface) precisely so that a loud
 * break is structural: a caller cannot ignore the possibility that a filter was
 * refused.
 *
 * <p>It lives in {@code jgdms-lib-dl} — the {@code net.jini.space} public API
 * module (release&nbsp;8 target), alongside {@link FilteredJavaSpace},
 * {@link JavaSpace05} and {@link InternalSpaceException} — because it is a
 * client-compile-time API, not a codebase-download proxy type. It names
 * <b>no</b> {@code jgdms-cel} or {@code jgdms-der} type: the server-side
 * admission seam maps a CEL {@code VerificationResult.Reason} onto the
 * transport-neutral {@link Reason} enum below before throwing, so the API layer
 * never depends on the CEL type surface.
 *
 * <p>Rationale for a checked exception over the codebase's usual unchecked
 * {@code IllegalArgumentException} "loud pre-side-effect" idiom: the filter
 * feature is security-relevant, and its whole contract is "reject rather than
 * run unfiltered". Making the rejection a declared checked exception forces the
 * compiler to keep that contract visible at every call site.
 *
 * @since JGDMS 4.0.0
 */
public class FilterRejectedException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * The machine-readable cause of a filter rejection. Transport-neutral: the
     * four {@code FILTER_*} constants correspond 1:1 to the CEL verifier's
     * {@code VerificationResult.Reason} constants, but are re-declared here so
     * that the {@code net.jini.space} API carries no CEL dependency.
     */
    public enum Reason {
        /**
         * The DER envelope {@code SEQUENCE { version, celWire }} was malformed:
         * wrong tag, non-canonical length/INTEGER, trailing bytes, an
         * unsupported {@code version}, or an envelope/ceiling breach. Detected
         * before the CEL bytes are ever examined.
         */
        ENVELOPE_MALFORMED,

        /**
         * A multi-template filtered operation
         * ({@code registerForAvailabilityEvent}, filtered {@code contents}, or
         * filtered bulk {@code take}) was called with more templates than the
         * server's admission ceiling permits. Each template forces an independent
         * schema build and CEL verification of the one filter, so an unbounded
         * template collection is a denial-of-service amplification vector;
         * exceeding the ceiling fails the whole operation loudly (fail-closed)
         * before any admission work, and is never truncated to a smaller query.
         */
        TEMPLATE_COUNT_EXCEEDED,

        /**
         * The CEL wire bytes failed canonical decode
         * ({@code VerificationResult.Reason.DECODE_REJECTED}).
         */
        FILTER_DECODE_REJECTED,

        /**
         * The CEL expression exceeded the verifier's static cost ceiling
         * ({@code VerificationResult.Reason.COST_EXCEEDED}).
         */
        FILTER_COST_EXCEEDED,

        /**
         * The CEL expression failed static type checking against the template's
         * schema ({@code VerificationResult.Reason.STATIC_TYPE_MISMATCH}).
         */
        FILTER_TYPE_MISMATCH,

        /**
         * The CEL expression's declared result type was inconsistent
         * ({@code VerificationResult.Reason.RESULT_TYPE_MISMATCH}).
         */
        FILTER_RESULT_TYPE_MISMATCH,

        /**
         * The CEL record was well-formed and verified, but its evaluation
         * context is a {@code Transform}, not a {@code Predicate}. Only a
         * boolean predicate may gate a query; a value-producing transform is
         * refused.
         */
        NOT_A_PREDICATE,

        /**
         * The template's on-wire v2 schema could not be resolved to type-check
         * the filter against (e.g. the template body did not decode as a v2
         * EntryRep body). A filter that names fields cannot be admitted without
         * a schema to check it against, so this fails closed.
         */
        SCHEMA_UNAVAILABLE,

        /**
         * The filter was admitted (decoded, verified, compiled) but the
         * operation's match chokepoint does not yet evaluate filters in this
         * build. Unit B1 wires the API, the envelope, and the admission seam;
         * predicate <em>evaluation</em> at the chokepoints is unit&nbsp;B3.
         * Until B3 lands, a filtered operation that reaches an unwired
         * chokepoint fails with this reason rather than running unfiltered.
         */
        EVALUATION_NOT_WIRED
    }

    private final Reason reason;

    /**
     * @param reason the machine-readable rejection cause (must not be null)
     * @param message a human-readable diagnostic
     */
    public FilterRejectedException(Reason reason, String message) {
        super(message);
        if (reason == null) throw new NullPointerException("reason");
        this.reason = reason;
    }

    /**
     * @param reason the machine-readable rejection cause (must not be null)
     * @param message a human-readable diagnostic
     * @param cause the underlying cause (may be null)
     */
    public FilterRejectedException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        if (reason == null) throw new NullPointerException("reason");
        this.reason = reason;
    }

    /**
     * @return the machine-readable cause of this rejection (never null)
     */
    public Reason reason() {
        return reason;
    }
}
