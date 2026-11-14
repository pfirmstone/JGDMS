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

import au.net.zeus.jgdms.cel.EvalOutcome;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.math.MathProvider;

/**
 * One evaluator behind one call, so a spec-derived test is written once and runs against both the
 * reference {@link Evaluator} and the {@link CompiledExpression}. A test class declares
 * {@code evaluators()} returning {@link #WALKER}; a subclass returns {@link #COMPILED}, and JUnit then
 * runs every inherited test a second time against the compiled evaluator. Test tree only.
 */
public interface EvaluatorUnderTest {

    /** Evaluates {@code node} against {@code candidate}; the compiled variant compiles on every call. */
    EvalOutcome evaluate(ExprNode node, CandidateProjection candidate);

    /**
     * The O(len) {@code size(string)} scans performed so far: the walker's own counter, or the sum over the
     * Sessions of the compiled variant (one Session per call, as each call compiles afresh).
     */
    long sizeStringComputations();

    /** Makes evaluators over one {@link MathProvider}. */
    interface Factory {
        EvaluatorUnderTest create(MathProvider math);

        /** A short name for failure messages. */
        String label();
    }

    Factory WALKER = new Factory() {
        @Override
        public EvaluatorUnderTest create(MathProvider math) {
            Evaluator walker = new Evaluator(math);
            return new EvaluatorUnderTest() {
                @Override
                public EvalOutcome evaluate(ExprNode node, CandidateProjection candidate) {
                    return walker.evaluate(node, candidate);
                }

                @Override
                public long sizeStringComputations() {
                    return walker.sizeStringComputationCountForTesting();
                }
            };
        }

        @Override
        public String label() {
            return "walker";
        }
    };

    Factory COMPILED = new Factory() {
        @Override
        public EvaluatorUnderTest create(MathProvider math) {
            return new EvaluatorUnderTest() {
                private long scans;

                @Override
                public EvalOutcome evaluate(ExprNode node, CandidateProjection candidate) {
                    CompiledExpression.Session session = CompiledExpression.compile(node, math).newSession();
                    EvalOutcome outcome = session.evaluate(candidate);
                    scans += session.sizeStringComputationCountForTesting();
                    return outcome;
                }

                @Override
                public long sizeStringComputations() {
                    return scans;
                }
            };
        }

        @Override
        public String label() {
            return "compiled";
        }
    };
}
