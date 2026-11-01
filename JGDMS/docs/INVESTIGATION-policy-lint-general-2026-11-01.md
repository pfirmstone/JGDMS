claude-sonnet-5-5
# PolicyLint as a general dangerous-policy gate, 2026-11-01

VERIFIED = I saw the output this session. SP = `C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\lint3`.
Nothing committed or pushed. Patch `SP\lint-general.patch` (25 files, `git add -N` then `git diff HEAD`, LF). `git apply --ignore-whitespace --check` passes on a fresh `git archive` copy (plain `--check` fails there only because the archive is CRLF and the patch LF, as for the earlier patches).
The worktree was cut from an older commit; I ran `git checkout --detach trunk` (54820fec6) first. Scratch copies outside the repo: `C:\t\mu` (mutants; `C:\t\ff` and `C:\t\ap` removed). `~\.m2` not written (only `mvn test`).

## Changes per file
- `jgdms-platform/.../api/policy/PermissionSets.java`: `Danger`, `Probe`, `HIGH`/`MEDIUM`, `danger()`, `probe()`, `probes()`, `broadFile`. HOLD gains insertProvider/removeProvider/putProviderProperty/defineClassInPackage; FLAG gains getProtectionDomain, exitVM, PropertyPermission write wildcard, AuthenticationPermission/URLPermission wildcard (`accessClassInPackage` stays `sun.` only). One catalogue: HOLD = HIGH, FLAG = MEDIUM, plus file, deserialisation and GrantPermission rules.
- `PolicyVerdict.java`: subjects http, https (+port), ftp, IP, `jar:http:` strangers; principals X500, a tool class, every class the policy names; `file:`, `jar:file:`, `jrt:`. Asks every catalogue probe plus each dangerous permission the file names; a probe also counts when an element of `getPermissions(pd)` implies it (unresolved classes are loaded with the tool's loader).
- `PolicyLint.java`: kinds D1-D5, P2, W1, W2 (shown in SUMMARY/TOTAL only when non-zero, so old output is unchanged); high kinds fail, medium kinds fail under `--strict`; case-insensitive walk; unrecognised-file and empty/unreadable-directory findings; `--require-defined`; S1 prints codeBase property names and their source; `NOTE` lines.
- Tests: `PolicyLintTest` 36 -> 49 (13 new; the f07 and s5 rows changed by ruling 2c), platform `PermissionSetsTest` 7 -> 9, test class `TrojanPermission`, 16 fixtures. Docs: `ProxyPolicyGenerator.md` lint section, `qa/build.xml` (`policy.lint.require.defined`, comment; XML parses, ant not run), polpAudit `SKILL.md` sentence (stage that file alone).

## Catalogue (`PermissionSets.danger`; the lint asks the provider about a representative of each row)
| Entry | Sev | Reason |
|---|---|---|
| AllPermission, UmbrellaGrantPermission, PolicyPermission | HIGH | everything / policy meta |
| GrantPermission whose target holds a HIGH entry or `*`/`-` | HIGH | can grant itself the entry |
| FilePermission write/delete/execute on `<<ALL FILES>>`, `-`, `*`, a root or drive tree | HIGH | rewrites code, config, policy |
| same, read | MEDIUM | reads every file (the brief's lower-severity FLAG) |
| SecurityPermission `*`, setPolicy, setProperty.*, insertProvider.*, removeProvider.*, putProviderProperty.*, createAccessControlContext | HIGH | replaces policy, providers, inputs |
| RuntimePermission `*`, setSecurityManager, createClassLoader, loadLibrary.*, defineClassInPackage.*, createAccessControlContext | HIGH | leaves the sandbox |
| ReflectPermission suppressAccessChecks; AuthPermission doAsPrivileged/modifyPrincipals/modifyPrivateCredentials; SerializablePermission enableSubclassImplementation; NativeInvocationPermission | HIGH | existing HOLD set |
| DeSerializationPermission `*`, MARSHALLED | HIGH | any class read from an untrusted stream |
| RuntimePermission getProtectionDomain, exitVM.*, accessClassInPackage.sun.*, setContextClassLoader, setFactory | MEDIUM | JDK internals, global state, DoS |
| SocketPermission any host (connect, accept, listen); PropertyPermission `*` write; URLPermission/AuthenticationPermission wildcard | MEDIUM | exfiltration, global state |
| shutdownHooks | not catalogued | runs only the caller's own code at exit |
| LoadClassPermission, NativeInvocationPermission (DirtyChai) | not probed | classes not loadable in the tool's JVM (NOTE line) |

## Tests (worktree, VERIFIED)
`mvn -o -pl jgdms-platform,tools/policy-condenser test -Dtest=PolicyLintTest,PermissionSetsTest,VerdictPermissionMapperTest,ProxyPolicyGeneratorTest,ListPromotionTest,PolicyUpdatePostProcessorTest,PolicyCondenserTest`: platform `Tests run: 26, Failures: 0, Errors: 0, Skipped: 0`; tools `Tests run: 165, Failures: 0, Errors: 0, Skipped: 2` (PolicyLintTest 49, the 2 skips are the chmod tests, Windows); `BUILD SUCCESS`. The DirtyChai provider is exercised (`providersAgreeOnFixtures` runs).

## Fail-first (new test class on trunk main code, copy `C:\t\ff`, same command with `-Dtest=PolicyLintTest`)
`Tests run: 48, Failures: 13, Errors: 0, Skipped: 3` (`everyCatalogueProbeIsFoundWhenGranted` removed: needs the new API; the 3rd skip is `deployPoliciesStayClean`, no deploy dir in the copy). Of the 13: 10 are new tests (https, principal, broad file/platform, dangerous table, unknown class, severity exits, case-insensitive walk, unrecognised files, empty dir, require-defined) and 3 are the changed old rows (f07 x2, s5). `deployPoliciesStayClean` passes on both (clean-case guard).

## Mutants (one at a time on a `git archive` copy `C:\t\mu`, restored after; `SP\mutants2.log`, `SP\m5.log`)
Same command in `C:\t\mu` with `-Dtest=PolicyLintTest,PermissionSetsTest -Dmaven.test.failure.ignore=true`. Baseline and restored: `Tests run: 50, Failures: 0` (platform 9, 0).
| # | Mutation | Result | Failing tests |
|---|---|---|---|
| M1 | no https probes | killed | httpsWildcardCodebaseGrantsAStranger |
| M2 | `<<ALL FILES>>` write not HIGH | killed | PermissionSetsTest.testDangerSeverities, broadFileAndPlatformCodebasesCount, dangerousPermissionsGrantedToStrangers |
| M3 | principal classes named by the policy ignored | killed | principalOfAnyClassIsProbed |
| M4 | D3/D4 dropped from the exit rule | killed (5) | broadFile..., newKindsFollowTheSeverityExitRules, 2 fixture tables, s5PlatformModuleGrant |
| M5 | walk case-sensitive again | **survived the first test** (the P2 rule linted `java.Policy` anyway); I added `assertEquals(0, count("P2"))`; then killed | walkMatchesPolicyNamesCaseInsensitively |
| M6 | unrecognised files swallowed | killed | unrecognisedPolicyLookingFilesFailTheGate |
| M7 | parse failure yields no finding | killed (6) | p1Unparseable, productionParserRejectionIsP1, bomIsReported..., exitCodeMatrix, 2 fixture tables |
| M8 | setSecurityManager out of HOLD and the probe list | killed (8) | 4 PermissionSets tests, dangerousPermissions..., newKinds..., principalOfAnyClassIsProbed, unknownPermissionClass... |
A first mutant run was invalid and discarded (restored files kept their old mtime so mutated classes survived; a platform failure also stopped the reactor); rerun with `LastWriteTime` touched and failures ignored. Removing only one of the two routes for a name is not caught by design: a name the file itself lists is probed anyway.

## Corpus (copies in `SP\corpus`, driver `SP\corpus.ps1`; before = trunk classes, after = mine; default run, `--strict` gave the same kinds)
| Dir (files) | before | after |
|---|---|---|
| qa/harness/policy (41) | U1=10 U3=6 U4=38 S1=36 S4=2 S5=129, exit 1 | same + **D1=2 D5=9**, exit 1 |
| deploy/policy (5) | clean, exit 0 (strict 0) | **D5=4**, exit 0 (strict **1**) |
| jinitest.policy (1) | U1=1 S5=10, exit 1 | same |
| jeri test.policy (1) | U1=1, exit 1 | same |
D1: `defaultsecurephoenix.policy.new` (24 dangerous permissions to every stranger) and `qa.policy` (FilePermission `*` write, createClassLoader). D5: seven `defaultspiffe*.policy` (`<<ALL FILES>>` read, lines 155-228), those two files, and 4 deploy files (SocketPermission to any host).

## Not verified
Linux/macOS (the unreadable-directory test skips on Windows); the ant target (XML parse only); a `jar:` codeBase (ten broad forms never matched a `jar:` probe, so the `jar:` probes cannot be shown to bite); DirtyChai `LoadClassPermission`/`NativeInvocationPermission`; cwd-relative `-` and `*` count as broad because the service's directory is unknown; a real polpAudit run; the second-model review.

## Questions for the operator
1. deploy/policy now shows D5 (SocketPermission to any host granted to all code, 4 files) and fails `--strict`: accept, make sockets informational, or scope those grants?
2. Are the HIGH grants to everyone in `defaultsecurephoenix.policy.new` and `qa.policy` intended?
3. An unrecognised file is P2 (fails) if it mentions grant/permission/keystore, else W2 (strict only): accept, or fail every unrecognised file?
4. `file:/-` or `jrt:/-` with AllPermission now fails the default run (ruling 2c); the f07 and s5 expectations were changed.
