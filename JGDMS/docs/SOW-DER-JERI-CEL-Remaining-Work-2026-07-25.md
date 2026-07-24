# SOW — DER / JERI / CEL Remaining Work + Outstanding Decisions (session handoff)

- **Drafted:** 2026-07-25, end of a long multi-thread session, for continuation in a fresh session.
- **Trunk at handoff:** `884a2c9a2` (local; **verify push state to origin** — session commits were local-only, `git rev-list origin/trunk..trunk` reported 0 which is suspicious given the volume — confirm before assuming pushed).
- **Purpose:** capture everything NOT finished, every decision still owed by Peter, and the scoped-but-unbuilt work, so a fresh session resumes without re-deriving state. Companion memory: `jgdms-outrigger-der-only-refactor` (the live ledger — READ FIRST), `jgdms-std011-deterministic-cel-status`, `jgdms-jeri-spec-modernization`.

---

## 0. What LANDED this session (context — do not redo)

Merged + reviewed on trunk: DER stream schema dedup (T1 spec v0.3, T2 Java impl, mandatory/canonical/CRIME-safe — board-reviewed **does not weaken encryption**); the reconstitution decompression-bomb fix (per-item 128 MiB + per-window 1 GiB ceilings, both RATIFIED + tunable via validated system properties, all security-reviewed); T6 base chain hardening (chain ceilings + completeness + P2 Merkle cross-check); the DER throwable-carrier fault fix (`DerThrowableForm`, dispatcher seam — closed the live "faults abort over DER" hole); Outrigger DER-only U1a/U1b/U1c; the JERI standard scaffold (SOW-JERI-Standard-Modernization + STD-012 skeleton + ARCH-005 umbrella); ASN.1 modules (STD-006 + STD-011 Appendix B, asn1tools-validated); the DirtyChai-SecurityManager correction across 3 docs + new `dirtychai-securitymanager` skill; item-21 throwable ceilings; and a 5-demo no-acronym wire-protocol showcase (`examples/wire-protocol-showcase`).

---

## 1. IN-FLIGHT / INCOMPLETE (re-dispatch — highest priority)

