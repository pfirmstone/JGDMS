# Handover: legacy-authority analysis → privilege-ceiling design → java.base conversion as the load-bearing change

**AS-OF 2026-10-22 22:50 AEST. JGDMS branch `claude/focused-bohr-t6bwal` at `93bcc9f98`; DirtyChai `trunk` at `ed4691471e3` (source: `git rev-parse --short HEAD` in each, run while writing this line).**
**Amended 2026-10-23 10:20 AEST (same session, before compaction): §3 item 9 and §5 rewritten for design-note revision 4 (§13); the git stamps above were NOT re-taken.** Everything after those commits is not reflected here: run `git log --oneline 93bcc9f98..HEAD` and `git log --oneline ed4691471e3..HEAD` first.
Written by the session that did the work (Claude Fable 5.1, `claude-fable-5-1`), BEFORE any compaction, with the live
conversation as the source, composed against `handover-composition` (pfirmstone/Cross-machine_Claude_Code_skills, read
2026-10-22 from the connected folder). The reader is me after compaction: this is consolidation, not transmission. It has
the same error rate as any other document I write (§7 counts the errors I made this session); nothing in it was reviewed
by a different model except where §2 says Opus did.

## 1. Where the truth lives

| Question | File | Status (its own header) |
|---|---|---|
| The design, four revisions, the `CombinerSecurityManager` impact, §12 (conversion as load-bearing) and §13 (universal per-class reduction, supersedes ceiling and sweep) | `DirtyChai/docs/DESIGN-privilege-ceiling-2026-10-19.md` | r4, "not implemented, not scheduled"; **read §13 first, then §12** |
| Serial data identity: provenance domains on deserialized objects, collected from frame receivers by the native walk; slices and where each runs (host vs container); AI-policy constraints per slice | `JGDMS/docs/DESIGN-serial-data-identity-2026-10-23.md` | r1, not implemented; written 21:30 AEST 2026-10-23 from a Sonnet recon index |
| The findings the design answers (A1–A10, N1, M1–M13, doPrivileged inventory) | `JGDMS/docs/ANALYSIS-legacy-API-authority-and-reconstruction-2026-10-19.md` | analysis only; §2 now carries a "Superseded" paragraph pointing at the design note |
| The abandoned check-and-inject plan the analysis references | `JGDMS/docs/PLAN-check-and-inject-unprivileged-construction-2026-10-19.md` | not scheduled, not implemented (not read this session) |
| DirtyChai's AI-contribution rules | `DirtyChai/CONTRIBUTING.md` line 5; `DirtyChai/AI_POLICY.md` §1, version table | in force (1.1 partially adopted) |
| The handover rules this document follows | `Cross-machine_Claude_Code_skills/handover-composition/SKILL.md` | — |
| Review heuristics for JGDMS | `.claude/skills/jgdms-board-review/SKILL.md` | not loaded this session |

No index file: the session produced three on-disk artefacts (the two documents above and this one) plus one stray copy
(§2); that is the whole index.

## 2. State, with provenance

VERIFIED = a command I ran this session and read the output of. RELAYED = read from a document or from the operator's
relay of an Opus session, not checked against code.

- VERIFIED: `DirtyChai/docs/DESIGN-privilege-ceiling-2026-10-19.md` exists, 478 lines after the authorship note
  (`wc -l`), r1 and r2 committed by the operator (`git log`: `001c858f7f4` create, `4ac4fd6719f` update); **r3, §11
  (CSM), §12, the authorship note and now §13/r4 are an UNCOMMITTED diff** (`git diff --stat` showed +63/−1 before §13; §13
  adds ~70 lines; file is 552 lines by `wc -l` at 10:20 AEST 2026-10-23).
- VERIFIED: the analysis document is tracked at `JGDMS/JGDMS/docs/` (commit `86232025f`), not at the repo-root
  `JGDMS/docs/` where I first wrote a duplicate. The duplicate is moved, not deleted (deletion is off in connected
  folders): `JGDMS/docs/_to_delete/ANALYSIS-legacy-API-authority-and-reconstruction-2026-10-19.md`. **Delete it.** The
  real file has an UNCOMMITTED 12-line insertion (the "Superseded" paragraph, `git diff --stat`).
