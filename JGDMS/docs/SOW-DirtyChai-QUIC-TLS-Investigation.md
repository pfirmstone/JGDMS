# SOW — DirtyChai QUIC-TLS Exposure Investigation

**(Path A enablement for a QUIC JERI transport)**

- **Authored:** 2026-06-30 (handoff from the JGDMS QUIC transport investigation)
- **Status:** Investigation / advisory only — **no source contributions** (DirtyChai is advise-only for AI)
- **Companions:** `SOW-QUIC-JERI-Transport.md` (esp. §3b, §5), `SOW-Unix-Domain-Socket-JERI-Transport.md` (the shared `SSLEngine`-migration keystone)
- **Repos:** investigation target = `~/GitHub/DirtyChai` (OpenJDK 27 fork, contains JEP 517 QUIC); cross-ref = `~/GitHub/JGDMS`

---

## 0. Objective

Determine **whether, and at what cost, DirtyChai can expose SunJSSE's QUIC-TLS engine** so
that JGDMS can build a QUIC JERI transport reusing its **existing `AuthManager`** (a custom
X509 Key/Trust manager) for SPIFFE/X.500 mutual TLS — i.e. **Path A** of
`SOW-QUIC-JERI-Transport.md` §3b/§5: *JGDMS builds the QUIC transport (RFC 9000/9002),
TLS stays JSSE, auth is reused — no bridge.* The alternative (Path B / Kwik) re-hosts the
entire auth layer onto a non-JSSE TLS stack and is the fallback if Path A proves
infeasible.

**Deliverable:** an ADVICE document with a **Go / No-Go / Go-with-caveats** verdict on
Path A plus a minimal, human-implementable change-set. **You must not write or commit any
`.java`, `module-info.java`, or build file** (see §4).

---

## 1. Why this matters (context, so you can skip re-deriving it)

The QUIC *TLS* problem is **already solved inside the JDK** — SunJSSE implements the TLS 1.3
handshake in QUIC mode (JEP 517, "HTTP/3 for the HTTP Client API", delivered JDK 26;
DirtyChai is 27 so it has it). JEP 517's stated non-goals are *no server-side HTTP/3* and
*no public QUIC API*, and it notes "this first implementation … will not support
secure-socket providers other than the default provider, SunJSSE. Support for third-party
secure-socket providers would require adding methods to the provider SPI." The TLS
*handshake-engine* capability (server mode, mTLS, peer-cert extraction) is genuinely
present and SunJSSE-based — that part of the original framing holds. **But do not read the
"provider SPI" caveat as confined to the HttpClient/transport layer.** The 2026-06-30 review
(now folded into §2.5) found the caveat made concrete **inside the TLS engine's
certificate-validation path**: QUIC cert validation dispatches to a SunJSSE-internal
`X509TrustManagerImpl` overload that takes the `QuicTLSEngineImpl`, with **no equivalent on
the public `X509ExtendedTrustManager` SPI** — so a custom trust manager is rejected mid-
handshake, not merely at `SSLContext` construction. The question for JGDMS is therefore
**exposure + custom-auth acceptance**, where *custom-auth acceptance* is the load-bearing,
non-trivial part (§2.4, §2.5, Q1/Q5) — not a one-line gate relaxation.

If Path A lands, it converges with the `SSLEngine`-migration keystone (P1 in the UDS/QUIC
SOWs): JGDMS's auth logic (`chooseClientAlias` / `checkServerTrusted` / `SubjectCredentials`
/ SPIFFE matching) runs over an *engine* rather than an `SSLSocket`, and UDS + QUIC + classic
TLS all share that one auth-over-engine asset.

---

## 2. What I already established (do NOT redo — build on it)

I have already read the DirtyChai source. The findings below are verified against it; cited
paths are real. **Start by confirming these, then go straight to §3.**

### 2.1 The stack is already split — TLS in `java.base`, transport in `java.net.http`

**TLS layer (java.base — the keystone for Path A):**
- `jdk.internal.net.quic.QuicTLSEngine` — the engine **interface** (SSLEngine-like)
  `src/java.base/share/classes/jdk/internal/net/quic/QuicTLSEngine.java`
