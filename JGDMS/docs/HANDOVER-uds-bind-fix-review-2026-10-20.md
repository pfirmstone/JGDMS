# Handover: adversarial review of the UDS owner-only bind fix

**AS-OF 2026-10-20, branch `claude/focused-bohr-t6bwal` at `25b5b0132`** (source: `git rev-parse --short HEAD`; `git status -sb`
showed the branch level with `origin/claude/focused-bohr-t6bwal` and a clean tree). Anything committed after `25b5b0132` is
NOT reflected here: run `git log --oneline 25b5b0132..HEAD` first. No handover skill was available to the writing agent
(Fable 5.1, adversarial-security seat, agentId `a7be4165110042fb3`); this follows the shape of
`docs/HANDOVER-der-codec-performance-2026-10-18.md`. The review itself was read-only; the only repository edit by this
agent is this file.

## 1. Where the truth lives

| Question | Authoritative file |
|---|---|
| The code under review and its two follow-up fixes | `jgdms-jeri/src/main/java/net/jini/jeri/uds/UdsServerEndpoint.java` (`listen` at `:494-595`, `restrictPosixOwnerOnly` at `:301-353`, staging at `:597-781`, `validateParentDirectory` at `:950-980`) |
| Tests that pin the behaviour | `jgdms-jeri/src/test/java/net/jini/jeri/uds/{UdsRestrictPermissionsTest,UdsStagedBindTest,UdsEndpointRoundTripTest,UdsConstraintEnforcementTest}.java` |
| Review heuristics applied | `docs/JGDMS-Board-Reviewer-Guidance.md` (G2 outcome-vs-mechanism, G3/G13 run the adversarial input, G6 fail-closed decision vs fail-open side effect, §2.3 WS §5.3 HIGH-1) |
| Original design intent for the socket file | `docs/SOW-Unix-Domain-Socket-JERI-Transport.md` §8 (`:180`: "create in a private directory with restrictive perms") |
| The commit under review and the fixes | `git show f27eb98c9` (review target), `81b138766` (staging + link), `25b5b0132` (group-writable parent) |
| Probes (reproducible evidence) | `/tmp/claude-0/-home-user/d4500893-c6b9-5565-960c-ab4381350b7d/scratchpad/probe/Probe.java` and `.../probe/window.py` (session scratchpad; copies of the text are in section 3 so they can be recreated if the scratchpad is gone) |

## 2. State, with provenance

- **Tests at `25b5b0132` (re-run by this agent at 09:34 on DirtyChai 27, `mvn -o -q -pl jgdms-jeri test -Dtest='Uds*Test'`):**
  `UdsStagedBindTest` 8/0 failures, `UdsRestrictPermissionsTest` 7/0, `UdsEndpointRoundTripTest` 16 run 1 skipped,
  `UdsConstraintEnforcementTest` 7/0, `UdsEndpointStreamBuiltTest` 1/0. The one skip is `testFailedListenLeavesNoSocketFile`,
  gated by `childCreationDenied` because the container runs as **root** (uid 0) and root bypasses directory modes.
- **A stale FAILURE report existed before that run:** `target/surefire-reports/net.jini.jeri.uds.UdsStagedBindTest.txt` at 09:32
  showed `groupWritableStickyParentIsAccepted` failing (8 run, 1 failure). It was written in the same minute as `25b5b0132` and
  predates the committed test/code pair; the 09:34 re-run on the committed source is green. Do not read the old report as a
  live defect. (Population: that one class; the other four were green in both runs.)
- **Build trap hit once:** `mvn -o -pl jgdms-jeri test` on the stock `/usr/bin/java` (OpenJDK 21) fails to compile
  (`cannot access au.net.zeus.jgdms.der.DerInputLimits` etc.) and **empties `jgdms-jeri/target/classes/net/jini/jeri/uds/`**
  before failing. Use `export JAVA_HOME=/opt/dirtychai/jdk-27 PATH=/opt/dirtychai/jdk-27/bin:$PATH` (as the DER handover says).
  The module was rebuilt by this agent afterwards; `git status` is clean (no tracked `target/` in `jgdms-jeri`).
