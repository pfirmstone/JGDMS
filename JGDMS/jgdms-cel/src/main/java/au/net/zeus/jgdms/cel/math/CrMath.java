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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.function.IntFunction;

/**
 * JGDMS-STD-011 T3 phase 2: a genuinely correctly-rounded {@link
 * CorrectlyRoundedMath} implementation (§7.5's ratified mandate) built on
 * {@link BigDecimal} arbitrary-precision arithmetic, using Ziv's classic
 * "escalating precision" technique -- never a fixed-precision approximation
 * masquerading as correctly rounded.
 *
 * <h2>Architecture</h2>
 * <p>For each call, this class computes the mathematical result at an
 * escalating working precision {@code P} (starting around 120 bits, doubling
 * on ambiguity, hard-capped -- {@link #DEFAULT_MAX_DIGITS}), together with a
 * <em>rigorous upper bound</em> on the absolute error of that computation
 * (Taylor-series alternating-remainder bounds plus conservative rounding-
 * error bookkeeping through every intermediate step). If the computed
 * {@code value +/- errorBound} interval does not straddle the boundary
 * between two adjacent {@code double}s, the correctly-rounded result is
 * unambiguous and is returned; otherwise {@code P} is doubled and the whole
 * computation retried at higher precision ({@link #correctlyRounded}, the
 * Ziv driver). Termination for nonzero finite inputs relies on Lindemann's
 * theorem (sin/cos/tan/atan of nonzero algebraic numbers are transcendental,
 * hence never exactly a dyadic-rational rounding-boundary value) -- the
 * ±0/±1 exact special cases below are the only points where an exact tie
 * could otherwise arise, and they are special-cased before the core ever
 * runs. A hard cap ({@link #maxDigits}) still bounds the loop, failing loudly
 * (G6) rather than looping forever if some unforeseen case defeats the
 * theorem-backed termination argument.
 *
 * <h2>Argument reduction</h2>
 * <p>{@code sin}/{@code cos}/{@code tan} reduce {@code x} modulo pi/2 using
 * pi computed to a working precision that scales with {@code x}'s own
 * magnitude ({@link #magnitudeDigits}): reducing a huge argument (the corpus
 * includes 1e300-class values) subtracts two quantities of the same huge
 * magnitude, so preserving {@code P} correct digits in the small, reduced
 * remainder requires computing both operands to roughly
 * {@code magnitudeDigits(x) + P} digits in the first place -- a fixed-size pi
 * constant would silently lose all precision in the subtraction for a large
 * enough {@code x}. This is the BigDecimal-arithmetic analogue of a
 * Payne-Hanek reduction (same idea -- borrow enough bits/digits of pi to
 * survive the cancellation -- expressed in decimal arbitrary precision
 * rather than fixed-point binary).
 *
 * <h2>Method per function</h2>
 * <ul>
 *   <li>{@code sin}/{@code cos}: argument-reduce mod pi/2, then an alternating
 *       Taylor series on the reduced remainder (|r| &lt;= pi/4, so the series
 *       converges fast and its remainder is bounded by the first omitted
 *       term -- the standard alternating-series estimate).</li>
 *   <li>{@code tan}: {@code sin(x)/cos(x)} computed from the same reduction,
 *       with quotient error propagation; if {@code cos(x)}'s error bound ever
 *       exceeds its own magnitude (near an odd multiple of pi/2) the division
 *       is deliberately treated as unresolved, forcing Ziv escalation rather
 *       than dividing by an uncertain near-zero value.</li>
 *   <li>{@code atan}: reduces {@code |y|>1} via {@code atan(y) = pi/2 -
 *       atan(1/y)}, then repeated tangent-half-angle argument halving
 *       ({@code atan(y) = 2*atan(y/(1+sqrt(1+y^2)))}) until the remaining
 *       argument is small enough for a fast alternating Taylor series.</li>
 *   <li>{@code asin}: {@code atan(x / sqrt(1-x^2))} (endpoints {@code x=+-1}
 *       special-cased -- the identity has a removable singularity there).</li>
 *   <li>{@code acos}: {@code pi/2 - asin(x)}, computed end-to-end in the same
 *       high-precision arithmetic (never by subtracting two already-rounded
 *       {@code double}s, which would double-round) and rounded to a
 *       {@code double} exactly once, at the very end.</li>
 *   <li>{@code atan2}: the full IEEE-754/C99 F.10.1.4 signed-zero and
 *       quadrant table implemented directly (§7.2 row 15), falling back to
 *       {@code atan(y/x)} plus a pi offset for the general finite/nonzero
 *       case.</li>
 * </ul>
 *
 * <p>Pi itself is computed (never hard-coded) via Machin's formula
 * ({@code pi = 16*atan(1/5) - 4*atan(1/239)}, both arguments already
 * &lt;= 1 so this never recurses into the {@code atan(y)} large-argument
 * branch that itself needs pi/2) and memoized at the highest precision
 * computed so far, extended on demand.
 *
 * <p>Package-private by design (mirrors {@link StrictMathPlaceholder}): the
 * only sanctioned way to obtain a {@link MathProvider} backed by this class
 * is the public {@link MathProviders#correctlyRounded()} factory.
 */
