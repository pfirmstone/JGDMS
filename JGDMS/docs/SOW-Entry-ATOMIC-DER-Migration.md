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
  hard dependencies of B3); `SOW-RemoteEvent-Source-DER-Encoding.md` (the board-reviewed precedent
  for what NOT to do: hard-coding a format onto one side of a byte-compared value).

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
covers Reggie's format flip only, so the substrate is ready when that integration is scoped.

---

## 2. Verified ground truth (all trunk, 2026-07-20)

### 2.1 The projection substrate exists — nothing to invent

- `MarshalledInstanceRecord` (`jgdms-der/.../marshal/MarshalledInstanceRecord.java:67-70`)
  unconditionally embeds `schemaBytes` (Merkle-chained per-class `AtomicSerialSchemaRecord`, each
  an ordered `wireName`/`wireType` field list) beside `payloadBytes`; scalars encode as native DER
  primitives (`ObjectCodec.encodeValue`, `.../object/ObjectCodec.java:1066-1142`).
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
  (`@SerialEntry` path). No constraints-aware constructor call, no format-selection plumbing of
  any kind exists in outrigger.
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
- **Server-side re-marshal is off the table by design.** Converting stored JOSS bytes to DER
  requires deserializing the entry — which the server must never do (STD-009 §9.2's
  opaque-entry / confused-deputy discipline). Any migration strategy that says "the server
  converts old entries" is rejected at the door.
- **Board precedent:** `SOW-RemoteEvent-Source-DER-Encoding.md`'s original fix was withdrawn
  exactly because hard-coding a format onto one side of a byte-compared value breaks the
  comparison. Template and stored entry must be produced under the **same negotiated format**;
  dual-read (`payloadFormat` dispatch on decode) fixes *decode* of old records but **not**
  equals/hash-bucket matching.
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
  (no field index on this path, `:772-779`).
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
  live in `isInterested`/`process` (`AvailabilityRegistrationWatcher.java:209-224`), which do
  **not** re-run `matches` — enforcement must sit where `matches` is called.
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

### 2.5 Outrigger internals survey (for Part C)

- **Entry storage:** per-class `EntryHolder.content` is a `ConcurrentLinkedQueue<EntryHandle>`
  (`EntryHolder.java:50`; the custom `FastList` was removed by Peter in 2013, commit
  `11ce40943`). Removal is `content.remove(this)` — an **O(n) scan per entry removal**
  (`BaseHandle.java:65`), real cost under take-heavy churn.
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
   filter false-positives cost bandwidth, never integrity.
3. **The projection reader is the existing fail-closed codec** (schemaDigest verified, canonical
   rejection, size bounds) — never a second lenient scanner (G1). Laziness (decode only
   predicate-touched fields by TLV skipping) is an optimization *inside* that codec, B2.
4. **Filter narrows after template.** The template's byte-equality fields (and the field index)
   keep doing their job; the predicate refines the survivors at the `matches` chokepoints. A
   pure-wildcard template with a filter is a legal full-scan query — cost containment is B4's
   problem, not a reason to forbid it.
5. **Migration strategy decision is A2's deliverable, not presumed here.** The evidence (§2.3)
   admits: (a) coordinated djinn-wide flip + **lease-expiry drain** of old-format entries
   (entries and registrations are leased; old bytes age out naturally — with documented
   reduced-visibility semantics during the drain window); (b) transitional **dual-format
   marshalling** (client writes both encodings; matching uses whichever side pairs; storage and
   wire cost roughly double during transition); (c) per-deployment cold-start migration
   (drain/destroy stores at upgrade — brutal but honest for spaces used as transient
   coordination). Server-side conversion is excluded (§2.3). A2 picks, per service, with Peter.
