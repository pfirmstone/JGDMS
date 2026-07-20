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

import java.math.BigDecimal;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct unit coverage of {@link CrMath}'s own machinery, complementing (not
 * duplicating) {@code CorpusRunnerTest}'s bit-exact corpus assertions:
 * <ul>
 *   <li>special-value handling that runs before the BigDecimal core (signed
 *       zero, exact endpoints, the atan2 zero/quadrant table);</li>
 *   <li>the argument-reduction machinery directly, against the same
 *       oracle-derived expected bit patterns the corpus carries (STD-011
 *       T3-phase-2's "huge argument" reduction-stress vectors);</li>
 *   <li>the Ziv escalation driver ({@link CrMath#correctlyRounded}) and its
 *       hard-cap fail-loud path, exercised directly (package-private access)
 *       rather than only incidentally via whichever real inputs happen to
 *       need escalation.</li>
 * </ul>
 */
class CrMathTest {

    private static final CrMath M = new CrMath();

    // ======================================================================
    // Special values (handled before the BigDecimal core -- STD-011 SS7.2)
    // ======================================================================

    @Test
    void signPreservingZeros() {
        assertEquals(0x0000000000000000L, Double.doubleToRawLongBits(M.sin(0.0)));
        assertEquals(0x8000000000000000L, Double.doubleToRawLongBits(M.sin(-0.0)));
        assertEquals(0x0000000000000000L, Double.doubleToRawLongBits(M.tan(0.0)));
        assertEquals(0x8000000000000000L, Double.doubleToRawLongBits(M.tan(-0.0)));
        assertEquals(0x0000000000000000L, Double.doubleToRawLongBits(M.atan(0.0)));
        assertEquals(0x8000000000000000L, Double.doubleToRawLongBits(M.atan(-0.0)));
        assertEquals(0x0000000000000000L, Double.doubleToRawLongBits(M.asin(0.0)));
        assertEquals(0x8000000000000000L, Double.doubleToRawLongBits(M.asin(-0.0)));
    }

    @Test
    void cosAndAcosAtZero_areUnsigned() {
        assertEquals(1.0, M.cos(0.0));
        assertEquals(1.0, M.cos(-0.0));
        assertEquals(M.acos(0.0), M.acos(-0.0)); // pi/2 regardless of input sign
    }

    @Test
    void asinAcosExactEndpoints() {
        assertEquals(0x3ff921fb54442d18L, Double.doubleToRawLongBits(M.asin(1.0)),
                "asin(1) must be the correctly-rounded double nearest pi/2 (0x3ff921fb54442d18)");
        assertEquals(0xbff921fb54442d18L, Double.doubleToRawLongBits(M.asin(-1.0)));
        assertEquals(0.0, M.acos(1.0));
        assertEquals(0x400921fb54442d18L, Double.doubleToRawLongBits(M.acos(-1.0)),
                "acos(-1) must be the correctly-rounded double nearest pi (0x400921fb54442d18)");
    }

    @Test
    void asinAcosDomainGuard_defensive() {
        assertThrows(IllegalArgumentException.class, () -> M.asin(1.5));
        assertThrows(IllegalArgumentException.class, () -> M.acos(-1.5));
    }

    @Test
    void nonFiniteInput_throwsDefensively() {
        assertThrows(IllegalArgumentException.class, () -> M.sin(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> M.cos(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> M.atan2(Double.NaN, 1.0));
        assertThrows(IllegalArgumentException.class, () -> M.atan2(1.0, Double.POSITIVE_INFINITY));
    }

    /** The full IEEE-754/C99 F.10.1.4 table (STD-011 SS7.2 row 15), all 12 zero cases. */
    @Test
    void atan2ZeroQuadrantTable() {
        double pi = M.atan2(0.0, -1.0); // == +pi, established independently below
        double piHalf = M.atan2(1.0, 0.0);

        assertBits(0.0, M.atan2(0.0, 1.0), "atan2(+0,+x)");
        assertBits(-0.0, M.atan2(-0.0, 1.0), "atan2(-0,+x)");
        assertBits(pi, M.atan2(0.0, -1.0), "atan2(+0,-x)");
        assertBits(-pi, M.atan2(-0.0, -1.0), "atan2(-0,-x)");
        assertBits(piHalf, M.atan2(1.0, 0.0), "atan2(+y,+0)");
        assertBits(-piHalf, M.atan2(-1.0, 0.0), "atan2(-y,+0)");
        assertBits(piHalf, M.atan2(1.0, -0.0), "atan2(+y,-0)");
        assertBits(-piHalf, M.atan2(-1.0, -0.0), "atan2(-y,-0)");
        assertBits(0.0, M.atan2(0.0, 0.0), "atan2(+0,+0)");
        assertBits(-0.0, M.atan2(-0.0, 0.0), "atan2(-0,+0)");
        assertBits(pi, M.atan2(0.0, -0.0), "atan2(+0,-0)");
        assertBits(-pi, M.atan2(-0.0, -0.0), "atan2(-0,-0)");

        // pi/2 and pi are themselves correctly-rounded (matches STD-011's own oracle hard-doubles bit patterns).
        assertEquals(0x3ff921fb54442d18L, Double.doubleToRawLongBits(piHalf));
        assertEquals(0x400921fb54442d18L, Double.doubleToRawLongBits(pi));
    }

    private static void assertBits(double expected, double actual, String label) {
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual),
                () -> label + ": expected bits 0x" + Long.toHexString(Double.doubleToRawLongBits(expected))
                        + " got 0x" + Long.toHexString(Double.doubleToRawLongBits(actual)));
    }

    // ======================================================================
    // Argument-reduction machinery, direct: same oracle-derived expected bits
    // the corpus carries (transcendental_oracle.py's "huge-*" hard doubles),
    // called straight against the CrMath API rather than through the full
    // Evaluator/decoder path CorpusRunnerTest already exercises.
    // ======================================================================

    @Test
    void reductionStress_1e300() {
        assertEquals(0xbfea2c16b010e385L, Double.doubleToRawLongBits(M.sin(1e300)), "sin(1e300)");
        assertEquals(0xbfe2699022adc4c1L, Double.doubleToRawLongBits(M.cos(1e300)), "cos(1e300)");
        assertEquals(0x3ff6be411f37ac77L, Double.doubleToRawLongBits(M.tan(1e300)), "tan(1e300)");
        assertEquals(0x3ff921fb54442d18L, Double.doubleToRawLongBits(M.atan(1e300)), "atan(1e300)");
    }

    @Test
    void reductionStress_exactPowerOfTwo_2pow100() {
        double x = Math.scalb(1.0, 100); // 2^100, exact
        assertEquals(0xbfebe8ed97ac1f59L, Double.doubleToRawLongBits(M.sin(x)), "sin(2^100)");
        assertEquals(0x3fdf4eb3ff66e36dL, Double.doubleToRawLongBits(M.cos(x)), "cos(2^100)");
    }

    @Test
    void reductionStress_nearPiOver2() {
        double nearPiOver2 = Double.longBitsToDouble(0x3FF921FB54442D18L); // nearest double to pi/2
        assertEquals(0x3ff0000000000000L, Double.doubleToRawLongBits(M.sin(nearPiOver2)),
                "sin(nearest double to pi/2) must round to exactly 1.0");
        assertEquals(0x434d02967c31cdb5L, Double.doubleToRawLongBits(M.tan(nearPiOver2)),
                "tan(nearest double to pi/2): the classic near-pole reduction/cancellation stress case");
    }

    @Test
    void asinAcosHalf() {
        assertEquals(0x3fe0c152382d7366L, Double.doubleToRawLongBits(M.asin(0.5)));
        assertEquals(0x3ff0c152382d7366L, Double.doubleToRawLongBits(M.acos(0.5)));
    }

    // ======================================================================
    // Ziv escalation driver: direct, controlled exercise of the mechanism
    // itself (CrMath#correctlyRounded / CrMath#tryRound), independent of
    // whether any real double under the production start/max digits happens
    // to need it. Finding a genuine double whose sin/cos/tan/atan value is
    // provably within 1e-40ish of a double rounding boundary (i.e. needs
    // escalation past this class's ~133-bit starting precision) is the
    // "Table Maker's Dilemma" -- a specialised search problem (Lefevre/SLZ-
    // class algorithms), not something a random or exhaustive corpus search
    // finds in reasonable time; empirically, none of the 188 real corpus
    // inputs need more than one Ziv attempt (see escalationStats_acrossHardAndReductionStressInputs
    // below). The driver logic is instead verified directly against a synthetic
    // Formula that deliberately reports an unresolved (huge) error bound
    // below a threshold and a tiny, resolvable one above it.
    // ======================================================================

    /**
     * A decimal value that is deliberately NOT the exact value of any {@code
     * double}: 0.75 is exactly representable, but perturbing it by 1e-31 is
     * far below any double's precision, so {@link CrMath#tryRound}'s
     * "exact hit" shortcut (which a plain {@code BigDecimal.valueOf(0.5)}-style
     * target would trip, bypassing the error-bound distance-to-boundary logic
     * this test exists to exercise) never fires for it.
     */
    private static final BigDecimal NOT_EXACTLY_A_DOUBLE = new BigDecimal("0.7500000000000000000000000000001");

    @Test
    void zivDriver_escalatesPastTwoHundredBitsThenResolves() {
        // ~200 bits ~= 61 decimal digits. Force at least 3 doublings from a small start
        // (20 -> 40 -> 80 -> 160 all "unresolved", 320 "resolves") to cross that threshold.
        CrMath m = new CrMath(20, 3200);
        IntFunction<BigDecimal[]> formula = digits -> {
            BigDecimal err = (digits < 200)
                    ? BigDecimal.ONE // hopelessly large -- always ambiguous
                    : BigDecimal.ONE.movePointLeft(50); // small enough to resolve unambiguously
            return new BigDecimal[]{NOT_EXACTLY_A_DOUBLE, err};
        };
        double result = m.correctlyRounded(formula);
        assertEquals(0.75, result);
        assertTrue(CrMath.lastResolvedDigits >= 200,
                "expected the Ziv driver to have escalated past the 200-decimal-digit (~664-bit) "
                        + "threshold before resolving; resolved at " + CrMath.lastResolvedDigits);
        assertTrue(CrMath.lastAttempts >= 4,
                "expected at least 4 Ziv attempts (20,40,80,160,320,...); got " + CrMath.lastAttempts);
    }

    @Test
    void zivDriver_neverEscalatesWhenFirstAttemptIsAlreadyUnambiguous() {
        CrMath m = new CrMath();
        IntFunction<BigDecimal[]> formula = digits ->
                new BigDecimal[]{NOT_EXACTLY_A_DOUBLE, BigDecimal.ONE.movePointLeft(digits - 5)};
        double result = m.correctlyRounded(formula);
        assertEquals(0.75, result);
        assertEquals(1, CrMath.lastAttempts, "an unambiguous first attempt must not escalate");
    }

    // ======================================================================
    // Hard-cap fail-loud path (G6): unreachable in practice (Lindemann's
    // theorem backs termination for nonzero finite arguments), exercised via
    // a tiny cap that forces the failure deterministically.
    // ======================================================================

    @Test
    void hardCap_failsLoud_neverInfiniteLoop_syntheticNeverResolves() {
        CrMath m = new CrMath(20, 80); // small, deterministic cap
        IntFunction<BigDecimal[]> neverResolves = digits ->
                new BigDecimal[]{NOT_EXACTLY_A_DOUBLE, BigDecimal.ONE}; // permanently ambiguous
        ArithmeticException e = assertThrows(ArithmeticException.class, () -> m.correctlyRounded(neverResolves));
        assertTrue(e.getMessage().contains("hard cap"), "exception must name the hard-cap condition: " + e.getMessage());
    }

    @Test
    void hardCap_failsLoud_realFunctionWithAbsurdlyTinyCap() {
        // tan() near an odd multiple of pi/2 is the one real case in this class that
        // deliberately forces outer-Ziv escalation (see tanCore's cosXErr guard, and
        // escalationStats_acrossHardAndReductionStressInputs below, which observed it
        // needing up to 320 working digits under the production start/cap) -- with the
        // cap pinned below what that escalation needs, resolution is impossible and this
        // MUST fail loud rather than return an uncertified answer.
        double nearPiOver2 = Double.longBitsToDouble(0x3FF921FB54442D18L);
        CrMath tiny = new CrMath(10, 10); // one attempt only, far short of the ~320 digits this case needs
        assertThrows(ArithmeticException.class, () -> tiny.tan(nearPiOver2));
    }

    @Test
    void constructorRejectsInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> new CrMath(1, 100)); // startDigits < 5
        assertThrows(IllegalArgumentException.class, () -> new CrMath(100, 50)); // maxDigits < startDigits
    }

    // ======================================================================
    // Diagnostic: Ziv escalation depth actually needed across every "hard"
    // input the corpus's oracle generator uses (STD-011 T3-phase-2 report
    // data), including the reduction-stress additions. Not a strict
    // correctness assertion beyond "stays within the production hard cap
    // with room to spare" -- its value is the printed distribution.
    // ======================================================================

    @Test
    void escalationStats_acrossHardAndReductionStressInputs() {
        double[] inputs = {
                0.0, -0.0, 1.0, -1.0, 0.5, -0.5,
                Double.longBitsToDouble(0x3FF921FB54442D18L),          // pi/2-nearest
                Double.longBitsToDouble(0x400921FB54442D18L),          // pi-nearest
                3.0 * Double.longBitsToDouble(0x3FF921FB54442D18L),    // 3pi/2-nearest
                2.0 * Double.longBitsToDouble(0x400921FB54442D18L),    // 2pi-nearest
                -Double.longBitsToDouble(0x3FF921FB54442D18L),
                1e300, -1e300, 1e200, 1e100, 1e50, 1e15,
                Math.scalb(1.0, 100), -Math.scalb(1.0, 100),
                Double.MAX_VALUE, -Double.MAX_VALUE,
                Double.MIN_NORMAL, Double.MIN_VALUE, -Double.MIN_VALUE,
        };
        CrMath m = new CrMath();
        int maxDigitsSeen = 0;
        int maxAttemptsSeen = 0;
        int escalatedCount = 0;
        for (double x : inputs) {
            for (Runnable call : new Runnable[]{
                    () -> m.sin(x), () -> m.cos(x), () -> m.tan(x), () -> m.atan(x)
            }) {
                call.run();
                maxDigitsSeen = Math.max(maxDigitsSeen, CrMath.lastResolvedDigits);
                maxAttemptsSeen = Math.max(maxAttemptsSeen, CrMath.lastAttempts);
                if (CrMath.lastAttempts > 1) escalatedCount++;
            }
            if (Math.abs(x) <= 1.0) {
                m.asin(x);
                maxDigitsSeen = Math.max(maxDigitsSeen, CrMath.lastResolvedDigits);
                m.acos(x);
                maxDigitsSeen = Math.max(maxDigitsSeen, CrMath.lastResolvedDigits);
            }
        }
        System.out.println("CrMath escalation stats over " + inputs.length + " hard/reduction-stress inputs: "
                + "max working digits resolved at = " + maxDigitsSeen + " (start = " + CrMath.DEFAULT_START_DIGITS
                + "), max Ziv attempts = " + maxAttemptsSeen + ", calls needing >1 attempt = " + escalatedCount);
        // Sanity bound only, not a tight correctness assertion: reduction cost for sin/cos/atan
        // is absorbed inside a single Ziv attempt via magnitude-scaled working digits (see
        // reduceModPiOver2), so those never need outer escalation -- the one deliberate
        // exception is tan() near an odd multiple of pi/2 (tanCore's cosXErr guard forces
        // escalation whenever cos(x)'s own error swallows its magnitude), which is exactly
        // the "pi/2-nearest" input included above and is expected to dominate this maximum.
        assertTrue(maxDigitsSeen <= CrMath.DEFAULT_MAX_DIGITS,
                "sanity bound: must stay within the production hard cap with room to spare -- saw "
                        + maxDigitsSeen);
    }
}
