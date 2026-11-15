claude-opus-5-5

# REVIEW: 8.3 short names, Local Settings and PolicyScope aliasing (shortnames.patch) on JGDMS-shortnames-20261107 at 073333b04

- Reviewer model: `claude-opus-5-5`. The brief names the producer as Sonnet 5.5, so this is a cross-model review.
- Skills loaded: handover-composition, production-code, code-comments (rag-file not loaded: no scan points are involved).
- Date: 2026-11-07. The worktree was not edited. All builds and mutants ran in two copies under the scratchpad. E:\ was not touched, so rule 7 (TRAINING_DOCS_INDEX.md, AI_AGENT_* handover check) is NOT DONE.

## Headline

- **Inside the ruled scope the patch holds.** 0 false negatives among 18 hand-chosen spellings (absolute, at most two names deep, ending in "-"). 16 rate H, and the 2 at 0 reach nothing on the OS or in the JDK. Every new test fails at base. 4 of 9 mutants are killed. Producer figures reproduce: platform 582/1 failure (known), condenser 221/0.
- **What it does not close, most exploitable first:**
  - F1: the host-property comparisons are lexical. A lint JVM launched by an 8.3 path makes a JDK-home write rate 0. Measured.
  - F2: the Local Settings junction's own 8.3 name `LOCALS~1`, and nested junctions, still reach the temp tree at 0. Measured on this host.
  - F3: PolicyScope's home rule is host-bound and ignores `--define`, so ruling (3) is host-conditional, and an alias is now rated stricter than the tree it aliases.
  - F4: the javadoc's allow-list escape hatch for false positives does not exist.
- **Gate validity:** the corpus and deploy tests cannot see this change (0 of 580 tracked policy files contain a token). The new unit tests are the only gates, and three non-equivalent mutants survive them (F6).
- Two operator questions need only a look at the recommendation text he agreed to (Q2, Q3).

## AS-OF and where the evidence lives

The FASTEST-AGEING section is this one: it describes the uncommitted worktree, which can change under it. Re-read the disk before acting on it.

Subject, measured 2026-11-07 ~11:50:
- `shortnames.patch` md5 `8012B4139B2844548E8B61097F4D799B`, 186 lines. `git diff HEAD` in the worktree is 195 lines and 7 files: the same six tracked hunks plus the staged deletion of `n128-unc-share-dir-read-clean.policy`.
- HEAD `073333b0419f84b76dae3bdd79812d983fbddabf`.
- Worktree `PermissionSets.java` md5 `45CF8D775E820F599C04B652BB0285B4`, `PolicyScope.java` md5 `83C6B89B4C44CC7F12C3185283F63A12`. The pristine copy was checked md5-equal to the worktree for all six changed tracked files (Get-FileHash, both trees).
- Base main sources, from `git show 073333b04:...`: `PermissionSets.java` md5 `546AA267B3F10E9C4B5B5393266ABAA0`.

