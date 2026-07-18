# Corroboration Framework — Design

**Status:** Design (working draft). No implementation yet; the SPIs and the first plug-in are specified, not built.
**Scope:** General-purpose. This document specifies a reusable framework. The survey/geodesy plug-in is named as the first instantiation, but its details live in the Survey-zoot application repository, not here.
**Related:** `DESIGN-AttestedCapabilityMatching.md`, `DESIGN-SpaceChannelAuthorization.md`, `DESIGN-AgentEscalationGate.md`, and the DirtyChai security model.

## 1. Purpose

The substrate (JGDMS space, JERI, DirtyChai authorization, attested capability matching) establishes *who / what / when* produced an observation and delivers it tamper-evidently. It does not establish whether the observation is *true*. The Corroboration Framework is the layer that turns attested, provenance-stamped observations into **justified belief**: a trust-scored estimate, qualified by the fidelity at which it was computed and the reference frame in which it is expressed.

It is deliberately general. An "observation" is any provenance-bearing datum; a "belief" is any derived quantity with an uncertainty and a verdict. Survey/geodesy is the first and most rigorous plug-in (certified frames, a mature estimator, well-developed reliability theory), but the engine is domain-agnostic.

## 2. Position in the stack

```
observation sources
   → space substrate (provenance stamp · channel authorization · delivery)
   → Corroboration Framework (estimate · consistency test · verdict)
   → justified belief + audit lineage
```

- **Input:** provenance-stamped observations carrying the multi-principal conjunction (operator ∧ workload SVID ∧ code digest ∧ time/place) defined by the DirtyChai trust model and attested-capability matching.
- **Output:** a verdict per derived quantity — trust score, achieved uncertainty, the fidelity tier used, the reference frame, and the lineage that produced it.

## 3. Core model

### 3.1 Var-model dependency graph = provenance lineage
Derived quantities depend on observations and on other derived quantities, forming a directed acyclic graph. That graph is simultaneously the *computation* graph (how a belief is estimated) and the *provenance* lineage (what it derives from). The framework treats them as one structure, so every belief carries an auditable chain back to attested observations.

### 3.2 SPIs (the pluggable seams)
- **Estimator** — observations + a stochastic model → estimates with covariance.
- **Consistency test** — residuals/covariance → inconsistency flag + blunder localisation.
- **Reference frame** — defines the datum a belief is expressed in, and transforms between frames with uncertainty propagation.

A domain plug-in supplies implementations of these three, plus a stochastic model and a set of fidelity tiers.

### 3.3 Fidelity ladder + Morpher
A belief can be computed at several fidelities F0…F3 (cheap/approximate → rigorous/certified). The tier is selected per belief from three inputs: the **attestation strength** of the inputs, the **trust floor** required by the consumer, and the **resources** available (compute, time, connectivity). A *Morpher* reconfigures the graph as conditions change — connectivity lost → drop a tier; more independent observations arrive → promote.

### 3.4 Verdict
The output is not a bare number. A verdict = {estimate, achieved uncertainty, trust score, fidelity tier, reference frame, lineage}. A consumer may require a minimum trust score *and* a minimum fidelity *and* a specific frame.

## 4. The two trust axes compose

Trust has two independent axes that multiply:

- **Attestation strength** — how strongly the input's origin is proven (T1 self-attesting workload SVID → T2 gateway-vouched → T3 operator/procedural).
- **Corroboration strength** — how strongly the input agrees with independent evidence; ≈ 1/MDB (minimal detectable bias) given the network's redundancy.

The product is what governs acceptance:
- **Weak attestation + strong corroboration = acceptable.** This is how an un-attestable legacy instrument safely joins a zero-trust system: its reading agrees with the certified network within covariance.
- **Strong attestation + failed residuals = flagged.** Authentic origin does not make a blunder or a spoof correct.

The corroboration axis is the backstop for the entire un-attestable tier, and the injection/spoof detector for the attested tier.

## 5. Corroboration mechanism

Corroboration is the multi-principal conjunction lifted from authorization to data: belief is justified by **agreement among independent, attested observations**, not by volume.

- Each observation has a redundancy number; the network's minimal detectable bias bounds the smallest error that would pass undetected (internal reliability) and its effect on the result (external reliability).
- Corroboration strength scales as 1/MDB; more *independent* observations lower MDB.
- "Independent" is load-bearing. N observations that share a systematic (the same uncorrected refraction, the same instrument constant, the same un-decorrelated condition) count as far fewer than N. Independence is achieved by *diversity* — different principals, times, geometries, methods.

## 6. Measurement strategy (the operating principle)

Stated operationally: **take enough independent observations to reach the required tolerance, and recognise the systematic floor where more stop helping.**

- Additional independent observations reduce the *random* component as 1/√N.
- They do nothing for *systematic / correlated* error, which sits at a floor. The framework watches whether observed scatter is still falling like 1/√N (keep going) or has plateaued (floor reached → more observations are wasted; attack the systematic instead — decorrelate, recalibrate, change geometry).
- Conditions are **characterised and propagated**, not accepted/rejected. An adverse condition becomes an honestly inflated uncertainty that the estimator down-weights correctly. Bad conditions become quantified uncertainty — never silent error, never discarded data.
- Because per-observation uncertainty is characterised, the framework computes how many observations are needed (N ≈ (σ_single / σ_target)²), preventing both under- and over-observing.

## 7. Honesty about the limit (the blind spot)

Provenance integrity is not physical ground truth, and corroboration is not truth. A *coordinated* systematic error — the same bias on every contributing observation — produces internally consistent residuals and passes the consistency test. This is the framework's blind spot, and it is real.

