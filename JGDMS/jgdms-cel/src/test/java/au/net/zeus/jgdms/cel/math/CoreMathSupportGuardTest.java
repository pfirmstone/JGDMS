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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Drives the guard that {@link CoreMathSupport#u128ToDouble} adds to the C: a fixed-point result whose
 * upper word is zero throws {@link IllegalStateException} instead of reaching {@code clzll(0)}, which is
 * undefined in the C (its comment proves the case cannot occur). The guard exists so that, if that proof
 * were ever wrong, a call fails loudly rather than returning a plausible wrong number. What the exception
 * does in the evaluator is stated in {@code u128ToDouble}'s javadoc. Runtime tier: FAST (default suite).
 *
 * <h2>SEALED PREDICTIONS (fix pass, written before the first run of this test)</h2>
 * <ol>
 *   <li><b>F13</b> {@code u128ToDouble} throws {@code IllegalStateException} for an upper word of zero, for
 *       both signs and for a zero and a non-zero lower word (4 of 4), and the message names the upper word.</li>
 *   <li><b>F14</b> it does not throw, and returns the value computed by hand, when the upper word is not zero:
 *       {@code r = 2^126} (upper word 2^62) is 2^126 / 2^128 = 0.25, so {@code +0.25} for sign 0 and
 *       {@code -0.25} for sign 1.</li>
 * </ol>
 */
class CoreMathSupportGuardTest {

    @Test
    void zeroUpperWordThrows() {
        long[] lows = {0L, 0x123456789abcdefL};
        int thrown = 0;
        for (long lo : lows) {
            for (int s = 0; s < 2; s++) {
                final int sign = s;
                final long low = lo;
                IllegalStateException e = assertThrows(IllegalStateException.class,
                        () -> CoreMathSupport.u128ToDouble(U128.of(0L, low), sign));
                assertTrue(e.getMessage().contains("upper word"), e.getMessage());
                thrown++;
            }
        }
        assertEquals(4, thrown);
    }

    @Test
    void nonZeroUpperWordReturnsTheValue() {
        U128 quarter = U128.of(1L << 62, 0L);
        assertEquals(0.25, CoreMathSupport.u128ToDouble(quarter, 0));
        assertEquals(-0.25, CoreMathSupport.u128ToDouble(quarter, 1));
    }
}
