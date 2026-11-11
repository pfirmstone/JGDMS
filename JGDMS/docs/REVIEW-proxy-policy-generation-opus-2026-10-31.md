claude-opus-5-5

# Review: ProxyPolicyGenerator list-safety change (uncommitted), 2026-10-31

Reviewer claude-opus-5-5. Producer claude-sonnet-5-5 (line 1 of `docs/INVESTIGATION-proxy-policy-generation-2026-10-31.md`), so this is a cross-model review. Skills loaded: handover-composition, production-code, code-comments (rag-file does not apply). I did not read `E:\Claude\TRAINING_DOCS_INDEX.md` because the brief forbids touching E:\. The earlier Opus review measured 0 JGDMS rows in it (RELAYED).
Tree: the JGDMS clone at HEAD **af64aea0c**, not the d35bc3af0 the producer names. The one commit between them does not touch policy-condenser (`git diff --stat d35bc3af0 HEAD -- JGDMS/tools/policy-condenser` is empty). The scratchpad `orig/` backups equal HEAD apart from CR (`git show HEAD:<f> | cmp - <(tr -d '\r' < orig/<f>)` gives 0 for all 4 files).
Labels: VERIFIED means read or measured, with the command or file:line given. PLAUSIBLE means it follows from code I read but I did not run it. UNDETERMINED means it needs data or the operator. Evidence is in `C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\rev` (`logs\*.log`, plus the scripts `mutant.sh`, `runB.sh`, `runC.sh`, `runD.sh` and `tallyC.sh`). Python was not used.

## Verdict
All five features are wired, and 10 of 12 mutants are killed by a test. On the real lists the change does what F1 asked for: it drops LoadClassPermission and holds host paths (see C). But three things make it unsafe to rely on as a gate:
- `--strict` cannot fire on real data.
- 5 of the 14 lists come out with zero live entries, and nothing flags them.
- On the production `policy-update` path, the existing 14 lists are never re-checked.
The findings follow, most severe first.