- VERIFIED: no `handover` skill exists in either repo's `.claude/skills` (`ls`), nor in this session's skill list; the
  skill is `handover-composition` in the separate skills repo.
- VERIFIED by reading source (not by running anything): the DirtyChai `AccessController`/`AccessControlContext`/
  `DomainIdentity`/`ProtectionDomain`/`SubjectDomainCombiner` behaviours cited in the design note §1, §2, §8 — line
  references are in the earlier parts of this conversation, not in the note.
- RELAYED (operator relaying Opus, 2026-10-19 evening): the "ceiling caps elevation only" model, the ~406 sites / 222 files
  count for java.base `doPrivileged`, the "85% of a check is `getContext()`" figure (`ContextCaptureSplit` benchmark —
  not run or read by me), the 1.7 µs / 4 µs check-cost figures (operator, 2026-10-22, no source named).
- RELAYED (operator): `SecurityPolicyWriter` is polpAudit's SecurityManager; `Subject` ScopedValue is inherited by
  virtual threads in the fork (javadoc read, mechanism not examined).
- NOT DONE: no code changed in either repo; no tests written or run; no sub-agents used (zero `Agent` calls this
  session — the only parallel work was the operator's separate Opus session, relayed by paste).

## 3. Decisions taken (operator, in order; each overturned part of what came before)

1. A ceiling must be policy-granted, never a static permission list (2026-10-19 14:39). Killed the r0 `ceiling(Permission...)`.
2. Identity = caller codesource + a message; `Callable`, not `PrivilegedAction` (15:00).
3. The ceiling is scoped like `Subject`: a `ScopedValue` inherited by virtual threads only (16:49). Killed `ROOT`
   and the per-thread default; killed the lazy-JDK-singleton problem.
4. The binding accumulates (immutable array, previous + new) and never replaces; `ceiling()` never calls
   `getContext()` (19:37–19:42).
5. The ceiling applies to elevating `doPrivileged` forms only, not the three-argument policy-reducing form; decided by
   form (`perms.length == 0`), not by module; **convert all java.base `doPrivileged` to the policy-reducing form** (19:43).
6. The three-argument intersection-domain design is intended (replaced Java 8 limited-context state to keep contexts
   immutable); the static-collection union is the defect (Opus relay, 19:11). My earlier "use the 2-arg constructor" was wrong.
7. `CombinerSecurityManager`'s `doPrivileged(action, delegateContext)` is a context *installation* to run the combiner,
   not an elevation; must not be converted (20:42). Generalised into §4.6 of the note.
8. **The java.base conversion is the load-bearing change and may be sufficient without the ceiling** (2026-10-22 21:52);
   recorded as §12 with a revised order; ceiling retained as a documented option only.
9. **Apply the per-class policy-reducing intersection to every `doPrivileged` form in `executePrivileged`**
   (2026-10-23 10:11). No elevating form remains; the ceiling and the ~406-site conversion sweep are both superseded by
   one change; third-party code is bounded without edits. Recorded as §13 with its own revised order. Safe against
   impostors because the limiting domain comes from `Reflection.getCallerClass()` and only intersects.

## 4. Open decisions and my recommendations

1. **Where the design note lives.** `DirtyChai/docs/` is not the repository root, so `AI_POLICY.md`'s Repository
   Documentation Exception does not cover it as written (`CONTRIBUTING.md` line 5). Recommend: either move it to the
   DirtyChai root with the authorship note it now carries, or mirror it to `JGDMS/docs/` as the primary copy (where
   AI-authored design documents are the norm — cf. `DirtyChai-Required-Changes-PermissionGrant-SOW.md` from the
   2026-10-20 handover) and leave a pointer. Project Lead decision; I added the authorship note, nothing else.
2. **SM-mediated checks under a ceiling** (note §11.1): bounded through the snapshot, or not. Moot if the ceiling is
   never built (§12); if it is, recommend accept.
3. **Exempt the policy-reducing form entirely vs a mechanical allow-list** — decided (5 above); the residual (per-class
   floors of I/O deputies) is recorded in §4.6 and §12 with its controls. Not reopened.
4. **Data provenance via the stack walk** (operator, 22:27–22:33): receivers in frames carry the data the check is
   about and `getContext()` discards them; stamping wire-origin objects with a provenance domain and having the
   `StackWalker`-based `getContext()` collect it from receivers would give the third authorization axis and follow
   data across threads. Discussed only; **not in any document**. Recommend a short DESIGN note before it is lost; its
   two stated limits (receivers not arguments; needs the Java walk, not the native one) go in its §0.

## 5. Next steps (from §13's revised order; nothing started; supersedes the §12 list)

1. DirtyChai (human-written per policy): an internal non-reducing raw primitive for `AccessController`'s own use and
   VM init (`getResource` calls `doPrivileged`; universal reduction would recurse); a `ClassValue<DomainIdentity>`
   per-class cache; per-class codesource derived from the class's `ProtectionDomain` codesource + class name.
2. The per-class codesource matching check — now load-bearing: a correct grant that silently does not match makes every
   `doPrivileged` deny. Forms are `jrt:/<module>/<Class>` and `<location>/<Class>`.
3. `executePrivileged` intersects the per-class domain on every form; `checkAuthorized` via
   `AccessController.checkPermission`; javadoc rewritten.
4. `ContextBuilder.combine(acc)`; `CombinerSecurityManager` and polpAudit's writer evaluate with it (their
   `doPrivileged(getContext, delegateContext)` is an installation site — §11.3, §13 item 4).
