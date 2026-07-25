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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

import net.jini.core.entry.Entry;

import org.apache.river.outrigger.proxy.EntryRep;
import org.apache.river.outrigger.proxy.FilterEnvelope;
import net.jini.space.FilterRejectedException;

import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.authoring.CelEncoder;
import au.net.zeus.jgdms.cel.authoring.CelRecordBuilder;
import au.net.zeus.jgdms.cel.wire.ScalarType;

/**
 * The server-side CEL filter admission seam (SOW Part B, unit B1): the
 * verify-reject matrix, Transform rejection, schema-less full scan, accept, and
 * the unwired-chokepoint loud reject. Every rejection must fail LOUDLY with the
 * mapped {@link FilterRejectedException.Reason}.
 */
public class FilterAdmissionTest {

    /** Two-field test entry; canonical field order a, b (both String -> STRING). */
    public static class Doc implements Entry {
        public String a;
        public String b;
        public Doc() {}
        public Doc(String a, String b) { this.a = a; this.b = b; }
    }

    /** A differently-shaped entry: single String field {@code z}, and crucially
     *  NO field named {@code a}. A filter referencing {@code a} therefore DEFERS
     *  (UNKNOWN) against this schema and is admitted, whereas against {@link Doc}
     *  (whose {@code a} is a STRING) a numeric comparison on {@code a} is a loud
     *  STATIC_TYPE_MISMATCH. */
    public static class Other implements Entry {
        public String z;
        public Other() {}
        public Other(String z) { this.z = z; }
    }

    private static ExprNode fieldRef(String name) {
        return new ExprNode.FieldRef(
                Collections.<ExprNode.SelectorStep>singletonList(
                        new ExprNode.SelectorStep.Unqual(name)));
    }

    private static byte[] envelopeOfPredicate(ExprNode node) throws Exception {
        byte[] celWire = CelEncoder.encode(CelRecordBuilder.predicate(node));
        return FilterEnvelope.encode(celWire);
    }

    private static byte[] envelopeOf(byte[] celWire) {
        return FilterEnvelope.encode(celWire);
    }

    private EntryRep tmpl(Entry e) throws Exception {
        return new EntryRep(e);
    }

    private static void assertRejected(FilterRejectedException.Reason expected, Admit body) {
        try {
            body.run();
            fail("expected FilterRejectedException(" + expected + ")");
        } catch (FilterRejectedException e) {
            assertEquals(expected, e.reason());
        } catch (Exception other) {
            fail("expected FilterRejectedException(" + expected + "), got " + other);
        }
    }

    private interface Admit { void run() throws Exception; }

    // ---- Accept paths ---------------------------------------------------

    @Test
    public void admitsWellTypedPredicateAgainstConcreteTemplate() throws Exception {
        // a == "x" : STRING == STRING -> bool. Type-checks against Doc's schema.
        byte[] env = envelopeOfPredicate(
                new ExprNode.Eq(fieldRef("a"), new ExprNode.LitString("x")));
        CompiledFilter cf = FilterAdmission.admit(env, tmpl(new Doc("x", "y")));
        assertNotNull(cf);
        assertNotNull(cf.expr());
        assertFalse(cf.isSchemaLess());
        assertNotNull(cf.applicabilitySchemaDigest());
        // Applicability key is the template's own entrySchemaDigest.
        assertEquals(32, cf.applicabilitySchemaDigest().length);
    }

    @Test
    public void nullTemplateAdmitsSchemaLessFullScan() throws Exception {
        byte[] env = envelopeOfPredicate(
                new ExprNode.Eq(new ExprNode.LitInt(1), new ExprNode.LitInt(1)));
        CompiledFilter cf = FilterAdmission.admit(env, null);
        assertNotNull(cf);
        assertTrue(cf.isSchemaLess());
        assertNull(cf.applicabilitySchemaDigest());
    }

    @Test
    public void matchAnyTemplateAdmitsSchemaLess() throws Exception {
        byte[] env = envelopeOfPredicate(
                new ExprNode.Eq(new ExprNode.LitInt(1), new ExprNode.LitInt(1)));
        CompiledFilter cf = FilterAdmission.admit(env, EntryRep.matchAnyEntryRep());
        assertTrue(cf.isSchemaLess());
    }

