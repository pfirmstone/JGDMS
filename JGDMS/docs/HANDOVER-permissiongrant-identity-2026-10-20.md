# Handover: PermissionGrant mirror, DigestDomainGrant, verdict binding, JERI authenticateServer

**AS-OF 2026-10-20, branch `claude/sow-permissiongrant-domain-grants`, trunk at `25b5b013` (source: `git log -1`).**
Everything after that commit is not reflected here: run `git log --oneline 25b5b013..HEAD` first.
Written by the same session that did the work, AFTER an auto-compact had already run (the compaction summary, not the live understanding, was the source for sections 2-5). Re-composed against `handover-composition` (pfirmstone/Cross-machine_Claude_Code_skills) once that skill was cloned; sections 7-10 were added then. Read as: consolidation by the author, who trusts it more than a stranger's document and has the same error rate; nothing in it has been reviewed by a different model.

## 1. Where the truth lives

| Question | File |
|---|---|
| Scope, steps, decisions (sections 6-8) | `JGDMS/docs/SOW-PermissionGrant-Mirror-and-ProtectionDomain-Grants.md` |
| Investigation, review verdicts, decisions taken and open | `JGDMS/docs/DESIGN-dynamic-grant-solutions-2026-10-18.md` |
| JERI authenticated-server accessor design | `JGDMS/docs/DESIGN-jeri-authenticated-server-accessor.md` |
| What DirtyChai must change (humans write it; DirtyChai forbids AI contributions) | `JGDMS/docs/DirtyChai-Required-Changes-PermissionGrant-SOW.md` |
| Review heuristics | `.claude/skills/jgdms-board-review/SKILL.md` |

## 2. State, with provenance

- Done and merged to trunk: `DigestDomainGrant` (context DIGEST_DOMAIN=7, inlined constant, must match DirtyChai), `ClassLoaderGrant`, `CodeSourceKey` digest helpers, builder refusals, signer/URI/digest grant hardening; verdict binding in `PreferredProxyCodebaseProvider` (hash URN compared, freshness, `VERDICT_MAX_AGE_MS_PROPERTY`); `BasicInvocationHandler.authenticateServer(Method)` (exact-class checks, `ContextPermission` gate, one ServerSubject, immutable principals); Kerberos `ServerSubject` plus mutual-auth check.
- Tests I ran earlier (javac + JUnit harness, not Maven): verdict test 92, server-identity, relay-stream, `AuthenticateServerTest` 8 passed WITH SpiffePrincipal compiled in. The harness scripts were in the session scratchpad and are gone; this session did NOT re-run anything after the trunk pull.
- `AuthenticateServerTest` failure on Maven: `SpiffePrincipal` is in jgdms-lib-dl, which depends on jgdms-jeri, so it is absent from jeri's test classpath and the SSL ServerSubject holds only the X500 principal. Three tests failed for that reason. Fix committed with this handover (`spiffeAvailable()`, `Assume`, conditional assertions). UNVERIFIED under Maven: run `mvn -o -pl jgdms-jeri test -Dtest=AuthenticateServerTest`.
- Weakness of that fix: with SPIFFE absent, the SPIFFE-pinning behaviour is not tested in jgdms-jeri at all. A better home for those tests is jgdms-lib-dl (or a module that has both).

## 3. Decisions taken

- Identity comes from the bootstrap proxy's own authenticated call (`authenticateServer`), never the delivering stream (relay case is real on Atomic JOSS). Pin must be a single principal (`ServerMinPrincipal` accepts only one).
- Remotely reconstructed domains (RemoteContextCodec) are never "verified"; `DigestGrant` does not imply them, `DigestDomainGrant` may.
- Process isolation is the only true class isolation.
- Fable seat cannot be the security reviewer; board is one Fable, one Sonnet, one Opus.

## 4. Open decisions and my recommendations

1. Steady-state identity gate beyond the verdict: recommend NO. `BootstrapPermission` stays boot-window only (lookup services/registries); the signed hash-URN verdict plus pinned identity at resolve covers steady state, and reviewers withdrew the steady-state move.
2. `checkBootstrapPermissionByDigest` (non-TLS digest boot check): recommend KEEP until a replacement exists. The new identity rule would silently remove it for non-TLS transports. Dropping it must be an explicit decision with a negative test, not a side effect.

## 5. Next steps

1. Run the Maven test above; fix if red.
2. In `PreferredProxyCodebaseProvider.resolve`: check handler class, call `authenticateServer` with the `CodebaseAccessor.getClassAnnotation` Method, require exactly one SpiffePrincipal or the single KerberosPrincipal, pin via `ServerMinPrincipal` + `ServerAuthentication.YES` before any further call, delete the stream-context `ServerSubject` read. A failed probe takes an explicit refuse/unverified branch. Negative tests required.
3. Then: loader expected-hash map, directory/Class-Path gating, provider-private registry with read-only SPI + `VerifiedCodebaseProxyPreparer`, remote-attested tier.
4. DirtyChai (human-authored): `issued(pd)` first, narrowed R1, write-once `readExternal`, permission-gated verified factory, principal-merge gate.

## 6. Build recipe

