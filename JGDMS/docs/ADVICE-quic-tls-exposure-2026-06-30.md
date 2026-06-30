# ADVICE — Exposing SunJSSE's QUIC-TLS engine for a custom-auth (JGDMS) QUIC transport

- **Date:** 2026-06-30
- **Target:** DirtyChai (OpenJDK 27 fork) — the SunJSSE QUIC-TLS engine added with JEP 517.
- **Question (Path A):** can DirtyChai expose SunJSSE's QUIC-TLS engine so that JGDMS can
  drive a QUIC handshake with its existing custom `AuthManager` (X509 Key/Trust manager) for
  SPIFFE/X.500 mutual TLS, and at what cost?
- **Status:** Analysis and advice only, per the OpenJDK Interim Policy on Generative AI
  adopted by DirtyChai and the handoff SOW
  (`JGDMS/JGDMS/docs/SOW-DirtyChai-QUIC-TLS-Investigation.md`). **Every proposed code change
  in this document is a proposal for a human contributor to implement; no source, tests,
  JavaDoc, build files, or commits were generated or modified.**
- **Cross-references:** `SOW-DirtyChai-QUIC-TLS-Investigation.md` (the questions),
  `SOW-QUIC-JERI-Transport.md` §3b/§5, `SOW-Unix-Domain-Socket-JERI-Transport.md` (the
  shared `SSLEngine`-migration keystone).

---

## 1. Verdict — GO WITH CAVEATS

DirtyChai **can** enable Path A with a small, defensible change. The QUIC-TLS engine is real,
server-capable, and — apart from one trust-dispatch branch — already drives certificate
validation and selection through the standard `javax.net.ssl.X509Extended{Key,Trust}Manager`
SPI, builds a populated `SSLSession` for peer-certificate extraction on both ends, and has a
complete, test-exercised server handshake. The one true blocker to a custom JGDMS
`AuthManager` is a **load-bearing** SunJSSE-only trust dispatch (not merely the
`SSLContext`-construction gate), and relaxing it is a bounded, P2-defensible change.

The caveats:

1. **The fix is two coupled edits, not one.** Relaxing the `isUsableWithQuic()` gate alone
   does not work — the handshake then hard-fails deeper in `CertificateMessage`. Both sites
   must change together (§2(b)).
2. **The relaxation must satisfy P2.** The recommended route (option (c)) routes a custom
   trust manager through a connection-context-free validation call, which drops SunJSSE's
   engine-tied endpoint-identification and algorithm-constraint enforcement. This is sound
   for JGDMS (its `AuthManager` self-validates) but must be **fenced** so it does not weaken
   validation for SunJSSE's own managers (§2(b), §3 Q5).
3. **Mutual TLS over QUIC server mode is wired but untested.** The client-auth path is present
   and ungated, but no existing test exercises it. Because JGDMS mTLS is mandatory, a
   client-auth QUIC handshake test is the most important thing to add before relying on
   Path A (§3 Q7).
4. **The dominant cost is relocated to JGDMS, not DirtyChai.** The JDK's QUIC *transport* is
   client-only and fully encapsulated, so JGDMS must build its own QUIC transport (~30k LOC)
   on the exposed engine (§4). That is a JGDMS scope decision; it is not a TLS-feasibility
   blocker, and the engine exposure is the prerequisite either way.

In short: the TLS exposure is feasible and cheap on the DirtyChai side; the go/no-go for the
overall effort is JGDMS's transport-build appetite, not the engine.

---

## 2. Minimal DirtyChai change-set (proposal for a human contributor; not written or committed by AI)

Smallest defensible surface first. All citations are `file:line` in the **DirtyChai** repo
(the investigation target); this advice document lives on the JGDMS side
(`JGDMS/JGDMS/docs/`) and no DirtyChai source/build file was modified.

### (a) Expose the engine

`src/java.base/share/classes/module-info.java:197` currently reads:

```
exports jdk.internal.net.quic to
    java.net.http;
```

