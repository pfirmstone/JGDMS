# SOW: QUIC JERI Transport (with UDS as the stepping stone)

**Status:** design / investigation, 2026-06-30. JGDMS-side, committable. Companion to
`SOW-Unix-Domain-Socket-JERI-Transport.md` (UDS) — read that first; this builds on it.

## 0. Thesis

QUIC subsumes, in one standardized protocol (RFC 9000), the three things JERI builds
in separate layers: **TLS** (`net.jini.jeri.ssl`), **multiplexing** (the hand-rolled
`org.apache.river.jeri.internal.mux`), and **connection management** over TCP. So QUIC
is less "another transport" than a chance to retire the mux and unify the stack.

**UDS is the right stepping stone** because it shares QUIC's one hard problem —
*no `java.net.Socket`, so the TLS/auth layer must be decoupled from `SSLSocket`* —
without QUIC's protocol surface (congestion control, loss recovery, packet protection).
Do the decoupling once, prove it over the simplest reliable channel (UDS), then reuse
it for QUIC.

## 1. The shared keystone — and why the expensive part is already done

Verified against current source:

- **The durable asset is `SSLContext` + `AuthManager`, not `SSLSocket`.** JGDMS's
  identity/constraint machinery lives in `AuthManager` /`ClientAuthManager`
  /`ServerAuthManager` (`extends FilterX509TrustManager implements X509KeyManager`) +
  `SubjectCredentials`. `Utilities.SSLContextInfo` bundles an `SSLContext` *initialized
  with those managers*; the socket factory is merely `sslContext.getSocketFactory()`.
  So **constraint-driven client-cert selection** (`chooseClientAlias` /
  `chooseEngineClientAlias`, already present), **peer validation**
  (`checkServerTrusted`), **cert↔Subject mapping** (`SubjectCredentials`), and the
  SPIFFE principal matching are all **JSSE-SPI callbacks invoked by the TLS engine** —
  identical whether TLS is driven by `SSLSocket`, by `SSLEngine`, or by any stack that
  accepts an `SSLContext`.
- **The `SSLSocket` coupling is confined** to `SslConnection` (`volatile SSLSocket`;
  `getInputStream`/`getOutputStream` delegate to it) and `SslServerEndpointImpl`'s
  accept plumbing. The `net.jini.jeri.connection.Connection` SPI is already
  transport-shape-agnostic: streams + an *optional* `SocketChannel getChannel()` (the
  mux's non-blocking path).

So the **keystone = migrate `SslConnection`'s record I/O from `SSLSocket` to `SSLEngine`
over a byte channel**, completing the `X509Extended{Key,Trust}Manager` overloads (the
`chooseEngineClientAlias` hook and the `// extends X509ExtendedKeyManager — do we want
this?` TODO in `Utilities` show this was already anticipated). The auth/identity/
constraint layer rides along unchanged. This is exactly UDS SOW §4 Option (i).

## 2. What UDS proves (the stepping stone)

Building UDS first exercises, in isolation, everything QUIC also needs:

1. **The `Endpoint`/`ServerEndpoint` SPI for a non-TCP, non-`Socket` transport**
   (`UnixEndpoint`/`UnixServerEndpoint` over `SocketChannel(UNIX)`).
2. **`SSLEngine` over a channel** — the record-layer migration, validated end-to-end
   with the real SPIFFE handshake + constraints, with TCP regression alongside.
3. **A channel-backed `Connection`** (`getChannel()` returns the real channel →
   non-blocking I/O → the **virtual-threads win**: no platform-thread-per-connection).
4. **Subject extraction over a channel** — `getClientSubject` sourced from
   `SSLEngine.getSession().getPeerCertificates()` instead of `SSLSocket`'s.

Every one of those is a prerequisite for QUIC; UDS de-risks them on a trivial transport.

## 3. The QUIC delta (what changes vs UDS)

| Concern | UDS | QUIC |
|---|---|---|
| Byte layer | `SocketChannel(UNIX)` | UDP (`DatagramChannel`) inside the QUIC stack |
| TLS | `SSLEngine` over the channel | QUIC's **integrated** TLS 1.3 |
| Auth/identity | reuse `SSLContext`+`AuthManager` via `SSLEngine` (direct, JSSE) | **bridge** the auth *logic* to a non-JSSE TLS stack (see §3a) — NOT an `SSLContext` drop-in |
| Multiplexing | one connection (mux on top, as today) | **one JERI request ⇄ one QUIC stream; retire the mux** |
| Connection | `getChannel()` = UDS channel | a QUIC stream pair behind `Connection`'s streams |

**Wins** (beyond UDS): no transport head-of-line blocking (independent streams);
1-RTT/0-RTT handshakes; **connection migration** (roaming nodes keep connections — the
HaLOW mesh / instrument-federation case); **polyglot** — QUIC (RFC 9000, mature in
every language incl. Rust `quinn`) + DER payload = a fully documented, language-neutral
stack end to end, the same "trust the documented artifact" principle as the DER work.

**Caveats** (decide explicitly):
- **Constraint-driven cert selection** depends on the QUIC API exposing `SSLContext`
  (or the `KeyManager`/`TrustManager` SPI). If it only takes a static cert/key, we lose
  per-call credential selection — must verify before committing (see §5).
- **0-RTT data is replayable / not forward-secret** — never carry the §7.3 reducing-
  context block (or any auth-bearing payload) in 0-RTT; gate 0-RTT to idempotent calls
  or disable it.
- **UDP middleboxes** throttle/block QUIC on some networks — keep `SslEndpoint`/
  `TcpEndpoint` as fallbacks (a proxy already carries multiple endpoints; dual-export
  like UDS SOW §7).
- **QUIC-stack maturity** — you inherit the impl's bugs (cf. blog-6's "disciplined
  decoder" caution, applied to the QUIC stack).

## 3a. The TLS-stack reality (corrects an earlier assumption)

The JDK's own QUIC is **client-only and internal** — it backs `HttpClient`/HTTP-3 (there
is no HTTP/3 *server* in the JDK), exposed only as `jdk.internal…`. A JERI
`ServerEndpoint` must *accept* connections and needs a public API, so the JDK QUIC is a
non-starter on both counts. QUIC therefore requires a third-party stack with **client +
server**: **Kwik** (pure-Java, server-capable — keeps the no-native ethos) or
**Netty/`quiche`** (native — fights the pure-Java + Rust-musl story).

