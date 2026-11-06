# REVIEW: OS-alias patch (aliases.patch) on JGDMS-aliases-20261107 at 685e1e541

- Reviewer model: `claude-opus-5-5`. The producer was Sonnet 5.5, so this is a cross-model review.
- Skills loaded: handover-composition, production-code, code-comments.
- Date: 2026-11-07. Read-only: nothing in the worktree was edited, no mvn was run, and E:\ was not touched.

## AS-OF and where the evidence lives

Subject:
- `aliases.patch`, md5 `a6a6061764fa24c2b61677f651900ea2`.
- I diffed it against `git diff` in the worktree. The tracked hunks are identical. The patch also carries the three untracked fixture files n126 to n128.
- The patched `PermissionSets.java` in the worktree has md5 `5cf1cdff8d60098ca29001ab7532ca14`.
- The base is `git show 685e1e541:.../PermissionSets.java`, md5 `0355c8216c5aef4ed5e1bef3c99b6c3b`.

How I built and ran it:
- Both versions were compiled with `javac --release 21` into a scratch folder: `...\scratchpad\rev-aliases\cls-{base,new}`.
- They were compiled against `~/.m2/.../jgdms-platform-4.0.0-SNAPSHOT.jar` (Nov 1), copied into scratch.
- They run on the DirtyChai JDK `27-internal`.
- `tools/policy-condenser` main was compiled from `git archive 685e1e541` into `rev-aliases/cls-tool`. That module is unchanged by the patch.

Scratch evidence, all under `...\scratchpad\rev-aliases\`:
- `probe/RevProbe.java` runs base and patched side by side through separate class loaders and calls the production `PermissionSets.danger` and `flagReason` entry points. It also runs the JDK's `FilePermission.implies` and live OS `Files.isSameFile` checks.
- `probe/names-hunt.txt` holds 80 names. `probe/names-prop.txt` holds 15. `probe/corpus-names.txt` and `probe/corpus-rate.tsv` hold 45 names.
- `mutants.sh` and `mut/` hold 13 mutants.
- `fx/` holds the fixtures and the variant policies.

## Producer claims, re-run

| Claim | My measurement | Verdict |
|---|---|---|
| Differential 0/15,322 JDK-covered rows and 0/992 controls, unchanged | Patched: `REJECTION RATE 0 / 15322 ... narrow controls 0 / 992`, 32 tests OK. Base, same tests: the same 0/15322 and 0/992. Command: `java -cp "cls-new;cls-test;platform.jar;junit;hamcrest" org.junit.runner.JUnitCore ...PermissionSetsTest ...PermissionSetsFilePermissionDifferentialTest` | CONFIRMED |
| F1 to F5 spellings were already flagged at base; only the undefined property was not | The base run of `testFlagReasonReadsAFileNameAsDangerDoes` first fails at `flagReason C:\Documents and Settings\-`. That name sits after every F1 to F5 entry in the array, so all F1 to F5 rows passed at base | CONFIRMED |
| The new tests fail at base | Base fails 4 of 32: the 4 new test methods | CONFIRMED |
| Mutants M1 (drop Documents and Settings) 5 fail; M2 (drop X$) 4 fail; M3 1; undefined-property revert 1 | Over the two jgdms-platform test classes only: M01 fails 4, M02 fails 3, M05 (undefined revert) fails 1, M14 (flag skips the alias) fails 1. The one-test gap on M1 and M2 is plausibly the PolicyLintCorpusTest rows n126 and n127, which I did not run | CONFIRMED for the platform tests; the corpus part is UNVERIFIED-NEEDS-BUILD |
| CLI: n126 and n127 exit 1; n128 and `\\h\ADMIN$\Users\x` exit 0 | Patched: n126 = 1 (D1), n127 = 1 (D1), n128 = 0, v5 `\\h\ADMIN$\Users\x` = 0. Base: n126 = 0, n127 = 0. Ran in both default and `--strict` mode | CONFIRMED, but n128 cannot fail (finding F5) |
| Surviving mutants: the f.absolute, !f.unc and !f.device guards | All three survive (32/32 OK). Only M07 (f.absolute) is equivalent on what I probed. M08 (!f.unc) and M09 (!f.device) each have an input that changes the rating (finding F6). M12 (FLAG with write=true) also survives and is equivalent | CONFIRMED |
| The ruled sentinels are unchanged | `lib/-` and `lib\-` are 0/0 at base and patched. `..\..\-`, `C:..\..\-` and `../-` are M/H at both. `\\h\share\Users\-` and `//h/share/data/-` are 0/0. `\\h\share\-` (v3) exits 1. `\\.\UNC\h\C$\-` is H/H. `\\?\unc\h\c$\users\-` is now H/H. `git diff --stat` of cross-check-allow.tsv is empty, and its row 21 `f14-rel-sub-write` is present | CONFIRMED |

