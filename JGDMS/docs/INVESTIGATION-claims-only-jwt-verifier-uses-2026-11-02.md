claude-sonnet-5-5
# Claims-only DefaultJwtVerifier constructors: who uses them (2026-11-02)

## Conclusion: YES-WITH-CONDITIONS
No production code, configuration, script or document-as-instruction constructs `new DefaultJwtVerifier()` or `(String,String)`. READ.
Only FOUR test call sites use them; all four test the claims-only behaviour itself, so they are deleted or rewritten with the constructors. Conditions:
1. Tests (READ): DefaultJwtVerifierClaimsTest:234; DefaultJwtVerifierNegativeTest:262 and :269; and JwtPrincipalBindingTest:349 (jgdms-jeri; this fourth site was not in the brief).
2. DefaultJwtVerifier.java: delete ctors at :120 and :134 and the claims-only wording at :54, :108-110, :116-125, :228. The private 5-arg ctor path with null algorithm/keys must go or reject null (INFERRED: internals not fully read).
3. Docs: update the stale records below.
4. Not in any release tag per the brief (not verified by me).

## Hits (DefaultJwtVerifier|JwtVerifier|setJwtVerifier|jwtVerifier), JGDMS/JGDMS tree, 216 matching lines
| Class | Where | Verdict |
|---|---|---|
| (a) claims-only call | ClaimsTest:234 `(ISS, AUD)`; NegativeTest:262 `(ISS, AUD)`; NegativeTest:269 `(ISS, "")`; jeri JwtPrincipalBindingTest:349 `setJwtVerifier(new DefaultJwtVerifier(ISS, AUD))` | READ |
| (b) signature ctor | VerifiedJwtApiTest:90,97,101; NegativeTest:59,109,248,276,281; ClaimsTest:55,154-187; JwtPrincipalBindingTest:81; JwtDispatcherFailClosedTest:191,227,347; JwtLoginModuleTest:588; REVIEW-lint doc:15,54 | READ |
| (d) definition | DefaultJwtVerifier.java:40,120,134,153,169,185 | READ |
| other main use | JwtValidator.java:98,214,292,306 use only static members (DEFAULT_MAX_PRINCIPALS, identityFromClaims, parseObject); no ctor | READ |
| dispatcher | BasicInvocationDispatcher:2514-2518 refuses a JWT when no verifier installed; no DEFAULT_JWT_VERIFIER in current source | READ |
| (e) config | JGDMS/qa/build/deps/qa1-mercury.deps:252,255 list `DefaultJwtVerifier.class`, `JwtVerifier.class` as jar content, not constructors | READ |
| (c) docs | docs/*.md, Big picture security architecture/*.md, one .svg, aaa - copilot latest.txt | mention only |

## Negatives (commands, population)
- Constructor/reflection by name, `DefaultJwtVerifier\s*\(|DefaultJwtVerifier\.class|jwt\.DefaultJwtVerifier` via Grep over C:\...\GitHub\JGDMS (excluding class/jar/zip/TMP): the hits above only; no `Class.forName`, no `.config/.groovy/.conf/.properties/.xml/.policy/.sh/.bat/.ps1` hit apart from the qa .deps file. Git tracks 13503 files in that repo, 1376 of them with those config/doc extensions (all covered by the grep; ignored/untracked binaries excluded).
- Per-directory `grep -rIlE "DefaultJwtVerifier|JwtVerifier|setJwtVerifier|jwtVerifier"` (100 s timeout each): 0 files in Cross-machine_Claude_Code_skills, DirtyChai, Observations_to_Salsa, Pack200-ex-openjdk, Pack200.net, Rio, Survey-zoot, authorize, googletest, graal, jfx, jtreg, jtreg-4.2-b16, jtreg-7.5.1, jxrlib, kwik, libE57Format, mx, openE57. READ (but see gaps).
- Outer JGDMS repo dirs deploy, docs, historical, state, "Claude outputs", KEYS, root *.md/*.txt/*.xml/*.sh/*.properties: 0 files; qa: 1 file (the .deps above).
- Old worktrees JGDMS-wt-compare (8 files), wt-b3 (21), wt-verify3: `new DefaultJwtVerifier()` at BasicInvocationDispatcher.java:267 / :268 (stale snapshots of the OLD default, not live code; also in target/classes copies). READ. Delete or ignore; they would not compile against a removal if rebuilt.
- Machine locations: Documents\NetBeansProjects (131 files), Documents\DirtyChai_tests (190826 files), "Problem with granting permissions", Reflect, Sequoia, the top-level Documents text files (JGDMS demo.txt, JGDMS recent fix attempts.txt, Dirty Chai and JGDMS process isolation.txt), C:\Users\peter\source: 0 hits. No folders named jini/river/reggie/phoenix/deploy/config found in Documents or C:\Users\peter.

## Test notes
- ClaimsTest:234 `claimsOnlyVerifierYieldsNoPrincipals`: purpose is a token nobody authenticated yields no identity; delete (the property no longer exists), or use a signed token with an unknown/absent kid and assert rejection, already covered by NegativeTest.
- NegativeTest:262 `claimsOnlyModeStillRequiresExp`: purpose is exp mandatory; rewrite with `rs256` and a properly signed token lacking exp, expect rejection (a "missing exp" case with a signed token is the real property; check one does not already exist).
- NegativeTest:269 `emptyAudienceConfigurationIsRefused`: change to `new DefaultJwtVerifier(ISS, "", "RS256", keys)`; ClaimsTest:159 already does exactly this, so it may simply be deleted as a duplicate.
- jeri JwtPrincipalBindingTest:349 `aClaimsOnlyVerifierGivesNoJwtPrincipalAtAll`: purpose is that a verifier conferring no principals leaves wire-asserted JwtPrincipals refused; rewrite using a lambda `raw -> new VerifiedJwt(...)` with empty principals (as `raw -> null` at :343 is used), or with the signing ctor and a token whose claims yield nothing is impossible (sub is required), so the lambda is the route. INFERRED: VerifiedJwt ctor visibility not checked.

## Stale documentation (still describe `new DefaultJwtVerifier()` as dispatcher default)
- docs/Big picture security architecture/AI_Agent_JGDMS-SecurityAssessment-context_11.md:28, 173-180 (`new DefaultJwtVerifier()`), 426
- .../AI_Agent_JGDMS-SecurityWeaknesses-ImplementationPlan-context_10.md:230, 242-246, 263, 806, 899, 915 ("claims-only verifier")
- docs/INVESTIGATION-jwt-fix-2026-11-01.md:40 and INVESTIGATION-jwt-principal-binding-2026-11-01.md:61 ask this very question (keep public or not); resolve and mark answered.
- DefaultJwtVerifier.java javadoc :54, :116-125 (own source).

## Could not search
Lines were located by Grep/grep text only; no search of E:\, of OneDrive folders, AppData, Downloads, or of other machines. grep per-directory used 100 s timeouts, so large trees (DirtyChai_tests, jfx, graal) may be incompletely scanned; the JGDMS repo itself was searched with Grep unbounded. Jars were not opened (a deployed jar referencing the class by name would not show).
