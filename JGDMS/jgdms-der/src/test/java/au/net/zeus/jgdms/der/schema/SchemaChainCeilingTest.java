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
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceRecord;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.object.fixtures.NestedValue;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * STD-006 S4.5 chain-ceiling adoption (T6, SOW-DER-Stream-Schema-Dedup) -- covers
 * the three board-confirmed base-codec gaps:
 *
 * <ol>
 *   <li><b>Chain ceilings</b>: {@code maxChainRecords} (64) and {@code maxChainBytes}
 *       (65536), both inclusive, metered DURING the chain-decode loop
 *       ({@link SchemaChain#decodeChain}). Boundary pairs: accepted at exactly the
 *       ceiling, rejected at ceiling + 1; plus an adversarial 1000-record chain.</li>
 *   <li><b>Truncated-chain acceptance</b>: a terminal record carrying a dangling
 *       {@code parentSchemaHash} is now rejected (S7.8 chain completeness) at the
 *       top-level P1 site ({@code MarshalledInstanceRecord.decodeSchemaChain})
 *       AND the nested P2 site ({@code ObjectCodec.decodeNested}).</li>
 *   <li><b>P2 Merkle-check asymmetry</b>: {@code decodeNested} previously parsed
 *       nested chain records WITHOUT the adjacent-pair {@code parentSchemaHash}
 *       cross-check the P1 path runs; both sites now share
 *       {@link SchemaChain#decodeChain}, and the broken-pair reject at P2 is proven
 *       here (it was accepted before the fix).</li>
 * </ol>
 *
 * Plus: a legitimate maximum-real-world-magnitude chain (3 records; the repo-wide
 * deepest real hierarchy is 4) is still accepted, and a valid nested record still
 * decodes end-to-end.
 */
class SchemaChainCeilingTest {

    private static final byte[] DIGEST_32 = new byte[32]; // arbitrary 32-byte hash

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Builds {@code n} linked records (leaf-first) with tiny field lists. */
    private static SchemaChain.Result linkedChain(int n) {
        List<AtomicSerialSchemaRecord> raw = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            raw.add(new AtomicSerialSchemaRecord(
                    "com.example.Chain" + i,
                    List.of(new AtomicSerialFieldDef("f" + i, "int"))));
        }
        return SchemaChain.linkAndGetLeafDigest(raw);
    }

    /** Concatenates each record's DER encoding, leaf-first (S7.8 chain encoding). */
    private static byte[] chainBytes(List<AtomicSerialSchemaRecord> chain) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (AtomicSerialSchemaRecord rec : chain) {
            byte[] enc = rec.encode();
            buf.write(enc, 0, enc.length);
        }
        return buf.toByteArray();
    }

    /** Wraps a chain result + payload into a decodable MarshalledInstanceRecord (P1). */
    private static MarshalledInstanceRecord p1Record(SchemaChain.Result chain) {
        return new MarshalledInstanceRecord(
                new byte[]{0x30, 0x00},           // payload content is never reached
                chainBytes(chain.chain()),
                chain.leafDigest(),
                MarshalledInstanceRecord.PAYLOAD_FORMAT);
    }

    /** Wraps raw chain bytes as a nested (P2) record TLV: SEQUENCE{OCTET STRING, OCTET STRING}. */
    private static byte[] p2NestedTlv(byte[] rawChainBytes) {
        return DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(rawChainBytes),
                DerWriter.writeOctetString(new byte[]{0x30, 0x00})));
    }

    /**
     * Builds a single-record, complete (no parent hash) chain whose encoded length is
     * EXACTLY {@code targetBytes}: a fixed population of 4-byte-named fields for coarse
     * size, then one final field whose wireName length is iteratively adjusted until the
     * encoding hits the target (converges in a few iterations; DER length-of-length
     * jumps are absorbed by re-measuring each round).
     */
    private static byte[] chainOfExactSize(int targetBytes) {
        int s = fieldDefSize();
        int fineLen = 100;
        int coarse = Math.max(0, (targetBytes - paddedRecord(0, fineLen).encode().length) / s);
        for (int attempt = 0; attempt < 100; attempt++) {
            byte[] enc = paddedRecord(coarse, fineLen).encode();
            int delta = targetBytes - enc.length;
            if (delta == 0) {
                return enc;
            }
            if (Math.abs(delta) > 200) {
                // coarse knob: whole field defs at a time
                coarse = Math.max(0, coarse + delta / s);
                continue;
            }
            int newFine = fineLen + delta;
            if (newFine < 1 || newFine > 250) {
                // shift the coarse knob one step and re-centre the fine knob
                coarse = Math.max(0, coarse + ((newFine < 1) ? -1 : 1));
                fineLen = 100;
            } else {
                fineLen = newFine;
            }
        }
        throw new AssertionError("could not build a chain of exactly " + targetBytes + " bytes");
    }

    private static int fieldDefSize() {
        return new AtomicSerialFieldDef("aaaa", "int").encode().length;
    }

    private static AtomicSerialSchemaRecord paddedRecord(int coarseFields, int fineNameLen) {
        List<AtomicSerialFieldDef> fields = new ArrayList<>(coarseFields + 1);
        for (int i = 0; i < coarseFields; i++) {
            fields.add(new AtomicSerialFieldDef("aaaa", "int"));
        }
        fields.add(new AtomicSerialFieldDef("p".repeat(fineNameLen), "int"));
        return new AtomicSerialSchemaRecord("com.example.Padded", fields);
    }

    // =========================================================================
    // Gap 1 -- maxChainRecords boundary pair (inclusive fencepost)
    // =========================================================================

    @Test
    void chainRecords_atCeiling_accepted() throws Exception {
        SchemaChain.Result chain = linkedChain(SchemaChain.MAX_CHAIN_RECORDS);
        // Shared decoder
        List<AtomicSerialSchemaRecord> decoded =
                SchemaChain.decodeChain(chainBytes(chain.chain()), "test");
        assertEquals(SchemaChain.MAX_CHAIN_RECORDS, decoded.size(),
                "a chain of exactly maxChainRecords records must be accepted (inclusive)");
        // P1 site
        assertEquals(SchemaChain.MAX_CHAIN_RECORDS,
                p1Record(chain).decodeSchemaChain().size(),
                "P1 decodeSchemaChain must accept a chain at the record ceiling");
    }

    @Test
    void chainRecords_ceilingPlusOne_rejected() {
        SchemaChain.Result chain = linkedChain(SchemaChain.MAX_CHAIN_RECORDS + 1);
        DerException e = assertThrows(DerException.class,
                () -> p1Record(chain).decodeSchemaChain(),
                "a chain of maxChainRecords + 1 records must be rejected");
        assertTrue(e.getMessage().contains("maxChainRecords"),
                "reject must cite maxChainRecords; got: " + e.getMessage());
    }

    // =========================================================================
    // Gap 1 -- maxChainBytes boundary pair (inclusive fencepost)
    // =========================================================================

    @Test
    void chainBytes_atCeiling_accepted() throws Exception {
        byte[] exact = chainOfExactSize(SchemaChain.MAX_CHAIN_BYTES);
        assertEquals(SchemaChain.MAX_CHAIN_BYTES, exact.length, "builder must hit the target size");
        List<AtomicSerialSchemaRecord> decoded = SchemaChain.decodeChain(exact, "test");
        assertEquals(1, decoded.size(),
                "a chain of exactly maxChainBytes bytes must be accepted (inclusive)");
    }

    @Test
    void chainBytes_ceilingPlusOne_rejected() {
        byte[] over = chainOfExactSize(SchemaChain.MAX_CHAIN_BYTES + 1);
        assertEquals(SchemaChain.MAX_CHAIN_BYTES + 1, over.length, "builder must hit the target size");
        DerException e = assertThrows(DerException.class,
                () -> SchemaChain.decodeChain(over, "test"),
                "a chain of maxChainBytes + 1 bytes must be rejected");
        assertTrue(e.getMessage().contains("maxChainBytes"),
                "reject must cite maxChainBytes; got: " + e.getMessage());
    }

    // =========================================================================
    // Gap 1 -- adversarial over-ceiling chain against the built decoder (G13)
    // =========================================================================

    @Test
    void adversarial_1000RecordChain_rejected_atBothSites() {
        SchemaChain.Result chain = linkedChain(1000);
        byte[] bytes = chainBytes(chain.chain());

        // P1: top-level MarshalledInstanceRecord
        DerException p1 = assertThrows(DerException.class,
                () -> p1Record(chain).decodeSchemaChain(),
                "P1 must reject a 1000-record chain");
        assertTrue(p1.getMessage().contains("maxChainRecords"),
                "P1 reject must cite maxChainRecords; got: " + p1.getMessage());

        // P2: nested decode site
        DerException p2 = assertThrows(DerException.class,
                () -> ObjectCodec.decodeNested(p2NestedTlv(bytes), 0),
                "P2 must reject a 1000-record chain");
        assertTrue(p2.getMessage().contains("maxChainRecords"),
                "P2 reject must cite maxChainRecords; got: " + p2.getMessage());
    }

    // =========================================================================
    // Gap 2 -- truncated chain (dangling terminal parentSchemaHash) rejected, P1
    // =========================================================================

    @Test
    void truncatedChain_p1_rejected() {
        // A full {leaf, root} chain truncated to {leaf}: the leaf keeps its
        // parentSchemaHash but the parent record is gone. Pre-fix this was ACCEPTED
        // (the adjacent-pair loop never inspected the terminal record), so
        // {leaf} and {leaf, root} both produced the same leaf digest from
        // different bytes -- breaking digest -> bytes injectivity.
        SchemaChain.Result full = linkedChain(2);
        AtomicSerialSchemaRecord danglingLeaf = full.chain().get(0);
        assertNotNull(danglingLeaf.parentSchemaHashOrNull(), "precondition: leaf is linked");

        byte[] truncated = danglingLeaf.encode();
        MarshalledInstanceRecord rec = new MarshalledInstanceRecord(
                new byte[]{0x30, 0x00}, truncated, full.leafDigest(),
                MarshalledInstanceRecord.PAYLOAD_FORMAT);

        DerException e = assertThrows(DerException.class, rec::decodeSchemaChain,
                "P1 must reject a truncated chain (terminal record with parentSchemaHash)");
        assertTrue(e.getMessage().contains("truncated"),
                "reject must name the truncation; got: " + e.getMessage());

        // The full chain remains accepted -- the check bites only on truncation.
        assertDoesNotThrow(() -> p1Record(full).decodeSchemaChain(),
                "the complete {leaf, root} chain must still be accepted");
    }

    @Test
    void truncatedChain_singleDanglingRecord_p1_rejected() {
        AtomicSerialSchemaRecord dangling = new AtomicSerialSchemaRecord(
                "com.example.Dangling", DIGEST_32,
                List.of(new AtomicSerialFieldDef("x", "int")));
        DerException e = assertThrows(DerException.class,
                () -> SchemaChain.decodeChain(dangling.encode(), "test"),
                "a single record carrying a parentSchemaHash is a truncated chain");
        assertTrue(e.getMessage().contains("truncated"),
                "reject must name the truncation; got: " + e.getMessage());
    }

    // =========================================================================
    // Gap 2 -- truncated chain rejected at the nested P2 site
    // =========================================================================

    @Test
    void truncatedChain_p2_rejected() {
        SchemaChain.Result full = linkedChain(2);
        byte[] truncated = full.chain().get(0).encode(); // leaf only, dangling hash

        DerException e = assertThrows(DerException.class,
                () -> ObjectCodec.decodeNested(p2NestedTlv(truncated), 0),
                "P2 decodeNested must reject a truncated chain");
        assertTrue(e.getMessage().contains("truncated"),
                "reject must name the truncation; got: " + e.getMessage());
    }

    // =========================================================================
    // Gap 3 -- broken adjacent pair rejected at the nested P2 site
    // (previously ACCEPTED: decodeNested parsed the chain without the cross-check)
    // =========================================================================

    @Test
    void brokenAdjacentPair_p2_rejected() {
        // leaf claims a parent hash that does NOT match the terminal record's digest
        AtomicSerialSchemaRecord terminal = new AtomicSerialSchemaRecord(
                "com.example.Root", List.of(new AtomicSerialFieldDef("r", "int")));
        AtomicSerialSchemaRecord lyingLeaf = new AtomicSerialSchemaRecord(
                "com.example.Leaf", DIGEST_32,
                List.of(new AtomicSerialFieldDef("l", "int")));
        assertFalse(java.util.Arrays.equals(DIGEST_32, terminal.schemaDigest()),
                "precondition: the claimed parent hash must not match the real digest");

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.writeBytes(lyingLeaf.encode());
        buf.writeBytes(terminal.encode());

        DerException e = assertThrows(DerException.class,
                () -> ObjectCodec.decodeNested(p2NestedTlv(buf.toByteArray()), 0),
                "P2 decodeNested must reject a chain whose adjacent-pair cross-check fails"
                + " (this was accepted before the fix -- the P1/P2 asymmetry)");
        assertTrue(e.getMessage().contains("cross-check failed"),
                "reject must name the cross-check; got: " + e.getMessage());
    }

    @Test
    void brokenMidChain_missingParentHash_p2_rejected() {
        // A non-terminal record with NO parentSchemaHash: broken chain at P2.
        AtomicSerialSchemaRecord bareLeaf = new AtomicSerialSchemaRecord(
                "com.example.BareLeaf", List.of(new AtomicSerialFieldDef("l", "int")));
        AtomicSerialSchemaRecord terminal = new AtomicSerialSchemaRecord(
                "com.example.Root", List.of(new AtomicSerialFieldDef("r", "int")));

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.writeBytes(bareLeaf.encode());
        buf.writeBytes(terminal.encode());

        DerException e = assertThrows(DerException.class,
                () -> ObjectCodec.decodeNested(p2NestedTlv(buf.toByteArray()), 0),
                "P2 must reject a non-terminal record with no parentSchemaHash");
        assertTrue(e.getMessage().contains("chain broken"),
                "reject must name the broken chain; got: " + e.getMessage());
    }

    // =========================================================================
    // Positive controls -- legitimate producers stay accepted
    // =========================================================================

    @Test
    void nested_p2_validRecord_stillAccepted() throws Exception {
        // End-to-end: a real nested record (chain produced by SchemaGenerator, which
        // always emits a complete chain -- linkAndGetLeafDigest clears the root's
        // parentSchemaHash) still decodes through the hardened P2 path.
        NestedValue value = new NestedValue(7, "ceiling-positive-control");
        SchemaChain.Result chain = SchemaGenerator.generateChain(NestedValue.class);
        byte[] payload = ObjectCodec.encodeHierarchy(value, chain);
        byte[] tlv = DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(chainBytes(chain.chain())),
                DerWriter.writeOctetString(payload)));

        Object decoded = ObjectCodec.decodeNested(tlv, 0);
        assertEquals(value, decoded, "a legitimate nested record must still decode");
    }

    @Test
    void legit_maxRealWorldChain_accepted() throws Exception {
        // The deepest chain SchemaGenerator can produce from this module's classpath:
        // Gamma -> Beta -> Alpha (3 records). The repo-wide deepest real hierarchy is
        // 4 records (ConstrainableRegistrarEvent -> RegistrarEvent -> ServiceEvent ->
        // RemoteEvent in reggie-dl) -- same order of magnitude, 16x below the 64-record
        // ceiling. (The platform's own deepest exception chain,
        // UnknownLeaseException -> LeaseException -> AtomicException, is not currently
        // generatable: AtomicException.serialForm() declares a raw Throwable field that
        // SchemaGenerator.toWireType rejects -- a pre-existing generator limitation
        // unrelated to the chain ceilings.)
        SchemaChain.Result chain = SchemaGenerator.generateChain(
                au.net.zeus.jgdms.der.object.fixtures.Gamma.class);
        byte[] bytes = chainBytes(chain.chain());

        List<AtomicSerialSchemaRecord> decoded =
                SchemaChain.decodeChain(bytes, "test");
        assertEquals(chain.chain().size(), decoded.size());
        assertNull(decoded.get(decoded.size() - 1).parentSchemaHashOrNull(),
                "a generated chain's terminal record must carry no parentSchemaHash");

        // Evidence for the [PROPOSED] ceiling values: real maxima vs the ceilings.
        System.out.println("[T6 evidence] deepest generatable chain (real-world magnitude): "
                + decoded.size() + " records, " + bytes.length + " bytes"
                + " (ceilings: " + SchemaChain.MAX_CHAIN_RECORDS + " records, "
                + SchemaChain.MAX_CHAIN_BYTES + " bytes)");
        assertTrue(decoded.size() <= SchemaChain.MAX_CHAIN_RECORDS);
        assertTrue(bytes.length <= SchemaChain.MAX_CHAIN_BYTES);

        // And the full P1 path accepts it.
        assertDoesNotThrow(() -> p1Record(chain).decodeSchemaChain());
    }
}