- **Kernel facts on this host** (source: `cat /proc/sys/fs/protected_symlinks /proc/sys/fs/protected_hardlinks` printed `0`
  then `1`): `protected_symlinks=0`, `protected_hardlinks=1`. **CORRECTION** to the review report handed back to the
  coordinator, which said `protected_symlinks=1` "set on this host" and that `/tmp` was therefore kernel-mitigated against
  finding 2: on this container it is NOT; the finding's precondition analysis is otherwise unchanged, and the staging fix
  removes the window regardless. The maintainer's workstation may differ; re-check there. The shell `umask` is 022.

## 3. What the review found (against `f27eb98c9`) and the evidence

The commit replaced a `NOFOLLOW_LINKS` `PosixFileAttributeView.setPermissions` (JDK: `open(O_NOFOLLOW)`+`fchmod`, ENXIO on a
socket, so `UdsServerEndpoint` could never bind on Linux) with a following `Files.setPosixFilePermissions` wrapped in NOFOLLOW
`lstat` checks before (must be a socket, record `fileKey`) and after (still a socket, same `fileKey`, mode exactly `rwx------`).

Verdict delivered: the mechanism is correct for what it targets; nothing HIGH; two MEDIUMs about claims the code did not enforce.

| # | Severity | Finding | Evidence | Status at `25b5b0132` |
|---|---|---|---|---|
| 1 | MEDIUM [verified] | The bind->chmod window was real and connectable. `bind()` creates the inode at `0777 & ~umask` and already `listen()`s, so a connect in the gap is queued and `accept()`ed after the chmod. The comment at the time ("the private parent covers the bind->chmod window (F4)") was an outcome without a mechanism: `validateParentDirectory` only refused world-writable non-sticky parents, so `/tmp` (1777) and 0755/2775 parents passed. | `window.py` (server root, client `nobody` via `setpriv`, parent 1777): umask 022 -> `0755`, nobody REFUSED before and after; umask 002 -> `0775`, nobody refused but the process's gid has write; umask 000 -> `0777`, nobody **CONNECTED** before chmod, refused after. `Probe.java` step 3: a pre-chmod connection was returned by `accept()` after the mode was `rwx------`. | **FIXED by `81b138766`**: `listen()` creates a 0700 `.uds-<hex>` directory beside the target (`createStagingDirectory`, `:615-668`), binds and runs `restrictPosixOwnerOnly` there, then `link(2)`s the inode to the public name (`linkIntoPlace`, `:746-761`). `link` refuses to replace, so F3 (never take over a live incumbent) holds; a stale name goes through `reclaimStaleIncumbent`. `UdsStagedBindTest.publicPathNeverShowsAWiderMode` has a watcher. The in-place bind remains for non-POSIX platforms and staged paths over 100 bytes (WARNING logged). |
| 2 | MEDIUM/LOW [verified] | Symlink swapped in between the two checks: the post-check refuses, but the following `chmod` has already set the victim (any file or directory the server uid owns) to `rwx------`. Fail-closed decision, fail-open side effect (G6). Needs directory-replace rights: group-writable non-sticky parent, a sticky parent owned by another non-root user, or `fs.protected_symlinks=0`. | `Probe.java` step 4/5: a `rw-rw-rw-` file became `rwx------`; a `rwxr-xr-x` directory became `rwx------`; the method threw "was replaced" both times. | **FIXED by construction in `81b138766`**: the chmod now runs inside the 0700 staging directory, where no other uid can swap entries. The in-place fallback (long path / non-POSIX) still has this residual. |
| 3 | LOW [verified] | `validateParentDirectory` ignored group-writable non-sticky parents while the new javadoc leaned on it. | `window.py`: a `2775` directory was accepted (`OTHERS_WRITE` absent). | **FIXED by `25b5b0132`** (`:969-970` now tests `OTHERS_WRITE || GROUP_WRITE`, sticky accepted). Tests `groupWritableNonStickyParentIsRefused`, `groupWritableStickyParentIsAccepted`, `privateAndReadOnlyForOthersParentsAreAccepted`. **Only the immediate parent is checked**; see open item 1. |
| 4 | LOW [reasoned] | The failed-listen cleanup unlinked by name (`Files.deleteIfExists(socketPath)`), unlike `LH.unlinkIfStillOurs` which matches `fileKey`; and the `fileKey` was re-read after the chmod (second small TOCTOU). Not exploitable: a swapper could delete the entry anyway, and our socket is closed before the delete. | Inspection of `f27eb98c9` `:543-545`, `:526`. | **PARTLY FIXED**: `restrictPosixOwnerOnly` now returns the verified key; the staged path deletes with `deleteIfSameInode(socketPath, key)` (`:701`). The **in-place fallback still deletes by name in effect**: `fileKey` is only assigned at `:549` after `restrictPermissions` returns, so when `restrictPermissions` throws, `deleteIfSameInode(socketPath, null)` (`:570`) falls back to "delete if it is a socket". See open item 3. |
| 5 | INFO [plausible, not run] | SecurityManager footprint grew: `Files.readAttributes(..., PosixFileAttributes.class)` needs `FilePermission(path,"read")` + `RuntimePermission("accessUserInformation")`; the old path needed write + `accessUserInformation`. `LE.checkPermissions` (`:488-492`) models only `"write,delete"`. `jgdms-jeri/src/test/resources/test.policy` is `AllPermission`, so the `dirtychai` profile cannot notice. | JDK source knowledge (`UnixFileAttributeViews.Posix.readAttributes` -> `checkReadExtended`); not executed. | **OPEN**. `81b138766` runs staging in `doPrivileged` after `checkPermissions`, which covers the staged path, but the in-place fallback's reads still run on the caller's stack. See open item 2. |