Consequence: those stacks ship their **own TLS 1.3** (Kwik = `agent15`; `quiche` =
BoringSSL), **not JSSE**. So the `SSLContext`/`AuthManager` *drop-in* that works for UDS
(real JSSE `SSLEngine`) does **not** work for QUIC — the auth *logic*
(`chooseClientAlias` constraint→cert selection, `checkServerTrusted`,
`SubjectCredentials`, SPIFFE matching) must be **bridged** to the stack's own
cert-selection + trust callbacks. The auth *logic* is still the durable, reusable asset;
what differs per transport is the TLS engine that hosts it.

So the QUIC make-or-break is **"does the stack expose dynamic client-cert selection +
trust callbacks rich enough to host the JGDMS auth logic?"** — not "does it take an
`SSLContext`". One softening: a **SPIFFE** workload has essentially **one SVID**, so a
stack allowing a single statically-configured client cert per connection may suffice for
the SPIFFE deployment even without JGDMS's full multi-principal dynamic selection; the
general X.500 multi-principal case is the harder one.

This recalibrates the **stepping-stone scope**: UDS proves the structural layer
(Endpoint SPI, channel-`Connection`, vthreads) **and** direct JSSE auth reuse, but does
**not** de-risk the QUIC TLS-auth bridge (JSSE → `agent15`/BoringSSL) — QUIC's dominant
remaining cost/risk.

## 3b. Option: clean QUIC with `SSLEngine` (the DirtyChai unlock)

The non-JSSE bridge (§3a) exists only because third-party stacks ship their own TLS. A
fork-owner can avoid it entirely. **A standard public `SSLEngine` cannot drive QUIC** —
RFC 9001 needs the TLS stack to expose raw **handshake messages** (for CRYPTO frames,
per encryption level), the **TLS 1.3 traffic secrets** (QUIC derives its own AEAD
packet keys), and the **`quic_transport_parameters`** extension, while doing *no* record
framing. Public `SSLEngine` emits records and hides secrets → unusable for QUIC. That is
why Kwik/`quiche` reimplement TLS.

**DirtyChai changes this — confirmed by JEP 517** ("HTTP/3 for the HTTP Client API",
delivered **JDK 26**, so DirtyChai-27 has it). JEP 517 states (a) two firm non-goals:
*"not a goal to provide a server-side implementation of HTTP/3"* (client-only) and
*"not a goal to provide an API for the QUIC protocol"* (no public QUIC API); and (b) the
decisive line: *"This first implementation of HTTP/3 will not support secure-socket
providers other than the default provider, **SunJSSE**. Support for third-party
secure-socket providers would require **adding methods to the provider SPI**…"* So the
QUIC TLS **is SunJSSE** (JSSE-based, as hypothesised) with an **internal QUIC-TLS
provider-SPI** that is simply not public yet (JEP 517 flags exposing it as future work).

