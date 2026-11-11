claude-opus-5-5

# Review: round-4 policy-promotion fixes (commit ae004e42f), 2026-10-31/11-01

Reviewer claude-opus-5-5. Producer claude-sonnet-5-5 (the `Co-Authored-By` trailer of ae004e42f, and line 1 of the round-4 investigation), so this is a cross-model review. Skills loaded: handover-composition, production-code. E:\ (TRAINING_DOCS_INDEX) and DirtyChai were not read, per the brief. No Python was used. Labels: VERIFIED = I ran it or read the line; PLAUSIBLE = read, not run; UNDETERMINED = needs data or the operator.
Scratch dir `C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\opus-r4\` (`OR` below): scripts `real.sh`, `promote.sh`, `b2.sh`, `r4-attack.sh`, `r3-attack*.sh`, `mutants.sh`, `mm-copy.sh`, `mbmg-copy.sh`, each with its `.out`. The CLI ran from `OR\classes`, a snapshot of `target/classes` (compiled 23:53, source 23:52). Every source file is identical to ae004e42f; the later commits 2a223d76 and 8100b400 change only docs. Abbreviations: PPG/LP = `tools/policy-condenser/.../ProxyPolicyGenerator.java` / `ListPromotion.java`.

## INCIDENT: read this first (VERIFIED)
A second session was editing this repository while I worked: its PolicyLint work (untracked `PolicyLint.java`/`PolicyLintTest.java`, `qa/build.xml` at 00:13:32, `.claude/skills/jgdms-polp-audit-policy-gen/SKILL.md` at 00:15:20, and PPG). My `mutants.sh` backed up PPG at 00:14:41, which already held that session's edits, then copied files over PPG six times (three mutants, three restores) until 00:16:31. `cmp` against the backup was identical.
The other session then wrote PPG at 00:20:17 with my MM mutant in it (`PPG.listSource`: `line.contains(OLD_HEADER_PREFIX)`). It must have read the file during the MM window.
At about 00:23:20 I reverted that one line to `line.startsWith(OLD_HEADER_PREFIX)` with a single Edit. Afterwards `grep` finds no mutant text (MB, MG, MM). The remaining diff from HEAD (+62 -8) is that session's parseGrants work.
Consequences:
(1) Any PPG edit the other session made between 00:14:41 and 00:16:31 was overwritten. I cannot recover it.
(2) Anything it compiled or tested from 00:20:17 to 00:23:20 ran against MM. MM survives all 95 tests, so its tests could not show this.
(3) My live-tree MM run is invalid: BUILD FAILURE, Tests run: 0, "Unable to create test class ListPromotionTest", most likely the other session's concurrent build.
After 00:16:31 I ran mutants only on a `git archive ae004e42f` copy (`OR\copy`).
`md5sum -c OR\bak\pre.md5`: 55 of 57 OK. The 2 FAILED (PPG and qa/build.xml) are that session's changes. I never wrote qa/build.xml.
**Action for the other session: re-read PPG, re-apply any lost edit and re-run its tests.**

## Verdict
R1-R9 are fixed as the producer claims, and every round-3 attack is still refused or harmless. Through the production CLI over 190 real-policy runs I found no partial output, staging leftovers or false rc. Six findings remain. The two that matter:
- the `--clean` invariant "never replaces a hand-written list" breaks on a case-variant directory (F2);
- the refused sets miss two admission-gate classes, and every one-step escalation permission is promotable with the CI gate green (F1).

## Findings (most severe first)
**F1 (MEDIUM) Escalation-grade entries pass plan, gate and apply. Two admission gates are missing from GATE_PERMISSION_CLASSES. VERIFIED, run (`OR\r4-attack.out` A1).**
Null fixture: `--out` holds only phoenix-dl, the target is NEW FILE, and one line is appended per case. No append: PROMOTABLE, check rc 0. `AllPermission`, `( java.security.AllPermission)` and a tab variant: REFUSED, check rc 1, so the gate discriminates.
These 13 entries are each PROMOTABLE with check rc 0 and apply rc 0: RuntimePermission `setSecurityManager`, `createClassLoader`; ReflectPermission `suppressAccessChecks`; SecurityPermission `createAccessControlContext`, `setPolicy`; SerializablePermission `enableSubclassImplementation`; AuthPermission `doAsPrivileged`, `modifyPrincipals`; FilePermission `<<ALL FILES>>` `execute`; `net.jini.loader.pref.INCONCLUSIVEPermit "*"`, `BootstrapPermission "loadCodebase"`; `org.apache.river.api.security.PolicyPermission "REMOTE"`; `au.zeus.jdk.authorization.guards.NativeInvocationPermission`.
The CI gate's rejection rate is 0 of these 13. On "the list is a CAP": it is bounded by the grantor's GrantPermission (`VerdictPermissionMapper.boundedCeiling`; `Security.grant` javadoc: "checkPermission ... with a GrantPermission"). But `BasicProxyPreparer.grant` uses the jar's list as the grant set whenever no permissions are configured (`perms = ((AdvisoryDynamicPermissions) loader).getPermissions(); ... Security.grant(proxy.getClass(), perms)`), and `VerifyingProxyPreparer` does the same. Under a grantor that holds AllPermission, as the qa harness does, the list therefore is the grant. Recommendations: see Item 4.

**F2 (MEDIUM-LOW) `--clean` overwrites a hand-written list in a case-variant directory and prints `KEPT`. VERIFIED, run (`OR\b2.out` c11).**
Run: `out\Mahalo-DL-4.0.0\...\PERMISSIONS.LIST` = `# my own list`, policy artefact `mahalo-dl-4.0.0`, `--clean`. Result: rc 0, note `KEPT Mahalo-DL-4.0.0: ... --clean does not delete it`. The file now starts with the licence header, and `grep -c 'my own list'` = 0.
Cause: `PPG.planned` compares `plan.artefact.equals(artefact)`, which is case-sensitive. So the guard `if (clean && !handWritten.isEmpty())` never sees the list, and `moveInto` (REPLACE_EXISTING) writes through the case-insensitive path. The collision check two lines earlier already keys on `toLowerCase(Locale.ROOT)`.
This falsifies `docs/ProxyPolicyGenerator.md` :130 at ae004e42f ("never replaced or deleted, with or without `--clean`"; another session is now editing that file). Without `--clean` the run is refused, as it should be.
Side effects, cosmetic: a case-variant old-header list is noted `REMOVED` when it was replaced, and a same-policy one is noted `STALE`.
Fix (describe only): compare case-insensitively, or by canonical path, in `planned`.

