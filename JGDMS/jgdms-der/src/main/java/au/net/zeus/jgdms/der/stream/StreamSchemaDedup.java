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

import au.net.zeus.jgdms.der.DerEncoder;
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
import au.net.zeus.jgdms.der.schema.SchemaGenerator;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    // =========================================================================
    // Reconstitution-ceiling expansion factors (sec.C.8.1, RATIFIED defaults
    // 8/64 — Peter 2026-07-24; deployment-tunable via system property)
    // =========================================================================

    /** Package-scoped logger (mirrors the sibling {@code der.object}/{@code der.serial} loggers). */
    private static final Logger LOGGER = Logger.getLogger("au.net.zeus.jgdms.der.stream");

    /**
     * System property overriding {@link #RECONSTITUTION_EXPANSION_FACTOR} (per-item
     * peak-memory ceiling factor). Property namespace mirrors this class's package, the
     * same convention {@link DerInputLimits} uses for {@code au.net.zeus.jgdms.der.*}.
     */
    static final String PROP_RECONSTITUTION_FACTOR =
            "au.net.zeus.jgdms.der.stream.reconstitutionFactor";

    /**
     * System property overriding {@link #STREAM_RECONSTITUTION_EXPANSION_FACTOR}
     * (per-window cumulative-work ceiling factor). For large-schema/high-fan-out
     * deployments where the false-reject ratio cancels {@code maxInputBytes} so only the
     * factor moves the threshold (sec.C.8.1).
     */
    static final String PROP_STREAM_RECONSTITUTION_FACTOR =
            "au.net.zeus.jgdms.der.stream.streamReconstitutionFactor";

    /** Ratified default per-item factor (sec.C.8.1, Peter 2026-07-24). */
    static final int DEFAULT_RECONSTITUTION_FACTOR = 8;

    /** Ratified default per-window factor (sec.C.8.1, Peter 2026-07-24). */
    static final int DEFAULT_STREAM_RECONSTITUTION_FACTOR = 64;

    /**
     * The largest value either factor may be set to. A factor at/above the ~1560×
     * marginal {@code chainRef} amplification ({@code maxChainBytes}/~40 wire bytes)
     * would raise the ceiling to (or past) the unmetered ~24 GiB bomb target — i.e.
     * effectively DISABLE the fence (the exact "fat-finger to Integer.MAX" failure the
     * cap exists to refuse). 1024 is a documented power-of-two ceiling comfortably below
     * that: even at the maximum permitted factor the fence still bites (default 16 MiB ×
     * 1024 = 16 GiB &lt; ~24 GiB bomb), and {@code maxInputBytes × 1024} cannot overflow
     * {@code long} for any {@code int maxInputBytes}. A property value above this is
     * treated as invalid and falls back to the ratified default (never accepted, never
     * silently clamped up to a weaker-but-legal value).
     */
    static final int MAX_EXPANSION_FACTOR = 1024;

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
     *
     * <p><b>Deployment-tunable (sec.C.8.1).</b> Overridable via {@link
     * #PROP_RECONSTITUTION_FACTOR}; read <em>once</em> at class init and hard-validated
     * ({@link #resolveFactor}): a positive integer in {@code [1, }{@link
     * #MAX_EXPANSION_FACTOR}{@code ]}, else the {@linkplain #DEFAULT_RECONSTITUTION_FACTOR
     * ratified default} with a warning (an invalid/hostile value can never weaken the
     * fence into uselessness). The composition invariant {@code
     * STREAM_RECONSTITUTION_EXPANSION_FACTOR >= RECONSTITUTION_EXPANSION_FACTOR} is
     * enforced fail-closed at class init ({@link #checkCompositionInvariant}).
     */
    static final int RECONSTITUTION_EXPANSION_FACTOR;

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
     *
     * <p><b>Deployment-tunable (sec.C.8.1).</b> Overridable via {@link
     * #PROP_STREAM_RECONSTITUTION_FACTOR}; same read-once/validate contract as {@link
     * #RECONSTITUTION_EXPANSION_FACTOR}, additionally bound by the composition invariant
     * (must be {@code >=} the per-item factor).
     */
    static final int STREAM_RECONSTITUTION_EXPANSION_FACTOR;

    static {
        // Read-once (class init), fail-safe per factor, fail-closed on the composition
        // invariant. A misconfigured property can NEVER disable/gap the ceilings: each
        // factor is clamped to a validated positive range (invalid -> ratified default),
        // and an inverted per-window<per-item pair refuses to load (sec.C.8.1).
        int itemFactor = resolveFactor(PROP_RECONSTITUTION_FACTOR, DEFAULT_RECONSTITUTION_FACTOR);
        int streamFactor =
                resolveFactor(PROP_STREAM_RECONSTITUTION_FACTOR, DEFAULT_STREAM_RECONSTITUTION_FACTOR);
        checkCompositionInvariant(itemFactor, streamFactor);
        RECONSTITUTION_EXPANSION_FACTOR = itemFactor;
        STREAM_RECONSTITUTION_EXPANSION_FACTOR = streamFactor;
    }

    /**
     * Reads and validates one expansion-factor system property once (class init). Returns
     * a value guaranteed in {@code [1, }{@link #MAX_EXPANSION_FACTOR}{@code ]}: the parsed
     * value when it is a positive integer within range, otherwise the ratified {@code def}
     * (with a {@code WARNING}). Never returns an out-of-range or non-positive value, so the
     * fence can never be weakened into uselessness by a fat-fingered or hostile property; a
     * {@link SecurityException} reading the property is treated as absence (default).
     *
     * <p>Package-private for direct unit testing of the property→factor wiring.
     */
    static int resolveFactor(String propName, int def) {
        String raw;
        try {
            raw = System.getProperty(propName);
        } catch (SecurityException e) {
            LOGGER.log(Level.WARNING,
                    "Cannot read system property {0} ({1}); using ratified default {2}",
                    new Object[] { propName, e.toString(), def });
            return def;
        }
        return parseFactor(raw, def, propName);
    }

    /**
     * Pure validation of one factor's raw string against the ratified {@code def}
     * (testable without touching system state): {@code null} (unset) → {@code def};
     * non-numeric / {@code <= 0} / {@code >} {@link #MAX_EXPANSION_FACTOR} → {@code def}
     * with a {@code WARNING}. The result is always in {@code [1, MAX_EXPANSION_FACTOR]}
     * (the default itself always is), so the ceiling {@code maxInputBytes × factor} is
     * always bounded and never overflows {@code long}.
     */
    static int parseFactor(String raw, int def, String propName) {
        if (raw == null) {
            return def;
        }
        int v;
        try {
            v = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING,
                    "Non-numeric value \"{0}\" for {1}; using ratified default {2}",
                    new Object[] { raw, propName, def });
            return def;
        }
        if (v <= 0) {
            LOGGER.log(Level.WARNING,
                    "Non-positive value {0} for {1} would disable the reconstitution ceiling;"
                    + " using ratified default {2}",
                    new Object[] { v, propName, def });
            return def;
        }
        if (v > MAX_EXPANSION_FACTOR) {
            LOGGER.log(Level.WARNING,
                    "Value {0} for {1} exceeds the maximum permitted factor {2} (would push the"
                    + " ceiling toward the unmetered bomb target and effectively disable the"
                    + " fence); using ratified default {3}",
                    new Object[] { v, propName, MAX_EXPANSION_FACTOR, def });
            return def;
        }
        return v;
    }

    /**
     * Enforces the composition invariant {@code streamFactor >= itemFactor} fail-closed
     * (sec.C.8.1): the per-window cumulative ceiling MUST NOT sit below the per-item
     * ceiling, or the bound the bomb-fix relies on reopens (one top-level item could
     * breach the cumulative bound before the per-item bound fires — a gap between the two
     * fences). Unlike a single fat-fingered value (which has an obviously-safe recovery,
     * the ratified default), an inverted-but-individually-valid pair has no silent
     * recovery that respects the operator's stated numbers, so this refuses to start with
     * an actionable message rather than silently clamping or defaulting both.
     *
     * @throws IllegalStateException if {@code streamFactor < itemFactor}
     */
    static void checkCompositionInvariant(int itemFactor, int streamFactor) {
        if (streamFactor < itemFactor) {
            throw new IllegalStateException(
                    "StreamSchemaDedup: reconstitution expansion factors violate the composition"
                    + " invariant — streamReconstitutionFactor (" + streamFactor + ", "
                    + PROP_STREAM_RECONSTITUTION_FACTOR + ") must be >= reconstitutionFactor ("
                    + itemFactor + ", " + PROP_RECONSTITUTION_FACTOR + "). The per-window"
                    + " cumulative ceiling (maxStreamReconstitutedBytes) must never be below the"
                    + " per-item ceiling (maxReconstitutedBytes) or the DoS bounds gap reopens."
                    + " Fix the two system properties so streamReconstitutionFactor >="
                    + " reconstitutionFactor (defaults 64 >= 8).");
        }
    }

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

    /** Chain bytes appended to an output (decode direction); always {@code <= streamReconstitutedChainBytes}. */
    private long materialisedChainBytes = 0;

    /**
     * One verified chain: the interned bytes, the parsed (leaf-first) records, the chain
     * identity digest, and (encode direction only) the {@code chainRef} TLV every
     * subsequent occurrence emits.
     */
    private record TableEntry(byte[] chainBytes, List<AtomicSerialSchemaRecord> records,
                              byte[] digest, byte[] chainRefTlv) {}

    /**
     * ENCODE direction only: an index over {@link #table} keyed by the chain's own bytes.
     * Encode-side chain bytes are produced locally by this node's {@code ObjectCodec} from
     * its own classes, so a second occurrence of the same chain is recognised by one
     * content-equality lookup instead of re-hashing the leaf record. It is a pure
     * accelerator: every entry is also in {@link #table}; a miss falls back to the digest
     * path unchanged, and it is bounded by the table ceilings (one entry per tabled chain).
     * Never populated on the decode direction, where chain bytes come from the wire.
     */
    private final Map<ByteBuffer, TableEntry> encodeByBytes = new HashMap<>();

    /**
     * DECODE direction only: an index over {@link #table} keyed by the chain's bytes as
     * received, used by {@link #verifiedChain}. Every entry is a chain this stream already
     * verified through {@link SchemaChain#decodeChain} at its {@code fullChain} site, so a
     * lookup hit means "these exact bytes were verified"; nothing is added except in
     * {@link #insert}, which is metered by the table ceilings, and the index is discarded
     * with the stream. Never consulted for acceptance without a full content comparison.
     */
    private final Map<ByteBuffer, TableEntry> decodeByBytes = new HashMap<>();

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
        this(encoding, maxInputBytes,
                RECONSTITUTION_EXPANSION_FACTOR, STREAM_RECONSTITUTION_EXPANSION_FACTOR);
    }

    /**
     * Test seam: build with explicit (still hard-validated) factors, bypassing the
     * class-init system-property read, so a test can exercise how a chosen factor moves
     * the effective ceiling and the decode accept/reject boundary deterministically in one
     * JVM. Applies the same range and composition-invariant validation the property path
     * does, and the same overflow-checked ceiling derivation.
     *
     * @param encoding      encode ({@code true}) or decode ({@code false}) direction
     * @param maxInputBytes the stream's DoS input cap
     * @param itemFactor    per-item factor (must be in {@code [1, }{@link #MAX_EXPANSION_FACTOR}{@code ]})
     * @param streamFactor  per-window factor (same range; must be {@code >= itemFactor})
     */
    StreamSchemaDedup(boolean encoding, int maxInputBytes, int itemFactor, int streamFactor) {
        requireValidFactor(itemFactor, PROP_RECONSTITUTION_FACTOR);
        requireValidFactor(streamFactor, PROP_STREAM_RECONSTITUTION_FACTOR);
        checkCompositionInvariant(itemFactor, streamFactor);
        this.encoding = encoding;
        // Math.multiplyExact keeps a large factor × maxInputBytes from overflowing to a
        // small/negative ceiling (structurally impossible in the validated range —
        // MAX_EXPANSION_FACTOR × Integer.MAX_VALUE < Long.MAX_VALUE — but pinned defensively).
        this.maxReconstitutedChainBytes =
                Math.multiplyExact((long) maxInputBytes, (long) itemFactor);
        this.maxStreamReconstitutedChainBytes =
                Math.multiplyExact((long) maxInputBytes, (long) streamFactor);
    }

    /** Rejects a factor outside {@code [1, MAX_EXPANSION_FACTOR]} (the validated invariant). */
    private static void requireValidFactor(int factor, String propName) {
        if (factor <= 0 || factor > MAX_EXPANSION_FACTOR) {
            throw new IllegalArgumentException(
                    "StreamSchemaDedup: expansion factor " + factor + " for " + propName
                    + " is out of range [1, " + MAX_EXPANSION_FACTOR + "]");
        }
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

    /**
     * Chain bytes actually appended to an output so far (decode direction) — a test seam for the
     * meter-before-append invariant: it can never exceed {@link #streamReconstitutedChainBytes()}.
     */
    long materialisedChainBytes() {
        return materialisedChainBytes;
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
        return dedupTopLevelAtomic(canonicalRecord, null);
    }

    /**
     * As {@link #dedupTopLevelAtomic(byte[])}, with the record's leaf class so the record's
     * own chain site can be recognised as this node's locally generated chain (verified
     * once per class, see {@link SchemaGenerator#verifiedLocalChain}) instead of being
     * re-parsed and re-hashed per stream. {@code leafClass} is only a hint: when the chain
     * bytes are not exactly the local chain (or {@code leafClass} is {@code null}) the
     * unhinted path runs unchanged, so the output bytes are identical either way.
     */
    byte[] dedupTopLevelAtomic(byte[] canonicalRecord, Class<?> leafClass) throws DerException {
        DerEncoder out = DerEncoder.create();
        dedupTopLevelAtomicTo(canonicalRecord, leafClass, out);
        return out.toByteArray();
    }

    /**
     * As {@link #dedupTopLevelAtomic(byte[], Class)}, appending the stream-form record to {@code out}
     * instead of returning it. On any exception {@code out} holds a partial encoding and must be
     * discarded (the stream is dead on a dedup failure in either direction).
     */
    void dedupTopLevelAtomicTo(byte[] canonicalRecord, Class<?> leafClass, DerEncoder out)
            throws DerException {
        requireDirection(true);
        transformP1(canonicalRecord, leafClass, out);
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
        return reconstituteTopLevelAtomicVerified(streamRecord).recordBytes();
    }

    /**
     * The reconstituted canonical record plus the record's own chain, exactly as this stream
     * verified it at the record's chain site (the digest is the one computed over the
     * received leaf-record bytes, sec.C.7.3).
     *
     * @param recordBytes the byte-exact canonical sec.7.8 four-field record
     * @param topChain    the record's verified schema chain bound to {@code recordBytes}'s
     *                    {@code schemaBytes}/{@code schemaDigest}
     */
    record Reconstituted(byte[] recordBytes, SchemaChain.Result topChain) {}

    /**
     * As {@link #reconstituteTopLevelAtomic}, also returning the record's own verified
     * chain so the caller need not parse (and re-hash) the very bytes this method just
     * verified. The chain and the record bytes come from the same {@link Site}, so they
     * cannot disagree.
     */
    Reconstituted reconstituteTopLevelAtomicVerified(byte[] streamRecord) throws DerException {
        DerEncoder out = DerEncoder.create();
        SchemaChain.Result topChain = reconstituteTopLevelAtomicTo(streamRecord, out);
        return new Reconstituted(out.toByteArray(), topChain);
    }

    /**
     * As {@link #reconstituteTopLevelAtomicVerified}, appending the canonical record to {@code out}
     * and returning the record's own verified chain. On any exception {@code out} holds a partial
     * encoding and must be discarded.
     */
    SchemaChain.Result reconstituteTopLevelAtomicTo(byte[] streamRecord, DerEncoder out)
            throws DerException {
        requireDirection(false);
        reconstitutedChainBytes = 0;   // per-item budget reset (peak memory, sec.C.8.3);
        // streamReconstitutedChainBytes is deliberately NOT reset here — it accumulates
        // cumulative WORK across the whole input window (sec.C.8.1/C.8.3).
        Site site = transformP1(streamRecord, null, out);
        return new SchemaChain.Result(site.records, site.digest.clone());
    }

    /**
     * Decode direction: if {@code chainBytes} are byte-for-byte a chain this stream has
     * already verified (at a {@code fullChain} site, through {@link SchemaChain#decodeChain}),
     * returns that verified chain; otherwise {@code null}, and the caller verifies the bytes
     * itself. A hit proves the bytes were verified (equality, not digest agreement, so a
     * different chain sharing a leaf cannot be mistaken for a tabled one). Used by nested
     * decode to avoid re-parsing and re-hashing the interned chain bytes reconstitution just
     * placed in each nested record; chains inside excluded {@code [8]} regions are not in the
     * table unless byte-identical to a tabled one, and otherwise take the full path.
     */
    SchemaChain.Result verifiedChain(byte[] chainBytes) {
        TableEntry e = decodeByBytes.get(ByteBuffer.wrap(chainBytes));
        return e == null ? null : new SchemaChain.Result(e.records(), e.digest().clone());
    }

    /**
     * Encode direction for a {@code [9]} top-level value array's content
     * ({@code UTF8String(arrayWireType) ++ SEQUENCE(elements)}): if the component is
     * {@code @AtomicSerial}, each element is a P2 chain site and is deduplicated;
     * otherwise the content passes through verbatim.
     */
    byte[] dedupTopLevelArray(byte[] arrayContent) throws DerException {
        DerEncoder out = DerEncoder.create();
        dedupTopLevelArrayTo(arrayContent, out);
        return out.toByteArray();
    }

    /** As {@link #dedupTopLevelArray}, appending to {@code out} (two consecutive TLVs, no wrapper). */
    void dedupTopLevelArrayTo(byte[] arrayContent, DerEncoder out) throws DerException {
        requireDirection(true);
        transformTopLevelArray(arrayContent, out);
    }

    /** Decode-direction counterpart of {@link #dedupTopLevelArray}. */
    byte[] reconstituteTopLevelArray(byte[] arrayContent) throws DerException {
        DerEncoder out = DerEncoder.create();
        reconstituteTopLevelArrayTo(arrayContent, out);
        return out.toByteArray();
    }

    /** As {@link #reconstituteTopLevelArray}, appending to {@code out}. */
    void reconstituteTopLevelArrayTo(byte[] arrayContent, DerEncoder out) throws DerException {
        requireDirection(false);
        reconstitutedChainBytes = 0;   // per-item budget reset (peak memory, sec.C.8.3);
        // streamReconstitutedChainBytes is deliberately NOT reset here (cumulative WORK).
        transformTopLevelArray(arrayContent, out);
    }

    private void requireDirection(boolean wantEncoding) {
        if (this.encoding != wantEncoding) {
            throw new IllegalStateException(
                    "StreamSchemaDedup: wrong direction (instance is "
                    + (encoding ? "encode" : "decode") + ")");
        }
    }

    // =========================================================================
    // The walker (A3): offset-based reads of ONE input buffer, one output DerEncoder
    //
    // Every transform below reads a TLV as a range [start, end) of the input and either re-emits
    // it verbatim (out.raw: no slice, no re-framing) or rebuilds it into `out`, substituting only
    // at chain sites. What is decided, in what order, and every reject message are exactly as in
    // the byte[]-composition walker this replaced (kept frozen in the test tree and run against
    // this one on the whole corpus and on mutated inputs); only how bytes move changed.
    //
    // Two orders matter and are preserved:
    //  * PRE-ORDER (sec.C.6.2): a record's own chain site is processed before its payload interior.
    //    In a P1 record the chain field PHYSICALLY follows the payload, so the fields are located
    //    first, the site processed, and only then is anything emitted.
    //  * METER BEFORE APPEND (G10): decodeSite meters a chain's re-materialisation before that
    //    chain's bytes are appended to `out` (appendChain); appended <= metered <= ceiling holds at
    //    every instant, whatever the nesting order of the appends.
    // On any exception `out` holds a partial encoding and must be discarded by the caller.
    // =========================================================================

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
     *
     * @return this record's own chain site
     */
    private Site transformP1(byte[] in, Class<?> leafHint, DerEncoder out) throws DerException {
        Objects.requireNonNull(in, "recordBytes");
        DerReader outer = new DerReader(in);
        DerReader seq = outer.readSequence();
        if (outer.hasMore()) {
            throw new DerException(
                    "StreamSchemaDedup: trailing bytes after P1 record SEQUENCE");
        }

        // Field 1 (both forms): payloadBytes OCTET STRING — located, not copied.
        int payloadLen = seq.readOctetStringHeader();
        int payloadStart = seq.position();
        seq.skip(payloadLen);

        Site site;
        if (encoding) {
            // Canonical fields 2-3: schemaBytes, schemaDigest (dropped from stream form).
            int chainLen = seq.readOctetStringHeader();
            int chainStart = seq.position();
            seq.skip(chainLen);
            byte[] digestField = seq.readOctetString();
            if (digestField.length != 32) {
                throw new DerException(
                        "StreamSchemaDedup: canonical P1 schemaDigest must be 32 bytes, got "
                        + digestField.length);
            }
            site = encodeSite(in, chainStart, chainLen, leafHint);
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

        // Emit. The payload interior is walked AFTER the record's own chain site (pre-order).
        out.begin(Tag.SEQUENCE).beginOctetString();
        transformHierarchyPayload(in, payloadStart, payloadStart + payloadLen, site.records, 0, out);
        out.end();
        if (encoding) {
            out.raw(site.refOrFullTlv);
        } else {
            appendChain(out, site);
            out.octets(site.digest);
        }
        out.utf8(payloadFormat);
        out.end();
        return site;
    }

    // =========================================================================
    // [9] top-level value array content
    // =========================================================================

    private void transformTopLevelArray(byte[] content, DerEncoder out) throws DerException {
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
            out.raw(content, 0, content.length);
            return;
        }
        // Element SEQUENCE: each element is a P2 nested-site-shaped TLV.
        DerReader seq = r.readSequence();
        if (r.hasMore()) {
            throw new DerException(
                    "StreamSchemaDedup: trailing bytes after [9] array element SEQUENCE");
        }
        out.raw(content, 0, wtEnd);
        out.begin(Tag.SEQUENCE);
        while (seq.hasMore()) {
            int start = seq.position();
            int end = seq.skipTlv();
            transformNestedSite(content, start, end, 0, out);
        }
        out.end();
    }

    // =========================================================================
    // P2 — the nested @AtomicSerial site (sec.C.5.3 DedupNestedAtomicRecord)
    // =========================================================================

    /**
     * Transforms one nested {@code @AtomicSerial} site TLV (the range {@code [start, end)} of
     * {@code in}). The TLV is one of:
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
     * @param depth enclosing nesting depth (mirrors {@code ObjectCodec.encodeNested}'s
     *              accounting: this record's interior is walked at {@code depth + 1})
     */
    private void transformNestedSite(byte[] in, int start, int end, int depth, DerEncoder out)
            throws DerException {
        if (isNullTlv(in, start, end)) {
            out.raw(in, start, end - start);
            return;
        }
        DerReader r = new DerReader(in, start, end);
        Tag t = r.peekTag();
        if (CTX_PROXY.equals(t)) {
            out.raw(in, start, end - start);   // sec.C.6.5: [8] byte region excluded, transitively.
            return;
        }
        if (CTX_ENUM.equals(t)) {
            out.raw(in, start, end - start);   // enum leaf: no chain site, value travels verbatim.
            return;
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
            int chainLen = seq.readOctetStringHeader();   // canonical sec.16: chain first
            int chainStart = seq.position();
            seq.skip(chainLen);
            site = encodeSite(in, chainStart, chainLen, null);
        } else {
            site = decodeSite(seq);                       // stream form: SchemaChainRef first
        }
        int payloadLen = seq.readOctetStringHeader();
        int payloadStart = seq.position();
        seq.skip(payloadLen);
        if (seq.hasMore()) {
            throw new DerException(
                    "StreamSchemaDedup: nested record has trailing content after payloadBytes"
                    + " (exactly two fields)");
        }

        out.begin(Tag.SEQUENCE);
        if (encoding) {
            out.raw(site.refOrFullTlv);
        } else {
            appendChain(out, site);
        }
        out.beginOctetString();
        transformHierarchyPayload(in, payloadStart, payloadStart + payloadLen, site.records, depth + 1, out);
        out.end();
        out.end();
    }

    // =========================================================================
    // Hierarchy payload walk (schema-driven, sec.C.6.2 order)
    // =========================================================================

    /**
     * Walks one hierarchy payload ({@code SEQUENCE} of per-class {@code SEQUENCE}s,
     * root-first), the range {@code [start, end)} of {@code in}, against its verified chain,
     * transforming every interior chain site in schema field order and passing every value TLV
     * through verbatim. Writes the payload's CONTENT into {@code out} (the caller has opened the
     * OCTET STRING that carries it).
     */
    private void transformHierarchyPayload(byte[] in, int start, int end,
                                           List<AtomicSerialSchemaRecord> chainLeafFirst,
                                           int depth, DerEncoder out) throws DerException {
        // Fast path: a hierarchy whose fields cannot contain chain sites is verbatim.
        if (!hierarchyMaybeContainsSites(chainLeafFirst)) {
            out.raw(in, start, end - start);
            return;
        }
        DerReader outer = new DerReader(in, start, end);
        DerReader seq = outer.readSequence();
        if (outer.hasMore()) {
            throw new DerException(
                    "StreamSchemaDedup: trailing bytes after hierarchy SEQUENCE");
        }
        int n = chainLeafFirst.size();
        out.begin(Tag.SEQUENCE);
        // Wire order is root-first (sec.7.8 hierarchy layout); the chain list is leaf-first.
        for (int i = n - 1; i >= 0; i--) {
            AtomicSerialSchemaRecord rec = chainLeafFirst.get(i);
            if (!seq.hasMore()) {
                throw new DerException(
                        "StreamSchemaDedup: hierarchy payload has fewer per-class SEQUENCEs"
                        + " than chain records (missing '" + rec.className() + "')");
            }
            transformClassSequence(in, seq, rec, depth, out);
        }
        if (seq.hasMore()) {
            throw new DerException(
                    "StreamSchemaDedup: hierarchy payload has more per-class SEQUENCEs"
                    + " than chain records");
        }
        out.end();
    }

    /** Transforms one per-class SEQUENCE: one TLV per schema field, in field order. */
    private void transformClassSequence(byte[] in, DerReader hierarchySeq,
                                        AtomicSerialSchemaRecord rec,
                                        int depth, DerEncoder out) throws DerException {
        DerReader classSeq = hierarchySeq.readSequence();
        List<AtomicSerialFieldDef> fields = rec.fields();
        out.begin(Tag.SEQUENCE);
        for (AtomicSerialFieldDef f : fields) {
            if (!classSeq.hasMore()) {
                throw new DerException(
                        "StreamSchemaDedup: class SEQUENCE for '" + rec.className()
                        + "' is missing a TLV for field '" + f.wireName() + "'");
            }
            int start = classSeq.position();
            int end = classSeq.skipTlv();
            transformFieldTlv(in, start, end, f.wireType(), depth, out);
        }
        if (classSeq.hasMore()) {
            throw new DerException(
                    "StreamSchemaDedup: class SEQUENCE for '" + rec.className()
                    + "' has trailing TLVs beyond its schema fields");
        }
        out.end();
    }

    /** Dispatches one field TLV by wire type. Value types pass verbatim (rule 2). */
    private void transformFieldTlv(byte[] in, int start, int end, String wireType, int depth,
                                   DerEncoder out) throws DerException {
        if (wireType.startsWith("@AtomicSerial")) {
            transformNestedSite(in, start, end, depth, out);
            return;
        }
        if (wireType.startsWith("array:")) {
            String comp = wireType.substring("array:".length());
            if (comp.startsWith("@AtomicSerial")) {
                transformNestedArray(in, start, end, depth, out);
                return;
            }
            out.raw(in, start, end - start);   // scalar/String/Class/enum component arrays: no sites.
            return;
        }
        if (CollectionWireTypes.isCollection(wireType)) {
            transformCollection(in, start, end, wireType, depth, out);
            return;
        }
        if ("any".equals(wireType)) {
            transformAny(in, start, end, depth, out);
            return;
        }
        // scalar / String / byte[] / Class / enum:<class> — verbatim.
        out.raw(in, start, end - start);
    }

    /** {@code array:@AtomicSerial:<class>} field value: NULL, or SEQUENCE of P2 sites. */
    private void transformNestedArray(byte[] in, int start, int end, int depth, DerEncoder out)
            throws DerException {
        if (isNullTlv(in, start, end)) {
            out.raw(in, start, end - start);
            return;
        }
        DerReader r = new DerReader(in, start, end);
        DerReader seq = r.readSequence();
        if (r.hasMore()) {
            throw new DerException(
                    "StreamSchemaDedup: trailing bytes after @AtomicSerial array SEQUENCE");
        }
        out.begin(Tag.SEQUENCE);
        while (seq.hasMore()) {
            int elemStart = seq.position();
            int elemEnd = seq.skipTlv();
            transformNestedSite(in, elemStart, elemEnd, depth, out);
        }
        out.end();
    }

    /**
     * Collection field value per the sec.3.8 token grammar. Element order is
     * preserved exactly as received in BOTH directions — the stream's element order
     * is the canonical (pre-transform) order, per sec.C.5.3 ("everything else in the
     * stream grammar is unchanged, octet for octet"); this walker never re-sorts (it opens
     * plain constructs, never a sorted set).
     */
    private void transformCollection(byte[] in, int start, int end, String token, int depth,
                                     DerEncoder out) throws DerException {
        if (isNullTlv(in, start, end)) {
            out.raw(in, start, end - start);
            return;
        }
        if (depth > ObjectCodec.MAX_NESTING) {
            throw new DerException(
                    "StreamSchemaDedup: collection nesting depth " + depth
                    + " exceeds MAX_NESTING (" + ObjectCodec.MAX_NESTING + ")");
        }
        if (CollectionWireTypes.isMap(token)) {
            String[] kv = CollectionWireTypes.mapKeyValueWireTypes(token);
            if (!maybeContainsSites(kv[0]) && !maybeContainsSites(kv[1])) {
                out.raw(in, start, end - start);
                return;
            }
            rebuildCollectionOuter(in, start, end, (entryStart, entryEnd) -> {
                // entry := SEQUENCE { key, value }
                DerReader er = new DerReader(in, entryStart, entryEnd);
                if (!Tag.SEQUENCE.equals(er.peekTag())) {
                    throw new DerException(
                            "StreamSchemaDedup: map entry must be a SEQUENCE{key,value}");
                }
                DerReader entry = er.readSequence();
                int keyStart = entry.position();
                int keyEnd = entry.skipTlv();
                int valStart = entry.position();
                int valEnd = entry.skipTlv();
                if (entry.hasMore()) {
                    throw new DerException(
                            "StreamSchemaDedup: trailing bytes in map entry SEQUENCE");
                }
                out.begin(Tag.SEQUENCE);
                transformElement(in, keyStart, keyEnd, kv[0], depth, out);
                transformElement(in, valStart, valEnd, kv[1], depth, out);
                out.end();
            }, out);
            return;
        }
        String elemWT = CollectionWireTypes.elementWireType(token);
        if (!maybeContainsSites(elemWT)) {
            out.raw(in, start, end - start);
            return;
        }
        rebuildCollectionOuter(in, start, end,
                (elemStart, elemEnd) -> transformElement(in, elemStart, elemEnd, elemWT, depth, out), out);
    }

    /** One collection element / map key / map value ({@code ObjectCodec.encodeElementValue} mirror). */
    private void transformElement(byte[] in, int start, int end, String elemWT, int depth,
                                  DerEncoder out) throws DerException {
        if (CollectionWireTypes.isCollection(elemWT)) {
            transformCollection(in, start, end, elemWT, depth + 1, out);
            return;
        }
        transformFieldTlv(in, start, end, elemWT, depth, out);
    }

    /** Per-element transform used by {@link #rebuildCollectionOuter}: one child, a range of the input. */
    @FunctionalInterface
    private interface ElementTransform {
        void apply(int start, int end) throws DerException;
    }

    /**
     * Rebuilds a collection's outer TLV (SET {@code 0x31} or SEQUENCE {@code 0x30}
     * — whichever was received; the discipline check belongs to the record-level
     * decoder) with each child transformed, ORDER PRESERVED.
     */
    private void rebuildCollectionOuter(byte[] in, int start, int end, ElementTransform per,
                                        DerEncoder out) throws DerException {
        DerReader r = new DerReader(in, start, end);
        Tag outerTag = r.peekTag();
        if (!Tag.SEQUENCE.equals(outerTag) && !Tag.SET.equals(outerTag)) {
            throw new DerException(
                    "StreamSchemaDedup: collection outer tag must be SEQUENCE (0x30) or"
                    + " SET (0x31), got " + outerTag);
        }
        DerReader.TlvHeader hdr = r.readTlvHeader();
        int contentStart = r.position();
        r.skip(hdr.contentLength());
        if (r.hasMore()) {
            throw new DerException(
                    "StreamSchemaDedup: trailing bytes after collection TLV");
        }
        DerReader elems = new DerReader(in, contentStart, contentStart + hdr.contentLength());
        out.begin(outerTag);
        while (elems.hasMore()) {
            int elemStart = elems.position();
            int elemEnd = elems.skipTlv();
            per.apply(elemStart, elemEnd);
        }
        out.end();
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
    private void transformAny(byte[] in, int start, int end, int depth, DerEncoder out)
            throws DerException {
        if (isNullTlv(in, start, end)) {
            out.raw(in, start, end - start);
            return;
        }
        DerReader r = new DerReader(in, start, end);
        Tag t = r.peekTag();
        if (t.tagClass() != Tag.CLASS_CONTEXT) {
            throw new DerException(
                    "StreamSchemaDedup: AnyElement must be context-tagged or NULL, got " + t);
        }
        int n = t.tagNumber();
        if (n <= ANY_TAG_SCALAR_MAX && !t.isConstructed()) {
            out.raw(in, start, end - start);   // scalar arms: value octets verbatim.
            return;
        }
        if (n == ANY_TAG_ATOMIC && t.isConstructed()) {
            Range inner = readExplicitInner(in, r);
            out.begin(t);
            transformNestedSite(in, inner.start, inner.end, depth, out);
            out.end();
            return;
        }
        if ((n == ANY_TAG_CANONICAL_COLL || n == ANY_TAG_ORDERED_COLL) && t.isConstructed()) {
            Range inner = readExplicitInner(in, r);
            DerReader ir = new DerReader(in, inner.start, inner.end);
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
            out.begin(t);
            rebuildCollectionOuter(in, inner.start, inner.end, (childStart, childEnd) -> {
                Tag ct = new DerReader(in, childStart, childEnd).peekTag();
                if (Tag.SEQUENCE.equals(ct)) {
                    // Map entry: SEQUENCE { keyAny, valAny }.
                    DerReader er = new DerReader(in, childStart, childEnd);
                    DerReader entry = er.readSequence();
                    int keyStart = entry.position();
                    int keyEnd = entry.skipTlv();
                    int valStart = entry.position();
                    int valEnd = entry.skipTlv();
                    if (entry.hasMore()) {
                        throw new DerException(
                                "StreamSchemaDedup: trailing bytes in Any map entry");
                    }
                    out.begin(Tag.SEQUENCE);
                    transformAny(in, keyStart, keyEnd, next, out);
                    transformAny(in, valStart, valEnd, next, out);
                    out.end();
                    return;
                }
                transformAny(in, childStart, childEnd, next, out);
            }, out);
            out.end();
            return;
        }
        throw new DerException(
                "StreamSchemaDedup: unregistered/mismatched Any context tag [" + n
                + "] (constructed=" + t.isConstructed() + ") — fail-secure reject");
    }

    /** A range {@code [start, end)} of the input buffer. */
    private record Range(int start, int end) {}

    /** Locates an EXPLICIT wrapper's single inner TLV (trailing bytes rejected). */
    private static Range readExplicitInner(byte[] in, DerReader r) throws DerException {
        DerReader.TlvHeader hdr = r.readTlvHeader();
        int innerStart = r.position();
        r.skip(hdr.contentLength());
        if (r.hasMore()) {
            throw new DerException(
                    "StreamSchemaDedup: trailing bytes after EXPLICIT Any wrapper");
        }
        int innerEnd = innerStart + hdr.contentLength();
        DerReader ir = new DerReader(in, innerStart, innerEnd);
        ir.skipTlv();
        if (ir.hasMore()) {
            throw new DerException(
                    "StreamSchemaDedup: EXPLICIT Any wrapper has trailing bytes after its"
                    + " single inner TLV");
        }
        return new Range(innerStart, innerEnd);
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
    private Site encodeSite(byte[] in, int chainOff, int chainLen, Class<?> leafHint)
            throws DerException {
        if (chainLen == 0) {
            throw new DerException("StreamSchemaDedup: empty schema chain at encode site");
        }
        // Same bytes as an already-tabled chain => same identity: one content-equality
        // lookup replaces re-hashing the leaf record (pure accelerator, see encodeByBytes).
        // The lookup key is a window onto the input, so a repeat occurrence copies nothing.
        TableEntry e = encodeByBytes.get(ByteBuffer.wrap(in, chainOff, chainLen));
        byte[] digest = null;   // set below whenever e == null (the only path that reaches insert)
        List<AtomicSerialSchemaRecord> records = null;
        byte[] chainBytes = null;
        if (e == null) {
            chainBytes = Arrays.copyOfRange(in, chainOff, chainOff + chainLen);   // first occurrence: interned
            SchemaGenerator.VerifiedChain local = leafHint == null ? null
                    : SchemaGenerator.verifiedLocalChain(leafHint, chainBytes, "StreamSchemaDedup(encode)");
            if (local != null) {
                digest = local.leafDigest();
                records = local.records();
            } else {
                digest = leafRecordDigest(chainBytes);
            }
            e = table.get(ByteBuffer.wrap(digest));
        }
        if (e != null) {
            // Subsequent occurrence: MUST be the reference form — no discretion.
            return new Site(e.chainBytes(), e.records(), e.digest(), e.chainRefTlv());
        }
        // First occurrence: verify through the single chain-decode path (chain-level
        // ceilings, Merkle cross-check, completeness — loud local failure on breach),
        // then insert under the stream-level ceilings, then emit the full form. (A hinted
        // local chain was verified through that same path, once per class.)
        if (records == null) {
            records = SchemaChain.decodeChain(chainBytes, "StreamSchemaDedup(encode)");
        }
        TableEntry inserted = insert(digest, chainBytes, records);
        encodeByBytes.put(ByteBuffer.wrap(chainBytes), inserted);
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
            byte[] digest = leafRecordDigest(content);
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
    private TableEntry insert(byte[] digest, byte[] chainBytes,
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
        TableEntry entry = new TableEntry(chainBytes, List.copyOf(records), digest,
                encoding ? DerWriter.writeTlv(TAG_CHAIN_REF, digest) : null);
        table.put(ByteBuffer.wrap(digest), entry);
        if (!encoding) {
            decodeByBytes.put(ByteBuffer.wrap(chainBytes), entry);
        }
        tableBytes += chainBytes.length;
        return entry;
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
     * SHA-256 of the received leaf-record bytes: the first complete SEQUENCE TLV of the
     * chain content, which is the pinned digest preimage (sec.C.7.3). Digests the range in
     * place; the preimage is never copied out.
     */
    private static byte[] leafRecordDigest(byte[] chainBytes) throws DerException {
        DerReader r = new DerReader(chainBytes);
        DerReader.TlvHeader hdr = r.readTlvHeader();
        if (!Tag.SEQUENCE.equals(hdr.tag())) {
            throw new DerException(
                    "StreamSchemaDedup: chain content must begin with an"
                    + " AtomicSerialSchemaRecord SEQUENCE, got " + hdr.tag());
        }
        r.readRawContent(hdr.contentLength());
        MessageDigest md = newSha256();
        md.update(chainBytes, 0, r.position());
        return md.digest();
    }

    /**
     * A pristine SHA-256 instance, never updated; cloned per use (far cheaper than
     * {@link MessageDigest#getInstance}'s synchronized provider lookup, and fresh and
     * thread-confined per call).
     */
    private static final MessageDigest SHA256_PROTOTYPE;
    static {
        try {
            SHA256_PROTOTYPE = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static MessageDigest newSha256() {
        try {
            return (MessageDigest) SHA256_PROTOTYPE.clone();
        } catch (CloneNotSupportedException notCloneable) {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new AssertionError("SHA-256 unavailable", e);
            }
        }
    }

    /**
     * Whether the TLV at {@code [start, end)} is a DER NULL; a first octet of {@code 0x05} followed by
     * anything but {@code 00} and nothing else is malformed and rejected.
     */
    private static boolean isNullTlv(byte[] in, int start, int end) throws DerException {
        if (end == start) {
            throw new DerException("StreamSchemaDedup: empty TLV");
        }
        if (in[start] != 0x05) {
            return false;
        }
        if (end - start != 2 || in[start + 1] != 0x00) {
            throw new DerException("StreamSchemaDedup: malformed NULL TLV (expected 05 00)");
        }
        return true;
    }

    /**
     * Appends a decoded site's chain bytes to {@code out} — the re-materialisation that
     * {@link #decodeSite} has already metered (meter-before-append, G10).
     */
    private void appendChain(DerEncoder out, Site site) {
        out.octets(site.chainBytes);
        materialisedChainBytes += site.chainBytes.length;
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
