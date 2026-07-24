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
import au.net.zeus.jgdms.der.DerInputLimits;
import au.net.zeus.jgdms.der.DerReader;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.getarg.CollectionWireTypes;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Per-stream schema-chain dedup for DER object streams — the STD-006 Appendix C
 * ("Stream Schema Dedup", v0.3-DRAFT) stream layer, mandatory in the released DER
 * object-stream format (Appendix C sec.C.9: dedup is the format, not a mode).
 *
 * <h2>What this class is</h2>
 * <p>
 * One instance is one <b>direction of one stream's dedup state</b>: the per-stream
 * table (sec.C.7 — created at stream open, discarded with the codec, never global,
 * never shared, never pre-seeded) plus the byte-level transform between the two
 * grammars:
 * <ul>
 *   <li><b>Encode direction:</b> canonical record-level full forms (STD-006 sec.7.8
 *       four-field {@code MarshalledInstanceRecord} at P1 sites, STD-008 sec.16
 *       two-field nested record at P2 sites) → the Appendix C <b>stream productions</b>
 *       ({@code DedupMarshalledInstanceRecord} / {@code DedupNestedAtomicRecord},
 *       sec.C.5.3), with each chain site's {@code SchemaChainRef} chosen by the pinned
 *       rule of sec.C.6.1: first occurrence of a chain identity = {@code fullChain
 *       [0]}, every subsequent occurrence = {@code chainRef [1]}. No encoder
 *       discretion, no size threshold, no fallback.</li>
 *   <li><b>Decode direction:</b> stream productions → byte-exact canonical full forms
 *       (reconstitution, sec.C.5.4), with fail-closed resolution (sec.C.7.4): a
 *       {@code chainRef} resolves ONLY against a chain already received and verified
 *       in this same stream; unknown digest, duplicate full form, malformed arm, or
 *       any ceiling breach is a hard reject of the stream. The reconstituted P1
 *       record re-inserts the computed leaf digest as {@code schemaDigest}, so
 *       everything above the transport observes exactly the bytes a canonical
 *       full-form stream would have delivered.</li>
 * </ul>
 *
 * <h2>Traversal order (sec.C.6.2)</h2>
 * <p>
 * "First occurrence" is defined over the record-entry (pre-order) traversal: a
 * record's own chain site precedes every chain site in its payload, payload sites in
 * schema field order, collection/array elements in wire element order, interior
 * structures depth-first. Both directions of this class walk record TLVs as buffered
 * units and process the record's chain site <em>before</em> descending into the
 * payload interior, which is the only order under which sec.C.6.2 is satisfiable in
 * one pass (the P1 record's chain field physically follows its payload bytes).
 *
 * <h2>The {@code [8]} proxy-interior exclusion (sec.C.6.5, RATIFIED)</h2>
 * <p>
 * The entire content of every {@code [8] CTX_PROXY} TLV — top-level item and nested
 * field record, at any depth — is outside the dedup layer, byte-region-scoped and
 * transitive: the walker passes a {@code [8]} TLV through <b>verbatim without
 * descending</b>, in both directions, so no interior chain populates or consults the
 * table and no stream production can appear inside a retained/relayed region. The
 * four trunk retention sites are thereby covered by construction:
 * {@code DerObjectStreamCodec.writeObject}/{@code readObject}'s top-level {@code [8]}
 * pair never routes through this class, and {@code ObjectCodec.encodeProxy}/
 * {@code decodeProxy}'s nested pair produces/consumes bytes inside payload regions
 * this walker skips verbatim.
 *
 * <h2>Chain-site discovery is schema-driven, never sniffed (sec.C.3.1 rule 1)</h2>
 * <p>
 * The payload walker locates P2 sites from the (already verified) chain's field
 * wire-types — {@code "@AtomicSerial"} fields, {@code array:@AtomicSerial:*}
 * elements, collection elements per the sec.3.8 token grammar, and the {@code Any}
 * {@code [20]} arm. Value octets (scalars, strings, {@code byte[]} — including a
 * {@code MarshalledInstance}'s captured inner {@code schemaBytes} field value,
 * sec.C.4.3) travel verbatim and are never matched against, replaced by, or used to
 * resolve a reference, even when they are recognisably chain bytes.
 *
 * <h2>Ceilings (sec.C.8, RATIFIED values; inclusive fenceposts, metered during decode)</h2>
 * <ul>
 *   <li>{@link #MAX_DISTINCT_CHAINS_PER_STREAM} = 256 and
 *       {@link #MAX_DEDUP_TABLE_BYTES} = 1&nbsp;048&nbsp;576: checked at table
 *       <em>insertion</em>, at each {@code fullChain} site, as the stream is
 *       processed — not at stream end (G10). Applied identically as encode-time
 *       failures (sec.C.8.2: an encoder that would exceed a ceiling fails the encode
 *       loudly; it never falls back to un-deduped full forms).</li>
 *   <li>The chain-level ceilings ({@code maxChainRecords} 64 / {@code maxChainBytes}
 *       65536) are enforced inside {@link SchemaChain#decodeChain} — the single
 *       chain-decode path, which this class calls at every {@code fullChain} arm (and
 *       on the encode side at each first occurrence), composing rather than
 *       duplicating the T6 base enforcement.</li>
 * </ul>
 *
 * <h2>Digest preimage (sec.C.7.3, pinned)</h2>
 * <p>
 * The chain identity is SHA-256 over the <b>received leaf-record bytes exactly as
 * received</b> — the first complete SEQUENCE TLV of the {@code fullChain} content —
 * never over a re-encode of the parsed structure. (The two coincide because
 * {@link DerReader}/{@link AtomicSerialSchemaRecord#decode} reject non-canonical DER,
 * but this class hashes received bytes regardless, per the pin.)
 *
 * <h2>Interning note (sec.C.7.6)</h2>
 * <p>
 * The decode table holds exactly one {@code byte[]} (and one parsed record list) per
 * distinct chain; every reference site reuses that array when rebuilding the
 * canonical record bytes, so this class itself never duplicates chain state.
 * Full receiver-side heap sharing INTO reconstructed {@code MarshalledInstance}
 * state (the sec.C.7.6 SHOULD) additionally requires an interning-aware record
 * constructor path ({@code MarshalledInstanceRecord} defensively copies) — that half
 * remains with the C5 storage-interning work and is deliberately not forced here.
 *
 * <p>This class is NOT thread-safe; a stream's codec confines it, matching the
 * per-marshal-stream table scope (sec.C.7.1).
 */
final class StreamSchemaDedup {

    // =========================================================================
    // Stream-format version octet (sec.C.5.2, RATIFIED item 13)
    // =========================================================================

    /** {@code [15]} primitive context tag of the stream-format version octet ({@code 0x8F}). */
    static final Tag TAG_VERSION = new Tag(Tag.CLASS_CONTEXT, false, 15);

    /** The stream-format version this implementation speaks (Appendix C = version 1). */
    static final int STREAM_FORMAT_VERSION = 0x01;

    /**
     * The complete version TLV every DER object stream begins with: {@code 8F 01 01}.
     * Returns a fresh copy (callers append it to write buffers).
     */
    static byte[] versionTlv() {
        return new byte[] { (byte) 0x8F, 0x01, (byte) STREAM_FORMAT_VERSION };
    }

    // =========================================================================
    // Stream-level ceilings (sec.C.8.1, RATIFIED values; inclusive)
    // =========================================================================

    /** Max distinct chain identities in one stream's dedup table (= fullChain count). */
    static final int MAX_DISTINCT_CHAINS_PER_STREAM = 256;

    /** Max sum of stored chain-byte lengths in one stream's table. */
    static final int MAX_DEDUP_TABLE_BYTES = 1_048_576;

    /**
     * Multiplier applied to {@code maxInputBytes} to derive
     * {@link #maxReconstitutedChainBytes}, the absolute ceiling on the total
     * schema-chain bytes re-materialised while reconstituting <em>one</em> top-level
     * item (sec.C.8.1 {@code maxReconstitutedBytes}).
     *
     * <p><b>Why an absolute ceiling, not a ratio.</b> Dedup's whole purpose is that the
     * reconstituted output is <em>larger</em> than the wire input (that expansion is the
     * saving), so a per-item {@code output <= input} or fixed-ratio cap would reject
     * legitimate dedup-heavy traffic — e.g. a collection of thousands of same-typed
     * {@code @AtomicSerial} elements, all sharing one chain, legitimately expands
     * ~30x. The bomb is a difference of <em>magnitude</em>, not ratio: a chainRef is
     * ~40 wire bytes but re-materialises a chain up to {@link SchemaChain#MAX_CHAIN_BYTES}
     * (65536 B), a marginal ~1560x per reference site, and with only {@code maxInputBytes}
     * (default 16 MiB) of references an unmetered decoder buffers ~24 GiB before any base
     * decode or class resolution. The defence is therefore an <em>absolute</em> byte
     * budget for one item's re-materialised chains, sized generously above real payloads
     * (largest heavy-reuse legitimate reconstitution measured in the corpus is tens of
     * MiB) and far below OOM.
     *
     * <p>Tying the budget to {@code maxInputBytes} (rather than a hard constant) keeps it
     * proportional to the deployment's own DoS budget: an operator who raises the input
     * cap to accept larger graphs raises the reconstitution budget in step; one who
     * lowers it for a tighter posture tightens both. Factor 8 gives the default a 128 MiB
     * per-item budget — comfortable headroom over legitimate traffic, a bounded and
     * survivable peak buffer, and ~190x below the unmetered bomb target.
     */
    static final int RECONSTITUTION_EXPANSION_FACTOR = 8;

    /**
     * Multiplier applied to {@code maxInputBytes} to derive
     * {@link #maxStreamReconstitutedChainBytes}, the ceiling on the <em>cumulative</em>
     * schema-chain bytes re-materialised across <em>every</em> top-level item of one
     * input window (sec.C.8.1 {@code maxStreamReconstitutedBytes}).
     *
     * <p><b>Why a cumulative bound is needed in addition to the per-item one.</b> The
     * per-item ceiling ({@link #RECONSTITUTION_EXPANSION_FACTOR}) bounds PEAK MEMORY: the
     * reconstituted buffer for one top-level item, released between {@code readObject}
     * calls. It does <em>not</em> bound cumulative CPU/GC WORK. An attacker who keeps each
     * item just under the per-item cap but sends many items drives unbounded-in-constant
     * array-copy/GC churn: with the whole input window at {@code maxInputBytes} (16 MiB) of
     * ~40-byte {@code chainRef}s spread across ~200 items, ~24 GiB of cumulative
     * re-materialisation flows through the decoder — memory-safe (each item under the peak
     * cap) but a ~1560x work-amplification DoS. This cumulative ceiling fences that WORK.
     *
     * <p><b>Why per input WINDOW, not per stream lifetime — and why they coincide here.</b>
     * The bound is confined to one {@code StreamSchemaDedup} decode instance, whose lifetime
     * is exactly one input window: the DER read path
     * ({@link DerMarshalInputStream}/{@code DerInputLimits.readAllBytesBounded}) eagerly
     * buffers the <em>entire</em> stream into a single {@code byte[]} bounded <em>once</em> by
     * {@code maxInputBytes}, decodes it through one codec, then discards the codec and its
     * dedup table. There is no periodic in-stream reset and no long-running DER stream (unlike
     * {@code AtomicMarshalInputStream}, whose JOSS streams read incrementally over a
     * long-lived connection and reset their counters at periodic {@code TC_RESET} / per-item
     * boundaries so genuine long streams are not rejected). Consequently there is exactly ONE
     * window per DER stream, the cumulative counter is reset only at construction (window
     * open), and it CANNOT reject a genuine long stream — a DER stream is inherently finite
     * (its wire is {@code ≤ maxInputBytes}). If a future streaming/periodic-reset DER path is
     * added, this counter MUST reset at the same boundary the input-byte budget does (window
     * close/reopen), mirroring {@code AtomicMarshalInputStream}.
     *
     * <p><b>Why this cannot reintroduce OOM.</b> Peak memory is guarded by the SEPARATE
     * per-item counter ({@link #reconstitutedChainBytes}, reset each item) and by the buffers
     * being released between {@code readObject} calls — neither of which this counter or its
     * (construction-only) reset touches. Adding a cumulative ceiling only ADDS a fail-closed
     * rejection; it never relaxes the per-item memory bound nor defers buffer release, so peak
     * memory stays bounded to one item ({@code maxReconstitutedBytes}) regardless of how many
     * items the window carries.
     *
     * <p>Factor 64 gives the default a 1 GiB per-window work budget: generous headroom over
     * the measured legitimate cumulative expansion of a dense same-schema stream (single-to-
     * low-double-digit× the ≤16 MiB wire), and ~24x below the ~24 GiB unmetered bomb. Tied to
     * {@code maxInputBytes} so it tracks the deployment's own DoS posture. Necessarily {@code
     * ≥ RECONSTITUTION_EXPANSION_FACTOR} (64 ≥ 8) so the two ceilings compose without a gap:
     * one item can never breach the cumulative bound before the per-item bound fires.
     */
    static final int STREAM_RECONSTITUTION_EXPANSION_FACTOR = 64;

    // =========================================================================
    // SchemaChainRef arm tags (sec.C.5.1): both IMPLICIT primitive context tags.
    // Constructed forms (0xA0/0xA1) do NOT compare equal and are rejected.
    // =========================================================================

    /** {@code fullChain [0] IMPLICIT OCTET STRING} — wire tag byte {@code 0x80}. */
    private static final Tag TAG_FULL_CHAIN = new Tag(Tag.CLASS_CONTEXT, false, 0);

    /** {@code chainRef [1] IMPLICIT OCTET STRING (SIZE(32))} — wire tag byte {@code 0x81}. */
    private static final Tag TAG_CHAIN_REF = new Tag(Tag.CLASS_CONTEXT, false, 1);

    /** {@code [8]} constructed context tag: proxy region — excluded verbatim (sec.C.6.5). */
    private static final Tag CTX_PROXY = new Tag(Tag.CLASS_CONTEXT, true, 8);

    /** {@code [7]} constructed context tag: enum leaf in a polymorphic slot — no chain site. */
    private static final Tag CTX_ENUM = new Tag(Tag.CLASS_CONTEXT, true, 7);

    /** {@code Any} arm tags this walker must recognise (see {@code AnyCodec}). */
    private static final int ANY_TAG_ATOMIC = 20;
    private static final int ANY_TAG_CANONICAL_COLL = 30;
    private static final int ANY_TAG_ORDERED_COLL = 31;
    private static final int ANY_TAG_SCALAR_MAX = 9;

    // =========================================================================
    // Per-stream state
    // =========================================================================

    /** True = encode direction (canonical → stream form); false = decode direction. */
    private final boolean encoding;

    /**
     * The per-stream dedup table: chain identity (32-byte SHA-256 over the received
     * leaf-record bytes) → interned chain bytes + parsed records. Iteration order is
     * insertion order (deterministic); keys are content-compared {@link ByteBuffer}s.
     */
    private final Map<ByteBuffer, TableEntry> table = new LinkedHashMap<>();

    /** Running sum of stored chain-byte lengths (metered at insertion, sec.C.8.2). */
    private long tableBytes = 0;

    /**
     * Absolute ceiling (decode direction) on the schema-chain bytes re-materialised
     * while reconstituting one top-level item — {@code maxInputBytes ×
     * RECONSTITUTION_EXPANSION_FACTOR} (sec.C.8.1 {@code maxReconstitutedBytes}). The
     * dominant, and the only super-linear, cost of reconstitution: every other output
     * byte (verbatim payloads, value TLVs, DER framing) is 1:1 with consumed input and
     * so separately bounded by {@code maxInputBytes}.
     */
    private final long maxReconstitutedChainBytes;

    /**
     * Chain bytes re-materialised so far in the current top-level reconstitution
     * (decode direction). Reset to 0 at each top-level decode entry point (per-item
     * budget: buffers are released between items, so the peak decoder buffer — not the
     * stream total — is what must be bounded, sec.C.8.3). Metered at the single
     * chain-materialisation chokepoint {@link #decodeSite}. Bounds PEAK MEMORY.
     */
    private long reconstitutedChainBytes = 0;

    /**
     * Ceiling (decode direction) on the schema-chain bytes re-materialised across the
     * <em>whole input window</em> — {@code maxInputBytes ×
     * STREAM_RECONSTITUTION_EXPANSION_FACTOR} (sec.C.8.1 {@code maxStreamReconstitutedBytes}).
     * Bounds cumulative reconstitution WORK (CPU/GC), the residual the per-item ceiling
     * leaves open (many items each under the per-item cap). One decode instance = one input
     * window (the whole ≤{@code maxInputBytes} DER buffer), so this is a per-window bound
     * with exactly one window per stream.
     */
    private final long maxStreamReconstitutedChainBytes;

    /**
     * Chain bytes re-materialised so far across the current input window (decode
     * direction). Accumulates across ALL top-level items and is <em>never</em> reset per
     * item — reset only at construction (window open). Metered at the same
     * chain-materialisation chokepoint {@link #decodeSite} as the per-item counter. Bounds
     * cumulative WORK, not peak memory (peak memory is the per-item counter's job); it does
     * not gate buffer release, so it cannot increase peak memory (sec.C.8.3).
     */
    private long streamReconstitutedChainBytes = 0;

    /** One verified chain: the interned bytes and the parsed (leaf-first) records. */
    private record TableEntry(byte[] chainBytes, List<AtomicSerialSchemaRecord> records) {}

    /**
     * @param encoding {@code true} for the encode direction (canonical → stream form),
     *                 {@code false} for the decode direction (stream form → canonical)
     */
    StreamSchemaDedup(boolean encoding) {
        this(encoding, DerInputLimits.DEFAULT.maxInputBytes());
    }

    /**
     * @param encoding      {@code true} for the encode direction, {@code false} for decode
     * @param maxInputBytes the stream's DoS input cap (the codec's {@link DerInputLimits}
     *                      value); scales the absolute reconstitution ceiling
     *                      (sec.C.8.1 {@code maxReconstitutedBytes})
     */
    StreamSchemaDedup(boolean encoding, int maxInputBytes) {
        this.encoding = encoding;
        this.maxReconstitutedChainBytes =
                (long) maxInputBytes * RECONSTITUTION_EXPANSION_FACTOR;
        this.maxStreamReconstitutedChainBytes =
                (long) maxInputBytes * STREAM_RECONSTITUTION_EXPANSION_FACTOR;
    }

    /**
     * The cumulative schema-chain bytes re-materialised so far across this input window
     * (decode direction) — a test/diagnostic seam for the sec.C.8.3 measurement and the
     * cumulative-work-bound conformance vectors. Not reset per item.
     */
    long streamReconstitutedChainBytes() {
        return streamReconstitutedChainBytes;
    }

    /**
     * The per-item schema-chain bytes re-materialised in the current/last top-level
     * reconstitution (decode direction) — a test/diagnostic seam proving the per-item
     * peak-memory bound resets each item while the cumulative-work counter accumulates.
     * Reset to 0 at each top-level entry point.
     */
    long reconstitutedChainBytes() {
        return reconstitutedChainBytes;
    }

    /** The per-item peak-memory ceiling (sec.C.8.1 {@code maxReconstitutedBytes}); test seam. */
    long maxReconstitutedChainBytes() {
        return maxReconstitutedChainBytes;
    }

    /** The cumulative per-window work ceiling (sec.C.8.1 {@code maxStreamReconstitutedBytes}); test seam. */
    long maxStreamReconstitutedChainBytes() {
        return maxStreamReconstitutedChainBytes;
    }

    /** Number of distinct chains currently tabled (test/diagnostic seam). */
    int distinctChains() {
        return table.size();
    }

    // =========================================================================
    // Public entry points (called from DerObjectStreamCodec)
    // =========================================================================

    /**
     * Encode direction: transforms the canonical sec.7.8 four-field
     * {@code MarshalledInstanceRecord} bytes (a {@code [1]} item's content) into the
     * stream-form {@code DedupMarshalledInstanceRecord} (sec.C.5.3), deduplicating
     * this record's chain site and every P2 site in its payload interior in
     * pre-order.
     *
     * @param canonicalRecord the complete canonical record SEQUENCE TLV bytes
     * @return the stream-form record SEQUENCE TLV bytes
     * @throws DerException on any structural mismatch or ceiling breach (loud
     *                      encode-time failure, sec.C.8.2 — never a fallback)
     */
    byte[] dedupTopLevelAtomic(byte[] canonicalRecord) throws DerException {
        requireDirection(true);
        return transformP1(canonicalRecord);
    }

    /**
     * Decode direction: verifies and reconstitutes a stream-form
     * {@code DedupMarshalledInstanceRecord} (a {@code [1]} item's content) into the
     * byte-exact canonical sec.7.8 four-field record, resolving every chain site
     * fail-closed in pre-order (sec.C.7.3/C.7.4).
     *
     * @param streamRecord the stream-form record SEQUENCE TLV bytes as received
     * @return the canonical four-field record SEQUENCE TLV bytes
     * @throws DerException hard stream reject: unknown digest, duplicate full form,
     *                      malformed {@code SchemaChainRef}, chain verification
     *                      failure, or ceiling breach
     */
    byte[] reconstituteTopLevelAtomic(byte[] streamRecord) throws DerException {
        requireDirection(false);
        reconstitutedChainBytes = 0;   // per-item budget reset (peak memory, sec.C.8.3);
        // streamReconstitutedChainBytes is deliberately NOT reset here — it accumulates
        // cumulative WORK across the whole input window (sec.C.8.1/C.8.3).
        return transformP1(streamRecord);
    }

    /**
     * Encode direction for a {@code [9]} top-level value array's content
     * ({@code UTF8String(arrayWireType) ++ SEQUENCE(elements)}): if the component is
     * {@code @AtomicSerial}, each element is a P2 chain site and is deduplicated;
     * otherwise the content passes through verbatim.
     */
    byte[] dedupTopLevelArray(byte[] arrayContent) throws DerException {
        requireDirection(true);
        return transformTopLevelArray(arrayContent);
    }

    /** Decode-direction counterpart of {@link #dedupTopLevelArray}. */
    byte[] reconstituteTopLevelArray(byte[] arrayContent) throws DerException {
        requireDirection(false);
        reconstitutedChainBytes = 0;   // per-item budget reset (peak memory, sec.C.8.3);
        // streamReconstitutedChainBytes is deliberately NOT reset here (cumulative WORK).
        return transformTopLevelArray(arrayContent);
    }

    private void requireDirection(boolean wantEncoding) {
        if (this.encoding != wantEncoding) {
            throw new IllegalStateException(
                    "StreamSchemaDedup: wrong direction (instance is "
                    + (encoding ? "encode" : "decode") + ")");
        }
    }

    // =========================================================================
    // P1 — the [1] item record (sec.C.5.3)
    // =========================================================================

    /**
     * Transforms one P1 record between its two forms. Both directions process the
     * record's own chain site BEFORE the payload interior (pre-order, sec.C.6.2).
     *
     * <p>Canonical form (sec.7.8): {@code SEQUENCE { payload OCTET STRING,
     * schemaBytes OCTET STRING, schemaDigest OCTET STRING(32), payloadFormat
     * UTF8String }}.
     * <p>Stream form (sec.C.5.3): {@code SEQUENCE { payload OCTET STRING,
     * schema SchemaChainRef, payloadFormat UTF8String }} — exactly three fields;
     * the {@code schemaDigest} field is dropped (RATIFIED item 2) and re-derived
     * on reconstitution.
     */
    private byte[] transformP1(byte[] recordBytes) throws DerException {
        Objects.requireNonNull(recordBytes, "recordBytes");
        DerReader outer = new DerReader(recordBytes);
        DerReader seq = outer.readSequence();
        if (outer.hasMore()) {
            throw new DerException(
                    "StreamSchemaDedup: trailing bytes after P1 record SEQUENCE");
        }

        // Field 1 (both forms): payloadBytes OCTET STRING.
        byte[] payload = seq.readOctetString();

        Site site;
        if (encoding) {
            // Canonical fields 2-3: schemaBytes, schemaDigest (dropped from stream form).
            byte[] chainBytes = seq.readOctetString();
            byte[] digestField = seq.readOctetString();
            if (digestField.length != 32) {
                throw new DerException(
                        "StreamSchemaDedup: canonical P1 schemaDigest must be 32 bytes, got "
                        + digestField.length);
            }
            site = encodeSite(chainBytes);
        } else {
            // Stream field 2: SchemaChainRef — processed BEFORE the payload interior.
            site = decodeSite(seq);
        }

        // Final field (both forms): payloadFormat UTF8String; then field-count enforcement.
        String payloadFormat = seq.readUtf8String();
        if (seq.hasMore()) {
            throw new DerException(encoding
                    ? "StreamSchemaDedup: canonical P1 record has trailing content after"
                      + " payloadFormat (sec.7.8: exactly four fields)"
                    : "StreamSchemaDedup: stream P1 record has trailing content after"
                      + " payloadFormat (sec.C.5.3: exactly three fields — a re-inserted"
                      + " schemaDigest or any extra field is rejected)");
        }

        // Payload interior AFTER the record's own chain site (pre-order).
        byte[] payloadOut = transformHierarchyPayload(payload, site.records, 0);

        List<byte[]> children = new ArrayList<>(4);
        children.add(DerWriter.writeOctetString(payloadOut));
        if (encoding) {
            children.add(site.refOrFullTlv);
        } else {
            children.add(DerWriter.writeOctetString(site.chainBytes));
            children.add(DerWriter.writeOctetString(site.digest));
        }
        children.add(DerWriter.writeUtf8String(payloadFormat));
        return DerWriter.writeSequence(children);
    }

    // =========================================================================
    // [9] top-level value array content
    // =========================================================================

    private byte[] transformTopLevelArray(byte[] content) throws DerException {
        DerReader r = new DerReader(content);
        int wtEnd;
        String awt;
        awt = r.readUtf8String();
        wtEnd = r.position();
        if (!awt.startsWith("array:")) {
            throw new DerException(
                    "StreamSchemaDedup: [9] array wireType must start with 'array:', got '"
                    + awt + "'");
        }
        String comp = awt.substring("array:".length());
        if (!comp.startsWith("@AtomicSerial")) {
            // No chain sites (scalar/String/Class/enum components) — verbatim (rule 2).
            return content;
        }
        byte[] wtTlv = r.slice(0, wtEnd);
        // Element SEQUENCE: each element is a P2 nested-site-shaped TLV.
        DerReader seq = r.readSequence();
        if (r.hasMore()) {
            throw new DerException(
                    "StreamSchemaDedup: trailing bytes after [9] array element SEQUENCE");
        }
        List<byte[]> elems = new ArrayList<>();
        while (seq.hasMore()) {
            elems.add(transformNestedSite(readTlv(seq), 0));
        }
        byte[] seqOut = DerWriter.writeSequence(elems);
        byte[] out = new byte[wtTlv.length + seqOut.length];
        System.arraycopy(wtTlv, 0, out, 0, wtTlv.length);
        System.arraycopy(seqOut, 0, out, wtTlv.length, seqOut.length);
        return out;
    }

    // =========================================================================
    // P2 — the nested @AtomicSerial site (sec.C.5.3 DedupNestedAtomicRecord)
    // =========================================================================

    /**
     * Transforms one nested {@code @AtomicSerial} site TLV. The TLV is one of:
     * <ul>
     *   <li>DER NULL ({@code 05 00}) — verbatim;</li>
     *   <li>{@code [7]} enum leaf — verbatim (no chain site);</li>
     *   <li>{@code [8]} nested proxy — <b>verbatim, no descent</b> (the sec.C.6.5
     *       byte-region exclusion: nothing inside a {@code [8]} populates or consults
     *       the table, in either direction);</li>
     *   <li>a SEQUENCE record: canonical STD-008 sec.16 {@code SEQUENCE {
     *       schemaChainBytes OCTET STRING, payloadBytes OCTET STRING }} ⇄ stream-form
     *       {@code SEQUENCE { schema SchemaChainRef, payloadBytes OCTET STRING }}
     *       (chain-first field order in both).</li>
     * </ul>
     *
     * @param tlv   the complete site TLV bytes
     * @param depth enclosing nesting depth (mirrors {@code ObjectCodec.encodeNested}'s
     *              accounting: this record's interior is walked at {@code depth + 1})
     */
    private byte[] transformNestedSite(byte[] tlv, int depth) throws DerException {
        if (isNullTlv(tlv)) {
            return tlv;
        }
        DerReader r = new DerReader(tlv);
        Tag t = r.peekTag();
        if (CTX_PROXY.equals(t)) {
            return tlv;         // sec.C.6.5: [8] byte region excluded, transitively.
        }
        if (CTX_ENUM.equals(t)) {
            return tlv;         // enum leaf: no chain site, value travels verbatim.
        }
        if (!Tag.SEQUENCE.equals(t)) {
            throw new DerException(
                    "StreamSchemaDedup: unexpected tag " + t
                    + " at nested @AtomicSerial site (expected NULL, [7], [8], or SEQUENCE)");
        }
        if (depth + 1 > ObjectCodec.MAX_NESTING) {
            throw new DerException(
                    "StreamSchemaDedup: nesting depth " + (depth + 1)
                    + " exceeds MAX_NESTING (" + ObjectCodec.MAX_NESTING + ")");
        }
        DerReader seq = r.readSequence();
        if (r.hasMore()) {
            throw new DerException(
                    "StreamSchemaDedup: trailing bytes after nested record SEQUENCE");
        }

        Site site;
        if (encoding) {
            byte[] chainBytes = seq.readOctetString();   // canonical sec.16: chain first
            site = encodeSite(chainBytes);
        } else {
            site = decodeSite(seq);                      // stream form: SchemaChainRef first
        }
        byte[] payload = seq.readOctetString();
        if (seq.hasMore()) {
            throw new DerException(
                    "StreamSchemaDedup: nested record has trailing content after payloadBytes"
                    + " (exactly two fields)");
        }
        byte[] payloadOut = transformHierarchyPayload(payload, site.records, depth + 1);

        List<byte[]> children = new ArrayList<>(2);
        if (encoding) {
            children.add(site.refOrFullTlv);
        } else {
            children.add(DerWriter.writeOctetString(site.chainBytes));
        }
        children.add(DerWriter.writeOctetString(payloadOut));
        return DerWriter.writeSequence(children);
    }

    // =========================================================================
    // Hierarchy payload walk (schema-driven, sec.C.6.2 order)
    // =========================================================================

    /**
     * Walks one hierarchy payload ({@code SEQUENCE} of per-class {@code SEQUENCE}s,
     * root-first) against its verified chain, transforming every interior chain site
     * in schema field order and passing every value TLV through verbatim.
     */
    private byte[] transformHierarchyPayload(byte[] payload,
                                             List<AtomicSerialSchemaRecord> chainLeafFirst,
                                             int depth) throws DerException {
        // Fast path: a hierarchy whose fields cannot contain chain sites is verbatim.
        if (!hierarchyMaybeContainsSites(chainLeafFirst)) {
            return payload;
        }
        DerReader outer = new DerReader(payload);
        DerReader seq = outer.readSequence();
        if (outer.hasMore()) {
            throw new DerException(
                    "StreamSchemaDedup: trailing bytes after hierarchy SEQUENCE");
        }
        int n = chainLeafFirst.size();
        List<byte[]> classSeqs = new ArrayList<>(n);
        // Wire order is root-first (sec.7.8 hierarchy layout); the chain list is leaf-first.
        for (int i = n - 1; i >= 0; i--) {
            AtomicSerialSchemaRecord rec = chainLeafFirst.get(i);
            if (!seq.hasMore()) {
                throw new DerException(
                        "StreamSchemaDedup: hierarchy payload has fewer per-class SEQUENCEs"
                        + " than chain records (missing '" + rec.className() + "')");
            }
            classSeqs.add(transformClassSequence(seq, rec, depth));
        }
        if (seq.hasMore()) {
            throw new DerException(
                    "StreamSchemaDedup: hierarchy payload has more per-class SEQUENCEs"
                    + " than chain records");
        }
        return DerWriter.writeSequence(classSeqs);
    }

    /** Transforms one per-class SEQUENCE: one TLV per schema field, in field order. */
    private byte[] transformClassSequence(DerReader hierarchySeq,
                                          AtomicSerialSchemaRecord rec,
                                          int depth) throws DerException {
        DerReader classSeq = hierarchySeq.readSequence();
        List<AtomicSerialFieldDef> fields = rec.fields();
        List<byte[]> out = new ArrayList<>(fields.size());
        for (AtomicSerialFieldDef f : fields) {
            if (!classSeq.hasMore()) {
                throw new DerException(
                        "StreamSchemaDedup: class SEQUENCE for '" + rec.className()
                        + "' is missing a TLV for field '" + f.wireName() + "'");
            }
            out.add(transformFieldTlv(readTlv(classSeq), f.wireType(), depth));
        }
        if (classSeq.hasMore()) {
            throw new DerException(
                    "StreamSchemaDedup: class SEQUENCE for '" + rec.className()
                    + "' has trailing TLVs beyond its schema fields");
        }
        return DerWriter.writeSequence(out);
    }

    /** Dispatches one field TLV by wire type. Value types pass verbatim (rule 2). */
    private byte[] transformFieldTlv(byte[] tlv, String wireType, int depth)
            throws DerException {
        if (wireType.startsWith("@AtomicSerial")) {
            return transformNestedSite(tlv, depth);
        }
        if (wireType.startsWith("array:")) {
            String comp = wireType.substring("array:".length());
            if (comp.startsWith("@AtomicSerial")) {
                return transformNestedArray(tlv, depth);
            }
            return tlv;     // scalar/String/Class/enum component arrays: no sites.
        }
        if (CollectionWireTypes.isCollection(wireType)) {
            return transformCollection(tlv, wireType, depth);
        }
        if ("any".equals(wireType)) {
            return transformAny(tlv, depth);
        }
        return tlv;         // scalar / String / byte[] / Class / enum:<class> — verbatim.
    }

    /** {@code array:@AtomicSerial:<class>} field value: NULL, or SEQUENCE of P2 sites. */
    private byte[] transformNestedArray(byte[] tlv, int depth) throws DerException {
        if (isNullTlv(tlv)) {
            return tlv;
        }
        DerReader r = new DerReader(tlv);
        DerReader seq = r.readSequence();
        if (r.hasMore()) {
            throw new DerException(
                    "StreamSchemaDedup: trailing bytes after @AtomicSerial array SEQUENCE");
        }
        List<byte[]> elems = new ArrayList<>();
        while (seq.hasMore()) {
            elems.add(transformNestedSite(readTlv(seq), depth));
        }
        return DerWriter.writeSequence(elems);
    }

    /**
     * Collection field value per the sec.3.8 token grammar. Element order is
     * preserved exactly as received in BOTH directions — the stream's element order
     * is the canonical (pre-transform) order, per sec.C.5.3 ("everything else in the
     * stream grammar is unchanged, octet for octet"); this walker never re-sorts.
     */
    private byte[] transformCollection(byte[] tlv, String token, int depth)
            throws DerException {
        if (isNullTlv(tlv)) {
            return tlv;
        }
        if (depth > ObjectCodec.MAX_NESTING) {
            throw new DerException(
                    "StreamSchemaDedup: collection nesting depth " + depth
                    + " exceeds MAX_NESTING (" + ObjectCodec.MAX_NESTING + ")");
        }
        if (CollectionWireTypes.isMap(token)) {
            String[] kv = CollectionWireTypes.mapKeyValueWireTypes(token);
            if (!maybeContainsSites(kv[0]) && !maybeContainsSites(kv[1])) {
                return tlv;
            }
            return rebuildCollectionOuter(tlv, entryTlv -> {
                // entry := SEQUENCE { key, value }
                DerReader er = new DerReader(entryTlv);
                if (!Tag.SEQUENCE.equals(er.peekTag())) {
                    throw new DerException(
                            "StreamSchemaDedup: map entry must be a SEQUENCE{key,value}");
                }
                DerReader entry = er.readSequence();
                byte[] keyTlv = readTlv(entry);
                byte[] valTlv = readTlv(entry);
                if (entry.hasMore()) {
                    throw new DerException(
                            "StreamSchemaDedup: trailing bytes in map entry SEQUENCE");
                }
                List<byte[]> pair = new ArrayList<>(2);
                pair.add(transformElement(keyTlv, kv[0], depth));
                pair.add(transformElement(valTlv, kv[1], depth));
                return DerWriter.writeSequence(pair);
            });
        }
        String elemWT = CollectionWireTypes.elementWireType(token);
        if (!maybeContainsSites(elemWT)) {
            return tlv;
        }
        return rebuildCollectionOuter(tlv, elem -> transformElement(elem, elemWT, depth));
    }

    /** One collection element / map key / map value ({@code ObjectCodec.encodeElementValue} mirror). */
    private byte[] transformElement(byte[] tlv, String elemWT, int depth)
            throws DerException {
        if (CollectionWireTypes.isCollection(elemWT)) {
            return transformCollection(tlv, elemWT, depth + 1);
        }
        return transformFieldTlv(tlv, elemWT, depth);
    }

    /** Per-element transform used by {@link #rebuildCollectionOuter}. */
    @FunctionalInterface
    private interface ElementTransform {
        byte[] apply(byte[] elementTlv) throws DerException;
    }

    /**
     * Rebuilds a collection's outer TLV (SET {@code 0x31} or SEQUENCE {@code 0x30}
     * — whichever was received; the discipline check belongs to the record-level
     * decoder) with each child transformed, ORDER PRESERVED.
     */
    private byte[] rebuildCollectionOuter(byte[] tlv, ElementTransform per)
            throws DerException {
        DerReader r = new DerReader(tlv);
        Tag outerTag = r.peekTag();
        if (!Tag.SEQUENCE.equals(outerTag) && !Tag.SET.equals(outerTag)) {
            throw new DerException(
                    "StreamSchemaDedup: collection outer tag must be SEQUENCE (0x30) or"
                    + " SET (0x31), got " + outerTag);
        }
        DerReader.TlvHeader hdr = r.readTlvHeader();
        byte[] content = r.readRawContent(hdr.contentLength());
        if (r.hasMore()) {
            throw new DerException(
                    "StreamSchemaDedup: trailing bytes after collection TLV");
        }
        DerReader elems = new DerReader(content);
        List<byte[]> out = new ArrayList<>();
        while (elems.hasMore()) {
            out.add(per.apply(readTlv(elems)));
        }
        int total = 0;
        for (byte[] c : out) total += c.length;
        byte[] joined = new byte[total];
        int pos = 0;
        for (byte[] c : out) {
            System.arraycopy(c, 0, joined, pos, c.length);
            pos += c.length;
        }
        return DerWriter.writeTlv(outerTag, joined);
    }

    // =========================================================================
    // Any elements (STD-006 memo sec.4 grammar; see AnyCodec)
    // =========================================================================

    /**
     * One {@code AnyElement} TLV: scalars {@code [0..9]} and NULL verbatim;
     * {@code [20]} unwraps to a nested site; {@code [30]}/{@code [31]} unwrap to an
     * inner SET/SEQUENCE of Any elements (map entries are bare inner SEQUENCEs of two
     * Any elements). Depth accounting mirrors the ENCODER's ({@code AnyCodec.encode}:
     * collection arm {@code +1}, object arm same depth), so no encoder-producible
     * stream is falsely rejected here; the record-level decoder's own (stricter)
     * checks still run on the reconstituted bytes.
     */
    private byte[] transformAny(byte[] tlv, int depth) throws DerException {
        if (isNullTlv(tlv)) {
            return tlv;
        }
        DerReader r = new DerReader(tlv);
        Tag t = r.peekTag();
        if (t.tagClass() != Tag.CLASS_CONTEXT) {
            throw new DerException(
                    "StreamSchemaDedup: AnyElement must be context-tagged or NULL, got " + t);
        }
        int n = t.tagNumber();
        if (n <= ANY_TAG_SCALAR_MAX && !t.isConstructed()) {
            return tlv;                         // scalar arms: value octets verbatim.
        }
        if (n == ANY_TAG_ATOMIC && t.isConstructed()) {
            byte[] inner = readExplicitInner(r);
            byte[] innerOut = transformNestedSite(inner, depth);
            return DerWriter.writeTlv(t, innerOut);
        }
        if ((n == ANY_TAG_CANONICAL_COLL || n == ANY_TAG_ORDERED_COLL) && t.isConstructed()) {
            byte[] inner = readExplicitInner(r);
            DerReader ir = new DerReader(inner);
            Tag innerTag = ir.peekTag();
            if (!Tag.SEQUENCE.equals(innerTag) && !Tag.SET.equals(innerTag)) {
                throw new DerException(
                        "StreamSchemaDedup: Any collection inner tag must be SEQUENCE or SET,"
                        + " got " + innerTag);
            }
            if (depth + 1 > ObjectCodec.MAX_NESTING) {
                throw new DerException(
                        "StreamSchemaDedup: Any collection nesting depth " + (depth + 1)
                        + " exceeds MAX_NESTING (" + ObjectCodec.MAX_NESTING + ")");
            }
            int next = depth + 1;
            byte[] innerOut = rebuildCollectionOuter(inner, child -> {
                Tag ct = childTag(child);
                if (Tag.SEQUENCE.equals(ct)) {
                    // Map entry: SEQUENCE { keyAny, valAny }.
                    DerReader er = new DerReader(child);
                    DerReader entry = er.readSequence();
                    byte[] keyTlv = readTlv(entry);
                    byte[] valTlv = readTlv(entry);
                    if (entry.hasMore()) {
                        throw new DerException(
                                "StreamSchemaDedup: trailing bytes in Any map entry");
                    }
                    List<byte[]> pair = new ArrayList<>(2);
                    pair.add(transformAny(keyTlv, next));
                    pair.add(transformAny(valTlv, next));
                    return DerWriter.writeSequence(pair);
                }
                return transformAny(child, next);
            });
            return DerWriter.writeTlv(t, innerOut);
        }
        throw new DerException(
                "StreamSchemaDedup: unregistered/mismatched Any context tag [" + n
                + "] (constructed=" + t.isConstructed() + ") — fail-secure reject");
    }

    /** Reads an EXPLICIT wrapper's single inner TLV (trailing bytes rejected). */
    private static byte[] readExplicitInner(DerReader r) throws DerException {
        DerReader.TlvHeader hdr = r.readTlvHeader();
        byte[] inner = r.readRawContent(hdr.contentLength());
        if (r.hasMore()) {
            throw new DerException(
                    "StreamSchemaDedup: trailing bytes after EXPLICIT Any wrapper");
        }
        DerReader ir = new DerReader(inner);
        DerReader.TlvHeader innerHdr = ir.readTlvHeader();
        ir.readRawContent(innerHdr.contentLength());
        if (ir.hasMore()) {
            throw new DerException(
                    "StreamSchemaDedup: EXPLICIT Any wrapper has trailing bytes after its"
                    + " single inner TLV");
        }
        return inner;
    }

    // =========================================================================
    // Chain-site processing — the dedup core
    // =========================================================================

    /** One processed chain site. */
    private static final class Site {
        /** Canonical chain bytes (interned: one array per distinct chain per stream). */
        final byte[] chainBytes;
        /** Parsed records, leaf-first (verified once per distinct chain per stream). */
        final List<AtomicSerialSchemaRecord> records;
        /** Chain identity: SHA-256 over the leaf-record bytes. */
        final byte[] digest;
        /** Encode direction only: the SchemaChainRef TLV to emit at this site. */
        final byte[] refOrFullTlv;

        Site(byte[] chainBytes, List<AtomicSerialSchemaRecord> records,
             byte[] digest, byte[] refOrFullTlv) {
            this.chainBytes = chainBytes;
            this.records = records;
            this.digest = digest;
            this.refOrFullTlv = refOrFullTlv;
        }
    }

    /**
     * Encode direction: the pinned rule (sec.C.6.1). First occurrence of a chain
     * identity emits {@code fullChain}; every subsequent occurrence emits
     * {@code chainRef}. Table ceilings are loud encode-time failures (sec.C.8.2).
     */
    private Site encodeSite(byte[] chainBytes) throws DerException {
        if (chainBytes.length == 0) {
            throw new DerException("StreamSchemaDedup: empty schema chain at encode site");
        }
        byte[] digest = sha256(leafRecordBytes(chainBytes));
        TableEntry e = table.get(ByteBuffer.wrap(digest));
        if (e != null) {
            // Subsequent occurrence: MUST be the reference form — no discretion.
            return new Site(e.chainBytes(), e.records(), digest,
                    DerWriter.writeTlv(TAG_CHAIN_REF, digest));
        }
        // First occurrence: verify through the single chain-decode path (chain-level
        // ceilings, Merkle cross-check, completeness — loud local failure on breach),
        // then insert under the stream-level ceilings, then emit the full form.
        List<AtomicSerialSchemaRecord> records =
                SchemaChain.decodeChain(chainBytes, "StreamSchemaDedup(encode)");
        insert(digest, chainBytes, records);
        return new Site(chainBytes, records, digest,
                DerWriter.writeTlv(TAG_FULL_CHAIN, chainBytes));
    }

    /**
     * Decode direction: reads the {@code SchemaChainRef} at the reader's position and
     * resolves it fail-closed (sec.C.7.3 verify-before-insert; sec.C.7.4).
     */
    private Site decodeSite(DerReader seq) throws DerException {
        DerReader.TlvHeader hdr = seq.readTlvHeader();
        Tag t = hdr.tag();
        if (TAG_FULL_CHAIN.equals(t)) {
            byte[] content = seq.readRawContent(hdr.contentLength());
            if (content.length == 0) {
                throw new DerException(
                        "StreamSchemaDedup: empty fullChain content (SIZE(1..maxChainBytes))"
                        + " — rejected");
            }
            // 1. Parse + ceilings-during-parse + Merkle cross-check + completeness:
            //    the single chain-decode path (SchemaChain.decodeChain, T6).
            List<AtomicSerialSchemaRecord> records =
                    SchemaChain.decodeChain(content, "StreamSchemaDedup(fullChain)");
            // 2. Digest over the received bytes exactly (sec.C.7.3 preimage pin).
            byte[] digest = sha256(leafRecordBytes(content));
            // 3. Duplicate-full reject (sec.C.6.4: the canonical stream would have used
            //    a reference; accepting would admit a second encoding and a rebinding
            //    surface — this is the mandatory-dedup enforcement check, sec.C.9.2).
            if (table.containsKey(ByteBuffer.wrap(digest))) {
                throw new DerException(
                        "StreamSchemaDedup: duplicate fullChain for an identity already in"
                        + " this stream's table — the pinned rule (sec.C.6.1) requires a"
                        + " chainRef; non-deduped output is a malformed stream (sec.C.9.2)"
                        + " — rejected");
            }
            // 4. Insert (stream-level ceilings metered at insertion, sec.C.8.2).
            insert(digest, content, records);
            // 5. Meter reconstituted output BEFORE the caller re-materialises these bytes
            //    (transformP1/transformNestedSite writeOctetString) — the interior fence
            //    on total re-materialised chain bytes (sec.C.8.1/C.8.3, G10).
            meterReconstitution(content.length);
            return new Site(content, records, digest, null);
        }
        if (TAG_CHAIN_REF.equals(t)) {
            byte[] digest = seq.readRawContent(hdr.contentLength());
            if (digest.length != 32) {
                throw new DerException(
                        "StreamSchemaDedup: chainRef content must be exactly 32 bytes, got "
                        + digest.length + " — rejected");
            }
            TableEntry e = table.get(ByteBuffer.wrap(digest));
            if (e == null) {
                // Unknown-digest hard reject: never a fetch, never a local/registry
                // fallback, never a skip (sec.C.7.4).
                throw new DerException(
                        "StreamSchemaDedup: chainRef names a digest with no prior verified"
                        + " fullChain in this stream — unknown-digest hard reject"
                        + " (sec.C.6.4/C.7.4; references never resolve across streams or"
                        + " against local schemas)");
            }
            // Reference-site amplification is metered here — the same interior fence as
            // the fullChain arm — BEFORE the caller re-materialises the interned chain
            // (each chainRef is ~40 wire bytes but re-emits up to maxChainBytes; without
            // this fence, maxInputBytes of references buffers ~maxChainBytes/40 × that,
            // ~24 GiB by default, sec.C.8.3).
            meterReconstitution(e.chainBytes().length);
            return new Site(e.chainBytes(), e.records(), digest, null);
        }
        throw new DerException(
                "StreamSchemaDedup: unexpected tag " + t + " at SchemaChainRef position"
                + " (expected fullChain [0] 0x80 or chainRef [1] 0x81, primitive; constructed"
                + " arm encodings and the canonical record-level forms are rejected at stream"
                + " chain sites)");
    }

    /** Table insertion with the stream-level ceilings, inclusive fenceposts (sec.C.8). */
    private void insert(byte[] digest, byte[] chainBytes,
                        List<AtomicSerialSchemaRecord> records) throws DerException {
        if (table.size() + 1 > MAX_DISTINCT_CHAINS_PER_STREAM) {
            throw new DerException(
                    "StreamSchemaDedup: distinct chain count would exceed"
                    + " maxDistinctChainsPerStream (" + MAX_DISTINCT_CHAINS_PER_STREAM
                    + ", inclusive; sec.C.8.1) — rejected at insertion");
        }
        if (tableBytes + chainBytes.length > MAX_DEDUP_TABLE_BYTES) {
            throw new DerException(
                    "StreamSchemaDedup: dedup table bytes " + (tableBytes + chainBytes.length)
                    + " would exceed maxDedupTableBytes (" + MAX_DEDUP_TABLE_BYTES
                    + ", inclusive; sec.C.8.1) — rejected at insertion");
        }
        table.put(ByteBuffer.wrap(digest), new TableEntry(chainBytes, records));
        tableBytes += chainBytes.length;
    }

    /**
     * Meters cumulative re-materialised schema-chain bytes for the current top-level
     * reconstitution against {@link #maxReconstitutedChainBytes}, failing closed BEFORE
     * the amplifying allocation (sec.C.8.1 {@code maxReconstitutedBytes}, inclusive
     * fencepost). This is the interior fence that bounds the reconstituted-output buffer
     * — the dominant decoder-memory cost that the table/depth/chain ceilings do NOT
     * cover: those cap distinct/stored/single-chain state, none of them the number of
     * reference sites or the total re-materialised output (sec.C.8.3).
     *
     * @param chainByteLen the chain bytes about to be re-materialised at this decode site
     */
    private void meterReconstitution(int chainByteLen) throws DerException {
        // Per-item ceiling (peak memory). Fires first on a single-item bomb because the
        // per-item budget is the tighter of the two (factor 8 ≤ 64), so the two ceilings
        // compose with no gap and no double-reject.
        long nextItem = reconstitutedChainBytes + chainByteLen;
        if (nextItem > maxReconstitutedChainBytes) {
            throw new DerException(
                    "StreamSchemaDedup: reconstituted schema-chain bytes " + nextItem
                    + " would exceed maxReconstitutedBytes (" + maxReconstitutedChainBytes
                    + " = maxInputBytes × " + RECONSTITUTION_EXPANSION_FACTOR
                    + ", inclusive; sec.C.8.1) — a chainRef fan-out decompression bomb"
                    + " (each ~40-byte reference re-materialises up to maxChainBytes);"
                    + " rejected at the reference site before allocation (sec.C.8.3)");
        }
        // Cumulative per-input-window ceiling (total reconstitution WORK). Catches the
        // residual the per-item ceiling leaves open: many top-level items each under the
        // per-item cap but summing to unbounded array-copy/GC churn across the window.
        // Math.addExact is defensive (values are far from overflow: bound ~1 GiB, chain
        // ≤ maxChainBytes) and pins the fail-closed contract even under a misconfiguration.
        long nextStream = Math.addExact(streamReconstitutedChainBytes, (long) chainByteLen);
        if (nextStream > maxStreamReconstitutedChainBytes) {
            throw new DerException(
                    "StreamSchemaDedup: cumulative reconstituted schema-chain bytes " + nextStream
                    + " would exceed maxStreamReconstitutedBytes ("
                    + maxStreamReconstitutedChainBytes + " = maxInputBytes × "
                    + STREAM_RECONSTITUTION_EXPANSION_FACTOR + ", inclusive; sec.C.8.1) — a"
                    + " chainRef fan-out work-amplification bomb spread across many top-level"
                    + " items (each under maxReconstitutedBytes, cumulatively unbounded);"
                    + " rejected at the reference site before allocation (sec.C.8.3)");
        }
        // Both checks passed — commit both counters. No partial/poisoned state on breach
        // (G6): neither counter advances when either ceiling would be exceeded, and the
        // stream is dead (codec discarded) regardless.
        reconstitutedChainBytes = nextItem;
        streamReconstitutedChainBytes = nextStream;
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    /**
     * The received leaf-record bytes: the first complete SEQUENCE TLV of the chain
     * content — the pinned digest preimage (sec.C.7.3).
     */
    private static byte[] leafRecordBytes(byte[] chainBytes) throws DerException {
        DerReader r = new DerReader(chainBytes);
        DerReader.TlvHeader hdr = r.readTlvHeader();
        if (!Tag.SEQUENCE.equals(hdr.tag())) {
            throw new DerException(
                    "StreamSchemaDedup: chain content must begin with an"
                    + " AtomicSerialSchemaRecord SEQUENCE, got " + hdr.tag());
        }
        r.readRawContent(hdr.contentLength());
        return r.slice(0, r.position());
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 unavailable", e);
        }
    }

    /** Reads one complete TLV (header + content) from {@code r}, returning its bytes. */
    private static byte[] readTlv(DerReader r) throws DerException {
        int start = r.position();
        DerReader.TlvHeader hdr = r.readTlvHeader();
        r.readRawContent(hdr.contentLength());
        return r.slice(start, r.position());
    }

    private static Tag childTag(byte[] tlv) throws DerException {
        return new DerReader(tlv).peekTag();
    }

    private static boolean isNullTlv(byte[] tlv) throws DerException {
        if (tlv.length == 0) {
            throw new DerException("StreamSchemaDedup: empty TLV");
        }
        if (tlv[0] != 0x05) {
            return false;
        }
        if (tlv.length != 2 || tlv[1] != 0x00) {
            throw new DerException("StreamSchemaDedup: malformed NULL TLV (expected 05 00)");
        }
        return true;
    }

    /** Whether a wire type can contain chain sites anywhere beneath it. */
    private static boolean maybeContainsSites(String wireType) throws DerException {
        if (wireType.startsWith("@AtomicSerial")) return true;
        if ("any".equals(wireType)) return true;
        if (wireType.startsWith("array:")) {
            return wireType.substring("array:".length()).startsWith("@AtomicSerial");
        }
        if (CollectionWireTypes.isCollection(wireType)) {
            if (CollectionWireTypes.isMap(wireType)) {
                String[] kv = CollectionWireTypes.mapKeyValueWireTypes(wireType);
                return maybeContainsSites(kv[0]) || maybeContainsSites(kv[1]);
            }
            return maybeContainsSites(CollectionWireTypes.elementWireType(wireType));
        }
        return false;
    }

    /** Whether any field of any record in the chain can contain a chain site. */
    private static boolean hierarchyMaybeContainsSites(
            List<AtomicSerialSchemaRecord> chain) throws DerException {
        for (AtomicSerialSchemaRecord rec : chain) {
            for (AtomicSerialFieldDef f : rec.fields()) {
                if (maybeContainsSites(f.wireType())) return true;
            }
        }
        return false;
    }
}
