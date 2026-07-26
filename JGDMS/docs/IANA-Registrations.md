# IANA Registrations — Zeus Project Services Pty Ltd

*Drafted 2026-07-20 (Claude, at Peter's request) after IANA closed the prior ATOMIC DER
submission. **Which registry that submission targeted determines what needs resubmitting**
— this document covers both plausible cases so either path is ready to send. Placeholders
Peter must fill are marked `«...»`.*

---

## 0. Which submission was closed? (read first)

STD-006's only *hard* IANA dependency is a **Private Enterprise Number (PEN)**: the OID
arc `1.3.6.1.4.1.<PEN>` is a clearly-marked placeholder (`999999`) that MUST be replaced
on assignment (STD-006 §4.4 area, notes at lines ~37/1053-1082/3025). If the closed
submission was the PEN application (§1 below), note two consequences:

- **One PEN covers everything.** A PEN is assigned to the *organization*, not a format.
  Sub-arcs beneath it are self-managed — ATOMIC DER, DETERMINISTIC CEL, and any future
  Zeus format get arcs by internal allocation (§1.3), with **no further IANA involvement
  ever**. DETERMINISTIC CEL therefore needs **no separate submission** at this level.
- **The common closure reason is non-response.** PEN applications are closed when the
  applicant misses IANA's follow-up/verification emails (they verify the organization and
  contact). On resubmission, watch `«the contact inbox»` for mail from iana.org for
  several weeks; replies are what keep the ticket open.

Media-type registration (§2) is a **separate, optional** registry. Neither format
strictly needs it today (in-stream identification uses `payloadFormat` /
`CelFilterRecord.formatVersion`), but standalone artifacts — stored/archived records, the
T5 conformance corpus, anything served over HTTP — benefit, and batching both templates
in one submission round is cheap. §2's templates are complete and ready to adapt.

---

## 1. PEN (Private Enterprise Number) application

Submitted at <https://pen.iana.org/pen/PenApplication.page>. The form is short; the
substance is organization verification.

| Field | Value |
|---|---|
| Organization name | Zeus Project Services Pty Ltd |
| Organization address | `«registered business address»` |
| Contact name | Peter Firmstone |
| Contact email | peter.firmstone@zeus.net.au *(or a role address — see note)* |
| Contact phone | `«phone»` |

**Notes for resubmission.**

- If the prior application closed for non-response, nothing about the content needs to
  change — resubmit and answer IANA's verification mail promptly.
- Consider a **role email** (e.g. `iana@zeus.net.au`) as contact: PEN records are
  long-lived and published; personal-mailbox churn is the classic way orgs lose control
  of a PEN.
- The PEN is published in the public enterprise-numbers registry (org name, contact).

### 1.3 Sub-arc allocation plan (internal, self-managed — no IANA involvement)

On assignment, replace every `999999` placeholder in STD-006 (its own notes list the
sites) and record the internal allocation here as the single source of truth:

```
1.3.6.1.4.1.<PEN>          Zeus Project Services Pty Ltd
1.3.6.1.4.1.<PEN>.1        JGDMS
1.3.6.1.4.1.<PEN>.1.1      JGDMS-STD-006 ATOMIC DER (ASN.1 module OIDs per STD-006)
1.3.6.1.4.1.<PEN>.1.2      JGDMS-STD-011 DETERMINISTIC CEL (Appendix B module OID, if/when
                           the module is given one — none required today)
1.3.6.1.4.1.<PEN>.2        DirtyChai (reserved)
1.3.6.1.4.1.<PEN>.3        (unassigned — future projects)
```

---

## 2. Media-type registrations (RFC 6838, vendor tree) — optional, batchable

Both formats are DER-encoded ASN.1, so both can carry RFC 6839's registered **`+der`**
structured-syntax suffix, letting generic DER tooling recognize them. Proposed names
(alternatives noted; pick one pair and keep them visible siblings):

- `application/vnd.zeus.atomic+der` — ATOMIC DER (alternative:
  `vnd.zeus.atomic-der`, without the suffix, if the reviewer finds `atomic-der+der`
  shaped names redundant; the in-stream `payloadFormat` string
  `"JGDMS-STD-006/ATOMIC-DER"` is unaffected either way).
- `application/vnd.zeus.deterministic-cel+der` — DETERMINISTIC CEL.

