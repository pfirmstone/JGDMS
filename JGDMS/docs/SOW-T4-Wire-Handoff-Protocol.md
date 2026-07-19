# T4 Wire-Protocol Handoff — byte/framing-level specification

- **Status:** IMPLEMENTED (this document describes what is built, not a plan).
- **Origin:** `SOW-Smart-Proxy-Isolation-Wiring.md` T4, carried forward as
  `SOW-Smart-Proxy-Isolation-Remaining-Work.md`'s T2 ("Wire-protocol handoff").
  Implements `au.net.zeus.jgdms.loader.isolation.SubProcessWireHandoff` for real.
- **Companions:** read `SOW-Smart-Proxy-Isolation-Wiring.md` T2/T4 and
  `SOW-Smart-Proxy-Isolation-Remaining-Work.md` §2/T2 first — this document
  assumes their acceptance criteria (S1–S6, the three-axes check, G13) and
  states exactly how each is satisfied by the code below.

---

## 1. Scope and non-goals

**In scope:** the byte-level framing and message protocol between the
process that resolves a downloaded smart proxy (the *orchestrating client*,
running `PreferredProxyCodebaseProvider.resolve()`) and the per-principal
isolated subprocess that reconstructs it; the subprocess-side reconstruction
gate stack; the client-side thin-stub construction and business-call
forwarding.

**Out of scope (explicitly, not silently assumed):**

- **Real OS-process spawning.** `SubProcessLauncher`'s production
  implementation (the actual `fork`/`exec` + UDS bring-up for the
  subprocess) remains `UnsupportedSubProcessLauncher` — that is a *separate*
  task, not scoped to T4. This document's protocol is transport-agnostic: it
  operates over any `java.nio.channels.ByteChannel` obtained from
  `SubProcessLauncher.Spawned.openWireChannel()` (a new accessor this task
  adds — see §5). A real launcher supplies a genuine Unix Domain Socket
  channel there; nothing else in this protocol changes.
