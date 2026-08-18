/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.outrigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import net.jini.core.entry.Entry;
import org.apache.river.outrigger.proxy.EntryRep;

import au.net.zeus.jgdms.cel.CelValue;
import au.net.zeus.jgdms.cel.ast.ExprNode;

/**
 * Unit B3 — the per-candidate CEL evaluation core ({@link FilterEval}) and the
 * per-candidate projection budget ({@link EntryProjection#MAX_PROJECTION_DECODE_BYTES}).
 * Proves the design memo B3 &sect;1.4/&sect;3/&sect;4/&sect;7 behaviour: applicable-filter
 * selection off the STORED digest, class-free value evaluation, all-must-pass,
 * honest-false vs fail-closed accounting, subclass resolution by an inherited
 * field, the cost ceiling, and the Stage-1 {@code size()} memo's identity
 * precondition.
 */
public class FilterEvalTest {

    // ---- real reflective entries (their classes ARE on the test classpath;
    //      class-free-ness itself is proven by EntryProjectionTest) -----------

    public static class Reading implements Entry {
        public Double temperatureCelsius;
        public String stationName;
        public Reading() {}
        public Reading(Double t, String s) { temperatureCelsius = t; stationName = s; }
    }

    /** A SUBCLASS of Reading — byte-matches a Reading template, longer schema chain. */
    public static class DetailedReading extends Reading {
        public String sensorId;
        public DetailedReading() {}
        public DetailedReading(Double t, String s, String sensor) {
            super(t, s); sensorId = sensor;
        }
    }

    // ---- AST helpers --------------------------------------------------------

    private static ExprNode.FieldRef ref(String name) {
        return new ExprNode.FieldRef(
                java.util.List.of(new ExprNode.SelectorStep.Unqual(name)));
    }

    /** temperatureCelsius > 20.0 && stationName == "North" */
    private static ExprNode warmNorth() {
        return new ExprNode.And(
                new ExprNode.Gt(ref("temperatureCelsius"), new ExprNode.LitDouble(20.0)),
                new ExprNode.Eq(ref("stationName"), new ExprNode.LitString("North")));
    }

    private static CompiledFilter concrete(ExprNode expr, byte[] digest) {
        return new CompiledFilter(expr, 0L, digest);
    }

    private static CompiledFilter schemaLess(ExprNode expr) {
        return new CompiledFilter(expr, 0L, null);
    }

    // ---- metric helpers -----------------------------------------------------

    private static long metric(String name) {
        long[] m = FilterAdmission.metrics();
        for (int i = 0; i < FilterAdmission.METRIC_NAMES.length; i++) {
            if (FilterAdmission.METRIC_NAMES[i].equals(name)) return m[i];
        }
        throw new AssertionError("no such metric: " + name);
    }

    // =====================================================================

    @Test
    public void emptyFilterSetIsNoOp() throws Exception {
        EntryRep cand = new EntryRep(new Reading(25.0, "North"));
        long ev = metric("filter.evaluated");
        assertTrue(FilterEval.matches(FilterSet.EMPTY, cand));
        assertEquals("EMPTY must never evaluate", ev, metric("filter.evaluated"));
    }

    @Test
    public void concreteTruePasses() throws Exception {
        EntryRep cand = new EntryRep(new Reading(25.0, "North"));
        FilterSet fs = FilterSet.of(concrete(warmNorth(), cand.entrySchemaDigest()));
        long ev = metric("filter.evaluated"), pa = metric("filter.passed");
        assertTrue(FilterEval.matches(fs, cand));
        assertEquals(ev + 1, metric("filter.evaluated"));
        assertEquals(pa + 1, metric("filter.passed"));
    }

    @Test
    public void concreteFalseIsHonestExclusion() throws Exception {
        EntryRep cand = new EntryRep(new Reading(10.0, "North")); // too cold
        FilterSet fs = FilterSet.of(concrete(warmNorth(), cand.entrySchemaDigest()));
        long xf = metric("filter.excludedFalse"), fc = metric("filter.failClosedExclusions");
        assertFalse(FilterEval.matches(fs, cand));
        assertEquals("honest predicate-false", xf + 1, metric("filter.excludedFalse"));
        assertEquals("not a fault-driven exclusion", fc, metric("filter.failClosedExclusions"));
    }

