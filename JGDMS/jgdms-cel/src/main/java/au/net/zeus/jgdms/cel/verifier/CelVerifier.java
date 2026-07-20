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

import au.net.zeus.jgdms.cel.cost.CostExceededException;
import au.net.zeus.jgdms.cel.cost.CostModel;
import au.net.zeus.jgdms.cel.wire.CelDecodeException;
import au.net.zeus.jgdms.cel.wire.CelDecoder;
import au.net.zeus.jgdms.cel.wire.CelFilterRecord;

/**
 * T6 of {@code SOW-CEL-Filter-Format.md}: the single registration-time
 * verification/load-gate for DETERMINISTIC CEL. One call --
 * {@link #verify(byte[], SchemaView)} -- composes every layer STD-011 §12
 * requires into one fail-closed accept/reject decision with a diagnostic, so
 * an integrator (Outrigger B3, BAE, a survey-instrument registrar) cannot
 * forget a layer by calling only part of the pipeline.
 *
 * <h2>The SOW §5 open question 3 ruling: additive vs. redundant, decomposed per check</h2>
 * The SOW's open question asks whether T6's gate is "genuinely additive to
 * T2's own decode-time rejection, or redundant with it." Having now built
 * both {@code CelDecoder} (T2) and this facade against it, the honest answer
 * is <b>neither, uniformly</b> -- it decomposes cleanly per STD-011 §12
 * obligation, and the two categories are structurally distinguishable, not
 * just conventionally distinguished:
 *
 * <h3>Decoder-owned (§12.1 structural well-formedness minus list-literal
 * homogeneity, §12.2 allowlist, §12.3's node/depth/selector-step ceilings):
 * genuinely redundant to re-implement, not merely "belt and braces"</h3>
 * This facade does <b>not</b> re-check canonical DER form, the closed
 * node-tag/arity/component-presence grammar, non-emptiness of {@code
 * LIST_LIT}/{@code FIELD_REF.steps}, the function-id/arity allowlist, or
 * {@code maxExprNodes}/{@code maxExprDepth}/{@code maxSelectorSteps}/{@code
 * maxScalarBytes}. The reason is stronger than "T2 already does this, so
 * let's not duplicate the work": <b>every {@link CelFilterRecord} this
 * facade ever holds was produced by {@code CelDecoder.decode(wire)}, because
 * this facade's only API surface is wire bytes</b> -- both {@code verify}
 * overloads take {@code byte[]}, and no overload accepting a pre-built
 * {@code CelFilterRecord} or {@code ExprNode} exists. That narrower claim is
 * the load-bearing one (review finding, 2026-07-20): {@code ExprNode}'s
 * records have ordinary public constructors enforcing only null checks, so a
 * hand-built AST <em>can</em> violate any ceiling -- which is exactly why no
 * AST-accepting overload may be added. <b>API-evolution constraint: adding a
 * {@code verify(CelFilterRecord)} overload would silently invalidate this
 * ruling</b>; any such change must re-open the SOW §5.3 redundancy decision
 * and either re-implement the decoder-owned checks or route through
 * re-encode-and-decode. Within the byte[]-only surface, re-implementing
 * these checks here would be dead code, unreachable by any wire input. This
 * ruling resolves STD-011 §12.2/§12.3's former "open per the SOW §5.3
 * redundancy decision" annotations -- see those sections' updated text.
 *
 * <h3>Verifier-owned (§12.3's cost ceiling, §12.4 static type-checking,
 * declared-result-type consistency, §B.6.6 list-literal homogeneity):
 * genuinely additive -- previously enforced nowhere, or enforced nowhere
 * <em>as an admission gate</em></h3>
 * <ul>
 *   <li><b>The cost gate.</b> This is the sharpest finding of this task:
 *   before this class existed, {@code CostModel.computeCost} was fully
 *   implemented and unit-tested but was <b>never invoked from any
 *   admission path in this module</b> -- {@code CelDecoder} does not call
 *   it, and nothing else did either. An expression could satisfy every
 *   node/depth/selector-step/scalar-length ceiling and still have an
 *   arbitrarily large {@code C(E)} (including the exact 2^32-adjacent
 *   wrap-hazard case {@code CostModelTest} already regression-tests at the
 *   {@code CostModel} level) and nothing would stop its registration. {@link
 *   #verify} is the first and only place {@code maxExprCost} is enforced as
 *   a mandatory accept/reject decision, not merely a computable value.</li>
 *   <li><b>Schema-dependent static type checking</b> ({@link
 *   StaticTypeChecker}, STD-011 §11.2/§12.4): operator/function operand
 *   types and field-reference types resolved against an optional {@link
 *   SchemaView}. {@code CelDecoder} is deliberately schema-decoupled (see
 *   {@code CostModel}'s own class javadoc making the identical
 *   architectural point about comparison-cost typing) and performs zero
 *   type checking of any kind -- this is a wholly new capability, not a
 *   re-check of anything T2 does.</li>
 *   <li><b>Declared-result-type consistency.</b> T2 decodes and structurally
 *   validates the {@code EvaluationContext}/{@code DeclaredResultType}
 *   shape (Appendix B §B.6.4) but never cross-checks it against the
 *   expression's own type -- a predicate whose root can only ever be
 *   {@code int}, or a transform whose root type contradicts its declared
 *   result type, decodes perfectly and would previously have been
 *   registered as-is. This facade is the first place that cross-check runs.</li>
 *   <li><b>List-literal homogeneity</b> (STD-011 §5.3.2). Appendix B §B.6.6
 *   states this explicitly: the wire grammar does not structurally enforce
 *   it, and it is assigned to T6, not T2. {@link StaticTypeChecker} folds it
 *   into the same bottom-up pass as the operand-type checks above.</li>
 * </ul>
 *
 * <h2>Fail-closed composition order</h2>
 * {@link #verify} runs, and stops at the first failure of: (1) T2 decode,
 * (2) the cost gate, (3) static type-checking (schema-optional), (4)
 * declared-result-type consistency -- each stage only runs once the
 * previous one has actually accepted, so e.g. an expression whose wire form
 * itself is malformed never reaches the cost gate, and one that exceeds
 * {@code maxExprCost} never reaches type-checking. This is a deliberate
 * cheapest-check-first ordering (decode and cost are wire/AST-local and
 * schema-free; type-checking is the most expensive and schema-dependent
 * stage) and is itself exercised by this module's facade-composition tests
 * via each {@link VerificationResult.Reason}'s distinct diagnostic prefix.
 */
