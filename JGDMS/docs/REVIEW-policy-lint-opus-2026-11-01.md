claude-opus-5-5
# Review: PolicyLint (commit 61d597451, producer claude-sonnet-5-5), 2026-11-01

Reviewer: Opus 5.5 (a different model from the producer). Skills loaded: handover-composition, production-code, code-comments.
Grades: VERIFIED = I ran it or read the line; PLAUSIBLE = mechanism read, outcome not run; UNDETERMINED = not checked.
SCR = `C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\opus-lint`.
It holds a `git archive 61d597451` copy, plus `common.xml` from the same commit so qa/build.xml loads.
Baseline: `mvn -o -B test` = 114 tests, 0 failures (SCR\build-baseline.log). The live tree and DirtyChai were only read; E:\ was not consulted (brief).

## Reference: the real provider, asked directly (null-validated)
SCR\probe\RealPolicyProbe.java (DIAGNOSTIC) runs the installed DirtyChai JDK (`C:\Program Files\DirtyChai\jdk`).
It asks `new ConcurrentPolicyFile().implies(pd, AllPermission)` for a stranger's `http://evil.example.com/evil.jar`, with no principal (h) and with an arbitrary X500 principal (hp); that provider is the one `CombinerSecurityManager` installs (:146-150) and polpAudit uses (SecurityPolicyWriter.java:272).
Controls: c1 `grant {AllPermission}` gives h=true; c2 `codeBase "http://good.example.com/ok.jar"` gives h=false.
Without an SM, `Policy.getPolicy()` returned `sun.security.provider.PolicyFile`, which disagrees on 7 fixtures; it is not the production verdict.
Raw results: SCR\real-probe2.txt (real provider), SCR\lint-fx.txt (lint, default and `--strict --audit-input`); fixtures in SCR\fx, SCR\fx3, SCR\fx4.

## Findings, most severe first
**F1 HIGH VERIFIED. `keystore` without `;` hides the next grant from the lint in EVERY mode.**
The lint does `"keystore".equalsIgnoreCase(st.sval) ... skipToSemicolon(st)` (`ProxyPolicyGenerator.parseGrants` :1727-1729).
The real `DefaultPolicyScanner.readKeystoreEntry` reads at most two quoted strings, then calls `st.pushBack()` (DirtyChai :169-184).
f04 is `keystore "file:/nonexistent.jks"` followed by `grant { permission java.security.AllPermission; };`. Real provider: h=true. Lint: 0 findings, rc=0, even with `--strict --audit-input`.
Ant with `-Dpolicy.lint.audit.input=true -Dpolicy.lint.strict=true` gives BUILD SUCCESSFUL. f23 (`keystore "x", "JKS"`) behaves the same. The skip is inherited (`61d597451^` :1709-1710).

**F2 HIGH VERIFIED. A codeBase containing `${prop}` scores S1 (exit 0), but DirtyChai UNSCOPES the grant when the property is unset.**
`DefaultPolicyParser.resolveGrant` (DirtyChai :211-224) catches `ExpansionFailedException` (thrown at Segment.java:176), logs it, and leaves `codebases` empty.
`URIGrant.implies` then returns true for everything: `if (location.isEmpty()) return true;` (URIGrant.java:140).
f01 `file:${no.such.prop}/lib/x.jar` and f02 `file:${jsk.home}${/}lib${/}*` (jsk.home unset): h=true, hp=true. Lint: S1, rc=0. Ant default: BUILD SUCCESSFUL.
Stock `sun.security.provider.PolicyFile` skips such an entry (h=false), so this is a **DirtyChai fail-open, to report to its humans** (CLAUDE.md Invariant 2 / HC-2).
In the corpus, 36 of the 165 harness S1 findings are `file:${...}` codeBases, all in `*.policy.old` (S1 reasons grouped by prefix, SCR\out-harness.txt).
Every deploy policy scopes its grants by `codebase "file:${jgdms.lib-dl}/*"` (not AllPermission; same mechanism; PLAUSIBLE). JGDMS's own copy catches the same exception (jgdms-platform DefaultPolicyParser.java:277; PLAUSIBLE).

**F3 MEDIUM VERIFIED. A lone `/` comments out the rest of the line for the real parser; the lint treats it as a token.**
The lint calls `st.ordinaryChar('/');` (`ProxyPolicyGenerator.policyTokenizer` :1752). The real scanner uses a default `new StreamTokenizer(r)`, and `configure()` (DirtyChai :99-105) keeps its `commentChar('/')`.
f03 `grant / codeBase "http://good.example.com/ok.jar"`, newline, `{AllPermission}`: real h=true; lint S1, rc=0.

