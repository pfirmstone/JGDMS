# SOW — Pluggable Discovery-Transport Layer (scoping / design)

**Status:** **DRAFT — for Peter's review.** AI-authored design scoping note; **no
production code written or changed.** 2026-07-11.
**Module scope:** `jgdms-platform` (`net.jini.discovery`, `org.apache.river.discovery`),
`jgdms-discovery-providers`, `jgdms-lib` / `jgdms-lib-dl` (`net.jini.lookup`),
`jgdms-jeri` (SPIFFE credential provider), `services/reggie` (announce/answer side).
**Depends on / relates to:** the `dl-lookup` Java-8 facade split (trunk `b6c8655c3`,
2026-07-02); DiscoveryV2 wire format (DiscoveryV1 removed in 4.0.0); STD-003
(Multi-Subject Identity), the SPIFFE HaLOW-mesh federation direction, and the
"space-as-trust-boundary" (Outrigger/Blitz) direction. Method mirrors STD-010's
"spec the seam before you build the transport" posture.

> **Requirements language.** MUST / SHOULD / MAY per RFC 2119/8174. Every claim about
> *current* code is cited `File.java:line`; everything else is marked **[PROPOSED]**,
> **[OPEN]**, or **[EXISTS TODAY]**. "Fail closed" = on any ambiguity, discover nothing
> and verify nothing rather than fall back to a permissive default.

---

## 0. Thesis (TL;DR)

Jini/JGDMS discovery bakes **IP multicast** into the mechanism that *finds* peers.
On a LAN that is fine; on an 802.11ah (HaLOW) multi-hop radio mesh it fails three
ways (§2). The fix is **not** a new discovery protocol — it is a **seam**. The code
already cleanly separates three concerns, and only the first is hard-wired to
multicast:

- **(a) peer/lookup DISCOVERY** — "find a `ServiceRegistrar` and learn its
  `LookupLocator`." **Hard-wired to multicast** in `AbstractLookupDiscovery`
  (client) and `RegistrarImpl` (server).
- **(b) registrar-proxy TRANSPORT/exchange** — a **unicast TCP handshake** over a
  plain `Socket` that transfers the registrar proxy. **Already a pluggable SPI**
  (`DiscoveryFormatProvider` via `DiscoveryV2` + `Service.providers`), and it is
  transport-neutral: it needs only a connected `Socket` + a `LookupLocator`.
- **(c) TRUST verification** — outbound identity (`DiscoveryCredentialProvider`
  Subject), codebase-integrity-gated unmarshalling, and `ProxyPreparer` proxy-trust.
  **Already independent of how the peer was found.**

Because (b) and (c) already operate against an abstract `LookupLocator` + `Socket`,
the entire missing abstraction is a **peer-source SPI for concern (a)** — something
that *yields `LookupLocator` endpoints* (plus advertised groups and an optional
attestation hint) by whatever transport. Multicast becomes **one provider**;
gossip/beacon and space-substrate discovery become **others**, reusing the existing
unicast exchange + proxy-trust (except the space arm, which skips the exchange — see the
board revision below, which also corrects the seam altitude and the trust default).
`LookupLocatorDiscovery`
is already 90 % of this shape — it is "discovery = the locator was handed to me out
of band," minus the pluggable source *and* minus a way to accept a caller-supplied
socket instead of dialing `host:port` (see the board revision below).

> **REVISED (board 2026-07-11):** the seam described above is drawn **one layer too
> high** and the shared trust path is **fail-open by default** — and these are the
> *same* problem. See **§0.5** for the summary and the inline **REVISED:** markers in
> §3, §4, §6, §7, §8. The corrected seam cuts at `Discovery.doUnicastDiscovery(Socket)`
> with a **discriminated-union** peer output, which is also the place to verify a
> peer's SVID **before** deserialising its proxy bytes.

---

## 0.5 Board-review revisions (2026-07-11)

Two critics reviewed this draft adversarially; both findings were verified against the
code and **accepted by Peter**. This section summarises what moved; inline **REVISED:**
markers show the exact edits. The thesis (pluggable discovery transport, multicast as
one provider) **stands** — but the seam altitude and the trust posture are corrected,
and they turn out to be a single issue.

