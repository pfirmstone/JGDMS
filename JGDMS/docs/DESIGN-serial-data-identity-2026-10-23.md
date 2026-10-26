# Design: serial data identity — provenance domains for deserialized objects

**Status: design note, revision 1 (2026-10-23). Not implemented, not scheduled.** Extends DirtyChai
`docs/DESIGN-privilege-ceiling-2026-10-19.md` (r4, §13: every `doPrivileged` bounded by the caller's per-class policy
domain) with the third authorization axis: **what data a method is running on**. Produced with AI assistance (Claude
Fable 5.1; code anchors from a Sonnet recon pass, `claude-sonnet-5-5` self-reported, 22 read-only commands,
2026-10-23 06:48–06:50Z) in discussion with the maintainer. Nothing here has been executed; every behavioural claim is
from reading source, with file:line anchors taken from the working trees on 2026-10-23 (§12). Prior art is in
`docs/PRIOR-ART-privilege-design-2026-10-23.md`; the parts that bind this design are repeated in §9.

Paths: `D` = `jgdms-der/src/main/java/au/net/zeus/jgdms/der`; `P` = `jgdms-platform/src/main/java/org/apache/river/api`;
`JB` = DirtyChai `src/java.base/share/classes`; `HS` = DirtyChai `src/hotspot/share`.

## 0. The question, and the limits

Java's model has two axes — *what code is running* (`ProtectionDomain` from the class) and *on whose behalf* (principals
from the `Subject`) — and none for *what it is handling*. `getContext()` walks the stack, keeps each frame's class domain,
and discards the frame's receiver and arguments; the data the check is about is in the frame and thrown away. Objects
reconstructed from the wire therefore have no identity policy can see, and an attacker-built instance of a trusted class
is indistinguishable on the stack from one trusted code built itself. That is the limit stated in the ceiling note §0:
a ceiling bounds control flow and cannot follow data.

This note gives wire-origin objects an identity — a **provenance domain** — and has the stack walk collect it from frame
**receivers**, so that any method running on such an object, on any thread, carries the object's provenance into every
check. Three limits are stated here first, because they are where the mechanism stops:

1. **Receivers, not arguments.** `Files.write(attackerPath)` has a trusted receiver; the data is a parameter. Not covered.
2. **Roots, not derivations.** A method on a stamped object that builds a new object and hands it off produces an
   unstamped object. There is no propagation (Trishul paid +167% CPU-bound for it, §9).
3. **Vetted classes only.** `String`, boxed primitives, arrays and JDK collections are not `@AtomicSerial` and carry no
   label. Attacker-controlled strings flow unlabelled.

All three are covered by the other half of the model, unchanged: authenticate the caller, validate (`check(GetArg)`),
the finite `@AtomicSerial` set with the escape rule on constructors, the type gate, two-phase decode.

## 1. What a datum's identity is

A **provenance domain** is a *sealed*, value-equal `DomainIdentity` (`JB/java/security/DomainIdentity.java`, ctor at 150):

| part | value | why |
|---|---|---|
| codesource | `DigestCodeSource` over the class's **schema leaf digest** (`D/schema/AtomicSerialSchemaRecord.java:340` `schemaDigest()` = SHA-256 of the record's deterministic DER; `D/schema/SchemaChain.java:116` `linkAndGetLeafDigest`) | the *shape* — the same identity the admission gate keys on |
| principals | the authenticated identity of the **delivering connection** (§2) | the *origin*, as far as this node can vouch for it |
| static permissions | none; policy consulted | so polpAudit can observe and policy can grant |
| sealed | yes (§5) | the principals are the data's provenance, not the current actor's identity; no combiner may add to them |

Interned by value: one domain per `(schema digest, principals)` pair, not per object. The DER payload digest (the
*content*; `payloadSequence` is a `byte[]` at `D/object/ObjectCodec.java:315–322`) is available and may be carried as an
optional attribute for pinning specific values, but it is **not** part of the domain identity — per-value policy keys
are too fine to administer.

