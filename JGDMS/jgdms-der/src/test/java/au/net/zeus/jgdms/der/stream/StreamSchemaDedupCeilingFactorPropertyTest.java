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
 * The two reconstitution-ceiling expansion factors are deployment-tunable via system
 * property (sec.C.8.1, RATIFIED — Peter 2026-07-24), with hard fail-safe/fail-closed
 * validation so a misconfigured or hostile property can never disable or gap the DoS
 * ceilings.
 *
 * <p>Covers, per the security review: (1) a property override moves the effective ceiling
 * and the decode accept/reject boundary in both directions; (2) invalid values
 * (non-numeric, 0, negative, over-max) fall back to the ratified default — never unbounded;
 * (3) the {@code streamFactor >= itemFactor} composition invariant is enforced fail-closed;
 * (4) defaults are unchanged (8/64) when no property is set; (5) the {@code
 * maxInputBytes × factor} derivation is overflow-checked.
 */
class StreamSchemaDedupCeilingFactorPropertyTest {

    private static final Tag FULL = new Tag(Tag.CLASS_CONTEXT, false, 0);  // 0x80
    private static final Tag REF  = new Tag(Tag.CLASS_CONTEXT, false, 1);  // 0x81

    // =========================================================================
    // (4) Defaults unchanged when no property is set
    // =========================================================================

    @Test
    void ratifiedDefaults_whenNoPropertySet() {
        // The test JVM sets neither property, so the class-init read must yield 8/64.
        assertEquals(8, StreamSchemaDedup.RECONSTITUTION_EXPANSION_FACTOR);
        assertEquals(64, StreamSchemaDedup.STREAM_RECONSTITUTION_EXPANSION_FACTOR);
        assertEquals(8, StreamSchemaDedup.DEFAULT_RECONSTITUTION_FACTOR);
        assertEquals(64, StreamSchemaDedup.DEFAULT_STREAM_RECONSTITUTION_FACTOR);
        // Composition invariant holds for the defaults.
        assertDoesNotThrow(() -> StreamSchemaDedup.checkCompositionInvariant(
                StreamSchemaDedup.RECONSTITUTION_EXPANSION_FACTOR,
                StreamSchemaDedup.STREAM_RECONSTITUTION_EXPANSION_FACTOR));
    }

    // =========================================================================
    // (2) parseFactor — pure validation of a raw property string
    // =========================================================================

    @Test
    void parseFactor_validValues_accepted() {
        assertEquals(1, StreamSchemaDedup.parseFactor("1", 8, "p"));       // range floor
        assertEquals(16, StreamSchemaDedup.parseFactor("16", 8, "p"));
        assertEquals(64, StreamSchemaDedup.parseFactor("  64  ", 8, "p")); // trimmed
        assertEquals(StreamSchemaDedup.MAX_EXPANSION_FACTOR,
                StreamSchemaDedup.parseFactor(
                        Integer.toString(StreamSchemaDedup.MAX_EXPANSION_FACTOR), 8, "p")); // ceiling
    }

    @Test
    void parseFactor_invalidValues_fallBackToDefault_neverUnbounded() {
        assertEquals(8, StreamSchemaDedup.parseFactor(null, 8, "p"));         // unset
        assertEquals(8, StreamSchemaDedup.parseFactor("abc", 8, "p"));        // non-numeric
        assertEquals(8, StreamSchemaDedup.parseFactor("", 8, "p"));           // empty
        assertEquals(8, StreamSchemaDedup.parseFactor("0", 8, "p"));          // zero
        assertEquals(8, StreamSchemaDedup.parseFactor("-3", 8, "p"));         // negative
        assertEquals(8, StreamSchemaDedup.parseFactor(
                Integer.toString(StreamSchemaDedup.MAX_EXPANSION_FACTOR + 1), 8, "p")); // over-max
        assertEquals(8, StreamSchemaDedup.parseFactor(
                Integer.toString(Integer.MAX_VALUE), 8, "p"));               // fat-finger MAX
        // The recovery value is always the ratified default (never 0 / negative / huge).
        assertEquals(64, StreamSchemaDedup.parseFactor("nope", 64, "p"));
    }

    // =========================================================================
    // (1a) resolveFactor — the real System.getProperty read + validate wiring
    // =========================================================================

