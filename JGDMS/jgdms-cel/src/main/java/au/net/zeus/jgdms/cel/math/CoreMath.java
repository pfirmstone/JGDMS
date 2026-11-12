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

package au.net.zeus.jgdms.cel.math;

/**
 * A correctly-rounded {@link CorrectlyRoundedMath} (JGDMS-STD-011 §7.5) whose {@code sin} and
 * {@code cos} are ports of CORE-MATH's binary64 routines ({@link CoreMathSin}, {@link CoreMathCos},
 * ported from {@code src/binary64/sin/sin.c} and {@code src/binary64/cos/cos.c} at CORE-MATH
 * commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7, MIT licence; see the NOTICE file).
 * The other five functions are not ported yet and <b>delegate</b> to a {@link CrMath}, so this
 * provider is conformant for all seven at every commit; the ports replace the delegation one
 * function at a time.
 * <p>
 * <b>Which inputs the ports handle.</b> Every finite {@code double}, zero and subnormals included:
 * the C routines return correctly rounded results for them, and the differential tests
 * ({@code CoreMathSinCosDifferentialTest}) compare them bit for bit with {@link CrMath}. NaN and
 * infinities are <em>delegated</em>: the evaluator never passes them (it maps them to
 * {@code DOMAIN} first, in {@code Evaluator.callTranscendental}), the C routines would return a
 * NaN for them, and {@code CrMath} throws {@link IllegalArgumentException}. Delegating keeps this
 * provider's failure on a contract violation identical to {@code CrMath}'s instead of returning a
 * NaN that would read as a value.
 * <p>
 * <b>Thread safety.</b> Stateless apart from the delegate, which is {@link CrMath}: its mutable statics
 * are the lock-guarded pi cache ({@code piCache}, {@code cachedPiDouble}, {@code cachedPiOver2Double}) and two
 * diagnostic counters ({@code lastAttempts}, {@code lastResolvedDigits}), read 2026-11-12. The ported code keeps
 * no state and the constant tables are private to {@code CoreMathTables}.
 * Package-private like {@code CrMath}; the sanctioned entry point is
 * {@link MathProviders#correctlyRounded()}.
 */
final class CoreMath implements CorrectlyRoundedMath {

    private final CorrectlyRoundedMath delegate;

    /** The production provider: tan, asin, acos, atan and atan2 delegate to a {@link CrMath}. */
    CoreMath() {
        this(new CrMath());
    }

    /**
     * For the tests: lets a counting or failing delegate prove which calls reached the ported code
     * and which fell back ({@code CoreMathSinCosDifferentialTest} test T5).
     */
    CoreMath(CorrectlyRoundedMath delegate) {
        if (delegate == null) {
            throw new NullPointerException("delegate");
        }
        this.delegate = delegate;
    }

    @Override
    public double sin(double x) {
        if (!Double.isFinite(x)) {
            return delegate.sin(x);
        }
        return CoreMathSin.sin(x);
    }

    @Override
    public double cos(double x) {
        if (!Double.isFinite(x)) {
            return delegate.cos(x);
        }
        return CoreMathCos.cos(x);
    }

    @Override
    public double tan(double x) {
        return delegate.tan(x);
    }

    @Override
    public double asin(double x) {
        return delegate.asin(x);
    }

    @Override
    public double acos(double x) {
        return delegate.acos(x);
    }

    @Override
    public double atan(double x) {
        return delegate.atan(x);
    }

    @Override
    public double atan2(double y, double x) {
        return delegate.atan2(y, x);
    }
}
