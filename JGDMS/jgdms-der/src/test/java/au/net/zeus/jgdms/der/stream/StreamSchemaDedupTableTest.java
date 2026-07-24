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

package au.net.zeus.jgdms.der.stream;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-level adversarial vectors for {@link StreamSchemaDedup}'s per-stream table:
 * the STD-006 Appendix C sec.C.8 stream-level ceiling <b>boundary pairs</b>
 * (inclusive fenceposts, metered at insertion — sec.C.8.2/G10), the fail-closed
 * resolution rejects (sec.C.6.4/C.7.4), and the chain-level ceiling composition
 * through the {@code fullChain} arm (enforced by {@link SchemaChain#decodeChain},
 * the single chain-decode path — composed here, not duplicated).
 *
 * <p>These vectors drive {@code reconstituteTopLevelAtomic} directly with synthetic
 * chains (fabricated class names, zero-field records): reconstitution verifies chains
 * and meters ceilings WITHOUT loading classes or constructing objects, so the table
 * behaviour is testable at exact byte boundaries that no real class population could
 * reach. Every reject is asserted to occur AT the violating call — i.e. during
 * decode of the violating site, not at stream end (G10) — because the reconstitution
 * of each record IS the site processing.
 *
 * <p>End-to-end (real classes, real streams) coverage is in
 * {@link DerStreamSchemaDedupTest}.
 */
class StreamSchemaDedupTableTest {

    private static final Tag FULL  = new Tag(Tag.CLASS_CONTEXT, false, 0);   // 0x80
    private static final Tag REF   = new Tag(Tag.CLASS_CONTEXT, false, 1);   // 0x81
    private static final String FORMAT = "JGDMS-STD-006/ATOMIC-DER";

    // =========================================================================
    // Synthetic chain builders
    // =========================================================================

    /** A single complete (root, no parent hash) record chain for a fabricated class. */
    private static byte[] chain(String className) {
        return new AtomicSerialSchemaRecord(className, List.of()).encode();
    }

    /**
     * A single-record chain whose encoded length is EXACTLY {@code target} bytes.
     * Bulk comes from fixed-size (200-char-name) fields; fine adjustment comes from
     * className padding (1..1024 window, far wider than the per-field quantum), with
     * a small local search absorbing DER length-octet boundary shifts. Asserts exact.
     */
    private static byte[] chainOfExactSize(String className, int target) {
        int maxPad = 1000 - className.length();
        int k = 0;
        for (int guard = 0; guard < 4096; guard++) {
            int probe = build(className, k, 0).length;
            int need = target - probe;
            if (need < 0) break;                       // overshot: unreachable window
            if (need <= maxPad + 4) {
                for (int adj = Math.max(0, need - 4); adj <= Math.min(maxPad, need + 4); adj++) {
                    byte[] out = build(className, k, adj);
                    if (out.length == target) return out;
                }
                break;                                 // window covered target but no hit
            }
            // Step toward the window: each fixed field adds ~211 encoded bytes.
            k += Math.max(1, (need - 900) / 211);
        }
        throw new AssertionError("chainOfExactSize: cannot hit " + target + " bytes");
    }

    /** One record: {@code nFields} fixed-size fields + {@code classPad} name padding. */
    private static byte[] build(String className, int nFields, int classPad) {
        List<AtomicSerialFieldDef> fields = new ArrayList<>(nFields);
        for (int i = 0; i < nFields; i++) {
            fields.add(new AtomicSerialFieldDef(
                    "f" + String.format("%03d", i) + "x".repeat(196), "int"));
        }
        return new AtomicSerialSchemaRecord(
                className + "z".repeat(classPad), fields).encode();
    }

    /** A properly-linked multi-record chain of {@code n} records (leaf-first bytes). */
    private static byte[] linkedChain(String classPrefix, int n) {
        List<AtomicSerialSchemaRecord> records = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            records.add(new AtomicSerialSchemaRecord(classPrefix + i, List.of()));
        }
        SchemaChain.Result linked = SchemaChain.linkAndGetLeafDigest(records);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (AtomicSerialSchemaRecord r : linked.chain()) {
            buf.writeBytes(r.encode());
        }
        return buf.toByteArray();
    }

    private static byte[] sha256(byte[] b) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(b);
    }

    // =========================================================================
    // Synthetic stream-form P1 records
    // =========================================================================

    /** Hierarchy payload matching a chain of {@code classCount} zero-field records. */
    private static byte[] emptyHierarchy(int classCount) {
        List<byte[]> classSeqs = new ArrayList<>(classCount);
        for (int i = 0; i < classCount; i++) {
            classSeqs.add(DerWriter.writeSequence(List.of()));
        }
        return DerWriter.writeSequence(classSeqs);
    }

    /** Stream-form P1 record with a fullChain arm over a 1-record chain. */
    private static byte[] p1Full(byte[] chainBytes) {
        return p1Full(chainBytes, 1);
    }

    private static byte[] p1Full(byte[] chainBytes, int classCount) {
        List<byte[]> children = new ArrayList<>(3);
        children.add(DerWriter.writeOctetString(emptyHierarchy(classCount)));
        children.add(DerWriter.writeTlv(FULL, chainBytes));
        children.add(DerWriter.writeUtf8String(FORMAT));
        return DerWriter.writeSequence(children);
    }

    /** Stream-form P1 record with a chainRef arm. */
    private static byte[] p1Ref(byte[] digest) {
        List<byte[]> children = new ArrayList<>(3);
        children.add(DerWriter.writeOctetString(emptyHierarchy(1)));
        children.add(DerWriter.writeTlv(REF, digest));
        children.add(DerWriter.writeUtf8String(FORMAT));
        return DerWriter.writeSequence(children);
    }

    // =========================================================================
    // maxDistinctChainsPerStream boundary pair (256 accepted / 257th rejected)
    // =========================================================================

    @Test
    void distinctChains_acceptedAtExactly256_rejectedAt257() throws Exception {
        StreamSchemaDedup d = new StreamSchemaDedup(false);
        for (int i = 0; i < 256; i++) {
            d.reconstituteTopLevelAtomic(p1Full(chain("C" + i)));
        }
        assertEquals(256, d.distinctChains(),
                "exactly maxDistinctChainsPerStream distinct chains must be accepted");
        // The 257th distinct fullChain is rejected AT its own site (this call), not
        // at stream end (G10: metered at insertion).
        DerException e = assertThrows(DerException.class,
                () -> d.reconstituteTopLevelAtomic(p1Full(chain("C256"))));
        assertTrue(e.getMessage().contains("maxDistinctChainsPerStream"),
                "reject must name the breached ceiling: " + e.getMessage());
        assertEquals(256, d.distinctChains(),
                "the violating chain must not have been inserted");
    }

    @Test
    void distinctChains_referencesDoNotCountAgainstTheCeiling() throws Exception {
        StreamSchemaDedup d = new StreamSchemaDedup(false);
        byte[] c0 = chain("C0");
        d.reconstituteTopLevelAtomic(p1Full(c0));
        byte[] digest = sha256(c0);
        for (int i = 0; i < 300; i++) {
            d.reconstituteTopLevelAtomic(p1Ref(digest));   // 300 refs, table size 1
        }
        assertEquals(1, d.distinctChains(),
                "references are table lookups, not insertions");
    }

    // =========================================================================
    // maxDedupTableBytes boundary pair (sum == 1048576 accepted / +1 rejected)
    // =========================================================================

    @Test
    void tableBytes_acceptedAtExactCeiling() throws Exception {
        // 15 chains of exactly 65536 (each also exercising accepted-at-maxChainBytes,
        // the chain-level inclusive boundary) + one of 65500 + one of 36:
        // sum == 1_048_576 exactly — every insertion accepted.
        StreamSchemaDedup d = new StreamSchemaDedup(false);
        long sum = 0;
        for (int i = 0; i < 15; i++) {
            byte[] c = chainOfExactSize("B" + i, 65536);
            sum += c.length;
            d.reconstituteTopLevelAtomic(p1Full(c));
        }
        byte[] c15 = chainOfExactSize("B15pad", 65500);
        sum += c15.length;
        d.reconstituteTopLevelAtomic(p1Full(c15));
        byte[] c16 = chainOfExactSize("B16", 36);
        sum += c16.length;
        assertEquals(1_048_576L, sum, "vector must land exactly on maxDedupTableBytes");
        d.reconstituteTopLevelAtomic(p1Full(c16));      // sum == ceiling: accepted
        assertEquals(17, d.distinctChains());
    }

    @Test
    void tableBytes_rejectedAtCeilingPlusOne() throws Exception {
        // Same shape but the final chain is one byte longer: sum == 1_048_577.
        StreamSchemaDedup d = new StreamSchemaDedup(false);
        for (int i = 0; i < 15; i++) {
            d.reconstituteTopLevelAtomic(p1Full(chainOfExactSize("B" + i, 65536)));
        }
        d.reconstituteTopLevelAtomic(p1Full(chainOfExactSize("B15pad", 65500)));
        byte[] last = chainOfExactSize("B16x", 37);
        DerException e = assertThrows(DerException.class,
                () -> d.reconstituteTopLevelAtomic(p1Full(last)));
        assertTrue(e.getMessage().contains("maxDedupTableBytes"),
                "reject must name the breached ceiling: " + e.getMessage());
        assertEquals(16, d.distinctChains(),
                "the violating chain must not have been inserted");
    }

    // =========================================================================
    // Chain-level ceilings COMPOSE through the fullChain arm (single decode path)
    // =========================================================================

    @Test
    void chainLevel_maxChainBytes_rejectedThroughFullChainArm() {
        // A single chain of 65537 bytes: SchemaChain.decodeChain's maxChainBytes
        // metering (T6) fires inside the stream fullChain arm.
        StreamSchemaDedup d = new StreamSchemaDedup(false);
        byte[] over = chainOfExactSize("Big", 65537);
        DerException e = assertThrows(DerException.class,
                () -> d.reconstituteTopLevelAtomic(p1Full(over)));
        assertTrue(e.getMessage().contains("maxChainBytes"),
                "chain-level ceiling must compose: " + e.getMessage());
    }

    @Test
    void chainLevel_maxChainRecords_boundaryThroughFullChainArm() throws Exception {
        // 64 linked records: accepted (and inserted). 65: rejected by the shared path.
        StreamSchemaDedup ok = new StreamSchemaDedup(false);
        ok.reconstituteTopLevelAtomic(p1Full(linkedChain("Ok", 64), 64));
        assertEquals(1, ok.distinctChains());

        StreamSchemaDedup bad = new StreamSchemaDedup(false);
        byte[] over = linkedChain("Bad", 65);
        DerException e = assertThrows(DerException.class,
                () -> bad.reconstituteTopLevelAtomic(p1Full(over, 65)));
        assertTrue(e.getMessage().contains("maxChainRecords"),
                "chain-level record ceiling must compose: " + e.getMessage());
    }

    @Test
    void truncatedChain_terminalWithParentHash_rejectedThroughFullChainArm() {
        // Completeness (sec.C.5.1): a terminal record CARRYING parentSchemaHash is a
        // truncated chain — the table-poisoning shape; rejected before any insertion.
        StreamSchemaDedup d = new StreamSchemaDedup(false);
        byte[] truncated = new AtomicSerialSchemaRecord(
                "Trunc", new byte[32], List.of()).encode();
        DerException e = assertThrows(DerException.class,
                () -> d.reconstituteTopLevelAtomic(p1Full(truncated)));
        assertTrue(e.getMessage().contains("truncated"),
                "completeness reject expected: " + e.getMessage());
        assertEquals(0, d.distinctChains(), "nothing may be tabled from a rejected chain");
    }

    // =========================================================================
    // Fail-closed resolution (unit forms; end-to-end twins in DerStreamSchemaDedupTest)
    // =========================================================================

    @Test
    void duplicateFullForm_rejected_unitForm() throws Exception {
        StreamSchemaDedup d = new StreamSchemaDedup(false);
        byte[] c = chain("Dup");
        d.reconstituteTopLevelAtomic(p1Full(c));
        DerException e = assertThrows(DerException.class,
                () -> d.reconstituteTopLevelAtomic(p1Full(c)));
        assertTrue(e.getMessage().contains("duplicate fullChain"),
                "mandatory-dedup enforcement: " + e.getMessage());
    }

    @Test
    void unknownDigest_rejected_and_crossInstanceIsolated() throws Exception {
        byte[] c = chain("Iso");
        byte[] digest = sha256(c);
        // Never sent in full in THIS stream -> unknown-digest reject.
        StreamSchemaDedup fresh = new StreamSchemaDedup(false);
        DerException e = assertThrows(DerException.class,
                () -> fresh.reconstituteTopLevelAtomic(p1Ref(digest)));
        assertTrue(e.getMessage().contains("unknown-digest"),
                "fail-closed resolution: " + e.getMessage());
        // Sent in full in stream A; stream B still rejects the same digest (sec.C.7.5).
        StreamSchemaDedup a = new StreamSchemaDedup(false);
        a.reconstituteTopLevelAtomic(p1Full(c));
        StreamSchemaDedup b = new StreamSchemaDedup(false);
        assertThrows(DerException.class, () -> b.reconstituteTopLevelAtomic(p1Ref(digest)),
                "a digest valid in stream A must be unknown in stream B");
    }

    @Test
    void chainRef_wrongLength_rejected() {
        StreamSchemaDedup d = new StreamSchemaDedup(false);
        for (int len : new int[] { 31, 33 }) {
            DerException e = assertThrows(DerException.class,
                    () -> d.reconstituteTopLevelAtomic(p1Ref(new byte[len])));
            assertTrue(e.getMessage().contains("32 bytes"),
                    "SIZE(32) enforcement: " + e.getMessage());
        }
    }

    @Test
    void schemaChainRef_constructedArms_and_foreignTags_rejected() {
        StreamSchemaDedup d = new StreamSchemaDedup(false);
        byte[] c = chain("Tags");
        // Constructed [0]/[1] (0xA0/0xA1) and a universal OCTET STRING (the canonical
        // record-level shape) at the SchemaChainRef position: all rejected.
        Tag constructedFull = new Tag(Tag.CLASS_CONTEXT, true, 0);
        Tag constructedRef  = new Tag(Tag.CLASS_CONTEXT, true, 1);
        for (byte[] armTlv : new byte[][] {
                DerWriter.writeTlv(constructedFull, c),
                DerWriter.writeTlv(constructedRef, new byte[32]),
                DerWriter.writeOctetString(c) }) {
            List<byte[]> children = new ArrayList<>(3);
            children.add(DerWriter.writeOctetString(emptyHierarchy(1)));
            children.add(armTlv);
            children.add(DerWriter.writeUtf8String(FORMAT));
            byte[] rec = DerWriter.writeSequence(children);
            DerException e = assertThrows(DerException.class,
                    () -> d.reconstituteTopLevelAtomic(rec));
            assertTrue(e.getMessage().contains("SchemaChainRef"),
                    "arm-tag reject: " + e.getMessage());
        }
    }

    @Test
    void emptyFullChain_rejected() {
        StreamSchemaDedup d = new StreamSchemaDedup(false);
        DerException e = assertThrows(DerException.class,
                () -> d.reconstituteTopLevelAtomic(p1Full(new byte[0])));
        assertTrue(e.getMessage().contains("empty fullChain"), e.getMessage());
    }

    @Test
    void statelessShapedChain_zeroFields_roundTripsThroughTable() throws Exception {
        // Degenerate (sec.C.11.3(11)): an empty-fields (@Stateless-shaped) chain is a
        // legitimate chain; full-then-ref works and reconstitution is byte-exact.
        StreamSchemaDedup d = new StreamSchemaDedup(false);
        byte[] c = chain("Stateless");
        byte[] rec1 = d.reconstituteTopLevelAtomic(p1Full(c));
        byte[] rec2 = d.reconstituteTopLevelAtomic(p1Ref(sha256(c)));
        assertArrayEquals(rec1, rec2,
                "full-arm and ref-arm sites must reconstitute byte-identical canonical records");
    }
}