    @Test
    public void fieldReferencingPredicateAgainstNullTemplateIsAdmittedSchemaLess() throws Exception {
        // OPTION (b): a null / match-any template has no schema to type-check
        // against, so field references DEFER (the verifier soundly treats an
        // unknown field type as UNKNOWN, never a static rejection). The filter
        // IS admitted schema-lessly and MAY reference fields; per-candidate
        // resolution (and fail-closed exclusion for a missing field) is B3.
        byte[] env = envelopeOfPredicate(
                new ExprNode.Eq(fieldRef("a"), new ExprNode.LitString("x")));
        CompiledFilter cf = FilterAdmission.admit(env, null);
        assertNotNull(cf);
        assertTrue("schema-less must NOT mean 'references no fields'", cf.isSchemaLess());
        assertNull(cf.applicabilitySchemaDigest());
    }

    @Test
    public void unknownFieldNameAgainstConcreteTemplateIsAdmitted() throws Exception {
        // Opus F2 safe direction: a reference to a field NAME absent from the
        // template's schema DEFERS (UNKNOWN), so the filter is admitted and the
        // candidate that lacks the field is fail-closed-excluded at eval (B3) --
        // NOT loudly rejected at admission. Contrast rejectsStaticTypeMismatch:
        // a WRONG TYPE on an EXISTING field IS loudly rejected.
        byte[] env = envelopeOfPredicate(
                new ExprNode.Eq(fieldRef("nonexistentField"), new ExprNode.LitString("x")));
        CompiledFilter cf = FilterAdmission.admit(env, tmpl(new Doc("x", "y")));
        assertNotNull(cf);
        assertFalse(cf.isSchemaLess());
        assertNotNull(cf.applicabilitySchemaDigest());
    }

    // ---- Verify-reject matrix ------------------------------------------

    @Test
    public void rejectsMalformedEnvelope() throws Exception {
        assertRejected(FilterRejectedException.Reason.ENVELOPE_MALFORMED,
                () -> FilterAdmission.admit(new byte[] { 0x31, 0x00 }, tmpl(new Doc("x", "y"))));
    }

    @Test
    public void rejectsNullEnvelope() throws Exception {
        // Cast disambiguates the byte[] overload from admit(PreparedFilter, ...);
        // this asserts the byte[]-envelope path rejects a null envelope loudly.
        assertRejected(FilterRejectedException.Reason.ENVELOPE_MALFORMED,
                () -> FilterAdmission.admit((byte[]) null, tmpl(new Doc("x", "y"))));
    }

    @Test
    public void rejectsDecodeFailure() throws Exception {
        // Well-formed envelope wrapping garbage (non-decodable) CEL wire bytes.
        byte[] env = envelopeOf(new byte[] { 0x30, 0x02, (byte) 0xFF, (byte) 0xFF });
        assertRejected(FilterRejectedException.Reason.FILTER_DECODE_REJECTED,
                () -> FilterAdmission.admit(env, tmpl(new Doc("x", "y"))));
    }

    @Test
    public void rejectsStaticTypeMismatch() throws Exception {
        // a > 5 : STRING > INT -> static type mismatch against Doc's schema.
        byte[] env = envelopeOfPredicate(
                new ExprNode.Gt(fieldRef("a"), new ExprNode.LitInt(5)));
        assertRejected(FilterRejectedException.Reason.FILTER_TYPE_MISMATCH,
                () -> FilterAdmission.admit(env, tmpl(new Doc("x", "y"))));
    }

    @Test
    public void rejectsResultTypeMismatch() throws Exception {
        // Transform whose expression is bool but declared result type is STRING.
        ExprNode boolExpr = new ExprNode.Eq(new ExprNode.LitInt(1), new ExprNode.LitInt(1));
        byte[] celWire = CelEncoder.encode(
                CelRecordBuilder.transformScalar(boolExpr, ScalarType.STRING_T));
        byte[] env = envelopeOf(celWire);
        assertRejected(FilterRejectedException.Reason.FILTER_RESULT_TYPE_MISMATCH,
                () -> FilterAdmission.admit(env, tmpl(new Doc("x", "y"))));
    }

    @Test
    public void rejectsTransformContext() throws Exception {
        // A well-typed Transform (string literal -> STRING) verifies, but only a
        // Predicate may gate a query -> NOT_A_PREDICATE.
        byte[] celWire = CelEncoder.encode(
                CelRecordBuilder.transformScalar(new ExprNode.LitString("x"), ScalarType.STRING_T));
        byte[] env = envelopeOf(celWire);
        assertRejected(FilterRejectedException.Reason.NOT_A_PREDICATE,
                () -> FilterAdmission.admit(env, tmpl(new Doc("x", "y"))));
    }