Copies, all under `C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\`:
- `rev-short\JGDMS`: the pristine copy of the change, made with `robocopy ... /XD target .git`. `rev-short\deploy` is a copy of the worktree root's `deploy\`, which two tests read as `../../../deploy`.
- `rev-short-mut\JGDMS`: the mutant copy. `rev-short\mutants.ps1` is the runner and `rev-short\mutants-summary.txt` holds its results.
- `rev-short\probe\RateProbe.java` calls the production `PermissionSets.danger` (read and write) and `PermissionSets.flagReason` for each name in `names.txt`, in three environments. Outputs: `rate-A.tsv`, `rate-B.tsv`, `rate-XP.tsv`.
- `rev-short\probe\OsProbe.java` runs live `Files.isSameFile` on this host and the JDK's `FilePermission.implies`.
- `rev-short\fx\cb01-18.policy` and `rev-short\fx2\cd01-05.policy` are codeBase fixtures. They were run through the production CLI, `java -cp <classes> org.apache.river.tool.PolicyLint <file>`.

JDK for everything: `C:/Users/peter/Documents/GitHub/DirtyChai/build/windows-x86_64-server-release/images/jdk`, `openjdk version "27-internal"`. Maven 3.9.15, offline.

Probe classpath: the copy's `jgdms-platform\target\classes` comes FIRST, then the condenser classes, then `mvn dependency:build-classpath` output. That output resolves jgdms-platform to the Nov-1 `~/.m2` jar, which the first entry shadows.

## Producer claims, re-run

| Claim | My measurement (population, command) | Verdict |
|---|---|---|
| platform 582 run, 1 failure (CombinerDebugRecursionTest) | 582 run, 1 failure, 1 skipped. The failure is `CombinerDebugRecursionTest.combinerDebugDoesNotRecurseOrLeakPrivateCredentials:80`. Command: `mvn -o -B -pl jgdms-platform,tools/policy-condenser clean test -Dmaven.test.failure.ignore=true` in `rev-short\JGDMS`; log `rev-short\run-change-full.log`, 2 min 54 s | CONFIRMED |
| condenser 221 run, 0 failures, 2 skipped | 221 run, 0 failures, 5 skipped in my copy. Three skips are copy artefacts: `cleanCasesStayClean` and `deployPoliciesStayClean` assume `../../../deploy`, and `testQaBuildFileHasOneConsistentLineEndingStyle` assumes `qa/`. With `deploy\` copied in, `-Dtest=PolicyLintGateTest,PolicyLintTest` gives 31/0/0 and 49/0/2, and both deploy tests PASS (`rev-short\run-deploy-tests.log`). The producer's own surefire XML in the worktree lists only `lockedFileIsP1` and `unreadableDirectoryWarns` as skipped | CONFIRMED |
| Mutants "Local Settings alias neutered" and "mayBeShortName neutered" fail their tests | See the mutant table. I did not re-run these two exact mutants. I ran neighbours of them | see table |
| PolicyScope test not mutant-tested | Done here: MS1, MS2, MS3 | see table |

### Mutants (brief item e), run in `rev-short-mut\JGDMS`, never in the worktree

- Runner: `rev-short\mutants.ps1`. Before each mutant it restores both main files from the pristine copy. It then checks that the replaced string occurs exactly once and runs `mvn -o -B -pl jgdms-platform,tools/policy-condenser clean test -Dtest=<list> -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=true`.
- Scoring rule: a mutant is KILLED if any testcase in the surefire XML of either module has a `<failure>` or `<error>`.
- Every run reported non-zero run counts and no compile error (`mutants-summary.txt`). Populations: platform `PermissionSetsTest` 33; condenser `PermissionSetsTest` 4, `PolicyLintGateTest` 31 (0 skipped, deploy present), and `PolicyLintCorpusTest` 19 (M0 and MS only).

| id | mutation (module-qualified) | result | equivalent? |
|---|---|---|---|
| M0 | base main sources (073333b04) with the new tests | **KILLED by all 3 new tests**: `testAnEightDotThreeNameNearTheRootIsHigh` (`C:\DOCUME~1\- read expected:<2> but was:<0>`), `testLocalSettingsIsAppDataLocal` (bare `AssertionError`), and `junctionAndShortNamesOfAHomeAreWritable`. The corpus test, 19/0, cannot see the patch (see (b)) | n/a |
| MS1 | `PolicyScope.file`: `PermissionSets.aliased(p)` to `filePath(p)` | KILLED (`expected:<FILE_WRITABLE> but was:<FILE_PINNED>`) | n/a |
| MS2 | `PolicyScope.file`: `PermissionSets.aliased(d)` to `filePath(d)` (the property side) | **SURVIVES** | **No.** With `-Duser.home="C:\Documents and Settings\bob"`, the change rates `file:/C:/Users/bob/-` D3, exit 1 (measured, PolicyLint CLI). Under MS2, `[Documents and Settings, bob]` does not start with `[Users, bob]`, so it would be PINNED (by reading, not run) |
| MS3 | `PolicyScope.file`: `if (false && PermissionSets.mayBeShortName(f))` | KILLED | n/a |
| MP1 | `PermissionSets.mayBeShortName` tests the first name only | **SURVIVES** | **No.** `C:\data\PETERF~1\-` is H/H on the change (`rate-A.tsv`) and would be 0/0. The ruling says "among its first two names". The test's only second-position entry, `C:\Users\PETER~1\-`, is HIGH through the one-user's-home rule whether or not the 8.3 rule exists |
| MP2 | `PermissionSets.aliased`: Local Settings fold drops the `Users` first-name guard | **SURVIVES** | Practically yes: the assertion on `C:\data\bob\Local Settings\Temp\-` passes either way. The guard itself costs a false negative for a relocated profile. In env R (home `D:\Profiles\bob`, tmpdir `D:\Profiles\bob\AppData\Local\Temp\`), `D:\Profiles\bob\Local Settings\Temp\-` write rates **0**, while `...\AppData\Local\Temp\-` rates H (`RateProbe2 names-reloc.txt R`) |
| MP3 | Local Settings fold drops `f.absolute` | **SURVIVES** | Yes in `fileReach`: a relative name returns null before any directory test unless it starts with `..`. So the assertion on `Users\bob\Local Settings\Temp\-` cannot fail |
| MP5 | Local Settings fold drops `!f.unc` | KILLED (`testLocalSettingsIsAppDataLocal`) | n/a |
| MP6 | `mayBeShortName` drops `!"-".equals(f.wildcard)` | **SURVIVES** | Yes in `fileReach`, where the call sits inside `if ("-".equals(f.wildcard))`. **No in `PolicyScope.file`**, where the call is unguarded: `file:/C:/DOCUME~1/x.jar` would become FILE_WRITABLE (by reading). No test names a short-name codeBase that is not "-" |

Tally: 4 of 9 KILLED (M0, MS1, MS3, MP5). Of the 5 survivors, MS2, MP1 and MP6 are non-equivalent somewhere, and MP2 and MP3 are equivalent in practice. The two mutants the producer reported were neutering the alias and neutering `mayBeShortName`. I did not re-run those exactly; MS1, MS3 and M0 cover the same ground.

## Brief (a): false negatives of the 8.3 rule inside its ruled scope (at most two names, "-")

Population: 18 spellings, group P of `probe\names.txt`. Rated through `danger` and `flagReason` in env A (user.home `C:\Users\peter`, tmpdir `C:\Users\peter\AppData\Local\Temp\`, java.home `C:\Program Files\Java\jdk-21`), output `rate-A.tsv`.

- **16 of 18 rate H/H and FLAG.** These cover the long-path prefix (`\\?\UNC\localhost\C$\DOCUME~1\peter\-`), UNC drive share (`\\localhost\C$\DOCUME~1\-`), `..` (`C:\x\..\DOCUME~1\-`, `C:\DOCUME~1\peter\..\-`), `${user.home}${/}..${/}..${/}DOCUME~1${/}-`, one and two trailing dots, `-\.` tails, case (`C:\docume~1\PETER\-`), the second-position-only `C:\data\PETERF~1\-`, and `C:\Documents and Settings\PETERF~1\-`. That last one is H through the one-user's-home rule. The device form `//./C:/DOCUME~1/-` is H through the device rule. `C:\DOCUME~1\peter \-` is also H; the JDK refuses that name (next bullet), so this is a conservative over-rating.
- **The 2 that rate 0/0 are both inert, so neither is a false negative:**
  - `C:\DOCUME~1...\-`: on this host `C:\DOCUME~1...\peter` does not exist (`OsProbe`, `Files.exists` false). Win32 strips one trailing dot, not three. `DOCUME~1..` is also absent on the OS, though it is rated H.
  - `C:\DOCUME~1 \-`: the JDK refuses the name. `Paths.get("C:\DOCUME~1 \peter")` throws `InvalidPathException: Trailing char < >`, and `new FilePermission("C:\DOCUME~1 \-","read").implies(... "C:\DOCUME~1 \peter\x")` is **false** (OsProbe). The grant reaches nothing.