6. **DER-encodability is a real adoption constraint A1 must surface, not paper over.** A JOSS
   `MarshalledInstance` accepts any `Serializable` field value; the DER path requires
   `@AtomicSerial`/scalar/substituted types and rejects raw `Object`-typed content
   (`SchemaGenerator.toWireType` — the exact failure `SOW-RemoteEvent-Source-DER-Encoding.md`
   root-caused). An entry class with non-DER-encodable field values must fail marshalling
   **loudly at write time** under `ATOMIC_DER` — never silently fall back per-field to JOSS
   (per-field format mixing would recreate §2.3's hazard inside a single entry).

Open (recorded in §9, decided during the tasks): the client-facing filter API shape (B1);
CEL field-name resolution against STD-006 §3.9's **per-class namespaces** across an entry
hierarchy (B1); filter-applicability key — `className` vs `schemaDigest` (B1); write-path cost
bounds (B4); whether Outrigger adopts Reggie's exact `requiresDerFormat` idiom or a simplified
space-wide format epoch (A1×A2 joint).

---

## 4. Part A — `ATOMIC_DER` entry migration

| Task | Deliverable |
|------|-------------|
| **A1** · Outrigger DER marshal path + format selection | Constraints-aware marshalling in `EntryRep` (both the reflection path `:338` and the `@SerialEntry` path `:406`), per-relationship format derivation mirroring Reggie's `Util.requiresDerFormat`, and the loud-failure rule for non-DER-encodable field values (§3.6). Includes the proxy-side template path (`SpaceProxy2.repFor`) so template and entry are produced under the same negotiated format — the §2.3 board precedent made normative. |
| **A2** · Coexistence & migration choreography | The strategy decision (§3.5) per service, including: recovery behavior over a mixed snaplogstore/snapshot, drain-window visibility semantics documented for operators, `useDerForEntries` documentation (currently undocumented outside code), and the flip plan for Reggie's default. Decision doc + config/recovery changes, board-reviewed. |
| **A3** · Reggie default flip | Flip `useDerForEntries` default per A2's plan; align `Util.requiresDerFormat` docs; release-note the operational sequence. Mechanical once A2 lands. |
| **A4** · Cross-format regression + persistence tests | Extend the `EntryRepDerFormatTest` pattern to Outrigger (matching, `EntryFieldIndex` bucketing, `hasMatch`/`ContinuingQuery`/watcher paths); mixed-store recovery tests for both services; adversarial probe: same logical value, both formats, assert the *documented* (not accidental) behavior on every path. |

## 5. Part B — Outrigger filter integration

| Task | Deliverable |
|------|-------------|
| **B1** · API + wire design | How a client expresses (template, predicate): the filter as an **optional field on `EntryRep`** (the §2.4 wire seam — no signature changes) vs a parallel collection on the `JavaSpace05` batch ops; the client-facing API surface for attaching a CEL expression to a query; filter-applicability key (`className` vs `schemaDigest`); **field-name resolution semantics** across the entry hierarchy given STD-006 §3.9 per-class namespaces and `FieldComparator` ordering (qualified names? leaf-shadows-super? reject collisions?). Board-reviewed design memo; this is the task that must not be rushed. |
| **B2** · Lazy field projector | A projection reader over the existing codec that decodes only predicate-referenced fields (DER TLV lengths make skipping cheap), full fail-closed behavior inherited (schemaDigest verification, canonical rejection, bounds). Extends `decodeToFieldMap`'s machinery; no second parser (§3.3). |
| **B3** · Chokepoint wiring | Predicate enforcement at the verified sites: `EntryHolder.java:177` and `:818` (synchronous paths), `WatchersForTemplateClass.java:113` / `TemplateHandle.matches` (write-time fan-out for blocking queries, notify, availability), catch-up replays `OutriggerServerImpl.java:2593`/`:2185`. Fail-closed rules of §3.2 applied identically at every site; `EntryFieldIndex` untouched (remains a narrower). Depends on `SOW-CEL-Filter-Format.md` T1–T3 (grammar, DER encoding, Java evaluator). |
| **B4** · Write-path cost containment | Filter evaluation now runs on the `OperationJournal` thread for every pending filtered registration per write — a new DoS surface distinct from the evaluator's own bounded-by-construction guarantee (that bounds *one* evaluation; this bounds *N registrations × writes*). Static expression-size/cost caps at registration time (CEL cost-estimation precedent), per-registration and per-principal limits, metering. **C3 is the structural complement** — caps bound the damage, C3's shared projections and predicate indexing shrink the work itself. Cross-check against `SOW-BAE-Timing-Sidechannel-Denial.md` §1b and the CEL SOW's T7 isolation-posture outcome once that lands. |
| **B5** · Integration tests + adversarial probes | End-to-end over real read/take/notify/availability/blocking paths; crafted malformed expressions and payloads at every chokepoint; hierarchy field-resolution edge cases (shadowed names, subclass-only fields, empty/degenerate templates per G11); mixed-format candidates asserting fail-closed no-match; conformance against the CEL SOW's T5 corpus where applicable. |

## 6. Part C — Matching-runtime modernisation (Outrigger)

The unlock is architectural, not incremental: JOSS forced the server to treat entry data as
opaque bytes, so the only possible index was byte-hash equality. Schema-visible DER lets the
server hold **typed views of entry data it never loads classes for** — everything below is that
one capability, applied at four sites. All of Part C presupposes Part A (a JOSS or undecodable
entry simply has no projection and is invisible to filtered queries, per §3.2's fail-closed
rule — never an error).

| Task | Deliverable |
|------|-------------|
| **C1** · Write-time typed projection | Decode each `ATOMIC_DER` entry's field projection **once at write**, class-free and fail-closed, and keep the typed values on the `EntryHandle` beside the opaque bytes — extending the existing derived-data idiom (the add-time quick-reject hash, `EntryHandle.java:180-207`). Filters then evaluate over in-memory values with zero query-path decode. Decide eager-vs-lazy (lazy-with-memoize is the likely answer for entries no filter ever touches); strict memory accounting (projection size is sender-influenced; codec bounds inherited); projection derived **only** from the validated canonical bytes, never a second decode path (G1/G12). |
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
valuable (canonical matching bytes, data independence for stored entries) and should not wait
for Part B or C. Part B without Part C is correct but slow — acceptable for first landing,
not for the SOW's definition of done.

## 9. Open questions

1. **B1's field-name resolution rule** against STD-006 §3.9 per-class namespaces — the one place
   the CEL grammar meets the schema model with no precedent to copy. Candidate rules: leaf-first
   unqualified with reject-on-ambiguity; always-qualified (`ClassName.field`); or restrict
   predicates to the template's declared class's own namespace. Needs a worked example set.
2. **Whether Outrigger adopts per-relationship format negotiation (Reggie's idiom) or a
   space-wide format epoch** — per-relationship is more flexible but means one space serving
   mixed-format writers indefinitely, which §2.3 shows is a matching-visibility partition;
   an epoch is cruder but converges. A1×A2 joint decision.
3. **Drain-window semantics** operators can live with (A2): how loudly to surface "old-format
   entries are invisible to new-format templates" — metric, log, admin query?
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