    @Test
    public void rejectsUnresolvableTemplateSchema() throws Exception {
        // A template whose body is not a valid v2 EntryRep body -> the filter
        // cannot be type-checked and is refused (fail-closed), never admitted.
        EntryRep badBody = new EntryRep() {
            @Override public byte[] bodyBytes() { return new byte[] { 1, 2, 3, 4, 5 }; }
        };
        byte[] env = envelopeOfPredicate(
                new ExprNode.Eq(fieldRef("a"), new ExprNode.LitString("x")));
        assertRejected(FilterRejectedException.Reason.SCHEMA_UNAVAILABLE,
                () -> FilterAdmission.admit(env, badBody));
    }

    // ---- Unwired-chokepoint loud reject (B1 stops at "a verified filter exists")

    @Test
    public void evaluationNotWiredIsLoud() throws Exception {
        byte[] env = envelopeOfPredicate(
                new ExprNode.Eq(fieldRef("a"), new ExprNode.LitString("x")));
        CompiledFilter cf = FilterAdmission.admit(env, tmpl(new Doc("x", "y")));
        FilterRejectedException e = FilterAdmission.evaluationNotWired("read", cf);
        assertEquals(FilterRejectedException.Reason.EVALUATION_NOT_WIRED, e.reason());
    }

    /** The unwired-chokepoint loud reject is op-name agnostic: the two multi-
     *  template query ops folded into B1 (filtered contents + filtered bulk take)
     *  reach the same EVALUATION_NOT_WIRED break as the single-entry ops. */
    @Test
    public void evaluationNotWiredIsLoudForContentsAndBulkTake() throws Exception {
        byte[] env = envelopeOfPredicate(
                new ExprNode.Eq(fieldRef("a"), new ExprNode.LitString("x")));
        CompiledFilter cf = FilterAdmission.admit(env, tmpl(new Doc("x", "y")));
        assertEquals(FilterRejectedException.Reason.EVALUATION_NOT_WIRED,
                FilterAdmission.evaluationNotWired("contents", cf).reason());
        assertEquals(FilterRejectedException.Reason.EVALUATION_NOT_WIRED,
                FilterAdmission.evaluationNotWired("take<multiple>", cf).reason());
    }

    // ---- Multi-template admission (one filter, "all templates must pass") ----
    //
    // The filtered contents and bulk-take ops are MULTI-template with a SINGLE
    // filter. OutriggerServerImpl admits the one filter against EACH template's
    // own schema and rejects the whole op if admission fails against any of them.
    // These tests exercise that loop at the seam it is built from.

    /** Mirrors the server's fixed per-template admission loop: decode the
     *  (template-invariant) envelope ONCE via {@link FilterAdmission#prepare},
     *  then admit that one prepared filter against each template in turn; any
     *  rejection fails the whole op. Returns the last admitted filter (matching
     *  the impl, which then throws EVALUATION_NOT_WIRED). */
    private CompiledFilter admitAgainstEach(byte[] env, EntryRep... tmpls)
            throws FilterRejectedException {
        FilterAdmission.PreparedFilter prepared = FilterAdmission.prepare(env);
        CompiledFilter cf = null;
        for (EntryRep t : tmpls) {
            cf = FilterAdmission.admit(prepared, t);
        }
        return cf;
    }

    @Test
    public void multiTemplateAdmitsWhenFilterTypeChecksAgainstEverySchema() throws Exception {
        // a == "x" : STRING == STRING -> bool. Type-checks against every Doc
        // template, so admission against each succeeds.
        byte[] env = envelopeOfPredicate(
                new ExprNode.Eq(fieldRef("a"), new ExprNode.LitString("x")));
        CompiledFilter cf = admitAgainstEach(env, tmpl(new Doc("x", "y")), tmpl(new Doc("p", "q")));
        assertNotNull(cf);
        assertFalse(cf.isSchemaLess());
    }

