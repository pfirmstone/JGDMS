# SOW — DirtyChai: Retire public ObjectInputStream/ObjectOutputStream, DER-backed SPI for the load-bearing internal consumers

- **Authored:** 2026-07-10 (captured from design conversation with Peter Firmstone).
- **Status:** Design / advisory only — **no source contributions**. DirtyChai's own
  `CLAUDE.md` adopts the OpenJDK Interim Policy on Generative AI: *"Analyze and advise
  only. Do NOT generate or commit contributions"* to any code or doc in that repo. This
  document is JGDMS-side planning material **for DirtyChai's human maintainers to read,
  weigh, and implement themselves** — it is not a patch, not a diff, and contains no
  `.java` source intended to be dropped into DirtyChai. Where a mechanism is proposed, it
  is described conceptually (shape/interface/responsibilities), by analogy to a real
  JGDMS precedent, not authored as compilable code.
- **Companions:** `SOW-DirtyChai-QUIC-TLS-Investigation.md` (same advisory framing, same
  repo pairing); `SOW-AtomicSerial-Delegate-Marshalling.md` and
  `SOW-MarshalledObject-Migration-RemainingGroups.md` (the JGDMS-side precedents this
  design borrows from — per-package delegate dispatch and the dual-read migration
  pattern, respectively).
- **Repos:** target = `~/GitHub/DirtyChai` (OpenJDK 27 fork); cross-ref = `~/GitHub/JGDMS`
  (source of the DER codec and the adapter-pattern precedent).

---

## 0. Objective

Record a proposed design, worked through with Peter, for DirtyChai's handling of
`java.io.ObjectInputStream`/`ObjectOutputStream` (OIS/OOS): **gut the public classes so
they throw unconditionally at construction**, closing an entire recurring class of "did we
gate every entry point" audit risk, while **not silently breaking the small number of
JDK-internal consumers that are load-bearing** (`SignedObject`, `SealedObject`,
`JceKeyStore`) by giving them a narrow, DER-backed replacement mechanism instead of routing
through the now-gutted public classes.

This SOW verifies the claims from the design conversation against live DirtyChai source
(corrects two line-number/method mix-ups and confirms one previously "unconfirmed" fact),
proposes the SPI's shape by analogy to JGDMS's own `Serializer`/`DerReplacer` mechanism,
and states two open design questions explicitly rather than resolving them.

---

## 1. Motivation: merge burden, not just attack surface

DirtyChai already gates standard Java deserialization with a custom permission check —
`au.zeus.jdk.authorization.guards.SerialObjectPermission` — inside
`java.io.ObjectInputStream`. **Verified against current source**
(`/home/user/GitHub/DirtyChai/src/java.base/share/classes/java/io/ObjectInputStream.java`):
the gate is present in exactly two places, both a single line,
`new SerialObjectPermission(cl.getName()).checkGuard(null);`, immediately before the
class is instantiated:

- `readProxyDesc(boolean unshared)` (method starts line 1916; gate at line **1976**).
- `readOrdinaryObject(boolean unshared)` (method starts line 2216; gate at line **2231**).

(Correction to the design-conversation notes: the two line numbers were attributed to the
methods in the opposite order — `readProxyDesc` is the ~1976 site, `readOrdinaryObject` is
the ~2231 site. Both gates are confirmed present and current; the substance of the claim —
"both paths are gated" — holds.)

The stated historical concern — that `readProxyDesc()` was a real, documented gap that had
to be found and closed after the fact — is exactly the failure mode this design eliminates
structurally rather than by continued vigilance: **every** future OpenJDK change to
`ObjectInputStream`'s internals (new object-construction paths, refactored helper methods,
new record/enum/array handling) is a candidate for a *new* uncovered path, discoverable
only by re-auditing the whole class against upstream diffs. That is also, independently, a
**merge-conflict burden**: the `SerialObjectPermission` checks are woven into the bodies of
upstream's own methods (`readOrdinaryObject`, `readProxyDesc`), so every upstream patch that
touches those method bodies is a **semantic** merge to reconcile (does the patch add a new
construction path that also needs a gate? did line numbers/control flow shift under the
inserted check?), not a mechanical textual merge. Gutting the method bodies makes upstream's
future changes to *that* logic **irrelevant on arrival** — there is nothing left to merge
semantically once the body is replaced with an unconditional throw.

