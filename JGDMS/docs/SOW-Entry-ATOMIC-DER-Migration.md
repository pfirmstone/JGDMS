# Scope of Work — Entry `ATOMIC_DER` Migration + Outrigger Filter-Pushdown Integration

- **Drafted:** 2026-07-20. **Part C added later the same day** at Peter's request ("can Outrigger
  be modernised internally to be more efficient for function searches?"), after a third
  exploration pass over Outrigger's internal data structures (§2.5).
- **Status:** DRAFT — investigation complete (all file:line evidence below verified against trunk
  2026-07-20 by three dedicated exploration passes), task breakdown for review, no implementation started.
- **Origin:** the follow-on SOW named (but not written) in `SOW-CEL-Filter-Format.md` §3 non-goals,
  triggered by Peter's 2026-07-20 question: *can filters run on serialized data contents, without
  classes — observing string and numeric data?* The answer (yes — see STD-009 §8's 2026-07-20
  verification note) corrected the cost framing of STD-009 §11's research note: the typed-projection
  substrate **already exists on trunk**; the real work is `ATOMIC_DER` adoption by the entry paths
  plus the matching-path wiring. Peter additionally asked (2026-07-20) for the Outrigger filter
  integration to be investigated and scoped — that is Part B.
- **Companions:** `JGDMS-STD-009-...-DRAFT.md` §8 (2026-07-20 note: the verified class-free
  projection mechanism), §11 (2026-07-20 correction); `JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md`
  §2.3/§3.11 (data independence), §3.12 (wire type model), §7.8 (`MarshalledInstanceRecord`);
  `SOW-CEL-Filter-Format.md` (the evaluator primitive this SOW's Part B consumes — T1/T2/T3 are
  hard dependencies of B3). *(`SOW-RemoteEvent-Source-DER-Encoding.md` was previously cited here
  as board-reviewed precedent — corrected below, §2.3: it is not a precedent for this SOW's
  byte-compare-mismatch hazard shape.)*
- **Board review (2026-07-20):** Board-reviewed 2026-07-20 (two adversarial seats: evidence+Part-A,
  Part-B/C-design; both SOUND-WITH-FIXES). Consolidated 18-item fix list applied this revision;
  NO-GO for implementation was lifted to GO-for-Part-A once these landed.

---

## 1. What this closes, precisely

Three things, sequenced:

- **Part A — format adoption.** Outrigger marshals every entry field through the JOSS-default
  `MarshalledInstance` constructor and has no DER path at all; Reggie has a DER path but it is
  config-gated default-off. Class-free filtering (and every other STD-006 §2.3 data-independence
  benefit) only works over `ATOMIC_DER` bytes, so this migration is the gating dependency for the
  entire filter effort — and it is a real migration, not a flag-flip, because entry matching is a
  raw byte-compare and both services persist marshalled bytes to disk (§2.3 below).
- **Part B — Outrigger filter integration.** The design and wiring to carry a CEL-shaped predicate
  (per `SOW-CEL-Filter-Format.md`) with a query, evaluate it per-candidate over schema-projected
  DER field values at Outrigger's match chokepoints — synchronous scan, blocking-query write-time
  fan-out, and notify/availability registration — with fail-closed semantics throughout.

- **Part C — matching-runtime modernisation.** Under JOSS the server could never look inside an
  entry without its class, so matching efficiency had a hard ceiling: hash-bucket equality over
  opaque bytes. Schema-visible DER removes that ceiling — the server can hold **typed, class-free
  views** of entry data. Part C exploits that: decode-once-at-write typed projections, ordered
  typed indexes for range predicates, write-path predicate indexing, and closing the existing
  full-scan gaps. Without Part C, Part B is correct but every filtered query is a full scan with
  per-candidate decode; Part C is what makes function search *fast*, not merely possible.

Reggie's *filter integration* (attribute predicates) is deliberately **not** here (§7): Part A
covers Reggie's format adoption only (born-DER default at instantiation, §3.5), so the substrate
is ready when that integration is scoped.

---

## 2. Verified ground truth (all trunk, 2026-07-20)

### 2.1 The projection substrate exists — nothing to invent

- `MarshalledInstanceRecord` (`jgdms-der/.../marshal/MarshalledInstanceRecord.java:67-70`)
  unconditionally embeds `schemaBytes` (Merkle-chained per-class `AtomicSerialSchemaRecord`, each
  an ordered `wireName`/`wireType` field list) beside `payloadBytes`; scalars encode as native DER
  primitives (`ObjectCodec.encodeValue`, `.../object/ObjectCodec.java:~1027/1032`).
- Class-free decode exists: `ObjectCodec.decodeToFieldMap` (`ObjectCodec.java:892`) —
  `className → (fieldName → value)`, no `Class.forName`, no constructor, no `check(GetArg)`;
  exercised by `Std006ConformanceTest`.
- Format negotiation exists: `net.jini.core.constraint.MarshallingFormat` (`ATOMIC_DER`, `JOSS`
  constants), enforced by `MarshalledInstance.requiredFormat`/`chooseMarshalFactory`
  (`net/jini/io/MarshalledInstance.java:588-632`), codec resolved via
  `ServiceLoader<MarshalFactoryProvider>` (`:562-571`).

### 2.2 Entry marshalling today

- **Outrigger:** `EntryRep` builds every field with `new MarshalledInstance(fieldValue)` — the
  JOSS default — at `outrigger-dl/.../proxy/EntryRep.java:338` (reflection path) and `:406`
  (`@SerialEntry` path). No constraints-aware constructor call, no **entry-field** format-selection
  plumbing exists in outrigger — but format-selection plumbing does exist elsewhere in the same
  codebase: `StorableReference.java:140-155` and `JoinStateManager.java:558-586` already do
  `ATOMIC_DER`-hardcoded, dual-read (JOSS-fallback) marshalling for other object kinds. A1/A2
  should study this existing dual-read prior art rather than design the mechanism from scratch.
- **Reggie:** `EntryRep.marshal(val, useDer)` (`reggie-dl/.../proxy/EntryRep.java:195-200`)
  already chooses `ATOMIC_DER` vs legacy per the `useDer` flag; `Util.requiresDerFormat`
  (`.../proxy/Util.java:61-71`) derives it **per client↔server relationship** from the proxy's
  method constraints; server-side config `useDerForEntries` **defaults to `Boolean.FALSE`**
  (`RegistrarImpl.java:5262-5263`, threaded at `:3779`, `:3798-3799`, `:5472`).

### 2.3 The load-bearing hazard: matching is a raw byte-compare, and the bytes are persisted

- `MarshalledInstance.equals`/`hashCode` are `final` and compare/hash **`payloadBytes` only**,
  explicitly ignoring `payloadFormat`/`schemaBytes` (`MarshalledInstance.java:914-925`, `:937`;
  javadoc `:174-192`). `fullyEquals` adds only the codebase annotation (`:878-884`).
- Outrigger template matching is `values[f].equals(...)` per non-wildcard field
  (`EntryRep.matches`, `EntryRep.java:916-940`); `EntryFieldIndex` buckets candidates on
  `MarshalledInstance.hashCode()` (`EntryHandle.hashForField`, `EntryHandle.java:248-254`;
  `EntryFieldIndex.java:111,139,198`) and documents that its correctness **rests on**
  "equals ⇒ hash-equal" (`:59-68`). Reggie's `matchEntry` is the same byte-compare via
  `MarshalledWrapper.equals` (`reggie-dl/.../proxy/EntryRep.java:369-379`,
  `MarshalledWrapper.java:321-325`).
- **Consequence (test-confirmed):** a JOSS and a DER encoding of the *same value* have different
  `payloadBytes` → unequal, different index buckets — cross-format template/entry pairs
  **silently never match**, no exception, no fallback, no reconciliation code anywhere.
  `EntryRepDerFormatTest.mixingFormatsBreaksEqualsAndMatchEntry`
  (`reggie-dl/src/test/.../EntryRepDerFormatTest.java:177-204`) is the canonical statement.