Add the JGDMS module to the target list (or, preferred, do not export the internal package at
all and add a supported facade — see (d)). A caller needs **only** `jdk.internal.net.quic`:
the `QuicTLSContext` factory and the `QuicTLSEngine` interface. No `sun.security.ssl` export is
required — `QuicTLSContext` encapsulates the `QuicTLSEngineImpl` impl and reaches
`SSLContextImpl` itself via an internal `VarHandle` (see (c)). The factory surface is small and
already public: `QuicTLSContext.isQuicCompatible(SSLContext)`, `new QuicTLSContext(SSLContext)`,
and `createEngine()` / `createEngine(peerHost, peerPort)`
(`src/java.base/share/classes/jdk/internal/net/quic/QuicTLSContext.java:55,97,113,129`).

This item is **necessary but not sufficient** — it makes the engine reachable but does not, by
itself, make a custom trust manager acceptable. That is item (b).

### (b) Accept a custom `X509ExtendedTrustManager` in QUIC mode — two coupled edits

**(b1)** `src/java.base/share/classes/sun/security/ssl/SSLContextImpl.java:485-486`:

```java
public boolean isUsableWithQuic() {
    return trustManager instanceof X509TrustManagerImpl;
}
```

This is a fail-fast guard at `QuicTLSContext` construction. It must be widened to accept the
trust managers JGDMS uses. Note that JGDMS's `AuthManager`/`FilterX509TrustManager` implements
the 2-arg `javax.net.ssl.X509TrustManager` (not `X509ExtendedTrustManager`), so on
`SSLContext.init()` it is wrapped by `SSLContextImpl.chooseTrustManager` in an
`AbstractTrustManagerWrapper` (an `X509ExtendedTrustManager`). The widened predicate should
therefore accept any `X509ExtendedTrustManager`, not just `X509TrustManagerImpl`.

**(b2)** `src/java.base/share/classes/sun/security/ssl/CertificateMessage.java` — the QUIC
trust dispatch, at `:1228-1234` (server validating the client certificate) and `:1289-1295`
(client validating the server certificate):

```java
} else if (transport instanceof QuicTLSEngineImpl qtlse) {
    if (tm instanceof X509TrustManagerImpl tmImpl) {
        tmImpl.checkClientTrusted(certs.clone(), authType, qtlse);   // SunJSSE-internal overload
    } else {
        throw new CertificateException("QUIC only supports SunJSSE trust managers");
    }
}
```

For a non-`X509TrustManagerImpl` extended manager, add a branch that validates **without** the
`QuicTLSEngineImpl` connection object — either the 2-arg
`((X509ExtendedTrustManager) tm).checkServerTrusted(certs, authType)` /
`checkClientTrusted(certs, authType)` from the base `X509TrustManager` contract, or a thin
adapter that presents the QUIC handshake as an `SSLEngine` (exposing `getHandshakeSession()` and
`getSSLParameters()`), so the existing `…SSLEngine` overload applies and endpoint-identification
still runs.

**Why this second edit is unavoidable (root cause).** The QUIC engine reuses the shared TLS 1.3
machinery — `QuicTLSEngineImpl(SSLContextImpl)` builds `new TransportContext(...)` with
`enabledProtocols = TLS13`
(`src/java.base/share/classes/sun/security/ssl/QuicTLSEngineImpl.java:123-141`) — so peer
validation runs through the normal `CertificateMessage`. For `SSLEngine`/`SSLSocket` transports
that path uses the **standard** `X509ExtendedTrustManager` SPI
(`CertificateMessage.java:1216-1226`, `:1278-1288`), but the QUIC branch calls a
**SunJSSE-internal** overload `X509TrustManagerImpl.check{Client,Server}Trusted(chain, authType,
QuicTLSEngineImpl)` (`src/java.base/share/classes/sun/security/ssl/X509TrustManagerImpl.java:143-149`).
That overload has **no equivalent on the public `X509ExtendedTrustManager` SPI**, because
`QuicTLSEngineImpl` is not a `javax.net.ssl.SSLEngine` and so cannot be passed to the standard
`checkServerTrusted(chain, authType, SSLEngine)`. This is exactly JEP 517's *"would require
adding methods to the provider SPI"* caveat — present **inside the engine's certificate path**,
not only at the transport layer. Hence the gate is load-bearing, and (b1) without (b2) merely
relocates the failure from construction to the handshake.

