# Design memo: encounter order of Set/Map/Collection fields in JGDMS-STD-006 (DER)

**Status:** design research. Recommendation REVISED per owner decision (see note below).
**Author:** research pass, 2026-07-04.

> **Revision note (owner + board decision, 2026-07-04 — FINAL).** This memo originally
> recommended *canonicalize-by-default including `LinkedHashSet`/`LinkedHashMap`, with an
> explicit per-field opt-in to preserve*. **That recommendation is superseded.** The
> settled discriminator is **whether the declared type guarantees a DETERMINISTIC
> iteration order** (owner: "typically the iterator follows the order" — preserve the
> iterator's order, but *only* when that order is deterministic). The JDK 21
> sequenced-collection interfaces (`java.util.SequencedCollection`/`SequencedSet`/
> `SequencedMap`, JEP 431) are the *backbone* of the preserve side, but the test is
> determinism, not interface membership — which is why two cases do **not** follow naively
> from "implements `Sequenced*`":
>
> - **PRESERVE (deterministic iteration order):**
>   - *element-derived order* (deterministic, and **no** byte-vs-`equals` tension — equal
>     instances serialise identically): `SortedSet`/`TreeSet`, `SortedMap`/`TreeMap`,
>     `ConcurrentSkipListSet`/`ConcurrentSkipListMap`, `EnumSet`/`EnumMap` (natural ordinal
>     order — the reason is *element-derived order*, not merely that they are `Sequenced*`).
>   - *positional / order-IS-the-value*: `List` (`ArrayList`, `LinkedList`, `Vector`,
>     `CopyOnWriteArrayList`), arrays incl. `byte[]`, `Deque` (`ArrayDeque`). (`List.equals`
>     is order-sensitive, so no tension.)
>   - *non-concurrent insertion order* (deterministic, app-controlled, **with** tension —
>     byte-equality stricter than `.equals`): `LinkedHashSet`, `LinkedHashMap`.
> - **CANONICALISE (X.690 §11.6 octet-sort — order non-deterministic or unspecified):**
>   - *hash-bucket order* (iterator disclaims order): `HashSet`, `HashMap`,
>     `ConcurrentHashMap`.
>   - *concurrent insertion-history SET*: `CopyOnWriteArraySet`. **This REVERSES the naive
>     preserve classification** — its insertion order is race-dependent under concurrency,
>     so it is **not** deterministic. It differs from `LinkedHashSet` (non-concurrent → its
>     insertion order *is* deterministic → preserve). Concurrency undermines determinism
>     specifically for insertion-history *sets*, **not** for comparator-sorted concurrent
>     collections (`ConcurrentSkipListSet`/`Map` preserve) nor for concurrent *Lists*
>     (`CopyOnWriteArrayList` preserves — order is its value).
>   - *iterator explicitly disclaims order; natural (priority) order is a function of
>     elements+comparator, rebuilt at the receiver*: `PriorityQueue`, `PriorityBlockingQueue`.
>
> **Governing principle (owner's words):** *"We shouldn't break the order of something
> that has order — we should respect it, or it will cause bigger problems."* Refined to
> the determinism test: **an encoder preserves an order only if the type guarantees it is
> deterministic.** Preservation is the safe default; canonicalisation is reserved for the
> genuinely non-deterministic. The type contract *is* the signal — no separate per-field
> opt-in, annotation, or extra schema token is needed to decide preserve-vs-canonicalise.
>
> A new **§3a "Serialized equality and encounter order (the documented tension)"** records
> a deliberate consequence the owner asked to be documented, not resolved: the byte-vs-
> `equals` gap (serialized byte-equality *stricter* than object `.equals`) applies **only
> to the insertion-ordered `LinkedHashSet`/`LinkedHashMap`**; the element-derived-order and
> positional preserve types have no such tension. The parts of this memo that remain correct
> — the no-`hashCode` reasoning, the X.690 §11.6 octet-sort mechanics (scoped to the
> canonicalise side), the design-now/build-later scoping (the built codec has no
> `set:`/`map:` token yet), and the prior-art survey — are retained. Sentences that predate
> this decision are corrected in place; where a longer passage is left for the record, it is
> flagged **[SUPERSEDED — see revision note]**.
**Anchors read:** STD-006 v0.13 §3.2 (folded into design principle 2 "DER not BER"),
§3.7 (acyclic), §3.8 (values-not-behaviour; intrinsic-order + opaque-octet carve-outs),
§3.9–§3.11 (namespace/GetArg), §4.5 (module validity, `maxCollection`=65536), §7.6
(substituted types + the "Collections are NOT in this catalogue" note), §7.7.1–§7.7.2
(Entry schema identity + byte-equality template matching), §9 (conformance).
**Codec inspected (trunk):** `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\jgdms-der\src\main\java\...`
— `getarg\WireTypes.java`, `object\ObjectCodec.java`, `serial\DerReplacer.java`, `Tag.java`.

---

## 0. The problem, restated precisely

STD-006 is DER (§3.2 / principle 2): *one canonical encoding per value*. That canonicity
is load-bearing in exactly three places:

1. **Signature verification** — any collection field inside a signed TBS (`tbs` §7.4.1,
   `MulticastAnnouncementTbs`/`MulticastRequestTbs` §7.7.7, SCAP verdicts §7.4) must
   produce identical bytes on the signer and every verifier, or the signature fails.
2. **`@AtomicSerial` schema digests** — `schemaDigest` over `schemaBytes` (§7.8), and the
   `EntrySchemaRecord` type-identity hash (§7.7.1); these are content addresses.
3. **Jini Entry byte-equality template matching (§7.7.2)** — the Registrar matches a
   template `EntryFieldValue.present OCTET STRING` against a stored one *by byte equality*,
   never decoding the field type. Same logical value MUST be the same bytes.

STD-006 today encodes every collection (`Set`/`Map`/`List`) as an order-**preserving**
DER `SEQUENCE OF` (§3.8, §7.6 `CollectionField`/`MapField`), with the rule "order is
neither asserted nor relied upon on the wire." Under the final rule the discriminator
is whether the declared type **guarantees a DETERMINISTIC iteration order**:

- **A deterministic-order type** (the `Sequenced*` backbone — `List`, `Deque`,
  `LinkedHashSet`, `LinkedHashMap`, `SortedSet`/`TreeSet`, `SortedMap`/`TreeMap` — plus
  `EnumSet`/`EnumMap`, the concurrent *sorted* `ConcurrentSkipListSet`/`Map`,
  `CopyOnWriteArrayList`, and arrays incl. `byte[]`) has an iteration order that is
  reproducible and meaningful. Preservation is correct and required; reordering corrupts
  the value.
- **A non-deterministic-order type** (`HashSet`, `HashMap`, `ConcurrentHashMap`;
  `CopyOnWriteArraySet`; `PriorityQueue`/`PriorityBlockingQueue`) has *no* reproducible
  iteration order, so order-preservation does **nothing for byte determinism on encode**.
  A `HashSet` of the same three logical elements, built by two different insertion orders —
  or on two JVMs — iterates in different orders, so `SEQUENCE OF` in iteration order yields
  different octets. Inside a signature or an Entry field value, that is a correctness bug:
  the same logical set fails to verify / fails to match itself. This is the case the
  §11.6 octet-sort canonicalisation exists for.

`SEQUENCE OF` preserving order is *right* for every deterministic-order type and
*insufficient* for the non-deterministic ones. Determining the boundary between the two —
via the determinism test — and documenting the equality consequence of preserving order
are the subjects of this memo.

---

## 1. Q1 — Taxonomy of JDK collection types

Classification key (**final**: the discriminator is whether the declared type **guarantees
a deterministic iteration order**, not a "value vs behaviour" judgement call and not raw
`Sequenced*` membership):

- **PRESERVE (deterministic iteration order)** — the type's iterator yields a reproducible,
  meaningful order. That order is part of the value and MUST be preserved on the wire
  exactly as given (order-significant `SEQUENCE OF`, §3.8 carve-out). Never reorder, sort,
  or canonicalise. Three sub-reasons: *element-derived* (comparator/ordinal — deterministic,
  no tension), *positional* (a `List` / array — order **is** the value), and *non-concurrent
  insertion order* (`LinkedHashSet`/`LinkedHashMap` — deterministic and app-controlled, but
  **with** the §3a tension). Comparator order is deterministic **even when concurrent**
  (`ConcurrentSkipListSet`/`Map`), because it is a function of the elements, not of a race.
- **CANONICALIZE (non-deterministic / unspecified order)** — the iterator's order is
  hash-derived, or (for insertion/FIFO-history collections) race-dependent under
  concurrency, or explicitly disclaimed. It carries no value and is not reproducible across
  runs/JVMs/threads. Canonicalize by encoding (Q2, §11.6 octet-sort).
- **ARRAY (PRESERVE)** — arrays and primitive arrays (`byte[]` above all) are positional;
  order *is* the value. Always preserved (§3.8).

The load-bearing question: **does the declared type guarantee a deterministic iteration
order?** If yes → preserve. If not → canonicalise. This replaces the earlier
INTRINSIC/BEHAVIOURAL/AMBIGUOUS trichotomy (and the interim "does it implement `Sequenced*`"
shortcut): `Sequenced*` membership is the *backbone* of the preserve side, but the two
concurrency-sensitive cases are decided by determinism, not membership — a concurrent
insertion-ordered *set* (`CopyOnWriteArraySet` — a plain `Set`, **not** `SequencedSet`)
canonicalises because order is not part of a `Set`'s value and its concurrent insertion
order is non-deterministic anyway, while a comparator-sorted concurrent set
(`ConcurrentSkipListSet`, which **is** `SequencedSet` via `SortedSet`) and a concurrent
*List* (`CopyOnWriteArrayList`) both preserve.

| Type | Iteration order | Class (final) | Why |
|---|---|---|---|
| **Set** | | | |
| `HashSet` | none (bucket order: capacity + spread(hash) + insertion/resize history) | **CANONICALIZE** | non-deterministic hash-bucket order, unstable across insertion order, capacity, JVM. Octet-sort. |
| `LinkedHashSet` | non-concurrent insertion order (deterministic) | **PRESERVE** | app-controlled insertion order, deterministic. Preserve exactly; never sort. *Insertion order is non-rederivable history → §3a tension (byte-equality stricter than `.equals`).* |
| `TreeSet` | comparator / natural order (element-derived, deterministic) | **PRESERVE** | `SortedSet`; encounter order is a function of elements+comparator, so two `.equals` `TreeSet`s serialise identically — **no §3a tension**. |
| `EnumSet` | enum `ordinal()` ascending (element-derived, deterministic) | **PRESERVE** | ordinal-ascending, a deterministic function of the elements. Preserve; **no §3a tension**. (Reason is element-derived order, *not* merely `Sequenced*` membership.) |
| `CopyOnWriteArraySet` | **concurrent** insertion order (race-dependent → NON-deterministic) | **CANONICALIZE** | canonicalise because order is **not part of a `Set`'s value** (Tier 2, §1c) **and** its concurrent insertion order is non-deterministic anyway. **Reverses the naive preserve call.** Contrast `CopyOnWriteArrayList` (same impl, but a `List` → preserve) and `LinkedHashSet` (a `Set` too, but non-concurrent so its insertion order *is* deterministic → preserve). Octet-sort. |
| `ConcurrentSkipListSet` | comparator / natural order (element-derived, deterministic even when concurrent) | **PRESERVE** | comparator order is a function of the elements, not of a race — deterministic despite concurrency. Same bucket as `TreeSet`; no tension. |
| **Map** (each entry = `{key,value}`) | | | |
| `HashMap` | none (bucket order of keys) | **CANONICALIZE** | non-deterministic; canonicalize entries by encoded key (§11.6). |
| `LinkedHashMap` | non-concurrent insertion (deterministic) | **PRESERVE** | app-controlled insertion order. Preserve entry order → §3a tension. *Caveat:* an **access-order** `LinkedHashMap` (LRU cache) mutates its order on every read — a poor thing to serialise, but the order at the instant of encoding is still what the rule preserves; declare a canonicalise (`HashMap`) `Map` if order-independence is wanted. |
| `TreeMap` | key comparator / natural (element-derived, deterministic) | **PRESERVE** | `SortedMap`; entry order is a function of keys+comparator → **no §3a tension**. |
| `EnumMap` | key `ordinal()` (element-derived, deterministic) | **PRESERVE** | ordinal-ordered; deterministic function of keys. Preserve; **no §3a tension** (reason: element-derived order, not `Sequenced*` alone). |
| `ConcurrentHashMap` | none (bucket order; resize-dependent) | **CANONICALIZE** | non-deterministic, even less stable than `HashMap`. Octet-sort entries by encoded key. |
| `ConcurrentSkipListMap` | key comparator / natural (element-derived, deterministic even when concurrent) | **PRESERVE** | comparator order is element-derived, deterministic despite concurrency. Same bucket as `TreeMap`; no tension. |
| `Properties` | none (inherited `Hashtable` bucket order) | **CANONICALIZE** | non-deterministic; §7.6 tags it "behavioural; unordered." Octet-sort entries by encoded key (String). |
| **List** | | | |
| `ArrayList` | index order (positional, deterministic) | **PRESERVE** | positional, index *is* the value. Preserve. |
| `LinkedList` (as List) | index order (positional) | **PRESERVE** | positional; §3.8 names "linked lists" in the carve-out. Preserve. |
| `CopyOnWriteArrayList` | index order (positional; concurrent but order **is** the value) | **PRESERVE** | preserve because order **IS** a `List`'s value (Tier 1, §1c) — it can never be reordered regardless of concurrency; its construction determinism is the *application's* concern, not the format's. (Contrast `CopyOnWriteArraySet`: same impl, but a `Set` → canonicalise.) |
| `Vector` | index order (positional) | **PRESERVE** | positional `List`. Preserve. |
| **Queue / Deque** | | | |
| `ArrayDeque` | non-concurrent head→tail order (deterministic) | **PRESERVE** | a deque's app-controlled head-tail order is deterministic. Preserve. |
| `LinkedList` (as Queue/Deque) | non-concurrent head→tail (deterministic) | **PRESERVE** | `Deque`; deterministic sequence order. Preserve. |
| `ConcurrentLinkedQueue`, `ConcurrentLinkedDeque` | **concurrent** FIFO/insertion order (race-dependent) | **CANONICALIZE** | concurrent insertion/FIFO order is race-dependent → non-deterministic. (The Deque split falls exactly on the concurrency line: non-concurrent `ArrayDeque` preserves; concurrent `ConcurrentLinkedDeque` canonicalises.) Octet-sort as multiset. |
| `ArrayBlockingQueue`, `LinkedBlockingQueue`, `LinkedBlockingDeque`, `LinkedTransferQueue`, `DelayQueue` | **concurrent** FIFO/insertion order (race-dependent) | **CANONICALIZE** | concurrent insertion/FIFO iteration is race-dependent → non-deterministic. Octet-sort as multiset. (`DelayQueue`'s iterator does not return delay order.) |
| `SynchronousQueue` | holds no elements (zero-capacity handoff) | **CANONICALIZE** (trivially empty) | never holds elements; serialises as an empty collection. Listed for completeness. |
| `PriorityQueue` | **none** (heap array order — NOT sorted, iterator disclaims order) | **CANONICALIZE as multiset** | `iterator()` returns heap-array order (insertion/sift artefact), not priority order and not a value function. See §1b. |
| `PriorityBlockingQueue` | **none** (iterator disclaims order; also concurrent) | **CANONICALIZE as multiset** | same as `PriorityQueue` — the iterator disclaims order (this holds independent of concurrency). Priority order rebuilt at receiver. See §1b. |

### 1a. The insertion-ordered preserve types (`LinkedHashSet` / `LinkedHashMap`) — PRESERVE, with the §3a tension

Earlier drafts treated these as the "hard case" and recommended default-canonicalise. The
final decision resolves it: **`LinkedHashSet`/`LinkedHashMap` guarantee a deterministic,
app-controlled insertion order, so they are PRESERVED** — with the caveat that they are the
*only* preserve types carrying the §3a byte-vs-`.equals` tension.

- These are `SequencedSet`/`SequencedMap`, but the operative reason is **determinism**: a
  *non-concurrent* insertion order is reproducible and app-controlled. (This is exactly what
  separates `LinkedHashSet` from `CopyOnWriteArraySet`, whose concurrent insertion order is
  race-dependent and therefore canonicalises.)
- The governing principle — *don't break the order of something that has order* — means
  the author's choice of `LinkedHashSet` (an ordered option list, a display sequence, a
  certificate chain in a `Subject`'s `ClassSet`) is respected on the wire, not silently
  dropped. This is the safe direction: silently reordering a guaranteed order corrupts
  developer intent and surfaces as hard-to-trace downstream failures.
- **The cost, documented not resolved (§3a):** `LinkedHashSet`/`LinkedHashMap` inherit the
  order-*insensitive* `Set`/`Map` `.equals`, so `new LinkedHashSet<>([a,b]).equals(new
  LinkedHashSet<>([b,a]))` is `true`, yet the two serialise to *different* octets because
  their insertion order differs and we preserve it. For these **insertion-ordered** types
  the gap is real (insertion order is non-rederivable history). A field that instead wants
  order-independent wire equality must be declared as a canonicalise (`HashSet`/`HashMap`)
  `Set`/`Map`. This is the deliberate tension the owner asked to be recorded; see §3a in
  full. The element-derived-order preserve types (`TreeSet`/`TreeMap`, `EnumSet`/`EnumMap`,
  `ConcurrentSkipListSet`/`Map`) and the positional ones (`List`, arrays) have **no** such
  tension.

### 1b. PriorityQueue / PriorityBlockingQueue — multiset + comparator, iteration ≠ sort

`PriorityQueue.iterator()` (and `PriorityBlockingQueue.iterator()`) returns elements in
**heap-array order, not priority order** — and the heap array's layout is a function of the
insertion/sift history, so two priority queues holding the same elements with the same
comparator can iterate differently. So:

- Preserving iteration order is *wrong* (non-deterministic, and not even the useful order).
- The transmissible *value* is a **multiset of elements** (+ a comparator that is *code*,
  reconstructed at the receiver, not wire data).
- Therefore **CANONICALIZE by encoding as a multiset** (duplicates allowed — a priority
  queue may hold equal-priority duplicates; do NOT dedupe). This drops the heap layout,
  which carries no value. The receiver rebuilds the heap with its comparator.

This holds for **both** `PriorityQueue` and `PriorityBlockingQueue`: the disqualifier is the
iterator disclaiming order, which is independent of concurrency (`PriorityBlockingQueue` is
concurrent, but even single-threaded the iterator would not yield priority order).

Note the contrast with `TreeSet`/`TreeMap`/`ConcurrentSkipListSet`/`ConcurrentSkipListMap`:
those expose the comparator order *through the iterator* (a deterministic, element-derived
order), so they PRESERVE; a priority queue's `iterator()` yields heap order, not priority
order, so it canonicalises. The comparator is code in both cases; what differs is whether
the type's *iteration contract* exposes a deterministic encounter order.

Verdict: `PriorityQueue`/`PriorityBlockingQueue` → canonicalize-as-multiset. Never preserve
heap order.

### 1c. Developer guidance: is your collection's order part of its value?

The rule can look arbitrary type-by-type; it is not. One question decides whether STD-006
preserves or canonicalises a collection field's element order: **is element order part of
the collection's value?** The type's own `equals` contract answers it — *not* the
implementation, and *not* what the iterator happens to yield. The taxonomy above is just
this question applied type by type.

**Tier 1 — order IS the value (`List`, arrays).** `List.equals` is order-sensitive: `[a,b]`
and `[b,a]` are different lists, and arrays are positional by nature. Element order is
**always preserved** on the wire — the format never reorders them, because reordering would
change the value (exactly as sorting a `byte[]` would corrupt it). *Consequence:* if you
need a `List`'s serialized bytes to be reproducible (for a signature, or for byte-matching),
**you** must construct the list deterministically. The format faithfully serialises whatever
order the list has; it cannot "fix" a non-deterministically-built list by reordering,
because that order *is* the value.

**Tier 2 — order is NOT part of the value (`Set`, `Map`).** `Set.equals` and `Map.equals`
are order-independent: `{a,b}` equals `{b,a}`. Because order is not part of the value, the
format is free to reorder, and it uses that freedom to guarantee **equal values produce
equal bytes** — so byte-comparison is a sound proxy for value-comparison, which Jini Entry
byte-matching (§7.7.2) and cross-runtime interop rely on. *Within Tier 2* the format still
splits, on whether the type guarantees a deterministic order worth carrying:

- **PRESERVE** the encounter order when the type guarantees a deterministic one:
  `LinkedHashSet`/`LinkedHashMap` (app-controlled insertion), `SortedSet`/`SortedMap` and
  `EnumSet`/`EnumMap` (order derived from the elements).
- **CANONICALISE** (octet-sort) when the order is unspecified or non-deterministic:
  `HashSet`/`HashMap` (hash-bucket order) and concurrent insertion-ordered sets like
  `CopyOnWriteArraySet` (race-dependent insertion order).

**Worked example — `CopyOnWriteArrayList` vs `CopyOnWriteArraySet`.** Same backing
implementation, same insertion order, **opposite sides** — which is exactly why the
discriminator is the value-contract, not the implementation:

- `CopyOnWriteArrayList` is a **`List`** → order **is** the value → **PRESERVED** (two
  different insertion orders are two different lists; the format may never reorder them).
- `CopyOnWriteArraySet` is a **`Set`** → order is **not** the value → **CANONICALISED** (two
  different insertion orders of the same elements are the *same* set, so they must serialise
  to the same bytes; its concurrent insertion order is non-deterministic anyway).

**Practical guidance.** If element order carries meaning in your data, model it with a `List`
(or another order-guaranteeing type) — its order survives the wire. If you use a `Set`/`Map`
and depend on a specific iteration order surviving serialisation, note that only the
deterministic-order types (`LinkedHashSet`, `SortedSet`, `TreeMap`, enums) preserve it, and
even those make serialized byte-equality **stricter** than `.equals` (§3a — two `.equals`
`LinkedHashSet`s with different insertion order will not byte-match). For a `Set`/`Map` field
that must byte-match by content — inside a signature, or as a Jini Entry — canonicalisation
is precisely what makes byte-equality agree with value-equality.

---

## 2. Q2 — The canonical rule for the canonicalise side (non-deterministic-order Set/Map)

This section applies **only to the canonicalise side** — a `Collection`/`Set`/`Map` whose
declared type does **not** guarantee a deterministic iteration order: the hash types
(`HashSet`, `HashMap`, `ConcurrentHashMap`, `Properties`), the concurrent insertion/FIFO
types (`CopyOnWriteArraySet`, `ConcurrentLinkedQueue`/`Deque`, the blocking queues/deques,
`LinkedTransferQueue`, `DelayQueue`, `SynchronousQueue`), and the priority queues
(`PriorityQueue`, `PriorityBlockingQueue`). Preserve-side types (§1, §3.8) are never
octet-sorted; the mechanics below do not apply to them.

**Rule (DER SET OF octet-sort, X.690 §11.6):**

> Encode each element to its canonical DER (recursively — an element that is itself a
> canonicalise-side collection is canonicalized first, bottom-up; a nested *preserve-side*
> element is encoded in its own preserved order first, then treated as an opaque octet
> string by the outer sort). Then order the *element encodings* in **ascending order,
> compared as octet strings**, where — for the comparison only — the shorter encoding is
> conceptually padded at its trailing end with 0x00 octets; the padding is never emitted.
> Emit the elements in that order inside the `SEQUENCE OF`.

This is exactly X.690 §11.6 ("Set-of components"). It is the DER canonical form for an
unordered collection and it is a **pure function of the element values** — which is why it,
and only it, gives machine/run independence (see §3).

Note on §11.5 vs §11.6: §11.5 (SET) sorts a *heterogeneous* SET's components by **tag**;
that is not our case. §11.6 (SET OF) sorts a *homogeneous* collection's components by their
**full octet encodings**; that is our case. We adopt the §11.6 octet-sort. (Whether we emit
the universal SET-OF tag 0x31 or keep the SEQUENCE tag 0x30 with the octet-sort *discipline*
is a wire-tagging choice — see §6/decision; the sort algorithm is §11.6's either way.)

**Map.** A `Map` is `SEQUENCE OF SEQUENCE { key, value }`. Canonicalize by sorting the
`{key,value}` entries by the **encoded key** octets (§11.6 applied to the entry encodings —
which, because the key is the first component, orders primarily by key). Keys in a Map are
distinct *values*; distinct values have distinct canonical DER encodings (DER is injective
on values by construction), so **no two entries can have colliding key encodings** — the
sort is total and unambiguous. (If two keys encoded identically they would *be* the same
value and the Map could not contain both.) Sorting the whole entry encoding vs the key-only
encoding gives the same order here because the key is the leading component and keys are
unique; sort by the entry encoding for simplicity and it is still key-determined.

**Duplicates after canonicalization.**
- A **Set** cannot contain post-canonical duplicates: two elements with identical canonical
  encodings are the same value, so the source Set already held only one. If a decoder sees
  two identical element encodings in a set-typed field, that is a malformed encoding →
  reject (fail-secure, principle 6) or the field is actually a List/multiset (typed
  differently). Recommended: for set-typed fields, **reject duplicate element encodings**.
- A **multiset** (a priority-queue-as-multiset, or any canonicalise-side collection that may
  hold duplicates — note a `List` is never canonicalised; Lists are positional/preserve-side)
  may legitimately hold duplicates; keep them. This is why the *field's declared discipline*
  (Q3), not the byte pattern, decides whether a duplicate is legal.

**Nulls in collections.** DER has no bare "null element" universal form here; STD-006
already models optional/absent element values via a `CHOICE { absent NULL, present ... }`
(exactly the §7.7.2 `EntryFieldValue` pattern). A null element/value encodes as the `absent`
arm's NULL TLV (`05 00`), which is a well-defined 2-byte octet string and sorts naturally
under §11.6 (it is short, sorts early). So: a `Set` containing `null`, or a `Map` with a
`null` value, is representable and canonically ordered without special-casing. A `null`
**key** in a Map is permitted by `HashMap` — it encodes as `absent`/NULL and sorts first;
only one null key can exist (keys unique), so no ambiguity. (Constructor/`check()` may still
reject nulls per the object's contract — that is behaviour, §3.8, not a wire concern.)

**Recursion.** The rule is applied **bottom-up**: to canonicalize a `Set<Map<...>>`, first
canonicalize each inner `Map` to its bytes, then octet-sort the outer set of those byte
strings. Because each level's output is canonical, the composition is canonical. This is the
only correct order — sorting an outer collection before its elements are canonicalized would
sort non-canonical bytes.

**Interaction with the opaque-octet carve-out (§3.8).** No conflict, and it is in fact the
clean case. A `Set<X509Certificate>` carries each certificate as **opaque octets, verbatim
`getEncoded()`** (never re-encoded). Those verbatim octet strings are *still just octet
strings*, so §11.6's octet-sort orders them directly and deterministically without parsing
or re-encoding — the carve-out (never touch the inner bytes) and the SET-OF sort (order the
outer TLVs) are orthogonal and both satisfied. A `Set` of `X500Principal` opaque octets is
identical in treatment. The sort reads the outer element TLV octets; it never inspects,
normalizes, or re-canonicalizes the opaque payload.

---

## 3a. Serialized equality and encounter order (the documented tension)

This section records a consequence of the preserve-order rule that is **deliberate and
must be understood — the owner has asked that it be documented, not "resolved" by
canonicalising the order away.** It is the price of respecting a type that has a defined
encounter order, and it is the correct price to pay.

**The mechanism.** For a preserve-side type, element order is preserved, so it is part of
the *serialized identity*: the octets depend on the iteration order. Serialized byte-equality
is therefore **order-sensitive** for all preserve-side types. For most of them this is
harmless (their `.equals` is either order-sensitive too, or their order is element-derived —
see the nuance below). The sharp case is the **insertion-ordered `Set`/`Map` preserve
types**: `LinkedHashSet` and `LinkedHashMap` **inherit the order-*insensitive* `Set`/`Map`
`.equals`** — their `equals` ignores their own insertion order (a known JDK characteristic:
`Set`/`Map` equality is defined over membership, not iteration order) — yet we preserve that
insertion order on the wire. Putting these together:

> **For `LinkedHashSet`/`LinkedHashMap`, serialized byte-equality is STRICTER than object
> `.equals`.** Byte-equal ⟹ object-equal, but the converse fails: two instances that are
> `.equals` but were built in different insertion orders serialise to **different** octets
> and will **not** compare byte-equal (nor match under §7.7.2 Entry byte-matching).

This is the correct behaviour for a type whose insertion order is significant. The author
*chose an insertion-ordered type*; the wire honours that order. A field that instead wants
**order-independent** equality on the wire must be declared as a **canonicalise
(`HashSet`/`HashMap`) `Set`/`Map`**, whose §11.6 canonical form makes serialized
byte-equality coincide with `.equals` (two `.equals` `HashSet`s always produce identical
canonical octets). *Choose the collection type accordingly* — the type *is* the declaration
of which equality you want on the wire.

**The nuance — this gap only bites the INSERTION-ordered `Set`/`Map` preserve types.** The
byte≠object mismatch is real only where iteration order is **non-rederivable history** *and*
the type's `.equals` ignores it:

- **Insertion-ordered `Set`/`Map`** — `LinkedHashSet`, `LinkedHashMap` — *do* exhibit the
  mismatch. Two `LinkedHashSet`s `{a,b}` and `{b,a}` are `.equals` but were built in
  different insertion orders, and nothing in the elements themselves recovers that order,
  so they serialise differently. Here the stricter-than-`.equals` behaviour genuinely
  applies.
- **Element-derived-order** preserve types — `SortedSet`/`TreeSet`, `SortedMap`/`TreeMap`,
  `EnumSet`/`EnumMap`, `ConcurrentSkipListSet`/`ConcurrentSkipListMap` — do **not** exhibit
  the mismatch. Their iteration order is a **pure function of the elements plus the
  comparator/ordinal**, so two `.equals` instances necessarily have the **same** order and
  serialise to the **same** octets. Byte-equality and `.equals` coincide for them, just as
  they do for the canonicalised case — no divergence.
- **Positional** preserve types — `List` (incl. `CopyOnWriteArrayList`), arrays — also do
  **not** exhibit it: `List.equals` is *already* order-sensitive, so two `.equals` lists are
  in the same order by definition and serialise identically.

So the practical guidance: if you want order honoured on the wire, use a preserve-side type
and accept that byte-equality is order-sensitive (and, for `LinkedHashSet`/`LinkedHashMap`,
stricter than `.equals`); if you want order-independent wire equality, use a canonicalise
(`HashSet`/`HashMap`) `Set`/`Map` and let §11.6 canonicalise. The element-derived and
positional preserve types give you both (order honoured *and* byte-equality tracking
`.equals`), because their order is derivable from the value.

**Why this is coherent across runtimes.** For a preserve-side type the order is *data
carried on the wire* — a non-JVM peer reads the elements in the transmitted order and
reproduces it without needing to recompute anything. For a canonicalise-side type,
octet-sort lets any runtime produce identical bytes from the same membership without
coordination. In neither case is any flavour of `hashCode` consulted to establish order
(see §3): identity `hashCode` is a per-run PRNG seed, and even a value-`hashCode` gives
bucket order, not a value order. The order is either transmitted (preserved) or derived by
octet-sort (canonicalised); it is never inferred from a hash.

---

## 3. Q3 — The signal for preserve-vs-canonicalize (THE decision), and why no hashCode can be used

Earlier drafts framed this as an *erasure* problem: a field declared `Set<X>` gives the
encoder "no static signal" whether order matters, because erasure hides `HashSet` from
`LinkedHashSet`. **The decision changes the framing.** The signal is not the erased
*concrete class*; it is the **declared type's guarantee of a deterministic iteration
order**. The JDK 21 sequenced-collection interfaces (`SequencedCollection`/`SequencedSet`/
`SequencedMap`, JEP 431) are the *backbone* of that guarantee, but the test is determinism,
not raw interface membership — two concurrency-sensitive cases are decided by determinism:
a concurrent insertion-ordered *set* (`CopyOnWriteArraySet`, a plain `Set` — **not**
`SequencedSet`) canonicalises because a `Set`'s order is not part of its value and its
concurrent insertion order is race-dependent anyway, whereas a comparator-sorted
concurrent set (`ConcurrentSkipListSet`) and a concurrent *List* (`CopyOnWriteArrayList`)
preserve (element-derived / positional order is deterministic regardless of concurrency).
The signal is carried by the type itself and available to the encoder from the
declared/actual type. No separate per-field opt-in, annotation, or extra schema token is
required to decide preserve-vs-canonicalise — the type *is* the declaration.

(The one thing that is still forbidden, whichever way the discriminator points, is deriving
order from any `hashCode`. §3.0 states why; it is unchanged by the decision.)

### 3.0 First, the disqualification: NO hashCode ordering, for two independent reasons

Both reasons must be stated because they kill two *different* tempting shortcuts.

1. **Identity hashCode is a per-run PRNG seed, not the address, and is reproducible on no
   run.** For a type that does not override `hashCode` (or where `System.identityHashCode`
   is used), modern HotSpot's **default** identity-hash mode is `-XX:hashCode=5`: a
   **thread-local Marsaglia xorshift PRNG** whose output is written **lazily into the object
   header** (≤31 bits) the first time the hash is requested. It is *not* the memory address
   (that is mode 4, non-default and itself GC-relocation-unstable). Consequently the value
   differs on every JVM run, every machine, and even between two objects created identically
   in the same run. **Any ordering derived from identity hashCode is reproducible nowhere.**
   This alone kills "sort the set by hash value."

2. **Even a value-based hashCode does not yield a canonical order.** `String`, `Integer`,
   and record `hashCode()` are *specified functions of the value*, so they ARE machine-
   consistent. But `HashMap`/`HashSet` **iteration order is bucket order, not hashCode
   order**: the bucket index is `spread(hashCode) & (capacity-1)`, and the traversal is
   table-slot order, which depends on the table **capacity** (a function of size and load
   factor), the **spread/perturbation** function, and the **insertion/resize history**. Two
   sets with identical stable hashCodes but different capacities or insertion sequences
   iterate differently. **So stable hashCodes still do not give a canonical iteration
   order.** This kills the subtler "the elements have value-based hashCodes so their
   iteration is stable" assumption.

**Conclusion carried into every option below:** the ONLY machine- and run-independent
canonical order is **sorting elements by their canonical DER *encoding*** (§11.6 octet-sort)
— a pure function of the values. This composes with STD-006 because the codec already
encodes each element from its `@AtomicSerial` `serialForm()` **value-fields**, never from
identity or hashCode, so the element bytes are themselves machine-independent, and a sort
over machine-independent bytes is machine-independent. (Opaque-octet elements are likewise
verbatim value bytes.) No option in this memo may fall back to any hashCode.

### 3.1 The candidate signals (for the record)

The options considered before the decision, and why the `Sequenced*` interface contract
wins:

**(a) Canonicalize ALL Set/Map by encoding (uniform).**
Every `Set`/`Map` octet-sorted regardless of type; only `List`/array/deque preserve.
- *Pro:* always deterministic; a non-JVM party needs only "this field is a set/map."
- *Con — the reason this was NOT adopted:* it **silently drops the encounter order of every
  sequenced type** — `LinkedHashSet`, `LinkedHashMap`, `TreeSet`, `TreeMap`. That violates
  the governing principle: *don't break the order of something that has order.* An author
  who chose a `LinkedHashSet` (ordered option list, certificate chain in a `Subject`'s
  `ClassSet`) loses that order with no diagnostic. Rejected as the *default*; retained only
  for the genuinely unordered types, which is exactly what (d) below scopes it to.

**(b) Switch on the runtime *concrete class*.**
`HashSet`→canonicalize, `LinkedHashSet`→preserve, by exact class.
- *Con — disqualifying:* keying on the **concrete class** binds wire bytes to a type the
  *declared* contract does not name, and the **receiver may reconstruct a different concrete
  type** (a `Set<X>` field is often rebuilt as a `LinkedHashSet` or an unmodifiable wrapper
  regardless of what was sent), so a receiver re-encode would not round-trip — breaking the
  "same value → same bytes" invariant. It also demands a non-JVM encoder know the JDK class
  taxonomy. **The interface discriminator (d) fixes this:** it keys on the *contract*
  (`is-a SequencedSet`), which is a stable, receiver-agnostic property — "has a defined
  encounter order" is preserved by any faithful reconstruction, whereas "is exactly a
  `LinkedHashSet`" is not.

**(c) Explicit per-field `ordered`/`unordered` schema token.**
A new wire-type token (`orderedset:` vs `set:`) declares the discipline in the schema.
- *Con — the reason this is NOT needed:* it adds API/schema surface and asks the author to
  re-declare, as a token, information the **type already carries**. A `LinkedHashSet` field
  already says "I have a defined encounter order" by being a `SequencedSet`. The decision
  below makes the *type contract* the signal, so no separate opt-in token is introduced.

**(d) THE DECISION — discriminate on the type's guarantee of a deterministic iteration order.**
The type's own iteration-order contract decides (the `Sequenced*` interfaces are the
backbone; determinism is the actual test):
- Guarantees a deterministic iteration order → **PRESERVE**: the sequenced backbone (`List`,
  `Deque`, `LinkedHashSet`, `LinkedHashMap`, `SortedSet`/`TreeSet`, `SortedMap`/`TreeMap`),
  plus `EnumSet`/`EnumMap` (ordinal), the concurrent *sorted* `ConcurrentSkipListSet`/`Map`
  (comparator order is element-derived, deterministic even when concurrent), and
  `CopyOnWriteArrayList` (a `List` — order is its value). Arrays incl. `byte[]` → always
  PRESERVE.
- Iteration order non-deterministic / unspecified → **CANONICALIZE** by §11.6 octet-sort:
  the hash types (`HashSet`, `HashMap`, `ConcurrentHashMap`), the concurrent insertion/FIFO
  types (`CopyOnWriteArraySet`, `ConcurrentLinkedQueue`/`Deque`, the blocking queues/deques,
  `LinkedTransferQueue`, `DelayQueue`, `SynchronousQueue`), and the priority queues
  (`PriorityQueue`, `PriorityBlockingQueue`). This is where option (a)'s determinism is
  applied, scoped to the genuinely non-deterministic.
- *Pro:* the discriminator is a **type contract**, not an erased concrete class (avoids (b)'s
  round-trip hazard) and not an extra schema token (avoids (c)'s surface). It is
  receiver-agnostic and honours the author's chosen type. Byte-determinism holds for both
  arms: preserved-order for deterministic-order types (order transmitted as data), octet-sort
  for the rest.

### 3.2 Recommendation (as decided)

**The rule is the deterministic-iteration-order discriminator (option d):** a type that
guarantees a deterministic iteration order is preserved exactly; a type whose order is
non-deterministic or unspecified is canonicalised by §11.6 octet-sort; arrays are always
preserved. No hashCode is ever consulted (§3.0). No per-field opt-in token is needed — the
type contract is the signal.

One-line rationale: **preserve the order of anything whose type guarantees a deterministic
iteration order (the sequenced backbone plus element-derived and positional orders — even
concurrent, where the order is comparator- or position-derived), canonicalise by encoding
only the non-deterministic (hash order, concurrent insertion/FIFO order, or iterators that
disclaim order) — never using any hashCode.** This takes (a)'s determinism *for the
non-deterministic case only*, rejects (b)'s concrete-class binding in favour of the type
contract, and needs none of (c)'s extra schema surface.

Consequences, stated (see §3a for the full treatment):
- *Preserve (deterministic order):* order is part of the serialized identity → serialized
  byte-equality is order-sensitive. For **insertion-ordered** `Set`/`Map`
  (`LinkedHashSet`/`LinkedHashMap`) this makes wire byte-equality *stricter* than object
  `.equals`; for **element-derived-order** types (`SortedSet`/`SortedMap`, `EnumSet`/`EnumMap`,
  `ConcurrentSkipListSet`/`Map`) and **positional** types (`List`, arrays) byte-equality and
  `.equals` coincide. A field wanting order-independent wire equality must be declared as a
  canonicalise (`HashSet`/`HashMap`) `Set`/`Map`.
- *Canonicalize (non-deterministic order):* byte-determinism holds unconditionally; two
  `.equals` `HashSet`s produce identical octets.
- *Concrete-class keying (b):* rejected — receiver may reconstruct a different concrete type
  and break round-trip; the type contract (deterministic-order guarantee) is the
  receiver-stable property.

---

## 4. Q4 — Prior art

| Scheme | Unordered-collection rule | What we take / the pitfall |
|---|---|---|
| **DER `SET OF`, X.690 §11.6** | Component **encodings** sorted ascending as octet strings; shorter padded with trailing 0x00 *for comparison only*. (§11.5 SET sorts by tag — different case.) | **This is our rule.** Sorting *encodings* (not decoded values) is the key virtue: machine-independent, no type knowledge needed, composes recursively. |
| **Canonical/deterministic CBOR, RFC 8949 §4.2.1** | Map keys sorted in **bytewise lexicographic order of their deterministic encodings**. | Same principle as §11.6 (sort by encoding). **Pitfall (cautionary):** RFC 7049 "Canonical CBOR" originally sorted **length-first** (shorter key sorts earlier), then RFC 8949 changed the *core* rule to **bytewise** and kept length-first only as a named compatibility profile. A bytewise-vs-length-first mismatch silently produces different bytes for the same map → two "canonical" encoders disagree. **Lesson: pin the exact comparator in normative text; do not say "sorted" and leave it.** §11.6's padded-octet comparison is well-pinned; cite it verbatim. |
| **JSON Canonicalization Scheme, RFC 8785** | Object members sorted by property-name **UTF-16 code units** (value-level string sort). | **Cautionary contrast:** JCS sorts on the *decoded* key representation (UTF-16 code units of the string), which forces every party to agree on a string-normalization/representation model and only works because JSON keys are strings. Sorting *encodings* (DER/CBOR) sidesteps this — it needs no per-type comparator and works for keys of any type. Confirms our choice to sort **encodings, not values**. |
| **Protobuf** | **NOT deterministic for `map` by spec.** The "deterministic" serialization option sorts map keys but is explicitly *not* guaranteed canonical across languages/versions; the wire is unordered and the spec warns against relying on byte-stability. | **Negative example / cautionary.** Protobuf is what STD-006 must *not* be: "mostly deterministic in one implementation" is a trap under signatures. Reinforces making the rule normative and conformance-tested, not implementation-incidental. |
| **XML C14N** | Attributes sorted by (namespace URI, local name); namespace nodes before attributes. | Attribute-set canonicalization by a defined key order — same family. Pitfall: C14N's complexity (namespace inheritance) is a warning to keep our rule *narrow* (octet-sort of element encodings, nothing more). |
| **Git tree objects** | Tree entries sorted by **byte order of the name** (with a subtlety: directories sort as if they had a trailing `/`). | Content-addressing depends on a fixed sort; the trailing-`/` subtlety is a famous "same logical tree, two hashes" bug class. Lesson: **the sort key must be exactly, unambiguously specified** — the same lesson as CBOR's length-first/bytewise split. |

**Common pattern extracted:** every robust canonical serialization for unordered
aggregates **sorts by a fully-pinned comparison over a stable byte representation**, and the
ones that sort by *encoding* (DER §11.6, CBOR §4.2.1) are simpler and type-agnostic than the
ones that sort by *decoded value* (JCS UTF-16, Git names). The recurring failure is an
under-specified comparator (CBOR length-first vs bytewise; Git trailing slash). **Adopt
§11.6 by exact citation** and conformance-test it.

---

## 5. Q5 — Where it bites in STD-006, and urgency scoping

**Codebase finding (verified in trunk `jgdms-der`):** the built codec has **no
Set/Map/Collection wire type at all**. `WireTypes` decodes only: `boolean`/boxed-integer/
`String`/`byte[]`/`float`/`double`/`char`, `enum:<class>`, `array:<component>` (order-
preserving `SEQUENCE`), and nested `@AtomicSerial` (`array:@AtomicSerial:<class>` via
`ObjectCodec`). Grep for `set:`/`map:`/`collection:`/`SET OF`/`sort` across
`jgdms-der/src/main` returns nothing collection-related (the one `sort` is a registry
priority sort in `stream\DerDecodeUnit`, unrelated). `CollectionField`/`MapField` exist
**only in the spec (§7.6)**, not in code. `Tag.java` is generic enough to express SET-OF
(0x31) but no code emits it.

**So the exposure is LATENT, not present.** Concretely:

- **(a) Signed structures.** The signed types that could carry a plain-Set/Map field — SCAP
  verdicts (§7.4), multicast TBS (§7.7.7) — are **spec-only / unbuilt** (§7.4 is `[OPEN]`;
  §7.7.7 Jini discovery is spec-only). The *live* signed path today is the identity/ACC
  transport (§7.2 `RemoteContextCodec`), whose reducing-domain set is an **order-significant
  `SEQUENCE`** of codebase records (§3.8 / §9.10 — explicitly preserved, never sorted) — a
  deterministic-order (preserve) shape, not a non-deterministic collection. So no *live*
  signed structure currently octet-order-depends on a non-deterministic collection.
- **(b) Entry byte-matching.** `EntryRecord`/`EntryTemplate` (§7.7.2) is where it would bite
  hardest — a plain-Set/Map-valued Entry field matched by byte equality. But the Jini §7.7
  types are **spec-only, unbuilt** (open item 10 even asks whether array-valued Entry fields
  are permitted yet). An `@SerialEntry` with a plain `Set`/`Map` field, once §7.7 is
  implemented, would silently mis-match unless the canonical (octet-sort) rule is in place
  first; a preserve-side field would carry order-sensitive bytes (§3a) that the author must
  understand.

**Urgency verdict:** *Design-now, build-later.* This is **not** a fire — nothing on trunk
today encodes any `Set`/`Map` into a signature or an Entry field, because the collection
wire type is unbuilt (there is no `set:`/`map:` token in `WireTypes` — only
`enum:`/`array:`/scalar/nested). But it is a **must-fix-before-build**: the moment anyone
adds a `set:`/`map:` wire type to `WireTypes`/`SchemaGenerator`, or implements
§7.4/§7.7.2/§7.7.7, the **deterministic-order discriminator rule and the §3a equality
consequence must already be normative**, or the first `Set`-valued signed field / Entry field
ships a latent nondeterminism bug (for a non-deterministic type) or a misunderstood
order-sensitive-equality surprise (for a preserve-side type). The right action is to **settle the §3.8/§7.6 prose now**
(this memo + the spec edits it drives), so the codec is built correct the first time — while
leaving the actual `set:`/`map:` token *grammar* as design-for-later. It also gates the
"confirm array-valued Entry fields" open item (10) and the "complete substituted-type set"
open item (§7.6) — collections should not be added to the codec until the ordering
discipline is normative.

---

## 6. Q6 — Recommendation, decision matrix, and proposed spec wording

### 6.1 Decision matrix (collection → discipline) — FINAL (determinism discriminator)

Discriminator: **does the declared type guarantee a DETERMINISTIC iteration order?** Yes →
PRESERVE; non-deterministic / unspecified → CANONICALIZE. The `Sequenced*` interfaces are the
preserve backbone, but concurrency can defeat determinism for insertion/FIFO-history types
(the concurrent **deques** — `ConcurrentLinkedDeque`, `LinkedBlockingDeque` — canonicalise
despite being `SequencedCollection` via `Deque`; `CopyOnWriteArraySet` canonicalises simply
as a plain, non-deterministic `Set`). No hashCode ever.

| Type | Deterministic order? | Discipline | Wire treatment / §3a note | hashCode used? |
|---|---|---|---|---|
| `HashSet` | no (hash-bucket) | **canonicalize** | §11.6 octet-sort of element encodings | **NO** |
| `HashMap` | no (hash-bucket) | **canonicalize** | `SEQUENCE OF {key,value}` sorted by §11.6 (keys unique → total order) | NO |
| `ConcurrentHashMap` (and its key-set view) | no (hash-bucket, resize-dependent) | **canonicalize** | §11.6 octet-sort over entry/element encodings | NO |
| `Properties` | no (Hashtable-based) | **canonicalize** | §11.6 octet-sort of `{key,value}` (String) entries | NO |
| `CopyOnWriteArraySet` | **no — concurrent insertion order is race-dependent** | **canonicalize** | §11.6 octet-sort. **Reverses naive preserve** — a *concurrent* insertion-history set; contrast `LinkedHashSet` (non-concurrent → preserve) | NO |
| `ConcurrentLinkedQueue`, `ConcurrentLinkedDeque` | no — concurrent FIFO/insertion, race-dependent | **canonicalize as multiset** | §11.6 octet-sort; dups retained. (Deque split: `ArrayDeque` preserves, `ConcurrentLinkedDeque` canonicalises) | NO |
| `ArrayBlockingQueue`, `LinkedBlockingQueue`, `LinkedBlockingDeque`, `LinkedTransferQueue`, `DelayQueue` | no — concurrent FIFO/insertion, race-dependent | **canonicalize as multiset** | §11.6 octet-sort; dups retained (`DelayQueue` iterator ≠ delay order) | NO |
| `SynchronousQueue` | n/a — holds no elements | **canonicalize (trivially empty)** | serialises as empty collection; listed for completeness | NO |
| `PriorityQueue`, `PriorityBlockingQueue` | no — iterator disclaims order (heap layout) | **canonicalize as multiset** (keep dups, drop heap order) | §11.6 octet-sort; dups retained; priority order rebuilt at receiver | NO |
| `LinkedHashSet` | yes — non-concurrent insertion order | **PRESERVE** | order-significant `SEQUENCE OF`, insertion order as-given — *§3a tension: byte-equality stricter than `.equals`* | NO |
| `LinkedHashMap` | yes — non-concurrent insertion order | **PRESERVE** | order-significant `SEQUENCE OF {key,value}`, insertion order — *§3a tension applies* | NO |
| `TreeSet`, `ConcurrentSkipListSet` | yes — comparator order (element-derived, deterministic even when concurrent) | **PRESERVE** | order-significant `SEQUENCE OF`, comparator order — *§3a: no tension* | NO |
| `TreeMap`, `ConcurrentSkipListMap` | yes — key comparator order (element-derived, deterministic even when concurrent) | **PRESERVE** | order-significant `SEQUENCE OF {key,value}`, comparator order — *§3a: no tension* | NO |
| `EnumSet`, `EnumMap` | yes — ordinal order (element-derived) | **PRESERVE** | order-significant `SEQUENCE OF`, ordinal order — *§3a: no tension* | NO |
| `ArrayList`, `LinkedList`, `Vector`, `CopyOnWriteArrayList` (as List) | yes — positional (order **is** the value; `CopyOnWriteArrayList` preserves despite concurrency) | **preserve** | order-significant `SEQUENCE OF` (§3.8) — *§3a: no tension (`List.equals` order-sensitive)* | NO |
| `ArrayDeque`, `LinkedList` (as Queue/Deque) | yes — non-concurrent head→tail order | **preserve** | order-significant `SEQUENCE OF` | NO |
| arrays incl. `byte[]`, principal chains, cert paths, stack traces | yes — positional | **preserve** | order-significant `SEQUENCE OF` (already in §3.8/§9); order **is** the value | NO |
| **Any ordering derived from `hashCode`/`identityHashCode`/bucket order** | — | **FORBIDDEN** | — | reason 1: identity hash = per-run PRNG header seed (mode 5), reproducible nowhere; reason 2: value-hashCode still gives bucket order, not canonical iteration |

### 6.2 Normative wording — inserted into §3.8 (as decided)

The normative prose actually adopted is the §3.8 subsection **"Encounter order and
serialized equality"** in `JGDMS-STD-006-v0.13-DRAFT`. It states the **deterministic-order
discriminator** (deterministic iteration order → preserve; non-deterministic → octet-sort),
names arrays incl. `byte[]` as always-preserved, and carries the §3a equality consequence:
serialized byte-equality is order-sensitive for preserve-side types, and — because
`LinkedHashSet`/`LinkedHashMap` inherit the order-independent `Set`/`Map` `.equals` — is
*stricter* than their in-memory `.equals` for those two insertion-ordered types. See that
subsection for the governing text; the older draft wording that appeared here (a
"canonicalize-by-default + `ordered` opt-in" formulation) is **[SUPERSEDED — see revision
note]** and is not the adopted rule.

Key points the adopted wording pins (unchanged from the earlier analysis, re-scoped to the
canonicalise side):

> **Non-deterministic-order Set/Map canonical order.** Where a field's declared type does not
> guarantee a deterministic iteration order (hash order — `HashSet`/`HashMap`/
> `ConcurrentHashMap`; concurrent insertion/FIFO order — `CopyOnWriteArraySet`, the
> concurrent queues/deques; or an iterator that disclaims order —
> `PriorityQueue`/`PriorityBlockingQueue`), the encoder MUST emit its elements in the DER
> `SET OF` canonical order of X.690 §11.6: each element is first encoded to its own canonical
> DER (recursively, bottom-up), and the element encodings are then placed in ascending order
> compared as octet strings, the shorter encoding notionally padded at its trailing end with
> 0x00 octets for the comparison only (padding never transmitted). A `Map` is `SEQUENCE OF
> SEQUENCE { key, value }` ordered by this rule over the entry encodings; distinct keys have
> distinct canonical DER encodings, so the order is total. A null element/value is carried via
> the field's `CHOICE { absent NULL, present ... }` form (as §7.7.2) and orders naturally.
>
> This canonical order is a **pure function of the element values**. An encoder MUST NOT
> derive collection order from `Object.hashCode()`, `System.identityHashCode()`, hash-bucket
> iteration order, or any other run- or machine-dependent quantity (§3.0).
>
> **Deterministic-order types are preserved, never octet-sorted.** Where the declared type
> guarantees a deterministic iteration order — the sequenced backbone (`List`, `Deque`,
> `LinkedHashSet`, `LinkedHashMap`, `SortedSet`/`TreeSet`, `SortedMap`/`TreeMap`), plus
> `EnumSet`/`EnumMap`, the concurrent sorted `ConcurrentSkipListSet`/`ConcurrentSkipListMap`,
> and `CopyOnWriteArrayList` — its iteration order is part of the value and is preserved as an
> order-significant `SEQUENCE OF`; the encoder MUST NOT reorder it and the decoder MUST NOT
> deduplicate or re-canonicalise it. Arrays — including `byte[]` — and the intrinsic sequences
> of §3.8 (principal chains, certificate paths, stack traces) are likewise always preserved.

### 6.3 §7.6 `CollectionField`/`MapField` — build-later token grammar (design-now/build-later)

The actual `set:`/`map:` wire-type **token grammar** is left as **design-for-later**: the
built codec (`WireTypes`) has no `set:`/`map:` token today (only `enum:`/`array:`/scalar/
nested), so nothing ships nondeterministic now. When the token is introduced, it need only
distinguish the two disciplines the §3.8 rule already fixes — a canonicalise form and a
preserve form — for example:

> ```asn1
> -- non-deterministic-order Set/Map: elements emitted in X.690 SET OF octet order (S3.8 / S11.6):
> CollectionField ::= SEQUENCE SIZE(0..maxCollection) OF Element          -- octet-sorted
> MapField        ::= SEQUENCE SIZE(0..maxCollection) OF SEQUENCE { key Element, value Element }
>                     -- entries octet-sorted by key encoding (keys unique -> total order)
>
> -- deterministic-order Set/Map/Collection, or intrinsic array/List/deque: order preserved (S3.8):
> OrderedCollectionField ::= SEQUENCE SIZE(0..maxCollection) OF Element   -- iteration order preserved
> ```
>
> Both share the `SEQUENCE OF` tag; they differ only in the encoder's ordering obligation,
> which the declared type's `Sequenced*` contract fixes (and the schema digest therefore
> covers). A decoder need not distinguish them structurally — it re-imposes the object's own
> ordering/uniqueness at construction (§3.8) — but the *encoder* MUST honour the discipline so
> signed and Entry-matched fields are byte-deterministic. **How** the discipline is carried in
> the token grammar (a `set:`/`orderedset:` split, a flag, or derivation from the field's
> declared Java type at schema-generation time) is the build-later design question; the
> *rule it must implement* is settled here and in §3.8.

### 6.4 Conformance-test sketch (add to §9)

1. **Plain `HashSet` cross-machine / cross-insertion determinism (the acceptance criterion).**
   Build `HashSet` H1 by inserting {a,b,c} in order a,b,c and `HashSet` H2 by inserting
   c,a,b; encode each as a canonical (octet-sorted) collection. **Assert byte-identical
   output.** Repeat forcing different initial capacities (`new HashSet<>(2)` vs
   `new HashSet<>(64)`) → still byte-identical. Run on two JVM builds / two machines (or under
   `-XX:hashCode=0`, `=2`, `=5` for elements without value-hashCode) → **all identical.**
   Machine/run-independence acceptance criterion for the *plain* case.
2. **Plain `HashMap` key-sort determinism.** Two `HashMap`s with the same entries in different
   insertion order and capacities → byte-identical. Include a `null` value and a `null` key →
   still identical, null sorts first.
3. **Recursion.** A `Set<Map<String,Integer>>` and a `Map<String,Set<Integer>>` (canonicalise
   variants) built two ways each → byte-identical (proves bottom-up canonicalization); a
   nested *preserve-side* element is encoded in its own preserved order first, then
   octet-sorted as an opaque encoding by the outer canonicalise collection.
4. **Opaque-octet elements.** A plain `Set<X509Certificate>` (or `Set<X500Principal>`)
   carrying verbatim `getEncoded()` octets, built in two insertion orders → byte-identical,
   inner certificate octets unchanged (proves §3.8 opaque carve-out and §11.6 sort coexist).
5. **Priority-queue multiset.** Two `PriorityQueue`s (and, separately, two
   `PriorityBlockingQueue`s) with the same multiset (including a duplicate) and different
   comparators/insertion → byte-identical canonical output; duplicate retained; heap order
   irrelevant. (Both canonicalise — the iterator disclaims order.)
6. **Concurrent insertion/FIFO canonicalise (the reversal case).** A `CopyOnWriteArraySet`
   (a plain `Set`, **not** `SequencedSet`) built by inserting a,b,c vs c,a,b → **byte-identical**
   (proves it canonicalises, *not* preserves — a `Set`'s order is not its value and the
   concurrent insertion order is non-deterministic). Likewise a `ConcurrentLinkedQueue`/
   `ConcurrentLinkedDeque` and a `LinkedBlockingQueue` with the same multiset in different
   insertion orders → byte-identical. Contrast test 8 (`LinkedHashSet` preserves).
7. **Deterministic-order preserve — positive.** A `LinkedHashSet` inserted a,b,c, a `TreeSet`
   of {a,b,c}, a `ConcurrentSkipListSet` of {a,b,c}, an `EnumSet`, and a `CopyOnWriteArrayList`
   [a,b,c] → each encodes with its **iteration order preserved** (insertion / ascending /
   ascending / ordinal / positional respectively); the encoder does **not** octet-sort them.
8. **Insertion-ordered preserve — negative determinism (the §3a mismatch).** A `LinkedHashSet`
   inserted a,b,c vs one inserted c,b,a — **`.equals` but DIFFERENT bytes** (proves iteration
   order is honoured and byte-equality is stricter than `.equals` for an insertion-ordered
   type, §3a).
9. **Element-derived-order preserve — no §3a mismatch.** Two `TreeSet`s (and two
   `ConcurrentSkipListSet`s, and two `EnumSet`s) with the same elements built in different
   insertion orders → **byte-identical** (proves that for an element-derived-order type the
   order is a function of elements+comparator/ordinal, so byte-equality and `.equals`
   coincide, §3a).
10. **Duplicate rejection for canonicalised set-typed fields.** A hand-crafted canonicalise
   set field containing two identical element encodings → decoder **rejects** (fail-secure).
11. **No-hashCode guard (static).** A lint/architecture test asserting the collection encoder
   references no `hashCode`/`identityHashCode`/bucket-iteration API — only element-encoding
   comparison for the canonicalise case and faithful iteration for the preserve case.
   (Optional but cheap; encodes the steer as a build gate.)

---

## 7. Open questions for Peter (owner calls)

**RESOLVED by the owner decision (2026-07-04):**

1. ~~**LinkedHashSet/LinkedHashMap default.**~~ **RESOLVED — PRESERVE.** The discriminator is
   whether the type guarantees a deterministic iteration order; `LinkedHashSet`/`LinkedHashMap`
   have a non-concurrent, app-controlled insertion order that is deterministic, so it is
   preserved. Keying on the *type contract* (not the erased concrete class) avoids the old (b)
   hazard. The equality consequence is documented, not resolved-away, in §3a.

5. ~~**`TreeSet`/`TreeMap` with a *custom* comparator.**~~ **RESOLVED — PRESERVE.** Their
   comparator order is exposed through the iterator as a deterministic, element-derived order,
   so the sender's order **is** preserved on the wire. Because that order is a function of
   elements+comparator, it is reproducible and coincides with `.equals` (no §3a mismatch). The
   same holds for the concurrent sorted `ConcurrentSkipListSet`/`ConcurrentSkipListMap`. No
   opt-in needed.

**Still open — deferred to build-later (design-now/build-later scope):**

2. **Schema token surface.** *(Build-later.)* When the `set:`/`map:` wire-type token is
   introduced, how is the preserve-vs-canonicalise discipline carried — a `set:`/`orderedset:`
   token split, a boolean flag, or derivation from the field's declared Java type at
   schema-generation time (since the type's deterministic-order guarantee already determines
   it)? The rule the token must implement is settled (§3.8); the grammar is not yet built.

3. **Wire tag: keep `SEQUENCE` (0x30) or emit `SET OF` (0x31) for the canonicalise case?**
   *(Build-later.)* Recommend **keep 0x30** with the octet-sort discipline (consistent with
   the built codec, which has no `SET OF` emitter), but this touches interop with off-the-shelf
   ASN.1 decoders and can be revisited when the token lands.

4. **Duplicate-in-canonicalised-set policy.** Recommend **reject** duplicate element encodings
   in a canonicalised set field (fail-secure, principle 6); silent collapse is the alternative.
   (A preserve-side type carries whatever its contract permits — a `List`/multiset may hold
   duplicates; a `LinkedHashSet` cannot by construction.) Confirm at build time.

6. **Interaction with open item 10 (array-valued Entry fields).** Adding `Set`/`Map` Entry
   fields (§7.7.2) should be gated behind this rule. Confirm collections stay out-of-scope for
   the codec until the §3.8 wording lands, so the first canonicalise-collection-valued Entry
   field is built canonical and the first preserve-side one is built order-preserving with §3a
   understood.