**F4 MEDIUM VERIFIED. `signedBy ","` scores S1 but is unscoped.** It yields no aliases, so `resolveSigners` returns `new Certificate[0]`.
`CertificateGrant.impliesSigners` then hits `if (certs.isEmpty()) return true;`. f05: real h=true; lint `present(g.signedBy)` gives S1, rc=0.

**F5 MEDIUM VERIFIED. Wildcard principals score S2, which passes `--strict`.**
f06 `principal * *` and f17 `principal javax.security.auth.x500.X500Principal *`: real hp=true, h=false, i.e. AllPermission for any authenticated subject.
Lint: S2 with rc=0, also under `--strict`; only `--audit-input` fails it.

**F6 MEDIUM VERIFIED (read). A scoped `GrantPermission` targeting AllPermission is never reported (the producer admits this).**
A corpus instance: `defaultsecuretest.policy.old` :69/72 and :114/115 hold `GrantPermission "java.security.AllPermission \"\", \"\""` on `${...harnessJar}` and `${qa.home}` codeBases.
With F2, these become an unscoped meta-grant when the property is unset (PLAUSIBLE end-to-end).

**F7 MEDIUM VERIFIED. REUSE miss: three closer production artefacts exist and the commit names none.**
(a) `jgdms-platform` is already a compile dependency here (pom.xml). Its `org.apache.river.api.security.DefaultPolicyScanner` has `configure()`, `scanStream()` and `readKeystoreEntry()` that diff EMPTY against DirtyChai's.
(b) `envcheck` `CheckPolicy.AllPermissionsTask` already asks the live Policy whether a stranger CodeSource implies AllPermission. That outcome-based check could not have missed F1-F4.
(c) `qa/harness/policy/check-spiffe-policy-no-broad-grants.sh` is an existing unqualified-grant gate (exit 0/1/2). It also fails AuthPermission `*`/`callAs`/`doAs` and RuntimePermission `*`/`setSecurityManager`, which PolicyLint ignores: two definitions of "unscoped broad grant" now exist. Commands: `git grep -l -i AllPermission 61d597451 -- '*.sh' 'JGDMS/tools/**/*.java'` and `git grep -l StreamTokenizer 61d597451 -- JGDMS/jgdms-platform`.

**F8 LOW VERIFIED. P1 does not mean "the real parser rejects this file".**
The real provider rejects these files WHOLE, but the lint scores U1 with no P1: BOM (f08; `readText` :1890 strips it, the real reader does not), `keystorePasswordURL` (f24; real :139-141 rejects it), a nested `{` (f26).
`PolicyLintTest.caseCrlfBomAndKeystore` (:241-259) asserts BOM acceptance, which cements the divergence.
`codeBase ""` (f10) is U1 in the lint, but the real provider grants it to nothing. All of these err in the safe direction.

**F9 LOW VERIFIED. Test gaps: all three of my mutants survived (Q6).** For MX1 and MX2 the code itself is right (measured).

**F10 LOW VERIFIED (read). Skill and doc disagree, and the skill overstates `--audit-input`.**
SKILL.md:25 says no *unscoped* AllPermission; ProxyPolicyGenerator.md says "scoped or not". A codeBase-scoped AllPermission on the service's own jar also masks polpAudit for that jar.
The skill presents `--audit-input` as the check, but it misses F1 and F6, and AllPermission-free masking grants. Its "a missing file kills the JVM" claim matches `PolicyUtils.getPolicyURLs` :390-402 for a plain path. A `file:` URL form is UNDETERMINED.

**F11 LOW.** The ant fail message shows only the Unix separator `[:/dir]`. `--glob` goes through `<arg line=...>`, so a pattern with whitespace splits (read). The `policy.promote.classpath` filesets need `lib-ext.dir`/`river.lib.dir` to exist; the default wiring is unrun by both of us (UNDETERMINED).
**F12 LOW PLAUSIBLE.** The U2 text "UmbrellaGrantPermission implies every GrantPermission" misstates its javadoc: a GrantPermission "for all permissions in ps", the domain's own set.

## Q1 false negatives / Q2 false positives (real h/hp -> lint)
| Construct | Real | Lint | Verdict |
|---|---|---|---|
| keystore without `;` (f04, f23) | true/true | none, rc 0 in all modes | **FN, F1** |
| codeBase `${unset}` (f01, f02); lone `/` (f03); `signedBy ","` (f05) | true/true | S1, rc 0 | **FN by default, F2-F4** |
| `principal * *`, `X500Principal *` (f06, f17) | false/true | S2, passes `--strict` | **weak, F5** |
| `file:/-` (f07) | file: code true, http false | S1 | scoped-but-broad; S1 is fair |
| UPPERCASE keywords, comments between tokens, missing `;`, NULs, `signedBy ""` | true | U1 | agree |
| BOM, UTF-16, `'`, nested `{`, `keystorePasswordURL`, `codeBase ""`, `digest "SHA-256:"` | rejected / no match | U1 / P1 / S2 | conservative (F8) |
| comments containing `}` and `"`, AllPermission as a quoted target (f13) | false | clean | agree, no false positive |