The ruling-(1) spellings in the brief all rate H read and H write when patched. Source: `RevProbe rate`, names-hunt, 80 names, 30 of them changed from base. Examples:
- `\\h\c$\Users.\-`, `//?/UNC/h/C$/Users/-` and `\\h\C$\-\.`
- `C:\documents AND settings\-`, `C:\Documents and Settings.\-` and `C:\Documents and Settings\.\-`
- `\\h\C$\Documents and Settings\alice\-` and `\\?\C:\Documents and Settings\-`
- `\\h\C$${/}Users${/}-` and `${user.home}${/}..${/}..${/}Documents and Settings${/}-`

The drive-relative `C:Documents and Settings\-` stays 0/0, as the ruling requires.

## Findings, most exploitable first

### F1. 8.3 short names reach the Users tree and rate 0 (CONFIRMED, pre-existing, outside the ruled spelling)

- Rating: `C:\DOCUME~1\-` and `C:\DOCUME~1\alice\-` are 0 read and 0 write at both base and patched (`RevProbe rate`). On the entry-line path ListPromotion uses, `flagReason("(java.io.FilePermission \"C:\\DOCUME~1\\-\" \"read\")")` returns null at both.
- PolicyLint: `fx/v7-open-docume1.policy` exits 0 at both.
- OS (this host, n = 1): `Files.isSameFile("C:\DOCUME~1", "C:\Users")` is true, and `C:\DOCUME~1\peter` is the same file as `C:\Users\peter`.
- JDK: `FilePermission("C:\DOCUME~1\-")` implies `C:\DOCUME~1\a\f` (true) but not `C:\Users\a\f` (false). So a grant written this way lets code read every profile through the short name.
- The gap is systemic (`os.txt` run, this host):
  - `C:\PROGRA~1` is the same file as `C:\Program Files`. That reaches the JDK-home write rule wherever the JDK is installed there. It is not exercised here, because this host's java.home is under `C:\Users`.
  - `C:\Users\peter\LOCALS~1\Temp` is the same file as the temp dir, which reaches the temp write rule.
  - `C:\Users\peter\APPLIC~1` is the same file as AppData\Roaming.
  - `C:\USERS~1` does not exist, because `Users` is already a valid 8.3 name.
