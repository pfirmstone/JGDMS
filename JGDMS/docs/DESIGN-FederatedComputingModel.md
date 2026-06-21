# Federated Computing Model — Design (learnings from SORCER)

**Status:** Design / analysis (working draft). Concepts drawn from SORCER as prior art; not implemented. Selective adoption only — abstractions, not code.
**Scope:** The federated computing model one layer **above** the JGDMS space substrate — how distributed work is dispatched, composed, and executed over the space. SORCER is analysed as the mature prior art; the contribution here is supplying the deny-by-default, multi-principal trust SORCER lacked.
**Prior art:** SORCER (Service-ORiented Computing EnviRonment; M. Sobolewski), `github.com/mwsobol/SORCER`. Recon clone retained at `/home/user/GitHub/SORCER-recon` for further study; do not delete.
**Related:** `DESIGN-AttestedCapabilityMatching.md`, `DESIGN-CorroborationFramework.md`, `DESIGN-SpaceChannelAuthorization.md`, the DirtyChai security model.

## 1. Purpose

SORCER is a mature federated SOA built on the same stack this project uses (Jini/River + Rio provisioning + JavaSpaces). It is the **computing model one layer above the secure substrate**. The design intent is to mine that model and supply the trust it never had: deny-by-default, multi-principal-conjunction-over-digest authorization, with authority riding the space take. The full Exertion-Oriented Programming surface is more than this project needs; adoption is of concepts, not code.

## 2. Concepts drawn (grounded against source)

| Concept | SORCER mechanism (as found in source) | Use here |
|---------|----------------------------------------|----------|
| **Space-based pull execution** | `SpaceTaker.run()` loops `space.take(entry, txn)` → `ExertionEnvelop` → threadpool `SpaceWorker`; transactional take; dual dispatch (Catalog direct-lookup vs Space pull) | Prior art for Outrigger capability-matching and the Monitor. **No auth before take (pure template match)** — exactly the security upgrade point. |
| **Late-bound self-describing work units** | Exertion / Signature / Context / control-strategy (WHAT / WHERE / DATA / HOW) | Informs `WorkRequirement` / `CapabilityClaim` (see `DESIGN-AttestedCapabilityMatching.md`) |
| **Composite federation** | `Job` = recursive transient self-organising federations | Multi-agent orchestration / multi-stage pipelines (the metrological DAG) |
| **Multi-fidelity + morphing** | `plexus`: FidelityManager / Morpher / MorphFidelity; `service/modeling/Variability` | Feeds the corroboration fidelity ladder + Morpher (see `DESIGN-CorroborationFramework.md` §3.3) |
| **Signed tasks + audit** | `SignedServiceTask` + `TaskAuditor` (wired but opt-in via `instanceof`); `sos-exertmonitor` | Provenance/audit precedent |

## 3. Security — the negative template

SORCER has more security than Rio (a `SorcerPrincipal`, signed tasks, an ACL convertor, audit), but it is the enterprise-RBAC shape this project deliberately moves away from:

| SORCER | This project |
|--------|--------------|
| Coarse RBAC, role/access-class/**password**, defaulting to ANONYMOUS | Crypto **workload SVID** + operator + code digest, multi-principal conjunction |
| **Opt-in** (e.g. `SignedServiceTask` checked via `instanceof`) | **Deny-by-default** (`ConcurrentPolicyFile`) |
| Enforced downstream **at execution** | Enforced **at the space take** (auth rides the take) |
| Proxy trust barely used (≈2 files) | Proxy trust / smart-proxy as a first-class capability |

SORCER is therefore a worked, 20-year-validated example of the model whose **security** the deny-by-default, conjunction-over-digest, auth-before-take design improves on.

## 4. Benefits (why draw it)

- **Pull execution:** space+time decoupling (suits an intermittent fleet), self-balancing elasticity, transactional-take reliability, and authority that rides the take.
- **Late-bound self-describing units:** evolvability; the work unit is also the audit unit; clean security seams.
- **Composite federation:** compositional pipelines with per-hop authority.
- **Multi-fidelity / morphing:** resource-proportional graceful degradation; continuity under change; lineage-carrying distributed computation.

## 5. The value argument

Three points make this strategically relevant:
1. **De-risking** — the computing model is validated by ~20 years of running use.
2. **Reuse of abstractions, not code** — full EOP / variable-oriented programming is more than needed; adopt the model selectively.
3. **It quantifies the value of the security work** — the same computing model, but this project's is the one that is **safe on an untrusted, regulated, intermittent real-world network**. That safety is the differentiator and the income point.

## 6. Relationship to the rest of the stack

- **Below:** the JGDMS space substrate (Outrigger) + `DESIGN-SpaceChannelAuthorization.md`.
- **Dispatch:** a capability match + install-on-take, per `DESIGN-AttestedCapabilityMatching.md` (the trust SORCER's pure template-match take lacks).
- **Fidelity:** the multi-fidelity/morphing concept is realised in `DESIGN-CorroborationFramework.md`.
- **Application:** the underground survey leapfrog coordinate handoff is a concrete space-coordination instance (Survey-zoot `architecture/UNDERGROUND_SURVEY_SYSTEM.md`).

## 7. Status / unverified assumptions

| Assumption | Claim | Status | Notes |
|-----------|-------|--------|-------|
| Selective adoption | The model can be adopted as abstractions without the full EOP surface | ⚠️ DESIGN | Not implemented |
| Auth-before-take | Moving authorization to the space take is sound and sufficient | ⚠️ DESIGN | Grounded in the DirtyChai conjunction; not coded |
| Concept mapping | Exertion/Job/plexus map cleanly onto CapabilityClaim/pipelines/fidelity | ⚠️ DESIGN | Conceptual; validated against SORCER source, not against an implementation |

## 8. References
- SORCER — `github.com/mwsobol/SORCER`; recon clone at `/home/user/GitHub/SORCER-recon`.
- `DESIGN-AttestedCapabilityMatching.md`, `DESIGN-CorroborationFramework.md`, `DESIGN-SpaceChannelAuthorization.md`.
- DirtyChai security model — deny-by-default multi-principal conjunction.
