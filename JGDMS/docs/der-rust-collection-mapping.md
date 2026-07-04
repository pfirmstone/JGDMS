# Mapping STD-006 §3.8 collection-ordering onto Rust collection types

**Status:** research / cross-language design note. Companion to
`docs/JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md` §3.8 ("Encounter order and serialized
equality") and `docs/der-collection-ordering-research.md`.
**Author:** research pass, 2026-07-05.
**Scope:** how a *future, language-neutral* Rust codec of the STD-006 DER wire format would
classify Rust collections into the **same** determinism disciplines the Java reference codec
uses — so that equal values encode to identical DER across a Rust peer and a Java peer.

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

The just-built `jgdms-der` codec is intended to carry the discipline in a schema wire-type
**token**:

| token | discipline | meaning |
|---|---|---|
| `orderedset:<elemWT>` | PRESERVE (set) | deterministic-order set; iteration order preserved |
| `list:<elemWT>` | PRESERVE (list/deque) | positional / order-is-the-value; iteration order preserved |
| `orderedmap:{keyWT}{valWT}` | PRESERVE (map) | deterministic-order map; entry order preserved |
| `set:<elemWT>` | CANONICALISE (set) | non-deterministic set; elements octet-sorted, dups illegal |
| `bag:<elemWT>` | CANONICALISE_MULTISET | non-deterministic multiset; elements octet-sorted, dups kept |
| `map:{keyWT}{valWT}` | CANONICALISE (map) | non-deterministic map; entries octet-sorted by encoded key |

The mapping below says which token each Rust type maps to, **decided by the same
discriminator** (deterministic iteration order?), plus each type's closest Java analog.

---

## 1. The mapping table

> Column meanings: **Java type** — the reference-side type the Rust type is analogous to.
> **Discipline / wire token** — the STD-006 discipline and the `jgdms-der` schema token the
> Rust type would map to. **Rust equivalent** — the Rust type. **Deterministic order?** — does
> the Rust type *guarantee* a deterministic, machine-independent iteration order (the sole
> discriminator). **Notes** — why, and any subtlety verified against the docs.

| Rust type | Deterministic order? | STD-006 discipline | Wire token | Closest Java analog | Notes |
|---|---|---|---|---|---|
| `Vec<T>` | **Yes** — positional index order **is** the value | PRESERVE (list) | `list:<elemWT>` | `ArrayList` / `Vec`-like `List` | `PartialEq` is order-**sensitive** (`vec![a,b] != vec![b,a]`). Order is the value; never reorder. |
| `[T; N]` (fixed array) | **Yes** — positional | PRESERVE (list) | Java array `T[]` | Array carve-out (§3.8): order **is** the value, always preserved. Length is compile-time; still a positional sequence on the wire. |
| `&[T]` / `[T]` (slice) | **Yes** — positional view | PRESERVE (list) | Java array / `List` view | A borrowed positional sequence; `PartialEq` order-sensitive. Encodes exactly like `Vec<T>` (`Vec`/array/slice share slice `PartialEq`). |
| `VecDeque<T>` | **Yes** — front-to-back order, deterministic | PRESERVE (list) | `ArrayDeque` (as `Deque`) | `iter()` is documented "front-to-back"; `PartialEq` order-sensitive. A double-ended queue whose head→tail order is the value. Non-concurrent → deterministic. |
| `LinkedList<T>` | **Yes** — front-to-back order | PRESERVE (list) | `LinkedList` (as `List`/`Deque`) | Doubly-linked; `iter()` is a "forward iterator", `PartialEq` order-sensitive. Positional sequence. |
| `BTreeSet<T>` | **Yes** — sorted by `Ord` (element-derived, deterministic) | PRESERVE (set) | `TreeSet` / `SortedSet` | Requires `T: Ord` — the analog of Java needing `Comparable`/`Comparator`. Iterates in ascending sorted order; two equal sets iterate identically → **no §3a tension**. Could equally be octet-canonicalised without changing bytes-vs-value, but PRESERVE is correct and cheaper (already sorted). |
| `BTreeMap<K, V>` | **Yes** — sorted by key `Ord` | PRESERVE (map) | `TreeMap` / `SortedMap` | Requires `K: Ord`. Docs: iterators "produce their items in key order". Deterministic; entry order is a function of the keys → no tension. |
| `HashSet<T>` | **No** — `iter()` "arbitrary order"; `RandomState`/SipHash 1-3, per-instance random seed | CANONICALISE (set) | `HashSet` | See §2 — **stronger case than Java's**. `PartialEq` is true set-equality (order-independent). Octet-sort; dups illegal by construction. |
| `HashMap<K, V>` | **No** — `iter()` "arbitrary order"; random per-instance seed | CANONICALISE (map) | `HashMap` | Octet-sort entries by encoded key. `PartialEq` is content-equality, order-independent. See §2. |
| `BinaryHeap<T>` | **No** — `iter()` "arbitrary order" / "unsorted (and unspecified)"; max-heap array layout, **not** sorted | CANONICALISE_MULTISET | `PriorityQueue` / `PriorityBlockingQueue` | Requires `T: Ord`. Duplicates allowed and kept (verified: `BinaryHeap::from([-10,1,2,3,3])`). Heap-array iteration is a sift artefact, not priority order → octet-sort as a **bag** (dups retained). See §3 for the `PartialEq` wrinkle. |

### Notable non-std (standard-in-practice) crates

| Rust type | Deterministic order? | STD-006 discipline | Wire token | Closest Java analog | Notes |
|---|---|---|---|---|---|
| `indexmap::IndexSet<T>` | **Yes** — insertion order, "does not depend on the values or the hash function at all" | PRESERVE (set) | `LinkedHashSet` | Crate, **not std**. Insertion-order-preserving hash set. See §4: its `==` is order-**independent** (HashMap-compatible), so it is the one preserve type where iteration determinism and `==` **diverge** — the direct §3a-tension analog. |
| `indexmap::IndexMap<K, V>` | **Yes** — insertion order | PRESERVE (map) | `LinkedHashMap` | Crate, not std. Insertion-order-preserving hash map. `==` order-independent (drop-in HashMap compat); iteration deterministic. §3a tension analog — see §4. |
| `dashmap::DashMap<K, V>` | **No** — sharded concurrent hash map, no ordering guarantee | CANONICALISE (map) | `ConcurrentHashMap` | Crate, not std. Concurrency ⇒ non-determinism (mirrors the Java concurrent ruling). Octet-sort. |
| `im` / `rpds` persistent maps/sets (`HashMap`/`HashSet` variants) | **No** — hash-trie (HAMT) order, hash-derived | CANONICALISE (set/map) | persistent/immutable hash `Set`/`Map` | Persistent (structural-sharing) hash collections. Order is hash-derived → canonicalise. |
| `im` / `rpds` persistent ordered maps/sets (`OrdMap`/`OrdSet`) | **Yes** — sorted by `Ord` | PRESERVE (set/map) | `TreeMap`/`TreeSet` (immutable) | B-tree/RB-tree persistent variants iterate in sorted order → preserve, like `BTreeMap`/`BTreeSet`. |
| `im::Vector<T>` (RRB-tree vector) | **Yes** — positional | PRESERVE (list) | `List` / `Vec`-like | Persistent vector; index order is the value → `list:`. |

**Key alignment observation.** In every row, the Rust discriminator produces the **same
discipline** the Java reference assigns to the analog — the rule transfers without a single
reclassification. `Vec`/`VecDeque`/`LinkedList`/array/slice → PRESERVE list, exactly as
Java's `List`/`Deque`/array. `BTreeSet`/`BTreeMap` → PRESERVE (sorted), exactly as
`TreeSet`/`TreeMap`. `HashSet`/`HashMap`/`DashMap` → CANONICALISE, exactly as
`HashSet`/`HashMap`/`ConcurrentHashMap`. `BinaryHeap` → CANONICALISE_MULTISET, exactly as
`PriorityQueue`. This is the language-neutrality claim made concrete (§5).

---

## 2. `HashSet`/`HashMap` — a STRONGER case for canonicalising than Java's

Both languages canonicalise their hash set/map, but Rust's case is **stronger** on two counts,
both documented, not incidental:

1. **Per-instance random seed by default.** `HashMap`/`HashSet` default to `RandomState`,
   which seeds SipHash 1-3 from a high-quality random source, and **"each `HashMap` instance
   uses a different seed"** (likewise `HashSet`: *"each `HashSet` instance uses a different
   seed, which means that `HashSet::new` cannot be used in const context"*). So two hash sets
   holding the same elements iterate differently **within a single run**, not merely across
   runs/machines. Java's identity-`hashCode`-derived bucket order is per-run unstable too
   (memo §3.0), but Rust makes the per-*instance* randomisation an explicit, documented
   default (HashDoS resistance), so preserving iteration order is *guaranteed* meaningless for
   byte-determinism.

2. **Order is unspecified even with a fixed hasher.** Even if you swap `RandomState` for a
   fixed/deterministic `BuildHasher`, the *iteration* order is documented as **"arbitrary"**
   and must not be relied on: `iter()`/`keys()`/`values()` are each *"an iterator visiting all
   … in arbitrary order"*, and the implementation *"internally visits empty buckets too"* — so
   order is a function of capacity/insertion/resize history, not of the values. **You should
   never write code that assumes a specific iteration order for `HashMap`, even if the hasher
   is fixed.** This exactly parallels the Java "value-`hashCode` still gives bucket order, not
   a canonical order" argument (memo §3.0 reason 2): a fixed hasher removes the randomness but
   **not** the unspecified-ness.

Conclusion: **CANONICALISE** (`set:` / `map:`), octet-sorting element/entry encodings, is the
only way a Rust peer and a Java peer produce identical DER from the same membership. A
`HashMap` is octet-sorted by encoded key; keys are distinct values, so encodings are distinct
and the sort is total (memo §2).

> **Ambiguity flag for reviewers:** the "arbitrary order even with a fixed hasher" claim is
> the load-bearing one. It is documented on the std `HashMap`/`HashSet` pages ("arbitrary
> order", "internally visits empty buckets too", HashDoS/random-seed notes), but the std docs
> do **not** print a single sentence of the exact form "iteration order is unspecified even
> with a deterministic hasher." The conclusion is sound (it follows from "arbitrary order" +
> bucket-walk-with-empty-buckets + capacity-dependence), but a reviewer wanting a verbatim
> normative citation should note the docs express it as "arbitrary", not as an explicit
> fixed-hasher carve-out. Either way the codec **must** canonicalise; nothing hangs on the
> distinction for the mapping.

---

## 3. `BinaryHeap` — CANONICALISE_MULTISET (the `PriorityQueue` analog)

`BinaryHeap<T>` (requires `T: Ord`) is a **max-heap**. The subtle, verified points:

- **`iter()` yields arbitrary heap-array order, NOT sorted order.** The docs: *"Returns an
  iterator visiting all values in the underlying vector, in arbitrary order"*, and elsewhere
  *"the elements are visited in unsorted (and unspecified) order"*. This is the heap's backing
  `Vec` layout — an insertion/sift artefact — exactly like Java `PriorityQueue.iterator()`
  returning heap-array order rather than priority order (memo §1b). To get sorted order you
  must drain (`into_sorted_vec()` / repeated `pop()`), which is not what `iter()` does.
- **Duplicates are legitimate and retained.** Verified: `BinaryHeap::from([-10, 1, 2, 3, 3])`
  holds the duplicate `3`. A priority queue may hold equal-priority duplicates; they carry
  value and must **not** be deduped.
- Therefore the transmissible *value* is a **multiset** of elements (the comparator/`Ord` is
  code, rebuilt at the receiver, not wire data) whose iteration order is non-deterministic and
  meaningless. → **CANONICALISE_MULTISET** (`bag:<elemWT>`): octet-sort the element encodings,
  keep duplicates. The receiver rebuilds a heap with its own `Ord`.

> **Verified wrinkle worth flagging:** `BinaryHeap` **does not implement `PartialEq`/`Eq`** at
> all (its trait list is `Clone, Debug, Default, Extend, From, FromIterator, IntoIterator` —
> no `PartialEq`). So unlike Java's `PriorityQueue` (which inherits `AbstractCollection`, whose
> `equals` is `Object` identity — also *not* a value-equality), Rust `BinaryHeap` gives you no
> `==` at all. This does **not** change the discipline (a heap's *value* is still the multiset
> of its elements, and octet-sorting that multiset is the canonical form), but it means the §5
> "`==` mirrors the discipline" parallel is **vacuously** satisfied for `BinaryHeap`, not
> positively — there is no `==` to mirror. Reviewers verifying §5 should treat `BinaryHeap` as
> "no `==` defined" rather than "order-insensitive `==`". The multiset canonicalisation is
> justified by the heap's *semantics* (a priority multiset), not by an `==` contract.