Maven does not build here (Pack200 dependency). Use `javac` with a cross-module `-sourcepath`; add `--enable-preview --release 21` for jgdms-der and jgdms-jeri (ScopedValue); compile `MarshalDelegateProcessor` first and run it as the annotation processor or JOSS fails with `serialForm() not reachable`; digest tests need a stub `java.security.DigestCodeSource` via `--patch-module java.base=...`. `Subject.callAs` wraps exceptions in `CompletionException`: unwrap. Do not put `rm -rf` on variable paths in shell loops (safety check blocks it); use fresh output directory names.

## 7. Provenance: RELAYED vs VERIFIED

- VERIFIED this session after the compact (commands run): working tree was clean at `25b5b013` after the trunk pull, so my earlier uncommitted `AuthenticateServerTest` edit was NOT on disk (`git status --short` empty; `grep spiffeAvailable` found nothing) and I re-applied it; test file diff shown by `git diff` before commit; push of `f7e8c832` (the `git push -q` printed nothing, `git log --oneline -1` shows the commit; remote head not re-read).
- RELAYED from the compaction summary, not re-checked against code or logs: the test counts (verdict test 92, 8 AuthenticateServerTest passing with SpiffePrincipal), the root cause of the Maven failure (reproduced in the earlier javac harness: 3 failures without SpiffePrincipal, 8 passes with it), the design decisions in section 3, and the file list.
- NOT VERIFIED at all: the fixed test under Maven; that the Maven `jgdms-jeri` classpath really lacks `SpiffePrincipal` (inferred from the module dependency direction: `grep` shows the class in `jgdms-lib-dl/src/main`, which depends on jgdms-jeri).
- Self-error count: NOT cross-checked against the log, so treat it as an under-count. Known errors this session: a Python replacement that damaged `DynamicPolicyProvider` (restored with git checkout); stash-based verification that ran the new classes; "verdict not bound to hash" (only half right); handover written after compact instead of before.

## 8. Fastest-ageing section

Section 2 (state) and section 5 (next steps) age fastest: trunk is edited by the user between my turns (it moved under me at least once, and a pulled trunk silently removed an uncommitted edit). Re-read `git log --oneline 25b5b013..HEAD` and `git status -sb` before acting on them.

## 9. Triggers (learnings, in the form they will be met)

- BEFORE ASSERTING WHAT A MECHANISM DOES, OPEN ITS IMPLEMENTATION (the "verdict not bound to the hash" claim was wrong because I had not read the comparison site).
- BEFORE CALLING A TEST FAILURE A CODE BUG, CHECK WHICH MODULE'S CLASSPATH IT RUNS ON AND THE MODULE DEPENDENCY DIRECTION (the Spiffe failures were classpath, not logic).
- BEFORE TRUSTING AN UNCOMMITTED EDIT ACROSS A PULL OR A COMPACT, RUN `git status` AND GREP FOR IT.
- BEFORE CONTEXT PASSES ~60%, WRITE THE HANDOVER; auto-compact ran before this one was written.
- BEFORE A SHELL LOOP, NEVER PUT `rm -rf` ON A VARIABLE PATH; use a fresh output directory name (the harness blocks it).
- BEFORE TOUCHING DIRTYCHAI, REREAD ITS `CLAUDE.md`: no AI-generated contributions, no Co-Authored-By trailers, no auto PRs there; write requirements documents instead.

## 10. Index of on-disk recordings and agent roster

Everything is in this repository under `JGDMS/docs/` (see section 1 for which file answers which question); the transcript is `/root/.claude/projects/-home-user/9c79f79c-26fe-586e-87d9-88adfa9eead4.jsonl`. The earlier javac/JUnit scratch scripts were in the session scratchpad and no longer exist. No separate index file was produced (no scribe run); this section is the pointer list. The DER handover precedent is the only other handover in `docs/`.

Sub-agents (IDs recovered from the transcript by `Agent` tool_use/tool_result pairing; model is the REQUESTED model, self-report not verified; `ListAgents` showed none reachable after compaction, so they are likely not resumable and their reports are in the transcript, not here):

| ID | requested model | role |
|---|---|---|
| a793f43d1f9eeb42d | fable | Board review: Fable seat (SOW) |
| a09b61ffeafad7014 | opus | Board review: Opus seat (SOW, security) |
| ac8158dc41cbf30c6 | sonnet | Board review: Sonnet seat (SOW) |
| a85e3f413f91acef1 | fable | Board design review: Fable seat |
| ae31fb28a8b263305 | opus | Board design review: Opus seat |
| a39cabcacf386d836 | sonnet | Board design review: Sonnet seat |
| a697c39b1ce23d20f | fable | Investigation: solutions to reviewers' points |
| a32b9ecb094a050bd | opus | Review of that investigation |
| af15271b5245ea4f0 | sonnet | Review of that investigation |
| a3b13b73d520c21c4 | opus | Board: security review of the JERI accessor |
| afa3244636184a2ab | sonnet | Board: API/transports review |
| a115b1a276e2aeacf | fable | Board: implementation correctness |

Not consulted for this handover (they could not be reached); their conclusions are only in `DESIGN-dynamic-grant-solutions-2026-10-18.md` and the SOW. Do-not-redo: none recorded, because the decisions in section 3 are rulings (user) and are unblocked by the user changing them.
