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

### 2.1 Verified: "throw in the constructor" alone is insufficient — `Unsafe`/`ReflectionFactory` bypass constructors entirely

Peter raised a concern about §2 as originally scoped ("gut the constructors so they throw"):
`sun.misc.Unsafe.allocateInstance(Class)` and `jdk.internal.reflect.ReflectionFactory`'s
serialization-specific constructor factory (`newConstructorForSerialization`) both create an
instance **without ever invoking any constructor** — so a design that only guts the
constructor body leaves a zero-initialized, fully-`instanceof`-valid `ObjectInputStream`/
`ObjectOutputStream` reachable to anyone who can reach either bypass, with every non-constructor
method still functional. This was verified empirically against DirtyChai's actual built product
image (not assumed), and the verification changes the recommendation in a specific, bounded way.

**What was verified, and how.** Built product image used:
`/home/user/GitHub/DirtyChai/build/linux-x86_64-server-release/images/jdk` — `openjdk version
"27-internal"`, confirming the SOW's stated OpenJDK 27 target. A standalone test class (outside
the DirtyChai repo — no DirtyChai source read as a basis for writing DirtyChai code, per that
repo's own advise-only policy) was compiled and run **against this real JDK** to test three
things directly:

1. **The public API gate (`Unsafe.getUnsafe()`)** — blocked as designed: DirtyChai's
   `sun.misc.Unsafe.getUnsafe()` (`src/jdk.unsupported/share/classes/sun/misc/Unsafe.java:111`)
   is unmodified stock OpenJDK and still gates on `VM.isSystemDomainLoader(caller.getClassLoader())`,
   throwing for any non-bootstrap caller. This is the "front door" and it is not the concern.
2. **The reflective `Unsafe` bypass** — **confirmed live and unconditionally reachable from
   plain classpath code, with zero flags.** `sun.misc.Unsafe.class.getDeclaredField("theUnsafe")`
   + `setAccessible(true)` + `.get(null)` succeeded with **no `SecurityManager` installed and no
   `--add-opens`/`--add-modules` needed at all**, then `Unsafe.allocateInstance(TargetClass)`
   produced a zero-initialized instance whose constructor body provably never ran (verified via a
   marker field left at its default value). Root cause, confirmed by reading source: DirtyChai's
   `jdk.unsupported/share/classes/module-info.java` is **unmodified stock OpenJDK** —
   `exports sun.misc; exports sun.reflect; opens sun.misc; opens sun.reflect;` — all
   **unconditional** (not qualified to specific modules), and the classpath/unnamed module reads
   `jdk.unsupported` by default with no extra flags (empirically confirmed: the test needed none).
3. **The `ReflectionFactory.newConstructorForSerialization` bypass** — **also confirmed live and
   reachable**, and via a *more direct* path than `Unsafe`: `sun.reflect.ReflectionFactory`
   (`src/jdk.unsupported/share/classes/sun/reflect/ReflectionFactory.java`) wraps
   `jdk.internal.reflect.ReflectionFactory.newConstructorForSerialization` and its public factory
   method has **only a `SecurityManager`-conditional gate**:
   `if (security != null) security.checkPermission(new RuntimePermission("reflectionFactoryAccess"));`
   (lines 84-89) — **no caller-classloader check at all**, unlike `Unsafe.getUnsafe()`. With no
   `SecurityManager` installed, this method is callable directly (not even via reflection-on-a-
   private-field) by any code on the classpath, and `newConstructorForSerialization(TargetClass,
   Object.class.getDeclaredConstructor())` yields a `Constructor` that, when invoked, builds a
   `TargetClass` instance while running only `Object`'s no-arg constructor — the target's own
   constructor (gutted or not) never executes. This was empirically confirmed.

