/*
 * CoreMathTan: Java port of CORE-MATH's binary64 tangent, src/binary64/tan/tan.c at commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7,
 * round-to-nearest path only.
 *
 * DERIVATION: this file is a Java port, that is a modified and derived work, of
 * src/binary64/tan/tan.c of the CORE-MATH project at commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7.
 * It is not part of CORE-MATH and is not endorsed by its authors. The copyright and permission notice below are the upstream's, reproduced without change.
 *
 * Copyright (c) 2022-2025 Paul Zimmermann and Tom Hubrecht
 *
 * This file is part of the CORE-MATH project
 * (https://core-math.gitlabpages.inria.fr/).
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package au.net.zeus.jgdms.cel.math;

import static au.net.zeus.jgdms.cel.math.CoreMathSupport.MODE_ACCURATE;
import static au.net.zeus.jgdms.cel.math.CoreMathSupport.MODE_FAST;

/**
 * Correctly-rounded binary64 tangent, a port of CORE-MATH's {@code cr_tan} ({@code tan.c:2245-2297}) with its fast path
 * {@code tan_fast} ({@code tan.c:1943-2080}) and its accurate path {@code tan_accurate} ({@code tan.c:2083-2243}). Round-to-nearest only.
 *
 * <h2>Structure</h2>
 * <ul>
 *   <li>{@code |x| <= 0x1.d12ed0af1a27ep-27}: tan(x) rounds to x; {@code fma(x, 2^-54, x)} (zero returns itself, so {@code tan(-0.0) = -0.0}).</li>
 *   <li>Otherwise the argument is reduced to {@code frac(|x|/(2 pi))} as an index {@code i} on the grid {@code i/2^11} plus a double-double
 *       {@code h + l} ({@code reduce_fast}: one product with 1/(2 pi) below 2 pi, a Payne-Hanek product with the 1/(2 pi) table above), folded
 *       by the identities tan(pi + x) = tan(x), tan(pi/2 + x) = -cot(x), tan(pi/2 - x) = cot(x) into {@code i < 256} and a flag for tan or cot.
 *       Sine and cosine of {@code i/2^11 + h + l} come from the {@code SC} table and two short polynomials, are divided
 *       ({@code fast_div}), and the quotient's error bound {@code 0x1.1ap-66 * |result|} decides the rounding. When the argument reduces to
 *       {@code i = 0, h < 2^-37} the fast path declines (it returns the error 1).</li>
 *   <li>When {@code h + (l - err)} and {@code h + (l + err)} differ, {@code tan_accurate}: a 128-bit reduction ({@code reduce}, {@code reduce2}),
 *       the {@code dint64_t} polynomials {@code PS}, {@code PC} and tables {@code S}, {@code C}, a 128-bit division, and a final check that
 *       the result is farther than 86 ulps (of the 128-bit mantissa) from a rounding boundary, with two hard-coded exceptions. NOT IN THE C: a GUARD widens that window
 *       by K/R ulps after a fold (R the folded argument), by K when nothing was folded, and answers "undecided" inside it, so that {@link CoreMath} delegates to {@link CrMath} (section "The guard" below).</li>
 * </ul>
 *
 * <h2>Proof status, and what the evidence is (read 2026-11-12)</h2>
 * CORE-MATH's homepage lists <b>no published correctness proof for tan</b> (Chisel's web check of 2026-11-12; the nine binary64 proofs listed are
 * acos, acosh, asin, cbrt, cos, cosh, sin, sinh, tanh). This port's correctness therefore rests on (a) the C authors' worst-case search recorded in
 * {@code tan.wc} ({@code CoreMathTanSlowTest} runs every finite entry of it), (b) differential tests against {@link CrMath} on at least 5,000,000
 * seeded random inputs, and (c) the fidelity of the port to the C. That is weaker evidence than a proof, which is why the random population of this
 * function is larger than sine's. The C comment at {@code tan.c:2233-2235} ("a hard-to-round case, but since all hard-to-round cases are known and
 * pass all tests, we are ok") is the one place where the C's correctness argument is by search, not by bound: a result within the window of a rounding
 * boundary that is not one of the two {@code exceptions} would be rounded without a guarantee. The C's window is 86 ulps, which the next section shows is not the
 * accurate path's error after a fold; this port widens it (the guard) and delegates what falls inside. {@link #accurateAmbiguous} reports whether the 86-ulp test fired;
 * that is a WIRING CHECK on the two exception inputs, not a hard-case detector (an 86-ulp window is about 2^-68 of the spacing of the doubles: expected hits in 7.6 million
 * random inputs about 2^-45), so its count of zero says nothing about a third hard case.
 *
 * <h2>Properties of the C found while porting (MEASURED 2026-11-12 by the phase B2 tests; the port transcribes the C text, these are not port defects)</h2>
 * <ul>
 *   <li><b>The 86-ulp claim is false after a fold</b> (found by the phase B2 reviews, Opus M1 and Fable F-1, independently, 2026-11-12; reproduced by
 *       {@code CoreMathTanFoldErrorReproducerTest}). {@code reduce} bounds its error relative to {@code F = frac(x/(2 pi))} (tan.c:1538-1541). {@code tan_accurate} then
 *       subtracts exact multiples of a quarter turn (the folds, tan.c:2101-2123), which leaves the ABSOLUTE error unchanged, and writes the bound as if it were relative to the
 *       folded argument {@code R} (tan.c:2127-2131), which is smaller than F by the factor F/R, unbounded as x approaches a multiple of pi/2. The accurate path's error is
 *       therefore about {@code 2.6/R} ulps (Fable: largest of error[ulps] x R[turns] 2.58 over the 938,104 folded {@code tan.wc} entries; 4.06e18 ulps, about 66 correct bits, at
 *       {@code x = 0x1.6ac5b262ca1ffp+849}), not 86; without a fold (F below 1/8) the 86 held (0 above 86 ulps of 176,857, Fable). No wrong result was found in any population
 *       (3.8% of the {@code tan.wc} entries are farther than 86 ulps from tan(x); every one still rounds correctly), but correct rounding is unproved for inputs with {@code |tan|}
 *       or {@code 1/|tan|} between about 2^27 and 2^48 that {@code tan.wc} does not list; both reviewers estimate 0.02 to 0.06 misrounded doubles in all of binary64 (HYPOTHESIS,
 *       uniform model). The fast path is not affected (it uses an absolute bound and declines {@code R < 2^-37}). The guard below closes the gap in this port.</li>
 *   <li>{@code reduce} for {@code ex <= 1} (|x| below 2): the C's comment (tan.c:1570-1574) bounds the reduction's relative error by 2^-126.852 by treating the multiplier's approximation error
 *       2^-130.22 as relative; it is absolute, and relative to 1/(2 pi) it is 2^-127.57, so the provable bound for that branch is (1 + 2^-127.57)(1 + 2^-127) - 1 &lt; 2^-126.26. {@code CoreMathTanReductionTest}
 *       measured 24 of 96 inputs above the C's stated 2^-126.67 (worst 2^-126.34) and none above 2^-126.26; Fable 1,047 of 5,600 (largest 2^-126.277, 0 above 2^-126.26); for {@code ex >= 2} the stated
 *       2^-126.67 held (0 of 441; 0 of 204,600, Fable). This is the smaller of the two gaps: carried through the C's chain it makes the window 88 ulps, negligible next to the fold.</li>
 *   <li>{@code set_dd} with {@code c1 = 0}: the C writes the exponent field of {@code l} as {@code f - 64 - g} where the {@code c1 != 0} branch has {@code f - 53 - g}, so {@code l} is 2^-11 times the
 *       exact truncation. The branch needs {@code frac(x/(2 pi)) < 2^-64}; the smallest possible is about 2^-62, so tan never reaches it. The Java transcribes the C text (tested against a model written from it; the C was not run).</li>
 *   <li>{@code CoreMathDint#toD}: in the overflow branch for {@code ex > 1025} the C assigns a positive {@code MAX_VALUE} to {@code r.f}, so a negative value overflows to +Infinity; the
 *       subnormal and overflow branches are unreachable from tan (|tan(x)| lies in [0x1.14ae72e6ba22fp-60, 0x1.d9ba9a7975636p+60] above the tiny exit). Ported verbatim, driven by hand cases only (UNVALIDATED beyond those).</li>
 *   <li>{@code add_dint}: tan.c:157-158 says the Sterbenz case is exact; for both mantissas 2^128 - 1 and exponents one apart it is off by half an ulp (Fable C-4, 29 of 29 such pairs; unreachable
 *       from tan, whose operands {@code MAGIC} and {@code ONE} have mantissa 2^127). For the upstream list only.</li>
 * </ul>
 *
 * <h2>The guard (not in the C; operator ruling 2026-11-12, "Yes to all four, add the guard"; window narrowed the same day, "Narrow it")</h2>
 * <b>What it does.</b> After the C's own 86-ulp test and exceptions, {@link #guardFires} tests whether the 128-bit result {@code U} is within the window of a multiple of 2^74, the
 * rounding boundaries and midpoints of the 53-bit result (a CLOSED interval {@code [U - w, U + w]}). The window is {@code 86 + K/R} ulps (2^-128 of the mantissa) when a fold happened and
 * {@code 86 + K} when nothing was folded ({@code F = frac(x/(2 pi))} below 1/8, so {@code R = F}); {@code R = i/2^11 + X} is the folded argument in turns ({@code i} the grid index 0..255
 * and {@code X} the remainder after {@code fold}: the variables {@code i} and {@code xd} of {@code tanAccurate}), {@code K = }{@link CoreMathTanTables#GUARD_K} {@code = 8}, and "a fold
 * happened" is {@code st[2]} of {@link #fold} (the grid index {@code reduce2} returned was 256 or more). When it fires {@link #tan} returns {@code NaN} and {@link CoreMath} delegates the
 * call to {@link CrMath} (exact by construction) and counts it, the pattern it uses for atan2's failure branch. The seams {@link #accurate} and {@link #accurateAmbiguous} stay the C's
 * accurate path (so they can be compared with {@code CrMath} on their own); {@code accurateAmbiguous} reports the guard in {@code flags[1]}; {@link #accurateGuarded} and
 * {@link #tanWithK} are test seams (the guard applied on a path the fast path would decide; the guard with another K).
 * <p>
 * <b>Derivation of the window (DERIVED from the C's component bounds and the corrected {@code reduce} bound, with the premises below; not a machine-checked proof).</b> Let
 * {@code F = frac(x/(2 pi))} (0 &lt;= F &lt; 1) and {@code R} the distance of F from the nearest quarter-turn multiple ({@code R <= F}, {@code R < 1/8}; {@code R = F} exactly when
 * F &lt; 1/8). PREMISES: (1) {@code reduce} returns F with absolute error at most {@code delta*F}, {@code delta = 2^-126.25 = 1.682 * 2^-127} (the provable bound 2^-126.26 of the
 * {@code ex <= 1} branch rounded up; for {@code ex >= 2} the C's 2^-126.67 needs {@code F >= 2^-62}, the C's own bound on how close x can come to a multiple of 2 pi, tan.c:1656-1662, and
 * tan.wc has F &gt;= 2^-62.55 for every finite input, which gives at most 2^-126.55); (2) the index folds and {@code reduce2} are exact, and so is the fold {@code 2^-11 - X} (the C's
 * {@code add_dint (X, MAGIC, X)}) when {@code X >= 2^-12} (Sterbenz); for {@code X < 2^-12} it truncates and errs by up to 2 ulps of X, 2^-138 absolute, which adds at most
 * {@code (pi/2) * 2^-138 * 2^128/R = 0.0015/R} ulps (the constant 5.284 below becomes 5.2855; Opus LOW-1, Fable L-1); (3) the C's 86 contains the reduction's own flat share, 86 for
 * {@code ex >= 2} and about 88 for {@code ex <= 1} (the provable bound of D6), and the rest of it (the polynomials, the tables, {@code div_dint}) is the C's claim (its sage scripts,
 * not re-derived here). Given those: the absolute error of R is at most {@code delta*F}; the result is {@code y = tan(2 pi R)} or {@code cot(2 pi R)};
 * {@code |d ln y / dR| = 4 pi / sin(4 pi R) <= (pi/2)/R} for {@code 0 < R <= 1/8} (as {@code sin t >= 2t/pi} on [0, pi/2]), so the relative error of y is at most
 * {@code delta*F*(pi/2)/R}. In units of the last bit of the 128-bit mantissa (a mantissa below 1, so one relative unit is at most 2^128 ulps) this is at most
 * {@code 1.682 * 2 * (pi/2) * F/R = 5.284 * F/R} ulps, so the error of {@code U} is at most about {@code 88 + 5.29 * F/R} ulps. AFTER A FOLD {@code F <= 1}, so the fold term is at most
 * {@code 5.29/R} and the window {@code 86 + K/R} has slack {@code (8 - 5.29)/R >= 21.7} ulps over it (R &lt; 1/8) against the 2 ulps of premise (3). WITHOUT A FOLD {@code F = R}: the fold
 * term does not exist, the C's own 86 (88 for {@code ex <= 1}) applies, and even adding the whole reduction term on top, {@code 88 + 5.29 = 93.3}, stays below the window
 * {@code 86 + K + 1 = 95} of the code. The measurement: 2.58/R (Fable, {@code tan.wc}), 2.70/R (Fable, random) and 1.608/R ({@code CoreMathTanGuardTest}, 1,409
 * inputs) after a fold, at most 14.4 ulps without one (Fable); K = 8 is 1.51 times the derived constant and 3.0 times the largest measured one. The division {@code K/R} is done in
 * {@link CoreMathDint} arithmetic (relative error 2^-123.67, absorbed by the margin and by the {@code +1}); if the window term reaches 2^74 the window reaches the spacing of the
 * boundaries and the guard fires unconditionally.
 * <p>
 * <b>What it costs.</b> A call reaches the guard only when the fast path is undecided (about 0.02% of random inputs, 30% of inputs within 8 ulps of a multiple of pi/2). It then delegates
 * with probability about {@code 2*W/2^74} for a result spread evenly over the spacing of the doubles, {@code W} the window: any multiple of 2^74 in {@code [U - W, U + W]} counts,
 * representable doubles as well as midpoints, so this is twice the midpoint-only figure {@code 2*W/2^75} (Fable L-2); about 2^-67 without a fold ({@code W = 95}), and after a fold
 * ({@code W = 8/R}) about 2^-30 at R = 2^-40 and 1 below R = 2^-71. The first window of the phase B2 fix pass, {@code 86 + K/R} on EVERY input, also widened the unfolded tiny
 * arguments ({@code R = x/(2 pi)}, 2^32.6 ulps at x = 2^-27) and so delegated 1,571 of the 1,098,254 {@code tan.wc} entries, 554 of the 4,260 golden entries and the two neighbours of the
 * tiny threshold, every one an unfolded input on which the fold gap does not exist (re-check reviews of 22a20ad5a: Opus MEDIUM-1, Fable M-2); the window above does not. MEASURED
 * with the narrowed window (fix pass 2, 2026-11-12, READY section Z; population and rule: calls of the production provider counted by {@code tanGuardDelegations}): 0 of the 1,098,254
 * {@code tan.wc} entries, 0 of the 4,260 golden entries, 0 of 70,512 fast-tier inputs, 0 of 6,500,000 random and 0 of 500,000 near-pole inputs; on the FORCED accurate path the guard fires on
 * 1 of the 1,098,245 in-domain {@code tan.wc} entries, the folded {@code x = 0x1.92456c9a2452ep282} (which the fast path decides) and on none of the other populations.
 *
 * <h2>Which constants the tests can see by behaviour (phase B2 mutation runs, 2026-11-12; DIAGNOSTIC)</h2>
 * Every error constant and every table is a named constant of {@link CoreMathTanTables}, under the committed digest and the comparison with the C
 * text, so none depends on a behavioural test to be guarded. Each mutant below was applied to the working tree (restored with {@code git checkout} after each run) and run against the fast tier
 * (named inputs, the first 2,000 entries and the committed golden of 4,260 entries of {@code tan.wc}, seven random populations of 10,000, the BigInteger
 * models) and, where stated, the slow tier (every one of the 1,098,254 finite {@code tan.wc} entries, 6,500,000 random inputs and 500,000 inputs near multiples of
 * pi/2), the digest and C-text tests apart, which every constant mutant fails by design. Scoring: bits against {@link CrMath}.
 * <ul>
 *   <li>{@code FAST_ERR} (0x1.1ap-66): halved, survives both tiers; divided by 4, seen by 5 of the first 2,000 {@code tan.wc} entries; by 8, 72 of 2,000; by 16, 176 of 2,000
 *       and 470 of 4,260 golden entries (and every slow test); by 256, 351 of 2,000. The hard cases of {@code tan.wc} lie within 2^-96 of a rounding boundary, so any actual error
 *       above the shrunk bound mis-decides them: they measure the actual error of the fast path directly, which lies between 1/4 and 1/2 of the constant (a margin between
 *       2 and 4), like the atan and atan2 constants of phase B1. A random population does not see halving.</li>
 *   <li>{@code ACCURATE_ERR} (86): halved to 43, invisible to every behavioural test; set to 1, the 86-ulp test no longer fires for the exception inputs but no result changed in the fast tier (the slow tier was not run for that mutant).</li>
 *   <li>The {@code exceptions} table: removing the loop changes no result on any entry of {@code tan.wc} (which contains both inputs), on the four exception inputs (both
 *       signs) or on the 6,500,000 random inputs, so for round-to-nearest in this arithmetic the table is redundant (as four of the five acos exceptions of phase B1 were); it is
 *       ported because the C has it.</li>
 *   <li>{@code H_MIN} 2^-37 to 2^-38 (both tiers) and {@code ERR_UNDECIDED} 1 to 2^-70 (fast tier): invisible to behaviour (the second leaves {@code left != right} for every reduced argument that reaches it).
 *       {@code CH} moved by one ulp: seen at once by the fast tier.</li>
 *   <li>A dropped carry in {@code reduce} (into c[2]): seen by the BigInteger model, by the named inputs and by 4 of the 7 slow-tier tests; in {@code reduce_fast} (into c[1], exponent
 *       field above 1074): by the BigInteger model alone in the fast tier and by 5 of 1,098,254 {@code tan.wc} entries in the slow tier (probability 2^-11 per input).</li>
 * </ul>
 *
 * <h2>What differs from the C, and why</h2>
 * <ul>
 *   <li>The C falls from the fast path into {@code tan_accurate} by a call. Here {@link #fast} returns {@code NaN} (never a valid result for a finite
 *       argument) when the bounds differ, {@link #accurate} forces the accurate path, and {@link #tan} is the C's behaviour; one private method
 *       {@code run} holds the C's control flow once.</li>
 *   <li>{@code tan_fast}, {@code reduce_fast}, {@code fast_div} and the polynomial evaluators return a double-double through pointers; here the
 *       pair is a two-element array written by the callee. {@code fast_two_sum} is the high word inline and {@link CoreMathSupport#fastTwoSumLow}
 *       for the low word; {@code a_mul}, {@code s_mul} and {@code d_mul} are private statics.</li>
 *   <li>{@code fegetround()} ({@code tan.c:467}, in {@link CoreMathDint#subnormalize}): only {@code FE_TONEAREST} is ported. {@code errno}
 *       ({@code EDOM}, {@code ERANGE}) is set only under {@code #ifdef CORE_MATH_SUPPORT_ERRNO}; Java has no {@code errno}, so those branches are
 *       dropped, the {@code return} stays. {@code fenv} flags (the C raises invalid for an infinity: {@code x - x}) have no Java counterpart.</li>
 *   <li>{@code Math.fma} is {@code __builtin_fma}; Java never contracts {@code a*b+c} into an fma on its own, the C compiler may (the CORE-MATH
 *       README says the code must work with contraction everywhere or nowhere).</li>
 *   <li>The C's unsigned 64-bit comparisons ({@code c[0] < (uint64_t) u}, {@code lo0 > U->lo}, {@code lo1 < U->lo}, {@code k < 128}) are
 *       {@link Long#compareUnsigned}.</li>
 * </ul>
 *
 * <h2>Inputs</h2>
 * Finite {@code x}, zero included. tan has no pole in binary64: no finite input gives an infinite result (the largest {@code |tan(x)|} is
 * 0x1.d9ba9a7975636p+60, at {@code x = 0x1.6ac5b262ca1ffp+849}), and none gives a subnormal or zero result except by the tiny-argument exit. NaN
 * returns {@code x + x} and an infinity {@code x - x} (a NaN), as the C does; the provider {@link CoreMath} never lets either reach this class
 * (the evaluator's contract excludes them and {@code CrMath} fails loudly on them, so {@code CoreMath} delegates them to it).
 */
