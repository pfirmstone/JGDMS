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
 * The seven correctly-rounded transcendental functions JGDMS-STD-011 §7.5
 * ratifies as REQUIRED (option (a): "the binary64 value nearest the exact
 * mathematical result, ties to even" -- §2's "Correctly rounded" definition).
 * <p>
 * <b>Implementations in this module</b> (both package-private; callers take
 * {@link MathProviders#correctlyRounded()}):
 * <ul>
 *   <li>{@link CoreMath}: conformant for all seven. {@code sin} and {@code cos} are Java ports of
 *       CORE-MATH's correctly-rounded binary64 routines ({@code sin.c}, {@code cos.c} at CORE-MATH
 *       commit 018fa97c, MIT licence, see the NOTICE file); the other five delegate to {@link CrMath}
 *       until they are ported. This is what {@code MathProviders.correctlyRounded()} returns.</li>
 *   <li>{@link CrMath}: conformant for all seven, by arbitrary-precision arithmetic with Ziv
 *       escalation (slow). It is kept as the delegate of {@code CoreMath} and as the independent
 *       oracle that the tests of the CORE-MATH ports compare against.</li>
 *   <li>{@link StrictMathPlaceholder}: explicitly NOT conformant ({@code StrictMath} is
 *       reproducible fdlibm, not correctly rounded: §7.5's honest cost statement), reachable only
 *       through {@link MathProvider#nonConformantForTestingOnly()}.</li>
 * </ul>
 * {@link MathProvider} is the fail-closed gate: with no provider installed a transcendental
 * {@code CALL} fails.
 * <p>
 * Every method's domain is finite-only per STD-011 §7.2: implementations are
 * never asked to handle NaN or ±Inf arguments (the evaluator maps those to
 * {@code DOMAIN} before calling in). {@code radians}/{@code degrees} are
 * intentionally <em>not</em> declared here -- STD-011 §7.3 pins them as a
 * single IEEE multiply by a fixed bit-pattern constant, which needs no
 * correctly-rounded library at all (see {@code
 * au.net.zeus.jgdms.cel.eval.Evaluator}'s constant-multiply implementation).
 */
public interface CorrectlyRoundedMath {

    /** Correctly-rounded {@code sin(x)} for finite {@code x}. */
    double sin(double x);

    /** Correctly-rounded {@code cos(x)} for finite {@code x}. */
    double cos(double x);

    /** Correctly-rounded {@code tan(x)} for finite {@code x}. */
    double tan(double x);

    /** Correctly-rounded {@code asin(x)} for finite {@code x}, {@code |x| <= 1}. */
    double asin(double x);

    /** Correctly-rounded {@code acos(x)} for finite {@code x}, {@code |x| <= 1}. */
    double acos(double x);

    /** Correctly-rounded {@code atan(x)} for finite {@code x}. */
    double atan(double x);

    /** Correctly-rounded {@code atan2(y, x)} for finite {@code y}, {@code x}. */
    double atan2(double y, double x);
}