---

## 4. `indexmap` — the insertion-ordered PRESERVE types (and the §3a divergence)

`IndexSet`/`IndexMap` are hash containers that **preserve insertion order** for iteration:
*"The values have a consistent order that is determined by the sequence of insertion and
removal calls … The order does not depend on the values or the hash function at all"* and
*"All iterators traverse the set in order"* (with the caveat that `swap_remove`/`remove` can
reorder). Iteration is **deterministic and app-controlled** → **PRESERVE**
(`orderedset:` / `orderedmap:`). They are the direct analog of Java `LinkedHashSet` /
`LinkedHashMap`.

**Two things to flag:**

1. **It is a crate, not std.** A **std-only** Rust binding has **no insertion-ordered set or
   map** — std offers only `HashSet`/`HashMap` (canonicalise) and `BTreeSet`/`BTreeMap`
   (sorted-preserve). So the `orderedset:`/`orderedmap:` *preserve-hash* discipline is
   **inexpressible in std-only Rust** (see §6). A binding that must interop with Java
   `LinkedHashSet`/`LinkedHashMap` fields and preserve their insertion order needs `indexmap`
   (or `ordermap`, below).

2. **`IndexMap`/`IndexSet` `==` is order-INDEPENDENT — the §3a tension, natively.** This is the
   subtle one. `IndexMap` deliberately makes `PartialEq` **order-independent for drop-in
   `HashMap` compatibility**: two `IndexMap`s with the same entries in different insertion
   orders compare **equal** (`==`), even though they *iterate* differently. (`indexmap` also
   offers a parallel content-equality helper; the sibling crate `ordermap` is the variant whose
   `==` **is** insertion-order-sensitive.) So for `IndexMap`/`IndexSet`, exactly as for Java
   `LinkedHashSet`/`LinkedHashMap` (memo §3a):

   > two instances that are `==` but were built in different insertion orders **serialise to
   > different DER** (because PRESERVE keeps their differing insertion order), so serialized
   > byte-equality is **stricter** than `==`.

   This is the one preserve type where iteration-determinism and `==` **diverge** — and it
   diverges the *same way, for the same reason*, in both languages. It is independent
   cross-language confirmation that §3a is a real, type-driven property, not a Java quirk.

