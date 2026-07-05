# Board guidance: rule-fidelity, determinism, and cross-language review

**Audience:** future board members reviewing a *new* rule, discriminator, wire-type, or
canonicalisation change against the settled STD-006 (DER) determinism/value story.
**Scope:** how to keep a growing design self-consistent — the quiet work of confirming a
new rule is a faithful sibling of the ones already decided, not a contradiction wearing
familiar words.
**Provenance:** distilled from the review chain that produced the 14-framework comparison
table, the JOSS-vs-DER value-equality property, and the rule-fidelity reviews of the
collection-ordering codec and the closed-subset type model. It cites specific docs, files,
and lines so you can re-derive every claim rather than trust it.

**The three anchor documents you must hold in your head before reviewing anything:**
- `docs/JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md` — the normative spec. Especially
  §3.7 (acyclic), §3.8 ("Values and Bounds, Never Behaviour" + "Encounter order and
  serialized equality" + the intrinsic-order and opaque-octet carve-outs), §7.6
  (substituted types; "Collections are NOT in this catalogue"), §7.8 (schema digests),
  §9 (conformance).
- `docs/der-collection-ordering-research.md` — the determinism memo (on trunk). §0/§0.1
  (why value-equality matters; DER restores the value/serial-equality correspondence JOSS
  breaks), §1 (the taxonomy + the discriminator), §2 (X.690 §11.6 octet-sort), §3a (the
  byte-equality-vs-`.equals` tension).
- `docs/der-type-model-and-element-rule.md` — the closed-subset type model + element
  rule (branch `der-type-model`). The sibling rule; read it as the worked example of
  "faithful sibling done right."

---

## 0. The one sentence to keep

**A new rule is faithful iff it keys on *the declared type as the carrier of intent*, puts
*structure* on the wire and *behaviour* in the constructor, and preserves the property that
*byte-equality mirrors the type's own `equals` contract*.** Everything below is how you
check those three things and catch the ways they quietly fail.

---

## 1. The recurring coherence hazards (what actually goes wrong)

These are the failure modes that recur. Each is a *tell* — a shape a proposal takes when it
is about to contradict something already settled.

### 1.1 A new rule silently contradicts a settled one

The determinism discriminator was **revised once already** and the revision reversed a naive
call — that history is the warning. The final discriminator is *"does the declared type
guarantee a **deterministic iteration order**?"* (memo §1), **not** the earlier
"canonicalize-by-default with per-field opt-in," and **not** the interim "does it implement
`Sequenced*`?" shortcut. Two cases do **not** follow from `Sequenced*` membership:
- `CopyOnWriteArraySet` **canonicalises** (a `Set`, order not part of value, *and* its
  concurrent insertion order is non-deterministic) — this **reverses** the naive
  "insertion-ordered → preserve" call. Contrast `CopyOnWriteArrayList` (same impl family,
  but a `List` → preserve).
- `ConcurrentSkipListSet`/`Map` **preserve** (comparator order is element-derived → deterministic
  *even under concurrency*).

**Hazard:** a new proposal re-imports the superseded framing ("let's default to preserve and
add an annotation to canonicalise", or "just check `Sequenced*`"). It *reads* plausible
because it uses the same vocabulary. **Tell:** the proposal cites the interface hierarchy or a
"value vs behaviour judgement call" instead of the determinism question. Reject or send back:
the settled discriminator is determinism of the *declared type*, full stop.

### 1.2 The discriminator drifts from "the declared type carries the intent"

Both settled rules key on the **declaration the developer already wrote**, never on a runtime
instance and never on a restated fact:
- Ordering: `disciplineFor(Class)` reads the declared collection *class*
  (`HashSet`→`set:`, `LinkedHashSet`→`orderedset:`, `TreeMap`→`orderedmap:`) —
  `CollectionWireTypes.java`, a pure function of the type.
- Element type: `rule(E)` reads the declared *generic signature* via
  `Field.getGenericType()` — type-model memo §3. Also a pure function of the declaration.

**Hazard 1 — reading the instance.** Any rule that sniffs `value.getClass()`, `hashCode()`,
`identityHashCode()`, or hash-bucket iteration order to decide *wire structure* has drifted.
The determinism guarantee **rests on the ordering never being a function of a hash** (memo §2;
enforced in the codec by a source-scanning test, `collectionCodec_referencesNoHashCode_sourceGuard`,
that fails if `CollectionWireTypes` so much as *calls* `hashCode`). `value.getClass()` at
*encode* for a polymorphic `@AtomicSerial` slot is fine — that's the value self-identifying by
digest, not the rule deciding structure.

**Hazard 2 — the annotation smell.** Peter's directive: **do not add an annotation that
restates a declared fact.** `@DerElement(Foo.class)` on a `Set<Foo>` field restates `Foo`, and
*any place a developer restates a fact is a place the two can drift and the annotation lies*
(type-model memo §0). **Tell:** a proposal adds an annotation whose argument is recoverable
from the declaration. Push back: derive it by rule. (The genuine exception — a type variable
`Set<T>` in a generic class — is exactly the case an annotation **also** cannot fix, because
there is no concrete type at the declaration site; it degrades to `Any` by rule. That's the
proof the annotation was never the answer.)

### 1.3 Value-vs-implementation confusion (the deepest one)

This is the headline property and the easiest to lose. **JOSS serialises the *implementation*;
DER serialises the *value*.** A `HashSet` and a `TreeSet` of the same elements are `.equals`
as `Set`s but serialise to **different** JOSS bytes (JOSS answers "same implementation and
layout?"). DER under the ordering rule octet-sorts the elements independent of implementation,
so two `.equals` sets produce **identical** bytes — serial-equality **coincides** with
value-equality (memo §0.1). That restoration is what makes DER bytes usable as a
*value-equality proxy* — the foundation under §7.7.2 Entry byte-matching, §7.8/§7.7.1
content-address digests, and signature stability.

**Hazard:** a proposal reintroduces implementation identity onto the wire — carrying a concrete
class name where a digest belongs, preserving an *implementation-derived* order (bucket order,
`identityHashCode`), or letting the *sender's* concrete type dictate the *receiver's* type.
**Tell:** ask "if I send this value from two different concrete implementations of the same
logical value, do I get the same bytes?" If the answer depends on the implementation, the
proposal has reintroduced the JOSS disease. (This is also why **receiver-chooses-implementation**
must survive every change: a sender's `HashMap` may be reconstructed at the receiver as a
`TreeMap` / `Map.copyOf` / `EnumMap` / domain type — the wire fixes the *value*, the constructor
picks the *implementation*.)

### 1.4 The byte-equality-vs-`.equals` tension (know exactly where it lives)

For **preserve** types, byte-equality is *order-sensitive*; for **canonicalise** types it
tracks `.equals`. The tension — serialized byte-equality **stricter** than object `.equals` —
lives in **exactly two types**: `LinkedHashSet` and `LinkedHashMap` (memo §3a). They inherit
the order-independent `Set`/`Map` `.equals` yet carry a non-rederivable insertion-history
order, so two `.equals` instances built in different insertion orders serialise to *different*
octets (and won't match under §7.7.2). This is **intended, not a bug** — it's correct for a
type whose insertion order is significant.

The other preserve types have **no** tension: element-derived order (`TreeSet`/`TreeMap`/
`EnumSet`/`ConcurrentSkipList*`) — two `.equals` instances have the same order by construction;
positional (`List`/array) — `.equals` is already order-sensitive.

**Hazard:** a proposal either (a) "fixes" the `LinkedHashSet` tension by canonicalising it —
wrong, that discards the insertion order the type exists to keep; or (b) *assumes* `.equals`
⇒ byte-equal for `LinkedHashSet`/`LinkedHashMap` in some matching or dedup path — wrong, and a
latent correctness bug. **Tell:** any claim of the form "equal objects always encode
identically" that doesn't carve out these two types. The correct guidance to a developer who
wants order-independent wire equality: **declare a plain (canonicalise) `Set`/`Map`.**

---

## 2. Review heuristics (how to actually check)

### 2.1 Is the new rule a faithful sibling? — the four-question pass

For any proposed rule/discriminator, ask in order:
1. **What is the signal?** It must be *the declared type* (class or generic signature), a pure
   function of the declaration. If it's an instance property, an annotation restating a
   declaration, or a runtime hash — stop.
2. **What layer does it live in?** Structural facts (ordering, element type, nullability-as-
   *shape*) belong on the **wire/schema** (Layer 1, digest-covered). Semantic facts
   (uniqueness, null-*policy*, cross-field invariants, typed coercion) belong in the
   **constructor's `check(GetArg)`** (Layer 2). A rule that puts a *behavioural* contract on the
   wire, or a *structural* fact in the constructor, has crossed the split (§2.4 below).
3. **Does it compose orthogonally with the existing rules?** The proof-of-faithfulness in the
   type model: discipline (from the class) and element type (from the signature) compose
   *independently* — `LinkedHashMap<String,HashSet<Foo>>` → `orderedmap:{...}{set:@AtomicSerial}`,
   outer preserves, inner canonicalises, per level, no interaction (type-model §3.1). A faithful
   new rule slots into the recursive tree-walk without needing to know about the other axes.
4. **Does the value-equality property survive?** Re-run the §1.3 test: same logical value from
   two implementations → same bytes (for canonicalise types), and the two-type tension is
   preserved (for `LinkedHash*`).

If all four hold, it's a sibling. The collection codec and the type model both passed all four;
that's why both reviews returned COHERES.

### 2.2 Testing cross-language / cross-runtime determinism claims

A determinism rule that is really a *Java artifact* will not survive translation; a rule that is
*semantic* will reappear in every runtime. **The single strongest cross-language tell we found:
the §3a tension reappears in Rust.** The insertion-ordered-set tension isn't about
`LinkedHashSet` the Java class — it's about *any* type whose iteration order is non-rederivable
history layered over order-independent equality. `der-rust-collection-mapping.md` shows the same
discipline landing on `BTreeSet`/`HashSet`/`Vec`/`BTreeMap`, and the tension recurs on the
analogous Rust types. That the *same tension* appears is the evidence the rule is semantic, not
a JVM quirk.

**How to test a cross-language claim:**
- Map the rule's categories onto a second runtime's type system (the three base categories —
  DER primitives, digest-addressed records, the six collection productions — must map cleanly;
  type-model §2.2).
- For each *discipline* outcome, find the target-runtime type that would land there and check
  the *reason* survives (is `X`'s order deterministic *in that runtime*?). If the classification
  flips for the "same" type, that's a real finding — the rule was keying on a Java-specific
  guarantee.
- For any canonical-bytes claim, verify the ordering is a **pure function of the element
  values** (X.690 §11.6 octet-sort over *complete encoded forms*), because only that gives
  machine/run/language independence (memo §2, §3). A canonicalisation that depends on anything
  the second runtime computes differently (hash, iteration order, locale) is not canonical.
- **Do not accept "it's canonical" without asking "canonical *of what*?"** (see §3.3 — Avro's
  canonical form is of the *schema*, not the *data*; that distinction sank a comparison cell).

### 2.3 Catching "this contradicts what we decided"

- **Read the revision notes first.** Both the spec (v0.13 changelog) and the determinism memo
  carry explicit "SUPERSEDED — see revision note" markers and an owner-decision block. A
  proposal that re-proposes a superseded option is the most common contradiction, and it's
  usually invisible unless you've read *why* the earlier version was dropped.
- **Grep the settled invariants the proposal touches.** Before blessing a change near
  canonicalisation, `check(GetArg)`, digests, or ordering, find the load-bearing sentences
  (memo §0 lists the three places canonicity is load-bearing: signatures, schema digests, Entry
  byte-matching). A change that "simplifies" one of these usually breaks one of the three.
- **Verify the proposal's factual anchors against the built code, don't take them on faith.**
  In the type-model review, the "mechanism gap" framing depended on `SerialForm` holding only
  `Class<?> type` and the builders taking a developer-supplied element string — both were
  *checked* on trunk (`AtomicSerial.java` `SerialForm`; `SchemaGenerator.collectionWireType`)
  before the verdict rested on them. A memo's self-description of the codebase can be stale;
  the codebase is authoritative.
- **Empty search ≠ absence; compile ≠ validated.** A rule "has no counterexample" only after
  you've enumerated the taxonomy, not after one grep. The determinism memo's authority comes
  from a *complete* JDK collection taxonomy table (memo §1), not a spot check.

### 2.4 The two-layer split — the test that catches most altitude errors

**Layer 1 (wire/schema):** the declared *structural* type — ordering discipline + element type
— baked into the token and **digest-covered** (part of the bytes `schemaDigest` commits).
Reproducible, digest-stable, a pure function of declarations.
**Layer 2 (`check(GetArg)` / constructor):** *semantic* validity + the typed coercion.
`GetArg.get("tags", null, Set.class)` hands the constructor a validated collection; the
constructor imposes uniqueness/null-policy/invariants and coerces to the concrete type it wants.

**Neither layer reads instance generics** (type-model §5) — Layer 1 reads *declarations*, Layer
2 reads *the developer's own typed constructor* — which is *why erasure never enters the path*.
When a proposal seems to need reflected *instance* type arguments, it has almost always put a
Layer-2 concern on Layer 1 (or vice versa). Push it to the correct layer and the erasure
"problem" evaporates.

---

## 3. JGDMS gotchas (the specifics that bite)

### 3.1 Canonicalise vs preserve — the exact boundary
The discriminator is **determinism of the declared type's iteration order**, and the two
concurrency-sensitive reversals (§1.1) are where reviewers go wrong. Memorise: `HashSet`/`HashMap`/
`ConcurrentHashMap`/`CopyOnWriteArraySet` → canonicalise; the priority/concurrent-FIFO queues →
canonicalise **as a multiset** (`bag:`, duplicates *retained* — a queue may legitimately hold
dups, unlike a Set); `List`/`Deque`(non-concurrent `ArrayDeque`)/`LinkedHash*`/`Sorted*`/`EnumSet`/
`ConcurrentSkipList*`/`CopyOnWriteArrayList` → preserve. On **decode**, canonicalise-set fields
reject duplicate *element encodings* (a Set can't hold post-canonical dups — memo §2); `bag:`/
`list:` keep them. The `set:`/`bag:` split is not cosmetic — getting a priority queue classified
as a set would silently drop duplicates.

### 3.2 Digest coverage — what the schema commits, and where it thins
The schema token is part of `schemaBytes`, so the ordering discipline *and* the structural
element type are **digest-covered** (§7.8; a Merkle chain over the class hierarchy). The one
place coverage *thins* is the `Any` fallback: a `set:@AtomicSerial` commits "every element is a
closed record of a known shape"; a `set:` of `Any` commits only "elements are closed-subset
values." **But this is a *reduction*, not a *loss*** — the per-element `@AtomicSerial` digests
still travel *inside* each `AnyElement.body` (type-model §4.3). When you review anything that
touches digests, keep that distinction sharp: "looser collection-level schema" is acceptable and
disclosed; "records lose their digests" would not be.

### 3.3 The "any"-fallback discipline
`Any` is a compact tagged union **over the closed subset** (scalar / `@AtomicSerial` object /
collection), and three rules make it safe:
1. **Rule-selected, never default, never developer-chosen.** It is reached *only* for the five
   genuinely unresolvable declared shapes (raw `Set`, `Set<?>`/`? extends Object`, `Set<Object>`,
   `Set<? super X>`, type-variable `Set<T>` — type-model §3.4/E10–E14). A *resolvable* type
   (`Set<Foo>`, `Set<? extends Shape>`) landing in `Any` is a **conformance failure**, not a
   convenience.
2. **It doesn't reopen the subset.** An `Any` payload is still one of the three base categories —
   never an arbitrary `Object` graph, never arbitrary `Serializable` (that's the JOSS gadget
   surface STD-006 refuses), never a cycle.
3. **Canonicalisation still applies.** An `AnyElement` is a complete TLV, so a canonicalise
   collection of `Any` octet-sorts the `AnyElement` encodings exactly as homogeneous elements
   (type-model §4.2). If a proposal's `Any`-like escape hatch breaks octet-sort or reject-non-
   canonical, it's not this `Any`.

**Tell of a bad fallback:** it becomes the *default* for convenience, or it admits something
outside the closed subset "just this once." Both forfeit a property (cross-language,
value-equality, or canonicity) the closed subset exists to guarantee.

### 3.4 The exclusion boundary is load-bearing, not incidental
The model *deliberately* excludes raw `Object` graphs, arbitrary `Serializable`, and cycles
(type-model §2.1; spec §3.7). These three exclusions are **jointly what make cross-language +
byte-value-equality + DER canonicity simultaneously achievable** — admit any one back and you
forfeit at least one. This is the "less is more" discipline: the closed subset is the price *and*
the payoff. A proposal that relaxes an exclusion "for flexibility" is spending a property; make
it name which one.

### 3.5 Opaque-octet and intrinsic-order carve-outs
Two things the ordering rule must **never** touch (spec §3.8): (a) *intrinsic-order* sequences —
principal chains (leaf-first), certificate paths, arrays — where order **is** the value and
sorting corrupts it; (b) *opaque octets* — X.509 certs, `X500Principal`, `Signature` output —
carried verbatim from `getEncoded()`, never parsed/re-encoded/sorted (re-encoding breaks the
signature over them). A canonicalisation proposal that "normalises" one of these is a security
bug. When reviewing a sort/canonicalise change, explicitly confirm it excludes both carve-outs.

---

## 4. The principles that made the reviews effective

- **The declaration is the signal.** Every faithful rule keys on what the developer already
  wrote (the declared class, the declared generic type) — never a restated fact, never a runtime
  instance. This single principle catches annotation-drift, hash-in-the-ordering-path, and
  value-vs-implementation confusion in one move.
- **Structure on the wire, behaviour in the constructor.** The two-layer split is the altitude
  check for *every* proposal. Most "we need reflection / we need erasure info" problems are a
  concern placed on the wrong layer.
- **Byte-equality must mirror the type's `equals` contract.** This is the property, stated as a
  test you can run: same value → same bytes for canonicalise types; order-sensitive for preserve
  types; stricter-than-`equals` for exactly `LinkedHash*`. If a change breaks the mirror, it
  breaks the format's headline.
- **Verify against the real artifact, report faithfully.** Check the memo's claims against the
  built code and the settled spec; separate *design* from *shipped* (the type model is design;
  the collection codec is merged-to-trunk-but-unreleased — say which). Distinguish "the design
  provides X" from "the codec delivers X today." An honest caveat is worth more than an
  optimistic tick.
- **Honest tradeoffs beat flattering ones.** The comparison table's credibility came from saying
  plainly where competitors win (Fory/Protobuf ship multi-language codecs *now*; Cap'n Proto/
  FlatBuffers are zero-copy; compact formats are smaller; Avro's automatic reader/writer
  resolution is zero-code where STD-006 asks for a constructor). A review that only finds
  strengths hasn't looked hard enough.
- **"Less is more" is a design result, not a slogan.** The closed subset, the acyclic constraint,
  the refusal of arbitrary `Serializable` — each *subtraction* is what *enables* three
  simultaneous properties. When you review an *addition*, ask which subtraction it undoes.

---

## 5. War stories (the tell that surfaced each)

### 5.1 The value-equality-proxy insight (the headline property)
**What:** the realisation that DER's canonicalise-unordered-collections rule doesn't just give
*determinism* — it makes **DER byte-equality mirror the type's `equals` contract**, restoring the
value/serial-equality correspondence JOSS structurally breaks. This became the format's
distinctive axis (the `VEq` column: "no surveyed framework — including generic ASN.1 DER —
canonicalises unordered-collection element order to preserve value-equality").
**The tell:** it surfaced from asking a *contrast* question — "why can't JOSS do this on a `Set`
field?" — rather than describing DER in isolation. JOSS serialises implementation; a `HashSet`
and a `TreeSet` that are `.equals` get different bytes. The moment you frame it as
implementation-vs-value, the property names itself. **Lesson:** the sharpest properties are found
by contrast with what the incumbent *cannot* do, not by listing what you *can*.

### 5.2 The generic-DER subtlety (the cell that almost got it wrong)
**What:** generic ASN.1 DER *is* canonical (`SET OF` sorts, definite lengths), so the reflexive
call was to give it a ✓ on value-equality too. It's wrong: generic DER canonicalises *within a
given ASN.1 value* but doesn't know a `HashSet` and a `TreeSet` are the same logical value — an
app that maps both to a bare `SEQUENCE OF` in iteration order gets different bytes. STD-006 adds
the *type→ordering-discipline* rule **on top of** DER; that's the distinctive layer.
**The tell:** "canonical" was doing two different jobs — canonical *encoding of a value* vs
canonical *choice of which value to encode*. **Lesson:** when a word (canonical, deterministic)
appears in two frameworks' claims, force it to say *canonical of what* before you compare.

### 5.3 The Avro "canonical form" trap
**What:** Avro has a "Parsing Canonical Form" + fingerprint, which invites a ✓ on canonical
encoding. It's a canonicalisation of the **schema** (for schema resolution/identity), **not** of
the **data** encoding — Avro's binary *data* isn't defined byte-canonical (no mandated map-entry
order). So its Canon/Sign cells are `~`, not `✓`.
**The tell:** the same "canonical of what?" question as 5.2 — this time the answer was "of the
schema, not the bytes you'd sign." **Lesson:** verify the *object* of a canonicity claim against
the primary spec (protobuf's own docs even title a page "Proto Serialization Is Not Canonical" —
cite the primary source, not folklore).

### 5.4 The honest-tradeoff reframing on evolution
**What:** the first draft called Avro's reader/writer schema resolution "a stronger evolution
model." Peter's correction: that's one-sided. The balanced truth — verified against STD-001
(`GetArg.get(name, default, type)`, `check(GetArg)` running *before* construction) — is
two-sided: **Avro wins on zero-code automatic reconciliation; STD-006's imperative `@AtomicSerial`
reconciliation is strictly more expressive (compute-from-old, representation change, cross-field)
and enforces invariants at construction, which declarative models structurally cannot.**
**The tell:** a comparative superlative ("stronger", "better") with only one side's benefit named.
**Lesson:** every "X is stronger" claim on an axis where both sides trade something is a flag;
name what each side gives up. And *verify the mechanics in the repo* before writing the balanced
version (I read `GetArg`'s signatures in source before rewriting) — don't reframe from memory.

### 5.5 The "built but the property isn't shipped" honesty (the caveat that kept changing)
**What:** the value-equality property went through three honest states in the docs — *design-now/
build-later* → *built + tested but unmerged* → *merged to trunk but unreleased*, plus a standing
residual (the auto-wiring for plain `Set`/`Map` fields is deferred, and **fails closed** — never
silently non-canonical). Each state was true *at the time*; the discipline was updating the caveat
the moment the codebase moved, and *keeping* the residual that was still true.
**The tell:** the temptation each time was to round "built" up to "delivered." **Lesson:** track
design-vs-shipped-vs-released as *distinct* states; a caveat is a live claim about the codebase,
not decoration — sweep every instance when the state changes (there were 8 the day it merged), and
keep the parts that are still true.

### 5.6 The cross-language proof (the §3a tension in Rust)
**What:** the confidence that the ordering rule is *semantic* and not a Java artifact came from the
byte-equality-vs-`.equals` tension **reappearing in Rust** on the analogous insertion-ordered
types — the same tension, same reason (non-rederivable history over order-independent equality),
different runtime.
**The tell:** a rule that's a language quirk classifies "the same" type differently across
runtimes; a rule that's semantic reproduces its *hard cases* everywhere. The tension recurring is
stronger evidence than the easy cases mapping cleanly. **Lesson:** to test whether a determinism
rule is real, don't check that the obvious cases translate — check that the *awkward* case (the
tension, the reversal) translates. If the awkwardness is intrinsic, it travels.

---

## 6. The one-page checklist

When a new rule/discriminator/wire-type/canonicalisation change lands on your desk:

1. **Signal check** — keys on the *declared type* (class or generic signature)? Not an instance,
   not a hash, not an annotation restating a declaration? *(§1.2, §2.1 Q1)*
2. **Layer check** — structure on the wire (digest-covered), behaviour in `check(GetArg)`? Does the
   erasure "problem" disappear once each concern is at the right layer? *(§2.4)*
3. **Composition check** — slots into the recursive rule orthogonally to discipline/element/other
   axes? *(§2.1 Q3)*
4. **Value-equality check** — same logical value from two implementations → same bytes
   (canonicalise)? Two-type `LinkedHash*` tension preserved, not "fixed"? Receiver still chooses
   the implementation? *(§1.3, §1.4)*
5. **Contradiction check** — read the revision/changelog notes; is this a superseded option
   returning? Verify the proposal's claims about the built code against trunk. *(§2.3)*
6. **Canonicity carve-out check** — canonicalisation excludes intrinsic-order sequences and opaque
   octets? Octet-sort is a pure function of element values (no hash)? *(§3.1, §3.5)*
7. **Fallback/exclusion check** — any `Any`-like escape hatch stays rule-selected + inside the
   closed subset? Any relaxed exclusion names the property it spends? *(§3.3, §3.4)*
8. **Cross-language check** — the rule's *hard* case (a tension, a reversal) reproduces in a second
   runtime; "canonical" states *canonical of what*. *(§2.2, §5.2–§5.3, §5.6)*
9. **Honesty check** — design vs shipped vs released stated distinctly; tradeoffs name what each
   side gives up; caveats are live and swept. *(§4, §5.4, §5.5)*

If all nine hold, it's a faithful sibling. If one fails, you've found the exact sentence to send
back.
