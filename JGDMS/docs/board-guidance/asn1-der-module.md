# Board Reviewer's Guidance — ASN.1 / DER Wire-Format & Module-Boundary

*Distilled from seven sequential board reviews (2026-07-04/05): the STD-006 v0.13 ASN.1
module + spec edits, the UDS transport security review, the DER collection-codec conformance
ruling, the module+spec sync, the QUIC ACC/export board review, the DirtyChai export-surface
location map, the DER closed-subset type-model review, and the STD-010 conformance-gate verdict.*

This is the knowledge a DER/ASN.1/module-boundary reviewer needs to keep the wire honest. It is
written to outlive any one reviewer. Where a claim is load-bearing, a concrete finding is cited so
a successor can go read the actual defect and its fix.

---

## 0. The one sentence to remember

**DER means exactly one valid encoding per value — so anything that admits a second encoding of the
same value, or fails to reject a non-canonical one on decode, is a bug, even if it round-trips.**
Almost every real defect below is a violation of that single invariant wearing a different costume.

---

## 1. Recurring HAZARDS & TRAPS (watch for these first)

### H1. Non-canonical encodings that "round-trip" and therefore look fine
The most dangerous DER defect passes every happy-path test. A codec that *encodes* canonically but
*accepts* a non-canonical input on decode is still broken: DER requires the decoder to **reject**
the non-unique encoding, because the canonical bytes are load-bearing under (a) signatures, (b)
content-address digests, and (c) byte-equality matching. If decode-accept-then-re-encode does not
reproduce the input bytes, the "one encoding per value" invariant is broken *at the receiver*.
- **Tell:** the decode path checks for *duplicates* but not for *order*; or it decodes into an
  order-preserving container and never validates the incoming order.
- **War story (WS-1):** the collection codec's `decodeSetOrList`/`decodeMap` rejected duplicate
  element/key encodings but **not** wrong order — a hand-crafted `set:int` holding INTEGER `3,2,1`
  decoded happily. Ruling: **rejecting non-canonical order on decode is MANDATORY** (X.690 §10.1
  "unique encoding" + §11.6 SET OF restriction clause + STD-006 principle 6 fail-secure), and
  **pre-merge, not fast-follow** — leniency calcifies (the CBOR length-first-vs-bytewise trap) and
  the green suite hid the gap because no test fed unsorted bytes. Fix was ~6 lines + one test.

### H2. Distinct-tag violations on OPTIONAL SEQUENCE components and on CHOICE arms (X.680)
Every `OPTIONAL` component in a `SEQUENCE` must carry a tag distinct from every component that can
immediately follow it; every `CHOICE` arm must be distinct-tagged among its siblings. Bodies that
share a universal tag (two `SEQUENCE OF` → both `0x30`; two `INTEGER` → both `0x02`; a `SEQUENCE`
object body and a `SEQUENCE OF` collection body → both `0x30`) are **indistinguishable** and the
module is **invalid ASN.1** — standard tooling rejects it.
- **Tell:** two adjacent `... OPTIONAL` fields of the same shape; a `CHOICE` whose arms are bare
  (untagged) universal types; a hand-rolled `ENUMERATED tag` field bolted on precisely *because*
  the bodies aren't tag-distinct (that ENUMERATED is a symptom — see H3).
- **War stories:** §7.6 `Permission` (`name`/`actions` both `UTF8String OPTIONAL`) and
  `ReducingDomainRecord` arms were fixed with `[n] IMPLICIT` context tags (STD-006 §4.5). The
  closed-subset `Any` review (WS-4) is the CHOICE version: a naive `CHOICE` over the closed subset
  collides `atomicObject` (`0x30`) with a preserve-`collection` (`0x30`) and all the `INTEGER`
  scalars (`0x02`) — invalid; the fix is one `[n]` context tag per category.

