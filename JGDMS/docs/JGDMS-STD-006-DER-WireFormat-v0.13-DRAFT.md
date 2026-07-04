# JGDMS-STD-006: Language-Neutral DER Wire Format

**Status:** Draft (working scaffold for discussion)
**Version:** 0.13-DRAFT
**Applies to:** JGDMS, DirtyChai (JDK fork), and non-JVM JGDMS participants
**Depends on:** JGDMS-STD-001 (@AtomicSerial), JGDMS-STD-003 (Multi-Subject Identity)
**Supersedes (on completion):** the Java-serialization-based JERI wire encoding

> **Editorial note (v0.13-DRAFT):** This is a working scaffold. Sections marked
> **[OPEN]** require Peter's knowledge of the exact field layout of the current
> implementation. Sections marked **[PROPOSED]** are design suggestions for
> discussion, not settled decisions. ASN.1 modules are illustrative and not yet
> validated against an ASN.1 compiler (§4.5 makes compiler validation the next
> gating action).
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
>   the JOSS engine (2026-06, reflective-gadget removal); whether `Permission`
>   travels at all is now **[OPEN]**.
> - **§7.8 — schema-identity comment corrected**: `className` is part of
>   `AtomicSerialSchemaRecord`, so identical field lists under different class names
>   hash differently (the previous wording claimed otherwise).
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

Java Object Serialization remains the default encoding beneath `@AtomicSerial`, and
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
  in the DER wire format. `AtomicMarshalOutputStream` already defaults to
  `writeCodebaseAnnotations=false`, making the existing Java-serialization stream
  effectively annotation-free in JGDMS deployments today. The DER format formalises
  this as a permanent property of the encoding.

DER with a published ASN.1 schema inverts all four of the above: one decode path, a
language-neutral grammar with mature tooling in every serious language, a
self-describing tag-length-value structure that can be bounded before allocation, and
no in-stream annotation channel.

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
contracts. Ordering (except where intrinsic — see below), uniqueness, mutability,
sortedness, and null-handling are imposed by the constructed object *after*
validation, never asserted by the encoding. Anything that appears to require a
behavioural contract on the wire is a signal that the contract actually belongs in
`check(GetArg)` or the constructor.

This is what allows the substituted collection carriers
(`MapSerializer`/`SetSerializer`/`ListSerializer`) to collapse into a native DER
`SEQUENCE OF` with a `SIZE` bound (§7.6): the carrier's two jobs — bounding (DOS
defence) and immutability — are subsumed by the schema `SIZE` constraint and DER's
inherently read-only decoded structure. A `SortedSet` field and a `HashSet` field
both encode as `SEQUENCE OF Element`; the receiving object imposes ordering and
uniqueness during construction. The wire never needs to distinguish them.

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

The rule, stated for conformance: *order is preserved and significant where it is
intrinsic to the value; it is neither asserted nor relied upon on the wire where it
is an imposed behavioural contract; all other behaviour belongs in the constructor.*
Order-significant fields are annotated as such in their ASN.1 module (§7).

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

### 4.4 Object Identity / Type Discrimination

Each top-level wire object is wrapped in a structure carrying an explicit type
identifier (OID or closed `ENUMERATED`) so a decoder selects the correct schema
without inferring it from context. **[OPEN: decide OID-rooted vs enumerated. An OID
arc under a JGDMS-controlled root is more extensible and self-describing; an
ENUMERATED is more compact. Recommendation leans OID for forward-compatibility.]**

### 4.5 Module Validity: Distinct-Tag Rule, No DEFAULT, Profile Size Ceilings

**Distinct-tag rule (NORMATIVE).** Every `OPTIONAL` component in a `SEQUENCE` MUST
carry a tag distinct from the tag of every component that can immediately follow it
(X.680; a module violating this is not valid ASN.1 and standard tooling rejects it).
Where a collision would otherwise arise, the `OPTIONAL` component carries an
`IMPLICIT` context tag (`[0]`, `[1]`, …). Ad-hoc positional disambiguation — e.g.
counting how many same-tagged TLVs remain in the enclosing `SEQUENCE` — is
**forbidden**: it is undecodable by schema-driven tooling and defeats the
language-neutrality goal. (v0.13 fixed violations in §7.3, §7.6 `Permission`,
§7.7.5, §7.7.7, and — by field deletion — §7.8.)

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
them identically, rejecting before allocation. §7.2's `MAX_DOMAINS`/`MAX_CERTS`/
`MAX_DIGEST_LEN`/`MAX_CERT_LEN` established the pattern.