## A. Mutant checks
Population: one single-line mutation per row, in the working-tree source. Each row ran the brief's mvn command, then I restored the file with `cp` from `rev/bak` and checked it with `cmp`. All 12 restores were byte-identical, with md5 d5c9e96b for the generator and 78b3cd12 for the post-processor.
Scoring rule: a mutant is KILLED if at least one test fails. Each run compiled ("Tests run: 20" and "Tests run: 7" both printed) and took 14-16 s.
| feature | mutation (module-qualified) | failing tests |
|---|---|---|
| 1 gate | `ProxyPolicyGenerator.plan`: `if (false && GATE_PERMISSION_CLASSES.contains(p.type))` | testGatePermissionsAreNeverEmitted, testCliStrictRefusal... (2) |
| 2 host/run | `plan`: `String hostSpecific = null;` | testHostAndRun..., testStrictRefuses..., testMappedHostValue..., testCli... (4) |
| 2 strict only | `generate`: `if (false && strict)` | testStrictRefuses..., testCli... (2) |
| 3 refusal | `generate`: `if (false && !foreign.isEmpty())` | testDifferentSourcePolicyRefusedUnlessClean, PolicyUpdatePostProcessorTest.testProxyOutputHolding... (2) |
| 3 header | `writePermissionsList`: drop `pw.println(SOURCE_POLICY_PREFIX + source);` | testHeaderRecordsProvenance, testDifferentSource..., testProxyOutputHolding... (3) |
| 4 call | `generate`: drop `validateList(listFile);` | testUnparseableLineFailsGenerationAndLeavesNoList (1) |
| 4 cleanup | `generate`: drop `for (File f : written) f.delete();` | testUnparseableLine... (1) |
| 5 diff | `generate`: drop `if (diffAgainst != null) diff = diff(plans, diffAgainst);` | testDiffAgainst..., testDiffRefuses..., testCli... (3) |
| 5 read-only | `diff`: `if (false && targets.contains(...))` | testDiffRefusesToOverwriteACheckedInList (1) |
| CLI notes | `run`: drop `for (String note : r.notes()) err.println(note);` | testCli... (1) |
| **notes log** | `PolicyUpdatePostProcessor.generateProxyArtifacts`: drop the `LOG.log(Level.WARNING, "{0}: {1}", ...)` loop | **NONE, SURVIVES**: the only channel that tells a `policy-update` user about DROPPED, HELD or STALE has no test |
| (pre-existing) | `ProxyPolicyGenerator.policyEscape`: stop escaping `\` | **NONE, SURVIVES**: the ceiling half of testEscapingRoundTrips only asserts `gps.size()==1` |
Restore proof: `logs\RESTORE_verify.log` shows "Compiling 3 source files with javac [debug release 8]" and 20 + 7 tests green. `javap -v` gives major version 52. **Release 8: VERIFIED**, because the code was compiled under `--release 8`.

## B. Fixture change in testEscapingRoundTrips: legitimate, not a weakening. VERIFIED
- **Measured.** I ran the ORIGINAL HEAD test files against the NEW main code (`logs\B_origtests.log`). 8 of 9 generator tests and 6 of 6 post-processor tests pass. Only testEscapingRoundTrips fails, with `expected:<1> but was:<0>` at the `perms.size()` assert, because `C:\work\-` is now HELD. So rule 2 is the only cause of the conflict.
- **What the old fixture covered and the new one keeps:** `\` escaping through `getEncoded` and `parse` on a live line. `work\data\-` has 2 backslashes, including `\-`, against 2 in `C:\work\-`. It also keeps the ceiling-parses check.
- **What is lost:** only "an absolute Windows path is emitted live". That is the behaviour rule 2 removes on purpose. The escaped text of an absolute Windows path is still asserted in a HELD comment (`assertHeld(held, "C:\\\\Users", ...)` in testHostAndRunSpecificEntriesAreHeldBack).
- **A weakness the change did not introduce:** the ceiling half "escaping is correct for the policy grammar" is not load-bearing. The policyEscape mutant survives with the new fixture (measured). It plausibly survives with the old one too, since the assert is the same.

## C. Real policies, old (HEAD, compiled to `rev\oldclasses`) against new (working tree), on copies only
Inputs were untouched: `md5sum -c` passed on the 8 originals.
- **Control fidelity.** old(jinitest.policy copy) equals the 4 committed jinitest lists (`diff -r --strip-trailing-cr` is empty).
- **Population A: 8 files** (the 7 `*.policy.old` / `*.policy.new`, plus jinitest.policy). Each generator wrote 32 lists from 54 old entries.
  - New: 52 live, **0 dropped, 2 held**. The two held entries are AuthenticationPermission with `${reggie}` and `${test}`, in defaultsecuretest.policy.old/_qa1-share-dl.
  - One list ends with 0 live entries. Conservation (old = live + DROPPED + HELD) holds in 32 of 32.
  - **Rule 1 and the path rules are INERT on A:** the control has 0 gate-class entries out of 54 (grep). The prereg's own anti-vacuous clause applies.
- **Population B: the 14 committed lists** (25 live entries), turned into fixture policies by `runB.sh`. Each entry `(T ...)` becomes `permission T ...;` with the quoted strings kept verbatim. This builds an input only.
  - The fixture is faithful: the old generator reproduces 14 of 14 lists' entries exactly.
  - New: **11 live, 7 dropped, 7 held**.
    - Dropped: 7 `au.zeus...LoadClassPermission "ALLOW"`.
    - Held: 4 `file:` URL, 2 absolute path, 1 `jdk.proxy6.$Proxy11`.
  - Conservation holds in 14 of 14.
  - Diff line, old against new at {defaultnonactvm/mahalo-dl, defaultnonactvm/outrigger-dl, jinitest/jsk-dl}: {1→0 live (1 dropped), 3→1 live (2 held), 1→1}.
- **Cross-check against the earlier review.**
  - LoadClassPermission: 7 of 14 by grep census, and 7 of 14 lists the new generator drops from. Agree.
  - Absolute or `file:` host paths: 4 of 14 by grep, and 4 of 14 lists with such a HELD entry. Agree.
  - `createVirtualThread` stays live, as the producer stated.

## D. Findings, most severe first
**D-1 (HIGH) `--strict` cannot refuse on real data. VERIFIED, MEASURED.**
- One field drives two things:
  - classification, in `ProxyPolicyGenerator.isProxy`: `if (strict) return hasProxyShape(g);` (:507);
  - the new refusal, in `generate`: `if (strict) {` (:435).
- `--strict` census over 13 policies (5 B fixtures and 8 A files):
  - 12 print "no proxy-targeted grants" and exit 0;
  - 1 (defaultsecuretest.policy.old) is REFUSED, because its grant happens to carry AuthenticationPermission.
- None of the B fixtures counts as a proxy under `--strict`. So the 7 held entries in the committed lists can never trigger the refusal. A user who runs `--strict` to fail loudly gets rc 0, "nothing written", and no list.
- Under the null the gate reads the same: "--strict writes no PERMISSIONS.LIST" (prereg P2) PASSes on real data for the wrong reason.
- Both strict tests add `AccessPermission "someMethod"` to their fixture, so they cannot see this.
- The docs (`docs/ProxyPolicyGenerator.md:68`) and the constructor javadoc (:244) describe only the classification meaning.

**D-2 (HIGH) Zero-live lists are written and counted as manifests, with no list-level signal. VERIFIED, MEASURED.**
- On B, 5 of 14 lists end with 0 live entries: both mahalo-dl lists, defaultsecuretest jgdms-lib-dl and phoenix-dl, and defaulttest jgdms-lib-dl.
- `describe()` prints only `manifests=N, dropped, held`, and the header prints `# Entries: 0 live`.
- An empty list requests the same as no list (`AdvisoryDynamicPermissions.java:86` `new Permission [0]`; `PreferredClassLoader.java:1598-1606`). So under `-wins` (earlier review F1) it replaces mahalo-dl's reviewed list. `--diff-against` shows exactly that, `diff mahalo-dl-4.0.0-SNAPSHOT vs services/mahalo/mahalo-dl/...: +0 -3 =0`, but only if a human runs it.
- `--strict` does not refuse a list that only has dropped entries, and D-1 stops it from even classifying one.

**D-3 (MEDIUM) The production path never re-checks the existing 14 lists. VERIFIED, MEASURED.**
- The four harness `.policy` files behind the 10 harness lists (defaultnonactvm, defaultsecurephoenix, defaultsecuretest, defaulttest) have 0 grant blocks each (grep -c).
- `generate` returns early (`if (proxies.isEmpty())`, :421-425) before `existingLists` (:446). So no STALE note fires, with or without `--clean`. A copy run left the 4 committed defaultnonactvm lists byte-unchanged with rc 0 and printed "nothing written" (`logs\D.log`, D3).
- The 4 jinitest lists have no provenance line, so they are REFUSED. `PolicyUpdatePostProcessor.generateProxyArtifacts` catches it (`catch (Throwable t)`, :350) and logs WARNING; the run succeeds. That logging is the untested path (row "notes log" in A).
- Net effect: the 7 LoadClassPermission and 6 host-path entries stay committed, and they are what `-wins` ships.

**D-4 (MEDIUM) The provenance key mixes runs. VERIFIED, MEASURED.** `source = policyFile.getName()` (:429).
- I ran two different policies with the same file name (host A with 4 grants, host B with 2) into one `--out`.
- Run 2 exits rc 0 with only 2 `STALE` notes. The directory now holds 2 lists from each host, all headed `# Source policy: defaultnonactvm.policy`. That is the earlier review's mixed-run case, unchanged.

**D-5 (MEDIUM-LOW) Header injection reaches a live line, and validation passes it. VERIFIED, MEASURED.**
- A digest clause `"SHA-256=ab\n(java.security.AllPermission)"` gives the live line `(java.security.AllPermission)`.
  - It is written by `writePermissionsList`: `pw.println("# Proxy codebase digest: " + grp.template.digest);` (:689).
  - Result: rc 0, dropped=0, held=0. The meta and gate exclusions are bypassed.
- The same thing through the codebase line (:687) is PRE-EXISTING: HEAD also emits the live AllPermission line (`rev\d\d7`).
- The new digest line adds a second vector. `validateList` checks only that a line parses, not that the live set equals `plan.emit`.
- Exploiting this needs write access to the policy file.

**D-6 (LOW-MEDIUM) A validation failure is not "nothing left written". VERIFIED, MEASURED.**
- Run 1 wrote a-dl and b-dl. In run 2, b-dl's digest ends `\nnot a permission`, and the run is REFUSED (rc 1, "line 19").
- Afterwards, **0 lists remain**. That includes a-dl, which had validated in that same run, and the previous good lists are gone. The ceiling is rewritten (md5 de03fe81 → 2da6428c, now holding the refused run's `exitVM`), because `writeCeilingFragment` (:469) runs before the try block.
- So the comment at :427-428 ("a refusal leaves the output directory as it was") and the `GenerationException` javadoc (:1406, "nothing was left written") are false for this path. Per code-comments this is a WHAT that has gone wrong. Fix the code, not the comment.

**D-7 (LOW) The validation gate's rejection rate on real input is 0. VERIFIED (read and measured).**
- `getEncoded` escapes `"`, `\`, `\r` and `\n` (AdvisoryPermissionParser.java:218-239), and `parse` accepts any such output (:39-150). Every type is a tokenizer word.
- So on entry lines feature 4 can fire only through the test seam or a D-6 header injection. It fired 0 times across 46 lists (32 in A, 14 in B).
- Earlier finding F5's real site is still unchecked: hand-written or checked-in lists, and the jar copy step. `--diff-against` only marks `[does not parse]` and does not fail.

**D-8 (LOW) `--clean` deletes lists the tool did not write. VERIFIED (read).**
- `removeLists` (:771-781) deletes every `<child>/META-INF/PERMISSIONS.LIST` under `--out`, including hand-written ones with no generator marker. HEAD never deleted lists.
- Outside `--out`:
  - Only `<policy>.grantperm` is written or deleted (unchanged from HEAD), plus the D-6 leftover.
  - `--diff-against` is read-only: the md5s of the 22 tracked PERMISSIONS.LIST files and `git status` were identical before and after a run against `JGDMS/JGDMS`.

**D-9 (minor) Smaller points.**
- `GENERATED_PROXY_CLASS` (:205-206) misses `com.sun.proxy.$ProxyN` (JDK 8-15 naming); 0 of the 14 lists contain it today. VERIFIED.
- An `Error` from `Class.forName(..., true, ...)` inside `validateList` is not caught, so the written lists are left behind. PLAUSIBLE.
- `docs/ProxyPolicyGenerator.md` is not updated (`git diff --stat` is empty). VERIFIED.

## Advice (describe only, a human writes it)
1. Split `--strict` into two flags. Make the refusal independent of classification, and refuse zero-live lists in that mode.
2. Validate that the read-back live set equals `plan.emit`. Reject control characters in the header fields.
3. Key provenance on the policy's content hash, plus the host and audit commit. Run the STALE check before the 0-proxy early return.
4. Write the ceiling, and delete earlier lists, only after every list has validated.
5. Restrict `--clean` to files that carry the "Generated by ProxyPolicyGenerator" marker.
6. Test the post-processor's WARNING notes. Add a check mode that applies `plan()` to an existing list, so the 14 committed lists can be reviewed.

UNDETERMINED: operator rulings are needed on (a) whether the gate classes belong in the `.grantperm` ceiling and (b) whether held `${...}` principals should be expanded instead.
