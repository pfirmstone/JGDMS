# Marshalling security properties — catalog (gathering pass, not yet condensed)

- **Authored:** 2026-07-13, gathered from this session's audit work and the pron98 Reddit thread.
- **Status:** Phase 1 (gather) of a two-phase task. This is the comprehensive, information-preserving
  catalog — every property established or verified this session, what it contributes, and what
  happens when it's violated. Phase 2 (condense precisely, without loss of information) is a
  separate pass once this is reviewed; don't draw Reddit-reply text directly from this doc yet,
  it's deliberately verbose here so nothing gets lost before condensing.
- **Organizing idea:** each property below is independently necessary and none is sufficient alone.
  Violating any one reopens a real, historically-exploited failure mode even if every other
  property holds. Where two properties are commonly conflated or mistaken for substitutes, that's
  called out explicitly (the determinism/behavior-freedom pair below is the worked example that
  prompted this doc).

---

## 1. Canonical / deterministic encoding

**What it is:** the same logical content always produces the same bytes — minimal-form lengths,
octet-sorted collections, canonicalized floats/NaN, no encoder-choice ambiguity.

**What it contributes:** a digest of the bytes becomes a stable identifier for the *logical value*,
not an artifact of encoder mood. This is the precondition for any digest-based mechanism: recurring
attack detection (Federated Malicious Digest Registry), code identity (digest-based `LoadClassPermission`),
schema identity (`schemaDigest`), and cross-language equality (same logical content, same bytes,
regardless of which implementation wrote it).

**Consequence of violation, verified this session:** `DerFieldStore.decodeAllFields` used to
silently default missing fields and discard extra trailing TLVs against the schema. Append a
no-op trailing TLV to a valid payload, fix up the length octets, and you get byte-different bytes
that decode to the identical object — one logical attack, multiple valid digests. Not an RCE by
itself; it defeats anything downstream that recognizes recurrence by digest, for free, with no
change to the actual attack. Fixed this session (`c868bfe05`): strict decode now throws on any
TLV-count mismatch against the transmitted schema, restoring one-encoding-per-value.

**Independent of:** behavior-freedom (§2) — a format can be perfectly deterministic and still let
an attacker execute code via method invocation during parsing. See the worked example at the end.

---

## 2. Stream/wire format never invokes behavior belonging to the object it's reconstructing

**What it is:** the stream format's job is reading structure — which bytes mean what, per the
transmitted schema — and nothing else. It never calls `hashCode()`, `equals()`, `compareTo()`, or
any other method on an object it's in the middle of reconstructing.

**What it contributes:** this is the actual mechanism that closes gadget-chain attacks, not a
side effect of something else. Any method invocation during structural decode necessarily executes
against attacker-controlled state before anything has validated it, because validation is a
different layer's job (§5) and hasn't run yet at the point structural decode is still happening.

**Consequence of violation:** this *is* the CC6/CC1/CC7 mechanism, not an analogy for it.
`HashSet.readObject()` → `hash(key)` → `key.hashCode()` fires on a `LazyMap` with zero invariant
checking, mid-reconstruction, because `HashSet`'s own deserialization logic decided invoking a
method on not-yet-trusted data was fine. CC1 substitutes the same technique via
`AnnotationInvocationHandler.memberValues`; CC7 via `Hashtable` key-collision `equals()` during
bucket resolution. Three different entry points, identical underlying violation.

**Verified/reinforced this session:** the immutable collection-wrapper fix found that even *this
codebase's own* accumulation phase violated this property more than realized — `LinkedHashSet.add()`/
`LinkedHashMap.put()` call `hashCode()`/`equals()` during decode, not just at final construction.
Fixed (`666c6d06f`) by accumulating into a plain positional `ArrayList` first, zero method
invocations on elements at any point during decode, verified via a spy-element test instrumenting
all three methods with call counters across all six wire disciplines.

**Independent of:** determinism (§1) — see worked example.

---

## 3. Evolution is the constructor's responsibility, never the wire decoder's

**What it is:** when a byte payload doesn't exactly match what a schema declares (a field the
schema expects is missing, or extra data is present), deciding what that *means* — old version,
new version, corruption, attack — belongs entirely to `@AtomicSerial`/`check(GetArg)`'s own
defaulting logic (`GetArg.get(name, default, type)`), never to the wire-level decoder.

