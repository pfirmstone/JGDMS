# JGDMS-STD-006 — ATOMIC DER Wire Format

> **Format name.** This standard defines the wire format named **ATOMIC DER** — it names
> the `@AtomicSerial` object model it encodes (not the transport, not the project). The
> **standard document identifier** remains `JGDMS-STD-006` (family consistency with
> STD-001/003/008/…). Throughout: "ATOMIC DER" is the format's proper name; generic
> "DER" (X.690 DER, "DER-encoded", "the DER value tree") continues to mean the ASN.1
> encoding rules and is unchanged.

**Status:** Draft (working scaffold for discussion)
**Version:** 0.13-DRAFT
**Applies to:** JGDMS, DirtyChai (JDK fork), and non-JVM JGDMS participants
**Depends on:** JGDMS-STD-001 (@AtomicSerial), JGDMS-STD-003 (Multi-Subject Identity)
**Supersedes (on completion):** the Java-serialization-based JERI wire encoding
**Integral parts:** **Appendix C — Stream Schema Dedup**
(`JGDMS-STD-006-Appendix-C-Stream-Schema-Dedup-v0.1-DRAFT.md`, internal version
0.3-DRAFT; standalone file pending merge) is a **NORMATIVE, MANDATORY** part of this
standard — **RATIFIED by Peter, 2026-07-21**: dedup is the sole stream form of the
released DER object-stream format (one record sequence, one stream encoding; no
non-dedup variant exists). Every conformant implementation of this standard's object
streams MUST implement Appendix C; a stream lacking its `[15]` version octet or its
dedup grammar is malformed and MUST be rejected. Appendix C's ceilings compose with
§4.5's table; its record-level canonical full forms (non-stream contexts:
`MarshalledInstance` capture, persistence) are defined in Appendix C §C.5.3–§C.5.4.

> **Editorial note (v0.13-DRAFT):** This is a working scaffold. Sections marked
> **[OPEN]** require Peter's knowledge of the exact field layout of the current
> implementation. Sections marked **[PROPOSED]** are design suggestions for
> discussion, not settled decisions. ASN.1 modules are illustrative and not yet
> validated against an ASN.1 compiler (§4.5 makes compiler validation the next
> gating action).
>
> **Naming/structure pass (2026-07-06)** — **RATIFIED by Peter:** (1) the wire format
> is named **ATOMIC DER** (names the `@AtomicSerial` object model it encodes); the
> standard document id stays `JGDMS-STD-006` and generic "DER" (X.690) is unchanged —
> only the format's proper name changed (title, §1 note, §8 headings/prose).
> **Wire-identifier rename — RATIFIED by Peter (2026-07-10):** the earlier decision to
> keep the wire/code identifiers verbatim is **superseded**. The constraint constant is
> now `MarshallingFormat.ATOMIC_DER` and the on-wire/in-code identifier string is
> `"JGDMS-STD-006/ATOMIC-DER"`, aligning them with the format's proper name. This IS an
> on-wire format-identifier change; it is safe precisely because STD-006 is a pre-1.0
> DRAFT with no deployed peers, and is deliberately made before v1.0. (Applied to
> `jgdms-platform`, `jgdms-der`, `jgdms-jeri` main + tests.)
> (2) The §4.4 OID root is restructured under **Zeus Project Services Pty Ltd**
> (registrant): `zeusProjectServices → jgdms(1) → atomicDer(1) → wireTypes(1)`, with
> `TypedWireObject` typeIds under `…atomicDer.wireTypes`. `<PEN>` (written `999999`) is
> a clearly-marked PLACEHOLDER; Zeus Project Services Pty Ltd will register an IANA PEN
> and it MUST be replaced before v1.0.
>
> **Ratified follow-ups (2026-07-06).** §7.4 **SCAP signature TBS = canonical DER of
> the signed fields** (RATIFIED): each signed record's to-be-signed content is
> `DER(SEQUENCE{signed fields})` with the signature field excluded (per-record coverage
> table in §7.4), replacing the as-built non-DER `writeUTF` `canonicalBytes()`.
> **Clean break** — SCAP unreleased → no legacy verdicts, no dual-format cutover, no
> signature-format versioning; DER-canonical TBS from first release. Impl follow-up
> (change SCAP classes to sign/verify over the DER TBS, not `canonicalBytes()`) flagged
> as a separate `jgdms-platform` code task (not this doc branch). §7.7.1
> **hash-algorithm migration = Option B**
> (RATIFIED): the mandatory `hashAlgorithm` version tag makes the scheme self-describing
> (`1` = ATOMIC DER `SHA-256(DER(EntrySchemaRecord))`, `2` = legacy RULE-7 64-bit;
> unknown = reject; match within one algorithm only; enumerant numbers still to pin).
> §7.6 **`Throwable` = the SAFE SUBSET** (RATIFIED): only inert data travels —
> `className` (name string), `message`, `stackTrace`, `suppressed[]`, `cause` (nested
> `ThrowableRecord`s, `[0]`/`[1]` context-tagged for distinct-tag validity). The as-built
> serializer's **reflective-constructor reconstruction is EXCLUDED** and its `clazz`
> (`Class`), `perm` (`Permission`), and `classname`/`length`/`eof` fields are **dropped**
> (gadget-adjacent; a `Permission`, if ever needed, follows the §7.6 string-form rule).
> ASN.1 module updated (`ThrowableRecord` gains `suppressed`; both nesting fields
> context-tagged) and re-validated (asn1tools, 55 types; both/cause-only/suppressed-only
> round-trip).
>
> **Open-item resolution pass (2026-07-06)** — resolved the remaining `[OPEN]`
> markers. **RATIFIED by Peter (2026-07-06):** §4.4 top-level discriminator is
> **OID-rooted** (`TypedWireObject`, single discriminator per board H3; registrant Zeus
> Project Services Pty Ltd, PEN placeholder pending registration); §7.6 `Permission` and §7.5
> `PermissionGrant`/`DigestGrant` are **NOT structured DER** — carried as textual
> string form and **re-parsed** through the trusted policy parser (governing
> authority-vs-signature principle now stated in §7.5); §7.6 `Date` is **epoch-millis
> `INTEGER`**; §7.6 `MarshalledObject` nesting is **bound to `MAX_NESTING` + §4.5 size
> caps** (a `maxNesting` number still to set); **§7.3.1 (new) verification policy** —
> the `DigestCodeSource.unverified` flag does not travel, decode forces the UNVERIFIED
> state and reconstructs by direct field-set (no re-compute/re-download), and the
> receiver verifies only against code present or being loaded anyway (never
> download-to-verify). **Resolved from source:** §7.3
> `DigestCodeSourceRecord` (`DigestCodeSource.java`), §7.4 SCAP field lists
> (`au.net.zeus.jgdms.api.codebase.*` — no `SignedVerdict` class; signatures inline),
> §7.6 `URL`/`URI`/`File`/`UID`/`Properties`/`StackTraceElement` (their serializers) +
> full substituted-type set (adds `Uuid`/`Marker`), §7.7.1 RULE-7 hash (as-built =
> 64-bit-truncated SHA-256 over `writeUTF`; DER = 32-byte `SHA-256(DER(...))`), §7.7.7
> `getTrustBundle()` (confirmed) and `SvidRotationListener` (confirmed — deliberately
> **not** a `@FunctionalInterface`; `addListener`), §7.7.8 `UnicastResponse`/
> `SslEndpoint.getInstance` stub path. The ASN.1 module was updated (structured
> `Permission` removed; `PermissionTextForm`/`PermissionGrantTextForm` added) and
> re-validated with asn1tools (compiles, 50 types). Items still needing Peter:
> OID-root PEN, `maxNesting` value, the §4.5 ceiling numbers, the §7.7.1 migration
> option, `Throwable` field-set scope, and the §7.7.5 `attributes`/`attributeTemplates`
> SET-OF flip.
>
> **Changes in v0.13 (from v0.12)** — external review fixes; all module changes are
> to unreleased formats, so no compatibility window applies:
> - **§4.5 (new) — distinct-tag rule + profile size ceilings.** Several §7 modules
>   were invalid ASN.1: X.680 requires an `OPTIONAL` component's tag to be distinct
>   from every component that can follow it. Violations fixed with context tags (or
>   field removal): `MulticastAnnouncementRecord`/`MulticastRequestRecord`
>   (`signerCert` vs `signature`, both OCTET STRING — inside the signed TBS),
>   `DigestCodeSourceRecord` (`certificates` SEQUENCE OF vs `digest` SEQUENCE, both
>   tag 0x30), `ServiceTemplateRecord` (two consecutive `SEQUENCE OF … OPTIONAL`),
>   the proposed `Permission` form (`name`/`actions` both UTF8String OPTIONAL), and
>   `MarshalledInstanceRecord` (`codebaseAnnotation` vs `payloadFormat`, both
>   UTF8String — resolved by **deleting** the field, see below). The as-built Java
>   decoder's count-the-remaining-UTF8Strings rule is withdrawn. §4.5 also assigns
>   named profile ceilings to every `SIZE(0..MAX)` (design principle 5 requires a
>   declared bound; `MAX` without a profile number was a violation).
> - **§7.8 — `codebaseAnnotation` REMOVED from `MarshalledInstanceRecord`.** It
>   contradicted §8.3 ("no codebase annotation field at any level") and reintroduced
>   the §8.1 stale-URL hazard. `CodebaseAccessor` over authenticated TLS is the one
>   and only codebase channel. This also removes the ASN.1 ambiguity above.
> - **§7.8 — hierarchy payload layout added (was unspecified).** The as-built
>   encoding — outer wrapper SEQUENCE, one private per-class SEQUENCE in
>   **superclass-first (root-first, leaf-last)** order, opposite to the leaf-first
>   schema chain — was implemented (`ObjectCodec.encodeHierarchy`) but absent from
>   the spec; a non-JVM party could not interoperate from the document alone. Now
>   normative, with the chain↔payload index correspondence stated.
> - **§7.8/§12.4 — fail-secure schema resolution.** `schemaDigest` is now verified
>   against the embedded `schemaBytes` leaf record before any use (it is a routing
>   hint, not an integrity claim); absent, corrupt, or digest-mismatched
>   `schemaBytes` is a hard **reject** — the previous "fall back to the local
>   schema and accept defaults" step violated design principle 6 (no permissive
>   fallback) and §7.8's own MUST. The registry is no longer a decode-time fallback.
> - **§7.7.7 — TBS binds the format identifier; replay note.** The signature input
>   is now `DER(SEQUENCE { formatName, protocolVersion, …record fields })` with the
>   format name and version signed-but-not-transmitted (known from the packet
>   header), preventing cross-format/cross-protocol signature reuse. A normative
>   note records that announcements carry no freshness and MUST be treated as
>   advisory (replayable) hints; trust is established only at the authenticated
>   unicast step.
> - **`DEFAULT` eliminated from all modules** (`hashAlgorithm INTEGER … DEFAULT 1`,
>   `tlsRequired`/`renewable`/`nullable BOOLEAN DEFAULT TRUE`): DER mandates
>   omitting a component equal to its DEFAULT, so an implementation that encodes it
>   explicitly silently breaks byte-equality entry matching (§7.7.2) and signed
>   records. All such fields are now mandatory.
> - **§7.7.6 — `Lease.ANY` vs `Lease.FOREVER` corrected.** `-1` is `Lease.ANY`
>   ("any duration acceptable"), not FOREVER (`Long.MAX_VALUE`); the two named
>   values are now distinct. (Closes open item 13.)
> - **§12 — registry fixes.** The unenforceable "registration requires the owning
>   service" rule is dropped (content addressing makes registration by any
>   authenticated party safe); `isCompatible` is redefined over the full Merkle
>   chain (the leaf-only prefix rule gave wrong answers across §11.4/§11.6
>   hierarchy evolutions).
> - **§7.2 — normativity clarified**: the ASN.1 module is the target DER form for
>   conformance to this standard; the as-built `writeInt`/`writeUTF` byte framing is
>   transitional documentation of the current transport, superseded at the Tier-0
>   envelope migration (§5.4).
> - **§7.6 — `Permission` row corrected**: `PermissionSerializer` was deleted from
>   the JOSS engine (2026-06, reflective-gadget removal); whether `Permission` travels
>   at all was left `[OPEN]` here and is **RESOLVED in the 2026-07-06 pass**
>   (RATIFIED: string-form + reparse, not a structured type).
> - **§7.8 — schema-identity comment corrected**: `className` is part of
>   `AtomicSerialSchemaRecord`, so identical field lists under different class names
>   hash differently (the previous wording claimed otherwise).
> - *(type-model-pinning addendum, 2026-07-05)* — **§3.12 (new subsection) "Wire
>   Type Model."** Pins, as normative STD-006 text, the closed-subset type algebra
>   from `docs/der-type-model-and-element-rule.md` (board-reviewed design memo):
>   `Scalar | AtomicSerialObject | Collection(WireType…) | Any`, the
>   declaration-based element-derivation rule (no annotation — the developer's
>   existing generic declaration is the sole signal, mirroring §3.8's
>   declared-class ordering discriminator), the exclusion boundary (no raw
>   `Object` graphs outside `Any`, no arbitrary `Serializable`, no cycles), the
>   `Any` `CHOICE` form and its context-tag registry (scalars `[0]`-`[9]`
>   `IMPLICIT`; `atomicSerialObject [20]`, `canonicalCollection [30]`,
>   `orderedCollection [31]` `EXPLICIT` so each arm's own load-bearing outer tag
>   survives, X.680 §31.2.7), and the four decoder fences as normative
>   obligations (`MAX_NESTING` through every `Any` recursion; the `Any` object
>   body gated through the identical `DeSerializationPermission("ATOMIC")` +
>   `ResolutionContext` + `check(GetArg)` path, hierarchy-wide; unknown/mismatched
>   tag hard reject; canonical minimal tag encoding). Cross-references §3.8
>   (ordering discriminator, octet-sort) and §7.6 (the six collection
>   productions). Corresponding `.asn1` `AnyElement CHOICE` production added
>   (`docs/asn1/JGDMS-STD-006-v0.13.asn1`), compiled and validated with
>   asn1tools.
> - *(review addendum, 2026-07-04)* — ASN.1 grammar validation (open item 20,
>   `docs/asn1/`): value references renamed to conformant case (`maxFields` …,
>   `leaseForever`/`leaseAny`); §7.2/§7.3 ceilings promoted from comments to value
>   assignments (`maxDomains`/`maxCerts`/`maxCertLen`/`maxDigestLen`; the duplicate
>   `MAX_CERT_COUNT`/`MAX_CERT_BYTES` names merged into `maxCerts`/`maxCertLen`);
>   §4.3 `AlgorithmIdentifier` parameters-absent rule (RFC 5280/8702); §4.6 (new)
>   tagging mode `EXPLICIT TAGS` with `ReducingDomainRecord` arms marked
>   `IMPLICIT`; §7.7.7 `MulticastTbs` concretised into
>   `MulticastAnnouncementTbs`/`MulticastRequestTbs`; two Markdown fence fixes.
> - *(collection-ordering addendum, 2026-07-04)* — **§3.8 (new subsection) "Encounter
>   order and serialized equality."** Element order of a collection field is now
>   governed by whether the declared type **guarantees a deterministic iteration
>   order**. **PRESERVE** (deterministic): the JDK 21 sequenced backbone (`List`,
>   `Deque`, `LinkedHashSet`, `LinkedHashMap`, `SortedSet`/`TreeSet`,
>   `SortedMap`/`TreeMap`), plus `EnumSet`/`EnumMap` (ordinal order), the concurrent
>   *sorted* `ConcurrentSkipListSet`/`ConcurrentSkipListMap`, `CopyOnWriteArrayList`
>   (a `List`), and all arrays (incl. `byte[]`). **CANONICALISE** (non-deterministic /
>   unspecified): `HashSet`, `HashMap`, `ConcurrentHashMap` (hash-bucket order);
>   `CopyOnWriteArraySet` and the concurrent insertion/FIFO queues/deques
>   (`ConcurrentLinkedQueue`/`Deque`, the blocking queues/deques, `LinkedTransferQueue`,
>   `DelayQueue`, `SynchronousQueue`) — insertion/FIFO order is race-dependent, hence not
>   deterministic; contrast the non-concurrent `LinkedHashSet`/`ArrayDeque`, which preserve;
>   and `PriorityQueue`/`PriorityBlockingQueue` (iterator disclaims order — priority order
>   rebuilt at the receiver) — octet-sorted per X.690 §11.6. (The codec now implements all
>   six collection tokens with the pinned `SET OF`/`SEQUENCE OF` tags and §11.6 order
>   enforcement — branch `der-collection-codec`.) The subsection
>   documents the deliberate consequence that serialized byte-equality is
>   **order-sensitive** for preserved types and **stricter than object `equals`** for
>   the insertion-ordered `LinkedHashSet`/`LinkedHashMap` (they inherit the
>   order-independent `Set`/`Map` `equals`); the element-derived-order types
>   (`SortedSet`/`SortedMap`, `EnumSet`/`EnumMap`, `ConcurrentSkipList*`) and the
>   positional types (`List`, arrays) have **no** such tension. Cross-referenced from
>   **§7.6** (collection-field note), scoped **§9 item 5**, and added as **§9 conformance
>   item 14**. The two unordered attribute-bag sequences —
>   `ServiceItemRecord.attributes` and `ServiceTemplateRecord.attributeTemplates` — are
>   annotated **"behavioural; unordered (§3.8)"** (comments only; no field-structure
>   change), consistent with the `Properties` row. Full rationale:
>   `docs/der-collection-ordering-research.md`.
>
> **Changes in v0.12 (from v0.11):**
> - §6.3/6.4, §7.2, §7.6, §8.2, §9, §10 — **§7.2 reconciled with the as-built
>   implementation `net.jini.jeri.RemoteContextCodec`** (jgdms-jeri), which is the
>   live transport (client `BasicInvocationHandler.marshal`, server
>   `BasicInvocationDispatcher.unmarshal`). The prior `AccessControlContextSerializer`
>   (`org.apache.river.api.io`, with `DomainIdentityRecord` + the
>   `[httpmdCount][…][anonCount]` format) was an **earlier implementation, since replaced**
>   by `RemoteContextCodec` — gone from live source (only a stale bundled-source build
>   artifact remains under `target/`). Three substantive corrections to the workload-identity
>   (Layer 2) path — two reflect a deliberate hardening, the third fixes a
>   privilege-escalation bug:
>     1. **No principals on the wire.** The transmitted ACC is the caller's *reducing*
>        domain set — **codebases only**. The additive workload principals are stamped
>        at the receiver from the authenticated mTLS `RemoteSubject`, never from the
>        wire. `DomainIdentityRecord` loses its `principals` field.
>     2. **No `anonCount`/verifiable-vs-anonymous split.** *Every* reducing domain is
>        transmitted inline as a codebase-identity record discriminated by a `kind`
>        (null-CS / DigestCodeSource / URL). There are no anonymous placeholder domains
>        and no separate count; a null-CodeSource domain is transmitted (dropping it
>        would *elevate* authority) and reconstructed codebase-less + principal-bearing.
>     3. **`jrt:/java.base` exclusion is REQUIRED — and was a bug when missing.** The
>        platform `java.base` domain is always fully privileged (`AllPermission`),
>        native, and unstamped, so it is *not* a reducer: reconstructing it with the
>        remote principals would inherit the receiver's unconditional platform grant (an
>        escalation). It MUST be excluded by the encoder *and* refused by the decoder.
>        `RemoteContextCodec` was found transmitting it (no `jrt:` filter at all) — a
>        privilege-escalation bug — now FIXED on both ends (`isJavaBaseModule`). Other
>        `jrt:` modules are retained. (The draft's `jrt:` exclusion was therefore
>        correct; only the `anonCount`/placeholder *machinery* around it is withdrawn.)
>   This decouples STD-006 §7.2 from the (separate, unchanged) **user-identity** path:
>   JWT/OIDC `UserSubject`s (§7.1 `UserSubjectBlock`, the `0x02` block) *are* still
>   transmitted and independently verified at the receiver — see §7.1 and STD-003.
> - §4.1 — replaced the illustrative `httpmdCount : 4B BE` example (a notation that was
>   never implemented) with `domainCount : 4B BE`, the real fixed-width counter in
>   `RemoteContextCodec`.
>
> **Changes in v0.11 (from v0.10):**
> - §3.9 — Three decoding cases clarified: "the schema" always refers to the
>   `MarshalledInstance` embedded schema (the at-marshal-time schema), not the
>   code's current `serialForm()`. These may differ when schema and code have
>   evolved independently. The `@AtomicSerial` `GetArg` layer bridges the
>   divergence in all cases.
> - §3.11 — New paragraph: `@AtomicSerial` as the compatibility layer between
>   schema and code. Schema and code are independently evolvable; `GetArg` absorbs
>   all divergence at runtime; the `MarshalledInstance` schema is always authoritative
>   for the data it accompanies.
> - §7.8 — `MarshalledInstanceRecord`: added normative statement that the embedded
>   schema is always authoritative for decoding and must be used in preference to
>   the code's current `serialForm()`. Cross-reference to STD-007 §3.6.

---

## 1. Purpose and Scope

This specification defines a **language-neutral, DER-encoded wire format** for the
data objects that JGDMS transmits over JERI endpoints. It is the canonical successor
to the Java-serialization-based encoding that JGDMS has used to date.

The goals are:

- **Cross-runtime participation.** A non-JVM process (Python, Rust, Go, C) can
  encode and decode JGDMS wire objects using standard ASN.1/DER tooling, without
  reimplementing the Java Object Serialization Stream Protocol.
- **Attack-surface reduction.** A single, schema-defined decode path eliminates the
  multiple object-instantiation channels (`TC_OBJECT`, `TC_PROXYCLASSDESC`, etc.)
  that have made Java serialization the JVM's richest vulnerability class.
- **Encoding consistency.** The JGDMS identity layer (SPIFFE X.509 SVIDs, trust
  bundles) is already DER. A DER object layer means the entire data plane uses one
  encoding family, and any participant that already parses SVIDs for TLS has the
  toolchain in hand.
- **Format-independent validation.** The `@AtomicSerial` validation contract
  (`check(GetArg)` invariant enforcement, `SerialForm[]` shape declaration) is
  preserved unchanged. Only the encoding beneath it changes.

This specification does **not** change the `@AtomicSerial` validation semantics
(STD-001), the identity model (STD-003), the SCAP pipeline (STD-002), or the JERI
transport/TLS layer. It defines only the on-the-wire octet encoding of object state
and the negotiation by which DER and legacy encodings coexist during migration.

---

## 2. Motivation

### 2.1 Why Replace Java Serialization

The `@AtomicSerial` protocol (STD-001) already separates *validation* from
*construction*: a class declares its serial shape via `SerialForm[] serialForm()`
and validates field values via a `check(GetArg)` method whose first action runs
before any field is assigned. Crucially, `@AtomicSerial` defines validation
*contracts*, not a wire encoding — the encoding is pluggable, exactly as JERI's
transport is pluggable.

Java Object Serialization was the first encoding implemented beneath `@AtomicSerial`, and
it carries inherent problems that no validation discipline fully removes:

- **Multiple object-creation pathways.** The stream grammar instantiates objects
  through several distinct paths. Each requires independent guarding; the
  `readProxyDesc()` / `SerialObjectPermission` gap (DirtyChai §13, since closed) is a
  direct consequence — a guard correct for `readOrdinaryObject()` left
  `TC_PROXYCLASSDESC` unguarded. A schema-defined format has one decode path and no
  hidden instantiation side channels.
- **Java-specific grammar.** The stream format is defined in terms of Java class
  descriptors, making a faithful non-JVM decoder impractical to build and audit.
- **Opaque structure.** The wire bytes are not self-describing against a published
  schema, so a receiver cannot statically bound what a stream may contain before
  decoding it.
- **In-stream codebase annotations (deprecated for removal).** Java serialization
  embeds URL codebase annotations in the stream to enable the receiver to load class
  definitions from remote sources. As documented in Warres (2006) [SMLI TR-2006-149],
  this mechanism produces type conflicts, codebase annotation loss, codebase
  annotation mixing, stale-content failures, and complex configuration requirements.
  JGDMS replaces this mechanism entirely (§8); codebase annotations **do not appear**
  in the ATOMIC DER wire format. `AtomicMarshalOutputStream` already defaults to
  `writeCodebaseAnnotations=false`, making the existing Java-serialization stream
  effectively annotation-free in JGDMS deployments today. The ATOMIC DER format
  formalises this as a permanent property of the encoding.

DER with a published ASN.1 schema inverts all four of the above: one decode path, a
language-neutral grammar with mature tooling in every serious language, a
self-describing tag-length-value structure that can be bounded before allocation, and
no in-stream annotation channel.

A further, subtler consequence follows for collection-valued fields. Because Java
serialization encodes an object's concrete implementation and internal layout, two
collections that are `.equals` in object form — a `HashSet` and a `TreeSet` of the same
elements, or two `HashSet`s built differently — serialize to *different* bytes; its
serial-equality reflects implementation identity, not value equality. The DER encoding
fixes a collection field's element order from the declared type's own `equals` contract
(a canonical order where `equals` is order-independent, the preserved order where it is
not — §3.8 "Encounter order and serialized equality"), so serialized byte-equality tracks
*value* equality. That is what lets DER bytes serve as a value-equality proxy for Jini
Entry byte-matching (§7.7.2), the content-address digests (§7.8), and signature stability
— none of which Java serialization can support on a `Set`/`Map` field.

### 2.2 Relationship to the Cross-Runtime Goal

STD-003 establishes that JGDMS's security properties (multi-principal authorization,
ACC propagation, content-addressed identity) are *protocol* concepts whose current
*implementation* happens to assume a Java deserializer on the receiving end. This
standard removes that assumption. Once a non-JVM participant can decode the wire
types defined here, the path to first-class JERI participation for non-JVM runtimes
(e.g. AI inference services) reduces to a conventional protocol-implementation task:
a DER codec against this schema, plus the TLS/SPIFFE handshake, plus the framing
defined in §6–§8.

### 2.3 Data Independence from Code

In Java serialisation, an object's class is inseparable from its serialised form. A
serialised byte stream without its originating class is completely opaque — no field
name, no field value, no structure is recoverable. The class is the key. If the class
is unavailable — because the JAR is no longer served, the codebase URL has gone
stale, or the service has been decommissioned — the data is permanently unrecoverable.

The DER encoding specified here, combined with the `@AtomicSerial` schema
(`serialForm()`) embedded in `MarshalledInstance`, severs this dependency. **The
schema is the key, not the class.** Given the DER bytes and the schema:

- Every field name is known
- Every field value is decodable by type
- The complete data content is accessible to any DER-capable decoder in any language
- No class loading is required; no JVM is required

The class — the codebase — is needed only for *behaviour*: typed construction,
invariant enforcement, method invocation, and business logic. For data access,
inspection, migration, archival, forensic analysis, and cross-language
interoperability, the schema alone is sufficient.

This property has significant consequences for distributed systems that evolve over
years:

- **Data outlives code.** Service implementations are replaced; old proxies become
  unavailable; JARs disappear. The data they produced — service items, event
  payloads, persisted state — remains fully legible as long as the schema is
  available.
- **Migration without code.** A migration utility can transform stored records to a
  new format using only the schema and a DER decoder, without loading the original
  class.
- **Forensic auditability.** Audit logs, event histories, and service item snapshots
  can be read years later without requiring the class that produced them.
- **Polyglot access.** A non-JVM tool can read, filter, and process JGDMS data
  objects without any Java infrastructure.

The schema embedded in `MarshalledInstance` (§7.8) is the mechanism that guarantees
this property. It must be present unconditionally — not optional, not a fallback
path. Without the embedded schema, the data independence guarantee is lost.

---

## 3. Design Principles

1. **Schema is the contract.** Every wire type has a published ASN.1 module. The
   schema, not an implementation, is authoritative. A participant conforms to the
   schema, not to the JVM implementation's behaviour.

2. **DER, not BER.** Distinguished Encoding Rules (canonical, one encoding per value)
   are mandatory. This matters for security: any field whose octets are
   signature-covered (e.g. `RegistryVerdict`, `JarAnalysisReport`, `SignedVerdict`)
   must have a single canonical byte form so signatures verify deterministically
   across implementations. BER's encoding flexibility would break signature
   determinism.

3. **Validation is unchanged and format-independent.** `check(GetArg)` runs on
   decoded field values regardless of source encoding. The `GetArg` abstraction
   (STD-001) is the decode target; this standard defines how DER octets populate a
   `GetArg`, not what `check()` does with it.

