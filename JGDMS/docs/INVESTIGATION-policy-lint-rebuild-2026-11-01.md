claude-sonnet-5-5
# PolicyLint rebuild: AllPermission verdict from the production policy provider, 2026-11-01

VERIFIED = I saw the output this session. UNDETERMINED = not checked. Nothing committed or pushed; the main clone's files were not touched
(its `.git` now lists my detached worktree; remove with `git worktree remove --force <S>\wt`). S = `...\scratchpad\lint2`.
Patch: `S\lint-rebuild.patch` (36 files: 8 changed or new Java/doc/pom/ant files plus 28 fixtures; 1078+/106-). VERIFIED `git apply --check` and a real
`git apply` on a fresh `git archive HEAD` copy (with the repo's `.gitattributes`) give files byte-identical to my worktree, CRLF kept.
Setup note: the worktree path exceeds MAX_PATH for 2 test-source dirs (archetype `src`, benchmarks `serialization/src`), so the worktree is sparse (those 2 `src` dirs absent).

## What changed (VERIFIED present)
- `PolicyVerdict.java` (new): loads the file with the provider's own `DefaultPolicyParser` (parse failure = P1) and asks `new <Provider>(URL[]).implies(pd, AllPermission)` for
  `http://evil.example.com/evil.jar` with no principal / with an X500Principal, a `file:` codebase, a `jrt:` module, and a null CodeSource. By reflection, release 8.
- `PolicyLint.java`: kinds U1 U2 **U3 U4** S1 S2 **S3 S4 S5** P1; `--define NAME=VALUE` (repeatable, also `--define=`), `--provider=auto|dirtychai|platform`; `PROVIDER`
  and per-file `VERDICT` lines. Exit codes as briefed (fail: U1 U2 U3 U4 P1; `--strict` adds S1 S3 S4; `--audit-input` any S).
- `ProxyPolicyGenerator.parseGrants` text scan: `commentChar('/')` (lone `/` = comment), `keystore` read like the real scanner (no `;` needed), `principal * "n"` class wildcard.
  `signedBy ","` now unscoped in the lint. `ProxyPolicyGenerator` tests (45) still pass.
- `qa/build.xml`: `-Dpolicy.lint.define.NAME=VALUE` (via `<syspropertyset>`, repeatable), `policy.lint.provider`, `policy.lint.jvmargs`. `ProxyPolicyGenerator.md` lint section rewritten
  (verdict source, kinds, exit codes, property rule, DirtyChai fail-open). SKILL.md: one sentence ("no AllPermission grant", scoped one masks too). `pom.xml`: a jdk>=9 profile passes
  `--add-exports java.base/au.zeus.jdk.authorization.policy=ALL-UNNAMED` to surefire so the DirtyChai provider is exercised (a JDK without the package only warns).

## Provider choice (decision, with reason)
`auto` = DirtyChai `au.zeus.jdk.authorization.policy.ConcurrentPolicyFile` if reachable, else jgdms-platform `org.apache.river.api.security.ConcurrentPolicyFile`.
Reason: the DirtyChai class is what `CombinerSecurityManager` and polpAudit install, so it is the true oracle. But `java.base` does NOT export that package (VERIFIED:
`IllegalAccessError`/`IllegalAccessException` without `--add-exports`), so a plain run falls back to jgdms-platform and the `PROVIDER` line says so and why.
Agreement: VERIFIED the two give identical kinds and VERDICT values on all 28 review fixtures (`diff` of the full tables empty; also `PolicyLintTest.providersAgreeOnFixtures`, run with the
export) and identical findings on the 41-file harness corpus (diff differs only in the provider class named in the U3 reason). Stock `sun.security.provider.PolicyFile` is never used.
The review's real-probe2 answers (stranger/any-principal) match all 24 loadable fixtures. Platform provider needs jgdms-collections on the class path (ant already has it).

## Tests (VERIFIED): `mvn -o -pl jgdms-platform,tools/policy-condenser test -Dtest=<the 6 classes>` = 152 run, 0 fail, 1 skipped (`lockedFileIsP1`: Windows cannot chmod; the MX2 test itself runs)
PolicyLintTest 19 -> 36 tests. Fail-first (VERIFIED, `S\failfirst.log`): the new test class against HEAD's PolicyLint/ProxyPolicyGenerator (3 API-dependent tests removed): 33 run, 19 failed.
Tests: all 28 fixtures with kinds/exit/strict+audit/verdict values; MX1 `missingPathNextToGoodPathExitsTwo`; MX2 `unreadableFileIsP1`; MX3 `loneSlashCommentsOutTheLine`; U3, U4, `--define`
(incl. property restored), S3 (4 forms), S4, S5 (4 forms), provider-absent fallback and forced-provider exit 2, keystore, `signedBy ","`, BOM/keystorePasswordURL P1, exit matrix (31 rows).
Wrong outputs that still pass (not defended): fixture-name special-casing; `jrt:/m/` with a trailing slash as S5; a lone `/` glued to a word (`/x`); S4 for `principal`-scoped meta grants; the
ant property path beyond one manual run; a provider that answers right for `evil.example.com` but wrong for other hosts.

## Mutants, one at a time, on a `git archive` copy (`S\mut`), restored and compared (VERIFIED, `S\mutants2.log`); all 12 killed
MU3 (U3 off) 4 tests fail; MU4 (U4 off) 5; MS3 (wildcard principal as S2) 3; MS4 (no S4) 4; MS5 (no S5) 2; MDEF (`--define` ignored) 3; MFB (no fallback) 1; MKS (old keystore skip) 4;
MSG (signedBy "," scoped) 3; **MX1 1 (missingPathNextToGoodPathExitsTwo); MX2 1 (unreadableFileIsP1); MX3 3 (loneSlashCommentsOutTheLine +2)**.
A first mutant run was invalid (restore failed, stale classes) and was discarded and rerun with a fixed driver.

## Corpus (VERIFIED; copies in `S\corpus`; cmd per dir: `java -cp <tools classes;platform classes;deps> org.apache.river.tool.PolicyLint --format=tsv corpus\<dir>`, `S\lint.sh`)
| Dir | files | committed lint | rebuilt lint (default) |
|---|---|---|---|
| harness | 41 | U1=10 S1=165 | U1=10 **U3=6 U4=38** S1=36 **S4=2 S5=129**, exit 1 |
| deploy | 5 | clean | clean |
| jinitest.policy | 1 | U1=1 S1=10 | U1=1 S5=10 |
| jeri test.policy | 1 | U1=1 | U1=1 |
Committed numbers reproduced by compiling HEAD (`S\oldclasses`). U3 and U4 are all `*.policy.old`: `defaultnonactvm`, `defaultsecurephoenix`, `defaultsecuresharedvm`, `defaultsecuretest`, `defaultsharedvm`
(U3 1 each; U4 3,2,2,5,2) and `defaulttest` (U3 1, U4 24). Why: their codeBases use `${jsk.home}`, `${qa.home}`, harness jar properties, and `${jsk.iiop.jar}`-style names the runtime defines; unset
here, the production parser unscopes them. S4=2 are the review's F6 grants (`defaultsecuretest.policy.old:69,114`). The 36 S1 are exactly those property codeBases; the 129 jrt S1 became S5.
With 10 `--define`s for the harness properties U3 drops to 1 and U4 to 9 (`defaulttest.policy.old` uses 9 more properties). deploy stays clean because its undefined `${jgdms.lib-dl}` grants are not AllPermission.
Ant (VERIFIED, `ant ... policy-lint`): strict + wildcard principal fails (S3, exit 1); `-Dpolicy.lint.define.no.such.prop=/opt/lib` turns f01 from U3+U4 (exit 1) to S1 (exit 0).

## Not verified
Linux/macOS; a real polpAudit run on a linted policy; JDK 8 runtime; the `jrt:` question on a JDK without a jrt handler (code returns false); the review's fx3 stress files (big, NUL, UTF-16);
subdirectories skipped silently by `visitFileFailed` (unchanged, review item c); `--define` values containing a `;`/`:` path list expanding to several codebases (Segment); signedBy/principal-name `${}` undefined (same
fail-open path, not checked by U4); that the DirtyChai fail-open also exists in a running JVM with a different `-D` set than the lint JVM.

## Decisions needed
1. U4 scope: only AllPermission/meta grants (chosen, keeps deploy clean). Extending to every grant would fail all 5 deploy policies unless `--define`d.
2. Default provider is jgdms-platform unless the tool JVM gets `--add-exports`; make the ant target pass it by default on DirtyChai JDKs?
3. Report the DirtyChai fail-open (undefined `${prop}` in `codeBase` = unscoped grant, `DefaultPolicyParser.resolveGrant`) to its owners; the lint only detects it.
4. `.policy.old` files: U3/U4 now fail them; retire, `--define`, or exclude via a dedicated glob.
5. A parse-rejected file (BOM etc.) is P1 (exit 1) as well as its text finding; before, a BOM file got no P1.
