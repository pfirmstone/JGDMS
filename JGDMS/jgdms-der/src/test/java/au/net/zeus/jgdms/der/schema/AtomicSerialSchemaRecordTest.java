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

package au.net.zeus.jgdms.der.schema;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AtomicSerialSchemaRecord} and {@link SchemaChain} -- Phase 2 Tasks 2.1 and 2.2.
 *
 * <h3>Task 2.1 coverage</h3>
 * <ul>
 *   <li>2.1-a  Round-trip without parentSchemaHash (root / Object parent).</li>
 *   <li>2.1-b  Round-trip with parentSchemaHash PRESENT (32-byte hash).</li>
 *   <li>2.1-c  Structural difference: present vs absent encodings differ; both decode back equal.</li>
 *   <li>2.1-d  Field order preserved exactly -- S3.9 positional significance.</li>
 *   <li>2.1-e  jqwik property: random valid records round-trip.</li>
 *   <li>2.1-f  Fail-secure: 31-byte parentSchemaHash -> DerException on decode.</li>
 *   <li>2.1-g  Fail-secure: 33-byte parentSchemaHash -> DerException on decode.</li>
 *   <li>2.1-h  Fail-secure: empty className -> DerException on decode.</li>
 * </ul>
 *
 * <h3>Task 2.2 coverage</h3>
 * <ul>
 *   <li>2.2-a  Identical records -> identical digests AND byte-identical DER.</li>
 *   <li>2.2-b  Changing field name changes digest.</li>
 *   <li>2.2-c  Changing field type changes digest.</li>
 *   <li>2.2-d  Changing field ORDER changes digest (positional, S3.9).</li>
 *   <li>2.2-e  Changing className changes digest.</li>
 *   <li>2.2-f  Changing parentSchemaHash changes digest.</li>
 *   <li>2.2-g  Merkle chain: 3-level chain leaf->mid->root; leaf digest changes when root changes.</li>
 *   <li>2.2-h  Root (no parent) digest is stable.</li>
 *   <li>2.2-i  Digest stability: re-encoding many times yields identical bytes; two equal records
 *              yield identical digests (no HashMap iteration).</li>
 * </ul>
 */
class AtomicSerialSchemaRecordTest {

    // ====================================================================
    // Task 2.1 -- Round-trip tests
    // ====================================================================

    // ----------------------------------------------------------------
    // 2.1-a  Round-trip WITHOUT parentSchemaHash (root record)
    // ----------------------------------------------------------------

    @Test
    void roundTrip_noParentHash() throws DerException {
        AtomicSerialSchemaRecord original = new AtomicSerialSchemaRecord(
                "com.example.Root",
                List.of(
                        new AtomicSerialFieldDef("value", "int"),
                        new AtomicSerialFieldDef("name", "java.lang.String")
                ));
        byte[] der = original.encode();
        AtomicSerialSchemaRecord decoded = AtomicSerialSchemaRecord.decode(der);
        assertEquals(original, decoded);
        assertTrue(decoded.parentSchemaHash().isEmpty(), "parentSchemaHash must be absent");
    }

    @Test
    void roundTrip_emptyFields() throws DerException {
        AtomicSerialSchemaRecord original = new AtomicSerialSchemaRecord(
                "com.example.Empty", List.of());
        byte[] der = original.encode();
        AtomicSerialSchemaRecord decoded = AtomicSerialSchemaRecord.decode(der);
        assertEquals(original, decoded);
        assertTrue(decoded.fields().isEmpty());
    }

    // ----------------------------------------------------------------
    // 2.1-b  Round-trip WITH parentSchemaHash PRESENT
    // ----------------------------------------------------------------

    @Test
    void roundTrip_withParentHash() throws DerException {
        byte[] hash = make32Bytes(0xAB);
        AtomicSerialSchemaRecord original = new AtomicSerialSchemaRecord(
                "com.example.Child",
                hash,
                List.of(
                        new AtomicSerialFieldDef("extra", "long")
                ));
        byte[] der = original.encode();
        AtomicSerialSchemaRecord decoded = AtomicSerialSchemaRecord.decode(der);
        assertEquals(original, decoded);
        assertTrue(decoded.parentSchemaHash().isPresent(), "parentSchemaHash must be present");
        assertArrayEquals(hash, decoded.parentSchemaHash().get());
    }

