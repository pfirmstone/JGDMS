# Feasibility investigation: a conformant JERI ATOMIC DER peer in Haskell (codec + stream schema dedup + DETERMINISTIC CEL)

**Status:** research / feasibility investigation. **Date:** 2026-07-21.
**Companions:**
`docs/der-haskell-collection-mapping.md` (the Haskell collection-discipline mapping this
investigation builds on),
`docs/JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md`,
`docs/JGDMS-STD-006-Appendix-C-Stream-Schema-Dedup-v0.1-DRAFT.md` (note: v0.2 content),
`docs/JGDMS-STD-011-CEL-Filter-Expression-Format-v0.1-DRAFT.md`,
`SOW-DER-Stream-Schema-Dedup.md`.
**Question answered:** could a conformant JERI ATOMIC DER peer — including the Appendix C
stream schema dedup layer and a DETERMINISTIC CEL evaluator passing STD-011 §13's
bit-identical conformance regime — be implemented in Haskell? This is a qualitative
feasibility assessment grounded in the normative requirements and in Hackage/GHC evidence
fetched 2026-07-21, with every claim labelled verified or inferred (§9).

---

## 0. Verdict up front

**Yes, with caveats — a conformance-passing Haskell peer is technically feasible in all four
layers, and no layer contains a Haskell-specific blocker.** The genuinely hard parts:

1. **The JERI transport layer** — hard by *scope*, not by language (§8).
2. **The strictness discipline** that the fail-closed decode obligations impose on a lazy
   language — a pervasive engineering discipline, not a wall (§2).
3. **Correctly-rounded transcendentals** (STD-011 §7.5, RATIFIED) — solvable the same way as
   Rust (FFI to CORE-MATH), but with **no pre-made Hackage binding** (verified absent by
   search). The FFI itself is ~7 trivial `foreign import ccall` declarations of pure
   `double → double` functions, so this is a day of work plus build-system C integration,
   not a research problem (§5).

Difficulty relative to the planned Rust T4 port: **qualitatively comparable, modestly harder
overall.** Haskell wins on codec ergonomics — ADTs model the closed `CHOICE`/tag registries
and reject-unknown-enumerant semantics natively, and pervasive immutability gives Appendix
C's snapshot/verify-once semantics for free. It loses on the laziness/DoS discipline, the
missing CORE-MATH binding, and — the real-world constraint for a lean company — a *third*
language peer to maintain forever (§10).

Nothing here argues the peer *should* be built. It argues the standards' G9 claim —
implementable from the documents alone, in any language — survives contact with Haskell.

---

## 1. The normative requirements being assessed against

The obligations this investigation checked Haskell against, by source:

- **STD-006 v0.13:** DER-not-BER, one encoding per value (principle 2); bounded before
  allocation with the §4.5 ceiling table (`maxCollection` 65536, `maxFields` 65535, etc. —
  inclusive fenceposts); fail-secure decode, no permissive fallback (principle 6); the §3.8
  collection disciplines (PRESERVE / CANONICALISE / CANONICALISE_MULTISET, `SEQUENCE OF`
  0x30 vs `SET OF` 0x31, decoder MUST reject unsorted canonicalise collections, wrong
  container tags, duplicates); no `DEFAULT`; canonical scalar forms.
- **Appendix C (stream schema dedup):** the three ratified construction rules (§C.3.1 — no
  cross-value context, values never compressed, dedup over the public structural layer
  only); the pinned pre-order first-occurrence rule and its normative traversal order
  (§C.6.2), including the P1 buffering consequence for one-pass decoders; verify-once decode
  with the digest computed over received leaf-record bytes (§C.7.3); fail-closed resolution —
  unknown digest / duplicate full form / ceiling breach ⇒ whole-stream reject, never a
  fetch or fallback (§C.7.4); cross-stream isolation (§C.7.5); the §C.8 ceilings metered
  *inside the accumulating loop* (§C.8.2); the §C.9 negotiation.
