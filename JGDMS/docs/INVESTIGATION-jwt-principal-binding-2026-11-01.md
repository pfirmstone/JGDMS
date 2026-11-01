claude-sonnet-5-5
# JWT principal binding, assert-principal permission, verifier hardening (2026-11-01)

SP = C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\jwt2
Patch: SP\jwt-bind.patch (`git diff HEAD` after `git add -N`; includes this file). Worktree `C:\Users\peter\Documents\GitHub\JGDMS\.claude\worktrees\agent-a6c87b42f62544696`, uncommitted. It started BEHIND trunk (no b4b9bd562), so I ran `git reset --hard trunk` there (clean, my own); base 54820fec6. The tool refused writes to the shared checkout, so this report sits in the worktree's JGDMS\docs.

## What changed
- NEW `jgdms-platform/.../security/jwt/VerifiedJwt`: immutable sub, iss, aud (List), exp, email, groups, principals. ONE mapping `derivePrincipals` (sub:, email:, group:) called by its constructor; the dispatcher uses `getPrincipals()` as given. `unauthenticated(...)` = no principals.
- `JwtVerifier.verify` returns `VerifiedJwt`. Deviation: NO legacy void path (only DefaultJwtVerifier and tests implemented it; a void verifier cannot be bound, so it is a compile error for deployers instead of a silent weaker mode).
- `DefaultJwtVerifier`: strict JSON parser (top-level members only, duplicate member refused, depth 16, exp must be a JSON number, `1e999999999` refused before BigDecimal scaling); `aud` string or array, whole-element match; signature mode refuses null/empty issuer AND audience at construction; header `typ` must be JWT (any case) if present; exp at most 24 h ahead (`DEFAULT_MAX_LIFETIME`; 5-arg constructor takes a Duration); claims-only mode kept but yields NO principals. alg pin, kid, crit, key size, exp required, nbf, iat unchanged.
- NEW `net.jini.security.AssertPrincipalPermission(principalClassName|"*", namePattern)`: name = class, actions = pattern (`*`, exact, `prefix*`, `*suffix`, matched against `Principal.getName()`, RFC 2253 for X500). No existing class fitted (AuthenticationPermission is for constraints, AccessPermission for methods).
- `BasicInvocationDispatcher`: `readUserSubjects(in, peer)`; `peerSubject(context)` takes the ClientSubject element; new `bindPrincipals`; cache value `CachedJwt(until, VerifiedJwt)`; expiry from the verified claims (removed the substring `extractJwtExp`); a null verifier result is a refusal; `peerDomain` extracted from `checkClientPermission` and reused.

## Rules per user Subject
1. JwtPrincipals = union of principals derived from its verified tokens (client may send none, a subset or all). A wire JwtPrincipal outside that set = IOException (call refused), even for a peer holding `"*","*"`.
2. Wire SpiffePrincipal (by wire class name) = refused always, before any policy lookup.
3. Any other wire principal needs `AssertPrincipalPermission(class, name)` held by the AUTHENTICATED PEER = principals of the connection's ClientSubject. Check = Gate-1 mechanism: PD(no code, peer principals) via `Security.create` + `sm.checkPermission(perm, ctx)`. Peer null or without principals = refused without asking.
4. Deviation (Q1): with NO SecurityManager I ask `Policy.getPolicy().implies(pd, perm)` rather than deny. Still default deny, and the answer does not depend on an SM being present (Gate 1 itself passes silently without one). Alternative: always deny without an SM.

## Tests
New: platform `DefaultJwtVerifierClaimsTest` 19, `AssertPrincipalPermissionTest` 10; jeri `JwtPrincipalBindingTest` 15 and `PeerPolicyFixture` (peer CN=gateway holds only what it is given, principal-less code holds all; installs no SM because DirtyChai cannot uninstall one).
Changed, none deleted: `JwtDispatcherFailClosedTest` (audience "svc-a" not null, tokens carry aud, verifier lambdas return VerifiedJwt via `trusting()`, wire Subject has no X500 principal, cache reflection reads `CachedJwt.until`); `MultiSubjectWireProtocolTest` (2-arg reader; two verifier tests use JwtPrincipal sub:bob/dave instead of an unverifiable X500); `BasicInvocationDispatcherMultiSubjectTest` (reads as the permitted peer under PeerPolicyFixture).