**P2 security justification (see also §3 Q5).** This change *widens an existing capability
narrowly*; it does not remove or weaken a validation layer:

- SunJSSE's own managers keep their exact current path (the `X509TrustManagerImpl` branch is
  untouched). Only previously-**rejected** custom managers gain a path.
- The connection-context-free (2-arg) path drops the **endpoint-identification** (RFC 6125 /
  SNI hostname check) and **algorithm-constraint** enforcement that `X509TrustManagerImpl`
  performs via the connection's session/`SSLParameters`. For JGDMS this loses nothing: its
  `AuthManager` performs its own SPIFFE/X.500 trust evaluation and ignores the connection
  object entirely (it already forwards its engine overloads to the legacy overloads with a
  `null` transport — see §3 Q2).
- To preserve the threat model for *any other* caller, **fence the new branch** to
  non-`X509TrustManagerImpl` extended managers, and/or implement the `SSLEngine`-adapter
  variant so endpoint-identification still runs. The fail-secure posture is retained: an
  unrecognised/again-unsupported manager should still throw, not silently pass.

### (c) The `VarHandle` "horrible hack" is not a blocker

`QuicTLSContext` reaches the private `SSLContext.contextSpi` field via a `VarHandle`
(`src/java.base/share/classes/jdk/internal/net/quic/QuicTLSContext.java:60`, `:133-150`). This
is internal to `java.base` and works regardless of the caller, so it is **not** part of the
exposure cost. It is optional hygiene only: it could be replaced with a package-private
`SSLContextSpi` accessor, or folded behind the facade (d). Advisory; no action required for
Path A.

### (d) Facade vs. raw export — recommend a supported facade

Exporting `jdk.internal.net.quic` to a non-JDK module ties JGDMS to an unsupported internal API
that can change without notice and requires `--add-exports` plumbing. A small **supported
facade** — a public factory mapping an `SSLContext` to a QUIC engine — is the better choice: it
gives DirtyChai a stable contract, hides the `VarHandle` and the `sun.security.ssl` dependency,
lets DirtyChai bundle the (b) relaxation (and any future 0-RTT controls) behind a reviewed
surface, and prototypes what JEP 517 itself flags as future work (exposing the QUIC-TLS provider
SPI). The raw qualified export is acceptable for a throwaway spike; the facade is the production
recommendation.

**Recommended package: `au.zeus.jdk.net.ssl`** (in `java.base`, exported). The facade must live
in `java.base` — only there can it reach `jdk.internal.net.quic` and `new QuicTLSContext(...)` —
and DirtyChai's established convention for its own java.base additions is `au.zeus.jdk.*`
(`au.zeus.jdk.authorization.{guards,policy,sm,spire,…}`, `au.zeus.jdk.concurrent.*`, and the
existing `au.zeus.jdk.net.Uri`). A `…net.ssl` (or `…net.quic`) subpackage sits naturally beside
`au.zeus.jdk.net.Uri`, is correctly categorised as transport/TLS, is DirtyChai-owned and
-maintained, and — crucially — exists in `java.base` **only**, so there is no split package.

**Do NOT put the facade in `org.apache.river.api.security`.** Two independent reasons:
1. **Split package.** That package already exists in *both* `java.base` (DirtyChai embeds 12
   authorization-grant classes there, exported unqualified at `module-info.java:138`) **and**
   `jgdms-platform` (the full ~35-class River authorization/policy set), with all 12 embedded
   class names overlapping exactly. It is the single most fragile namespace in the stack — the
   source of the "recompile `--release 21` silently emits the embedded copy → `NoSuchMethodError`"
   trap. Adding a java.base-only facade member to a package that also lives in `jgdms-platform`
   deepens that resolution ambiguity. A `java.base`-only package avoids it entirely.
2. **Category mismatch.** `org.apache.river.api.security` is authorization (permission grants,
   policy, principals). An `SSLContext` → QUIC-engine factory is JSSE/transport plumbing; placing
   it there couples two unrelated lifecycles and pollutes a stable authorization contract.