---

## 2. Proposed design: gut OIS/OOS, throw unconditionally at construction

Replace the constructors of `ObjectInputStream`/`ObjectOutputStream` — and, in effect, the
operative body of the class — with something that throws immediately and unconditionally
(e.g. `UnsupportedOperationException`), with **no runtime opt-back-in** (no property, no
permission, no subclass hook that restores function). This is not a novel move for
DirtyChai to make on its own: **mainline OpenJDK has already done exactly this shape of
change**, for `SecurityManager`, in **JEP 486** ("Permanently Disable the Security
Manager") — keep a minimal class present for compile/link compatibility (so code that
merely references the type, or declares `implements Serializable`-shaped contracts, keeps
compiling), make it permanently non-functional, remove any path back to functional. DirtyChai
doing the same to OIS/OOS is the same precedent pattern, aimed at the same kind of
"legacy mechanism with a large, hard-to-fully-audit attack surface" problem SM was.

**What stays untouched:** the `java.io.Serializable`/`Externalizable` marker interfaces.
Gutting OIS/OOS does not require touching these — they are zero-risk to leave alone, and
leaving them alone avoids ecosystem-wide compile breakage from every `implements
Serializable` declaration in every class that merely marks itself serializable without ever
being pushed through an actual `ObjectOutputStream`. The break is scoped to the I/O
mechanism, not the marker contract.

**What breaks, expected and desired:** every consumer that actually constructs or
subclasses OIS/OOS to do real serialization work — this is the point. RMI (`java.rmi`),
JMX-over-RMI (`java.management.rmi`), JNDI-LDAP object deserialization
(`java.naming`/`com.sun.jndi.ldap.Obj`), `javax.sql.rowset` (`CachedRowSetImpl`), and
`jdk.jshell`'s execution channel are all legacy RPC/serialization plumbing this whole
DirtyChai effort is explicitly moving away from (JERI+DER is JGDMS's answer to exactly this
class of mechanism) — their breakage is the intended outcome, not a regression to work
around.

---

## 3. The exception: three load-bearing internal consumers

A small number of things **inside java.base/javax.crypto itself** are not legacy RPC
plumbing — they are core security primitives whose only current implementation mechanism
happens to be OIS/OOS. Verified by reading the source directly:

### 3.1 `java.security.SignedObject`
(`java.base/share/classes/java/security/SignedObject.java`)

Confirmed: the constructor (`SignedObject(Serializable object, PrivateKey signingKey,
Signature signingEngine)`, ~line 155) pipes the caller-supplied payload through a fresh
`ObjectOutputStream` to a `ByteArrayOutputStream`, then signs the resulting bytes
(`this.content = b.toByteArray()`); `getObject()` (~line 183) reverses this with an
`ObjectInputStream` over `this.content`. OIS/OOS is not incidental here — it **is** the
serialize-then-sign / deserialize-after-verify mechanism, the class's entire reason for
existing.

### 3.2 `javax.crypto.SealedObject`
(`java.base/share/classes/javax/crypto/SealedObject.java`)

Same shape, for encryption: the constructor pipes the payload through
`ObjectOutputStream` before sealing (~line 160); unsealing uses a package-private
`extObjectInputStream extends ObjectInputStream` subclass (line ~454) obtained via
`getExtObjectInputStream(Cipher)` (~line 441) to decrypt-then-deserialize. Also directly
dependent on OIS/OOS as its core mechanism.

### 3.3 `com.sun.crypto.provider.JceKeyStore`
(`java.base/share/classes/com/sun/crypto/provider/JceKeyStore.java`)