- The JDK does not resolve the short name: `FilePermission("C:\DOCUME~1\-")` implies `C:\DOCUME~1\peter\x` (true) and does not imply `C:\Users\peter\x` (false). So the short name is a second door into the same tree, and the OS opens it: `Files.isSameFile("C:\DOCUME~1\peter","C:\Users\peter")` is true.
- The aliases exist on this host (`cmd /c dir /x /a C:\` and `C:\Users\peter`): `DOCUME~1` is a junction to `C:\Users`; `PROGRA~1`, `PROGRA~2` and `PROGRA~3` are Program Files, Program Files (x86) and ProgramData; `C:\Users\peter\LOCALS~1` is a junction to `C:\Users\peter\AppData\Local`; `APPLIC~1` is a junction to AppData\Roaming. `fsutil 8dot3name query C:` was refused with access denied (not admin), so the volume setting is UNVERIFIED.

**Result: zero false negatives inside the ruled scope.** Population: 18 spellings, one host, env A. The first link where the response would go to zero is SCENE: every spelling was hand-chosen, with no generator. That is a hunt, not a census.

## Findings, most exploitable first

### F1. The JDK-home and temp write rules compare against the HOST's property spelling, and a JVM launched through an 8.3 path reports java.home in 8.3 form. The long spelling of the JDK home then rates 0 (CONFIRMED mechanism, pre-existing, outside the ruled scope)

- Code: `PermissionSets.fileReach` (jgdms-platform) builds `dirs.add(new String[]{"the JDK home", property(env, "java.home")})`, then `FilePath dir = aliased(d[1])` and `isPrefix(dir.segments, f.segments)`. That is a lexical comparison against whatever spelling the property holds.
- Measured: launching the same JDK as `C:\Users\peter\DOCUME~1\GitHub\DIRTYC~1\build\WINDOW~1\images\jdk\bin\java.exe` gives `java.home = C:\Users\peter\DOCUME~1\GitHub\DIRTYC~1\build\WINDOW~1\images\jdk` (`-XshowSettings:properties`).
- `RateProbe names-host.txt HOST`, write ratings:

| name (write) | lint JVM launched by the long path | lint JVM launched by the 8.3 path |
|---|---|---|
| `...\Documents\GitHub\DirtyChai\build\windows-x86_64-server-release\images\jdk\conf\security\java.security` | H | **0** |
| `...\Documents\...\images\jdk\-` | H | **0** |
| `...\DOCUME~1\GitHub\DIRTYC~1\build\WINDOW~1\images\jdk\conf\security\java.security` | **0** | H |
| `${java.home}${/}conf${/}security${/}java.security` | H | H |

- The same happens without launching: env XP (`java.home=C:\PROGRA~1\Java\jdk-21`) rates `C:\Program Files\Java\jdk-21\conf\security\java.security` write **0**, and env A rates `C:\PROGRA~1\Java\jdk-21\conf\security\java.security` write **0** (`rate-XP.tsv`, `rate-A.tsv`).
- Failure scenario: a deployer sets `JAVA_HOME=C:\PROGRA~1\Java\jdk-21`, a common way to avoid the space. The lint then runs under that JVM on `grant { permission java.io.FilePermission "C:\\Program Files\\Java\\jdk-21\\conf\\security\\java.security", "write"; };`, and the file has no D-finding for that entry. The grantee can rewrite `java.security` (for example `policy.url.n`), which takes effect at the next start.
- Temp, the same mechanism: env B (`tmpdir=C:\Users\PETERF~1\AppData\Local\Temp\`, home `C:\Users\peter.firmstone`) rates `C:\Users\peter.firmstone\AppData\Local\Temp\-` write **0** (`rate-B.tsv`).
  - PLAUSIBLE, not measured here, whether real hosts report an 8.3 tmpdir. This host's user name `peter` has no short name.
  - From memory and unverified, GitHub Windows runners report `C:\Users\RUNNER~1\AppData\Local\Temp`.
  - **The operator can settle it** with `java -XshowSettings:properties -version | findstr tmpdir` on a deployment host whose user name is longer than 8 characters.
- Not introduced by this patch (the base has the same comparison). It is, however, the same 8.3 family the ruling addresses, and the brief's item (a) names "user.home given as a short name in the environment". The ruled shape rule cannot catch it: the names are deeper than two segments and contain no short segment.

### F2. Legacy-junction spellings deeper than two names still reach the temp tree for writing and rate 0, including the 8.3 alias of the very junction ruling (2) folds (CONFIRMED, residual of the ruled scope)

The table below compares env A ratings (`rate-A.tsv`) with this host's OS (`OsProbe`, `Files.isSameFile` against `C:\Users\peter\AppData\Local\Temp`). The long spelling `C:\Users\peter\AppData\Local\Temp\-` and its fold `C:\Users\peter\Local Settings\Temp\-` are write-H.

| name (write) | rating | same file as the temp dir? |
|---|---|---|
| `C:\Users\peter\LOCALS~1\Temp\-` | 0/0 | true |
| `C:\Users\peter\Local Settings\Application Data\Temp\-` | 0/0 | true |
| `C:\Users\peter\AppData\Local\Application Data\Temp\-` | 0/0 | true |
| `C:\Users\peter\AppData\Local\APPLIC~1\Temp\-` | 0/0 | true |
| `C:\DOCUME~1\peter\AppData\Local\Temp\-` | 0/0 | true |
| `C:\DOCUME~1\peter\Local Settings\Temp\-` | 0/0 | true |
| `C:\Users\peter\LOCALS~1\-` (vs `C:\Users\peter\AppData\Local\-`, which is 0/H) | 0/0 | `LOCALS~1` = AppData\Local: true |

- Why:
  - `PermissionSets.aliased` folds only the literal `s.get(2).equalsIgnoreCase("Local Settings")` under a first name `Users`, so `LOCALS~1` and a first name `DOCUME~1` escape it.
  - `PermissionSets.mayBeShortName` stops at `f.segments.size() > 2`.
  - `AppData\Local\Application Data` is a self-junction (`dir /x /a C:\Users\peter\AppData\Local` shows `APPLIC~1 Application Data [C:\Users\peter\AppData\Local]`), so the family is unbounded lexically.
- PolicyScope, same family (PolicyLint CLI, this host): `file:/C:/Users/peter/LOCALS~1/-`, `file:/C:/DOCUME~1/peter/AppData/Local/-`, `file:/C:/Users/peter/AppData/Local/Application%20Data/-` and `file:/C:/Users/peter/Local%20Settings/Application%20Data/-` each **exit 0**, while `file:/C:/Users/peter/AppData/Local/-` gives D3, exit 1 (fixtures cb09, cb13, cb12, cd04, cd05).
- Failure scenario: `grant { permission java.io.FilePermission "C:\\Users\\svc\\LOCALS~1\\Temp\\-", "write"; };` passes the lint, and the grantee writes anywhere in the service's temp tree, which the catalogue rates HIGH in its long spelling.
- **Operator question Q1:** the ruling bounds the shape rule to the first two names and folds only `Local Settings`. Should `LOCALS~1` (the 8.3 name of that same junction, present on this host) and a `DOCUME~1` first name also be folded? Any lexical rule leaves the self-junction family open.

### F3. PolicyScope's home rule is host-bound and ignores `--define`, so ruling (3) holds only when the lint user's own home sits in C:\Users, and the new host-blind 8.3 rule rates an alias STRICTER than the tree it aliases (CONFIRMED)

- Code: `PolicyScope.file` (policy-condenser) reads `String d = System.getProperty(prop);` for `user.home`, `java.io.tmpdir` and `user.dir`. It never reads `env`, and it has no host-blind `Users\<name>` or `home/<name>` rule. `PermissionSets.fileReach` has both.
- Results:
  - With `--define user.home=C:\Users\bob`, `file:/C:/Users/bob/-` (cb15) exits **0**. With the JVM property `-Duser.home=C:\Users\bob` it gives D3, exit 1.
  - On this host (user.home `C:\Users\peter`), `file:/C:/Users/bob/-` (cb15) and `file:/C:/Documents%20and%20Settings/bob/-` (cb16) exit **0**, while `file:/C:/DOCUME~1/bob/-` (cb17) gives D3.
  - With a lint host whose home is not under C:\Users (`-Duser.home=/home/lint -Djava.io.tmpdir=/tmp -Duser.dir=D:\lint\work`, emulating Linux properties on Windows), these exit **0**: `file:/C:/Users/-` (cb05), `file:/C:/Documents%20and%20Settings/-` (cb04), `.../Documents%20and%20Settings/peter/-` (cb14), `.../Users/peter/Local%20Settings/-` (cb08) and `.../Users/peter/AppData/Local/-` (cb12). Meanwhile `file:/C:/DOCUME~1/-`, `.../DOCUME~1/peter/-`, `.../DOCUME%7E1/-`, `file://localhost/C:/DOCUME~1/-`, `.../DOCUME~1/bob/-` and `.../PROGRA~1/MyApp/-` give D3, exit 1.
- The new test cannot see this. `PolicyLintGateTest.junctionAndShortNamesOfAHomeAreWritable` sets `System.setProperty("user.home", "C:\\Users\\alice")`, and its own comment says why: "since the over-home rule reads the host's own". Under the null it reads green on any host only because it pins the host.
- Failure scenario: `grant codeBase "file:/C:/Users/svc/-" { permission java.lang.RuntimePermission "createClassLoader"; };` linted by a different Windows user, or by a Linux CI job, is FILE_PINNED (no D3). The same grant written `file:/C:/DOCUME~1/svc/-` is flagged.
- production-code, one definition per concept: "is this a user home, or above one" now has two definitions. `PermissionSets.fileReach` is host-blind for Users/home, uses `env` and is case-insensitive. `PolicyScope.file` is host-bound and ignores `env`. The patch extends the second with the first's alias reading, not with its home rule. Even the prefix test is duplicated: private `PolicyScope.startsWith(List,List)` and private `PermissionSets.isPrefix(List,List)` are the same loop.
- Pre-existing, but the patch makes it a visible inconsistency.
- **Operator question Q2:** was ruling (3) meant to make Documents and Settings WRITABLE on every lint host, as ruling (1) is host-blind? If so, PolicyScope needs the same host-blind home rule. Settled by reading the recommendation text the operator agreed to.

### F4. The false-positive escape hatch the new javadoc names does not exist (CONFIRMED by reading and a run)

- Code: `PermissionSets.mayBeShortName` javadoc: *"so the shape alone is rated and a manual allow-list row clears a real directory of that shape."*
- No such row can exist:
  - `PolicyLint.run` takes only `--strict --audit-input --fix-hint --require-defined --help --format= --provider= --define --glob`. `Select-String -Pattern '(?i)allow'` over PolicyLint.java, PolicyVerdict.java and PolicyScope.java gives 0 hits.
  - The corpus allow-list `cross-check-allow.tsv` says in its header that rows must not rate HIGH: *"...and none may rate HIGH in the catalogue."*
  - `PolicyLintCorpusTest.judgeListed` (javadoc: *"a listed permission the catalogue rates HIGH is a finding whatever else is true of it: no row and no rule below accepts it"*) enforces that at `if (d != null && d.severity >= PermissionSets.HIGH)`.
  - ListPromotion's `--allow-held` excuses HOLD entries per artefact; FLAG never refuses, so it needs no row.
- Consequence: a real directory of that shape has no clearing path (measured: `C:\PROGRA~1\MyApp\-` read rates H/H and FLAG; `file:/C:/PROGRA~1/MyApp/-` gives D3, exit 1 on both host settings).
- **STOP-AND-SURFACE Q3:** which allow-list did the ruling mean? The operator can settle it by reading the recommendation he agreed to on 2026-11-07. If it was a future PolicyLint option, the javadoc should say "none yet".

### F5. A home's `*` spelled through an alias rates 0 where the long spelling rates M/H (CONFIRMED, outside the ruled "-" scope)

- Env A: `C:\DOCUME~1\peter\*` is 0/0, while `C:\Users\peter\*` and `C:\Documents and Settings\peter\*` are M/H.
- Env B: `C:\Users\PETERF~1\*` is 0/0, while `C:\Users\peter.firmstone\*` is M/H.
- Write to the files directly in a home includes files such as `.gitconfig` and `_netrc`.
- The test comment pins `"*" names ... are unrated` as part of the ruling. This finding states the cost of that choice.

### F6. Test gaps: three non-equivalent mutants survive, and three of the four negative Local Settings assertions cannot fail (CONFIRMED by mutants, LOW. MS2 and MP1 would let an under-rating through; MP6 would let an over-rating through)

- **MS2 survives.** `PolicyLintGateTest.junctionAndShortNamesOfAHomeAreWritable` never puts an alias in a host property, so the property-side `aliased(d)` in `PolicyScope.file` is untested. `PermissionSetsTest` does have the analogue for `fileReach`: the comment *"the working home is compared in the same form"* sits beside `env.setProperty("user.home", "C:\\Documents and Settings\\bob\\h")`.
- **MP1 survives.** The ruling's "second name" is never pinned by an entry the Users or home rules would not already rate.
- **MP6 survives.** No test checks that a short-name codeBase that is not "-" stays PINNED.
- **`PermissionSetsTest.testLocalSettingsIsAppDataLocal`:**
  - The `assertNull`s for `C:\data\bob\Local Settings\Temp\-` (MP2), `Users\bob\Local Settings\Temp\-` (MP3) and, by reading, `C:\Users\Local Settings\Temp\-` hold for right and wrong code alike. No directory in its env sits at the wrongly folded spelling.
  - Only the UNC assertion, `\\Users\bob\Local Settings\Temp\-`, is load-bearing (MP5).
  - Its `assertNotNull`s carry no message and do not check severity, so a failure prints a bare `AssertionError` (M0 log, `rev-short\mut-M0-base-main.log`).
- **`junctionAndShortNamesOfAHomeAreWritable`** failures do not name the codeBase (MS1 log: `expected:<FILE_WRITABLE> but was:<FILE_PINNED>`).

### F7. Comment accuracy (CONFIRMED by reading, LOW)

- `PermissionSets.aliased` javadoc still says *"{@link #filePath} with two OS aliases of a ruled tree"*, and describes only Documents and Settings and X$. The patch adds a third, Local Settings, documented only in an inline comment.
- `PermissionSets.danger` javadoc lists *"a Documents and Settings first name is read as Users and a <letter>$ share as that drive"*. It omits Local Settings and the 8.3 shape rule.
- The `PermissionSets.fileReach` javadoc's "Wide are:" list omits the 8.3 rule.
- `mayBeShortName` says *"Deeper names are left to the named-directory ruling"*, which names no ruling, date or source.
- The reason text *"which may stand for a user home"* is shown for `C:\PROGRA~1\-`, `/opt/v1~2/-` and `D:\BUILD~1\-` (`rate-A.tsv`). It is true by the ruling's letter but misleading in a report.
- Nit: `SHORT_NAME` is declared with the fully qualified type `java.util.regex.Pattern`, although `Pattern` is already imported (`import java.util.regex.Pattern;`, PermissionSets.java:31).

### F8. Drive-relative codeBase asymmetry (CONFIRMED, LOW, pre-existing)

`file:C:Users/-` gives D3, "above user.home", because PolicyScope's home check compares segments whatever `f.absolute` says. `file:C:DOCUME~1/-` exits 0, because `mayBeShortName` requires `f.absolute` (fixtures cd01, cd02).

## Brief (b): false positives, and whether any fixture or ruled tree changes rating

- Shape-only false positives at depth 2 or less, `rate-A.tsv`: `C:\PROGRA~1\MyApp\-`, `C:\PROGRA~2\-`, `C:\PROGRA~3\MyApp\-`, `D:\BUILD~1\-`, `/opt/app~1/-` and `/opt/v1~2/-` all rate H/H with FLAG, on every host. As codeBases, `file:/C:/PROGRA~1/MyApp/-` gives D3 (cb06). Depth 3 (`C:\PROGRA~1\app\lib\-`) stays unrated, as pinned. With F4, none of these can be cleared.
- **No tracked fixture and no ruled tree changes rating.**
  - Census: `git grep -n -i -E '~[0-9]|%7e[0-9]|local( |%20)settings|documents( |%20)and( |%20)settings|applic~|locals~' -- '*.policy' '*.policy.*' '*PERMISSIONS.LIST' '*.tsv'` from the worktree root, over 580 tracked files (`git ls-files` count), gives 1 hit: `n126-docsettings-users-read.policy`.
  - That hit is a FilePermission name that base `fileReach` already folded, under `grant {`. It is not a codeBase.
  - A wider grep over all tracked files for FilePermission or codeBase lines with a token finds only docs and the new tests.
  - The argument that makes the census complete: the patch changes behaviour only for names containing `~digit` (`mayBeShortName`), a third name `Local Settings`, or, in PolicyScope only, a first name `Documents and Settings`. PolicyScope's X$ fold is unreachable, because UNC names return earlier at `if (m >= 2) return share(...)`. The other source of change is a host property that contains one of these. On this host none does (`-XshowSettings:properties`).
- **So the corpus and deploy tests are INERT for this change.** `PolicyLintCorpusTest` (19 tests), `deployPoliciesStayClean` and `cleanCasesStayClean` all pass, and they read the same with or without the patch: the link that is zero is SCENE, with 0 of 580 files containing a token. The producer's "condenser 221/0" therefore carries evidence about the new rules only through `junctionAndShortNamesOfAHomeAreWritable`.
- n128 removal: the file was in the base commit (`git show HEAD:.../n128...policy` prints `grant codeBase "file:/opt/app/-"` and `//h/share/data/-` read). Remaining references to it are in `docs/REVIEW-aliases-opus-2026-11-07.md` only (`git grep -n n128`). Dropping it removes no assertion that could fail: it is the prior review's F5, and the operator chose "drop" by the brief.

## Brief (c): Local Settings

- **Target correct:** `dir /x /a C:\Users\peter` shows `LOCALS~1 Local Settings [C:\Users\peter\AppData\Local]`. `Files.isSameFile` of `...\Local Settings\Temp` and the temp dir is true.
- **Only under a profile:** the fold requires an absolute, non-UNC, non-device name whose first name is `Users` and whose third name is `Local Settings`. It also folds `Users\Default`, `Users\Public` and so on, and `/Users/<x>/...` on a macOS reading. Each is harmless, because only an env directory at that spelling would match: `C:\Users\Default\Local Settings\Temp\-` is 0/0, and `/Users/peter/Local Settings/Temp/-` is write-H only because env A's tmpdir is that user's (`rate-A.tsv`). The `Users` guard misses a profile relocated off `Users`: in env R, `D:\Profiles\bob\Local Settings\Temp\-` write rates 0 (MP2 row). Whether Windows creates the junction in a relocated profile is UNVERIFIED here.
- **Existing ratings changed:** only names with a third name `Local Settings`. In env A, `C:\Users\peter\Local Settings\-` goes to 0/H and `...\Local Settings\Temp\*` to 0/M. Both are correct, because Local Settings is the temp directory's parent. `C:\Users\peter\Local Settings\Application Data\Temp\-` stays 0/0 before and after: no unintended change, but a residual (F2).
- **The fold is env-relative:** in env B (tmpdir 8.3) and env XP the folded name rates 0/0 (F1 mechanism).
- **Flag set:** `flagReason`, `wholeTree`, `nameReach`, `fileReach` and `aliased` share the path (grep of `src/main`). The fold produces only unprefixed temp reasons, and `wholeTree` flags only `[H]`, so no FLAG changes. Measured: every Local Settings row has `-` in the FLAG column, as does the long spelling. Every 8.3 rule hit is FLAGged. ListPromotion reaches this through `PermissionSets.flagReason(e)` (ListPromotion.java:429).

## Brief (d): PolicyScope and the public API

- `aliased()` in PolicyScope changes only codeBases with a first name `Documents and Settings` or a profile `Local Settings`, and property values containing those or a `\\h\X$\` user.dir. The last is the correct direction. The X$ fold on the codeBase side is unreachable (see (b)).
- Public surface: `PermissionSets` public static members go from 29 at base to 31 (`Select-String '^\s+public static'`): `aliased` and `mayBeShortName`.
  - `filePath` was already public and returns the same mutable `FilePath`. `aliased` returns a fresh instance per call, and no static `FilePath` exists (`Select-String 'static[^(]*FilePath'` matches only the class and the two factory methods), so nothing shared can be mutated.
  - No `module-info.java` exists under `jgdms-platform/src` (Get-ChildItem -Recurse).
  - jgdms-platform is in `check-api-compat.sh` ARTIFACTS (line 97, japicmp, binary-incompatible only). The additions do not break it, but narrowing them later would be a break needing a ledger entry.
  - Runtime use of PermissionSets in production is `VerdictPermissionMapper.META_PERMISSION_CLASSES = PermissionSets.META` only (grep), so the file rules are tool-only.

## Brief (e): test quality, summarised

- Each new test fails at base (M0).
- The PolicyScope test kills the codeBase-side `aliased` mutant (MS1) and the `mayBeShortName` mutant (MS3), but not the property-side `aliased` mutant (MS2).
- The full table and the surviving mutants are above and in F6.
- Runtime tier: the three new methods each take 0.002 to 0.010 s (surefire XML), so they are fast default-suite tests.

## Operator questions, collected

- **Q1 (F2):** should the Local Settings fold also take its 8.3 name `LOCALS~1`, a `DOCUME~1` first name, and profiles outside `Users` (MP2, env R)?
- **Q2 (F3):** was ruling (3) meant to make Documents and Settings WRITABLE on every lint host? If so, PolicyScope needs `fileReach`'s host-blind home rule, and arguably `env` (`--define`) as well. Settled by the recommendation text he agreed to.
- **Q3 (F4), STOP-AND-SURFACE:** which "manual allow-list row" clears a real 8.3-shaped directory? None exists in PolicyLint, and the corpus allow-list forbids HIGH rows. Settled by the same text.
- **F1:** does any deployment host report `java.home` or `java.io.tmpdir` in 8.3 form? Check with `java -XshowSettings:properties -version` on that host, launched exactly as the lint is launched there.

## UNVERIFIED and not checked

- **Linux:** not run. The F3 emulation sets Linux-like `user.home` and `java.io.tmpdir` on the Windows JDK. A Linux JDK's `filePath` path is host-blind by design and was not exercised.
- Whether deployment hosts report an 8.3 `java.io.tmpdir` or `java.home` (F1). The mechanism is measured here; the frequency is the operator's check.
- `fsutil 8dot3name query C:` (access denied without admin).
- PolicyLint ran on the platform `ConcurrentPolicyFile`. The DirtyChai provider is not exported to the unnamed module, as the output shows: "fell back from au.zeus.jdk.authorization.policy.ConcurrentPolicyFile: IllegalAccessException". The D3 findings above come from the symbolic half (PolicyScope), which does not depend on the provider.
- Rule 7: TRAINING_DOCS_INDEX.md and the AI_AGENT_* handover check are on E:\, which this brief forbids.
