<!-- CLAIM-HEADER (edit in place; body is append-only)
GOVERNING STATE: claim_register/hypotheses/DERPERF/INDEX.md in the clone of pfirmstone/Claude (branch master; `E:\Claude\` = that repo's root)  (this file's claims: CL-DERPERF-0001 to CL-DERPERF-0019; read the index, it carries the current row for each); rulings: claim_register/rulings/JGDMS/INDEX.md and claim_register/rulings/DIRTYCHAI/INDEX.md (nine rulings of this session are NOT yet in them, see section 7)
LATEST ADDENDUM: add.01b dated 2026-11-16 at heading "Addendum add.01b, 2026-11-16: the branch is merged into trunk" (add.01, at "10. Review by a different model", also stands (its corrections govern the sections above; predecessor HANDOVER-der-codec-performance-2026-10-18.md still governs its own sections 1 to 8))
SUPERSEDED BY: none
FASTEST-AGEING SECTION: 3. STATE (branches, pins, what is pushed, who is running) - re-read the disk before acting on it
-->
# Handover: JGDMS low latency and high scalability, the TLS accept path, and the api.security alignment with DirtyChai (2026-10-20 to 2026-11-14)

**AS-OF 2026-11-14 (JGDMS branch `claude/focused-bohr-t6bwal` at `87953afea`, before the commit that adds this file).** Author: the session titled
"SOW-DER Codec Performance docs" (configured model `claude-sonnet-5-5`; `get_session` reported `last_served_model` `claude-sonnet-5-5`, 2026-11-14).
Written following `handover-composition` (pfirmstone/cross-machine_claude_code_skills, `954ccb0`, read in full today).

## 0. Authorship hazard (read first)

This document is consolidation by its author, not a message from a colleague. I trust it more than a stranger's and it has the same error rate.
- **RELAYED vs VERIFIED, per claim** (the claims themselves live in the register; this table says only how I know):

| IDs | What I checked first-hand | What I only relayed (a subagent's report, or the maintainer's paste) |
|---|---|---|
| CL-DERPERF-0001, CL-DERPERF-0014 | the commit `499b47e95` exists and is pushed (`git log`); the test names exist (`grep -n`) | the failing-before and passing-after runs, the mutation check and the suite counts (Sonnet agent report) |
| CL-DERPERF-0003, CL-DERPERF-0015 | nothing run: these are open | the Opus cold review's limits and the board addendum |
| CL-DERPERF-0005 | `ServerMain` exits 0 after `ready` with stdin closed (agent run, then my own smoke run of the keepalive change) | that the Windows launch lacked a usable stdin: not observed |
| CL-DERPERF-0006, CL-DERPERF-0016 | the guard line and exit 3 under a shrunk port range (agent run) | all Windows numbers: the maintainer's pastes in this conversation |
| CL-DERPERF-0008, CL-DERPERF-0009 | the commit and the test names exist | the 57-input table, the mutation checks and both JDK results (agent report; I did not re-run `UriTest`) |
| CL-DERPERF-0019, CL-DERPERF-0011 | nothing re-run by me. (I checked that trunk's `ClassLoaderGrant.java` imports `CodeSource` with `grep -n import`; that concerns the compile fix, not these two rows.) | the probe tables and the emulated test counts (agent reports) |
| CL-DERPERF-0012 | nothing: the evidence is the maintainer's pasted mvn output of 2026-10-23 (the first locator wrongly said 2026-11-11; the reviewer found it in the transcript) | the conclusion that the image, not the code, is at fault: the non-conformances doc says it is not verified |
| CL-DERPERF-0018 | I ran `MuxChannelBatchingTest` and `SslServerHelloRetryTest` three times each after the port: 3 of 3 passed | the merge verification counts (agent report) |

- **Self-error count: at least 10, NOT cross-checked against the transcript** (so treat it as an under-count; the transcript is
  `/root/.claude/projects/-home-user/d4500893-c6b9-5565-960c-ab4381350b7d.jsonl`, container-only, and begins at the 2026-10-23 compaction, so errors 1 to 3 cannot be checked in it). Named: (1) said the socket path
  could read ahead (earlier segment, fixed); (2) said testing the JGDMS copy removes the serialization failure (earlier segment); (3) routed one
  message to the wrong agent (earlier segment, disclosed); (4) first diagnosis of the closing server window was the Java version, with no
  information that the maintainer runs DirtyChai; (5) a first `git apply` of the Mux fix skipped the file silently, and I went on to test before reading
  `git status`; (6) a `bash` tool call with the wrong case (no effect); (7) I quoted a sustainable-rate estimate for Windows port exhaustion (70 to
  135 connections per second) from memory with no source on the page; (8) I allocated nine ruling IDs in a shared project scope while another session
  was allocating from the same numbers (section 7), and found it only before syncing; (9) a register row said none of the 35 emulated classes changed between `95ef6f96b` and
  `4c71278f1`, which is false for `DigestDomainGrant` and `UnresolvedPrincipal` (the row was replaced); (10) the first version of this file declared the emulation stale and omitted about half of the
  window's commits (the review found both; sections 2, 3 and 11 are corrected).
- **A different model reviewed this document:** see section 10 (an Opus reviewer). The first version was pushed before the review finished, so every correction here is marked in place.

## 1. Where the truth lives

| Question | Authoritative file (paths from the JGDMS repo root; docs are under `JGDMS/docs/`) |
|---|---|
| What the DER codec, streaming encoder and admission work built and decided | `HANDOVER-der-codec-performance-2026-10-18.md` and `SOW-DER-Streaming-Encoder.md` |
| The TLS accept path (S0): the board's rulings, the bounds, the cold review of 2026-10-23 and what it fixed | `BOARD-REVIEW-S0-tls-accept-path-2026-10-20.md` (last addendum: "Addendum, 2026-10-23: the cold review of the accept path") and `DESIGN-tls-nonblocking-connectionio-2026-10-20.md` sections 2 and 4.6 |
| Benchmarks, the handshake sections 34 to 41, the Windows port-exhaustion section | `jgdms-benchmarks/serialization/RESULTS.md` (last section: "Running TlsAcceptRate on Windows without running out of ports (2026-10-24)") |
| The api.security copies in JGDMS against DirtyChai's bundled copies: D1 to D11, J1 to J9, the emulation recipe, the package layout | `NON-CONFORMANCES-api-security-dirtychai-2026-10-23.md` |
| Accepted API breaks (category G is the SubjectDomain removal) | `JGDMS-API-Compatibility-Accepted-Breaks.md` |
| Deserialization into a privileged context (plan, not started) | `PLAN-check-and-inject-unprivileged-construction-2026-10-19.md` |
| Claims and rulings | the register (header above) |

## 2. What happened, by stream (claims are cited by ID; the register holds what is believed about each)

1. **TLS accept path (S0).** Four board reviews, then a fifth Opus cold review (2026-10-23) of the record counter and the retry reading. See
   CL-DERPERF-0001, CL-DERPERF-0014, CL-DERPERF-0003 and CL-DERPERF-0015; the implementation itself is in section 2a.
2. **Windows measurements** (the maintainer runs them; this container has 4 cores): CL-DERPERF-0005, CL-DERPERF-0006, CL-DERPERF-0016. Tooling
   built: `ServerMain -Dbench.server.keepalive=true`; `TlsAcceptRate` options `bench.connect.hosts`, `bench.bind.hosts`, the guard and the `TIME_WAIT` lines.
3. **api.security alignment.** Platform tests no longer patch `java.base` or use `-Xbootclasspath/a`; seven JGDMS-only classes moved to
   `au.net.zeus.jgdms.api.policy`; D2 and D5 followed in the JGDMS copy; `SubjectDomain` removed; J8 and J9 fixed; DirtyChai pin moved to
   `4c71278f1` (trunk `95ef6f96b` did not compile for a missing `CodeSource` import; the maintainer fixed it in `29d03fd1c`). Claims: CL-DERPERF-0008, CL-DERPERF-0009, CL-DERPERF-0019, CL-DERPERF-0011, CL-DERPERF-0012. The rulings behind these are in section 7.
4. **Housekeeping.** Trunk merged into the branch (`49afb8a6b`); five commits of the maintainer's `sow-permissiongrant-domain-grants` line cherry-picked
   (`PreferredClassLoaderCodebaseGrantTest`, `SubProcessLocalPolicyAdminTest`, two `Variants` commits, `UserSubjectCompilePortabilityTest`); the Mux test race: CL-DERPERF-0018.

### 2a. The S0 implementation and its follow-ups (2026-10-21 and 2026-10-22, about 45 of the 83 first-parent commits; added after the review found the gap)

Pointer-only; the reasoning and measurements are in `BOARD-REVIEW-S0-tls-accept-path-2026-10-20.md`, `DESIGN-tls-nonblocking-connectionio-2026-10-20.md` and RESULTS sections 34 to 41. By theme (hashes resolve with `git log -1`):
- **Accept path:** HTTPS accept thread (`29cc1756c`), explicit listen backlog on every listener (`8be4a9adf`), handshake moved off the accept thread (`6393d4346`), total deadline and watchdog (`4751410fa`),
  cap, queue and pause-accept (`8d76e8134`), named-group pin and first-flight CPU bound (`d627f83de`), server mux start deadline (`6879a461d`, `917c5f349`), cryptography on a few platform threads (`fb7eb93ce`),
  budgets shared among all listeners of the JVM (`03893dcf9`), timeout-scheduler caller check and its permissions (`e002ed60c`, `6205732ab`), CPU bound for the `SSLSocket` path (`752d6bfe3`).
- **Follow-ups after two more reviews:** first TLS record read before a permit (`9f563a353`, `f83bd9dc0`), whole ClientHello while waiting (`31e07c84f`), bound on the client's answer (`6d9f4489f`), TLS 1.3 only on the socket path and the
  permit kept through a HelloRetryRequest (`1e60099cc`), second ClientHello whole on the engine path and the record count (`5a68c62bb`), burst-failure reporting in the summary (`e70b57aeb`, `16db2238e`),
  the default cap follows the processors (`4029db3f7`).
- **Transport:** `ConnectionManager.connect` in constant time, one open at a time, and the reaper race with `ReaperRaceProbe` (`87b4bf261`, RESULTS section 35); the IOVecWrapper and JDK bug 4854354 workarounds removed from `SocketChannelConnectionIO`
  (`1aa52d443`, `8dc35b03a`); `sun.nio.ch` made an optional OSGi import with a manifest test (`e8abed7a5`).
- **Measurement tools:** `tls-s0.py`, `TlsFlood`, `TlsAdversary`, `TlsStorm`, the call timeline (`2e2b52e80`, `88d5101e7`); RESULTS sections 36 to 41.
- **api.security guard rails:** tests of this module's copies whatever the JDK bundles (`724ca16de`) and a drift detector against the bundled copies (`93bcc9f98`); the maintainer's `PrincipalGrant` and `ProtectionDomainGrant` alignment commits (`b9cc0212d`, `ce85aeac6`, `f2c2ab35d`, `7e04de732`).
- **Build:** `jgdms.bash` for the compat-gate (`e612b8334`).
The full list of the 83 commits is Appendix A.

## 3. STATE (FASTEST-AGEING: re-read the disk before acting on it; its staleness is a measured property)

AS-OF 2026-11-14. Measured, not remembered (command in each line):
- `git rev-list --left-right --count origin/claude/focused-bohr-t6bwal...HEAD` in the JGDMS clone: `0 0`; `git status --short | wc -l`: `0`.
  The population is the JGDMS clone at `/home/user/JGDMS` in this container, which is discarded; the branch on GitHub is the copy that survives.
- `git rev-list --count HEAD..origin/trunk` = `56`: the maintainer's trunk has moved 56 commits since my merge of 2026-11-11. The merge is to be repeated.
- The DirtyChai clone `/home/user/pfirmstone/dirtychai` (read-only, shallow) is at `4c71278f1`; `git rev-list --count HEAD..origin/trunk` there = `5`
  (newest `7d58b3b3f`, 2026-11-13: SPIRE retry #237, SerialObjectPermission #213 and the #236 follow-ups). ~~The emulation pin in the non-conformances doc is
  therefore stale again~~ **CORRECTED (Opus review, H3; verified by `git -C dirtychai diff --name-only 4c71278f1 origin/trunk`):** those five commits change only `SpiffeCredentialManager.java` and `ObjectInputStream.java`; none of the 35 emulated
  classes changed, so the pin is not stale. CL-DERPERF-0012 depends on the maintainer's JDK, not on the pin.
- ~~Not pushed: the local branch `stubs-experiment`~~ **Pushed 2026-11-14 at the maintainer's word ("Yes, keep the experiments, push")** as `origin/claude/stubs-experiment` at `91b1531c3` (`git ls-remote --heads origin claude/stubs-experiment`); nothing is merged and stubs are not adopted (section 7 item 3). The generator, the module and the pin are in that commit.
- The register: this session's hypotheses are published (`b58e690470` on pfirmstone/Claude master). Its rulings are not (section 7).
- No subagent is running at composition time (the last report was received before this wrap-up began).

## 4. Open items (each cites its ID; none restates a status)

1. **Windows runs** (maintainer): the warm-up cap runs (default, 24, 12, post-flight bound off) and the three-address run. CL-DERPERF-0015, CL-DERPERF-0006, CL-DERPERF-0016; the jar sent was built from `d7dfb0140`.
   The cap default (`maxServerHandshakes`) stays as it is until then.
2. **The window that closed** (CL-DERPERF-0005): ask whether the working launch used the keepalive flag.
3. **Maintainer decisions pending** (all in section D of the non-conformances doc): does the D5 ruling extend to `ProtectionDomainGrant`; keep or drop the `unverified()` guard
   on `DigestDomainGrant`; should a PROTECTIONDOMAIN grant without a class be void; confirm D11 (`CodeSource(URL)` now throws for an invalid escape).
   DirtyChai-side, his to write: `au.zeus.jdk.net.Uri.toURL()` with user info (CL-DERPERF-0009), the stale `UnresolvedPrincipal` Javadoc (D10).
4. **Verification gaps carried honestly:** the serial-schema gate and the trunk-baseline half of the API-compat gate could not run offline (need `asm`); these modules do not build offline: `classdep`, `serial-schema-tracker`, `preferred-list-analyzer`, `bytecode-analysis-engine-service` (need `asm`), `jgdms-iiop` (the ORB and `javax.transaction`), the tests of `outrigger-service` (`mockito-all`), `jgdms-service-archetype`, and every module that follows `classdep` in the reactor (`security-policy-debug`, `service-starter`, `jgdms-destroy`, `jini-2.1-compat`, the norm, group and fiddler implementations, outrigger snaplogstore and cel, `phoenix`, `phoenix-init`, `dist`, `browser`, `extra`, `compat-gate`, `envcheck`); the main sources of eight of them compiled with plain `javac --release 21`; the QA harness
   edits for D2 were made by reading and never run; no non-JDK TLS client was tried (CL-DERPERF-0003); the Windows branch of `Uri.implies` is untested on Windows.
5. **Repeat the trunk merge** (56 commits behind) and re-run `jgdms-platform` and `jgdms-jeri`. ~~Refresh the emulation against DirtyChai trunk~~ (not needed: see section 3; the recipe is in the non-conformances doc, "Refresh at DirtyChai `4c71278f1` (2026-11-11)").
6. **Register:** allocate the nine rulings of section 7 once the numbering collision is cleared.
7. Older, still open (from the predecessor handover and my earlier summary): the plan to refuse deserialization into a privileged context has not been started; the "three
   handshake bounds" redesign was never decided; an OSGi resolution test on a real Felix with `org.osgi.framework.bootdelegation=org.apache.river.api.security` is untested; J7
   (removing `Permission` serialization from the JGDMS copies) is open; PermissionDelegate is for the maintainer to rewrite (provenance).

## 5. Do NOT redo (each conditional: what it rests on, and what unblocks it)

| Item | Rests on | Unblocks when |
|---|---|---|
| Patching `java.base` or `-Xbootclasspath/a` in the platform tests BY DEFAULT (the optional `dirtychai-patch` profile and the emulation recipe use `--patch-module`, off unless asked) | RULING (section 7 item 1; its quoted words cover `-Xbootclasspath/a` only, the rest is my reading) | the maintainer says otherwise |
| Writing or committing any DirtyChai source | RULING (section 7 item 9): no AI-assisted code in DirtyChai; the second half of the statement (agents only describe changes) is my reading of it | OpenJDK policy changes |
| Changing the `maxServerHandshakes` default (`min(512, maxFds/8, max(16, 4 x processors))`) | EVIDENCE: RESULTS section 39 rests on the maintainer's 6-core, 12-logical-processor machine and a 4-core container (no difference established there); no run on the 36-core workstation | CL-DERPERF-0015 has clean warm runs |
| Adopting the compile-time stubs | RULING (section 7 item 3) | the maintainer decides; the experiment is `claude/stubs-experiment` on origin |
| Re-running the Opus cold review of RecordCounter | EVIDENCE: the review measured legitimate JDK 27 client framings and synthetic ones, on an in-memory engine pair with CPU in 10 ms clock steps | a non-JDK client is tried (CL-DERPERF-0003) or the bound's constants change |
| Rebuilding the emulation | EVIDENCE: built at `4c71278f1`; the five newer DirtyChai commits touch none of the 35 emulated classes | a newer DirtyChai commit changes one of them (check with `git diff --name-only`) |

## 6. Triggers (written to fire at the act, not to describe a failure)

- **BEFORE DIAGNOSING A FAILURE ON THE MAINTAINER'S MACHINE, ASK WHICH JDK AND DIRTYCHAI BUILD IS INSTALLED THERE.** This container's image JDK is not his, and the first test failure he reported (the D1 test), CL-DERPERF-0012 and the Java-version guess about the closing window were that difference.
- **AFTER ANY `git apply` OR CHERRY-PICK, READ `git status` FOR THE FILES YOU EXPECTED BEFORE RUNNING TESTS.** The git toplevel here is `/home/user/JGDMS` and the project is the `JGDMS/` subdirectory,
  so `git apply -p2` with `--include` skipped a file with no error; `git apply -p1` was right.
- **A FIX SUGGESTED FROM A DIFF IS A GUESS UNTIL THE FAILING CASE RUNS AGAINST IT.** The one-line J9 fix did not work (CL-DERPERF-0009); the agent found it because it ran the case.
- **BEFORE QUOTING A GREEN CONFORMANCE TEST, STATE WHAT IT COMPARES.** `BundledCopyConformanceTest` compares signatures (CL-DERPERF-0011).
- **BEFORE ALLOCATING A REGISTER ID IN A SHARED SCOPE, READ ORIGIN'S LOG AND THE OTHER SESSIONS' HANDOVERS FOR THE SAME RANGE.** Section 7 is what happened.
- **A SUBAGENT'S CLAIM THAT A COMMIT IS PUSHED IS CHECKED WITH `git rev-list --left-right --count origin/<branch>...HEAD`.**

## 7. Rulings of this session not yet in the register (IDs deliberately unallocated)

**Why.** I appended nine rulings to my clone's logs for the JGDMS and DIRTYCHAI rulings scopes (the JGDMS scope numbers 29 to 34 and the DIRTYCHAI scope numbers 5 to 7).
Before syncing, I found that the JGDMS trunk handover `HANDOVER-2026-11-01-code-free-proxy-window.md` (add.4t, commit `517aef5be`) says another session recorded JGDMS rulings numbered 29 to 35
in the maintainer's local register (`E:\Claude`), which origin/master does not yet hold. Syncing would have put two different rulings under one ID. I discarded my nine unpublished lines
(`git checkout --` on those two logs in my clone; nothing had reached origin) and published only the hypotheses. The lines are kept in
`/tmp/claude-0/-home-user/d4500893-c6b9-5565-960c-ab4381350b7d/scratchpad/my-rulings-unpublished.tsv` (container only, so the table below is the durable copy).
**To do:** after the other session's entries are on origin/master (`claim_register.py sync` shows them), append these with `claim_register.py new JGDMS|DIRTYCHAI IN-FORCE --kind ruling ... --note "<verbatim>"`
and cite the new IDs here. Statements and the maintainer's verbatim words (conversation 2026-10-23 and 2026-11-11):

| # | project | statement as I would register it | verbatim words | where the quote comes from | locator |
|---|---|---|---|---|---|
| 1 | JGDMS | JGDMS tests do not use -Xbootclasspath/a; the jgdms-platform tests exercise the shared org.apache.river.api.security classes through their public API against whichever copy the JDK loads. (Reading, not his words: nor is java.base patched by default.) | -Xbootclasspath/a I don't wish to use this option. Can we test the classes using public API instead? | compaction summary (not re-read from the raw message) | conversation session_01C2445ZHSiqqCFhjuD9dFAS 2026-10-23; JGDMS 029a84221 and 6ed9413fa; JGDMS/docs/NON-CONFORMANCES-api-security-dirtychai-2026-10-23.md heading 'Running the jgdms-platform tests (J5 resolved)' |
| 2 | JGDMS | The seven JGDMS-only (non-Apache-River) classes ExternallyVoidablePermissionGrant, LocalPrincipalProvider, LeasedDelegation, LeasedPermissionGrant, OneShot, OneShotLeasedPermissionGrant and UserSubjectSupport live in au.net.zeus.jgdms.api.policy, not in the shared org.apache.river.api.security package, which is kept as small as possible | I'm considering however that they should be moved into a different package. They aren't of Apache River origin and we want to minimise anything in that shared package. A more appropriate package for them would be au.net.zeus.jgdms.api.policy ; then, confirming (relayed via the compaction summary): "I agree with your recommendation, yes have the sonnet agent find every reference first" and "Yes, please proceed." | compaction summary (not re-read from the raw message) | conversation session_01C2445ZHSiqqCFhjuD9dFAS 2026-10-23; JGDMS/docs/NON-CONFORMANCES-api-security-dirtychai-2026-10-23.md heading 'F. Status (2026-10-23): seven of the eight classes moved' |
| 3 | JGDMS | Compile-time stubs of DirtyChai's org.apache.river.api.security classes, in place of the bundled copies, are not adopted yet; the copies stay for now and the stubs are noted as planned work | 1. No not yet. | raw message, matched by the reviewer | conversation session_01C2445ZHSiqqCFhjuD9dFAS 2026-10-23 (answer to the question whether to adopt the stubs on this branch); JGDMS/docs/NON-CONFORMANCES-api-security-dirtychai-2026-10-23.md heading 'G. Planned for the future: compile-time stubs instead of copies' |
| 4 | JGDMS | The org.apache.river.api.security classes that remain in JGDMS are for compile-time support on standard OpenJDK and are not for production; production on DirtyChai uses DirtyChai's own copies, so no DirtyChai-only behaviour (such as DomainIdentity.getCallerDomain in DigestDomainGrant) is added to the JGDMS copies | The classes that remain in JGDMS are for compile time support on standard OpenJDK, they're not for production. | raw message, matched by the reviewer | conversation session_01C2445ZHSiqqCFhjuD9dFAS 2026-11-11 (item D9); JGDMS 87953afea; related RL-JGDMS-0027 |
| 5 | JGDMS | The SubjectDomain feature (SubjectDomain, Security.doAs and doAsPrivileged, DistributedSubjectCombiner) is removed from jgdms-platform; better solutions to the same problem are being implemented | SubjectDomain We are implementing better solutions to the problem it attempts to solve.  Yes remove it please. | raw message, matched by the reviewer | conversation session_01C2445ZHSiqqCFhjuD9dFAS 2026-10-23; JGDMS f358d53bd; JGDMS/docs/JGDMS-API-Compatibility-Accepted-Breaks.md category G |
| 6 | JGDMS | Serialization support is to be removed from the bundled security classes as bad security practice, so throwing on serialization is the intended behaviour | The intent is to remove serialization support from these classes as it is not good security practise, so it was easier to make it throw than implement it. | compaction summary (not re-read from the raw message) | conversation session_01C2445ZHSiqqCFhjuD9dFAS 2026-10-23; JGDMS/docs/NON-CONFORMANCES-api-security-dirtychai-2026-10-23.md item J7 |
| 7 | DIRTYCHAI | A CLASSLOADER grant with no class is void on purpose: the grant is garbage collected after the ClassLoader it grants permission for has been removed, so it must never become a grant to all domains | Yes, this is intended to garbage collect the grant after the ClassLoader it grants permission for has been removed. | raw message, matched by the reviewer | conversation session_01C2445ZHSiqqCFhjuD9dFAS 2026-10-23 (item D2); DirtyChai 841105767; JGDMS b5eed4c8d (the JGDMS copy follows); JGDMS/docs/NON-CONFORMANCES-api-security-dirtychai-2026-10-23.md heading 'D2 observed and done (2026-10-23)' |
| 8 | DIRTYCHAI | DigestDomainGrant is a dynamic grant that lasts only as long as the ClassLoader it is granted to; its identity is whether the DigestCodeSource is one that ClassLoader loaded, not signer order, a signer set or isVerifiedDigest | D5 DigestDomainGrant is a dynamic grant that only lasts as long as the ClassLoader it is granted to, in this case if DigestCodeSource is either one loaded by the ClassLoader, or it isn't. | raw message, matched by the reviewer | conversation session_01C2445ZHSiqqCFhjuD9dFAS 2026-10-23 (item D5); JGDMS b5eed4c8d; JGDMS/docs/NON-CONFORMANCES-api-security-dirtychai-2026-10-23.md heading 'D5 inventory and what was done (2026-10-23)' |
| 9 | DIRTYCHAI | PermissionDelegate counts as AI-assisted and the maintainer will probably rewrite it at some stage. (Reading, not his words: no AI-assisted code goes into DirtyChai, OpenJDK policy as stated in the heading of section A of the non-conformances doc, so agents describe changes for DirtyChai and never write or commit its source.) | PermissionDelegate counts as AI assisted, so I will probably need to rewrite that at some stage. | compaction summary (not re-read from the raw message) | conversation session_01C2445ZHSiqqCFhjuD9dFAS 2026-10-23; JGDMS/docs/NON-CONFORMANCES-api-security-dirtychai-2026-10-23.md heading 'A. Provenance (OpenJDK policy: no AI-assisted code in DirtyChai)' |

**Corrections after the review (M3, M4, L1, L2):** rows 1, 2, 6 and 9 quote words that the reviewer found only inside the compaction summary, so they are relayed, not first-hand; rows 1 and 9 had statements that went beyond their quotes and are narrowed above, with the extra part marked as my reading; row 2 now carries his confirming words.
Only the JGDMS scope collides: origin's DIRTYCHAI scope ends at number 4 and nothing cites 5 or above, so rows 7 to 9 could be appended now **if no other session has allocated DIRTYCHAI numbers in the maintainer's local register (not checked: it is on `E:\Claude`)**.

## 8. Operational knowledge I would not rediscover

- **Builds.** `JAVA_HOME=/opt/dirtychai/jdk-27`; run `mvn -o` from the `JGDMS/` directory because `JGDMS/.mvn/maven.config` has a relative `-s .mvn/settings.xml`. Use a private repository copy
  (`cp -r ~/.m2/repository <scratch>/m2repo`, `-Dmaven.repo.local=...`) so `~/.m2` stays clean. `jgdms-benchmarks` is not in the reactor: install `jgdms-jeri` and `jgdms-rmi-tls` first, then
  `mvn -o -f jgdms-benchmarks/pom.xml -pl serialization -am -DskipTests package`; ~~revert the regenerated `dependency-reduced-pom.xml`~~ (no longer generated or tracked since the commit of 2026-11-16 that sets `createDependencyReducedPom` to false in `jgdms-benchmarks/pom.xml`).
- **Emulating a newer DirtyChai:** `-Djgdms.dirtychai.patch=<dir> -Djgdms.conformance.jdkClasses=<dir>` on `mvn -pl jgdms-platform test`; recipe in the non-conformances doc.
  A stock-JDK run: `-Djvm=/usr/lib/jvm/java-21-openjdk-amd64/bin/java`.
- **Agents share one working tree.** `git pull --rebase` refuses while another agent has unstaged edits; use `git fetch`, check you are not behind, push a fast-forward, stage files by explicit path.
  Build in a `git worktree` when another agent is editing the main tree.
- **The register.** Clone pfirmstone/Claude (private, master) and run `python3 -I claim_register/claim_register.py sync` at the start and `sync --by <you>` after entries (`claim_register/CLOUD.md`).
  The first `sync` pushed a one-line commit (`c9c8286eb3`) that only added a missing trailing newline to `rulings/JGDMS/LOG.tsv`; that is the tool's design, not a defect of mine.
- **Agents' reports are advice.** Their commit hashes were checked with `git log -1` before this document cited them.

## 9. Subagent roster (resumable by `SendMessage` while the session lives; not enumerable by any tool, so an unrecorded ID is unreachable)

| agentId | model | role | holds |
|---|---|---|---|
| a7565489b9fb8962e | Sonnet | `jgdms.bash` property for the compat-gate | the SOW bullet and the stripped-pom check |
| ac7a45794df99a73a | Sonnet | drop java.base patching; test via public API | the `SecurityCopy` helpers, the Assume-skipped test list, the old image's defects |
| ade59b28b764c927d | Opus | cold review of RecordCounter and the retry reading (read-only) | the probes under `scratchpad/probe/`, the legitimate-client table |
| abbb0ddc063c53721 | Sonnet | the six TLS review fixes | before/after logs under `scratchpad/before.log`, `after.log` |
| ae15af0e94623a221 | Sonnet | SubjectDomain removal | the compat-gate category G, the unbuilt-module list |
| ad7b92eb4befaad35 | Sonnet | D2 and D5 alignment | the observed trunk behaviour for a void CLASSLOADER grant; the `CodeSourceKey` inventory |
| a49e66b4df5a418e6 | Sonnet | emulation at `95ef6f96b`, D1 probe | `scratchpad/dc95/` |
| a589639a8045a6400 | Sonnet | emulation at `4c71278f1` | `scratchpad/dc4c/`, J8 and J9 discovery |
| abaa37ce50d4de97d | Sonnet | J8 and J9 fixes | the 57-input semantics probe `scratchpad/j8/probe/SemProbe.java` |
| a7e93214a3112ce59 | Sonnet | overlap of the maintainer's branch with ours (read-only) | the commit-by-commit table |
| a127b99b01ebfdae2 | Sonnet | merge verification (read-only) | `scratchpad/mergecheck/` logs |
| acce81e7e9da77f67 | Sonnet | ServerMain reproduction | `scratchpad/servermain/`, the split-package `IllegalAccessError` on an old DirtyChai |
| a48ce1179d9e390fe | Sonnet | TlsAcceptRate address options and guard | `scratchpad/tar/` |
| ac2d1648c1c1961ac | Sonnet | benchmark jar and Linux smoke run | `scratchpad/bjar/` (jar, sha256 `de2a3776d1983a800f2adc1565473524db873954f23d8a491a0ace5ac0e52596`) |
| a958d68943a91a762 | Opus | cold review of this handover and the register rows (read-only, 2026-11-14) | `scratchpad/review/opus-handover-review.md` (container only) |
| a7be4165110042fb3 | Fable | UDS bind fix review (2026-10-20; self-reported, not re-verified) | see the predecessor handover |

My reading of them (mine, not theirs): every Sonnet report that included a count was later consistent with a re-run or a second agent's run; the two places where an agent's first
proposal was wrong (J9, and the arithmetic of the prefix) were caught by the same agent running the case. Their scratchpad directories live in the container only. **Missing from this roster:** the S0 board seats of 2026-10-20 and 2026-10-21, and the Opus divergence-analysis agent of 2026-10-23; their IDs were not in my context when I wrote it (check section 8 of the predecessor handover and the transcript of the earlier segments).

## 10. Review by a different model (add.01, 2026-11-14)

An Opus agent (read-only) reviewed the first version of this file (`2ec3fd489`) and the 13 register rows it cites: 3 HIGH, 8 MEDIUM, 10 LOW findings; the full report is `scratchpad/review/opus-handover-review.md` (container only). What it found and what I did:

| Finding | Disposition |
|---|---|
| H1 the row for the maintainer's own-JDK run had the wrong date and a status stronger than its evidence | the register status was lowered and the locator corrected (appended; CL-DERPERF-0012); section 0 row corrected |
| H2 the currency check passed a file that omitted about half the window (the S0 implementation) | section 2a and Appendix A added; section 11 re-run with the missing keywords |
| H3 the emulation was called stale though nothing it uses changed | struck in place in sections 3, 4 and 5; verified with `git diff --name-only` in the DirtyChai clone |
| M1 to M2, M5, M6, L5 five register rows misstated their evidence | each replaced by a new row (CL-DERPERF-0019, CL-DERPERF-0015, CL-DERPERF-0014, CL-DERPERF-0016, CL-DERPERF-0018); the old rows were closed in the register |
| M3, M4, L1, L2 the rulings table | corrected in section 7 |
| M7 roster missing an agent | added; the agents I cannot name are listed as missing |
| M8, L3 to L10 | corrected in place (section 0 row, section 11 counts, the "34 to 41" range, the java.base wording, the trigger, the named modules, the words beside IDs) |

One more error was mine and found afterwards: one of the replacement rows said none of the 35 emulated classes changed between `95ef6f96b` and `4c71278f1`; that is false, and it was replaced again (CL-DERPERF-0019).
The review did not run tests or mutations, did not recount the 57-input table, and could not see the maintainer's pasted outputs except where they appear in the transcript. **This file after the corrections has not been reviewed again.**

## 12. Tasks for a LOCAL session (added after the review; the local disk holds what this container lacks)

A local session can read the whole raw transcript of this session (this container's copy begins at the 2026-10-23 compaction) and the real register at `E:\Claude`. In this order:
1. Check `E:\Claude\claim_register\rulings\JGDMS` and `...\DIRTYCHAI` for numbers above those on origin/master of pfirmstone/Claude (the code-free-proxy-window handover cites JGDMS numbers 29 to 35); then append the nine rulings of section 7 with `claim_register.py new`, quoting only words found in the raw messages (rows 1, 2, 6 and 9 are currently relayed), and run `sync`.
2. Audit the self-error count of section 0 against the transcript, and find the IDs of the earlier S0 board-seat agents and the October divergence-analysis agent for the roster; record both as an addendum, not an edit.
3. Review this file again with a model other than the author's (the corrections of add.01 have not been reviewed), checking the register rows and the hashes with commands.
Not for a local session: the Windows runs (maintainer), and anything that needs this container's scratchpad (it is discarded).

## 11. CURRENCY CHECK (mechanical; re-run at add.01, 2026-11-14)

AS-OF stamp of this section: 2026-11-14 (the file's head is `87953afea` plus the handover commits). What changed since the predecessor's AS-OF (2026-10-20), enumerated from `git log --first-parent --reverse 9465d4661..87953afea` (83 commits, Appendix A) and from this conversation: the S0 implementation and follow-ups,
the transport changes, the measurement tools, the api.security guard rails and alignment, the platform-test change, the stubs question, the SubjectDomain removal, D1 to D11, J8 and J9, the Windows tooling and port-exhaustion work, the trunk merge and the cherry-picks, the Mux race, the register.
`grep -ci` of one keyword from each over sections 0 to 10 of this file (this section and Appendix A are excluded so that they cannot supply the hits; a zero would be a gap): handshake:5 watchdog:1 backlog:1 HTTPS:1 IOVecWrapper:1 4854354:1 reaper:2 sun.nio.ch:1 drift:1 jgdms.bash:2 29d03fd1c:1 4c71278f1:8 RecordCounter:2 keepalive:3 TlsAcceptRate:3 SubjectDomain:6 D2:5 D5:9 J8:4 J9:6 stubs:9 cherry:2 Windows:9 TIME_WAIT:1 maxServerHandshakes:2 ProtectionDomainGrant:2 Felix:1 J7:2 SerialObjectPermission:1 Mux:4 ConnectionManager:1 TLS 1.3:1.
Every item is either in this file or excluded below. Deliberately excluded: the individual test logs (container scratchpad), the 5,000-row census of other agents' handovers (not mine), the full agent transcripts, and the contents of the sub-documents (pointers only, section 1).

## Appendix A. First-parent commits since the merge base `9465d4661` (pointer list; `git log --first-parent --reverse --format='%h %ad %s' --date=short 9465d4661..87953afea`)

- `87b4bf261` 2026-10-21 RESULTS section 35: ConnectionManager.connect in constant time, one open at a time, and the reaper race; ReaperRaceProbe
- `f79c77d07` 2026-10-21 Merge remote-tracking branch 'origin/trunk' into claude/focused-bohr-t6bwal
- `38753b2b5` 2026-10-21 S0 board review: record the maintainer's decisions
- `29cc1756c` 2026-10-21 Start the accept thread for the HTTPS server endpoint
- `8be4a9adf` 2026-10-21 Request an explicit accept backlog on every server listener
- `6393d4346` 2026-10-21 Move the TLS server handshake off the accept thread
- `4751410fa` 2026-10-21 Give the TLS server handshake a total deadline and a watchdog
- `8d76e8134` 2026-10-21 Cap the TLS handshakes in progress, queue the rest, pause accept when full
- `d627f83de` 2026-10-21 Pin the server's named groups and bound the CPU of the first flight
- `6879a461d` 2026-10-21 Give the server mux a start deadline
- `049387b54` 2026-10-21 Measure the TLS accept path before and after S0 (RESULTS section 36)
- `36e5ac6c0` 2026-10-21 Describe the built S0 in the TLS design document
- `8076bc7f1` 2026-10-21 S0 board review: record the DirtyChai-only runtime assumption (no carrier pinning)
- `8e3d09928` 2026-10-21 S0 board review: record decisions on queue-full, the deadline and the follow-up package
- `2e2b52e80` 2026-10-21 Pin the cores of each JVM in tls-s0.py, warm the storm, add TlsFlood and a call timeline
- `996d27c12` 2026-10-21 Remove a server connection from its listener even when closing it fails
- `917c5f349` 2026-10-21 Schedule the server mux's start timer before the I/O starts
- `fb7eb93ce` 2026-10-21 Run the server handshake's cryptography on a few platform threads
- `88d5101e7` 2026-10-21 Bound TlsFlood by wall time, add --idle-only to tls-s0.py
- `6c827ad83` 2026-10-21 RESULTS section 37: why calls slow down during a storm of new TLS connections after S0
- `03893dcf9` 2026-10-21 Share the TLS handshake budgets among all the server listeners of the JVM
- `e002ed60c` 2026-10-21 Keep the connection timeout scheduler to its callers and off blocking work
- `752d6bfe3` 2026-10-21 Bound the CPU of server handshakes done with an SSLSocket
- `dcb5eb823` 2026-10-21 Small items from the S0 implementation review
- `9f563a353` 2026-10-21 Read the first TLS record of a waiting server connection, and give a turn only with time left
- `e8abed7a5` 2026-10-21 Make sun.nio.ch an optional import, and test the generated manifest
- `1aa52d443` 2026-10-21 Remove the IOVecWrapper workaround from SocketChannelConnectionIO
- `8dc35b03a` 2026-10-21 Remove the workaround for JDK bug 4854354 from SocketChannelConnectionIO
- `c0a000f9b` 2026-10-21 Measure and describe the S0 follow-up: RESULTS section 38, design 2 and 4.6, board addendum
- `6a7966ae7` 2026-10-21 Bound the duration of the start timer test by time, not by iterations
- `86d4b0695` 2026-10-21 Test the CPU bound of the handshake permits against the same flood, not a constant
- `f83bd9dc0` 2026-10-22 Read the first TLS record before the SSLSocket path takes its permit
- `6205732ab` 2026-10-22 Document and test the permissions that the timeout scheduler needs of its own domain
- `7bf6cf91b` 2026-10-22 Smaller findings of the review of the S0 follow-up
- `4029db3f7` 2026-10-22 Make the default for the handshakes at once follow the processors
- `e70b57aeb` 2026-10-22 Report the last failures of a burst, and those left at close, in the handshake summary
- `31e07c84f` 2026-10-22 Read the whole ClientHello while a connection waits for its turn
- `6d9f4489f` 2026-10-22 Bound how long a handshake waits for the client's answer to the server's flight
- `1e60099cc` 2026-10-22 Take TLS 1.3 only on the SSLSocket path, and keep its permit through a HelloRetryRequest
- `0060fc266` 2026-10-22 Smaller findings of the second review of the S0 follow-up
- `f61e3a889` 2026-10-22 Measure the second follow-up of the TLS accept path and record it
- `c0c7dd7a7` 2026-10-22 ValueSerializersMalformedInputTest: compare with the absolute File on Windows
- `5a68c62bb` 2026-10-22 Read the second ClientHello whole on the engine path, and count the records of the client's flight
- `069539d0a` 2026-10-22 Test the bound on the wait for the second ClientHello, and correct the documents of the TLS accept path
- `16db2238e` 2026-10-22 Write the last losses of a burst of lost timer tasks, and widen the margin of the slow-client test
- `724ca16de` 2026-10-22 Test this module's org.apache.river.api.security classes whatever the JDK bundles
- `93bcc9f98` 2026-10-22 Detect drift between this module's api.security classes and those a JDK bundles
- `b9cc0212d` 2026-10-23 Make ClassLoaderGrant and DigestDomainGrant extend PrincipalGrant, as the copies bundled in DirtyChai do
- `ce85aeac6` 2026-10-23 Make the PermissionGrantBuilder constructor package-private and codeSources abstract, as in the copy DirtyChai bundles
- `f2c2ab35d` 2026-10-23 Make UnresolvedPrincipal.WILDCARD package-private, as in the copy DirtyChai bundles
- `7e04de732` 2026-10-23 Carry the warning suppressions of the copies DirtyChai bundles, and write the wildcard of UnresolvedPrincipal as they do
- `771ead124` 2026-10-23 Record the non-conformances between the api.security copies of JGDMS and DirtyChai
- `67bd83fba` 2026-10-23 Non-conformances: serialization removal is intended (D3, D7), add the JGDMS-side item
- `70a3d52ae` 2026-10-23 Non-conformances: DigestGrant and DigestDomainGrant provenance, package layout under consideration
- `2089708b9` 2026-10-23 Non-conformances: DigestDomainGrant independence confirmed (P6)
- `e2131e5a8` 2026-10-23 Move seven JGDMS-only classes to au.net.zeus.jgdms.api.policy
- `80260cd96` 2026-10-23 Docs: record the move of seven classes to au.net.zeus.jgdms.api.policy
- `0ccf8446e` 2026-10-23 Test: the platform bundle exports au.net.zeus.jgdms.api.policy
- `a2c5aa224` 2026-10-23 Non-conformances: record the plan to replace the shared-class copies with compile-time stubs
- `029a84221` 2026-10-23 jgdms-platform: stop patching the shared security classes into java.base for tests
- `6ed9413fa` 2026-10-23 jgdms-platform tests: run against whichever copy of the shared security classes the JDK loads
- `e123574d5` 2026-10-23 Non-conformances: point J5 at the note on running the platform tests
- `e612b8334` 2026-10-23 compat-gate: make the bash executable configurable via jgdms.bash
- `f358d53bd` 2026-10-23 Remove the SubjectDomain feature from jgdms-platform
- `499b47e95` 2026-10-23 Count the records of the client's flight where the engine consumes them
- `09bc264e5` 2026-10-23 ClientHelloReader: alerts, reads of no bytes, and the JDK's longest message
- `a9957a937` 2026-10-23 Record the cold review of the TLS accept path and what it fixed
- `b5eed4c8d` 2026-10-23 D2 and D5: a CLASSLOADER grant without a class is void; DigestDomainGrant is bound to its ClassLoader's DigestCodeSources
- `5c68612f6` 2026-10-23 jgdms-jeri tests: hold mux replies until the connection is cut; copy FREE under its lock before iterating
- `86ddec4c8` 2026-10-23 D1 fixed in DirtyChai dafbac90a: pin the emulation at 95ef6f96b, test that a loader grant never implies a CodeSource
- `54b6d5e1f` 2026-10-21 PreferredClassLoaderCodebaseGrantTest: use the system loader's parent, the module compiles with --release 8
- `b4b320bdd` 2026-10-21 SubProcessLocalPolicyAdminTest: use a computed (verified) DigestCodeSource; an asserted digest is unverified and not implied
- `97aac360f` 2026-10-21 Variants: build the javac classpath from the code source URI so it works on Windows
- `02e21a23c` 2026-10-21 Variants: report the classpath and the AtomicSerial source when the compile fails
- `4482b30f1` 2026-10-21 UserSubjectCompilePortabilityTest: scan with walkFileTree so transient IDE files cannot fail the walk
- `7336a88a3` 2026-10-23 Non-conformances: DirtyChai trunk 29d03fd1c carries the CodeSource import; pin moves to it
- `e8074fc20` 2026-10-23 DigestDomainGrantTest: skip the D1 test on a JDK whose bundled copy predates dafbac90a
- `e6000a140` 2026-10-24 ServerMain: -Dbench.server.keepalive=true runs without a parent; say so when stdin is closed from the start
- `3d2940901` 2026-10-24 TlsAcceptRate: spread connections over address pairs, port exhaustion guard, TIME_WAIT report
- `49afb8a6b` 2026-11-11 Merge origin/trunk into claude/focused-bohr-t6bwal
- `d7dfb0140` 2026-11-11 Pin DirtyChai 4c71278f1: rebuild emulation, record results, J8/J9 Uri defects
- `f89127f4c` 2026-11-11 Uri: keep the separator in the "/-", ";-" and "/*" prefix compare (J8); toURL with user info (J9)
- `87953afea` 2026-11-11 Non-conformances: J8 and J9 fixed in f89127f4c; D9 resolved by the maintainer's ruling

## Addendum add.01b, 2026-11-16: the branch is merged into trunk (this governs the state sections above)

- The maintainer merged `claude/focused-bohr-t6bwal` (head `5f8b537fe`) into `trunk` and pushed: `4157cf573` "Merge branch 'claude/focused-bohr-t6bwal' into trunk" (`git log --oneline -3 origin/trunk`). One of his own commits (`7672af1ef`, a proxy-board handover) lies between them.
  Measured at 2026-11-16: `git merge-base --is-ancestor origin/claude/focused-bohr-t6bwal origin/trunk` is true; `git rev-list --left-right --count origin/trunk...HEAD` after the fast-forward below is `0 0`.
- My branch was fast-forwarded to trunk (`git merge --ff-only origin/trunk`), so nothing of it is outside trunk. Further work starts from trunk on this branch name; no history is rewritten.
- Section 3 (STATE), section 4 item 5 (repeat the trunk merge) and section 12's pointer to the branch are therefore out of date; sections 4 items 1 to 4, 6 and 7 and section 5 stand.
- Before the merge the branch gained three commits that sections 0 to 12 do not mention: `6e6362289` (the LICENSE and NOTICE root guard in `JGDMS/pom.xml` made resolvable offline), a second trunk merge (`432aa77ed`, trunk's `decodeToFieldMap` commit; `jgdms-der` 1536 tests, 0 failures), and `5f8b537fe` (benchmark `dependency-reduced-pom.xml` no longer generated or tracked).
- Not reported to me: whether the two gates that need `asm` (`check-serial-schema.sh` in full, the trunk-baseline half of `check-api-compat.sh`) were run before the push. The cloud container cannot run them.
