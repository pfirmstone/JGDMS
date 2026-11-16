claude-opus-5-5

# Review, round 2: `PermissionSets.filePath` normaliser patch (JGDMS, jgdms-platform)

**Reviewer:** claude-opus-5-5. **Producer:** Sonnet 5.5 (per the brief), so this is a cross-model review.
**Date:** 2026-11-07. **Mode:** read-only. No mvn was run, and nothing under E:\ was touched.
**Skills loaded:** handover-composition, production-code, code-comments. rag-file was not loaded: no scan data is involved.
**Not consulted:** `E:\Claude\TRAINING_DOCS_INDEX.md`. The brief forbids E:\.

## 0. What was reviewed

**Bytes under review.** In worktree `JGDMS-normaliser-20261106/JGDMS` (base 0eb9f0cc7):
- `PermissionSets.java` md5 `e8aadfdd…`;
- `PermissionSetsTest.java` md5 `4f52b04d…`;
- `PermissionSetsFilePermissionDifferentialTest.java` md5 `30eb2930…`.

**Checks that the bytes are the right ones:**
- All three equal the producer's `final-md5-r2.txt`.
- They were the same at the end of the review: `git status` and `md5sum` at 01:05.
- `normaliser.patch` matches the worktree. The production hunks hash `d2c12944…` in both the patch and `git diff`. The new test file hashes `30eb2930…` both inside the patch and on disk.
- Nothing was written under `JGDMS-review-20261106/probe-pathdiff`: `find -newer <start marker>` returns 0 files, recursively.

**JDK for every result:** `DirtyChai/build/windows-x86_64-server-release/images/jdk` (27-internal-adhoc.peter.DirtyChai), with `jdk.io.permissionsUseCanonicalPath=false`. The probe prints both facts.

**Scratch folder.** `R = …/scratchpad/opus-r2` holds every input, output, source and class file named below.

**Catalogues, compiled by me** (`javac --release 8`, against the m2 jgdms-platform 4.0.0-SNAPSHOT jar, md5 `91a06334…`):
- `cls-base`: `git show 0eb9f0cc7:…PermissionSets.java`, md5 `27e93c09…`. It is identical to round 1's src-base.
- `cls-r1`: the round-1 file, md5 `537b4ce5…`.
- `cls-r2`: the worktree file.

**Instruments:**
- The round-1 probe `opus-normrev/probe/Rv.java`, unchanged. It calls the production `PermissionSets.danger`/`flagReason` and the JDK's `FilePermission.implies`, plus `Files.isSameFile`.
- PathDiff. I copied its class files, ran them with the producer's cwd, and wrote output only to `R/pd-*`.
- Both test classes, compiled `--release 8` the way the producer's class files were (major version 52).
  - `PolicyPermission` was compiled into `R/cls-extra`. DirtyChai's `java.base` exports the package `org.apache.river.api.security` but lacks that class.
- `R/probe/RawOpen.java` (new). It opens a name for read with `FileInputStream` and closes it unread. "Access is denied" means Win32 resolved the name to a device. The control, a nonexistent device, gives "cannot find".

## 1. Findings, most severe first

### R2-1. Gap, mechanism CONFIRMED, impact PLAUSIBLE: a `*` under a device root is unrated, and the unit test pins two such names at 0