    @Test
    public void schemaLessResolvesAgainstCandidateOwnSchema() throws Exception {
        EntryRep cand = new EntryRep(new Reading(25.0, "North"));
        FilterSet fs = FilterSet.of(schemaLess(warmNorth()));
        assertTrue(FilterEval.matches(fs, cand));
    }

    @Test
    public void schemaLessMissingFieldFailsClosed() throws Exception {
        EntryRep cand = new EntryRep(new Reading(25.0, "North"));
        // References a field the candidate's schema does not declare => ABSENT_FIELD
        // => fail-closed exclusion, counted (never an honest-false, never a match).
        FilterSet fs = FilterSet.of(schemaLess(
                new ExprNode.Eq(ref("noSuchField"), new ExprNode.LitString("x"))));
        long fc = metric("filter.failClosedExclusions"), xf = metric("filter.excludedFalse");
        assertFalse(FilterEval.matches(fs, cand));
        assertEquals(fc + 1, metric("filter.failClosedExclusions"));
        assertEquals("absent field is NOT an honest-false", xf, metric("filter.excludedFalse"));
    }

    @Test
    public void subclassFilteredByInheritedField() throws Exception {
        // A DetailedReading byte-matches a Reading template but carries its own,
        // longer digest. The one concrete filter (keyed to Reading's digest) must
        // still constrain it, resolved schema-lessly against its own (inherited)
        // stationName/temperatureCelsius (§3 amendment, the fail-open the old rule left).
        EntryRep readingTmpl = new EntryRep(new Reading(null, null));
        EntryRep warm = new EntryRep(new DetailedReading(25.0, "North", "s1"));
        EntryRep cold = new EntryRep(new DetailedReading(10.0, "North", "s1"));
        FilterSet fs = FilterSet.of(concrete(warmNorth(), readingTmpl.entrySchemaDigest()));

        assertTrue("subclass warm-north matches via inherited fields",
                FilterEval.matches(fs, warm));
        assertFalse("subclass cold is excluded by the inherited predicate",
                FilterEval.matches(fs, cold));
    }

    @Test
    public void filterSetGuardsSharedExpression() {
        // The one-envelope-per-op invariant (§6.2/N-6): a FilterSet whose filters
        // carry DIFFERENT predicate expressions cannot be built — that would let one
        // template's predicate be silently misapplied to another's candidates, and
        // §5's subclass resolution (which applies ANY filter's expression on a digest
        // mismatch) would be unsound. Rejected LOUDLY at construction.
        CompiledFilter t = schemaLess(
                new ExprNode.Gt(ref("temperatureCelsius"), new ExprNode.LitDouble(20.0)));
        CompiledFilter f = schemaLess(
                new ExprNode.Eq(ref("stationName"), new ExprNode.LitString("South")));
        try {
            new FilterSet.Builder().add(t).add(f).build();
            org.junit.Assert.fail("expected IllegalStateException for mixed expressions");
        } catch (IllegalStateException expected) {
            // one-envelope-per-op invariant enforced at construction
        }
    }

    @Test
    public void allMustPassAcrossSchemaLessAndConcreteSameEnvelope() throws Exception {
        // A legitimate multi-entry applicable list (§5 all-must-pass): a match-any
        // template (schema-less) AND a concrete template, from the SAME envelope so
        // both carry the same predicate. A candidate matching the concrete digest is
        // subject to BOTH; the all-must-pass loop evaluates each. Both agree (one
        // envelope), so a matching candidate passes and a non-matching one is excluded.
        EntryRep cand = new EntryRep(new Reading(25.0, "North"));
        FilterSet fs = new FilterSet.Builder()
                .add(schemaLess(warmNorth()))
                .add(concrete(warmNorth(), cand.entrySchemaDigest()))
                .build();
        assertTrue(FilterEval.matches(fs, cand));

        EntryRep cold = new EntryRep(new Reading(10.0, "North"));
        FilterSet fs2 = new FilterSet.Builder()
                .add(schemaLess(warmNorth()))
                .add(concrete(warmNorth(), cold.entrySchemaDigest()))
                .build();
        assertFalse(FilterEval.matches(fs2, cold));
    }

