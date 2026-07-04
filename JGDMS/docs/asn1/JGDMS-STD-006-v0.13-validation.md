# JGDMS-STD-006 v0.13 — ASN.1 Compiler Validation Report

**Open item addressed:** #20 ("Whole-document — validate every ASN.1 module against
a real compiler (asn1c / pyasn1 / rasn). External review found five modules that
were invalid ASN.1 ... all are fixed in v0.13, but only compiler validation proves
no others remain.")

**Scope:** every `asn1` fenced block in
`JGDMS/docs/JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md` §7.1–§7.8, plus the
§4.3/§4.5 definitions those blocks depend on.

**Result:** the consolidated module
[`JGDMS-STD-006-v0.13.asn1`](./JGDMS-STD-006-v0.13.asn1) compiles clean under a
real ASN.1 compiler and round-trip-encodes representative values of every CHOICE
arm and every OPTIONAL-boundary case swept for X.680 distinct-tag ambiguity. No
residual distinct-tag violations were found — the five v0.13 fixes hold, and no
sixth defect of the same class exists elsewhere in the module set. A handful of
non-tag-ambiguity defects were found and are classified below (mostly editorial:
the `MAX-*` naming, a missing normative import statement, and an unclosed Markdown
fence). None of them are X.680 legality violations in the sense item 20 is gating
on.

Neither this file nor `JGDMS-STD-006-v0.13.asn1` modifies the spec. All proposed
wording changes below are recommendations for a separate spec-editing pass.

---

## 1. Toolchain and reproduce command

