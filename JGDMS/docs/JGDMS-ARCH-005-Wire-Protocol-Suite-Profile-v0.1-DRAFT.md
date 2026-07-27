# JGDMS 4.0 Wire-Protocol Suite — Architecture and Conformance Profile

**Document ID:** JGDMS-ARCH-005
**Status:** Draft (working scaffold for review-board critique)
**Version:** 0.1-DRAFT
**Date:** 2026-07-24
**Applies to:** JGDMS 4.0.0, DirtyChai (JDK fork), and non-JVM JGDMS participants
**Profiles (binds, does not restate):**
- **JERI** — the JGDMS Extensible Remote Invocation transport & invocation stack
  *(cited here as [JERI] = **JGDMS-STD-012** (JERI Transport & Invocation Standard),
  in preparation — v0.1-DRAFT skeleton)*
- **ATOMIC DER** — `JGDMS-STD-006` (ATOMIC DER Wire Format, v0.13-DRAFT) **including its
  NORMATIVE, MANDATORY Appendix C** (Stream Schema Dedup, internal v0.3-DRAFT)
- **DETERMINISTIC CEL** — `JGDMS-STD-011` (DETERMINISTIC CEL Filter/Transform Expression
  Format, v0.1-DRAFT) including its Appendix B (DER Wire Encoding, v0.1-DRAFT)
- **Supporting:** `JGDMS-STD-001` (`@AtomicSerial`), `JGDMS-STD-003` (Multi-Subject
  Identity), `JGDMS-STD-005` (`@SerialEntry`), `JGDMS-STD-008` (`@AtomicSerial`
  Serialization Uncoupling), `JGDMS-STD-009` (Service & Remote-Function Annotation
  Model), `JGDMS-STD-010` (QUIC-TLS JERI Transport)

> **Editorial note (v0.1-DRAFT).** This document is a *profile*, not a component
> standard. It has one job and one non-negotiable structural rule (see §0). It is
> **normative only about composition and conformance** — what versions of the
> component standards fit together, and what "a conformant JGDMS 4.0 peer" is. Every
> protocol rule below is narrated for orientation and then **deferred to its owning
> standard by explicit citation**. Where narrative and an owning standard could be
> read to disagree, the owning standard wins; this document is wrong by definition if
> it ever contradicts one. The deferral ledger (§10.3) lists every normative deferral
> and its target so the boundary is auditable in one place.
>
> **Requirements language.** The keywords **MUST**, **MUST NOT**, **REQUIRED**,
> **SHALL**, **SHALL NOT**, **SHOULD**, **SHOULD NOT**, **RECOMMENDED**, **MAY**, and
> **OPTIONAL** are to be interpreted as described in RFC 2119 / RFC 8174 when, and only
> when, they appear in capitals. "Fail closed" / "fail secure" means: on any ambiguity,
> breach, or unrecognised input, construct no object, carry no traffic, and refuse —
> never fall back to a permissive default.

---

## 0. The one structural rule (read this first)

This document **MUST NOT** duplicate a normative rule from any component standard.

A restated rule is a fourth source of truth. The moment two documents state the "same"
rule in their own words, they begin to drift: one is edited, the other is not, and a
reader — or worse, an implementer — cannot tell which is authoritative. JGDMS keeps
exactly one authoritative statement of every protocol rule, in the standard that owns
it, and treats that document's own claim ("a peer can implement this from this document
alone") as a testable property (the project calls it *G9*).

This profile therefore states normative requirements about **two things only**:

1. **Composition** — which document *versions* compose into "JGDMS 4.0", and how the
   layers connect (what feeds what, in which order, under whose ceilings).
2. **Conformance** — what a *conformant JGDMS 4.0 peer* is: the set of component
   conformances it must simultaneously satisfy, plus the small number of
   cross-standard obligations that live in the seams between components and are owned
   by no single standard.

Every other sentence in this document that sounds like a rule is **narrative**: it
exists to orient a reader who has not memorised eleven standards, and it ends in a
citation to the document that actually decides the matter. If you want to *build* a
JGDMS peer, read the cited standards; this profile tells you which ones, in what
combination, and what it means to have got them all right at once.

The analogy is an Internet-Standard *architecture / profile* RFC — the kind that binds
a family of component RFCs into an interoperable whole (e.g. how a profile RFC names
the TLS, certificate, and transport RFCs a conformant deployment must jointly satisfy
without re-specifying any of them). This document is that, for the JGDMS wire stack.

---

## Abstract

JGDMS (Jini Global Distributed Micro Services) is a security-first
rewrite of the wire stack that Jini / Apache River used to distribute Java objects and
remote calls. Its 4.0 wire protocol is not one format but a suite of three composed
layers, each with its own ratified standard:

- **JERI** — the pluggable transport and remote-invocation framework: connection
  multiplexing, an invocation layering from `Basic` through `Atomic` to `AtomicDer`,
  a constraint model, a family of endpoint types (TCP, TLS, Unix-domain sockets, QUIC),
  a data-only fault carrier, and distributed garbage collection after the Network
  Objects design.
- **ATOMIC DER** — the object-encoding layer: a canonical, schema-carrying,
  bounded-by-construction DER encoding of the `@AtomicSerial` object model, with
  mandatory in-stream schema deduplication designed to be safe over encrypted
  transports.
- **DETERMINISTIC CEL** — the filter/transform layer: a small, non-Turing-complete,
  bounded-by-construction expression language whose evaluation is bit-identical across
  independent implementations and languages.

This profile defines how those three layers compose into "JGDMS 4.0", what a conformant
peer must satisfy across all three at once, and traces one JavaSpace operation through
every layer end to end. It restates no protocol rule; it is normative only about
composition and conformance, and defers every rule to its owning standard by citation.

---

## Status of This Memo

This is a **draft** working document for the JGDMS review board. It profiles the
JGDMS **4.0.0** release train. It is not a finished standard: the component standards
it binds are themselves drafts (ATOMIC DER at v0.13-DRAFT, DETERMINISTIC CEL at
v0.1-DRAFT, the JERI standard in preparation), and this profile will re-version as they
ratify. Sections marked **[PENDING]** await an identifier or decision from a
concurrently-scoped standard; sections marked **[OPEN]** defer a question to a named
owner. Nothing in this document is on-wire-load-bearing in its own right — it produces
no bytes and defines no mechanism; it constrains only the *combination* of mechanisms
the component standards define.

Distribution is unlimited. This document is written to be handed, whole, to a competent
distributed-systems engineer outside the project — every JGDMS-specific term is
explained on first use.

---

## RFC 2119 Terminology

The keywords are as in the Editorial note above. Two project-specific conventions:

- A **conformant JGDMS 4.0 peer** is defined in §6.2; it is the central normative
  object of this document.
- A **component conformance** is the conformance clause of one owning standard (e.g.
  ATOMIC DER §9, DETERMINISTIC CEL §13). This profile requires several of them
  *jointly*; it does not restate any of them.

---

## 1. Introduction & Motivation

### 1.1 What problem the suite solves