5. Shipped coarse grants (`grant codebase "jrt:/<module>/-"`, per-jar for JGDMS platform) so deployments keep running;
   polpAudit condenses to per-class; the AllPermission-outside-java.base lint.
6. One deliberate deputy test (constructor → URL handler) under coarse, then condensed, policy.
7. Commit the uncommitted diffs (§2) once the location decision (§4.1) is made; delete the stray copy.
8. Open: whether the limiting domain should be a separate field intersected at `implies` time rather than appended to
   the context array (§13, last paragraph). Decide when a second installation site appears.

## 6. Build recipe

Nothing was built. From the 2026-10-20 handover (RELAYED): Maven does not build JGDMS here (Pack200); use `javac`
with cross-module `-sourcepath`, `--enable-preview --release 21` for jgdms-der/jgdms-jeri. Device shell notes from
this session (VERIFIED): `grep -r` over `DirtyChai/src` and `git status` over the whole JGDMS tree both exceed the
120 s tool timeout — scope greps to `src/java.base/share/classes/java/security` and `git status` to a path.

## 7. Self-error count, cross-checked against the conversation (n = 9; likely an under-count)

1. Said the analysis document "was never committed" and wrote a duplicate into `JGDMS/docs/` — it was committed at
   `JGDMS/JGDMS/docs/` (`git log` `86232025f`). Wrong directory; caught when looking for the handover skill.
2. Proposed a static `Permission...` ceiling (r0) — operator: deployment-hostile.
3. Proposed a per-thread default ceiling `ROOT` (r1) — Opus: conflicts with the AllPermission-means-empty rule; bounds
   lazy JDK singletons for the JVM's life.
4. Proposed capturing full `getContext()` at every executor submit (r1/r2) — Opus: semantic change plus 85% of a check
   per submit.
5. Proposed a `ceiling` *field* on `AccessControlContext` (r2) — replaced by the appended sealed domain, then by the
   binding array alone.
6. Said the three-argument form's per-class policy consultation was a bug to fix with the 2-arg constructor — Opus: it
   is the intended design; the static-collection union is the defect.
7. Listed `CombinerSecurityManager`'s combiner-capture `doPrivileged` among the sites to convert — operator: it is a
   context installation, converting it would push the SM's limiting domain into every evaluation.
8. Wrote a "Superseded" paragraph into the analysis that described `ROOT` — stale within the same day; rewritten
   2026-10-22 (and the first copy went into the wrong file, see 1).
9. Claimed `getContext()` "already sees the ceiling's sealed domain" would cover platform-thread inheritance without
   qualification — true only when a `doPrivileged` frame is on the stack at thread creation; needed the public/internal
   `getContext()` split.

Not counted because not errors: the r2→r3 reframing to "caps elevation only" was a design improvement from the Opus
session, not a correction of a false claim.

## 8. Fastest-ageing section