### H3. Redundant discriminators — a second place to state the category = a lying-encoding hazard
If the wire carries the category *twice* — once as an explicit tag/enum and once intrinsically (the
body's own universal tag already says `INTEGER` vs `UTF8String`) — the two can disagree, and a
decoder must now explicitly reject the mismatch. That is a non-canonical surface DER exists to
forbid.
- **Tell:** `SEQUENCE { tag ENUMERATED, body <open type> }` where `body`'s universal tag already
  discriminates most categories.
- **War story (WS-4):** the `Any` element form was proposed as
  `AnyElement ::= SEQUENCE { tag AnyTag(ENUMERATED), body Element }`. Two defects: `body Element`
  (the `OCTET STRING` stub) mis-models a *tag-varying open type* and, if literally `OCTET STRING`-
  wrapped, breaks the value-equality-of-payload claim; and the `ENUMERATED tag` re-encodes what the
  body tag already says (`scalarInt(3)` vs the body's `0x02`) — admitting a lying `AnyElement`.
  **Recommendation: `[n] IMPLICIT`-tagged `CHOICE`** keyed on the category — one discriminator, not
  two; distinct-tag-clean; no `OCTET STRING` wrapper; *smaller* on the wire; and it makes the
  octet-sort's category-grouping a **provable** consequence of the leading context tag rather than
  an accident of `SEQUENCE` framing.

### H4. IMPLICIT tagging that silently overwrites a load-bearing universal tag
`IMPLICIT` replaces the underlying type's tag with the context tag *in place*. That's the right,
compact choice for most fields — **but** it is illegal for a `CHOICE`/`ANY` alternative without an
explicit tag (X.680 §31.2.7), and it is *wrong* whenever the underlying universal tag is itself a
discriminator you need to keep.
- **Tell:** an `[n] IMPLICIT` applied to a `SET OF`/`SEQUENCE OF` whose `0x31`/`0x30` distinction is
  the very thing that encodes a discipline; or a bare `[n]` on a `CHOICE` arm.
- **War stories:** in the `Any` CHOICE, the `collection` arm **must be `[n] EXPLICIT`** (or split
  canonical/ordered) so the inner `SET OF 0x31` / `SEQUENCE OF 0x30` Option-A discipline survives —
  exactly the §31.2.7 hazard flagged earlier for `ReducingDomainRecord`. Conversely, marking the
  `ReducingDomainRecord` arms `[0]/[1]/[2] IMPLICIT` was correct and **changed the wire bytes**
  (nullCs went from an EXPLICIT-wrapped `A0 02 05 00` to a bare `80 00`) — a real byte-level change
  that a round-trip test must expect, not a stylistic one.

### H5. EXPLICIT-vs-IMPLICIT module default treated as inert when it is not
"Both TAGS modes produce identical bytes" is only true when *every* context tag is written with an
explicit `IMPLICIT`/`EXPLICIT` qualifier. The moment a bare `[n]` exists, the module default
governs and the bytes differ.
- **Tell:** a module header comment claiming byte-equivalence of the two TAGS modes while the body
  contains (or a future edit could add) a bare `[n]`.
- **War story:** the STD-006 module header originally claimed EXPLICIT/IMPLICIT produced identical
  bytes "for every type"; the `ReducingDomainRecord` arms (bare `[0]/[1]/[2]` under
  `DEFINITIONS EXPLICIT TAGS`) were the counterexample. §4.6 (NORMATIVE tagging mode) was written to
  state: EXPLICIT is the defensive default, every context tag is written `IMPLICIT` explicitly, so
  the default only governs *future untagged additions* — and the "byte-identical either way" claim
  was explicitly retracted as false.

### H6. "The kernel/transport already bounds it" — size-bound-before-allocation deleted on a false premise
A lower layer bounding *bytes* is not the same as bounding *decoded element counts*. A few
flow-controlled bytes can decode into a huge collection or a deeply nested structure.
- **Tell:** a decoder that relies on a byte limit (QUIC `MAX_DATA`, a stream cap, a socket read
  size) and skips the schema `SIZE` ceilings.
- **War stories:** STD-006 §4.5 pins `maxCollection`/`maxFields`/`maxStackFrames`/`maxCauseDepth`/
  `maxDomains`/… and the collection codec caps all six loops (65536 accepted, 65537 rejected). The
  STD-010 review (WS-6) caught that §8.2's size-bound MUST was written only in the client-initiated
  frame and did **not** explicitly cover the *client decoding a server-pushed request body* — the
  new decode surface that server-initiated streams opened. "Authenticated sender ⇒ trusted bytes"
  is the reverse-direction version of the same fallacy; bounds apply to **every** decode surface in
  **both** directions.

### H7. Map/SET-OF canonical sort: sorting the wrong object
For a `SET OF`, §11.6 sorts the **complete element encodings**. For a `Map` (SET OF SEQUENCE{k,v}),
you must sort by the **encoded key only** — sorting the whole `{key,value}` entry TLV lets the
value bytes (via the entry's length octet and content) perturb the order, so two conformant
encoders disagree cross-implementation even though each is internally deterministic.
- **Tell:** a map canonicaliser that calls the generic `octetSort(entryTlvs)` instead of sorting by
  a parallel key-encoding list.
- **War story (WS-3):** the collection codec at one commit sorted maps by whole entry
  (`octetSort(entryTlvs)`); the fix sorted by `keyTlvs` with an inline rationale. Sub-finding that
  matters: the prefix-of-one-another case for two distinct keys does **not** break either variant
  (distinct values → distinct canonical DER → the length octet diverges first; keys unique → total
  order) — so it is **not** a total-order hazard, it is a *cross-implementation-agreement* hazard.
  Know the difference; they have different fixes.

### H8. The org.apache.river.api.security split-package landmine
This package exists in **both** `java.base` (DirtyChai embeds ~11–12 authorization-grant classes,
exported unqualified) **and** `jgdms-platform` (the full ~35-class River authorization set), with
overlapping class names. It is the single most fragile namespace in the stack — the documented
source of the "recompile `--release 21` silently emits the embedded copy → `NoSuchMethodError`"
trap.
- **Tell:** any proposal to add a class to `org.apache.river.api.security`, or to build one of those
  classes with `--release <n>` targeting an older API.
- **War story (WS-5):** the QUIC-TLS facade home. Rejecting `org.apache.river.api.security` was
  correct for *two* independent reasons — split-package (a `java.base`-only member added to a
  package that also lives in `jgdms-platform` deepens the resolution ambiguity in a security-
  critical TLS path) **and** category mismatch (authorization ≠ TLS plumbing). The sound home is
  `au.zeus.jdk.net.ssl` — `java.base`-only ⇒ split-package-free by construction, DirtyChai's
  established `au.zeus.jdk.net.*` convention. Squatting `javax.net.ssl` was also rejected (a fork
  must not squat the standard namespace; a future upstream JEP would collide).

---

## 2. REVIEW HEURISTICS — what I check first, and the questions that surface a defect

**The order matters — cheap checks that catch the highest-severity defects first.**

1. **"Is there exactly one encoding per value, and does decode reject the others?"** — the master
   question (H1). For any `SET OF`/`SET`/canonicalise form, immediately ask: *does the decoder
   verify ascending §11.6 order, reject duplicates in a set, and reject the wrong container tag?* If
   the decode path only checks duplicates, you've found the defect. Encode-canonical + decode-lenient
   is the default failure mode.

2. **"Does this compile under a real ASN.1 tool, and are all OPTIONAL/CHOICE tags distinct?"** —
   run the module through `asn1tools` (or equivalent). Distinct-tag violations (H2) are *mechanically
   detectable* — a tool rejects them — so there is no excuse for shipping one. Watch specifically for
   adjacent same-shape `OPTIONAL`s and bare `CHOICE` arms.

3. **"What is the leading octet after every edit?"** — for any IMPLICIT/EXPLICIT change (H4), encode
   a tiny sample and read the tag byte. `SET OF` must be `0x31`, `SEQUENCE OF` `0x30`, `[n] IMPLICIT`
   is `0x80|n`, EXPLICIT wraps. A round-trip test that only checks `decode==input` will *pass* on a
   wrong tag if both ends agree wrongly — so verify the *tag*, not just the round-trip.

4. **"Is the discriminator stated more than once?"** — if a category is carried both by an explicit
   tag/enum and by the body's intrinsic universal tag (H3), that's a lying-encoding hazard. Prefer a
   single `[n]`-tagged CHOICE.

5. **"Sort the right object?"** — for any canonical ordering: SET OF sorts complete element TLVs;
   Map sorts by *encoded key only* (H7). Confirm the code sorts what §11.6 says to sort.

6. **"Does the size-bound survive this transport/direction change?"** — whenever a payload moves to a
   new transport or a new direction (server-push, event callback), ask: *is there a new decode
   surface, and does the §4.5 SIZE ceiling explicitly cover it?* (H6). The MUST must be directional
   or an auditor can't confirm it.

7. **"Where does this package live, and does it live anywhere else?"** — for any new class in
   `java.base` or a shared module (H8): is the package already present in a second module? If so,
   split-package landmine — pick a `java.base`-only home.

8. **"Is the derived wire-type digest-covered and deterministic?"** — for any schema-generation
   change: is the token that ends up in the schema bytes a *pure function of declarations* (not
   instance state, not `hashCode`, not runtime reflection of erased generics), so two builds and two
   runtimes produce the same digest? (WS-7: `Field.getGenericType()` reads the class-file Signature
   attribute — a declaration-time, compile-fixed artefact — so it is digest-stable; instance
   generics would not be.)

9. **"Are the citations right?"** — cross-references are load-bearing and silently propagate. For any
   `§x.y` citation into a dependency, open the dependency and confirm the section is what the citing
   doc claims (WS-6 §7.2/§7.3). A wrong citation that everyone copies from the same SOW becomes
   "true by repetition."

---

## 3. JGDMS-SPECIFIC GOTCHAS

- **The split-package landmine (H8).** `org.apache.river.api.security` in `java.base` **and**
  `jgdms-platform`. Never add to it; never `--release`-downtarget its embedded classes. New java.base
  additions go under `au.zeus.jdk.*` (`au.zeus.jdk.net.ssl` for TLS/transport,
  `au.zeus.jdk.authorization.*` for authz). Split-package-free = `java.base`-only.

- **DER canonical-decode invariants (STD-006).** Principle 2 (DER not BER) and principle 6
  (fail-secure decode, no permissive fallback) together *mandate* rejecting: non-canonical `SET OF`
  order, wrong container tag (`0x30` where `0x31` is required or vice versa), disallowed duplicates
  in `set:`/`orderedset:`/`map:`/`orderedmap:` (a `bag:` multiset keeps dups), over-`maxCollection`
  counts, `AlgorithmIdentifier` with `parameters` present (parameters-absent fail-secure, RFC
  5280/8702), and a `schemaDigest` that doesn't match `schemaBytes`. Every one is a *reject*, not a
  tolerate.

- **The six collection productions + Option A tags.** Canonicalise (`set:`/`bag:`/`map:`) → `SET OF`
  universal tag **0x31**, §11.6 octet-sorted, decoder rejects out-of-order. Preserve
  (`orderedset:`/`list:`/`orderedmap:`) → `SEQUENCE OF` tag **0x30**, iterator order verbatim. Map is
  `SET OF SEQUENCE{key,value}` (outer 0x31, per-entry SEQUENCE 0x30), entries sorted by encoded key.
  The module declares `CanonicalSet`/`CanonicalMultiset`/`CanonicalMap`/`OrderedSetField`/`ListField`/
  `OrderedMapField`, all `SIZE(0..maxCollection)` **inclusive** (exactly 65536 accepted). Declaring
  `SET OF` gets you §11.6-order enforcement *for free* from any conformant DER tool — Option A was
  chosen precisely so the ordering guarantee is machine-checkable, not solely hand-rolled.

- **IMPLICIT-vs-EXPLICIT trap (STD-006 §4.6).** The module is `DEFINITIONS EXPLICIT TAGS`; every
  context tag is written `IMPLICIT` explicitly. The default is EXPLICIT (defensive: keeps the
  universal tag visible, forecloses the §31.2.7 CHOICE/ANY hazard). Do **not** claim the two TAGS
  modes are byte-identical — they are not once a bare `[n]` appears. New tagged fields **must** spell
  out their disposition.

- **Digest coverage.** The schema wire-type token is part of `schemaBytes`, which `schemaDigest`
  commits (STD-006 §7.8). So any element-type/discipline derivation that lands in the token is
  digest-covered and must be deterministic. Cross-language decoders agree because they consume the
  *token in the schema*, never the JVM reflection. `schemaDigest` MUST be verified against
  `schemaBytes` before use — a routing hint, not an integrity claim.

- **The ASN.1 comment `--` trap (mechanical, but it will bite).** In X.680, `--` opens **and closes**
  a comment; a *second* `--` on the same line reopens/closes it, and text after can become live
  grammar. An em-dash written as `--` mid-comment broke the module compile (`asn1tools` parse error
  at "both TAGS modes"). Never use `--` mid-comment; use `;` or `:` or a real em-dash.

- **The Python toolchain.** The working interpreter is
  `C:\Users\peter\AppData\Local\Programs\Python\Python311\python.exe` — the bare `python`/`py` on PATH
  are broken Windows Store aliases. Recompile+round-trip gate:
  `python -c "import asn1tools; s=asn1tools.compile_files(['JGDMS-STD-006-v0.13.asn1'],'der'); print('types',len(s.types))"`.
  A green compile is necessary but **not** sufficient — it proves distinct-tag/grammar validity, not
  canonical-decode correctness (H1). Always also encode→decode the arms whose bytes changed and
  assert `decode==input`, and read the leading tag byte.

- **STD-006 §7.2 is the ACC reducing-domain transport; §7.3 is `DigestCodeSourceRecord`.** The design
  SOWs miscite the ACC block as "§7.3" repeatedly. It is **§7.2**
  (`AccessControlContextRecord`/`ReducingDomainRecord`, the `RemoteContextCodec` reducing-domain model
  from v0.12+). §7.3 is code identity. When reviewing anything that cites the ACC block, check the
  number, and ensure the citing doc references STD-006 ≥ v0.12 (where §7.2 became the reducing-domain
  model — the in-tree v0.10 copy still shows the withdrawn `DomainIdentityRecord`/
  `AccessControlContextSerializer` model).

---

## 4. PRINCIPLES that made the reviews effective

- **Canonicity is not a preference; it is the contract.** DER's whole point is one encoding per
  value. Frame every finding as "does this preserve or break that?" and the severity sorts itself.

- **Mandatory vs optional is a ruling you must make, and timing is part of it.** When a fix is a
  *conformance* requirement (H1's reject-non-canonical), say **mandatory** and say **pre-merge** —
  because leniency merged is leniency calcified, and interop partners cement against whatever shipped.
  Cite the standard (X.690 §10.1/§11.6, RFC clause) so the ruling carries weight, not just opinion.

- **A green test suite is evidence of nothing about the case it doesn't exercise.** WS-1 (unsorted
  input) and WS-6 (server-push decode surface) were both invisible to passing suites. Ask "what input
  would break this that no test sends?" — then require that test.

- **Prefer the fix that makes the invariant true by construction over the fix that enforces it by
  check.** The `[n] IMPLICIT CHOICE` (WS-4) beats `SEQUENCE{tag,body}` not because it's prettier but
  because it makes a lying encoding *unrepresentable* — there is no second discriminator to disagree.
  Declaring `SET OF` (Option A) beats a hand-rolled sort-check because a stock tool enforces the order.
  Construction > vigilance.

- **Distinguish the total-order hazard from the cross-implementation-agreement hazard.** They look
  alike (both about sorting) and have different fixes (WS-3). Precision about *which* property is
  threatened is what makes a finding actionable rather than alarming.

- **Report faithfully and separate "verified" from "recommended."** Mark what you re-verified against
  current source (`[verified]`) vs what you infer. When the coordinator hands you a premise that turns
  out stale (WS-3: the cited commit predated the fix), say so plainly and review the *actual* HEAD.

- **The module and the spec must agree, and both must match the built codec.** A wire standard with
  three sources of truth (prose, ASN.1 module, code) is only conformance-ready when all three say the
  same thing. Sync them as one mergeable unit; a divergence is a latent interop bug.

- **Trust the documented artifact.** A hand-owned, security-critical, cross-language wire needs a
  *normative* spec before it ships (the STD-006 discipline extended to STD-010). "Interop is
  unverifiable without a normative spec" — a peer implementing "from the doc alone" is the test of
  whether the doc is done.

---

## 5. WAR STORIES — the defects and the tell that surfaced each

- **WS-1 — reject-non-canonical on decode (collection codec).** *Tell:* decode path had a `== 0`
  duplicate check but no ascending-order check. A hand-crafted `set:int` of `3,2,1` decoded clean.
  *Ruling:* mandatory (X.690 §10.1 unique encoding + §11.6 restriction clause + principle 6),
  pre-merge. *Why it mattered:* accepting it breaks receiver round-trip (decode→re-encode ≠ input),
  which is the "one encoding per value" invariant failing at the receiver — the foundation under
  signatures, digests, and Entry byte-matching.

- **WS-2 — UDS fail-closed leak (transport review).** *Tell:* the F1 fix made `restrictPermissions`
  throw *after* a successful bind, but the `if (!ok)` finally only closed the channel — it didn't
  unlink the just-bound socket file. *Result:* a failed-chmod listen leaves an *unprotected bound
  socket* on disk — exactly the world-accessible control socket F1 exists to prevent. *Lesson:* a
  security fix can open a new hole on its own error path; trace the failure path, not just the happy
  path. (Also flagged the fail-open server block missing a final `else` — an unrecognised transport
  skipping cert validation.)

- **WS-3 — map sorted by whole entry, not key (collection codec).** *Tell:* `octetSort(entryTlvs)`
  instead of sorting by a parallel `keyTlvs` list. *Nuance caught:* the prefix-of-distinct-keys case
  doesn't break the total order (length octet diverges first, keys unique) — so it's a
  cross-implementation-agreement hazard, not a total-order hazard. *Also caught:* the coordinator
  cited a commit (`f2ac15050`) that predated the HEAD fix (`c6adec2f1`) — reviewed actual HEAD and
  reported the discrepancy rather than the stale premise.

- **WS-4 — `Any` element as `SEQUENCE{ENUMERATED tag, body}` (type-model review).** *Tell:* an
  explicit `ENUMERATED tag` bolted onto a body whose universal tag already discriminates the category
  — a redundant discriminator that admits a lying encoding, and a `body Element` stub that mis-models
  a tag-varying open type. *Recommendation:* `[n] IMPLICIT CHOICE` keyed on category (one
  discriminator, distinct-tag-clean, no wrapper, smaller, sort-grouping provable), with the collection
  arm `[n] EXPLICIT` to preserve the inner `0x31`/`0x30` discipline (§31.2.7).

- **WS-5 — QUIC-TLS facade home + trust-dispatch (export-surface reviews).** *Tell:* a proposal to
  home a `java.base` TLS facade near authorization code. *Rulings:* reject
  `org.apache.river.api.security` (split-package landmine + category mismatch) and `javax.net.ssl`
  (squatting); home it at `au.zeus.jdk.net.ssl` (java.base-only, split-free). Separately, the crux
  verification: the JGDMS 2-arg `checkServerTrusted` body *does* perform full SPIFFE/X.500 peer-auth
  (not a stub) — so the 2-arg path was *safe*, but it *drops algorithm-constraint enforcement*, which
  is why the 3-arg `SSLEngine`-adapter path (constraints carry) was the right ratified choice; and the
  server-block missing-`else` fail-open was elevated to a REQUIRED fix (peer-cert validation
  fail-open on an untrusted-network mTLS path is unacceptable).

- **WS-6 — STD-010 directional DER-bound gap + §7.2 citation (conformance-gate review).** *Tell:*
  §8.2's size-bound MUST was written entirely in the client-initiated frame ("request body on send
  side, response on receive side") and rev.2 had just added *server-initiated* streams — making the
  *client* a new DER-decode surface that §8.2 didn't name. *Also:* verified all five `§7.3`
  occurrences were now *corrective* references (SOWs' miscite → correct §7.2), zero live miscites
  remaining, and confirmed the correction itself against the STD-006 doc (§7.2 = ACC, §7.3 =
  DigestCodeSourceRecord). *Verdict:* conformance-ready as a P3 gate; fold the both-directions bound
  sentence in on the next pass; hold ack-octet-encoding and error-code values as interop-gates.

- **WS-7 — closed-subset type algebra + `getGenericType()` mechanism (type-model review).** *Tell:*
  a claim that "erasure means we can't get `Set<X>`'s `X`." *Correction:* erasure erases *instances*,
  not *declarations* — `Field.getGenericType()` reads the class-file Signature attribute, a
  compile-fixed, digest-stable, cross-runtime-reproducible artefact (the same mechanism Jackson/Gson/
  Spring use). *Verdict:* the derived element wire-type is digest-covered and deterministic; the sole
  irreducible boundary (a type variable in a generic `@AtomicSerial` class → resolves to `Any`) is an
  *honest* boundary an annotation couldn't fix either — flagged that `Box<Foo>` and `Box<Bar>` become
  schema-indistinguishable at the collection level (element digests survive in the bodies).

---

## 6. A closing note to my successor

The value you add is not finding *bugs* — the suite finds bugs. Your value is finding the encodings
that are *wrong but pass*: the unsorted SET OF that round-trips, the redundant tag that admits a lie,
the size-bound that silently doesn't cover the new direction, the split-package that only breaks on a
`--release` build months later. Those are invisible to green tests and visible only to someone who
holds "one canonical encoding per value" as a reflex and asks, every time, "what does this let two
conformant implementations disagree about?" Keep that reflex. Cite the clause. Rule mandatory when
it's mandatory, and say pre-merge when leniency would calcify. And when three sources of truth exist
— prose, module, code — make them agree before you sign off.

It was an honour to keep the wire honest.