**F1 — the SPI seam was one layer too high.** The draft had a transport yield a
`LookupLocator`. But `LookupLocator` is a validated `scheme://host:port` URL — scheme ∈
{`jini`,`https`}, host + port validated in the ctor and re-validated in `readObject`;
`equals`/`hashCode` over host/port/scheme (`LookupLocator.java`). It **cannot** carry a
proxy, a route, an SVID, or a rendezvous handle. And a `LookupLocator` is consumed by
the **IP connector** `MultiIPDiscovery.getResponse(scheme,host,port,ic)`
(`MultiIPDiscovery.java:40`) → `InetAddress.getAllByName(host)` (`:45`) → `new Socket()`
+ `connect(new InetSocketAddress(host,port))`. So "yield a `LookupLocator`" **forces
every transport to produce a routable IP host** — which a gossip/multi-hop/NAT'd/
sleeping mesh peer does not have, and a space/install-on-take peer does not use (there
is no socket at all). The genuinely transport-clean seam is **one level deeper**:
`Discovery.doUnicastDiscovery(Socket, …)` (`Discovery.java:422`), whose impl
(`DiscoveryV2.java:410-509`) touches only `socket.getInputStream()/getOutputStream()` —
no host, no `InetAddress`. **Correction:** `DiscoveredPeer` yields a **discriminated
union** — (1) **DIALABLE** `LookupLocator` (LAN/multicast, today's connect is fine);
(2) **CONNECTED** a transport-supplied *connected* `Socket`/`Endpoint` + expected peer
identity, fed straight to `doUnicastDiscovery(Socket,…)`, **bypassing** `MultiIPDiscovery`
(gossip/multi-hop/NAT); (3) **DELIVERED** an already-unmarshalled `ServiceRegistrar`
proxy (space/install-on-take), where the exchange (b) is **skipped** and only trust (c)
runs. Also corrected: `DiscoveryManagement`/`GroupManagement`/`LocatorManagement` bake
in a two-axis groups+locators worldview (`DiscoveryGroupManagement.java:23-33`;
`LookupDiscoveryManager.java:101-104` hard-builds one engine per axis at `:163/:171/:238`),
so a third transport needs its **own** per-transport management/config contract — the
draft's "LDM just enumerates providers" understated the public-API churn.

**F2 — the shared trust path is fail-open by default and deserialises-before-trust
(more severe than the draft implied).** The default `registrarPreparer` is
`new BasicProxyPreparer()` (`AbstractLookupLocatorDiscovery.java:1387`,
`AbstractLookupDiscovery.java:1429`); the no-arg ctor is
`this(false,false,null,…,false)` (`BasicProxyPreparer.java:151-153`) → **`verify=false`**,
and `verify()` calls `Security.verifyObjectTrust` **only if `verify==true`** (`:346-376`).
So out of the box a discovered `ServiceRegistrar` proxy is returned to SDM with **zero
proxy-trust / SVID check**. Worse, the proxy graph is **fully deserialised (codebase
possibly resolved) inside the exchange, and trust is applied only after**:
`getResponse(...)` (`AbstractLookupLocatorDiscovery.java:315`) *then*
`prepareProxy(...)` (`:318-319`). The legacy `net.jini.discovery.plaintext` provider
reads the response over the **unfiltered JOSS** path
(`plaintext/Client.java:113-118` → `Plaintext.readUnicastResponse(...)`, integrity
`false`); only ssl/https use the hardened `AtomicMarshalInputStream` overload
(`Plaintext.java:597-604`). `ConstrainableRegistrarProxy` checks constraint-consistency
(`:117-143`) — a *constraint* check, **not** identity/proxy-trust. So the draft's
"trust (c) is preserved on every transport / a mesh transport cannot weaken this" was
**misleading**: the default (c) does ~nothing, and every transport drives
deserialise-first.

**The key connection (new insight).** F2 is *guaranteed* by F1: a bare-`LookupLocator`
SPI **forces** the deserialise-first exchange with **nowhere to gate attestation**.
Cutting the seam at `doUnicastDiscovery(Socket)` with the discriminated union
(DELIVERED-proxy + pre-attested CONNECTED-socket variants) creates the natural place to
**verify the peer's SVID before deserialising its bytes**. **The seam altitude *is* the
security control point** — getting it right is a security requirement, not an
architecture nicety. Under "provenance-as-authority," deserialise-before-trust means the
proxy's *asserted* provenance is consumed before its provider is authenticated → a
spoofed-provenance peer is not structurally prevented today; on open radio (an
attacker-influenced peer set) this is the crown-jewel exposure.

**Reclassification.** The five §7 "open questions" become **MUST-level mesh-profile
requirements** in the proposed **JGDMS-STD-011** (§7, revised). Fail-closed by default,
atomic-only, mandatory pre-exchange attestation.

**Increment plan.** Inc2 (NIC-pin hardening) **unchanged** — both critics agree it is
safe and independent. Inc1 (extract seam) **revised**: seam at
`doUnicastDiscovery(Socket)`, `DiscoveredPeer` discriminated union, **and the
pre-exchange attestation hook is first-class in the SPI**. Inc3–5 carry the five MUSTs.

---

## 1. Current-state map (ground truth, `File.java:line`-cited)

### 1.1 Concern (a): peer discovery — where multicast is wired in

**Client side — `net.jini.discovery.AbstractLookupDiscovery`** (package-private base;
public concrete subclass `LookupDiscovery.java:639-641`
`extends AbstractLookupDiscovery implements DiscoveryManagement, DiscoveryGroupManagement`):

- Multicast is instantiated **directly**, no abstraction: the receive side opens
  `sock = new MulticastSocket(Constants.discoveryPort)`
  (`AbstractLookupDiscovery.java:443`) and `sock.joinGroup(Constants.getAnnouncementAddress())`
  (`:452, :479, :505, :552, :565`); the send side opens a second `MulticastSocket`
  (`:724`), sets TTL (`:725-727`), and `mcSocket.send(packet[i])` via `sendPacketByNIC`
  (`:1983-2085`, send at `:2102`). The socket **type**, the **group addresses** (via
  `Constants`), and the fixed **`discoveryPort`** are literals in these inner classes.
- **Interface selection defaults to *every* NIC.** Modes at `:272-275`
  (`NICS_USE_ALL=0`, `NICS_USE_SYS=1`, `NICS_USE_LIST=2`, `NICS_USE_NONE=3`); the
  config entry `multicastInterfaces` is read at `:1495-1498`, and **when the entry is
  absent** the code enumerates `NetworkInterface.getNetworkInterfaces()` and sets
  `nicsToUse = NICS_USE_ALL` (`:1516-1527`). So the out-of-box default multicasts on
  loopback + flaky WiFi + good bridge indistinguishably.
- **Blind fixed-interval retry of dead NICs.** `multicastInterfaceRetryInterval`
  defaults to `5*60*1000` = **300 000 ms** (`:1531-1536`); `retryBadNics()` re-joins on
  a wall-clock timer (`:540-572`, `:581-596`) with no health signal.
- **Finite request burst, then silence.** The `Requestor` thread (`:710-809`) loops
  `for (count = multicastRequestMax; --count >= 0 ...)` sending a request, then
  `Thread.sleep(count>0 ? multicastRequestInterval : finalMulticastRequestInterval)`
  (`:772-791`) — **7** requests (`multicastRequestMax`, default `:1462-1467`) spaced
  **5 s** (`multicastRequestInterval`, `:1468-1473`), a final at **2 min**
  (`finalMulticastRequestInterval`, `:1474-1479`) — **and then the thread exits and
  closes the socket** (`:797-806`). After the burst the client is passive: it depends
  entirely on the lookup service's periodic **announcements** to stay discovered.
  Miss those and you are deaf (the observed "0 events / 9 min" stall).
- **Announcement liveness tuned to LAN.** `multicastAnnouncementInterval` default
  `2*60*1000` (`:1538-1543`); `AnnouncementTimerThread` (`:827-867`) discards a lookup
  after `N_INTERVALS=3` missed announcements (`:829, :836, :846-863`) — i.e. a 6-minute
  blind eviction, fatal when HaLOW drops unacked multicast hop-to-hop.

**Addresses/ports — `net.jini.discovery.Constants`:** `discoveryPort = 4160` (`:72`);
request group `FF05::156` / IPv4 `224.0.1.85` (`getRequestAddress`, `:87-99`);
announce group `FF05::155` (or `FF0X::155` global) / IPv4 `224.0.1.84`
(`getAnnouncementAddress`, `:110-126`). `FF05::` is **site-local** scope — one L2
segment; it does not cross mesh hops without multicast routing the radios will not do.

**Server side — `org.apache.river.reggie.RegistrarImpl`** (same multicast baked in):
`import java.net.MulticastSocket` (`:33`); a multicast-request listener thread joins
the request group (`socket = new MulticastSocket(Constants.discoveryPort)` `:2848`,
`joinGroup(requestAddr)` `:2857, :2872, :2974`); the `Announce` runnable (`:3110`)
opens a `MulticastSocket` (`:3132`) and periodically sends `MulticastAnnouncement`
packets every `multicastAnnouncementInterval` (`await(...)` `:3158`, build at `:3192`,
send `:3235-3260`). Same `multicastInterfaces` NIC config (`:411, :2849, :3235`).

**The static-locator alternative already exists.** `AbstractLookupLocatorDiscovery`
(`:83-84` `implements DiscoveryManagement, DiscoveryGroupManagement,
DiscoveryLocatorManagement`) does **no multicast at all** — no `MulticastSocket`
anywhere in the file. Given a `LookupLocator` it connects **directly** to
`host:port` (`doUnicastDiscovery` `:274-315`, via `MultiIPDiscovery.getResponse(scheme,
host, port, ic)` `:315`) and runs the unicast exchange. This is the transport-agnostic
core the mesh needs — it just has no pluggable *source* of locators (they arrive only
through the `DiscoveryLocatorManagement` API, `:132-134`).

### 1.2 Concern (b): registrar-proxy transport/exchange — already an SPI

The unicast exchange that actually moves the registrar proxy is factored behind a
**format-provider SPI** keyed by an 8-byte format ID:

- Facade: `org.apache.river.discovery.Discovery` (`:75` abstract; only v2, `:78`).
  `getProtocol2(ClassLoader)` (`:97-99`) → `DiscoveryV2.getInstance`. The wire ops are
  `encode/decodeMulticastRequest`, `encode/decodeMulticastAnnouncement`, and — the
  proxy exchange — `doUnicastDiscovery(Socket, constraints, defaultLoader,
  verifierLoader, context)` (`:422-428`, client) and `handleUnicastDiscovery(...)`
  (`:458-464`, server). **These operate on a plain `Socket`** — the exchange is
  independent of how the peer/socket was found.
- Provider loading: `DiscoveryV2` (`:65`) buckets providers into six role slots
  (`:75-93`) discovered via **`Service.providers(DiscoveryFormatProvider.class, ldr)`**
  (`DiscoveryV2.java:619-620`), keyed by `computeFormatID` = first 8 bytes of
  `SHA-1(getFormatName())` (`:641-673`). Base SPI `DiscoveryFormatProvider` (`:37`,
  sole method `getFormatName()` `:44`); six subinterfaces incl. `UnicastDiscoveryClient`
  (`:35`, `doUnicastDiscovery` `:90`) / `UnicastDiscoveryServer`.
- Concrete providers in `jgdms-discovery-providers` (plaintext, ssl+sha2\*, x500\*,
  kerberos, https), registered in
  `.../resources/META-INF/services/org.apache.river.discovery.DiscoveryFormatProvider`
  (21 client/server pairs). E.g. `plaintext/Client.java` implements
  `MulticastRequestEncoder, MulticastAnnouncementDecoder, UnicastDiscoveryClient`
  (`:52-56`), `getFormatName()` = `"net.jini.discovery.plaintext"` (`:65-67`).
- The proxy is carried in `UnicastResponse` (`:30`, `getRegistrar()` `:108`), read in
  `Plaintext.readUnicastResponse` — `mi.get(defaultLoader, verifyCodebaseIntegrity,
  verifierLoader, context)` (`Plaintext.java:555-559`; atomic variant `:597-606`).
  Server answers via `getDiscovery(pv).handleUnicastDiscovery(new UnicastResponse(host,
  port, groups, registrarProxy), ...)` (`RegistrarImpl.java:5033-5034`).
- **SPI registry bridge — `org.apache.river.resource.Service`** (`:158`): under OSGi it
  unions the classpath `META-INF/services` scan with the **OSGi service registry**
  (`providers(...)` `:414-434`, `ChainedIterator` `:350-373`, `OSGiServiceIterator` +
  `Service.setOsgi()` `:134-135`); outside OSGi it is a plain `LazyIterator` classpath
  scan. **This is the registration mechanism any new transport provider MUST reuse.**

### 1.3 Concern (c): trust verification — already source-independent

- **Outbound identity:** `DiscoveryCredentialProvider` (`:31`, `Subject getSubject()`
  `:39`); default `NoOpDiscoveryCredentialProvider.INSTANCE` returns `null` (`:40-41`);
  SPIFFE impl `net.jini.jeri.ssl.SpiffeDiscoveryCredentialProvider` returns the ambient
  `WorkerSubject` (`:29-34`). Consumed in `AbstractLookupDiscovery`: field `:282`,
  configured `:1419-1423`, used to run the request encode under the Subject
  (`getDiscoverySubject()` `:2935`, `runWithDiscoverySubject` at `:780-781`).
- **Inbound proxy trust:** codebase-integrity flag gates unmarshalling
  (`EndpointBasedClient.java:122, :156-157`; `Plaintext.java:555-559`), then
  `registrarPreparer.prepareProxy(resp.getRegistrar())`
  (`AbstractLookupLocatorDiscovery.java:318-319`; default `BasicProxyPreparer`
  `:1383-1387`). Proxy-trust is supplied by `ConstrainableRegistrarProxy`
  (`reggie-dl/.../proxy/ConstrainableRegistrarProxy.java:46-47`,
  `implements RemoteMethodControl`, `ProxyTrustIterator`). Locator/constraint objects
  are vouched by `ConstrainableLookupLocatorTrustVerifier` (`:41, :60-63`) and
  `DiscoveryConstraintTrustVerifier` (`:41, :63-72`).

**Key property:** none of (c) depends on multicast. It runs on the `LookupLocator` and
the `Socket` — exactly the inputs a replacement transport would supply.

### 1.4 How ServiceDiscoveryManager sits on top (and the `-dl` facade split)

- **SDM consumes only `DiscoveryManagement`.** The SPI's `getDiscoveryManager()`
  returns `DiscoveryManagement` (`ServiceDiscoveryManagerSpi.java:83`); SDM never sees
  multicast. `DiscoveryManagement` (`:32`: `addDiscoveryListener`, `getRegistrars`,
  `discard`, `terminate`) is implemented by `AbstractLookupDiscoveryManager` (`:47-49`),
  which **aggregates** `LookupDiscovery` (multicast, field `:99`) + `LookupLocatorDiscovery`
  (unicast, field `:109`), stored via constructor (`:798-808`).
- **The hard-wire point in the aggregator:** `LookupDiscoveryManager`
  (`:101 extends AbstractLookupDiscoveryManager`) constructs the engines with concrete
  `new`: `new LookupDiscovery(groups, ...)` (`:163, :238`) and
  `new LookupLocatorDiscovery(locators, ...)` (`:171, :238`). **This is the single call
  site where a transport-agnostic manager would instead enumerate providers.**
- **The recent `-dl` facade/SPI split** (trunk 2026-07-02, `3bc17fddf` split +
  `b6c8655c3` merge; makes `*-dl.jar` Java-8-reachable): `ServiceDiscoveryManager`
  (`jgdms-lib-dl/.../ServiceDiscoveryManager.java:88`) is a **concrete facade** holding
  `ServiceDiscoveryManagerSpi impl` (`:130`); it resolves a `DiscoveryProviderFactory`
  once via `Service.providers(DiscoveryProviderFactory.class, ...)` (`:107-109`,
  fail-closed `:124`) and delegates construction (`:162-166, :207-213`). The impl
  `DiscoveryProviderFactoryImpl` (`jgdms-lib/.../DiscoveryProviderFactoryImpl.java:43`)
  builds `ServiceDiscoveryManagerImpl` (`:667`), registered in
  `META-INF/services/net.jini.lookup.DiscoveryProviderFactory`. **There is NO `-dl`
  facade for `LookupDiscoveryManager` yet** — it remains a single `jgdms-platform`
  class. This split is the *precedent pattern* the transport SPI should follow.

---

## 2. Requirements

### 2.1 LAN (preserve)
- **R-L1** Preserve current multicast discovery behavior byte-for-byte where multicast
  is present (DiscoveryV2 wire, `FF05::` groups, port 4160, timing defaults). Existing
  qa discovery suites MUST stay green with no config change.
- **R-L2** Preserve the `-dl` facade/SPI split and the Java-8 reach of `*-dl.jar`; the
  transport SPI must not drag impl dependencies into `jgdms-lib-dl`.
- **R-L3** Preserve `DiscoveryManagement` / `DiscoveryGroupManagement` /
  `DiscoveryLocatorManagement` and the `ServiceDiscoveryManager` public API — additive
  only.
- **R-L4** Reuse the `org.apache.river.resource.Service` registry (OSGi + ServiceLoader)
  for provider registration; no parallel discovery mechanism.

### 2.2 Mesh (enable)
- **R-M1 Lossy/intermittent tolerant.** No reliance on a finite request burst + a
  passive-listen-forever model. Discovery MUST recover a peer after arbitrary loss
  without an operator poke (contrast §1.1 burst-then-exit).
- **R-M2 Multi-hop.** MUST NOT assume one L2 segment or `FF05::` reachability. Peer
  reachability information must be able to propagate across hops (gossip) or via a
  rendezvous (space).
- **R-M3 Deterministic interface binding.** A mesh node MUST be able to **pin** the
  radio interface explicitly; auto-enumerate-all is a fallback, not the default
  (contrast §1.1 `NICS_USE_ALL`). Health-aware, not fixed-5-min-blind, retry.
- **R-M4 Constrained.** Low duty cycle; small packets; adjustable/adaptive intervals;
  tolerate nodes that sleep and move.
- **R-M5 Leaderless / offline.** No dependency on a live central lookup for a node to
  discover peers; a peer set can form and re-form without a coordinator.
- **R-M6 SPIFFE-federated trust.** Each transport MUST carry or bootstrap the two-gate
  trust handshake (identity via SVID + proxy-trust), never bypass it (R-carries-trust).
- **R-M7 Sybil/DoS resistance** on open radio: admission by SPIFFE trust-domain;
  signed advertisements; cheap pre-screen before the (expensive) unicast exchange.

### 2.3 Cross-cutting
- **R-X1** The DISCOVERY concern (a) MUST be separable from the EXCHANGE (b) and TRUST
  (c) concerns, so any transport reuses the existing (b)+(c) code path.
- **R-X2** Virtual-thread-safe, no `ThreadLocal` (per platform norms); the executor
  default is already `newVirtualThreadPerTaskExecutor()` (`AbstractLookupDiscovery.java:1454-1459`).

---

## 3. Proposed abstraction — the pluggable discovery-transport SPI **[PROPOSED]**

### 3.1 The seam
Introduce **one new SPI at concern (a): a peer-source.** It emits/withdraws candidate
lookup endpoints; it does **not** do the proxy exchange or trust (those stay in the
existing (b)+(c) path). The aggregator (`LookupDiscoveryManager`) enumerates providers
via `Service.providers` and, for each surfaced peer, runs today's
`MultiIPDiscovery`/`doUnicastDiscovery` + `registrarPreparer.prepareProxy`.

```
                 ServiceDiscoveryManager (unchanged; sees only DiscoveryManagement)
                                    │
                 LookupDiscoveryManager  ── enumerates ──►  Service.providers(
                   (transport-agnostic aggregator)              DiscoveryTransportProvider.class )
                                    │                                  │
              peerDiscovered(LookupLocator, groups, attestation?)      ├─ MulticastBeaconProvider   [wraps today's AbstractLookupDiscovery]
                                    │                                  ├─ StaticLocatorProvider     [today's LookupLocatorDiscovery seed]
   ┌────────────────────────────────┴───────────────┐                 ├─ GossipBeaconProvider       [PROPOSED]
   (b) EXISTING unicast exchange over a Socket        │                └─ SpaceRegistryProvider      [PROPOSED]
       Discovery.doUnicastDiscovery(...) → UnicastResponse.getRegistrar()
   (c) EXISTING trust: codebase-integrity + registrarPreparer.prepareProxy(...)
```

> **REVISED (F1):** the aggregator does **not** always "run `MultiIPDiscovery` +
> `prepareProxy`." It **switches on the peer variant** (§3.2): DIALABLE → today's
> `MultiIPDiscovery` connect then `doUnicastDiscovery(Socket)`; CONNECTED → skip
> `MultiIPDiscovery`, feed the caller's connected socket straight to
> `doUnicastDiscovery(Socket)`; DELIVERED → skip the exchange entirely, run trust only.
> In **all** variants a mandatory **pre-exchange attestation gate** runs first
> (`attestation()` is a MUST on mesh transports, not a MAY — §4, §7).

### 3.2 Interface sketch (DESIGN — not committed code)

> **REVISED (F1/F2):** `DiscoveredPeer.locator()` is replaced by a **discriminated
> union** so a transport can surface a peer it cannot express as a routable
> `scheme://host:port`, and so the SVID can be checked *before* any bytes are
> deserialised. The union's CONNECTED/DELIVERED arms are the seam-at-`doUnicastDiscovery`
> and the security control point the board identified.

```java
package net.jini.discovery;   // PROPOSED

/**
 * A source of discovered lookup peers over some transport. Concern (a) ONLY.
 * The registrar-proxy exchange (b) and proxy-trust verification (c) are performed
 * by the DiscoveryManagement aggregator against the LookupLocator this yields —
 * NOT by the provider. Keyed and loaded exactly like DiscoveryFormatProvider,
 * via org.apache.river.resource.Service.providers(...).
 */
public interface DiscoveryTransportProvider {

    /** Stable name; SHA-1-hashed to a transport id, as DiscoveryFormatProvider is. */
    String getTransportName();                 // e.g. "net.jini.discovery.multicast"

    /**
     * Begin discovering peers advertising membership in any of the given groups
     * (ALL_GROUPS == null). {@code credentials} supplies the outbound Subject
     * (concern c bootstrap); {@code config} supplies transport-specific tuning
     * (interface pin, intervals, gossip fanout, space template, ...).
     */
    DiscoverySession start(String[] groups,
                           DiscoveryConstraints constraints,
                           DiscoveryCredentialProvider credentials,
                           net.jini.config.Configuration config) throws java.io.IOException;
}

public interface DiscoverySession {
    void setGroups(String[] groups) throws java.io.IOException;   // desired-group change
    void addPeerListener(DiscoveredPeerListener l);
    /** Peer proved unreachable in the exchange: re-discover it (mesh: don't blind-evict).
     *  REVISED (F1): keyed on an opaque transport peer id, not a LookupLocator — a
     *  Connected/Delivered peer has no routable locator. */
    void discard(Object peerId);
    void terminate();
}

public interface DiscoveredPeerListener {
    void peerDiscovered(DiscoveredPeer p);   // candidate lookup peer appeared
    void peerChanged(DiscoveredPeer p);      // its groups / attestation changed
    void peerGone(Object peerId);            // REVISED (F1): opaque peer id (announced departure / lease expiry)
}

/**
 * What a transport surfaces. REVISED (F1): a DISCRIMINATED UNION over how the peer can
 * be reached, because a mesh peer often has no routable scheme://host:port and a space
 * peer uses no socket at all. The union arm decides which of exchange (b) / trust (c)
 * the aggregator runs. attestation() is REVISED (F2) from a MAY hint to a MUST gate on
 * mesh transports (verified BEFORE any DELIVERED proxy is trusted / any CONNECTED bytes
 * are read). groups() is the functional gate.
 */
public interface DiscoveredPeer {
    Object peerId();                   // stable transport-scoped id (for discard/peerGone)
    String[] groups();                 // advertised member groups (functional gate)
    Attestation attestation();         // REVISED: mandatory pre-exchange gate on mesh (null only on LAN)
    Reach reach();                     // the discriminated union below
}

/** REVISED (F1): the three ways a transport can hand a peer to the exchange/trust path. */
public sealed interface Reach permits Dialable, Connected, Delivered {}

/** (1) LAN / multicast: a validated scheme://host:port. Aggregator dials it via the
 *  EXISTING MultiIPDiscovery.getResponse(...) then Discovery.doUnicastDiscovery(Socket). */
public record Dialable(net.jini.core.discovery.LookupLocator locator) implements Reach {}

/** (2) gossip / multi-hop / NAT: the transport already opened the route. The aggregator
 *  BYPASSES MultiIPDiscovery and feeds this connected socket straight to
 *  Discovery.doUnicastDiscovery(Socket,…) (Discovery.java:422). expectedPeer pins the
 *  SVID identity the pre-exchange gate must have matched. */
public record Connected(java.net.Socket socket, Object expectedPeer) implements Reach {}

/** (3) space / install-on-take: the ServiceRegistrar proxy is ALREADY unmarshalled by
 *  the (SPIFFE-authenticated) space transport. Exchange (b) is SKIPPED; ONLY trust (c)
 *  prepareProxy(...) runs. */
public record Delivered(net.jini.core.lookup.ServiceRegistrar registrar) implements Reach {}

/** Additive: the SVID / capability claim the pre-exchange gate verifies (signature +
 *  freshness + anti-replay) BEFORE deserialising a Connected socket or trusting a
 *  Delivered proxy. REVISED (F2): mandatory on mesh transports. */
public interface Attestation { /* SVID, signature, nonce/counter, notAfter, claims... */ }
```

### 3.3 Why this is the right cut
> **REVISED (F1):** this subsection originally claimed the seam was "minimal — a
> transport need only produce a `LookupLocator`" and "the aggregator change is one
> file." Both are corrected below: the clean seam is `doUnicastDiscovery(Socket)`, not
> `LookupLocator`, and the aggregator needs a per-transport management/config contract,
> which is real (bounded) public-API work, not a one-line swap.

- **Minimal *at the right altitude*.** The transport-clean cut is
  `Discovery.doUnicastDiscovery(Socket,…)` (`Discovery.java:422`), whose impl touches
  only the socket's streams (`DiscoveryV2.java:410-509`). A transport produces a
  `Reach` (dialable locator, connected socket, or delivered proxy) + an `Attestation`;
  `LookupLocator` is only the LAN arm. Nothing about IP addressing is forced on gossip,
  multi-hop, NAT, or space peers.
- **Multicast is demoted, not deleted.** `MulticastBeaconProvider` wraps today's
  `AbstractLookupDiscovery` verbatim: its `AnnouncementListener`/`Requestor` already
  learn a peer's `LookupLocator` + groups from decoded `MulticastAnnouncement`s — it
  just calls `peerDiscovered(...)` instead of driving the exchange itself. Zero wire
  change; DiscoveryV2 untouched.
- **`LookupLocatorDiscovery` generalizes.** Its unicast+trust core becomes the shared
  back half; static config becomes `StaticLocatorProvider`. Gossip and space are new
  *fronts* on the same back half.
- **Registration is free.** Providers register in
  `META-INF/services/net.jini.discovery.DiscoveryTransportProvider` and are found by the
  same `Service.providers(...)` bridge that already works under OSGi (§1.2).
- **Aggregator change is bounded public-API work (not one file).** **REVISED (F1):**
  `LookupDiscoveryManager`'s `new LookupDiscovery(...)` / `new LookupLocatorDiscovery(...)`
  (`:163, :171, :238`) becomes an enumeration of transport providers — but
  `DiscoveryManagement`/`DiscoveryGroupManagement`/`DiscoveryLocatorManagement` hard-code a
  **two-axis groups(multicast)+locators(unicast) worldview**
  (`DiscoveryGroupManagement.java:23-33`; `LookupDiscoveryManager.java:101-104`). A third
  transport is neither a group set nor a locator set, so the SPI needs its **own
  per-transport management/config contract** (e.g. `DiscoveryTransportManagement`) that the
  aggregator composes alongside the two legacy axes. **What SDM sees is still only
  `DiscoveryManagement` — that stays unchanged** — but the manager's own construction API
  grows a third axis. This is additive and bounded, but it is real public-API churn, not a
  one-line swap.

### 3.4 Composition with DiscoveryV2 providers
The **format** providers (§1.2, keyed by `SHA-1(getFormatName)`) and the new
**transport** providers are **orthogonal**: transport chooses *how you learn a peer +
open a socket*; format chooses *how the bytes on that socket are framed and the proxy
unmarshalled*. `MulticastBeaconProvider` uses DiscoveryV2 for both request/announce and
unicast framing; `GossipBeaconProvider`/`SpaceRegistryProvider` still hand the resulting
`Socket` to `Discovery.doUnicastDiscovery` (DiscoveryV2 unicast format) for the proxy —
so ssl/x500/plaintext format selection and constraints keep working under any transport.

---

## 4. Trust integration (discovery-carries-or-bootstraps-trust)

> **REVISED (F2) — the crown-jewel correction.** The original §4 claimed "the trust
> gate is preserved on every transport / a mesh transport cannot weaken this." That is
> **false as the code ships**: the default `registrarPreparer` is
> `new BasicProxyPreparer()` with **`verify=false`** (`BasicProxyPreparer.java:151-153`;
> `verify()` no-ops unless `verify==true`, `:346-376`), so a discovered proxy reaches SDM
> with **no proxy-trust / SVID check at all**; and the proxy is **fully deserialised
> (codebase possibly resolved) inside the exchange, before trust runs** — `getResponse`
> (`AbstractLookupLocatorDiscovery.java:315`) *then* `prepareProxy` (`:318-319`). The
> legacy `plaintext` provider even reads it over **unfiltered JOSS**
> (`plaintext/Client.java:113-118`, integrity `false`). So the default (c) does ~nothing,
> and **every** transport currently drives deserialise-first. F2 is *forced* by F1's
> bare-`LookupLocator` seam (there is nowhere to gate the peer before the exchange).
> The corrected model below makes the SVID gate **precede** deserialisation and makes the
> secure posture **mandatory (fail-closed)** on mesh transports.

The two-gate model (**functional gate** = template/group match; **trust gate** = SVID
identity + proxy-trust) must be **enforced ahead of deserialisation** on every mesh
transport — it is *not* automatically preserved; today's default is fail-open:

- **Outbound identity per transport.** `DiscoverySession.start(..., credentials, ...)`
  receives the `DiscoveryCredentialProvider` Subject (§1.3). Multicast encodes its
  request under that Subject today (`runWithDiscoverySubject`, `AbstractLookupDiscovery.java:780-781`);
  a gossip provider **signs each advertisement** with the SVID; a space provider
  **registers the entry under** the SVID. Same Subject, three carriers.
- **Pre-exchange attestation gate — MANDATORY on mesh (REVISED from MAY to MUST).**
  Before the aggregator touches a `Connected` socket's stream or trusts a `Delivered`
  proxy, it MUST verify `DiscoveredPeer.attestation()`: SVID signature, freshness/
  not-after, and anti-replay (nonce/monotonic counter). This is the security control
  point the seam altitude buys us — **verify the peer before deserialising its bytes.**
  On LAN/multicast the gate is `null` and the legacy path is unchanged (opt-in hardening
  only); on mesh transports a missing/failed attestation ⇒ **fail closed**, peer dropped,
  no exchange. (R-M6/R-M7; STD-011 §7 items 2–4.)
- **Trust gate must be turned ON for mesh (it is OFF by default).** The mesh profile
  MUST configure `registrarPreparer` with **`verify=true` + `Integrity.YES`** and the
  **atomic-only** unicast format (retire the `plaintext`-JOSS
  `readUnicastResponse` path; ssl/https atomic overload `Plaintext.java:597-604` only),
  so that `Security.verifyObjectTrust` / the downstream `getProxyVerifier` proxy-trust
  actually runs and the bytes are filter-checked. `ConstrainableRegistrarProxy`
  constraint-consistency (`:117-143`) is necessary but **not** identity trust — do not
  conflate them. (STD-011 §7 item 1.)
- **`Delivered` (space) skips exchange (b) — CORRECTED.** For `SpaceRegistryProvider`
  the registrar proxy is **already unmarshalled** by the SPIFFE-authenticated space
  transport, so the DiscoveryV2 unicast exchange **does not run** ("(b) runs unchanged
  behind space" in the original was wrong). Trust (c) still runs: the space provider MUST
  authenticate the **entry writer** and reject poison **before** `peerDiscovered`, then
  the aggregator runs `prepareProxy` (verify=true) on the delivered proxy. register-in-
  space is the advertisement; **install-on-take** conveys proxy + capability claim; the
  space is the outer trust boundary but an *honest space faithfully serving
  attacker-written poison is a confused deputy* — hence writer-auth + reject-before-
  deliver is a MUST (STD-011 §7 item 5).

---

## 5. Backward compatibility

| Item | Disposition |
|---|---|
| DiscoveryV2 wire format (request/announce/unicast) | **Unchanged.** Multicast provider uses it verbatim; DiscoveryV1 stays removed. |
| `DiscoveryFormatProvider` SPI + 21 format providers | **Unchanged.** Orthogonal to transport (§3.4). |
| `DiscoveryManagement` / group / locator interfaces | **Unchanged.** Additive only (R-L3). |
| `ServiceDiscoveryManager` public API + `-dl` facade | **Unchanged.** SDM sees only `DiscoveryManagement`; the split (`ServiceDiscoveryManagerSpi`/`DiscoveryProviderFactory`) is untouched. |
| `LookupDiscovery` / `LookupLocatorDiscovery` public classes | **Retained.** Reimplemented internally as the multicast and static-locator providers; public constructors preserved. |
| `Service.providers` OSGi/ServiceLoader registry | **Reused** for the new `DiscoveryTransportProvider` service key. |
| `*-dl.jar` Java-8 reach | **Preserved.** Transport SPI interfaces live in `jgdms-platform` (or a `-dl` if a downloadable client must name them); impls stay in impl jars (R-L2). |

Everything mesh-related is **new registered providers + an additive third management
axis on the manager + the mesh security profile**; nothing existing is removed. New STD
for the gossip/space wire + mesh trust profile (§7).

> **REVISED (F1):** the `DiscoveryManagement / group / locator` row above stays
> "unchanged" **only for what SDM consumes** (`DiscoveryManagement`). The concrete
> `LookupDiscoveryManager` construction surface **grows a third, additive
> per-transport management/config axis** (§3.3) beside the legacy groups/locators axes,
> because a mesh transport is neither a group set nor a locator set. Additive, but real
> public-API work. Also: `LookupLocatorDiscovery` is retained *and* gains an internal
> ability to accept a **caller-supplied connected `Socket`** (the `Connected` arm),
> which it cannot do today (it only dials `host:port` via `MultiIPDiscovery`).

---

## 6. Increments (each independently valuable + testable)

- **Inc1 — Extract the transport SPI; multicast becomes a provider. No behavior
  change.** **REVISED (F1/F2):** define the SPI with the seam at
  `Discovery.doUnicastDiscovery(Socket,…)` and `DiscoveredPeer` as the **discriminated
  union** (`Dialable`/`Connected`/`Delivered` + `Attestation`) — *not* a bare
  `LookupLocator` — and make the **pre-exchange attestation hook a first-class part of
  the SPI** from day one (even though the LAN provider passes `null` and skips it), so
  the mesh security control point exists in the contract, not bolted on later. Wrap
  today's `AbstractLookupDiscovery` as `MulticastBeaconProvider` (always emits `Dialable`,
  `attestation()==null`) and the static locators as `StaticLocatorProvider`; add the
  third management axis (§3.3); change `LookupDiscoveryManager` to switch on the peer
  variant and enumerate providers via `Service.providers`. **Gate:** existing qa
  discovery suites green, unchanged config, LAN path byte-identical. *De-risks the seam
  and installs the security control point without yet touching the wire or default trust.*
- **Inc2 — Deterministic interface binding (LAN provider hardening).** Flip the
  `MulticastBeaconProvider` default from `NICS_USE_ALL` (`AbstractLookupDiscovery.java:1516-1527`)
  to **explicit-pin-or-system-default**; make the `multicastInterfaceRetryInterval`
  (`:1531-1536`) health-aware instead of blind-5-min. Independently shippable; directly
  fixes the observed flaky-NIC waste. *Needs no new transport.*
- **Inc3 — Gossip/beacon provider.** `GossipBeaconProvider`: epidemic anti-entropy of
  **SVID-signed** peer advertisements (locator + groups + attestation), TTL/lease,
  multi-hop tolerant, adaptive duty cycle; Sybil resistance by SPIFFE trust-domain
  admission + signature + rate limit. New optional wire (STD, §7). *Delivers R-M1/2/5/7.*
- **Inc4 — Space-substrate discovery provider.** `SpaceRegistryProvider` on the
  Outrigger/Blitz substrate: register-in-space advertisement + install-on-take +
  attested-capability match. *Delivers the "space-as-trust-boundary" direction.*
- **Inc5 — HaLOW-specific tuning.** 802.11ah-aware provider profile: unacked-multicast
  awareness, adaptive intervals for the radio MCS floor, sleep/move tolerance, optional
  L2 beacon. *Delivers R-M4 on the real link.*

> **REVISED (F2):** Inc3–5 are **normative-gated by JGDMS-STD-011** and ship
> **fail-closed by default**: `verify=true` + `Integrity.YES`, atomic-only unicast (no
> plaintext-JOSS), and **mandatory pre-exchange attestation** (SVID signature + freshness
> + anti-replay). The five items formerly listed as "open questions" (§7) are their
> normative MUSTs, not deferred questions.

---

## 7. Mesh-profile requirements (was "open questions") + residual risks

> **REVISED (F2):** the board reclassified the five former "open questions" 2–5 (and the
> revocation/bootstrap/poison sub-issues) as **MUST-level requirements** of a new
> standard, **JGDMS-STD-011: Pluggable Discovery-Transport & Mesh Advertisement** — the
> mesh transport ships **fail-closed by default**, not "secure if you configure it." They
> are stated as normative MUSTs below; genuine residual risks/open items follow in §7.2.

### 7.1 Normative MUSTs for the mesh profile (→ JGDMS-STD-011)

1. **Fail-closed trust gate (fixes F1/F2 fail-open + deserialise-first).** The mesh
   profile MUST configure `registrarPreparer` with **`verify=true` + `Integrity.YES`**
   and **atomic-only** unicast; it MUST **retire the plaintext-JOSS
   `readUnicastResponse` path** for mesh transports (DiscoveryV2/atomic only). Rationale:
   the default `new BasicProxyPreparer()` is `verify=false`
   (`BasicProxyPreparer.java:151-153`) and `plaintext/Client.java:113-118` reads
   unfiltered — unacceptable on radio.
2. **Mandatory pre-exchange attestation gate (was a "MAY" hint).** On mesh transports
   `DiscoveredPeer.attestation()` MUST be verified — SVID signature + freshness — **before**
   the exchange (before reading a `Connected` socket or trusting a `Delivered` proxy).
   A missing/failed attestation ⇒ drop the peer, no exchange. Fixes discovery-amplification
   and the deserialise-before-authenticate exposure.
3. **Advertisement wire anti-abuse.** The advertisement wire MUST specify **anti-replay**
   (nonce or monotonic counter + TTL) and **per-signer ad-rate + peer-cardinality caps**.
   Rationale: an SVID-signed ad without freshness is replayable, and one SVID can
   otherwise sign thousands of fake peer ads (Sybil amplification).
4. **Bounded revocation-freshness, fail-closed.** The profile MUST define a revocation-
   freshness bound and, when offline, **reject peers whose SVID freshness exceeds the
   bound** (fail closed). This turns former open-Q "revocation lag vs discovery freshness"
   into a policy with a safe default rather than an unbounded window.
5. **Space provider writer-auth + reject-poison-before-`peerDiscovered`.** The
   `SpaceRegistryProvider` MUST authenticate the **entry writer** and reject poison
   **before** surfacing a peer (an honest space faithfully serving attacker-written poison
   is a confused deputy). Seed/bootstrap locators MUST be **operator-pinned trust
   anchors**, **never gossip-seeded** without the attestation gate — closing the
   chicken-and-egg bootstrap without reintroducing an unauthenticated seed.

### 7.2 Residual risks / genuine open items

- **Multicast scope & routing on the mesh.** `FF05::` is site-local and will not cross
  hops (§1.1). Resolution (settled): the multicast provider stays **LAN/datacenter-only
  by design**; the mesh is served by Inc3/Inc4. Confirm no code path assumes multicast is
  always available (SDM does not — it sees only `DiscoveryManagement`).
- **LAN assumptions leaking through SDM.** SDM is transport-agnostic (consumes only
  `DiscoveryManagement`), **but** `LookupCache` staleness/discard timing and
  `LeaseRenewalManager` renew intervals are LAN-RTT-tuned. **[OPEN]:** audit for mesh
  latency/intermittency; likely needs mesh-profile timing defaults even though the seam
  itself is clean.
- **No `-dl` facade for `LookupDiscoveryManager` yet** (§1.4). If a downloadable client
  must construct a transport-agnostic manager, an LDM `-dl` facade may be needed — decide
  whether the transport SPI interfaces belong in `jgdms-platform` or a new `-dl`.
- **Third management axis surface** (§3.3). The exact shape of the per-transport
  management/config contract (`DiscoveryTransportManagement`) is **[OPEN]** — it must be
  additive to the manager without perturbing what SDM consumes.

---

## 8. Recommendation

> **REVISED (board 2026-07-11):** the thesis is sound and worth pursuing. The board
> caught two things and showed they are **the same problem**: a **wrong-altitude seam**
> (F1 — output a bare `LookupLocator`) and a **fail-open-by-default trust posture** (F2 —
> `verify=false`, deserialise-before-trust, plaintext-JOSS) that would be **catastrophic
> on radio**. The fix for F1 (cut at `doUnicastDiscovery(Socket)` with the discriminated
> union) **is** the fix for F2: it creates the place to authenticate a peer's SVID before
> deserialising its bytes. **Right seam = the security control point.**

- **Get the seam altitude right in Inc1 — it is a security requirement, not polish.**
  Define `DiscoveredPeer` as the discriminated union (`Dialable`/`Connected`/`Delivered`
  + `Attestation`) cut at `Discovery.doUnicastDiscovery(Socket,…)`, with the pre-exchange
  attestation hook first-class from day one. Reimplementing multicast behind it stays
  **zero-behavior-change** on LAN (multicast emits `Dialable`, `attestation()==null`),
  gated by the existing green qa suites — but the mesh control point now exists in the
  contract instead of being retrofitted.
- **Pull Inc2 forward as an immediate independent win — UNCHANGED.** Both critics agree
  the `NICS_USE_ALL` default flip + health-aware retry is safe and independent; it needs
  no SPI and directly fixes a **live** failure mode from the qa session. Land it now,
  ahead of or beside Inc1.
- **Timing vs 4.0.0 — UNCHANGED.** Land Inc2 now. Sequence **Inc1 after the current 4.0.0
  deserialization-uncoupling / DER-wire work settles**: the exchange path touches
  `@AtomicSerial`-heavy, churning code (`MarshalledInstance` in `Plaintext.java:552-559`,
  the atomic variant `:597-606`), so extracting the seam onto a moving trunk raises
  avoidable merge risk — and Inc1 now *depends* on that atomic path being the mesh default
  (STD-011 §7.1 item 1), a further reason to let the wire work settle first.
- **Mesh work is STD-gated and fail-closed.** Inc3–Inc5 land only behind **JGDMS-STD-011**
  with the five §7.1 MUSTs (verify=true + Integrity.YES, atomic-only, mandatory
  attestation, anti-replay + caps, bounded revocation, space writer-auth) — fail-closed by
  default, never "secure only if configured."

---

## 9. Execution plan — task breakdown (agent type + effort)

Work-breakdown for executing §6's increments as delegated agent tasks. Effort follows
the project convention (security → `xhigh`, class/wire-resolution → `max`, mechanical →
`medium`, test-debug → `xhigh`); orchestration follows the norm *fresh implement → review
gate*, with a **parallel review board** for high-blast-radius/security work and a **single
reviewer** for contained changes. All models = opus unless noted.

### Summary

| Task | Deliverable | Phase | Implement (agent · effort) | Review | Depends |
|------|-------------|-------|----------------------------|--------|---------|
| **T1** · Inc2 NIC-pin | Flip `AbstractLookupDiscovery` `NICS_USE_ALL` default → explicit-pin / system-default + health-aware retry (no SPI) | **A — now** | general-purpose · **HIGH** | single reviewer + qa multicast run | none |
| **T6** · LDM `-dl` facade | Give `LookupDiscoveryManager` the facade→`Service.providers`→SPI split already used for SDM (`b6c8655c3`) | **A — now** | general-purpose · **sonnet** · **MEDIUM** | single reviewer + `check-api-compat.sh` | none |
| **T0** · JGDMS-STD-011 | Author the standard: SPI contract, `DiscoveredPeer`/`Reach` union, §7.1 five mesh MUSTs, advertisement wire format | **A — now (draft)** | general-purpose/Plan · **HIGH** (design/spec) | **security review board** (2–3 adversarial) | must land before T3/T4 |
| **T2** · Extract transport SPI | `DiscoveryTransportProvider` SPI + `sealed Reach {Dialable\|Connected\|Delivered}` + mandatory `Attestation`, seam at `Discovery.doUnicastDiscovery(Socket)`; multicast → `MulticastBeaconProvider`; per-transport mgmt axis. **Zero behavior change.** *(may split T2a contract / T2b refactor)* | **B — after 4.0.0** | general-purpose · **XHIGH** (API + wire + trust seam + compat) | **parallel board**: API-compat · wire-form · security | 4.0.0 settled, T0, T6 |
| **T3** · `GossipBeaconProvider` | SVID-signed ads, anti-replay + per-signer rate/cardinality caps, multi-hop, mandatory pre-exchange attestation gate, bounded fail-closed revocation | **C — mesh** | general-purpose · **XHIGH** (security-critical) | **adversarial security board** (Sybil/replay/amplification) | T2, T0 |
| **T4** · `SpaceRegistryProvider` | Register-in-space + install-on-take + attested-capability match + entry-writer auth + reject-poison-before-`peerDiscovered`, on Outrigger | **C — mesh** | general-purpose · **XHIGH** (security + integration) | **security board** (poisoning/confused-deputy) + integration tests | T2, T0, attested-capability work |
| **T5** · HaLOW field tuning | Unacked-multicast-aware + adaptive intervals + sleep/move tolerance | **C — field** | **HIGH; human-led + agent support** (needs real radios) | field validation, not a code board | T3/T4 + hardware |

### Sequencing

- **Phase A (now, parallel):** **T1** (independent win — the fix for the flaky-interface
  problem this SOW originated from), **T6** (low-risk enabler), **T0** (draft the spec).
- **Phase B (when 4.0.0 deserialization/DER settles):** **T2** — the keystone; everything
  downstream needs the seam. Guarded hardest (parallel board), because its blast radius is
  the whole all-proxy discovery path *plus* the trust seam.
- **Phase C (post-B, normative-gated by STD-011):** **T3 ∥ T4** (independent providers,
  each behind a security board), then **T5** on hardware.

### Notes

- **T2 is the one to guard hardest** — high blast radius + the security control point;
  clearest case for a parallel board rather than a single reviewer.
- **T5 cannot be closed by an agent** — it needs the physical HaLOW mesh; scope it as
  human-led field work with agent support, not a delegable implementation task.
- **T0 can and should start now** even though T3/T4 wait for 4.0.0 — the normative trust
  contract is the long pole and gates the mesh providers.

---

*End of DRAFT. No production code was written or modified in producing this document.*