> **Ambiguity flag for reviewers:** the `IndexMap`/`IndexSet` `==` semantics were the trickiest
> to pin. The `docs.rs` struct pages show a `PartialEq` impl and a parallel `par_eq`
> ("regardless of each map's indexed order") but do **not**, in the fetched excerpt, print a
> one-liner stating standard `==` is order-independent. The order-independence (and the
> `ordermap` contrast) is documented in the crate's own comparison notes and is *why* `ordermap`
> exists as a separate crate. If a reviewer needs a verbatim citation, confirm against the
> current `indexmap` `Eq`/`PartialEq` docs and the `ordermap` README; the mapping's discipline
> (**PRESERVE** — iteration is deterministic insertion order) is unaffected either way, because
> PRESERVE is decided by iteration determinism, not by the `==` contract. What the `==`
> nuance affects is *only* which §5 bucket it lands in (it is a "preserve type carrying the §3a
> tension", like `LinkedHashSet`, **not** like `BTreeSet`).

---

## 5. The cross-language validation: Rust's `PartialEq`/`Eq` contracts line up with the rule

This is the headline result. STD-006's rule is engineered so that **DER byte-equality mirrors
the type's `==`/`.equals` contract** — canonicalise the types whose equality is
order-insensitive so equal values get equal bytes; preserve the types whose equality is
order-sensitive (or whose order is element-derived) so bytes track the value. In Java this is a
*designed* correspondence the codec must uphold. In **Rust it holds natively in the standard
library's own `PartialEq`/`Eq` implementations** — which is independent evidence the rule is
language-neutral, not a Java artefact.

Verified Rust `==` semantics, matched to the discipline the rule assigns:

| Rust type | Rust `==` (verified) | Discipline | Does the rule keep bytes ⇔ `==`? |
|---|---|---|---|
| `Vec<T>`, `[T;N]`, `&[T]` | order-**SENSITIVE** (`vec![a,b] != vec![b,a]`; slice `PartialEq` is positional) | PRESERVE (list) | **Yes** — `==` is order-sensitive, PRESERVE keeps order in the bytes; two `==` vecs are same-order by definition → identical DER. |
| `VecDeque<T>`, `LinkedList<T>` | order-**SENSITIVE** (front-to-back) | PRESERVE (list) | **Yes** — same as `Vec`: order-sensitive `==`, order-sensitive bytes, they agree. |
| `HashSet<T>` | order-**INSENSITIVE** — true set-equality: equal iff same elements, regardless of order/insertion/seed | CANONICALISE (set) | **Yes** — `==` ignores order, canonicalisation (octet-sort) makes equal sets → identical DER, so bytes ⇔ `==`. |
| `HashMap<K,V>` | order-**INSENSITIVE** — content-equality, order irrelevant | CANONICALISE (map) | **Yes** — canonicalise by encoded key; two `==` maps → identical DER. |
| `BTreeSet<T>`, `BTreeMap<K,V>` | content-equality; iteration is sorted, so equal ⇒ same iteration order | PRESERVE (sorted) | **Yes** — order is element-derived (`Ord`), so two `==` instances iterate identically → identical DER (no §3a tension), *exactly like the canonicalise types*. |
| `BinaryHeap<T>` | **no `==` at all** (does not impl `PartialEq`) | CANONICALISE_MULTISET | **Vacuously** — no `==` to contradict; the multiset canonical form is the natural value-equality a heap *would* have. |
| `indexmap::IndexMap`/`IndexSet` | order-**INSENSITIVE** (`HashMap`-compat) — but iteration order-**preserving** | PRESERVE (ordered) | **Stricter** — bytes are order-sensitive while `==` is not, so byte-equal ⟹ `==` but not conversely. This **is** the §3a tension, and it is the `LinkedHashSet`/`LinkedHashMap` analog, present natively in Rust. |

**Statement of the invariant, holding in both languages.** *Canonicalise the
`==`-order-insensitive types and preserve the order-sensitive ones, and equal Rust values
encode to identical DER just as equal Java values do.* The three std pillars line up cleanly:

- **Order-sensitive `==` (`Vec`, `VecDeque`, `LinkedList`, arrays, slices) → PRESERVE.** The
  bytes are order-sensitive and so is `==`; they agree exactly. (Java `List` — same.)
- **Order-insensitive `==` with non-deterministic iteration (`HashSet`, `HashMap`, `DashMap`)
  → CANONICALISE.** Octet-sort restores bytes ⇔ `==`. (Java `HashSet`/`HashMap`/
  `ConcurrentHashMap` — same.)
- **Order-insensitive `==` (`BTreeSet`/`BTreeMap`) but element-derived deterministic iteration
  → PRESERVE (sorted), with no tension.** Sorted iteration means equal instances iterate
  identically, so preserving is as byte-canonical as sorting would be. (Java `TreeSet`/
  `TreeMap` — same.)

The **only** divergence, in both languages, is the insertion-ordered hash container
(`IndexMap`/`IndexSet` ≈ `LinkedHashSet`/`LinkedHashMap`): iteration is deterministic
(→ PRESERVE) yet `==` is order-insensitive, so byte-equality is **stricter** than `==`. That
this identical corner case appears, for the identical reason, in a language whose collections
were designed with no knowledge of STD-006 is the strongest evidence the discriminator is a
property of *collection semantics*, not of Java.

**One genuine cross-language contrast on `==` (not a rule change):** Rust `HashSet`/`HashMap`
give you *value* set/map-equality directly in the standard library (`==` is true set/map
equality). Java's `Set`/`Map` `.equals` is likewise value-equality — so the two agree — but
Java's *priority queue* and *heap-like* types inherit `Object`/identity `equals`, whereas Rust
`BinaryHeap` supplies **no** `==` at all. Neither affects the discipline (both heaps
canonicalise as a multiset by their element semantics), but a reviewer comparing the `==`
columns should not expect a heap `==` to exist on either side.

---

## 6. Gaps — what a std-only Rust binding can and cannot express

The rule is language-neutral, but the *type inventories* are not identical. A binding must know
which disciplines it can represent natively.

### 6.1 Java disciplines with NO std-Rust equivalent

- **Insertion-ordered set/map (PRESERVE-hash — `orderedset:`/`orderedmap:` over a hash
  container).** Java `LinkedHashSet`/`LinkedHashMap`. **std Rust has none** — std gives only
  `HashSet`/`HashMap` (canonicalise) and `BTreeSet`/`BTreeMap` (sorted-preserve). To *preserve*
  a received insertion order in a hash-shaped container you **need `indexmap`** (`ordermap` if
  you also want order-sensitive `==`). Consequence: a std-only Rust **decoder** that receives an
  `orderedset:`/`orderedmap:` field can still honour the wire order by decoding into a `Vec` of
  entries (order preserved) — but it cannot land it in an idiomatic insertion-ordered *set/map*
  type, only a `Vec`/`BTreeMap`/`HashMap`. A std-only **encoder** cannot *produce* an
  insertion-ordered hash set/map value to begin with (no such std type exists), so it never
  needs to emit that discipline from a native value — the gap bites on the decode/round-trip
  side.
- **`EnumSet`/`EnumMap` (PRESERVE, ordinal order).** Rust enums are not integers with a
  reflective `ordinal()`, and there is no std enum-keyed set/map. A Rust binding would represent
  an enum-keyed set as `BTreeSet<MyEnum>` / an enum-keyed map as `BTreeMap<MyEnum, V>` with
  `#[derive(PartialEq, Eq, PartialOrd, Ord)]` on the enum — the derived `Ord` follows
  **declaration order** of the variants, which is the natural analog of Java's `ordinal()`
  order. That lands them in PRESERVE (sorted), which is byte-compatible with Java's
  `EnumSet`/`EnumMap` PRESERVE **iff** the variant declaration order matches the Java enum's
  constant order (the encoder must guarantee this). Alternatively a fixed-size bitset crate
  (e.g. `enumset`) mirrors `EnumSet` more directly but is, again, not std. There is no
  `ordinal()`-by-reflection in Rust, so the ordering must be carried by `derive(Ord)` +
  matching declaration order, not discovered at runtime.
- **`ConcurrentSkipListSet`/`ConcurrentSkipListMap` (PRESERVE, sorted, concurrent).** **No std
  Rust analog** — std has no concurrent sorted map. The *value* discipline is identical to
  `BTreeSet`/`BTreeMap` (sorted-preserve), so on the wire it is the same `orderedset:`/
  `orderedmap:`; a Rust binding would simply use `BTreeSet`/`BTreeMap` (single-threaded) or a
  crate (e.g. `crossbeam-skiplist`) for the concurrent case. The *discipline* is expressible
  (it is sorted-preserve); only the concurrent-sorted *container* is not std.
- **The concurrent FIFO/blocking queues (CANONICALISE_MULTISET).** Java
  `ConcurrentLinkedQueue`/`Deque`, `ArrayBlockingQueue`, `LinkedBlockingQueue`/`Deque`,
  `LinkedTransferQueue`, `DelayQueue`. Rust's std channels (`std::sync::mpsc`) are not
  iterable collections and have no analog here; crate queues (`crossbeam`) exist but are not
  std. The discipline (canonicalise-as-multiset) is expressible via `bag:`; the specific
  concurrent-queue *types* are not std. (Note: a Rust `VecDeque` is the **non-concurrent**
  deque and PRESERVES — it maps to `ArrayDeque`, not to the concurrent deques.)

### 6.2 Rust types with no clean Java analog

- **`&[T]` / `[T]` slices and `[T; N]` fixed arrays as first-class borrowed/sized sequences.**
  Java arrays are the closest analog, but Rust's slice/array distinction (borrow vs owned vs
  compile-time-sized) has no Java counterpart. All three collapse to the same PRESERVE-list
  discipline on the wire (`list:`), so the distinction is a source-language convenience, not a
  wire concern.
