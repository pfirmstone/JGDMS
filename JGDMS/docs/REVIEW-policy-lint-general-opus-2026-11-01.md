claude-opus-5-5
# Adversarial review: 66974f70f (PolicyLint as a general dangerous-policy gate), 2026-11-01

CONFIRMED = I ran it in a private copy and saw the output. PLAUSIBLE = reasoned from the code, not run.
SP = `C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad`.
- Copy: `git archive HEAD` (66974f70f) into `SP\opus-r8\src`. I built and tested it with `mvn -o -pl jgdms-platform,tools/policy-condenser test -Dtest=PolicyLintTest,PermissionSetsTest -Dsurefire.failIfNoSpecifiedTests=false`. Baseline: 49 + 9 + 1 tests, 0 failures, 2 skips (the chmod tests). I did not run `install`, and `~\.m2` was not written. The live repo is clean apart from this file.
- Lint driver `SP\opus-r8\run.sh`: `java [--add-exports java.base/au.zeus.jdk.authorization.policy=ALL-UNNAMED] -cp <copy classes> org.apache.river.tool.PolicyLint --provider=dirtychai|platform [--strict] --format=tsv FILE`. Results: `SP\opus-r8\regress.txt` (old corpus), `SP\opus-r8\adv2-results.txt` (my 159 policies in `SP\opus-r8\adv2`).
- Direct provider questions: `SP\opus-r8\probe\Ask.java`, run through `SP\opus-r8\ask.sh`. It builds DirtyChai's `ConcurrentPolicyFile(URL[])` and calls `implies(new ProtectionDomain(cs, null, null, principals), perm)`.
- The two providers gave the same exit codes on every file, in both modes.

## Findings, ranked by exploitability (exit = default / --strict; "provider" = DirtyChai ConcurrentPolicyFile, cross-checked on platform where noted)
1. **A wildcard host outside the fixed probe hosts passes. CONFIRMED HIGH.**
   - The stranger subjects are only `evil.example.com` (http, https, :8443, ftp, jar:http) and `203.0.113.9:8080` (`PolicyVerdict.STRANGER_URLS`).
   - `https://*.net/-` with `createClassLoader` exits **0/0**. The provider (both kinds) grants it to `https://evil.net/x.jar`.
   - AllPermission to `https://*.org/-`, `http://*.example.org/-`, `http://*:8443/-` exits **0** (1 with --strict, S1 only); the provider grants AllPermission to `https://evil.org/x.jar`, `http://attacker.example.org/x.jar`, `http://evil.example.com:8443/x.jar`.
   - Stranger here: anyone who can register a name under that suffix, or serve on that port.
   - Fix direction: build probe URLs from each grant's own codeBase (replace `*` with a fresh label, try another port when the codeBase has `:*` or a port wildcard), or treat any `*` in a remote host as a stranger.
2. **Grants to wildcard principals of the classes this system actually uses are never probed. CONFIRMED HIGH.**
   - `grant principal au.zeus.jdk.authorization.spire.SpiffePrincipal * { RuntimePermission "setSecurityManager" }` exits **0/0**. JwtPrincipal + `createClassLoader` and KerberosPrincipal + `createClassLoader` also exit **0/0**.
   - The only output is a `NOTE ... cannot be instantiated from a name`. `principals()` tries only the names `lint-stranger` and `CN=lint-stranger`, the SpiffePrincipal package is not exported, and jgdms-security-jwt is not on the lint classpath.
   - The provider grants setSecurityManager to code run as `SpiffePrincipal(spiffe://evil.example.org/attacker)`.
   - Stranger: any workload in any trust domain the service accepts.
   - Related, by design: D2 (a dangerous permission to any principal) and S3 are MEDIUM, so these exit **0** by default (1 with --strict), and the provider grants each one:
     - `principal * *` + setSecurityManager;
     - `X500Principal *` + `<<ALL FILES>>` write,execute;
     - the old a04/a19/a20/a28/a33 (AllPermission to any X500 or UserPrincipal subject; e.g. a20 answers true for `UserPrincipal("mallory")`).
   - Stranger here: any peer that authenticates with any principal of that class. The test `principalOfAnyClassIsProbed` asserts exit 0.