**`DigestCodeSource` must be built through a verified path.** `JB/java/security/DigestCodeSource.java` has an
`unverified` flag (field 143; the asserted-digest constructors at 497–516 set it; `readExternal` at 852), and
`P/security/DigestGrant.implies` (115–165) requires `unverified() == false`. A provenance codesource whose digest we
computed ourselves from the schema bytes is verified; constructing it through an asserted-digest constructor would make
every digest grant silently fail to match. This is the one-line decision on which the whole mechanism either grants or
never does.

## 2. Where identity comes from: explicit, from the gate, never ambient

The schema half is in hand at construction. The peer half is **known but not bound** when objects are built:
`jgdms-jeri/.../BasicInvocationDispatcher.java` unmarshals arguments at 1127 (`unmarshalArguments`; `admitStream`
1121–1122), and the `Subject.doAs(workerSubject)` / `UserSubjectSupport.callAsAll(userSubjects, …)` wrap begins only at
1156 → 2191/2201. `ObjectCodec` and `DerGetArg` read no `Subject` at all (recon 1f). So a codec that read
`Subject.currentAll()` would see nothing — the JGDMS analysis N1 finding from another angle.

Therefore **provenance is passed in explicitly by the gate**, not read from the thread:

- `DerGetArg` already has a package-private, deliberately non-broadcast channel (`D/object/DerGetArg.java` canonical ctor
  at 204 taking `ResolutionContext`; `getObjectStreamContext()` at 555 returns only the completion token, by design —
  recon 2d). Provenance travels the same way: a `ProtectionDomain provenance` on the context the dispatcher/gate hands
  to `ObjectCodec.decode` (313) / `decodeHierarchy` (702), surfaced to constructors as `GetArg.provenance()`.
- The dispatcher knows the client before decoding (`getClientSubject` ~2062, `getUserSubjects` 2079). It builds the
  provenance domain from that and passes it in.

**Whose principals (recommended default, maintainer to confirm):** the *delivering connection's* authenticated identity
(TLS / Kerberos / SPIFFE), stated in the policy and the docs as **"who delivered this to me"**, not "who originated
it". A relayed object decoded from a stream carries the relay's identity. This follows the 2026-10-20 ruling that
identity comes from an endpoint's own authenticated call, never the delivering stream's claims, and it is a deliberate
limit, not a gap. The alternative — the dispatcher's user `Subject`s — coincides for a direct call and should not be
used for anything that may have been relayed. Peer-*claimed* domains (`RemoteContextCodec`) never feed a provenance
domain.

## 3. Stamping

Every object the codec constructs is stamped, not only the root: nested objects are built by the same `decode` path
with the same delivering identity and their own schema digest.

**Preferred: field + marker interface.** `GetArg.provenance()` returns the domain; an `@AtomicSerial` class stores it in
a `final` field and implements `Provenanced { ProtectionDomain provenance(); }`. The annotation processor cannot add the
field, but it can refuse to compile a class that lacks it: `MarshalDelegateProcessor.validate` (166–197) today emits only
`Kind.WARNING` (177, 182, 187, 191) and never rejects (comment at 165); one diagnostic promoted to `ERROR` under an
option (it already has `marshaldelegate.validateOnly`, 69) makes the finite set enforceably stamped. This is consistent
with §6 of the ceiling note: the constructable set is finite and vetted, and adding the field is the same review pass
as the escape rule.

**Bridge: side table.** A weak identity map in `jgdms-der` (there is none today — recon 8 found only identity *sets* in
`D/stream/StreamPolicyMeter.java:69,73`), populated at construction, consulted by the walk for objects without the
field. No class changes; a lookup per receiver per walk and GC pressure. Useful to get numbers before touching classes;
not the end state.

Exposing the domain to the constructor is harmless: it is the domain the object will be *checked against*; reading it
cannot widen anything.

## 4. Collection: the native walk, continuing past privileged frames

The walk is `JVM_GetStackAccessControlContext` (`HS/prims/jvm.cpp` 1260–1333). It is the native one and must stay so:
the maintainer's StackWalker-based `getContext()` was fragile during bootstrap (contexts are built by stack walks before
the JDK is initialised; `JB/java/security/AccessController.java:987` records the same), and the native walk already
handles virtual threads (`vframeStream` over the continuation). It also already reads frame locals — for the
`executePrivileged` frame it takes `locals[1]` (ACC) and `locals[2]` (caller class) at 1289–1296 — so reading local 0 of a
non-static method is the same `javaVFrame::locals()` machinery on another frame.

