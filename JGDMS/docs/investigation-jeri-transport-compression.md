# Investigation — Transport-layer DEFLATE for STD-006 DER wire-compactness

**Status:** investigation (not a proposal to implement). Evidence-based; measurements
reproducible from the harness described in Appendix A.
**Question (Peter):** STD-006 DER is deliberately verbose (explicit tags + lengths +
embedded self-describing schema) — that verbosity is what buys canonicity,
self-description, and cross-language decodability. Rather than compress the DER
*encoding* (which would break canonicity/determinism), compress the *transport bytes* in
JERI so the wire is compact while the DER stays canonical. Does transport-layer DEFLATE
(or zstd) recover wire-compactness, make DER comparable on the comparison-table
**Compact** axis, and — the deciding question — **is it SAFE on an untrusted-network
transport, given CRIME/BREACH?**

**One-line verdict.** Transport DEFLATE materially recovers wire size *and already exists
in the codebase* (`AtomicInvocationHandler`/`AtomicInvocationDispatcher`,
`net.jini.jeri.Compression`), placed correctly **below** the value/DER layer and — a key
finding — **above** the raw stream in a way that already keeps the §7.2 ACC block and
auth material *out* of the compression context. Determinism is preserved by construction.
**But** compression-before-encryption is a real length side-channel on untrusted TLS
transports, and the current wiring compresses the whole method-argument stream as one
context, which can still mix attacker-influenced and secret data within a single message.
**Recommendation: keep it opt-in and OFF by default on untrusted paths; if enabled,
compress only where no attacker+secret mixing is possible; never compress auth material
(already the case); prefer length-padding; and treat per-message-context as necessary but
NOT sufficient.** The comparison-table `~` on Compact should stay `~`, with an honest
footnote that transport DEFLATE recovers wire-compactness at a CPU cost and under a
side-channel constraint. Full detail below.

---

## 0. Prior art already in the tree (important context)

This is not a greenfield idea. The mechanism is **already implemented** and merged to
trunk:

- `net.jini.jeri.Compression` — enum `{ DEFLATE, DEFLATE_BEST_COMPRESSION,
  DEFLATE_BEST_SPEED, NONE }` (`jgdms-jeri/.../net/jini/jeri/Compression.java`).
- `net.jini.jeri.AtomicInvocationHandler` — client side. `createMarshalOutputStream`
  wraps `request.getRequestOutputStream()` in a `DeflaterOutputStream` per the configured
  `Compression`; `createMarshalInputStream` wraps the response stream in an
  `InflaterInputStream`. Default is `Compression.NONE`. The setting is a serialized field
  of the handler (`serialForm` carries `"compression"`), so a proxy can be *exported* with
  compression on.
- `net.jini.jeri.AtomicInvocationDispatcher` — server side. Symmetric:
  `InflaterInputStream` on the inbound request stream, `DeflaterOutputStream` on the
  outbound response stream.

So this investigation is really: *(a)* quantify what that existing facility buys, *(b)*
confirm it does not break determinism, and *(c)* decide whether it is safe to turn on,
and under what constraints — because today it exists but is off by default and, as far as
the docs record, its security envelope has not been written down.

**Architecture finding (the good news up front).** In `BasicInvocationHandler.marshal`
(the parent of `AtomicInvocationHandler`) the **ACC block, user Subjects, and the
protocol/version bytes are written to the *raw* `request.getRequestOutputStream()`
(`ros`) BEFORE `createMarshalOutputStream` wraps that same stream in the deflater**
(source: `BasicInvocationHandler.java` lines ~996–1027). Only `marshalMethod` +
`marshalArguments` go through the compressed stream. Net effect: **the current
implementation already keeps §7.2 auth material out of the compression context.** That is
exactly the highest-priority CRIME mitigation, already in place — though, as §4 shows, it
does not by itself make the argument stream safe.

---

## 1. Empirical compressibility (measured)