| Constant | Value | Applies to |
|---|---|---|
| `MAX-FIELDS` | 65535 | `AtomicSerialSchemaRecord.fields`, `EntrySchemaRecord.fields`, `EntryRecord.fieldValues`, `EntryTemplate.fieldValues` |
| `MAX-COLLECTION` | 65536 | §7.6 `CollectionField` / `MapField` elements (per-type schemas MAY declare tighter bounds) |
| `MAX-STACK-FRAMES` | 2048 | `ThrowableRecord.stackTrace` |
| `MAX-CAUSE-DEPTH` | 64 | `ThrowableRecord.cause` nesting (closes part of open item on §7.6) |
| `MAX-GROUPS` | 128 | discovery `groups` sequences (§7.7.7, §7.7.8) |
| `MAX-KNOWN-SERVICE-IDS` | 256 | `MulticastRequestRecord.knownServiceIds` (datagram-bounded anyway) |
| `MAX-OPERATIONS` | 1024 | `ServiceSpecRecord.operations` |
| `MAX-PARAMETERS` | 255 | `OperationDescriptor.parameters` |
| `MAX-INTERFACES` | 64 | `ServiceTemplateRecord.requiredInterfaces` |

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

- `MarshallingFormat.DER` — identifier `"JGDMS-STD-006/DER"`.
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
`MarshallingFormat.DER` carries the same fail-before-transmission guarantee already given
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
  JOSS unless it *requires* `MarshallingFormat.DER`, in which case the call correctly fails
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

Requiring `MarshallingFormat.DER` on an endpoint is the **firewall**: it fail-fast rejects
any call that would otherwise ride JOSS, so a security-sensitive endpoint is never silently
downgraded. On a transport without confidentiality+integrity, DER-requiring endpoints MUST
require `MarshallingFormat.DER` (no negotiable fallback), since a plaintext format selection
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
| 6.5 | `DigestCodeSourceRecord` | Code identity | `DigestCodeSource.java` | **[OPEN]** §7.3 |
| 6.6 | `AnalysisRequest` | SCAP | STD-002 | **[OPEN]** §7.4 |
| 6.7 | `JarAnalysisReport` | SCAP | STD-002 | **[OPEN]** §7.4 |
| 6.8 | `SignedVerdict` | SCAP | STD-002 | **[OPEN]** §7.4 |
| 6.9 | `RegistryVerdict` | SCAP | STD-002 | **[OPEN]** §7.4 |
| 6.10 | `CrashReport` | SCAP | STD-002 | **[OPEN]** §7.4 |
| 6.11 | `PermissionGrant` / `DigestGrant` | Policy | STD-004 (policy syntax) | **[OPEN]** §7.5 |
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
ReducingDomainRecord ::= CHOICE {
    -- A genuinely null-CodeSource domain at the sender (dynamic proxy, lambda,
    -- bootstrap). It is a real reducer; dropping it would ELEVATE authority.
    -- Reconstructed codebase-less + principal-bearing -> matches principal-only grants.
    nullCs      [0] NULL,

    -- A DirtyChai java.security.DigestCodeSource: self-describing codebase identity
    -- pinned by content hash. Reconstructed verbatim; the digest is re-verified later,
    -- at policy/class-load time via DigestGrant -- NOT in the codec.
    digest      [1] DigestCodeSourceRecord,   -- see §7.3

    -- Any other CodeSource that carries a location URL.
    url         [2] UrlCodeSourceRecord
}

UrlCodeSourceRecord ::= SEQUENCE {
    locationUri     UTF8String,                                    -- RFC 3986; locator
    certificates    SEQUENCE SIZE(0..100) OF OCTET STRING OPTIONAL -- MAX_CERTS; each = X509Certificate.getEncoded(), opaque & verbatim (§3.8), SIZE(1..65536)
}

AccessControlContextRecord ::= SEQUENCE {
    -- ORDER-SIGNIFICANT (§3.8): captured domain order is preserved end to end.
    -- EVERY reducing domain is transmitted inline; there is no verifiable/anonymous
    -- split and no separate count of suppressed domains.  The ONE exclusion is the
    -- platform jrt:/java.base module domain (see the java.base security note below):
    -- the encoder MUST drop it and the decoder MUST refuse to reconstruct it.
    domains     SEQUENCE SIZE(0..4096) OF ReducingDomainRecord     -- MAX_DOMAINS
}