    @Test
    public void multiTemplateRejectsWhenFilterFailsAnyOneSchema() throws Exception {
        // a > 5 DEFERS against Other (no field 'a' -> UNKNOWN -> admitted) but is
        // a loud STATIC_TYPE_MISMATCH against Doc (a:STRING). "All templates must
        // pass" => admitting against Other first does NOT let the op through; the
        // Doc template rejects it loudly. It is never admitted schema-lessly and
        // never downgraded to an unfiltered query.
        byte[] env = envelopeOfPredicate(
                new ExprNode.Gt(fieldRef("a"), new ExprNode.LitInt(5)));
        // Sanity: the filter really is admitted against Other on its own.
        assertNotNull(FilterAdmission.admit(env, tmpl(new Other("z"))));
        assertRejected(FilterRejectedException.Reason.FILTER_TYPE_MISMATCH,
                () -> admitAgainstEach(env, tmpl(new Other("z")), tmpl(new Doc("x", "y"))));
    }

    @Test
    public void multiTemplateRejectsNullEnvelopeAgainstEverySchema() throws Exception {
        // A null filter is invalid on the filtered multi-template ops (unfiltered
        // callers use ordinary JavaSpace05); admission fails closed on the first
        // template.
        assertRejected(FilterRejectedException.Reason.ENVELOPE_MALFORMED,
                () -> admitAgainstEach(null, tmpl(new Doc("x", "y")), tmpl(new Other("z"))));
    }

    @Test
    public void multiTemplateRejectsMalformedEnvelopeAgainstEverySchema() throws Exception {
        assertRejected(FilterRejectedException.Reason.ENVELOPE_MALFORMED,
                () -> admitAgainstEach(new byte[] { 0x31, 0x00 },
                        tmpl(new Doc("x", "y")), tmpl(new Other("z"))));
    }

    // ---- Multi-template admission ceiling (MAX_TEMPLATES, DoS defence) ----
    //
    // The three multi-template filtered ops admit the ONE filter against EACH
    // template, so an unbounded template array is an admission-amplification
    // vector. checkTemplateCount() is the shared fail-closed ceiling every one of
    // them calls before its loop.
    //
    // TEST-SCOPE GAP (board finding, recorded honestly): these tests exercise
    // checkTemplateCount() directly, NOT the three real OutriggerServerImpl ops.
    // OutriggerServerImpl is a full activatable server (ActivationID/LifeCycle
    // ctor) and is not instantiable in unit scope, so nothing here would fail if
    // the checkTemplateCount call were dropped from an op. That server-call-site
    // wiring is verified by the qa deployment matching suite + code review, not by
    // this unit test. See memory: grep-count != verify / mirror != real path.

    @Test
    public void checkTemplateCountAdmitsAtAndBelowMax() throws Exception {
        // The boundary and below must NOT throw.
        FilterAdmission.checkTemplateCount(0);
        FilterAdmission.checkTemplateCount(1);
        FilterAdmission.checkTemplateCount(FilterAdmission.MAX_TEMPLATES);
    }

    @Test
    public void checkTemplateCountRejectsAboveMaxLoudly() {
        // A breach is a loud FilterRejectedException(TEMPLATE_COUNT_EXCEEDED) --
        // uniform with every other filter rejection on the same op signatures.
        assertRejected(FilterRejectedException.Reason.TEMPLATE_COUNT_EXCEEDED,
                () -> FilterAdmission.checkTemplateCount(FilterAdmission.MAX_TEMPLATES + 1));
    }

    @Test
    public void checkTemplateCountRejectsGrosslyOversizedArray() {
        // The concrete attack the board flagged: ~10^4 templates.
        assertRejected(FilterRejectedException.Reason.TEMPLATE_COUNT_EXCEEDED,
                () -> FilterAdmission.checkTemplateCount(10_000));
    }

    @Test
    public void templateCountRejectionIsCounted() throws Exception {
        // The DoS defence must be observable: a breach increments the operator
        // metric, so the flood this guard exists to stop is not invisible.
        long[] before = FilterAdmission.metrics();
        int slot = indexOf(FilterAdmission.METRIC_NAMES, "filter.rejected.templateCount");
        assertTrue("metric slot must exist", slot >= 0);
        try {
            FilterAdmission.checkTemplateCount(FilterAdmission.MAX_TEMPLATES + 1);
            fail("expected TEMPLATE_COUNT_EXCEEDED");
        } catch (FilterRejectedException expected) {
            // expected
        }
        long[] after = FilterAdmission.metrics();
        assertEquals("templateCount rejection must be counted",
                before[slot] + 1, after[slot]);
    }