Method: byte-accurate DER TLV structures were built to mirror STD-006 framing (SEQUENCE
`0x30` / SET OF `0x31`, definite lengths, UTF8String `0x0C`, INTEGER `0x02` minimal
two's-complement, OCTET STRING `0x04`, BOOLEAN, plus an embedded Merkle schema chain with
per-class `AtomicSerialSchemaRecord`s carrying UTF8 class names, field-name/type-token
`FieldDef`s, and a 32-byte SHA-256 digest — as §7.8 embeds). For each logical payload a
**Protobuf-style varint** encoder produced the *same logical data* as a compact-tier
baseline. DEFLATE is `java.util.zip.Deflater` (the exact library the shipped
`DeflaterOutputStream` uses) at BEST_SPEED / DEFAULT / BEST_COMPRESSION. CPU is measured
over 20 000 iterations with a fresh per-message `Deflater`/`Inflater` (matching the
per-request wiring). Full harness in Appendix A; reproducible on the DirtyChai JDK.

| Payload | raw DER (B) | protobuf (B) | pb/raw | DEFLATE best (B) | best/raw | **best/pb** |
|---|---:|---:|---:|---:|---:|---:|
| Entry (6 fields, ServiceInfo-like) | 473 | 156 | 0.33 | 414 | 0.88 | **2.65** |
| Exported proxy | 634 | 292 | 0.46 | 485 | 0.76 | **1.66** |
| MarshalledInstance | 694 | 347 | 0.50 | 548 | 0.79 | **1.58** |
| ServiceTemplate | 553 | 229 | 0.41 | 443 | 0.80 | **1.93** |
| ServiceItem (3 entries) | 2139 | 845 | 0.40 | 926 | 0.43 | **1.10** |
| Reggie registration (item + lease + ids) | 2649 | 1039 | 0.37 | 970 | 0.37 | **0.93** |
| Lookup batch (50 ServiceItems) | 83305 | 34450 | 0.41 | 1466 | **0.02** | **0.04** |

(BEST_SPEED vs BEST_COMPRESSION differed by <2% on the single-object rows and only
mattered on the batch: 2454 B vs 1466 B — i.e. best-compression is worth using only when
there is a lot of redundancy to find.)

**CPU cost (per message, fresh context, microseconds):**

| Payload | compress µs | inflate µs |
|---|---:|---:|
| Entry | 28.0 | 6.3 |
| Exported proxy | 32.4 | 8.0 |
| MarshalledInstance | 37.3 | 9.7 |
| ServiceTemplate | 23.5 | 3.4 |
| ServiceItem (3 entries) | 52.7 | 11.7 |
| Reggie registration | 65.4 | 13.8 |
| Lookup batch (50 items) | 512.9 | 138.2 |

Throughput on the 83 KB batch: **~162 MB/s compress, ~603 MB/s inflate** (single core,
DirtyChai JDK 27). Compression is ~4× more expensive than decompression, as expected.

### Reading the numbers

- **DER's verbosity is overwhelmingly *repeated self-description*.** The tag/length
  framing, and above all the embedded schema chain (class names like
  `net.jini.lookup.entry.ServiceInfo`, field names, type tokens, repeated across every
  instance and every hierarchy level), is exactly the kind of redundancy DEFLATE eats.
  Protobuf avoids that redundancy *structurally* (field numbers, no names, no per-message
  schema) — which is why raw DER is 2–3× protobuf, but DEFLATE closes most of the gap by
  removing the redundancy DEFLATE, not protobuf's schema-numbering, is good at.
- **Single small objects: DEFLATE helps but does NOT reach the compact tier.** An
  isolated Entry or proxy compresses only to 0.76–0.88 of raw and remains **1.6–2.6×
  larger** than protobuf. DEFLATE's 32 KB window and ~11-byte zlib overhead give it little
  to work with on a few hundred bytes, and there is real entropy (UUIDs, SHA-256 digests,
  certificates) it cannot touch. **On tiny single messages, transport DEFLATE does not
  make DER "compact".**
