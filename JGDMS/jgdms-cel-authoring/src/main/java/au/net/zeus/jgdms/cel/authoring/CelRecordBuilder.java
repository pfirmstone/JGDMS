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

package au.net.zeus.jgdms.cel.authoring;

import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.wire.CelFilterRecord;
import au.net.zeus.jgdms.cel.wire.DeclaredResultType;
import au.net.zeus.jgdms.cel.wire.EvaluationContext;
import au.net.zeus.jgdms.cel.wire.ScalarType;

/**
 * Assembles a full {@code CelFilterRecord} (formatVersion 1) from a parsed
 * {@link ExprNode} and a programmatically-supplied {@link EvaluationContext}.
 * <p>
 * STD-011 §5's textual grammar defines only the {@code Expr} production -- there
 * is no textual syntax for the {@code EvaluationContext}/{@code DeclaredResultType}
 * envelope -- so the context (predicate vs transform, and a transform's declared
 * result type) is chosen here in code rather than parsed from text. This keeps
 * the parser honest: it never invents envelope syntax the standard does not
 * define.
 */
public final class CelRecordBuilder {

    private CelRecordBuilder() {}

    /** Builds a predicate-context record (result type is always {@code bool}, §9.4). */
    public static CelFilterRecord predicate(ExprNode expression) {
        return new CelFilterRecord(CelFilterRecord.FORMAT_VERSION, new EvaluationContext.Predicate(), expression);
    }

    /** Builds a transform-context record with an explicit declared result type. */
    public static CelFilterRecord transform(ExprNode expression, DeclaredResultType resultType) {
        return new CelFilterRecord(CelFilterRecord.FORMAT_VERSION,
                new EvaluationContext.Transform(resultType), expression);
    }

    /** Convenience: transform returning a scalar of the given type. */
    public static CelFilterRecord transformScalar(ExprNode expression, ScalarType type) {
        return transform(expression, new DeclaredResultType.Scalar(type));
    }

    /** Convenience: transform returning {@code list<T>} of the given scalar element type. */
    public static CelFilterRecord transformList(ExprNode expression, ScalarType elementType) {
        return transform(expression, new DeclaredResultType.ListType(elementType));
    }

    /** Parses {@code text} to an AST and wraps it as a predicate-context record. */
    public static CelFilterRecord parsePredicate(String text) throws CelParseException {
        return predicate(CelTextParser.parse(text));
    }
}
