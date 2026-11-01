claude-opus-5-5
# Review of 5799eeda6, 06f7d8889, 8a5632e01, fd567437e, 5f88b1900, df4f18de1 (2026-11-01)

CONFIRMED = I ran it in a private copy and saw the output. PLAUSIBLE = reasoned from the code, not run. READ = read in the code.
SP = `C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\opus-r10`.
- Copy: `git archive --format=zip HEAD` (df4f18de1), extracted with Windows `tar.exe` into `SP\src`. `corpus/r8/v04-continuation.policy` has `\` then LF only (checked with `od -c`).
- JDK: DirtyChai image (`lib/modules` built 20:05). All runs used `mvn -o ... test -Dtest=... -Dsurefire.failIfNoSpecifiedTests=false`. I never ran `install`. The live JGDMS and DirtyChai trees were not edited; the only write is this file.
- Logs: `SP\base.log`, `SP\mutants.txt`, `SP\mutants2.txt`, `SP\mut-M*.log`. Probes: `SP\probe\WideCross.java` (output `SP\probe\wide.txt`), `SP\probe2\src\Json.java`, `SP\probe2\src\Which.java`.

## Tests measured (one run at HEAD, `-pl jgdms-platform,jgdms-der,jgdms-jeri,jgdms-security-jwt,tools/policy-condenser`): 372 run, 0 failures, 0 errors, 2 skipped, BUILD SUCCESS
| commit | classes, tests run (all with 0 failures) |
|---|---|
| 5799eeda6 | JwtPrincipalBindingTest 26, AssertPrincipalPermissionTest 14, DefaultJwtVerifierClaimsTest 25, DefaultJwtVerifierNegativeTest 24, VerifiedJwtApiTest 3, JwtDispatcherFailClosedTest 15, MultiSubjectWireProtocolTest 12, BasicInvocationDispatcherMultiSubjectTest 5 |
| 06f7d8889 | PolicyLintTest 49 (2 skipped, the chmod tests), PolicyLintGateTest 17, PolicyLintCorpusTest 2 (prints `provider cross-check: files covered=78 failures=0`), PermissionSetsTest 12 (platform) + 2 (tools) |
| 8a5632e01 | JwtPrincipalBindingTest 26 (above), ListPromotionTest 46, ProxyPolicyGeneratorTest 45, both PermissionSetsTest (above) |
| fd567437e | JwtLoginModuleTest 20, JwksJsonParserTest 16, JwtPrincipalTest 21 |
| 5f88b1900, df4f18de1 | UnresolvedPrincipalImpliesTest 6, DefaultPolicyScannerTest 12, the PolicyLint classes (above) |

## Earlier findings: addressed? (file:line is in the HEAD copy)
| finding | status | where | test that pins it |
|---|---|---|---|
| Lint 1, wildcard host or port | ADDRESSED | PolicyScope.java:227-235 | Gate `wildcardHostsFailTheDefaultRun`; my M3 is killed |
| Lint 2, wildcard principals of issued classes | ADDRESSED | PolicyScope.java:86, 139-151, StandInPrincipal; S3/D2 now fail the default run; 5f88b1900 refuses `principal *` | Gate `wildcardPrincipalsOfIssuedClassesFail`, `principalStandInIsAskedAbout` |
| Lint 3, `${...}` file names | ADDRESSED | PermissionSets.java:560 `expand` | Gate `filePermissionNamesAreExpanded` |
| Lint 4, `..` reaching the root | ADDRESSED | PermissionSets.java:615-618 | corpus n22, r8/f16, r8/f19 |
| Lint 5, GrantPermission `delim=` | ADDRESSED | PermissionSets.java:441-478 | Gate `grantPermissionForms` |
| Lint 6, catalogue gaps | **PARTLY** | the listed gaps were added (extraDanger :309-, probes :751-756), but finding 1 below lists more | Gate `catalogueGaps` |
| Lint 7, a class the lint cannot judge counts as clean | **PARTLY** | an unloadable class, a rejected constructor or a throwing `implies` is now D6 or HIGH (PolicyLint `symbolic`, PolicyVerdict.java:383, 432). A **loadable** class the catalogue does not know is still clean | Gate `unknownPermissionClassIsNeverClean`, `impliesThatThrowsIsCannotTell` |
| Lint 8, directory walk | ADDRESSED | PolicyLint.java:416 (16 MiB cap), :435 short ignore list | Gate `walkLints...`, `walkReadsTheWholeFile...`, `grantpermAndProxyPermissionsAreLinted`, `linkLoopIsAFinding` |
| Lint 9, codeBases under the home directory | ADDRESSED | PolicyScope.java:84, 187 | Gate `codeBasesInWritablePlacesFail`; a35 now fails |
| Lint 10, M2 survived (a throwing `implies` read as "no") | ADDRESSED | PolicyVerdict.java:383 `noteThrows` | Gate `impliesThatThrowsIsCannotTell` |
| Binding 1, tokens of different users combined | ADDRESSED | BasicInvocationDispatcher.java:2420-2440, 2458 `sameIdentity` | `tokensOfOneSubjectMustAgreeOnTheIssuer`, `aCallCarriesAtMostOneVerifiedUserIdentity` (my M1 is killed). Residual: finding 9 below |
| Binding 2, unverified email | ADDRESSED | DefaultJwtVerifier.java:417, VerifiedJwt.java:112 | `emailPrincipalOnlyWhenEmailVerifiedIsJsonTrue`; client side in JwtLoginModuleTest |
| Binding 3, gateway asserting a workload's X500 identity | **PARTLY** (javadoc only) | AssertPrincipalPermission javadoc, dispatcher javadoc; waits on operator question 3 of that review | none |
| Binding 4, wildcards hand the permission to every peer | ADDRESSED for `principal *` (scanner refusal, exact class in `implies`) and for the catalogue and HOLD set (PermissionSets.java:133-136). Unscoped AllPermission still confers it, as documented; the lint reports that as U1 | | DefaultPolicyScannerTest (3 tests), `UnresolvedPrincipalImpliesTest` (but see finding 3) |
| Binding 5, MD survived (an exact name becomes a prefix) | ADDRESSED | | `exactGrantIsNotAPrefixGrant`; I re-ran MD as M8: killed |
| Binding 6, javadoc example; prefix on a DN | ADDRESSED | AssertPrincipalPermission.java:123-131 (IAE), javadoc warning | `aStarAnywhereButTheEndIsRefusedAtConstruction` |
| Binding 7, empty names, no cap, lax JSON | ADDRESSED | DefaultJwtVerifier.java:404-428, number grammar :590-616 | `defaultCapOf64...`, `emptySubAndEmptyGroupAreRefused`, `numbersFollowTheJsonGrammar`, `unicodeEscapes...`, `nullCritHeader...` |

## Findings, most important first
1. **The provider cross-check is not independent of the catalogue, and the catalogue still misses dangerous classes.** CONFIRMED.
   - `PolicyLintCorpusTest.cleanFilesGrantNoStrangerAHighPermission` chooses its own codebases and principals, but its permissions are `PermissionSets.probes()` of HIGH severity: the same list the lint uses. A catalogue miss is invisible to it.
   - Wider check (`WideCross`):
     - Population: every non-abstract Permission class in the JDK image (all modules, wider than java.base) and in jgdms-platform: 56 classes, 48 of them constructible. I tried 21 names × 16 action strings.
     - Each was asked of DirtyChai `ConcurrentPolicyFile` for the 78 files that `corpus-expectations.tsv` marks default-exit 0. CodeSource `https://cdn.attacker-evil.example/payload/x.jar`; principals none, `X500Principal CN=mallory,O=Evil Corp`, `UserPrincipal mallory`. I also listed `getPermissions`.
     - Result: 78 files loaded, 650 `implies` answered true. All of those are catalogued classes in files the lint reports as D5 (MEDIUM, by design).
     - One class the catalogue does not know was granted: `java.sql.SQLPermission "setLog"` (r8/j17), which exits 0/0 even with --strict.
   - Loadable classes that are absent from the catalogue (`PermissionSets.danger` returns null for every instance I built):
     - DirtyChai `au.zeus.jdk.authorization.guards.DefineClassPermission` (guards `Lookup` class definition) and `NativeMemoryPermission` (off-heap memory, DoS);
     - JMX `SubjectDelegationPermission` (acting as another subject), `com.sun.jdi.JDIPermission`, `jdk.jfr.FlightRecorderPermission`, `LinkPermission`, `SQLPermission`, `jdk.net.NetworkPermission`, `InquireSecContextPermission`, AWT/Audio/Card;
     - jgdms Access/Export/Discovery/Context/SettleTransaction permissions (application permissions, probably fine).
   - PLAUSIBLE (I did not write a new policy file, as the brief asked): `grant { permission au.zeus.jdk.authorization.guards.DefineClassPermission "*"; }` passes both modes. Reason: `PolicyLint.symbolic` reaches `continue` when `danger` is null and the class is judgeable, and the provider half asks only catalogue probes.
