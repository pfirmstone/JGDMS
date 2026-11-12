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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * A REPRODUCER for the upstream report on CORE-MATH's {@code tan.c} (operator request 2026-11-12, "Can you make a test case?"). It runs a short NAMED list of inputs through the
 * port of {@code tan_accurate} (a statement-by-statement port of the C's algorithm; its rounded results are compared with {@code CrMath}, not with the C: see {@code CoreMathTanDifferentialTest}) and compares the 128-bit result it rounds, {@code U}, with an
 * EXACT tangent ({@link CoreMathTanExactReference}: Machin's pi to 2,400 bits, 800-bit fixed point, independent of the port, of {@code CrMath} and of CORE-MATH).
 * <p>
 * <b>The property shown.</b> {@code tan.c:2127-2131} and {@code :2206-2212} claim that the accurate path's 128-bit result is within 86 ulps (units of the last bit of the 128-bit
 * mantissa) of tan(x). {@code reduce} bounds its error relative to {@code F = frac(x/(2 pi))}; the accurate path folds F by exact quarter turns to a smaller {@code R} and divides the
 * error by R. Where the fold subtracts, the error is about {@code 2.6/R} ulps: up to 4e18 ulps (about 66 correct bits instead of 121) at {@code x = 0x1.6ac5b262ca1ffp+849}. No wrong
 * rounding results for these inputs (the true tangent is far from a rounding midpoint), which the test also shows; the guard of this port nevertheless widens the window to
 * {@code 86 + 8/R}. The lines printed with the prefix {@code UPSTREAM} are the data of {@code UPSTREAM_TAN_TESTCASE_INPUTS.md} (the field {@code U} is the 128-bit result of the accurate path
 * before rounding, in the layout of the C's {@code dint64_t}, so that the C's value can be compared with it bit for bit). The C was not compiled or run to produce them.
 * Runtime tier: FAST (default suite), about one second.
 *
 * <h2>SEALED PREDICTIONS (B2F-U1..U5, the fix pass; written before the first run of this class)</h2>
 * Population: the nine named folded inputs x = 0x1.6c6cbc45dc8dep5 (Opus review), 0x1.6ac5b262ca1ffp+849 (Fable review, the input of the largest |tan|), 2 * that (the smallest |tan|),
 * the doubles nearest pi/2, pi, 3 pi/2 and 2 pi, 1.58 and 3.15, and the two controls 4 * 0x1.6ac5b262ca1ffp+849 (a multiple of 2 pi: no fold) and 0.1 (no fold).
 * <ol>
 *   <li><b>B2F-U1.</b> For each of the nine folded inputs the 128-bit result is farther than 86 ulps from the exact tangent: 9 of 9 (the reviewers measured 253 ulps at 1.58, 175 at 3.15, about 4e18
 *       at 0x1.6ac5b262ca1ffp+849 and about 1e16 at the doubles nearest pi/2, pi, 2 pi). ELSE: the property is not reproduced for that input.</li>
 *   <li><b>B2F-U2.</b> For the nine, (error in ulps) x R lies below 3.2 (the law error = 2.6/R, measured 2.58 at most over tan.wc); for the two controls the error is at most 86 ulps.</li>
 *   <li><b>B2F-U3.</b> The final rounded result of {@code tan_accurate}'s path, {@link CoreMathTan#accurate}, equals the exactly rounded tangent and {@code CrMath}'s value, for all 11
 *       inputs (0 wrong).</li>
 *   <li><b>B2F-U4.</b> For each of the nine, the true tangent is farther from the nearest rounding midpoint than the error (so the rounding of the faulty {@code U} is right), OR the guard fires:
 *       9 of 9. The production provider ({@code MathProviders.correctlyRounded()}) returns {@code CrMath}'s value for all 11.</li>
 *   <li><b>B2F-U5.</b> The guard window {@code 86 + 8/R} contains the measured error for all 11: 11 of 11.</li>
 * </ol>
 * SCORING (appended 2026-11-12 after the first run; the sealed text above is unchanged): U1 HELD (9 of 9 above 86 ulps; the smallest error is 175.5 ulps at 3.15, the largest 4.06e18 at
 * 0x1.6ac5b262ca1ffp+849); U2 HELD (largest error x R of the nine 0.39, the controls' errors 3.25 and 6.06 ulps); U3 HELD (11 of 11 equal); U4 HELD, by its first branch only: for all nine the
 * true tangent is at least 2.8e21 ulps from a midpoint (the largest possible is 2^74 = 1.9e22), 1.3e3 to 6.5e19 times the error, so the guard fires on none of them (guardFires=false for the nine; the guard's firing is shown by
 * {@code CoreMathTanGuardTest} on constructed results); U5 HELD (11 of 11). The population carries no input on which the C's 86-ulp test or the guard fires: a wrong rounding needs the true
 * tangent within the error of a midpoint, and none of these is.
 * <p>
 * FIX PASS 2 (appended 2026-11-12; the sealed texts above stand as history; re-check review Fable M-3). SCOPE: every comparison here and in the tests this one refers to is against {@code CrMath}
 * or against the exact reference of this class; the C was never compiled or run, so "the port equals the correctly rounded tangent on these inputs" is agreement with the C only where the C is
 * correctly rounded. With the window narrowed (operator decision "Narrow it") the window of the two controls, which are unfolded, is {@code 86 + 8 = 94} ulps (95 in the code) instead of
 * {@code 86 + 8/R}; sealed before the first run: U1 to U4 unchanged (9 of 9; error x R below 3.2; 11 of 11; 9 of 9), U5 with the narrowed window: 11 of 11, and the code's fold flag equals the
 * exact reference's "frac(x/(2 pi)) >= 1/8" on all 11.
 */
class CoreMathTanFoldErrorReproducerTest {

    private static final double X0 = 0x1.6ac5b262ca1ffp+849;

    /** The nine folded inputs, then the two controls. */
    private static final double[] INPUTS = {
        0x1.6c6cbc45dc8dep5, X0, 2 * X0, 0x1.921fb54442d18p+0, 0x1.921fb54442d18p+1, 0x1.2d97c7f3321d2p+2, 0x1.921fb54442d18p+2, 1.58, 3.15,
        4 * X0, 0.1,
    };
    private static final int FOLDED = 9;

    @Test
    void theAccuratePathsErrorExceedsTheCsWindowAfterAFoldAndTheRoundedResultStillEqualsCrMath() {
        int over86 = 0;
        int decidedOrGuarded = 0;
        int windowContains = 0;
        for (int k = 0; k < INPUTS.length; k++) {
            double x = INPUTS[k];
            CoreMathDint xd = new CoreMathDint();
            int[] ig = new int[2];
            CoreMathDint u = CoreMathTan.accurateU(x, xd, ig);
            CoreMathTanExactReference.Tan t = CoreMathTanExactReference.tan(x);
            double ulps = CoreMathTanExactReference.errorUlps(u, t);
            double dist = CoreMathTanExactReference.distanceToMidpointUlps(u, t);
            double r = new BigDecimal(ig[0]).divide(new BigDecimal(2048)).add(new BigDecimal(CoreMathDintTest.mant(xd)).multiply(pow2(xd.ex - 128))).doubleValue();
            boolean cFires = CoreMathTan.c86TestFires(u);
            boolean folded = !CoreMathTanExactReference.isUnfolded(t);
            assertEquals(folded, ig[1] != 0, "the code's fold flag equals frac(x/(2 pi)) >= 1/8 of the exact reference");
            boolean guard = CoreMathTan.guardFires(u, ig[0], xd, folded);
            double windowUlps = folded ? 86 + 8 / r : 86 + 8;                // fix pass 2: the window narrowed (86 + K/R after a fold, 86 + K without)
            double accurate = CoreMathTan.accurate(x);
            double exact = CoreMathTanExactReference.round(t);
            double oracle = CoreMathTestSupport.ORACLE.tan(x);
            double provider = CoreMathTestSupport.PROVIDER.tan(x);
            System.out.println(new StringBuilder(400).append("UPSTREAM x=").append(Double.toHexString(x)).append(" tan=").append(t.negative ? "-" : "+").append(t.absTan(40).round(new java.math.MathContext(40)).toString())
                    .append(" errorUlps=").append(String.format(Locale.ROOT, "%.6g", ulps)).append(" R=").append(String.format(Locale.ROOT, "%.6g", r)).append(" errorTimesR=").append(String.format(Locale.ROOT, "%.4f", ulps * r))
                    .append(" distanceToMidpointUlps=").append(String.format(Locale.ROOT, "%.6g", dist)).append(" folded=").append(folded).append(" c86Fires=").append(cFires)
                    .append(" guardFires=").append(guard).append(" window=").append(String.format(Locale.ROOT, "%.6g", windowUlps)).append(" correctlyRounded=").append(Double.toHexString(exact))
                    .append(" U=(hi 0x").append(Long.toHexString(u.hi)).append(" lo 0x").append(Long.toHexString(u.lo)).append(" ex ").append(u.ex).append(", |tan x| = (hi/2^64 + lo/2^128) 2^ex)"));
            // B2F-U3, U4 (second half)
            assertEquals(Double.doubleToRawLongBits(exact), Double.doubleToRawLongBits(accurate), CoreMathTestSupport.msg("accurate path at ", Double.toHexString(x)));
            assertEquals(Double.doubleToRawLongBits(oracle), Double.doubleToRawLongBits(accurate), CoreMathTestSupport.msg("accurate path against CrMath at ", Double.toHexString(x)));
            assertEquals(Double.doubleToRawLongBits(oracle), Double.doubleToRawLongBits(provider), CoreMathTestSupport.msg("provider at ", Double.toHexString(x)));
            if (k < FOLDED) {
                assertTrue(folded, CoreMathTestSupport.msg("the input is folded: ", Double.toHexString(x)));
                if (ulps > 86) {
                    over86++;
                }
                assertTrue(ulps * r < 3.2, CoreMathTestSupport.msg("error x R at ", Double.toHexString(x), ": ", ulps * r));
                if (ulps < dist || guard) {
                    decidedOrGuarded++;
                }
            } else {
                assertTrue(!folded, CoreMathTestSupport.msg("the control is not folded: ", Double.toHexString(x)));
                assertTrue(ulps <= 86, CoreMathTestSupport.msg("control error ", ulps));
            }
            if (ulps <= windowUlps) {
                windowContains++;
            }
        }
        System.out.println(new StringBuilder(160).append("B2F-U reproducer: error above the C's 86 ulps on ").append(over86).append(" of ").append(FOLDED).append(" folded inputs; decided or guarded ").append(decidedOrGuarded)
                .append(" of ").append(FOLDED).append("; the window (86 + 8/R after a fold, 86 + 8 without) contains the error on ").append(windowContains).append(" of ").append(INPUTS.length));
        assertEquals(FOLDED, over86);
        assertEquals(FOLDED, decidedOrGuarded);
        assertEquals(INPUTS.length, windowContains);
    }

    private static BigDecimal pow2(long k) {
        if (k >= 0) {
            return new BigDecimal(java.math.BigInteger.ONE.shiftLeft((int) k));
        }
        return new BigDecimal(java.math.BigInteger.valueOf(5).pow((int) -k)).movePointLeft((int) -k);
    }
}
