# Scope of Work — DER Stream Schema Dedup (canonical, CRIME-safe wire compaction)

- **Drafted:** 2026-07-21.
- **Status:** DRAFT — scoped at Peter's direction ("exactly what we need", 2026-07-21);
  design task breakdown for review; no implementation started.
- **Origin:** the compression discussion in `SOW-Outrigger-DER-Only-JOSS-Rejection.md`
  §9.4. Board seat 3 (performance) quantified the DER invocation stream's largest cost:
  the codec encodes **a full schema chain per `@AtomicSerial` occurrence** — no per-stream
  interning (`DerObjectStreamCodec.java:410`, encode `:1158-1162`) — so a bulk response of
  N entries × F fields carries N×F byte-identical `schemaBytes` arrays (estimated
  100–150 KB of duplicated, highly-compressible schema text per 100-entry MatchSet batch).
  General-purpose compression was ruled out over encrypted connections (CRIME-class
  compression oracle; Peter's decision 6, 2026-07-21). Peter then asked whether a
  *canonical* compression *specific to DER* could avoid CRIME — and the answer derived
  the scheme this SOW builds: **structure-only, value-independent dedup**, CRIME-safe by
  construction, capturing essentially all of the measured redundancy.
- **Ratified (Peter, 2026-07-21):** the direction, and the three construction rules
  (§3.1) that make the scheme CRIME-resistant. Recorded first in
  `SOW-Outrigger-DER-Only-JOSS-Rejection.md` §9.4's clarification note; this SOW is their
  implementation vehicle.
- **Ratified (Peter, 2026-07-21, on T1's [PATCH] finding):** the **base-standard chain
  ceilings** are adopted — T1 found that STD-006 §4.5 has no chain-record-count or
  chain-byte ceiling today and `decodeSchemaChain` loops uncapped below `maxInputBytes`
  (a pre-existing bounds gap in every DER decode path, independent of dedup). STD-006
  §4.5 adopts the chain ceilings and the codec meters them during decode. Tracked as T6
  (§4), dispatched once the wire-format board seat's verdict confirms the proposed
  values and blast radius; T6 lands regardless of T2-T5's timing.
- **Companions:** `JGDMS-STD-006-DER-WireFormat-*-DRAFT.md` (§7.8
  `MarshalledInstanceRecord` — the record whose schema chains this dedups; the wire-form
  extension lands under its governance); `SOW-Outrigger-DER-Only-JOSS-Rejection.md`
  (§9.4 origin; its U1c wire-size probe is this SOW's before-measurement; its Unit 3 C5
  is the *storage-side* sibling of this *wire-side* scheme);
  `SOW-Entry-ATOMIC-DER-Migration.md` §9.6 / the new SOW's §6.1 (EntryRep-v2 — the
  *intra-entry* sibling: one chain per entry instead of per field; complementary, not
  replaced); `SOW-der-serializer-registry.md` (registry/schemaDigest governance
  precedent: wire-visible encoding changes are versioned, board-reviewed changes);
  `JGDMS-Board-Reviewer-Guidance.md` (G1 canonical-form and §2.1/§2.2 domains govern
  review).

---

## 1. What this closes, precisely

The DER object-stream codec transmits the full `AtomicSerialSchemaRecord` chain with
**every** `@AtomicSerial` value it encodes. Within one stream, chains repeat massively
(every entry of a class, every field-MI of a type). This SOW adds a **per-stream,
by-reference dedup layer**: the first occurrence of a schema chain travels in full; every
subsequent occurrence travels as its `schemaDigest` reference. Receivers reconstitute
byte-identical full records. Nothing about values, identity, or trust changes.

Also closed: the encrypted-transport wire-bloat gap that decision 6 (no compression over
encryption) deliberately left open — this scheme is **admissible over TLS/mTLS by
construction** (§3.1), so DER bulk responses stop paying the duplication tax on secure
transports without reopening the CRIME surface.

Explicitly **not** a general-purpose compressor: values are never compressed, and the
optional plaintext-transport compression follow-on
(`SOW-Outrigger-DER-Only-JOSS-Rejection.md` §9.4) remains separate — this SOW likely
shrinks its motivation to nothing, which U1c's probe plus this SOW's T4 measurement will
confirm or refute.

## 2. Verified ground truth

- **The duplication is real and code-visible** (board seat 3, CONFIRMED):
  `DerObjectStreamCodec` encodes each `@AtomicSerial` object as a full
  `MarshalledInstanceRecord` — "full, every occurrence", no per-stream interning or
  back-reference (`DerObjectStreamCodec.java:410`, `:1158-1162`).
- **Chains are pure functions of the class**: `SchemaGenerator` memoizes the *parsed*
  chain per class (`ClassValue`, `SchemaGenerator.java:194-203`); the encoded bytes are
  deterministic per class per codec version. (The related per-marshal re-encode waste is
  the *chain-bytes caching* rider in `SOW-Outrigger-DER-Only-JOSS-Rejection.md` §5 —
  complementary: that fixes redundant *work* at the sender; this fixes redundant *bytes*
  on the wire.)
- **`schemaDigest` already exists as the chain's content address** and is verified
  against `schemaBytes` on decode (`MarshalledInstanceRecord.java:371-382`) — the
  reference key requires no new cryptographic machinery.
- **Schema chains are public, value-independent data**: a chain is the class's field
  structure (names, wire types, parent hash) — the class definition, not a secret. This
  is the fact the CRIME-safety argument rests on.
- **Identity is unaffected by construction elsewhere**: `MarshalledInstance.equals`/
  `hashCode` compare `payloadBytes` only, and the ratified invariant (Outrigger SOW §9.4)
  is that canonical uncompressed bytes are the only identity — dedup is transport
  encoding, below identity.

## 3. Design positions taken here (T1 refines, board reviews)

### 3.1 The three CRIME-safety construction rules (RATIFIED — normative for T1)

Output length must be a function of message **structure**, never of secret **values**:

1. **No match/window/reference ever spans a field-value boundary.** There is no
   cross-value compression context at all — the scheme has no LZ window; its only
   mechanism is whole-chain by-reference substitution.
2. **Values are never compressed — not even solo.** A secret value's compressed length
   leaks its internal entropy profile, which is more than DER's existing length
   side channel reveals. Value bytes travel verbatim.
3. **Dedup is by-reference over the public structural layer only**: schema chains keyed
   by `schemaDigest`. Output length then depends only on *which shapes* appear in the
   stream — information the length side channel reveals regardless — never on secret
   content. Consequence: admissible over encrypted connections; decision 6's
   no-compression rule does not cover it because it is not compression in the
   CRIME-relevant sense.

### 3.2 Canonicality of the dedup encoding itself (G1)

"Optionally deduped" is two encodings for one stream — forbidden. The reference rule is
**pinned and deterministic**: within a stream, the first occurrence (in stream order) of
a given `schemaDigest` MUST carry the full chain; every subsequent occurrence MUST be the
reference form. No encoder discretion, no size threshold, no heuristics. Same record
sequence ⇒ same stream bytes, on every conformant implementation. (Stream bytes are
transport encoding, not value identity — but determinism keeps implementations
comparable, conformance-testable, and free of covert encoder channels.)

### 3.3 Per-stream table, fail-closed resolution (security spine)

- The dedup table is **per-stream (per-connection), strictly**: created at stream open,
  discarded at close. Never global, never cross-stream, never persistent — a shared table
  would be both a poisoning surface and a cross-principal information channel (whether
  *some other* connection already sent a schema).
- A reference MUST resolve only to a chain **already received and digest-verified in
  this same stream**. Reference to an unknown digest = hard decode reject (fail-closed),
  never a fetch, never a fallback, never a skip.
- Verification cost design win: the full chain is digest-verified **once** at first
  occurrence; references are pure table lookups — the receiver does O(distinct schemas)
  hashing per stream instead of O(occurrences). (The same intern-by-digest shape the
  Outrigger SOW §4 mandates for recovery.)
- **Bounds**: pinned ceilings on distinct chains per stream, per-chain size (existing
  STD-006 ceilings apply), and total table bytes; exceeding any = reject, metered during
  decode, not after (G10 interior fence).
- **Reconstitution is byte-exact**: a decoded record's `schemaBytes` MUST be
  byte-identical to the sender's canonical full form (round-trip law: re-encoding a
  decoded record in non-dedup context reproduces canonical bytes). Heap note: receivers
  SHOULD share one interned `byte[]` per distinct chain across reconstituted records —
  the wire dedup hands the C5 heap-half sharing over for free on the receive path.

