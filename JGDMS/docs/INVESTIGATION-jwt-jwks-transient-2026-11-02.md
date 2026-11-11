claude-sonnet-5-5
# JwtLoginModule: an unreadable JWKS is a transient refresh failure, 2026-11-02

## Exception design
`JwksUnavailableException extends JwtValidationException` (package-private, new file). Thrown by `JwksKeyCache` (getKey/refresh/fetchAndUpdate, which used to log and return) ONLY for: connection error or timeout, interrupt, HTTP status != 200, body over 1 MiB, body with no `"keys"` array (new `parseJwksJson(json, sawKeys)` overload; the old one delegates). A JWKS read successfully that lacks the kid returns null from the cache: plain `JwtValidationException` (permanent). `JwtValidator.resolveKey` already refetches once on an unknown kid: kept; a failing refetch is unavailable, a good refetch without the kid is permanent. Subclassing means every existing `catch (JwtValidationException)` still fails closed by default; the two new `catch (JwksUnavailableException)` clauses sit before it. Messages hold status or class name only, no token text.
`doRefresh`: rethrows the signal itself (it is an `Exception`, so the loop's transient branch backs off 5 s doubling to 5 min; I did not wrap it in a plain Exception, so the class name survives). `refreshOnce` records `lastRefreshFailure = "JwksUnavailableException"` (class name only). `login()`: LoginException "JWT validation failed: the JWKS endpoint could not be read (...)".

## Permanent / transient after the change
| event | class |
|---|---|
| HTTP 400, 401, 403 from the token endpoint | permanent |
| 200 not one JSON object / no access_token | permanent |
| new token: bad signature, kid absent from a REACHABLE JWKS, other iss, wrong aud, expired, no exp, malformed claims, not a JWT | permanent |
| new token: other or missing sub; Error in thread | permanent |
| JWKS endpoint: refused, timeout, HTTP != 200, over 1 MiB, not a JWKS document (NEW) | transient, token kept |
| token endpoint: network error, timeout, over 64 KiB, 5xx, 408, 429, other non-200 | transient |
| first login, JWKS unreadable (NEW) | LoginException |

## Fail-first (new tests on unmodified main code; `failfirst.log`)
`Tests run: 43, Failures: 2`: `testJwksDownDuringRefreshKeepsTheTokenAndRecovers` (HTTP 500 gave FatalRefreshException, token removed), `testFirstLoginWithTheJwksUnreadableFailsWithALoginException` (message was "No public key found"). `testRefreshWithAKidTheReachableJwksLacksFailsClosed` passes on trunk by design (guard for the permanent class). After: `JwtLoginModuleTest 43, Failures 0`; JwksJsonParserTest 16, JwtPrincipalTest 21, DefaultJwtVerifier tests 47, VerifiedJwtApiTest 3, all 0; BUILD SUCCESS. The earlier permanent cases (bad signature, other iss, no exp, wrong aud, 400/401/403) are unchanged tests and pass.

## Mutants (`mut.py`, private `git archive` copy plus my files; restore byte-compared, equal to the worktree)
| id | mutant | result |
|---|---|---|
| M1 | JWKS-down caught as permanent again | KILLED, 1 failure (JwksDown test) |
| M2 | kid missing from a reachable JWKS thrown as unavailable | KILLED, 1 failure (kid test) |
| M3 | first login succeeds on JwksUnavailableException | KILLED, 1 error (first-login test) |
| M4 | unavailable stops the thread (never retries) | KILLED, 1 failure (JwksDown test, "thread is retrying") |
Unmutated copy: `Tests run: 43, Failures: 0`.

## Not verified
- Full platform/jeri suites, Linux, SecurityManager runs. Recovery is tested for HTTP 500 only; garbage 200 and oversized body are tested for keep-and-retry, not recovery. Connection refused is tested at first login only (the refresh test uses the one test server).
- Every refresh builds a fresh `JwksKeyCache`, so no stale-cache fallback exists: an unknown kid costs two fetches (existing behaviour).
- The refresh_token rotated by a refresh whose JWKS then failed is kept (assigned before validation); a retry uses it.

## Questions
1. A permanently failing JWKS (wrong URI, 404) retries forever while the old token lives, then the token expires. Acceptable, or cap the retries?
2. Should other non-200 token-endpoint statuses (404, 410, 3xx) be permanent? (carried over)