- **Medium messages: DEFLATE matches or beats protobuf.** By the ServiceItem/Reggie-
  registration size (~2–3 KB) the repeated schema chains give DEFLATE enough to work with:
  best/pb reaches 1.10 and 0.93 — i.e. **compressed DER is already the same size as, or
  smaller than, uncompressed protobuf** for the same data. This is the regime most real
  JGDMS control-plane messages live in.
- **Batches: DEFLATE crushes DER far below protobuf.** A 50-item lookup response
  (83 KB raw, the shape of a match-all `lookup()` reply) compresses to **2% of raw** and
  **4% of the protobuf size**, because the same class-name/schema strings repeat 50×.
  Here compressed DER is *dramatically* more compact than even a compact binary format,
  because the compact format still re-emits the same field structure per message while
  DEFLATE deduplicates it across the whole stream.

**Net answer to item 1:** transport DEFLATE recovers wire-compactness *for the payloads
that dominate JGDMS traffic* (multi-entry service items, registrations, lookup batches),
where it matches or beats the compact tier. It does **not** recover compactness for
isolated small messages, where DER stays 1.6–2.6× a varint format even after compression.
So "approaches the compact tier" is true for medium/large/repetitive payloads and false
for tiny ones — an honest, size-dependent answer.

### zstd

zstd would improve the small-message case (smaller headers, better entropy coding, and
crucially a *trained dictionary* could carry the common JGDMS class-name/schema strings so
even a single small message compresses well) and roughly match DEFLATE on the large
redundant case at much higher speed. **But zstd is not measurable here and not free to
adopt:** there is no `zstd` binary, no Python, and — decisively — **no zstd on the JDK
classpath.** `java.util.zip` ships DEFLATE only; zstd needs a native library (zstd-jni) or
the incubating foreign-function bindings, i.e. a new native dependency on the security-
critical transport path. Given JGDMS's "less is more / minimum you can do confidently"
posture and the DirtyChai java.base trust model, **adding a native compressor to the
untrusted-network transport is a materially larger trust and supply-chain commitment than
turning on the DEFLATE that is already in `java.util.zip` and already wired.** The one
place zstd would clearly win — a shared dictionary of JGDMS schema strings — is *also* the
place it becomes a **cross-message shared compression context**, which (see §4) is the
CRIME-dangerous configuration. **Recommendation: do not pursue zstd for the transport
unless/until the side-channel envelope (§4) is settled; if pursued, a per-connection or
per-message dictionary must not span trust boundaries.** *(Flagged: zstd ratios are
unmeasured — no zstd available in this environment. The claims here are from published
DEFLATE-vs-zstd behaviour on small structured payloads, not local measurement.)*

---

## 2. Architecture — where the codec sits (and where it already sits)

JERI layering, top to bottom:

```
  application value  (@AtomicSerial objects)
  ───────────────────────────────────────────
  DER value/codec    (ObjectCodec / MarshalledInstanceCodec)   ← canonical bytes live here
  ───────────────────────────────────────────
  MarshalOutputStream / AtomicMarshalOutputStream (invocation)
  ─────────────  ← COMPRESSION SITS HERE (a stream wrapper)  ───
  OutboundRequest.getRequestOutputStream()  (invocation/mux)
  ───────────────────────────────────────────
  Connection (net.jini.jeri.connection.Connection: get{Input,Output}Stream / SocketChannel)
  ───────────────────────────────────────────
  Endpoint transport: TCP / SSL(TLS) / (future) QUIC
```

The existing wiring places compression as a **stream wrapper between the marshal stream
and the request stream** — i.e. it compresses the serialized value bytes *after* the DER
codec has produced canonical octets and *before* they enter the mux/transport. That is
precisely "below the DER/value layer, above the wire." Concretely
(`AtomicInvocationHandler.createMarshalOutputStream`):

```java
OutputStream out;
switch (compression) {
    case NONE:  out = request.getRequestOutputStream(); break;
    case DEFLATE: out = new DeflaterOutputStream(request.getRequestOutputStream()); break;
    ... BEST_COMPRESSION / BEST_SPEED ...
}
return new AtomicMarshalOutputStream(out, ...);   // DER/value bytes flow into `out`
```

