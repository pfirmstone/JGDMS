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
import org.apache.river.outrigger.proxy.FilterRejectedException;

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

    // ---- Verify-reject matrix ------------------------------------------

    @Test
    public void rejectsMalformedEnvelope() throws Exception {
        assertRejected(FilterRejectedException.Reason.ENVELOPE_MALFORMED,
                () -> FilterAdmission.admit(new byte[] { 0x31, 0x00 }, tmpl(new Doc("x", "y"))));
    }

    @Test
    public void rejectsNullEnvelope() throws Exception {
        assertRejected(FilterRejectedException.Reason.ENVELOPE_MALFORMED,
                () -> FilterAdmission.admit(null, tmpl(new Doc("x", "y"))));
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