    @Test
    public void overBudgetCandidateExcludedAndCounted() throws Exception {
        // A stationName far larger than the 64 KiB per-candidate projection budget:
        // decoding it blows the budget => fail-closed exclusion, counted in the
        // broken-out projectionBudget sub-category (§4.3 / §7). Lock-hold bounded.
        char[] big = new char[EntryProjection.MAX_PROJECTION_DECODE_BYTES + 8];
        Arrays.fill(big, 'N');
        EntryRep cand = new EntryRep(new Reading(25.0, new String(big)));
        FilterSet fs = FilterSet.of(schemaLess(
                new ExprNode.Eq(ref("stationName"), new ExprNode.LitString("x"))));
        long pb = metric("filter.rejected.projectionBudget");
        long fc = metric("filter.failClosedExclusions");
        assertFalse(FilterEval.matches(fs, cand));
        assertEquals("over-budget candidate counted", pb + 1,
                metric("filter.rejected.projectionBudget"));
        assertEquals("and is a fail-closed exclusion", fc + 1,
                metric("filter.failClosedExclusions"));
    }

    @Test
    public void withinBudgetLargeFieldStillEvaluates() throws Exception {
        // A field comfortably under budget decodes and evaluates normally — the
        // budget bounds worst case, it does not fail the happy path.
        char[] med = new char[1024];
        Arrays.fill(med, 'N');
        String station = new String(med);
        EntryRep cand = new EntryRep(new Reading(25.0, station));
        FilterSet fs = FilterSet.of(schemaLess(
                new ExprNode.Eq(ref("stationName"), new ExprNode.LitString(station))));
        assertTrue(FilterEval.matches(fs, cand));
    }

    @Test
    public void multiSchemaSubclassResolvedViaSharedExpression() throws Exception {
        // FIX 2 (recall): two concrete filters keyed to DISTINCT schemas but sharing
        // the SAME predicate (the one-envelope-per-op invariant) + a subclass candidate
        // whose digest matches neither key and no matched-template hint. The OLD rule
        // returned null => fail-closed, silently over-excluding EVERY legitimate subclass
        // result at a multi-template site (contents/register/fan-out all pass null). Now
        // the candidate is resolved schema-lessly against the shared expression, exactly
        // like a directly-matching candidate: warm-north matches by its inherited fields,
        // cold is an honest predicate-false, and neither is a fault-driven exclusion.
        EntryRep other = new EntryRep(new Reading(1.0, "z"));
        byte[] d2 = other.entrySchemaDigest().clone();
        d2[0] ^= 0x5A; // a different, non-matching digest
        CompiledFilter f1 = concrete(warmNorth(), other.entrySchemaDigest());
        CompiledFilter f2 = concrete(warmNorth(), d2);
        FilterSet fs = new FilterSet.Builder().add(f1).add(f2).build();

        EntryRep warm = new EntryRep(new DetailedReading(25.0, "North", "s1"));
        EntryRep cold = new EntryRep(new DetailedReading(10.0, "North", "s1"));
        long fc = metric("filter.failClosedExclusions");
        long xf = metric("filter.excludedFalse");
        assertTrue("subclass warm-north matches via the shared inherited predicate",
                FilterEval.matches(fs, warm));
        assertFalse("subclass cold is an honest predicate-false, not fail-closed",
                FilterEval.matches(fs, cold));
        assertEquals("cold is an honest-false", xf + 1, metric("filter.excludedFalse"));
        assertEquals("neither subclass is a fault-driven exclusion", fc,
                metric("filter.failClosedExclusions"));
    }

    // =====================================================================
    // FIX 3: the defensive "no applicable filter" default must EXCLUDE.
    // =====================================================================

    /**
     * A map that reports non-empty on its FIRST {@code isEmpty()} look and empty on every
     * one after. It exists to drive {@link FilterEval} to the {@code applicable.isEmpty()}
     * branch that {@link FilterSet}, as written, makes unreachable — modelling exactly the
     * hazard FIX 3 defends against: a future {@code applicableTo} that hands back an empty
     * list. With the old {@code return true} this test would prove a silent, unfiltered
     * admission of every candidate.
     */
    private static final class EmptyAfterFirstLookMap
            extends java.util.AbstractMap<String, CompiledFilter> {
        private int looks;
        @Override public boolean isEmpty() { return looks++ > 0; }
        @Override public java.util.Set<java.util.Map.Entry<String, CompiledFilter>> entrySet() {
            return java.util.Collections.emptySet();
        }
    }

