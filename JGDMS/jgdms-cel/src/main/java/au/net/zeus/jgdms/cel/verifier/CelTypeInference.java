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
import au.net.zeus.jgdms.cel.ast.ExprNode;

import java.util.Optional;

/**
 * The one public window onto {@link StaticTypeChecker}'s bottom-up static
 * type inference: "what scalar {@link CelType} does this {@code ExprNode}
 * have against this {@link SchemaView}, if statically determinable?" -- the
 * exact same inference {@link CelVerifier} runs, exposed for a consumer (e.g.
 * the authoring toolchain's text parser) that must resolve a type-dispatched
 * platform-function overload (Appendix B §B.8.3: {@code size}/{@code abs}/
 * {@code min}/{@code max}) from an operand's declared type <em>without</em>
 * standing up a second, divergent type system (G8).
 * <p>
 * The result is deliberately {@link Optional}: {@link Optional#empty()} means
 * "not statically resolvable here" -- an unknown/absent field (defer, as
 * {@link SchemaView} documents), or a subexpression this checker can prove is
 * ill-typed. In both cases a caller resolving an overload has no sound wire
 * id to choose and MUST fail closed. A present value is a positively inferred
 * type, never a guess (the same soundness discipline {@link StaticTypeChecker}
 * enforces everywhere).
 */
public final class CelTypeInference {

    private CelTypeInference() {}

    /**
     * Infers the statically-known {@link CelType} of {@code node} against
     * {@code schema}.
     *
     * @param node   the AST node to type (never {@code null})
     * @param schema the governing schema, or {@code null} for the schema-free
     *               case (every field reference then infers to empty)
     * @return the inferred scalar type, or {@link Optional#empty()} if the
     *         type is not statically determinable or the subexpression is
     *         provably ill-typed
     */
    public static Optional<CelType> inferType(ExprNode node, SchemaView schema) {
        if (node == null) throw new NullPointerException("node");
        try {
            StaticTypeChecker.Inferred inferred = StaticTypeChecker.infer(node, schema);
            return inferred.isUnknown() ? Optional.empty() : Optional.of(inferred.type());
        } catch (StaticTypeChecker.Rejected rejected) {
            // A provably ill-typed subexpression yields no sound type to a caller
            // resolving an overload; surface it as "not resolvable" so the caller
            // fails closed. The full CelVerifier still reports the specific reason.
            return Optional.empty();
        }
    }
}
