# Investigation: Rust/Haskell portability of the AtomicSerial DER read-ahead admission filter

- **Status:** scoping / design memo. **No code has been written.** This investigation does not
  implement anything — it determines whether the Tier A/B filter design already proposed for
  Java translates onto the Rust and Haskell peer codec designs, and where it would hook in, without
  assuming those codecs are further along than they actually are.

- **Companions — read in this order:**
  1. `investigation-atomicserial-schema-readahead-filtering.md` — the Java-side design this
     investigation builds on. Read first; do not re-derive its Tier A–D analysis or its
     recommendation (first-class Tier A + thin Tier B; reuse existing machinery for C/D) — take
     that as settled and ask only what changes, if anything, for a non-JVM peer.
  2. `der-rust-collection-mapping.md`, `der-haskell-collection-mapping.md` — the collection-
     ordering discipline mappings for each language's proposed codec. These are the most
     concrete sketches of each codec's shape that currently exist.
  3. `investigation-haskell-atomic-der-dedup-cel-feasibility.md` — the broader feasibility
     verdict for a conformant Haskell peer (codec + stream schema dedup + CEL). Its §2
     "strictness discipline" finding is directly relevant to Question 2 below; read it before
     answering, not after.
  4. `JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md`, `JGDMS-STD-011-CEL-Filter-Expression-
     Format-v0.1-DRAFT.md`.

  **Important framing before starting:** none of the Rust or Haskell material found in this
  repo is landed code — collection-mapping notes and one feasibility investigation are the
  most that exists for either language, both explicitly "research" status. Do not search for
  or assume Rust/Haskell source; ground every claim in what these design docs actually specify,
  and mark `[OPEN]` wherever they don't specify enough to answer a question here — the same
  discipline `investigation-atomicserial-schema-readahead-filtering.md` itself models.

## Why this isn't a "nice to have" port

The Java memo's own recommendation names the stakes precisely, and it's worth restating because
it inverts the usual "port it later" priority: on DirtyChai, Tier A composes as defense-in-depth
*alongside* the SecurityManager-enforced `DeSerializationPermission("ATOMIC")` gate — a second,
independent line of defense. On a Rust or Haskell peer, there is no SecurityManager, no
`ATOMIC` gate, no admission control of any kind today. Tier A isn't additive for those peers —
it is the *entire* admission-control story. That makes this investigation, if anything, more
urgent for the non-JVM peers than the Java increment it follows, not a lower-priority sequel to it.

## Objective

Determine, for both Rust and Haskell: where the "verified schema in hand, before construction"
hook point sits in each language's proposed decode pipeline; whether the Tier A (class-name /
schema-digest allowlist) and thin Tier B (structural/wildcard shape rules) design translates
without modification; and what, if anything, is language-specific enough to need a different
answer than Java's `DerDeserializationFilter` SPI sitting in `ObjectCodec.decodeHierarchy`.

## Specific questions to investigate

1. **Hook location.** The Java hook sits between `MarshalledInstanceRecord.decodeSchemaChainAsResult()`
   (schema verified against `schemaDigest`) and `ObjectCodec.decodeHierarchy` (construction). Do
   the Rust and Haskell codec sketches in the collection-mapping docs imply an equivalent seam —
   a point where the schema chain is verified but no value has been allocated/constructed yet?
   If the docs don't say enough to answer this, say so — don't infer a decode pipeline that isn't
   actually specified anywhere.

2. **Haskell laziness vs. fail-closed reject-before-construction.** The feasibility investigation's
   §2 already establishes that fail-closed decode obligations generally require a strictness
   discipline in a lazy language. Ask specifically: could a filter's `REJECT` verdict be
   observed *too late* — i.e., could laziness let partial construction proceed on an unevaluated
   thunk before the filter's verdict is forced — unless the filter hook is deliberately made
   strict at exactly the same points the base codec's fail-closed obligations already are? Is
   this the same discipline already covered by that investigation's §2, or does the filter add a
   new forcing point that document doesn't already account for?

3. **Rust ownership/allocation timing.** `decodeToFieldMap`'s Java behavior is "no class loaded,
   no constructor invoked" — a reflection-free, allocation-light field map. Does Rust's ownership
   model force an earlier commitment to a concrete typed allocation than that, or can an
   equivalent read-without-reconstruct projection be expressed without it? This determines
   whether Tier A can be exactly as cheap in Rust as the Java memo assumes ("~free, already
   decoded") or carries a different cost profile there.

4. **Digest-verification sequencing.** Confirm whether each language's proposed codec design
   verifies `schemaDigest == SHA-256(leaf record)` — the "schema cannot lie" guarantee — *before*
   any filter hook would run, matching `MarshalledInstanceRecord.java:350`'s Java ordering. If
   the existing design docs don't specify this ordering explicitly, flag it as a gap to close in
   those docs, not just in this one.

5. **SPI/trait/typeclass shape.** Sketch, at design-note level (not implementation), what the
   `DerDeserializationFilter` equivalent would look like idiomatically in each language — a Rust
   trait, a Haskell typeclass or plain function type. This is meant to confirm the shape is
   expressible, not to finalize an API.

6. **Sequencing question.** Given neither base codec is implemented yet, is now — before either
   exists — actually the cheaper time to settle this, compared to Java where the filter is being
   added after the fact onto a already-shipping codec? If so, say plainly that the filter design
   should be folded into each language's base codec design docs as a day-one part of the decode
   pipeline, not scoped as a later addition the way this whole line of work started in Java.

## Non-goals

- Do not implement anything in any language.
- Do not re-litigate Tiers C/D. The Java memo's reasoning that they reuse existing machinery
  (STD-006 §4.5 ceilings; CEL/STD-011) rather than being rebuilt is language-independent — a
  size/depth ceiling and a CEL predicate over a field-map projection don't change shape because
  the host language changed. Take that as settled.
- Do not assess the base Rust/Haskell codec feasibility itself — that question is already
  answered (with caveats) by `investigation-haskell-atomic-der-dedup-cel-feasibility.md` for
  Haskell, and partially by the collection-mapping note for Rust. This investigation asks only
  the incremental question of where/how the filter layers onto codecs already assessed
  elsewhere, not whether those codecs themselves are buildable.

## Deliverable

Written analysis only, in the same style and honesty conventions as its Java-side companion:
cite the specific line/section of a design doc for every claim, mark `[OPEN]` wherever the
existing material doesn't settle a question, and do not present an inferred decode pipeline as
if it were a specified one.
