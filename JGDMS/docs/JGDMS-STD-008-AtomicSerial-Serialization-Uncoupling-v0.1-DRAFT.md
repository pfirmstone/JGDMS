# JGDMS-STD-008: @AtomicSerial Serialization Uncoupling (JGDMS 4.0.0)

**Status:** Draft (for discussion)
**Version:** 0.6-DRAFT
**Applies to:** JGDMS 4.0.0, DirtyChai (JDK fork), and non-JVM JGDMS participants
**Depends on:** JGDMS-STD-001 (@AtomicSerial), JGDMS-STD-006 (DER Wire Format)
**References:** Birrell, Evers, Nelson, Owicki, Wobber, *Distributed Garbage
Collection for Network Objects*, DEC SRC Research Report 116 (1993) — the
normative DGC algorithm (§6).
**Supersedes (on completion):** the Java-Object-Serialization coupling of the
`@AtomicSerial` API as defined in STD-001

> **Editorial note (v0.6-DRAFT):** This standard captures the 4.0.0 decision to
> remove all Java Object Serialization coupling from the `@AtomicSerial` API. It
> records the design agreed in design discussion; field-level details marked
> **[OPEN]** await confirmation. Class/line references are against `trunk`
> (worktree `der-wireformat-std006`).
>
> **Changes in v0.2:** §6 (client-side DGC) rewritten against the source
> algorithm (SRC-RR-116): the dirty/clean/lease/sequence-number protocol is
> preserved verbatim and rides DER as ordinary JERI calls; only the *local*
> `ObjectInputStream`/`registerValidation` batching coupling is replaced; the
> transmit-race acknowledgement ordering (RR-116 Invariant 3) is made explicit.
>
> **Changes in v0.3:** §6.4 added — DGC authenticates as the node SPIFFE workload
> identity, removing the legacy reliance on a captured/last-authenticated user
> `Subject`. Faithful to RR-116's process-identity dirtySet; aligned with STD-003
> / STD-006 §7.1; a least-privilege win (§2.1).
>
> **Changes in v0.6:** §13 added — `MarshalledInstance` integration as a hybrid of
> "B" (pluggable codec via a `ServiceLoader` `MarshalFactory` keyed by `payloadFormat`)
> and "C" (the §7.8 `MarshalledInstanceRecord` becomes `MarshalledInstance`'s serial
> form, with `schemaBytes`/`schemaDigest`/`payloadFormat` first-class). Includes a
> ready-to-apply STD-006 §7.8 amendment (STD-006 lives on another branch). The Phase-5
> der `MarshalledInstanceRecord` folds into the base class + the DER factory.
>
> **Changes in v0.5:** §9.1 added — transition strategy. The JOSS path
> (`writeObject`/`readObject`/`serialPersistentFields`) is RETAINED alongside the
> neutral `@AtomicSerial` path during migration to enable comparative (JOSS-vs-DER)
> testing, and removed only in the end state. `GetArg`/`PutArg`/`SerialForm` still
> go neutral now; `serialPersistentFields` is rebuilt as an *independent*
> `ObjectStreamField[]` (deliberate duplication so the comparison isn't masked).
> Supersedes the spike's destructive deletion of `writeObject`/`serialPersistentFields`.
>
> **Changes in v0.4:** §4.4 rewritten — NO new `AtomicSerialPermission`. The
> `GetArg`/`PutArg` construction guard (`Check`/`SerializablePermission`) is
> *removed*, not replaced (a replacement would just relocate a grant, against §2.1).
> Deserialization is gated solely by `DeSerializationPermission("ATOMIC")` checked
> against each `@AtomicSerial` class's `ProtectionDomain` prior to construction
> (existing JOSS control) plus `check(GetArg)`. Normative: every decode path
> including DER MUST enforce that gate — flagged as a current gap in `ObjectCodec`.

---

## 1. Purpose and Scope

JGDMS-STD-006 introduced a language-neutral DER wire format to replace the
Java Object Serialization Stream Protocol (JOSS) on the wire. That work exposed a
deeper coupling: the `@AtomicSerial` **API contract itself** is still anchored on
`java.io` serialization types — `GetArg extends ObjectInputStream.GetField`,
`PutArg extends ObjectOutputStream.PutField`, `SerialForm extends
ObjectStreamField`, and the `ReadObject`/`@ReadInput` escape hatch that reads a
`java.io.ObjectInput` stream.

This standard defines the **4.0.0 big-bang removal** of that coupling. After 4.0.0:

- the `@AtomicSerial` validation contract depends on **no Java Object Serialization
  type**;
- Java Object Serialization compatibility is **not retained** (no `Serializable`
  interop on the wire or in the API);
- JERI selects an encoding (DER, and in principle others) through the neutral
  `ObjectInput`/`ObjectOutput` boundary and a `WireFormat` method constraint;
- existing on-disk persistence (reliable logs) is **upgraded by a bounded,
  read-only compatibility/conversion layer**, not by retaining a general
  serialization read path.

This standard does **not** change the `@AtomicSerial` *validation semantics*
(`check(GetArg)` before construction; `serialForm()` shape declaration) — only the
types those semantics are expressed in, and the encodings beneath them.

---

## 2. Motivation

### 2.1 The security argument: serialization forces permission grants, and grants are attack surface

The primary reason to remove Java Object Serialization is **not** encoding
aesthetics — it is **least privilege**. Every use of Java Object Serialization
requires the deployment to *grant permissions*, and on DirtyChai (which retains and
advances the Authorization framework precisely to enforce least privilege), each
granted permission is attack surface that weakens the policy's defences.

Concretely:

- Subclassing `GetArg`/`PutArg` requires the codebase to hold
  `java.io.SerializablePermission("enableSubclassImplementation")`
  (`AtomicSerial.Check`). A DER deserializer that is forced to extend
  `ObjectInputStream.GetField` must therefore be *granted a Java-serialization
  permission it has no business needing* — see STD-006 `DerGetArg`, which today
  carries exactly this obligation.
- The Java Object Serialization runtime is guarded by a family of
  serialization-related permissions and object-instantiation channels. Keeping any
  serialization read path alive obliges the policy to keep the corresponding grants
  alive — and a grant that exists "for compatibility" is a grant an attacker can
  aim for.
- DirtyChai's stated mission is to **prevent loading untrusted code, break gadget
  attack chains, and block injection / unauthenticated deserialization targets** by
  whitelisting and denying the permissions an exploit needs. Removing serialization
  removes whole categories of permission grant from every policy — it makes the
  least-privilege baseline strictly smaller.

The rule, stated for this standard: **a 4.0.0 conformant deployment requires no
serialization-related permission grant for normal operation.** Any residual
serialization read path (the persistence upgrade shim, §8) is bounded, local,
trusted, and removable, and MUST NOT require a wire-facing grant.

### 2.2 The structural argument: `Serializable` forces the whole subclass chain; `@AtomicSerial` is per-class opt-in

Java `Serializable` is inherited: once a class is `Serializable`, every subclass is
too, and a non-`Serializable` superclass must supply an accessible no-arg
constructor. `@AtomicSerial` inverts this — it is **per-class opt-in**: only classes
that implement the contract contribute state to the wire, and a non-`@AtomicSerial`
class is invisible to it (STD-006 §3.10).

Carrying `SerialForm extends ObjectStreamField` (and the dual-mode
`serialPersistentFields = serialForm()` idiom) imports the very `Serializable`
inheritance model that `@AtomicSerial` was designed to escape. Removing the
`ObjectStreamField` base removes this contradiction.

### 2.3 Data independence and cross-runtime parity

STD-006 §2.3 establishes that DER + the embedded schema makes data legible without
the originating class or a JVM. An `@AtomicSerial` API that no longer references
`java.io` serialization types completes that picture: the contract a non-JVM
participant must satisfy is expressed entirely in neutral terms.

---

## 3. The Current Coupling (baseline to be removed)

All in `org.apache.river.api.io.AtomicSerial` unless noted. Classified as
**(a) structural** (extends/implements a serialization class),
**(b) referential** (a serialization type appears in a signature), or
**(c) nominal** (naming only, no hard type dependency).

| Element | Coupling | Java-serialization dependency |
|---|---|---|
| `GetArg extends ObjectInputStream.GetField` | (a) | the entire `GetField` abstract surface |
| `GetArg.getObjectStreamClass()` | (a)/(b) | returns `java.io.ObjectStreamClass` |
| `PutArg extends ObjectOutputStream.PutField` | (a) | the entire `PutField` abstract surface |
| `PutArg.output()` | (b) | returns `java.io.ObjectOutput` |
| `PutArg.write(ObjectOutput)` (inherited) | (b) | `java.io.ObjectOutput` |
| `SerialForm extends ObjectStreamField` | (a) | type-code/ordering/`Comparable` of `ObjectStreamField` |
| `ReadObject.read(ObjectInput)` / `@ReadInput` | (b) | `java.io.ObjectInput` (and `AtomicObjectInput extends ObjectInput`) |
| `Check` guard | (b) | `java.io.SerializablePermission("enableSubclassImplementation")` |
| `GetArg implements net.jini.io.ObjectStreamContext` | — | **NEUTRAL** (`net.jini.io`, not `java.io`) — retained |

Blast radius (trunk): **86** classes declare `public static SerialForm[]
serialForm()`; **19** use the dual-mode `serialPersistentFields = serialForm()`
pattern; **14** production sites use `@ReadInput`.

---

## 4. The 4.0.0 API

### 4.1 Neutral base types (normative)

- `GetArg` MUST be a standalone abstract class (supertype `Object`), implementing
  `net.jini.io.ObjectStreamContext`. It MUST NOT extend any `java.io` type.
- `PutArg` MUST be a standalone abstract class implementing
  `net.jini.io.ObjectStreamContext`. It MUST NOT extend any `java.io` type.
- `SerialForm` MUST be a standalone value type carrying `(name : String, type :
  Class)`. It MUST NOT extend `ObjectStreamField`. It MUST implement
  `Comparable<SerialForm>` reproducing the previous field ordering (primitives
  before objects, alphabetical within each group) so schema field order is stable.

### 4.2 Preserved signatures (source compatibility for implementors)

The following MUST remain source-identical so the ~86 implementors need no change:

- `arg.get(name, <primitive> default)` (all primitive overloads)
- `arg.get(name, default, Class type)` (typed object get, with the existing
  type-mismatch → `InvalidObjectException` behaviour)
- `arg.defaulted(name)`, `arg.validateInvariants(...)`, `arg.serialClasses()`,
  `arg.getObjectStreamContext()`
- `putArg.put(name, value)` (all overloads), `putArg.writeArgs()`
- `new SerialForm(name, type)`, `sf.getName()`, `sf.getType()`

### 4.3 Removed from the contract (normative)

- `GetArg.getObjectStreamClass()` — REMOVED from the `@AtomicSerial` contract. The
  ~5 production callers (`ProxySerializer`, `BasicObjectEndpoint`) MUST be migrated
  to a neutral class-name lookup. A JOSS-only implementation MAY retain it as an
  internal detail.
- `PutArg.output()` and the inherited `PutField.write(ObjectOutput)` — REMOVED (see
  §5: once `@ReadInput`-paired raw writes become declared fields, `output()` has no
  callers). *(Implementation note: confirm no other `output()` callers before
  deletion.)*
- `ReadObject` / `@ReadInput` — REMOVED (see §5).

### 4.4 Permission model (normative): no new permission — rely on `DeSerializationPermission`

No new permission (no `AtomicSerialPermission`) is introduced. The
`SerializablePermission("enableSubclassImplementation")` guard in `Check.check()`,
invoked by the protected `GetArg()`/`PutArg()` constructors, is a vestige of the
Java-serialization subclassing model and is **removed**, not replaced. Introducing
a replacement permission would merely *relocate* a required grant, contradicting
§2.1 (every grant is attack surface).

The construction-time guard adds no security over the two controls that already
carry it, so its removal is safe:

- **`DeSerializationPermission("ATOMIC")` — the deserialization gate.** This is
  checked against the `ProtectionDomain` of *each* `@AtomicSerial` class in the
  object's hierarchy, **prior to construction** (today via
  `ObjectStreamClassContainer.deSerializationPermitted(ATOMIC)` in
  `AtomicMarshalInputStream`). Per its own contract it is "checked only against the
  domains representing the class hierarchy of an object about to be de-serialized…
  trusting the classes of the object to check all invariants while reading a stream
  from an untrusted source." Only classes whose PD holds this permission may be
  atomically deserialized; everything else is rejected. A forged or untrusted
  `GetArg` cannot widen this — it can only supply field values for classes that are
  already permitted, and those values are validated by `check(GetArg)`.
