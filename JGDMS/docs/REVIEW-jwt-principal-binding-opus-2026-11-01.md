claude-opus-5-5
# Adversarial review: 396227b5c (JWT principal binding, AssertPrincipalPermission), 2026-11-01

CONFIRMED = I ran it in a private copy and saw the output. READ = established by reading code, not run. PLAUSIBLE = reasoned, not shown.
SP = `C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\opus-r7`.
Copy: `git archive --format=zip 396227b5c` extracted to `SP\x`. All runs: `mvn -o -pl jgdms-platform,jgdms-der,jgdms-jeri test -Dtest=... -Dsurefire.failIfNoSpecifiedTests=false` from `SP\x\JGDMS` (no `install`). Probes (copy only, print `OPUSR7` lines): `jgdms-jeri\...\net\jini\jeri\OpusR7BindingProbeTest.java`, `jgdms-platform\...\net\jini\security\jwt\OpusR7VerifierProbeTest.java`. Mutants: `SP\mutate.sh`, output `SP\mutants.txt`. I did not modify the live JGDMS tree (it has uncommitted changes from someone else: PermissionSets, PolicyLint, two docs) or DirtyChai.
Baseline in the copy: the six committed test classes ran 53 + 47 tests, 0 failures. With `-Pdirtychai` (CombinerSecurityManager, ConcurrentPolicyFile): JwtPrincipalBindingTest 15, 0 failures.

**Verdict on review finding 1 (the hole):** closed at `readUserSubjects` for every variant I tried. Gaps remain: the tokens of one Subject are combined, an unverified email becomes a principal, and the assert right is only as narrow as the policy that grants it.

## Findings, ranked by exploitability

**1. One user Subject may carry tokens for different users, and their principals are combined. CONFIRMED.**
`readUserSubjects` adds the derived principals of every token in the block to one `derived` set (BasicInvocationDispatcher ~2379-2383), whatever each token's `sub`/`iss`.
```
OPUSR7 2a alice token + bob-admin token in ONE subject => ACCEPTED [JwtPrincipal[sub:alice], JwtPrincipal[sub:bob], JwtPrincipal[group:administrators]]
```
So a grant needing `sub:alice` AND `group:administrators` is met although neither token proves both. To use it the attacker must hold two valid bearer tokens (a relaying gateway, or a stolen admin token used to act as "alice plus admin"). The test `twoTokensOfOneSubjectContributeTheirUnion` asserts this union as intended behaviour (with two tokens for the same sub). Smallest fix (described, not written): refuse a Subject whose tokens disagree on `sub` (and `iss`). Separate Subjects (`2b`: alice in Subject 0, bob-admin in Subject 1) are accepted separately, but on DirtyChai the merge described in INVESTIGATION-dirtychai-principal-matching (READ by that agent, not run by me) puts them in one flat set at policy time. Splitting into two Subjects therefore gives the same result, and per-Subject binding cannot stop it.

**2. An `email` claim becomes a principal without `email_verified`. CONFIRMED.**
`DefaultJwtVerifier.validateClaims` derives `JwtPrincipal("email:"+email)` from any signed `email` claim:
```
OPUSR7 email unverified => ACCEPTED ... principals=[JwtPrincipal[sub:m], JwtPrincipal[email:ceo@corp.example]]   (email_verified:false)
```
At an IdP where users can set their own email address, a user can then match `principal JwtPrincipal "email:<someone else>"` grants. Whether this is exploitable depends on the IdP. Require `email_verified == true` (a JSON boolean), or do not derive email (operator Q2 in the investigation).