Other facts established, in case they are questioned later:
- `Files.setPosixFilePermissions` is a following `chmod(2)`; macOS/FreeBSD accept it on sockets and honour the socket file's
  write bit on `connect` (`unp_connect` -> `VOP_ACCESS(VWRITE)`); `fileKey` is `(st_dev, st_ino)` there too. The Windows ACL
  branch (`applyOwnerOnlyAcl`) was not touched by any of the three commits.
- `isSocket` (`:266-271`) is really `isOther` (true for FIFOs/devices); harmless because `chmod` on a foreign FIFO fails EPERM.
- If `before.fileKey()` were null the identity check silently degrades to "is some socket" (`key != null &&`); not reachable on
  Linux/macOS/BSD.
- Hard-linking a bound socket keeps it connectable by the new name with mode and `fileKey` intact, and `link` over an existing
  live socket throws `FileAlreadyExistsException` (`Probe.java` step 6) - this is what made the staging design safe to adopt.
- The re-enabled `UdsEndpointRoundTripTest`/`UdsConstraintEnforcementTest` differ from their commented-out predecessors only by
  the TODO removal and the root-aware `childCreationDenied` gate (checked by diffing with the `//` prefix stripped); no
  assertion was weakened.

Probe text, so the evidence can be regenerated if the scratchpad is gone: `Probe.java` binds a UDS in a 1777 directory and
prints (1) the bind-time mode and fileKey, (2) that the NOFOLLOW view `setPermissions` throws "No such device or address",
(3) that a connect made before the chmod is returned by `accept()` after it, (4)/(5) a copy of `restrictPosixOwnerOnly` with a
`Runnable` that swaps the socket for a symlink to a victim file/directory, printing the victim's mode before and after,
(6) `createLink` of a staged socket into a public name, connect through it, and `createLink` over a live socket.
`window.py` (run as root) binds under umask 022/002/000 in a 1777 directory, then runs
`setpriv --reuid=nobody --regid=nogroup --clear-groups python3 -I -c '<connect>'` before and after `chmod 0700`.

## 4. Decisions already made (maintainer, Peter, and the coordinator)

- The private-directory staging plus `link(2)` design is adopted (`81b138766`); a NOFOLLOW view for the chmod is NOT to be
  reintroduced (it cannot work on a socket).
- A group-writable non-sticky parent is refused (`25b5b0132`; decided 2026-10-20, no existing UDS deployments so nothing breaks).
- `restrictPermissionsFaultInjector` fires only after `link(2)`, on the public path, to drive the HIGH-1 cleanup; do not add a
  seam that fires before the link.
