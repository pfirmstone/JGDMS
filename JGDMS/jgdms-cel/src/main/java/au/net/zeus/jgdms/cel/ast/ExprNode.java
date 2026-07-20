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

package au.net.zeus.jgdms.cel.ast;

import java.util.Arrays;
import java.util.List;

/**
 * The complete, closed abstract syntax tree node inventory of JGDMS-STD-011
 * §11.1 / Appendix B §B.4.1 -- exactly 27 node kinds, no others. This sealed
 * interface and its nested record implementations are the entire AST: the
 * wire decoder ({@code au.net.zeus.jgdms.cel.wire.CelDecoder}) MUST reject
 * (never approximate or default) any wire content that would require a node
 * kind outside this list, and the evaluator's dispatch over this hierarchy
 * is exhaustive by construction (a Java {@code switch} over a sealed type
 * with no {@code default} arm is a compile error if a case is missing).
 * <p>
 * Every nested type here is declared in this single file, so the Java
 * compiler infers the {@code permits} set automatically (JLS 8.1.1.2) --
 * there is no separate node type declarable anywhere else in the module.
 */
public sealed interface ExprNode {

    // ---- literals: tags [0]-[5], arity 0 -------------------------------

    record LitBool(boolean value) implements ExprNode {}

    record LitInt(long value) implements ExprNode {}

    /** The double is guaranteed finite (non-NaN, non-Inf) by decode-time rejection (Appendix B §B.5 item 5). */
    record LitDouble(double value) implements ExprNode {}

    /** Already validated as well-formed, non-overlong UTF-8 and within {@code maxScalarBytes} at decode time. */
    record LitString(String value) implements ExprNode {
        public LitString {
            if (value == null) throw new NullPointerException("value");
        }
    }

    /** Already validated within {@code maxScalarBytes} at decode time. */
    record LitBytes(byte[] value) implements ExprNode {
        public LitBytes {
            if (value == null) throw new NullPointerException("value");
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LitBytes other)) return false;
            return Arrays.equals(value, other.value);
        }
        @Override public int hashCode() { return Arrays.hashCode(value); }
        @Override public String toString() { return "LitBytes[" + value.length + " bytes]"; }
    }

    record LitNull() implements ExprNode {}

    // ---- structural / reference: tags [6]-[8] --------------------------

    /** Non-empty per §5.3.2; element count already bounded by {@code maxCollection} at decode time. */
    record ListLit(List<ExprNode> elements) implements ExprNode {
        public ListLit {
            if (elements == null) throw new NullPointerException("elements");
            if (elements.isEmpty()) throw new IllegalArgumentException("ListLit must be non-empty (STD-011 §5.3.2)");
            elements = List.copyOf(elements);
        }
    }

    /** Non-empty selector chain, already bounded by {@code maxSelectorSteps} at decode time. */
    record FieldRef(List<SelectorStep> steps) implements ExprNode {
        public FieldRef {
            if (steps == null) throw new NullPointerException("steps");
            if (steps.isEmpty()) throw new IllegalArgumentException("FieldRef must have at least one selector step");
            steps = List.copyOf(steps);
        }
    }

    /** {@code has(target)}; {@code target} is structurally a field designator (§5.3.1 item 2), never a general expression. */
    record Has(FieldRef target) implements ExprNode {
        public Has {
            if (target == null) throw new NullPointerException("target");
        }
    }

    // ---- unary: tags [9]-[10] -------------------------------------------

    record Not(ExprNode operand) implements ExprNode {
        public Not { if (operand == null) throw new NullPointerException("operand"); }
    }

    record Neg(ExprNode operand) implements ExprNode {
        public Neg { if (operand == null) throw new NullPointerException("operand"); }
    }

    // ---- binary: tags [11]-[23] ------------------------------------------

    record And(ExprNode left, ExprNode right) implements ExprNode { public And { requireNonNull(left, right); } }
    record Or(ExprNode left, ExprNode right) implements ExprNode { public Or { requireNonNull(left, right); } }
    record Eq(ExprNode left, ExprNode right) implements ExprNode { public Eq { requireNonNull(left, right); } }
    record Ne(ExprNode left, ExprNode right) implements ExprNode { public Ne { requireNonNull(left, right); } }
    record Lt(ExprNode left, ExprNode right) implements ExprNode { public Lt { requireNonNull(left, right); } }
    record Le(ExprNode left, ExprNode right) implements ExprNode { public Le { requireNonNull(left, right); } }
    record Gt(ExprNode left, ExprNode right) implements ExprNode { public Gt { requireNonNull(left, right); } }
    record Ge(ExprNode left, ExprNode right) implements ExprNode { public Ge { requireNonNull(left, right); } }
    record Add(ExprNode left, ExprNode right) implements ExprNode { public Add { requireNonNull(left, right); } }
    record Sub(ExprNode left, ExprNode right) implements ExprNode { public Sub { requireNonNull(left, right); } }
    record Mul(ExprNode left, ExprNode right) implements ExprNode { public Mul { requireNonNull(left, right); } }
    record Div(ExprNode left, ExprNode right) implements ExprNode { public Div { requireNonNull(left, right); } }
    record Mod(ExprNode left, ExprNode right) implements ExprNode { public Mod { requireNonNull(left, right); } }

    // ---- membership / conditional / call: tags [24]-[26] -----------------

    record In(ExprNode needle, InListOperand listOperand) implements ExprNode {
        public In {
            if (needle == null) throw new NullPointerException("needle");
            if (listOperand == null) throw new NullPointerException("listOperand");
        }
    }

    record Cond(ExprNode condition, ExprNode thenBranch, ExprNode elseBranch) implements ExprNode {
        public Cond {
            if (condition == null) throw new NullPointerException("condition");
            if (thenBranch == null) throw new NullPointerException("thenBranch");
            if (elseBranch == null) throw new NullPointerException("elseBranch");
        }
    }

    /**
     * {@code functionId} is the Appendix B §B.8.3 pinned registry id
     * (1..24) -- never a wire-supplied name resolved dynamically.
     */
    record Call(int functionId, List<ExprNode> arguments) implements ExprNode {
        public Call {
            if (arguments == null) throw new NullPointerException("arguments");
            arguments = List.copyOf(arguments);
        }
    }

    // ---- selector chain components (not ExprNode themselves) -------------

    /** One step of a {@link FieldRef} selector chain (Appendix B §B.3's {@code SelectorStep}). */
    sealed interface SelectorStep {
        record Unqual(String name) implements SelectorStep {
            public Unqual { if (name == null) throw new NullPointerException("name"); }
        }
        record Qual(String className, String fieldName) implements SelectorStep {
            public Qual {
                if (className == null) throw new NullPointerException("className");
                if (fieldName == null) throw new NullPointerException("fieldName");
            }
        }
    }

    /** The right operand of {@link In} (Appendix B §B.3's {@code InListOperand}): a list literal or a field designator, never any other expression shape (§5.3.1 item 6). */
    sealed interface InListOperand {
        record LiteralList(ListLit list) implements InListOperand {
            public LiteralList { if (list == null) throw new NullPointerException("list"); }
        }
        record FieldList(FieldRef field) implements InListOperand {
            public FieldList { if (field == null) throw new NullPointerException("field"); }
        }
    }

    private static void requireNonNull(ExprNode left, ExprNode right) {
        if (left == null) throw new NullPointerException("left");
        if (right == null) throw new NullPointerException("right");
    }
}
