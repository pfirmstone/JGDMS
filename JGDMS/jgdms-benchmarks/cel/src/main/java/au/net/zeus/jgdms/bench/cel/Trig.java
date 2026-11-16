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

import au.net.zeus.jgdms.cel.math.CorrectlyRoundedMath;

/**
 * The two non-production trigonometric sources the benchmarks need, both behind the evaluator's own public seam
 * ({@link CorrectlyRoundedMath}) so that the native baselines and the evaluator can be fed the same functions.
 * Production's correctly-rounded source is {@code MathProviders.correctlyRounded().get()}; it is not re-implemented here.
 */
final class Trig {

    private Trig() {
    }

    /**
     * The B-STRICT baseline's source: {@link StrictMath} (reproducible fdlibm, NOT correctly rounded: STD-011 §7.5 says it
     * does not qualify). Its results may differ from the correctly-rounded ones in the last bit; the benchmark that uses it
     * measures arithmetic speed, never conformance.
     */
    static final class Strict implements CorrectlyRoundedMath {
        @Override public double sin(double x) { return StrictMath.sin(x); }
        @Override public double cos(double x) { return StrictMath.cos(x); }
        @Override public double tan(double x) { return StrictMath.tan(x); }
        @Override public double asin(double x) { return StrictMath.asin(x); }
        @Override public double acos(double x) { return StrictMath.acos(x); }
        @Override public double atan(double x) { return StrictMath.atan(x); }
        @Override public double atan2(double y, double x) { return StrictMath.atan2(y, x); }
    }

    /**
     * Counts calls into the wrapped source. Used only to count how many transcendental calls one evaluation makes (the
     * count does not depend on the values unless a branch flips, which the guards in the expressions make a rare
     * edge); never installed in a timed benchmark.
     */
    static final class Counting implements CorrectlyRoundedMath {
        private final CorrectlyRoundedMath delegate;
        long sin;
        long cos;
        long tan;
        long asin;
        long acos;
        long atan;
        long atan2;

        Counting(CorrectlyRoundedMath delegate) {
            this.delegate = delegate;
        }

        long total() {
            return sin + cos + tan + asin + acos + atan + atan2;
        }

        @Override public double sin(double x) { sin++; return delegate.sin(x); }
        @Override public double cos(double x) { cos++; return delegate.cos(x); }
        @Override public double tan(double x) { tan++; return delegate.tan(x); }
        @Override public double asin(double x) { asin++; return delegate.asin(x); }
        @Override public double acos(double x) { acos++; return delegate.acos(x); }
        @Override public double atan(double x) { atan++; return delegate.atan(x); }
        @Override public double atan2(double y, double x) { atan2++; return delegate.atan2(y, x); }
    }
}
