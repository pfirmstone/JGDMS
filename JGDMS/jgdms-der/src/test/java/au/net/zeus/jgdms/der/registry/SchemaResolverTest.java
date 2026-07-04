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

package au.net.zeus.jgdms.der.registry;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceRecord;
import au.net.zeus.jgdms.der.marshal.fixtures.VersionedRecord;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 7.3 -- SchemaResolver S12.4 decision-tree acceptance tests
 * (STD-006 S12.4: fail-secure resolution).
 *
 * <p>The tree has two branches and two rejections; a registry is never
 * consulted at decode time, and a record without a usable embedded schema is
 * rejected, never decoded against a guessed schema:
 *
 * <ul>
 *   <li>7.3.1 -- <b>LOCAL_MATCH</b>: verified embedded digest matches the local
 *               {@code serialForm()} digest.</li>
 *   <li>7.3.2 -- <b>EMBEDDED</b>: digest mismatch -> embedded schema drives the
 *               decode (discriminating swapped-field-order proof).</li>
 *   <li>7.3.3 -- <b>REJECT (absent schema)</b>: empty {@code schemaBytes} ->
 *               {@link DerException}; no registry or local fallback.</li>
 *   <li>7.3.4 -- <b>REJECT (lying digest)</b>: {@code schemaDigest} that does not
 *               match the embedded leaf record -> {@link DerException}; the
 *               fast path can no longer be hijacked by a digest that
 *               impersonates the receiver's local schema.</li>
 * </ul>
 */
class SchemaResolverTest {

    // =========================================================================
    // 7.3.1 -- LOCAL_MATCH: verified digest matches local serialForm()
    // =========================================================================

    /**
     * 7.3.1 -- When the (verified) embedded schema digest matches the receiver's
     * local {@code serialForm()} digest, the resolver takes the LOCAL_MATCH branch:
     * the local and embedded schemas are byte-identical (S3.9 case (a)).
     */
    @Test
    void test_7_3_1_LocalMatch() throws Exception {
        // Build a conforming MarshalledInstanceRecord for VersionedRecord
        VersionedRecord orig = new VersionedRecord(42, "hello", "world");
        SchemaChain.Result chain = SchemaGenerator.generateChain(VersionedRecord.class);
        byte[] payload = ObjectCodec.encodeHierarchy(orig, chain);
        MarshalledInstanceRecord rec = MarshalledInstanceRecord.fromChain(chain, payload);

        // The record's schemaDigest must equal the local serialForm() digest
        // (it was built from the same chain) -- this is the LOCAL_MATCH precondition.
        assertArrayEquals(chain.leafDigest(), rec.schemaDigest(),
                "Pre-condition: record digest must equal local chain digest");

        SchemaResolver.Result result = SchemaResolver.resolve(rec, VersionedRecord.class);

        assertEquals(SchemaResolver.Branch.LOCAL_MATCH, result.branch(),
                "Must take LOCAL_MATCH branch when local digest equals verified embedded digest");
        assertArrayEquals(chain.leafDigest(), result.chain().leafDigest(),
                "Resolved chain digest must be the shared digest");
    }

    // =========================================================================
    // 7.3.2 -- EMBEDDED: digest mismatch -> embedded schema used
    // =========================================================================

