claude-opus-5-5

# Review: uncommitted `PermissionSets.filePath` normaliser patch (JGDMS, jgdms-platform)

**Reviewer:** claude-opus-5-5. The producer is Sonnet 5.5 (per the brief), so this is a cross-model review.
**Date:** 2026-11-06/07. **Mode:** read-only on both worktrees. No mvn was run.
- Worktree `JGDMS-normaliser-20261106`: status and md5 unchanged after the review (`PermissionSets.java` md5 `537b4ce50dd31c42698524b2ad18b4e9`, matching the producer's `final-md5.txt`).
- `JGDMS-review-20261106/probe-pathdiff`: nothing written there (`find -newer` is empty). It was only the cwd of one read-only run.

**Skills loaded:** handover-composition, production-code, code-comments. rag-file was not loaded: no scan data is involved.
**Not consulted:** `E:\Claude\TRAINING_DOCS_INDEX.md`. The brief forbids touching `E:\`.

**JDK for every result:** `C:\Users\peter\Documents\GitHub\DirtyChai\build\windows-x86_64-server-release\images\jdk` (27-internal-adhoc.peter.DirtyChai), with `jdk.io.permissionsUseCanonicalPath=false`. The probe prints both facts.

**Scratch root (all inputs, outputs and sources):** `S = C:/Users/peter/AppData/Local/Temp/claude/C--Users-peter-Documents-GitHub-DirtyChai/757702ae-f74f-45fd-95a6-a302242c73cd/scratchpad/opus-normrev`

## 1. Method

- **Two catalogues, built by me.**
  - `cls-base` is `git show 0eb9f0cc7:…/PermissionSets.java`. It is identical to the earlier report's md5 `6855c39…` modulo line endings.
  - `cls-new` is the worktree file, md5 `537b4ce5…`.
  - Both were compiled with `javac --release 8` against the m2 `jgdms-platform-4.0.0-SNAPSHOT.jar` (md5 `91a06334…`).
- **Production entry points.** The probe `S/probe/Rv.java` loads both catalogues in separate class loaders. It calls `PermissionSets.danger(FILE, name, action)` and `flagReason(...)`, plus `PolicyScope.classify` reflectively.
- **JDK side.** `new FilePermission(name, a).implies(new FilePermission(access, a))`, with the `invalid`, `npath` and `recursive` fields read reflectively.
- **OS side.** A `LIVE` row means `Files.isSameFile(alias, canon)` on this host, plus `new File(alias).exists()`. The two agreed on every row.
- **Validation under the null.**
  - The LIVE instrument reads false where it should: `\\localhost\C$\..\C$\Windows` is false, `C:\Users..\peter` is false, `\\localhost\..\C$\Windows` gives "network name cannot be found". It reads true elsewhere.
  - The catalogue diff responds in both directions: rows are raised and rows are lowered.
  - The mutants below change verdicts.
- **NT device behind `C:` on this host:** `\Device\HarddiskVolume8`. Source: `QueryDosDevice("C:")` through PowerShell P/Invoke, which reads the object namespace only.
  - So the earlier review's GLOBALROOT check, which used N=3, could never have matched. HarddiskVolume3 in the producer's tests is a lexical stand-in.
- **Re-run of the earlier generator.** `PathDiff` (105,694 spellings × 4 actions = 422,776 rows) was run against `cls-new` and `cls-base`. Outputs are in `S/pd-new/` and `S/pd-base/`. They reproduce the producer's figures exactly:
  - FN-danger 8,864 (unrated 7,808, MEDIUM 1,056);
  - FP-read 17,066;
  - base 58,932 / 8,264.

## 2. Findings, ranked by exploitability

### N1 — CONFIRMED. Regression plus gap: the device namespace has one root, `\\.\`, but the patch treats the device name as a drive root

`au.net.zeus.jgdms.api.policy.PermissionSets.deviceNamespace`:
```java
        int want = dev.equalsIgnoreCase("GLOBALROOT") ? 3 : 1;
        ...
        f.drive = "//./" + String.join("/", root);
        return "/" + after;
```
Its javadoc says a volume, `GLOBALROOT/Device/HarddiskVolumeN` "or other device is a drive root".

**What was measured** (rows in `S/live.out`, `S/grant.out`, `S/grant2.out`):
- **The OS.** Win32 roots a `\\.\` name at `\\.\` alone. `..`, `.` and one trailing dot all act on the device name itself:
  - `\\.\x\..\Volume{14e09011-…}\Windows` ≡ `C:\Windows`: true.
  - `\\.\UNC\..\Volume{…}\Windows` ≡: true.
  - `\\.\pipe\..\HarddiskVolume8\Users\peter` ≡ `C:\Users\peter`: true.
  - `\\.\pipe\..\UNC\localhost\C$\Users\peter` ≡: true.
  - `\\.\UNC.\localhost\C$\…` ≡: true.
  - `\\.\.\Volume{…}\Windows` ≡: true.
  - `\\.\GLOBALROOT\..\GLOBALROOT\Device\HarddiskVolume8\Windows` ≡: true.
- **The JDK.** It parses `\\.\X\` as a UNC root with host `.` and share X, and clamps `..` there. So it normalises the access `\\.\X\..\Y\…` to `\\.\X\Y\…`.

**Consequence:** any recursive grant `\\.\X\Y…\-` also reaches `\\.\Y…\`.

**Measured family** (`S/devfam.out`, mode DEVFAM of `Rv`): grants `\\.\X\Y\-` with access `\\.\X\..\Y\zq81\deep\f.txt`.
- X: 10 values (pipe, a, x, Volume{…}, HarddiskVolume8, GLOBALROOT, UNC, `.`, Mup, PhysicalDrive0).
- Y: 12 targets (UNC, UNC\server, UNC\server\share, the volume, the volume's Users and Users\alice, the same three under HarddiskVolume8, GLOBALROOT, GLOBALROOT\Device\HarddiskVolume8, and that path's Users).
- Rows: 10 × 12 × {read, write} = **240 rows; the JDK implies all 240.**
- **Base rated 86 HIGH; the patch rates 14 HIGH.**
- **78 rows (39 spellings) fell from HIGH to below HIGH (regressions).**
- 148 rows are below HIGH in both versions (gaps). 6 rows were raised.

**Regressions run one by one with a live-verified access family** (base → new, read and write; flagReason true → false):
- `\\.\pipe\UNC\-`: every share on every host, 2 → 0.
- `\\.\pipe\GLOBALROOT\-`: the whole NT namespace, so every volume, 2 → 0.
- `\\.\a\Volume{14e09011-…}\-`: the whole C: volume, 2 → 0.
- `\\.\pipe\HarddiskVolume8\-`: the whole C: drive, 2 → 0.
- `\\.\.\Volume{…}\Users\-`: `C:\Users`, 2 → 0.

**Gaps run one by one** (0 → 0):
- `\\.\pipe\HarddiskVolume8\Users\-`
- `\\.\Volume{…}\HarddiskVolume8\Users\-`
- `\\.\a\Volume{…}\Users\-`
- `\\.\x\UNC\server\share\-`
- `\\.\Volume{…}\UNC\server\share\-`
- `\\.\UNC\GLOBALROOT\Device\HarddiskVolume8\-`

**Failure scenario.** The policy entry is `permission java.io.FilePermission "\\\\.\\pipe\\UNC\\-", "read,write";`.
1. The code opens `\\.\pipe\..\UNC\nas\share\secret`.
2. The JDK's npath is `\\.\pipe\UNC\nas\share\secret`, which is inside the grant, so the access is permitted.
3. Win32 opens `\\.\UNC\nas\share\secret`.
4. The patched lint gives severity 0 and no flag. Base gave HIGH.

**Grade.**
- The JDK and catalogue sides are CONFIRMED for all 240 rows.
- OS reach is CONFIRMED live for X ∈ {x, a, pipe, UNC, Volume, GLOBALROOT}.
- OS reach is PLAUSIBLE, by the same lexical mechanism, for X ∈ {HarddiskVolume8, `.`, Mup, PhysicalDrive0}.

**Exploitability.** Only an adversarial or obfuscated grant text produces these spellings. No benign policy names `\\.\pipe\UNC`. But each one is a whole-machine or whole-network grant.

**Why the earlier generator missed it.** The link where its response first goes to zero is SCENE: 0 of its 343 lowered device-namespace read rows has a device as the second name. Measured with `S/tally.awk` over `S/lowered-diag.tsv`.

**Direction (advice; humans write the fix).**
- Rate a `\\.\X\rest` name as the maximum of its reading with and without X.
- Or rate every recursive `\\.\` grant HIGH. That is an operator question (Q3).
- The underlying divergence is in DirtyChai: FilePermission and WindowsPath clamp `..` at `\\.\X\`, while Win32 does not. Raising that with DirtyChai is a Phase-2-style advisory, not code.

### N2 — CONFIRMED. Gap, not a regression: the F4 fix covers only the canonical spelling of GLOBALROOT and device UNC

The root recognition in `deviceNamespace` is positional and runs before `.` and `..` are applied:
```java
            if (root.size() == 1 && !nm.equalsIgnoreCase("Device")) break;
```
The `dev.equalsIgnoreCase("UNC")` test does not allow for the OS stripping a trailing dot.

The following 9 spellings were each measured with JDK `implies` true against an access whose family is verified live (OS target in C:\, `C:\Users` or a share). All are rated **0 / 0 for read and write**, in base and patched, with no flag:
- `\\.\GLOBALROOT\Device\HarddiskVolume8\..\HarddiskVolume8\Users\-` → `C:\Users`
- `\\.\GLOBALROOT\.\Device\HarddiskVolume8\Users\-` → `C:\Users`
- `\\.\GLOBALROOT\Device\.\HarddiskVolume8\-` → whole C:
- `\\.\GLOBALROOT\Device\x\..\HarddiskVolume8\Users\alice\-` → one home
- `\\.\GLOBALROOT.\Device\HarddiskVolume8\Users\-` → `C:\Users` (live: `\\.\GLOBALROOT.\…` ≡ true)
- `\\.\GLOBALROOT\Device\Mup\server\share\-` → whole share. `\Device\Mup` is the UNC redirector (live: `…\Mup\localhost\C$\Users\peter` ≡ true).
- `\\.\GLOBALROOT\Device\Mup\localhost\C$\-` → whole C:
- `\\.\UNC.\server\share\-` → whole share (live: `\\.\UNC.\localhost\C$\Users\peter` ≡ true)
- `\\.\.\UNC\server\share\-` → whole share (live ≡ true)

**Scenario.** As in N1, but no `..` is needed in the access: the grant itself is spelled oddly.

**Grade:** CONFIRMED for these 9. The producer's "F4 fixed" holds for the canonical forms only.

### N3 — Catalogue CONFIRMED; Linux JDK reach is HYPOTHESIS. A new Linux-visible narrowing (brief item 6)

The catalogue is host-blind, so its rating is measured here (`S/q.in`). Each of these was HIGH at base and is **0 now**, for read:
- `//./home/alice/-`
- `//./Users/alice/-`
- `//home/alice/../bob/-`
- `//home/alice/x/../../bob/-`
- `//var/root/../root/-`

On a Unix JDK, `//` collapses to `/` (DirtyChai `src/java.base/unix/classes/java/io/UnixFileSystem.java:72-94`; the duplicate-slash skip is at line 83). `UnixPath.normalize` (`UnixPath.java:566`) then applies `..`. So each of these names one user's whole home there.

**Causes:**
- `deviceNamespace` is applied on every host;
- the F8 UNC floor is applied on every host.

The base reading (pop through) happened to match Unix.

**Grade.** The rating change is CONFIRMED. The Linux reach is UNVERIFIED-NEEDS-LINUX-JDK.

### N4 — CONFIRMED. The differential test is not load-bearing for the families in N1–N3 (brief item 4)

`PermissionSetsFilePermissionDifferentialTest` was run with JUnit 4.13.2 against each catalogue (`S/run-new-tests.out`, `S/run-diff-base.out`, `S/mut-*.out`):

| catalogue | rejection rate (missed / JDK-covered rows) | other tests |
|---|---|---|
| patched | 0 / 4,808 (0.00 %) | reproduces the producer |
| base `0eb9f0cc7` | 3,932 / 4,808 (81.78 %) | |
| F1 reverted (producer's `revert-f1-src`, rebuilt by me) | 2,404 / 4,808 (50.00 %) | `wildcard-dot` 1,202/1,202 and `wildcard-parent-chain` 1,202/1,202 missed; reproduces the producer |
| **M1:** F3 reverted (`f.drive == null &&` restored) | **0 / 4,808: passes** | only `PermissionSetsTest.testDriveRelativeParentChainsAreRated` catches it |
| **M2:** `nm.equals("Device")` (case-sensitive) | **0 / 4,808: passes** | all 24 `PermissionSetsTest` tests pass as well |

**M2 under the suite.** M2 lowers two OS-verified whole trees from HIGH to 0 (`S/mut.in` output):
- `\\.\GLOBALROOT\device\HarddiskVolume8\Users\-` → `C:\Users`;
- `\\.\GLOBALROOT\DEVICE\HarddiskVolume8\-` → whole C:.

The OS treats `device` case-insensitively (live ≡ true). Every test stays green.

**Gap sizes and causes:**
- **Population.** 3,390 spellings × 2 actions = 6,780 rows, of which 4,808 are covered. There is no `.` or `..` inside a device root, no trailing dot on a device name, no second device name, and no `Mup`. All of N1 (240 rows) and N2 (9 spellings) lie outside it.
- **Probes are spelled in the grant's own root family**, and the JDK side is lexical. So reach through an OS alias, as in N1, is invisible by construction. The class javadoc says as much ("a second instrument on the catalogue, not a second bed for the OS").
- **One-sided.** The test only checks for under-rating. A catalogue that rates everything HIGH passes it.
- **Deep probes only.** The `star` tag never appears among covered rows (`S/run-new-tests.out`: `star-tag-present: 0`). `*` handling and non-recursive directory grants are therefore never exercised.
- **Read and write only.** delete and execute share `fileDanger`'s write branch, so the risk is low.
- **Windows only.** Both test methods start with `Assume.assumeTrue(windows())` and `assumeTrue(lexical())` (read). So on Linux CI they are reported as skipped, not passed.
- **Arithmetic.** `missed / covered` over rows, with the population printed. The figures check out.

### N5 — CONFIRMED by measurement. Comment honesty (brief item 5)

- **The old false comment is gone, and its replacement is true.**
  - The `PermissionSetsTest` list now holds `\\?\UNC\…`, `//?/UNC/…` and `\\?\C:\-`. Measured: npath `\\server\share\` recursive, `\\server\share\` recursive, and `C:\` recursive.
  - The lowercase member moved to its own block, which says the JDK refuses `//?/unc/server/share/-`. Measured: `invalid=true`.
- **C1 (false, and the reference N1 was built against).** `deviceNamespace`'s javadoc says "a volume, GLOBALROOT/Device/HarddiskVolumeN or other device is a drive root". The OS measurements in N1 refute it.
- **C2 (incomplete).** `FilePath.deviceUnc`'s javadoc, "the OS … pops a share with `..`", is true but stops short: the OS also pops the host and `UNC` itself (`\\.\UNC\..\Volume{…}` ≡ the volume).
- **C3 (a contract that is not met).** `filePath`'s javadoc promises "the wider reading where the JDK's and the OS's differ". In the device namespace the patch takes the narrower reading. The javadoc also says "the way java.io.FilePermission reads it", but `PolicyScope.classify` uses `filePath` for CodeSource URLs, which match by raw `endsWith("/-")` (DirtyChai `CodeSource.java:448`); see N6.
- **C4 (mechanism attributed to the wrong class; conclusion right).** `withoutLongPathPrefix` says WinNTFileSystem "collapses a run of leading separators … before it looks for the prefix". In fact `WinNTFileSystem.normalize` strips the raw prefix first (`WinNTFileSystem.java:131-132`) and collapses later (`normalizePrefix`, line 225). The strip after the collapse is `WindowsPathParser.parse`'s (line 66-67).
- **C5 (false for GLOBALROOT).** The differential test's class javadoc says "the review verified the families live with Files.isSameFile". The earlier review's GLOBALROOT check failed (N=3) and was graded PLAUSIBLE. I have now verified it live with HarddiskVolume8.
- **C6 (true for the spelling quoted, misleading as a rule).** "The JDK is case-sensitive and refuses `//?/unc/…`" holds for that spelling. The backslash form `\\?\unc\server\share\-` is accepted as the *relative* path `unc\server\share` (`invalid=false`, `rec=true`; implies `\\server\share\…` = false). So "refuses" holds for the slash form only.

### N6 — CONFIRMED. PolicyScope callers: conservative changes, plus one pre-existing gap

`PolicyScope.classify`, base vs new (`S/scope.in`), 12 codeBases. **3 changed, all from FILE_PINNED to FILE_WRITABLE:**
- `file:/C:/-/.` → "drive or filesystem root". An over-rating: CodeSource does not treat `/-/.` as recursive.
- `file:/C:/Users/peter/-/.` → "over a directory above user.home". An over-rating for the same reason.
- `file:/C:/Temp./lib/-` → "temp". Correct: Win32 strips the dot.

**Pre-existing, not a regression:** `file:/C:../lib/-` is FILE_PINNED in both versions. `PolicyScope.classify`'s test `!f.absolute && f.drive == null` misses drive-relative names, which is the counterpart of F3 that the patch fixed only in `fileReach`. PLAUSIBLE impact (a code source whose URL is drive-relative).

## 3. Negatives (each with what established it, and where the response would first go to zero)

- **No under-rating regression in the earlier PathDiff population.**
  - The join of `S/pd-base/all.tsv` and `S/pd-new/all.tsv` is row-aligned: 422,776 rows, 0 misaligned.
  - 6,540 rows were lowered (1,635 read). Every one of the 1,635 read rows was adjudicated by its own JDK fields (`S/adj-lowered.out`): 479 JDK-invalid, 327 non-recursive, **0 recursive over a root**, 829 recursive over a named directory.
  - The named directories are all below a share, a volume or `C:\` (list in `S/adj-lowered.out`). They stay narrow because Win32 clamps `..` at a UNC share, as the next item shows.
  - First zero link: SCENE (see N1).
- **The F8 UNC floor matches the OS, not only the JDK.**
  - `\\localhost\C$\..\C$\Windows` ≡ `C:\Windows`: false.
  - `\\localhost\C$\..\Windows`: true. `\\localhost\C$\Windows\..\..\Windows`: true.
  - A share named `..`: "network name cannot be found".
  - So the producer's `\\server\share\..\-\-` = 0 is right for the OS as well.
- **One trailing dot, as the patch assumes.** `C:\Users.\peter` ≡ true; `Users..`, `Users...`, `Users. .` and `Users .` are all false. `.. .` is not `..` (false).
- **Remaining PathDiff FN is all in F7 families.** 8,864 FN-danger rows. Their first alias families are adminshare 7,392, docsettings 7,168, driverel 3,520 and rel 1,760 (rows counted once per tree). None reaches a tree through canon, fwd, rootrel, long, volguid or dotseg.
- **No new FP class among the raised rows.**
  - 12,123 raised read rows had no PathDiff coverage (`S/adj-raised.out`). Of these, 5,543 are JDK-invalid and 932 non-recursive.
  - The rest are recursive over trees PathDiff does not model: HarddiskVolume3 stand-ins, dotted volume and root-relative forms, and shares of hosts other than `server`.
  - Also `C:-\.` moved to read MEDIUM / write HIGH, consistent with `C:-`.
- **Wildcard and `..` edge cases (brief item 1): 36 grant rows, none under-rated** (`S/grant.out`, W block):
  - `C:\x\..\-`, `C:\-\.\.`, `C:\Users\-\..\-`, `C:\..\-`, `\\?\C:\..\-`, `\\?\C:\Users\..\..\-\.`, `/C:/Users/-/.`, `c:\USERS\-\.`, `${user.home}${/}..${/}-` → HIGH;
  - `C:\*\.` and `C:\-\..` → the JDK gives invalid and exact-root respectively, and the catalogue gives 0.
- **ADS and trailing spaces cannot under-rate.** `C:\-::$DATA`, `C:\Users::$INDEX_ALLOCATION\-`, `C:\Users:x\-`, `C:\Users \-` and `C:\- \.` are all `invalid=true` in the JDK.
- **Ruled sentinels unchanged, base = new** (`S/grant.out`, R block):
  - `lib/-` write 0 (r8/f14);
  - `\\server\share\dir\-` 0;
  - `C:\Users\alice\Documents\-` 0;
  - `${undefined.zz}/-` read HIGH;
  - `<<ALL FILES>>` readlink MEDIUM, read HIGH;
  - `-` read MEDIUM, write HIGH.
- **policy-condenser suites against the patched catalogue.** 5 classes, 149 tests. 7 failures, environmental (missing jeri classes, `InternalError`). The failure text is identical with base apart from temp-dir names (`S/pc-fail2-*.txt`). So no difference caused by the patch was observed. But the corpus cross-check did not run to completion: UNVERIFIED-NEEDS-BUILD.

## 4. Operator questions (each answerable from an existing artefact)

- **Q1 (producer's): case-insensitive `\\?\unc\`.** The JDK grants nothing on any share for either form (N5 C6). Keeping HIGH is a harmless false-positive pin. My view: keep it, and fix the comment to name both behaviours. Artefact: `S/q.in` output.
- **Q2 (producer's): deep probes or direct children.** Deep probes are right for the HIGH "whole tree" rule, but they leave `*` unexercised (0 covered `star` rows). Today these rate 0: `C:\Users\*`, `C:\Users\alice\*` and `\\server\share\*`; `${user.home}${/}*` rates MEDIUM. Should `*` over a ruled directory carry a rating? If so, add a second, direct-child scoring rule rather than replacing the deep probes.
- **Q3 (mine): the device namespace.** Should any recursive grant under `\\.\` be HIGH, given the OS root is `\\.\` (N1)? Should the JDK-side divergence go to DirtyChai as an advisory?
- **Q4 (mine): host-blind Windows rules.** Should the UNC floor and the device-namespace reading apply only where they widen the rating, by taking the maximum of the Windows and Unix readings? That is what `filePath`'s own javadoc promises (N3, N5 C3).

## 5. UNVERIFIED

- **UNVERIFIED-NEEDS-LINUX-JDK:** N3's reach on Linux.
- **UNVERIFIED-NEEDS-BUILD:**
  - `PolicyLintCorpusTest` (`cross-check-allow.tsv`) and the PolicyLint end-to-end run on the patched catalogue. My classpath lacked jeri classes; the producer's mvn run is the instrument.
  - The ListPromotion and flagReason wiring (F6) is out of scope.
- **OS reach in the device family for X ∈ {HarddiskVolume8, `.`, Mup, PhysicalDrive0}:** PLAUSIBLE (see N1).

## 6. Reproduce

From `S`, with `J` = the DirtyChai JDK, `JAR` = the m2 jgdms-platform jar, and `JU` = hamcrest-core 1.3 followed by junit 4.13.2 (hamcrest first):
- **Probe:** `$J/bin/java --add-exports java.base/sun.security.util=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED -cp cls-probe Rv <live.in|grant.in|grant2.in|q.in|mut.in|devfam.in|scope.in|adj-*.in> $JAR cls-base cls-new [pc-main]`
- **Differential test:** `$J/bin/java -cp "$JU;cls-test;cls-<new|base|m1|m2|m3>;$JAR" org.junit.runner.JUnitCore au.net.zeus.jgdms.api.policy.PermissionSetsFilePermissionDifferentialTest`
- **PathDiff:** cwd `JGDMS-review-20261106/probe-pathdiff`, output to `S/pd-*`, classpath `out-classes;S/cls-<new|base>;$JAR`, volGuid argument `Volume{14e09011-0000-0000-0000-300300000000}`.
