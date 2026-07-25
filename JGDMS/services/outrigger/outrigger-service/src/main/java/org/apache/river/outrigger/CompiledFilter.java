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

import au.net.zeus.jgdms.cel.ast.ExprNode;

/**
 * An immutable, server-side, verified CEL predicate ready to gate a query
 * (SOW Part&nbsp;B, unit&nbsp;B1). A {@code CompiledFilter} is produced <b>only</b>
 * by {@link FilterAdmission#admit}, after the filter envelope has been decoded,
 * the CEL wire bytes have passed {@code CelVerifier.verify} against the
 * template's own v2 schema, and the evaluation context has been confirmed to be
 * a {@code Predicate} (never a {@code Transform}). Holding an instance therefore
 * means "a verified boolean predicate exists"; it does not itself evaluate.
 *
 * <p>Predicate <em>evaluation</em> against candidate entries at the match
 * chokepoints is unit&nbsp;B3. This class deliberately carries the verified AST
 * ({@link #expr()}), the verifier's static {@link #cost()} estimate, and the
 * template's {@link #applicabilitySchemaDigest() applicability key} so that B3
 * can (a) walk the predicate against a candidate's schema-projected fields and
 * (b) apply the fail-closed applicability rule — a candidate whose
 * {@code entrySchemaDigest} does not match the key is a non-match — without
 * re-doing admission.
 *
 * @since JGDMS 4.0.0
 */
public final class CompiledFilter {

    private final ExprNode expr;
    private final long cost;
    private final byte[] applicabilitySchemaDigest; // null for a schema-less (match-any) filter

    CompiledFilter(ExprNode expr, long cost, byte[] applicabilitySchemaDigest) {
        if (expr == null) throw new NullPointerException("expr");
        this.expr = expr;
        this.cost = cost;
        this.applicabilitySchemaDigest =
                (applicabilitySchemaDigest == null) ? null : applicabilitySchemaDigest.clone();
    }

    /**
     * @return the verified predicate AST (never null)
     */
    public ExprNode expr() {
        return expr;
    }

    /**
     * @return the verifier's static cost estimate for this predicate
     */
    public long cost() {
        return cost;
    }

    /**
     * The applicability key: the {@code entrySchemaDigest} of the template this
     * filter was admitted against, or {@code null} if it was admitted
     * schema-lessly (a match-any / null template).
     *
     * <p><b>Non-null key</b> — the filter applies only to candidates whose
     * {@code entrySchemaDigest} equals this key; a candidate of a different
     * schema is a non-match (B3).
     *
     * <p><b>Null key</b> — the filter applies to <em>all</em> candidates
     * (Peter's ruling, OPTION&nbsp;(b)); see {@link #isSchemaLess()} for the
     * per-candidate field-resolution rule this implies.
     *
     * @return a copy of the applicability digest, or {@code null}
     */
    public byte[] applicabilitySchemaDigest() {
        return (applicabilitySchemaDigest == null) ? null : applicabilitySchemaDigest.clone();
    }

    /**
     * Whether this filter was admitted <em>schema-lessly</em> — against a null /
     * match-any template, for which there was no single template schema to
     * type-check the predicate against.
     *
     * <p><b>Schema-less does NOT mean "references no fields".</b> The verifier
     * soundly <em>defers</em> unknown field references to a dynamic outcome
     * (never a static rejection), so a schema-less filter MAY reference fields.
     * Per Peter's ruling (OPTION&nbsp;(b)), a schema-less (null-applicability-key)
     * filter applies to ALL candidates, and each referenced field name is
     * resolved <b>per candidate</b>, against that candidate's own v2 schema, at
     * evaluation time (B3). A candidate whose schema lacks a referenced field is
     * a FAIL-CLOSED exclusion (counted in
     * {@code filter.failClosedExclusions}), never a match and never an error.
     *
     * @return {@code true} if this filter has a null applicability key (admitted
     *         against a null / match-any template)
     */
    public boolean isSchemaLess() {
        return applicabilitySchemaDigest == null;
    }
}
