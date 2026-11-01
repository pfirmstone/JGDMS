claude-opus-5-5
# Review of 14fbbd08c, 11080ae29, 3a65485c0 (2026-11-02)

CONFIRMED = I ran it in a private copy and saw the output. PLAUSIBLE = reasoned from the code, not run. SP = `C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\opus-r13`. The copy was made with `git archive` of HEAD 2901c190e, which contains all three commits, and extracted with Windows `tar.exe` into `SP\src`. This file is my only write outside SP. I did not edit the live JGDMS tree or DirtyChai, and I never ran `install`. JDK: `DirtyChai\build\windows-x86_64-server-release\images\jdk` (`bin\java.exe` present, `27-internal-adhoc.peter.DirtyChai`). Logs: `SP\jwt-run.log`, `SP\lint-run.log`, `SP\probe-jwt.log`, `SP\probe-slash.log`, `SP\mutants.txt`, `SP\mut-*.log`. Scripts: `SP\tools`, `SP\probe`.

## Tests (`mvn -o test`, `-Dtest` limited to the classes below, reactors as briefed): 344 run, 0 failures, 0 errors, 2 skipped, both builds exit 0
JwtLoginModuleTest 43, JwksJsonParserTest 16, JwtPrincipalTest 21, WriteUserSubjectsLockTest 1, JwtPrincipalBindingTest 27, DefaultJwtVerifierClaimsTest 24, DefaultJwtVerifierNegativeTest 23, VerifiedJwtApiTest 3. PolicyLintTest 49 (2 skipped), PolicyLintGateTest 25, PolicyLintCorpusTest 6, ListPromotionTest 46, ProxyPolicyGeneratorTest 45, PermissionSetsTest 2 (tools) + 13 (platform). Every count matches the commit messages. The corpus test printed `files covered=87 of clean=87` and `permissions listed=4064 distinct-per-file=63 allow-listed=49 implying-no-universe-question=49 files-with-nothing-listed=25 subjects-listed=57`.

## Earlier findings 1 to 5 (REVIEW-lint-and-jwt-lifecycle-opus-2026-11-02.md); line numbers are from the HEAD copy
| finding | status | where | pinning test (mutant that kills it) |
|---|---|---|---|
| 1 refreshed token that fails validation is transient | ADDRESSED | JwtLoginModule.java:788-792 throws FatalRefreshException; loop:647-657 fails closed | `testRefreshWithADifferentIssFailsClosed`, `testRefreshResponseThatIsNotAValidTokenFailsClosed`, `...KidTheReachableJwksLacksFailsClosed` (Ja) |
| 2 cross-check independent in classes, not names | ADDRESSED, with a residual | PolicyVerdict.java:381 `listedToEach`; PolicyLintCorpusTest `judgeListed` (:422) judges every listed element | `aSpecificNameGrantOfAnUncataloguedClassIsReported`, `crossCheckFailsAgainstAProviderThatListsNothing`, `cleanFilesGrantNoStrangerAHighPermission` (Lb). Residual: finding 3 below |
| 3 `file://///host` read as pinned | ADDRESSED for the plain forms; see finding 1 below | PolicyScope.java:268-289 | Gate `slashRunTable`, `slashRunsOfAFileCodeBaseDecideLocalOrShare`, corpus n93-n101 (La) |
| 4 HTTP 400 other fails closed only in `loop`, untested | ADDRESSED | loop :654 is now reached by tests on the real thread | `testPermanentHttpStatusesFailClosedOnTheRealThread` and 4 others (Jb; Jb is the old J2, which survived before) |
| 5 HTTP 401/403 transient | ADDRESSED | JwtLoginModule.java:750-754 | `testPermanentHttpStatusesFailClosedOnTheRealThread` |

