claude-sonnet-5-5
# Stale DefaultJwtVerifier doc claims struck in place (2026-11-02, commit 7cd3f0a24)
Working tree only, nothing committed, no .java touched. Style: ~~old~~ then "Corrected 2026-11-02 (commit 7cd3f0a24)". Refusal cited: BasicInvocationDispatcher.verifyJwtWithCache(), "no JwtVerifier is installed; refusing".

## Edited (current line numbers, see git diff)
- Big picture security architecture/AI_Agent_JGDMS-SecurityAssessment-context_11.md: v4 changelog (28), Fix (v4) paragraph (173-180), action table row 7 (426).
- Big picture security architecture/AI_Agent_JGDMS-SecurityWeaknesses-ImplementationPlan-context_10.md: v58 item 2 (242-246), file list (263), table cell (806), zero-network bullets (897-900), NEW-file row (915), summary row (1089).
- INVESTIGATION-jwt-fix-2026-11-01.md:40 and INVESTIGATION-jwt-principal-binding-2026-11-01.md:61: marked ANSWERED: removed (operator decision 2026-11-02, no uses outside four tests). Line 61 also notes the 24 h maximum is superseded by 1 h (note appended; the "24 h" wording is not itself struck).
CRLF preserved in the two AI_Agent files.

## Left alone
- Same-dated historical records of the state at the time (true when written): INVESTIGATION-jwt-fix-2026-11-01.md:9,10,35,40(other text), SURVEY-lazy-statics-untrusted-input-2026-10-31.md:20 (cites DEFAULT_JWT_VERIFIER fallback; dated survey, no edit), HANDOVER-2026-11-01 lines 26,31 etc., REVIEW-*-opus-2026-11-01.md, INVESTIGATION-jwt-binding-followup/client-align/lifecycle.
- INVESTIGATION-jwt-principal-binding-2026-11-01.md:45 ("M12 claims-only derives principals"): a mutation-test label, not a live claim.
- examples/wire-protocol-showcase README files: "claims only what it shows" unrelated.
- README.md:28 (24 hours = regression test runtime); spiffe-admin-deployment.md:474 (ca_ttl 24h); JGDMS-ARCH-004...Server-Architecture-Specification.md:224 (key rotation every 24 hours): unrelated to token lifetime.
- No document states a 24-hour maximum token lifetime except the question at principal-binding:61.
- .claude/worktrees copies not touched.

## Unsure
- AI_Agent_JGDMS-GrantPermission-RoleManagement-context_8.md:60 ("a free DefaultJwtVerifier" in Option D): not read in full, may describe a free claims-only verifier; not edited.
- AI_Agent_JGDMS-SecurityAssessment-context_11.md:439 (WI44 row mentions DefaultJwtVerifier, "Complete"): left, still a true completion record.