Two candidate homes, and why the existing one is right:

1. **Invocation-layer stream wrapper (where it is today).** Pros: it is *per-request*
   (fresh `Deflater` per call → separate compression context per message by construction —
   the first CRIME mitigation, free); it sees only the argument/value bytes, not the
   framing or the ACC block; it is trivially opt-in per exported proxy via the
   `Compression` field; and it is transport-agnostic (works over TCP, SSL, and a future
   QUIC endpoint without touching them). Cons: it compresses the *entire* method+args
   stream as one context (§4 hazard within a message), and it cannot deduplicate across
   requests (which, per §4, is actually a security *feature*, not a limitation).
2. **`Connection`-level codec (below the mux).** A `Connection` decorator wrapping
   `getInputStream()/getOutputStream()` would compress *everything on the connection as one
   long-lived stream*. This is **the CRIME-worst option**: it creates a persistent
   cross-message, cross-call, potentially cross-*user* compression context — exactly the
   TLS-compression configuration that CRIME broke and TLS 1.3 removed. **Do not put
   compression at the Connection level on an untrusted transport.**

There is **no other existing JERI compression** (grep for `Deflater|Inflater|GZIP` across
`jgdms-jeri` returns only these three invocation-layer classes and one unrelated
`AnalysisRequest` field). QUIC brings no built-in payload compression (QPACK is
HTTP/3-header-specific and not in play here).

**Integration sketch (transport-only, determinism-safe):** keep it exactly where it is —
a `DeflaterOutputStream`/`InflaterInputStream` wrapper around the *request/response*
stream, selected per exported proxy, applied only to the method+args region, never spanning
the mux framing or the ACC block, one fresh `Deflater` per request. The receiver inflates
back to canonical DER *before* any DER-level operation runs. No change to the DER codec, the
schema digest, or the value layer is required or desirable.

---

## 3. Determinism preservation (must hold — and does)

The requirement: compression must be a **pure transport transform**, invisible to the
canonical DER. Signatures, §7.7.2 Entry byte-matching, schema digests, and the
value-equality property all operate on the **uncompressed canonical DER**. DEFLATE is
non-deterministic (the same input yields different compressed bytes across levels,
zlib versions, flush points, and window state), so the compressed form must **never** be
what any canonical/signature/match operation runs on.

**The layering guarantees this, and the existing implementation respects it:**

- **Canonical bytes are produced strictly above the compressor and consumed strictly
  after decompression.** The DER codec (`ObjectCodec`/`MarshalledInstanceCodec`) emits
  canonical octets into `AtomicMarshalOutputStream`; only *then* do they enter the
  `DeflaterOutputStream`. On receipt, `InflaterInputStream` restores the *exact same*
  canonical octets *before* `AtomicMarshalInputStream`/the DER decoder sees a byte.
  DEFLATE is lossless, so `inflate(deflate(x)) == x` byte-for-byte; the decoder, the schema
  digest, `check(GetArg)`, and Entry byte-matching all see the identical canonical DER they
  would have seen with `Compression.NONE`.
- **The compressed bytes are never addressed.** Schema digests (§7.8 Merkle chain) are
  computed over the DER *schema record*, which is produced by the codec and lives above the
  compressor — the digest input is the canonical DER, never the deflated stream. Signatures
  and content-address digests sign the canonical DER for the same reason. §7.7.2 Entry
  byte-matching compares the canonical DER field encodings, produced above the compressor.
  **Nothing canonical ever ingests a deflated byte.**
- **Value-equality is untouched.** Two `.equals` objects still encode to byte-identical
  canonical DER (the octet-sort collection discipline, commit `c0e20eb30`). They may then
  deflate to *different* transport bytes (if compressed at different levels or with
  different upstream stream state) — and that is fine, because value-equality is asserted
  on the canonical DER, which both sides reconstruct identically. Compression sits *outside*
  the equality-bearing layer.

