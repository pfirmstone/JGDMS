claude-opus-5-5
# Review of 3b6562ade (JWT body deadline) and c28cb25a6 (PolicyLint slash/decoding, allow-list) (2026-11-02)
CONFIRMED = I ran it and saw the output. PLAUSIBLE = I reasoned it from the code and did not run it. SP = `C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\opus-r14`. The copy is `git archive` of HEAD d18bab9d8, extracted with Windows `tar.exe` into `SP\src`. Fixtures are byte-exact LF. This file is my only write outside SP. I did not run `install`. JDK: `DirtyChai\build\windows-x86_64-server-release\images\jdk`, `bin\java.exe` present, `27-internal-adhoc.peter.DirtyChai`, lib/modules dated 2026-11-01 22:52. Logs are in SP: `jwt-run1.log`, `lint-run1-clean.log`, `lint-run2-touched.log`, `probe-*.log`, `flake-*.log`, `smprobe\sm-probe.log`, `mutants.txt`, `mut-*.log`. Probes: `SP\src\JGDMS\...\OpusR14*ProbeTest.java`, `SP\smprobe`, `SP\overlay`.

## Tests (`mvn -o test`, `-Dtest` limited, reactors as briefed): 0 failures, 0 errors in every run
- JWT, 163 tests: JwtLoginModuleTest 48, JwksJsonParserTest 16, JwtPrincipalTest 21, JwtPrincipalBindingTest 27, WriteUserSubjectsLockTest 1, DefaultJwtVerifierClaimsTest 24, DefaultJwtVerifierNegativeTest 23, VerifiedJwtApiTest 3.
- Lint, 194 tests (2 skipped), run twice with the same counts: PolicyLintTest 49 (2 skipped), PolicyLintGateTest 30, PolicyLintCorpusTest 9, ListPromotionTest 46, ProxyPolicyGeneratorTest 45, PermissionSetsTest 2 + 13. Run 1 was `clean test`. Run 2 came after touching PolicyScope.java and DefaultPolicyScanner.java; 348 + 8 sources were recompiled. Corpus: `files covered=88 of clean=88`, `allow-listed=49`, `files-with-nothing-listed=26`, provider `au.zeus...ConcurrentPolicyFile`.

## The flake did NOT reproduce. Its signature is explained; the cause is PLAUSIBLE
- The four files (r6/a04, r6/a33, r8/q03, r8/z02) are exactly the corpus files with a principal CLASS wildcard (`grep -E 'principal\s+\*'`). Their P1 comes only from the provider's parser (PolicyLint.java:636-640). The text scanner accepts `principal *` (ProxyPolicyGenerator:1821).
- CONFIRMED (`SP\flake-overlay-probe.log`): with a parser that accepts `principal *`, exactly these four lose P1 and keep every other kind. That is the reported symptom. I compiled an overlay of DefaultPolicyScanner with the JGDMS 5f88b1900 rejection undone and ran it with `--provider=platform`.
- Both current providers reject the wildcard: DirtyChai #236 in the image, and JGDMS 5f88b1900. Both give P1, with and without `--add-exports` (`SP\flake-provider-probe.log`). So a run without P1 used a parser from before those changes. Candidates: a DirtyChai image older than #236 (or one being rebuilt) on JAVA_HOME, or the fallback to a stale jgdms-platform. The installed `~/.m2` jgdms-platform-4.0.0-SNAPSHOT.jar (2026-11-01 02:38) accepts `principal *`. But the current lint gets NoSuchMethodError (`PermissionSets.probe`) against it, so `-pl tools/policy-condenser` alone would fail loudly, not silently.
- Gap: `corpusExitCodesAndKinds` never asserts which provider answered. A silent fallback or an old JDK changes the expectations without saying why. The lint agent's saved logs (`lint7\*.log`) do not contain the failing run.

## Earlier Opus findings 1 to 5 (REVIEW-refresh-jwks-lint-opus-2026-11-02.md)
| # | status | where | pinning test |
|---|---|---|---|
| 1 localhost/// and %2F/%5C read as local | ADDRESSED | PolicyScope.java:313-342 (`file`), :261-283 (`percentDecode`) | Gate `localhostIsLocalOnlyBeforeASinglePathSlash`, `percentEncodedSeparators...`, `aMalformedEscape...`; corpus n102-n106. Mutant ML-A killed |
| 2 refetch-fails ordering untested | ADDRESSED | JwtValidator.java:239-250 (unchanged) | `testRefetchFailingAfterAnUnknownKidKeepsTheOldToken` (JwtLoginModuleTest:1283) |
| 3 allow-list boilerplate, global entries | ADDRESSED, with residuals (findings 2, 3 below) | Corpus `allowListProblems` :282, `judgeListed` :561-582, `staleReferences` :316; tsv now has 49 distinct reasons and one file each | `everyAllowEntryNeeds...` :684, `anAllowEntryCovers...` :707, `aStaleFileReferenceFails` :737 |
| 4 permanent rules sound | no action was needed | - | - |
| 5 body reads had no timeout | ADDRESSED, with residuals (findings 1, 4) | BoundedBody.java:69-92; JwksKeyCache:229; JwtLoginModule:764 | three `testDripped*` tests (:1184, :1218, :1255). Mutant MJ-A killed |
| (not checked last time) refresh without additionalClaims | ADDRESSED | JwtLoginModule.java:807-808 matches :274-275 | `testRefreshedTokenIsReadWithTheLoginsAdditionalClaims`. Mutant MJ-B killed |