DigestValue ::= SEQUENCE {
    algorithm   AlgorithmIdentifier,
    digest      OCTET STRING
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
- **DoS bounds are part of the contract:** `MAX_DOMAINS = 4096`, `MAX_CERTS = 100`,
  `MAX_DIGEST_LEN = 512`, `MAX_CERT_LEN = 65536`. A non-JVM decoder MUST enforce the
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

### 7.3 Code Identity: DigestCodeSourceRecord  **[OPEN]**

```asn1
DigestCodeSourceRecord ::= SEQUENCE {
    locationUri     UTF8String,          -- RFC 3986; locator, not trust anchor
    -- [0] IMPLICIT context tag (§4.5 distinct-tag rule): untagged, this SEQUENCE OF
    -- would collide with the DigestValue SEQUENCE that follows (both tag 0x30).
    certificates    [0] IMPLICIT SEQUENCE SIZE(0..100) OF OCTET STRING OPTIONAL,  -- MAX_CERT_COUNT; each = X509Certificate.getEncoded(), opaque & verbatim (§3.8), SIZE(1..65536)
    digest          DigestValue
    -- equality/identity is (uri, certs, algorithm, digestBytes) per DigestCodeSource
    -- a plain CodeSource (no digest) is a DISTINCT identity and MUST NOT be
    -- representable as a DigestCodeSourceRecord with an absent digest.
}
```

**[OPEN]** Confirm against `DigestCodeSource.java`:
- Certificate encoding (**NORMATIVE**): each certificate is an opaque `OCTET STRING`
  holding the exact bytes of `X509Certificate.getEncoded()`, preserved verbatim and
  never parsed-and-re-encoded (§3.8 opaque-octet carve-out). The earlier
  `OF Certificate` (inline X.509 type) form is **withdrawn**: it would invite a
  conforming decoder to parse and re-encode the certificate, and a non-strict-DER
  certificate would then fail its own digest. Order within the `SEQUENCE` is the
  certificate-path order and is order-significant (§3.8) — never sorted or deduplicated.
- The DOS bounds (MAX_CERT_COUNT=100, MAX_CERT_BYTES=64KiB, MAX_DIGEST_BYTES=512)
  become schema `SIZE` constraints so a non-JVM decoder enforces them identically.
- Whether the `httpmd:` URL form is represented as `locationUri` with the digest in
  the fragment, or normalised into the explicit `digest` field. **Recommendation:**
  normalise into the explicit field; the `httpmd:` fragment was an in-band trick
  precisely because Java serialization had no explicit slot — DER does.

### 7.4 SCAP Data Objects  **[OPEN — needs STD-002 field-level detail]**

`AnalysisRequest`, `JarAnalysisReport`, `SignedVerdict`, `RegistryVerdict`,
`CrashReport`. These are signature-bearing (except `AnalysisRequest`), which is
exactly why DER (canonical) rather than BER is mandatory — the signed octets must be
reproducible across implementations.

```asn1
-- Illustrative skeleton only; field lists from STD-002 must be filled in.
AnalysisRequest ::= SEQUENCE {
    packedJarBytes  OCTET STRING,        -- Pack200-compressed; [OPEN] size bound
    contentHash     DigestValue,         -- SHA-256 of RAW bytes
    originalUri     UTF8String,          -- traceability only
    maxBfsDepth     INTEGER (0..MAX)
}

SignedObject ::= SEQUENCE {
    tbs         OCTET STRING,            -- the DER-encoded to-be-signed content
    signature   SEQUENCE {
        algorithm   AlgorithmIdentifier,
        value       OCTET STRING
    }
}
-- RegistryVerdict, JarAnalysisReport, SignedVerdict, CrashReport each wrap their
-- payload as the `tbs` of a SignedObject. The signer (engine key / Host-3 key /
-- Phoenix key) and the verification rules are unchanged from STD-002.
```

The *signature input* must be the canonical DER of the `tbs`, computed identically on
signer and verifier — specified normatively in §7.4.1 below. Under Java serialization
the signed bytes were the Java-serialized form; under DER they are the canonical DER of
the `tbs`. **SCAP is unreleased, so this is a clean break, not a compatibility
problem:** there are no deployed Java-serialized verdicts that must remain verifiable,
so no dual-format cutover and no signature-format versioning are required — the DER
signature input is simply *the* format from first release. (The verdict cache, keyed by
the JAR content hash per STD-002, is independent of verdict encoding and is unaffected
regardless.)

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

### 7.5 Policy: PermissionGrant / DigestGrant  **[OPEN]**

Needed only if grants travel the wire (they do, via `RemotePolicyProvider`
`replace()` / push updates, and `DynamicPolicyProvider` `Security.grant()`).
Field layout from STD-004. **[OPEN.]**

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

```asn1
-- behavioural collection (Set/Map/List): order NOT relied upon by receiver
CollectionField ::= SEQUENCE SIZE(0..MAX-COLLECTION) OF Element   -- §4.5
MapField        ::= SEQUENCE SIZE(0..MAX-COLLECTION) OF SEQUENCE { key Element, value Element }   -- §4.5
-- the receiving object imposes ordering/uniqueness/null-policy at construction (§3.8)
```

(An *array* or *linked list* field is also a `SEQUENCE OF`, but ORDER-SIGNIFICANT per
§3.8 — the receiver relies on wire order.)

The irreducible substituted types requiring their own DER form:

| Type | Current serializer | Proposed DER form | Status |
|---|---|---|---|
| `Byte`/`Short`/`Integer`/`Long` | boxed-primitive serializers | `INTEGER` (value-bounded) | [PROPOSED] |
| `Float`/`Double` | boxed-primitive serializers | `OCTET STRING` of IEEE-754 bits (4/8 bytes, big-endian), strict-canonical | **[RESOLVED — STD-008 §17.3.1]** |
| `Character` | `CharSerializer` | `INTEGER` (Unicode codepoint; BMP non-surrogate) | **[RESOLVED — STD-008 §17.3.2]** |
| `Boolean` | `BooleanSerializer` | `BOOLEAN` | [PROPOSED] |
| `Properties` | `PropertiesSerializer` | `SEQUENCE OF SEQUENCE { key UTF8String, value UTF8String }` (behavioural; unordered) | **[OPEN: confirm Properties values are always String]** |
| `URL` | `URLSerializer` | `UTF8String` (RFC 3986; locator, no DNS) | **[OPEN: URL vs URI normalisation]** |
| `URI` | `URISerializer` | `UTF8String` (RFC 3986) | [PROPOSED] |
| `UID` (`java.rmi.server.UID`) | `UIDSerializer` | `SEQUENCE { unique INTEGER, time INTEGER, count INTEGER }` | **[OPEN: confirm field set]** |
| `File` | `FileSerializer` | `UTF8String` path **[OPEN: platform path semantics — is File even sent across runtimes? May be JVM-internal only]** | **[OPEN]** |
| `MarshalledObject` | `MarshalledObjectSerializer` | nested frame around a `MarshalledInstanceRecord` (§7.8) — NO codebase annotation (§8.3; the v0.12 sketch's `codebaseAnnotation` slot is withdrawn with §7.8's) | **[OPEN: this is itself a serialized-object container — define carefully; it nests the wire format inside itself and needs explicit depth/size bounds]** |
| `StackTraceElement` | `StackTraceElementSerializer` | `SEQUENCE { declaringClass UTF8String, methodName UTF8String, fileName UTF8String OPTIONAL, lineNumber INTEGER }` | [PROPOSED] |
| `X500Principal` | `X500PrincipalSerializer` | `OCTET STRING` = `X500Principal.getEncoded()` — opaque & verbatim (§3.8), never re-encoded | **[RESOLVED — opaque octets; see note below]** |
| `Date` | `DateSerializer` | `INTEGER` epoch-millis | **[OPEN: epoch-millis INTEGER vs GeneralizedTime — recommend epoch-millis for exact round-trip and no timezone ambiguity]** |
| `Permission` | *(none — `PermissionSerializer` DELETED 2026-06: reflective-gadget removal, JOSS engine audit)* | `SEQUENCE { className UTF8String, name [0] IMPLICIT UTF8String OPTIONAL, actions [1] IMPLICIT UTF8String OPTIONAL }` (context tags per §4.5: a name-only encoding must be distinguishable from an actions-only one) | **[OPEN: PermissionSerializer no longer exists — confirm whether `Permission` travels the DER wire at all before defining this form]** |
| `Throwable` | `ThrowableSerializer` | see below | **[OPEN]** |
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

**`Throwable` (security-sensitive).** `Throwable` is a classic gadget vector under
ordinary Java serialization; routing it through `ThrowableSerializer` rather than
default serialization is a deliberate containment. Its DER form should carry only the
safe, bounded shape, and the cause chain is acyclic (§3.7) — a `Throwable` whose cause
chain contained a cycle would be rejected:

```asn1
ThrowableRecord ::= SEQUENCE {
    className     UTF8String,
    message       UTF8String OPTIONAL,
    -- ORDER-SIGNIFICANT (§3.8): stack frames are top-of-stack first
    stackTrace    SEQUENCE SIZE(0..MAX-STACK-FRAMES) OF StackTraceElement,  -- §4.5
    cause         ThrowableRecord OPTIONAL   -- acyclic; nesting <= MAX-CAUSE-DEPTH (§4.5)
}
```

**[OPEN] for §7.6 as a whole:**
- Confirm the *complete* substituted-type set against the live `serializers` map and
  the `instanceof` fallbacks in `defaultReplaceObject` (the table above is taken from
  `AtomicMarshalOutputStream` as supplied; verify nothing has been added since).
- `Float`/`Double`: settle REAL vs IEEE-754-bits (recommend bits).
- `Date`: settle epoch-millis vs `GeneralizedTime` (recommend epoch-millis).
- `MarshalledObject`: define the nested-frame structure; it embeds the wire format
  recursively and needs explicit depth/size bounds.
- `Throwable`: bounds RESOLVED in §4.5 (`MAX-STACK-FRAMES` = 2048,
  `MAX-CAUSE-DEPTH` = 64) — confirm the numbers.
- `File`: determine whether `File` is ever transmitted cross-runtime or is
  JVM-internal only; if cross-runtime, define platform-neutral path semantics.

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
    fields          SEQUENCE (SIZE(1..MAX-FIELDS)) OF EntryWireFieldDef   -- §4.5
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

**[OPEN] Hash algorithm migration.** STD-005 RULE-7 currently uses a different
algorithm: `SHA-256` over `superclassHash_64bit || className_utf8 || field bytes`,
where `superclassHash_64bit` is a legacy 64-bit Jini hash. The DER-format
`SHA-256(DER(EntrySchemaRecord))` is a breaking change to type identity for existing
Registrars. Three migration options:

| Option | Mechanism | Impact |
|---|---|---|
| A | Registrar stores both hash forms during transition | No client changes; Registrar complexity increases |
| B | `EntryRecord` carries a 1-byte hash algorithm version tag | Clean versioning; requires client and Registrar changes |
| C | DER Registrar only (new deployment); legacy Registrar retained for existing entries | No migration; two Registrar types coexist |

**Recommendation: Option B.** A version tag is the cleanest long-term solution and
makes the algorithm explicit in the wire format. **Confirm before implementation.**

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
    hashAlgorithm   INTEGER (1..255),               -- 1 = SHA-256(DER(EntrySchemaRecord)); [OPEN: version tag per §7.7.1]
    schemaHash      OCTET STRING (SIZE(32)),        -- identifies the @SerialEntry class
    -- ORDER-SIGNIFICANT (§3.8): fieldValues[i] corresponds to fields[i]
    -- in the EntrySchemaRecord identified by schemaHash.
    fieldValues     SEQUENCE (SIZE(1..MAX-FIELDS)) OF EntryFieldValue   -- §4.5
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
    parameters  SEQUENCE (SIZE(0..MAX-PARAMETERS)) OF TypeDescriptor,   -- §4.5
    returnType  TypeDescriptor
}

