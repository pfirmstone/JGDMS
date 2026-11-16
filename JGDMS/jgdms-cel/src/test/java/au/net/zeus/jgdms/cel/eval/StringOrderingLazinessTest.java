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

import au.net.zeus.jgdms.cel.CelValue;
import au.net.zeus.jgdms.cel.EvalOutcome;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.math.MathProvider;
import au.net.zeus.jgdms.cel.testsupport.FakeCandidateProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B3 §4.4 lock-hold fix: proves the {@link Evaluator}'s string ordering
 * comparison is now lazy/{@code O(min)} yet semantically identical to the
 * former eager {@code codePoints().toArray()} walk (§4.4 lexicographic Unicode
 * code-point order, NOT {@code String#compareTo}/UTF-16 order), and that {@code
 * size(string)} counts a given field's code points at most once per candidate.
 */
class StringOrderingLazinessTest {

    /** The tests are written once; the compiled-evaluator subclass overrides this to run them again. */
    EvaluatorUnderTest.Factory evaluators() {
        return EvaluatorUnderTest.WALKER;
    }

    private EvaluatorUnderTest evaluator;

    @BeforeEach
    void createEvaluator() {
        evaluator = evaluators().create(MathProvider.nonConformantForTestingOnly());
    }
    private static final CandidateProjection EMPTY = FakeCandidateProjection.builder().build();

    // ---- reference (the ORIGINAL eager algorithm, kept here as the oracle) ----------

    /** The pre-fix implementation, verbatim: the oracle the lazy version must match sign-for-sign. */
    private static int referenceCompareCodePoints(String a, String b) {
        int[] ca = a.codePoints().toArray();
        int[] cb = b.codePoints().toArray();
        int n = Math.min(ca.length, cb.length);
        for (int i = 0; i < n; i++) {
            if (ca[i] != cb[i]) return Integer.compare(ca[i], cb[i]);
        }
        return Integer.compare(ca.length, cb.length);
    }

    /** Drives the real (lazy) comparator through the Evaluator's ordering ops, returning -1/0/+1. */
    private int evalCompareSign(String a, String b) {
        ExprNode la = new ExprNode.LitString(a);
        ExprNode lb = new ExprNode.LitString(b);
        boolean lt = boolOf(evaluator.evaluate(new ExprNode.Lt(la, lb), EMPTY));
        boolean gt = boolOf(evaluator.evaluate(new ExprNode.Gt(la, lb), EMPTY));
        // Cross-check the non-strict forms are internally consistent with lt/gt.
        boolean le = boolOf(evaluator.evaluate(new ExprNode.Le(la, lb), EMPTY));
        boolean ge = boolOf(evaluator.evaluate(new ExprNode.Ge(la, lb), EMPTY));
        assertEquals(lt || !gt, le, "le must equal (lt or equal)");
        assertEquals(gt || !lt, ge, "ge must equal (gt or equal)");
        assertFalse(lt && gt, "lt and gt cannot both hold");
        if (lt) return -1;
        if (gt) return 1;
        return 0;
    }

    private static boolean boolOf(EvalOutcome outcome) {
        return ((CelValue.BoolV) ((EvalOutcome.Value) outcome).value()).value();
    }

    // ---- semantic equivalence table -------------------------------------------------

    @Test
    void lazyComparison_matchesEagerOracle_acrossTheTable() {
        String astral0 = new String(Character.toChars(0x1F600));  // U+1F600 GRINNING FACE (astral)
        String astral1 = new String(Character.toChars(0x1F601));  // U+1F601
        String[][] pairs = {
                {"", ""},                       // equal empty
                {"", "a"},                      // empty vs non-empty
                {"a", ""},
                {"abc", "abc"},                 // equal content
                {"abc", "abd"},                 // common prefix then differ
                {"abd", "abc"},
                {"ab", "abc"},                  // prefix vs longer
                {"abc", "ab"},
                {"café", "cafe"},          // multi-byte UTF-8: U+00E9 vs 'e', common prefix "caf"
                {"cafe", "café"},
                {"naïve", "naive"},        // U+00EF vs 'i'
                {astral0, "a"},                 // astral vs BMP (code point 128512 vs 97)
                {"a", astral0},
                {astral0, astral0},             // equal surrogate pairs
                {astral0, astral1},             // two distinct astral code points, share leading surrogate
                {astral1, astral0},
                {astral0, "�"},            // astral vs high-BMP: code-point order != UTF-16 order (see dedicated test)
                {new StringBuilder("a").append(astral0).append("b").toString(),
                        new StringBuilder("a").append(astral0).append("c").toString()}, // differ AFTER an astral char
                {"z", astral0},                 // 'z'(122) < astral(128512): NOT UTF-16 order
        };
        for (String[] p : pairs) {
            int expected = Integer.signum(referenceCompareCodePoints(p[0], p[1]));
            int actual = evalCompareSign(p[0], p[1]);
            assertEquals(expected, actual,
                    () -> new StringBuilder("code-point order mismatch for [").append(p[0]).append("] vs [")
                            .append(p[1]).append("]").toString());
        }
    }

    /**
     * The load-bearing regression guard: an astral code point (U+1F600 = 128512)
     * sorts ABOVE a high BMP scalar (U+FFFD = 65533) in code-point order, but its
     * leading surrogate (0xD83D = 55357) sorts BELOW U+FFFD in UTF-16 order. A
     * regression to {@code String#compareTo} would flip the sign; this pins the
     * §4.4 semantics.
     */
    @Test
    void codePointOrder_differsFrom_utf16CompareTo() {
        String astral = new String(Character.toChars(0x1F600));
        String bmp = "�";
        assertTrue(astral.compareTo(bmp) < 0, "sanity: UTF-16 compareTo puts the surrogate-lead string first");
        assertEquals(1, evalCompareSign(astral, bmp), "code-point order must put the astral char LAST");
        assertEquals(-1, evalCompareSign(bmp, astral));
    }

    // ---- laziness / boundedness ----------------------------------------------------

    /**
     * A very large string compared against one differing at code point 0 returns
     * correctly. The lazy comparator returns at the first differing code point
     * ({@code O(1)} here) and never materialises either operand; the former eager
     * version allocated an {@code int[]} the full length of each operand. Kept a
     * correctness/bounded test (no wall-clock assertion) per the B3 SOW.
     */
    @Test
    void largeStrings_differAtFirstCodePoint_correctAndBounded() {
        int n = 800_000;
        String base = "a".repeat(n);
        String differsAtZero = new StringBuilder("b").append("a".repeat(n - 1)).toString(); // differs only at index 0
        assertEquals(-1, evalCompareSign(base, differsAtZero), "'a...' < 'b...'");
        assertEquals(1, evalCompareSign(differsAtZero, base));

        // Astral operands differing at the very first code point exercise the
        // surrogate-advance path on large input without full materialisation.
        String astralBig = new String(Character.toChars(0x1F600)).repeat(n / 2);
        String astralBigHigher = new StringBuilder(new String(Character.toChars(0x1F601)))
                .append(new String(Character.toChars(0x1F600)).repeat(n / 2 - 1)).toString();
        assertEquals(-1, evalCompareSign(astralBig, astralBigHigher));
        assertEquals(1, evalCompareSign(astralBigHigher, astralBig));
    }

    /**
     * A large string equal to a longer string up to its own length exercises the
     * "shorter operand exhausted first sorts earlier" branch on big input.
     */
    @Test
    void largeStrings_prefixOfLonger_ordersShorterFirst() {
        int n = 500_000;
        String shorter = "a".repeat(n);
        String longer = new StringBuilder(shorter).append("a").toString(); // identical prefix, longer by one code point
        assertEquals(-1, evalCompareSign(shorter, longer));
        assertEquals(1, evalCompareSign(longer, shorter));
        assertEquals(0, evalCompareSign(shorter, shorter));
    }

    // ---- size(string) compute-once -------------------------------------------------

    private static ExprNode sizeOfField(String fieldName) {
        return new ExprNode.Call(1, List.of( // function id 1 = size(string)
                new ExprNode.FieldRef(List.of(new ExprNode.SelectorStep.Unqual(fieldName)))));
    }

    @Test
    void sizeString_overSameField_computesCodePointCountOnce() {
        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("com.example.X", "s", new CelValue.StringV("hello")) // 5 code points
                .build();
        // Two independent occurrences of size(s) in one predicate: size(s) == size(s).
        ExprNode predicate = new ExprNode.Eq(sizeOfField("s"), sizeOfField("s"));

        EvalOutcome outcome = evaluator.evaluate(predicate, candidate);

        assertTrue(boolOf(outcome), "size(s) == size(s) is true");
        assertEquals(1, evaluator.sizeStringComputations(),
                "code-point count must be computed once per candidate field, not once per size() occurrence");
    }

    @Test
    void sizeString_distinctFields_countedIndependently() {
        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("com.example.X", "s", new CelValue.StringV("hello"))   // 5
                .field("com.example.X", "t", new CelValue.StringV("hi"))       // 2
                .build();
        // size(s) + size(t) + size(s): field s referenced twice, field t once.
        ExprNode predicate = new ExprNode.Eq(
                new ExprNode.Add(new ExprNode.Add(sizeOfField("s"), sizeOfField("t")), sizeOfField("s")),
                new ExprNode.LitInt(12)); // 5 + 2 + 5

        EvalOutcome outcome = evaluator.evaluate(predicate, candidate);

        assertTrue(boolOf(outcome));
        assertEquals(2, evaluator.sizeStringComputations(),
                "two distinct field strings => exactly two O(len) scans, despite three size() occurrences");
    }

    /**
     * Two different SPELLINGS of one field ({@code s} and {@code com.example.X.s}) are different AST nodes, so
     * structural merging cannot join them; only the identity memo, which keys on the String the projection
     * returns for the same query, can. The same-spelling tests above are satisfied by merging alone (design
     * section 9 item 3) and cannot tell whether the identity memo works.
     */
    @Test
    void sizeString_twoSpellingsOfOneField_scanOnce() {
        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("com.example.X", "s", new CelValue.StringV("hello"))
                .build();
        ExprNode qualified = new ExprNode.Call(1, List.of(new ExprNode.FieldRef(
                List.of(new ExprNode.SelectorStep.Qual("com.example.X", "s")))));
        ExprNode predicate = new ExprNode.Eq(sizeOfField("s"), qualified);

        EvalOutcome outcome = evaluator.evaluate(predicate, candidate);

        assertTrue(boolOf(outcome), "size(s) == size(com.example.X.s) is true: both are the same field");
        assertEquals(1, evaluator.sizeStringComputations(),
                "one String instance reached through two spellings must be scanned once");
    }

    /**
     * The identity memo is keyed on the String instance, not its content: two fields holding equal content in
     * different instances are two scans. Guards against a memo that quietly became a content map.
     */
    @Test
    void sizeString_equalContentDistinctInstances_scanTwice() {
        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("com.example.X", "s", new CelValue.StringV(new String("hello".toCharArray())))
                .field("com.example.X", "t", new CelValue.StringV(new String("hello".toCharArray())))
                .build();

        EvalOutcome outcome = evaluator.evaluate(new ExprNode.Eq(sizeOfField("s"), sizeOfField("t")), candidate);

        assertTrue(boolOf(outcome));
        assertEquals(2, evaluator.sizeStringComputations(),
                "equal content in distinct String instances is counted independently, as the walker does");
    }

    @Test
    void sizeString_valueUnchanged_isCodePointCount() {
        // Guards that memoization did not alter the returned value: still code points, not UTF-16 units.
        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("com.example.X", "s", new CelValue.StringV(
                        new StringBuilder(new String(Character.toChars(0x1F600))).append("ab").toString()))
                .build();
        EvalOutcome outcome = evaluator.evaluate(
                new ExprNode.Eq(sizeOfField("s"), new ExprNode.LitInt(3)), candidate); // 1 astral + 2 = 3 code points
        assertTrue(boolOf(outcome), "size of one astral char + 'ab' is 3 code points (not 4 UTF-16 units)");
    }
}
