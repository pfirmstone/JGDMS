# JGDMS-STD-006: Language-Neutral DER Wire Format

**Status:** Draft (working scaffold for discussion)
**Version:** 0.2-DRAFT
**Applies to:** JGDMS, DirtyChai (JDK fork), and non-JVM JGDMS participants
**Depends on:** JGDMS-STD-001 (@AtomicSerial), JGDMS-STD-003 (Multi-Subject Identity)
**Supersedes (on completion):** the Java-serialization-based JERI wire encoding

> **Editorial note (v0.2-DRAFT):** This is a working scaffold. Sections marked
> **[OPEN]** require Peter's knowledge of the exact field layout of the current
> implementation. Sections marked **[PROPOSED]** are design suggestions for
> discussion, not settled decisions. ASN.1 modules are illustrative and not yet
> validated against an ASN.1 compiler.
>
> **Changes in v0.2 (from v0.1):**
> - New §3.7 — Acyclic Object Graph Requirement (documented security contract
>   inherited from `AtomicMarshalInputStream`).
> - New §3.8 — "Values and bounds, never behaviour" design rule, with the
>   intrinsic-order carve-out (arrays, principal chains, certificate paths).
> - Rewrote §7.1 and §7.2 to annotate `SEQUENCE OF` fields as **order-significant**
>   where order is intrinsic (principal chains).
> - New §7.6 — Substituted Standard Types catalogue. Collection carriers
>   (`MapSerializer`/`SetSerializer`/`ListSerializer`) collapse into native
>   `SEQUENCE OF` and are **removed** as distinct wire types; the catalogue lists
>   only the irreducible substituted types.
> - Revised §8 (frame-level codebase table as the sole permitted reference-like
>   construct, with strict pre-decode-and-validate ordering) and §9 (conformance:
>   reject cycles/back-references; preserve order-significant sequences).
> - Source basis added: `AtomicMarshalInputStream.java`,
>   `AtomicMarshalOutputStream.java`.

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

DER with a published ASN.1 schema inverts all three: one decode path, a
language-neutral grammar with mature tooling in every serious language, and a
self-describing tag-length-value structure that can be bounded before allocation.

### 2.2 Relationship to the Cross-Runtime Goal

STD-003 establishes that JGDMS's security properties (multi-principal authorization,
ACC propagation, content-addressed identity) are *protocol* concepts whose current
*implementation* happens to assume a Java deserializer on the receiving end. This
standard removes that assumption. Once a non-JVM participant can decode the wire
types defined here, the path to first-class JERI participation for non-JVM runtimes
(e.g. AI inference services) reduces to a conventional protocol-implementation task:
a DER codec against this schema, plus the TLS/SPIFFE handshake, plus the framing
defined in §6–§8.

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

---

## 4. Encoding Conventions

### 4.1 Base Encoding

All wire objects are encoded using ASN.1 **DER** (X.690). Integers are encoded as
ASN.1 `INTEGER`; the legacy big-endian fixed-width counters in the current format
(e.g. `subjectCount : u16`, `httpmdCount : 4B BE`) are replaced by `INTEGER` with a
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

---

## 5. Version Negotiation and Coexistence

### 5.1 Wire Format as a Method Constraint **[PROPOSED]**

DER and legacy Java-serialization encodings coexist during migration. The selected
encoding is expressed through the existing JERI `MethodConstraints` mechanism, the
same way `AtomicInputValidation.YES`, `Confidentiality.YES`, etc. are expressed
today. A proposed constraint:

```
WireFormat.DER        — this endpoint requires DER encoding
WireFormat.JAVA       — legacy Java serialization (default during transition)
WireFormat.ANY        — negotiable; highest mutually-supported wins
```

Because constraints are enforced *before bytes leave the client*
(`UnsupportedConstraintException` is thrown at constraint resolution time), an
endpoint can require `WireFormat.DER` and have that requirement enforced with the
same fail-before-transmission property already guaranteed for authentication and
confidentiality. Security-sensitive endpoints migrate first; the rest follow
incrementally.

### 5.2 Protocol Version Marker

The JERI wire protocol version byte (currently `0x02` for the multi-Subject block)
gains a successor value for the DER framing. **[OPEN: choose value, e.g. `0x03`;
confirm there is no collision with any in-use value and that the dispatcher can
branch on it cleanly.]**

### 5.3 Migration End-State

Once a DER implementation exists and is deployed, `WireFormat.JAVA` is deprecated:

- New deployments default to `WireFormat.DER`.
- The SCAP `AtomicSerialComplianceVisitor` already flags non-`@AtomicSerial`
  classes as `DANGEROUS`; those are precisely the classes that cannot ride the DER
  path cleanly, so the deprecation pressure and the existing safety pipeline point
  the same direction.