    // ----------------------------------------------------------------
    // 2.1-c  Structural difference: present vs absent encodings differ;
    //         both decode back equal.
    // ----------------------------------------------------------------

    @Test
    void encodingsStructurallyDiffer_presentVsAbsentParentHash() throws DerException {
        List<AtomicSerialFieldDef> fields = List.of(new AtomicSerialFieldDef("x", "int"));

        AtomicSerialSchemaRecord withHash = new AtomicSerialSchemaRecord(
                "com.example.Child", make32Bytes(0x11), fields);
        AtomicSerialSchemaRecord withoutHash = new AtomicSerialSchemaRecord(
                "com.example.Child", (byte[]) null, fields);

        byte[] derWith    = withHash.encode();
        byte[] derWithout = withoutHash.encode();

        // The DER bytes must differ (the OCTET STRING TLV is present in one, absent in other)
        assertFalse(Arrays.equals(derWith, derWithout),
                "DER with parentSchemaHash must differ from DER without");

        // The record with the hash is longer (contains 34 extra bytes: 04 20 + 32 content)
        assertEquals(derWithout.length + 34, derWith.length,
                "DER with parentSchemaHash should be 34 bytes longer (tag+len+32 content)");

        // Both must decode back to equal records respectively
        assertEquals(withHash,    AtomicSerialSchemaRecord.decode(derWith));
        assertEquals(withoutHash, AtomicSerialSchemaRecord.decode(derWithout));
    }

    // ----------------------------------------------------------------
    // 2.1-d  Field ORDER preserved exactly (S3.9 positional significance)
    //
    // Build a record whose fields would reorder under alphabetical sorting;
    // round-trip; assert identical order.
    // ----------------------------------------------------------------

    @Test
    void fieldOrderPreservedExactly() throws DerException {
        // Deliberately in reverse alphabetical order
        AtomicSerialFieldDef f1 = new AtomicSerialFieldDef("zField", "int");
        AtomicSerialFieldDef f2 = new AtomicSerialFieldDef("mField", "long");
        AtomicSerialFieldDef f3 = new AtomicSerialFieldDef("aField", "java.lang.String");

        AtomicSerialSchemaRecord original = new AtomicSerialSchemaRecord(
                "com.example.Ordered", List.of(f1, f2, f3));

        byte[] der = original.encode();
        AtomicSerialSchemaRecord decoded = AtomicSerialSchemaRecord.decode(der);

        List<AtomicSerialFieldDef> fields = decoded.fields();
        assertEquals(3, fields.size());
        assertEquals(f1, fields.get(0), "field[0] must be zField");
        assertEquals(f2, fields.get(1), "field[1] must be mField");
        assertEquals(f3, fields.get(2), "field[2] must be aField");
    }

    // ----------------------------------------------------------------
    // 2.1-e  jqwik property: random valid records round-trip
    // ----------------------------------------------------------------

    @Property(tries = 200)
    void property_roundTrip(@ForAll("validSchemaRecord") AtomicSerialSchemaRecord original)
            throws DerException {
        byte[] der = original.encode();
        AtomicSerialSchemaRecord decoded = AtomicSerialSchemaRecord.decode(der);
        assertEquals(original, decoded);
    }