final class CoreMathTan {

    private CoreMathTan() {}

    /**
     * C {@code cr_tan} ({@code tan.c:2245-2297}) PLUS THE GUARD (not in the C): returns {@code NaN} (never a valid tangent of a finite argument) when the accurate path's 128-bit
     * result is within the guard's window of a rounding boundary ({@code 86 + K/R} ulps after a fold, {@code 86 + K} when nothing was folded) and the argument is not one of the
     * C's two exceptions; {@link CoreMath} then answers by delegating to {@link CrMath} and counts the event. See the class javadoc.
     */
    static double tan(double x) {
        return run(x, CoreMathSupport.MODE_NORMAL, null, true, CoreMathTanTables.GUARD_K);
    }

    /**
     * {@link #tan(double)} with the guard's factor {@code K} given. TEST SEAM: with a large {@code K} the guard fires on many real folded inputs, so that the production wiring (the
     * guard applied on the production path, fed the FOLDED argument) can be tested against {@link #guardFires} on the folded pair; production calls {@link #tan(double)}.
     */
    static double tanWithK(double x, double k) {
        return run(x, CoreMathSupport.MODE_NORMAL, null, true, k);
    }

    /**
     * The fast path alone for finite {@code x} outside the tiny-argument exit ({@code |x| > 0x1.d12ed0af1a27ep-27}): the correctly rounded
     * result, or {@code NaN} if its error bound cannot decide the rounding. Inside the exit the C answers, and so does this.
     */
    static double fast(double x) {
        return run(x, MODE_FAST, null, false, CoreMathTanTables.GUARD_K);
    }