Also **do not** use `javax.net.ssl`: a fork should not squat the standard namespace (it would
collide with a future upstream JEP that exposes the QUIC-TLS SPI). Keep the fork-local supported
API under `au.zeus.jdk.net.*`; if it is ever upstreamed, it becomes `javax.net.ssl` at that point.
This is a maintenance-vs-coupling call for the project lead, but the namespace choice is not — it
should be `au.zeus.jdk.net.*`.

---

## 3. Answers to Q1–Q7 (with source evidence)

### Q1 — Custom-auth acceptance (the crux)

The engine **does** consume the standard SPI for the validation *logic* — it reuses
`CertificateMessage` via a shared `TransportContext` (`QuicTLSEngineImpl.java:123-141`) — but the
trust **dispatch** hard-codes a SunJSSE-internal overload for the QUIC transport and throws for
anything else (`CertificateMessage.java:1228-1234`, `:1289-1295`; the overload at
`X509TrustManagerImpl.java:143-149`). So the `isUsableWithQuic` `instanceof` is **not** merely
over-cautious — it guards a real SPI-shape gap (`QuicTLSEngineImpl` is not an `SSLEngine`).
**Conclusion:** the gate is load-bearing; the fix is the bounded option (b)/(c) above, not a
one-line relaxation. (Recommend confirming, as a low-risk closeout, that the `CertificateVerify`
signature-check consumer runs the shared TLS 1.3 path with no separate QUIC trust shortcut; the
gate inventory in Q7 indicates no such gating.)

### Q2 — Server-side mTLS / client-auth and dynamic cert selection

- **Trust side:** hard-gated, as in Q1.
- **Key side: not hard-gated.** Local-alias selection lives in `X509Authentication`
  (`X509Authentication.java:315-337` server, `:221-243` client). In QUIC mode it uses a
  SunJSSE-internal `chooseQuic{Server,Client}Alias(..., QuicTLSEngineImpl)` *only* when the key
  manager is a SunJSSE `X509KeyManagerCertChecking`; otherwise it falls back to the **standard**
  legacy `chooseServerAlias`/`chooseClientAlias(keyType, issuers, null)`. There is **no** "QUIC
  only supports SunJSSE" throw and **no** `instanceof X509KeyManagerImpl` gate on the key side.
- **JGDMS compatibility (verified JGDMS-side):** JGDMS implements its constraint-driven
  selection *in* the legacy overloads
  (`ClientAuthManager.java:312`, `ServerAuthManager.java:333`), and its engine overloads merely
  forward to them with a `null` transport
  (`ClientAuthManager.java:378-379`, `ServerAuthManager.java:409-410`). The QUIC fallback calls
  exactly those legacy methods in the same `null`-transport shape JGDMS already uses internally
  — so constraint-driven client/server cert selection works **unchanged in QUIC mode with no
  DirtyChai change.**
- **mTLS request:** `CertificateRequest` is reachable and **ungated** for QUIC server mode
  (enabled in `SignatureAlgorithmsExtension.java:269-273`; the producer body
  `CertificateRequest.java:918-955` has no `isQuic` gate), so a server can request client auth.

### Q3 — Peer-cert extraction for the authenticated `Subject`

Works on both ends. `QuicTLSEngineImpl.getSession()` returns `conContext.conSession`
(`QuicTLSEngineImpl.java:187-188`), a real `SSLSessionImpl`. During the handshake,
`setPeerCertificates` (server `CertificateMessage.java:1245`, client `:1308`) and
`setLocalCertificates` (server `:954`, client `:1057`/`:1059`) populate the handshake session,
which `finish()` promotes to `conSession` (`SSLSessionImpl.java:760-766` — `finish()` returns
`this`). `getPeerCertificates()`, `getLocalCertificates()`, and `getPeerPrincipal()` all return
the chains/principal on client and server, so JGDMS can build its authenticated `Subject` from
the peer chain at both ends. (Nuance: peer certs are write-once, `SSLSessionImpl.java:719-723`.)

### Q4 — Minimal exposure design