Change, in that loop (1278–1310): for each frame whose holder implements `Provenanced` and whose method is non-static,
read local 0, call (or read the field behind) `provenance()`, and add the sealed domain to the array alongside the class
domains (dedupe at 1305–1308 applies).

**Collection continues past the privileged frame.** The class-domain walk stops at `executePrivileged`
(`if (is_privileged) break;` at 1310). Provenance collection must **not**: if it stopped there, the gadget path
`readResolve → trusted helper → doPrivileged` would shed the label at the cut, exactly as .NET's `Assert` masked `Deny`
(§9), on the one path the mechanism exists for. So the loop breaks for class domains and keeps walking to the bottom
for receivers. Provenance is the one case where "sticky" behaviour is right; the proxy objection to sticky domains does
not apply (platform helpers act on a proxy's *behalf*; on a gadget's *data*).

**Compiled frames may have dropped `this`.** C2 drops a local after its last use; `javaVFrame::locals()` on a compiled
frame returns what the scope descriptor kept, and a receiver not needed after the current call site can be unavailable.
`executePrivileged` solves this for its own locals with `ensureMaterializedForStackWalk` and `reachabilityFence`; that
cannot be asked of every `@AtomicSerial` method. Two rules:

1. **A class flag.** Provenanced classes are the finite `@AtomicSerial` set; mark the `Klass` when it is found to
   implement `Provenanced` at link time, and have the compilers keep the receiver live to method exit for methods of
   flagged classes. One live oop per frame on a small set of classes.
2. **Fail closed when the receiver is unavailable anyway.** If a frame's holder is flagged and local 0 cannot be read
   (deopt edge cases, a frame compiled before the flag applied), add an **unknown-provenance** sealed domain that no
   policy grants to, rather than skipping the frame. Skipping is the fail-open path a gadget would aim for: get the
   method JIT'd, then have the walk lose its label.

## 5. Semantics

- **Sealed.** `SubjectDomainCombiner.combine` (`JB/javax/security/auth/SubjectDomainCombiner.java:219`) skips
  `staticPermissionsOnly() || implies(ALL_PERMISSION)` domains at 271 and adds the merged principals to every other
  domain. Provenance domains must be skipped too: their principals are the data's origin, and if the combiner added the
  current thread's subject to them, data from peer P handled under the admin's subject would acquire admin grants. This
  is the `DomainIdentity.sealed` mode from the ceiling note §4.1 (no such flag exists yet — recon 14b), used for its
  actual purpose; `goCombiner`'s presence check protects it against other combiners.
- **A check is** `stack ∩ (§13 per-class domain of the elevating caller) ∩ provenance domains of receivers on the stack`.
  Each only narrows. Code, actor and data meet in one intersection. No grant on a provenance domain means a method
  running on an untrusted object that triggers a check is denied.
- **Thread hand-off is covered by construction.** When a worker calls `task.run()`, the task is the receiver; its label
  is on that thread's stack. No capture at submission. This closes the ceiling note's §0 limit for stamped objects.
- **Two-phase decode is unchanged.** The stamp is set at construction; admission does not alter it. What an admitted
  object may be used for is the grant on its `(schema, principals)`.
- **§13 is unaffected**; the two compose by intersection.

## 6. Policy and polpAudit

The grant is ordinary policy text with the digest grammar the policy already parses:

```
grant codebase "digest:sha256:<schema leaf digest>",
      principal javax.security.auth.x500.X500Principal "CN=peer" {
    permission java.util.PropertyPermission "foo", "read";
};
```