    /**
     * The accurate path alone ({@code tan_accurate}) even when the fast path would have decided, for {@code |x| > 0x1.d12ed0af1a27ep-27}
     * (the C's comment: "Assume x is a regular number, and |x| > 0x1.d12ed0af1a27ep-27"). Inside the tiny-argument exit the C answers. WITHOUT the guard: this is the
     * C's accurate path as ported, so that the tests can compare it with {@code CrMath} on its own.
     */
    static double accurate(double x) {
        return run(x, MODE_ACCURATE, null, false, CoreMathTanTables.GUARD_K);
    }

    /**
     * The accurate path forced, WITH the guard applied: {@code NaN} where the guard fires, else the rounded result. TEST SEAM for inputs on which the fast path decides, so that
     * production never reaches the guard (for example the real folded {@code tan.wc} entry {@code x = 0x1.92456c9a2452ep282}): {@code CoreMath} given this as its tan port counts and
     * delegates them.
     */
    static double accurateGuarded(double x) {
        return run(x, MODE_ACCURATE, null, true, CoreMathTanTables.GUARD_K);
    }

    /**
     * {@link #accurate} that also reports, in {@code flags[0]}, whether the 128-bit result lay within 86 ulps of a rounding boundary (the condition under which the C
     * consults its {@code exceptions} table) and, if {@code flags} has a second element, in {@code flags[1]} whether the guard fires. WIRING CHECK, NOT A HARD-CASE DETECTOR: the
     * first flag fires on the two exception inputs and on nothing else in any population tested, because a 86-ulp window is about 2^-68 of the spacing of the doubles
     * (expected hits in 7.6 million random inputs: about 2^-45), so a count of zero says nothing about whether a third hard case exists; the second flag is the guard's decision.
     * Diagnostic: the production path never asks; the test census does.
     */
    static double accurateAmbiguous(double x, boolean[] flags) {
        return run(x, MODE_ACCURATE, flags, false, CoreMathTanTables.GUARD_K);
    }