Vendor-tree registrations are submitted via the media-types application form
(<https://www.iana.org/form/media-types>) and get expert review on the
media-types@iana.org list; respond to reviewer feedback on-list to keep the ticket open.

### 2.1 Template — `application/vnd.zeus.atomic+der`

- **Type name:** application
- **Subtype name:** vnd.zeus.atomic+der
- **Required parameters:** none
- **Optional parameters:** none *(format version and schema identity are carried
  in-band: `payloadFormat`, `schemaDigest`)*
- **Encoding considerations:** binary
- **Security considerations:** The format is a canonical (DER, one-encoding-per-value)
  serialization of application data with an embedded, digest-verified schema
  (`MarshalledInstanceRecord`, JGDMS-STD-006 §7.8). Decoding allocates only within
  schema/profile-declared size ceilings (bounded-before-allocation, STD-006 §3 principle
  5) and is specified fail-secure: any non-canonical encoding, schema/digest mismatch,
  unknown tag, or constraint breach is a hard rejection (principle 6). The object graph
  is acyclic by construction — reference cycles and back-references are unrepresentable
  (STD-006 §3.7) — eliminating cyclic-reference denial-of-service and
  partially-constructed-object reference theft at the format level. Decoding does not
  itself execute code and instantiates only receiver-approved types through a gated
  validation layer (`@AtomicSerial` `check(GetArg)`, JGDMS-STD-001); the format carries
  no code, no codebase annotations, and no serialized class implementations. Content is
  arbitrary application data: confidentiality and end-to-end integrity are the
  responsibility of the carrying transport/storage (in JGDMS, TLS-based JERI transports
  with integrity constraints). Embedded schemas make field names and typed values
  recoverable by any conforming decoder without the originating code; users should be
  aware the format is deliberately *not* obscure — data at rest is legible to anyone
  holding the bytes.
- **Interoperability considerations:** The encoding is DER (ITU-T X.690); the wire type
  model is a closed subset specified for cross-language implementation (JVM and non-JVM)
  with byte-identical canonical output required of every conforming encoder. A published
  ASN.1 module accompanies the specification.
- **Published specification:** JGDMS-STD-006 — ATOMIC DER Wire Format,
  `«stable public URL, e.g. the GitHub blob URL for the ratified revision»`
- **Applications that use this media type:** JGDMS (Jini Global Distributed Micro Services) service marshalling, discovery, and persistent storage; DirtyChai;
  archival/forensic tooling reading stored `MarshalledInstanceRecord`s.
- **Fragment identifier considerations:** none
- **Additional information:**
  - Deprecated alias names for this type: none
  - Magic number(s): none (outer TLV is a DER SEQUENCE, tag 0x30; records carry the
    UTF8String `"JGDMS-STD-006/ATOMIC-DER"` as the `payloadFormat` field)
  - File extension(s): `«.ader [PROPOSED]»`
  - Macintosh file type code(s): none
- **Person & email address to contact for further information:** Peter Firmstone,
  peter.firmstone@zeus.net.au
- **Intended usage:** COMMON
- **Restrictions on usage:** none
- **Author:** Peter Firmstone, Zeus Project Services Pty Ltd
- **Change controller:** Zeus Project Services Pty Ltd

### 2.2 Template — `application/vnd.zeus.deterministic-cel+der`

- **Type name:** application
- **Subtype name:** vnd.zeus.deterministic-cel+der
- **Required parameters:** none
- **Optional parameters:** none *(format version is in-band:
  `CelFilterRecord.formatVersion`)*
- **Encoding considerations:** binary
- **Security considerations:** The format encodes expressions of DETERMINISTIC CEL
  (JGDMS-STD-011), a deliberately restricted, non-Turing-complete filter/transform
  expression language: the grammar contains no loops, recursion, user-defined functions,
  assignment, or I/O constructs — such behaviour is unrepresentable rather than
  prohibited — and every expression's worst-case evaluation cost is statically
  computable and bounded by pinned profile ceilings before evaluation (STD-011 §3, §10;
  cost arithmetic is specified overflow-checked). The wire form is canonical DER with a
  closed node set: unknown or reserved node tags, non-canonical encodings (including
  non-minimal INTEGER/ENUMERATED/BOOLEAN and non-shortest-form UTF-8), ceiling
  violations, unknown function identifiers, and arity violations are all specified as
  hard decode-time rejections (STD-011 Appendix B §B.5-§B.6). Evaluation is total and
  side-effect-free over a closed error set; in filtering use, any evaluation error is
  specified fail-closed (no match). These guarantees hold only for conforming
  evaluators; recipients MUST enforce the decode-time rejections and the static cost
  gate before evaluating expressions from untrusted sources, as expressions are
  attacker-supplied by design in the format's primary (predicate push-down) use case.
  Expressions may reveal information about their author's query intent; no
  confidentiality is provided by the format itself.
- **Interoperability considerations:** Specified for bit-identical results from
  independent implementations (Java and Rust are the reference targets): all
  implementation latitude present in upstream CEL — evaluation order, error identity,
  NaN ordering, transcendental accuracy (correct rounding is REQUIRED) — is pinned by
  the specification, and a shared conformance corpus is normative. The textual form is
  a strict syntactic subset of CEL (cel.dev); the wire form is DER (ITU-T X.690) with a
  published ASN.1 module.
- **Published specification:** JGDMS-STD-011 — DETERMINISTIC CEL Filter/Transform
  Expression Format (and its Appendix B, DER Wire Encoding),
  `«stable public URL for the ratified revision»`
- **Applications that use this media type:** JGDMS service-side filter push-down
  (JavaSpaces/Outrigger, Reggie lookup); stored filter registrations; the DETERMINISTIC
  CEL cross-implementation conformance corpus.
- **Fragment identifier considerations:** none
- **Additional information:**
  - Deprecated alias names for this type: none
  - Magic number(s): none (outer TLV is a DER SEQUENCE, tag 0x30, first field
    `formatVersion` INTEGER)
  - File extension(s): `«.dcel [PROPOSED]»`
  - Macintosh file type code(s): none
- **Person & email address to contact for further information:** Peter Firmstone,
  peter.firmstone@zeus.net.au
- **Intended usage:** COMMON
- **Restrictions on usage:** none
- **Author:** Peter Firmstone, Zeus Project Services Pty Ltd
- **Change controller:** Zeus Project Services Pty Ltd

---

## 3. Submission checklist

1. `«Confirm which registry the closed submission was in»` — PEN (§1) or media types
   (§2) — and, if reviewer feedback exists, paste it into this file so the resubmission
   addresses it point-by-point rather than repeating the round-trip.
2. PEN: resubmit §1, watch for verification mail, replace the `999999` placeholders in
   STD-006 on assignment, record the §1.3 arc allocation.
3. Media types (optional, batchable any time): fill the `«URLs»` with stable public
   spec links (a tagged GitHub release URL beats a branch URL — reviewers prefer
   references that won't drift), settle the `+der`-suffix naming question, submit both
   templates in one round.
4. Nothing further is needed for DETERMINISTIC CEL at the PEN level — it rides the
   organization PEN's self-managed arc (§1.3).