**F3 (LOW) MalformedInput and FileNotFound still print stack traces (R8 residual). VERIFIED, run.**
`PPG.run` catches only `InvalidPathException`. A Latin-1 byte in a list under `--out` (b2 c10), in a checked-in target (A3: plan and check rc 1, 17 stack lines) or in a generated list (A4) throws `MalformedInputException` from `Files.readAllLines(..., UTF8)`. The loader itself decodes with replacement (`PreferredClassLoader`, `new InputStreamReader(in, "UTF-8")`), so such a list is valid to the loader but crashes the CI gate.
A missing policy argument throws `FileNotFoundException` (A5). It also fired 16 times in 180 real runs, because the run on the emptied base policy deletes its own `.grantperm` before that file's turn as an input. Each of the 4 `.grantperm` files run on its own gives rc 0.
Every case fails closed: rc 1, nothing written. None of the 8 checked-in module lists has a non-ASCII byte (`tr -d '\000-\177' | wc -c` = 0).

**F4 (LOW) `--clean` in the proxy path deletes a list from another policy without a note; a test pins the silence. VERIFIED, run (b2 c5, c5b).**
`PPG.removeLists` notes REMOVED only when `replaced == null`, that is, only on the zero-proxy path. `ProxyPolicyGeneratorTest.testCleanKeepsAListThisToolDidNotWrite` asserts `assertEquals(r.notes().toString(), 1, r.notes().size())` right after `alpha-dl` (from a.policy) was deleted. That snapshots behaviour instead of stating an invariant.

**F5 (LOW) The fix report's CLI evidence for R3 "gate rc 1" does not discriminate. VERIFIED.**
In the attack2 n11 fixture the gate fails in every case, including the benign `setSecurityManager` one, on `check FAIL mahalo-dl-4.0.0: REMOVED 1 (setIO)` (`OR\w2\n11c.out`). The unit test `testAListHoldingAnExcludedClassIsNotPromotable` does discriminate (target GET_CL, generated GET_CL plus the bad entry), and so does my A1 null fixture.

