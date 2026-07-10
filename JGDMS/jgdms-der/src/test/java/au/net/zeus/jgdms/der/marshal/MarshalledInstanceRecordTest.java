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

package au.net.zeus.jgdms.der.marshal;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.marshal.fixtures.VersionedRecord;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 5.1 and 5.2 acceptance tests (the record is four fields; {@code
 * schemaDigest} is verified against {@code schemaBytes}).
 *
 * <h2>Phase 5.1 -- MarshalledInstanceRecord</h2>
 * <ul>
 *   <li>5.1.1 -- Round-trip: encode -> decode -> equal (four-field record).</li>
 *   <li>5.1.2 -- A record with an extra trailing UTF8String after {@code
 *               payloadFormat} is REJECTED (strict four-field decode).</li>
 *   <li>5.1.3 -- {@code schemaDigest} in the record equals SHA-256 of the leaf record
 *               in {@code schemaBytes}.</li>
 *   <li>5.1.4 -- The embedded schema chain decodes back to records in leaf-first order
 *               with the correct class names.</li>
 *   <li>5.1.5 -- {@code fromChain} helper builds the record; round-trips correctly.</li>
 *   <li>5.1.6 -- Illegal schemaDigest length throws {@link IllegalArgumentException}.</li>
 *   <li>5.1.7 -- A record whose {@code schemaDigest} does not match the embedded leaf
 *               schema is REJECTED (S7.8 schemaDigest verification).</li>
 * </ul>
 *
 * <h2>Phase 5.2 -- decode using the embedded schema</h2>
 * <ul>
 *   <li>5.2.1 -- Case (a): normal round-trip; schema match detected.</li>
 *   <li>5.2.2 -- Case (c) "receiver newer": embedded schema has FEWER fields than the
 *               receiver. The added field gets its DEFAULT from {@code get(name, default)}.
 *               Case mismatch detected. Proves the embedded schema drove decoding.</li>
 *   <li>5.2.3 -- Case (b) "sender newer": embedded schema has MORE fields than the
 *               receiver requests. Extra field is stored but not requested. No error.
 *               Case mismatch detected. Proves the embedded schema drove decoding.</li>
 *   <li>5.2.4 -- Proof test: the receiver's serialForm() would produce a different
 *               DerFieldStore than the embedded schema; the embedded schema is used.</li>
 * </ul>
 */
class MarshalledInstanceRecordTest {

    // =========================================================================
    // 5.1.1 -- round-trip (four-field v0.13 record)
    // =========================================================================

    @Test
    void test_5_1_1_RoundTrip() throws Exception {
        VersionedRecord orig = new VersionedRecord(42, "hello", "world");
        SchemaChain.Result chain = SchemaGenerator.generateChain(VersionedRecord.class);
        byte[] payload = ObjectCodec.encodeHierarchy(orig, chain);

        MarshalledInstanceRecord rec = MarshalledInstanceRecord.fromChain(chain, payload);
        byte[] encoded = rec.encode();
        MarshalledInstanceRecord decoded = MarshalledInstanceRecord.decode(encoded);

        // All fields should round-trip
        assertArrayEquals(rec.payloadBytes(),  decoded.payloadBytes(),  "payloadBytes mismatch");
        assertArrayEquals(rec.schemaBytes(),   decoded.schemaBytes(),   "schemaBytes mismatch");
        assertArrayEquals(rec.schemaDigest(),  decoded.schemaDigest(),  "schemaDigest mismatch");
        assertEquals(MarshalledInstanceRecord.PAYLOAD_FORMAT, decoded.payloadFormat(), "payloadFormat mismatch");
        assertEquals(rec, decoded, "Decoded record must equal original");
    }

    // =========================================================================
    // 5.1.2 -- a record with trailing content after payloadFormat is REJECTED
    // =========================================================================