    @Provide
    Arbitrary<AtomicSerialSchemaRecord> validSchemaRecord() {
        Arbitrary<String> nameArb = Arbitraries.strings()
                .withCharRange('a', 'z').ofMinLength(1).ofMaxLength(30);
        Arbitrary<AtomicSerialFieldDef> fieldArb = nameArb.flatMap(n ->
                nameArb.map(t -> new AtomicSerialFieldDef(n, t)));
        Arbitrary<List<AtomicSerialFieldDef>> fieldsArb =
                fieldArb.list().ofMinSize(0).ofMaxSize(5);
        Arbitrary<String> classNameArb = Arbitraries.strings()
                .withCharRange('a', 'z').ofMinLength(1).ofMaxLength(40);
        // Sometimes include a parentSchemaHash
        Arbitrary<byte[]> maybeHash = Arbitraries.oneOf(
                Arbitraries.just((byte[]) null),
                Arbitraries.bytes().array(byte[].class).ofSize(32));
        return classNameArb.flatMap(cn ->
                maybeHash.flatMap(hash ->
                        fieldsArb.map(fields ->
                                new AtomicSerialSchemaRecord(cn, hash, fields))));
    }

    // ----------------------------------------------------------------
    // 2.1-f/g  Fail-secure: wrong-length parentSchemaHash
    // ----------------------------------------------------------------

    @Test
    void decode_parentHash31Bytes_throwsDerException() throws Exception {
        byte[] der = buildSchemaRecordWithHashLen(31);
        assertThrows(DerException.class, () -> AtomicSerialSchemaRecord.decode(der));
    }

    @Test
    void decode_parentHash33Bytes_throwsDerException() throws Exception {
        byte[] der = buildSchemaRecordWithHashLen(33);
        assertThrows(DerException.class, () -> AtomicSerialSchemaRecord.decode(der));
    }

    // ----------------------------------------------------------------
    // 2.1-h  Fail-secure: empty className -> DerException on decode
    // ----------------------------------------------------------------

    @Test
    void decode_emptyClassName_throwsDerException() throws Exception {
        // Build a raw DER: SEQUENCE { UTF8String(""), SEQUENCE {} }
        byte[] classNameTlv = DerWriter.writeUtf8String("");   // 0x0C 0x00
        byte[] fieldsTlv    = DerWriter.writeSequence(new byte[0]);
        byte[] der = DerWriter.writeSequence(List.of(classNameTlv, fieldsTlv));
        assertThrows(DerException.class, () -> AtomicSerialSchemaRecord.decode(der));
    }

    // ====================================================================
    // Task 2.2 -- Digest / determinism / Merkle chain tests
    // ====================================================================

    // ----------------------------------------------------------------
    // 2.2-a  Identical records -> identical digests AND byte-identical DER
    // ----------------------------------------------------------------

    @Test
    void identicalRecords_identicalDigestsAndDer() {
        List<AtomicSerialFieldDef> fields = List.of(
                new AtomicSerialFieldDef("x", "int"),
                new AtomicSerialFieldDef("y", "long"));

        // Build two INDEPENDENTLY constructed equal records
        AtomicSerialSchemaRecord r1 = new AtomicSerialSchemaRecord("com.example.Foo", fields);
        AtomicSerialSchemaRecord r2 = new AtomicSerialSchemaRecord("com.example.Foo", fields);

        assertEquals(r1, r2);
        assertArrayEquals(r1.encode(),       r2.encode(),       "DER must be byte-identical");
        assertArrayEquals(r1.schemaDigest(), r2.schemaDigest(), "digests must be identical");
    }

    // ----------------------------------------------------------------
    // 2.2-b  Changing field name changes digest
    // ----------------------------------------------------------------

    @Test
    void digestChanges_whenFieldNameChanges() {
        AtomicSerialSchemaRecord r1 = new AtomicSerialSchemaRecord(
                "com.example.X", List.of(new AtomicSerialFieldDef("alpha", "int")));
        AtomicSerialSchemaRecord r2 = new AtomicSerialSchemaRecord(
                "com.example.X", List.of(new AtomicSerialFieldDef("beta", "int")));

        assertFalse(Arrays.equals(r1.schemaDigest(), r2.schemaDigest()),
                "digest must change when wireName changes");
    }

    // ----------------------------------------------------------------
    // 2.2-c  Changing field type changes digest
    // ----------------------------------------------------------------