ServiceSpecRecord ::= SEQUENCE {
    -- Desired Java interface name; proxy factory generates this interface.
    interfaceName  UTF8String (SIZE(1..1024)),
    -- ORDER-SIGNIFICANT (§3.8): operation order determines generated interface layout.
    operations     SEQUENCE (SIZE(1..MAX-OPERATIONS)) OF OperationDescriptor,   -- §4.5
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
    attributes  SEQUENCE (SIZE(0..64)) OF EntryRecord,  -- 64 = Jini spec attribute limit
    proxy       ProxyDescriptor
}

EntryTemplate ::= SEQUENCE {
    hashAlgorithm INTEGER (1..255),               -- MANDATORY, no DEFAULT (§4.5); must match algorithm in stored EntryRecord
    schemaHash    OCTET STRING (SIZE(32)),        -- identifies the @SerialEntry class to match
    -- ORDER-SIGNIFICANT (§3.8): fieldValues[i] matches fields[i].
    -- absent = wildcard (matches any value including null).
    -- present = must byte-match the stored EntryRecord fieldValues[i].
    fieldValues   SEQUENCE (SIZE(1..MAX-FIELDS)) OF EntryFieldValue   -- §4.5
}

-- Context tags per §4.5 distinct-tag rule: requiredInterfaces and
-- attributeTemplates are both SEQUENCE OF (tag 0x30) and both OPTIONAL --
-- untagged they are indistinguishable when only one is present.
ServiceTemplateRecord ::= SEQUENCE {
    serviceId           [0] IMPLICIT ServiceID OPTIONAL,
    -- Interface hashes: schemaHash of each required service interface.
    -- [OPEN] Confirm whether interface type identity uses the same hash scheme.
    requiredInterfaces  [1] IMPLICIT SEQUENCE (SIZE(0..MAX-INTERFACES)) OF OCTET STRING (SIZE(32)) OPTIONAL,   -- §4.5
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
LeaseForever INTEGER ::= 9223372036854775807
LeaseAny     INTEGER ::= -1

LeaseRecord ::= SEQUENCE {
    leaseId     OCTET STRING (SIZE(16)),   -- UUID
    grantorId   ServiceID,
    -- Absolute expiry in epoch-millis.  LeaseForever = no expiry.
    -- LeaseAny is NOT valid here: a granted lease always has a definite expiry
    -- or LeaseForever.
    expiry      INTEGER,
    renewable   BOOLEAN                    -- MANDATORY, no DEFAULT (§4.5)
}

LeaseRenewalRecord ::= SEQUENCE {
    leaseId           OCTET STRING (SIZE(16)),
    -- Requested additional duration in milliseconds (positive), or one of the
    -- named values: LeaseAny (grantor picks), LeaseForever (request no expiry).
    requestedDuration INTEGER
}

LeaseCancellationRecord ::= SEQUENCE {
    leaseId OCTET STRING (SIZE(16))
}

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
bundle (`X509Certificate[]` from `SpiffeCredentialManager.getTrustBundle()`). This
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
    groups          SEQUENCE (SIZE(0..MAX-GROUPS)) OF UTF8String,   -- §4.5
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
    groups          SEQUENCE (SIZE(0..MAX-GROUPS)) OF UTF8String,           -- §4.5
    -- ORDER-SIGNIFICANT (§3.8): ServiceIDs already known to the client.
    -- A Registrar whose ServiceID appears here need not respond.
    knownServiceIds SEQUENCE (SIZE(0..MAX-KNOWN-SERVICE-IDS)) OF ServiceID, -- §4.5
    signerPrincipal UTF8String,
    signerCert      [0] IMPLICIT OCTET STRING OPTIONAL,   -- §4.5 distinct-tag rule
    signature       OCTET STRING
}
```

**TBS (to-be-signed) content (NORMATIVE).** The signature covers one canonical DER
`SEQUENCE` — **not** the concatenated raw DER octets of each field individually —
constructed as:

```asn1
MulticastTbs ::= SEQUENCE {
    -- Domain-separation prefix: SIGNED BUT NOT TRANSMITTED.  Both values are
    -- already known to the verifier from the discovery packet header (the
    -- protocol-2 format-ID negotiation), so transmitting them would be
    -- redundant; binding them into the TBS prevents a signature produced under
    -- one format/version from verifying under another (cross-format /
    -- cross-protocol signature reuse).
    formatName      UTF8String,     -- e.g. "net.jini.discovery.spiffe.SHA256withECDSA"
    protocolVersion INTEGER,        -- discovery protocol version (currently 2)
    -- ...followed by all fields of the record preceding `signature`, in the
    -- order declared (including signerCert when present).
}
```

The signer computes that `SEQUENCE` once and signs its octets; the verifier
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
    groups      SEQUENCE (SIZE(0..MAX-GROUPS)) OF UTF8String,   -- §4.5
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

**[OPEN]** `UnicastResponse` constructor for DER client side: confirm whether
`UnicastResponse` can be constructed from an already-built `ServiceRegistrar`
proxy stub (for the `JeriEndpointRecord` path where the client constructs a JERI
stub directly), or whether a new subtype is required.

**[OPEN]** `JeriEndpointRecord` → JERI stub construction on the client: confirm
the correct factory path in JGDMS for constructing an `SslEndpoint` +
`AtomicILFactory` stub from `(host, port, spiffeId)` without a prior unicast
handshake.
```

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
    fields           SEQUENCE (SIZE(0..MAX-FIELDS)) OF AtomicSerialFieldDef   -- §4.5
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
    payloadFormat      UTF8String     -- "JGDMS-STD-006/DER"

    -- NOTE (v0.13): the former `codebaseAnnotation UTF8String OPTIONAL` field is
    -- REMOVED.  It contradicted §8.3 (the DER format carries no codebase
    -- annotation at any level), reintroduced the §8.1 stale-URL hazards, and made
    -- this module invalid ASN.1 (§4.5 distinct-tag rule: two adjacent UTF8Strings,
    -- one OPTIONAL).  The authenticated `CodebaseAccessor` channel (§8.2) is the
    -- ONLY codebase mechanism; a decoder MUST reject a record containing a
    -- trailing UTF8String after payloadFormat.
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
`parentSchemaHash` chain cross-check, or does not match `schemaDigest`, the record
MUST be **rejected** — no fallback to a local or registry schema (§12.4).

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