- **`Option<T>` as the null analog.** Rust has **no `null`**. Where the Java codec handles a
  `null` element, a `null` map value, or a `null` `HashMap` key (memo §2, "Nulls in
  collections": encoded via the field's `CHOICE { absent NULL, present … }`, `05 00`, sorting
  first), a Rust binding uses `Option<T>` — `None` ↔ the `absent`/NULL arm, `Some(x)` ↔
  `present x`. Concretely: a Rust `HashSet<Option<T>>` or `HashMap<K, Option<V>>` maps cleanly
  onto the Java null cases and canonicalises identically (`None`/`05 00` sorts first, exactly as
  the Java null does). **Divergence to note:** Rust `HashMap` keys are `K: Eq + Hash` and there
  is no "null key" — a `None` key only exists if you *choose* `HashMap<Option<K>, V>`, whereas
  Java `HashMap` permits a `null` key in a `HashMap<K,V>` implicitly. So the Java "one null key,
  sorts first" case (memo §2) has a Rust equivalent **only** if the Rust type is explicitly
  `Option`-keyed; a plain `HashMap<K,V>` in Rust simply cannot carry the null-key case, which is
  a *narrowing*, not a conflict — the wire form (`absent` arm) is identical when it does occur.
- **Persistent / structural-sharing collections (`im`, `rpds`).** No Java analog in `java.util`
  (Java's immutable collections — `List.of`, `Set.of` — are not structural-sharing persistent
  data structures). They classify by the same discriminator anyway: `im::Vector` → PRESERVE
  (list), `im::OrdMap`/`OrdSet` → PRESERVE (sorted), `im::HashMap`/`HashSet` → CANONICALISE. The
  persistence is an implementation property invisible to the wire.