    @Test
    void digestChanges_whenFieldTypeChanges() {
        AtomicSerialSchemaRecord r1 = new AtomicSerialSchemaRecord(
                "com.example.X", List.of(new AtomicSerialFieldDef("value", "int")));
        AtomicSerialSchemaRecord r2 = new AtomicSerialSchemaRecord(
                "com.example.X", List.of(new AtomicSerialFieldDef("value", "long")));

        assertFalse(Arrays.equals(r1.schemaDigest(), r2.schemaDigest()),
                "digest must change when wireType changes");
    }

    // ----------------------------------------------------------------
    // 2.2-d  Changing field ORDER changes digest (S3.9 positional significance)
    // ----------------------------------------------------------------

    @Test
    void digestChanges_whenFieldOrderChanges() {
        AtomicSerialFieldDef f1 = new AtomicSerialFieldDef("alpha", "int");
        AtomicSerialFieldDef f2 = new AtomicSerialFieldDef("beta", "long");

        AtomicSerialSchemaRecord r1 = new AtomicSerialSchemaRecord(
                "com.example.X", List.of(f1, f2));
        AtomicSerialSchemaRecord r2 = new AtomicSerialSchemaRecord(
                "com.example.X", List.of(f2, f1));  // swapped

        assertFalse(Arrays.equals(r1.schemaDigest(), r2.schemaDigest()),
                "digest must change when field order changes");
    }

    // ----------------------------------------------------------------
    // 2.2-e  Changing className changes digest
    // ----------------------------------------------------------------

    @Test
    void digestChanges_whenClassNameChanges() {
        List<AtomicSerialFieldDef> fields = List.of(new AtomicSerialFieldDef("x", "int"));
        AtomicSerialSchemaRecord r1 = new AtomicSerialSchemaRecord("com.example.Alpha", fields);
        AtomicSerialSchemaRecord r2 = new AtomicSerialSchemaRecord("com.example.Beta",  fields);
        assertFalse(Arrays.equals(r1.schemaDigest(), r2.schemaDigest()),
                "digest must change when className changes");
    }

    // ----------------------------------------------------------------
    // 2.2-f  Changing parentSchemaHash changes digest
    // ----------------------------------------------------------------

    @Test
    void digestChanges_whenParentHashChanges() {
        List<AtomicSerialFieldDef> fields = List.of(new AtomicSerialFieldDef("x", "int"));
        AtomicSerialSchemaRecord r1 = new AtomicSerialSchemaRecord("com.example.X",
                make32Bytes(0x11), fields);
        AtomicSerialSchemaRecord r2 = new AtomicSerialSchemaRecord("com.example.X",
                make32Bytes(0x22), fields);
        assertFalse(Arrays.equals(r1.schemaDigest(), r2.schemaDigest()),
                "digest must change when parentSchemaHash changes");
    }

    // ----------------------------------------------------------------
    // 2.2-g  Merkle chain: 3-level chain
    //
    // Build leaf, mid, root INITIALLY without links.
    // Link them via SchemaChain.linkAndGetLeafDigest.
    // Assert leaf digest changes when root content changes.
    // Assert root digest is stable (no parent).
    // ----------------------------------------------------------------

    @Test
    void merkleChain_threeLevels_leafDigestChangesWhenRootChanges() {
        // Initial records (unlinked)
        AtomicSerialSchemaRecord root = new AtomicSerialSchemaRecord(
                "com.example.Root",
                List.of(new AtomicSerialFieldDef("rootField", "int")));
        AtomicSerialSchemaRecord mid = new AtomicSerialSchemaRecord(
                "com.example.Mid",
                List.of(new AtomicSerialFieldDef("midField", "long")));
        AtomicSerialSchemaRecord leaf = new AtomicSerialSchemaRecord(
                "com.example.Leaf",
                List.of(new AtomicSerialFieldDef("leafField", "java.lang.String")));

        // Link chain 1: leaf -> mid -> root
        SchemaChain.Result chain1 = SchemaChain.linkAndGetLeafDigest(List.of(leaf, mid, root));
        byte[] leafDigest1 = chain1.leafDigest();

        // Root with different content
        AtomicSerialSchemaRecord rootModified = new AtomicSerialSchemaRecord(
                "com.example.Root",
                List.of(new AtomicSerialFieldDef("rootField", "long")));  // type changed

        // Link chain 2: same leaf/mid, different root
        SchemaChain.Result chain2 = SchemaChain.linkAndGetLeafDigest(List.of(leaf, mid, rootModified));
        byte[] leafDigest2 = chain2.leafDigest();

        assertFalse(Arrays.equals(leafDigest1, leafDigest2),
                "leaf digest must change when root content changes");
    }