**What it contributes:** keeps the wire decoder's contract fully specified and auditable — it
either matches the schema it was given or it doesn't, no judgment calls. This is also what makes
§1 (determinism) actually hold: a decoder with no leniency can't accept payload variants, so there's
exactly one valid encoding per value, not "one plus however many the decoder is willing to tolerate."

**Consequence of violation:** the exact same schema-leniency bug as §1 — this property and
determinism failed together because they're causally linked (the leniency *was* the wire decoder
making an evolution decision), but they're conceptually distinct failure modes worth naming
separately: one is "digests become unstable," the other is "the decoder's contract becomes
ambiguous, so 'is this corruption or evolution' stops being a well-defined question."

**Root cause, worth preserving precisely:** this bug entered the codebase via implementers
unconsciously pattern-matching conventions from *other* marshalling systems (Avro/Protobuf
deliberately *do* reconcile schema at the wire level) rather than this project's specifically
different intent. A generalizable lesson about AI-assisted (and human) drift toward "how this
usually works elsewhere" rather than the actual local design intent.

---

## 4. The wire's schema chain determines what class gets constructed — never the attacker

**What it is:** `decodeHierarchy` loads the class named in the wire's own transmitted schema chain
and requires a `(GetArg)` constructor for it. There is no "any class with a no-arg constructor
reachable off the classpath" path the way `ObjectInputStream` has.

**What it contributes:** this is the actual precondition CC6 depends on and this design denies —
the entire "substitute `LazyMap` for a `Map`-typed field" move requires the stream to be able to
name an arbitrary implementation class for a declared-interface slot. Here, a `set:`/`map:`-typed
field resolves to this codebase's own immutable wrapper (§8) regardless of what the sender's bytes
claim; there is no allowlist to get right for that slot because there's no question being asked.

**Consequence of violation:** the literal `ObjectInputStream` failure mode — stream-chosen classes,
the root enabling condition every gadget chain in this thread depends on. Without this property,
§2 (behavior-freedom) is the only remaining defense, and it only helps once an object exists;
this property is what prevents the wrong object from being constructible in the first place.

---

## 5. Construction is atomic — validated before the instance exists, not after

**What it is:** `check(GetArg)` runs, and can throw, before the object is ever returned to a
caller. No partially-constructed, not-yet-validated instance ever becomes observable.

**What it contributes:** guarantees that *any* object that exists in the system has already passed
its own semantic validation — which is the precondition §2's safety argument actually depends on.
"Method invocation is safe once elements are validated" (the collection-wrapper case) only holds
because atomicity guarantees "validated" means "fully validated," not "validated so far."

**Consequence of violation:** classic Java serialization — objects constructed via reflective
field-scraping (`Unsafe`/`ReflectionFactory`, or ordinary field-by-field `readObject`) before any
invariant is checked, sometimes never checked at all if `readObject` doesn't bother. `Enum.valueOf`-
style eager class-init during decode (§9's L1 finding) is a narrow instance of this same shape:
behavior (a static initializer) executing before the class it belongs to has been through any gate.

---

## 6. Admission control — should this class/stream be reachable at all, given who it's from

**What it is:** a decision made *before* anything runs: given a code digest (code identity), a
SPIFFE workload identity (process identity), and a JWT (user identity), should this code load into
this process at all, and should this byte stream be parsed at all — distinct from and prior to
"is this object's state valid" (§5, which only applies to objects admission already let through).

**What it contributes:** the piece JEP 290's `ObjectInputFilter` structurally cannot provide.
`ObjectInputFilter` can restrict *which classes* are reachable from a stream — a real, useful
control — but it operates with zero visibility into *which remote party* the stream came from, so
it can't express "this class from this peer is fine, the same class from that peer is not."

**Consequence of violation:** the confused-deputy shape generally — authority bound to code/process
rather than to who's actually asking, so any privileged boundary that doesn't check "on whose
behalf" is exploitable (viral `doPrivileged`, SSRF-to-cloud-metadata being the generalized,
non-Java-specific instance of the same failure).