**Verified against DirtyChai source (2026-06-30 — see the dedicated handoff SOW
`SOW-DirtyChai-QUIC-TLS-Investigation.md`).** The QUIC-TLS layer is real and already
factored out of the HttpClient transport: interface `jdk.internal.net.quic.QuicTLSEngine`
(SSLEngine-like, in **java.base**), factory `jdk.internal.net.quic.QuicTLSContext`
(constructed *from an `SSLContext`*), impl `sun.security.ssl.QuicTLSEngineImpl`. The engine
**already supports server mode** (`setUseClientMode`, server-only handshake states) and
exposes the mTLS surface (`getSSLParameters`/`setSSLParameters`, `getSession` peer-certs,
`getDelegatedTask` for vthreads) — so the JEP 517 *client-only* limit is the **transport**
(HttpClient), not the TLS engine. The package is qualified-exported only to `java.net.http`,
so an export to the JGDMS module is mechanically trivial.

**Correction to an earlier optimism — the SunJSSE-only limit DOES bite, and the gate is
LOAD-BEARING (not a one-line relaxation).** I previously said a JGDMS custom `AuthManager`
sidesteps it; the source disproves that, and a deeper read (2026-06-30 review of the handoff
SOW) shows the fix is bigger than relaxing one `instanceof`. `QuicTLSContext` gates on
`SSLContextImpl.isUsableWithQuic()` (`SSLContextImpl.java:485-486`), literally
`return trustManager instanceof X509TrustManagerImpl;`. JGDMS's `AuthManager`
(`extends FilterX509TrustManager implements X509KeyManager`) is a **custom** X509 trust
manager — **not** `X509TrustManagerImpl` — so a JGDMS-`AuthManager` `SSLContext` is rejected
at construction. **But deleting that one line is not enough.** The QUIC engine reuses the
*shared* TLS 1.3 machinery (`QuicTLSEngineImpl` → `new TransportContext(...)`,
`enabledProtocols = TLS13`), so cert validation runs through the normal
`sun.security.ssl.CertificateMessage`. There the trust dispatch (`CertificateMessage.java`,
~`:1214-1314`) handles `SSLEngine` and `SSLSocket` transports via the **standard**
`X509ExtendedTrustManager` SPI, but for a `QuicTLSEngineImpl` transport it calls a
**SunJSSE-internal** overload `X509TrustManagerImpl.check{Client,Server}Trusted(chain,
authType, QuicTLSEngineImpl)` (`X509TrustManagerImpl.java:143-149`) and **hard-throws
`new CertificateException("QUIC only supports SunJSSE trust managers")`** at `:1233`
(server validating client cert) and `:1294` (client validating server cert) for any other
trust manager. The root cause: that overload has **no equivalent on the public
`X509ExtendedTrustManager` SPI**, because `QuicTLSEngineImpl` is *not* a
`javax.net.ssl.SSLEngine`, so the standard `checkServerTrusted(chain, authType, SSLEngine)`
cannot carry the QUIC engine. This **is** JEP 517's "would require adding methods to the
provider SPI" caveat — manifesting **inside the TLS engine's cert path**, not just the
transport layer. So relaxing the gate means **both** the `SSLContextImpl:486` `instanceof`
**and** a `CertificateMessage` change. The three options (smallest defensible surface
first), for Peter to weigh (advise-only on the AI side; the handoff SOW §3 Q1/Q5 develops
the P2 argument):
- **(c) — DirtyChai-local, recommended:** add a `CertificateMessage` branch routing a custom
  `X509ExtendedTrustManager` in QUIC mode through the 2-arg `checkServerTrusted(chain,
  authType)` (or a thin `SSLEngine` adapter exposing `getHandshakeSession()`/
  `getSSLParameters()`). JGDMS's `FilterX509TrustManager` is a plain `X509TrustManager`
  (2-arg) that does its own SPIFFE/X.500 evaluation and never consumes the connection
  object — so the 2-arg path gives it exactly the interface it already implements. The
  **P2 cost**: the 2-arg path drops SunJSSE's engine-tied endpoint-identification +
  algorithm-constraint enforcement in QUIC mode — the relaxation must show JGDMS (or the
  caller) re-imposes those, or accept their loss with explicit reasoning.
- **(a) — upstream-correct, out of scope here:** add a QUIC overload to the *public*
  `X509ExtendedTrustManager` SPI — the JCP/standards-level change JEP 517 deliberately
  avoided; a compatibility commitment, not a DirtyChai-local patch.
