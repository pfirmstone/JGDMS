# Mapping STD-006 §3.8 collection-ordering onto Haskell collection types

**Status:** research / cross-language design note. Companion to
`docs/JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md` §3.8 ("Encounter order and serialized
equality"), `docs/der-collection-ordering-research.md`, and
`docs/der-rust-collection-mapping.md` (the Rust analog of this note, whose structure and
honesty conventions this note mirrors).
**Author:** research pass, 2026-07-21.
**Scope:** how a *future, language-neutral* Haskell codec of the STD-006 DER wire format would
classify Haskell collections into the **same** determinism disciplines the Java reference codec
uses — so that equal values encode to identical DER across a Haskell peer, a Rust peer, and a
Java peer.

---

## 0. The rule being mapped FROM (one sentence)

STD-006 §3.8 chooses a collection field's element-order **discipline** by a single
discriminator applied to the **declared type**: *does the declared type guarantee a
DETERMINISTIC, machine-independent iteration order?*

- **Yes → PRESERVE** — emit in iteration order (the order is part of the value, or is a
  deterministic function of the elements). Never reorder.
- **No → CANONICALISE** — octet-sort the element encodings (X.690 §11.6: encode each element
  to canonical DER, sort the complete encodings as unsigned octet strings, shorter notionally
  0x00-padded at the trailing end for the comparison only). This makes equal values → identical
  bytes.
- **No, but duplicates are legitimate → CANONICALISE_MULTISET** — same octet-sort, but do
  **not** dedupe (the source may legitimately hold equal elements, e.g. a priority queue).

The `jgdms-der` codec carries the discipline in a schema wire-type **token**:

| token | discipline | meaning |
|---|---|---|
| `orderedset:<elemWT>` | PRESERVE (set) | deterministic-order set; iteration order preserved |
| `list:<elemWT>` | PRESERVE (list/deque) | positional / order-is-the-value; iteration order preserved |
| `orderedmap:{keyWT}{valWT}` | PRESERVE (map) | deterministic-order map; entry order preserved |
| `set:<elemWT>` | CANONICALISE (set) | non-deterministic set; elements octet-sorted, dups illegal |
| `bag:<elemWT>` | CANONICALISE_MULTISET | non-deterministic multiset; elements octet-sorted, dups kept |
| `map:{keyWT}{valWT}` | CANONICALISE (map) | non-deterministic map; entries octet-sorted by encoded key |

The mapping below says which token each Haskell type maps to, **decided by the same
discriminator** (deterministic iteration order?), plus each type's closest Java and Rust
analogs.

**A note on "standard" in Haskell.** Haskell has no single std the way Rust does. Three tiers
matter here: **`base`** (the true standard library — it has almost no collections beyond lists);
**`containers`** (a GHC *boot library*, shipped with every GHC — `Data.Map`, `Data.Set`,
`Data.IntMap`, `Data.Sequence`; the de-facto std tier); and **ordinary Hackage packages**
(`unordered-containers`, `pqueue`, `heaps`, `ordered-containers`,
`insert-ordered-containers`). "Std-only" below means `base` + `containers`.

---

## 1. The mapping table

> Column meanings as in the Rust note: **Deterministic order?** is the sole discriminator —
> does the Haskell type *guarantee* a deterministic, machine-independent iteration order.
> Analogs name the reference-side Java type and the Rust type from the companion note.

### base + containers (the "std" tier)

| Haskell type | Deterministic order? | STD-006 discipline | Wire token | Java analog | Rust analog | Notes |
|---|---|---|---|---|---|---|
| `[a]` (list, `base`) | **Yes** — positional; order **is** the value | PRESERVE (list) | `list:<elemWT>` | `List`/`ArrayList` | `Vec<T>` | `Eq` is order-sensitive (derived structural equality — Haskell Report semantics). **Laziness wrinkle:** the type does not guarantee finiteness; encoding forces the spine, so an infinite list is a non-terminating encode. The schema `SIZE` bound is the defence (§6.5). |
| `Data.List.NonEmpty` (`base`) | **Yes** — positional (`data NonEmpty a = a :| [a]`) | PRESERVE (list) | `list:<elemWT>` (SIZE ≥ 1) | `List` (non-empty invariant) | `Vec<T>` (non-empty by construction) | Derived `Eq` — order-sensitive. The non-emptiness is a *bound* (min `SIZE` 1), which is exactly what STD-006 §3.8 says the wire may carry. Verified: base-4.22.0.0 declares `Eq a => Eq (NonEmpty a)`. |
| `Data.Sequence.Seq` (`containers`) | **Yes** — positional; "a finite sequence of values"; 2-3 finger trees | PRESERVE (list) | `list:<elemWT>` | `ArrayDeque` (as `Deque`) / `List` | `VecDeque<T>` | Docs: "Whereas lists can be either finite or infinite, sequences are always finite." Left-to-right positional iteration; `Eq` order-sensitive. The idiomatic Haskell deque/vector; always safe to snapshot. |
| `Data.Map.Strict` / `Data.Map.Lazy` (`containers`) | **Yes** — "Folds in order of increasing key" / "Traverses in order of increasing key" (`Ord k`) | PRESERVE (map) | `orderedmap:{keyWT}{valWT}` | `TreeMap`/`SortedMap` | `BTreeMap<K,V>` | Size-balanced binary search tree sorted by `Ord`. Entry order is element-derived → deterministic, **no §3a tension**. Strict and Lazy share one `Map` type — the distinction is evaluation, invisible on the wire (§6.5). Requires lawful `Ord` (§6.6). |
| `Data.Set` (`containers`) | **Yes** — `toList = toAscList`; `foldr f z == foldr f z . toAscList`; `elems` "an alias of `toAscList`" | PRESERVE (set) | `orderedset:<elemWT>` | `TreeSet`/`SortedSet` | `BTreeSet<T>` | Sorted by `Ord a` — the analog of Java needing `Comparable` and Rust's `T: Ord`. Element-derived order → no tension. |
| `Data.IntMap` / `Data.IntSet` (`containers`) | **Yes** — "Folds in order of increasing key"; big-endian Patricia trees | PRESERVE (map/set) | `orderedmap:` / `orderedset:` | `TreeMap<Integer,V>` / `TreeSet<Integer>` | `BTreeMap<i64,V>`-ish | Key type is fixed `Int` — no `Ord` instance is even consulted; ascending signed-`Int` order is intrinsic. Note `Int` is platform-width (≥ 30 bits, 64 on GHC/64-bit) — a *value-range* wire concern for the key encoding, not an ordering concern. |

### unordered-containers (the hash tier — NOT a boot library)

| Haskell type | Deterministic order? | STD-006 discipline | Wire token | Java analog | Rust analog | Notes |
|---|---|---|---|---|---|---|
| `Data.HashMap.Strict`/`.Lazy` | **No** — "The order of its elements is unspecified, and it may change from version to version of either this package or of `hashable`"; "A `HashMap` makes no guarantees as to the order of its elements" | CANONICALISE (map) | `map:{keyWT}{valWT}` | `HashMap` | `HashMap<K,V>` | HAMT (hash array mapped trie). See §2 — the case differs in *mechanism* from Rust (no per-instance random seed) but the discriminator's answer is the same and just as decisive. `Eq` is content-equality, order-insensitive. |
| `Data.HashSet` | **No** — same wording as `HashMap` ("makes no guarantees as to the order of its elements") | CANONICALISE (set) | `set:<elemWT>` | `HashSet` | `HashSet<T>` | Implemented over `HashMap` (unit values, `toMap`/`fromMap` O(1)). Octet-sort; dups illegal by construction. |

### Priority queues (the CANONICALISE_MULTISET row — needs a package)

| Haskell type | Deterministic order? | STD-006 discipline | Wire token | Java analog | Rust analog | Notes |
|---|---|---|---|---|---|---|
| `pqueue`: `Data.PQueue.Min.MinQueue` | **No** — ascending-order iteration but "This implementation does not guarantee stable behavior" (tie order among `Ord`-equal elements unspecified) | CANONICALISE_MULTISET | `bag:<elemWT>` | `PriorityQueue` | `BinaryHeap<T>` | Binomial heap. Duplicates legitimate and retained. See §3 — Haskell's queues iterate *nearly* sorted (much closer to deterministic than Java/Rust heap-array iteration), but the tie caveat means the declared type does not guarantee determinism → canonicalise. |
| `heaps`: `Data.Heap.Heap` | **No** — `Foldable` folds min-first via `deleteMin` (sorted by the heap's **stored** `leq` function; tie order unspecified); `toUnsortedList` "in some arbitrary, very likely unsorted, order" | CANONICALISE_MULTISET | `bag:<elemWT>` | `PriorityQueue` | `BinaryHeap<T>` | **Wire-format red flag:** `Heap a` *stores its comparison function inside the value* (`Heap !Int (a -> a -> Bool) !(Tree a)`). A function is behaviour, not a value — STD-006 §3.8 "values and bounds, never behaviour" makes the comparator receiver-side code; only the element multiset is transmissible. See §3. |

### Insertion-ordered containers (the §3a-tension hunt — needs a package)

| Haskell type | Deterministic order? | STD-006 discipline | Wire token | Java analog | Rust analog | Notes |
|---|---|---|---|---|---|---|
| `ordered-containers`: `Data.Map.Ordered.OMap` | **Yes** — insertion order (tag-indexed shadow map); non-concurrent | PRESERVE (map) | `orderedmap:{keyWT}{valWT}` | `LinkedHashMap` | `indexmap::IndexMap` | **`Eq` is order-SENSITIVE** (verified source: `(==) = (==) \`on\` assocs`, and `assocs` returns pairs in insertion order). So — unlike Java and Rust — bytes ⇔ `==` holds even here. See §4: the §3a tension does **not** reproduce. |
| `ordered-containers`: `Data.Set.Ordered.OSet` | **Yes** — insertion order | PRESERVE (set) | `orderedset:<elemWT>` | `LinkedHashSet` | `indexmap::IndexSet` | **`Eq` order-SENSITIVE** (verified source: `(==) = (==) \`on\` toList`; `Foldable` folds the tag-indexed map, i.e. insertion order). Same §4 finding. |
| `insert-ordered-containers`: `Data.HashMap.Strict.InsOrd.InsOrdHashMap` (0.3.0) | **Yes** — "folds and traverses in insertion order" (`toList` sorts by a per-entry insertion index) | PRESERVE (map) | `orderedmap:{keyWT}{valWT}` | `LinkedHashMap` | `indexmap::IndexMap` | **`Eq` order-SENSITIVE in the released 0.3.0** (verified raw source: `a == b = toList a == toList b`). **Version caveat — see §4 flag:** a fetch of the GitHub master branch returned the order-*insensitive* form instead; pin the version. |

**Key alignment observation.** In every row, the Haskell discriminator produces the **same
discipline** the Java reference assigns to the analog — the rule transfers without a single
reclassification, exactly as it did for Rust. `[a]`/`NonEmpty`/`Seq` → PRESERVE list.
`Map`/`Set`/`IntMap`/`IntSet` → PRESERVE (sorted), exactly as `TreeMap`/`TreeSet`/`BTreeMap`/
`BTreeSet`. `HashMap`/`HashSet` → CANONICALISE, exactly as Java `HashMap`/`HashSet` and Rust
`HashMap`/`HashSet`. `MinQueue`/`Heap` → CANONICALISE_MULTISET, exactly as `PriorityQueue`/
`BinaryHeap`. `OMap`/`OSet`/`InsOrdHashMap` → PRESERVE (insertion), exactly as
`LinkedHashMap`/`IndexMap`. What differs from Rust is not any classification but (a) *which*
side of the rule the language's defaults sit on (§2), and (b) the `==` semantics of the
insertion-ordered types (§4).

---

## 2. `Data.HashMap`/`Data.HashSet` — CANONICALISE, by a different route than Rust

Both the Java and Rust hash containers canonicalise; Haskell's do too, but the *mechanism* of
non-determinism differs and is worth recording precisely, because the task-relevant question —
"if iteration were a deterministic function of the key set for a fixed hash, would the
discriminator still say CANONICALISE?" — gets a sharp answer here.

1. **No per-instance randomisation.** Unlike Rust's `RandomState` ("each `HashMap` instance
   uses a different seed"), `unordered-containers` + `hashable` are **deterministic within a
   fixed build by default**: same `hashable` version, same architecture, same default salt →
   same hashes → same HAMT shape. `hashable` *does* ship an opt-in cabal flag whose
   description reads: *"Randomly initialize the initial seed on each final executable
   invocation This is useful for catching cases when you rely on (non-existent) stability of
   hashable's hash functions"* — i.e. randomisation exists but as a *testing tool for smoking
   out exactly the assumption a PRESERVE mapping would need*.

2. **But the declared type disclaims the order outright.** `Data.HashMap.Strict.toList`:
   *"The order of its elements is unspecified, and it may change from version to version of
   either this package or of `hashable`."* And the type overview: *"A `HashMap` makes no
   guarantees as to the order of its elements."* The discriminator asks what the declared type
   **guarantees**; the answer is printed verbatim: nothing.

3. **Machine-independence fails independently.** `hashable`'s own docs: *"Hashable does not
   have a fixed standard. This allows it to improve over time"*; *"different computers or
   computers on different versions of the code will observe different hash values"*; and the
   library's self-ruling, which is effectively STD-006's conclusion stated by the hash
   library itself: *"Hashable is not recommended for use other than in-memory datastructures.
   Specifically, Hashable is not intended for network use or in applications which persist
   hashed values."* A HAMT's iteration order is a function of hash bits, so cross-version /
   cross-architecture hash instability is cross-version / cross-architecture *iteration-order*
   instability.

4. **It is not even key-set-deterministic within one build.** The `Eq` docs carry this
   caveat: *"in the presence of hash collisions, equal `HashMap`s may behave differently,
   i.e. extensionality may be violated"* — colliding keys land in a collision bucket whose
   internal order depends on insertion history, so two equal maps built differently can
   iterate differently even with identical hashes. This is the Haskell analog of the Rust
   "arbitrary even with a fixed hasher" argument (Rust note §2 point 2).

**Answer to the fixed-hash question:** even in the strongest hypothetical — a pinned
`hashable` version, one architecture, no random seed, no collisions — the declared type still
*guarantees* nothing (point 2), and the guarantee is what the discriminator reads. In the real
world points 3 and 4 make preservation not merely unguaranteed but wrong. **CANONICALISE**
(`set:`/`map:`), octet-sorting element/entry encodings, exactly as for Java and Rust.

**The inversion, and what it means.** In Java and Rust the *default* set/map is the hash one
(canonicalise) and the sorted one is the opt-in. In Haskell it is inverted: the de-facto
default (`containers`, shipped with GHC) is the **sorted** `Data.Map`/`Data.Set`, and the hash
containers are an *extra package*. Consequence: **idiomatic Haskell values land overwhelmingly
on the PRESERVE side of the rule**, and the canonicalise machinery is exercised only when a
codebase opted into `unordered-containers`. This does not change any classification — it
changes which *gap* a std-only binding has (§6.2, the mirror image of Rust's gap).

---

## 3. Priority queues — CANONICALISE_MULTISET, with a Haskell-specific near-miss

The Java (`PriorityQueue`) and Rust (`BinaryHeap`) rows were easy: their iterators yield raw
heap-array order, documented arbitrary. Haskell's pure priority queues are *almost*
deterministic, which makes the discriminator's verdict worth spelling out:

- **`pqueue` `MinQueue`:** `toList` *"Returns the elements of the priority queue in ascending
  order. Equivalent to `toAscList`"*; the folds come in `foldrAsc`/`foldlAsc` ("in ascending
  order") and `foldrU`/`foldlU` ("unordered") variants. So the primary iteration surface is
  **sorted** — far closer to deterministic than a heap-array walk. The gap is ties: the
  package states *"This implementation does not guarantee stable behavior"* — elements that
  compare `EQ` under `Ord` come out in unspecified relative order. Whenever `Ord` is coarser
  than structural equality (a queue ordered by a priority *field*, a `newtype` comparing on
  part of the value — entirely idiomatic), `EQ`-tied elements have **different encodings**, and
  their relative order is not a function of the value. The declared type therefore does *not*
  guarantee a deterministic machine-independent order → **CANONICALISE_MULTISET** (`bag:`),
  octet-sort, duplicates retained. The comparator is code, rebuilt at the receiver — the same
  ruling as Java's comparator (§3.8: natural priority order "is a function of elements plus
  comparator, rebuilt at the receiver").
- **`heaps` `Data.Heap`:** same discipline, plus a sharper lesson. `Heap a` **stores its
  ordering function inside the value**: `Heap {-# UNPACK #-} !Int (a -> a -> Bool) !(Tree a)`
  (verified source). Iteration (`Foldable`) is min-first by that stored `leq` (`foldMap`
  extracts the root and recurses via `deleteMin`), `toUnsortedList` is documented *"in some
  arbitrary, very likely unsorted, order"*, and ties are again unspecified. A function cannot
  be a wire value — STD-006 §3.8 "values and bounds, never behaviour" — so the transmissible
  value is exactly the element **multiset**, and the receiver reconstitutes a heap with its own
  comparator. `bag:`. That `heaps` reifies the comparator *into the runtime value* makes
  Haskell the language where the "comparator is code, not wire data" ruling is most literally
  visible in the type.

> **`==` wrinkle for §5:** `Data.Heap`'s `Eq` needs **no `Eq` on the elements at all** — it
> compares sizes and then walks both sorted sequences checking `leq x y && leq y x`
> (comparator-*equivalence*, verified source). So Heap `==` can call two structurally different
> values "equal" (whenever `leq` is coarse). Byte-equality of the canonicalised multiset is
> stricter than that `==` — a divergence of a *new* kind (comparator-coarseness, not insertion
> order). It is the same phenomenon as a Java `TreeSet` with a comparator inconsistent with
> `equals`, and like it, it is a property of a lawless/coarse comparator, not of the
> discipline; the multiset canonical form remains correct. `pqueue`'s `MinQueue` `Eq`
> (requires `Ord a`) is documented in-source as equivalent to comparing `toAscList` — the same
> coarseness appears exactly when `Ord` is coarser than structural `Eq`.

---

## 4. The §3a-tension hunt — the headline: it does NOT reproduce in Haskell

The Rust/Java finding (Rust note §4; STD-006 §3.8's `LinkedHashSet`/`LinkedHashMap`
paragraph): the insertion-ordered hash container is the one place iteration-determinism and
`==` diverge — `LinkedHashMap` and `indexmap::IndexMap` keep deterministic insertion order
(→ PRESERVE) yet define `==`/`equals` order-*insensitively*, so serialized byte-equality is
**stricter** than `==`.

Haskell has insertion-ordered containers, and they classify PRESERVE exactly as their
Java/Rust analogs. But their `==` is different — and it flips the result:

- **`ordered-containers` `OMap`:** `instance (Eq k, Eq v) => Eq (OMap k v)` is
  ``(==) = (==) `on` assocs`` where `assocs` lists pairs **in insertion order** (verified
  source, v0.2.4). Two `OMap`s with the same pairs inserted in different orders are **not**
  `==`. `Ord` likewise compares `on assocs`.
- **`ordered-containers` `OSet`:** ``(==) = (==) `on` toList``, and `Foldable` folds the
  tag-indexed (insertion-order) map (verified source). Order-sensitive.
- **`insert-ordered-containers` `InsOrdHashMap` (released 0.3.0):**
  `a == b = toList a == toList b`, where `toList` sorts by the stored insertion index
  (verified raw source of the 0.3.0 tarball). Order-sensitive.

So **every surveyed Haskell insertion-ordered container defines order-sensitive `==`** — the
design `ordermap` (the sibling crate of `indexmap`) chose, and `LinkedHashMap`/`indexmap`
did not. Consequences:

1. **The §3a tension does not exist in Haskell** (among the surveyed types): for the
   insertion-ordered types, PRESERVE keeps the insertion order in the bytes *and* `==` already
   distinguishes insertion orders, so byte-equality ⇔ `==` — no "stricter than" gap.
2. Combined with §5, **bytes ⇔ `==` holds exactly for every Haskell type in this note** —
   Haskell is the cleanest of the three languages surveyed. (The only strictly-coarser `==` is
   the comparator-equivalence wrinkle of §3, which is a lawless-comparator artefact, not an
   order artefact.)
3. **This is still confirmation of the rule, not a counterexample.** The discriminator never
   consults `==` — it consults iteration determinism, and by that test `OMap`/`OSet`/
   `InsOrdHashMap` PRESERVE exactly like `LinkedHashMap`/`IndexMap`. What Haskell shows is the
   *other* legitimate resolution of the same underlying divergence: where Java/Rust kept a
   HashMap-compatible `==` and accepted bytes-stricter-than-`==`, the Haskell authors
   strengthened `==` to match the order the type advertises. Both ecosystems, designing with
   no knowledge of STD-006, hit the same fork §3.8 documents — one kept the tension, one
   eliminated it in the type. A rule that is genuinely semantic reproduces its awkward case
   *as a fork* everywhere; which branch an ecosystem takes shows up only in `==`, never in
   the discipline.

> **Ambiguity flag for reviewers (the one genuinely unstable cell):** `InsOrdHashMap`'s `Eq`
> could not be pinned to a single answer across sources. The **released 0.3.0** raw source
> (fetched from the Hackage tarball, twice, verbatim) reads
> `a == b = toList a == toList b` — order-sensitive. A fetch of the **GitHub master branch**
> of `phadej/insert-ordered-containers` instead returned
> `InsOrdHashMap _ a == InsOrdHashMap _ b = a == b` — comparing the *underlying* `HashMap`s,
> whose per-entry `P` wrapper has an index-*blind* `Eq` (`P _ a == P _ b = a == b`, present in
> **both** sources), i.e. order-INSENSITIVE — which would be the `LinkedHashMap`/`indexmap`
> branch of the fork, §3a tension included. The 0.3.0 changelog mentions no `Eq` change, and
> we could not reconcile the two reads (mis-extraction of one fetch, or genuine branch drift).
> This note takes the released 0.3.0 source as authoritative. A reviewer whose conclusion
> depends on `InsOrdHashMap` `==` (it affects only the §5 bucket, never the PRESERVE
> discipline) must pin the package version and read that version's source. Note also the
> curiosity either way: the index-blind `P` `Eq` means the underlying-`HashMap` comparison is
> order-insensitive by *construction*, so both semantics sit one line apart in this type.

---

## 5. The cross-language validation: Haskell's `Eq` instances line up with the rule

STD-006's rule is engineered so DER byte-equality mirrors the type's `==` contract:
canonicalise the order-insensitive, preserve the order-sensitive (or element-derived). In Java
this is a designed correspondence; in Rust it held natively except for `indexmap`; in Haskell
it holds natively **without exception** among the surveyed types.

| Haskell type | `Eq` semantics (verified) | Discipline | Does the rule keep bytes ⇔ `==`? |
|---|---|---|---|
| `[a]`, `NonEmpty a`, `Seq a` | order-**SENSITIVE** (structural/positional; derived for list and `NonEmpty`) | PRESERVE (list) | **Yes** — `==` is order-sensitive, PRESERVE keeps order in the bytes; two `==` values are same-order by definition → identical DER. |
| `Map k v` / `Set a` / `IntMap a` / `IntSet` | content-equality (same key–value pairs / same elements); iteration is sorted, so equal ⇒ identical iteration | PRESERVE (sorted) | **Yes** — order is element-derived (`Ord` / fixed `Int` order): two `==` instances iterate identically → identical DER; no §3a tension, exactly like `TreeMap`/`BTreeMap`. |
| `HashMap k v` / `HashSet a` | order-**INSENSITIVE** content-equality (with a documented extensionality caveat under hash collisions) | CANONICALISE | **Yes** — `==` ignores order; octet-sort makes equal values → identical DER, so bytes ⇔ `==`. (The collision caveat concerns *behavioural* extensionality of folds, not `==` itself.) |
| `MinQueue a` (pqueue) | priority-order semantic equality — in-source noted equivalent to comparing `toAscList`; requires `Ord a` | CANONICALISE_MULTISET | **Yes, up to `Ord` lawfulness** — for `Ord` consistent with structural `Eq`, `==` is multiset equality and octet-sorting the multiset gives bytes ⇔ `==`. For a *coarse* `Ord`, `==` can be coarser than bytes (comparator wrinkle, §3). |
| `Heap a` (heaps) | comparator-**equivalence** (no `Eq a` constraint at all; pairwise `leq x y && leq y x` over sorted order) | CANONICALISE_MULTISET | **Bytes stricter where `leq` is coarse** — a lawless/coarse-comparator artefact, the moral analog of the Rust `BinaryHeap` "no `==` at all" row: the multiset canonical form is the natural value-equality a heap *should* have; this `==` is weaker than value equality by design. |
| `OMap k v` / `OSet a` (ordered-containers) | order-**SENSITIVE** (`on assocs` / `on toList`, insertion order) | PRESERVE (insertion) | **Yes** — the type where Java/Rust diverge, and Haskell does not: `==` distinguishes insertion orders, PRESERVE keeps them → bytes ⇔ `==` exactly. |
| `InsOrdHashMap k v` (0.3.0) | order-**SENSITIVE** (`toList` on insertion index) — **see §4 version flag** | PRESERVE (insertion) | **Yes** (per 0.3.0). If a future/other version reverts to underlying-`HashMap` `==`, this row becomes the Rust `IndexMap` row: bytes stricter than `==`, §3a tension restored. |

**Statement of the invariant.** *Canonicalise the `==`-order-insensitive types and preserve
the order-sensitive ones, and equal Haskell values encode to identical DER just as equal Java
and Rust values do.* The three-pillar structure of the Rust note carries over — positional
types (order-sensitive `==` → PRESERVE, agree), hash types (order-insensitive `==` +
non-deterministic iteration → CANONICALISE, restored agreement), sorted types
(order-insensitive `==` + element-derived iteration → PRESERVE with no tension) — and Haskell
adds a fourth pillar Rust std lacked: insertion-ordered types whose `==` was *strengthened* to
order-sensitivity, closing the one gap the other two languages exhibit. The residual
divergences are all comparator-coarseness artefacts (lawless `Ord`, `Heap`'s stored `leq`),
which exist identically in Java (comparator inconsistent with `equals`) and are excluded there
by the same lawfulness assumptions (§6.6).

---

## 6. Gaps — what a base+containers-only Haskell binding can and cannot express

### 6.1 What the std tier (base + containers) CAN express

- **PRESERVE (list):** `[a]`, `NonEmpty`, `Seq` → `list:`. ✔
- **PRESERVE (sorted set/map):** `Set`, `Map`, `IntSet`, `IntMap` → `orderedset:`/
  `orderedmap:` (the decode target for Java `TreeSet`/`TreeMap`/`ConcurrentSkipList*` and Rust
  `BTreeSet`/`BTreeMap`). ✔
- That is **all**. The std tier is entirely on the PRESERVE side — see the inversion, §2.

### 6.2 What the std tier CANNOT express (the mirror image of Rust's gap)

- **CANONICALISE (`set:`/`map:`) has no base+containers producer.** Rust's std gap was the
  *insertion-ordered* hash container (no `LinkedHashMap` analog); Haskell's std gap is the
  *plain hash* container itself — `base`+`containers` contain no unordered set or map at all.
  Consequences are asymmetric in the same way as Rust's, but on the other side: a std-only
  Haskell **decoder** handles a received `set:`/`map:` field perfectly well — it validates the
  §11.6 octet order and duplicate-freedom, then inserts into `Data.Set`/`Data.Map`, whose own
  element-derived order supersedes wire order harmlessly (the receiver "imposes" its
  discipline, as §3.8 intends). But a std-only **encoder** never *originates* a `set:`/`map:`
  value: any set/map it can natively hold is a sorted one, which the discriminator sends as
  `orderedset:`/`orderedmap:`. To be a byte-faithful peer for a schema that *declares*
  `set:`/`map:` (because the Java side declared `HashSet`/`HashMap`), the Haskell encoder
  needs `unordered-containers` — or must canonicalise a `Data.Set`'s elements by octet order
  (re-sorting from `Ord` order to encoding order) into the declared `SET OF`, which is legal
  and cheap: the discipline is expressible, the *idiomatic producer type* is not std.
- **CANONICALISE_MULTISET (`bag:`) has no base+containers producer.** `containers` ships no
  priority queue (`Data.Sequence` is a deque — PRESERVE). `bag:` needs `pqueue`/`heaps`, or
  degrades to encoding a `[a]`/`Seq` — which would be `list:` (order-preserving), a *different
  declared discipline*, not a substitute.
- **Insertion-ordered set/map (`orderedset:`/`orderedmap:` over insertion history):** needs
  `ordered-containers` (or `insert-ordered-containers`) — precisely Rust's `indexmap`
  situation, crate-not-std, with the §4 difference that the Haskell packages' `==` matches
  the preserved bytes. A std-only decoder honours the wire order only in a positional type
  (`[(k,v)]`/`Seq`), as in Rust.

### 6.3 Java disciplines with no idiomatic Haskell type (any tier)

- **`EnumSet`/`EnumMap` (PRESERVE, ordinal order).** Haskell's analog is `Data.Set MyEnum` /
  `Data.Map MyEnum v` (or `IntSet`/`IntMap` via `fromEnum`) with `deriving (Eq, Ord, Enum,
  Bounded)`: the Haskell Report specifies that derived `Ord` follows **constructor declaration
  order**, the natural analog of Java's `ordinal()` — so these land in PRESERVE (sorted),
  byte-compatible with `EnumSet`/`EnumMap` **iff** constructor declaration order matches the
  Java enum's constant order (an encoder obligation, exactly as stated for Rust
  `derive(Ord)`). There is no runtime `ordinal()` reflection; the ordering is carried by the
  derived instance.
- **The concurrent collections (both disciplines' concurrent variants).** Haskell's std
  concurrency story is `MVar`/`STM` around immutable structures — `MVar (Map k v)` is just a
  `Map` at snapshot time, and takes the *underlying* type's discipline (a snapshot taken
  atomically has no concurrency non-determinism of its own). Dedicated concurrent containers
  (`stm-containers` `Map` — hash-based, unspecified order) are packages and canonicalise,
  mirroring the `ConcurrentHashMap`/`DashMap` ruling. There is no concurrent *sorted* map in
  common use; the discipline (sorted-preserve) is expressible with `Data.Map` under a lock,
  as in Rust.

### 6.4 Haskell types/properties with no clean Java analog

- **Persistence is universal.** *Every* Haskell collection here is persistent/immutable —
  what `im`/`rpds` are to Rust is simply the default. As the Rust note concluded for `im`:
  persistence is an implementation property invisible to the wire; it classifies by the same
  discriminator. One genuine benefit: a Haskell "snapshot" is free and race-free by
  construction — the encoder can never observe a torn collection, which disposes of the
  concurrent-iteration hazards §3.8 worries about for Java's live views.
- **`Maybe a` as the null analog.** Identical to Rust's `Option<T>` row: `Nothing` ↔ the
  `absent`/NULL `CHOICE` arm (`05 00`, sorting first), `Just x` ↔ `present x`. `Map k
  (Maybe v)` / `Set (Maybe a)` map cleanly onto Java's null-in-collection cases; a plain
  `Map k v` cannot hold a null key/value at all — the same narrowing-not-conflict as Rust.

### 6.5 Laziness — the genuinely Haskell-specific wrinkle

Encoding is **forcing**. A DER encoder is a fold to normal form over the whole collection, so:

- **Infinite and partial values fail at encode time, not before.** `[a]` may be infinite
  (`Seq` may not — "sequences are always finite"); a lazy value inside `Data.Map.Lazy` ("`Map`
  is strict in its keys but lazy in its values") may be an unevaluated thunk that diverges or
  throws when forced. The encoder is the point where these surface. The schema `SIZE` bound
  (§3.8/§7.6) bounds the spine walk, so an over-long or infinite list fails the bound rather
  than hanging — but a single bottom *element* is only discovered by forcing it. An encoder
  SHOULD therefore force the collection (spine and elements) to normal form (`NFData`) *before*
  emitting any octets, so a bottom cannot truncate a partially-written encoding — the
  snapshot-then-encode shape, which immutability otherwise gives Haskell for free.
- **Strict vs Lazy module choice is wire-invisible.** `Data.Map.Strict` and `Data.Map.Lazy`
  share one `Map` type; `HashMap.Strict`/`.Lazy` likewise. The discipline column never
  depends on it; only the *when* of the forcing does.

### 6.6 Lawful `Ord` — the analog of Rust's `T: Ord` logic-error contract

Every PRESERVE-sorted row and both priority queues delegate their determinism to an `Ord`
instance, which in Haskell is programmer-written code with unenforced laws — precisely Java's
`Comparator` and Rust's `Ord` situation. Two peers agree on bytes for a `Data.Set MyType`
field only if they agree on `MyType`'s ordering *as code*; an unlawful `Ord` (non-total,
inconsistent with `Eq`) silently corrupts `containers` invariants and with them the "equal
values iterate identically" argument of §5. A **newtype-wrapped ordering** (`Down a`, or a
domain newtype with a custom `Ord`) is the idiomatic comparator: it changes the declared
element type, so the discriminator naturally reads the *newtype's* order — deterministic,
still PRESERVE — and the schema must treat the newtype as its own element wire-type so both
peers apply the same ordering. (Flag: the fetched `containers` pages do not print a verbatim
"`Ord` must be a lawful total order" warning — see §7. The requirement is implied by the
tree invariants, not stated.)

**Bottom line for a binding author.** Every STD-006 *discipline* is expressible in Haskell,
but — dual to Rust — the std tier covers **only the PRESERVE side**: PRESERVE-list
(`[a]`/`NonEmpty`/`Seq`), PRESERVE-sorted (`Map`/`Set`/`IntMap`/`IntSet`). The two
CANONICALISE disciplines have **no base+containers producer type**: `set:`/`map:` needs
`unordered-containers` (or octet-re-sorting a sorted container into the declared `SET OF`),
and `bag:` needs `pqueue`/`heaps`. The insertion-ordered PRESERVE forms need
`ordered-containers`, as they needed `indexmap` in Rust — but arrive with order-sensitive
`==`, so Haskell alone has no type whose serialized byte-equality is stricter than its `==`.

---

## 7. Verification notes (sources)

All Haskell semantics were verified against the cited docs/source on 2026-07-21, not assumed.
Package versions are the ones Hackage resolved as latest on that date.

- **`Data.HashMap.Strict` / `Data.HashSet`** (unordered-containers-0.2.21) — "The order of its
  elements is unspecified, and it may change from version to version of either this package or
  of `hashable`"; "makes no guarantees as to the order of its elements"; `Eq` extensionality
  caveat "in the presence of hash collisions, equal `HashMap`s may behave differently"; HAMT.
  (`hackage.haskell.org/package/unordered-containers/docs/Data-HashMap-Strict.html`,
  `.../Data-HashSet.html`.)
- **`Data.Hashable`** (hashable-1.5.1.0) — "Hashable does not have a fixed standard";
  "different computers or computers on different versions of the code will observe different
  hash values"; "not intended for network use or in applications which persist hashed values";
  cabal flag "Randomly initialize the initial seed on each final executable invocation …
  (non-existent) stability". (`hackage.haskell.org/package/hashable/docs/Data-Hashable.html`
  and the package page.)
- **`Data.Map.Strict` / `Data.Map.Lazy` / `Data.Set`** (containers-0.8) — "Folds in order of
  increasing key"; "Traverses in order of increasing key"; `foldr f z == foldr f z .
  toAscList`; `elems` "an alias of `toAscList`"; "strict in its keys but lazy in its values"
  (Lazy); size-balanced binary trees; maxBound size warning.
  (`hackage.haskell.org/package/containers/docs/Data-Map-Strict.html`, `.../Data-Map-Lazy.html`,
  `.../Data-Set.html`.)
- **`Data.IntMap`** (containers-0.8) — "Folds in order of increasing key"; big-endian Patricia
  trees; `Key = Int`, no `Ord` instance consulted.
  (`hackage.haskell.org/package/containers/docs/Data-IntMap-Strict.html`.)
- **`Data.Sequence`** (containers-0.8) — "finite sequence of values"; "Whereas lists can be
  either finite or infinite, sequences are always finite"; 2-3 finger trees.
  (`hackage.haskell.org/package/containers/docs/Data-Sequence.html`.)
- **`Data.List.NonEmpty`** (base-4.22.0.0) — `data NonEmpty a = a :| [a]`; `Eq a => Eq
  (NonEmpty a)` present (derived). (`hackage.haskell.org/package/base/docs/Data-List-NonEmpty.html`.)
- **`pqueue` `Data.PQueue.Min`** (pqueue-1.7.0.0) — `toList` "Returns the elements of the
  priority queue in ascending order. Equivalent to `toAscList`"; `toListU` "in no particular
  order"; `foldrAsc`/`foldlAsc`/`foldrU`/`foldlU`; "does not guarantee stable behavior";
  source `Eq`/`Ord` for `MinQueue` (structural on size/min/rest; `Ord` commented "equivalent
  to `comparing toAscList`"). (`hackage.haskell.org/package/pqueue/docs/Data-PQueue-Min.html`
  and the `Data.PQueue.Internals` source.)
- **`heaps` `Data.Heap`** (heaps-0.4.1) — data decl `Heap !Int (a -> a -> Bool) !(Tree a)`
  (stored `leq`); `Eq` with no element-`Eq` constraint, pairwise `f x y && f y x` over
  `F.toList`; `Foldable` `foldMap` via root + `deleteMin` (min-first); `toUnsortedList` "in
  some arbitrary, very likely unsorted, order"; `nub` exists (duplicates legitimate).
  (`hackage.haskell.org/package/heaps/docs/Data-Heap.html` and its source.)
- **`ordered-containers`** (0.2.4) — `OMap` decl `OMap !(Map k (Tag, v)) !(Map Tag (k, v))`;
  ``instance (Eq k, Eq v) => Eq (OMap k v)`` = ``(==) `on` assocs``; `assocs` via `toAscList`
  of the Tag-indexed map (insertion order); `OSet` `(==) = (==) `on` toList`, `Foldable` over
  the Tag-indexed map. (`hackage.haskell.org/package/ordered-containers` — `Data.Map.Ordered`
  docs and `Data.Map.Ordered.Internal` / `Data.Set.Ordered` sources.)
- **`insert-ordered-containers`** (0.3.0) — module header "InsOrdHashMap is like HashMap, but
  it folds and traverses in insertion order"; raw tarball source: `Eq` = `toList a == toList
  b`; `toList` = `sortBy (comparing (getPK . snd))` over the underlying `HashMap` (insertion
  index); `P` decl and index-blind `P` `Eq`; changelog (no `Eq` entry). GitHub master fetch
  disagreed on `Eq` — see §4 flag. (`hackage.haskell.org/package/insert-ordered-containers`
  docs, tarball source at `/src/src/Data/HashMap/Strict/InsOrd.hs`, changelog, and
  `raw.githubusercontent.com/phadej/insert-ordered-containers/master/...`.)

### Cells a reviewer should double-check (genuine ambiguity)

1. **`InsOrdHashMap` `Eq` version drift** (§4 flag) — the released 0.3.0 tarball source is
   order-sensitive (`toList`-based); a GitHub-master fetch returned the order-insensitive
   underlying-`HashMap` form. The headline "§3a tension does not reproduce in Haskell" rests
   on the released sources of all three insertion-ordered types; if any version of
   `InsOrdHashMap` ships the order-insensitive `Eq`, that version restores the tension in
   `indexmap` form. Discipline (PRESERVE) unaffected either way.
2. **`containers` lawful-`Ord` requirement** (§6.6) — the fetched `Data.Map`/`Data.Set` pages
   print no verbatim "`Ord` must be a lawful total order" sentence (unlike Rust's documented
   logic-error framing). The requirement is real but implied by the tree invariants; a reviewer
   wanting a normative citation should check the current `containers` haddocks/README.
3. **`Map`/`Set` `Eq` exact implementation** (§5 row 2) — semantics (content equality; equal ⇒
   identical sorted iteration) are solid, but the exact instance body (historically
   `size`-check + `toAscList` comparison; newer containers use `liftEq` helpers) was not
   verbatim-fetched — the `Data.Map.Internal` source exceeded the fetch window.
4. **`pqueue` `MinQueue` `Eq`** (§3/§5) — the fetched source shows a structural comparison of
   size/min/rest whose semantic equivalence to `comparing toAscList` is asserted by an
   in-source comment on the **`Ord`** instance; the `Eq` instance carries no such comment.
   The "equality = multiset equality up to `Ord` lawfulness" characterisation follows from the
   structure but is not a verbatim doc statement. Discipline (CANONICALISE_MULTISET)
   unaffected.
5. **`pqueue` "does not guarantee stable behavior"** — quoted from the fetched module/package
   docs; a reviewer should confirm current phrasing and that it refers to tie order (it is the
   standard stability caveat). The tie-order argument for canonicalising also stands
   independently on "the docs nowhere *guarantee* tie order".
6. **`heaps` `Foldable` min-first order** (§3) — read from the instance *source* (`foldMap`
   via root + `deleteMin`), not from a doc sentence; the haddocks do not state the fold order.
7. **`hashable` random-init-seed default** (§2) — the flag's description was quoted verbatim
   from the package page; that its default is *off* is implied by its "catching cases" purpose
   but was not verbatim-verified from the `.cabal` stanza.
8. **`[a]`/`NonEmpty` `Eq` order-sensitivity** — asserted from Haskell Report derived-instance
   semantics (structural, positional) rather than a fetched sentence; `NonEmpty`'s instance
   list was verified present in base-4.22.0.0.
9. **`Data.IntSet`** — mapped by family with `Data.IntMap` (same Patricia-trie module family,
   "increasing key" folds verified on the IntMap page); the IntSet page itself was not
   separately fetched.
