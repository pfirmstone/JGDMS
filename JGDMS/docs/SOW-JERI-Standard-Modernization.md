# Scope of Work — JERI Transport & Invocation Standard modernization (the last non-G9 layer)

- **Drafted:** 2026-07-24.
- **Status:** DRAFT — scoping pulled forward from wk 2026-07-27 at Peter's direction
  (2026-07-24). This SOW is the primary deliverable: a task breakdown to **produce** a
  normative JERI standard. It ships alongside a companion skeleton of the standard itself
  (`JGDMS-STD-012-JERI-Transport-v0.1-DRAFT.md`) so the review agent can assess scope
  completeness before any section is written to full prose. No production code, and no
  normative prose beyond the skeleton, is written by this SOW.
- **Proposed identifier:** **JGDMS-STD-012** (justification in §0). The document title is
  *JERI Transport & Invocation Standard*.
- **Origin:** the one layer of the JGDMS stack not written to the G9 "implementable from
  the doc alone" standard. STD-006 (+ Appendix C stream dedup) and STD-011 (+ Appendix B)
  and their ASN.1 modules (`docs/asn1/`) already let a non-JVM peer implement the *object
  and expression* layers from the docs alone. The **mux / invocation / constraint /
  endpoint / DGC layer is the gap a Rust JERI DER peer hits first** — and that peer gates
  STD-011's Rust T4 evaluator (`SOW-CEL-Filter-Format.md` T4) and the GLS instrument
  federation. This standard closes the gap.
- **Substrate being modernized (all stale, none authoritative — G3):**
  - the original Sun-era Jini ERI specification, which survives in-repo as HTML javadoc,
    not as a standard: the multiplexing-protocol spec
    (`jgdms-jeri/src/main/java/net/jini/jeri/connection/doc-files/mux.html`, **Jini ERI
    Multiplexing Protocol version 1.1**, revised by JGDMS in 2019 to an 8-bit SessionID)
    and the `net/jini/jeri/package.html` / `Overview.html` package specs;
  - the DGC design substrate — **SRC-RR-116** (`docs/SRC-RR-116.pdf`, Birrell 1993,
    *Network Objects*, the paper behind JERI/RMI DGC);
  - the `OutboundRequest`/`InboundRequest`/`Connection`/`ServerConnection` javadoc, which
    the JERI-transport board seat rules **is** today's real spec (the guarantees live in
    prose there, not in the interface signatures).
- **Companions (read first):**
  - `docs/board-guidance/transport-jeri.md` — the JERI-transport reviewer's distilled
    guidance (the three-axes model, the DGC-ack contract, the pre-deletion audit, the
    ephemeral-client reframe). This SOW's hard questions are largely that document's
    findings promoted to normative work items; the review board for this standard **is**
    that seat.
  - `JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md` + `JGDMS-STD-006-Appendix-C-Stream-Schema-Dedup-v0.1-DRAFT.md`
    — the object-stream substrate the invocation layer opens (the mandatory version octet
    `8F 01 01` and the per-marshal-stream dedup table lifetime this standard must cite,
    not re-specify).
  - `JGDMS-STD-008-AtomicSerial-Serialization-Uncoupling-v0.1-DRAFT.md` — §15/§16 DER
    object-stream framing and the item-tag registry; **§6 is the DGC modernization
    design-of-record** (DER dirty/clean round-trip already proven —
    `BasicObjectEndpointDerDgcTest`).
  - `JGDMS-STD-010-QUIC-JERI-Transport-v0.1-DRAFT.md` — a transport **instance**; under
    this standard STD-010 is re-read as a *profile* of §4's endpoint SPI and §9's family
    model, not a peer standard (see §0 on the numbering inversion).
  - `JGDMS-STD-011-CEL-Filter-Expression-Format-v0.1-DRAFT.md` §3.3 (the half-discharged
    `maxScalarBytes`, folded in §6 open questions and handed to a STD-006/STD-011
    editorial pass where it does not intersect this layer).
  - `SOW-QUIC-JERI-Transport.md`, `SOW-Unix-Domain-Socket-JERI-Transport.md`,
    `SOW-UDS-JERI-Increment-2-Peer-Authentication.md`, `SOW-RemoteEvent-Source-DER-Encoding.md`
    (the fault/return-carrier consumers).

---

## 0. The identifier decision (STD-012) — proposed, not guessed