- The socket file is unlinked at most once per listen handle (`LH.unlinked` AtomicBoolean, `:1302`, `:1339`): `close()` and
  the accept loop's exit both called `unlinkIfStillOurs`, and a re-export of the same path in between could reuse the freed
  inode number and delete the new socket.

## 5. Do-NOT-redo list (each conditional)

| Item | Rests on | Unblocks when |
|---|---|---|
| Re-review the bind->chmod window on the staged path | EVIDENCE: `publicPathNeverShowsAWiderMode` watcher + `Probe.java` step 6 (link preserves mode/inode) | the staging code or `linkIntoPlace` changes |
| Re-probe the symlink-swap chmod side effect on the staged path | CONSTRUCTION: chmod happens inside a 0700 directory we created | staging is bypassed (long path / non-POSIX) - that residual is real and documented, not a regression |
| Treat the 09:32 `UdsStagedBindTest` failure report as live | EVIDENCE: 09:34 re-run on `25b5b0132` green | a fresh run fails |
| Build `jgdms-jeri` with stock JDK 21 | EVIDENCE: compile failure that empties `target/classes` | never; use DirtyChai 27 |

## 6. Open items

1. **Ancestor-directory walk.** `validateParentDirectory` checks the immediate parent only. A writable/non-sticky ancestor lets
   an attacker rename the parent itself (sshd `StrictModes` walks to `/`). Effort: small; decide whether to stop at the first
   directory not owned by us or root, as sshd does.
2. **SecurityManager read-permission footprint (finding 5).** Under a restrictive policy the in-place fallback's
   `Files.readAttributes` needs `FilePermission(path,"read")` and `RuntimePermission("accessUserInformation")`, which
   `LE.checkPermissions` does not model and `test.policy` (`AllPermission`) cannot detect. Either wrap the in-place reads in
   `doPrivileged` like the staged path, or document the grant. Not run under a restrictive policy by anyone yet.
3. **In-place fallback cleanup is by name in effect (finding 4).** `fileKey` is assigned after `restrictPermissions` returns
   (`:549`), so on a throw `deleteIfSameInode(socketPath, null)` deletes any socket at the name. `restrictPosixOwnerOnly`
   already returns the key; capturing it (`fileKey = restrictPermissions(...)` or reading it before the chmod) closes this.
   Harmless today, but inconsistent with the staged path.
4. **No test has run as a non-root user.** Every UDS test run so far (08:34, 09:32, 09:34) was uid 0: `EPERM` on a foreign
   file, `EACCES` on connect, and the read-only-directory HIGH-1 test are therefore unexercised. Run the five UDS classes once
   as an ordinary user (and once under the `dirtychai` SM profile with a non-`AllPermission` policy) before claiming them
   proven.
5. **Test gaps that survive mutation** (unit level, informational): deleting the final `ownerOnly.equals(after.permissions())`
   check or `!isSocket(after)` in `restrictPosixOwnerOnly` still passes every test (the `fileKey` compare subsumes them in the
   swap tests). `symlinkSwappedInBetweenTheChecksIsRefused` asserts refusal but not the decoy's mode; on the in-place path
   the decoy IS chmod'ed, so an assertion there would document the residual.
6. Three `net.jini.jeri.ssl.AuthenticateServerTest` failures arrived with the trunk merge `415c2d1bb` and are unrelated to UDS
   (recorded in the DER handover; not investigated here).

## 7. Agent roster

| agentId | Model | Seat | Holds |
|---|---|---|---|
| a7be4165110042fb3 | Fable 5.1 | adversarial security, UDS bind fix review (2026-10-20) | the five findings above, the probes, the stale-report and JDK-21 build-trap observations; finished, read-only |

## 8. CURRENCY CHECK (mechanical)

Keywords checked against this document at composition: `umask`, `link(2)`, `fileKey`, `staging`, `group-writable`,
`SecurityManager`, `root`. Deliberately excluded: the full review report text (delivered to the coordinator via handback, not
on disk), the raw surefire XML, and the DER-handover paragraph `docs/HANDOVER-der-codec-performance-2026-10-18.md` §7 item 6,
which summarises the same fix from the implementer's side and should be read alongside this file.