**What actually gates both bypasses, and where that leaves the risk.** Both routes verified
above have a real, unmodified, stock-OpenJDK gate that only activates **when a `SecurityManager`
is installed**: `java.lang.reflect.AccessibleObject.checkPermission()`
(`src/java.base/share/classes/java/lang/reflect/AccessibleObject.java:84-88`) requires
`ReflectPermission("suppressAccessChecks")` before `setAccessible(true)` succeeds (gates the
`Unsafe` route), and `ReflectionFactory.getReflectionFactory()`'s `reflectionFactoryAccess`
`RuntimePermission` check (above) gates the `ReflectionFactory` route directly. **Neither gate
is DirtyChai-specific hardening — both are checked and confirmed unmodified from stock OpenJDK**,
which matters because it means this isn't a gap DirtyChai introduced; it's a gap that exists
**whenever no `SecurityManager` is installed**, which is a precondition of DirtyChai's entire
security model being active at all (not unique to this design). Searched
`src/java.base/share/classes/au/zeus/jdk/` for any DirtyChai-added filtering of `Unsafe`/
`ReflectionFactory` reflection specifically: none exists. The one related DirtyChai addition
found, `System.java`'s `isUnsafeReflectionFrame()` (~line 3062), is a `StackWalker` frame-detector
used **only** inside the defense-in-depth path for `setSecurityManager()` itself (to stop a
malicious *custom* `SecurityManager` from being installed via an Unsafe/reflection-laundered call
stack) — a narrower, different purpose than gating general `Unsafe`/`ReflectionFactory` use, and
not a control this design can lean on.

So: **under DirtyChai's actual intended operating mode for JGDMS (a `SecurityManager` — normally
`CombinerSecurityManager` — installed with a least-privilege `ConcurrentPolicyFile` policy)**,
both bypasses are already gated by permissions (`ReflectPermission("suppressAccessChecks")`,
`RuntimePermission("reflectionFactoryAccess")`) that a correctly-scoped policy must not grant to
untrusted codebases — the same class of maximally-dangerous permission JGDMS's own least-privilege
norm (memory: `polp-no-allpermission-scaffolding`) already treats as throwaway-QA-only. Spot-checked
JGDMS's own policy corpus for exactly this: `grep -rl "suppressAccessChecks\|reflectionFactoryAccess"`
across `~/GitHub/JGDMS` matches **only** `qa/src/.../end2end/policies/end2end.policy` (QA test
fixture scaffolding) and one stale QA log file — **not** any production/deployment-oriented example
policy. **If** DirtyChai/JGDMS is ever run with no `SecurityManager` installed at all, both bypasses
are trivially reachable with zero permission checks (empirically confirmed above) — but at that
point essentially all of DirtyChai's SM-based hardening is simultaneously moot, which is a
pre-existing, general operating precondition of the whole security model, not something this SOW's
design change causes or can fix.

**Adversarial assessment of Fix A ("gut every method, not just the constructor").**
Gutting every public/security-relevant method body to throw unconditionally as its first statement
closes the *specific* gap Peter raised: even a `Unsafe`/`ReflectionFactory`-allocated,
constructor-never-ran instance becomes inert, because there is no method left whose body does
anything before throwing — this holds regardless of how the instance was allocated. Checked
adversarially for residual gaps:

- **Native methods bypassing the Java-level gut:** checked — `grep -n "native "` against both
  `ObjectInputStream.java` and `ObjectOutputStream.java` in DirtyChai returns nothing. Neither
  class declares any native method, so there is no JNI-reachable path that a Java-level "throw
  first" edit fails to cover. This is a real (if narrow) thing worth re-checking if upstream ever
  adds one.
