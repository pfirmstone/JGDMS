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
 * An explicitly <b>non-conformant</b> {@link CorrectlyRoundedMath}
 * implementation backed by {@link StrictMath}.
 * <p>
 * JGDMS-STD-011 §7.5 is unambiguous that {@code StrictMath} does not qualify
 * as correctly rounded (it is reproducible fdlibm-derived output, typically
 * accurate to within about 1 ulp, not the mathematically nearest binary64
 * value). This class exists only so that:
 * <ul>
 *   <li>the calling convention, error mapping, and cost accounting around
 *       {@code CALL} nodes for {@code sin}/{@code cos}/{@code tan}/{@code
 *       asin}/{@code acos}/{@code atan}/{@code atan2} can be exercised in
 *       tests before a conformant provider exists (T3 phase 2, a separate
 *       task); and</li>
 *   <li>a deployment that has deliberately decided it does not need
 *       bit-exact cross-language transcendental results (accepting the
 *       explicit STD-011 §7.5/§13 non-conformance this implies) has an
 *       escape hatch, used only via an unmistakable opt-in.</li>
 * </ul>
 * Tests using this class MUST NOT assert specific expected numeric results
 * as if they were conformant (that would silently launder fdlibm output as
 * spec compliance) -- they should assert only that the placeholder-gating
 * machinery itself works (the call succeeds once opted in, and is refused
 * otherwise). See {@link MathProvider#nonConformantForTestingOnly()}.
 */
public final class StrictMathPlaceholder implements CorrectlyRoundedMath {

    /**
     * Package-private deliberately: {@link MathProvider#nonConformantForTestingOnly()}
     * (same package) is the only sanctioned construction path. A public
     * constructor here would let any caller build a "conformant" {@link
     * MathProvider} via {@code MathProvider.conformant(new
     * StrictMathPlaceholder())} -- compiling cleanly and reporting {@code
     * isConformant() == true} -- which defeats the entire point of gating
     * the non-conformant placeholder behind an unmistakably-named opt-in
     * method. Do not widen this visibility.
     */
    StrictMathPlaceholder() {}

    @Override
    public double sin(double x) {
        return StrictMath.sin(x);
    }

    @Override
    public double cos(double x) {
        return StrictMath.cos(x);
    }

    @Override
    public double tan(double x) {
        return StrictMath.tan(x);
    }

    @Override
    public double asin(double x) {
        return StrictMath.asin(x);
    }

    @Override
    public double acos(double x) {
        return StrictMath.acos(x);
    }

    @Override
    public double atan(double x) {
        return StrictMath.atan(x);
    }

    @Override
    public double atan2(double y, double x) {
        return StrictMath.atan2(y, x);
    }
}
