claude-sonnet-5-5
# JwtLoginModule refresh-thread fixes (R1-R4), 2026-11-02

Population: the Opus review findings 1, 4, 5, 7/8 (REVIEW-lint-and-jwt-lifecycle-opus-2026-11-02.md). Base 473d246ea, worktree only, nothing committed.
Scoring: "Tests run" lines are from `mvn -o -pl jgdms-security-jwt,jgdms-platform,jgdms-der,jgdms-jeri test -Dtest=<classes> -Dsurefire.failIfNoSpecifiedTests=false` (JAVA_HOME = DirtyChai release image).

## Changes per file
- JwtLoginModule.java: `doRefresh` throws FatalRefreshException (permanent, caught by the thread loop, which calls `failClosed`) for 401/403, a 200 body that is not one JSON object, a 200 without `access_token`, and `JwtValidationException` from the new token. Class javadoc rewritten: permanent and transient lists, R4 lifetime sentence.
- JwtValidator.java: R4 sentence (client does not enforce the server's max token lifetime; raise `maxLifetime` on the server verifier). No client-side limit added.
- DefaultJwtVerifier.java: removed the two dead null guards (`expectedIssuer`, `expectedAudience`; constructor requires both); javadoc for iss and aud no longer says "if configured". Behaviour identical.
- JwtLoginModuleTest.java: `/token` hook `tokenEndpointStatus`; helper `assertThreadFailsClosed` drives the REAL thread (token inside the skew, so it refreshes at once; `Thread.join(10 s)`, no sleeps). `testRefreshWithADifferentIssIsRefused` became `testRefreshWithADifferentIssFailsClosed`; `testRefreshResponseThatIsNotAValidTokenLeavesTheSubjectUnchanged` became `...FailsClosed` (the old "Subject unchanged" expectation is replaced by the operator's ruling; `assertRefreshRefused` is deleted with them, `assertUnchanged` is now unused but kept). New: `testMalformedTokenResponseFailsClosed`, `testPermanentHttpStatusesFailClosedOnTheRealThread`, `testTransientHttpStatusesKeepTheTokenAndTheThread`.

## Permanent / transient as implemented
| event | class |
|---|---|
| HTTP 400 (invalid_grant or not), 401, 403 | permanent, fail closed |
| 200, body not one JSON object / no `access_token` (my choice: IdP malfunction) | permanent |
| new token: bad signature, unknown kid, other iss, wrong aud, expired, no exp, malformed claims, not a JWT | permanent |
| new token: other or missing sub | permanent (unchanged) |
| Error in the thread (not OOM) | permanent (unchanged) |
| network error, timeout, body over 64 KiB | transient, back-off, token stays |
| HTTP 5xx, 408, 429 | transient |
| any other non-200 status (404, 3xx, other 4xx) | transient (unchanged; not in the ruling) |

## Fail-first (new tests against unmodified main code; log `jwt7\failfirst-keep.log`)
`Tests run: 40, Failures: 4`: `testRefreshWithADifferentIssFailsClosed` (another iss), `testRefreshResponseThatIsNotAValidTokenFailsClosed` (not a JWT, first case), `testMalformedTokenResponseFailsClosed` (not JSON), `testPermanentHttpStatusesFailClosedOnTheRealThread` (400 cases passed on trunk, 401 failed). `testTransientHttpStatusesKeepTheTokenAndTheThread` passes on trunk, as intended (negative guard).
After the fix: `JwtLoginModuleTest Tests run: 40, Failures: 0`; `DefaultJwtVerifierClaimsTest 24`, `DefaultJwtVerifierNegativeTest 23`, `VerifiedJwtApiTest 3`, `JwtPrincipalTest 21`, all 0 failures; BUILD SUCCESS.

## Mutants (script `jwt7\mut.py`, log `jwt7\mut.log`; one at a time in the worktree, file restored and `filecmp` byte-identical each time)
| id | mutant | result |
|---|---|---|
| M1 | validation failure thrown as plain Exception (transient) | KILLED, 2 failures (iss, not a JWT) |
| M2 | `if (false)` for 401/403 | KILLED, 1 failure (401) |
| M3 | thread loop skips `owner.failClosed` (old J2) | KILLED, 4 failures (all thread-driven; refreshNow tests do not see it) |
| M4 | HTTP >= 500 permanent | KILLED, 1 failure (503 retrying) |
| M5 | `failClosedLocked` leaves the JwtRawToken | KILLED, 10 failures |
Each run: `mvn -o -pl jgdms-security-jwt,jgdms-platform,jgdms-der,jgdms-jeri test -Dtest=JwtLoginModuleTest ...` and its `Tests run: 40, Failures: N` line is in mut.log.

## Not verified
- Full platform/jeri suites, Linux, SecurityManager-installed runs. Mutants ran on the patched worktree, not a git-archive copy.
- `JwksKeyCache` runtime failures inside `validate` stay transient (plain exceptions), but an unreachable JWKS endpoint makes `resolveKey` return null: that is a JwtValidationException ("No public key"), so a JWKS outage during a refresh is now PERMANENT.

## Questions
1. Is a JWKS outage at refresh time (above) acceptable as permanent, or should it stay transient? A fix needs a distinct exception type from the validator.
2. Should other non-200 statuses (404, 410, 3xx) be permanent too?