    private static double run(double x, int mode, boolean[] flags, boolean applyGuard, double k) {
        long t = Double.doubleToRawLongBits(x);
        int e = (int) ((t >>> 52) & 0x7ffL);
        if (e == 0x7ff) {                                           // NaN, +Inf and -Inf
            if ((t << 1) == (0x7ffL << 53)) {
                return x - x;                                       // +/-Inf (the C raises invalid and sets errno = EDOM): a NaN
            }
            return x + x;                                           // NaN
        }
        // now x is a regular number
        long ux = t & 0x7fffffffffffffffL;
        if (ux <= CoreMathTanTables.TINY_BITS) {                    // |x| <= 0x1.d12ed0af1a27ep-27: tan(x) rounds to x
            if (x == 0) {
                return x;
            }
            // Taylor expansion of tan(x) is x + x^3/3 around zero; the C sets errno = ERANGE for 0 < |x| < 2^-1022 (dropped)
            return Math.fma(x, CoreMathTanTables.TINY_FMA, x);
        }
        double[] hl = new double[2];
        double err = tanFast(hl, x);
        double h = hl[0];
        double l = hl[1];
        double left = h + (l - err);
        double right = h + (l + err);
        if (left == right) {
            if (mode != MODE_ACCURATE) {
                return left;
            }
        } else if (mode == MODE_FAST) {
            return Double.NaN;
        }
        return tanAccurate(x, flags, applyGuard, k);
    }

    // ------------------------------------------------------------------------------------------
    // Double-double primitives (tan.c:1402-1436). The high word is written inline by the caller in the text
    // of the C expression; the low word is computed here from the same operands.
    // ------------------------------------------------------------------------------------------

    /** Low word of {@code a_mul(&hi, &lo, a, b)} (tan.c:1403-1406), given {@code hi = a * b}. */
    private static double aMulLow(double a, double b, double hi) {
        return Math.fma(a, b, -hi);
    }

    /** Low word of {@code s_mul(&hi, &lo, a, bh, bl)} (tan.c:1410-1415): {@code a * (bh + bl)}, given {@code hi = a * bh}. */
    private static double sMulLow(double a, double bh, double bl, double hi) {
        double lo = aMulLow(a, bh, hi);                              // exact
        return Math.fma(a, bl, lo);
    }

    /** Low word of {@code d_mul(&hi, &lo, ah, al, bh, bl)} (tan.c:1419-1426), given {@code hi = ah * bh}. */
    private static double dMulLow(double ah, double al, double bh, double bl, double hi) {
        double s = aMulLow(ah, bh, hi);
        double t = Math.fma(al, bh, s);
        return Math.fma(ah, bl, t);
    }

    /** C {@code evalPSfast} (tan.c:1443-1455): {@code out = (h, l)} approximates sin2pi(xh + xl); {@code uh + ul} approximates (xh + xl)^2. */
    private static void evalPSfast(double[] out, double xh, double xl, double uh, double ul) {
        double h = CoreMathTanTables.psFast(4);                      // degree 7
        h = Math.fma(h, uh, CoreMathTanTables.psFast(3));            // degree 5
        h = Math.fma(h, uh, CoreMathTanTables.psFast(2));            // degree 3
        double sh = h * uh;                                          // s_mul (h, l, *h, uh, ul)
        double sl = sMulLow(h, uh, ul, sh);
        double p = CoreMathTanTables.psFast(0);                      // fast_two_sum (h, &t, PSfast[0], *h)
        double fh = p + sh;
        double t = CoreMathSupport.fastTwoSumLow(p, sh, fh);
        double fl = sl + (CoreMathTanTables.psFast(1) + t);          // *l += PSfast[1] + t
        double mh = fh * xh;                                         // multiply by xh+xl: d_mul (h, l, *h, *l, xh, xl)
        out[1] = dMulLow(fh, fl, xh, xl, mh);
        out[0] = mh;
    }

    /** C {@code evalPCfast} (tan.c:1462-1472): {@code out = (h, l)} approximates cos2pi(xh + xl); {@code uh + ul} approximates (xh + xl)^2. */
    private static void evalPCfast(double[] out, double uh, double ul) {
        double h = CoreMathTanTables.pcFast(4);                      // degree 6
        h = Math.fma(h, uh, CoreMathTanTables.pcFast(3));            // degree 4
        h = Math.fma(h, uh, CoreMathTanTables.pcFast(2));            // degree 2
        double sh = h * uh;                                          // s_mul (h, l, *h, uh, ul)
        double sl = sMulLow(h, uh, ul, sh);
        double p = CoreMathTanTables.pcFast(0);                      // fast_two_sum (h, &t, PCfast[0], *h)
        double fh = p + sh;
        double t = CoreMathSupport.fastTwoSumLow(p, sh, fh);
        out[0] = fh;
        out[1] = sl + (CoreMathTanTables.pcFast(1) + t);             // *l += PCfast[1] + t
    }

    // ------------------------------------------------------------------------------------------
    // Fast path
    // ------------------------------------------------------------------------------------------

    /** C {@code normalize} (tan.c:1514-1536): shifts {@code X} left so that the top bit of {@code X.hi} is set (if X is non-zero). */
    static void normalize(CoreMathDint x) {
        if (x.hi != 0L) {
            int cnt = Long.numberOfLeadingZeros(x.hi);
            if (cnt != 0) {
                x.hi = (x.hi << cnt) | (x.lo >>> (64 - cnt));
                x.lo = x.lo << cnt;
            }
            x.ex -= cnt;
        } else if (x.lo != 0L) {
            int cnt = Long.numberOfLeadingZeros(x.lo);
            x.hi = x.lo << cnt;
            x.lo = 0L;
            x.ex -= 64 + cnt;
        }
    }

    /**
     * C {@code set_dd} (tan.c:1684-1738): {@code out = (h, l)} <- {@code c1/2^64 + c0/2^128} rounded towards zero, as a double-double
     * (the C's wording; the truncation error is below 2^-106).
     */
    static void setDd(double[] out, long c1, long c0) {
        if (c1 != 0L) {
            long e = Long.numberOfLeadingZeros(c1);
            if (e != 0L) {
                c1 = (c1 << e) | (c0 >>> (64 - e));
                c0 = c0 << e;
            }
            long f = 0x3feL - e;
            out[0] = Double.longBitsToDouble((f << 52) | ((c1 << 1) >>> 12));
            c0 = (c1 << 53) | (c0 >>> 11);
            if (c0 != 0L) {
                long g = Long.numberOfLeadingZeros(c0);
                if (g != 0L) {
                    c0 = c0 << g;
                }
                out[1] = Double.longBitsToDouble(((f - 53L - g) << 52) | ((c0 << 1) >>> 12));
            } else {
                out[1] = 0;
            }
        } else if (c0 != 0L) {
            long e = Long.numberOfLeadingZeros(c0);
            long f = 0x3feL - 64L - e;
            c0 = c0 << (e + 1L);                                     // most significant bit shifted out
            // put the upper 52 bits of c0 into h
            out[0] = Double.longBitsToDouble((f << 52) | (c0 >>> 12));
            // put the lower 12 bits of c0 into l
            c0 = c0 << 52;
            if (c0 != 0L) {
                long g = Long.numberOfLeadingZeros(c0);
                c0 = c0 << (g + 1L);
                out[1] = Double.longBitsToDouble(((f - 64L - g) << 52) | (c0 >>> 12));
            } else {
                out[1] = 0;
            }
        } else {
            out[0] = 0;
            out[1] = 0;
        }
    }

