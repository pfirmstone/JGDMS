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

/**
 * T6 of {@code SOW-CEL-Filter-Format.md}: the registration-time
 * verification/load-gate for DETERMINISTIC CEL (STD-011 §12).
 * <p>
 * {@link au.net.zeus.jgdms.cel.verifier.CelVerifier} is the sole entry
 * point -- one call composes T2 decode ({@code
 * au.net.zeus.jgdms.cel.wire.CelDecoder}), the {@code maxExprCost} cost gate
 * ({@code au.net.zeus.jgdms.cel.cost.CostModel}), schema-optional static
 * type-checking, and declared-result-type consistency into one fail-closed
 * accept/reject decision ({@link
 * au.net.zeus.jgdms.cel.verifier.VerificationResult}). See {@code
 * CelVerifier}'s class javadoc for the SOW §5 open-question-3 redundancy
 * ruling (which checks are decoder-owned vs. verifier-owned, and why).
 * <p>
 * {@link au.net.zeus.jgdms.cel.verifier.SchemaView} is the registration-time,
 * per-class-hierarchy counterpart of {@link
 * au.net.zeus.jgdms.cel.eval.CandidateProjection} (that interface is
 * per-instance and evaluation-time); {@link
 * au.net.zeus.jgdms.cel.verifier.DerSchemaChainView} adapts a real {@code
 * jgdms-der} {@code AtomicSerialSchemaRecord} chain to it.
 * <p>
 * <b>Acceptance never licenses skipping dynamic enforcement</b> (STD-011
 * §12.4's both-layers rule): a filter accepted by {@code CelVerifier} MUST
 * still be evaluated by {@code au.net.zeus.jgdms.cel.eval.Evaluator}, whose
 * runtime type/ambiguity/homogeneity checks always run -- static
 * verification is an early, schema-informed rejection opportunity, never a
 * substitute for the evaluator's own checks.
 * <p>
 * {@code SchemaView} implementations are trusted registration-time code, not
 * attacker-controlled wire input: exceptions they throw propagate to the
 * caller <em>deliberately</em> and are not mapped to a fail-closed
 * verification reject.
 */
package au.net.zeus.jgdms.cel.verifier;