**Paths where compression could leak into a canonical operation (and why they don't
today):**

- *If* someone digested/signed the on-wire (compressed) bytes instead of the canonical
  DER → determinism breaks. The design does not; the digest/signature inputs are codec
  outputs, structurally above the compressor. **Guard to preserve:** never expose the
  compressed stream to the digest/signature/match APIs; keep those APIs fed only by the
  codec.
- *If* compression were moved to the `Connection` level, the codec would still see
  canonical DER (inflate happens before decode), so **determinism would still hold** — but
  §4's side-channel would worsen. Determinism is robust to placement; security is not.
- *If* a shared dictionary (zstd) were introduced, canonical DER is still recovered on
  decompress, so determinism holds — but the dictionary becomes a cross-message context
  (§4). Again: determinism safe, security not.

**Conclusion:** the transport-only layering keeps determinism and value-equality **fully
intact**. There is no path in the current design where compression touches a canonical
operation. The only discipline required is the obvious one: canonical/signature/match
operations must always run on the codec's canonical DER, never on transport bytes — which
is already how the system is built.

---

## 4. Security — CRIME/BREACH (the crux)

Compression-before-encryption is the classic compression-ratio side channel. CRIME (2012)
and BREACH (2013) recover secrets by observing that when attacker-influenced data and a
secret share **one compression context**, a *correct guess* of the secret compresses
*shorter* (the guess matches existing bytes and is back-referenced), and the ciphertext
length leaks that. TLS 1.3 **removed** TLS-level compression for exactly this reason.
JERI runs over SSL and (future) QUIC endpoints = TLS on untrusted networks. So this is
first-order, not a footnote.

### 4.1 The attack works here — demonstrated

A concrete oracle (Appendix B, `CrimeDemo.java`) builds a per-request message shaped like
a JERI request: a secret authorization token plus an attacker-controlled field
(e.g. an Entry string the attacker gets the service to echo or store), compressed
together with `java.util.zip.Deflater` at the default level — **the exact library and a
per-message fresh context matching the shipped wiring.** Result:

```
actual next secret char: 'Z'
guess minimising compressed length: 'Z'   (length 62)  -> matches: true
```

The correct next secret character is recovered purely from compressed length. **This is
not theoretical for JGDMS** — it is `java.util.zip.Deflater`, per-message, exactly as
`DeflaterOutputStream` uses it.

### 4.2 Per-message separate contexts are necessary but NOT sufficient

A common mitigation belief is "use a fresh compression context per message." The demo
above **already uses a fresh `Deflater` per guess**, and the leak persists — because the
secret and the attacker's guess **co-reside in the same message**. Separate per-message
contexts only defeat the attack when the attacker's data and the secret are in
*different* messages. **Within one message, separate-per-message buys nothing.** This is
the single most important nuance: the shipped per-request wiring gives separate-per-message
for free, but that does not make an argument stream that mixes attacker input with a secret
safe.

### 4.3 What actually closes it: never co-compress secret with attacker data

The demo's third stage puts the secret in a **separate, uncompressed stream** and
compresses only the attacker-echoed field:

```
guess minimising length now: 'A'  -> leak gone (no correlation to secret)
```

With the secret outside the compression context, compressed length no longer correlates
to the secret. **The only robust structural mitigation is to keep secrets and
attacker-influenced data in different compression contexts (or uncompressed).**

### 4.4 Where does attacker-influenceable data share a context with secrets in a JERI
call?

- **The §7.2 ACC block / user Subjects / auth material — SAFE TODAY.** As established in
  §0/§2, `BasicInvocationHandler.marshal` writes the ACC block, user Subjects, and JWTs to
  the **raw uncompressed** `ros` *before* the deflater wraps the stream. **Auth material is
  already outside the compression context.** This is the highest-value mitigation and it is
  already in place. It MUST be preserved: never move ACC/Subject/JWT writing inside the
  compressed region. (Recommend a regression test asserting the ACC block bytes are not
  deflated.)