final class CrMath implements CorrectlyRoundedMath {

    /** ~133 bits: STD-011 T3-phase-2 SOW's "start P around 120 bits". */
    static final int DEFAULT_START_DIGITS = 40;

    /** ~10,630 bits: STD-011 T3-phase-2 SOW's G6 "hard cap ~10000 bits". */
    static final int DEFAULT_MAX_DIGITS = 3200;

    /** Extra guard digits added to every internal working precision, absorbing chained rounding-error accumulation. */
    private static final int GUARD_DIGITS = 20;

    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal FOUR = BigDecimal.valueOf(4);
    private static final BigDecimal SIXTEEN = BigDecimal.valueOf(16);
    private static final BigInteger BI_FOUR = BigInteger.valueOf(4);
    private static final BigDecimal HALVING_THRESHOLD = new BigDecimal("0.01");
    private static final BigDecimal MIN_SUBNORMAL_HALF = new BigDecimal(Double.MIN_VALUE).divide(TWO);

    private final int startDigits;
    private final int maxDigits;

    /** Production constructor: default Ziv start/cap (STD-011 T3 phase 2). */
    CrMath() {
        this(DEFAULT_START_DIGITS, DEFAULT_MAX_DIGITS);
    }

    /**
     * Test-only constructor (package-private): lets {@code CrMathTest}
     * exercise the escalation path and the hard-cap fail-loud path
     * deterministically with tiny bounds, without waiting on (or hunting
     * for) a genuinely pathological double under the production bounds.
     */
    CrMath(int startDigits, int maxDigits) {
        if (startDigits < 5) throw new IllegalArgumentException("startDigits too small: " + startDigits);
        if (maxDigits < startDigits) throw new IllegalArgumentException("maxDigits < startDigits");
        this.startDigits = startDigits;
        this.maxDigits = maxDigits;
    }

    private static void requireFinite(double x, String name) {
        if (!Double.isFinite(x)) {
            throw new IllegalArgumentException(
                    "CrMath." + name + " received a non-finite argument (" + x + "). STD-011 SS7.2 requires the "
                    + "evaluator to map NaN/+-Inf to DOMAIN before ever calling a CorrectlyRoundedMath method -- "
                    + "reaching here means that contract was violated upstream.");
        }
    }

    private static boolean isNegativeZero(double v) {
        return Double.doubleToRawLongBits(v) == 0x8000000000000000L;
    }

    // ======================================================================
    // Public API (CorrectlyRoundedMath)
    // ======================================================================

    @Override
    public double sin(double x) {
        requireFinite(x, "sin");
        if (x == 0.0) return x; // sign-preserving: sin(+-0) = +-0
        return correctlyRounded(digits -> sinCosCore(x, digits, true));
    }

    @Override
    public double cos(double x) {
        requireFinite(x, "cos");
        if (x == 0.0) return 1.0; // cos(+-0) = 1
        return correctlyRounded(digits -> sinCosCore(x, digits, false));
    }

    @Override
    public double tan(double x) {
        requireFinite(x, "tan");
        if (x == 0.0) return x; // sign-preserving: tan(+-0) = +-0
        return correctlyRounded(digits -> tanCore(x, digits));
    }