Codebase annotations **do not appear** in the DER wire format. This section
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

### 8.3 Implications for the DER Wire Format

The DER object stream carries **no codebase annotation field** at any level —
not per-object, not per-frame, not as a stream header. There is no frame-level
codebase table, no URL annotation alongside class descriptors, no concept equivalent
to `java.rmi.server.codebase`. (Until v0.13, `MarshalledInstanceRecord` §7.8 carried
an optional per-object "backup URL" annotation that contradicted this section; it
has been removed. This statement is now true without exception, and a conforming
decoder rejects a record carrying the legacy field.)

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
   deduplicate, or canonicalise them. For behavioural collections, the wire order is
   neither asserted nor relied upon; the constructed object imposes ordering,
   uniqueness, and null-policy after validation.
6. Preserves `SEQUENCE OF` ordering where ordering is semantically required (§7.1
   Subject order and principal-chain order, ACC domain order, §7.6 stack-trace order).
7. **Carries externally-produced DER structures — X.509 `Certificate`, X.501 `Name`
   (`X500Principal`), nested signatures — as opaque `OCTET STRING` octets captured
   verbatim from `getEncoded()`, and never parses, re-encodes, sorts, or
   re-canonicalises them (§3.8 opaque-octet carve-out).** A conformance suite MUST
   demonstrate byte-for-byte survival of a non-strict-DER ("BER-ish") certificate and
   of a `Name` bearing a `TeletexString` attribute value.
