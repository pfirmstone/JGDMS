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

import au.net.zeus.jgdms.cel.CelType;

import java.util.List;
import java.util.Optional;

/**
 * A registration-time, class-level view of a governing schema (JGDMS-STD-011
 * §12.4: "when the consumer holds a governing schema... the expression MUST
 * be type-checked against it"), abstracted so {@link CelVerifier} /
 * {@link StaticTypeChecker} do not hard-couple to {@code jgdms-der}'s
 * {@code AtomicSerialSchemaRecord} chain -- an integrator with some other
 * source of schema truth can implement this directly. {@link
 * DerSchemaChainView} adapts a real {@code AtomicSerialSchemaRecord} chain
 * for the common case.
 * <p>
 * This is deliberately the <em>static, per-class-hierarchy</em> counterpart
 * of {@link au.net.zeus.jgdms.cel.eval.CandidateProjection}: that interface
 * is a per-<em>instance</em>, evaluation-time view over one decoded
 * candidate (it can answer {@code fieldValue}); this interface is a
 * per-<em>class</em>, registration-time view over a schema with no instance
 * data at all (it can only ever answer "what type would this field be").
 * The namespace-chain / {@code declaresField} shape deliberately mirrors
 * {@code CandidateProjection} so the two are easy to reason about side by
 * side -- the same STD-011 §8.2 uniqueness-or-error resolution rule applies
 * to both, just statically here instead of dynamically.
 * <p>
 * <b>Soundness contract (STD-011 §12.4's both-layers rule).</b> Every method
 * here answers "what is knowable from the schema alone, right now" --
 * {@link #fieldType(String, String)} and {@link #nestedSchema(String,
 * String)} return {@link Optional#empty()} whenever a type (or, for a
 * further selector step, a concrete nested-object schema) cannot be
 * statically pinned: an {@code Any}/unresolved wire type, a polymorphic
 * interface/abstract-class-typed field (STD-006's nested-object encoding is
 * deliberately polymorphic -- the runtime class travels with the instance,
 * not with the schema), a collection element type, or simply a field this
 * particular schema snapshot does not declare. {@link CelVerifier} and
 * {@link StaticTypeChecker} treat every such {@code empty()} as "defer to
 * dynamic evaluation" -- <em>never</em> as a type mismatch and
 * <em>never</em> as a registration-time rejection. This is required, not
 * merely permitted: STD-011 §12.4 requires both a static layer and an
 * always-on dynamic layer specifically because schema skew (a candidate's
 * own schema may differ from the registration-time schema, STD-006 §11
 * evolution) and genuinely unresolvable declarations are expected outcomes,
 * not exceptional ones. An implementation that instead rejected an unknown
 * field, or guessed at an unresolved type, would make registration reject
 * expressions that are perfectly valid against candidates that show up later
 * -- exactly the "declaration-vs-drift hazard" STD-011 §8.2 already argues
 * against for the dynamic case.
 */
public interface SchemaView {

    /**
     * The ordered namespace chain for the class this view represents: class
     * names that declare its own fields, most-derived (leaf) class first
     * (STD-006 §3.9) -- the same order {@link
     * au.net.zeus.jgdms.cel.eval.CandidateProjection#namespaceChain()} uses
     * at evaluation time. Used to implement STD-011 §8.2's
     * uniqueness-or-error unqualified resolution rule statically: an
     * unqualified name declared by more than one namespace in this chain is
     * {@code AMBIGUOUS_FIELD}, a hard reject, not a guess.
     */
    List<String> namespaceChain();

    /**
     * True iff {@code className} (a member of {@link #namespaceChain()})
     * declares {@code fieldName} in its own namespace -- schema presence,
     * independent of whether the field's type is statically resolvable.
     */
    boolean declaresField(String className, String fieldName);

    /**
     * The statically known {@link CelType} of {@code fieldName} within
     * {@code className}'s namespace, or {@link Optional#empty()} if the
     * schema declares the field but its wire type cannot be pinned to one
     * of the eight expression types (an {@code Any}/unresolved element
     * type, for instance). Only called when {@link #declaresField(String,
     * String)} for the same pair is {@code true}.
     */
    Optional<CelType> fieldType(String className, String fieldName);

    /**
     * When {@link #fieldType(String, String)} for the same pair is present
     * and equal to {@link CelType#OBJECT}, this field's own nested-object
     * {@code SchemaView} -- <em>if and only if</em> the field's concrete
     * nested class is itself statically known. A plain {@code
     * @AtomicSerial}-typed field is deliberately polymorphic on the wire
     * (STD-006's nested-object encoding: the runtime class travels with the
     * instance, not with the schema), so a generic adapter cannot supply
     * one for the common case. Returning {@link Optional#empty()} is always
     * sound: it makes every selector step past this one defer to dynamic
     * evaluation rather than guess at a class that may not match the actual
     * runtime value.
     */
    Optional<SchemaView> nestedSchema(String className, String fieldName);
}