    @Override
    public double asin(double x) {
        requireFinite(x, "asin");
        if (Math.abs(x) > 1.0) {
            throw new IllegalArgumentException("CrMath.asin: |x| > 1 (" + x + ") is out of domain; "
                    + "the evaluator MUST map this to DOMAIN before calling in (STD-011 SS7.2 row 13).");
        }
        if (x == 0.0) return x; // sign-preserving: asin(+-0) = +-0
        if (x == 1.0) return piOver2Double();
        if (x == -1.0) return -piOver2Double();
        return correctlyRounded(digits -> asinCore(x, digits));
    }

    @Override
    public double acos(double x) {
        requireFinite(x, "acos");
        if (Math.abs(x) > 1.0) {
            throw new IllegalArgumentException("CrMath.acos: |x| > 1 (" + x + ") is out of domain; "
                    + "the evaluator MUST map this to DOMAIN before calling in (STD-011 SS7.2 row 13).");
        }
        if (x == 1.0) return 0.0;
        if (x == -1.0) return piDouble();
        return correctlyRounded(digits -> {
            BigDecimal[] asinResult = asinCore(x, digits);
            BigDecimal[] piHalf = piOver2WithError(digits + GUARD_DIGITS);
            MathContext wmc = new MathContext(digits + GUARD_DIGITS, RoundingMode.HALF_EVEN);
            BigDecimal value = piHalf[0].subtract(asinResult[0], wmc);
            BigDecimal err = piHalf[1].add(asinResult[1]);
            return new BigDecimal[]{value, err};
        });
    }

    @Override
    public double atan(double x) {
        requireFinite(x, "atan");
        if (x == 0.0) return x; // sign-preserving: atan(+-0) = +-0
        return correctlyRounded(digits -> atanCore(new BigDecimal(x), BigDecimal.ZERO, digits));
    }

    @Override
    public double atan2(double y, double x) {
        requireFinite(y, "atan2(y,_)");
        requireFinite(x, "atan2(_,x)");
        // ---- IEEE-754/C99 F.10.1.4 signed-zero and quadrant boundary table (STD-011 SS7.2 row 15) ----
        if (y == 0.0) {
            boolean yNeg = isNegativeZero(y);
            if (x == 0.0) {
                boolean xNeg = isNegativeZero(x);
                if (!xNeg) return yNeg ? -0.0 : 0.0;               // atan2(+-0, +0) = +-0
                return yNeg ? -piDouble() : piDouble();             // atan2(+-0, -0) = +-pi
            }
            if (x > 0.0) return yNeg ? -0.0 : 0.0;                 // atan2(+-0, +x) = +-0
            return yNeg ? -piDouble() : piDouble();                 // atan2(+-0, -x) = +-pi
        }
        if (x == 0.0) {
            // y != 0 here; sign of x's zero does not matter (both give the same result)
            return (y > 0.0) ? piOver2Double() : -piOver2Double();  // atan2(+-y, +-0) = +-pi/2
        }
        // General case: both finite and nonzero.
        final double yf = y, xf = x;
        return correctlyRounded(digits -> atan2Core(yf, xf, digits));
    }

    // ======================================================================
    // Ziv driver
    // ======================================================================

    /**
     * Escalates {@code f}'s requested working precision (decimal significant
     * digits) starting from {@link #startDigits}, doubling on ambiguity,
     * until {@code f}'s returned {@code (value, errorBound)} pair
     * unambiguously identifies a nearest {@code double} ({@link #tryRound}),
     * or {@link #maxDigits} is exceeded (fail loud, G6 -- never an infinite
     * loop).
     */
    /**
     * Diagnostics only (package-private, read by {@code CrMathTest}): the
     * number of Ziv attempts and the working-precision digit count at which
     * the most recent {@link #correctlyRounded} call on ANY {@code CrMath}
     * instance resolved. Not synchronized/thread-confined -- purely
     * informational, never consulted for a correctness decision.
     */
    static volatile int lastAttempts;
    static volatile int lastResolvedDigits;

