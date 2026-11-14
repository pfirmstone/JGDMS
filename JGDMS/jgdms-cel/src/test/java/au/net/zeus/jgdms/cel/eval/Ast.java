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

import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.wire.FunctionRegistry;

import java.util.List;

/** Short constructors for hand-built ASTs in tests. Test tree only. */
final class Ast {

    private Ast() {}

    static ExprNode.LitInt i(long v) {
        return new ExprNode.LitInt(v);
    }

    static ExprNode.LitDouble d(double v) {
        return new ExprNode.LitDouble(v);
    }

    static ExprNode.LitString s(String v) {
        return new ExprNode.LitString(v);
    }

    static ExprNode.LitBool b(boolean v) {
        return new ExprNode.LitBool(v);
    }

    /** An unqualified one-step reference. */
    static ExprNode.FieldRef f(String name) {
        return new ExprNode.FieldRef(List.of(new ExprNode.SelectorStep.Unqual(name)));
    }

    /** A qualified one-step reference. */
    static ExprNode.FieldRef q(String className, String fieldName) {
        return new ExprNode.FieldRef(List.of(new ExprNode.SelectorStep.Qual(className, fieldName)));
    }

    static ExprNode.ListLit list(ExprNode... elements) {
        return new ExprNode.ListLit(List.of(elements));
    }

    static ExprNode.Div div(ExprNode l, ExprNode r) {
        return new ExprNode.Div(l, r);
    }

    /** {@code 1 / 0}: DIVISION_BY_ZERO. */
    static ExprNode divByZero() {
        return div(i(1), i(0));
    }

    /** The registry's pinned wire id of {@code name} (Appendix B §B.8.3), looked up so the table is not written out again. */
    static int id(FunctionRegistry.Name name) {
        for (int id = 1; id <= 24; id++) {
            FunctionRegistry.Entry e = FunctionRegistry.byId(id);
            if (e != null && e.name() == name) return id;
        }
        throw new IllegalStateException(name.name());
    }

    static ExprNode.Call call(FunctionRegistry.Name name, ExprNode... args) {
        return new ExprNode.Call(id(name), List.of(args));
    }

    /** A call that throws IllegalStateException under {@code MathProvider.none()} if it is ever evaluated. */
    static ExprNode.Call sinOfOne() {
        return call(FunctionRegistry.Name.SIN, d(1.0));
    }
}