- **Raw field read/write via `Unsafe`, bypassing methods entirely:** real capability (confirmed
  present in this build's `sun.misc.Unsafe` surface: `objectFieldOffset`, `getObject`/`putObject`,
  `compareAndSwapObject`, etc.) — but **moot under Fix A specifically because the throw is the
  first statement of every method**: an attacker could use `Unsafe` to forge/corrupt any private
  field of a gutted OIS/OOS instance, but since no method reads a field before throwing, the
  forged state is never consulted. This conclusion depends entirely on the throw being truly
  unconditional and first — not gated behind any `if (initialized)`/lazy-check pattern (the same
  shape of partial-gate gap Peter's concern originally targeted at the constructor). Worth stating
  as an explicit implementation requirement for whoever writes this: **every method body's first
  statement, no exceptions, no conditional paths that read state before the throw.**
- **`instanceof`/type-dispatch elsewhere treating a gutted-but-still-`instanceof`-valid instance
  specially, without calling any of its methods:** the theoretical residual risk Fix A cannot
  close by construction — a gutted instance still satisfies `x instanceof ObjectInputStream`, so
  any code elsewhere that branches on *type* rather than calling a (now-throwing) *method* is
  unaffected by Fix A. Not found in the three load-bearing consumers (§3, which stop referencing
  OIS/OOS entirely once migrated to the DER-backed SPI) or in the 17-file call-site audit (§5,
  all either `new ObjectInputStream(...)`/`extends ObjectInputStream` — construction/subclassing,
  not bare `instanceof` checks) — but this was **not** exhaustively re-grepped for bare
  `instanceof ObjectInputStream`/`instanceof ObjectOutputStream` patterns across all of DirtyChai
  (out of scope for this pass; flagged here as a cheap, worthwhile follow-up audit, not a blocker).
- **A more general point this exercise surfaces:** if `Unsafe`/`ReflectionFactory` access is
  actually reachable (no SM, or an over-permissive policy), the attacker is not limited to
  resurrecting OIS/OOS specifically — the identical `allocateInstance`/`newConstructorForSerialization`
  technique bypasses the constructor of *any* class in the JVM, including DirtyChai's own hardened
  classes (`CombinerSecurityManager`, `ConcurrentPolicyFile`, any future gutted `SecurityManager`
  shell, etc.). Fix A makes OIS/OOS specifically inert against this technique, but does nothing
  for the general exposure — which is squarely a "is `Unsafe`/`ReflectionFactory` reachable"
  question, not an "is this one class hardened enough" question.

**Assessment of Fix B (separate-module approach) against what was actually found.** Fix B's
stated appeal — "no bypass-the-constructor attack surface at all, since there's no functional
bytecode present" — turns out to add little over a *correctly implemented* Fix A here, for two
concrete reasons specific to this design (not a general dismissal of module-separation as a
technique elsewhere): (1) the three load-bearing consumers (§3) are being migrated **off** OIS/OOS
entirely regardless of Fix A vs Fix B, so neither fix's OIS/OOS-specific hardening is even in their
path once §3 lands; (2) for the legacy-plumbing consumers (§5 — RMI/JMX/JNDI/rowset/jshell/desktop),
§2 already treats their breakage as the *intended, desired* outcome — whether that breakage
manifests as "class exists, every method throws" (Fix A) or "class/module absent, `NoClassDefFoundError`"
(Fix B) is the same practical outcome (unusable) via a different exception shape, so Fix B buys
nothing there either. Fix B's engineering cost, by contrast, is substantial and specific to this
codebase: `java.io` is a single package that cannot be split across modules, so Fix B would require
relocating the *real* implementation to a new package in a new optional module, keeping a
JEP-486-style gutted stub under `java.io` for compile/link compatibility anyway (i.e. **Fix B
does not replace Fix A, it adds an entire second module on top of it**), plus cascading explicit
`requires` changes across `java.rmi`, `java.management.rmi`, `java.naming`, `java.sql.rowset`,
`jdk.jshell`, and parts of `java.desktop` (§5), plus a `jlink`/default-image packaging decision
about whether that new module ships by default. Given (1) and (2), that cost buys no additional
protection for the specific concern raised here.

**Recommendation.** **Fix A, implemented correctly (every method, unconditional first-statement
throw, no partial/conditional gating anywhere) is the warranted fix — Fix B is not warranted for
this concern specifically**, given what was verified: the actual residual exposure after a correct
Fix A is not "OIS/OOS is still reachable" (it verifiably would not be) but "is `Unsafe`/
`ReflectionFactory` reachable by untrusted code at all" — a pre-existing, JVM-wide question that
threatens every hardened class equally, not something scoped to OIS/OOS, and not something Fix B
would fix either (Fix B protects only OIS/OOS from a technique that, if live, threatens everything
else DirtyChai hardens too). The verified, already-effective control for the actual root cause is
policy discipline DirtyChai/JGDMS already has the mechanism for: **never grant
`ReflectPermission("suppressAccessChecks")` or `RuntimePermission("reflectionFactoryAccess")` to
untrusted codebases**, consistent with existing least-privilege norms; a worthwhile, low-cost
follow-up (not blocking this SOW) is an explicit audit confirming no JGDMS production/example
policy grants either permission outside QA test scaffolding (spot-checked here: currently true).

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
   replace constructors **and, per §2.1's verified `Unsafe`/`ReflectionFactory` bypass finding,
   every other public/security-relevant method body** with the unconditional throw,
   JEP-486-style — constructor-only gutting is verified insufficient (§2.1). At this point the
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

## 7. Board-guidance-informed analysis: two review reflexes applied

The following is analysis, not new design — it applies two review reflexes from
`JGDMS-Board-Reviewer-Guidance.md` to this SOW's existing content. Where it surfaces a real
gap, that gap is stated as an **open question**, matching this document's existing style
(§4); nothing here is force-closed.

### 7.1 Reflex 1 — "second door to the same machinery" (ungated reconstruction door), applied to §4.2's dual-read option

The board-guidance reflex: *"A new wire form or tag that reaches an object-reconstruction
mechanism bypassing the gate the primary path goes through... a polymorphic/self-describing
form that lets the wire name the class to reconstruct is exactly where capability escalation
hides."*

§4.2 already states two options for `JceKeyStore`'s `SecretKeyEntry` on-disk format and does
not pick one: **dual-read** (parse legacy classic-format bytes on load, always write the new
DER format going forward) or **DER-only** (no legacy read path, operator must re-import).
Applying the reflex directly to the dual-read option: legacy `SecretKeyEntry` bytes are
exactly "the wire names the class to reconstruct" — `engineLoad` (verified §3.3, ~line 841)
opens `ois = new ObjectInputStream(dis)` and calls `ois.readObject()`, which walks
`ObjectStreamClass`, resolves class names off the stream, and drives reconstruction via
reflection — the identical shape the codebase's DER/`@AtomicSerial` design (STD-008) and this
SOW's own §2 exist to close off. **A dual-read legacy path is not compatible with §2's stated
design in its current unconditional form.** §2 requires *"no runtime opt-back-in (no
property, no permission, no subclass hook that restores function)"* and §6/P1 calls for
"every other public/security-relevant method body" gutted, JEP-486-style, with **no path
back to functional**. A legacy-format `SecretKeyEntry` decode path requires, by construction,
a reachable, functional `ObjectInputStream` instance somewhere in `java.base` — that is
precisely "a path back to functional," even if narrowly invoked.