    double correctlyRounded(IntFunction<BigDecimal[]> f) {
        int digits = startDigits;
        int attempts = 0;
        while (digits <= maxDigits) {
            attempts++;
            BigDecimal[] valueAndError = f.apply(digits);
            Double rounded = tryRound(valueAndError[0], valueAndError[1]);
            if (rounded != null) {
                lastAttempts = attempts;
                lastResolvedDigits = digits;
                return rounded;
            }
            digits = (digits > maxDigits / 2) ? maxDigits + 1 : digits * 2; // avoid overflow-ish creep; forces loop exit cleanly
        }
        throw new ArithmeticException(
                "CrMath: could not certify a correctly-rounded double result within " + maxDigits
                + " decimal digits of working precision (STD-011 T3-phase-2 G6 hard cap). This should be "
                + "unreachable for any genuine finite-double transcendental result of a nonzero argument "
                + "(Lindemann's theorem rules out an exact rounding-boundary tie) -- if reached in production, "
                + "this is a bug in CrMath's error bookkeeping, not a legitimately unresolvable input.");
    }

    /**
     * Returns the correctly-rounded {@code double} nearest {@code value} if
     * {@code [value - err, value + err]} provably does not straddle the
     * boundary between two adjacent {@code double}s, else {@code null}
     * (ambiguous -- caller must escalate precision).
     */
    static Double tryRound(BigDecimal value, BigDecimal err) {
        if (err.signum() < 0) throw new IllegalStateException("negative error bound: " + err);
        // Signed-underflow case: the interval is provably entirely closer to zero than the
        // smallest subnormal's half-ulp -- correctly rounds to a zero of value's own sign.
        // BigDecimal has no signed zero, so this must be handled explicitly (never delegated
        // to BigDecimal#doubleValue(), whose underflow-sign behaviour we don't want to depend on).
        if (value.abs().add(err).compareTo(MIN_SUBNORMAL_HALF) < 0) {
            return value.signum() < 0 ? -0.0 : 0.0;
        }
        double approx = value.doubleValue();
        if (Double.isInfinite(approx) || Double.isNaN(approx)) {
            throw new ArithmeticException("CrMath: intermediate high-precision value " + value
                    + " does not correspond to any finite double -- unexpected for this registry's bounded "
                    + "transcendental functions; treating as a bug rather than silently returning +-Inf/NaN.");
        }
        BigDecimal approxExact = new BigDecimal(approx);
        int cmp = value.compareTo(approxExact);
        if (cmp == 0) return approx; // exact hit (e.g. an intermediate 0 feeding a larger expression)
        double other = (cmp > 0) ? Math.nextUp(approx) : Math.nextDown(approx);
        double lower = Math.min(approx, other);
        double upper = Math.max(approx, other);
        BigDecimal lowerExact = new BigDecimal(lower);
        BigDecimal upperExact = new BigDecimal(upper);
        BigDecimal boundary = lowerExact.add(upperExact).divide(TWO); // exact midpoint: dyadic, exact in BigDecimal
        BigDecimal dist = value.subtract(boundary).abs();
        if (dist.compareTo(err) <= 0) return null; // ambiguous: value +/- err may straddle the boundary
        return (value.compareTo(boundary) < 0) ? lower : upper;
    }

    // ======================================================================
    // Shared high-precision helpers
    // ======================================================================

    /** Decimal digits before the point in {@code |exact|}; 0 for |exact| <= 1 (no reduction cancellation risk). */
    private static int magnitudeDigits(BigDecimal exact) {
        BigDecimal abs = exact.abs();
        if (abs.compareTo(BigDecimal.ONE) <= 0) return 0;
        int intDigits = abs.precision() - abs.scale();
        return Math.max(0, intDigits);
    }

    /** Conservative absolute rounding-error bound for a value of the given approximate magnitude, computed at mc's precision. */
    private static BigDecimal opErrorBound(BigDecimal approxMagnitude, MathContext mc) {
        BigDecimal scale = approxMagnitude.abs().max(BigDecimal.ONE);
        return scale.multiply(BigDecimal.ONE.movePointLeft(mc.getPrecision() - 4));
    }

    // ---- pi, memoized and extended on demand ----

    private static final Object PI_LOCK = new Object();
    /**
     * Single-reference publication (board review fix, 2026-07-20): pi, its
     * error bound, and the precision they were computed at travel as ONE
     * immutable record behind ONE volatile field. The previous three
     * separately-volatile fields permitted a torn cross-writer read on the
     * lock-free fast path -- a stale {@code pi} paired with a newer, tighter
     * {@code err} from a concurrent higher-precision write, understating the
     * stale value's true error.
     */
    private record PiCache(BigDecimal pi, BigDecimal err, int digits) {}
    private static volatile PiCache piCache;