The device rule in `au.net.zeus.jgdms.api.policy.PermissionSets.fileReach`:
```java
        if (f.device && "-".equals(f.wildcard)) {
```
The pin in `PermissionSetsTest.testARecursiveNameInTheDeviceNamespaceIsHigh`:
```java
        // not recursive, so not covered by the rule: an exact name, and "*" (which the JDK does not recurse)
        assertReadAndWrite(0, 0, dev("x\\..\\" + g), dev("UNC\\server\\share"), dev("pipe\\*"));
```
The mechanism that N1 measured for `-` also works one level down for `*`. The JDK clamps `..` at `\\.\X\`, so `\\.\pipe\*` (npath `\\.\pipe\`, directory, not recursive) implies `\\.\pipe\..\Y` for any one name Y. Win32 opens `\\.\Y`.

**Measured** (`R/devtest.out` and `R/devstar.cls-*.out`, plus the RawOpen output below; elevated=False):

| step | result |
|---|---|
| JDK: `\\.\pipe\*` implies `\\.\pipe\..\Volume{14e09011-…}` | r=true, w=true |
| JDK: `\\.\pipe\*` implies `\\.\pipe\..\PhysicalDrive0` | r=true, w=true |
| JDK: `\\.\pipe\*` implies `\\.\pipe\..\C:` | false (that name is invalid) |
| OS: `\\.\pipe\..\Volume{…}` | "Access is denied", the same as `\\.\Volume{…}` itself |
| OS: `\\.\pipe\..\PhysicalDrive0` | "Access is denied", the same as `\\.\PhysicalDrive0` itself |
| OS control: `\\.\pipe\..\NoSuchDeviceZq81` | "The system cannot find the file specified" |
| OS: `\\.\x\..\Volume{…}` | "Access is denied". This is the other exact name pinned at 0/0, and it is the raw volume. |
| catalogue: all of the above | 0 for read and write, in base and in round 2 |

A volume's own `*` behaves the same way:
- `\\.\Volume{14e09011-…}\*` is the device spelling of `C:\*`. Live: `\\.\VOL\Windows` ≡ `C:\Windows` is true. The JDK implies `\\.\VOL\zq81.txt`.
- The catalogue rates `C:\*` read 1 / write 2. It rates `\\.\VOL\*`, `\\.\pipe\..\VOL\*` and `\\.\GLOBALROOT\Device\HarddiskVolume8\*` 0 / 0.
- The same holds for `\\.\VOL\Users\peter\*`, the device spelling of `${user.home}\*`, which is rated 1 / 2 in its plain spelling.

**Against base and round 1.**
- Base rates all of these 0. So this is not a regression against base.
- Round 1 rated `\\.\pipe\*`, `\\.\VOL\*` and `\\.\GLOBALROOT\Device\HarddiskVolume8\*` read 1 / write 2. Round 2 lowers all three to 0 / 0.

**Failure scenario.** The grant `permission java.io.FilePermission "\\\\.\\pipe\\*", "read,write";` reads as a benign named-pipe grant.
1. In a JVM running elevated, the code opens `\\.\pipe\..\PhysicalDrive0` for write.
2. The JDK permits it.
3. Win32 opens the raw disk.
4. The lint gives 0 and no flag.

Without elevation the open fails with access denied, so the impact needs an elevated service. That is the PLAUSIBLE part of the grade.

**Bearing on item 5.** The brief's example of a legitimate unflagged grant, `\\.\pipe\*`, is not benign. Its reach is every raw volume and disk; only Windows ACLs stand between it and them.

**Bearing on test design.** The pin at `PermissionSetsTest:566` snapshots current behaviour. It does not state an invariant, and it will fail any future fix.

### R2-2. Test gap, CONFIRMED by mutants: two realistic one-token defects pass all 26 tests, and PathDiff as well

Method: I wrote 9 mutants and rebuilt the producer's 4, and ran both test classes against each (`R/mut/run-*.out`). Each mutant differs from round 2 by exactly the lines shown (checked with `diff --strip-trailing-cr`).

**x-case.** In `PermissionSets.fileReach`, `f.segments.get(0).equalsIgnoreCase("home"|"Users")` becomes `.equals(…)`.
- Result: **OK (26 tests)**. Differential 0 / 15,322.
- Lowered (`R/surv-case.in`, run as r2 → mutant):
  - `c:\USERS\alice\-`: read and write 2 → 0, flag true → false. The JDK implies `C:\Users\alice\zq81\f.txt`: true.
  - `/HOME/-`: 2 → 0.
- `C:\users\-` stays HIGH, through the user.home prefix check.
- PathDiff on the mutant: FN-danger is still 8,864, the same as round 2 (`R/pd-x-case/summary.txt`).
- First zero link: **SCENE**. Neither generator writes a lowercase or uppercase first name below the root with a second name after it. The only case variants in the tests are `/Root/-` and `/Users/Alice/-`, and in the second the first segment is canonical.

**x-del.** In `PermissionSets.fileDanger`, `boolean write = hasAction(actions, "write") || hasAction(actions, "delete") || …` loses `delete`.
- Result: **OK (26 tests)**.
- Lowered (`R/surv-del.in`):

| name, delete | round 2 | x-del |
|---|---|---|
| `${java.io.tmpdir}${/}-` | 2 | 0 |
| `${java.home}${/}-` | 2 | 0 |
| `${java.home}${/}lib${/}security${/}cacerts` | 2 | 0 |
| `/tmp/-` | 2 | 0 |
| `/var/tmp/-` | 2 | 0 |
| `-` | 2 | 1 |
| `${java.io.tmpdir}${/}*` | 1 | 0 |

- Why no test catches it:
  - The differential test scores `ACTIONS = {"read", "write"}` only.
  - Every delete row in `PermissionSetsTest` is a name that is `[H]` whatever the action.
  - PathDiff's trees are all `[H]` for any action, so it cannot see the change either.
- This gap predates the patch. The patch did not touch `fileDanger`.
- For contrast, **x-exec** (execute dropped) is killed by `testDangerSeverities` and `testFileTargetsAreExpandedAndNormalised`.

**Killed** (for the record):

| mutant | what it changes | killed by |
|---|---|---|
| x-tdot | trailing-dot strip off | differential 1,504/15,322 = 9.82 %, plus unit |
| x-devlead2 | device root only with exactly two slashes | differential 4,680/15,322 = 30.54 % |
| x-starkeep | `*` never rewritten to `-` | differential 46/15,322, plus 5 unit tests |
| x-floor | round-1 UNC floor restored | unit test only (`testNamesThatAreAHomeOnAUnixJdkStayHigh`) |
| x-longcase | case-sensitive `UNC` in `withoutLongPathPrefix` | unit test only |
| x-devstar | device rule also for `*` (an over-rating) | unit test only, line 566 |
| x-stardir | `*`-in-directory branch removed | unit test only |

### R2-3. CONFIRMED: comment honesty (brief item 6)

**Fixed and true** (each was measured):
- `withoutDeviceRoot`:
  - npath `\\.\x\Volume{…}` for `\\.\x\..\Volume{…}\-` (`R/devtest.out`);
  - live reach `\\.\x\..\UNC\localhost\C$\Users\peter` ≡ `C:\Users\peter`;
  - live reach `\\.\UNC.\localhost\C$\Users\peter` ≡ `C:\Users\peter`;
  - `\Device\Mup\localhost\C$\Users\peter`: `File.exists` false.
- `withoutLongPathPrefix`: the order of the three strips, and the DirtyChai line citations, checked in the source:
  - `stripLongOrUNCPrefix` declared at `WinNTFileSystem.java:69` and called at :132;
  - `normalizePrefix` at :225-254;
  - the prefix strip at `WindowsPathParser.java:66-67`.
- The `unc` case wording (round-1 C6): `//?/unc/…` gives invalid=true, and `\\?\unc\…` gives npath `unc\server\share`, relative.
- The F1 inline citation `FilePermission.java:373-389` is correct.
- The N3 inline comment is correct.
- The npaths cited by the F1 and F2 test comments: 17 rows measured (`R/cmt.out`), all as the comments state.

