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
 * Public factory for {@link MathProvider}s. This is the sanctioned entry
 * point for obtaining the JGDMS-STD-011 SS7.5-conformant, correctly-rounded
 * transcendental provider ({@link CoreMath}, which falls back to {@link CrMath}
 * for the functions not yet ported) from outside this package -- both are
 * deliberately package-private (mirroring {@link StrictMathPlaceholder}'s own
 * unmistakable-opt-in pattern), so callers in other packages (an
 * {@code Evaluator} wiring, {@code CorpusRunnerTest}, a service's bootstrap
 * code) must come through here.
 */
public final class MathProviders {

    private MathProviders() {}

    /**
     * A genuinely correctly-rounded {@link MathProvider} (STD-011 SS7.5):
     * round-to-nearest-ties-even of the exact mathematical result, over the
     * full finite {@code double} domain, for {@code sin}/{@code cos}/
     * {@code tan}/{@code asin}/{@code acos}/{@code atan}/{@code atan2}.
     * Backed by {@link CoreMath}: {@code sin} and {@code cos} are ports of
     * CORE-MATH's binary64 routines, checked bit for bit against {@link CrMath}
     * by {@code CoreMathSinCosDifferentialTest}; {@code tan}, {@code asin},
     * {@code acos}, {@code atan} and {@code atan2} are delegated to {@link CrMath}
     * (arbitrary-precision {@code BigDecimal} arithmetic with Ziv-style escalating
     * precision -- see its class javadoc) until they are ported.
     */
    public static MathProvider correctlyRounded() {
        return MathProvider.conformant(new CoreMath());
    }
}