- Neither the JDK nor the patch reads 8.3 names lexically.
- **Operator question Q1:** should a segment shaped like an 8.3 alias (`~` followed by digits) be ruled? The operator can settle it by looking at `dir /x C:\` and `dir /x C:\Users\<user>`, and `fsutil 8dot3name query C:`, on a deployment host.

### F2. The per-profile legacy junction reaches the temp tree for writing and rates 0 (CONFIRMED, pre-existing, outside scope)

- `C:\Users\peter\Local Settings\Temp\-` (write) and `C:\Users\peter\LOCALS~1\Temp\-` (write) rate 0/0 at both base and patched.
- The real temp dir `C:\Users\peter\AppData\Local\Temp\-` (write) rates H.
- OS: `Files.isSameFile` of the junction spelling and the real temp dir is true.
- This is the same family as Documents and Settings, one level down. It is outside the brief, so I surface it only. Artefact for the operator: `dir /aL C:\Users\<user>`.

### F3. PolicyScope's codeBase user-home check does not see the junction (CONFIRMED, outside the literal ruling)

- `PolicyScope.file` still calls the unaliased `PermissionSets.filePath(p)` and `PermissionSets.filePath(d)`.
- `fx/cb1-users-home-ccl.policy` (`grant codeBase "file:/C:/Users/peter/-"` with `createClassLoader`) gives D3 and exit 1.
- `fx/cb2-docsettings-home-ccl.policy` (`file:/C:/Documents%20and%20Settings/peter/-`, same permission) gives exit 0 with no finding.
- I ran this on the patched class only. It is the same at base by construction, because the patch does not touch PolicyScope or `filePath`, and `testDocumentsAndSettingsIsUsers` pins `filePath` as unaliased.
- **Operator question Q2:** does "every Users rule" cover the codeBase user.home rule?

### F4. New over-rating: `..` across a share combined with X$ gives a reading no host produces (CONFIRMED, LOW, fails safe)

- `\\h\x\..\C$\Users\-` and `\\h\C$\..\C$\Users\-` were 0/0 at base. Patched they are H/H and FLAG.
- PolicyLint `fx/v6-open-dotdot-share.policy` exits 1 (D1) patched and 0 at base.
- The JDK clamps at the share. `FilePermission("\\h\x\..\C$\Users\-")` implies `\\h\x\C$\Users\a\f` (true), not `\\h\C$\Users\a\f` (false). The same pattern holds for `\\h\C$\..\C$\...`.
- The OS also clamps (localhost): `\\localhost\C$\..\C$\Users\peter` has canonical form `\\localhost\C$\C$\Users\peter` and does not exist.
- Cause: `filePath` pops the share on `..`. That is the N3 "wider" reading, taken from Unix, where `//` is `/`. The X$ alias is Windows-only, so the combination is a chimera.
- Under both host readings these names are a named directory below a share, which the ruling says stays unrated.
- **Operator question Q3:** accept this over-rating, or have `aliased()` skip X$ when a `..` crossed the share? Any fix must not undo N3.

### F5. Fixture n128 cannot fail (CONFIRMED, LOW)

- n128 is `grant codeBase "file:/opt/app/-"` with `//h/share/data/-` read.
- A grant pinned to a local directory reports nothing, whatever it holds. Compare n107: HIGH `createClassLoader`, expected clean.
- Measured on the patched class with the same codeBase:
  - `//h/share/-`, a HIGH whole share (`fx/v1`), exits 0.
  - `C:\Users\-` (`fx/v4`) exits 0.
  - The same whole share under `grant {` (`fx/v3`) exits 1.
- So n128 reads 0 for both right and wrong code, and its `cross-check-no-listing.tsv` row exists only because of the pinned codeBase.
- The corpus convention for a clean, unscoped FilePermission sentinel is n125: `grant {` plus a `cross-check-allow.tsv` row (row 29). The brief froze that file.
- The rating itself is pinned by `PermissionSetsTest`: `//h/share/data/-` and `\\h\share\Users\-` are 0/0.
- **Operator question Q4:** drop n128, or allow one allow-list row?

### F6. Two surviving guard mutants are not equivalent (CONFIRMED, LOW: test gap, over-rating direction only)

- M08, which removes `!f.unc` (patched vs mutant):
  - `\\Documents and Settings\peter\*` goes from 0/0 to M/H.
  - `\\Documents and Settings\peter\...\images\jdk\-` (write) goes from 0 to H.
- M09, which removes `!f.device`: `//./Documents and Settings/peter/.../jdk/bin/java.exe` (write) goes from 0 to H.
- No test names these inputs.
- M07 (f.absolute) is equivalent on my inputs. The reason: `fileReach` rates a relative name only when it has a leading `..`.
- M12 (FLAG asks with write=true) is equivalent. No `[H]` reason in `fileReach` depends on `write`, because the user home is checked first and write adds only unprefixed reasons. So "FLAG does not look at the action" is structural, not a choice the tests could catch.

### F7. Comment accuracy (CONFIRMED by reading, LOW)

- Two comments say ListPromotion asks `flagReason` and nothing else:
  - `PermissionSets.wholeTree`: *"ListPromotion asks flagReason only"*.
  - `PermissionSetsTest.testFlagReasonReadsAFileNameAsDangerDoes`: *"ListPromotion asks flagReason and nothing else"*.
  - Both are false. `ListPromotion.item` calls `PermissionSets.holdReason(entry)` (ListPromotion.java:368) as well as `flagReason(e)` (:429). The true claim is "it never calls danger()".
- The `wholeTree` comment *"so the two cannot part again"* overclaims. FLAG and danger still part in two ways:
  - On write-only HIGH, which the new `flagReason` javadoc itself documents.
  - Across environments: ListPromotion has no `--define`. `grep -n -i 'define\|Properties env' ListPromotion.java` finds 0 hits, while PolicyLint has `--define`.
