# JGDMS-STD-006 Appendix C — Stream Schema Dedup (Canonical, CRIME-Safe By-Reference Schema-Chain Compaction)

**Status:** Draft (T1 deliverable of `SOW-DER-Stream-Schema-Dedup.md`), written as a
normative appendix to `JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md`, to be merged into
that document after board review. This file does not modify STD-006 or any other
document; every place this appendix needs a change to the base standard is called out
explicitly as a **[PATCH]** block, and every contradiction or gap found in the SOW's
ground-truth claims while verifying them against trunk source is recorded as a
**[FLAG]** (per board guidance G3: the codebase is authoritative; discrepancies are
reported, never silently adapted to).
**Version:** 0.2-DRAFT
**Date:** 2026-07-21
**Author:** Peter Firmstone + Claude (T1 of `SOW-DER-Stream-Schema-Dedup.md`)
**Applies to:** JGDMS 4.0.0+ DER object streams (`jgdms-der` stream layer, `AtomicDer*`
JERI invocation layer), and non-JVM JGDMS participants (the future Rust JERI DER peer —
this document plus the T5 conformance corpus are that peer's sole implementation
sources, per G9).
**Depends on:** `JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md` (§3.7 acyclicity, §4.5
profile ceilings, §4.6 tagging mode, §5.1–§5.3 format selection/coexistence, §7.8
`MarshalledInstanceRecord`), JGDMS-STD-008 (§15 DER object-stream framing, §16 nested
`@AtomicSerial` records — the productions this appendix extends).
**Related:** `SOW-DER-Stream-Schema-Dedup.md` (task breakdown; this document is T1;
its §3.1 rules are RATIFIED, Peter 2026-07-21, and appear here as normative text),
`SOW-Outrigger-DER-Only-JOSS-Rejection.md` §9.4/decision 6 (origin; the
no-compression-over-encryption rule this scheme is admissible under), §5 Unit 3 (the C5
storage-interning sibling and the chain-bytes-caching rider),
`SOW-Entry-ATOMIC-DER-Migration.md` §9.6 (EntryRep-v2, the intra-entry sibling),
`JGDMS-STD-011-Appendix-B-DER-Wire-Encoding-v0.1-DRAFT.md` (structural precedent for a
standalone appendix file merged after review), `JGDMS-Board-Reviewer-Guidance.md`
(review standard this document is written against: G1 canonical form and G9
peer-implementability are the primary quality gates).

RFC 2119 keywords (MUST / MUST NOT / SHOULD / SHOULD NOT / MAY) are normative
throughout. **[PROPOSED]** marks a design decision made here that requires Peter's
ratification; **[OPEN]** marks a question deliberately left unresolved, with its owning
task named; **[PATCH]** marks a recommended change to a base document, stated but not
applied; **[FLAG]** marks a verified discrepancy between a source document's claim and
trunk source. Everything not so marked is normative as written, subject to the board
review record below.

**Board review record.** Board-reviewed 2026-07-21 (two adversarial seats:
wire-format/canonicality, adversarial security; both **SOUND-WITH-FIXES**); the
consolidated 8-item fix list was applied in this revision (v0.1 → v0.2). Substantive
changes: the `[8]` proxy-interior exclusion is generalized to **every nesting level,
byte-region-scoped and transitive** — v0.1's claim that nested proxy records have no
retention path was **wrong against trunk** and is recorded as a [FLAG] against this
document's own verification (§C.6.5, §C.13.2); a **chain-completeness rule** closes a
truncated-chain identity collision that would have poisoned the dedup table (§C.5.1,
§C.7.3 — the fix the dropped-`schemaDigest` decision was made contingent on); export
configuration now gates **acceptance as well as emission** (§C.9, resolving a
capable-but-opted-out ambiguity and the per-stream table resource commitment);
marked-profile ceiling precedence over base §4.5 maxima is pinned (§C.8.1); the digest
preimage is pinned to received bytes with canonical-encoding rejection (§C.7.3);
§C.3.3's side-channel non-claim is corrected (dedup does reveal chain distinctness —
§C.3.3); a new base-standard validation gap ([FLAG]: P2 decode skips the Merkle
cross-check) is ledgered with a [PATCH] (§C.7.3, §C.13.2); and the conformance corpus
gains boomerang-relay, exclusion-transitivity, max-chain fan-out, depth-interleaving,
and non-canonical interior-encoding vectors (§C.11). Positively discharged by the
board (no redundant defense to be added): the CRIME thesis term-by-term,
verify-before-insert ordering, fail-closed completeness including cleanup, the
downgrade/echo analysis, `[15]` tag safety, the traversal-order definition, and the
dropped-`schemaDigest` decision (now that the completeness rule holds).

---

> **Decision summary (for the board; each decided section carries the full rationale).**
>
> | # | SOW item | Decision here |
> |---|---|---|
> | 1 | §6.1 placement | **STD-006 appendix**, authored as this standalone file and merged after review (the STD-011 Appendix B precedent). §C.1.3. |
> | 2 | §6.2 reference granularity | **Whole-chain only**; per-record links rejected with numbers (estimated-from-code basis). §C.10. |
> | 3 | §6.3 negotiation mechanism | **Export-time capability (ILFactory-scoped, §5.1-shaped) + in-band per-stream mode marker `[15]` + response-echoes-request.** The export configuration gates **both emission and acceptance** (config-off endpoints reject marked streams loudly — board fix 3); absence of support falls back **transparently to full form** on the sending side (normative), and every mismatch cell is loud. §C.9. |
> | 4 | Ceilings | `maxDistinctChainsPerStream` = 256, `maxChainRecords` = 64, `maxChainBytes` = 65536, `maxDedupTableBytes` = 1 048 576 — inclusive, metered during decode; the marked-stream profile deliberately tightens the admissible class set relative to base §4.5 maxima (loud encode-time failure — board fix 4). §C.8 [PROPOSED]. |
> | 5 | (found in design) dedup-mode record shape | The dedup-mode §7.8 record **drops the `schemaDigest` field** (derivable in both arms; removes a restate-and-disagree surface). Contingent on the §C.5.1 chain-completeness rule (board fix 2), which makes chain identity → chain bytes injective. §C.5.3 [PROPOSED]. |
> | 6 | (found in source) table scope | **Per marshal stream** (one codec lifetime: the request *or* response object stream of one call, or one standalone encode unit) — a precision correction to the SOW's "per-stream (per-connection)" parenthetical. §C.7.1 [FLAG]. |
> | 7 | (found in source) boomerang seam | **Every** `[8]` proxy item's interior — at any nesting level — is **excluded** from dedup, byte-region-scoped and transitive (raw-wire-form retention's verbatim-relay contract exists at both the object-stream and the nested field level; v0.1 wrongly claimed top-level only). §C.6.5 [PROPOSED]. |
> | 8 | (board round 1) chain completeness | A `fullChain` whose **last record carries `parentSchemaHash`** is rejected (truncated-chain reject) — closes a table-poisoning identity collision; recommended for base §7.8 decode too ([PATCH]). §C.5.1, §C.7.3, §C.8.1. |

---

## C.1 Purpose and Scope

### C.1.1 What this defines

The DER object-stream codec (STD-008 §15, `DerObjectStreamCodec`) transmits the full
`AtomicSerialSchemaRecord` chain with **every** `@AtomicSerial` value it encodes — "full,
every occurrence", no per-stream interning (verified against trunk:
`DerObjectStreamCodec.java:410` states it as the item contract, `:1158-1162` implements
it). Within one stream, chains repeat massively: a bulk response of N entries × F
`MarshalledInstance`-wrapped fields carries on the order of N×F byte-identical chain
copies (§C.10.2 reconstructs the SOW's 100–150 KB per 100-entry batch estimate from
record sizes in source).

This appendix defines a **per-stream, by-reference dedup layer** for those chains:

- the **first occurrence** of a schema chain in a stream travels in full;
- **every subsequent occurrence** travels as a 32-byte digest reference;
- receivers reconstitute **byte-identical** full chains before anything above the
  transport sees them.

Nothing about values, identity, or trust changes. The scheme is **not compression** in
the CRIME-relevant sense: it removes only structural, value-independent redundancy, and
is therefore admissible over encrypted transports that general-purpose compression is
banned from (§C.3; `SOW-Outrigger-DER-Only-JOSS-Rejection.md` §9 decision 6).

### C.1.2 Scope boundary: wire only (NORMATIVE)

The reference form is a **transport encoding, below identity**. It exists only inside a
negotiated DER object stream, between one encoder and one decoder, for the lifetime of
that stream:

1. The reference form MUST NOT cross any persistence boundary. Stored records, snaplog
   state, and any bytes handed to a storage layer are canonical full form.
2. The reference form MUST NOT enter `MarshalledInstance` identity. The `payloadBytes`,
   `schemaBytes`, `schemaDigest`, and `payloadFormat` values that
   `MarshalledInstance.equals`/`hashCode`, Entry byte-matching, digests, and signatures
   observe remain canonical full form at all times, on both sides. (The ratified
   invariant from `SOW-Outrigger-DER-Only-JOSS-Rejection.md` §9 item 4: canonical
   uncompressed bytes are the only identity.)
3. A standalone `MarshalledInstance` capture stream (`DerMarshalInstanceOutput` /
   `DerMarshalInstanceInput` — the stream that produces an MI's own captured
   `payloadBytes`/`schemaBytes` state) MUST NOT use dedup mode. Those bytes are
   identity- and persistence-bearing by construction.
4. Dedup applies per individual marshal stream and only when negotiated (§C.9). An
   un-negotiated stream is byte-identical to the pre-dedup format.

Storage-side duplication is C5's job (digest-keyed interning); intra-entry duplication
is EntryRep-v2's. The three are complementary layers of one idea at three lifetimes:
wire (this appendix), heap/disk (C5), entry layout (v2). See §C.12.

### C.1.3 Placement decision (SOW §6.1) — STD-006 appendix

**Decision: STD-006 appendix**, following the SOW's recommendation, for two reasons:

1. Everything here is an alternative encoding of records STD-006/STD-008 already
   govern — §7.8 `MarshalledInstanceRecord` and the STD-008 §16 nested record. It
   introduces no new value type, no new semantics, and inherits §4.5's ceilings style,
   §4.6's tagging mode, and principle 6's fail-secure decode wholesale. A standalone
   standard would have to re-import all of that by reference and would still be
   meaningless without STD-006 open beside it — the definition of content that belongs
   in an appendix.
2. The delivery mechanism follows the `JGDMS-STD-011-Appendix-B-DER-Wire-Encoding`
   precedent exactly: authored as a standalone file for independent board review,
   merged into the base document afterward.

**[OPEN → merge editor]** STD-006 v0.13 has no appendices A or B; the letter "C" comes
from the SOW's task breakdown. Whether the merged appendix keeps the letter C (reserving
A/B) or is re-lettered at merge is editorial and deliberately not decided here.

### C.1.4 What this deliberately is not

- **Not general-purpose compression.** No LZ window, no entropy coding, no value bytes
  touched (§C.3.1 rules 1–2). The plaintext-transport compression follow-on stays where
  it is (`SOW-Outrigger-DER-Only-JOSS-Rejection.md` §9.4), pending T4's measurement.
- **Not storage interning (C5), not EntryRep-v2** — siblings, §C.1.2/§C.12.
- **Not cross-stream or cached-session dictionaries.** Per-stream only, by security
  design (§C.7); revisiting that is a future SOW with its own threat model.