### 1.1 Demos 2 & 4 self-building fix — NOT DONE (agent stopped, committed nothing)
**Branch `fix/demos-self-building` was deleted (it had zero commits — the agent `a091e35` stopped, likely at the model-switch/process-exit boundary, before committing).** The problem is REAL and unfixed: demos resolve JGDMS jars from `~/.m2`, which can be STALE (demo 4's cumulative-bomb refusal was undemonstrable because the installed `jgdms-der` predated the fix) or absent on a fresh checkout. **Re-dispatch:** make demos 2 & 4 (and ideally 1, 3, 5) build the JGDMS modules they need from the current source tree at run time — `mvn -pl services/outrigger/outrigger-dl -am -Dmaven.test.skip=true install` covers both (`-am` builds jgdms-der/platform/lib-dl current). **Verify by simulating a stale `~/.m2`** (install an old `jgdms-der`, run, confirm self-correction, restore `~/.m2`). Build on the demo2 clean-build fix already on trunk (cygpath path handling; quoted `-D` args). Demo 5 already self-builds — use it as the pattern.

---

## 2. OUTSTANDING DECISIONS (Peter owes a call)

1. **Demo 4 split-package bridge classes** — demo 4 sections 3-4 place showcase classes INSIDE jgdms-der's own packages (`au.net.zeus.jgdms.der.stream`/`.object`) to reach package-private internals (the depth fence + metered-bomb reproduction genuinely need them). Works on the classpath; **breaks under a JPMS module-path**, and a viewer might copy the pattern. Decide before sharing with Paul Hammant: accept as-is (documented), or add a small test-scoped internal-access API in jgdms-der so the demo uses a clean seam.
2. **Cosmetic (non-blocking):** `StreamSchemaDedup.meterReconstitution` reject-message strings interpolate the STATIC default factors, not the instance factors — only divergent via the test seam (production passes the static fields). One-line tidy if wanted.
3. **CEL text-parser + wire-encoder scope (see §3.1)** — decide priority/whether to build now.
4. **Push to origin** — nothing was pushed this session (verify). Decide when to push the ~30+ merges.

---

## 3. SCOPED-BUT-NOT-BUILT WORK

### 3.1 CEL text parser + wire encoder [NEW — from demo 5 finding, Peter asked to scope]
**Confirmed by the demo-5 build (branch merged 884a2c9a2):** `jgdms-cel` has **NO text parser** (cannot author a rule from a string) and **NO wire encoder** (cannot serialize an `ExprNode` AST to the canonical wire form) — it ships only the wire **decoder** (`CelDecoder`) + a programmatic sealed `ExprNode` AST (27 node kinds) + the evaluator + verifier. STD-011 §5.1 explicitly makes the text form the authoring/display syntax and says evaluators need not include a parser — but for authoring, tooling, round-tripping, and the conformance corpus, both are needed. **Scope two units:**
- **T-CEL-A · Text parser** — parse STD-011 §5's textual grammar → `ExprNode` AST, rejecting exactly the §5.4 exclusions and enforcing §5.3.1 well-formedness. Must round-trip with the wire form. Governed by STD-011 (the sole normative source). Cross-check: the textual form is a strict CEL-syntax subset (§5.1).
- **T-CEL-B · Wire encoder** — `ExprNode` → canonical DER wire form per STD-011 Appendix B (the exact tags/registry the decoder already consumes). **Canonicality (G1):** one AST → one encoding; re-decode must reproduce the AST; encoding a decoded stream reproduces bytes. This is the encoder half of the T2/decoder that already exists.
- Both enable: authoring rules as text (better demos, tooling), generating the T5 conformance corpus from text, and the Outrigger filter-pushdown client (§3.4) sending a rule.
- **[OPEN]** decide whether the parser is a jgdms-cel main artifact or a tooling/test-scope module (STD-011 says evaluators needn't include it — so likely a separate authoring module, keeping the evaluator minimal/polyglot).

### 3.2 Rust JERI DER → then CEL Rust evaluator (STD-011 T4) [gated]
The Rust `DETERMINISTIC CEL` evaluator (T4) is gated on a **Rust JERI DER** implementation, which is gated on the **JERI standard** (§3.3) being written to G9 (implementable-from-doc-alone). Correctly-rounded transcendentals via CORE-MATH (shared C with a future Haskell peer). See `jgdms-std011-deterministic-cel-status`.

### 3.3 JERI standard — full normative prose (STD-012) [scaffold done, prose not]
`SOW-JERI-Standard-Modernization.md` (T1-T10) + the STD-012 skeleton are on trunk. Remaining: write the full normative prose per the skeleton — mux (reconcile against the existing `mux.html` v1.1 + the named deviations: client-negotiation unimplemented, Ping-initiation NYI, the `Mux.java:67` comment error), invocation layer (the ratified method-identifier map: **0x00 reject / 0x01 SHA-1 sunset-JGDMS-5.0 / 0x02 DER SHA-256, length-delimited SHA-512-capable, 128-bit default**), fault marshalling, constraint model, endpoint families, DGC (DER-native per STD-008 §6; residual legacy `readObject` path = removal work). G9 acceptance = a Rust peer implements from the doc alone. This is the prerequisite for §3.2. Deferred to **wk 2026-07-27** (`jgdms-jeri-spec-modernization`).

### 3.4 Outrigger CEL filter pushdown (Part B) + matching modernization (Part C)
`SOW-Entry-ATOMIC-DER-Migration.md` Parts B & C — route a CEL rule to Outrigger, evaluate class-free over schema-projected fields at the match chokepoints (B1 API + wire, B2 lazy projector, B3 chokepoint wiring fail-closed + the transactional-visibility confused-deputy guard, B4 write-path cost, B5 tests); Part C = typed projections, ordered indexes, journal modernization, the `ConcurrentSkipListMap` entry-store swap (C4a). Part B needs §3.1's wire encoder (to carry a rule). Gated on CEL T1-T3 (done) + these.

### 3.5 Schema read-ahead admission filter (from the merged investigation memo)
`docs/investigation-atomicserial-schema-readahead-filtering.md` (committed 377743e10) recommends **proceed — first-class the thin Tier-A `DerDeserializationFilter` SPI**: a per-object, schema-digest/class-name allowlist on the DER decode path (`ObjectCodec.decodeHierarchy`, after schema verification, before construction), fail-closed, default accept-all. The mechanism already exists (`admissibleConstructClass` + `decodeToFieldMap`); the gap is the deployment-configurable policy seam. It COMPOSES with CEL (Tier D delegates to a predicate), does NOT duplicate it. Framed as the polyglot-portable admission layer (Java/no-SM peers) AND DirtyChai defense-in-depth (digest-bound, pre-class-load — what the ProtectionDomain-keyed `DeSerializationPermission` gate structurally can't do). **Decision:** green-light Tier A?

### 3.6 Smaller committed-scope follow-ons
- **QA harness policy regeneration** — U1c's config sweep merged but the full harness never validated end-to-end: `defaultsecuretest.policy` is committed EMPTY (blocks every child VM at classload → uniform `StreamCorruptedException` that LOOKS like a serialization regression but is the empty-policy). Regenerate via polpAudit (`jgdms-polp-audit-policy-gen` skill) to actually exercise DER-only through the harness. LANDMINE — record as such.
- **Reggie A3** — born-DER Reggie (parallel to Outrigger A1/A2); not started; `SOW-Entry-ATOMIC-DER-Migration.md` A3.
- **EntryRep-v2** — whole-entry DER record (one schema chain per entry, per-field TLV slices) — kills the per-field-capture schema duplication U1c measured (~4.3% dedup on the Outrigger layout because per-field MIs are identity-bearing). Deferral is safe (later migration is class-free DER→DER verbatim transcription). §9.6 of the migration SOW.
- **Platform:** fail-closed `MarshalledInstance.get()` / `getPayloadFormat()` retrofit (~18 files, known HIGH from `SOW-RemoteEvent-Source-DER-Encoding.md`) — the platform backstop against JOSS-payload smuggling; `getPayloadFormat()` half already landed (U1a). And `RemoteEvent`'s deprecated MO constructor silently drops its handback on the DER wire — candidate for the same UOE treatment.
- **Dedup SOW T3 (full adversarial conformance corpus) + T4 (three-way wire measurement, part-satisfied by U1c's probe) + T5 (Rust conformance corpus)** — `SOW-DER-Stream-Schema-Dedup.md`.
- **STD-006 chain-ceiling [PROPOSED] markers** — T6 landed the ceilings; confirm any remaining `[PROPOSED]` on `maxChainRecords`/`maxChainBytes` in §4.5 is struck (mostly done).

---

## 4. KEY LANDMINES (carry forward — see memories)
- **DirtyChai ENFORCES the SecurityManager** (CombinerSecurityManager via `-Djava.security.manager=default`) — NOT removed like stock JDK 24+/JEP 486. Never say a permission gate is "inert under DirtyChai." (Skill: `dirtychai-securitymanager`.)
- **Model switch (`/model`) kills in-flight background agents** — reactivating Fable 5 mid-session terminated the batch; the harness labels it "stopped by user." Don't switch models with agents running, or accept they die (doc agents' disk files survive; analysis-only agents lose everything).
- **grep-count ≠ semantic verification** — a keyword count does NOT confirm meaning (I mis-claimed the SHA-1 drop from a grep; the reviewer's READ found the lane retained). Verify load-bearing claims by reading, before committing/merging.
- **Empty `defaultsecuretest.policy`** blocks the whole secure QA harness (looks like a serialization regression; is not).
- **Git-Bash-on-Windows classpath trap** — Unix paths (`/c/...`) inside a `;`-joined javac `-cp` are NOT auto-converted; use `cygpath -m`. PowerShell 5.1 splits unquoted `-D` args (quote them).
- **Demos must self-build** — depending on `~/.m2` runs stale behavior silently.

---
*End. Trunk 884a2c9a2. No production code written in producing this SOW.*