4. **Explicit over implicit.** Anything Java serialization carried implicitly —
   codebase annotation, class identity, type discriminators — becomes an explicit,
   schema-defined field. This is a security improvement (nothing is woven into the
   stream mechanics) but must be designed in deliberately (§7).

5. **Bounded before allocation.** Every variable-length field has a schema-declared
   or profile-declared maximum (`SIZE` constraint). A decoder rejects an
   over-length field before allocating for it, mirroring the DOS defences already
   present in `DigestCodeSource` (MAX_STREAM_BYTES, etc.) and the `0x02` Subject
   block caps (16 Subjects × 64 principals).

6. **Fail-secure decode.** A decode failure, schema violation, unknown enumerant in
   a closed enumeration, or constraint breach results in rejection (no object
   constructed), never in a permissive fallback.

### 3.7 Acyclic Object Graph Requirement

The object graph is **acyclic**. This is not merely an encoding consequence of DER
(which has no native back-reference mechanism) — it is a documented security
contract already enforced by `AtomicMarshalInputStream`, whose class documentation
lists "Object graphs with circular references" under *De-serialization Not
supported*, alongside `readObject` and zero-arg-superclass construction.

The acyclic constraint is the precondition for validation-before-construction.
Circular references force a serialization format to carry a handle/back-reference
table and to support *deferred* construction — an object must exist, at least
partially, before its fields are populated, so that a later field can reference back
to it. That deferred-construction requirement is fundamentally incompatible with
running `check(GetArg)` before field assignment: you cannot validate an object before
construction if the graph requires the object to already exist so something else can
point at it.

Three layers are therefore in agreement by construction: the `@AtomicSerial`
validation contract cannot tolerate a cycle, the existing `AtomicMarshal*`
wire subset does not produce one, and DER cannot express one. The format carries
**no handle table and no object-reference mechanism** anywhere.

This eliminates two of the three classic deserialization vulnerability classes at
the format level — reference theft from a partially-constructed object, and
cyclic-reference denial of service — because neither is representable. The acyclic
property is a **security requirement**, not an encoding convenience: no future schema
revision may introduce a reference type or any construct that permits a cycle.

The single permitted exception is the frame-level codebase-identity table (§8), which
is a flat dictionary decoded and validated *in its entirety* before any object body
may index into it. Its indices resolve only into already-completed, immutable entries
— never into the object graph under construction — so it does not reintroduce
deferred construction or cycles. If that strict ordering cannot be guaranteed, the
table is abandoned in favour of repeating the records (§8).

### 3.8 Values and Bounds, Never Behaviour

The wire format carries **values and bounds**. It does **not** carry behavioural
contracts. Uniqueness, mutability, and null-handling are imposed by the constructed
object *after* validation, never asserted by the encoding. **Ordering and sortedness
are the exception** — where the declared type guarantees a deterministic iteration
order, that order *is* asserted by the encoding (preserved on the wire); where the
type does not, ordering is canonicalised on the wire, never left to the receiver. This
is governed by the "Encounter order and serialized equality" subsection below.
Anything else that appears to require a behavioural contract on the wire is a signal
that the contract actually belongs in `check(GetArg)` or the constructor.

This is what allows the substituted collection carriers
(`MapSerializer`/`SetSerializer`/`ListSerializer`) to collapse into a native DER
`SEQUENCE OF` with a `SIZE` bound (§7.6): the carrier's two jobs — bounding (DOS
defence) and immutability — are subsumed by the schema `SIZE` constraint and DER's
inherently read-only decoded structure. A `SortedSet` field and a `HashSet` field
both encode as `SEQUENCE OF Element`; the receiving object imposes uniqueness during
construction, but their **element order** on the wire is fixed by the encoder, not the
receiver — preserved for the deterministic-order type, canonicalised for the other, per
the "Encounter order and serialized equality" subsection below.

**Intrinsic-order carve-out.** Where order is *intrinsic to a value* — arrays, linked
lists, principal chains, certificate paths, any sequence whose indices are
semantically distinct — that order **is** part of the value and is preserved on the
wire and significant. DER `SEQUENCE OF` preserves element order by specification, so
the encoding mechanism is the same as for a behavioural collection; the difference is
that the receiving `check()`/constructor *relies on* the order rather than
*overwriting* it. A SPIFFE principal chain (leaf-first) or an X.509 certificate path
must not be reordered by an encoder, deduplicated, or canonicalised away by a decoder.
An implementer who treats such a sequence as an unordered set and reorders it (for
example, by sorting for canonicalisation) corrupts the value and breaks chain
validation.

**Encounter order and serialized equality.** Whether an encoder preserves or
canonicalises a collection's element order is determined by whether the **declared
type guarantees a deterministic iteration order**. An encoder preserves the iterator's
order only where the type guarantees it is deterministic; where the type's iteration
order is unspecified, hash-derived, or (for insertion-history collections)
concurrency-dependent, the encoder canonicalises instead. (A developer deciding how a
given collection field's order will be treated should consult the developer guidance in
the collection-ordering memo, `docs/der-collection-ordering-research.md` §1c: the
underlying discriminator is whether element order is part of the collection's value —
`List`/array order is the value and is always preserved; `Set`/`Map` order is not, so
it is preserved only for the deterministic-order types and otherwise canonicalised.)

- **PRESERVE (deterministic iteration order).** The backbone is the JDK 21
  sequenced-collection interfaces (`java.util.SequencedCollection`/`SequencedSet`/
  `SequencedMap`): `List` (`ArrayList`, `LinkedList`, `Vector`, `CopyOnWriteArrayList`),
  `Deque` (`ArrayDeque`), `LinkedHashSet`, `LinkedHashMap`, and — because `SortedSet`
  extends `SequencedSet` and `SortedMap` extends `SequencedMap` — `SortedSet`/`TreeSet`
  and `SortedMap`/`TreeMap`. It also covers `EnumSet`/`EnumMap` (deterministic natural
  ordinal order), the concurrent **sorted** collections `ConcurrentSkipListSet`/
  `ConcurrentSkipListMap` (comparator-derived, so deterministic despite concurrency),
  and `CopyOnWriteArrayList` (a `List` — order **is** its value). Arrays — including
  `byte[]`, whose order **is** the value — are always preserved, as are the intrinsic
  sequences named in the carve-out above.
- **CANONICALISE (iteration order non-deterministic or unspecified).** `HashSet`,
  `HashMap`, and `ConcurrentHashMap` (hash-bucket order — the iterator disclaims any
  order); the concurrent insertion/FIFO-history collections — `CopyOnWriteArraySet` (an
  insertion-history **set** whose insertion order is race-dependent under concurrency,
  hence not deterministic — this differs from `LinkedHashSet`, which is non-concurrent and
  whose insertion order therefore *is* deterministic), `ConcurrentLinkedQueue`,
  `ConcurrentLinkedDeque`, `ArrayBlockingQueue`, `LinkedBlockingQueue`,
  `LinkedBlockingDeque`, `LinkedTransferQueue`, `DelayQueue` (whose iterator does not
  return delay order), and `SynchronousQueue` (which holds no elements — trivially an empty
  collection); and `PriorityQueue`/`PriorityBlockingQueue` (the iterator explicitly
  disclaims order — heap-array layout, not priority order — and the natural priority order
  is a function of elements plus comparator, rebuilt at the receiver). The `Deque` family
  splits exactly on the concurrency line: the non-concurrent `ArrayDeque` preserves, the
  concurrent `ConcurrentLinkedDeque` canonicalises.

The canonical (octet-sort) rule for the canonicalise side is the X.690 §11.6 SET OF
order (each element encoded to canonical DER, the element encodings sorted as octet
strings); its full mechanics are recorded in the collection-ordering memo
(`docs/der-collection-ordering-research.md`). An encoder MUST NOT derive collection
order from any `hashCode`/`identityHashCode` or hash-bucket iteration order (that order
is per-run, machine-dependent, and reproducible nowhere).

**Wire tag and decoder obligations (NORMATIVE).** The two disciplines are distinguished
on the wire by their ASN.1 container, not only by the schema token:

- A **canonicalise** collection (`set:`, `bag:`, `map:`) is a **`SET OF`** — universal
  tag **`0x31`** — whose elements (or, for `map:`, whose per-entry `SEQUENCE`s) are in
  X.690 §11.6 ascending octet order. A `map:` is `SET OF SEQUENCE { key, value }`: the
  outer container is `SET OF` (`0x31`) and each entry is a `SEQUENCE` (**`0x30`**); the
  entries are octet-ordered by the **encoded key** only (keys are unique values, so the
  key order is a total order; value octets never participate — see §7.6).
- A **preserve** collection (`orderedset:`, `list:`, `orderedmap:`) is a **`SEQUENCE OF`**
  — universal tag **`0x30`** — carrying the iterator's order verbatim. An `orderedmap:`
  is `SEQUENCE OF SEQUENCE { key, value }` (both containers `0x30`).

A conformant DER decoder **MUST reject** (fail-secure, principle 6): (a) a canonicalise
collection whose elements — or whose `map:` entries, by encoded key — are **not in
strictly ascending §11.6 order** (an unsorted `SET OF` is not valid DER, X.690 §10.1 /
§11.6); (b) a **wrong container tag** for the declared token (a `SEQUENCE OF` `0x30`
where the token requires a `SET OF` `0x31`, or vice versa); (c) a **duplicate** element
encoding in a `set:`/`orderedset:` field, or a duplicate key encoding in a
`map:`/`orderedmap:` field (a `bag:` multiset retains duplicates). This rejection is
mandatory for **every** conformant decoder, JVM and non-JVM alike.

A consequence is deliberate and must be understood, and it is scoped precisely: for a
preserved type, element order is part of the serialized identity, so serialized
byte-equality is order-sensitive. This produces a byte-vs-`equals` **tension only for
`LinkedHashSet` and `LinkedHashMap`.** These inherit the order-independent `Set`/`Map`
`equals` (which ignores iteration order) yet carry an insertion order that is
non-rederivable history, so their serialized byte-equality is **stricter** than their
in-memory `equals` — two instances that are `equals` but were built in different
insertion orders serialise to different octets and will not compare byte-equal (and
will not match under §7.7.2 Entry byte-matching). This is the correct behaviour for a
type whose insertion order is significant; a field that instead wants order-independent
equality on the wire must be declared as a plain (non-sequenced, canonicalised)
`Set`/`Map`, whose canonical form makes serialized byte-equality coincide with
`equals`. Choose the collection type accordingly.

The other preserved types have **no such tension** — equal instances always serialise
identically:

- **Element-derived order** — `SortedSet`/`TreeSet`, `SortedMap`/`TreeMap`,
  `ConcurrentSkipListSet`/`ConcurrentSkipListMap`, `EnumSet`/`EnumMap`: the encounter
  order is a function of the elements (plus comparator / ordinal), so two `equals`
  instances have the same order and serialise to the same octets.
- **Positional / order-is-the-value** — `List` (whose own `equals` is already
  order-sensitive) and arrays: two `equals` instances are, by definition, in the same
  order and serialise identically.

The rule, stated for conformance: *order is preserved and significant where the
declared type guarantees a deterministic iteration order (the sequenced backbone plus
`EnumSet`/`EnumMap` and the concurrent sorted collections, arrays, and the intrinsic
sequences above); a type whose iteration order is non-deterministic or unspecified — a
plain `HashSet`/`HashMap`/`ConcurrentHashMap`, the concurrent `CopyOnWriteArraySet`, or
a `PriorityQueue`/`PriorityBlockingQueue` — is octet-sorted into a canonical order; all
other behaviour — uniqueness, mutability, null-policy — belongs
in the constructor.* Order-significant fields are annotated as such in their ASN.1
module (§7).

**Opaque-octet carve-out.** Where a field value is an externally-produced DER
structure — an X.509 `Certificate`, an X.501 `Name` (`X500Principal`), the output of
`java.security.Signature` — the value is carried as **opaque octets, preserved
byte-for-byte** (an `OCTET STRING` holding exactly the source's `getEncoded()` bytes),
and is **never parsed, re-encoded, sorted, or re-canonicalised** by this format. Such
structures are produced and validated by an external authority (a CA, the JDK
certificate factory, a signing key), and their bytes are frequently *not* strict DER —
BER-permissive length forms and legacy `TeletexString` attribute values are common in
the wild. Because these values typically sit under a signature or digest, any
re-encoding pass would change their bytes and break the very signature that
authenticates them. The acceptability of the *inner* bytes is the external authority's
contract (PKIX path validation, the signature check), never this codec's; this codec's
only obligations are the outer TLV framing and the schema `SIZE` bound. An implementer
who re-encodes an embedded certificate or `Name` — even to "normalise" it — corrupts a
signed value. This carve-out is referenced normatively by §7.3, §7.4.1, §7.6, and
§7.7.7.

---

### 3.9 Private Namespace Invariant

Each `@AtomicSerial` class owns a single, private, opaque SEQUENCE on the wire.
That SEQUENCE is exclusively accessible to the `(GetArg)` constructor of the class
that declared it. No other class in the hierarchy — neither a parent nor a child —
can read or write the fields of another class's SEQUENCE through `GetArg`.

Field names are scoped to the declaring class's namespace. A field named `"x"` in
class `Beta` and a field named `"x"` in class `Alpha` are entirely independent: they
occupy separate SEQUENCEs, carry no implied relationship, and require no coordination
between the developers of `Alpha` and `Beta`. One developer may be entirely unaware
of the other's existence.

Field placement within a namespace is permanent. Once a field appears in a class's
`serialForm()`, it occupies that position in that class's SEQUENCE indefinitely. No
refactoring of the Java class hierarchy can relocate a field to a different namespace.
Moving field ownership between classes requires a versioned, breaking wire change.

**Decoding model — `GetArg` as a complete field store.**

The decoder reads the SEQUENCE according to the schema and populates `GetArg`
completely before the `(GetArg)` constructor is called. `GetArg` holds all decoded
field values for the duration of the construction chain. The constructor then
selects the fields it needs via `arg.get(name, defaultValue)`. Fields decoded and
stored in `GetArg` but never requested by any constructor in the hierarchy remain
in memory until `GetArg` goes out of scope at the end of construction, at which
point they become eligible for garbage collection. The construction chain is the
lifetime boundary for all decoded field values.

`arg.get()` is therefore a selection operation over an already-populated store, not
a decoding trigger. The decoding is complete before construction begins.

**`OPTIONAL` in ASN.1 schema is not required for this model.** Optionality is
handled uniformly and completely by the `GetArg` layer. The ASN.1 schema for a
class's SEQUENCE describes only structure — field names, types, and order. It carries
no `OPTIONAL` or `DEFAULT` annotations. Those are constructor concerns, expressed in
the `arg.get(name, defaultValue)` calls and invariant checking, not wire concerns.

**Three decoding cases.**

In all three cases, *"the schema"* refers to the `MarshalledInstance` embedded
schema — the at-marshal-time `AtomicSerialSchemaRecord` chain. This is the schema
that was used to encode the data and is permanently correct for it. The code's
current `serialForm()` may differ from this schema if schema and code have evolved
independently. The `GetArg` layer absorbs this divergence in all cases.

**(a) Schema matches code exactly — primary case.**
The `MarshalledInstance` schema digest matches the digest computed from the local
`serialForm()`. The code's `serialForm()` and the at-marshal-time schema are
identical. Every byte in the SEQUENCE corresponds to a field in the schema. All
fields are decoded and stored in `GetArg`. The constructor requests the fields it
needs; unrequested fields are stored and released to GC after construction.
No bytes are discarded, no fields are absent.

**(b) Schema has more fields than code knows about.**
The `MarshalledInstance` schema was encoded with a newer version than the code's
current `serialForm()`. The schema has fields the code does not request. Those
fields are decoded and stored in `GetArg` but the code never calls `arg.get()` for
them. They are released to GC after construction. No data is lost; no error occurs.
This is the backward-compatibility path: new data, old code.

**(c) Code knows about more fields than the schema has.**
The code's current `serialForm()` has fields that were not present when the
`MarshalledInstance` was encoded — because the schema was added to after this
data was created, or because this class did not exist when the data was written.
Those fields are absent from the `MarshalledInstance` schema and therefore absent
from `GetArg`. `arg.get(name, defaultValue)` returns the declared default. The
constructor applies invariant checking. This is the forward-compatibility path:
old data, new code.

Cases (b) and (c) are the expected operational modes when schema and code evolve
independently. Case (a) is the fast path. In all cases, `@AtomicSerial`'s `GetArg`
layer is the compatibility mechanism that makes independent evolution safe.

*No coordination between developers of different classes in the hierarchy is required
for independent evolution of their respective namespaces.*

---

### 3.10 `@AtomicSerial` Hierarchy Wire Visibility

Only classes that implement `@AtomicSerial` contribute SEQUENCE types to the wire.
A class that does not implement `@AtomicSerial` is **invisible** to the wire format.
Two cases follow directly:

**Non-`@AtomicSerial` subclass (`Bar extends Foo`, only `Foo` is `@AtomicSerial`).**
`Bar`'s state is dropped on serialisation. The wire carries only `Foo`'s SEQUENCE.
Deserialisation produces a `Foo` instance — not a `Bar`. `Bar` does not exist in the
deserialised form. There is no partial reconstruction, no missing fields, and no
default initialisation of `Bar`-specific state. `Bar` opted out of the wire contract.

**Non-`@AtomicSerial` superclass (`Beta extends Alpha`, only `Beta` is `@AtomicSerial`).**
`Beta` is responsible for constructing `Alpha`. `Beta`'s `serialForm()` includes
whatever of `Alpha`'s state must survive serialisation, in `Beta`'s own namespace.
`Beta`'s `(GetArg)` constructor extracts those values and passes them as ordinary
constructor arguments to `super(...)`. `Alpha` has no `(GetArg)` constructor and
receives no `GetArg` directly. `Alpha`'s state lives in `Beta`'s SEQUENCE, under
`Beta`'s field names, which `Alpha` cannot see and need not know about.

The consequence of §3.9 and §3.10 together: the set of SEQUENCE types on the wire
corresponds exactly to the set of `@AtomicSerial` classes in the hierarchy, one
SEQUENCE per class, each private to its class, each evolved independently.

---

### 3.11 Data Independence from Code

*See §2.3 for motivation.*

A DER-encoded `@AtomicSerial` object together with its `AtomicSerialSchemaRecord`
constitutes a self-describing data record. All field names, field types, and field
values are recoverable from these two artefacts alone. The originating Java class is
not required.

**`@AtomicSerial` as the compatibility layer between schema and code.**
Schema and code are independently evolvable artefacts. A service may update its
JAR (fix bugs, add methods) without changing its schema. A schema may gain new
fields without requiring clients to update their code. At any point in time, the
schema embedded in a `MarshalledInstance` and the schema implied by a receiver's
current `serialForm()` may differ. `@AtomicSerial`'s `GetArg` layer is the
compatibility bridge that absorbs this divergence at runtime:

- Code requests fields it knows about via `arg.get(name, default)`
- Data supplies the fields that were present at encoding time
- Missing fields return defaults; extra fields are stored but not accessed
- Neither side needs to know the other's schema version

The `MarshalledInstance` embedded schema is always the authoritative schema for
decoding the data it accompanies. A receiver's current `serialForm()` describes
what its code can process. `GetArg` bridges the difference between the two,
silently and correctly, in both directions. Code that processes `@AtomicSerial`
objects — including LLVM-compiled artifacts (STD-007 §3.6) — does not need to be
recompiled or reconfigured when schemas evolve.

**Normative statement:** Any implementation conforming to this standard MUST be able
to decode a DER-encoded `@AtomicSerial` object into a named field map given only the
DER bytes and the corresponding `AtomicSerialSchemaRecord` chain, without loading
any class from the originating codebase. This capability is required for conformance;
it is not an optional feature.

The named field map produced by this decoding corresponds exactly to the state that
the `GetArg` layer would hold during construction: all field names present in the
schema, each bound to its decoded value or to the declared default if absent. This
map is the data. The class is needed only to transform that map into typed behaviour.

**Schema permanence and data longevity.** Because field placement within a namespace
is permanent (§3.9), the schema for any serialised object is stable for the lifetime
of that object. A schema registered at the time of creation (§12) will correctly
decode the object indefinitely, regardless of subsequent evolution of the class.
Data that was correct when written remains correct when read, provided the schema
is available.

**The schema is the universal key.** The `MarshalledInstance` (§7.8) MUST embed
the schema unconditionally. The schema registry (§12) MUST be append-only. Together
these two requirements ensure that the schema is always available and that data
independence is never compromised by the evolution or retirement of code.

---

### 3.12 Wire Type Model (NORMATIVE)

STD-006's wire type system is a **closed subset**, closed under recursive
composition — a type algebra over three base categories plus a self-describing
`Any` form. This section is the normative statement of that algebra; §7.6 (the six
collection productions) and §4.5 (the `Any` tag registry, tag-encoding discipline)
are its concrete ASN.1 realisation.

```
WireType  ::=  Scalar
            |  AtomicSerialObject          -- a schema-digest-identified @AtomicSerial record
            |  Collection(WireType…)       -- set/bag/orderedset/list/map/orderedmap over WireType(s)
            |  Any                          -- the self-describing CHOICE over the three above

Scalar             ::= boolean | byte | short | int | long | float | double | char
                     | java.lang.String | byte[]              -- the built scalar set
AtomicSerialObject ::= "@AtomicSerial"                        -- concrete class travels via the embedded schema chain (§3.9-§3.11)
Collection(E)      ::= "set:"E | "bag:"E | "orderedset:"E | "list:"E
Collection(K,V)    ::= "map:{"K"}{"V"}" | "orderedmap:{"K"}{"V"}"
Any                ::= [n] IMPLICIT/EXPLICIT CHOICE over Scalar | AtomicSerialObject | Collection
```

**Closure.** The set is closed under `Collection`: `Set<Map<String,List<Foo>>>` is a
legal `WireType` because each nesting level is again one of the categories.
Recursion bottoms out at a scalar, an `@AtomicSerial` object, or `Any`.

**Element-derivation rule (declaration-based, no annotation).** For a collection
field, the element wire-type is derived from the field's **declared generic type**
(the `ParameterizedType` the JDK retains in the class file's generic `Signature`
attribute, JVMS §4.7.9 — recovered via `Field.getGenericType()`), applied
recursively; for a map, the key and value types are recovered and resolved
independently. This is the same discriminator discipline as §3.8's ordering
rule (which keys on the declared collection *class*): the developer's existing
declaration is the sole signal, and **no annotation is read or required.** A
declared element type that is unresolvable at the declaration site — a raw
collection, an unbounded or lower-bounded wildcard, an `Object` element, or a type
variable in a generic `@AtomicSerial` class — resolves to `Any` (below); a bounded
wildcard `? extends B` resolves to `rule(B)`, the upper bound, and remains fully
typed (not `Any`). The rule is total: every field resolves either to a concrete
closed-subset wire-type or to `Any`; `Any` is a rule-selected fallback, never a
developer choice and never the default for a resolvable type.

**The exclusion boundary.** The model deliberately excludes, and a conformant
schema/encoder MUST NOT admit:

1. **Raw arbitrary `Object` graphs.** A field typed `Object` (or a collection whose
   element type is `Object`) has no closed-subset structural type and is not
   silently promoted to "carry anything." It is admissible *only* through the `Any`
   form, and even then only as one of {scalar, `@AtomicSerial` object, collection}
   — never an arbitrary graph. (An interface/abstract-typed field is not a raw
   `Object`: it is a polymorphic `@AtomicSerial` slot whose concrete class
   self-identifies via the schema digest, already covered by the
   `AtomicSerialObject` category. **Enum exception:** a Java `enum` that
   implements a marshalled interface — e.g. `AtomicInputValidation implements
   InvocationConstraint` — cannot be `@AtomicSerial` (and migrating it to a class
   is a binary break), yet is a legitimate closed, inert, canonical value. In a
   polymorphic slot such a value carries a per-VALUE `[7]` CTX_ENUM discriminator
   [`UTF8String(declaringClassName) ++ UTF8String(constantName)`, name-canonical,
   the same wire form as the object-stream bare-enum `[7]`], distinguishing an
   "enum leaf" from an `@AtomicSerial` hierarchy leaf. This is a per-value wire
   fact, **not** a schema-token/schemaDigest change; decode resolves via the
   endpoint loader and gates the enum class by declared-type assignability. See
   STD-008 §17.1.1.)
2. **Arbitrary `Serializable`.** "Any object with a class descriptor" is exactly
   what this standard refuses: it serialises the implementation, defeats
   cross-language consumption and value-equality, and is a gadget surface. Only
   the `@AtomicSerial` closed record form is admitted.
3. **Cycles.** The model is a finite tree (DAG-free by construction): no
   back-references, no object identity on the wire (§3.7). The type model is the
   structural counterpart of that acyclicity requirement.

These three exclusions are what make cross-language production/consumption,
byte-level value-equality, and DER canonicity simultaneously achievable;
admitting any one back would forfeit at least one of the other two.

**The `Any` CHOICE.** `Any` is a context-tagged `CHOICE` over the closed-subset
categories — one context tag per category, no `ENUMERATED` discriminator and no
`tag`+`body` `SEQUENCE` wrapper. The `CHOICE`'s context tag **is** the category
discriminator, carried in place on the element's own encoding:

```asn1
AnyElement ::= CHOICE {
    scalarBoolean        [0]  IMPLICIT BOOLEAN,
    scalarByte           [1]  IMPLICIT INTEGER,
    scalarShort          [2]  IMPLICIT INTEGER,
    scalarInt            [3]  IMPLICIT INTEGER,
    scalarLong           [4]  IMPLICIT INTEGER,
    scalarFloat          [5]  IMPLICIT OCTET STRING,   -- IEEE-754 32-bit, strict-canonical (§7.6, STD-008 §17.3.1)
    scalarDouble         [6]  IMPLICIT OCTET STRING,   -- IEEE-754 64-bit, strict-canonical
    scalarChar           [7]  IMPLICIT INTEGER,         -- Unicode codepoint (BMP non-surrogate)
    scalarString         [8]  IMPLICIT UTF8String,
    scalarBytes          [9]  IMPLICIT OCTET STRING,    -- a byte[] element
    -- gap [10..19] RESERVED for future scalar categories
    atomicSerialObject   [20] EXPLICIT AtomicSerialRecord,  -- schema digest travels in the embedded chain; EXPLICIT preserves the record's own tag
    -- gap [21..29] RESERVED
    canonicalCollection  [30] EXPLICIT CanonicalCollection, -- SET OF 0x31 (set:/bag:/map:); outer tag preserved under EXPLICIT
    orderedCollection    [31] EXPLICIT OrderedCollection    -- SEQUENCE OF 0x30 (orderedset:/list:/orderedmap:); outer tag preserved
}
```

- **Scalars `[0]`-`[9]` are `IMPLICIT`**: a cross-language reader determines the
  primitive category from the context tag alone, and the value bytes past the tag
  are the scalar's ordinary canonical DER — no wrapper.
- **`atomicSerialObject [20]` is `EXPLICIT`, not `IMPLICIT`.** The inner
  `AtomicSerialRecord`'s own tag (a `SEQUENCE` for most records, a proxy-record
  tag for a substituted `@AtomicSerial` class, or DER `NULL` for a stateless
  record) is itself load-bearing: a decoder recognises the record's shape from
  that tag before it ever reads the schema digest. `IMPLICIT` would overwrite that
  tag in place; X.680 §31.2.7 forbids `IMPLICIT`-tagging a `CHOICE`/`ANY`
  alternative whose own outer tag carries meaning (§4.6 states the same rule for
  this standard's tagging mode generally). `EXPLICIT` wraps the tag instead, so
  the inner record's tag survives underneath it.
- **`canonicalCollection [30]`/`orderedCollection [31]` are `EXPLICIT`** for the
  identical reason: the inner `SET OF` (`0x31`)/`SEQUENCE OF` (`0x30`) outer tag
  is the §3.8/§7.6 preserve-vs-canonicalise discriminator and MUST survive.
  `IMPLICIT` would destroy that discriminator the same way it would the
  `atomicSerialObject` record's tag.

**Value-equality is preserved (no wrapper leak).** Under an `[n] IMPLICIT` scalar
tag, the value's bytes past the leading tag octet are unchanged from how the value
would encode with a declared type; under the `atomicSerialObject`/collection
`EXPLICIT` arms, the inner record/collection encoding is byte-identical to the
declared-element form, wrapped rather than overwritten. A value that appears both
in a declared-element collection and in an `Any` collection therefore has the same
post-tag bytes. See §4.2 of `docs/der-type-model-and-element-rule.md` for the
exact scope of this claim (it holds between equal values of the *same*
collection-discipline class; it does not claim byte-equality between differently
disciplined collections that happen to be `.equals`).

**Octet-sort category-grouping is a theorem, not an accident.** Because the
context tag is the leading octet of every `AnyElement` encoding and tags are
distinct and ordered, X.690 §11.6 octet-sort (§3.8, §7.6) groups a canonicalise
collection of `Any` elements by category first, then by value within a category —
a provable property of the leading tag.

**The four decoder fences (NORMATIVE decoder obligations, each conformance-tested
per §9).** `Any` moves decode dispatch from the fixed, digest-covered schema
token to attacker-controlled per-element bytes. That shift is safe only behind
four fences, each a MUST for every conformant decoder (JVM and non-JVM):

1. **`MAX_NESTING` threaded through every `Any` recursion.** The decoder MUST
   carry a depth counter and decrement/bound it on every
   `Any`→`canonicalCollection`/`orderedCollection` step *and* every
   `Any`→`atomicSerialObject` step (whose record may itself contain `Any`-typed
   fields), rejecting before the bound is exceeded. Under a declared type the
   schema token bounds structural depth; under `Any`, nesting depth is
   attacker-controlled, so unbounded recursion is a StackOverflow DoS.
2. **The same `@AtomicSerial` reconstruction gate — `Any` is not a second door.**
   An `Any` `atomicSerialObject [20]` body MUST be reconstructed through the
   identical path as a typed `@AtomicSerial` element: the
   `DeSerializationPermission("ATOMIC")` gate, the endpoint `ResolutionContext`,
   and the class's `check(GetArg)`/deserialising constructor, applied
   hierarchy-wide. `Any` MUST NOT provide an ungated or differently-gated route
   to object reconstruction.
3. **Unknown or mismatched tag → hard reject (fail-secure).** A context tag not
   in the pinned registry (§4.5) — including a reserved-gap tag (`[10]`-`[19]`,
   `[21]`-`[29]`) and any tag `> [31]` — MUST be a decode error. The decoder MUST
   NOT skip the element, MUST NOT default it to any category, MUST NOT continue.
   In the `CHOICE` form this is largely automatic (an unlisted alternative is a
   `CHOICE` decode error), but it is stated normatively so no implementation
   "tolerantly" skips a reserved or out-of-range tag.
4. **Canonical minimal tag encoding + deterministic value-to-category mapping.**
   The context tag MUST be in minimal (definite short/long-form) DER tag
   encoding, and every value MUST map to exactly one category tag (a `String`
   value is always `[8]`, never `[9]`; an `int` is always `[3]`; a canonicalise
   collection is always `[30]`, an ordered one always `[31]`). A non-minimal tag
   encoding or a value encodable under two tags would give two byte forms for one
   value, breaking both octet-sort and value-equality.

Because an `Any` field's schema no longer commits element *types* (only the
container is coerced; the elements are not), per-element type validation shifts
entirely onto the developer's `check(GetArg)` for any field that resolves to
`Any`. The fences above make `Any` safe to decode; they do not make its elements
the types the developer expects — that is the receiving class's responsibility.

