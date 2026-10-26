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

import java.util.List;

/**
 * The class-free candidate projection an evaluation observes (JGDMS-STD-011
 * §2's "Projection" definition, §8.1). This is the seam the SOW calls out
 * explicitly: it is deliberately decoupled from any particular service or
 * wire codec, so that Outrigger's B3 field-projection work and the
 * bytecode-analysis-engine (or anything else that can produce a {@code
 * className -> (fieldName -> typed value)} map) can each bind their own
 * implementation without this module depending on either.
 * <p>
 * One {@code CandidateProjection} instance represents <em>one object</em> --
 * the root candidate, or a nested {@code object}-typed field's own value
 * ({@link CelValue.ObjectV}) -- together with its own schema chain (STD-006
 * §3.9): the ordered list of per-class namespaces that declare its fields,
 * most-derived class first ("leaf first").
 * <p>
 * Implementations MUST be side-effect-free and MUST return the identical
 * answer for the identical {@code (className, fieldName)} query across the
 * lifetime of one evaluation (STD-011 §9.6's evaluation-context isolation) --
 * the evaluator may call these methods more than once for the same query.
 * For {@link #fieldValue(String, String)} "identical answer" is normative in
 * the strong, reference sense -- see that method's stability precondition.
 */
public interface CandidateProjection {

    /**
     * True if this projection's own decode failed (STD-011 §8.1:
     * canonical-form rejection, schema-digest mismatch, or any other
     * fail-closed projection failure). When {@code true}, every method below
     * MUST NOT be called by the evaluator -- any field access or {@code
     * has()} against an undecodable candidate is the error {@code
     * CANDIDATE_UNDECODABLE} (§9.2) at the point this projection is reached,
     * without inspecting namespaces or fields at all.
     */
    boolean isUndecodable();

    /**
     * The ordered namespace chain for this object: class names that declare
     * this object's own fields, most-derived (leaf) class first (STD-006
     * §3.9). Used by the evaluator to implement §8.2's uniqueness-or-error
     * unqualified resolution rule. Never called if {@link #isUndecodable()}
     * is {@code true}.
     */
    List<String> namespaceChain();

    /**
     * True iff {@code className} (a member of {@link #namespaceChain()})
     * declares {@code fieldName} in its own namespace -- i.e. schema
     * presence, independent of whether the value itself is {@code null}
     * (STD-011 §4.6 distinguishes "present, null" from "absent"). Never
     * called if {@link #isUndecodable()} is {@code true}.
     */
    boolean declaresField(String className, String fieldName);

    /**
     * The already-widened (STD-011 §4.2) value of {@code fieldName} within
     * {@code className}'s namespace. MUST only be called when {@link
     * #declaresField(String, String)} for the same pair is {@code true}. A
     * wire-null field yields {@link CelValue.NullV#INSTANCE}, never Java
     * {@code null}.
     * <p>
     * <b>Precondition (normative) -- reference stability.</b> Within one
     * evaluation, repeated calls with the identical {@code (className,
     * fieldName)} pair MUST return the <b>identical instance</b> ({@code ==},
     * not merely {@code equals}), and for a {@link CelValue.StringV} /
     * {@link CelValue.BytesV} the wrapped {@code String} / {@code byte[]} MUST
     * likewise be the identical instance. This is stronger than the
     * type-level "identical answer" wording above, and it is load-bearing, not
     * stylistic: the evaluator memoizes {@code size(string)} /
     * {@code size(bytes)} per candidate in an {@code IdentityHashMap} keyed on
     * the returned value, so an implementation that returned an
     * {@code equals}-but-not-{@code ==} instance per call would silently defeat
     * that memo and re-scan the value on every AST occurrence -- reinstating
     * the unbounded {@code O(len) x AST-node-count} walk term that the ratified
     * per-evaluation cost bound (JGDMS Outrigger CEL design memo B3 §4.4)
     * depends on being removed. An implementation that decodes eagerly at
     * construction and merely selects here (the reference implementation,
     * Outrigger's {@code EntryProjection}) satisfies this by construction; one
     * that decodes lazily MUST cache and return the cached instance.
     * <p>
     * <b>Precondition (normative):</b> any {@link CelValue.StringV} returned
     * MUST be well-formed UTF-16 -- a valid Unicode scalar-value sequence,
     * with no lone (unpaired) surrogate code units. This is not enforced at
     * runtime by the evaluator: the DER decode path (STD-006/Appendix B)
     * never produces an ill-formed {@code StringV} in the first place, so
     * this is a trust-boundary precondition on the implementation, exactly
     * like the rest of this interface's contract. The behaviour of string
     * operations (comparison, {@code contains}/{@code startsWith}/{@code
     * endsWith}, code-point ordering per §4.4) on an ill-formed value that
     * somehow reaches the evaluator anyway is undefined.
     */
    CelValue fieldValue(String className, String fieldName);
}
