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
(`WireFramingTest`, `SubProcessWireHandoffEndToEndTest`), run against the
real implementation, not reasoned about from the code shape (G13).

1. **A `MarshalledInstance` crafted to reconstruct outside the gate.**
   `handoff_hostedProxyImpersonatingAdminInterface_refused`: a business
   object additionally implementing the real `SubProcessAdministrable` is
   refused (`HOSTING_REFUSED`), with **zero** live reference recorded.
   `handoff_sameNamedDecoyInterface_notRejected` proves the guard is
   identity-based, not name-based (a same-named decoy interface in a
   different package is *not* rejected).
2. **Oversized/deeply-nested payload rejected before allocation.**
   `WireFramingTest` proves the frame-length ceiling check runs before
   `ByteBuffer.allocate`; `serve_hostileOversizedRequestFrame_rejectedNotHang`
   proves the same against the live dispatcher (a ~2 GiB declared length,
   header-only, does not hang or crash the subprocess thread). Deep
   collection/`Any` nesting recursion is inherited from
   `AtomicMarshalInputStream`'s/DER's own existing bounds (pre-existing,
   already flagged in Board Guidance G10/G12 as a residual on the
   underlying codec, not newly introduced by this wire path).
3. **Wrong-target / replay.** `handoff_wrongAssertedPoolingKey_refused`: a
   request asserting a different principal's pooling key is refused
   (`KEY_MISMATCH`) even though it arrives over a channel this subprocess's
   own launcher opened.
4. **No subprocess-originated callback.**
   `serve_neverOriginatesConnectionBackToClient`: exactly one connection is
   opened (by the client) and exactly one is accepted (by the subprocess)
   across a full successful handoff.