- **(b) — large:** adapt `QuicTLSEngineImpl` to present as an `SSLEngine` so the existing
  `…SSLEngine` overload applies — semantically awkward (SSLEngine wrap/unwrap ≠ QUIC).

Good news that survives: apart from the trust-dispatch overload, everything else in cert
handling already runs the standard path (shared `TransportContext`/`HandshakeContext`/
`CertificateMessage`), which is what makes option (c) a bounded change rather than a rewrite.
There is also an **asymmetry** worth confirming: there is **no** equivalent "only supports
SunJSSE" hard-stop or `instanceof X509KeyManagerImpl` on the **KeyManager** side, so
local-cert selection (`chooseEngineClientAlias`/`chooseEngineServerAlias`) likely already
reaches the standard `X509ExtendedKeyManager` SPI — the trust side is the hard part, not the
key side (handoff SOW §3 Q2). With option (c) (or a) implemented:

- QUIC TLS is **JSSE** ⇒ **direct `SSLContext`/`AuthManager` reuse** — §3a's bridge
  problem disappears; constraint-driven cert selection + SPIFFE Subject extraction work
  unchanged, and it's the *same* engine the P1 `SSLEngine` migration uses (shared auth
  wiring with UDS).
- **Server support** is present in the engine (server-only states exist); confirm the impl's
  server path is complete/exercised (handoff SOW §3 Q7).
- **Pure-Java, no native** — fits the Rust-musl-embedded story.

**The relocated cost:** everything below TLS — a **from-scratch QUIC transport** (RFC
9000 packets/frames/streams/flow-control/migration/retry + RFC 9002 loss-recovery/
congestion). Large and security-critical (why Kwik/`quiche` took years; the blog-6
"disciplined decoder" caution at full force). **Counterweight:** owning the
security-critical wire is exactly JGDMS's posture for the DER codec; this retires the
mux *and* the TCP+TLS plumbing for one standards-based, polyglot, migration-capable
transport.

**Decision shape:** own a QUIC transport (big, standards-based, we control the decode)
vs. adopt Kwik + auth bridge (less code, external dep) vs. defer. The bounded
high-value first step either way is the **DirtyChai QUIC-mode `SSLEngine` de-risk** in §5.

## 4. Phased plan

1. **P1 — `SSLEngine` migration of `net.jini.jeri.ssl` (the keystone).** Rewrite
   `SslConnection` record I/O as an `SSLEngine` wrap/unwrap loop over the `Connection`
   channel; complete the `X509Extended{Key,Trust}Manager` overloads; source
   `getClientSubject` from `SSLEngine.getSession()`. Validate over **TCP** (full
   regression of the SPIFFE matching test) — same transport, new record layer.
2. **P2 — UDS endpoint (the stepping stone).** `UnixEndpoint`/`UnixServerEndpoint` +
   channel-backed `Connection`/`ServerConnection`; a UDS variant of the matching test
   (per-service VMs talk UDS). Banks the vthread/non-blocking win. (UDS SOW §9.)
3. **P3 — QUIC endpoint.** `QuicEndpoint`/`QuicServerEndpoint`; map JERI request ⇄ QUIC
   stream; reuse `SSLContext`+`AuthManager`; **retire the mux** behind QUIC; QUIC
   variant of the matching test; keep TCP/SSL + UDS as fallbacks.

Each phase is additive on the `Endpoint` SPI — nothing above the transport
(`AtomicILFactory`, dispatchers, §7.3 reducing-context, DER, DGC) changes.

## 5. De-risk spike (do FIRST, before P3 — possibly before P1 sequencing)

The whole QUIC case hinges on the **Java QUIC implementation**. The **Path A** spike is now
fully scoped in its own handoff doc — **`SOW-DirtyChai-QUIC-TLS-Investigation.md`** — which
already locates the SunJSSE QUIC-TLS engine in DirtyChai and identifies the concrete blocker.
That blocker is now understood to be **load-bearing**: not just the
`isUsableWithQuic()` → `X509TrustManagerImpl` gate (§3b) but a downstream hard-throw in
`sun.security.ssl.CertificateMessage` (a custom trust manager fails the QUIC handshake even
with the gate relaxed), rooted in an SPI-shape mismatch (`QuicTLSEngineImpl` is not an
`SSLEngine`). The summary below frames the two architectures; defer to that doc for Path A.
Spike to answer:
1. **Two architectures** (JDK confirmed client-only + no public QUIC API, JEP 517):
   - **A — clean / DirtyChai (preferred):** DirtyChai exposes SunJSSE's existing
     QUIC-TLS provider-SPI (a QUIC-mode `SSLEngine`); JGDMS builds the QUIC *transport*
     (RFC 9000/9002). TLS stays JSSE → **direct `AuthManager` reuse**.
   - **B — Kwik:** adopt Kwik (pure-Java, server-capable) for transport + TLS; **bridge**
     the auth logic to `agent15` (non-JSSE).