- **Both services persist the bytes.** Outrigger: `EntryRep.store` writes the
  `MarshalledInstance[]` into the snaplogstore blob (`EntryRep.java:1011-1030`,
  `snaplogstore/BaseObject.java:45-56`). Reggie: `takeSnapshot` persists `SvcReg`/`Item`
  attribute `EntryRep`s (`RegistrarImpl.java:6357-6387`). A mid-life format flip therefore
  creates a **mixed-format store** exhibiting the silent-mismatch failure against half of it.
  *(This is exactly the failure the ratified born-immutable format now **prevents by
  construction** — §3 item 5: format is fixed at instantiation and a mid-life flip is refused —
  not managed. This section documents **why** mixing must be prevented; it is not a description of
  a state any JGDMS 4.0.0+ store can enter.)*
- **Server-side re-marshal of JOSS-encoded bytes is off the table by design.** Converting stored
  JOSS bytes to DER requires deserializing the entry — which the server must never do (STD-009
  §9.2's opaque-entry / confused-deputy discipline). Any migration strategy that says "the server
  converts old JOSS-encoded entries" is rejected at the door. **This prohibition is scoped to JOSS
  bytes specifically**: a class-free, schema-driven DER→DER re-encode (via
  `ObjectCodec.decodeToFieldMap`/`encodeValue`, no deserialization) is possible in principle and is
  relevant to Part C's schema-evolution story, but it does not help JOSS migration — the server
  still cannot get from opaque JOSS bytes to a schema without deserializing.
- **Board precedent — corrected citation.** `SOW-RemoteEvent-Source-DER-Encoding.md` is **not**
  precedent here: its original fix (wrapping `RemoteEvent.source` in a hard-coded-format
  `MarshalledInstance`) was withdrawn for encodability (it failed its own `Integer`-source
  acceptance test) and deserialization-downgrade reasons (a wire-controlled `payloadFormat`
  JOSS fallback), and that document explicitly states no byte-level comparison of
  `RemoteEvent`/`EventID`/`source` exists anywhere in the platform. It is a precedent for
  DER-encodability constraints and for the JOSS-downgrade hazard shape (both relevant elsewhere
  in this SOW), but **not** for the byte-compare-mismatch hazard this section actually turns on:
  Template and stored entry must be produced under the **same negotiated format**; dual-read
  (`payloadFormat` dispatch on decode) fixes *decode* of old records but **not** equals/hash-bucket
  matching.
- **The upside, stated plainly:** DER is canonical — same value ⇒ same bytes regardless of
  sender, which JOSS never guaranteed for entry matching (impl-dependent serialization could
  yield unequal bytes for `.equals` values). Migration *improves* the byte-matching contract
  (board guidance G1's "Entry byte-matching" canonicity site) in addition to enabling filters.

### 2.4 Outrigger matching architecture — the chokepoints (for Part B)

Every match in every path funnels through **`EntryRep.matches(EntryRep)`**
(`EntryRep.java:916`). The concrete sites:

- **Synchronous scan (read/take):** `OutriggerServerImpl.getMatch` (`:2406`) → `find` (`:2697`)
  → per-subclass `EntryHolder.hasMatch` (`EntryHolder.java:138`): `EntryFieldIndex.candidates`
  narrows (`:154`), quick-reject hash mask (`:172`), then **`tmpl.matches(rep)` at `:177`**,
  then `confirmAvailabilityWithTxn` (`:180`). Multi-template `take(EntryRep[])` goes via
  `ContinuingQuery` with its own `tmpl.matches(handle.rep())` at **`EntryHolder.java:818`**
  (no field index on this path, `:772-779`), transactional visibility confirmed only afterward at
  `:791`.
- **New confused-deputy vector Part B introduces (HIGH, security).** In both shapes above, the
  predicate would evaluate *before* the candidate's transactional visibility to the caller is
  confirmed — `tmpl.matches(rep)` at `:177`/`:818` runs, then `confirmAvailabilityWithTxn` at
  `:180`/`:791`. Today's byte-equality template can only confirm "this exact value is present,"
  which requires the caller to already know the value. A CEL comparison/range predicate evaluated
  at that pre-confirmation point lets a non-participant caller learn an *ordered* fact (`<`, `>`,
  a `contains` boundary) about another principal's **uncommitted, exclusively-locked** entry
  field — a binary-search disclosure of an in-flight value. This is qualitatively worse than
  today's exact-value-required byte-equality leak and is new to Part B; it is not present in Part
  A's byte-compare matching, which has no ordering semantics to exploit. See §3.2 for the fix and
  §5 B3 for the acceptance criterion.
- **Sixth chokepoint (previously missing from this list):** `OutriggerServerImpl.java:3607`
  (`IteratorImpl.nextReps`) — the `JavaSpaceAdmin.contents()` admin paging iterator, distinct from
  `ContinuingQuery`/`MatchSet` — already calls `tmpl.matches(reps[i])` and would silently **not**
  apply a filter unless B3 explicitly wires it. This list should not be trusted as closed; see §5
  B3's re-audit requirement.
- **Blocking queries (write-time fan-out):** unmatched queries register watchers keyed by
  template (`templates.add(watcher, tmpl)`, `OutriggerServerImpl.java:2578`); the write path
  records a transition (`:1430-1431`) which a dedicated journal thread fans out:
  `OperationJournal.run` (`OperationJournal.java:422`) → `TransitionWatchers.allMatches`
  (`TransitionWatchers.java:134`) → `WatchersForTemplateClass.collectInterested` →
  **`handle.matches(rep)` at `WatchersForTemplateClass.java:113`** (`TemplateHandle.matches`,
  `TemplateHandle.java:89`). Catch-up replays at `OutriggerServerImpl.java:2593` (singleton)
  and `:2185` (multi-take) also call `matches`.
- **Notify / availability events:** registrations are `TransitionWatcher`s stored in
  `TemplateHandle`s (`notify` `:1659-1707`; `registerForAvailabilityEvent` `:1729-1802`) and
  ride the **same** write-time fan-out; per-watcher semantics (`visibilityOnly`, `ifExists`)
  live in `isInterested`/`process` (`TransitionWatcher.java:168`, abstract — implemented in
  `StorableAvailabilityWatcher`/`TransactableAvailabilityWatcher`, not the
  `AvailabilityRegistrationWatcher.java:209-224` `ifExists` watcher family it was previously
  conflated with), which do **not** re-run `matches` — enforcement must sit where `matches` is
  called.
- **Class hierarchy:** entries are held per exact class (`EntryHolderSet.java:41`), subclass
  matching is the server's `types.subTypes` loop (`OutriggerServerImpl.java:2708-2725`,
  `TypeTree.java:195`); field offsets align across the hierarchy via `EntryRep.FieldComparator`
  (super-first, alphabetical — `EntryRep.java:967-984`).
- **Wire seam for a filter:** `EntryRep` is `@AtomicSerial` with an explicit
  `serialForm()`/`serialize()` (`EntryRep.java:461-482`); an optional filter field on `EntryRep`
  travels through **every** existing method signature (`OutriggerServer` already passes
  `EntryRep`/`EntryRep[]` everywhere) with no interface change. There is **no** existing
  pluggable match SPI; `EntryFieldIndex` is documented as a candidate narrower only —
  `EntryRep.matches` remains the arbiter (`EntryFieldIndex.java:38-69`).
- **Version-skew hazard in this seam.** An `EntryRep` filter field added as an optional
  `@AtomicSerial` field is **fail-open** under version skew: an old server silently drops the
  unknown field (documented STD-006 §11.8 behavior — unrequested fields are stored-not-accessed),
  so a new client's filter silently **vanishes** and the query runs unfiltered, returning *more*
  than the predicate asked for — a false-positive-direction failure §3.2's fail-closed rule does
  not cover, since that rule only contemplates decode/format failure on the *candidate* side, not
  disappearance of the filter itself. The `JavaSpace05` batch-parameter alternative fails
  **loudly** instead: an old server lacks the overload, so dispatch fails outright. B1 must decide
  this as a first-class question — see §5 B1.

### 2.5 Outrigger internals survey (for Part C)

- **Entry storage:** per-class `EntryHolder.content` is a `ConcurrentLinkedQueue<EntryHandle>`
  (`EntryHolder.java:50`; the custom `FastList` was removed by Peter in 2013, commit
  `11ce40943`). Removal is `content.remove(this)` — an **O(n) scan per entry removal**
  (`BaseHandle.java:66`), real cost under take-heavy churn.
- **Index coverage gap (explicit in-code follow-up note):** `EntryFieldIndex` narrows only the
  single-template `hasMatch` hot path; `ContinuingQuery` — the path under `contents()`/MatchSet
  and multi-template take — **full-scans the queue** (`EntryHolder.java:773-778`). `contents()`
  is a live weakly-consistent cursor over that queue (`ContentsQuery`,
  `OutriggerServerImpl.java:2744-3010`; `contentsIterator = content.iterator()`,
  `EntryHolder.java:709`), not a snapshot.
- **Derived-data precedent:** `EntryHandle` already computes and stores a packed quick-reject
  hash at add time (`EntryHandle.java:180-207`), and `EntryRep.shareWith` reference-shares
  class-level metadata across entries of a class (`EntryRep.java:144-149`) — both are the
  existing idiom Part C's typed projections extend.
- **Write-time fan-out:** `OperationJournal` is a **single serial thread** over a hand-rolled,
  **unbounded** linked queue with no backpressure (`OperationJournal.java:353-357`, `:422`); it
  dies on an unexpected `RuntimeException` (`:466-480`) — a single point of failure for all
  event delivery. Its ordinal **total order is load-bearing**: the
  `TransitionIterator`/`watcherRegistered` protocol that prevents a query's initial scan and its
  watcher registration from missing an in-between transition is sound *because* processing is
  serial (`:34-49`). Any parallelisation must be proven against that protocol.
- **Schema duplication under DER (measured against the real code):** each entry field is its own
  `MarshalledInstance`; under `ATOMIC_DER` each would carry its **own duplicate-content**
  `schemaBytes` array — `SchemaGenerator`'s `ClassValue` cache dedupes the *parsed* chain per
  class (`SchemaGenerator.java:194-203`) but `MarshalledInstanceRecord.fromChain` re-encodes a
  fresh `byte[]` per marshal (`MarshalledInstanceRecord.java:139-148`) and nothing interns the
  stored copies: 1000 entries × F fields ⇒ 1000×F byte-identical arrays. The
  `InMemorySchemaRegistry` (`ConcurrentHashMap` keyed by digest,
  `InMemorySchemaRegistry.java:71`) exists but is not wired into storage.
- **Watcher storage:** `TemplateHandle` uses a concurrent set + `ReentrantReadWriteLock`
  (`TemplateHandle.java:51-59`); a scaling optimization was explicitly deferred as "probably
  never very large" (`:40-49`) — an assumption filtered registrations may invalidate.

---

## 3. Design positions taken here (and what stays open)

1. **Sequencing: A before B.** Class-free projection needs schema-bearing bytes; a filter meeting
   a JOSS entry cannot evaluate. Part B lands behind Part A's Outrigger migration.
2. **Fail-closed filter semantics (fixed here, per STD-009 §8's 2026-07-20 note):** a candidate
   whose bytes fail canonical decode, whose format is not `ATOMIC_DER`, or whose schema lacks a
   field the predicate references ⇒ **no-match**. Filter verdicts are pre-selection only —
   `check(GetArg)` never ran, so the taking client still performs full gated deserialization;
   filter false-positives cost bandwidth, never integrity. **New rule, added by board review:**
   filter-predicate evaluation MUST NOT run against a candidate whose transactional visibility to
   the caller is unconfirmed — evaluation is gated on (or re-checked after)
   `confirmAvailabilityWithTxn`/`handle.canPerform` returning `true`. Without this, a CEL
   predicate evaluated ahead of transactional confirmation (as `tmpl.matches` is today at every
   chokepoint, §2.4) discloses an ordered comparison fact about another principal's uncommitted,
   exclusively-locked value — a confused-deputy channel plain byte-equality matching never had
   reason to guard, because equality-only matching cannot leak an ordering. This is a NEW hazard
   Part B introduces, not present in Part A's byte-compare matching (see §2.4).
3. **The projection reader is the existing fail-closed codec** (schemaDigest verified, canonical
   rejection, size bounds) — never a second lenient scanner (G1). Laziness (decode only
   predicate-touched fields by TLV skipping) is an optimization *inside* that codec, B2.
4. **Filter narrows after template.** The template's byte-equality fields (and the field index)
   keep doing their job; the predicate refines the survivors at the `matches` chokepoints. A
   pure-wildcard template with a filter is a legal full-scan query — cost containment is B4's
   problem, not a reason to forbid it.
5. **Migration strategy — RATIFIED (Peter, 2026-07-20): born-immutable per-deployment format;
   no in-place migration of a running store.** A space/registrar's marshalling format is fixed at
   **instantiation** and immutable for the life of that instance — a new (JGDMS 4.0.0+) space is
   `ATOMIC_DER` from birth; existing JOSS deployments keep running **unchanged on prior JGDMS
   versions** and are never migrated in place. A DER space registers **only** with a DER Reggie: a
   DER djinn (DER Reggie + DER spaces + DER-capable clients) and a legacy JOSS djinn are distinct
   deployments that do not mix. "Migration" is therefore **redeployment at the operator's pace**
   (stand up new DER instances, retire old JOSS ones, whole-djinn), never an in-store format
   conversion — or, if a durable space's persisted state must survive the upgrade, **offline
   conversion via the sanctioned A5 bridge** (§4 A5), never a live in-store conversion and no
   change to this section's redeployment default for every other case. Reggie needs no such
   bridge at all — see §4 A5 for why.

   **This decision dissolves, rather than solves, §2.3's mixed-store hazard.** Because a store is
   never mixed — every entry, template, and persisted snapshot in a given space is that space's
   single born format — the entire coexistence problem (dual-format marshalling, drain windows,
   lease-drain convergence, mixed-snapshot recovery) **does not arise**. §2.3's analysis survives
   only as the *justification* for immutability (why mixing must be prevented), not as machinery
   to build. The three strategies the earlier draft weighed — lease-drain (also independently
   unsound: renewal never re-marshals — `OutriggerServerImpl.renew`/`renewServiceLeaseInt` only
   touch expiry, `SvcReg.item` is `final`, and the registrar's own `Long.MAX_VALUE` self-lease
   never drains), transitional dual-format, cold-start — are all **withdrawn**; none is built.

   **Mechanism (reuses existing plumbing).** The space's exported proxy advertises its format as a
   `MarshallingFormat` requirement in its method constraints; every client of that space inherits
   it, so templates (`SpaceProxy2.repFor`) and entries marshal uniformly in that one format — the
   "space-wide epoch," fixed at birth. A format-mismatched client (e.g. a JOSS-only client reaching
   a DER space) **fails loudly** via the existing `chooseMarshalFactory`
   `UnsupportedConstraintException`, never a silent no-match. **Two guards A1/A2 must add:** (i) a
   service MUST refuse any attempt to change format on an existing populated store (fail-closed —
   immutability is enforced, not merely assumed); (ii) recovery reads a snapshot in the store's own
   single format and MUST refuse a snapshot whose format contradicts the instance's configuration.
   Guard (ii) refuses a JOSS snapshot at a born-DER instance **by design** — a live server must
   never deserialize JOSS (§9.2) — and A5 (§4 A5) is the sanctioned **offline** bridge for a
   durable Outrigger's persisted state that must survive an upgrade: complementary to this guard,
   not in tension with it, since A5 runs entirely outside the guard's view and hands the new
   instance only already-DER bytes that pass the guard cleanly.
6. **DER-encodability is a real adoption constraint A1 must surface, not paper over.** A JOSS
   `MarshalledInstance` accepts any `Serializable` field value; the DER path requires
   `@AtomicSerial`/scalar/substituted types and rejects raw `Object`-typed content
   (`SchemaGenerator.toWireType` — the exact failure `SOW-RemoteEvent-Source-DER-Encoding.md`
   root-caused). An entry class with non-DER-encodable field values must fail marshalling
   **loudly at write time** under `ATOMIC_DER` — never silently fall back per-field to JOSS
   (per-field format mixing would recreate §2.3's hazard inside a single entry). **Blast radius,
   full survey (2026-07-20, supersedes the earlier 4/26 estimate).** An exhaustive pass over
   every `net.jini.core.entry.Entry` implementer in the repo found **exactly two shipped fields
   that fail today**, both in `net.jini.lookup.entry`: `Status.severity` (typed `StatusType`, a
   plain non-`@AtomicSerial` `Serializable` value class) and `UIDescriptor.factory` (typed
   `java.rmi.MarshalledObject`, which the DER serializer registry deliberately DEFERS for
   unresolved security/canonicality reasons). The earlier estimate's other named cases were
   wrong and are corrected: `ServiceTypes.serviceType` (`Class`-typed) is **encodable** — `Class`
   became a supported wire type in commit `415e81570`, 2026-07-18; `RemoteMatch.aRI` and
   `PayloadEntry.payload` are **QA-test fixtures, not shipped**, and neither statically fails (a
   `Remote`/interface field is a polymorphic `@AtomicSerial` slot, a value-dependent runtime
   check; `PayloadEntry.payload`'s type is deliberately `@AtomicSerial`). **Per-case fix sizing:**
   `Status.severity` is an **A1 mechanical fix** — retrofit `@AtomicSerial` onto `StatusType`
   (a closed three-instance value class, no independent persisted form, `Status`'s public field
   type unchanged). `UIDescriptor.factory` is a **compatibility-gated decision, not mechanical**:
   either resolve the platform-wide `MarshalledObject` DER deferral (a larger unit with its own
   security review) or narrow the shipped public field type to a DER-safe carrier
   (e.g. `MarshalledInstance`) — a serialized-form-committed break A1 must flag, not silently
   attempt. So A1's Entry-fix scope is one mechanical retrofit plus one flagged decision, not a
   broad survey-and-repair — but the survey step stays in scope so the two known cases don't
   surprise the migration, and so any Entry types added since are re-checked.
7. **Read-path union shim ruled out.** A tempting alternative — match JOSS-vs-JOSS and DER-vs-DER
   separately per query and union the results — is rejected: it adds nothing over today's
   same-format matching (§2.3), since bridging JOSS↔DER at the value level still needs either
   forbidden deserialization or the deferred value-level-matching redesign (§7 non-goal). It also
   does not partition cleanly at N-to-M scale: format/template pairing is not a clean
   per-relationship split once a store has many concurrent writers —
   `EntryRepDerFormatTest`'s own comment notes mixed-format is "the normal case mid-migration,
   since `JoinManager` registers with every discovered lookup service concurrently," which
   reinforces the epoch decision now ratified (§3.5 / §9.2 resolved) — and which born-immutability
   secures by construction: a store's clients all inherit its one born format, so no such
   concurrent mix arises within a space.

Open (recorded in §9, decided during the tasks): the client-facing filter API shape (B1);
CEL field-name resolution against STD-006 §3.9's **per-class namespaces** across an entry
hierarchy (B1); filter-applicability key — `className` vs `schemaDigest` (B1); write-path cost
bounds (B4). *(The per-relationship-vs-space-wide-epoch question formerly listed here is now
**resolved** — a space-wide epoch fixed at instantiation, §3.5 / §9.2.)*

---

## 4. Part A — `ATOMIC_DER` entry migration

| Task | Deliverable |
|------|-------------|
| **A1** · Outrigger DER marshal path + format selection | Constraints-aware marshalling in `EntryRep` (both the reflection path `:338` and the `@SerialEntry` path `:406`) that marshals in **the space's own configured format** — advertised via the exported proxy's `MarshallingFormat` constraint and inherited uniformly by every client of that space (§3.5) — reusing the `MarshallingFormat`/`chooseMarshalFactory` plumbing; **no** per-relationship format derivation and **no** dual-format path. Includes the proxy-side template path (`SpaceProxy2.repFor`) so templates are produced in that **same single** born format as the entries they match. **Guard (i) in scope (§3.5):** the marshal path MUST refuse any attempt to change format on an existing populated store — immutability enforced, not assumed. Plus the loud-failure rule for non-DER-encodable field values (§3.6). **Additional marshal sites** (missing from earlier drafts): Outrigger `SpaceProxy2.java:549,699` (handbacks), `AvailabilityRegistrationWatcher.java:406`, `EventRegistrationWatcher.java:207`; Reggie `Item.java:243-246`, `EventReg.serialize`/`writeObject`. **Handback resolution (2026-07-20, implementation + review):** the four Outrigger handback sites **stay opaque JOSS by nature and are correctly left unchanged** — each re-wraps an already-serialized `java.rmi.MarshalledObject` (the legacy `JavaSpace`/pre-`JavaSpace05` event-delivery API), which carries no format to thread and cannot be re-marshalled without deserializing (forbidden); handbacks are event-delivery payload, never byte-matched, so no format-consistency requirement applies. They are out-of-scope by construction, not unfinished work. Most materially, `RegistrarImpl.marshalAttributes` (`:4447-4456`) and `marshalLocators` (`:4492-4501`) are both JOSS-only today and both called from inside `takeSnapshot` (`:6368-6369`) — the persistence path §2.3 already cites — so they belong in A4's single-format recovery tests, not silently assumed covered by A3's config flip. **Also in scope:** the DER-encodability fix for the two shipped failing fields the full survey found (§3.6) — `Status.severity` (mechanical `@AtomicSerial` retrofit on `StatusType`) and `UIDescriptor.factory` (`MarshalledObject`, a flagged compatibility-gated decision, not a mechanical fix). |
| **A2** · Born-immutable format design + guards | The format-at-instantiation config surface per service (format fixed at instantiation, immutable for the instance's life, §3.5); the exported-proxy `MarshallingFormat` constraint advertisement that makes that format the space-wide epoch every client inherits; the **two immutability guards** — (i) refuse an in-place format change on a populated store, (ii) refuse a snapshot on recovery whose format contradicts the instance's configuration; the **DER space registers only with a DER Reggie** deployment rule; and operator-facing guidance that "migration" = **redeployment** (stand up new DER instances, retire old JOSS ones — never an in-place flip). Plus `useDerForEntries` documentation (currently undocumented outside code). The withdrawn coexistence strategies (lease-drain, transitional dual-format, cold-start) and their unsoundness are recorded in §3 item 5 and are **not** built. Decision doc + config/recovery changes, board-reviewed. |
| **A3** · Reggie born-DER default + client-reach | A new (JGDMS 4.0.0+) Reggie is configured `ATOMIC_DER` **at instantiation** — the new-deployment default is DER — **not** an in-place flip of a running registrar's `useDerForEntries`; the JOSS path remains only for running as, or interoperating with, prior-version djinns. **Scope correction (accurate, reframed):** `useDerForEntries` as a runtime knob governs only the registrar's own self-attributes/self-registration marshalling — an ordinary client's service-registration attribute format is decided entirely client-side by the deployed proxy's method constraints (`Util.requiresDerFormat`, invoked only client-side in `RegistrarProxy.java`/`Registration.java`) — but under born-immutability that format is set **at instantiation**, not flipped mid-life. **Client-reach caveat:** a DER Reggie serves only DER-capable clients; a JOSS-only client uses the prior-version djinn — the intended deployment boundary (§3.5), not a gap. Under born-immutability the lever is simply **which version you deploy**, not an in-place migration. Align `Util.requiresDerFormat` docs; release-note the operational sequence. Ties to §9.2 (now resolved: a space-wide format epoch fixed at instantiation). |
| **A4** · Cross-format regression + persistence tests | Extend the `EntryRepDerFormatTest` pattern to Outrigger (matching, `EntryFieldIndex` bucketing, `hasMatch`/`ContinuingQuery`/watcher paths); **single-format** recovery tests for both services (a store is born-complete in one format, §3.5 — there is no mixed store to recover); **guard tests** asserting the two immutability guards fire — (i) a format-change attempt on a populated store is refused, (ii) a snapshot whose format contradicts the instance config is refused on recovery; adversarial probe: same logical value in both formats reaches a store, assert the *documented* fail-loud behavior — a format-mismatched client raises `UnsupportedConstraintException` via `chooseMarshalFactory`, never a silent no-match. Recovery tests must also cover the additional marshal sites named in A1 (handbacks, watcher registration paths, and Reggie's `marshalAttributes`/`marshalLocators` inside `takeSnapshot`) in the store's single born format, not just the primary entry-field path. |
| **A5** · JOSS→DER snapshot migration utility **[SCOPED — build on demand, Peter 2026-07-20]** | **FOLLOW-ON, separable from A1–A4, not on the critical path.** A standalone, offline, operator-run tool that converts a durable Outrigger's persisted JOSS snaplogstore to DER, so a new born-DER instance can load it through the normal recovery path — passing guard (ii) (§3 item 5), since the produced snapshot's format already matches the new instance's configuration. The sanctioned bridge for the one case born-immutability otherwise strands: a **durable** Outrigger (a `JavaSpace` used as a persistent store) upgrading to DER, whose persisted JOSS state would otherwise be lost. Reggie needs no such tool — service registrations self-heal via `JoinManager` re-registration on re-discovery — and a durable space has no auto-rewrite analogue. Full design in the subsection immediately below. **Gated:** build only if durable-space use is a supported requirement; otherwise the documented upgrade path is cold-start + re-registration (§3 item 5) and A5 stays unbuilt. |

### A5 — JOSS→DER snapshot migration utility (design, SCOPED — build on demand, Peter 2026-07-20)

**What it is.** A standalone, offline, operator-run tool that converts a durable Outrigger's
persisted JOSS snaplogstore to DER so a new born-DER instance can load it through the normal
recovery path, passing guard (ii) (§3 item 5) cleanly, since the produced snapshot's format
already matches the new instance's configuration. FOLLOW-ON, separable from A1–A4, **not on the
critical path**; built only if durable-space use is a supported requirement (see "Gating" below).

- **Why it exists at all.** Redeployment (§3 item 5's ratified migration strategy) costs Reggie
  nothing: service registrations are soft state that self-heals via `JoinManager` re-registration
  once a client rediscovers the new DER lookup service — no persisted registration needs to, or
  does, survive a redeploy. A **durable** Outrigger — a `JavaSpace` run as a persistent store
  rather than a transient one — has no such auto-rewrite analogue: its persisted JOSS entries are
  the operator's data, and stand-up-new/retire-old redeployment as ratified would simply strand
  it. A5 is the sanctioned bridge for that one case.
- **Isolation is the whole point.** The one dangerous step — deserializing JOSS (runs the entry's
  `readObject`, i.e. arbitrary code, and requires the entry classes on the classpath) — happens in
  a SEPARATE PROCESS that never shares a JVM with the production DER Outrigger. The live server
  never loads an entry class, never runs a foreign `readObject`, never sees a JOSS byte; it
  ingests only the tool's DER output, validated via `@AtomicSerial` exactly like any client-written
  DER entry. This keeps 100% of the deserialization surface out of the server — the §9.2
  confused-deputy / class-availability exposure that makes live JOSS-loading and server-side
  re-marshal forbidden (§2.3) is exactly what this isolation avoids reintroducing.
- **Why it's correct — DER canonicity.** Outrigger matches by byte-comparing `MarshalledInstance`
  payloads (§2.3), so a converted entry is only useful if its DER bytes are identical to what a
  fresh DER client would produce for the same value. DER guarantees exactly that — one canonical
  encoding per value — so deserialize-then-re-marshal-via-the-DER-codec yields the same bytes any
  DER writer produces, and converted entries match new DER clients' templates. Canonical DER is
  what makes JOSS→DER migration well-defined; JOSS (non-canonical, implementation-serialized)
  never had this property. Note the asymmetry with Part C's schema-evolution story (§2.3, §9.6):
  JOSS→DER is fundamentally class-requiring — no class-free path exists — unlike a DER→DER schema
  transcription, which can go via `decodeToFieldMap`/`encodeValue` with no deserialization at all.
- **The "can't convert" set equals the "can't store in DER" set.** An entry whose fields aren't
  DER-encodable (e.g. `UIDescriptor.factory`'s `MarshalledObject`, §3 item 6) can't be converted —
  but couldn't live in a DER space anyway, converted or not. The tool rejects/flags such entries
  **up front**, before conversion starts, so the operator learns before committing to the
  migration — never a silent drop.
- **One-time class dependency.** This is the last time the entry classes are needed for this
  data; once it's DER, future migrations are class-free (STD-006 §2.3). The conversion is the
  final payment for permanent data-independence.
- **Isolation strength is a spectrum — operator/implementer choice.** Minimum viable: a standalone
  CLI running in the operator's own JVM — the entry classes being deserialized are the operator's
  own deployed types, not untrusted downloaded code, so the meaningful boundary is process/
  lifecycle separation from the production server, not sandboxing from the operator. Defense-in-
  depth: run the deserialization step in a quarantined subprocess reusing the role-neutral
  attested-worker/sidecar pattern from the smart-proxy-isolation work (STD-009 §8.6) —
  architecturally the same "quarantine dangerous deserialization away from the server" shape,
  cheap to reuse given it already exists, and likely overkill for operator-owned classes, but it
  composes for free if chosen.
- **Round-trip self-check.** For each converted entry, re-decode the DER output and assert
  value-equality with the JOSS input — positive confidence the migration didn't silently corrupt
  data, not merely an absence of exceptions.
- **Operator flow.** In the old JOSS deployment: force a snapshot (collapse the operation log) and
  shut down cleanly. Run the converter offline against the static snapshot, with the deployment's
  entry jars on its classpath. Load the resulting DER snaplogstore into the new born-DER instance,
  which then recovers it through the normal single-format recovery path (§3 item 5, guard (ii)) —
  passing, since the tool's output already matches the new instance's configured format.
- **Gating.** Build only if durable-space use is a supported requirement; otherwise the documented
  upgrade path is cold-start + re-registration (§3 item 5) and A5 stays unbuilt, scoped-but-not-
  built, per Peter's 2026-07-20 guidance: "we can scope it, and build it if there is demand."

## 5. Part B — Outrigger filter integration

| Task | Deliverable |
|------|-------------|
| **B1** · API + wire design | How a client expresses (template, predicate): the filter as an **optional field on `EntryRep`** (the §2.4 wire seam — no signature changes) vs a parallel collection on the `JavaSpace05` batch ops; the client-facing API surface for attaching a CEL expression to a query; filter-applicability key (`className` vs `schemaDigest`); **field-name resolution semantics** across the entry hierarchy given STD-006 §3.9 per-class namespaces and `FieldComparator` ordering (qualified names? leaf-shadows-super? reject collisions?). **Failure-direction decision (version skew, §2.4):** name both wire-seam options explicitly — filter-as-`EntryRep`-field (silent false-positive under skew: filter vanishes, query runs unfiltered against an old server) vs. filter-as-`JavaSpace05`-batch-parameter (loud break: dispatch fails outright against an old server lacking the overload). If the `EntryRep`-field route is chosen, require a server-declared capability check so a new client can detect absent filter support rather than silently getting an unfiltered result; for a security-relevant feature, loud-break is the safer default absent that check. **Dual-use hazard:** `EntryRep` serves both templates and stored (written) entries; nothing today rejects a populated filter field on a write-path `EntryRep` — B1 must specify the field is template-only and reject (or ignore-and-flag) one found on a write. `EntryRep` also has a **second** serialization path — `store()`/`restore()` (`EntryRep.java:1011-1054` → `snaplogstore/BaseObject.java:45-56`) — distinct from `serialForm()`/`serialize()` used on the wire; the filter field must **not** be persisted there. **Observability requirement:** specify a client- or operator-visible signal distinguishing a Part-B fail-closed no-match (candidate undecodable/wrong-format/missing-field ⇒ silently excluded per §3.2) from a genuine "no entries match" result — distinct from A2's drain-window visibility metric (open question §9.3), which covers Part-A format-visibility only and says nothing about filtered queries. Board-reviewed design memo; this is the task that must not be rushed. |
| **B2** · Lazy field projector | A projection reader over the existing codec that decodes only predicate-referenced fields (DER TLV lengths make skipping cheap), full fail-closed behavior inherited (schemaDigest verification, canonical rejection, bounds). Extends `decodeToFieldMap`'s machinery; no second parser (§3.3). |
| **B3** · Chokepoint wiring | **First step: re-derive the complete `EntryRep.matches`/`TemplateHandle.matches` caller set exhaustively against trunk rather than trusting the list below as closed** — a missed call site is a silent fail-open bypass, not a benign gap (§2.4). Predicate enforcement at the verified sites: `EntryHolder.java:177` and `:818` (synchronous paths), `WatchersForTemplateClass.java:113` / `TemplateHandle.matches` (write-time fan-out for blocking queries, notify, availability), catch-up replays `OutriggerServerImpl.java:2593`/`:2185`, and `OutriggerServerImpl.java:3607` (`IteratorImpl.nextReps` — the `JavaSpaceAdmin.contents()` admin paging iterator, distinct from `ContinuingQuery`/MatchSet). Fail-closed rules of §3.2 applied identically at every site; `EntryFieldIndex` untouched (remains a narrower). **Acceptance criterion at every chokepoint:** predicate evaluation is gated on / re-checked after transactional-visibility confirmation (`confirmAvailabilityWithTxn`/`handle.canPerform`) — never evaluated against a transactionally-unconfirmed candidate (§3.2/§2.4). The write-time fan-out chokepoint (`WatchersForTemplateClass.java:113`) needs the equivalent check for uncommitted writes — a write inside an open transaction must not have its predicate-match result disclosed to non-participant watchers before it commits. Depends on `SOW-CEL-Filter-Format.md` T1–T3 (grammar, DER encoding, Java evaluator). |
| **B4** · Write-path cost containment | Filter evaluation now runs on the `OperationJournal` thread for every pending filtered registration per write — a new DoS surface distinct from the evaluator's own bounded-by-construction guarantee (that bounds *one* evaluation; this bounds *N registrations × writes*). Static expression-size/cost caps at registration time (CEL cost-estimation precedent), per-registration and per-principal limits, metering. **C3 is a structural complement, not a resolution** — caps bound the damage, C3's shared projections and predicate indexing shrink the work itself, but **only for registrations with sargable conjuncts**: C3(c)'s predicate indexing helps only expressions of the `field op constant` shape C2 can extract; a predicate with none (e.g. `strContains`) falls back to full per-write evaluation bounded only by B4's static caps. State this sargable-only scope explicitly rather than treating B4 as resolved by C3; open question §9.4 (admission control beyond static caps) remains live precisely for the non-sargable case. Cross-check against `SOW-BAE-Timing-Sidechannel-Denial.md` §1b and the CEL SOW's T7 isolation-posture outcome once that lands. |
| **B5** · Integration tests + adversarial probes | End-to-end over real read/take/notify/availability/blocking paths; crafted malformed expressions and payloads at every chokepoint; hierarchy field-resolution edge cases (shadowed names, subclass-only fields, empty/degenerate templates per G11); mixed-format candidates asserting fail-closed no-match; conformance against the CEL SOW's T5 corpus where applicable. Tests must also assert B1's observability signal fires correctly (distinguishing fail-closed no-match from genuine no-match), alongside the mixed-format fail-closed assertions already scoped. |

## 6. Part C — Matching-runtime modernisation (Outrigger)

The unlock is architectural, not incremental: JOSS forced the server to treat entry data as
opaque bytes, so the only possible index was byte-hash equality. Schema-visible DER lets the
server hold **typed views of entry data it never loads classes for** — everything below is that
one capability, applied at four sites. All of Part C presupposes Part A (a JOSS or undecodable
entry simply has no projection and is invisible to filtered queries, per §3.2's fail-closed
rule — never an error).

| Task | Deliverable |
|------|-------------|
| **C1** · Write-time typed projection | Decode each `ATOMIC_DER` entry's field projection **once at write**, class-free and fail-closed, and keep the typed values on the `EntryHandle` beside the opaque bytes — extending the existing derived-data idiom (the add-time quick-reject hash, `EntryHandle.java:180-207`). Filters then evaluate over in-memory values with zero query-path decode. Decide eager-vs-lazy (lazy-with-memoize is the likely answer for entries no filter ever touches); strict memory accounting (projection size is sender-influenced; codec bounds inherited); projection derived **only** from the validated canonical bytes, never a second decode path (G1/G12). **Acceptance criterion:** the write-time projection must be rebuilt on snaplogstore recovery/restore, not only on live writes. Placing the decode inside the shared `EntryHandle` constructor (matching the existing quick-reject-hash precedent, computed the same way) gets this for free, since recovery reconstructs `EntryHandle`s through the same constructor; hooking the decode into the write RPC body instead would leave recovered entries unprojected and silently invisible to filters after a restart. |
| **C2** · Typed ordered indexes + sargable planning | Per-field ordered indexes (`ConcurrentSkipListMap`-style) over projected numeric/string values, so range predicates narrow candidates instead of full-scanning. Query side: extract sargable conjuncts (`field op constant`) from the CEL expression, choose the most selective index — the same heuristic `EntryFieldIndex.candidates` already applies to equality buckets (`EntryFieldIndex.java:203-207`) — then run the residual predicate per survivor. Keep planning deliberately primitive (single best index, no cost model); index-field selection policy is §9.5. `EntryFieldIndex` stays for byte-equality template fields, now strengthened by DER canonicity (same value ⇒ same bytes across senders). |
| **C3** · Write-path fan-out modernisation | Four graded pieces: (a) **one projection per written entry**, shared across every interested filter evaluation (amortizes C1's decode across N watchers); (b) **expression interning** — identical registered expressions evaluate once; (c) **predicate indexing** — index registered sargable conditions (interval structures) so a write touches only the watchers its values can satisfy: the symmetric dual of C2 (index the queries, not just the data), and the structural fix behind B4's caps; (d) **journal hygiene** — bound the `OperationJournal` queue with backpressure and supervise/restart the thread, but keep processing **serial**: the ordinal total order is load-bearing (§2.5) and parallelisation is out of scope without a proof (§9.7). |
| **C4** · Index the `ContinuingQuery` path + entry-store structure | Extend `EntryFieldIndex` (and C2's typed indexes once present) to `contents()`/MatchSet and multi-template take — closing the explicit in-code follow-up (`EntryHolder.java:773-778`). Independent of filters and valuable to all bulk operations; the equality half has no Part-C dependency and can land early. Also replace `EntryHolder.content` per the researched recommendation below (**C4a**): a sequence-keyed `ConcurrentSkipListMap` — O(n)-scan removal becomes O(log n), FIFO iteration preserved, and leased cursors become stateless. Benchmark before/after on a mid-queue-take-heavy workload to confirm, not assume, the win (G13). |
| **C5** · Schema interning in storage | Intern `schemaBytes` by digest for stored entries (wire `InMemorySchemaRegistry` or an equivalent digest-keyed pool into entry storage and the snaplogstore path), collapsing §2.5's 1000×F duplicate arrays to one per schema. Cheap, mechanical, and can ride Part A rather than waiting for the rest of Part C. The stronger **EntryRep-v2** layout (one whole-entry DER record, per-field TLV offsets, byte-equality preserved as canonical TLV-slice comparison) is deliberately **not** scoped here — §9.6. |

### C4a — Entry-store structure decision (researched 2026-07-20)

**Chosen: `ConcurrentSkipListMap<Long, EntryHandle>` keyed by a per-holder monotonic sequence
(`AtomicLong`), the assigned key stored on the `EntryHandle` at insert** — replacing
`EntryHolder.content`'s `ConcurrentLinkedQueue`. Rationale, from a dedicated literature and
library survey plus workload analysis:

- **When O(n) actually bites (workload analysis).** `ConcurrentLinkedQueue.remove(Object)` scans
  from the head, so the classic FIFO producer/consumer pattern removes at/near the head cheaply —
  which is why the 2013 `FastList`→CLQ change (`11ce40943`) never hurt. Interior removal is the
  expensive case: selective templates, expiry reaping, and above all **filtered takes**, which
  make mid-population removal the common case. The structure swap is therefore coupled to the
  filter work, not free-floating hygiene.
- **What the seq-keyed skip list buys:** removal by handle-held key is **O(log n)** lock-free
  (`content.remove(handle.seq, handle)` — the same keep-the-removal-key-on-the-handle idiom
  `idMap`'s `Uuid` cookie already uses); monotonic keys make iteration order = insertion order,
  preserving today's FIFO fairness/starvation behavior (the JavaSpaces spec doesn't require it,
  but silently changing it is not this task's call); iterators are weakly consistent and
  documented never to throw `ConcurrentModificationException` — safe for leased long-lived
  cursors. **Bonus:** `tailMap(lastSeq, false)` turns `ContentsQuery`'s pinned live
  `contentsIterator` (`EntryHolder.java:709`) into a **stateless cursor** — a resumable `long`
  instead of a held iterator object, a strictly better shape for a leased cross-call query.
- **Open risk requiring a fix before implementation: the `tailMap(lastSeq, false)` resume-atomicity
  gap.** The stateless-cursor resume assumes `AtomicLong` key-assignment and the corresponding
  `put()` into the map are effectively atomic together. If they are not — a writer reserves a
  lower sequence number but its `put()` completes *after* a reader has already advanced its
  watermark past that key — the entry is hidden from that cursor **permanently**, strictly worse
  than the current held-iterator behavior (a live `ConcurrentLinkedQueue` iterator cannot lose a
  linked-ahead entry once inserted). This must be closed before implementation, either with a
  two-phase publish (reserve-then-visible-commit, cursors skip not-yet-committed keys) or a
  contiguous-watermark design (the resumable watermark advances only to the highest *contiguous*
  committed sequence, not the highest assigned one).
- **Alternatives surveyed and rejected:** `ConcurrentLinkedDeque`/`LinkedTransferQueue` — same
  O(n) `remove(Object)`, no exposed node handles (confirmed against JDK source). JCTools/Agrona —
  throughput queues, no interior handle-delete. Caffeine's and Netty's intrusive doubly-linked
  lists — true O(1) unlink but **not concurrent on their own** (single-writer drain funnel /
  `synchronized` arena), reintroducing a write-path serialization point. Per-class
  `ConcurrentHashMap` keyed by `Uuid` — O(1) but unordered (abandons FIFO fairness and stateless
  cursor resume). Bw-tree / ART-OLC / Masstree — persistent-index-class machinery, disproportionate
  TCB for a per-class in-memory set (C2's needs are met by `ConcurrentSkipListMap` too). Lock-free
  "bag" structures (Sundell et al., SPAA '11) — explicitly unordered.
- **The hand-rolled option is explicitly declined, on this codebase's own precedent.** A
  Sundell–Tsigas-style intrusive lock-free doubly-linked list (OPODIS '04/JPDC '08) is the only
  structure giving true O(1) delete-by-reference — and it is exactly the `FastList` shape this
  codebase already built, hardened for years (RIVER-391, occasional-iterator-failure fixes), and
  deleted in 2013 for correctness simplicity. O(log n) from a JDK-maintained, Fraser-lineage
  structure (a 2024 survey, arXiv:2403.04582, confirms skip lists remain the practical concurrent
  ordered structure) beats O(1) from bespoke pointer-marking code this team must then own. Only
  revisit if C4's benchmark shows the skip list is an actual bottleneck.
- **Cost stated honestly:** skip-list nodes carry extra per-level index overhead vs. a
  singly-linked queue node, and add goes from O(1) to O(log n) — immaterial next to the
  O(n)→O(log n) removal win at realistic per-class populations, but the benchmark confirms.
- **Separate flag, surfaced by the survey (not scoped here):** `EntryHolder`/`EntryHandle`'s
  `synchronized(handle)` state-machine blocks pin carrier threads under virtual threads on
  pre-JEP-491 JDKs. None of the structures above require external `synchronized` for their own
  guarantees; the handle state machine is its own follow-up (§9.8).

**Virtual-threads / DirtyChai addendum (Peter's question, 2026-07-20: "JGDMS 4.0.0 deploys only
on DirtyChai; with virtual threads, is `FastList` relevant again?").** Verified against the local
DirtyChai checkout: **JDK 27 base** (`make/conf/version-numbers.conf`,
`DEFAULT_VERSION_FEATURE=27`), so JEP 491 (synchronized-without-pinning, JDK 24+) is present.
Consequences, which cut in both directions but net the same recommendation:

- **Pinning is moot on the only supported platform.** Neither `FastList`'s node-level
  `synchronized` removal nor the `EntryHandle` state machine pins carrier threads on DirtyChai.
  This removes the anti-`synchronized` argument entirely (and resolves §9.8) — but pinning was
  never the pro-`FastList` argument, so nothing swings toward revival on this ground. Post-491
  the comparison reverts to O(1) unlink + lower per-node memory vs. bespoke-TCB correctness
  risk, and the 2013 deletion reason is untouched by virtual threads.
- **The genuinely new virtual-thread consideration cuts *against* the old `FastList`.** Its
  documented memory invariant — deferred-unlink garbage bounded because "the number of nodes
  which remain in a list for a long time will be less than the number of threads in the VM" —
  was written for platform-thread counts (dozens–hundreds). Virtual threads make concurrent
  traverser count effectively unbounded (tens of thousands of blocked queries is the *point*),
  exploding that bound. A revival would therefore not be "restore the old code" but "design a
  new unlink discipline whose garbage bound is not thread-count-proportional" — i.e., the
  Sundell–Tsigas-class fallback already named above, with a new proof obligation added, not
  removed.
- **What virtual threads *do* change for this task:** mutator concurrency rises sharply, which
  makes CLQ's O(n) interior removal worse and makes the C4 benchmark's realism matter — it MUST
  run at virtual-thread-scale concurrency (thousands of concurrent filtered takes/blocked
  queries), not a dozen platform threads. `ConcurrentSkipListMap` remains fully non-blocking
  under 491-era semantics; monotonic-key insertion concentrates CAS contention at the tail, but
  CLQ's `offer` has the same tail hotspot, so it is not a differentiator.
- **Net: recommendation unchanged, fallback sharpened.** Seq-keyed `ConcurrentSkipListMap`
  first; if the virtual-thread-scale benchmark shows it as a real bottleneck, the fallback is a
  *modernized* intrusive list (VarHandle mark-bit à la Harris, GC-based reclamation, no
  `synchronized` needed, thread-count-independent unlink discipline) — potentially even homed as
  a DirtyChai platform utility under `au.zeus.jdk.*` given the fork-only deployment. But the
  CEL-interpreter lesson applies verbatim: "runs on our fork" does not shrink the correctness
  burden of bespoke pointer-marking code; it just relocates it.

*References:* Harris, DISC '01 (logical-delete mark bit — ancestor of CSLM's deletion); Fraser,
PhD 2004 (CSLM's skip-list basis); Sundell & Tsigas, OPODIS '04 (O(1) handle-delete DLL — the
declined option); Shalev & Shavit, JACM '06 (split-ordered lists — unordered, inapplicable);
arXiv:2403.04582 (2024 skip-list survey).

## 7. Non-goals

- **Not building the CEL primitive** — grammar, wire encoding, evaluators, conformance suite are
  `SOW-CEL-Filter-Format.md` T1–T8.
- **Not Reggie filter integration** (attribute-predicate pushdown wiring) — follows Part A's
  substrate and this SOW's B1 design precedent, as its own SOW. Reggie may later also want a
  Part-C-style typed view over attributes; not scoped here.
- **Not the EntryRep-v2 whole-entry storage layout** — C5 does the cheap interning half only;
  the full redesign is §9.6's own design pass (wire + persistence implications).
- **Not replacing byte-equality template matching with value-level matching.** Once entries are
  DER, value-level comparison (schema-projected, cross-schema-version) becomes *possible* and
  would lift the byte-brittleness of template matching itself — a real future direction, but a
  matching-semantics change with its own compatibility surface. Flag only.
- **Not parallelising the `OperationJournal`** — bounded/supervised yes (C3d), parallel no
  (§9.7).
- **Not the sidecar/isolation decision** — that remains the CEL SOW's T7; B4 consumes its answer.

## 8. Execution plan

Effort follows project convention; model tier follows Peter's 2026-07-20 cost-tiering guidance
(design/security review on the top-tier model; implementation on cheaper models against the
settled design; mechanical work cheapest).

| Task | Agent · effort | Model tier | Review | Depends |
|------|----------------|-----------|--------|---------|
| A1 | general-purpose · HIGH | sonnet (impl) | security-literate reviewer (top-tier): downgrade/format-negotiation surface | — |
| A2 | general-purpose · XHIGH | top-tier (design) | **parallel board** (migration correctness is the whole ballgame) | A1 draft |
| A3 | general-purpose · MEDIUM | haiku/sonnet | single reviewer | A2 |
| A4 | general-purpose · XHIGH (test-debug) | sonnet | single reviewer + adversarial spot-check | A1, A2 |
| B1 | general-purpose · MAX | top-tier (design) | **parallel board** (API + namespace semantics are permanent) | A1; CEL T1 |
| B2 | general-purpose · HIGH | sonnet (impl) | security-literate reviewer: must prove codec-fence inheritance (G1) | B1 |
| B3 | general-purpose · XHIGH | sonnet (impl) | **parallel board**: every chokepoint, fail-closed at each | B1, B2; CEL T1–T3; A1/A2 landed for Outrigger |
| B4 | general-purpose · XHIGH | top-tier (design) + sonnet (impl) | security-literate reviewer | B3 shape; CEL T7; C3 |
| B5 | general-purpose · XHIGH (test-debug) | sonnet | single reviewer + run-the-probes mandate (G13) | B3, B4 |
| C1 | general-purpose · HIGH | sonnet (impl) | security-literate reviewer: memory bounds, single-decode-path soundness (G1/G12) | A1, A2 landed |
| C2 | general-purpose · XHIGH | top-tier (planning design) + sonnet (impl) | security-literate reviewer + adversarial probes (index/arbiter agreement) | C1; B1 (predicate shape) |
| C3 | general-purpose · XHIGH | top-tier (design — ordering protocol) + sonnet (impl) | **parallel board**: the journal's ordinal-order protocol is load-bearing | C1; B3 shape |
| C4 | general-purpose · HIGH | sonnet | single reviewer | equality half: none; typed half: C2 |
| C5 | general-purpose · MEDIUM | haiku/sonnet | single reviewer | A1 |

**Sequencing:** A1 → A2 (overlapping), then A3/A4; B1 can start once A1's shape and CEL T1 exist;
B2 after B1; B3 gates on the CEL evaluator existing; B4/B5 close it out. **C4's equality half and
C5 are early riders** (no filter dependency — C5 can land with Part A, C4-equality any time);
C1 → C2/C3 follow Part A and inform B4's final calibration. Part A alone is independently
valuable (canonical matching bytes, data independence for stored entries, filter-readiness) and
should not wait for Part B or C. **Under born-immutability there is no incomplete-migration state
within a store** (§3.5): a store is born-complete in its single format, so a DER-born space
realizes Part A's value **immediately** — there is no homogeneity gap to close and no
completion-dependency caveat. Part B without Part C is correct but slow — acceptable for first
landing, not for the SOW's definition of done.

## 9. Open questions

1. **B1's field-name resolution rule** against STD-006 §3.9 per-class namespaces — the one place
   the CEL grammar meets the schema model with no precedent to copy. Candidate rules: leaf-first
   unqualified with reject-on-ambiguity; always-qualified (`ClassName.field`); or restrict
   predicates to the template's declared class's own namespace. Needs a worked example set.
2. **[RESOLVED — Peter, 2026-07-20 A2 ratification] Per-relationship format negotiation vs. a
   space-wide format epoch.** Resolved to a **space-wide epoch, fixed at instantiation and
   immutable** (§3.5); per-relationship negotiation is rejected. Rationale: a space is a shared
   store matched across principals, so format is a property of the store, not of any one
   client↔server relationship.
3. **[MOOT for Part A — resolved by elimination, 2026-07-20] Drain-window operator visibility.**
   Under born-immutability there is no drain window and no "old-format entries invisible to
   new-format templates" state within a store (§3.5), so the Part-A visibility question this
   asked does not arise. Part B's separate fail-closed **filter**-observability requirement — a
   signal distinguishing a fail-closed no-match from a genuine no-match — is unaffected by this
   and stands on its own under B1 (§5).
4. **Does B4 need admission control beyond static caps** (e.g. per-principal registered-filter
   budgets) once real workloads exist — revisit with deployment evidence, not guessed now.
5. **C2's index-field selection policy:** index every scalar field, a configured subset, or
   adaptively build indexes from observed query shapes? Adaptive is the modern answer but adds
   state and churn; start-configured-then-measure is the likely landing. Needs deployment
   evidence, not a guess.
6. **EntryRep v2 — the whole-entry DER record.** One record per entry (single schema chain,
   per-field TLVs at known offsets) instead of per-field `MarshalledInstance`s: kills §2.5's
   schema duplication outright, gives lazy projection for free, and — because DER is canonical —
   per-field byte-equality matching survives as TLV-slice comparison, wildcards stay positional.
   A genuine `EntryRep` redesign with wire and snaplogstore implications; deserves its own
   board-reviewed design pass after Part A proves the format plumbing. C5's interning is the
   cheap interim.
7. **Journal parallelisation.** Only admissible with a proof against the transition-ordering
   protocol (`OperationJournal.java:34-49`); sharding by entry class is **not** obviously sound
   (null-template and superclass watchers span classes). Until someone writes that proof, C3d
   keeps the thread serial — bounded and supervised, not parallel.
8. **[RESOLVED 2026-07-20] `synchronized(handle)` under virtual threads.** The
   `EntryHandle`/`EntryHolder` per-handle state machine uses `synchronized` blocks
   (`EntryHolder.java:315,564,838`), which pin carrier threads only on pre-JEP-491 JDKs.
   Verified: DirtyChai — JGDMS 4.0.0's only supported platform — is a **JDK 27 base**
   (`DirtyChai/make/conf/version-numbers.conf`, `DEFAULT_VERSION_FEATURE=27`), which includes
   JEP 491 (JDK 24+). No `ReentrantLock` migration needed; the handle state machine stays as-is.

---

*End of DRAFT. Investigation evidence gathered 2026-07-20; no production code written or modified
in producing this document.*
