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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The fail-closed transcendental provider seam (JGDMS-STD-011 §7.5): no
 * provider by default (throws), a genuinely conformant provider installs
 * cleanly, and the explicitly non-conformant placeholder is reachable only
 * through its unmistakable opt-in method.
 */
class MathProviderTest {

    @Test
    void noProvider_getThrows() {
        MathProvider provider = MathProvider.none();
        assertFalse(provider.isConformant());
        assertThrows(IllegalStateException.class, provider::get);
    }

    @Test
    void placeholderOptIn_getSucceeds_butIsNotConformant() {
        MathProvider provider = MathProvider.nonConformantForTestingOnly();
        assertFalse(provider.isConformant(), "the placeholder must never report itself as conformant");
        CorrectlyRoundedMath math = provider.get();
        assertInstanceOf(StrictMathPlaceholder.class, math);
        // Merely demonstrates the call succeeds once opted in -- not that the result is spec-conformant.
        assertTrue(Double.isFinite(math.sin(0.5)));
    }

    @Test
    void conformantProvider_installsAndReportsConformant() {
        CorrectlyRoundedMath fake = new CorrectlyRoundedMath() {
            @Override public double sin(double x) { return Math.sin(x); }
            @Override public double cos(double x) { return Math.cos(x); }
            @Override public double tan(double x) { return Math.tan(x); }
            @Override public double asin(double x) { return Math.asin(x); }
            @Override public double acos(double x) { return Math.acos(x); }
            @Override public double atan(double x) { return Math.atan(x); }
            @Override public double atan2(double y, double x) { return Math.atan2(y, x); }
        };
        MathProvider provider = MathProvider.conformant(fake);
        assertTrue(provider.isConformant());
        assertSame(fake, provider.get());
    }

    @Test
    void conformantRequiresNonNull() {
        assertThrows(NullPointerException.class, () -> MathProvider.conformant(null));
    }

    /**
     * Board-review regression (MED-HIGH): {@link StrictMathPlaceholder} must
     * expose no public constructor. A public constructor would let any
     * caller write {@code MathProvider.conformant(new
     * StrictMathPlaceholder())} -- which compiles and makes {@code
     * isConformant()} report {@code true} -- defeating the documented
     * property that the non-conformant placeholder is reachable only via
     * {@link MathProvider#nonConformantForTestingOnly()}'s unmistakable
     * opt-in. Asserted by reflection so a future contributor re-widening the
     * constructor's visibility (e.g. "to make testing easier") trips this
     * test rather than silently reopening the hole.
     */
    @Test
    void strictMathPlaceholder_exposesNoPublicConstructor() {
        for (var ctor : StrictMathPlaceholder.class.getDeclaredConstructors()) {
            assertFalse(java.lang.reflect.Modifier.isPublic(ctor.getModifiers()),
                    "StrictMathPlaceholder MUST NOT have a public constructor -- doing so lets "
                    + "MathProvider.conformant(new StrictMathPlaceholder()) masquerade as conformant, "
                    + "bypassing nonConformantForTestingOnly()'s unmistakable opt-in. Found: " + ctor);
        }
    }
}