- **`check(GetArg)` — invariant validation.** Runs before any field assignment on
  whatever values the `GetArg` supplies, regardless of who produced it.

Because a `GetArg` is only a field-value supplier, guarding *who may construct one*
protects nothing that the target-class gate + `check()` do not already protect.

**Normative for 4.0.0:**

- The `GetArg`/`PutArg` construction permission guard (`Check`) is removed; a
  conforming deployment requires **no** serialization-subclassing permission grant
  to perform `@AtomicSerial` deserialization (§2.1). In particular the DER
  deserializer's codebase needs no such grant.
- **Every decode path (DER included) MUST enforce `DeSerializationPermission("ATOMIC")`
  against the `ProtectionDomain` of each `@AtomicSerial` class in the hierarchy,
  before constructing it** — mirroring the JOSS path. *This is currently a gap in
  the DER engine:* `ObjectCodec.decode()` constructs without the gate and MUST add
  it (cross-ref STD-006). The check is on the class hierarchy's domains only, not on
  the caller stack, so untrusted code may drive the decoder without widening what
  can be instantiated.
- The write side (`PutArg`) is not a deserialization gate; its construction guard is
  also removed. Whether the write path needs any analogous control is **[OPEN]**
  (see §11) — serialization does not instantiate from untrusted input, so likely not.

### 4.5 Decode context (normative)

`net.jini.io.ObjectStreamContext` is the **sole** mechanism by which decode-channel
information (e.g. integrity enforcement) reaches a constructor. `GetArg` IS an
`ObjectStreamContext`; constructors that need such information MUST obtain it via
`arg` (e.g. `MarshalledWrapper.integrityEnforced(arg)`), never via a stream
side-read. *(`ObjectStreamContext` is `net.jini.io`; renaming it to drop the
"ObjectStream" echo is OPTIONAL and cosmetic.)*

---

## 5. Removal of `@ReadInput` / `ReadObject`

`@ReadInput` + `ReadObject.read(ObjectInput)` is the only hook that reads a
`java.io.ObjectInput` stream directly. Analysis of the 14 production uses
classifies each as **A** (trivially expressible as declared fields), **B** (needs a
non-field mechanism), or **C** (legacy-only):

| Use | Class(es) | Disposition |
|---|---|---|
| A (×7) | reggie `AdminProxy`/`RegistrarProxy`/`RegistrarEvent`/`RegistrarLease`/`ServiceLease`, `AbstractLease` | The raw 16-byte `ServiceID` / `long` expiration written after the field block — only existed to avoid JOSS codebase-annotation loss (Sun bug 4745728), irrelevant under DER → **declare as `serialForm()` fields**, drop `@ReadInput`. |
| B-integrity (×2) | `org.apache.river.proxy.MarshalledWrapper`, outrigger `EntryRep` | Read an integrity boolean from the **stream context**, not from bytes → call `integrityEnforced(arg)` via `ObjectStreamContext` (§4.5); drop `@ReadInput` (vestigial). Fixes a latent `(ObjectInputStream)` cast in outrigger; outrigger `EntryRep` also needs a first `serialForm()`. |
| B-persistence (×2) | `FiddlerImpl.RegistrationInfo`, `RegistrarImpl.EventReg` | Read a marshalled listener (service-internal persistence) → **declare the listener as a `MarshalledInstance` field**; drop `@ReadInput`. |
| B-DGC (×1) | `BasicObjectEndpoint` | Captures the raw stream for client-side DGC batch coalescing → replaced by a DER-native callback (§6). |
| C (×1) | `com.sun.jini.proxy.MarshalledWrapper` (deprecated `jini-2.1-compat`) | **Deleted** with the module. |

**Outcome:** `@ReadInput`/`ReadObject` is REMOVED from the API. The only legitimate
non-field need (integrity) is served by the neutral `ObjectStreamContext`; the only
genuine stream need (DGC) is served by §6.

---

## 6. Client-side DGC — DER-native batch callback

Client-side distributed garbage collection is **retained**. Its algorithm is the
Network Objects reference-listing collector (SRC-RR-116), which JERI implements.
The 4.0.0 change is narrow: replace the *one* JOSS-specific local coupling, leaving
the distributed protocol intact.

### 6.1 What is unchanged (the SRC-RR-116 protocol rides DER as ordinary calls)

The following are **unchanged** and require no new mechanism — the `dirty`/`clean`
calls are themselves remote calls that travel over JERI and are therefore
DER-encoded in 4.0.0 like any other call:

- **Set-based reference listing.** The owner of object `O` keeps `O.dirtySet`, the
  *set of client identities* holding a surrogate (not a count) — enabling idempotent
  `dirty`/`clean` and crash recovery (RR-116 §2). Identity is the `wireRep`
  (owner id + per-owner object index); JGDMS uses `Uuid`s, which are never reused
  (RR-116 §2.4 requires non-reuse so premature collection surfaces as a clean call
  failure, never as object confusion).