- **The method-argument stream — the residual hazard.** Everything in
  `marshalMethod` + `marshalArguments` shares one per-request `Deflater` context. If a
  single call carries *both* a secret and an attacker-influenced value, their lengths
  correlate. Realistic shapes:
  - A method whose arguments include a caller-supplied token/capability **and** an
    attacker-chosen field (e.g. `store(capability, attackerControlledEntry)`).
  - A **response** that echoes attacker input alongside another user's data (BREACH's
    original shape — server responses reflecting a request parameter into a page that also
    holds a CSRF token). JGDMS `lookup()`/notify responses that return other services'
    Entries while reflecting an attacker-chosen template field are the analogue.
- **Cross-user data in a shared response.** A match-all `lookup()` response (the batch in
  §1, where compression is most attractive) returns *many services' data in one compressed
  context*. If any of that data is secret-to-the-attacker and any is attacker-influenced,
  the batch is the worst case for the side channel even as it is the best case for the
  ratio. **The compressibility win and the side-channel risk peak on the same payload.**

### 4.5 Mitigations, ranked

1. **Never compress auth material** (ACC block, Subjects, JWTs, capabilities/tokens).
   *Already true for the ACC block* — preserve it, extend it to any token argument, and add
   a regression test. This is the non-negotiable floor.
2. **Off by default on untrusted paths.** `Compression.NONE` is the default today — keep
   it. Compression should be an explicit, audited opt-in on an exported proxy, not ambient.
3. **Compress only where no attacker+secret mixing is possible.** Safe niches: bulk
   data-plane transfers with no secret in-band and no attacker-chosen field (e.g. a
   forensic/archival DER export, a JavaSpaces bulk read of non-sensitive entries); a
   control-plane call whose arguments are entirely server-authenticated and carry no
   attacker-influenced field. **Unsafe:** any call/response mixing a secret with
   attacker-influenced content in one context — which includes the most compressible case
   (match-all lookup responses reflecting a template).
4. **Length-hiding / padding.** If compression is enabled where some mixing is possible,
   pad the compressed output to a coarse bucket (e.g. round up to the next 256 B, or add a
   random 0–N-byte pad) so single-byte length deltas are masked. This is the TLS-record-
   padding-style defence; it costs some of the ratio back but is the standard countermeasure
   when compression on a mixed context is unavoidable. **Padding is mandatory if compression
   is ever enabled on a context that could mix secret + attacker data.**
5. **Per-message context (necessary, not sufficient).** Keep the fresh-`Deflater`-per-
   request behaviour (it is free and blocks cross-message correlation), but do **not** rely
   on it as *the* mitigation — §4.2 shows it does not stop within-message leakage.
6. **Never at the `Connection` level.** A connection-spanning compressor creates a
   long-lived cross-message/cross-user context — the CRIME configuration. Prohibit it on
   SSL/QUIC endpoints.

### 4.6 Verdict

Given JGDMS's entire premise is untrusted-network security, **transport compression is
acceptable only as a constrained, opt-in, off-by-default facility, never as a default and
never over auth material.** The load-bearing constraints are: (1) auth material out of the
context (already done — preserve + test); (2) enabled only where the argument/response
context provably cannot mix a secret with attacker-influenced data, or else length-padded;
(3) per-message context retained but not trusted as sufficient; (4) never at the Connection
level. Under those constraints it is safe *for the niches that satisfy them* — which,
notably, **excludes the most compressible payload (mixed multi-user lookup responses)
unless padded.** The side channel and the ratio peak together, so the naive "turn it on
for the big responses" is exactly the dangerous move. This is the deciding factor and it
narrows the safe use to bulk/no-secret/no-attacker-input data flows plus padded mixed
flows.

---

## 5. Comparison-table implication (re-rating the Compact cell)

