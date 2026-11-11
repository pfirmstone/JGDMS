claude-sonnet-5-5
# PolicyLint redesign: symbolic scope check next to the probes, 2026-11-01

VERIFIED = I ran it this session. Base: `git checkout --detach trunk` = 54b392987. Nothing committed or pushed. Patch: `...\scratchpad\lint4\lint-redesign.patch` (`git add -N`, `git diff HEAD`).
Scratch (outside the repo): `C:\t4\trunk` = `git archive` of 54b392987, used first as the trunk baseline, then as the mutant sandbox. `C:\wt` = junction to my worktree. `~\.m2` not written (only `mvn -o test`).

## Design
The lint decided "reaches a stranger" only by asking the provider about fixed subjects. It now also reads each grant (`PolicyScope`, new): codeBase pattern, signedBy, digest, principal list -> NONE / STRANGER / PRINCIPAL / FILE / PLATFORM, then applies the catalogue to every permission in the block. Either half raising a finding counts; a kind already raised is not repeated. Rules are in `ProxyPolicyGenerator.md`.

## Changes per file
- `PermissionSets.java` (platform): catalogue gaps; file names expanded (`--define`, system properties, `${/}`) and normalised (`..`), unresolved property = root; GrantPermission read by `GrantPermission`'s own parser (reflection on `grants`) so `delim=` works, text fallback when it rejects the target; new probes (home, JDK conf, AssertPrincipal, PrivateCredential, Net, SSL, jdk.internal, MBean...); `expand`, `loadable`, `instantiate`, `knownByName`, `FilePath`. HOLD/FLAG (proxy promotion) untouched.
- `PolicyScope.java` (new): the symbolic scope. `StandInPrincipal.java` (new): generated class of the named principal class.
- `PolicyVerdict.java`: stand-in principals, `unbuildable` list (fail closed), `Hit` carries id/severity/reason, a throwing `implies` and an unloadable or constructor-rejected permission are hits (not a NOTE), listing read once per subject, `grantedTo()` for tests.
- `PolicyLint.java`: `symbolic()`, kinds P3 (link loop/depth), D6 (cannot judge), I1 (info); S3 and D2 now fail the default run; walk rewritten (every file read unless on the short ignore list, `.grantperm` and `.proxy-permissions` linted, whole file read, 16 MiB cap = P1, links followed to 8 hops with ancestors' real paths, loop = P3); S3 only when the scope really is a wildcard principal, else S2.
- Tests: `PolicyLintGateTest` (17, one per item), `PolicyLintCorpusTest` (2), `LintHarness`, `ThrowingPermission`, `UnbuildablePrincipal`, `corpus/` (42 r6 + 159 r8 + 43 new `n`) and `corpus-expectations.tsv`; `PolicyLintTest` rows changed (below); platform `PermissionSetsTest` 9 -> 12.
- Docs: `ProxyPolicyGenerator.md` lint section, `qa/build.xml` target comment (LF kept in the index), polpAudit `SKILL.md` sentence.

## Catalogue additions (severity, why)
| Entry | Sev | Why |
|---|---|---|
| AssertPrincipalPermission with `*` | HIGH | confers any identity |
| PrivateCredentialPermission with a wildcard | HIGH | private keys/tickets of any subject |
| NetPermission `*`, specifyStreamHandler; SSLPermission `*`, setDefaultSSLContext | HIGH | replaces URL handlers / TLS trust |
| accessClassInPackage.jdk.internal.* ; clearProviderProperties.* | HIGH | JDK internals; provider reset |
| PropertyPermission write on java.security.policy/manager/auth.login.config/properties, or `*` | HIGH | where policy comes from |
| MBeanPermission wildcard+mutating action, MBeanServer `*`, MBeanTrust, Management `control`, Attach attachVirtualMachine | HIGH | run code / reconfigure JVM |
| LoadClassPermission `*` (both), DirtyChai SerialObjectPermission `*`, NativeInvocationPermission | HIGH | open the class/deserialisation/native gates |
| DirtyChai LoadClassPermission "ALLOW" | MEDIUM | one open gate, normal when scoped |
| setIO, getClassLoader, modifyThread(Group), read/writeFileDescriptor, manageProcess, enableContextClassLoaderOverride, charsetProvider, getPolicy, createPolicy.*, DownloadPermission, Logging, Kerberos `*`, Net/SSL minor | MEDIUM | global state, DoS |
| FilePermission write: home/JDK/temp tree, `*` in home/JDK, any file under JDK home | HIGH | rewrites config/code |
| FilePermission `/tmp/*` | MEDIUM | the deploy policies use it; not reclassified (see questions) |

## Fail-first (VERIFIED)
`PolicyLintGateTest` (command line only) on trunk's lint, copy `C:\t4\trunk`: `Tests run: 17, Failures: 16` (the 17th is `cleanCasesStayClean`, a guard). After: `Tests run: 17, Failures: 0`.

## Corpus (VERIFIED; 244 files; default and --strict exit 1 counts; trunk = lint of 54b392987 run here)
| group | files | new default exit 1 | trunk default exit 1 | new strict exit 1 | default exit changed | strict exit changed |
|---|---|---|---|---|---|---|
| r6 (earlier 42) | 42 | 35 | 28 | 39 | 7 | 0 |
| r8 (Opus 159) | 159 | 101 | 58 | 139 | 43 | 47 |
| n (new) | 43 | 29 | 5 | 35 | 24 | 26 |
Per-file old/new and kinds: `corpus-expectations.tsv`. Reviewed by hand: no file went from fail to clean. Deliberate changes: a26 `file:/*` and a35 now fail (drive root, home); z02 (signedBy + `principal * *`, provider grants nothing) went S3 -> S2 (0/0, was 0/1); `PropertyPermission * write` is HIGH (it covers java.security.policy). Clean cases kept: deploy host1 0/0; host2-5 0/1 (D5 only: SocketPermission `*` and `/tmp/*`); `jrt:/java.base` AllPermission; read of a directory; pinned https narrow; AssertPrincipal exact; SerialObjectPermission named.

## Provider cross-check (VERIFIED, DirtyChai `ConcurrentPolicyFile`)
Independent of the lint's own probes: 16 attacker codebases (wildcard-TLD hosts, ports, ftp, IP, jar:, /tmp, root, Downloads, temp, cwd, relative) x 3 principal sets x every HIGH catalogue probe + AllPermission, asked of each file the new lint passes (default exit 0): **79 files covered, 0 grants, 0 failures**. Same check on the 153 files trunk passed: 37 files grant a HIGH permission (1014 grants), so the check bites.

## Mutants (VERIFIED; one at a time in `C:\t4\trunk`, restored and byte-compared identical each time; the 4 test classes)
| # | mutation | killed by |
|---|---|---|
| M1 | no symbolic wildcard-host rule | Gate wildcardHosts, unknownPermissionClass, eitherHalf; Corpus |
| M2 | principal fail-closed dropped | Gate principalThatCannotBeMadeFailsClosed; Corpus |
| M3 | `${user.home}` not expanded | PermissionSetsTest fileTargets; Gate filePermissionNames, grantPermissionForms; Corpus |
| M4 | no delim parsing | PermissionSetsTest delimiter forms; Gate grantPermissionForms; Corpus |
| M5 | AssertPrincipal entry removed | PermissionSetsTest x2; Gate catalogueGaps, grantPermissionForms; Corpus |
| M6 | unknown class clean | Gate catalogueGaps, grantPermissionForms, impliesThrows, unknownClass; Corpus |
| M7 | extension skip list back | Gate walkLints...; PolicyLintTest directoryWalk, unrecognised... |
| M8 | read capped at 1 MiB | Gate walkReadsTheWholeFile |
| M9 | implies-throws = no (provider and symbolic) | Gate impliesThatThrows; Corpus |
| M10 | no junction-loop finding | Gate linkLoopIsAFinding |
| M11 (extra) | no generated stand-in principal | Gate principalStandInIsAskedAbout |
All 11 killed. Final sandbox run before mutating: platform `Tests run: 12`; tools `Tests run: 69, Failures: 0, Skipped: 2` (PolicyLintTest 49, Gate 17, Corpus 2, PermissionSetsTest 1). Command: `mvn -o -pl jgdms-platform,tools/policy-condenser test -Dtest=PolicyLintTest,PolicyLintGateTest,PolicyLintCorpusTest,PermissionSetsTest -Dsurefire.failIfNoSpecifiedTests=false`.

## Not verified
Linux/macOS (junction test used `mklink /J`; symbolic-link branch not run); the ant target (comment edit only); a real SPIFFE/JWT principal class (stand-in only; class-name matching by the provider shown by the D2 tests, not by reading its source); `qa/harness/policy` (48 files) ran new: D1=98 D5=63 D6=40 P1=6 P2=2 W2=1 besides the old U/S kinds, not reviewed; the 6 P1 are probably `.grantperm` files now read as policies; the full platform suite.

## Questions
1. `.grantperm` files are linted "like policies" but 6 of the qa ones give P1 (not policy syntax?). Lint them with their own grammar or ignore?
2. `/tmp/*` write (deploy host2/4/5) kept MEDIUM against your "temp dir HIGH" to keep host2-5 unchanged; HIGH only for `/tmp/-`. Agree?
3. `.md` and README are ignored whatever they hold, as listed; accept?
4. `PropertyPermission * write` is now HIGH; `file:/*` and `file:lib/-` fail as drive-root and relative codeBases.