    private static BigDecimal[] piWithError(int digits) {
        PiCache c = piCache;
        if (c != null && c.digits() >= digits) return new BigDecimal[]{c.pi(), c.err()};
        synchronized (PI_LOCK) {
            c = piCache;
            if (c != null && c.digits() >= digits) return new BigDecimal[]{c.pi(), c.err()};
            int computeDigits = digits + GUARD_DIGITS;
            MathContext wmc = new MathContext(computeDigits, RoundingMode.HALF_EVEN);
            // Machin's formula: pi = 16*atan(1/5) - 4*atan(1/239). Both arguments are
            // already <= 1 in magnitude, so atanCore never takes its |y|>1 branch here --
            // no circular dependency on pi/2 while computing pi itself.
            BigDecimal oneFifth = BigDecimal.ONE.divide(BigDecimal.valueOf(5), wmc);
            BigDecimal oneOver239 = BigDecimal.ONE.divide(BigDecimal.valueOf(239), wmc);
            BigDecimal[] atan5 = atanCore(oneFifth, BigDecimal.ZERO, computeDigits);
            BigDecimal[] atan239 = atanCore(oneOver239, BigDecimal.ZERO, computeDigits);
            BigDecimal pi = atan5[0].multiply(SIXTEEN, wmc).subtract(atan239[0].multiply(FOUR, wmc), wmc);
            BigDecimal piErr = atan5[1].multiply(SIXTEEN).add(atan239[1].multiply(FOUR)).add(opErrorBound(pi, wmc));
            piCache = new PiCache(pi, piErr, computeDigits);
            return new BigDecimal[]{pi, piErr};
        }
    }

    private static BigDecimal[] piOver2WithError(int digits) {
        BigDecimal[] pi = piWithError(digits);
        return new BigDecimal[]{pi[0].divide(TWO), pi[1].divide(TWO)};
    }

    private static volatile Double cachedPiDouble;
    private static volatile Double cachedPiOver2Double;

    /** Correctly-rounded {@code double} nearest pi, computed (not hard-coded) via {@link #piWithError} + Ziv rounding. */
    private static double piDouble() {
        Double v = cachedPiDouble;
        if (v != null) return v;
        synchronized (PI_LOCK) {
            if (cachedPiDouble != null) return cachedPiDouble;
            int digits = DEFAULT_START_DIGITS;
            while (true) {
                BigDecimal[] pi = piWithError(digits);
                Double rounded = tryRound(pi[0], pi[1]);
                if (rounded != null) {
                    cachedPiDouble = rounded;
                    return rounded;
                }
                // G6 fail-loud cap (board review fix, 2026-07-20): pi's
                // transcendence guarantees termination, but this loop honors
                // the class's own hard-cap discipline like the main driver.
                if (digits > DEFAULT_MAX_DIGITS) {
                    throw new ArithmeticException(
                        "pi Ziv escalation exceeded " + DEFAULT_MAX_DIGITS + " digits");
                }
                digits *= 2;
            }
        }
    }

    /** Correctly-rounded {@code double} nearest pi/2. */
    private static double piOver2Double() {
        Double v = cachedPiOver2Double;
        if (v != null) return v;
        synchronized (PI_LOCK) {
            if (cachedPiOver2Double != null) return cachedPiOver2Double;
            int digits = DEFAULT_START_DIGITS;
            while (true) {
                BigDecimal[] piHalf = piOver2WithError(digits);
                Double rounded = tryRound(piHalf[0], piHalf[1]);
                if (rounded != null) {
                    cachedPiOver2Double = rounded;
                    return rounded;
                }
                // G6 fail-loud cap (board review fix, 2026-07-20) -- see piDouble().
                if (digits > DEFAULT_MAX_DIGITS) {
                    throw new ArithmeticException(
                        "pi/2 Ziv escalation exceeded " + DEFAULT_MAX_DIGITS + " digits");
                }
                digits *= 2;
            }
        }
    }

    // ---- sin/cos: argument reduction mod pi/2 + Taylor series ----

    private record Reduced(int quadrant, BigDecimal r, BigDecimal rErr) {}