**3. The assert right is not limited to user identities: a broad grant lets a gateway assert another workload's X500 identity. CONFIRMED.**
Only the wire class name `au.net.zeus.jgdms.spiffe.SpiffePrincipal` is always refused. With `AssertPrincipalPermission "*","*"` (or `X500Principal "*"`):
```
OPUSR7 5d wildcard peer, X500 of another workload => ACCEPTED [CN=svc-b, O=Example]
```
TLS peers also authenticate as X500Principal, so `principal X500Principal "CN=svc-b,..."` workload grants are reachable from a user Subject. The ruling names only SpiffePrincipal, so this is the deployer's scope unless the operator widens the ruling (Q3). Also accepted with that grant, but harmless as far as I can see: wire classes outside the allowlist (`au.zeus.jdk.authorization.spire.SpiffePrincipal`, lower-case `spiffeprincipal`, `net.jini.security.jwt.JwtPrincipal ` with a trailing space, class `*`) all become `RemotePrincipal`. `RemotePrincipal.equals` matches only RemotePrincipal, and `UnresolvedPrincipal.implies` compares `getClass().getName()` (`...BasicInvocationDispatcher$RemotePrincipal`), so no Spiffe or JwtPrincipal grant matches it except through the `principal *` defect in finding 4.

**4. Policy wildcards hand the assert right to every authenticated peer. CONFIRMED (pre-existing defects that this permission now depends on).**
- Unscoped AllPermission: under `-Pdirtychai` with the module's `test.policy` (`grant { AllPermission }`), `OPUSR7 7a ambient default policy, peer CN=gateway => ACCEPTED [CN=alice]`. The agent noted this but did not show it.
- Operator precedence in `UnresolvedPrincipal.implies` (jgdms-platform `org/apache/river/api/security/UnresolvedPrincipal.java:113-119`): `OPUSR7 UnresolvedPrincipal(*, CN=gateway).implies(X500 CN=evil) => true`. A grant written `grant principal * "CN=gateway,O=Example" { permission net.jini.security.AssertPrincipalPermission "*","*"; }` therefore applies to every peer with any principal. The DirtyChai copy has the same expression (READ, per the investigation doc :19).
- PolicyLint and PermissionSets do not know the new permission (agent's admission; not re-checked against the live tree's uncommitted PermissionSets edit).

**5. Test gap: a mutant that turns an exact-name grant into a prefix grant survives. CONFIRMED.** See the mutants table.

**6. The javadoc's own example grant does not work. CONFIRMED.** The class javadoc shows `"CN=*,OU=staff,O=Example"`, but `patternImplies` supports only `*`, `prefix*`, `*suffix` and exact names. An infix `*` is compared literally: `OPUSR7 6a javadoc example grant, in-pattern name => REFUSED`. This fails closed, but a deployer whose grant "does not work" is likely to widen it to `"*"`. Separately, `prefix*` on RFC 2253 names is a plain string prefix: grant `CN=alice*` admits `CN=alice,O=Attacker` (`6e`, ACCEPTED). This is correct glob behaviour, but the javadoc should warn about it.

**7. Minor (CONFIRMED, no exploit found).** Empty `sub`/group names yield principals `sub:` and `group:` (4g, 4f). The derived set is uncapped (an issuer-signed token with 4000 groups gave 4001 principals in 35 ms; `MAX_USER_PRINCIPALS` caps only wire principals). The parser accepts some non-JSON input: `+170…`, leading zeros, `\u+041` (Integer.parseInt accepts a sign), lone surrogates, and `"crit":null` (treated as absent). The signature covers the bytes, so this is leniency, not confusion.

