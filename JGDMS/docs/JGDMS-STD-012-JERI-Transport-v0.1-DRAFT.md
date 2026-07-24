# JGDMS-STD-012 — JERI Transport & Invocation Standard

> **Format name.** This standard specifies the **JERI Transport & Invocation** layer —
> the multiplexing protocol, the request/response invocation wire sequence, the
> constraint-enforcement and fault-marshalling contracts, the endpoint/transport SPI, and
> distributed GC — as one normative model. It names the layer that opens the **ATOMIC DER**
> object stream (STD-006) but is not itself the object format. The **standard document
> identifier** is `JGDMS-STD-012` (§0). Generic "JERI" continues to mean Jini Extensible
> Remote Invocation; "the mux" means the Jini ERI Multiplexing Protocol reconciled in §5.

**Status:** Draft — **SKELETON ONLY** (section inventory + one normative-intent sentence per section + open-question register). No section is written to full prose; this artifact exists so the review board can assess scope completeness before prose is authored (per `SOW-JERI-Standard-Modernization.md`).
**Version:** 0.1-DRAFT
**Date:** 2026-07-24
**Author:** Peter Firmstone + Claude
**Applies to:** JGDMS 4.0.0+, DirtyChai (JDK fork), and non-JVM JGDMS participants (the Rust JERI DER peer, gated per `SOW-CEL-Filter-Format.md` T4)
**Depends on:**
- **JGDMS-STD-006** (ATOMIC DER Wire Format) **≥ v0.13** — the object-stream substrate the invocation layer opens; **Appendix C (Stream Schema Dedup)** is a MANDATORY, NORMATIVE part: the `[15]` version octet `8F 01 01` and the per-marshal-stream dedup table lifetime are **cited, never restated** (§6.3).
- **JGDMS-STD-008** (AtomicSerial Serialization Uncoupling) — **§6 is the DGC modernization design-of-record** (§10); §15/§16 the DER object-stream framing and item-tag registry.
- **JGDMS-STD-003** (Multi-Subject Identity) — the user-Subject / ACC blocks carried in the v2 invocation header (§6.2).
**Related (non-normative):**
- `docs/board-guidance/transport-jeri.md` — the JERI-transport reviewer seat; this standard's contracts are that document's findings promoted to normative work items. **The review board for this standard IS that seat.**
- **JGDMS-STD-010** (QUIC-JERI Transport) — re-read under this standard as a **profile** of §4's SPI and §9's family model, not a peer standard (§0, §9).
- **JGDMS-STD-009** (Service & @RemoteFunction Annotation Model) — processor-assigned method identifiers (§6.4).
- **JGDMS-STD-011** (DETERMINISTIC CEL) §3.3 — the half-discharged `maxScalarBytes`; its intersection with this layer is OQ-4 (§8).
**Substrate being modernized (all stale, none authoritative — G3):** the Sun-era Jini ERI mux spec surviving in-repo as `net/jini/jeri/connection/doc-files/mux.html` (**Multiplexing Protocol v1.1**); `net/jini/jeri/package.html` / `Overview.html`; SRC-RR-116 (`docs/SRC-RR-116.pdf`, Birrell 1993, *Network Objects*) for DGC; and the `OutboundRequest`/`InboundRequest`/`Connection`/`ServerConnection` javadoc, which the board seat rules **is** today's real spec.

---

> **Editorial note (v0.1-DRAFT — SKELETON).** This document is the companion skeleton to
> `SOW-JERI-Standard-Modernization.md`. It carries **section headings, one normative-intent
> sentence each, and the open-question register** — not prose. Each section's intent
> sentence is written to the **G9 acceptance criterion**: it states *what a non-JVM peer
> (the Rust JERI DER implementer) must be told* to implement that section from this document
> alone, with zero access to Java source. The full-prose author of each section (SOW tasks
> T1–T8) writes to that sentence.
>
> **Requirements language.** The key words **MUST**, **MUST NOT**, **REQUIRED**, **SHALL**,
> **SHOULD**, **SHOULD NOT**, **MAY**, and **OPTIONAL** are to be interpreted as in
> RFC 2119 / RFC 8174 when in capitals. **"Fail closed"** means: on any ambiguity, breach,
> or unrecognised input, construct no object, carry no traffic, authorize nothing, and
> refuse — never fall back to a permissive default.
>
> **Markers.** **[PROPOSED]** = a design recommendation requiring Peter's ratification.
> **[OPEN]** = a question deliberately not resolved here, each naming its owning SOW task.
> **[RATIFIED]** = decided by Peter with the date recorded. **[RECONCILE]** = content that
> already exists in an in-repo artifact and is imported/aligned, not written from scratch
> (§5). **[FLAG]** = a source-vs-source discrepancy or live `TODO` in trunk this standard
> MUST settle. **[G3]** = a claim verified against trunk source at the cited `file:line`.
>
> **Provenance & verification.** The wire facts below were surveyed against trunk on
> 2026-07-24 and re-verified for this skeleton. Where the authoring SOW's claim diverged
> from trunk, the trunk value governs and the divergence is recorded inline (see §5.2 and
> the "SOW-vs-trunk deltas" note at the end). Prose never overrides code; every wire claim
> is anchored to `file:line`.