**The `Any` tag registry (PINNED NORMATIVE).** There is no separate `ENUMERATED`
to maintain: the `CHOICE`'s context tags above **are** the registry. The full
pinned assignment, registry rules, and reserved-gap disposition are stated in
§4.5, alongside this standard's other distinct-tag and tag-encoding obligations;
the assignment is repeated here for convenience:

| Context tag | Category | Tagging |
|---|---|---|
| `[0]`-`[9]` | scalars (bool, byte, short, int, long, float, double, char, String, byte[]) | `IMPLICIT` |
| `[10]`-`[19]` | **RESERVED** (future scalar categories) | — |
| `[20]` | `@AtomicSerial` object | `EXPLICIT` |
| `[21]`-`[29]` | **RESERVED** | — |
| `[30]` | canonicalise collection (`set:`/`bag:`/`map:`) | `EXPLICIT` |
| `[31]` | ordered collection (`orderedset:`/`list:`/`orderedmap:`) | `EXPLICIT` |
| `> [31]`, or any reserved-gap tag | **UNREGISTERED** — hard reject | — |

The tag set is closed and versioned with the format: exactly three base
categories exist (scalar, `@AtomicSerial` object, collection), so the shape of
the registry is fixed; only within-category refinements (a new scalar, a future
collection discipline) could ever consume a reserved gap, and only by a spec
revision. A reserved tag is not decodable until a spec revision lists it — until
then it is UNREGISTERED and hard-rejected, per fence 3 above.

**Provenance.** This section pins, as normative STD-006 text, the type model and
`Any` form developed in `docs/der-type-model-and-element-rule.md` (design memo,
board-reviewed). That memo's §7 (the `SchemaGenerator` enabling mechanism) and its
edge-case table (§6) remain implementation guidance, not spec text; nothing in
this section requires a code change to be true of the wire format as designed —
it requires a code change only to be *produced* by the current `SchemaGenerator`
(the memo's deferred auto-wiring item).

---

## 4. Encoding Conventions

### 4.1 Base Encoding

All wire objects are encoded using ASN.1 **DER** (X.690). Integers are encoded as
ASN.1 `INTEGER`; the legacy big-endian fixed-width counters in the current format
(e.g. `subjectCount : u16`, `domainCount : 4B BE`) are replaced by `INTEGER` with a
schema `SIZE`/value constraint expressing the same ceiling. **[PROPOSED]**

### 4.2 String Encoding

Text fields (principal class names, principal names, URIs) are `UTF8String`.
This replaces the ad-hoc `UTF-8 : className / name` pairs in the `0x02` block.

### 4.3 Digests and Algorithms

Content digests are encoded as `AlgorithmIdentifier` (the X.509 structure, reused
for encoding consistency with SVIDs) plus an `OCTET STRING` of the digest bytes.
The allowed algorithm OIDs correspond to the DirtyChai `DigestCodeSource` allow-list
(SHA-256, SHA-384, SHA-512, SHA-512/256, SHA3-256, SHA3-384, SHA3-512). An unknown
or disallowed OID is a decode failure (fail-secure). **[PROPOSED — confirm OID set]**

`AlgorithmIdentifier` is the RFC 5280 §4.1.1.2 structure: `SEQUENCE { algorithm
OBJECT IDENTIFIER, parameters ANY DEFINED BY algorithm OPTIONAL }`. For every OID in
the allow-list above, a conforming STD-006 encoder MUST omit `parameters` entirely,
and a conforming decoder MUST reject an `AlgorithmIdentifier` in which `parameters`
is present — including `NULL` (fail-secure; one logical value has exactly one
encoding). This follows the stricter of the CMS conventions (RFC 5754 prefers absent
for SHA-2; RFC 8702 requires absent for SHA-3) and removes any dependency on the 1988
`ANY` construct for the algorithm set this standard allows. This rule applies to
STD-006-native `AlgorithmIdentifier` values only; `AlgorithmIdentifier`s embedded
inside opaque-octet structures (X.509 certificates, `X500Principal`, §3.8 carve-out)
are carried verbatim and never inspected or re-encoded.

### 4.4 Object Identity / Type Discrimination

**RATIFIED (Peter, 2026-07-06): the top-level type discriminator is OID-rooted**
(chosen over `ENUMERATED` for forward-compatibility and self-description — a new
wire type is a new arc under the ATOMIC DER `wireTypes` root, allocatable without a
coordinated enumerant-registry edit, and a decoder that does not recognise an OID can
still name it). Each top-level wire object is wrapped in a `TypedWireObject` structure
carrying one explicit `OBJECT IDENTIFIER` type tag, so a decoder selects the correct
schema without inferring it from context:

```asn1
TypedWireObject ::= SEQUENCE {
    typeId  OBJECT IDENTIFIER,   -- the wire type's arc under …atomicDer.wireTypes
    body    OCTET STRING         -- canonical DER of the identified wire type (opaque
                                 -- to this envelope; decoded against the schema the
                                 -- typeId selects). Carried as an OCTET STRING so the
                                 -- envelope never re-encodes the body (same discipline
                                 -- as SignedObject.tbs, §7.4.1).
}
```

**Single discriminator (board H3 — "one discriminator, not two").** The `typeId`
OID is the *sole* type discriminator. The envelope MUST NOT additionally restate the
category the OID already fixes (no redundant `ENUMERATED tag` beside the OID, no
second "kind" field): a second statement of the type is a lying-encoding hazard (two
places that can disagree, forcing a decoder to reject the mismatch). The `body`'s own
inner universal tag is not a *second* discriminator here — it is opaque `OCTET STRING`
content the envelope does not interpret; the `typeId` alone routes it.

**OID root — registrant Zeus Project Services Pty Ltd; PEN placeholder pending
registration.** The OID root belongs to **Zeus Project Services Pty Ltd** (the legal
company). One IANA Private Enterprise Number (PEN) roots *all* the company's projects;
JGDMS is a sub-arc, ATOMIC DER a sub-arc of JGDMS, and the `TypedWireObject` `typeId`
values live under `…atomicDer.wireTypes`. The arc shape is:

```
1.3.6.1.4.1.<PEN>   Zeus Project Services Pty Ltd   -- IANA PEN, PLACEHOLDER pending registration
        .1  jgdms
            .1  atomicDer
                .1  wireTypes    -- TypedWireObject type-ID subtree

-- individual wire types are children of …atomicDer.wireTypes, e.g.
--   marshalledInstance    …<PEN>.1.1.1.1
--   unicastResponse       …<PEN>.1.1.1.2
--   accessControlContext  …<PEN>.1.1.1.3
```

Written as an ASN.1 value assignment (validation module):

```asn1
zeusProjectServices OBJECT IDENTIFIER ::=
  { iso(1) identified-organization(3) dod(6) internet(1) private(4)
    enterprise(1) 999999 }   -- PLACEHOLDER PEN — replace on IANA assignment
jgdms       OBJECT IDENTIFIER ::= { zeusProjectServices 1 }
atomicDer   OBJECT IDENTIFIER ::= { jgdms 1 }
wireTypes   OBJECT IDENTIFIER ::= { atomicDer 1 }
```

**NORMATIVE placeholder note.** `<PEN>` (written `999999` in the module so it compiles)
is a **clearly-marked PLACEHOLDER**. Zeus Project Services Pty Ltd will register an
IANA Private Enterprise Number, and `<PEN>` **MUST** be replaced with the assigned
number **before v1.0**. On assignment, the number is replaced in one edit (module
value assignment + this section) and every `wireTypes` child OID below it becomes
normative; the arc *shape* (`zeusProjectServices → jgdms → atomicDer → wireTypes`) is
already fixed.

### 4.5 Module Validity: Distinct-Tag Rule, No DEFAULT, Profile Size Ceilings

**Distinct-tag rule (NORMATIVE).** Every `OPTIONAL` component in a `SEQUENCE` MUST
carry a tag distinct from the tag of every component that can immediately follow it
(X.680; a module violating this is not valid ASN.1 and standard tooling rejects it).
Where a collision would otherwise arise, the `OPTIONAL` component carries an
`IMPLICIT` context tag (`[0]`, `[1]`, …). Ad-hoc positional disambiguation — e.g.
counting how many same-tagged TLVs remain in the enclosing `SEQUENCE` — is
**forbidden**: it is undecodable by schema-driven tooling and defeats the
language-neutrality goal. (v0.13 fixed violations in §7.3, §7.6 `Permission`,
§7.7.5, §7.7.7, and — by field deletion — §7.8.) The `Any` `CHOICE` (§3.12) is the
same discipline applied to a `CHOICE` rather than a `SEQUENCE`: its context tags
`[0]`-`[9]`, `[20]`, `[30]`, `[31]` are the pinned, closed **`Any` tag registry**
(§3.12), with `[10]`-`[19]`/`[21]`-`[29]` reserved and any unregistered or
out-of-range tag a hard decode reject.

**No `DEFAULT` (NORMATIVE).** No module in this standard uses `DEFAULT`. DER
(X.690 §11.5) requires a component equal to its `DEFAULT` value to be *omitted*
from the encoding; an implementation that encodes it explicitly produces
non-canonical bytes that silently break the two places byte equality is
load-bearing — `EntryRecord` template matching (§7.7.2) and every signed record.
Rather than rely on all implementations honouring the omission rule, fields that
previously carried `DEFAULT` are mandatory. (`OPTIONAL` in `@AtomicSerial` object
modules is likewise being removed — §11 note — for the independent reason that the
`GetArg` layer owns optionality.)

**Profile size ceilings [PROPOSED — confirm numbers].** Design principle 5 requires
every variable-length field to have a schema- or profile-declared maximum; a bare
`SIZE(0..MAX)` satisfies neither. The following named ceilings are the profile
bounds for every `MAX` occurrence in §7; a conforming decoder (JVM or not) enforces
them identically, rejecting before allocation. §7.2/§7.3's certificate- and
domain-count ceilings are merged into this same table (`maxDomains`/`maxCerts`/
`maxCertLen`/`maxDigestLen`) rather than kept as separate per-section names — the
`UrlCodeSourceRecord` and `DigestCodeSourceRecord` certificate paths share one bound.

`maxCollection` is written `SIZE(0..maxCollection)` on the six §7.6 collection types,
**inclusive** — exactly `maxCollection` (65536) elements/entries are accepted and
`maxCollection + 1` is rejected before allocation. **Every** conformant decoder MUST
enforce this cap, JVM and non-JVM alike: it is enforced by the built Java codec (branch
`der-collection-codec`) on all six collection loops, so it is not a non-JVM-only
obligation.

| Constant | Value | Applies to |
|---|---|---|
| `maxFields` | 65535 | `AtomicSerialSchemaRecord.fields`, `EntrySchemaRecord.fields`, `EntryRecord.fieldValues`, `EntryTemplate.fieldValues` |
| `maxCollection` | 65536 | §7.6 collection-field element/entry count — the six discipline types `CanonicalSet`/`CanonicalMultiset`/`CanonicalMap`/`OrderedSetField`/`ListField`/`OrderedMapField` (`SIZE(0..maxCollection)`, inclusive; per-type schemas MAY declare tighter bounds). Enforced by **every** decoder, JVM and non-JVM (built codec, branch der-collection-codec). |
| `maxStackFrames` | 2048 | `ThrowableRecord.stackTrace` — **RATIFIED (Peter, 2026-07-24, item 21)** |
| `maxCauseDepth` | **12** | `ThrowableRecord.cause` nesting — **RATIFIED (Peter, 2026-07-24, item 21; was proposed 64)**: under the nested-`@AtomicSerial` carrier realization (`DerThrowableForm`) each cause level consumes one codec nesting level and `MAX_NESTING`=16 makes 64 structurally unreachable; a consistency test pins `maxCauseDepth + 2 ≤ MAX_NESTING`. Capture truncates with an explicit marker (boundary class + message preserved); decode rejects over-ceiling wire fail-secure |
| `maxSuppressedPerNode` | 32 | `ThrowableRecord.suppressed` per node — **RATIFIED (Peter, 2026-07-24, item 21)** |
| `maxThrowableNodes` | 128 | total nodes per carried fault tree — **RATIFIED (Peter, 2026-07-24, item 21)**; carrier `className` additionally bounded 1..2048 octets |
| `maxGroups` | 128 | discovery `groups` sequences (§7.7.7, §7.7.8) |
| `maxKnownServiceIds` | 256 | `MulticastRequestRecord.knownServiceIds` (datagram-bounded anyway) |
| `maxOperations` | 1024 | `ServiceSpecRecord.operations` |
| `maxParameters` | 255 | `OperationDescriptor.parameters` |
| `maxInterfaces` | 64 | `ServiceTemplateRecord.requiredInterfaces` |
| `maxDomains` | 4096 | `AccessControlContextRecord.domains` (§7.2) |
| `maxCerts` | 100 | `UrlCodeSourceRecord.certificates` (§7.2) and `DigestCodeSourceRecord.certificates` (§7.3) — shared bound |
| `maxCertLen` | 65536 | per-certificate `OCTET STRING` length, both §7.2 and §7.3 certificate paths |
| `maxDigestLen` | 512 | `DigestValue.digest` (§7.2) |
| `maxChainRecords` | 64 **[RATIFIED — Peter, 2026-07-21 (dedup item 7) / T6 merge 2026-07-24]** | §7.8 schema chain — maximum `AtomicSerialSchemaRecord` SEQUENCEs per encoded chain, at **every** chain decode site: the top-level `MarshalledInstanceRecord.schemaBytes` and each nested `@AtomicSerial` field record's embedded chain. Inclusive: exactly 64 records accepted, 65 rejected. Metered **during** the chain-decode loop (the ceiling-breaching record is the last one parsed), not checked once at entry. |
| `maxChainBytes` | 65536 **[RATIFIED — Peter, 2026-07-21 (dedup item 7) / T6 merge 2026-07-24]** | §7.8 schema chain — cumulative encoded byte length of one chain, same two sites as `maxChainRecords`. Inclusive: a chain of exactly 65536 bytes accepted, 65537 rejected. Metered during the chain-decode loop. |

**Chain-ceiling admissibility note [RATIFIED with the values — Peter, 2026-07-21/24].** `maxChainBytes`
deliberately **tightens** the base-admissible set: a single `AtomicSerialSchemaRecord` at
the `maxFields` (65535) and `className`/`wireType` (1024-byte) ceilings could alone encode
to tens of megabytes, so such a record — while individually legal against the per-record
bounds — is not encodable inside a chain. This is intentional pre-release tightening, not
an oversight: the deepest real `@AtomicSerial` hierarchy in the JGDMS repository is **4
records** (`ConstrainableRegistrarEvent → RegistrarEvent → ServiceEvent → RemoteEvent`),
the largest real `serialForm()` is ~11 fields, and real encoded chains are under 2 KiB —
64 records / 64 KiB is 16×/32× headroom over the measured maxima, not a target. The
per-record ceilings (`maxFields`, the SIZE bounds on `className`/`wireName`/`wireType`)
continue to apply to each record individually; the chain ceilings additionally bound the
aggregate. **One profile with Appendix C (alignment per §C.8.1, ratified 2026-07-21):**
these chain ceilings are the released format's **admissibility bounds** — the same
constants, the same admissibility statement, everywhere chains are parsed (base
record-level decode and the Appendix C stream layer alike; the stream layer adds its own
table ceilings `maxDistinctChainsPerStream` = 256 and `maxDedupTableBytes` = 1 048 576,
§C.8.1). There is no marked/unmarked or stream/base profile split: a class whose chain
exceeds these bounds is not encodable in the released format anywhere — a loud
encode-time error on the sender, a decode-time reject on the receiver, never a silent
fallback.

```asn1
maxFields          INTEGER ::= 65535
maxCollection      INTEGER ::= 65536
maxStackFrames     INTEGER ::= 2048
maxCauseDepth      INTEGER ::= 12   -- ratified 2026-07-24 (item 21); see §4.5 table note
maxSuppressedPerNode INTEGER ::= 32
maxThrowableNodes  INTEGER ::= 128
maxGroups          INTEGER ::= 128
maxKnownServiceIds INTEGER ::= 256
maxOperations      INTEGER ::= 1024
maxParameters      INTEGER ::= 255
maxInterfaces      INTEGER ::= 64
maxDomains         INTEGER ::= 4096
maxCerts           INTEGER ::= 100     -- shared: UrlCodeSourceRecord and DigestCodeSourceRecord cert paths
maxCertLen         INTEGER ::= 65536
maxDigestLen       INTEGER ::= 512
maxChainRecords    INTEGER ::= 64      -- RATIFIED §7.8 schema chain record count, inclusive
maxChainBytes      INTEGER ::= 65536   -- RATIFIED §7.8 schema chain cumulative bytes, inclusive
```

### 4.6 Tagging Mode (NORMATIVE)

Every ASN.1 module in this standard is `DEFINITIONS EXPLICIT TAGS`. Every context tag
this standard uses is additionally written `IMPLICIT` explicitly at its use site
(e.g. `certificates [0] IMPLICIT SEQUENCE …`, `nullCs [0] IMPLICIT NULL` in §7.2). The
module default therefore governs only future untagged additions — it never silently
changes the meaning of a tag already written down, because every tag this standard
defines spells out its own disposition.

EXPLICIT is chosen as the defensive default, for two reasons:

1. It keeps the underlying universal tag visible under every context tag, which is
   one fewer thing a non-JVM decoder can get wrong when a field is added later and a
   spec editor forgets to write `IMPLICIT`.
2. X.680 §31.2.7 forbids `IMPLICIT` tagging of a `CHOICE` or `ANY` alternative unless
   that alternative itself carries an explicit tag. Every `CHOICE` in this standard
   (`ReducingDomainRecord` §7.2, `ProxyDescriptor` §7.7.4, `EntryFieldValue` §7.7.2)
   already tags each arm explicitly, so `EXPLICIT TAGS` forecloses this hazard for
   any future `CHOICE` arm without relying on every editor remembering the rule.

This is **not** a claim that `EXPLICIT TAGS` and `IMPLICIT TAGS` produce identical
bytes for this module — they do not in general, and did not for the three
`ReducingDomainRecord` arms before this revision marked them `IMPLICIT` (§7.2). The
correct and now-true statement is narrower: because every context tag in this
standard is written `IMPLICIT` explicitly, the module-level `EXPLICIT`/`IMPLICIT`
default is inert for this standard's own types — it matters only for a future
addition that introduces a bare `[n]` tag without stating its disposition, which
conformance to this section forbids.

This choice is orthogonal to §4.4's open item (OID-rooted vs `ENUMERATED` type
discrimination) — that item is about *which* discriminator mechanism to use; this
section is about how *any* context tag in this standard's modules is resolved to
bytes once written.

---

## 5. Wire-Format Selection and Coexistence

JGDMS 4.0 carries two marshalling formats — DER (this standard) and legacy Java Object
Serialization (JOSS) — which must coexist during migration from the 3.X series. The
selection mechanism is **built** (JGDMS-STD-008 §18.3); this section documents it and
supersedes the earlier `WireFormat`/protocol-version-byte sketch.

### 5.1 `MarshallingFormat` — selection as an `InvocationConstraint`

The marshalling format is named by a first-class `InvocationConstraint`,
`net.jini.core.constraint.MarshallingFormat`, resolved by the existing JERI constraint
machinery exactly as `Integrity`, `Confidentiality`, and `AtomicInputValidation` are:

- `MarshallingFormat.ATOMIC_DER` — identifier `"JGDMS-STD-006/ATOMIC-DER"`. (This is the
  ATOMIC DER format; the constraint symbol and the wire identifier string were **renamed**
  from the former `DER` / `"JGDMS-STD-006/DER"` to match the format's proper name. This
  was a deliberate **on-wire format-identifier change**, ratified 2026-07-10 and applied
  before v1.0 while STD-006 is a pre-1.0 DRAFT with no deployed peers.)
- `MarshallingFormat.JOSS` — identifier `"JOSS"`.

The constraint carries the format **identifier string** rather than being an
enumeration, so further formats (e.g. CBOR) can be added without a wire change; the
identifier is the same self-describing `payloadFormat` that `MarshalledInstance` carries
as first-class state (§7.8, STD-008 §13). There is deliberately **no `ANY` value** — the
*absence* of a `MarshallingFormat` requirement is the negotiable case.

A `MarshallingFormat` requirement is a **policy assertion checked fail-fast**, not a
per-call negotiation: the codec is fixed at export time by the proxy's
`InvocationLayerFactory`. A DER service exports with `AtomicDerILFactory` (yielding an
`AtomicDerInvocationHandler`/`Dispatcher` pair); a JOSS service exports with the basic
factory. On the client, `BasicInvocationHandler.requireMarshallingFormat` throws
`UnsupportedConstraintException` *before bytes leave the client* if a required format
does not match the proxy's configured codec; on the server,
`BasicInvocationDispatcher.verifyAndStripMarshallingFormat` rejects a mismatched
requirement and strips the constraint before the transport sees it. So requiring
`MarshallingFormat.ATOMIC_DER` carries the same fail-before-transmission guarantee already given
for authentication and confidentiality.

The constraint is decode *policy*; the proxy's configured codec is decode *mechanism*. A
receiver always needs the mechanism (it must know how to decode an inbound request) — but
it needs the `MarshallingFormat` *class* only to express or enforce a *requirement*. A
client that simply uses whatever codec its proxy was built with never references the class.

### 5.2 No protocol-version-byte successor

DER is **not** a new value of the `BasicInvocationDispatcher` request version byte. That
byte (`0x00` `PREVIOUS_VERSION`; `0x01` `VERSION` — shipped in 3.X; `0x02`
`VERSION_WITH_PRINCIPALS_AND_ACC` — unreleased) remains private to the JOSS invocation
layer. DER is a **separate invocation layer** — the `AtomicDer*` handler/dispatcher pair
selected per-proxy by the `InvocationLayerFactory` as in §5.1 — not a discriminator octet
in the JOSS dispatcher's stream. The earlier "version byte gains a `0x03` successor"
sketch is withdrawn.

> **[NOTE — envelope vs payload]** `AtomicDerInvocationDispatcher` inherits
> `BasicInvocationDispatcher.dispatch()` and overrides only the arg/return marshal
> streams. The call *envelope* (the request preamble — integrity/atomic flags, user
> `Subject`s, and the serialized `AccessControlContext`) is therefore still JOSS-marshalled
> even on DER calls. DER-ising the envelope is tracked as a migration item (STD-008 §18.3);
> see §5.4 Tier 0.

### 5.3 Coexistence: the proxy carries its codec; DER rides as downloaded code

Coexistence uses the standard Jini property that **a proxy carries its own invocation
layer**. A client "discovers" a service's format by the proxy it receives from the lookup
service — there is no separate format-negotiation protocol. Consequently:

- The **DER codec is mobile code.** `jgdms-der` and the `AtomicDer*` JERI classes ship in
  the downloadable `-dl` codebase, and the DER object stream carries no codebase annotation
  (§8). A 4.0 service proxy's codebase is provisioned over the **authenticated** bootstrap
  path (`CodebaseAccessor` / `ProxyCodebaseSpi`): the bootstrap proxy is reconstructed from
  local classes, authenticated (SPIFFE/TLS server principals), and only *then* are
  `DownloadPermission`/`DeSerializationPermission` granted and the JARs fetched —
  **authentication strictly precedes download** (no in-stream-annotation fallback; §8). A
  client need not have the DER codec pre-installed.
- A client therefore needs, **locally**, only: the Atomic-JOSS engine for the
  discovery/bootstrap layer, and a thin **DER-awareness shim** — the 4.0 `MarshalledInstance`
  (to recognise `payloadFormat` and route an inbound DER payload to the codec) plus the
  uncoupled `AtomicSerial` engine. `MarshallingFormat` is **not** required locally to *use* a
  DER proxy; it is needed only to *require* DER (§5.1).
- Deserialization is **CNFE-tolerant** across the version gap. A proxy whose constrained
  method signatures reference types the client lacks does not fault on deserialization:
  `net.jini.constraint.StringMethodConstraints` conveys method names and parameter **types by
  name** (string), expressly "to avoid `ClassNotFoundException`s … when classes don't exist."
  (Verified: a `StringMethodConstraints` naming a non-existent parameter type round-trips with
  no CNFE — `StringMethodConstraintsCnfeTest`.) Combined with format-as-string conveyance and
  `@AtomicSerial` schema tolerance (a newer `MarshalledInstance`'s extra fields are dropped,
  not faulted), an older client receives and uses a newer proxy without crashing.

The two directions of a 3.X ↔ 4.0 mixed deployment:

- **3.X client → 4.0 service:** works iff the 4.0 service offers a JOSS-capable endpoint for
  it (the 4.0 JOSS dispatcher still accepts `0x00`/`0x01`).
- **4.0 client → 3.X service:** the 3.X service offers only a JOSS proxy; the client uses
  JOSS unless it *requires* `MarshallingFormat.ATOMIC_DER`, in which case the call correctly fails
  fast.

> **[OPEN — dual-export]** A no-full-shutdown 3.X→4.0 upgrade needs a single 4.0 service to
> offer a JOSS proxy *and* a DER proxy concurrently, so old and new clients both reach it
> during the window. Whether the export path supports concurrent dual-export is unconfirmed;
> the `InvocationLayerFactory` is one-per-export.
>
> **[TO CONFIRM]** That no `AtomicDer` export path bakes a `MarshallingFormat` object into a
> transmitted proxy's constraints. If none does, `MarshallingFormat` never travels and is
> purely a client-side opt-in (consistent with §5.1).