See §2. Smallest surface: (a) export `jdk.internal.net.quic` only (no `sun.security.ssl`), or a
facade (d); (b) the two-edit trust-manager relaxation with its P2 fence; (c) the `VarHandle` is
internal and needs no change. The key-manager side and the session plumbing need no change
(Q2, Q3).

### Q5 — Security review of relaxing the gate (P2)

- **(i) Validation rigor.** The relaxation does not remove a layer; it widens one narrowly. The
  P2 cost is the dropped endpoint-identification and algorithm-constraint enforcement on the
  2-arg path, argued sound for JGDMS (self-validating `AuthManager`) and fenced for other
  callers — see §2(b).
- **(ii) 0-RTT / early data — unimplemented, so the concern is moot today.** `KeySpace.ZERO_RTT`
  is enum-only scaffolding: there is **no** ZeroRtt key manager (`QuicKeyManager` permits only
  `Initial`/`Handshake`/`OneRtt` — `QuicKeyManager.java:200,346,471`); the `early_data` TLS
  extension is null-wired (no producer/consumer, empty supported-protocols —
  `SSLExtension.java:554-565`); and every engine entry point that receives `ZERO_RTT` returns
  `false`/no-keys or throws (`QuicTLSEngineImpl.java:279`, `:430`, `:483-485`). The record layer
  states it outright: "Note that JDK does not support 0-RTT yet" (`SSLTransport.java:181-182`).
  The caller supplies the `KeySpace` to `encryptPacket`/`decryptPacket`, so JGDMS both controls
  and can segregate by epoch, and JGDMS's hard requirement (never carry auth-bearing payloads in
  early data) is satisfied **by absence**. **Forward caveat:** if a future DirtyChai rebase wires
  up `early_data` and adds a ZeroRtt key manager, re-run this analysis — the interface exposes no
  explicit `disableEarlyData()` switch, so refusal currently rests on the engine throwing for
  `ZERO_RTT` and on the caller not driving that key space.

### Q6 — Transport reuse vs. rebuild

Rebuild — see §4.

### Q7 — Server-handshake completeness

**Complete and exercised.** No server-reachable stubs: the two `UnsupportedOperationException`s
are intentional (ChangeCipherSpec is suppressed for QUIC — `SSLConfiguration.java:527`; the
record-layer key-update variant is unused by QUIC). The server TLS 1.3 flow (ServerHello →
EncryptedExtensions → server Certificate/CertificateVerify/Finished → HANDSHAKE_DONE) runs
through the shared machinery with **no QUIC gating of server message flow**
(`TransportContext.java:199-201`, `:250-263`; `ServerHello.java:720-730`;
`Finished.java:849-858`), and `NEED_SEND_HANDSHAKE_DONE` is produced and consumed server-side
(`QuicTLSEngineImpl.java:684-687`, `:828-844`). Server mode is exercised by tests
(`QuicTLSEngine*Test` set `setUseClientMode(false)`; the test-lib `QuicServerConnection` drives
real network tests). **Two qualifiers:**

- **mTLS is wired but untested.** No QUIC test sets `CLIENT_AUTH_REQUIRED/REQUESTED`, so
  server-requested client auth — mandatory for JGDMS — is present-but-unexercised. A dedicated
  client-auth QUIC handshake test is the most important pre-Path-A item.
- **`getDelegatedTask()` is a TODO no-op** (returns `null` — `QuicTLSEngineImpl.java:757-758`;
  `useDelegatedTask()` returns `true` at `:773`), so handshake tasks run inline. This is fine for
  a virtual-thread model (inline-on-a-vthread is acceptable), but it qualifies any reliance on
  the `NEED_TASK` offload pattern.

---

## 4. Transport reuse verdict — JGDMS builds its own

The JDK QUIC *transport* (`src/java.net.http/share/classes/jdk/internal/net/http/quic/`) is
**not reusable** for a JGDMS server endpoint:

- **Client-only.** `QuicConnectionImpl.isClientConnection()` is hardcoded `true`
  (`QuicConnectionImpl.java:3712-3714`); `startHandshake()` throws for a non-client connection
  (`:2881-2883`); there is no `accept`/`listen`/`bind`-to-listen / server-connection factory;
  and `isServer()` is always `false` because `QuicClient` is the only concrete `QuicInstance`
  (`QuicEndpoint.java:1229-1235`) — the server branches are dead scaffolding with no `QuicServer`
  class to activate them.