### 6.3 What a std-only Rust binding CAN express

- **PRESERVE (list):** `Vec`, `VecDeque`, `LinkedList`, `[T; N]`, `&[T]` → `list:`. ✔
- **PRESERVE (sorted set/map):** `BTreeSet`, `BTreeMap` → `orderedset:`/`orderedmap:`
  (sorted-preserve; also the target for `TreeSet`/`TreeMap`/`ConcurrentSkipList*`/enum-keyed via
  `derive(Ord)`). ✔
- **CANONICALISE (set/map):** `HashSet`, `HashMap` → `set:`/`map:`. ✔
- **CANONICALISE_MULTISET:** `BinaryHeap` → `bag:`. ✔

### 6.4 What a std-only Rust binding CANNOT express (needs a crate, or degrades to `Vec`)

- **Insertion-ordered hash set/map (PRESERVE-hash, `orderedset:`/`orderedmap:` carrying a
  `LinkedHash*`-style insertion order):** needs `indexmap`/`ordermap`. Without it, a decoder can
  only preserve the received order in a `Vec` of elements/entries (order kept, but not in a
  set/map type); an encoder has no native value to emit.
- **Concurrent hash map (`ConcurrentHashMap` analog):** the *discipline* (canonicalise) is fine
  with std `HashMap`, but a genuinely concurrent map needs `dashmap` — which also canonicalises,
  so no wire difference.
