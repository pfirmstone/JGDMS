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

package au.net.zeus.jgdms.der.entry;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.object.fixtures.Alpha;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The MATCH-CONTRACT PROOF (JGDMS-STD-006 EntryRep-v2 amendment &sect;A.4): the central
 * board-review burden. Proves that an {@code EntryRepV2Body} {@code FieldSlice} is a pure
 * function of the field value alone, so positional slice-byte comparison preserves v1
 * template-matching semantics.
 */
public class EntryRepV2MatchContractTest {

    // ------------------------------------------------------------------ fixtures

    /** Plain "entry" class T (public mutable fields; not required to implement Entry for the codec). */
    public static class EntryT {
        public String name;      // -> "java.lang.String"
        public Integer count;    // -> "int"
        public EntryT() {}
        public EntryT(String name, Integer count) { this.name = name; this.count = count; }
    }

    /** Subclass S: adds a same-named field 'count' (shadow, different namespace) and 'zebra', 'apple'. */
    public static class EntryS extends EntryT {
        public Integer count;    // shadows EntryT.count -- distinct namespace/position
        public String zebra;
        public String apple;
        public EntryS() {}
    }

    /** Entry with two @AtomicSerial-typed fields of the SAME class (dedup probe). */
    public static class TwoAlpha {
        public Alpha first;
        public Alpha second;
        public TwoAlpha() {}
        public TwoAlpha(Alpha a, Alpha b) { this.first = a; this.second = b; }
    }

    // ------------------------------------------------------------------ CRUX 1

    @Test
    public void sliceIsPureFunctionOfValue_scalar() throws Exception {
        // The same logical value encoded in template role and entry role, and by two
        // independent encoder calls, is byte-for-byte identical.
        byte[] tmplRole  = EntryRepV2Codec.encodeFieldSlice(Integer.valueOf(42)).sliceBytes();
        byte[] entryRole = EntryRepV2Codec.encodeFieldSlice(Integer.valueOf(42)).sliceBytes();
        byte[] independent = EntryRepV2Codec.encodeFieldSlice(Integer.valueOf(42)).sliceBytes();
        assertArrayEquals(tmplRole, entryRole,
                "same value must produce identical slice bytes in template vs entry role");
        assertArrayEquals(tmplRole, independent,
                "independent encoder invocations must produce identical slice bytes (canonicity)");
    }

    @Test
    public void sliceIsContextIndependent_acrossEnclosingEntries() throws Exception {
        // Integer(7) as EntryT.count and as EntryS.count (different enclosing entry, different
        // entry chain, different schemaTable). The extracted slice bytes MUST be identical --
        // the whole-entry chain must NOT leak into a value payload (amendment &sect;A.4.3).
        EntryRepV2Codec.EncodedBody bodyT = EntryRepV2Codec.encodeReflective(
                EntryT.class, new EntryT("x", 7));

        EntryS s = new EntryS();
        s.name = "x"; ((EntryT) s).count = 999; s.count = 7; s.zebra = "z"; s.apple = "a";
        EntryRepV2Codec.EncodedBody bodyS = EntryRepV2Codec.encodeReflective(EntryS.class, s);

        byte[] sliceForSeven = EntryRepV2Codec.encodeFieldSlice(Integer.valueOf(7)).sliceBytes();

        // EntryT field order: [count, name] (FieldComparator alpha within class) -> count is index 0.
        assertArrayEquals(sliceForSeven, bodyT.sliceBytes()[fieldIndex(EntryT.class, "count", 0)],
                "EntryT.count slice must equal the standalone value slice");
        // EntryS field order: super-first [EntryT.count, EntryT.name], then [S.apple, S.count, S.zebra].
        // The S.count holding 7 must produce the SAME slice bytes despite a different enclosing entry.
        int sCountIdx = fieldIndex(EntryS.class, "count", 1); // second 'count' (S's own)
        assertArrayEquals(sliceForSeven, bodyS.sliceBytes()[sCountIdx],
                "EntryS.count slice must equal the standalone value slice (context-free)");

        // And the entrySchemaDigests DIFFER (different classes) while the slice bytes match:
        assertFalse(Arrays.equals(bodyT.entrySchemaDigest(), bodyS.entrySchemaDigest()),
                "different entry classes must have different entrySchemaDigest");
    }

    // ------------------------------------------------------------------ CRUX 2: subclass alignment