STD-001 through STD-011 are allocated (`docs/` inventory, 2026-07-24: 001 AtomicSerial
compliance, 002 safe-codebase audit, 003 multi-subject identity, 004 policy-file syntax,
005 SerialEntry compliance, 006 DER wire format, 007 codebase-compatibility interchange,
008 serialization uncoupling, 009 service/@RemoteFunction annotation model, 010 QUIC-JERI
transport, 011 CEL filter format + Appendix B). **The next free number is 012.**

**Recommendation: JGDMS-STD-012.** Two notes for the ratifier, neither blocking:

1. **The numbering reads inverted and that is fine.** STD-010 (QUIC-JERI) is a *single
   transport instance*; STD-012 is the *general model* of which QUIC, TCP, TLS, and UDS
   are all instances (§4, §9). A general model numbered *after* one of its own instances
   is only cosmetically odd — standards are numbered in authoring order, not dependency
   order (STD-006 is depended on by STD-008/011 which are lower-and-higher around it
   already). **[PROPOSED]** STD-010 gains a one-line forward-reference at merge ("a
   profile of STD-012 §4/§9"); STD-010's normative content is not moved.
2. **Do not renumber to "reserve" a block.** There is no house precedent for reserving
   numbers; the appendix-lettering question in Appendix C (§C.1.3 `[OPEN → merge editor]`)
   is the closest analogue and it was left to the merge editor, not pre-allocated. Same
   posture here.

**[OPEN → Peter]** ratify 012, or assign otherwise.

---

## 1. What this closes, precisely

Every other layer of the JGDMS remoting stack can be implemented by a non-JVM peer from
the standards alone. The transport/invocation layer cannot: its normative content is
scattered across (a) an HTML mux spec that is *mostly* accurate but has un-catalogued
implementation deviations, (b) interface javadoc that the board seat treats as
authoritative but which no peer implementer would know to read as a spec, (c) a 1993
research paper for DGC, and (d) tacit contracts (DGC-ack, delivery-status, half-close,
the caller-identity direction rule) enforced by the implementation being replaced rather
than by any written interface. A Rust peer building a conformant endpoint today would have
to reverse-engineer the Java source — the exact failure G9 exists to prevent.

This SOW produces **one normative standard** that:

- states the **layering model** and the **endpoint/transport SPI** as one model with
  named, testable contracts (not interface signatures);
- **reconciles** the mux protocol spec (v1.1 already exists in-repo — this is a
  reconcile-and-catalogue-deviations task, *not* a write-from-scratch task, §3) into the
  standard, and lifts the four invisible transport contracts out of javadoc into
  normative text;
- specifies the **invocation wire sequence** exactly (the version byte, integrity /
  atomic-validation / user-Subject / ACC blocks, the method hash, argument marshalling,
  and the `0x01`-return / `0x02`-throw response discriminator), and pins its boundary with
  the DER object stream (the STD-006 mandatory version octet and the per-marshal-stream
  dedup table lifetime — **cited**, not re-specified);
- specifies the **fault-marshalling contract** (the `marshalThrow`/`unmarshalThrow` seam
  and the DER `DerThrowableForm` carrier) as part of the invocation contract, which it was
  not in the Sun spec;
- specifies the **constraint model**: how `InvocationConstraints`, `MarshallingFormat`
  (`ATOMIC_DER`), and integrity/confidentiality requirements are carried and *enforced* at
  endpoints, and the constraint-honesty rule (a transport MUST NOT assert a security
  property it has not verified);
- **modernizes DGC** off SRC-RR-116 as **DER-native** (per the 4.0.0 JOSS-rejection
  posture), adopts STD-008 §6 as the design-of-record, and frames the surviving legacy
  `readObject(ObjectInputStream)` path in `BasicObjectEndpoint` as **removal work**, not a
  retained coupling;
- states **versioning/evolution** across the three distinct version discriminators that
  coexist in this layer, and the **G9 acceptance criterion**.

It does **not** re-specify STD-006/008/011; it cites them and specifies only the seams.

---

## 2. Verified ground truth (surveyed against trunk, 2026-07-24 — G3)

The following are confirmed against source; the standard's sections quantify over them and
the skeleton already carries them as anchors.

- **The mux spec already exists and is largely current.** `mux.html` is *Jini ERI
  Multiplexing Protocol version 1.1*: 8-byte connection headers
  (`ClientConnectionHeader`/`ServerConnectionHeader` with `initialRation`), first-byte
  message-type ranges (NoOperation `0x00`, Shutdown `0x02`, Ping `0x04`, PingAck `0x06`,
  Error `0x08`, IncrementRation `0x10–0x1F`, Abort `0x20–0x23`, Close `0x30`,
  Acknowledgment `0x40`, Data `0x80–0x9F`), 256 sessions (8-bit SessionID, JGDMS 2019
  revision), client-only session establishment (Data with `open` flag), per-session
  inbound/outbound rations for flow control. **Named deviations to capture** (§5): the
  client-side major-version negotiation is unimplemented; **ping *response* is implemented**
  (`handlePing`→`asyncSendPingAck`, `Mux.java:908–914,1075–1081`) while only ping
  *initiation* machinery is stubbed (`Mux.java:1094`, `NYI: rest of ping machinery`); and
  `Mux.java:67`'s comment for the ACKNOWLEDGMENT bit-pattern is in error (the comment
  disagrees with the table — a G3 source-vs-source discrepancy the standard must resolve
  to the wire, not the comment).
