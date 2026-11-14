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

package au.net.zeus.jgdms.cel.eval;

import au.net.zeus.jgdms.cel.CelError;

/**
 * The pure numeric and ordering rules of JGDMS-STD-011 that {@link Evaluator} (the
 * reference implementation) and {@link CompiledExpression} must apply identically.
 * They live here once so the two evaluators cannot drift apart on them.
 * <p>
 * <b>Epistemic status of the differential test.</b> Because both evaluators call this
 * class, a fault inside it is invisible to a walker-versus-compiled comparison. These
 * rules are covered by the spec-derived tests ({@code NumericEdgeCasesTest},
 * {@code NaNSignedZeroTest}, {@code StringOrderingLazinessTest}, {@code CorpusRunnerTest}).
 * Rules that only one evaluator uses, or that are control flow rather than arithmetic
 * (field resolution, the {@code &&}/{@code ||} pinning, {@code in} scanning), stay in
 * each evaluator on purpose, so the differential test has two implementations to compare.
 */
final class CelSemantics {

    private CelSemantics() {}

    /** Returned by {@link #compareIntDoubleExact} when the double is NaN; never a possible comparison result. */
    static final int UNORDERED = Integer.MIN_VALUE;

    private static final double LONG_MIN_AS_DOUBLE = -9223372036854775808.0; // -2^63, exact
    private static final double LONG_MAX_EXCLUSIVE_AS_DOUBLE = 9223372036854775808.0; // 2^63, exact

    /** STD-011 §7.3's pinned bit patterns; {@code Math.toRadians}/{@code toDegrees} differ from these in the last place. */
    private static final long C_DEG2RAD_BITS = 0x3F91DF46A2529D39L;
    private static final long C_RAD2DEG_BITS = 0x404CA5DC1A63C1F8L;
    private static final double C_DEG2RAD = Double.longBitsToDouble(C_DEG2RAD_BITS);
    private static final double C_RAD2DEG = Double.longBitsToDouble(C_RAD2DEG_BITS);

    /**
     * Exact int64-vs-double comparison per §4.3.3's pinned algorithm.
     * Returns {@link #UNORDERED} if {@code d} is NaN (callers map that to
     * "not equal" / "every ordered comparison false"); otherwise returns
     * negative/zero/positive as {@code i <=> d}.
     */
    static int compareIntDoubleExact(long i, double d) {
        if (Double.isNaN(d)) return UNORDERED;
        if (d >= 9223372036854775808.0) return -1; // d >= 2^63: i (<= 2^63-1) is always smaller
        if (d < -9223372036854775808.0) return 1;  // d < -2^63: i (>= -2^63) is always larger
        double floorD = Math.floor(d);
        long floorLong = (long) floorD; // safe: floorD is within [-2^63, 2^63) here
        int cmp = Long.compare(i, floorLong);
        if (cmp != 0) return cmp;
        double frac = d - floorD;
        return (frac > 0.0) ? -1 : 0; // i == floor(d); a positive fractional part makes d strictly greater
    }

    static boolean intDoubleEqual(long i, double d) {
        return compareIntDoubleExact(i, d) == 0;
    }

    /**
     * Lexicographic Unicode code-point comparison (§4.4) -- never {@code
     * String#compareTo} (UTF-16 code-unit order).
     * <p>
     * <b>Lazy and {@code O(min)} (B3 §4.4 lock-hold fix).</b> The two operands
     * are scanned in lockstep <em>by code point</em> ({@code codePointAt} +
     * advance by {@link Character#charCount}), returning at the first differing
     * code point, so the work is {@code O(index of first difference)} in time
     * and {@code O(1)} in extra allocation -- neither operand is ever
     * materialised via {@code codePoints().toArray()}. This is what makes the
     * {@code CostModel} string-ordering tightening ({@code 1 + min(S,
     * literalLen)}) sound: a one-character literal no longer forces a full scan
     * of a large candidate field. The result is byte-for-byte identical to the
     * former eager array walk: equal code points up to the shorter operand's
     * length compare by remaining length, i.e. the operand with fewer code
     * points sorts first. {@code codePointAt}/{@code charCount} reproduce the
     * exact code-point sequence of {@link String#codePoints()} (including for
     * astral pairs, and identically even for the contract-forbidden lone
     * surrogate, which both treat as its own code point), so equivalence holds
     * for all inputs.
     */
    static int compareCodePoints(String a, String b) {
        int lenA = a.length();
        int lenB = b.length();
        int ia = 0;
        int ib = 0;
        while (ia < lenA && ib < lenB) {
            int cpA = a.codePointAt(ia);
            int cpB = b.codePointAt(ib);
            if (cpA != cpB) return Integer.compare(cpA, cpB);
            ia += Character.charCount(cpA);
            ib += Character.charCount(cpB);
        }
        // Every compared code point was equal and consumed one code point from
        // each side, so the side with characters still remaining has strictly
        // more code points and therefore sorts after the exhausted side.
        if (ia < lenA) return 1;   // a has more code points => a > b
        if (ib < lenB) return -1;  // b has more code points => a < b
        return 0;                  // identical code-point sequences
    }

    /** Unsigned-octet lexicographic comparison (§4.5). */
    static int compareUnsignedBytes(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int ai = a[i] & 0xFF;
            int bi = b[i] & 0xFF;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return Integer.compare(a.length, b.length);
    }

    /**
     * The zero-divisor and overflow guard shared by {@code int} {@code /} and {@code %} (§4.3.1);
     * {@code null} when {@code a / b} (or {@code a % b}) may go ahead.
     * <p>
     * {@code Long.MIN_VALUE % -1} is mathematically 0 and Java's raw {@code %} agrees, but STD-011 pins
     * it to OVERFLOW for cross-language (Rust) alignment, so {@code %} takes the same guard as {@code /}.
     */
    static CelError intDivModError(long a, long b) {
        if (b == 0L) return CelError.DIVISION_BY_ZERO;
        if (a == Long.MIN_VALUE && b == -1L) return CelError.OVERFLOW;
        return null;
    }

    /**
     * The domain and range check of {@code int(x: double)} (§7.2 row 4); {@code null} when {@code x}
     * converts. NaN/±Inf are checked explicitly and out-of-range magnitude before narrowing, never a bare
     * {@code (long) x} cast, because that cast saturates.
     */
    static CelError intOfDoubleError(double x) {
        if (!Double.isFinite(x)) return CelError.DOMAIN;
        double truncated = truncate(x);
        if (truncated < LONG_MIN_AS_DOUBLE || truncated >= LONG_MAX_EXCLUSIVE_AS_DOUBLE) {
            return CelError.OVERFLOW;
        }
        return null;
    }

    /** The truncation toward zero of {@code x}; defined only when {@link #intOfDoubleError} returned {@code null}. */
    static long intOfDoubleValue(double x) {
        return (long) truncate(x);
    }

    private static double truncate(double x) {
        return x < 0.0 ? Math.ceil(x) : Math.floor(x);
    }

    /** {@code radians(x)}: one multiply by the pinned constant, never {@code Math.toRadians} (§7.3). */
    static double radians(double x) {
        return x * C_DEG2RAD;
    }

    /** {@code degrees(x)}: one multiply by the pinned constant, never {@code Math.toDegrees} (§7.3). */
    static double degrees(double x) {
        return x * C_RAD2DEG;
    }
}