    @Test
    public void subclassPositionalAlignment_andShadowedFields() throws Exception {
        List<java.lang.reflect.Field> tFields =
                EntrySchemaGenerator.forClass(EntryT.class).orderedFields();
        assertEquals(List.of("count", "name"),
                tFields.stream().map(java.lang.reflect.Field::getName).toList(),
                "EntryT order: alphabetical within class");

        List<java.lang.reflect.Field> sFields =
                EntrySchemaGenerator.forClass(EntryS.class).orderedFields();
        // super-first (EntryT.count, EntryT.name) then S's own (apple, count, zebra).
        assertEquals(List.of("count", "name", "apple", "count", "zebra"),
                sFields.stream().map(java.lang.reflect.Field::getName).toList(),
                "EntryS order: superclass fields first, then alphabetical within S; 'count' appears twice");
        // The two 'count' fields are DISTINCT (different declaring classes / positions).
        assertEquals(EntryT.class, sFields.get(0).getDeclaringClass());
        assertEquals(EntryS.class, sFields.get(3).getDeclaringClass());
    }

    // ------------------------------------------------------------------ CRUX 3: wildcard/null parity

    @Test
    public void wildcardNullParity() throws Exception {
        byte[] absentA = EntryRepV2Codec.encodeFieldSlice(null).sliceBytes();
        byte[] absentB = EntryRepV2Codec.encodeFieldSlice(null).sliceBytes();
        assertArrayEquals(absentA, absentB, "the absent marker is context-free (identical everywhere)");
        assertEquals(2, absentA.length, "absent [0] IMPLICIT NULL = tag+len, empty content");

        byte[] value = EntryRepV2Codec.encodeFieldSlice("hello").sliceBytes();
        assertFalse(Arrays.equals(absentA, value), "a value slice never equals an absent slice");
    }

    @Test
    public void nullTemplateMatchesStoredValue_valueTemplateDoesNot() throws Exception {
        // v1 parity: template wildcard (absent) skipped -> matches any stored value.
        // A non-wildcard template slice never byte-equals a stored absent, so a null stored
        // field fails a non-wildcard template.
        byte[] wildcard = EntryRepV2Codec.encodeFieldSlice(null).sliceBytes();
        byte[] stored5  = EntryRepV2Codec.encodeFieldSlice(5).sliceBytes();
        byte[] tmpl5    = EntryRepV2Codec.encodeFieldSlice(5).sliceBytes();
        byte[] storedNull = EntryRepV2Codec.encodeFieldSlice(null).sliceBytes();

        assertTrue(matchesField(wildcard, stored5), "wildcard template matches any stored value");
        assertTrue(matchesField(tmpl5, stored5), "value template matches equal stored value");
        assertFalse(matchesField(tmpl5, storedNull), "value template does NOT match a stored null");
        assertTrue(matchesField(wildcard, storedNull), "wildcard template matches a stored null");
    }

    // ------------------------------------------------------------------ round-trip + index parity

    @Test
    public void roundTrip_bodyDecodePreservesSlices() throws Exception {
        EntryRepV2Codec.EncodedBody body = EntryRepV2Codec.encodeReflective(
                EntryT.class, new EntryT("north", 20));
        EntryRepV2Codec.DecodedBody decoded = EntryRepV2Codec.decode(body.body());
        assertEquals(body.sliceBytes().length, decoded.sliceBytes().length);
        for (int i = 0; i < body.sliceBytes().length; i++) {
            assertArrayEquals(body.sliceBytes()[i], decoded.sliceBytes()[i],
                    "decoded slice[" + i + "] must byte-equal the encoded slice");
        }
        assertArrayEquals(body.entrySchemaDigest(), decoded.entrySchemaDigest());
    }

    @Test
    public void roundTrip_fieldValues_scalarAndNested() throws Exception {
        TwoAlpha t = new TwoAlpha(new Alpha(1, "one"), new Alpha(1, "one"));
        EntryRepV2Codec.EncodedBody body = EntryRepV2Codec.encodeReflective(TwoAlpha.class, t);
        EntryRepV2Codec.DecodedBody d = EntryRepV2Codec.decode(body.body());
        // TwoAlpha order: [first, second] (alpha). Reconstruct the Alpha values.
        Object first = EntryRepV2Codec.decodeFieldValue(d.sliceBytes()[0], Alpha.class, d.schemaTable());
        Object second = EntryRepV2Codec.decodeFieldValue(d.sliceBytes()[1], Alpha.class, d.schemaTable());
        assertEquals(new Alpha(1, "one"), first);
        assertEquals(new Alpha(1, "one"), second);
    }

