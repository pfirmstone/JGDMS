claude-opus-5-5

# Review: generated META-INF/PERMISSIONS.LIST build step (pom.xml profiles + check-permissions-list-in-jar.sh), 2026-10-31

Reviewer claude-opus-5-5; producer claude-sonnet-5-5 (line 1 of `docs/INVESTIGATION-permissions-list-build-step-2026-10-31.md`), so this is a cross-model review. Skills loaded: handover-composition, production-code, code-comments (I judge the pom comment); rag-file does not apply. I also read the project skill `.claude/skills/jgdms-policy-condenser`. `E:\Claude\TRAINING_DOCS_INDEX.md` has no JGDMS row (grep -i jgdms: 0 rows), so no AI_AGENT_* pre-read applies.
I worked read-only and ran no mvn or java. Population: the clone `C:\Users\peter\Documents\GitHub\JGDMS` at HEAD d35bc3af0, the working-tree `JGDMS/pom.xml` (+94/-0, `git diff -- JGDMS/pom.xml`) and the untracked script. Labels: VERIFIED means I read the line or ran the read-only command named. PLAUSIBLE means it follows from code or Maven behaviour that I read or recall, but nothing was built. UNDETERMINED needs a build or the operator. RELAYED means a producer build result that I did not re-run.

## Verdict
With both properties unset the change does nothing, and it does what it claims in the cases its script tests. But point it at the real generator output and it has exactly one effect: it replaces a reviewed hand-written list with an unreviewed list that mixes runs and requests a class-loading gate permission. That happens only through `-wins`. Every way it can miss is silent. Do not ship generated lists through it as written. Findings follow, most severe first.

## F1 (security): the only real-data effect is the override, and the real lists cannot be reviewed as they stand. VERIFIED (facts), PLAUSIBLE (consequence)
- **Profile 1 has no real target.** Modules without a checked-in list: policy-service-dl and verdict-registry-dl. Of the 11 -dl poms (find), 8 have a list (`git ls-files '*src/main/resources/META-INF/PERMISSIONS.LIST'`), and reggie-dl-subsystem is `<packaging>esa`. The 14 generated files (`find qa -path '*proxy-permissions*'`) cover jgdms-lib-dl, mahalo-dl, outrigger-dl, reggie-dl and phoenix-dl, plus the unversioned jsk-dl, phoenix-dl, qa1-lookup-simulator-dl and reggie-dl. The two sets do not overlap. So "checked-in wins" ships nothing from any of the 5 directories, and only `-Dpermissions.list.generated.wins=true` changes a jar. The producer's "Real layout" PASS (report section 3) is that case. It replaced mahalo-dl's reviewed 3-entry list with a 1-entry `LoadClassPermission "ALLOW"` list.
- **A gate permission is requested.** `(au.zeus.jdk.authorization.guards.LoadClassPermission "ALLOW")` appears in 7 of the 14 generated lists and in 0 of the 8 hand-written ones (grep -l counts).
  - DirtyChai's `LoadClassPermission` javadoc (LoadClassPermission.java:31-34) says it is required to load classes with SecureClassLoader, so an administrator can block class loading from untrusted URLs. Ruling 8 grants it to the digest in java.policy.
  - The generator excludes only GrantPermission, UmbrellaGrantPermission and AllPermission (docs/ProxyPolicyGenerator.md:81-84).
  - `net.jini.security.BasicProxyPreparer.grant` (jgdms-platform, BasicProxyPreparer.java:418-424) grants the loader's advisory list through the whole-loader overload `Security.grant(proxy.getClass(), perms)`.
  - Scenario: a preparer with no configured permissions, and a client ceiling taken from the same audit (`<policy>.grantperm`). After prepareProxy, every domain of the proxy loader holds LoadClassPermission, including other jars in its codebase annotation. The jar has requested its own admission ticket. PLAUSIBLE; needs a test.
