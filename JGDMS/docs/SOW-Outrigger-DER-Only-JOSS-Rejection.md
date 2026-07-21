# Scope of Work — Outrigger DER-Only (JOSS Rejection)

- **Drafted:** 2026-07-21. **This revision:** post-board, consolidated three-seat fix list
  applied 2026-07-21.
- **Status:** DRAFT — board-reviewed 2026-07-21 (three adversarial seats: security/fail-closed/
  pre-deletion-audit, migration-correctness/scope/API-compat, performance; **all three
  SOUND-WITH-FIXES**, no UNSOUND-grade finding, the DER-only decision itself unchallenged).
  Consolidated fix list applied this revision; ready for implementation dispatch.
- **Origin:** Peter's 2026-07-21 decision: **Outrigger supports the `ATOMIC_DER` marshalling
  format only and rejects JOSS** — superseding (strengthening) the born-immutable
  *format-choice* regime of `SOW-Entry-ATOMIC-DER-Migration.md` §3.5 for Outrigger: there is
  no born-JOSS Outrigger in JGDMS 4.0.0 at all. Proxy and service code are **implementation
  code** (free to change); only the service API evolves, backward-compatibly.
- **Ratified (Peter, 2026-07-21, pre-board):**
  1. **Fail-loud posture throughout** — a JOSS-only or DER-incapable client fails with
     `UnsupportedConstraintException` (existing `chooseMarshalFactory` mechanism); never a
     silent no-match, never a silent fallback. Board addendum (seat 1's through-line):
     fail-loud is only realized when each new throw is also **fail-clean** — see §3 item 2.
  2. **DER-only includes the invocation layer**: `AtomicDerILFactory` is Outrigger's exporter
     invocation layer unconditionally — in the service default and in every QA transport
     config.
  3. **Deprecated `MarshalledObject` API overloads throw `UnsupportedOperationException`** —
     signatures retained for binary/source compatibility, behavior explicitly withdrawn.
- **Ratified (Peter, 2026-07-21, post-board round):**
  4. **Null-handback carve-out on decision 3** (seat 2 finding 1): a call to the deprecated
     MO overload with a **null** handback delegates to the MI path — nothing MO-shaped ever
     exists in a null call, so the withdrawal's spirit is intact and ~30 conformance classes
     binding the MO overload with null stay green. **Non-null MO → UOE.**
  5. **JOSS-payload handback reject at registration confirmed** (the seats conflicted; the
     security seat's argument prevailed): an MI handback with `payloadFormat != ATOMIC_DER`
     is loudly rejected. Rationale: the handback is wire-inbound, server-persisted, and
     later delivered to the listener — possibly a third party (e.g. a mercury mailbox) —
     whose `.get()` is an unguarded JOSS-downgrade vector; reject closes it at the source.
     Costs accepted: `JavaSpace`/`TupleSpace` javadoc corrected to show constraint-built DER
     MIs (`new MarshalledInstance(obj)` produces JOSS payload — the deprecation guidance
     must stop pointing at it bare); the predecessor SOW's opaque-handback ruling annotated
     superseded for Outrigger; placement pinned per §3 item 5.
  6. **Compression policy** (Peter's ruling on seat 3 finding 1): if compression is ever
     added to `AtomicDerILFactory`, it MUST be **scoped to MarshalledInstance content** and
     MUST NOT operate **over an encrypted connection** (compression-oracle /
     CRIME-class defence: attacker-influenced plaintext compressed under encryption leaks
     through ciphertext length). Consequence: the config sweep's silent `Compression.DEFLATE`
     drop on the TLS transports (`jsse`/`spiffe`) is **correct by this rule**, not a
     regression to restore. The structural fix for encrypted-transport wire bloat is
     stream-level schema dedup or EntryRep-v2 (§6.1), not compression. A bulk-response
     wire-size probe lands in U1c (§7) to quantify; compression support remains an optional
     platform follow-on admissible only with both defences (§9.4).
  7. **DER-native persistence SPI rewrite** (not the thin byte-container variant) — §4.
  8. **EntryRep-v2 stays deferred** — §6.1 (rationale reworded per seat 2 finding 6).
- **Companions:** `SOW-Entry-ATOMIC-DER-Migration.md` (its Outrigger half — A1/A2 — largely
  landed; **A3 (Reggie) is not landed** and is unaffected by this SOW; supersession notes
  added there this revision); `SOW-CEL-Filter-Format.md` / STD-011 (Part B consumer);
  `SOW-MarshalledObject-Migration-RemainingGroups.md` + `REVIEW-NOTES-MarshalledObject-Migration.md`
  (the Group B MI-overload program decisions 3/4 complete for Outrigger);
  `SOW-RemoteEvent-Source-DER-Encoding.md` (the `payloadFormat`-downgrade HIGH follow-up,
  §6.2); `SOW-Remaining-DER-ProxyTrust-Followups.md` items 4 and ProxyTrust Stage-2;
  `JGDMS-Board-Reviewer-Guidance.md` (review standard; §2.3's pre-deletion audit reflex
  governs this SOW: **no half-retirement of JOSS**).

---

## 1. What this closes, precisely

`SOW-Entry-ATOMIC-DER-Migration.md` Part A gave Outrigger a born-immutable format epoch —
a space is born `ATOMIC_DER` **or** JOSS and never changes. This SOW removes the second
option and retires every JOSS surface Outrigger still carries:

- the `useDerForEntries == false` branch and its `FALSE` default (wire + entry marshalling);
- the dual-read legacy-`MarshalledObject` acceptance paths (persistence + watchers);
- the `MarshalledObject` API overloads' behavior (→ UOE, null carve-out per decision 4);
- **the snaplogstore container format and the `Storable*` SPI — the last raw
  `ObjectInputStream`/`ObjectOutputStream` in Outrigger** (§4, the core remaining work);
- the two QA configs still exporting Outrigger over `BasicILFactory`.

Done means: `grep -r ObjectInputStream` over the three outrigger modules returns nothing,
and every rejection is loud **and clean** (no live endpoint, thread, lease, or durable
record survives a refusal — §3 items 2 and 5).

**Free wins this migration should claim, not merely neutrality (board seat 3, CONFIRMED):**
matching stays `hash` precheck + `Arrays.equals(payloadBytes)` with identical
per-comparison cost — but canonical DER makes `EntryFieldIndex` buckets and the add-time
quick-reject hash **deterministic cross-sender** (same value ⇒ same bytes regardless of
sender, including collection-valued fields where JOSS bytes could legitimately differ for
equal values), eliminating a spec-legal JOSS false-no-match class; and with one format the
mixed-format silent-no-match failure mode is structurally unreachable.

## 2. Verified ground truth (three inventory passes + three board verifications, trunk 2026-07-21)

### 2.1 Already landed (the DER-only decision arrives on prepared ground)

- `EntryRep` marshals per configured format: `outrigger-dl/.../proxy/EntryRep.java:355-357`
  (reflective) and `:436-438` (`@SerialEntry`); `@AtomicSerial` wire contract `:499-545`.
- Born-immutable epoch: `OutriggerServerImpl.entryFormat()` `:442-444` over immutable
  `useDerForEntries` (`:435`, default `FALSE` at `:1129-1130`).
- Exporter ternary `:1152-1157`: `AtomicDerILFactory` when DER, else `AtomicILFactory`;
  `BasicILFactory` imported (`:47`) but **dead**.
- Recovery format guard exists (`BornFormatGuardTest`, snaplogstore) — currently
  configuration-relative, not unconditional; and see §3 item 2 for its confirmed
  fail-open cleanup defect.
- Handback plumbing is MI end-to-end: `MarshalledObject` **never crosses the wire** — the
  deprecated `JavaSpace.notify` overload converts client-side (`SpaceProxy2.java:610-617`);
  the private protocol is MI-only (`OutriggerServer.java:349-352`, `:461-465`);
  `RemoteEvent`'s DER serial form carries only `miHandback`, java.io serialization
  hard-disabled. The Group B MI-overload API evolution (`TupleSpace`) is built.
- ~31 DER unit tests across the three modules (`EntryRepDerFormatTest`,
  `OutriggerSerialEntryRoundTripTest`, `EntryFormatGetArgCompatTest`,
  `ConstrainableSpaceProxy2FormatTest`, `JoinStateManagerDerFormatTest`,
  `StorableReferenceDerFormatTest`, `BornFormatGuardTest`).

### 2.2 Remaining JOSS surfaces (the deletion/rewrite list)

**Board note (seat 1):** this inventory is **representative, not closed** — the U1a
implementer MUST re-run the module-wide
`ObjectInputStream|ObjectOutputStream|Serializable|readObject|writeObject|MarshalledObject`
grep and reconcile against §1's done-criterion before declaring completion.

**Wire/config (Unit 1):**
- `useDerForEntries` JOSS branch + default (`OutriggerServerImpl.java:442-444`, `:1129-1130`,
  `:1152-1157`); dead `BasicILFactory` import `:47`.
- Dual-read legacy-MO acceptance: `StorableReference.java:151-155` (outrigger-service),
  `JoinStateManager.java:582-586`, watcher normalizations
  (`EventRegistrationWatcher.java:206-208`, `AvailabilityRegistrationWatcher.java:405-407`).
- Watcher MO constructors — **expanded per seat 1**: `EventRegistrationWatcher.java:129`,
  `AvailabilityRegistrationWatcher.java:142`, `TransactableAvailabilityWatcher.java:67-69`,
  `StorableAvailabilityWatcher.java:90-92`, plus the `TransactableEventWatcher`/
  `StorableEventWatcher` siblings. The base `handback` field is effectively `Object`-typed
  with runtime `instanceof` dispatch — the removal is a **field-type collapse to
  `MarshalledInstance` plus dispatch deletion**, not merely constructor removal.
- `OutriggerAvailabilityEvent.java:83-90` — a `@Deprecated public` MO-handback constructor
  (already dead: the watcher converts before constructing). Implementation code: **delete**.
- `ConstrainableSpaceProxy2.java:127` — stale `MarshalledObject.class` entry in the
  method-constraint mapping array once `notify(MO)` is withdrawn; clean up.
- MO API overloads: `SpaceProxy2.java:610-617` (notify), `:755-767`
  (registerForAvailabilityEvent — pre-existing missing null guard → NPE; mooted by the
  null carve-out, which routes null before any conversion).
- QA configs: `qa/harness/configs/{jeri,http}/outrigger/outrigger.config` hardcode
  `BasicILFactory` (jeri `:47-58`); `https`/`kerberos` configs carry stale `BasicILFactory`
  imports; every non-`der` config uses `AtomicILFactory` rather than `AtomicDerILFactory`.
- Deployer-override vector (seat 2, PLAUSIBLE): `OutriggerServerImpl.java:1158-1165` lets a
  deployer-supplied `serverExporter` replace the exporter wholesale — a silent JOSS-IL
  reintroduction path against decision 1. Document under §9.1; add a startup check where
  feasible (an arbitrary `Exporter` is hard to introspect — best-effort, documented).

**API stragglers (Unit 1):**
- `net.jini.space.InternalSpaceException` (`jgdms-lib-dl`) — plain-JOSS `Serializable`,
  no `@AtomicSerial`. Public API: evolve compatibly (add `@AtomicSerial`; JOSS fence per
  the verified `AvailabilityEvent` precedent — `writeObject`/`readObject`/`readObjectNoData`
  all throw `NotSerializableException`, `AvailabilityEvent.java:156-180`). **Board
  constraints (seats 1+2):** the exception **already crosses DER invocation layers today**
  via `ThrowableSerializer` (generic ctor-matching reconstruction) — U1b MUST verify
  serializer-vs-`@AtomicSerial` dispatch precedence leaves the existing wire form unchanged
  (serial-schema compat gate); and the fence blocks JOSS serialization **in any graph,
  including as a nested cause** — widen the release note accordingly, and probe the QA
  harness's own JOSS transit of failure causes (precedent: the txnmanager
  `CannotCommitException` collateral, fixed via QAConfig `serialPersistentFields`).
- `SnapshotRep` (`outrigger-dl`) — `Serializable` transitively via `Entry`, not
  `@AtomicSerial`. Board-verified impl-only (package-private, referenced only within
  outrigger-dl; a snapshot is JVM-local per spec): fix outright.

**Persistence (Unit 2 — the core):**
- SPI anchor: `StorableObject.store(ObjectOutputStream)`/`restore(ObjectInputStream)`
  (`outrigger-dl/.../proxy/StorableObject.java:43,52`; `StorableResource.java:33`).
- Implementers persisting through it: `EntryRep.store/restore` `:1071-1112`;
  `Txn.java:489-512`; `StorableReference.java:132-155` (`Externalizable`,
  outrigger-service); `JoinStateManager.java:543-623`; `StorableEventWatcher.java:132-150`;
  `StorableAvailabilityWatcher.java:184-207`.
- Container: `LogOutputFileImpl.java` (`ObjectOutputStream` at `:60,:148`, record writes
  `:220-386`), `LogInputFile.java:160-267`, `SnapshotFile.java:41-109`,
  `BackEnd.java:285-321` (snapshot read incl. persisted `entryFormat` marker), `:681-695`
  (write); `Serializable` disk records: `BaseObject` (`:37`, nested blob streams `:48,:60`,
  custom `readObject` `:78`), `ByteArrayWrapper:37`, `PendingTxn:41` (+`PendingOp`/`WriteOp`/
  `TakeOp`), `BackEnd.LastLog:715`, `Registration`, `Resource`.

### 2.3 QA surface (test-impact sizing — corrected per seat 2)

- ~330 QA tests exercise Outrigger (impl suites: matching 89, transaction 34, leasing 10,
  javaspace05 15, admin 11; conformance suite ~152 incl. 69 snapshot variants). Default
  service type **activatable/persistent** (`qaDefaults.properties:201-204`) → the 20
  `*Shutdown.td` tests + `NotifyLogTest` genuinely exercise snaplogstore recovery.
- **The "default flip converts the suite to DER" claim is only half automatic** (seat 2,
  CONFIRMED): the *entry-format* half propagates (no QA config pins `useDerForEntries`);
  the *invocation-layer* half does **not** — every config overrides the exporter, so
  decision 2's wire coverage arrives only with U1c's sweep. **U1a and U1c land as one
  unit**; between them, `jeri`/`http` would run DER entries over a JOSS IL or break.
- MO-handback call sites in QA — **the earlier "single site" claim was wrong as a breakage
  inventory** (seat 2, CONFIRMED): beyond `NotifyTestUtil.java:85-89`
  (`convertToMarshalledObject()` round-trip), the conformance suite's `space` field is
  typed `JavaSpace` (`conformance/JavaSpaceTest.java:62`), so ~30 conformance classes bind
  the MO overload **with null** (covered by decision 4's carve-out, no changes needed);
  non-null MO sites needing U1c rewrite: `JavaSpaceAuditor.notify` (`:411-438`, asserted
  round-trip via `MonitoredSpaceListener:258`) and `RegisterForAvailabilityEventTest05`
  (+ transaction variant; seven `new MarshalledObject("notUsedHere")` sites).

## 3. Unit 1 — wire-side flip and JOSS deletions

1. `entryFormat()` returns `ATOMIC_DER` unconditionally; delete the JOSS branch, the
   `useDerForEntries` config choice (config entry recognized-and-rejected loudly if set to
   anything but DER, or removed — implementer proposes at review), and the exporter
   ternary's `AtomicILFactory` arm. Remove the dead `BasicILFactory` import. Document the
   deployer `serverExporter` override vector (§2.2) and add the best-effort startup check.
2. **Recovery refusal becomes unconditional AND fail-clean (board seat 1 — CONFIRMED HIGH,
   the round's most serious finding).** Today `recoverEntryFormat` throws
   `IllegalStateException` (`OutriggerServerImpl.java:3787-3794`) from inside the startup
   `PrivilegedExceptionAction` — but the endpoint is **exported at `:799`** and
   `txnMonitor`/`starter` threads started (`:801-802`) **before** `setupStore` runs
   (`:813`), and an unchecked throw **bypasses the only cleanup/`unwindExporter` block**
   (`catch (PrivilegedActionException)`, `:898-951`), leaving a live JERI endpoint and
   running threads after a refusal — exactly the store-swap-downgrade case the guard
   exists to refuse. Required: (i) the guard throws a **checked** exception declared by the
   action (wrapped → caught → cleaned up), or cleanup moves to `finally`/`catch(RuntimeException)`;
   (ii) **move `exporter.export()` after successful store recovery** so a refused store
   never had a live endpoint; (iii) a guard test asserting no endpoint listens and no
   non-daemon threads survive a refused recovery (G13: run it). What already holds and must
   be preserved: the guard precedes `consumeLogs` (`BackEnd.java:184-190`), so a refused
   JOSS store is left pristine on disk for the A5 converter.
3. MO overloads (`SpaceProxy2` notify + registerForAvailabilityEvent): **null handback →
   delegate to the MI path; non-null → `UnsupportedOperationException`** (decision 4).
   `JavaSpace`/`JavaSpace05` MO overloads stay on the interface, `@Deprecated(forRemoval)`,
   javadoc documents the null-delegation + non-null throw.
4. Delete the six watcher MO constructors (§2.2's expanded list), collapse the base
   `handback` field to `MarshalledInstance`, delete all `instanceof MarshalledObject`
   dispatch; delete `OutriggerAvailabilityEvent`'s deprecated MO constructor; clean the
   `ConstrainableSpaceProxy2.java:127` mapping entry; delete the dual-read legacy-MO
   acceptance in `StorableReference` and `JoinStateManager` (write already DER-hardcoded
   at both sites; read becomes DER-only, reject otherwise).
5. Registration-time handback check (decision 5): MI with `payloadFormat != ATOMIC_DER` →
   loud reject. **Placement pinned (board seat 1 — CONFIRMED): the check MUST precede
   lease grant (`OutriggerServerImpl.java:1770`), `eventRegistrations` insert (`:1775`),
   template registration (`:1783`/`:1795`), and the durable `log.registerOp` write
   (`:1791`)** — and the equivalent points in `registerForAvailabilityEvent` (`:1805+`).
   A rejected registration leaves no lease, no watcher, no log record. Javadoc work rides
   here: `JavaSpace`/`TupleSpace` docs show constraint-built DER MIs
   (`new MarshalledInstance(obj, EMPTY_SET, new InvocationConstraints(ATOMIC_DER, null))`),
   never bare `new MarshalledInstance(obj)`.
6. API stragglers: `InternalSpaceException` → `@AtomicSerial` + JOSS fence, under §2.2's
   two board constraints (ThrowableSerializer dispatch-precedence verified by the
   serial-schema gate; release note covers nested-cause JOSS use; harness-transit probe).
   `SnapshotRep` → `@AtomicSerial`.
7. ProxyTrust Stage-2 rider: remove `getProxyTrustIterator()` from the 3 outrigger-dl
   proxies (files are open anyway; the shared machinery removal remains the Stage-2 SOW's).
8. QA: every `configs/*/outrigger/outrigger.config` exporter → `AtomicDerILFactory`
   (decision 2), including `jeri`/`http` (upgrade, not retire); remove stale
   `BasicILFactory` imports in `https`/`kerberos`. **The `Compression.DEFLATE` drop on
   `jsse`/`spiffe` is correct per decision 6** (no compression over encrypted
   connections) — record it in the configs as a comment, not silently. Rewrite
   `NotifyTestUtil` to constraint-built DER MI handbacks with content assertions; rewrite
   the non-null MO sites (`JavaSpaceAuditor`/`MonitoredSpaceListener`,
   `RegisterForAvailabilityEventTest05` + variant); add negative tests per §7; add the
   **bulk-response wire-size probe** (N-entry MatchSet batch, bytes on the wire, DER vs
   JOSS+DEFLATE baseline) that turns seat 3's estimated regression numbers into measured
   ones.

## 4. Unit 2 — persistence SPI + container rewrite (the core)

**Decision (7): DER-native rewrite.** Replace the `Storable*` SPI's
`store(ObjectOutputStream)`/`restore(ObjectInputStream)` signatures with DER-record-based
ones, and re-frame the snaplogstore container (log records + snapshot) as DER records,
eliminating `ObjectInputStream`/`ObjectOutputStream`/`Serializable` from all three modules.

**Pre-answered audit findings the design memo records as baseline (board seat 3 —
CONFIRMED, de-risking):** today's log has **no cross-record back-reference compression**
(`out.reset()` after every record, `LogOutputFileImpl.java:494`; fresh stream per file
`:148`) — a DER-record design loses nothing there; record framing is **already
container-level**, not OOS-level (own 4-byte length header + zero-sentinel + backfill +
4-byte alignment, `:442-513`, written directly to the `RandomAccessFile` outside the
object stream); snapshot inner content is **already per-object streams**
(`BaseObject.java:45-56` — fresh `ObjectOutputStream` per blob), so a DER snapshot with
digest-per-record + schema side table is strictly better than the status quo, not parity.

Design constraints for the implementing task (board-reviewed design first):
- **One codec** (G1): records encode/decode through the existing `jgdms-der` fail-closed
  machinery (`@AtomicSerial` serial forms / `MarshalledInstanceRecord` where a marshalled
  graph is the payload) — never a second lenient parser. Size/depth ceilings inherited.
- **Crash-atomicity is a WRITE-side responsibility (board seat 1 — CONFIRMED, the
  mislocated-responsibility finding):** the partial-tail tolerance is produced by
  `LogOutputFileImpl.flush`'s **deferred zero-length-header protocol** (`:428-495` — a
  record's header stays `0` until the record is fully written and fsync'd; the reader
  merely terminates on `updateLen == 0` and hard-fails on corruption, `LogInputFile.java:176-188`),
  plus the 4-byte alignment guaranteeing a header never spans a disk block (torn-write
  avoidance, `:480`). A rewrite reproducing only read-side truncation detection silently
  loses crash-safety. The DER framing MUST provide equivalent write-side atomicity —
  the recommended shape is **length-prefix + CRC-per-record**, which self-validates and
  reduces today's **two** fsyncs per committed op (`:439`, `:488` — the sentinel protocol's
  cost) to **one** (board seat 3: fsync dominates non-transactional write latency; this is
  a claimable improvement, made an acceptance criterion below).
- **Transaction durability contract preserved (seat 1):** `flush(txnId == null)`
  (`:239,:261,:279,:301`) defers sync for ops under an active transaction until prepare —
  "no durable commit until prepare," entangled with the header protocol above. The DER
  container must preserve it explicitly.
- **Blob fault-isolation is broader than entries (seat 1):** `BaseObject`'s nested-blob
  indirection decouples container parsing from domain-object reconstruction for
  **JoinState and Txn too** — an unloadable domain object fails at `restore()`, never
  breaking log parse. Preserve the property, not just the entry-blob instance of it.
- **Entry blob stays opaque to the container** so a future EntryRep-v2 layout change (§6.1)
  touches entry encoding only, never container framing again.
- **Fail-stop on persist failure preserved:** `LogOutputFileImpl.failed()` →
  `System.exit(-5)` (`:515-520`) is a deliberate contract; carry it forward.
- **Recovery is DER-only, fail-loud, fail-clean**: a JOSS-era store encountered at
  recovery → refuse with an operator-actionable message naming the A5 converter path,
  under Unit 1 item 2's cleanup discipline.
- **Recovery cost is O(distinct schemas), not O(records) (seat 3):** DER decode verifies
  the schema digest per record (`MarshalledInstanceRecord.java:371-382`); recovery MUST
  intern by digest (verify once per distinct schema, byte-compare thereafter) — which is
  also exactly C5's hook, confirming C5's disk half rides here naturally.
- **Performance acceptance criteria (seat 3 — gates, not afterthoughts):** (i) **capture
  JOSS baselines — log size, snapshot size, recovery time on a populated store — BEFORE
  U1a lands** (after the flip, producing a baseline means checking out old trunk: a real
  sequencing trap); (ii) log-record and snapshot size parity vs that baseline; (iii) one
  fsync per committed non-transactional op (or explicit justification); (iv) recovery-time
  parity on a populated store; (v) partial-tail/torn-record equivalence tests.
- **Txn/watcher/join-state records** migrate with their owners (`Txn`, both Storable
  watchers, `JoinStateManager`, `StorableReference` — the latter drops `Externalizable`).
- A5 converter design (`SOW-Entry-ATOMIC-DER-Migration.md` §4) updates to emit this
  container format; still build-on-demand.

## 5. Unit 3 — riders

- **C5 schema interning** — digest-keyed `schemaBytes` pool. **Split per seat 3:** the
  **disk half** rides Unit 2's container (the intern-by-digest recovery constraint above);
  the **heap half** — interning stored `MarshalledInstance.schemaBytes` and the per-MI
  `payloadFormat` String (~56 B heap each, never interned, `MarshalledInstance.java:238-239`) —
  depends only on U1a and **may be pulled forward** if a workload with `@AtomicSerial`-valued
  fields hits memory pressure first. Note (seat 3, correcting the predecessor's §2.5
  premise): `schemaBytes` duplication applies **only to `@AtomicSerial`-valued fields** —
  scalar/String/enum/byte[] field values take the empty-schema path
  (`DerMarshalInstanceOutput.java:197-202`), so typical entries carry no duplication and
  likely shrink on flip; no day-one cliff exists.
  **U3 reviewer instruction (seat 2):** check C5's storage wiring against Unit 2's
  opaque-blob constraint — interning must not make the container schema-aware.
- **Chain-bytes caching (seat 3 — cheapest system-wide win, platform-adjacent):**
  `SchemaGenerator` memoizes the *parsed* chain per class (`ClassValue`,
  `SchemaGenerator.java:194-203`) but `MarshalledInstanceRecord.fromChain` re-encodes
  fresh bytes on **every marshal** plus two defensive copies (`:139-140`, `:108-110`,
  `:175-177`). Cache the encoded chain bytes per class — cuts allocation on every DER
  marshal in the system (wire and disk). Small `jgdms-der` change, own review.
- **C4 equality-index half** (`EntryFieldIndex` to `ContinuingQuery`/`contents()` paths) —
  no filter dependency; DER-only strengthens its premise (deterministic buckets).
- **QA-level DER persistence assertion**: a harness test that writes entries, restarts the
  (persistent) service, and asserts recovery of DER-written snaplog state end-to-end —
  closing `SOW-Remaining-DER-ProxyTrust-Followups.md` item 4 for outrigger properly.

## 6. Deferred / flagged (not in scope)

### 6.1 EntryRep-v2 — deferred; rationale corrected per board (seats 2+3)

The whole-entry DER record (`SOW-Entry-ATOMIC-DER-Migration.md` §9.6) stays a separate,
later design pass. The "do it now to avoid paying migration twice" argument fails because
a later per-field→v2 migration is a **schema-chain-carrying DER→DER transcription** —
each v1 `MarshalledInstanceRecord`'s payload TLVs and `schemaBytes` chains carried
**verbatim** into the v2 layout: class-free, offline, mechanical, no deserialization.
(Seat 2's correction: a class-free *re-encode* of decoded values could NOT regenerate
`schemaBytes` for nested `@AtomicSerial` values — the claim holds only for verbatim
carriage, which is the actual mechanism.) **Recorded invariant for the v2 design pass:
v2's layout must remain derivable from v1's stored data (positional TLVs + per-field
chains — `EntryRep` persists no field names), else this deferral rationale lapses** —
a name-bearing v2 schema, which B1's CEL field-name resolution will pressure toward,
would need classes and must be designed against this constraint explicitly.

Scope honesty (seat 3): C5 covers **storage** duplication only. The **wire-level**
duplication (full schema chain per `@AtomicSerial` occurrence in bulk responses,
`DerObjectStreamCodec.java:410` — no per-stream interning) is untouched by C5 and is
fixed only by EntryRep-v2 or stream-level schema dedup — which, under decision 6's
no-compression-over-encryption rule, is also the *only* fix available on TLS transports.
This strengthens v2's eventual case; the deferral stands on the migration argument alone.
*(2026-07-21: the stream-level schema dedup is now scoped at Peter's direction —
`SOW-DER-Stream-Schema-Dedup.md`.)*

### 6.2 Platform-level items flagged for separate sequencing

- **Fail-closed `MarshalledInstance.get()` retrofit** (`getPayloadFormat()` +
  constraint-checked `get(...)`; ~18 consumer files; the known HIGH from
  `SOW-RemoteEvent-Source-DER-Encoding.md`). **Outrigger-local closure argument (seat 1 —
  the doc now demonstrates rather than defers, per G9):** the only *server-side* `.get()`
  consumers in the three modules are `JoinStateManager.java:587` and
  `StorableReference.java:107`, each fed exclusively by its own same-process write site,
  and both write sites hardcode `ATOMIC_DER` (`JoinStateManager.java:558`,
  `StorableReference.java:140`); entry-field MIs are never `.get()`'d server-side
  (`EntryRep.entry()` runs client-side only, plus two debug dumps). With the recovery
  guard refusing whole-JOSS stores, no server-side `.get()` can meet a JOSS payload in a
  born-DER store — the platform retrofit is defense-in-depth for Outrigger's server, and
  the **load-bearing gate for listeners** receiving delivered handbacks (decision 5
  closes the Outrigger-sourced instance of that vector at registration).
- **`RemoteEvent`'s deprecated MO constructor** silently drops its handback on the DER wire
  (sets `miHandback = null`; `serialize()` writes only `miHandback`) — candidate for the
  same UOE treatment; platform type, Peter's call separately.
- Part B (CEL filter pushdown) and Part C (matching-runtime modernisation) proceed per
  `SOW-Entry-ATOMIC-DER-Migration.md`, unchanged except Part B's "candidate whose format is
  not ATOMIC_DER" fail-closed rule becomes vacuous in a DER-only store.

## 7. Test plan summary

- **Two-halves conversion (corrected):** the default flip converts the *entry-format* half
  of the ~330-test suite automatically; the *invocation-layer* half arrives only with
  U1c's config sweep. Run the full suite **after U1a+U1c land together**.
- New negative tests (adversarial: run them, G13): non-null MO → UOE on both overloads;
  null MO-overload call → delegates, event delivered; JOSS-payload MI handback → rejected
  with **no lease, no watcher, no log record** (seat 1's placement pin, asserted);
  unconditional JOSS-snapshot refusal → **no live endpoint, no surviving non-daemon
  threads, store pristine on disk** (item 2's fail-clean guard test); DER-incapable
  client → `UnsupportedConstraintException`.
- **Wire-size probe** (U1c): N-entry MatchSet batch, bytes on the wire, DER vs
  JOSS+DEFLATE baseline — turns seat 3's estimates into measurements; informs §9.4.
- **Performance baselines captured BEFORE U1a** (seat 3's sequencing trap): JOSS log size,
  snapshot size, recovery time on a populated store — Unit 2's parity gates need them.
- Unit 2: container round-trip + corruption/partial-tail recovery parity + write-side
  crash-atomicity equivalence (kill-during-write) + txn deferred-durability semantics +
  recovery refusal of a JOSS-era store; extend `BornFormatGuardTest` to the unconditional
  rule.
- `NotifyTestUtil` rewrite (constraint-built DER MI handback, content assertion,
  survives-restart assertion); `JavaSpaceAuditor`/`RegisterForAvailabilityEventTest05`
  MO-site rewrites.
- `InternalSpaceException`: serial-schema gate on dispatch precedence; harness-transit
  probe (nested-cause JOSS collateral).

## 8. Execution plan

Effort per project convention; model tier per the 2026-07-20 cost-tiering guidance.
Orchestration: fresh implement → review gate; parallel board where marked. Every review
dispatch cites `JGDMS-Board-Reviewer-Guidance.md` in the initial brief.

| Task | Agent · effort | Model tier | Review | Depends |
|------|----------------|-----------|--------|---------|
| U0 — JOSS perf baselines (log/snapshot size, recovery time) **[DONE 2026-07-21: `U0-Outrigger-JOSS-Persistence-Baselines-2026-07-21.md` — DER *shrinks* simple entries (−13.3% log / −15.9% snapshot), custom `@AtomicSerial`-valued −4% (~120 B/entry schema carriage = C5's quantified target); write throughput format-invariant ~400 ops/s (fsync-dominated, confirming gate iii's one-fsync lever); recovery parity-or-better]** | general-purpose · LOW | haiku/sonnet | none (artifact archived with SOW) | — (MUST precede U1a) |
| U1a — format flip, deletions, UOE+carve-out, both fail-clean guards (items 1-5) | general-purpose · **HIGH** (raised per board: carries two security guards + startup reordering) | sonnet | security-literate reviewer (top-tier): fail-clean paths, export-after-recovery, placement pin | U0 |
| U1b — API stragglers + ProxyTrust rider (items 6-7) | general-purpose · MEDIUM | sonnet | single reviewer (serial-schema gate + compat notes) | — |
| U1c — QA config sweep, MO-site + NotifyTestUtil rewrites, negative tests, wire-size probe (item 8) | general-purpose · XHIGH (test-debug) | sonnet | single reviewer + run-the-probes (G13) | U1a, U1b (harness-transit collateral) |
| U2-design — persistence SPI + container design memo | general-purpose · XHIGH | top-tier | **parallel board** (write-side atomicity + txn durability are the review's spine) | U1a landed |
| U2-impl — SPI + container + owners migration | general-purpose · XHIGH | sonnet | **parallel board** | U2-design |
| U3 — C5 (both halves), chain-bytes caching, C4-equality, QA DER-recovery assertion | general-purpose · MEDIUM-HIGH | sonnet | single reviewer (+ C5 opaque-blob check per §5) | U2-impl (C5 disk); U1a (rest) |

**Sequencing:** U0 → U1a → (U1b ∥ …) → U1c → full-suite run → U2-design → U2-impl → U3.
**U1a and U1c are one landing unit** for suite-green purposes (§2.3). C5's heap half and
chain-bytes caching may be pulled forward on evidence. U1 makes the DER-only claim true on
the wire; U2 makes it true on disk; done means §1's grep test passes.

## 9. Open questions — RESOLVED (Peter, 2026-07-21)

1. **RESOLVED:** `useDerForEntries` config entry is **retained and reject-if-JOSS** — a
   config that sets it to anything but `ATOMIC_DER` fails loudly at startup; it is not
   silently removed. (Consistent with the fail-loud posture: a stale JOSS deployment
   config surfaces as an explicit refusal, never a silently ignored setting.) Residual
   implementer-proposal item at U1a review: the exact shape of the best-effort deployer
   `serverExporter` override check (§2.2).
2. **RESOLVED:** the `jeri`/`http` harness configs **stay, upgraded** (per item 8) —
   retained for testing prior to deployment, and **documented as not for deployment**
   (plaintext, non-secure transports). U1c adds that documentation to the configs
   themselves.
3. **RESOLVED:** actual `forRemoval` deletion of the MO API methods is **deferred to
   JGDMS 5.0**; UOE-with-null-carve-out is the 4.0.0 posture.
4. **Constraints ratified in sentiment (Peter, 2026-07-21); build remains gated on U1c's
   wire-size probe.** **Compression support in `AtomicDerILFactory`** — optional platform
   follow-on,
   admissible only under decision 6's two defences (MI-scoped; never over an encrypted
   connection). U1c's wire-size probe informs whether the plaintext-transport case is
   worth building; the encrypted-transport case is closed to compression by policy and
   waits on stream-level schema dedup / EntryRep-v2 (§6.1).
   **Design constraints (Peter's question + resolution, 2026-07-21): decompression
   without unmarshalling is possible and required.** Inflation is a pure octet
   transformation — no class resolution, no constructor, no deserialization gate — so a
   receiver recovers the exact canonical DER bytes and byte-matches/stores/digest-verifies
   without ever opening the envelope; the opaque-entry discipline is preserved. Two
   invariants any implementation MUST pin: (i) **canonical identity is always the
   uncompressed bytes** — DEFLATE is non-canonical (same value, encoder-dependent
   compressed bytes), so `equals`/`hashCode`/index buckets/digests/signatures operate
   only on inflated canonical DER; the receiving endpoint inflates at decode time (the
   same layer that verifies `schemaDigest`), and compression is a wire-transit encoding,
   never a storage or identity format — anything else recreates the mixed-format
   silent-no-match hazard; (ii) **streaming inflation bound, fail-closed** — compression
   inverts the size relationship (~1000:1 expansion possible), so DER size ceilings are
   enforced *during* inflation (stop at cap+1, reject), never inflate-fully-then-check
   (G10's interior fence applied to the decompressor). Corollary: `schemaDigest` is
   computed over uncompressed bytes, so compression is fully transparent to the trust
   chain.
   **CRIME-resistance clarification (Peter's question + resolution, 2026-07-21).** A
   *canonical* compressor (fully-pinned deterministic algorithm, including a pinned rule
   for *when* compression applies — the DETERMINISTIC CEL spec discipline) is achievable
   but does not defeat CRIME: the oracle is shared-context redundancy between
   attacker-influenced and secret plaintext observed through ciphertext length, and it
   leaks identically under a deterministic encoder. A DER-specific scheme IS
   CRIME-resistant by construction iff it removes only **structural, value-independent**
   redundancy: (i) no match/window ever spans a field-value boundary; (ii) values are
   never compressed — not even solo (a secret's compressed length leaks its entropy
   profile); (iii) dedup is by-reference over the public structural layer only (schema
   chains by digest, class-name/TLV-skeleton dictionaries). Output length then depends
   only on message shape — which the length side channel reveals regardless — never on
   secret content. Consequence: **stream-level schema dedup is admissible over encrypted
   connections** — decision 6's no-compression-over-encryption rule does not cover it,
   because it is not compression in the CRIME-relevant sense — and since the measured
   bulk-response bloat is almost entirely duplicated schema text, the structure-only
   scheme captures essentially the whole win. General-purpose (value-compressing)
   compression remains plaintext-transport-only, per decision 6.
   *(2026-07-21: the structure-only scheme is scoped as `SOW-DER-Stream-Schema-Dedup.md`
   at Peter's direction — its T4 measurement supersedes this item's remaining question.)*

---

*End of DRAFT (post-board revision). Board-reviewed 2026-07-21, three seats, all
SOUND-WITH-FIXES; consolidated fix list applied. No production code written or modified in
producing this document.*
