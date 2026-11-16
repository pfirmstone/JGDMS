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

package au.net.zeus.jgdms.bench.cel;

import au.net.zeus.jgdms.cel.CelValue;
import au.net.zeus.jgdms.cel.EvalOutcome;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.eval.Evaluator;
import au.net.zeus.jgdms.cel.math.MathProvider;

/**
 * One production {@link Evaluator} over one expression and one flyweight projection: the unit the benchmarks and the
 * correctness test both drive, so that the timed path and the checked path are the same code.
 * <p>
 * The evaluator is built ONCE and reused across records. Its only per-instance state is a memo for {@code size(string)},
 * which these expressions never reach; its javadoc says the Outrigger usage builds one per candidate, and the cost of that
 * construction (an {@code IdentityHashMap}) is NOT measured here. Not thread-safe: one runner per thread.
 */
public final class CelRunner {

    private final Evaluator evaluator;
    private final ExprNode root;
    private final RecordProjection projection;

    public CelRunner(ExprNode root, MathProvider math, RecordProjection projection) {
        this.evaluator = new Evaluator(math);
        this.root = root;
        this.projection = projection;
    }

    /** Points the projection at record {@code i} and evaluates: the whole per-record cost the Service would pay. */
    public EvalOutcome evaluate(int i) {
        projection.set(i);
        return evaluator.evaluate(root, projection);
    }

    /** Evaluates record {@code i} and copies the list result into {@code out}; an error outcome is a failure. */
    public void evaluateInto(int i, double[] out) {
        unpack(i, evaluate(i), out);
    }

    /** Copies the list of doubles in {@code o} into {@code out}; anything else is a failure naming record {@code i}. Shared with {@link CompiledRunner}. */
    static void unpack(int i, EvalOutcome o, double[] out) {
        if (!(o instanceof EvalOutcome.Value v) || !(v.value() instanceof CelValue.ListV list)) {
            throw new IllegalStateException(new StringBuilder("record ").append(i).append(" did not yield a list: ")
                    .append(o).toString());
        }
        if (list.elements().size() != out.length) {
            throw new IllegalStateException(new StringBuilder("record ").append(i).append(" yielded ")
                    .append(list.elements().size()).append(" elements, expected ").append(out.length).toString());
        }
        for (int j = 0; j < out.length; j++) {
            out[j] = ((CelValue.DoubleV) list.elements().get(j)).value();
        }
    }
}