## Findings, most important first
1. **Under a SecurityManager, the first body read needs `RuntimePermission "createPlatformThread"` on the caller's whole stack.** CONFIRMED (`SP\smprobe\sm-probe.log`). DirtyChai's `Thread` constructor checks it (Thread.java:771-773). The watchdog thread is created lazily inside `WATCHDOG.schedule` (BoundedBody:71), with no doPrivileged. My probe ran under CombinerSecurityManager, with the jwt classes holding AllPermission and the calling class lacking that one permission. Both reads threw `AccessControlException createPlatformThread`; with the grant, both passed.
   - In JwksKeyCache:250 the exception becomes "JWKS fetch failed: AccessControlException". At login that is a LoginException. On refresh it is transient and retried forever.
   - Whether it fails depends on which caller first creates the thread. After a privileged caller has created it, unprivileged callers pass.
   - Before this commit, a login without refresh needed no thread permission. The RefreshThread already needs it (PLAUSIBLE: same constructor path).
   - The thread also inherits the first caller's context class loader. CONFIRMED: `ccl=AppClassLoader`, group `main`. That can pin a webapp loader.
2. **A glob of only wildcards and slashes defeats the per-file rule.** CONFIRMED.
   - `allowListProblems` (:304) flags only references that are entirely `*`. `*` and `**` match no corpus file anyway, since every path has a slash. `*/*` (which matches every file) and `r8/*` are ACCEPTED (`SP\probe-lint.log`).
   - A glob-only entry also skips the citation rule (:303-310, `exact` stays false).
   - Mutant ML-C sets exitVM's files to `*/*` in the tsv: the corpus suite stays green (survived). The unit test's "matches everything" case (:702) uses `*`, which is the wrong example.
   - Today every tsv entry names one exact file, so nothing is hidden yet.
