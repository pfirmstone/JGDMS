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
 * <b>This interface has no conformant implementation in this module.</b>
 * §7.5's honest cost statement is explicit: {@code StrictMath} is
 * reproducible-but-not-correctly-rounded fdlibm and does <em>not</em>
 * qualify; a real implementation means porting or binding a correctly-
 * rounded binary64 library (the CORE-MATH-class lineage §7.5 names). That
 * port is out of this module's scope -- it is **T3 phase 2**, a separate,
 * subsequent task. What this module ships is the seam and a fail-closed
 * default ({@link MathProvider}), plus an explicitly non-conformant,
 * opt-in-only placeholder ({@link StrictMathPlaceholder}) so callers can
 * exercise the calling convention and error-mapping machinery in tests
 * without silently mistaking placeholder output for spec-conformant
 * results.
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
