# Demonstration 6: Two different collection classes, one value, one encoding

## What you will see

Three records that are **equal by value**, each holding the same members in a
different collection implementation — a `HashSet`, a `TreeSet`, and a
`LinkedHashSet` assigned to the same field declared as `Set` — are written in the
canonical format. All three come out **byte-for-byte identical**, with the same
checksum. The same is shown for `List` (`ArrayList` vs `LinkedList`, same order)
and for `Map` (`HashMap` vs `TreeMap` vs `LinkedHashMap`).

Then the very same collections are written with stock Java serialization
(`java.io.ObjectOutputStream`): every implementation produces **different** bytes,
because the default format records the concrete class and its internal layout
along with the value.

The reason: the canonical format serialises the collection's *value* — the thing
its `.equals` compares — never the implementation holding it. A field declared
`Set` or `Map` promises no iteration order, so the encoder sorts the element
encodings into the one canonical order (the ASN.1 `SET OF` rule, X.690 §11.6).
A field declared `List` promises a positional order that *is* part of the value,
so that order is preserved — and two lists holding the same elements in the same
order are already the same value.

### The honest exception, shown, not hidden

A field deliberately declared as `LinkedHashSet` — a type that *guarantees* a
deterministic insertion order — has that order **preserved**, so two routes with
the same waypoints inserted in different orders are `.equals` yet encode to
**different** bytes. This is intended and documented in the wire-format spec
(STD-006 §3.8, the "§3a tension"): if you declare an insertion-ordered type, the
insertion order is part of what you declared. Declare the field as `Set` if only
membership is the value.

## How to run it

```
# from the showcase folder
./run-demos.ps1        # or ./run-demos.sh   (runs all demonstrations)
```

or on its own, after `mvn -q package -DskipTests`:

```
java -cp "target/classes;target/lib/*" au.net.zeus.jgdms.showcase.demo.CollectionEqualityDemo
```

The program asserts every claim it prints (canonical bytes identical, stock Java
bytes all different, the `LinkedHashSet` exception behaving exactly as documented)
and exits non-zero if any claim fails.

## The same rule in other languages (informative)

The ordering rule is a property of collection *semantics*, not of Java. This
repository's cross-language research notes map the same disciplines onto the Rust
and Haskell types whose documented iteration-order guarantees put them in the same
canonical DER form:

| Java declared type | Wire form | Rust | Haskell |
|---|---|---|---|
| `Set` / `HashSet` | `SET OF`, octet-sorted | std `HashSet<T>` | `Data.HashSet` (unordered-containers) |
| `Map` / `HashMap` | `SET OF` entries, key-sorted | std `HashMap<K,V>` | `Data.HashMap` (unordered-containers) |
| `List` (`ArrayList`, ...) | `SEQUENCE OF`, order preserved | std `Vec<T>` / `VecDeque` / `LinkedList` | `[a]`, `Data.Sequence.Seq` (containers) |
| `SortedSet` / `TreeSet` | `SEQUENCE OF`, sorted order preserved | std `BTreeSet<T>` | `Data.Set` (containers) |
| `SortedMap` / `TreeMap` | `SEQUENCE OF`, sorted order preserved | std `BTreeMap<K,V>` | `Data.Map` (containers) |
| `LinkedHashSet` / `LinkedHashMap` | `SEQUENCE OF`, insertion order preserved | `indexmap` `IndexSet`/`IndexMap` (crate — none in std) | `OSet`/`OMap` (ordered-containers package — none in base) |
| `PriorityQueue` | `SET OF`, octet-sorted multiset | std `BinaryHeap<T>` | `MinQueue` (pqueue) / `Heap` (heaps) |

Sources: `docs/der-rust-collection-mapping.md` and
`docs/der-haskell-collection-mapping.md`, companions to
`docs/der-collection-ordering-research.md` and
`docs/der-type-model-and-element-rule.md`. Notably, the `LinkedHashSet` exception
reproduces in Rust for exactly the same reason (`indexmap`'s `==` ignores
insertion order while its iteration preserves it), while Haskell's
ordered-containers defines `==` order-sensitively, so there bytes and `==` agree
exactly. Two independently designed ecosystems hitting the same fork is the
evidence the rule is semantic, not a Java quirk.

**Caveat, stated plainly:** the Rust and Haskell DER encoders are future work.
The table is the *documented canonical-form mapping* from the research notes —
what *would* encode byte-equal, grounded in those languages' own documentation —
not a live cross-runtime byte comparison. The Java measurements in the demo output
are the measured part.

## Honesty notes

- **The contrast uses stock `java.io.ObjectOutputStream`, on purpose.** This
  project's own Java-serialization-compatible stream (`AtomicMarshalOutputStream`)
  already substitutes any `Set`/`Map` with a neutral serial form
  (`SetSerializer`/`MapSerializer`) that drops the implementation class — a real
  hardening step — but it still writes the runtime instance's *iteration* order,
  which for a hash container is bucket order, not a canonical function of the
  value. (In an earlier draft of this demonstration it made a `HashSet` and a
  `TreeSet` coincidentally byte-equal — because those three strings' hash order
  happened to match sorted order — while the `LinkedHashSet` differed. A
  coincidence is not a guarantee.) Only the canonical DER path makes equal values
  byte-identical by construction.
- The demonstration covers the `Set`, `List`, and `Map` categories with real
  measured bytes, plus the `LinkedHashSet` exception. The `SortedSet`/`SortedMap`
  and `PriorityQueue` rows of the table are exercised by the codec's own test
  suite (`CollectionOrderingTest` in `jgdms-der`), not re-measured here.

## Why it matters

Signatures, checksums, and byte-equality template matching (demonstration 2) only
work if the same value always has the same bytes. Collections are where that
promise usually dies: the same logical set can live in a dozen implementation
classes, each with its own serialized form and its own iteration order. The
canonical format erases the implementation entirely — so a record built with a
`HashSet` on one machine and a `TreeSet` on another still matches byte-for-byte,
and a signature over either verifies against both.