- Java serialization support is retained only as a read-path compatibility shim for
  a defined deprecation window, then removed (mirrors STD-003 §14 phased approach).

---

## 6. Core Wire Types — Inventory

The following types require ASN.1 modules. Grouped by subsystem. Status reflects how
well the current field layout is captured in available documentation.

| # | Wire type | Subsystem | Source of truth today | Schema status |
|---|---|---|---|---|
| 6.1 | `UserSubjectBlock` (the `0x02` multi-Subject block) | Identity / dispatch | blog appendix; `BasicInvocationDispatcher` | drafted §7.1 |
| 6.2 | `Principal` record | Identity | className + name pairs | drafted §7.1 |
| 6.3 | `AccessControlContextRecord` | Authorization transport | `AccessControlContextSerializer`; blog appendix | drafted §7.2 |
| 6.4 | `DomainIdentityRecord` | Authorization transport | blog appendix | drafted §7.2 |
| 6.5 | `DigestCodeSourceRecord` | Code identity | `DigestCodeSource.java` | **[OPEN]** §7.3 |
| 6.6 | `AnalysisRequest` | SCAP | STD-002 | **[OPEN]** §7.4 |
| 6.7 | `JarAnalysisReport` | SCAP | STD-002 | **[OPEN]** §7.4 |
| 6.8 | `SignedVerdict` | SCAP | STD-002 | **[OPEN]** §7.4 |
| 6.9 | `RegistryVerdict` | SCAP | STD-002 | **[OPEN]** §7.4 |
| 6.10 | `CrashReport` | SCAP | STD-002 | **[OPEN]** §7.4 |
| 6.11 | `PermissionGrant` / `DigestGrant` | Policy | STD-004 (policy syntax) | **[OPEN]** §7.5 |
| 6.12 | Substituted standard types (boxed primitives, `URI`/`URL`, `Date`, `UID`, `MarshalledObject`, `StackTraceElement`, `X500Principal`, `Permission`, `Throwable`, `Properties`) | Cross-cutting | `AtomicMarshalOutputStream.replaceObject()` | drafted §7.6 |
| — | Collections (`Map`/`Set`/`List`) | Cross-cutting | `AtomicMarshal*` carriers | **collapsed to native `SEQUENCE OF` — no wire type (§3.8, §7.6)** |

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

### 7.2 Authorization: AccessControlContext and DomainIdentity

Replaces `[httpmdCount:4B BE][DomainIdentityRecord…][anonCount:4B BE]`.

```asn1
DomainIdentityRecord ::= SEQUENCE {
    codebaseUri     UTF8String,          -- RFC 3986 form (no DNS)
    digest          DigestValue,         -- see 4.3
    -- ORDER-SIGNIFICANT (§3.8): baked-in workload principal chain order preserved.
    principals      SEQUENCE SIZE(0..MAX) OF Principal
    -- [OPEN] does the wire record carry the full permission set, or only identity?
    -- Current model: receiver re-derives permissions from policy using identity.
    -- Confirm permissions are NOT on the wire (identity-only record).
}

AccessControlContextRecord ::= SEQUENCE {
    verifiableDomains   SEQUENCE SIZE(0..MAX) OF DomainIdentityRecord,
    anonCount           INTEGER (0..MAX)
    -- anonCount preserves unverifiable domains as permission ceilings.
    -- Domain stripping was removed (serializer v23): removing unverifiable
    -- domains is an implicit privilege escalation, so the COUNT must travel.
    -- [OPEN] confirm jrt:/java.base exclusion rule is applied at encode time
    -- (excluded from anonCount) and document it as an encoder obligation here.
}

DigestValue ::= SEQUENCE {
    algorithm   AlgorithmIdentifier,
    digest      OCTET STRING
}
```

**Notes / [OPEN]:**
- The `anonCount` integer is security-load-bearing: it is the count of permission
  ceilings the receiver must reconstruct as anonymous placeholder domains. A decoder
  that drops it would silently escalate privilege. The schema makes it mandatory.
- `jrt:/java.base` domains are excluded from `anonCount`; other `jrt:` module domains
  are retained. This is an **encoder obligation** — document precisely so a non-JVM
  encoder replicates it. **[OPEN: full exclusion rule.]**

### 7.3 Code Identity: DigestCodeSourceRecord  **[OPEN]**

```asn1
DigestCodeSourceRecord ::= SEQUENCE {
    locationUri     UTF8String,          -- RFC 3986; locator, not trust anchor
    certificates    SEQUENCE SIZE(0..100) OF Certificate OPTIONAL,  -- MAX_CERT_COUNT
    digest          DigestValue
    -- equality/identity is (uri, certs, algorithm, digestBytes) per DigestCodeSource
    -- a plain CodeSource (no digest) is a DISTINCT identity and MUST NOT be
    -- representable as a DigestCodeSourceRecord with an absent digest.
}
```