- **Host- and run-specific entries.**
  - 4 of the 14 have FilePermission entries on host paths, e.g. `"/home/user/GitHub/JGDMS/.../reggie-dl-4.0.0-SNAPSHOT.jar" "read"` (grep `^\(.*(/home/|C:/)`). Some also name a `file:/...` path, which FilePermission treats as a meaningless relative path.
  - defaulttest/jgdms-lib-dl has `SerialObjectPermission "jdk.proxy6.$Proxy11"`, a class name that depends on load order.
  - defaultsecurephoenix/jgdms-lib-dl has `RuntimePermission "createVirtualThread"`, which no hand-written list has.
- **One `<dir>` is not one audit run.**
  - In defaultnonactvm, jgdms-lib-dl and mahalo-dl name `file:/C:/Users/peter/...`, while outrigger-dl and reggie-dl name `file:/home/user/...` (line 17 of each file). defaultsecuretest is mixed the same way. All 10 harness files were committed in d5ebd674e (2026-07-17).
  - `ProxyPolicyGenerator.generate` (:291-301) cleans only the ceiling and never removes stale artefact directories.
  - So the pom comment's "its source (the audit run) is the trust boundary" (pom.xml:632) names a run that the directory does not identify.
- **The generated lists are also narrower.** The generated mahalo-dl list lacks `DeSerializationPermission "ATOMIC"`, ReflectPermission `newProxyInPackage` and `accessClassInPackage.com.sun.proxy`. The report has no non-audit QA run with a generated list inside the jar.

**What a reviewer needs before any generated list ships:**
1. Per artefact, an add/remove diff against the checked-in list, in git.
2. Provenance inside the file: policy name, audit commit, host and date. Today the header carries only the codebase URL (ProxyPolicyGenerator.java:443-445).
3. A deny check, unless each entry is justified: no LoadClassPermission, GrantPermission, AllPermission, FilePermission or SocketPermission on literal hosts or paths, RuntimePermission createClassLoader or setSecurityManager, ReflectPermission suppressAccessChecks, `${`, or `jdk.proxyN.$ProxyM` name.
4. The GrantPermission ceiling that will bound the list. The list is only a request; the ceiling is the control.
5. One normal (not polpAudit) QA run with the generated-list jars.
6. The operator's choice among the 5 policy directories. They disagree: jgdms-lib-dl has 4 distinct lists across 4 policies.

## F2: `-P proxy-permissions-list-generated` bypasses "checked-in wins". PLAUSIBLE, UNVERIFIED-NEEDS-BUILD
- Naming a profile with -P, or listing it in `<activeProfiles>`, ignores `<activation>`. The guard `<missing>${basedir}/src/main/resources/META-INF/PERMISSIONS.LIST</missing>` (pom.xml:641) is then skipped, and `<overwrite>true</overwrite>` (pom.xml:657) replaces the checked-in file without the `wins` property.
- The guard lives in activation, which is the one place a user can override.
- Ambient `-D` settings (MAVEN_OPTS, `.mvn/maven.config`) would also activate it. Nothing does so today: `.mvn/maven.config` is `-s .mvn/settings.xml`, and that settings file activates only `github-pack200` (read).
- Make it loud: put the check on the execution path, for example a rule in profile 1 that fails when the checked-in file exists and wins is not true.

## F3: stale copies outlive the property, and the identity gate cannot see it. PLAUSIBLE (part of the mechanism MEASURED)
- **MEASURED: the copy keeps the source's mtime.** mahalo-dl `target/classes/META-INF/PERMISSIONS.LIST` and its source are both `2024-11-18 17:13:37.4310695` (stat).
- **policy-service-dl:** after one build with the dir set, the copied file stays in target/classes until `clean`; the pom comment admits that nothing deletes it (pom.xml:630-631). The next build with no property puts it in the jar.
- **mahalo-dl after `wins=true`:** the default copy (overwrite=false) skips a target that is newer than its source. That is the maven-filtering rule as I recall it; I did not read it. If so, the generated list survives a plain build. The operator then believes report constraint (c): property unset means identical to [A].
- **Where the vacuity went:** `build()` in check-permissions-list-in-jar.sh deletes `$ROOT/$dir/target/classes/$ENTRY` and the jar before every build (line 78). So (c) was measured only from a clean state, and it reads PASS for both a healthy tree and a stale one. The report says the stale case was not measured (section 4) but does not attach that to (c).
- **`all` ends with `sc_override`** (line 184). That leaves the 215 B fixture, containing `RuntimePermission "@fixture@"`, in mahalo-dl/target, and a later non-clean `mvn install` could install it. The tree is clean now: the mahalo-dl target entry md5 is f697725e5dfc, the checked-in file's md5. policy-service-dl's jar has no entry (`unzip -p | md5sum` gives d41d8..., the empty md5).