8. Computes signatures over the canonical DER of the `tbs` (§7.4.1), and over the
   `MulticastTbs` `SEQUENCE` — which binds the signed-but-not-transmitted format
   name and protocol version ahead of the record fields — for record-level
   signatures (§7.7.7), verifying against the received octets without re-encoding
   them.
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

---

## 10. Open Questions Carried Forward

Consolidated list of every **[OPEN]** above, for the next working session:

1. §4.4 — OID-rooted vs `ENUMERATED` type discrimination (recommendation: OID).
2. §5.2 — Protocol version marker value (e.g. `0x03`); collision check.
3. §7.1 — Confirm `PRINCIPAL_CTORS` allow-list stays in validation layer, not schema.
4. §7.2 — **RESOLVED (v0.12)** against `RemoteContextCodec`: ACC records are
   codebase-only (no principals and no permissions on the wire; the receiver stamps
   authenticated mTLS principals). The `verifiableDomains`/`anonCount` split is withdrawn
   — every reducing domain is transmitted inline, discriminated by `kind` — **except** the
   `jrt:/java.base` platform domain, whose exclusion is **required** (encoder drops,
   decoder refuses) and was a privilege-escalation bug until fixed in v0.12.
5. §7.3 — `DigestCodeSource` field confirmation (certificate encoding, DOS bounds as
   schema constraints). Note: `DigestCodeSourceRecord` represents ACC *domain
   identity* only — not a stream annotation and carries no URL-locator role.
   The `httpmd:` fragment normalisation into an explicit `digest` field still applies.