The design conversation flagged this as **unconfirmed** — whether the actual persisted
on-disk JCEKS format round-trips through OIS/OOS. **Verified by reading `engineStore`
(~line 510) and `engineLoad` (~line 690): partially, and specifically.** The bulk of the
format is hand-rolled `DataOutputStream`/`DataInputStream` framing (magic number, version,
per-entry tag, alias, date, then for `PrivateKeyEntry`/`TrustedCertEntry` the raw encoded
key/cert bytes via `getEncoded()`) — **not** OIS/OOS for those two entry kinds. But for
`SecretKeyEntry` specifically, `engineStore` opens `oos = new ObjectOutputStream(dos)` and
calls `oos.writeObject(((SecretKeyEntry) entry).sealedKey)` (~line 643) — i.e. it
Java-serializes a `SealedObject` instance (§3.2) directly into the on-disk stream, inline
with the `DataOutputStream` framing; `engineLoad`'s mirror is `ois = new
ObjectInputStream(dis)` (~line 841). **So the persisted JCEKS format genuinely depends on
OIS/OOS wherever a keystore contains a `SecretKeyEntry`** (i.e. any JCEKS keystore holding a
symmetric/secret key, not just asymmetric key pairs or trusted certs) — the "unconfirmed"
flag in the design conversation resolves to **confirmed, and non-trivial**: this is not a
peripheral code path, it is the on-disk format for an entire entry type.

### The proposed fix

Define a small internal SPI — name/shape to be finalized by DirtyChai's maintainers, not
prescribed here — with the conceptual shape of "serialize this payload to bytes / rebuild
it from bytes," analogous in spirit (not code) to a mechanism JGDMS already uses in its own
DER codec for exactly this kind of problem: `org.apache.river.api.io.Serializer`
(`jgdms-platform`) + `au.net.zeus.jgdms.der.serial.DerReplacer`
(`jgdms-der/src/main/java/au/net/zeus/jgdms/der/serial/DerReplacer.java`). Read for the
pattern (JGDMS's own, not reusable verbatim — DirtyChai's SPI is for `java.base`/
`javax.crypto` internal code, not JGDMS's marshalling stack):

- A type JGDMS doesn't control (can't add `@AtomicSerial` to — e.g. `java.net.URL`,
  `java.util.Date`) gets a small **companion class**, itself `@AtomicSerial`, annotated
  `@Serializer(replaceObType = X.class)`, with a constructor taking the foreign type and a
  `readResolve()` (via a `Resolve` marker interface) that rebuilds it.
- `DerReplacer.replace(Object)` substitutes the companion for the foreign value on encode
  (constructing the companion reflectively via its `(X)` constructor);
  `DerReplacer.resolve(Object)` calls `readResolve()` on decode to hand back the original
  type. Companions are enumerated via a classpath resource list
  (`META-INF/jgdms/der-serializers`), not hardwired into the codec — "no visibility or
  permission obstacle," per that file's own doc comment, because a companion is referenced
  by name and doesn't need to be `public`.
- The shape that matters for DirtyChai: **an external adapter class marshals a type the
  target class doesn't cooperate with**, discovered/registered rather than baked into the
  core engine, so the engine (DER codec there; the proposed SPI here) stays agnostic to the
  specific foreign types it serves.

For DirtyChai, the analogous shape: `SignedObject`, `SealedObject`, and `JceKeyStore` would
each depend on the new internal SPI instead of on `java.io.ObjectOutputStream`/
`ObjectInputStream` directly — and JGDMS's Atomic DER codec (`jgdms-der`, JGDMS-STD-006)
would be **an** implementation of that SPI reachable from `java.base` (mechanically: either
DirtyChai vendors/links a minimal DER encode/decode primitive into `java.base`, or the SPI
is a thin interface that `jgdms-der` implements from outside `java.base` and DirtyChai wires
in at a lower layer than JGDMS's own module — the exact linkage is an implementation
decision for DirtyChai's maintainers, not resolved here). The result: gutting the public
OIS/OOS classes (§2) no longer takes these three classes down with them, because they would
no longer depend on OIS/OOS at all — they'd depend on the new SPI, whose only implementation
is DER-backed.

---

## 4. Open design questions (stated, not resolved)

### 4.1 `SignedObject`/`SealedObject`: payload-type contract narrows

Today, `SignedObject`/`SealedObject`'s public constructors accept **any** `Serializable`
payload — an open, arbitrary-Java-type contract, exactly the same "marshal anything a
caller hands you" shape that motivates this whole effort elsewhere. Routing through a
DER-backed SPI **narrows** that to "payload must have DER support" — i.e. be
`@AtomicSerial`, or have a registered `@Serializer`/companion for the foreign type. This is
a **deliberate, real breaking API contract change**: a caller today can `new
SignedObject(myArbitrarySerializableThing, key, sig)` for any `Serializable` type it likes;
after this change, a payload type with no DER support would need to fail — and needs to fail
**loudly and specifically** (a clear, typed exception naming the unsupported type), not
silently misbehave (e.g. not silently drop fields, not silently fall back to some other
partial mechanism). Framed as the **correct, desired direction** — bounding what gets
marshalled to known/vetted types is consistent with the rest of this security posture (the
same "no arbitrary gadget-chain-shaped payload" instinct behind gating OIS in the first
place) — but it is a compatibility break for any existing caller passing an
non-DER-covered `Serializable` type, and the error-handling semantics (exception type,
message content, whether there's a "day one" allowlist vs. a growable one) is an open
decision, not resolved here.

### 4.2 `JceKeyStore`: on-disk format compatibility for `SecretKeyEntry`

Per §3.3, the *only* place JCEKS's persisted format actually depends on OIS/OOS is the
`SecretKeyEntry` case (`oos.writeObject(sealedKey)`/`ois.readObject()` inline within the
`DataOutputStream`/`DataInputStream` framing). If DirtyChai's `JceKeyStore` switches that
one write/read to go through the new DER-backed SPI instead, **the on-disk byte format for
`SecretKeyEntry` changes** — a real backward-compatibility question: a JCEKS file
containing a secret key, written by stock OpenJDK (or by DirtyChai pre-change), would not
load correctly under a DirtyChai build that made this switch, and vice versa, **unless**
there is an explicit migration path. (`PrivateKeyEntry`/`TrustedCertEntry` are unaffected —
their persisted format never touched OIS/OOS.)

There is a directly-applicable precedent for exactly this shape of problem already in
JGDMS's own history: the MarshalledObject→MarshalledInstance migration
(`SOW-MarshalledObject-Migration-RemainingGroups.md`, memory
`jgdms-marshalledobject-mi-migration`) used a **dual-read** pattern — always write the new
format going forward, but accept either the old or the new format on read, so existing
persisted/wire data keeps working while new writes converge on the new format. Two options
for `JceKeyStore`, stated but not resolved here:

- **Dual-read**: `engineLoad` detects/attempts the legacy OIS-based `SecretKeyEntry` framing
  first (or on DER-decode failure), falls back to it for backward compatibility, while
  `engineStore` always writes the new DER-backed format going forward. Cost: two decode
  paths to maintain indefinitely (or until a deprecation window closes); benefit: existing
  JCEKS keystores with secret keys keep loading without operator action.
- **DER-only, no legacy read path**: `JceKeyStore` under DirtyChai simply cannot load a
  `SecretKeyEntry` written by stock OpenJDK (or pre-change DirtyChai) — an explicit,
  loud incompatibility, pushed onto operators as a "re-import your keystore" migration step
  (manual export via stock `keytool` + re-import, or a one-time offline conversion tool).
  Cost: a real compatibility break for anyone with an existing JCEKS secret-key entry;
  benefit: no dual-decode-path maintenance burden, and no ambiguity about which format a
  given file is in.

Different cost/risk tradeoff either way; this SOW does not pick one.

---

## 5. Internal call-site audit (re-verified against live source)

Re-ran the design conversation's grep (`new ObjectInputStream(`, `new
ObjectOutputStream(`, `extends ObjectInputStream`, `extends ObjectOutputStream`) across
`/home/user/GitHub/DirtyChai/src`, excluding `java.io` itself and test trees. **Result:
the prior list is confirmed accurate and complete** (same 17 files, one grep-visible
addition worth noting: `demo/share/jfc/Stylepad/Stylepad.java` is a `demo/` sample, not a
shipped module, and can be dropped from consideration entirely — it was already listed but
its non-shipped status is worth stating explicitly).

| File | Category |
|---|---|
| `java.base/…/java/security/SignedObject.java` | **Needs SPI** (§3.1) |
| `java.base/…/javax/crypto/SealedObject.java` | **Needs SPI** (§3.2) |
| `java.base/…/com/sun/crypto/provider/JceKeyStore.java` | **Needs SPI**, partial (§3.3 — only the `SecretKeyEntry` path) |
| `java.rmi/…/java/rmi/MarshalledObject.java` | Expected breakage — legacy RMI marshalling, JGDMS's own `MarshalledInstance`/DER work is the stated replacement direction |
| `java.rmi/…/sun/rmi/server/MarshalInputStream.java` | Expected breakage — RMI plumbing |
| `java.rmi/…/sun/rmi/server/MarshalOutputStream.java` | Expected breakage — RMI plumbing |
| `java.management.rmi/…/RMIConnector.java` | Expected breakage — JMX-over-RMI; also the connector class the companion `SOW-JMX-JERI-DER-Connector.md` proposes to obsolete |
| `java.management.rmi/…/RMIConnectorServer.java` | Expected breakage — same |
| `java.management/…/ObjectInputStreamWithLoader.java` | Expected breakage — JMX internal support for the RMI connector path |
| `java.management/…/EnvHelp.java` | Expected breakage — JMX internal support |
| `java.naming/…/com/sun/jndi/ldap/Obj.java` | Expected breakage — LDAP-attribute-to-Java-object deserialization, legacy JNDI feature |
| `java.sql.rowset/…/CachedRowSetImpl.java` | Expected breakage — `javax.sql.rowset` serialization support |
| `jdk.jshell/…/execution/Util.java` | Expected breakage — jshell's remote execution channel uses OIS/OOS as its wire format |
| `java.desktop/…/java/awt/dnd/SerializationTester.java` | Expected breakage (module-scope caveat, see below) |
| `java.desktop/…/java/beans/Beans.java` | Expected breakage (module-scope caveat) |
| `java.desktop/…/sun/awt/datatransfer/DataTransferer.java` | Expected breakage (module-scope caveat) |
| `java.desktop/…/sun/awt/datatransfer/TransferableProxy.java` | Expected breakage (module-scope caveat) |
| `demo/share/jfc/Stylepad/Stylepad.java` | Not shipped — a `demo/` sample, drop from consideration |

**Module-scope check (re-verified, not assumed):** the design conversation flagged that
`java.desktop`/`jdk.jshell` breakage may be moot if DirtyChai's shipped runtime image
doesn't include those modules. Checked the actual built product image
(`/home/user/GitHub/DirtyChai/build/linux-x86_64-server-release/images/jdk/jmods/`):
**`java.desktop.jmod`, `jdk.jshell.jmod`, and `jdk.unsupported.desktop.jmod` are all
present** (68 jmods total in the built image). So this category of breakage is **not**
moot for this build configuration — AWT drag-and-drop serialization, `java.beans.Beans`
instantiation-from-serialized-state, and jshell's remote execution channel would all break
under §2, same as the RMI/JMX/JNDI/rowset group. (Whether that's acceptable is a product
decision for DirtyChai's maintainers — these are all plausibly "legacy plumbing being
retired" in the same spirit as RMI, but unlike RMI/JMX/JNDI they weren't named as
explicitly in-scope-for-breakage in the original design conversation, so flagging them
here rather than silently folding them into "expected.")

---

## 6. Rough phased plan (for DirtyChai's maintainers to weigh — not a JGDMS commitment)

1. **P0 — land the SPI + DER-backed implementation for the three load-bearing consumers
   first**, before touching OIS/OOS itself. `SignedObject`, `SealedObject`, `JceKeyStore`
   migrate onto the new SPI while OIS/OOS still exists and still works underneath (or
   underneath a parallel DER-backed path) — so this phase is independently testable and
   reversible, and resolves §4.1/§4.2 in the process (the payload-type contract change and
   the on-disk migration decision must be settled here, before the next phase makes them
   irreversible).
2. **P1 — gut the public `ObjectInputStream`/`ObjectOutputStream`** (§2) once P0 is proven:
   replace constructors with the unconditional throw, JEP-486-style. At this point the
   expected-breakage list (§5) breaks as intended; the three load-bearing consumers do not,
   because they no longer touch OIS/OOS.
3. **P2 — audit/response for the module-scope findings** (§5's `java.desktop`/`jdk.jshell`
   group): explicit decision per consumer — accept the breakage (consistent with "legacy
   plumbing retirement"), or, if any of those are judged load-bearing for a DirtyChai use
   case, fold them into the P0 SPI-migration set before P1 ships.
4. **P3 — deprecation/removal cleanup**: once P1 is stable, consider whether the "keep a
   minimal class for compile/link compatibility" shell classes need any further trimming
   (mirroring whatever JEP 486 ultimately did to `SecurityManager`'s shell class over time).

---

## References

- `java.io.ObjectInputStream`
  (`/home/user/GitHub/DirtyChai/src/java.base/share/classes/java/io/ObjectInputStream.java`)
  — `SerialObjectPermission` gate sites: `readProxyDesc` (method @1916, gate @1976),
  `readOrdinaryObject` (method @2216, gate @2231). Re-verify line numbers before acting;
  they will drift with any further edits.
- `au.zeus.jdk.authorization.guards.SerialObjectPermission` — the existing DirtyChai gate
  this design proposes to retire (in favour of removing the gated code paths entirely).
- JEP 486, "Permanently Disable the Security Manager" — the precedent for "keep a
  compile/link-compatible shell class, make it permanently non-functional, no
  re-enable path," cited as prior art for the same move applied to OIS/OOS.
- `java.security.SignedObject`, `javax.crypto.SealedObject`,
  `com.sun.crypto.provider.JceKeyStore` (all under
  `/home/user/GitHub/DirtyChai/src/java.base/share/classes/...`) — the three load-bearing
  internal consumers (§3).
- `org.apache.river.api.io.Serializer` (annotation),
  `au.net.zeus.jgdms.der.serial.DerReplacer`
  (`jgdms-der/src/main/java/au/net/zeus/jgdms/der/serial/DerReplacer.java`), and the
  existing companion classes in `jgdms-platform/src/main/java/org/apache/river/api/io/`
  (`URLSerializer`, `DateSerializer`, `ThrowableSerializer`, etc.) — the JGDMS-side
  adapter-pattern precedent this SOW's proposed SPI borrows its *shape* from (§3, "the
  proposed fix").
- `SOW-AtomicSerial-Delegate-Marshalling.md` — companion JGDMS mechanism
  (`MarshalDelegate`), a different precedent (package-private access broker for JGDMS's own
  types) noted here only to distinguish it from the `Serializer`/`DerReplacer` mechanism
  actually being borrowed from.
- `SOW-MarshalledObject-Migration-RemainingGroups.md` — the dual-read migration pattern
  referenced in §4.2.
- `SOW-DirtyChai-QUIC-TLS-Investigation.md` — the sibling advisory-only document this SOW
  follows in framing and repo-boundary discipline.
- `SOW-JMX-JERI-DER-Connector.md` — companion JGDMS-side SOW; notes `RMIConnector`/
  `RMIConnectorServer` (§5 of this doc) as the connector class a JERI/DER-based JMX
  connector would let operators stop depending on.
- `/home/user/GitHub/DirtyChai/CLAUDE.md` — the no-AI-contribution policy this document
  is written to comply with (advisory analysis only, no DirtyChai source or doc changes).