3. **FilePermission names that use `${...}` are never probed by name. CONFIRMED HIGH.**
   - The lint skips named probes whose name contains `${`, and `broadFile` only knows roots.
   - `"${user.home}${/}-"` and `"${user.home}${/}*"` write: **0/0**; the provider grants a stranger write on `C:\Users\peter\.java.policy` and the Startup folder's `x.bat`. `"${java.home}${/}-"` write: **0/0**; the provider grants write on `...\jdk\conf\security\java.security`. Each can become AllPermission at the next start.
4. **A `..` path reaches the root. CONFIRMED HIGH (Windows).** `FilePermission "/tmp/../-", "write"` exits **0/0**. The provider grants write on `\x` and `\Windows\System32\x.dll`, but not on `C:\x`, the only form the catalogue probe asks about. `C:\\Windows\\..\\-` and `C:\\Users\\..\\-` are caught (D1).
5. **GrantPermission in `delim=` form. CONFIRMED HIGH.**
   - `GrantPermission "delim=| java.lang.RuntimePermission |createClassLoader|"` exits **0/0**. The provider (both kinds) grants `GrantPermission(RuntimePermission createClassLoader)` to a stranger.
   - Cause: `grantDanger` splits the target on the delimiter and loses the class/name pairing. The catalogue's GrantPermission probes cover only setSecurityManager, setPolicy and suppressAccessChecks.
   - The same happens with `GrantPermission "net.jini.security.AssertPrincipalPermission \"*\", \"*\""` (0/0, provider grants).