- **Fully encapsulated.** `src/java.net.http/share/classes/module-info.java` exports only
  `java.net.http` (line 291); `jdk.internal.net.http.quic` is not exported at all.
- **Size / coupling.** ~32,000 LOC across 83 files; only thinly coupled to HttpClient (generic
  `jdk.internal.net.http.common` utilities plus one bypassable `AltServicesRegistry` overload),
  so it is liftable *in principle* — but the missing server/accept path is decisive.

JGDMS needs a server endpoint, so it must **build its own QUIC transport** (packets, frames,
streams, flow control, loss detection, congestion control, pacing, connection migration, and an
accept loop — RFC 9000/9002) on top of the exposed `QuicTLSEngine`. This is the dominant Path-A
cost, and it lands on JGDMS, not DirtyChai. (The TLS exposure is the prerequisite either way;
this only sizes the JGDMS build.)

---

## 5. What JGDMS does next (tie-in to the P1 `SSLEngine`-migration keystone)

The exposed `QuicTLSEngine` is engine-shaped — `consumeHandshakeBytes`/`getHandshakeBytes` per
`KeySpace`, a `getDelegatedTask` `NEED_TASK` pattern, and `getSession()` peer-cert extraction —
the same shape as `SSLEngine`. So the P1 keystone in the JGDMS SOWs (migrating
`net.jini.jeri.ssl` record I/O from `SSLSocket` to `SSLEngine`) produces the **same
auth-over-engine asset** QUIC needs: `AuthManager` (Key/Trust manager) + `SubjectCredentials` +
SPIFFE matching ride on the engine unchanged. Once P1 lands (validated over TCP), UDS reuses it
directly (real JSSE `SSLEngine` over a channel), and QUIC reuses the same auth wiring over
`QuicTLSEngine` — with DirtyChai's §2(b) relaxation supplying the only QUIC-specific difference
on the auth side. One auth-over-engine asset, three transports. The QUIC delta versus UDS is the
transport build (§4) plus the trust-dispatch relaxation — not the auth logic.

---

## 6. Open questions (each with the experiment that settles it)

1. **mTLS over QUIC server mode is wired but untested (Q7).** *Experiment:* a throwaway
   prototype on the DirtyChai JDK — two `QuicTLSEngineImpl` instances, the server in server mode
   with client auth required, driven through a full mutual handshake, asserting both ends'
   `getSession().getPeerCertificates()` populate. This doubles as the regression test DirtyChai
   should add. **Highest priority before relying on Path A.**
2. **Does option (c)'s 2-arg path actually skip anything JGDMS needs?** *Experiment:* prototype
   the §2(b) relaxation and run a real JGDMS SPIFFE/X.500 handshake over the engine; confirm the
   authenticated `Subject` and constraint checks match the `SSLSocket` path. (Largely answered —
   `AuthManager` self-validates — but worth confirming end-to-end.)
3. **`CertificateVerify` in QUIC mode** uses the shared path with no QUIC trust shortcut —
   *Experiment:* trace the T13 `CertificateVerify` consumer for a `QuicTLSEngineImpl` transport.
   Low risk; the Q7 gate inventory indicates no QUIC gating.
4. **Facade vs. qualified export (§2(d))** — recommended home is `au.zeus.jdk.net.ssl` (java.base,
   exported); NOT `org.apache.river.api.security` (split package + category mismatch) and NOT
   `javax.net.ssl` (namespace squatting). The facade-vs-raw-export choice is a
   maintenance-vs-coupling call for the project lead,
   given JGDMS owns the fork.
5. **`getDelegatedTask()` no-op (Q7)** — does JGDMS's virtual-thread model need real task
   delegation, or is inline-on-vthread sufficient? If the former, that is a second (small)
   DirtyChai item.

---

*Produced as analysis and advice for human implementation, per the handoff SOW and the OpenJDK
Interim Policy on Generative AI adopted by DirtyChai. No source, tests, JavaDoc, build files, or
commit messages were generated or modified, and no pull request was created.*