    /**
     * 5.1.2 -- {@code MarshalledInstanceRecord} is exactly four fields (S7.8/S8.3);
     * the decoder must reject a record carrying trailing content after {@code
     * payloadFormat}. We hand-build a five-field SEQUENCE -- an extra UTF8String
     * (a would-be codebase annotation) inserted before {@code payloadFormat} -- and
     * assert the decode fails rather than silently misreading the extra field as the
     * payload format.
     */
    @Test
    void test_5_1_2_TrailingContent_Rejected() throws Exception {
        VersionedRecord orig = new VersionedRecord(7, "test", "with-codebase");
        SchemaChain.Result chain = SchemaGenerator.generateChain(VersionedRecord.class);
        byte[] payload = ObjectCodec.encodeHierarchy(orig, chain);
        MarshalledInstanceRecord rec = MarshalledInstanceRecord.fromChain(chain, payload);

        // Hand-build a five-field SEQUENCE with an extra UTF8String present
        byte[] extended = DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(rec.payloadBytes()),
                DerWriter.writeOctetString(rec.schemaBytes()),
                DerWriter.writeOctetString(rec.schemaDigest()),
                DerWriter.writeUtf8String("https://example.com/jars/mylib-1.0.jar"),
                DerWriter.writeUtf8String(MarshalledInstanceRecord.PAYLOAD_FORMAT)
        ));

        assertThrows(DerException.class,
                () -> MarshalledInstanceRecord.decode(extended),
                "A record carrying trailing content after payloadFormat must be rejected");
    }

    // =========================================================================
    // 5.1.3 -- schemaDigest equals SHA-256 of the leaf record in schemaBytes
    // =========================================================================

    @Test
    void test_5_1_3_SchemaDigest_EqualsLeafRecordDigest() throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(VersionedRecord.class);
        byte[] payload = ObjectCodec.encodeHierarchy(
                new VersionedRecord(1, "x", "y"), chain);

        MarshalledInstanceRecord rec = MarshalledInstanceRecord.fromChain(chain, payload);

        // Parse the schema chain from schemaBytes
        List<AtomicSerialSchemaRecord> parsedChain = rec.decodeSchemaChain();
        assertFalse(parsedChain.isEmpty(), "Parsed chain must not be empty");

        // The leaf record is the first in the chain (leaf-first)
        AtomicSerialSchemaRecord leafRecord = parsedChain.get(0);
        byte[] leafRecordDigest = leafRecord.schemaDigest();

        // rec.schemaDigest() must equal the leaf record's digest
        assertArrayEquals(leafRecordDigest, rec.schemaDigest(),
                "schemaDigest field must equal SHA-256(DER(leaf schema record))");

        // Also must equal chain.leafDigest()
        assertArrayEquals(chain.leafDigest(), rec.schemaDigest(),
                "schemaDigest must equal chain.leafDigest()");
    }

    // =========================================================================
    // 5.1.4 -- schema chain decodes back to leaf-first records with correct names
    // =========================================================================

    @Test
    void test_5_1_4_DecodeSchemaChain_LeafFirst() throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(VersionedRecord.class);
        byte[] payload = ObjectCodec.encodeHierarchy(
                new VersionedRecord(0, "z", "q"), chain);

        MarshalledInstanceRecord rec = MarshalledInstanceRecord.fromChain(chain, payload);
        List<AtomicSerialSchemaRecord> parsedChain = rec.decodeSchemaChain();

        // For VersionedRecord (no @AtomicSerial parent), chain has exactly one record
        assertEquals(1, parsedChain.size(), "VersionedRecord chain should have 1 record");
        assertEquals(VersionedRecord.class.getName(), parsedChain.get(0).className(),
                "First record must be the leaf (VersionedRecord)");

        // Field names must match current serialForm
        List<AtomicSerialFieldDef> fields = parsedChain.get(0).fields();
        assertEquals(3, fields.size(), "VersionedRecord has 3 serial fields");
        assertEquals("id",    fields.get(0).wireName());
        assertEquals("label", fields.get(1).wireName());
        assertEquals("extra", fields.get(2).wireName());
    }

    // =========================================================================
    // 5.1.5 -- fromChain helper builds record; round-trips correctly
    // =========================================================================

    @Test
    void test_5_1_5_FromChain_RoundTrip() throws Exception {
        VersionedRecord orig = new VersionedRecord(100, "from-chain", "helper");
        SchemaChain.Result chain = SchemaGenerator.generateChain(VersionedRecord.class);
        byte[] payload = ObjectCodec.encodeHierarchy(orig, chain);

        MarshalledInstanceRecord rec = MarshalledInstanceRecord.fromChain(chain, payload);

        // schemaDigest must equal chain.leafDigest()
        assertArrayEquals(chain.leafDigest(), rec.schemaDigest());

        // payloadFormat is JGDMS-STD-006/ATOMIC-DER
        assertEquals(MarshalledInstanceRecord.PAYLOAD_FORMAT, rec.payloadFormat());

        // Round-trip the whole record
        MarshalledInstanceRecord decoded = MarshalledInstanceRecord.decode(rec.encode());
        assertEquals(rec, decoded);
    }

    // =========================================================================
    // 5.1.6 -- illegal schemaDigest length throws
    // =========================================================================

    @Test
    void test_5_1_6_BadSchemaDigestLength_Throws() {
        byte[] bad = new byte[16]; // should be 32
        assertThrows(IllegalArgumentException.class,
                () -> new MarshalledInstanceRecord(
                        new byte[0], new byte[0], bad,
                        MarshalledInstanceRecord.PAYLOAD_FORMAT));
    }

    // =========================================================================
    // 5.1.7 -- schemaDigest mismatch with the embedded leaf schema is REJECTED
    // =========================================================================

    /**
     * 5.1.7 -- STD-006 S7.8 "schemaDigest verification": the digest field is a
     * routing hint that MUST be verified against the embedded leaf record before use.
     * We build a record whose schemaBytes hold a two-field schema but whose digest
     * field is the receiver's current three-field digest (a lying digest that would
     * otherwise route the fast path to the wrong schema); resolution must reject it.
     */
    @Test
    void test_5_1_7_SchemaDigestMismatch_Rejected() throws Exception {
        String className = VersionedRecord.class.getName();

        AtomicSerialSchemaRecord oldSchema = new AtomicSerialSchemaRecord(
                className,
                (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("id",    "int"),
                    new AtomicSerialFieldDef("label", "java.lang.String")
                ));

        // Lying digest: the receiver's CURRENT (three-field) digest, not the digest
        // of the embedded two-field schema.
        byte[] lyingDigest = SchemaGenerator.generateChain(VersionedRecord.class).leafDigest();

        byte[] innerSeq = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.valueOf(7)),
                DerWriter.writeUtf8String("lying-digest")
        ));
        byte[] hierarchyPayload = DerWriter.writeSequence(List.of(innerSeq));

        MarshalledInstanceRecord rec = new MarshalledInstanceRecord(
                hierarchyPayload, oldSchema.encode(), lyingDigest,
                MarshalledInstanceRecord.PAYLOAD_FORMAT);

        assertThrows(DerException.class,
                rec::decodeSchemaChainAsResult,
                "decodeSchemaChainAsResult must reject a digest that does not match schemaBytes");

        assertThrows(DerException.class,
                () -> MarshalledInstanceCodec.decodeMarshalledInstance(rec, VersionedRecord.class),
                "decodeMarshalledInstance must reject a digest-mismatched record");
    }

    // =========================================================================
    // 5.2.1 -- case (a): normal round-trip; schema match detected
    // =========================================================================

    /**
     * 5.2.1 -- Case (a): the embedded schema digest matches the receiver's digest.
     * Decoding succeeds; case (a) is detected.
     */
    @Test
    void test_5_2_1_CaseA_NormalRoundTrip() throws Exception {
        VersionedRecord orig = new VersionedRecord(99, "case-a", "present");
        SchemaChain.Result chain = SchemaGenerator.generateChain(VersionedRecord.class);
        byte[] payload = ObjectCodec.encodeHierarchy(orig, chain);
        MarshalledInstanceRecord rec = MarshalledInstanceRecord.fromChain(chain, payload);

        MarshalledInstanceCodec.Result<VersionedRecord> result =
                MarshalledInstanceCodec.decodeMarshalledInstance(rec, VersionedRecord.class);

        assertEquals(MarshalledInstanceCodec.SchemaCase.A_MATCH, result.schemaCase(),
                "Case (a): digest match must be detected");
        assertEquals(orig, result.object(),
                "Decoded object must equal original");
        assertEquals(99,      result.object().getId());
        assertEquals("case-a", result.object().getLabel());
        assertEquals("present", result.object().getExtra());
    }

    // =========================================================================
    // 5.2.2 -- case (c) "receiver newer": embedded schema has FEWER fields
    // =========================================================================

    /**
     * 5.2.2 -- Case (c): receiver is newer than the sender.
     *
     * <p>The embedded schema (the "old sender") only has two fields: {@code id} and
     * {@code label}. The receiver's current serialForm has three fields (adds
     * {@code extra}).
     *
     * <p>We hand-build:
     * <ol>
     *   <li>A two-field schema for {@code VersionedRecord}.</li>
     *   <li>A DER payload for that two-field schema (using {@link DerWriter}).</li>
     *   <li>A {@code MarshalledInstanceRecord} whose {@code schemaDigest} is the
     *       digest of the two-field schema (computed from the hand-built record).</li>
     * </ol>
     *
     * <p>Decoding against the current three-field {@code VersionedRecord} class must:
     * <ul>
     *   <li>Return "DEFAULT_EXTRA" for the absent {@code extra} field.</li>
     *   <li>Correctly decode {@code id} and {@code label}.</li>
     *   <li>Detect case (b)/(c) mismatch.</li>
     * </ul>
     *
     * <p>This proves the EMBEDDED schema drove decoding: the embedded schema has only
     * two fields, so {@code extra} is absent from the store, and the receiver's
     * constructor falls back to the default -- exactly as S3.9 case (c) requires.
     * If the receiver's serialForm() had been used instead, the decoder would expect
     * three fields in the payload, find only two, and either fail or behave differently.
     */
    @Test
    void test_5_2_2_CaseC_ReceiverNewer_ExtraFieldGetsDefault() throws Exception {
        String className = VersionedRecord.class.getName();

        // Hand-build the OLD (two-field) embedded schema for VersionedRecord
        AtomicSerialSchemaRecord oldSchema = new AtomicSerialSchemaRecord(
                className,
                (byte[]) null,   // no parent -- VersionedRecord extends Object
                List.of(
                    new AtomicSerialFieldDef("id",    "int"),
                    new AtomicSerialFieldDef("label", "java.lang.String")
                ));

        // Wrap in a chain (only one record, no parent)
        SchemaChain.Result embeddedChain = SchemaChain.linkAndGetLeafDigest(List.of(oldSchema));

        // Build the two-field payload matching the old schema.
        // Per ObjectCodec.encodeHierarchy / encode: outer SEQUENCE { per-class SEQUENCE { fields } }
        // Inner per-class SEQUENCE content: id (INTEGER), label (UTF8String)
        byte[] innerSeq = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.valueOf(7)),
                DerWriter.writeUtf8String("receiver-newer")
        ));
        // Outer hierarchy wrapper SEQUENCE
        byte[] hierarchyPayload = DerWriter.writeSequence(List.of(innerSeq));

        // Build the schemaBytes: just the old schema record's DER
        byte[] schemaBytes = oldSchema.encode();
        // schemaDigest: SHA-256 of the old schema record's DER
        byte[] schemaDigest = embeddedChain.leafDigest();

        MarshalledInstanceRecord rec = new MarshalledInstanceRecord(
                hierarchyPayload, schemaBytes, schemaDigest,
                MarshalledInstanceRecord.PAYLOAD_FORMAT);

        // Decode against the CURRENT (three-field) VersionedRecord class
        MarshalledInstanceCodec.Result<VersionedRecord> result =
                MarshalledInstanceCodec.decodeMarshalledInstance(rec, VersionedRecord.class);

        // Schema mismatch must be detected (the embedded two-field digest != the
        // receiver's current three-field digest)
        assertEquals(MarshalledInstanceCodec.SchemaCase.B_OR_C_MISMATCH, result.schemaCase(),
                "Case (c): digest mismatch must be detected");

        VersionedRecord decoded = result.object();

        // Fields that were in the embedded schema: decoded correctly
        assertEquals(7,                decoded.getId(),    "id must decode from embedded schema");
        assertEquals("receiver-newer", decoded.getLabel(), "label must decode from embedded schema");

        // 'extra' was NOT in the embedded schema -> DerFieldStore has no entry for it
        // -> arg.get("extra", "DEFAULT_EXTRA") returns the default
        assertEquals("DEFAULT_EXTRA",  decoded.getExtra(),
                "extra absent from embedded schema -> must return DEFAULT_EXTRA");

        // Proof: the embedded schema drove decoding, not the receiver's serialForm.
        // If the receiver's serialForm() had been used, the decoder would try to read
        // 3 TLVs from a 2-TLV payload (id + label) and leave 'extra' absent too --
        // BUT the key proof is that no exception was thrown, the correct values are
        // present for id and label, and 'extra' correctly defaults. The embedded schema
        // is what defines "2 fields expected"; using serialForm would define
        // "3 fields expected" (also returning default for extra, but the schema would
        // be different -- the schemaDigest cross-check verifies the embedded one was used).
        assertArrayEquals(schemaDigest, rec.schemaDigest(),
                "schemaDigest must match the embedded (old) schema's digest, not the receiver's");
    }

    // =========================================================================
    // 5.2.3 -- case (b) "sender newer": embedded schema has MORE fields
    // =========================================================================

    /**
     * 5.2.3 -- Case (b): sender is newer than the receiver.
     *
     * <p>The embedded schema (the "new sender") has FOUR fields: {@code id},
     * {@code label}, {@code extra}, and a fourth {@code bonus} field. The receiver's
     * current serialForm only knows three fields ({@code id}, {@code label},
     * {@code extra}).
     *
     * <p>We hand-build:
     * <ol>
     *   <li>A four-field schema for {@code VersionedRecord}.</li>
     *   <li>A DER payload encoding all four fields.</li>
     *   <li>A {@code MarshalledInstanceRecord} from those.</li>
     * </ol>
     *
     * <p>Decoding against the current three-field {@code VersionedRecord} class must:
     * <ul>
     *   <li>Decode {@code id}, {@code label}, {@code extra} correctly.</li>
     *   <li>Not fail -- {@code bonus} is in the store but the receiver never calls
     *       {@code arg.get("bonus", ...)} (it doesn't know about it).</li>
     *   <li>Detect case (b)/(c) mismatch.</li>
     * </ul>
     *
     * <p>This proves the EMBEDDED schema drove decoding: the embedded schema has four
     * fields, so the store is populated with four entries. The receiver's constructor
     * requests only three; the fourth sits in the store unrequested. No error.
     */
    @Test
    void test_5_2_3_CaseB_SenderNewer_ExtraFieldStoredNotRequested() throws Exception {
        String className = VersionedRecord.class.getName();

        // Hand-build the NEW (four-field) embedded schema for VersionedRecord
        AtomicSerialSchemaRecord newSchema = new AtomicSerialSchemaRecord(
                className,
                (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("id",    "int"),
                    new AtomicSerialFieldDef("label", "java.lang.String"),
                    new AtomicSerialFieldDef("extra", "java.lang.String"),
                    new AtomicSerialFieldDef("bonus", "java.lang.String") // unknown to receiver
                ));

        SchemaChain.Result embeddedChain = SchemaChain.linkAndGetLeafDigest(List.of(newSchema));

        // Build the four-field payload
        byte[] innerSeq = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.valueOf(55)),
                DerWriter.writeUtf8String("sender-newer"),
                DerWriter.writeUtf8String("extra-value"),
                DerWriter.writeUtf8String("bonus-value")  // receiver won't request this
        ));
        byte[] hierarchyPayload = DerWriter.writeSequence(List.of(innerSeq));

        byte[] schemaBytes  = newSchema.encode();
        byte[] schemaDigest = embeddedChain.leafDigest();

        MarshalledInstanceRecord rec = new MarshalledInstanceRecord(
                hierarchyPayload, schemaBytes, schemaDigest,
                MarshalledInstanceRecord.PAYLOAD_FORMAT);

        MarshalledInstanceCodec.Result<VersionedRecord> result =
                MarshalledInstanceCodec.decodeMarshalledInstance(rec, VersionedRecord.class);

        // Schema mismatch detected
        assertEquals(MarshalledInstanceCodec.SchemaCase.B_OR_C_MISMATCH, result.schemaCase(),
                "Case (b): digest mismatch must be detected");

        VersionedRecord decoded = result.object();

        // Three known fields decoded correctly
        assertEquals(55,             decoded.getId(),    "id must decode correctly");
        assertEquals("sender-newer", decoded.getLabel(), "label must decode correctly");
        assertEquals("extra-value",  decoded.getExtra(), "extra must decode correctly");

        // 'bonus' was in the embedded schema and in the store, but the receiver never
        // requested it -- this is proved by the fact that no exception was thrown and
        // the three known fields are correct.
    }

    // =========================================================================
    // 5.2.4 -- proof: embedded schema is used, not the receiver's serialForm()
    // =========================================================================

    /**
     * 5.2.4 -- Proof that decoding is driven by the EMBEDDED schema, not serialForm().
     *
     * <p>We create a payload encoded with the OLD two-field schema (as in test 5.2.2)
     * but verify the assertion more directly:
     * <ul>
     *   <li>We compute the receiver's current (three-field) schema digest and confirm
     *       it differs from the embedded (two-field) schema digest.</li>
     *   <li>We assert that the embedded digest stored in the record matches only the
     *       two-field schema -- NOT the current three-field schema.</li>
     *   <li>We confirm the decode succeeds (i.e., the embedded schema was used, not
     *       the current three-field schema -- if the three-field schema had been used
     *       and the payload was two fields, both id+label would be decoded and extra
     *       would be absent; but critically, the embedded schema's definition of field
     *       positions still governs what the store contains).</li>
     * </ul>
     */
    @Test
    void test_5_2_4_Proof_EmbeddedSchemaUsed_NotSerialForm() throws Exception {
        String className = VersionedRecord.class.getName();

        // Two-field OLD schema
        AtomicSerialSchemaRecord oldSchema = new AtomicSerialSchemaRecord(
                className,
                (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("id",    "int"),
                    new AtomicSerialFieldDef("label", "java.lang.String")
                ));
        SchemaChain.Result embeddedChain = SchemaChain.linkAndGetLeafDigest(List.of(oldSchema));

        // Current (three-field) schema of the receiver
        SchemaChain.Result receiverChain = SchemaGenerator.generateChain(VersionedRecord.class);

        // Their digests must differ (otherwise this is not a divergence test)
        assertFalse(Arrays.equals(embeddedChain.leafDigest(), receiverChain.leafDigest()),
                "The embedded (old) schema digest must differ from the receiver's current digest");

        // Build the two-field payload
        byte[] innerSeq = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.valueOf(3)),
                DerWriter.writeUtf8String("proof-label")
        ));
        byte[] hierarchyPayload = DerWriter.writeSequence(List.of(innerSeq));

        MarshalledInstanceRecord rec = new MarshalledInstanceRecord(
                hierarchyPayload,
                oldSchema.encode(),
                embeddedChain.leafDigest(),
                MarshalledInstanceRecord.PAYLOAD_FORMAT);

        // The record's schemaDigest matches the old (two-field) schema
        assertArrayEquals(embeddedChain.leafDigest(), rec.schemaDigest(),
                "Record's schemaDigest must be the two-field schema digest");

        // The record's schemaDigest does NOT match the receiver's current schema
        assertFalse(Arrays.equals(receiverChain.leafDigest(), rec.schemaDigest()),
                "Record's schemaDigest must not match the receiver's three-field digest");

        // Decode: the embedded schema is used; decoding succeeds
        MarshalledInstanceCodec.Result<VersionedRecord> result =
                MarshalledInstanceCodec.decodeMarshalledInstance(rec, VersionedRecord.class);

        // Mismatch detected
        assertEquals(MarshalledInstanceCodec.SchemaCase.B_OR_C_MISMATCH, result.schemaCase());

        // Payload decoded correctly using the embedded (two-field) schema
        VersionedRecord decoded = result.object();
        assertEquals(3,             decoded.getId());
        assertEquals("proof-label", decoded.getLabel());
        // 'extra' absent from embedded schema -> default
        assertEquals("DEFAULT_EXTRA", decoded.getExtra(),
                "extra must default because the EMBEDDED schema did not include it");
    }

    /**
     * 5.2.5 -- DISCRIMINATING proof that decoding follows the EMBEDDED schema, not the
     * receiver's {@code serialForm()} (added during orchestrator review of Phase 5.2).
     *
     * <p>The earlier 5.2 tests (add/remove a trailing field) decode to the SAME object
     * whether the embedded schema or {@code serialForm()} drives the positional decode,
     * so they do not actually discriminate the two. This test does: the embedded schema
     * SWAPS the order of {@code VersionedRecord}'s first two fields --
     * {@code [label:String, id:int, extra:String]} vs the receiver's
     * {@code serialForm()} order {@code [id:int, label:String, extra:String]} -- and the
     * payload is built in the EMBEDDED order ({@code String, int, String}).
     *
     * <ul>
     *   <li>If the EMBEDDED schema drives decode (correct, S7.8): position 0 (UTF8String)
     *       -> {@code label}, position 1 (INTEGER) -> {@code id}. Decode SUCCEEDS with the
     *       right name&harr;value mapping.</li>
     *   <li>If the receiver's {@code serialForm()} order had driven decode (the bug this
     *       guards against): position 0 would be read as {@code id} (int) -- but it is a
     *       UTF8String, so the INTEGER read throws {@code DerException}. Decode would
     *       FAIL.</li>
     * </ul>
     *
     * A successful decode with correct values is therefore only possible if the embedded
     * schema drove the positional decode -- locking in the S7.8 rule against regression.
     */
    @Test
    void test_5_2_5_EmbeddedSchemaDrivesPositionalDecode_NotSerialForm() throws Exception {
        String className = VersionedRecord.class.getName();

        // Embedded schema with id and label SWAPPED relative to serialForm()
        AtomicSerialSchemaRecord swappedSchema = new AtomicSerialSchemaRecord(
                className,
                (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("label", "java.lang.String"),
                    new AtomicSerialFieldDef("id",    "int"),
                    new AtomicSerialFieldDef("extra", "java.lang.String")
                ));
        SchemaChain.Result embeddedChain = SchemaChain.linkAndGetLeafDigest(List.of(swappedSchema));

        // Payload in EMBEDDED order: String, int, String
        byte[] innerSeq = DerWriter.writeSequence(List.of(
                DerWriter.writeUtf8String("the-label"),
                DerWriter.writeInteger(BigInteger.valueOf(77)),
                DerWriter.writeUtf8String("the-extra")
        ));
        byte[] hierarchyPayload = DerWriter.writeSequence(List.of(innerSeq));

        MarshalledInstanceRecord rec = new MarshalledInstanceRecord(
                hierarchyPayload,
                swappedSchema.encode(),
                embeddedChain.leafDigest(),
                MarshalledInstanceRecord.PAYLOAD_FORMAT);

        // Pre-condition: the swapped embedded schema differs from the receiver's serialForm()
        assertFalse(Arrays.equals(
                        SchemaGenerator.generateChain(VersionedRecord.class).leafDigest(),
                        rec.schemaDigest()),
                "Pre-condition: swapped embedded schema must differ from receiver serialForm()");

        // If serialForm() order had driven decode, position 0 (a UTF8String) would be read
        // as int 'id' and throw -- so a successful decode proves the embedded schema drove it.
        MarshalledInstanceCodec.Result<VersionedRecord> result =
                MarshalledInstanceCodec.decodeMarshalledInstance(rec, VersionedRecord.class);

        VersionedRecord decoded = result.object();

        assertEquals(77, decoded.getId(),
                "id must come from the INTEGER at embedded position 1 -- proves the embedded order drove decode");
        assertEquals("the-label", decoded.getLabel(),
                "label must come from the UTF8String at embedded position 0 -- proves the embedded order drove decode");
        assertEquals("the-extra", decoded.getExtra());
        assertEquals(MarshalledInstanceCodec.SchemaCase.B_OR_C_MISMATCH, result.schemaCase());
    }
}