    private static FilterSet filterSetThatSelectsNothing() throws Exception {
        final java.lang.reflect.Constructor<FilterSet> c = FilterSet.class
                .getDeclaredConstructor(java.util.Map.class, java.util.List.class);
        c.setAccessible(true);
        return c.newInstance(new EmptyAfterFirstLookMap(), java.util.List.of());
    }

    @Test
    public void emptyApplicableListFailsClosedNotOpen() throws Exception {
        final EntryRep cand = new EntryRep(new Reading(25.0, "North"));
        final FilterSet selectsNothing = filterSetThatSelectsNothing();
        final long fc = metric("filter.failClosedExclusions");
        final long ev = metric("filter.evaluated");

        assertFalse("a non-empty FilterSet that selects NO applicable filter is a fault:"
                + " exclude, never admit the candidate unfiltered",
                FilterEval.matches(selectsNothing, cand));
        assertEquals("and the fault is counted, not silently dropped",
                fc + 1, metric("filter.failClosedExclusions"));
        assertEquals("no predicate ran, so nothing 'reached CEL evaluation'",
                ev, metric("filter.evaluated"));
    }

    // =====================================================================
    // N-4: filter.evaluated counts only candidates that REACHED CEL evaluation.
    // =====================================================================

    @Test
    public void evaluatedExcludesOverBudgetExclusions() throws Exception {
        final char[] big = new char[EntryProjection.MAX_PROJECTION_DECODE_BYTES + 8];
        Arrays.fill(big, 'N');
        final EntryRep over = new EntryRep(new Reading(25.0, new String(big)));
        final FilterSet fs = FilterSet.of(schemaLess(
                new ExprNode.Eq(ref("stationName"), new ExprNode.LitString("x"))));
        final long ev = metric("filter.evaluated");
        final long pb = metric("filter.rejected.projectionBudget");

        assertFalse(FilterEval.matches(fs, over));
        assertEquals("an over-budget candidate is excluded BEFORE the evaluator runs,"
                + " so it must not inflate the denominator",
                ev, metric("filter.evaluated"));
        assertEquals(pb + 1, metric("filter.rejected.projectionBudget"));
    }

    @Test
    public void evaluatedExcludesUndecodableProjections() throws Exception {
        // A write-path rep (no retained Decoded => the projectFields fallback) whose body
        // has been corrupted: the projection is undecodable, so CEL never runs.
        final EntryRep cand = new EntryRep(new Reading(25.0, "North"));
        corruptBody(cand);
        org.junit.Assert.assertNull("write-path rep: the projectFields fallback is taken",
                cand.decoded());
        final FilterSet fs = FilterSet.of(schemaLess(warmNorth()));
        final long ev = metric("filter.evaluated");
        final long fc = metric("filter.failClosedExclusions");

        assertFalse(FilterEval.matches(fs, cand));
        assertEquals("an undecodable candidate never reached CEL evaluation",
                ev, metric("filter.evaluated"));
        assertEquals("but it IS counted as a fail-closed exclusion",
                fc + 1, metric("filter.failClosedExclusions"));
    }

    @Test
    public void happyPathIdentityEvaluatedEqualsPassedPlusExcludedFalse() throws Exception {
        // §8.2's identity, with a pre-CEL (budget) exclusion mixed in to prove the
        // exclusion no longer perturbs the denominator.
        final long ev0 = metric("filter.evaluated");
        final long pa0 = metric("filter.passed");
        final long xf0 = metric("filter.excludedFalse");
        final long fc0 = metric("filter.failClosedExclusions");

        final FilterSet fs = FilterSet.of(schemaLess(warmNorth()));
        assertTrue(FilterEval.matches(fs, new EntryRep(new Reading(25.0, "North"))));
        assertFalse(FilterEval.matches(fs, new EntryRep(new Reading(10.0, "North"))));

        final char[] big = new char[EntryProjection.MAX_PROJECTION_DECODE_BYTES + 8];
        Arrays.fill(big, 'N');
        assertFalse(FilterEval.matches(fs, new EntryRep(new Reading(25.0, new String(big)))));

        final long dEv = metric("filter.evaluated") - ev0;
        final long dPa = metric("filter.passed") - pa0;
        final long dXf = metric("filter.excludedFalse") - xf0;
        assertEquals("two candidates reached CEL evaluation (the third never did)", 2L, dEv);
        assertEquals("§8.2: evaluated == passed + excludedFalse", dEv, dPa + dXf);
        assertEquals("the budget exclusion is accounted for separately",
                fc0 + 1, metric("filter.failClosedExclusions"));
    }