---

## 0. Identifier decision — JGDMS-STD-012

**Normative intent (G9):** The peer implementer is told which standard number names this layer and how it relates to STD-010, so cross-references resolve unambiguously.

- **[RATIFIED? → Peter]** Allocate **STD-012** (STD-001..011 are assigned; 012 is the next free number, `docs/` inventory 2026-07-24). **[OPEN → Peter]** ratify 012 or assign otherwise (SOW §0).
- **[PROPOSED]** The numbering reads inverted (STD-010 QUIC is a single *instance* of the general model this standard defines) and that is acceptable — standards are numbered in authoring order. STD-010 gains a one-line forward-reference at merge ("a profile of STD-012 §4/§9"); STD-010's normative content is **not** moved (OQ-6).

---

## 1. Purpose and Scope

**Normative intent (G9):** The peer implementer is told exactly which contracts this standard makes implementable-from-the-doc — and which it defers to STD-006/008/011 by citation — so the boundary of "what STD-012 obliges me to build" is unambiguous.

### 1.1 What this closes
One normative standard that states the layering model and endpoint/transport SPI as named testable contracts; reconciles the mux protocol (§5); specifies the invocation wire sequence exactly (§6); specifies fault-marshalling (§7) and constraint enforcement (§8); modernizes DGC (§10); and states versioning + the G9 acceptance criterion (§11–§12). It closes the **one layer of the JGDMS stack a Rust JERI DER peer hits first that cannot today be built from the docs**.

### 1.2 What this deliberately is not (non-goals)
- **Not re-specifying STD-006/008/011** — the object stream, nested-record framing, dedup rules, and CEL grammar are **cited, never restated**; only the seams are owned here.
- **Not rewriting the mux wire format** — §5 is [RECONCILE] against `mux.html` v1.1, not green-field.
- **Not designing new transports** — TCP/TLS/UDS/QUIC exist; a future transport is a new *profile* authored against §4/§9.
- **Not redesigning DGC** — modernize the write-up and pin the one residual coupling (§10), do not invent a new DGC.
- **Not the Rust implementation** — spec + conformance-corpus obligation only (Appendix C / SOW T10).
- **Not the ERI *discovery* spec** — DiscoveryV1 was removed in 4.0.0 (V2-only); discovery is out of scope.
- **Not changing on-wire *values*** — the `0x00/0x01/0x02` version byte, the `0x01`-return/`0x02`-throw discriminator, and the mux message-type table are transcribed as-is; any change is a *versioned* wire change with its own board round (§11).

### 1.3 Security context
This layer is where authenticated peer identity, invocation constraints, and the reducing-domain ACC meet adversary-influenced bytes; §6–§8 are the security-defining core and are held to the BAE / adversarial standard. A bug in the byte sequence (§6) or in whether a claimed constraint is honoured (§8) is a wire-format or confused-deputy security bug.

---

## 2. Definitions and terminology

**Normative intent (G9):** The peer implementer is given a single vocabulary — `Endpoint`, `ServerEndpoint`, `OutboundRequest`, `InboundRequest`, `Connection`, `ServerConnection`, session, request, marshal stream, method identifier, DGC lease — so every later section's terms are pinned before use.

- **[OPEN → T1]** Populate the term table (house style: STD-006 §2, STD-011 §2). Each term cites its trunk interface/javadoc origin.
- Three terms are defined here because the whole standard turns on keeping them apart, and Appendix A elaborates them: **byte-flow direction**, **request-origination direction**, **authorization direction** — three *independent* axes.

---

## 3. Layering model — the reference stack

**Normative intent (G9):** The peer implementer is told the exact vertical order of the layers and where one hands off to the next, so it knows which byte belongs to which layer and can build them as separable modules.

