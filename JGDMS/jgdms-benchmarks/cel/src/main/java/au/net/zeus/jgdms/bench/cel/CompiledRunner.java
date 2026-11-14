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

import au.net.zeus.jgdms.cel.EvalOutcome;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.eval.CompiledExpression;
import au.net.zeus.jgdms.cel.math.MathProvider;

/**
 * The compiled twin of {@link CelRunner}: one production {@link CompiledExpression} (public API only: {@code compile},
 * {@code newSession}, {@code Session.evaluate}) over one expression and one flyweight projection. The timed path is the same
 * as the walker's: {@code projection.set(i)} then one evaluation. Compiling happens in the constructor, never per record.
 * Not thread-safe: one runner per thread, as the Session it owns.
 */
public final class CompiledRunner {

    private final CompiledExpression.Session session;
    private final RecordProjection projection;

    public CompiledRunner(ExprNode root, MathProvider math, RecordProjection projection) {
        this.session = CompiledExpression.compile(root, math).newSession();
        this.projection = projection;
    }

    /** Points the projection at record {@code i} and evaluates: the whole per-record cost the Service would pay. */
    public EvalOutcome evaluate(int i) {
        projection.set(i);
        return session.evaluate(projection);
    }

    /** As {@link CelRunner#evaluateInto}. */
    public void evaluateInto(int i, double[] out) {
        CelRunner.unpack(i, evaluate(i), out);
    }
}