    private static int indexOf(String[] names, String name) {
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(name)) return i;
        }
        return -1;
    }

    // ---- Decode-once seam (prepare + admit(PreparedFilter, EntryRep)) -----
    //
    // The envelope is identical across every template, so it is decoded ONCE via
    // prepare() and the resulting PreparedFilter is reused across the loop. The
    // decode-once path must be behaviourally identical to admit(byte[], tmpl).

    @Test
    public void prepareThenAdmitEqualsSingleShotAdmit() throws Exception {
        byte[] env = envelopeOfPredicate(
                new ExprNode.Eq(fieldRef("a"), new ExprNode.LitString("x")));
        EntryRep t = tmpl(new Doc("x", "y"));

        CompiledFilter oneShot = FilterAdmission.admit(env, t);
        FilterAdmission.PreparedFilter prepared = FilterAdmission.prepare(env);
        CompiledFilter viaPrepared = FilterAdmission.admit(prepared, t);

        assertNotNull(viaPrepared);
        assertEquals(oneShot.isSchemaLess(), viaPrepared.isSchemaLess());
        // Same template ⇒ same applicability key on both admission paths.
        assertArrayEquals(oneShot.applicabilitySchemaDigest(),
                viaPrepared.applicabilitySchemaDigest());
    }

    @Test
    public void prepareOnceReusedAcrossManyTemplates() throws Exception {
        // One prepared envelope admits against each of many templates without
        // re-decoding the envelope — exactly the server's post-fix loop.
        byte[] env = envelopeOfPredicate(
                new ExprNode.Eq(fieldRef("a"), new ExprNode.LitString("x")));
        FilterAdmission.PreparedFilter prepared = FilterAdmission.prepare(env);
        for (int i = 0; i < 5; i++) {
            CompiledFilter cf = FilterAdmission.admit(prepared, tmpl(new Doc("x" + i, "y")));
            assertNotNull(cf);
            assertFalse(cf.isSchemaLess());
        }
    }

    @Test
    public void preparedReuseStillRejectsTypeMismatchTemplateLoudly() throws Exception {
        // Reusing a PreparedFilter must NOT weaken the "every template must pass"
        // contract: a > 5 defers against Other (no field 'a') but is a loud
        // STATIC_TYPE_MISMATCH against Doc (a:STRING) — even via the reused token,
        // never downgraded to unfiltered.
        byte[] env = envelopeOfPredicate(
                new ExprNode.Gt(fieldRef("a"), new ExprNode.LitInt(5)));
        FilterAdmission.PreparedFilter prepared = FilterAdmission.prepare(env);
        assertNotNull(FilterAdmission.admit(prepared, tmpl(new Other("z"))));
        assertRejected(FilterRejectedException.Reason.FILTER_TYPE_MISMATCH,
                () -> FilterAdmission.admit(prepared, tmpl(new Doc("x", "y"))));
    }

    @Test
    public void prepareRejectsNullEnvelopeLoudly() {
        assertRejected(FilterRejectedException.Reason.ENVELOPE_MALFORMED,
                () -> FilterAdmission.prepare(null));
    }

    @Test
    public void prepareRejectsMalformedEnvelopeLoudly() {
        assertRejected(FilterRejectedException.Reason.ENVELOPE_MALFORMED,
                () -> FilterAdmission.prepare(new byte[] { 0x31, 0x00 }));
    }

    @Test
    public void admitRejectsNullPreparedFilter() {
        try {
            FilterAdmission.admit((FilterAdmission.PreparedFilter) null,
                    EntryRep.matchAnyEntryRep());
            fail("expected NullPointerException for a null PreparedFilter");
        } catch (NullPointerException expected) {
            // expected — a null prepared token is a programming error.
        } catch (FilterRejectedException e) {
            fail("expected NullPointerException, got " + e);
        }
    }

    // ---- Observability --------------------------------------------------

    @Test
    public void metricsSnapshotHasStableShape() {
        long[] m = FilterAdmission.metrics();
        assertEquals(FilterAdmission.METRIC_NAMES.length, m.length);
    }

    @Test
    public void applicabilityDigestIsDefensiveCopy() throws Exception {
        byte[] env = envelopeOfPredicate(
                new ExprNode.Eq(fieldRef("a"), new ExprNode.LitString("x")));
        CompiledFilter cf = FilterAdmission.admit(env, tmpl(new Doc("x", "y")));
        byte[] d1 = cf.applicabilitySchemaDigest();
        d1[0] ^= 0xFF;
        assertFalse(java.util.Arrays.equals(d1, cf.applicabilitySchemaDigest()));
    }
}
