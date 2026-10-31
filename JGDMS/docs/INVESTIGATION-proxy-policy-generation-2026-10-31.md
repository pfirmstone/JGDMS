claude-sonnet-5-5

# Investigation: ProxyPolicyGenerator output quality, 2026-10-31. STOPPED ON A CLASSIFIER REFUSAL. INCOMPLETE: code written, NOT compiled, NOT run, NO test evidence.

## 1. The refusal (the stop is a finding)
- At 19:22 (+1000, clock `date`) the Bash tool call that was to take BUILD_OWNER slot 1 was refused by the auto-mode safety classifier. Reason given: `[Modify Shared Resources]`.
- What the call did: build a "SLOT 1 TAKEN" entry, prepend it to the shared prepend-only file `E:\Claude\BUILD_OWNER.md` (tail-equals-prior check, then copy over, then read-back). It was my seat's rule 4 step before any mvn/java.
- Per seat rule 1 I did not retry, rephrase or work around it. I ran no further tool call except writing this file and the hand-back.
- State of that call: denied as a whole, so I believe nothing in it ran (the BUILD_OWNER.md prepend and the scratch files `bo_take.txt` / `bo_new.md`). UNDETERMINED: I did not re-read BUILD_OWNER.md after the refusal. Last read before it (19:22:04): top entry "SLOT 2 RELEASED by Film 18:09:43", 1640440 B, md5 80176f12, both slots free, java/javaw 0 / 0.
- Consequence: I hold no BUILD_OWNER slot, so no mvn/java was run by this seat. There is no baseline control, no test run, no mutant run, no run on the real policies. Every acceptance item that needs a build is NOT DONE.
- Needs the operator: either take the slot for this seat (or grant a Bash permission rule for writing `E:\Claude\BUILD_OWNER.md`), or tell the dispatcher another seat will build and run. I cannot judge which.

## 2. Exact state of the tree (MEASURED: `git diff --stat -- JGDMS/tools` at ~19:21, JGDMS clone at d35bc3af0)
- Modified, uncommitted, uncompiled, under `JGDMS/tools/policy-condenser/src`:
  - `main/.../ProxyPolicyGenerator.java` (+615 lines): features 1-5 (below), CRLF kept (lf=crlf=1488).
  - `main/.../PolicyUpdatePostProcessor.java` (+16): `-Dpolicy.update.proxy.clean=true` passes `clean(true)`; WARNING-logs `Result.notes()`.
  - `test/.../ProxyPolicyGeneratorTest.java` (+449): 11 new tests, one fixture changed (section 4).
  - `test/.../PolicyUpdatePostProcessorTest.java` (+32): 1 new test (unrecorded list refused; regenerated with the property).
- Not touched: `JGDMS/pom.xml` (the pre-existing uncommitted diff is not mine), `check-permissions-list-in-jar.sh`, any -dl `src/main/resources`, DirtyChai.
- NOT yet done: `JGDMS/docs/ProxyPolicyGenerator.md` update; mutant runs; run on the real policies; counts.
- Backups of the originals: scratchpad `orig/` (ProxyPolicyGenerator.java md5 1f5dee8d, ProxyPolicyGeneratorTest.java a56e69ce, PolicyUpdatePostProcessor.java 0dee9854, PolicyUpdatePostProcessorTest.java 0400db7d, ProxyPolicyGenerator.md 4560989d). The scratchpad is `C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad`.
- Seal: `PREREG_proxy_policy_generation_2026-10-31.txt` in that scratchpad, md5 3d60a2e8 (not committed: the brief forbids commits; no external witness named).

## 3. What the code does (design as written; UNDETERMINED whether it compiles or behaves as intended)
1. Gate: `LoadClassPermission` (`au.zeus.jdk.authorization.guards` and `net.jini.loader`), `java.net.URLPermission`, `net.jini.loader.DownloadPermission` are dropped from the list, reported on `Result.notes()` (CLI stderr) and as `# DROPPED (reason): entry` comments. They are left in the `.grantperm` ceiling: the brief limits the change to the list; operator decision.
2. Host/run-specific: after `applyReplacements`, an entry whose name has `${`, a `file:` prefix, a Windows drive or UNC path, a `/` path on a FilePermission, or `jdk.proxyN.$ProxyM` is written as `# HELD (reason): entry`; `--strict` throws `GenerationException` before anything is written.
3. Provenance: header lines `# Source policy:`, `# Proxy codebase digest:` (if the grant has one), `# Generator: ... list format 2`, `# Generated: <date>`, `# Entries:`. Lists under `--out` with another or no `# Source policy:` line are refused unless `--clean`; same-source lists the run does not regenerate give a `STALE` note. Key is the policy file NAME only.
4. Validation: every written list is read back and each entry line parsed with `AdvisoryPermissionParser.parse` (loader skip rules); a failure deletes the lists written in that run and throws.
5. `--diff-against <dir>`: scans `<dir>` for `META-INF/PERMISSIONS.LIST` (module = dir above `src/main/resources`; skips `target`, `build`, `.git`, `*.proxy-permissions`), matches a generated `x-4.0.0-SNAPSHOT` to `x`, compares parsed entries, prints `+`/`-` per artefact to stdout, only reads `<dir>`, and refuses a plan whose output file is one of the compared lists.
- CLI is now `run(args, out, err)` returning 0 / 1 (refused) / 2 (usage); `main` exits non-zero on it.