**What is actually there today, and why it matters to this tension:** `JceKeyStore` does not
call bare `ois.readObject()` — it first installs a custom `ObjectInputFilter`
(`DeserializationChecker`, `JceKeyStore.java` ~lines 949-994) via
`ois2.setObjectInputFilter(new DeserializationChecker(fullLength))` (~line 847) *before*
`readObject()` runs. The filter is a real, load-bearing, depth-indexed class allowlist:
depth 1 must be `SealedObjectForKeyProtector`, depth 2 must be `SealedObject` or `byte[]`,
anything deeper must be `null`/`Object`, and it additionally bounds `arrayLength()` against
the keystore's own `fullLength` — then falls through to the JVM-wide default
`ObjectInputFilter.Config.getSerialFilter()`. This is a genuine, narrow, already-present gate
on exactly this reconstruction path — not an ungated door today. But it is gated by
`ObjectInputFilter` (JEP 290/415 class/depth/size allowlisting), **a different gate from,
and not integrated with,** this codebase's `DeSerializationPermission("ATOMIC")` model (the
per-class, per-protection-domain check in `ObjectCodec.checkAtomicDeSerializationPermitted`,
`jgdms-der/.../ObjectCodec.java` ~lines 198-233, which is the primary path's gate everywhere
else in this design). Retaining this filter keeps the *specific* risk narrow (three named
classes, bounded size), but it does not close the *structural* risk §2 is designed to close:
the full `ObjectStreamClass`/reflective-construction machinery, and everything §2.1 found
about `Unsafe`/`ReflectionFactory` bypassing constructors, remains reachable through this one
call site for as long as dual-read exists — meaning §2's "structural closure, not continued
vigilance" framing (§1) would have exactly one surviving vigilance-dependent exception.

**Conclusion.** The honest scoping, consistent with Reflex 1 and with this SOW's own §2
design intent: **§2's unconditional, no-opt-back-in gut is incompatible with a general
backward-compat classic-byte read path, full stop.** Two ways to reconcile, neither resolved
here (same "not resolved" framing as §4.2):

- **Take §4.2's DER-only option** — no legacy read path, ever; only data written after the
  cutover is coverable by the new SPI. This is the only option that lets §2's "gutted,
  unconditionally, no path back to functional" claim hold literally and without caveat.