3. **The other allow-list rules hold, but they are textual.** CONFIRMED by probe and mutants.
   - Empty, blank and under-25-character reasons fail. Identical reasons fail (exact string after trim; a single changed character passes). A reason citing none of the stems fails (ML-B killed). No file fails. A file outside the list fails (`outsideList`, :574). A stale reference fails (:605). An entry nothing uses fails (:617-620).
   - Weak spots: "cites" needs only one stem among several files. A reason that is only the stem repeated (`r16-rt-exitVM r16-rt-exitVM r16`) is accepted.
   - Outside-list and stale (the lint agent's M5 and M6) are pinned only by synthetic tests. Those tests call the real `judgeListed` and `staleReferences`, so I think that is enough. The real corpus cannot exercise outside-list, because each permission comes from one fixture.
   - The HIGH check runs first and independently of the allow-list (judgeListed:542-546, plus the question loop :498-506). No entry can hide a catalogued HIGH.
   - Broadest entries: `[A-Za-z]:[\\/]- read` (r8/f18), `${user.home}[\\/]- read` (n/n26), `<<ALL FILES>> readlink` (r8/f05), `SocketPermission * resolve` (r8/s03), `*.com connect,resolve` (r8/s04), `${java.io.tmpdir}[\\/]*\* write` (n/n20), setIO, exitVM, modifyPublicCredentials, setLoginConfiguration, doAs. All are D5 under the existing rulings.
4. **BoundedBody is correct on every stream shape I tried.** CONFIRMED (`SP\probe-jwt.log`, 10 probe tests).
   - Size: exactly the limit gives 64 bytes; limit+1 and a 100 000-byte stream give 65, which the callers reject at :230 and :765. So the 64 KiB and 1 MiB caps still hold alongside the deadline.
   - A throwing stream gives the plain IOException, not a timeout. A one-byte drip every 20 ms against a 400 ms deadline is cut at 413 ms.
   - A stream that blocks until it is closed and then returns EOF (the "truncated JSON passes as the whole body" case) gives a timeout, at 302 ms. The same stream ending with a throw also gives a timeout.
   - `expired` is set before `close()` (:72-74), so no early EOF can be read as a normal end. HttpResponseInputStream.close offers LAST_LIST and wakes a blocked `take()` (DirtyChai ResponseSubscribers:641-659), so the real stream throws "closed".
   - Concurrency: 8 concurrent reads each met their own deadline (200/400/601/800 ms) while fast reads passed. After 10 000 completed reads the queue held 0 tasks: the cancel works, and `setRemoveOnCancelPolicy` holds.
   - **But** the post-read check (:87) is not pinned by any commit test. Mutant MJ-C removes it and survives, because the real HTTP stream throws instead. My probe `blockUntilClosedThenEof` kills it.
   - One watchdog thread serialises closes: a `close()` that blocks 2 s held another read's 100 ms deadline to 1951 ms (CONFIRMED with a synthetic stream; HttpResponseInputStream.close does not block, PLAUSIBLE).
   - Logout interrupt: `take()` throws InterruptedException, the stream closes, an IOException is not a timeout, and the loop returns because `!running` (PLAUSIBLE, read only). The timeout drops the cause (cosmetic).
5. **The option is validated.** CONFIRMED. `99`, `120001`, `abc`, `1e3`, `-1`, `0`, `1000.0` and a 20-digit number give a LoginException (or IAE in `parseDeadline`). Blank or null gives 10000; Integer and Long values are accepted. The bound is not pinned by any commit test: mutant MJ-D (range check off) survived.
6. **additionalClaims cannot override a security claim.** READ. JwtValidator.java:270-283: each extra name is looked up in the same `payload` under the same key, so naming `iss`, `sub` or `email` re-puts the identical value. `aud` and `groups` are arrays, so `scalar` returns null and nothing is put. `exp` is overwritten afterwards (:281). Principals come only from `identityFromClaims(payload)` (:222). The module reads only `exp` and `iss` from allClaims (:287, :350, :489, :824). Login and refresh build the same `JwksKeyCache(uri, bodyDeadlineMs)` and `JwtValidator(cache, issuer, audience, additionalClaims)`. Minor: `split(",\\s*")` does not trim a leading or trailing space.
7. **The decoding is right and nothing throws.** CONFIRMED (`SP\probe-lint.log`).
   - It decodes once: `%252F` gives `%2F`, and `file:/%252F%252Fhost` is FILE_PINNED.
   - It equals `URI.getPath()` (used by `File(URI)`) on all 7 inputs, including `%C0%AF` and `%E0%80%AF` (U+FFFD, never `/`), `%FF%FE` and `%00`. ParseUtil (used by URLClassPath) instead REJECTS malformed UTF-8, which is harmless.
   - A decoded drive letter is judged as a drive (`%43%3A`). `%5C%5C%3F%5CUNC` gives a share. `jar:` uses the first literal `!/`, as JarURLConnection does; `%21` stays inside the path. `jar:file:/%2F%2Fhost/a.jar%21/x!/` gives a share.
   - null, empty, `file:`, `file:%`, `file:%2`, `jar:`, `jar:!/` and `jar:file:%!/` all classify without throwing. 100 000 slashes, `%2F`, `%C3` or path segments take 2-39 ms.
   - Pre-existing and outside the diff: 20 000 nested `jar:` take 830 ms (quadratic `substring`, :187-192).

## Mutation sample (one at a time; each file restored, sha256 equal, all `True`)
| id | commit | mutant | result |
|---|---|---|---|
| MJ-A | 3b6562ade | watchdog does not close the stream | KILLED: the 3 `testDripped*` (4012 ms, not cut off, 15004 ms) |
| MJ-B | 3b6562ade | refresh validator without additionalClaims | KILLED: `testRefreshedTokenIsReadWithTheLoginsAdditionalClaims` (null vs ops) |
| ML-A | c28cb25a6 | "decoded path starts with 2+ slashes is a share" removed | KILLED: Gate x4, Corpus n101 |
| ML-B | c28cb25a6 | "reason cites no fixture" check removed | KILLED: `everyAllowEntryNeedsItsOwnSpecificReason` |
| MJ-C, MJ-D, ML-C | both | post-read expired check removed / deadline range off / tsv file `*/*` | SURVIVED (findings 4, 5, 2) |

## Not checked
ConcurrentPolicyFile matching on the new codeBases (no policy files, per the brief); Linux; the full platform, jeri and der suites; a live logout during a blocked body read; the RefreshThread under a SecurityManager.

## For the operator to settle
1. Must JwtLoginModule work under a SecurityManager for callers that lack `createPlatformThread`? If so, should the watchdog be created eagerly or privileged, and in which policy files should the permission be granted (finding 1)?
2. Should file references in the allow-list be exact paths only, with globs forbidden (finding 2)?
3. Should the corpus test fail when the provider is not `au.zeus.jdk.authorization.policy.ConcurrentPolicyFile` (flake section)?
4. Which JAVA_HOME and image did the lint agent's two failing runs use?
