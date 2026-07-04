# Design memo: canonical ordering of behavioural Set/Map/Collection fields in JGDMS-STD-006 (DER)

**Status:** design research, READ-ONLY. No spec or code changed.
**Author:** research pass, 2026-07-04.
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

STD-006 today encodes every behavioural collection (`Set`/`Map`/`List`) as an
order-**preserving** DER `SEQUENCE OF` (§3.8, §7.6 `CollectionField`/`MapField`), with the
rule "order is neither asserted nor relied upon on the wire." That rule is correct for
*decode semantics* (the receiver re-imposes ordering) but it does **nothing for byte
determinism on encode**. A `HashSet` of the same three logical elements, built by two
different insertion orders — or on two JVMs — iterates in different orders, so `SEQUENCE
OF` in iteration order yields different octets. Inside a signature or an Entry field value,
that is a correctness bug: the same logical set fails to verify / fails to match itself.

`SEQUENCE OF` preserving order is *right* for intrinsic-order sequences (arrays, chains,
stack traces) and *insufficient* for behavioural collections. The gap is the whole subject
of this memo.

---

## 1. Q1 — Taxonomy of JDK collection types

Classification key:
- **INTRINSIC** — iteration order is part of the value; the receiver relies on it. Preserve
  wire order (order-significant `SEQUENCE OF`, §3.8 carve-out). Reordering corrupts.
- **BEHAVIOURAL** — iteration order is imposed by the receiver's *code* (hash layout, a
  comparator, insertion policy). The value is the *multiset/keyset of elements*, not their
  order. Canonicalize by encoding (Q2).
- **AMBIGUOUS** — could be read either way; needs an owner call (§ open questions).

The genuinely load-bearing distinction is: **is the order a function of the wire *values*,
or a function of the receiver's *behaviour*?** Only the former can be intrinsic.

| Type | Iteration order source | Class | Why |
|---|---|---|---|
| **Set** | | | |
| `HashSet` | bucket order (table capacity + spread(hash) + insertion/resize history) | **BEHAVIOURAL** | not a value function; unstable across insertion order, capacity, JVM. Canonicalize. |
| `LinkedHashSet` | insertion order (or access order) | **AMBIGUOUS → treat BEHAVIOURAL by default** | insertion order *is* the point to the author, but it is not intrinsic to the *set value* {a,b,c}. See §1a — this is the hard case; default canonicalize, opt-in preserve. |
| `TreeSet` | comparator / natural order | **BEHAVIOURAL** | order comes from a `Comparator`/`Comparable` — *code*, not wire data (§3.8). Receiver re-sorts with ITS comparator. Wire is just the elements. Canonicalize by encoding. |
| `EnumSet` | enum `ordinal()` ascending | **BEHAVIOURAL, already-deterministic** | order = declaration order of the enum, a value function → already canonical *if* elements encode by name/ordinal. Canonicalize is a no-op that also protects against a non-EnumSet carrier of the same elements. |
| `CopyOnWriteArraySet` | insertion order (backing array) | **AMBIGUOUS → BEHAVIOURAL by default** | same shape as `LinkedHashSet`: insertion-ordered but semantically a set. |
| `ConcurrentSkipListSet` | comparator / natural order | **BEHAVIOURAL** | same as `TreeSet` — order is a comparator (code). Canonicalize. |
| **Map** (each entry = `{key,value}`) | | | |
| `HashMap` | bucket order of keys | **BEHAVIOURAL** | as `HashSet`; canonicalize entries by encoded key. |
| `LinkedHashMap` | insertion (or access) order | **AMBIGUOUS → BEHAVIOURAL by default** | as `LinkedHashSet`. Note: access-order LHM (LRU cache) iteration is *runtime-state*, never a transmissible value — must canonicalize. |
| `TreeMap` | key comparator / natural | **BEHAVIOURAL** | key order is code; receiver re-sorts. Canonicalize by encoded key. |
| `EnumMap` | key `ordinal()` | **BEHAVIOURAL, already-deterministic** | as `EnumSet`. |
| `ConcurrentHashMap` | bucket order, *and* concurrent-resize-dependent | **BEHAVIOURAL** | even less stable than `HashMap` (segment/resize races); canonicalize. |
| `Properties` | inherited from `Hashtable` bucket order | **BEHAVIOURAL** | §7.6 already tags it "behavioural; unordered." Canonicalize entries by encoded key (String). |
| **List** | | | |
| `ArrayList` | index order | **INTRINSIC** | a List's contract is positional; index *is* the value. Preserve. |
| `LinkedList` (as List) | index order | **INTRINSIC** | positional; §3.8 names "linked lists" in the carve-out. Preserve. |
| `CopyOnWriteArrayList` | index order | **INTRINSIC** | positional List. Preserve. |
| `Vector` | index order | **INTRINSIC** | positional List. Preserve. |
| **Queue / Deque** | | | |
| `ArrayDeque` | head→tail order | **INTRINSIC** | a deque's order is its whole contract (FIFO/LIFO position). Preserve. |
| `LinkedList` (as Queue/Deque) | head→tail | **INTRINSIC** | positional/sequence. Preserve. |
| `PriorityQueue` | **heap array order — NOT sorted order** | **AMBIGUOUS → see §1b** | the hard one. Iteration order is the internal binary-heap layout, which depends on insertion order (not a value function and not the priority order). The *value* is a multiset + a comparator. |

