<!-- CLAIM-HEADER (edit in place; body is append-only)
GOVERNING STATE: claim_register/hypotheses/DERPERF/INDEX.md in the clone of pfirmstone/Claude (branch master; `E:\Claude\` = that repo's root)  (this file's claims: CL-DERPERF-0001 to CL-DERPERF-0013); rulings: claim_register/rulings/JGDMS/INDEX.md and claim_register/rulings/DIRTYCHAI/INDEX.md (nine rulings of this session are NOT yet in them, see section 7)
LATEST ADDENDUM: none yet (this file is the first of its line; the October handover HANDOVER-der-codec-performance-2026-10-18.md is its predecessor and still governs sections 1 to 8 of that file)
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
| CL-DERPERF-0001, CL-DERPERF-0002 | the commit `499b47e95` exists and is pushed (`git log`); the test names exist (`grep -n`) | the failing-before and passing-after runs, the mutation check and the suite counts (Sonnet agent report) |
| CL-DERPERF-0003, CL-DERPERF-0004 | nothing run: these are open | the Opus cold review's limits and the board addendum |
| CL-DERPERF-0005 | `ServerMain` exits 0 after `ready` with stdin closed (agent run, then my own smoke run of the keepalive change) | that the Windows launch lacked a usable stdin: not observed |
| CL-DERPERF-0006, CL-DERPERF-0007 | the guard line and exit 3 under a shrunk port range (agent run) | all Windows numbers: the maintainer's pastes in this conversation |
| CL-DERPERF-0008, CL-DERPERF-0009 | the commit and the test names exist | the 57-input table, the mutation checks and both JDK results (agent report; I did not re-run `UriTest`) |
| CL-DERPERF-0010, CL-DERPERF-0011 | that trunk's `ClassLoaderGrant.java` now imports `CodeSource` (`grep -n import`) | the probe tables and the emulated test counts (agent reports) |
| CL-DERPERF-0012 | nothing: the evidence is the maintainer's pasted mvn output | the conclusion that the image, not the code, is at fault |
| CL-DERPERF-0013 | I ran `MuxChannelBatchingTest` and `SslServerHelloRetryTest` three times each after the port: 3 of 3 passed | the merge verification counts (agent report) |

- **Self-error count: at least 8, NOT cross-checked against the transcript** (so treat it as an under-count; the transcript is
  `/root/.claude/projects/-home-user/d4500893-c6b9-5565-960c-ab4381350b7d.jsonl`, which a later session can grep). Named: (1) said the socket path
  could read ahead (earlier segment, fixed); (2) said testing the JGDMS copy removes the serialization failure (earlier segment); (3) routed one
  message to the wrong agent (earlier segment, disclosed); (4) first diagnosis of the closing server window was the Java version, with no
  information that the maintainer runs DirtyChai; (5) a first `git apply` of the Mux fix skipped the file silently, and I went on to test before reading
  `git status`; (6) a `bash` tool call with the wrong case (no effect); (7) I quoted a sustainable-rate estimate for Windows port exhaustion (70 to
  135 connections per second) from memory with no source on the page; (8) I allocated nine ruling IDs in a shared project scope while another session
  was allocating from the same numbers (section 7), and found it only before syncing.
- **A different model reviewed this document:** see section 10 (an Opus reviewer, run before shipping).

## 1. Where the truth lives

| Question | Authoritative file (paths from the JGDMS repo root; docs are under `JGDMS/docs/`) |
|---|---|
| What the DER codec, streaming encoder and admission work built and decided | `HANDOVER-der-codec-performance-2026-10-18.md` and `SOW-DER-Streaming-Encoder.md` |
| The TLS accept path (S0): the board's rulings, the bounds, the cold review of 2026-10-23 and what it fixed | `BOARD-REVIEW-S0-tls-accept-path-2026-10-20.md` (last addendum: "Addendum, 2026-10-23: the cold review of the accept path") and `DESIGN-tls-nonblocking-connectionio-2026-10-20.md` sections 2 and 4.6 |
| Benchmarks, the handshake sections 22 to 41, the Windows port-exhaustion section | `jgdms-benchmarks/serialization/RESULTS.md` (last section: "Running TlsAcceptRate on Windows without running out of ports (2026-10-24)") |
| The api.security copies in JGDMS against DirtyChai's bundled copies: D1 to D11, J1 to J9, the emulation recipe, the package layout | `NON-CONFORMANCES-api-security-dirtychai-2026-10-23.md` |
| Accepted API breaks (category G is the SubjectDomain removal) | `JGDMS-API-Compatibility-Accepted-Breaks.md` |
| Deserialization into a privileged context (plan, not started) | `PLAN-check-and-inject-unprivileged-construction-2026-10-19.md` |
| Claims and rulings | the register (header above) |

## 2. What happened, by stream (claims are cited by ID; the register holds what is believed about each)

1. **TLS accept path (S0).** Four board reviews, then a fifth Opus cold review (2026-10-23) of the record counter and the retry reading. Fixes
   and boundary tests: CL-DERPERF-0001, CL-DERPERF-0002. Open: CL-DERPERF-0003, CL-DERPERF-0004.
2. **Windows measurements** (the maintainer runs them; this container has 4 cores): CL-DERPERF-0005, CL-DERPERF-0006, CL-DERPERF-0007. Tooling
   built: `ServerMain -Dbench.server.keepalive=true`; `TlsAcceptRate` options `bench.connect.hosts`, `bench.bind.hosts`, the guard and the `TIME_WAIT` lines.
3. **api.security alignment.** Platform tests no longer patch `java.base` or use `-Xbootclasspath/a`; seven JGDMS-only classes moved to
   `au.net.zeus.jgdms.api.policy`; D2 and D5 followed in the JGDMS copy; `SubjectDomain` removed; J8 and J9 fixed; DirtyChai pin moved to
   `4c71278f1`. Claims: CL-DERPERF-0008 to CL-DERPERF-0012. The rulings behind these are in section 7.
4. **Housekeeping.** Trunk merged into the branch (`49afb8a6b`); five commits of the maintainer's `sow-permissiongrant-domain-grants` line cherry-picked
   (`PreferredClassLoaderCodebaseGrantTest`, `SubProcessLocalPolicyAdminTest`, two `Variants` commits, `UserSubjectCompilePortabilityTest`); the Mux test race: CL-DERPERF-0013.

## 3. STATE (FASTEST-AGEING: re-read the disk before acting on it; its staleness is a measured property)

AS-OF 2026-11-14. Measured, not remembered (command in each line):
- `git rev-list --left-right --count origin/claude/focused-bohr-t6bwal...HEAD` in the JGDMS clone: `0 0`; `git status --short | wc -l`: `0`.
  The population is the JGDMS clone at `/home/user/JGDMS` in this container, which is discarded; the branch on GitHub is the copy that survives.
- `git rev-list --count HEAD..origin/trunk` = `56`: the maintainer's trunk has moved 56 commits since my merge of 2026-11-11. The merge is to be repeated.
- The DirtyChai clone `/home/user/pfirmstone/dirtychai` (read-only, shallow) is at `4c71278f1`; `git rev-list --count HEAD..origin/trunk` there = `5`
  (newest `7d58b3b3f`, 2026-11-13: SPIRE retry #237, SerialObjectPermission #213 and the #236 follow-ups). **The emulation pin in the non-conformances doc is
  therefore stale again** (CL-DERPERF-0012 depends on a newer image).
- Not pushed: the local branch `stubs-experiment` (`91b1531c3`, worktree `/tmp/claude-0/stubs-wt`, JGDMS clone). It dies with the container. The maintainer has not been
  asked since he answered that stubs are not adopted (section 7); ask before pushing a branch he has not named.
- The register: this session's hypotheses are published (`b58e690470` on pfirmstone/Claude master). Its rulings are not (section 7).
- No subagent is running at composition time (the last report was received before this wrap-up began).

## 4. Open items (each cites its ID; none restates a status)

1. **Windows runs** (maintainer): the warm-up cap runs (default, 24, 12, post-flight bound off) and the three-address run. CL-DERPERF-0004, CL-DERPERF-0006, CL-DERPERF-0007; the jar sent was built from `d7dfb0140`.
   The cap default (`maxServerHandshakes`) stays as it is until then.
2. **The window that closed** (CL-DERPERF-0005): ask whether the working launch used the keepalive flag.
3. **Maintainer decisions pending** (all in section D of the non-conformances doc): does the D5 ruling extend to `ProtectionDomainGrant`; keep or drop the `unverified()` guard
   on `DigestDomainGrant`; should a PROTECTIONDOMAIN grant without a class be void; confirm D11 (`CodeSource(URL)` now throws for an invalid escape).
   DirtyChai-side, his to write: `au.zeus.jdk.net.Uri.toURL()` with user info (CL-DERPERF-0009), the stale `UnresolvedPrincipal` Javadoc (D10).
4. **Verification gaps carried honestly:** the serial-schema gate and the trunk-baseline half of the API-compat gate could not run offline (need `asm`); twelve-odd modules
   do not build offline (list in the merge verification report, `/tmp/claude-0/-home-user/d4500893-c6b9-5565-960c-ab4381350b7d/scratchpad/mergecheck/` logs, container only); the QA harness
   edits for D2 were made by reading and never run; no non-JDK TLS client was tried (CL-DERPERF-0003); the Windows branch of `Uri.implies` is untested on Windows.
5. **Repeat the trunk merge** (56 commits behind) and re-run `jgdms-platform` and `jgdms-jeri`; refresh the emulation against DirtyChai trunk (recipe in the non-conformances doc, "Refresh at DirtyChai `4c71278f1` (2026-11-11)").
6. **Register:** allocate the nine rulings of section 7 once the numbering collision is cleared.
7. Older, still open (from the predecessor handover and my earlier summary): the plan to refuse deserialization into a privileged context has not been started; the "three
   handshake bounds" redesign was never decided; an OSGi resolution test on a real Felix with `org.osgi.framework.bootdelegation=org.apache.river.api.security` is untested; J7
   (removing `Permission` serialization from the JGDMS copies) is open; PermissionDelegate is for the maintainer to rewrite (provenance).

## 5. Do NOT redo (each conditional: what it rests on, and what unblocks it)

| Item | Rests on | Unblocks when |
|---|---|---|
| Patching `java.base` or `-Xbootclasspath/a` in the platform tests | RULING (section 7 item 1) | the maintainer says otherwise |
| Writing or committing any DirtyChai source | RULING (section 7 item 9): no AI-assisted code in DirtyChai; the second half of the statement (agents only describe changes) is my reading of it | OpenJDK policy changes |
| Changing the `maxServerHandshakes` default | EVIDENCE: container runs only, 4 cores, JDK 27, no clean warm Windows run | CL-DERPERF-0004 has clean warm runs |
| Adopting the compile-time stubs | RULING (section 7 item 3) | the maintainer decides; the experiment is `stubs-experiment` |
| Re-running the Opus cold review of RecordCounter | EVIDENCE: JDK 27 client framings only, in-memory engine pair, CPU in 10 ms clock steps | a non-JDK client is tried (CL-DERPERF-0003) or the bound's constants change |
| Rebuilding the emulation | EVIDENCE: valid for DirtyChai `4c71278f1` only | DirtyChai trunk moves (it has: section 3) |

## 6. Triggers (written to fire at the act, not to describe a failure)

- **BEFORE DIAGNOSING A FAILURE ON THE MAINTAINER'S MACHINE, ASK WHICH JDK AND DIRTYCHAI BUILD IS INSTALLED THERE.** This container's image JDK is not his, and four of this session's
  surprises (CL-DERPERF-0010, CL-DERPERF-0011, CL-DERPERF-0012, the closing window) were that difference.
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

| # | project | statement | verbatim words |
|---|---|---|---|
| 1 | JGDMS | JGDMS tests do not use -Xbootclasspath/a (nor patching java.base by default); the jgdms-platform tests exercise the shared org.apache.river.api.security classes through their public API against whichever copy the JDK loads | -Xbootclasspath/a I don't wish to use this option. Can we test the classes using public API instead? |
| 2 | JGDMS | The seven JGDMS-only (non-Apache-River) classes ExternallyVoidablePermissionGrant, LocalPrincipalProvider, LeasedDelegation, LeasedPermissionGrant, OneShot, OneShotLeasedPermissionGrant and UserSubjectSupport live in au.net.zeus.jgdms.api.policy, not in the shared org.apache.river.api.security package, which is kept as small as possible | I'm considering however that they should be moved into a different package. They aren't of Apache River origin and we want to minimise anything in that shared package. A more appropriate package for them would be au.net.zeus.jgdms.api.policy |
| 3 | JGDMS | Compile-time stubs of DirtyChai's org.apache.river.api.security classes, in place of the bundled copies, are not adopted yet; the copies stay for now and the stubs are noted as planned work | 1. No not yet. |
| 4 | JGDMS | The org.apache.river.api.security classes that remain in JGDMS are for compile-time support on standard OpenJDK and are not for production; production on DirtyChai uses DirtyChai's own copies, so no DirtyChai-only behaviour (such as DomainIdentity.getCallerDomain in DigestDomainGrant) is added to the JGDMS copies | The classes that remain in JGDMS are for compile time support on standard OpenJDK, they're not for production. |
| 5 | JGDMS | The SubjectDomain feature (SubjectDomain, Security.doAs and doAsPrivileged, DistributedSubjectCombiner) is removed from jgdms-platform; better solutions to the same problem are being implemented | SubjectDomain We are implementing better solutions to the problem it attempts to solve.  Yes remove it please. |
| 6 | JGDMS | Serialization support is to be removed from the bundled security classes as bad security practice, so throwing on serialization is the intended behaviour | The intent is to remove serialization support from these classes as it is not good security practise, so it was easier to make it throw than implement it. |
| 7 | DIRTYCHAI | A CLASSLOADER grant with no class is void on purpose: the grant is garbage collected after the ClassLoader it grants permission for has been removed, so it must never become a grant to all domains | Yes, this is intended to garbage collect the grant after the ClassLoader it grants permission for has been removed. |
| 8 | DIRTYCHAI | DigestDomainGrant is a dynamic grant that lasts only as long as the ClassLoader it is granted to; its identity is whether the DigestCodeSource is one that ClassLoader loaded, not signer order, a signer set or isVerifiedDigest | D5 DigestDomainGrant is a dynamic grant that only lasts as long as the ClassLoader it is granted to, in this case if DigestCodeSource is either one loaded by the ClassLoader, or it isn't. |
| 9 | DIRTYCHAI | No AI-assisted code goes into DirtyChai (OpenJDK policy); AI-assisted code such as PermissionDelegate is rewritten by the maintainer, so agents propose changes for DirtyChai as descriptions and never write or commit DirtyChai source | PermissionDelegate counts as AI assisted, so I will probably need to rewrite that at some stage. |

## 8. Operational knowledge I would not rediscover

- **Builds.** `JAVA_HOME=/opt/dirtychai/jdk-27`; run `mvn -o` from the `JGDMS/` directory because `JGDMS/.mvn/maven.config` has a relative `-s .mvn/settings.xml`. Use a private repository copy
  (`cp -r ~/.m2/repository <scratch>/m2repo`, `-Dmaven.repo.local=...`) so `~/.m2` stays clean. `jgdms-benchmarks` is not in the reactor: install `jgdms-jeri` and `jgdms-rmi-tls` first, then
  `mvn -o -f jgdms-benchmarks/pom.xml -pl serialization -am -DskipTests package`; revert the regenerated `dependency-reduced-pom.xml`.
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
| a7be4165110042fb3 | Fable | UDS bind fix review (2026-10-20; self-reported, not re-verified) | see the predecessor handover |

My reading of them (mine, not theirs): every Sonnet report that included a count was later consistent with a re-run or a second agent's run; the two places where an agent's first
proposal was wrong (J9, and the arithmetic of the prefix) were caught by the same agent running the case. Their scratchpad directories live in the container only.

## 10. Review by a different model

(filled in below by the Opus review of this document before it is pushed)

## 11. CURRENCY CHECK (mechanical, run at composition 2026-11-14)

Enumerated from `git log --first-parent 9465d4661..HEAD` (83 commits) and this conversation: the TLS accept-path fixes, the Windows tooling (keepalive, `TlsAcceptRate` options and guard), the api.security work (D1 to D6, D9, J8, J9, the move, the stubs question, the emulation pins), the trunk merge and the cherry-picks, the Mux race, the register. `grep -ci` of one keyword from each against this file (a zero would be a gap): S0:2 RecordCounter:2 keepalive:3 TlsAcceptRate:3 SubjectDomain:4 D2:3 D5:5 J8:3 J9:6 stubs:4 emulation:6 cherry:2 Windows:8 TIME_WAIT:1 maxServerHandshakes:2 4c71278f1:5 ProtectionDomainGrant:1 Felix:1 J7:1 plan:3. Every item is either in this file or excluded below.
Deliberately excluded: the individual test logs (in the container's scratchpad), the 5,000-row census of other agents' handovers (not mine), and the full agent transcripts.