### 3.4 Versioning and negotiation (fail-loud, never silent)

The reference form is a wire-format change: an old receiver cannot decode it. The
capability MUST be negotiated — the stream/codec version or a `MarshallingFormat`-adjacent
constraint — with **loud** failure semantics: a sender never emits references to a
receiver that has not affirmed support; a receiver meeting an unnegotiated reference form
hard-rejects. No silent downgrade in either direction; whether the *absence* of dedup
support downgrades loudly or transparently falls back to full-form is a T1 decision
(transparent fallback is acceptable — full form is always valid — but MUST be explicit in
the spec, not emergent).

### 3.5 Scope boundary: wire only

The reference form never crosses a persistence boundary and never enters
`MarshalledInstance` identity: stored records, `payloadBytes`, `schemaBytes` fields, and
everything `equals`/`hashCode`/digests/signatures see remain canonical full form.
Storage-side duplication is C5's job (digest-keyed interning); intra-entry duplication is
EntryRep-v2's. The three are complementary layers of the same idea at three lifetimes:
wire (this SOW), heap/disk (C5), entry layout (v2).

## 4. Task breakdown

Effort per project convention; model tier per the 2026-07-20 cost-tiering guidance.
Review dispatches cite `JGDMS-Board-Reviewer-Guidance.md` in the initial brief.

