# Design Memo — Outrigger CEL Filter Pushdown, Unit B3 (Confused-Deputy-Safe Server-Side Evaluation)

- **Status:** DESIGN pass on `feat/cel-filter-b3` (off trunk `4a5a2fd02`, reactor under `JGDMS/`). Peter has
  ratified the per-eval cost ceiling (§4, `[RATIFIED Peter 2026-07-26]`) and the subclass-candidate resolution
  rule (§5/§3, `[RATIFIED Peter 2026-07-26]`); board review of the remainder continues. **No implementation code
  in this unit** — this memo fixes the decisions that will outlive the B3 implementation.
- **Scope:** SOW `SOW-Entry-ATOMIC-DER-Migration.md` §5 Part B, unit **B3**: wire predicate **evaluation** at
  every match chokepoint, confused-deputy-safe, fail-closed, with a bounded per-candidate cost. B3 consumes
  the B1 admission seam (`FilterAdmission` → `CompiledFilter`) and the B2 class-free projection
  (`EntryProjection implements CandidateProjection`) and the `jgdms-cel` `Evaluator`. It adds **no** new wire
  format, **no** new client API; it turns the eight B1 `EVALUATION_NOT_WIRED` chokepoints into real,
  entitlement-gated evaluations.
- **Companions:** `DESIGN-Outrigger-CEL-Filter-B1.md` (esp. §4.1 unwired chokepoints, §6.1 OPTION-(b)
  applicability, §6.2 multi-template plurality); B2 `EntryProjection` (the projection B3 consumes);
  `JGDMS-Board-Reviewer-Guidance.md` (§2.2 adversarial: confused-deputy hunt, ungated-reconstruction door,
  decode-surface DoS; G2 outcome-vs-mechanism, G6 fail-closed-side-effect, G10/G13).

This memo records the decisions that outlive the implementation. Where B1 §6.1/§6.2 left a first-class
question open, the decision taken is stated here with its rationale; every place the B1 memo is ambiguous or a
design call had to be made is flagged **[DESIGN CALL]** or **[OPEN — BOARD]**.

---

## 0. The one-sentence job and the two invariants

**Job.** Given an admitted, verified `CompiledFilter` (a boolean CEL predicate) and a candidate entry the
caller has *matched by byte-equality template*, decide "does the predicate hold?" — **class-free, fail-closed,
and only against candidates the caller is transactionally entitled to see** — and let a predicate-true
candidate through, exclude a predicate-false or fail-closed candidate.

Two invariants govern every placement decision below. They are the mechanisms behind the B1 stated outcomes
(G2: a stated outcome is a claim; here is the enforced construct):

- **INV-1 (confused-deputy safety).** The predicate is a *client-supplied* program. It MUST NOT be executed by
  the server against any entry the requesting client is not already entitled to observe. The entitlement gate
  is `handle.canPerform(txn, op)` on the capture path and `transition.getTxn() == null || == the registrant's
  own txn` on the fan-out path. **Predicate evaluation happens strictly after that gate, never before.** A
  client learns *nothing* — not even a timing signal (§4) — about an entry it could not otherwise read.
- **INV-2 (no captured false-positive).** On a *consuming* op (take), a predicate-false candidate MUST NEVER be
  removed/locked. Outrigger has no un-take / undo primitive, so the evaluation is placed **inside the
  `synchronized(handle)` confirm window, after `canPerform`, before `grab`** — the one point where the entry is
  proven available-and-entitled but not yet captured. A predicate-false result there simply returns
  "not this one" and the scan continues; nothing was taken.

Everything else in this memo is the exhaustive application of INV-1 and INV-2 to every match site.

---

## 1. Chokepoint wiring — evaluate at EVERY match site, confused-deputy-safe placement

### 1.0 Why exhaustiveness is the whole game

Matching in Outrigger funnels through `EntryRep.matches()` (outrigger-dl `EntryRep.java:1021`, EntryRep-v2
positional byte-equality over slice bytes) and its one wrapper `TemplateHandle.matches()`
(`TemplateHandle.java:89`). **Missing a single caller is a silent fail-open bypass**: that path would return
byte-matched candidates the predicate never filtered, i.e. the exact "silently unfiltered query" hazard the
whole feature exists to prevent (B1 §1). So the enumeration below is the load-bearing artifact of B3 and MUST
be complete. There are **six** `matches()` call sites. Five are reachable by a filtered op and each gets an
evaluation; the sixth is a legacy admin path that **no** filtered overload reaches and MUST be *proven*
unreachable, not merely left alone (§1.5).

### 1.1 The two placement archetypes

Every filtered op resolves to one of two archetypes:

- **CAPTURE archetype** — the op removes or locks the entry (sync `read`/`take`/`readIfExists`/`takeIfExists`,
  multi-`take`, and the *blocking* form of any of these when it later fires). All capture flows converge on
  **one** method: `EntryHolder.confirmAvailability(...)`, entered via `confirmAvailabilityWithTxn(...)` from
  three callers (`hasMatch:180`, `attemptCapture:254`, `ContinuingQuery.next:793`). Evaluation goes **inside
  its `synchronized(handle)` window, after `canPerform` (EntryHolder.java:328), before `grab` (:355)** — one
  insertion point covering three match sites (A, B, and the blocking-capture re-entry). This is INV-2's home.

- **FAN-OUT/EVENT archetype** — the op delivers an *event* without capturing (`notify`,
  `registerForAvailabilityEvent`, and the read-only `contents` snapshot). Delivery runs on the single
  `OperationJournal` thread. There is no confirm window (nothing is captured), so evaluation goes in the
  watcher, **after the watcher's existing `transition.getTxn()` entitlement gate, before delivery/enqueue**,
  wrapped catch-everything (§2). This is INV-1's hard case — the true confused deputy.

### 1.2 The hand-off object: `FilterSet` (per-template plurality carrier) — [DESIGN CALL]

B1 §6.2 requires **one `CompiledFilter` per template** for the three multi-template ops. The admission loops
today (`OutriggerServerImpl.java:2355/2373/2391`) overwrite a single `CompiledFilter filter` variable — B1 §6.2
flags this as harmless only because nothing evaluates. B3 replaces that variable with an immutable **`FilterSet`**
(new package-private type in `outrigger-service`), built once at admission and threaded to every chokepoint the
op reaches:

```
final class FilterSet {                         // immutable; built by the admission loop, never mutated after
    // Concrete-schema filters, keyed by applicability digest (hex of entrySchemaDigest).
    private final Map<String, CompiledFilter> byDigest;   // one entry per non-schema-less template
    // Schema-less (null-key) filters — each applies to ALL candidates (B1 §6.1 OPTION-(b)).
    private final List<CompiledFilter> schemaLess;

    /** All CompiledFilters a candidate of this digest must satisfy (see §5 selection rule). */
    List<CompiledFilter> applicableTo(byte[] candidateEntrySchemaDigest);
    boolean isEmpty();
}
```

- **Single-template ops** (`read`/`readIfExists`/`take`/`takeIfExists`/`notify`) build a `FilterSet` of exactly
  one filter — either one `byDigest` entry (concrete template) or one `schemaLess` entry (null/match-any
  template).
- **Multi-template ops** (`registerForAvailabilityEvent`, `contents`, bulk `take`) build a `FilterSet` from the
  admission loop: each template contributes its `CompiledFilter` to `byDigest` (keyed by its own
  `applicabilitySchemaDigest`) or to `schemaLess` (null key). See §5 for the all-must-pass selection rule and
  the collision note.

`FilterSet` is what B3 threads through `confirmAvailabilityWithTxn` (capture archetype) and stores on the
watcher (fan-out archetype). Threading one object — not a bare `CompiledFilter` — is what makes §6.2 plurality
correct by construction rather than by vigilance (G7: construction beats a check).

### 1.3 The exhaustive match-site table