6. **Gaps in the catalogue, where an unlisted entry counts as clean. CONFIRMED** (each exits **0/0**, and the provider grants it to a stranger; my severity in brackets):
   - `AssertPrincipalPermission "*","*"` [HIGH]. The previous commit added this class and the catalogue does not know it. Its javadoc says `"*","*"` "gives the peer every identity" except Spiffe/Jwt. An unscoped grant gives that to every peer (PLAUSIBLE: I did not drive the dispatcher's check).
   - `PrivateCredentialPermission "* * \"*\"","read"` [HIGH: private keys of any reachable Subject].
   - `NetPermission "*"` (specifyStreamHandler) and `SSLPermission "*"` (setDefaultSSLContext) [HIGH: replaces the URL handlers or the TLS trust used by JERI and codebase downloads].
   - `RuntimePermission accessClassInPackage.jdk.internal.misc` [MEDIUM; `sun.*` is already MEDIUM, `jdk.internal.*` is not catalogued at all].
   - `setIO` [MEDIUM].
   - `PropertyPermission "java.security.policy","write"` [MEDIUM, PLAUSIBLE impact].
   - Not catalogued, exit 0/0, provider not asked:
     - RuntimePermission: getClassLoader, modifyThread, modifyThreadGroup, writeFileDescriptor, readFileDescriptor, manageProcess, enableContextClassLoaderOverride, charsetProvider;
     - SecurityPermission: `clearProviderProperties.*` (`removeProviderProperty.*` is HIGH), getPolicy, createPolicy.*;
     - `MBeanPermission "*","*"`, ManagementPermission control, AttachPermission, LoggingPermission control, Kerberos Delegation/ServicePermission `*`, DownloadPermission;
     - both LoadClassPermission names with `*`. The DirtyChai one loads and implies no probe. The `net.jini.loader` one gives only a NOTE.
7. **A permission class the lint cannot judge counts as clean. CONFIRMED** (from the code: PermissionSets/PolicyVerdict).
   - `com.example.NoSuchPermission "*"` exits **0/0**, and so does `net.jini.loader.LoadClassPermission "*"`; the only output is a NOTE.
   - `GrantPermission "java.io.*"` gives NOTE "GrantPermission is not loadable here", which is wrong: the class loads, but its target is rejected. Exit 0/0.
   - On a JDK or classpath that differs from the service's, every permission the lint cannot load passes. Making a NOTE at least a strict-mode kind would close this.
8. **Directory walk. CONFIRMED.** Each case below has a clean `good.policy` beside it:
   - An unscoped AllPermission saved as `all.txt`, `conf.properties` or `server.xml` gives **0/0** with no line at all. The `UNRELATED` extensions are skipped before `looksLikePolicy`, and the test `unrecognisedPolicyLookingFilesFailTheGate` asserts that `notes.txt` is not read. The provider loads `all.txt` and grants AllPermission.
   - `server.pol` with 2.1 MB of spaces before the grant: W2 only, exit **0** (1 with --strict), because `looksLikePolicy` reads only the first 1 MiB. The provider grants AllPermission.
   - A `hide.policy` inside a `*.proxy-permissions` directory, and `x.grantperm`, are skipped silently (0/0). The provider loads `hide.policy` (it is skipped by design).
   - A directory junction is skipped silently. Symlinks need admin rights here, so I did not test them.
9. **Codebases under the user's home directory. CONFIRMED MEDIUM.** `file:${user.home}/-` with createClassLoader: **0/0**; with AllPermission (old a35): 0/1, S1. The provider grants both to `file:/C:/Users/peter/Downloads/evil.jar`; the file: probe uses only `/lint-stranger`.
10. **Mutation sample** (one mutant at a time on the copy; restored and re-run green after; logs `SP\opus-r8\mut*.log`):
    - M1, no https stranger URLs: killed by `httpsWildcardCodebaseGrantsAStranger`.
    - M2, a throwing `implies` is treated as "no" (`collect` catch: `yes = false`): **survived**, 49/49 pass. No test checks fail-closed behaviour for a throwing permission.
    - M3, files with no extension treated as unrelated: killed by `unrecognisedPolicyLookingFilesFailTheGate`.
    - M4, AllPermission no longer D3 for `file:` code: killed (3 tests).
11. **Smaller (CONFIRMED):** W1 says "although other paths did" when the directory was the only path; a grant to exactly `http://evil.example.com/evil.jar` (old b05) is U3 because that is the probe host.

## Old (c96183b6d, opus-r6 build) vs new (66974f70f) on the 42-file corpus (DirtyChai provider; platform identical)
| files | old exit (strict) | new exit (strict), kinds |
|---|---|---|
| a01 a02 a11-a16 a18 a24 a25 a31 a32 a36 b02 b05 (U1/U3/P1) | 1 (1) | 1 (1), unchanged |
| a05 a08 a30 (U2) | 1 (1) | 1 (1); a05 a30 add D1 |
| a06 SecurityPermission *, a07 setSecurityManager, a22 setPolicy+3, a23 GrantPermission setPolicy | **0 (0)** | 1 (1), D1 |
| a09 file:/-, a34 file:${/}-, a10 jrt:/- | 0 (1) | 1 (1), D3 / D4 |
| a17 https://*/-, b04 https://*.com/- | 0 (1) | 1 (1), U3 |
| a04 a19 a20 a28 a33 (wildcard principal, AllPermission) | 0 (1) | **0 (1)**, S3: finding 2 |
| a35 file:${user.home}/- AllPermission | 0 (1) | **0 (1)**, S1: finding 9 |
| a03 a21 a29 (signedBy unresolvable), a26 file:/*, a27 codeBase "-" | 0 (1) | 0 (1), S1. The provider grants nothing to a stranger (checked a26, a27), so this is fine |
| b01 b03 b06 (S2) | 0 (0) | 0 (0), fine |

## Ran and found fine (CONFIRMED, exit 1/1 unless stated)
- FilePermission: `<<ALL FILES>>` write, delete or execute, or combined actions with odd case and spacing (D1); read and readlink are D5 (0/1).
- FilePermission write on `/-`, `/*`, `C:\\-`, `c:/-`, `D:\\-`, a relative `-` or `*`, `../../../-` (D1). A relative `lib/-` is clean.
- RuntimePermission `*`, setSecurityManager, createClassLoader, defineClassInPackage, `loadLibrary.*`: D1.
- SecurityPermission `*`, setPolicy, `setProperty.*`, `insertProvider[.*]`, `putProviderProperty.*`, `removeProviderProperty.*`, createAccessControlContext: D1.
- ReflectPermission suppressAccessChecks and `*`; AuthPermission doAsPrivileged, modifyPrincipals, modifyPrivateCredentials, `*`; DeSerializationPermission `*`; NativeInvocationPermission `*`: D1.
- GrantPermission of RuntimePermission `*`, of `<<ALL FILES>>` write, of suppressAccessChecks, of a `;` list, of a nested GrantPermission of AllPermission, of `java.*`.
- D5 (0/1): SocketPermission `*` (4 forms), PropertyPermission `*` write, accessDeclaredMembers, `accessClassInPackage.sun.*`, exitVM, setContextClassLoader, the other AuthPermissions, `newProxyInPackage.*`.
- Syntax variants: comments between tokens, `\t\f\v` and CRLF, mixed-case keywords, an empty actions string, a missing final `;` (D1). A unicode escape in the class name or in the quotes, and a nested `grant` or `keystore` block, give P1.
- Provider answers no, so clean is correct: a unicode escape or line continuation inside the name, `signedBy` with an absent alias or a missing keystore, a per-permission `signedBy`.
- `file:${undefined}/-` + createClassLoader: D1. `file:/-` + createClassLoader: D3.
- Clean cases: deploy/policy host1 0/0 and host2-5 0/1 (D5, SocketPermission `*` accept,resolve). `jrt:/java.base` AllPermission: 0/0 (S5). Read on a specific directory: 0/0.
- Walk:
  - uppercase `ALL.POLICY`, a hidden `.hidden.policy`, three levels deep, `java.policy.disabled`: U1;
  - a file named `policy`, a `policy~`, a file inside a directory named `x.policy`, a directory of only `.pol`: P2 + U1, exit 1;
  - a BOM: P1 + U1;
  - UTF-16 named `.policy`: P1 (the provider cannot load it). UTF-16 as `.pol`: W2, and the provider grants nothing;
  - zero-byte and binary files: W2;
  - a directory holding only `.md`: exit 2.

## Not checked
Real symlinks (admin rights needed); Linux and macOS; the ant target; the qa/harness corpus; whether the dispatcher's AssertPrincipalPermission check actually matches an unscoped grant (finding 6 rests on the provider answering true for a stranger codebase); an actual dynamic grant through finding 5; JwtPrincipal asked directly (its jar is not on my classpath); a parser divergence between DirtyChai and platform (I found none in about 200 files).

## For the operator to settle
1. deploy/policy host2-5: SocketPermission `*` accept,resolve is granted to all code, which fails only `--strict`. Is any-host accept intended for all code? If yes, keep it as D5. If not, scope it.
2. `defaultsecurephoenix.policy.new` and `qa.policy`: are the HIGH grants to everyone intended? They are files a person can read; I did not judge them.
3. Unrecognised files: should every unrecognised file fail? Finding 8 shows that the extension skip list, not the P2/W2 split, is where an AllPermission file gets through.
4. Should a dangerous permission granted to any principal of a class (D2/S3) fail the default run, given the earlier finding that peers can assert user principals?
5. Should `${user.home}`, `${java.home}` and `${java.io.tmpdir}` count as broad, both as FilePermission targets and as codeBases?