| Task | Deliverable | Agent · effort | Model tier | Review | Depends |
|------|-------------|----------------|-----------|--------|---------|
| **T1** · Spec | Normative wire-form spec: reference-form encoding (tag/shape under STD-006 governance — recommendation: an STD-006 appendix, placement decided here), the §3.1 rules as normative text, §3.2 pinned reference rule, §3.3 table/bounds/fail-closed semantics, §3.4 negotiation, round-trip byte-exactness law, conformance-vector definitions. Peer-implementable from the doc alone (G9) — the future Rust JERI DER peer implements from this spec, so it cannot lean on Java source. | general-purpose · XHIGH | top-tier | **parallel board** (wire-format + security seats) | — |
| **T2** · Java implementation | Encoder (per-stream digest table, first-full-then-reference per §3.2) + decoder (verify-once-intern, reference resolution, bounds metered during decode, hard rejects) in `DerObjectStreamCodec`/`jgdms-der`; negotiation wiring in the stream/codec version layer; interned-`byte[]` sharing on reconstitution. | general-purpose · XHIGH (wire-format security) | sonnet | security-literate reviewer (top-tier) + adversarial probes | T1 |
| **T3** · Tests + adversarial probes | Round-trip byte-exactness; unknown-digest reference → reject; table-bound exhaustion → reject (run the adversarial input, G13); digest-mismatched first occurrence → reject; cross-stream isolation (reference valid in stream A meaningless in stream B); negotiation matrix (old↔new sender/receiver, all four cells loud-or-valid, never silent); degenerate cases (single record, zero `@AtomicSerial` values, one class repeated at ceiling — G11). | general-purpose · XHIGH (test-debug) | sonnet | single reviewer + run-the-probes | T2 |
| **T4** · Measurement | Extend the Outrigger U1c wire-size probe to a three-way comparison: JOSS+DEFLATE baseline vs plain DER vs deduped DER, on the N-entry bulk-response shape. Acceptance: deduped DER on TLS ≤ rough parity with the old JOSS+DEFLATE size for schema-dominated payloads. Also settles whether the §9.4 general-compression follow-on retains any motivation. | general-purpose · MEDIUM | sonnet | single reviewer | T2; Outrigger U1c probe exists |
| **T5** · Rust conformance obligation | Not an implementation task now (Rust JERI DER is future work, per the CEL SOW's T4 sequencing): T5 is the *conformance corpus* — wire vectors (deduped streams + expected reconstituted records + expected rejects) committed alongside T1's spec so the Rust peer validates against them when it arrives, per the STD-011/T5 precedent. | general-purpose · MEDIUM | sonnet | single reviewer | T1, T3 |
| **T6** · Base-standard chain ceilings **[RATIFIED — Peter, 2026-07-21]** | Adopt `maxChainRecords`/`maxChainBytes` into STD-006 §4.5's table (values as board-confirmed from T1's proposal); meter `decodeSchemaChain` during decode at the accumulating frame (G10), hard reject on breach; boundary-pair tests (accepted at ceiling, rejected at ceiling+1) plus an adversarial over-ceiling chain run against the built decoder (G13). Independent of dedup: closes a live bounds gap in every DER decode path on trunk. Compatibility note for the reviewer: values must clear every legitimate chain in the repo (real `@AtomicSerial` hierarchies are shallow; verify, don't assume). | general-purpose · HIGH | sonnet | security-literate reviewer | T1 board verdict (values confirmed) |

**Sequencing:** T1 → T2 → T3 → T4/T5. Independent of the Outrigger SOW's U1/U2 (platform
layer, benefits every `AtomicDerILFactory` consumer); T4 wants U1c's probe for the
baseline column. No coupling to Outrigger's persistence rewrite.