- **`net.jini.jeri.uds` as the business-call transport.** Considered and
  rejected for *this* deliverable — see §6 ("why not reuse JERI/UDS
  directly").
- **Wiring `SubProcessAdministrable`/`PolicyAdmin` onto a real transport.**
  That remains T1 (`SubProcessDynamicPolicy` T3(c), a parallel task) and is
  untouched here. The wire-handoff channel this document specifies is
  deliberately **separate** from the admin channel (S1).

---

## 2. Frame format (`WireFraming`)

All messages, in both directions, are carried as one frame:

```
offset  0 : int32  magic     = 0x53504857   ("SPHW" — SubProcess Handoff Wire)
offset  4 : byte   version   = 1
offset  5 : byte   type      (see §3)
offset  6 : int32  length    payload length in bytes, 0 <= length <= MAX_PAYLOAD_LEN
offset 10 : byte[length]     payload
```

All multi-byte integers are big-endian. The 10-byte header is always
read/written in full before the payload is touched.

`MAX_PAYLOAD_LEN = 64 MiB` (67,108,864 bytes) — generous for a marshalled
control/business message (interface names, a marshalled `MarshalledInstance`
envelope, a marshalled bootstrap-proxy stub, invocation arguments/results),
while bounding worst-case memory commitment from a single hostile length
field. **Codebase JAR bytes never travel over this channel** — those are
fetched by the subprocess directly from the codebase URLs inside the reused
`resolve()` call, exactly as the legacy in-process path already does.

### Allocate-after-validate discipline (the readNewArray-class fix, mirrored here)

`WireFraming.readFrame` validates the declared `length` against
`MAX_PAYLOAD_LEN` **before** calling `ByteBuffer.allocate(length)`. A
hostile length far above the ceiling is rejected with a bounded `IOException`
and commits **no** memory. Below the ceiling, the payload is still read
incrementally (looping `channel.read(buffer)` until full or EOF), so a
truncated payload on an in-bounds-but-lying length fails fast with a bounded
`EOFException` rather than hanging or silently returning a short buffer.

This mirrors, at the frame layer, the fix already landed in
`AtomicMarshalInputStream` (`readNewArray` / `readBlockDataLong` /
`decodeUTF`): a wire-declared count must never size an allocation before it
is validated. See `WireFramingTest` for the adversarial proof (a ~2 GiB
declared length, header-only, rejected before allocation and before any
attempt to read the — nonexistent — payload bytes).

---

## 3. Message types and payload shapes (`WireHandoffCodec`)

| Type | Value | Direction | Payload |
|---|---|---|---|
| `REQUEST` | 1 | client → subprocess | handoff request (§3.1) |
| `REPLY_OK` | 2 | subprocess → client | successful handoff reply (§3.2) |
| `REPLY_ERROR` | 3 | subprocess → client | closed-vocabulary error (§3.3) |
| `INVOKE_REQUEST` | 4 | client → subprocess | business method call (§3.4) |
| `INVOKE_REPLY_RESULT` | 5 | subprocess → client | business method return value |
| `INVOKE_REPLY_EXCEPTION` | 6 | subprocess → client | business method threw (§3.3 shape) |

### Per-field independent sub-streams (a deliberate, load-bearing choice)

Each payload is a sequence of **independently marshalled fields**: every
field is written to, and read from, its *own* `AtomicMarshalOutputStream` /
`AtomicMarshalInputStream`, each length-prefixed (4-byte big-endian, same
allocate-after-validate check as the outer frame) inside the payload — never
multiple fields sharing one stream's object graph.

This is not incidental style. It mirrors an existing, deliberate JGDMS
pattern for exactly this hazard class — the historical commit note on
`ProxySerializer`: *"marshal a proxy separately from the stream, such that
it has no shared state with the stream."* It is required here because
adversarial testing while building this codec found a **real, reproducible
defect** in `AtomicMarshalInputStream.readNewArray`: when a reference array
(e.g. a top-level `Object[]` envelope) contains an `Externalizable` element
that is *not* the array's last element, the shared stream's block-data state
is left corrupted after that element is read; the next element's read throws
`StreamCorruptedException`, which hits a **pre-existing null-`exceptions`-
list bug** in `readNewArray`'s own catch block (`Cannot invoke
"java.util.List.isEmpty()" because "exceptions" is null` —
`AtomicMarshalInputStream.java:2039`), producing an opaque
`NullPointerException` instead of a clean decode error.

Per-field independent streams sidestep this defect entirely — no field's
decode is ever "followed by another element still to be read in the same
stream/array" — rather than depending on a fix to already-landed,
security-critical decode machinery this task does not own.

> **Flagged for the board, and as a follow-up bug report against
> `AtomicMarshalInputStream`:** an `Externalizable` array element that is
> not the array's last element can crash the decoder with an NPE instead of
> a clean `IOException`. Reproduced deterministically (see the now-removed
> throwaway `DiagArrayCodecTest` in this task's development history — not
> committed, since it exists only to document the repro steps here). Not
> fixed as part of T4: out of this task's scope, and touching
> `AtomicMarshalInputStream` is its own security-critical change needing its
> own review.

### 3.1 `REQUEST` (client → subprocess) — 7 fields

| # | Field | Type | Notes |
|---|---|---|---|
| 1 | `assertedPoolingKey` | `String` | The client's `IsolationPoolingKey.value()`. The subprocess re-derives its **own** key independently (from how it was spawned) and fails closed on mismatch — defence in depth against a request replayed or misdirected onto the wrong subprocess. |
| 2 | `path` | `String` | Codebase annotation string. **Diagnostic only.** `resolve()` re-derives its own authoritative `path` from `bootstrapProxy.getClassAnnotation()` and never trusts this field as an input to reconstruction. |
| 3 | `verifyCodebaseIntegrity` | `Boolean` | Distilled from the client's `IntegrityEnforcement` context element. |
| 4 | `serviceProxy` | `MarshalledInstance` | **Still-marshalled.** The client never calls `.get()` on it; writing it only serialises its own opaque `byte[]`/`String`/`int` fields (`MarshalledInstance.serialize(PutArg,...)`), never resolving or naming any class the wrapped object references. |
| 5 | `bootstrapProxy` | `CodebaseAccessor` | A trusted, first-party JERI dynamic proxy (always `java.lang.reflect.Proxy`-based in production — `resolve()` itself asserts this via `Proxy.getInvocationHandler`), not hosted mobile code; safe to move between trusted processes exactly like any other remote reference. |
| 6 | `methodConstraints` | `MethodConstraints` | Nullable. |
| 7 | `serverPrincipals` | `Principal[]` | Nullable. Re-derived by the client from the same `ServerSubject` context element `resolve()` itself consults at the T1 choke point. |

### 3.2 `REPLY_OK` (subprocess → client) — 2 fields

| # | Field | Type | Notes |
|---|---|---|---|
| 1 | `interfaceNames` | `String[]` | The resolved business-interface closure (public interfaces only) the reconstructed object implements, computed **after** `HostedProxyGuard` has already passed — guaranteed not to include any management-plane interface. |
| 2 | `hostedId` | `String` | Opaque id (a UUID) this connection's business calls, and the client's DGC-analogue bookkeeping, are scoped to. |

### 3.3 `REPLY_ERROR` / `INVOKE_REPLY_EXCEPTION` — 2 fields

| # | Field | Type | Notes |
|---|---|---|---|
| 1 | `category` / `exceptionClassName` | `String` | `REPLY_ERROR`: a short, closed-vocabulary label (`MALFORMED_REQUEST`, `KEY_MISMATCH`, `RECONSTRUCTION_REFUSED`, `RECONSTRUCTION_FAILED`, `HOSTING_REFUSED`, `NULL_RESULT`, `NO_INTERFACES`, `INTERNAL`, `PROTOCOL`). `INVOKE_REPLY_EXCEPTION`: the business exception's class **name only**, never the resolved `Class`. |
| 2 | `message` | `String` | Bounded-length diagnostic text. |

**Deliberately never serialises the actual `Throwable`.** Reconstructing an
arbitrary exception object client-side would reopen exactly the decode
surface this whole mechanism exists to close on the request side; the reply
direction gets the same discipline. `ForwardingInvocationHandler`
synthesizes a fresh `RemoteException` (if the called method declares one) or
a plain `RuntimeException` client-side, carrying the reported category/class
name and message as **text only**.

### 3.4 `INVOKE_REQUEST` (client → subprocess) — 3 fields

| # | Field | Type | Notes |
|---|---|---|---|
| 1 | `methodName` | `String` | |
| 2 | `parameterTypeNames` | `String[]` | Used to match against the resolved, `HostedProxyGuard`-cleared method set — see §4.4. |
| 3 | `args` | `Object[]` | Nullable elements. |

### `INVOKE_REPLY_RESULT` — 1 field: the return value.

---

## 4. Where each gate runs, and why that placement is correct

### 4.1 SCAP/BAE verdict gate, `DeSerializationPermission("ATOMIC")`, endpoint-assigned `ResolutionContext`

**Mechanism:** `SubProcessReconstructionServer.reconstruct()` calls, inside
the subprocess:

```java
sp = new PreferredProxyCodebaseProvider().resolve(
        req.bootstrapProxy, req.serviceProxy,
        subprocessRootLoader, subprocessRootLoader, context);
```

This is **literally the same, already-reviewed legacy in-process
reconstruction path** — `checkVerdictForJar`, per-JAR digest verification and
`DigestGrant` issuance, `DownloadPermission`/`URLPermission` grants,
`PreferredClassLoader` creation, and the terminal
`serviceProxy.get(loader, true, verifier, context)` call (where
`DeSerializationPermission("ATOMIC")`/`"PROXY"` are enforced against each
resolved class's protection domain, and where the DER/JOSS decode path
constructs its endpoint-assigned `ResolutionContext`) — called here, inside
the subprocess, instead of being reimplemented.

**Why this placement is correct, and why reuse (not reimplementation) is the
right call:** two independent implementations of the same security-critical
gate stack can drift (G1/G8 — "all sources of truth must agree"). Reusing
the one, existing implementation cannot drift from itself. The gates run
*after* the process boundary crossing (inside the subprocess) exactly
because `resolve()` itself now executes inside the subprocess — nothing
about *when* or *how* the gates fire has changed, only *where the whole
call* runs.

**Required invariant, defended twice:** the subprocess process must run
with smart-proxy isolation routing **disabled**
(`net.jini.loader.pref.smartProxyIsolation.enabled` unset/false), so the
reused `resolve()` call takes the ordinary in-process branch rather than
recursing into another isolation hand-off.
`SubProcessReconstructionServer`'s constructor defensively re-checks this
system property and refuses to start if it is set — but the **authoritative
backstop**, which holds even if that defensive check were ever removed or
bypassed, is `resolve()`'s own default `UnsupportedIsolationRouter`, which
fails closed regardless.

### 4.2 Canonical pooling-key re-validation (defence in depth, S5-adjacent)

`reconstruct()` compares `req.assertedPoolingKey` against the subprocess's
own `ownKey` (supplied at construction by whatever spawned it for a specific
principal) and refuses on any mismatch, **before** touching `resolve()` at
all. This guards against a request replayed or misdirected onto the wrong
subprocess — a confused-deputy / wrong-target hazard distinct from, and in
addition to, `SubProcessPool`'s own per-principal keying (which already
guarantees *the client* only ever obtains a handle to the correct
subprocess; this is the subprocess's own independent confirmation that the
request it received actually names it).

### 4.3 `HostedProxyGuard` reject-on-load

Runs **immediately after `resolve()` returns**, on `sp.getClass()`, **before**
the resolved object's interfaces are ever computed for the reply or
dispatched to — exactly the point the wiring SOW names: *"since this is
where a hosted proxy's interface closure first becomes a resolved `Class`
set."* A `SecurityException` here (`HOSTING_REFUSED`) aborts the whole
handoff — no `REPLY_OK` is ever sent, and no live reference is ever recorded
on `SubProcessHandle` (see `handoff_recordsLiveReferenceOnHandle`-adjacent
test coverage).

### 4.4 Business-call dispatch is method-set-restricted, not reflection-open

The client's `INVOKE_REQUEST` is matched against a `Set<Method>` gathered
**only** from the public-interface closure `HostedProxyGuard` has already
cleared (`publicInterfaceClosure(sp.getClass())` → `c.getMethods()` for each
resolved interface). A method not in that set (including any `Object`
method, any non-public method, or — structurally, since
`HostedProxyGuard` already rejected the closure otherwise —
`SubProcessAdministrable`/`PolicyAdmin` methods) is refused with
`NoSuchMethodException`, never reflectively invoked. This is what makes
"the client sends a method name string" safe: the server never resolves an
arbitrary method by reflection against the whole object, only against a
pre-computed, already-guarded allow-list.

---

## 5. The `SubProcessHandle`/`SubProcessLauncher` extension this task adds

`SubProcessWireHandoff.handoff(SubProcessHandle handle, ...)` needs *some*
channel to the subprocess; none was exposed by the landed T2(wiring)
surface (only `adminSurface()` was). This task adds:

- `SubProcessLauncher.Spawned.openWireChannel()` — a `default` method
  (fails closed with `UnsupportedOperationException`, mirroring
  `UnsupportedSubProcessLauncher`'s existing pattern) so pre-existing test
  fakes implementing `Spawned` keep compiling unchanged.
- `SubProcessHandle.openWireChannel()` — a thin, fail-closed-if-torn-down
  delegator to the above.

**One connection is opened per hosted-object handoff** (not multiplexed):
the connection carries exactly one `REQUEST`/`REPLY` exchange, then — only
on success — that one hosted object's `INVOKE_REQUEST`/`INVOKE_REPLY`
traffic for the connection's lifetime. This is a deliberate scope
simplification (see §6): correct and fully testable without a real
multiplexing transport, at the cost of one OS connection per live proxy
reference rather than one per subprocess. Flagged as a reasonable follow-up
for connection-count efficiency, not a correctness gap.

---

## 6. Why not reuse `net.jini.jeri.uds` directly for the business channel

Reusing JERI's own `UdsServerEndpoint`/`BasicJeriExporter` for the hosted
object's export was seriously considered — it would inherit JERI's own
DGC/at-most-once/method-constraints machinery for free. It was **not**
chosen for this deliverable, for two independent reasons:

1. **Module layering.** `jgdms-pref-class-loader` (where `SubProcessHandle`/
   `SubProcessWireHandoff` live) does not depend on `jgdms-jeri`, and adding
   that dependency would be a reverse-layering smell (a low-level
   classloading module pulling in the whole RMI/invocation-layer stack for
   one feature) — exactly the "reverse package-coupling edge" the sibling
   T5 (`DerProxySerializer`) task was independently cautioned against.
   Placing the real export mechanism in a higher module that depends on
   both is a legitimate follow-up, not done here.
2. **Environment.** `net.jini.jeri.uds`'s own round-trip test suite
   (`UdsEndpointRoundTripTest`) is currently **entirely disabled** in this
   sandbox — every test commented out, with a TODO citing a socket-file-
   permission-restriction failure specific to this environment
   (`java.io.IOException: could not restrict socket file ... refusing to
   bind an unprotected control socket`). Building this task's adversarial
   self-tests against a transport that cannot currently be exercised in
   this environment would have produced untested code, not tested code.

Instead, the business-call layer is a small, self-contained,
**method-set-restricted** reflective relay (§4.4) riding the same
`WireFraming`/`WireHandoffCodec` machinery already built and tested for the
control exchange. It does **not** reimplement JERI's DGC lease/dirty-set
protocol; liveness is instead tracked client-side (§7). This is an explicit,
narrower scope than "a real JERI transport," flagged here for the board to
weigh: production hardening (real UDS export, JERI's proven at-most-once
semantics, connection multiplexing) is a reasonable, identified follow-up,
not silently assumed to already exist.

---

## 7. DGC-analogue liveness (ties into `SubProcessHandle`)

`SubProcessHandle.hostReference` / `referenceRetired` (landed by T2(wiring))
model retirement as "the DGC dispatcher actually processed a clean call or
lease expiry" — explicitly **not** connection-close (S4).

This implementation drives that model from a **client-process-local**
signal: at successful handoff, `SubProcessWireHandoffImpl` calls
`handle.hostReference(hostedId)`, then registers a `PhantomReference` on the
returned thin stub (via a hand-rolled `ReferenceQueue` + daemon-thread pump,
since this module compiles at `--release 8`, pre-`java.lang.ref.Cleaner`).
When the client process's own garbage collector proves the stub is
phantom-reachable — i.e. *this process* holds no more references to it —
the registered action calls `handle.referenceRetired(hostedId,
RetirementReason.CLEAN_CALL_PROCESSED)` and closes the connection.

**Why this is a sound substitute for the literal DGC-dispatcher signal, not
a weaker one:** the isolated subprocess serves **only** the client process
that spawned it (per-principal, non-shared pooling — `SubProcessPool`'s
whole design). There is no third party whose reference count could differ
from this process's own. So "does *this* process still hold a live
reference" is not merely a proxy for the question `SubProcessHandle` needs
answered — for this architecture, it *is* that question, answered locally
and correctly without needing a cross-process acknowledgement. This is
explicitly **not** the connection-close signal (S4's prohibited shape):
phantom-reachability is a true-unreachability proof from this JVM's own GC,
observed independently of whether the TCP/UDS connection is still open.

**Known, flagged simplification:** `RetirementReason.LEASE_EXPIRED` is
defined by T2(wiring) for the case a client crashes/disconnects without a
clean call; this implementation does not currently distinguish it from
`CLEAN_CALL_PROCESSED` (phantom-reachability from garbage collection is used
uniformly). Wiring a literal JERI-dispatcher-level lease-expiry signal is a
deeper undertaking (touching `org.apache.river.jeri.internal.runtime`
internals this task does not otherwise depend on) and is left as a follow-up
if that distinction is ever needed for observability; it does not affect
teardown correctness, since both reasons are treated identically by
`SubProcessHandle.referenceRetired` today ("the reason is informational...
does not change the teardown decision").

---

## 8. Three-axes check (S3), stated explicitly

- **Byte flow:** both directions, on one connection per hosted object —
  request bytes client→subprocess, reply bytes subprocess→client, for both
  the handoff exchange and every subsequent business call.
- **Request origination: client-initiated only.**
  `SubProcessWireHandoffImpl` only ever calls
  `SubProcessHandle.openWireChannel()` (opening a connection *to* the
  subprocess) and then writes a request before reading a reply,  never
  accepts an inbound connection, and never reads unsolicited bytes.
  `SubProcessReconstructionServer.serve()` is symmetric: given an
  already-open channel, it only ever reads-then-writes on it, and never
  opens an outbound connection back to a client. There is **no**
  subprocess-originated call toward the client anywhere in this mechanism —
  re-derived here explicitly, not assumed, and checked by
  `serve_neverOriginatesConnectionBackToClient`.
- **Authorization:** this business/wire-handoff channel is entirely separate
  from `SubProcessHandle.adminSurface()`'s admin channel; possessing one
  proves nothing about the other. If a future revision multiplexes
  `SubProcessDynamicPolicy`'s grant-push traffic onto this same physical
  connection, its authorization must be re-derived independently (S1's
  admin authentication gate), never inherited from having successfully
  completed a business handoff here.

---

## 9. Adversarial self-test summary

All probes below are automated tests in
`jgdms-pref-class-loader/src/test/java/au/net/zeus/jgdms/loader/isolation/`
(`WireFramingTest`, `SubProcessWireHandoffEndToEndTest`,
`DecodeDepthGuardTest`), run against the real implementation, not reasoned
about from the code shape (G13).

1. **A `MarshalledInstance` crafted to reconstruct outside the gate.**
   `handoff_hostedProxyImpersonatingAdminInterface_refused`: a business
   object additionally implementing the real `SubProcessAdministrable` is
   refused (`HOSTING_REFUSED`), with **zero** live reference recorded.
   `handoff_sameNamedDecoyInterface_notRejected` proves the guard is
   identity-based, not name-based (a same-named decoy interface in a
   different package is *not* rejected).
2. **Oversized payload (breadth) rejected before allocation.**
   `WireFramingTest` proves the frame-length ceiling check runs before
   `ByteBuffer.allocate`; `serve_hostileOversizedRequestFrame_rejectedNotHang`
   proves the same against the live dispatcher (a ~2 GiB declared length,
   header-only, does not hang or crash the subprocess thread).
3. **Deeply-nested payload (depth) rejected before the real decode.** See
   §10 (Finding 2) -- `DecodeDepthGuardTest` and
   `SubProcessWireHandoffEndToEndTest#deepNestedReply_rejectedCleanly_...`.
   **Corrected citation (2026-07-20 board review):** an earlier revision of
   this document wrongly attributed a depth bound to
   `AtomicMarshalInputStream`/`ObjOutputStream` via Board Guidance G10/G12.
   G10/G12 describe the **DER** `Any`/collection codec's depth counter, a
   different, unrelated code path. `AtomicMarshalInputStream`/
   `ObjOutputStream` -- the codec this wire-handoff path actually uses --
   has **no depth ceiling of its own at all** (confirmed by grep: zero
   hits for any nesting-depth counter). This was a new, previously-
   unreachable cross-process decode surface for attacker-shaped data, not
   an already-accepted residual; §10 covers the fix.
4. **Wrong-target / replay.** `handoff_wrongAssertedPoolingKey_refused`: a
   request asserting a different principal's pooling key is refused
   (`KEY_MISMATCH`) even though it arrives over a channel this subprocess's
   own launcher opened.
5. **No subprocess-originated callback.**
   `serve_neverOriginatesConnectionBackToClient`: exactly one connection is
   opened (by the client) and exactly one is accepted (by the subprocess)
   across a full successful handoff.

---

## 10. 2026-07-20 adversarial board review: three review rounds

A 3-seat adversarial board reviewed commit `967a59799` and returned BLOCK
across two review rounds (Findings 1-2 below), then a third round -- while
specifically probing `DecodeDepthGuard`'s own self-flagged blind spot for a
live crash -- found something more serious: silent wrong data, not a crash
(the silent-field-loss finding, below Finding 2). The core reconstruction-
gate property (§4, §8 above) was independently confirmed sound by all three
seats across all rounds and is unchanged by this section.

### Finding 1 -- the per-field-independent-stream design did not cover array-typed fields

**What was claimed, and why it was wrong.** `WireHandoffCodec`'s per-field
independent streams (§3) isolate separate top-level *fields* from sharing
corrupted stream state with each other -- but `INVOKE_REQUEST.args`
(`Object[]`) and `INVOKE_REPLY_RESULT` are themselves *single fields* whose
own value can be a multi-element reference array. Two board seats called
`encodeInvokeRequest`/`decodeInvokeRequest` and
`encodeInvokeReplyResult`/`decodeInvokeReplyResult` directly with a
2-element array containing a non-last `Externalizable` element and
reproduced the exact `NullPointerException` from
`AtomicMarshalInputStream.readNewArray:2039` in both directions. The
`WireHandoffCodec` javadoc's "sidesteps the defect entirely" claim was
empirically false for this field shape; corrected in that javadoc directly
(see the class-level "Correction (2026-07-20 board review)" note).

**Fixes landed:**

1. **Root cause, stage 1 (jgdms-platform, separate minimal commit):**
   `AtomicMarshalInputStream.java:2030-2039` -- the `StreamCorruptedException`
   catch branch dereferenced `exceptions.isEmpty()` unconditionally, but
   `exceptions` is only assigned in the sibling `ClassNotFoundException`
   branch. Null-guarded: `if (exceptions != null && !exceptions.isEmpty())
   break;`. At the time this landed, it closed the crash (uncaught
   `NullPointerException` &rarr; clean `StreamCorruptedException`) but not
   the underlying stream desynchronisation -- see the silent-field-loss
   finding below, which traced that desync to its actual root cause and
   closed it fully. **Current, final state:** this exact shape (a non-last
   `Externalizable` array element) now round-trips correctly end to end,
   field value intact -- not merely "fails cleanly."
2. **Client-side containment (`SubProcessWireHandoffImpl
   .ForwardingInvocationHandler.invoke`):** `decodeInvokeReplyResult` and
   `decodeInvokeReplyException` are now wrapped in `catch (Throwable t)`
   (previously: no exception handling at all around the client's decode of
   bytes from the isolated, potentially-adversarial hosted proxy -- the
   primary blocker). A decode failure now always synthesises a clean
   `RemoteException`/`RuntimeException` via the same `synthesizeException`
   path used for ordinary reported business exceptions, category
   `DECODE_FAILURE`. Kept regardless of stage-1's later full resolution --
   this is a correct, load-bearing defence for any *other* decode failure
   shape, known or not yet found.
3. **Server-side symmetry (`SubProcessReconstructionServer`):** the three
   `catch (Exception e)` sites guarding attacker-controlled decode
   (`reconstruct`'s `decodeRequest` call, `dispatchInvoke`'s
   `decodeInvokeRequest` call, and `dispatchInvoke`'s `target.invoke`
   +`encodeInvokeReplyResult` call) are now `catch (Throwable e)`, so a
   `StackOverflowError` (an `Error`, not caught by `Exception`) is
   contained the same way an ordinary decode `Exception` already was.

**Verified current behaviour:**
`SubProcessWireHandoffEndToEndTest#nonLastExternalizableArrayElement_roundTripsCorrectly_throughFullStack`
asserts the call now succeeds, with the `Externalizable` element's own
carried field intact on the far side -- upgraded from an earlier, more
conservative "fails cleanly" assertion once the silent-field-loss
investigation (below) found and fixed the actual root cause.

### Finding 2 -- unbounded decode-recursion depth (new, more severe)

`AtomicMarshalInputStream`/`ObjOutputStream` has no nesting-depth ceiling at
all (§9 item 3). A ~20,000-deep nested single-element `Object[]` chain (a
few hundred KB, trivially under `MAX_PAYLOAD_LEN`) drives a live
`StackOverflowError` through the same zero-exception-handling path as
Finding 1.

**Fixes landed:**

1. **`DecodeDepthGuard`** (new class): a best-effort, structure-aware
   byte-level pre-scan, applied to every `WireHandoffCodec.unmarshalOneField`
   call (i.e. every field of every message type, uniformly). It walks the
   standard `java.io.ObjectStreamConstants` tag protocol by hand -- verified
   empirically against this codec's own encoder output, not assumed --
   counting recursive nesting (array elements, object fields via the
   self-describing class-descriptor field table, class-hierarchy
   superclass chains) and throws `DepthExceededException` (a clean,
   O(`MAX_DEPTH`) rejection, `MAX_DEPTH = 64`) the moment a *confidently
   parsed* structure exceeds the ceiling -- long before the real,
   expensive/dangerous recursive decode would run.
   - **Explicitly not a complete parser, and documented as such in its own
     javadoc.** `Externalizable` content is only self-describing by
     out-of-band convention between a class's own `writeExternal`/
     `readExternal` (there is no grammar-level guarantee it is pure block
     data), and dynamic-proxy class descriptors (`TC_PROXYCLASSDESC`) are
     deliberately not modelled -- both to avoid any risk of this scanner
     mis-parsing legitimate traffic (a legitimate `CodebaseAccessor` stub,
     or a genuine business `Externalizable` argument) and silently
     rejecting it, which would be a functional regression, not merely a
     missed catch. When the scanner cannot fully account for a shape, it
     returns silently, claiming no safety guarantee for that payload.
   - **Encode-side asymmetry, stated explicitly.** The guard only applies
     to decode. `ObjOutputStream.writeNewArray` has the identical
     unbounded-recursion property as the reader, so a hosted business
     object that itself returns a pathologically deep structure can still
     drive a `StackOverflowError` **server-side, during encode** of the
     reply. This is not unguarded, however: it happens inside
     `SubProcessReconstructionServer.dispatchInvoke`'s own
     `catch(Throwable)` (Finding 1 fix item 3 above), so it is still
     turned into a clean `INVOKE_REPLY_EXCEPTION` naming
     `StackOverflowError`, which the client resynthesises as a
     `RemoteException` exactly like any other reported business exception.
     The end-to-end safety property (client application code never sees a
     raw `Throwable`) holds either way; only the *attribution* differs
     (client-side `DECODE_FAILURE` vs. a server-reported
     `StackOverflowError`), and
     `SubProcessWireHandoffEndToEndTest#deepNestedReply_rejectedCleanly_clientNeverCrashes_throughFullStack`
     accepts either rather than assuming one. An encode-side depth guard is
     a reasonable follow-up, not built here -- flagged rather than silently
     assumed unnecessary.
2. **Test payloads are hand-crafted bytes, not round-tripped through the
   real encoder.** Building a ~20,000-deep object graph and marshalling it
   via the real, equally-recursive `AtomicMarshalOutputStream` throws
   `StackOverflowError` **during test setup** on this environment's default
   thread stack -- confirmed empirically, not assumed. `DecodeDepthGuardTest`
   constructs the adversarial byte patterns directly (matching the standard
   tag protocol, one-time verified against small real-encoder samples),
   which is also the more faithful adversarial shape: a real attacker has
   no reason to go through a conforming encoder either.

**Verified:** `DecodeDepthGuardTest` (12 cases: shallow/at-ceiling/
over-ceiling boundaries for both array- and object-field-based nesting,
breadth-vs-depth non-confusion, primitive-array non-confusion,
`Externalizable` non-false-positive, and the literal ~20,000-deep repro,
each asserted to reject in well under the time a real recursive attempt
would take) and
`SubProcessWireHandoffEndToEndTest#deepNestedReply_rejectedCleanly_clientNeverCrashes_throughFullStack`
(the full client/subprocess round trip, real `target.invoke()` building the
deep structure, real dispatch).

### Finding 3 (third review round) -- silent Externalizable field loss, not a crash

While specifically probing whether `DecodeDepthGuard`'s own self-documented
proxy/`Externalizable` blind spot could still let a live crash through, one
seat found something worse: in the exact configuration `WireHandoffCodec`
uses (`AtomicMarshalInputStream.create(..., readAnnotations=false)`),
decoding a plain `Externalizable` object whose `readExternal()` reads a
reference-typed field via `ObjectInput.readObject()` **silently succeeded
with the field dropped** -- no exception, `readExternal()` never entered,
the object constructed via its no-arg constructor with the field at its
default value. The same class round-trips correctly through vanilla
`java.io.ObjectOutputStream`/`ObjectInputStream`, confirming this is
specific to this codec's `readAnnotations=false` path. `MarshalledInstance`
(`@AtomicSerial`) and `CodebaseAccessor`/`bootstrapProxy` (a dynamic
`Proxy`) go through entirely different decode branches and are unaffected
-- this was a business-call-channel reliability/correctness bug, never a
reconstruction-gate bypass.

**Root cause, confirmed by direct instrumentation and byte-level stream
dumps at each stage (not assumed), two compounding bugs:**

1. `ObjOutputStream.writeNewClassDesc` only set the class descriptor's
   `SC_EXTERNALIZABLE` flag for `@AtomicExternal`-annotated classes, even
   though the sibling method that actually decides whether to call
   `writeExternal()` (`writeNewObject`) checks plain `Externalizable.class
   .isAssignableFrom(theClass)`, with no annotation requirement. Real
   instance data was written under a class descriptor whose flags byte
   falsely claimed "not serializable, not externalizable, zero declared
   fields" (confirmed: `flags=0x00` in a byte-level dump of the actual
   encoded stream). On read, `AtomicMarshalInputStream` faithfully believed
   the wrong flags, took the field-table path instead of calling
   `readExternal`, found zero declared fields, and silently left the real
   written data on the wire unread. **Fixed:** also recognise plain
   `Externalizable`, matching `writeNewObject`'s own check and matching
   `AtomicMarshalInputStream`'s own read-side support for plain
   `Externalizable` (gated by `DeSerializationPermission("EXTERNALIZABLE")`,
   which never required the annotation either) -- three-way consistency
   restored.