Jini's original insight — a service is reached through a *proxy object* that the client
downloads and calls locally, so the network protocol between proxy and service is the
service's private business — remains JGDMS's foundation. Its original *implementation*
carried that proxy, and every argument and return value, using **Java Object
Serialization** (JOSS). Two decades of CVEs later, JOSS is understood as the JVM's
richest single vulnerability class: its stream grammar instantiates objects through
several distinct pathways, resolves classes from bytes, and allocates before it
validates. Guarding one instantiation path leaves others open; the security history is
a history of that whack-a-mole.

JGDMS 4.0 replaces the *encoding beneath the proxy model* — not the proxy model itself —
with a stack designed from a single premise: **the receiver decides what it will
accept, before it accepts anything, from a published schema.** Three design commitments
follow, and they recur at every layer of the suite:

- **Fail-closed.** Any ambiguity, unknown tag, ceiling breach, or schema mismatch is a
  hard reject that constructs no object. There is no permissive fallback anywhere.
- **Canonical.** Every value has exactly one legal byte encoding. Canonical form is not
  a nicety; it is *identity* — it is what lets bytes serve as an equality proxy, a
  content address, and a stable signature input across independent implementations.
- **Bounded by construction.** Every variable-length thing has a schema- or
  profile-declared ceiling checked before allocation; every expression's cost is
  computable before it runs. Safety is a property of the grammar, not of a runtime
  watchdog.

### 1.2 Why a suite and not a format

These three commitments cut across concerns that genuinely differ in kind — moving
bytes reliably and authentically (transport), encoding an object faithfully and
canonically (object format), and evaluating adversary-authored predicates safely
(expression language) — so JGDMS factors them into three standards rather than one
monolith. Each is independently implementable, independently reviewable, and
independently versioned. The cost of factoring is that *composition* itself becomes a
thing that can be got wrong: a peer can be individually correct at all three layers and
still fail to interoperate if it composes the wrong versions or mishandles a seam. This
profile exists to make composition a first-class, checkable object — which is precisely
why it must not restate the rules it composes (§0).

### 1.3 Audience and reading order

A reader new to JGDMS should read §2 (the layering picture) and §7 (one operation
traced through every layer) first; those two sections are narrative and self-contained.
§3–§5 narrate each layer and hand off to its standard. §6 is the normative core (what a
conformant peer is). §8–§10 consolidate security posture, cross-language evidence, and
references.

---

## 2. Architecture Overview

### 2.1 The layering

JGDMS 4.0 is a stack. Bytes enter at the bottom (a socket), are framed and
authenticated by JERI, decoded into objects by ATOMIC DER, delivered to service logic
as `@AtomicSerial` objects, matched or projected as Jini entries, and — where a service
exposes a filter/transform surface — evaluated by DETERMINISTIC CEL. The same stack runs
in reverse on the way out.