## Ran and found fine (CONFIRMED unless marked READ)
- The hole: bob's token + alice's principals; no token + JwtPrincipal (verifier installed); no token + X500 with no peer; no verifier + no token: all REFUSED. A Subject borrowing another Subject's identity: REFUSED (committed test). `sub:Alice` token vs wire `sub:alice`, and a trailing space, are REFUSED (exact equality, no normalisation). A JwtPrincipal ctor failure (no colon) becomes RemotePrincipal with the JwtPrincipal wire class: REFUSED. Spiffe together with a valid token: REFUSED. An X500 with an empty name: REFUSED (the permission constructor throws, caught as "no"). `sub` `CN=admin` derives only `JwtPrincipal[sub:CN=admin]`, never an X500. A newline in `sub` stays inside one name.
- AssertPrincipalPermission: a wire name `*` cannot widen the request; a wildcard class on the wire never matches a class-specific grant (READ + 5f/6d); `equals`/`hashCode` are consistent; `implies` direction is right (READ). Serialisation (READ): there is no `readObject` validation, but a hostile stream with a null pattern throws an NPE inside the policy check, and `peerMayAssert` catches it as "no".
- Peer identification (READ): `Target.dispatch` builds a new `ArrayList` per request (Target.java:488-489). `SslServerConnection.populateContext` adds its `final` per-connection `clientSubject` (SslServerEndpointImpl.java:2494, 2648, 2948). `ClientUserSubject` is a separate interface and is added only after the read. So `peerSubject` cannot return a user Subject or another request's Subject on a multiplexed connection. The check runs in the header phase, BEFORE Gate 1 (dispatch ~1068-1073), with no TOCTOU because the Subject is final. An anonymous TLS or plain-TCP client gives a null or empty peer, which is refused.
- No-SecurityManager branch: fail-closed with the JDK default PolicyFile (7a, no SM: REFUSED), with a policy that throws RuntimeException (7b: REFUSED), and with one that throws Error (7c: propagates; dispatch's `catch (Throwable)` aborts the request). It grants only when the Policy grants (7d). With an SM the same Policy is consulted through `sm.checkPermission(perm, ctx)`; the probe gave the same verdicts under `-Pdirtychai`.
- DefaultJwtVerifier: duplicate members (including `\u0073ub`) and a duplicate header `alg` are refused. Nested `sub` is not lifted. `exp` as a string is refused, a float is floored, negative is expired, `1e999999999` and `1e-999999999` are refused in ≤1 ms (no BigDecimal DoS), and `>24h` is refused. Non-string or nested `aud` elements are refused; `"svc-a,svc-b"` is not split. `crit`, a numeric `kid`, `alg` none/rs256 and `typ` `at+jwt` are refused. 5000-deep nesting is refused without a StackOverflowError, as are a BOM and trailing text. Constructor refuses RSA-1024, empty keys and null aud. Claims-only mode yields no principals, and `VerifiedJwt.unauthenticated` cannot carry any (READ: `derivePrincipals` is skipped).
- Cache (READ + committed tests): the value is `CachedJwt(until, VerifiedJwt)` with `until = min(exp, now+300s)`, so a hit is never served after `exp`. The verifier and the cache are swapped in one volatile write, so a hit cannot be served from a previous verifier. The SHA-256 key covers the whole token. Expiry now comes from the parsed claims instead of the old first-`"exp"` substring scan.

## Mutants (one at a time, hash-checked restore `restored=True` each)
| id | mutant | result |
|---|---|---|
| MA | `peerMayAssert` uses a principal-less PD instead of the peer's | KILLED, 4 JwtPrincipalBindingTest |
| MB | `derived` shared across all Subjects of a request | KILLED, `twoTokensOfOneSubjectContributeTheirUnion` |
| MC | duplicate-member check removed | KILLED, `duplicateTopLevelMemberIsRejected` |
| MD | exact name pattern becomes `other.startsWith(mine)` | **SURVIVED** AssertPrincipalPermissionTest 10 + JwtPrincipalBindingTest 15 |

## Not checked
- No end-to-end JERI call over TLS or Kerberos. Whether the ClientSubject carries the worker's principals at read time is still not run.
- DirtyChai's runtime merge of Subjects, the `JwtLoginModule`/`JwtValidator` paths, and the DirtyChai ConcurrentPolicyFile path for a real `AssertPrincipalPermission` grant (only ad-hoc Policy objects and test.policy).
- Concurrent cache races; behaviour on a stock JDK 24+ (no SM); Linux.

## Questions only the operator can settle
1. May one user Subject carry tokens for different `sub`/`iss` values (finding 1)? Given the DirtyChai flat merge, should a call carry more than one user Subject at all?
2. Should email be derived only when `email_verified` is true, or not derived at all?
3. Should the ruling also cover X500 and Kerberos workload identities (refuse wire principals of the peer's own transport-identity classes), or is limiting them left to the deployer's grant?
4. No SecurityManager: keep asking the Policy, or always deny? Production is ruled to always have an SM.
5. On a real Proxy to Service call: does the ClientSubject seen in `dispatch` carry the worker's X500/SPIFFE principals when the user-Subject block is read? (A TLS integration test, or a look at a live call, settles this.)
6. Should PolicyLint refuse `AssertPrincipalPermission` under `principal *` or in unscoped grants, and should it go in the PermissionSets HOLD set?