| # | Site (file:line) | Enclosing / operation | Filtered ops that reach it | Archetype | B3 evaluation placement |
|---|---|---|---|---|---|
| **A** | `EntryHolder.java:177` `tmpl.matches(rep)` | `hasMatch(...)` — single-template immediate scan | sync `read`/`take`/`readIfExists`/`takeIfExists` | CAPTURE | inside `confirmAvailability` window, after `canPerform` (:328), before `grab` (:355) |
| **B** | `EntryHolder.java:818` `tmpl.matches(handle.rep())` | `ContinuingQuery.handleMatch/next(...)` — multi-template iterator | filtered `contents` (take-into-matchset) and bulk `take(EntryRep[])` | CAPTURE (take) / SNAPSHOT (contents read) | same `confirmAvailability` window (take); for the read-only contents snapshot see §1.4 + F-2 hazard |
| **C** | `OutriggerServerImpl.java:2575` `tmpl.matches(rep)` | bulk-`take` journal catch-up (multi-template) | filtered bulk `take(EntryRep[])` | CAPTURE (via `watcher.catchUp` → `attemptCapture` → confirm window) | evaluation still occurs in the confirm window at capture; the catch-up loop only decides *interest* — see §1.4 |
| **D** | `OutriggerServerImpl.java:2983` `tmpl.matches(rep)` | single-template journal catch-up | blocking `read`/`take`/`notify` registration catch-up | CAPTURE (consuming) or EVENT (notify) | consuming: confirm window at capture; notify: watcher `process` after txn gate (§2) |
| **E** | `WatchersForTemplateClass.java:113` `handle.matches(rep)` | `collectInterested(...)` — journal fan-out interested-set build | `notify`, `registerForAvailabilityEvent`, and every *blocking* query watcher | FAN-OUT/EVENT | **NOT here.** Byte-match here only builds the interested set. CEL runs **per-watcher, after its `transition.getTxn()` gate**, in the watcher's `process`/`isInterested` (§2) — never inside the shared `collectInterested` |
| **F** | `OutriggerServerImpl.java:3997` `tmpl.matches(reps[i])` | `IteratorImpl.nextReps(...)` — **legacy `JavaSpaceAdmin` AdminIterator** contents | **none** — no filtered overload routes here | n/a | **Must be *proven* unreachable by any filtered op (§1.5), fail-closed if that ever changes** |
| **G** | `ReadWatcher.java:52–74` `process(...)`/`catchUp(...)` | non-transactional blocking `read` watcher | blocking `read` | NON-CAPTURE BLOCKING READ | after the existing `transition.getTxn() == null` gate (:49, :65), **before `resolve(...)`** (:58, :69) — predicate-false ⇒ do not resolve, keep waiting |
| **H** | `ReadIfExistsWatcher.java:101–155` `process(...)`/`catchUp(...)` | non-transactional blocking `readIfExists` watcher | blocking `readIfExists` | NON-CAPTURE BLOCKING READ | after the existing `transition.getTxn() == null` gate (:111, :152), **before `resolve(...)`** (:112, :153) — predicate-false ⇒ do not resolve, keep waiting |

**Reconciliation of the archetypes.** Sites A, B, C, and the consuming half of D all reach the *same* method,
`EntryHolder.confirmAvailability`. That is the design's biggest lever: **one evaluation insertion point covers
four match sites**, because Outrigger already funnels every capture through it (INV-2). Site E is the one place
CEL must be added *outside* the confirm window, because event delivery never captures — and it is exactly the
confused-deputy site (§2). Site F is the trap: a legacy path with no filter parameter that must never silently
serve unfiltered.

**Sites G/H are a second confused-deputy trap, distinct from F — `[RATIFIED Peter 2026-07-26]` (Blocker 2).**
`ReadWatcher` (`ReadWatcher.java:52–74`) and `ReadIfExistsWatcher` (`ReadIfExistsWatcher.java:101–155`) are
**non-transactional blocking read** watchers. They do not add a new `matches()` call site — the byte-match that
selects them as candidates already happened upstream, at site D's catch-up loop (`OutriggerServerImpl.java:2983`)
or at site E's `collectInterested`. The gap is downstream of that match: on a hit, both watchers `resolve(...)`
the matched handle **directly** (`ReadWatcher.java:58,69`; `ReadIfExistsWatcher.java:112,153`) — they never call
`attemptCapture`/`confirmAvailabilityWithTxn`, so they never pass through the confirm window that hosts every
other CAPTURE-archetype evaluation. Left unwired, a blocking `read`/`readIfExists` would resolve on the first
byte-matched, entitled transition **without ever consulting the predicate** — a silent fail-open exactly like
site F, but on the live (non-legacy) blocking-read path. Both watchers already gate correctly on
`transition.getTxn() == null` before resolving (`ReadWatcher.java:49,65`; `ReadIfExistsWatcher.java:111,152`), so
INV-1's entitlement precedent is already in place; B3 must insert predicate evaluation **between that existing
gate and the `resolve(...)` call**: predicate-false ⇒ do not resolve, keep waiting for the next candidate
transition (nothing is captured either way, so INV-2 is trivially satisfied here — this is a NON-CAPTURE
archetype, closer in shape to fan-out than to CAPTURE, except the entitlement gate is the simpler unconditional
`getTxn() == null` test rather than a per-registration txn comparison). Both watchers must carry the op's
`FilterSet`, exactly as the consuming watchers do (§1.4).

### 1.4 Capture-path evaluation, in detail (INV-2)

`confirmAvailability` today (EntryHolder.java:309–361) runs, inside `synchronized(handle)`:

```
synchronized (handle) {
    if (handle.removed()) return false;                 // :316  removed
    if (isExpired(time, handle)) return false;          // :318  expired
    if (handle.isProvisionallyRemoved()) { ...; return false; }   // :321
    int op = takeIt ? TAKE : READ;
    if (!handle.canPerform(txn, op)) { ...conflict...; return false; }   // :328  ENTITLEMENT GATE
    // ----------------------------------------------------------------
    //  B3 INSERTION POINT: evaluate the applicable filter(s) HERE.
    //  entrySchemaDigest==key check (§3), project (B2), evaluate (jgdms-cel),
    //  any non-BoolV(true) => return false (no capture), counted (§7).
    // ----------------------------------------------------------------
    if (grab(handle, txn, op, takeIt, false)) return true;   // :355  CAPTURE
    else throw new AssertionError("entry became non-available while locked");
}
```

`confirmAvailabilityWithTxn` gains a `FilterSet` parameter (default/unfiltered callers pass an empty/`null`
`FilterSet`, which is a no-op — the unfiltered path is byte-for-byte unchanged). The evaluation, when a
`FilterSet` is present, is:

1. **entrySchemaDigest == key** (§3) selects the applicable `CompiledFilter`(s) for this candidate via
   `filterSet.applicableTo(candidate.entrySchemaDigest())`. A concrete-key filter whose key ≠ the candidate's
   digest is **out of scope** (not even a fail-closed exclusion — simply not this filter's candidate, B1 §6.1).
2. **Project** the candidate's referenced fields class-free: `EntryProjection.project(rep.bodyBytes(),
   filter.expr())` (B2), under the per-candidate decode budget (§4).
3. **Evaluate**: `new Evaluator().evaluate(filter.expr(), projection)`. Map the `EvalOutcome`: `BoolV(true)` ⇒
   pass; `BoolV(false)` or *any* `EvalOutcome.Error` (`CANDIDATE_UNDECODABLE`, `ABSENT_FIELD`, `AMBIGUOUS_FIELD`,
   `TYPE_MISMATCH`, ...) ⇒ **no capture, return false**, counted per §7. (The `Evaluator` is *total* — it never
   throws for well-formed input; the only escaping exception it documents is a transcendental CALL with no
   `MathProvider`, which admission cannot produce for a predicate and which B3 still catches defensively, §2.)
4. **All-must-pass** across the selected `CompiledFilter` list (§5): the first non-pass short-circuits to
   no-capture.

Because this is inside the lock and before `grab`, a predicate-false entry is provably never captured (INV-2),
and because it is after `canPerform`, the predicate provably never runs against an entry this txn cannot see
(INV-1). The scan (`hasMatch` loop A / `ContinuingQuery.next` loop B) simply continues to the next candidate on
a `false` return, exactly as it already does for a conflict.

**Blocking capture (sites C / consuming-D).** When an immediate scan misses and the op blocks, a `QueryWatcher`
is registered; when a later transition makes an entry available, the consuming watcher
(`ConsumingWatcher`/`TakeWatcher`/`TakeIfExistsWatcher`) calls `getServer().attemptCapture(...)` →
`confirmAvailabilityWithTxn` → **the same confirm window**. So the *capture* of a blocked read/take is filtered
by the very same insertion point; the watcher must carry the op's `FilterSet` so it can pass it into
`attemptCapture`. The journal catch-up loops C/D only decide *interest* (whether to wake the watcher); the
authoritative filter decision is re-made at capture in the confirm window. This is the key exhaustiveness
argument for the blocking path: **no consuming delivery escapes the confirm-window evaluation.**

**Read-only `contents` snapshot (site B, read side) — continuation resolved, `[RATIFIED Peter 2026-07-26]`.**
Filtered `contents` returns a `MatchSetData` the client pages through. The first batch is materialized
server-side through `ContinuingQuery`; B3 evaluates each candidate at materialization (there is no capture — the
entitlement gate for a non-transactional read snapshot is visibility, which `ContinuingQuery` already enforces;
under a txn, `canPerform(READ)` is the gate). **Correction to the B1 SESSION-WRAP F-2 description (stale):** the
memo previously described the `MatchSet` *continuation* as "re-fetching through unfiltered `JavaSpace05.contents`"
— that description does not match the actual call path and is corrected here. The client-side continuation is
`MatchSetProxy.next()`, which on batch exhaustion calls **`space.nextBatch(uuid, lastRepReturned.id())`**
(`MatchSetProxy.java:86`) — a distinct `OutriggerServer` method, never the unfiltered `JavaSpace05.contents`
overload. Server-side, `OutriggerServerImpl.nextBatch` (`OutriggerServerImpl.java:3197`) looks up the retained
`ContentsQuery` by its `Uuid` and calls its own `nextBatch(entryUuid, now)` (`OutriggerServerImpl.java:3310`),
which continues the **same** `ContentsQuery` — advancing through `createQuery(...)` /
`EntryHolder.ContinuingQuery` (`OutriggerServerImpl.java:2490,2711`) into `ContinuingQuery.next` (site B) exactly
as the first batch did. So every continuation batch already runs through the identical server-side query state
that produced the first batch — there is no separate unfiltered re-fetch to drop the filter on. **Resolution:**
thread the op's `FilterSet` into the retained `ContentsQuery`/`ContinuingQuery` state at construction (alongside
`tmpls`/`txn`/`limit`), so every continuation batch is filtered **by construction**, not by re-derivation at each
`nextBatch` call. **No full-materialization fallback is needed** — the continuation already re-enters the
filtered path; it only needs the `FilterSet` reference carried in state it does not yet have. This closes
`[OPEN — BOARD]` item 2 of §10.

### 1.5 Site F — the legacy admin iterator MUST be proven unreachable — [DESIGN CALL]

`IteratorImpl.nextReps` (`OutriggerServerImpl.java:3997`) is the `JavaSpaceAdmin` AdminIterator path. It has
**no `byte[] filterEnvelope` parameter** and no filtered overload calls it. Leaving it "alone" is not enough
(G6: check the default branch): if a future refactor ever routed a filtered `contents` through it, it would
serve byte-matched-but-unfiltered entries — a silent bypass. B3's obligation:

- **Do not wire a filter into F** (there is none to wire), but **add a compile/structural guard** that the
  filtered `contents`/`take` paths never delegate to `IteratorImpl` — a code-path assertion or an architectural
  test that fails if a filtered op reaches `nextReps`. This is the "prove the negative" analogue of B1's
  enumerate-every-admission-site discipline. **`[RATIFIED Peter 2026-07-26]`: exempt-but-structurally-guarded.**
  The admin-only `IteratorImpl.nextReps` path is an acceptable standing exemption from filtering — it is not
  reachable from any client-facing filtered operation and gaining filter support there is out of B3 scope. The
  ratification is conditioned on the guard being a **mechanism, not a promise**: B3 MUST ship an actual
  structural/architectural test (not merely updated prose in this memo) that fails the build if any filtered
  `contents`/`take` code path is ever refactored to route through `IteratorImpl.nextReps` — e.g. a test that
  walks the call graph from each filtered `OutriggerServerImpl` entry point (or asserts on the absence of any
  caller of `nextReps` outside the `JavaSpaceAdmin` admin path) so a future refactor that quietly wires
  `AdminIterator` into a filtered op fails loudly at build/test time, not silently in production. Until that test
  exists, the exemption is a documented risk, not a closed one.

### 1.6 What threading looks like (no code, signatures only)

- `EntryHolder.confirmAvailabilityWithTxn` / `confirmAvailability` / `attemptCapture` / `hasMatch` /
  `ContinuingQuery.next` gain a trailing `FilterSet filters` parameter; unfiltered callers pass `FilterSet.EMPTY`
  (a shared immutable no-op instance) so their behaviour is unchanged.
- The event watchers (`EventRegistrationWatcher` for `notify`, `AvailabilityRegistrationWatcher` for
  `registerForAvailabilityEvent`) gain a final `FilterSet filters` field set at construction and consulted in
  their delivery decision (§2).
- The eight filtered `OutriggerServerImpl` methods replace `throw FilterAdmission.evaluationNotWired(...)` with:
  build the `FilterSet` from the admission result(s), then run the *normal* (unfiltered) query machinery with
  the `FilterSet` threaded in. The `evaluationNotWired` helper and its counter are retired (or kept only as a
  dead-safe assertion that no chokepoint is left unwired).

---

## 2. Fan-out (#5) entitlement gate — the true confused deputy

The fan-out path is where the server runs **one client's predicate over another client's entries**, on a shared
thread. It is the single richest confused-deputy surface (Board §2.2 hazard 1/2), and it has three distinct
hazards, each with an enforced mechanism.

### 2.1 The entitlement gate (INV-1) — reuse the existing txn-visibility precedent

The `OperationJournal` single thread (`OperationJournal.java:422 run()`) pulls each `EntryTransition`, asks
`TransitionWatchers.allMatches(t, ordinal)` for the interested watchers (byte-match via
`WatchersForTemplateClass.collectInterested`, site E), and calls `watcher.process(t, now)` for each.

The transition already carries its originating txn: `EntryTransition.getTxn()` (`EntryTransition.java:119`) —
`null` means globally visible/available; non-null means visible only to that txn's participants. **Every
existing watcher already gates on it.** The canonical precedent is
`TransactableAvailabilityWatcher.java:101`:

```
final TransactableMgr transitionTxn = transition.getTxn();
if (((transitionTxn == null) || (transitionTxn == txn)) && !transition.hasProcessed(this)) { ... }
```

**Decision.** B3 evaluates the CEL predicate **only inside that already-guarded branch** — i.e. only once the
watcher has established `transitionTxn == null || transitionTxn == <this registration's own txn>`. Concretely:
the predicate evaluation is inserted between the existing entitlement test and the delivery/enqueue
(`server.enqueueDelivery(...)` at `AvailabilityRegistrationWatcher.java:187`; the analogous send in
`EventRegistrationWatcher`). A transition under a *third party's* txn is filtered out **before** the predicate
ever touches its entry — the predicate is never executed against an entry the registrant is not entitled to
observe. This is INV-1 by reuse of a mechanism the codebase already trusts, not a new one (G2/G7).

**Why not evaluate in `collectInterested` (site E)?** Because `collectInterested` is shared across all watchers
for a template class and runs *before* per-watcher entitlement; evaluating there would run a registrant's
predicate against entries selected for *other* registrants — the confused deputy. So the byte-match stays at
E (cheap, entitlement-agnostic, builds the interested set) and the **CEL runs per-watcher, after the gate.**

### 2.2 Cheap byte-equality first, CEL last (ordering)

**Decision.** Keep the existing quick-reject (`handle.hash() & desc.mask`) and `EntryRep.matches()`
byte-equality at site E as the first filter, unchanged. Only a watcher that (a) byte-matches AND (b) passes the
`transition.getTxn()` entitlement gate runs CEL — and CEL runs at most once per (watcher, transition) pair. This
ordering is both a correctness requirement (INV-1: entitlement before predicate) and the natural DoS ordering
(cheapest, entitlement-agnostic checks first; the expensive class-free projection + walk last), mirroring
`CelVerifier`'s own cheapest-check-first composition.

**Nit, pinned exactly:** "the watcher" above means the watcher's **`process(...)` method only** — the method the
`OperationJournal` thread calls per live transition, one watcher at a time. It does **not** mean a watcher's
`isInterested(...)` method, which `WatchersForTemplateClass`/`TransitionWatchers` calls while **assembling** the
interested set for a template class, potentially under a lock shared across every watcher registered for that
class. Running CEL inside `isInterested` would (a) run it before the per-watcher entitlement gate exists in that
call at all for some watcher shapes, and (b) hold a shared assembly lock for the class-free projection + walk,
turning a per-watcher cost into a cross-registrant stall. CEL belongs exclusively in `process` (and the
consuming-watcher's `attemptCapture`-driven confirm-window path, per §1.4), never in `isInterested`.

### 2.3 Wrap catch-everything — a RuntimeException must not kill the journal thread (G6)

**The mechanism-level hazard.** `OperationJournal.run()`'s catch block (`OperationJournal.java:469–480`) logs a
`Throwable` and continues **only for checked exceptions** — but lines 478–479 *re-throw* any `Error` or
`RuntimeException`:

```
} catch (Throwable t) {
    logger.log(Level.INFO, "...continuing", t);
    if (t instanceof Error) throw (Error) t;
    if (t instanceof RuntimeException) throw (RuntimeException) t;   // <-- kills the single journal thread
}
```

So an escaping `RuntimeException` from a watcher's `process` — e.g. a latent bug in projection or the evaluator,
or the `Evaluator`'s documented `IllegalStateException` for a transcendental CALL with no `MathProvider` — would
**terminate the one thread that delivers every event to every registrant on the whole server.** That is a
catastrophic availability failure triggered by one hostile filter.

**Decision.** B3's fan-out evaluation is wrapped **catch-everything except `VirtualMachineError`** at the
watcher's evaluation site, mapping any escaping `Throwable` to **fail-closed no-match** (the transition is
simply not delivered to *that* watcher), counted in `filter.failClosedExclusions` (§7):

```
boolean pass;
try {
    pass = evaluateFilterSet(filters, rep);     // project (budgeted, §4) + evaluate; total on success
} catch (VirtualMachineError vme) {
    throw vme;                                  // OOME/StackOverflow are NOT a candidate verdict — never swallow
} catch (Throwable t) {
    pass = false;                               // fail-closed: exclude this candidate, count it, keep the thread alive
}
```