- **The invocation wire sequence is exact and in javadoc** (`BasicInvocationDispatcher`
  `dispatch`, `:824–965`). A marshal-stream **protocol version byte** (`0x00`/`0x01`/`0x02`;
  any other value → two `0x00` bytes written back = version-mismatch signal), then
  version-dependent blocks: `0x00` → integrity byte; `0x01` → integrity + atomic-input-
  validation bytes; `0x02` → integrity + validation + one-or-more user-Subject blocks +
  a serialized `AccessControlContext`. The user-Subject block wire format is documented
  (`:871–881`: `subjectCount` u16, then per Subject `principalCount` u16, then per
  principal `classNameLength`/`classNameBytes`/`nameLength`/`nameBytes`). Then the DER
  marshal input stream is created, the **method hash** read, constraints checked, args
  unmarshalled, `invoke` called; on normal return a **`0x01`** byte precedes
  `marshalReturn`, on exception a **`0x02`** byte precedes `marshalThrow`. **[FLAG]**
  `BasicInvocationDispatcher.java:1015–1016` carries a live `TODO` questioning whether the
  version byte "was to be written in spec, or just 0x00" — an un-pinned point this
  standard MUST settle (§6, §11).
- **The method hash is RMI-derived SHA-1 in shipped 3.x; SHA-256 is the ONLY modern scheme**
  (`org/apache/river/jeri/internal/runtime/Util.java:243–267`): the hash River/JGDMS 3.x
  actually released (RMI heritage) is the first 64 bits of `SHA` (SHA-1) over
  `writeUTF(methodName + JVMS-descriptor)`, accumulated little-endian into a `long`. SHA-1
  here is a *structural identifier*, not a security primitive. **Ratified (Peter,
  2026-07-24):** the modernized standard defines the method identifier as the leftmost *N*
  bytes of **SHA-256**(name + JVMS §4.3.3 descriptor) as the **only** scheme — there is **no
  SHA-1 legacy lane**. The 3.x SHA-1/64-bit hash is recorded as historical fact and
  **deliberately severed** under the 4.0.0 clean-break posture (consistent with the JOSS
  rejection and the DiscoveryV1 removal), *not* retained for 3.x interop. Pre-4.0 invocation
  version bytes are **rejected fail-loud** via the existing MISMATCH path (two `0x00` bytes,
  skeleton §6.5). The identifier field is **version-keyed and length-delimited**, sized to
  accommodate up to the full 64-byte SHA-512 output with zero framing change (note
  **SHA-512/256**'s 64-bit-CPU throughput advantage); the collision bound is set by the
  identifier **field width**, not the digest algorithm. Full detail in §6 OQ-3
  **[RATIFIED]** and the skeleton's §6.4.
- **The DER object-stream boundary is settled by STD-006 Appendix C.** Dedup is the
  **mandatory** stream form (not negotiated); the stream begins with the version octet
  `8F 01 01` (tag `[15]`); the dedup table lifetime is **per marshal stream** = one
  `DerObjectStreamCodec` lifetime = the argument stream *or* the return stream of one
  remote call. This standard cites those facts as the invocation↔object boundary; it does
  not restate the dedup rules.
- **The four invisible transport contracts are real and consumer-anchored**
  (`board-guidance/transport-jeri.md` §1): the **DGC AcknowledgmentSource** contract
  (fires only after the receiver's `RequestDispatcher` *processed* the request, not on
  byte delivery — 13 consumers incl. `BasicObjectEndpoint` and an independent HTTP-transport
  re-implementation), `OutboundRequest.getDeliveryStatus()` (the at-most-once / retry
  signal, fail-closed to "maybe processed"), per-direction **half-close** (early response
  readable while request still writing), and mux **flow-control** rationing. None is
  visible in the SPI signatures; all must become normative text.
- **DGC is DER-native; the residual is a legacy path to remove.** STD-008 §6 is the
  modernization design-of-record and the DER dirty/clean round-trip is already proven
  (`BasicObjectEndpointDerDgcTest`). The batch map is **already generalized** to an
  Object-keyed decode-unit token that is DER-capable (`BasicObjectEndpoint.java:233–243`) —
  it is **not** a JOSS coupling. The actual residual is narrower: the **surviving legacy
  `readObject(ObjectInputStream)` path** (`BasicObjectEndpoint.java:765–800`), which the
  standard's DGC section frames as **removal work** completing the DER-native transition per
  STD-008 §6 — not a coexisting JOSS path to document or preserve. It does not re-open the
  whole DGC design.
- **The endpoint families are already one SPI.** `Endpoint`/`ServerEndpoint`/
  `OutboundRequest`/`InboundRequest`/`Connection`/`ServerConnection` are implemented by
  TCP, SSL/TLS-1.3, UDS, and QUIC alike; `Connection.getChannel()` (optional
  `SocketChannel`) is the seam that distinguishes blocking (`SSLSocket`) from
  channel-backed (UDS/QUIC) transports. §9 specifies the families as **profiles** of one
  model.

---

## 3. Design positions taken here (each section's author refines; board reviews)

- **Reconcile, do not rewrite, the mux.** The mux wire format is version 1.1 and shipping;
  the standard imports it as normative (transcribed to G9 markdown + an ASN.1/bit-layout
  companion in `docs/asn1/`, matching the STD-006 precedent), and its *only* new content
  over `mux.html` is (a) the deviation catalogue (§2), (b) the four invisible contracts
  promoted from javadoc, and (c) the version-negotiation resolution. Rewriting the frame
  format from scratch is a **non-goal** and a regression risk.
- **The javadoc contracts are normative source.** Per the board seat: `OutboundRequest`/
  `InboundRequest` javadoc *is* the spec for at-most-once, early-response, close-vs-abort,
  and delivery-status. The standard transcribes each paragraph into RFC-2119 text and
  cites the javadoc as its origin — it does not paraphrase loosely.
- **One endpoint model, transports are profiles.** TCP/TLS/UDS/QUIC are specified as
  instances of §4's SPI with per-profile deltas (confidentiality claims, `getChannel()`
  nullability, server-initiated streams for ephemeral clients). STD-010 becomes the QUIC
  profile by reference.