- **`dirty` on first receipt.** When a process first receives a reference it makes a
  `dirty` call to the owner before creating the surrogate (RR-116 §2.1). Cost: one
  RPC per first-receipt — batched, see §6.2.
- **`clean` on local collection, delayed + batched.** The local collector reclaiming
  a surrogate triggers a `clean` (RR-116 §2.2). `clean` calls are already delayed and
  batched by a cleaning demon in the original design.
- **Sequence numbers** per `(O,P)` order out-of-order `dirty`/`clean`; the
  *strong-clean* rule retains a seqno only after a failed `dirty` (RR-116 §2.3, §2.5).
- **Liveness.** Client termination is detected and the client removed from all dirty
  sets (RR-116 §2.4); JERI uses leased dirty references renewed before expiry (the
  RMI refinement of §2.4). This is unchanged.

### 6.2 What changes (the local batching hook)

Today `BasicObjectEndpoint` keys a `Map<ObjectInputStream, DgcBatchContext>` and
calls `ObjectInputStream.registerValidation(...)` so that all references decoded in
one stream are coalesced into a single batched `dirty` call fired when the stream's
object graph is fully read. Both `Map` key and `registerValidation` are JOSS-only.

4.0.0 MUST provide a **DER-native** equivalent with no `java.io` dependency:

- a per-**decode-unit** identity token (an opaque handle for the current
  request/reply decode, obtainable from the decode context) that keys the
  `DgcBatchContext`; and
- an **end-of-decode-unit completion callback** — the neutral analogue of
  `registerValidation` — invoked exactly once when the decode unit's object graph is
  fully decoded, which flushes the batched `dirty` call.

This preserves the existing batching semantics (one `dirty` RPC per decode unit
rather than per reference) without any Java-serialization type.

### 6.3 Ordering constraint that MUST be preserved (RR-116 Invariant 3)

Batching the `dirty` to end-of-decode is only safe because of the transmit-race
prevention in RR-116 §2.1 / Invariant 3: **the sender keeps `O` in a dirty set
until the receiver acknowledges receipt**, so `O` cannot be collected in the window
before the receiver's batched `dirty` reaches the owner. The acknowledgement is
implicit for an **argument** (the method return is the ack) and explicit for a
**result** (an ack sent when unmarshalling completes). A conformant DER
implementation MUST preserve this ordering: the receiver's batched `dirty` for a
decode unit MUST be issued before that decode unit is acknowledged (i.e. before the
method return for arguments; before the explicit ack for results), and the sender
MUST keep the transmitted object reachable / dirty-set-listed until the
acknowledgement is received.

**[OPEN]** exact placement of the decode-unit token on `GetArg`/the decode context,
the callback-registration API, and where the argument/result acknowledgement is
emitted in the DER request/reply framing.

### 6.4 DGC authenticates as the node SPIFFE workload identity (not a captured Subject)

