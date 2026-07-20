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

package au.net.zeus.jgdms.cel.verifier;

import au.net.zeus.jgdms.cel.wire.CelFilterRecord;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The outcome of {@link CelVerifier#verify}: an accept-or-reject decision
 * with a diagnostic, over a <em>closed</em> {@link Reason} set -- there is
 * no fifth kind of rejection, and no partial/best-effort acceptance.
 * <p>
 * On rejection, {@link #record()} and {@link #cost()} are populated as far
 * as the fail-closed composition in {@link CelVerifier} actually got before
 * rejecting (see that class's javadoc for the exact order): a {@link
 * Reason#DECODE_REJECTED} result never has a record or a cost; every other
 * reason has a record (decode succeeded); only an accepted result, or one
 * rejected for {@link Reason#STATIC_TYPE_MISMATCH} or {@link
 * Reason#RESULT_TYPE_MISMATCH}, has a cost (the cost gate itself already
 * passed).
 */
public final class VerificationResult {

    /**
     * The closed set of reasons {@link CelVerifier} can reject a candidate
     * {@code CelFilterRecord} for. {@link #DECODE_REJECTED} is T2's own
     * rejection, surfaced through this facade for composition-order
     * diagnostics -- not itself a T6-owned check (see {@link CelVerifier}'s
     * class javadoc's redundancy ruling). The other three are T6's own,
     * genuinely additive verification surface.
     */
    public enum Reason {
        /** {@code CelDecoder.decode(wire)} itself rejected the wire form. */
        DECODE_REJECTED,
        /** The decoded AST's statically computed {@code C(E)} exceeds {@code maxExprCost}, or a checked-arithmetic term computing it overflowed (STD-011 §10.6) -- both treated identically, per {@code CostModel}. */
        COST_EXCEEDED,
        /** A schema-optional, bottom-up static type-checking pass (STD-011 §11.2/§12.4) proved an operator, function-call, {@code in}, {@code ?:}, or list-literal-homogeneity operand-type violation, or a {@code STD-011 §8.2} field-reference ambiguity. */
        STATIC_TYPE_MISMATCH,
        /** The expression's statically inferred root type is provably inconsistent with its declared {@code EvaluationContext} (a predicate root that cannot be {@code bool}, or a transform root whose type contradicts its {@code DeclaredResultType}). */
        RESULT_TYPE_MISMATCH
    }

    private final boolean accepted;
    private final Reason reason;
    private final String diagnostic;
    private final CelFilterRecord record;
    private final OptionalLong cost;

    private VerificationResult(boolean accepted, Reason reason, String diagnostic,
            CelFilterRecord record, OptionalLong cost) {
        this.accepted = accepted;
        this.reason = reason;
        this.diagnostic = diagnostic;
        this.record = record;
        this.cost = cost;
    }

    /** An accepted result: decode, the cost gate, static type-checking (if a schema was supplied), and result-type consistency all passed. */
    public static VerificationResult accepted(CelFilterRecord record, long cost) {
        Objects.requireNonNull(record, "record");
        return new VerificationResult(true, null, null, record, OptionalLong.of(cost));
    }

    /** A rejection before decode succeeded: no record, no cost. */
    public static VerificationResult rejected(Reason reason, String diagnostic) {
        return rejected(reason, diagnostic, null, OptionalLong.empty());
    }

    /** A rejection after decode succeeded but before (or without) a computed cost -- e.g. the cost gate itself rejecting. */
    public static VerificationResult rejected(Reason reason, String diagnostic, CelFilterRecord record) {
        return rejected(reason, diagnostic, record, OptionalLong.empty());
    }

    /** A rejection after decode and the cost gate both succeeded (static type-checking or result-type consistency rejecting). */
    public static VerificationResult rejected(Reason reason, String diagnostic, CelFilterRecord record, long cost) {
        return rejected(reason, diagnostic, record, OptionalLong.of(cost));
    }

    private static VerificationResult rejected(Reason reason, String diagnostic, CelFilterRecord record, OptionalLong cost) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(diagnostic, "diagnostic");
        return new VerificationResult(false, reason, diagnostic, record, cost);
    }

    public boolean accepted() {
        return accepted;
    }

    /** @throws IllegalStateException if {@link #accepted()} is {@code true} */
    public Reason reason() {
        if (accepted) throw new IllegalStateException("accepted result has no reason");
        return reason;
    }

    /** @throws IllegalStateException if {@link #accepted()} is {@code true} */
    public String diagnostic() {
        if (accepted) throw new IllegalStateException("accepted result has no diagnostic");
        return diagnostic;
    }

    /** The decoded record, present iff T2 decode itself succeeded (i.e. always for an accepted result; present for every rejection reason except {@link Reason#DECODE_REJECTED}). */
    public Optional<CelFilterRecord> record() {
        return Optional.ofNullable(record);
    }

    /** The computed {@code C(E)}, present iff the cost gate itself was passed (always for an accepted result; present for {@link Reason#STATIC_TYPE_MISMATCH}/{@link Reason#RESULT_TYPE_MISMATCH} rejections; absent for {@link Reason#DECODE_REJECTED}/{@link Reason#COST_EXCEEDED}). */
    public OptionalLong cost() {
        return cost;
    }

    @Override
    public String toString() {
        return accepted
                ? "VerificationResult[accepted, cost=" + cost.getAsLong() + "]"
                : "VerificationResult[rejected, reason=" + reason + ", diagnostic=" + diagnostic + "]";
    }
}