    // ----------------------------------------------------------------
    // 2.2-h  Root (no parent) digest is stable
    // ----------------------------------------------------------------

    @Test
    void merkleChain_rootDigest_isStable() {
        AtomicSerialSchemaRecord root = new AtomicSerialSchemaRecord(
                "com.example.Root",
                List.of(new AtomicSerialFieldDef("rootField", "int")));

        byte[] d1 = root.schemaDigest();
        byte[] d2 = root.schemaDigest();
        assertArrayEquals(d1, d2, "root digest must be stable across multiple calls");
        assertTrue(root.parentSchemaHash().isEmpty(), "root has no parent");
    }

    // ----------------------------------------------------------------
    // 2.2-i  Digest stability: re-encoding / two equal records -> identical
    //         32-byte digest (no HashMap iteration in the path)
    // ----------------------------------------------------------------

    @Test
    void digestStability_reEncodeMany_identicalDigest() {
        AtomicSerialSchemaRecord record = new AtomicSerialSchemaRecord(
                "com.example.Stable",
                List.of(
                        new AtomicSerialFieldDef("c", "int"),
                        new AtomicSerialFieldDef("b", "long"),
                        new AtomicSerialFieldDef("a", "java.lang.String")));

        byte[] first = record.schemaDigest();
        for (int i = 0; i < 100; i++) {
            assertArrayEquals(first, record.schemaDigest(),
                    "digest must be identical on iteration " + i);
        }

        // Two independently constructed equal records must produce the same digest
        AtomicSerialSchemaRecord twin = new AtomicSerialSchemaRecord(
                "com.example.Stable",
                List.of(
                        new AtomicSerialFieldDef("c", "int"),
                        new AtomicSerialFieldDef("b", "long"),
                        new AtomicSerialFieldDef("a", "java.lang.String")));

        assertArrayEquals(first, twin.schemaDigest(),
                "independently constructed equal records must produce identical digest");
        assertArrayEquals(record.encode(), twin.encode(),
                "independently constructed equal records must produce byte-identical DER");
    }

    // ----------------------------------------------------------------
    // 2.2-g extended: verify chain structure (mid's parentSchemaHash = root digest,
    //                  leaf's parentSchemaHash = mid digest after linking)
    // ----------------------------------------------------------------

    @Test
    void merkleChain_linkedRecord_parentHashesAreCorrect() {
        AtomicSerialSchemaRecord root = new AtomicSerialSchemaRecord(
                "com.example.Root", List.of(new AtomicSerialFieldDef("r", "int")));
        AtomicSerialSchemaRecord mid = new AtomicSerialSchemaRecord(
                "com.example.Mid", List.of(new AtomicSerialFieldDef("m", "int")));
        AtomicSerialSchemaRecord leaf = new AtomicSerialSchemaRecord(
                "com.example.Leaf", List.of(new AtomicSerialFieldDef("l", "int")));

        SchemaChain.Result result = SchemaChain.linkAndGetLeafDigest(List.of(leaf, mid, root));
        List<AtomicSerialSchemaRecord> chain = result.chain();

        AtomicSerialSchemaRecord linkedLeaf = chain.get(0);
        AtomicSerialSchemaRecord linkedMid  = chain.get(1);
        AtomicSerialSchemaRecord linkedRoot = chain.get(2);

        // Root has no parent
        assertTrue(linkedRoot.parentSchemaHash().isEmpty(), "root must have no parent hash");

        // Mid's parentSchemaHash = SHA-256(DER(linkedRoot))
        byte[] expectedMidParentHash = linkedRoot.schemaDigest();
        assertArrayEquals(expectedMidParentHash, linkedMid.parentSchemaHash().orElseThrow(),
                "mid's parentSchemaHash must equal root's schemaDigest");

        // Leaf's parentSchemaHash = SHA-256(DER(linkedMid))
        byte[] expectedLeafParentHash = linkedMid.schemaDigest();
        assertArrayEquals(expectedLeafParentHash, linkedLeaf.parentSchemaHash().orElseThrow(),
                "leaf's parentSchemaHash must equal mid's schemaDigest");

        // result.leafDigest == SHA-256(DER(linkedLeaf))
        assertArrayEquals(linkedLeaf.schemaDigest(), result.leafDigest(),
                "result.leafDigest must equal linkedLeaf.schemaDigest()");
    }