    // =====================================================================
    // B3 §4.2/§4.4 slice reuse (board FIX 1): the projection must NOT re-run an
    // O(body) structural decode per candidate under the handle lock.
    // =====================================================================

    /**
     * Round-trips a rep through the {@code StorableResource} store/restore path — one of
     * the two <em>decode constructions</em> every server-side candidate arrives by (the
     * other is the {@code @AtomicSerial} {@code GetArg} unmarshal on the write path).
     * Both run {@code codec().decode(body)} and retain its result, so this is a faithful
     * stand-in for the candidate frames {@code FilterEval} actually sees.
     */
    private static EntryRep storeRestore(EntryRep rep) throws Exception {
        final java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
        final java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(bout);
        rep.store(oos);
        oos.flush();
        final EntryRep out = new EntryRep();
        out.restore(new java.io.ObjectInputStream(
                new java.io.ByteArrayInputStream(bout.toByteArray())));
        return out;
    }

    /** Overwrites a rep's canonical body with garbage, leaving its retained decode intact. */
    private static void corruptBody(EntryRep rep) throws Exception {
        final java.lang.reflect.Field f = EntryRep.class.getDeclaredField("body");
        f.setAccessible(true);
        f.set(rep, new byte[] { 0x30, 0x03, (byte) 0xDE, (byte) 0xAD, (byte) 0xBE });
    }

    @Test
    public void decodeConstructedRepRetainsItsDecodedBody() throws Exception {
        // The reuse source must actually be populated at the frames FilterEval evaluates.
        final EntryRep written = new EntryRep(new Reading(25.0, "North"));
        org.junit.Assert.assertNull(
                "the client write path builds no Decoded — the documented projectFields fallback",
                written.decoded());

        final EntryRep restored = storeRestore(written);
        org.junit.Assert.assertNotNull(
                "a decode-constructed rep MUST retain its Decoded (the §4.2 reuse source)",
                restored.decoded());
        org.junit.Assert.assertNotNull("slices retained", restored.decoded().sliceBytes);
        org.junit.Assert.assertNotNull("absent markers retained", restored.decoded().absent);
        org.junit.Assert.assertNotNull(
                "the SCHEMA TABLE is the part that used to be discarded — projecting needs it",
                restored.decoded().schemaTable);
    }

    @Test
    public void projectReusingIsIdenticalToProjectFields() throws Exception {
        // Same candidate, two routes: decode-the-body vs reuse-the-decode. The reuse
        // factory is a pure cost reduction — it must observe exactly the same projection.
        final EntryRep rep = storeRestore(new EntryRep(new DetailedReading(25.0, "North", "s1")));
        final java.util.Set<String> referenced =
                java.util.Set.of("temperatureCelsius", "stationName", "sensorId");

        final EntryProjection viaBody = EntryProjection.projectFields(rep.bodyBytes(), referenced);
        final EntryProjection viaReuse = EntryProjection.projectReusing(rep.decoded(), referenced);

        assertFalse("control: the body route decodes", viaBody.isUndecodable());
        assertFalse("the reuse route decodes", viaReuse.isUndecodable());
        assertEquals(viaBody.namespaceChain(), viaReuse.namespaceChain());
        org.junit.Assert.assertArrayEquals(
                viaBody.entrySchemaDigest(), viaReuse.entrySchemaDigest());
        for (String cls : viaBody.namespaceChain()) {
            for (String field : referenced) {
                assertEquals(cls + "#" + field + " schema presence",
                        viaBody.declaresField(cls, field), viaReuse.declaresField(cls, field));
                if (viaBody.declaresField(cls, field)) {
                    assertEquals(cls + "#" + field + " value",
                            viaBody.fieldValue(cls, field), viaReuse.fieldValue(cls, field));
                }
            }
        }
    }