- **Not a change to schema semantics.** Chain content, `parentSchemaHash` linking,
  digest algorithm, decode gates, `GetArg` population, and §11 evolution rules are
  untouched. Only the *carriage* of chain bytes changes.
- **Not the Java implementation (T2), tests (T3), measurement (T4), or Rust corpus
  obligations (T5)** — those implement exactly what is written here.

---

## C.2 Definitions

| Term | Meaning |
|---|---|
| **Schema chain** | The leaf-first concatenation of `AtomicSerialSchemaRecord` DER SEQUENCEs for one `@AtomicSerial` class hierarchy (STD-006 §7.8 "Schema chain encoding"). A pure function of the class (`SchemaGenerator.generateChain`, memoised per class — `SchemaGenerator.java:194-203`). |
| **Chain identity / chain digest** | `SHA-256(DER(leaf AtomicSerialSchemaRecord))`, computed over the received leaf-record bytes (§C.7.3). Because `parentSchemaHash` forms a Merkle chain (§7.8) **and this appendix enforces chain completeness** (a terminal record carrying `parentSchemaHash` is rejected, §C.5.1), the leaf digest commits the entire chain's bytes and the identity→bytes mapping is **injective**: two accepted chains with equal leaf digests are byte-identical absent a SHA-256 collision. (Without the completeness rule the claim is false — a truncated `{leaf}` and the full `{leaf, parent, root}` share a leaf digest with different bytes; board round 1, fix 2.) |
| **Stream** | One DER object stream: the byte sequence produced by one `DerObjectStreamCodec` lifetime — concretely, the argument stream *or* the return stream of one remote call (`createMarshalOutputStream` / `createMarshalInputStream` each construct a fresh codec), or one standalone encode/decode unit. See §C.7.1. |
| **Marked stream** | A stream that begins with the dedup mode marker (§C.5.2) and therefore uses the dedup-mode grammar at every chain site. An **unmarked stream** uses the pre-dedup grammar exactly, byte-for-byte. |
| **Chain site** | A position in the stream grammar where the encoder emits a schema chain (§C.4): the chain field of a §7.8 `MarshalledInstanceRecord` (production P1) or of a STD-008 §16 nested record (production P2). |
| **Occurrence** | One chain site instance in one stream, ordered by the normative traversal order (§C.6.2). |
| **Dedup table** | The per-stream map from chain digest to verified chain bytes (decoder) / to emitted-marker (encoder). Created at stream open, discarded at stream close (§C.7). |
| **Full-chain form / reference form** | The two arms of `SchemaChainRef` (§C.5.1): the complete canonical chain bytes, or the 32-byte chain digest of a chain already carried in full earlier in the same stream. |
| **Reconstitution** | The decoder-side recovery of canonical full-form bytes for every referenced chain, such that everything above the transport observes exactly the bytes a pre-dedup stream would have delivered (§C.5.4). |

---

## C.3 Security Thesis — Length Independence by Construction (NORMATIVE)

### C.3.1 The three construction rules (RATIFIED — normative text)

The following three rules were ratified by Peter (2026-07-21; recorded first in
`SOW-Outrigger-DER-Only-JOSS-Rejection.md` §9.4's clarification note, restated as
`SOW-DER-Stream-Schema-Dedup.md` §3.1). They are the load-bearing construction
constraints of this appendix; every encoding rule in §C.5–§C.7 MUST be checkable
against them, and no future revision of this appendix may add a mechanism that violates
any of them without a new, board-reviewed security argument:

1. **No match, window, or reference ever spans a field-value boundary.** There is no
   cross-value compression context of any kind — the scheme has no LZ window; its only
   mechanism is whole-chain by-reference substitution at fixed grammar sites (§C.4).
   In particular, bytes *inside* any field value MUST NOT be replaced by, or serve as
   the resolution target of, a reference — even when those bytes are recognisably a
   schema chain (see §C.4.3, the `MarshalledInstance`-as-value case).
2. **Values are never compressed — not even solo.** A secret value's compressed length
   leaks its internal entropy profile, which is more than DER's existing length side
   channel reveals. Value bytes (payload octets, scalars, strings, byte arrays —
   everything that is not a chain site) travel verbatim in both stream modes.
3. **Dedup is by-reference over the public structural layer only**: schema chains keyed
   by their chain digest. A chain is the class's field structure — names, wire types,
   parent hash — i.e. the class definition, public and value-independent
   (`SchemaGenerator` derives it from the class alone; no instance data enters it).
   Output length then depends only on *which shapes* appear in the stream —
   information the length side channel reveals regardless — never on secret content.

### C.3.2 Claim and argument

> **Claim (length independence).** For a fixed stream mode, the byte length of an
> encoded stream is a function of the stream's *structure* only: the sequence of item
> kinds, each value's structural shape (declared classes, chain identities, collection
> sizes, scalar/string/octet lengths — every quantity the pre-dedup encoding's length
> already depends on), and the multiset-with-order of chain identities at chain sites.
> No secret value octet influences the length delta this appendix introduces.

**Argument.**

*(i) The only length-affecting mechanism is chain-site substitution.* By construction
(§C.5), a marked stream differs from the corresponding unmarked stream in exactly three
places: the fixed-size stream marker (constant length), the removal of the `schemaDigest`
field from P1 records (constant 34-byte delta per P1 site, independent of content), and
the choice of `SchemaChainRef` arm at each chain site. Every other octet — payload
values, scalars, tags, collection contents — is emitted verbatim by the same rules as
the unmarked grammar (rule 2). Hence
`len(marked) = len(unmarked) + len(marker) − 34·|P1 sites| − Σ_subsequent (len(chainᵢ) − len(ref))`,
where the sum ranges over subsequent occurrences under the pinned rule of §C.6.

*(ii) Every term of that expression is a function of structure.* `|P1 sites|` and the
occurrence sequence are determined by the object graph's shape (which classes appear,
where, how many times) — the same facts that determine the unmarked stream's length.
Which occurrences are "subsequent" is determined by chain-identity equality in traversal
order (§C.6), and chain identity is a function of the class alone (§C.2): no instance
value can make two sites share or not share a chain. `len(chainᵢ)` is a function of the
class. `len(ref)` is constant (34 bytes).

*(iii) No adversary-visible length varies with a secret value.* Combining (i) and (ii):
two streams that are structurally identical but differ arbitrarily in field values
(payload octets of any field, of any record) produce marked encodings of **identical
length**, because no term above reads a value octet. The scheme therefore adds *zero*
value-dependent length variation beyond what DER's explicit length fields already
reveal in the unmarked format. This is precisely the property whose absence makes
DEFLATE-over-TLS a CRIME oracle: there, a shared compression context lets an
attacker-controlled plaintext's length depend on its byte-level similarity to a secret
plaintext. Here there is no shared context between values at all (rule 1), no value is
ever shortened (rule 2), and the only shared state — the chain table — is keyed by
public class structure (rule 3). ∎

**Consequence (admissibility over encrypted transports, NORMATIVE).** Decision 6
(`SOW-Outrigger-DER-Only-JOSS-Rejection.md` §9, ratified 2026-07-21) bans
general-purpose compression over connections with confidentiality, because compressed
length is a function of plaintext content. This scheme's output length is a function of
plaintext *structure* only, so decision 6 does not cover it: dedup MAY be negotiated
over TLS/mTLS and every other transport, and the encrypted bulk-response wire-bloat gap
decision 6 deliberately left open is closed by it. DEFLATE and any other
value-reading compressor remain banned over encrypted transports regardless of this
appendix.

### C.3.3 What the thesis does not claim (informative, honesty note)

- **It does not claim the structural length channel is closed — and dedup widens it
  slightly.** Message lengths reveal message shape — which classes appear, how many,
  collection sizes — with or without dedup. Dedup additionally reveals chain
  **distinctness**: two records carrying equal-length but *distinct* chains encode as
  two full forms, while two records of the *same* class encode as full + reference —
  observably different lengths where the unmarked format's lengths coincide. An
  observer of ciphertext lengths can therefore infer whether shapes repeat, which the
  unmarked format does not always disclose. (v0.1 claimed everything so inferable was
  already inferable unmarked; that was an overclaim — corrected per board round 1,
  fix 7.) The delta is confined to *structural, public* facts — which class shapes
  appear and whether they coincide — never value content: rule 3 and the §C.3.2
  value-independence argument are unaffected, and no JGDMS security property depends
  on hiding class-shape repetition from an observer who can see ciphertext lengths.
- **It does not claim confidentiality of schema presence or content.** Schema-bearing
  DER is readable by whoever holds the bytes (STD-009 §8's honesty note applies); a
  transport observer with lengths only can estimate which schemas repeat. Neither is a
  secret this format protects.
- **It does not claim timing-side-channel resistance.** Table lookups and
  verify-once hashing may take value-independent but shape-dependent time;
  construction-safety and side-channel-safety are different properties
  (`SOW-BAE-Timing-Sidechannel-Denial.md` owns that axis).
- **It does not claim integrity beyond the transport's.** The chain digest is a content
  address, verified by recomputation over bytes received *in the same stream* — it is
  self-consistent, not bound to an external known-good reference (G12). Dedup grants an
  attacker who controls the stream nothing they lack today: such an attacker already
  delivers arbitrary full chains to the pre-dedup decoder, and every existing decode
  gate (admission checks, `check(GetArg)`, ceilings, §7.8 chain cross-checks) runs
  unchanged on reconstituted chains. What dedup must *itself* guarantee — and does, by
  §C.6/§C.7 — is that the reference indirection cannot cause a chain substitution the
  full-form stream could not have expressed: within one stream a digest binds to
  exactly one verified chain (first occurrence), rebinding is unrepresentable
  (duplicate full form is a hard reject, §C.6.4), and no other stream's bindings are
  reachable (§C.7.5).
- **The argument covers exactly this scheme.** Any extension — per-record links
  (§C.10), class-name or TLV-skeleton dictionaries (SOW §6.4) — requires re-running
  §C.3.2 for the new mechanism before adoption.

---

## C.4 Chain Sites — Where Schema Chains Occur (verified against trunk)

This section is the ground-truth inventory the rest of the appendix quantifies over.
It was verified against trunk source, not taken from the SOW (G3); §C.13.2 records the
discrepancies found.

### C.4.1 Production P1 — `MarshalledInstanceRecord` (§7.8)

The four-field record `SEQUENCE { payloadBytes, schemaBytes, schemaDigest,
payloadFormat }` occurs in the object stream at:

- **P1a — top-level `[1] CTX_ATOMIC` items**: every `@AtomicSerial` object written via
  `writeObject` (`DerObjectStreamCodec.writeObject` → `encodeAtomicRecord`,
  `DerObjectStreamCodec.java:1158-1162`).
- **P1b — the handler record inside a top-level `[8] CTX_PROXY` item**: a bare
  `java.lang.reflect.Proxy`'s `@AtomicSerial` `InvocationHandler` rides as an embedded
  `[1]` record inside the `[8]` content. **Excluded from dedup** — see §C.6.5.

### C.4.2 Production P2 — nested `@AtomicSerial` record (STD-008 §16)

The two-field record `SEQUENCE { schemaChainBytes OCTET STRING, payloadBytes OCTET
STRING }` (chain first — note the field order differs from P1) occurs wherever an
`@AtomicSerial` value sits below top level (`ObjectCodec.encodeNested/decodeNested`):

- **P2a — nested `@AtomicSerial`-typed fields** of an enclosing record's payload;
- **P2b — the `Any` polymorphic slot's `atomicSerialObject [20]` arm** (`AnyCodec`,
  which wraps the same P2 record);