Two things keep it honest:
- The blind spot is **measurable**: the minimal detectable bias quantifies the smallest coordinated error that could hide. The framework reports it; it does not pretend to zero.
- The mitigation is **diversity**: corroboration converts a systematic into an averageable error only to the extent the systematic *varies* across independent observations. Independent principals, times, geometries, and methods are what shrink the undetectable coordinated component.

## 8. Plug-in contract

A domain plug-in provides:
1. An **Estimator** (observations + stochastic model → estimates + covariance).
2. A **Consistency test** (residual/variance testing + blunder localisation).
3. A **Reference-frame** provider (datum definitions + inter-frame transforms with uncertainty).
4. A **stochastic model** (per-observation-class uncertainty, including correlated terms).
5. A set of **fidelity tiers** with selection criteria.

### First plug-in: survey / geodesy (specified in Survey-zoot)
- **Estimator:** rigorous least-squares network adjustment (DynAdjust).
- **Consistency test:** variance-factor / χ² global test + Baarda data-snooping.
- **Reference frames:** GDA2020, Regulation-13-certified sites, AUSPOS covariance; underground traverse frames.
- **Stochastic model:** instrument σ (e.g. gyro ±20″, angle, distance) plus correlated terms (lateral refraction, vibration).
- **Fidelity tiers:** F0 field-provisional → F3 certified-frame rigorous adjustment.

The survey plug-in's details (instrument integration, error budget, field method) belong in Survey-zoot. This is the general/plug-in seam: general at the SPI, lean at the implementation.

> **Cross-pointer 2026-07-17 (flagged here at the JGDMS-side seam only — real design belongs in
> Survey-zoot, per this doc's own scope note above): a possible application for JGDMS's `@RemoteFunction`
> filter mechanism (`JGDMS-STD-009-...-DRAFT.md` §8/§11) at the F0 tier specifically.** STD-009 §11
> already names this framework's Consistency-test SPI as a forward reference ("the `Test` predicate of
> `DESIGN-CorroborationFramework.md`"); this note is the other direction — where filter-shaped pushdown
> might serve *this* framework, not the reverse. The **Estimator** and **Consistency-test** SPIs above
> stay firmly out of scope for this — DynAdjust's least-squares adjustment and Baarda/χ² testing are
> iterative, stateful, rigorous numerical procedures, not bounded pure predicates; conflating them with a
> CEL-shaped filter would repeat the exact category error flagged in
> `SOW-Smart-Proxy-Isolation-Architecture-Overview.md` §1a (CEL cannot replace genuinely stateful
> computation). What plausibly *does* fit: **bounded, threshold-based screening at or near the
> instrument, before an observation is worth admitting to F1+ processing at all** — e.g. rejecting/
> flagging observations against PDOP/HDOP, SNR, elevation-mask, or instrument-reported quality flags,
> all bounded comparisons over a handful of typed numeric fields, structurally identical to the
> Outrigger/Reggie filter case. Two live JGDMS-side threads make this concretely relevant rather than
> abstract: (1) the "GLS instrument federation" embedded-JERI-in-Rust work means an instrument-hosted
> filter of this kind needs no JVM sidecar at all, since the CEL-shaped format's whole point is being
> safe-by-construction without process isolation — this is a genuinely cheap win specifically for
> resource-constrained field instruments, not just a JVM-service optimization; (2) STD-009 §11's own
> still-open "what does 'computed' mean" question (its phrase "ranges, inequality, compound, computed"
> is undefined anywhere in that document set) plausibly has a concrete answer here too — a bounded, pure,
> **fixed-formula value transform** (not just a boolean predicate), e.g. converting raw bearing/elevation/
> distance into vector components at the instrument (the "convert to vector-math convention in one
> isolated step" lesson already captured elsewhere in this project's working notes) — CEL's arithmetic
> operators plus a small, fixed, platform-audited set of custom functions (not arbitrary user code) could
> cover this while staying bounded/safe. **Application confirmed (Peter, 2026-07-17) — the direction is
> validated: bounded, instrument-level filter/value-transform pushdown, at the F0 screening tier, is a
> real fit for this framework, distinct from and never substituting for the Estimator/Consistency-test
> SPIs above.** Detailed design is still unstarted — the exact predicate/vocabulary needs, the concrete
> custom-function set, and where this actually lives belong in **Survey-zoot's own repo** (present
> locally, real per-instrument modules for RTC360/GLS/etc.), including checking whether an informal
> version of this screening pattern already exists there before assuming a blank slate. This note
> records the confirmed *direction* only, not a design.

## 9. Status / unverified assumptions

| Assumption | Claim | Status | Notes |
|-----------|-------|--------|-------|
| Framework SPIs | Estimator / Consistency-test / Reference-frame seams are sufficient | ⚠️ DESIGN | Not implemented |
| Two-axis trust | attestation × corroboration composes into one acceptance decision | ⚠️ DESIGN | Grounded in the DirtyChai conjunction; not coded |
| DynAdjust as Estimator | DynAdjust serves the F2/F3 Estimator SPI | ⚠️ UNVERIFIED | Integration not built; process-invocation first, FFM later |
| MDB ≙ corroboration strength | trust score derivable from minimal detectable bias | ⚠️ DESIGN | Standard geodetic reliability theory; general-trust-score mapping not yet formalised |
| Survey plug-in | the first instantiation validates the seam | ⚠️ DESIGN | Specified in Survey-zoot; not implemented |

## 10. References
- `DESIGN-AttestedCapabilityMatching.md` — attested capability claims; the trust gate the attestation axis builds on.
- `DESIGN-SpaceChannelAuthorization.md` — channel authorization for observation delivery.
- DirtyChai security model — the multi-principal conjunction this lifts to the data level.
- Geodetic reliability theory — Baarda data-snooping; variance-factor testing; internal/external reliability.
- Survey-zoot — the survey/geodesy plug-in (first instantiation).