## Evidence (output seen)
`mvn -o -pl jgdms-platform,jgdms-der,jgdms-jeri test -Dtest='JwtPrincipalBindingTest,JwtDispatcherFailClosedTest,MultiSubjectWireProtocolTest,BasicInvocationDispatcherMultiSubjectTest' -Dsurefire.failIfNoSpecifiedTests=false` (in JGDMS): Tests run 47, Failures 0. Same with `-Pdirtychai` (SecurityManager=default + test.policy; surefire XML shows java.security.manager=default, so the `sm.checkPermission` branch ran): 47, 0. `mvn -o -pl jgdms-platform test -Dtest='DefaultJwtVerifier*Test,AssertPrincipalPermissionTest'`: 53, 0.
Full 3-module `test` (SP\full.log): platform 532 run, 3 failures + 13 errors = the 16 known (DigestDomainGrantTest 12, GrantSignerTest 4); der 1526, 0; jeri 329, 1 failure + 1 error = ConnectionManagerCapacityTest and MuxServerStartTimeoutTest (both known).

## Fail-first (SP\swap.py: pre-change dispatcher from `git show trunk:` plus a 2-arg overload delegating to the old reader; new tests and platform classes)
JwtPrincipalBindingTest 15 run, 14 failed. The three attacks: `principalsFromNoTokenAreRefusedWhenThePeerLacksThePermission` FAIL "was accepted"; `principalsWithNoTokenAndNoVerifierAreRefused` FAIL "was accepted"; `bobsValidTokenCannotCarryAlicesPrincipals` FAIL at its control line (old code derives nothing: expected sub:bob only, got empty) so the forged call was not reached in that run; the same forged call is covered by `wireJwtPrincipalOutsideTheDerivedSetRefusesEvenIfOthersAreInIt` (FAIL "was accepted") and by mutant M1 ("bob's token, alice's principals: was accepted"). Also failing: Spiffe, pattern scope, ungranted, claims-only, null result, swap, cache, union. JwtDispatcherFailClosedTest: 1 error (reflection on the new field; proves nothing behavioural). `DefaultJwtVerifierClaimsTest` cannot compile against the old verifier, so its fail-first is mutants M4, M5, M8-M10.

## Mutants (SP\mutate.py, SP\mutants.txt; one at a time on the worktree files, hash-checked restore `restored=True` each time)
| id | mutant | killed by |
|---|---|---|
| M1 | binding comparison removed | 8 tests |
| M2 | permission check always true | 5 |
| M3 | principals from the wire again | 13 |
| M4 | nested claims lifted again | 3 |
| M5 | scalar aud comma-split again | 1 |
| M6 | Spiffe refusal removed | 1 |
| M7 | peer with no principals counts | 1 |
| M8 | no max lifetime | 2 |
| M9 | null aud/iss accepted in signature mode | 2 |
| M10 | typ unchecked | 1 |
| M11 | verifier swap keeps old cache | 5 |
| M12 | claims-only derives principals | 2 |
| M13 | permission ignores name pattern | 6 |
| M14 | cache hit returns a different VerifiedJwt | 4 |
No survivor. Not run on a git-archive copy (the worktree is private).

## NOT verified
- No end-to-end JERI call over TLS: `readUserSubjects` is driven by reflection with a hand-built peer. That `peerSubject(context)` finds the ClientSubject on a real request, and that its principals are the worker's identity at that moment (before Gate 1), is unrun.
- No run with a real policy file (DynamicPolicyProvider/CombinerSecurityManager) holding an `AssertPrincipalPermission` grant; only my test Policy and test.policy (AllPermission).
- An unscoped `grant { AllPermission }` gives every peer this permission.
- `JwtLoginModule` and `JwtValidator.parseClaimsJson` (same comma-join flaw, jgdms-security-jwt) untouched; the client keeps its own copy of the sub/email/group mapping. Docs saying "null disables verification" not searched. Windows only.
- PolicyLint/PermissionSets do not know the new permission.

## Questions for the operator
1. No SecurityManager: ask the Policy (built) or always deny?
2. Derive `email:` (JwtLoginModule sends it, so I did) or only sub and groups?
3. Put `AssertPrincipalPermission` in PermissionSets' HOLD set (it confers identity)?
4. Is 24 h the right maximum lifetime for your IdP; ~~keep claims-only DefaultJwtVerifier public now that it confers nothing?~~ **Corrected 2026-11-02 (commit 7cd3f0a24):** ANSWERED: removed (operator decision 2026-11-02, after a search found no uses outside four tests); the claims-only constructors are gone and `DefaultJwtVerifier` is signature mode only, default maximum token lifetime 1 hour. (The 24 h maximum lifetime in the same question is also superseded: the default is now 1 hour.)
5. Look at: on a real Proxy to Service call, does the ClientSubject in the server context carry the worker's X500/SPIFFE principals when the user-Subject block is read?