§2 (state): two uncommitted diffs in two repos and a stray file, in working trees the operator edits between my turns
(measured: the operator committed r1 and r2 of the design note while I believed it untracked, and the JGDMS trunk moved
under the previous session's handover). Re-run `git status --short docs/` in DirtyChai and
`git status --short JGDMS/docs/ docs/_to_delete/` in JGDMS before acting on anything in §2 or §5.6.

## 9. Triggers (learnings, in the form they will be met)

- BEFORE SAYING A FILE DOES NOT EXIST OR IS UNTRACKED, `git ls-files | grep <name>` FROM THE REPO ROOT — a repo can
  have two `docs/` directories, and `ls` of one of them proves nothing about the other.
- BEFORE CALLING A `doPrivileged(action, ctx)` AN ELEVATION, READ WHAT THE ACTION DOES — if it is `getContext()` or
  runs a combiner, it is an installation, and converting it corrupts the evaluated context.
- BEFORE CALLING SOMETHING IN DIRTYCHAI A BUG, ASK WHETHER IT REPLACED A JAVA 8 MECHANISM ON PURPOSE — the maintainer
  rewrote limited privilege to keep contexts immutable; "differs from OpenJDK" is not "wrong".
- BEFORE WRITING INTO A REPO, READ ITS `CONTRIBUTING.md` LINE FOR AI — DirtyChai restricts AI-assisted Markdown to
  the repository root; a `docs/` subdirectory I created is outside the exception.
- BEFORE PROPOSING A DEFAULT-ON MECHANISM, ENUMERATE WHO ELSE INHERITS IT — `ROOT` died on `Cleaner`, the HTTP
  selector and the common pool, none of which were in the design until Opus listed them.
- WHEN THE OPERATOR SAYS "IT DOESN'T FEEL FAIL-CLOSED YET", LIST THE STATES IN WHICH THE MECHANISM IS ABSENT — that
  list, not the mechanism, is where every r1→r2→r3 change came from.

## 10. Addendum 2026-10-23 10:45 AEST: prior-art research stood down

- A `deep-research` run was launched at 10:34 with three researchers (model not pinned; inherits this session's
  `claude-fable-5-1`; each was asked to self-report its model ID in its notes — none did, because none wrote notes):
  (1) per-class policy-bounded elevation on the JVM (Elasticsearch entitlements, OSGi, limited doPrivileged, Koved/Pistoia);
  (2) data provenance in stack inspection (Abadi–Fournet, Phosphor, JIF, receiver-in-walk);
  (3) canonical-encoding digests as policy keys (content-addressed auth, CRIME/BREACH, schema fingerprints).
- Stood down at 10:42 at the operator's request (Fable budget 99%). VERIFIED: notes folder
  `/home/claude/research_notes/JVM per class privilege prior art/` empty at 10:43 (`ls -la`). Nothing on disk.
- RELAYED from researcher (2)'s last status line before the stop: it had found **"Trishul"** as a system that includes
  the RECEIVER's taint in a method's context and enforces policy at runtime — a direct precedent for the handover §4.4
  idea. Unverified; no citation captured.
- A scheduled reminder fires in this session at 15:03 AEST 2026-10-23 to resume or relaunch the three and then run the
  report writer. The researchers' handles are held by this session only; if the session is gone, relaunch from the
  three briefs (recorded in this conversation, not on disk — a gap if compaction drops them; the three bullet lines
  above are the minimum brief).
- **Resumed 15:03 AEST, all three completed by 15:12; report written and delivered 15:20.** VERIFIED on disk:
  `JGDMS/docs/PRIOR-ART-privilege-design-2026-10-23.md` (88 lines, copied from the container's
  `/home/claude/reports/JVM per class privilege prior art.md`); researcher notes remain only in the container at
  `/home/claude/research_notes/JVM per class privilege prior art/` (three files; not copied — the report cites them).
  Design note §14 added with the per-mechanism verdicts. All three researchers self-reported `claude-fable-5-1`
  (UNVERIFIED); the per-class researcher's own finish time was stale by ~4 h. Trishul now cited (IR-CS-45), with
  IBAC (S&P 2007) as the second citation to distinguish from; the .NET Deny/Assert lesson means receiver-provenance
  collection must continue past `doPrivileged` frames.
- Correction 15:58: the canonical-digest researcher reported an Oracle Deployment Rule Set fetch "rejected by the user";
  the operator saw no approval prompt (VERIFIED: operator statement), so the refusal was the fetch tool or proxy, not a
  decision. The gap stands; the attribution in the researcher's notes is wrong. Trigger for next time: A SUBAGENT'S
  "USER REJECTED" IS A TOOL RESULT, NOT A USER ACT — CHECK WITH THE OPERATOR BEFORE REPEATING IT.
- **21:30 AEST 2026-10-23:** `JGDMS/docs/DESIGN-serial-data-identity-2026-10-23.md` written (258 lines, `wc -l`) from
  a Sonnet recon index (22 read-only commands; anchors in its §12). Corrections it carries that earlier chat got wrong:
  the peer `Subject` is NOT bound when arguments are decoded (`BasicInvocationDispatcher` 1127 vs 1156) so provenance
  must be passed in explicitly, not read from the thread; `DigestCodeSource` must be constructed via a verified path or
  `DigestGrant.implies` never matches (`unverified` flag). Decisions still the maintainer's: whose principals the gate
  stamps (note recommends the delivering connection's identity, "who delivered this"); when to start. VERIFIED: the
  device bridge is a 2-CPU Linux VM with OpenJDK 11 and the DirtyChai build is `windows-x86_64-server-release`
  (`uname`, `java -version`, `ls build`), so anything that runs on DirtyChai runs on the Windows host, not from here.
  Standing instruction from the operator (16:11): search/recon agents on Sonnet, pinned explicitly; Opus for complex.
- **2026-10-24 08:55 AEST — §13 item 3 rewritten (DirtyChai note now 634 lines, `wc -l`).** Decisions (operator):
  the per-class domain carries a strong `Class<?> callerClass` field and takes its codesource and loader from the
  class's own PD (no synthesis; `getResource` deleted — its non-module branch could hit the network via
  `PreferredClassLoader`); per-class refinement is a `class` qualifier on `PermissionGrant`, the only grammar change;
  dynamic code calls plain `doPrivileged` bounded by its prepare-time grants through the loader; `Security.doPrivileged`
  deprecated for `-dl` code (four sites found: `Bootstrap` :122, `AID` :144, `SpaceProxy2` :310, `Item` :105 — VERIFIED by
  scoped grep over `*-dl/src/main`), kept for platform only as a recorded deputy where never-cut semantics are wanted;
  weak class reference rejected as a correctness defect (GC-dependent authority of stored contexts). Pending: fold the
  `class`-qualifier slice and the `-dl` conversions into the serial-data-identity note's §10 table; both are JGDMS work
  an agent can do in the container.
- **10:30 AEST 2026-10-24 — `Security.doPrivileged` and `doAs*` to be removed** (operator: confused deputy under §13).
  32 `main` files call `Security.doPrivileged` (VERIFIED: scoped grep per module; 0 in `qa`). Conversion rule recorded in
  the DirtyChai note §13 item 3: semantics-preserving `doPrivileged(action, AccessController.getContext())` first; the
  one-argument form only per site with polpAudit evidence. This is a JGDMS slice for a Sonnet agent (container clone,
  branch, javac build); not started.
- **12:30 AEST 2026-10-24 — conversion done on branch `claude/remove-security-doprivileged` (Sonnet agent, container
  clone of remote `e8074fc`; first push: commits `05e9c9d`, `dc0d433`; being amended).** TWO ERRORS OF MINE corrected
  by the agent, both struck in place in the DirtyChai note §13.3: (a) the conversion rule — the wrapper carried only
  the caller's domain (B5), so the ONE-argument form is the exact equivalent and the two-argument snapshot I
  prescribed was narrower; (b) "0 callers in qa" — qa tests the wrapper (`FakeCaller`, `DoPrivilegedTest`,
  `DoPrivilegedExceptionTest`, jtreg `Security/doPrivileged/`); they are deleted in a third commit. Agent's build:
  Maven fails offline here (nexus-staging plugin); javac fallback with throwaway OSGi/Pack200 stubs compiled the 38
  changed files + reachable sources, 0 errors; qa not compiled. Remote branch already lacked `doAs`/`doAsPrivileged`
  (removed 2026-10-23) — the operator's local tree I grepped was behind the remote. Triggers: BEFORE PRESCRIBING A
  REPLACEMENT, READ THE BODY OF WHAT IS REPLACED (I read `Security.doPrivileged` and still paraphrased it wrong);
  A GREP THAT PRINTS 0 IS NOT A NEGATIVE UNTIL THE PATH IT SEARCHED IS SHOWN TO EXIST (`qa` had the callers).
  Self-error count for this handover's session is now 11.
- **12:15–13:00 AEST 2026-10-24.** Branch `claude/remove-security-doprivileged` final: `90e369d` (61 sites → ONE-argument
  form), `d71cb6e` (wrappers removed), `7932fde` (qa tests of the wrappers removed: `DoPrivilegedTest`,
  `DoPrivilegedExceptionTest` + `.td`s, `FakeCaller`, jtreg `Security/doPrivileged/` ×7; `qa/build.xml` targets
  `qa1-securityDoPrivileged.jar`/`qa1-securityUtil.jar` and two `security.policy` grant blocks). Agent-flagged
  behaviour difference: the one-argument form drops the assigned `DomainCombiner` (site `PreferredProxyCodebaseProvider`
  :2092 and any site under a `Subject.doAs` worker subject). **Operator decision: do NOT add `WithCombiner`; migrate
  `Subject.doAs` → `Subject.callAs` instead**, so principals ride the ScopedValue through every form and policy can
  require an authenticated principal for an elevation (unauthenticated data gets the no-principal floor).
  VERIFIED on the fork: JGSS reads `Subject.current()` (`GSSUtil.java:328`, `Krb5Util.java:63/81/103`); fork
  `Subject.current()` returns the scoped subject; fork `Subject.doAs` REFUSES `WorkerSubject`/`UserSubject`
  ("must use callAs") and for plain Subjects binds the scoped value too — Kerberos does not need `doAs`.
  Open check (recon running, Sonnet): what type reaches `BasicInvocationDispatcher:2201 Subject.doAs(workerSubject…)`;
  every `doAs`/combiner site in `main`; platform-thread creation inside subject scopes (callAs is inherited by
  virtual threads only). Conversion to be an Opus slice. `ConfigurationFile.java:331,437` still names the removed
  method in its prohibited list (two small edits, not done).
- **16:00 AEST 2026-10-24 — `Subject.doAs` → `callAs` migration landed: commit `3cb7fcb` on
  `claude/remove-security-doprivileged` (Opus agent, 12 files, 1 new: `net.jini.security.SubjectScope` — needed because
  Browser/destroy/service-starter/phoenix-group compile with `--release 8` and cannot call `Subject.callAs`).** All 11
  sites converted; dispatcher 2201 now `callAs(workerSubject)` with comment blocks rewritten; Browser AWT handler and
  Kerberos ConnectionHandler re-bind captured subjects; `RemoteContextCodec` combiner read dropped (shaped a discarded
  check); `BasicUntrustedObjectSecurityContext` reads `Subject.current()` (stock-JDK+SM path loses the combiner);
  `Activation:1912` now includes the client's reducing domains (narrowing); `AbstractActivationGroup.doAction` no longer
  leaks raw `CompletionException`. VERIFIED by the agent (RELAYED here): javac fallback compiled the 12 files; release-8
  files compiled with `--release 8`; `SubjectScope` runtime smoke test passed; Maven/qa not run. Fork fact (agent read
  `Subject.doNoCheck` on the operator's machine): `doAs` binds the ScopedValue AND runs `doPrivileged(action, callerAcc)`.
  **OPEN — design call for the operator:** six bootstrap scopes start services whose library code spawns platform threads
  (`JoinManagerImpl`, `LookupCacheImpl`, `LeaseRenewalManagerImpl`, `AbstractLookupDiscovery`, …); under `doAs` those
  inherited the ACC, under `callAs` they carry no Subject. Rule given by the operator: capture `Subject.currentAll()`
  before thread creation and re-establish with `callAs` in the thread — so those library classes need a capture at
  construction (model: `SubjectAwareExecutor`). Next slice: Sonnet recon of every library class creating threads on a
  service's behalf, then conversion.

