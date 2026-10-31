# Handover: the code-free proxy window (2026-10-31 to 2026-11-01)

AS-OF 2026-11-01. Written because the operator said "your memory is your handovers" and the scratchpad
HANDOVER_2026-10-31.md was not updated during this window. Everything below is on disk; nothing here is only in a context window.
Source of truth for the design: `docs/DESIGN-class-free-lookup-and-registrar-split-2026-10-31.md` (the memo): operator rulings 1-19,
candidates D15-D16, sections 11-13. Method for any per-service investigation: `docs/TRAINING-code-free-proxy-investigation-2026-10-31.md`.

## State of the trees
- JGDMS `trunk` at `8100b400d` (not pushed). Uncommitted, in progress by sub-agent `a49de6db668b8bcb7`: `PolicyLint.java`,
  `PolicyLintTest.java`, a `policy-lint` target in `qa/build.xml`, an edit to `ProxyPolicyGenerator.java`, and a correction to
  `.claude/skills/jgdms-polp-audit-policy-gen/SKILL.md` (that directory is git-ignored; do not expect it in a commit).
- DirtyChai `trunk` at `91737c79880` (operator's commits; clean tree). The eMudhra fix is in: `bc6a1dad8a2` (cherry-pick of upstream
  `8e492bf50b8`, JDK-8395690) and the operator's `VerifyCACerts.java` follow-up (COUNT 105, checksum
  `57:BD:18:38:9D:AF:25:4B:B1:C0:56:A8:00:55:92:BE:17:03:3F:DB:F7:47:CC:C4:2E:D7:07:57:23:BF:A5:84`, which matches what the author computed
  by regenerating `cacerts` with `make/jdk/src/classes/build/tools/generatecacerts/GenerateCacerts.java`; control: the old data set reproduced the old checksum).