    /**
     * C {@code reduce_fast} (tan.c:1746-1882): for {@code 0x1.7137449123ef6p-26 < x < +Inf} returns {@code i} and sets {@code hl = (h, l)} such
     * that {@code i/2^11 + h + l} approximates {@code frac(x/(2 pi))}, {@code |h| < 2^-11}, {@code |l| < 2^-52.36}, absolute error below 2^-104.815.
     */
    static int reduceFast(double[] hl, double x) {
        double h;
        double l;
        if (x <= CoreMathTanTables.TWO_PI_LIMIT) {                  // x < 2*pi
            h = CoreMathTanTables.CH * x;                            // a_mul (h, l, CH, x): exact
            l = aMulLow(CoreMathTanTables.CH, x, h);
            l = Math.fma(CoreMathTanTables.CL, x, l);
        } else {                                                     // x > 0x1.921fb54442d17p+2
            long tb = Double.doubleToRawLongBits(x);
            int e = (int) ((tb >>> 52) & 0x7ffL);                    // 1025 <= e <= 2046
            long m = (1L << 52) | (tb & 0xfffffffffffffL);
            long c0;
            long c1;
            long c2;
            // x = m/2^53 * 2^(e-1022)
            if (e <= 1074) {                                         // 1025 <= e <= 1074: 2^2 <= x < 2^52
                long t2 = CoreMathTanTables.t(2);
                long t1 = CoreMathTanTables.t(1);
                long t0 = CoreMathTanTables.t(0);
                long vHigh = Math.unsignedMultiplyHigh(m, t2);       // v = m * T[2]; only v >> 64 is used
                long uLow = m * t1;                                  // u = m * T[1]
                long uHigh = Math.unsignedMultiplyHigh(m, t1);
                c0 = uLow + vHigh;                                   // c[0] = u + (v >> 64)
                c1 = uHigh + (CoreMathSupport.ltu(c0, uLow) ? 1L : 0L);
                uLow = m * t0;                                       // u = m * T[0]
                uHigh = Math.unsignedMultiplyHigh(m, t0);
                c1 += uLow;
                c2 = uHigh + (CoreMathSupport.ltu(c1, uLow) ? 1L : 0L);
                e = 1075 - e;                                        // 1 <= e <= 50: the number of low bits of c[2] contributing to frac(x/(2pi))
            } else {                                                 // 1075 <= e <= 2046, 2^52 <= x < 2^1024
                int i = (e - 1138 + 63) / 64;                        // i = ceil((e-1138)/64), 0 <= i <= 15
                long vHigh = Math.unsignedMultiplyHigh(m, CoreMathTanTables.t(i + 3));
                long uLow = m * CoreMathTanTables.t(i + 2);
                long uHigh = Math.unsignedMultiplyHigh(m, CoreMathTanTables.t(i + 2));
                c0 = uLow + vHigh;
                c1 = uHigh + (CoreMathSupport.ltu(c0, uLow) ? 1L : 0L);
                uLow = m * CoreMathTanTables.t(i + 1);
                uHigh = Math.unsignedMultiplyHigh(m, CoreMathTanTables.t(i + 1));
                c1 += uLow;
                c2 = uHigh + (CoreMathSupport.ltu(c1, uLow) ? 1L : 0L);
                c2 += m * CoreMathTanTables.t(i);
                e = 1139 + (i << 6) - e;                             // 1 <= e <= 64: the number of bits of c[2] to ignore
            }
            if (e == 64) {
                c0 = c1;                                             // all bits of c[0] are ignored
                c1 = c2;
            } else {
                c0 = (c1 << (64 - e)) | (c0 >>> e);                  // the low bits of c[0] are ignored
                c1 = (c2 << (64 - e)) | (c1 >>> e);
            }
            double[] dd = new double[2];
            setDd(dd, c1, c0);
            h = dd[0];
            l = dd[1];
        }
        double i = Math.floor(h * 0x1p11);
        h = Math.fma(i, -0x1p-11, h);
        hl[0] = h;
        hl[1] = l;
        return (int) i;
    }

    /** C {@code fast_div} (tan.c:1887-1937): {@code out = (h, l)} approximates {@code (bh + bl) / (ah + al)}, relative error below 2^-96.99. */
    private static void fastDiv(double[] out, double bh, double bl, double ah, double al) {
        // Karp-Markstein's trick for the division
        double y = 1.0 / ah;
        double h = bh * y;
        double eh = Math.fma(ah, -h, bh);
        double el = Math.fma(al, -h, bl);
        out[1] = y * (eh + el);
        out[0] = h;
    }

    /**
     * C {@code tan_fast} (tan.c:1943-2080): for finite {@code |x| > 0x1.d12ed0af1a27ep-27} sets {@code hl = (h, l)} approximating tan(x) and returns
     * the maximal absolute error {@code err} such that {@code |h + l - tan(x)| < err}; or returns 1 (the C: {@code return 0x1p0}) when the reduced
     * argument is too small for the fast path.
     */
    private static double tanFast(double[] hl, double x) {
        int neg = x < 0 ? 1 : 0;
        int isTan = 1;
        double absx = neg != 0 ? -x : x;
        // now absx > 0x1.d12ed0af1a27ep-27
        int i = reduceFast(hl, absx);
        double h = hl[0];
        double l = hl[1];
        // if i >= 2^10: 1/2 <= frac(x/(2pi)) < 1 thus pi <= x <= 2pi: we use tan(pi+x) = tan(x)
        i = i & 0x3ff;
        // if i >= 2^9: pi/2 <= x <= pi: we use tan(pi/2+x) = -cot(x)
        isTan = isTan ^ (i >> 9);
        neg = neg ^ (i >> 9);
        i = i & 0x1ff;
        if ((i & 0x100) != 0) {                                      // case pi/4 <= x_red <= pi/2: tan(pi/2-x) = cot(x)
            isTan = isTan == 0 ? 1 : 0;
            i = 0x1ff - i;
            // 0x1p-11 - h is exact (tan.c:1973-1982)
            h = 0x1p-11 - h;
            l = -l;
        }
        if (i == 0 && h < CoreMathTanTables.H_MIN) {
            hl[0] = h;
            hl[1] = l;
            return CoreMathTanTables.ERR_UNDECIDED;                 // for i=0 and h<2^-37, we defer to the slow path
        }
        // Now 0 <= i < 256 and 0 <= h+l < 2^-11. The SC[] table evaluates at i/2^11 + SC[i][0] and not at i/2^11, so SC[i][0] is subtracted
        // from h+l (exact: tan.c:2009-2012).
        h -= CoreMathTanTables.sc(3 * i);
        // fast_two_sum (h, l, *h, *l): guarantees |l| <= ulp(h)
        double fh = h + l;
        l = CoreMathSupport.fastTwoSumLow(h, l, fh);
        h = fh;
        // now -2^-24 < h < 2^-11+2^-24, |l| < 2^-52.36
        double uh = h * h;                                           // a_mul (&uh, &ul, *h, *h)
        double ul = aMulLow(h, h, uh);
        ul = Math.fma(h + h, l, ul);
        // uh+ul approximates (h+l)^2
        double[] w = new double[2];
        evalPSfast(w, h, l, uh, ul);
        double sh = w[0];
        double sl = w[1];
        evalPCfast(w, uh, ul);
        double ch = w[0];
        double cl = w[1];
        double sc1 = CoreMathTanTables.sc(3 * i + 1);                // SC[i][1] approximates sin2pi(x_i)
        double sc2 = CoreMathTanTables.sc(3 * i + 2);                // SC[i][2] approximates cos2pi(x_i)
        double sh0 = sc2 * sh;                                       // s_mul (&sh0, &sl0, SC[i][2], sh, sl)
        double sl0 = sMulLow(sc2, sh, sl, sh0);
        double ch0 = sc1 * ch;                                       // s_mul (&ch0, &cl0, SC[i][1], ch, cl)
        double cl0 = sMulLow(sc1, ch, cl, ch0);
        double h1 = ch0 + sh0;                                       // fast_two_sum (&h1, &l1, ch0, sh0)
        double l1 = CoreMathSupport.fastTwoSumLow(ch0, sh0, h1);
        l1 += sl0 + cl0;
        // | h1 + l1 - sin2pi (R) | < 2^-67.777 * |sin2pi(R)| with |l1| < 2^-49.47
        double chn = sc2 * ch;                                       // s_mul (&ch, &cl, SC[i][2], ch, cl)
        double cln = sMulLow(sc2, ch, cl, chn);
        double shn = sc1 * sh;                                       // s_mul (&sh, &sl, SC[i][1], sh, sl)
        double sln = sMulLow(sc1, sh, sl, shn);
        double h2 = chn + -shn;                                      // fast_two_sum (&h2, &l2, ch, -sh)
        double l2 = CoreMathSupport.fastTwoSumLow(chn, -shn, h2);
        l2 += cln - sln;
        // | h2 + l2 - cos2pi (R) | < 2^-68.073 * |cos2pi(R)| with |l2| < 2^-49.62
        if (isTan != 0) {
            fastDiv(w, h1, l1, h2, l2);
        } else {
            fastDiv(w, h2, l2, h1, l1);
        }
        double sgn = CoreMathSupport.signMultiplier(neg);
        double rh = w[0] * sgn;
        double rl = w[1] * sgn;
        hl[0] = rh;
        hl[1] = rl;
        return rh * CoreMathTanTables.FAST_ERR;                      // 2^-65.864 < 0x1.1ap-66
    }

