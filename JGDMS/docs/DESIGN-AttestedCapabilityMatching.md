# Design — Attested Capability Matching (provisioning placement for a heterogeneous, trust-tiered fleet)

- **Status:** Provisional (pre-release; revisable on good cause). Exploratory — drawn from Rio, re-grounded; keystones validated, one open verify (§11, #3).
- **Date:** 2026-06-20
- **Branch:** `secarch-take-commit-recon`
- **Grounded against:** JGDMS trunk `fe1861e69`; DirtyChai `java.base` (`org.apache.river.api.security.*`, `au.zeus.jdk.authorization.*`) read 2026-06-20.
- **Related:** `DESIGN-SpaceChannelAuthorization.md` (foundation — channels, auth-before-match, install-on-take), `DESIGN-AgentEscalationGate.md`, `DESIGN-LeasedPermissionGrant.md`, DirtyChai `SECURITY_MODEL.md`.

---

## 1. Context, provenance, threat model

**What this is.** A model for matching *work* (provisioning a service, processing a dataset, accepting a capture task) to *nodes* (instruments, controllers, sidecars, agent hosts) across a radically heterogeneous, variably-trusted fleet — and delivering the placement as a capability, not an RPC.

**Where it comes from.** This is the salvageable core of **Project Rio**, re-grounded. Rio is designed for *trusted networks* (no identity/integrity/authority at any hop) and is rebuild-not-repair as-is — its `deploy → Cybernode → instantiate(downloaded code)` path is unauthenticated RCE. But Rio's *control-plane concepts* are good once re-grounded so the trust Rio *assumed* becomes an *explicit attested check*. Rio also serves as a **negative template**: its operation surface (deploy / instantiate / discover / associate / scale / report-metric) is the inventory of authority-bearing operations it left ungated. The single highest-value draw is **capability-based matching**; this doc develops it. (See `rio-blitz-substrate` memory for the full salvage triage.)

**Threat model** (identical to `DESIGN-SpaceChannelAuthorization.md` §1, plus a fleet axis):

- **No untrusted code.** All code passes the SCAP admission gate and is digest-pinned; the gadget/RCE class is closed by admission control.
- **Permission = multi-principal ∧ multi-digest conjunction**, deny-by-default (DirtyChai `SECURITY_MODEL.md` §8).
- **The new axis — *attested* heterogeneity.** Nodes differ in *what they can do* (capability) **and** *how strongly they can attest it* (trust tier T1 self-SVID / T2 gateway-vouched / T3 operator-procedural). A capability is therefore only meaningful paired with its attestation. "Adversarial input" = adversarial **data values, self-asserted capability claims, flood/DoS** from a differently-privileged or misbehaving principal — never gadget code.

The job: place work on a node that **can** do it (functional) **and** that we **trust to** do it (attestation × corroboration) — with the placement itself being an authority-bearing act.

---

## 2. The two artifacts

### 2.1 `CapabilityClaim` — what a node advertises

An `EntryRep` written to a dedicated **capability channel** (an exact-class channel per `DESIGN-SpaceChannelAuthorization.md` §3).

```
CapabilityClaim
  subject       SVID              ← SPACE-STAMPED at write from the verified mTLS peer,
                                     NOT a field the node fills in (identity is bound, not asserted)
  qualitative   Set<Predicate>    os=linux, jvm>=21, instr.protocol=SDR33, gnss=true, driver=topcon-gls
  quantitative  Map<Resource,Num> cpu=4, mem=8G, disk=120G, battery=72, uplink=sat|none
  attestation   {tier:  T1|T2|T3,
                 root:  tpm|atecc608|secure-boot|none,
                 voucher: gateway-SVID (when T2)}
  codeDigests   Set<Digest>       SCAP-vetted binaries present (the DigestGrant half of the model)
  metrology     {calibValidUntil, traceableTo, reg13Ref}?     ← instruments only
  validity      [from,to] epoch  + Outrigger entry-lease (renew / re-attest or the claim evaporates)
```

The move over Rio: a node does not advertise "I can read SDR33." It advertises "I can read SDR33, **attested at T2 via gateway-SVID, backed by an ATECC608, running code-digest D, calibration valid until epoch E**." Identity is space-stamped (writer provenance, never self-asserted — `DESIGN-SpaceChannelAuthorization.md` §5); the attestation tier and calibration become *matchable, attested* attributes.

### 2.2 `WorkRequirement` — the demand side

A template (matched) plus the authority the work will confer.

```
WorkRequirement
  needQual      Set<Predicate>     subset-required
  needQuant     Map<Resource,Min>  threshold-required (>=)
  trustFloor    {minTier, minRoot, minCorroboration}
  codeConstraint Set<Digest>?      must run exactly this vetted impl
  metroFloor    {needCalib, needTraceableTo}?
  grantsOnTake  PermissionGrant-template   ← what taking the offer authorizes the node to do
```

### 2.3 Schema-ordering rule (load-bearing — serves three concerns at once)

Lay the schema out as a **cheap, DER-parseable scalar head** (`attestation`, `quantitative`, `validity`, `metrology` epochs, `subject`) followed by a **lazily-unmarshalled tail** of heavy objects (full metrology certificate chains, the service payload). The same head serves (i) the proxy filter (§4.2), (ii) the trust-floor grant's out-of-band condition evaluation (§3.1), and (iii) the lazy-unmarshalling discipline (§5). Three pressures, one schema — see §5.

---

## 3. The match — two gates, deny-by-default, trust-gate first

```
eligible(node, work) =
   TRUST GATE   (authorization — server-side, deny-by-default)         §3.1
     AND
   FUNCTIONAL GATE (selection — exact match server-side + filter proxy-side)   §3.2
   evaluated TRUST-GATE-FIRST  (auth-before-match: the capability channel is a fleet-topology oracle)
```

Ordering follows `DESIGN-SpaceChannelAuthorization.md`'s **auth-before-match**: the capability channel leaks fleet topology and instrument placement, so channel-read authority is checked **before** the matcher runs over it. An ineligible reader/claim is **skipped, not denied** (no existence/timing signal).

### 3.1 Trust gate (authorization) — *validated against DirtyChai source*

The trust gate splits, and the policy model handles each half:

**(a) Identity + digest conjunction — stock, free reuse.** The grantee's `(SVID-principal ∧ codebase-digests ∧ user)` conjunction is directly expressible:
- `PermissionGrantBuilder.principals(Principal[])` — matches when the Subject has **all** provided principals, in any order (exact set-presence, ANDed).
- `PermissionGrantBuilder.digest(algorithm, byte[])` / `DigestGrant` — content-addressed; a plain `CodeSource` never matches a `DigestGrant` (fail-secure).
- ANDed with URI/certificate selectors as needed. Evaluated by `ConcurrentPolicyFile` (deny-by-default).

**(b) Ordinal tier (`tier ≥ T2`) + temporal calibration (`valid-at-epoch`) — NOT stock selectors, but a first-class extension covers it.** The builder's selector set is closed (`CLASSLOADER / PROTECTIONDOMAIN / PRINCIPAL / CODESOURCE_CERTS / URI / DIGEST`), and `PermissionGrant.implies` is handed **only** a `ProtectionDomain` (or `CodeSource`/`Principal[]`) — no ordinal, no comparison, no clock. So the trust floor is implemented as a small **`TrustFloorGrant`**, using one of the model's own documented extension paths:

- **Add a grant type** the way `DigestGrant` does: extend `PermissionGrant`/`URIGrant`, override `implies(ProtectionDomain)` to read a non-standard attribute from the domain (`DigestGrant` reads `pd.getCodeSource()` as a `DigestCodeSource`), conjoin with `super.implies` (principals + certs), and wire a builder `context`. `DigestGrant` is the working precedent that this is a sanctioned, normal extension.
- **For the temporal condition**, use the `PermissionGrant` **decorator** documented for *"condition or event based policy decisions"*: an **external event thread** (the attestation / calibration refresh) does the work and updates a **volatile**; `implies` merely reads the volatile — **no security check inside `implies`** (avoids the recursion/DoS the class warns about). Supported by `isDyanamic()` (runtime-only grant) and `isVoid()` with `Policy.refresh()` propagation.

**This keeps the trust floor *inside* the one unified policy model** — not a parallel guard subsystem.

**Unification with the ephemeral grant.** The decorator + volatile + `isDynamic` + `isVoid` + `refresh` machinery *is* the ephemeral, time-bounded grant (`impliesEphemeral`) the install-on-take design already uses (`DESIGN-LeasedPermissionGrant.md`). `calibration-valid-until` is simply the ephemeral grant's validity bound. No new concept; only its *condition* (attested tier/epoch) is new.

**Honest accounting.** Earlier framing ("trust gate = free policy reuse") was too strong. Reality: identity+digest is free; the ordinal/temporal floor is **one small dynamic-grant class** on the model's intended extension path. "Lean" survives; "free" does not.

**Source caveats the doc must honour:**
- `implies` must not perform security checks (recursion/DoS) → evaluate the condition out-of-band, `implies` reads a volatile.
- State transitions are visible only after `Policy.refresh()` → calibration expiry is refresh-driven (fits the lease/ephemeral model).
- `implies` sees only the `ProtectionDomain`, so the attested tier/epoch must bind to the grantee's **SVID-principal** (which is in the domain); the grant/decorator holds the attested scalars keyed by that principal.
- **Fail-closed where the attribute is absent** — like `DigestGrant` on a non-digest-aware JVM, a `TrustFloorGrant` implies *nothing* where the attestation attribute is missing (fail-secure, but a documented config footgun).

### 3.2 Functional gate (selection) — two-stage, SDM idiom

Outrigger's associative matching is **exact-equality / wildcard only** — it cannot do quantitative thresholds (`cpu ≥ 2`), logical combinations (OR/NOT), or the two-axis trust substitution (§4). The resolution is the canonical River **`ServiceDiscoveryManager` / `ServiceItemFilter`** pattern: coarse exact match at the indexed server, arbitrary predicate filter on the proxy.

- **Server-side:** coarse **exact** associative match on qualitative predicates + class + code-digests — the Blitz-derived per-field index (`feature/blitz-outrigger-field-index`; `EntryHolder.hasMatch`). Returns a candidate cursor via `JavaSpace05.contents()` (`MatchSet`).
- **Proxy-side:** an **`EntryFilter`** (analog of `ServiceItemFilter.check`) applies the *expressive* part — quantitative thresholds, logical combinations, and trust-as-preference/ranking — over the candidates.

**The rule that makes this safe in a capability-secure space (the difference from Jini lookup):**

> **Proxy-side filtering is SELECTION, never AUTHORIZATION.**

In `ServiceDiscoveryManager` the filter narrows results the client is *already entitled to see*, so client-side filtering is not a security boundary. Here, authorization stays server-side: (a) the §3.1 trust gate / channel-read gate ensures the proxy only ever **receives** candidates it is entitled to; (b) the trust **floor as placement authority** is re-checked server-side at **take-commit** (§6), not trusted to the proxy. Invariant: **a compromised or buggy proxy filter can only pick a *worse* node — never see unauthorized claims, never place unauthorized work.** Degraded selection, not a breach.

---

## 4. Two-axis substitutability (how a T3 DR-DOS gyro safely qualifies)

Attestation strength and corroboration strength are independent and **compose**. The trust floor is satisfiable two ways:

```
trustFloor satisfied  ⟺  attestation.tier ≥ minTier                                  (strong attestation)
                      ∨  (attestation.tier ≥ minTierFallback ∧ work.corroborationPlan ≥ minCorroboration)
                                                              (weak attestation, backstopped by corroboration)
```

A T3 instrument (e.g. a DR-DOS gyrostation) qualifies for trust-sensitive work **only if** the work commits to cross-checking its output against the certified network (the geodetic-adjustment / least-squares corroboration layer — see `real-world-data-system` memory). The matcher makes the attestation/corroboration trade explicit and per-work. The *selection* form of this lives in the proxy filter (§3.2); the *authoritative* floor is re-checked at take-commit (§6).

---

## 5. Lazy / staged unmarshalling

> **Never unmarshal an object you might discard.** Cost *and* deserialization-surface must rise **monotonically** with how far an entry survives.

- **Stage 0 — server, zero unmarshalling.** Exact match on **marshalled bytes** (`MarshalledInstance` byte-equality; never deserialize-then-compare).
- **Stage 1 — proxy, minimal parse.** **DER-parse only the scalar fields the filter references** (tier, cpu, battery, epoch) — cheap, **no class loading, no codebase contact**. Run the filter. **Short-circuit:** most-selective conjunct first; on the first failing predicate, stop — do not parse the fields later predicates would need (the "objects associated with filters that don't match").
- **Stage 2 — proxy, full materialization, survivors only.** Full Java unmarshal (class resolution / codebase / graph reconstruction) runs **only** for entries that passed, and only on fields actually consumed.

The `EntryFilter` declares its **read-set** (a field projection) so the proxy materializes exactly those fields.

**Feasible because** Outrigger's `EntryRep` keeps fields **individually marshalled**. River already moved this way at the discovery layer: the eager-`Entry[]` registrar is deprecated; **`SecureServiceRegistrar` returns marshalled forms** (client-controlled unmarshalling) — which *reinforces* this rule rather than contradicting it.

**Rationale even under no-untrusted-code** (gadget-RCE closed): unmarshalling is still (a) a **flood-DoS vector** (an adversarial principal floods the channel with cheap-to-byte-match but expensive-to-deserialize claims), (b) **codebase-contact / class-resolution surface**, (c) hot-path cost. This is the **cost/DoS dimension of auth-before-match**: do not deserialize for entries you would reject.

This is the same cheap-DER-scalar-head schema (§2.3) the trust-floor grant's out-of-band condition (§3.1) and the proxy filter (§3.2) both want.

---

## 6. Placement — install-on-take (authority-flow inversion; fixes Rio's RCE)

Rio: `match → Cybernode.instantiate(downloaded code)`, authority pushed monitor→node, unauthenticated. Re-grounded:

```
match → write a provisioning offer to channel[node.subject], target == node.subject
node TAKES the offer → install-on-take mints an ephemeral PermissionGrant(subject ∧ codeDigest),
                       live-only / TTL → node runs the digest-pinned service under that scoped grant
```

Authority flows because the node **takes** an offer it is eligible for (*take-conveys-authority*), not because a monitor commands it. Consequences:

- The node runs only code whose digest it already advertised (SCAP-vetted) and the offer authorizes — **no code mobility, no download-and-run** (Webster is gone).
- `target == node.subject` blocks another node stealing the offer.
- The grant is minted at take-commit, **non-serializable / live-only** → **restore-binding for free** (a re-provisioned node with a new SVID does not inherit it).
- The **trust floor is re-verified server-side here** (§3.1), independent of any proxy-side selection — this is the authoritative check.
- The **Monitor can only offer, never install** (§8). It runs its matching/offering on the live dispatch stack, down-scoped per the `doPrivileged` placement rule (`DESIGN-SpaceChannelAuthorization.md` §2).

Mechanism reuse: this is exactly the install-on-take / ephemeral `PermissionGrant` of `DESIGN-LeasedPermissionGrant.md` and `DESIGN-AgentEscalationGate.md`, with the offer carrying a `WorkRequirement.grantsOnTake` template.

---

## 7. Liveness, decay, self-healing

- Claims are **lease-held** Outrigger entries; a node must renew / re-attest or the claim evaporates (handles node death, partition, and underground disconnection naturally).
- **Trust decays:** when `calibValidUntil` passes or an attestation quote goes stale, the `TrustFloorGrant`'s condition flips (§3.1) — the node drops below the floor for trust-sensitive work **while still matching low-trust work**. Trust is time-bounded, not static.
- The **Monitor reconciles** a declared desired-state tuple against live placements; lease expiry + re-match = self-healing (Rio's `OperationalString` / Provision-Monitor loop, on the space).
- **Underground / disconnected island:** claims are island-local (embedded SPIRE), reconciled on surface; the survey-measurement-against-certified-control is the reconciliation oracle (`real-world-data-system` memory).

---

## 8. Who holds the filter (resolves the confidentiality concern by construction)

The proxy doing the filtering is the **Monitor** — Rio's Provision-Monitor role, re-grounded. The capability channel's **read-ACL authorizes the Monitor for all claims**, so it is entitled to everything `contents()` returns → no leak. Work-requesters **submit `WorkRequirement`s to the Monitor**; they do not read the capability channel themselves. The candidate set is **fleet-sized** (tens–hundreds), so "return-to-authorized-Monitor-then-filter" is cheap. The leak-and-filter risk arises only in a *decentralized per-requester* match, which Monitor-mediation avoids.

---

## 9. What is stock vs new code (lean accounting)

| concern | mechanism | stock / new |
|---|---|---|
| capability channel | exact-class Outrigger channel (`DESIGN-SpaceChannelAuthorization.md`) | stock |
| claim / requirement | `EntryRep` entries | stock |
| functional exact match | space matcher / Blitz per-field index | stock (in progress) |
| proxy filter | `EntryFilter` (SDM `ServiceItemFilter` idiom) over `JavaSpace05.contents()` | **new (small)** |
| trust gate — identity+digest | `PermissionGrantBuilder.principals/digest` + `ConcurrentPolicyFile` | stock |
| trust gate — ordinal/temporal | `TrustFloorGrant` (DigestGrant template + decorator/volatile) | **new (small)** |
| placement | install-on-take ephemeral `PermissionGrant` | stock (designed) |
| identity (taker, for policy) | SPIFFE SVID stamped on `ProtectionDomain` | stock |
| writer-provenance stamp on claim | capture `getClientSubject` in `write` + writer-SVID field on server-side `EntryHandle` (§11 #3) | **new (small)** |
| liveness / decay | Outrigger entry-lease + landlord; `isVoid` + `Policy.refresh` | stock |
| reconciler / self-heal | Monitor agent + space `notify` | **new (agent)** |

**New code = four small, well-scoped pieces:** the two schemas + `EntryFilter`, the `TrustFloorGrant`, the Monitor reconciler agent, and the writer-SVID stamp (capture in `write` + an `EntryHandle` field, §11 #3). Everything else is composition of existing or already-planned primitives. (Honours `lean-resourcing-focus`: only the fleet-placement and corroboration-trigger draws directly serve income; the rest of Rio stays a negative template, not a maintenance burden.)

---

## 10. Rio scorecard

Rio's single ungated operation — `match → instantiate` — decomposes here into **four authority checkpoints** where Rio had **zero**:

1. **channel-read / trust gate** (server, auth-before-match) — may this matcher even see the claim;
2. **functional + trust-floor selection** (proxy `EntryFilter`) — does the node fit, with what trust trade;
3. **offer target-binding** (`target == node.subject`) — only the intended node can take;
4. **take-commit install** (install-on-take, trust floor re-verified) — authority conveyed only on the node's own take.

---

## 11. Residuals & open items

- **Capability *values* are still self-asserted.** The SVID binds *who*, not the *truth* of "cpu=8" or "I can read SDR33" — the same provenance≠ground-truth gap as the data system. Bound by: require **attested** (e.g. TPM-measured) capabilities for safety-relevant work; corroboration demerits a node that fails work it claimed (identity is bound, so you know who lied); for instruments, calibration is an attested credential, not asserted.
- **The Monitor is a broad-visibility deputy** (reads the whole capability channel to match) — confused-deputy surface. Mitigated by the placement rule (matching runs on the live stack, down-scoped) and offer-only-never-install (§6, §8).
- **Capability channel as topology oracle** — even gated, match timing can leak; same residual/mitigation as `DESIGN-SpaceChannelAuthorization.md` (auth-before-match, skip-don't-deny).
- **Attestation freshness vs offline operation** — island-local claims + reconcile-on-surface (§7).
- **VERIFIED (#3, 2026-06-20) — feasible; small server-side addition, no blocker.** The verified peer SVID is *capturable* at write (JERI runs `OutriggerServerImpl.write` under `Subject.doAs(workerSubject)`; `Util.getClientSubject()` / `ServerContext.getServerContextElement(ClientSubject.class)` retrieves it) — but `write` does **not** call it today, so capture is a small addition. The client-supplied `EntryRep` lives in `outrigger-dl` (downloadable proxy, travels the wire) and is **untrusted for writer identity**; the stamp therefore belongs on the **server-side `EntryHandle`** (`outrigger-service`, created at write via `holder.newEntryHandle(rep, txn)`, which has no identity field today). #3 resolves to: capture the SVID in `write` + thread it into `newEntryHandle(rep, txn, writerSvid)` as a new `EntryHandle` field. The **matcher** reads it from the handle (`handle.rep()` access already exists); the **policy / `TrustFloorGrant`** reads the *taker's* SVID from its `ProtectionDomain` (stock SPIFFE stamping), and the Monitor correlates claim-writer-SVID ↔ taker-SVID to bind attested attributes to the authorized node. No open assumptions remain in §3; the writer-stamp is a scoped new addition (counted in §9).
- **Not built / not run.** This validates *detection and design*, not runtime behaviour. The property/differential tests (DirtyChai `SECURITY_MODEL.md` gate) are required before any implementation lands; `TrustFloorGrant` in particular needs deny-by-default + fail-closed-on-missing-attribute + expiry-on-refresh property tests.

---

## 12. Code-reference table (verified 2026-06-20 unless noted)

| element | location | fact relied on |
|---|---|---|
| grant selectors | `org/apache/river/api/security/PermissionGrantBuilder.java` | closed set: `CLASSLOADER/PROTECTIONDOMAIN/PRINCIPAL/CODESOURCE_CERTS/URI/DIGEST`; `principals()` = exact set-presence, any order |
| grant extension point | `org/apache/river/api/security/PermissionGrant.java` | `implies(ProtectionDomain)` only; documented **decorator for condition/event grants** (external-thread → volatile); `isDyanamic()`, `isVoid()` |
| custom-grant template | `org/apache/river/api/security/DigestGrant.java` | extends `URIGrant`; `implies(PD)` reads `DigestCodeSource` from `pd.getCodeSource()`, conjoins `super.implies`; builder `context(DIGEST)`; fail-closed where attribute absent |
| policy evaluation | `au/zeus/jdk/authorization/policy/ConcurrentPolicyFile.java` | deny-by-default `implies(PD, perm)` |
| SPIFFE identity | `au/zeus/jdk/authorization/spire/*`, `SpiffePolicyFile.java` | per-process SVID; principal stamping |
| exact-class channel / matcher | Outrigger `EntryHolder.hasMatch`; `feature/blitz-outrigger-field-index` | per-field index; whole-entry returns (forces exact-class channels — see `DESIGN-SpaceChannelAuthorization.md`) |
| proxy filter substrate | `net.jini.space.JavaSpace05.contents()` → `MatchSet` | server cursor the proxy iterates + filters |
| discovery precedent | `net.jini.lookup.ServiceDiscoveryManager` / `ServiceItemFilter`; `SecureServiceRegistrar` | coarse-exact-server + arbitrary-proxy-filter; marshalled-return registrar = client-controlled unmarshal |
| install-on-take / ephemeral grant | `DESIGN-LeasedPermissionGrant.md`, `DESIGN-AgentEscalationGate.md` | take-conveys-authority; `impliesEphemeral`; restore-binding |
| write path / writer stamp (#3) | `OutriggerServerImpl.write` (~L1390; no `getClientSubject` today), `EntryHolder.newEntryHandle` (L82), server-side `EntryHandle` (no identity field) | SVID capturable via JERI `getClientSubject` but uncalled; client `EntryRep` is `outrigger-dl` (untrusted for identity) → stamp on `EntryHandle` |