### 5.4 Migration order (threat-model driven)

`@AtomicSerial`-over-JOSS removes post-parse gadget execution but retains the JOSS
stream-grammar parser (handle/back-reference graph, allocate-before-validate, in-stream
class resolution) as residual attack surface. Severity is dominated by *who can deliver the
bytes*, so migrate the least-trusted edges first:

- **Tier 0 — the invocation envelope.** Even DER calls still JOSS-deserialise the request
  preamble (user `Subject`s + `AccessControlContext`, §5.2). One fix removes a JOSS
  deserialisation from *every* call path; do it first (STD-008 §18.3).
- **Tier 1 — discovery.** Multicast announcement/request and the unicast response
  deserialise a proxy from the least-authenticated position.
- **Tier 2 — broad-aperture servers** ingesting untrusted marshalled content from many
  authenticated clients: the lookup service, JavaSpaces (entries), the transaction manager
  (participants), the event mailbox.
- **Tier 3 — clients** deserialising proxies and return values from semi-trusted peers.
- **Tier 4 — local persistence** read (JOSS retained read-only; see §5.5) — last, off the
  network.

Requiring `MarshallingFormat.ATOMIC_DER` on an endpoint is the **firewall**: it fail-fast rejects
any call that would otherwise ride JOSS, so a security-sensitive endpoint is never silently
downgraded. On a transport without confidentiality+integrity, DER-requiring endpoints MUST
require `MarshallingFormat.ATOMIC_DER` (no negotiable fallback), since a plaintext format selection
is downgrade-attackable.

### 5.5 End state

After the migration window, JOSS is removed from the wire entirely:

- New and migrated deployments are DER-only; there is no `WireFormat.JAVA` negotiation to
  attack.
- The SCAP `AtomicSerialComplianceVisitor` already flags non-`@AtomicSerial` classes as
  `DANGEROUS` — precisely the classes that cannot ride the DER path — so the deprecation
  pressure and the safety pipeline point the same way.
