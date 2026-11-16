claude-opus-5-5

# Differential review: FilePermission whole-tree ratings (PermissionSets) and the JWT uninstall ordering

**Reviewer:** claude-opus-5-5 (cross-model; the producer is named in both commits' trailers as Claude Sonnet 5.5).
**Date:** 2026-11-06. **Mode:** read-only on the main checkout. All probes ran in worktree
`C:/Users/peter/Documents/GitHub/JGDMS-review-20261106` (detached at `0eb9f0cc7`) and are left
uncommitted under `probe-pathdiff/`.
**Skills loaded:** handover-composition, production-code. rag-file was not loaded because no scan data is
involved. code-comments was not loaded because no comments were judged, except one test comment noted under F8.
**Commits under review:** `0eb9f0cc7` (PermissionSets: long-path prefix, slash before a drive letter) and
`d95503fe7` (JwtLoginModule: state reset before `shutdown()`, refused interrupt logged).
**JDK for every result in this file:** `C:\Users\peter\Documents\GitHub\DirtyChai\build\windows-x86_64-server-release\images\jdk`
(`java.version=27-internal`, `java.vm.version=27-internal-adhoc.peter.DirtyChai`; printed by the probe at
startup). The installed `C:\Program Files\DirtyChai\jdk` was not used.
`jdk.io.permissionsUseCanonicalPath=false` (the security property, printed by the probe and stated at
`conf/security/java.security:1509`). Nothing ran on Linux; see the UNVERIFIED list.
**Not consulted:** `E:\Claude\TRAINING_DOCS_INDEX.md`. The brief says never to touch `E:\`, so the
standing rule 7 lookup was not done. Rule 7's AI_AGENT_* handover check could not be done either.

---

## 1. Method (what was measured and how it was scored)

**Catalogue side (production entry points).** `PermissionSets.java` was compiled from the reviewed commit
(md5 `6855c3945b8c06175417b49092bcdcad`) with `javac --release 8`, the same flag as the reactor
(`JGDMS/pom.xml` `<release>8</release>`). It was placed ahead of the m2 `jgdms-platform-4.0.0-SNAPSHOT.jar`
(md5 `91a0633411e84f7e5ea781ad61096bcf`, built 2026-11-01). The probe printed that `PermissionSets` loaded from
`probe-pathdiff/ps-classes/`. For each spelling and action the probe calls
`PermissionSets.danger("java.io.FilePermission", s, a)` and `PermissionSets.flagReason(...)`. These are
the calls `PolicyLint.symbolic` (`PolicyLint.java:739`) and `ListPromotion` (`ListPromotion.java:429`) make.
Confirmations were then run end-to-end through the production CLI
`org.apache.river.tool.PolicyLint`, compiled from the worktree's `tools/policy-condenser` sources.

**JDK side (ground truth).** Each spelling is expanded with the JDK's own `sun.security.util.PropertyExpander.expand`
(strict). The probe then builds `new FilePermission(expanded, a)` and asks whether it `implies(new FilePermission(P, a))`
for access spellings P of a file inside each ruled tree. The file sits at a name that no generated spelling contains
(`zq81\deep\f.txt`, share `zq82`; the generator asserts that no spelling contains "zq"). So an implies of
P can only come from coverage of the whole tree.

**Trees (10):** the C: drive root; `C:\Users`; one other user's home `C:\Users\alice`; `user.home`
= `C:\Users\peter`; the Unix analogues on the current drive `\home`, `\home\alice`, `\root`, `\var\root`;
the UNC share `\\server\share`; and the UNC host `\\server`.

**Access-spelling (alias) families per tree:** canon, forward slashes, root-relative `\…`, `\\?\` long form,
`\\.\Volume{GUID}\…`, relative from the cwd, drive-relative `C:..\…`, trailing-dot segment (`Users.`),
`Documents and Settings`, `\\localhost\C$\…`, and for UNC trees `\\.\UNC\…`.
**Each family was verified live** by `Files.isSameFile(alias(existing analog), canon(existing analog))`.
86 of 94 alias spellings verified. The 8 unverified are the `\\.\C:\…` device-drive family. The JDK refuses that
family itself (`InvalidPathException: Illegal character [:] in path at index 5: \\.\C:\Windows`), so it cannot
contribute coverage either way. Result: "rows covering a whole tree only via UNVERIFIED alias families: 0".

**Spellings generated:** 22 root forms × 34 middle-segment lists × 13 tails × 11 separator styles. The styles are
`\`, `/`, both alternations, a first-separator run of 2–5, every separator doubled, `/\` mixed, and `///` first.
The middle lists include `.`, `..`, `x\..`, `Users`, `Users\alice`, `home`, `root`, `var\root`, `Users.` and
`Documents and Settings`. The roots include `C:\`, `c:\`, `\C:\`, `C:`, `\C:`, `\`, relative, `\\?\C:\`,
`\\.\C:\`, `\\.\Volume{GUID}\`, `\\.\GLOBALROOT\…`, `\\server\share\`, `\\server\`, `\\?\UNC\`, `\\?\unc\`,
`\\.\UNC\`, `\\localhost\C$\`, a doubled long prefix, `${user.home}${/}`, `${user.home}\` and
`${undefined.zz}\`. The tails include `-`, `*`, `-\`, `-\\`, `-\.`, `-\x\..`, `- `, `-.` and `-\..\-`.
Deduplicated: **105,694 unique spellings; × 4 actions (read, write, delete, execute) = 422,776 rows.**
Source: `probe-pathdiff/run1/summary.txt`.

**Scoring rules.**
- **FN-danger:** the JDK says some verified alias of a ruled tree is implied, and `danger(...).severity < HIGH`.
- **FN-flag:** the same coverage, and `flagReason == null`.
- **FP-read:** the action is read, no alias of any tree is implied, the property is defined, and
  `danger == HIGH`. This bound is loose; see F8.

**Validation under the null (rule 1).** The coverage detector is not constant:
- **Rows that agree:** 33,820 rows are covered and HIGH (92,752 covered − 58,932 FN; read alone: 8,191
  covered and HIGH of 23,188 covered).
- **Response to a known catalogue change:** the probe was re-run against the parent `b342a34d3`'s
  `PermissionSets` (md5 `75c3bc839474957c57ff63b21371a423`). It reports 2,128 covered rows raised by
  `0eb9f0cc7` (the commit's intended fix, e.g. `\\?\C:\Users\-`) and 704 covered rows lowered (F3). So the
  WIRING link responds in both directions.
- **REPORT link:** rows = 422,776, so the run actually ran.

**Population caveat.** The generator is deliberately adversarial and combinatorial. Every rate below is a
property of this generated population. None of them is an estimate of how often real policies hit a defect.

## 2. Headline counts

| Figure | Value | Population / source / rule |
|---|---|---|
| Unique spellings | 105,694 | generator, `run1/summary.txt` |
| Rows | 422,776 | spellings × 4 actions |
| Rows covering ≥1 ruled whole tree (JDK, verified alias) | 92,752 (read 23,188) | `run1/all.tsv` |
| **FN-danger rows** | **58,932** (read 14,997; write, delete and execute 14,645 each) | covered and `danger < HIGH`. Of these, 58,580 are UNRATED and 352 are MEDIUM |
| Unique spellings with an FN | 14,997 | `run1/mismatches.tsv` |
| FN on the core trees only (drive, share, host, `Users`, one home, `user.home`), read, excluding `localhost` and `Documents and Settings` | 11,097 (write: 10,745) | awk over `mismatches.tsv` |
| Read FN / read covered | 14,997 / 23,188 = 64.7 % | generated population only |
| **End-to-end via `PolicyLint` CLI**, one unscoped `read` grant per FN spelling | **9,796 files exit CLEAN**; 352 D5 (MEDIUM); 4,849 D1 (HIGH) | 14,997 files, `e2e-bulk-lint.tsv` (md5 `bb10d5b1…`), joined in `e2e-bulk-join.tsv` |
| FN rows at the parent `b342a34d3` | 60,356 | `run0-parent/summary.txt`. Net Δ from `0eb9f0cc7` = −1,424 = −2,128 fixed + 704 regressed |
| FP-read rows | 8,264 (an upper bound; 5,013 adjudicated, all in the conservative direction) | see F8 |

**Why PolicyLint catches 4,849.** The CLI also asks the production provider about fixed probes
(`PermissionSets.probes()`: `add(res, FILE, under, a, rootTree)` with `root = File.listRoots()[0]`, and
`home + sep + "lint-probe"`). Those probes cover only the lint host's `C:\` and `user.home`. They do not
cover shares, `C:\Users`, other users' homes, root-relative `\` names or `/home`-style trees, which is
why 9,796 whole-tree grants pass with exit 0. `ListPromotion` consults `flagReason` only, so the probes do
not rescue anything there.

## 3. Findings, ranked by exploitability

### F1 — CONFIRMED (Windows). A wildcard followed by `.` or `x\..` is not a wildcard to the catalogue. Every ruled tree is affected, for every action.

`au.net.zeus.jgdms.api.policy.PermissionSets.filePath` takes the wildcard from the raw last segment
*before* applying `.` and `..`:
```java
        if (last >= 0 && (raw[last].equals("-") || raw[last].equals("*"))) {
            f.wildcard = raw[last];
            last--;
        }
        for (int i = 0; i <= last; i++) {
            String s = raw[i];
            if (s.length() == 0 || s.equals(".")) continue;
```
The JDK normalizes first and then tests the last name (`java.io.FilePermission`, private constructor,
DirtyChai `src/java.base/share/classes/java/io/FilePermission.java:382-389`):
```java
npathLocal = builtInFS.getPath(new File(name).getPath()).normalize();
Path lastName = npathLocal.getFileName();
if (lastName != null && lastName.equals(DASH_PATH)) {
```
**Measured** (`probe-pathdiff/spot1.out`; each line is `jdk npath / recursive | implies(...) | catalogue read sev, write sev | flagReason`):

| Spelling | JDK | Catalogue |
|---|---|---|
| `C:\-\.` | `npath=C:\ rec=true`, `implies(read,[C:\zq81\deep\f.txt])=true`, write `true` | read sev 0, write sev 0, flagReason null |
| `C:\-\x\..`, `C:/-/./` | same | same |
| `C:\Users\-\.` | `npath=C:\Users rec=true` | 0 / 0 / null |
| `C:\Users\alice\-\.` | `npath=C:\Users\alice rec=true` | 0 / 0 / null |
| `${user.home}${/}-${/}.` | `npath=C:\Users\peter rec=true` | 0 / 0 / null |
| `/-/.`, `/home/-/.`, `/root/-/x/..` | `npath=\`, `\home`, `\root`, rec=true | 0 / 0 / null |
| `\\server\share\-\.`, `//server/share/-/x/..` | `npath=\\server\share\ rec=true` | 0 / 0 / null |

**Size of the class (bucket A):** 11,613 read spellings, with 11,437 rows each for write, delete and execute.
**At the CLI:** of the 11,613 one-grant files, 6,888 are CLEAN (exit 0), 176 are D5 and 4,549 are D1. The D1 files
are only the ones that imply the provider probes `C:\lint-probe\x` or `C:\Users\peter\lint-probe`. Four of the
named fixtures in `probe-pathdiff/e2e/` belong to this class (output in `e2e.out`):
- `p02 "C:/-/." read,write,delete,execute`: D1, from the provider probe.
- `p04 "${user.home}${/}-${/}."`: D1.
- `p11 "/-/." read,write`: **exit 0**.
- `p12 "//server/share/-/."`: **exit 0**.

**Failure scenario.** `grant { permission java.io.FilePermission "//nas/share/-/.", "read,write"; };` grants every
stranger read and write over the whole share. `PolicyLint` exits 0, and the same entry in a proxy list is not flagged.
**Unix:** HYPOTHESIS, by shared source. `FilePermission` is share code, `UnixPath.normalize`
(`src/java.base/unix/classes/sun/nio/fs/UnixPath.java:566`) removes `.` and `x/..`, and the catalogue is host-blind.
On Linux the provider probe `/lint-probe/x` would rescue `/-/.`, but not `/home/-/.`, `/Users/alice/-/.` or
`//server/share/-/.`. UNVERIFIED-NEEDS-LINUX-JDK.
**Direction (advice).** Apply `.` and `..` before choosing the wildcard, so the last *normalized* name is tested,
as the JDK does. Pin `C:\-\.`, `/-/.`, `/home/-/x/..` and `//server/share/-/.` as HIGH for read and for write.

### F2 — CONFIRMED (Windows). Three or more separators before `?`, or a doubled long prefix: the catalogue reads `?` as a UNC host

`PermissionSets.filePath` strips the long-path prefix only at index 0 with exactly two separators:
```java
        if (p.length() >= 8 && p.regionMatches(true, 0, "//?/UNC/", 0, 8)) p = "//" + p.substring(8);
        else if (p.startsWith("//?/")) p = p.substring(4);
```
The JDK first collapses any run of leading separators (`java.io.WinNTFileSystem.normalizePrefix`, DirtyChai
`src/java.base/windows/classes/java/io/WinNTFileSystem.java:225-252`). It then strips `\\?\` in
`sun.nio.fs.WindowsPathParser.parse` (`WindowsPathParser.java:66-73`). Measured:

| Spelling | JDK | Catalogue |
|---|---|---|
| `\\\?\C:\Users\-` | `npath=C:\Users rec=true`, implies `C:\Users\zq83\f.txt` | 0 / 0 / null |
| `///?/C:/Users/alice/-` | `npath=C:\Users\alice` | 0 / 0 / null |
| `\\\?\UNC\server\share\-`, `///?/UNC/server/share/-` | `npath=\\server\share\` | 0 / 0 / null |
| `\\?\\\?\C:\Users\-` | `npath=C:\Users` | 0 / 0 / null |
| `\\\?\C:\-` | `npath=C:\` | 2, HIGH by luck: segments `[?, C:]` meet `f.unc && size <= 2` |

The catalogue labels the HIGH it does give as "a whole network share or host tree".
**Size:** 1,120 + 68 read spellings. At the CLI, 840 + 48 are CLEAN. Fixtures `p06` and `p07`: **exit 0**.
**Direction.** Collapse a leading run of separators before the prefix test, and strip the prefix repeatedly.
Note that the JDK's own `UNC` test is case-sensitive (see F8).

### F3 — CONFIRMED, and a **regression introduced by `0eb9f0cc7`**. A drive-relative `..` chain is unrated for every action, and the commit routed `/C:..` into that gap

`PermissionSets.fileReach`:
```java
        if (!f.absolute) {
            if (f.drive == null && !f.segments.isEmpty() && f.segments.get(0).equals("..")) {
                return "a path above the current directory";
            }
            return null;
        }
```
The `f.drive == null` test leaves `C:..\..\-` with no rating. Plain `..\..\-` is MEDIUM for read and HIGH for write.
`0eb9f0cc7` changed `driveAfterSlashes`:
```diff
-                && Character.isLetter(p.charAt(lead)) && (p.length() == lead + 2 || p.charAt(lead + 2) == '/');
+                && Character.isLetter(p.charAt(lead));
```
Before the change, `/C:../../../../../../-` fell through as an absolute name. Its `..` emptied the segments and it
was rated `[H]a whole root or drive tree`, conservatively and by accident. Now it is drive-relative and unrated.

**Measured, live on this host.** The cwd is
`C:\Users\peter\Documents\GitHub\JGDMS-review-20261106\probe-pathdiff`, which is 6 levels deep.
- `isSameFile(C:..\..\..\..\..\..\Windows, C:\Windows)=true`.
- `C:..\..\..\..\..\..\-`: JDK `npath=C:..\..\..\..\..\.. rec=true`, `implies(read and write, [C:..\..\..\..\..\..\zq81\f.txt])=true`. Catalogue read sev 0, **write sev 0**.

**Regression.** Diffing `run0-parent/all.tsv` against `run1/all.tsv` gives 176 read spellings (704 rows) that went
HIGH → UNRATED while the JDK covers `C:\`, `C:\Users` or `C:\Users\peter` (`probe-pathdiff/regressions-read.tsv`).
All of them have the form `/C:..\…\-`.
**End-to-end** with fixture `p15 "/C:../../../../../../-" read,write`:
- with the parent's `PermissionSets`: `D1 … reaches a whole root or drive tree`, `exit=1`;
- with `0eb9f0cc7`: `exit=0`.

The whole bucket is 352 read spellings, and all of them are CLEAN at the CLI.
**Where the vacuity went.** The commit's new assertions pin `/C:-` and `/C:*` for write. No assertion covers `/C:..`,
so the widened branch moved those spellings from an accidental root rating into the unrated drive-relative branch.
**Direction.** Rate a drive-relative name whose first segment is `..` the same way as the drive-less form, at
least as "a path above the current directory". Add `/C:../-` and `C:../-` to the tests.

### F4 — CONFIRMED (Windows). In the device namespace `\\.\UNC\…` and `\\.\Volume{GUID}\…`, a `.` host is dropped and the next segment becomes the host

`PermissionSets.filePath`, the line `if (s.length() == 0 || s.equals(".")) continue;`, drops the device host `.`.
So `//./UNC/server/share/-` becomes segments `[UNC, server, share]`, and `f.unc && f.segments.size() <= 2` fails.
**Measured live:**
- `isSameFile(\\.\UNC\localhost\C$\Windows, C:\Windows)=true`.
- `isSameFile(\\.\Volume{14e09011-0000-0000-0000-300300000000}\Users\peter, C:\Users\peter)=true`.

| Spelling | JDK | Catalogue |
|---|---|---|
| `\\.\UNC\server\share\-` | `npath=\\.\UNC\server\share rec=true` | 0 / 0 / null |
| `\\.\Volume{14e09011-…}\Users\alice\-` | `npath=\\.\Volume{…}\Users\alice rec=true` | 0 / 0 / null |
| `\\.\UNC\server\share\..\-\.` | `npath=\\.\UNC\server` (every share on the host) | 0 / 0 / null |
| `\\.\UNC\server\-`, `\\.\UNC\-`, `\\.\Volume{…}\-` | whole host / all hosts / whole volume | 2: HIGH, by the size ≤ 2 rule |

**Size:** 220 + 264 read spellings, all CLEAN at the CLI. Fixtures `p05` and `p10`: **exit 0**.
`\\.\GLOBALROOT\Device\HarddiskVolumeN\-` has the same parse (JDK `rec=true`, catalogue 0) and is **PLAUSIBLE**.
Its live check failed for N=3 (`isSameFile=false`). I did not probe other volume numbers, because that could
touch `E:\`. `\\.\C:\…` is not in this class: the JDK refuses it (F8).
**Direction.** Treat a `.` in the host position as a device prefix: `\\.\UNC\h\s` is `\\h\s`, and
`\\.\Volume{…}\` and `\\.\GLOBALROOT\Device\…\` are drive roots.

### F5 — CONFIRMED (Windows). A trailing-dot segment (`C:\Users.\-`) is a Win32 spelling of `C:\Users`

The JDK keeps `Users.` lexically, and Win32 removes one trailing dot from a path segment. The catalogue's
`f.segments.get(0).equalsIgnoreCase("Users")` sees `Users.` as a different name.
**Live:**
- `isSameFile(C:\Users.\peter, C:\Users\peter)=true`.
- `isSameFile(C:\Users\peter.\Documents, C:\Users\peter\Documents)=true`.

`C:\Users.\-` and `C:\Users.\alice\-` are 0 / 0 / null. `C:\Users\alice.\-` is HIGH, because the one-home rule
accepts any name.
**Size:** 328 read spellings, all CLEAN at the CLI. Fixture `p09`: **exit 0**.
**Direction.** Strip one trailing `.` from each segment that is not `.` or `..` before comparing names.
Win32 does exactly this, and the catalogue already normalizes `.` and `..`.

### F6 — CONFIRMED (catalogue) / PLAUSIBLE (exploit path). The FLAG set (`flagReason`) misses all of F1–F5, and also `${undefined}` names

`ListPromotion` (`ListPromotion.java:429`) calls only `PermissionSets.flagReason(e)`. It runs neither `danger()`
nor the provider probes. Every spelling in F1–F5 returns `flagReason=null` (measured, spot1.out).
`flagReason` is also null for `${undefined.zz}/-` (measured), where `danger()` gives HIGH under the
undefined-property ruling. `PermissionSets.wholeTree` returns early (`if (e == null) return false;`) with the
comment "danger() rates it from its own environment", but the promotion path never calls `danger()`.
**Exploit path (PLAUSIBLE, not run).** Code under a polpAudit recording opens `new File("C:\\-\\.")`. The recorder writes
`FilePermission "C:\\-\\.", "read"`, and the JDK treats that name as recursive over `C:\`. The generated list then
promotes with no flag. I did not verify that `SecurityPolicyWriter` records the name verbatim.

### F7 — CONFIRMED mismatch, ruling question rather than a defect. OS aliases and relative depth

These spellings reach a ruled tree through an OS mechanism the catalogue does not model. I report them as
questions, because the producer's own commit message lists aliases of this kind as deliberately unrated:
"Unrated and not changed: /private/var/root/-, //nas/home/alice/-, /export/home/alice/-".
- `C:\Documents and Settings\-`: a junction to `C:\Users`. Live: `isSameFile(C:\Documents and Settings\peter, C:\Users\peter)=true`. Catalogue 0 / 0. 328 read spellings, CLEAN at the CLI (fixture `p14`).
- `\\localhost\C$\Users\-` and `\\localhost\C$\Users\alice\-`: an admin share aliasing `C:\`. Live: `isSameFile(\\localhost\C$\Users\peter, C:\Users\peter)=true`, from this unelevated process. This is within the ruling "a named directory below a share stays UNRATED", but the ruling's premise (a named directory is narrow) fails for admin shares. 528 read spellings, CLEAN.
- `..\..\..\..\..\..\-` read is MEDIUM ("a path above the current directory"). Live from this cwd it is `C:\`: `isSameFile(..\..\..\..\..\..\Windows, C:\Windows)=true`. 176 read spellings at D5.
  The count is a lower bound: my relative aliases used exactly the cwd depth (6), and spellings with more `..` also reach the root because Win32 clamps.
- If a deployment sets `jdk.io.permissionsUseCanonicalPath=true`, canonicalization resolves junctions and
  trailing dots before matching. `service-starter/.../SharedActivationPolicyPermission.java:51` documents that
  setting. Not measured.

### F8 — CONFIRMED, conservative direction only. False positives, and one inaccurate test comment in `0eb9f0cc7`

There are 8,264 FP-read rows (read, no tree implied, HIGH). This is an upper bound: my probe set only knows the
host `server`, so whole shares of other hosts count as FPs here. The adjudicated part:
- **JDK-invalid names rated HIGH: 4,682 rows.** Examples:
  - `C:\- ` (trailing space; `invalid=true`);
  - `\\.\C:\-` (`Illegal character [:]`; catalogue: "whole network share or host tree");
  - `//?/unc/server/share/-` (`invalid=true`).

  The JDK's prefix test is case-sensitive (`WindowsPathParser.parse`: `input.startsWith("UNC\\", 4)`), so `\\?\unc\…`
  is refused, and `\\?\Unc\server\share\-` is the *relative* path `Unc\server\share`.
- **Not recursive to the JDK: 331 rows.** Example: `\\server\-`. The JDK reads it as a share named `-`, with
  `npath=\\server\-\ rec=false`; the catalogue says "whole network share or host tree".
- **A `..` that pops a UNC share in the catalogue but not in the JDK.** Example: `\\server\share\..\-\-`.
  The JDK reads `npath=\\server\share\-` (a directory named `-`); the catalogue gives HIGH.

These cannot under-rate anything.
**Test comment.** The new test block in `PermissionSetsTest` (`0eb9f0cc7`) says "the JDK strips the long-path
prefix on Windows". It then asserts `"//?/unc/server/share/-"` HIGH for read and write. The JDK marks that name
invalid, so it grants nothing (measured). The assertion pins a false positive, and the comment is a false
reference for that member.

### Nothing-found items (with what established each)

- **The commit's stated forms behave as stated** (measured, spot1.out):
  - `\\?\UNC\server\share\-` and `\\?\C:\-` are HIGH, and the JDK covers the share and the drive;
  - `/C:-` and `/C:*` are MEDIUM for read and HIGH for write; the JDK reads `npath=C:`, the drive-relative cwd
    tree, which is consistent with the `-` (cwd) policy;
  - the narrow names `\\?\UNC\server\share\dir\-`, `///server/share/dir/-` and `/C:/data/app/-` are 0, and
    the JDK covers only that directory.
- **No coverage came only from an unverified alias family.** 0 rows (summary.txt). The first link at which this
  could have read zero is ALGEBRA: the only unverified family (`\\.\C:\`) is refused by the JDK, so its implies
  can never fire.
- **Undefined property:** `danger("${undefined.zz}/-", read)` is HIGH, as ruled (measured). Only the FLAG-set
  gap is reported (F6).
- **Duplication check (production-code skill).** `git grep -n "filePath(\|wholeTree(\|fileReach("` (main
  sources): the only external user is `PolicyScope.java:345`, which reuses `filePath` for codeBase file locations.
  F1's ordering therefore also applies there. That path was **not** tested; it is PLAUSIBLE.
  The design point: `filePath` is a host-blind re-implementation of `WinNTFileSystem`, `WindowsPathParser`,
  `UnixPath.normalize` and `FilePermission`. F1–F5 are all places where that mirror diverges.
  **Advice:** on the lint host, cross-check each FILE verdict against `FilePermission.implies` on host probes for
  the ruled trees, using names that do not occur in the grant: `C:\<nonce>\x`, `\\h\s\<nonce>\x`, `\<nonce>\x`
  and `C:\Users\<nonce>\x`. That would have caught F1–F5 on Windows at lint time.

## 4. JWT commit `d95503fe7`: `JwtLoginModule.uninstallLocked` and `RefreshThread.shutdown`

`au.zeus.jgdms.security.jwt.JwtLoginModule.uninstallLocked` after the change:
```java
        try {
            removeInstalledLocked();
        } finally {
            installedSub = null;
            installedIss = null;
            commitSucceeded = false;
            if (rt != null) rt.shutdown();
        }
```
- **J1 — no path introduced by this commit leaves the token on the Subject.** Checked by reading:
  - `logout`, `abort` and the second-`commit` undo hold the Subject monitor, and so does `installRefreshed`.
    `installRefreshed` rechecks `caller.running || commitSucceeded` under the lock, and `shutdown()` now runs in
    the same critical section. A refresh in flight therefore cannot reinstall after logout.
  - `shutdown()` now catches a `RuntimeException` from `interrupt()`, and `closeClient` already caught
    `Throwable`. So the resets can no longer be skipped by `shutdown()`.
  - In DirtyChai, `Subject.setReadOnly` takes the Subject monitor (`Subject.java:250`), so it cannot interleave
    between the two `removeAll` calls.
- **J2 — HYPOTHESIS; the commit's "No new test" premise looks refuted.** The commit says the refused interrupt
  "needs a SecurityManager that denies Thread.interrupt". However:
  - `JwtLoginModule.threadStarter` is a package-private test hook, already used at `JwtLoginModuleTest.java:2019` and `2219`;
  - `rt.thread = threadStarter.apply(rt, …)` accepts any `Thread`;
  - `Thread.interrupt()` is `public` and not final (DirtyChai `Thread.java:1926`).

  So a `Thread` subclass whose `interrupt()` throws `SecurityException` can exercise the path with no
  SecurityManager. A load-bearing test would assert that `logout()` returns normally and leaves nothing on the
  Subject; before this commit, `logout()` threw. UNVERIFIED-NEEDS-BUILD: I did not compile such a test.
- **J3 — PLAUSIBLE, pre-existing, loud.** `removeInstalledLocked` removes principals *before* public credentials:
  ```java
          target.getPrincipals().removeAll(addedPrincipals);
          target.getPublicCredentials().removeAll(addedCredentials);
  ```
  Suppose the second `removeAll` throws. One way is `AuthPermission("modifyPublicCredentials")` refused after a
  policy refresh between commit and logout, while `modifyPrincipals` is still granted. Then:
  - the principals are gone, but the `JwtRawToken` that `writeUserSubjects` sends on the wire stays;
  - `logout` throws;
  - after this commit the module also reports `commitSucceeded=false`.

  A retry still works, because `addedCredentials` is cleared only after both calls. Removing the credentials
  first would make the residue "principals without a token" rather than "a bearer token without principals".
  Not exercised.
- **J4 — observation.** After a refused interrupt, the `RefreshThread` keeps `refreshToken` and `clientSecret`
  in memory until it wakes, which can be up to 80 % of the token lifetime. They are not on the Subject.

## 5. UNVERIFIED (could not be measured here)

- **UNVERIFIED-NEEDS-LINUX-JDK:**
  - F1 on Unix (`/-/.`, `/home/-/.`, `/Users/alice/-/x/..`);
  - which Linux provider probes rescue which spellings.

  No Linux JDK and no WSL distro on this host (`wsl -l -v` printed usage only).
- **UNVERIFIED-NEEDS-BUILD:**
  - J2's test;
  - the `PolicyScope` codeBase use of `filePath` (F1 ordering);
  - whether polpAudit's `SecurityPolicyWriter` records `C:\-\.` verbatim (F6 exploit path).
- **The `GLOBALROOT` form (F4):** not verified live; that would need probing other volume numbers, which could touch `E:\`.
- **The PolicyLint bulk run:** used `policy-condenser` main sources compiled from the worktree against the
  2026-11-01 m2 `jgdms-platform` jar plus the reviewed `PermissionSets`. Platform main-source changes after that jar
  (`git log --since=2026-10-31 -- JGDMS/jgdms-platform/src/main`) are:
  - `PermissionSets`, which is shadowed by the reviewed copy;
  - `DefaultJwtVerifier`;
  - `DefaultPolicyScanner` and `UnresolvedPrincipal`, in `org.apache.river.api.security`, a package `java.base`
    owns in this JDK.

  None of them parse FilePermission names.

## 6. Questions the operator can settle from an existing artefact

1. **The commit message of `0eb9f0cc7`** ("Unrated and not changed: /private/var/root/-, //nas/home/alice/-,
   /export/home/alice/-"). Was leaving aliases of ruled trees unrated a ruling, or the producer's choice?
   It decides F7: `C:\Documents and Settings\-`, and `\\localhost\C$\Users\…` via admin shares.
2. **The ruling "a named directory below a share stays UNRATED".** Should it exempt admin shares (`C$`, `ADMIN$`)
   and loopback hosts, which alias local drives? (F7)
3. **The undefined-`${property}` ruling.** Does it apply to the FLAG set used by `ListPromotion`, or only to
   `danger()`? (F6)
4. **Relative `..` chains for read** (currently MEDIUM). Should one count as a whole-drive read once the count
   reaches the deploy cwd depth? (F7)

## 7. Probe files (uncommitted, in the worktree)

`C:/Users/peter/Documents/GitHub/JGDMS-review-20261106/probe-pathdiff/`:
- **Sources:** `src/PathDiff.java` (md5 `55d4cfa2…`), `src/Spot.java` (`31043cbe…`), `src/E2eGen.java` (`1795c5b8…`).
- **Inputs and classes:** `spot1.txt`, `ps-classes/` (reviewed `PermissionSets`), `prev-classes/` and `prev-src/`
  (parent `PermissionSets`), `pc-classes/` (`PolicyLint` and the rest of `policy-condenser`).
- **Outputs:** `spot1.out`; `run1/` (`all.tsv` md5 `16888f09…`, `mismatches.tsv` `02c68a06…`, `aliases.tsv`,
  `summary.txt`); `run0-parent/`; `regressions-read.tsv`.
- **End-to-end:** `e2e/` (15 named fixtures) with `e2e.out`; `e2e-bulk/` (14,997 fixtures and `index.tsv`) with
  `e2e-bulk-lint.tsv` and `e2e-bulk-join.tsv`.
- **Counting scripts:** `bucket.awk`, `bucket2.awk`, `fpbucket.awk`. They only tally production output by
  spelling pattern.

**Reproduce:** from `probe-pathdiff`, with `J` = the DirtyChai build JDK:
- `$J/bin/java --add-exports java.base/sun.security.util=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED -cp "out-classes;ps-classes;<m2 jgdms-platform jar>" PathDiff run1 "Volume{14e09011-0000-0000-0000-300300000000}"` (15.6 s);
- `… Spot spot1.txt`;
- `$J/bin/java --add-exports java.base/au.zeus.jdk.authorization.policy=ALL-UNNAMED -cp "pc-classes;ps-classes;<jar>" org.apache.river.tool.PolicyLint --format=tsv e2e-bulk` (2 min 35 s).