2. **The cross-check passes when the provider loads nothing.** CONFIRMED by mutant M7: making `grantedTo` throw before it builds the Policy still prints `files covered=78 failures=0`, and the test passes.
   - Cause: `PolicyVerdict.grantedTo` reads a load or reflection failure as "nothing granted" (PolicyVerdict.java:339). It also returns quietly when `source(url)` is null (:331).
3. **JGDMS's `UnresolvedPrincipal` change is dead code on the DirtyChai JDK, and the change that is live is not committed.** CONFIRMED.
   - `Which` prints `UnresolvedPrincipal -> module java.base`, while `DefaultPolicyScanner` loads from the classpath.
   - Mutant M5 restores the class wildcard in JGDMS's `implies` and **survives**: `UnresolvedPrincipalImpliesTest` exercises DirtyChai's copy.
   - The live fix is uncommitted in the DirtyChai working tree: `git status` shows `M` on `UnresolvedPrincipal.java`, `au/zeus/.../policy/DefaultPolicyScanner.java` and `Messages.java`.
   - So the green state of df4f18de1 (UnresolvedPrincipalImpliesTest, f06 P1, the corpus P1 rows) depends on uncommitted DirtyChai source in a locally built image.
4. **The client's `crit` refusal is untested.** CONFIRMED by mutant M4: `JwtValidator.java:167` `if (false)` survives JwtLoginModuleTest, JwksJsonParserTest and JwtPrincipalTest.
5. **Token refresh is broken, and fails closed.** PLAUSIBLE, from READ.
   - `updateSubject` (JwtLoginModule.java:538-546) replaces the JwtPrincipals but not the `JwtRawToken`, which is added only at login (:233). BasicInvocationHandler sends the old token with the new principals, so the server refuses once they differ or the old token expires.
   - `logout` removes only the login-time `addedPrincipals` (:283), so principals added by a refresh survive logout.
   - `removeIf(p -> p instanceof JwtPrincipal)` also removes principals that other login modules added.