**Still wrong or overstated:**
- **C-1.** `PermissionSetsTest.testARecursiveNameInTheDeviceNamespaceIsHigh` says: *"The spellings are the review's; the JDK grants each of them over the target it reaches (C:\, C:\Users, one home or a share)."* Measured over all 35 of its names:
  - two are JDK-invalid: `\\.\C:\Users\-` and `//?///./pipe/x/-`;
  - the "any depth" block is the producer's, not the review's: `\\.\pipe\x\-`, `\\.\Volume{0…}\data\-`, `\\.\UNC\server\share\dir\-` and `\\.\Volume{0…}\Users\alice\Documents\-`. These names reach no ruled target as spelled; they are HIGH by rule.
  - The line-566 comment ("not recursive, so not covered") presents not-covered as harmless; R2-1 refutes that.
- **C-2.** `PermissionSetsTest.testLongPathPrefixAfterAnyRunOfSeparators` says: *"WinNTFileSystem and WindowsPathParser strip the long-path prefix after the run of leading separators is collapsed."*
  - WinNTFileSystem strips before the collapse: `:132` runs before `normalizePrefix`.
  - The comment is unchanged from round 1 (round-1 patch line 256).
  - Round-1 C4 was fixed in the production javadoc only.
- **C-3.** `filePath`'s javadoc says `.` and `..` are applied *"and only then the last name tested for `-` or `*`"*.
  - The code tests `*` on the raw last name: `boolean star = last >= 0 && raw[last].equals("*");`. So does the JDK: `name.endsWith("*")` at `FilePermission.java:373`.
  - The inline F1 comment says this correctly.
  - Measured consequence: `C:\Users\*\.` is invalid in the JDK and rated 0, which matches the code, not the javadoc.