### 1a. LinkedHashSet / LinkedHashMap — the insertion-order case

Is insertion order intrinsic (§3.8's linked-list carve-out) or behavioural?

- §3.8's carve-out names "linked lists ... any sequence whose indices are semantically
  distinct." A `LinkedList` used as a `List` qualifies: position *i* is addressable and
  meaningful. A `LinkedHashSet` is **not** that: it is a `Set` (no positional contract, no
  `get(i)`, `equals` ignores order — `new LinkedHashSet<>([a,b]).equals(new LinkedHashSet<>([b,a]))`
  is `true`). Insertion order is *incidental history*, not part of the set value.
- Therefore, by the letter of §3.8, LinkedHashSet/Map are **behavioural**: two sets that
  are `.equals()` must not encode differently, and they only differ by insertion order.
- **But** an author sometimes chooses `LinkedHashSet` *because* they want a stable,
  meaningful iteration order to survive (e.g. an ordered option list, a display sequence).
  Under a blanket canonicalize rule that intent is silently dropped. This is the real
  design tension and the reason Q3 exists. **Default: canonicalize; preserve only on an
  explicit per-field opt-in** (Q3 recommendation). Rationale: byte-determinism under
  signature/Entry-match is a *correctness* property that must hold by default; insertion-
  order preservation is an *author convenience* that must be opted into, because erasure
  hides `LinkedHashSet` from the field contract (a `Set<X>` field could hold either).

### 1b. PriorityQueue — multiset + comparator, iteration ≠ sort

`PriorityQueue.iterator()` returns elements in **heap-array order, not priority order** —
and the heap array's layout is a function of the insertion/sift history, so two PQs holding
the same elements with the same comparator can iterate differently. So:

- Preserving iteration order is *wrong* (non-deterministic, and not even the useful order).
- The transmissible *value* is a **multiset of elements** (+ a comparator that is *code*,
  reconstructed at the receiver, not wire data — exactly like TreeSet's comparator).
- Therefore **BEHAVIOURAL, canonicalize by encoding as a multiset** (duplicates allowed —
  a PQ may hold equal-priority duplicates; do NOT dedupe). This drops the heap layout, which
  carries no value. The receiver rebuilds the heap with its comparator.

Verdict: PriorityQueue → canonicalize-as-multiset. Never preserve heap order.

---

## 2. Q2 — The canonical rule for behavioural collections

**Rule (DER SET OF octet-sort, X.690 §11.6):**

> Encode each element to its canonical DER (recursively — an element that is itself a
> behavioural collection is canonicalized first, bottom-up). Then order the *element
> encodings* in **ascending order, compared as octet strings**, where — for the comparison
> only — the shorter encoding is conceptually padded at its trailing end with 0x00 octets;
> the padding is never emitted. Emit the elements in that order inside the `SEQUENCE OF`.

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
- A **List/multiset** (PriorityQueue-as-multiset, or a `List` that is being canonicalized —
  which should not happen; Lists are intrinsic) may legitimately hold duplicates; keep them.
  This is why the *field's declared discipline* (Q3), not the byte pattern, decides whether
  a duplicate is legal.

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

## 3. Q3 — The erasure / signal problem (THE decision), and why no hashCode can be used

A field declared `Set<X>` (or `Map<K,V>`) gives the encoder **no static signal** whether
order matters: erasure means the field type cannot distinguish `HashSet` from
`LinkedHashSet`. The encoder must decide *preserve vs canonicalize* somehow.

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

### 3.1 The three options

**(a) Canonicalize ALL Set/Map by encoding (uniform).**
Every `Set`/`Map` field is octet-sorted (§11.6) regardless of concrete runtime type;
`List`/array/deque/queue-as-sequence stay order-preserving.
- *Pro:* always deterministic; zero dependence on erased runtime type; simplest to specify,
  implement, and conformance-test; a non-JVM party needs no type metadata, only "this field
  is a set/map." Signature and Entry-match determinism hold unconditionally.
- *Con:* silently drops `LinkedHashSet`/`LinkedHashMap`/`CopyOnWriteArraySet` insertion
  order. If an author *needed* that order to survive, it is lost with no diagnostic.

**(b) Switch on the runtime concrete type.**
`HashSet`→canonicalize, `LinkedHashSet`→preserve, `TreeSet`→canonicalize (or preserve),
`PriorityQueue`→canonicalize-as-multiset, etc.
- *Pro:* "does what the author's chosen class does."
- *Con — disqualifying:* the wire bytes now depend on a **runtime concrete type that
  erasure hides from the declared contract**. The schema says `Set<X>`; the bytes depend on
  whether the *instance* was a `HashSet` or `LinkedHashSet` — invisible to the schema, to a
  non-JVM party, and to the digest/signature contract. Worse, the **receiver may
  reconstruct a different concrete type** (a `Set<X>` field is typically rebuilt as, say, a
  `LinkedHashSet` or an unmodifiable wrapper regardless of what was sent), so a re-encode by
  the receiver would not round-trip the bytes — breaking the "same value → same bytes"
  invariant that signatures and Entry-match need. It also demands the non-JVM encoder know
  the JDK class taxonomy. Reject (b).

**(c) Explicit per-field declaration (`ordered` vs `unordered`) in the schema.**
The `@AtomicSerial` `serialForm()` / `SerialForm` field descriptor (which already carries a
`wireType` string — verified in `AtomicSerialFieldDef`/`WireTypes`, e.g. `"array:<...>"`,
`"enum:<...>"`) gains a collection discipline: e.g. `"set:<elementWireType>"` (unordered,
canonicalize) vs `"orderedset:<elementWireType>"` / `"list:<...>"` (order-significant,
preserve), and `"map:<k>:<v>"` (canonicalize by key) vs an ordered map variant.
- *Pro:* the discipline is **in the schema**, therefore in the digest, visible to every
  party including non-JVM, and independent of the erased runtime type. The author states
  intent once; it is stable and auditable. Byte-determinism is guaranteed *and* insertion-
  order preservation is available where genuinely wanted.
- *Con:* adds API/schema surface — one more thing an `@AtomicSerial` author declares, and
  the annotation processor / `SchemaGenerator` must emit the right discipline token.

### 3.2 Recommendation

**Adopt (a) as the default rule, with (c) as the explicit escape hatch.** Concretely:

- Default for any `Set`/`Map`/behavioural-`Collection` field: **canonicalize by §11.6
  octet-sort** (option a). This makes byte-determinism the *default* — the safe direction,
  since it is a correctness property under signatures and Entry-match.
- Provide an **explicit per-field opt-in** (option c) — an `ordered` collection wire-type
  token — for the genuinely order-significant behavioural case (an author who truly needs
  `LinkedHashSet` insertion order on the wire). Opting in makes it order-significant
  `SEQUENCE OF` (preserve), and the schema records that choice so the digest and every
  party agree.

One-line rationale: **canonicalize-by-encoding by default (only order that is machine/run-
independent — no hashCode anywhere), with an explicit schema opt-in to preserve order where
the author declares order is part of the value.** This is (a)+(c); it rejects (b) because (b)
binds wire bytes to an erased, receiver-variable runtime type.

Failure modes, stated:
- *(a) default:* author loses insertion order silently → mitigated by (c) opt-in and by a
  lint/annotation-processor warning when a field is *declared* `LinkedHashSet`/`Ordered*`
  but not marked ordered.
- *(b):* signature/Entry-match break when sender and receiver concrete types differ →
  reason to reject outright.
- *(c) opt-in:* an author who marks a set `ordered` but populates it from a `HashSet`
  reintroduces nondeterminism *for that field only* — but they asked for order-significance
  explicitly, so it is their declared contract (and a `HashSet` source with an `ordered`
  field is a lint-flaggable mismatch).

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

- **(a) Signed structures.** The signed types that could carry a Set/Map field — SCAP
  verdicts (§7.4), multicast TBS (§7.7.7) — are **spec-only / unbuilt** (§7.4 is `[OPEN]`;
  §7.7.7 Jini discovery is spec-only). The *live* signed path today is the identity/ACC
  transport (§7.2 `RemoteContextCodec`), whose reducing-domain set is an **order-significant
  `SEQUENCE`** of codebase records (§3.8 / §9.10 — explicitly preserved, never sorted), not
  a behavioural collection. So no *live* signed structure currently octet-order-depends on a
  behavioural collection.
- **(b) Entry byte-matching.** `EntryRecord`/`EntryTemplate` (§7.7.2) is where it would bite
  hardest — a Set/Map-valued Entry field matched by byte equality. But the Jini §7.7 types
  are **spec-only, unbuilt** (open item 10 even asks whether array-valued Entry fields are
  permitted yet). An `@SerialEntry` with a `Set`/`Map` field, once §7.7 is implemented,
  would silently mis-match unless the canonical rule is in place first.

**Urgency verdict:** *Design-now, implement-with-collections.* This is **not** a fire —
nothing on trunk today encodes a behavioural collection into a signature or an Entry field,
because the collection wire type is unbuilt. But it is a **must-fix-before-build**: the
moment anyone adds a `set:`/`map:` wire type to `WireTypes`/`SchemaGenerator`, or implements
§7.4/§7.7.2/§7.7.7, the rule must already be normative, or the first `Set`-valued signed
field / Entry field ships a latent nondeterminism bug. The right action is to **settle
§3.8/§7.6 wording and the schema token now** (this memo), so the codec is built correct the
first time. It also should gate the "confirm array-valued Entry fields" open item (10) and
the "complete substituted-type set" open item (§7.6) — collections should not be added to
the codec until the ordering discipline is normative.

---

## 6. Q6 — Recommendation, decision matrix, and proposed spec wording

### 6.1 Decision matrix (collection → discipline)

| Runtime type (field declared as its interface) | Discipline | Wire treatment | hashCode used? |
|---|---|---|---|
| `HashSet`, `ConcurrentHashMap`-keyset, `TreeSet`, `ConcurrentSkipListSet` | canonicalize | §11.6 octet-sort of element encodings | **NO** — sort by DER encoding |
| `EnumSet`, `EnumMap` | canonicalize (already-deterministic) | §11.6 octet-sort (no-op if elements pre-ordered by ordinal, but applied for uniformity/safety) | NO |
| `HashMap`, `TreeMap`, `ConcurrentHashMap`, `Properties` | canonicalize | `SEQUENCE OF {key,value}` sorted by §11.6 over entry encodings (key-determined; keys unique) | NO |
| `PriorityQueue` | canonicalize **as multiset** (keep dups, drop heap order) | §11.6 octet-sort; duplicates retained | NO |
| `LinkedHashSet`, `LinkedHashMap`, `CopyOnWriteArraySet` | **default canonicalize**; **preserve only if field marked `ordered`** (Q3c) | default §11.6 octet-sort; opt-in → order-significant `SEQUENCE OF` | NO |
| `ArrayList`, `LinkedList`, `Vector`, `CopyOnWriteArrayList` (as List) | preserve (INTRINSIC) | order-significant `SEQUENCE OF` (§3.8) | NO |
| `ArrayDeque`, `LinkedList` (as Queue/Deque) | preserve (INTRINSIC) | order-significant `SEQUENCE OF` | NO |
| arrays, principal chains, cert paths, stack traces | preserve (INTRINSIC, already in §3.8/§9) | order-significant `SEQUENCE OF` | NO |
| **Any ordering derived from `hashCode`/`identityHashCode`/bucket order** | **FORBIDDEN** | — | reason 1: identity hash = per-run PRNG header seed (mode 5), reproducible nowhere; reason 2: value-hashCode still gives bucket order, not canonical iteration |

### 6.2 Proposed normative wording — insert into §3.8

> **Behavioural-collection canonical order (NORMATIVE).** Where a field is a behavioural
> collection (a `Set`, `Map`, or other `Collection` whose iteration order is imposed by the
> receiver at construction and is not intrinsic to the value — §3.8), the encoder MUST emit
> its elements in the DER `SET OF` canonical order of X.690 §11.6: each element is first
> encoded to its own canonical DER (recursively, bottom-up, so that an element which is
> itself a behavioural collection is canonicalized before its containing collection is
> ordered), and the element encodings are then placed in ascending order compared as octet
> strings, the shorter encoding being notionally padded at its trailing end with 0x00 octets
> for the comparison only (the padding is never transmitted). A `Map` is encoded as
> `SEQUENCE OF SEQUENCE { key, value }` ordered by this rule applied to the entry encodings;
> because distinct keys have distinct canonical DER encodings, this order is total and
> unambiguous. A null element or null map value is carried via the field's `CHOICE { absent
> NULL, present ... }` form (as §7.7.2) and orders naturally under the octet comparison.
>
> This canonical order is a **pure function of the element values**. An encoder MUST NOT
> derive collection order from `Object.hashCode()`, `System.identityHashCode()`, hash-bucket
> iteration order, or any other run- or machine-dependent quantity. (Identity hash codes are,
> on common runtimes, a lazily-assigned per-object pseudo-random value in the object header
> that differs on every run; and even value-derived hash codes yield hash-*bucket* iteration
> order, not a canonical order.) Sorting the canonical element encodings is the only ordering
> that is identical across runtimes, machines, and insertion histories, which is required
> wherever the field is signature-covered (§7.4.1, §7.7.7) or matched by byte equality
> (§7.7.2).
>
> **Order-significant behavioural collections (opt-in).** Where an author declares that a
> behavioural collection's order is part of the value (e.g. a `LinkedHashSet` whose insertion
> order is significant), the field is marked order-significant in its schema (§7.6 collection
> wire-type token) and is then encoded as an order-**preserving** `SEQUENCE OF` per the
> intrinsic-order carve-out above; the encoder MUST NOT reorder it and the decoder MUST NOT
> deduplicate or re-canonicalize it. Absent that explicit marking, the canonical order above
> applies. Intrinsic sequences — arrays, `List`s, deques/queues, principal chains, certificate
> paths, stack traces — are always order-preserving and are never octet-sorted.

### 6.3 Proposed wording — amend §7.6 `CollectionField`/`MapField` block

> A behavioural collection field is one of two schema-declared disciplines, distinguished by
> its wire-type token (the `@AtomicSerial` `serialForm()` field descriptor, e.g.
> `set:<elementWireType>` / `map:<keyWireType>:<valueWireType>` for **canonical (unordered)**,
> and `orderedset:<...>` / `orderedmap:<...>` / `list:<...>` for **order-significant**):
>
> ```asn1
> -- behavioural collection, CANONICAL (Set/Map/unordered Collection):
> -- elements emitted in X.690 SET OF octet order (S3.8); receiver imposes uniqueness/order.
> CollectionField ::= SEQUENCE SIZE(0..maxCollection) OF Element          -- octet-sorted, S11.6
> MapField        ::= SEQUENCE SIZE(0..maxCollection) OF SEQUENCE { key Element, value Element }
>                     -- entries octet-sorted by key encoding (keys unique -> total order)
>
> -- behavioural collection, ORDER-SIGNIFICANT (opt-in) or intrinsic (array/List/deque):
> OrderedCollectionField ::= SEQUENCE SIZE(0..maxCollection) OF Element   -- order preserved (S3.8)
> ```
>
> The two share the `SEQUENCE OF` tag; they differ only in the encoder's ordering obligation,
> which the schema token fixes and the schema digest therefore covers. A decoder never needs
> to distinguish them structurally — it re-imposes the object's own ordering/uniqueness at
> construction (§3.8) — but the *encoder* MUST honour the declared discipline so that signed
> and Entry-matched fields are byte-deterministic.

### 6.4 Conformance-test sketch (add to §9)

1. **Cross-machine / cross-insertion set determinism (the acceptance criterion).**
   Build `HashSet` H1 by inserting {a,b,c} in order a,b,c and `HashSet` H2 by inserting
   c,a,b; encode each as a canonical `CollectionField`. **Assert byte-identical output.**
   Repeat forcing different initial capacities (e.g. `new HashSet<>(2)` vs `new HashSet<>(64)`)
   to exercise different bucket layouts → still byte-identical. Run the same fixture on two
   JVM builds / two machines (or under `-XX:hashCode=0`, `=2`, `=5` to simulate different
   identity-hash regimes for elements without value-hashCode) → **all outputs identical.**
   This is the machine/run-independence acceptance criterion.
2. **Map key-sort determinism.** Two `HashMap`s with the same entries in different insertion
   order and different capacities → byte-identical `MapField`. Include a `null` value and (for
   HashMap) a `null` key → still identical, null sorts first.
3. **Recursion.** A `Set<Map<String,Integer>>` and a `Map<String,Set<Integer>>` built two
   ways each → byte-identical (proves bottom-up canonicalization).
4. **Opaque-octet elements.** A `Set<X509Certificate>` (or `Set<X500Principal>`) carrying
   verbatim `getEncoded()` octets, built in two insertion orders → byte-identical, and the
   inner certificate octets are byte-for-byte unchanged (proves §3.8 opaque carve-out and
   §11.6 sort coexist).
5. **PriorityQueue multiset.** Two `PriorityQueue`s with the same multiset (including a
   duplicate) and different comparators/insertion → byte-identical canonical output;
   duplicate retained; heap order irrelevant.
6. **Order-significant opt-in (negative determinism).** A field marked `orderedset` populated
   from a `LinkedHashSet` inserted a,b,c vs c,b,a → **DIFFERENT** bytes (proves opt-in
   preserve is honoured and order IS significant when declared).
7. **Duplicate rejection for set-typed fields.** A hand-crafted `CollectionField` (set
   discipline) containing two identical element encodings → decoder **rejects** (fail-secure).
8. **No-hashCode guard (static).** A lint/architecture test asserting the collection encoder
   references no `hashCode`/`identityHashCode`/bucket-iteration API — only element-encoding
   comparison. (Optional but cheap; encodes the steer as a build gate.)

---

## 7. Open questions for Peter (owner calls)

1. **LinkedHashSet/LinkedHashMap default.** This memo recommends **default-canonicalize,
   opt-in-preserve** (§1a, §3.2). The alternative — default-preserve for
   `LinkedHash*`/`CopyOnWriteArraySet` — makes wire bytes depend on the erased runtime type
   (the (b) hazard) and is not recommended, but it is the one place a reasonable owner might
   disagree, because "I chose LinkedHashSet, I meant the order." Confirm the default.

2. **Schema token surface (Q3c mechanism).** Do you want the explicit ordered/unordered
   discipline expressed as **new wire-type tokens** (`set:` / `orderedset:` / `map:` /
   `orderedmap:`, extending the existing `array:`/`enum:` token grammar in
   `AtomicSerialFieldDef`), or as a **separate boolean flag** on the field descriptor
   (`ordered=true`)? Token grammar is consistent with what's built; a flag is smaller but
   splits the type across two schema attributes. Recommend tokens.

3. **Wire tag: keep `SEQUENCE` (0x30) with octet-sort discipline, or emit `SET OF` (0x31)?**
   X.690's §11.6 order is defined for the SET-OF tag. STD-006 currently gives *all*
   collections the `SEQUENCE OF` tag (0x30) and distinguishes discipline by schema token, not
   tag. Emitting a genuine `SET OF` (0x31) for canonical collections would be more
   self-describing to generic ASN.1 tooling, but it (a) reintroduces a structural distinction
   the decoder was told it "never needs" and (b) means a schema change for a field that flips
   discipline. Recommend **keep 0x30 + schema-token discipline** (consistent with the built
   codec and §7.6), but this is a defensible either-way call and touches interop with off-the-
   shelf ASN.1 decoders. Confirm.

4. **Duplicate-in-set policy.** Recommend **reject** duplicate element encodings in a
   set-disciplined field (fail-secure, principle 6). Alternative: silently collapse. Reject
   is safer (a duplicate signals a malformed/hostile encoding); confirm.

5. **`TreeSet`/`TreeMap` with a *custom* comparator.** The comparator is code (§3.8), not
   wire data, so the memo canonicalizes by encoding and lets the receiver re-sort with ITS
   comparator. Confirm there is no case where the *sender's* comparator order must be
   preserved on the wire (there should not be — that would be transmitting behaviour — but
   flag it because a `TreeSet` author might assume their order survives). If such a case
   exists, it is an `orderedset` opt-in, same mechanism.

6. **Interaction with open item 10 (array-valued Entry fields).** Adding `Set`/`Map` Entry
   fields (§7.7.2) should be gated behind this rule. Confirm collections are out-of-scope for
   the codec until §3.8/§7.6 wording lands, so the first collection-valued Entry field is
   built canonical.