```
   ┌──────────────────────────────────────────────────────────────────────────┐
   │  Service / application logic  (JavaSpaces, lookup, transactions, events) │
   └───────────────▲───────────────────────────────────────────▲──────────────┘
                   │ @AtomicSerial objects                     │ match / project
                   │ (validated, typed)                        │ verdict / value
   ┌───────────────┴───────────────┐        ┌──────────────────┴───────────────┐
   │  Entry model & byte-matching  │        │  DETERMINISTIC CEL  [STD-011]    │
   │  @SerialEntry / EntryRep      │◀─────▶│  predicate (filter) /            │
   │  byte-equality template match │        │  transform over the projection   │
   │  [STD-005, ATOMIC DER §7.7.2] │        │  (bounded-by-construction)       │
   └───────────────▲───────────────┘        └──────────────────────────────────┘
                   │ decoded field values (GetArg)
   ┌───────────────┴────────────────────────────────────────────────────────────┐
   │  ATOMIC DER object layer  [STD-006 + Appendix C, NORMATIVE+MANDATORY]      │
   │  • @AtomicSerial object model: validate-before-construct via check(GetArg) │
   │  • canonical DER value tree (one encoding per value); acyclic; no handles  │
   │  • schema-carrying records (schema is the key, not the class)              │
   │  • MANDATORY per-stream schema dedup + stream-format version octet         │
   │  • ceilings checked before allocation (§4.5 + Appendix C §C.8)             │
   └───────────────▲────────────────────────────────────────────────────────────┘
                   │ marshalled arg / return streams
   ┌───────────────┴───────────────────────────────────────────────────────────────┐
   │  JERI invocation layer   [JERI → STD-012 (draft)]                             │
   │  Basic  →  Atomic (@AtomicSerial over JOSS)  →  AtomicDer (@AtomicSerial/DER) │
   │  InvocationLayerFactory selects the pair per-proxy at export                  │
   │  constraints: Integrity, Confidentiality, AtomicInputValidation,              │
   │               MarshallingFormat  (fail-fast, before bytes leave)              │
   │  fault carrier (data-only Throwable) · client-side DGC (SRC-RR-116)           │
   └───────────────▲───────────────────────────────────────────────────────────────┘
                   │ multiplexed request/reply streams
   ┌───────────────┴───────────────────────────────────────────────────────────────┐
   │  JERI mux + endpoints    [JERI → STD-012 (draft); STD-010 for QUIC]           │
   │  connection multiplexing  ·  Endpoint / ServerEndpoint SPI                    │
   │  tcp · ssl (TLS 1.3, SPIFFE/X.509) · uds (Unix-domain) · quic (RFC 9000/9001) │
   └───────────────▲───────────────────────────────────────────────────────────────┘
                   │ authenticated byte stream
   ┌───────────────┴──────────────────────────────────────────────────────────────┐
   │  Discovery  ·  SPIFFE/TLS identity  ·  transport sockets                     │
   └──────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 How the layers hand off (composition, narrated)

- **Transport → invocation.** An `Endpoint`/`ServerEndpoint` pair produces an
  authenticated, multiplexed byte stream. The invocation layer rides on it. Which
  transport is used is the proxy's private business (§3); a non-JVM peer must reproduce
  only the wire-visible contract of whichever endpoint family it speaks (deferred to
  [JERI]; QUIC to [STD-010]).
- **Invocation → object format.** The `AtomicDer` invocation layer marshals a call's
  arguments and return value through the ATOMIC DER codec. The *envelope* (request
  preamble: integrity/atomic flags, user `Subject`s, the `AccessControlContext`) is a
  separate concern currently on a different migration tier (narrated §3.4; deferred to
  ATOMIC DER §5.2/§5.4 and STD-008 §18.3).
- **Object format → entry model.** Jini entries are `@AtomicSerial` value classes; their
  ATOMIC DER encoding is canonical, so template matching reduces to **byte equality**
  on field values (narrated §4.3; deferred to ATOMIC DER §7.7.2, STD-005).
- **Entry model → CEL.** Where a service exposes a filter or computed-value surface,
  DETERMINISTIC CEL evaluates over the *projection* — the class-free field map the
  ATOMIC DER codec decodes from a candidate — never over a reconstructed object
  (narrated §5; deferred to DETERMINISTIC CEL §8.1 and STD-009 §8).

The load-bearing composition fact is that **canonical form is the shared contract that
makes the seams work**: byte-equality matching, content-address digests, signature
stability, schema deduplication, and bit-identical CEL results all rest on "same value →
same bytes", which every layer preserves and no layer is permitted to relax.

---

## 3. JERI (transport & invocation)

> **Narrative for orientation.** Every rule in this section is owned by the JERI
> standard, **JGDMS-STD-012** (JERI Transport & Invocation Standard, in preparation —
> v0.1-DRAFT skeleton). QUIC-specific rules are owned by **[STD-010]**. This profile
> restates none of them.

### 3.1 What JERI is

**JERI** — Jini Extensible Remote Invocation — is JGDMS's remote-call framework. Unlike
Java RMI, its transport and its invocation semantics are *pluggable*: a proxy carries
its own `Endpoint` (how to reach the service) and its own `InvocationLayerFactory` (how
to marshal a call), so the wire protocol between a proxy and its service is chosen at
export time and travels with the proxy. A client does not negotiate a protocol; it
*receives* one, embodied in the proxy it downloads from a lookup service.

### 3.2 The mux and the endpoint families

Beneath invocation, JERI multiplexes many logical request/reply streams over one
connection (`org.apache.river.jeri.internal.mux`). Above the socket, a pluggable
`Endpoint` / `ServerEndpoint` SPI provides the transport families JGDMS ships:

- **`tcp`** — plaintext TCP, for trusted or loopback deployments.
- **`ssl`** — TLS 1.3 with mutual authentication; JGDMS binds peer identity to
  **SPIFFE X.509 SVIDs** and X.500 principals through JSSE.
- **`uds`** — Unix-domain sockets, for same-host isolation without a network hop.
- **`quic`** — QUIC (RFC 9000) with QUIC-integrated TLS 1.3 (RFC 9001). Because QUIC
  provides transport-native stream multiplexing, the `quic` endpoint **retires the
  hand-rolled mux** — the load-bearing structural change of that transport. *(Owned by
  [STD-010]; implementation is a fork of `kwik` with the JDK's JSSE QUIC-TLS engine.)*

### 3.3 The invocation layering: Basic → Atomic → AtomicDer

JGDMS layers invocation semantics as a progression, each selected per-proxy by the
`InvocationLayerFactory`:

- **Basic** (`BasicInvocationHandler`/`Dispatcher`) — the classic JERI call, JOSS-
  marshalled.
- **Atomic** (`AtomicInvocationHandler`/`Dispatcher`) — `@AtomicSerial`
  validate-before-construct discipline, still over the JOSS stream grammar. This removes
  post-parse gadget execution but retains the JOSS parser as residual attack surface.
- **AtomicDer** (`AtomicDerInvocationHandler`/`Dispatcher`) — `@AtomicSerial` over the
  **ATOMIC DER** codec (§4). The dispatcher inherits `BasicInvocationDispatcher.dispatch()`
  and overrides only the argument/return marshal streams; the DER codec ships as mobile
  code in the downloadable `-dl` codebase, provisioned over the *authenticated* bootstrap
  path so that authentication strictly precedes any code download.

### 3.4 Constraints, including format selection

JERI expresses call requirements as `InvocationConstraint`s resolved by the constraint
machinery — `Integrity`, `Confidentiality`, `AtomicInputValidation`, and (new in 4.0)
**`MarshallingFormat`**, which names the object encoding (`ATOMIC_DER`, wire identifier
`"JGDMS-STD-006/ATOMIC-DER"`; or `JOSS`). A `MarshallingFormat` requirement is a
policy assertion checked **fail-fast, before bytes leave the client**; it is the
firewall that prevents a security-sensitive endpoint from being silently downgraded to
JOSS. *(Owned by ATOMIC DER §5.1–§5.4; a naming drift with STD-008's older `WireFormat`
constraint is a G8 finding — §10.4.)*

### 3.5 The fault carrier

When a remote call throws, the exception travels back as **data, not as a reconstructed
object graph**: a `Throwable` is carried as a safe subset — class *name* string,
message, stack trace, suppressed exceptions, and cause (nested, context-tagged) —
with the historical reflective-constructor reconstruction and any `Class`/`Permission`
fields **excluded** as gadget-adjacent. *(Owned by ATOMIC DER §7.6.)*

### 3.6 Distributed garbage collection

JERI retains distributed GC using the **Network Objects reference-listing collector**
(Birrell et al., SRC-RR-116): the owner of a remote object keeps a *set* of holder
identities (not a count), enabling idempotent `dirty`/`clean` and crash recovery. In
4.0 DGC is **DER-native** per the JOSS-rejection posture: the `dirty`/`clean` calls ride
DER like any other call, the batch map is already generalized to an Object-keyed,
DER-capable decode-unit token with an end-of-decode callback (the one surviving legacy
`readObject` path is removal work, not a retained JOSS coupling), and DGC authenticates
as the **node's SPIFFE workload identity** rather than a captured user `Subject`.
*(Owned by STD-008 §6, grounded in SRC-RR-116.)*

---

## 4. ATOMIC DER (object encoding)

> **Narrative for orientation.** Every rule in this section is owned by **`JGDMS-STD-006`
> (v0.13-DRAFT)** and its **NORMATIVE, MANDATORY Appendix C** (Stream Schema Dedup,
> internal v0.3-DRAFT). The `.asn1` modules in `docs/asn1/` are the machine-checkable
> counterparts (compiler-validated with asn1tools). This profile restates none of it.

### 4.1 What ATOMIC DER is, and why the name

**ATOMIC DER** is the wire format's proper name; it names the `@AtomicSerial` object
model it encodes. Generic "DER" continues to mean X.690 Distinguished Encoding Rules
(the ASN.1 encoding: one canonical byte form per value), which ATOMIC DER uses as its
substrate and does not modify. The document identifier stays `JGDMS-STD-006` for family
consistency. *(Owned by STD-006 title note.)*

The `@AtomicSerial` model (STD-001) already separates *validation* from *construction*:
a class declares its serial shape (`SerialForm[] serialForm()`) and validates field
values in a `check(GetArg)` method that runs **before any field is assigned**. Crucially,
`@AtomicSerial` defines validation *contracts*, not an encoding — the encoding is
pluggable. JOSS was the first encoding beneath it; ATOMIC DER is the canonical successor.

### 4.2 Canonicality is identity; the schema is the key

Two consequences of "one canonical byte form per value" do the heavy lifting across the
whole suite:

- **Canonical form as a value-equality proxy.** Because a collection field's element
  order is fixed from the declared type's own contract (preserved where iteration order
  is deterministic; canonicalised — octet-sorted — where it is not), *serialized
  byte-equality tracks value-equality*. Java serialization cannot do this: a `HashSet`
  and a `TreeSet` of the same elements serialize to different bytes. This property is
  what makes byte-match entry templates, content-address digests, and stable signatures
  possible. *(Owned by STD-006 §2.1, §3.8, §7.7.2, §7.8.)*
- **Data independence from code — the schema is the key, not the class.** A JOSS byte
  stream without its originating class is opaque and permanently unrecoverable. An
  ATOMIC DER `MarshalledInstance` carries the `@AtomicSerial` schema (`serialForm()`)
  *inside* it, so every field name and value is decodable by any DER-capable tool in any
  language, with no class loading and no JVM. Data outlives code. *(Owned by STD-006
  §2.3, §7.8.)*

### 4.3 Schema-carrying records and chain integrity

An `@AtomicSerial` object's wire form is a hierarchy of per-class field records plus a
**schema chain** describing them, addressed by `schemaDigest` (a content hash). On
decode, the schema is verified against its embedded bytes before any use, and an absent,
corrupt, chain-inconsistent, or digest-mismatched schema is a hard reject — there is no
"fall back to the local schema" step. *(Owned by STD-006 §7.8, §12.4; conformance item
13. Note a Merkle-cross-check gap for nested records flagged by Appendix C — §10.4.)*

### 4.4 Mandatory stream dedup, and the CRIME-safe design

A bulk reply — say 100 JavaSpace entries of one class — would otherwise carry 100
byte-identical schema chains. Appendix C defines a **per-stream, by-reference dedup**:
the first occurrence of a chain travels in full; every later occurrence travels as its
`schemaDigest` reference; receivers reconstitute byte-identical full records. This is
**mandatory** — the sole stream form of the released DER object-stream format, ratified
2026-07-21 ("there isn't a non-dedup and dedup version with differing bytes"). A stream
lacking its stream-format **version octet** or its dedup grammar is malformed and MUST
be rejected. *(Owned by STD-006 Appendix C §C.5, §C.7, §C.9; version octet §C.5.2
[PROPOSED as `8F 01 01`].)*

The design is deliberately **not** a general-purpose compressor, and that is a security
decision. General compression over an encrypted channel is a CRIME-class oracle: if
attacker-influenced plaintext and secret plaintext share a compression context, ciphertext
*length* leaks secret content. Appendix C is admissible over TLS/mTLS by construction
because it obeys three rules — no cross-value compression context, values are never
compressed, and dedup operates only over the **public structural layer** (schema chains
are the class's field structure, not a secret). Length therefore reveals only which
*classes* appear, never any value. *(Owned by STD-006 Appendix C §C.3; origin in
`SOW-Outrigger-DER-Only-JOSS-Rejection.md` §9.4, decision 6.)*

*Measured effect (informative).* A 100-entry bulk decode shrank **22180 → 9378 bytes
(42.3%)** even with a tiny 125-byte chain; larger chains shrink more. On the Outrigger
persistence baselines, scalar/String entries shrank ~13% (log) / ~16% (snapshot) on the
JOSS→DER flip alone, and `@AtomicSerial`-valued fields ~4% (schema carriage dominates —
exactly what dedup and storage interning reclaim). *(Sources:
`memory: jgdms-outrigger-der-only-refactor` T2 measurement;
`U0-Outrigger-JOSS-Persistence-Baselines-2026-07-21.md`.)*

### 4.5 Ceilings as a philosophy

Every variable-length field has a declared maximum (`SIZE` constraint or profile
ceiling) checked **before allocation**; the object graph is **acyclic** with **no handle
table and no back-reference mechanism** anywhere — which eliminates two classic
deserialization vulnerability classes (reference theft from a partially-constructed
object, and cyclic-reference DoS) at the format level, because neither is representable.
The dedup layer's own ceilings (`maxDistinctChainsPerStream`=256, `maxChainRecords`=64,
`maxChainBytes`=65536, `maxDedupTableBytes`=1048576) compose with the base §4.5 table and
are metered *during* decode. *(Owned by STD-006 §3.7, §4.5, Appendix C §C.8.)*

---

## 5. DETERMINISTIC CEL (filter / transform)

> **Narrative for orientation.** Every rule in this section is owned by **`JGDMS-STD-011`
> (v0.1-DRAFT)** and its Appendix B (DER Wire Encoding). This profile restates none of
> it. *(The name is **DETERMINISTIC CEL**; the "DETERMINISTIC CELL" spelling that
> appears in some early requests is a confirmed typo — STD-011 title note, Peter,
> 2026-07-20.)*

### 5.1 What it is, and what it deliberately is not

Where a service exposes a *filter* (does this candidate match?) or a *computed value*
(derive this bounded result from a candidate's fields), JGDMS does **not** ship bytecode
or a smart proxy for it. It ships an expression in a small, closed, non-Turing-complete
language modeled on Google's CEL (Common Expression Language). There are no loops, no
comprehensions, no macros, no user-defined functions, no variables, no assignment, no
I/O, no state. Anything needing those is out of scope *by construction, not by policy*.
*(Owned by DETERMINISTIC CEL §1, §5.4.)*

### 5.2 Bounded by construction (a theorem, not an aspiration)

The safety claim is stated as a theorem with a proof by structural induction over the
closed AST node inventory: for every well-formed expression and every candidate,
evaluation **terminates**, is **side-effect free and deterministic**, completes within a
**statically computable cost bound** (no candidate needed to compute it), and allocates
memory bounded by expression size and input scalar sizes only. No watchdog, fuel meter,
or timeout is required for these properties to hold. The closed-world premise is
enforced at three independent layers (grammar, wire-decode reject-unknown, and
verification-time allowlist), so no malformed encoding can smuggle in a construct the
theorem does not cover. *(Owned by DETERMINISTIC CEL §3, §10, §12.)*

### 5.3 Correctly-rounded transcendentals; cross-language determinism

The Survey-zoot transform use case (bearing/elevation/distance → local vector
components) needs `sin`/`cos`/`tan`/`asin`/`acos`/`atan`/`atan2`. IEEE-754 mandates
correct rounding for `+ − × ÷ sqrt` but **not** for transcendentals, so ordinary libms
differ in the last bit — which would make two independent evaluators return different
vectors for the same input and silently break any downstream digest, signature,
byte-match, or cache key. STD-011 therefore **requires correctly-rounded**
implementations of those seven functions (crlibm / CORE-MATH class), rejecting both a
tolerance regime (it would break "one encoding per value") and dropping the functions
(it would gut the use case). This binds Java and Rust evaluators to ship
correctly-rounded routines — a real, priced cost, not a discovered one. *(Owned by
DETERMINISTIC CEL §7.4, §7.5.)*

### 5.4 Two forms, one abstract syntax; it rides ATOMIC DER

An expression has a **textual authoring form** (a one-directional subset of CEL's own
syntax, so CEL editor tooling carries over) and a **canonical interchange form** (the
DER-encoded AST, Appendix B). The canonical form is what crosses the wire, is verified,
and is evaluated; it extends ATOMIC DER with expression-node tags and inherits ATOMIC
DER's acyclicity, definite-length, and one-encoding-per-value guarantees. Evaluation
runs over the ATOMIC DER **projection** (`className → (fieldName → typed value)`)
decoded by the fail-closed codec — never over a reconstructed object. *(Owned by
DETERMINISTIC CEL §5.1, §8.1, Appendix B; type substrate deferred to STD-006 §3.12.)*

---

## 6. Composition & Conformance Profile (NORMATIVE)

This is the normative core of the document. Everything above is narrative; everything
below is a requirement about *combination*, and defers each protocol rule to its owner.

### 6.1 Composing versions (NORMATIVE)

A deployment claiming to be **JGDMS 4.0** SHALL compose exactly these document versions
(or their ratified successors that preserve the same composition seams):

| Layer | Owning standard | Version this profile binds |
|---|---|---|
| Transport & invocation | [JERI] = STD-012 | **JGDMS-STD-012** — v0.1-DRAFT (in preparation) |
| QUIC transport | STD-010 | v0.1-DRAFT |
| Object encoding | STD-006 (ATOMIC DER) | v0.13-DRAFT **+ Appendix C (internal v0.3), which is NORMATIVE and MANDATORY** |
| Object model | STD-001 (`@AtomicSerial`), STD-008 (uncoupling) | as cited by STD-006 |
| Entry model | STD-005 (`@SerialEntry`) | with ATOMIC DER §7.7.2 |
| Filter/transform | STD-011 (DETERMINISTIC CEL) + Appendix B | v0.1-DRAFT |
| Identity | STD-003 (Multi-Subject) | as cited by STD-006 §7.1 |

Composition rules, each **normative and each deferred**:

1. **Appendix C is not optional.** A conformant peer's DER object streams SHALL
   implement Appendix C. This is not this profile's rule to make — it is ratified in and
   owned by **STD-006 header / Appendix C §C.9**; stated here only so no reader treats
   dedup as a negotiable mode. There is **one** stream encoding.
2. **The stream-format version octet gates every object stream.** A stream lacking it,
   or carrying an unknown version, SHALL be rejected. *(Deferred to STD-006 Appendix C
   §C.5.2.)*
3. **DETERMINISTIC CEL composes only over the ATOMIC DER projection**, decoded by the
   fail-closed STD-006 codec under its ceilings; it SHALL NOT be evaluated over any
   other reader. *(Deferred to STD-011 §8.1 + STD-009 §8.)*
4. **Format selection is fail-fast and cannot silently downgrade.** An endpoint
   requiring `MarshallingFormat.ATOMIC_DER` SHALL reject a non-DER call before
   transmission; on a transport without confidentiality+integrity, a DER-requiring
   endpoint SHALL require the constraint (no negotiable fallback). *(Deferred to STD-006
   §5.1, §5.4.)*
5. **Canonical form is preserved end to end.** No layer SHALL relax "one encoding per
   value": byte-match matching (STD-006 §7.7.2), content-address digests (§7.8),
   signature TBS (§7.4/§7.7.7), schema dedup (Appendix C §C.3), and bit-identical CEL
   results (STD-011 §13) all depend on it jointly.

### 6.2 What a conformant JGDMS 4.0 peer is (NORMATIVE)

A **conformant JGDMS 4.0 peer** is a participant that **simultaneously** satisfies every
one of the following component conformances, each in full and none restated here:

- **ATOMIC DER conformance** — STD-006 **§9** (all 14 items), **including** its
  MANDATORY Appendix C conformance (**§C.11**): observable equivalence of dedup and
  full forms, the coverage minima, and the rejection corpus.
- **`@AtomicSerial` conformance** — STD-001 (validate-before-construct via
  `check(GetArg)`), as required by STD-006 conformance item 9.
- **Entry conformance** — STD-005 (`@SerialEntry`) with ATOMIC DER §7.7.2 byte-match
  matching, for any peer that produces or consumes Jini entries.
- **Invocation & transport conformance** — the JERI standard **JGDMS-STD-012** (in
  preparation), and STD-010 for any peer speaking the `quic` endpoint.
- **DETERMINISTIC CEL conformance** — STD-011 **§13** and Appendix B **§B.11**, for any
  peer that authors, transmits, verifies, or evaluates a filter/transform (its
  bit-identical corpus, ceilings, and rejection vectors). A peer that neither produces
  nor consumes CEL expressions is not required to implement an evaluator, but SHALL
  still reject a CEL-bearing wire structure it does not implement (fail-closed), never
  skip or default it.

A peer that satisfies some but not all applicable component conformances is **not** a
conformant JGDMS 4.0 peer, regardless of how well it does the parts it implements. The
whole point of the profile is that partial correctness does not compose.

### 6.3 The G9 doctrine (NORMATIVE, about the standards themselves)

Each component standard SHALL remain implementable **from its own document alone** — the
project's *G9* property. This profile SHALL NOT be, and SHALL NOT become, a required
input to implementing any single layer: a JERI implementer needs [JERI], a codec
implementer needs STD-006 + Appendix C, an evaluator implementer needs STD-011 —
**never this document**. This profile is required only to *compose* correctly and to know
*what conforming across all layers at once means*. If any component rule can only be
understood by reading this profile, that is a defect in the component standard to be
fixed there, not here. This is the structural guarantee that keeps the profile from
becoming a fourth source of truth (§0).

### 6.4 Conformance seams owned by no single standard (NORMATIVE)

A small number of obligations live in the *seams between* components and are genuinely
this profile's to state, because no single owning standard can (each would have to
reach into another's scope). These are the only original normative requirements in this
document:

- **S1 — Version-octet ↔ MarshallingFormat agreement.** A stream selected by
  `MarshallingFormat.ATOMIC_DER` (STD-006 §5.1) SHALL carry the Appendix C version octet
  (§C.5.2); a peer SHALL NOT accept a DER-selected stream that lacks it, nor a
  version-octet-bearing stream selected as JOSS. *(Composes STD-006 §5.1 with Appendix C
  §C.5.2.)*
- **S2 — Projection reader identity.** A CEL evaluation and an entry byte-match over the
  same candidate SHALL observe the *same* fail-closed STD-006 decode of that candidate —
  not two independently-configured decoders with divergent ceilings. *(Composes STD-011
  §8.1 with STD-006 §7.7.2 / §9.)*
- **S3 — Signature substrate agreement.** Any signed record (STD-006 §7.4/§7.7.7) whose
  fields include a CEL expression or a deduped schema chain SHALL sign over the
  reconstituted canonical full form, not the by-reference or version-octet-framed stream
  bytes. *(Composes STD-006 §7.4/§7.7.7 with Appendix C §C.5.4 round-trip law.)*

Each S-item is a *composition* constraint — it says how two components must agree — and
each cites both sides. None restates either side's internal rule.

---

## 7. A Worked Example — one JavaSpace `write` and `take`, traced through every layer

This section is **informative**; the outcomes it asserts are normative in the cited
standards. It follows a single logical operation through the whole stack so a reader can
see the layering do its work. Scenario: a survey data collector `write`s an
`OrderEntry`-style observation entry into a JavaSpace (Outrigger), and a consumer later
`take`s it back by template.

### 7.1 Discovery and binding (before any call)

The collector already holds the space's proxy — obtained earlier from a lookup service
(Reggie). Discovery itself is a JGDMS operation: a multicast announcement/request
exchange over the least-authenticated position, deliberately treated as **advisory,
replayable hints** — trust is established only at the authenticated unicast step, where
the returned registrar proxy is deserialized under `@AtomicSerial` discipline. The
space proxy carries its own `Endpoint` (say `ssl`, TLS 1.3 + SPIFFE SVID) and its own
`AtomicDerILFactory`. *(Deferred: multicast advisory posture, STD-006 §7.7.7;
authenticated unicast, [JERI]; proxy-carries-codec, STD-006 §5.3.)*

### 7.2 The `write` call leaves the client

1. **Invocation layer.** `write(entry, txn, lease)` is dispatched by
   `AtomicDerInvocationHandler`. Before any byte leaves, JERI checks constraints
   fail-fast: `Confidentiality` and `Integrity` are satisfied by the `ssl` endpoint;
   `MarshallingFormat.ATOMIC_DER` matches the proxy's configured codec. A mismatch here
   would throw `UnsupportedConstraintException` *on the client*, before transmission.
   *(Deferred: STD-006 §5.1.)*
2. **Entry marshalling.** The `entry` is an `@AtomicSerial` value class (STD-005
   `@SerialEntry`). Its fields are marshalled to an **`EntryRecord`**: a `schemaHash`
   (identifying the class), a `hashAlgorithm` version tag, and an order-significant
   `fieldValues` sequence — each non-null field a canonical DER `OCTET STRING`.
   *(Deferred: STD-006 §7.7.2.)*
3. **Object stream + dedup + version octet.** The argument stream opens with the
   Appendix C stream-format **version octet**. The entry's `@AtomicSerial` schema chain
   travels **in full on its first occurrence**; were this a bulk write of many entries,
   every subsequent identical chain would travel as a `schemaDigest` **reference**. The
   codec constructs the marshal stream, so dedup is mandatory by construction — there is
   no non-dedup path to select. *(Deferred: STD-006 Appendix C §C.5.2, §C.5.3, §C.9.)*
4. **Mux + transport.** The framed request rides a multiplexed stream over the
   authenticated TLS 1.3 connection (or, on a `quic` endpoint, over a QUIC stream with no
   separate mux). *(Deferred: [JERI]; STD-010 for QUIC.)*

### 7.3 The `write` call arrives at the space

5. **Bounded decode, fail-closed.** The server decodes strictly from the published ASN.1
   modules. Every `SIZE`/ceiling is checked **before allocation** (base §4.5 table plus
   the Appendix C chain ceilings, metered during decode). Any unknown tag, non-minimal
   length, ceiling breach, or a schema chain whose `schemaDigest` fails to verify against
   its embedded bytes is a **hard reject — no object constructed**. There is no handle
   table to exploit and no permissive fallback. *(Deferred: STD-006 §3.7, §4.5, §7.8,
   §12.4, Appendix C §C.7.)*
6. **Validate-before-construct.** The decoded field values populate a `GetArg`; the
   entry class's `check(GetArg)` runs and enforces invariants **before any field is
   assigned**. Only then is the entry a constructed object the space will store.
   *(Deferred: STD-001; STD-006 conformance item 9.)*
7. **Persistence.** Outrigger writes the entry to its reliable log. Under DER the entry
   is ~13–16% smaller than the JOSS baseline for scalar/String fields; schema-carrying
   value fields ride storage interning (the persistence-side sibling of stream dedup).
   *(Sources: U0 baselines; SOW-Outrigger-DER-Only-JOSS-Rejection §5.)*

### 7.4 The `take` — template match, and (optionally) a CEL filter

8. **Template as byte-match.** The consumer's `take(template, txn, timeout)` carries an
   **`EntryTemplate`**: `null` fields are wildcards; non-null fields are exact matches.
   Because DER is canonical, "exact match" is **byte equality** on the `present OCTET
   STRING` values — a non-JVM Registrar matches with a byte-string comparison and never
   decodes the field type. *(Deferred: STD-006 §7.7.2.)*
9. **Optional CEL filter/transform.** If the consumer supplied a DETERMINISTIC CEL
   predicate (e.g. `sampleCount >= 30 && quality > 0.9`), the space evaluates it over the
   candidate's **projection** — the class-free field map from the *same* fail-closed
   STD-006 decode (seam S2). Evaluation is total (a value or one of the closed error
   values), terminates within a statically-computed cost bound, and yields the **same
   verdict on any conformant evaluator, in any language** (seam: bit-identical, STD-011
   §13). A Survey-zoot *transform* filter would instead compute vector components using
   correctly-rounded transcendentals, so its DER-encoded result is byte-identical across
   evaluators. *(Deferred: STD-011 §8.1, §9, §13; §7.5 for transcendentals.)*
10. **Return value.** The matched entry marshals back through the identical DER path
    (version octet, first-occurrence-full schema chain, canonical fields). The consumer
    decodes it under the same fail-closed discipline and `check(GetArg)`.

### 7.5 The fault path

11. **If the call throws** — a `TransactionException`, say — the exception returns as
    **data**: class-name string, message, stack trace, suppressed, and cause (nested,
    context-tagged), through the same bounded DER decode. No reflective reconstruction
    runs; no `Class` or `Permission` object is materialised from the wire. The receiver
    reconstructs an inert carrier, not a live gadget. *(Deferred: STD-006 §7.6.)*

Every step above is a *narration* of a rule owned elsewhere. The value of the trace is
seeing the three commitments of §1.1 — fail-closed, canonical, bounded — reappear at
each layer, and seeing the seams (S1 version-octet/format agreement, S2 shared
projection reader, S3 signature substrate) exactly where the profile says they are.

---

## 8. Security Considerations

The suite's security posture is the *sum* of its components' postures plus the
composition seams; the consolidated shape is:

- **Fail-closed everywhere.** No layer has a permissive fallback. Unknown tag, unknown
  enumerant, ceiling breach, schema mismatch, unknown stream version, unknown CEL node
  or function — every one constructs nothing and refuses. *(STD-006 §3, §9; Appendix C
  §C.7.4; STD-011 §9, §12; Appendix B §B.6.)*
- **Canonical form as contract.** One encoding per value is not cosmetic; it is the
  substrate of matching, content addressing, signatures, dedup, and cross-language
  determinism. Relaxing it anywhere silently breaks all of them. *(STD-006 §3.8, §7.7.2;
  STD-011 §7.5 rejecting tolerances.)*
- **Bounded decode.** Everything variable-length is capped before allocation; the graph
  is acyclic with no handle table, removing reference-theft and cyclic-DoS at the format
  level. CEL cost is bounded before evaluation with no runtime gate required. *(STD-006
  §3.7, §4.5; STD-011 §3, §10.)*
- **CRIME-safe dedup.** In-stream compaction is admissible over encryption because it is
  structure-only, value-independent, and single-context — length reveals only which
  classes appear, never a value. General compression over encryption remains excluded.
  *(STD-006 Appendix C §C.3.)*
- **Reconstruction-door discipline.** The wire carries values and bounds, never
  behaviour: no codebase annotations in-stream (authenticated `CodebaseAccessor` is the
  one code channel; authentication precedes download), no reflective `Throwable`
  reconstruction, no `Class`/`Permission` objects from bytes (a `Permission`, if ever
  needed, is textual string form re-parsed through the trusted policy parser). *(STD-006
  §7.6, §8; §7.5/§7.6.)*
- **JOSS-rejection posture.** `@AtomicSerial`-over-JOSS removes post-parse gadgets but
  keeps the JOSS parser as residual surface; the migration deliberately DER-ises the
  least-trusted edges first (envelope, discovery, broad-aperture servers, clients,
  persistence last), and the end state removes JOSS from the wire entirely, retaining a
  read-only atomic-JOSS engine only in a one-shot offline migrator outside the
  network-facing TCB. *(STD-006 §5.4, §5.5; STD-008.)*
- **Honesty notes carried forward, not swept.** The suite does **not** claim: timing
  side-channel resistance for CEL evaluation (T7's question); confidentiality of
  schema-bearing DER (readable by whoever holds the bytes); freshness of multicast
  announcements (advisory, replayable). These are stated in the owning standards and are
  properties this profile inherits, not resolves. *(STD-011 §3.3; STD-006 §7.7.7.)*

The seams (§6.4) are themselves security boundaries: S1 prevents a format-confusion
downgrade, S2 prevents a split-decoder discrepancy between what is matched and what is
evaluated, S3 prevents signing the wrong (by-reference or version-framed) bytes.

---

## 9. Cross-Language Implementations

The G9 property — implementable from the document alone — is the enabling claim for
non-JVM participation. Two lines of evidence:

- **Rust (planned).** A conformant Rust JERI ATOMIC DER peer, including the Appendix C
  dedup layer and a DETERMINISTIC CEL evaluator, is the intended second implementation
  (STD-011 T4; the Rust JERI DER port). The correctly-rounded transcendentals bind to
  the CORE-MATH project's binary64 routines. Rust's ADTs model the closed CHOICE/tag
  registries and reject-unknown-enumerant semantics natively. *(Sources:
  `der-rust-collection-mapping.md`; STD-011 §7.5.)*
- **Haskell (feasibility-proven).** A feasibility investigation concluded a
  conformance-passing Haskell peer is technically feasible in all four layers with no
  Haskell-specific blocker — the genuinely hard parts are scope (the JERI transport),
  strictness discipline against a lazy language's fail-closed decode obligations, and
  the (absent-but-trivial) CORE-MATH FFI binding. Its verdict is explicitly *not* that
  the peer should be built, but that **the standards' G9 claim survives contact with a
  third, very different language** — the real deliverable of the exercise. *(Sources:
  `investigation-haskell-atomic-der-dedup-cel-feasibility.md`;
  `der-haskell-collection-mapping.md`.)*

Neither is an argument to build a third peer for a lean team; both are evidence that the
composition profiled here is genuinely language-neutral, which is the property that lets
a non-JVM AI inference service — or any polyglot participant — join a JGDMS mesh by
implementing a documented protocol rather than reverse-engineering a JVM.

**IANA note (informative).** ATOMIC DER's OID arc and any media-type registration
depend on a Zeus Project Services Pty Ltd Private Enterprise Number (one PEN covers
ATOMIC DER, DETERMINISTIC CEL, and future formats via self-managed sub-arcs); the
`999999` placeholder in STD-006 §4.4 MUST be replaced before v1.0. *(Source:
`IANA-Registrations.md`.)*

---

## 10. References, Deferral Ledger, and G8 Findings

### 10.1 Normative references

- **[STD-006]** JGDMS-STD-006 — ATOMIC DER Wire Format, v0.13-DRAFT, **including
  Appendix C** — Stream Schema Dedup, internal v0.3-DRAFT (NORMATIVE, MANDATORY per the
  STD-006 header). Machine-checkable counterparts: `docs/asn1/JGDMS-STD-006-v0.13.asn1`,
  `docs/asn1/JGDMS-STD-011-AppendixB-v0.1.asn1` (asn1tools-validated).
- **[STD-011]** JGDMS-STD-011 — DETERMINISTIC CEL Filter/Transform Expression Format,
  v0.1-DRAFT, including Appendix B — DER Wire Encoding, v0.1-DRAFT.
- **[STD-001]** JGDMS-STD-001 — `@AtomicSerial` Compliance Standard.
- **[STD-003]** JGDMS-STD-003 — Multi-Subject Identity Architecture.
- **[STD-005]** JGDMS-STD-005 — `@SerialEntry` Compliance Standard.
- **[STD-008]** JGDMS-STD-008 — `@AtomicSerial` Serialization Uncoupling.
- **[STD-009]** JGDMS-STD-009 — Service & Remote-Function Annotation Model.
- **[STD-010]** JGDMS-STD-010 — QUIC-TLS JERI Transport, v0.1-DRAFT.
- **[JERI]** JGDMS-STD-012 — JERI Transport & Invocation Standard, in preparation
  (v0.1-DRAFT; deliverables: `SOW-JERI-Standard-Modernization.md` and the
  `JGDMS-STD-012-JERI-Transport-v0.1-DRAFT.md` skeleton). Grounded here in the codebase
  (`net.jini.jeri.*`, `AtomicDer*`), STD-008 §6 (DGC), STD-010 (QUIC), and the
  UDS/SSLEngine SOWs.
- **[RFC2119]/[RFC8174]** Requirements-language keywords.
- **[RFC9000]/[RFC9001]** QUIC transport / TLS for QUIC (via STD-010).
- **[SRC-RR-116]** Birrell, Nelson, Owicki, Wobber — *Network Objects* (the DGC
  reference-listing collector JERI implements). `docs/SRC-RR-116.pdf`.

### 10.2 Informative references

- `U0-Outrigger-JOSS-Persistence-Baselines-2026-07-21.md` (measured DER shrink).
- `SOW-DER-Stream-Schema-Dedup.md` (dedup origin; the 22180→9378 B measurement trail).
- `SOW-Outrigger-DER-Only-JOSS-Rejection.md` §9.4 (CRIME decision; storage-interning
  sibling).
- `investigation-haskell-atomic-der-dedup-cel-feasibility.md`,
  `der-rust-collection-mapping.md`, `der-haskell-collection-mapping.md` (cross-language
  evidence).
- `IANA-Registrations.md` (PEN / media-type path).
- `der-type-model-and-element-rule.md` (the wire type algebra pinned by STD-006 §3.12).

### 10.3 Deferral-citation ledger

Every normative statement in this profile that narrates a protocol rule, and the owning
standard + section it defers to. (Composition/conformance requirements original to this
profile — §6.1 items, §6.2, §6.3, §6.4 S1–S3 — are marked **[this profile]**; they are
about combination, not protocol, and each still cites both sides it composes.)

| # | Narrated here | Deferred to (owner) |
|---|---|---|
| D1 | JERI mux, endpoint families, invocation layering, constraints | **[JERI] = STD-012** (in preparation); QUIC → STD-010 |
| D2 | `MarshallingFormat` selection, fail-fast, no silent downgrade | STD-006 §5.1, §5.4 |
| D3 | Data-only `Throwable` fault carrier (safe subset) | STD-006 §7.6 |
| D4 | Client-side DGC, Network Objects collector | STD-008 §6; SRC-RR-116 |
| D5 | ATOMIC DER naming; DER-not-BER canonicality | STD-006 title note, §3 |
| D6 | Canonical form as value-equality proxy; collection ordering | STD-006 §2.1, §3.8, §7.7.2 |
| D7 | Schema-is-the-key / data independence | STD-006 §2.3, §7.8 |
| D8 | Schema-carrying records, chain integrity, fail-secure resolution | STD-006 §7.8, §12.4 |
| D9 | Mandatory stream dedup + version octet | STD-006 Appendix C §C.5, §C.7, §C.9 |
| D10 | CRIME-safe dedup (three construction rules) | STD-006 Appendix C §C.3 |
| D11 | Ceilings before allocation; acyclic, no handle table | STD-006 §3.7, §4.5; Appendix C §C.8 |
| D12 | `@AtomicSerial` validate-before-construct (`check(GetArg)`) | STD-001; STD-006 conformance item 9 |
| D13 | Entry byte-match template matching | STD-006 §7.7.2; STD-005 |
| D14 | CEL non-Turing-complete, no state/loops/macros | STD-011 §1, §5.4 |
| D15 | Bounded-by-construction theorem | STD-011 §3, §10, §12 |
| D16 | Correctly-rounded transcendentals; no tolerance | STD-011 §7.4, §7.5 |
| D17 | CEL two forms; rides ATOMIC DER; projection contract | STD-011 §5.1, §8.1, Appendix B; STD-006 §3.12 |
| D18 | Multicast discovery advisory/replayable; unicast trust | STD-006 §7.7.7; [JERI] |
| D19 | JOSS-rejection migration tiering & end state | STD-006 §5.4, §5.5; STD-008 |
| D20 | Codebase over authenticated `CodebaseAccessor`, no in-stream annotations | STD-006 §8 |
| C1 | Which versions compose into JGDMS 4.0 | **[this profile] §6.1** (cites all owners) |
| C2 | What a conformant peer is (joint component conformance) | **[this profile] §6.2** (cites STD-006 §9/§C.11, STD-011 §13/§B.11, [JERI], STD-005, STD-001) |
| C3 | G9 doctrine (each layer implementable from its own doc) | **[this profile] §6.3** |
| C4 | Seam S1 — version-octet ↔ MarshallingFormat agreement | **[this profile] §6.4** (composes STD-006 §5.1 + Appendix C §C.5.2) |
| C5 | Seam S2 — single shared projection reader | **[this profile] §6.4** (composes STD-011 §8.1 + STD-006 §7.7.2/§9) |
| C6 | Seam S3 — signature over reconstituted canonical full form | **[this profile] §6.4** (composes STD-006 §7.4/§7.7.7 + Appendix C §C.5.4) |

### 10.4 G8 findings — constituent-document tensions observed while narrating

Per the one structural rule (§0) and board guidance G3/G8, discrepancies found between
constituent documents while narrating them are **reported, not silently harmonized**.
This profile takes no position on their resolution; it records them for the owners.

1. **Format-selection constraint named two ways.** STD-006 §5.1 (ratified 2026-07-10)
   names the format-selection constraint **`MarshallingFormat`** (identifier
   `"JGDMS-STD-006/ATOMIC-DER"`). STD-008 §7 and §14 still call it a **`WireFormat`**
   `MethodConstraint` (values `DER`/`ANY`, `JAVA` removed). These are the same mechanism
   under two names; STD-008 predates the rename. **Owner: STD-008** (align §7/§14 to
   `MarshallingFormat` and the current identifier string).

2. **`schemaDigest` present vs dropped depends on which STD-006 section you read.**
   STD-006 §7.8 carries a `schemaDigest` field and conformance item 13 requires verifying
   it; Appendix C §C.5.3 (RATIFIED) **drops** `schemaDigest` from the *stream-form*
   record (derivable both arms; a restate-and-disagree surface removed), sound given the
   chain-completeness rule. Both are internally consistent, but a reader of §7.8 *alone*
   would encode a field the mandatory stream form omits. The composed truth requires
   reading Appendix C — exactly the composition subtlety this profile flags rather than
   restates. **Owner: STD-006** (a forward-pointer from §7.8 to Appendix C §C.5.3 would
   close it).

3. **Base-standard Merkle cross-check gap (self-flagged in Appendix C).** Appendix C
   §C.7.3 / §C.13.2 records a **[FLAG]**: STD-006's nested-record (P2) decode path skips
   the Merkle cross-check that Appendix C's dedup table integrity relies on, ledgered
   with a **[PATCH]** against the base standard. This is a genuine base-vs-appendix
   tension in an integrity-load-bearing path. **Owner: STD-006** (apply the [PATCH]).

4. **STD-011 internal inconsistency on CEL function wire-ids (self-flagged in Appendix
   B).** Appendix B §B.8.1 records that STD-011 §7.2's "one row, one wire id" claim is
   inconsistent with its own table, and §B.8.2 that `contains`/`startsWith`/`endsWith`
   have **no pinned function-id** in STD-011 at all. Appendix B proposes a pinned table
   to close both gaps. **Owner: STD-011** (ratify the §7.2 table + the function-id
   assignments).

5. **Cross-document citation drift (already corrected downstream, recorded for the
   trail).** STD-010's provenance note records that the QUIC design SOWs cite the ACC
   reducing-domain transport as STD-006 **"§7.3"** when the correct citation is
   **§7.2** (§7.3 is `DigestCodeSourceRecord`); STD-010 corrected it inline. Noted so the
   wrong citation is not re-propagated by a future reader of those SOWs. **Owner: the
   QUIC design SOWs** (informative).

6. **Version octet still [PROPOSED] while dedup is RATIFIED mandatory.** The 2026-07-21
   ruling makes dedup the mandatory stream form (RATIFIED), but the concrete
   stream-format **version octet** encoding (`8F 01 01`) in Appendix C §C.5.2 is still
   marked **[PROPOSED]**. The mechanism is mandatory; its exact octet is not yet ratified.
   §6.1 item 2 and seam S1 depend on it, so this profile inherits the [PROPOSED] status.
   **Owner: STD-006 Appendix C** (ratify the octet form).

None of these are contradictions *this profile* introduces; all are pre-existing tensions
between the owned standards, surfaced by the act of composing them. Reporting them here —
rather than papering over them with a harmonized restatement — is the profile doing its
one job correctly (§0).

---

*End of JGDMS-ARCH-005 v0.1-DRAFT. This document produces no bytes, defines no mechanism,
and is not required to implement any single layer (§6.3). It is authoritative only about
composition (§6.1) and joint conformance (§6.2), and defers every protocol rule to its
owning standard (§10.3).*
