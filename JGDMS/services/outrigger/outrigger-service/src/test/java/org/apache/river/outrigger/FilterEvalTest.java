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
    public void allApplicableMustPass() throws Exception {
        EntryRep cand = new EntryRep(new Reading(25.0, "North"));
        // Two schema-less filters: one true, one false => all-must-pass => excluded.
        CompiledFilter t = schemaLess(
                new ExprNode.Gt(ref("temperatureCelsius"), new ExprNode.LitDouble(20.0)));
        CompiledFilter f = schemaLess(
                new ExprNode.Eq(ref("stationName"), new ExprNode.LitString("South")));
        FilterSet fs = new FilterSet.Builder().add(t).add(f).build();
        assertFalse(FilterEval.matches(fs, cand));
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
    public void ambiguousMultiSchemaSubclassFailsClosed() throws Exception {
        // Two concrete filters of DISTINCT schemas + a subclass candidate whose
        // digest matches neither and no matched-template hint => fail-closed (never
        // a guess, never a fail-open).
        EntryRep other = new EntryRep(new Reading(1.0, "z"));
        byte[] d2 = other.entrySchemaDigest().clone();
        d2[0] ^= 0x5A; // a different, non-matching digest
        CompiledFilter f1 = concrete(warmNorth(), other.entrySchemaDigest());
        CompiledFilter f2 = concrete(warmNorth(), d2);
        FilterSet fs = new FilterSet.Builder().add(f1).add(f2).build();
        EntryRep sub = new EntryRep(new DetailedReading(25.0, "North", "s1"));
        long fc = metric("filter.failClosedExclusions");
        assertFalse(FilterEval.matches(fs, sub));
        assertEquals(fc + 1, metric("filter.failClosedExclusions"));
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
