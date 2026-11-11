claude-sonnet-5-5
# JWT binding follow-up: fixes for the Opus review findings (2026-11-01)

Worktree `...\JGDMS\.claude\worktrees\agent-a401ac7b6f99a2e95`, detached at 54b392987, uncommitted. Patch: `SP\jwt-followup.patch`, SP = `C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\jwt3`. Report sits in the worktree because the shared checkout is not writable from here.
Maven always `mvn -o -pl jgdms-platform,jgdms-der,jgdms-jeri test -Dtest=<classes> -Dsurefire.failIfNoSpecifiedTests=false`, never `install`.

## What changed
- `BasicInvocationDispatcher` (F1, F6): tokens in one user Subject must agree on `sub` and `iss`; one call carries one verified user identity (a second token-bearing Subject must match); a token without `sub` never matches another token. `SPIFFE_PRINCIPAL_CLASSES` now holds both the JGDMS and the DirtyChai (`au.zeus.jdk.authorization.spire`) class, both refused. Javadoc states why (DirtyChai flat principal merge) and the X500 / AllPermission / `principal *` caveats.
- `VerifiedJwt` (F2, F5): 7-arg constructor with `emailVerified`, `isEmailVerified()`; the 6-arg constructor derives no email. `derivePrincipals` throws IAE for an empty sub/email/group.
- `DefaultJwtVerifier` (F2, F5): `email_verified` must be JSON `true`; empty sub/email/group refused; cap `DEFAULT_MAX_PRINCIPALS`=64, new 6-arg constructor takes `maxPrincipals`; RFC 8259 number grammar; `\u` needs exactly 4 ASCII hex digits; lone/reversed surrogates refused; `crit:null` kept so it trips the crit refusal.
- `AssertPrincipalPermission` (F3, F6): only `*`, exact, trailing-`*` prefix; a `*` anywhere else throws IAE at construction (suffix form removed); javadoc example replaced by an exact name, prefix-on-DN warning, F6 warnings.
- Tests: `JwtPrincipalBindingTest` +9, `AssertPrincipalPermissionTest` +4 (one rewritten), `DefaultJwtVerifierClaimsTest` +8, new `VerifiedJwtApiTest` 3.

## Changed existing tests (none deleted)
- `AssertPrincipalPermissionTest.prefixAndSuffixPatterns` became `prefixPattern`: it built `CN=*,O=Example` and `*,O=Example`, now refused by rule F3.
- `DefaultJwtVerifierClaimsTest.derivedPrincipalsAreExactly...`: payload gained `email_verified:true` (F2).
- `JwtPrincipalBindingTest.twoTokensOfOneSubjectContributeTheirUnion`: the ivy/jon two-Subject part now uses ivy in both (F1 refuses jon); the borrowing check was kept with a group only the first token carries.

## Evidence
After: platform+jeri, 7 classes (JwtPrincipalBindingTest 24, AssertPrincipalPermissionTest 14, DefaultJwtVerifierClaimsTest 25, VerifiedJwtApiTest 3, JwtDispatcherFailClosedTest 15, MultiSubjectWireProtocolTest 12, BasicInvocationDispatcherMultiSubjectTest 5): all `Failures: 0, Errors: 0`, BUILD SUCCESS. With `-Pdirtychai` (SecurityManager): 14+25+3 platform, `Tests run: 24, Failures: 0` jeri.

## Fail-first (main code reverted to trunk, new tests kept; `VerifiedJwtApiTest` removed, it needs the new API and cannot compile there)
| area | result on trunk code |
|---|---|
| JwtPrincipalBindingTest 24 run | 5 fail: sub disagreement, iss disagreement, sub-less tokens, two-user call, DirtyChai Spiffe class (all "was accepted") |
| DefaultJwtVerifierClaimsTest 25 run | 6 fail: email unverified, empty sub/group/email, cap 65, `+exp`, `\u+041` etc., `crit:null` |
| AssertPrincipalPermissionTest 14 run | 1 fail: mid-string pattern accepted |
| VerifiedJwtApiTest | not run on trunk (does not compile); covered by mutants M3, M5 |
| Pass on trunk by design | exact/prefix boundary tests, unknown-class RemotePrincipal, empty name, F7 failing-policy test: they pin existing behaviour (and kill MD) |

## Mutants (private git-archive copy plus the changed files, one at a time; restore byte-compared)
| id | mutant | result |
|---|---|---|
| M1 | drop sub/iss agreement in a Subject | KILLED, 3 JwtPrincipalBindingTest fail; restored True |
| M2 | allow two user Subjects with different subs | KILLED, `aCallCarriesAtMostOneVerifiedUserIdentity`; restored True |
| M3 | email without `email_verified` | KILLED, DefaultJwtVerifierClaimsTest 1 + VerifiedJwtApiTest 1; restored True |
| M4 | accept mid-string `*` | KILLED, `aStarAnywhereButTheEndIsRefusedAtConstruction`; restored True |
| M5 | remove principal cap | KILLED, `defaultCapOf64...` + `principalCapIsConfigurable`; restored True |
| M6 | accept `+1` as exp | KILLED, `numbersFollowTheJsonGrammar`; restored True |
Raw output: `SP\mutants1-5.txt`, `SP\mutants.txt` (M6; baseline 42+24 pass). Command: `python mutate.py` (runs the 4 classes per mutant). Not rerun: the earlier MD mutant (exact becomes prefix); `exactGrantIsNotAPrefixGrant` asserts the three cases it survived on, by reading.

## Decisions
- Unknown wire class stays `RemotePrincipal` (not refused): the javadoc documents it, it still needs the peer's permission, and it equals no grant except one naming `RemotePrincipal`. No test depended on it; a test now pins it.
- F7: the no-SM branch is unchanged; a test shows a RuntimeException from the policy is a refusal and an Error aborts the request, nothing admitted.
- Removed an `AssertPrincipalPermission.readObject` I had added: DirtyChai's `Permission` is not Serializable here, so it was untestable.

## Not verified
- No end-to-end JERI call; no Linux; the full platform suite was not run (16 known failures).
- `jgdms-security-jwt` `JwtLoginModule` was not touched.

## Questions
1. `JwtLoginModule` (client) still adds `email:` unconditionally; the server now refuses a wire `email:` principal whose token lacks `email_verified:true`, so such a client call is refused. Align the client in a separate change?
2. Is a grant `principal *` fix (`UnresolvedPrincipal.implies` precedence, DirtyChai side) wanted? Only documented here.
3. Should PolicyLint / PermissionSets learn this permission (other agent's area, untouched)?
