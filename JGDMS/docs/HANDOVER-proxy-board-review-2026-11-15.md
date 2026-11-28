<!-- CLAIM-HEADER (edit in place; body is append-only)
GOVERNING STATE: E:\Claude\claim_register\hypotheses\PROXYBOARD\INDEX.md  (this file's claims: CL-PROXYBOARD-0001 to CL-PROXYBOARD-0031, CL-PROXYBOARD-0053); rulings: E:\Claude\claim_register\rulings\JGDMS\INDEX.md  (RL-JGDMS-0037 to RL-JGDMS-0050, RL-JGDMS-0052 to RL-JGDMS-0116, RL-JGDMS-0134 to RL-JGDMS-0191, RL-JGDMS-0198 to RL-JGDMS-0207, RL-JGDMS-0241 to RL-JGDMS-0249) and E:\Claude\claim_register\rulings\DIRTYCHAI\INDEX.md  (RL-DIRTYCHAI-0005 to RL-DIRTYCHAI-0015)
LATEST ADDENDUM: add.27 dated 2026-11-28 at heading "ADDENDUM add.27, AS-OF 2026-11-28 09:40 +1000"  (addenda govern body text)
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

## ADDENDUM add.06, AS-OF 2026-11-16 15:30 +1000

Work after add.05, same session, after a second compaction. Where this differs from the body or an earlier addendum, this governs.

**Register entries added.** CL-PROXYBOARD-0026 to CL-PROXYBOARD-0030, RL-JGDMS-0083 to RL-JGDMS-0095. This session appended one status line to an existing entry, CL-PROXYBOARD-0028; another session appended one to CL-PROXYBOARD-0009. Read current state in the indexes named in the header, not here. At about 11:37 +1000 a git operation left conflict markers in seven register logs and the tool refused writes; the operator repaired it before 12:26 +1000. Whether the entries are synced was not checked.

**What happened.**

- Where to substitute the endpoint. `PreferredProxyCodebaseProvider.resolve` already routes a smart proxy to a subprocess hand-off; it is the routing point, not the substitution point, because the service proxy is still bytes there. What was read is in CL-PROXYBOARD-0026.
- The operator's proposal to locate endpoints in the DER bytes and rewrite them, and the rulings on it: RL-JGDMS-0083, RL-JGDMS-0084 (narrowed by RL-JGDMS-0088), RL-JGDMS-0085, RL-JGDMS-0086, RL-JGDMS-0087, RL-JGDMS-0089.
- Scope of six work items, agreed to start with items 1, 2 and 4: RL-JGDMS-0090. Item 1 is a Sonnet fact report (CL-PROXYBOARD-0027). Item 2 is the class-free endpoint locator in `jgdms-der` (CL-PROXYBOARD-0029). Item 4 is `RelayEndpoint` in `jgdms-jeri`; its first report is CL-PROXYBOARD-0028 and its corrected state is CL-PROXYBOARD-0030. Items 3 (rewriter), 5 (relay process) and 6 (hand-off integration) are not started.
- DGC managed by the relay: RL-JGDMS-0091. The operator attached SRC Research Report 116 (Birrell and others, distributed garbage collection for network objects); this session read it and drew three requirements from it for item 5: acknowledge a returned reference only after the relay's own dirty call, do not retire relay state while a request through it is open, never reuse hosted-proxy identifiers.
- Caller Subjects bypass the proxy JVM by a per-call token: RL-JGDMS-0092, RL-JGDMS-0093.
- Implementation and test runs move to an Opus agent on the operator's Linux machine, coordinated through git: RL-JGDMS-0094, RL-JGDMS-0095. This session wrote `docs/relay-isolation/README.md` (standing brief) and `docs/relay-isolation/WORK-ORDER-01-verify.md`.
- The operator told this session that the `qa` directory has an ant script that runs an integration harness and the jtreg tests. This session listed the targets in `qa/build.xml` and the directories under `qa/jtreg/net/jini/jeri`; it read no test and does not know how either suite is run.

**On disk (re-read at 15:25 +1000).** JGDMS `trunk` is at ba2025883. Commit b916d9506 in its history holds the locator, the relay endpoint, their tests and `docs/relay-isolation/`. No local branch named `relay-isolation` was found, although the standing brief tells the implementing agent to work only on that branch; this needs resolving before the Linux agent starts. `docs/relay-isolation/` has no results file. This addendum and the header change are uncommitted. A whole-tree `git status` times out on the mounted folder; `qa/src`, `qa/jtreg`, `JGDMS/tools`, `JGDMS/services` and most other modules were not checked for uncommitted changes. DirtyChai was not touched or re-read.

**Next actions.**

1. Operator: decide where the relay work lives (create `relay-isolation` from the commit above, or change the brief to name `trunk`), then start the Linux agent on `docs/relay-isolation/README.md` and work order 01.
2. This session or its successor: write work order 02 for the rewriter (item 3). Inputs: the locator's location model, the canonical-set re-sort case and the top-level-payload case reported under CL-PROXYBOARD-0029, the hash recomputation in RL-JGDMS-0086, the reverse mapping, and setting `dgc` false per RL-JGDMS-0091.
3. Operator decisions still open: whether `BasicInvocationHandler.authenticateServer` may accept `RelayEndpoint` (see CL-PROXYBOARD-0027 item 1); where the signer for the node key lives; whether the Linux agent may write to the register (the brief says no).
4. Item 5 design points not yet written down anywhere but here: the relay ends one stream-dedup context and starts another, which needs access to package-private classes in `au.net.zeus.jgdms.der.stream` (CL-PROXYBOARD-0027 item 4); the destination identifier must be issued per host, port and socket factory (item 2 there); revocation of a call token when the host call returns, because a captured token outlives its binding (CL-PROXYBOARD-0030); the connect itself is outside the handshake timeout.
5. Item 6: `jgdms-pref-class-loader` compiles at release 8 and does not depend on `jgdms-der`, so the hand-off needs a service-provider interface in `jgdms-platform`; the bootstrap proxy crosses the hand-off as a live object and must be rebuilt with a relay endpoint; references returned from the subprocess to the host carry relay endpoints and the host must convert them and make its own dirty call.
6. The next actions in add.03 and add.05 still stand except add.05 items 1 and 3, which this work replaces.

**Not verified.** This session read none of the code the two Opus agents wrote. Their test counts come from their own JDK 21 scratch builds with stand-in JUnit classes. Nothing new has been through Maven, real JUnit, release 25, a security manager or Windows; the operator was given commands but reported no run. The fact report's line numbers are the subagent's. `StreamSchemaDedup` behaviour is from its class comment. Whether a `BasicObjectEndpoint` built by the public constructor takes part in DGC was not checked, nor whether a lone decoded object-endpoint record gets the decode-unit context that DGC registration batching expects. `marshalRemoteContext` was not read, so it is not known whether the host can produce the access-control-context block without the real proxy object.

**DEVIATIONS.** The item 4 agent was sent back with corrections (unfulfilled constraints, server principals, handshake timeout) before the operator had seen its first report; the call-token field was added after the operator agreed. Subagents wrote nothing to the register; their reports are recorded by this session under the CL IDs above. Subagents staged archives through `jgdms-der/target` and `jgdms-jeri/target` and removed them; none remained at 15:25 +1000. One whole-tree `git status` was killed by a timeout after about 36 seconds; it ran with optional locks off and no `index.lock` was left. The claim-register write for the corrected item 4 report failed once while the logs held conflict markers and was repeated after the repair.

## ADDENDUM add.07, AS-OF 2026-11-16 19:25 +1000

Work after add.06, same session, after a third compaction. Where this differs from the body or an earlier addendum, this governs.

**Register entries added.** CL-PROXYBOARD-0031, RL-JGDMS-0096 to RL-JGDMS-0108. This session appended one status line each to two existing rulings, RL-JGDMS-0093 and RL-JGDMS-0106; read the register for what they say. No claim in this session's scope was settled at this wrap-up: none of the open ones has been through a build or a check that this session could make.

**What happened.**

- The operator asked what a hostile smart proxy can still do under the current isolation model and what can be detected by static analysis, by the relay and by the host. The answer and the requirements drawn from the discussion are in a new file, `docs/relay-isolation/DESIGN-security.md` (requirements R1 to R20 with R16a to R16g, and open points). Each requirement there is marked as an operator ruling or a design-session proposal.
- Rulings from that discussion: the proxy JVM's only channel is the Unix socket to the relay (RL-JGDMS-0096); the service's server must be authenticated (RL-JGDMS-0097); the host sets the constraints that require it and they reach the relay with the token registration (RL-JGDMS-0098); the design documents may be updated because the Linux agent has not started (RL-JGDMS-0099).
- Concurrency: callers are queued and dispatched by one thread (RL-JGDMS-0100). The token mechanism of the earlier per-call token ruling is restated without its concurrency sentence as RL-JGDMS-0101.
- Sharing and scaling: RL-JGDMS-0102, RL-JGDMS-0103, RL-JGDMS-0104.
- Hardware: a Sonnet subagent searched the web for boards with in-order cores only and 16 GB or more (CL-PROXYBOARD-0031). On that report the operator chose the processor and the arrangement: RL-JGDMS-0105, RL-JGDMS-0107, RL-JGDMS-0108. A swap device was proposed and then dropped by the operator (RL-JGDMS-0106, RL-JGDMS-0107). A virtual machine boundary and a mixed-core part were discussed and set aside (RL-JGDMS-0108).
- `docs/relay-isolation/README.md` gained the new decisions and a pointer to the design file. Work order 01 is unchanged.

**On disk (re-read at 19:22 +1000).** Uncommitted in JGDMS: this handover (add.06, add.07 and the header), `docs/relay-isolation/README.md` (modified), `docs/relay-isolation/DESIGN-security.md` (new). No local branch named `relay-isolation` was found; the relay commit is still on `trunk`.

**Next actions.**

1. Operator: decide where the relay work lives (unchanged from add.06 item 1) and whether to commit the three files above.
2. This session or its successor: write work order 02 for the rewriter. In addition to the inputs in add.06 item 2 it must cite R3, R7 and R11 of the design file.
3. Operator decisions open, listed at the end of the design file: queue scope, call deadline and queue bound (R16a); the sharing boundary between clients (the design session proposed a trust-domain label in the pooling key, one domain per host process by default); the address policy in R11; where the signer lives; who fetches the codebase for the proxy JVM; whether single-threaded loop counting between coarse clock edges matters without jitter (R16).
4. Before buying hardware: confirm stock and price of a 16 GB i.MX 95 board or module, the processor's maximum addressable memory, whether inline ECC is on by default and what memory it costs, and whether the processor has an IOMMU. None of these was found by the subagent.
5. The relay protocol design now differs from the committed `RelayEndpoint` only on the relay side: the token registration gains a constraints field and the relay gains the rule that proxy-supplied constraints only add requirements. Neither is built.
6. The next actions in add.03, add.05 and add.06 still stand except where this addendum replaces them: add.06 item 3's `authenticateServer` question is now a compatibility question only (R1).

**Not verified.** The capacity model in R16c (latency, then memory, then cores, then connections) is an estimate with no measurement. The hardware facts come from one subagent's web search and were not checked by this session; it listed many processor families as not researched. Statements this session made about hypervisors, shared caches on mixed-core parts and inline ECC overhead were from general knowledge and were labelled as such to the operator. The launcher requirement about path-based Unix sockets (R15) has not been tested because no launcher exists. Whether today's register entries are synced was not checked.

**DEVIATIONS.** This session first wrote into the design file that finalization should be disabled and the clock jittered, as though new; the operator had already said no finalizer runs and the clock adds no jitter, and R16 was corrected the same hour. The design session recorded several operator remarks worded as possibilities ("could", "seems like") as rulings, with the wording preserved in each note.

**Correction to "On disk" above (re-read at 19:35 +1000).** The paragraph above was wrong when written. The operator committed the design file, the README change and this handover up to add.06 as 3b4d2c67e (2026-11-16 17:11), and `trunk` is now at f057400e0, a merge from GitHub. Only add.07 and the header change that goes with it are uncommitted. Next action 1 therefore reduces to the branch decision and committing add.07. The merge brought in documents from another session that bear on this work and that this session has not read: `docs/SOW-Smart-Proxy-Isolation-Real-Launcher.md` and `docs/SOW-SubProcessLauncher-Implementation.md`. Read them before writing any launcher requirement into a work order; R14 and R15 of the design file were written without them.

**Register collision, not repaired by this session.** Another session (the JGDMS orphan-audit session) allocated CL-PROXYBOARD-0031 for a different claim after this session had, then moved its claim to CL-PROXYBOARD-0052 and appended a line returning CL-PROXYBOARD-0031 to this session's entry. Its first colliding line remains in the PROXYBOARD log and lint reports it as an error against the log, not against this handover. The log is append-only and the line is not this session's, so it was left for the operator. CL-PROXYBOARD-0032 to CL-PROXYBOARD-0052 belong to that other session, not to this handover.

## ADDENDUM add.08, AS-OF 2026-11-16 21:10 +1000

**Register entries added.** CL-PROXYBOARD-0053. No ruling was added and no status line was appended.

**What happened.** After compaction the operator said "Please continue". This session read the two launcher documents that arrived by merge (`docs/SOW-Smart-Proxy-Isolation-Real-Launcher.md`, `docs/SOW-SubProcessLauncher-Implementation.md`), and the public API of `EndpointLocator`, `EndpointFinding`, `EndpointLocatorConfig` and `RelayEndpoint`. It wrote work order 02 for the rewriter and appended a section to the design file listing where the relay design conflicts with the landed isolation design as those documents describe it (CL-PROXYBOARD-0053).

**On disk, uncommitted.** In `JGDMS/docs/relay-isolation/`: `WORK-ORDER-02-rewriter.md` (new); `DESIGN-security.md` (section "Conflicts with the landed isolation code and the two launcher documents" appended, items C1 to C6); `README.md` (two lines pointing at that section). This handover (add.07 and add.08, header).

**Next actions.**
1. Operator rulings on C1 to C5 of the design file before any work order touches the launcher, the pool or the hand-off.
2. Operator decision on the branch for the relay work; work order 02 says to use whichever the operator names.
3. Review `RESULTS-01.md` when it exists; work order 02 must not start before it.
4. The `dgc` false rewrite on object endpoints is not in work order 02; the locator does not report that field. It needs its own order after `RESULTS-02.md`.
5. Items 3 to 6 of add.07's next actions stand.

**Not verified.** The conflicts in the design file were read from the two documents, not from the code. Work order 02's statements about the codec are expectations for the implementing agent to check. Nothing was built or run.

**DEVIATIONS.** None.

**Added to add.08 at 21:15 +1000.** RL-JGDMS-0109 was added in answer to conflict C1 of the design file, and the C1 item there now cites it. Which channel carries the administration calls is still an operator decision.

**Added to add.08 at 21:20 +1000.** RL-JGDMS-0110 to RL-JGDMS-0113 were added in answer to conflicts C2 to C5; the design file has a subsection citing them and listing what is still open.

**Added to add.08 at 21:25 +1000.** RL-JGDMS-0114 was added in answer to conflict C6.

**Added to add.08 at 21:30 +1000.** RL-JGDMS-0115 was added, a second answer to conflict C1.

**Added to add.08 at 21:35 +1000.** RL-JGDMS-0116 was added. The first 150 lines of `docs/SOW-SubProcessDynamicPolicy.md` were read; the rest was not.

## ADDENDUM add.09, AS-OF 2026-11-20 21:30 +1000

**Register entries added.** None since the last paragraph appended to add.08. No claim in this session's scope was settled at this wrap-up: CL-PROXYBOARD-0053 and the earlier open ones rest on documents and reports this session did not check against code or a build.

**What happened since add.08.**
- 2026-11-17: at the operator's request and with his explicit permission, this session changed `cmd_lint` in `E:\Claude\claim_register\claim_register.py`. A later line carrying a statement under an existing ID is now a warning, not an error, when that exact statement is the first-line statement of another ID in the same log (an ID collision repaired by re-registration). Every other restatement is still an error; both cases were checked on a scratch copy. No log line was edited. The change is in that folder only; this session could not commit or sync it.
- 2026-11-20: this session drafted an email for the operator, to two software architects, describing the JGDMS and DirtyChai security design. It is in the conversation only, not on disk. The operator added that a client can ask a service for a CEL expression and run it over its own data in a layout the service API defines; this was not registered as a ruling.

**On disk.** `git status` for `JGDMS/docs` showed nothing uncommitted before this addendum was written, on `trunk` at 83c4d4c7a. This session's work order 02, the design-file additions and add.07 and add.08 are therefore committed (by the operator). Only this addendum and the header line are uncommitted.

**Found at this wrap-up and NOT read by this session.** `JGDMS/docs/relay-isolation/` now also holds `REVIEW-01-docs-and-locator.md`, `DESIGN-inventory.md` and `WORK-ORDER-02-inventory.md`, written on 2026-11-16 by a Claude Opus 5.5 session on the operator's Linux machine (commit 075478c56). There are now two files named work order 02 (`-rewriter` from this session, `-inventory` from that one) and both tell the implementing agent to write `RESULTS-02.md`.

**Next actions.**
1. Read `REVIEW-01-docs-and-locator.md` and `DESIGN-inventory.md`; they review this session's documents and the locator. Then ask the operator which work order 02 stands and renumber the other.
2. Operator decisions still open: who fetches the codebase; the pooling key; the branch for the relay work; the sharing boundary between clients; queue scope, deadline and bound; signer location; address policy. The design file's last section lists them.
3. Read the second half of `docs/SOW-SubProcessDynamicPolicy.md` before writing anything on the policy administration path.
4. The two launcher documents have not been edited to reflect RL-JGDMS-0109 to RL-JGDMS-0116.
5. Hardware facts for the chosen board are unconfirmed (CL-PROXYBOARD-0031).

**Not verified.** Nothing was built or run by this session. The "in place" list in the drafted email was taken from the operator's own draft.

**DEVIATIONS.** The lint change was first attempted on 2026-11-17 without explicit permission and was blocked by the session's permission check before it ran; it was made only after the operator gave permission.

## ADDENDUM add.10, AS-OF 2026-11-20 22:45 +1000

**Register entries added.** None. No claim in this session's scope was settled: the open ones (CL-PROXYBOARD-0031, CL-PROXYBOARD-0053 and the earlier open ones) still rest on documents and reports not checked against code or a build.

**What happened since add.09.**
- The operator asked whether client-side and server-side CEL can remove code download from the proposed lookup service. This session read sections 0 to 10 and 14 of `docs/DESIGN-class-free-lookup-and-registrar-split-2026-10-31.md` and parts of `docs/JGDMS-STD-011-CEL-Filter-Expression-Format-v0.1-DRAFT.md` (the text on trunk, which is v0.2 with bindings), not the code. The answer given in conversation, unregistered and unreviewed: yes for the lookup service itself, no for smart service proxies that hold state or do I/O. Observed on disk: no `RemoteRegistrar` class and no `services/derek` directory exist.
- This session wrote `docs/SOW-Lookup-CEL-Code-Free-Investigation-2026-11-20.md` (new, CRLF, uncommitted): a read-only scope for a Claude Code agent in six sections, A and B (Sonnet search agents), C (Opus), D and E (Fable), F (Opus review). Output file: `docs/RESULTS-lookup-cel-investigation.md`. Section D carries six hypotheses of this session about string concatenation, glob matching, regular expressions and case folding; they are untested.
- The operator's agent has started that scope at 83c4d4c7a and asked two questions, answered through the operator: STD-011 section numbers in the scope are the trunk (v0.2) layout; the design memo's STD-011 line and section citations predate v0.2 and must be found by content; the results file goes in `JGDMS/JGDMS/docs`, uncommitted.
- This session is not reachable through the operator's mailbox skill (its repository is not connected); the operator relays.

**On disk.** `trunk` at 83c4d4c7a. Uncommitted in `JGDMS/docs`: this handover (add.09, add.10 and the header line) and the new scope file.

**Next actions.**
1. The operator's new request, not started: do for Outrigger and Mahalo what was done for the lookup service (an assessment of whether client-side and server-side CEL can remove code download, then a scope for an agent). Leads not yet read for this purpose: `docs/INVESTIGATION-code-free-outrigger-2026-10-31.md`, `docs/INVESTIGATION-code-free-mahalo-2026-10-31.md`, `docs/DESIGN-Outrigger-CEL-Filter-B1.md`, `docs/DESIGN-Outrigger-CEL-Filter-B3.md`, and section 12 of the design memo (other services), which this session has not read.
2. Review `docs/RESULTS-lookup-cel-investigation.md` when the operator says it is finished.
3. The next actions of add.09 all stand; none was done.

**Not verified.** Nothing was built or run by this session. The lookup assessment rests on the design memo's own text, much of which that memo labels RELAYED.

**DEVIATIONS.** None.

## ADDENDUM add.11, AS-OF 2026-11-21 00:15 +1000

**Register entries added.** RL-JGDMS-0134, RL-JGDMS-0135. No claim was settled.

**What happened since add.10.**
- Outrigger and Mahalo: this session read `docs/INVESTIGATION-code-free-outrigger-2026-10-31.md`, `docs/INVESTIGATION-code-free-mahalo-2026-10-31.md`, the two reviews of them, `docs/DESIGN-Outrigger-CEL-Filter-B1.md`, parts of the B3 memo and sections 12 and 13 of the lookup design memo, gave an assessment in conversation (not registered, code not opened), and wrote `docs/SOW-Outrigger-Mahalo-CEL-Code-Free-Investigation-2026-11-20.md` (new, CRLF, uncommitted): a read-only scope in sections A to G with agent types. Whether the operator's agent has started it is not known to this session.
- `docs/RESULTS-lookup-cel-investigation.md` is on disk (the operator's agent finished it). This session read its one-page answer and recommendations table only. It has not been reviewed.
- The operator then discussed a new Entry specification (see the two rulings above and six unregistered operator statements quoted in the design file below) and asked for a review and design of the new APIs. This session read the entry, space and codec interfaces and wrote `docs/DESIGN-Entry-Records-MarshalledEntry-Space-API-2026-11-20.md` (new, CRLF, uncommitted, DRAFT rev 1, unreviewed): record entries, a `MarshalledEntry` type, a space interface that returns it, eleven open questions for the operator.

**On disk.** `trunk` at 83c4d4c7a. Uncommitted in `JGDMS/docs`: this handover (add.09 to add.11 and the header line), the two scope files and the design draft.

**Next actions.**
1. The operator answers the design draft's section 7; item 1 decides whether six statements become rulings, item 2 whether a row of RL-JGDMS-0135 is replaced by a new ruling.
2. A blind review of the design draft by a different model; the operator has not yet asked for one.
3. The STD-006 freeze belongs to another session (RL-JGDMS-0133); the design draft's section 6 lists what it would add. Nothing has been sent to that session.
4. The Outrigger and Mahalo scope has not been edited to take the design draft as input (its sections A2, C.4, C.5, E.4).
5. Review `docs/RESULTS-lookup-cel-investigation.md` when the operator asks.
6. The next actions of add.09 all stand; none was done.

**Not verified.** Nothing was built or run. The design draft labels each finding; its section 9 lists what was not checked.

**DEVIATIONS.** None.

**Added to add.11 at 00:05 +1000.** RL-JGDMS-0136 and RL-JGDMS-0137 were added, from the operator's answers to questions 1 and 2 of the design draft's section 7. The design draft's section 4.4 note on RL-JGDMS-0135 is now answered by RL-JGDMS-0137; the draft file has not been edited. The review board has not been convened; questions 3 to 11 are unanswered.

**Added to add.11 at 00:08 +1000.** RL-JGDMS-0138, RL-JGDMS-0139 and RL-JGDMS-0140 were added, from the operator's answers to questions 3, 4 and 5 as numbered in this session's reply (the design draft's section 7 items 3, the freeze question of its section 8 item 1, and item 8). Two of the answers leave a point open, recorded in each ruling's note. The design draft has not been edited.

**Added to add.11 at 00:15 +1000.** RL-JGDMS-0141 was added. On the module for RL-JGDMS-0140 the operator said at 00:04 "Yes move Java from release 8 to 16" and at 00:05 "Oh, that's a point, maybe jgmd-lib-dl stay at 8 for old clients?"; neither is registered, the point is open, and board seat 2 was asked to recommend. The design draft gained a section 10 listing RL-JGDMS-0136 to RL-JGDMS-0140. The review board was convened at the operator's request: two blind background agents (Opus, adversarial; Sonnet, compatibility), each to write one file, `docs/REVIEW-entry-records-design-opus-2026-11-21.md` and `docs/REVIEW-entry-records-design-sonnet-2026-11-21.md`. Neither had reported when this was written.

**Added to add.11 at 00:20 +1000.** RL-JGDMS-0142 was added; it answers the module point left open under RL-JGDMS-0140. Board seat 2 was told of it by message.

**Added to add.11 at 00:25 +1000.** RL-JGDMS-0143 was added.

**Added to add.11 at 00:27 +1000.** RL-JGDMS-0144 was added. Open with the operator: whether a service that serves both older and new clients exports twice from one instance or runs as separate instances.

**Added to add.11 at 00:29 +1000.** RL-JGDMS-0145 was added.

**Added to add.11 at 00:35 +1000.** RL-JGDMS-0146 and RL-JGDMS-0147 were added, and a status line was appended to RL-JGDMS-0143. Both board seats were told of RL-JGDMS-0146 by message. The design draft (sections 5.2, 6) and both scope files were written before these and have not been edited to match.

**Added to add.11 at 00:37 +1000.** RL-JGDMS-0148 was added.

**Added to add.11 at 00:40 +1000.** RL-JGDMS-0149 was added. It bears on the lookup design memo (D13, D9) and on the lookup scope and result, none of which has been edited.

**Added to add.11 at 00:42 +1000.** RL-JGDMS-0150 was added.

**Added to add.11 at 00:50 +1000.** At the operator's request ("We need to revise the lookup design memo") this session appended section 17 to `docs/DESIGN-class-free-lookup-and-registrar-split-2026-10-31.md` and added one clause to its status line; sections 0 to 16 are otherwise unedited. Section 17 lists which decisions RL-JGDMS-0132 and RL-JGDMS-0134 to RL-JGDMS-0150 change and six open questions. The memo is uncommitted.

**Added to add.11 at 01:25 +1000.** Both board seats reported. Their files are `docs/REVIEW-entry-records-design-opus-2026-11-21.md` (245 lines) and `docs/REVIEW-entry-records-design-sonnet-2026-11-21.md` (388 lines). The design draft gained section 11 (the board's verdicts, the HIGH findings with the author's replies, the two points the seats disagree on, their advice on the six statements of RL-JGDMS-0136, and what a rev 2 must do) and its status line now says it is not to be adopted as written. No finding was registered as a claim. Seat 1 asks the operator to revisit RL-JGDMS-0138; seat 2 does not. Rev 2 of the draft is not started.

**Added to add.11 at 01:30 +1000.** RL-JGDMS-0151 to RL-JGDMS-0154 were added, from the operator's four numbered answers of 00:44, which this session matched to the lookup memo's section 17.4 items 1 to 4 (the reason is in each ruling's note). The lookup memo gained a section 17.5 saying so. Still open with the operator: the four decisions the board left (null in the record constructor, coexisting versions of one class name, release 21 for the local module; the fourth, no class download for entry classes, is answered by RL-JGDMS-0154), and section 17.4 items 5 and 6.

**Added to add.11 at 01:33 +1000.** RL-JGDMS-0155 was added and a status line was appended to RL-JGDMS-0154. The lookup memo's section 17.5 gained a line. The board's question on class download for entry classes is therefore answered by RL-JGDMS-0155, not RL-JGDMS-0154.

**Added to add.11 at 01:40 +1000.** New file `docs/CANDIDATE-isolated-processor-service-ui-2026-11-21.md` (CRLF, uncommitted): a candidate, not a ruling, recording the operator's idea of a device with an isolated processor for untrusted proxies and service interface code, and the outline he said would work. Nothing was registered for it. Open with the operator: what makes a downloaded entry class trusted under RL-JGDMS-0155 (a local digest, an isolated host, or both), and the three board decisions.

**Added to add.11 at 01:45 +1000.** At the operator's request this session created `docs/SUGGESTIONS-requiring-further-review.md` (new, CRLF, uncommitted), a list of unreviewed ideas, and entered the isolated-processor candidate as its entry 1; the candidate file's status line now points to it.

**Added to add.11 at 01:55 +1000.** RL-JGDMS-0156 to RL-JGDMS-0159 were added and a status line was appended to RL-JGDMS-0140. Still open with the operator: null in the record constructor (RL-JGDMS-0138; he asked for more context on the seats' disagreement, which this session gave in conversation). Rev 2 of the Entry design draft is not started.

**Added to add.11 at 02:00 +1000.** RL-JGDMS-0160 was added and a status line was appended to RL-JGDMS-0138. All four decisions the board left are now answered (RL-JGDMS-0155 and RL-JGDMS-0156, RL-JGDMS-0157, RL-JGDMS-0159, RL-JGDMS-0160). Next action: rev 2 of `docs/DESIGN-Entry-Records-MarshalledEntry-Space-API-2026-11-20.md` against its section 11.6 list and these rulings; not started. Whether the six statements of RL-JGDMS-0136 are now to be registered as rulings, given the board's advice, has not been asked.

## ADDENDUM add.12, AS-OF 2026-11-21 01:05 +1000

Written at the operator's wrap-up request before a compaction. It gathers add.11 and its appended paragraphs into one place; where they differ, this addendum governs.

**Register entries added since add.10.** RL-JGDMS-0134 to RL-JGDMS-0160. Status lines were appended to RL-JGDMS-0138, RL-JGDMS-0140, RL-JGDMS-0143 and RL-JGDMS-0154. No claim was added. No claim in this session's scope was settled: none of the open ones (CL-PROXYBOARD-0031, CL-PROXYBOARD-0053 and the earlier open ones) was checked against code or a build in this period.

**Rulings by subject (IDs only).**
- New Entry specification: RL-JGDMS-0134, RL-JGDMS-0135, RL-JGDMS-0136, RL-JGDMS-0137, RL-JGDMS-0139, RL-JGDMS-0141, RL-JGDMS-0160.
- Entry class loading and trust: RL-JGDMS-0155, RL-JGDMS-0156, RL-JGDMS-0157, RL-JGDMS-0158.
- No live interoperation with older releases, migration, names: RL-JGDMS-0144, RL-JGDMS-0145, RL-JGDMS-0146, RL-JGDMS-0147, RL-JGDMS-0148, RL-JGDMS-0149, RL-JGDMS-0150.
- Local client module, wrappers, discovery: RL-JGDMS-0142, RL-JGDMS-0151, RL-JGDMS-0152, RL-JGDMS-0153, RL-JGDMS-0159.
- Entries whose status line changed in this period: RL-JGDMS-0138, RL-JGDMS-0140, RL-JGDMS-0143, RL-JGDMS-0154.

**Files this session wrote or changed since add.10 (all in `JGDMS/docs`, all uncommitted; `trunk` at 83c4d4c7a).**
- New: `SOW-Outrigger-Mahalo-CEL-Code-Free-Investigation-2026-11-20.md` (scope for the operator's agent, sections A to G).
- New: `DESIGN-Entry-Records-MarshalledEntry-Space-API-2026-11-20.md` (DRAFT rev 1; section 10 lists rulings made after it; section 11 records the board; its status line says it is not to be adopted as written).
- New, written by two background agents of this session: `REVIEW-entry-records-design-opus-2026-11-21.md` and `REVIEW-entry-records-design-sonnet-2026-11-21.md`.
- New: `CANDIDATE-isolated-processor-service-ui-2026-11-21.md` and `SUGGESTIONS-requiring-further-review.md` (the candidate is its entry 1).
- Changed: `DESIGN-class-free-lookup-and-registrar-split-2026-10-31.md` (one clause in the status line; sections 17 and 17.5 appended).
- Changed: this handover (add.09 to add.12 and the header).
- Also uncommitted from before: `SOW-Lookup-CEL-Code-Free-Investigation-2026-11-20.md`. On disk and not written by this session: `RESULTS-lookup-cel-investigation.md`.

**Next actions, in order.**
1. Ask the operator the question left open when he called the wrap-up: whether the six statements of RL-JGDMS-0136 are written into rev 2 as amended by the board for him to confirm, or ruled on first. Both seats advised adopting all six, each with a change (design draft section 11.4).
2. Write rev 2 of the Entry design draft from its section 11.6 list, the two review files and the rulings above. Points already fixed for it: record entries rest on the existing `@SerialEntry` path; a template for a record type is built by a builder; the `Query` record of section 5.1 is withdrawn; `MarshalledEntry` covers record entries first; the server's one-hash-per-class-name check has to change if RL-JGDMS-0157 is read as applying to the space (this session's reading, told to the operator, not confirmed); the new local module is the home of the new interface and the wrappers.
3. Tell the session that holds the STD-006 freeze (RL-JGDMS-0133) what RL-JGDMS-0141 adds. Nothing has been sent.
4. The Outrigger and Mahalo scope, the lookup scope and the lookup result were written before RL-JGDMS-0146 and RL-JGDMS-0149 and have not been edited. Whether the operator's agent has started the Outrigger and Mahalo scope is not known.
5. Review `RESULTS-lookup-cel-investigation.md` when the operator asks; only its one-page answer and recommendations table have been read.
6. Lookup memo section 17.4 items 5 and 6 are unanswered (where the codenamed implementation lives and when it takes Reggie's name; a rev 4 of that memo).
7. Board seat 1's finding H1 is about existing code (five copies of an entry's type in `EntryRep`, one checked). This session did not re-open it and registered no claim for it.
8. The next actions of add.09 all stand; none was done.

**Open with the operator, beyond the above.** Whether RL-JGDMS-0150 covers the services he did not name. What the remote djinn policy of RL-JGDMS-0156 is and where it is held. Three unregistered ideas offered for the suggestions list and not answered: loading an untrusted entry class only on an isolated host; a server-side take-and-write-derived operation; a writer-set must-be-understood mark on a field.

**Not verified.** Nothing was built or run by this session or by the two board agents. Of the board's findings this session re-opened three in code: the per-class-name hash check in `OutriggerServerImpl.typeCheck`, the generator's walk to `Object` at `EntrySchemaGenerator.java:162`, and the record example in `SerialEntry.java:102-127`. Everything else from the board is relayed. The survey counts are the compatibility seat's.

**DEVIATIONS.**
1. The times in add.11's heading and in its "Added to add.11 at ..." paragraphs were estimated and run ahead of the operator's clock by up to an hour (the last of them says 02:00; the operator's message that followed was stamped 00:58). The operator-message times in the register locators were taken from the messages and are right.
2. RL-JGDMS-0151 to RL-JGDMS-0154 were registered from four numbered answers that could have referred to either of two open lists; the reason for the choice is in each note and was told to the operator, who then rephrased the fourth (RL-JGDMS-0155) without objecting to the reading.
3. The design draft's section 2 finding 16 was wrong in substance (found by board seat 1, confirmed by this session); it is corrected in the draft's section 11.3, not in place.

## ADDENDUM add.13, AS-OF 2026-11-21 02:10 +1000

Written after the compaction that followed add.12. Times here are from the operator's message stamps.

**RULINGS ADDED.** RL-JGDMS-0161 (component types and collections), RL-JGDMS-0162 to RL-JGDMS-0167 (the six statements that RL-JGDMS-0136 sent to the board, one ruling each, in the order of the design draft's section 1).

**FILES CHANGED.** `docs/SUGGESTIONS-requiring-further-review.md`: entry 2 added (clients and services in other languages as a constraint on the wire). Not registered.

**NEXT ACTION.** Write rev 2 of `docs/DESIGN-Entry-Records-MarshalledEntry-Space-API-2026-11-20.md` from its section 11.6, the two review files, and RL-JGDMS-0134 to RL-JGDMS-0167. Before stating collections as buildable, read what the entry codec does with a collection-typed field.

**OPEN.** Whether arrays, including `byte[]`, are excluded as record entry components (this session's reading, in the note of RL-JGDMS-0161; told to the operator, not corrected). The other open items of add.12 stand, except the question about RL-JGDMS-0157 and the space, which RL-JGDMS-0165 answers.

**NOT VERIFIED.** Nothing in this addendum was checked against code or a build.

**Added to add.13 at 2026-11-21 02:12 +1000.** RL-JGDMS-0168 added (two kinds of `MarshalledEntry`; staging of the wrapper). Rev 2 of the Entry design draws on RL-JGDMS-0134 to RL-JGDMS-0168.

**Added to add.13 at 2026-11-21 02:35 +1000.** RL-JGDMS-0169 and RL-JGDMS-0170 added. New file `docs/REVIEW-entry-encoding-canonical-matching-sonnet-2026-11-21.md` (a Sonnet agent's read-only report; RELAYED; four cited places re-opened by this session, listed in its status paragraph). Rev 2 of the Entry design draws on RL-JGDMS-0134 to RL-JGDMS-0170 and on that file. To read before rev 2 states the collection wrappers as buildable: whether the schema generator records type arguments for a parameterised class that is not a JDK collection, and whether the `@SerialEntry` path keeps type arguments.

**Added to add.13 at 2026-11-21 02:50 +1000.** Rev 2 of the Entry design written: `docs/DESIGN-Entry-Records-MarshalledEntry-Space-API-rev2-2026-11-21.md` (DRAFT, not reviewed, uncommitted); rev 1's status line now points to it. The NEXT ACTION of add.13 is done. Next: the operator's answers to rev 2's six open questions and his choice between its options W1 and W2, then a blind review by a model other than Fable. Read by this session for rev 2 and VERIFIED there: `SchemaGenerator.toWireType(Type, Class)` and `EntryRepV2Codec.encodeSerialEntry`. Still not told: the session holding the STD-006 freeze.

**Added to add.13 at 2026-11-21 02:52 +1000.** RL-JGDMS-0171 added. Rev 2 of the Entry design revised in place for it (section 3.2 replaced; sections 1, 6.2, 8.2, 10, 11 and 12 adjusted); its status line says so.

**Added to add.13 at 2026-11-21 03:12 +1000.** RL-JGDMS-0172 added. Rev 2 of the Entry design gained sections 3.2.3 (other languages), 3.6 (nested records) and 3.7 (mapping Java classes to schema), and open questions 7 to 10. Row 2 of the suggestions list notes RL-JGDMS-0172. Awaiting the operator: W1 or W2 (rev 2 section 4.1) and the ten open questions of its section 11.

**Added to add.13 at 2026-11-21 03:19 +1000.** RL-JGDMS-0173 added. Rev 2 section 3.7 gained "The nullable mark" and open question 11. Two rulings now bear on the STD-006 freeze session and have not been sent to it: RL-JGDMS-0141 and RL-JGDMS-0173.

**Added to add.13 at 2026-11-21 03:22 +1000.** RL-JGDMS-0174 added; rev 2 sections 1, 3.4 and open question 11 changed for it.

**Added to add.13 at 2026-11-21 03:26 +1000.** RL-JGDMS-0175 added; rev 2 sections 1, 3.7 and open question 11 changed for it.

## ADDENDUM add.14, AS-OF 2026-11-21 03:33 +1000

Wrap-up before a compaction asked for by the operator at 03:31 +1000. It consolidates add.13 and its "Added to add.13" paragraphs. Times are from the operator's message stamps.

**CLAIMS SETTLED.** None. No open claim in this file's scope was checked against code or a build in this period.

**RULINGS ADDED SINCE add.12, BY SUBJECT.**
- Record entry component types and collections: RL-JGDMS-0161, RL-JGDMS-0169, RL-JGDMS-0171.
- The six statements sent to the board by RL-JGDMS-0136: RL-JGDMS-0162 to RL-JGDMS-0167.
- Kinds of `MarshalledEntry` and staging of the wrapper: RL-JGDMS-0168.
- Matching and the canonical check: RL-JGDMS-0170.
- Other languages and the schema: RL-JGDMS-0172.
- The nullable mark: RL-JGDMS-0173, RL-JGDMS-0174, RL-JGDMS-0175.

**FILES, ALL UNCOMMITTED, IN `JGDMS/docs`.**
- NEW `DESIGN-Entry-Records-MarshalledEntry-Space-API-rev2-2026-11-21.md`: DRAFT rev 2, not reviewed, revised in place several times; its status line lists the revisions. Its section 1 maps rulings to subjects, section 11 holds eleven open questions, section 4.1 holds the W1 or W2 choice, section 3.7 holds the token-spelling choice.
- NEW `REVIEW-entry-encoding-canonical-matching-sonnet-2026-11-21.md`: a Sonnet agent's read-only report, RELAYED except the places its status paragraph lists.
- CHANGED `DESIGN-Entry-Records-MarshalledEntry-Space-API-2026-11-20.md`: status line points to rev 2.
- CHANGED `SUGGESTIONS-requiring-further-review.md`: entry 2 added and annotated.
- This handover.

**NEXT ACTION (the operator, 03:31 +1000: "Please write the scope for a coding agent after compact.").** Write a scope for the operator's coding agent. The operator did not say for which work. This session's reading, to be confirmed with him first: the nullable mark, that is RL-JGDMS-0173 and RL-JGDMS-0175 (field definition gains the mark; declaration in the serial form; bytecode check of constructors; update of existing classes), with RL-JGDMS-0174 for record components only if record entries are in the same scope. The other candidate is the whole of rev 2, which is not reviewed and has open choices. A scope follows the form of the earlier ones in `docs` (agent type per section). Remember the coordination rule: only one side writes code at a time, and this session writes scopes, not code.

**OPEN, AWAITING THE OPERATOR.**
- Rev 2: W1 or W2; token spellings; open questions 1 to 10; in question 11, whether nested records follow the never-null default.
- Readings recorded in notes and not confirmed: that the nullable mark is per field in the field definition and so applies to every `@AtomicSerial` schema, and that it is `BOOLEAN DEFAULT TRUE` (note of RL-JGDMS-0173).
- A blind review of rev 2 by a model other than Fable has not been asked for.
- The session holding the STD-006 freeze has been told nothing; RL-JGDMS-0141, RL-JGDMS-0173 and rev 2's request for a record-entry section all bear on it.
- The open items of add.12 stand, except those add.13 says are answered.

**NOT VERIFIED.** Nothing was compiled or run. Code opened by this session in this period, and only at the lines cited in rev 2 as VERIFIED: `SchemaGenerator`, `EntryRepV2Codec`, `AtomicSerialSchemaRecord`, `AtomicSerialFieldDef`, `DerObjectStreamCodec`, `ObjectCodec`, outrigger-dl and reggie-dl `EntryRep`, `SerialEntry`, `EntryWireField`, `Valid`. The count of `Valid.notNull(` covers the main sources of four modules only. The statement that the class-file API is standard from release 24 is from recall.

**DEVIATIONS.**
1. Rev 2 was edited in place after it was written, not appended to; its status line records each revision.
2. RL-JGDMS-0172 registers a stated need, not a decision; RL-JGDMS-0148 is the precedent this session followed.

## ADDENDUM add.15, AS-OF 2026-11-21 04:31 +1000

Author: Claude Fable 5.2, the same session. Not reviewed by a different model.

**COMMIT.** At the operator's request (03:34) the uncommitted files under `docs` were committed to JGDMS `trunk` as `21965190d` (12 files, not pushed), author and committer `Claude <noreply@anthropic.com>` because the VM has no git identity. It includes `docs/RESULTS-lookup-cel-investigation.md`, written by another agent and not reviewed here. The commit was made with `write-tree`, `commit-tree` and `update-ref` after a plain `git commit` timed out.

**BOARD REVIEW OF REV 2.** Asked for by the operator at 03:38 in place of a coding scope ("We aren't ready to write the scope yet"). Two read-only agents, Opus and Sonnet. Reports saved as received: `docs/REVIEW-entry-records-design-rev2-opus-2026-11-21.md`, `docs/REVIEW-entry-records-design-rev2-sonnet-2026-11-21.md`. Both found rev 2 not ready to build from.

**CLAIMS SETTLED.** None.

**RULINGS REGISTERED IN THIS PERIOD, BY SUBJECT (status is in the register).**
- Component types and immutability: RL-JGDMS-0176, RL-JGDMS-0185.
- Cycles: RL-JGDMS-0177.
- Validity and its consequences: RL-JGDMS-0178, RL-JGDMS-0188, RL-JGDMS-0189, RL-JGDMS-0190.
- Java release and where the wrappers live: RL-JGDMS-0179, RL-JGDMS-0182.
- Primitives: RL-JGDMS-0180. Cap on digests: RL-JGDMS-0181.
- Codec as configuration, canonical constraint, one digest: RL-JGDMS-0183, RL-JGDMS-0184, RL-JGDMS-0191.
- Wrapper encoding: RL-JGDMS-0186.
- Nullability of existing classes as separate work: RL-JGDMS-0187.
- Status changes made in this period: RL-JGDMS-0169, RL-JGDMS-0171, RL-JGDMS-0176.

**FILES, ALL UNCOMMITTED, UNDER `JGDMS/JGDMS/docs`.**
- `DESIGN-Entry-Records-MarshalledEntry-Space-API-rev3-2026-11-21.md` (new; DRAFT rev 3, not reviewed).
- `DESIGN-Entry-Records-MarshalledEntry-Space-API-rev2-2026-11-21.md` (status line points to rev 3).
- The two rev 2 review files named above.
- This handover.

**NEXT ACTION.** The operator decides the open questions in rev 3 section 13 (twelve), and whether rev 3 gets a review by a model other than Fable before a coding scope is written. No scope has been written. The operator said the scope is to cover the whole entry design, not only the nullable mark.

**OPEN ITEMS.**
- The session holding the STD-006 freeze has not been told of RL-JGDMS-0173, RL-JGDMS-0187, RL-JGDMS-0191 or of rev 3's request for new sections.
- The suggestions list entry 2 has not been updated for RL-JGDMS-0183 and RL-JGDMS-0184.
- Items carried in add.14 that this period did not touch remain open.

**NOT VERIFIED.** Nothing was compiled or run. Of the two rev 2 reviews this session re-opened only: `ObjectCodec.java` lines 705-716, 735-738 and 2117-2126; `jgdms-der/pom.xml` lines 45 and 66-74; `MarshallingFormat.java` by a search of its declarations; and two rev 2 passages. Every other finding in those reviews is relayed.

**DEVIATIONS.**
1. Two of the operator's replies were taken as assent without the word: the wording of RL-JGDMS-0176 (he then confirmed it at 04:00), and "Yes" to an either/or question for RL-JGDMS-0184. Each ruling's note says so.
2. RL-JGDMS-0183 and RL-JGDMS-0184 were registered in the operator's short words with this session's reading in the note; he confirmed both readings afterwards and the confirmations are appended to those rulings.
3. This session proposed at 04:04 that the old-interface wrapper skip unconvertible entries, then withdrew it as contrary to RL-JGDMS-0167.

## ADDENDUM add.16, AS-OF 2026-11-21 14:52 +1000

Author: Claude Fable 5.2, the same session. Not reviewed by a different model. Written before a compaction the operator asked for at 14:51.

**CLAIMS SETTLED.** None. The CLAIM-HEADER and the scope's INDEX.md were re-read at 14:34 at the operator's instruction; six CENSUS-UNVERIFIED pointers in the scope were treated as pointers only.

**RULINGS REGISTERED SINCE add.15, BY SUBJECT (status is in the register).**
- The new space proxy is a dynamic proxy with no downloaded code; service API: RL-JGDMS-0198, RL-JGDMS-0199.
- The jar for the space's service API: RL-JGDMS-0200.
- Binding interfaces: RL-JGDMS-0201, RL-JGDMS-0202.
- Decorators for the old interfaces, their jar, registration of two proxies, statelessness, trusted download: RL-JGDMS-0203, RL-JGDMS-0204, RL-JGDMS-0205, RL-JGDMS-0206, RL-JGDMS-0207.
- Registered by another session in the same period and used by rev 3: RL-JGDMS-0192, RL-JGDMS-0194, RL-JGDMS-0196.

**FILES, ALL UNCOMMITTED, UNDER `JGDMS/JGDMS/docs`.** As add.15. Rev 3 was revised in place between 12:55 and 14:52 for the rulings above (its status line says so) and gained section 8.2.

**NEXT ACTION.** No coding scope has been written; the operator said at 03:38 that the design is not ready for one. Before a scope:
1. The operator's answers to the open points: rev 3 section 13 (questions 1, 6 and 7 are the ones this session has put to him three times: final `@AtomicSerial` classes as components, records that are not public, what a space permission names) and rev 3 section 8.2 (one service ID or two; how the lookup service's own proxy gets its decorator; a client without the compatibility jar).
2. A check of CL-PROXYBOARD-0007, which bears on RL-JGDMS-0198 and RL-JGDMS-0207.
3. Whether rev 3 gets a review by a model other than Fable.

**OPEN ITEMS.**
- Unconfirmed readings recorded in ruling notes: RL-JGDMS-0199 (one module for all parties, contract only), RL-JGDMS-0204 ("service decorators" read as general to services), RL-JGDMS-0205 and RL-JGDMS-0206 (what "deserializable locally" and "stateless" require).
- A provider interface in `jgdms-platform` that compatibility jars plug into has the operator's tentative assent only (14:46); it is not a ruling.
- The compatibility jar has no name. The interface name and package in `tuple-space-api.jar` are not ruled.
- The session holding the STD-006 freeze has still not been told.
- The suggestions list entry 2 has not been updated for RL-JGDMS-0183, RL-JGDMS-0184, RL-JGDMS-0199.

**NOT VERIFIED.** Nothing was compiled or run. Opened by this session since add.15: `ProxyWireSupport.java` lines 86-166; the class Javadoc of `DerProxySerializer`, `ProxyWireSupport` and `AtomicDerInvocationHandler`; `MarshallingFormat.java` by a search of its declarations; the declaration of `OutriggerServer`; the locations of `JavaSpace`, `JavaSpace05`, `MatchSet`, `ServiceDiscoveryManager`, `LookupDiscovery`, `LookupLocatorDiscovery`, `LookupDiscoveryManager`; the release settings and dependencies in the `jgdms-lib`, `jgdms-lib-dl` and `jgdms-der` build files. Whether JOSS and Atomic JOSS require a binding interface was not checked.

**DEVIATIONS.**
1. Rev 3 was edited in place after it was written, as rev 2 was.
2. This session wrote RL-JGDMS-0192 into rev 3 before seeing that the register had allocated RL-JGDMS-0198 (another session had taken six IDs); corrected in the same minute.
3. "I agree with all three" (11:05) was read as repeating the 04:27 assent already registered; nothing was registered for it.

## ADDENDUM add.17, AS-OF 2026-11-27 23:20 +1000

Author: Claude Fable 5.2, the same session. Not reviewed by a different model. Written at the operator's wrap-up instruction of 2026-11-27 23:10, before a compaction. This session did no work between add.16 and this instruction.

**CLAIMS SETTLED.** None. This session ran no test and opened no source since add.16, so it knows the answer to no further claim in its scope. CL-PROXYBOARD-0007 was not checked. The six pointers named in add.16 remain pointers only.

**RULINGS REGISTERED SINCE add.16.** None by this session. Other sessions have registered JGDMS rulings up to RL-JGDMS-0240 (relay and board subjects); this session has not read them against rev 3.

**FILES.** The five files add.15 and add.16 listed as uncommitted (rev 3, the rev 2 status line, the two rev 2 reviews, this handover as of add.16) are in commit `8d3e31250`, made by the operator on 2026-11-21. This addendum is the only uncommitted change by this session. Seen in `docs` and not this session's: a modified `HANDOVER-relay-case-2026-11-22.md` and an untracked `HANDOVER-limited-privilege-review-2026-11-27.md`.

**NEXT ACTION.** Unchanged from add.16: no coding scope; the three items under NEXT ACTION there come first. One addition: before rev 3 is used, read it against the rulings other sessions registered after RL-JGDMS-0207.

**OPEN ITEMS.** As add.16; none closed.

**NOT VERIFIED.** Nothing was compiled or run. Whether commit `8d3e31250` holds the files exactly as this session left them was not compared; `git status` on `docs` shows no difference for them.

**DEVIATIONS.** None.

## ADDENDUM add.18, AS-OF 2026-11-27 23:45 +1000

Author: Claude Fable 5.2, the same session. Not reviewed by a different model. At 23:21 the operator invited this session to read the other sessions' work; at 23:28 he agreed to its recommendations ("Yes, please update as per your recommendations.").

**READ.** The register entries RL-JGDMS-0192 to RL-JGDMS-0197 and RL-JGDMS-0208 to RL-JGDMS-0240; `HANDOVER-relay-case-2026-11-22.md` (head and addenda 14 to 18); in `relay-isolation/DESIGN-security.md` the header, trust boundaries, R16l, K2 to K7, K16 and open points 16, 17, 40 and 41; `HANDOVER-limited-privilege-review-2026-11-27.md`; in DirtyChai, `Uri.toURL()` and the log of `Uri.java`.

**REV 3.** Read against those rulings: none of RL-JGDMS-0208 to RL-JGDMS-0240 bears on the entry design, and RL-JGDMS-0192, RL-JGDMS-0194 and RL-JGDMS-0196 were already taken in. This closes the addition under NEXT ACTION in add.17.

**REGISTERED.** RL-JGDMS-0241 (new). A note logged on each of RL-JGDMS-0077, RL-JGDMS-0078, RL-JGDMS-0111, RL-JGDMS-0114, RL-JGDMS-0115 and RL-JGDMS-0116 answering open point 17 of DESIGN-security, and a reading note on RL-JGDMS-0211. See `show` for each.

**FILES CHANGED (uncommitted).** `relay-isolation/DESIGN-security.md`: a header paragraph dated 2026-11-27; K3 (a proxy JVM cannot change its address); K5 (the tick count corrected from k + 1 to 2k + 1 for K16); open point 17 closed; open point 40 gains the admission and eviction question. `HANDOVER-limited-privilege-review-2026-11-27.md`: a dated note appended and its header's addendum line. This handover.

**NOT DONE, FOR THE OPERATOR.**
- RL-JGDMS-0037's rule that the board never calls the host and uses no call-back listeners is carried only by K5, a proposal. This session registered nothing for it: the operator has not said he still holds it.
- The admission and eviction rule itself (open point 40) is not written.
- The relay-case session has not been told of these changes to the file it last edited.
- R16c's own text was not edited; K5 now tells it which figure to use.

**NOT VERIFIED.** Nothing was compiled or run. The 2k + 1 figure is arithmetic from the text of K5 and K16, not a measurement. In DirtyChai only `Uri.java` was opened; `computeDigestFromStream` was searched for under `java/security` and `au` in `java.base` only and not found there.

**DEVIATIONS.**
1. Two register commands were run at the same time and one failed to take the lock; it was rerun. Each of the seven status lines and the new entry is in the log once.
2. This session appended a note to another session's handover.

## ADDENDUM add.19, AS-OF 2026-11-27 23:55 +1000

Author: Claude Fable 5.2, the same session. Not reviewed by a different model.

**REGISTERED.** RL-JGDMS-0242, from the operator's message of 23:42 ("Yes, the board never calls the host.  Also the mail service might need some precautions as well."). It answers the first item under NOT DONE in add.18. This session had overlooked that RL-JGDMS-0038 already covers the host exporting no listener.

**FILES CHANGED (uncommitted).** `relay-isolation/DESIGN-security.md`: K7 cites RL-JGDMS-0242 and RL-JGDMS-0038; open point 15 notes the operator's remark on the mail service; the 2026-11-27 header paragraph. This handover.

**OPEN.** Precautions for the event mailbox: this session put proposals to the operator in conversation (who may deliver to a registration, events never unmarshalled on the mailbox or the host, the listener's address replaced by a relay-issued id, quotas, acknowledgements on the tick). None is written into the design or ruled. Other items as add.18.

**NOT VERIFIED.** Nothing was compiled or run. The mailbox proposals were made without reading the Mercury source.

**DEVIATIONS.** None.

Note, 2026-11-27 23:58 +1000: RL-JGDMS-0243 registered from the operator's message of 23:47, replacing this session's mailbox proposal that the mailbox keep the handback; open point 15 of DESIGN-security cites it. Caveat put to the operator: the decoder fault found in the rev 2 board review (the wire-named class is loaded before it is checked against the declared slot, `ObjectCodec.java` 709 and 714) applies to decoding a handback on the host.

Note, 2026-11-28 00:10 +1000: RL-JGDMS-0244 registered from the operator's message of 2026-11-27 23:58; a correction note logged on RL-JGDMS-0243 (inspection, not decoding to objects). Read for it: `DerReader.java` (searched for class loading and reflection, none found) and `ObjectCodec.java` lines 575-640, 688-745, 1840-1900, 2112-2130 and its `loadClass` call sites (709, 737, 2124, 2480, 2508, 2697 on decode; 1422 on encode). Advice given in conversation on how to make the check by name before loading; no scope written, nothing compiled or run.

Note, 2026-11-28 00:20 +1000: the operator pointed out (00:08) that Atomic DER allows inheritance to be refactored to composition, so the wire's schema chain need not match the local superclass chain. This session withdrew its advice to compare the wire chain with the leaf's real superclasses and advised instead that field stores be keyed by class name and matched to the calling class's name, so no chain class is loaded by a wire name. Read for it: `ObjectCodec.java` lines 745-790 and the `storeMap` uses in `DerGetArg.java`. Not read: how a refactored class obtains its former superclass's store. Nothing registered.

Note, 2026-11-28 00:25 +1000: RL-JGDMS-0245 registered from the operator's message of 00:13, if the register allocated that ID (see its log for 2026-11-28). Read for it: `ResolutionContext.java` and a search of `ObjectCodec`, `DerGetArg`, `DerFieldStore` and `SchemaChain` for static caches. Not read: `ClassLoading.loadClass`, the whole of `DerFieldStore`.

## ADDENDUM add.20, AS-OF 2026-11-28 00:35 +1000

Author: Claude Fable 5.2, the same session. Not reviewed by a different model. Written at the operator's wrap-up instruction of 2026-11-28 00:25, before a compaction. It gathers add.18, add.19 and the notes after them; where they differ, this governs.

**CLAIMS SETTLED.** None. No test was run. CL-PROXYBOARD-0007 was not checked; a pointer for whoever checks it: the Javadoc of `admissibleConstructClass` in `ObjectCodec.java` (lines 575-590) speaks of `DerProxySerializer` being admitted to narrowly typed slots. The other entries of the scope without a test result are as they were.

**RULINGS REGISTERED BY THIS SESSION SINCE add.17.** RL-JGDMS-0241 to RL-JGDMS-0246. Notes logged on RL-JGDMS-0077, RL-JGDMS-0078, RL-JGDMS-0111, RL-JGDMS-0114, RL-JGDMS-0115, RL-JGDMS-0116, RL-JGDMS-0211 and (a correction) RL-JGDMS-0243. Subjects, for finding them: the relay's identification of a subprocess (0241), the board and the host (0242), the event handback (0243), class loading in `ObjectCodec` (0244, 0245), whose dynamic code needs a proxy module (0246).

**FILES CHANGED BY THIS SESSION, UNCOMMITTED.** `relay-isolation/DESIGN-security.md` (header paragraph of 2026-11-27, K3, K5, K7, open points 15, 17 and 40); `HANDOVER-limited-privilege-review-2026-11-27.md` (a note appended, header addendum line); this handover (add.17 to add.20). The register's JGDMS log. Nothing is committed; commit only when the operator asks.

**NEXT ACTION.** The operator said at 00:25: "You may investigate further following compaction." What that covers, from the conversation:
1. How many cases still need dynamic code once DER and CEL have taken their share, and which of them are third-party (RL-JGDMS-0246). Start with `docs/RESULTS-lookup-cel-investigation.md`, not yet reviewed by this session.
2. For RL-JGDMS-0244 and RL-JGDMS-0245, read before anything is specified: how a class refactored from inheritance to composition obtains its former superclass's field store; the component-class load at `ObjectCodec.java` line 2697; whether `ClassLoading.loadClass` initialises the class; where the per-class deserialization permission check must move; and, for open slots, whether the name check is a build-time index, a policy permission (DirtyChai's `LoadClassPermission`, not read) or both. The operator has not chosen.
3. Still no coding scope: "We aren't ready to write the scope yet" stands.

**OPEN ITEMS.**
- Event mailbox precautions: this session's proposals 1, 2, 3, 5, 6 and 7 (who may deliver; nothing unmarshalled on the mailbox; nothing service-defined unmarshalled on the host; the listener's address replaced by a relay-issued id; quotas; acknowledgements on the tick) are neither written into the design nor ruled. Proposal 4 is replaced by RL-JGDMS-0243. Whoever walks a handback should cap nesting depth and size; `DerReader` was seen to check lengths only.
- The admission and eviction rule for modules (DESIGN-security open point 40) is not written.
- The relay-case session has not been told that this session edited `DESIGN-security.md`.
- R16c's own text still carries the old tick figure; K5 now says which to use.
- Everything listed open in add.16 and add.17, except that rev 3 has now been read against the register to RL-JGDMS-0240 (add.18).

**NOT VERIFIED.** Nothing was compiled or run in this session since add.16. Every statement about code since then comes from reading the lines named in add.18 and the notes after it. The account of Warres's report given to the operator was from recall.

**DEVIATIONS.**
1. As add.18: two register commands collided on the lock and one was rerun; a note was appended to another session's handover.
2. The second sentence of RL-JGDMS-0246's statement is this session's wording, agreed by the operator, not his own; its note says so.
3. The note of 00:25 above said "if the register allocated that ID" of RL-JGDMS-0245; it did.

## ADDENDUM add.21, AS-OF 2026-11-28 01:15 +1000

Investigation the operator allowed after compaction. Reading only; nothing built or run; no register entry made. Bears on RL-JGDMS-0244, RL-JGDMS-0245, RL-JGDMS-0246.

### ObjectCodec (jgdms-der, paths as in add.20)

1. Loading does not initialise, as far as read. `ClassLoading.loadClass` (jgdms-platform, line 343) hands to the installed provider or `RMIClassLoader.loadClass`; `PreferredClassProvider` (jgdms-pref-class-loader, lines 622 to 722) uses `LoadClass.forName(name, false, defaultLoader)`. NOT READ: `LoadClass.forName`, which provider a proxy JVM installs, the JDK fallback.
2. The per-class `DeSerializationPermission("ATOMIC")` check (`ObjectCodec.java` 213 to 247, called at 330, 768, 2530) is made against the protection domains of the loaded classes, so it cannot run before the load. It already runs before any constructor. It does not need to move; the name check goes in front of the load.
3. DirtyChai `LoadClassPermission` (58 lines) takes no class name (only "ALLOW") and gates which code source may define classes. It cannot say which names a remote end may cause to be loaded. For open slots the build-time index is the only option that needs no new permission class.
4. Array component class (`ObjectCodec.java` 2697): the wire's component class name is loaded and used to allocate the array. The declared component type is passed in from `DerGetArg.java` 325 to 334 but is used only to admit each element, not to check the wire component name before or after loading. `DerObjectStreamCodec.java` 1641 to 1642 passes no declared type.
5. Composition. Inferred from `AtomicSerial.GetArg.callerClass` (jgdms-platform `AtomicSerial.java` near 395): a class refactored from `extends S` to holding an `S` gets S's values by handing the same `GetArg` to `new S(arg)`; the stack walk finds S as the nearest frame in the registered set and returns S's store. This works today only because S was loaded from the wire chain at 737 or 2508. Name-keyed stores keep it working. NOT RUN; no test for this pattern was looked for.
6. Conflict with STD-006. The draft v0.13 section "Class absent locally, fields present on the wire" (near line 3368) says the decoder reads the absent class's SEQUENCE and no error occurs. `decodeHierarchy` calls `loadClass` for every chain record (737, 2508), which throws "cannot load class" for a name absent locally. By reading only, the constructing path fails where the standard says it must not. Name-keyed stores would remove the conflict. For the STD-006 owner.
7. `callerClass` returns the sole registered class without looking at the stack when only one class is registered (`serial.size() == 1`). A helper handed the `GetArg` then reads that class's store whatever the helper is. Under name-keyed stores this shortcut needs the same identity test as the other path.

### Dynamic code that remains

From `docs/RESULTS-lookup-cel-investigation.md` (another session's result at commit 83c4d4c7a; sections 2, A3, C4, E.8 read; RELAYED, not re-opened in source):
- Every filter and template its survey found is expressible in CEL with no language addition.
- Left outside CEL on the lookup path: smart proxies with state, shared logic or backend calls; `ServiceItemFilter` code that reads the service object (the four qa TestFilters); `MarshalledObject` values such as `UIDescriptor.factory`; legacy Reggie and JOSS-only clients.
- Thirty client-path class-load or unmarshal sites are tabled in its A3.

Download modules, counted by `find` (Java files under src/main/java; classes not opened): jgdms-lib-dl 117, reggie-dl 31, outrigger-dl 26, mercury-dl 15, norm-dl 12, fiddler-dl 10, phoenix-dl 9, mahalo-dl 8, policy-service-dl 5, verdict-registry-dl 1; jfr-telemetry-dl, hello-world-dl and bytecode-analysis-engine-dl 0.

NOT DONE: a class-by-class sort of those modules into data (DER), predicate (CEL), reflective proxy over a resident interface, and code that must run on the client. Until that is done the number of remaining cases is not known.

## ADDENDUM add.22, AS-OF 2026-11-28 01:50 +1000

Sort of the download modules, asked for by the operator ("Word ;)" to my proposal of Sonnet agents). Three Sonnet sub-agents, read-only, one row per source file; their tables are in this session's transcript, not in a file. Bears on RL-JGDMS-0246. No register entry made.

Bins: D data; X exception; M interface or other declaration only; P pure forwarder or proxy-trust plumbing; L work done in the client; U uncertain.

| Module | D | X | M | P | L | U | files |
|---|---|---|---|---|---|---|---|
| jgdms-lib-dl | 31 | 6 | 48 | 4 | 27 | 1 | 117 |
| reggie-dl | 2 | 0 | 2 | 11 | 16 | 0 | 31 |
| outrigger-dl | 4 | 0 | 6 | 6 | 10 | 0 | 26 |
| mercury-dl | 3 | 1 | 3 | 4 | 4 | 0 | 15 |
| norm-dl | 2 | 3 | 2 | 4 | 1 | 0 | 12 |
| fiddler-dl | 2 | 0 | 1 | 4 | 3 | 0 | 10 |
| phoenix-dl | 0 | 1 | 6 | 1 | 1 | 0 | 9 |
| mahalo-dl | 1 | 2 | 2 | 3 | 0 | 0 | 8 |
| policy-service-dl | 1 | 0 | 1 | 2 | 1 | 0 | 5 |
| verdict-registry-dl | 0 | 0 | 0 | 1 | 0 | 0 | 1 |
| total | 46 | 13 | 71 | 40 | 63 | 1 | 234 |

All rows RELAYED. The second agent's first totals did not add up; the figures above are its corrected row counts. Agents did not read comment text. A leaf class binned P can inherit L behaviour from its parent (for example ConstrainableSpaceProxy2 from SpaceProxy2).

CHECKED by me (line opened, text matches the agent's claim): every class-load, unmarshal and thread site the agents cited in AbstractSmartProxy (480, 506), EntryBeans (49, 55, 80, 85), UIDescriptor (216 to 219), BasicRenewalFailureEvent (183, 218), Bootstrap (80 to 85, 108), MarshalledWrapper (205, 239, 275), CodebaseProvider (53, 58, 69, 74); reggie EntryClass 277, EntryRep 289, 301, 340, Item 268, 381, 395, 421, RegistrarProxy 252, 316, 352, 384, ServiceType 290, 298; outrigger EntryRep 324, 847, 1345 to 1351, IteratorProxy 116, MatchSetProxy 99, SpaceProxy2 1196; mercury RemoteEventData 114; norm SetProxy 170; fiddler FiddlerRegistration 817 to 818; phoenix AID 130, 132; PolicyUpdateListener 297, 401, 425. I did not re-read whole files, and did not re-open the L rows that load nothing.

My grouping of the 63 L rows (from the agents' notes, not a second read):
1. Entry and service-type machinery that loads classes from a codebase or unmarshals field values: reggie EntryClass, EntryClassBase, EntryRep, Item, Matches, RegistrarEvent, ConstrainableRegistrarEvent, RegistrarProxy, ServiceType, ServiceTypeBase; outrigger EntryRep and what reaches it (IteratorProxy, MatchSetProxy, OutriggerAvailabilityEvent, SpaceProxy2).
2. A service handing the client an object that belongs to someone else, unmarshalled in the client: norm SetProxy.getLeases and BasicRenewalFailureEvent (other services' leases), fiddler FiddlerRegistration.getRegistrars (registrars, with integrity fixed to false at 818), mercury RemoteEventData (other services' events), phoenix AID.activate, UIDescriptor.getUIFactory (integrity fixed to false at 219, context loader swapped).
3. Client behaviour with no class loading: SpaceProxy2's retry and poll loops, the paging iterators, the batching lease maps, PolicyUpdateListener (exports a listener at 297 and starts a thread at 401; compare RL-JGDMS-0242 and RL-JGDMS-0038).
4. Local library code and helpers: lease arithmetic and maps, landlord utilities, constraint translation, LookupAttributes, AbstractEntry, the provider-loaded facades (LeaseRenewalManager, ServiceDiscoveryManager, JoinManager, which is the one U row), the ProxyUtil classes.
5. Name-derived loading outside the assigned loader: EntryBeans; AbstractSmartProxy.createFor (name derived from a local interface, initialise true).

jfr-telemetry-dl, hello-world-dl and bytecode-analysis-engine-dl hold no source, no pom, only target/ with a Backend interface and a Proxy with a nested Constrainable proxy each (agent report; I confirmed hello-world-dl holds only target/). `JiniService` appears in hello-world-api HelloService.java. Whether those proxies are generated from that annotation, and whether the directories are stale build output, NOT CHECKED.

NOT DONE: deciding, class by class, which P rows a reflective proxy over a resident interface replaces and which L rows the entry redesign (rev 3) and the class-free lookup already remove. That is design and is not written.

## ADDENDUM add.23, AS-OF 2026-11-28 02:20 +1000

The operator asked for a scope: "Can you write a scope to address all issues with ObjectCodec and investigate design options for reducing code downloads by replacing them with CEL where possible?"

- Written, uncommitted: `docs/SOW-ObjectCodec-Class-Admission-and-Code-Download-Reduction-2026-11-28.md`. Part A is the decoder change (acts on RL-JGDMS-0244 and RL-JGDMS-0245) with four operator decisions D1 to D4 that gate its coding steps. Part B is a read-only investigation (RL-JGDMS-0246). Not reviewed by anyone.
- The add.22 tables are now on disk for two of the three sub-agents, written by the sub-agents themselves at my request: `docs/RESULTS-dl-module-class-sort-2026-11-28-part1-lib-dl-1-59.md` and `-part3-services.md`. Part 1 was retyped by its agent, not machine-compared with its first report.
- Part 2 (jgdms-lib-dl sorted files 60 to 117) is NOT on disk: the sub-agent's write was refused by the permission system. I did not write it in its place. Its table is in this session's transcript only. The scope says what to do if the file is missing.
- DEVIATION: add.22 said the tables were in the transcript only; two are now files. I gave sub-agents one permitted write each after telling them the task was read-only.

Note, 2026-11-28 02:35 +1000 (after add.23). The operator said "I give permission to the sub agent to write the file."; the sub-agent's retry was refused again by the permission system. The operator then said "Yes, write the table please". I wrote `docs/RESULTS-dl-module-class-sort-2026-11-28-part2-lib-dl-60-117.md` myself from the sub-agent's report (58 rows; bins counted by script match its corrected totals). The scope's section 2 now names all three part files. This supersedes add.23's statement that part 2 is not on disk.

## ADDENDUM add.24, AS-OF 2026-11-28 03:00 +1000

Wrap-up before compaction, at the operator's instruction. Covers everything since add.20; add.21 to add.23 and the note after add.23 hold the detail.

### CLAIMS SETTLED
None. No test was run this period. CL-PROXYBOARD-0007 is still unchecked.

### RULINGS REGISTERED THIS PERIOD
RL-JGDMS-0247, RL-JGDMS-0248, RL-JGDMS-0249, from the operator's "Your recommended decisions are accepted." (decisions D1, D2, D3 of the scope below). Each statement is my wording; each note says so and gives his words. D4 is not a ruling.

### FILES (all uncommitted)
- New: `docs/SOW-ObjectCodec-Class-Admission-and-Code-Download-Reduction-2026-11-28.md` (CRLF). Section A.1 records D1 to D3 as decided and the index details, with three points marked open (generator, merged jars, OSGi).
- New: `docs/RESULTS-dl-module-class-sort-2026-11-28-part1-lib-dl-1-59.md`, `-part2-lib-dl-60-117.md`, `-part3-services.md` (LF). Parts 1 and 3 written by their sub-agents; part 2 by me with the operator's permission.
- Changed: this handover; the register's JGDMS ruling log.
- Still uncommitted from before: `relay-isolation/DESIGN-security.md`, `HANDOVER-limited-privilege-review-2026-11-27.md`.

### IN FLIGHT: the board review of the scope
The operator said: "You can establish a board using the board skill to review the scope.  Have Opus and Sonnet review, if you think a difficult item warrants it, a Fable agent as well." The skill is `JGDMS/.claude/skills/jgdms-board-review/SKILL.md` (guidance: `docs/JGDMS-Board-Reviewer-Guidance.md`).
- Dispatched 2026-11-28, read-only, both running when this was written: an Opus seat (adversarial security plus DER and module boundary; attacks the A3 gate and A4 stores, checks the order of checks, the tests, and Part B's trust moves) and a Sonnet seat (verifies every anchor in the scope, checks STD-006, STD-008, STD-011 and the earlier CEL scopes against it, looks for existing tests that already cover T1 to T7, checks the class-sort totals).
- No Fable seat dispatched. I would add one only if the two seats disagree on a hard item, or for the name-level form of the `@Serializer` and `Resolve` rule in A3.
- Their reports are NOT in. Nothing in the scope has been reviewed yet.

### NEXT ACTION
1. Read the two board reports when they arrive. Re-open every HIGH and MEDIUM finding in source before accepting it. Revise the scope. Report to the operator what was accepted, rejected and why.
2. The operator then decides D4 and dispatches Part A and Part B. This session writes no code.

### OPEN ITEMS
- The scope is unreviewed until item 1 is done.
- The index's resource name, line format, generator, merged-jar and OSGi handling (not ruled; see the note on RL-JGDMS-0247).
- The STD-006 owner has not been told of the absent-class conflict (add.21 item 6) or of RL-JGDMS-0248.
- Mailbox proposals 1, 2, 3, 5, 6, 7 (add.19) wait on the operator. BasicRenewalFailureEvent and mercury RemoteEventData (add.22 group 2) bear on them.
- PolicyUpdateListener exports a listener and starts a thread; compare RL-JGDMS-0242 and RL-JGDMS-0038. Not raised as a defect anywhere yet.
- Everything listed open in add.20.

### NOT VERIFIED
- The class-sort rows, beyond the cited load, unmarshal and thread lines I opened (add.22).
- That the constructing decode fails on a locally absent chain class: by reading, not run.
- Whether any installed provider initialises a class on load.
- Decode-side load sites outside ObjectCodec (scope table A.2 row O7).

### DEVIATIONS
- Sub-agents were told read-only, then given one permitted write each; one write was refused twice by the permission system and I wrote that file after the operator said "Yes, write the table please".
- The scope was dispatched to the board before the ruling IDs existed, so it cites "the handover" for them instead of the IDs.
- A register log line count rose by more than my own entries during this period; other sessions were writing. I did not look at their entries.

## ADDENDUM add.25, AS-OF 2026-11-28 03:30 +1000

- CL-PROXYBOARD-0007: the operator asked for a scope for a coding agent and allowed the reading. Scope written: `SOW-CL-PROXYBOARD-0007-Proxy-Carrier-Decode-Test-2026-11-28.md` (new, uncommitted, tests only, not dispatched). No status change recorded; no test was run.
- Reading by this session at commit 45f719aa7f342f69ba224c0820ff49e839c87db9 (nothing run): `ObjectCodec.java` 594 to 609 and 2363 to 2367; `DerProxySerializer.java` 149 to 185 and 260 to 266; `PreferredProxyCodebaseProvider.java` 1928 to 1969 and 2040 to 2060; `ServerIdentityPin.java` 100 to 135. The order read is: `ServerIdentityPin.probe` (invokes `authenticateServer` on a platform JERI handler), then `getClassAnnotation()`, then the isolation check. NOT CHECKED: whether `authenticateServer` connects; which handler classes decode into the `InvocationHandler` slot; which permission checks run before `readResolve`.
- The scope for RL-JGDMS-0247 to RL-JGDMS-0249 now names those IDs in its A.1.
- IN FLIGHT, unchanged: the two board seats on the ObjectCodec scope have not reported.
- NEXT ACTION: operator dispatches the CL-PROXYBOARD-0007 test scope; this session records the result in the register when the report is in.

## ADDENDUM add.26, AS-OF 2026-11-28 04:30 +1000

- Both board seats reported on `SOW-ObjectCodec-Class-Admission-and-Code-Download-Reduction-2026-11-28.md` (Opus: adversarial security; Sonnet: rule fidelity and anchors). Neither ran a test. No Fable seat was used; the two did not disagree.
- This session re-opened the lines behind every HIGH finding at commit 45f719aa7f342f69ba224c0820ff49e839c87db9 and found them as reported: `AtomicSerial.java` 691 to 697 and 395 to 400; `DerGetArg.java` 296 to 304, 350 to 356, 450 to 460; `DerMarshalInputStream.java` 396 to 404; `DerObjectStreamCodec.java` 2110 to 2117, 1724 to 1731, 1766 to 1772; `ObjectCodec.java` 2304 to 2310, 735 to 740; `DerFieldStore.java` 408 to 419, 326; `WireTypes.java` 126 to 130, 505 to 514; `ResolutionContext.java` 73; `DerReplacer.java` 124 to 162; `MarshalDelegateProcessor.java` 50 to 80; `ClassEvolutionTest.java` 170 to 200; `Std006DataIndependenceClassNamingFieldsTest.java` 236 to 249; `HelloService.java` 41 to 47. Test names in the scope's A.2a were confirmed by grep. Nothing was run.
- The scope is now revision 2: new defects O10 to O18, an existing-test list (A.2a), tests T1 to T13, a review record (A.6), corrections in section 1 and Part B. What was taken, taken as an option, not taken and not re-opened is in its A.6.
- OPEN FOR THE OPERATOR, none ruled: D5 to D10 in the scope's A.1 (the `Object` slot residual; whose index admits a name; proxy interface names; `Class` and enum field values; changing existing tests; composition against STD-006 section 3.9). Steps A3 to A7 are blocked on them. D4 is still open.
- Corrections to earlier addenda: add.21 item 5 said no composition test was looked for; `ClassEvolutionTest` line 174 is one. add.22's "no existing annotation processor" is wrong; `MarshalDelegateProcessor` exists. The scope's first text called `DerReplacer`'s list a precedent for merging; it reads one resource.
- NOT VERIFIED: the items marked RELAYED in the scope's A.2 and A.6; whether a jar signature covers a `META-INF` resource; the `RMIClassLoader` fallback.

## ADDENDUM add.27, AS-OF 2026-11-28 09:40 +1000

Wrap-up before compaction. This addendum supersedes the NEXT ACTION lines of add.24 to add.26.

- CLAIMS SETTLED: none. No test was run by this session. CL-PROXYBOARD-0007 was re-read (add.25) and has a test scope; its status is unchanged.
- RULINGS: none new since add.24. RL-JGDMS-0247 is under question (below) but stands until the operator rules.
- OPERATOR'S POSITION, 2026-11-28 09:19, his words, NOT registered as a ruling: "there is a tension, we have DeserializationPermission that checks whether a class's ProtectionDomain says whether it's trustworthy to perform deserialization, and this is checked by SCAP.  We have LoadClassPermission to ensure only trusted and identified jar's can be loaded.  But we don't want to break encapsulation and turn deserialization into an administrative task either." And at 09:23: "I think this requires further investigation by a coding agent, before finalizing decisions".
- THIS SESSION'S VIEW, not a ruling: the declared type of the reading slot is the non-administrative answer to "which trusted class in this slot"; if a load never runs code and is already gated by LoadClassPermission, the index of RL-JGDMS-0247 adds little and could become optional. D7's index section of interfaces is withdrawn as a recommendation. The question put to the operator and not answered: is the harm in RL-JGDMS-0244 the load itself, or code of a wire-named class running before the check.
- WRITTEN, new, uncommitted, not dispatched: `SOW-ObjectCodec-Phase1-Decode-Class-Loading-Investigation-2026-11-28.md` (tests and reading only; five questions Q1 to Q5; tests P1 to P3, E1, E2 and the parent's T-tests).
- UNCOMMITTED FILES of this session in `docs`: the phase 1 scope; `SOW-ObjectCodec-Class-Admission-and-Code-Download-Reduction-2026-11-28.md` (revision 2); `SOW-CL-PROXYBOARD-0007-Proxy-Carrier-Decode-Test-2026-11-28.md`; the three `RESULTS-dl-module-class-sort-2026-11-28-*` files; this handover; and from earlier `relay-isolation/DESIGN-security.md` and `HANDOVER-limited-privilege-review-2026-11-27.md`. Register log: RL-JGDMS-0247 to RL-JGDMS-0249.
- IN FLIGHT: nothing. Both board seats have finished.
- NEXT ACTION: the operator dispatches the phase 1 scope (and, if he wishes, the CL-PROXYBOARD-0007 test scope to the same agent) and relays the results. This session reviews them, re-opening findings in source, then puts D5 to D10 and the index question to the operator with the evidence. No code from this session.
- OPEN FOR THE OPERATOR: D4 to D10 in the parent scope; the RL-JGDMS-0244 question above; Part B dispatch; and everything listed open in add.24.
- NOT VERIFIED: everything in the parent scope marked RELAYED; whether any provider initialises on load; whether a wire name can cause a fetch; whether LoadClassPermission is checked on the decode load path.
- DEVIATIONS: the lint counts rose by one log line between add.24 and add.25 without a write by this session; another session wrote to the register. Not investigated.