"Data of this shape, delivered by this peer, may be used for this." Coarser forms fall out of the existing grammar:
principal-only grants ("anything delivered by P"), digest-only grants ("this shape from anyone"). No grant means
nothing — fail closed. polpAudit records against `(schema digest, principals)`, giving the floor **per data
provenance**: the answer to "what may this deputy do with what it is holding". Three lints, the same family as the
ceiling note §13's: (1) the condenser never widens a principal-qualified data grant into an unqualified one; (2) audit
runs have the delivering identity bound as production does, or the floor records unqualified grants for whatever the
harness exercised; (3) a wildcard connect/write/exec grant to any `digest:` codesource is flagged for review.

Digest agility and revocation follow the prior art (§9): the algorithm name travels with the digest (`DigestCodeSource`
carries `digestAlgorithm`, field 139); revocation of a digest grant is deletion of the rule; multiple digests may be
granted during an algorithm migration.

## 7. Cost

- One interned `DomainIdentity` per `(schema, principals)`; contexts gain provenance domains only when a stamped
  receiver is on the stack — usually zero or one — so the `ContextCache` grows by distinct provenance *sets*, not by
  object count.
- The walk: a klass-flag test per frame on every walk, plus a locals read on flagged frames; and a **full-depth**
  receiver scan whenever a check happens inside a privileged action (common in java.base), where the class-domain walk
  used to stop. Measure with `ContextCaptureSplit` before any policy is written. If the full-depth scan is the cost
  problem, the fallback is to stop receiver collection at the *second* privileged frame, which still covers the
  single-cut gadget path; record that as a measured decision, not a default.
- Side-table bridge: one weak identity lookup per receiver per walk — the reason it is a bridge.

## 8. What the first test looks like

The deputy test from the ceiling note §12/§13, repeated with a stamped receiver: an `@AtomicSerial` task decoded from an
authenticated peer with no provenance grant, submitted to a pre-existing platform pool, whose `run()` reaches a URL
handler → **denied**; the same with a `digest:` + principal grant for the property it needs → **passes**; the same with
the method JIT'd (`-Xcomp` or a warm loop) → still denied, which tests §4's compiled-frame rule; and the same object
handled under an *admin* subject → still denied, which tests sealing. Four runs, one object, one grant.

## 9. Prior art that binds this design (from `PRIOR-ART-privilege-design-2026-10-23.md`)

- **No precedent for the mechanism** — labelling deserialized objects with a value-equal domain and intersecting
  receivers' domains into an ordinary `AccessControlContext` was not found (five searches listed in the report).
- **Two citations to distinguish from.** *Trishul* (Nair, Simpson, Crispo, Tanenbaum, VU Amsterdam IR-CS-45; modified
  Kaffe 1.1.7): "the initial context taint of a method includes the taint of the `this` pointer for instance methods",
  policies match `thisTaint`, enforced at runtime; +167% CPU-bound, +12.9% load time, shadow memory per object member,
  full propagation, no deserialization treatment, archived 2021. *IBAC* (Pistoia, Banerjee, Naumann, IEEE S&P 2007):
  permission sets as per-value integrity labels, checked at the same points as stack inspection **and through
  privileged blocks**; static prototype only, no cost data. This design differs from both by labelling roots only (no
  propagation), reusing `ProtectionDomain` and the existing intersection as the carrier, and keying policy on a canonical
  schema digest plus delivering principals.
