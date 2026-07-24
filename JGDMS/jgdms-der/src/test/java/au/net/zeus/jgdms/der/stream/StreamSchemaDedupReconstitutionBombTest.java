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
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial vectors for the reconstitution decompression-bomb defence (sec.C.8.1
 * {@code maxReconstitutedBytes}, sec.C.8.3): a {@code chainRef} is ~40 wire bytes but
 * re-materialises the full interned chain (up to {@code maxChainBytes} = 65536 B) at
 * <em>every</em> reference site, so an unmetered decoder buffers ~{@code maxChainBytes/40}
 * × the reference bytes — with only {@code maxInputBytes} of references (default 16 MiB)
 * that is ~24 GiB, structurally defeating the {@code maxInputBytes} DoS cap by expanding
 * ~1560x <em>inside</em> the decoder before any base decode or class resolution.
 *
 * <p>The table/depth/chain ceilings do NOT cover this: they bound distinct chains, stored
 * table bytes, nesting depth, and single-chain size — none of them the reference-site
 * COUNT or the total re-materialised output. The absolute per-item ceiling
 * {@code maxReconstitutedBytes = maxInputBytes × RECONSTITUTION_EXPANSION_FACTOR} is the
 * fence, metered at the single chain-materialisation chokepoint (both the {@code fullChain}
 * and {@code chainRef} arms), failing closed at the reference site before allocation (G10).
 *
 * <p><b>Bounded reproduction (G13):</b> these vectors use a deliberately SMALL
 * {@code maxInputBytes} so the ceiling is small and the reject fires after a few dozen
 * references — proving the amplification and the fence WITHOUT buffering the ~24 GiB the
 * default budget would permit before rejecting.
 */
class StreamSchemaDedupReconstitutionBombTest {

    private static final Tag FULL = new Tag(Tag.CLASS_CONTEXT, false, 0);  // 0x80
    private static final Tag REF  = new Tag(Tag.CLASS_CONTEXT, false, 1);  // 0x81

    // ---- chain builders (a single zero-hierarchy-site record, padded to a target size) ----

    private static byte[] build(String className, int nFields, int classPad) {
        List<AtomicSerialFieldDef> fields = new ArrayList<>(nFields);
        for (int i = 0; i < nFields; i++) {
            fields.add(new AtomicSerialFieldDef(
                    "f" + String.format("%03d", i) + "x".repeat(196), "int"));
        }
        return new AtomicSerialSchemaRecord(
                className + "z".repeat(classPad), fields).encode();
    }

    /** A single-record chain whose encoded length is EXACTLY {@code target} bytes. */
    private static byte[] chainOfExactSize(String className, int target) {
        int maxPad = 1000 - className.length();
        int k = 0;
        for (int guard = 0; guard < 4096; guard++) {
            int probe = build(className, k, 0).length;
            int need = target - probe;
            if (need < 0) break;
            if (need <= maxPad + 4) {
                for (int adj = Math.max(0, need - 4); adj <= Math.min(maxPad, need + 4); adj++) {
                    byte[] out = build(className, k, adj);
                    if (out.length == target) return out;
                }
                break;
            }
            k += Math.max(1, (need - 900) / 211);
        }
        throw new AssertionError("chainOfExactSize: cannot hit " + target + " bytes");
    }