public final class CelVerifier {

    private CelVerifier() {}

    /**
     * Verifies {@code wire} with no governing schema available at
     * registration time. The cost gate and declared-result-type consistency
     * check still run in full; static type-checking (STD-011 §12.4) defers
     * every field reference to {@code UNKNOWN} and can therefore only catch
     * schema-independent operand-type violations (e.g. {@code NOT} applied
     * to a literal {@code int}), never a field-reference type mismatch.
     */
    public static VerificationResult verify(byte[] wire) {
        return verify(wire, null);
    }

    /**
     * Verifies {@code wire} against an optional governing {@code schema},
     * composing T2 decode, the cost gate, static type-checking, and
     * declared-result-type consistency into one fail-closed accept/reject
     * decision -- see the class javadoc for the redundancy ruling and the
     * composition order this method enforces.
     *
     * @param wire   the candidate {@code CelFilterRecord} DER encoding
     * @param schema the registering consumer's governing schema for the
     *               expression's root candidate, or {@code null} if none is
     *               available (STD-011 §12.4: static type-checking is then
     *               skipped for field references specifically, not failed --
     *               every field reference simply defers to the always-on
     *               dynamic layer)
     * @return an accept-or-reject decision with a diagnostic (never {@code null})
     * @throws NullPointerException if {@code wire} is {@code null}
     */
    public static VerificationResult verify(byte[] wire, SchemaView schema) {
        if (wire == null) throw new NullPointerException("wire");

        final CelFilterRecord record;
        try {
            record = CelDecoder.decode(wire);
        } catch (CelDecodeException e) {
            return VerificationResult.rejected(VerificationResult.Reason.DECODE_REJECTED,
                    "decode rejected: " + e.getMessage());
        }

        final long cost;
        try {
            cost = CostModel.computeCost(record.expression());
        } catch (CostExceededException e) {
            return VerificationResult.rejected(VerificationResult.Reason.COST_EXCEEDED,
                    "cost gate rejected: " + e.getMessage(), record);
        }

        final StaticTypeChecker.Inferred rootType;
        try {
            rootType = StaticTypeChecker.infer(record.expression(), schema);
        } catch (StaticTypeChecker.Rejected e) {
            return VerificationResult.rejected(VerificationResult.Reason.STATIC_TYPE_MISMATCH,
                    "static type check rejected: " + e.getMessage(), record, cost);
        }

        String resultTypeDiag = StaticTypeChecker.checkResultTypeConsistency(record.context(), rootType);
        if (resultTypeDiag != null) {
            return VerificationResult.rejected(VerificationResult.Reason.RESULT_TYPE_MISMATCH,
                    "declared-result-type check rejected: " + resultTypeDiag, record, cost);
        }

        return VerificationResult.accepted(record, cost);
    }
}