    @Test
    void resolveFactor_readsRealSystemProperty() {
        String prop = StreamSchemaDedup.PROP_RECONSTITUTION_FACTOR;
        String saved = System.getProperty(prop);
        try {
            System.setProperty(prop, "16");
            assertEquals(16, StreamSchemaDedup.resolveFactor(prop, 8));
            System.setProperty(prop, "0");                       // invalid -> default
            assertEquals(8, StreamSchemaDedup.resolveFactor(prop, 8));
            System.setProperty(prop, "999999");                  // over-max -> default
            assertEquals(8, StreamSchemaDedup.resolveFactor(prop, 8));
            System.clearProperty(prop);                          // unset -> default
            assertEquals(8, StreamSchemaDedup.resolveFactor(prop, 8));
        } finally {
            if (saved == null) System.clearProperty(prop); else System.setProperty(prop, saved);
        }
    }

    // =========================================================================
    // (3) Composition invariant streamFactor >= itemFactor, fail-closed
    // =========================================================================

    @Test
    void compositionInvariant_enforced() {
        assertDoesNotThrow(() -> StreamSchemaDedup.checkCompositionInvariant(8, 64)); // default
        assertDoesNotThrow(() -> StreamSchemaDedup.checkCompositionInvariant(8, 8));  // equal ok
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> StreamSchemaDedup.checkCompositionInvariant(64, 8));            // inverted
        assertTrue(e.getMessage().contains("composition invariant"), e.getMessage());
        assertTrue(e.getMessage().contains("streamReconstitutionFactor"), e.getMessage());
    }

    @Test
    void constructor_rejectsInvertedFactors() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new StreamSchemaDedup(false, 16 * 1024, 8, 4)); // per-window < per-item
        assertTrue(e.getMessage().contains("composition invariant"), e.getMessage());
    }

    @Test
    void constructor_rejectsOutOfRangeFactors() {
        assertThrows(IllegalArgumentException.class,
                () -> new StreamSchemaDedup(false, 16 * 1024, 0, 64));   // zero
        assertThrows(IllegalArgumentException.class,
                () -> new StreamSchemaDedup(false, 16 * 1024, -1, 64));  // negative
        assertThrows(IllegalArgumentException.class,
                () -> new StreamSchemaDedup(false, 16 * 1024,
                        StreamSchemaDedup.MAX_EXPANSION_FACTOR + 1,
                        StreamSchemaDedup.MAX_EXPANSION_FACTOR + 1));    // over-max
    }

    // =========================================================================
    // (5) The maxInputBytes × factor derivation is overflow-checked
    // =========================================================================

    @Test
    void ceilingDerivation_isOverflowSafeAcrossTheWholeValidRange() {
        // The extreme corner: largest maxInputBytes × largest permitted factor. The product
        // is a large POSITIVE long, never an overflowed small/negative ceiling.
        StreamSchemaDedup d = new StreamSchemaDedup(false, Integer.MAX_VALUE,
                StreamSchemaDedup.MAX_EXPANSION_FACTOR, StreamSchemaDedup.MAX_EXPANSION_FACTOR);
        long expected = (long) Integer.MAX_VALUE * StreamSchemaDedup.MAX_EXPANSION_FACTOR;
        assertEquals(expected, d.maxReconstitutedChainBytes());
        assertEquals(expected, d.maxStreamReconstitutedChainBytes());
        assertTrue(d.maxReconstitutedChainBytes() > 0, "ceiling must not overflow to <= 0");
    }

    @Test
    void effectiveCeiling_isMaxInputBytesTimesFactor() {
        int maxInput = 16 * 1024;
        StreamSchemaDedup d = new StreamSchemaDedup(false, maxInput, 3, 9);
        assertEquals((long) maxInput * 3, d.maxReconstitutedChainBytes());
        assertEquals((long) maxInput * 9, d.maxStreamReconstitutedChainBytes());
    }

    // =========================================================================
    // (1b) A chosen factor MOVES the decode accept/reject boundary — both ways
    // =========================================================================

    @Test
    void smallFactor_rejectsAStreamThatDefaultFactorAccepts() throws Exception {
        // 31 chain materialisations of 2048 B each (1 fullChain seed + 30 chainRefs).
        int maxInput = 16 * 1024;                       // 16 KiB
        byte[] chain = chainOfExactSize("Factor", 2048);
        byte[] input = arrayOfRefs(chain, 30);          // 31 × 2048 = 63488 B reconstituted

        // Default per-item factor 8 -> ceiling 131072 B: 63488 < ceiling -> ACCEPTED.
        StreamSchemaDedup accept = new StreamSchemaDedup(false, maxInput, 8, 64);
        byte[] out = accept.reconstituteTopLevelArray(input);
        assertTrue(out.length > 0);
        assertEquals(1, accept.distinctChains());

        // Tighter per-item factor 1 -> ceiling 16384 B: the 9th materialisation (18432 B)
        // crosses the ceiling -> REJECTED at the per-item fence. (streamFactor kept high so
        // the per-item ceiling, not the cumulative one, is the one that fires.)
        StreamSchemaDedup reject = new StreamSchemaDedup(false, maxInput, 1, 1024);
        DerException e = assertThrows(DerException.class,
                () -> reject.reconstituteTopLevelArray(input),
                "a smaller per-item factor must reject a stream the default accepts");
        assertTrue(e.getMessage().contains("maxReconstitutedBytes"),
                "reject must name the per-item ceiling: " + e.getMessage());
    }

    @Test
    void largerFactor_acceptsAStreamThatSmallFactorRejects() throws Exception {
        // Vice-versa: hold the input fixed; a larger per-window factor lifts the cumulative
        // ceiling so a work-spread stream that a tight cumulative factor rejects is accepted.
        int maxInput = 16 * 1024;                       // 16 KiB
        byte[] chain = chainOfExactSize("Window", 2048);
        byte[] digest = sha256(chain);

        // Seed once, then many single-ref items (each far under the per-item cap): a WORK
        // spread that only the cumulative ceiling can catch.
        byte[] seed = arrayContent(List.of(nestedFull(chain)));
        byte[] refItem = arrayContent(List.of(nestedRef(digest)));

        // Small per-window factor 1 -> cumulative ceiling 16384 B: rejects after 8 items.
        StreamSchemaDedup tight = new StreamSchemaDedup(false, maxInput, 1, 1);
        tight.reconstituteTopLevelArray(seed);          // 2048 cumulative
        DerException e = null;
        for (int i = 0; i < 100 && e == null; i++) {
            try { tight.reconstituteTopLevelArray(refItem); } catch (DerException ex) { e = ex; }
        }
        assertNotNull(e, "tight per-window factor must reject the cumulative work spread");
        assertTrue(e.getMessage().contains("maxStreamReconstitutedBytes"),
                "reject must name the cumulative ceiling: " + e.getMessage());

        // Generous per-window factor 64 -> cumulative ceiling 1 MiB: the SAME item sequence
        // (well within 1 MiB) is fully accepted.
        StreamSchemaDedup generous = new StreamSchemaDedup(false, maxInput, 8, 64);
        generous.reconstituteTopLevelArray(seed);
        for (int i = 0; i < 100; i++) {
            generous.reconstituteTopLevelArray(refItem);    // must NOT throw
        }
        assertEquals(1, generous.distinctChains());
        assertTrue(generous.streamReconstitutedChainBytes()
                <= generous.maxStreamReconstitutedChainBytes());
    }

    // ---- stream builders (mirrors StreamSchemaDedupReconstitutionBombTest) ----

    private static byte[] build(String className, int nFields, int classPad) {
        List<AtomicSerialFieldDef> fields = new ArrayList<>(nFields);
        for (int i = 0; i < nFields; i++) {
            fields.add(new AtomicSerialFieldDef(
                    "f" + String.format("%03d", i) + "x".repeat(196), "int"));
        }
        return new AtomicSerialSchemaRecord(
                className + "z".repeat(classPad), fields).encode();
    }

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

    private static byte[] arrayOfRefs(byte[] chain, int refCount) throws Exception {
        byte[] digest = sha256(chain);
        List<byte[]> elems = new ArrayList<>(refCount + 1);
        elems.add(nestedFull(chain));                        // first occurrence seeds the table
        for (int i = 0; i < refCount; i++) elems.add(nestedRef(digest));
        return arrayContent(elems);
    }
}