6. **Symbolic scope: two cases where "unknown" counts as clean.** PLAUSIBLE.
   - `PolicyScope.file` drops the host of `file://host/share/-` (PolicyScope.java:259-262). The result is FILE_PINNED, so the reach is NONE. On Windows that path is an SMB share on a remote host.
   - A scheme the lint does not judge becomes OTHER, so the reach is NONE (:198).
   - In both cases only the provider half remains, and its stranger URLs contain no UNC or unknown-scheme codebase.
7. **The Guard (8a5632e01) is sound as used.** READ, plus CONFIRMED by mutant M2.
   - The context is exactly `{peerDomain(peer)}`, and the CombinerSecurityManager checks only that context. M2 (check the stack instead) is killed by 8 tests.
   - The ProtectionDomain is cached per peer Subject (weak identity, :380-386, :1676-1681), so no context is shared between peers. A stale principal snapshot is the same pre-existing property as Gate 1.
   - Weakness: the Guard's own `if (sm == null) return;` passes silently. It is safe only because `peerMayAssert` checks first (:2691); any second caller of the Guard would fail open. Throwing there would be fail-secure.
8. **The no-SecurityManager branch** refuses every wire principal that is not derived from a token. It is pinned by the child-JVM test (killed in the author's M1 and M2). READ: `PeerPolicyFixture` leaves a SecurityManager installed for the rest of the surefire fork, so classes that run after it in the same fork depend on run order.
9. **"One user per call" covers tokens only.** PLAUSIBLE. A peer-asserted X500 (when the peer holds the permission) beside a token for another user is accepted, and DirtyChai merges both into one flat set. This is scoped by the gateway grant.
10. **The strict JSON parser is fine.** CONFIRMED with `Json`:
    - depth 17 and depth 5000 are refused in 0 ms;
    - duplicates are refused, also escaped (`\u0073ub`) and nested ones;
    - `01`, `1.`, NaN, a trailing comma, a raw control character and a lone surrogate are refused;
    - `1e999999999` parses, and `timeClaim` bounds it;
    - a 60000-digit number takes 92 ms (first call). The header is parsed before the signature check, so this is pre-auth CPU, but small.
    - Client and server derive identity through the same `identityFromClaims`. The one divergence: the client cap is fixed at 64, the server's is configurable. That fails closed.

## Mutation sample (one at a time, restore byte-compared `restored=True` each)
| id | commit | mutant | result |
|---|---|---|---|
| M1 | 5799eeda6 | `sameIdentity` ignores `iss` | KILLED, 2 JwtPrincipalBindingTest |
| M2 | 8a5632e01 | Guard checks the caller's stack, not the peer context | KILLED, 8 JwtPrincipalBindingTest |
| M3 | 06f7d8889 | wildcard or range port treated as pinned | KILLED, Gate `wildcardHosts...` + `corpusExitCodesAndKinds` |
| M4 | fd567437e | client accepts a `crit` header | **SURVIVED** |
| M5 | 5f88b1900 | JGDMS `UnresolvedPrincipal.implies` honours a class wildcard again | **SURVIVED** (shadowed by java.base) |
| M6 | 5f88b1900 | scanner accepts `principal *` again | KILLED, 3 DefaultPolicyScannerTest |
| M7 | 06f7d8889 | cross-check provider cannot load any file | **SURVIVED** |
| M8 | 5799eeda6 | exact name pattern becomes a prefix (the old MD) | KILLED, `exactGrantIsNotAPrefixGrant` |

## Not checked
Linux and macOS; the `-Pdirtychai` profile; an end-to-end JERI call; the full platform suite; `qa/jtreg` JwtLoginModuleTest; a run of the refresh thread; the lint on a DefineClassPermission, UNC or unknown-scheme grant (each would need a new policy file); `qa/harness/policy`; whether `java.base` loading UnresolvedPrincipal is intended on a stock JDK.

## For the operator to settle
1. Who wrote the uncommitted DirtyChai edits (UnresolvedPrincipal, au.zeus DefaultPolicyScanner, Messages), and when will they be committed? DirtyChai production source is to be human-written, and df4f18de1 depends on these edits.
2. Should a loadable permission class that the catalogue does not know fail (as D6, or at least under --strict) instead of passing? Which of DefineClassPermission, NativeMemoryPermission, SubjectDelegationPermission and JDIPermission are HIGH?
3. Should the cross-check fail when the provider cannot load a file, instead of counting "nothing granted"?
4. Binding review question 3 is still open: should wire principals of the transport-identity classes (X500, Kerberos) be refused in a user Subject?
5. On refresh, should the JwtRawToken be replaced, and must the new token keep the same `sub` and `iss`?
6. Should JGDMS keep its own `UnresolvedPrincipal`, given that DirtyChai's java.base copy shadows it?
