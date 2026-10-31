# Merge-assessment item: eMudhra compromised CA keys (upstream fix to adopt)

Recorded 2026-10-31 for the DirtyChai merge to `jdk-27+7` (or the next upstream tag that carries the fix).
Format: one finding record in the shape of `MERGE_SECURITY_ASSESSMENT_RUBRIC.md` section 8, because that
is the form the Phase-2 punch-list uses. This is analysis only: it proposes no code. Any change to the
DirtyChai tree is human-authored (`CLAUDE.md`); the record is a JGDMS-side file so the operator can place it
wherever DirtyChai tracks merge items (DirtyChai's `TODO.md` is a different, Subject-documentation list).

| Field | Content |
|---|---|
| `id` | `Mjdk27-emudhra-001` |
| `loc` | `src/java.base/share/data/cacerts/emsignrootcag1`, `emsignrootcag2`, `emsigneccrootcag3`, `comodoaaaca` (the four files present in the working tree, per the local check below); and the installed JDK's `lib/security/cacerts` (aliases `emsignrootcag1 [jdk]`, `emsignrootcag2 [jdk]`, `emsigneccrootcag3 [jdk]`, `comodoaaaca [jdk]`) |
| `op` | The four root certificates are trusted by default. OpenJDK states the private keys of eight eMudhra intermediate CA certificates are compromised and tells operators to remove these four roots until a fixed runtime is installed. |
| `category` | none of C1/C2/C2b/C3/R fits: an upstream security fix to adopt (a trust-store data change). Closest is `R-removed` (data files removed upstream); mark `S` only if the merge removes them without a visible conflict. |
| `remediation` | Take the upstream fix tracked as JDK-8395690 / CVE-2026-114820 in the merge, and confirm the four roots are gone from the merged tree. Until then, remove the four roots from each installed DirtyChai JDK's `cacerts` (operator action; the commands are in the session reply of 2026-10-31) and keep revocation checking available (`-Dcom.sun.net.ssl.checkRevocation=true -Dcom.sun.security.enableCRLDP=true`). |
| `target` | the four `cacerts` data files above; no permission or guard involved |
| `placement` | the merge of the upstream tag that contains JDK-8395690; verify after the merge with OpenJDK's `CheckCerts.java` (linked from `https://openjdk.org/emudhra`) run on the built image |
| `model_ref` | none in `SECURITY_MODEL.md`; relevant to the TLS trust anchors, not to the SecurityManager model |
| `confidence` | high that the installed JDK and the source tree trust the four roots (read directly); medium on which upstream build carries the complete fix (UNVERIFIED, see notes) |
| `notes` | see below |

## Notes (every figure carries its source)

- **Primary source read by me:** `https://openjdk.org/emudhra`, last updated 2026-10-24 19:24 UTC: eight eMudhra
  intermediate CA private keys compromised; update to a runtime whose notes cite JDK-8395690 / CVE 2026-114820, or
  remove the four roots; revocation-checking properties as an alternative.
- **Read through page summaries by a sub-agent, not by me** (`docs/VERIFY-jsse-ca-distrust-2026-10-31.md`): CVE records
  for CVE-2026-91403 (Oracle, JSSE, CVSS 9.1, published 2026-10-21) and CVE-2026-114820 (published 2026-10-24); Red Hat
  scores one at 6.8 against Oracle's 9.1; CISA's SSVC exploitation field read "none". Oracle's own alert pages returned
  403 and NVD returned only a shell, so those two were not seen. The fixed upstream build numbers disagree between the
  Oracle and OpenJDK pages and are UNVERIFIED.
- **Local check (read-only, by the same sub-agent):** the installed JDK (`openjdk version "27-internal" 2026-09-15`,
  `C:\Program Files\DirtyChai\jdk`) lists the four aliases in `cacerts`; none of the three emSign root fingerprints is in
  its `lib\security\blocked.certs`; the source tree still ships the four data files and `java.security` has no eMudhra entry.
  A repository-wide search timed out, so only `src/java.base`, `make/data` and `test/jdk/sun/security` were searched.
- **Corrections to the 2026-10-31 news survey:** the count is eight, not sixteen; CVE-2026-114820 is confirmed in the CVE
  record, the OpenJDK advisory and Red Hat, so it is not single-source; the intermediates-only distrust was the first,
  insufficient fix and the root removal followed.
- **Reach into the JGDMS/DirtyChai stack (reasoning, not tested):** SPIFFE mutual TLS uses its own trust bundle, and
  downloaded code is digest-pinned, so those paths do not depend on `cacerts`; anything using the JDK default trust
  (HTTPS fetches, build tooling) does.
- **Escalation:** to the named human owner. Not a C3/C2b, but a silent removal of data files is the case the rubric
  says to always escalate.

## Mergeability check (2026-10-31, read-only; run in the DirtyChai clone)

Commands: `git show --stat`, `git tag --contains`, `git branch -r --contains`, and a simulated cherry-pick with
`git merge-tree --write-tree --merge-base=<fix>^ trunk <fix>` (no working-tree or ref change; it writes one
unreferenced tree object into `.git/objects`, which `git gc` prunes). `git status` was clean before and after.

- **The upstream fix:** commit `8e492bf50b8` "8395690: CA certificate improvements", Mark Powers, 2026-10-23. It
  deletes `src/java.base/share/data/cacerts/{comodoaaaca,emsigneccrootcag3,emsignrootcag1,emsignrootcag2}`, deletes the
  test `.../certification/EmSignRootG2CA.java`, removes the eMudhra entries from `.../certification/CAInterop.java`, and
  edits `test/jdk/sun/security/lib/cacerts/VerifyCACerts.java` (7 files, +3 -353).
- **Not in any local release tag:** `git tag --contains` lists no `jdk-27*` tag (the local tags run +5 to +9 and `jdk-27-ga`),
  so merging to `jdk-27+7` will not bring it. It is on `upstream/master` (and on three upstream `pr/` refs).
- **Simulated application to `trunk`:** the four data-file deletions and the `CAInterop.java` edit apply with no conflict.
  Two conflicts, both in test files: `EmSignRootG2CA.java` (modified on our side, deleted by the fix: resolve by deleting,
  which is the fix's intent) and `VerifyCACerts.java` (content conflict in the expected-certificates list).
- **Cause of the conflicts:** upstream drift, not DirtyChai edits. `git log trunk --author=pfirmstone` shows no DirtyChai
  commit touching the data files or those tests, and the merge-base of `trunk` and the fix's parent is upstream `6165daf03c8`
  (2026-01-22): other upstream changes to `VerifyCACerts.java` landed in between.
- **Policy (CLAUDE.md):** an inbound mechanical merge is permitted; conflicts are reported and a human authors the
  reconciling edit. I have not applied the cherry-pick.
- **Options (for the operator):** (a) cherry-pick `8e492bf50b8` onto `trunk` now and reconcile the two test files by hand,
  which fixes the production part immediately; (b) take a later upstream point that contains the fix, accepting a larger merge;
  (c) as an interim, remove the four data files only, which is the production half of the fix. The installed JDK and any
  built image still need the `keytool` removal until a rebuilt image exists.
- **Not checked:** whether `jdk-27+8`/`+9`/`ga` differ from `+7` in `src/java.base/share/data/cacerts`; whether other
  upstream commits between the merge-base and the fix also change that directory.

## Cherry-pick started (2026-10-31, DirtyChai `trunk`, on the operator's instruction "lets cherry pick now")

Command: `git cherry-pick -x 8e492bf50b8` from a clean `trunk` (HEAD `2ee51eddd60`). It stopped on two conflicts, as the
simulation predicted, and is **left in progress** (`.git/CHERRY_PICK_HEAD` exists) so that a human authors the reconciling
edits (CLAUDE.md: inbound merges are mechanical; conflicts are reported, not resolved). No commit has been made.

Staged cleanly: the four data-file deletions (`comodoaaaca`, `emsigneccrootcag3`, `emsignrootcag1`, `emsignrootcag2`) and the
`CAInterop.java` edit. After it the test's fingerprint map has 0 lines naming eMudhra or Comodo AAA (grep).

To reconcile (analysis only; the human writes the edits):
1. `EmSignRootG2CA.java` (status `UD`, deleted by the fix, modified on our side): the fix's intent is deletion, so `git rm` it.
2. `VerifyCACerts.java` (status `UU`, three conflict hunks):
   - the `@bug` list near line 31: ours ends `8361212`; the fix's side adds `8372351 8387123 8386371 8392771 8395690`. Four of those
     are other upstream fixes not in our tree; my suggestion is to add only `8395690`.
   - `COUNT` (line ~56): ours 109, the fix's side 107. The data directory has 110 files at HEAD including one `README`, so 109 certificates;
     after the four deletions it has 106 files, so **105 certificates**. The fix's 107 reflects upstream's different base (the drift).
     Expect 105, and confirm by running the test.
   - `CHECKSUM` (line ~64): the SHA-256 of the built `cacerts`. Neither side's value fits our tree; compute it from the rebuilt image with the
     command in the file's comment, or take the "Expected/Actual checksum" the test prints.
3. Then `git add` the two files and `git cherry-pick --continue`; the commit keeps the upstream author and message plus the
   `(cherry picked from commit 8e492bf50b8...)` line. Add no AI trailer. To back out: `git cherry-pick --abort`.
4. After the commit: rebuild the image, run `VerifyCACerts` and OpenJDK's `CheckCerts.java`, and remove the four aliases from the
   installed JDK with `keytool` until the rebuilt image replaces it.