- **The three axes are a first-class section.** Byte-flow direction, request-origination
  direction, and authorization direction are independent and get their own normative
  treatment (Appendix A of the standard), because every serious transport bug the board
  found lived in the gap between them, and a peer implementer given only signatures will
  conflate them.
- **DGC is modernized, not redesigned.** SRC-RR-116 supplies the model (dirty/clean lease
  reference counting); STD-008 §6 supplies the DER carriage; this standard states the
  contract and names the residual coupling to remove. It does not invent a new DGC.
- **Cite the object layer, own the seam.** The DER version octet and dedup lifetime belong
  to STD-006 Appendix C; this standard specifies only *where the invocation layer enters
  the object stream* and *that one call = two independent marshal streams (args, return),
  each with its own dedup table*.

---

## 4. Non-goals

- **Not re-specifying STD-006/008/011.** The object stream, nested-record framing, dedup
  rules, and CEL grammar are cited, never restated. Where a seam is under-pinned in those
  documents (e.g. STD-011 §3.3 `maxScalarBytes` candidate-projection side), this standard
  either names the intersection precisely or hands it back — it does not adopt the orphan.
- **Not rewriting the mux wire format** (§3) — reconcile only.
- **Not designing new transports.** TCP/TLS/UDS/QUIC exist; this standard specifies the
  model they already instantiate. A future transport is a new *profile*, authored against
  §4/§9, not a change to this standard.