**[OPEN]** Confirm against `DigestCodeSource.java`:
- Certificate encoding (X.509 DER `Certificate` reused — consistency win).
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

**[OPEN]** The critical correctness point: the *signature input* must be the DER
encoding of the `tbs`, computed identically on signer and verifier. Today the signed
bytes are the Java-serialized form; under DER the signed bytes become the canonical
DER of the `tbs`. This is a **breaking change to signature computation** and must be
versioned carefully — a verdict signed under Java serialization cannot be verified
against its DER re-encoding. The verdict cache (keyed by content hash, STD-002) is
unaffected since the JAR content hash is independent of verdict encoding, but the
verdict *signature* path needs a clear cutover. **This deserves its own subsection
before implementation.**

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
CollectionField ::= SEQUENCE SIZE(0..MAX) OF Element
MapField        ::= SEQUENCE SIZE(0..MAX) OF SEQUENCE { key Element, value Element }
-- the receiving object imposes ordering/uniqueness/null-policy at construction (§3.8)
```

(An *array* or *linked list* field is also a `SEQUENCE OF`, but ORDER-SIGNIFICANT per
§3.8 — the receiver relies on wire order.)

The irreducible substituted types requiring their own DER form:

| Type | Current serializer | Proposed DER form | Status |
|---|---|---|---|
| `Byte`/`Short`/`Integer`/`Long` | boxed-primitive serializers | `INTEGER` (value-bounded) | [PROPOSED] |
| `Float`/`Double` | boxed-primitive serializers | `REAL`, or `OCTET STRING` of IEEE-754 bits | **[OPEN: REAL is awkward; IEEE-754 bits is deterministic and cross-language safe — recommend the latter]** |
| `Character` | `CharSerializer` | `INTEGER (0..65535)` | [PROPOSED] |
| `Boolean` | `BooleanSerializer` | `BOOLEAN` | [PROPOSED] |
| `Properties` | `PropertiesSerializer` | `SEQUENCE OF SEQUENCE { key UTF8String, value UTF8String }` (behavioural; unordered) | **[OPEN: confirm Properties values are always String]** |
| `URL` | `URLSerializer` | `UTF8String` (RFC 3986; locator, no DNS) | **[OPEN: URL vs URI normalisation]** |
| `URI` | `URISerializer` | `UTF8String` (RFC 3986) | [PROPOSED] |
| `UID` (`java.rmi.server.UID`) | `UIDSerializer` | `SEQUENCE { unique INTEGER, time INTEGER, count INTEGER }` | **[OPEN: confirm field set]** |
| `File` | `FileSerializer` | `UTF8String` path **[OPEN: platform path semantics — is File even sent across runtimes? May be JVM-internal only]** | **[OPEN]** |
| `MarshalledObject` | `MarshalledObjectSerializer` | nested frame: `SEQUENCE { objectBytes OCTET STRING, locationBytes OCTET STRING, codebaseAnnotation ... }` | **[OPEN: this is itself a serialized-object container — define carefully; it nests the wire format inside itself]** |
| `StackTraceElement` | `StackTraceElementSerializer` | `SEQUENCE { declaringClass UTF8String, methodName UTF8String, fileName UTF8String OPTIONAL, lineNumber INTEGER }` | [PROPOSED] |
| `X500Principal` | `X500PrincipalSerializer` | DER `Name` (X.501) — **already DER-native**; reuse X.509 `Name` encoding directly | **[PROPOSED — consistency win; confirm getEncoded() round-trips]** |
| `Date` | `DateSerializer` | `INTEGER` epoch-millis | **[OPEN: epoch-millis INTEGER vs GeneralizedTime — recommend epoch-millis for exact round-trip and no timezone ambiguity]** |
| `Permission` | `PermissionSerializer` | `SEQUENCE { className UTF8String, name UTF8String OPTIONAL, actions UTF8String OPTIONAL }` | **[OPEN: confirm the safe shape — class + target/name + actions]** |
| `Throwable` | `ThrowableSerializer` | see below | **[OPEN]** |
| `AccessControlContext` | `AccessControlContextSerializer` | §7.2 `AccessControlContextRecord` | drafted §7.2 |

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
    stackTrace    SEQUENCE SIZE(0..MAX) OF StackTraceElement,
    cause         ThrowableRecord OPTIONAL   -- acyclic; bounded depth [OPEN: max depth]
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
- `Throwable`: settle maximum cause-chain depth and stack-trace length bounds.
- `File`: determine whether `File` is ever transmitted cross-runtime or is
  JVM-internal only; if cross-runtime, define platform-neutral path semantics.

---

## 8. Codebase Identity Metadata (the implicit-to-explicit migration)

Java serialization carried codebase annotation implicitly in the stream (RMI
codebase annotation). The DER format has no stream-level annotation channel, so
codebase/code identity becomes an **explicit, schema-defined field** on every wire
object whose decoding depends on knowing the producing code's identity.

This is the single most important deliberate-design item in the migration: it is a
security *improvement* (identity is explicit and schema-bounded, not woven into
serialization mechanics), but it must be designed rather than inherited.

**[OPEN]** Decisions required:
- Which wire types carry a codebase-identity field, and whether it is the full
  `DigestCodeSourceRecord` or a reference to one carried once per frame.
- Whether code identity is per-object or per-frame.

**Frame-level table — the sole permitted reference-like construct (§3.7).** A
frame-level `codebaseTable` with per-object indices would avoid repeating identical
`DigestCodeSourceRecord`s and shrink the wire — analogous to how the current ACC
format counts rather than repeats anonymous domains. This is the *only* place the
format permits anything resembling a reference, and it is admissible **only** under a
strict ordering constraint that keeps it outside the object graph under construction:

1. The `codebaseTable` is a flat dictionary at the frame header.
2. It is decoded and validated **in its entirety** before any object body that
   indexes into it is decoded.
3. Indices resolve only into already-completed, immutable table entries — never into
   a partially-constructed or forward object.

Under those three conditions the table does not reintroduce deferred construction or
cycles (§3.7): an index can never point at something still being built. **If that
ordering cannot be cleanly guaranteed in the framing, the table is abandoned** and
`DigestCodeSourceRecord`s are repeated inline, accepting the wire-size cost to
preserve the strict no-references property end-to-end. **Recommendation: frame-level
table, contingent on the framing being able to guarantee header-before-bodies decode
order; otherwise repeat inline.**

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
7. Computes signatures over the canonical DER of the `tbs` (§7.4).
8. Runs the STD-001 `check()` validation contract on decoded values before treating
   any object as constructed.
9. Replicates the encoder obligations documented as **[OPEN]** here (e.g. the
   `jrt:/java.base` `anonCount` exclusion) so that JVM and non-JVM encoders produce
   byte-identical output for identical logical content (required for any
   signature-bearing type).

---

## 10. Open Questions Carried Forward

Consolidated list of every **[OPEN]** above, for the next working session:

1. §4.4 — OID-rooted vs `ENUMERATED` type discrimination (recommendation: OID).
2. §5.2 — Protocol version marker value (e.g. `0x03`); collision check.
3. §7.1 — Confirm `PRINCIPAL_CTORS` allow-list stays in validation layer, not schema.
4. §7.2 — Confirm ACC records are identity-only (no permissions on wire); full
   `jrt:` exclusion rule.
5. §7.3 — `DigestCodeSource` field confirmation; `httpmd:` fragment → explicit field
   normalisation; DOS bounds as schema constraints.
6. §7.4 — Full SCAP object field lists from STD-002; **signature-input cutover**
   design (Java-serialized-bytes → canonical-DER-bytes is breaking).
7. §7.5 — Whether/how `PermissionGrant`/`DigestGrant` travel; STD-004 field layout.
8. §8 — Per-object vs frame-level codebase identity (recommendation: frame-level
   table, contingent on header-before-bodies decode ordering; otherwise repeat
   inline to preserve the strict no-references property).
9. §7.6 — Confirm the complete substituted-type set against the live `serializers`
   map and `defaultReplaceObject` fallbacks; settle `Float`/`Double` (IEEE-754 bits
   vs REAL), `Date` (epoch-millis vs GeneralizedTime), `MarshalledObject` nested-frame
   structure and bounds, `Throwable` cause-chain depth / stack-trace bounds, `File`
   cross-runtime applicability, `X500Principal` `getEncoded()` round-trip.
10. Whole-document — validate every ASN.1 module against a real compiler
    (asn1c / pyasn1 / rasn) before promoting past DRAFT.

---

## Appendix A: Relationship to Existing Standards

| Standard | Relationship |
|---|---|
| JGDMS-STD-001 (@AtomicSerial) | STD-006 is the canonical *encoding* for `@AtomicSerial` objects; STD-001 remains the *validation* contract, unchanged. STD-001 should reference STD-006 as its default wire encoding once DRAFT is promoted. |
| JGDMS-STD-002 (SCAP) | SCAP data objects (§7.4) get ASN.1 modules here; the signature-input cutover (§7.4 [OPEN]) is a coordination point with STD-002. |
| JGDMS-STD-003 (Multi-Subject Identity) | The `UserSubjectBlock` (§7.1) and `AccessControlContextRecord` (§7.2) are the wire encodings of the STD-003 identity model. The `WorkerSubject` is never on the wire (ambient via ProtectionDomain); only `UserSubject` principals and the ACC domain records travel. |
| JGDMS-STD-004 (Policy File Syntax) | If grants travel the wire (§7.5), their DER form is defined here; the policy *file* syntax in STD-004 is unaffected. |