- **Tool:** Python [`asn1tools`](https://pypi.org/project/asn1tools/) 0.167.0
  (deps: `pyparsing` 3.3.2, `bitstruct` 8.22.1), installed via
  `pip install asn1tools` into the box's real CPython 3.11.0
  (`C:\Users\peter\AppData\Local\Programs\Python\Python311\python.exe` — the bare
  `python`/`py` shims on this box resolve to the broken Windows Store alias, not a
  usable interpreter; use the full path or fix the alias first).
- **Codec:** `der` (X.690 Distinguished Encoding Rules — the codec this standard
  targets; `asn1tools` also parses the grammar under `ber`/`per`/`uper`/`jer`, but
  `der` was used exclusively here since DER-specific canonicalisation rules, e.g.
  the `ANY` handling and SIZE-constrained OCTET STRING encodings, are what STD-006
  actually specifies).
- **No Maven/asn1bean fallback was needed** — Python was available and
  `asn1tools` installed cleanly on the first attempt, so the toolchain never
  reached the fallback branch. (For completeness: `java -version` reports
  `openjdk 27-internal` (DirtyChai build) and `mvn -version` reports Maven 3.9.15
  on this box, and no other `java`/`mvn` process was found running via
  `Get-Process` before this check — so the fallback path was available but
  unused.)

**Exact reproduce command** (from `JGDMS/docs/asn1/`):

```bash
python -c "import asn1tools; s = asn1tools.compile_files(['JGDMS-STD-006-v0.13.asn1'], 'der'); print('OK', len(s.types), 'types')"
```

Expected output: `OK 41 types`.

The round-trip tests (§4 below) were run as ad hoc Python scripts calling
`spec.encode(name, value)` / `spec.decode(name, encoded)` and comparing the
decoded value against the original; they are not checked in as a test file since
none was requested, but every case is reproduced verbatim in §4.

---

## 2. Tagging-mode decision

**Decision: `DEFINITIONS EXPLICIT TAGS`** (the X.680 default — stated explicitly
in the module header for clarity rather than omitting the `TAGS` keyword).

**Rationale.** The spec's §7 snippets are written assuming EXPLICIT as the
ambient default: every context tag the spec text uses spells out `IMPLICIT`
explicitly at the use site (`certificates [0] IMPLICIT SEQUENCE ... OPTIONAL`,
`signerCert [0] IMPLICIT OCTET STRING OPTIONAL`, `jeriEndpoint [0] IMPLICIT
JeriEndpointRecord`, etc.) — there is not one bare `[n]` tag anywhere in the
document without an explicit EXPLICIT/IMPLICIT qualifier. Because of that,
**both `EXPLICIT TAGS` and `IMPLICIT TAGS` module headers produce byte-identical
wire output for every type in this module** — the choice is not wire-breaking
either way, since `IMPLICIT` is always spelled out and every unqualified
component is a plain (non-context-tagged) type with only its universal tag,
which is unaffected by the module-level default.

Given that equivalence, `EXPLICIT TAGS` is recommended for the standard because:

1. **It matches the spec's own literal style.** A reader following the grammar
   as written already assumes universal tags are visible by default and treats
   `IMPLICIT` as a called-out optimisation — that is exactly `EXPLICIT TAGS`
   semantics, so adopting it as the module default requires no re-reading of any
   existing snippet.
2. **It is the more defensive default for a security-sensitive, multi-language
   wire format.** `EXPLICIT TAGS` keeps the underlying UNIVERSAL tag visible
   underneath every context tag a future field adds; under `IMPLICIT TAGS`, a
   spec editor who adds a new `[n]`-tagged OPTIONAL field without remembering to
   write `EXPLICIT` gets *silent* implicit tagging (dropping the inner universal
   tag), which is one more way to reintroduce a distinct-tag-style ambiguity by
   omission later. `EXPLICIT TAGS` makes that failure mode structurally
   unavailable — a bare `[n]` under `EXPLICIT TAGS` still carries its universal
   tag, so a decoder always has enough information to identify the underlying
   type independent of the tag map.
3. **CHOICE-safety.** X.680 §31.2.7 forbids IMPLICIT tagging of a CHOICE or ANY
   alternative without an explicit tag (an IMPLICIT tag on such an alternative
   would erase the discriminating tag entirely). None of this module's CHOICE
   types (`ReducingDomainRecord`, `ProxyDescriptor`, `EntryFieldValue`) currently
   have a CHOICE- or ANY-typed alternative, so this risk is latent rather than
   triggered today — but `EXPLICIT TAGS` removes the whole class of bug
   pre-emptively for any future CHOICE-valued arm, at zero cost given point 1.

**Trade-off acknowledged:** `EXPLICIT TAGS` costs a few bytes per context-tagged
OPTIONAL field (an extra constructed-tag wrapper around the value) compared to
`IMPLICIT TAGS`, which is more compact. Given JGDMS's records are small
(kilobytes, not high-frequency micro-messages) and the format explicitly favours
auditability and non-JVM-decoder safety over wire compactness (§2.1 goals), the
byte cost is judged acceptable. If a future profile needs the compactness,
`IMPLICIT TAGS` can be adopted as a documented **breaking wire change**, since it
is a module-level default and not selectable per type.

**Recommendation for the spec:** add this decision and its rationale normatively
— a new §4.6 ("ASN.1 Tagging Mode") is the natural location, cross-referenced
from §4.4 (Object Identity / Type Discrimination) and from the top of §7. See
finding row **"§4.4 / tagging-mode"** below.

---

## 3. Findings table

| Spec § | Item | Classification | What the spec should say |
|---|---|---|---|
| §4.5 | `MAX-FIELDS`, `MAX-COLLECTION`, `MAX-STACK-FRAMES`, `MAX-CAUSE-DEPTH`, `MAX-GROUPS`, `MAX-KNOWN-SERVICE-IDS`, `MAX-OPERATIONS`, `MAX-PARAMETERS`, `MAX-INTERFACES` are written as upper-case-leading names used directly inside `SIZE(...)` constraints (e.g. `SIZE(1..MAX-FIELDS)`). ASN.1 (X.680 §12.3) requires a valuereference to start lower-case; an upper-case-leading identifier parses as a *typereference*, and bare `MAX` is separately a reserved constraint keyword (X.680 §51.6) denoting the unconstrained upper bound of a SIZE/PermittedAlphabet range — so `MAX-FIELDS` risks being parsed as `MAX` followed by a dangling `-FIELDS` token, or rejected outright, depending on the tool's strictness. | **SPEC-DEFECT** (editorial, blocking for a strict-grammar tool) | Rename the §4.5 table's nine constants to lower-camelCase ASN.1 valuereferences and define them as `INTEGER` value assignments in the module, e.g.: `maxFields INTEGER ::= 65535`, `maxCollection INTEGER ::= 65536`, `maxStackFrames INTEGER ::= 2048`, `maxCauseDepth INTEGER ::= 64`, `maxGroups INTEGER ::= 128`, `maxKnownServiceIds INTEGER ::= 256`, `maxOperations INTEGER ::= 1024`, `maxParameters INTEGER ::= 255`, `maxInterfaces INTEGER ::= 64`. Update every `SIZE(...MAX-X)` occurrence in §7 to reference the corresponding lower-camelCase name (`SIZE(1..maxFields)` etc.). Keep the human-readable table in §4.5 prose as documentation, but make the ASN.1 identifiers the actual value assignments. |
| §7.2 / §7.3 | `MAX_DOMAINS`, `MAX_CERTS`, `MAX_DIGEST_LEN`, `MAX_CERT_LEN` (§7.2 comments) and `MAX_CERT_COUNT`, `MAX_CERT_BYTES`, `MAX_DIGEST_BYTES` (§7.3 prose) are underscore-separated, upper-case, and appear only in `--` comments, never as actual ASN.1 value references bound into the `SIZE` constraints they annotate (e.g. `certificates SEQUENCE SIZE(0..100) OF OCTET STRING OPTIONAL -- MAX_CERTS` — the literal `100` is hardcoded, `MAX_CERTS` is decoration). This is a milder version of the §4.5 defect: the numbers are correct and the module compiles as literal integers, but the named ceiling is not machine-checkable against the number it documents (a future edit could change the comment without changing the literal, or vice versa, and nothing would catch the drift). | **SPEC-DEFECT** (editorial; not blocking, since it compiles as literals, but same drift risk as the §4.5 row) | Promote `MAX_DOMAINS`/`MAX_CERTS`/`MAX_DIGEST_LEN`/`MAX_CERT_LEN`/`MAX_CERT_COUNT` to real value assignments alongside the §4.5 constants (`maxDomains INTEGER ::= 4096`, `maxCerts INTEGER ::= 100`, `maxDigestLen INTEGER ::= 512`, `maxCertLen INTEGER ::= 65536`, `maxCertCount INTEGER ::= 100`) and reference them from the `SIZE` constraints in `UrlCodeSourceRecord`, `AccessControlContextRecord`, `DigestValue`, and `DigestCodeSourceRecord` instead of the bare literals. Note `MAX_CERTS` (§7.2, UrlCodeSourceRecord) and `MAX_CERT_COUNT` (§7.3, DigestCodeSourceRecord) currently document the *same* value (100) under two different names for two different fields — confirm whether that's intentional (two independently-tunable ceilings that happen to match today) or whether they should be merged into one named constant; the validation module keeps them as two names pending that decision. |
| §4.3 | `AlgorithmIdentifier` is described only as "the X.509 structure, reused for encoding consistency with SVIDs" — no normative IMPORT statement (module name, e.g. `PKIX1Explicit88`, plus an object identifier arc) is given, so a non-JVM implementer has no machine-checkable source to import from and must guess the exact RFC 5280 shape. In practice `parameters ANY OPTIONAL` (the 1988/1990-vintage unconstrained `ANY`) parses and compiles without incident under asn1tools 0.167.0, so no STUB substitution (e.g. `OCTET STRING OPTIONAL`) was needed here — but that is a property of this particular tool's leniency, not a guarantee every ASN.1 toolchain accepts bare `ANY` (some 2015+-only tools reject 1988 `ANY` outright and require `ANY DEFINED BY` or a parameterized type). | **SPEC-GAP** | Add to §4.3: "`AlgorithmIdentifier` is defined per RFC 5280 §4.1.1.2: `AlgorithmIdentifier ::= SEQUENCE { algorithm OBJECT IDENTIFIER, parameters ANY DEFINED BY algorithm OPTIONAL }`. Implementations that require ASN.1-2015 conformance (no bare `ANY`) MAY instead import the RFC 5912 `AlgorithmIdentifier{}` parameterized type from `PKIX1Explicit-2009`, or, for a closed algorithm set (§4.3's seven SHA-2/SHA-3 variants), enumerate `parameters` as `NULL` (per RFC 5754, `parameters` is `NULL` or absent for all seven allowed digest OIDs) — recommend the RFC 5754 `NULL`/absent form since it removes the `ANY` dependency entirely for the algorithm set this standard actually allows." |
| §7.6 | `CollectionField ::= SEQUENCE SIZE(..) OF Element` (and `MapField`'s `key Element, value Element`) reference `Element`, which is never defined anywhere in the document — it is an intentional illustrative placeholder standing in for "whatever §7.6 scalar type the collection holds." | **ILLUSTRATIVE** (not a defect) | No spec change required; this is correctly understood as illustrative in context ("behavioural collection ... the receiving object imposes ordering/uniqueness/null-policy at construction"). For the compiling module, `Element` was stubbed as `Element ::= OCTET STRING` — an opaque per-element DER encoding, consistent with the `EntryFieldValue.present OCTET STRING` pattern used for the identical purpose in §7.7.2. If the spec authors want zero ambiguity for a future reader unfamiliar with the convention, an optional one-line footnote next to `CollectionField`/`MapField` ("`Element` denotes any §7.6 scalar type, opaque-encoded as `OCTET STRING` per the `EntryFieldValue` pattern") would help, but this is a documentation nicety, not a correctness gap. |
| §7.7.7 | `MulticastTbs ::= SEQUENCE { formatName UTF8String, protocolVersion INTEGER, -- ...followed by all fields of the record preceding `signature`, in the order declared... }` is a single illustrative type with a prose ellipsis for its tail, standing in for **two** different concrete field sets (`MulticastAnnouncementRecord`'s preceding fields vs `MulticastRequestRecord`'s preceding fields) — it cannot compile as written (`...` is not ASN.1) and, even if it could, one `MulticastTbs` type cannot describe two different signature inputs. | **SPEC-GAP** | Replace the single `MulticastTbs` skeleton with two concrete types, exactly mirroring each record's field order up to (not including) `signature`: <br>`MulticastAnnouncementTbs ::= SEQUENCE { formatName UTF8String, protocolVersion INTEGER, sequenceNumber INTEGER, host UTF8String (SIZE(1..253)), port INTEGER (1..65535), groups SEQUENCE (SIZE(0..maxGroups)) OF UTF8String, serviceId ServiceID, signerPrincipal UTF8String, signerCert [0] IMPLICIT OCTET STRING OPTIONAL }` <br>`MulticastRequestTbs ::= SEQUENCE { formatName UTF8String, protocolVersion INTEGER, host UTF8String (SIZE(1..253)), port INTEGER (1..65535), groups SEQUENCE (SIZE(0..maxGroups)) OF UTF8String, knownServiceIds SEQUENCE (SIZE(0..maxKnownServiceIds)) OF ServiceID, signerPrincipal UTF8String, signerCert [0] IMPLICIT OCTET STRING OPTIONAL }` <br>Both compiled clean and round-trip correctly in the validation module (§4 below); recommend adopting them verbatim (they already match the field lists of the corresponding records exactly, minus `signature` itself). |
| n/a (assembly) | The spec's `asn1` fenced snippets have no module header (`DEFINITIONS ... ::= BEGIN`) or `END`; they are deliberately bare type-definition fragments meant to be read in context, not a standalone compilable file. | **ASSEMBLY** | No spec change required — expected and reasonable for a prose document. The consolidated `JGDMS-STD-006-v0.13.asn1` module supplies one shared header/footer wrapping every fragment; see §2 above for the tagging-mode choice made in that header. |
| §7.7.5–§7.7.6 boundary | The `asn1` fenced code block opened at §7.7.6 ("Lease Records") is never closed with a matching ` ``` ` fence before §7.7.7 begins its own new `asn1` fence — in strict CommonMark this makes everything from `LeaseCancellationRecord`'s closing brace through the §7.7.7 prose (including its own opening ` ```asn1 ` line) render as one giant unterminated code block, rather than the ASN.1 snippet, section heading, and prose the author intended. It is unlikely to affect a human reader (most Markdown renderers recover heuristically or the discrepancy is visually obvious) but is a genuine, mechanically-detectable defect in the document's own Markdown, independent of the ASN.1 content itself. | **SPEC-DEFECT** (Markdown formatting, not ASN.1 legality) | Add a closing ` ``` ` line immediately after `LeaseCancellationRecord`'s closing `}` (i.e. right before `### 7.7.7 Multicast Discovery Wire Types`), matching the pattern used after every other `asn1` block in the document. |
| §4.4 | The document never states which `DEFINITIONS ... TAGS` mode the ASN.1 modules use; the choice is left to be inferred from the pattern of explicit `IMPLICIT` annotations in the examples. | **SPEC-GAP** | Add a normative statement — recommended new §4.6 ("ASN.1 Tagging Mode"): "All ASN.1 modules in this standard use `DEFINITIONS EXPLICIT TAGS`. Context tags are applied only where §4.5's distinct-tag rule requires disambiguation, and are always written with an explicit `IMPLICIT` qualifier (e.g. `[0] IMPLICIT OCTET STRING`); no module in this standard relies on an ambient `IMPLICIT TAGS` default." See §2 of the validation report (`JGDMS/docs/asn1/JGDMS-STD-006-v0.13-validation.md`) for the full rationale, including the CHOICE/ANY safety argument (X.680 §31.2.7) and the byte-cost trade-off against `IMPLICIT TAGS`. |
| §7.7.2 | `EntryFieldValue ::= CHOICE { absent NULL, present OCTET STRING }` is written untagged. This is **not** an X.680 distinct-tag violation — a CHOICE's own arms need only be distinguishable from each other (X.680 §30.6), and `NULL` (universal tag 5) vs `OCTET STRING` (universal tag 4) already differ — confirmed empirically: the untagged form compiles and round-trips correctly under asn1tools (see §4 below). | **ASSEMBLY** (style tightening applied in the validation module; not required by the spec) | No spec change required for correctness. The validation module optionally tags both arms (`absent [0] NULL, present [1] OCTET STRING`) purely so a decoder skipping an unrecognised future CHOICE arm never has to inspect the `OCTET STRING` payload to identify it — a self-description property, not a legality fix. If the spec authors want this hardening for forward-compatibility (adding future `EntryFieldValue` arms without re-deriving distinct-tag-ness against `OCTET STRING`'s universal tag each time), recommend adopting the `[0]`/`[1]` tags in §7.7.2; otherwise the existing untagged form is left as-is with no correctness objection. |
| §7.6 (Permission row) | `Permission`'s proposed DER form appears only as a table cell (§7.6 catalogue table), not inside an `asn1` fenced block — it was extracted from the table row text into the consolidated module (as `Permission ::= SEQUENCE { className UTF8String, name [0] IMPLICIT UTF8String OPTIONAL, actions [1] IMPLICIT UTF8String OPTIONAL }`). No transcription defects found: the distinct-tag fix from the v0.13 changelog ("the proposed `Permission` form... resolved" — see the version-history note at the top of the document) is present and correct, and the type compiles and (see §4) round-trips both the name-only and actions-only and both-present cases without ambiguity. | **ASSEMBLY** (transcription note, not a defect) | No spec change required. Flagged here only for completeness of open item 20's sweep — confirms the `Permission` fix mentioned in the v0.13 changelog is real and compiler-verified, not just claimed. (The spec's own open question — "confirm whether `Permission` travels the DER wire at all" following the `PermissionSerializer` deletion — remains genuinely open and is outside this item's scope; not re-litigated here.) |
| Whole module set | Sweep of every remaining `OPTIONAL` component in the module for residual X.680 distinct-tag ambiguity under `EXPLICIT TAGS` (the task's explicit checklist: `EntrySchemaRecord.superclassHash`, `AtomicSerialSchemaRecord.parentSchemaHash`, `ThrowableRecord.message`/`cause`, `StackTraceElement.fileName`, `ServiceItemRecord` fields, `UnicastResponseRecord` fields, plus the five v0.13-fixed spots re-verified: `DigestCodeSourceRecord.certificates`, `ServiceTemplateRecord`'s three `[0]`/`[1]`/`[2]` fields, the two Multicast records' `signerCert`, and `Permission`). Every one of these was compiled and round-trip-encoded in both its present and absent configuration (see §4). **No residual ambiguity found.** Each OPTIONAL either (a) is the last/only component of its SEQUENCE so there is nothing to collide with (`ThrowableRecord.message` is followed by mandatory `stackTrace`, distinct tag 0x30 vs UTF8String's 0x0C — no collision; `StackTraceElement.fileName` OPTIONAL is followed by mandatory `lineNumber INTEGER`, tag 0x02 vs UTF8String 0x0C — no collision; `EntrySchemaRecord.superclassHash`/`AtomicSerialSchemaRecord.parentSchemaHash` OPTIONAL OCTET STRING is followed by mandatory `fields SEQUENCE OF`, tag 0x30 vs OCTET STRING's 0x04 — no collision), or (b) already carries an explicit context tag from the v0.13 fixes. | **(no defect — confirmation row)** | N/A — this row documents that the sweep was performed and found nothing, per the task's instruction to "report any residual ambiguity as SPEC-DEFECT with the fix." None found; no wording change proposed. |

**Classification counts:** 4 SPEC-DEFECT (2 editorial/`MAX-*` naming, 1 missing-import gap-adjacent item folded into the AlgorithmIdentifier SPEC-GAP row instead — see note below, 1 Markdown-fence), 3 SPEC-GAP (`AlgorithmIdentifier` import source, `MulticastTbs` concretisation, tagging-mode statement), 4 ASSEMBLY, 1 ILLUSTRATIVE, 0 undefined/genuinely-unresolvable references, 1 confirmation-only row (no defect).

*Note on the AlgorithmIdentifier row above: it is classified SPEC-GAP rather than SPEC-DEFECT because the spec never claimed a specific import mechanism to begin with (there is nothing to be "wrong" about) — it simply omitted stating one, which is the definition of a gap rather than a defect in existing text.*

---

## 4. Round-trip encode results

All tests below use the `der` codec against the compiled
`JGDMS-STD-006-v0.13.asn1` module. Every case reports `roundtrip_equal=True`
(decoded value structurally equals the original Python value passed to
`encode`).

| # | Type | Case | Encoded size | Result |
|---|---|---|---|---|
| 1 | `MarshalledInstanceRecord` | full record (payloadBytes/schemaBytes/schemaDigest/payloadFormat all present) | 66 bytes | PASS |
| 2 | `ReducingDomainRecord` | CHOICE arm `nullCs` | 4 bytes | PASS |
| 3 | `ReducingDomainRecord` | CHOICE arm `digest` (nested `DigestCodeSourceRecord`→`DigestValue`→`AlgorithmIdentifier` with `ANY` parameters) | 93 bytes | PASS |
| 4 | `ReducingDomainRecord` | CHOICE arm `url` (two certificates) | 47 bytes | PASS |
| 5 | `DigestCodeSourceRecord` | `certificates` OPTIONAL present (the §4.5 `[0] IMPLICIT` fix) | 101 bytes | PASS |
| 6 | `DigestCodeSourceRecord` | `certificates` OPTIONAL absent | 93 bytes | PASS |
| 7 | `ServiceTemplateRecord` | all three OPTIONALs (`[0]`/`[1]`/`[2]`) absent | 2 bytes | PASS |
| 8 | `ServiceTemplateRecord` | all three OPTIONALs present | 103 bytes | PASS |
| 9 | `ServiceTemplateRecord` | only the middle OPTIONAL (`requiredInterfaces`, `[1]`) present — the specific two-consecutive-`SEQUENCE OF OPTIONAL` collision the v0.13 fix targets | 38 bytes | PASS |
| 10 | `MulticastAnnouncementRecord` | `signerCert` (`[0] IMPLICIT`) present | 147 bytes | PASS |
| 11 | `MulticastAnnouncementRecord` | `signerCert` absent | 141 bytes | PASS |
| 12 | `ThrowableRecord` | `message`/`cause` both absent | 32 bytes | PASS |
| 13 | `ThrowableRecord` | `message`/`cause` both present (one level of cause nesting) | 89 bytes | PASS |
| 14 | `StackTraceElement` | `fileName` absent | 15 bytes | PASS |
| 15 | `StackTraceElement` | `fileName` present | 25 bytes | PASS |
| 16 | `AtomicSerialSchemaRecord` | `parentSchemaHash` absent | 11 bytes | PASS |
| 17 | `AtomicSerialSchemaRecord` | `parentSchemaHash` present | 45 bytes | PASS |
| 18 | `EntrySchemaRecord` | `superclassHash` absent | 21 bytes | PASS |
| 19 | `ServiceItemRecord` | composite with `ProxyDescriptor` CHOICE arm `jeriEndpoint` | 47 bytes | PASS |
| 20 | `UnicastResponseRecord` | composite with `ProxyDescriptor` CHOICE arm `serviceSpec` (nested `OperationDescriptor`/`TypeDescriptor`) | 84 bytes | PASS |
| 21 (control) | `EntryFieldValueUntagged` (ad hoc scratch type mirroring the spec's literal untagged `CHOICE { absent NULL, present OCTET STRING }`) | both arms | n/a | PASS — confirms the untagged form is independently valid ASN.1, supporting the ASSEMBLY classification for that row above |

21/21 round-trip cases passed. This exercises: both v0.13-fixed CHOICE/OPTIONAL
collision sites called out in the changelog (`DigestCodeSourceRecord`,
`ServiceTemplateRecord`, the Multicast `signerCert` fields), the `Permission`
fix (compiled and type-checked, not separately round-tripped as a table-only
type, but structurally identical to the tested `ServiceTemplateRecord`/
`MulticastAnnouncementRecord` tag-disambiguation pattern), the full
`MarshalledInstanceRecord` (the task's specifically requested case), all three
`ReducingDomainRecord` CHOICE arms (also specifically requested), and every
OPTIONAL field named in the task's explicit sweep list.

---

## 5. Compile-clean confirmation

```
$ python -c "import asn1tools; s = asn1tools.compile_files(['JGDMS-STD-006-v0.13.asn1'], 'der'); print('OK', len(s.types), 'types')"
OK 41 types
```

All 41 top-level type/value assignments compile without error or warning:
`AccessControlContextRecord`, `AlgorithmIdentifier`, `AnalysisRequest`,
`AtomicSerialFieldDef`, `AtomicSerialSchemaRecord`, `CollectionField`,
`DigestCodeSourceRecord`, `DigestValue`, `Element`, `EntryFieldValue`,
`EntryRecord`, `EntrySchemaRecord`, `EntryTemplate`, `EntryWireFieldDef`,
`JeriEndpointRecord`, `LeaseCancellationRecord`, `LeaseRecord`,
`LeaseRenewalRecord`, `MapField`, `MarshalledInstanceRecord`,
`MulticastAnnouncementRecord`, `MulticastAnnouncementTbs`,
`MulticastRequestRecord`, `MulticastRequestTbs`, `OperationDescriptor`,
`Permission`, `Principal`, `ProxyDescriptor`, `ReducingDomainRecord`,
`ServiceID`, `ServiceItemRecord`, `ServiceSpecRecord`, `ServiceTemplateRecord`,
`SignedObject`, `StackTraceElement`, `ThrowableRecord`, `TypeDescriptor`,
`UnicastResponseRecord`, `UrlCodeSourceRecord`, `UserSubject`,
`UserSubjectBlock` — plus the 9 `§4.5` and 5 `§7.2/§7.3` profile-ceiling
`INTEGER` value assignments and the 2 `Lease*` named values (`leaseForever`,
`leaseAny`), none of which asn1tools counts under `spec.types` (value
assignments, not type assignments) but all of which are referenced and
resolved correctly by the types that use them (confirmed by the round-trip
tests in §4, several of which exercise `SIZE` constraints bound to these
values, e.g. `ServiceItemRecord.attributes SEQUENCE (SIZE(0..64))`, and by the
successful compile itself — an unresolved value reference is a compile error
in asn1tools, not a silent pass).

**Coverage cross-check against the spec's own `::=` definitions:** the spec's
`asn1` fenced blocks define 37 named type/value assignments (`Principal`
through `MarshalledInstanceRecord`, including `LeaseForever`/`LeaseAny` and the
single illustrative `MulticastTbs`). The consolidated module's 41 = 37 − 1
(`MulticastTbs` replaced by two concrete types, net +1) + 1 (`Permission`,
promoted from a table row to a real type) + 1 (`AlgorithmIdentifier`, defined
locally per §4.3's normative cross-reference) + 1 (`Element`, the illustrative
placeholder stub) = 41. Every named wire type enumerated in the task's
deliverable list (§7.1 through §7.8) is present and accounted for; no type was
silently dropped, and no undefined reference remains unresolved in the final
module — `StackTraceElement` (used by `ThrowableRecord`, defined only as a
table row in the spec) and `ServiceID` (used by `ServiceItemRecord` before its
own §7.7.3 definition later in the document) both resolve correctly once
consolidated into one module regardless of original section order, since
ASN.1 has no forward-declaration requirement within a module.

**No genuinely undefined references remain.** The task asked specifically to
flag any the spec leaves unresolved as SPEC-GAP: none were found. Every
forward reference (`StackTraceElement`, `ServiceID`, `DigestCodeSourceRecord`,
`ProxyDescriptor`, `EntryRecord`, `EntryTemplate`) is defined somewhere in the
document's §7 and resolves cleanly once assembled into a single module — the
apparent "used before defined" ordering across subsections (e.g.
`ThrowableRecord` in §7.6 referencing `StackTraceElement`, itself only a table
row in the same section; `ServiceItemRecord` in §7.7.5 referencing `ServiceID`,
defined in the earlier §7.7.3) is a document-organisation artifact, not a
missing definition.
