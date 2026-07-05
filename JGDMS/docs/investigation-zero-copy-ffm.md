# Investigation — Zero-Copy for JGDMS via Java FFM (MemorySegment / Arena)

**Status:** investigation (not a proposal to implement). JGDMS-side, committable to the
`investigate-zero-copy-ffm` branch. Not to be pushed or merged; destined for the
fresh-first review board.

**Question (Peter).** "Zero-copy" is a fashionable phrase attached to two very different
things. For JGDMS — the STD-006 DER wire format plus the JERI transport work — is there a
place where Java's Foreign Function & Memory API (`MemorySegment`/`Arena`, final since
JDK 22, present in the DirtyChai JDK 27 runtime) earns its keep *without* compromising the
security posture that is the whole point of the project? Investigate two concrete
applications: (a) a **same-host shared-memory JERI transport** beside UDS/SSL/QUIC, and
(b) **bulk-binary payloads carried by reference** (point clouds, scans, E57 blobs — the
survey / real-world-data vision) where DER carries the canonical metadata and the heavy
bytes live in a mapped segment the receiver reads in place.

**One-line verdict.** There are two distinct axes and only one of them is compatible with
DER. **FORMAT-level zero-copy** (Cap'n Proto / FlatBuffers — the wire *is* the in-memory
object layout) is **architecturally incompatible** with DER and must not be chased: it
would delete canonicity, the `@AtomicSerial` value-object reconstruction, the
receiver-chooses-the-implementation property, and gated deserialization — the four things
DER exists to provide. **TRANSPORT/IO-level zero-copy** (moving canonical DER bytes
between co-located processes without socket/kernel copies, and carrying large opaque
blobs by reference into a mapped region) is **compatible**, and is where FFM helps. Of the
two applications, **(b) bulk-binary-by-reference is the stronger, lower-risk first
increment** — it extends the existing §3.8 opaque-octet carve-out, needs no new transport,
and directly serves the survey vision. **(a) shared-memory transport** is real but is a
larger, security-critical build that should follow the P1 `SSLEngine` keystone and reuse
the same-host trust model UDS already established; it is *not* the first thing to do.
Across both, the load-bearing lesson is the one the compression investigation reached
independently: **shared memory is a same-host, mutually-trusting construct — the opposite
trust context from DER's untrusted-network home** — so untrusted-peer-written shared memory
is attacker-controlled memory and STILL needs fail-secure size-bound-before-allocation, the
gated-deserialization gate, and every STD-006 decode bound. FFM's bounds-checked
`MemorySegment` is a genuine security *asset* over `sun.misc.Unsafe`, but it does not by
itself close the use-after-free and TOCTOU threats, which are analysed in §5.

---

## 0. Framing — two axes of "zero-copy", only one compatible with DER

The single most important thing this investigation does is refuse to conflate two ideas
that the marketing term "zero-copy" fuses.

### 0.1 FORMAT-level zero-copy — INCOMPATIBLE with DER, do NOT pursue

Cap'n Proto and FlatBuffers achieve "zero parse" by making **the wire bytes identical to
the in-memory object layout**: a receiver `mmap`s (or points a pointer at) the received
buffer and reads fields *in place* through generated accessors, allocating nothing and
parsing nothing. The "object" is a typed view over the raw bytes; there is no
reconstruction step.

This is fundamentally at odds with every load-bearing DER property in STD-006:

- **Canonical TLV vs. in-place layout.** DER is Distinguished Encoding Rules — canonical
  tag-length-value, one encoding per value (STD-006 §3 principle 2), octet-sorted
  collections (§3.8), no `DEFAULT`, minimal INTEGER (§4.5). Canonicity is what makes DER
  bytes a value-equality proxy for Jini Entry byte-matching (§7.7.2), content-address
  digests (§7.8), and signature stability. A zero-copy struct format is the *opposite*:
  its layout is chosen for CPU-cheap field access (alignment, pointers, padding), and the
  same logical value has many valid in-memory encodings. You cannot have both "the bytes
  are the object layout" and "there is exactly one canonical byte form per value."

- **`@AtomicSerial` value-object reconstruction.** STD-006 decodes a private, opaque
  SEQUENCE into a `GetArg` field store (§3.9), then hands it to the class's `(GetArg)`
  constructor, whose *first action* is `check(GetArg)` invariant enforcement (STD-001)
  *before any field is assigned*. The result is a real, validated, typed value object
  with its own invariants. Format-level zero-copy has no constructor and no `check` — the
  "object" is a view over attacker-supplied bytes with no invariant ever enforced. That is
  precisely the property JGDMS spent years removing from Java serialization.