    @Test
    void merkleChain_singleRecord_noParentHash() {
        AtomicSerialSchemaRecord single = new AtomicSerialSchemaRecord(
                "com.example.Solo", List.of(new AtomicSerialFieldDef("x", "int")));
        SchemaChain.Result result = SchemaChain.linkAndGetLeafDigest(List.of(single));
        AtomicSerialSchemaRecord linked = result.chain().get(0);
        assertTrue(linked.parentSchemaHash().isEmpty(), "single-record chain has no parent");
        assertArrayEquals(linked.schemaDigest(), result.leafDigest());
    }

    // ====================================================================
    // Helpers
    // ====================================================================

    /** Creates a 32-byte array filled with {@code value}. */
    private static byte[] make32Bytes(int value) {
        byte[] b = new byte[32];
        Arrays.fill(b, (byte) value);
        return b;
    }

    /**
     * Builds a raw DER AtomicSerialSchemaRecord with an OCTET STRING of the given
     * length in the parentSchemaHash position. Bypasses the constructor's size check.
     */
    private static byte[] buildSchemaRecordWithHashLen(int hashLen) {
        // SEQUENCE {
        //   UTF8String("com.example.X"),
        //   OCTET STRING(hashLen bytes of 0xAB),
        //   SEQUENCE {}   -- empty fields
        // }
        byte[] classNameTlv = DerWriter.writeUtf8String("com.example.X");
        byte[] hashContent  = new byte[hashLen];
        Arrays.fill(hashContent, (byte) 0xAB);
        byte[] hashTlv      = buildOctetStringTlv(hashContent);
        byte[] fieldsTlv    = DerWriter.writeSequence(new byte[0]);

        int seqContent = classNameTlv.length + hashTlv.length + fieldsTlv.length;
        byte[] seqLen = encodeLen(seqContent);
        byte[] result = new byte[1 + seqLen.length + seqContent];
        int pos = 0;
        result[pos++] = 0x30;
        System.arraycopy(seqLen,       0, result, pos, seqLen.length);       pos += seqLen.length;
        System.arraycopy(classNameTlv, 0, result, pos, classNameTlv.length); pos += classNameTlv.length;
        System.arraycopy(hashTlv,      0, result, pos, hashTlv.length);      pos += hashTlv.length;
        System.arraycopy(fieldsTlv,    0, result, pos, fieldsTlv.length);
        return result;
    }

    private static byte[] buildOctetStringTlv(byte[] content) {
        byte[] lenBytes = encodeLen(content.length);
        byte[] tlv = new byte[1 + lenBytes.length + content.length];
        tlv[0] = 0x04;
        System.arraycopy(lenBytes, 0, tlv, 1, lenBytes.length);
        System.arraycopy(content,  0, tlv, 1 + lenBytes.length, content.length);
        return tlv;
    }

    private static byte[] encodeLen(int len) {
        if (len <= 127) return new byte[]{ (byte) len };
        if (len <= 255) return new byte[]{ (byte) 0x81, (byte) len };
        return new byte[]{ (byte) 0x82, (byte)(len >> 8), (byte)(len & 0xFF) };
    }
}