- The `nameReach` javadoc narrates its two lines of code (code-comments: delete it or reduce it to the contract).
- The `aliased()` javadoc is WHY-grade and accurate. I re-measured both of its `isSameFile` premises as true on this host. It is silent on F4.

### F8. Pre-existing, not introduced: an empty property name throws (CONFIRMED, LOW)

- `${}` makes `System.getProperty("")` throw `IllegalArgumentException: key can't be empty`.
- `danger`, `flagReason(type,…)` and `flagReason(entry)` throw at both base and patched.
- So `ListPromotion.item` would abort on such an entry.

### F9. Pre-existing over-rating, outside scope

`\\Users\peter\*` (host `Users`, share `peter`) rates M/H at both base and patched. The cause is that `sameDrive` treats a UNC name's null drive as matching any drive, so host and share are compared as if they were user.home segments.

## Brief item 2: is the undefined-property FLAG a regression or the intended ruling?

It is intended, and I found no measured noise.
- Base `danger` already rates `${undefined.x}/foo.jar` H/H, as do `${hello.config.dir}/-` and `${jgdms.bench.workload.path}` (`RevProbe rate`). The FLAG now agrees with that rating, which is ruling (2)'s letter. The reason text says "rated as the root".
- Real promotion inputs: the 8 checked-in `PERMISSIONS.LIST` files hold 24 lines matching `Permission` and 0 FilePermission entries (`git ls-files | grep PERMISSIONS.LIST` and grep). So 0 new flags there.
- Repo-wide: 359 tracked `*.policy`, `*.policy.*`, `PERMISSIONS.LIST` and `*.tsv` files yield 45 distinct FilePermission names (`extract.pl`). 3 change, and all 3 are FLAG only, with 0 severity changes: `${catalina.home}/-` (corpus n21), `${dollar}{config.dir}/-` (archetype templates) and `${hello.config.dir}/-` (hello-world deployer policies). None of the three is a ListPromotion input.
- PLAUSIBLE, unmeasured: lists generated from polpAudit records with custom path properties. None are checked in. FLAG never refuses an entry, and ListPromotion cannot silence one, because it has no `--define`.

## Brief item 3: behaviour changes through `nameReach`

- **fileDanger:** the expression moved into `nameReach` unchanged, so its only change is `aliased()` inside `fileReach`.
- **wholeTree:** it now passes `env` and `refsWorkingDir`, and neither changes an `[H]` outcome. The working directory is checked after the user home and yields only unprefixed reasons. `env` matters only for `danger(…, env)`; mutant M06, which passes null, kills 2 tests including the existing `testFileTargetsAreExpandedAndNormalised`.
- **The directory lookup** (user.home, java.home, tmpdir) is now aliased. That changes nothing on this host, because none of the property values holds an alias.
- **The `*` wildcard:** `\\h\C$\*` and `\\localhost\C$\Users\peter\*` move from 0/0 to M/H, matching `C:\*` and `C:\Users\peter\*`.
- **PolicyScope and broadFile:** PolicyScope is unaffected (F3). `broadFile` has no production caller (grep).
- **FLAG ignores the action:** this is pre-existing. At base, `..\..\-`, `${java.home}${/}-` and `${java.io.tmpdir}${/}-` are all write-H and unflagged, and they still are.
- **Linux, by reading only:** `aliased()` is host-blind. `//h/C$/Users/-`, `/Documents and Settings/-`, `//h/C$/home/-` and `//h/C$/tmp/-` (write) become HIGH on a Linux lint host. All of these are over-ratings of paths with no special meaning there, and none of them lowers a rating.

## UNVERIFIED (needs the build or another host)

- `PolicyLintCorpusTest` with n126 to n128, the corpus expectations and cross-check rows, and the full jgdms-platform and policy-condenser suites.
- My CLI runs used the platform `ConcurrentPolicyFile`, because java.base does not export `au.zeus.jdk.authorization.policy` to the unnamed module. Whether the Maven run uses the DirtyChai provider is unverified.
- The alias tests under a Linux JDK.
- The OS facts beyond this one host.
- Rule 7: TRAINING_DOCS_INDEX.md is on E:\, which this brief forbids, so I did not check it.