2. **The make-or-break, per path:**
   - **A (now scoped in the handoff SOW):** the engine SPI is confirmed present
     (`jdk.internal.net.quic.QuicTLSEngine`/`QuicTLSContext`, server-mode capable). The
     make-or-break is now better understood: the QUIC engine **does** reuse the standard
     TLS 1.3 machinery and the standard `X509ExtendedTrustManager` SPI for the actual
     validation logic — *but* the trust **dispatch** in `CertificateMessage` hard-codes a
     SunJSSE-only `X509TrustManagerImpl(...QuicTLSEngineImpl)` overload and throws for any
     other trust manager (§3b). So the gate is **load-bearing**, and the smallest defensible
     fix is option (c): a `CertificateMessage` branch routing a custom
     `X509ExtendedTrustManager` through the 2-arg (or `SSLEngine`-adapter) path, **plus** the
     `SSLContextImpl:486` relaxation — with a P2 argument for the endpoint-identification /
     algorithm-constraint enforcement that the 2-arg path drops. Remaining open items:
     server-path completeness (§3 Q7), the KeyManager-side asymmetry (§3 Q2 — likely already
     standard-SPI), and the transport reuse-vs-rebuild scoping (§3 Q6). That is the handoff
     SOW's §3.
   - **B:** does Kwik expose **dynamic client-cert selection + trust callbacks** rich
     enough to host the JGDMS auth logic (`chooseClientAlias` / `checkServerTrusted` /
     `SubjectCredentials` / SPIFFE)? If only a single static client cert per connection,
     the SPIFFE single-SVID case may suffice (general X.500 multi-principal deferred).
3. **Conformance/interop matrix** (from JEP 517's own test list): a JGDMS QUIC server
   must interop with **Netty, quic-go, quiche, Neqo, nghttp3** clients and with `quinn`
   (Rust) — RFC 9000/9001/9002 conformance, paired with a DER payload smoke test.
4. **Is the stream API blocking / virtual-thread-friendly?** (must fit the vthread
   model without `ThreadLocal`, per `no-threadlocal-virtual-threads`).
5. **Peer-cert extraction** for `getClientSubject` from the QUIC handshake (Path A:
   `QuicTLSEngine.getSession()`).
6. **Cross-language interop**: a `quinn` (Rust) ↔ JDK/Kwik smoke test, to validate the
   polyglot claim with DER payloads (the embedded-JERI federation case).

## 6. Open questions
1. Sequencing: do P1 (`SSLEngine`) now to bank the vthread win regardless of QUIC, or
   gate it on the §5 spike? (Recommend P1 regardless — it's independently valuable.)
2. Endpoint selection across TCP/UDS/QUIC in a multi-endpoint proxy (dual/tri-export,
   capability/preference order, graceful degradation) — generalizes UDS SOW §7.
3. QUIC connection idle-timeout + migration vs JERI connection reuse + DGC lifecycle.
4. Does retiring the mux lose anything the mux provided beyond multiplexing (its
   request framing, abort semantics, the `ServerConnection` model)? Audit before P3.

## 7. References
- `SOW-DirtyChai-QUIC-TLS-Investigation.md` — **the Path A handoff**: DirtyChai source
  findings (QUIC-TLS engine location, the `isUsableWithQuic` gate), the questions to
  answer, advise-only constraints, and the deliverable.
- `SOW-Unix-Domain-Socket-JERI-Transport.md` — the stepping stone (the `SSLSocket`→
  `SSLEngine` crux, options (i)/(ii), platform constraints, dual-export).
- `net.jini.jeri.connection.Connection` — streams + optional `getChannel()` (the SPI).
- `net.jini.jeri.ssl.{SslConnection,SslServerEndpointImpl,AuthManager,ClientAuthManager,
  ServerAuthManager,Utilities,SubjectCredentials}` — `SSLContext`+`AuthManager` (reuse);
  `SSLSocket`-coupled today.
- `net.jini.jeri.tcp.{TcpEndpoint,TcpServerEndpoint}` — transport template.
- Memory: `no-threadlocal-virtual-threads`, `spiffe-halow-mesh-federation`,
  `gls-instrument-federation` (Rust embedded JERI), `jgdms-marshallingformat-der-invocation-layer`
  (DER polyglot payload to pair with the QUIC wire).