- Rules: DirtyChai code and commit messages are human-authored (OpenJDK policy); JGDMS code may be written by the agent, with the
  `Co-Authored-By` trailer from the session reminder; never that trailer in DirtyChai. Project memory: `C:\Users\peter\.claude\projects\C--Users-peter-Documents-GitHub-DirtyChai\memory\`.

## JGDMS commits this window (oldest first)
`62670b2ad` required MarshallingFormat enforced on nested MarshalledInstance (Fix 1+2); `68acb611e` memo; `b17f72c3f` default exporter is
AtomicDerILFactory in all services; `af64aea0c` lazy decodes refused at decode (GetArg.requiredPayloadFormat); `bb2e10720` top-level block bound
(AtomicMarshalInputStream, ObjOutputStream.MAX_BLOCK_DATA_LEN; fixes 11 jeri tests that fail only under a SecurityManager);
`5fd08a183` platform-domain comments, DER large-block test; `ae004e42f` policy generator output checks + two-step PERMISSIONS.LIST promotion
(95 tests); `2a223d76b` docs; `8100b400d` operator decisions on round 4.

## Open items, in the order to take them
1. **Other computer (operator, 2026-11-02):** finish and commit the QA test fixes, then run the full QA suite to regenerate policy files. Before that:
   the starting policies must pass the lint with `--audit-input` (the per-service `default*.policy`, `all.policy`, `policy.all` in `qa/harness/policy`
   carry an unscoped `grant { AllPermission }`, which masks a polpAudit run). polpAudit needs `-Djava.security.manager=polpAudit` and
   `-Djava.security.policy=<an EXISTING, even empty, file>`. Targets: `ant policy-update-run-all` (full), `-run-categories`, `-run-tests`; then
   `policy-promote-plan`, `-check`, `-apply` (the plan is read by a human; apply re-derives everything). 93 tracked `qa/*.txt` result files will be rewritten by a run.
2. Sub-agents still running (single-shot, resumable only by id): `a433e41b8fb526b91` Opus check of round 4 (report
   `docs/REVIEW-proxy-policy-tooling-round4-opus-2026-10-31.md`); `a49de6db668b8bcb7` policy lint (report `docs/INVESTIGATION-policy-lint-2026-11-01.md`; then an Opus review).
3. **Operator-only:** remove the four eMudhra roots from the installed JDK (`keytool -delete -cacerts -alias "<name> [jdk]"` for `emsignrootcag1`,
   `emsignrootcag2`, `emsigneccrootcag3`, `comodoaaaca`; PowerShell needs `&` before the quoted path), until a rebuilt image replaces it. Source: `https://openjdk.org/emudhra`.
4. **JWT fixes awaiting the operator's go-ahead:** survey candidates 1 and 2 in `docs/SURVEY-lazy-statics-untrusted-input-2026-10-31.md`:
   the static JWT verification cache in `BasicInvocationDispatcher` survives `setJwtVerifier` (needs clear-on-set, plus a test; whether it is live depends on whether any
   production JVM accepts wire calls before the verifier is installed), and `AtomicSerial.GetArg`'s `StackWalker` created at class init without `doPrivileged`. Then JWT negative tests.
5. Per-service code-free work (reports in `docs/INVESTIGATION-code-free-<service>-2026-10-31.md`, reviews `REVIEW-code-free-service-reports-{opus,fable}-...`):
   foundation items still to do: BasicJeriTrustVerifier accepts the DER handler; handler runs default methods locally (D16); preparers use the digest-scoped
   `Security.grant(Class, CodeSource[], ...)` instead of the whole-loader grant; default permission class and refuse-to-start unless DER; a real-grants run of the jeri suite
   (the `dirtychai` profile's `test.policy` is an unscoped AllPermission, so it tests no grants). Then per service: Mahalo, Mercury, Norm, Fiddler, Outrigger.
6. Derek (the DER-only registrar, Reggie 2.0): design only; `RemoteRegistrar` is in no Java file. Open: O19 (where interface and records live), O21, O22 (package namespace).
7. The "code to the data" memo section (CEL expressions vs real code on isolated boards) was offered and not written. The Wired Binance article could not be opened (built-in browser refuses wired.com).
8. DirtyChai analysis the operator may want: `initPhase3` pre-initialises singletons (commit `344ba66bca1`); new upstream lazy singletons in a future merge need a check; the `java.base`
   domain is privileged only while alone on the stack by design; an option to make the policy parser ignore AllPermission (discussed: warn loudly, stage it, check JGDMS's own policy providers).

## Facts established this window that are easy to lose
- `-Djava.security.manager=default` installs `CombinerSecurityManager` (DirtyChai `System.java:2505-2506`); `polpAudit` installs `SecurityPolicyWriter`; `legacy` the plain manager.
- The sender names a MarshalledInstance's payload format; the receiver's required format must be enforced at decode (done for DER calls and DER dispatchers; clients that ask for no format are unprotected: the wrapper must set `ATOMIC_DER`).
- The three-term meet: polpAudit floor, `GrantPermission` ceiling, `PERMISSIONS.LIST` cap; an unscoped AllPermission grant defeats the audit. `PreferredClassLoader` reads the jar's own list in its constructor, before authentication.
- Reference for "a figure was verified": ask for the command and the output line; the sub-agent reports in `docs/` label claims VERIFIED / PLAUSIBLE / UNDETERMINED.

## Sub-agent roster (ids, so they are reachable; none is enumerable by any tool)
Investigators (Sonnet 5.5): outrigger `a7bd9cb21592a4827`, mahalo `a33e5cc768f53f6d9`, mercury `ad3aa29786e0d9671`, norm `ab47d80c75a67b378`, fiddler `abfbd04ba735ad321`;
reviews: Opus `a6d0a66821c6f4f7b`, Fable `a6da22ba2be494470`. Tooling producers: build-step `a6e3af9d64c4f0d95`, generator round 1 `a6df5545ede6024d1`, rounds 2-4
`ae5956fd2a31fd1aa`, `a391a8bba6b407f53`, `ac4201998e377c016`; their reviewers: Opus `a65aa2ff2d076e676`, `aab3be0a693366fcb`, `aff2c7d837307cf50`; Fable `abee59006cb59436f`.
SecurityManager test run `a23d034b288bfe845` (Sonnet), root cause `a5f358411ff212c41` (Opus), block-bound review `a2ba9b3811f72ec86` (Fable). Surveys: lazy statics
`a36b0e642b4fea98f` (Opus), news `a71e100c2a3cca1ba` and CA verification `ad0c829704da3dfe7` (Sonnet).
