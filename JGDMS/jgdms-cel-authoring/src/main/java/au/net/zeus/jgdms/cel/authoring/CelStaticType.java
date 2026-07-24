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
import au.net.zeus.jgdms.cel.wire.FunctionRegistry;

/**
 * A deliberately-partial static type inference over the AST, used for the two
 * jobs a standalone (schema-free) authoring tool can still do soundly:
 * <ol>
 *   <li>resolving the type-dispatched platform-function overloads
 *       ({@code size}/{@code abs}/{@code min}/{@code max}, whose text name maps
 *       to several distinct Appendix B §B.8.3 wire ids by operand type), and</li>
 *   <li>proving the definite half of STD-011 §5.3.2 list-literal homogeneity
 *       (rejecting a list whose elements have two <em>known</em> distinct
 *       scalar types, or a definitely non-scalar element).</li>
 * </ol>
 * Where an expression's type is not statically determinable without a schema
 * (a field reference, or arithmetic over one), this returns {@link #UNKNOWN}:
 * the tool then defers -- to the caller's overload guidance for functions, and
 * to T6/evaluation for homogeneity (Appendix B §B.6.6 assigns full homogeneity
 * enforcement to T6/eval, not the wire layer, so deferring an unprovable case
 * is spec-faithful, not a gap).
 */
enum CelStaticType {
    BOOL, INT, DOUBLE, STRING, BYTES, LIST, NULL, OBJECT, UNKNOWN;

    /** True for the five scalar expression types admissible as list elements (§5.3.2). */
    boolean isScalar() {
        return this == BOOL || this == INT || this == DOUBLE || this == STRING || this == BYTES;
    }

    static CelStaticType of(ExprNode node) {
        return switch (node) {
            case ExprNode.LitBool b -> BOOL;
            case ExprNode.LitInt i -> INT;
            case ExprNode.LitDouble d -> DOUBLE;
            case ExprNode.LitString s -> STRING;
            case ExprNode.LitBytes b -> BYTES;
            case ExprNode.LitNull n -> NULL;
            case ExprNode.ListLit l -> LIST;
            case ExprNode.FieldRef f -> UNKNOWN;      // needs a schema
            case ExprNode.Has h -> BOOL;
            case ExprNode.Not n -> BOOL;
            case ExprNode.Neg n -> of(n.operand());   // int or double, propagate
            case ExprNode.And a -> BOOL;
            case ExprNode.Or o -> BOOL;
            case ExprNode.Eq e -> BOOL;
            case ExprNode.Ne e -> BOOL;
            case ExprNode.Lt r -> BOOL;
            case ExprNode.Le r -> BOOL;
            case ExprNode.Gt r -> BOOL;
            case ExprNode.Ge r -> BOOL;
            case ExprNode.In in -> BOOL;
            case ExprNode.Add x -> numeric(x.left(), x.right());
            case ExprNode.Sub x -> numeric(x.left(), x.right());
            case ExprNode.Mul x -> numeric(x.left(), x.right());
            case ExprNode.Div x -> numeric(x.left(), x.right());
            case ExprNode.Mod x -> numeric(x.left(), x.right());
            case ExprNode.Cond c -> commonBranch(c.thenBranch(), c.elseBranch());
            case ExprNode.Call c -> callResultType(c.functionId());
        };
    }

    /** Numeric-result inference for {@code + - * / %}: DOUBLE if either operand is DOUBLE, INT if either is INT, else UNKNOWN. */
    private static CelStaticType numeric(ExprNode l, ExprNode r) {
        CelStaticType lt = of(l), rt = of(r);
        if (lt == DOUBLE || rt == DOUBLE) return DOUBLE;
        if (lt == INT || rt == INT) return INT;
        return UNKNOWN;
    }

    private static CelStaticType commonBranch(ExprNode t, ExprNode e) {
        CelStaticType tt = of(t), et = of(e);
        if (tt == et) return tt;
        if (tt == UNKNOWN) return et;
        if (et == UNKNOWN) return tt;
        return UNKNOWN;
    }

    /** The pinned result type of each registry function id (Appendix B §B.8.3). */
    private static CelStaticType callResultType(int functionId) {
        return switch (functionId) {
            case 1, 2, 3, 4, 6 -> INT;                       // size(*)->int, int(double)->int, abs(int)->int
            case 8, 10 -> INT;                               // min/max int -> int
            case 5, 7, 12, 13, 14, 15, 16, 17, 18, 19, 20 -> DOUBLE; // double(int), abs(double), sqrt..atan
            case 9, 11, 21 -> DOUBLE;                        // min/max double, atan2
            case FunctionRegistry.CONTAINS_ID,
                 FunctionRegistry.STARTS_WITH_ID,
                 FunctionRegistry.ENDS_WITH_ID -> BOOL;      // 22/23/24
            default -> UNKNOWN;
        };
    }
}
