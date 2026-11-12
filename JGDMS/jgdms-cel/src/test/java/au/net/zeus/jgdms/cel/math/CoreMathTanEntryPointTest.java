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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.SplittableRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The PRODUCTION ENTRY POINT of the tan guard: {@code CoreMathTan.tan} and the way {@link CoreMath} reaches it (fix pass 3, re-check 2 of 69639bd15, Fable M-1). With the window narrowed, the
 * production path delegates NOTHING on any population tried (0 of 7 million inputs; about one input is expected over all of binary64, HYPOTHESIS), so "0 delegations" is also what an unguarded
 * {@code tan()} gives: Fable's mutants PG ({@code tan()} passes {@code false} for the guard flag) and PK ({@code tan()} passes 2^-60 for K) passed every fast and slow test. No real input can
 * close that gap. Two things close it here: {@code tan(x)} IS {@code tanWithK(x, GUARD_K)} (so the tests that drive {@code tanWithK}, the K test of {@code CoreMathTanGuardTest}, drive the
 * production path), and this class pins the TEXT of the one call that no behaviour can see: the constant at that call, and the default port of {@link CoreMath}. Hermetic: it reads two sources of
 * this module and FAILS (does not skip) if it cannot find them. Runtime tier: FAST.
 *
 * <h2>SEALED PREDICTIONS (B2F3-E, fix pass 3; written in the commit that precedes the first run of this class)</h2>
 * <ol>
 *   <li>The body of {@code CoreMathTan.tan(double)} is exactly {@code return tanWithK(x, CoreMathTanTables.GUARD_K);} (whitespace aside), and the method is defined once.</li>
 *   <li>The default constructor chain of {@code CoreMath} ends in {@code this(delegate, atan2Port, CoreMathTan::tan);}, so {@code new CoreMath(delegate)} counts the guard's events of the
 *       production path.</li>
 *   <li>{@code tan(x)} and {@code tanWithK(x, GUARD_K)} agree bit for bit (NaN included) on the real folded {@code tan.wc} entry {@code x = 0x1.92456c9a2452ep282}, the named inputs and 2,000 seeded
 *       random inputs: 0 mismatches of 2,000 + 12.</li>
 * </ol>
 * Mutants (sealed in the message of the seal commit): PK, PG0 and PC are each killed by this class alone, one failing method; PG (the flag flipped inside {@code tanWithK}) by the K test of
 * {@code CoreMathTanGuardTest} alone.
 */
class CoreMathTanEntryPointTest {

    private static final String PKG = "src/main/java/au/net/zeus/jgdms/cel/math/";

    private static String normalised(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    @Test
    void theProductionEntryPointIsTheGuardedPathWithTheGuardConstant() throws IOException {
        Path file = CoreMathTestSupport.moduleDir().resolve(PKG).resolve("CoreMathTan.java");
        String text = CoreMathTestSupport.readText(file);
        Matcher m = Pattern.compile("static double tan\\(double x\\) \\{(.*?)\\n    \\}", Pattern.DOTALL).matcher(text);
        assertTrue(m.find(), "CoreMathTan.tan(double) was not found");
        assertEquals("return tanWithK(x, CoreMathTanTables.GUARD_K);", normalised(m.group(1)), "the body of CoreMathTan.tan(double)");
        assertFalse(m.find(), "CoreMathTan.tan(double) is defined once");
    }

    @Test
    void coreMathReachesTanThroughTheProductionPortByDefault() throws IOException {
        Path file = CoreMathTestSupport.moduleDir().resolve(PKG).resolve("CoreMath.java");
        String text = normalised(CoreMathTestSupport.readText(file));
        assertTrue(text.contains("this(delegate, atan2Port, CoreMathTan::tan);"), "the default tan port of CoreMath is CoreMathTan::tan");
        assertTrue(text.contains("double r = tanPort.applyAsDouble(x); if (r != r) {"), "CoreMath.tan asks the port and treats a NaN as the guard's undecided");
        assertTrue(text.contains("tanGuardDelegations.increment(); return delegate.tan(x);"), "CoreMath.tan counts the event and answers from the delegate");
    }

    @Test
    void tanIsTanWithKAtTheGuardConstantBitForBit() {
        SplittableRandom r = new SplittableRandom(20261801L);
        double[] named = {0x1.92456c9a2452ep282, -0x1.92456c9a2452ep282, 0x1.921fb54442d18p0, 0x1.6ac5b262ca1ffp849, 0x1.6c6cbc45dc8dep5, 1.58, 3.15, 0.1, 0x1.d12ed0af1a27fp-27, 0x1.921fb54442d18p2, 1e300, 1e-300};
        double[] random = CoreMathTanTestSupport.binades(r.nextLong(), 2000, -20, 1000);
        int mismatches = 0;
        int n = 0;
        for (double[] xs : new double[][] {named, random}) {
            for (double x : xs) {
                n++;
                if (Double.doubleToRawLongBits(CoreMathTan.tan(x)) != Double.doubleToRawLongBits(CoreMathTan.tanWithK(x, CoreMathTanTables.GUARD_K))) {
                    mismatches++;
                }
            }
        }
        System.out.println(new StringBuilder(100).append("B2F3-E tan(x) against tanWithK(x, GUARD_K): ").append(mismatches).append(" mismatches of ").append(n));
        assertEquals(0, mismatches);
    }
}
