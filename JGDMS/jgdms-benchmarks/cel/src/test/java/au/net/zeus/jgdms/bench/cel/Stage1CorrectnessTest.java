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

package au.net.zeus.jgdms.bench.cel;

import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.authoring.CelParseException;
import au.net.zeus.jgdms.cel.authoring.CelTextParser;
import au.net.zeus.jgdms.cel.math.MathProvider;
import au.net.zeus.jgdms.cel.math.MathProviders;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * STD-011 stage-1 correctness: the CEL trees the benchmarks time must compute the SAME bits as native code doing the same
 * arithmetic, on the real scan, or the timings compare unlike things. RUNTIME TIER: slow-ish (reads a 466 MB scan, CRC
 * checks every page, runs a correctly-rounded evaluator over 100,000 records); it is in the module's default run because the
 * brief asks for it to run with the build, and it reads its input from {@link Sampling#rawscanPath()} (a READ-ONLY file; the
 * test FAILS, it does not skip, when the file is absent).
 *
 * <h2>SEALED PREDICTIONS (written and committed BEFORE the first run of this class; 2026-11-11, producer seat Sonnet)</h2>
 * Witness: the dispatcher (Chisel) reads these in the commit that introduces this file; no mailbox was used (the brief forbids
 * it). Scoring path: {@code mvn -f jgdms-benchmarks/pom.xml -pl cel test} (the only path that scores them); the scratch
 * programs run before this seal (a de-paging XML dump and a rough timing loop of the correctly-rounded functions) were
 * EXPLORATORY and are not scored. Pinned statistic is stated per prediction; no tolerance is ever widened to pass.
 * <ol>
 *   <li>P1 (AC1) every page of the file CRC32C-verifies, big-endian trailer: CRC failures on page 0, on the record pages and
 *       on the later pages are each 0. ELSE: print the counts and fail; the fixture then refuses to time.</li>
 *   <li>P2 (AC2) stored azimuth in [-(float)pi, (float)pi], elevation in [-(float)(pi/2), (float)(pi/2)], range not negative
 *       or NaN: out-of-bound counts are 0 (the writer enforces this convention). ELSE: the count is printed with its
 *       denominator and booked as a finding about the FILE; the assertion then becomes "under 0.1% of records" and the
 *       change is DECLARED in the READY file, the 0 prediction standing as sealed and failed.</li>
 *   <li>P3 the record count of {@code points/@recordCount} equals {@code geo:conversionPointsWritten}.</li>
 *   <li>P4 (partial independent witness for the angle convention) E1's Cartesian bounds over the valid returns agree with the
 *       file XML's own {@code cartesianBounds} within 0.05 m on each of the six bounds; EXPECTED within 2 mm (the EDM zero-point
 *       and scale if the XML bounds predate the correction). A gross failure (over 0.05 m) fails the test and leaves E1 and E2
 *       unvalidated for convention. SCOPE: the file's own bounds, same bed, a second statement by the converter; it does not
 *       establish independence from the converter's own spherical convention.</li>
 *   <li>P5 CEL E1 (correctly-rounded provider) is bit-identical to B-CR at the three named in-front records and over 100,000
 *       records spread evenly over the whole file: 0 bit mismatches of 300,000 values. ELSE: print the count, the first
 *       mismatching record and both values; fail.</li>
 *   <li>P6 CEL E2 (correctly-rounded provider) is bit-identical to B-CR at the three named in-front records and a further 9
 *       evenly spread in-front records (12 in all: each is about a hundred and fifty thousand correctly-rounded calls, so the
 *       100,000-record sample the brief asked for is infeasible for E2, a DECLARED deviation), and over up to 2,000 evenly
 *       spread BEHIND records (the {@code ?:} behind branch, 5 calls each).</li>
 *   <li>P7 (DIAGNOSTIC: tests the tree's arithmetic and structure, not correct rounding) CEL E2 under the production
 *       StrictMath placeholder provider is bit-identical to B-STRICT over the 3 named and 240 evenly spread in-front
 *       records.</li>
 *   <li>P8 admission: E1 is ACCEPTED by {@code CelVerifier} with and without a schema; E2 has more than 1,024 nodes and depth
 *       over 32, is refused by the encoder, and its static cost exceeds 1,000,000 (CostModel refusal). ELSE the "REJECTED by
 *       v0.1 ceilings" finding is void and is reported as such.</li>
 *   <li>P9 evaluated transcendental calls for one in-front record: E1 5; E2 156,553 (derived by hand from the templates before
 *       any run: 5 per camera-frame coordinate, 20 in rho, 26 in theta, 71 in each raw a and b, 598 in s, 669 in each a1 and b1,
 *       6021 in each a2 and b2, 24,084 in th2, 54,189 in each u, 78,274 in each of col and row, plus 5 in the behind test).</li>
 *   <li>P10 (anti-vacuous controls, the gate's discrimination) a native E1 with azimuth and elevation swapped differs from CEL
 *       E1 on at least 90% of the sampled valid returns; E2 with the radial-scale template altered (the fifth-order term using
 *       one factor of theta squared too few) differs from B-STRICT on at least 1 of the 240 records. If either shows 0 the
 *       comparison is not discriminating on this sample and the test fails.</li>
 *   <li>NOT predicted: any timing. JMH numbers are Chisel's to take under both build slots.</li>
 * </ol>
 * AC3 stands UNVALIDATED regardless: both sides come from one idea (the same camera/angle convention written once), so
 * bit-identity says the evaluator computes what was written, not that it is what {@code PerImageCamera} computes.
 *
 * <h2>DECLARED DEVIATIONS (added after the first run at commit c18348171; the seal above is NOT edited)</h2>
 * First run, MEASURED 2026-11-11 (log J:/std011-speed-2026-11-11/logs/step7_test_run1.log): P1-P3 held, P7 held, P8 held, P9 held
 * (156,553 exactly), the P10 E2 control rejected 240 of 240; P4's SEALED reading FAILED; P5 and P6 could not be scored for the
 * three reasons below. None of this run's P5/P6 numbers is used in any report.
 * <ul>
 *   <li>D1 (P4). Every one of the six bounds differs from the file's by exactly 0.1 m (max |d| = 0.10000000000000009 m against
 *       the sealed 0.05 m): the converter pads each bound by 0.1 m (E57RawWriter.java:1088-1097, READ in the
 *       Observations_to_Salsa clone at 827cb9ce) and computes the bounds from the stored float32 values with the very formula
 *       E1 uses (E57RawWriter.java:483-505). The sealed reading therefore stands FAILED; the assertion is now the declared
 *       reading (residual after removing the 0.1 m pad under 1e-9 m). The agreement is the converter's own formula applied to
 *       the same stored floats: it checks the field offsets and the arithmetic, and is NOT independent of the converter's
 *       angle convention.</li>
 *   <li>D2 (P5). A test-harness defect of mine: the E1 comparison was wired to the native E2 (width argument 1 instead of 3);
 *       the 9 mismatches at the named records were that wiring, caught by the assertion. Corrected, then run.</li>
 *   <li>D3 (P6). The synthetic camera pointed (azimuth 0.7, elevation 0.45) put all 8,261,030 records in front, so the sealed
 *       BEHIND branch had no records to test. The synthetic pointing is now azimuth -0.8, elevation 0.1 (54% in front by an
 *       exploratory count). The in-front half of P6 had held (0 mismatches of 30 values over 15 records) under the old
 *       camera; the figures reported are from the run at the final commit.</li>
 * </ul>
 *
 * <h2>ADDED AFTER THE OPUS REVIEW (committed BEFORE the run that scores P11 and P12; the seal and D1-D3 above are not edited)</h2>
 * Review: J:/std011-speed-2026-11-11/REVIEW_OPUS_STAGE1.md (claude-opus-5-5), fixes brief BRIEF_STAGE1_FIXES.md.
 * <ul>
 *   <li>P11 (review F3, a one-record forward shift of the reader). Predicted: {@code geo:sx12ReturnsDelivered} equals the record
 *       count (8,261,030, the value the first run's README quoted and the reviewer did not read), so records minus returns
 *       delivered is 0 and {@code rangeZero} is 0; the slack after the last record is 260 bytes
 *       (453,547 pages x 1,020 - 8,261,030 x 56) and none of them is non-zero (the reviewer read the same bytes with {@code od};
 *       this prediction is therefore not independent of that reading). ELSE: print the values and fail; a nonzero
 *       {@code rangeZero} with zero returns-missing means the reader is late. SCOPE: it detects a forward shift of 1-4
 *       records (the slack holds 4 whole records); a larger shift would read past the record pages into the appended sections,
 *       which this check does not analyse (UNVALIDATED).</li>
 *   <li>P12 (review F7). Predicted: all 4,096 timed records give finite native E2 (StrictMath) outputs and finite 3-element E1
 *       results under both providers; the negative control (a NaN range) makes the check throw.</li>
 *   <li>D4 (review F4). The correctly-rounded E2 comparison counts DISTINCT records (the evenly spread 12 repeated two named
 *       records, so the first run compared 13 distinct records, not 15). The sealed P6 text stands.</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Stage1CorrectnessTest {

    private static final int E1_SAMPLE = 100_000;
    private static final int E2_CR_EXTRA = 9;
    private static final int E2_BEHIND = 2_000;
    private static final int E2_STRICT = 240;

    /** E57RawWriter.java:1094 (READ 2026-11-11): each cartesianBounds value is the data bound moved outward by this much. */
    private static final double WRITER_BOUNDS_PAD_M = 0.1;

    private RawScanColumns cols;
    private Stage1Expressions x;
    private Sampling.Front front;
    private ExprNode e1;
    private ExprNode e2;
    private NativeBaselines nativeCr;
    private NativeBaselines nativeStrict;

    @BeforeAll
    void load() throws IOException, CelParseException {
        cols = RawScanColumns.load(Sampling.rawscanPath());
        x = Stage1Expressions.synthetic();
        e1 = x.e1();
        e2 = x.e2();
        nativeCr = new NativeBaselines(MathProviders.correctlyRounded().get(), x.camera());
        nativeStrict = new NativeBaselines(new Trig.Strict(), x.camera());
        front = Sampling.inFront(cols, nativeStrict);
        say(x.camera().describe());
        say(cols.census.describe());
        say(new StringBuilder("in-front valid returns (StrictMath camera z < 0): ").append(front.indices().length)
                .append(" of ").append(front.validReturns()).append(" valid returns (range > 0) of ").append(front.records())
                .append(" records").toString());
    }

    // ----------------------------------------------------------------------------------------- the file

    @Test
    void p1p2p3_everyPageVerifiesAndTheStoredAnglesAreInBounds() {
        RawScanColumns.Census c = cols.census;
        assertEquals(0L, c.crcFailuresPage0(), "page 0 CRC");
        assertEquals(0L, c.crcFailuresRecords(), "record pages CRC");
        assertEquals(0L, c.crcFailuresOther(), "later pages (appended sections, XML) CRC");
        assertEquals(c.recordCount(), c.geoConversionPoints(), "points/@recordCount vs geo:conversionPointsWritten");
        assertEquals(c.pagesInFile(), c.pagesVerified(), "every page of the file was CRC-checked");
        assertEquals(0L, c.azimuthOutOfBounds(), "stored azimuth outside [-(float)pi, (float)pi]");
        assertEquals(0L, c.elevationOutOfBounds(), "stored elevation outside +-(float)(pi/2)");
        assertEquals(0L, c.rangeNegativeOrNaN(), "stored range negative or NaN");
        assertTrue(front.validReturns() > c.recordCount() / 2, "most records are returns, so the sample is not vacuous");

        // P11 (review F3): a reader that begins k records late still decodes n records and passes the CRC, bound and count
        // checks above; its last k records come from the zero padding after the last record, so they read range 0.
        say(new StringBuilder("P11: geo:sx12ReturnsDelivered=").append(c.sx12ReturnsDelivered()).append("; records minus returns delivered=")
                .append(c.recordCount() - c.sx12ReturnsDelivered()).append("; range == 0 records=").append(c.rangeZero())
                .append("; slack after the last record=").append(c.slackBytes()).append(" bytes, non-zero=")
                .append(c.slackNonZeroBytes()).toString());
        assertTrue(c.sx12ReturnsDelivered() >= 0, "the file must state its returns delivered for this check");
        assertEquals(c.recordCount() - c.sx12ReturnsDelivered(), c.rangeZero(),
                "range == 0 records must equal records minus the converter's returns delivered (a forward shift reads the padding)");
        assertEquals(c.recordPages() * PAYLOAD - c.recordCount() * RECORD, c.slackBytes(), "slack after the last record");
        assertEquals(0L, c.slackNonZeroBytes(), "the padding after the last record must be zero");
    }

    private static final long PAYLOAD = RawScanColumns.PAYLOAD_BYTES;
    private static final long RECORD = RawScanColumns.RECORD_BYTES;

    /** P12 (review F7): every one of the 4,096 timed records is finite and well-formed under both providers, and the check can fail. */
    @Test
    void p12_theTimedSampleIsFiniteAndWellFormedAndTheCheckCanFail() throws CelParseException {
        int[] sample = front.evenly(CelEvalBenchmark.SAMPLE);
        CelEvalBenchmark.Data.verifyTimedSample(cols.range, cols.azimuth, cols.elevation, sample);
        say(new StringBuilder("P12: the ").append(sample.length).append(" timed records are finite for native E2 (StrictMath) and for E1 under the")
                .append(" correctly-rounded and the placeholder providers").toString());
        // negative control: a NaN range must make the check fail, naming the record
        float[] r = {5.0f, Float.NaN};
        float[] a = {0.5f, 0.5f};
        float[] e = {0.2f, 0.2f};
        assertThrows(IllegalStateException.class, () -> CelEvalBenchmark.Data.verifyTimedSample(r, a, e, new int[] {0, 1}));
        float[] okR = {5.0f, 6.0f};
        CelEvalBenchmark.Data.verifyTimedSample(okR, a, e, new int[] {0, 1});
        // the sample is what the stride cursor assumes: SAMPLE distinct records, strictly increasing
        for (int k = 1; k < sample.length; k++) {
            assertTrue(sample[k] > sample[k - 1], "the sample must be strictly increasing");
        }
        assertEquals(1, java.math.BigInteger.valueOf(CelEvalBenchmark.STRIDE).gcd(java.math.BigInteger.valueOf(CelEvalBenchmark.SAMPLE)).intValue(),
                "the cursor stride must be coprime with the sample size");
    }

    @Test
    void p4_e1CartesianBoundsAgreeWithTheFilesOwnBounds() {
        double[] lo = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] hi = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        double[] out = new double[3];
        long used = 0;
        for (int i = 0; i < cols.count(); i++) {
            if (!(cols.range[i] > 0f)) {
                continue;
            }
            nativeStrict.e1(cols.range[i], cols.azimuth[i], cols.elevation[i], out);
            used++;
            for (int k = 0; k < 3; k++) {
                lo[k] = Math.min(lo[k], out[k]);
                hi[k] = Math.max(hi[k], out[k]);
            }
        }
        double[] xml = cols.census.xmlCartesianBounds();
        double[] mine = {lo[0], hi[0], lo[1], hi[1], lo[2], hi[2]};
        String[] names = {"xMin", "xMax", "yMin", "yMax", "zMin", "zMax"};
        StringBuilder sb = new StringBuilder(512);
        sb.append("E1 bounds over ").append(used).append(" valid returns (StrictMath, B-STRICT E1) vs the file XML cartesianBounds:\n");
        double worst = 0.0;
        double worstAfterPad = 0.0;
        for (int k = 0; k < 6; k++) {
            double d = mine[k] - xml[k];
            double padded = (k % 2 == 0) ? xml[k] + WRITER_BOUNDS_PAD_M : xml[k] - WRITER_BOUNDS_PAD_M;
            worst = Math.max(worst, Math.abs(d));
            worstAfterPad = Math.max(worstAfterPad, Math.abs(mine[k] - padded));
            sb.append("  ").append(names[k]).append(": mine=").append(mine[k]).append(" xml=").append(xml[k])
                    .append(" d=").append(d).append('\n');
        }
        sb.append("  SEALED P4 reading (max |d| < 0.05 m): max |d| = ").append(worst).append(" m, ")
                .append(worst < 0.05 ? "HELD" : "FAILED").append('\n');
        sb.append("  DECLARED D1 reading (XML bound = mine -+ 0.1 m, the writer's padding): max residual = ")
                .append(worstAfterPad).append(" m");
        say(sb.toString());
        assertTrue(worstAfterPad < 1e-9, "E1 differs from the file's own bounds beyond the writer's 0.1 m padding");
    }

    // ----------------------------------------------------------------------------------------- the expressions

    @Test
    void substitutedTreesEqualTheParseOfTheInlinedText() throws CelParseException {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("substituted tree vs CelTextParser.parse(fully inlined text), per piece:\n");
        int compared = 0;
        for (String name : x.pieces().keySet()) {
            long len = x.inlinedLength(name);
            if (len > 900_000L) {
                // Review F5: the threshold is this test's own (900,000), below the parser's cap of 1,000,000 characters:
                // th2 and rpx (about 984,000 chars) are under the cap and would be refused by the node ceiling instead.
                sb.append("  ").append(name).append(": inlined text ").append(len)
                        .append(" chars, skipped by this test's 900,000-char threshold (the parser's own cap is 1,000,000)\n");
                continue;
            }
            ExprNode whole;
            try {
                whole = CelTextParser.parse(x.inlinedText(name, 900_000));
            } catch (CelParseException e) {
                sb.append("  ").append(name).append(": inlined text ").append(len).append(" chars, REFUSED by the parser: ")
                        .append(e.getMessage()).append('\n');
                continue;
            }
            assertEquals(whole, x.expand(name), new StringBuilder("substitution differs from inlining for ").append(name).toString());
            compared++;
            sb.append("  ").append(name).append(": inlined text ").append(len).append(" chars, trees equal\n");
        }
        say(sb.toString());
        assertTrue(compared >= 8, "the equality check must cover E1 and several E2 intermediates");
    }

    @Test
    void ourNodeAndDepthCountsAgreeWithTheProductionCeilingsAtTheirBoundaries() throws CelParseException {
        // nodes: a list literal of n double literals is 1 + n nodes at depth 2
        String ok = listText(1023);
        String over = listText(1024);
        assertEquals(1024L, Stage1Expressions.measure(CelTextParser.parse(ok)).nodes());
        assertThrows(CelParseException.class, () -> CelTextParser.parse(over));
        // depth: n negations over a literal are n+1 nodes at depth n+1
        ExprNode d32 = CelTextParser.parse(negText(31));
        assertEquals(32, Stage1Expressions.measure(d32).depth());
        assertThrows(CelParseException.class, () -> CelTextParser.parse(negText(32)));
        // a field reference counts itself and its step
        assertEquals(2L, Stage1Expressions.measure(CelTextParser.parse("range")).nodes());
    }

    @Test
    void p8p9_admissionAndCallCounts() {
        Stage1Expressions.Measure m1 = Stage1Expressions.measure(e1);
        Stage1Expressions.Measure m2 = Stage1Expressions.measure(e2);
        StringBuilder sb = new StringBuilder(2048);
        sb.append("E1: ").append(m1.nodes()).append(" nodes, depth ").append(m1.depth()).append(", ").append(m1.trigCallsSyntactic())
                .append(" transcendental calls (syntactic), text ").append(x.inlinedLength("e1")).append(" chars\n");
        sb.append("    ").append(Stage1Expressions.staticCost(e1)).append('\n');
        String v1 = Stage1Expressions.admissionVerdict(e1, null);
        String v1s = Stage1Expressions.admissionVerdict(e1, new Stage1Expressions.Schema());
        sb.append("    ").append(v1).append('\n').append("    ").append(v1s).append('\n');
        sb.append("E2: ").append(m2.nodes()).append(" nodes, depth ").append(m2.depth()).append(", ").append(m2.trigCallsSyntactic())
                .append(" transcendental calls (syntactic, both ?: branches), text ").append(x.inlinedLength("e2"))
                .append(" chars (parser input cap 1,000,000)\n");
        sb.append("    ").append(Stage1Expressions.staticCost(e2)).append('\n');
        String v2 = Stage1Expressions.admissionVerdict(e2, null);
        sb.append("    ").append(v2).append('\n');
        assertTrue(v1.contains("ACCEPTED") && v1s.contains("ACCEPTED"), v1.concat(" / ").concat(v1s));
        assertTrue(m1.nodes() <= 1024 && m1.depth() <= 32);
        assertTrue(m2.nodes() > 1024 && m2.depth() > 32, "E2 must exceed the v0.1 ceilings for the REJECTED finding to stand");
        assertTrue(v2.startsWith("REFUSED"), v2);
        assertTrue(Stage1Expressions.staticCost(e2).startsWith("CostModel refused"), "E2 static cost must exceed maxExprCost");

        int probe = front.first();
        Trig.Counting c1 = new Trig.Counting(new Trig.Strict());
        new CelRunner(e1, MathProvider.conformant(c1), new RecordProjection(cols.range, cols.azimuth, cols.elevation))
                .evaluateInto(probe, new double[3]);
        Trig.Counting c2 = new Trig.Counting(new Trig.Strict());
        new CelRunner(e2, MathProvider.conformant(c2), new RecordProjection(cols.range, cols.azimuth, cols.elevation))
                .evaluateInto(probe, new double[2]);
        sb.append("evaluated transcendental calls, record ").append(probe).append(" (in front): E1 ").append(c1.total())
                .append(" [cos ").append(c1.cos).append(", sin ").append(c1.sin).append("]; E2 ").append(c2.total())
                .append(" [cos ").append(c2.cos).append(", sin ").append(c2.sin).append(", atan2 ").append(c2.atan2)
                .append(", tan ").append(c2.tan).append("]");
        say(sb.toString());
        assertEquals(5L, c1.total());
        assertEquals(156_553L, c2.total());
    }

    /** EXPLORATORY, no prediction: how much of the timed in-front population would land inside the synthetic 4096 x 3072 image. */
    @Test
    void diagnostic_imageWindowShareOfTheInFrontRecords() {
        double[] out = new double[2];
        long inImage = 0;
        for (int i : front.indices()) {
            nativeStrict.e2(cols.range[i], cols.azimuth[i], cols.elevation[i], out);
            if (out[0] >= 0.0 && out[0] < 4096.0 && out[1] >= 0.0 && out[1] < 3072.0) {
                inImage++;
            }
        }
        say(new StringBuilder("EXPLORATORY: ").append(inImage).append(" of ").append(front.indices().length)
                .append(" in-front valid returns project inside the synthetic 4096 x 3072 image (StrictMath E2); the timed sample is")
                .append(" NOT restricted to them").toString());
    }

    // ----------------------------------------------------------------------------------------- bit-identity

    @Test
    void p5_e1CelEqualsBCrOnTheNamedRecordsAndOverTheWholeFileSample() throws Exception {
        int[] named = {front.first(), front.middle(), front.last()};
        Outcome n = compare(named, 3, () -> celInto(e1, correctlyRounded()), () -> natInto(nativeCr, 3));
        say(namedLine("E1", "B-CR", named, n));

        int[] sample = Sampling.evenlyOverFile(cols.count(), E1_SAMPLE);
        Outcome s = compare(sample, 3, () -> celInto(e1, correctlyRounded()), () -> natInto(nativeCr, 3));
        long valid = 0;
        long inFront = 0;
        for (int i : sample) {
            if (cols.range[i] > 0f) {
                valid++;
                if (nativeStrict.inFront(cols.range[i], cols.azimuth[i], cols.elevation[i])) {
                    inFront++;
                }
            }
        }
        say(sampleLine("E1", "B-CR", sample.length, cols.count(), s, valid, inFront));
        assertEquals(0L, n.bitMismatches, () -> n.firstDifference);
        assertEquals(0L, s.bitMismatches, () -> s.firstDifference);
        assertTrue(valid > 1000 && s.minValue < s.maxValue, "the sample must hold real, varying returns");

        // P10 control: the comparison must be able to fail. Swap azimuth and elevation in the native E1.
        long differs = 0;
        double[] swapped = new double[3];
        for (int k = 0; k < sample.length; k++) {
            int i = sample[k];
            if (!(cols.range[i] > 0f)) {
                continue;
            }
            nativeStrict.e1(cols.range[i], cols.elevation[i], cols.azimuth[i], swapped);
            if (Double.doubleToRawLongBits(swapped[0]) != Double.doubleToRawLongBits(s.cel[k][0])
                    || Double.doubleToRawLongBits(swapped[1]) != Double.doubleToRawLongBits(s.cel[k][1])
                    || Double.doubleToRawLongBits(swapped[2]) != Double.doubleToRawLongBits(s.cel[k][2])) {
                differs++;
            }
        }
        say(new StringBuilder("CONTROL (azimuth and elevation swapped in the native E1): differs from CEL E1 on ").append(differs)
                .append(" of ").append(valid).append(" sampled valid returns (").append(String.format("%.2f", 100.0 * differs / valid))
                .append("%)").toString());
        assertTrue(differs * 10 >= valid * 9, "the control must reject at least 90% of the valid returns");
    }

    @Test
    void p6_e2CelEqualsBCrOnNamedExtraInFrontAndBehindRecords() throws Exception {
        int[] named = {front.first(), front.middle(), front.last()};
        int[] extra = front.evenly(E2_CR_EXTRA + 3);
        // Review F4 (Opus 2026-11-11): the evenly spread records include the first and the middle in-front record, which are
        // already named, so the earlier "15 records" were 13 distinct; count distinct records, named ones first.
        java.util.LinkedHashSet<Integer> distinct = new java.util.LinkedHashSet<>();
        for (int i : named) {
            distinct.add(i);
        }
        for (int i : extra) {
            distinct.add(i);
        }
        int[] all = new int[distinct.size()];
        int at = 0;
        for (int i : distinct) {
            all[at++] = i;
        }
        Outcome o = compare(all, 2, () -> celInto(e2, correctlyRounded()), () -> natInto(nativeCr, 2));
        say(namedLine("E2", "B-CR", named, o));
        say(new StringBuilder("E2 vs B-CR over ").append(all.length).append(" DISTINCT in-front records (3 named + ")
                .append(extra.length).append(" evenly spread, ").append(named.length + extra.length - all.length)
                .append(" of which repeat a named record; of ").append(front.indices().length).append(" in front): bit mismatches ")
                .append(o.bitMismatches).append(" of ").append(o.values).append(" values, max |d| ").append(o.maxAbs).toString());
        assertEquals(0L, o.bitMismatches, () -> o.firstDifference);
        assertTrue(o.minValue < o.maxValue);

        int[] behind = behindRecords(E2_BEHIND);
        Outcome b = compare(behind, 2, () -> celInto(e2, correctlyRounded()), () -> natInto(nativeCr, 2));
        say(new StringBuilder("E2 vs B-CR over ").append(behind.length).append(" BEHIND records (evenly spread of ")
                .append(front.validReturns() - front.indices().length).append(" valid returns not in front): bit mismatches ")
                .append(b.bitMismatches).append(" of ").append(b.values).append(" values; every output is [-1.0, -1.0]: ")
                .append(b.minValue == -1.0 && b.maxValue == -1.0).toString());
        assertEquals(0L, b.bitMismatches, () -> b.firstDifference);
        assertTrue(b.minValue == -1.0 && b.maxValue == -1.0, "behind records must take the [-1.0, -1.0] branch");
    }

    @Test
    void p7p10_e2UnderTheStrictPlaceholderEqualsBStrictAndTheControlDiffers() throws Exception {
        int[] named = {front.first(), front.middle(), front.last()};
        int[] spread = front.evenly(E2_STRICT);
        Outcome o = compare(spread, 2, () -> celInto(e2, MathProvider.nonConformantForTestingOnly()), () -> natInto(nativeStrict, 2));
        say(namedLine("E2 DIAGNOSTIC (StrictMath placeholder provider)", "B-STRICT", named,
                compare(named, 2, () -> celInto(e2, MathProvider.nonConformantForTestingOnly()), () -> natInto(nativeStrict, 2))));
        say(new StringBuilder("E2 (StrictMath placeholder) vs B-STRICT over ").append(spread.length)
                .append(" evenly spread in-front records of ").append(front.indices().length).append(": bit mismatches ")
                .append(o.bitMismatches).append(" of ").append(o.values).append(" values, max |d| ").append(o.maxAbs).toString());
        assertEquals(0L, o.bitMismatches, () -> o.firstDifference);
        assertTrue(o.minValue < o.maxValue);

        // control: the fifth-order term of the radial scale loses one factor of theta squared
        String altered = x.pieces().get("rs").replace(
                " * (theta * theta) * (theta * theta) * (theta * theta) * (theta * theta)",
                " * (theta * theta) * (theta * theta) * (theta * theta)");
        assertFalse(altered.equals(x.pieces().get("rs")), "the control must actually alter the template");
        ExprNode wrong = x.withPiece("rs", altered).e2();
        Outcome c = compare(spread, 2, () -> celInto(wrong, MathProvider.nonConformantForTestingOnly()), () -> natInto(nativeStrict, 2));
        say(new StringBuilder("CONTROL (E2 with the fifth-order radial term one factor of theta^2 short) vs B-STRICT: ")
                .append(c.recordsDiffering).append(" of ").append(spread.length).append(" records differ (")
                .append(c.bitMismatches).append(" of ").append(c.values).append(" values)").toString());
        assertTrue(c.recordsDiffering >= 1, "the E2 comparison must be able to fail");
    }

    @Test
    void theProjectionHonoursTheReferenceStabilityContract() {
        RecordProjection p = new RecordProjection(cols.range, cols.azimuth, cols.elevation);
        p.set(front.first());
        assertSame(p.fieldValue(RecordProjection.CLASS_NAME, "range"), p.fieldValue(RecordProjection.CLASS_NAME, "range"));
        assertTrue(p.declaresField(RecordProjection.CLASS_NAME, "azimuth"));
        assertFalse(p.declaresField(RecordProjection.CLASS_NAME, "intensity"));
        assertFalse(p.isUndecodable());
        assertNotNull(p.namespaceChain());
    }

    // ----------------------------------------------------------------------------------------- machinery

    private interface Into {
        void run(int record, double[] out);
    }

    private MathProvider correctlyRounded() {
        return MathProviders.correctlyRounded();
    }

    private Into celInto(ExprNode root, MathProvider math) {
        CelRunner r = new CelRunner(root, math, new RecordProjection(cols.range, cols.azimuth, cols.elevation));
        return r::evaluateInto;
    }

    private Into natInto(NativeBaselines nb, int width) {
        return (i, out) -> {
            if (width == 3) {
                nb.e1(cols.range[i], cols.azimuth[i], cols.elevation[i], out);
            } else {
                nb.e2(cols.range[i], cols.azimuth[i], cols.elevation[i], out);
            }
        };
    }

    private int[] behindRecords(int want) {
        List<Integer> all = new ArrayList<>();
        int stride = 997;
        for (int i = 0; i < cols.count() && all.size() < want * 4; i += stride) {
            if (cols.range[i] > 0f && !nativeStrict.inFront(cols.range[i], cols.azimuth[i], cols.elevation[i])) {
                all.add(i);
            }
        }
        assertTrue(all.size() >= want, "not enough behind records found");
        int[] out = new int[want];
        for (int k = 0; k < want; k++) {
            out[k] = all.get((int) ((long) k * all.size() / want));
        }
        return out;
    }

    /** What a comparison found; the stashed per-record outputs feed the controls. */
    private static final class Outcome {
        long values;
        long bitMismatches;
        long recordsDiffering;
        double maxAbs;
        double minValue = Double.POSITIVE_INFINITY;
        double maxValue = Double.NEGATIVE_INFINITY;
        double[][] cel;
        double[][] nat;
        /** Reported through the assertion message of a check that expects none; a CONTROL expects some and prints nothing. */
        String firstDifference = "no difference";
    }

    /** Runs CEL and native on every index, in parallel slices, each slice with its OWN evaluator, projection and baseline. */
    private Outcome compare(int[] indices, int width, Supplier<Into> celFactory, Supplier<Into> natFactory)
            throws InterruptedException, ExecutionException {
        Outcome o = new Outcome();
        o.cel = new double[indices.length][width];
        o.nat = new double[indices.length][width];
        int threads = Math.max(1, Math.min(10, Runtime.getRuntime().availableProcessors() - 1));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int from = (int) ((long) t * indices.length / threads);
                final int to = (int) ((long) (t + 1) * indices.length / threads);
                futures.add(pool.submit(() -> {
                    Into cel = celFactory.get();
                    Into nat = natFactory.get();
                    for (int k = from; k < to; k++) {
                        cel.run(indices[k], o.cel[k]);
                        nat.run(indices[k], o.nat[k]);
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }
        for (int k = 0; k < indices.length; k++) {
            boolean differs = false;
            for (int j = 0; j < width; j++) {
                o.values++;
                double a = o.cel[k][j];
                double b = o.nat[k][j];
                if (Double.doubleToRawLongBits(a) != Double.doubleToRawLongBits(b)) {
                    o.bitMismatches++;
                    differs = true;
                }
                o.maxAbs = Math.max(o.maxAbs, Math.abs(a - b));
                o.minValue = Math.min(o.minValue, a);
                o.maxValue = Math.max(o.maxValue, a);
            }
            if (differs) {
                o.recordsDiffering++;
                if (o.recordsDiffering == 1) {
                    o.firstDifference = new StringBuilder("first differing record ").append(indices[k]).append(": cel ")
                            .append(java.util.Arrays.toString(o.cel[k])).append(" native ")
                            .append(java.util.Arrays.toString(o.nat[k])).toString();
                }
            }
        }
        return o;
    }

    private String namedLine(String what, String against, int[] named, Outcome o) {
        // the named outcome may come from a larger comparison; the stash is indexed like `named` only for the named call
        StringBuilder sb = new StringBuilder(256);
        sb.append("cel vs native (").append(what).append(" vs ").append(against).append(") at {").append(named[0]).append(',')
                .append(named[1]).append(',').append(named[2]).append("}: d={");
        for (int k = 0; k < 3 && k < o.cel.length; k++) {
            sb.append(k == 0 ? "" : ",").append('[');
            for (int j = 0; j < o.cel[k].length; j++) {
                sb.append(j == 0 ? "" : ",").append(o.cel[k][j] - o.nat[k][j]);
            }
            sb.append(']');
        }
        sb.append("} (first, middle, last in-front valid return; each d is cel minus native per output component)");
        return sb.toString();
    }

    private String sampleLine(String what, String against, int sample, int records, Outcome o, long valid, long inFront) {
        return new StringBuilder(what).append(" vs ").append(against).append(" over ").append(sample)
                .append(" records spread evenly over the file (of ").append(records).append("; ").append(valid)
                .append(" valid returns, ").append(inFront).append(" of them in front): bit mismatches ").append(o.bitMismatches)
                .append(" of ").append(o.values).append(" values (").append(o.cel[0].length).append(" x ").append(sample)
                .append("), max |d| = ").append(o.maxAbs).toString();
    }

    private static String listText(int n) {
        StringBuilder sb = new StringBuilder(n * 5 + 2);
        sb.append('[');
        for (int i = 0; i < n; i++) {
            sb.append(i == 0 ? "" : ",").append("1.0");
        }
        return sb.append(']').toString();
    }

    private static String negText(int negations) {
        StringBuilder sb = new StringBuilder(negations + 4);
        for (int i = 0; i < negations; i++) {
            sb.append('-');
        }
        return sb.append("1.0").toString();
    }

    private static void say(String s) {
        System.out.println(s);
    }
}