The grammar has no include. A polpAudit run started with single `=` (qa/build.xml:408) also loads the JDK `java.policy` (narrow) and `~/.java.policy` (absent here), which the lint never sees.

## Q3 counts (copies of 61d597451 in SCR\corpus)
- Harness: 41 files (TOTAL files=41; the directory holds 58 files at this commit by `find | wc -l`, so the brief's "52" does not reproduce). U1=10, S1=165, rc=1.
- Deploy: 5 files, clean, rc=0. jinitest: U1=1, S1=10. jeri `test.policy`: U1=1. All match the producer.
- "139 of 175 jrt" = 129 harness + 10 jinitest, grouped by codeBase prefix.
- Read line by line, and agreeing: `defaultmahalo` 1-8; `all.policy` (U1 at 1 only); `jinitest` 22-79; jeri `test.policy` 12-14; `host1-lookup` 15-27 (comment only). Not agreeing: `defaultsecuretest.policy.old` 69-116 (F6).
- Counts are right; the producer's "No misclassification found" is not, because 36 S1 rows are unscoped under F2.

## Operator questions: recommendation
(a) **Do not skip `jrt:` silently.** Give exact `jrt:/<module>` its own counted, informational kind. The "asserted remotely" text is false for jrt, and polpAudit ignores platform modules anyway.
The real noise fix is the other direction: make property-dependent scope (F2) fail by default. It is the dangerous part of S1, buried under 129 jrt rows.
(b) **Wire it on the polpAudit starting policy with `--audit-input`, but only after F1-F5 are fixed.** A masked audit wastes the run, and its output masks the next one.
Expect the 10 harness U1 files to fail at once; you decide which `default*.policy` are starting policies.
Better: take the verdict from the production provider (F7a/b). That means jgdms-platform's DefaultPolicyParser plus `grant.implies(stranger CodeSource, {no principal, any principal})`, which cannot drift from the grammar as F1-F4 did.
(c) **Keep exit 2 for zero files.** A gate over an empty population is vacuous. Measured: empty directory rc=2; typo next to a good path rc=2; ant `Java returned: 2`. Also count skipped unreadable subdirectories: `walk.visitFileFailed` continues silently (read, not run).
(d) Report F2 to DirtyChai's humans. Merge the `.sh` gate's AuthPermission/RuntimePermission rules into PolicyLint, or retire one of the two.

## Q4 exit codes and CI (VERIFIED, on SCR\classes-pristine)
- One good plus one unterminated file: rc=1. Typo'd path: rc=2. `--strict` on S2: rc=0. `--audit-input` on S1: rc=1. File locked with FileShare.None: `P1 file cannot be read`, rc=1.
- Control characters in a codeBase print as `\u001B`/`\u000D`/`\u000A` (0 raw ESC bytes), so they cannot forge a TOTAL line.
- 51.6 MB file of 600,000 grants: U1, rc=1, about 2 s. 8 MB single line: U1. `-Xmx64m`: OutOfMemoryError, rc=1 (fails closed, but indistinguishable from "findings").
- **Can a build pass with an AllPermission policy present? Yes, measured through ant:** F1 even with strict+audit; F2, F3 and F4 by default; F5 under `--strict`.

## Q5 ant target and skill
Ran on the copy with a `;`-separated list including a directory with a space: BUILD FAILED on U1, as designed; `strict=true` failed S1; `format=tsv` worked. Remaining issues: F10, F11.

## Q6 mutants (mine, one at a time; source restored and `cmp` identical; `target/classes` re-diffed EMPTY; SCR\mutants.log)
- MX1 `PolicyLint.run`: missing path `return usageError(...)` -> `continue;`. 19/19 pass: SURVIVED. `usageErrorsExitTwo` uses a lone missing path, which still exits 2 via "no files".
- MX2 `PolicyLint.lint`: IOException adds no P1. 19/19 pass: SURVIVED. Nothing tests an unreadable file.
- MX3 `ProxyPolicyGenerator.policyTokenizer`: `ordinaryChar('/')` -> `commentChar('/')`. 114/114 pass: SURVIVED. Its classes report f03 as U1 and f22 as S1 (real: true/false), so this change fixes F3.
UNVERIFIED-NEEDS-BUILD: Linux; the default ant classpath; a real polpAudit run; JGDMS's own DefaultPolicyParser fail-open; F6 end-to-end.