    private static byte[] sha256(byte[] b) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(b);
    }

    /** Hierarchy payload matching a 1-record zero-field chain: SEQUENCE{ SEQUENCE{} }. */
    private static byte[] emptyHierarchy1() {
        return DerWriter.writeSequence(List.of(DerWriter.writeSequence(List.of())));
    }

    /** A [9] top-level array's content: UTF8(arrayWireType) ++ SEQUENCE(elements). */
    private static byte[] arrayContent(List<byte[]> elements) {
        byte[] wt = DerWriter.writeUtf8String("array:@AtomicSerial:Foo");
        byte[] seq = DerWriter.writeSequence(elements);
        byte[] out = new byte[wt.length + seq.length];
        System.arraycopy(wt, 0, out, 0, wt.length);
        System.arraycopy(seq, 0, out, wt.length, seq.length);
        return out;
    }

    /** A nested-site fullChain element: SEQUENCE{ fullChain[0], payloadBytes }. */
    private static byte[] nestedFull(byte[] chainBytes) {
        return DerWriter.writeSequence(List.of(
                DerWriter.writeTlv(FULL, chainBytes),
                DerWriter.writeOctetString(emptyHierarchy1())));
    }

    /** A nested-site chainRef element: SEQUENCE{ chainRef[1], payloadBytes }. */
    private static byte[] nestedRef(byte[] digest) {
        return DerWriter.writeSequence(List.of(
                DerWriter.writeTlv(REF, digest),
                DerWriter.writeOctetString(emptyHierarchy1())));
    }

    private static byte[] arrayOfRefs(byte[] chain, int refCount) throws Exception {
        byte[] digest = sha256(chain);
        List<byte[]> elems = new ArrayList<>(refCount + 1);
        elems.add(nestedFull(chain));                        // first occurrence seeds the table
        for (int i = 0; i < refCount; i++) elems.add(nestedRef(digest));
        return arrayContent(elems);
    }

    /** Stream-form P1 record with a fullChain arm over a 1-record chain. */
    private static byte[] p1Full(byte[] chainBytes) {
        return DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(emptyHierarchy1()),
                DerWriter.writeTlv(FULL, chainBytes),
                DerWriter.writeUtf8String("JGDMS-STD-006/ATOMIC-DER")));
    }

    // =========================================================================
    // The bomb is REJECTED at the reconstitution ceiling, no OOM (G13)
    // =========================================================================

    @Test
    void fanOutBomb_rejectedAtReconstitutionCeiling() throws Exception {
        // Small budget: maxInputBytes = 256 KiB -> maxReconstitutedBytes = 2 MiB. A near-
        // maxChainBytes chain (65500 B) is re-materialised at every ref site, so the fence
        // fires after ~32 refs, having buffered < 2 MiB — the bounded reproduction of the
        // 24 GiB default-budget bomb.
        int smallMaxInput = 256 * 1024;
        byte[] chain = chainOfExactSize("Bomb", 65500);
        byte[] input = arrayOfRefs(chain, 1000);

        StreamSchemaDedup d = new StreamSchemaDedup(false, smallMaxInput);
        DerException e = assertThrows(DerException.class,
                () -> d.reconstituteTopLevelArray(input),
                "the chainRef fan-out bomb must be rejected at the reconstitution ceiling");
        assertTrue(e.getMessage().contains("maxReconstitutedBytes"),
                "reject must name the breached ceiling: " + e.getMessage());

        // Marginal per-ref amplification, computed from byte sizes (no live unmetered
        // decode) — for the record: this is what the fence caps.
        byte[] digest = sha256(chain);
        int refWire = nestedRef(digest).length;
        int refRecon = DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(chain),
                DerWriter.writeOctetString(emptyHierarchy1()))).length;
        System.out.printf(
                "[BOMB] chainSize=%d | wire/ref=%d B recon/ref=%d B -> marginal amplification=%.1fx"
                + " | default 16 MiB pure-ref input would reconstitute ~%.1f GiB unmetered%n",
                chain.length, refWire, refRecon, (double) refRecon / refWire,
                (16.0 * 1024 * 1024 * refRecon / refWire) / (1024.0 * 1024 * 1024));
    }

    @Test
    void topLevelAtomicPath_alsoMetered_atFullChainArm() throws Exception {
        // The P1 [1] top-level record entry point (reconstituteTopLevelAtomic) reaches the
        // SAME decodeSite chokepoint via transformP1 — proving the fence is not array-
        // specific and covers the fullChain arm too. A tiny budget (maxInputBytes = 4 KiB
        // -> maxReconstitutedBytes = 32 KiB) is below one 65500 B chain, so even a single
        // fullChain re-emission is rejected at the P1 site.
        int tinyMaxInput = 4 * 1024;                // maxReconstitutedBytes = 32 KiB
        byte[] chain = chainOfExactSize("P1", 65500);
        StreamSchemaDedup d = new StreamSchemaDedup(false, tinyMaxInput);
        DerException e = assertThrows(DerException.class,
                () -> d.reconstituteTopLevelAtomic(p1Full(chain)));
        assertTrue(e.getMessage().contains("maxReconstitutedBytes"), e.getMessage());
    }

    // =========================================================================
    // The fence does NOT break legitimate dedup (absolute ceiling, not a ratio)
    // =========================================================================

    @Test
    void legitimateDedupExpansion_underCeiling_succeeds() throws Exception {
        // Dedup's whole point is reconstituted > wire. A collection of many same-typed
        // elements sharing one small chain expands far above 1x yet stays well under the
        // absolute ceiling. Real chains are < 2 KiB; use 512 B and 400 refs = ~205 KiB of
        // re-materialised chain, under the default-shaped budget with room to spare.
        int maxInput = 256 * 1024;                  // maxReconstitutedBytes = 2 MiB
        byte[] chain = chainOfExactSize("Legit", 512);
        byte[] input = arrayOfRefs(chain, 400);
        StreamSchemaDedup d = new StreamSchemaDedup(false, maxInput);
        byte[] out = d.reconstituteTopLevelArray(input);   // must NOT throw
        assertTrue(out.length > input.length * 3,
                "legitimate dedup must be allowed to expand (out=" + out.length
                + " in=" + input.length + ")");
        assertEquals(1, d.distinctChains());
    }

    @Test
    void perItemBudget_resetsBetweenTopLevelItems() throws Exception {
        // The budget is per top-level item (buffers released between items), NOT a stream
        // total — so a long stream of many separately-reconstituted items each within
        // budget is accepted. Seed one chain, then reconstitute MANY separate top-level
        // records referencing it, cumulatively far exceeding one item's budget.
        int maxInput = 256 * 1024;                  // maxReconstitutedBytes = 2 MiB
        byte[] chain = chainOfExactSize("PerItem", 65500);
        byte[] digest = sha256(chain);
        StreamSchemaDedup d = new StreamSchemaDedup(false, maxInput);
        // Seed via a full arm (one item), then 200 separate ref items: 200 × 65500 ≈ 12.5
        // MiB cumulative re-materialisation, > the 2 MiB per-item budget — yet each item is
        // one chain (< budget), so none is rejected.
        d.reconstituteTopLevelArray(arrayContent(List.of(nestedFull(chain))));
        for (int i = 0; i < 200; i++) {
            d.reconstituteTopLevelArray(arrayContent(List.of(nestedRef(digest))));
        }
        assertEquals(1, d.distinctChains(),
                "per-item reset must not reject a long legitimate stream");
    }
}
