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
 * Adversarial + measurement vectors for the <em>cumulative</em> reconstitution-work bound
 * (sec.C.8.1 {@code maxStreamReconstitutedBytes}, sec.C.8.3) — the residual the per-item
 * ceiling ({@code maxReconstitutedBytes}) leaves open.
 *
 * <p>The per-item ceiling bounds PEAK MEMORY (one top-level item's reconstituted buffer,
 * released between {@code readObject} calls). It does NOT bound cumulative CPU/GC WORK: an
 * attacker keeps every item just under the per-item cap but sends many items, driving
 * ~24 GiB of array-copy/GC churn across one ≤{@code maxInputBytes} window — memory-safe,
 * ~1560x work-amplified. The cumulative ceiling
 * {@code maxStreamReconstitutedBytes = maxInputBytes × STREAM_RECONSTITUTION_EXPANSION_FACTOR}
 * fences that WORK, accumulated across ALL items at the same {@code decodeSite} chokepoint,
 * reset only at window open (per-instance) — which on the DER path is once per stream (the
 * whole eagerly-buffered ≤{@code maxInputBytes} input is one window, one codec, one dedup
 * instance).
 *
 * <p><b>Composition (no OOM reintroduced).</b> Peak memory stays bounded by the SEPARATE
 * per-item counter (reset each item) and buffer release between items; the cumulative
 * counter never gates buffer release, so it can only ADD a fail-closed rejection, never
 * relax the memory bound. {@link #legitimateLongStream_acceptedAndMemoryBounded()} proves
 * this: a long stream whose cumulative work climbs far past the per-item cap stays
 * memory-bounded (per-item counter resets, item outputs bounded) across every item.
 */
class StreamSchemaDedupCumulativeWorkBoundTest {

    private static final Tag FULL = new Tag(Tag.CLASS_CONTEXT, false, 0);  // 0x80
    private static final Tag REF  = new Tag(Tag.CLASS_CONTEXT, false, 1);  // 0x81

    // ---- builders (mirroring StreamSchemaDedupReconstitutionBombTest) ----

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

    private static byte[] arrayContent(List<byte[]> elements) {
        byte[] wt = DerWriter.writeUtf8String("array:@AtomicSerial:Foo");
        byte[] seq = DerWriter.writeSequence(elements);
        byte[] out = new byte[wt.length + seq.length];
        System.arraycopy(wt, 0, out, 0, wt.length);
        System.arraycopy(seq, 0, out, wt.length, seq.length);
        return out;
    }

    private static byte[] nestedFull(byte[] chainBytes) {
        return DerWriter.writeSequence(List.of(
                DerWriter.writeTlv(FULL, chainBytes),
                DerWriter.writeOctetString(emptyHierarchy1())));
    }

    private static byte[] nestedRef(byte[] digest) {
        return DerWriter.writeSequence(List.of(
                DerWriter.writeTlv(REF, digest),
                DerWriter.writeOctetString(emptyHierarchy1())));
    }

    // =========================================================================
    // MEASUREMENT: the legitimate cumulative expansion of a dense same-schema stream
    // =========================================================================

    /**
     * Measures the cumulative reconstituted-chain bytes and the reconstituted/wire ratio of
     * a legitimate dedup-heavy stream — many genuine same-schema objects sharing one chain,
     * the dedup normal case — for a bracket of realistic chain sizes. The ratio is
     * scale-invariant ({@code chainSize / refUnitWire}), so it projects the cumulative for a
     * full {@code maxInputBytes} window. This is the number the cumulative cap must clear
     * with headroom. Reports; asserts each projected legitimate figure stays under the
     * default cap ({@code maxInputBytes × 64} = 1 GiB).
     */
    @Test
    void measure_legitimateCumulativeExpansion() throws Exception {
        final int maxInput = 16 * 1024 * 1024;              // default posture
        final long cap = (long) maxInput * StreamSchemaDedup.STREAM_RECONSTITUTION_EXPANSION_FACTOR;
        // Representative sample: ~40k references per chain size (dense pure-ref packing —
        // minimal payloads maximise ref density and therefore the cumulative/wire ratio, so
        // this is the WORST legitimate case, not a soft one).
        final int refsPerItem = 400;                        // per item well under per-item cap
        final int items = 100;                              // 40k refs total
        System.out.println("[MEASURE] legitimate cumulative reconstitution (dense same-schema,"
                + " minimal payloads = worst-legit ratio); default cap = " + cap + " B (1 GiB)");
        for (int chainSize : new int[] { 256, 512, 1024, 2048 }) {
            byte[] chain = chainOfExactSize("Legit" + chainSize, chainSize);
            byte[] digest = sha256(chain);
            byte[] refElem = nestedRef(digest);
            int refUnitWire = refElem.length;

            StreamSchemaDedup d = new StreamSchemaDedup(false, maxInput);
            long wire = 0;
            // Seed the chain (first occurrence = one fullChain item).
            byte[] seedItem = arrayContent(List.of(nestedFull(chain)));
            wire += seedItem.length;
            d.reconstituteTopLevelArray(seedItem);
            // Many subsequent ref-only items (the dedup saving in action).
            List<byte[]> refs = new ArrayList<>(refsPerItem);
            for (int i = 0; i < refsPerItem; i++) refs.add(refElem);
            byte[] refItem = arrayContent(refs);
            for (int i = 0; i < items; i++) {
                wire += refItem.length;
                d.reconstituteTopLevelArray(refItem);
            }
            long cumulative = d.streamReconstitutedChainBytes();
            double ratio = (double) cumulative / wire;
            long projectedAtMaxInput = (long) (ratio * maxInput);
            System.out.printf(
                    "  chain=%5d B refUnitWire=%d B | measured cumulative=%,d B over wire=%,d B"
                    + " -> ratio=%.1fx | projected cumulative at %d MiB window=%,d B (%.1f%% of cap)%n",
                    chainSize, refUnitWire, cumulative, wire, ratio,
                    maxInput / (1024 * 1024), projectedAtMaxInput,
                    100.0 * projectedAtMaxInput / cap);
            assertTrue(projectedAtMaxInput < cap,
                    "legitimate projected cumulative (" + projectedAtMaxInput
                    + ") must stay under the cap (" + cap + ") for chain " + chainSize);
        }
    }

    // =========================================================================
    // MANDATORY (a): the many-items fan-out WORK bomb is REJECTED at the cumulative cap
    // =========================================================================

    /**
     * The residual bomb: many top-level items, EACH a single {@code chainRef} (far under the
     * per-item cap), cumulatively summing past the cumulative cap. The per-item ceiling never
     * fires (each item re-materialises one chain); only the cumulative ceiling catches it.
     * Bounded reproduction: a small {@code maxInputBytes} so the reject fires after a few
     * dozen items having buffered only one chain at a time (no OOM, no 24 GiB burst).
     */
    @Test
    void manyItemsFanOutBomb_rejectedAtCumulativeCeiling() throws Exception {
        // maxInputBytes = 256 KiB -> per-item cap = 2 MiB, cumulative cap = 16 MiB. A ~65500 B
        // chain per item is far under the 2 MiB per-item cap, so per-item NEVER fires; the
        // cumulative cap fires after ~256 items (256 × 65500 ≈ 16 MiB).
        final int smallMaxInput = 256 * 1024;
        byte[] chain = chainOfExactSize("WorkBomb", 65500);
        byte[] digest = sha256(chain);
        StreamSchemaDedup d = new StreamSchemaDedup(false, smallMaxInput);
        // Seed the chain (one full item, under per-item cap).
        d.reconstituteTopLevelArray(arrayContent(List.of(nestedFull(chain))));

        byte[] oneRefItem = arrayContent(List.of(nestedRef(digest)));
        DerException ex = null;
        int accepted = 0;
        for (int i = 0; i < 100_000 && ex == null; i++) {
            try {
                d.reconstituteTopLevelArray(oneRefItem);   // one chain re-materialised
                accepted++;
                // Per-item peak-memory counter must reset every item and stay tiny — proving
                // this is a WORK bomb (cumulative), not a memory bomb (per-item).
                assertTrue(d.reconstitutedChainBytes() <= chain.length + 16,
                        "per-item counter must reset each item (memory bounded)");
            } catch (DerException e) {
                ex = e;
            }
        }
        assertNotNull(ex, "the many-items fan-out WORK bomb must be rejected at the cumulative ceiling");
        assertTrue(ex.getMessage().contains("maxStreamReconstitutedBytes"),
                "reject must name the cumulative ceiling: " + ex.getMessage());
        // The reject arrives only after MANY items were individually accepted (each under the
        // per-item cap) — i.e. the per-item ceiling did NOT catch this; the cumulative one did.
        assertTrue(accepted > 100,
                "many items must be individually accepted before the cumulative cap fires (got "
                + accepted + ") — proving per-item did not catch the work bomb");
        assertTrue(d.streamReconstitutedChainBytes() <= d.maxStreamReconstitutedChainBytes(),
                "cumulative counter never advances past the cap (fail-closed before allocation)");
        System.out.printf("[WORK-BOMB] rejected after %d items accepted; cumulative counter=%,d B"
                + " (cap=%,d B), per-item counter stayed <= one chain the whole time%n",
                accepted, d.streamReconstitutedChainBytes(), d.maxStreamReconstitutedChainBytes());
    }

    // =========================================================================
    // MANDATORY (b): a legitimate long stream is ACCEPTED, round-trips, stays memory-bounded
    // =========================================================================

    /**
     * A legitimate long stream: many genuine same-schema items whose cumulative reconstitution
     * WORK climbs far past the per-item peak-memory cap, yet stays under the cumulative cap —
     * all accepted, and provably memory-bounded (Peter's OOM gate): after every item the
     * per-item counter has reset and the item's reconstituted output is bounded, while the
     * cumulative counter grows monotonically. Proves the cumulative bound does not penalise
     * legitimate long streams AND that resetting the per-item counter across many items does
     * not let memory accumulate.
     */
    @Test
    void legitimateLongStream_acceptedAndMemoryBounded() throws Exception {
        // maxInputBytes = 256 KiB -> per-item cap = 2 MiB, cumulative cap = 16 MiB. Use a
        // ~1 KiB chain and modest per-item fan-out; run enough items that cumulative work
        // greatly exceeds the 2 MiB per-item cap (crossing what a per-window-reset model
        // would call "many windows") while staying under the 16 MiB cumulative cap.
        final int maxInput = 256 * 1024;
        final long perItemCap = maxInput * StreamSchemaDedup.RECONSTITUTION_EXPANSION_FACTOR; // 2 MiB
        byte[] chain = chainOfExactSize("LongOK", 1024);
        byte[] digest = sha256(chain);
        StreamSchemaDedup d = new StreamSchemaDedup(false, maxInput);
        d.reconstituteTopLevelArray(arrayContent(List.of(nestedFull(chain))));

        // 8 refs/item × 1024 B ≈ 8 KiB reconstituted per item (far under 2 MiB per-item cap).
        List<byte[]> refs = new ArrayList<>();
        for (int i = 0; i < 8; i++) refs.add(nestedRef(digest));
        byte[] item = arrayContent(refs);

        long prevCumulative = d.streamReconstitutedChainBytes();
        int itemsRun = 0;
        // 1500 items × 8 refs × 1024 B ≈ 12.3 MiB cumulative — > 6x the per-item cap, under the cap.
        for (int i = 0; i < 1500; i++) {
            byte[] out = d.reconstituteTopLevelArray(item);    // must NOT throw
            itemsRun++;
            // (1) Memory bound: the per-item counter reset and stayed under the per-item cap.
            assertTrue(d.reconstitutedChainBytes() <= perItemCap,
                    "per-item peak-memory counter must stay <= per-item cap every item");
            assertTrue(d.reconstitutedChainBytes() <= 8L * 1024 + 64,
                    "per-item counter reflects only THIS item's chains (memory bounded)");
            // (2) The item's reconstituted OUTPUT (the actual peak buffer) is bounded and small.
            assertTrue(out.length < 64 * 1024,
                    "reconstituted item output stays small (peak memory bounded): " + out.length);
            // (3) Work bound: the cumulative counter grows monotonically across items.
            long now = d.streamReconstitutedChainBytes();
            assertTrue(now > prevCumulative, "cumulative work counter must accumulate across items");
            prevCumulative = now;
        }
        long cumulative = d.streamReconstitutedChainBytes();
        assertEquals(1, d.distinctChains(), "one shared schema across the whole legitimate stream");
        assertTrue(cumulative > perItemCap * 5,
                "cumulative work must exceed the per-item cap many times over (got " + cumulative
                + " vs per-item cap " + perItemCap + ") — a genuine long stream");
        assertTrue(cumulative < d.maxStreamReconstitutedChainBytes(),
                "...yet stay under the cumulative cap (accepted, not rejected)");
        System.out.printf("[LONG-OK] %d items accepted; cumulative work=%,d B (per-item cap=%,d B,"
                + " cumulative cap=%,d B); peak per-item stayed <= 8 KiB throughout%n",
                itemsRun, cumulative, perItemCap, d.maxStreamReconstitutedChainBytes());
    }

    // =========================================================================
    // Composition: per-item fires first on a single-item bomb (no gap, no double-count)
    // =========================================================================

    /**
     * On a single-item fan-out bomb the per-item ceiling (the tighter of the two, factor
     * 8 ≤ 64) fires first, naming {@code maxReconstitutedBytes} — confirming the two bounds
     * compose with no gap (a single item cannot breach the cumulative bound before the
     * per-item one) and the cumulative counter is not double-counted into the per-item reject.
     */
    @Test
    void singleItemBomb_perItemFiresFirst_boundsCompose() throws Exception {
        final int smallMaxInput = 256 * 1024;               // per-item 2 MiB, cumulative 16 MiB
        byte[] chain = chainOfExactSize("Compose", 65500);
        byte[] digest = sha256(chain);
        List<byte[]> elems = new ArrayList<>();
        elems.add(nestedFull(chain));
        for (int i = 0; i < 1000; i++) elems.add(nestedRef(digest));  // one huge item
        byte[] oneBigItem = arrayContent(elems);

        StreamSchemaDedup d = new StreamSchemaDedup(false, smallMaxInput);
        DerException e = assertThrows(DerException.class,
                () -> d.reconstituteTopLevelArray(oneBigItem));
        assertTrue(e.getMessage().contains("maxReconstitutedBytes")
                        && !e.getMessage().contains("maxStreamReconstitutedBytes"),
                "single-item bomb must be caught by the per-item ceiling first: " + e.getMessage());
    }
}