- **Enum-keyed set/map with true ordinal semantics, concurrent sorted maps, concurrent
  queues:** as in §6.1 — the disciplines are expressible (they reduce to sorted-preserve or
  multiset-canonicalise), but the idiomatic *container types* are crate-provided, not std.

**Bottom line for a binding author.** Every STD-006 *discipline* is expressible in std-only
Rust — PRESERVE-list (`Vec`/`VecDeque`/…), PRESERVE-sorted (`BTree*`),
CANONICALISE (`Hash*`), CANONICALISE_MULTISET (`BinaryHeap`). The **one wire form with no
native std producer** is the insertion-ordered *hash* set/map (`orderedset:`/`orderedmap:` over
a `LinkedHashSet`/`LinkedHashMap`-shaped value): a std-only peer can *decode* it order-faithfully
into a `Vec`, but cannot represent it as an idiomatic ordered set/map without `indexmap`, and
cannot *originate* such a value at all. Everything else round-trips with std types alone, and —
by §5 — equal Rust values encode to the same DER as equal Java values.

---

## 7. Verification notes (sources)

All Rust semantics below were verified against the cited docs on 2026-07-05, not assumed.

- **`HashMap` / `HashSet`** — `iter()`/`keys()`/`values()` "arbitrary order"; default
  `RandomState`/SipHash 1-3; "each instance uses a different seed"; "internally visits empty
  buckets too"; `HashSet::new` not usable in const context.
  (`doc.rust-lang.org/std/collections/struct.HashMap.html`,
  `.../struct.HashSet.html`, `.../hash_map/struct.HashMap.html`.)