## 4. Defects and decisions I want a reviewer to see (all UNVERIFIED by a build)
- CONFLICT with the acceptance line "existing 9 tests still pass": `testEscapingRoundTrips` uses the absolute Windows path `C:\work\-` and expects it LIVE. Under rule 2 that entry is held back, so the unchanged test would fail by design. I changed its fixture to the relative `work\data\-` (still tests backslash escaping). I intended to show the original test failing against the new code first; that needs a build, so UNDETERMINED. Dispatcher/operator should confirm the rule beats the test.
- An all-held or all-dropped group still gets a list with zero live entries. READ: `AdvisoryDynamicPermissions.DEFAULT_PERMISSIONS` is `new Permission[0]` (AdvisoryDynamicPermissions.java:86) and `PreferredClassLoader.getPermissions` (:1561-1566) returns it for a missing list, so a comment-only list requests the same as none. Promoting one over a reviewed list would still remove that list's entries.
- Old lists carry no `# Source policy:` line, so the 10 committed lists under `qa/harness/policy/*.proxy-permissions` and the 4 under `qa/src/.../jinitest.policy.proxy-permissions` count as "different" and `policy-update` will log a refusal for them until run with `-Dpolicy.update.proxy.clean=true`.
- Name-only provenance cannot see the same policy name re-audited on another host (the mixed `defaultnonactvm` case in the Opus review). The held-back host paths catch some of it.
- `AdvisoryPermissionParser.parse` returns an `UnresolvedPermission` rather than throwing for a mistyped or unavailable class, so validation does not flag a typo'd class name. It also initialises the named class (`Class.forName(..., true, loader)`).
- Not covered by any rule: `RuntimePermission "loadLibrary./usr/..."`, SocketPermission on literal hosts, `newProxyInPackage.jdk.proxyN`, and the other deny-list items of the Opus review F1.3.
- Release 8: the module compiles with `<release>8</release>` (tools/policy-condenser/pom.xml), so the new code avoids newer APIs; unchecked until a compile.

## 5. Figures I did measure (read-only; population and rule inline)
- Population: the 14 tracked generated lists (`git ls-files 'qa/*proxy-permissions*'` = 14: 10 under `qa/harness/policy`, 4 under `qa/src/org/apache/river/test/resources/jinitest.policy.proxy-permissions`). Rule: list has at least one live line of the class. Read from a `cat` of all 14.
  - LoadClassPermission: 7 of 14 (nonactvm jgdms-lib-dl and mahalo-dl; securephoenix jgdms-lib-dl, mahalo-dl, phoenix-dl; securetest phoenix-dl; test jgdms-lib-dl). Agrees with the Opus figure.
  - Absolute host path in a FilePermission: 4 of 14 (nonactvm outrigger-dl and reggie-dl; securetest jgdms-lib-dl and phoenix-dl). Agrees with the Opus figure. Forms seen: `/home/user/...`, `file:/home/user/...`, `file:/C:/Users/...`.
  - `jdk.proxy6.$Proxy11` in 1 (test jgdms-lib-dl); `createVirtualThread` in 1 (securephoenix jgdms-lib-dl).
- Checked-in lists: 8 `*-dl` modules (`git ls-files '*src/main/resources/META-INF/PERMISSIONS.LIST'`). I read mahalo-dl's only (3 entries, no LoadClassPermission). The Opus review says 0 of 8 carry one; I did not re-read the other 7: UNDETERMINED here.
- Population for the planned real run: of the 33 `qa/harness/policy/*.policy`, 16 have 0 `grant` blocks (emptied for cross-platform polpAudit regeneration), so they yield nothing. The proxy grants survive only in `*.policy.old` / `*.policy.new` (31 `dl|httpmd` codebase lines in defaulttest.policy.old alone) and `qa/src/.../jinitest.policy`. The generated lists in git cannot be regenerated from the current `.policy` files.
- Read: `PolicyUtils.instantiatePermission` (PolicyUtils.java:454-487) does no `${}` expansion, so a placeholder in a list reaches the permission verbatim. VERIFIED for that call chain only; downstream consumers of the Permission objects (`Security.grant`) were not read.
- Read: `PreferredProxyCodebaseProvider` grants `DownloadPermission`, `URLPermission` and `net.jini.loader.LoadClassPermission` (unresolved) itself after verifying the codebase (lines 1540-1552, 2055-2075). That is the basis for rule 1; whether a dynamic grant is consulted at define time is not read.

## 6. What a next seat does
1. Get a BUILD_OWNER slot (see 1). 2. Compile; fix errors. 3. Run the original unmodified `ProxyPolicyGeneratorTest` (scratchpad `orig/`) against the new main code to show the section-4 conflict. 4. Mutant-test each of features 1-5 (new tests must FAIL, restore with cmp). 5. Control run: compile HEAD's generator into scratch, run it and the new one on COPIES of `*.policy.old`, `*.policy.new`, `jinitest.policy` (never in place: the generator writes `<policy>.grantperm` beside its input) and report dropped/held counts against the control. 6. Update `docs/ProxyPolicyGenerator.md`. 7. Review by Opus or Fable, not Sonnet.

## 7. Not verified
Everything that needs a compiler or a run: that the code compiles; that any of the 12 new tests (11 generator, 1 post-processor) pass or fail as predicted; that the 9+6 existing tests still pass; the mutant results; the counts on real policies; the `Package.getImplementationVersion()` path; the PolicyUpdatePostProcessor test.