## Findings, most important first
1. **The slash rule still reads two Windows share forms as local.** CONFIRMED with a probe that calls `PolicyScope.classify` directly and resolves each URL the way `URLClassPath.FileLoader` does (`getFile`, `/` to `\`, percent-decode, `new File`). Output is in `SP\probe-slash.log`.
   - `file://localhost///host/share/-` gives FILE_PINNED, but the path is `\\host\share\x.jar`. The same holds for `file://localhost/\\host\share/-` and `jar:file://localhost///host/share/a.jar!/`. Cause (:282-284): after localhost, the remaining path `///host` reaches the `n == 3` "empty authority" branch. That branch only makes sense before an authority, not after one. `file://localhost//host` and `file://localhost////host` give REMOTE_WILD, which is correct.
   - Percent-encoded separators give FILE_PINNED, but Windows opens `\\host\share`: `file:/%2F%2Fhost/share/-`, `file:/%2Fhost/share/-`, `file:///%2Fhost/share/-`, `file:/%5C%5Chost/share/-`. The rule never decodes. `file:%2F%2Fhost/...` gives FILE_WRITABLE (relative), which is still a finding.
   - Correct (local): `file:/C:/x`, `file:///C:/x`, `file:/\\host` (the Windows URL handler rewrites `\` before parsing, so this is `/host`). Correct (stranger, fail closed): `file://C:/x`, `file:////C:/x`, `file://127.0.0.1`, `file://localhost:80`, `file://LocalHost.`.
   - Whether ConcurrentPolicyFile would match these codeBases to code loaded from the share is PLAUSIBLE only (that needs a policy file). It is the same open question as for the five-slash form.
   - Both forms can be written as unit tests on `classify`, in the style of `slashRunTable`.
2. **A MITM or a faulty JWKS can turn "kid not listed" into a transient failure.** CONFIRMED by a probe test on the real thread (`SP\probe-jwt.log`, added to the copy and then removed, sha256 equal).
   - Setup: the refreshed token has an unknown kid. The first JWKS fetch succeeds, and the refetch returns 500.
   - Result: `why=JwksUnavailableException alive=true tokensIsT1=true names=[group:a, sub:alice] expiries=1`. The path is JwtValidator.java:246, where `refresh()` throws.
   - The old, already-accepted token is kept until its own exp, and the thread retries. I think this is acceptable under the ruling.
   - **A new token is never installed against a stale key set.** Each refresh builds a new `JwksKeyCache` (JwtLoginModule.java:778), and validation stops at the throw. A body with no `"keys"` array, an oversized body and a non-200 status all behave the same way. `{"keys":[]}` stays permanent.
   - No test pins the "refetch fails" ordering.
3. **The allow-list inherits the catalogue's ratings for 33 of its 49 entries.** READ.
   - Matching uses class AND whole-name regex AND whole-actions regex (Corpus `Allow.allows`). No name regex is `.*`.
   - The HIGH check runs before the allow-list and does not depend on it. A stale entry fails the test (:512).
   - The 25 no-listing files: I read each one. Each is pinned by codeBase, signer, keystore, digest, a named or unloadable principal, or a dropped permission, as its tsv reason says.
   - The 33 MEDIUM entries all carry the same boilerplate reason ("Rated MEDIUM (D5) ... accepted by the default run"). For those 33, the "independent" listing check therefore repeats the catalogue: an under-rated class would pass both halves.
   - The broadest entries: `FilePermission [A-Za-z]:[\\/]- read`, `${user.home}[\\/]- read`, `<<ALL FILES>> readlink`, `SocketPermission *.com connect,resolve`, RuntimePermission `setIO`, `exitVM`, `accessClassInPackage.sun.*`, and AuthPermission `modifyPublicCredentials`, `setLoginConfiguration`, `doAs`.
   - The entries are global, not per file. `${java.io.tmpdir}[\\/]*\*` also accepts zero separators (harmless).
4. **The permanent rules are sound.** CONFIRMED by tests and mutants. 400, 401, 403, malformed 200, missing `access_token`, validation failure (iss, aud, signature, expired, no exp, not a JWT, unlisted kid) and a different or missing sub all end with the thread stopped and nothing of ours left, while a foreign principal stays (`assertThreadFailsClosed` checks all of this). 5xx, 408, 429 and JWKS outages keep the token (tests plus Jc and Jd). The caps are bounded `readNBytes` calls (JWKS 1 MiB at :214, token response 64 KiB at :736), and a non-200 JWKS body is closed unread. No token text appears: I grepped the added lines. The messages are fixed text, status codes, class names, the expected iss/aud, or the exp instant.
5. **The body reads have no timeout.** PLAUSIBLE. `HttpRequest.timeout` covers only the wait for headers, so a server that drips its body holds the refresh thread (or the first login) until the cap. Logout still interrupts it. The worst case is the same as a transient failure.
6. **No new race.** READ. The new permanent branches all go through `failClosed`, which locks `caller.subject` and returns early once logout, abort or a second commit has cleared `running`. Line :687 writes `lastRefreshFailure` outside the lock (cosmetic, as before).
7. **The NOTE path is unchanged** (PolicyLint.java not touched since 79b560986). READ. :707-714 is reached only when `danger` is null, so a catalogued HIGH is never noted away and the exit status is not changed. In the corpus test, a listed uncatalogued class that is neither allow-listed nor seen by a question fails `judgeListed` (Lb output names n80 ExportPermission and n88 SQLPermission).

## Mutation sample (one at a time in the copy; each file restored and checked by sha256, all `sha-equal= True`)
| id | commit | mutant | result |
|---|---|---|---|
| Ja | 14fbbd08c | refreshed-token validation failure thrown as plain Exception (transient) | KILLED, 3 failures (DifferentIss, NotAValidToken, KidTheReachableJwksLacks) |
| Jb | 14fbbd08c | `loop` permanent branch drops `owner.failClosed` | KILLED, 5 failures incl. `testPermanentHttpStatusesFailClosedOnTheRealThread` |
| Jc | 11080ae29 | `sawKeys` check removed (a non-JWKS 200 becomes "no kid": permanent) | KILLED: `testJwksDownDuringRefreshKeepsTheTokenAndRecovers` |
| Jd | 11080ae29 | `catch (JwksUnavailableException)` in `doRefresh` removed (outage becomes permanent) | KILLED: the same test |
| La | 3a65485c0 | after localhost, `continue` becomes `break` | KILLED: Gate `slashRunTable`, `slashRunsOf...`, Corpus `corpusExitCodesAndKinds` |
| Lb | 3a65485c0 | `listedToEach` no longer resolves UnresolvedPermission | KILLED: `cleanFilesGrantNoStrangerAHighPermission` |

## Not checked
- ConcurrentPolicyFile matching on the finding-1 codeBases (no policy files, per the brief); Linux; the full platform or jeri suites.
- SecurityManager-installed runs of JwtLoginModule; a body-drip server (finding 5); the read-only-Subject branch of `installRefreshed`.
- That the refresh validator is built without `additionalClaims` (:779), unlike login (:253). It does not change principals, but `allClaims` extras differ after a refresh.

## For the operator to settle
1. Are `file://localhost///host/...` and percent-encoded separators (`%2F`, `%5C`) in a `file:` codeBase remote shares for the lint (finding 1)?
2. May a clean (exit 0) file grant the 33 MEDIUM permissions in `cross-check-allow.tsv` to a stranger? In particular, are the catalogue ratings of `modifyPublicCredentials`, `setLoginConfiguration`, `setIO`, `C:\- read` and `${user.home}\- read` right?
3. Should allow-list entries be tied to the files that justify them (the investigation's question 2)?
4. After the old token expires, a transient-failure retry that later succeeds installs a fresh identity, so the session does not end at expiry. Is that intended?