- **P2c — elements of `array:@AtomicSerial:<class>` fields** and of collection fields
  with `@AtomicSerial` element type (each element is a P2 record);
- **P2d — the handler record of a *nested* (field-level) proxy** (`ObjectCodec.
  encodeProxy` → `encodeNested`). **Not a chain site — excluded from dedup**: this
  path consults raw-wire-form retention exactly as the top-level `[8]` does
  (`ObjectCodec.encodeProxy` re-emits retained bytes verbatim, `ObjectCodec.java:
  1739-1742`; nested `decodeProxy` injects the original `[8]` content as `rawForm` on
  narrowing, `:2088-2094`). See §C.6.5, which excludes **every** `[8]` interior at any
  nesting level. *(v0.1 of this appendix asserted P2d "has no retention path in
  trunk"; that was a verification error, corrected here — §C.13.2 item 6.)*

P2 records carry **no digest field and no `payloadFormat`** in the current format; the
decoder computes the leaf digest from the received bytes
(`ObjectCodec.decodeNested`, `:1996-2006`).

### C.4.3 Non-sites: chains that are field *values* (NORMATIVE exclusion)

A `net.jini.io.MarshalledInstance` **object** travelling through the stream is an
ordinary `@AtomicSerial` value whose *payload* contains, among its fields, the MI's
captured `schemaBytes` — the chain of the class the MI wraps — as an `OCTET STRING`
field **value**. Those bytes are inside a field value and inside `MarshalledInstance`
identity. By rule 1 and §C.1.2 they MUST NOT be deduplicated, referenced, or used as a
reference-resolution target, even though they are recognisably chain bytes. The same
applies to any other value that happens to contain chain bytes (stored blobs, digests'
preimages, retained raw wire forms).

**Scope honesty (informative, quantified in §C.10.2).** For the Outrigger bulk-response
shape (`EntryRep` carrying `MarshalledInstance[] values`, one MI per entry field —
`EntryRep.java:80,523`), the duplication this appendix removes is the *structural*
layer: the `EntryRep` chain (×N) and the `MarshalledInstance` class's own chain (×N×F).
Site classification depends on how the response marshals: an `EntryRep` written as its
own top-level `writeObject` call is a **P1a** site, while EntryReps riding inside a
containing record or array are **P2** sites, and the per-field MIs are **P2c** array
elements in both cases — the dedup rule is identical either way (board fix 8c
wording correction). The *inner* captured chains (of the wrapped field classes, inside each
MI's `schemaBytes` field value) are values and are **not** removed. Per the Outrigger
SOW's seat-3 verification, scalar/String/enum/byte[] field values take the empty-schema
path, so for typical entries the inner chains are empty and the structural layer is
essentially the whole duplication; for `@AtomicSerial`-valued fields an inner-chain
residue remains, and it belongs to EntryRep-v2/C5, not to this appendix (§C.12).

### C.4.4 [FLAG] — inventory corrections to the SOW

The SOW's §1/§2 describe the duplication entirely in terms of `MarshalledInstanceRecord`
`schemaBytes`. Verified against trunk: (i) the **P2 nested-record production is a
second, digest-field-free chain carrier** the SOW does not name, and it — not P1 —
hosts the N×F bulk of the Outrigger duplication (nested MIs and entries are P2 sites;
P1a occurs once per top-level `writeObject`); a dedup layer covering only P1 would miss
most of the measured redundancy. (ii) The SOW's line citations were verified accurate
(`DerObjectStreamCodec.java:410` item contract, `:1158-1162` encode;
`MarshalledInstanceRecord.java:371-382` digest verification; `SchemaGenerator.java:
194-203` `ClassValue` cache). This appendix covers P1 and P2 uniformly.

---

## C.5 The Reference-Form Wire Encoding (NORMATIVE)

### C.5.1 `SchemaChainRef`

In the STD-006 house style (§4.6: module `EXPLICIT TAGS`, every context tag written
with its disposition at the use site):

```asn1
-- Appendix C additions to the STD-006 module set (DEFINITIONS EXPLICIT TAGS)

SchemaChainRef ::= CHOICE {
    -- Canonical full form: the complete leaf-first concatenated
    -- AtomicSerialSchemaRecord chain, byte-identical to the sec. 7.8
    -- "Schema chain encoding" bytes for the same class.  Bounded by
    -- maxChainBytes / maxChainRecords (sec. C.8).
    fullChain [0] IMPLICIT OCTET STRING (SIZE(1..maxChainBytes)),
    -- Reference form: SHA-256(DER(leaf AtomicSerialSchemaRecord)) of a chain
    -- already carried as fullChain EARLIER in this same stream's traversal
    -- order (sec. C.6).  Exactly 32 bytes.
    chainRef  [1] IMPLICIT OCTET STRING (SIZE(32))
}
```

Both arms are `IMPLICIT` context tags over `OCTET STRING` — a primitive, non-`CHOICE`
type, so X.680 §31.2.7 does not bite; the context tag is the sole discriminator (one
discriminator, H3). Tag bytes on the wire: `fullChain` = `0x80`, `chainRef` = `0x81`.

Decode rules (fail-secure, principle 6 — reject, never skip or default):

- Any tag other than `[0]`/`[1]` at a `SchemaChainRef` position MUST be rejected.
- A `chainRef` whose content length is not exactly 32 MUST be rejected.
- A `fullChain` whose content is empty, exceeds `maxChainBytes`, does not parse as one
  or more complete `AtomicSerialSchemaRecord` SEQUENCEs with no trailing bytes, or
  fails the §7.8 `parentSchemaHash` cross-check MUST be rejected.
- **Chain completeness (truncated-chain reject).** A `fullChain` whose **last record
  carries a `parentSchemaHash`** MUST be rejected: a complete chain always terminates
  in a root record with no parent hash (§7.8 — the root's parent is `Object`), so a
  dangling terminal hash means the chain was truncated. This rule is load-bearing for
  the whole appendix: without it, a truncated `{leaf}` and the genuine
  `{leaf, parent, root}` share one chain identity with different bytes, and a hostile
  first occurrence could bind the stream's table to the truncated bytes — the genuine
  chain would then reject as a duplicate full form (§C.6.4) and every reference would
  reconstitute non-canonical bytes, breaking the §C.5.4 round-trip law and feeding
  §C.7.6/C5 interning under a self-consistent-only digest (G12). With the rule
  enforced, chain identity → chain bytes is injective (§C.2) and first-occurrence
  binding is collision-free. *(Base-standard gap: trunk
  `MarshalledInstanceRecord.decodeSchemaChain`, `:306-343`, cross-checks adjacent
  pairs only and accepts a dangling terminal hash — see the §C.8.1 [PATCH].)*
- Constructed (`0xA0`/`0xA1`) encodings of either arm MUST be rejected (DER primitive
  encoding only), and all content within a `fullChain` MUST be strict canonical DER —
  a non-minimal length, non-canonical inner encoding, or any BER-ism inside a chain
  record MUST be rejected (see the digest-preimage rule, §C.7.3).

### C.5.2 The stream mode marker

A marked stream begins with the **dedup mode marker**: a context-specific primitive TLV,
tag `[15]` (tag byte `0x8F`), content exactly one octet `0x01` (mode version 1). Wire
bytes: `8F 01 01`.

- The marker MUST be the first TLV of the stream — before any object item or positional
  primitive. A marker anywhere else in a stream MUST be rejected. A second marker MUST
  be rejected.
- A marker whose content length is not 1, or whose content octet is not `0x01`, MUST be
  rejected (unknown mode version — a future revision that changes the grammar assigns
  `0x02`, it does not reuse `0x01`).
- A dedup-**enabled** receiver (an endpoint whose export configuration enables dedup,
  §C.9.2) MUST accept both marked and unmarked streams. A dedup-capable but
  **config-off** receiver MUST reject a marked stream exactly as a pre-dedup receiver
  does — loudly, at the marker (§C.9.4: the export configuration gates acceptance as
  well as emission). In an unmarked stream the pre-dedup grammar applies exactly; a
  `SchemaChainRef` arm tag or dedup-mode record shape appearing in an unmarked stream
  is malformed input and MUST be rejected (it will be, by the existing grammar's own
  checks).
- A pre-dedup receiver meeting `0x8F` fails loudly by existing construction: no
  pre-dedup production admits context tag `[15]` — `readObject`'s catch-all rejects it
  by name, and every positional typed read rejects it as a tag mismatch (verified
  against `DerObjectStreamCodec.readObject`'s final reject and `DerReader`'s
  universal-tag checks). See the negotiation matrix, §C.9.5.

**[PROPOSED]** Tag number 15 — the next free value after the object-stream item tags
`[0]`–`[14]` (STD-008 §15.2/§15.2.1). **[OPEN → T2]** confirm against STD-008's tag
registry at merge time that `[15]` remains unallocated, and record the allocation there.

### C.5.3 Dedup-mode record shapes

In a **marked stream**, the two chain-carrying productions are replaced as follows —
this is a whole-grammar switch, not a per-record option (§C.6.1):

```asn1
-- Replaces the sec. 7.8 MarshalledInstanceRecord at P1 sites in marked streams.
DedupMarshalledInstanceRecord ::= SEQUENCE {
    payloadBytes  OCTET STRING,      -- unchanged: sec. 7.8 hierarchy payload,
                                     -- except interior P2 sites use
                                     -- DedupNestedAtomicRecord (recursive grammar)
    schema        SchemaChainRef,
    payloadFormat UTF8String         -- unchanged: "JGDMS-STD-006/ATOMIC-DER"
}

-- Replaces the STD-008 sec. 16 nested record at P2 sites in marked streams.
DedupNestedAtomicRecord ::= SEQUENCE {
    schema        SchemaChainRef,    -- chain-first, matching the sec. 16 field order
    payloadBytes  OCTET STRING
}
```

**The `schemaDigest` field is deliberately absent** from
`DedupMarshalledInstanceRecord` **[PROPOSED]**. Rationale: in the reference arm the
digest *is* the content (`chainRef`); in the full arm the receiver must recompute the
leaf digest from the received bytes anyway (that recomputation is the §7.8-mandated
verification, and the result is the table key). Keeping the field would restate a
derivable fact in a second place that can disagree with the first — exactly the
restate-and-drift hazard G4/H3 exist to remove, and removing it makes a lying digest
*unrepresentable* rather than checked-for (G7: construction beats vigilance). The §12.4
fast-path comparison against the local `serialForm()` digest is unaffected: it runs
against the *computed* leaf digest, which is available in both arms at the same point
in decode. `payloadFormat` is retained unchanged: it is identity-bearing state
(reconstituted into the MI layer) and its value MUST remain exactly
`"JGDMS-STD-006/ATOMIC-DER"` — dedup is stream framing, not a payload format
(§C.9.2 rationale).

Field-count enforcement mirrors §7.8: a `DedupMarshalledInstanceRecord` is exactly
three fields and a `DedupNestedAtomicRecord` exactly two; trailing content MUST be
rejected.

**Everything else in the stream grammar is unchanged, octet for octet**: item tags
`[0]`–`[14]`, positional primitives, payload interiors (except interior chain sites),
enum/array/proxy/collection encodings, `Any` arms, canonical scalar forms. Rule 2 of
§C.3.1 is enforced by this sentence: a conformance vector MUST be able to diff a marked
and unmarked encoding of the same record sequence and find differences *only* at the
marker, at P1 field-set changes, and at chain sites.

### C.5.4 Canonicality and the round-trip law

- **One encoding per stream mode (G1).** For a given record sequence and stream mode,
  there is exactly one conformant byte stream. In a marked stream the full/reference
  choice at every site is forced by §C.6 (no encoder discretion, no size thresholds, no
  heuristics); in an unmarked stream the pre-dedup encoding applies unchanged. Same
  record sequence ⇒ same stream bytes, on every conformant implementation.
- **Decoders reject non-canonical dedup streams** (G1's decode half): a `fullChain`
  whose digest is already in the table (a duplicate full form where the pinned rule
  requires a reference) MUST be rejected, exactly as a `chainRef` with no table entry
  MUST be rejected (§C.6.4, §C.7.4). Tolerating either would create two accepted
  encodings of one stream.
- **The round-trip law.** Reconstitution is byte-exact: for every decoded record, the
  chain bytes the decoder delivers upward (into `SchemaChain.Result`, `GetArg`
  machinery, MI state, retained raw forms, or any re-encode) MUST be byte-identical to
  the sender's canonical full form — i.e. to the bytes `SchemaGenerator` +
  §7.8/STD-008 §16 would produce for the same class. Consequently, re-encoding a
  decoded record in a non-dedup context MUST reproduce the canonical full-form bytes
  byte-for-byte, and any digest, signature, byte-comparison, or persistence operating
  above the transport is provably unaffected by which arm carried the chain.
  Reconstituting the P1 §7.8 four-field record inserts the computed leaf digest as
  `schemaDigest` — deterministically identical to the field the sender omitted.

### C.5.5 Worked example (informative — illustrative, not machine-verified)

Byte values below are structural sketches for the reviewer's orientation; the
authoritative vectors are T3/T5 outputs generated from the built codec (a hand-derived
hex transcript is deliberately *not* presented as verified — G13's discipline; T5's
corpus is the known-good).

A marked response stream carrying two top-level `@AtomicSerial` objects of the same
class `com.example.Foo` (chain digest `D`):

```
8F 01 01                          -- mode marker, version 1
A1 <len>                          -- [1] CTX_ATOMIC item 1
  30 <len>                        --   DedupMarshalledInstanceRecord
    04 <len> <payload bytes>      --     payloadBytes (verbatim value octets)
    80 <len> <chain bytes>        --     schema: fullChain (first occurrence of D)
    0C 18 "JGDMS-STD-006/..."     --     payloadFormat
A1 <len>                          -- [1] CTX_ATOMIC item 2, same class
  30 <len>
    04 <len> <payload bytes>
    81 20 <D: 32 bytes>           --     schema: chainRef (subsequent occurrence)
    0C 18 "JGDMS-STD-006/..."
```

The same two objects in an unmarked stream carry two identical full `schemaBytes`
octet strings and two `schemaDigest` fields, and no marker — and MUST be byte-identical
to the pre-dedup format.

---

## C.6 The Pinned Reference Rule (NORMATIVE)

### C.6.1 The rule

Within a marked stream, considering every chain site in the normative traversal order
of §C.6.2:

- the **first occurrence** of a given chain identity MUST be encoded as `fullChain`;
- **every subsequent occurrence** of that chain identity MUST be encoded as `chainRef`
  carrying that identity.

No encoder discretion exists: no size threshold (a one-record chain of 40 bytes is
still referenced on repeat), no per-record opt-out, no heuristics, no
"dedup-if-beneficial". The rule is total over chain sites and deterministic, which is
what makes stream bytes a function of the record sequence (G1) and forecloses covert
encoder channels (an encoder that could choose full-vs-reference per site could signal
≈1 bit per repeated site to a confederate observer; the pinned rule removes the
choice).

### C.6.2 Normative traversal order

"First occurrence in stream order" is defined over the **record-entry (pre-order)
traversal** of the stream, not over raw byte positions:

1. Top-level stream items are ordered as written (`writeObject`/positional call order —
   the order their TLVs appear in the stream).
2. Within a record (P1 or P2), the record's **own chain site precedes every chain site
   in its payload**.
3. Chain sites within a payload are ordered by payload encode order: per-class
   `SEQUENCE`s root-first (§7.8 hierarchy layout), fields within a class in schema
   field order, collection/array elements in wire element order, and interior
   structures (`Any` arms, nested proxies, nested records) depth-first at the position
   they occupy.

Equivalently: the order in which the **normative decode algorithm** (§C.7.3) processes
chain sites — each record's chain is processed when the record's chain field is
decoded, which happens before its payload is decoded.

**Why byte order is not the rule (informative but load-bearing for G9).** The two
productions disagree about where the chain sits in the record's bytes: P2 puts the
chain *before* the payload, but P1 puts `payloadBytes` *first*, so a P1 record's own
chain bytes appear physically *after* every nested chain inside its payload. Byte order
and processing order therefore diverge exactly at P1. The divergence is observable for
a self-recursive class — the degenerate case that forces the definition (G11): consider
`@AtomicSerial class Node { Node next; }` and a top-level `Node` whose `next` is a
`Node` (the graph is acyclic, §3.7; the *type* recurses, the values do not). The outer
record's chain site and the nested record's chain site carry the same chain identity.
Under this section's rule the **outer** site is first (rule 2) and MUST be `fullChain`;
the **nested** site MUST be `chainRef` — even though the nested site's bytes appear
earlier in the stream than the outer chain's bytes. A decoder following §C.7.3
processes the outer chain first and has the table entry ready when it reaches the
nested reference. Under a byte-order rule the same stream would be undecodable in one
pass (the outer reference would point at a full form the decoder has not yet parsed).
Conformance case §C.11.3(12) pins this.

Encoders MUST implement the rule in this order regardless of internal construction
order. (Implementation note, non-normative: a bottom-up encoder — the current
`ObjectCodec` shape, which encodes payloads before assembling enclosing records —
satisfies the rule by registering the enclosing record's chain identity in the encode
table *when it begins encoding the record*, before encoding the payload, so interior
sites of the same identity resolve to references.)

**Buffering consequence for a one-pass streaming decoder (normative consequence,
stated for the peer implementation).** Because a P1 record's `schema` field physically
*follows* its `payloadBytes`, a strictly streaming decoder cannot resolve the payload's
interior chain sites as their bytes arrive: interior references resolve against a table
state that includes the enclosing record's own chain, whose bytes arrive later. A
conformant decoder therefore processes each P1 record TLV as a unit — buffer the
record's fields (the payload octets held unparsed), process the record's own chain
site, *then* parse the payload interior. This is bounded (the record is within the
stream's `maxInputBytes` cap and the trunk decoder already buffers whole record TLVs)
and it is the only decode order under which §C.6.2 is satisfiable in one pass; a Rust
peer implementing from this document alone MUST NOT attempt fully-incremental interior
decode ahead of the enclosing chain field.

### C.6.3 Occurrence granularity

An occurrence is a chain **site**, not a class mention: every P1a/P2a–P2d position
emits exactly one `SchemaChainRef`, and nested records inside a payload are occurrences
in their own right. The chain identity at a site is the leaf digest of the chain for
the *runtime class being encoded at that site* (the same class the pre-dedup format
would have emitted a chain for) — dedup never changes which class's chain a site
carries, only whether it travels in full.

### C.6.4 Decoder enforcement of the rule (G1 decode half)

A conformant decoder MUST maintain the same traversal order and MUST reject:

- a `chainRef` whose digest is not in the table at the moment the site is processed
  (**unknown-digest reject** — never a fetch, never a fallback to a local or registry
  schema, never a skip; §12.4's no-fallback rule applies);
- a `fullChain` whose computed leaf digest **is** already in the table (**duplicate
  full form** — the canonical stream would have used a reference; accepting it would
  admit a second encoding and a rebinding surface);
- any site whose `SchemaChainRef` is malformed per §C.5.1.

A rejection at any site is a decode failure of the whole stream (no partial tolerance,
no object constructed from the failing item — principle 6).

### C.6.5 Exclusion: every `[8]` proxy interior, at any nesting level [PROPOSED]

**The rule (one rule, byte-region-scoped, transitive).** The entire content of every
`[8]` `CTX_PROXY` TLV — the top-level object-stream item (P1b) **and** the nested
field-level proxy record (P2d), at any depth — is **outside the dedup layer**, in both
directions:

- No chain site anywhere inside a `[8]` TLV's content participates in dedup: the
  handler's own record, and transitively every P1/P2-shaped record inside the
  handler's `@AtomicSerial` field payloads, MUST be encoded with the canonical
  pre-dedup grammar (full §7.8 / STD-008 §16 forms) even in a marked stream.
- No chain inside a `[8]` interior is entered into the dedup table, counted as an
  occurrence, or usable as a reference-resolution target; no `chainRef` may appear
  inside a `[8]` interior. The exclusion is a property of the **byte region** (the
  `[8]` TLV content), not of any particular record within it.

Consequently P1b and P2d are **not chain sites** (§C.4.1, §C.4.2), and within a marked
stream every byte region has exactly one grammar: dedup grammar outside `[8]`
interiors, canonical pre-dedup grammar inside them — no site has two encodings (G1
preserved).

**Rationale.** `[8]` items are subject to the raw-wire-form retention contract
(`RawWireFormRetaining` / `ProxyWireSupport.wireContentForBoomerang`) **at both
levels** — verified against trunk: the object-stream write site
(`DerObjectStreamCodec.writeObject`, retained-branch-first) *and* the nested field
write site (`ObjectCodec.encodeProxy`, `ObjectCodec.java:1739-1742`) re-emit retained
original `[8]` content bytes **verbatim, byte-for-byte** on re-forward, and both decode
sites inject the original `[8]` content as the handler's `rawForm` when interface
narrowing occurs (`DerObjectStreamCodec.readObject`; nested `decodeProxy`,
`ObjectCodec.java:2088-2094`). Retained bytes containing a stream-scoped `chainRef`
would dangle in every later stream (references are meaningless outside their stream,
§C.7.5), and rewriting retained bytes would break the verbatim-relay integrity
contract. Transitivity is forced by the same mechanism: retention captures the *whole*
`[8]` content region, so a reference anywhere inside it — however deep — poisons a
later relay. Excluding the region wholesale keeps both invariants true by construction
(G7) and gives the exclusion a definition a peer can implement without knowing which
handler fields exist (G9).

**[FLAG — self-correction, board round 1 fix 1 (both seats, HIGH, CONFIRMED).]** v0.1
of this appendix asserted that nested proxy records "have no retention path in trunk"
and admitted P2d as an ordinary chain site. That claim was **false**:
`ObjectCodec.encodeProxy` consults retention first and `decodeProxy` injects on
narrowing (citations above; `ProxyWireSupport`'s documentation names both write
sites). The error is recorded in §C.13.2 per this document's own G3 standard.

**Cost annotation (workload-dependent — measured by T4, not asserted).** The exclusion
leaves `[8]`-interior chains undeduplicated, so a bulk response carrying a *bare* proxy
per item would repeat handler chains at full size. This residue is expected to be
immaterial because most smart proxies ride the dedupable `[1]` carrier path
(`DerProxySerializer` substitution on the invocation arg/return streams), leaving bare
`[8]` items rare and per-proxy rather than per-entry — but that is a workload
expectation, not a measurement; T4's wire-size probe reports the actual `[8]`-interior
residue alongside its three-way comparison.

**[OPEN → T2/T3]** T2 MUST implement the region exclusion at both write/read sites;
T3's corpus carries the §C.11.3(15)–(16) boomerang-relay and transitivity vectors.

---

## C.7 Per-Stream Table Semantics (NORMATIVE)

### C.7.1 Stream scope — one codec lifetime [FLAG on the SOW's parenthetical]

The dedup table is strictly **per stream**, where a stream is one `DerObjectStreamCodec`
lifetime (§C.2): the argument stream of one call, the return stream of one call, or one
standalone encode/decode unit. Each direction of each call is a distinct stream with a
distinct table; the request and response streams of the same call share nothing.

**[FLAG]** The SOW (§3.3) writes "per-stream (**per-connection**), strictly". Verified
against trunk: the codec has no connection-scoped state and no access to any — a fresh
`DerObjectStreamCodec` is constructed per marshal stream
(`AtomicDerInvocationHandler.createMarshalOutputStream` / `createMarshalInputStream`,
`AtomicDerInvocationDispatcher` likewise, and `DerReducingContextCodec` constructs its
own for the context channel). A per-connection table would *widen* sharing across the
calls multiplexed on a connection — more state, a cross-call information channel of
exactly the kind §3.3's own rationale rejects for cross-stream tables, and new plumbing.
This appendix pins the narrower, code-true scope; the SOW's parenthetical is treated as
imprecise shorthand, not a requirement. (The measured redundancy is intra-stream — a
bulk response's N×F repetition sits inside one return stream — so nothing material is
lost by the narrower scope; cross-call repetition of first occurrences is the price of
zero cross-call coupling, and the chain-bytes-caching rider makes those first
occurrences cheap to produce, §C.12.3.)

### C.7.2 Lifecycle

- The table is created at stream open (encoder: first byte written; decoder: marker
  processed) and MUST be discarded at stream close — including abandoned/failed
  streams. It MUST NOT be global, MUST NOT be shared between streams (not even two
  streams of one call, one connection, or one thread), MUST NOT be persisted, and MUST
  NOT be pre-seeded from any source (no "well-known chains" dictionary — a pre-seeded
  entry is a cross-stream channel and a poisoning surface, and it breaks the
  first-occurrence-is-full invariant that makes streams self-contained).
- An unmarked stream has no table and no dedup state of any kind.

### C.7.3 Normative decode algorithm (verify once, then look up)

For each chain site, in traversal order (§C.6.2):

1. **`fullChain` arm:** parse the chain bytes into `AtomicSerialSchemaRecord`s
   (rejecting on any parse failure, any non-canonical encoding, or trailing bytes),
   enforce §C.8 ceilings *during* parsing (§C.8.2), run the §7.8 `parentSchemaHash`
   cross-check over **all adjacent record pairs** plus the §C.5.1 **completeness
   check** (terminal record MUST NOT carry `parentSchemaHash`), and compute the chain
   identity. Reject if the identity is already in the table (§C.6.4). Otherwise store
   `identity → chain bytes` and proceed, using the parsed chain for this record's
   decode. The chain is thus digest-verified **exactly once** per distinct chain per
   stream.

   **Digest preimage (pinned).** The chain identity is `SHA-256` computed over the
   **received leaf-record bytes exactly as received** — the first complete SEQUENCE
   TLV of the `fullChain` content — never over a re-encode of the parsed structure.
   The two coincide only because decoders MUST reject any non-canonical DER inside
   `fullChain` content (non-minimal lengths, non-canonical scalar forms — the Java
   `DerReader` already enforces this; a peer implementing from this document alone
   MUST replicate it, G9): with strict-DER enforcement, received bytes *are* the
   canonical bytes, so hash-received and hash-reencoded cannot diverge and no
   "same identity, different accepted bytes" pair is representable. A decoder that
   hashed a re-encode while tolerating non-canonical input would silently launder
   non-canonical bytes into the table — the construction this pin forecloses.

   *(Marked/unmarked validation asymmetry, [FLAG] — base-standard gap found in this
   review round: the base P2 decode path, `ObjectCodec.decodeNested`
   `:1996-2006`, parses chain records but **skips the `parentSchemaHash` Merkle
   cross-check** that the base P1 path runs — despite its "same format as
   MarshalledInstanceRecord.decodeSchemaChain" comment. This appendix's algorithm
   cross-checks uniformly at every full arm, P1 and P2 alike, so until the base gap
   is fixed a marked stream validates P2 chains* more *strictly than an unmarked
   stream — a direct tension with §C.11.1(3)'s mode-transparency claim for
   malformed-chain inputs. See the §C.8.1 [PATCH], which recommends adding the
   cross-check (and the completeness check) to base P2 decode.)*
2. **`chainRef` arm:** look the digest up in the table. Absent ⇒ reject (§C.6.4).
   Present ⇒ use the stored, already-verified chain for this record's decode — no
   re-hash, no re-parse, no re-cross-check. The receiver's hashing cost is
   O(distinct chains), not O(occurrences).

The computed identity then serves the unchanged §7.8/§12.4 steps (comparison against
the local `serialForm()` digest for the fast path; embedded schema authoritative for
`GetArg` population on mismatch). All existing decode gates — admission of the
wire-named leaf against the declared slot type, `check(GetArg)`, nesting depth,
`DerInputLimits` — run unchanged on the reconstituted chain.

### C.7.4 Fail-closed resolution (restated as the security spine)

A reference resolves **only** to a chain already received *and verified* in the same
stream. On unknown digest, malformed arm, ceiling breach, cross-check failure, or
duplicate full form: hard decode reject of the stream. There is no fetch (no registry,
no `SchemaAccessor`, no local `serialForm()` substitution), no fallback, no skip, no
partially-decoded object. (Fetching would convert a wire reference into a server-side
side effect and a confused-deputy surface; local substitution would let a sender route
a payload onto a schema it never transmitted — the §7.8 verification exists to prevent
exactly that.)

### C.7.5 Cross-stream isolation

A `chainRef` is meaningful only within the stream that carried its `fullChain`. A
digest valid in stream A MUST be treated in stream B exactly as any unknown digest
(reject) unless B carried its own `fullChain` first. Implementations MUST NOT observe
another stream's table for any purpose, including "optimisations" (a cross-stream read
is a cross-principal information channel: whether some other connection already sent a
schema). Conformance case §C.11.3(10).

### C.7.6 Reconstitution sharing (heap note)

On reconstitution, receivers SHOULD share one interned `byte[]` (or equivalent
immutable buffer) per distinct chain across all records decoded from the stream that
referenced it — the wire dedup hands the C5 heap-half sharing over for free on the
receive path. This is a SHOULD, not a MUST: it is observable only in memory footprint,
never in bytes. (Implementation note for T2: `MarshalledInstanceRecord`'s constructor
defensively copies its arrays; an interning-aware path is needed if the shared buffer
is to survive into MI state. Observable byte-identity is the normative requirement
either way.)

The **encoder-side** table stores, per chain identity, at most the encoded chain bytes
(or nothing but the identity, when the chain-bytes cache of §C.12.3 serves the bytes);
its size is bounded by the same §C.8 ceilings, enforced as encode failures.

---

## C.8 Ceilings [PROPOSED — values for ratification]

### C.8.1 The table (STD-006 §4.5 style)

Design principle 5 (bounded before allocation) applied to the new accumulation surface
this appendix introduces — the dedup table — plus the previously-unbounded chain
dimensions it makes load-bearing. All bounds are **inclusive** (the §4.5 `maxCollection`
convention): exactly the ceiling is accepted; ceiling + 1 is rejected. Every conformant
encoder and decoder, JVM and non-JVM, enforces them identically.

| Constant | Value | Applies to |
|---|---|---|
| `maxDistinctChainsPerStream` | 256 | Distinct chain identities entered into one stream's dedup table (= number of `fullChain` occurrences in a marked stream). The 257th distinct `fullChain` is rejected at insertion. |
| `maxChainRecords` | 64 | `AtomicSerialSchemaRecord` count in one chain (`fullChain` content). Chains are class hierarchies; real JVM hierarchies are ≤ ~10 deep — 64 mirrors `maxCauseDepth`'s generosity without admitting pathological record floods. |
| `maxChainBytes` | 65536 | Byte length of one `fullChain` content (`SIZE(1..maxChainBytes)`, §C.5.1). Typical chains measure hundreds of bytes (§C.10.2); 64 KiB accommodates `maxChainRecords` records of unusual width while capping single-chain allocation. |
| `maxDedupTableBytes` | 1048576 | Sum of stored chain-byte lengths in one stream's table. Binds before `maxDistinctChainsPerStream × maxChainBytes` (16 MiB) can be reached; 1 MiB of *distinct* schema text in one stream is far beyond any legitimate workload. |

```asn1
maxDistinctChainsPerStream INTEGER ::= 256
maxChainRecords            INTEGER ::= 64
maxChainBytes              INTEGER ::= 65536
maxDedupTableBytes         INTEGER ::= 1048576
```

Existing bounds continue to apply unchanged and are not restated as new obligations:
per-record `className` `SIZE(1..1024)`, `maxFields` 65535 (§4.5), the stream-level
`maxInputBytes` / nesting caps (`DerInputLimits`, STD-008), and `MAX_NESTING` for
nested records.

**Precedence over base §4.5 maxima [PROPOSED — board fix 4].** `maxChainBytes` = 64 KiB
is intentionally far below the theoretical maximum a single base-admissible record can
reach (`maxFields` = 65535 fields of up to ~1.3 KB each, `className` ≤ 1024 B): a class
can exist that is encodable in an **unmarked** stream but exceeds the marked-stream
chain ceilings. The pinned resolution: **the marked-stream profile deliberately
tightens the admissible class set**, and the failure mode is a **loud encode-time
error** (the §C.8.2 encoder obligation — never a silent fallback to full form or to an
unmarked stream) and a decode-time reject on receipt. Rationale: no sane class
approaches these bounds (a 64 KiB *schema* is a generated-code pathology, not a
design), DER is unreleased with no deployed peers to strand, and the alternative —
raising `maxChainBytes` to cover the §4.5-theoretical maximum (~85 MB) — would gut the
table ceilings' DoS value. Consequence for the corpus: the §C.8.2 boundary vectors are
constrained **jointly** — no vector may demand an accepted-at-`maxFields` record inside
an accepted chain, since `maxChainBytes` binds first; each ceiling's boundary pair is
built to satisfy every *other* ceiling with margin. The same tension arises if the base
standard adopts these ceilings ([PATCH] below); its resolution there (tighten §4.5's
own maxima vs. accept the narrowed set globally) belongs to the base-adoption pass
(tracked as T6 in the board fix list), cross-referenced in §C.13.3.

**[PATCH — STD-006 §7.8 / §4.5 / base decode paths]** On merge, recommend to the base
standard (flagged here, not enacted — this appendix's normative reach is marked
streams):

1. Add the four constants above to the §4.5 profile table. Independently of dedup:
   §4.5 currently declares **no ceiling on chain record count or chain byte length** —
   `schemaBytes` in the pre-dedup format is bounded only by the stream-level
   `maxInputBytes`. `maxChainRecords`/`maxChainBytes` are sound bounds for the
   pre-dedup chain parser too (`MarshalledInstanceRecord.decodeSchemaChain` loops
   until bytes are exhausted with no record-count cap — trunk-verified). Ceiling
   precedence per the paragraph above is part of the adoption decision (T6).
2. **Chain completeness in base decode (board fix 2):** `decodeSchemaChain`
   (`MarshalledInstanceRecord.java:306-343`) cross-checks adjacent pairs only and
   never rejects a *terminal* record carrying a dangling `parentSchemaHash` — a
   truncated chain decodes cleanly today. Add the §C.5.1 completeness check there,
   and add one clarifying sentence to §7.8's "Schema chain encoding" prose: *"the
   final record of a chain MUST NOT carry `parentSchemaHash`; a decoder MUST reject a
   chain whose last record does."*
3. **P2 Merkle cross-check in base decode (board fix 5):** `ObjectCodec.decodeNested`
   (`:1996-2006`) skips the `parentSchemaHash` cross-check the P1 path runs, despite
   its "same format" comment — nested chains are currently accepted with broken
   Merkle links in unmarked streams. Add the cross-check (and item 2's completeness
   check) to base P2 decode, which also removes the marked/unmarked validation
   asymmetry noted in §C.7.3.

### C.8.2 Metered during decode (G10 interior fence)

Every ceiling MUST be enforced **inside the loop that accumulates**, before the
allocation or insertion that would exceed it — never by post-hoc measurement of a
completed structure:

- `maxChainRecords` / `maxChainBytes`: checked as each record TLV of a `fullChain` is
  parsed (record count incremented per record; the byte bound is checkable from the
  `OCTET STRING` length header *before* content allocation, and MUST be).
- `maxDistinctChainsPerStream` / `maxDedupTableBytes`: checked at table insertion, at
  each `fullChain` site, as the stream is decoded — not at stream end. A stream that
  exceeds a table bound is rejected at the first violating site, having allocated at
  most the ceiling.
- Encoders MUST apply the same bounds as encode-time failures (an encoder that would
  emit a 257th distinct chain fails the encode; it MUST NOT silently fall back to
  emitting full forms past the cap — that would be a second encoding, §C.5.4).

Boundary-pair conformance obligations (the §10.3/STD-011 inclusive-fencepost
convention): for **each** of the four constants, the corpus carries an
accepted-at-exactly-the-limit case and a rejected-at-limit-plus-one case
(§C.11.3(8)). Boundary vectors are constructed **jointly** under the §C.8.1
precedence rule: each pair satisfies every ceiling other than the one it probes (in
particular, no vector demands an accepted record at `maxFields` inside an accepted
chain — `maxChainBytes` binds first; the `maxChainBytes` boundary case uses many
small records or one wide record within the byte bound).

### C.8.3 Honesty note (informative)

The dedup table is a **new** allocation surface: the pre-dedup format has no per-stream
accumulation keyed by stream content (each record is self-contained, bounded by
`maxInputBytes`). These ceilings fence that new surface; they do not claim to improve
the pre-dedup format's bounds (see the [PATCH] above for that separate recommendation).
Worst-case decoder memory attributable to dedup is `maxDedupTableBytes` + one in-flight
chain ≤ `maxChainBytes`, per live stream.

---

## C.9 Negotiation (NORMATIVE; decides SOW §6.3)

### C.9.1 What the existing machinery offers (surveyed before deciding)

Verified against STD-006 §5 and trunk:

- **`MarshallingFormat` (§5.1)** is a *policy assertion checked fail-fast* — a
  constraint naming a format identifier string, resolved before transmission; it is
  deliberately not a per-call negotiation. The format mechanism itself is fixed at
  export time by the `InvocationLayerFactory`.
- **There is no protocol version byte (§5.2)** — DER was introduced as a separate
  invocation layer, not a version discriminator, and that section explicitly withdraws
  the version-byte pattern. A "codec version bump" mechanism would have to be invented,
  against precedent.
- **The proxy carries its codec (§5.3).** A client's DER marshalling code is the
  `AtomicDer*` handler shipped (as authenticated mobile code) by the service's own
  export. Handler and dispatcher of one export are one codebase — the two ends of a
  proxied call run *matched* stream implementations except when a proxy is stale
  (obtained before a service upgrade/rollback).
- **`payloadFormat`** (`"JGDMS-STD-006/ATOMIC-DER"`) is per-record, identity-bearing MI
  state. Changing it to signal dedup would alter reconstituted identity bytes —
  disqualified immediately.

### C.9.2 Decision [PROPOSED]: export-affirmed capability + in-band stream marker + response echo

The least-new-surface design consistent with §5.1–§5.3:

1. **Capability is export-scoped** (the §5.1 shape): a service opts in through its
   `AtomicDerILFactory` configuration; the exported handler/dispatcher pair are both
   dedup-capable, and the proxy carries that capability to clients as it carries the
   codec itself. No new constraint class, no new wire identifier, no handshake round
   trip.
2. **Activation is per-stream and in-band**: a dedup stream declares itself with the
   §C.5.2 marker as its first TLV. The marker is the *only* wire-visible negotiation
   artifact.
3. **Direction rules:**
   - A sender MUST NOT emit a marked stream unless the receiver's support is
     affirmed. For the **request** direction, the affirmation is the proxy itself: a
     handler created by a dedup-enabled export marks its request streams (the
     dispatcher that will decode them is the same export's other half). A handler from
     a non-dedup export sends unmarked streams.
   - For the **response** direction, the affirmation is the request: a dispatcher MUST
     mark its response stream **iff the corresponding request stream was marked**
     (response-echoes-request). An unmarked request — from a pre-dedup client or a
     stale pre-upgrade proxy — always receives an unmarked, pre-dedup-byte-identical
     response.
   - Marking is content-independent: a dedup-mode sender marks every stream it is
     entitled to mark, even one containing no chain sites (a marked stream with zero
     occurrences is valid and degenerate-conformant, §C.11.3(11)); it never
     conditionally omits the marker. One rule, no discretion (G1).
4. **The export configuration gates both emission and acceptance [PROPOSED — board
   fix 3]**: a dedup-**enabled** endpoint marks the streams it is entitled to mark and
   MUST accept both marked and unmarked streams (§C.5.2); a **config-off** endpoint —
   even one running dedup-capable code — does not advertise, does not mark, and MUST
   **reject marked streams loudly** at the marker, identically to a pre-dedup binary.
   Rationale: accepting a marked stream is a **resource commitment** — a per-stream
   table of up to `maxDedupTableBytes` (1 MiB) plus verification state — and an
   operator who deliberately opts out must thereby refuse that commitment; a
   capability that cannot be turned off at the acceptance side is not an opt-in.
   Emission-side, **absence of dedup support falls back transparently to full form** —
   normative, explicit, and safe because the full form is always valid (§C.1.2
   item 4); it is not an emergent behaviour.

### C.9.3 Rejected alternatives (recorded rationale)

- **New `MarshallingFormat` identifier** (`"…;dedup"` or similar): couples a transport
  framing detail into the format-identity string that `payloadFormat`/MI state and
  §5.1's policy layer share; would force clients to *require* dedup to get it, or
  services to dual-export per identifier. Heavier surface, and misuses a policy
  mechanism as a capability mechanism (the constraint layer is decode *policy*, §5.1's
  own distinction).
- **New `InvocationConstraint` capability advertisement**: per-call constraint
  negotiation is exactly what §5.1 says the format machinery is *not*; it adds a
  constraint class to `net.jini.core.constraint` (public API surface, serialized
  everywhere) to express something the proxy already knows statically.
- **Codec/stream version byte**: withdrawn pattern per §5.2; and a bare version byte is
  strictly weaker than the marker (same wire cost, but positioned as a permanent
  header rather than an opt-in mode signal).
- **Per-export static enablement without a marker**: broken under stale proxies — a
  pre-upgrade handler would receive dedup responses it cannot decode, and the failure
  (unknown tag mid-stream) would be loud but avoidable. The echo rule converts that
  cell into a valid transparent-fallback cell.

### C.9.4 Absence-of-support disposition (normative statement)

Three endpoint states exist and the first two are wire-indistinguishable by design:
**pre-dedup binary** (no dedup code), **capable-but-config-off** (dedup code present,
export configuration off), and **enabled**. v0.1 of this appendix conflated the first
two ([FLAG] — board fix 3): it required any *capable* receiver to accept marked
streams, which would have made opting out of the per-stream table commitment
impossible. The pinned resolution: **config-off behaves as pre-dedup on the wire in
both directions** — sends unmarked, rejects marked at the marker.

For an **enabled receiver**, an unmarked stream is not an error, not a warning, and
not an inferior mode to be upgraded: it is the pre-dedup format, accepted
byte-for-byte unchanged, forever (the coexistence guarantee — pre-dedup senders never
break). For an **enabled sender**, absence of peer affirmation means unmarked
full-form emission — a valid, silent, *correct* outcome, explicitly not a "silent
downgrade" in §3.4's prohibited sense because full form is not a degraded security
mode: it is the canonical encoding itself. What MUST be loud is the true mismatch:
a marked stream reaching any endpoint — pre-dedup or config-off — that has not
affirmed acceptance. **Explicit consequence of acceptance-gating: the
stale-proxy-after-rollback cell is loud.** A client holding an enabled-export proxy
whose service has since rolled back (to a pre-dedup binary *or* to config-off) sends a
marked request and receives an immediate, identifiable reject at the marker — not a
transparent fallback. This is the deliberate price of refusable resource commitment;
the client's remedy is re-fetching the proxy (the normal stale-proxy remedy), and the
failure is fail-secure, first-TLV, before any table allocation on either side.

### C.9.5 The negotiation matrix (every cell loud-or-valid, never silent-wrong)

Sender columns collapse to two (pre-dedup and config-off both send unmarked); receiver
rows are the three states. Each cell covers both stream directions via the direction
rules (request per proxy affirmation, response per echo).

| Receiver ↓ / Sender → | **Unmarked sender** (pre-dedup binary *or* config-off) | **Marked sender** (enabled export) |
|---|---|---|
| **Pre-dedup binary** | Unmarked streams both ways. **Valid** (pre-dedup format, unchanged). | Skew case (stale new proxy vs rolled-back service, or misconfiguration). First TLV is `0x8F`, which **no pre-dedup production admits** — `readObject`'s tag catch-all or the positional readers' universal-tag checks reject it immediately, before any object is constructed. **Loud, fail-secure, never a misdecode.** |
| **Capable, config-off** | Unmarked streams both ways. **Valid.** | Same skew case. The receiver MUST reject at the marker (§C.9.2 item 4) — behaviourally identical to the pre-dedup row, by rule rather than by ignorance; the endpoint thereby refuses the table commitment it opted out of. **Loud.** |
| **Enabled** | Receiver accepts the unmarked request and — echo rule — responds unmarked. **Valid, transparent fallback.** Covers the stale *old* proxy against an upgraded service. | Marked streams both ways. **Valid** (dedup mode). |

A pre-dedup or config-off *client* can never receive a marked response: it can only
have sent an unmarked request, and no conformant enabled dispatcher marks a response
to an unmarked request — the echo rule forecloses those cells. There is no cell in
which a receiver silently misinterprets bytes: the marker tag and the `SchemaChainRef`
arm tags are outside every pre-dedup grammar, dedup-mode productions are gated behind
the marker for enabled receivers, and config-off receivers reject the marker by rule.
Conformance vectors cover all six cells, including the config-off row (§C.11.3(13)).

### C.9.6 Scope of streams covered

These rules apply to every DER object stream a dedup-configured endpoint pair creates,
including the invocation arg/return streams and — if T2 wires it — the
`DerReducingContextCodec` context streams (each is its own stream with its own table;
the marker/echo rules apply per stream). **[OPEN → T2]** whether the context-channel
streams enable dedup in the first increment (their chain traffic is small; leaving them
unmarked is conformant — an unmarked stream is always valid) — but whichever choice T2
makes MUST be deterministic per endpoint configuration, not per-stream discretionary.
Standalone MI capture streams are excluded categorically (§C.1.2 item 3).

---

## C.10 Reference Granularity Decision (SOW §6.2) — Whole-Chain Only

### C.10.1 The alternatives

- **Whole-chain (decided):** the unit of reference is the entire leaf-first chain,
  keyed by leaf digest. One table, one lookup per site, references are 34 bytes.
- **Per-record links (rejected):** additionally intern individual
  `AtomicSerialSchemaRecord`s (keyed by per-record digest), so a subclass chain could
  reference its parent records even when the whole-chain identity differs — sharing
  parent records across *sibling* classes' chains.

### C.10.2 Sharing analysis (basis: estimated from code — no live capture available; T4/U1c measure)

Record-size model, from the `AtomicSerialSchemaRecord`/`AtomicSerialFieldDef` grammar
(§7.8) and trunk encoders: a field def costs ≈ 6 bytes of TLV overhead + name + wire
type (typical 30–60 B); a record costs ≈ 40 B fixed (headers, 34-B parent hash) +
className (typical 25–60 B) + fields. A representative 5–6-field class record ≈
250–350 B; `net.jini.io.MarshalledInstance`'s own single-record chain (4 fields, short
names) ≈ 150–200 B; a 3-record chain ≈ 700–1000 B.

**Scenario A — the SOW's bulk-response shape** (100 `EntryRep` × 5 field-MIs,
scalar-valued fields): structural chains = 100 × EntryRep-chain (≈ 350 B) + 500 ×
MI-chain (≈ 180 B) ≈ **125 KB** of chain bytes per response stream — independently
reconstructing the SOW's 100–150 KB estimate from source, which corroborates the
ground-truth claim. Whole-chain dedup: 2 full chains (≈ 530 B) + 598 references
(≈ 34 B each ≈ 20.3 KB) ≈ **21 KB** — roughly an 83% reduction of chain bytes, ≈ 99% of
the theoretical maximum (the references themselves are the floor). Per-record links
change nothing here: the two distinct chains share no records.

**Scenario B — sibling entry classes:** 3 entry classes sharing a common
`AbstractEntry`-style parent record (≈ 200 B), mixed in one stream. Whole-chain stores
3 chains whose parent record is duplicated in the table: extra cost vs per-record ≈
2 × 200 = **400 B once per stream**. Per-record linking would recover those 400 B but
add per-record reference overhead (≈ 34 B × every record of every first-occurrence
chain) — for 3 × 2-record chains, ≈ 200 B back — net saving ≈ **200 B per stream**,
against Scenario A's 104 KB: **≈ 0.2%**.

**Scenario C — deep hierarchies:** a 6-level hierarchy with 4 sibling leaves sharing 5
ancestor records (≈ 1.2 KB shared): per-record saves ≈ 3 × 1.2 KB − overhead ≈ 3 KB per
stream — material only if such families dominate a stream's *distinct*-chain
population, which no surveyed JGDMS workload shape (Reggie items, Outrigger entries,
invocation args) exhibits; distinct-chain populations are small (that is why
`maxDistinctChainsPerStream` = 256 is generous).

### C.10.3 Decision and rationale

**Whole-chain only.** The dominant redundancy is *whole-chain repetition across
occurrences* (N×F identical chains), which whole-chain references capture essentially
completely; per-record links attack only the residual *distinct-chain overlap*, worth
well under 1% on representative shapes. Against that, per-record links cost: a second
table and second key space; references *inside* chain encodings (so `fullChain` is no
longer a self-contained §7.8 byte string — reconstitution, the round-trip law, and the
Merkle cross-check all become multi-level); a larger canonicality surface (first
occurrence must be pinned at *two* granularities); and a subtler failure taxonomy for
T3's adversarial probes. Complexity of the security-bearing kind, purchased for noise
(the "less is more" lesson). Recorded revisit trigger: if T4's measurement on real
streams shows distinct-chain overlap materially above these estimates
(hierarchy-heavy deployments), a future revision MAY add per-record links **under the
same three §C.3.1 rules and a fresh §C.3.2 argument**; nothing in this appendix's wire
form obstructs that extension (a new `SchemaChainRef` arm under a new mode version).

---

## C.11 Conformance Requirements (NORMATIVE) — what T3 implements and T5 asserts

The corpus is versioned with this appendix; an implementation claims conformance to
(appendix version, corpus version) pairs. It is the deferred Rust peer's safety net:
the Rust JERI DER implementation MUST pass the same corpus version as the Java codec
before any cross-language deployment (the STD-011 §13/T5 precedent).

### C.11.1 Observable equivalence

For every corpus case (stream bytes + expected outcome), every conformant
implementation MUST produce the identical outcome:

1. **Decode equivalence:** identical reconstituted records — for each decoded item,
   byte-identical canonical chain bytes, payload bytes, and (P1) reconstituted
   four-field §7.8 record including the computed `schemaDigest` — or the identical
   rejection (case-labelled per §C.11.3).
2. **Encode determinism:** for a given record sequence and mode, the encoded stream is
   byte-identical across implementations and across repeated runs (no
   iteration-order, timing, or identity dependence — chain identity is a function of
   the class, never of instances; G4's declaration-not-instance discipline).
3. **Mode transparency:** decoding the marked and unmarked encodings of the same
   record sequence yields observably identical results above the transport (same
   objects, same chain bytes delivered upward, same MI state). *(Scope: this holds
   unconditionally for valid inputs. For malformed-chain inputs, base decode
   currently validates P2 chains less strictly than this appendix's marked-stream
   algorithm — the §C.7.3 [FLAG]; full rejection-behaviour transparency arrives with
   the §C.8.1 [PATCH] items 2–3.)*
4. **Round-trip byte-exactness:** decode(marked stream) → re-encode in unmarked mode
   → byte-identical to the canonical unmarked stream for the same sequence; and
   chain bytes delivered upward are byte-identical to `SchemaGenerator` output for the
   same classes (§C.5.4).

### C.11.2 Coverage minima (populated cases)

The corpus MUST include at least: a multi-item stream with repeated single-class
occurrences (P1a full-then-ref); repetition across P2a nested fields; P2b `Any`-slot
records; P2c array/collection elements at ceiling-relevant counts; a mixed stream
where the same chain identity first occurs at a P2 site and is later referenced at a
P1 site (and vice versa); multi-record chains exercising the `parentSchemaHash`
cross-check on the full arm; a stream at exactly `maxDistinctChainsPerStream` distinct
chains; Scenario-B sibling chains (distinct identities, overlapping parent records,
both carried full — asserting per-record sharing is *not* performed); **`chainRef`
sites at `MAX_NESTING` depth interleaved with P1/P2 first occurrences** (deep-nesting
table interaction — the reference-resolution path exercised at the recursion bound,
board fix 8b); and **one chain at exactly `maxChainBytes` referenced at high fan-out**
(a single maximal chain, one full occurrence, many references — the §C.8 boundary and
the §C.7.6 interning path exercised together, board fix 8a).

### C.11.3 Rejection conformance (implementations MUST reject; the corpus MUST include)

1. **Unknown-digest reference:** a `chainRef` whose digest has no prior `fullChain` in
   the stream — including the digest of a chain that *is* known to the receiver's local
   classes (no local fallback), and a reference-before-full byte ordering that a
   byte-order (rather than traversal-order) implementation would wrongly accept.
2. **Duplicate full form:** a second `fullChain` for an identity already tabled
   (canonicality reject, §C.6.4).
3. **Malformed `SchemaChainRef`:** arm tag outside `[0]`/`[1]`; constructed arm
   encodings; `chainRef` of length 31 and 33; empty `fullChain`; `fullChain` with
   trailing bytes after the last record; non-record content; **non-canonical interior
   encoding** (a chain record containing a non-minimal length encoding, and one
   containing a non-canonical scalar form — asserting the §C.7.3 digest-preimage rule:
   strict DER within `fullChain`, rejected before any table interaction).
4. **Chain verification failures on the full arm:** `parentSchemaHash` mismatch
   between adjacent records; a non-terminal record lacking `parentSchemaHash`
   (broken-chain case, mirroring `decodeSchemaChain`'s existing rejects); **a terminal
   record *carrying* `parentSchemaHash`** (the §C.5.1 truncated-chain/completeness
   reject — the mirror case trunk's base decode currently accepts; include the
   poisoning shape: truncated `{leaf}` first, genuine `{leaf, parent, root}` second,
   asserting the stream rejects at the *first* occurrence rather than tabling it).
5. **Marker misuse:** marker absent but dedup-mode productions present (i.e. dedup
   shapes in an unmarked stream); marker not the first TLV; duplicate marker; marker
   content length ≠ 1; unknown mode version octet (`0x00`, `0x02`, `0xFF`).
6. **Mode mixing:** a marked stream containing a pre-dedup four-field P1 record at a
   chain site (other than inside a `[8]` interior, where the canonical form is
   *required* at every nesting level, §C.6.5); a marked stream with dedup shapes
   inside a `[8]` interior — probed at the top level, at a nested proxy, **and
   transitively** (a `chainRef` inside the handler's own field payloads) — all
   rejected.
7. **`schemaDigest` restatement:** a marked-stream P1 record with four fields (the
   dropped digest field re-inserted) — trailing/extra-field reject.
8. **Ceiling boundary pairs (all four constants, inclusive fencepost):** accepted at
   exactly `maxDistinctChainsPerStream` / `maxChainRecords` / `maxChainBytes` /
   `maxDedupTableBytes`; rejected at each +1. The table-bound exhaustion cases MUST be
   *run* against the built decoder as adversarial inputs (G13 — run, don't reason),
   and the reject MUST occur at the first violating site during decode, not at stream
   end (G10 — assert via allocation/progress observation or instrumentation).
9. **Value-boundary non-dedup:** a stream carrying `MarshalledInstance` objects whose
   inner captured `schemaBytes` values equal a tabled chain — asserting the inner
   value octets travel verbatim (never referenced) and are never used to satisfy a
   reference (§C.4.3): a crafted stream whose `chainRef` could only resolve against
   value-interior bytes MUST reject as unknown-digest.
10. **Cross-stream isolation:** stream A's full-then-ref pair decodes; stream B
    consisting of A's reference item alone rejects (same digest, fresh table); two
    interleaved streams on one logical connection maintain independent tables.
11. **Degenerate cases (G11):** a marked stream with zero `@AtomicSerial` values
    (marker + primitives only — valid, empty table); a marked stream with exactly one
    chain site (valid, one full form, zero references); a single-record chain (one
    class, no parent); an empty-fields record chain (`@Stateless`-shaped class); one
    class repeated at high count within one stream (table size 1, reference count
    large — valid).
12. **Traversal-order forcing case:** the self-recursive class (`Node.next: Node`,
    §C.6.2) — outer full + nested ref accepted; the byte-order-tempting inverse
    (outer ref + nested full) rejected on both legs (outer site: unknown digest at
    processing time; nested site: duplicate full).
13. **Negotiation matrix:** all six §C.9.5 cells — including the marked-request→
    pre-dedup-receiver loud reject (run against the actual pre-dedup decoder), the
    **marked-request→config-off-receiver loud reject** (run against dedup-capable code
    with the capability off — asserting rejection happens at the marker, before any
    table allocation), the unmarked-request→enabled-receiver transparent path with
    byte-identical pre-dedup response, and echo-rule conformance (an enabled
    dispatcher answering an unmarked request MUST NOT mark the response).
14. **Standalone-context exclusion:** an MI capture produced by a dedup-capable
    implementation contains no marker and no dedup shapes (byte-compare against
    pre-dedup capture of the same object).
15. **Boomerang relay across streams (board fix 1a):** a nested proxy decoded from a
    **marked** stream with interface narrowing (raw-form retention triggered), then
    re-forwarded into a *later* stream — both a marked and an unmarked one. Both
    relays MUST decode cleanly with byte-verbatim `[8]` content, which is possible
    precisely because the §C.6.5 exclusion means the retained interior was never
    dedup-encoded (no stream-scoped reference to dangle). The negative leg: a
    synthetic retained form containing a `chainRef` MUST reject on re-decode.
16. **Exclusion transitivity (board fix 1b):** a marked stream carrying a `[8]` proxy
    whose handler has an `@AtomicSerial` field of class `X`, where `X` also occurs at
    ordinary chain sites *outside* the `[8]` in the same stream: the outside sites
    dedup normally (first full, then references), while every interior `X` record is
    canonical full form — and the interior full forms are NOT counted as occurrences
    (an outside-`X` site *after* the `[8]` still follows the outside-only
    first-occurrence sequence; the interior neither populates nor consumes the
    table).

---

## C.12 Relationship Notes (informative)

### C.12.1 C5 storage interning (`SOW-Outrigger-DER-Only-JOSS-Rejection.md` §5)

C5 interns chain bytes at rest (heap: stored `MarshalledInstance.schemaBytes`; disk:
digest-keyed container storage). Same digest key, different lifetime, zero coupling:
stored bytes are always canonical full form (§C.1.2), so C5 interns exactly what this
appendix reconstitutes, and §C.7.6's SHOULD hands receive-path sharing to C5's heap
half for free. Neither depends on the other's presence.

### C.12.2 EntryRep-v2 (`SOW-Entry-ATOMIC-DER-Migration.md` §9.6)

v2 restructures the *entry layout* — one chain per entry instead of per field —
attacking the intra-entry duplication this appendix cannot reach (the inner-MI chain
values of §C.4.3) and reducing the structural site count itself. Complementary, not
replaced: post-v2 streams still repeat per-entry chains across N entries, which
remains this appendix's job. The v2 deferral's recorded invariant (v2 derivable from
v1's stored data) is unaffected — this appendix never changes stored data.

### C.12.3 The chain-bytes-caching rider (Outrigger SOW §5, seat 3)

`SchemaGenerator` memoises the *parsed* chain per class, but
`MarshalledInstanceRecord.fromChain` re-encodes chain bytes on every marshal. The rider
caches encoded chain bytes per class — a sender-side *work* reduction, where this
appendix is a wire *bytes* reduction. They compose multiplicatively: with both, a
subsequent occurrence costs neither an encode nor its bytes, and a first occurrence
costs one cache read. The rider also hands T2's encoder its table-value representation
(§C.7.6). Independent changes; either lands without the other.

### C.12.4 Decision 6 and compression (why this is admissible and DEFLATE is not)

Decision 6 bans value-reading compression over encrypted connections because a shared
compression context makes ciphertext length a function of secret plaintext content
(CRIME). §C.3.2 shows this scheme's length is a function of structure only — it is not
compression in the CRIME-relevant sense, so the ban does not cover it, and the
encrypted-transport bulk-response bloat decision 6 left open is closed here. The
optional plaintext-transport DEFLATE follow-on remains where it is, pending T4's
three-way measurement (JOSS+DEFLATE vs plain DER vs deduped DER) — which this
appendix's Scenario-A estimate predicts will show deduped-DER-over-TLS at rough parity
with the old JOSS+DEFLATE sizes for schema-dominated payloads, likely dissolving the
follow-on's remaining motivation. Class-name/TLV-skeleton dictionaries (SOW §6.4) stay
deferred until T4 shows a material residue; any such extension re-runs §C.3.2.

---

## C.13 Summary of [PROPOSED] / [OPEN] / [FLAG] / [PATCH] Items

### C.13.1 Requiring Peter's ratification [PROPOSED]

| # | Item | Section |
|---|---|---|
| 1 | Placement: STD-006 appendix (standalone file, merged post-review) | §C.1.3 |
| 2 | `SchemaChainRef` arms/tags and the dedup-mode record shapes, incl. dropping `schemaDigest` from the marked-stream P1 record (board-endorsed contingent on item 10) | §C.5.1, §C.5.3 |
| 3 | Stream mode marker: tag `[15]`, one-octet version content | §C.5.2 |
| 4 | Traversal-order definition of "first occurrence" (record-entry pre-order), incl. the one-pass buffering consequence | §C.6.2 |
| 5 | **Every** `[8]` proxy interior excluded from dedup — any nesting level, byte-region-scoped, transitive (retention contract at both write/read sites) | §C.6.5 |
| 6 | Per-marshal-stream table scope (narrowing the SOW's "per-connection") | §C.7.1 |
| 7 | Ceiling values 256 / 64 / 65536 / 1 048 576 | §C.8.1 |
| 8 | Negotiation: export-affirmed capability + marker + response-echo; transparent full-form fallback on the sending side | §C.9.2, §C.9.4 |
| 9 | Granularity: whole-chain only | §C.10.3 |
| 10 | Chain-completeness rule (terminal record carrying `parentSchemaHash` = reject), making chain identity → bytes injective; plus the base-decode [PATCH] items (completeness in `decodeSchemaChain`, Merkle cross-check in P2 `decodeNested`, §7.8 clarifying sentence) | §C.5.1, §C.7.3, §C.8.1 |
| 11 | Export configuration gates **acceptance as well as emission** (config-off rejects marked streams at the marker; stale-proxy-after-rollback becomes loud) | §C.9.2 item 4, §C.9.4, §C.9.5 |
| 12 | Marked-profile ceiling precedence: the marked-stream profile tightens the admissible class set relative to base §4.5 maxima, failing loudly at encode time | §C.8.1 |

### C.13.2 Verified discrepancies and gaps found [FLAG]

| # | Finding | Disposition |
|---|---|---|
| 1 | SOW §3.3 "per-stream (per-connection)": the codec's real granularity is per marshal stream (per call, per direction); no connection-scoped state exists | Pinned narrower, §C.7.1 |
| 2 | SOW's chain-site inventory omits the STD-008 §16 nested production (P2) — the site class hosting most of the N×F duplication | Covered uniformly, §C.4.4 |
| 3 | Chains inside `MarshalledInstance` field *values* (inner captured `schemaBytes`) are not dedupable under the ratified rules; "captures essentially all of the measured redundancy" holds for scalar-field entries (empty inner schemas) but leaves an inner-chain residue for `@AtomicSerial`-valued fields | Scope-honesty note §C.4.3; residue owned by EntryRep-v2/C5, §C.12.2 |
| 4 | STD-006 §4.5 has no chain-record-count or chain-byte ceiling; pre-dedup chain parsing loops uncapped below `maxInputBytes` | [PATCH] recommendation, §C.8.1 |
| 5 | SOW line citations (`:410`, `:1158-1162`, `:371-382`, `:194-203`) verified accurate against trunk | No action, §C.4.4 |
| 6 | **This document's own v0.1 verification error (board round 1, fix 1 — both seats, HIGH, CONFIRMED):** v0.1 claimed nested (P2d) proxy records have no raw-form retention path. False against trunk: `ObjectCodec.encodeProxy` consults retention first (`ObjectCodec.java:1739-1742`), nested `decodeProxy` injects `[8]` content as `rawForm` on narrowing (`:2088-2094`), and `ProxyWireSupport` documents both write sites. The v0.1 grep-level check saw the fresh-encode call at `:1762` and missed the retained branch 20 lines above it — an instance of exactly the empty-search≠absence failure G3 warns about, recorded here by the standard this document applies to others | Corrected: exclusion generalized to all `[8]` interiors, §C.4.2, §C.6.5 |
| 7 | **Base-standard validation gaps (board round 1, fixes 2 and 5):** (a) base `decodeSchemaChain` (`MarshalledInstanceRecord.java:306-343`) accepts a terminal record with a dangling `parentSchemaHash` (truncated chain decodes cleanly — under dedup this would have been stream-wide table poisoning); (b) base P2 decode (`ObjectCodec.decodeNested:1996-2006`) skips the Merkle cross-check the P1 path runs, contra its own "same format" comment | Marked-stream rules close both for dedup streams (§C.5.1, §C.7.3); base fixes recommended in the §C.8.1 [PATCH], items 2–3 |

### C.13.3 Left open, with owners [OPEN]

| # | Item | Owner |
|---|---|---|
| 1 | Appendix letter at merge (STD-006 has no A/B yet) | merge editor |
| 2 | `[15]` tag registration in STD-008's object-stream tag registry | T2 (+ STD-008 editor) |
| 3 | Whether `DerReducingContextCodec` context streams enable dedup in increment 1 (must be deterministic per endpoint config either way) | T2 |
| 4 | Implementing the §C.6.5 region exclusion at all four retention sites (both write, both read — the paths exist in trunk **now**, `ObjectCodec.java:1739-1742`/`:2088-2094` and the object-stream pair) and carrying the §C.11.3(15)–(16) vectors | T2 / T3 |
| 5 | Exact `AtomicDerILFactory` configuration surface for the capability flag (which now gates acceptance as well as emission, §C.9.2 item 4) | T2 |
| 6 | Whether T4's measurement triggers the per-record-links revisit (§C.10.3's recorded trigger), the SOW §6.4 dictionaries, or action on the `[8]`-interior residue (§C.6.5 cost annotation) | T4 → board |
| 7 | Base-standard adoption of the chain ceilings and their precedence vs §4.5 maxima, plus the [PATCH] items 2–3 (completeness and P2 cross-check in base decode) | base-adoption pass (T6 per the board fix list) → STD-006 editor + board |

---

*End of v0.2-DRAFT. Produced as T1 of `SOW-DER-Stream-Schema-Dedup.md`. Board-reviewed
2026-07-21 (two adversarial seats: wire-format/canonicality, adversarial security; both
SOUND-WITH-FIXES); the consolidated 8-item fix list was applied in this revision — see
the board review record in the header, including the two HIGHs (the generalized `[8]`
interior exclusion, correcting this document's own v0.1 verification error; and the
chain-completeness rule closing the truncated-chain table-poisoning collision). No
production code was written or modified in producing this document. Ground-truth
claims were verified against trunk source (`jgdms-der`, `jgdms-jeri`, Outrigger
`EntryRep`) on 2026-07-21, including re-verification of the board's fix-1 and fix-2
citations; all worked byte sketches are illustrative pending T3/T5's machine-generated
vectors.*