    // ------------------------------------------------------------------------------------------
    // Accurate path
    // ------------------------------------------------------------------------------------------

    /**
     * C {@code reduce} (tan.c:1543-1666): approximates {@code X/(2 pi) mod 1} (call it F). The C's stated bound is {@code |Xout - F| < 2^-126.67 * Xout}, RELATIVE TO F: it
     * holds for {@code ex >= 2} and is 2^-126.26 for {@code ex <= 1} (class javadoc); it is not a bound relative to the folded argument the accurate path divides by. {@code X}
     * must be normalised at input and is normalised at output.
     */
    static void reduce(CoreMathDint x) {
        long e = x.ex;
        if (e <= 1) {                                                // |X| < 2
            // multiply by T[0]/2^64 + T[1]/2^128, where |T[0]/2^64 + T[1]/2^128 - 1/(2pi)| < 2^-130.22
            long t1 = CoreMathTanTables.t(1);
            long tiny = x.hi * t1;                                   // u = (u128) X->hi * T[1]; uint64_t tiny = u
            x.lo = Math.unsignedMultiplyHigh(x.hi, t1);              // X->lo = u >> 64
            long t0 = CoreMathTanTables.t(0);
            long uLow = x.hi * t0;                                   // u = (u128) X->hi * T[0]
            long uHigh = Math.unsignedMultiplyHigh(x.hi, t0);
            x.lo += uLow;
            x.hi = uHigh + (CoreMathSupport.ltu(x.lo, uLow) ? 1L : 0L);
            e = x.ex;
            normalize(x);
            e = e - x.ex;
            // put the upper e bits of tiny into X->lo
            if (e != 0L) {
                x.lo |= tiny >>> (64 - e);
            }
            return;
        }
        // now 2 <= e <= 1024
        // Up to 4 consecutive values of T[i] can contribute (only 3 when e is a multiple of 64).
        int i = (e < 127) ? 0 : (int) ((e - 127 + 64 - 1) / 64);     // ceil((e-127)/64), 0 <= i <= 15
        long xh = x.hi;
        long uLow = xh * CoreMathTanTables.t(i + 3);                 // u = (u128) X->hi * T[i+3]; i+3 <= 18
        long c0 = uLow;
        long c1 = Math.unsignedMultiplyHigh(xh, CoreMathTanTables.t(i + 3));
        uLow = xh * CoreMathTanTables.t(i + 2);
        long uHigh = Math.unsignedMultiplyHigh(xh, CoreMathTanTables.t(i + 2));
        c1 += uLow;
        long c2 = uHigh + (CoreMathSupport.ltu(c1, uLow) ? 1L : 0L);
        uLow = xh * CoreMathTanTables.t(i + 1);
        uHigh = Math.unsignedMultiplyHigh(xh, CoreMathTanTables.t(i + 1));
        c2 += uLow;
        long c3 = uHigh + (CoreMathSupport.ltu(c2, uLow) ? 1L : 0L);
        uLow = xh * CoreMathTanTables.t(i);
        uHigh = Math.unsignedMultiplyHigh(xh, CoreMathTanTables.t(i));
        c3 += uLow;
        long c4 = uHigh + (CoreMathSupport.ltu(c3, uLow) ? 1L : 0L);
        // up to here, the ignored part hi*(T[i+4]+T[i+5]+...) can contribute by less than 2^64 in c[0], thus less than 1 in c[1]
        int f = (int) (e - 64L * i);                                 // hi*T[i]/2^128 is multiplied by 2^f
        // {c, 5} = hi*(T[i]+T[i+1]/2^64+T[i+2]/2^128+T[i+3]/2^192): now shift c[0..4] by f bits to the left
        long tiny;
        if (f < 64) {
            x.hi = (c4 << f) | (c3 >>> (64 - f));
            x.lo = (c3 << f) | (c2 >>> (64 - f));
            tiny = (c2 << f) | (c1 >>> (64 - f));
        } else if (f == 64) {
            x.hi = c3;
            x.lo = c2;
            tiny = c1;
        } else {                                                     // 65 <= f <= 127: this case can only occur when e >= 65
            int g = f - 64;                                          // 1 <= g <= 63
            // we compute an extra term
            long u = Math.unsignedMultiplyHigh(xh, CoreMathTanTables.t(i + 4));      // u = (X->hi * T[i+4]) >> 64; i+4 <= 19
            c0 += u;
            boolean carry = CoreMathSupport.ltu(c0, u);
            c1 += carry ? 1L : 0L;
            c2 += (carry && c1 == 0L) ? 1L : 0L;
            c3 += (carry && c1 == 0L && c2 == 0L) ? 1L : 0L;
            x.hi = (c3 << g) | (c2 >>> (64 - g));
            x.lo = (c2 << g) | (c1 >>> (64 - g));
            tiny = (c1 << g) | (c0 >>> (64 - g));
        }
        // the approximation error between X/(2pi) mod 1 and X->hi/2^64 + X->lo/2^128 + tiny/2^192 is less than 2^-191 (absolute)
        x.ex = 0L;
        normalize(x);
        // the worst case (for 2^25 <= x < 2^1024) is X->ex = -61, attained for |x| = 0x1.6ac5b262ca1ffp+851
        if (x.ex < 0L) {                                             // put the upper -ex bits of tiny into low bits of lo
            x.lo |= tiny >>> (64 + x.ex);
        }
    }

    /**
     * C {@code reduce2} (tan.c:1671-1681): given {@code Xin = X} with {@code 0 <= Xin < 1}, returns {@code i} and modifies {@code X} so that
     * {@code Xin = i/2^11 + Xout} with {@code 0 <= Xout < 2^-11}. Exact.
     */
    static int reduce2(CoreMathDint x) {
        if (x.ex <= -11L) {
            return 0;
        }
        int sh = (int) (64L - 11L - x.ex);
        int i = (int) (x.hi >>> sh);
        x.hi = x.hi & ((1L << sh) - 1L);
        normalize(x);
        return i;
    }