- `jdk.internal.net.quic.QuicTLSContext` — the **factory**, constructed *from an
  `SSLContext`*; `createEngine(host,port)` → `QuicTLSEngine`
  `src/java.base/share/classes/jdk/internal/net/quic/QuicTLSContext.java`
- impl: `sun.security.ssl.QuicTLSEngineImpl`, `QuicKeyManager`, `QuicCipher`,
  `QuicTransportParametersExtension`, `QuicEngineOutputRecord` (in
  `src/java.base/share/classes/sun/security/ssl/`).
  ⚠️ **`QuicKeyManager` is the QUIC *packet-protection* key manager** (INITIAL/HANDSHAKE/
  ONE_RTT AEAD + header-protection secrets, key update/discard), **not** the X509 credential
  `KeyManager`. X509 cert selection/validation lives in the shared TLS 1.3 classes
  (`CertificateMessage`, `X509{Key,Trust}ManagerImpl`) — see §2.5.
- support types: `jdk.internal.net.quic.{QuicTLSContext, QuicVersion, QuicOneRttContext,
  QuicTransportParametersConsumer, QuicKeyUnavailableException, QuicTransportException,
  QuicTransportErrors}`

**Transport layer (java.net.http — HttpClient-coupled, RFC 9000/9002):**
- `jdk.internal.net.http.quic.*` — `QuicClient`, `QuicEndpoint`, `QuicConnection(Impl)`,
  `QuicSelector`, congestion controllers, RTT estimator, pacer, timer queue, `packets/`,
  `streams/`, `frames/` (in `src/java.net.http/share/classes/jdk/internal/net/http/quic/`)

The clean separation is the good news: **the TLS engine does not depend on the HttpClient
transport.** JGDMS's prerequisite is the TLS layer; the transport layer is a separate
reuse-vs-rebuild question (§3 Q6).

### 2.2 The TLS engine already supports server mode and mTLS surface — it is NOT client-only

`QuicTLSEngine` exposes (verified signatures): `setUseClientMode(boolean)` /
`getUseClientMode()`; `getSSLParameters()` / `setSSLParameters(SSLParameters)` (the
client-auth hook); `getSession()` / `getHandshakeSession()` (peer-cert extraction for
`getClientSubject`); `KeySpace{INITIAL,HANDSHAKE,RETRY,ZERO_RTT,ONE_RTT}`;
`HandshakeState{NEED_RECV_CRYPTO, NEED_SEND_CRYPTO, NEED_RECV_HANDSHAKE_DONE,
NEED_SEND_HANDSHAKE_DONE, NEED_TASK, …}` (note the **server-only** states);
`consumeHandshakeBytes(KeySpace, ByteBuffer)`; `getDelegatedTask()` (the `NEED_TASK`
pattern → virtual-thread-friendly, like `SSLEngine`); `setLocalQuicTransportParameters`;
key discard/availability; header-protection sample size; auth-tag size.

⇒ The **JEP 517 "client-only" constraint is the transport (HttpClient), not the TLS engine.**
The TLS engine has explicit server states and a server handshake.

### 2.3 Exposure delta is trivial — `jdk.internal.net.quic` is qualified-exported to ONE module

`src/java.base/share/classes/module-info.java`:
```
exports jdk.internal.net.quic to
    java.net.http;
```
So mechanically, Path A needs that list to include the JGDMS module (or a public facade).
**That part is easy. It is not the real cost.**

### 2.4 ⚠️ THE LANDMINE — `isUsableWithQuic()` rejects any custom TrustManager

`QuicTLSContext(SSLContext)` calls `isQuicCompatible()`, which calls
`SSLContextImpl.isUsableWithQuic()`
(`src/java.base/share/classes/sun/security/ssl/SSLContextImpl.java:485`):
```java
public boolean isUsableWithQuic() {
    return trustManager instanceof X509TrustManagerImpl;
}
```
This is JEP 517's "SunJSSE-only" limit **made concrete**: QUIC TLS is gated to SSLContexts
whose TrustManager is SunJSSE's own `X509TrustManagerImpl`.

