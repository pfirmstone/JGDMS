claude-sonnet-5-5
# JwtLoginModule: Opus follow-ups 4 (W1 body deadline, W2 refetch ordering, W3 additionalClaims), 2026-11-02

## W1: total deadline on response bodies
Mechanism: new package-private `BoundedBody.read(in, limit, deadlineMs)`. `java.net.http` has no per-read timeout (only `HttpRequest.timeout`, headers only), so a per-read timeout was not available and would not catch a one-byte-per-150-ms drip anyway. A shared daemon `ScheduledThreadPoolExecutor` (1 thread) closes the body stream at the deadline; closing cancels the exchange and wakes the blocked `readNBytes` (shown by the tests). After the read, a flag set by the watchdog turns any outcome, including a clean-looking early EOF, into `SocketTimeoutException`. Least invasive: two call sites, no new HTTP stack.
Value: default 10000 ms, option `responseBodyTimeoutMs` (100 to 120000; bad value = LoginException at login). Token endpoint: the exception is a plain IOException, so the loop's transient branch backs off and the token stays. JWKS: caught by `fetchAndUpdate` as JwksUnavailableException ("JWKS fetch failed: SocketTimeoutException"); first login = LoginException.
## W3: what I found
Login passes `additionalClaims` to the validator; refresh did not (`new JwtValidator(cache, issuer, audience)`). So the paths were NOT equivalent: a refreshed ParsedJwt lacked the configured extras. BUT `additionalClaims` is only a list of claim names to COPY into `allClaims`, not a requirement: a missing claim is not a failure on either path, and nothing reads the extras after login (parsedJwt is cleared). So the requested test (refreshed token lacking the claim = permanent) does not apply and I did not invent a requirement. Refresh now gets the same set (module field -> RefreshThread). Observable only through a new package-private `lastRefreshed` (ParsedJwt of the last installed refresh; nothing in main code reads it).
## W2
Refetch-500 ordering already passes on trunk (a pin; mutant below). Refetch-succeeds-without-kid is permanent: already pinned by `testRefreshWithAKidTheReachableJwksLacksFailsClosed`, so no new test (mutant W2b kills it).
## Changes
BoundedBody.java (new); JwksKeyCache (deadline field, ctor `(URI,long)`, body read via BoundedBody); JwtLoginModule (option, doc row, `additionalClaims` and deadline fields to RefreshThread, `lastRefreshed`); JwtLoginModuleTest (+5 tests, 43 -> 48; the test server now uses a cached daemon pool so a dripping handler does not block the other endpoint; drip, JWKS request budget and `waitUntil` helpers).
## Fail-first (new tests on trunk + only the `lastRefreshed` field; JwtLoginModuleTest `Tests run: 48, Failures: 4`)
| test | trunk | after |
|---|---|---|
| DrippedTokenResponseIsCutOff...Retried | FAIL (not cut off in 4 s) | pass |
| DrippedJwksDuringRefreshIsCutOff... | FAIL (null failure at 4 s) | pass |
| FirstLoginWithADrippedJwks... | FAIL (login failed after 15006 ms) | pass |
| RefreshedTokenIsReadWithTheLoginsAdditionalClaims | FAIL (expected ops, was null) | pass |
| RefetchFailingAfterAnUnknownKid... (W2 pin) | pass | pass |
After: `mvn -o -pl jgdms-security-jwt,jgdms-platform,jgdms-der,jgdms-jeri test -Dtest=JwtLoginModuleTest,...`: JwtLoginModuleTest 48, JwksJsonParserTest 16, JwtPrincipalTest 21, DefaultJwtVerifierClaims 24, Negative 23, VerifiedJwtApi 3, JwtDispatcherFailClosed 15, JwtPrincipalBinding 27; all 0 failures; BUILD SUCCESS.
## Mutants (private copy of the worktree, one at a time, restored and `cmp`-equal; same mvn command, `-Dtest=JwtLoginModuleTest`)
| mutant | result |
|---|---|
| token body read without deadline | KILLED `Tests run: 48, Failures: 1` (DrippedTokenResponse...) |
| JWKS body read without deadline | KILLED `Tests run: 48, Failures: 2` (DrippedJwks..., FirstLoginWithADrippedJwks...) |
| refetch failure swallowed in resolveKey (stale key set; the literal "installs the new token" needs a bypassed signature check, not built) | KILLED `Failures: 1` (RefetchFailing...: got permanent FatalRefreshException) |
| W2b: refetch ok without kid becomes transient | KILLED `Failures: 1` (RefreshWithAKidTheReachableJwksLacks...) |
| refresh validator drops additionalClaims | KILLED `Failures: 1` (RefreshedTokenIsReadWith...) |
## Not verified
Under a SecurityManager (watchdog thread creation needs thread permissions; RefreshThread has the same need). Slow-machine timing: assertions allow deadline 1 s + 3 s. A server drip of headers (covered by the existing 15 s request timeout). Full platform suite not run. CRLF kept in edited files.
## Questions
1. Should `additionalClaims` ever be a requirement (claim must be present)? Today it is extraction only.
2. `lastRefreshed` keeps the last refreshed claims in memory (tests only); drop it and test via a hook elsewhere?