- **If dual-read is judged operationally necessary** (existing deployed JCEKS keystores with
  secret keys cannot all be forced through a re-import step), then §2's claim must be
  explicitly downgraded from "no opt-back-in, full stop" to "no *general-purpose* opt-back-in;
  one narrowly-scoped, filter-gated, single-call-site legacy decode path survives, isolated to
  `JceKeyStore.engineLoad`'s `SecretKeyEntry` case, read-only (never used for new writes), and
  — to avoid being a structurally different, ungated door relative to the rest of this design —
  should additionally be routed through the same `DeSerializationPermission`-style permission
  gate the DER path uses everywhere else, not left to rely on `ObjectInputFilter` alone." This
  turns the existing `DeserializationChecker` from an accidental survivor into a *documented,
  deliberate, permission-reinforced* exception, with an explicit deprecation-window framing
  (the two options are not symmetric in permanence — dual-read implies an eventual removal
  date, not a permanent architecture).

Either path is a real decision for DirtyChai's maintainers (same posture as the rest of §4);
this analysis's contribution is naming the conflict between §4.2's dual-read option and §2's
"no opt-back-in, ever" claim explicitly, so it is not discovered later as a contradiction
between two already-written sections of this same document.

### 7.2 Reflex 2 — pre-deletion audit ("what does the old layer do beyond its headline job?")

The board-guidance reflex: *"Read the old layer's core state machine end-to-end; for every
behavior ask 'is this pure {headline job}, or a contract the layer above depends on?' ...
Nothing gets deleted until every item is reproduced or its removal is explicitly documented
and accepted; 'no half-retirement' is a MUST."*

Catalog of `ObjectInputStream`/`ObjectOutputStream`'s public/protected API surface (read
directly from `/home/user/GitHub/DirtyChai/src/java.base/share/classes/java/io/
Object{Input,Output}Stream.java`), checked against the SOW's current SPI sketch (§3, "serialize
this payload to bytes / rebuild it from bytes," by analogy to `Serializer`/`DerReplacer`) and
against how the three load-bearing consumers actually use the class:

| Capability | Status vs. current SOW design |
|---|---|
| `readObject`/`writeObject` (core marshal/unmarshal) | **Reproduced** — this is the SPI's entire reason to exist (§3). |
| `defaultReadObject`/`defaultWriteObject`, `GetField`/`PutField` (`readFields()`/`writeFields()`) | **Dropped / not addressed for the consumers' OWN classes.** Not just used for the wrapped payload: `SignedObject.readObject` (`SignedObject.java` ~lines 264-277) uses `s.readFields()` to restore its **own** `content`/`signature`/`thealgorithm` fields, and `SealedObject.readObject` (`SealedObject.java` ~lines 429-438) uses `s.defaultReadObject()` for its **own** fields. §3/§4.1 only discuss replacing OIS/OOS for the *wrapped payload*; they do not address that `SignedObject`/`SealedObject` are themselves `Serializable` classes whose own wire form depends on this machinery — gutting public OIS/OOS (§2/P1) breaks the ability to Java-serialize a `SignedObject`/`SealedObject` *instance itself* (e.g. if one is passed to RMI or another `ObjectOutputStream` elsewhere), independent of and in addition to the payload-marshalling concern §3 already covers. Open question, not previously stated in this SOW. |
| `resolveClass`/`resolveProxyClass` | **Undecided.** `SealedObject`'s package-private `extObjectInputStream` (lines 454-478) overrides `resolveClass` as a workaround for a specific class-loading bug ("bug 4224921"), falling back to a secondary lookup when `super.resolveClass()` fails. The DER codec has its own class-resolution path (`ResolutionContext`/`loadClass` in `ObjectCodec`), but the SOW does not state whether/how that subsumes this specific workaround's need. |
| `registerValidation` (`ObjectInputValidation`) | **Dropped, but not used by the three consumers** (checked, not found) — moot for §3's scope specifically, but an unaddressed general capability of the retired class. |
| `resolveObject`/`enableResolveObject`, `replaceObject`/`enableReplaceObject` | **Reproduced by analogy, not called out.** Not used by the three consumers directly, but this is functionally the same shape as `DerReplacer.replace`/`resolve` already cited in §3 as the borrowed precedent — worth stating explicitly in §3 rather than leaving the correspondence implicit. |
| `useProtocolVersion` (OOS) | **Dropped, reasonable.** No DER equivalent needed — DER's schema-driven framing is self-describing per encode, not globally versioned the way OOS's protocol version is. Not previously stated as a decision. |
| `reset()` (OOS) | **Dropped, appears safe for the three consumers** (each opens a fresh stream per operation; none was found relying on handle-table reset) — but this is a general capability of the retired class with no DER equivalent, worth one explicit line rather than silence. |
| `readStreamHeader`/`writeStreamHeader`, `readClassDescriptor`/`writeClassDescriptor` | **Dropped, subsumed by design.** Not overridden by any of the three consumers; DER's own TLV framing (§ObjectCodec) replaces the role these play. Reasonable, but implicit rather than stated. |
| **`ObjectInputFilter` (`getObjectInputFilter`/`setObjectInputFilter`, JEP 290/415)** | **Dropped / not addressed — the most significant finding of this catalog.** `JceKeyStore.engineLoad` installs a custom, load-bearing `DeserializationChecker` filter (see §7.1) specifically to bound the `SecretKeyEntry` decode. This is a real security control §3.3/§4.2 do not mention at all. Whatever `JceKeyStore` does going forward (DER-only or dual-read, §4.2/§7.1), this filter's allowlist/size-bound *behavior* needs an explicit successor — either the DER path's fixed-type contract (§4.1: "payload must have DER support") is argued to structurally subsume it (plausible, since `@AtomicSerial` already restricts reconstructable types to a known, registered set — but this argument is not made anywhere in the current SOW), or the filter's role is explicitly retained (as discussed in §7.1 for the dual-read case). Not resolved here; flagged as a gap this SOW should account for before implementation. |
| Low-level `DataInput`/`DataOutput` passthrough (`readInt`, `writeUTF`, etc.) | **Not applicable.** `JceKeyStore` already uses `DataInputStream`/`DataOutputStream` directly for its non-OIS framing (§3.3) — never reaches these via OIS/OOS. |

