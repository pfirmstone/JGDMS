# Handover — discovery identity after DirtyChai's platform-thread Subject change

**Written:** 2026-08-29. **Author:** assistant session `7782d0b3`, first-hand for everything below
unless marked otherwise. **Branch:** `fix/reggie-discovery-backend-proxy`, all work **UNCOMMITTED**.
**[M] Branch is 4 ahead / 40 BEHIND `origin/trunk`** (verified `git rev-list --left-right --count`
2026-08-29) — a rebase is owed before any of this lands.

⚠ Written while agent **MultiSubj** was still editing `SubjectAwareExecutor.java`. That file's state
is not covered here.

Every claim is tagged `[M]` measured / `[H]` hypothesis / `[A]` assumption. Figures carry their
population and scoring rule inline — a number without its condition is not portable.

---

## 1. Root cause, in one sentence

DirtyChai commit `cc09a47009c` ("Subject propagation by Virtual Threads, but not platform Threads,
for fail closed behaviour") stopped platform threads inheriting the scoped `Subject`; JGDMS relied
on that inheritance in four services, so discovery threads lost their identity, could sign nothing,
and the node went silent.

`[M]` `java/lang/Thread.java:847` — platform constructor assigns `scopedSubject = new Subject[0]`.
`[M]` `:895` — virtual constructor assigns `VM.isBooted() ? SubjectAccess.scoped() : new Subject[0]`.
`[M]` `:1714` — `runWith` re-establishes the captured array around the task.

---

## 2. MEASURED facts

**2.1 `Subject.callAs` IS sufficient for principal-scoped policy grants. Do NOT use `doAs`.**
Established by **two independent methods that agree** (a source trace and a probe — deliberately
run by different agents, blind to each other, because two readings of one file are one witness).
- Source: `callAs` binds only a ScopedValue (`Subject.java:625-632`); principals are injected at
  **check** time — `AccessController.getContext()` calls `SubjectDomainCombiner.currentAll()`
  (`AccessController.java:995`), which rebuilds the context with principals baked into each
  ProtectionDomain (`SubjectDomainCombiner.java:267-287`). Grant matching is on `pd.getPrincipals()`
  (`PrincipalGrant.java:264-272`).
- Probe: population = 1 principal-scoped grant × 4 conditions × 3 runs on the built DirtyChai JDK.
  Scoring rule = permission granted vs `AccessControlException`. **Controls held**: different-principal
  Subject DENIED, no-Subject baseline DENIED, never-granted sentinel permission DENIED,
  `CombinerSecurityManager` confirmed installed. Result: grant resolved under `callAs`.
- `[M]` `doAs` is deprecated and **throws `IllegalArgumentException` for a `UserSubject`**
  (`Subject.java:722-739`). It works for a *plain* `Subject` — which is what `LoginContext` creates
  (`LoginContext.java:582`) — so it works today and breaks on any migration to `UserSubject`.

**2.2 `Subject.callAs(null, action)` does NOT throw.** Measured this session on **both** JDKs:
DirtyChai 27 and stock 25 each returned `ran, current()=null`. Source agrees —
`requireNonNull` covers *action* only, and the tail is
`callNoCheck(action, subject != null ? new Subject[]{subject} : NO_SUBJECTS)`.

**2.3 An ACC carries principals, but NOT credentials.** `Subject.getSubject(acc)` ignores its
argument and returns `current()`. So `doPrivileged(action, context)` restores privilege and
authorization principals, but **cannot restore private credentials** — which is what X.500 signing
needs. This reconciles two agents' apparently conflicting reports and explains the failure exactly:
policy grants would have resolved; signing could not.

**2.4 `ThreadPool` leaks the submitter's Subject into pool tasks.** Population = 2 executor types ×
2 conditions × 3 runs. Scoring rule = `Subject.current()` observed on the worker thread.
- virtual executor + submit inside `callAs(subj)` → **`subj` (LEAK)**
- virtual executor + no subject bound (control) → `null`
- platform executor, same pattern → `null` (no leak)
Mechanism: a virtual thread's `Thread` is constructed **per task at `submit()`, on the submitting
thread**, capturing its binding. **Mutation-tested** — reverting `Task.run()` on a scratch copy
reproduced the leak, so the probe detects the actual defect.
`[M]` `ThreadPool`'s class javadoc claimed "Every task is wrapped in `Subject.callAs(null, task)`";
`Task.run()` did no such thing. The documented mitigation **did not exist in code**.
`[M]` This also reconciles the apparent contradiction with `Subject.java:521-524` ("Executor tasks do
not inherit"): that javadoc is **true for platform pools** and predates virtual threads.

**2.5 Fail-closed is verified, not asserted.** With a SecurityManager denying exactly
`AuthPermission "callAs"`, `SubjectIsolation.runWithoutSubject` threw `SecurityException` and the
action's `actionRan` flag stayed `false` — the task was **refused**, not run under the caller's
identity. Operator's decision: keep this.

**2.6 `org.apache.river.api.security` is a genuine split package** — 13 classes in DirtyChai's
`java.base`, 37 in `jgdms-platform`. New JGDMS utilities go in `au.net.zeus.jgdms.api.*` instead.

**2.7 The unicast credential is a TLS server credential, not a signature.**
`discovery/ssl/Server.java:85` builds the endpoint with a null server Subject →
`useCurrentSubject = true` → `resolveSubjectIfNeeded()` reads `Subject.current()`
(`SslServerEndpointImpl.java:800`) → null on the `discoveryResponseExec` thread →
`UnsupportedConstraintException("Server credentials unsuitable for TLS")` → caught at
`Levels.HANDLED` (`DiscoveryV2.java:553`) → client sees **"format negotiation failed"** (`:573`).

---

## 3. What changed in the tree (all uncommitted)

**Discovery identity seams** — `AbstractLookupDiscovery.java` (`runWithDiscoverySubject` was a no-op
stub that discarded its Subject; now honours it); `RegistrarImpl.java` (announce seam, resolve-per-
iteration; unicast `respond`/`respondHttps` seam; announce-packet cache bounded by new config
`multicastAnnouncementPacketMaxAge`, default 5 min, `0` = legacy); `FiddlerImpl.java`,
`MailboxImpl.java` (August construction-time wrappers **removed as inert**; real seams at the
outbound event deliveries); `NormServerBaseImpl.java` + `norm/event/EventType.java`,
`EventTypeGenerator.java` (norm's original `callAs` was **NOT** inert — it wraps a real operation;
only its comment was false).

**ThreadPool leak** — new `jgdms-collections/.../au/net/zeus/jgdms/api/security/SubjectIsolation.java`
(170 lines, plain `Subject.callAs((Subject) null, action)`, fail-closed); `ThreadPool.java` wraps each
task; `bnd.bnd` exports the package. Placed in `jgdms-collections` because `jgdms-platform` depends on
it — the reverse would be a cycle.

**Diagnostics/正确ness** — `X500Provider.java` (WARNING recalibrated: routine "wrong key algorithm for
this format" → FINE; the remaining WARNING no longer advises checking thread-construction context,
which stopped mattering); `SpiffeSupport.java` (swallowed `catch (Exception) { return null; }` split
so a denial is visible and distinguished from benign method-absence; `Method.invoke` wraps callee
exceptions in `InvocationTargetException` — unwrap before classifying);
`SpiffeDiscoveryCredentialProvider.java` (`getSubject()` now returns **null** = "identity is ambient,
establish nothing", because a `WorkerSubject` cannot be bound by `callAs`).

**Still present deliberately** — `BUG1-DIAG` instrumentation across ~6 files, and
`qa1.logging:204` at FINEST. **Do not remove until `TerminateDiscovery` passes**; they are the
instrumentation for the run that tests all of the above. Also `qa/harness/configs/jsse/test.config`
carries an **EXPERIMENT** marked in-place (`unicastDiscovery` given
`ConstraintAlternatives{phoenix, reggie}`) — revert unless adopted.

---

## 4. NOT verified — read this before trusting section 3

- ⛔ **No runtime evidence for ANY discovery seam.** Every one is a source trace plus a clean compile
  (`mvn -o install -DskipTests` over 8 modules, `BUILD SUCCESS`). The qa run is the test and has not
  been run since the changes.
- ⛔ **No behaviour tested under a real SecurityManager** on the discovery paths. Every seam creates a
  policy requirement at its call site — `AuthPermission "callAs"`, plus `"getSubject"` and a
  `PrivateCredentialPermission` on the discovery path, and now `jgdms-collections` needs `"callAs"` on
  the JERI dispatch path. `defaultsecurereggie.policy` / `defaultsecuresharedvm.policy` are emptied
  3-line stubs. **With fail-closed, a missing grant is a total dispatch outage, not a degradation.**
  Whether `jgdms-collections`' code source is covered in every deployment policy is **unaudited**.
- `[H]` SPIFFE discovery is **not wired end-to-end** — no caller of `SpiffeSupport.processWorkerSubject()`
  exists yet; the spiffe multicast client/server are unwritten Phase 2 work.
- `[M]` `respondHttps` has **no caller** — `enableHttpsUnicast` only creates the provider and changes
  the advertised locator scheme (`//TODO: finish https discovery`). Seam added for symmetry; documented
  as dead code so no reviewer concludes https unicast works.

---

## 5. Traps that cost real time (all first-hand)

1. **Absence of a log line is not absence of the condition.** A WARNING never appeared; I reasoned from
   that twice before checking `qa1.logging`, where a later key was silencing the logger.
2. **A module not rebuilt = testing stale code.** A whole 11-minute run was wasted because
   `ClientPermissionChecker` lives in `jgdms-platform` and I had rebuilt only `jgdms-jeri` + `dist`.
   **Verify the fix is in the shipped jar** (`javap -c` out of `dist/target/.../lib/*.jar`).
3. **Bash cwd persists** — a `cd` into DirtyChai later broke `mvn -pl` with "Could not find the selected
   project in the reactor". Use absolute `cd` in every build command.
4. **A swallowed reflective probe degrades forever.** Bit this codebase twice: `SpiffeSupport`, and
   `SubjectAwareExecutor:146` where a wrong element type (`Subject[]` vs `UserSubject[]`) means the
   multi-subject path has **never executed on any JDK**.
5. **A comment claiming more than the code.** `ThreadPool`'s javadoc promised per-task isolation that
   did not exist. Four service comments asserted construction-time capture that DirtyChai had removed.
6. **Two readings of one file are one witness.** Cross-*method* (read vs measure) is what settled the
   crux; cross-*model* alone would not have.

---

## 6. Agents used (all reported their model ID; `fable` has silently run as Sonnet before)

`SubjSource` opus-5 · `Combiner` **fable-5** (read) · `Bench` sonnet-5 (measure) · `Seam`/`Seam2`
opus-5 · `Unicast` opus-5 · `Probe` sonnet-5 · `Ambient` sonnet-5 · `Stale` sonnet-5 · `NoSubject`
opus-5 · `Simplify` sonnet-5 · `MultiSubj` opus-5 (in flight at time of writing).

⚠ The `Agent` tool has **no effort parameter** — effort is session-inherited. Per-agent effort +
model belong in `.claude/agents/*.md` frontmatter; **neither that directory nor the user-level one
exists**. ⚠ The Write tool **hard-blocks subagent paths containing "FINDINGS"** — take findings as
text. ⚠ Skills `agent-dispatch` and `handover-composition` are recorded in memory as installed but
are **NOT** ("Unknown skill").

---

## 7. RESULTS — the qa run happened; read this before section 8

**7.1 ⛔⛔ FIRST RUN WAS VOID — the test certificates had EXPIRED.** `[M]` All qa keystores carry a
**5-DAY** validity (generated 18 Aug 22:36, expired 23 Aug 22:36). Phoenix could not export its SSL
endpoint — `CertificateExpiredException: NotAfter: Sun Aug 23 22:36:57 AEST 2026` — so the activation
system never started and the test died in `construct()` without touching discovery.
**The symptom presented as "ActivationSystemAdmin: activation system did not start"**, which reads like
a service/config fault, not an expiry. **Fix: `ant clean-trust trust`** (trust files are gitignored
build artifacts; regenerating disturbs nothing tracked). ⚠ Any run more than 5 days after generation
fails this way. Widening that validity would delete a whole class of wasted 11-minute runs.

**7.2 SECOND RUN — the server side is FIXED, verified at runtime for the first time.**
`[M]` Population = one run, `TerminateDiscovery.td`, DirtyChai JDK, polpAudit SM.
- **30 × `ALD.announcement annGroups=<REAL> alreadyKnown=false groupsOverlap=true pending=LookupLocator`**
  — reggie signs, the client accepts, group match succeeds, unicast is queued. Compare the 18 Aug
  baseline: reggie signed **nothing** ("0 principal(s) and no private credentials", "no encoded requests").
- Scoring rule = presence of the diagnostic line; population = whole run.

**7.3 The remaining failure is entirely CLIENT-side, one cause.** `[M]`
- 44 × "no X.500 private credentials" — **all** in `AbstractLookupDiscovery$Requestor`.
- 15 × `UnsupportedConstraintException: "Client must be logged on and caller must do as"` at
  `SslEndpointImpl.getCallContext:348` ← `discovery/ssl/Client$ClientImpl.getEndpoint:83`.
- 225 UnicastDiscoveryTask attempts, **`maybeAddNewRegistrar` reached 0 times** (42 in an earlier
  experiment) ⇒ unicast never completes ⇒ **0 of 3 discovery events**.
- Cause: `getDiscoverySubject()` returns null, so the seam takes its passthrough branch.
  ⭐ Source trace and runtime agree — Combiner predicted that exact exception string from
  `SslEndpointImpl.getCallContext:297-349`.

**7.4 `SubjectAwareExecutor` multi-subject path FIXED and measured.** `[M]` A/B on the DirtyChai JDK,
population = 5 scenarios × pre/post build. Scoring rule = `currentAll()` observed on the worker:
line E (2 subjects via executor) **pre `[[alice]]` → post `[[alice],[bob]]`**, matching control B (the
submitting thread); controls A/C/D unchanged. `[M]` `javap` output byte-for-byte identical pre/post —
**no API/binary-compat gate impact**; every added member is `private static`.
`[M]` `UserSubject` **is** constructible from JGDMS (`public final`, public constructor) — the only
barrier had been the wrong reflective element type.

## 8. RULING — remove the `discoveryCredentialProvider` SPI (operator-approved 2026-08-29)

A board-guided critical review (agent **Critic**, fable-5, per `JGDMS-Board-Reviewer-Guidance.md`)
returned **REMOVE the SPI, keep the binder**. Operator approved. Agent **Remove** (opus-5) was
implementing at the time of writing — **its state is not covered here.**

**Provenance, verified first-hand by me (not relayed):**
- `[M]` `c89faa84b` (2026-05-15) added the SPI **with a working binder**: `return Subject.doAs(discoverySubject, action);`
- `[M]` `2c1b27a6a` (2026-05-16, `copilot-swe-agent[bot]`, "Remove Subject.doAs wrapping from discovery
  credential flow") **deleted that binding and softened the SPI javadoc** in the same commit. The unit
  test went later (`ac280695d`). An amputation with the documentation adjusted to match — evidence the
  contract was unimplementable from day two, not merely unfinished.
- `[M]` `@since 3.1.1` names an unreleased version — the only tag is `jgdms-3.1.0`. **Removal costs
  nothing in release compatibility.**
- `[M]` Configured in **zero** `.config`/`.properties` in repo history; absent from `LookupDiscovery`'s
  normative javadoc config table.

**Why:** the implementation space is empty. SPIFFE is *prohibited* from satisfying the contract
(`callAs` throws `IllegalArgumentException` for a `WorkerSubject`); a conventional implementation
degenerates to a constant function returning the login Subject the caller already holds. Both shipped
implementations now return null.

**Replacement:** keep `runWithDiscoverySubject` as built; feed it a Subject **captured at
`AbstractLookupDiscovery` construction**. That — not the SPI — is what fixes §7.3.

**⛔ Two hazards the implementer was told to handle, and the reviewer must check:**
1. **Never capture a `WorkerSubject`** (ambient, rotates hourly, "must never be captured for later
   use"). It cannot be named at compile time (absent on stock OpenJDK). Letting `callAs` throw and
   swallowing it is the fail-open pattern the review criticised.
2. **The capture may be vacuous.** `ADVICE …§9` records construction-scope capture as UNVERIFIED. If
   `Subject.current()` is null at construction on the qa path, the fix does nothing and looks correct.

**Correction the review made to my design, accepted:** my null branch conflated three states — no
provider, ambient identity, **and provider threw**. The third was **fail-open**: a broken provider
silently ran under whatever ambient identity existed, potentially signing as the wrong principal —
while `SubjectIsolation`, written hours earlier, chose fail-*closed* on the same class of question.
Correct rule: *unconfigured → passthrough; configured-but-failing → fail closed and loud.* Removing
the SPI dissolves the ambiguity structurally instead of relying on vigilance.

**Open, non-blocking (review F3):** the unicast seam (`~:3142`) wraps the **whole** exchange, so
untrusted-bytes deserialization and the codebase-annotation RPC run inside the discovery Subject's
scope; the multicast seam (`~:826`) is correctly narrow. Whether binding is needed past the TLS
handshake was not established.

## 9a. ⭐ RESULT OF THE RUN AFTER THE SPI REMOVAL — first discovery event ever

`[M]` Population = one run, `TerminateDiscovery.td`, full reactor build + rebuilt `dist`, fresh certs,
policy files reset. **1 of 3 discovery events** (every prior run today, and the 18 Aug baseline: **0**).

Discriminating counts, versus the previous run (§7.3):

| signal | before | after |
|---|---|---|
| "no X.500 private credentials" (client `Requestor`) | 44 | **0** |
| `ALD.maybeAddNewRegistrar` | 0 | **18** (6 each × 3 registrars) |
| discovery events | 0 | **1** |
| "Client must be logged on and caller must do as" | 15 | 54 |

⭐ **The construction-time capture is PROVEN non-vacuous** — the hazard flagged as the one that would
make the fix "look correct and do nothing". Client signing works, all three registrars are reached
(`[::1]:4160`, `:41409`, `:46759`, 6 adds each), unicast completes.

⛔ **The 54 remaining failures are in classes we never touched** — attributed by walking back from each
occurrence: **`LookupDiscoveryManager` ×108** and **`AbstractLookupDiscovery$Notifier` ×36** (counts are
of stack-context hits, not of distinct failures; the 54 is the exception count). This is exactly
review finding **L3** — "other constructors degrade to null → unsigned" — now measured rather than
inferred, plus a site nobody predicted:
- `[M]` **`LookupDiscoveryManager`** is a different class with its own construction; it received no
  capture. The qa test uses it.
- `[H]` **The Notifier / event-delivery path** has no seam. Registrars are discovered but delivering
  the `DiscoveryEvent` appears to need identity the Notifier platform thread does not have — the most
  probable reason 3 registrars yield only 1 event.

**Next fix, precisely scoped:** apply the same construction-time capture + `runWithDiscoverySubject`
seam to `LookupDiscoveryManager` (and check `LookupLocatorDiscovery`), and add a seam to the Notifier's
delivery path. Then re-run; the expected signal is events 1 → 3.

## 9. Next step

Run `TerminateDiscovery` and read the result:

```
cd /home/user/GitHub/JGDMS/qa && JAVA_HOME=/home/user/GitHub/DirtyChai/build/linux-x86_64-server-release/images/jdk \
  ant -Drun.tests=org/apache/river/test/spec/joinmanager/TerminateDiscovery.td policy-update-run-tests
```

~11 min. **First check the certs have not expired (§7.1) — `keytool -list -v` on any keystore.**
It rewrites the tracked policy files: that is the polpAudit capture, and how the new `AuthPermission`
requirements get discovered rather than hand-written.

**Baseline to beat: 0 of 3 discovery events.** Progression so far (population = one run each):
announce denials 42→0 (SM-routing fix); unicast attempts 15→27; 294 TLS trust failures at a
newly-reachable stage; then **§7.2 — announcements accepted, 30×, server side fixed**; failure now
purely client-side (§7.3).

**Sequence from here:**
1. Agent **Remove** finishes the SPI removal + construction-time capture.
2. Agent **Critic** (`ac1e3a3c1cd5476d5`, fable-5, holds the review context) reviews Remove's work —
   operator's instruction: the reviewing agent reviews the implementation.
3. Compile (8 modules), **rebuild `dist`** (§5 trap 2 — the harness runs from `dist/target`, not
   `~/.m2`), re-run `TerminateDiscovery`.
4. Expected next signal: whether §7.3's 44 + 15 client failures go to 0 and `maybeAddNewRegistrar`
   becomes non-zero. `[H]` If the construction-time capture is vacuous (§8 hazard 2), nothing changes
   and the count stays at 0 — that is the discriminating observation.

**Open decisions for the operator:** policy-grant audit for `jgdms-collections` (fail-closed makes a
missing `AuthPermission "callAs"` a total dispatch outage); whether the `test.config` experiment is
adopted or reverted; the `ThreadPool` fail-closed behaviour now in place; rebase onto trunk (40 behind);
and whether to widen the 5-day cert validity.

---

## 10. SCOPE FOR THE NEXT SESSION (priority order)

**1. Finish the seam — the only work that moves the test.** `[M]` §9a localised it: apply the same
construction-time capture + `runWithDiscoverySubject` pattern to **`LookupDiscoveryManager`** (check
`LookupLocatorDiscovery` too), and add a seam to **`AbstractLookupDiscovery$Notifier`**'s event-delivery
path. Expected signal: discovery events **1 → 3**. The mechanism is proven; this is application, not
diagnosis. ⚠ Verify the Notifier hypothesis before coding it — it is `[H]`, not `[M]`.

**2. Resolve L3 properly for the services.** Do reggie/fiddler/mercury/norm construct their
`LookupDiscovery(Manager)` inside their login scope? **Measure it** (source trace + probe, the standard
set in §8 hazard b). Inferring this is precisely the mistake that produced the original bug.

**3. Policy-grant audit — this one can brick a deployment.** Every seam needs `AuthPermission "callAs"`
at its code source; discovery also needs `"getSubject"` + `PrivateCredentialPermission`; and
`jgdms-collections` now needs `"callAs"` on the **JERI dispatch path**. `SubjectIsolation` is
**fail-closed**, so a missing grant is a total dispatch outage, not a degradation. Harvest what
polpAudit captured, then audit real deployment policies.

**4. Cleanup, only once the test passes.** Remove `BUG1-DIAG` (~6 files), revert `qa1.logging:204` to
SEVERE, and revert-or-adopt the `test.config` `unicastDiscovery` experiment (§3). ⛔ Not before —
they are the instrumentation.

**5. Then: rebase.** `[M]` 4 ahead / 40 behind `origin/trunk`.

**Deferred, recorded, not scheduled:** review F3 (unicast seam wraps deserialization as well as the
handshake); reggie `dataPackets` re-sign-on-rotation (ADVICE §8.5); the signing-format-must-fail-loud
invariant (ADVICE §7); `net.jini.security.SubjectAwareExecutor` is fixed but its multi-subject path had
**never executed** — worth asking what else in this codebase has never run.

## 11. Ops notes for whoever runs the agents

- `[M]` **`model: "opus"` routing is inconsistent.** Agents today self-reported `claude-opus-5` *and*
  `claude-opus-4-5-20251101` for the same parameter. **Always require the model ID in the first line
  and check it.** Same for `fable`, which has silently run as Sonnet before.
- `[M]` The `Agent` tool has **no effort parameter** (session-inherited). Per-agent effort+model live in
  `.claude/agents/*.md` frontmatter — **neither that dir nor the user-level one exists**. Phasing the
  dispatch by effort worked; wait for a phase to *complete* before changing the level, since it is
  unknown whether effort binds at dispatch or per call.
- `[M]` The Write tool **hard-blocks subagent paths containing "FINDINGS"**. Take findings as text.
- `[M]` Skills `agent-dispatch` and `handover-composition` are recorded in memory as installed but are
  **not** ("Unknown skill"). `jgdms-board-review` IS installed and was used.
- ⚠ **Another session is editing this tree** — a `Thread`→`Runnable` refactor of the discovery worker
  classes. `git diff` on `AbstractLookupDiscovery` mixes two sessions' work; commit by pathspec.
- **Agents used** (all self-reported): `SubjSource` opus-5 · `Combiner` **fable-5** · `Bench` sonnet-5 ·
  `Seam2` opus-5 · `Unicast` opus-5 · `Probe` sonnet-5 · `Ambient` sonnet-5 · `Stale` sonnet-5 ·
  `NoSubject` opus-5 · `Simplify` sonnet-5 · `MultiSubj` opus-5 · `Critic` **fable-5** (review +
  re-review; holds context, resumable) · `Remove` **opus-4.5** · `Polish` sonnet-5.
- ⭐ **What actually worked:** cross-*method* verification (one agent reads, a different-model agent
  measures) settled the `callAs` question that two readings could not. Cross-*model* alone would have
  given false corroboration. Use it for anything load-bearing.

---

## 12. RESULT 2026-08-30 — `TerminateDiscovery` PASSES

`[M]` **`# of tests passed = 1`, `# of tests failed = 0`**, 713 s, `BUILD SUCCESSFUL`.
Log: `scratchpad/TD-notifier-seam.log` (9058 lines). One variable changed since §9a: the Notifier seam.

| metric | §9a (0829) | this run |
|---|---|---|
| "Client must be logged on" | 54 | **0** |
| "no usable credentials" | — | **0** |
| `TestException` | — | **0** |
| discovery events | 1 of 3 | **test-level assertion PASSES** |

⭐ **The pass is not vacuous.** `run()` calls `waitForDiscovery` **twice expecting success**
(`TerminateDiscovery.java:102,114`) before the terminate step, and `BaseQATest.waitForDiscovery`
**throws `TestException("discovery failed -- ...")`** on timeout. Reaching the terminate step therefore
proves discovery, Notifier delivery, and the listener's remote calls all completed.

### 12.1 The §10-item-1 plan was WRONG in three ways — corrected by verification before coding

- ⛔ **`LookupDiscoveryManager` needed NO capture.** It has no threads and performs no discovery; it
  composes `LookupDiscovery` (already captured) + `LookupLocatorDiscovery`, and its `notifyListener()`
  (`AbstractLookupDiscoveryManager.java:1232,1255`) delivers **synchronously on the caller's thread**.
  The ×108 "LDM" frames sit on the **Notifier's** stack. **The ×108 and ×36 were ONE failure counted at
  two frames** — [[false-corroboration-shared-source]], which I walked into writing §8.
- ⛔ **The mechanism was not "the Notifier needs identity for its own work."** It is the *listener's*
  remote calls: `BaseQATest$LookupListener.discovered()` calls `regs[i].getLocator()` (`:772`) and
  `getGroups()` (`:806`), **unguarded**, inside `catch(Exception e){ e.printStackTrace(); }` — the
  failure was **swallowed**, which is why it presented as missing events rather than errors.
- ⛔ **A wrong lead, killed cheaply:** I first suspected the ×36 was an artifact of the FINEST-only
  `getLocator()` at `AbstractLookupDiscovery:418`. `COMPONENT_NAME` = `net.jini.discovery.LookupDiscovery`,
  set to **FINE** in `qa1.logging:206` ⇒ that block never runs. Not the cause.

### 12.2 Why the seam is a RESTORATION, not a privilege elevation

The `doPrivileged` at `:385` deliberately restores the **client's** context to restrict possibly-untrusted
listener code. Injecting a subject there looks like the confused-deputy direction just closed in
`ThreadPool`. It is not, and the reason is specific: `discoverySubject` is captured at construction, the
**same moment** as `securityContext` (`:192`), and the Notifier thread is created **on the client's
thread** (`:1809`) — so before `cc09a47009c` it *inherited exactly this Subject*. The seam restores prior
behaviour. ⛔ This argument is load-bearing: it would NOT hold for a subject captured anywhere else.

⭐ It also documents a **4th comment-outclaims-code**: `:368-384` says "the client's Subject should be
restored" — an **ACC carries principals, not credentials**, so that code never did, and under DirtyChai
it cannot.

### 12.3 Still open — do NOT read the pass as "all clear"

- ⛔ `[M]` **`AbstractLookupLocatorDiscovery` has the SAME defect** — `private class Notifier extends
  Thread` (`:416`), platform. **Dormant in this test only because it uses groups, not locators.** Left
  untouched deliberately so this run changed one variable. **This is the next real bug.**
  ⚠ **CORRECTION (2026-08-30, on reading the class):** I wrote above that its `doPrivileged` (`:477`)
  "does not even restore the client context". **That was WRONG** — it restores `loginContext`, captured
  by `AccessController.getContext()` in the Notifier constructor (`:424`). The defect is therefore
  *identical* to `AbstractLookupDiscovery`'s, not worse: an ACC carries principals, not credentials.
  ⛔ I asserted a comparative severity from a `grep` of member names without reading the constructor.
- ⚠ `[U]` **236 `UnsupportedConstraintException` remain** (105 `ClientMinPrincipal{CN=Reggie}`, 45 bare
  `Integrity.YES`, 42 `CN=Tester`, 30 `ServerMinPrincipal{CN=Reggie}`, 14 `ClientAuthentication.YES`).
  Plausibly ordinary provider/format negotiation churn — **but NOT baselined**: the §9a log was pasted,
  not retained, so no before/after comparison exists. **Do not claim these are unchanged.**
- ⚠ `maybeAddNewRegistrar` 18 → 4. Expected (the test now completes early instead of retrying), but
  **inferred, not measured**.
- Item 4 cleanup (`BUG1-DIAG`, `qa1.logging:204`, the `test.config` experiment) is now UNBLOCKED —
  the gate was "once the test passes".

**Revised next-session order:** (1) fix `AbstractLookupLocatorDiscovery`'s Notifier; (2) §10 item 2
(measure the services' login scope); (3) §10 item 3 policy-grant audit — still the one that can brick a
deployment; (4) cleanup; (5) rebase. ⛔ One test passing is ONE test — the wider suite is unrun.

---

## 13. `LookupLocatorDiscovery` FIXED + REVIEWED — 2026-08-30

`[M]` **`locatordiscovery/Discovered.td` PASSES** (was 0 of 3 events); `TerminateDiscovery.td` still
passes. Logs retained in `scratchpad/`: `LLD-seam`, `LLD-unicast-seam`, `LLD-classloader`,
`ABLATION-no-notifier-seam`.

### 13.1 THREE defects, found by layered failure — each hid the next

| # | change | evidence |
|---|---|---|
| 1 | capture + `runWithLoginSubject` helpers (one outer field `loginSubject`) | capture fired **10×** with a real Subject |
| 2 | seam around **unicast discovery** (`LocatorReg$1.performDiscovery`) | "Client must be logged on" **24 → 0**; `Subject.callAs` seen in a live stack |
| 3 | seam around **event delivery** (`Notifier.run`) | **ablation, below** |
| + | `defaultLoader`/`verifierLoader` were **`null,null`**; now the class's own loader, mirroring ALD `:3205-3206` (operator-directed) | `NoClassDefFoundError: net/jini/export/ProxyAccessor` **24 → 0** |

⛔ **The classloader defect is PRE-EXISTING and unrelated to DirtyChai** — it would fail identically on
stock OpenJDK. It was invisible until fix 2 let discovery reach deserialization.
⛔ **I fixed the wrong layer first** (delivery before unicast) by pattern-matching to yesterday's
delivery bug. The recorded rule — *work handed to a PRE-EXISTING executor does not inherit the scope* —
was already in memory and would have aimed the first attempt correctly. Cost: one 22-min run.

### 13.2 Ablation `[M]` — change 3 is NECESSARY, prediction pre-registered and HELD

Reviewer `Critic` (fable-5) lodged its prediction **before** the run. Reverting change 3 alone:
`Discovered.td` **FAILS, 1 of 3 events**, and all three discriminating observables matched —
"Client must be logged on" **0 → 9**; failures on the **delivery** stack
(`Notifier.run:547` → `BaseQATest$LookupListener.discovered:772` → `getLocator()`); **0** on the unicast
stack. ⭐ This is [[precommitment-needs-external-witness]] working as intended.

### 13.3 ⛔ MY RESTORATION ARGUMENT WAS FALSE — right conclusion, wrong mechanism

I argued the seam "restores what the Notifier inherited before `cc09a47009c`". **Refuted.** DirtyChai
propagates the Subject by **wrapping `Thread.run()`**; LLD's `Notifier extends Thread` and **overrides
`run()`**, so the wrapper never fires — measured in **`DESIGN-discovery-credential-resolution.md` §11.7**
(2026-08-09, live stack comparison). That thread never inherited the Subject on **either** side of the
commit. ⛔ **The refutation was already on disk in this repo and I did not consult it** —
[[unearned-true-claim]]: correct claim, mechanism the author never had, invisible to any reviewer
reading only the page.

⭐ **The correct baseline (Critic's) is stock-JDK/SecurityManager:** a Thread captured its parent's
`inheritedAccessControlContext`, the Notifier restored the construction-time ACC explicitly, and
`Subject.getSubject(acc)` reached the **whole Subject, private credentials included**, via the
`SubjectDomainCombiner`. Listener callbacks always could and did sign as the client — by design. The
seam restores that under ScopedValue. Comments in **both** files now carry this and explicitly forbid
restating the inheritance version. ⚠ §12.2 above retains the false claim — **superseded by this section**.
⚠ Nuance Critic flattened: ALD's `Notifier` is `implements Runnable` in the **working tree** (uncommitted
Runnable refactor) but `extends Thread` at **HEAD**, which is the historical baseline that matters.

### 13.4 Confused-deputy: ANSWERED, not re-opened `[M]`/`[H]`

- *Signing as the client* — not new; historically intended (13.3).
- *Extracting credentials* — gated: `Subject.current()` checks `AuthPermission("getSubject")`
  (`Subject.java:386-393`), the same gate the stock-era route had. Nesting is load-bearing and correct:
  **`callAs` OUTSIDE, `doPrivileged(loginContext)` INSIDE**, so extraction evaluates against the client's
  ACC ∩ the listener's own domain. ⛔ **Do not reorder** — now stated in the code.
- *vs the `ThreadPool` leak* — different vector. ⭐ **Discriminator worth keeping: a seam is a deputy
  hazard when the bound identity's owner DIFFERS from the principal whose work the code is doing.**
  Here: owner's identity, owner's callbacks. There: arbitrary submitter, other principals' work.

### 13.5 Open

- ⚠ `[M]` **`registrarPreparer.prepareProxy` runs OUTSIDE the unicast seam** (`:379-380`) while
  deserialization runs inside — the boundary is unprincipled in **both** directions. A *verifying*
  preparer would fail with exactly this saga's swallowed-error signature. Folds into review item **F3**,
  which should place the boundary deliberately: narrow (handshake only) or complete (through
  preparation).
- `CAPTURABLE_SUBJECT_TYPES` now exists **twice** in `net.jini.discovery`; one package-private holder
  would remove the drift hazard the "Kept identical to" comment currently manages by vigilance.
  Cleanup-priority.
- ⚠ `[U]` 335 `UnsupportedConstraintException` and 96 `SocketException: Network is unreachable` remain,
  **unbaselined** — do not claim they are unchanged.
- ⛔ `qa1.logging` `LookupLocatorDiscovery` raised SEVERE → CONFIG as `BUG1-DIAG`; revert with the rest.
- ⛔ Critic's grants warning stands: `"callAs"`/`"getSubject"` are absent from the emptied service
  policies — **every seam throws the day a SecurityManager returns**. This is §10 item 3.