## F4: the silent skip covers the production codebase forms. VERIFIED (code), PLAUSIBLE (consequence)
- **Name matching.** `ProxyPolicyGenerator.artefactName` (:457-477) keeps the last path segment minus `.jar`: `http://*/reggie-dl.jar` becomes `reggie-dl`, `httpmd:` codebases are the same, and digest-only grants become `digest-<...>`. Only the QA `file:` dist layout carries `-<version>`, the form that matches `${project.build.finalName}` (pom.xml:660).
- **Cases that ship nothing but report BUILD SUCCESS**, with one "skip non existing resourceDirectory" INFO line per module:
  - the jinitest directory (4 artefacts);
  - an audit at 4.0.0-SNAPSHOT used for a 4.0.0 build;
  - `wins=true` with no dir (untested, report section 4).
- **Why it is unsafe.** Nothing checks the operator's belief that the jar carries the list, and at run time the only trace is a CONFIG log line. The pom comment "a missing directory is skipped, not an error" (pom.xml:629) documents a success that did nothing. Per code-comments, the fix belongs in the code, not the comment.
- **Ways to make it loud:**
  - an expected-artefact list (`-Dpermissions.list.expect=...`), checked after package;
  - a required-file check on `<dir>/${finalName}/META-INF/PERMISSIONS.LIST`, bound only in -dl modules when the dir is set;
  - a check that fails when the dir is set and the jar's entry differs from its source.

## F5: `${...}` placeholders are copied but never resolved, and a malformed list fails silently. VERIFIED (code)
- **No expansion at run time.** pom.xml:630 justifies "no filtering" because "the generator can emit ${...} placeholders". `AdvisoryPermissionParser.parse` (jgdms-platform, AdvisoryPermissionParser.java:39-149) uses the name verbatim, and `PreferredClassLoader.PreferredPermissionsPrivilegedAction.run` (:1598-1604) passes it raw lines. A list generated with `--properties` therefore requests a literal `${x}`; as a FilePermission that is a relative path.
- **One bad line loses the whole list.** A malformed line throws IllegalArgumentException (parse :136). It is caught at :1611-1612 and logged at CONFIG, and the jar falls back to `DEFAULT_PERMISSIONS` (:1562-1563).
- **Today's lists are placeholder-free.** 0 of the 14 generated lists contain `${` (grep), because `PolicyUpdatePostProcessor.generateProxyArtifacts` (:327-331) passes no `--properties`.
- **The test gap.** The script's fixture ships `${fixture.property}` and passes. Byte fidelity is the right test of the copy, but nothing checks that a shipped list parses or is free of placeholders.
- **Missing:** a build-time parse of the copied list with AdvisoryPermissionParser that fails on `${` or a parse error.

## F6 (Q1): `${basedir}` keys on the module. VERIFIED for one invocation, PLAUSIBLE for the others
- **`-pl <module> -am` from the root, Maven 3.9.15.** The producer's mutant M1 discriminates here. Had `${basedir}` resolved to the root, or stayed unresolved, mahalo-dl would see "missing" and checked-in-wins would FAIL. M1 (condition removed) FAILED and [B] PASSED (report section 3, RELAYED).
- **`mvn -f <module>/pom.xml` or running from the module directory.** PLAUSIBLE that the result is the same, because the activation context's project directory is the POM being built. UNVERIFIED-NEEDS-BUILD.
- **The root aggregator** has no file, so profile 1 activates there and skips. Harmless.
- **Maven older than 3.2.2** OR-ed activation conditions (my recollection), so the property alone would override the checked-in lists. The root pom has no parent and no requireMavenVersion or prerequisites (grep: 0).
- **Maven 4:** UNDETERMINED.