The reference stack, top to bottom: **object/ATOMIC-DER (STD-006)** → **invocation (§6)** → **request/response SPI (§4)** → **mux / connection (§5)** → **transport / endpoint (§4, §9)** → **byte stream**. Each adjacent pair has a named seam; the invocation↔object seam is pinned in §6.3, the transport↔mux seam in §5, the endpoint↔transport seam in §9.

- **[OPEN → T1]** The stack diagram + the one-paragraph-per-seam handoff statement.

---

## 4. Endpoint & transport SPI — the one model with named contracts

**Normative intent (G9):** The peer implementer is given the full set of behavioural obligations behind `Endpoint`/`ServerEndpoint`/`OutboundRequest`/`InboundRequest`/`Connection`/`ServerConnection` as RFC-2119 text — **not interface signatures** — because the guarantees live in prose that no peer would know to read as a spec.

### 4.1 The SPI surface and its deliberate asymmetry
`ServerConnection` exposes only `processRequestData` + `InboundRequest` hooks — **no `newRequest`, no `OutboundRequest`**; the client-side `Connection` is the only side that feeds `OutboundRequest`. **[G3]** This asymmetry is load-bearing: request origination flows client→server only (`Session.getOutboundRequest()` asserts `role==CLIENT`, `getInboundRequest()` asserts `role==SERVER`). A server endpoint that grows an origination path is a finding, not a feature (except the §9 server-initiated-stream profile).

### 4.2 The four invisible transport contracts (promoted from javadoc to normative text)
Each MUST become RFC-2119 text with its consumer named; none is visible in the SPI signatures:
- **4.2.1 DGC AcknowledgmentSource** — fires `acknowledgmentReceived(true)` **only after the receiver's `RequestDispatcher` processed the request**, never on byte delivery. A stream/TCP/QUIC FIN is **strictly weaker** and MUST NOT be equated with it (silent DGC lease-correctness bug). Consumers: `BasicObjectEndpoint` (the DGC client) + an independent HTTP-transport re-implementation.
- **4.2.2 `getDeliveryStatus()`** — the at-most-once / retry signal: `false` = "known not processed, safe to retry"; `true`/unknown = "may have been processed, MUST NOT auto-retry." **Fail-closed to "maybe processed"** on any ambiguity.
- **4.2.3 Per-direction half-close** — the response is readable **while the request is still being written**; a transport that couples the two directions' end-of-stream state breaks the early-response contract.
- **4.2.4 Mux flow-control rationing** — per-session credit; a transport with its own flow control (QUIC `MAX_STREAM_DATA`, TCP window) MUST **delete** the mux rationing, never stack two credit schemes.

### 4.3 The `getChannel()` seam
`Connection.getChannel()` returns an **optional** `SocketChannel`: `null` for blocking `SSLSocket`-bound transports, a real channel for UDS/QUIC. This optionality is why the SPI is already the right shape for channel-backed / virtual-thread transports; it is the per-profile nullability §9 quantifies over.

- **[OPEN → T1]** Transcribe each `OutboundRequest`/`InboundRequest` javadoc guarantee paragraph to RFC-2119, citing the javadoc as its origin (do not paraphrase loosely).

---

## 5. Mux protocol [RECONCILE — not write-from-scratch]

**Normative intent (G9):** The peer implementer is given the complete frame format, message-type table, session lifecycle, and flow-control rules to speak the mux on the wire — imported from the shipping v1.1 spec, with every trunk deviation from that spec catalogued and resolved to the wire.

### 5.1 Import of `mux.html` v1.1
The mux wire format is **imported as normative** (transcribed to G9 markdown + a bit-layout companion in Appendix B, matching the STD-006 `docs/asn1/` precedent), not rewritten. **[G3]** `MAGIC = "Jmux"` (0x4A6D7578), `VERSION = 0x01`, 8-bit `SessionID` (`MAX_SESSION_ID = 0xFF`, 256 sessions), 8-byte connection headers with `initialRation`, client-only session establishment, per-session inbound/outbound rations. Message-type first bytes **[G3, `Mux.java:59-75`]**: `NO_OPERATION 0x00`, `SHUTDOWN 0x02`, `PING 0x04`, `PING_ACK 0x06`, `ERROR 0x08`, `INCREMENT_RATION 0x10`, `ABORT 0x20` (`ABORT_PARTIAL 0x02` bit), `CLOSE 0x30`, `ACKNOWLEDGMENT 0x40`, `DATA 0x80` (with `OPEN 0x10`/`CLOSE 0x08`/`EOF 0x04`/`ACK_REQUIRED 0x02` flag bits).

