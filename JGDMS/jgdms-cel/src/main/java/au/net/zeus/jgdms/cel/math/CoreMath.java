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

import java.util.concurrent.atomic.LongAdder;
import java.util.function.DoubleBinaryOperator;

/**
 * A correctly-rounded {@link CorrectlyRoundedMath} (JGDMS-STD-011 §7.5) whose {@code sin}, {@code cos},
 * {@code asin}, {@code acos}, {@code atan} and {@code atan2} are ports of CORE-MATH's binary64 routines
 * ({@link CoreMathSin}, {@link CoreMathCos}, {@link CoreMathAsin}, {@link CoreMathAcos}, {@link CoreMathAtan},
 * {@link CoreMathAtan2}, ported from {@code src/binary64/<f>/<f>.c} at CORE-MATH
 * commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7, MIT licence; see the NOTICE file).
 * {@code tan} is not ported yet and <b>delegates</b> to a {@link CrMath}, so this
 * provider is conformant for all seven at every commit; the ports replace the delegation one
 * function at a time.
 * <p>
 * <b>Which inputs the ports handle.</b> Every finite {@code double}, zero and subnormals included, except where
 * noted: the C routines return correctly rounded results for them, and the differential tests
 * ({@code CoreMathSinCosDifferentialTest}, {@code CoreMathInverseTrigDifferentialTest}) compare them bit for bit with
 * {@link CrMath}. NaN and infinities are <em>delegated</em>: the evaluator never passes them (it maps them to
 * {@code DOMAIN} first, in {@code Evaluator.callTranscendental}), the C routines would return a
 * NaN for them, and {@code CrMath} throws {@link IllegalArgumentException}. Delegating keeps this
 * provider's failure on a contract violation identical to {@code CrMath}'s instead of returning a
 * NaN that would read as a value. {@code asin} and {@code acos} delegate {@code |x| > 1} for the same reason
 * (the C returns a NaN; {@code CrMath} throws).
 * <p>
 * <b>atan2's correct-rounding failure branch.</b> {@link CoreMathAtan2} signals, by a {@code NaN} result for finite
 * arguments, that CORE-MATH's accurate path reached the case where the C prints "Unexpected worst-case found" and exits.
 * This provider never prints and never guesses: it counts the event ({@link #atan2CorrectRoundingFailures()}) and
 * answers that call by delegating to {@link CrMath}, which is correctly rounded by construction.
 * <p>
 * <b>Thread safety.</b> Stateless apart from the delegate, which is {@link CrMath}: its mutable statics
 * are the lock-guarded pi cache ({@code piCache}, {@code cachedPiDouble}, {@code cachedPiOver2Double}) and two
 * diagnostic counters ({@code lastAttempts}, {@code lastResolvedDigits}), read 2026-11-12; and one
 * {@link LongAdder}, the failure counter, which is thread-safe. The ported code keeps
 * no state and the constant tables are private to {@code CoreMathTables} and {@code CoreMathInverseTrigTables}.
 * Package-private like {@code CrMath}; the sanctioned entry point is
 * {@link MathProviders#correctlyRounded()}.
 */
final class CoreMath implements CorrectlyRoundedMath {

    private final CorrectlyRoundedMath delegate;
    private final DoubleBinaryOperator atan2Port;
    private final LongAdder atan2Failures = new LongAdder();

    /** The production provider: tan delegates to a {@link CrMath}, as do non-finite arguments and the correct-rounding failures of atan2. */
    CoreMath() {
        this(new CrMath());
    }

    /**
     * For the tests: lets a counting or failing delegate prove which calls reached the ported code
     * and which fell back ({@code CoreMathSinCosDifferentialTest} test T5).
     */
    CoreMath(CorrectlyRoundedMath delegate) {
        this(delegate, CoreMathAtan2::atan2);
    }

    /**
     * For the tests of the atan2 failure routing: {@code atan2Port} stands in for {@link CoreMathAtan2#atan2}, so a port
     * that reports a correct-rounding failure on demand proves that the failure is counted and answered by the delegate.
     */
    CoreMath(CorrectlyRoundedMath delegate, DoubleBinaryOperator atan2Port) {
        if (delegate == null) {
            throw new NullPointerException("delegate");
        }
        if (atan2Port == null) {
            throw new NullPointerException("atan2Port");
        }
        this.delegate = delegate;
        this.atan2Port = atan2Port;
    }

    /** How many {@code atan2} calls on this instance reached the correct-rounding failure branch and were answered by the delegate. */
    long atan2CorrectRoundingFailures() {
        return atan2Failures.sum();
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
        if (!(Math.abs(x) <= 1.0)) {                       // NaN, +-Inf, |x| > 1: the delegate throws IllegalArgumentException
            return delegate.asin(x);
        }
        return CoreMathAsin.asin(x);
    }

    @Override
    public double acos(double x) {
        if (!(Math.abs(x) <= 1.0)) {
            return delegate.acos(x);
        }
        return CoreMathAcos.acos(x);
    }

    @Override
    public double atan(double x) {
        if (!Double.isFinite(x)) {
            return delegate.atan(x);
        }
        return CoreMathAtan.atan(x);
    }

    @Override
    public double atan2(double y, double x) {
        if (!Double.isFinite(y) || !Double.isFinite(x)) {
            return delegate.atan2(y, x);
        }
        double r = atan2Port.applyAsDouble(y, x);
        if (r != r) {                                      // never a valid atan2 of finite arguments: the C's failure branch
            atan2Failures.increment();
            return delegate.atan2(y, x);
        }
        return r;
    }
}