- **STD-011 (DETERMINISTIC CEL):** §4.3.1 checked int64 (`OVERFLOW` at every ±2⁶³ edge,
  including the pinned `(−2⁶³) % (−1)` case); §4.3.2 IEEE-total binary64 operators; §4.3.3
  exact int×double cross-type comparison; §4.3.4 NaN/signed-zero pins and boundary NaN
  canonicalization to `0x7FF8000000000000`; §4.4 code-point string semantics (ordering ≡
  UTF-8 byte order); §7.5's RATIFIED requirement that `sin`/`cos`/`tan`/`asin`/`acos`/
  `atan`/`atan2` be **correctly rounded**; §10's static cost model with §10.6's ≥64-bit
  checked cost arithmetic; §13's conformance regime — bit-identical outcomes on the
  corpus, **no tolerances anywhere**.

---

## 2. (a) Canonical DER codec — feasible; laziness is the one real discipline item

The core obligations — one encoding per value, reject-non-canonical decode (minimal
definite lengths, X.690 §11.6 `SET OF` octet order, no-`DEFAULT`, canonical scalar forms
per §C.7.3's "received bytes *are* the canonical bytes" pin), bounded-before-allocation,
fail-secure reject — are all pure functions over strict bytes, which is Haskell's best
register. Strict `ByteString` gives O(1) slicing, so definite-length TLV parsing is
natural: parse header, bounds-check the length against the ceiling *before* slicing,
recurse on the slice. The ceilings-metered-during-decode requirement (§C.8.2's "inside the
loop that accumulates") is a strict state counter threaded through the parser — standard
practice in `attoparsec`/`flatparse`-style parsers or a hand-rolled reader mirroring the
Java `DerReader`. Nothing in the reject-non-canonical or definite-length rules is awkward
in Haskell; closed tag registries and closed enumerations map onto ADTs whose pattern
matches reject unknowns by construction.

**Laziness is a genuine DoS/correctness surface, with a known discipline.** Two directions:

- **Decode:** a decoder that returns lazily-built structures defers both validation work
  and memory accounting — thunk buildup means "bounded before allocation" can be silently
  violated by suspended computation retaining the input buffer. The discipline:
  `StrictData`/bang patterns on every decoded record type, `Data.Map.Strict` (never Lazy)
  for decoded collections, and forcing decoded values to normal form (`NFData`) before the
  codec returns — decode-to-strict-snapshot. This matches
  `der-haskell-collection-mapping.md` §6.5's conclusion for the encode side. It is
  pervasive (every type, every field) but mechanical, and it is the strictness posture
  high-assurance Haskell parsing code (e.g. the `tls` package) already takes.
- **Encode:** forcing is where bottoms surface; the encoder must force to normal form
  before emitting the first octet, so a divergent or throwing thunk cannot truncate a
  half-written stream.

One pleasant alignment: Appendix C §C.6.2's normative buffering consequence — "a
conformant decoder therefore processes each P1 record TLV as a unit — buffer the record's
fields (the payload octets held unparsed), process the record's own chain site, *then*
parse the payload interior" — *forbids* fully-incremental streaming decode at exactly the
place laziness would tempt it. The spec itself mandates the strict whole-TLV shape a
careful Haskell implementation wants anyway.

---

## 3. (b) Collection disciplines — established by the companion mapping doc

Per `der-haskell-collection-mapping.md`: every STD-006 §3.8 discipline is expressible in
Haskell, and the discriminator transfers with **zero reclassifications** (as it did for
Rust). What a codec author imports:

- **`containers`** (GHC boot library — ships with every GHC): all PRESERVE forms —
  `list:` via `[a]`/`NonEmpty`/`Seq`, `orderedset:`/`orderedmap:` via
  `Data.Set`/`Data.Map`/`Data.IntSet`/`Data.IntMap`.
- **`unordered-containers`** (+ `hashable`): to *originate* `set:`/`map:` values
  (`Data.HashSet`/`Data.HashMap`).
- **`pqueue`** (or `heaps`): the `bag:` producer.
- **`ordered-containers`**: insertion-ordered PRESERVE (`OMap`/`OSet`).

The one std-tier gap is the **inverse of Rust's**: base+containers has no
canonicalise-side producer type (Haskell's defaults are the sorted containers; the hash
containers are the opt-in package). Because the canonicalise encoding of a sorted
container is just an octet re-sort into the declared `SET OF`, every *discipline* is
writable from the boot libraries alone — only the idiomatic source types need packages.

Bonus: universal immutability means the encoder can never observe a torn collection — the
concurrent-live-view hazards §3.8 worries about for Java do not exist; every Haskell value
is already a snapshot.

---

## 4. (c) Numeric semantics — clean, with genuine Haskell advantages and one platform pin

- **Checked int64 (§4.3.1).** GHC's `Int64` wraps silently — the same trap as Java's raw
  operators and Rust's release mode, so STD-011's normative warnings apply verbatim to
  Haskell too. *(Flag: the fetched base-4.22 `Data.Int` page contains no verbatim
  "arithmetic is performed modulo 2^n" sentence; silent wrap is well-known GHC behaviour
  but is reported here as not verbatim-verified — §9.)* The checked path is actually
  *easier* than in Java or Rust: base's arbitrary-precision `Integer` is built in, so
  `+ - * / % abs` and negate can be computed exactly in `Integer` and range-checked
  against ±2⁶³ — trivially correct for every pinned edge case, including `(−2⁶³)/(−1)` and
  the pinned `(−2⁶³) % (−1) ⇒ OVERFLOW` special case (a one-line explicit check in any
  language). GHC also has overflow-reporting primops (`addIntC#` etc.) if performance
  demands them (inferred, not verified). §10.6's ≥64-bit checked cost arithmetic:
  `Integer`, done — overflow-to-reject is trivial when the accumulator cannot overflow.
- **IEEE binary64 (§4.3.2 / §4.3.4).** GHC `Double` is IEEE binary64 on all tier-1
  (64-bit) platforms. base provides `isNaN`, `isNegativeZero`, `isIEEE`, and — verified
  present in GHC.Float with signatures — **`castDoubleToWord64` / `castWord64ToDouble`**:
  exactly what boundary NaN-canonicalization to `0x7FF8000000000000` and exact-bit-pattern
  emission need. There is **no `copySign` in base** (verified absent from the GHC.Float
  listing) — irrelevant in practice: §7.2's `abs(double)` = "clear sign bit" is one
  bit-cast-and-mask. The `Double.compare`-class trap (§4.3.4's normative warning) exists
  in Haskell too: `compare` on `Double` is not the required IEEE predicate for NaN — the
  evaluator must use the raw `<`/`==` operators (compiled to IEEE comparison instructions)
  plus explicit NaN/signed-zero handling, the same audit posture STD-011 already mandates
  for Java and Rust.
- **Exact int×double comparison (§4.3.3).** Haskell is arguably the *best* of the three
  languages here: `toRational :: Double -> Rational` is exact for finite doubles, and
  comparing a `Rational` against an `Integer` is exact by construction — the §4.3.3 killer
  cases (`9007199254740993 > 9007199254740992.0`, etc.) fall out correctly with no
  cleverness. (The spec's §4.3.3 threshold algorithm works too.)
- **Platform pin (the x87 question).** GHC has a `-fexcess-precision` flag whose existence
  implies the default is excess-precision-OFF, and 32-bit x86/x87 code generation is the
  historical deviation surface. A conformant Haskell peer should be **pinned to 64-bit
  tier-1 targets** (SSE2 semantics), where GHC does no contraction or fast-math by
  default. *(Flag: this paragraph is inferred from GHC flag inventory and folklore, not
  verified against the current GHC user's guide — verify before relying; §9.)* Rust
  carries the same 32-bit-x87 asterisk, so this is a platform pin both non-JVM ports
  need, not a Haskell demerit.

---

## 5. (d) Correctly-rounded transcendentals (§7.5, RATIFIED) — FFI to CORE-MATH, same as Rust

Hackage was searched: **no existing CORE-MATH binding exists** (the `rounded` package binds
MPFR — correctly-rounded *arbitrary-precision*, useful as the §13.4 independent oracle,
wrong shape and speed for the evaluator; `cmath` binds the ordinary C math library, which
is non-conformant). GHC's own `sin`/`cos`/… delegate to the platform libm — not correctly
rounded, platform-varying, and MUST NOT be used (§7.4's problem statement applies to GHC
exactly as to glibc/MSVC/`StrictMath`).

The realistic path is exactly Rust's: link the CORE-MATH C routines (`cr_sin`, `cr_cos`,
`cr_tan`, `cr_asin`, `cr_acos`, `cr_atan`, `cr_atan2`) and bind them with
`foreign import ccall unsafe`. Pure `double → double` functions are the *easiest possible*
FFI case in Haskell — no marshalling, no callbacks, importable as pure functions so
referential transparency is preserved honestly. The cost is build-system plumbing
(vendoring the C sources in the cabal package), not design.

**Shared-implementation point:** both non-JVM peers (Rust T4 and a hypothetical Haskell
peer) would then share **one C implementation** of the seven ratified functions — which
also de-risks T5 cross-language agreement for §7.2 rows 12–14: any divergence would be a
binding bug, not a reimplementation divergence.

---

## 6. (e) Strings (§4.4) — natural fit, one sidestep available

`text-2.1.4` uses **UTF-8 internally** (the package page confirms the UTF-16→UTF-8
transition, landed in text-2.0). §4.4's theorem — code-point order is byte-identical to
UTF-8 byte order — means a Haskell evaluator can compare the *wire bytes* (`ByteString`)
directly and never trips a `compareTo`-class trap; `Data.Text`'s code-point operations
(`length` counts code points) match `size(s)` natively; `contains`/`startsWith`/`endsWith`
are directly available at either the `Text` or the byte level. *(Flag: `Text`'s `Ord`
instance semantics were not verbatim-verified — §9 — but the safe implementation compares
UTF-8 bytes per the spec's own license to do so, making that verification unnecessary.)*
Java's UTF-16 code-unit trap (§4.4's normative warning) has **no Haskell analog** under
text-2.x.

---

## 7. (f) SHA-256 and the dedup table — non-issue

`crypton` (the maintained cryptonite fork) provides C-backed SHA-256 at constant-factor
parity with JVM/Rust implementations (common knowledge, not re-verified this session —
§9). The Appendix C per-stream pattern — hash each `fullChain`'s received leaf-record
bytes exactly once (§C.7.3's pinned digest preimage), intern `digest → chain bytes` in a
strict per-codec-lifetime table, O(distinct-chains) hashing, `chainRef` = strict map
lookup, fail-closed on unknown digest and on duplicate full form — is a small strict
`Map ByteString ByteString` (or `HashMap`) plus the four §C.8 counters
(`maxDistinctChainsPerStream`/`maxChainRecords`/`maxChainBytes`/`maxDedupTableBytes`),
each enforced at insertion per §C.8.2. Immutability makes §C.7.5's cross-stream isolation
(a table is just a value scoped to one codec) and §C.7.6's interned-buffer sharing
(`ByteString` slices share storage natively) essentially free. The traversal-order rule
(§C.6.2), occurrence granularity (§C.6.3), and the `[8]`-proxy-interior exclusion
(§C.6.5) are decode-structure logic, language-neutral.

---

## 8. (g) Transport, and the honest effort ranking

`tls-2.4.3` is a native Haskell TLS implementation — "Native Haskell TLS 1.2/1.3 protocol
implementation for servers and clients" (verified from the package page; client-certificate
/ mTLS support believed present but not verified this session — §9; FFI to OpenSSL exists
as a fallback), and the `network` package covers sockets. The *ingredients* exist.

**Effort ranking, hardest first:**

1. **JERI transport/invocation layer** — hardest, by scope not language: the Jini ERI
   multiplexing protocol, invocation and constraint semantics, endpoint negotiation
   (including Appendix C §C.9's export-affirmed capability + in-band stream marker +
   response echo), and enough of the surrounding object model to be a *peer* rather than a
   codec. This is exactly as hard in Rust. **It is also the one area NOT covered by the
   G9-standalone documents:** the DER, dedup, and CEL specs are written to be
   implementable from the document alone; the mux/invocation layer is not specified to
   that standard, so a non-JVM implementer would be reading trunk Java as the reference —
   the largest single feasibility risk for *any* non-JVM peer, Haskell or Rust.
2. **CEL evaluator** — a small closed language (no loops, no user functions, a fixed node
   inventory, a closed ratified function registry), so structurally easy; the difficulty
   is concentrated exactness engineering: every §13.3 edge (checked-int edges, exact
   cross-type comparison, NaN/−0.0 pins, supplementary-plane ordering, `has()`/null/absent
   matrix, §6.4 error-identity table, §10.6 cost-width case) under the bit-identical,
   no-tolerance corpus regime. Haskell's `Integer`/`Rational` and ADT-based error
   modelling (`Either ErrorCode Value` makes §6.4's absorption table direct) genuinely
   help here.
3. **DER codec** — medium: mechanical once the reader/writer core and the §2 strictness
   discipline are set; ADTs map 1:1 onto the closed `CHOICE`/tag registries with
   reject-by-construction pattern matching.
4. **Dedup layer** — smallest: a table, four ceilings, a traversal rule, and a negotiation
   marker, all bolted onto an existing codec.

---

## 9. Verified vs inferred — the honesty ledger

**Verified this session (2026-07-21):**

- The normative requirements themselves: STD-006 v0.13 §3 (principles), §3.8, §4.5;
  Appendix C §C.3, §C.6, §C.7, §C.8; STD-011 §4.3, §4.4, §7, §10, §13 — read from the
  repo documents.
- `castDoubleToWord64` / `castWord64ToDouble` present in base-4.22 `GHC.Float` (signatures
  fetched); `copySign` **absent** from the same module listing; `isNaN` /
  `isNegativeZero` / `isIEEE` present with doc text.
- `text-2.1.4`: the UTF-16→UTF-8 internal-representation transition (package page).
- `tls-2.4.3`: "Native Haskell TLS 1.2/1.3 protocol implementation for servers and
  clients" (package page).
- **No CORE-MATH binding on Hackage** (targeted search; `rounded` = MPFR, `cmath` =
  ordinary libm).
- The collection-discipline results (verified in the companion
  `der-haskell-collection-mapping.md`, with its own per-source citations and reviewer
  flags).

**Inferred / flagged, NOT verbatim-verified (kept flagged deliberately):**

1. `Int64` silent wrap-on-overflow — well-known GHC behaviour, but no verbatim
   "modulo 2^n" sentence was found on the fetched base-4.22 `Data.Int` page.
2. GHC excess-precision / x87 status and the `-fexcess-precision` flag default — inferred
   from flag inventory and folklore; verify against the current GHC user's guide before
   relying on the 64-bit-tier-1 platform pin's sufficiency.
3. `Text`'s `Ord` instance exact semantics — sidestepped by comparing UTF-8 bytes, which
   §4.4 explicitly licenses.
4. hs-tls mTLS / client-certificate specifics — believed supported, not verified.
5. `crypton` SHA-256 performance parity — common knowledge, not measured.
6. GHC checked-arithmetic primops (`addIntC#` etc.) — remembered, not fetched; the
   `Integer`-widening checked path does not depend on them.

None of the flagged items sits on the critical path of the verdict: the two that could
theoretically bite (float excess precision; mTLS support) have cheap mitigations (pin to
64-bit tier-1 targets; OpenSSL FFI).

---

## 10. Scope note — possibility, not recommendation

This investigation documents **possibility**: a Haskell peer is implementable, the G9
"from the documents alone, in any language" claim holds for the three layers those
documents cover, and several requirements (exact rational comparison, immutable
snapshots, ADT-closed registries) are *easier* in Haskell than in the reference or the
planned Rust port. It is **not** a recommendation to build one. The binding real-world
constraint is not technical: a third-language peer is a permanent maintenance surface —
corpus tracking across spec revisions, toolchain and dependency upkeep, a second FFI
consumer of the CORE-MATH vendoring — carried by a small company whose priority is the
core authorization scope. The planned Rust T4 path already exercises the
cross-language claims; this document's value is as independent evidence that the
standards' language-neutrality is real, and as a map of where a future non-JVM peer (in
any language) will find the effort concentrated: the under-specified transport layer
first, exactness engineering second, everything else mechanical.