    @Test
    public void filterEvalTakesTheReusePathAndNeverReDecodesTheBody() throws Exception {
        // THE proof that the structural re-decode is gone. Take a decode-constructed
        // candidate, then deliberately corrupt its `body` bytes while leaving the retained
        // Decoded intact. Any structural re-decode of the body would now fail closed; the
        // verdict must still be correct, which is only possible if the body is never
        // re-decoded (§4.2/§4.4 slice reuse).
        final EntryRep warm = storeRestore(new EntryRep(new Reading(25.0, "North")));
        final EntryRep cold = storeRestore(new EntryRep(new Reading(10.0, "North")));
        org.junit.Assert.assertNotNull(warm.decoded());
        org.junit.Assert.assertNotNull(cold.decoded());

        corruptBody(warm);
        corruptBody(cold);

        // Control: the OLD code path (decode the body) is now fail-closed for this rep.
        assertTrue("control: a structural re-decode of the corrupted body fails closed",
                EntryProjection.projectFields(warm.bodyBytes(),
                        java.util.Set.of("stationName")).isUndecodable());

        final FilterSet fs = FilterSet.of(schemaLess(warmNorth()));
        final long fc = metric("filter.failClosedExclusions");
        assertTrue("warm-north still passes — the body was never re-decoded",
                FilterEval.matches(fs, warm));
        assertFalse("cold is still an honest predicate-false, not a fail-closed drop",
                FilterEval.matches(fs, cold));
        assertEquals("no fault-driven exclusion on the reuse path",
                fc, metric("filter.failClosedExclusions"));
    }

    @Test
    public void projectReusingIsFailClosedOnAnUnusableDecoded() throws Exception {
        // Defensive requirements on the reuse input (the flag-day single-provider
        // coupling): null, and a schemaTable that is not the provider's Map, both fail
        // CLOSED here rather than being guessed at. The caller's fallback to projectFields
        // is a caller decision; this factory never downgrades silently.
        assertTrue(EntryProjection.projectReusing(null, java.util.Set.of("stationName"))
                .isUndecodable());

        final EntryRep rep = storeRestore(new EntryRep(new Reading(25.0, "North")));
        final org.apache.river.api.io.EntryV2Codec.Decoded good = rep.decoded();
        final org.apache.river.api.io.EntryV2Codec.Decoded foreign =
                new org.apache.river.api.io.EntryV2Codec.Decoded(
                        good.entrySchemaDigest, good.sliceBytes, good.absent,
                        "not-a-map");
        assertTrue("a non-Map schemaTable is refused, not trusted",
                EntryProjection.projectReusing(foreign, java.util.Set.of("stationName"))
                        .isUndecodable());

        final org.apache.river.api.io.EntryV2Codec.Decoded noSlices =
                new org.apache.river.api.io.EntryV2Codec.Decoded(
                        good.entrySchemaDigest, null, good.absent, good.schemaTable);
        assertTrue("null slices are refused",
                EntryProjection.projectReusing(noSlices, java.util.Set.of("stationName"))
                        .isUndecodable());
    }

    // =====================================================================
    // Stage-1 size() memo: fieldValue returns the SAME instance across one
    // evaluation (design memo B3 §3 — confirm the memo's soundness assumption).
    // =====================================================================

    @Test
    public void projectionFieldValueIsInstanceStableAcrossOneEvaluation() throws Exception {
        EntryRep cand = new EntryRep(new Reading(25.0, "North"));
        EntryProjection p = EntryProjection.project(cand.bodyBytes(), ref("stationName"));
        assertFalse(p.isUndecodable());
        String cls = Reading.class.getName();
        CelValue v1 = p.fieldValue(cls, "stationName");
        CelValue v2 = p.fieldValue(cls, "stationName");
        assertSame("same CelValue instance across calls", v1, v2);
        assertSame("same underlying String instance (size() memo soundness)",
                ((CelValue.StringV) v1).value(), ((CelValue.StringV) v2).value());
    }
}