**F6 (LOW, duplication) The META set is defined twice:** `VerdictPermissionMapper.META_PERMISSION_CLASSES` and `PPG.META_PERMISSION_CLASSES`. Both hold the same 3 names. Extend one and make it the single definition, or F1's additions will drift.

## Item 1: R1 and the three operator questions
Operator ruling 8100b400 (committed 00:03, during this review) accepts all three as-is. My view agrees with each, and I add reasons and one test.
- **(a) Accept the refusal; no hash-qualified names.**
  - The two groups differ (`defaultsecuretest.policy.old` :904-953, VERIFIED). The portless `httpmd://${HOST}:${reggie-dl.jar}` holds 5 live entries (AuthenticationPermission x2, MARSHALL, getSubject, accessClassInPackage). The `:9080/` grants hold 1 (accessClassInPackage). Picking either one narrows or widens reggie-dl; a human must choose.
  - The portless form has a jar name where the port goes, so the grant probably never matches at runtime (PLAUSIBLE).
  - Hash names would only move the refusal to promotion. Mapping both to `reggie-dl` gives `ambiguous target` (refused in r3-attack n5 and n13, VERIFIED). A hex name with letters gives "no -dl module" (PLAUSIBLE: not implemented, so not run).
  - Under `--strict` the `:9080/` group lacks proxy shape, so the policy generates (rc 0, 3 proxies, 2 lists): one silent way out. Fix the policy only at recapture.
- **(b) Agree.**
  - Each of the 10 committed pre-provenance lists has exactly 1 commit (`git log -- <file> | wc -l`), so none was hand-edited, and `--clean` deletes only git-recoverable files.
  - Measured cost: re-auditing `defaulttest.policy.old` into the committed `defaulttest.policy.proxy-permissions` is REFUSED (`jgdms-lib-dl-4.0.0-SNAPSHOT` is never replaced: the new names have no version). With `--clean` it goes 1 → 19 lists, with a REMOVED note.
  - Limit: the marker counts on any line, but only at column 0. A later-line marker counts as tool-written (c7); an indented, case-changed or BOM-prefixed line-1 marker counts as hand-written (c7b, c6, c8). All of these err toward keeping a file except c7.
- **(c) Agree (refuse).**
  - No real input reaches this case: of 45 inputs only `defaultsecuretest.policy.old` collides, and both of its groups are writable.
  - Allowing it would hide from the reviewer that a second codebase of that jar name existed.
  - Mutant MG (skip groups that write no list) SURVIVED all 95 tests, so the ruling is untested. Add a test that pins it.

## Item 2: real policies through the production CLI (`OR\real.out`, `OR\promote.out`)
**Population.** `qa\harness\policy` holds 44 regular files matching `*.policy*`: 33 `*.policy`, 6 `.old`, 1 `.new`, 4 `.grantperm`. The brief said 33. Adding `jinitest.policy` gives 45 inputs. Each ran in 4 variants (plain, `--strict`, `--fail-on-held`, `--clean`) on a fresh copy of the directory, with the default `--out` = 180 runs. 10 more runs re-audited into the committed list directories.
**Results:** no PARTIAL output (out-dir and `.grantperm` md5 unchanged on every rc 1), no leftover staging directory, no rc 0 printed with REFUSED. Stack traces: 16 of 180, all F3 FileNotFound. rc 1 with REFUSED: 3 (`defaultsecuretest.policy.old` plain, fail-on-held, clean). List counts match round 2 (securesharedvm 2, sharedvm 1, defaulttest 19, jinitest 4).
The emptied policies delete their own committed `.grantperm` even without `--clean`; this predates round 4 (5fd08a18 `if (ceilingFile.exists()) ceilingFile.delete();`).
**Promotion** of 5 generated `--out` sets (jinitest 4, defaulttest.old 19, securesharedvm.old 2, sharedvm.old 1, strict securetest.old 2) over a copy of the 13 `-dl` modules (31 files; its 8 lists are md5-equal to the tree):
plan rc 0 every time, no stack traces; check fails exactly where entries are REMOVED; apply without the allow flags is refused and writes nothing. Apply with `--allow-removals` wrote 6 targets: read back, each target's live entries equal its generated list; a re-plan shows +0 -0; a second apply on the stale plan is refused.
Real data: phoenix-dl from securesharedvm.old requests `FilePermission "<<ALL FILES>>" "read"`, and the plan's `+` line was the only barrier.