6. §7.4 — Full SCAP object field lists from STD-002. (Signature-input form RESOLVED in
   §7.4.1: canonical DER of `tbs`. No cutover or signature-format versioning needed —
   SCAP is unreleased, so the DER signature input is the format from first release.)
7. §7.5 — Whether/how `PermissionGrant`/`DigestGrant` travel the wire; STD-004
   field layout.
8. §7.6 — Confirm the complete substituted-type set against the live `serializers`
   map and `defaultReplaceObject` fallbacks; settle `Date` (epoch-millis vs
   GeneralizedTime), `MarshalledObject` nested-frame structure and bounds, `File`
   cross-runtime applicability; confirm whether `Permission` travels at all
   (`PermissionSerializer` deleted 2026-06).
   (`Float`/`Double`/`Character` RESOLVED — STD-008 §17.3. `Throwable` bounds
   RESOLVED v0.13 — §4.5 `MAX-STACK-FRAMES`/`MAX-CAUSE-DEPTH`, numbers to confirm.)
9. §7.7.1 — **Hash algorithm migration** (most consequential): settle Option A/B/C
   for coexistence of RULE-7 legacy hash with DER `SHA-256(DER(...))`. Recommendation:
   Option B (version tag in `EntryRecord`). Confirm before implementation.
10. §7.7.1 — Confirm whether array-valued `EntryWireField` types (e.g. `String[]`)
    are permitted. If yes, encode as ORDER-SIGNIFICANT `SEQUENCE OF` per §3.8.