- **C-4.** The differential test's javadoc says the catalogue rates `${user.home}${/}*` MEDIUM. That is true for read only. Write is 2 (`C:\Users\peter\*` write 2, `R/devstar.cls-base.out`).

## 2. The producer's claims, each re-measured

### Item 1: device namespace

- **The round-1 DEVFAM family.** Grants `\\.\X\Y\-` with access `\\.\X\..\Y\…`; 10 values of X × 12 of Y × {read, write}:
  - 240 rows, all implied by the JDK;
  - **240 / 240 rated HIGH in round 2** (base 86);
  - 0 regressions and 0 rows below HIGH (`R/devfam.r2.out`).
- **Every round-1 N1 and N2 spelling** (in `grant.in`, `grant2.in` and `mut.in`) is HIGH for read and write.
- **Names that reach `\\.\` only after the long-prefix strip and the separator collapse** are flagged as device and rated HIGH (`R/devprefix.out`): `\\?\UNC\.\VOL\Users\alice\-`, `//?/UNC/./…`, `\\?\UNC\\.\pipe\x\-`, `/\./pipe/UNC/-` and the doubled-separator form.
- **The producer's "not device" list holds:**
  - `\Device\Mup\…` (one separator): JDK npath `\Device\Mup\server\share`; OS: `File.exists` false; rated 0.
  - `\\UNC.\server\share\-`: OS gives "network path was not found"; rated 0.
  - `\\.\pipe\*` and `\\.\x\..\Volume{…}`: rated 0, but see R2-1.
- `\\?\GLOBALROOT\…\-` and `\\?\Volume{…}\-` are relative in the JDK (npath with no root), and are rated 0, which is consistent.

### Item 2: UNC floor withdrawn; PathDiff, base against round 2

**Method.** I ran PathDiff (105,694 spellings × 4 actions = 422,776 rows) against `cls-base` and `cls-r2`, then joined the outputs by line number: 0 rows misaligned.
- Summaries reproduce the producer's exactly:
  - base: FN 58,932, FP-read 8,264;
  - round 2: FN 8,864 (unrated 7,808, MEDIUM 1,056), FP-read 23,319.
- Raised rows: 118,896. **Lowered rows: 572.** All 572 went from HIGH to 0, 143 per action, and flagReason went from true to false on all 572 (`R/lowered-from-high.tsv`).
  - 212 are JDK-invalid.
  - 360 are JDK-valid. Their npaths are `C:\-` (192) and `c:\-` (168): a directory named `-`. Every one has the shape `\\\?\C:\..{1-9}\-\-` or `\\?\?\C:\…`.
- **0 lowered rows cover a ruled tree** through a verified alias. That covers every tree PathDiff models: drive, `C:\Users`, one home, user.home, `\home`, `\home\alice`, `\root`, `\var\root`, share and host.
- 0 lowered rows come from an undefined property, and none was lowered from MEDIUM.
- The negative's first zero link is **SCENE**. PathDiff does not generate `*` under device roots, case-folded two-name homes, or delete-only trees. R2-1 and R2-2 are what those blind spots hide.

**The round-1 Linux-visible list** (`R/q.r2.out`, read):
- `//./home/alice/-`, `//./Users/alice/-`, `//home/alice/../bob/-`, `//home/alice/x/../../bob/-` and `//var/root/../root/-` are HIGH again (round 1 had 0).
- `//home/x/../-` and `/home/alice./-` are HIGH as well.
- `//?/home/alice/-` is 0 in both base and round 2.

### Item 3: the differential test is now load-bearing

Re-derived with my own builds and the round-2 test classes (rejection rate = missed / JDK-covered rows; population 8,878 spellings × 2 = 17,756 rows):

| catalogue | missed / covered | rate | controls hit |
|---|---|---|---|
| round 2 | 0 / 15,322 | 0.00 % | 0 / 992 |
| base | 13,786 / 15,322 | 89.98 % | |
| F3 reverted | 216 / 15,322 | 1.41 % | |
| F1 reverted | 9,380 / 15,322 | 61.22 % | |
| m-devcase | 7,488 / 15,322 | 48.87 % | |
| all-HIGH | 0 / 15,322 | | **992 / 992** |
| round 1 | 10,704 / 15,322 | 69.86 % | |
| round-1 M2 | 10,776 / 15,322 | 70.33 % | |

