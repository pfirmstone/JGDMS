# SOW — Rust and Haskell peer implementation plan: JERI + ATOMIC DER + DETERMINISTIC CEL

- **Drafted:** 2026-08-29.
- **Status:** DRAFT — planning only. **No code has been written or is proposed by this document.**
  This is a task breakdown and sequencing plan for a non-JVM peer implementation, folding in the
  Entry-representation question that was raised while scoping it. Where the underlying design docs
  say "research" or "skeleton, prose not written," this document repeats that status rather than
  presenting the work as further along than it is.

- **Companions — read in this order before starting:**
  1. `investigation-haskell-atomic-der-dedup-cel-feasibility.md` — the existing Haskell feasibility
     verdict ("feasible with caveats, no blocker") and its §8 effort ranking, which this plan adopts
     and extends to Rust. Read in full; this document does not re-derive §§1–7's per-layer findings
     (numeric semantics, transcendentals FFI, strings, laziness discipline, SHA-256/dedup) — take
     them as settled for Haskell and, where §8 says a finding is language-independent, for Rust too.
  2. `der-rust-collection-mapping.md`, `der-haskell-collection-mapping.md` — the only concrete
     per-language codec sketches that currently exist (collection-ordering discipline only). Both
     are marked "research / cross-language design note" — no other part of either codec is designed.
  3. `investigation-atomicserial-schema-readahead-filtering.md` and its portability follow-on
     `investigation-readahead-filter-rust-haskell-portability.md` — the latter's Question 6 answer
     (fold the Tier A admission hook into each base codec's design *now*, day one, rather than
     retrofit it the way Java did) is a direct input to Part 2.2 below.
  4. `JGDMS-STD-012-JERI-Transport-v0.1-DRAFT.md` — **skeleton only**, no section written to full
     prose. Read its status line before assuming it can be implemented from.
  5. `SOW-JERI-Standard-Modernization.md` — the task list that produces STD-012's prose; its own G9
     acceptance criterion ("a Rust peer implements from the doc alone") is why JERI gates this plan
     (Part 1).
  6. `SOW-DER-JERI-CEL-Remaining-Work-2026-07-25.md` §3.2–3.3 — the existing gating statement this
     plan generalizes: today it is written for Rust only ("Rust JERI DER → then CEL Rust evaluator
     ... gated on ... JERI standard being written to G9"). Part 1 below treats the same gate as
     applying to Haskell, since nothing in the gate's reasoning is Rust-specific.
  7. `JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md` (+ Appendix C, Stream Schema Dedup) and
     `JGDMS-STD-011-CEL-Filter-Expression-Format-v0.1-DRAFT.md` (+ Appendix B) — the two standards
     that, unlike STD-012, are already written to a level a non-JVM implementer can work from
     (the "G9 already met" layers; see Part 1).

## Background

Peter's question that this document folds in: whether Rust or Haskell would need something
equivalent to Java's dynamic `Entry` class generation. The answer bears directly on scope, so it is
addressed first (Part 0) before the JERI/DER/CEL plan proper (Parts 1–3), rather than as an
afterthought — it changes what the peers need to build, not just how.

The plan proper exists because "implement JERI + ATOMIC DER + CEL in Haskell and Rust" is not one
task. It is four components (JERI transport/invocation, DER codec, dedup layer, CEL evaluator)
crossed with two languages, and — per the companions above — the four components are not equally
specified, equally hard, or even orderable the same way in both languages. This document's job is
to say, concretely, what to build first in each language and why, not to re-establish that any of
it is feasible (already done, for Haskell, by the feasibility investigation).

## Part 0 — Entry representation needs no dynamic class generation in either language

**Finding:** neither Rust nor Haskell should attempt to replicate Java's dynamic `Entry` class
generation. It is not a missing capability that needs to be built for these peers — the mechanism
it replaces is a Java-specific backward-compatibility obligation that does not exist for a
ground-up peer.

1. **Why Java does it at all.** `net.jini.core.entry.Entry` is a legacy Jini lookup-service
   contract: existing clients expect a *named class* with public fields and a no-arg constructor.
   When an `Entry`'s schema arrives over the wire without that class available locally, JGDMS
   synthesizes one at runtime (bytecode generation) so old lookup-service client code keeps working
   unmodified. This is compatibility machinery for a decades-old API shape, not a requirement of
   the ATOMIC DER model itself — DER already carries the schema with the bytes and does not need a
   loaded class to read a value (`decodeToFieldMap`: class-free, reflection-free, no constructor
   invoked).

2. **Neither peer language has (or needs) the equivalent mechanism.** Rust has no runtime bytecode
   generation facility in stable Rust at all — this is not a gap to fill, it is simply not how the
   language works. Haskell has a theoretical path (compiling and loading source at runtime via the
   GHC API, or the `hint` package) but it is heavyweight and impractical for this purpose, and nothing
   in either language's ecosystem expects a named-class Entry shape the way old Jini lookup clients
   do — there is no legacy client population on either peer to stay compatible with.

3. **What each peer should do instead: use the generic field-map/record shape natively.** Both
   languages should represent a decoded `Entry` the same way `decodeToFieldMap` already represents
   one on the Java side before any class is involved — `className → (fieldName → value)` — as a
   native structure: a Rust enum (`DerValue`, or similar, matching the wire-type token vocabulary in
   `der-rust-collection-mapping.md` §0) or a Haskell algebraic data type. This is not a lesser
   substitute for dynamic class generation; it is the same schema-with-bytes projection Java's own
   read-ahead path already uses, and it is a *smaller* amount of machinery than Java needs, because
   Java carries the legacy-compatibility burden and these peers do not.

4. **Compile-time codegen is an optional later ergonomic increment, not a requirement.** Where a
   service's `Entry` schemas are known in advance, Rust proc macros or Haskell Template Haskell can
   generate strongly-typed accessors over the generic field map, the way a schema-derived struct is
   nicer to use than a raw map. That is a convenience layer to consider once a base codec exists
   (see Part 2), never a prerequisite for correctness, and out of scope for this plan's first
   increment.

5. **This also means Entries never need the process-isolation/stub treatment proxies need on
   either peer.** That distinction (established in the process-isolation design work: a stub is
   only required for a proxy, because a proxy carries remote *behavior*; an Entry is pure data,
   materialized directly from the DER schema) applies identically off the JVM. A Rust or Haskell
   peer receiving an Entry has nothing to isolate — no code is loaded, named, or executed to
   represent it — and receiving a proxy from such a peer raises a separate, harder question (an
   arbitrary remote peer's proxy execution model) that this document does not attempt to scope.

## Part 1 — Sequencing: why JERI gates the whole plan, in both languages

This is the load-bearing fact for the rest of the document, and it is not this document's own
finding — it already exists, stated for Rust, in `SOW-DER-JERI-CEL-Remaining-Work-2026-07-25.md`
§3.2: *"The Rust DETERMINISTIC CEL evaluator (T4) is gated on a Rust JERI DER implementation, which
is gated on the JERI standard (§3.3) being written to G9 (implementable-from-doc-alone)."*

Three things are true simultaneously and jointly produce this gate:

- **STD-006 (ATOMIC DER) and STD-011 (DETERMINISTIC CEL) are already specified to the level a
  non-JVM implementer can work from** — both have ASN.1 modules (`docs/asn1/`), and per
  `SOW-JERI-Standard-Modernization.md`'s own framing, STD-006 (+ Appendix C) and STD-011 (+
  Appendix B) already let a peer implement "the object and expression layers from the docs alone."
  The DER codec and the CEL evaluator are *not* blocked on more Java-side documentation work.
- **STD-012 (JERI Transport & Invocation) is a skeleton, not a specification** — section headings
  and one normative-intent sentence per section, explicitly not implementable yet. Its own status
  line says so. A peer cannot implement JERI's mux/invocation/DGC layer from STD-012 today; it
  would have to read trunk Java as reference instead (`net/jini/jeri/connection/doc-files/mux.html`
  v1.1, the `OutboundRequest`/`InboundRequest`/`Connection`/`ServerConnection` javadoc that the
  board seat rules is "today's real spec," and SRC-RR-116 for DGC) — exactly the finding the
  Haskell feasibility investigation's §8 already reached independently: JERI is "hard by scope not
  language," and it is the one area not covered by a self-contained spec the way DER/CEL are.
- **CEL evaluation over remote data and JERI invocation are, in this codebase, coupled at the
  transport** — a peer CEL evaluator that can only run against locally-supplied test vectors is a
  real and useful deliverable on its own, but the deployment this whole line of work is for
  (Outrigger filter pushdown, a Rust/Haskell participant actually talking to a JGDMS/DirtyChai
  deployment) needs JERI underneath it. That is what "gated on" means in §3.2 — not that the CEL
  evaluator cannot be *built* without JERI, but that it cannot be *used as a JGDMS peer* without it.

**This plan treats the same gate as applying to Haskell, not just Rust.** Nothing in the three
bullets above is Rust-specific: STD-012's skeleton status is language-independent, and the Haskell
feasibility investigation's own §8 reached the identical "JERI hardest, identical difficulty in
Rust" conclusion from the Haskell side. The existing gating statement is written for Rust only
because that is the peer the CEL Filter Format SOW's T4 happens to name; it should be read as the
general rule, and this document makes that explicit rather than leaving it as an implied
Rust-specific case. `[OPEN]`: whether STD-012's own G9 acceptance bar should be *proven* by an
independent Haskell peer in addition to the named Rust one, or whether one non-JVM implementation
is treated as sufficient evidence the doc is implementable-from-alone — the existing SOW language
only names Rust for this; that asymmetry should be resolved when `SOW-JERI-Standard-Modernization.md`
is next revisited, not silently inherited here.

**Practical consequence for sequencing (both languages):** build the DER codec and the CEL
evaluator first — they are gated on nothing but implementation effort — and treat JERI as the
long-pole item that determines when either peer can act as a real JGDMS participant rather than a
standalone codec/evaluator. This is exactly the Haskell feasibility doc's own effort ranking
(transport hardest; CEL structurally easy but exactness-hard; DER codec medium; dedup smallest),
generalized to Rust, where §3.2 already independently arrives at the same order for the one
component it discusses.

## Part 2 — Per-component plan

Both languages are planned together per component, because the hard part of each component (per
the feasibility investigation's §8 and the STD-012 gap analysis above) is largely
language-independent scope, not language-specific difficulty — the differences that *do* exist are
called out explicitly within each component below.

### 2.1 JERI transport & invocation — the long pole, both languages

- **Blocked on:** `SOW-JERI-Standard-Modernization.md`'s prose-writing task (STD-012 §§ mux,
  invocation, fault marshalling, constraint model, endpoint families, DGC) reaching G9. This is
  Java-side documentation work this plan does not do — it is upstream of both peer implementations,
  named here as a dependency, not as a task this document assigns.
  - **Interim path, if the peer work starts before STD-012 reaches G9:** implement directly against
    trunk Java as reference (the mux.html v1.1 spec + the javadoc the board seat rules is the real
    spec + SRC-RR-116 for DGC) rather than waiting, understanding that whatever is built this way
    inherits reference-implementation risk (undocumented behavior copied faithfully vs.
    intentional deviation) until the standard's prose exists to check against. This is the same
    risk `SOW-JERI-Standard-Modernization.md` names as the reason G9 acceptance requires successful
    implementation *from the doc alone* — building from trunk first does not satisfy that bar
    retroactively; it produces a peer that will need to be re-validated against the prose once
    written.
- **Ratified invocation-layer facts usable now, regardless of prose status** (per
  `SOW-DER-JERI-CEL-Remaining-Work-2026-07-25.md` §3.3): the method-identifier map (`0x00` reject /
  `0x01` SHA-1, sunset JGDMS 5.0 / `0x02` DER SHA-256, length-delimited, SHA-512-capable, 128-bit
  default) is ratified, not skeleton — a peer can implement the invocation identifier scheme
  correctly today even though the surrounding prose is not yet written.
- **Language-specific transport ingredients** (per the feasibility investigation, Haskell side):
  `network` for sockets and `tls-2.4.3` for a native-Haskell TLS stack supply the raw ingredients;
  the mux/invocation logic on top is the unspecified part discussed above, not the transport
  substrate. `[OPEN]`: the equivalent Rust crate survey (a `tokio`/`rustls`-based sketch analogous
  to the Haskell doc's) does not exist yet in this repo — `der-rust-collection-mapping.md` is
  scoped to collection ordering only and says nothing about transport. This is a gap in the Rust
  material relative to the Haskell material (see Part 4), not a finding that Rust lacks suitable
  crates.
- **DGC:** STD-012 depends on STD-008 §6, which is "the DGC modernization design-of-record" —
  DER-native DGC per STD-006, with the residual legacy `readObject` path flagged separately for
  removal. A peer's DGC implementation should target the DER-native design, not the legacy path.

### 2.2 ATOMIC DER codec — medium difficulty, both languages, and where the read-ahead filter folds in

- **Status quo:** collection-ordering discipline is mapped for both languages
  (`der-rust-collection-mapping.md`, `der-haskell-collection-mapping.md`); nothing else about
  either codec is designed yet — no scalar encoding walkthrough, no schema-chain/dedup handling
  sketch, no admission-filter hook, for either language.
- **Difficulty assessment (adopted from the feasibility investigation, applies to both):** medium.
  ADTs — Rust enums and Haskell algebraic data types alike — map close to 1:1 onto STD-006's closed
  CHOICE/tag registries, giving a natural reject-by-construction pattern-matching style for
  unrecognized tags in both languages. Rust's ownership model is the one place this may differ
  from Haskell's story: `[OPEN]` per
  `investigation-readahead-filter-rust-haskell-portability.md` Question 3, it is not yet established
  whether Rust's ownership rules force an earlier commitment to a concrete typed allocation than the
  Java `decodeToFieldMap` behavior ("no class loaded, no constructor invoked") assumes, or whether an
  equivalent read-without-reconstruct borrow/projection can be expressed without it.
- **Fold-in from the read-ahead filter portability investigation (Question 6):** that investigation's
  own conclusion is that *now* — before either base codec exists — is the cheaper time to settle the
  schema-verified-before-construction admission seam, compared to Java, where the filter is being
  retrofitted onto an already-shipping codec. This plan adopts that conclusion directly: **the
  schema-digest-verified / not-yet-constructed hook point (Tier A: class-name/schema-digest
  allowlist) should be a day-one part of each base codec's decode pipeline design**, not a later
  addition. Concretely, whoever designs the Rust and Haskell codec decode paths should locate and
  document this seam as part of that design, answering
  `investigation-readahead-filter-rust-haskell-portability.md`'s Questions 1 and 4 (hook location;
  digest-verification-before-filter ordering) as part of the codec's own architecture, not as a
  separate later investigation.
- **Haskell-specific:** the feasibility investigation's §2 laziness-discipline finding applies to
  the base codec's own fail-closed obligations already, independent of the filter question — a
  strictness discipline is needed so a `REJECT`-equivalent verdict (malformed input, ceiling
  violation, unrecognized tag) cannot be bypassed by an unevaluated thunk. The portability
  investigation's Question 2 (does a filter hook need its *own* forcing point, or does it ride the
  codec's existing strictness discipline) should be answered once the base codec's strictness
  points are actually decided, not before — this plan defers it to that point rather than
  guessing at codec internals that do not exist yet.

### 2.3 Stream schema dedup — smallest component, both languages

- Per the feasibility investigation: a table/ceilings/traversal rule bolted onto an existing codec,
  the smallest of the four components. STD-006 Appendix C is the normative source (the `[15]`
  version octet `8F 01 01`, per-marshal-stream dedup table lifetime — "cited, never restated" per
  STD-012's own framing). No component-specific caveats beyond what Part 2.2 already covers for the
  base codec it sits on top of.
- **Sequencing:** build after the base codec exists (it modifies the codec's chain-decode path),
  before or alongside CEL — it has no dependency on CEL or JERI.

### 2.4 DETERMINISTIC CEL evaluator — structurally easy, exactness-hard, both languages

- **Structural shape:** per the feasibility investigation, a small closed expression language (27
  node kinds in the Java sealed `ExprNode` AST) — this maps naturally onto a Rust enum or Haskell
  ADT the same way the DER codec's tag registries do.
- **Where the real difficulty is:** not the shape, but exactness under the bit-identical-output
  conformance regime STD-011 requires — this is the "correctly-rounded transcendentals" problem,
  and the reason `SOW-DER-JERI-CEL-Remaining-Work-2026-07-25.md` §3.2 calls out **CORE-MATH as
  shared C** across a future Rust *and* Haskell peer specifically — both language plans should
  bind to the same CORE-MATH-based transcendentals implementation via FFI rather than each
  re-deriving correctly-rounded math independently, since divergent rounding between peers would
  break exactly the bit-identical-corpus guarantee STD-011 exists to provide.
- **Scope note carried over from the Java side, applies identically to both peers:** the Java
  `jgdms-cel` module currently ships only a wire *decoder* plus the evaluator and verifier — it has
  no text parser and no wire *encoder* yet (§3.1 of the remaining-work SOW: T-CEL-A/T-CEL-B, not yet
  built). A peer evaluator only needs to *consume* the wire form to evaluate rules, so this gap does
  not block a peer evaluator — but a peer that also wants to *author* rules as text, or generate its
  own conformance vectors, inherits the same missing-parser/encoder gap the Java side has, since
  STD-011's textual grammar is defined once and shared, not duplicated per peer.
- **Dependency on JERI:** per Part 1, the evaluator itself is buildable and testable against local
  vectors without JERI; acting as a live filter-pushdown participant in an Outrigger deployment
  needs the transport underneath it.

## Part 3 — Cross-language shared work (build once, not twice)

A few pieces of this plan are explicitly not "do it once per language" — building them twice risks
exactly the kind of cross-peer divergence STD-006/STD-011's bit-identical guarantees exist to
prevent:

- **CORE-MATH transcendentals (C, via FFI)** — one shared implementation, bound from both Rust and
  Haskell, per Part 2.4. Do not let either peer's FFI binding drift into reimplementing rounding
  behavior independently.
- **ASN.1 modules (`docs/asn1/`)** — already validated with `asn1tools`; both codecs should be
  checked against the same modules, not independently-interpreted prose.
- **Conformance corpora** — STD-006/STD-011's bit-identical-across-implementations guarantee is only
  actually verified by testing both peers against the same corpus. `SOW-DER-Stream-Schema-Dedup.md`
  T5 already names a Rust conformance corpus as scoped-but-not-built (§3.6 of the remaining-work
  SOW); this plan extends that to say a Haskell peer needs the same corpus run against it, not a
  separately-authored one, once both codecs exist.
- **JERI reference reading, if the interim (pre-G9) path is taken (Part 2.1):** if both language
  efforts proceed against trunk Java before STD-012 reaches G9, they should read the *same* trunk
  reference and flag the *same* deviations, rather than each independently reverse-engineering
  mux.html v1.1 and risking two different peers disagreeing about undocumented behavior neither one
  has a standard to check against yet.

## Part 4 — The Rust/Haskell material asymmetry, named plainly

The Haskell side of this plan rests on an existing, fairly thorough feasibility investigation
(`investigation-haskell-atomic-der-dedup-cel-feasibility.md`, 10 sections covering codec, laziness
discipline, collections, numeric semantics, transcendentals FFI, strings, dedup, and transport).
The Rust side rests on one narrower document (`der-rust-collection-mapping.md`, collection ordering
only) plus the single gating sentence in `SOW-DER-JERI-CEL-Remaining-Work-2026-07-25.md` §3.2. This
plan has generalized the Haskell findings to Rust wherever §8 of that investigation already says a
finding is language-independent (transport difficulty, CEL structural shape, dedup being the
smallest piece), but that is a generalization, not a Rust-specific investigation — the Rust-specific
questions Part 2 above marks `[OPEN]` (ownership/allocation timing for a read-without-reconstruct
codec path; the transport-crate survey) are exactly the gaps a Rust-side companion to the Haskell
feasibility investigation would close. **This document does not attempt to write that companion
itself** — scoping it, the way `investigation-readahead-filter-rust-haskell-portability.md` was
scoped as a companion to the Java-side read-ahead filter memo, is a reasonable next increment if
the Rust peer work is prioritized ahead of or alongside the Haskell peer work, and is named here as
a candidate follow-on rather than undertaken in this document.

## Non-goals

- Do not implement anything in any language. This document plans and sequences; it does not write
  Rust, Haskell, or Java code, and does not write STD-012's normative prose (that is
  `SOW-JERI-Standard-Modernization.md`'s task, not this one).
- Do not re-litigate the Haskell feasibility verdict or the read-ahead filter's Tier A/B design —
  both are taken as settled per their own companions above.
- Do not attempt to resolve the `[OPEN]` items this document marks (STD-012 G9's single- vs.
  dual-peer proof question; Rust ownership/allocation timing for the codec; the Rust transport-crate
  survey). Each is named as a gap for a future scoping pass, not answered speculatively here.
- Do not assume STD-012 reaches G9 on any particular timeline. This plan sequences around that gate;
  it does not estimate when the gate opens.

## Constraint on how this document's follow-on work is completed

This plan spans two repositories with a stated AI-contribution policy (`CLAUDE.md`, `AI_POLICY.md`,
and — for OpenJDK-adjacent work — `openjdk_ai_policy.md`, all in DirtyChai; the equivalent policy
governs whichever repository a given increment of this plan actually lands in). Any agent, human or
AI, picking up a task named in Parts 2–3 follows that policy without exception: analysis, design
documentation, and a concrete task/finding breakdown are in scope for AI assistance; committing
Rust, Haskell, or Java implementation code, touching a "do not modify" file, or opening a PR are
not, unless the governing policy for that specific repository says otherwise. Where a task above
would require writing normative standard prose (STD-012), that prose-writing itself is scoped
separately in `SOW-JERI-Standard-Modernization.md`, which carries its own completion constraints —
this document does not add to or relax them.