- **Not redesigning DGC** — modernize the write-up, pin the coupling (§3).
- **Not the Rust implementation.** Spec + conformance-corpus obligation only (T10),
  sequenced behind the Rust JERI DER work exactly as `SOW-CEL-Filter-Format.md` T4 and
  `SOW-DER-Stream-Schema-Dedup.md` T5 are.
- **Not modernizing the old ERI *discovery* spec.** DiscoveryV1 was removed in 4.0.0 as
  insecure (V2-only); discovery is out of this standard's transport/invocation scope —
  `SOW-discovery-transport-scoping.md` owns it.
- **Not touching the marshal-stream protocol *values* on the wire.** The `0x00/0x01/0x02`
  version byte, the `0x01/0x02` response discriminator, and the mux message-type table are
  transcribed as-is; if any is to change (OQ-3's method-hash proposal, the `TODO` at
  `:1015`), that is a *versioned* change with its own board round, not folded into the
  transcription.

---

## 5. Execution plan — task breakdown

Effort per project convention (security → `xhigh`, wire/class-resolution → `max`,
mechanical/transcription → `medium`, test-corpus → `xhigh`); model tier per the 2026-07-20
cost-tiering guidance; orchestration follows *fresh author → board gate*. Every review
dispatch cites `docs/board-guidance/transport-jeri.md` **and** `JGDMS-Board-Reviewer-Guidance.md`
in the initial brief (the transport seat is mandatory on this standard). This is a doc-only
SOW: every task's deliverable is normative (or conformance) text, no production code.

### Summary