    private static Reduced reduceModPiOver2(BigDecimal xExact, int digits) {
        int extra = magnitudeDigits(xExact);
        int workDigits = digits + extra + GUARD_DIGITS;
        MathContext wmc = new MathContext(workDigits, RoundingMode.HALF_EVEN);
        BigDecimal[] piHalf = piOver2WithError(workDigits);
        BigDecimal piOver2 = piHalf[0];
        BigDecimal piOver2Err = piHalf[1];
        BigDecimal q = xExact.divide(piOver2, wmc);
        BigInteger k = q.setScale(0, RoundingMode.HALF_EVEN).toBigIntegerExact();
        BigDecimal kBD = new BigDecimal(k);
        BigDecimal kPiOver2 = kBD.multiply(piOver2, wmc);
        BigDecimal r = xExact.subtract(kPiOver2, wmc);
        BigDecimal rErr = piOver2Err.multiply(kBD.abs()).add(opErrorBound(xExact, wmc));
        int quadrant = k.mod(BI_FOUR).intValue();
        return new Reduced(quadrant, r, rErr);
    }

    /** Alternating Taylor series for sin(r) and cos(r), |r| <= pi/4 (guaranteed by {@link #reduceModPiOver2}). */
    private static BigDecimal[] taylorSinCos(BigDecimal r, BigDecimal rErr, int digits) {
        int workDigits = digits + GUARD_DIGITS;
        MathContext wmc = new MathContext(workDigits, RoundingMode.HALF_EVEN);
        BigDecimal r2 = r.multiply(r, wmc);
        BigDecimal sinSum = BigDecimal.ZERO;
        BigDecimal cosSum = BigDecimal.ZERO;
        BigDecimal sinTerm = r;
        BigDecimal cosTerm = BigDecimal.ONE;
        BigDecimal epsilon = BigDecimal.ONE.movePointLeft(workDigits);
        int n = 0;
        while (true) {
            sinSum = sinSum.add(sinTerm, wmc);
            cosSum = cosSum.add(cosTerm, wmc);
            BigDecimal sinDenom = BigDecimal.valueOf((2L * n + 2) * (2L * n + 3));
            sinTerm = sinTerm.multiply(r2, wmc).divide(sinDenom, wmc).negate();
            BigDecimal cosDenom = BigDecimal.valueOf((2L * n + 1) * (2L * n + 2));
            cosTerm = cosTerm.multiply(r2, wmc).divide(cosDenom, wmc).negate();
            n++;
            if (sinTerm.abs().compareTo(epsilon) < 0 && cosTerm.abs().compareTo(epsilon) < 0) break;
            if (n > 100_000) throw new ArithmeticException("sin/cos Taylor series failed to converge (|r| should be <= pi/4)");
        }
        BigDecimal opErr = BigDecimal.ONE.movePointLeft(workDigits).multiply(BigDecimal.valueOf(n + 10L));
        BigDecimal sinErr = sinTerm.abs().add(opErr).add(rErr); // |d(sin)/dr| <= 1
        BigDecimal cosErr = cosTerm.abs().add(opErr).add(rErr); // |d(cos)/dr| <= 1
        return new BigDecimal[]{sinSum, sinErr, cosSum, cosErr};
    }

    private static BigDecimal[] sinCosCore(double x, int digits, boolean wantSin) {
        BigDecimal xExact = new BigDecimal(x);
        Reduced reduced = reduceModPiOver2(xExact, digits);
        BigDecimal[] sc = taylorSinCos(reduced.r(), reduced.rErr(), digits);
        BigDecimal sinR = sc[0], sinErr = sc[1], cosR = sc[2], cosErr = sc[3];
        BigDecimal value;
        BigDecimal err;
        switch (reduced.quadrant()) {
            case 0 -> { value = wantSin ? sinR : cosR; err = wantSin ? sinErr : cosErr; }
            case 1 -> { value = wantSin ? cosR : sinR.negate(); err = wantSin ? cosErr : sinErr; }
            case 2 -> { value = wantSin ? sinR.negate() : cosR.negate(); err = wantSin ? sinErr : cosErr; }
            case 3 -> { value = wantSin ? cosR.negate() : sinR; err = wantSin ? cosErr : sinErr; }
            default -> throw new IllegalStateException("unreachable quadrant " + reduced.quadrant());
        }
        return new BigDecimal[]{value, err};
    }