11. §7.7.4 — Confirm `ServiceSpecRecord` operation set suffices for embedded device
    interface patterns. Confirm `void` return type representation in `TypeDescriptor`.
12. §7.7.5 — Confirm Jini spec attribute limit (64 entries per `ServiceItemRecord`).
    Confirm whether interface type identity uses `EntrySchemaRecord` hash scheme.
13. §7.7.6 — **RESOLVED (v0.13).** The prior text conflated `Lease.ANY` (`-1`, "any
    duration acceptable") with `Lease.FOREVER` (`Long.MAX_VALUE`). Both are now
    distinct named values (`LeaseAny`, `LeaseForever`); `requestedDuration` admits
    both, `expiry` admits only `LeaseForever`.
14. §7.7.7 — **RESOLVED (v0.13).** Signature input is the `MulticastTbs` canonical
    DER `SEQUENCE` (never raw field concatenation), and it now binds the
    signed-but-not-transmitted format name + protocol version as a
    domain-separation prefix (prevents cross-format signature reuse).
15. §7.7.7 — **MTU constraint**: measure actual P-256 SVID cert size from deployed
    SPIRE instance. If cert + principal + signature exceeds datagram budget, settle
    one of: cert-on-first-announcement-only, cert-reference-with-fetch, or minimum
    MTU requirement for `net.jini.discovery.spiffe.*`.
16. §7.7.7 — **`SpiffeCredentialManager.getTrustBundle()`**: confirm the exact method
    name exposing the `X509Certificate[]` trust bundle. Add this method to
    `SpiffeCredentialManager` if it does not exist.
17. §7.7.7 — **`SvidRotationListener`**: confirm it is a `@FunctionalInterface` and
    the registration method name on `SpiffeCredentialManager`.
18. §7.7.8 — `UnicastResponse` construction from a pre-built proxy stub; confirm
    whether a new subtype is needed for the `JeriEndpointRecord` client path.
19. §7.7.8 — `JeriEndpointRecord` → JERI stub factory path on the client side
    (constructing `SslEndpoint` + `AtomicILFactory` from `host:port:spiffeId`).
20. Whole-document — validate every ASN.1 module against a real compiler
    (asn1c / pyasn1 / rasn). **Elevated (v0.13): this is now the next gating
    action, not an exit criterion.** External review found five modules that were
    invalid ASN.1 (X.680 distinct-tag violations on `OPTIONAL` components — §4.5);
    all are fixed in v0.13, but only compiler validation proves no others remain.
21. §4.5 — confirm the [PROPOSED] profile ceiling numbers
    (`MAX-FIELDS`, `MAX-COLLECTION`, `MAX-STACK-FRAMES`, `MAX-CAUSE-DEPTH`,
    `MAX-GROUPS`, `MAX-KNOWN-SERVICE-IDS`, `MAX-OPERATIONS`, `MAX-PARAMETERS`,
    `MAX-INTERFACES`).

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

**Primary case vs. fallback case.**
When the at-marshal-time schema is available — from the `MarshalledInstance`
schema embedding, from `ServiceSchemaEntry`, or from `SchemaAccessor` — every
byte in every SEQUENCE is accounted for by the schema. All fields are decoded and
stored in `GetArg`. None are discarded at the SEQUENCE level. Fields stored but
never requested by any constructor become eligible for GC after construction
completes. This is the primary operational mode.

The "remaining bytes discarded" path described in §3.9 case (c) is the fallback:
it arises only when the decoder's schema is older than the data. When the correct
schema is used this path is never reached. The `MarshalledInstance` schema embedding
exists precisely to ensure the correct schema is always available for stored data,
eliminating the fallback case for the most important category — long-lived persisted
or transmitted objects.

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
    /** Format identifier: "JGDMS-STD-006/DER". */
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