## Item 3: B2, R2 and R9 (`OR\b2.out`, 22 cases x plain/--clean)
- Without `--clean`, a hand-written list is refused, never touched. With `--clean` it is refused if it would be replaced and KEPT otherwise. Exception: c11 (F2).
- Old-header lists: replaced with a REPLACED note; with `--clean`, deleted with a REMOVED note.
- Both headers: treated as a provenance list (STALE or refused without `--clean`; deleted silently with it, F4).
- Variants: a CRLF old header counts as tool-written. A lower-case or upper-case header, an indented marker, or a BOM before a line-1 marker counts as hand-written.
- A Latin-1 byte gives a stack trace with the file intact (F3).
- Zero-proxy `--clean` (R9): deletes old-header lists and KEPTs hand-written ones (c12). On the real tree it deletes the committed 4 (nonactvm) and 3 (securephoenix) lists, with notes.

## Item 4: the META and GATE sets (recommendation)
Real-data census: 42 live entries in the 28 generated lists, plus 25 entries in the 8 checked-in lists. None falls in the refuse set proposed below, so refusing costs nothing today. It also means the corpus never exercises that set.
- **Refuse: add to GATE.** `net.jini.loader.pref.INCONCLUSIVEPermit` and `net.jini.loader.pref.BootstrapPermission`. Their javadoc makes them admission and tolerance gates, which is exactly what GATE_REASON describes.
- **Refuse: add to META.** `org.apache.river.api.security.PolicyPermission` (updates a remote policy service).
- **Refuse by default as HELD, by class and name.** The generator writes the entry as `# HELD (escalation)`, `--fail-on-held` refuses it, promotion needs `--allow-held`, and LP refuses an edited list that holds it live. Names:
  - RuntimePermission `setSecurityManager`, `createClassLoader`, `loadLibrary.*`;
  - ReflectPermission `suppressAccessChecks`;
  - SecurityPermission `createAccessControlContext`, `setPolicy`, `setProperty.*`;
  - FilePermission `<<ALL FILES>>` with `execute`;
  - NativeInvocationPermission;
  - AuthPermission `doAsPrivileged`, `modifyPrincipals`, `modifyPrivateCredentials`.
  Each is a one-step escape from the SecurityManager whatever else is granted.
- **Flag only:** a `!` beside the plan's `+` line, listed by the check without failing it. Covers FilePermission `<<ALL FILES>>` read/write (2 of 42 real entries), AuthPermission `getSubject` (2 of 42, used by JERI) and `doAs`, SerializablePermission, other ReflectPermission names, RuntimePermission `accessDeclaredMembers`/`setContextClassLoader`/`setFactory`/`accessClassInPackage.sun.*`, and SocketPermission `*`.

## Item 5: three new mutants (`OR\copy-*.log`; null on the copy: 95 run, 0 failures, 1 skipped, the qa/build.xml test, absent from the copy)
Predicted before running: all three survive. Result: all three SURVIVED, 95 run and 0 failures each, on the ae004e42f copy.
- **MB:** `PPG.entryType` no longer skips whitespace after `(`. No test has whitespace inside the parenthesis. On the real code A1 shows `( java.security.AllPermission)` refused, so the parity with `AdvisoryPermissionParser.parse` holds but no test defends it. Add that test.
- **MG:** the collision check skips groups with no list (ruling 3 untested).
- **MM:** the old-header marker matches anywhere on a line instead of at its start (marker position unpinned).

## Item 6: round-3 attacks re-run (`OR\r3-attack.out`, `OR\r3-attack2.out`; copies, round-4 classes)
p1-p7 and n1-n17 are refused or harmless. Changes since round 3: n5 and n13 (two artefacts, one target) refused; n11 all 5 META/GATE cases refused; n12 printed 0 raw ESC and 0 U+202E; n14 no "NOT restored"; n15 refused in both modes; n17 BOM refused as a format character; n8 malformed root gives REFUSED, not a stack trace.
Unchanged: ` mahalo-dl-4.0.0 ` is trimmed and WROTE; n11 `setSecurityManager` WROTE (F1). n2's "tree-unchanged=NO" is a script artefact: n1's legitimate write changed the base.

## Not verified
Linux, symlinks (junctions only), the real ant targets, cross-volume staging, the TOCTOU window, a `deleteTree` race. Whether the other session lost edits (see INCIDENT): UNDETERMINED.