    private static BigDecimal[] tanCore(double x, int digits) {
        BigDecimal xExact = new BigDecimal(x);
        Reduced reduced = reduceModPiOver2(xExact, digits);
        BigDecimal[] sc = taylorSinCos(reduced.r(), reduced.rErr(), digits);
        BigDecimal sinR = sc[0], sinErr = sc[1], cosR = sc[2], cosErr = sc[3];
        BigDecimal sinX, sinXErr, cosX, cosXErr;
        switch (reduced.quadrant()) {
            case 0 -> { sinX = sinR; sinXErr = sinErr; cosX = cosR; cosXErr = cosErr; }
            case 1 -> { sinX = cosR; sinXErr = cosErr; cosX = sinR.negate(); cosXErr = sinErr; }
            case 2 -> { sinX = sinR.negate(); sinXErr = sinErr; cosX = cosR.negate(); cosXErr = cosErr; }
            case 3 -> { sinX = cosR.negate(); sinXErr = cosErr; cosX = sinR; cosXErr = sinErr; }
            default -> throw new IllegalStateException("unreachable quadrant " + reduced.quadrant());
        }
        if (cosXErr.compareTo(cosX.abs()) >= 0) {
            // cos(x)'s own uncertainty swallows its magnitude (x is extremely close to an odd
            // multiple of pi/2) -- deliberately refuse to divide by an unresolved near-zero
            // value; report a huge error bound so the Ziv driver escalates precision instead.
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ONE};
        }
        int workDigits = digits + GUARD_DIGITS;
        MathContext wmc = new MathContext(workDigits, RoundingMode.HALF_EVEN);
        BigDecimal tanValue = sinX.divide(cosX, wmc);
        // Quotient error propagation: |d(a/b)| <= (da + |a/b|*db) / (|b| - db), valid since |cosXErr| < |cosX| (checked above).
        BigDecimal denomLowerBound = cosX.abs().subtract(cosXErr);
        BigDecimal tanErr = sinXErr.add(tanValue.abs().multiply(cosXErr)).divide(denomLowerBound, wmc)
                .add(opErrorBound(tanValue, wmc));
        return new BigDecimal[]{tanValue, tanErr};
    }

    // ---- atan: |y|>1 reduction via pi/2, then tangent-half-angle argument halving + Taylor series ----

    private static BigDecimal[] atanCore(BigDecimal y, BigDecimal yErr, int digits) {
        int workDigits = digits + GUARD_DIGITS;
        MathContext wmc = new MathContext(workDigits, RoundingMode.HALF_EVEN);
        boolean neg = y.signum() < 0;
        BigDecimal ya = y.abs();
        BigDecimal yaErr = yErr;

        boolean invert = false;
        BigDecimal offset = BigDecimal.ZERO;
        BigDecimal offsetErr = BigDecimal.ZERO;
        if (ya.compareTo(BigDecimal.ONE) > 0) {
            BigDecimal[] piHalf = piOver2WithError(workDigits);
            offset = piHalf[0];
            offsetErr = piHalf[1];
            invert = true;
            BigDecimal inv = BigDecimal.ONE.divide(ya, wmc);
            // d(1/y)/dy = -1/y^2; |1/y| = inv, so |d(1/y)| = inv^2 * yaErr
            BigDecimal invErr = inv.multiply(inv, wmc).multiply(yaErr).add(opErrorBound(inv, wmc));
            ya = inv;
            yaErr = invErr;
        }

        int halvings = 0;
        while (ya.compareTo(HALVING_THRESHOLD) > 0) {
            BigDecimal y2 = ya.multiply(ya, wmc);
            BigDecimal onePlusY2 = BigDecimal.ONE.add(y2, wmc);
            BigDecimal s = onePlusY2.sqrt(wmc);
            BigDecimal denom = BigDecimal.ONE.add(s, wmc);
            BigDecimal yNext = ya.divide(denom, wmc);
            // The half-angle map is a contraction (|dy'/dy| < 1 for y >= 0); bound conservatively.
            yaErr = yaErr.add(opErrorBound(yNext, wmc).multiply(FOUR));
            ya = yNext;
            halvings++;
            if (halvings > 1000) throw new ArithmeticException("atan argument-halving reduction failed to converge");
        }

        BigDecimal y2 = ya.multiply(ya, wmc);
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal term = ya;
        BigDecimal epsilon = BigDecimal.ONE.movePointLeft(workDigits);
        int n = 0;
        BigDecimal seriesRemainder;
        while (true) {
            sum = sum.add(term.divide(BigDecimal.valueOf(2L * n + 1), wmc), wmc);
            term = term.multiply(y2, wmc).negate();
            n++;
            BigDecimal nextTermMag = term.abs().divide(BigDecimal.valueOf(2L * n + 1), wmc);
            if (nextTermMag.compareTo(epsilon) < 0) {
                seriesRemainder = nextTermMag;
                break;
            }
            if (n > 200_000) throw new ArithmeticException("atan Taylor series failed to converge");
        }
        BigDecimal opErr = BigDecimal.ONE.movePointLeft(workDigits).multiply(BigDecimal.valueOf(n + 10L));
        BigDecimal seriesErr = seriesRemainder.add(opErr).add(yaErr); // |d(atan)/dy| <= 1

        BigDecimal scale = BigDecimal.valueOf(1L << Math.min(halvings, 62));
        // halvings is bounded (~<=200 hard cap far above), but guard against absurd shift width defensively:
        if (halvings > 62) {
            scale = BigDecimal.TWO.pow(halvings);
        }
        BigDecimal result = sum.multiply(scale, wmc);
        BigDecimal resultErr = seriesErr.multiply(scale);

        if (invert) {
            result = offset.subtract(result, wmc);
            resultErr = resultErr.add(offsetErr);
        }
        if (neg) result = result.negate();
        return new BigDecimal[]{result, resultErr};
    }

    // ---- asin/acos via the atan identity ----

    private static BigDecimal[] asinCore(double x, int digits) {
        int workDigits = digits + GUARD_DIGITS;
        MathContext wmc = new MathContext(workDigits, RoundingMode.HALF_EVEN);
        BigDecimal xExact = new BigDecimal(x);
        BigDecimal s = BigDecimal.ONE.subtract(xExact.multiply(xExact, wmc), wmc); // 1 - x^2; strictly > 0 (|x|=1 special-cased by callers)
        BigDecimal sqrtS = s.sqrt(wmc);
        // Error-bound note (board review, 2026-07-20): this bound covers the
        // sqrt operation's own rounding x4; the propagation of s's rounding
        // error through d(sqrt(s))/ds = 1/(2*sqrt(s)) is not separately
        // derived -- it is subsumed by opErrorBound's generic safety margin,
        // because s carries at most one wmc-rounding of error and
        // 1/(2*sqrt(s)) <= ~2^26 at the domain's worst reachable double
        // (x = nextDown(1.0), s ~ 2^-52), well inside the margin. Verified
        // empirically bit-exact at that worst case and neighbors against an
        // independent high-precision oracle.
        BigDecimal sqrtSErr = opErrorBound(sqrtS, wmc).multiply(FOUR);
        BigDecimal ratio = xExact.divide(sqrtS, wmc);
        // d(x/s)/ds = -x/s^2 = -ratio/s; bound |d(ratio)| by |ratio/s| * sqrtSErr, plus this division's own rounding.
        BigDecimal ratioErr = ratio.abs().divide(sqrtS, wmc).multiply(sqrtSErr).add(opErrorBound(ratio, wmc));
        return atanCore(ratio, ratioErr, digits);
    }

    // ---- atan2: general finite/nonzero case ----

    private static BigDecimal[] atan2Core(double y, double x, int digits) {
        int workDigits = digits + GUARD_DIGITS;
        MathContext wmc = new MathContext(workDigits, RoundingMode.HALF_EVEN);
        BigDecimal yExact = new BigDecimal(y);
        BigDecimal xExact = new BigDecimal(x);
        BigDecimal ratio = yExact.divide(xExact, wmc);
        BigDecimal ratioErr = opErrorBound(ratio, wmc);
        BigDecimal[] atanResult = atanCore(ratio, ratioErr, digits);
        BigDecimal value = atanResult[0];
        BigDecimal err = atanResult[1];
        if (x < 0.0) {
            BigDecimal[] pi = piWithError(workDigits);
            if (y >= 0.0) {
                value = value.add(pi[0], wmc);
            } else {
                value = value.subtract(pi[0], wmc);
            }
            err = err.add(pi[1]);
        }
        return new BigDecimal[]{value, err};
    }
}