    /**
     * 7.3.2 -- When the record's (verified) digest does NOT match the local schema,
     * the resolver takes the EMBEDDED branch and uses the embedded schema chain.
     *
     * <p>The embedded schema has the first two of VersionedRecord's fields SWAPPED
     * (label first, id second) -- matching the discriminating test in Phase 5.2.5.
     * We assert that the resolved chain's field order follows the embedded schema
     * (label first), not the local serialForm() (id first), and prove it with a
     * positional decode that would throw if the local order had been used.
     */
    @Test
    void test_7_3_2_Embedded_UsedWhenDigestMismatch() throws Exception {
        String className = VersionedRecord.class.getName();

        // Embedded schema: SWAPPED relative to local serialForm()
        //   Local order:    [id:int, label:String, extra:String]
        //   Embedded order: [label:String, id:int, extra:String]
        AtomicSerialSchemaRecord embeddedRecord = new AtomicSerialSchemaRecord(
                className, (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("label", "java.lang.String"),
                    new AtomicSerialFieldDef("id",    "int"),
                    new AtomicSerialFieldDef("extra", "java.lang.String")
                ));
        SchemaChain.Result embeddedChain =
                SchemaChain.linkAndGetLeafDigest(List.of(embeddedRecord));

        // Payload in EMBEDDED order: String, int, String
        byte[] innerSeq = DerWriter.writeSequence(List.of(
                DerWriter.writeUtf8String("the-label"),
                DerWriter.writeInteger(BigInteger.valueOf(77)),
                DerWriter.writeUtf8String("the-extra")
        ));
        byte[] hierarchyPayload = DerWriter.writeSequence(List.of(innerSeq));

        MarshalledInstanceRecord rec = new MarshalledInstanceRecord(
                hierarchyPayload,
                embeddedRecord.encode(),
                embeddedChain.leafDigest(),
                MarshalledInstanceRecord.PAYLOAD_FORMAT);

        // Pre-condition: embedded digest != local serialForm() digest
        SchemaChain.Result localChain = SchemaGenerator.generateChain(VersionedRecord.class);
        assertFalse(java.util.Arrays.equals(localChain.leafDigest(), rec.schemaDigest()),
                "Pre-condition: embedded digest must differ from local digest");

        SchemaResolver.Result result = SchemaResolver.resolve(rec, VersionedRecord.class);

        assertEquals(SchemaResolver.Branch.EMBEDDED, result.branch(),
                "Must take EMBEDDED branch when digest mismatches");

        // The resolved chain's leaf record must have the EMBEDDED field order
        List<AtomicSerialFieldDef> resolvedFields = result.chain().chain().get(0).fields();
        assertEquals("label", resolvedFields.get(0).wireName(),
                "Resolved chain field[0] must be 'label' (embedded order, not local)");
        assertEquals("id", resolvedFields.get(1).wireName(),
                "Resolved chain field[1] must be 'id' (embedded order, not local)");

        // Decode using the resolved chain to prove the embedded schema drove positioning.
        // If the local schema had been used instead, position 0 (UTF8String) would be
        // read as int 'id' and throw -- successful decode with correct values proves
        // the embedded schema was used.
        VersionedRecord decoded =
                ObjectCodec.decodeHierarchy(VersionedRecord.class, result.chain(), hierarchyPayload);
        assertEquals(77,          decoded.getId(),    "id from embedded position 1 (int)");
        assertEquals("the-label", decoded.getLabel(), "label from embedded position 0 (String)");
        assertEquals("the-extra", decoded.getExtra(), "extra from embedded position 2");
    }

    // =========================================================================
    // 7.3.3 -- REJECT: embedded schema absent -> DerException, no fallback
    // =========================================================================

    /**
     * 7.3.3 -- S7.8 requires the embedded schema unconditionally; a record with
     * empty {@code schemaBytes} is non-conforming and MUST be rejected (S12.4).
     * There is no fallback to a registry or to the local schema with defaults
     * (design principle 6).
     */
    @Test
    void test_7_3_3_EmbeddedAbsent_Rejected() {
        // A digest for some schema -- irrelevant, the empty schemaBytes must reject first
        byte[] someDigest = new byte[32];
        java.util.Arrays.fill(someDigest, (byte) 0x42);

        byte[] innerSeq = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.valueOf(9)),
                DerWriter.writeUtf8String("no-schema")
        ));
        byte[] hierarchyPayload = DerWriter.writeSequence(List.of(innerSeq));

        MarshalledInstanceRecord rec = new MarshalledInstanceRecord(
                hierarchyPayload,
                new byte[0],   // absent embedded schema -- non-conforming
                someDigest,
                MarshalledInstanceRecord.PAYLOAD_FORMAT);

        assertThrows(DerException.class,
                () -> SchemaResolver.resolve(rec, VersionedRecord.class),
                "A record without an embedded schema must be rejected -- no registry or"
                + " local-schema fallback exists at decode time (v0.13 S12.4)");
    }

    // =========================================================================
    // 7.3.4 -- REJECT: schemaDigest does not match the embedded leaf record
    // =========================================================================

    /**
     * 7.3.4 -- The digest field is a routing hint that MUST be verified against the
     * embedded leaf record before use (S7.8). A lying digest -- here, one that
     * impersonates the receiver's CURRENT local schema while the embedded schema
     * differs -- would otherwise route the fast-path comparison to the wrong
     * schema. It must be rejected.
     */
    @Test
    void test_7_3_4_LyingDigest_Rejected() throws Exception {
        String className = VersionedRecord.class.getName();

        // Embedded schema: two fields only
        AtomicSerialSchemaRecord embeddedRecord = new AtomicSerialSchemaRecord(
                className, (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("id",    "int"),
                    new AtomicSerialFieldDef("label", "java.lang.String")
                ));

        // Lying digest: the receiver's CURRENT (three-field) digest
        byte[] lyingDigest = SchemaGenerator.generateChain(VersionedRecord.class).leafDigest();

        byte[] innerSeq = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.valueOf(1)),
                DerWriter.writeUtf8String("lying")
        ));
        byte[] hierarchyPayload = DerWriter.writeSequence(List.of(innerSeq));

        MarshalledInstanceRecord rec = new MarshalledInstanceRecord(
                hierarchyPayload,
                embeddedRecord.encode(),
                lyingDigest,
                MarshalledInstanceRecord.PAYLOAD_FORMAT);

        assertThrows(DerException.class,
                () -> SchemaResolver.resolve(rec, VersionedRecord.class),
                "A schemaDigest that does not match the embedded leaf record must be rejected");
    }
}
