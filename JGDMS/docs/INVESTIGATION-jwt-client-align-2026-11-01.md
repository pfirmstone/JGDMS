claude-sonnet-5-5
# JWT client aligned with the hardened server rules (2026-11-01)

Worktree `...\JGDMS\.claude\worktrees\agent-ae07f9e6a3783c076`, detached at 34b490940, uncommitted. Patch: `SP\jwt-client.patch`, SP = `C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\jwt4`. Report is in the worktree (the patch carries it).
Dependency direction: `jgdms-security-jwt` already depends on `jgdms-platform`, so the mapping is shared, not duplicated.
Command: `mvn -o -pl jgdms-security-jwt,jgdms-platform,jgdms-der,jgdms-jeri test -Dtest=<classes> -Dsurefire.failIfNoSpecifiedTests=false`.

## What changed
- `DefaultJwtVerifier` (platform): `parseObject` is now `public`; new `public static identityFromClaims(payload, maxPrincipals)` holds the claims-to-`VerifiedJwt` mapping (exp, iss, aud, sub, email, `email_verified` JSON true, groups, empty-value refusals, cap). The server's `validateClaims` tail now calls it, so server and client run one code path and one grammar.
- `JwtValidator`: `parseClaimsJson` and its lax helpers deleted; header and payload go through `DefaultJwtVerifier.parseObject` (top level only, RFC 8259 numbers, duplicate names refused, nested values never lifted). Claims are derived by `identityFromClaims` with cap 64. `aud` is a string or string array, exact element match. Header `crit` refused, non-string `alg`/`kid` refused. `exp` is now required (the server requires it). New `parseTokenResponse` (same parser) for the OAuth2 refresh response.
- `ParsedJwt`: new component `principals` (the server-derived set).
- `JwtLoginModule`: Subject principals come from `ParsedJwt.principals()` (login and refresh), so `email:` appears only with `email_verified:true`; an over-cap or empty-value token fails `login()` with a `LoginException`. Refresh no longer puts the HTTP response body into exception text.
- Token hygiene: messages no longer echo token-derived values (kid, got-issuer, got-audience, JSON parse detail, JCA exception text). Grep of the module: no log line contains the token; `JwtRawToken.toString` shows the header segment but is not logged anywhere (left, platform class, not asked).
- Tests: `JwtLoginModuleTest` +8 (one existing email test now signs `email_verified:true`); `JwksJsonParserTest` lax `parseClaims*` tests (5 plus the truncation one) replaced by 3 `parseTokenResponse` tests. Behaviour change worth a look: a token with no `exp` now fails the client login.

## Fail-first (main code and JwksJsonParserTest at trunk, new JwtLoginModuleTest kept; `parseTokenResponse` tests cannot compile on trunk)
`Tests run: 20, Failures: 7`: emailOnlyWhenVerified, clientEqualsServerDerivedSet ("unverified email: client [email:a@b.c, sub:s] vs server [sub:s]"), nestedClaimsNotLifted ("nested exp was lifted"), audienceExactElement ("audience accepted: evil,svc-a"), sixtyFiveGroups, emptySub, noTokenFragment. After: `JwtLoginModuleTest 20`, `JwksJsonParserTest 16`, `JwtPrincipalTest 21`, platform `DefaultJwtVerifierClaimsTest 25`, `DefaultJwtVerifierNegativeTest 24`, `VerifiedJwtApiTest 3`: all `Failures: 0, Errors: 0`, BUILD SUCCESS.
Equality table (21 rows, client set vs `DefaultJwtVerifier` set or both refuse): verified/unverified/absent/string/1 `email_verified`, no/empty sub, empty/non-string group, empty email, duplicates, 63/64/65 principals, aud array/comma string/comma element, nested sub, duplicate member.

## Mutants (private copy of the worktree, one at a time, restore byte-compared True; class JwtLoginModuleTest)
| id | mutant | result |
|---|---|---|
| M1 | `VerifiedJwt` derives email without `emailVerified` | KILLED (emailOnlyWhenVerified) |
| M2 | `parseMap` lifts nested members | KILLED (nestedClaimsNotLifted) |
| M3 | `JwtValidator` comma-joins aud then `contains` | KILLED, 2 (equalsServer, audienceExactElement) |
| M4 | cap check `false &&` | KILLED, 2 (sixtyFiveGroups, noTokenFragment) |
Raw: `SP\mutants.txt`.

## Not verified
- Platform and jeri suites beyond the classes above; no end-to-end JERI call; no `-Pdirtychai` run; Linux.
- The refresh thread path (`updateSubject`, `parseTokenResponse` in `doRefresh`) has no test beyond the parser unit tests.
- `qa/jtreg/.../JwtLoginModuleTest.java` not run; it may build email tokens without `email_verified`.

## Questions
1. Keep `exp` mandatory on the client, or relax to match nothing the server accepts anyway?
2. Should `JwtRawToken.toString` stop showing the header segment?
3. Should `identityFromClaims` stay public API in `net.jini.security.jwt`, or move to a smaller shared class?