    /** C {@code evalPS} (tan.c:1480-1493): {@code Y} approximates sin2pi(X) for {@code 0 <= X < 2^-11}, where {@code X2} approximates X^2. */
    static void evalPS(CoreMathDint y, CoreMathDint x, CoreMathDint x2) {
        CoreMathDint.mul21(y, x2, CoreMathTanTables.ps(5));                  // degree 11
        CoreMathDint.add(y, y, CoreMathTanTables.ps(4));             // degree 9
        CoreMathDint.mul(y, y, x2);
        CoreMathDint.add(y, y, CoreMathTanTables.ps(3));             // degree 7
        CoreMathDint.mul(y, y, x2);
        CoreMathDint.add(y, y, CoreMathTanTables.ps(2));             // degree 5
        CoreMathDint.mul(y, y, x2);
        CoreMathDint.add(y, y, CoreMathTanTables.ps(1));             // degree 3
        CoreMathDint.mul(y, y, x2);
        CoreMathDint.add(y, y, CoreMathTanTables.ps(0));             // degree 1
        CoreMathDint.mul(y, y, x);                                   // multiply by X
    }

    /** C {@code evalPC} (tan.c:1500-1512): {@code Y} approximates cos2pi(X) for {@code 0 <= X < 2^-11}, where {@code X2} approximates X^2. */
    static void evalPC(CoreMathDint y, CoreMathDint x2) {
        CoreMathDint.mul21(y, x2, CoreMathTanTables.pc(5));                  // degree 10
        CoreMathDint.add(y, y, CoreMathTanTables.pc(4));             // degree 8
        CoreMathDint.mul(y, y, x2);
        CoreMathDint.add(y, y, CoreMathTanTables.pc(3));             // degree 6
        CoreMathDint.mul(y, y, x2);
        CoreMathDint.add(y, y, CoreMathTanTables.pc(2));             // degree 4
        CoreMathDint.mul(y, y, x2);
        CoreMathDint.add(y, y, CoreMathTanTables.pc(1));             // degree 2
        CoreMathDint.mul(y, y, x2);
        CoreMathDint.add(y, y, CoreMathTanTables.pc(0));             // degree 0
    }

    /**
     * The folds of {@code tan_accurate} (tan.c:2086-2123): reduces {@code |x|} to {@code frac(|x|/(2 pi))} ({@code reduce}), splits the grid index off ({@code reduce2}) and
     * folds by tan(pi + x) = tan(x), tan(pi/2 + x) = -cot(x), tan(pi/2 - x) = cot(x). Returns the grid index {@code i} (0..255) and leaves in {@code xd} the remainder
     * {@code X} (0 &lt;= X &lt; 2^-11), so that the FOLDED ARGUMENT, in turns, is {@code R = i/2^11 + X} (0 &lt;= R &lt; 1/8: the distance of frac(x/(2 pi)) from the nearest
     * multiple of a quarter turn); {@code st[0]} is {@code is_tan} (1 for tan, 0 for cot), {@code st[1]} the sign flag {@code neg} and {@code st[2]} (so {@code st} has three
     * elements) is 1 if a fold happened, that is if the grid index {@code reduce2} returned was 256 or more (frac(x/(2 pi)) of 1/8 or more, so that {@code R < F} in general and F/R
     * is not 1), 0 if nothing was folded (F below 1/8, {@code R = F}).
     */
    static int fold(double x, CoreMathDint xd, int[] st) {
        double absx = (x > 0) ? x : -x;
        CoreMathDint.fromD(xd, absx);
        // reduce argument
        reduce(xd);
        // The C's comment here: "now |X - x/(2pi) mod 1| < 2^-126.67*X, with 0 <= X < 1" (relative to X = frac(x/(2 pi)); see the class javadoc for ex <= 1)
        int isTan = 1;
        int neg = x < 0 ? 1 : 0;
        // Write X = i/2^11 + r with 0 <= r < 2^11
        int i = reduce2(xd);                                         // exact
        st[2] = i >= 256 ? 1 : 0;                                    // F = frac(x/(2 pi)) >= 1/8: some fold below subtracts, R <= F (NOT IN THE C: the guard reads it)
        i = i & 0x3ff;                                               // if pi <= x < 2*pi, tan(x) = tan(x-pi)
        if ((i & 0x200) != 0) {                                      // pi/2 <= x < pi: tan(x) = -1/tan(x-pi/2)
            isTan = 0;
            neg = neg == 0 ? 1 : 0;
            i = i & 0x1ff;
        }
        // now 0 <= i < 2^9
        if ((i & 0x100) != 0) {                                      // pi/4 <= x < pi/2: tan(x) = 1/tan(pi/2-x)
            isTan = isTan == 0 ? 1 : 0;
            xd.sgn = 1L;                                             // negate X
            CoreMathDint.add(xd, CoreMathTanTables.dintMagic(), xd); // X -> 2^-11 - X
            i = 0x1ff - i;                                           // 256 <= i <= 511 becomes 0 <= i < 256
        }
        st[0] = isTan;
        st[1] = neg;
        return i;
    }

    /**
     * tan.c:2125-2210: the 128-bit quotient {@code S/C} (or {@code C/S}) of the sine and cosine of the folded argument {@code R = i/2^11 + X}, a positive dint {@code U}
     * (the C's {@code U}). The C's comments at this place claim a relative error below 2^-121.578 ("less than 86 ulps"); THAT CLAIM IS THE C'S AND IS FALSE AFTER A FOLD (the
     * error of {@code reduce} is bounded relative to frac(x/(2 pi)), the C divides it by the smaller R): see the class javadoc.
     */
    private static CoreMathDint accurateQuotient(CoreMathDint xd, int i, boolean isTan) {
        // now 0 <= i < 256 and 0 <= X < 2^-11: if is_tan=1, tan |x| = tan2pi (R * (1 + eps)), else cot2pi; R = i/2^11 + X;
        // the C says |eps| < 2^-126.67 (the C's claim, relative to frac(x/(2 pi)) rather than to R: false for R below frac)
        CoreMathDint u = new CoreMathDint();
        CoreMathDint v = new CoreMathDint();
        CoreMathDint x2 = new CoreMathDint();
        CoreMathDint.mul(x2, xd, xd);                                // X2 approximates X^2
        evalPC(u, x2);                                               // cos2pi(X), 0.999 < U <= 1
        evalPS(v, xd, x2);                                           // sin2pi(X), 0 <= V < 0.0005
        // sin2pi(R) ~ sin2pi(i/2^11)*cos2pi(X)+cos2pi(i/2^11)*sin2pi(X)
        CoreMathDint sin = new CoreMathDint();
        CoreMathDint uu = new CoreMathDint();
        CoreMathDint vv = new CoreMathDint();
        CoreMathDint.mul(uu, CoreMathTanTables.s(i), u);
        CoreMathDint.mul(vv, CoreMathTanTables.c(i), v);
        CoreMathDint.add(sin, uu, vv);
        // cos2pi(R) ~ cos2pi(i/2^11)*cos2pi(X)-sin2pi(i/2^11)*sin2pi(X)
        CoreMathDint cos = new CoreMathDint();
        CoreMathDint.mul(u, CoreMathTanTables.c(i), u);
        CoreMathDint.mul(v, CoreMathTanTables.s(i), v);
        v.sgn = 1L - v.sgn;                                          // negate V
        CoreMathDint.add(cos, u, v);
        // Now if is_tan=1 we compute S/C, otherwise we compute C/S.
        if (isTan) {
            CoreMathDint.div(u, sin, cos);
        } else {
            CoreMathDint.div(u, cos, sin);
        }
        return u;
    }

