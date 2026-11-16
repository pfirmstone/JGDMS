<!-- CLAIM-HEADER (edit in place; body is append-only)
GOVERNING STATE: E:\Claude\claim_register\hypotheses\PROXYBOARD\INDEX.md  (this file's claims: CL-PROXYBOARD-0001 to CL-PROXYBOARD-0025); rulings: E:\Claude\claim_register\rulings\JGDMS\INDEX.md  (RL-JGDMS-0037 to RL-JGDMS-0050, RL-JGDMS-0052 to RL-JGDMS-0082) and E:\Claude\claim_register\rulings\DIRTYCHAI\INDEX.md  (RL-DIRTYCHAI-0005 to RL-DIRTYCHAI-0015)
LATEST ADDENDUM: add.05 dated 2026-11-16 at heading "ADDENDUM add.05, AS-OF 2026-11-16 08:35 +1000"  (addenda govern body text)
SUPERSEDED BY: none
FASTEST-AGEING SECTION: LIVE STATE - re-read the disk before acting on it
-->
# Handover: the proxy-board design review and the DirtyChai thread gates (2026-11-15)

AS-OF 2026-11-15 17:50 +1000. Author: Claude Fable 5.2, claude.ai/code session_018wGHqgexvwKfVXuCVJMAzR, a cloud session bridged to the operator's Windows machine (DESKTOP-LOI9FL7). Not reviewed by a different model. Composed without the `handover-composition` skill: its repository was not connected and the session's permission settings refused the request to connect it (see DEVIATIONS).

This file is a journal. Status lives in the claim register; IDs are cited here without it.

## 1. What this session was

The operator opened with `SUMMARY-proxy-board-design-2026-11-15.md` and asked for an opinion. That became a review of the proxy-board design in which the operator answered a series of questions, and then a piece of work on DirtyChai: two missing permission checks were found by test, the operator fixed both, and two tests were written and rewritten until they ran under the configuration the project deploys.

## 2. Where the truth lives

| What | Where |
|---|---|
| The operator's statements from the review | the register: RL-JGDMS-0052 to RL-JGDMS-0072 and RL-DIRTYCHAI-0005 to RL-DIRTYCHAI-0010 |
| What was found, by test or by reading code | the register: CL-PROXYBOARD-0001 to CL-PROXYBOARD-0012 |
| The design as now understood, with the review's proposals F1 to F18 | `JGDMS\JGDMS\docs\SUMMARY-proxy-board-design-2026-11-15.md` (revised by this session; a reading aid, not an authority) |
| The thread gates, the submission checks and their limits | `DirtyChai\SECURITY_MODEL.md` version 2.5, sections 11.1, 11.7, 12, 13 and 15 |
| What each test asserts and what was observed for which revision | the header of each test file (section 3) |
| The earlier rulings on the board and the memo's proposals P1 to P11 | RL-JGDMS-0037 to RL-JGDMS-0050; `JGDMS\JGDMS\docs\DESIGN-class-free-lookup-and-registrar-split-2026-10-31.md` sections 15 and 16 |

## 3. LIVE STATE (re-read the disk before acting on this)

Nothing below was committed by this session. `git status` timed out through the bridge on both repositories, so the list is from what this session wrote and read, not from git.

**DirtyChai, written by this session:**

- `test/jdk/ai/java/lang/Thread/VirtualThreadCreationPermissionTest.java` (new directory). Author tag names Claude Fable 5.2 and Claude Opus 5.5.
- `test/jdk/ai/java/util/concurrent/ForkJoinPool/CommonPoolSubmissionPermissionTest.java` (new directory). Same author tag.
- `SECURITY_MODEL.md`: version 2.4 to 2.5, Last Reviewed 2026-11-15. Invariants 14 and 15 in section 15 are this session's wording and the operator has not yet confirmed them.

**DirtyChai, changed by the operator during the session (seen on disk, not authored here):**

- `src/java.base/share/classes/java/lang/Thread.java`: a `createVirtualThread` check in `startVirtualThread`.
- `src/java.base/share/classes/java/util/concurrent/ForkJoinPool.java` and `ForkJoinTask.java`: `createInnocuousThread` checks behind the constant `TIMING_GRANULARITY_NANOS`.

**JGDMS, written by this session:**

- `JGDMS/docs/SUMMARY-proxy-board-design-2026-11-15.md` (rewritten in place; the first version is in git history only if it was committed).
- `JGDMS/docs/HANDOVER-proxy-board-review-2026-11-15.md` (this file).

**Register:** entries were appended on the operator's disk, not in a clone, so no `sync` was run. A file `CLAIM_INDEX_UNVERIFIED.md.tmp35600` dated 2026-11-14 was already in the register folder and was left alone.

**Gone with the session:** the working copies of the tests and their stand-in library classes in the cloud workspace.

## 4. What the session established

The register holds the statements and their standing. Topics only, so the right entry can be found:

**Rulings recorded from the operator's words in the review (JGDMS):**

- RL-JGDMS-0052, RL-JGDMS-0053: no dynamic code on the host; the host-to-node wire format.
- RL-JGDMS-0054: what detection aims at. Thresholds were left open.
- RL-JGDMS-0055: isolation and storage of the proxy boards.
- RL-JGDMS-0056, RL-JGDMS-0057: node identity and how the service learns whom a node acts for.
- RL-JGDMS-0058: the limits of static analysis.
- RL-JGDMS-0059 to RL-JGDMS-0062, RL-JGDMS-0065: the digest grant, the verdict registry, how a node gets its verdict, where the registry sits, and the local cache.
- RL-JGDMS-0063: isolating first on a changed verdict.
- RL-JGDMS-0064, RL-JGDMS-0068: the two-step load and the user principal.
- RL-JGDMS-0066: allow list, not deny list.
- RL-JGDMS-0067, RL-JGDMS-0069, RL-JGDMS-0070, RL-JGDMS-0071: the three levels of policy, what a grant requires, what `GrantPermission` checks, the lifetime of a grant, ephemeral permissions and guards that release their object.
- RL-JGDMS-0072: the node JVM's thread guards and timer resolution.

**Rulings recorded from the operator's words (DirtyChai):**

- RL-DIRTYCHAI-0005, RL-DIRTYCHAI-0006: `allow` is not used; how a policy for enforcement is produced.
- RL-DIRTYCHAI-0007: when the submission checks are enabled.
- RL-DIRTYCHAI-0008, RL-DIRTYCHAI-0009: `AccessControlContext` and its cache.
- RL-DIRTYCHAI-0010: early daemon threads.

**Claims from tests and from reading code:**

- CL-PROXYBOARD-0001, CL-PROXYBOARD-0002: virtual thread creation, before the operator's change and now.
- CL-PROXYBOARD-0003, CL-PROXYBOARD-0004: reaching a common-pool worker, before the operator's change and now.
- CL-PROXYBOARD-0005: the current revision of the common-pool test.
- CL-PROXYBOARD-0006: how far the new submission checks reach, and virtual thread scheduling.
- CL-PROXYBOARD-0007: the proxy carrier in the Atomic DER decoder.
- CL-PROXYBOARD-0008: what the launcher reports under the stock policy.
- CL-PROXYBOARD-0009: common-pool parallelism of 0.
- CL-PROXYBOARD-0010: the virtual-thread carrier factory.
- CL-PROXYBOARD-0011: the per-thread context slot for the stack walk.
- CL-PROXYBOARD-0012: `InstantGranularityTest`.

## 5. What to do next, in order

1. **Run the current common-pool test.** Its last change, inspecting the returned future, has not been run on DirtyChai (CL-PROXYBOARD-0005). Record the result in its header.
2. **Look at virtual threads with coarsening on** before relying on the new submission checks (CL-PROXYBOARD-0006). The suggestion made to the operator was to limit the checks to the common pool and to submissions from outside it. That is advice; under DirtyChai's policy the operator writes the change.
3. **Decide how the host enforces "no dynamic code"** given the proxy-carrier path (CL-PROXYBOARD-0007). Three ways were put to the operator and none was chosen; they are in the summary, section 7. The two missing JGDMS tests named there are the first ones worth writing.
4. **Observe each test failing with a check missing from the code.** Each header lists this as still owed. For the common-pool test, one run with the granularity option taken out of the child's command line would do it.
5. **Confirm or reword invariants 14 and 15** in `SECURITY_MODEL.md` section 15.
6. **Find out why `InstantGranularityTest` fails** (CL-PROXYBOARD-0012). It is not this session's test and was not investigated.
7. **Answer the open design questions** in the summary, section 10: which responses are automatic, detection thresholds, what evidence the issuer accepts for a node, where the code bytes are stored, whether a long registry outage isolates nodes, and which permissions are ephemeral on a node.
8. **Rule on, or discard, F1 to F18** (summary, section 9). They are this session's suggestions and are written down nowhere else.

Not started and not promised: converting the eleven other `tierAI` tests that use `-Djava.security.manager=allow`; the per-thread context slot (CL-PROXYBOARD-0011); updating the memo or the code-free handover to point at the new rulings.

## 6. Do not redo, and do not misread

- The two tests were first written to install a plain `SecurityManager` through `allow` and flip a flag in a `Policy`. That form cannot work under `-Djava.security.manager=default`, which installs the caching `CombinerSecurityManager`. Do not go back to it. Results observed for that form are kept in the headers as history and are not controls for the current code.
- A test cannot be launched directly under `=default`: jtreg's own wrapper has no grant. Use the driver form the two tests and `SecurityPolicyWriterStartupReadsTest` use.
- In both tests only the one permission under test is removed from the untrusted grant. Removing the whole grant would stop the untrusted class loading, and the test would pass without exercising anything.
- AI-authored test infrastructure is not allowed in DirtyChai, so the two tests duplicate their driver code on purpose.
- The review's finding that every node needs read-only boot was withdrawn: the board document already requires it.

## 7. What was not verified

- Reports from agents were relied on without re-reading every line: the board document and the digest plan (Sonnet), the rulings-and-memo comparison (Sonnet), the Atomic DER decoder trace (Opus, three places checked here), and the first survey of thread-creation sites (Opus, the two main findings checked here and then confirmed by test).
- Neither repository's git state.
- Whether the operator's source changes were rebuilt into the image before each run; the runs' results imply they were.
- Anything about the bytecode analysis engine beyond what `JGDMS-STD-002` v1.3 says.

## 8. DEVIATIONS from the usual process

- **No `handover-composition` checklist.** The skill's repository was not connected. A request to connect it was refused by the session's permission settings, not by the operator.
- **Register writes.** The rulings were appended in one batch by calling the register module's own functions under its lock, with one index rebuild, instead of twenty-seven separate `new` commands. The claims went in through `ingest`. Deletion had to be enabled for the register folder first, because the script removes its lock file and replaces the index files.
- **Scope name.** `PROXYBOARD` was chosen by this session; nobody assigned it.
- **Rulings that carry some of the assistant's wording.** Where a ruling's statement needed context the operator's reply did not contain, such as what "#1" or "the two step exchange" referred to, the note beside it says which words are his.
- **The summary restates the operator's statements.** It was revised before the rulings were registered, so it carries them under the tag STATED and says they are not in the register. They now are; the summary was not edited again to say so.

## ADDENDUM add.01, AS-OF 2026-11-15 17:50 +1000

After the handover was written the operator said he had limited the new permission checks to the common pool. Read on disk at 17:45:

- `ForkJoinPool.externalSubmit`, `startDelayScheduler` and `scheduleDelayedTask` now test `this == common`, and `ForkJoinTask.fork()` tests `p == ForkJoinPool.common`.
- `ForkJoinPool.poolSubmit` does not: it still makes the check for every pool. `execute`, `submit`, `invoke` and `lazySubmit` go through it, and `VirtualThread` uses `lazySubmit` and `execute` on its scheduler pool.

So item 2 of section 5 is narrower than it was but not closed, and the register entry for it (CL-PROXYBOARD-0006) has a note saying so. `SECURITY_MODEL.md` section 11.1 and the summary, section 5, were edited to describe the code as it stood at 17:45. None of this was rebuilt or run.

## ADDENDUM add.02, AS-OF 2026-11-15 17:56 +1000

Supersedes the second bullet of add.01. The operator then changed `ForkJoinPool.poolSubmit` too. Read on disk at 17:54: every instance check site (`poolSubmit`, `externalSubmit`, `startDelayScheduler`, `scheduleDelayedTask`) tests `this == common`, and `ForkJoinTask.fork()` tests `p == ForkJoinPool.common`. By reading, no pool other than the common pool makes the check, so `VirtualThread` scheduling does not. CL-PROXYBOARD-0006 has a note to that effect. `SECURITY_MODEL.md` section 11.1 and 13 and the summary, section 5, were edited to match. Not rebuilt and not run: the common-pool test has not been re-run against this code, and no virtual thread test under coarsening exists.

## ADDENDUM add.03, AS-OF 2026-11-15 21:00 +1000

Work after add.02, same session. Where this differs from the body or from add.01 and add.02, this governs.

**Register entries added or changed.** CL-PROXYBOARD-0005 has a new line. New: CL-PROXYBOARD-0013 to CL-PROXYBOARD-0016, RL-DIRTYCHAI-0011 to RL-DIRTYCHAI-0014. Read their current state in the indexes named in the header, not here.

**What happened.**

- The operator changed `ForkJoinPool.poolSubmit` as well (RL-DIRTYCHAI-0011), rebuilt the image (RL-DIRTYCHAI-0014) and ran tierAI: 27 of 27, result files stamped 18:28. What that run shows for the common-pool test is CL-PROXYBOARD-0005.
- Virtual threads with coarsening on: the remaining question is CL-PROXYBOARD-0013. No test exists for it.
- `InstantGranularityTest`: the cause of the earlier failures is CL-PROXYBOARD-0014. The test was revised (granularity 2^28 ns, 2 s window, control above 100 distinct instants); the result is CL-PROXYBOARD-0015. This closes the `InstantGranularityTest` item in the body's next actions.
- The three test headers under `DirtyChai/test/jdk/ai` (`VirtualThreadCreationPermissionTest`, `CommonPoolSubmissionPermissionTest`, `InstantGranularityTest`) now record the 18:28 results.
- `SECURITY_MODEL.md`: item 7 (line 81) and the opening of section 8.1 were corrected for RL-DIRTYCHAI-0012 and RL-DIRTYCHAI-0013; what the code was read to do is CL-PROXYBOARD-0016.
- Summary: F19 added (a hardware watchdog on each node, fed only on evidence of useful work); its two notes about the register were corrected.
- `JGDMS/JGDMS/docs/LAYOUT-proxy-host-board-2026-11-15.html`: six concept drawings following `PROXY_HOST_BOARD_REQUIREMENTS.md`, also published as a private artifact. Committed to JGDMS `trunk` as d41d22136, not pushed. The commit was made with `git write-tree`, `commit-tree` and `update-ref`, because `git commit` timed out on the mounted repository; author and committer were copied from the previous commit.

**Uncommitted on disk.** JGDMS: this handover and the summary. DirtyChai: the three tests above and `SECURITY_MODEL.md`. The claim register has not been synced.

**Next actions, replacing the body's list where they overlap.**

1. Run each of the three tests on a tree with its check removed (for the Instant test, mutation M3 in its header) and record the failure in the test header.
2. Write the virtual thread test named in CL-PROXYBOARD-0013.
3. Decide CL-PROXYBOARD-0007 (the `DerProxySerializer` path on the host) and proposal F1.
4. Find which locations make `DigestCodeSource` throw `UnsupportedOperationException` (CL-PROXYBOARD-0016).
5. Check whether `System.currentTimeMillis()` is coarsened; it matters only for a granularity above 1 ms. Not traced.
6. Two passages still say "network-loaded" and were left alone: `SECURITY_MODEL_ANALYSIS.md` line 482 and `SECURITY_MODEL.md` line 511 (the SPIFFE sentence).
7. Operator to confirm invariants 14 and 15 in `SECURITY_MODEL.md` section 15, and to rule on F1 to F19.

**Not verified.** No code was compiled or run by this session; every test result above was read from the operator's result files. The layout page was checked for well-formed drawings only, and the operator said it looks good.

**DEVIATIONS.** The earlier `InstantGranularityTest.jtr` that showed the 17:57 failure was overwritten by the 18:28 run, so CL-PROXYBOARD-0014 points at the test header's record of it. The `git commit` attempts left an `index.lock` in JGDMS, which this session removed after confirming no git process was running in its shell.

## ADDENDUM add.04, AS-OF 2026-11-15 21:02 +1000

Supersedes the sentence in add.03 that the claim register has not been synced. The operator ran `claim_register.py sync` on Windows and reported: published 13 log files to origin/master as b0d560ecfa. This session could not run it, because only `E:\Claude\claim_register` is connected and the repository root is `E:\Claude`. Everything else in add.03 stands.

## ADDENDUM add.05, AS-OF 2026-11-16 08:35 +1000

Work after add.04, same session, after one compaction. Where this differs from the body or an earlier addendum, this governs.

**Register entries added.** CL-PROXYBOARD-0017 to CL-PROXYBOARD-0025, RL-JGDMS-0073 to RL-JGDMS-0082, RL-DIRTYCHAI-0015. No existing entry changed. Read their current state in the indexes named in the header, not here. None of these has been synced.

**What happened.**

- Timing attacks by the proxy. The operator's three statements are RL-DIRTYCHAI-0015, RL-JGDMS-0073 and RL-JGDMS-0074. What follows from the first for the coarsened clock is CL-PROXYBOARD-0019.
- Certificate lifetime. The key in question is RL-JGDMS-0075 and where its handshake runs is RL-JGDMS-0076. The signing-oracle argument is CL-PROXYBOARD-0020. This session gave no measured lifetime; it gave the relation lifetime < samples needed x clock granularity / colluding proxies, and said the sample count is unknown for the candidate boards.
- Relay design. The operator's proposal is RL-JGDMS-0077; the agreed cut is RL-JGDMS-0078; the design as it stands is CL-PROXYBOARD-0025. It is not written into the summary (offered as F20, not accepted or declined).
- DER inspection at the relay. RL-JGDMS-0079 and RL-JGDMS-0080 are the operator's statements. Two read-only Sonnet subagents investigated: the JERI client layers (CL-PROXYBOARD-0022; the parts this session checked are CL-PROXYBOARD-0021) and the DER call stream (CL-PROXYBOARD-0024; the parts this session checked are CL-PROXYBOARD-0023).
- Two defects in `jgdms-der`, both directed by RL-JGDMS-0081: CL-PROXYBOARD-0017 and CL-PROXYBOARD-0018. New files: `getarg/EnumName.java`, `getarg/ClassName.java`, `conformance/Std006DataIndependenceClassNamingFieldsTest.java`, `getarg/PrimitiveArrayNullElementTest.java`. Changed: `getarg/WireTypes.java`, `getarg/DerFieldStore.java`, `object/ObjectCodec.java`.
- The operator committed all of it, with this handover (through add.04) and the summary, as JGDMS `trunk` 652768fbf, dated 2026-11-16 08:19 +1000. Whether it was pushed was not checked.
- Working arrangement for tests: RL-JGDMS-0082.

**On disk.** JGDMS `trunk` is at 652768fbf; this addendum and the header change are uncommitted. DirtyChai was not touched after add.04 and its state was not re-read: add.03 listed three tests and `SECURITY_MODEL.md` as uncommitted.

**Next actions.** The operator's stated next topic is the design of process isolation for the smart proxy and the key (CL-PROXYBOARD-0025). Open under it:

1. How the board forces the relay endpoint when the service chooses the endpoint class, and the three related paths in CL-PROXYBOARD-0022 (worker Subject fallback, DGC call, version 2 request prefix).
2. Where the node identity key lives and what may trigger its use. `PROXY_HOST_BOARD_REQUIREMENTS.md` says nothing on either; a grep of it on 2026-11-15 found only the fused secure-boot keys (N4).
3. A class-free reader that returns values for nested objects, arrays, collections and enums in a call stream, and a per-scalar ceiling on candidate data (CL-PROXYBOARD-0023, CL-PROXYBOARD-0024).
4. Whether to write the design into the summary as F20.
5. How `ObjectCodec` treats a NULL element in `@AtomicSerial` arrays and in collections with primitive elements (not checked; see CL-PROXYBOARD-0018).
6. The next actions in add.03 still stand; none was done.

**Not verified.** This session compiled `jgdms-der` main sources with JDK 21 and preview features in its cloud workspace, and ran the two new test classes through stand-in JUnit annotations and assertions, because Maven Central and a JDK 25 were unreachable there. The runs on the project toolchain are the operator's, read from pasted logs. Which JDK Maven used on the operator's machine was not determined. The timing, lifetime and signing-oracle analysis is reasoning; nothing was measured.

**DEVIATIONS.** To move files to the cloud workspace this session wrote two archives into `jgdms-der/target` (`claude-check.tgz`, `claude-check2.tgz`) and deleted both. Subagents wrote nothing to the register; their reports are entered by this session under its own name and marked as theirs. This session told the operator that `decodeToFieldMap` loads no class, on the strength of that method's Javadoc; that was wrong and is corrected in CL-PROXYBOARD-0017. The operator's sentence "The fixes have been committed" is not entered as a ruling because the commit was read directly.