This mirrors `EntryProjection`'s own `catch (VirtualMachineError) rethrow; catch (Throwable) fail-closed`
discipline (B2) and B1's defensive `catch (RuntimeException)` in `FilterAdmission.admit`. The same wrap is
applied on the **capture** path (confirm window) for symmetry, even though the confirm window is not the
single-thread-of-doom — a fail-closed no-match there is `return false`, identical to a conflict.

**[DESIGN CALL / note to board]** The `VirtualMachineError` re-throw means a genuine OOME/StackOverflow still
propagates (and on the journal thread, still re-thrown by the loop) — this is correct: a JVM error is not a
"candidate doesn't match" verdict and must not be laundered into one (B2 precedent, Board G6). The projection
decode budget (§4) is what keeps a hostile candidate from *reaching* a StackOverflow in the first place.

---

## 3. §6.1 digest == key enforcement (the B2 adversarial-seat hard dependency)

**The claim (B1 §6.1) and the mechanism.** `fieldValue(class, field)` is only trustworthy if the candidate's
schema *is* the schema the field names were resolved/type-checked against. If B3 evaluated a concrete-schema
filter against a candidate of a *different* schema, "field identity" (which class#field a name denotes) would be
unguaranteed — the B2 adversarial seat flagged this as a hard B3 dependency. The enforced mechanism:

**Decision.** Before B3 trusts any `fieldValue`, it selects the applicable filter by
`candidate.entrySchemaDigest()`, **per candidate, at the top of the confirm-window / fan-out evaluation, before
projection is consumed.** **Nit, pinned exactly (the two digest sources are not interchangeable in timing):** the
digest consulted for this **pre-decode** selection is the **stored `EntryRep`'s own digest** — computed and
installed once, at write-time decode (when the entry was accepted into the space and its v2 schema chain was
bound), and readable directly off the candidate's `EntryRep`/`EntryHandle` without invoking `EntryProjection` at
all. `EntryProjection.entrySchemaDigest()` is the **post-decode** value — it exists only once a projection has
actually been constructed for this candidate (which for the non-null-key case happens *after* this very
selection has already decided the filter applies, per the ordering note below). The two values are defined to
agree (both derive from the same schema-chain binding, B2/EntryRep-v2 §A.8/A.9), so using the stored `EntryRep`
digest pre-decode is not a weaker check — it is simply the one that is actually available at the point this
decision must be made, and it is what makes the "before projection decode" ordering below correct rather than
circular:

- **Non-null key** (`CompiledFilter.applicabilitySchemaDigest() != null`): the filter applies **without further
  resolution** iff `Arrays.equals(candidate.entrySchemaDigest(), key)` — field names were already statically
  type-checked against exactly this one schema at admission, so once the digest equals the key, `fieldValue` is
  trustworthy by construction. The check is a 32-byte `Arrays.equals`, done once per candidate.
  **`[RATIFIED Peter 2026-07-26]` — the digest-mismatch case is no longer "out of scope, untouched."** A
  candidate reaches this decision only because it already byte-matched some template in scope for this op (§1.0);
  a digest mismatch at this point is not evidence the candidate is unrelated to that template — the dominant real
  case is a **subclass entry**: it byte-matches the (superclass) template on the template's own declared fields,
  but its *own* schema chain is longer (its subclass fields extend the digest), so
  `candidate.entrySchemaDigest() != key` even though the candidate is a legitimate result the byte-match already
  selected. The superseded rule ("a mismatch is simply out of scope, not even a fail-closed exclusion, not
  counted" — B1 §6.1, now amended, see the B1 doc's §6.1 amendment note) was a **fail-open**: it let such
  candidates through this filter **entirely unconstrained** by the very predicate that byte-match selected them
  for. **Ratified resolution:** on a digest mismatch, resolve the predicate against the candidate's **own** schema
  chain, exactly like the null-key path immediately below — each referenced field name is looked up in the
  candidate's own v2 schema; present-and-unambiguous ⇒ evaluate against the candidate's own value (using the
  candidate's own field layout, not the template's); absent-or-ambiguous ⇒ fail-closed exclusion, counted in
  `filter.failClosedExclusions`. This is exactly §5's per-candidate selection rule updated the same way — see
  §5's cross-reference. **[DESIGN CALL]:** this requires the evaluation machinery to retain, for a digest
  mismatch, a reference to the *originating* `CompiledFilter` (the one whose template this candidate byte-matched
  at the match site) rather than only a digest-keyed lookup into `FilterSet` — for single-template ops there is
  exactly one candidate filter so this is immediate; for multi-template ops (§5) the match site already knows
  which template produced the byte-match, and that association must be threaded to this step rather than
  re-derived from the candidate's digest alone (a pure `byDigest` re-lookup would, by definition, miss on a
  subclass digest and has no way to recover *which* template's filter to fall back to).
- **Null key** (`CompiledFilter.isSchemaLess()`): the filter applies to **ALL** candidates (OPTION-(b)). There
  was no template schema to type-check against, so each referenced field name is resolved **per candidate,
  against that candidate's own v2 schema** — which is exactly what the `Evaluator` + `EntryProjection` already
  do: `resolveStep` consults `projection.namespaceChain()`/`declaresField(...)`, and an **undeclared referenced
  field yields `ABSENT_FIELD`** (`Evaluator.java:185`) → mapped to **fail-closed exclusion**, counted in
  `filter.failClosedExclusions`. An **ambiguous** unqualified name (declared in ≥2 classes of the candidate's
  chain) yields `AMBIGUOUS_FIELD` → also fail-closed exclusion. This is OPTION-(b) *by construction*: the
  projection's `declaresField` = schema presence, and the evaluator's absent/ambiguous → error → no-match chain
  is precisely the "missing/undeclared field ⇒ fail-closed" rule.

**Ordering (important).** The digest==key selection runs **before** projection decode for the non-null-key case,
so a wrong-schema candidate is dropped *without decoding any field value* (cheap and leak-free). For the null-key
case there is no key to compare, so projection proceeds and the per-candidate name resolution does the
fail-closed work. **[DESIGN CALL]** For a candidate that is `isUndecodable()` (its v2 body would not decode), the
digest is `null` and the candidate is a fail-closed exclusion regardless of key — checked first, before any key
comparison.

**Why the digest check is trustworthy (G12).** `entrySchemaDigest` is a content digest of the candidate's own
schema chain, verified present-in-and-bound-by the candidate's schemaTable during `EntryRepV2Codec.decode`
(B2/EntryRep-v2 §A.8/A.9 field-count + chain-binding guards). So "digest == key" means the candidate's fields
are laid out under exactly the schema the filter was type-checked against — bound-to-known-good, not merely
self-consistent.

---

## 4. Per-eval COST CEILING — the section Peter ratifies

This is the decision B1 §10/pushdown-design DECISION-2 deferred to Peter with board advice: because evaluation
runs **under the `handle` lock** (INV-2), per-candidate CEL + projection work is a **lock-hold DoS** risk, and it
couples to B4 (write-path cost caps). This section presents the worst-case analysis, three options, and a
recommended default.

### 4.1 What is already bounded, and what is not

**Already bounded at admission (no B3 action needed):**

- **The CEL AST.** `CelDecoder`/`CelVerifier` enforce, at *admission*, `MAX_EXPR_NODES` (1024), `MAX_EXPR_DEPTH`
  (32), `MAX_SELECTOR_STEPS` (16), `MAX_SCALAR_BYTES` (64 KiB, on *literal* scalars in the expression), and the
  **cost gate** `MAX_EXPR_COST` (`CostModel.computeCost`, the first and only mandatory cost admission gate).
- **Consequence — CORRECTED, `[RATIFIED Peter 2026-07-26]`.** This memo originally claimed here that "the
  predicate walk itself is already bounded — a hostile filter cannot make the *walk* large, because admission
  rejected a large AST," concluding the walk is `O(admitted AST nodes)` and therefore effectively free. **That
  claim was wrong**, and the board established why: bounding the *node count* only bounds the walk if every
  node's own cost is O(1) (or at least the O(1)-like bound `CostModel` charged it at admission). It is not, for
  one node kind — see the new bullet immediately below. The AST node count is bounded; the **per-node cost of a
  string ordering comparison is not**, and that gap is the real lock-hold term, not merely the already-listed
  decode terms.

**NOT bounded by anything CEL, and the real lock-hold term:**

- **String ordering is eager and unbounded, in contradiction with the cost model that admitted it — the
  central finding, `[RATIFIED Peter 2026-07-26]`.** `Evaluator.compareCodePoints` (`Evaluator.java:446`)
  implements string ordering (`<`/`<=`/`>`/`>=`) by calling `a.codePoints().toArray()` and
  `b.codePoints().toArray()` on **both** operands and then walking the shorter array's length — i.e. it is
  `O(len(a) + len(b))`, with **no short-circuit** on the shorter operand: even a one-character literal forces a
  full `toArray()` of the (potentially much longer) candidate-field operand. Separately, `size(string)`
  (`callSizeString`, `Evaluator.java:653`) is `s.value().codePointCount(0, s.value().length())` — also
  `O(len)`, recomputed on every occurrence of `size(field)` in the AST, not cached. Meanwhile `CostModel`'s
  admission-time cost for a string/bytes comparison (`CostModel.java:235–237`) charges only
  `1 + min(S, len(literalOperand))` — the **tightened** cost that assumes the *shorter* operand bounds the work
  (`S = MAX_SCALAR_BYTES`, `CostModel.java:93`). That tightening's justification is only true if the evaluator
  actually stops at `min(len(a), len(b))`; the current `Evaluator` does not implement that — it is eager and
  operand-length-symmetric, not short-circuited. So `MAX_EXPR_COST` admits an AST whose *charged* cost assumes
  cheap ordering ops, while the *actual* per-op cost is driven by the **candidate's** field length, which
  `CostModel` cannot see (it only knows the literal operand's static length at admission). **Consequence:** an
  admitted AST (≤`MAX_EXPR_NODES` = 1024 nodes) can pack roughly a thousand `field < "x"`-shaped ordering
  comparisons — each cheap under `CostModel` (literal length 1 ⇒ charged cost ≈ `1 + min(S,1)`) — all against
  the *same* candidate field. If that field is decoded to (or near) the Option-B `MAX_PROJECTION_DECODE_BYTES`
  budget (§4.3) of 64 KiB, ~1000 eager `toArray()` calls over a ~64 KiB `String` is **tens of MiB of scan and
  transient-array allocation per candidate**, under the `handle` lock — nowhere near "sub-millisecond," and the
  admission cost gate did nothing to prevent it, because it was charging the *wrong* thing. This is the finding
  behind the §4.4 ratified fix.

- **Projection decode of the candidate body.** `EntryProjection.projectFields` is **eager**: at construction it
  calls `EntryRepV2Codec.decode(candidateBody)` (a full structural decode of the v2 body) and then eagerly
  decodes every *referenced* field's value. Body size is bounded only by the **EntryRep-v2 body ceiling**
  (`maxBodyBytes` = 8 MiB) and per-slice payload ceiling (1 MiB) — *storage* ceilings, not *per-eval* ceilings.
- **The B2 amplifier the memo calls out:** a referenced **nested `@AtomicSerial` object** field is projected via
  `ObjectCodec.decodeToScalarFieldMapClassFree`, which **decodes ALL scalar sub-fields of that nested object —
  even for `has(obj)`**, which needs none of them. So `has(bigNestedObject)` still pays the full nested-scalar
  decode.
- **String/bytes operators on candidate field values.** `contains`/`startsWith`/`endsWith`/ordering over a
  candidate's `String`/`bytes` field is `O(field length)`, and the field length is bounded only by the 1 MiB
  slice ceiling, not by `MAX_SCALAR_BYTES` (which bounds the *literal*, not the candidate value).

**Worst-case lock-hold, unmitigated.** For each candidate scanned under the `handle` lock:
`decode(≤8 MiB body)` + `decode all referenced/nested scalars (≤ Σ slice payloads)` + `walk (≤1024 nodes,
with string ops ≤1 MiB each — and, per the finding above, ~1000 such ops may legally target the *same* field,
since `CostModel` under-charges each one)`. Multiplied across a scan of N candidates, an adversary who stores a
few large entries can hold the `handle` lock (and starve concurrent writes/takes) for a long time per query.
**This is the lock-hold DoS the ceiling must bound — and, critically, it is a DoS the admission cost gate alone
does not bound, because the cost gate's own per-op charge assumed a walk behaviour the `Evaluator` does not
have (see above).**

### 4.2 A structural reduction that pairs with every option: reuse the already-decoded slices — [DESIGN CALL]

The confirm window already holds a fully-parsed `EntryRep` for the candidate (EntryRep-v2 keeps transient
`sliceBytes[]`; `matches()` compared them). But `EntryProjection.projectFields` re-runs
`EntryRepV2Codec.decode(candidateBody)` from scratch — a **second full structural decode** of a body the server
already has decoded. **Recommendation (independent of which ceiling option is chosen):** add a B3 projection
factory that consumes the `EntryRep`'s already-decoded slices/schema instead of re-decoding the body, so the
`O(body)` structural decode is **not** repeated under the lock. This removes the largest fixed term (the 8 MiB
body re-decode) from the lock-hold, leaving only the *value* decode of referenced fields + the walk — which the
budget below then bounds. **[OPEN — BOARD]:** confirm the transient slice state is available and canonical at the
confirm-window frame (it is populated on load/`matches`); if not, the fallback is the byte-budget of §4.3 alone.

### 4.3 The three options

All three fail **closed** on exceed: the candidate is **excluded (no capture / no delivery) and counted** in a
new `filter.rejected.projectionBudget` (or `filter.failClosedExclusions`; §7 fixes the name). None ever
downgrades to unfiltered.

#### Option A — Admission bounds only, plus slice reuse (minimal; NOT recommended as sole defense)

Trust the admission AST ceilings + the EntryRep-v2 storage ceilings (8 MiB body / 1 MiB slice / 256-entry
schemaTable) as the *only* bound, and add only the §4.2 slice reuse. **Lock-hold worst case:** value-decode of
referenced slices (each ≤1 MiB) + walk with string ops over ≤1 MiB fields. Bounded, but the constant is large: a
single `contains` over a 1 MiB `String` field, times N candidates, is a real lock-hold. **Verdict:** simplest,
zero new knobs, no `jgdms-cel` change — but it leaves the lock-hold coupled to *stored-entry size*, which an
adversary controls at write time. Acceptable only if entry-size ceilings are separately tightened for spaces
that enable filtering (a B4 concern). Recommended only as the floor, not the ceiling.

#### Option B — Per-candidate projection-decode byte budget (RECOMMENDED DEFAULT)

Add `FilterAdmission.MAX_PROJECTION_DECODE_BYTES` — a fixed compile-time constant (proposed **64 KiB**, matching
`MAX_CEL_WIRE_BYTES`) bounding the total candidate-value bytes B3 will decode/scan **per candidate**. The
`EntryProjection` factory threads a small mutable byte counter: every `readOctetString` / scalar payload / nested
sub-field scalar it decodes decrements the budget; **exceeding it renders the projection `isUndecodable()`** →
the evaluator returns `CANDIDATE_UNDECODABLE` → **fail-closed exclusion, counted.** The budget also caps the
input length handed to string operators (a `contains` over more than the budget's worth of field bytes is a
fail-closed exclusion, not an unbounded scan).

- **Lock-hold worst case — CORRECTED, `[RATIFIED Peter 2026-07-26]`.** The budget bounds *decode*: the total
  candidate-value bytes B3 will read out of the wire form. It does **not**, by itself, bound how many times the
  walk **re-scans** an already-decoded value. As §4.1's central finding shows, `Evaluator.compareCodePoints`
  (`Evaluator.java:446`) is eager and un-short-circuited, and `CostModel`'s ordering tightening
  (`CostModel.java:235–237`) under-charges it — so the byte budget alone does **not** restore
  "`O(MAX_PROJECTION_DECODE_BYTES)` decode + `O(admitted AST)` walk ≈ 64 KiB + ≤1024 bounded nodes —
  sub-millisecond" as this memo previously claimed. Bounding decode to 64 KiB while the walk can still run
  ~1000 eager `O(len)` ordering comparisons *against that same 64 KiB value* leaves the walk term at **tens of
  MiB of scan/alloc per candidate**, not sub-millisecond. **Option B alone is therefore insufficient** — it
  bounds the decode term (§4.1's other bullets) but not the walk term this section exists to bound. **Option B
  is ratified only paired with the evaluator fix below**, at which point the sub-millisecond bound is genuinely
  restored: `O(MAX_PROJECTION_DECODE_BYTES)` decode + `O(admitted-AST-node-count × min(candidate-operand,
  literal-operand))` walk ≈ 64 KiB + ≤1024 nodes each doing genuinely `O(min)` work — independent of
  stored-entry size, as originally intended.
- **The paired evaluator fix (jgdms-cel only; no wire-format, no error-algebra/STD-011 §9.1 change).** Two
  changes to `au.net.zeus.jgdms.cel.eval.Evaluator`, scoped entirely inside `jgdms-cel`: (1) make string ordering
  comparison **lazy and O(min)** — compare code point by code point up to the length of the *shorter* operand and
  stop there (return as soon as a difference is found, or as soon as the shorter operand is exhausted), **never**
  materialize a `toArray()` of the longer operand; the equivalent applies to `compareUnsignedBytes` for
  consistency (currently also eager, though its literal-vs-candidate asymmetry is less severe since bytes
  comparison has no code-point decoding step). (2) **bound `size(string)`/`size(bytes)` per field** by computing
  it once per candidate (memoized against the already-decoded field value) rather than re-scanning on every AST
  occurrence of `size(field)`. Neither change touches `CelValue`, `EvalOutcome`, the closed eight-error set
  (STD-011 §9.1), or any wire format — it is a pure internal-algorithm fix that makes the `Evaluator`'s actual
  behaviour match the `O(min)` assumption `CostModel`'s tightening already charges for. This is what makes the
  cost-model tightening's justification **true** rather than merely assumed.
- **Interaction with the B2 `has(obj)` amplifier:** the budget is checked **cumulatively across the whole nested
  `decodeToScalarFieldMapClassFree` decode**, so `has(bigNestedObject)` is bounded by the budget even though B2
  decodes all nested scalars — the nested decode fail-closes at 64 KiB. This directly bounds the exact term the
  task flags, and is unaffected by the evaluator fix (it is a decode-side bound, not a walk-side one).
- **Cost:** the byte budget lives entirely in `outrigger-service` (`EntryProjection` + one constant); the paired
  fix lives entirely in `jgdms-cel`'s `Evaluator` internals. **No `jgdms-cel` public API change, no CEL error-set
  change** — both changes are internal-algorithm corrections, not new surface. One new counter/metric (the
  budget) plus no new error variant (the fix).
- **Verdict:** RECOMMENDED, **as a package with the evaluator fix — the two do not work independently.** The byte
  budget bounds the dominant *decode* lock-hold term and decouples it from attacker-controlled entry size; the
  evaluator fix bounds the *walk* term and makes the admission cost gate's own tightening assumption true. Pairs
  with §4.2 slice reuse to also remove the body-structural-decode term.

#### Option C — Full runtime CEL step budget (most defensive; DEFERRED)

Add an evaluation step meter to the `jgdms-cel` `Evaluator`: thread a `long stepBudget` (new `Evaluator`
constructor / `evaluate` overload), decrement per AST node visited **and per character compared in string/bytes
ops**, and on exhaustion return a fail-closed outcome. Combined with Option B, this gives a *total* per-candidate
work ceiling independent of both admitted-AST-cost and entry size.

- **Cost:** a **`jgdms-cel` public API change** (new `Evaluator` entry point) **and** either a new
  `CelError.BUDGET_EXCEEDED` member — which touches STD-011 §9.1's *closed eight-error* set, a normative spec
  change — or an out-of-band budget-exhausted signal that bypasses the closed error algebra. Either way it
  re-opens the CEL determinism seat and needs a board re-review + STD-011 amendment.
- **Verdict:** DEFERRED. The admission cost gate already bounds AST work, and Option B bounds the decode/string
  term more cheaply without perturbing the CEL error algebra. Hold C in reserve **only if** profiling shows the
  *walk itself* (not decode) is the lock-hold cost — which the admission ceilings make unlikely. If ever needed,
  it stacks cleanly on B.

### 4.4 Ratified decision — `[RATIFIED Peter 2026-07-26]`

**Peter ratifies Option B, `MAX_PROJECTION_DECODE_BYTES = 64 KiB`, PAIRED WITH the evaluator fix (§4.3), layered
on the §4.2 slice-reuse reduction. Option C remains DEFERRED.**

This corrects and supersedes the memo's original recommendation, which described Option B alone as achieving
"sub-millisecond, `O(64 KiB decode)`" lock-hold. The board established that claim was **wrong**: `Evaluator.
compareCodePoints` (`Evaluator.java:446`) eagerly `toArray()`s both string operands with no short-circuit
(`O(candidate-length)`, not `O(min)`), and `size(string)` (`Evaluator.java:653`, `codePointCount`) is `O(len)` per
occurrence — while `CostModel`'s string-ordering tightening (`CostModel.java:235–237`, `1 + min(S, literalLen)`)
charges admission cost as if the walk already stopped at `min(candidate-length, literal-length)`. It does not. An
admitted AST (≤1024 nodes, the `MAX_EXPR_NODES` ceiling) can legally pack roughly a thousand `field < "x"`-shaped
ordering comparisons against one field; if that field is decoded up to the Option-B 64 KiB budget, that is **tens
of megabytes of scan and transient-array allocation per candidate**, under the `handle` lock — not
sub-millisecond, and the admission cost gate does not prevent it because it was charging the wrong quantity.

**The ratified package (jgdms-cel-internal only — NO wire-format change, NO error-algebra/STD-011 §9.1 change):**

1. **§4.3 Option B, unchanged as specified:** `FilterAdmission.MAX_PROJECTION_DECODE_BYTES = 64 KiB`
   (fixed compile-time constant), bounding total per-candidate projection-decode bytes, fail-closed + counted on
   exceed.
2. **The evaluator fix, new to this ratification:** make `Evaluator`'s string ordering comparison lazy and
   genuinely `O(min)` — compare code point by code point up to the shorter operand's length, short-circuit on
   first difference, never `toArray()` the longer operand — and bound `size(string)`/`size(bytes)` by computing it
   once per candidate per field rather than re-scanning per AST occurrence. This is what makes `CostModel`'s
   `1 + min(S, literalLen)` tightening's justification **true**, restoring the sub-millisecond bound the byte
   budget alone cannot deliver. Scope: internal to `au.net.zeus.jgdms.cel.eval.Evaluator`; no public API change,
   no new `CelError` member, no STD-011 amendment.
3. **§4.2 slice reuse, retained as part of the ratified package:** the confirm window already holds a
   fully-parsed `EntryRep`; the B3 projection factory must consume its already-decoded slices/schema rather than
   re-running `EntryRepV2Codec.decode(candidateBody)` from scratch, removing the 8 MiB body-structural-decode term
   from the lock-hold.

With all three, the worst-case per-candidate lock-hold is genuinely `O(64 KiB decode + bounded-AST-node-count ×
min-bounded walk)` — sub-millisecond, independent of stored-entry size — and, critically, **no `jgdms-cel` public
API surface changes and no STD-011 §9.1 closed-error-set amendment is incurred**, because both the byte budget
and the evaluator fix are internal-algorithm corrections, not new surface.

**Why 64 KiB is retained as the uniquely coherent value.** 64 KiB is not an arbitrary midpoint between "16 KiB
tighter" and "256 KiB looser" — it is **`MAX_SCALAR_BYTES`**, the same admission-time ceiling `CostModel` already
uses as `S` (`CostModel.java:93`) to bound a *literal* scalar's contribution to comparison cost. Choosing any
other value for the per-candidate projection-decode budget would decouple the value half of the admission
cost-gate's operand-length assumption (bounded by `S`) from the candidate half (bounded by the projection budget)
— i.e. it would reintroduce an asymmetry between what the cost gate assumes about literals and what B3 enforces
about candidate values. 64 KiB keeps both sides of every comparison the cost gate reasons about under the same
ceiling, which is the reason the evaluator fix's `O(min)` bound is meaningful at all: `min(candidate ≤ 64 KiB,
literal ≤ 64 KiB)` is a bound the admission cost gate can actually charge for honestly, post-fix.

**Option C (full runtime CEL step meter) remains explicitly DEFERRED.** It would require a `jgdms-cel` **public**
API change (a new `Evaluator` entry point taking a step budget) and either a new `CelError.BUDGET_EXCEEDED`
member — which reopens STD-011 §9.1's closed eight-error set, a normative spec change — or an out-of-band signal
that bypasses the closed error algebra. Either path re-opens the CEL determinism seat and needs its own board
review and STD-011 amendment; neither is warranted now that the ratified B-plus-evaluator-fix package restores
the intended bound without touching that surface. Hold C in reserve only if profiling later shows the *walk*
itself — after the fix, genuinely `O(min)` — is still the lock-hold cost, which the admission ceilings make
unlikely.

---

## 5. Per-template plurality (§6.2) — data structure and candidate→filter selection

**Decision (implements B1 §6.2).** All three multi-template ops (`registerForAvailabilityEvent`, `contents`,
bulk `take`) retain **one `CompiledFilter` per template** via the `FilterSet` of §1.2, and apply the
**all-applicable-must-pass** rule. The admission loops
(`OutriggerServerImpl.java:2355/2373/2391`) stop overwriting a single `filter` variable and instead accumulate
into a `FilterSet.Builder`:

- Each concrete-schema template contributes `(applicabilitySchemaDigest → CompiledFilter)` to `byDigest`.
- Each null/match-any template contributes its `CompiledFilter` to `schemaLess`.

**Candidate → filter selection (`FilterSet.applicableTo(candidateDigest)`):**

1. Start with the (possibly empty) list of `schemaLess` filters — each applies to *every* candidate (B1 §6.1
   null-key ⇒ all candidates).
2. Add `byDigest.get(hex(candidateDigest))` if present — the one concrete filter whose applicability key equals
   this candidate's `entrySchemaDigest`.
3. The candidate **passes** iff it passes **every** filter in that combined list (all-must-pass); the first
   non-pass short-circuits to exclusion.

**`[RATIFIED Peter 2026-07-26]` — an empty combined list is no longer "no additional constraint."** The prior
text here reasoned that a candidate whose digest matches no `byDigest` entry and no `schemaLess` filter "would not
have byte-matched any template either, so it never reaches evaluation," and treated that empty-list case as a
safe no-op. That reasoning was **wrong for subclass candidates** (§3's amended non-null-key rule): a subclass
entry byte-matches its superclass template on the template's own fields, reaches evaluation as a legitimate
candidate, yet carries its *own*, longer `entrySchemaDigest` — so it produces exactly this "empty list" outcome
even though a concrete filter (the byte-matched template's own) plainly should constrain it. Treating that as "no
additional constraint" returned such candidates **unfiltered**, contradicting the feature's own reason for
existing (B1 §1) and B1 §6.1's applicability rule. **Resolved:** an empty combined list is never treated as "no
constraint" by default. Instead, per §3's amended non-null-key rule, the candidate is resolved schema-lessly
against **the `CompiledFilter` of the template it byte-matched** (looked up via the match site's own
template↔filter association, not re-derived from `FilterSet.byDigest` by digest) — absent/ambiguous referenced
field ⇒ fail-closed exclusion; present-and-unambiguous ⇒ evaluated against the candidate's own field values. This
makes subclass entries filtered by their inherited fields, exactly as a directly-matching candidate would be.
This amends B1 §6.1 OPTION-(b) — see `DESIGN-Outrigger-CEL-Filter-B1.md` §6.1's amendment note.

**Why all-must-pass, not any-pass.** B1 §4.2/§6.2 admit the one *envelope* against every template (all must
pass at admission) and §6.2 pins per-template retention. A candidate that byte-matches template *i* and carries
schema *i*'s digest is filtered by filter *i* (its own template's predicate) plus every schema-less filter.
Collapsing to any-pass, or to a single last-filter, would misapply one template's predicate to another
template's candidates — the exact hazard §6.2 names. All-applicable-must-pass is the fail-closed reading.

**[OPEN — BOARD] key-collision note.** If two templates in one multi-template op share an `entrySchemaDigest`
(same schema, different wildcard patterns) they collide in `byDigest`. Two templates of the *same schema* have
byte-identical schema and thus the *same* admission type-check, so their `CompiledFilter`s are equal *as
predicates* — but their *envelopes are one shared filter* (B1: one envelope per op), so this is a non-issue:
there is exactly one predicate per op, admitted once per distinct schema. `byDigest` therefore needs at most one
entry per distinct schema; a second template of the same schema is the same key mapping to the same predicate.
Confirm this holds (it follows from "one envelope per op") and that `FilterSet.Builder` de-dupes by digest
rather than rejecting a duplicate.

---

## 6. Null template-element normalization + restored arg-validation (the N-1 findings)

Two B1 review findings (SESSION-WRAP 2026-07-25b, "B3 obligations logged (both seats)") land in B3 because they
only bite once evaluation runs:

### 6.1 Null template-element normalization

**Decision.** The filtered server stubs must normalize a **null element** of a multi-template `EntryRep[]` (and a
null single template) to the match-any template, exactly as the *unfiltered* path does (`setupTmpl`/
`matchAnyEntryRep`). The filtered admission/eval path currently does not do this null→match-any normalization,
so a null element would NPE or mis-scope. B3 normalizes each `tmpls[i]` (and the single `tmpl`) before admission
and before building the `FilterSet` — a null element admits its filter **schema-lessly** (null applicability
key ⇒ applies to all candidates, per §5), which is the correct semantics for a match-any template. This must be
symmetric with the unfiltered path so a filtered query and its unfiltered sibling scope identically.

### 6.2 Restore full argument validation (checkLimit / leaseTime — N-1)

**Decision.** The B1 filtered stubs abbreviated argument validation (they admit-then-throw, so full validation
was moot). B3 restores the **complete** unfiltered-path validation in every filtered stub *before* running the
query: `checkLimit(limit)` and `checkLeaseTime(leaseTime)`/timeout validation for `contents`, bulk `take`,
`registerForAvailabilityEvent`, and `notify`; the `checkForNull`/`checkForEmpty`/`checkHandbackFormat` already
present stay. A filtered op must reject a bad `limit`/`leaseTime` exactly as its unfiltered sibling does — no
weaker validation on the filtered path (a validation gap on the security-relevant path is itself a defect).

---

## 7. Fail-closed uniformity and observability

**Decision (fail-closed uniformity).** Across every chokepoint, a candidate that is **non-`ATOMIC_DER` /
undecodable / over the projection budget (§4) / yields any `EvalOutcome.Error` (CANDIDATE_UNDECODABLE /
ABSENT_FIELD / AMBIGUOUS_FIELD / TYPE_MISMATCH / DIVISION_BY_ZERO / OVERFLOW / DOMAIN) / yields `BoolV(false)` /
escapes as any non-`VirtualMachineError` Throwable** ⇒ **no-match** (no capture on the take path, no delivery on
the event path), and is **counted**. There is exactly one "pass" outcome — `EvalOutcome.Value` carrying
`BoolV(true)` — and everything else excludes. This is the total-function property the `Evaluator` already
guarantees (`evaluate` returns a value or one of the closed eight errors), lifted to a binary
pass/exclude verdict at the chokepoint.

**The filter is pre-selection only, not an authorization boundary.** A candidate that *passes* the filter is
still subject to the taking client's **full gated deserialization** (the `DeSerializationPermission("ATOMIC")`
gate, `ResolutionContext`, `check(GetArg)`) when the client actually deserializes the returned entry. So a
false-positive (a candidate wrongly passed by a buggy/loose filter) costs **bandwidth** (the entry is shipped
and the client decodes it), **not integrity** — the filter never grants the client anything the unfiltered
query would not have shipped. This bounds the blast radius of any B3 evaluation bug to over-return, never
over-authorization, and is the reason fail-*closed* (exclude on doubt) is strictly safe: excluding a genuine
match is a correctness annoyance (the client can fall back to an unfiltered query), never a security failure.

**Observability counters (permanent metric names; extend `FilterAdmission.METRIC_NAMES`).** B1 declared
`filter.failClosedExclusions` (`FAIL_CLOSED_EXCLUSIONS`, operator-only) as the B3-populated exclusion counter.
B3 populates it and adds, in the same operator-only style (B1 §5 DECISION: operator-only, never on the client
result path — an exclusion count is itself a mild info-channel about data the client cannot see):

| Metric | Increment when |
|---|---|
| `filter.evaluated` | a candidate reached CEL evaluation (post-entitlement, post-byte-match) — the denominator |
| `filter.passed` | a candidate evaluated to `BoolV(true)` |
| `filter.failClosedExclusions` | a candidate excluded by a fail-closed rule (undecodable / missing-or-ambiguous field / CelError / budget / escaping Throwable) — **distinguishes a fail-closed no-match from an honest empty result** |
| `filter.excludedFalse` | a candidate cleanly evaluated to `BoolV(false)` (an honest predicate rejection, distinct from a fail-closed exclusion) |
| `filter.rejected.projectionBudget` | Option-B budget exceeded on a candidate (a sub-category of failClosedExclusions, broken out so lock-hold-DoS attempts are visible) |

Separating `filter.excludedFalse` (honest predicate false) from `filter.failClosedExclusions` (fault-driven
exclusion) is what lets an operator tell "the predicate is working and rejecting non-matches" from "candidates
are being dropped because they won't decode / lack the field" — the diagnostic B1 §5 asked for, made concrete.
**[OPEN — BOARD]:** confirm these five names as permanent API now (they extend `METRIC_NAMES`), matching B1's
practice of fixing metric names before the code that populates them.

---

## 8. Demo7 + confused-deputy proof plan

Following the demo5/demo6 born-DER Outrigger pattern; the class-free property is the headline.

### 8.1 Setup

- **Type absent from the server classpath:** `WeatherReading { Double temperatureCelsius; String stationName }`
  — compiled into the *client* only; the Outrigger server is started with a classpath that **provably lacks**
  `WeatherReading` (assert via a server-side `Class.forName("...WeatherReading")` that must throw
  `ClassNotFoundException`, printed in the demo output, as demo5/6 do for their absent type). This proves the
  server filters **class-free**: it never loads the entry class.
- **Author the predicate** on the client:
  `EntryFilter.compile("temperatureCelsius > 20.0 && stationName.startsWith(\"North\")",
  WeatherReading.class)` → `byte[] filterEnvelope`. This is a *value* predicate (`> 20.0`, `startsWith`) that
  positional byte-equality matching **cannot** express — the whole point.
- **Write** a spread of entries: warm-northern (temp>20, station "North*"), warm-southern, cold-northern,
  cold-southern — enough that only the warm-northern ones satisfy the predicate.

### 8.2 Class-free server-side filtering (the headline assertion)

- Cast the space proxy to `FilteredJavaSpace`; call filtered `contents(Collections.singletonList(matchAny),
  txn, lease, limit, filterEnvelope)` and filtered `take(..., filterEnvelope)`.
- **Assert:** exactly the warm-northern entries are returned — computed **server-side, class-free** (the server
  evaluated `temperatureCelsius > 20.0 && stationName.startsWith("North")` against each candidate's own v2
  schema without loading `WeatherReading`). Assert the count and identities precisely; assert
  `filter.evaluated > filter.passed` (some candidates were evaluated and cleanly rejected) and
  `filter.failClosedExclusions == 0` (every candidate decoded fine — no fault-driven exclusions in the happy
  path), to prove the exclusions were *honest predicate* rejections, not fail-closed drops.

### 8.3 The confused-deputy proof (INV-1, the security headline)

- **Client P** (a *non-participant* in a transaction) registers a filtered standing query / issues a filtered
  `take` with the warm-northern predicate.
- **Client Q** writes a warm-northern `WeatherReading` (temp 25.0, station "North Ridge") **under its own
  transaction `Tq`** — a **txn-private** entry, visible only to `Tq`'s participants. P is not a participant.
- **Assert (predicate never ran against the private entry):**
  1. P's filtered query does **not** return Q's txn-private entry (INV-1 — P is not entitled to see it).
  2. **The predicate was never *evaluated* against it** — not merely "matched but withheld." Prove this with the
     observability counters (operator-side probe, as B1 keeps them operator-only): a per-registration
     `filter.evaluated` delta shows **zero** evaluations attributable to Q's private transition on P's watcher
     while `Tq` is uncommitted. (Implementation of the probe: the demo reads the operator metric snapshot before
     and after Q's write; the private transition, gated out at `transition.getTxn() != P's txn` in the watcher
     *before* CEL, contributes no `filter.evaluated` increment.) This is the concrete, observed evidence that the
     entitlement gate precedes evaluation (INV-1), not just that the result was filtered — the distinction the
     Board's confused-deputy hunt insists on (mechanism, not outcome). **Nit:** `FilterAdmission`'s counters
     (`filter.evaluated` et al.) are **global static** metrics, not scoped per-registration or per-server-instance
     by the counter API itself; the demo's "per-registration `filter.evaluated` delta" is only a valid,
     attributable signal because the demo runs as a **single quiescent scenario** — one server, one registration
     of interest, no concurrent filtered traffic between the before/after snapshot. Any concurrent filtered
     activity during the probe window would add noise to the same global counter and invalidate the delta's
     attribution to Q's specific transition. This is fine for a demo/proof but is not, by itself, a per-registration
     observability primitive — a future multi-tenant diagnostic would need a scoped (per-`FilterSet`/registration)
     counter, out of B3 scope.
- **Assert (matches after commit):** Q commits `Tq`. The entry becomes globally visible
  (`transition.getTxn() == null` semantics on commit). Now P's filtered query **does** evaluate the predicate
  against it and **returns** it (it is warm-northern). This closes the proof: the predicate ran exactly when —
  and not before — P became entitled to observe the entry.

### 8.4 Slice / what demo7 exercises

Demo7 exercises: chokepoint A (filtered sync `take`), chokepoint B (filtered `contents` materialization +
continuation), chokepoint E/fan-out (filtered standing query with the per-txn entitlement gate), §3 null-key
per-candidate resolution (match-any template + schema-less filter over the candidate's own schema), §4 budget
(a large-field entry to show the budget bounds lock-hold without failing the happy path), and §7 counters. It is
the end-to-end proof that filtering is class-free, value-expressive, fail-closed, and confused-deputy-safe.

---

## 9. Module + dependency summary (B3 adds no new module, no new client API)

| Module | Adds (B3) | Notes |
|---|---|---|
| `outrigger-service` | `FilterSet` (per-template plurality carrier); confirm-window + watcher evaluation wiring; a slice-reuse projection factory (§4.2); `MAX_PROJECTION_DECODE_BYTES` (§4.3 Option B) + new counters (§7) | all internal; consumes B1 `CompiledFilter`/`FilterAdmission` + B2 `EntryProjection` + `jgdms-cel` `Evaluator` |
| `jgdms-cel` | **nothing** under the recommended Option B | Option C (deferred) *would* add an `Evaluator` budget overload + a STD-011 §9.1 error-set change — explicitly not in B3 |
| `outrigger-dl` / `jgdms-lib-dl` | **nothing** | no new wire format, no new client API; B3 is pure server-side behaviour behind the B1 seam |

B3 is the smallest-surface unit of Part B: it changes **behaviour** (the eight chokepoints now evaluate), not
**format** or **API**. That is by design — B1 fixed the permanent surfaces precisely so B3 could be a behaviour
change reviewable in isolation.

---

## 10. Open questions carried to the board / Peter

1. **`[RATIFIED Peter 2026-07-26]` Per-eval cost ceiling (§4).** Ratified: **Option B**, per-candidate
   **`MAX_PROJECTION_DECODE_BYTES = 64 KiB`**, fail-closed + counted, layered on §4.2 slice reuse, **PAIRED WITH**
   a `jgdms-cel`-internal evaluator fix (lazy `O(min)` string ordering + per-candidate-bounded `size(string)`) that
   makes `CostModel`'s existing `O(min)` cost-tightening assumption actually true; **Option C (CEL step meter)
   remains deferred**. Peter ratified: (a) B-plus-evaluator-fix over C, (b) the 64 KiB value (= `MAX_SCALAR_BYTES`,
   the uniquely coherent choice, not merely a midpoint between 16 KiB/256 KiB alternatives), (c) that bounding
   per-candidate work (not an overall per-query wall-clock cap) adequately addresses the lock-hold/B4 coupling.
   See §4.4.
2. **RESOLVED — `contents` continuation filter-drop (§1.4, B1 F-2).** The memo's prior description ("the
   continuation re-fetches through unfiltered `JavaSpace05.contents`") was stale/incorrect: `MatchSetProxy.next()`
   calls `space.nextBatch(...)`, which server-side continues the **same, already-filtered** `ContentsQuery` /
   `ContinuingQuery` state — there is no separate unfiltered re-fetch. Resolution: thread the `FilterSet` into
   `ContentsQuery` state at construction so every continuation batch is filtered by construction; no
   full-materialization fallback is needed. Closed.
3. **`[RATIFIED Peter 2026-07-26]` Legacy admin iterator exemption (§1.5, site F).** `IteratorImpl.nextReps`
   (JavaSpaceAdmin) stays unfiltered-but-**structurally guarded**, rather than gaining filter support — ratified
   conditional on B3 shipping an actual structural/architectural test (not merely this memo's prose) proving no
   filtered op ever reaches `nextReps`. The exemption is a mechanism to build, not a standing promise.
4. **[OPEN — BOARD] Metric-name set (§7).** Confirm the five permanent names
   (`filter.evaluated`/`filter.passed`/`filter.excludedFalse`/`filter.failClosedExclusions`/
   `filter.rejected.projectionBudget`) now, extending `FilterAdmission.METRIC_NAMES`, per B1's fix-names-early
   practice; and confirm operator-only (no client-visible exclusion count), consistent with B1 §5.
5. **[OPEN — BOARD] `FilterSet` key-collision / de-dup (§5).** Confirm "one envelope per op" implies at most one
   predicate per distinct schema, so `byDigest` de-dupes by digest and a same-schema duplicate template is not a
   rejection.

### Places the B1 design memo (§6.1/§6.2) was ambiguous or required a design call

- **§6.2 said "retain one `CompiledFilter` per template" but not *how a candidate is routed to its filter*.**
  Resolved here as `FilterSet` + `applicableTo(candidateDigest)` with the all-applicable-must-pass rule (§5),
  including the schema-less-applies-to-all overlay from §6.1.
- **§6.1 pins digest==key but not *where per candidate* it runs relative to projection.** Resolved: the
  non-null-key comparison runs **before** projection decode (cheap, leak-free drop of wrong-schema candidates);
  the null-key case relies on the evaluator's existing absent/ambiguous→error→fail-closed chain (§3).
- **The confirm-window placement was *proposed* in the pushdown-design memo but "do NOT pre-ratify"** pending the
  cost-ceiling advice. This memo pairs the placement (INV-2, §1.4) with the ceiling analysis (§4) so the board
  can ratify them together, as DECISION-2 intended.
- **Fan-out escaping-exception hazard was named in the pushdown memo but its exact mechanism (the journal loop's
  `RuntimeException` re-throw at `OperationJournal.java:478–479`) is pinned here (§2.3)** — the wrap is not
  belt-and-braces, it is load-bearing: without it, one hostile filter kills all event delivery server-wide.
- **The B2 `decodeToScalarFieldMapClassFree`-decodes-all-nested-scalars-even-for-`has(obj)` note** is addressed
  concretely by Option B's cumulative budget across the nested decode (§4.3) — the memo left the *bound* to B3.

---

*End of B3 design memo. No implementation code is part of this unit; the enumeration in §1.3, the entitlement
mechanism in §2, the digest==key rule in §3, the ceiling options in §4, and the plurality structure in §5 are
the artifacts the board reviews and the cost ceiling in §4.4 is the one Peter ratifies before implementation.*
