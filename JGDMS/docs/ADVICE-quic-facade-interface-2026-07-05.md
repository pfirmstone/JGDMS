# ADVICE — proposed `au.zeus.jdk.net.ssl` facade interface for JERI's QUIC transport (P3-prep)

- **Date:** 2026-07-05
- **Target:** DirtyChai (OpenJDK 27 fork) — `sun.security.ssl.QuicTLSEngineImpl` (JEP 517 QUIC-TLS).
- **Purpose:** A read-only enumeration of the **minimal method surface** JERI's forthcoming QUIC
  transport actually needs from DirtyChai's QUIC-TLS engine, and a **recommended facade
  interface** in a new `au.zeus.jdk.net.ssl` package (java.base) that exposes exactly that surface
  — instead of exporting `jdk.internal.net.quic.QuicTLSEngine` as a named type to a third-party
  module. **This document is analysis and advice only.** Produced under the OpenJDK Interim
  Policy on Generative AI adopted by DirtyChai (`DirtyChai/CLAUDE.md`: *"Analyze and advise only.
  Humans write the fix. Do NOT generate code."*). **No DirtyChai source, JavaDoc, tests, or build
  files were created or modified.** Every recommendation below is for a human DirtyChai
  contributor to implement, reviewed and adjusted at their discretion. This document lives on the
  writable JGDMS side.
- **Source verification baseline:** DirtyChai repo `C:\Users\peter\Documents\GitHub\DirtyChai`,
  branch `trunk`, **HEAD `3cac7b7fcf4d`**. Confirmed **no diff** on
  `QuicTLSEngineImpl.java` between this HEAD and the `a5f70fec85f` baseline used by the prior
  ADVICE (`ADVICE-quic-tls-dirtychai-locations-2026-07-05.md`) — all `file:line` citations in that
  document and in this one are current against `3cac7b7fcf4d`.
- **JGDMS-side baseline:** trunk `ca174da4a1e`, this artifact produced on isolated worktree
  `JGDMS-wt-quic-facade`, branch `docs/quic-facade-interface`.
- **Relationship to prior ADVICE:** `ADVICE-quic-tls-dirtychai-locations-2026-07-05.md` maps *where*
  to change DirtyChai for trust-dispatch (the `SSLEngine`-adapter path, item C: "Supported facade:
  `SSLContext → QUIC engine` factory only; export the package"). **This document specifies what
  that facade (item C) should actually look like** — the concrete minimal interface, its method
  table, and the rationale for the shape.

---

## 0. Where the source lives (verification note)

The `file:line` citations named in the task (`QuicTLSEngineImpl.java:187 getSession`, `:192
getHandshakeSession`, `:209/:214 getSSLParameters`, `:279 keysAvailable(ZERO_RTT)`, `:483
consumeHandshakeBytes`) are all in:

```
C:\Users\peter\Documents\GitHub\DirtyChai\src\java.base\share\classes\sun\security\ssl\QuicTLSEngineImpl.java
```

This is a local clone of `https://github.com/pfirmstone/DirtyChai` (OpenJDK 27 fork), on branch
`trunk`, confirmed clean (`git status` → nothing to commit) at HEAD `3cac7b7fcf4d`. `CLAUDE.md` is
at the repo root and was read (not modified) to confirm the advise-only constraint. The public
`QuicTLSEngine` SPI interface (implemented by `QuicTLSEngineImpl`) is at:

```
C:\Users\peter\Documents\GitHub\DirtyChai\src\java.base\share\classes\jdk\internal\net\quic\QuicTLSEngine.java
```

and the engine factory is:

```
C:\Users\peter\Documents\GitHub\DirtyChai\src\java.base\share\classes\jdk\internal\net\quic\QuicTLSContext.java
```

All three files are unchanged relative to the `a5f70fec85f` baseline (`git diff --stat` between
the two commits, scoped to these paths, is empty).

---

## 1. Cluster 1 — AUTH/SESSION (the SPIFFE/mTLS surface JGDMS reuses)

This is the surface JGDMS's `net.jini.jeri.ssl` package already depends on for the *existing*
TCP/TLS transport (`SslConnection.java:268` calls `sslSocket.getSession()` and stores it as
`javax.net.ssl.SSLSession`, consumed later for peer-principal extraction). The QUIC facade should
provide the **same JSSE-standard session type** so JGDMS's SPIFFE/X.500 peer-principal logic
(`FilterX509TrustManager`, `SubjectCredentials`, `SslTrustVerifier`) is reused unmodified across
both transports — no QUIC-specific principal-extraction code needed in JGDMS.

| Facade method (proposed) | Underlying `QuicTLSEngineImpl` method | file:line |
|---|---|---|
| `SSLSession getSession()` | `public SSLSession getSession()` | `QuicTLSEngineImpl.java:187` |
| `SSLSession getHandshakeSession()` | `public SSLSession getHandshakeSession()` | `QuicTLSEngineImpl.java:192` |
| `SSLParameters getSSLParameters()` | `public SSLParameters getSSLParameters()` | `QuicTLSEngineImpl.java:214` |
| *(carried inside `SSLParameters`)* `getAlgorithmConstraints()` | `SSLParameters.getAlgorithmConstraints()` on the object `getSSLParameters()` (`:214`) returns; engine-internal derivation at package-private `AlgorithmConstraints getAlgorithmConstraints()` | `QuicTLSEngineImpl.java:203-211` (package-private helper), consumed via `:209` |
| *(carried inside `SSLParameters`)* `getEndpointIdentificationAlgorithm()` | `SSLParameters.getEndpointIdentificationAlgorithm()` on the object `getSSLParameters()` returns | `QuicTLSEngineImpl.java:214` |
| `void setSSLParameters(SSLParameters)` | `public void setSSLParameters(SSLParameters)` | `QuicTLSEngineImpl.java:158-184` |

**Note on `getAlgorithmConstraints()`/`getEndpointIdentificationAlgorithm()`:** these are **not**
separate methods on `QuicTLSEngineImpl` — they are accessors on the `javax.net.ssl.SSLParameters`
object returned by `getSSLParameters()` (`:214`). `QuicTLSEngineImpl` also has a **package-private**
helper `AlgorithmConstraints getAlgorithmConstraints()` (`:203-211`) used internally
(context-sensitive: handshake-context constraints during handshake, else the configured
`SSLParameters`' constraints) — that helper is **not public** and is **not** part of the proposed
facade surface; the facade only needs the public `getSSLParameters()` path, matching exactly how
`X509TrustManagerImpl.checkTrusted(..., QuicTLSEngineImpl, ...)` derives constraints for the
existing (SunJSSE-internal) QUIC trust path (see prior ADVICE §0.1, `X509TrustManagerImpl.java:253-254`).

**Also useful, arguably Cluster-1-adjacent:**

| Facade method (proposed) | Underlying method | file:line |
|---|---|---|
| `String getApplicationProtocol()` | `public String getApplicationProtocol()` | `QuicTLSEngineImpl.java:218-222` |
| `String getPeerHost()` | `public String getPeerHost()` | `QuicTLSEngineImpl.java:761-764` |
| `int getPeerPort()` | `public int getPeerPort()` | `QuicTLSEngineImpl.java:766-769` |

`getApplicationProtocol()` is the ALPN-negotiated protocol string; JERI's endpoint/transport
negotiation may want this for protocol-selection logging or assertions, mirroring how the existing
`SslEndpoint`/`SslConnection` code path can inspect the negotiated protocol. `getPeerHost()` /
`getPeerPort()` are advisory-only (constructor hints, `QuicTLSEngineImpl.java:100-101,127-129`),
useful for diagnostics but **not security-load-bearing** — endpoint identity is established by the
SPIFFE/X.500 cert-chain evaluation, not by these advisory strings.

---

## 2. Cluster 2 — HANDSHAKE-DRIVE (QUIC-TLS as key-schedule + CRYPTO provider, RFC 9001)

| Purpose | Facade method (proposed) | Underlying `QuicTLSEngineImpl` method | file:line |
|---|---|---|---|
| Feed inbound CRYPTO per encryption level | `void consumeHandshakeBytes(KeySpace, ByteBuffer)` | `public void consumeHandshakeBytes(KeySpace keySpace, ByteBuffer payload) throws QuicTransportException` | `QuicTLSEngineImpl.java:477-479` (body `477-626`; ZERO_RTT rejection at `483-485`) |
| Produce outbound CRYPTO to send per level | `ByteBuffer getHandshakeBytes(KeySpace)` | `public ByteBuffer getHandshakeBytes(KeySpace keySpace) throws IOException` | `QuicTLSEngineImpl.java:435-448` |
| Per-level key/secret availability | `boolean keysAvailable(KeySpace)` | `public boolean keysAvailable(KeySpace keySpace)` | `QuicTLSEngineImpl.java:273-284` (ZERO_RTT case `→ false` at `:279`) |
| Discard per-level keys | `void discardKeys(KeySpace)` | `public void discardKeys(KeySpace keySpace)` | `QuicTLSEngineImpl.java:286-295` |
| Header-protection sample size per level | `int getHeaderProtectionSampleSize(KeySpace)` | `public int getHeaderProtectionSampleSize(KeySpace keySpace)` | `QuicTLSEngineImpl.java:297-304` |
| Header-protection mask (packet AEAD support) | `ByteBuffer computeHeaderProtectionMask(KeySpace, boolean, ByteBuffer)` | `public ByteBuffer computeHeaderProtectionMask(...)` | `QuicTLSEngineImpl.java:306-320` |
| AEAD tag size | `int getAuthTagSize()` | `public int getAuthTagSize()` | `QuicTLSEngineImpl.java:322-330` |
| Packet AEAD encrypt (per level) | `void encryptPacket(KeySpace, long, IntFunction<ByteBuffer>, ByteBuffer, ByteBuffer)` | `public void encryptPacket(...)` | `QuicTLSEngineImpl.java:332-339` |
| Packet AEAD decrypt (per level) | `void decryptPacket(KeySpace, long, int, ByteBuffer, int, ByteBuffer)` | `public void decryptPacket(...)` | `QuicTLSEngineImpl.java:341-357` (ONE_RTT-before-complete guard `348-353`) |
| Derive INITIAL keys (connection-ID-salted) | `void deriveInitialKeys(QuicVersion, ByteBuffer)` | `public void deriveInitialKeys(...)` | `QuicTLSEngineImpl.java:698-709` |
| QUIC transport parameters — set local | `void setLocalQuicTransportParameters(ByteBuffer)` | `public void setLocalQuicTransportParameters(ByteBuffer params)` | `QuicTLSEngineImpl.java:786-789` |
| QUIC transport parameters — get peer | *(see GAP-1 below — no direct getter; push-only)* | `void setRemoteQuicTransportParametersConsumer(QuicTransportParametersConsumer)` | `QuicTLSEngineImpl.java:817-821`; consumer invoked from `processRemoteQuicTransportParameters(ByteBuffer)` at `:823-826` |
| Handshake status (what's needed next) | `HandshakeState getHandshakeState()` | `public HandshakeState getHandshakeState()` | `QuicTLSEngineImpl.java:258-261` |
| Current send key space | `KeySpace getCurrentSendKeySpace()` | `public KeySpace getCurrentSendKeySpace()` | `QuicTLSEngineImpl.java:268-271` |
| Handshake completion (TLS-level) | `boolean isTLSHandshakeComplete()` | `public boolean isTLSHandshakeComplete()` | `QuicTLSEngineImpl.java:865-883` |
| Server: try to mark HANDSHAKE_DONE sent | `boolean tryMarkHandshakeDone()` | `public boolean tryMarkHandshakeDone()` | `QuicTLSEngineImpl.java:828-844` |
| Client: try to record HANDSHAKE_DONE received | `boolean tryReceiveHandshakeDone()` | `public boolean tryReceiveHandshakeDone()` | `QuicTLSEngineImpl.java:846-863` |
| Delegated-task hook (trust-manager task pattern) | `Runnable getDelegatedTask()` | `public Runnable getDelegatedTask()` | `QuicTLSEngineImpl.java:755-759` |
| Whether caller must pump delegated tasks | `boolean useDelegatedTask()` | `public boolean useDelegatedTask()` | `QuicTLSEngineImpl.java:771-774` |
| QUIC version negotiated | `void versionNegotiated(QuicVersion)` | `public void versionNegotiated(...)` | `QuicTLSEngineImpl.java:711-726` |
| Supported QUIC versions | `Set<QuicVersion> getSupportedQuicVersions()` | `public Set<QuicVersion> getSupportedQuicVersions()` | `QuicTLSEngineImpl.java:224-227` |
| Client/server mode | `void setUseClientMode(boolean)` / `boolean getUseClientMode()` | same names | `QuicTLSEngineImpl.java:144-150`, `152-155` |
| Retry-packet integrity (Retry token, not per-level CRYPTO) | `void signRetryPacket(...)` / `void verifyRetryPacket(...)` | same names | `QuicTLSEngineImpl.java:359-387`, `389-423` |
| Restart handshake after Version Negotiation | `void restartHandshake()` | `public void restartHandshake() throws IOException` | `QuicTLSEngineImpl.java:791-815` |
| 1-RTT context supply (peer-ack info for key updates) | `void setOneRttContext(QuicOneRttContext)` | `public void setOneRttContext(...)` | `QuicTLSEngineImpl.java:229-232` |

**`KeySpace` enum values** (`jdk.internal.net.quic.QuicTLSEngine.KeySpace`,
`QuicTLSEngine.java:48-54`): `INITIAL`, `HANDSHAKE`, `RETRY`, `ZERO_RTT`, `ONE_RTT`. The facade's
`KeySpace` should be a **re-declared enum in `au.zeus.jdk.net.ssl`** with the same five constants
(see §4) — **not** a re-export of `jdk.internal.net.quic.QuicTLSEngine.KeySpace`, so that no
internal type leaks into the facade's public signatures. Per JEP 517 and DirtyChai's `ZERO_RTT`
refusal (see §5), the facade's `KeySpace` enum **omits `ZERO_RTT`** (4 constants:
`INITIAL, HANDSHAKE, RETRY, ONE_RTT`) — the facade should not offer a value it will unconditionally
reject.

**`HandshakeState` enum values** (`QuicTLSEngine.java:56-87`): `NEED_RECV_CRYPTO`,
`NEED_RECV_HANDSHAKE_DONE`, `NEED_SEND_CRYPTO`, `NEED_SEND_HANDSHAKE_DONE`, `NEED_TASK`,
`HANDSHAKE_CONFIRMED`. These map directly to the facade's own `HandshakeState` (re-declared, same
6 constants — no ZERO_RTT-specific state exists here to exclude).

---

## 3. Analysis — minimal set JERI needs vs. everything else exposed

`QuicTLSEngineImpl` implements two interfaces: `jdk.internal.net.quic.QuicTLSEngine` (23 methods,
`QuicTLSEngine.java`) and `sun.security.ssl.SSLTransport` (package-private, SunJSSE-internal
handshake-plumbing marker — **not relevant to JERI**, must not be exposed). Additionally the class
itself declares two `public` constructors and several package-private/test-only members.

**IN the minimal facade (both clusters combined, ~21 methods):**

All of §1 and §2 above. This is the entire *public* `QuicTLSEngine` SPI **except** the three items
excluded below. JERI's QUIC transport is a from-scratch RFC 9001 implementation (per the task
framing: "TLS as key-schedule + CRYPTO provider... NOT a record engine") and genuinely drives every
stage of the handshake state machine itself (feed/produce CRYPTO, pull keys per level, encrypt/
decrypt packets, track handshake state, drive delegated tasks) — so nearly the whole engine-facing
SPI is load-bearing. This is **not** over-exposure: it is the necessary consequence of JERI owning
the QUIC record layer instead of consuming a pre-built one.

**EXCLUDED from the facade (must NOT be exposed):**

1. **`ZERO_RTT` as a usable `KeySpace` value** — see §5. The facade's `KeySpace` enum has 4
   constants, not 5.
2. **The package-private `AlgorithmConstraints getAlgorithmConstraints()` helper**
   (`QuicTLSEngineImpl.java:203-211`) — not public on the engine today, and not needed: the facade
   exposes the standard `getSSLParameters().getAlgorithmConstraints()` path instead, which is
   sufficient (§1) and matches what the existing SunJSSE-internal QUIC trust dispatch already uses.
3. **`sun.security.ssl.SSLTransport`** (the second interface `QuicTLSEngineImpl` implements) — an
   internal SunJSSE handshake-plumbing contract with no meaning outside `sun.security.ssl`. The
   facade must not re-expose it, directly or transitively.
4. **The test-only accessor** `public int getOneRttKeyPhase()` (`QuicTLSEngineImpl.java:890-892`,
   explicitly commented `// this is only used in tests`) and the **test-only key-derivation
   overload** `void deriveOneRTTKeys(QuicVersion, SecretKey, SecretKey, CipherSuite, boolean)`
   (`:743-753`, comment `// for testing (PacketEncryptionTest)`) — neither is part of the production
   contract; excluding them is also a security good: they let a caller inject raw traffic secrets
   directly, bypassing the engine's own key schedule.
5. **The two `public QuicTLSEngineImpl(...)` constructors** (`:123-125`, `:127-142`) — construction
   must go through the factory only (`QuicTLSContext.createEngine(...)`, see §4's `Factory`), never
   direct construction of the underlying impl type. This is the existing design of
   `QuicTLSContext` (`QuicTLSContext.java:113-131`) and the facade should preserve that
   factory-only discipline, extending it to the facade's own factory rather than
   `QuicTLSContext` itself (which stays `jdk.internal` and unexported).
6. **`processRemoteQuicTransportParameters(ByteBuffer)`** (package-private,
   `QuicTLSEngineImpl.java:823-826`) — this is the internal delivery mechanism for the consumer
   registered via `setRemoteQuicTransportParametersConsumer`; JERI only needs to *register* a
   consumer (`:817-821`), not to invoke the delivery method itself (SunJSSE's handshake code calls
   it internally when parsing the peer's Encrypted Extensions).
7. **`deriveHandshakeKeys()` / `deriveOneRTTKeys()`** (`:728-733`, `:735-740`) — these are
   `public` on `QuicTLSEngineImpl` but **absent from the `QuicTLSEngine` SPI interface itself**
   (not `@Override`-annotated, not declared in `QuicTLSEngine.java`). They are invoked internally
   by SunJSSE's own handshake dispatch after `HANDSHAKE`/`ONE_RTT` secrets become derivable from the
   TLS key schedule — a facade caller never needs to call these directly, since key derivation is
   an automatic consequence of `consumeHandshakeBytes`/`getHandshakeBytes` progressing the
   handshake. Flagged for completeness; **not** in the facade.

**Net effect:** the facade is smaller than "everything public on `QuicTLSEngineImpl`" by 2
constructors + 2 test-only members + 1 package-private delivery method + 2 SPI-absent derive
methods + 1 non-public helper — and it re-declares `KeySpace` locally with one fewer constant
(`ZERO_RTT` excluded) than the internal enum. It is not smaller than "the `QuicTLSEngine` SPI
proper" by more than that one enum constant, because JERI's ground-up RFC 9001 implementation
uses essentially the whole per-level CRYPTO/key/state contract.

---

## 4. Proposed Java interface sketch (`au.zeus.jdk.net.ssl`, java.base)

**This is a sketch for human review — a starting point for the human maintainer's own design,
not a drop-in implementation.** Package placement follows the existing `au.zeus.jdk.*` convention
already used in DirtyChai for authorization types (`au.zeus.jdk.authorization.*`,
`au.zeus.jdk.net.Uri`, per `CLAUDE.md`'s Project Structure section) — `au.zeus.jdk.net.ssl` sits
alongside `au.zeus.jdk.net.Uri` under the existing `au.zeus.jdk.net` root.

```java
package au.zeus.jdk.net.ssl;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Set;
import java.util.function.IntFunction;
import javax.crypto.AEADBadTagException;
import javax.crypto.ShortBufferException;

/**
 * Facade over the platform's QUIC-TLS handshake-drive engine (RFC 9001: TLS 1.3
 * used as a key-schedule and CRYPTO-stream provider for QUIC, not as a record
 * layer). Exposes only the method surface a QUIC transport implementation needs
 * to drive the handshake and derive per-packet AEAD keys; does not expose the
 * underlying engine type itself.
 *
 * <p>0-RTT is intentionally not supported: {@link KeySpace} has no ZERO_RTT
 * constant, and callers cannot request 0-RTT key material through this facade.
 */
public interface QuicTlsHandshakeDriver {

    /** Per-encryption-level key space. No ZERO_RTT constant: 0-RTT is excluded. */
    enum KeySpace { INITIAL, HANDSHAKE, ONE_RTT, RETRY }

    /** Handshake progress, mirrors the underlying engine's HandshakeState. */
    enum HandshakeState {
        NEED_RECV_CRYPTO,
        NEED_RECV_HANDSHAKE_DONE,
        NEED_SEND_CRYPTO,
        NEED_SEND_HANDSHAKE_DONE,
        NEED_TASK,
        HANDSHAKE_CONFIRMED
    }

    // -- mode / lifecycle --------------------------------------------------
    void setUseClientMode(boolean mode);
    boolean getUseClientMode();
    void setSSLParameters(SSLParameters params);
    SSLParameters getSSLParameters();
    Set<String> getSupportedQuicVersions();          // String form of QuicVersion; see GAP-2
    void versionNegotiated(String quicVersion);

    // -- handshake drive -----------------------------------------------------
    HandshakeState getHandshakeState();
    KeySpace getCurrentSendKeySpace();
    boolean isTLSHandshakeComplete();
    ByteBuffer getHandshakeBytes(KeySpace keySpace) throws IOException;
    void consumeHandshakeBytes(KeySpace keySpace, ByteBuffer payload)
            throws QuicTlsHandshakeException;
    void restartHandshake() throws IOException;
    boolean tryMarkHandshakeDone();                   // server side
    boolean tryReceiveHandshakeDone();                // client side
    Runnable getDelegatedTask();
    boolean useDelegatedTask();

    // -- transport parameters ------------------------------------------------
    void setLocalQuicTransportParameters(ByteBuffer params);
    void setRemoteQuicTransportParametersConsumer(RemoteTransportParametersConsumer consumer);

    // -- keys / packet AEAD ----------------------------------------------------
    void deriveInitialKeys(String quicVersion, ByteBuffer connectionId) throws IOException;
    boolean keysAvailable(KeySpace keySpace);
    void discardKeys(KeySpace keySpace);
    int getHeaderProtectionSampleSize(KeySpace keySpace);
    ByteBuffer computeHeaderProtectionMask(KeySpace keySpace, boolean incoming, ByteBuffer sample)
            throws QuicKeyUnavailableException, QuicTlsHandshakeException;
    int getAuthTagSize();
    void encryptPacket(KeySpace keySpace, long packetNumber,
                        IntFunction<ByteBuffer> headerGenerator,
                        ByteBuffer packetPayload, ByteBuffer output)
            throws QuicKeyUnavailableException, QuicTlsHandshakeException, ShortBufferException;
    void decryptPacket(KeySpace keySpace, long packetNumber, int keyPhase,
                        ByteBuffer packet, int headerLength, ByteBuffer output)
            throws QuicKeyUnavailableException, AEADBadTagException,
                    QuicTlsHandshakeException, ShortBufferException;
    void signRetryPacket(String quicVersion, ByteBuffer originalConnectionId,
                         ByteBuffer packet, ByteBuffer output)
            throws ShortBufferException, QuicTlsHandshakeException;
    void verifyRetryPacket(String quicVersion, ByteBuffer originalConnectionId, ByteBuffer packet)
            throws AEADBadTagException, QuicTlsHandshakeException;

    // -- JSSE-standard session accessors (Cluster 1: SPIFFE/mTLS reuse) -----
    SSLSession getSession();
    SSLSession getHandshakeSession();
}

/** Minimal callback: delivers the peer's QUIC transport parameters (wire-encoded). */
public interface RemoteTransportParametersConsumer {
    void accept(ByteBuffer encodedParameters) throws QuicTlsHandshakeException;
}

/** Facade-level checked exception; wraps the underlying QuicTransportException without
 *  exposing jdk.internal.net.quic types in the facade's public method signatures. */
public class QuicTlsHandshakeException extends Exception { /* ... */ }

/** Facade-level checked exception mirroring QuicKeyUnavailableException. */
public class QuicKeyUnavailableException extends Exception { /* ... */ }

/**
 * Factory: the only supported way to obtain a {@link QuicTlsHandshakeDriver}.
 * Mirrors {@code jdk.internal.net.quic.QuicTLSContext}'s existing factory
 * discipline (construction only through the context, never a direct
 * engine-impl constructor) — see ADVICE §3 item 5.
 */
public final class QuicTlsHandshakeDrivers {
    private QuicTlsHandshakeDrivers() { }

    /** {@return true if the given SSLContext supports QUIC TLS} */
    public static boolean isQuicCompatible(javax.net.ssl.SSLContext sslContext) { /* delegates
        to jdk.internal.net.quic.QuicTLSContext.isQuicCompatible(sslContext) */ throw null; }

    /** Creates a new handshake-drive facade bound to the given SSLContext. */
    public static QuicTlsHandshakeDriver create(javax.net.ssl.SSLContext sslContext) { throw null; }

    /** Creates a new handshake-drive facade with advisory peer-host/port hints. */
    public static QuicTlsHandshakeDriver create(javax.net.ssl.SSLContext sslContext,
                                                 String peerHost, int peerPort) { throw null; }
}
```

**Design points a human reviewer should weigh:**

- **`javax.net.ssl.SSLSession`, not a facade-specific session type.** `getSession()` and
  `getHandshakeSession()` return the **standard JSSE type**, exactly as `QuicTLSEngineImpl` itself
  does (`:187`, `:192`) and exactly as JERI's existing `net.jini.jeri.ssl.SslConnection` already
  consumes for its TCP/TLS transport (`SslConnection.java:268`,
  `sslSocket.getSession() : SSLSession`). This is the module authority's ratified shape: **the
  facade owns its handshake-drive interface, but session/identity flows through the existing JSSE
  contract**, so JGDMS's SPIFFE/X.500 peer-principal logic
  (`FilterX509TrustManager`/`SubjectCredentials`/`SslTrustVerifier`) needs **zero** QUIC-specific
  branching — it already knows how to read an `SSLSession`.
- **`QuicVersion` re-typed as `String`** in the sketch, since `jdk.internal.net.quic.QuicVersion`
  is itself an internal type (see GAP-2, §6) — a human reviewer may instead prefer a small
  facade-local `enum QuicVersion { QUIC_V1, QUIC_V2 }`, which would be cleaner than `String`; the
  sketch uses `String` only to flag that *some* re-typing decision is needed here, not to prescribe
  which.
- **No `KeySpace.ZERO_RTT` constant** — enforces the 0-RTT exclusion (§5) at the type level: a
  caller cannot even construct a `ZERO_RTT` request through this facade, rather than being turned
  away only by a runtime check.
- **Checked exceptions re-declared facade-side** (`QuicTlsHandshakeException`,
  `QuicKeyUnavailableException`) rather than re-exporting
  `jdk.internal.net.quic.QuicTransportException` / `QuicKeyUnavailableException` — consistent with
  "don't re-export internal types," including exception types, which otherwise leak
  `jdk.internal.net.quic` into JERI's `throws` clauses transitively.
- **The factory (`QuicTlsHandshakeDrivers`) is the only construction path**, matching
  `QuicTLSContext`'s existing discipline (`QuicTLSContext.java:97-104` constructor validates
  `isQuicCompatible`; `:113-131` `createEngine`/`createEngine(host,port)`) — this preserves
  "construction only through a checked factory, never a raw impl constructor" (§3 item 5) at the
  facade layer too.
- **Module export:** `au.zeus.jdk.net.ssl` would need a new `exports au.zeus.jdk.net.ssl to
  <JERI's module>;` (or `exports ... ;` unqualified, if DirtyChai's module boundary and Peter's
  prior "no ClassLoaders/capabilities broadcast" security posture allow an unqualified export for
  this narrow, non-capability-bearing facade — a human policy call, not this document's to make).
  This replaces the item-A "Spike: widen `jdk.internal.net.quic` export" path from the prior
  ADVICE with a **narrower, purpose-built export** — the production recommendation that document
  already flagged (item A: *"Production: do NOT export; add facade (item C)"*).

---

## 5. 0-RTT / ZERO_RTT exclusion — confirmed and enforced

Both runtime facts cited in the task are confirmed at the stated lines, unchanged at HEAD `3cac7b7fcf4d`:

- **`keysAvailable(ZERO_RTT) → false`** — `QuicTLSEngineImpl.java:279`:
  ```java
  case ZERO_RTT -> false;
  ```
  (inside the `switch` at `:274-284`).
- **`consumeHandshakeBytes(ZERO_RTT, ...)` throws** — `QuicTLSEngineImpl.java:483-485`:
  ```java
  if (keySpace == KeySpace.ZERO_RTT) {
      throw new IllegalArgumentException("Crypto in zero-rtt");
  }
  ```
  (this is also documented on the SPI interface itself: `QuicTLSEngine.java:419-420`,
  `@throws IllegalArgumentException if keySpace is ZERORTT or payload is empty`).

Corroborating evidence from the prior ADVICE (item E1/E2/E3, reproduced here for completeness,
each independently re-verified at this HEAD):

- `SSLExtension.java:316-318` — the `early_data` extension's `CH_/EE_/NST_EARLY_DATA` enum entries
  are declared **id+name only**, with no producer/consumer wired — `early_data` is inert, not just
  refused at the API surface.
- `QuicKeyManager.java` imports (`:61-63`) only `INITIAL`/`HANDSHAKE`/`ONE_RTT` key managers — there
  is no `ZeroRttKeyManager` class to even hold 0-RTT key material.

**Facade enforcement (this document's contribution over the prior ADVICE):** the proposed
`au.zeus.jdk.net.ssl.QuicTlsHandshakeDriver.KeySpace` enum in §4 **declares only 4 constants**
(`INITIAL, HANDSHAKE, ONE_RTT, RETRY`) — no `ZERO_RTT` value exists in the facade's type system at
all. This is stronger than relying on the runtime refusal alone: a JERI caller cannot construct a
`KeySpace.ZERO_RTT` argument to accidentally hit the `IllegalArgumentException` path, because the
facade's own vocabulary has no such value. **0-RTT is excluded from the facade surface by
construction, not merely by behavior.**

---

## 6. Flagged gaps — methods JERI plausibly needs that are absent or non-public today

### GAP-1: No pull-style "get peer's QUIC transport parameters" accessor

JERI's transport-parameter exchange (max UDP payload size, active connection ID limit, idle
timeout, etc. — RFC 9000 §18) is **push-only** on the current engine: the caller registers a
`QuicTransportParametersConsumer` via `setRemoteQuicTransportParametersConsumer(...)`
(`QuicTLSEngineImpl.java:817-821`), and the consumer's `accept(ByteBuffer)`
(`QuicTransportParametersConsumer.java:32-38`) is invoked *by SunJSSE's own handshake code* when it
parses the peer's Encrypted Extensions — there is **no getter** that lets a caller ask "what are
the peer's transport parameters right now" after the fact. If JERI's transport wants to read the
peer's transport parameters at an arbitrary later point (e.g., a connection-level API that answers
"what's the peer's max_udp_payload_size" outside the parsing callback), it must cache the bytes
itself when the consumer fires — the engine holds no queryable copy. **This is a real API gap, not
a design choice being second-guessed**: a getter mirroring the push callback
(`ByteBuffer getRemoteQuicTransportParameters()`, returning the last-delivered bytes or `null`
before negotiation) would close it cheaply if the human maintainers judge JERI needs pull-style
access. Flagged for the humans to decide; not assumed necessary if JERI's own transport design
already caches the callback's payload.

### GAP-2: `QuicVersion` is an internal type with no public equivalent

`jdk.internal.net.quic.QuicVersion` (referenced throughout `QuicTLSEngine`'s SPI —
`getSupportedQuicVersions()`, `deriveInitialKeys(QuicVersion, ...)`, `versionNegotiated(QuicVersion)`,
`signRetryPacket(QuicVersion, ...)`, `verifyRetryPacket(QuicVersion, ...)`) is itself
`jdk.internal`-scoped and would leak into the facade's public signatures verbatim unless re-typed.
The sketch in §4 punts to `String`, flagging that the human maintainer should choose between (a) a
small facade-local `enum QuicVersion { QUIC_V1, QUIC_V2 }` (cleanest, matches
`SUPPORTED_QUIC_VERSIONS = Set.of(QuicVersion.QUIC_V1, QuicVersion.QUIC_V2)` at
`QuicTLSEngineImpl.java:81-82`), or (b) a wire-format integer/String encoding. **Not a security
gap** — just an API-surface decision the facade design needs before it can compile.

### GAP-3: `getDelegatedTask()` is a permanent no-op — the "trust-manager task hook" JERI may be
### expecting does not actually do anything yet

`QuicTLSEngineImpl.getDelegatedTask()` (`:755-759`):
```java
@Override
public Runnable getDelegatedTask() {
    // TODO: actually delegate tasks
    return null;
}
```
The task explicitly asked for "the `getDelegatedTask`-style trust-manager task hook" — this method
exists on the public SPI and is included in the facade surface (§2), and `useDelegatedTask()`
(`:771-774`) unconditionally returns `true` (i.e., the engine's contract *claims* callers must be
prepared to pump delegated tasks), **but the engine never actually produces one**: this is a
**declared-but-unimplemented** part of the contract (explicit `TODO` in DirtyChai source). If
JERI's transport is written to expect blocking trust-manager work (e.g., CRL/OCSP checks, or the
delegated-task pattern familiar from `SSLEngine.getDelegatedTask()`) to surface here, **it will
never receive one from this engine today** — any such blocking work currently happens
synchronously inside `consumeHandshakeBytes`/`handleHandshakeMessage`
(`QuicTLSEngineImpl.java:628-696`) on whatever thread calls it. This is a gap the human maintainers
should be aware of before JERI's transport is designed around an assumption of asynchronous
delegated-task pumping — the current engine does not support that pattern despite exposing its
API shape.

---

## 7. Summary table — facade inclusion decision, one line per candidate

| Method / member | Cluster | In facade? | Why |
|---|---|---|---|
| `getSession()` | 1 | Yes | JSSE-standard session, reused by SPIFFE/X.500 logic |
| `getHandshakeSession()` | 1 | Yes | same |
| `getSSLParameters()` / `setSSLParameters()` | 1 | Yes | carries algorithm constraints + endpoint-ID |
| package-private `getAlgorithmConstraints()` helper | 1 | No | not public today; `getSSLParameters()` path suffices |
| `getApplicationProtocol()` | 1 (adjacent) | Yes | ALPN result, useful for negotiation logging |
| `getPeerHost()` / `getPeerPort()` | 1 (adjacent) | Yes | advisory only, not security-load-bearing |
| `consumeHandshakeBytes` | 2 | Yes | inbound CRYPTO feed |
| `getHandshakeBytes` | 2 | Yes | outbound CRYPTO produce |
| `keysAvailable` | 2 | Yes (minus ZERO_RTT) | per-level key availability |
| `discardKeys` | 2 | Yes | per-level key lifecycle |
| `getHeaderProtectionSampleSize` / `computeHeaderProtectionMask` | 2 | Yes | packet AEAD support |
| `getAuthTagSize` / `encryptPacket` / `decryptPacket` | 2 | Yes | packet AEAD core |
| `deriveInitialKeys` | 2 | Yes | INITIAL key derivation entry point |
| `setLocalQuicTransportParameters` / `setRemoteQuicTransportParametersConsumer` | 2 | Yes | transport-param exchange (see GAP-1 for the missing getter) |
| `getHandshakeState` / `getCurrentSendKeySpace` / `isTLSHandshakeComplete` | 2 | Yes | handshake status |
| `tryMarkHandshakeDone` / `tryReceiveHandshakeDone` | 2 | Yes | HANDSHAKE_DONE bookkeeping |
| `getDelegatedTask` / `useDelegatedTask` | 2 | Yes (with GAP-3 caveat) | task hook, currently a no-op |
| `versionNegotiated` / `getSupportedQuicVersions` | 2 | Yes | version negotiation |
| `setUseClientMode` / `getUseClientMode` | 2 | Yes | mode |
| `signRetryPacket` / `verifyRetryPacket` | 2 | Yes | Retry-packet integrity |
| `restartHandshake` | 2 | Yes | Version-Negotiation restart |
| `setOneRttContext` | 2 | Yes | 1-RTT peer-ack context supply |
| `KeySpace.ZERO_RTT` | 2 | **No** | 0-RTT excluded by construction (§5) |
| `sun.security.ssl.SSLTransport` (2nd implemented iface) | — | **No** | SunJSSE-internal plumbing, no external meaning |
| `getOneRttKeyPhase()` (test-only) | — | **No** | explicitly test-only in source comment |
| `deriveOneRTTKeys(version, secrets..., clientMode)` (test-only overload) | — | **No** | test-only; would let a caller inject raw secrets, bypassing key schedule |
| `deriveHandshakeKeys()` / `deriveOneRTTKeys()` (production, 0-arg) | — | **No** | not on the `QuicTLSEngine` SPI; internal-only, auto-triggered |
| `processRemoteQuicTransportParameters(ByteBuffer)` | — | **No** | package-private internal delivery, not a caller entry point |
| two `public QuicTLSEngineImpl(...)` constructors | — | **No** | construction only via factory (`QuicTlsHandshakeDrivers`) |

---

## 8. What the human maintainers need to do next (not this document's job)

1. Review this method table and the §4 interface sketch; adjust naming, exception hierarchy, and
   the `QuicVersion` re-typing decision (GAP-2) to DirtyChai's own conventions.
2. Decide GAP-1 (pull-style transport-parameter getter) based on whether JERI's transport design
   needs post-hoc access or can cache the push callback's payload itself.
3. Decide GAP-3's disposition — whether `getDelegatedTask()` should be left a no-op (documented as
   such in the facade's JavaDoc, so JERI doesn't design around a promise the engine doesn't keep)
   or whether wiring real delegated-task support (e.g., for CRL/OCSP checks during the QUIC
   handshake) is now in scope, given a second consumer (JERI) is arriving.
4. Write the actual `au.zeus.jdk.net.ssl` package (interface, enums, exceptions, factory), the
   `module-info.java` export, and tests — **all human-written**, per `CLAUDE.md`.
5. Cross-reference with `ADVICE-quic-tls-dirtychai-locations-2026-07-05.md` item C ("Supported
   facade: `SSLContext → QUIC engine` factory only; export the package") — this document is the
   detailed design for that item.

---

**Policy compliance:** This document is 100% analysis/advice, produced by reading DirtyChai source
read-only. No DirtyChai file was created, edited, or deleted. The interface sketch in §4 is example
code for human review and iteration — it is explicitly *not* a proposed commit to DirtyChai, and it
lives only in this JGDMS-side advisory document.