- **The Deny/Assert lesson** (.NET Framework 4 removed `Deny` because "A Deny action could be easily overridden by an
  Assert action"): provenance collection must continue past privileged frames (§4), as IBAC chose.
- **Laminar's trap**: it labels by *allocation* context, so a deserialized object would carry the decoder's label, not
  the peer's. Stamping here is by the gate's explicit provenance (§2), never by who allocated.
- **Schema-digest gating for security is unprecedented**: Avro's fingerprints "are not meant to provide any security
  guarantees"; Cap'n Proto IDs are name-derived. Every hashed canonical form has produced a "verifier hashes form A,
  consumer acts on form B" bug (CMS BER/DER, BIP 66, XML C14N, CBOR 7049→8949, JCS `-0`); the fix is always to reject
  non-distinguished input before hashing and act on the hashed bytes, which DER gives for free provided the decoder
  rejects non-DER.

## 10. Order if started, and where each slice runs

The bridge from this session to the maintainer's machine is a 2-CPU Linux VM with OpenJDK 11; DirtyChai's build is
`windows-x86_64-server-release`. **Anything that runs on or tests DirtyChai runs on the Windows host** (Claude Code
there, or the maintainer). DirtyChai `CONTRIBUTING.md`/`AI_POLICY.md` 1.1: source and shipped docs are human-written;
AI may author *new* tests only under the dedicated `ai/` subtree (Zone T).

| # | slice | repo | where | who |
|---|---|---|---|---|
| 1 | `Provenanced` interface; `GetArg.provenance()`; explicit provenance on the decode context; `ObjectCodec` stamping; side-table bridge; test that a decoded object carries `(schema digest, principals)` | JGDMS | container (any JDK 21+) or host | agent (Sonnet) |
| 2 | Dispatcher builds the provenance domain from the delivering identity before `unmarshalArguments` (1127) and passes it in; verified `DigestCodeSource` construction; negative test that an asserted-digest codesource never matches | JGDMS | container or host | agent (Opus) |
| 3 | `DomainIdentity.sealed`; combiner skip at `SubjectDomainCombiner:271`; `goCombiner` presence check | DirtyChai `java.base` | host | human (policy) |
| 4 | Native walk: receiver read for flagged klasses; continue past privileged frame for receivers; unknown-provenance domain; compiler keep-receiver-live flag | DirtyChai HotSpot | host | human (policy) |
| 5 | Zone-T tests for 3 and 4 under `ai/`, each stating the invariant it defends | DirtyChai | host | agent (Opus) |
| 6 | Processor: provenance-field check, `WARNING` → `ERROR` under an option | JGDMS | container | agent (Sonnet) |
| 7 | polpAudit: record and condense `(digest, principal)` grants; the three lints | JGDMS/DirtyChai tooling | host | agent (Opus) + human |
| 8 | The four-run test of §8 | JGDMS on DirtyChai | host | agent (Opus) |

## 11. Not verified

Nothing executed. Not examined: whether `javaVFrame::locals()` reliably yields local 0 for compiled frames of
non-flagged classes (the fail-closed rule in §4 assumes it may not); the cost of a full-depth receiver scan; whether a
`Klass` flag can be consulted by C1/C2 for receiver liveness without a larger change; `UserSubjectSupport.callAsAll`;
what `getClientSubject` returns for relayed objects; `DigestCodeSource` serialization (`Externalizable`) interaction with
sealed domains.

## 12. Anchors (working trees, 2026-10-23)

JGDMS: `D/object/ObjectCodec.java` 213/233 ATOMIC gate, 255/270/283 PROXY gate, 313–341 `decode`, 702/774/795
`decodeHierarchy`, 1122 `findGetArgConstructor`; `D/object/DerGetArg.java` 76 class, 142–204 ctors, 379/540/555
accessors, 231–245 package-private resolution/loaders; `D/schema/AtomicSerialSchemaRecord.java` 340–342, 410;
`D/schema/SchemaChain.java` 92, 116; `P/io/AtomicSerial.java` 169 annotation, 291 `GetArg`, 371 `callerClass`;
`MarshalDelegateProcessor.java` 68–69, 103, 166–197, 237, 324–355; `BasicInvocationDispatcher.java` 1077–1129,
1156, 1771–1783, 2062, 2079, 2132–2243, 2191, 2201; `P/security/DigestGrant.java` 52, 66, 115–165;
`P/security/DigestDomainGrant.java` 65, 124–155; `D/stream/StreamPolicyMeter.java` 69, 73.
DirtyChai: `JB/java/security/DigestCodeSource.java` 83, 139–143, 386–516, 553–667, 852; `JB/java/security/AccessController.java`
956, 965, 980, 987, 1056, 709/746; `JB/java/lang/LiveStackFrame.java` 53, 93, 110, 160–248; `HS/prims/jvm.cpp`
1260–1333 (1283–1287, 1289–1296, 1302, 1305–1310, 1319, 1330); `JB/javax/security/auth/SubjectDomainCombiner.java` 219,
265, 271; `JB/java/security/DomainIdentity.java` 150, 184.
