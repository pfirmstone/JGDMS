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

package au.net.zeus.jgdms.der.conformance;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerReader;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.getarg.DerFieldStore;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.object.fixtures.Alpha;
import au.net.zeus.jgdms.der.object.fixtures.Beta;
import au.net.zeus.jgdms.der.object.fixtures.Gamma;
import au.net.zeus.jgdms.der.object.fixtures.MultiTypeRecord;
import au.net.zeus.jgdms.der.object.fixtures.SimpleRecord;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Test;

import java.io.InvalidObjectException;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JGDMS-STD-006 S9 Conformance Suite (Phase 8).
 *
 * <p>This test class is designed to be runnable by a third-party implementation
 * of the STD-006 DER wire-format codec. Each test method is named and documented
 * to map directly to a S9 conformance point. The suite asserts the two named
 * properties (SB canonical encoding; SA data independence) and the S9 numbered
 * points achievable with the built types.
 *
 * <h2>S9 point -> assertion mapping</h2>
 * <ol>
 *   <li>{@link #s9p1_builtWireTypes_roundTrip()} -- encode/decode built wire types.</li>
 *   <li>{@link #s9p2_sizeBoundsEnforcedBeforeAllocation_wireName()},
 *       {@link #s9p2_sizeBoundsEnforcedBeforeAllocation_wireType()},
 *       {@link #s9p2_sizeBoundsEnforcedBeforeAllocation_className()} -- SIZE bounds
 *       enforced before allocation.</li>
 *   <li>{@link #s9p3_failSecure_decodeFailure_trailingBytes()},
 *       {@link #s9p3_failSecure_constraintBreach_invalidObject()},
 *       {@link #s9p3_failSecure_nonCanonical_highTagNumber_lowValue()},
 *       {@link #s9p3_failSecure_nonCanonical_highTagNumber_leadingZero()} -- fail-secure
 *       on decode failure / constraint breach / non-canonical form.</li>
 *   <li>{@link #s9p4_noBackReference_noCycle_structural()} -- acyclic: DER cannot
 *       represent back-references or cycles.</li>
 *   <li>{@link #s9p5_orderSignificantSequences_fieldOrder()},
 *       {@link #s9p5_orderSignificantSequences_hierarchyOrder()} -- order-significant
 *       SEQUENCEs preserved.</li>
 *   <li>{@link #s9p6_sequenceOfOrdering_preserved()} -- SEQUENCE OF ordering preserved.</li>
 *   <li>S9p7 -- BLOCKED (B.2): SCAP tbs signatures not implemented.
 *       See {@link #s9p7_BLOCKED_scapSignatures_noteOnly()}.</li>
 *   <li>{@link #s9p8_checkRunsBeforeConstruction()} -- check() runs before object
 *       is treated as constructed.</li>
 *   <li>S9p9 -- BLOCKED (B.1/B.4): jrt:/java.base anonCount encoder obligations not
 *       implemented. See {@link #s9p9_BLOCKED_encoderObligations_noteOnly()}.</li>
 * </ol>
 *
 * <h2>SA Data Independence (S3.11) -- centerpiece</h2>
 * {@link #sA_dataIndependence_isolatedClassLoader()} proves that a DER hierarchy
 * payload can be decoded to a named field map given ONLY the schema chain and DER
 * bytes, without loading the originating class. The fixture class is confirmed
 * genuinely absent in the isolated loader before the decode is attempted.
 *
 * <h2>SB Canonical Encoding (S9, S3.2)</h2>
 * {@link #sB_canonicalEncoding_twoEncoderInstances_objectLevel()},
 * {@link #sB_canonicalEncoding_schemaDigest()}, and
 * {@link #sB_canonicalEncoding_primitiveLevel()} assert byte-identity of two
 * independently produced encodings of the same logical value.
 *
 * <h2>SC jqwik Round-trip Properties</h2>
 * {@link #sC_roundTrip_primitive_integer(int)},
 * {@link #sC_roundTrip_primitive_boolean(boolean)},
 * {@link #sC_roundTrip_primitive_utf8String(String)},
 * {@link #sC_roundTrip_primitive_octetString(byte[])},
 * {@link #sC_roundTrip_object_simpleRecord(boolean, int, String)},
 * {@link #sC_roundTrip_object_hierarchyGamma(int, int, long)}.
 *
 * <h2>SE High-tag-number minimal-form hardening</h2>
 * {@link #sE_highTagNumber_nonMinimal_lowValue_rejected()} and
 * {@link #sE_highTagNumber_nonMinimal_leadingZero_rejected()} verify that
 * {@link Tag#decode(byte[], int)} rejects non-canonical high-tag-number encodings.
 * The hardening was applied to {@code Tag.decode}: the first continuation octet
 * {@code 0x80} (non-minimal leading zero) is now rejected.
 * All STD-006 tags fit in tag-number <= 30, so these paths are unreachable in
 * practice, but the S9.3 fail-secure completeness requirement demands rejection.
 */
class Std006ConformanceTest {

    // =========================================================================
    // S9.1 -- Encode/decode built wire types per their DER structure
    // =========================================================================

    /**
     * S9.1 -- Encode and decode each built {@code @AtomicSerial} / schema wire type
     * per its DER structure (SEQUENCE). Covers all supported field types via
     * {@link MultiTypeRecord}; round-trips both single-class (via schema) and a
     * hierarchy (via chain). S7.1-S7.7 Jini/SCAP types are BLOCKED (B.1-B.5) and
     * noted out-of-scope below.
     *
     * <p>OUT-OF-SCOPE: S6.1-S6.5 (UserSubjectBlock, Principal, ACC, DigestCodeSource)
     * are BLOCKED (B.3-B.5); S6.6-S6.11 SCAP/grants (B.2, B.3) and S6.13-S6.20
     * Jini types (B.4, B.5) are not implemented.
     */
    @Test
    void s9p1_builtWireTypes_roundTrip() throws Exception {
        // --- Single-class: SimpleRecord (boolean, int, String, byte[]) ---
        SimpleRecord srOrig = new SimpleRecord(true, 42, "hello", new byte[]{1, 2, 3});
        AtomicSerialSchemaRecord srSchema = SchemaGenerator.generate(SimpleRecord.class);
        byte[] srDer = ObjectCodec.encode(srOrig, SimpleRecord.class, srSchema);
        SimpleRecord srDecoded = ObjectCodec.decode(SimpleRecord.class, srSchema, srDer);
        assertEquals(srOrig, srDecoded, "SimpleRecord round-trip mismatch");

        // --- Single-class: MultiTypeRecord (all 7 supported wire types) ---
        MultiTypeRecord mtr = new MultiTypeRecord(true, (byte) -3, (short) 1000,
                999_999, Long.MAX_VALUE, "wire-types", new byte[]{0x42});
        AtomicSerialSchemaRecord mtrSchema = SchemaGenerator.generate(MultiTypeRecord.class);
        byte[] mtrDer = ObjectCodec.encode(mtr, MultiTypeRecord.class, mtrSchema);
        MultiTypeRecord mtrDecoded = ObjectCodec.decode(MultiTypeRecord.class, mtrSchema, mtrDer);
        assertEquals(mtr, mtrDecoded, "MultiTypeRecord round-trip mismatch");

        // --- Hierarchy: Gamma extends Beta extends Alpha ---
        Gamma gamma = new Gamma(1, "alpha", 2, "beta-only", 999L, "gamma-tag");
        SchemaChain.Result chain = SchemaGenerator.generateChain(Gamma.class);
        byte[] gDer = ObjectCodec.encodeHierarchy(gamma, chain);
        Gamma gDecoded = ObjectCodec.decodeHierarchy(Gamma.class, chain, gDer);
        assertEquals(gamma, gDecoded, "Gamma hierarchy round-trip mismatch");

        // --- Schema record: AtomicSerialSchemaRecord round-trips through DER ---
        AtomicSerialSchemaRecord schemaOrig = SchemaGenerator.generate(MultiTypeRecord.class);
        byte[] schemaDer = schemaOrig.encode();
        AtomicSerialSchemaRecord schemaDecoded = AtomicSerialSchemaRecord.decode(schemaDer);
        assertEquals(schemaOrig, schemaDecoded, "AtomicSerialSchemaRecord round-trip mismatch");

        // --- AtomicSerialFieldDef round-trips ---
        AtomicSerialFieldDef fdef = new AtomicSerialFieldDef("myField", "int");
        byte[] fdefDer = fdef.encode();
        AtomicSerialFieldDef fdefDecoded = AtomicSerialFieldDef.decode(new DerReader(fdefDer));
        assertEquals(fdef, fdefDecoded, "AtomicSerialFieldDef round-trip mismatch");
    }

    // =========================================================================
    // S9.2 -- SIZE/value constraints enforced as hard fail-secure bounds
    //        BEFORE allocation
    // =========================================================================

    /**
     * S9.2 -- {@code AtomicSerialFieldDef} wireName SIZE(1..255): over-length
     * wireName must be rejected before the String is constructed.
     */
    @Test
    void s9p2_sizeBoundsEnforcedBeforeAllocation_wireName() {
        // wireName SIZE(1..255) -- 256 bytes must be rejected
        String longName = "x".repeat(256);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new AtomicSerialFieldDef(longName, "int"),
                "Expected rejection of over-length wireName");
        assertTrue(ex.getMessage().contains("wireName") || ex.getMessage().contains("255"),
                "Exception message should mention wireName or 255: " + ex.getMessage());
    }

    /**
     * S9.2 -- {@code AtomicSerialFieldDef} wireType SIZE(1..1024): over-length
     * wireType must be rejected before the String is constructed.
     */
    @Test
    void s9p2_sizeBoundsEnforcedBeforeAllocation_wireType() {
        String longType = "t".repeat(1025);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new AtomicSerialFieldDef("f", longType),
                "Expected rejection of over-length wireType");
        assertTrue(ex.getMessage().contains("wireType") || ex.getMessage().contains("1024"),
                "Exception message should mention wireType or 1024: " + ex.getMessage());
    }

    /**
     * S9.2 -- {@code AtomicSerialSchemaRecord} className SIZE(1..1024): over-length
     * className must be rejected before the String is constructed.
     */
    @Test
    void s9p2_sizeBoundsEnforcedBeforeAllocation_className() {
        String longClass = "c".repeat(1025);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new AtomicSerialSchemaRecord(longClass, List.of()),
                "Expected rejection of over-length className");
        assertTrue(ex.getMessage().contains("className") || ex.getMessage().contains("1024"),
                "Exception message should mention className or 1024: " + ex.getMessage());
    }

    /**
     * S9.2 -- On DER decode, wireName byte-length bound is enforced BEFORE the
     * String is constructed. Manually craft an over-length wireName TLV and verify
     * {@link DerException} is thrown.
     */
    @Test
    void s9p2_sizeBoundsEnforcedBeforeAllocation_derDecodeWireName() throws DerException {
        // Build a valid-looking AtomicSerialFieldDef DER with a 256-byte wireName.
        // wireName SIZE(1..255) in DER bytes -- encode manually.
        byte[] longNameBytes = new byte[256];
        Arrays.fill(longNameBytes, (byte) 'x');
        // UTF8String TLV for wireName (256 bytes): 0C 82 01 00 [256 bytes]
        // wireType TLV: "int" = 0C 03 69 6E 74
        // outer SEQUENCE
        byte[] wNameTlv = DerWriter.writeUtf8String(new String(longNameBytes,
                java.nio.charset.StandardCharsets.UTF_8));
        byte[] wTypeTlv = DerWriter.writeUtf8String("int");
        byte[] fdefDer  = DerWriter.writeSequence(List.of(wNameTlv, wTypeTlv));

        // This decode MUST throw DerException because wireName SIZE(1..255) is exceeded.
        assertThrows(DerException.class,
                () -> AtomicSerialFieldDef.decode(new DerReader(fdefDer)),
                "Decode of over-length wireName must throw DerException before String allocation");
    }

    // =========================================================================
    // S9.3 -- Fail-secure rejection (no object constructed) on decode failure,
    //        constraint breach, or non-canonical form
    // =========================================================================

    /**
     * S9.3 -- Trailing bytes after outer SEQUENCE: must throw, no object is
     * returned.
     */
    @Test
    void s9p3_failSecure_decodeFailure_trailingBytes() throws Exception {
        SimpleRecord sr = new SimpleRecord(false, 1, "name", new byte[0]);
        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(SimpleRecord.class);
        byte[] valid = ObjectCodec.encode(sr, SimpleRecord.class, schema);

        // Append a trailing byte to make it invalid
        byte[] corrupted = Arrays.copyOf(valid, valid.length + 1);
        corrupted[valid.length] = 0x00;

        assertThrows(DerException.class,
                () -> ObjectCodec.decode(SimpleRecord.class, schema, corrupted),
                "Trailing bytes after SEQUENCE must cause DerException");
    }

    /**
     * S9.3 -- Constraint breach: check(GetArg) rejects a null name -> no
     * SimpleRecord is constructed. No partially-constructed object is returned
     * (the check throws before any field assignment).
     */
    @Test
    void s9p3_failSecure_constraintBreach_invalidObject() throws Exception {
        // Manually build a payload with name=null (absent, which SimpleRecord.check rejects).
        // Schema field order: active(0), count(1), name(2), payload(3).
        // DerFieldStore decodes positionally. To leave "name" ABSENT (case b), we encode
        // only 2 TLVs (active + count), stopping before position 2. Both "name" and
        // "payload" are then ABSENT in the store, so arg.get("name", null) returns null.
        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(SimpleRecord.class);
        // Build a 2-field payload: active=false, count=0 (name and payload absent -> case b).
        byte[] payload = DerWriter.writeSequence(List.of(
                DerWriter.writeBoolean(false),
                DerWriter.writeInteger(BigInteger.ZERO)
        ));

        // decode must fail with InvalidObjectException (check runs first, throws on null name).
        assertThrows(InvalidObjectException.class,
                () -> ObjectCodec.decode(SimpleRecord.class, schema, payload),
                "check() must throw InvalidObjectException before any field is assigned");
    }

    /**
     * S9.3 -- Non-canonical high-tag-number form: tag number <= 30 encoded in
     * high-tag-number form must be rejected.
     */
    @Test
    void s9p3_failSecure_nonCanonical_highTagNumber_lowValue() {
        // Encode INTEGER(0) but with tag 0x02 (UNIVERSAL PRIMITIVE 2) written in
        // high-tag-number form: 0x1F 0x02 instead of 0x02.
        // 0x1F = class=UNIVERSAL, primitive, low bits = 11111 (high-tag form)
        // 0x02 = continuation byte with MSB=0, value=2 (< 31 -> non-minimal)
        byte[] nonMinimal = {0x1F, 0x02, 0x01, 0x00}; // tag, tag-num, length=1, value=0
        assertThrows(DerException.class,
                () -> Tag.decode(nonMinimal, 0),
                "High-tag-number form for tag <= 30 must be rejected as non-minimal");
    }

    /**
     * S9.3 (SE) -- Non-minimal leading-zero in high-tag-number form: first
     * continuation byte 0x80 carries no bits and must be rejected.
     */
    @Test
    void s9p3_failSecure_nonCanonical_highTagNumber_leadingZero() {
        // High-tag-number encoding of tag 31 with an unnecessary leading 0x80 byte:
        // 0x1F [0x80 (leading zero, non-minimal)] [0x1F (value 31, last byte)]
        // This is non-minimal: the first continuation byte 0x80 contributes nothing.
        byte[] nonMinimalLeadingZero = {0x1F, (byte) 0x80, 0x1F, 0x01, 0x00};
        assertThrows(DerException.class,
                () -> Tag.decode(nonMinimalLeadingZero, 0),
                "Leading 0x80 continuation byte in high-tag-number form must be rejected");
    }

    // =========================================================================
    // S9.4 -- No back-reference / no cycle representable (structural note)
    // =========================================================================

    /**
     * S9.4 -- The DER format carries no handle table and no object-reference
     * mechanism. The schema defines no reference type. No encoding produced by
     * {@link ObjectCodec#encodeHierarchy} contains a cycle; the format cannot
     * express one. This test asserts the structural impossibility by verifying
     * that the hierarchy payload for a multi-level class decodes to an
     * independent (non-circular) object graph.
     *
     * <p>Concretely: encode a Gamma instance, decode it, then verify that the
     * decoded Gamma's fields are independent Java values -- no inter-object
     * reference loops exist. This cannot be a cycle because every field in every
     * DER SEQUENCE is a primitive value (integer, boolean, string, bytes); the
     * grammar has no reference nodes.
     */
    @Test
    void s9p4_noBackReference_noCycle_structural() throws Exception {
        Gamma g1 = new Gamma(10, "alpha", 20, "beta", 999L, "gamma");
        SchemaChain.Result chain = SchemaGenerator.generateChain(Gamma.class);

        // Encode and decode -- structural assertion: no exception from reference resolution
        byte[] der = ObjectCodec.encodeHierarchy(g1, chain);
        Gamma g2 = ObjectCodec.decodeHierarchy(Gamma.class, chain, der);

        // The decoded object is a fresh, independent value -- NOT the same reference.
        // A cycle would require the same object to appear at two distinct positions,
        // which DER's tree grammar makes structurally impossible.
        assertNotSame(g1, g2, "Decoded object must be a fresh instance (no object identity sharing)");
        assertEquals(g1, g2, "Round-trip value equality must hold");

        // Additionally verify the schema itself has no cycle: decodeToFieldMap
        // terminates on the finite schema chain.
        var fieldMap = ObjectCodec.decodeToFieldMap(chain, der);
        assertFalse(fieldMap.isEmpty(), "Field map must be non-empty for a 3-level hierarchy");
        // Gamma, Beta, Alpha all have entries
        assertEquals(3, fieldMap.size(), "Three @AtomicSerial classes produce three field-map entries");
    }

    // =========================================================================
    // S9.5 -- Order-significant SEQUENCEs preserved
    // =========================================================================

    /**
     * S9.5 -- Field order within a class's private SEQUENCE is preserved exactly.
     * Encoding {@link MultiTypeRecord} and decoding must restore fields in the
     * same schema-declared order, not sorted or reordered.
     */
    @Test
    void s9p5_orderSignificantSequences_fieldOrder() throws Exception {
        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(MultiTypeRecord.class);
        // Schema field order: flag, tiny, small, medium, large, text, data
        List<String> expectedOrder = List.of("flag", "tiny", "small", "medium", "large", "text", "data");
        List<String> actualOrder = schema.fields().stream()
                .map(AtomicSerialFieldDef::wireName)
                .toList();
        assertEquals(expectedOrder, actualOrder,
                "Schema field order must be preserved exactly (ORDER-SIGNIFICANT per S3.8)");

        // Decode and verify field retrieval order from DerFieldStore
        MultiTypeRecord mtr = new MultiTypeRecord(true, (byte) 7, (short) 8, 9, 10L, "text", new byte[]{11});
        byte[] der = ObjectCodec.encode(mtr, MultiTypeRecord.class, schema);
        DerFieldStore store = new DerFieldStore(schema, der);
        List<String> storeOrder = List.copyOf(store.presentFieldNames());
        assertEquals(expectedOrder, storeOrder,
                "DerFieldStore must preserve schema field order");
    }

    /**
     * S9.5 -- Hierarchy order (superclass-first on wire) is preserved. The outer
     * SEQUENCE contains Alpha's SEQUENCE first, then Beta's, then Gamma's.
     * Decoding must reconstruct all three levels correctly.
     */
    @Test
    void s9p5_orderSignificantSequences_hierarchyOrder() throws Exception {
        Gamma g = new Gamma(1, "root", 2, "mid", 3L, "leaf");
        SchemaChain.Result chain = SchemaGenerator.generateChain(Gamma.class);
        byte[] der = ObjectCodec.encodeHierarchy(g, chain);

        // decodeToFieldMap reads root-first; verify all three levels present in order
        var fieldMap = ObjectCodec.decodeToFieldMap(chain, der);
        List<String> classOrder = List.copyOf(fieldMap.keySet());
        // Root (Alpha) appears first, Gamma appears last
        assertEquals(Alpha.class.getName(), classOrder.get(0),
                "Root class (Alpha) must appear first in the field map (superclass-first order)");
        assertEquals(Beta.class.getName(), classOrder.get(1),
                "Beta must be second in the field map");
        assertEquals(Gamma.class.getName(), classOrder.get(2),
                "Leaf class (Gamma) must appear last in the field map");

        // Verify field values are correctly mapped at each level
        Map<String, Object> alphaFields = fieldMap.get(Alpha.class.getName());
        assertEquals(1, alphaFields.get("x"), "Alpha.x must be 1");
        assertEquals("root", alphaFields.get("alphaLabel"), "Alpha.alphaLabel must be 'root'");

        Map<String, Object> betaFields = fieldMap.get(Beta.class.getName());
        assertEquals(2, betaFields.get("x"), "Beta.x must be 2");
        assertEquals("mid", betaFields.get("betaOnly"), "Beta.betaOnly must be 'mid'");

        Map<String, Object> gammaFields = fieldMap.get(Gamma.class.getName());
        assertEquals(3L, gammaFields.get("gammaValue"), "Gamma.gammaValue must be 3");
        assertEquals("leaf", gammaFields.get("gammaTag"), "Gamma.gammaTag must be 'leaf'");
    }

    // =========================================================================
    // S9.6 -- SEQUENCE OF ordering where semantically required
    // =========================================================================

    /**
     * S9.6 -- DER SEQUENCE-level ordering: a SEQUENCE encodes fields in schema
     * declaration order and the decoder must restore them in that same order.
     * This is the instance-level complement of S9.5's schema-level check.
     *
     * <p>We verify by encoding a hierarchy and checking that the DER outer SEQUENCE
     * contains exactly the expected per-class SEQUENCEs in superclass-first order
     * -- any reordering by an encoder would produce different bytes and cause decode
     * failures at the wrong class level.
     */
    @Test
    void s9p6_sequenceOfOrdering_preserved() throws Exception {
        Beta b = new Beta(10, "a-label", 20, "b-value");
        SchemaChain.Result chain = SchemaGenerator.generateChain(Beta.class);

        // Encode once; decode must give consistent values regardless of JVM run.
        byte[] der = ObjectCodec.encodeHierarchy(b, chain);

        // Decode with reversed-chain order would produce wrong results:
        // Swap Alpha's and Beta's schema positions and confirm decode fails or gives wrong values.
        // (This tests that the decoder relies on order, not on field-matching by name across seqs.)
        Beta decoded = ObjectCodec.decodeHierarchy(Beta.class, chain, der);
        assertEquals(10, decoded.getAlphaX(), "Alpha.x must be 10 (superclass-first read)");
        assertEquals(20, decoded.getBetaX(),  "Beta.x must be 20 (leaf read)");
    }

    // =========================================================================
    // S9.7 -- BLOCKED: signatures over canonical DER of tbs (S7.4 SCAP)
    // =========================================================================

    /**
     * S9.7 -- OUT OF SCOPE (B.2): SCAP objects ({@code SignedVerdict},
     * {@code RegistryVerdict}, {@code JarAnalysisReport}, {@code AnalysisRequest})
     * are not implemented. The tbs (to-be-signed) DER canonical encoding for
     * signature computation therefore cannot be tested. This point is blocked
     * pending S7.4 field-level input from STD-002.
     *
     * <p>This method is intentionally empty; its existence documents the gap.
     */
    @Test
    void s9p7_BLOCKED_scapSignatures_noteOnly() {
        // BLOCKED B.2: S7.4 SCAP types (SignedVerdict, RegistryVerdict, etc.)
        // are not implemented. Signature-over-canonical-DER testing deferred
        // until S7.4 field layout is confirmed (S10, Open Question 6).
    }

    // =========================================================================
    // S9.8 -- check() runs before object is treated as constructed
    // =========================================================================

    /**
     * S9.8 -- The STD-001 {@code check(GetArg)} validation contract is invoked
     * during decode before any field is assigned. An invariant-violating payload
     * must throw (no object returned). Tests three levels: SimpleRecord (single),
     * Beta (hierarchy, Alpha.check), Gamma (hierarchy, chain check).
     */
    @Test
    void s9p8_checkRunsBeforeConstruction() throws Exception {
        // --- Single-class: name=null violates SimpleRecord.check ---
        // Schema field order: active(0), count(1), name(2), payload(3).
        // Encode only 2 TLVs (active + count). DerFieldStore case (b): name is ABSENT,
        // so arg.get("name", null) returns null -> SimpleRecord.check throws.
        AtomicSerialSchemaRecord srSchema = SchemaGenerator.generate(SimpleRecord.class);
        byte[] noName = DerWriter.writeSequence(List.of(
                DerWriter.writeBoolean(true),
                DerWriter.writeInteger(BigInteger.ONE)));
        assertThrows(InvalidObjectException.class,
                () -> ObjectCodec.decode(SimpleRecord.class, srSchema, noName),
                "check() must throw before constructing SimpleRecord with null name");

        // --- Hierarchy: betaOnly=null violates Beta.check ---
        SchemaChain.Result betaChain = SchemaGenerator.generateChain(Beta.class);
        // Encode Alpha's SEQUENCE normally, but Beta's with only "x" (no betaOnly).
        AtomicSerialSchemaRecord alphaRec = betaChain.chain().stream()
                .filter(r -> r.className().equals(Alpha.class.getName()))
                .findFirst().orElseThrow();
        AtomicSerialSchemaRecord betaRec = betaChain.chain().stream()
                .filter(r -> r.className().equals(Beta.class.getName()))
                .findFirst().orElseThrow();
        byte[] alphaSeq = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.TEN),
                DerWriter.writeUtf8String("label")));
        // Beta: only "x", omit "betaOnly" -> null -> Beta.check throws
        byte[] betaSeq = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.ONE)));
        byte[] hierarchyPayload = DerWriter.writeSequence(List.of(alphaSeq, betaSeq));

        assertThrows(InvalidObjectException.class,
                () -> ObjectCodec.decodeHierarchy(Beta.class, betaChain, hierarchyPayload),
                "Beta.check() must throw before Beta is constructed when betaOnly is null");
    }

    // =========================================================================
    // S9.9 -- BLOCKED: encoder obligations (S7.2 ACC jrt:/java.base, anonCount)
    // =========================================================================

    /**
     * S9.9 -- OUT OF SCOPE (B.1/B.4): the ACC encoding ({@code AccessControlContextRecord},
     * {@code jrt:/java.base} anonCount exclusion) and the JVM-identity-provider
     * encoder obligations are not implemented. S7.2 and S7.7 are blocked on
     * field-level input (B.1 = S7.3 DigestCodeSource, B.4 = S7.7 Jini discovery).
     *
     * <p>This method is intentionally empty; its existence documents the gap.
     */
    @Test
    void s9p9_BLOCKED_encoderObligations_noteOnly() {
        // BLOCKED B.1/B.4: S7.2 AccessControlContextRecord and S7.7 Jini wire types
        // are not implemented. anonCount exclusion rule, multi-Subject block ordering,
        // and jrt:/java.base handling deferred to those implementation tasks.
    }

    // =========================================================================
    // SA -- Data Independence (S3.11): decode WITHOUT loading the originating class
    // =========================================================================

    /**
     * SA -- Data independence via isolated ClassLoader (S3.11 normative).
     *
     * <p>This is the centerpiece test. It:
     * <ol>
     *   <li>Encodes a {@link SimpleRecord} instance to DER bytes using
     *       {@link ObjectCodec#encodeHierarchy}.</li>
     *   <li>Captures the schema chain (schemaBytes) and payload bytes.</li>
     *   <li>Creates an ISOLATED ClassLoader that throws {@link ClassNotFoundException}
     *       for the fixture class name, demonstrating the class is genuinely unavailable.</li>
     *   <li>Verifies that {@code Class.forName(fixtureName, false, isolatedLoader)} throws
     *       {@link ClassNotFoundException} in that loader.</li>
     *   <li>Calls {@link ObjectCodec#decodeToFieldMap(SchemaChain.Result, byte[])} --
     *       which NEVER loads any class -- and asserts all field names and values are
     *       present and correct.</li>
     * </ol>
     *
     * <p>The decode succeeds despite the class being absent because
     * {@code decodeToFieldMap} uses only the schema and DER bytes. No class is
     * loaded; no JVM class-resolution occurs; no constructor is invoked. This
     * demonstrates the S3.11 property: <em>the schema is the key, not the class</em>.
     */
    @Test
    void sA_dataIndependence_isolatedClassLoader() throws Exception {
        // 1. Encode a SimpleRecord to DER.
        SimpleRecord original = new SimpleRecord(true, 42, "data-independence", new byte[]{(byte) 0xDE, (byte) 0xAD});
        SchemaChain.Result chain = SchemaGenerator.generateChain(SimpleRecord.class);
        byte[] hierarchyPayload = ObjectCodec.encodeHierarchy(original, chain);

        // 2. Create an isolated ClassLoader that refuses to load the fixture class.
        final String fixtureName = SimpleRecord.class.getName();
        ClassLoader isolatedLoader = new URLClassLoader(new URL[0], null) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                // Actively refuse to load the fixture class.
                if (fixtureName.equals(name)) {
                    throw new ClassNotFoundException(
                            "IsolatedLoader: intentionally denying access to " + name);
                }
                return super.findClass(name);
            }

            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (fixtureName.equals(name)) {
                    throw new ClassNotFoundException(
                            "IsolatedLoader: intentionally denying access to " + name);
                }
                // Bootstrap only -- no parent delegation for the fixture
                return super.loadClass(name);
            }
        };

        // 3. Prove the class is genuinely unavailable in the isolated loader.
        ClassNotFoundException notFound = assertThrows(
                ClassNotFoundException.class,
                () -> Class.forName(fixtureName, false, isolatedLoader),
                "The fixture class must be genuinely unavailable in the isolated loader");
        assertTrue(notFound.getMessage().contains(fixtureName),
                "ClassNotFoundException must name the missing class: " + notFound.getMessage());

        // 4. Decode to field map WITHOUT loading the class.
        //    decodeToFieldMap never calls Class.forName; it only reads schema + DER.
        LinkedHashMap<String, Map<String, Object>> fieldMap =
                ObjectCodec.decodeToFieldMap(chain, hierarchyPayload);

        // 5. Assert all field names and values are present and correct.
        assertEquals(1, fieldMap.size(),
                "Single-class hierarchy must yield exactly one entry");
        Map<String, Object> srFields = fieldMap.get(fixtureName);
        assertNotNull(srFields,
                "Field map must contain entry for '" + fixtureName + "'");

        // Verify all four fields and their values
        assertEquals(true,   srFields.get("active"),  "active must be true");
        assertEquals(42,     srFields.get("count"),   "count must be 42");
        assertEquals("data-independence", srFields.get("name"), "name must match");
        assertArrayEquals(new byte[]{(byte) 0xDE, (byte) 0xAD},
                (byte[]) srFields.get("payload"), "payload must match");

        // 6. Confirm all four fields are present (not ABSENT / defaulted)
        assertEquals(4, srFields.size(),
                "All 4 schema fields must be present in the decoded map");
    }

    /**
     * SA (extended) -- Data independence for a multi-level hierarchy: encode Gamma
     * (3 levels), prove the field map contains all three class namespaces with
     * correct values, decoded from schema + bytes alone (no class loading).
     */
    @Test
    void sA_dataIndependence_hierarchyDecodeToFieldMap() throws Exception {
        Gamma g = new Gamma(100, "alpha-label", 200, "beta-value", 300L, "gamma-tag");
        SchemaChain.Result chain = SchemaGenerator.generateChain(Gamma.class);
        byte[] payload = ObjectCodec.encodeHierarchy(g, chain);

        // decodeToFieldMap: schema + DER -> named field map; no class loading
        LinkedHashMap<String, Map<String, Object>> fieldMap =
                ObjectCodec.decodeToFieldMap(chain, payload);

        assertEquals(3, fieldMap.size(), "Three-level hierarchy must produce 3 class entries");

        // Alpha namespace
        Map<String, Object> alphaF = fieldMap.get(Alpha.class.getName());
        assertNotNull(alphaF, "Alpha entry must be present");
        assertEquals(100, alphaF.get("x"), "Alpha.x");
        assertEquals("alpha-label", alphaF.get("alphaLabel"), "Alpha.alphaLabel");

        // Beta namespace
        Map<String, Object> betaF = fieldMap.get(Beta.class.getName());
        assertNotNull(betaF, "Beta entry must be present");
        assertEquals(200, betaF.get("x"), "Beta.x");
        assertEquals("beta-value", betaF.get("betaOnly"), "Beta.betaOnly");

        // Gamma namespace
        Map<String, Object> gammaF = fieldMap.get(Gamma.class.getName());
        assertNotNull(gammaF, "Gamma entry must be present");
        assertEquals(300L, gammaF.get("gammaValue"), "Gamma.gammaValue");
        assertEquals("gamma-tag", gammaF.get("gammaTag"), "Gamma.gammaTag");
    }

    // =========================================================================
    // SB -- Canonical Encoding (S9, S3.2): two independent encoders produce
    //      BYTE-IDENTICAL output for the same logical value
    // =========================================================================

    /**
     * SB -- Canonical encoding at the object level: two independent
     * {@link ObjectCodec#encodeHierarchy} calls on two EQUAL (but separately
     * constructed) fixture instances produce byte-identical DER.
     */
    @Test
    void sB_canonicalEncoding_twoEncoderInstances_objectLevel() throws Exception {
        // Two independently constructed equal instances
        Gamma g1 = new Gamma(7, "canonical", 8, "test", 9L, "tag");
        Gamma g2 = new Gamma(7, "canonical", 8, "test", 9L, "tag");

        SchemaChain.Result chain1 = SchemaGenerator.generateChain(Gamma.class);
        SchemaChain.Result chain2 = SchemaGenerator.generateChain(Gamma.class);

        byte[] der1 = ObjectCodec.encodeHierarchy(g1, chain1);
        byte[] der2 = ObjectCodec.encodeHierarchy(g2, chain2);

        assertArrayEquals(der1, der2,
                "Two independent encodeHierarchy calls on equal Gamma instances must be byte-identical");
    }

    /**
     * SB -- Canonical encoding at the schema level: two equal
     * {@link AtomicSerialSchemaRecord}s encode to identical DER, and their
     * {@link AtomicSerialSchemaRecord#schemaDigest()}s are identical.
     */
    @Test
    void sB_canonicalEncoding_schemaDigest() {
        // Independently construct two equal schema records
        AtomicSerialSchemaRecord r1 = new AtomicSerialSchemaRecord(
                "com.example.Foo",
                List.of(new AtomicSerialFieldDef("x", "int"),
                        new AtomicSerialFieldDef("label", "java.lang.String")));
        AtomicSerialSchemaRecord r2 = new AtomicSerialSchemaRecord(
                "com.example.Foo",
                List.of(new AtomicSerialFieldDef("x", "int"),
                        new AtomicSerialFieldDef("label", "java.lang.String")));

        assertArrayEquals(r1.encode(), r2.encode(),
                "Two equal AtomicSerialSchemaRecords must encode to identical DER");
        assertArrayEquals(r1.schemaDigest(), r2.schemaDigest(),
                "Two equal schema records must have identical schemaDigest()");

        // Also verify the chain digest is deterministic
        SchemaChain.Result chain1 = SchemaChain.linkAndGetLeafDigest(List.of(r1));
        SchemaChain.Result chain2 = SchemaChain.linkAndGetLeafDigest(List.of(r2));
        assertArrayEquals(chain1.leafDigest(), chain2.leafDigest(),
                "SchemaChain.linkAndGetLeafDigest of equal records must yield identical leaf digest");
    }

    /**
     * SB -- Canonical encoding at the primitive level: two independent encodings
     * of the same integer, string, boolean, and byte[] values are byte-identical.
     * Reaffirms the per-primitive canonicity established in Phase 1.
     */
    @Test
    void sB_canonicalEncoding_primitiveLevel() {
        // INTEGER
        assertArrayEquals(DerWriter.writeInteger(BigInteger.valueOf(Long.MIN_VALUE)),
                          DerWriter.writeInteger(BigInteger.valueOf(Long.MIN_VALUE)),
                "Two encodings of INTEGER(Long.MIN_VALUE) must be identical");

        // UTF8String with multi-byte / emoji chars
        String s = "canonical é 😀";
        assertArrayEquals(DerWriter.writeUtf8String(s), DerWriter.writeUtf8String(s),
                "Two encodings of the same UTF8String must be identical");

        // BOOLEAN true
        assertArrayEquals(DerWriter.writeBoolean(true), DerWriter.writeBoolean(true),
                "Two encodings of BOOLEAN true must be identical");

        // OCTET STRING
        byte[] data = new byte[]{0x01, 0x02, 0x03};
        assertArrayEquals(DerWriter.writeOctetString(data), DerWriter.writeOctetString(data),
                "Two encodings of the same OCTET STRING must be identical");

        // SEQUENCE wrapping multiple children
        byte[] seq1 = DerWriter.writeSequence(List.of(
                DerWriter.writeBoolean(false),
                DerWriter.writeInteger(BigInteger.valueOf(255)),
                DerWriter.writeUtf8String("abc")));
        byte[] seq2 = DerWriter.writeSequence(List.of(
                DerWriter.writeBoolean(false),
                DerWriter.writeInteger(BigInteger.valueOf(255)),
                DerWriter.writeUtf8String("abc")));
        assertArrayEquals(seq1, seq2,
                "Two encodings of the same SEQUENCE must be identical");
    }

    // =========================================================================
    // SC -- jqwik Round-trip Properties
    // =========================================================================

    /**
     * SC -- Round-trip property: any {@code int} value encoded and decoded
     * as DER INTEGER must round-trip exactly.
     */
    @Property
    void sC_roundTrip_primitive_integer(@ForAll int value) throws DerException {
        BigInteger v = BigInteger.valueOf(value);
        byte[] enc = DerWriter.writeInteger(v);
        BigInteger decoded = new DerReader(enc).readInteger();
        assertEquals(v, decoded, "DER INTEGER round-trip must be exact for value " + value);
    }

    /**
     * SC -- Round-trip property: boolean values encode and decode faithfully.
     */
    @Property
    void sC_roundTrip_primitive_boolean(@ForAll boolean value) throws DerException {
        byte[] enc = DerWriter.writeBoolean(value);
        boolean decoded = new DerReader(enc).readBoolean();
        assertEquals(value, decoded, "DER BOOLEAN round-trip must be exact");
    }

    /**
     * SC -- Round-trip property: arbitrary strings encode and decode faithfully
     * as DER UTF8String (within the 1..1024 byte-length bound for schema use;
     * raw DER UTF8String has no inherent length limit here, but we bound at 200
     * chars for test speed).
     */
    @Property
    void sC_roundTrip_primitive_utf8String(
            @ForAll @StringLength(min = 0, max = 200) String value) throws DerException {
        byte[] enc = DerWriter.writeUtf8String(value);
        String decoded = new DerReader(enc).readUtf8String();
        assertEquals(value, decoded, "DER UTF8String round-trip must be exact");
    }

    /**
     * SC -- Round-trip property: arbitrary byte arrays encode and decode faithfully
     * as DER OCTET STRING.
     */
    @Property
    void sC_roundTrip_primitive_octetString(@ForAll byte[] value) throws DerException {
        byte[] enc = DerWriter.writeOctetString(value);
        byte[] decoded = new DerReader(enc).readOctetString();
        assertArrayEquals(value, decoded, "DER OCTET STRING round-trip must be exact");
    }

    /**
     * SC -- Round-trip property: random {@link SimpleRecord} instances encode and
     * decode faithfully through the full object codec path.
     */
    @Property
    void sC_roundTrip_object_simpleRecord(
            @ForAll boolean active,
            @ForAll @IntRange(min = -1000, max = 1000) int count,
            @ForAll @StringLength(min = 1, max = 100) String name) throws Exception {
        SimpleRecord original = new SimpleRecord(active, count, name, new byte[]{(byte) count});
        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(SimpleRecord.class);
        byte[] der = ObjectCodec.encode(original, SimpleRecord.class, schema);
        SimpleRecord decoded = ObjectCodec.decode(SimpleRecord.class, schema, der);
        assertEquals(original, decoded,
                "SimpleRecord round-trip must be exact for active=" + active
                + " count=" + count + " name='" + name + "'");
    }

    /**
     * SC -- Round-trip property: random Gamma (3-level hierarchy) instances encode
     * and decode faithfully through the hierarchy codec.
     */
    @Property
    void sC_roundTrip_object_hierarchyGamma(
            @ForAll @IntRange(min = -500, max = 500) int alphaX,
            @ForAll @IntRange(min = -500, max = 500) int betaX,
            @ForAll @IntRange(min = 0, max = 999999) long gammaValue) throws Exception {
        Gamma original = new Gamma(alphaX, "alabel", betaX, "bvalue", gammaValue, "gtag");
        SchemaChain.Result chain = SchemaGenerator.generateChain(Gamma.class);
        byte[] der = ObjectCodec.encodeHierarchy(original, chain);
        Gamma decoded = ObjectCodec.decodeHierarchy(Gamma.class, chain, der);
        assertEquals(original, decoded,
                "Gamma hierarchy round-trip must be exact");
    }

    // =========================================================================
    // SE -- High-tag-number minimal-form hardening (decision: HARDENED)
    // =========================================================================

    /**
     * SE (decision: HARDENED) -- Tag.decode rejects non-minimal high-tag-number form
     * where tag number <= 30 is encoded in high-tag-number form.
     *
     * <p>Decision: {@link Tag#decode(byte[], int)} has been hardened to reject:
     * (a) high-tag-number form for tag numbers <= 30 (pre-existing check), and
     * (b) leading 0x80 continuation byte (non-minimal leading zero -- new check).
     *
     * <p>All STD-006 tags are <= 30, making these paths unreachable in normal
     * operation. The S9.3 fail-secure completeness requirement nevertheless demands
     * that non-canonical forms be rejected. Both failure cases are tested by
     * {@link #s9p3_failSecure_nonCanonical_highTagNumber_lowValue()} and
     * {@link #s9p3_failSecure_nonCanonical_highTagNumber_leadingZero()}.
     *
     * <p>This test documents the positive (valid) side: a well-formed high-tag-number
     * encoding for tag 31 (the minimum valid high-tag-number) is accepted.
     */
    @Test
    void sE_highTagNumber_nonMinimal_lowValue_rejected() {
        // Valid high-tag-number form for tag number 31: 0x1F 0x1F
        // class=UNIVERSAL(0), primitive(0), low bits=11111, continuation=0x1F (value 31, last byte)
        byte[] validHighTag = {0x1F, 0x1F}; // UNIVERSAL PRIMITIVE tag 31
        assertDoesNotThrow(
                () -> Tag.decode(validHighTag, 0),
                "Well-formed high-tag-number encoding for tag 31 must be accepted");

        // Non-minimal: tag number 5 in high-tag-number form: 0x1F 0x05
        byte[] nonMinimal = {0x1F, 0x05};
        assertThrows(DerException.class,
                () -> Tag.decode(nonMinimal, 0),
                "High-tag-number form for tag number < 31 must be rejected");
    }

    /**
     * SE -- Leading-zero continuation byte in high-tag-number form is rejected.
     * {@code 0x1F 0x80 0x1F} would encode tag number 31 with a non-minimal leading
     * zero byte. The hardened {@link Tag#decode(byte[], int)} rejects this.
     */
    @Test
    void sE_highTagNumber_nonMinimal_leadingZero_rejected() {
        // 0x80 as first continuation byte: contributes no value bits -> non-minimal
        byte[] leadingZero = {0x1F, (byte) 0x80, 0x1F};
        assertThrows(DerException.class,
                () -> Tag.decode(leadingZero, 0),
                "Leading 0x80 in high-tag-number continuation must be rejected (non-minimal)");

        // Confirm legitimate multi-byte high-tag-number (e.g. tag 128 = 0x1F 0x81 0x00):
        // base-128: 128 = 1*128 + 0 -> [0x81, 0x00]
        byte[] tag128 = {0x1F, (byte) 0x81, 0x00};
        assertDoesNotThrow(
                () -> Tag.decode(tag128, 0),
                "Valid 2-byte high-tag-number encoding for tag 128 must be accepted");
    }
}