All of these match the producer's figures.

**The 70.33 % question.** The 72-row difference between round 1 and its M2 mutant lies entirely on `dev-alias:GLOBALROOT/device/HarddiskVolume3`: 1,152 → 1,224 of 1,224.
- So the test isolates that mutation exactly. It only "barely moves" because round 1 already missed 1,152 of those rows.
- M2 mutates code that round 2 deleted, and round 2's own analogue (m-devcase) is killed at 48.87 %.
- **Verdict: not an unkilled mutant.**

**The controls work under the null.** On the healthy catalogue they read 0 / 992; when every name is rated HIGH they read 992 / 992.

**What still passes:** R2-2 (case of ruled names; delete). The gaps the brief named:
- **A `*` row in a tree:** generated but unscored. This is Q2, and it is open. It is not a defect of the test.
- **Delete and execute:** execute is guarded by the unit tests; delete is not (R2-2).
- **Chain spellings scored only against chain probes:** this is by design, because of F7, which is out of scope. The F3 revert is killed at 216 rows, and no chain-tier mutant I built passes.

### Item 4: regressions against base (`R/grant.r2.out` R block, `R/policy-act.out`)

- `lib/-` write: 0 → 0 (fixture r8/f14-rel-sub-write, `cross-check-allow.tsv` line 21).
- `/opt/app/conf/-` read: 0 → 0.
- Named directory below a share or home: `\\server\share\dir\-`, `C:\Users\alice\Documents\-` and `/home/alice/work/-` are 0 → 0.
- `${undefined.zz}/-` read: 2 → 2.
- `<<ALL FILES>>` readlink: 1 → 1.

**Real repo policies.** Every `java.io.FilePermission` in the 540 tracked `*.policy` files under `JGDMS-normaliser-20261106`:
- 898 lines, which reduce to 121 distinct (name, actions) and then 168 (name, action) rows;
- **0 changed between base and round 2.**

**`PolicyScope.classify`.** Same 12 codeBases as round 1 (`R/scope.r2.out`):
- 3 changed, all FILE_PINNED → FILE_WRITABLE: `file:/C:/-/.`, `file:/C:/Users/peter/-/.` and `file:/C:/Temp./lib/-`. These are the same 3 as in round 1.
- The device and long-prefix branches cannot be reached from `PolicyScope.file`: a path with two or more leading slashes goes to `share()` before `filePath` is called:
  ```java
  if (m >= 2) return share(q, m, url ? ", after decoding" : "");
  ```
  (`PolicyScope.java:342`).

**The dir lookup in `fileReach`** (`filePath(d[1])` on user.home, user.dir, java.home, java.io.tmpdir, /tmp and /var/tmp):
- On this host's values (`C:\Users\peter`, …) the parse is unchanged.
- The ACT rows that go through it are unchanged: `${java.io.tmpdir}${/}*` write 1, `${java.home}${/}lib${/}security${/}cacerts` write 2, `${user.home}${/}*` read 1 and write 2.
- The behaviour would change only for a property value that carries `\\?\` or `\\.\`, a trailing-dot segment, or `..` above a UNC share. No such value exists on this host.

### Item 5: FP cost

**The 12,038 device-rule FP-read rows** (`R/pd-r2/mismatches.tsv`, reason "device namespace"):
- 4,862 are JDK-invalid.
- 56 are JDK-non-recursive: names shaped like `\\.\-`, which the JDK reads as the share root `\\.\-\`.
- 7,120 are JDK-recursive. Of these:
  - 440 are `\\.\GLOBALROOT\`: the whole NT namespace, so a true reach;
  - 440 are the HarddiskVolume3 stand-in;
  - the rest are named directories under a device root, such as `\\.\VOL\Windows` and `\\.\UNC\server\share\Users\alice`.

**Does any repo policy pay this cost?** `git grep -F` for `\\\\.\\`, `//./`, `\\\\?\\` and `//?/`:
- over the 6,659 tracked non-Java files in JGDMS: only binaries hit (a JFR profile and a PDF);
- over the 4,926 tracked files outside `JGDMS/`: only QA log dumps of an environment variable (`CHROME_CRASHPAD_PIPE_NAME=\\.\pipe\…`);
- **0 policy, config or fixture files carry a device or long-path name.**
- The instrument works: the same pattern hits 8 times in the differential test's source.

