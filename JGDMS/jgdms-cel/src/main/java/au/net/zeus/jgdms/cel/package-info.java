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
 * DETERMINISTIC CEL: the Java wire decoder and evaluator for the bounded,
 * side-effect-free, non-Turing-complete filter/transform expression language
 * defined by {@code JGDMS-STD-011-CEL-Filter-Expression-Format-v0.1-DRAFT.md}
 * and its {@code Appendix-B-DER-Wire-Encoding} companion.
 * <p>
 * This module is T3 of {@code SOW-CEL-Filter-Format.md}: a tree-walking
 * evaluator over the Appendix B DER wire form, in Java. It is
 * trusted-computing-base code -- the sole safety boundary between
 * adversary-authored wire input and this JVM -- and is written to the
 * fail-closed discipline that implies: a decode problem is always a hard
 * rejection with a diagnostic, never a partial abstract syntax tree, and
 * evaluation is total (a typed value or one of the closed eight error
 * codes, never an escaping exception for well-formed input).
 * <p>
 * Package layout:
 * <ul>
 *   <li>{@link au.net.zeus.jgdms.cel} -- the type system ({@link
 *       au.net.zeus.jgdms.cel.CelValue}, {@link au.net.zeus.jgdms.cel.CelType},
 *       {@link au.net.zeus.jgdms.cel.CelError}) and the total evaluation
 *       outcome ({@link au.net.zeus.jgdms.cel.EvalOutcome}).</li>
 *   <li>{@link au.net.zeus.jgdms.cel.ast} -- the closed abstract syntax tree
 *       node inventory (STD-011 §11.1).</li>
 *   <li>{@link au.net.zeus.jgdms.cel.wire} -- the Appendix B DER decoder:
 *       ceilings, canonical-form rejection, the function-id table.</li>
 *   <li>{@link au.net.zeus.jgdms.cel.cost} -- the static cost model
 *       (STD-011 §10), computed in checked 64-bit arithmetic.</li>
 *   <li>{@link au.net.zeus.jgdms.cel.eval} -- the evaluator and the
 *       {@link au.net.zeus.jgdms.cel.eval.CandidateProjection} interface a
 *       consumer (Outrigger, the bytecode-analysis-engine, etc.) binds to.</li>
 *   <li>{@link au.net.zeus.jgdms.cel.math} -- the correctly-rounded
 *       transcendental seam (STD-011 §7.5): a conformant provider is T3
 *       phase 2; this module ships only an explicitly non-conformant,
 *       opt-in-only placeholder and a fail-closed default.</li>
 * </ul>
 */
package au.net.zeus.jgdms.cel;