The current cell is `~` with the Tradeoffs note "Not optimised for the smallest wire …
If wire size is the priority, the compact binary formats win." The evidence says: DER's
verbosity is largely *compressible redundancy* (repeated schema self-description), and
transport DEFLATE recovers most of it for medium/large/repetitive payloads (matching or
beating protobuf) while leaving tiny single messages behind — **but** recovering it safely
is gated by the compression side-channel constraint, so it is not a free "and it's compact
too" upgrade.

**Recommendation: keep the Compact cell `~`.** Do not upgrade to `✓`. Compressed-DER
compactness is real but conditional (payload-size-dependent, CPU-costed, and — decisively —
security-gated on untrusted paths). Upgrading the mark would overclaim. Instead, add a
footnote to the `~` and a Tradeoffs sentence:

> **Compact `~` (footnote).** DER's verbosity is largely *repeated self-description*
> (tag/length framing + the embedded schema chain), which is highly compressible.
> JGDMS ships an **opt-in transport-layer DEFLATE** (`net.jini.jeri.Compression`, applied
> below the value/DER layer in `AtomicInvocationHandler`/`AtomicInvocationDispatcher`, so
> the DER stays canonical — compression is a pure transport transform, invisible to
> signatures/digests/value-equality, and the receiver decompresses to canonical DER before
> any canonical operation). Measured (Appendix — `investigation-jeri-transport-compression`):
> it recovers little on isolated small messages (compressed DER stays ~1.6–2.6× a
> varint format) but **matches or beats a compact binary format on medium/repetitive
> payloads** (ServiceItem, Reggie registration) and crushes redundant batches (a 50-item
> lookup response → ~2% of raw, ~4% of protobuf). It costs CPU (~30–65 µs compress /
> 6–14 µs inflate per typical control message; ~160 MB/s compress single-core) and does
> **not** provide zero-copy. Critically, because JERI runs over TLS, transport compression
> is a **CRIME/BREACH length side channel**: it is **off by default**, keeps auth material
> (§7.2 ACC block, Subjects, tokens) out of the compression context, and is only safe where
> the argument/response context cannot mix a secret with attacker-influenced data (or is
> length-padded). So DER can *reach* the compact tier on the wire, but not unconditionally
> and not for free — hence `~`, not `✓`.

And in Tradeoffs, replace "If wire size is the priority, the compact binary formats win"
with a two-sided version:

> **Not optimised for the smallest wire — but transport DEFLATE recovers most of it,
> under a security constraint.** Raw DER is 2–3× a varint format for the same data. An
> opt-in transport-layer DEFLATE (below the DER layer, canonical-preserving) closes that
> gap for medium/large/repetitive payloads (matching/beating protobuf; a lookup batch
> compresses to ~2% of raw) at a CPU cost and with no zero-copy. Because JERI is a TLS
> transport, that compression is a CRIME/BREACH side channel, so it is off by default and
> constrained (never over auth material; only where secret+attacker-input do not share a
> context, else padded). Net: DER is verbose on the wire but not *irredeemably* so; the
> honest statement is "verbose encoding, transport DEFLATE recovers wire-compactness at a
> CPU cost, still behind the zero-copy tier, and gated by the compression side-channel
> constraint."

---

## 6. Recommendation

**Adopt/keep transport-layer DEFLATE as an opt-in, off-by-default facility — which is
essentially the status quo — and *document its security envelope*, which is currently
missing.** Specifically:

1. **Keep the existing invocation-layer placement** (`AtomicInvocationHandler`/
   `AtomicInvocationDispatcher`, per-request `DeflaterOutputStream`). It is architecturally
   correct: below the DER/value layer, above the wire, transport-agnostic, per-message
   context, determinism-safe. Do **not** move it to the `Connection` level.
2. **Keep it OFF by default** (`Compression.NONE`). Enable only per exported proxy, as an
   audited decision, on paths that meet the §4 constraints.
3. **Preserve and test the auth-material exclusion.** The ACC block / Subjects / JWTs are
   already written uncompressed; add a regression test asserting they never enter the
   deflated region, and extend the principle to any token/capability argument.