---

## 8. Independent verification (2026-07-11): internal call-site census for `SignedObject`/`SealedObject`/`JceKeyStore`

Dispatched as a follow-up to the independent Fable 5 review of §2.1/§7 (below), specifically to
ground P0's migration scope in actual usage rather than continued inference from §3 alone.
Searched both `/home/user/GitHub/DirtyChai/src` (all bundled modules, `.java` only, excluding
test trees) and `/home/user/GitHub/JGDMS` for real construction/call sites of all three classes,
independent of the code-path analysis already in §3.

**`SignedObject`: zero internal callers found.** The only hit in the whole DirtyChai tree is the
class's own Javadoc usage example (`SignedObject.java:49`). No other `java.base` code, and no
other bundled module, constructs or reads a `SignedObject` anywhere. Confirms §3.1 as previously
written (the class's own constructor/`getObject()` *is* the mechanism, §3.1's finding stands) and
additionally establishes that P0's `SignedObject` migration has **no downstream internal callers
to also update** — it is a self-contained edit to one class.

**`SealedObject`: exactly one internal consumer chain, confirmed narrow.** `SealedObject` is used
internally only by `com.sun.crypto.provider.KeyProtector` (`KeyProtector.java:327,354,363,372-376`)
to seal/unseal private and secret keys, via a package-private subclass
`SealedObjectForKeyProtector` (`SealedObjectForKeyProtector.java:34`) — which is exactly the class
§3.3's `JceKeyStore.engineStore`/`engineLoad` finding already names (`sealedKey` field,
`~line 643`/`~line 841`) and §7.1's `DeserializationChecker` filter already gates (depth-1 class
check). `SharedSecrets`/`JavaxCryptoSealedObjectAccess`
(`SharedSecrets.java:111,507-515`) is a `jdk.internal.access` bridge exposing `SealedObject`'s
package-private `getExtObjectInputStream` to `com.sun.crypto.provider` — plumbing for this same
`KeyProtector`→`JceKeyStore` path, not a separate, previously-unaccounted-for consumer. **No new
migration scope beyond §3.2/§3.3/§7.1 as already written** — this confirms the existing analysis
was already complete, rather than surfacing a gap.