## 5. Non-goals

- **Not general-purpose compression** — no LZ, no entropy coding, no value bytes touched
  (§3.1). The plaintext-transport compression follow-on stays where it is
  (`SOW-Outrigger-DER-Only-JOSS-Rejection.md` §9.4), pending T4's evidence.
- **Not storage-side interning** (C5) and **not EntryRep-v2** — siblings, §3.5.
- **Not cross-stream or cached-session dictionaries** — per-stream only, by security
  design (§3.3); revisiting that is a future SOW with its own threat model, not a tuning
  knob.
- **Not the Rust implementation** — spec + corpus obligations only (T5).

## 6. Open questions (T1 decides, board reviews)

1. **Spec placement:** STD-006 appendix vs standalone standard. Recommendation: STD-006
   appendix (it is an encoding of §7.8's records), mirroring the STD-011 Appendix B
   precedent.
2. **Reference granularity:** whole-chain references only (recommended — simplest, and
   chains repeat as wholes), or additionally per-`AtomicSerialSchemaRecord` links within
   a chain (a subclass chain shares its parent's records with a sibling class's chain).
   The finer grain saves more in class-hierarchy-heavy streams at real complexity cost —
   T1 weighs with numbers from a corpus of real streams, defaulting to whole-chain if the
   delta is small.
3. **Negotiation mechanism:** codec/stream version bump vs constraint-based
   advertisement — T1 picks whichever the existing `MarshallingFormat`/codec-version
   machinery supports with the least new surface, keeping §3.4's loud-failure matrix.
4. **Class-name/TLV-skeleton dictionaries** (mentioned in the origin discussion): dedup
   of repeated class-name strings *outside* schema chains (e.g., in `Any`-form type
   names). Deferred unless T4 shows chains alone leave a material residue — same §3.1
   rules would govern.

---

*End of DRAFT. Scoped 2026-07-21 from the ratified §9.4 clarification; no production code
written or modified in producing this document.*