4. **Where compression is enabled on a context that could mix secret + attacker-influenced
   data, require length-padding** (coarse bucketing or random pad). Do not enable
   compression on mixed contexts without it — this specifically covers the tempting
   match-all lookup responses.
5. **Prefer BEST_SPEED** for control-plane messages (the ratio difference vs
   BEST_COMPRESSION is <2% below ~2 KB; only large redundant batches justify
   BEST_COMPRESSION, and those are the highest side-channel risk anyway).
6. **Do not adopt zstd for the transport** without first settling the side-channel
   envelope; if adopted later, no shared dictionary may span a trust boundary, and it adds
   a native dependency to the security-critical path (weigh against "less is more").
7. **Comparison table:** keep Compact `~`; add the honest footnote (§5). Do not upgrade to
   `✓`.

**Bottom line.** Peter's instinct is sound: compressing the *transport* rather than the
*encoding* keeps DER canonical while recovering wire-compactness, and the codebase already
does it correctly, below the value layer, with auth material excluded. The empirical win
is real and large for the payloads that matter. The determinism argument is airtight. The
deciding constraint is the CRIME/BREACH side channel: it is genuine, demonstrated here
against the exact shipped library, and it means compression must stay opt-in,
off-by-default, never over auth material, and only over contexts that don't mix secret with
attacker input (or are padded) — which excludes the single most compressible payload unless
padded. Adopt within those rails; do not turn it on globally.

---

## What I could not measure (flagged)

- **zstd ratios/CPU** — no zstd binary, Python, or JVM zstd library in this environment.
  zstd claims (dictionary win on small messages, speed) are from published behaviour, not
  local measurement. Any zstd decision needs its own benchmark *and* a native-dependency /
  trust review.
- **Real captured JGDMS wire bytes** — the DER payloads here are byte-accurate *structural*
  reconstructions of STD-006 framing (correct TLV shapes, realistic class names, embedded
  schema chain with SHA-256 digests), not bytes captured from a live `jgdms-der` codec run.
  Ratios depend on the schema-string/entropy mix; a follow-up that drives the real codec
  (`ObjectCodec` + a real `SchemaGenerator` chain) and captures live bytes would tighten
  the numbers, though the *shape* of the result (redundant self-description compresses well;
  UUIDs/digests/certs don't; batches win big) is robust to that.
- **End-to-end throughput impact** — measured DEFLATE CPU in isolation, not the full
  invocation path under TLS with the mux. The per-message µs figures are a lower bound on
  added latency; real impact depends on message size distribution and whether TLS record
  padding is also applied.
- **Whether any current JGDMS response path actually mixes a secret with attacker-
  influenced data in one argument context** — I established the *hazard shape* and that the
  ACC block is safely excluded, but did not audit every dispatcher/response for a concrete
  secret+attacker-input co-residence. That audit is required before enabling compression on
  any specific proxy.

---

## Appendix A — DER/compression benchmark harness

`DerCompressBench.java` (run on DirtyChai JDK 27). Builds byte-accurate STD-006-style DER
TLV for Entry / exported proxy / MarshalledInstance / ServiceTemplate / ServiceItem /
Reggie registration / 50-item lookup batch, plus a protobuf-style varint encoder for the
same logical data; measures `java.util.zip.Deflater` at BEST_SPEED/DEFAULT/BEST_COMPRESSION
and per-message compress/inflate CPU over 20 000 iterations with a fresh `Deflater`/
`Inflater` per message. (Source retained in the investigation scratch; reproduce by
compiling and running under the DirtyChai JDK.)

## Appendix B — CRIME oracle demonstration

`CrimeDemo.java`. Builds a per-request message containing a secret auth token plus an
attacker-controlled echo field compressed together (fresh `Deflater`, default level =
shipped wiring); recovers the next secret character by minimising compressed length;
shows per-message separate contexts do not help when secret+guess co-reside; shows moving
the secret to a separate uncompressed stream removes the correlation. Output reproduced
inline in §4.1/§4.2/§4.3.