- **Receiver chooses the implementation.** In DER, the wire carries *values and bounds,
  never behaviour* (§3.8): a `Set` field arrives as octet-sorted elements and the receiver
  constructs whatever `Set` implementation it wants. In a zero-copy struct format the
  sender's memory layout *is* the receiver's object — the sender dictates representation.
  JGDMS deliberately severed this coupling (§2.1: Java serialization "encodes an object's
  concrete implementation and internal layout"; DER fixes that).

- **Gated deserialization.** DER decode is bounded-before-allocation (§3 principle 5) and
  runs behind `DeSerializationPermission("ATOMIC")` (STD-002 / the ATOMIC gate). A
  read-in-place format has no decode step to gate — the security check has nowhere to
  attach.

**Conclusion for axis 1:** on the FORMAT axis, DER stays not-zero-copy **by design**, and
the STD-006 comparison table's honest position ("verbose encoding, not optimised for the
smallest/fastest-to-parse wire; the zero-copy struct formats win *that* axis") should
stay exactly as it is. Do not try to make DER read-in-place. The compression investigation
reached the analogous conclusion on its own axis (compress the *transport*, never the
*encoding*); this is the same discipline: **do not deform the canonical encoding to chase a
performance format's property.**

### 0.2 TRANSPORT / IO-level zero-copy — COMPATIBLE, this is where FFM helps

The other axis is orthogonal to the encoding: **avoid copying bytes** as they move between
address spaces. The canonical DER envelope stays byte-for-byte identical; only the
*plumbing* underneath changes so that:

- co-located processes exchange the DER bytes through a shared memory segment instead of a
  socket (no `write()`/`read()` kernel copy, no loopback stack) — **application (a)**; and
- a large opaque payload that DER would otherwise carry inline as a giant `OCTET STRING`
  is instead *referenced* by the DER envelope and lives in a mapped `MemorySegment` the
  receiver reads directly — **application (b)**.

Both keep DER canonical, keep `check(GetArg)`, keep gated deserialization, keep the
receiver-chooses-the-implementation property. FFM (`MemorySegment` + `Arena` +
`FileChannel.map` / a POSIX-shm or Windows-section mapping) is the modern, bounds-checked,
`Unsafe`-free way to touch that off-heap memory. This is the whole of what is worth
pursuing, and the rest of this document designs it.

---

## 1. Where FFM fits the JERI transport-plugin architecture

The JERI transport SPI is already the right shape. Verified against source
(`jgdms-jeri/src/main/java/`):

- **Public pair:** `net.jini.jeri.Endpoint` (client) + `net.jini.jeri.ServerEndpoint`
  (server). A transport is thin — `net.jini.jeri.tcp` is three classes.
- **Request abstraction:** `Endpoint.newRequest(constraints)` → `OutboundRequestIterator`
  → `OutboundRequest` (each carries a request `OutputStream` + a response `InputStream`);
  server side, a `RequestDispatcher` receives `InboundRequest`s.
- **Shared connection SPI:** `net.jini.jeri.connection.{Connection, ServerConnection, …}`.
  **`Connection` (verified, `connection/Connection.java:55,64,75`) exposes
  `InputStream getInputStream()`, `OutputStream getOutputStream()`, and an OPTIONAL
  `SocketChannel getChannel()` that returns `null` when there is no channel.**

Two findings from that SPI matter here:

1. **`getChannel()` is typed `java.nio.channels.SocketChannel`, not a generic `Channel`.**
   A shared-memory segment is emphatically *not* a `SocketChannel`. So a shm-backed
   `Connection` returns **`null`** from `getChannel()` and drives its `getInputStream()`/
   `getOutputStream()` over the segment directly — exactly as the SSL provider does today
   (`SslConnection.getChannel()` returns `null`, `SslConnection.java:491-493`, and I/O
   goes through the `SSLSocket` streams). The mux's non-blocking path keys off a non-null
   channel; a shm connection simply does not offer it, and the blocking-stream path is
   used. **This means a shm transport slots in as a stream-backed `Connection` with no SPI
   change and no `getChannel()` contract violation.**

2. **The SPI does not bake in `Socket` or host:port** (the QUIC/UDS investigations already
   established this). `populateContext`, `checkConstraints`, `checkPermissions`,
   `writeRequestData`/`readResponseData` are where auth, constraints, and the request
   context wire in — identical whether the bytes came off a socket or out of a segment.

**Placement of FFM in the stack.** As with compression, the value/DER layer is untouched;
FFM lives strictly *below* it:

```
  application value  (@AtomicSerial objects)
  ───────────────────────────────────────────
  DER value/codec  (ObjectCodec / MarshalledInstanceCodec)  ← canonical bytes live here
  ───────────────────────────────────────────
  MarshalOutputStream / AtomicMarshalOutputStream (invocation)
  ───────────────────────────────────────────
  OutboundRequest.getRequestOutputStream()  (invocation/mux)
  ───────────────────────────────────────────
  Connection (getInputStream/getOutputStream; getChannel()==null for shm)
  ───────────────────────────────────────────
  Endpoint transport:  TCP / SSL(TLS) / QUIC / UDS / ── (a) ShmEndpoint ──
                                                        └─ FFM MemorySegment ring/slots
  ───────────────────────────────────────────
  (b) bulk-binary-by-reference: a MemorySegment-backed blob region mapped
      alongside, referenced from the DER envelope (extends §3.8 opaque-octet)
```

The DER codec produces canonical octets into the marshal stream exactly as today; the shm
transport moves those octets; the receiver reconstructs the identical canonical DER before
`AtomicMarshalInputStream`/the decoder sees a byte. Nothing canonical, signed, digested, or
matched ever depends on how the bytes travelled.

---

## 2. Application (a) — same-host shared-memory JERI transport (`ShmEndpoint`)

### 2.1 What it is and why

A `net.jini.jeri.shm` transport (proposed) that carries JERI invocation requests between
**co-located processes on one host** through an FFM `MemorySegment` mapped over POSIX
shared memory (`shm_open`+`mmap`) on Linux/macOS, or a named file-mapping / section object
on Windows — beside the existing TCP/SSL/QUIC and the shipped UDS endpoints. The motivation
is the same as UDS (`SOW-Unix-Domain-Socket-JERI-Transport.md`): co-located service
processes (the SPIRE-agent / broker / service split, the mesh node-local IPC of
`real-world-data-system`) talking JERI without a loopback socket. Shm goes one step past
UDS: **no kernel copy at all** — UDS still copies through the kernel socket buffer; a
shared segment is written once by the sender and read in place by the receiver.

**Honest scoping.** For *small control-plane* messages (the Entry/proxy/lookup traffic in
the compression investigation's table, hundreds of bytes to a few KB), the copy UDS
performs is already cheap and the shm win is marginal — the interesting latency is DER
encode/decode and `check(GetArg)`, not the byte move. **The shm transport earns its keep
only when messages are large or very high-frequency**, and its *real* payoff is as the
substrate for application (b): a shm data-plane where a control message hands over a
reference to a mapped blob region on the same host. **Recommendation up front: do not build
(a) as a general small-message transport to shave microseconds off lookups; build it, if at
all, as the co-located data-plane that (b) needs.**

### 2.2 Endpoint / ServerEndpoint / Connection mapping

- **`ShmServerEndpoint`** creates and owns the shared-memory object (a named POSIX shm
  segment `/jgdms-<uuid>` or a Windows section), sets its permissions to owner-only
  (§2.5), and publishes the name/handle the way `TcpServerEndpoint` publishes host:port —
  it becomes part of the exported proxy's endpoint data (a same-host client resolves it;
  an off-host client cannot use it, exactly the UDS dual-export story, §2.6).
- **`ShmEndpoint`** (client) opens/maps the same named segment (`shm_open`+`mmap` /
  `OpenFileMapping`+`MapViewOfFile`, via FFM downcalls or `FileChannel.map` where a
  file-backed mapping is acceptable). `newRequest(constraints)` allocates a request slot
  (or ring reservation, §2.3) and returns an `OutboundRequest` whose request
  `OutputStream` writes into the reserved segment region and whose response `InputStream`
  reads the reply region.
- **`ShmConnection` / `ShmServerConnection`** implement the `net.jini.jeri.connection`
  SPI. `getInputStream()`/`getOutputStream()` are thin adapters over the mapped segment
  (a `MemorySegment`-backed stream: write appends to the reserved region and signals;
  read blocks on the signal, then reads in place). **`getChannel()` returns `null`** (a
  segment is not a `SocketChannel`; §1 finding 1), so the blocking-stream path is used and
  the mux drives requests over the streams as it does for SSL today.

The DER envelope, `AtomicILFactory`, dispatchers, DGC, and the STD-006 §7.2 reducing-context
ACC block are all above the transport and untouched — additive, exactly like QUIC/UDS.

### 2.3 In-segment framing — per-message slots first, ring buffer later

Two candidate framings; recommend the simpler one first.

- **Per-message slots (RECOMMENDED first).** The segment is divided into a small header
  region + a pool of fixed-max-size message slots (plus an overflow path — see below). A
  request claims a free slot (a lock-free CAS on a free-slot bitmap in the header), writes
  its DER bytes + a length prefix into the slot, then signals the peer. The reply uses a
  paired reply slot. Simple, bounded, easy to reason about for the threat model
  (§5): every slot has a fixed maximum and the length prefix is validated against it
  *before* the reader trusts it. **Slots that cannot hold an oversized message fall back to
  the socket/UDS transport or to a (b)-style blob reference** — the shm slot never grows
  unboundedly.
- **SPSC/MPSC ring buffer (LATER, if profiling justifies).** A classic head/tail ring
  (producer writes at tail, advances tail; consumer reads at head, advances head; indices
  in the segment header, updated with release/acquire ordering). Higher throughput for
  streaming/high-frequency traffic, but the reader-writer synchronisation and the
  wrap-around/back-pressure logic are exactly where a malicious writer gets leverage
  (§5.3), so it is the *second* increment, built only once the slot design's threat model
  is settled and profiling shows the slots are the bottleneck.

**Framing carries the DER length up front so the reader can bound-before-map/read** — the
STD-006 principle-5 discipline applied at the transport frame, not only inside the codec.

### 2.4 Reader / writer synchronisation

Shared memory has no kernel to serialise access, so synchronisation is explicit and is a
first-order security concern, not an afterthought:

- **Wakeup / signalling.** A same-host notification primitive that does *not* require
  copying the payload: a companion UDS socket or a pipe as a doorbell (write one byte "slot
  N ready"), or a futex/eventfd (Linux) / named event (Windows) reached via FFM downcalls.
  **Recommendation: pair the shm segment with a UDS control socket** — the UDS socket
  carries the tiny doorbell + slot index (and can carry `SO_PEERCRED` later, §2.5), while
  the segment carries the bytes. This reuses the shipped UDS transport's connection
  lifecycle and its socket-file-permission trust gate, and keeps the segment itself purely
  a data region. (This also gives a natural place to hang the request framing / abort /
  DGC-ack obligations of §2.7 that the mux otherwise owns.)
- **Memory ordering.** Header indices / slot-ready flags are written with release
  semantics and read with acquire semantics (FFM `MemorySegment` var-handle access with
  the appropriate `MemoryOrdering`, or an explicit fence) so a reader never observes a
  "ready" flag before the payload write is visible. Getting this wrong is not just a
  correctness bug — a reader that sees "ready" before the bytes land reads stale/partial
  attacker-influenced memory (a TOCTOU flavour, §5.3).
- **No `ThreadLocal`.** JGDMS targets virtual threads (`no-threadlocal-virtual-threads` —
  banned). The shm event loop, slot bookkeeping, and any per-request state use
  `ScopedValue`/explicit passing, never `ThreadLocal`. The blocking-doorbell design sits
  well on virtual threads (a vthread blocks on the UDS doorbell read cheaply).

### 2.5 Security model — same-host trust boundary (access = who can map the segment)

This is the crux and it mirrors UDS exactly. **Access control on a shared-memory transport
is "who can map the segment", the direct analogue of the UDS socket-file `0700` permission
gate** (`SOW-UDS §5` defense-in-depth: (1) socket-file permissions gate who can connect,
(2) SPIFFE/mTLS identity, (3) the `@AtomicSerial`/`DeSerializationPermission` decode gate).
For shm:

1. **OS object permissions are the first gate.** The POSIX shm object is created with
   owner-only mode (`0600`, `umask`-guarded, in a private per-user runtime dir), or the
   Windows section object is created with a restrictive DACL (owner/SID-scoped). Only a
   process with the matching uid (or an explicitly-granted SID) can `mmap`/`MapViewOfFile`
   it. This is the "who can even see the bytes" boundary.
2. **Layer-2 identity is still SPIFFE/mTLS — over the companion UDS control socket.**
   The doorbell UDS socket carries the same layer-2 SPIFFE identity the shipped UDS
   transport uses (and, deferred, `SO_PEERCRED` kernel-attested uid/gid/pid, `SOW-UDS §5`).
   **The segment is host-local and unauthenticated by itself; identity is established on
   the control channel, not the data region.** This is the same split UDS already makes
   (byte transport swapped underneath, identity at layer-2).
3. **The decode gate is unchanged and mandatory.** Bytes read out of the segment are still
   DER decoded behind `DeSerializationPermission("ATOMIC")` with every STD-006 bound. The
   segment does not become a trusted channel because it is memory rather than a socket —
   see §5.

**Same-host does NOT mean same-trust.** UDS/shm are host-local, but two processes on one
host can be at very different privilege/trust (the SPIRE-agent vs. a service, or a
sandboxed foreign-protocol broker). The compression investigation reached the identical
"shared/co-located does not imply mutual trust" conclusion; the shm transport must hold it
too — hence layers 2 and 3 remain even though layer 1 already restricts *who* can map.

### 2.6 Endpoint selection / dual-export

Shm is host-local; a service reachable both locally and remotely exports **both** a shm (or
UDS) endpoint and a TCP/SSL/QUIC endpoint, and the client prefers the local one when
same-host (`SOW-UDS §7` dual-export; the open same-host-detection question is shared with
UDS and should be solved once for both). Off-host clients simply cannot resolve the shm
name and fall through to the network endpoint. This is the general tri/quad-export
degradation the QUIC investigation's §5 Q9 already flags.

### 2.7 Mux-obligation carry-over (do not lose DGC correctness)

The QUIC investigation's §3.2a pre-deletion mux audit applies to any transport that
bypasses the mux: the mux carries **application-level obligations beyond byte
multiplexing** — the DGC "request processed" acknowledgment (`ackRequired` /
`AcknowledgmentSource`, on which `BasicObjectEndpoint` leases depend — AUDIT-1), the
at-most-once delivery-status / `ABORT_PARTIAL` retry signal (AUDIT-2), and the
Close-vs-Abort half-close state machine (AUDIT-3). A shm transport that keeps the mux over
its streams (the recommended `getChannel()==null` stream path) **inherits these for free**,
which is a strong reason to keep the mux rather than hand-roll shm-native framing. If a
later ring-buffer increment retires the mux for shm, these obligations must be
re-implemented first — same gate as QUIC.

---

## 3. Application (b) — bulk-binary payloads by reference (the strong first increment)

### 3.1 The idea, precisely

For a large opaque payload — a point cloud, a laser scan grid, an E57 blob (the
`gls-*`/survey memories, `real-world-data-system`) — DER today would carry it inline as one
enormous `OCTET STRING`. Encoding it means copying megabytes through the codec and the
transport; decoding means allocating a megabytes-large byte array on the receiver heap.
Instead:

- **DER carries the canonical structured *metadata* + a *reference/handle*** to the blob
  (dimensions, point count, coordinate frame, sensor id, a content digest, and the handle
  that locates the bytes) as a normal `@AtomicSerial` value object — fully canonical,
  fully validated, signature-covered.
- **The heavy binary lives in a mapped `MemorySegment`** (a shm segment on the same host,
  or a memory-mapped file the receiver `mmap`s) that the receiver reads **in place** —
  zero-copy for the megabytes — while DER handles only the small envelope.

This is the natural extension of the **§3.8 opaque-octet carve-out**: STD-006 already says
externally-produced octets (certs, `X500Principal`, signatures) are carried *verbatim,
never re-encoded, and the codec's only obligations are the outer TLV framing and the schema
`SIZE` bound*. A bulk blob is the same shape — an opaque byte region the codec must not
parse — except its *bytes* are moved out-of-band into a mapped region and the DER envelope
carries a *reference* to them instead of the bytes themselves.

### 3.2 Wire representation of the reference

The DER envelope carries a `BlobReferenceRecord` (`@AtomicSerial`, proposed §7.x) whose
fields are all canonical, bounded, validated DER:

```asn1
BlobReferenceRecord ::= SEQUENCE {
    contentDigest   OCTET STRING (SIZE(32)),        -- SHA-256 of the blob bytes (content address, integrity)
    length          INTEGER (0..maxBlobLen),        -- exact byte length; bounded-before-map (principle 5)
    codec           BlobCodecId,                    -- how to interpret the bytes (E57 / raw XYZ / …), enumerated
    locator         BlobLocator                     -- CHOICE: how to reach the bytes (below)
}

BlobLocator ::= CHOICE {
    shmSegment  [0] IMPLICIT ShmSegmentRef,   -- same-host: named segment + offset + length
    mappedFile  [1] IMPLICIT MappedFileRef,   -- same-host: file path/handle the receiver mmaps
    inlineFallback [2] IMPLICIT OCTET STRING (SIZE(0..maxInlineBlob))  -- small/off-host: carry inline
}
```

Key properties, all consistent with STD-006:

- **The reference is canonical DER**; the *bytes it points at are not part of the canonical
  form*. Two `.equals` blob-bearing objects must produce byte-identical DER envelopes,
  which they do because the envelope carries the **content digest**, not the raw bytes —
  the digest is the value-equality proxy and the signature-covered field, exactly as §7.8
  content-address digests already work. (This is what keeps signatures/Entry-matching
  intact: the signed/matched thing is the digest in the canonical envelope, never the
  transient mapped region.)
- **`length` is bounded (`maxBlobLen`) and checked before the receiver maps/reads** — the
  principle-5 "reject before allocation" rule applied to the mapping size. A blob claiming
  a length beyond the profile ceiling, or beyond the actual mapped region, is a fail-secure
  decode failure.
- **`inlineFallback` gives graceful degradation**: off-host receivers (who cannot map the
  sender's segment) or small blobs get the bytes inline as an ordinary opaque `OCTET
  STRING`, so the same object round-trips over TCP/SSL/QUIC with no shm — the shm/mmap
  locator is a same-host *optimisation*, never a hard dependency. This is essential: a
  blob-by-reference object that only works same-host would break the transport-agnostic
  property.
- **`contentDigest` is verified against the mapped bytes after mapping and before use** —
  this is both an integrity check and the anti-TOCTOU anchor (§5.3): if a malicious writer
  mutates the region after the digest was computed, the receiver's re-hash on read
  mismatches and the payload is rejected. (Cost: re-hashing the blob is O(n) over the
  megabytes, which erodes some of the zero-copy CPU win — an honest trade discussed in
  §5.3.)

### 3.3 Arena lifetime / ownership of the mapped region

This is where FFM's `Arena` model does real work and where the sharpest safety questions
live.

- **The mapped blob region is owned by an `Arena`.** FFM's `Arena` is the lifetime scope of
  a `MemorySegment`: when the arena closes, the segment is unmapped and every access to it
  thereafter throws `IllegalStateException` (a *bounds-checked, deterministic* failure —
  not a JVM crash, unlike `Unsafe`). The receiver-side design must ensure the arena
  outlives every read of the blob.
- **Ownership question — who frees, and when.** The sender produced the bytes; the receiver
  reads them. Options:
  - **Sender-owned, receiver-leased (RECOMMENDED).** The sender maps the region, the
    receiver maps a *view* of it, and the region's lifetime is governed by a **JERI
    lease / DGC-style liveness** the receiver holds — the receiver renews while it is still
    reading; the sender must not unmap until the lease is released or expires. This reuses
    the JGDMS lease machinery (the same liveness discipline that governs proxies and
    codebases) and closes the use-after-free window: the sender's "free" is gated on the
    lease, not on a guess about when the receiver is done. This is the correct answer for
    the survey vision, where a scan service hands a large cloud to a consumer and must know
    when the region can be reclaimed.
  - **Copy-on-map (fallback).** If lease coordination is undesirable for a given call, the
    receiver copies the region into its own arena immediately on receipt and releases the
    shared view — trading the zero-copy win back for lifetime independence. This is the
    safe default when the two processes are at different trust and the reader does not want
    to depend on the writer's cooperation at all (§5.2).
  - **Confined vs. shared arena.** A `MemorySegment` from a *confined* arena is accessible
    only from the owning thread; a *shared* arena allows multi-thread access at the cost of
    a more expensive close (it must ensure no thread is mid-access). Since JGDMS is
    virtual-thread-based and a blob may be read by a different vthread than mapped it, the
    receiver's view generally needs a **shared arena** — and shared-arena close is exactly
    the operation that must be coordinated with the lease so it cannot race an in-flight
    reader (§5.2).

### 3.4 Composing a MemorySegment-backed blob with the `@AtomicSerial` value model

The `@AtomicSerial` object that "has a blob field" holds the **`BlobReferenceRecord`
(the metadata + handle), not the `MemorySegment` itself**, as its serial field. The
`MemorySegment` is a *transient, receiver-side resource* obtained by resolving the
reference — it is never part of `serialForm()`, never canonical, never signed. Concretely:

- `serialForm()` declares the `BlobReferenceRecord` field (canonical, bounded, validated).
- The `(GetArg)` constructor runs `check(GetArg)` on the *reference* (digest present,
  length within bound, codec recognised, locator well-formed) **before** any mapping is
  attempted — the validation gate runs on the envelope, not the bytes.
- The object exposes the blob through an accessor like `MemorySegment openBlob(Arena)` /
  `resolve()` that (i) maps/views the region, (ii) checks the mapped length equals the
  declared `length`, (iii) re-hashes and verifies `contentDigest`, (iv) hands back a
  read-only `MemorySegment` view (`asReadOnly()`) scoped to the caller's arena. Mapping is
  **lazy and explicit** — decoding the object does not map gigabytes; the receiver maps
  only when it actually reads the blob, and only if it is same-host and chose the mapped
  locator.
- The blob view is handed back **read-only** (`MemorySegment.asReadOnly()`), so the reader
  cannot scribble into a shared region and the value-object stays immutable in spirit.

This keeps the value model intact: the object is a normal validated `@AtomicSerial` value
whose canonical form is small and signature-friendly; the megabytes are an out-of-band,
lazily-resolved, read-only, lifetime-scoped resource. The zero-copy is confined to the
opaque bulk bytes; everything DER guarantees still holds on the envelope.

### 3.5 Fit with the survey / real-world-data vision

This is the application that directly serves `real-world-data-system` and the GLS/survey
memories: a scan/point-cloud service on a node produces a large E57/XYZ blob; a co-located
consumer (an adjustment engine, an exporter, an AI inference service) reads it in place
with no multi-hundred-MB copy, while the *provenance and structure* — sensor id, frame,
calibration reference, content digest, signature — travel as canonical, validated,
signature-covered DER. **Provenance-as-authority (the vision's core) lives in the DER
envelope; the bulk pixels/points live zero-copy in the segment.** The content digest in the
envelope is what ties the two together and makes the bulk bytes as trustworthy as the
signed metadata that vouches for them.

---

## 4. FFM as a security asset (why this is `Unsafe`-free)

A genuine positive worth stating plainly for the board: **FFM `MemorySegment` access is
spatially and temporally bounds-checked.** Every read/write is checked against the
segment's size (spatial) and against its arena's liveness (temporal); an out-of-bounds
access throws `IndexOutOfBoundsException` and an access after the owning arena closed throws
`IllegalStateException`. This is a categorical improvement over `sun.misc.Unsafe` (which
JGDMS is otherwise trying to leave behind) and over raw `ByteBuffer` off-heap tricks: a
malformed length or a stale handle produces a *deterministic Java exception at the access
site*, not a silent out-of-bounds read of adjacent memory or a JVM crash. For a security
project this is exactly the right primitive — the failure mode of touching attacker-shaped
shared memory is a catchable exception, which the fail-secure decode discipline can convert
into "construct no object, refuse." **This is the single strongest technical argument for
using FFM rather than any pre-Panama off-heap mechanism.**

It does **not**, however, close the higher-level threats — bounds-checking a read does not
tell you the *bytes* are honest, and arena liveness within one JVM does not govern a
*different* process's freeing of a shared region. Those are §5.

---

## 5. Security / threat model (first-order — this is JGDMS)

The governing principle, shared with the compression investigation: **shared memory written
by an untrusted peer is attacker-controlled memory.** Being memory rather than a socket
buys *nothing* on the trust axis. Every STD-006 decode bound and the gated-deserialization
gate apply unchanged to bytes that arrive via a segment. FFM's bounds-checking is a real
mitigation for *memory-safety* threats but not for *content* or *lifetime* or *TOCTOU*
threats. Three residual threat classes, each with mitigations.

### 5.1 Fail-secure size-bound-before-allocation still mandatory

A malicious writer controls the length prefixes and the `BlobReferenceRecord.length`. The
receiver MUST:

- validate every transport frame length against the slot/ring bound **before** trusting it
  (§2.3), and every `length` against `maxBlobLen` and against the *actual mapped region
  size* **before** reading (§3.2) — the principle-5 "reject before allocation/mapping" rule
  at both the transport frame and the blob reference;
- run the full DER decode behind `DeSerializationPermission("ATOMIC")` with all §4.5
  ceilings (`maxCollection`, `maxFields`, `maxDomains`, `maxCerts`, …) — a segment is not a
  bypass of the decode gate;
- treat a length that exceeds the mapped region, or a `codec`/enumerant it does not
  recognise, as a fail-secure decode failure (construct no object, refuse). FFM helps here:
  reading past the mapped size throws `IndexOutOfBoundsException` deterministically, so even
  a bound-check bug degrades to a catchable exception rather than an OOB read — but the
  explicit bound check must still be written; do not *rely* on the FFM trap as the primary
  gate.

### 5.2 Arena lifetime / use-after-free (writer frees while reader holds)

The sharp same-host threat: a malicious or buggy **writer unmaps / closes the segment (or
its arena) while the reader is mid-read.**

- **Within one JVM**, FFM already makes this safe-by-construction: closing a *shared* arena
  blocks/fails until no thread is accessing its segments, and any access after close throws
  `IllegalStateException` — a use-after-free becomes a deterministic exception, never a
  dangling pointer. Use a **shared arena** for a blob view read by virtual threads (§3.3);
  never a confined arena for cross-thread reads.
- **Across processes**, FFM's arena governs only *this* JVM's mapping — it cannot stop a
  *different* process from `munmap`/`ftruncate`/deleting the underlying shm object. Two
  defences:
  1. **Lease-gated freeing (RECOMMENDED).** The region's backing object is not reclaimed by
     the sender until the receiver's JERI lease over it is released or expires (§3.3). The
     writer's "free" is cooperative and gated, closing the window for the *cooperating*
     case. This handles the honest-but-racy sender.
  2. **Copy-on-map against a *hostile* sender.** A lease assumes the sender cooperates. A
     genuinely hostile co-located writer can `ftruncate` the shm object to shrink it under
     the reader regardless of any lease. The robust defence when the peer is not trusted is
     **copy-on-map**: the receiver copies the region into its own arena in one bounded pass
     and thereafter reads only its private copy — the hostile writer can no longer affect
     it. This trades the zero-copy win back for safety, and is the correct default whenever
     the writer is at lower/unknown trust. **The zero-copy benefit is therefore only fully
     available between *mutually trusting* co-located processes; across a trust boundary,
     copy-on-map (or a read-only, seal-backed mapping — see 5.3) is required and the win
     shrinks to "one copy instead of socket+kernel copies."**

### 5.3 TOCTOU / concurrent mutation (writer mutates mid-decode)

The classic shared-memory attack: the reader validates a value, and the writer mutates it
*between* the check and the use (or mutates the DER bytes mid-decode so the decoder sees an
inconsistent structure).

- **Do not decode in place from a mutable shared region.** The safe pattern is
  **snapshot-then-decode**: copy the DER *envelope* bytes out of the shared frame into a
  private buffer in one pass, then decode from the private copy. The envelope is small
  (that is the whole point of (b) — the metadata is small, only the blob is large), so
  snapshotting it is cheap and removes the TOCTOU surface for the *structured* decode
  entirely. **Zero-copy applies to the bulk blob, not to the DER envelope** — decode the
  envelope from a private snapshot; only the opaque bulk bytes are read in place.
- **For the bulk blob, anchor integrity in the content digest.** The receiver re-hashes the
  mapped blob and compares to `contentDigest` (§3.2). If a writer mutates the region after
  the digest was set, the re-hash mismatches → reject. This converts "silent mid-flight
  mutation" into "detected integrity failure." **Caveat — hash is not atomic with use:** a
  writer could pass the hash check and *then* mutate before the consumer reads a given
  region (a TOCTOU on the hash itself). Two robust closures: **(i) copy-on-map** (5.2) so
  the consumer reads a private copy that no writer can touch — hash the copy, use the copy;
  or **(ii) a read-only / sealed mapping** where the OS makes the region immutable for the
  writer after handoff (POSIX `memfd_create`+`F_SEAL_WRITE`+`F_SEAL_SHRINK` seals on Linux;
  a read-only section on Windows) so the writer *cannot* mutate or shrink it after sealing,
  making the hash-then-use gap safe. **Recommendation: prefer a sealed read-only mapping
  where the platform supports it (Linux memfd seals), else copy-on-map across a trust
  boundary; treat plain "hash and read in place from a writable shared region" as unsafe
  against a hostile writer.**
- **Never let a shared-memory value skip `check(GetArg)`.** The whole `@AtomicSerial`
  invariant-before-assignment discipline runs on the snapshotted envelope. There is no
  "trusted because it's local memory" shortcut.

### 5.4 Threat-model summary table

| Threat | FFM/JGDMS mitigation | Residual / requirement |
|---|---|---|
| OOB read from bad length | `MemorySegment` spatial bounds-check → `IndexOutOfBoundsException` | Still write the explicit principle-5 bound check; don't rely on the trap alone |
| Oversized alloc / decode DoS | STD-006 §4.5 ceilings + bound-before-map (`length` vs `maxBlobLen` vs mapped size) | Enforced by decoder + transport frame; mandatory on the shm path too |
| Ungated deserialization | `DeSerializationPermission("ATOMIC")` gate unchanged | Segment is not a gate bypass |
| Use-after-free (writer frees mid-read) | Shared `Arena` → `IllegalStateException` in-JVM; lease-gated freeing cross-process | Across a trust boundary use **copy-on-map**; lease only handles cooperating senders |
| TOCTOU on the DER envelope | **Snapshot-then-decode** the small envelope from a private copy | Never decode structured data in place from a writable shared region |
| TOCTOU / mutation of the bulk blob | Content-digest verify; **sealed read-only mapping** (memfd seals) or **copy-on-map** | Plain hash-then-read-in-place from a *writable* shared region is unsafe vs. a hostile writer |
| Unauthorised access to the segment | OS object perms (`0600` / restrictive DACL) = "who can map", analogous to UDS `0700` | Layer-2 SPIFFE identity on the companion UDS control socket; decode gate still runs |
| Confused deputy (co-located ≠ co-trusted) | Layers 2+3 retained even though layer 1 restricts mapping | Same lesson as the compression investigation: same-host ≠ same-trust |

---

## 6. Recommendation

**Pursue the TRANSPORT/IO axis; never the FORMAT axis.** DER stays not-zero-copy on the
format axis by design — do not chase Cap'n Proto/FlatBuffers read-in-place; it would delete
canonicity, `check(GetArg)`, receiver-chooses-implementation, and the deserialization gate.

**Sequencing.**

1. **Application (b) — bulk-binary-by-reference — FIRST.** It is the higher-value,
   lower-risk increment: it extends the existing §3.8 opaque-octet carve-out rather than
   inventing a new transport; it degrades gracefully to inline `OCTET STRING` over any
   transport (`inlineFallback`), so it is not hostage to shm; it directly serves the
   survey / real-world-data vision (large clouds/scans/E57 by reference with signed
   provenance in the envelope); and its zero-copy win (megabytes, not microseconds) is
   where the payoff is real. Constraints: content-digest-anchored integrity, bounded
   `length`-before-map, lazy read-only resolution, lease-or-copy-on-map lifetime, and the
   §5 TOCTOU discipline (sealed read-only mapping preferred, copy-on-map across trust
   boundaries). **This can be prototyped as a `BlobReferenceRecord` + resolver against a
   memory-mapped file, entirely on the JGDMS side, with no DirtyChai dependency and no new
   endpoint.**
2. **Application (a) — `ShmEndpoint` — SECOND, and only as (b)'s co-located data-plane.**
   Build it *after* the P1 `SSLEngine` keystone (per the QUIC decision record — the
   transport work has a settled ordering and shm should not jump the queue), reusing the
   UDS same-host trust model (companion UDS control socket for identity + doorbell; segment
   for bytes), the `getChannel()==null` stream-backed `Connection` (no SPI change), and the
   mux (inheriting the DGC-ack / delivery-status / half-close obligations of §2.7 for
   free). Do **not** build it as a general small-message speedup — the win there is
   marginal against DER encode/decode cost; build it when there is a large/high-frequency
   same-host data-plane that (b) feeds.

**Constraints that gate either increment.**

- FFM `MemorySegment`/`Arena` only (never `Unsafe`); read-only views handed to consumers;
  shared arena for cross-vthread reads; no `ThreadLocal` (virtual-thread rule).
- The DER envelope is always decoded from a **private snapshot**, never in place from a
  writable shared region; only the opaque bulk blob is read in place, and only under a
  sealed read-only mapping or copy-on-map across a trust boundary.
- **Same-host ≠ same-trust** is normative: layer-2 SPIFFE identity + the ATOMIC decode gate
  + all STD-006 bounds apply to segment-sourced bytes exactly as to socket-sourced bytes.
- A normative STD-\* addendum (a §7.x `BlobReferenceRecord` for (b); a shm-transport spec
  for (a), sibling to the chartered STD-010 QUIC spec) precedes any ship, so a non-JVM peer
  and a security reviewer can work from the document alone.

**Bottom line.** Peter's instinct is right that there is a real, security-compatible
zero-copy win in JGDMS — but only on the transport/IO axis, and mostly for *bulk binary*,
not for shaving microseconds off control messages. The strongest first move is
bulk-binary-by-reference (application b): it slots cleanly into the §3.8 opaque-octet model,
serves the survey vision directly, and keeps every DER guarantee on the signed envelope
while the megabytes go zero-copy. FFM's bounds-checked `MemorySegment` is the right
primitive and a genuine security upgrade over `Unsafe`, but it closes only the memory-safety
threats; the use-after-free, TOCTOU, and content-honesty threats are closed by content
digests, sealed/read-only mappings or copy-on-map across trust boundaries, snapshot-then-
decode for the envelope, and the unchanged gated-deserialization discipline. Build (b)
first, (a) second and only after the SSLEngine keystone, both behind a normative spec.

---

## What I could NOT verify / flagged

- **No FFM code exists in the tree** — this is greenfield; a keyword sweep found no
  `MemorySegment`/`Arena`/`FileChannel.map` transport usage (the grep hits were incidental
  substrings). Designs here are on-paper against the SPI, not against a prototype.
- **No measurement.** The zero-copy win for bulk blobs (avoiding a multi-hundred-MB copy)
  and the marginal win for small control messages are asserted from first principles, not
  benchmarked; a follow-up should measure (i) shm vs UDS vs loopback for representative
  message sizes and (ii) the content-digest re-hash cost against the copy it avoids (the
  re-hash is O(n) and partially erodes the zero-copy CPU win — the net win is dominated by
  avoiding the *kernel/socket* copies and the receiver-heap allocation, not by avoiding the
  hash).
- **`memfd_create` + file seals (`F_SEAL_WRITE`/`F_SEAL_SHRINK`)** as the anti-TOCTOU
  sealed-mapping primitive is cited from the Linux ABI; its exact reachability via FFM
  downcalls (vs. a DirtyChai java.base helper) and the Windows read-only-section equivalent
  were not implemented or tested here.
- **`SO_PEERCRED` / kernel-attested peer identity** on the companion UDS control socket is
  the deferred UDS increment (`SOW-UDS §5`); whether shm needs it *before* UDS does (a
  hostile co-located writer is a sharper threat for shared memory than for a copied socket)
  is a board question, not settled here.
- **Same-host detection + endpoint selection** for dual-export is a shared open question
  with UDS (`SOW-UDS §7`) and is not resolved by this investigation.