2. Fixing (1) exposed a second, previously-latent bug:
   `AtomicMarshalInputStream.readyPrimitiveData` unconditionally consumed
   the next stream tag, assuming it was always a `TC_BLOCKDATA`/`TC_BLOCKDATALONG`/
   `TC_RESET` marker -- but `ObjOutputStream.drain()` only emits one of
   those when primitive data was actually buffered during `writeExternal`.
   An `Externalizable` class whose `writeExternal`'s *first* call is an
   object write (no preceding primitive write -- an entirely ordinary
   pattern) has no such marker; the real first tag was being silently
   swallowed here instead of reaching `readExternal`'s own read,
   desynchronising the stream position for everything that followed.
   **Fixed:** push the tag back (this class's own existing `pushbackTC`
   mechanism, already used elsewhere) instead of discarding it, so it is
   available, unconsumed, to whatever reads next.

**This also fully closed Finding 1's own deeper residual** (§ above): the
"stream desync when an `Externalizable` isn't the last array element" case
that the stage-1 fix could only report cleanly, not resolve, now round-trips
correctly -- the two bugs were the same underlying mechanism, reached via a
different entry point.

**Verified:** `ExternalizableFieldLossRegressionTest` (jgdms-platform;
object-only-write shape cross-checked against vanilla `java.io` as ground
truth, primitive-then-object regression guard, null/array-field boundary
cases, a field-less negative control, and the board's original array-shape
repro re-asserted with a *stateful* probe -- a field-less probe cannot
detect this failure mode at all, since it has nothing to lose) and the
upgraded `SubProcessWireHandoffEndToEndTest
#nonLastExternalizableArrayElement_roundTripsCorrectly_throughFullStack`.

---

## 11. Known issue for whoever builds the real subprocess launcher

One board seat's own repro run hung because
`org.apache.river.concurrent.ReferenceProcessor`'s non-daemon cleaner
thread is spawned as a side effect of first using
`AtomicMarshalOutputStream`/`AtomicMarshalInputStream`, preventing a bare
JVM from exiting on its own. Pre-existing, out of scope for this task's
fixes (`SubProcessLauncher`'s real OS-process/lifecycle implementation
remains `UnsupportedSubProcessLauncher`, per §1) -- noted here so whoever
builds the real launcher/subprocess lifecycle management accounts for it
(e.g. an explicit shutdown hook or daemon-thread configuration for the
subprocess's own JVM), rather than rediscovering it under time pressure.
