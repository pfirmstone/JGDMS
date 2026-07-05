# JGDMS Board Reviewer's Guidance

*Consolidated 2026-07-05 from the retirement capstones of four board seats: ASN.1/DER +
module-boundary, adversarial security, JERI transport, and rule-fidelity/determinism/
cross-language. Each seat wrote its own capstone as it retired; this document merges the four
into one reviewer's manual. The four originals are preserved as provenance in
`docs/board-guidance/` (`asn1-der-module.md`, `security-adversarial.md`, `transport-jeri.md`,
`rule-fidelity-determinism.md`) — this document supersedes them as the thing you actually read
before a review.*

---

## 0. What this is, and how to use it

This is the distilled judgment of four retired board members, preserved so a fresh reviewer
inherits their reflexes instead of re-discovering them the hard way. Each seat reviewed dozens
of designs and merge gates over its tenure; what's below is what they concluded actually
mattered — the hazards that recurred, the questions that surfaced real defects fastest, and the
war stories with **the tell** that exposed each one. A tell is the thing you noticed *before* you
had the finding — reproduce the noticing, not just the conclusion.

**Before a review:**
1. Read **Part I (General / Cross-Cutting)** — every one of these principles showed up in at
   least two of the four original capstones independently, which is why they're promoted here.
   They apply regardless of which domain you're reviewing.
2. Read the relevant **Part II domain section(s)** for what's on your desk: §2.1 ASN.1/DER +
   module boundary, §2.2 adversarial security, §2.3 JERI transport, §2.4 rule-fidelity /
   determinism / cross-language. Most real reviews touch two or three at once (a new wire form
   is simultaneously a DER question, a security question, and a determinism question — review
   it as all three).
3. **Always cross-check against the durable specs and decision records, not against this
   document's summary of them.** This guidance cites section numbers and file:line anchors so
   you can re-verify; it is guidance, not normative, and it can go stale. The normative sources
   are: **STD-006** (`JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md`, the DER wire-format spec —
   check you're reading ≥v0.12, the v0.10 copy still shows a withdrawn model), **STD-010** (the
   QUIC-JERI transport spec and its ratified decision record — server-push authorization,
   directional size-bounds, the DGC-ack in-band ruling), the **QUIC decision record** (the
   in-band-ack ruling and the reachable-vs-ephemeral topology reframe that produced the
   server-initiated-stream requirement), and the **type-model memo**
   (`der-type-model-and-element-rule.md`, the closed-subset type algebra + element rule, and its
   sibling `der-collection-ordering-research.md` for the ordering discriminator). When a claim in
   this document and a claim in one of those disagree, **the spec wins**; flag the drift.
4. **Anchor every finding you write to `file:line` or `§section`.** Prose drifts; source and
   spec section numbers don't. The board should be able to re-verify your finding without
   trusting your summary of it — that discipline is why the four capstones below are still
   useful after their authors left.

---

# Part I — General / Cross-Cutting Principles

These recurred across *all four* seats, independently arrived at. They are the reflexes to hold
regardless of what you're reviewing; the domain sections in Part II are where they get applied
with teeth.

### G1. Canonical form is the contract — one encoding per value

DER's entire point is that a value has **exactly one valid encoding**, and a decoder must
**reject** any other encoding of the same value rather than tolerate it. This is not a style
preference; it's the property everything else is built on. Frame every wire-format finding as
"does this preserve, or break, one-encoding-per-value?" — the severity sorts itself once you've
answered that. A codec that *encodes* canonically but *accepts* a non-canonical input on decode
is still broken (ASN.1/DER §H1; security §5.1 — the same defect, found independently by both
seats: a `set:int` of `3,2,1` decoded without complaint because the decoder checked for
duplicates but never checked ascending order).

Canonicity is load-bearing in (at least) four concrete JGDMS places, and a break in any one is a
security *and* correctness bug, never merely cosmetic: signature verification over a TBS,
`@AtomicSerial` schema digests (content addresses), Jini `Entry` byte-matching (matches stored
vs. template bytes *without decoding*), and — the determinism seat's headline finding — DER
byte-equality mirroring a type's own `.equals` contract (rule-fidelity §1.3; JOSS serialises the
*implementation*, DER serialises the *value*, and that's why a `HashSet` and a `TreeSet` of the
same elements produce identical DER bytes but different JOSS bytes).

### G2. Outcome is a claim; mechanism is the proof — check the default

A spec (especially a good one) states the desired security or determinism *outcome* crisply:
"never authorized with the client's own privileges," "reject non-canonical," "the declared type
carries the intent." **A stated outcome is a claim, not a mechanism.** Convert every claim into:
*what concrete, enforced construct guarantees this, and is it actually in the design?* The gap
between a stated outcome and an enforced mechanism is where real defects live — both STD-010
HIGH findings (confused-deputy execution-subject, unsubscribed-push) were exactly this gap, and
so was the DER "any" review (the memo said "reject-non-canonical still applies" but was silent on
the depth bound and the ATOMIC gate).

The sharpest version of this check: **ask what the naive/default implementation does.** If the
obvious, unguided implementation would violate the claim, the spec must *positively mandate* the
enforcement, because a property that isn't actively required won't happen by itself. The
confused-deputy execution-subject finding turned entirely on this — the *default* JERI/JAAS
execution context inside the client process is the client's own ambient subject, so "never the
client's privileges" is an active requirement, not a passive one.

### G3. Verify independently — don't trust the self-report

