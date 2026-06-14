# JGDMS-STD-008: @AtomicSerial Serialization Uncoupling (JGDMS 4.0.0)

**Status:** Draft (for discussion)
**Version:** 0.1-DRAFT
**Applies to:** JGDMS 4.0.0, DirtyChai (JDK fork), and non-JVM JGDMS participants
**Depends on:** JGDMS-STD-001 (@AtomicSerial), JGDMS-STD-006 (DER Wire Format)
**Supersedes (on completion):** the Java-Object-Serialization coupling of the
`@AtomicSerial` API as defined in STD-001

> **Editorial note (v0.1-DRAFT):** This standard captures the 4.0.0 decision to
> remove all Java Object Serialization coupling from the `@AtomicSerial` API. It
> records the design agreed in design discussion; field-level details marked
> **[OPEN]** await confirmation. Class/line references are against `trunk`
> (worktree `der-wireformat-std006`).

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

### 4.4 Permission model (normative)

The `SerializablePermission("enableSubclassImplementation")` guard on the protected
`GetArg()`/`PutArg()` constructors MUST be replaced by a purpose-built permission
(e.g. `AtomicSerialPermission("enableFrameworkImplementation")`) whose semantics
describe the actual concern (only trusted code may produce `GetArg`/`PutArg`
instances), not Java serialization. A 4.0.0 deployment MUST NOT require any
`java.io.SerializablePermission` for `@AtomicSerial` operation (§2.1).

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

Client-side distributed garbage collection is **retained**. `BasicObjectEndpoint`
today keys a `Map<ObjectInputStream, DgcBatchContext>` and calls
`ObjectInputStream.registerValidation(...)` to coalesce DGC dirty-calls per
deserialization stream — both JOSS-specific.

4.0.0 MUST provide a **DER-native** equivalent with no `java.io` dependency:

- a per-decode **stream-identity token** (an opaque handle identifying the current
  decode unit / connection), obtainable from the decode context; and
- a **post-decode completion callback** registered against that token, invoked once
  the decode unit completes (the neutral analogue of `registerValidation`).

The DGC batch context is keyed by the token. **[OPEN]** exact placement of the token
on `GetArg`/the decode context and the callback registration API.

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

- **No Java Object Serialization compatibility is retained.** This is a breaking
  change; the release is **JGDMS 4.0.0** (major version bump).
- **Source compatibility:** the ~86 `@AtomicSerial` implementors need no source
  change (§4.2) — they use only neutral `arg.get`/`put`/`serialForm` calls.
- **Intentional breaks:** the 19 dual-mode `serialPersistentFields = serialForm()`
  classes break (they relied on `SerialForm extends ObjectStreamField`); this is
  by design, as `Serializable` interop is being removed.
- **Binary compatibility:** any external code that subclasses `GetArg`/`PutArg` or
  uses the removed methods must recompile. **[OPEN]** confirm JGDMS is the sole
  consumer of these types (it is believed to be).

---

## 10. Conformance

A 4.0.0-conformant implementation:

1. Exposes `GetArg`, `PutArg`, `SerialForm` with **no `java.io` serialization
   supertype**; preserves the §4.2 signatures.
2. Requires **no serialization-related permission grant** for normal operation
   (§2.1, §4.4).
3. Provides no `@ReadInput`/`ReadObject` and no `PutArg.output()` (§4.3, §5).
4. Sources all decode-channel context from `net.jini.io.ObjectStreamContext`
   (§4.5).
5. Retains client-side DGC via the DER-native callback (§6).
6. Selects encoding via the neutral `ObjectInput`/`ObjectOutput` boundary +
   `WireFormat` (§7).
7. Upgrades existing reliable-log persistence via the bounded read-only conversion
   layer (§8), writing only DER going forward, and requiring no wire-facing grant
   for the legacy read path.

---

## 11. Open Questions

1. §4.3 — confirm `PutArg.output()` has no callers other than the `@ReadInput`-paired
   raw writes before deletion.
2. §4.4 — exact name/shape of the replacement framework permission.
3. §6 — DGC token placement on the decode context and the callback registration API.
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