- The Atomic-JOSS engine is retained **read-only** solely to migrate 3.X-persisted state
  (JOSS-atomic streams, recognised by their `0xAC 0xED` magic vs DER's `0x30`). It belongs in
  a **one-shot offline migrator**, not the network-facing runtime, so the JOSS grammar parser
  leaves the long-running TCB at cutover (mirrors STD-003 §14 phased approach).

---

## 6. Core Wire Types — Inventory

The following types require ASN.1 modules. Grouped by subsystem. Status reflects how
well the current field layout is captured in available documentation.

| # | Wire type | Subsystem | Source of truth today | Schema status |
|---|---|---|---|---|
| 6.1 | `UserSubjectBlock` (the `0x02` multi-Subject block) | Identity / dispatch | blog appendix; `BasicInvocationDispatcher` | drafted §7.1 |
| 6.2 | `Principal` record | Identity | className + name pairs | drafted §7.1 |
| 6.3 | `AccessControlContextRecord` | Authorization transport (caller's *reducing* domain set, codebases only — no principals) | `RemoteContextCodec.java` (jgdms-jeri) | reconciled §7.2 |
| 6.4 | `ReducingDomainRecord` (codebase identity only; `kind` = null-CS / DigestCodeSource / URL) | Authorization transport | `RemoteContextCodec.java` (jgdms-jeri) | reconciled §7.2 |
| 6.5 | `DigestCodeSourceRecord` | Code identity | `DigestCodeSource.java` | **RESOLVED** §7.3 |
| 6.6 | `AnalysisRequest` | SCAP | `au.net.zeus.jgdms.api.codebase.AnalysisRequest` | **RESOLVED** §7.4 |
| 6.7 | `JarAnalysisReport` | SCAP | `…api.codebase.JarAnalysisReport` | **RESOLVED** §7.4 |
| 6.8 | ~~`SignedVerdict`~~ (no such class — signatures inline) | SCAP | — | **RESOLVED** §7.4 |
| 6.9 | `RegistryVerdict` | SCAP | `…api.codebase.RegistryVerdict` | **RESOLVED** §7.4 |
| 6.10 | `CrashReport` | SCAP | `…api.codebase.CrashReport` | **RESOLVED** §7.4 |
| 6.11 | `PermissionGrant` / `DigestGrant` | Policy | STD-004 (policy syntax) | **RATIFIED — string-form, not a wire record** §7.5 |
| 6.12 | Substituted standard types (boxed primitives, `URI`/`URL`, `Date`, `UID`, `MarshalledObject`, `StackTraceElement`, `X500Principal`, `Permission`, `Throwable`, `Properties`) | Cross-cutting | `AtomicMarshalOutputStream.replaceObject()` | drafted §7.6 |
| — | Collections (`Map`/`Set`/`List`) | Cross-cutting | `AtomicMarshal*` carriers | **collapsed to native `SEQUENCE OF` — no wire type (§3.8, §7.6)** |
| 6.13 | `EntrySchemaRecord` | Jini Entry identity | STD-005 `entryForm()` / `EntryClass.computeSerialEntryHash()` | drafted §7.7.1 |
| 6.14 | `EntryRecord` | Jini Entry instance | STD-005 `@SerialEntry` / `EntryRep` | drafted §7.7.2 |
| 6.15 | `ServiceID` | Jini service identity | `net.jini.core.lookup.ServiceID` | drafted §7.7.3 |
| 6.16 | `ProxyDescriptor` / `JeriEndpointRecord` / `ServiceSpecRecord` | Non-JVM service participation | `DynamicProxyCodebaseAccessor` / new | drafted §7.7.4 |
| 6.17 | `ServiceItemRecord` / `EntryTemplate` / `ServiceTemplateRecord` | Jini registration / lookup | `net.jini.core.lookup.ServiceItem` / `ServiceTemplate` | drafted §7.7.5 |
| 6.18 | `LeaseRecord` / `LeaseRenewalRecord` / `LeaseCancellationRecord` | Jini leasing | `net.jini.core.lease.Lease` | drafted §7.7.6 |
| 6.19 | `MulticastAnnouncementRecord` / `MulticastRequestRecord` | Jini multicast discovery | `X500Server` / `X500Client` / `EndpointBasedProvider` | drafted §7.7.7 |
| 6.20 | `UnicastResponseRecord` | Jini unicast discovery response | `EndpointBasedServer.writeUnicastResponse()` / `Plaintext.writeUnicastResponse()` | drafted §7.7.8 |

---

## 7. Core Wire Types — ASN.1 Modules

> All modules are illustrative scaffolds. Field names mirror current implementation
> terminology where known. **[OPEN]** markers indicate fields whose exact type,
> cardinality, or constraint must be confirmed against the implementation.

### 7.1 Identity: UserSubjectBlock and Principal

Replaces the `0x02` block (`subjectCount:u16` ≤ 16, per-Subject
`principalCount:u16` ≤ 64, per-Principal `className`/`name` UTF-8).

```asn1
Principal ::= SEQUENCE {
    className   UTF8String,
    name        UTF8String
}

UserSubject ::= SEQUENCE {
    -- ORDER-SIGNIFICANT (§3.8): the principal sequence order is intrinsic.
    -- A SPIFFE principal chain / X.509 path is leaf-first; encoders MUST NOT
    -- reorder, decoders MUST NOT deduplicate or canonicalise.
    principals  SEQUENCE SIZE(0..64) OF Principal
    -- credentials are NOT transmitted: UserSubject is principals-only on the wire
    -- (STD-003 §3.5: read-only, principals only, no credentials)
}

UserSubjectBlock ::= SEQUENCE {
    -- ORDER-SIGNIFICANT (§3.8): outermost-first; subjects[0] is the primary user.
    subjects    SEQUENCE SIZE(0..16) OF UserSubject
}
```

**Notes / [OPEN]:**
- **Order is significant at two levels** (§3.8): the outer `subjects` sequence is
  outermost-first so `subjects[0]` remains the primary user (`Subject.current()`),
  and the inner `principals` sequence preserves SPIFFE/X.509 chain order. Both are
  intrinsic-order sequences, not behavioural collections — they must not be sorted,
  deduplicated, or canonicalised by any conforming implementation.
- Principal class name is currently a trust-sensitive field validated against the
  `PRINCIPAL_CTORS` allow-list on the receiving side (STD-003 / context_8). The
  schema does not enforce the allow-list — that remains a `check()`-layer/dispatcher
  concern. **Confirm this stays in the validation layer, not the schema.**

### 7.2 Authorization: AccessControlContext and reducing domains

> **Reconciled with implementation (v0.12).** The schema below mirrors
> `net.jini.jeri.RemoteContextCodec` — the live transport (client
> `BasicInvocationHandler.marshal`, server `BasicInvocationDispatcher.unmarshal`). The
> earlier `[httpmdCount:4B BE][DomainIdentityRecord…][anonCount:4B BE]` /
> `AccessControlContextSerializer` model (principals on the wire, a verifiable-vs-`anonCount`
> split) was an **earlier implementation (`org.apache.river.api.io`), since replaced** by
> `RemoteContextCodec`; those parts are withdrawn. The
> draft's `jrt:/java.base` exclusion rule, by contrast, was **correct but missing** from
> the implementation — a privilege-escalation bug, now fixed and retained as a normative
> obligation below. See the design note at the end of this section.

The transmitted `AccessControlContext` is the remote caller's **reducing** domain set:
codebase identities **only, with no principals**. It is the *subtractive* half of the
two-gate workload-authorisation model — the permission ceiling that reduces the
dispatched call. The *additive* workload identity is supplied at the receiver by
stamping the principals of the authenticated mTLS `RemoteSubject` onto every
reconstructed domain; **principals are never carried on this wire.** (Contrast the
user-identity path, §7.1: `UserSubject` principals and their raw JWTs *are* carried and
independently verified at the receiver.)

```asn1
-- Codebase identity of one reducing domain. NO principals, NO permissions.
-- Arms are IMPLICIT (§4.6): NULL/SEQUENCE types, so IMPLICIT tagging of a CHOICE
-- alternative is legal (X.680 §31.2.7 forbids it only for CHOICE/ANY alternatives
-- without an explicit tag, which is not the case here — each arm has its own [n]).
ReducingDomainRecord ::= CHOICE {
    -- A genuinely null-CodeSource domain at the sender (dynamic proxy, lambda,
    -- bootstrap). It is a real reducer; dropping it would ELEVATE authority.
    -- Reconstructed codebase-less + principal-bearing -> matches principal-only grants.
    nullCs      [0] IMPLICIT NULL,

    -- A DirtyChai java.security.DigestCodeSource: self-describing codebase identity
    -- pinned by content hash. Reconstructed verbatim; the digest is re-verified later,
    -- at policy/class-load time via DigestGrant -- NOT in the codec.
    digest      [1] IMPLICIT DigestCodeSourceRecord,   -- see §7.3

    -- Any other CodeSource that carries a location URL.
    url         [2] IMPLICIT UrlCodeSourceRecord
}

UrlCodeSourceRecord ::= SEQUENCE {
    locationUri     UTF8String,                                              -- RFC 3986; locator
    certificates    SEQUENCE SIZE(0..maxCerts) OF OCTET STRING (SIZE(1..maxCertLen)) OPTIONAL -- each = X509Certificate.getEncoded(), opaque & verbatim (§3.8)
}

AccessControlContextRecord ::= SEQUENCE {
    -- ORDER-SIGNIFICANT (§3.8): captured domain order is preserved end to end.
    -- EVERY reducing domain is transmitted inline; there is no verifiable/anonymous
    -- split and no separate count of suppressed domains.  The ONE exclusion is the
    -- platform jrt:/java.base module domain (see the java.base security note below):
    -- the encoder MUST drop it and the decoder MUST refuse to reconstruct it.
    domains     SEQUENCE SIZE(0..maxDomains) OF ReducingDomainRecord     -- §4.5
}

DigestValue ::= SEQUENCE {
    algorithm   AlgorithmIdentifier,
    digest      OCTET STRING (SIZE(1..maxDigestLen))
}
```

**As-built byte framing (`RemoteContextCodec` — transitional; documents the current
transport only).** Exactly one of the two encodings in this section is normative for
any given conformance claim: an implementation of **this standard's DER format**
implements the `AccessControlContextRecord` ASN.1 module above; the byte framing
below documents the *as-built* encoding that rides the proxy's injected
`ObjectOutput`/`ObjectInput` today (DER under a DER-exported proxy, JOSS otherwise)
and is superseded when the invocation envelope migrates at Tier 0 (§5.4). It is
retained so the two can be diffed during migration; it is not a second conformance
target. The as-built stream is decoded in its own isolated codec stream (no shared
decode state, handle table, or DoS budget with the application arguments):

```
int   domainCount                       -- 4B BE, 0..4096 (MAX_DOMAINS)
repeat domainCount:
  byte kind                             -- 0 = NULL_CS, 1 = DIGEST, 2 = URL
    DIGEST : utf uri; utf algorithm; object digest(byte[] <=512); certs
    URL    : utf location;                                        certs
    NULL_CS: --
certs := int n(0..100); { object der(byte[] <=65536) } * n
```

**Notes:**
- **Identity is stamped, not asserted.** The receiver builds each domain as a value-equality
  `ProtectionDomain` (DirtyChai `DomainIdentity`) with the real `CodeSource` (or null),
  **no static permissions** (so the server's policy alone decides grants), and the
  authenticated worker principals — which come from the verified mTLS connection, never the
  wire. Value equality is load-bearing under virtual threads: N callers sharing a codebase +
  principal set produce one *equal* reducing `AccessControlContext`, so it deduplicates to a
  single entry in the ACC cache and the `CombinerSecurityManager` permission-result cache
  (one policy evaluation, not N). A plain identity-equality `ProtectionDomain` — reserved for
  ClassLoader domains — defeats that dedup; reconstructing with one was a virtual-thread
  scaling bug, fixed alongside `isJavaBaseModule`.
- **DoS bounds are part of the contract:** `maxDomains = 4096`, `maxCerts = 100`,
  `maxDigestLen = 512`, `maxCertLen = 65536` (§4.5). A non-JVM decoder MUST enforce the
  same ceilings.
- **Why every domain travels.** A null-CodeSource domain is a genuine reducer; omitting
  it — as an earlier "domain-stripping" serializer did, and as a verifiable-vs-`anonCount`
  split would for unverifiable domains — is an implicit privilege escalation. The
  as-built codec therefore transmits all reducing domains inline and never synthesises
  anonymous placeholders; the `anonCount`/placeholder machinery is unnecessary. (The
  `jrt:/java.base` exclusion in the next note is the one deliberate omission — and for
  the opposite reason: that domain is *not* a reducer.)
- **`jrt:/java.base` MUST be excluded (security).** The platform `java.base` module
  domain is always fully privileged (`AllPermission`), carries native code, and is
  unstamped (no content digest). It is therefore not a reducer, and reconstructing it
  with the remote worker principals would inherit the receiver's unconditional platform
  grant — a privilege escalation. The **encoder MUST drop** any `jrt:/java.base` domain,
  and the **decoder MUST refuse** to reconstruct one received from a malicious or
  pre-fix peer (defence in depth). Other `jrt:` module domains are retained — they are
  not necessarily fully privileged. Implemented in `RemoteContextCodec.isJavaBaseModule`
  (applied in both `marshal` and `unmarshal`); this rule was missing before v0.12 (a bug).

**Design note — withdrawn from the earlier (unimplemented) draft:**
- `DomainIdentityRecord.principals` — withdrawn. No principals travel on this wire; the
  receiver stamps authenticated mTLS principals instead.
- `verifiableDomains` + `anonCount` split and "anonymous placeholder domains" —
  withdrawn. Every reducing domain is transmitted inline, discriminated by `kind`.
- `jrt:/java.base` exclusion — **retained and now enforced** (not withdrawn). The draft
  had this right; the implementation was missing it. It is now an encoder *and* decoder
  obligation (see the security note above). Only the `anonCount`/anonymous-placeholder
  *machinery* that the draft wrapped around it is withdrawn.

### 7.3 Code Identity: DigestCodeSourceRecord  **[RESOLVED — confirmed against `DigestCodeSource.java`]**

> **Authority classification (governing principle, §7.5).** `DigestCodeSourceRecord`
> **MAY remain structured DER.** Its trust does **not** derive from its raw decoded
> fields: the record is only a *codebase identity*, and the digest it carries is a
> claim the receiver treats as **unverified** until it is checked locally against code
> that is present (or being loaded) anyway — see the **verification policy** below.
> Forging the fields does not forge trust: a decoded `DigestCodeSourceRecord` is only
> an assertion; it grants nothing until a `DigestGrant` matches it against a real,
> locally-known `DigestCodeSource.getDigest()` (at policy / class-load time — NOT in
> the codec, §7.2). This is the "trust-from-a-digest-compared-against-known-good" case
> the §7.5 principle admits as safe for structured DER, in contrast to
> `PermissionGrant` (whose decoded state *is* the authority) — **provided** the digest
> check never itself triggers a download (the trap the verification policy forecloses).

```asn1
DigestCodeSourceRecord ::= SEQUENCE {
    locationUri     UTF8String,          -- RFC 3986; locator, not trust anchor
    -- [0] IMPLICIT context tag (§4.5 distinct-tag rule): untagged, this SEQUENCE OF
    -- would collide with the DigestValue SEQUENCE that follows (both tag 0x30).
    certificates    [0] IMPLICIT SEQUENCE SIZE(0..maxCerts) OF OCTET STRING (SIZE(1..maxCertLen)) OPTIONAL,  -- each = X509Certificate.getEncoded(), opaque & verbatim (§3.8)
    digest          DigestValue
    -- equality/identity is (uri, certs, algorithm, digestBytes) per DigestCodeSource
    -- a plain CodeSource (no digest) is a DISTINCT identity and MUST NOT be
    -- representable as a DigestCodeSourceRecord with an absent digest.
}
```

**[RESOLVED]** Confirmed against `java.security.DigestCodeSource` (DirtyChai
`src/java.base/.../java/security/DigestCodeSource.java`). Its `writeExternal` field set
and identity are exactly what this record models:
- **Identity is `(uri, certificates, digestAlgorithm, digest)`** (`equals`, source): the
  record's four fields correspond one-for-one. A plain `CodeSource` (no digest) is a
  distinct identity and is not representable here.
- **`digestAlgorithm`** is one of the source's `ALLOWED` set — `SHA-256`, `SHA-384`,
  `SHA-512`, `SHA-512/256`, `SHA3-256`, `SHA3-384`, `SHA3-512` — matching the §4.3
  `AlgorithmIdentifier` allow-list (`DigestValue.algorithm`).
- **Bounds match §4.5 exactly** (source constants): `MAX_CERT_COUNT`=100 (`maxCerts`),
  `MAX_CERT_BYTES`=64 KiB (`maxCertLen`), `MAX_DIGEST_BYTES`=512 (`maxDigestLen`). The
  source stream also enforces `count ≥ 0` and per-cert `length ≥ 0`; the schema `SIZE`
  bounds subsume these.
- **Cert-type tag (source detail not yet in the record).** `writeExternal` writes, per
  certificate, `getEncoded()` **and** `cert.getType()`, and `readExternal` **rejects any
  type but `"X.509"`**. Since the record already fixes the certificate as an X.509
  `OCTET STRING` (opaque octets, below), the per-cert type string is redundant on the
  DER wire and is intentionally dropped; a decoder treats every `certificates` element
  as X.509 DER. **[PROPOSED — confirm dropping the per-cert type string is acceptable;**
  it carries no identity information the digest/URI don't already fix.]

Remaining field-encoding confirmations (unchanged, now source-backed):
- Certificate encoding (**NORMATIVE**): each certificate is an opaque `OCTET STRING`
  holding the exact bytes of `X509Certificate.getEncoded()`, preserved verbatim and
  never parsed-and-re-encoded (§3.8 opaque-octet carve-out). The earlier
  `OF Certificate` (inline X.509 type) form is **withdrawn**: it would invite a
  conforming decoder to parse and re-encode the certificate, and a non-strict-DER
  certificate would then fail its own digest. Order within the `SEQUENCE` is the
  certificate-path order and is order-significant (§3.8) — never sorted or deduplicated.
- The DOS bounds (`maxCerts`=100, `maxCertLen`=64KiB, `maxDigestLen`=512 — §4.5;
  merged with the §7.2 `UrlCodeSourceRecord` certificate-path bounds, since both are
  the same logical ceiling) become schema `SIZE` constraints so a non-JVM decoder
  enforces them identically.
- Whether the `httpmd:` URL form is represented as `locationUri` with the digest in
  the fragment, or normalised into the explicit `digest` field. **Recommendation:**
  normalise into the explicit field; the `httpmd:` fragment was an in-band trick
  precisely because Java serialization had no explicit slot — DER does.

#### 7.3.1 Verification policy (NORMATIVE — RATIFIED Peter, 2026-07-06)

A `DigestCodeSourceRecord` transmits a digest *claim*. When and how that claim is
verified is security-critical: a naive "always re-verify the digest against the real
code" is a **trap**, because obtaining the code to hash it may itself trigger a
network download. The following rules are normative for every conforming
implementation.

**The `unverified` state and its wire treatment.** `java.security.DigestCodeSource`
carries a field **`private transient boolean unverified`** (accessor
**`public boolean unverified()`** — "returns true if the digest hasn't been
verified"; note the *inverted* sense: `unverified == true` means NOT yet verified).
The flag is **`transient`: it does NOT travel on the wire** (it is absent from
`writeExternal`/`readExternal`), and there is no corresponding field in
`DigestCodeSourceRecord` above. This is deliberate and REQUIRED:

- **The verified/unverified state is the receiver's LOCAL determination and MUST NOT
  be trusted from the wire.** A peer could forge "verified"; a wire-asserted verified
  flag MUST NOT be believed. The flag therefore **does not travel**. (Were any future
  revision to carry it, a conforming decoder MUST ignore it and reset it — but the
  ratified form is *not-carried*.)
- **Secure default: a decoded `DigestCodeSource` is UNVERIFIED.** On every
  reconstruction path the receiver MUST set `unverified = true` (digest hasn't been
  verified) regardless of any value that might arrive. This matches the source
  exactly: both the transmitted-digest constructor
  (`DigestCodeSource(uri, certs, algorithm, digest)`) and the `readExternal` path set
  `unverified = true`; only the constructors that *compute* the digest from real bytes
  leave it `false`. A "verified" claim is authority-adjacent and, per the §7.5
  governing principle, must not be reconstituted from wire fields.

**Reconstruct by direct field-set, never by re-computation or re-download.** The
decoder MUST reconstruct the `DigestCodeSource` by **directly setting the transmitted
digest field** (the `readExternal` / `(uri, certs, algorithm, digest)` path). It MUST
NOT route reconstruction through any URL-taking constructor or code path that calls
`computeDigest` (i.e. that fetches `locationUri` and hashes it). Decode-time
re-computation is a re-download / TOCTOU landmine: it fetches attacker-influenced
bytes at decode time and the fetched bytes may differ from those the digest was
computed over. Decode transmits and stores the digest; it never recomputes it.

**Lazy verification — never download solely to verify (REQUIRED).**

- A receiver **MUST NOT download code SOLELY to verify** a `DigestCodeSourceRecord`'s
  digest. Verify-by-download is a **new security threat** introduced by the record
  itself: a peer that can send records could otherwise trigger arbitrary code
  downloads, traffic amplification, or TOCTOU races just by sending crafted records,
  with no other authority.
- The receiver **MUST verify the digest ONLY when the code is EITHER (a) already
  present on the local machine, OR (b) about to be downloaded ANYWAY because it is
  being loaded** for execution. Verification **piggybacks** on a load that is
  happening regardless of the record; it is **never** a download-initiated-to-verify.
- Until such a load occurs, the decoded `DigestCodeSource` simply remains
  `unverified() == true`. It confers no authority in that state: a `DigestGrant`
  (§7.2/§7.5) matches only against a domain whose `DigestCodeSource` is present and
  whose digest has been established locally, so an unverified record grants nothing.
  There is no correctness pressure to verify eagerly, and eager verification would
  reintroduce the download-to-verify threat.

This policy is consistent with §8 (the authenticated `CodebaseAccessor` is the *only*
code-download channel and is driven by a *load*, never by a bare identity record) and
with the acyclic/fail-secure decode discipline (§3.7, principle 6): decoding a
`DigestCodeSourceRecord` performs **no** network I/O.

### 7.4 SCAP Data Objects  **[RESOLVED — field lists confirmed against the `@AtomicSerial` classes]**

Field lists confirmed against the live `@AtomicSerial` classes in
`au.net.zeus.jgdms.api.codebase` (jgdms-platform): `AnalysisRequest`,
`JarAnalysisReport`, `RegistryVerdict`, `CrashReport`. **There is no `SignedVerdict`
class** — "SignedVerdict" in prior drafts was the generic *signed-object* concept; the
concrete signed types (`JarAnalysisReport`, `RegistryVerdict`, `CrashReport`) each carry
their signature **inline** as a `byte[]` field rather than wrapping a separate
`SignedObject`. These are signature-bearing (except `AnalysisRequest`), which is exactly
why DER (canonical) rather than BER is mandatory — the signed octets must be reproducible
across implementations.

> **Authority classification (governing principle, §7.5).** All four SCAP types **MAY
> remain structured DER.** Their trust derives from a **verified signature or a
> re-computed content hash**, not from raw decoded fields:
> - `JarAnalysisReport` (`engineSignature`), `RegistryVerdict` (`signature`), and
>   `CrashReport` (`signature`) each carry a `byte[]` signature over their own content;
>   the verifier checks it against the engine / registry / Phoenix public key. Forging
>   any field — including `JarAnalysisReport.declaredPermissions` (a `String[]` of
>   *permission strings*, which look authority-bearing) — breaks the signature, so the
>   forgery is caught. The permission strings are **not** an authority the receiver
>   grants by deserializing them; they are a signed *claim* the verifier checks, then
>   feeds through the normal policy path. This is the signature-backed safe case.
> - `AnalysisRequest` is **unsigned**, but its `check(GetArg)` **re-hashes the
>   unpacked jar and rejects a `contentHash` that does not match** (source:
>   `AnalysisRequest.check` / `sha256Hex`), so its integrity is self-verifying — a
>   forged `contentHash` or tampered `packedJarBytes` is rejected at construction.
>
> None of these has the `PermissionGrant` property (decoded state == authority
> conferred without a further check), so none requires the §7.5 string-reparse
> treatment. `declaredPermissions` is the one to keep an eye on: it stays structured
> **only because it is under the engine signature**; were it ever carried unsigned it
> would fall under the §7.5 principle. **[PROPOSED — confirm this classification.]**

**Confirmed serial forms (source):**

```asn1
-- AnalysisRequest.serialForm(): packedJarBytes byte[], contentHash String,
-- originalUri String, maxBfsDepth int. Unsigned; contentHash self-verified.
AnalysisRequest ::= SEQUENCE {
    packedJarBytes  OCTET STRING,        -- Pack200-compressed; SIZE bound: see note
    contentHash     DigestValue,         -- SHA-256 of RAW (unpacked) bytes; re-verified in check()
    originalUri     UTF8String,          -- traceability only
    maxBfsDepth     INTEGER (0..MAX)
}

-- JarAnalysisReport.serialForm(): contentHash String, classNames String[],
-- classResults ClassAnalysisResult[], engineSignature byte[],
-- declaredPermissions String[], codebaseUrls String[]. Engine-signed.
JarAnalysisReport ::= SEQUENCE {
    contentHash          DigestValue,
    -- ORDER-SIGNIFICANT (§3.8): classNames[i] pairs with classResults[i].
    classNames           SEQUENCE (SIZE(0..maxCollection)) OF UTF8String,
    classResults         SEQUENCE (SIZE(0..maxCollection)) OF ClassAnalysisResult,
    engineSignature      OCTET STRING,   -- DER signature over the signed TBS (§7.4.1)
    -- declaredPermissions: textual policy-permission strings, SIGNED (safe structured);
    -- fed to the policy parser downstream, not granted by decoding (see classification).
    declaredPermissions  SEQUENCE (SIZE(0..maxCollection)) OF UTF8String,
    codebaseUrls         SEQUENCE (SIZE(0..maxCollection)) OF UTF8String
}
-- ClassAnalysisResult: [PROPOSED — field list not yet extracted; confirm against
-- au.net.zeus.jgdms.api.codebase.ClassAnalysisResult @AtomicSerial serialForm().]

-- RegistryVerdict.serialForm(): codebaseUrls String[], verdict VerdictType,
-- timestamp long, signature byte[]. Registry-signed.
RegistryVerdict ::= SEQUENCE {
    codebaseUrls  SEQUENCE (SIZE(0..maxCollection)) OF UTF8String,
    verdict       INTEGER,               -- VerdictType enumerant (closed set; [PROPOSED] confirm values)
    timestamp     INTEGER,               -- epoch-millis (long), consistent with §7.6 Date
    signature     OCTET STRING           -- DER signature over the signed TBS (§7.4.1)
}

-- CrashReport.serialForm(): codebaseUrls String[], exitCode int, incarnation long,
-- stderrSummary String, signature byte[]. Phoenix-signed.
CrashReport ::= SEQUENCE {
    codebaseUrls   SEQUENCE (SIZE(0..maxCollection)) OF UTF8String,
    exitCode       INTEGER,
    incarnation    INTEGER,              -- long
    stderrSummary  UTF8String,
    signature      OCTET STRING          -- DER signature over the signed TBS (§7.4.1)
}

-- Generic signed-object form (available; the as-built classes do NOT use it — see note).
SignedObject ::= SEQUENCE {
    tbs         OCTET STRING,            -- the DER-encoded to-be-signed content
    signature   SEQUENCE {
        algorithm   AlgorithmIdentifier,
        value       OCTET STRING
    }
}
```

**Signature TBS = canonical DER of the signed fields (RATIFIED Peter, 2026-07-06).**
Each signed SCAP record carries its signature **inline** as a `byte[]` field
(`engineSignature` / `signature`) *alongside* the signed fields, not in a separate
`SignedObject` envelope. Its **TBS (to-be-signed) content is the canonical DER of the
record's signed fields, with the signature field itself excluded** — i.e.
`DER(SEQUENCE { …the record's non-signature fields, in the order below… })`, computed
once by the signer and reconstructed identically by the verifier (the
record-level-signature rule of §7.4.1, same as §7.7.7 multicast — **not** the
explicit-`tbs`-`OCTET STRING` wrapper). The `SignedObject` type above is retained as an
**optional generic form** a future signer MAY use, but is not the shape the SCAP
classes emit.

**Per-record TBS field coverage (NORMATIVE — signature field excluded from its own TBS):**

| Record | Signed fields (the TBS `SEQUENCE`, in order) | Excluded (not in TBS) | Signer key |
|---|---|---|---|
| `JarAnalysisReport` | `contentHash`, per-class results (`className` + `clinitVerdict` + `atomicVerdict`), `declaredPermissions`, `codebaseUrls` | `engineSignature` | engine key |
| `RegistryVerdict` | `codebaseUrls`, `verdict`, `timestamp` | `signature` | registry key |
| `CrashReport` | `codebaseUrls`, `exitCode`, `incarnation`, `stderrSummary` | `signature` | Phoenix key |

(`AnalysisRequest` is **unsigned**; its integrity is self-verifying — `check(GetArg)`
re-hashes the unpacked jar and rejects a mismatched `contentHash` — so it has no TBS
and is unchanged by this decision.)

**Clean break — no legacy TBS, no versioning (NORMATIVE rationale).** This TBS is the
**canonical DER** of the signed fields; it replaces the as-built, **non-DER**
`writeUTF`-based canonicalisation (e.g. `JarAnalysisReport.canonicalBytes()` — a
`ByteArrayOutputStream` with NUL-delimited `writeField` sections `"C"`/`"P"`/`"U"` and
sorted fields; `RegistryVerdict`/`CrashReport` have the analogous "canonical serialized
form"). Because **SCAP is UNRELEASED, this is a clean break, not a compatibility
problem:** there are **no** deployed Java-serialized (or `writeUTF`-canonical) verdicts
that must remain verifiable, so **no dual-format cutover and no signature-format
versioning are required** — the DER-canonical TBS is simply *the* signature input from
first release. (The verdict cache, keyed by the JAR content hash per STD-002, is
independent of verdict encoding and is unaffected regardless.)

> **Implementation follow-up (NOT in this doc branch — separate `jgdms-platform` code
> change).** The SCAP `@AtomicSerial` classes currently compute/verify the signature
> over the `writeUTF`-based `canonicalBytes()` (and the `RegistryVerdict`/`CrashReport`
> equivalents). To conform to this ratified decision, the implementation MUST be changed
> to compute and verify the signature over the **canonical DER TBS** (the per-record
> field coverage above) instead. Flagged here as a distinct code task; this
> documentation branch does not touch the SCAP classes.

#### 7.4.1 Signature input (NORMATIVE)

The signed octets are the bytes of the `tbs` `OCTET STRING` exactly as they appear in
the `SignedObject`. The signer encodes the to-be-signed content to canonical DER
**once**, places those bytes in `tbs`, and signs them. The verifier reads the `tbs`
`OCTET STRING` off the wire and verifies the signature against the received bytes
**without re-encoding them** — it MUST NOT decode `tbs` into its fields and
re-serialise before verifying. Any certificate, `Name`, or nested signature inside
`tbs` is itself opaque octets (§3.8) and is likewise never re-encoded. Carrying `tbs`
as an explicit `OCTET STRING` (rather than an inline `SEQUENCE` the verifier must
rebuild) is what guarantees byte agreement between signer and verifier: a single
non-strict-DER embedded value cannot change the signed bytes, so it cannot break an
otherwise-valid signature. The same rule governs record-level signatures that carry no
`tbs` wrapper (§7.7.7): the signature input is `DER(preceding-fields-as-a-SEQUENCE)`,
computed once by the signer and verified against the SEQUENCE reconstructed from the
received fields.

### 7.5 Policy: PermissionGrant / DigestGrant  **[RATIFIED (Peter, 2026-07-06)]**

Grants do travel the wire (via `RemotePolicyProvider` `replace()` / push updates, and
`DynamicPolicyProvider` `Security.grant()`). **They are NOT marshalled as a structured
DER record.** They are carried as their **textual policy-grant form** (STD-004 policy
syntax) and **re-parsed on deserialization through the trusted policy parser** — the
receiver reconstructs the grant's authority by parsing text, never by reconstituting a
grant object from wire-controlled serialized fields.

```asn1
-- A grant is carried as its STD-004 textual policy-grant form, re-parsed by the
-- vetted policy parser on the receiver. There is NO per-field DER grant record.
PermissionGrantTextForm ::= UTF8String
```

**Security rationale (NORMATIVE).** A `PermissionGrant`/`DigestGrant` is an
**authority-bearing** object: its deserialized state *is* the authority it confers.
Directly deserializing such an object from wire-controlled fields is a security
vulnerability — a peer could hand over a grant with forged fields (authority forgery /
gadget vector). The receiver **MUST** reconstruct the grant's authority by parsing a
textual representation through the vetted policy parser, and **MUST NOT** reconstitute
it from serialized object state.

This mirrors the implementation exactly: `PermissionGrant` (jgdms-platform,
`org.apache.river.api.security.PermissionGrant`) **deliberately does not implement
`Serializable` "for security reasons"** and forces subclasses through the
Serialization *Builder* Pattern; `DigestGrant.readObject` throws
`InvalidObjectException` and `writeReplace()` emits a builder template. The wire form
here is the language-neutral analogue of that builder: a text the trusted parser
vets, not an object graph the decoder trusts.

**Governing principle (NORMATIVE — applies across §7):** *a type whose deserialized
state is itself the authority (permissions, policy grants) MUST travel as a re-parsed
textual form, never as a directly-deserialized authority object.* A type whose trust
instead derives from a **verified signature or a digest compared against a known-good
value** — not from its raw decoded fields — MAY remain structured DER, because forging
the fields does not forge the trust (the signature/digest check catches it). Each §7
type is classified on this basis; see the §7.6 `Permission` row and the §7.3/§7.4
authority-classification notes.

### 7.6 Substituted Standard Types

`AtomicMarshalOutputStream.replaceObject()` substitutes a fixed set of standard JDK
types with dedicated `Serializer` classes, because their default Java serialization
is either unsafe (gadget surface) or not decodable by the `@AtomicSerial` subset. On
the read side these decode into immutable, validated carriers that an `@AtomicSerial`
constructor consumes via `GetArg` and then discards — they never become object fields
directly. Any of these types can appear as a field value inside a transmitted
`@AtomicSerial` object, so each needs a canonical DER form a non-JVM participant can
produce and consume identically.

**Collections are NOT in this catalogue.** The output stream substitutes `Map`,
`Set`, and `Collection` with `MapSerializer`/`SetSerializer`/`ListSerializer`, but
under §3.8 these collapse into a native, bounded `SEQUENCE OF` — the carrier's two
jobs (DOS bounding, immutability) are subsumed by the schema `SIZE` constraint and
DER's read-only decoded structure. There is therefore **no `MapSerializer`,
`SetSerializer`, or `ListSerializer` wire type.** A collection-valued field is:
(These six productions are also the `canonicalCollection`/`orderedCollection`
payload types wrapped by the `Any` `CHOICE`'s `EXPLICIT` collection arms — §3.12.)

```asn1
-- Collection field types, one per ordering discipline (§3.8). The discipline is
-- fixed by the declared type at schema-generation time and baked into the schema
-- wire-type token (set:/bag:/map:/orderedset:/list:/orderedmap:); the ASN.1
-- container below pins the wire TAG that goes with each token.

-- CANONICALISE (non-deterministic order) -> SET OF, universal tag 0x31,
-- octet-sorted per X.690 §11.6; a decoder MUST reject an out-of-order SET OF.
CanonicalSet      ::= SET      SIZE(0..maxCollection) OF Element                                  -- token set:  (distinct)
CanonicalMultiset ::= SET      SIZE(0..maxCollection) OF Element                                  -- token bag:  (dups permitted)
CanonicalMap      ::= SET      SIZE(0..maxCollection) OF SEQUENCE { key Element, value Element }  -- token map:  (entries octet-sorted by encoded KEY; per-entry SEQUENCE 0x30)

-- PRESERVE (deterministic order) -> SEQUENCE OF, universal tag 0x30, iterator
-- order emitted verbatim.
OrderedSetField   ::= SEQUENCE SIZE(0..maxCollection) OF Element                                  -- token orderedset: (distinct)
ListField         ::= SEQUENCE SIZE(0..maxCollection) OF Element                                  -- token list:       (dups permitted)
OrderedMapField   ::= SEQUENCE SIZE(0..maxCollection) OF SEQUENCE { key Element, value Element }  -- token orderedmap: (both containers 0x30)
-- the receiving object imposes uniqueness/null-policy at construction (§3.8)
```

Element order follows the §3.8 **deterministic-iteration-order** rule: a field whose
declared type guarantees a deterministic iteration order — the sequenced backbone
(`List`, `Deque`, `LinkedHashSet`, `LinkedHashMap`, `SortedSet`/`TreeSet`,
`SortedMap`/`TreeMap`), plus `EnumSet`/`EnumMap`, the concurrent sorted collections
`ConcurrentSkipListSet`/`ConcurrentSkipListMap`, `CopyOnWriteArrayList`, and arrays —
is emitted order-**preserved**; a type whose iteration order is non-deterministic or
unspecified (`HashSet`, `HashMap`, `ConcurrentHashMap`; the concurrent insertion/FIFO
collections `CopyOnWriteArraySet`, `ConcurrentLinkedQueue`/`ConcurrentLinkedDeque`, the
blocking queues/deques `ArrayBlockingQueue`/`LinkedBlockingQueue`/`LinkedBlockingDeque`,
`LinkedTransferQueue`, `DelayQueue`, `SynchronousQueue`; and
`PriorityQueue`/`PriorityBlockingQueue`) is octet-sorted into the §3.8 canonical order.
Preserve fields carry the `SEQUENCE OF` tag `0x30`; canonicalise fields carry the
`SET OF` tag `0x31` and MUST be in §11.6 ascending order (a decoder rejects an
out-of-order `SET OF` or the wrong container tag — §3.8). The `Properties` row below is a
**canonicalise** case (it is `Hashtable`-based, so its iteration order is
non-deterministic). The normative home for this rule is §3.8; this section only records
that collection-valued fields obey it. For preserved types, note that serialized
byte-equality is order-sensitive and (for the insertion-ordered
`LinkedHashSet`/`LinkedHashMap`) stricter than object `equals` — see §3.8.

The irreducible substituted types requiring their own DER form:

| Type | Current serializer | Proposed DER form | Status |
|---|---|---|---|
| `Byte`/`Short`/`Integer`/`Long` | boxed-primitive serializers | `INTEGER` (value-bounded) | [PROPOSED] |
| `Float`/`Double` | boxed-primitive serializers | `OCTET STRING` of IEEE-754 bits (4/8 bytes, big-endian), strict-canonical | **[RESOLVED — STD-008 §17.3.1]** |
| `Character` | `CharSerializer` | `INTEGER` (Unicode codepoint; BMP non-surrogate) | **[RESOLVED — STD-008 §17.3.2]** |
| `Boolean` | `BooleanSerializer` | `BOOLEAN` | [PROPOSED] |
| `Properties` | `PropertiesSerializer` | `SEQUENCE OF SEQUENCE { key UTF8String, value UTF8String }` (behavioural; unordered — canonicalise, §3.8) | **[RESOLVED — values are always `String`; see note]** |
| `URL` | `URLSerializer` | `UTF8String` = `URL.toString()` external form (locator, no DNS) | **[RESOLVED — external-form string, no URL/URI cross-normalisation; see note]** |
| `URI` | `URISerializer` | `UTF8String` = `URI.toString()` external form | **[RESOLVED — external-form string; see note]** |
| `UID` (`java.rmi.server.UID`) | `UIDSerializer` | `SEQUENCE { unique INTEGER, time INTEGER, count INTEGER }` — `unique`=int32, `time`=int64, `count`=int16 (the `UID.write` field set) | **[RESOLVED — see note]** |
| `File` | `FileSerializer` | `UTF8String` = `File.toURI().toString()` (a `file:` URI, RFC 3986 — **not** a raw platform path) | **[RESOLVED — File DOES travel, as a URI; see note]** |
| `MarshalledObject` | `MarshalledObjectSerializer` | nested `MarshalledInstanceRecord` (§7.8) — no codebase annotation (§8.3); nesting bound to `MAX_NESTING` + the §4.5 size caps (see note) | **[RATIFIED (Peter, 2026-07-06) — explicit depth + size bounds; see note]** |
| `StackTraceElement` | `StackTraceElementSerializer` | `SEQUENCE { declaringClass UTF8String, methodName UTF8String, fileName UTF8String OPTIONAL, lineNumber INTEGER }` | **[RESOLVED — matches `StackTraceElementSerializer.serialForm()`]** |
| `X500Principal` | `X500PrincipalSerializer` | `OCTET STRING` = `X500Principal.getEncoded()` — opaque & verbatim (§3.8), never re-encoded | **[RESOLVED — opaque octets; see note below]** |
| `Date` | `DateSerializer` | `INTEGER` epoch-millis (`Date.getTime()`) | **[RATIFIED (Peter, 2026-07-06) — epoch-millis `INTEGER`, not `GeneralizedTime`]** |
| `Permission` | *(none — `PermissionSerializer` DELETED 2026-06: reflective-gadget removal, JOSS engine audit)* | **NOT a structured DER type.** Either does not travel at all, or travels as its **textual string form** (`PermissionTextForm ::= UTF8String`) **re-parsed** through the trusted parser on decode | **[RATIFIED (Peter, 2026-07-06) — string-form + reparse; structured form withdrawn; see note]** |
| `Throwable` | `ThrowableSerializer` | bounded `ThrowableRecord` safe subset (name/message/stackTrace/suppressed/cause) — NO reflective reconstruction, NO `clazz`/`perm`; see below | **[RATIFIED (Peter, 2026-07-06) — safe subset; reflective path excluded]** |
| `AccessControlContext` | `RemoteContextCodec` (jgdms-jeri) | §7.2 `AccessControlContextRecord` (reducing domains, codebases only) | reconciled §7.2 |

**`Float` / `Double` / `Character` (strict-canonical, per STD-008 §17.3).** The boxed
wrappers use the same canonical wire form as the primitive `float` / `double` / `char`
types: `Float`/`Double` as a fixed-width IEEE-754 `OCTET STRING` (4 / 8 bytes,
big-endian) and `Character` as a DER `INTEGER` Unicode codepoint. Canonicalisation is
symmetric — the encoder emits the canonical quiet-NaN (`0x7FC00000` /
`0x7FF8000000000000`) and `+0.0`, and the decoder *rejects* non-canonical NaN, `-0.0`
bits, wrong length, and surrogate / supplementary-plane codepoints fail-secure
(STD-008 §17.3.1–.2). ASN.1 `REAL` is not used.

**`X500Principal` (verbatim, not re-encoded).** Although an X.501 `Name` is DER-native,
it MUST be carried as opaque octets — the exact bytes of `X500Principal.getEncoded()` —
not parsed into RDNs and re-encoded. X.500 attribute values carry a choice of string
type (`PrintableString` / `UTF8String` / the legacy `TeletexString`), and a re-encoding
pass can legitimately alter both the chosen tag and the bytes (e.g. normalising
`TeletexString` to `UTF8String`). When the `Name` sits inside a signed structure — a
SPIFFE/X.509 principal chain (§7.1), a multicast signer principal (§7.7.7), a SCAP
verdict (§7.4) — any such change breaks the signature. `getEncoded()` round-trips its
own bytes by construction; §9 requires a conformance test proving a `Name` with a
`TeletexString` AVA survives encode→decode byte-for-byte.

**`Permission` (RATIFIED 2026-07-06 — string-form, not a structured record).** The
`PermissionSerializer` was deleted (2026-06) as a reflective gadget. A
`java.security.Permission` likely need not travel the wire at all; **if** it does, it
travels as its **textual string form** — `PermissionTextForm ::= UTF8String` — and is
**re-parsed on decode** through the trusted policy parser, never reconstructed from
decoded per-field state. The earlier structured
`SEQUENCE { className, name, actions }` form is **withdrawn**. Rationale is the §7.5
governing principle: a `Permission`'s decoded state *is* an authority claim, so it
must be reconstructed only by the vetted parser (which decides what the calling
context is entitled to grant), not synthesised from wire-controlled fields. (This is
the §7.6-scalar analogue of the §7.5 `PermissionGrant` decision.)

**`Date` (RATIFIED 2026-07-06 — epoch-millis `INTEGER`).** `DateSerializer` writes
`out.writeLong(d.getTime())` and reconstructs `new Date(in.readLong())`. The DER form
is a DER `INTEGER` holding the signed epoch-millisecond value — exact round-trip, no
timezone ambiguity, no sub-millisecond loss. `GeneralizedTime` is **not** used (it
cannot represent the full `long` range unambiguously and admits multiple textual forms
of one instant, breaking canonicity). Alternative dropped.

**`MarshalledObject` (RATIFIED 2026-07-06 — nested frame with explicit depth + size
bounds).** `MarshalledObjectSerializer` carries a single `MarshalledInstance` field
(`instance`) and reconstructs via `instance.convertToMarshalledObject()`. On the wire
a `MarshalledObject` is therefore exactly a nested **`MarshalledInstanceRecord`**
(§7.8) — no separate structure, no codebase annotation (§8.3). Because this nests the
STD-006 wire format inside itself (a `MarshalledInstanceRecord`'s `payloadBytes` may
contain a field that is itself a `MarshalledObject`, and so on), the nesting **MUST**
be bounded by the existing decoder fences (§3.12, "the four decoder fences"): the
`MAX_NESTING` depth counter is decremented and bounded on entry to every nested
`MarshalledInstanceRecord` frame exactly as for an `Any`→`atomicSerialObject` step,
and the frame's own variable-length fields (`payloadBytes`, `schemaBytes`) and any
collection fields inside the nested payload remain subject to their §4.5 `SIZE`
ceilings (`maxCollection`, `maxFields`, `maxStackFrames`/`maxCauseDepth` for a nested
`Throwable`, etc.). A nested frame that would exceed `MAX_NESTING`, or whose inner
fields exceed their §4.5 caps, is **rejected before allocation** (fail-secure,
principle 6). **NOTE — `MAX_NESTING` has no numeric value in §4.5 yet:** the fence is
named normatively in §3.12 but §4.5's ceiling table does not assign it a number.
Recommend adding `maxNesting` to the §4.5 table (a small value, e.g. 16–64, sufficient
for any legitimate `MarshalledObject`/`Any` nesting) and binding this rule to it;
**flagged for Peter to set the number** (consistent with the other [PROPOSED] §4.5
ceilings, open item 21).

**`URL` / `URI` / `File` / `UID` / `Properties` / `StackTraceElement` (RESOLVED from
source).**
- **`URL`** — `URLSerializer` carries `urlExternalForm = url.toString()` and rebuilds
  `new URL(null, urlExternalForm)`. **`URI`** — `URISerializer` carries
  `uriExternalForm = uri.toString()` and rebuilds `new URI(...)`. Each carries its own
  **external string form**; there is **no URL↔URI cross-normalisation** (the two are
  distinct serializers). Resolves the "URL vs URI normalisation" open item: neither is
  normalised into the other; both are `UTF8String` external forms.
- **`File`** — `FileSerializer` carries `path = file.toURI()` (serialForm type `URI`)
  and rebuilds `new File(uri)`. So `File` **does** travel cross-runtime, and it travels
  as a **`file:` URI** (`File.toURI().toString()`, RFC 3986), **not** as a raw platform
  path string. This sidesteps the platform-path-semantics concern: the URI form is
  platform-neutral on the wire (a non-JVM party sees a `file:` URI; whether it can
  resolve it is its own concern). Resolves the open item.
- **`UID`** (`java.rmi.server.UID`) — `UIDSerializer` delegates to `UID.write`/
  `UID.read`, whose field set is `unique` (int32), `time` (int64), `count` (int16). The
  DER `SEQUENCE { unique INTEGER, time INTEGER, count INTEGER }` matches; the value
  ranges above are the confirmation the open item asked for.
- **`Properties`** — `PropertiesSerializer` casts every key and value to `String`
  (`(String) e.getKey()` / `(String) e.getValue()`), so values **are always `String`**
  (a non-`String` value throws `ClassCastException` before it reaches the wire).
  Resolves the open item; the `SEQUENCE OF SEQUENCE { key UTF8String, value UTF8String }`
  form is confirmed. It is a **canonicalise** collection (`Hashtable`-based, §3.8).
- **`StackTraceElement`** — `StackTraceElementSerializer.serialForm()` is exactly
  `declaringClass:String, methodName:String, fileName:String (nullable), lineNumber:int`;
  the spec's `SEQUENCE` matches. Resolved.

**`Throwable` (security-sensitive — RATIFIED 2026-07-06: the SAFE SUBSET).**
`Throwable` is a classic gadget vector under ordinary Java serialization; routing it
through `ThrowableSerializer` rather than default serialization is a deliberate
containment. **ATOMIC DER carries only the bounded, non-executable `ThrowableRecord`
safe subset below** — it does **NOT** reproduce the as-built serializer's
reflective-constructor reconstruction (see the exclusion note). The cause chain is
acyclic (§3.7) — a `Throwable` whose cause chain contained a cycle would be rejected:

```asn1
-- SAFE SUBSET (NORMATIVE). Only these fields travel; all are inert data (strings,
-- ints, and nested records of the same shape). No Class object, no Permission, no
-- reflective-reconstruction inputs.
ThrowableRecord ::= SEQUENCE {
    className     UTF8String,            -- the throwable's class NAME only (not a Class object)
    message       UTF8String OPTIONAL,
    -- ORDER-SIGNIFICANT (§3.8): stack frames are top-of-stack first
    stackTrace    SEQUENCE SIZE(0..maxStackFrames) OF StackTraceElement,  -- §4.5
    -- [0]/[1] IMPLICIT context tags (§4.5 distinct-tag rule): suppressed (SEQUENCE OF)
    -- and cause (a ThrowableRecord SEQUENCE) are both OPTIONAL and both tag 0x30 —
    -- untagged they are indistinguishable when only one is present.
    -- suppressed exceptions, each a full ThrowableRecord; depth counts toward
    -- maxCauseDepth / MAX_NESTING exactly as `cause` does (§4.5, §3.12).
    suppressed    [0] IMPLICIT SEQUENCE SIZE(0..maxSuppressedPerNode) OF ThrowableRecord OPTIONAL,  -- §4.5 (ratified 2026-07-24, item 21)
    cause         [1] IMPLICIT ThrowableRecord OPTIONAL   -- acyclic; nesting <= maxCauseDepth (§4.5)
}
```

**Reflective reconstruction is EXCLUDED (NORMATIVE security rationale).** The as-built
`ThrowableSerializer` (jgdms-platform, `org.apache.river.api.io.ThrowableSerializer`)
reconstructs a throwable by **reflectively selecting and invoking a constructor** keyed
on a decoded `Class` (`init(...)`, `clazz.getDeclaredConstructor(...).setAccessible(true).
newInstance(...)`), and to that end carries extra fields — a `clazz` (`Class`),
`classname` / `length` / `eof` (for `InvalidClassException` / `OptionalDataException` /
`URISyntaxException`), and a `perm` (`Permission`, for `AccessControlException`).
**ATOMIC DER does NOT admit any of this.** Driving constructor selection and reflective
instantiation from wire-controlled state (a decoded class name plus decoded constructor
arguments) is **gadget-adjacent**: it is exactly the "wire bytes choose which
constructor runs, with attacker-supplied arguments" shape the format exists to remove
(§2.1). Concretely:

- **Dropped: `clazz` (the `Class`) and reflective constructor invocation.** Only the
  class *name* string travels. A conforming decoder MUST NOT resolve that name to a
  `Class` and invoke a constructor from it; the receiver constructs, at most, a single
  fixed carrier type from the inert fields (a generic bounded throwable-shaped record),
  never an arbitrary `clazz`-selected type. The class name is *data* (for logging /
  diagnostics / a best-effort typed rebuild by trusted local code), not an instruction
  to instantiate.
- **Dropped: `perm` (`Permission`).** A `Permission` is authority-bearing (§7.5); it
  MUST NOT travel as a structured field. If a future need arises to convey the permission
  of an `AccessControlException`, it follows the §7.6 `Permission` rule — textual string
  form, re-parsed — not a decoded `Permission` object fed to a constructor.
- **Dropped: `classname` / `length` / `eof`.** These exist solely to feed the reflective
  reconstruction of specific JDK exception subclasses (`InvalidClassException`,
  `OptionalDataException`, `URISyntaxException`). With reflective reconstruction excluded
  they carry no safe-subset meaning and are not transmitted. (Should a specific subclass's
  extra state ever need to survive, it is added as a named, inert field with its own bound
  — never as a reflective-constructor argument.)
- **Kept: `className` (name), `message`, `stackTrace`, `suppressed[]`, `cause`.** All are
  inert data: strings, bounded `StackTraceElement` records, and nested `ThrowableRecord`s.
  Nothing here selects a constructor or conveys authority. `suppressed[]` and `cause`
  nest `ThrowableRecord`s and their depth is bounded by `maxCauseDepth` / `MAX_NESTING`
  (§3.12, §4.5); `stackTrace` by `maxStackFrames`; `suppressed[]` count by
  `maxCollection`.

The bound numbers are **RATIFIED (Peter, 2026-07-24 — open item 21 CLOSED)**:
`maxStackFrames`=2048, `maxCauseDepth`=12, `maxSuppressedPerNode`=32,
`maxThrowableNodes`=128 (§4.5 table).

**Realization note (2026-07-24, post-review):** the invocation-layer fault carrier
(`org.apache.river.api.io.DerThrowableForm`, applied at the
`AtomicDerInvocationDispatcher.marshalThrow`/`unmarshalThrow` seam) realizes this
section's RATIFIED `ThrowableRecord` **field set** (className/message/stackTrace/
suppressed/cause; the five gadget-adjacent fields excluded) in **`@AtomicSerial`
schema-record framing** rather than the hand-authored ASN.1 `SEQUENCE` above — the
closed DER registry continues to defer `Throwable` itself. The decoder never resolves
the carried class name; typed rebuild happens only at the trusted client seam.

**[OPEN → mostly RESOLVED] for §7.6 as a whole:**
- **Complete substituted-type set (RESOLVED from source, with two additions).** Against
  the live `@Serializer(replaceObType=…)` set in `org.apache.river.api.io`, the
  substituted types are: the boxed primitives (`Boolean`/`Byte`/`Character`/`Short`/
  `Integer`/`Long`/`Float`/`Double`), `Date`, `File`, `MarshalledObject`, `Properties`,
  `StackTraceElement`, `Throwable`, `UID`, `URI`, `URL` — **plus two the table above
  omitted**: `net.jini.id.Uuid` (`UuidSerializer`) and an internal `Marker`
  (`MarkerSerializer`, a stream-internal control marker, not application data).
  Collections (`Map`/`Set`/`List`/`Collection`) are also substituted but collapse to
  native `SEQUENCE OF`/`SET OF` per §3.8 (no wire type). **[PROPOSED — add `Uuid`** as a
  `SEQUENCE { mostSig INTEGER, leastSig INTEGER }` or a 16-byte `OCTET STRING` (it is
  two `long`s); note `net.jini.core.lookup.ServiceID` (§7.7.3) is already a 16-byte
  `OCTET STRING` UUID, so `Uuid` should reuse that form. `Marker` is stream-internal
  and does **not** need a §7.6 wire type — confirm it never escapes as a field value.]
- `Float`/`Double`/`Character`: **RESOLVED** — IEEE-754 bits / codepoint `INTEGER`
  (STD-008 §17.3).
- `Date`: **RATIFIED 2026-07-06** — epoch-millis `INTEGER`.
- `Permission`: **RATIFIED 2026-07-06** — string-form + reparse; not structured.
- `MarshalledObject`: **RATIFIED 2026-07-06** — nested `MarshalledInstanceRecord` bound
  to `MAX_NESTING` + §4.5 size caps (needs a `maxNesting` number — see the
  `MarshalledObject` note above and open item 21).
- `URL`/`URI`/`File`/`UID`/`Properties`/`StackTraceElement`: **RESOLVED from source**
  (see the note block above).
- `Throwable`: **RATIFIED 2026-07-06** — the bounded `ThrowableRecord` safe subset
  (name/message/stackTrace/suppressed/cause); reflective-constructor reconstruction and
  the `clazz`/`perm`/`classname`/`length`/`eof` fields are **excluded** (gadget-adjacent;
  see the `Throwable` note above). The §4.5 bound numbers are RATIFIED
  (Peter, 2026-07-24 — open item 21 CLOSED; see the realization note above).

### 7.6.1 Registered DER `@Serializer` set — wire-affecting, governed, NOT a decode gate (NORMATIVE)

The runtime DER codec implements §7.6 substitution through a **registry** of
`@AtomicSerial` + `@Serializer(replaceObType = X)` classes
(`au.net.zeus.jgdms.der.serial.DerReplacer`), loaded from the single
platform-controlled resource `META-INF/jgdms/der-serializers` that ships in
jgdms-der's own module/jar. This clause is the normative home for the governance rule
the code and SOW cite (previously cited but unwritten); cite it as **STD-006 §7.6.1**.

1. **The registered set is wire-affecting.** `DerReplacer.isRegistered(declaredType)`
   feeds `SchemaGenerator`: for a field whose declared type is a registered
   `replaceObType`, the generated schema admits the field as a nested `@AtomicSerial`
   and the substituted serializer's class participates in the transmitted schema chain
   and thus in the `schemaDigest` (§4.3, §7.8). **The set of registered serializers is
   therefore part of the wire contract**, on the same footing as a field-type change.

2. **Additions are a versioned, board-reviewed schema change with digest coordination.**
   Adding (or removing) a serializer changes which values encode as a substitution vs.
   fail schema-generation, and changes the `schemaDigest` of any value that transitively
   contains the affected type. An addition MUST be treated as a **versioned schema
   change**: reviewed by the board, released as a coordinated version bump, and rolled
   out so interoperating nodes share the same registered set (a value's `schemaDigest`,
   and hence Entry byte-matching §7.7.2 and signatures over the value, must be identical
   across nodes of the same JGDMS version regardless of extra classpath jars).

3. **An interface-keyed serializer is explicitly a wire-compatibility change.** A
   serializer whose `replaceObType` is an **interface or abstract type** (rather than a
   concrete final leaf like `X500Principal`) can cause deployment A to *substitute* where
   deployment B *encodes the concrete class natively* — two wire forms for one value, the
   determinism defect §0/§3.8 exists to prevent (and the ambiguity source for the WI-1
   most-specific selection rule). Registering an interface-keyed serializer is a
   **breaking wire change** and requires explicit board sign-off beyond an ordinary
   concrete-leaf addition.

4. **Flat-classpath shadowing residual (deployment note).** The registry is read via
   `Class.getResourceAsStream` on the codec's own defining loader — deliberately **not**
   a `ClassLoader.getResources()` merge — so in a proper per-jar / modular deployment
   only jgdms-der's own copy is authoritative. In a **flat / fat classpath**, a
   `META-INF/jgdms/der-serializers` appearing earlier in classpath order can shadow it.
   This is strictly narrower than an open merge (which would admit *every* copy); fully
   closing it requires module encapsulation (do not export/open the resource) or a
   signed-jar check. Deployments that must guarantee the registered set SHOULD run
   jgdms-der as an encapsulated module.

5. **The registry is NOT a decode-admission boundary (security review R2).** Decode does
   **not** consult this registry: the nested-record path reconstructs whatever
   `@AtomicSerial` leaf the transmitted schema chain names, registered or not, and
   `DerReplacer.resolve` only honours the java.io `Resolve` interface. Decode admission
   for a nested field is enforced by the **declared-type assignability gate**
   (`ObjectCodec.admissibleConstructClass`, run before construction — see STD-008
   §16 decode-admission) **+** `DeSerializationPermission("ATOMIC")` (SM-dependent:
   **ENFORCED on DirtyChai** via `CombinerSecurityManager` under
   `-Djava.security.manager=default` — the deployment posture; inert **only** on
   runtimes with no `SecurityManager`, i.e. stock JDK 24+ and non-JVM peers)
   **+** each class's `check(GetArg)` **+** decode depth/size
   bounds (§4.5). Do **not** rely on registry membership to bound what a peer may
   reconstruct; a genuinely `Object`/broad-interface-typed slot is a documented residual
   still governed only by the ATOMIC gate + `check(GetArg)`.

### 7.7 Jini Discovery/Registration Wire Types

These types enable a ServiceRegistrar to be implemented in any language — including
on embedded devices running a sidecar Registrar — and enable dynamic Java proxy
generation for non-JVM services. They depend on `EntrySchemaRecord` for type
identity and on §7.6 scalar types for field value encoding. See STD-005 Appendix B
for the relationship to the `@SerialEntry` validation contract.

#### 7.7.1 Entry Schema Identity

`entryForm()` in `@SerialEntry` classes (STD-005) declares a stable, developer-
controlled wire schema. The DER encoding of that schema, `EntrySchemaRecord`, is
the canonical input to the type-identity hash. Any DER-capable node can compute the
same hash as the JVM by encoding `EntrySchemaRecord` and hashing the result.

```asn1
EntryWireFieldDef ::= SEQUENCE {
    wireName  UTF8String (SIZE(1..255)),
    wireType  UTF8String (SIZE(1..1024))  -- fully qualified type name
}

EntrySchemaRecord ::= SEQUENCE {
    className       UTF8String (SIZE(1..1024)),       -- fully qualified Java class name
    -- superclassHash absent when direct superclass is Object.
    -- When present: SHA-256(DER(superclass EntrySchemaRecord)), computed recursively.
    superclassHash  OCTET STRING (SIZE(32)) OPTIONAL,
    -- ORDER-SIGNIFICANT (§3.8): field[i] corresponds to fieldValues[i] in EntryRecord.
    -- Any reordering changes the hash and creates a distinct type identity.
    fields          SEQUENCE (SIZE(1..maxFields)) OF EntryWireFieldDef   -- §4.5
}
```

**Schema hash computation** (computable by any DER node):

```
schemaHash = SHA-256( DER( EntrySchemaRecord ) )
```

Where `superclassHash` is itself `SHA-256(DER(superclass EntrySchemaRecord))`,
computed recursively up the inheritance chain until `Object`. For the common case
of single-level inheritance (direct superclass is `Object`), `superclassHash` is
absent and the computation is simply `SHA-256(DER(EntrySchemaRecord))`.

**[PROPOSED — migration strategy is Peter's call; the algorithm difference is now
source-confirmed] Hash algorithm migration.** Confirmed against
`org.apache.river.reggie.proxy.EntryClass.computeSerialEntryHash` (reggie-dl) and the
parallel `EntryRep.computeSerialEntryHash` (outrigger-dl). The as-built STD-005 RULE-7
hash is:

- `MessageDigest.getInstance("SHA-256")` over, via `DataOutputStream`:
  `writeLong(superclass.hash)` (the parent's **64-bit** hash) `+ writeUTF(className) +`
  for each field `writeUTF(fieldName) + writeUTF(typeName)`;
- then **truncated to the first 8 bytes → a 64-bit `long`** (`hash += (digest[i] & 0xFF)
  << (i*8)` for `i` in `0..7`).

So the framing is Java `writeUTF`/`writeLong`, and the *output* is a 64-bit truncated
value whose `superclassHash` is itself the parent's 64-bit hash written via
`writeLong`. (A legacy `"SHA"`/SHA-1 path, `computeHash`, still exists for the
pre-`@SerialEntry` `EntryField` form; SHA-256 was adopted "because SHA-1 may be removed
from future JDK releases.")

The DER-format `EntrySchemaRecord` hash is `SHA-256(DER(EntrySchemaRecord))` — a full
**32-byte** hash over **canonical DER** framing (not `writeUTF`), with `superclassHash`
a full 32-byte recursive hash. This is a genuine, breaking change to type identity on
**three** axes at once — digest width (8 vs 32 bytes), framing (`writeUTF` vs DER), and
the recursive `superclassHash` width — so a DER node and a legacy Registrar compute
different identities for the same `@SerialEntry` class. Three migration options:

| Option | Mechanism | Impact |
|---|---|---|
| A | Registrar stores both hash forms during transition | No client changes; Registrar complexity increases |
| B | `EntryRecord` carries a hash-algorithm version tag | Clean versioning; requires client and Registrar changes |
| C | DER Registrar only (new deployment); legacy Registrar retained for existing entries | No migration; two Registrar types coexist |

**RATIFIED (Peter, 2026-07-06): Option B.**

**Option B restated (normative).** The wire carries an explicit **hash-algorithm
version tag** as a field on every hashed record, so the hash scheme is self-describing
and both the legacy 64-bit-truncated `writeUTF` hash and the ATOMIC DER
`SHA-256(DER(EntrySchemaRecord))` hash can coexist and be told apart by inspection —
never inferred. Concretely:

- The version tag is the existing **`hashAlgorithm INTEGER (1..255)`** field already
  present in `EntryRecord` (§7.7.2) and `EntryTemplate` (§7.7.5) — it is **mandatory,
  no `DEFAULT`** (§4.5), so it is always transmitted and a divergent encoder cannot
  silently omit it.
- **Enumerant assignment (normative, closed set — unknown value is a fail-secure
  reject, principle 6):**
  - `hashAlgorithm = 1` → **ATOMIC DER**: `SHA-256(DER(EntrySchemaRecord))`, the full
    32-byte hash over canonical DER with a recursive 32-byte `superclassHash` (§7.7.1).
    This is the ATOMIC DER format's own scheme and the value a DER-native node writes.
  - `hashAlgorithm = 2` → **legacy RULE-7**: the as-built SHA-256-over-`writeUTF`
    scheme truncated to a 64-bit `long`, with a recursive 64-bit `superclassHash`
    (source: `EntryClass.computeSerialEntryHash`). Reserved for interop with a legacy
    Registrar's existing entries during transition; a DER-native node MUST NOT *emit*
    it, but MUST recognise it on decode.
  - other values → reserved; a decoder MUST reject an unrecognised `hashAlgorithm`.
- **Matching is within one algorithm only.** Because Jini template matching is byte
  equality on `schemaHash` (§7.7.2), an `EntryTemplate` and a stored `EntryRecord`
  match **only when their `hashAlgorithm` values are equal** (already required by the
  §7.7.5 comment "must match algorithm in stored `EntryRecord`"). A cross-algorithm
  comparison is never attempted — the version tag makes that boundary explicit rather
  than a silent mismatch.

This makes the algorithm explicit in the wire format (the cleanest long-term choice)
and is why the field is `INTEGER (1..255)` rather than a `DEFAULT`ed or absent value.
Options A and C are not adopted. **The enumerant numbers above are [PROPOSED — confirm]
(they need to be pinned before interop, like the other §4.5/enumerant numbers, open
item 21); the *mechanism* — Option B, version tag in `hashAlgorithm` — is ratified.**

#### 7.7.2 Entry Instances

```asn1
EntryFieldValue ::= CHOICE {
    absent  NULL,          -- null field: wildcard in EntryTemplate, absent value in EntryRecord
    present OCTET STRING   -- DER-encoded field value per §7.6 scalar types
}

EntryRecord ::= SEQUENCE {
    -- MANDATORY, no DEFAULT (§4.5): under DER a DEFAULT-valued component must be
    -- omitted, and a divergent encoder would silently break the byte-equality
    -- matching rule below.
    hashAlgorithm   INTEGER (1..255),               -- Option B version tag (§7.7.1, RATIFIED): 1 = ATOMIC DER SHA-256(DER(EntrySchemaRecord)), 2 = legacy RULE-7 64-bit; unknown = reject
    schemaHash      OCTET STRING (SIZE(32)),        -- identifies the @SerialEntry class
    -- ORDER-SIGNIFICANT (§3.8): fieldValues[i] corresponds to fields[i]
    -- in the EntrySchemaRecord identified by schemaHash.
    fieldValues     SEQUENCE (SIZE(1..maxFields)) OF EntryFieldValue   -- §4.5
}
```

**Field-value matching.** Jini template matching uses `null` as wildcard and exact
equality for non-null fields. Since DER is canonical, equality is byte equality on
the `present OCTET STRING` value. A non-JVM Registrar performs matching with a
byte-string comparison and never needs to decode or understand the field type. This
holds because:
- DER canonical encoding guarantees: same value → same bytes.
- `EntryWireField` types are immutable value types (`String`, `Integer`, etc.)
  whose DER encoding is deterministic.

#### 7.7.3 Service Identity

```asn1
ServiceID ::= OCTET STRING (SIZE(16))
-- RFC 4122 UUID in network byte order (big-endian).
-- Generated once at service export; stable for the lifetime of the service instance.
```

#### 7.7.4 Proxy Descriptor

A service registered by a non-JVM node may either speak JERI DER natively, or
describe its interface for proxy factory code generation. `ProxyDescriptor`
accommodates both patterns.

```asn1
JeriEndpointRecord ::= SEQUENCE {
    spiffeId    UTF8String (SIZE(1..2048)),  -- SPIFFE ID URI; used to verify TLS identity
    host        UTF8String (SIZE(1..253)),   -- hostname or IP
    port        INTEGER (1..65535),
    tlsRequired BOOLEAN                      -- MANDATORY, no DEFAULT (§4.5)
}

TypeDescriptor ::= SEQUENCE {
    typeName  UTF8String (SIZE(1..1024)),  -- Java type name or primitive ("int", "java.lang.String")
    nullable  BOOLEAN                      -- MANDATORY, no DEFAULT (§4.5)
}

OperationDescriptor ::= SEQUENCE {
    name        UTF8String (SIZE(1..255)),
    -- ORDER-SIGNIFICANT (§3.8): parameter order is part of the method signature.
    parameters  SEQUENCE (SIZE(0..maxParameters)) OF TypeDescriptor,   -- §4.5
    returnType  TypeDescriptor
}

ServiceSpecRecord ::= SEQUENCE {
    -- Desired Java interface name; proxy factory generates this interface.
    interfaceName  UTF8String (SIZE(1..1024)),
    -- ORDER-SIGNIFICANT (§3.8): operation order determines generated interface layout.
    operations     SEQUENCE (SIZE(1..maxOperations)) OF OperationDescriptor,   -- §4.5
    -- Protocol identifier: "coap", "mqtt", "http", "jeri-der", etc.
    protocol       UTF8String (SIZE(1..255)),
    -- Protocol-specific endpoint URI (e.g. "coap://[::1]:5683/sensor").
    endpoint       UTF8String (SIZE(1..2048))
}

ProxyDescriptor ::= CHOICE {
    jeriEndpoint  [0] IMPLICIT JeriEndpointRecord,  -- device speaks JERI DER natively
    serviceSpec   [1] IMPLICIT ServiceSpecRecord    -- proxy factory bridge
}
```

**Proxy factory integration.** When a `serviceSpec` descriptor is received, the
proxy factory service:
1. Generates a Java proxy JAR implementing the described interface (translating
   Java method calls to the device's native protocol).
2. Submits the JAR to the SCAP pipeline.
3. Serves it via `DynamicProxyCodebaseAccessor` after a `SAFE` verdict.
4. Issues a `DigestGrant` + `LoadClassPermission` so client JVMs can load it through
   the normal `PreferredProxyCodebaseProvider` path.

Client JVMs discover the service, receive the `ServiceItemRecord`, and load the
generated proxy exactly as they would any other JGDMS proxy — SCAP, `DigestGrant`,
and `LoadClassPermission` apply unchanged.

#### 7.7.5 ServiceItem and ServiceTemplate

```asn1
ServiceItemRecord ::= SEQUENCE {
    serviceId   ServiceID,
    -- behavioural; unordered (§3.8): a Jini attribute set is order-independent, so
    -- no element position here is order-significant. [PROPOSED — align to §3.8/§7.6:
    -- an unordered collection is a canonicalise case, so this SHOULD be a SET OF (tag
    -- 0x31), octet-sorted under Option A, so two encoders of the same attribute set
    -- produce byte-identical ServiceItemRecords (matters for content-addressing and
    -- Entry byte-matching). Kept SEQUENCE OF here pending Peter's ratification of the
    -- tag flip, since it is a byte-on-the-wire change to a fixed §7.7 record.]
    attributes  SEQUENCE (SIZE(0..64)) OF EntryRecord,  -- 64 = Jini spec attribute limit
    proxy       ProxyDescriptor
}

EntryTemplate ::= SEQUENCE {
    hashAlgorithm INTEGER (1..255),               -- Option B version tag (§7.7.1); MANDATORY, no DEFAULT (§4.5); MUST equal the stored EntryRecord's hashAlgorithm to match
    schemaHash    OCTET STRING (SIZE(32)),        -- identifies the @SerialEntry class to match
    -- ORDER-SIGNIFICANT (§3.8): fieldValues[i] matches fields[i].
    -- absent = wildcard (matches any value including null).
    -- present = must byte-match the stored EntryRecord fieldValues[i].
    fieldValues   SEQUENCE (SIZE(1..maxFields)) OF EntryFieldValue   -- §4.5
}

-- Context tags per §4.5 distinct-tag rule: requiredInterfaces and
-- attributeTemplates are both SEQUENCE OF (tag 0x30) and both OPTIONAL --
-- untagged they are indistinguishable when only one is present.
ServiceTemplateRecord ::= SEQUENCE {
    serviceId           [0] IMPLICIT ServiceID OPTIONAL,
    -- Interface hashes: schemaHash of each required service interface.
    -- [PROPOSED] Interface type identity is a DIFFERENT notion from @SerialEntry
    -- schema identity: reggie's ServiceType/EntryClass hash the CLASS (name +
    -- superclass hash), not an entryForm() field list, and a service INTERFACE has
    -- no serialForm()/entryForm() at all. The 32-byte width here presumes a
    -- SHA-256(class-name)-style identity; whichever scheme is chosen (a) MUST be the
    -- same one the Registrar indexes interfaces under, and (b) inherits the §7.7.1
    -- hash-migration decision (legacy 64-bit truncated vs DER 32-byte). Confirm the
    -- interface-identity scheme and its width against the Registrar's interface index
    -- before pinning 32 bytes here.
    requiredInterfaces  [1] IMPLICIT SEQUENCE (SIZE(0..maxInterfaces)) OF OCTET STRING (SIZE(32)) OPTIONAL,   -- §4.5
    -- behavioural; unordered (§3.8): an attribute-template set is order-independent
    -- (mirrors ServiceItemRecord.attributes). [PROPOSED — same as
    -- ServiceItemRecord.attributes: SHOULD become a SET OF (0x31) octet-sorted under
    -- Option A; kept SEQUENCE OF pending Peter's ratification of the byte-level flip.]
    attributeTemplates  [2] IMPLICIT SEQUENCE (SIZE(0..64)) OF EntryTemplate OPTIONAL    -- same 64 ceiling as ServiceItemRecord.attributes
}
```

#### 7.7.6 Lease Records

```asn1
-- Named values, matching net.jini.core.lease.Lease exactly:
--   Lease.FOREVER = Long.MAX_VALUE -- "never expires" / "request forever"
--   Lease.ANY     = -1             -- "any duration the grantor chooses is acceptable"
-- These are DISTINCT and both may appear in requestedDuration; conflating them
-- (encoding ANY as FOREVER) turns a modest request into an unbounded one.
leaseForever INTEGER ::= 9223372036854775807
leaseAny     INTEGER ::= -1

LeaseRecord ::= SEQUENCE {
    leaseId     OCTET STRING (SIZE(16)),   -- UUID
    grantorId   ServiceID,
    -- Absolute expiry in epoch-millis.  leaseForever = no expiry.
    -- leaseAny is NOT valid here: a granted lease always has a definite expiry
    -- or leaseForever.
    expiry      INTEGER,
    renewable   BOOLEAN                    -- MANDATORY, no DEFAULT (§4.5)
}

LeaseRenewalRecord ::= SEQUENCE {
    leaseId           OCTET STRING (SIZE(16)),
    -- Requested additional duration in milliseconds (positive), or one of the
    -- named values: leaseAny (grantor picks), leaseForever (request no expiry).
    requestedDuration INTEGER
}

LeaseCancellationRecord ::= SEQUENCE {
    leaseId OCTET STRING (SIZE(16))
}
```

### 7.7.7 Multicast Discovery Wire Types

These types cover the UDP multicast protocol by which Registrars announce their
presence and clients request discovery. Two format variants are defined, differing
in how trust is established for the signature.

#### Format variants

| Format name | Trust model | Leaf cert in packet | Use case |
|---|---|---|---|
| `net.jini.discovery.x500.SHA256withECDSA` | Static trust store (leaf cert lookup) | No | Non-SPIFFE ECDSA deployments; compatible with existing `X500Provider` infrastructure |
| `net.jini.discovery.spiffe.SHA256withECDSA` | PKIX CA-chain validation against SPIRE trust bundle | Yes — required for chain validation | SPIFFE-attested Registrars and embedded sidecar Registrars |

The `net.jini.discovery.x500.SHA256withDSA` and `net.jini.discovery.x500.SHA256withRSA`
formats (existing) are unaffected and not defined here.

#### ECDSA signature size

P-256 DER-encoded ECDSA signatures are at most 72 bytes. P-384 signatures are at
most 104 bytes. `MAX_SIGNATURE_LEN` in the implementation must match the curve in
use. See `SigningBufferFactory` buffer reservation arithmetic.

#### SPIFFE Subject DN requirement

The multicast protocol identifies signers by their X500Principal Subject DN
(`cert.getSubjectX500Principal()`). SPIFFE SVIDs MAY have an empty Subject DN
(the SPIFFE ID travels in the URI SAN). An empty Subject DN is not usable as a
signer identity in this protocol.

**Deployment requirement:** SPIRE registration entries for any node using the
`net.jini.discovery.spiffe.*` format MUST be configured to issue SVIDs with a
non-empty Subject DN. Recommended form: `CN=<path-component>`, e.g.
`CN=lookup` for `spiffe://jgdms.example.org/host/lookup`. Implementations MUST
detect and log a clear error (not a cryptic exception) if the Subject DN is empty.

#### CA-based trust and SVID rotation

For `net.jini.discovery.spiffe.*` formats, the verifier does NOT require the
signer's leaf certificate in a static trust store. The leaf certificate travels
in the packet and is validated via PKIX chain validation against the SPIRE trust
bundle (`X509Certificate[]` from `SpiffeCredentialManager.getTrustBundle()` —
**CONFIRMED**: `public X509Certificate[] getTrustBundle()` at
`au.zeus.jdk.authorization.spire.SpiffeCredentialManager` line 293, DirtyChai
`java.base`; the method name in this spec is correct). This
makes hourly SVID rotation transparent: the CA certificate is stable, and any SVID
issued by the same SPIRE CA is accepted throughout its lifetime without any
trust-store update. See §8.2 for the relationship to `SpiffeCredentialManager`.

PKIX revocation checking MUST be disabled (`PKIXParameters.setRevocationEnabled(false)`)
since SPIFFE SVIDs are short-lived and have no CRL distribution points.

```asn1
-- ─── Multicast announcement ──────────────────────────────────────────────
-- Sent periodically by a Registrar on UDP port 4160 (Jini multicast group).
-- Corresponds to Plaintext.encodeMulticastAnnouncement / X500Server.encodeMulticastAnnouncement.

MulticastAnnouncementRecord ::= SEQUENCE {
    sequenceNumber  INTEGER,
    host            UTF8String (SIZE(1..253)),       -- Registrar unicast host
    port            INTEGER (1..65535),              -- Registrar unicast port
    -- ORDER-SIGNIFICANT (§3.8): group membership order is preserved
    groups          SEQUENCE (SIZE(0..maxGroups)) OF UTF8String,   -- §4.5
    serviceId       ServiceID,                       -- stable Registrar identity
    -- X500Principal Subject DN of the signer (MUST be non-empty; see above)
    signerPrincipal UTF8String,
    -- DER-encoded X.509 leaf certificate of the signer.
    -- REQUIRED for net.jini.discovery.spiffe.* formats (PKIX chain validation).
    -- ABSENT for net.jini.discovery.x500.* formats (trust store model).
    -- [0] IMPLICIT context tag (§4.5): untagged, an absent signerCert makes the
    -- signature OCTET STRING indistinguishable from it.
    signerCert      [0] IMPLICIT OCTET STRING OPTIONAL,
    -- DER-encoded signature over the TBS content (see "TBS content" below --
    -- includes the format name and protocol version, which are NOT transmitted).
    -- Format: output of java.security.Signature.sign() with SHA256withECDSA
    -- (or SHA256withDSA / SHA256withRSA for x500.* variants — see format name).
    signature       OCTET STRING
}

-- ─── Multicast request ───────────────────────────────────────────────────
-- Sent by a client seeking Registrars.
-- Corresponds to Plaintext.encodeMulticastRequest / X500Client.encodeMulticastRequest.

MulticastRequestRecord ::= SEQUENCE {
    host            UTF8String (SIZE(1..253)),       -- client unicast host
    port            INTEGER (1..65535),              -- client unicast port
    -- ORDER-SIGNIFICANT (§3.8)
    groups          SEQUENCE (SIZE(0..maxGroups)) OF UTF8String,           -- §4.5
    -- ORDER-SIGNIFICANT (§3.8): ServiceIDs already known to the client.
    -- A Registrar whose ServiceID appears here need not respond.
    knownServiceIds SEQUENCE (SIZE(0..maxKnownServiceIds)) OF ServiceID, -- §4.5
    signerPrincipal UTF8String,
    signerCert      [0] IMPLICIT OCTET STRING OPTIONAL,   -- §4.5 distinct-tag rule
    signature       OCTET STRING
}
```

**TBS (to-be-signed) content (NORMATIVE).** The signature covers one canonical DER
`SEQUENCE` — **not** the concatenated raw DER octets of each field individually —
constructed as:

```asn1
-- Domain-separation prefix: SIGNED BUT NOT TRANSMITTED.  Both values are
-- already known to the verifier from the discovery packet header (the
-- protocol-2 format-ID negotiation), so transmitting them would be
-- redundant; binding them into the TBS prevents a signature produced under
-- one format/version from verifying under another (cross-format /
-- cross-protocol signature reuse).

MulticastAnnouncementTbs ::= SEQUENCE {
    formatName      UTF8String,     -- SIGNED, NOT TRANSMITTED (known from packet header)
    protocolVersion INTEGER,        -- SIGNED, NOT TRANSMITTED
    sequenceNumber  INTEGER,
    host            UTF8String (SIZE(1..253)),
    port            INTEGER (1..65535),
    groups          SEQUENCE (SIZE(0..maxGroups)) OF UTF8String,
    serviceId       ServiceID,
    signerPrincipal UTF8String,
    signerCert      [0] IMPLICIT OCTET STRING OPTIONAL
}

MulticastRequestTbs ::= SEQUENCE {
    formatName      UTF8String,     -- SIGNED, NOT TRANSMITTED
    protocolVersion INTEGER,        -- SIGNED, NOT TRANSMITTED
    host            UTF8String (SIZE(1..253)),
    port            INTEGER (1..65535),
    groups          SEQUENCE (SIZE(0..maxGroups)) OF UTF8String,
    knownServiceIds SEQUENCE (SIZE(0..maxKnownServiceIds)) OF ServiceID,
    signerPrincipal UTF8String,
    signerCert      [0] IMPLICIT OCTET STRING OPTIONAL
}
```

The signer computes the applicable `SEQUENCE` once and signs its octets; the verifier
reconstructs the identical `SEQUENCE` from the packet-header format name/version and
the received fields, and verifies. The embedded `signerCert` is opaque octets (§3.8)
and contributes its verbatim bytes (with its `[0]` context tag as encoded), so a
non-strict-DER leaf certificate cannot break an otherwise-valid announcement
signature. (Same canonical-DER rule as the SCAP signature input, §7.4.1.)

**Replay (NORMATIVE note).** These records carry no freshness field, and
`sequenceNumber` monotonicity is advisory (it survives neither Registrar restart nor
packet reordering). A signed announcement therefore proves *origin*, not *liveness*:
an attacker can replay a stale announcement indefinitely. Receivers MUST treat
announcements and requests as advisory discovery hints only — the trust decision is
made at the subsequent unicast connection, which is independently authenticated
(TLS/SPIFFE). An implementation MUST NOT grant anything, cache trust, or evict
known-good Registrar state on the strength of a multicast packet alone; the worst a
replayed packet can achieve is directing a client to attempt a connection that then
fails authentication (an availability nuisance, in line with the existing Jini
multicast threat model).

**[OPEN] MTU constraint.** The existing Jini multicast datagram is tuned for
~512–1500 byte UDP payloads. Including a DER-encoded leaf certificate (typically
400–600 bytes for P-256) may push the announcement close to or over the MTU on
some network paths. Measure actual SVID cert sizes from the deployed SPIRE instance
before finalising. If the cert makes the datagram too large, consider: (a) sending
the cert only on the first announcement after a rotation, (b) a cert reference
(principal + serial number) with a separate cert-fetch step, or (c) a minimum MTU
requirement for the `net.jini.discovery.spiffe.*` format.

### 7.7.8 Unicast Discovery Response

The unicast response is returned over a TCP connection (SSL/TLS, authenticated by
SPIFFE SVID) after a client connects to the Registrar's unicast port following
a multicast announcement.

#### Deprecation of the cert-grant mechanism

The existing unicast response for `net.jini.discovery.ssl.*` formats includes an
inline certificate block written by `EndpointBasedServer.writeClassAnnotationCerts`
and consumed by `EndpointBasedClient.readAnnotationCertsGrantPerm`. This mechanism
issued a `PermissionGrant` (with `DownloadPermission` and
`DeSerializationPermission("ATOMIC")`) to the proxy codebase for cert-signed code.

**This mechanism is deprecated for removal and MUST NOT appear in any DER format
implementation.** Trust is established by:
- The SPIFFE TLS layer at the connection level (no cert data needed in the response).
- The `VerdictRegistry` + `DigestGrant` + `LoadClassPermission` pipeline for proxy
  JAR trust (§8.2 and the SCAP pipeline, STD-002).

`EndpointBasedServer.writeUnicastResponse()` and
`EndpointBasedClient.readUnicastResponse()` are `protected` and overrideable.
DER format implementations override these two methods. All other infrastructure
(`EndpointBasedProvider`, `EndpointBasedServer`, `EndpointBasedClient`) is
unchanged.

```asn1
-- Unicast response: returned over TLS/SPIFFE TCP after a client contacts the
-- Registrar's unicast port.
-- Replaces: Plaintext.writeUnicastResponse + writeClassAnnotationCerts.
-- Extension point: EndpointBasedServer.writeUnicastResponse() override.

UnicastResponseRecord ::= SEQUENCE {
    host        UTF8String (SIZE(1..253)),    -- Registrar host (may differ from
                                              -- multicast announcement host)
    port        INTEGER (1..65535),           -- Registrar JERI port
    -- ORDER-SIGNIFICANT (§3.8): group membership order preserved
    groups      SEQUENCE (SIZE(0..maxGroups)) OF UTF8String,   -- §4.5
    serviceId   ServiceID,
    -- ProxyDescriptor CHOICE (§7.7.4):
    --   [0] JeriEndpointRecord — Registrar speaks JERI DER natively
    --   [1] ServiceSpecRecord  — embedded device; proxy factory bridge
    proxy       ProxyDescriptor
    -- No cert data: SPIFFE TLS at the connection layer establishes trust.
    -- No codebase annotation: writeClassAnnotationCerts is REMOVED (see above).
    -- No Java-serialized ServiceRegistrar proxy: replaced by ProxyDescriptor.
}
```

**[RESOLVED]** `UnicastResponse` constructor for the DER client side. Confirmed
against `org.apache.river.discovery.UnicastResponse`: its public constructor is
`UnicastResponse(String host, int port, String[] groups, ServiceRegistrar registrar)`
and it takes an **already-built** `ServiceRegistrar` proxy. **No new subtype is
required**: for the `JeriEndpointRecord` path the client constructs the
`ServiceRegistrar` JERI stub first (below) and then builds a `UnicastResponse` from
`(host, port, groups, registrar)` directly. (The DER path overrides
`EndpointBasedClient.readUnicastResponse()` to build the stub from the
`UnicastResponseRecord` instead of reading a Java-serialized proxy, then calls this
same constructor.)

**[RESOLVED]** `JeriEndpointRecord` → JERI stub construction on the client. Confirmed
against `net.jini.jeri.ssl.SslEndpoint` and the discovery providers
(`org.apache.river.discovery.ssl.sha256.Client`): the factory path is
`SslEndpoint.getInstance(host, port, socketFactory)` (the 3-arg factory;
`socketFactory` may be `null` for default sockets), which is then wrapped by an
`AtomicILFactory`-produced invocation handler into the `ServiceRegistrar` proxy at the
JERI `BasicObjectEndpoint`/`BasicInvocationHandler` layer — exactly the pattern the
discovery `Client` uses (`SslEndpoint.getInstance("ignored", 1, factory)` there, with
real `host:port` substituted for the DER path). No prior unicast handshake is needed to
build the endpoint; the SPIFFE `spiffeId` from the record is used to constrain/verify
the TLS peer identity on first connect (the constraints carried by the
`AtomicILFactory`/`SslEndpoint`), not to construct the stub. **[PROPOSED — confirm the
exact `AtomicILFactory` construction arguments (server constraints, permission class,
loader) for the Registrar stub on the DER client path; `SslEndpoint.getInstance` itself
is confirmed.]**

### 7.8 MarshalledInstance

`MarshalledInstance` is the standard container for a serialised object that must
travel across a JERI connection or be stored independently of its class. It bundles
the DER-encoded payload with the schema needed to decode it, ensuring the data
independence property of §3.11. The schema bytes are unconditionally required — not
optional — because `MarshalledInstance` must be self-decodable without any external
service. During unicast discovery, for example, the `ServiceRegistrar` proxy arrives
in a `MarshalledInstance` before the schema registry or `SchemaAccessor` are
available.

```asn1
AtomicSerialFieldDef ::= SEQUENCE {
    wireName  UTF8String (SIZE(1..255)),
    wireType  UTF8String (SIZE(1..1024))
}

AtomicSerialSchemaRecord ::= SEQUENCE {
    className        UTF8String (SIZE(1..1024)),
    -- SHA-256(DER(parent AtomicSerialSchemaRecord)); absent when parent is Object
    parentSchemaHash OCTET STRING (SIZE(32)) OPTIONAL,
    -- ORDER-SIGNIFICANT (§3.8): field[i] corresponds to payload position i
    fields           SEQUENCE (SIZE(0..maxFields)) OF AtomicSerialFieldDef   -- §4.5
}

-- Schema version = SHA-256(DER(AtomicSerialSchemaRecord))
-- Computed from serialForm() at class-load time; no developer assignment needed.
-- Schema identity is the triple (className, parentSchemaHash, fields): the class
-- name is part of the record, so two classes with identical field lists but
-- different names produce DIFFERENT schema versions -- deliberately, since
-- namespaces are per-class (S3.9).  What requires no coordination is versioning
-- WITHIN a class: any two builds of the same class with the same serialForm()
-- produce the same digest, with no developer-assigned version number.
-- The parentSchemaHash forms a Merkle chain: the leaf class digest captures the
-- entire hierarchy schema identity.

MarshalledInstanceRecord ::= SEQUENCE {
    -- DER-encoded payload object: the hierarchy payload defined below
    -- ("Hierarchy payload layout"), encoded against the schema chain in
    -- schemaBytes.
    payloadBytes       OCTET STRING,
    -- Full AtomicSerialSchemaRecord chain for the payload, leaf class first.
    -- REQUIRED — unconditionally present (see §3.11 and §2.3).
    -- Enables data recovery without code; enables bootstrap unmarshalling during
    -- discovery before schema registry or SchemaAccessor are available.
    schemaBytes        OCTET STRING,
    -- SHA-256(DER(leaf AtomicSerialSchemaRecord)).
    -- A ROUTING HINT, not an integrity claim: the receiver MUST verify it against
    -- schemaBytes before acting on it (see "schemaDigest verification" below).
    schemaDigest       OCTET STRING (SIZE(32)),
    -- Payload encoding format identifier.
    payloadFormat      UTF8String     -- "JGDMS-STD-006/ATOMIC-DER"

    -- NOTE: there is no codebase-annotation field. The authenticated
    -- `CodebaseAccessor` channel (§8.2) is the ONLY codebase mechanism (§8.3).
    -- The record is exactly these four fields; a decoder MUST reject a record
    -- containing trailing content after payloadFormat (fail-secure, principle 6).
}
```

**Hierarchy payload layout (NORMATIVE — added v0.13; was implemented but
unspecified).** `payloadBytes` contains one outer wrapper `SEQUENCE` holding one
private per-class `SEQUENCE` for each `@AtomicSerial` class in the hierarchy, in
**superclass-first (root-first, leaf-last)** order:

```
SEQUENCE {          -- outer hierarchy wrapper
  SEQUENCE { ... }  -- root @AtomicSerial class's private SEQUENCE (first on wire)
  SEQUENCE { ... }  -- ...intermediate classes, in inheritance order...
  SEQUENCE { ... }  -- leaf class's private SEQUENCE (last on wire)
}
```

Note the order is the **opposite** of the schema chain: `schemaBytes` is leaf-first
(leaf → root), the payload wrapper is root-first (root → leaf). For a hierarchy of
`n` `@AtomicSerial` classes, schema record `i` (0-based, leaf-first) describes
payload `SEQUENCE` `n−1−i`. Within each per-class `SEQUENCE`, `fields[j]` of the
corresponding schema record describes the value at position `j` (§3.9). A
single-class object is the degenerate case: a wrapper containing one `SEQUENCE`.
The wrapper carries no class names and no discriminators — the schema chain is the
sole source of structure, which is exactly the §3.11 data-independence property.

**schemaDigest verification (NORMATIVE — added v0.13).** Before using
`schemaDigest` for anything — including the §12.4 fast-path comparison against the
local `serialForm()` digest — the receiver MUST verify that `schemaDigest` equals
`SHA-256` of the first (leaf) `AtomicSerialSchemaRecord` `SEQUENCE` in
`schemaBytes`. The cost is one hash over a few hundred bytes; skipping it lets a
sender route a payload encoded under one schema into a decode under another.
(Field-level type confusion from a lying digest gains an attacker nothing beyond
what control of `payloadBytes` already gives — `check(GetArg)` still runs — but a
format that *silently* decodes data against the wrong schema violates fail-secure
decode, principle 6.) If `schemaBytes` is absent, empty, undecodable, fails the
`parentSchemaHash` chain cross-check, is truncated (terminal record carrying a
`parentSchemaHash` — see "Chain integrity and ceilings" below), exceeds the §4.5
`maxChainRecords`/`maxChainBytes` ceilings, or does not match `schemaDigest`, the
record MUST be **rejected** — no fallback to a local or registry schema (§12.4).

**Schema is authoritative for decoding.** The `schemaBytes` embedded in
`MarshalledInstanceRecord` are the authoritative, permanent schema for decoding
`payloadBytes`. A receiver MUST use this embedded schema to populate `GetArg`,
not its own current `serialForm()`. The receiver's `serialForm()` may differ from
the embedded schema if schema and code have evolved independently — this is expected
and correct. The `@AtomicSerial` `GetArg` layer absorbs the divergence (§3.11).
Using the embedded schema rather than the current `serialForm()` ensures the data is
always decoded as it was encoded, with `GetArg` handling any difference between what
the data contains and what the code requests.

**Schema chain encoding.** `schemaBytes` contains the DER encoding of the leaf
class's `AtomicSerialSchemaRecord` followed by each parent class's
`AtomicSerialSchemaRecord` in hierarchy order (leaf → root), concatenated. Each
record in the chain is a complete, self-contained DER SEQUENCE. The decoder reads
each record in order; `parentSchemaHash` in each record provides a cross-check
against the next record in the chain.

**Chain integrity and ceilings (NORMATIVE — added under T6, chain-ceiling
adoption).** A well-formed chain is **complete**: its terminal (last, root) record
MUST NOT carry a `parentSchemaHash` — a terminal record with a dangling
`parentSchemaHash` is a **truncated chain** and MUST be rejected. (Without this
check, a truncated chain `{leaf}` and the full chain `{leaf, parent, root}` yield
the same leaf digest from different `schemaBytes`, breaking the injectivity of
`schemaDigest` over chain bytes.) The adjacent-pair cross-check (each record's
`parentSchemaHash` equals the next record's schema version digest, and every
non-terminal record MUST carry one), the completeness check, and the §4.5 chain
ceilings (`maxChainRecords`, `maxChainBytes` — metered **during** the chain-decode
loop, rejecting on breach before further records are parsed) apply identically at
**every** chain decode site: the top-level `MarshalledInstanceRecord` and each
nested `@AtomicSerial` field record's embedded chain. All of these are hard decode
rejects (fail-secure, principle 6) — never a skip, default, or fallback.

**Schema version comparison.** After the mandatory verification of `schemaDigest`
against `schemaBytes` (above), the receiver computes
`SHA-256(DER(leafAtomicSerialSchemaRecord))` from its own `serialForm()` and
compares it to `schemaDigest`. A match indicates case (a) from §3.9 — primary path,
all fields present, and (because the digest is verified) the local schema and the
embedded schema are byte-identical, so either may be used. A mismatch indicates
case (b) — schema version differs; the embedded schema is used and `GetArg`
defaults handle divergence.

**`GetArg` population.** Using the schema in `schemaBytes`, the decoder reads
`payloadBytes` sequentially, populates `GetArg` completely (all fields from the
schema), then invokes the `(GetArg)` constructor chain. Unrequested fields are
stored in `GetArg` memory until construction completes, then released for GC.

---

## 8. Codebase Annotations — Deprecated for Removal

Codebase annotations **do not appear** in the ATOMIC DER wire format. This section
records why, and where code identity does travel.

### 8.1 What Codebase Annotations Were

Java Object Serialization embeds URL annotations in the stream alongside each
class descriptor. The intent was to allow a receiver to locate and load class
definitions it did not already have. In the Java RMI / Jini context this produced a
family of well-documented failures (Warres, 2006, SMLI TR-2006-149):

- **Type conflicts** — classes loaded from different codebases are distinct types
  even when their class files are identical. Multi-service interactions
  (`ClassCastException` across service composition and orchestration) arise directly.
- **Codebase annotation loss** — a class resolved locally during unmarshalling
  inherits the local process's codebase annotation when later marshalled onward,
  losing the original source.
- **Codebase annotation mixing** — an object graph containing both local and
  downloaded classes carries mixed annotations; the receiver maps them to sibling
  codebase loaders, causing type incompatibilities.
- **Stale content** — JAR file caching by URL means an updated codebase is invisible
  to a client that already cached the previous bytes.
- **Codebase configuration errors** — deployers specify `java.rmi.server.codebase`
  manually; misconfigurations (wrong host, `file:` URLs, missing JARs) produce
  run-time failures that cannot be detected at compile time.
- **DNS trust.** URL-based codebase identity trusts DNS to resolve names
  consistently, exposing the permission grant model to DNS poisoning and URL replay
  attacks. The same URL can serve different bytes; a grant tied to a URL is therefore
  a grant tied to a DNS name rather than to a specific artifact.

### 8.2 The JGDMS Replacement Architecture

JGDMS replaces the in-stream annotation mechanism entirely with two orthogonal,
authenticated channels:

**Channel 1 — JAR discovery: `CodebaseAccessor.getClassAnnotation()`.**
The service proxy implements `CodebaseAccessor`, a JGDMS interface. After the client
establishes a JERI connection and verifies the server's SPIFFE workload identity over
TLS, it calls `getClassAnnotation()` as an authenticated remote method. The returned
URL list is the set of JARs the proxy requires. Because the call goes over an
authenticated TLS channel (the server's SVID has already been verified), the URL list
is server-asserted under a proven identity, not an in-band hint in the serialization
stream that any intermediary could have substituted.

`PreferredProxyCodebaseProvider` receives this URL list, downloads the JARs,
and submits them to the VerdictRegistry before constructing any class loader.
`LoadClassPermission` is granted (via `DynamicPolicyProvider`) only after a `SAFE`
verdict is confirmed, and only for a JAR whose SHA-256 matches a policy `DigestGrant`
entry. The URL is a locator; the SHA-256 is the security-relevant identity.

**Channel 2 — Code identity in the ACC: `DigestCodeSourceRecord` (§7.2).**
Once a JAR is loaded, the class's `ProtectionDomain` carries a `DigestCodeSource`
whose `byte[] digest` is the SHA-256 of the actual JAR bytes (§7.3). When the
`AccessControlContextRecord` is transmitted over the JERI wire, the domain's
`DigestCodeSource` identity travels in the `digest` arm of a `ReducingDomainRecord`
(§7.2), including this digest. The receiver can therefore identify which code produced
the calling domain — by content hash, not by URL — and re-verify it at policy time before
deciding whether to accept the domain into its policy evaluation.

This architecture eliminates every failure class enumerated in §8.1:

| Warres (2006) failure class | JGDMS replacement |
|---|---|
| Type conflicts from sibling codebase loaders | `DigestCodeSource` identity is hash-based; the same bytes from different URLs produce the same domain identity |
| Codebase annotation loss | No in-stream annotation to lose; JAR identity is the SHA-256 pinned at load time |
| Codebase annotation mixing | Not applicable — no annotations in the stream |
| Stale content | `digestCache` pins the first-seen hash for a URI for the JVM session; a changed JAR produces a hash mismatch, not a silent stale read |
| Configuration errors | `CodebaseAccessor.getClassAnnotation()` is an authenticated API call; there is no `java.rmi.server.codebase` system property to misconfigure |
| DNS trust / URL replay | Grants are `DigestGrant`-conditioned on SHA-256; a URL change or DNS substitution produces a hash mismatch |

### 8.3 Implications for the ATOMIC DER Wire Format

The DER object stream carries **no codebase annotation field** at any level —
not per-object, not per-frame, not as a stream header. There is no frame-level
codebase table, no URL annotation alongside class descriptors, no concept equivalent
to `java.rmi.server.codebase`. (This holds without exception, including §7.8
`MarshalledInstanceRecord`.)

Code identity reaches the receiver via the `AccessControlContextRecord` (§7.2), which
is a first-class wire type in its own right. That is the complete code-identity
mechanism. Object bodies carry only field values.

`AtomicMarshalOutputStream` already reflects this: the `writeCodebaseAnnotations`
constructor parameter defaults to `false`, making the existing Java-serialization
stream annotation-free in current JGDMS deployments. The DER format formalises the
permanent absence of annotations as a property of the encoding, not a runtime flag.

**Migration note.** Existing JGDMS deployments using `writeCodebaseAnnotations=false`
(the default) require no changes to their object-stream content. The only
stream-level change is the encoding format itself (DER replacing Java serialization
bytes). Code that reads the stream-level codebase annotation (`annotateClass` /
`annotateProxyClass` / `readAnnotation`) has no DER equivalent and must be removed
during the migration.

---

## 9. Conformance

A conforming implementation:

1. Encodes and decodes all §6 wire types per their §7 ASN.1 modules using DER.
2. Enforces every schema `SIZE`/value constraint as a hard, fail-secure bound
   *before* allocation.
3. Rejects (no object constructed) on any decode failure, constraint breach, unknown
   closed-enumeration value, or disallowed algorithm OID.
4. **Rejects any encoding that would require a back-reference or produce a cycle in
   the object graph (§3.7).** Since DER cannot express a back-reference and no schema
   module defines one, this is satisfied by schema conformance; it is stated
   explicitly so the acyclic property is treated as a security requirement, not an
   incidental encoding property. The frame-level codebase table (§8) is the sole
   reference-like construct and is admissible only under the §8 ordering constraint.
5. **Preserves the order of order-significant sequences (§3.8)** — principal chains,
   certificate paths, arrays, linked lists, stack traces — and must not reorder,
   deduplicate, or canonicalise them. For a collection whose declared type does not
   guarantee a deterministic iteration order (`HashSet`, `HashMap`, `ConcurrentHashMap`;
   the concurrent insertion/FIFO and priority collections — see item 14), wire order is
   instead **canonicalised** by the encoder (§3.8 octet-sort), not left to the receiver;
   the constructed object still imposes uniqueness and null-policy after validation.
   Collection element order is thus always fixed on the wire — preserved for
   deterministic-order types, canonicalised for the rest (see item 14) — never
   receiver-imposed from arbitrary
   bytes.
6. Preserves `SEQUENCE OF` ordering where ordering is semantically required (§7.1
   Subject order and principal-chain order, ACC domain order, §7.6 stack-trace order).
7. **Carries externally-produced DER structures — X.509 `Certificate`, X.501 `Name`
   (`X500Principal`), nested signatures — as opaque `OCTET STRING` octets captured
   verbatim from `getEncoded()`, and never parses, re-encodes, sorts, or
   re-canonicalises them (§3.8 opaque-octet carve-out).** A conformance suite MUST
   demonstrate byte-for-byte survival of a non-strict-DER ("BER-ish") certificate and
   of a `Name` bearing a `TeletexString` attribute value.
8. Computes signatures over the canonical DER of the `tbs` (§7.4.1), and over the
   applicable `MulticastAnnouncementTbs`/`MulticastRequestTbs` `SEQUENCE` — which
   binds the signed-but-not-transmitted format name and protocol version ahead of
   the record fields — for record-level signatures (§7.7.7), verifying against the
   received octets without re-encoding them.
9. Runs the STD-001 `check()` validation contract on decoded values before treating
   any object as constructed.
10. Replicates the encoder obligations documented here — the order-significant
   domain/principal sequences of §3.8 / §7.1 / §7.2 (preserved, never sorted or
   deduplicated) and the `jrt:/java.base` exclusion of §7.2 (encoder drops it, decoder
   refuses it) — so that JVM and non-JVM encoders produce byte-identical output for
   identical logical content (required for any signature-bearing type) and no encoder
   transmits the always-privileged platform domain.
11. **Decodes strictly from the published modules (§4.5).** Every `OPTIONAL`
   component is tag-distinguishable; no positional/count-based disambiguation
   exists or is permitted. No module carries `DEFAULT`; a decoder does not need —
   and must not implement — DER default-omission handling for this format.
12. **Encodes the hierarchy payload in root-first wrapper order and maps it against
   the leaf-first schema chain** exactly as §7.8 "Hierarchy payload layout"
   specifies (schema record `i` ↔ payload `SEQUENCE` `n−1−i`).
13. **Verifies `schemaDigest` against `schemaBytes` before any use, and rejects**
   any `MarshalledInstanceRecord` whose embedded schema is absent, undecodable,
   chain-inconsistent, or digest-mismatched (§7.8, §12.4). No local-schema or
   registry fallback exists at decode time.
14. **Applies the §3.8 encounter-order discipline to collection-valued fields.**
   Preserves the encounter order of collections whose declared type guarantees a
   deterministic iteration order — the sequenced backbone (`List`, `Deque`,
   `LinkedHashSet`, `LinkedHashMap`, `SortedSet`/`TreeSet`, `SortedMap`/`TreeMap`),
   `EnumSet`/`EnumMap` (natural ordinal order), the concurrent sorted collections
   `ConcurrentSkipListSet`/`ConcurrentSkipListMap`, and `CopyOnWriteArrayList` — plus
   arrays (including `byte[]`) and the intrinsic sequences of §3.8, never reordering or
   deduplicating them; and octet-sorts the non-deterministically-ordered types
   (`HashSet`, `HashMap`, `ConcurrentHashMap`; the concurrent insertion/FIFO collections
   `CopyOnWriteArraySet`, `ConcurrentLinkedQueue`/`ConcurrentLinkedDeque`, the blocking
   queues/deques `ArrayBlockingQueue`/`LinkedBlockingQueue`/`LinkedBlockingDeque`,
   `LinkedTransferQueue`, `DelayQueue`, `SynchronousQueue` — whose insertion/FIFO order is
   concurrency-dependent; and `PriorityQueue`/`PriorityBlockingQueue`, whose iterator
   disclaims order) into the §3.8 canonical order, never deriving order from any
   `hashCode`/`identityHashCode` or hash-bucket iteration. It **MUST** treat
   serialized byte-equality as **order-sensitive** for preserved types, and stricter
   than object `equals` for the insertion-ordered `LinkedHashSet`/`LinkedHashMap` — i.e.
   it must not assume that object `equals` implies byte-equality for those two types
   (§3.8, §7.7.2). A canonicalise collection is a **`SET OF`** (tag `0x31`) and a preserve
   collection a **`SEQUENCE OF`** (tag `0x30`); a conformant decoder **MUST reject** a
   canonicalise collection not in §11.6 ascending order, the wrong container tag, a
   duplicate in a `set:`/`orderedset:`/`map:`/`orderedmap:` field, and a count exceeding
   `maxCollection` (§3.8, §4.5, §7.6). This obligation is **live**: all six collection
   tokens, the §11.6 order check, and the `maxCollection` cap are built (branch
   `der-collection-codec`), enforced by every conformant decoder, JVM and non-JVM alike.

---

## 10. Open Questions Carried Forward

Consolidated list of every **[OPEN]** above, for the next working session:

1. §4.4 — **RATIFIED (Peter, 2026-07-06): OID-rooted** type discrimination
   (`TypedWireObject { typeId OID, body OCTET STRING }`, single discriminator per board
   H3). Root registrant = **Zeus Project Services Pty Ltd**; arc
   `zeusProjectServices → jgdms(1) → atomicDer(1) → wireTypes(1)`; `typeId`s under
   `…atomicDer.wireTypes`. **OPEN sub-item: `<PEN>` (written `999999`) is a PLACEHOLDER**
   — Zeus Project Services Pty Ltd will register an IANA PEN and it MUST be replaced
   before v1.0.
2. §5.2 — Protocol version marker value (e.g. `0x03`); collision check.
3. §7.1 — Confirm `PRINCIPAL_CTORS` allow-list stays in validation layer, not schema.
4. §7.2 — **RESOLVED (v0.12)** against `RemoteContextCodec`: ACC records are
   codebase-only (no principals and no permissions on the wire; the receiver stamps
   authenticated mTLS principals). The `verifiableDomains`/`anonCount` split is withdrawn
   — every reducing domain is transmitted inline, discriminated by `kind` — **except** the
   `jrt:/java.base` platform domain, whose exclusion is **required** (encoder drops,
   decoder refuses) and was a privilege-escalation bug until fixed in v0.12.
5. §7.3 — **RESOLVED** against `java.security.DigestCodeSource` (DirtyChai): identity
   `(uri, certificates, digestAlgorithm, digest)`, bounds match §4.5
   (`maxCerts`=100/`maxCertLen`=64 KiB/`maxDigestLen`=512), algorithm from the source
   `ALLOWED` set. **§7.3.1 verification policy RATIFIED (Peter, 2026-07-06):** the
   `transient boolean unverified` flag (accessor `unverified()`) does **NOT** travel;
   decode reconstructs by direct field-set of the transmitted digest (never
   re-compute/re-download — TOCTOU), forces the **UNVERIFIED** state, and the receiver
   verifies **only** against code already present or being loaded anyway — **never
   download-to-verify**. Authority-classified **safe as structured DER** given that
   policy (§7.5 principle). **[PROPOSED sub-item:** drop the per-cert `getType()` string
   (source constrains it to `"X.509"`, redundant).] The `httpmd:` fragment
   normalisation into an explicit `digest` field still applies.
6. §7.4 — **RESOLVED**: field lists confirmed against the `@AtomicSerial` classes in
   `au.net.zeus.jgdms.api.codebase` (`AnalysisRequest`, `JarAnalysisReport`,
   `RegistryVerdict`, `CrashReport`; **no `SignedVerdict` class** — signatures are
   inline `byte[]` fields, not a `SignedObject` wrapper). Authority-classified **safe as
   structured DER** (signature-verified / content-hash-self-verified, §7.5 principle).
   **Signature TBS RATIFIED (Peter, 2026-07-06): the canonical DER of each record's
   signed fields** (signature field excluded; per-record coverage table in §7.4),
   replacing the as-built `writeUTF`-based `canonicalBytes()`. **Clean break** — SCAP
   unreleased → no legacy verdicts, no dual-format, no signature-format versioning.
   Impl follow-up flagged (compute/verify over DER TBS, not `canonicalBytes()`) as a
   separate `jgdms-platform` code change. **[PROPOSED sub-items:** extract
   `ClassAnalysisResult` serialForm; confirm `VerdictType` enumerant values.]
7. §7.5 — **RATIFIED (Peter, 2026-07-06): `PermissionGrant`/`DigestGrant` are NOT
   structured DER** — carried as their STD-004 textual policy-grant form and re-parsed
   through the trusted policy parser (never reconstituted from serialized object state;
   authority-forgery/gadget vector). Source-corroborated: `PermissionGrant` does not
   implement `Serializable`; `DigestGrant.readObject` throws. Establishes the §7
   governing authority-vs-signature principle.
8. §7.6 — **Substituted-type set RESOLVED from source** (adds `Uuid`, `Marker` the table
   omitted). `Date` **RATIFIED** epoch-millis `INTEGER`. `Permission` **RATIFIED**
   string-form + reparse (structured form withdrawn). `MarshalledObject` **RATIFIED**
   nested `MarshalledInstanceRecord` bound to `MAX_NESTING` + §4.5 caps (**needs a
   `maxNesting` number**). `URL`/`URI`/`File`/`UID`/`Properties`/`StackTraceElement`
   **RESOLVED from source** (external-form strings; `File`→`file:` URI; `Properties`
   values always `String`). `Throwable` **[PROPOSED]** safe-subset vs the broader
   as-built reflective-construction serializer — Peter to choose field set.
   (`Float`/`Double`/`Character` RESOLVED — STD-008 §17.3.)
9. §7.7.1 — **Hash algorithm migration RATIFIED (Peter, 2026-07-06): Option B** — the
   `hashAlgorithm INTEGER (1..255)` version tag on `EntryRecord`/`EntryTemplate`
   (mandatory, no DEFAULT) makes the scheme self-describing: `1` = ATOMIC DER
   `SHA-256(DER(EntrySchemaRecord))` (32-byte), `2` = legacy RULE-7 64-bit-truncated
   `writeUTF` hash; unknown = reject; matching only within one algorithm. Algorithm
   difference is source-confirmed (`EntryClass.computeSerialEntryHash`). **Enumerant
   *numbers* [PROPOSED — confirm] (open item 21); the mechanism is ratified.**
10. §7.7.1 — Confirm whether array-valued `EntryWireField` types (e.g. `String[]`)
    are permitted. If yes, encode as ORDER-SIGNIFICANT `SEQUENCE OF` per §3.8.
11. §7.7.4 — Confirm `ServiceSpecRecord` operation set suffices for embedded device
    interface patterns. Confirm `void` return type representation in `TypeDescriptor`.
12. §7.7.5 — Confirm Jini spec attribute limit (64 entries per `ServiceItemRecord`).
    **[PROPOSED]** interface type identity is a *different* notion from `@SerialEntry`
    schema identity (an interface has no `entryForm()`/`serialForm()`); it inherits the
    §7.7.1 hash-migration decision and its width. Confirm the scheme + width against the
    Registrar's interface index before pinning 32 bytes.
13. §7.7.6 — **RESOLVED (v0.13).** The prior text conflated `Lease.ANY` (`-1`, "any
    duration acceptable") with `Lease.FOREVER` (`Long.MAX_VALUE`). Both are now
    distinct named values (`leaseAny`, `leaseForever`); `requestedDuration` admits
    both, `expiry` admits only `leaseForever`.
14. §7.7.7 — **RESOLVED (v0.13).** Signature input is the applicable
    `MulticastAnnouncementTbs`/`MulticastRequestTbs` canonical DER `SEQUENCE` (never
    raw field concatenation), and it now binds the signed-but-not-transmitted format
    name + protocol version as a domain-separation prefix (prevents cross-format
    signature reuse).
15. §7.7.7 — **MTU constraint**: measure actual P-256 SVID cert size from deployed
    SPIRE instance. If cert + principal + signature exceeds datagram budget, settle
    one of: cert-on-first-announcement-only, cert-reference-with-fetch, or minimum
    MTU requirement for `net.jini.discovery.spiffe.*`.
16. §7.7.7 — **`SpiffeCredentialManager.getTrustBundle()` — RESOLVED/CONFIRMED**:
    `public X509Certificate[] getTrustBundle()` exists at DirtyChai
    `au.zeus.jdk.authorization.spire.SpiffeCredentialManager` line 293. Method name in
    the spec is correct; no addition needed.
17. §7.7.7 — **`SvidRotationListener` — RESOLVED, with correction**: it exists
    (`SpiffeCredentialManager` line 692) but is **deliberately NOT a
    `@FunctionalInterface`** ("Bootstrap-safe: no default methods, no
    `@FunctionalInterface`", source comment). Registration is
    **`addListener(SvidRotationListener)` / `removeListener(...)`**, not
    `registerRotation*`. The spec's open-item assumption (that it is a
    `@FunctionalInterface`) is corrected here.
18. §7.7.8 — **RESOLVED**: `UnicastResponse(host, port, groups, registrar)` takes a
    pre-built `ServiceRegistrar`; **no new subtype needed** for the `JeriEndpointRecord`
    client path.
19. §7.7.8 — **RESOLVED**: stub factory path is
    `SslEndpoint.getInstance(host, port, socketFactory)` wrapped by `AtomicILFactory`
    at the JERI proxy layer (confirmed against `SslEndpoint` + the discovery `Client`s).
    **[PROPOSED sub-item:** confirm the exact `AtomicILFactory` args for the Registrar
    stub on the DER client path.]
20. Whole-document — validate every ASN.1 module against a real compiler
    (asn1c / pyasn1 / rasn). **Elevated (v0.13): this is now the next gating
    action, not an exit criterion.** External review found five modules that were
    invalid ASN.1 (X.680 distinct-tag violations on `OPTIONAL` components — §4.5);
    all are fixed in v0.13, but only compiler validation proves no others remain.
21. §4.5 — confirm the [PROPOSED] profile ceiling numbers
    (`maxFields`, `maxCollection`, `maxStackFrames`, `maxCauseDepth`,
    `maxGroups`, `maxKnownServiceIds`, `maxOperations`, `maxParameters`,
    `maxInterfaces`).

---

## 11. Class Hierarchy Evolution

The rules in §3.9 and §3.10 determine the wire impact of every class hierarchy
change. This section enumerates each case and states its consequence precisely.

The governing principle throughout: **the wire contract is owned by `@AtomicSerial`
classes only, one SEQUENCE per class, each namespace private and independently
evolved.** Non-`@AtomicSerial` classes are invisible to the wire.

### 11.1 Adding a Non-`@AtomicSerial` Subclass

A new class `Bar extends Foo` is introduced. `Bar` does not implement
`@AtomicSerial`. **Wire impact: none.** `Bar`'s state is dropped on serialisation.
Serialising a `Bar` instance produces a `Foo` on the wire. Deserialising produces a
`Foo`. `Bar` does not exist in the round-trip.

### 11.2 Removing a Non-`@AtomicSerial` Subclass

`Bar extends Foo` is removed. `Bar` did not implement `@AtomicSerial`.
**Wire impact: none.** `Bar` was invisible to the wire before removal and remains
so. No existing wire data is affected.

### 11.3 Adding a Non-`@AtomicSerial` Superclass

A new class `NewBase` is inserted above an `@AtomicSerial` class `Beta`. `NewBase`
does not implement `@AtomicSerial`. **Wire impact: none on the wire format itself.**
`Beta` remains the lowest `@AtomicSerial` class and remains responsible for
constructing `NewBase`. If `NewBase` introduces state that must survive
serialisation, `Beta`'s developer adds the corresponding fields to `Beta`'s own
`serialForm()` — in `Beta`'s namespace. This may require `Beta` to change its
`super(...)` call, but `Beta`'s SEQUENCE structure (from the wire's perspective)
simply gains new fields, subject to the OPTIONAL / DEFAULT rules for backward
compatibility.

### 11.4 Adding `@AtomicSerial` to a Previously Non-`@AtomicSerial` Superclass

`Alpha` gains `@AtomicSerial`. `Beta extends Alpha`, `Beta` already implemented
`@AtomicSerial` and was responsible for `Alpha`'s construction.

**Wire impact on existing `Beta` data: none.** `Beta`'s SEQUENCE is unchanged.
`Beta`'s `serialForm()` continues to carry whatever fields it used to reconstruct
`Alpha`. Those fields remain permanently in `Beta`'s private namespace — `Alpha`
cannot access them and need not know they exist.

**New wire behaviour during marshalling:** `Alpha` now serialises its own state to
its own private SEQUENCE. This SEQUENCE is present on the wire for new data but is
not consumed by `Beta` unless `Beta` is explicitly updated to call `super(arg)`.

**`Beta` unchanged:** If `Beta` is not updated, it continues calling
`super(alphaField)` — a regular constructor, not `Alpha(GetArg)`. `Alpha`'s
SEQUENCE on the wire is present but ignored during `Beta`'s deserialisation.
`GetArg` passed to `Alpha(GetArg)` would carry only `Alpha`'s own namespace, which
is empty for data serialised before `Alpha` gained `@AtomicSerial`; all fields
return defaults and `Alpha`'s constructor applies invariant checking.

**`Beta` updated to call `super(arg)`:** `Alpha(GetArg)` receives a `GetArg` scoped
to `Alpha`'s namespace only. It cannot see `Beta`'s namespace. Fields that `Beta`
previously carried for `Alpha` are NOT available to `Alpha`'s `(GetArg)` constructor
— they are in `Beta`'s private SEQUENCE, which `Alpha` cannot access. `Alpha`'s
constructor works from defaults for those fields and applies invariant checking.

**The Alpha and Beta namespaces are orthogonal in all cases.** A field named
`"x"` in `Beta`'s namespace and a field named `"x"` in `Alpha`'s namespace are
entirely independent entries in separate SEQUENCEs. The Alpha developer need not
know about Beta's namespace and vice versa.

### 11.5 Removing `@AtomicSerial` from a Class

`Alpha` loses `@AtomicSerial`. It no longer has a `(GetArg)` constructor or a
`serialForm()`.

**Wire impact:** `Alpha`'s SEQUENCE disappears from the wire for new data.
`Beta extends Alpha`, `Beta` is `@AtomicSerial` — `Beta` is now responsible for
`Alpha`'s construction, as described in §3.10. If `Beta` needs to preserve any of
`Alpha`'s state, `Beta`'s developer adds those fields to `Beta`'s `serialForm()`.
They are new fields in `Beta`'s namespace; OPTIONAL with defaults for backward
compat with data written when `Alpha` was `@AtomicSerial` and `Beta` did not carry
those fields.

Existing wire data that carries `Alpha`'s SEQUENCE: `Beta`'s `(GetArg)` constructor
does not consume `Alpha`'s SEQUENCE (it was never in `Beta`'s namespace). Those
bytes are present on the wire but ignored. No data loss occurs during reconstruction
— `Beta` uses its own namespace.

### 11.6 Inserting a New `@AtomicSerial` Class into the Hierarchy

A new `@AtomicSerial` class `Mid` is inserted between two existing `@AtomicSerial`
classes `Beta` (child) and `Alpha` (parent). `Mid extends Alpha`, `Beta extends Mid`.

**Wire impact:** `Mid` gains a new private SEQUENCE. Existing wire data has no
`Mid` SEQUENCE — `Mid`'s fields are absent and return defaults. `Mid`'s constructor
applies invariant checking on those defaults.

`Beta`'s SEQUENCE is unchanged. `Beta`'s responsibility for `Alpha` (if `Beta` was
constructing `Alpha` via `super(...)`) is now partially or wholly transferred to
`Mid`, depending on how `Beta` is updated. Fields `Beta` carried for `Alpha`'s
construction remain in `Beta`'s namespace permanently (§3.9); `Mid` cannot access
them. If `Mid` needs to carry fields for `Alpha`, it declares them in its own
namespace independently.

### 11.7 Removing an `@AtomicSerial` Class from the Hierarchy

`Mid` is removed. `Beta extends Mid extends Alpha` becomes `Beta extends Alpha`.

**Wire impact:** `Mid`'s SEQUENCE disappears from new data. Existing wire data
carries `Mid`'s SEQUENCE; it is not consumed by `Beta` or `Alpha` (it was never in
their namespaces). `Beta`'s constructor is updated to construct `Alpha` directly,
using fields from `Beta`'s own namespace or defaults. `Mid`'s former namespace is
simply absent going forward — no migration is possible or required.

### 11.8 Symmetric Graceful Degradation

The rules in §3.9 and §3.10 might appear restrictive. The constraint that namespaces
are private and field placement is permanent closes off several migration paths that
might seem convenient. The purpose of this section is to show that this apparent
strictness is precisely what produces safe, coordination-free evolution in both
directions.

**New class present locally, absent from the wire.**
A class appears in the local hierarchy that did not exist when the wire data was
written. Its SEQUENCE is absent from the wire. When its `(GetArg)` constructor is
called, every `arg.get(name, defaultValue)` call returns the declared default — the
class's SEQUENCE was not in the decoded data so `GetArg` has no values for its
fields. The constructor then determines whether those defaults satisfy its invariants
— replacing them with derivable valid values if possible, or throwing if not. No
external coordination is needed. The class handles its own absence through the same
invariant path it uses for any missing field.

**Class absent locally, fields present on the wire.**
A class existed when the wire data was written, but is absent from the local
hierarchy. With the correct schema (case (a) from §3.9), the decoder reads its
SEQUENCE completely and stores all field values in `GetArg`. No `(GetArg)` constructor
consumes them because the class is not present locally. Those values remain in
`GetArg` memory and become eligible for garbage collection when `GetArg` goes out
of scope after construction. No error occurs, no data belonging to other classes is
disturbed.

Both directions are handled without error, without special deserialization modes,
and without coordination between the sender and receiver.

**The wire decoder is always given the at-marshal-time schema — there is no
fallback case at the SEQUENCE level.**
The schema passed to the wire decoder — from the `MarshalledInstance` schema
embedding, from `ServiceSchemaEntry`, or from `SchemaAccessor` — is always the
schema the SEQUENCE was actually encoded with. Every byte in every SEQUENCE is
accounted for by that schema: the decoder reads exactly one TLV per schema field,
in order, and every schema field is decoded and stored in `GetArg`. Fields stored
but never requested by any constructor become eligible for GC after construction
completes. This is the only operational mode at the wire level; there is no
"older schema" degradation path here.

A payload SEQUENCE whose TLV count does not match its own schema's field count —
too few TLVs, or extra trailing TLVs — is therefore not a legitimate evolution
signal at the wire level. It means the bytes do not match the schema they claim
to be encoded with: corruption or tampering. The decoder fails closed with a
decode error rather than silently defaulting the missing fields or discarding the
extras. (An earlier revision of this draft described "remaining bytes discarded"
as an expected fallback when "the decoder's schema is older than the data"; that
scenario does not arise through the calling convention described above — the
decoder never receives any schema but the at-marshal-time one — and describing it
as expected leniency conflated wire-level integrity with the `GetArg`-level
evolution mechanism in §3.9 cases (b)/(c), which operates one layer up, on the
gap between the at-marshal-time schema and the local class's current
`serialForm()`, never on the gap between a schema and its own payload.)

**Why the strictness produces this property.**
Because each namespace is private and no class can reach into another's SEQUENCE,
the presence or absence of any given class's SEQUENCE is completely isolated from
all other classes' behaviour. A class that is absent locally cannot accidentally
consume another class's fields. A class whose wire data is present but unconsumed
cannot corrupt another class's state. The namespace walls are what make both
directions safe. If namespaces were shared or accessible across class boundaries,
neither direction could be guaranteed — a new class might accidentally consume fields
belonging to an existing class, or an absent class's unconsumed data might be
misinterpreted as belonging to a neighbour.

**The property is uniform across all granularities.**
The same symmetric behaviour that applies to whole classes applies equally to
individual fields within a single class's namespace. A field added to `serialForm()`
that is absent from old wire data — because the data predates the field — has no
entry in `GetArg`; `arg.get()` returns the declared default, and the constructor
applies invariant checking. A field present in the decoded data but not requested
by the current constructor is stored in `GetArg` and released with it when
construction completes. Adding or removing a field within a class is therefore
exactly analogous to a class appearing in or disappearing from the hierarchy: in
both cases, the party with less information receives defaults; the party with more
information stores but does not access what is not needed. The model makes no
distinction between these granularities. The rule is uniform: *absent fields return
defaults; unrequested fields are stored, not accessed, and collected after
construction.*

The strictness is the mechanism. The graceful degradation is the outcome.

### 11.9 Summary

| Change | Wire impact | Migration notes |
|---|---|---|
| Add non-`@AtomicSerial` subclass | None | No action required |
| Remove non-`@AtomicSerial` subclass | None | No action required |
| Add non-`@AtomicSerial` superclass | None to wire format; `@AtomicSerial` child may add fields at end of its SEQUENCE | New fields added at end of child's `serialForm()`; absent in old data → `GetArg` returns defaults; constructor applies invariant checking |
| Add `@AtomicSerial` to superclass | New SEQUENCE on wire; existing child SEQUENCE unchanged | Old data has no superclass SEQUENCE → all superclass fields return defaults from `GetArg`; constructor applies invariant checking |
| Remove `@AtomicSerial` from class | SEQUENCE disappears; child takes responsibility | Child adds needed fields at end of its own `serialForm()`; absent in new data → `GetArg` returns defaults |
| Insert new `@AtomicSerial` class | New SEQUENCE; existing SEQUENCEs unchanged | New class fields absent in old data → `GetArg` returns defaults; constructor applies invariant checking |
| Remove `@AtomicSerial` class | SEQUENCE disappears; neighbouring classes unaffected | No migration required; GC collects stored-but-unrequested values |
| Add field to `serialForm()` | New field at end of SEQUENCE | Absent in old data → `GetArg` returns default; constructor applies invariant checking |
| Stop requesting a field | Field still encoded; stored in `GetArg`, not accessed | Value decoded, stored in `GetArg`, released to GC after construction |

The sole migration mechanism throughout is the `GetArg` layer: absent fields return
declared defaults; unrequested fields are stored and released with `GetArg` after
construction. No `OPTIONAL` or `DEFAULT` annotations are required in the ASN.1
schema for `@AtomicSerial` objects — optionality is handled uniformly and completely
at the `GetArg` level.

**Note on `OPTIONAL` in the §7 ASN.1 modules:** `OPTIONAL` keywords appearing in
the schema definitions for `@AtomicSerial` objects throughout §7 are to be removed
in the next revision. They are not wrong — a DER decoder will handle them correctly
— but they are redundant given the `GetArg` layer. `OPTIONAL` remains appropriate
in the Jini protocol message type schemas (§7.7) where fields are genuinely absent
by protocol design rather than by version evolution (e.g., `serviceId` in
`ServiceTemplateRecord`, `superclassHash` in `EntrySchemaRecord`).

---

## 12. Schema Registry Service

The Schema Registry is a Jini service that stores and distributes
`AtomicSerialSchemaRecord` instances indexed by their SHA-256 digest. It is the
complement to the `MarshalledInstance` schema embedding: `MarshalledInstance` ensures
schema availability for any specific instance; the registry ensures schema
availability for parties who want to inspect, compare, or archive schemas
independently of any specific instance.

### 12.1 Design Constraints

- **Append-only.** Schemas are never deleted. A schema registered with a given digest
  is immutable and permanent. Old data may reference old schema digests indefinitely.
  Deletion would break the data independence guarantee of §3.11 for data that
  predates the deletion.
- **Bytes and primitives only.** All interface methods take and return only `byte[]`,
  `String`, and primitive types. No service-specific objects. This is required for
  the same reason as `CodebaseAccessor` — the registry must be callable before the
  caller has loaded any service-specific classes.
- **Authenticated, but not "owned".** Access is authenticated via SPIFFE; any
  authenticated party in the trust domain may register or look up schemas. There is
  deliberately **no** "only the owning service may register" rule (v0.12 had one):
  ownership of a class name is unverifiable from a schema record, and the store is
  content-addressed — a schema is keyed by the SHA-256 of its own bytes, so a
  registration can neither overwrite nor impersonate another schema. Registering a
  bogus record buys an attacker nothing: nothing resolves schemas by class name,
  only by digest, and the digest binds the content. (Rate/size limits against
  storage exhaustion are a deployment concern, not a trust rule.)

### 12.2 Registry Interface

```java
public interface SchemaRegistry {

    /**
     * Registers a schema. Returns the SHA-256 digest of the DER-encoded
     * AtomicSerialSchemaRecord (the schema version). Idempotent: registering
     * an already-known schema returns the existing digest.
     *
     * @param schemaRecordBytes DER-encoded AtomicSerialSchemaRecord
     * @return SHA-256(DER(AtomicSerialSchemaRecord)), 32 bytes
     */
    byte[] register(byte[] schemaRecordBytes);

    /**
     * Retrieves a schema by its digest. Returns null if not found.
     *
     * @param schemaDigest 32-byte SHA-256 digest
     * @return DER-encoded AtomicSerialSchemaRecord, or null
     */
    byte[] getSchema(byte[] schemaDigest);

    /**
     * Retrieves the full schema chain for a leaf class schema digest.
     * Returns DER-encoded AtomicSerialSchemaRecord bytes for each class
     * in the hierarchy from leaf to root, in order.
     *
     * @param leafSchemaDigest 32-byte SHA-256 digest of the leaf class schema
     * @return array of DER-encoded AtomicSerialSchemaRecord bytes, leaf first
     */
    byte[][] getSchemaChain(byte[] leafSchemaDigest);

    /**
     * Tests lossless forward compatibility over the FULL hierarchy chain:
     * can data encoded under leaf schema A be decoded under leaf schema B
     * without dropping any field A declared and without any A-declared
     * field falling back to a GetArg default?
     *
     * (Note: mere decodability is not the question — the GetArg layer makes
     * ANY two schemas "decodable" via defaults, section 11.8. This method
     * answers the stronger, useful question: is the migration lossless?)
     *
     * Chain-wise rule: for EVERY record in A's chain (leaf to root), B's
     * chain must contain a record with the same className whose ordered
     * field list starts with A's record's field list (same (wireName,
     * wireType) pairs, same order). Classes present in B but not in A are
     * permitted (their fields receive defaults). Classes present in A but
     * not in B mean A-data would be stored-but-unconsumed: not lossless,
     * returns false.
     *
     * Returns false if either digest is unknown or either chain cannot be
     * completely retrieved (fail-secure: an unjudgeable chain is not
     * reported compatible).
     *
     * @param schemaDigestA 32-byte digest of the earlier/narrower leaf schema
     * @param schemaDigestB 32-byte digest of the later/wider leaf schema
     * @return true if B is losslessly forward-compatible with A
     */
    boolean isCompatible(byte[] schemaDigestA, byte[] schemaDigestB);
}
```

### 12.3 ServiceSchemaEntry

A Jini `@SerialEntry` carried in the service's attribute set in the Lookup Service,
advertising the service's current schema. Allows any client to find the schema
through standard Jini discovery without contacting the schema registry directly.
All field types are primitive, `String`, or `byte[]` — no service-specific objects.

```java
@SerialEntry
public class ServiceSchemaEntry implements Entry {
    /** SHA-256 digest of the leaf AtomicSerialSchemaRecord. Primary key. */
    public byte[]  schemaDigest;
    /** Fully qualified name of the service API interface. */
    public String  serviceInterface;
    /** Human-readable version label (optional; informational only). */
    public String  schemaVersion;
    /** Format identifier: "JGDMS-STD-006/ATOMIC-DER". */
    public String  schemaFormat;
}
```

### 12.4 Schema Resolution Decision Tree

When a receiver needs to decode a `MarshalledInstance` payload (rewritten v0.13 —
fail-secure; the old step 4b "fall back to the local schema and accept defaults"
was a permissive fallback forbidden by design principle 6 and contradicted §7.8's
"the embedded schema is authoritative" MUST):

```
1. Decode schemaBytes from MarshalledInstanceRecord (unconditionally required)
   and cross-check the parentSchemaHash chain.
   Absent / empty / undecodable / chain-check failure → REJECT. No fallback.
2. Verify schemaDigest == SHA-256(leaf record of schemaBytes)  (§7.8, NORMATIVE).
   Mismatch → REJECT. No fallback.
3. Compute SHA-256(DER(localSerialForm())) for the local class and compare with
   the (now verified) schemaDigest.
   Match    → case (a): local and embedded schema are byte-identical; use either
              (using the local one is the allocation-free fast path).
   Mismatch → case (b)/(c): use the embedded schema; GetArg absorbs divergence.
```

The registry plays **no role at decode time**: the embedded schema is always
present in conforming data, and data whose embedded schema is missing or damaged is
non-conforming and rejected. The registry's purposes are the §12 ones — caching,
sharing, archival, and offline inspection/compatibility queries (`isCompatible`) —
not a recovery path for malformed instances. For non-JVM implementations the
embedded-schema path (step 3, mismatch arm) is the primary operational path.

### 12.5 Schema Registry as a Jini Service

The registry is discovered via the normal Jini discovery path. Its proxy follows
the SCAP pipeline and `LoadClassPermission` + `DigestGrant` exactly as any other
JGDMS service. For bootstrap scenarios (discovering the Lookup Service itself),
the `MarshalledInstance` embedded schema (§7.8) is the mechanism — the registry
is not needed and must not be assumed available.

---

| Standard | Relationship |
|---|---|
| JGDMS-STD-001 (@AtomicSerial) | STD-006 is the canonical *encoding* for `@AtomicSerial` objects; STD-001 remains the *validation* contract, unchanged. STD-001 should reference STD-006 as its default wire encoding once DRAFT is promoted. |
| JGDMS-STD-002 (SCAP) | SCAP data objects (§7.4) get ASN.1 modules here; signature input is the canonical DER of `tbs` (§7.4.1) — clean break, no cutover (SCAP unreleased). |
| JGDMS-STD-003 (Multi-Subject Identity) | The `UserSubjectBlock` (§7.1) and `AccessControlContextRecord` (§7.2) are the wire encodings of the STD-003 identity model. The `WorkerSubject` is never on the wire (ambient via ProtectionDomain); only `UserSubject` principals and the ACC domain records travel. |
| JGDMS-STD-004 (Policy File Syntax) | If grants travel the wire (§7.5), their DER form is defined here; the policy *file* syntax in STD-004 is unaffected. |
| JGDMS-STD-005 (SerialEntry Compliance) | `@SerialEntry` classes follow STD-005's validation contract. The DER encoding of `entryForm()` → `EntrySchemaRecord` (§7.7.1) is the canonical encoding for cross-runtime use. STD-005 Appendix B records this relationship and the hash migration note. `PutEntryArg`/`GetEntryArg` are encoding-neutral interfaces; the DER implementation plugs in without modifying any `@SerialEntry` class. |
| Warres (2006) SMLI TR-2006-149 | Documents the class-loading failures that motivated removal of in-stream codebase annotations (§8). Referenced as the authoritative description of the problem space. |
| `PreferredProxyCodebaseProvider` | Implements the `CodebaseAccessor.getClassAnnotation()` authenticated replacement for in-stream URL annotations. Its `VerdictRegistry` + `DigestGrant` pipeline is the mechanism that replaces codebase annotation security (§8.2). |
| `JGDMS-AGENT-CONTEXT-SpiffeDiscoveryProvider.md` | Implementation guide for the `net.jini.discovery.x500.SHA256withECDSA` and `net.jini.discovery.spiffe.SHA256withECDSA` formats (§7.7.7) and the DER unicast response (§7.7.8). Contains phase-by-phase implementation plan, known traps, and acceptance criteria. Resolves the open questions in §10 items 14–19 during implementation. |
