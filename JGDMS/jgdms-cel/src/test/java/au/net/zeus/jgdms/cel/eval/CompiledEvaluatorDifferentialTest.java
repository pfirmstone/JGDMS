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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static au.net.zeus.jgdms.cel.testsupport.Msg.msg;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds {@link CompiledExpression} to {@link Evaluator} over a seeded random corpus: identical outcomes
 * (doubles by raw bits, lists by element type and element class, field-sourced values by instance), identical
 * exceptions, and the identical calls on the projection and the math provider (see {@link DifferentialHarness}).
 * <p>
 * <b>What it cannot see.</b> The rules in {@code CelSemantics} are one source for both evaluators, so a fault
 * there is invisible here; {@code NumericEdgeCasesTest}, {@code NaNSignedZeroTest},
 * {@code StringOrderingLazinessTest} and the conformance corpus cover them. And a pass is only as good as what
 * the corpus reached, which is why each run prints its census and fails if a kind, a function id or an outcome
 * class goes unreached (a generator that quietly degenerates must not turn this test into a rubber stamp).
 * <p>
 * Runtime tier: the default test is fast (20,000 cases); {@link #oneMillionCases} is {@code slow}.
 */
class CompiledEvaluatorDifferentialTest {

    /** Fixed, so a failure names a case that reproduces. Change it only to widen coverage, and say so. */
    static final long SEED = 0x5EED0001L;

    private static DifferentialHarness.Census run(long seed, int cases, boolean referenceToo) {
        DifferentialHarness.Census census = new DifferentialHarness.Census();
        for (int i = 0; i < cases; i++) DifferentialHarness.runCase(seed, i, referenceToo, census);
        return census;
    }

    /** Fails if the corpus did not reach what this test claims to cover. */
    static void assertCoverage(DifferentialHarness.Census census, int cases) {
        int minimum = Math.max(1, cases / 500); // every kind in at least 0.2% of the ASTs
        List<String> missing = new ArrayList<>();
        Set<String> required = DifferentialHarness.requiredKinds();
        for (String kind : required) {
            if (census.astsWithKind.getOrDefault(kind, 0) < minimum) missing.add(kind);
        }
        assertTrue(missing.isEmpty(), msg("kinds the corpus did not reach in at least ", minimum, " of ", cases,
                " ASTs: ", missing));

        List<String> unreached = new ArrayList<>();
        int outcomeMinimum = Math.max(1, cases / 1000);
        for (String mode : new String[] {"installed", "none"}) {
            for (String t : new String[] {"BOOL", "INT", "DOUBLE", "STRING", "BYTES", "NULL_T", "LIST", "OBJECT"}) {
                if (census.rootOutcomes.getOrDefault(msg(mode, " VALUE ", t), 0) < outcomeMinimum) {
                    unreached.add(msg(mode, " VALUE ", t));
                }
            }
            for (String e : new String[] {"TYPE_MISMATCH", "OVERFLOW", "DIVISION_BY_ZERO", "ABSENT_FIELD",
                "AMBIGUOUS_FIELD", "DOMAIN", "CANDIDATE_UNDECODABLE"}) {
                if (census.rootOutcomes.getOrDefault(msg(mode, " ERROR ", e), 0) < outcomeMinimum) {
                    unreached.add(msg(mode, " ERROR ", e));
                }
            }
        }
        if (census.rootOutcomes.getOrDefault("none THROWN IllegalStateException", 0) < outcomeMinimum) {
            unreached.add("none THROWN IllegalStateException (a transcendental with no provider)");
        }
        if (census.rootOutcomes.getOrDefault("installed THROWN IllegalStateException", 0) < outcomeMinimum) {
            unreached.add("installed THROWN IllegalStateException (the poisoned field)");
        }
        assertTrue(unreached.isEmpty(), msg("outcome classes the corpus did not reach at least ", outcomeMinimum,
                " times: ", unreached));
        assertTrue(census.astsWithSharedReference >= minimum, "no reference-shared DAGs were generated");
        assertTrue(census.astsWithMergedDuplicates >= minimum, "no structural duplicates were merged");
        assertTrue(census.memoHits > 0, "no memo was ever hit: sharing was never exercised");
        assertTrue(census.traceEventsCompared > 0, "no call trace was compared");
    }

    @Test
    void compiledMatchesWalker_20000Cases() {
        int cases = 20_000;
        long start = System.nanoTime();
        DifferentialHarness.Census census = run(SEED, cases, true);
        long millis = (System.nanoTime() - start) / 1_000_000L;
        census.print(System.out);
        System.out.println(msg("differential 20,000 cases: ", millis, " ms wall"));
        assertCoverage(census, cases);
    }

    /** The widest run, for scoring a build and for the mutant pass; excluded from the default suite by the {@code slow} tag. */
    @Test
    @Tag("slow")
    void oneMillionCases() {
        int cases = 1_000_000;
        long start = System.nanoTime();
        DifferentialHarness.Census census = run(SEED + 1, cases, true);
        long millis = (System.nanoTime() - start) / 1_000_000L;
        census.print(System.out);
        System.out.println(msg("differential 1,000,000 cases: ", millis, " ms wall"));
        assertCoverage(census, cases);
    }
}