**Worth being precise about the actual gap, verified in this session's Reddit-review work:** an
application that owns its own connection *can* already splice peer identity into an
`ObjectInputFilter` today via closure, zero JDK changes needed. The real, structural gap is
narrower and specific to *generic libraries*: something invoked as `readValue(InputStream, Class)`
never receives the connection at all, so it has no reachable parameter for identity to travel
through even when the calling application has it available. "No JDK hook exists anywhere" is not
quite the accurate claim; "no library-reachable hook exists" is.

---

## 7. Pre-load static verification of contract compliance — with a sharp caveat this session found

**What it is:** before a downloaded class is loaded at all, its bytecode is statically analyzed for
`@AtomicSerial` contract compliance (correct constructor shape, validation ordering, field
type-safety) in a separate, network-isolated process, with results signed and trusted only once a
quorum of independent analysis engines agree (`VerdictRegistryImpl`, confirmed live and wired this
session, not aspirational).

**What it contributes:** an admission gate earlier than §6 — code that fails this check is never
loaded regardless of whose codebase it came from.

**Consequence of violation, found and being fixed this session — the sharpest finding of the
whole audit:** a static checker that only verifies a check method's *shape* (right signature,
called before `super()`) rather than genuine *data dependency* between what it reads and what it
returns is trivially satisfiable by a vacuous check — `check(GetArg arg){ return true; }`, or
worse, `check(GetArg arg){ String.valueOf(arg.get(...)); return true; }`, laundering a discarded
read through any inert method call. Confirmed by building and running real bytecode against the
actual analyzer, not just reasoned about. **General lesson, worth stating on its own:** a
mechanism that verifies "a check exists" is not the same mechanism as one that verifies "the check
does something," and the gap between those two is exactly where an admission gate can look solid
while doing nothing — the same category of mistake JEP 290 makes at the identity layer (§6),
recurring one layer down at the code-verification layer.

**What this property structurally cannot do, even fixed:** prove the validation logic is
*semantically correct* — a check method that reads a field and applies genuinely wrong logic to it
still passes a data-dependency check. That's undecidable in general and out of scope by design, not
an oversight.

---

## 8. Immutable value objects — no mutation after construction

**What it is:** once a `set:`/`map:`/`list:`-typed field is decoded, what's handed to
`check(GetArg)` is a plain, array-backed, immutable holder — no working mutator methods — not the
raw mutable collection built during accumulation.

**What it contributes:** closes the gap between "validated at construction" and "still valid
whenever anyone looks at it later." Combined with §5 (atomicity), guarantees an object's validated
state can't be silently altered out from under holders of a reference to it after the fact.

**Consequence of violation, found and fixed this session:** `AtomicSerial.java`'s own documented
contract said collection fields would be "replaced... by a safe limited functionality immutable
Collection instance" — but the actual decode path handed constructors a raw, mutable
`LinkedHashSet`/`LinkedHashMap`/`ArrayList`. Documented but not implemented. This was found
specifically *because* a public claim ("handed to the constructor as an immutable parameter") had
to be verified before being reused in Reddit material — a different discovery mechanism than any
vulnerability-hunting pass would have used, since nothing about the gap itself is exploitable in
isolation; it's a broken promise, not a hole.

---

## 9. Per-class, per-endpoint namespace — no shared/ambient class resolution

**What it is:** each class in an object's inheritance hierarchy gets its own private namespace;
`ClassLoader`s are assigned per-endpoint, never duplicated via `TCCL`/stack-walking the way
classic RMI did.

**What it contributes:** deterministic, unambiguous class resolution independent of unrelated
code's classloading state — closes the historical RMI class-resolution "hand grenade" problem
(hit-or-miss `TCCL`/stack-walk resolution) at the root rather than patching around it.

**Consequence of violation:** classic RMI-era `ClassNotFoundException` non-determinism, or worse,
a namespace collision letting one party's class definition silently stand in for another's —
exactly the kind of ambiguity §4's "the wire's schema chain determines the class" is designed to
prevent from the opposite direction (this property prevents ambiguity in *resolving* a named class;
§4 prevents the attacker from *naming* an arbitrary one in the first place).

---

## 10. Static analysis coverage must actually reach the code it claims to admit

