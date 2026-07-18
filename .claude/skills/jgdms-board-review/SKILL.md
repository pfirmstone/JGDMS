---
name: jgdms-board-review
description: JGDMS design/security/code review heuristics distilled from four retired board seats (ASN.1/DER + module-boundary, adversarial security, JERI transport, rule-fidelity/determinism/cross-language). Use before scoping or conducting any nontrivial design review, security review, or PR review in this repo — covers canonical-form contracts, outcome-vs-mechanism checks, fail-closed audits, confused-deputy hunts, and domain-specific war stories/checklists. Triggers on "review this design", "security review", "is this safe", "board guidance", "review heuristics", or any request to review JGDMS code/design for correctness or security.
---

# JGDMS Board Reviewer Guidance

Distilled review heuristics from four retired JGDMS board seats. Load this before scoping or
conducting any nontrivial design review, security review, or PR review in JGDMS.

Full text: [`JGDMS/docs/JGDMS-Board-Reviewer-Guidance.md`](../../../JGDMS/docs/JGDMS-Board-Reviewer-Guidance.md)
(1180 lines). Don't read it end-to-end by default — read Part I always, then jump to the domain
section(s) that match the change under review (use `Read` with `offset`/`limit` on the line
ranges below, not a full read).

## Always read: Part I — General principles (lines 50-265)

13 cross-cutting principles (G1-G13) that recurred across all four board seats independently,
regardless of domain:

- **G1** canonical form is the contract — one valid encoding per value; a decoder that *encodes*
  canonically but *accepts* non-canonical input on decode is still broken.
- **G2** outcome is a claim, mechanism is the proof — convert every stated guarantee into "what
  concrete enforced construct guarantees this, and is it actually in the design?"
- **G3** verify independently, don't trust the self-report — green tests prove nothing about
  untested input; reproduce tallies yourself, run adversarial input against built classes.
- **G4** declaration-is-the-signal — every rule keys on what the developer already wrote, never a
  runtime instance/hash/restating annotation.
- **G5** structure on the wire, behaviour in the constructor — Layer 1 (wire/schema, digest-covered)
  vs. Layer 2 (`check(GetArg)`/constructor, semantic); a proposal misplacing a behavioral contract
  onto the wire has misplaced a layer.
- **G6** fail-closed on ambiguity, always — but check the fail-closed *decision* doesn't leave a
  fail-open *side effect* (trace the cleanup path of every security throw, every platform).
- **G7** repair not amputate, report faithfully — rank by real exploitability with a concrete
  scenario; distinguish verified/CONFIRMED from recommended/PLAUSIBLE.
- **G8** all sources of truth must agree — prose spec, machine-checkable module, built code;
  citations are load-bearing, open the dependency and confirm.
- **G9** a normative spec is the interop test — could a peer implement it from the doc alone?
  Cross-language claims need active testing against a second runtime, not extrapolation.
- **G10-G13** (fresh-reviewer-vs-veteran A/B findings) — a boundary fence is not an interior fence
  (check what a guarded recursive call recurses *into*); probe empty/degenerate inputs, not just
  populated ones; "verified" must mean bound-to-known-good, not merely self-consistent; run the
  adversarial input, don't just reason about the bound.

## Then read: the matching domain section(s)

Each domain section has hazards (cost/severity order), JGDMS-specific gotchas, war stories
(defect + "the tell" that surfaced it), and a one-page checklist.

| Domain | When the change touches | Lines |
|---|---|---|
| §2.1 ASN.1/DER & module boundary | wire encoding, schema digests, Entry byte-matching, canonical forms | 268-460 |
| §2.2 Adversarial security | new remote-facing entry points, object reconstruction paths, caller/principal handling, authorization | 461-695 |
| §2.3 JERI transport | endpoints, connection lifecycle, transport layering, replacing/deprecating a transport | 696-935 |
| §2.4 Rule-fidelity/determinism/cross-language | serialization rules, cross-JVM/cross-language consistency, spec-vs-implementation drift | 936-1165 |

Reviews often span more than one domain — e.g. a DER change to a remote-facing type usually needs
both §2.1 and §2.2.

## Two reflexes general enough to apply outside their home domain

- §2.2's **ungated reconstruction door** — a new form reaching object-reconstruction that bypasses
  the primary path's gate; "wire-names-the-class" is where capability escalation hides. Check this
  on any new deserialization/reconstruction entry point, not just security-labeled ones.
- §2.2's **confused-deputy / role-reversal hunt** — any new *direction* of request inverts
  "caller = connection initiator"; re-derive caller/execution-subject/stamped-principal explicitly
  rather than assuming the usual direction holds.
- §2.3's **pre-deletion audit** — before replacing or deprecating any layer, check what the old
  layer does beyond its headline job; no half-retirement.

These generalized once already: first applied outside DER/transport review to a completely
different domain (the DirtyChai OIS/OOS SPI-replacement design, `java.io` serialization) and both
reflexes surfaced real findings there — evidence they're genuinely general, not domain trivia.

## Provenance

Consolidated 2026-07-05 from four retired board seats' retirement capstones. Originals preserved
at `JGDMS/docs/board-guidance/{asn1-der-module,security-adversarial,transport-jeri,rule-fidelity-determinism}.md`
for provenance only — the consolidated doc supersedes them as the thing to actually read.
