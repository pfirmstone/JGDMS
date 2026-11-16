# Fable review of memo section 15 and handover revisions 4n/4o

Reviewer: sub-agent a3d2585ccfcf24bcd, self-reported model `claude-fable-5-1` (producer of the reviewed text: Sonnet 5.5).
Date of the review: 2026-11-06. Read-only; no build was run; E:\ untouched.
Recorded here on 2026-11-07 from the agent's hand-back message (its output file was empty); the wording below is the agent's report, lightly reformatted. Status: LIVE. The corrections it caused are in the memo (sections 14 and 15, struck in place) and in handover revision 4p.

Skills the reviewer loaded: handover-composition, production-code.

## Findings

1. **Section 15, "The exposure window" and "What this makes necessary": the damage bound does not hold as written. CONFIRMED** (read sections 14 and 15, `docs/AI_Agent_SPIFFE-CI-context` lines 17 and 70).
   - "Short, fixed SVID lifetime as the real control ... the lifetime sets the worst case" is false for an undetected compromise. The existing `SpiffeCredentialManager` renews before expiry automatically, and the re-attestation evidence section 15 lists (`hsm_dev_attest`: SoC ID, lifecycle, firmware hashes) is boot-time, so a runtime-compromised Linux/JVM passes it. The lifetime bounds only the tail after the issuer refuses renewal.
   - "No revocation machinery is needed ... A board that has been shut down or flagged must fail re-attestation": the flag is issuer-side state, that is revocation machinery. The two sentences contradict. Correct: window = detection delay (O34, unbounded today) + renewal tail; the lifetime bounds nothing before detection.
   - "SVID lifetime shorter than the token's (Ruling 21(1), earlier rulings)" inverts section 14 lines 833 and 858 ("token lifetime shorter than the SVID lifetime"); Ruling 21(1) (line 882) orders nothing. Only the JWT bound is real: `DEFAULT_MAX_LIFETIME = Duration.ofHours(1)` (`DefaultJwtVerifier.java:83`). The SVID lifetime is unruled.
   - "with a certificate-bound token the question goes away" contradicts section 14 line 877 ("a certificate-bound token does not help because the attacker holds the bound key"). Binding helps only after power-off and only if the key never left the board; today the SVID private key is an `X500PrivateCredential` in the JVM Subject, not in ELE, and RFC 8705 is unbuilt (CONFIRMED: grep `8705|x5t#S256|"cnf"` over `src/main/**/*.java`, only serialVersionUID digit hits).
   - Omitted between compromise and power-off: under the stated purpose the adversary is the partner, so P to S is a legitimate channel and exfiltrating U's JWT or the key needs no covert channel; the attack on C through the response path (DER/AtomicSerial decoded in the trusted JVM, the surface this programme exists for) and lateral movement via the chassis switch are not in the window; C keeps handing fresh JWTs to P until detection, so JWT exposure is detection delay + 1 h after the last hand-off, not "the token the board holds".

2. **Section 15 "Shut down the SOM": "the memo and the board requirements do not yet say where this authority lives" is a false negative, no grep. CONFIRMED.** DirtyChai `PROXY_HOST_BOARD_REQUIREMENTS.md` sections 3.2 to 3.4 (P2 "driven only by the management controller", M1, M4 "only the trusted launcher may request a power cycle", W1 to W3 external watchdog fed by a trusted lease signal) were committed in `2ee51eddd60`, 2026-10-31 16:06, two days before section 15. O32 is largely answered by the operator's own document; O34 has a kill path (W2) if not a compromise detector.

3. **Handover 4n section 1, "Second-model review status: Opus 5.5 reviewed everything up to and including ab94352a6 ...": overstated. CONFIRMED.** Rounds 5/6 (`4055eec11`, `03b317ef6`: shared PermissionSets, `--allow-held`) appear in no `docs/REVIEW-*.md` except the Sonnet handover-currency review. After 4o, `fabc79650`, `84e394eff`, `6e99c5841`, `d95503fe7`, `0eb9f0cc7` are unreviewed; the last two cite "Fable review 2026-11-03" and no such file exists in `docs/` (ls). Correct: name the reviewed commits per report; list these as unreviewed.

4. **Section 14 "Open and not yet decided: rebuilding PolicyLint on the production policy provider": stale when written. CONFIRMED.** `c96183b6d` (2026-11-01 02:34) "take the AllPermission verdict from the production policy provider"; `PolicyVerdict.java:211`, `PolicyLint.java:85` (U3). 4n section 3 rightly omits it; section 14 should strike it.

5. **4n section 4 "threads must be created privileged ... (acceptance MET)" versus 4o F8 "polpAudit assertion checks one foreign domain; mutant not run". CONFIRMED.** A verdict whose gate's rejection rate is unmeasured. Correct: "MET by a test asserting no grant to jgdms-collections; power unmeasured (F8)".

6. **4n section 1 "16 known unrelated platform failures: DigestDomainGrantTest, GrantSignerTest". CONFIRMED valence.** Digest grants are the mechanism rulings 7/8/12 and D20 rest on; last measured 2026-10-31 under `-Pdirtychai`, never on the fresh JDK; "NOT run by anyone" carries no source. Correct: "pre-existing, in the digest-grant mechanism (O9), not re-measured".

7. **Staleness against git log. CONFIRMED.** HEAD `0eb9f0cc7`; `origin/trunk` = trunk (4o "unpushed" now false). Two substantive commits after 4o (JWT module-state reset; long-path-prefix rating rule) are in no block; 4o's "added rulings" line lacks the new rule; JwtLoginModuleTest 58 to 60; `d95503fe7` says "No new test".

8. **JDK identity (PLAUSIBLE).** The image is named by path and mtime 22:52; the DirtyChai commit it was built from is unrecorded. `4c71278f1c5` (#236) is 21:18 the same day, so inclusion is likely, not shown.

Not findings: the C to P "NO code" claim carries its searches (INVESTIGATION lines 10, 15, 28); 4o honestly flags the fixes as unreviewed. UNVERIFIED by the reviewer: 25 worktrees (`git worktree list` not run).