### 5.2 Deviation catalogue [FLAG — resolve to the wire, G3]
The three trunk deviations from `mux.html` v1.1 the standard MUST settle (OQ-1):
- **5.2.1** Client-side major-version negotiation is **unimplemented** (`CLIENT_CONNECTION_HEADER_NEGOTIATE 0x01` exists but the client path is not wired).
- **5.2.2** **Ping *initiation* is NYI; ping *response* ships.** **[G3, `Mux.java:1094`]** the ping-initiation machinery is stubbed (`NYI: rest of ping machinery`), but **[G3, `Mux.java:908–914,1075–1081`]** `handlePing`→`asyncSendPingAck` implements the responder path. Disposition [PROPOSED]: a conformant peer **MUST answer a received `PING` with `PING_ACK`** (the responder is implemented) but need not *originate* a `PING` — normative-as-shipped, not mandated-into-existence (doc-only SOW).
- **5.2.3** **[G3, `Mux.java:67`]** The `ACKNOWLEDGMENT` comment `// 00100000` is **in error**: the value `0x40` is binary `01000000`; `00100000` is `0x20` (ABORT's base). **The wire value `0x40` is authoritative; the comment is wrong.** This is a source-vs-source discrepancy the standard resolves to the wire, never the comment.

### 5.3 The four invisible contracts, restated at the mux layer
§4.2's contracts are anchored here to their mux mechanisms (`sentAckRequired`/`receivedAcknowledgment`/`notifyAcknowledgmentListeners` for the ack; `ABORT|ABORT_PARTIAL` → `partialDeliveryStatus` for delivery-status; `Close`-instead-of-`Abort` for half-close; `inRation`/`outRation`/`IncrementRation` for flow control), and flagged as the highest-blast-radius, least-documented area of the whole standard.

- **[OPEN → T2]** Appendix B form: bit-layout table + formal (ABNF-style) grammar vs best-effort ASN.1-with-notes (OQ-5; the mux is bit-packed, not TLV, so ASN.1 is an awkward fit — the G9 requirement is peer-implementability, not ASN.1 specifically).

---

## 6. Invocation layer — the request/response wire sequence

**Normative intent (G9):** The peer implementer is given the exact byte-for-byte client→server request sequence and server→client response discriminator, plus the precise point at which it enters the STD-006 object stream, so it can drive and dispatch a remote call without reading Java.

### 6.1 The request byte sequence [G3, `BasicInvocationDispatcher.dispatch`, `:824-965`, `:994-1044`]
In order, on the request stream:
1. **Marshal-stream protocol version byte** — `0x00` (`PREVIOUS_VERSION`), `0x01` (`VERSION`), or `0x02` (`VERSION_WITH_PRINCIPALS_AND_ACC`); any other value → the server writes the version-mismatch signal (§6.5) and returns.
2. **Version-dependent header blocks:** `0x00` → integrity byte; `0x01` → integrity + atomic-input-validation bytes; `0x02` → integrity + validation + **one-or-more user-Subject blocks** + a serialized `AccessControlContext`.
3. **Method identifier** (§6.4) — read from the DER marshal input stream.
4. **Arguments** — unmarshalled from the marshal input stream.

### 6.2 The user-Subject block wire format [G3, `:871-881`]
`subjectCount` (u16 BE); per Subject `principalCount` (u16 BE); per principal `classNameLength` (u16 BE) / `classNameBytes` (UTF-8) / `nameLength` (u16 BE) / `nameBytes` (UTF-8). The reconstructed read-only Subjects + the ACC form the reducing-domain caller identity (STD-003; STD-006 §7.2 ACC model).

### 6.3 The DER object-stream boundary [cite STD-006 Appendix C — do NOT restate]
The marshal streams are ATOMIC DER object streams: each begins with the **mandatory** `[15]` version octet `8F 01 01`; **dedup is the sole stream form** (not negotiated); **dedup table lifetime = one marshal stream = one `DerObjectStreamCodec` lifetime.** One remote call opens **two independent marshal streams — the argument stream and the return (or throw) stream — each with its own dedup table.** This section owns *where the invocation layer enters the object stream*; STD-006 Appendix C owns the dedup rules.

### 6.4 Method identifier [RATIFIED — Peter, 2026-07-24]
**Normative intent (G9):** The peer is told exactly how to compute and frame the method-selector field for each wire version, so it selects the same method the Java dispatcher does, and how the field evolves without a frame change.

The method identifier is **version-keyed** — a property of the marshal-stream version byte (§6.1), NOT a single scheme:
- **`0x00` (pre-Atomic JOSS, `PREVIOUS_VERSION`) — REJECTED, fail-loud.** `0x00` is **rejected outright** via the §6.5 MISMATCH path (two `0x00` bytes). This is a **SECURITY** decision — `0x00` is the JOSS-without-Atomic version, the deserialization-gadget-exposure surface — **not** hash-driven. It is the **only** version rejected.
- **`0x01` (Atomic, JOSS wire format, `VERSION`) — SHA-1/64-bit RETAINED.** **[G3, `Util.computeMethodHash`, `:243-267`]** the identifier is the **first 64 bits of SHA-1** over `writeUTF(methodName + JVMS §4.3.3 descriptor)`, accumulated **little-endian** into a `long` — the hash River/JGDMS 3.x actually released (RMI heritage). Specified as a **non-cryptographic structural selector** with a threat-model note (collision lets a caller name a *different method of the same interface*; the dispatcher still enforces constraints and access on the **resolved** method — post-resolution authorization — and inputs are developer-declared, not attacker-chosen); confined to this atomic-interop lane there is no real risk in retaining it for the transition. **SUPPORTED through the 4.x line; sunset in JGDMS 5.0** (targeted for release *before* the NIST 2030 SHA-1 retirement).
- **`0x02` (DER — the version under development for release with JGDMS 4.0.0, adding principals/ACC + DER marshalling) — SHA-256.** The identifier is the **leftmost *N* bytes of SHA-256**(name + descriptor). Because `0x02` is **unreleased**, it adopts SHA-256 at **zero interop cost** — SHA-256 **ships in 4.0.0 on the DER path**, not deferred to 5.0.
- **Framing (RATIFIED design constraint):** the identifier *field* is **version-keyed and length-delimited**; *N* is a parameter of the wire **version**, not the frame. The framing MUST accommodate widths up to the full **64-byte SHA-512** output **with zero framing change** (a future version may adopt SHA-512, or SHA-512/256 for 64-bit throughput). **The collision bound is set by field WIDTH (birthday at 2^(N/2)), NOT by the digest algorithm** — upgrading SHA-1→SHA-256 without widening changes nothing measurable; it removes an audit finding, not a real exposure.
- **Default width for `0x02` (first modern version) [RATIFIED — Peter, 2026-07-24]:** **128 bits** (SHA-256 truncated; ~2^64 birthday bound; +8 bytes/call vs the old 64-bit hash — wire bytes are the only recurring cost, since hashing is memoised at proxy/dispatcher construction). Full-width identifiers are **deliberately not** the default.
- **STD-009 processor-assigned ids** (`@RemoteFunction`/`@JiniService`) fit the **same** length-delimited field — "explicit id where one exists, SHA-256-leftmost-*N* otherwise", not an either/or.
- **SHA-1 is NOT dropped entirely and pre-4.0 is NOT wholesale-rejected:** only `0x00` is rejected fail-loud (security); `0x01` keeps SHA-1 as the atomic-interop lane (sunset 5.0) and `0x02` uses SHA-256 from the start. An unrecognised version byte (neither `0x00`/`0x01`/`0x02`) is likewise rejected via the §6.5 MISMATCH path.

### 6.5 Version-mismatch signal [FLAG → settle OQ-2]
**[G3, `:1011-1019`]** On an unrecognised version byte the server writes `MISMATCH` (`0x00`) followed by `PREVIOUS_VERSION` (`0x00`) — two `0x00` bytes — then closes. **[FLAG, `:1015-1016`]** trunk carries a live `TODO`: *"Confirm if version was to be written in spec, or just 0x00 — currently test just checks for 0x00."* This standard MUST settle what a conformant peer writes and accepts, and whether the two-`0x00`-byte signal is normative.

### 6.6 The response discriminator [G3, `:932-964`]
On the response stream: a leading **`0x01`** byte precedes `marshalReturn` (normal return); a leading **`0x02`** byte precedes `marshalThrow` (exception). This one byte is the sole return-vs-throw discriminator; §7 owns the throw carrier.

---

## 7. Fault-marshalling contract

**Normative intent (G9):** The peer implementer is told how a thrown exception is carried back over the DER stream and reconstructed — a contract absent from the Sun spec — so a Java throw and a Rust throw round-trip identically.

- **7.1 The `marshalThrow`/`unmarshalThrow` seam** — **[G3]** the DER path is `AtomicDerInvocationDispatcher.marshalThrow` (`:189`) carrying the **`DerThrowableForm`** carrier (`jgdms-platform/.../org/apache/river/api/io/DerThrowableForm.java`); it encodes only the STD-006 §7.6 **safe subset** (className, message, stackTrace, suppressed[], cause) — no reflective reconstruction.
- **7.2 Wrapping rules** — `RemoteException` → `ServerException`; `Error` → `ServerError`; checked unmarshal exceptions (`IOException`/`ClassNotFoundException`/`NoSuchMethodException`) → `UnmarshalException`.
- **7.3 The null-method case** — a fault occurring before/during `unmarshalMethod` passes a `null` remote method to `marshalThrow`.
- **7.4 The round-trip law** — fault carriage over the DER stream MUST be lossless for the safe subset and MUST NOT reconstruct excluded gadget-adjacent fields.

---

## 8. Constraint model and enforcement

**Normative intent (G9):** The peer implementer is told how `InvocationConstraints`, the `MarshallingFormat` (`ATOMIC_DER`), and integrity/confidentiality/validation requirements are **carried on the wire and enforced at the endpoint** — and the rule that a transport MUST NOT claim a security property it has not verified.

- **8.1 Carriage** — the integrity/validation header bytes (§6.1), `MethodConstraints.getConstraints`, and the `MarshallingFormat.ATOMIC_DER` constant / `"JGDMS-STD-006/ATOMIC-DER"` on-wire identifier.
- **8.2 Enforcement** — `InboundRequest.checkConstraints`; unfulfilled non-`Integrity` requirements (or `Integrity.YES` unmet) → `UnsupportedConstraintException` back to the caller via the §6.6/§7 throw path; then `checkAccess`.
- **8.3 The constraint-honesty rule [normative]** — **a transport MUST NOT assert a security property it has not verified** (the UDS `Confidentiality.YES`-by-locality over-claim war story: a claim made statically from a deserialized path, forgeable by a hostile serialized form, is worse than no claim). Every `FULL_SUPPORT` claim MUST name who verified it and be un-forgeable by a deserialized value.
- **8.4 The `maxScalarBytes` intersection [OPEN → T5, OQ-4]** — **Default disposition: hand back** to a STD-006/STD-011 editorial pass. STD-011 §3.3's `maxScalarBytes` is enforced on the expression-literal side; the candidate-projection side + the STD-006 §4.5 ceiling row are unpinned. §8 pins it **only if** T5 finds the invocation layer is the actual enforcement point (e.g. as an `InvocationConstraint` on a `@RemoteFunction` call or an argument-stream ceiling). **This standard MUST NOT silently adopt the orphan bound.**

---

## 9. Endpoint families as profiles

**Normative intent (G9):** The peer implementer is told, for each shipping transport, its deltas from the §4 SPI — confidentiality/integrity claims, `getChannel()` nullability, and any server-initiated-stream inversion — so it can implement or interoperate with a specific family.

Each family is a **profile** of §4's one SPI:
- **9.1 TCP** — plaintext; `Confidentiality.NO`; mux-based.
- **9.2 SSL / TLS 1.3** — handshake-authenticated peer; **blocking `SSLSocket`, `getChannel()` returns `null`** (the `SSLSocket`→`SSLEngine`-over-channel keystone is the obstacle to channel-backing SSL); mux-based.
- **9.3 Kerberos** — mux-based; per-profile identity deltas.
- **9.4 UDS** — channel-backed (`getChannel()` non-null); mux-based; **inc-1 is plaintext + layer-2 SPIFFE/JWT identity carried inside request data**, socket-file perms as the access gate, **deliberately no `SSLEngine`** (do not credit it as the SSLEngine keystone).
- **9.5 HTTP / HTTPS** — **HTTP framing, NOT mux**; the DGC-ack contract is independently re-implemented here (proof it is a transport-SPI obligation).
- **9.6 QUIC (STD-010, draft / not-implemented) [OPEN → T6]** — becomes the **QUIC profile by reference**; carries the **server-initiated-stream / ephemeral-client** model: on a server-pushed (`0x01` bidi) stream the **caller is the SERVER**, authz **inverts** (caller = stream initiator, not connection initiator; confused-deputy guard mandatory), and the ACC reducing-domain block **mirrors direction**. If T6 finds STD-010 contradicts §4, reconcile toward STD-010 (board-reviewed against real QUIC), do not diverge silently.

---

## 10. Distributed GC (DGC)

**Normative intent (G9):** The peer implementer is told the dirty/clean lease reference-counting model, the well-known DGC object identity, and how DGC calls ride the DER invocation path — so a peer can participate in distributed GC without the 1993 research paper or Java source.

- **10.1 Model** — SRC-RR-116 (Birrell 1993) dirty/clean lease reference counting, **modernized to the DER era**; **STD-008 §6 is the design-of-record** (DER dirty/clean round-trip already proven — `BasicObjectEndpointDerDgcTest`).
- **10.2 Well-known DGC identity** — **[G3, `BasicObjectEndpoint.java:87`]** the DGC endpoint's well-known object Uuid is `d32cd1bc-273c-11b2-8841-080020c9e4a1`.
- **10.3 The DGC-ack's role** — a `dirty`/`clean` is ack-required; lease accounting depends on the §4.2.1 post-*processing* acknowledgment, never on byte delivery.
- **10.4 The residual legacy path to remove [OPEN → T7]** — DGC is specified **DER-native** per the 4.0.0 JOSS-rejection posture (**STD-008 §6** design-of-record). The batch map is **already generalized** to an Object-keyed, DER-capable decode-unit token (**[G3, `BasicObjectEndpoint.java:233–243`]**, DER-proven by `BasicObjectEndpointDerDgcTest`) — it is **not** a JOSS coupling. The actual residual is the **surviving legacy `readObject(ObjectInputStream)` path** (**[G3, `BasicObjectEndpoint.java:765–800`]**), framed as **removal work** completing the DER-native transition, not a coexisting JOSS path to preserve. This section states the removal direction and addresses the deserialization-uncoupling blocker; it does **not** re-open the whole DGC design.

---

## 11. Versioning and evolution

**Normative intent (G9):** The peer implementer is told the three independent version discriminators that coexist in this layer and the rule that none may be half-retired, so it can negotiate and evolve without conflating them.

The **three coexisting version discriminators**, evolving independently:
- **11.1** Mux major/minor version (`Jmux`/`0x01`; §5) — and the unimplemented negotiation (§5.2.1).
- **11.2** The marshal-stream protocol byte (`0x00/0x01/0x02`; §6.1) — the axis on which the §6.4 modern method-identifier is introduced (interacts with OQ-2/§6.5).
- **11.3** The STD-006 `[15]` stream-format version octet (`8F 01 01`; §6.3) — owned by STD-006, cited here.
- **11.4 No-half-retirement [MUST]** — a version/contract is not retired until every consumer is migrated; "some requests on the new path, some framed by the old logic" is the worst outcome (board §2.1 pre-deletion audit).

---

## 12. Conformance and G9 acceptance

**Normative intent (G9):** The standard states its own acceptance test as a falsifiable claim — *a Rust author can build a conformant endpoint from this text with zero access to Java source* — and defines the conformance corpus that makes it checkable.

- **12.1 The G9 acceptance criterion** — stated as a testable claim; T9's mandate is to **try to break peer-implementability**, not to affirm it.
- **12.2 Conformance corpus obligation [→ Appendix C / SOW T10]** — a captured-and-annotated real invocation stream (mux frames + marshal-stream bytes + a return and a throw), the mux message-type boundary vectors, and the invocation version-byte matrix. Sequenced behind the Rust JERI DER work.

---

## Appendix A — The three independent axes [normative]

**Normative intent (G9):** The peer implementer is told that byte-flow direction, request-origination direction, and authorization direction are **independent** and must never be inferred from one another — because every serious transport bug the board found lived in the gap between them.

- **A.1** Byte-flow: bidirectional on every connection.
- **A.2** Request-origination: client→server only in classic JERI (§4.1); server-initiated streams (§9.6) are the deliberate, security-analysed exception.
- **A.3** Authorization: caller = **stream initiator**, not connection initiator; on a server-push the caller is the server. Direction-aware caller resolution is mandatory; a dispatcher that stamps the client's identity on a server-originated request is the confused-deputy attack.

---

## Appendix B — Mux frame companion [OPEN → T2, OQ-5]

**Normative intent (G9):** The peer implementer is given a machine-checkable structural description of every mux frame (bit-layout table + formal grammar, or ASN.1-with-notes) to implement the framing exactly. Form TBD (§5.3); the requirement is peer-implementability, not ASN.1 specifically.

---

## Appendix C — Rust conformance corpus [OPEN → T10]

**Normative intent (G9):** The peer implementer is given committed golden vectors (real captured invocation stream, mux boundary vectors, version-byte matrix) to validate a Rust endpoint against, mirroring STD-011 T5 / the dedup-SOW T5. Not the Rust implementation — the corpus it validates against.

---

## Open-question register

| ID | Question | Owner | Status |
|----|----------|-------|--------|
| OQ-1 | Mux deviation set (client negotiation unimplemented; ping *initiation* NYI but *response* ships; `Mux.java:67` comment error) — resolve each to the wire, decide fate | T2 | **[OPEN]** — §5.2; wire (never comment) authoritative; a peer MUST answer `PING` with `PING_ACK` |
| OQ-2 | The `:1015` version-byte `TODO` — what a conformant peer writes/accepts on mismatch; is the two-`0x00` signal normative | T3 | **[OPEN]** — §6.5 |
| OQ-3 | Method-identifier algorithm & framing | Peter | **[RATIFIED 2026-07-24]** — §6.4; **version-keyed**: `0x00` rejected fail-loud (security — JOSS-without-Atomic), `0x01` retains SHA-1/64-bit (supported through 4.x, sunset 5.0), `0x02` DER version uses SHA-256 (ships in 4.0.0); default width 128-bit also ratified; nothing remains `[PROPOSED]` |
| OQ-4 | `maxScalarBytes` intersection — does §8 ever carry/enforce the STD-011 §3.3 bound | T5 | **[OPEN]** — §8.4; default = hand back, do not adopt orphan |
| OQ-5 | ASN.1 vs bit-layout+grammar companion for the mux | T2 | **[OPEN]** — Appendix B |
| OQ-6 | STD-012 numbering vs STD-010 forward-reference | Peter / merge editor | **[OPEN]** — §0 |

---

## SOW-vs-trunk deltas (recorded so the discrepancy is not silently propagated)

The `SOW-JERI-Standard-Modernization.md` wire claims were spot-checked against trunk 2026-07-24. All load-bearing claims **verified**; the following are minor and non-blocking:
- **Opcode ranges.** The SOW writes ranges (`IncrementRation 0x10–0x1F`, `Abort 0x20–0x23`, `Data 0x80–0x9F`). Trunk defines **base opcodes** (`0x10`, `0x20`, `0x80`) with flag bits masked in (`Mux.java:70-75`); the effective set is not a dense range (e.g. valid ABORT values are `0x20`/`0x22`, low bit always `0`). The bit-layout companion (Appendix B) MUST reflect the flag-bit structure, not literal ranges.
- **Session-width provenance.** The SOW attributes the 8-bit SessionID to a "JGDMS 2019 revision"; the trunk comment (`Mux.java:53-56`) attributes it to release **3.1.1** ("Increased from 0x7F to 0xFF in 3.1.1"). Use the source attribution.
- **Mux.java path.** The SOW/brief cite the mux constants under `net/jini/jeri/connection/mux/`; trunk location is **`org/apache/river/jeri/internal/mux/Mux.java`** (the normative *spec* `mux.html` is under `net/jini/jeri/connection/doc-files/`). Cite the trunk paths.
- **Confirmed exactly:** mux MAGIC/VERSION/opcodes (`Mux.java:59-83`); the invocation version bytes `PREVIOUS_VERSION=0x00`/`VERSION=0x01`/`VERSION_WITH_PRINCIPALS_AND_ACC=0x02` and `MISMATCH=0x00` (`BasicInvocationDispatcher.java:184-300`); the request sequence and user-Subject block (`:824-965`, `:871-881`); the `:1015-1016` version-byte `TODO`; the `0x01`-return/`0x02`-throw discriminator (`:932-964`); the SHA-1 little-endian 64-bit method hash (`Util.java:243-267`); the `marshalThrow`/`DerThrowableForm` seam (`AtomicDerInvocationDispatcher.java:189`, `DerThrowableForm.java`); the DGC Uuid (`BasicObjectEndpoint.java:87`), the batch map already generalized to an Object-keyed DER-capable decode-unit token (`BasicObjectEndpoint.java:233–243` — *not* a JOSS coupling) with the surviving legacy `readObject(ObjectInputStream)` path as the true residual (`BasicObjectEndpoint.java:765–800`); the `ACKNOWLEDGMENT` comment error (`Mux.java:67`).

---

*End of SKELETON. Section inventory follows `SOW-JERI-Standard-Modernization.md` §5 (T1–T10) and §6 (OQ-1..6); house style follows STD-006 v0.13 / STD-010 / STD-011. No production code was written or modified. Full prose is the SOW's T1–T8 deliverable; T9 is the whole-standard board review (transport + security + cross-language/G9 seats); T10 is the conformance corpus.*
