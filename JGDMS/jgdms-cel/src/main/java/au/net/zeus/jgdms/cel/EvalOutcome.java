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

package au.net.zeus.jgdms.cel;

/**
 * The total result of evaluating a well-formed expression against a
 * candidate (JGDMS-STD-011 §9.1): exactly one of a typed {@link CelValue} or
 * a {@link CelError}. There is no third outcome -- no exception escapes
 * evaluation of a well-formed expression.
 */
public sealed interface EvalOutcome {

    record Value(CelValue value) implements EvalOutcome {
        public Value {
            if (value == null) throw new NullPointerException("value");
        }
    }

    record Error(CelError code) implements EvalOutcome {
        public Error {
            if (code == null) throw new NullPointerException("code");
        }
    }

    static EvalOutcome of(CelValue value) {
        return new Value(value);
    }

    static EvalOutcome error(CelError code) {
        return new Error(code);
    }

    default boolean isError() {
        return this instanceof Error;
    }

    default boolean isValue() {
        return this instanceof Value;
    }

    /** Convenience accessor; throws {@link IllegalStateException} if this is an {@link Error}. */
    default CelValue valueOrThrow() {
        if (this instanceof Value v) return v.value();
        throw new IllegalStateException("not a value: " + this);
    }

    /** Convenience accessor; throws {@link IllegalStateException} if this is a {@link Value}. */
    default CelError errorOrThrow() {
        if (this instanceof Error e) return e.code();
        throw new IllegalStateException("not an error: " + this);
    }
}