**Plausible legitimate grants** (`R/devstar.cls-*.out`):

| name | rating | note |
|---|---|---|
| `\\.\pipe\foo` | 0 | |
| `\\.\pipe\*` | 0 | but see R2-1 |
| a long-path file `\\?\C:\Program Files\App\very\long\path\file.txt` | 0 | |
| `\\?\C:\Program Files\App\-` | 0 | |
| `\\.\pipe\-` | HIGH | base HIGH too. A true positive: it implies `\\.\pipe\..\VOL\Users\peter\…` |
| `\\.\pipe\foo\-` | HIGH | over-rated; base HIGH too |
| `\\.\C:\Program Files\App\-` | HIGH | JDK-invalid. Base 0, round 1 0 |

**Verdict:** the noise falls on spellings that no measured policy uses. The rule is not too noisy for realistic policies.

### Linux-visible behaviour (by reading only; UNVERIFIED-NEEDS-LINUX-JDK)

- `//./…/-` is HIGH at any depth on every host. On Unix `//./opt/app/-` is `/opt/app/-`, so this is an over-rating.
- The trailing-dot strip applies on Unix: `/home/alice./-` is rated as `/home/alice` (HIGH), an over-rating.
- `..` pops through `//host/share` again, which matches Unix.
- I found no new under-rating on Unix by reading.

## 3. Operator questions (each answerable from an artefact)

- **Q-A, extending Q3.** Should `*` directly under a device root, and exact `\\.\` names, carry a rating?
  - `\\.\pipe\*` reaches every raw volume and disk through `\\.\pipe\..\Y`.
  - `\\.\Volume{guid}\*` is `C:\*`.
  - Artefacts: `R/devtest.out`, `R/devstar.cls-r1.out`, and the RawOpen lines in R2-1.
  - If yes, the pin at `PermissionSetsTest:566` is wrong.
- **Q-B.** Should a case-folded ruled name (`c:\USERS\alice\-`) and delete-only reach (temp tree, JDK home) be pinned?
  - A row each in the unit test, or `delete` in the differential test's ACTIONS, would kill x-case and x-del (R2-2).

## 4. UNVERIFIED

- **UNVERIFIED-NEEDS-BUILD:** `PolicyLintCorpusTest` (`cross-check-allow.tsv`) end to end under mvn. I checked only the catalogue's rating of the corpus names, via Rv (168 rows, 0 changes).
- **UNVERIFIED-NEEDS-LINUX-JDK:** the Linux reach in section 2.
- **Impact of R2-1 in an elevated JVM:** not run elevated, by design. The resolution was measured without elevation.

## 5. Reproduce (from R; J = DirtyChai JDK, JAR = the m2 jar, JU = hamcrest-core 1.3 then junit 4.13.2)

- **Probe:**
  ```
  $J/bin/java --add-exports java.base/sun.security.util=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED -cp ../opus-normrev/cls-probe Rv <in> $JAR cls-base cls-r2 [../opus-normrev/pc-main]
  ```
  Inputs: `devtest.in`, `devstar.in`, `devprefix.in`, `cmt.in`, `surv-*.in`, `policy-act.in`, and the round-1 `*.in` files.
- **Tests:**
  ```
  $J/bin/java -cp "$JU;cls-test-r2;<cls-r2|cls-base|cls-r1|mut/cls-*>;cls-extra;$JAR" org.junit.runner.JUnitCore au.net.zeus.jgdms.api.policy.PermissionSetsFilePermissionDifferentialTest au.net.zeus.jgdms.api.policy.PermissionSetsTest
  ```
  Mutant sources are in `mut/<name>/`.
- **PathDiff:** cwd `JGDMS-review-20261106/probe-pathdiff`, classpath `R/pd-classes;R/cls-<v>;$JAR`, arguments `R/pd-<v> Volume{14e09011-0000-0000-0000-300300000000}`. Then join base and round 2 by line number with awk (`R/lowered-from-high.tsv`).
- **Raw open:** `java -cp R/cls-rawopen RawOpen '\\.\pipe\..\Volume{…}' …`, run from PowerShell.