**`JceKeyStore`: registration site confirmed, and confirmed not default-loaded anywhere.**
Registered as the `"JCEKS"` `KeyStore` service by `SunJCE.java:765-766`
(`ps("KeyStore", "JCEKS", "com.sun.crypto.provider.JceKeyStore")`) — the actual service-provider
config `KeyStore.getInstance("JCEKS")` resolves through. Checked whether anything reaches JCEKS
*without* an application explicitly asking for it, since that would have changed P0's urgency:
`java.security:323` sets `keystore.type=pkcs12` as the platform default; the JKS↔PKCS12
`keystore.type.compat` cross-load (`:328-333`) never involves JCEKS; `keytool`
(`Main.java:1399`) touches the string `"JCEKS"` only in a type-probe branch when a file already
opened turns out to be that type, not as a default action; no TLS/`KeyManagerFactory`/
`TrustManagerFactory` default path was found constructing a JCEKS keystore. **Confirmed: JCEKS,
and therefore this entire `SealedObject`/`JceKeyStore` OIS dependency, is reachable only when an
application explicitly requests a `"JCEKS"`-type keystore** — not a platform default, not touched
by TLS defaults or bundled tooling. Also confirmed by grep: plain JKS keystores
(`sun.security.provider.JavaKeyStore`) have zero references to `SealedObject`/`JceKeyStore` — the
OIS dependency is isolated to the JCEKS type specifically, nothing bleeds into the more commonly
used JKS/PKCS12 types.

**JGDMS's own source: zero hits, confirmed.** Grepped `/home/user/GitHub/JGDMS` for all three
class names: no `.java` source anywhere touches `java.security.SignedObject`,
`javax.crypto.SealedObject`, or `com.sun.crypto.provider.JceKeyStore`. The only textual matches
are JGDMS-STD-006's own DER wire-format ASN.1 constructs, named `SignedObject`/`SealedObject` by
design analogy (e.g. `docs/JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md:1738`,
`SignedObject ::= SEQUENCE {...}`) — unrelated to the JDK classes. Confirms JGDMS itself has no
stake in this migration beyond the SOW's advisory role; nothing in JGDMS's own codebase needs to
change regardless of how DirtyChai resolves §2/§3/§4.

**Explicit limits of this census, stated rather than implied away:** this is a complete
enumeration of what's reachable by static search across DirtyChai's and JGDMS's own source — it
is not, and cannot be, a claim about arbitrary third-party application code. `SignedObject`,
`SealedObject`, and `KeyStore.getInstance("JCEKS")` are all public JDK APIs; any external
application deployed on DirtyChai can call any of them directly, and that dependence is invisible
to a source-tree search of either repo. That category remains open and unenumerable — it does not
block P0 (which only needs to migrate the classes' own internal mechanism, §3), but it is relevant
to §4.2/§7.1's on-disk-compatibility decision: the population of "existing JCEKS keystores with
secret keys in the wild" is exactly the unenumerable external-application category, which is why
§4.2 poses dual-read vs. DER-only as a real operational tradeoff rather than something this census
can resolve by search.

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
- §7 verification sources: `JGDMS-Board-Reviewer-Guidance.md` (`~/GitHub/JGDMS/JGDMS/docs/`),
  reflex 4 ("ungated reconstruction doors," ~line 501) and the pre-deletion-audit reflex
  (~lines 788-796); `com.sun.crypto.provider.JceKeyStore`'s `DeserializationChecker`
  (`JceKeyStore.java` ~lines 841-859, 949-994); `java.security.SignedObject.readObject`
  (`SignedObject.java` ~lines 264-277); `javax.crypto.SealedObject.readObject` and
  `extObjectInputStream.resolveClass` (`SealedObject.java` ~lines 429-478); `ObjectCodec`'s
  `checkAtomicDeSerializationPermitted`/`ATOMIC` gate
  (`jgdms-der/src/main/java/au/net/zeus/jgdms/der/object/ObjectCodec.java` ~lines 156-233).
- §2.1 verification sources: `sun.misc.Unsafe.getUnsafe()`
  (`src/jdk.unsupported/share/classes/sun/misc/Unsafe.java:111`);
  `sun.reflect.ReflectionFactory.getReflectionFactory()`/`newConstructorForSerialization`
  (`src/jdk.unsupported/share/classes/sun/reflect/ReflectionFactory.java:83-118`);
  `jdk.unsupported`'s `module-info.java` (unconditional `exports`/`opens` of `sun.misc`,
  `sun.reflect`); `java.lang.reflect.AccessibleObject.checkPermission()`
  (`src/java.base/share/classes/java/lang/reflect/AccessibleObject.java:84-88`);
  `java.lang.System`'s `isUnsafeReflectionFrame()` (~line 3062, `setSecurityManager()`-only
  StackWalker heuristic, not a general reflection gate). Empirical test run against the built
  product image `/home/user/GitHub/DirtyChai/build/linux-x86_64-server-release/images/jdk`
  (`openjdk version "27-internal"`), standalone test code outside the DirtyChai repo.