**What it is:** the promise that "downloaded code is statically analyzed before loading" needs to
be true of the *actual* toolchain in front of it, not just the analyzer's own logic.

**Consequence of violation, found this session, not yet a security bug but a real coverage gap:**
`jgdms-der`'s own two `@AtomicSerial` classes (`ServiceSchemaEntry`, `DerProxySerializer`) currently
fail-secure to `MISSING_CONSTRUCTOR` under BAE's compliance visitor, not because they're
non-compliant, but because the module's resolved ASM dependency (9.7.1) rejects Java 25 class
files outright, a parser-version gap, not a logic gap. Fail-secure means nothing gets through
incorrectly, but it also means these two classes currently get zero real scrutiny from the
mechanism that's supposed to provide it. Logged as a follow-up (`asm.version` bump to 9.9.1),
not yet actioned.

---

## Worked example — the property that prompted this catalog: determinism and behavior-freedom are independent, and neither substitutes for the other

**Only deterministic JSON can be secured against digest evasion — but determinism alone does
nothing against gadget chains, because that's a different property's job.**

A hypothetical, perfectly canonical JSON encoding (sorted keys, fixed number formatting, no
encoder-choice ambiguity) gets you §1's guarantee: one attack, one digest, no free evasion by
re-serializing with different whitespace or key order. It gets you *nothing* against CC6, because
nothing about canonicalizing the *bytes* stops the *parser* from invoking `hashCode()`/`equals()`/
a custom deserializer callback on an object mid-reconstruction, before anything has validated it.
A JSON library with polymorphic-type deserialization (Jackson's `@JsonTypeInfo`-style mechanisms
are the closest real-world analogue) that resolves a type and invokes constructor/setter logic
based on attacker-supplied type hints, before any invariant check, has the CC6 shape regardless of
how deterministic its byte-level encoding is.

The reverse asymmetry holds too: a parser that never invokes object behavior during structural
decode (§2) but accepts non-canonical input (multiple valid encodings per value) is safe against
RCE but not against digest evasion — exactly what this session's own schema-leniency bug
demonstrated, in a format that already had §2 correctly implemented.

**General form of this pattern, worth checking for every property above:** for each property,
name a concrete format/mechanism that has it without its neighbors, and confirm the failure mode
its neighbors exist to prevent still applies. This is the check that keeps a properties list from
becoming a list of things that sound related but aren't actually independent — sections 1-9 above
have been through this check at least informally; a dedicated pass confirming each pairwise
independence claim explicitly would be a reasonable next step before condensing.

---

## Cost of leaving deserialization security unaddressed — timeline, not a property, but load-bearing context for §6/§7's "TBD" framing

Commons Collections gadget chains, 2015 (the CC6/CC1/CC7 mechanisms cataloged in §2 above).
JEP 290, 2017 — `ObjectInputFilter`, opt-in, attack-specific, changes nothing by default. JEP 415,
2021 — context-specific filters, same opt-in default. 2026: still no structural fix to the
underlying mechanism; "how exactly we do that process is TBD" (pron98, this thread, verbatim).
Meanwhile, CVEs in the same vulnerability class kept landing throughout: Apache MINA
CVE-2026-41635, patched, then bypassed again as CVE-2026-42779; Oracle Java SE RMI CVE-2026-21925
(Jan 2026 CPU). A decade-plus of successive, opt-in patches to a structural gap that has not been
closed, with the actual close-the-gap effort only now starting and still undefined. Already used
once in this thread (the "one enforceable principal per process" reply) as the designated response
if cost-effectiveness objections recur; held in reserve for whichever reply needs it next.

---

## Open items for the condense pass

- Decide which properties are strong enough, on their own, to be Reddit-reply material versus
  which are more useful as internal design documentation (§9 and §10 lean internal; §1/§2/§6/§7
  have already proven themselves as public-debate material).
- The worked example's "general form" note (pairwise independence checking) is itself worth
  doing properly before condensing, not just noting as a suggestion.
- Consider whether §7's finding (shape-verification vs. content-verification) deserves its own
  standalone public framing — it's structurally identical to the JEP 290 critique (§6) one layer
  down, which is a strong, symmetry-based argument that hasn't been made publicly yet.