    /**
     * The 128-bit magnitude {@code U} that {@code tan_accurate} rounds, for a test (the sign is not applied): returns {@code U}, leaves the remainder {@code X} in
     * {@code xdOut} and the grid index in {@code iOut[0]}, so the folded argument is {@code R = iOut[0]/2^11 + X}; if {@code iOut} has a second element it receives the fold flag
     * ({@code fold}'s {@code st[2]}: 1 if a fold happened). Diagnostic seam of the guard tests; production does not call it.
     */
    static CoreMathDint accurateU(double x, CoreMathDint xdOut, int[] iOut) {
        int[] st = new int[3];
        int i = fold(x, xdOut, st);
        iOut[0] = i;
        if (iOut.length > 1) {
            iOut[1] = st[2];
        }
        CoreMathDint copy = new CoreMathDint(xdOut.hi, xdOut.lo, xdOut.ex, xdOut.sgn);
        return accurateQuotient(copy, i, st[0] != 0);
    }

    /**
     * The C's 86-ulp test (tan.c:2212-2219): true if the upper 54 bits of {@code U - 86} and {@code U + 86} (ulps of the 128-bit mantissa) differ, i.e. {@code U} is within 86
     * ulps of a multiple of 2^74 (a rounding boundary or midpoint of the 53-bit result).
     */
    static boolean c86TestFires(CoreMathDint u) {
        long err = CoreMathTanTables.ACCURATE_ERR;
        long lo0 = u.lo - err;
        long hi0 = u.hi - (CoreMathSupport.ltu(u.lo, lo0) ? 1L : 0L);
        long lo1 = u.lo + err;
        long hi1 = u.hi + (CoreMathSupport.ltu(lo1, u.lo) ? 1L : 0L);
        // check the upper 54 bits are equal
        return (hi0 >>> 10) != (hi1 >>> 10);
    }

    /**
     * THE GUARD (not in the C; operator ruling 2026-11-12 after the phase B2 reviews). True if the 128-bit result {@code u} is within the window of a multiple of 2^74 (a rounding
     * boundary or midpoint of the 53-bit result): {@code 86 + K/R} ulps (2^-128 of the mantissa) if a fold happened, {@code 86 + K} if nothing was folded, with {@code K = }
     * {@link CoreMathTanTables#GUARD_K} and {@code R = i/2^11 + x} the folded argument in turns (operator ruling 2026-11-12, "Narrow it": the derivation of K gives {@code K F/R},
     * which is {@code K} when nothing is folded, {@code F = R}). The caller then returns "undecided" and {@code CoreMath} answers by delegating to {@code CrMath}. It is the C's own
     * test with the window widened by the term the C omits (see the class javadoc for the derivation). The comparison is CLOSED: some multiple of 2^74 in {@code [u - w, u + w]},
     * {@code w = 86 + floor(K/R) + 1} (or {@code 86 + floor(K) + 1}), so the reach is {@code w} on both sides (the C's own 86-ulp test is half-open). If the folded {@code R} is zero,
     * or the window reaches 2^74 (the spacing of the boundaries: it would always straddle one), the answer is true.
     *
     * @param u the 128-bit quotient (positive, normalised)
     * @param i the grid index after the folds (0..255)
     * @param x the remainder X after the folds (0 &lt;= X &lt; 2^-11)
     * @param folded true if a fold happened ({@code fold}'s {@code st[2]}: frac(x/(2 pi)) of 1/8 or more)
     */
    static boolean guardFires(CoreMathDint u, int i, CoreMathDint x, boolean folded) {
        return guardFires(u, i, x, folded, CoreMathTanTables.GUARD_K);
    }

    /** {@link #guardFires(CoreMathDint, int, CoreMathDint, boolean)} with the factor {@code K} given (test seam, see {@link #tanWithK}). */
    static boolean guardFires(CoreMathDint u, int i, CoreMathDint x, boolean folded, double kFactor) {
        CoreMathDint q = new CoreMathDint();
        CoreMathDint k = new CoreMathDint();
        CoreMathDint.fromD(k, kFactor);
        if (folded) {
            CoreMathDint r = new CoreMathDint();
            if (i == 0) {
                if (x.hi == 0L) {
                    return true;                                     // R = 0: no finite input reaches it, but never answer from a division by zero
                }
                CoreMathDint.copy(r, x);
            } else {
                CoreMathDint ri = new CoreMathDint();
                CoreMathDint.fromD(ri, i * 0x1p-11);                 // exact
                if (x.hi == 0L) {
                    CoreMathDint.copy(r, ri);
                } else {
                    CoreMathDint.add(r, ri, x);                      // R = i/2^11 + X, relative error 2^-127
                }
            }
            CoreMathDint.div(q, k, r);                               // K/R, relative error below 2^-123.67
        } else {
            if (i == 0 && x.hi == 0L) {
                return true;                                         // R = F = 0: never answer from a zero argument
            }
            CoreMathDint.copy(q, k);                                 // nothing folded: F = R, K F/R = K
        }
        if (q.ex >= 75) {
            return true;                                             // the window reaches 2^74, the spacing of the boundaries
        }
        int shift = (int) (128L - q.ex);                             // window term = mantissa * 2^-shift; shift <= 124 for K = 8
        U128 floorQ = shift >= 128 ? U128.of(0L, 0L) : U128.of(q.hi, q.lo).shr(shift);
        U128 window = floorQ.add(U128.ofU64(CoreMathTanTables.ACCURATE_ERR + 1L));   // 86 + floor(window term) + 1 >= 86 + window term
        U128 uv = U128.of(u.hi, u.lo);
        U128 low = uv.sub(window).sub(U128.ofU64(1L));               // U >= 2^127 and window < 2^75: no wrap. CLOSED interval [U - w, U + w]: floor((U - w - 1)/2^74) != floor((U + w)/2^74)
        U128 high = uv.add(window);
        if (high.ltU(uv)) {
            return true;                                             // U + window carries out of 128 bits: straddles 2^128
        }
        return !low.shr(74).equals(high.shr(74));
    }

    /**
     * C {@code tan_accurate} (tan.c:2083-2243), plus the guard. {@code flags}, if not null, receives in element 0 whether the C's 86-ulp test fired and, if it has a second
     * element, in element 1 whether the guard fired. When the guard fires and {@code applyGuard} is true the result is {@code NaN} (never a valid tangent of a finite
     * argument): "undecided". {@code k} is the guard's factor K ({@link CoreMathTanTables#GUARD_K} in production).
     */
    private static double tanAccurate(double x, boolean[] flags, boolean applyGuard, double k) {
        CoreMathDint xd = new CoreMathDint();
        int[] st = new int[3];
        int i = fold(x, xd, st);
        int neg = st[1];
        CoreMathDint u = accurateQuotient(xd, i, st[0] != 0);
        // total relative error: the C says < 2^-121.578, less than 86 ulps (the C's claim; false after a fold, see the class javadoc)
        if (c86TestFires(u)) {
            if (flags != null) {
                flags[0] = true;
            }
            for (int j = 0; j < 2; j++) {
                if (Math.abs(x) == CoreMathTanTables.exceptions(3 * j)) {
                    double yh = CoreMathTanTables.exceptions(3 * j + 1);
                    double yl = CoreMathTanTables.exceptions(3 * j + 2);
                    return (x > 0) ? yh + yl : -yh - yl;
                }
            }
            // if we go here, we have a hard-to-round case, but since all hard-to-round cases are known and pass all tests, we are ok (the C's argument; see the guard below)
        }
        // NOT IN THE C: the guard. The C's window of 86 ulps is not the error of the accurate path after a fold; the guard widens it by K/R after a fold, by K otherwise.
        if (guardFires(u, i, xd, st[2] != 0, k)) {
            if (flags != null && flags.length > 1) {
                flags[1] = true;
            }
            if (applyGuard) {
                return Double.NaN;
            }
        }
        if (neg != 0) {
            u.sgn = 1L - u.sgn;
        }
        return CoreMathDint.toD(u);
    }
}