| Task | Deliverable | Author (agent · effort) | Model tier | Review | Depends |
|------|-------------|-------------------------|-----------|--------|---------|
| **T1** · Layering model + endpoint/transport SPI (§3, §4) | The reference stack (object/DER → invocation → request/response SPI → mux/connection → transport/endpoint → byte stream) and the one SPI model with named contracts: `Endpoint`/`ServerEndpoint`/`OutboundRequest`/`InboundRequest`/`Connection`/`ServerConnection`, `ServerCapabilities`, the `getChannel()` seam, and the **three-axes** appendix (bytes / origination / authz as independent). Transcribe the `Out/InboundRequest` javadoc guarantees to RFC-2119. | general-purpose · **MAX** (wire/contract-resolution; the doc every other section hangs off) | top-tier | **parallel board** (transport seat + security seat) | — |
| **T2** · Mux protocol section (§5) | Reconcile `mux.html` v1.1 into the standard (G9 markdown + `docs/asn1/` bit-layout companion): connection headers, message-type table, session lifecycle, flow-control rationing. Promote the four invisible contracts (DGC-ack, delivery-status, half-close, flow control) to normative text. **Catalogue the deviations**: unimplemented client version negotiation, ping *initiation* NYI (ping *response* is implemented), the `Mux.java:67` ACKNOWLEDGMENT comment error (resolve to the wire). Flag the mux as the highest-risk / least-documented area. | general-purpose · **XHIGH** (the hardest section — the least-documented, highest-blast-radius contracts live here) | top-tier | **parallel board** (transport seat lead) | T1 |
| **T3** · Invocation layer section (§6) | The exact request/response byte sequence (version byte + integrity/validation/Subject/ACC blocks + method hash + args; `0x01`-return / `0x02`-throw). Pin the DER object-stream boundary: entry point, the STD-006 mandatory version octet `8F 01 01` **(cited)**, and **dedup table lifetime = per marshal stream (one call = two streams)**. Settle the `:1015` version-byte `TODO`. Specify the **ratified** method-identifier design (OQ-3): SHA-256 leftmost-*N* as the **only** scheme (**no SHA-1 legacy lane**; pre-4.0 version bytes rejected fail-loud via the MISMATCH two-`0x00`-byte path), the version-keyed **length-delimited** identifier field sized for SHA-512, default width **128-bit [RATIFIED — Peter, 2026-07-24]**. | general-purpose · **XHIGH** (wire-format + security seam) | top-tier | **parallel board** | T1 |
| **T4** · Fault-marshalling contract (§7) | `marshalThrow`/`unmarshalThrow` as part of the invocation contract (new vs the Sun spec): the DER `DerThrowableForm` carrier, `ServerException`/`ServerError` wrapping, checked-exception→`UnmarshalException` wrapping, the null-method case, and the round-trip law for fault carriage over the DER stream. | general-purpose · **HIGH** | mid-tier | security-literate reviewer + transport seat spot-check | T3 |
| **T5** · Constraint model + enforcement (§8) | How `InvocationConstraints`, `MarshallingFormat` (`ATOMIC_DER`), and integrity/confidentiality are carried and **enforced** (`InboundRequest.checkConstraints`, the integrity/validation bytes, `MethodConstraints`). The **constraint-honesty rule** (a transport MUST NOT assert an unverified security property — the board's UDS `Confidentiality.YES` over-claim war story) as normative text. Name the `maxScalarBytes` intersection (OQ-4). | general-purpose · **XHIGH** (security-defining) | top-tier | **parallel board** (security seat lead) | T1 |
| **T6** · Endpoint families as profiles (§9) | TCP, SSL/TLS-1.3, UDS, QUIC each specified as a §4 profile with its deltas: confidentiality/integrity claims, `getChannel()` nullability (blocking `SSLSocket` vs channel-backed), and the **server-initiated-stream / ephemeral-client** model (caller-identity inversion, confused-deputy guard) folded from STD-010 §4.6/§4.7 and the board §3.3/§3.4. STD-010 becomes the QUIC profile by reference. **Give HTTP/HTTPS an explicit disposition**: it uses **HTTP framing, not the mux**, and **independently re-implements `AcknowledgmentSource`** — specify it as a distinct **non-mux profile** (its independent DGC-ack re-implementation is itself proof the ack is a transport-SPI obligation, not a mux artifact), per skeleton §9.5. | general-purpose · **HIGH** | mid-tier | **parallel board** (transport seat) | T1, T2 |
| **T7** · DGC section (§10) | Specify DGC **DER-native** per the 4.0.0 JOSS-rejection posture: adopt **STD-008 §6** as design-of-record, state the DGC-ack contract's role (from §5), and frame the residual **legacy `readObject(ObjectInputStream)` path** (`BasicObjectEndpoint.java:765–800`) as **removal work** completing the DER-native transition — the batch map is *already* generalized to an Object-keyed, DER-capable decode-unit token (`BasicObjectEndpoint.java:233–243`) and is **not** the residual. Address the deserialization-uncoupling blocker explicitly. | general-purpose · **XHIGH** (last Java-Serialization path; distributed-correctness) | top-tier | **parallel board** (transport + security seats) | T1, T3 |
| **T8** · Versioning/evolution + conformance/G9 (§11, §12) | The **three coexisting version discriminators** (mux major/minor; marshal-stream protocol byte; DER stream-format version octet) and their independent evolution rules; the **no-half-retirement MUST** (board §2.1); the G9 acceptance criterion stated as a testable claim; the conformance-corpus definition. | general-purpose · **HIGH** | mid-tier | **parallel board** | T2, T3, T7 |
| **T9** · Whole-standard board review | Adversarial read of the assembled standard against `board-guidance/transport-jeri.md` §6 checklist: three-axes separation, DGC-ack reproduction, delivery-status fail-closed, half-close, flow-control non-double-accounting, server-caller direction-awareness, constraint-honesty, no-half-retirement, and **G9 peer-implementability** (could a Rust author build a conformant endpoint from this text with zero access to Java source?). Consolidated fix list, applied before ratification. | general-purpose · **XHIGH** | top-tier | **parallel board** (2–3 seats: transport, security, cross-language/G9) | T1–T8 |
| **T10** · Rust conformance-corpus obligation | Not the Rust implementation (future, gated on Rust JERI DER). A committed corpus the future peer validates against: a captured-and-annotated real invocation stream (mux frames + marshal-stream bytes + a return and a throw), the mux message-type boundary vectors, and the invocation version-byte matrix. Mirrors STD-011 T5 / dedup-SOW T5. | general-purpose · **MEDIUM** | mid-tier | single reviewer (transport seat) | T2, T3, T4 |

### Sequencing

- **Now:** **T1** — the long pole; §3/§4 define the SPI vocabulary every other section
  uses. Nothing downstream is well-defined without it.
- **After T1:** **T2** (mux — start early, it is the hardest and gates T6/T8), **T3**
  (invocation), **T5** (constraints) — parallelizable.
- **After T3:** **T4** (faults), **T7** (DGC — also needs T1).
- **After T1, T2:** **T6** (endpoint profiles).
- **After T2, T3, T7:** **T8** (versioning + conformance).
- **After T1–T8:** **T9** (whole-standard board), then **T10** (corpus) once the wire
  sections are stable.

### Notes

- **T2 is the one to guard hardest, and it is a reconcile task, not a green-field one.**
  The danger is *not* getting the frame format wrong (it is transcribed from a shipping
  v1.1 spec) — it is (a) failing to lift an invisible contract out of javadoc (the DGC-ack
  war story: a naive reader deletes the mux and ships a silently-wrong distributed GC), and
  (b) transcribing a source-vs-source discrepancy (the `Mux.java:67` comment) without
  resolving it to the wire. Both are caught by the board seat's own §2.1 pre-deletion
  audit discipline; brief the author with it.
- **T3 and T5 are the security-defining pair.** T3 owns the exact byte sequence and the
  object-stream seam; T5 owns whether a claimed constraint is honoured. A bug in either is
  a wire-format or confused-deputy security bug, per this codebase's history. Hold both to
  the BAE/`SubProcessDynamicPolicy` adversarial standard.
- **The G9 test is the acceptance test, not a nicety (T9).** The whole reason this
  standard exists is that the Rust JERI DER peer cannot be built from the current
  artifacts. T9's mandate is explicitly to try to break peer-implementability, the same
  way T5 in the dedup SOW made "same mechanism for Rust and Java" falsifiable.
- **Do not let STD-010 drift.** T6 re-reads STD-010 as a profile; if T6 finds STD-010
  says something §4 contradicts, that is a T6 finding to reconcile (probably STD-010 is
  right and §4 must accommodate it — STD-010 was board-reviewed against real QUIC), not a
  silent divergence.
- **Method-hash change (OQ-3) is out-of-band from transcription.** T3 records the SHA-1
  hash as the *historical* 3.x-shipped identifier (severed under the clean-break posture —
  §2 / OQ-3) and specifies SHA-256-leftmost-*N* as the **sole** modern identifier
  (RATIFIED); realizing it is a versioned wire change, never folded into the modernization
  transcription (§4). Pre-4.0 version bytes are rejected fail-loud, not SHA-1-hashed.

---

## 6. Open questions (authors decide within their section; board reviews; Peter ratifies the starred)

1. **OQ-1 · The `mux.html` deviation set — resolve to the wire, then decide fate.**
   Client version negotiation is unimplemented; **ping *initiation* is stubbed
   (`Mux.java:1094`) but ping *response* is implemented** (`handlePing`→`asyncSendPingAck`,
   `Mux.java:908–914,1075–1081`); and `Mux.java:67`'s ACKNOWLEDGMENT comment disagrees with
   the message-type table. For each: is the standard normative-as-shipped (document the gap
   — e.g. a conformant peer **MUST answer a received `PING` with `PING_ACK`** even though it
   need not *originate* one, since the responder path ships), or does the standard mandate
   the unimplemented behaviour (creating implementation work outside this doc-only SOW)? T2
   proposes; the wire, never the comment, is authoritative (G3).
2. **OQ-2 · The `:1015` version-byte `TODO`.** `BasicInvocationDispatcher` carries an
   unresolved question of whether the marshal-stream version byte was spec'd to be written
   or is "just 0x00." T3 must settle what the standard mandates a conformant peer to write
   and to accept, and whether the two-`0x00`-bytes mismatch signal is normative.
3. **OQ-3 [RATIFIED — Peter, 2026-07-24] · Method-identifier algorithm & framing.** Fully
   resolved on the axes below; T3 specifies the ratified design. The first-modern-version
   default width (128-bit) is now also ratified (Peter, 2026-07-24); nothing in OQ-3 remains
   `[PROPOSED]`.
   - **The load-bearing analytical point, recorded so the spec does not mislead:** the
     collision bound is set by the identifier **field width** (birthday at 2^(N/2)), **not**
     by the digest algorithm. Upgrading SHA-1→SHA-256 *without widening the field* changes
     nothing measurable in collision resistance; it removes an audit finding, not a real
     exposure. And the identifier is a *structural* selector, not a security primitive — a
     collision lets a caller name a different method of the same interface, but the
     dispatcher still checks constraints and access on the **resolved** method
     (post-resolution authorization), and the inputs are developer-declared, not
     attacker-chosen. Hashing is memoised at proxy/dispatcher construction
     (`Util.getMethodHash` `TableCache`), so **wire bytes are the only recurring cost** of
     a wider field.
   - **(1) Algorithm (RATIFIED).** The method identifier is the **leftmost *N* bytes of
     SHA-256**(method name + JVMS §4.3.3 descriptor) — the **only** scheme. There is **no
     SHA-1 legacy lane.** The SHA-1/64-bit hash River/JGDMS 3.x actually released (RMI
     heritage) is recorded as historical fact and **deliberately severed** under the 4.0.0
     clean-break posture (consistent with the JOSS rejection and the DiscoveryV1 removal),
     described as the old structural selector only, not preserved as a supported interop
     lane. Pre-4.0 invocation version bytes are **rejected fail-loud** via the OQ-2 / §6.5
     MISMATCH path (two `0x00` bytes), never silently hashed with SHA-1.
   - **(2) Framing (RATIFIED design constraint).** The method-identifier *field* is
     **version-keyed and length-delimited**: *N* is a parameter of the wire **version**, not
     of the frame format. The framing MUST accommodate identifier widths up to the full
     **64-byte SHA-512** output **with zero framing change**, so a future version byte can
     adopt SHA-512 — or **SHA-512/256** (noted for its 64-bit-CPU throughput advantage) —
     without touching the frame. This deliberately mirrors the STD-006 stream version-octet
     evolution philosophy: evolve the payload under a stable, self-describing frame.
   - **(3) Default width for the first modern version [RATIFIED — Peter, 2026-07-24].**
     **128 bits** (SHA-256 truncated): ~2^64 birthday bound, +8 bytes/call vs the old 64-bit
     hash, negligible. Full-width identifiers are **deliberately not** the default — 64
     bytes/call to select one of typically <100 interface methods is unjustified by the
     threat model (developer-declared inputs, post-resolution authorization).
   - **(4) STD-009 processor-assigned ids** remain the structural alternative for
     annotation-processed services (`@RemoteFunction`/`@JiniService` already mints stable
     ids); the length-delimited field accommodates them under the **same** framing — so this
     is "explicit id where one exists, SHA-256-leftmost-*N* otherwise", not an either/or.
   Out-of-band from the transcription (§4): §6 records SHA-1 as the historical 3.x-shipped
   hash (severed, not a supported lane) and specifies SHA-256-leftmost-*N* as the **sole**
   method identifier; the change is realized by a modern marshal-stream version (interacts
   with OQ-2), and pre-4.0 version bytes are rejected fail-loud rather than SHA-1-hashed.
4. **OQ-4 · The `maxScalarBytes` intersection (cross-standard, half-discharged).**
   STD-011 §3.3's `maxScalarBytes` is enforced on the expression-literal side but the
   candidate-projection side and the STD-006 §4.5 `[PATCH]` ceiling row are unpinned. Does
   this layer's constraint enforcement (§8) ever *carry or enforce* that bound — e.g. as an
   `InvocationConstraint` on a `@RemoteFunction` call, or as an argument-stream ceiling? T5
   determines the intersection precisely. **Default disposition: hand it back** to a
   STD-006/STD-011 editorial pass unless T5 finds the invocation layer is the actual
   enforcement point, in which case §8 pins it. Do **not** let this standard silently adopt
   an orphan bound.
5. **OQ-5 · ASN.1 companion for the mux.** STD-006 and STD-011 Appendix B have `docs/asn1/`
   modules; the mux frame format is bit-packed, not TLV, so an ASN.1 module is an awkward
   fit. T2 decides: a bit-layout table + a formal grammar (ABNF-style) as the G9 companion,
   vs a best-effort ASN.1-with-notes. The G9 requirement is peer-implementability, not
   ASN.1 specifically.
6. **OQ-6 · Where the standard lives relative to STD-010.** Confirm the §0 recommendation
   (012, STD-010 keeps its content and gains a forward-reference). Editorial; Peter or the
   merge editor.

---

*End of DRAFT. Scoping pulled forward 2026-07-24 at Peter's direction; ground truth
surveyed against trunk (mux `v1.1`, the invocation dispatcher wire sequence, the SHA-1
method hash, the DGC coupling, the STD-006 Appendix C boundary). No production code was
written or modified in producing this document; the only artifact beyond this SOW is the
companion skeleton `JGDMS-STD-012-JERI-Transport-v0.1-DRAFT.md`.*
