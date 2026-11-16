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
 * Evaluation of a verified JGDMS-STD-011 AST against a candidate (§6-§9). Where each concept lives:
 * <ul>
 *   <li>{@link au.net.zeus.jgdms.cel.eval.Evaluator}: the tree-walking REFERENCE evaluator. One
 *       evaluation of one AST, nothing retained between candidates.</li>
 *   <li>{@link au.net.zeus.jgdms.cel.eval.CompiledExpression}: the same semantics compiled once and
 *       evaluated over many candidates, with a per-thread {@code Session}. It must reproduce the
 *       reference's outcomes, evaluation order and exceptions; {@code CompiledEvaluatorDifferentialTest}
 *       holds it to that. Its executable nodes and the structural merging that feeds them are the
 *       package-private {@code CompiledNodes} and {@code CompiledGraph}.</li>
 *   <li>{@code CelSemantics} (package-private): the pure numeric and ordering rules both evaluators
 *       call, so they exist once.</li>
 *   <li>{@link au.net.zeus.jgdms.cel.eval.CandidateProjection}: the class-free candidate seam both
 *       evaluators read.</li>
 * </ul>
 * A change to a semantic rule is made in the reference and in {@code CelSemantics} first; the compiled
 * evaluator follows, and the differential test shows whether it did.
 */
package au.net.zeus.jgdms.cel.eval;