**JGDMS does not use `X509TrustManagerImpl`.** Verified in the cwd
(`JGDMS/jgdms-jeri/src/main/java/net/jini/jeri/ssl/`):
- `AuthManager extends FilterX509TrustManager implements X509KeyManager` (`AuthManager.java:52`)
- `FilterX509TrustManager extends X509ExtendedKeyManager implements X509TrustManager` (`FilterX509TrustManager.java:50`)
- `Utilities.SSLContextInfo` carries the `(SSLContext, AuthManager)` pair; the `AuthManager`
  is the single object used as **both** the `X509KeyManager` and the `X509TrustManager` when
  the `SSLContext` is `init`'d (`Utilities.java:534`, `:690`+).

So a JGDMS `AuthManager`-bearing `SSLContext` **fails `isUsableWithQuic()` → `QuicTLSContext`
throws `IllegalArgumentException`.** This gate, not the package export, is the heart
of the Path A go/no-go. Also note `QuicTLSContext` reaches `SSLContextImpl` via a `VarHandle`
"horrible hack" (its own comment) on the private `SSLContext.contextSpi` field.

**⚠️ But this gate is only the *first* of two — relaxing it alone does NOT work.** See §2.5:
the QUIC handshake hard-throws deeper, in `CertificateMessage`, for any non-`X509TrustManagerImpl`
trust manager. Earlier framing (and earlier drafts of this SOW) treated `isUsableWithQuic` as
plausibly "an over-cautious guard … small, defensible change." The 2026-06-30 source review
disproves that: **the gate is load-bearing**, and the real fix is a `CertificateMessage`
change plus the gate relaxation, with a P2 argument. Read §2.5 before answering Q1/Q5.

### 2.5 ⚠️ THE REAL DEPTH — a second, load-bearing hard-stop in `CertificateMessage` (verified 2026-06-30)

The §2.4 `isUsableWithQuic` gate is fail-fast at `SSLContext` construction. Even if you
delete it, the handshake fails deeper. This was found by reading the actual validation path,
and it **reframes Q1/Q5 from "is the gate conservative?" (no) to "how do we route a custom
trust manager through a connection-context-preserving path without weakening validation?"**

**The engine reuses the shared TLS 1.3 machinery — good news with one exception.**
`QuicTLSEngineImpl(SSLContextImpl)` builds `this.conContext = new TransportContext(...)` with
`conContext.sslConfig.enabledProtocols = List.of(ProtocolVersion.TLS13)`
(`sun/security/ssl/QuicTLSEngineImpl.java:99,123-141`). So peer-cert parsing and validation
run through the **normal** `sun.security.ssl.CertificateMessage`, not QUIC-specific code.

**The exception — the trust dispatch (`CertificateMessage.java`, `T13CertificateConsumer`):**
```java
// server validating the client's cert (checkClientCerts), ~:1214-1248
X509TrustManager tm = shc.sslContext.getX509TrustManager();
if (tm instanceof X509ExtendedTrustManager) {
    if (transport instanceof SSLEngine engine)      { ((X509ExtendedTrustManager)tm).checkClientTrusted(certs, authType, engine); }
    else if (transport instanceof SSLSocket socket) { ((X509ExtendedTrustManager)tm).checkClientTrusted(certs, authType, socket); }
    else if (transport instanceof QuicTLSEngineImpl qtlse) {
        if (tm instanceof X509TrustManagerImpl tmImpl) {
            tmImpl.checkClientTrusted(certs, authType, qtlse);          // SunJSSE-internal overload
        } else {
            throw new CertificateException("QUIC only supports SunJSSE trust managers");  // :1233
        }
    }
}
```
The client-side mirror (`checkServerCerts`, ~:1253-1314) throws the identical message at
`:1294`. Both directions of JGDMS's mandatory mTLS therefore fail for a custom `AuthManager`.