DGC `dirty`/`clean`/lease calls are infrastructure **between nodes**, not user
operations: they originate on background threads (the cleaning demon, lease
renewal) with no user invocation on the stack. Historically this forced the DGC
layer to authenticate using an ambient or last-authenticated `Subject` — a coupling
that is both a security smell (a DGC call for one user's reference could travel
under another user's, or a stale, captured credential) and unnecessary state to
carry across the asynchronous boundary.

In 4.0.0 this dependency is removed. Each node has its own **SPIFFE workload
identity (SVID)**, which already authenticates the node-to-node mTLS connection. DGC
calls authenticate as **that node workload identity**, never a captured user
`Subject`. This is:

- **Faithful to the algorithm.** RR-116's `O.dirtySet` is a set of *process*
  identities (§6.1), so the natural credential for a `dirty`/`clean` call is the
  calling *node's* identity — which SPIFFE supplies directly. The dirtySet client
  identity SHOULD be the holding node's SPIFFE workload identity.
- **Aligned with the multi-Subject model (STD-003; STD-006 §7.1).** Multiple
  `UserSubject`s multiplex over a single node-authenticated connection; user
  principals travel in the `UserSubjectBlock`, while the workload identity is
  ambient via the connection / `ProtectionDomain` and is never carried per call. DGC
  belongs to that node layer, not the user layer.
- **A least-privilege win (§2.1).** DGC no longer captures, carries, or acts under
  any user `Subject`, so it requires none of the user's permissions; the only
  authority needed is the node's own workload identity.

A conformant 4.0.0 implementation MUST NOT make DGC calls under a captured user
`Subject`; it MUST authenticate DGC calls with the node SPIFFE workload identity.
The captured-Subject plumbing for DGC is removed.

---

## 7. Encoding selection (DER vs other) via the neutral boundary

JERI already returns `ObjectInput`/`ObjectOutput` from
`createMarshalInputStream` / `createMarshalOutputStream`, and `MarshalFactory` /
`MarshalInstanceInput`(`extends ObjectInput`) / `MarshalInstanceOutput`
(`extends ObjectOutput`) provide a neutral pluggability seam for
`MarshalledInstance`. 4.0.0:

- A `WireFormat` `MethodConstraint` (STD-006 §5: `DER` / `ANY`; `JAVA` is removed in
  4.0.0) selects the encoding at the `createMarshal*` boundary and via
  `MarshalFactory`.
- The selected `ObjectInput`/`ObjectOutput` implementation need not be an
  `ObjectInputStream`/`ObjectOutputStream` subclass — the DER implementation is a
  TLV codec that drives `@AtomicSerial` construction via a DER `GetArg` (STD-006).
- **[OPEN]** whether `WireFormat` is per-connection or per-method (affects whether a
  single object graph may be encoded differently on different calls).

---

## 8. Persistence migration (reliable-log compatibility/conversion layer)

Persistent services (fiddler, reggie; also mercury, norm, and any service on
`AbstractJiniService.ServiceLogHandler`) use the shared
`org.apache.river.reliableLog.ReliableLog` + `LogHandler`. On disk: `Version_Number`
/ `Snapshot.N` / `Logfile.N`; format today is **plain JOSS**
(`ObjectOutputStream`/`ObjectInputStream`) for both snapshots and incremental log
records. (Outrigger uses a separate, already-versioned `snaplogstore` and is handled
independently.)

Reading a service's **own** local log is trusted local data, not untrusted wire
input — so a **bounded, read-only** legacy decoder is permitted for upgrade, and
MUST NOT require a wire-facing grant (§2.1).

### 8.1 Conversion approach (normative): read-old → reconstruct → write-new on first boot

`ReliableLog.recover()` rebuilds the live in-memory model, after which both fiddler
and reggie unconditionally call `log.snapshot()`. Because the live model sits
between reader and writer, the snapshot reader and writer need not share a format.
Therefore:

1. On first 4.0.0 boot, recovery detects the on-disk format and, if legacy,
   decodes with the retained read-only JOSS path; the live model is rebuilt exactly
   as before.
2. The post-recovery `log.snapshot()` writes a **fresh DER snapshot**;
   `ReliableLog` atomically swaps files and increments `Version_Number`.
3. Subsequent snapshots and log records are DER; the legacy decoder is never invoked
   again.

`ReliableLog` itself requires NO change. Only each service `LocalLogHandler`
(`snapshot`/`recover`) and the base `LogHandler.writeUpdate`/`readUpdate` change.

### 8.2 Format detection (normative)

The on-disk `LOG_VERSION` int is a hard equality check (it throws before a branch is
possible) and MUST NOT be the discriminator. New DER snapshot/log files MUST carry a
**self-identifying magic prefix**; legacy JOSS snapshots are recognised by the JOSS
stream magic (`0xAC 0xED`). Recovery peeks the leading bytes to choose the decoder.

### 8.3 Retained legacy read path (normative, bounded)

The legacy `readObject` bodies of `RegistrationInfo`, `EventReg`, `SvcReg`, and the
plain-`Serializable` `LogRecord` classes MUST be retained **read-only** for the
one-boot conversion. They are reachable only through the legacy snapshot decoder,
never the wire. They SHOULD be removed in a later release once no deployment carries
pre-4.0.0 logs.

### 8.4 Offline converter (recommended)

In addition to transparent one-boot conversion, an offline tool
(`convert --persist-dir <dir>`) SHOULD be provided that reads the legacy log,
reconstructs the model, writes a DER snapshot, and preserves the originals as
backups — for operators requiring a controlled, reversible migration.

### 8.5 Log records → DER (decision)

The ~16–17 `LogRecord` classes per service are plain `Serializable` and local-only.
**Recommendation:** convert them to `@AtomicSerial`/DER so no `Serializable`
read/write path survives in the running product (a persistent `Serializable` read
path is a latent local gadget vector). **[OPEN]** confirm DER conversion of log
records vs. retaining them as local JOSS.

---

## 9. Backward Compatibility and Blast Radius

- **End state: no Java Object Serialization compatibility is retained.** This is a
  breaking change; the release is **JGDMS 4.0.0** (major version bump). But the JOSS
  path is removed at the *end* of the migration, not during it — see §9.1.
- **Source compatibility:** the ~86 `@AtomicSerial` implementors need no source
  change to their `@AtomicSerial` members (§4.2) — they use only neutral
  `arg.get`/`put`/`serialForm` calls. (The spike found one mechanical exception: a
  JOSS-write helper param `PutField → PutArg`; under §9.1 that helper is instead
  kept on the JOSS side, so the atomic side is genuinely untouched.)
- **Binary compatibility:** any external code that subclasses `GetArg`/`PutArg` or
  uses the removed methods must recompile. **[OPEN]** confirm JGDMS is the sole
  consumer of these types (it is believed to be).

### 9.1 Transition: keep the JOSS path for comparative testing (then remove)

The uncoupling does **not** delete the Java-serialization path up front. During the
migration, dual-mode classes retain their JOSS members **alongside** the neutral
`@AtomicSerial` path so the *same* objects can be round-tripped through both encoders
and the results compared (JOSS vs DER) — the primary validation that the DER path is
behaviourally equivalent before the legacy reference implementation is deleted. This
supersedes the spike's destructive deletion of `writeObject`/`serialPersistentFields`.

Rules during transition (normative for the migration phase):

- `GetArg`, `PutArg`, `SerialForm` **become neutral now** (the uncouple proper) — this
  is unconditional and not deferred.
- A dual-mode class **keeps** its JOSS members: `private void writeObject(ObjectOutputStream)`,
  `private void readObject(ObjectInputStream)`, and `serialPersistentFields`.
  `@AtomicSerial` does not use any of these, so retaining them does not affect the
  `@AtomicSerial`/DER path or its security model.
- `serialPersistentFields` is rebuilt as an **independent** `ObjectStreamField[]`
  (duplicated from the field definitions, NOT `= serialForm()`, since `SerialForm` no
  longer extends `ObjectStreamField`). The duplication is deliberate: independent
  definitions are what make the comparison meaningful — shared code could mask a
  divergence between the two encodings.
- The two encodings therefore use **two parallel field-handlers**: the atomic path
  via `serialize(PutArg)` / `(GetArg)` (neutral `PutArg`/`GetArg`), and the JOSS path
  via `writeObject`/`readObject` (real `ObjectOutputStream.PutField` /
  `ObjectInputStream.GetField`). They can no longer share a helper.
- The retained JOSS path stays gated by `DeSerializationPermission` and is **removed
  in the 4.0.0 end state**, once comparative testing confirms DER↔JOSS equivalence.
- `@AtomicSerial`-only classes (no pre-existing `writeObject`/`serialPersistentFields`)
  gain nothing here — they are already pure `@AtomicSerial`.

**[OPEN]** scope of "dual-mode" for comparative testing: which classes are worth the
parallel handler (likely the wire-critical `-dl`/proxy types and a representative
sample), versus pure-`@AtomicSerial` classes that need no JOSS path at all.

---

## 10. Conformance

A 4.0.0-conformant implementation:

1. Exposes `GetArg`, `PutArg`, `SerialForm` with **no `java.io` serialization
   supertype**; preserves the §4.2 signatures.
2. Requires **no serialization-subclassing permission grant** (no
   `SerializablePermission`, no new `AtomicSerialPermission`) to deserialize; gates
   deserialization solely via `DeSerializationPermission("ATOMIC")` checked against
   each `@AtomicSerial` class's `ProtectionDomain` prior to construction, plus
   `check(GetArg)` (§2.1, §4.4). The DER decode path enforces this same
   `DeSerializationPermission("ATOMIC")` gate.
3. Provides no `@ReadInput`/`ReadObject` and no `PutArg.output()` (§4.3, §5).
4. Sources all decode-channel context from `net.jini.io.ObjectStreamContext`
   (§4.5).
5. Retains client-side DGC via the DER-native callback (§6), preserving the
   RR-116 protocol and the transmit-race acknowledgement ordering (§6.3), and
   authenticates DGC calls with the node SPIFFE workload identity — never a
   captured user `Subject` (§6.4).
6. Selects encoding via the neutral `ObjectInput`/`ObjectOutput` boundary +
   `WireFormat` (§7).
7. Upgrades existing reliable-log persistence via the bounded read-only conversion
   layer (§8), writing only DER going forward, and requiring no wire-facing grant
   for the legacy read path.

---

## 11. Open Questions

1. §4.3 — confirm `PutArg.output()` has no callers other than the `@ReadInput`-paired
   raw writes before deletion.
2. §4.4 — confirm the `PutArg` (write/serialize) side needs no access control once
   its construction guard is removed (serialization does not instantiate from
   untrusted input, so likely none); and confirm there is no non-deserialization
   caller that legitimately depended on the old `enableSubclassImplementation` guard.
3. §6 — DGC decode-unit token placement on the decode context, the
   callback-registration API, and where the argument/result acknowledgement
   (RR-116 Invariant 3) is emitted in the DER request/reply framing.
4. §7 — `WireFormat` per-connection vs per-method; `MarshalledInstance`/
   `MarshalFactory` selection wiring.
5. §8.5 — convert `LogRecord` classes to DER, or retain as local-only JOSS.
6. §8 — scope: mercury, norm, and `AbstractJiniService`-based services share the
   pattern; confirm they are in the 4.0.0 migration set. Outrigger is separate.
7. §9 — confirm no external consumers subclass the affected types (clean major break).

---

## 12. Relationship to Other Standards

| Standard | Relationship |
|---|---|
| JGDMS-STD-001 (@AtomicSerial) | This standard removes STD-001's Java-serialization coupling; the validation semantics (`check(GetArg)`, `serialForm()`) are unchanged. STD-001 should be updated to define `GetArg`/`PutArg`/`SerialForm` as neutral types. |
| JGDMS-STD-006 (DER Wire Format) | STD-006 is the encoding; this standard removes the API coupling that forced the DER `GetArg` to impersonate a `GetField` and to hold a `SerializablePermission`. STD-006 §5 `WireFormat` is the selection mechanism (§7). |
| DirtyChai | The motivation (§2.1) is DirtyChai's least-privilege mission: fewer required permission grants = smaller attack surface. DirtyChai retains the Authorization framework that makes those grants meaningful and enforceable. |

---

## 13. `MarshalledInstance` integration (hybrid: record-as-serial-form + pluggable codec)

`net.jini.io.MarshalledInstance` is the standard container for an object that travels
across JERI or is stored independently of its class. STD-006 §7.8 defines a parallel
`MarshalledInstanceRecord`. Rather than maintain two container types, 4.0.0
**converges them**: `MarshalledInstance`'s serial form *becomes* the §7.8 record, and
the codec is plugged in behind the existing `MarshalFactory` seam.

### 13.1 `MarshalledInstance` carries the §7.8 record as first-class state (from "C")

The base `net.jini.io.MarshalledInstance` (jgdms-platform) gains first-class fields:
`payloadBytes`, `schemaBytes`, `schemaDigest` (32), `payloadFormat`, and an OPTIONAL
`codebaseAnnotation` (the §8 backup URL); it retains `hash` for `equals`/
`java.rmi.MarshalledObject` compatibility. Its `serialForm()` becomes exactly this
shape. **Consequently STD-006's `MarshalledInstanceRecord` ASN.1 module IS the serial
form of `MarshalledInstance`** — the two are one type. The `jgdms-der`
`MarshalledInstanceRecord`/`MarshalledInstanceCodec` (Phase 5) fold into the base
class's fields plus the DER `MarshalFactory` (below).

Making the schema first-class (not buried inside `payloadBytes`) realises §7.8's
"embed the schema unconditionally" as a *structural* property and exposes
`schemaDigest` for the §12.4 fast-path comparison without parsing the payload.

### 13.2 Pluggable codec via a schema-aware `MarshalFactory` + `ServiceLoader` SPI (from "B")

Encode/decode is delegated to a `MarshalFactory` discovered via
`java.util.ServiceLoader`, keyed by `payloadFormat`. `jgdms-der` registers a DER
factory (`META-INF/services`); the built-in JOSS factory remains for the §9.1
transition. `MarshalledInstance.get(...)` reads its OWN `payloadFormat` field, looks up
the matching factory, and decodes using `payloadBytes` + `schemaBytes`. The codec is
never compiled into platform; additional formats (CBOR, …) plug in without changing
`MarshalledInstance`. (This realises the existing `// TODO: ServiceProvider for
MarshalFactory` in `MarshalledInstance`.)

### 13.3 Dependency direction (the constraint that shapes the design)

`jgdms-der` depends on `jgdms-platform`, so `MarshalledInstance` MUST NOT reference
the DER codec. The **neutral** seam interfaces (`MarshalFactory`,
`MarshalInstanceOutput`, `MarshalInstanceInput`) live in `jgdms-platform` and are
**widened to be schema-aware**: the output yields `(payloadBytes, schemaBytes,
schemaDigest, payloadFormat)`; the input consumes them. `jgdms-der` *implements* these.
Platform defines the contract; der provides the DER implementation; no cycle.

### 13.4 Correctness, transition, equality

- **No recursion / no chicken-and-egg.** `MarshalledInstance`'s own `@AtomicSerial`
  fields are plain `byte[]`/`String`, so *its* serialization uses the core codec
  directly. The contained object is already encoded into `payloadBytes` by the factory
  at construction — the outer instance just carries bytes (tidies the §7.6
  "MarshalledObject nests the wire format" note).
- **Transition (§9.1).** `payloadFormat` also discriminates the legacy path: a JOSS
  factory is registered too (format = JOSS/JAVA, `schemaBytes` empty); only the DER
  format populates `schemaBytes`/`schemaDigest`. Comparative testing = build a JOSS and
  a DER `MarshalledInstance` of the same object and diff `get()`.
- **Equality.** `equals`/`hashCode` remain over `payloadBytes` + `hash` (schema, format
  and `codebaseAnnotation` excluded — exactly as `locBytes` is excluded today).
- **Bootstrap (§7.8).** The embedded schema travels with the instance, so a
  `ServiceRegistrar` proxy arriving in a `MarshalledInstance` is self-decodable before
  the schema registry / `SchemaAccessor` are available.

### 13.5 Proposed STD-006 §7.8 amendment (ready to apply where STD-006 lives)

> *(STD-006 currently lives on a different branch; apply this to its §7.8.)*
>
> Add to §7.8: "`MarshalledInstanceRecord` is the serial form of
> `net.jini.io.MarshalledInstance`; the two are the same wire type. `payloadBytes`,
> `schemaBytes`, `schemaDigest` and `payloadFormat` are first-class fields of
> `MarshalledInstance`. The codec that produces/consumes `payloadBytes`+`schemaBytes`
> is selected at runtime by `payloadFormat` via a `MarshalFactory` obtained from
> `java.util.ServiceLoader` (the JGDMS-STD-006/DER factory is provided by the DER
> module). The decoder MUST use the embedded `schemaBytes` to populate `GetArg`
> (§7.8 normative), comparing `schemaDigest` to the receiver's current
> `serialForm()` digest only for the §12.4 fast-path. No codebase annotation appears
> except the OPTIONAL `codebaseAnnotation` backup URL (§8)."

### 13.6 Cost and open items

- **Platform change (4.0.0-breaking, already planned):** new `MarshalledInstance`
  fields + `serialForm()`; widen the `MarshalFactory`/`MarshalInstanceOutput`/
  `MarshalInstanceInput` contracts to be schema-aware; `ServiceLoader` registration;
  `locBytes` → OPTIONAL `codebaseAnnotation`.
- **[RESOLVED in §13.7]** the exact widened `MarshalInstanceOutput`/`Input` method shape;
  whether `payloadFormat` is an enum, OID, or string; and the `ServiceLoader`
  lookup/caching policy.

### 13.7 Implementation (4.0.0) — RESOLVED & BUILT 2026-06-14

Implemented on branch `der-wireformat-std006` (platform + `jgdms-der`). The [OPEN]
items are resolved as:

- **`payloadFormat` = `String`** (e.g. `"JOSS"`, `"JGDMS-STD-006/DER"`). A human-readable
  string is extensible without a platform enum and avoids OID-registry overhead; an
  OID/enum mapping can be layered later. `MarshalledInstance.FORMAT_JOSS` is the reserved
  built-in default.
- **Widened seam via DEFAULT methods (non-breaking).** `MarshalInstanceOutput` gains
  `default byte[] getSchemaBytes()` / `getSchemaDigest()` / `String getPayloadFormat()`
  (JOSS defaults: empty / empty / `FORMAT_JOSS`), reported AFTER `writeObject`/`flush`
  and captured by `MarshalledInstance.marshal(...)`. `MarshalFactory` gains a `default`
  9-arg `createMarshalInput(objIn, locIn, schemaBytes, schemaDigest, payloadFormat, …)`
  delegating to the legacy 6-arg form; schema-bearing codecs override it. All three
  existing implementors (JOSS, Atomic, DER) compile via the defaults; only DER overrides.
- **`MarshalledInstance` serial form** is now `payloadBytes, codebaseAnnotation,
  schemaBytes, schemaDigest, payloadFormat, hash` (6 fields). `schemaBytes`/`schemaDigest`
  are never null (empty for JOSS); `equals`/`hashCode` remain over `payloadBytes`+`hash`.
  `java.rmi.MarshalledObject` conversion is guarded to JOSS (throws otherwise).
  Finalizer-attack safety preserved: all encoding happens in the static `marshal(...)`
  returning a `Marshalled` holder, assigned by one private constructor.
- **`ServiceLoader` policy.** `getMarshalFactory()` defaults to
  `factoryForFormat(payloadFormat)`: JOSS → built-in factory; otherwise a
  `MarshalFactoryProvider` discovered by `ServiceLoader` keyed by `payloadFormat`, cached
  once in a lazy static map. Subclasses overriding `getMarshalFactory()` (e.g.
  `AtomicMarshalledInstance`) bypass the lookup. `jgdms-der` registers
  `DerMarshalFactoryProvider` via `META-INF/services/net.jini.io.MarshalFactoryProvider`.
  Deferred: per-classloader provider caching for multi-app containers; whether the SPI
  lookup should be gated by a `DeSerializationPermission`.
- **Schema is first-class as a SEPARATE field** (not buried in `payloadBytes`): the DER
  output writes only the object payload to `objOut` and reports `schemaBytes` separately;
  the DER input decodes `payloadBytes` against `schemaBytes` and RE-DERIVES the leaf
  digest from the embedded chain (the top-level `schemaDigest` is an unverified fast-path
  hint, never trusted for decoding — the embedded schema is authoritative, §7.8).
- **`DerMarshalledInstance` no longer overrides `getMarshalFactory()`** — it is a thin
  public bridge to the protected schema-aware constructor; decode is by `payloadFormat`
  via ServiceLoader, so a base `MarshalledInstance` carrying DER state decodes with NO
  subclass.

Verified: `mvn -o -pl jgdms-platform,jgdms-der test` → platform 244 + der 279 green.
Dependent modules (reggie/outrigger/mercury/etc., 43 callers) use the public API only and
still compile; full runtime verification of those is via the qa suite (not run here — this
branch is isolated and does not touch dist artifacts).

## 14. Surface 2: JERI in-band `MarshallingFormat` enforcement — SCOPE (not yet built)

Surface 1 (sec.13) made a `MarshalledInstance` honour `MarshallingFormat`. Surface 2 is
enforcing the format for the **in-band remote call itself** — the method + arguments
(client→server) and the return value (server→client) that travel over JERI. This section
scopes that work; it is grounded in the current call path and is **not yet implemented**.

### 14.1 Where the call is marshalled (current code)

- **Client** `net.jini.jeri.BasicInvocationHandler` (jgdms-jeri):
  - constraint check loop — `BasicInvocationHandler.java:894-926`: walks the request's
    *unfulfilled* requirements; recognises only `Integrity.YES` and
    `AtomicInputValidation.YES`; **any other requirement throws
    `UnsupportedConstraintException`** (line 902-904). So a `MarshallingFormat.DER`
    requirement is REJECTED today.
  - wire framing — `:962-974`: writes a **marshalling-protocol-version byte**
    (`0x00` legacy / `0x01` atomic / `0x02` ACC+Subjects) + integrity + atomicValidation
    bytes to the RAW request stream, BEFORE the marshal stream wraps it.
  - stream creation — `createMarshalOutputStream` `:1213-1238` returns a
    `MarshalOutputStream` (JOSS); then `marshalMethod` + `marshalArguments` (`:986-989`)
    write the method identifier and each argument via `ObjectOutput.writeObject`.
- **Server** `net.jini.jeri.BasicInvocationDispatcher`:
  - reads the version byte, determines `atomicValidation`, checks constraints
    (`:545-554`, `:874-899`; unsupported → `unsupportedConstraint(...)`);
    `createMarshalInputStream` `:1055-1080` returns a `MarshalInputStream`.
- **Capability declaration** `net.jini.jeri.ServerCapabilities.checkConstraints` — the
  transport layer states which requirement *aspects* it implements and returns the rest
  for higher layers. Its javadoc names `Integrity` and `AtomicInputValidation` as the
  only constraints partly handled above the transport. `MarshallingFormat` would join
  them as a **higher-layer (invocation-layer) constraint** the transport passes through.

### 14.2 The established template (how atomic serialization is selected)

Format selection is done by a **handler/dispatcher subclass pair**, configured by an
`InvocationLayerFactory` (ILFactory):
- `AtomicInvocationHandler extends BasicInvocationHandler` overrides
  `createMarshalOutputStream` → `AtomicMarshalOutputStream` and
  `createMarshalInputStream` → `AtomicMarshalInputStream` (`AtomicInvocationHandler.java:159-313`).
- `AtomicInvocationDispatcher extends BasicInvocationDispatcher` is the server peer.
- `AtomicMarshalOutputStream extends MarshalOutputStream` (which extends
  `ObjectOutputStream`) and `AtomicMarshalInputStream extends MarshalInputStream` — i.e.
  **full object-graph streaming codecs** (arbitrary types, arrays, references, cycles,
  null) with atomic validation layered on.

A DER call path would mirror this: a `Der{InvocationHandler,InvocationDispatcher}` pair
+ a `Der*ILFactory`, swapping in DER streams.

### 14.3 The central gap: DER has no streaming codec

This is the bulk of the work and the reason Surface 2 is large. The DER codec built in
STD-006 / sec.13 encodes a **single `@AtomicSerial` object** (object → schema chain →
DER record). The in-band path needs an `ObjectOutput`/`ObjectInput` that streams a
**method identifier followed by an arbitrary sequence of argument objects**, with the
full semantics `ObjectOutputStream`/`AtomicMarshalOutputStream` provide:
back-references/cycles, nulls, arrays, enums, the value types that appear in proxy
method signatures, and nested graphs. **No DER equivalent of
`MarshalOutputStream`/`MarshalInputStream` exists.** Building
`DerMarshalOutputStream`/`DerMarshalInputStream` is a substantial new component —
effectively a STD-006 "streaming profile" (a new phase/spec), not constraint glue.

### 14.4 Decomposition

- **Part A — constraint plumbing (small, mechanical).** Recognise `MarshallingFormat` in
  the handler/dispatcher constraint loops (treat like `AtomicInputValidation`, do not
  reject); pass it through `ServerCapabilities`/endpoint as a higher-layer constraint;
  decide the wire indicator (sec.14.5); add the `Der*` handler/dispatcher/ILFactory pair.
  **Alone, Part A can only fail-secure** (reject a required format the configured
  transport can't provide) and accept `JOSS` — it cannot *do* DER without Part B.
- **Part B — the DER streaming codec (large, the real work).**
  `DerMarshalOutputStream`/`DerMarshalInstanceInput`-style streams implementing the
  `ObjectOutput`/`ObjectInput` contract over DER, plus method-identifier marshalling.
  This is where the effort, the wire-format design, and the security review concentrate.

### 14.5 Decisions for Peter (these shape Part B and the wire)

1. **Graph model (the big one).** Full arbitrary-graph DER (a DER clone of
   `ObjectOutputStream`, reintroducing much of its complexity/attack surface), **or**
   restrict in-band DER to `@AtomicSerial`-validated types + a defined value-type set
   (primitives/arrays/String/enum/known core types) — rejecting non-`@AtomicSerial`
   arguments. The latter is bounded and aligned with the 4.0.0 thesis (everything on the
   wire is `@AtomicSerial`-validated; no Java Serialization). **Recommendation:
   `@AtomicSerial`-restricted.** Consequence: only services whose method signatures use
   `@AtomicSerial`/value types can be exported DER-only.
2. **New pair vs extend Atomic.** `DerInvocationHandler extends BasicInvocationHandler`
   (clean) vs `extends AtomicInvocationHandler` (reuse compression/ACC/Subject framing).
   Recommendation: extend Basic, reuse the sec.13 codec; revisit reuse later.
3. **Wire indicator.** Rely on the paired configuration (a `DerInvocationDispatcher`
   knows its format) vs allocate a new marshalling-protocol-version byte (e.g. `0x03`)
   for negotiation/mixed deployments. Recommendation: paired config first; reserve a
   version byte if negotiation is later required.
4. **References/cycles.** Whether the DER streaming profile supports back-references
   (needed for cyclic graphs / shared subobjects). Recommendation: support shared/cyclic
   references via an explicit handle table in the streaming profile (correctness).
5. **Return-value path.** Same codec for the reply stream (server→client); confirm symmetry.

### 14.6 Risks

- **Touches the jini network-protocol path your qa exercises** — Part A edits
  `BasicInvocationHandler`/`Dispatcher`/`ServerCapabilities`. Needs a coordinated qa run;
  not isolatable the way sec.13 was.
- **Wire-format design + security review** for Part B (a new deserialization surface; must
  preserve fail-secure / size-before-allocate / atomic construction like STD-006).
- **Magnitude**: Part B is comparable to the original STD-006 codec effort.

### 14.7 Suggested phased plan (each phase independently verifiable where possible)

1. **A0 (isolated, safe):** `DerInvocationHandler`/`Dispatcher`/`ILFactory` skeleton that
   recognises `MarshallingFormat` and, lacking the codec, **fails-secure** (required DER →
   `UnsupportedConstraintException`; absent → delegate to super). Unit-testable without
   the network; proves the constraint is honoured end-to-end at the handler level.
2. **B1:** `DerMarshalOutputStream`/`InputStream` streaming profile for the chosen graph
   model (sec.14.5#1) — method id + `@AtomicSerial`/value-type arguments, handle table.
   Tested in isolation (round-trip method+args byte-buffers), no network.
3. **B2:** wire B1 into the `Der*` handler/dispatcher; loopback `ObjectEndpoint` test
   (in-memory, no sockets/dist) exercising a real call round-trip under
   `MarshallingFormat.DER`.
4. **A1 (coordinated):** the `BasicInvocationHandler`/`Dispatcher`/`ServerCapabilities`
   constraint-loop edits; verified against your qa suite (your go-ahead + run).

### 14.8 Test strategy

Isolated (no network/dist): codec round-trips (B1); handler fail-secure (A0); loopback
`ObjectEndpoint` call round-trip (B2). Coordinated: qa over the constraint-loop edits (A1).
Mirror the sec.13 discipline — discriminating tests (prove DER actually drove the call,
not a JOSS fallback) and independent re-runs.

### 14.9 Recommendation

Start with **A0 + B1** (both isolatable and safe): they prove the handler honours the
constraint and that a DER streaming codec round-trips method+arguments, with zero touch to
the shared JERI classes or qa. Hold **A1** (the `BasicInvocationHandler`/`Dispatcher`
edits) for a coordinated session against your qa. Resolve sec.14.5#1 (graph model) before
B1 — it determines the codec's size and security surface.

## 15. B1 — DER Object Stream wire format and codec (DECIDED, building)

Peter's decisions (2026-06-14): **(#1) `@AtomicSerial`-restricted graph model**, and the
serialization layer is a **configuration concern** — a service that already uses
`@AtomicSerial` switches to DER by swapping its `InvocationLayerFactory` (no code change).
That maps A0+B1 onto the handler/dispatcher/ILFactory pattern WITHOUT editing the shared
`Basic*` constraint loops (A1 stays deferred): selecting the `Der` ILFactory IS the choice
of format.

### 15.1 Integration model — implement the `ObjectOutput`/`ObjectInput` INTERFACES

The base JERI contract is expressed entirely in terms of the interfaces, so the DER streams
implement `java.io.ObjectOutput` / `java.io.ObjectInput` **directly — NOT** subclasses of
`ObjectOutputStream`/`ObjectInputStream`:
- `BasicInvocationHandler.createMarshalOutputStream` returns `ObjectOutput` (:1213),
  `createMarshalInputStream` returns `ObjectInput` (:1287);
- `BasicInvocationDispatcher.createMarshalOutputStream` returns `ObjectOutput` (:1138),
  `createMarshalInputStream` returns `ObjectInput` (:1054);
- the marshal/unmarshal helpers consume the interfaces (`marshalMethod(..., ObjectOutput, ...)`
  :1379; `unmarshalArguments(..., ObjectInput, ...)` :1416).

`AtomicInvocationHandler` only narrows its overrides to `ObjectOutputStream`/`ObjectInputStream`
(covariant return) because it reuses the `ObjectOutputStream`-based atomic streams; the DER
codec has no such reason. Implementing the interfaces avoids all `ObjectOutputStream`
override-mode machinery (no protected no-arg superctor, no `writeObjectOverride`/
`readObjectOverride`, no inherited Java-Serialization stream state, no accidental stream
header) and is cleaner-aligned with the 4.0.0 "no Java Serialization" thesis.
`Der{InvocationHandler,InvocationDispatcher}` (extending the Basic classes) override
`createMarshal*Stream` to return these. Package `au.net.zeus.jgdms.der.stream`.

### 15.2 What rides which channel (ObjectOutput contract)

- **Primitives** go through the TYPED methods (`writeInt`/`readInt`, `writeBoolean`, …) —
  the JERI dispatcher reads arguments using the method's declared parameter types, so
  primitives are not self-describing. Encoded directly with STD-006 DER primitives:
  boolean→BOOLEAN, byte/short/int/long→INTEGER (range-checked, `WireTypes`),
  `writeUTF`/String→UTF8String, `write(byte[])`→OCTET STRING.
  `float`/`double`/`char` are **deferred in STD-006 (S7.6)** → throw
  `UnsupportedOperationException` for now (gap; a service using them can't go DER-only yet).
- **Objects** go through `writeObject`/`readObject` (self-describing). Each is one
  DER-tagged item (a CHOICE keyed by context tag):
  - `[0]` NULL — null reference.
  - `[1]` an `@AtomicSerial` object: the sec.13 `MarshalledInstanceRecord` (embedded schema
    + payload) — self-describing class + data-independent decode. Construction MUST validate
    `@AtomicSerial` (reuse `SchemaGenerator`/`ObjectCodec`); a non-`@AtomicSerial`,
    non-value object is **rejected** (`UnsupportedOperationException` / fail-secure — the
    restricted model).
  - `[2]` (reserved, NOT used) — there are no back-references in the grammar; a `[2]` tag is
    **rejected** fail-secure (sec.15.3).
  - `[3]` String (UTF8String), `[4]` a boxed primitive, `[5]` `byte[]` (OCTET STRING).
  - (later) `[6]` array, `[7]` enum.

### 15.3 No handle table — pure value-tree, deterministic, no cycles (security)

**Decided (Peter, 2026-06-14): no handle table, no back-references, no cycles.** Two reasons,
both decisive:

1. **Determinism / canonicalisation.** DER's value is a canonical encoding — one byte
   sequence per *value*. A handle table makes the encoding depend on object *identity*
   (whether two references are `==`) and on traversal order, so the same value could encode
   differently and equal graphs could diverge. That breaks value-equality of the wire form,
   which `MarshalledInstance.equals` / `schemaDigest` / any signing rely on. Without it, the
   stream is a deterministic function of the argument **values**.
2. **It buys nothing (and removing it shrinks attack surface).** `@AtomicSerial`
   deserialisation defensively **copies** mutable inputs and re-checks invariants per object,
   so shared mutable identity is never preserved across the boundary anyway (a deliberate
   security property). Preserving identity on the wire would be a false promise.

Therefore every object occurrence is encoded **in full, by value** (a pure tree). Reference
cycles are not representable (no back-reference exists to close a loop), and a
back-reference-style tag (`[2]`) is rejected fail-secure. This also removes the
partially-constructed-object exposure a cycle would otherwise require — the same hazard
`@AtomicSerial` exists to eliminate.

### 15.4 Method identifier

`BasicInvocationHandler.marshalMethod`/dispatcher `unmarshalMethod` write/read the method
identifier through the same `ObjectOutput`/`ObjectInput`. B1 must therefore support whatever
those use (typically a `long` method hash via `writeLong`/`readLong`, or a `String`); inc-1
supports both. Confirmed during B2 (loopback), not inc-1 (isolated).

### 15.5 Components and increments

Package `au.net.zeus.jgdms.der.stream`:
- `DerMarshalOutputStream implements java.io.ObjectOutput` /
  `DerMarshalInputStream implements java.io.ObjectInput` (plain interface implementations).
- `DerObjectStreamCodec` (engine: item tags, primitive read/write, handle table; reuses
  `DerWriter`/`DerReader`, `MarshalledInstanceRecord`/`ObjectCodec`).

**Increment 1 (DONE, isolated):** primitives (boolean/byte/short/int/long/String/
byte[]; float/double/char throw), `writeObject`/`readObject` for null + value objects +
flat `@AtomicSerial` objects; **NO handle table — pure value-tree, deterministic** (every
occurrence encoded in full; back-reference tag rejected; sec.15.3); round-trip tests against
a `ByteArrayOutputStream` (no JERI, no network). **Increment 2:** nested `@AtomicSerial` object **trees** (NO cycles — sec.15.3);
requires extending the STD-006 per-object codec (`ObjectCodec`/`SchemaGenerator`/wire-type
mapping) to encode object-typed fields, which today handle value types only. **Increment 3:**
arrays/enums; revisit float/double/char. **Then A0/B2:**
`Der{InvocationHandler,InvocationDispatcher}` + `DerILFactory` wiring + loopback
`ObjectEndpoint` round-trip (works on the flat model already built).

## 16. B1 inc-2 — nested `@AtomicSerial` object fields (design)

Today the per-object codec encodes only value-typed fields (boolean/byte/short/int/long/
String/byte[]); a field whose type is itself an `@AtomicSerial` object is rejected by
`SchemaGenerator.toWireType`. inc-2 lifts that to support **nested `@AtomicSerial` object
fields**, encoded as a **value-tree** (no cycles, no shared back-references — sec.15.3).

### 16.1 Where it plugs in (current code)

- `SchemaGenerator.toWireType(Class, Class)` — maps a field's Java type to a wireType
  string; throws for any object type other than String/byte[].
- `ObjectCodec.encodeValue(Object, wireType, name)` — switch on wireType -> DER primitive.
- `getarg.WireTypes.decode(DerReader, wireType)` + `getarg.DerFieldStore` — the decode side,
  value-types only.

### 16.2 Design

- **Self-describing nested encoding (polymorphism + data-independence).** A nested field's
  value must carry its OWN schema, because the field's declared type may be a supertype of
  the runtime value's class and because S3.11 data-independence requires the decoder not to
  depend on loading the originating class blindly. So a nested object field is encoded as a
  small self-describing record: `SEQUENCE { schemaChainBytes, payloadBytes }` where
  `payloadBytes = ObjectCodec.encodeHierarchy(value, generateChain(value.getClass()))` and
  `schemaChainBytes` is that chain encoded. Decode: read the SEQUENCE, parse the chain, then
  `decodeHierarchy(declaredFieldType, chain, payloadBytes)` — `decodeHierarchy` already does
  the assignability check (runtime class must be assignable to the declared field type).
- **NO module cycle.** `der.marshal.MarshalledInstanceRecord` already depends on
  `der.object`; therefore `der.object` MUST NOT use it. The nested record above is built from
  `der.object` (`ObjectCodec`, `SchemaChain`) + `der.schema` only. (A future refactor could
  hoist a shared "self-describing object" codec into `der.object`/`der.schema` and let
  `MarshalledInstanceRecord` build on it; for inc-2 keep a minimal nested-record codec in
  `der.object` to avoid the cycle.)
- **wireType marker.** `toWireType` returns a distinguished wireType for an `@AtomicSerial`
  field type — e.g. the literal `"@AtomicSerial"` (the runtime class travels in the embedded
  schema, so the marker need not name the class) or the declared class name. `encodeValue`
  and the decode side branch on this marker to the nested-record path.
- **null fields.** A null nested field encodes as DER NULL (`0x05 00`); decode returns null.
  (Value-typed fields remain non-null per current behaviour.)
- **No cycles / determinism (sec.15.3).** Pure tree: a nested value is encoded in full; there
  is no handle table and no back-reference, so a cycle cannot be expressed. Add a **recursion
  depth bound** (configurable, fail-secure `DerException` when exceeded) so a hostile deeply
  -nested stream cannot exhaust the stack — a deserialisation-DoS guard.
- **check-before-construction preserved.** Nested decode goes through `decodeHierarchy`, so
  the nested object's `check(GetArg)` runs and it is fully validated+constructed before being
  handed to the outer object's `GetArg` (consistent with @AtomicSerial copy semantics).

### 16.3 Touch list / tests

- `SchemaGenerator.toWireType`: recognise `@AtomicSerial` field types -> nested marker.
- `ObjectCodec.encodeValue` (+ a private `encodeNested`/`decodeNested`): the nested-record
  path; depth bound.
- `getarg.WireTypes` + `getarg.DerFieldStore`: decode the nested marker via the nested-record
  path (needs access to `ObjectCodec.decodeHierarchy` — mind package layering: the nested
  decode may belong in `der.object` with `DerFieldStore` delegating to it).
- The `der.stream` inc-1 codec then handles nested graphs automatically (a nested
  `@AtomicSerial` arg is just one `[1]` record whose payload now contains nested records).
- Tests: nested round-trip (outer holding an inner `@AtomicSerial`); polymorphic field
  (declared supertype, runtime subtype) drives decode via embedded schema; null nested field;
  declared-type assignability violation rejected; depth-bound DoS guard rejects over-deep
  nesting; determinism (same value -> same bytes). Build the 3-module reactor.

## 17. B1 inc-3 — enums, arrays (design)

inc-3 lifts the value-type vocabulary to cover **enums** and **arrays** (primitive,
`String[]`, and `@AtomicSerial[]`). Same principles as before (sec.15.3): pure value-tree,
deterministic, no cycles, fail-secure, `@AtomicSerial`-restricted for object elements.

### 17.1 Enums

- **Encoding by NAME, not ordinal.** Ordinal is fragile across versions (reordering enum
  constants silently changes meaning — a real correctness/security hazard). Name is stable;
  a removed constant is rejected fail-secure on decode. Determinism is trivial.
- **wireType marker.** `SchemaGenerator.toWireType` returns `"enum:<className>"` so the
  schema records the declared enum class (needed for the `Enum.valueOf` decode).
- **Wire.** A DER UTF8String holding the constant name (e.g. `Color.RED` → `"RED"`). A null
  enum field encodes as DER NULL (`0x05 0x00`), consistent with nested `@AtomicSerial`.
- **Decode.** Read UTF8String → `Enum.valueOf(enumClass, name)`. An unknown name (the enum
  has no such constant on this receiver) throws `IllegalArgumentException` from `valueOf`,
  which is wrapped as `DerException` — fail-secure, never silently default.
- **`enumClass` resolution.** The enum class is loaded by name from the
  `"enum:<className>"` schema marker via the same `loadClass` helper used elsewhere
  (`Thread.currentThread().getContextClassLoader()`); cached per stream is OPTIONAL.

### 17.2 Arrays

A unified wire shape regardless of element type: **the array is a DER SEQUENCE containing
one TLV per element, in array-index order**. A null array encodes as DER NULL. Determinism
is trivial (positional encoding). Length-bound: relies on STD-006's size-before-allocate
discipline at the underlying DER reader (no separate cap needed at this layer).

- **wireType marker.** `SchemaGenerator.toWireType` returns `"array:<componentWireType>"`
  for an array type, where `<componentWireType>` is the recursive `toWireType` of the
  element class — e.g. `"array:int"`, `"array:java.lang.String"`,
  `"array:@AtomicSerial"`, `"array:enum:com.example.Color"`.
- **Primitive arrays** (`boolean[]`/`short[]`/`int[]`/`long[]`): SEQUENCE of the
  element-type's natural DER primitive (BOOLEAN/INTEGER, range-checked per element).
  `byte[]` is UNCHANGED — remains OCTET STRING (the existing mapping); it is NOT promoted
  to `"array:byte"` for compactness and back-compat.
- **`String[]`**: SEQUENCE of (UTF8String | DER NULL) — per-element nullability supported
  by tag-peeking on read.
- **`@AtomicSerial[]`**: SEQUENCE of (nested-record SEQUENCE{schemaBytes, payloadBytes}
  | DER NULL) — each element uses the same nested encoding as a single `@AtomicSerial`
  field (`ObjectCodec.encodeNested`), with the same depth bound, **per-element**. Each
  element's runtime class is carried in its own embedded schema (polymorphism per element).
- **No multi-dimensional arrays yet.** `int[][]` is `"array:array:int"` if we extend
  `toWireType` to recurse, but inc-3 caps at one level — multi-dim is inc-4 if a real
  service needs it. State the restriction in code (fail-secure on `array:array:...`).
- **null array** vs **empty array.** Distinct: null encodes as DER NULL; empty encodes as
  a SEQUENCE of length 0. Decoders return `null` vs `new T[0]` accordingly.

### 17.3 Float / double / char — S7.6 deferral LIFTED (strict canonicalization)

STD-006 §7.6 deferred these types. **Lifted 2026-06-14** (Peter, after the
Entry-matching-determinism discussion: a `MarshalledInstance`'s wire bytes serve as the
Entry-matching key, so any non-canonical encoding lets a hostile sender or a benign
platform variation defeat template matching — *even within Java*).

The wire **must be byte-identical for the same value across all languages and senders**.
That demands **strict canonicalization on the encoder AND strict rejection of non-canonical
patterns on the decoder** (a lenient decoder doesn't help cross-sender matching since
Entry matching is on transmitted bytes, not on decode/re-encode).

#### 17.3.1 `float` / `double` — IEEE-754 in OCTET STRING

- **Wire:** OCTET STRING of exactly 4 bytes (`float`) or 8 bytes (`double`), big-endian,
  IEEE-754. A different length is rejected fail-secure (`DerException`).
- **Schema wireType:** `"float"` / `"double"` (matching the existing short-name convention).
- **Canonical NaN** (MUST be emitted; non-canonical NaN bit patterns MUST be rejected):
  - `float`: `0x7FC00000` (sign=0, exp=`0xFF`, mantissa MSB=1, rest=0).
  - `double`: `0x7FF8000000000000` (same shape, 64-bit).
  - This is the quiet-NaN bit pattern every mainstream language produces by default
    (Java `Float.NaN` / Rust `f32::NAN.to_bits()` / C `NAN` macro all match), so no peer
    pays a conversion cost in the common case.
- **Canonical zero:** `+0.0` (`0x00000000` / `0x0000000000000000`) is the wire form;
  `-0.0` bits (`0x80000000` / `0x8000000000000000`) MUST be rejected on decode. Encoders
  MAP `-0.0` to `+0.0` on encode (consistent with Java's `==` semantics for zero).
- **Infinity** (`0x7F800000` / `0xFF800000`; resp. 64-bit) is unique by construction — no
  canonicalization needed; both `±∞` are accepted.
- **Subnormals** are each unique values — no canonicalization needed.

#### 17.3.2 `char` — Unicode codepoint as INTEGER

- **Wire:** DER INTEGER carrying the Unicode codepoint.
- **Schema wireType:** `"char"`.
- **Range:** `[0, 0xFFFF]` with surrogate range `[0xD800, 0xDFFF]` excluded. A `char` is a
  BMP non-surrogate codepoint. Supplementary-plane characters (`> 0xFFFF`) don't fit in a
  Java `char` and MUST be carried in a `String` field. Surrogate code units (which Java
  *permits* in `char` but which are *not* valid Unicode codepoints) are rejected at the
  encoder (the wire is clean — the leaky abstraction stays on Java's side).
- DER INTEGER is already canonical (minimum-length two's complement, no leading zero
  bytes), so no extra canonicalization is needed beyond the range check.

#### 17.3.3 Security review items (MUST be tested as discriminating wire invariants)

For each lifted type, the wire invariant is enforced *symmetrically* — encoder produces
the canonical form, decoder rejects everything else. The test suite MUST cover, per type:

1. Canonical pattern accepted (positive control).
2. **Non-canonical NaN bit pattern rejected** on decode (the headline guarantee).
3. `-0.0` bits rejected on decode; `-0.0` Java value canonicalized to `+0.0` on encode
   (so a `-0.0` field and a `+0.0` field produce byte-identical wire forms).
4. Wrong OCTET STRING length rejected on decode (3 bytes for `float`, 7 bytes for
   `double`, etc.).
5. Surrogate codepoint rejected on encode and on decode.
6. Determinism: encoding the same value twice yields byte-identical bytes; encoding
   distinct-but-equal values (e.g. two `Float.NaN`s; `+0.0` and `-0.0`) also yields
   byte-identical bytes.

These are discriminating tests — they fail if a future implementer silently relaxes
canonicalization on either side.

### 17.4 Touch list

- `SchemaGenerator.toWireType`: recognise `enum` types → `"enum:<className>"`; recognise
  array types → `"array:<componentWireType>"` (one level only; multi-dim rejected); reject
  `array:array:...` and `array:byte` (the latter ambiguous w/ existing `byte[]` mapping).
- `ObjectCodec.encodeValue` (+ `encodeEnum`, `encodeArray*` helpers): add cases for the new
  markers; reuse `encodeNested` for `@AtomicSerial[]` elements (depth-bounded per element).
- `getarg.WireTypes` + decode path: matching `decodeEnum` / `decodeArray*` (range-check,
  element-wise tag-peek for nullable element types, `Enum.valueOf` with fail-secure unknown).
- `DerFieldStore`: enum and primitive arrays are decoded eagerly (value types);
  `@AtomicSerial[]` elements are decoded lazily through `DerGetArg.get` to thread the depth
  guard (mirror the `isNested`/`rawNested` pattern at the array-element granularity).

### 17.5 Tests

Per element type: round-trip; null array; empty array; per-element nullability (for object
arrays); unknown-enum-name rejection; determinism (same value → byte-identical bytes);
polymorphism for `@AtomicSerial[]` (declared supertype, runtime subtype per element); the
cumulative depth guard still trips on a deep `@AtomicSerial[]` chain (each element counted).
Multi-dim and `array:byte` rejected fail-secure. Float/double/char still throw with the S7.6
deferred message.