A green test suite is evidence of nothing about the input it doesn't exercise. **Empty search ≠
absence. Green compile ≠ correct. Passing tests ≠ the property holds.** When you can run it, run
it, against the real runtime path — not a self-built oracle:
- **Reproduce tallies yourself** on the correct toolchain, and confirm which commit actually set
  the green state (a docs-only top commit doesn't).
- **Brute-force properties instead of eyeballing them.** A comparator's correctness is a property
  to be *proven*, not a code shape to be recognized — the octet-sort comparator was verified by a
  harness over an adversarial byte universe checking antisymmetry, transitivity, and TimSort
  acceptance across 200 shuffles; eyeballing "looks unsigned" would not have caught a signed-byte
  bug.
- **Run the adversarial input against the built classes.** A probe that fed a reverse-sorted
  `set:int` to the decoder and *observed* the wrong output turned a suspicion into a confirmed
  finding with a concrete failure trace.
- **Verify a proposal's factual claims about the codebase against trunk**, not against the
  memo's self-description — a memo can be stale; the codebase is authoritative. A coordinator can
  hand you a premise (a cited commit, a "this is how it works") that turns out superseded; check
  the actual HEAD and report the discrepancy rather than the stale premise.

### G4. Declaration-is-the-signal — no annotations that restate a fact

Every faithful rule keys on **what the developer already wrote** — the declared class, the
declared generic signature — never a runtime instance, never a hash, never an annotation that
restates a declaration. `disciplineFor(Class)` reads the declared collection class; the element
rule reads the declared generic signature via `Field.getGenericType()` (a compile-fixed,
digest-stable artefact — erasure erases *instances*, not *declarations*, so this is exactly the
mechanism Jackson/Gson/Spring already rely on).

**Any place a developer restates a fact is a place the restatement and the fact can drift, and
the annotation lies when they do.** An `@DerElement(Foo.class)` on a `Set<Foo>` field restates
`Foo` — reject it, derive it by rule instead. The one genuine exception (a type variable in a
generic class) is proof the annotation was never the answer: there's no concrete type at the
declaration site for the annotation to name either, so it degrades to the `Any` fallback by rule.
Also watch the mirror-image hazard: a rule that sniffs `value.getClass()`, `hashCode()`, or
hash-bucket iteration order to decide *wire structure* has drifted from declaration to instance —
the determinism guarantee rests on ordering never being a function of a hash, and JGDMS enforces
this with a source-scanning test that fails if the collection-wire-type code so much as calls
`hashCode()`.

### G5. Structure on the wire, behaviour in the constructor — the two-layer split

JGDMS splits every value into **Layer 1 (wire/schema)** — structural facts: ordering discipline,
element type, nullability-as-shape — baked into the schema token and digest-covered; and **Layer
2 (`check(GetArg)` / constructor)** — semantic facts: uniqueness, null-*policy*, cross-field
invariants, typed coercion. Neither layer reads instance generics; Layer 1 reads declarations,
Layer 2 reads the developer's own typed constructor call — which is *why* erasure never enters
the path. When a proposal seems to need reflected instance type-arguments, or puts a behavioural
contract on the wire, or puts a structural fact in the constructor, it has almost always
misplaced a Layer-1/Layer-2 concern; push it to the correct layer and the "problem" usually
evaporates.

The same split shows up in the security domain as **where attacks live**: a form that hands
Layer 2 something it can't safely validate, or that silently shifts a guarantee from the wire to
developer discipline (the `Any` element-type downgrade — schema no longer commits per-element
type, so a constructor relying on homogeneity must now validate element types itself) is exactly
the seam to distrust.

### G6. Fail-closed on ambiguity, always

The security default is refusal, and a review's job is to check the *default branch*, not the
happy path:
- Delivery status unknown → treat as "maybe processed" (no auto-retry), never "safe to retry."
- Peer identity unprovable, or a canonical-form check ambiguous → reject, don't tolerate.
- Can't confirm a socket/resource is one you own → don't delete or reuse it.
- An unrecognised transport, tag, or wire form → reject, never silently pass through.
- A `bag:`/canonicalise-vs-preserve classification you're not sure of → the wrong call in the
  lenient direction (e.g. classifying a priority queue as a `set:` and silently dropping
  legitimate duplicates) is exactly the class of bug this discipline exists to prevent.

**But also check that a fail-closed *decision* doesn't leave a fail-open *side effect*.** The UDS
hardening made a permissions gate throw fail-closed, but closing the channel on that error path
didn't unlink the already-bound socket file — leaving exactly the world-accessible artifact the
gate existed to prevent. Trace the *cleanup* path of every security throw, on every platform (a
null `fileKey` on Windows, a null `InetAddress`, hid gaps the POSIX path didn't show).

### G7. Repair, not amputate; report faithfully

Rank findings by real exploitability with a concrete scenario each; distinguish `[verified]` /
CONFIRMED (you re-checked against current source or ran the adversarial input) from `recommended`
/ PLAUSIBLE (you infer it). Say plainly when a mechanism is genuinely sound (AEAD anti-splicing
on connection migration, for instance) so the team doesn't build redundant defense on top of it —
a finding with no concrete failure scenario is noise, and a blocking call with no exploitability
is over-reach in the other direction. When a coordinator's premise turns out stale, say so and
review the actual HEAD rather than silently substituting it.

Prefer the fix that makes the invariant **true by construction** over the fix that enforces it by
check: an `[n] IMPLICIT`-tagged `CHOICE` beats a `SEQUENCE{tag,body}` with a redundant
discriminator not because it's prettier, but because it makes the lying encoding
*unrepresentable* — there's no second discriminator left to disagree with the body. Construction
beats vigilance whenever you can buy it.

### G8. All sources of truth must agree

A wire standard typically has three independent sources of truth — prose spec, machine-checkable
module (ASN.1, IDL, schema), and the built code. It is only conformance-ready when all three say
the same thing; a divergence between any two is a latent interop bug waiting for someone to build
against the wrong one. Sync them as one mergeable unit rather than patching one and trusting the
others still apply. The same discipline applies to citations: a `§x.y` reference into a
dependency is load-bearing and propagates — open the dependency and confirm the section actually
says what the citing document claims. A wrong citation that everyone copies from the same source
document becomes "true by repetition" if nobody checks it.

### G9. Trust the documented artifact — normative specs are the interop test

A hand-owned, security-critical, cross-language wire format needs a *normative* spec before it
ships, and the test of whether the spec is actually done is whether a peer could implement it
"from the doc alone" without access to your source. This is also why cross-language claims need
active testing, not hopeful extrapolation: map the rule's categories onto a second runtime's type
system and check whether the *hard* case — a tension, a reversal, an edge case — reproduces
there too. A rule that's a language quirk classifies "the same" type differently across runtimes;
a rule that's genuinely semantic reproduces its awkward cases everywhere (the `LinkedHashSet`/
`LinkedHashMap` byte-equality-vs-`.equals` tension reappearing in the analogous Rust types was the
strongest evidence the ordering rule is semantic, not a JVM artifact).

---

# Part II — Domain Sections

## 2.1 ASN.1 / DER Wire-Format & Module-Boundary

*Distilled from seven sequential board reviews (2026-07-04/05): the STD-006 v0.13 ASN.1 module +
spec edits, the UDS transport security review, the DER collection-codec conformance ruling, the
module+spec sync, the QUIC ACC/export board review, the DirtyChai export-surface location map,
the DER closed-subset type-model review, and the STD-010 conformance-gate verdict.*

**The one sentence:** DER means exactly one valid encoding per value — anything that admits a
second encoding of the same value, or fails to reject a non-canonical one on decode, is a bug,
even if it round-trips (see G1). Almost every defect below is that invariant wearing a different
costume.

#### Hazards & traps (in the order to check them)

1. **Non-canonical encodings that round-trip and look fine (H1).** A decoder that checks
   duplicates but not order will accept a hand-crafted `set:int` of `3,2,1`. Rejecting
   non-canonical order on decode is **mandatory, pre-merge** (X.690 §10.1 unique encoding + §11.6
   SET OF restriction + STD-006 principle 6 fail-secure) — leniency merged is leniency calcified,
   and the green suite hides the gap because no test feeds unsorted bytes. *WS-1:* the collection
   codec's `decodeSetOrList`/`decodeMap` had exactly this gap; the fix was ~6 lines + one test.

2. **Distinct-tag violations on OPTIONAL/CHOICE (H2, X.680).** Every OPTIONAL component in a
   SEQUENCE needs a tag distinct from whatever can immediately follow it; every CHOICE arm needs a
   distinct tag among siblings. Two adjacent same-shape OPTIONALs, or bare (untagged) CHOICE arms
   over universal types, are mechanically invalid — a real ASN.1 tool rejects them, so there's no
   excuse for shipping one. *War stories:* `Permission{name,actions}` both `UTF8String OPTIONAL`
   and `ReducingDomainRecord` arms, fixed with `[n] IMPLICIT` context tags (STD-006 §4.5); the
   closed-subset `Any` CHOICE naively collides `atomicObject` (0x30) with a `collection` arm
   (0x30) and all INTEGER scalars (0x02) — fixed with one `[n]` tag per category.

3. **Redundant discriminators (H3).** If the wire states the category twice — an explicit
   tag/enum *and* the body's own intrinsic universal tag — the two can disagree, which is exactly
   the non-canonical surface DER forbids. *WS-4:* a proposed `AnyElement ::= SEQUENCE{tag
   ENUMERATED, body Element}` both mis-modelled a tag-varying open type as an `OCTET STRING` stub
   *and* let `ENUMERATED tag` re-encode what the body's own tag already said. Recommendation:
   `[n] IMPLICIT`-tagged CHOICE keyed on category — one discriminator, distinct-tag-clean, no
   wrapper, smaller on the wire, and the octet-sort's category grouping becomes a *provable*
   consequence of the leading tag rather than an accident of framing.

4. **IMPLICIT that silently overwrites a load-bearing universal tag (H4).** IMPLICIT replaces the
   tag in place — right for most fields, but illegal on a CHOICE/ANY alternative without an
   explicit tag (X.680 §31.2.7), and wrong wherever the universal tag is itself a discriminator
   you need. *War story:* the `Any` `collection` arm must be `[n] EXPLICIT` so the inner `SET OF
   0x31` / `SEQUENCE OF 0x30` distinction survives; conversely `ReducingDomainRecord` arms tagged
   `[0]/[1]/[2] IMPLICIT` was correct and *did* change the wire bytes (`A0 02 05 00` → `80 00`) — a
   real change a round-trip test must expect, not a stylistic one.

5. **EXPLICIT-vs-IMPLICIT module default treated as inert (H5).** "Both TAGS modes produce
   identical bytes" is only true if *every* context tag carries an explicit qualifier — the
   moment a bare `[n]` exists, the module default governs and bytes differ. The STD-006 module
   header originally claimed byte-equivalence "for every type"; `ReducingDomainRecord`'s bare
   tags were the counterexample, and the claim was retracted (§4.6 now states EXPLICIT is the
   defensive default and every tag is written out explicitly).

6. **"The transport already bounds it" (H6).** A lower layer bounding *bytes* is not the same as
   bounding *decoded element counts* — a few flow-controlled bytes can decode into a huge or
   deeply nested structure. STD-006 §4.5 pins `maxCollection`/`maxFields`/`maxStackFrames`/
   `maxCauseDepth`/`maxDomains` and the codec caps all six decode loops (65536 accepted, 65537
   rejected). *WS-6:* STD-010's §8.2 size-bound MUST was written only for the client-initiated
   frame and didn't explicitly cover the client *decoding a server-pushed* body — the new decode
   surface server-initiated streams opened. Bounds apply to every decode surface, in both
   directions; "authenticated sender ⇒ trusted bytes" is the reverse-direction version of the same
   fallacy (see also §2.2 hazard 1.6).

7. **Map/SET-OF canonical sort: sorting the wrong object (H7).** A `SET OF` sorts complete
   element encodings (§11.6); a `Map` (`SET OF SEQUENCE{k,v}`) must sort by the *encoded key
   only* — sorting the whole entry TLV lets the value bytes perturb order, so two conformant
   encoders disagree cross-implementation even though each is internally deterministic. *WS-3:*
   fixed from `octetSort(entryTlvs)` to sorting by a parallel `keyTlvs` list. Note the
   prefix-of-one-another case for distinct keys does *not* break either variant (length octet
   diverges first) — this is a cross-implementation-agreement hazard, not a total-order hazard;
   they have different fixes, know which one you're looking at.

8. **The `org.apache.river.api.security` split-package landmine (H8).** This package exists in
   *both* `java.base` (DirtyChai embeds ~11–12 authorization-grant classes, exported unqualified)
   *and* `jgdms-platform` (the full ~35-class set), with overlapping class names — the documented
   source of the "recompile `--release 21` silently emits the embedded copy →
   `NoSuchMethodError`" trap. Never add to it; a new `java.base` addition goes under
   `au.zeus.jdk.*`. *WS-5:* the QUIC-TLS facade home question rejected this package for two
   independent reasons (split-package risk *and* category mismatch — authorization ≠ TLS
   plumbing), landing on `au.zeus.jdk.net.ssl` (java.base-only, split-free by construction).

#### Review heuristics, in cost/severity order

1. Is there exactly one encoding per value, and does decode reject the others? (H1 — the master
   question; if the decode path only checks duplicates, you've found the defect.)
2. Does the module compile under a real ASN.1 tool, and are all OPTIONAL/CHOICE tags distinct?
   (H2 — mechanically detectable, no excuse for shipping a violation.)
3. What is the leading octet after every IMPLICIT/EXPLICIT edit? (H4 — a round-trip test that
   only checks `decode==input` will pass on a wrong tag if both ends agree wrongly; read the tag
   byte, don't just round-trip.)
4. Is the discriminator stated more than once? (H3 — prefer a single tagged CHOICE.)
5. Sort the right object — SET OF sorts whole TLVs, Map sorts by encoded key only. (H7.)
6. Does the size-bound survive this transport/direction change — is there a new decode surface,
   and does the SIZE ceiling explicitly cover it? (H6 — the MUST must be directional or an
   auditor can't confirm it.)
7. Where does this package live, and does it live anywhere else? (H8.)
8. Is the derived wire-type digest-covered and deterministic — a pure function of declarations,
   never instance state or erased-generic reflection? (WS-7 below.)
9. Are the citations right — open the dependency, confirm the section says what's claimed (G8).

#### JGDMS-specific gotchas

- Split-package landmine (H8): never add to `org.apache.river.api.security`; new `java.base`
  goes under `au.zeus.jdk.*` (`au.zeus.jdk.net.ssl` for TLS, `au.zeus.jdk.authorization.*` for
  authz).
- DER canonical-decode invariants (STD-006 principles 2 + 6) mandate *rejecting*: non-canonical
  SET OF order, wrong container tag, disallowed duplicates in `set:`/`orderedset:`/`map:`/
  `orderedmap:` (`bag:` keeps dups), over-`maxCollection` counts, `AlgorithmIdentifier` with
  `parameters` present (RFC 5280/8702 fail-secure), and a `schemaDigest` mismatching
  `schemaBytes`. Every one of these is a reject, never a tolerate.
- The six collection productions + Option A tags: canonicalise (`set:`/`bag:`/`map:`) → `SET OF`
  tag **0x31**, §11.6 octet-sorted, decoder rejects out-of-order; preserve (`orderedset:`/
  `list:`/`orderedmap:`) → `SEQUENCE OF` tag **0x30**, iterator order verbatim; Map is `SET OF
  SEQUENCE{key,value}`, entries sorted by encoded key. All fields `SIZE(0..maxCollection)`
  inclusive (exactly 65536 accepted). Declaring `SET OF` gets §11.6-order enforcement for free
  from any conformant DER tool.
- IMPLICIT-vs-EXPLICIT (STD-006 §4.6): module is `DEFINITIONS EXPLICIT TAGS`; every context tag
  is written `IMPLICIT` explicitly. Never claim the two TAGS modes are byte-identical once a bare
  `[n]` exists.
- Digest coverage: the schema wire-type token is part of `schemaBytes`, committed by
  `schemaDigest` (STD-006 §7.8) — any element-type derivation landing in the token must be a pure
  function of declarations, so cross-language decoders agree by consuming the token, never JVM
  reflection. Always verify `schemaDigest` against `schemaBytes` before use — it's a routing hint,
  not an integrity claim on its own.
- The ASN.1 comment trap: `--` opens *and closes* a comment in X.680; a second `--` on the same
  line reopens it, and text after can become live grammar. An em-dash typed as `--` broke a
  module compile. Use `;`/`:`/a real em-dash instead.
- Python toolchain: `C:\Users\peter\AppData\Local\Programs\Python\Python311\python.exe` (bare
  `python`/`py` on PATH are broken Windows Store aliases). Recompile+round-trip gate:
  `python -c "import asn1tools; s=asn1tools.compile_files(['JGDMS-STD-006-v0.13.asn1'],'der'); print('types',len(s.types))"`.
  A green compile proves distinct-tag/grammar validity, **not** canonical-decode correctness
  (H1) — always also encode→decode the changed arms, assert `decode==input`, and read the
  leading tag byte.
- STD-006 §7.2 is the ACC reducing-domain transport (`AccessControlContextRecord`/
  `ReducingDomainRecord`, from v0.12+); §7.3 is `DigestCodeSourceRecord` (code identity). SOWs
  have repeatedly miscited the ACC block as "§7.3" — check the number, and check the citing doc
  references STD-006 ≥ v0.12 (the v0.10 in-tree copy still shows the withdrawn
  `DomainIdentityRecord`/`AccessControlContextSerializer` model).

#### War stories (defect + the tell)

- **WS-1 — reject-non-canonical on decode.** *Tell:* a `== 0` duplicate check with no ascending-
  order check; a hand-crafted `set:int` of `3,2,1` decoded clean. Mandatory, pre-merge (X.690
  §10.1/§11.6 + principle 6) — accepting it breaks the receiver's decode→re-encode round-trip,
  the foundation under signatures, digests, and Entry byte-matching.
- **WS-2 — UDS fail-closed leak.** *Tell:* `restrictPermissions` threw fail-closed after a
  successful bind, but the `if (!ok)` finally only closed the channel — it didn't unlink the
  just-bound socket file, leaving an unprotected bound socket on disk. A security fix can open a
  new hole on its own error path; trace the failure path, not just the happy path. (See G6.)
- **WS-3 — map sorted by whole entry, not key.** *Tell:* `octetSort(entryTlvs)` instead of a
  parallel `keyTlvs` list. The prefix-of-distinct-keys case doesn't break total order (length
  octet diverges first) — cross-implementation-agreement hazard, not total-order hazard, and they
  have different fixes. Also: the coordinator's cited commit predated the actual HEAD fix —
  reviewed HEAD and reported the discrepancy (G3).
- **WS-4 — `Any` as `SEQUENCE{ENUMERATED tag, body}`.** *Tell:* an explicit `ENUMERATED tag`
  bolted onto a body whose universal tag already discriminates the category — redundant
  discriminator admitting a lying encoding, plus a `body Element` stub mis-modelling a
  tag-varying open type. Recommendation: `[n] IMPLICIT CHOICE`, collection arm `[n] EXPLICIT`.
- **WS-5 — QUIC-TLS facade home + trust-dispatch.** *Tell:* a proposal to home a `java.base` TLS
  facade near authorization code. Rejected `org.apache.river.api.security` (split-package +
  category mismatch) and `javax.net.ssl` (squatting the standard namespace); homed at
  `au.zeus.jdk.net.ssl`. Separately verified the 2-arg `checkServerTrusted` *does* perform full
  SPIFFE/X.500 peer-auth (not a stub) but drops algorithm-constraint enforcement — hence the 3-arg
  `SSLEngine`-adapter path was the right ratified choice; a fail-open missing-`else` on an
  unrecognised transport was elevated to a REQUIRED fix.
- **WS-6 — STD-010 directional DER-bound gap + §7.2 citation.** *Tell:* §8.2's size-bound MUST
  was written entirely for the client-initiated frame, and rev.2 had just added server-initiated
  streams — making the client a new decode surface §8.2 didn't name. Also verified all five §7.3
  occurrences were now corrective references, zero live miscites remaining. Verdict:
  conformance-ready as a P3 gate; fold the both-directions bound sentence in on the next pass.
- **WS-7 — closed-subset type algebra + `getGenericType()`.** *Tell:* a claim that "erasure means
  we can't get `Set<X>`'s `X`." Correction: erasure erases instances, not declarations —
  `Field.getGenericType()` reads the compile-fixed Signature attribute, digest-stable and
  cross-runtime-reproducible (same mechanism Jackson/Gson/Spring use). The one irreducible
  boundary (a type variable in a generic `@AtomicSerial` class → resolves to `Any`) is an honest
  boundary an annotation couldn't fix either.

#### One-page checklist (ASN.1/DER)

- [ ] Exactly one encoding per value; decode rejects the others (order, dup, wrong tag)?
- [ ] Module compiles under `asn1tools`; all OPTIONAL/CHOICE tags distinct?
- [ ] Leading tag byte checked after every IMPLICIT/EXPLICIT edit, not just `decode==input`?
- [ ] No redundant discriminator (tag/enum *and* intrinsic body tag both stating the category)?
- [ ] SET OF sorts whole TLVs; Map sorts by encoded key only?
- [ ] Size-bound explicitly covers every decode surface in both directions, including new
      transports/directions?
- [ ] New `java.base`/shared-module class checked against the split-package landmine?
- [ ] Derived wire-type token is a pure function of declarations, digest-covered, deterministic?
- [ ] Every `§x.y` citation opened and confirmed against the actual dependency?

---

## 2.2 Adversarial Security

*Distilled as the retirement capstone of the adversarial-security board seat (2026-07-05). Cites
findings from the collection-codec merge gate, the DER "any" element-form review, the QUIC-TLS
endpoint review, and the STD-010 QUIC-JERI transport review (v0.1 → rev.3).*

**The one-sentence job:** assume the peer is hostile *after* a valid handshake, and the bytes are
hostile *always*; check that every trust the design extends is bound by an enforced mechanism,
not an assumption or a stated intention.

#### Attack shapes to hunt for, by name

1. **Confused-deputy (the highest-value hunt on any authz-bearing transport).** A component with
   authority X is induced to act on behalf of a party with authority Y, and executes with X.
   *Tell:* the design specifies which identity the authorization *check* uses, but is silent on
   which identity the *execution* runs under — those are different questions (STD-010 §4.7, WS
   §5.4).

2. **Role-reversal authorization (a new trust direction).** Any design that adds a new *direction*
   of request — server-push, callbacks over a client-opened connection, a mirrored gate — inverts
   the standing assumption "caller = connection initiator." Attack it hardest: who is the caller
   on the reversed path, whose privileges execute, whose identity is stamped, and can the
   newly-trusted direction make the *other* party act under the wrong identity? (STD-010 §4.6/
   §4.7 — the server-push role reversal was the single richest finding surface in any review.)

3. **Decode-surface DoS.** Untrusted bytes drive allocation, recursion, or quadratic work *before*
   any semantic check runs. Three sub-shapes: **unbounded recursion** (no depth counter on the
   recursive path — the nested-`Any` StackOverflow, WS §5.2); **unbounded/uncapped allocation**
   (no `maxCollection` cap before building the container, WS §5.1); **algorithmic amplification**
   (O(n²) work on n attacker-supplied elements within a legitimately-sized payload, WS §5.1). The
   invariant: bytes-in-flight bounds are not decoded-structure bounds — a transport window caps
   bytes, and a small number of bytes can decode into a huge or deeply nested structure. This
   discipline is undiminished by any transport flow control (see §2.1 hazard 6).

4. **Ungated reconstruction doors ("second door to the same machinery").** A new wire form or tag
   that reaches an object-reconstruction mechanism bypassing the gate the primary path goes
   through. *Tell:* the primary `@AtomicSerial` path runs the `DeSerializationPermission("ATOMIC")`
   gate + endpoint `ResolutionContext` + `check(GetArg)`; a new form (an `Any` tag=
   atomicSerialObject body) reaches the same reconstruction, and the design never states the gate
   applies to it (WS §5.3). A polymorphic/self-describing form that lets the *wire* name the class
   to reconstruct is exactly where capability escalation hides.

5. **Side-channels and non-canonical-form escapes.** Two encodings of the same value break
   byte-equality and signature stability (see G1) — brute-force the comparator, don't trust "it's
   sorted" (WS §5.5). A true canonical decoder must *reject* non-canonical input, not merely
   produce canonical output — silently accepting unsorted/non-minimal input is a fail-secure gap
   even when the decoded object is value-correct. Timing/correlation channels: prefer a design
   whose correlation is *structural and unforgeable* over one carrying an explicit correlator a
   malicious peer can misattribute (WS §5.6).

6. **The "authenticated peer ⇒ trusted bytes/actions" fallacy.** Authentication is necessary but
   not sufficient for authorization. A peer that completed a valid mTLS handshake can still send
   malformed/oversized decode input (auth doesn't sanitize bytes), invoke operations it was never
   authorized for (handshake ≠ per-operation authz), push events it was never subscribed to (WS
   §5.4b), or be compromised *after* the handshake (identity is pinned; behaviour isn't).
   Whenever a design leans on "the peer is authenticated," ask what *additional* binding
   authorizes this specific action — if the only gate is "is authenticated," that's a hole.

#### Review heuristics — the outcome→mechanism conversion (see G2)

1. Find the security claim (a MUST/MUST NOT, a "guard"/"invariant").
2. Name the mechanism that would enforce it — a depth counter checked before recursion, a
   permission check against a specific ACC, a `Subject.doAs` boundary, a strictly-ascending order
   assertion on decode, a keyed MAC.
3. Search the design for that mechanism. If it's there and correct, discharged. If the spec
   asserts the outcome but the mechanism is absent, wrong, or "left to the implementer" — that's
   the finding. This single heuristic produced both STD-010 HIGH server-push findings.
4. Check the default (G2): does the claim hold if the implementer does the obvious thing, or does
   it require positive enforcement the spec must mandate?

Where to look first: the two-layer boundary seam (G5); every recursive decode path (is depth
threaded on each recursion, and is recursion bounded by the fixed schema token or by attacker-
controlled wire data — when dispatch moves from token to bytes, old depth-safety assumptions
silently die, §2.2 hazard 3); every new tag/union/self-describing form (unknown tag → reject?
tag/body type-consistency validated? routed through the same gate as its typed equivalent?);
every new request/trust direction (re-derive caller, execution subject, stamped principal); every
"rely on the platform" claim (some are true and load-bearing — AEAD anti-splicing genuinely
prevents peer-splice on migration, don't invent redundant mechanism; some are false comfort —
flow control ≠ decode bounds, "not implemented yet" is fail-*open*-by-absence, not
fail-closed-by-construction — distinguish them).

Independent verification (see G3): reproduce the tally yourself on the correct toolchain; check
another agent's build isn't already running (shared-toolchain wedge); brute-force properties;
run adversarial input against built classes; empty search ≠ absence, compile ≠ validated.

#### JGDMS-specific gotchas

- **`DeSerializationPermission("ATOMIC")` gate.** Before any `@AtomicSerial (GetArg)` constructor
  runs, every class in the hierarchy whose constructor will execute must have the permission
  granted to *its protection domain* (checked against an ACC built from the classes' domains — the
  grant sits with the class's codebase, not the caller's). **Critical caveat: it is a NO-OP when
  no SecurityManager is installed** — never rely on it as the sole gate in an SM-less deployment;
  the disciplined decode bounds + `check(GetArg)` must stand on their own. Hunt for any *new*
  reconstruction door that reaches the `@AtomicSerial` path without routing through
  `checkAtomicDeSerializationPermitted` and the endpoint-assigned `ResolutionContext` — a second
  ungated door negates the whole point of the gate. Class resolution must always use the
  endpoint-assigned loader, never the thread-context loader (ambient-resolution failures: wrong
  local copy, same-name conflicts, breaks under OSGi).
- **Ambient-subject leakage (the JGDMS confused-deputy).** Code dispatching a remote-requested
  action runs inside a process with its own ambient Subject/ACC. On a server dispatching a client
  request, this is handled (dispatch runs as the client). On the reversed path (client
  dispatching a server-pushed event) it is *not* automatic — the default execution context is the
  client's own, so the design must positively require the execution-subject swap. "The permission
  check uses the server identity" is not enough; the *execution* must run as the server. Related:
  a privileged op not wrapped in `doPrivileged` goes viral up the stack to the constrained
  infrastructure ACC — cure is `doPrivileged` at the responsible frame, never a broader grant; and
  never broadcast ClassLoaders/capabilities through a channel the whole object graph reads (e.g.
  `getObjectStreamContext`) — use a narrow guarded channel.
- **Outcome-vs-mechanism is the dominant JGDMS defect class** precisely because JGDMS specs are
  high-quality and state outcomes precisely — the claim reads as done, but the enforcing construct
  is missing or deferred. When a JGDMS spec states a security outcome, assume the mechanism is
  absent until you find it, then confirm it's tested with a **negative** assertion, not just the
  happy path.
- **Canonical-form load-bearing sites** (see G1): signature verification over a TBS,
  `@AtomicSerial` schema digests, Jini Entry byte-matching. Any new element form must preserve
  octet-sort over complete TLVs, minimal tag/length encoding, and a deterministic value→encoding
  mapping.
- **Toolchain traps for the verifying reviewer:** build/run on the DirtyChai JDK for `jgdms-der`
  (`--release 25`, needs the SM-capable JDK to run); confirm `JAVA_HOME` via `mvn -v`. Check for
  another agent's running `mvn`/`java` before you build — shared toolchain, concurrent Maven
  builds wedge; redirect test output to file to avoid the surefire console-flush wedge. A green
  reactor can mask a real problem or be masked by an unrelated env flake — validate the module in
  isolation and confirm which commit actually set the green state.

#### War stories (defect + the tell)

- **§5.1 Collection codec: non-canonical input accepted + O(n²) + no size cap.** *Tell:* decode
  checked `compareOctets(prev,cur)==0` (dup) but never `<0` (order) — canonical *output* but not
  reject-non-canonical *input*. Confirmed by compiling a probe that fed a reverse-sorted `set:int`
  and observing `[3,2,1]` accepted. Fix: strictly-ascending decode check (subsumes dup, O(n)),
  `bag:` non-decreasing, `MAX_COLLECTION=65536` on all six loops, Option-A tag rejection both
  directions — re-verified independently against built classes, not the branch's own tests.
- **§5.2 DER "any": nested-`Any` StackOverflow (depth-through-data).** *Tell:* the existing
  decoder doesn't increment `depth` on nested-collection recursion — safe only because the
  element wire-type was a fixed, digest-covered schema token (nesting statically bounded by the
  token string). The proposed `Any` form breaks exactly that: with `set:Any`, each element's body
  carries its own discipline, so decode dispatch is driven by attacker-controlled wire data, not
  the fixed token — a `set:Any` nested thousands deep recurses to StackOverflow before any
  `check()` runs, and `maxCollection` doesn't help (depth, not breadth). Heuristic: when dispatch
  moves from a fixed digest-covered token to attacker bytes, every bound relying on the token
  silently dies.
- **§5.3 DER "any": ungated `@AtomicSerial` door + tag/body confusion.** *Tell (a), HIGH:* an
  `Any tag=atomicSerialObject(20)` body reaches `@AtomicSerial` reconstruction and can name any
  schema-digest/class the attacker chooses — the memo never stated it goes through the same
  ATOMIC gate + ResolutionContext + `check(GetArg)` as the typed path. "Self-describing form where
  the wire names the class" is a reflex trigger to ask whether it goes through the typed path's
  gate. *Tell (b), MEDIUM:* tag/body type consistency and unknown-tag handling weren't specified
  as fail-secure rejects; `AnyTag` ENUMERATED must be canonical (minimal) or two byte-forms of the
  same value break byte-equality. *Downgrade note:* `Any` shifts per-element type validation onto
  the developer's `check()` — the memo must flag this so a constructor relying on `Set<Foo>`
  homogeneity validates element types when the field can be `Any`.
- **§5.4 STD-010 server push: execution-subject swap (outcome-vs-mechanism HIGH).** *Tell:* §4.7
  correctly *named* the confused-deputy and said a pushed event must be authorized as the server
  — but dispatch runs inside the client process, on client threads, under the client's ambient
  Subject/ACC. The spec specified which identity the *check* uses, silent on which identity
  *execution* runs under. Fix (verified rev.3): §4.7 rule 5 mandates dispatch **execute** under
  the server's subject via `Subject.doAs`/`callAs` + reduced ACC; harness asserts the execution
  subject with a no-client-leak negative.
- **§5.4b STD-010 server push: subscription-correlation ("authenticated ⇒ trusted" HIGH).** *Tell:*
  the only gate was "is the peer authenticated" — an authenticated (or post-compromise) mesh peer
  could push forged events the client never subscribed to. Fix (verified rev.3): §4.6.3 requires a
  push be dispatched only to a listener the client holds an outstanding subscription for, bound to
  ⟨authenticated peer, connection, listener⟩; unsubscribed push rejected fail-closed; §9.5 clarifies
  this subscription-security admission is not the forbidden application-level flow-control
  rationing.
- **§5.5 Collection codec: §11.6 octet-sort comparator, brute-force over eyeball.** *What:* a
  harness over an adversarial byte-array universe checked antisymmetry, transitivity,
  zero-iff-equal, and TimSort acceptance across 200 shuffles, rather than reading the comparator
  and concluding "looks correct." A signed-byte bug (`byte` is signed in Java, needs `&0xFF`)
  would surface as an antisymmetry/transitivity violation; a bad trailing-zero tie-break would
  surface as a TimSort contract exception. A comparator's correctness is a property to prove, not
  a code shape to recognize.
- **§5.6 STD-010 DGC ack: in-band vs separate-stream (structural correlation wins).** *Ruling:*
  in-band is more secure. In-band binds the ack to exactly one request by the stream it arrives
  on — unforgeable, structural. A separate ack stream needs an explicit request-correlator, a
  forgeable field a malicious peer can misattribute (ack A while claiming B → advance DGC lease
  state for an unprocessed request); it's also a mux-like control channel reintroduced, doubles
  `MAX_STREAMS` pressure, and its timing leak is no better than in-band's. Fence: the in-band
  marker must be position-defined (recognized only at the exact post-response-object position),
  never content-scanned. Lesson: prefer the design whose correlation is structural and
  unforgeable over one carrying an explicit correlator a peer can lie about.

#### One-page checklist (adversarial security)

- [ ] **Decode DoS:** every recursive path threads a depth bound bounded by a fixed digest-covered
      token (not attacker bytes)? counts capped before allocation? any O(n²) on attacker n?
- [ ] **Canonical form:** decoder rejects non-canonical, not just emits canonical? comparator
      brute-forced?
- [ ] **Reconstruction doors:** every path routed through the ATOMIC gate + ResolutionContext +
      `check(GetArg)`? any wire-named/self-describing class slot? unknown-tag / tag-body
      consistency fail-secure?
- [ ] **New trust directions:** caller re-derived by initiator? execution subject swapped (doAs),
      not just the check subject? can the newly-trusted side make the other act under the wrong
      identity?
- [ ] **Authenticated ≠ trusted:** every trust bound to a specific resource (subscription,
      capability), not merely "is authenticated"? rejected fail-closed?
- [ ] **Platform-reliance claims:** true and load-bearing, or false comfort? "not implemented yet"
      = fail-open-by-absence?
- [ ] **Outcome→mechanism:** every MUST/guard → named enforcing construct → tested with a
      NEGATIVE assertion?
- [ ] **Independent verification:** tally reproduced, properties brute-forced, adversarial input
      run against built classes, which commit set the green state?

---

## 2.3 JERI Transport

*Distilled by the JERI-transport reviewer who did the UDS hardening, the pre-deletion mux audit,
the server-init investigation, and the STD-010 verdict. Anchors to re-verify (trunk):
`Session.java`, `MuxClient.java`, `MuxServer.java`, `net/jini/jeri/{OutboundRequest,
InboundRequest,BasicObjectEndpoint,BasicInvocationDispatcher}.java`,
`net/jini/jeri/connection/{Connection,ServerConnection}.java`,
`net/jini/jeri/ssl/{SslConnection,SslServerEndpointImpl}.java`, `net/jini/jeri/tcp/TcpEndpoint.java`,
`net/jini/core/event/RemoteEventListener.java`; `docs/JGDMS-STD-010-QUIC-JERI-Transport-*.md`
(§4.0/§4.2/§4.6/§4.7/§5).*

**The one thing to internalise:** a JERI transport is thin at the SPI but load-bearing in its
contracts. `net.jini.jeri.tcp` is three classes — that thinness is a trap, because it makes a new
transport *look* like "swap the byte layer, done." The `Endpoint`/`ServerEndpoint`/`Connection`/
`ServerConnection` SPI carries invisible obligations — at-most-once delivery, the DGC
acknowledgment signal, delivery-status-for-retry, per-direction half-close, request-origination
directionality — enforced *by the old implementation you're replacing*, not by the interface
signatures. The whole discipline: find every contract the old layer silently honoured, and prove
the new layer honours it too, before anything is deleted. **Byte-flow bidirectionality is not
request-origination bidirectionality, and neither is authorization directionality** — three
independent axes; every serious transport bug lived in the gap between them.

#### Recurring hazards (with source anchors)

1. **Request-origination is uni-directional even though byte-flow isn't.** `MuxClient.newRequest()`
   allocates a session ID and creates `Session(this, sessionID, Session.CLIENT)` →
   `getOutboundRequest()` (`MuxClient.java:66-79`); `MuxServer` has **no** `newRequest` — only
   ever calls `getInboundRequest()` reactively. `Session.getOutboundRequest()` asserts `role ==
   CLIENT`, `getInboundRequest()` asserts `role == SERVER` (`Session.java:154,188`). *Trap:* "is
   the connection bidirectional? yes ⇒ can the server push a request back? no" — not in classic
   JERI. The server side is purely an `InboundRequest` dispatcher.

2. **The `Connection`/`ServerConnection` SPI asymmetry is deliberate and load-bearing.**
   `ServerConnection` exposes only `processRequestData` + `InboundRequest` hooks — no
   `newRequest`, no `OutboundRequest` (`connection/ServerConnection.java:125,145,170,195`). This
   asymmetry is the structural proof of hazard 1. When reviewing a new transport, read both SPI
   interfaces and confirm the new code respects it — a server endpoint that grows an origination
   path is either a bug or a genuinely new capability needing its own security analysis (§3.4 QUIC
   server-initiated stream).

3. **The DGC acknowledgment contract — highest-value, easiest-to-miss.** The mux carries an
   application-visible acknowledgment distinct from byte delivery: `sentAckRequired`/
   `receivedAcknowledgment`/`AcknowledgmentSource.Listener`/`notifyAcknowledgmentListeners`
   (`Session.java:117-121,439-463,510-513`), firing `acknowledgmentReceived(true)` **only after
   the receiver's `RequestDispatcher` processed the request**, not on stream/data arrival.
   Consumer: `BasicObjectEndpoint` (the DGC client, `BasicObjectEndpoint.java:48`); independently
   re-implemented by the HTTP transport (`Request.java`, `HttpClientConnection`,
   `HttpServerConnection`) — proving it's a transport-SPI obligation, not a mux quirk. *Trap:* a
   stream FIN means "all my bytes were delivered," strictly weaker than "the far end processed
   them." Equating FIN with acknowledgment is a silent DGC lease-correctness bug — no crash, no
   happy-path test failure, just slowly-wrong distributed GC (WS §5.1).

4. **`getDeliveryStatus()` — the at-most-once/retry-safety signal.**
   `OutboundRequest.getDeliveryStatus()` (`OutboundRequest.java:181-197`) is how the invocation
   layer decides whether a failed call is safe to auto-retry. The mux computes it precisely:
   server-side abort sends `ABORT | ABORT_PARTIAL` (`Session.java:259-261`), client records
   `partialDeliveryStatus` (`Session.java:343`). `false` = known-not-processed, safe to retry;
   `true`/unknown = may have been processed, MUST NOT auto-retry. *Trap:* a new transport's abort
   primitive (QUIC `RESET_STREAM`, socket reset) doesn't carry a standardized "did processing
   start" bit — invent an explicit convention, wire it to `getDeliveryStatus()`, **fail-closed
   toward "maybe processed"** on ambiguity.

5. **Per-direction half-close (the "early response" contract).** `OutboundRequest`'s javadoc
   promises the response is readable while the request is still being written
   (`OutboundRequest.java:149-159`); the mux implements the asymmetric `Close`-instead-of-`Abort`
   dance (`Session.java:249-257,379-434`). A transport that couples the two directions' EOS state
   (tears down on receive-FIN, or buffers the response until request FIN) breaks this. Confirm
   independent per-direction close.

6. **Flow-control double-accounting.** The mux has per-session rations (`inRation`/`outRation`,
   `Session.java:107-113,310-330`). A new transport with its own flow control must **delete the
   mux rationing entirely** — never stack two credit schemes (deadlocks). The danger is a
   *partial* retirement: some paths on the new credit scheme, some still consulting the old
   ration.

7. **Connection concurrency limits ⇄ request concurrency.** The mux muxes unlimited concurrent
   requests over one connection; QUIC caps concurrency via `MAX_STREAMS`. When the cap is hit,
   the transport must define a policy (block/back-pressure, or open a second connection) — don't
   let this go unstated; it interacts with connection reuse and DGC lifecycle.

8. **Connection-migration/identity pinning (new adversary class).** QUIC connections are keyed by
   Connection ID, not the 4-tuple — a peer roams across IP changes without teardown. Invariant:
   identity is pinned at the handshake; migration changes the *path*, not the *peer*. A migrated
   path must complete `PATH_CHALLENGE`/`PATH_RESPONSE` before carrying traffic, and must not be
   able to splice a different peer onto an authenticated connection. TCP/UDS never faced this; a
   QUIC review must.

9. **Reachable-vs-ephemeral client topology (the deepest trap — invisible in the code).** See
   below and WS §5.2.

#### Review heuristics

- **Pre-deletion "what does the old layer do beyond its headline job?" audit.** Don't review the
  new layer in isolation. Read the old layer's core state machine end-to-end (for the mux,
  `Session.java`); for every state transition ask "is this pure {multiplexing/framing/byte-
  transport}, or a contract the layer above depends on?" — the tell for a hidden contract is an
  **application-visible consumer** (grep the message/interface name across the whole module;
  `AcknowledgmentSource` had 13 consumers, that breadth is the signal). Produce a numbered
  AUDIT-1..N list with severity and disposition (reproduced / will be silently dropped). **Nothing
  gets deleted until every item is reproduced or its removal is explicitly documented and
  accepted; "no half-retirement" is a MUST.**
- **Spotting a hidden origination/reverse-request path.** Grep the server-side SPI for
  origination verbs (`newRequest`, `OutboundRequest`, `newCall`) — their absence on
  `ServerConnection`/`MuxServer` is the proof origination is client-only; presence anywhere
  unexpected is a finding. Trace every callback/listener/event/notification to its actual
  invocation — in JERI they all reduce to: the callee is an exported `Remote` object, and the
  notifier invokes it as an ordinary client call (`BasicObjectEndpoint.newCall` →
  `Endpoint.newRequest`, `BasicObjectEndpoint.java:490-491`). Confirm per callback type; don't
  assume. `ServerContext` exposes inbound context only and never originates — good negative
  control (`grep newRequest ServerContext.java` → zero hits).
- **Spotting a broken authorization assumption.** The authz "caller" identity is sourced from the
  connection's authenticated peer: `getClientSubject(sslSocket)` at handshake
  (`SslServerEndpointImpl.java:1514`), injected via `populateContext` (`:1770`), consumed by
  `invokeWithClientSubject`/`checkClientPermission` (`:1163,1530`). Standing assumption everywhere:
  **caller = client = connection-initiator**. Whenever a design changes *who opens the
  stream/connection* relative to *who is the logical caller*, that assumption inverts — confused-
  deputy risk. Fix is direction-aware caller resolution (caller = stream initiator, not connection
  initiator).
- **The three-independent-axes check.** For any new transport, separately verify: (a) which
  directions bytes flow, (b) which direction(s) requests originate, (c) which peer's identity
  authorizes a given request. Never let an answer to one silently stand in for another — most
  findings were an (a)⇒(b) or (b)⇒(c) conflation.
- **Read the javadoc contracts as normative.** `OutboundRequest`/`InboundRequest` javadoc is the
  spec (at-most-once, early-response, close-vs-abort, stream identity on repeat calls). Diff the
  new transport's behaviour against each paragraph.

#### JGDMS-specific gotchas

- The mux role model is hard-asymmetric: `Session.CLIENT` originates, `Session.SERVER` dispatches,
  enforced by asserts (`Session.java:154,188`); client allocates session IDs. Never reason about
  the mux as symmetric.
- **Listener callbacks are new client connections with reversed roles (classic model).** A
  `RemoteEventListener extends java.rmi.Remote` is exported; its proxy bakes in a reachable
  dial-back address — `TcpEndpoint` serializes `host`+`port` (`TcpEndpoint.java:98-102,134,141`),
  and its javadoc says the host/port is "the remote address to connect to" (`:74-75`). "Service
  notifies client" is physically the service acting as a client, dialing the client's exported
  server.
- **Ephemeral clients break dial-back ⇒ server-initiated streams are mandatory.** The classic
  model assumes the client is reachable. Under QUIC, client/server is *topological*: the server
  has a fixed IP, the client is NAT'd/mobile/migrating (connection migration exists precisely
  because client addresses move). The listener proxy's baked-in `host:port` is then unreachable —
  the only viable delivery is over the connection the client already opened, via a
  **server-initiated QUIC bidi stream (`0x01`)**. Consequence, and it's a win: the client accepts
  inbound STREAMS, never inbound CONNECTIONS — no accept loop, no reachable port, no inbound
  firewall hole, no client-side `ServerEndpoint`. Server-initiated is primary; dial-back is a
  reachable-only compat path (STD-010 §4.0/§4.6).
- **On a server-initiated stream, the caller is the SERVER — authz inverts.** The dispatcher (now
  the client) must authorize the pushed event against the server's authenticated workload identity
  (from `getSession().getPeerCertificates()`, handshake-pinned), never anonymous, never the
  client's own privileges. Generalise `getClientSubject` → `getCallerSubject(streamInitiator)`. The
  STD-006 §7.2 ACC reducing-domain block mirrors direction on a server-pushed on-behalf-of event,
  validated at the client against the server's identity — same gate, made direction-aware
  (STD-010 §4.7, §4.7.1).
- **The DGC-ack is itself direction-sensitive (subtle).** The ack contract (hazard 3) is written
  "server emits after dispatch." On a `0x01` server-push stream the *dispatcher is the client*, so
  an ack-required push needs the ack to ride the client's send side. Latent today (only
  client-initiated DGC dirty/clean is ack-required; plain `notify` isn't), but any spec
  hard-coding "server emits the ack" is inconsistent with a symmetric caller model — make the ack
  "the dispatcher emits after its `RequestDispatcher` returns."
- **Identity lineage: QUIC-TLS follows `net.jini.jeri.ssl`, NOT the shipped UDS inc-1.** The
  shipped UDS is plaintext + layer-2 SPIFFE/JWT identity carried *inside request data*, with
  socket-file perms as the access gate, and **deliberately no `SSLEngine`.** The "UDS is the
  SSLEngine stepping-stone" thesis assumed UDS would exercise `SSLEngine`-over-a-channel — the
  shipped UDS skipped it, so it did not bank that keystone. The keystone (`SslConnection`
  `SSLSocket`→`SSLEngine`-over-channel; `getChannel()` returns `null` today,
  `SslConnection.java:491-493`) must land on its own (validated over TCP with full SPIFFE
  regression) before QUIC.
- **`Connection.getChannel()`** returns an optional `SocketChannel` (mux non-blocking path); the
  SSL transport returns `null` (blocking, `SSLSocket`-bound); UDS/QUIC return a real channel. This
  optionality is why the SPI is already the right shape for channel-backed transports — and why
  "does this transport support non-blocking I/O / virtual threads?" reduces to "is `getChannel()`
  non-null?", with the `SSLSocket` coupling the whole obstacle to reusing SSL over a channel.

#### War stories (defect + the tell)

- **§5.1 The DGC-ack catch (mux audit, CRITICAL).** *Situation:* the QUIC design proposed "1
  request ⇄ 1 QUIC stream, retire the mux," treating the mux as pure multiplexing. *Tell:* reading
  `Session.java` end-to-end, message handlers clearly not about multiplexing —
  `handleAcknowledgment`, `sentAckRequired`, `notifyAcknowledgmentListeners`
  (`Session.java:439-463`). A multiplexer doesn't need an application-visible acknowledgment — that
  smell was the thread to pull. *Catch:* grepping `AcknowledgmentSource` found 13 consumers
  including `BasicObjectEndpoint` and an independent HTTP-transport re-implementation — proving a
  transport-SPI contract; and that it fires only after dispatch processing (not byte delivery)
  proved mapping it to stream FIN would be a silent DGC lease-correctness bug. *Lesson:* the most
  dangerous findings make no noise; the tell was an internal mechanism whose shape (post-
  processing ack) didn't match its supposed job (multiplexing).
- **§5.2 The dial-back-breaks-for-ephemeral-clients reframe (the standout).** *Situation:* the
  reviewer had already answered definitively — "all JERI requests client-initiated, callbacks are
  new client connections with reversed roles, mux retirable without a reverse path" — code-correct,
  file:line-anchored, and as QUIC guidance, **wrong**. *Tell:* a reframing prompt pointed at
  topology — in QUIC/HTTP-3 the client address is ephemeral by design (that's what connection
  migration is *for*). Re-reading the same evidence (`TcpEndpoint` bakes `host:port` into the
  listener proxy) against "the client has no reachable address," the classic model collapses.
  *Catch:* the mapping inverts — events ride a server-initiated stream over the connection the
  client already opened, the client never accepts inbound connections, and the authorization
  caller inverts to the server's identity. This reshaped the entire QUIC event model and STD-010
  §4.6/§4.7. *Lesson:* the deepest bug was invisible in the code — the reachable-client assumption
  is never written down because it's always been true; only holding the code against the
  *deployment topology* surfaced it. Be willing to overturn your own correct-but-mis-framed
  conclusion the moment the frame changes.
- **§5.3 The UDS HIGH-1 leak (fail-closed vs fail-open under a security throw).** *Tell:* "does the
  throw leave anything behind?" — tracing `listen()`'s try/finally: `bind` succeeded,
  `restrictPermissions` threw, and closing a UDS `ServerSocketChannel` does **not** unlink the
  pathname socket on Linux. Fail-closed on the gate had a fail-open on the artifact. *Catch:* track
  `boundHere` and delete-if-exists the socket in the `!ok` branch, but only when *we* bound it
  (never delete a live incumbent's file); on Windows the `fileKey` is null, so the close-time
  identity check needed an owner-match + is-socket fallback. *Lesson:* a fail-closed decision can
  still leave a fail-open side effect; check the cleanup path of every security throw, on every
  platform.
- **§5.4 The Confidentiality over-claim (constraint honesty).** *Tell:* UDS `Constraints` claimed
  `Confidentiality.YES` FULL_SUPPORT "by locality" — made statically, by the client endpoint, from
  a *deserialized* path, with no verification the far end was actually a local owner-only socket.
  Same over-claim class as a previously-removed `Integrity.YES`. *Catch:* drop to
  `Confidentiality.NO` (byte-identical to plaintext TCP); defer confidentiality-by-locality to
  where `SO_PEERCRED`/SVID actually verifies the peer is local. *Lesson:* a constraint claim is a
  promise the object layer relies on to skip its own checks — an unverified transport
  self-description forgeable by a hostile serialized form is worse than no claim.

#### One-page checklist (JERI transport)

- [ ] Which directions do bytes flow? Which direction do requests originate? Which peer authorizes
      each request? (Three separate answers.)
- [ ] Does the new layer reproduce the DGC `AcknowledgmentSource` contract (post-*processing* ack,
      never inferred from FIN)?
- [ ] Is `getDeliveryStatus()` wired to the abort primitive, fail-closed to "maybe processed"?
- [ ] Is per-direction half-close preserved (early response readable while request still writing)?
- [ ] Is old-layer flow control fully deleted (no double-accounting)?
- [ ] Any server-caller/reverse path? Caller-subject direction-aware (stream initiator, not
      connection initiator)? Confused-deputy guard explicit?
- [ ] Reachable vs ephemeral client topology stated? Callback delivery survives an ephemeral client
      (server-initiated streams, client accepts streams not connections)?
- [ ] Connection migration: identity pinned at handshake, path validated before traffic, no
      peer-splice?
- [ ] Every security throw: cleanup path checked for a fail-open artifact, on every platform?
- [ ] Every constraint claim: who verified it, can a deserialized value forge it?
- [ ] Nothing deleted until every audited contract is reproduced or its removal documented — no
      half-retirement.

---

## 2.4 Rule-Fidelity, Determinism, and Cross-Language Review

*Distilled from the review chain that produced the 14-framework comparison table, the
JOSS-vs-DER value-equality property, and the rule-fidelity reviews of the collection-ordering
codec and the closed-subset type model. The three anchor documents to hold in your head before
reviewing anything in this domain: `JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md` (especially §3.7
acyclic, §3.8 "Values and Bounds, Never Behaviour" + intrinsic-order/opaque-octet carve-outs,
§7.6 substituted types, §7.8 schema digests, §9 conformance), `der-collection-ordering-research.md`
(the determinism memo — §0/§0.1 why value-equality matters, §1 taxonomy + discriminator, §2 X.690
§11.6 octet-sort, §3a byte-equality-vs-`.equals` tension), and `der-type-model-and-element-rule.md`
(the closed-subset type model + element rule — read as the worked example of "faithful sibling
done right").*

**The one sentence:** a new rule is faithful iff it keys on the declared type as the carrier of
intent (G4), puts structure on the wire and behaviour in the constructor (G5), and preserves the
property that byte-equality mirrors the type's own `equals` contract (G1).

#### Recurring coherence hazards

1. **A new rule silently contradicts a settled one.** The determinism discriminator was revised
   once already, and the revision *reversed* a naive call — that history is the warning. The
   final discriminator is "does the declared type guarantee a deterministic iteration order?"
   (memo §1) — **not** "canonicalize-by-default with per-field opt-in," and **not** "does it
   implement `Sequenced*`?" Two cases don't follow from `Sequenced*` membership: `CopyOnWriteArraySet`
   *canonicalises* (a Set, order not part of value, and concurrent insertion order is
   non-deterministic anyway — reversing the naive "insertion-ordered → preserve" call), contrast
   `CopyOnWriteArrayList` (same impl family, but a List → preserve); `ConcurrentSkipListSet`/`Map`
   *preserve* (comparator order is element-derived → deterministic even under concurrency). *Tell:*
   a proposal cites the interface hierarchy or a "value vs behaviour judgement call" instead of the
   determinism question — reject or send back, the settled discriminator is determinism of the
   declared type, full stop.

2. **The discriminator drifts from "the declared type carries the intent."** See G4 in full.
   Reading the instance (`value.getClass()`, `hashCode()`, hash-bucket order) to decide wire
   structure has drifted — enforced by a source-scanning test that fails if the collection-wire-
   type code so much as calls `hashCode()`. (Note: `value.getClass()` at *encode* time for a
   polymorphic `@AtomicSerial` slot is fine — that's the value self-identifying by digest, not the
   rule deciding structure.) The annotation smell: `@DerElement(Foo.class)` on a `Set<Foo>` field
   restates `Foo` — push back, derive it by rule. The type-variable exception (`Set<T>` in a
   generic class, degrades to `Any`) is proof the annotation was never the answer — there's no
   concrete type for it to name either.

3. **Value-vs-implementation confusion (the deepest one).** See G1's cross-reference: JOSS
   serialises the implementation, DER serialises the value. A `HashSet` and a `TreeSet` of the same
   elements are `.equals` as Sets but serialise to *different* JOSS bytes; DER under the ordering
   rule octet-sorts elements independent of implementation, so two `.equals` sets produce
   *identical* bytes — serial-equality coincides with value-equality (memo §0.1), the foundation
   under Entry byte-matching, content-address digests, and signature stability. *Tell:* ask "if I
   send this value from two different concrete implementations of the same logical value, do I get
   the same bytes?" — if the answer depends on implementation, the proposal reintroduced the JOSS
   disease (carrying a concrete class name where a digest belongs, preserving an
   implementation-derived order, letting the sender's concrete type dictate the receiver's type).
   This is also why **receiver-chooses-implementation** must survive every change: the wire fixes
   the value, the constructor picks the implementation.

4. **The byte-equality-vs-`.equals` tension (know exactly where it lives).** For preserve types,
   byte-equality is order-sensitive; for canonicalise types it tracks `.equals`. The tension —
   serialized byte-equality *stricter* than object `.equals` — lives in **exactly two types**:
   `LinkedHashSet` and `LinkedHashMap` (memo §3a). They inherit order-independent `Set`/`Map`
   `.equals` yet carry a non-rederivable insertion-history order, so two `.equals` instances built
   in different insertion orders serialise to different octets (won't match under §7.7.2 Entry
   matching) — **intended, not a bug**, correct for a type whose insertion order is significant.
   All other preserve types have no tension (element-derived order for `TreeSet`/`TreeMap`/
   `EnumSet`/`ConcurrentSkipList*`; positional for `List`/array — `.equals` already
   order-sensitive). *Tell:* a proposal that "fixes" the `LinkedHashSet` tension by canonicalising
   it (wrong — discards the insertion order the type exists to keep), or that assumes `.equals` ⇒
   byte-equal for these two types in a matching/dedup path (wrong, latent correctness bug). Correct
   guidance to a developer wanting order-independent wire equality: declare a plain (canonicalise)
   Set/Map instead.

#### Review heuristics

**Is the new rule a faithful sibling? — the four-question pass, in order:**
1. What is the signal? Must be the declared type (class or generic signature), a pure function of
   the declaration — not an instance property, not an annotation restating a declaration, not a
   runtime hash. Otherwise stop.
2. What layer does it live in? Structural facts on the wire/schema (Layer 1, digest-covered);
   semantic facts in the constructor's `check(GetArg)` (Layer 2, see G5). A rule putting a
   behavioural contract on the wire, or a structural fact in the constructor, has crossed the
   split.
3. Does it compose orthogonally with existing rules? Discipline (from the class) and element type
   (from the signature) compose independently — `LinkedHashMap<String,HashSet<Foo>>` →
   `orderedmap:{...}{set:@AtomicSerial}`, outer preserves, inner canonicalises, per level, no
   interaction. A faithful new rule slots into the recursive tree-walk without needing to know
   about the other axes.
4. Does the value-equality property survive? Re-run hazard 3's test (same logical value, two
   implementations → same bytes for canonicalise types), and confirm the `LinkedHash*` tension is
   preserved, not "fixed."

If all four hold, it's a sibling.

**Testing cross-language/cross-runtime determinism claims** (see G9): map the rule's categories
onto a second runtime's type system; for each discipline outcome, find the target-runtime type
that would land there and check the *reason* survives (is X's order deterministic *in that
runtime*?) — if the classification flips for the "same" type, that's a real finding, the rule was
keying on a Java-specific guarantee. For any canonical-bytes claim, verify the ordering is a pure
function of element values (X.690 §11.6 octet-sort over complete encoded forms) — a
canonicalisation depending on anything the second runtime computes differently (hash, iteration
order, locale) is not canonical. **Do not accept "it's canonical" without asking "canonical of
what?"** (Avro's canonical form is of the schema, not the data — see below.)

**Catching "this contradicts what we decided":** read the revision notes first (both the spec's
changelog and the determinism memo carry explicit "SUPERSEDED" markers with owner-decision
blocks) — a proposal re-proposing a superseded option is the most common contradiction, invisible
unless you've read *why* the earlier version was dropped. Grep the settled invariants the proposal
touches — before blessing a change near canonicalisation, `check(GetArg)`, digests, or ordering,
find the load-bearing sentences (canonicity is load-bearing at signatures, schema digests, Entry
byte-matching — a change "simplifying" one usually breaks another). Verify the proposal's factual
anchors against the built code, not on faith (see G3) — a memo's self-description can be stale.

#### JGDMS gotchas

- **Canonicalise vs preserve — the exact boundary.** The discriminator is determinism of the
  declared type's iteration order. Memorise: `HashSet`/`HashMap`/`ConcurrentHashMap`/
  `CopyOnWriteArraySet` → canonicalise; priority/concurrent-FIFO queues → canonicalise **as a
  multiset** (`bag:`, duplicates retained — a queue may legitimately hold dups, unlike a Set);
  `List`/`Deque`(non-concurrent `ArrayDeque`)/`LinkedHash*`/`Sorted*`/`EnumSet`/
  `ConcurrentSkipList*`/`CopyOnWriteArrayList` → preserve. On decode, canonicalise-set fields
  reject duplicate element encodings (a Set can't hold post-canonical dups); `bag:`/`list:` keep
  them. Getting a priority queue classified as a set would silently drop duplicates — the split
  isn't cosmetic.
- **Digest coverage — what the schema commits, and where it thins.** The schema token is part of
  `schemaBytes` (§7.8, a Merkle chain over the class hierarchy), so ordering discipline *and*
  structural element type are digest-covered. The one place coverage thins is the `Any` fallback:
  a `set:@AtomicSerial` commits "every element is a closed record of a known shape"; a `set:` of
  `Any` commits only "elements are closed-subset values" — but this is a *reduction*, not a
  *loss*, because per-element `@AtomicSerial` digests still travel inside each `AnyElement.body`.
  When reviewing anything touching digests, keep "looser collection-level schema" (acceptable,
  disclosed) distinct from "records lose their digests" (would not be acceptable).
- **The `Any`-fallback discipline.** Three rules make it safe: (1) rule-selected, never default,
  never developer-chosen — reached only for the five genuinely unresolvable declared shapes (raw
  `Set`, `Set<?>`/`? extends Object`, `Set<Object>`, `Set<? super X>`, type-variable `Set<T>`); a
  *resolvable* type landing in `Any` is a conformance failure, not a convenience. (2) It doesn't
  reopen the subset — still one of the three base categories, never an arbitrary `Object` graph,
  never arbitrary `Serializable` (the JOSS gadget surface STD-006 refuses), never a cycle. (3)
  Canonicalisation still applies — an `AnyElement` is a complete TLV, so a canonicalise collection
  of `Any` octet-sorts the `AnyElement` encodings exactly as homogeneous elements. *Tell of a bad
  fallback:* it becomes the default for convenience, or admits something outside the closed subset
  "just this once" — both forfeit a property (cross-language, value-equality, or canonicity) the
  closed subset exists to guarantee.
- **The exclusion boundary is load-bearing, not incidental.** The model deliberately excludes raw
  `Object` graphs, arbitrary `Serializable`, and cycles — jointly what make cross-language +
  byte-value-equality + DER canonicity simultaneously achievable; admit any one back and you
  forfeit at least one property. "Less is more" is the closed subset's price *and* payoff. A
  proposal relaxing an exclusion "for flexibility" is spending a property — make it name which one.
- **Opaque-octet and intrinsic-order carve-outs (spec §3.8).** Two things the ordering rule must
  never touch: (a) intrinsic-order sequences — principal chains (leaf-first), certificate paths,
  arrays — where order *is* the value and sorting corrupts it; (b) opaque octets — X.509 certs,
  `X500Principal`, `Signature` output — carried verbatim from `getEncoded()`, never re-encoded or
  sorted (re-encoding breaks the signature over them). A canonicalisation proposal that
  "normalises" one of these is a security bug — explicitly confirm any sort/canonicalise change
  excludes both carve-outs.

#### War stories (the tell that surfaced each)

- **The value-equality-proxy insight (headline property).** *What:* the realisation that DER's
  canonicalise-unordered-collections rule doesn't just give determinism — it makes DER
  byte-equality mirror the type's `equals` contract, restoring the value/serial-equality
  correspondence JOSS structurally breaks (the comparison table's `VEq` column: no surveyed
  framework, including generic ASN.1 DER, canonicalises unordered-collection order to preserve
  value-equality). *Tell:* it surfaced from a *contrast* question — "why can't JOSS do this on a
  Set field?" — not from describing DER in isolation. Lesson: the sharpest properties are found by
  contrast with what the incumbent cannot do.
- **The generic-DER subtlety (the cell that almost got it wrong).** *What:* generic ASN.1 DER *is*
  canonical (SET OF sorts, definite lengths), so the reflexive call was a ✓ on value-equality too.
  Wrong: generic DER canonicalises *within a given ASN.1 value* but doesn't know a `HashSet` and a
  `TreeSet` are the same logical value — mapping both to a bare `SEQUENCE OF` in iteration order
  gets different bytes. STD-006's type→ordering-discipline rule is the distinctive layer *on top
  of* generic DER. *Tell:* "canonical" was doing two jobs — canonical *encoding of a value* vs
  canonical *choice of which value to encode*. Force the word to say canonical *of what* before
  comparing.
- **The Avro "canonical form" trap.** *What:* Avro's Parsing Canonical Form + fingerprint invites a
  ✓ on canonical encoding — it's a canonicalisation of the *schema* (resolution/identity), not the
  *data* encoding (no mandated map-entry order in Avro binary data), so the Canon/Sign cells are
  `~`, not `✓`. *Tell:* the same "canonical of what?" question, this time answered "of the schema,
  not the bytes you'd sign." Verify the object of a canonicity claim against the primary spec (cite
  the primary source, not folklore — protobuf's own docs even title a page "Proto Serialization Is
  Not Canonical").
- **The honest-tradeoff reframing on evolution.** *What:* a first draft called Avro's
  reader/writer schema resolution "a stronger evolution model" — one-sided. Balanced (verified
  against `GetArg.get(name,default,type)` and `check(GetArg)` running before construction): Avro
  wins on zero-code automatic reconciliation; STD-006's imperative `@AtomicSerial` reconciliation
  is strictly more expressive (compute-from-old, representation change, cross-field) and enforces
  invariants at construction, which declarative models structurally cannot. *Tell:* a comparative
  superlative ("stronger", "better") naming only one side's benefit — flag it, name what each side
  gives up, and verify the mechanics in the repo before writing the balanced version.
- **The "built but not shipped" honesty (the caveat that kept changing).** *What:* the
  value-equality property went through three honest states — design-now/build-later → built+tested
  but unmerged → merged to trunk but unreleased — plus a standing residual (auto-wiring for plain
  Set/Map fields deferred, and **fails closed**, never silently non-canonical). Each was true at
  the time; the discipline was updating the caveat the moment the codebase moved, and keeping the
  residual that was still true. *Tell:* the temptation each time was to round "built" up to
  "delivered." Track design-vs-shipped-vs-released as distinct states; sweep every instance when
  the state changes (there were 8 the day it merged).
- **The cross-language proof (the §3a tension in Rust).** *What:* confidence that the ordering rule
  is semantic, not a Java artifact, came from the byte-equality-vs-`.equals` tension *reappearing*
  in Rust on the analogous insertion-ordered types — same tension, same reason (non-rederivable
  history over order-independent equality), different runtime (`der-rust-collection-mapping.md`
  shows the same discipline landing on `BTreeSet`/`HashSet`/`Vec`/`BTreeMap`). *Tell:* a rule that's
  a language quirk classifies "the same" type differently across runtimes; a rule that's semantic
  reproduces its hard cases everywhere. To test whether a determinism rule is real, check that the
  *awkward* case translates, not just the easy cases.

#### One-page checklist (rule-fidelity / determinism / cross-language)

1. **Signal check** — keys on the declared type (class or generic signature)? Not an instance, not
   a hash, not an annotation restating a declaration?
2. **Layer check** — structure on the wire (digest-covered), behaviour in `check(GetArg)`? Does the
   erasure "problem" disappear once each concern is at the right layer?
3. **Composition check** — slots into the recursive rule orthogonally to discipline/element/other
   axes?
4. **Value-equality check** — same logical value from two implementations → same bytes
   (canonicalise)? `LinkedHash*` tension preserved, not "fixed"? Receiver still chooses the
   implementation?
5. **Contradiction check** — read the revision/changelog notes; is this a superseded option
   returning? Proposal's claims about the built code verified against trunk?
6. **Canonicity carve-out check** — canonicalisation excludes intrinsic-order sequences and opaque
   octets? Octet-sort is a pure function of element values (no hash)?
7. **Fallback/exclusion check** — any `Any`-like escape hatch stays rule-selected + inside the
   closed subset? Any relaxed exclusion names the property it spends?
8. **Cross-language check** — the rule's hard case (a tension, a reversal) reproduces in a second
   runtime; "canonical" states canonical *of what*?
9. **Honesty check** — design vs shipped vs released stated distinctly; tradeoffs name what each
   side gives up; caveats are live and swept?

If all nine hold, it's a faithful sibling. If one fails, you've found the exact sentence to send
back.

---

## Closing note

None of these four seats considered their value to be *finding bugs* — a green suite and a static
analyzer already do that. The value was finding what's **wrong but passes**: the unsorted SET OF
that round-trips, the redundant tag that admits a lie, the size-bound that silently doesn't cover
a new direction, the split-package that only breaks on a `--release` build months later, the
confused-deputy whose check-subject is right and execution-subject is wrong, the mux contract
whose shape didn't match its supposed job, the rule that reads plausible because it reuses old
vocabulary while quietly reversing an old ruling. Those are invisible to green tests and visible
only to a reviewer who holds a small number of reflexes — one canonical encoding per value,
outcome is a claim, check the default, the declaration is the signal, verify independently, fail
closed on ambiguity — and applies them, every time, to the actual design in front of them rather
than to what the spec says it does. Keep those reflexes. Cite the clause and the `file:line`. Rule
mandatory when it's mandatory. And when several sources of truth exist, make them agree before you
sign off.