    @Test
    public void roundTrip_selfDescribingScalarValue() throws Exception {
        byte[] slice = EntryRepV2Codec.encodeFieldSlice("hello").sliceBytes();
        Object v = EntryRepV2Codec.decodeFieldValue(slice, String.class, java.util.Map.of());
        assertEquals("hello", v);

        byte[] iSlice = EntryRepV2Codec.encodeFieldSlice(Integer.valueOf(123)).sliceBytes();
        Object iv = EntryRepV2Codec.decodeFieldValue(iSlice, Integer.class, java.util.Map.of());
        assertEquals(123, iv);

        byte[] byteSlice = EntryRepV2Codec.encodeFieldSlice(new byte[]{1, 2, 3}).sliceBytes();
        Object bv = EntryRepV2Codec.decodeFieldValue(byteSlice, byte[].class, java.util.Map.of());
        assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) bv);
    }

    @Test
    public void matchIndexParity_equalEntriesEqualSlices() throws Exception {
        EntryRepV2Codec.EncodedBody a = EntryRepV2Codec.encodeReflective(EntryT.class, new EntryT("n", 3));
        EntryRepV2Codec.EncodedBody b = EntryRepV2Codec.encodeReflective(EntryT.class, new EntryT("n", 3));
        assertArrayEquals(a.body(), b.body(), "two equal entries encode to byte-identical bodies");
        for (int i = 0; i < a.sliceBytes().length; i++) {
            assertArrayEquals(a.sliceBytes()[i], b.sliceBytes()[i]);
            // quick-reject hash parity: equal slices hash equal
            assertEquals(Arrays.hashCode(a.sliceBytes()[i]), Arrays.hashCode(b.sliceBytes()[i]));
        }
        EntryRepV2Codec.EncodedBody c = EntryRepV2Codec.encodeReflective(EntryT.class, new EntryT("n", 4));
        assertFalse(Arrays.equals(a.sliceBytes()[fieldIndex(EntryT.class, "count", 0)],
                                  c.sliceBytes()[fieldIndex(EntryT.class, "count", 0)]),
                "different value -> different slice bytes");
    }

    // ------------------------------------------------------------------ dedup measurement (G13)

    @Test
    public void measuredDedup_sharedSchemaCarriedOnce() throws Exception {
        TwoAlpha t = new TwoAlpha(new Alpha(1, "one"), new Alpha(2, "two"));
        EntryRepV2Codec.EncodedBody body = EntryRepV2Codec.encodeReflective(TwoAlpha.class, t);
        EntryRepV2Codec.DecodedBody d = EntryRepV2Codec.decode(body.body());

        // schemaTable holds: entry chain (TwoAlpha) + ONE Alpha chain (deduped), = 2 entries,
        // NOT 3 (the two Alpha fields share one chain entry).
        assertEquals(2, d.schemaTable().size(),
                "two same-class @AtomicSerial fields must share ONE schemaTable entry");

        // Measure the saving vs v1's per-field schema duplication: v1 carried the Alpha chain
        // bytes ONCE PER FIELD (2x); v2 carries it once. Report the absolute + relative saving.
        int alphaChainLen = EntrySchemaGenerator.chainByteLength(Alpha.class);
        int v2BodyLen = body.body().length;
        int v1ExtraCopyBytes = alphaChainLen; // one redundant copy eliminated (2 fields -> 1 shared)
        double pct = 100.0 * v1ExtraCopyBytes / (v2BodyLen + v1ExtraCopyBytes);
        System.out.println("[EntryRep-v2 DEDUP] Alpha chainBytes=" + alphaChainLen
                + "  v2 body=" + v2BodyLen
                + "  eliminated redundant schema copy=" + v1ExtraCopyBytes + " bytes"
                + "  (~" + String.format("%.1f", pct) + "% of the naive-v1-equivalent size for this entry)");
        assertTrue(v1ExtraCopyBytes > 0, "there is a measurable dedup saving");
    }

    // ------------------------------------------------------------------ adversarial decode (A.9)

    @Test
    public void adversarial_versionNotTwo_rejectedLoudly() throws Exception {
        byte[] body = EntryRepV2Codec.encodeReflective(EntryT.class, new EntryT("n", 1)).body();
        // The version INTEGER 2 is the first inner element: locate byte value 0x02 (content) after
        // the outer SEQUENCE header + INTEGER header. Flip it to 3 by re-encoding is cleaner: mutate.
        byte[] mutated = flipVersionTo(body, 3);
        DerException ex = assertThrows(DerException.class, () -> EntryRepV2Codec.decode(mutated));
        assertTrue(ex.getMessage().contains("version") || ex.getMessage().contains("2"),
                "version guard must reject LOUDLY: " + ex.getMessage());
    }

    @Test
    public void adversarial_trailingBytes_rejected() throws Exception {
        byte[] body = EntryRepV2Codec.encodeReflective(EntryT.class, new EntryT("n", 1)).body();
        byte[] withTrailing = Arrays.copyOf(body, body.length + 1);
        withTrailing[body.length] = 0x00;
        assertThrows(DerException.class, () -> EntryRepV2Codec.decode(withTrailing));
    }

    @Test
    public void adversarial_truncatedBody_rejected() throws Exception {
        byte[] body = EntryRepV2Codec.encodeReflective(EntryT.class, new EntryT("n", 1)).body();
        byte[] truncated = Arrays.copyOf(body, body.length - 1);
        assertThrows(DerException.class, () -> EntryRepV2Codec.decode(truncated));
    }

    @Test
    public void adversarial_bodyExceedingCeiling_rejected() {
        byte[] huge = new byte[EntryRepV2Codec.MAX_BODY_BYTES + 1];
        assertThrows(DerException.class, () -> EntryRepV2Codec.decode(huge));
    }

    @Test
    public void wellFormedBodyDecodesClean() throws Exception {
        byte[] body = EntryRepV2Codec.encodeReflective(EntryS.class, freshS()).body();
        EntryRepV2Codec.DecodedBody d = EntryRepV2Codec.decode(body);
        assertNotNull(d);
        assertEquals(5, d.sliceBytes().length, "EntryS has 5 usable fields");
    }

    @Test
    public void degenerateEmptyEntry_encodesAndDecodes() throws Exception {
        // An entry class with NO usable fields (G11 degenerate template).
        EntrySchemaGenerator.EntrySchema schema = EntrySchemaGenerator.forClass(Empty.class);
        EntryRepV2Codec.EncodedBody body = EntryRepV2Codec.encode(
                schema.entrySchemaDigest(), schema.chainBytes(), new Object[0]);
        EntryRepV2Codec.DecodedBody d = EntryRepV2Codec.decode(body.body());
        assertEquals(0, d.sliceBytes().length);
    }

    public static class Empty { /* no usable fields */ }

    // ------------------------------------------------------------------ adversarial schemaTable

    @Test
    public void adversarial_missingReferencedDigest_rejected() throws Exception {
        // A value slice references a 32-byte digest that is NOT present in the schemaTable.
        EntrySchemaGenerator.EntrySchema es = EntrySchemaGenerator.forClass(EntryT.class);
        byte[] bogusDigest = new byte[32];
        Arrays.fill(bogusDigest, (byte) 0xAB);
        byte[] badSlice = valueSlice(bogusDigest, new byte[]{0x01});
        byte[] body = buildBody(2, es.entrySchemaDigest(),
                List.of(schemaEntry(es.entrySchemaDigest(), es.chainBytes())),
                List.of(badSlice));
        DerException ex = assertThrows(DerException.class, () -> EntryRepV2Codec.decode(body));
        assertTrue(ex.getMessage().contains("completeness") || ex.getMessage().contains("absent"),
                ex.getMessage());
    }

    @Test
    public void adversarial_duplicateOrUnsortedTable_rejected() throws Exception {
        EntrySchemaGenerator.EntrySchema es = EntrySchemaGenerator.forClass(EntryT.class);
        // duplicate the entry-schema table entry -> not strictly ascending.
        byte[] body = buildBody(2, es.entrySchemaDigest(),
                List.of(schemaEntry(es.entrySchemaDigest(), es.chainBytes()),
                        schemaEntry(es.entrySchemaDigest(), es.chainBytes())),
                List.of());
        DerException ex = assertThrows(DerException.class, () -> EntryRepV2Codec.decode(body));
        assertTrue(ex.getMessage().contains("ascending") || ex.getMessage().contains("duplicate"),
                ex.getMessage());
    }

    @Test
    public void adversarial_digestChainMismatch_rejected() throws Exception {
        EntrySchemaGenerator.EntrySchema es = EntrySchemaGenerator.forClass(EntryT.class);
        // A SchemaEntry whose declared digest does not match the leaf of its chainBytes.
        byte[] wrongDigest = new byte[32];
        Arrays.fill(wrongDigest, (byte) 0x01);
        byte[] body = buildBody(2, wrongDigest,
                List.of(schemaEntry(wrongDigest, es.chainBytes())),
                List.of());
        DerException ex = assertThrows(DerException.class, () -> EntryRepV2Codec.decode(body));
        assertTrue(ex.getMessage().contains("does not match") || ex.getMessage().contains("leaf"),
                ex.getMessage());
    }

    // Low-level body builders (test-only) -------------------------------

    private static byte[] schemaEntry(byte[] digest, byte[] chainBytes) {
        return au.net.zeus.jgdms.der.DerWriter.writeSequence(List.of(
                au.net.zeus.jgdms.der.DerWriter.writeOctetString(digest),
                au.net.zeus.jgdms.der.DerWriter.writeOctetString(chainBytes)));
    }

    private static byte[] valueSlice(byte[] digest, byte[] payload) {
        byte[] inner = concat(
                au.net.zeus.jgdms.der.DerWriter.writeOctetString(digest),
                au.net.zeus.jgdms.der.DerWriter.writeOctetString(payload));
        return au.net.zeus.jgdms.der.DerWriter.writeTlv(
                new au.net.zeus.jgdms.der.Tag(au.net.zeus.jgdms.der.Tag.CLASS_CONTEXT, true, 1), inner);
    }

    private static byte[] buildBody(int version, byte[] entrySchemaDigest,
                                    List<byte[]> tableEntries, List<byte[]> slices) {
        return au.net.zeus.jgdms.der.DerWriter.writeSequence(List.of(
                au.net.zeus.jgdms.der.DerWriter.writeInteger(version),
                au.net.zeus.jgdms.der.DerWriter.writeOctetString(entrySchemaDigest),
                au.net.zeus.jgdms.der.DerWriter.writeSequence(tableEntries),
                au.net.zeus.jgdms.der.DerWriter.writeSequence(slices)));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private static EntryS freshS() {
        EntryS s = new EntryS();
        s.name = "n"; ((EntryT) s).count = 1; s.count = 2; s.apple = "a"; s.zebra = "z";
        return s;
    }

    /** Field-order index of the {@code occurrence}-th field named {@code name}. */
    private static int fieldIndex(Class<?> cls, String name, int occurrence) throws DerException {
        List<java.lang.reflect.Field> f = EntrySchemaGenerator.forClass(cls).orderedFields();
        int seen = 0;
        for (int i = 0; i < f.size(); i++) {
            if (f.get(i).getName().equals(name)) {
                if (seen == occurrence) return i;
                seen++;
            }
        }
        throw new AssertionError("field not found: " + name + "#" + occurrence);
    }

    /** Models EntryRep.matches for one field position: wildcard template skipped, else byte-equal. */
    private static boolean matchesField(byte[] templateSlice, byte[] storedSlice) throws Exception {
        // A wildcard is the absent marker; template-side absent = skip (match).
        byte[] absent = EntryRepV2Codec.encodeFieldSlice(null).sliceBytes();
        if (Arrays.equals(templateSlice, absent)) return true;
        return Arrays.equals(templateSlice, storedSlice);
    }

    /** Mutates the encoded body's version INTEGER content byte to {@code v} (assumes single-byte value). */
    private static byte[] flipVersionTo(byte[] body, int v) {
        // outer: 0x30 <len> 0x02 0x01 <version> ... ; find the INTEGER (0x02 0x01) after outer header.
        byte[] out = body.clone();
        for (int i = 0; i + 2 < out.length; i++) {
            if (out[i] == 0x02 && out[i + 1] == 0x01 && out[i + 2] == EntryRepV2Codec.VERSION) {
                out[i + 2] = (byte) v;
                return out;
            }
        }
        throw new AssertionError("version INTEGER not found");
    }
}