**Root cause — an SPI-shape mismatch, not mere caution.** QUIC validation must hand the
trust manager the `QuicTLSEngineImpl` as its connection-sensitive context, and only
`X509TrustManagerImpl` has a matching overload —
`checkClientTrusted(X509Certificate[], String, QuicTLSEngineImpl)` /
`checkServerTrusted(..., QuicTLSEngineImpl)` (`sun/security/ssl/X509TrustManagerImpl.java:143-149`).
That overload is **SunJSSE-internal**; the public `javax.net.ssl.X509ExtendedTrustManager`
SPI defines only the `Socket` and `SSLEngine` overloads, and **`QuicTLSEngineImpl` is not a
`javax.net.ssl.SSLEngine`**, so the standard `checkServerTrusted(chain, authType, SSLEngine)`
cannot carry it. This is JEP 517's *"would require adding methods to the provider SPI"* —
inside the engine's cert path.

**The fix is therefore bigger than `SSLContextImpl:486`.** Three options, smallest defensible
surface first (the change-set is developed in Q4; the P2 argument in Q5):
- **(c) — DirtyChai-local, recommended.** Add a `CertificateMessage` branch that, for a
  custom `X509ExtendedTrustManager` in QUIC mode, calls the 2-arg
  `checkServerTrusted(chain, authType)` (or a thin `SSLEngine` adapter exposing
  `getHandshakeSession()`/`getSSLParameters()`). JGDMS's `FilterX509TrustManager` is a plain
  `X509TrustManager` (2-arg) that does its own SPIFFE/X.500 evaluation and never consumes the
  connection object — the 2-arg path is **exactly the interface it already implements**.
  **P2 cost:** the 2-arg path drops SunJSSE's engine-tied endpoint-identification +
  algorithm-constraint enforcement in QUIC mode (normally done in `X509TrustManagerImpl`
  using the engine's session/params). The relaxation must show JGDMS (or the caller)
  re-imposes those, or accept their loss with explicit reasoning. This is the crux of Q5.
- **(a) — upstream-correct, out of scope.** Add a QUIC overload to the *public*
  `X509ExtendedTrustManager` SPI (taking a public QUIC-engine type). This is the
  JCP/standards-level change JEP 517 deliberately avoided — a compatibility commitment, not
  a DirtyChai-local patch.
- **(b) — large/awkward.** Adapt `QuicTLSEngineImpl` to present as an `SSLEngine` so the
  existing `…SSLEngine` overload applies. `SSLEngine`'s wrap/unwrap semantics do not match
  QUIC; high effort, high risk.

**Net:** the verdict tilts to **Go-with-significant-caveats** via option (c), not a one-liner.
Everything in cert handling *except* this dispatch already runs the standard path, which is
what keeps (c) a bounded change rather than a TLS rewrite.

---

## 3. The investigation (the questions to answer, with evidence)

For each: answer **with source citations (`file:line`, method names)**, and where you cannot
determine it from source, state the **experiment** that would (e.g. a throwaway prototype on
the DirtyChai JDK).

**Q1 — Custom-auth acceptance (THE CRUX).** *§2.5 substantially answers this; Q1 is now
confirm-and-quantify, not open discovery.* The finding: peer validation **does** run through
the standard `X509ExtendedTrustManager` SPI in shared code (`CertificateMessage` via
`TransportContext`/`HandshakeContext`), **except** the QUIC dispatch branch, which hard-codes
the SunJSSE-internal `X509TrustManagerImpl(...QuicTLSEngineImpl)` overload and throws for any
other trust manager. So the `isUsableWithQuic` `instanceof` is **not** merely over-cautious —
it guards a real SPI-shape gap. Remaining Q1 work:
- **Confirm the dispatch is the *only* impl-internal reach for the trust path.** Verify no
  other QUIC-mode site bypasses or shortcuts `X509ExtendedTrustManager`; confirm
  `CertificateVerify` (signature check) uses the shared TLS 1.3 path with no QUIC-specific
  trust shortcut.
- **Quantify option (c).** Exactly which `CertificateMessage` lines change, and whether the
  2-arg path vs. an `SSLEngine`-adapter is cleaner — feed Q4/Q5.
- ⚠️ **Pointer correction (earlier drafts were mis-aimed):** cert validation is **not** in
  `QuicTLSEngineImpl` — it delegates to shared `sun.security.ssl.CertificateMessage`. And
  **`QuicKeyManager` is the QUIC *packet-protection* key manager** (INITIAL/HANDSHAKE/ONE_RTT
  AEAD + header-protection secrets; `QuicKeyManager.java:60-73`, `permits …InitialKeyManager,
  HandshakeKeyManager, OneRttKeyManager`) — **not** the X509 credential `KeyManager` / local
  cert chain. Do **not** grep `QuicTLSEngineImpl`/`QuicKeyManager` for `checkServerTrusted`;
  look in `CertificateMessage.java` (~:1214-1314, trust dispatch),
  `X509TrustManagerImpl.java:143-149` (the QUIC overloads), and the `CertificateMessage`
  *producer* / `X509KeyManager` path for local-chain selection (Q2).

**Q2 — Server-side mTLS / client-auth.** Does the QUIC engine, in server mode with
`SSLParameters.setNeedClientAuth(true)` (or want), **request and validate a client
certificate** via the TrustManager's `checkClientTrusted`, and on the client side invoke the
KeyManager's `chooseClientAlias`/`chooseEngineClientAlias`? JGDMS mTLS (SPIFFE/X.500) is
mandatory, and cert selection is **dynamic per connection** (JGDMS picks the alias by
constraints — `AuthManager`/`ClientAuthManager`). Confirm those callbacks fire in QUIC mode.
- **Note the trust-vs-key asymmetry (from the 2026-06-30 review).** The hard-stop in §2.5 is
  **trust-side only**: a quick scan found **no** equivalent `"QUIC only supports SunJSSE"`
  throw and **no** `instanceof X509KeyManagerImpl` gate on the `KeyManager` path. So local-
  cert selection (`chooseEngineClientAlias`/`chooseEngineServerAlias`) likely already reaches
  the standard `X509ExtendedKeyManager` SPI — probably with a `null` `SSLEngine` argument
  (which the SPI permits), since `QuicTLSEngineImpl` is not an `SSLEngine`. **Confirm this:**
  if true, the key side needs no DirtyChai change and only the *trust* side requires option
  (c); if the producer also passes the `QuicTLSEngineImpl` to a SunJSSE-only key-manager
  overload, the change-set (Q4) doubles. Check whether JGDMS's constraint-driven
  `chooseEngineClientAlias` receives a usable (or `null`) engine arg and still selects by
  constraints. This directly sizes Q4.

**Q3 — Peer-cert extraction for `getClientSubject`.** After handshake, do
`getSession().getPeerCertificates()` and local-cert accessors populate on **both** ends?
JGDMS builds the authenticated `Subject` from these (see `getClientSubject`,
`SubjectCredentials` in the cwd).

**Q4 — Minimal exposure design.** Enumerate **exactly** what DirtyChai must change for Path A,
smallest defensible surface first:
- (a) `module-info` qualified-export of `jdk.internal.net.quic` to the JGDMS module — and is
  anything else needed (e.g. `sun.security.ssl` access, or only the `jdk.internal.net.quic`
  facade)?
- (b) the `isUsableWithQuic` relaxation (the §3 Q1/Q5 outcome).
- (c) replace `QuicTLSContext`'s `VarHandle` reach into `SSLContext.contextSpi` with a clean
  accessor?
- (d) **facade vs. raw export:** is a small *supported* public API (a `javax.net.ssl`-adjacent
  factory: `SSLContext` → QUIC engine) cleaner and more defensible than exporting
  `jdk.internal.*` to a non-JDK module? Recommend one.

**Q5 — Security review of relaxing the gate (DirtyChai P2).** Does QUIC packet protection /
0-RTT / key-update impose trust requirements *beyond* standard TLS 1.3 cert validation that
**justify** the `X509TrustManagerImpl` restriction — i.e. is the gate load-bearing or merely
conservative? The relaxation must be argued as **not weakening** the threat model (CLAUDE.md
P2). Cover at least: (i) is the custom TrustManager invoked with the same rigor as
`X509TrustManagerImpl` (no skipped validation in QUIC mode)? **§2.5 makes this concrete:**
option (c)'s 2-arg `checkServerTrusted(chain, authType)` path drops the engine-tied
**endpoint-identification** (RFC 6125 / SNI hostname check) and **algorithm-constraint**
enforcement that `X509TrustManagerImpl` performs via the connection's session/`SSLParameters`.
The P2 argument must establish that (1) JGDMS's `AuthManager` does its own trust evaluation
(SPIFFE/X.500 — it ignores the connection object anyway, so *for JGDMS* nothing is lost), and
(2) the relaxation does not silently weaken validation for *other* callers of a relaxed gate
(e.g. fence option (c) to non-`X509TrustManagerImpl` extended managers, or have the adapter
preserve `getHandshakeSession()`/`getSSLParameters()` so endpoint-ID still runs). (ii) 0-RTT
replay — JGDMS must
**never carry the §7.3 reducing-context ACC block in 0-RTT/early-data**; confirm the engine
lets the caller refuse/segregate 0-RTT.

**Q6 — Transport-layer reuse vs. rebuild (scopes JGDMS, not DirtyChai).** Is
`jdk.internal.net.http.quic.*` (RFC 9000/9002: packets, streams, loss detection, congestion,
pacing) **liftable/exportable** for JGDMS to reuse, or so HttpClient-coupled (`QuicClient` /
`QuicEndpoint` / `QuicSelector` own the datagram socket + event loop, no server accept path)
that JGDMS must **build its own transport** on top of the exposed `QuicTLSEngine`? Estimate
effort both ways. (The TLS exposure is Path A's prerequisite **either** way — this just sizes
the JGDMS build.)

**Q7 — Server-handshake completeness.** Is the **server** handshake path in
`QuicTLSEngineImpl` fully implemented and exercised, or present-but-only-client-tested (since
HttpClient drives client only)? If server mode is untested, flag the test burden and any
TODO/`throw`/unimplemented markers.

---

## 4. Constraints (non-negotiable)

- **DirtyChai is advise-only for AI** (`DirtyChai/CLAUDE.md`): *"Any code or doc change
  requested → Analyze and advise only. Do NOT generate or commit contributions."* You must
  **not** create or edit any `.java`, `module-info.java`, or build file. **All proposed code
  in your deliverable is a proposal for a human to implement — label it as such.**
- Never touch `System.java`, `AccessController.java`, `CombinerSecurityManager.java`,
  `ConcurrentPolicyFile.java`, `Uri.java`, guard `*Permission.java` (CLAUDE.md ❌ list). None
  should arise here; stated for safety.
- Any proposed `isUsableWithQuic` relaxation must satisfy **P2 (never weaken a security
  validation layer)** — frame it as preserving the threat model with explicit reasoning (§3 Q5).
- **Writable artifacts:** only documentation. CLAUDE.md says `*.md` docs are safe to update;
  repo-root advice docs are the accepted output channel. Put your deliverable there.
- **DirtyChai commit messages must NOT credit AI** (no `Co-Authored-By` for Claude) — but you
  are not committing anyway. JGDMS-side `.md` (if you also update the JGDMS SOW/memory) uses
  the JGDMS `Co-Authored-By` footer.
- **Build/run, if needed:** use the DirtyChai JDK as `JAVA_HOME` (memory `dirtychai-build`:
  SM-capable OpenJDK 27 at `DirtyChai/build/.../images/jdk`). Most of this SOW is
  **source analysis — no build required.** If you do build: sandbox must be **disabled**
  (Maven Central / toolchain), and **check for other agents' running `mvn` first — only one
  at a time** (memory `mvn-single-instance-across-agents`, `jgdms-local-build-env`).

---

## 5. Deliverable

A single ADVICE markdown — **`ADVICE-quic-tls-exposure-2026-06-30.md`**, placed in
**`JGDMS/JGDMS/docs/`** (moved here from the DirtyChai repo root at Peter's request, 2026-06-30,
to keep the artifact on the writable JGDMS side and avoid the DirtyChai AI-contribution-policy
question entirely — it advises about DirtyChai but modifies nothing there), containing:

1. **Verdict:** Go / No-Go / Go-with-caveats on Path A, in the first paragraph.
2. **Minimal DirtyChai change-set as a human-implementable proposal** — exact files, the
   export line, the `isUsableWithQuic` relaxation *with its security justification*, the
   facade-vs-export recommendation — each item with `file:line` citations. Clearly marked
   "proposal for a human contributor; not written/committed by AI."
3. **Answers to Q1–Q7** with source evidence.
4. **Transport reuse verdict** (JGDMS builds vs. reuses), feeding back into
   `SOW-QUIC-JERI-Transport.md` §5.
5. **"What JGDMS does next":** how the exposed `QuicTLSEngine` plugs into the P1
   `SSLEngine`-migration keystone so UDS + QUIC + classic TLS share one auth-over-engine asset.
6. **Open questions** you couldn't settle from source, each with the experiment that would.

Then **mirror the verdict** back to the JGDMS side (writable, no AI restriction): update
`JGDMS/JGDMS/docs/SOW-QUIC-JERI-Transport.md` §3b/§5 and the memory note
`jgdms-uds-jeri-transport` with the go/no-go and the concrete blocker/effort.

---

## 6. Success criteria

You have succeeded when, **with source evidence**, you can answer:
1. Can a JGDMS custom-`AuthManager` `SSLContext` drive a QUIC handshake, and what is the
   **smallest, P2-defensible DirtyChai change** to allow it?
2. Does server-mode **mTLS + peer-cert extraction** work (Q2/Q3)?
3. Does JGDMS **build or reuse** the QUIC transport (Q6)?
4. Does relaxing the gate **preserve the threat model** (Q5)?

Anything undecidable from source is listed as an open question with a concrete experiment.

---

## 7. Fast-start pointers

**DirtyChai (verified paths):**
- `src/java.base/share/classes/jdk/internal/net/quic/QuicTLSEngine.java` (interface)
- `src/java.base/share/classes/jdk/internal/net/quic/QuicTLSContext.java` (factory; the VarHandle hack)
- `src/java.base/share/classes/sun/security/ssl/QuicTLSEngineImpl.java` (impl; `TransportContext` wiring, server path — Q2/Q7)
- `src/java.base/share/classes/sun/security/ssl/CertificateMessage.java` (**the trust dispatch — Q1/Q5 live here**, ~:1214-1314; QUIC throw at :1233/:1294)
- `src/java.base/share/classes/sun/security/ssl/X509TrustManagerImpl.java:143-149` (the SunJSSE-only `check{Client,Server}Trusted(...,QuicTLSEngineImpl)` overloads — the SPI gap)
- `src/java.base/share/classes/sun/security/ssl/QuicKeyManager.java` (⚠️ QUIC *packet-protection* keys, NOT the X509 credential manager)
- `src/java.base/share/classes/sun/security/ssl/SSLContextImpl.java:485` (`isUsableWithQuic`; `chooseTrustManager`/`chooseKeyManager` nearby)
- `src/java.base/share/classes/module-info.java:197` (`exports jdk.internal.net.quic to java.net.http;`)
- `src/java.net.http/share/classes/jdk/internal/net/http/quic/` (transport — Q6)

**JGDMS cross-reference (cwd `JGDMS/jgdms-jeri/src/main/java/net/jini/jeri/ssl/`):**
- `AuthManager.java`, `ClientAuthManager.java`, `ServerAuthManager.java`,
  `FilterX509TrustManager.java` (the custom Key/Trust managers)
- `Utilities.java` (`SSLContextInfo`, `get*SSLContextInfo` — how the `SSLContext` is built)
- `SubjectCredentials.java`, `SslConnection.java`, `SslServerEndpointImpl.java`,
  `SslEndpointImpl.java` (the `SSLSocket` coupling to be replaced by the engine)

**JGDMS docs/memory:**
- `JGDMS/JGDMS/docs/SOW-QUIC-JERI-Transport.md` (§3b Path A, §5 de-risk spike)
- `JGDMS/JGDMS/docs/SOW-Unix-Domain-Socket-JERI-Transport.md` (the `SSLEngine` keystone)
- memory: `jgdms-uds-jeri-transport`, `jgdms-spiffe-auth`, `dirtychai-build`,
  `dirtychai-ai-contribution-policy`

**References:** RFC 9000 (transport), RFC 9001 (TLS), RFC 9002 (loss/congestion);
JEP 517 (HTTP/3 for the HTTP Client API).