## F7: scope and jar identity. PLAUSIBLE
- **Scope.** The profiles are in the root parent, so they reach every module, matched only on finalName. `isProxy` treats any grant that has a digest clause as a proxy (ProxyPolicyGenerator.java:309-310), so a digest grant on a service jar would produce a directory that matches a non-dl module.
- **Identity.** Changing `-D` changes the jar bytes (CONTENT-HASH and .pack.gz) under one GAV. A digest pinned in the default policy (ruling 7) then pins one variant, and nothing in the jar says which directory produced it.
- **Duplication (production-code).** The two `maven-resources-plugin` blocks (pom.xml:647 and :685) are near-copies; the diff could be one block. REUSE: no other build step copies the list (`git grep 'PERMISSIONS\.LIST|proxy-permissions' -- '*.xml' '*.properties' '*.bnd'` hits only the new pom lines and the qa/build.xml:555-556 comment).

## F8: the script. VERIFIED (read)
- **Vacuity.** `absent` and `checked-in-wins` both pass on [A], and the report says so.
- **The negative controls are real** (RELAYED): M1 makes checked-in-wins FAIL; assert-absent on the generated jar FAILs; assert-equals against the checked-in file on the override jar FAILs; a wrong-artefact fixture FAILs. These make checked-in-wins load-bearing for the guard, and absent-nomatch load-bearing against a copy from another artefact's directory.
- **Constraint (d)'s "exactly one entry" cannot fail** when copying from a directory. Its `other` count is the part that would catch a wrong outputDirectory.
- **Not covered:** an incremental build (F3), explicit -P (F2), unversioned or version-mismatched directories (F4), `wins` with no dir, parse validity (F5), and the `-f` or module-directory invocations (F6).
- **Destructive outside `$WORK`.**
  - Line 78 runs `rm -f` on `target/classes/META-INF/PERMISSIONS.LIST`, the module jar and `*.pack.gz` in the module target directories of the SHARED clone.
  - Each `mvn -am package` (line 79) re-packages upstream jars: 11 modules, per report section 5.
  - No source file is touched, and the `rm -rf` calls (lines 115, 126, 140, 150) stay inside `$WORK/gen-*`.
  - The script takes no build lock (header line 9).
- **The version parse works on the CRLF root pom.** The script's sed gives `[4.0.0-SNAPSHOT]`, and od shows no `\r`.

## F9 (Q5): a Maven profile or an Ant step? Neither as a build-time injection
- **Recommendation.** The less error-prone step is a promotion inside the existing qa/build.xml policy-update flow, or as a PolicyUpdatePostProcessor option. After the audit, it would copy each generated list into its module's `src/main/resources/META-INF/PERMISSIONS.LIST` and leave a git diff for a human to review and commit.
- **Why:**
  - A plain `mvn package` then builds the jar from tracked content: no input from outside the tree, no flag, no stale state, and a digest that can be reproduced from the commit.
  - The add/remove diff is the review artefact F1 asks for.
  - The artefact-to-module mapping runs once, where the generator runs, and can fail loudly on an unmatched directory.
- **It fits the existing flow**, which already writes tracked qa policy files and restores protected ones with `git checkout` (qa/build.xml:549-551).
- **The producer's argument cuts the other way.** It favours Maven because an Ant step "does not run for a plain mvn package" (report section 2). That is the reason against injection: a plain package should not depend on outside state.
- The choice is the operator's. Promotion also retires the question of whether checked-in or generated lists take precedence.

## F10 (Q6): no behaviour change with both properties unset. VERIFIED (read), plus RELAYED
- The two profiles activate only on `permissions.list.generated.dir` and on `permissions.list.generated.wins=true`. Neither is active by default, and the diff contains nothing else.
- The producer's byte-identity of both jars (report section 3 (c)) is RELAYED, and it holds only from a clean target (F3).

## Not verified here (UNVERIFIED-NEEDS-BUILD)
- Any build.
- Maven's -P and AND/OR activation behaviour on this pom.
- maven-filtering's newer-than rule.
- Whether a dynamically granted LoadClassPermission is consulted when later classes are defined (F1).
- Whether the ceiling in a policy file is property-expanded while the dynamic grant is not (F5).