- **`BTreeMap` / `BTreeSet`** — iterators "produce their items in key order"; total order via
  `Ord`; deterministic. (`doc.rust-lang.org/std/collections/struct.BTreeMap.html`.)
- **`Vec` / slice / `[T;N]`** — `PartialEq` order-sensitive (positional), iteration index order.
  (`doc.rust-lang.org/std/vec/struct.Vec.html`.)
- **`VecDeque`** — `iter()` "front-to-back", `PartialEq` order-sensitive.
  (`doc.rust-lang.org/std/collections/struct.VecDeque.html`.)
- **`LinkedList`** — forward iterator, `PartialEq` order-sensitive.
  (`doc.rust-lang.org/std/collections/struct.LinkedList.html`.)
- **`BinaryHeap`** — max-heap; `iter()` "arbitrary order" / "unsorted (and unspecified)";
  duplicates allowed (`from([-10,1,2,3,3])`); **does not implement `PartialEq`/`Eq`**.
  (`doc.rust-lang.org/std/collections/struct.BinaryHeap.html`.)
- **`indexmap::IndexMap`/`IndexSet`** — insertion order "does not depend on the values or the
  hash function at all"; "all iterators traverse … in order"; `==` order-INDEPENDENT
  (HashMap-compatible), `par_eq` an explicit content-equality; sibling crate `ordermap` provides
  order-sensitive `==`. (`docs.rs/indexmap`, and the `indexmap`/`ordermap` crate comparison
  notes.)
- **`dashmap::DashMap`** — sharded concurrent hash map, no ordering guarantee.
  (`docs.rs/dashmap`.)

### Cells a reviewer should double-check (genuine ambiguity)

1. **`HashMap`/`HashSet` "arbitrary even with a fixed hasher"** (§2 flag) — sound, but the std
   docs say "arbitrary", not a verbatim fixed-hasher carve-out. Discipline (CANONICALISE)
   unaffected.
2. **`BinaryHeap` iteration order** (§3) — confirmed "arbitrary"/"unsorted (and unspecified)",
   and it has **no** `PartialEq`, so the §5 `==` parallel is vacuous for it, not positive.
3. **`IndexMap`/`IndexSet` `==` order-independence** (§4 flag) — the trickiest; documented via
   the `par_eq` method and the `ordermap` contrast rather than a one-line struct-doc sentence.
   Discipline (PRESERVE) unaffected either way, but the §5 bucket ("tension" vs "no tension")
   depends on it.
