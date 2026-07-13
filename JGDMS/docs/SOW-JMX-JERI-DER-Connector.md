# SOW: JMX Remote API Connector over JERI + Atomic DER

**Status:** design / scoping, captured 2026-07-10. Not yet implemented. JGDMS-side,
committable as a design doc. No code in this SOW.

## 0. Thesis

The JMX Remote API (JSR 160) ships one mandatory connector — `javax.management.remote.rmi`
— built on `java.rmi`/JRMP: unauthenticated-by-default object identity, codebase-URL class
loading, and Java serialization of arbitrary `Serializable` attribute values. JGDMS already
owns a alternative RPC stack purpose-built to avoid exactly those properties: **JERI**
(`net.jini.jeri`) for the transport/session/constraint layer, and **Atomic DER**
(`jgdms-der`, JGDMS-STD-006/008) for deterministic, digest-friendly, gadget-chain-resistant
marshalling on top of `@AtomicSerial`. A JMX connector built on that stack lets an operator
point VisualVM/JConsole/any JSR 160 client at a JVM's `MBeanServer` over a channel that
carries JERI's per-connection identity model (digest for code identity, SPIFFE for process
identity, JWT for user identity) and DER's hardened wire format, instead of RMI/JRMP.

This is a **scoping document only**. It records what is real in the codebase today (the SPI
shape, the marshal-substitution mechanism, the discovery-metadata extension point, the
existing "JMX is special" recognition in JERI's own invocation-handler code), what the
proposed connector's boundaries are, and the open questions that must be closed before a
build phase starts.

## 1. Why this is a sanctioned extension point, not a hack

JSR 160 defines JMX connectors as pluggable from day one:
`javax.management.remote.JMXConnectorProvider` (client) and
`javax.management.remote.JMXConnectorServerProvider` (server) are SPIs, discovered via
`java.util.ServiceLoader` or the `jmx.remote.protocol.provider.pkgs` system/environment
property, keyed by the protocol string embedded in a `JMXServiceURL`
(`service:jmx:<protocol>://...`). RMI is the JDK's only *bundled* connector, but it was never
the only *specified* one: **JMXMP** — a from-scratch, non-RMI JMX Remote API connector — was
shipped for years as a separate jar (`opendmk`/`jmxremote_optional`), proving the SPI supports
a wire protocol with no RMI underneath. A JERI+DER connector is the same shape of extension:
implement the two provider SPIs, register them, done — no core JMX code changes.

## 2. Scope

Build a `JMXConnectorProvider` + `JMXConnectorServerProvider` pair, keyed to a new protocol
string (proposed: `jeri`), where:

- **Transport** is a JERI `Endpoint`/`ServerEndpoint` pair (any existing JERI transport —
  `net.jini.jeri.tcp.TcpEndpoint`, `net.jini.jeri.ssl.SslEndpoint`, and in future the UDS/QUIC
  transports under design — see `SOW-Unix-Domain-Socket-JERI-Transport.md` and
  `SOW-QUIC-JERI-Transport.md`). The connector does not invent a new byte transport; it rides
  the existing `Endpoint` SPI, the same way every JERI-exported service does.
- **Wire encoding** is Atomic DER (`jgdms-der`), reached via `AtomicDerILFactory`
  (`net.jini.jeri.AtomicDerILFactory`) on a `BasicJeriExporter`, exactly as any other
  `@AtomicSerial` JERI service is exported today (`BasicJeriExporter exporter = new
  BasicJeriExporter(TcpServerEndpoint.getInstance(port), new AtomicDerILFactory(...))`).
- **MBean surface targeted: Open MBeans only** (`javax.management.openmbean`) — MBeans whose
  attribute/parameter/return values are constrained to the `OpenType` universe: the
  `SimpleType` primitives (wrapper types, `String`, `BigDecimal`, `BigInteger`, `Date`,
  `ObjectName`, `Void`), plus `CompositeData`/`CompositeDataSupport`,
  `TabularData`/`TabularDataSupport`, and `ArrayType` (arrays of the above, including
  multi-dimensional). This is a **small, closed, self-describing type universe** — a
  bounded, one-time set of DER substitutions to write, not an open-ended "marshal anything a
  managed bean author might return" problem.
- Server-side, a thin remote object (e.g. `RemoteMBeanServerConnection`, name TBD) that
  forwards `MBeanServerConnection` method calls to the local `MBeanServer`, exported over
  JERI/DER; client-side, a `JMXConnector` implementation whose `MBeanServerConnection` is a
  JERI/DER proxy to that remote object. This mirrors the RMI connector's own shape
  (`RMIConnection` wrapping `MBeanServerConnection`), just re-hosted on JERI/DER instead of
  `java.rmi.Remote`/JRMP.

## 3. Out of scope

- **Legacy (non-Open) MBeans** whose attributes are arbitrary `Serializable` Java objects —
  the standard-MBean/dynamic-MBean case where `MBeanAttributeInfo.getType()` names an
  arbitrary class, not an `OpenType`. Supporting these would require either (a) falling back
  to generic Java-serialization marshalling for attribute payloads — reintroducing the exact
  gadget-chain surface this connector exists to avoid — or (b) a full JGDMS-marshal-delegate
  story for *arbitrary, unbounded* third-party types, which is an open-ended commitment this
  SOW deliberately declines. **Recommendation:** the connector requires Open MBean compliance
  to be exposed over it; a non-Open MBean either is not visible through this connector, or the
  connector refuses the connection/registration outright. This should be a stated, enforced
  contract (fail closed), not silent data loss.
- **JMXMP or RMI/IIOP interop** — no bridging to the existing connectors; JERI/DER is an
  additional protocol string, not a replacement shipped inside `javax.management.remote.rmi`.
- **Client/server building of a general-purpose "any Serializable over DER" bridge.** If a
  future increment wants non-Open MBean support, that is a separate, explicitly-scoped SOW.
- **Authentication/authorization policy design.** JERI's constraint-based auth (identity via
  digest/SPIFFE/JWT, per `net.jini.core.constraint`) is the transport's existing model; this
  SOW does not design new JMX-specific authorization semantics (e.g. mapping JMX's own
  `JMXAuthenticator`/subject-delegation concepts onto JERI identity) beyond noting it as an
  open question (§6).

## 4. Marshalling work: the fixed JMX meta-type set

Regardless of Open-MBean scoping, `MBeanServerConnection`'s own method signatures carry a
small, fixed, JDK-owned "meta-type" surface that must round-trip over DER. None of these
types are `@AtomicSerial` (they are `javax.management.*`, not JGDMS-owned), so **the
applicable mechanism is `@Serializer(replaceObType=...)` + `DerReplacer`
(`au.net.zeus.jgdms.der.serial.DerReplacer`, `jgdms-der`), not `MarshalDelegate`.**

This distinction matters and corrects an easy mix-up: `org.apache.river.api.io.MarshalDelegate`
(`jgdms-platform`) is an **access broker for JGDMS's own `@AtomicSerial` classes** — it lets the
marshalling engines invoke a *package-private* JGDMS class's `serialForm`/`serialize`/`(GetArg)`
ctor without `setAccessible`, when the class lives in a JGDMS-owned package the engine doesn't
share. It has nothing to do with third-party types outside JGDMS's control. The mechanism that
*does* fit third-party JDK types is the existing `@Serializer(replaceObType=X)` pattern
(`org.apache.river.api.io.Serializer`, an annotation) already used for `java.net.URL`
(`URLSerializer`), `java.util.Date` (`DateSerializer`), `Throwable`, `Map`, `Set`, `List`, and
others in `jgdms-platform/.../org/apache/river/api/io/`: a small, itself-`@AtomicSerial`
"serializer" class with a `(X)` constructor and a `readResolve()` (`Resolve` interface) that
DER substitutes on encode and resolves back on decode, dispatched by
`DerReplacer.replace`/`resolve` and enumerated via classpath resource
`META-INF/jgdms/der-serializers`. **A JMX connector module contributes one `@Serializer`
class per JDK JMX type it needs to carry, plus a `META-INF/jgdms/der-serializers` listing.**
(A `MarshalDelegate` would only enter the picture if the connector's own internal
`@AtomicSerial` glue classes end up package-private — an ordinary consequence of normal
JGDMS coding style, not something JMX-specific.)

The fixed set needing a `@Serializer` (or hand confirmation that an existing one already
covers it — `Date` is already done):

- `javax.management.ObjectName` — the omnipresent MBean identity type; appears in almost
  every method signature and inside `CompositeData`/`TabularData` values too.
- The `MBeanInfo` family: `MBeanInfo`, `MBeanAttributeInfo`, `MBeanOperationInfo`,
  `MBeanConstructorInfo`, `MBeanNotificationInfo`, `MBeanFeatureInfo` (common supertype),
  `MBeanParameterInfo`, and `Descriptor`/`DescriptorSupport` (the open-ended `Object[]`
  key/value bag hanging off most of the above — the one place genuine open-endedness leaks
  into the "fixed" set; see §6).
- `Attribute`, `AttributeList` (attribute name/value pairs — `AttributeList` is a
  `java.util.ArrayList<Attribute>` subclass, so also touches the existing `ListSerializer`
  path).
- `ObjectInstance` (ObjectName + class name pair, returned by `queryMBeans`).
- `QueryExp` and the query-building `Query`/`QueryEval` machinery, if query pushdown is
  supported (`queryNames`/`queryMBeans`) — otherwise this can be deferred/rejected (§6).
- The `Notification` family: `Notification`, `AttributeChangeNotification`,
  `MBeanServerNotification` — needed if/when notification delivery is in scope (§6).

This set is small and has been stable across JDK versions for a long time (JMX's own spec
discipline), so it is a one-time cost, not an ongoing tax — the same "bounded, well-known type
universe" property claimed for the Open MBean `SimpleType`/`CompositeData`/`TabularData`
universe in §5.

## 5. Marshalling work: the Open MBean value universe

The `OpenType` universe that Open MBean attribute/operation values are constrained to also
needs `@Serializer` coverage (again via `DerReplacer`, not `MarshalDelegate` — none of these
are JGDMS-owned):

- `SimpleType` primitives: most (`Integer`, `Long`, `Boolean`, `String`, `Character`, ...) are
  either already natively `@AtomicSerial`-adjacent or already have a `Serializer` in
  `org.apache.river.api.io` (`IntSerializer`, `BooleanSerializer`, `DateSerializer`, etc.). The
  ones that likely still need one: `BigDecimal`, `BigInteger`, and confirming `ObjectName`
  (shared with §4) and `Void` (trivial/marker).
- `CompositeData`/`CompositeDataSupport` — a self-describing `String -> Object` map keyed by
  a `CompositeType` (itself carrying an `OpenType` per key) — recursive over the same
  universe. One `@Serializer` for `CompositeDataSupport` (or `CompositeData` via
  `CompositeDataSupport` as the concrete carrier) plus one for `CompositeType` (a `OpenType`
  subtype).
- `TabularData`/`TabularDataSupport` — an indexed collection of `CompositeData` rows keyed by
  a `TabularType`. One `@Serializer` for `TabularDataSupport` + one for `TabularType`.
- `ArrayType` (and the raw arrays it describes) — arrays of any of the above, including
  multi-dimensional arrays; DER/JOSS already handle Java arrays structurally, so the work here
  is chiefly `ArrayType`/`OpenType` metadata itself, not the array values.
- `OpenMBeanInfo`/`OpenMBeanAttributeInfo`/`OpenMBeanOperationInfo`/etc. — the
  `Open*` sibling of the §4 `MBeanInfo` family, carrying `OpenType` instead of a bare class
  name; likely each needs its own `@Serializer` alongside the plain `MBeanInfo` family.

Net: on the order of ten to fifteen `@Serializer` classes total across §4 and §5, each a small,
mechanical wrapper in the existing `URLSerializer`/`DateSerializer` style (wire form = the
type's own public accessors, `readResolve()` rebuilds the JDK object) — a bounded, front-loaded
cost, not a per-MBean cost.

## 6. Open questions / risks

1. **Descriptor is not actually closed.** `javax.management.Descriptor` (`DescriptorSupport`)
   is a `String -> Object` bag with **no type constraint on the values** — an MBean author can
   put an arbitrary `Serializable` (or non-serializable) object in a descriptor field. This is
   a crack in the "bounded universe" claim of §4/§5. Options: (a) support only descriptor
   values that are themselves in the OpenType/JDK-boxed-primitive universe and drop/reject
   others with a logged warning; (b) require descriptor values to be `@AtomicSerial` or have a
   registered `@Serializer`, else fail closed on that specific field. Needs a decision before
   build; leaning (a) with fail-closed-on-unknown, consistent with the connector's general
   Open-MBean-only posture.

2. **Registration/naming mechanics.** Concretely nail down: the `JMXServiceURL` protocol
   string (`service:jmx:jeri://...` — proposed `jeri`, confirm it doesn't collide with an
   existing registered JMX Remote API protocol name), how the JERI endpoint (host/port,
   transport choice, constraints) is encoded into/out of a `JMXServiceURL`'s
   host/port/URLPath, and whether discovery is via `META-INF/services/javax.management.remote.
   JMXConnectorProvider` + `.JMXConnectorServerProvider` (`ServiceLoader`, the JSR 160-native
   path) or `jmx.remote.protocol.provider.pkgs` (package-naming-convention path,
   `<pkgs>.jeri.ClientProvider`/`ServerProvider`) — or both, for compatibility with tools that
   pre-date `ServiceLoader`-based discovery. Recommendation: `ServiceLoader` registration as
   primary (matches how JGDMS already discovers `MarshalDelegate`/JERI providers elsewhere),
   `provider.pkgs` support only if a concrete client tool needs it.

3. **How JERI's session/connection model maps onto JMX's generic connector abstraction.**
   JSR 160's `JMXConnector` is a coarse-grained "one connection, one `MBeanServerConnection`,
   plus a notification listener registration/dispatch side-channel" abstraction
   (`addConnectionNotificationListener`, connection-closed events, `connectionId`). JERI's
   `Endpoint`/`ObjectEndpoint`/`InvocationLayerFactory` model is finer-grained (per-call
   constraint negotiation, no built-in notion of a long-lived "connector session" beyond
   whatever the transport's connection-reuse does). Need to design: what `JMXConnector.
   connect()`/`close()` map to (export/unexport of the remote MBeanServerConnection object?
   an explicit session handshake?), and how `connectionId` (opaque, connector-defined per
   JSR 160) is derived from JERI's identity (SPIFFE/digest/JWT principal + `Uuid`).

4. **Notification delivery.** Standard JMX notification delivery (`NotificationListener`
   registered against a *local* proxy, delivered by a background fetch thread polling
   `fetchNotifications` in the RMI connector, or push in JMXMP) needs a JERI-shaped
   equivalent. Two shapes to weigh: (a) **polling**, mirroring the RMI connector's
   `RMIConnection.fetchNotifications` — simple request/response, no new JERI capability
   needed, but adds latency and a background poll thread per client; (b) **push**, exporting a
   client-side callback object over JERI (JERI supports bidirectional export — a client can
   export an object and hand the server its proxy, same as Jini remote-event listeners) so the
   server calls back directly on notification — lower latency, but needs the client to accept
   inbound JERI connections (firewall/NAT implications) or reuse the existing connection's
   reverse channel if the chosen transport supports it. Recommend starting with (a) — it has
   no open transport question and matches the RMI baseline — and treating (b) as a follow-on
   once the base connector is green, noting it's a natural fit for JERI's existing
   listener-export pattern used elsewhere in JGDMS (Jini remote events).

5. **`JMXAuthenticator` / subject delegation vs. JERI identity.** JSR 160 has its own
   pluggable `JMXAuthenticator` and a `Subject`-delegation story (`JMXConnectorServer`
   environment map keys). JERI already produces an authenticated identity per connection
   (constraint-negotiated `ClientSubject`/SPIFFE/JWT principal). Decide whether the connector
   (a) ignores `JMXAuthenticator` entirely and relies solely on JERI's own identity + JGDMS
   policy/permission checks at the `MBeanServer` boundary (consistent with "constraint-based
   auth replaces the weaker model," per this SOW's motivation), or (b) bridges the JERI
   identity into a `Subject` and still runs it through `JMXAuthenticator`/MBeanServer access
   checks for drop-in compatibility with existing JMX authorization tooling. Leaning (a) for a
   first increment, with (b) explicitly flagged as a compatibility nice-to-have, not a
   correctness requirement.

6. **`Descriptor`/attribute value validation at the trust boundary.** Because Open MBean
   values ultimately reach application code (an MBean's `getAttribute`/`invoke`), the
   connector inherits the general `DeSerializationPermission`/POLP posture JGDMS already
   applies to `@AtomicSerial` payloads (per `jgdms-joss-atomicmarshal-security` /
   `jgdms-atomicserial-phase0-halfmigrated` memory) — confirm the `@Serializer` classes for
   §4/§5 are gated the same way (no special-case bypass just because the caller is "a JMX
   attribute").

7. **Module placement and naming.** No module exists yet for this connector. Candidates,
   following the existing `jgdms-jeri`/`jgdms-lib-dl`/`services/*` naming conventions: a new
   top-level module (e.g. `jgdms-jmx-jeri`) depending on `jgdms-jeri` + `jgdms-der` +
   `jgdms-platform`, with the `@Serializer` classes and the `JMXConnectorProvider`/
   `JMXConnectorServerProvider` implementations together, or split into an API/client module
   and a `-dl` companion if the remote MBeanServerConnection proxy needs codebase-style
   distribution (unlikely, since JERI/DER connectors are typically same-jar client/server,
   unlike Jini lookup-discovered services). Decide at build time; not load-bearing for the
   design.

## 7. The JMXProtocolType extension point (trivial, do early)

`net.jini.lookup.entry.jmx.JMXProtocolType` (`jgdms-lib-dl/src/main/java/net/jini/lookup/
entry/jmx/JMXProtocolType.java`) is discovery *metadata only* — a `@SerialEntry` Jini
lookup-service `Entry` advertising which JMX protocol(s) a service's connector endpoint
supports, alongside `JMXProperty` (the endpoint's `JMXServiceURL` string). It already
enumerates `RMI = "rmi"`, `IIOP = "iiop"`, `JMXMP = "jmxmp"` as `public static final String`
constants. This is a one-line, low-risk addition once the connector's protocol string is
fixed (§6.2):

```java
public static final String JERI = "jeri";
```

This lets a JGDMS service advertise a JERI-based JMX endpoint through ordinary Jini lookup
discovery (a client browsing the lookup service sees the `JMXProtocolType`/`JMXProperty`
attributes and knows to use the JERI connector). This constant can be added independently of
and ahead of the connector build — it costs nothing and unblocks nothing else, but signals the
intended endpoint shape to any future consumer of the lookup entry.

## 8. Incremental build plan

**Phase 0 — trivial, independent.**
- Add `JMXProtocolType.JERI = "jeri"` (§7). No dependencies on anything else in this SOW.

**Phase 1 — meta-type marshalling foundation.**
- New module (name TBD, §6.7) with `@Serializer(replaceObType=...)` classes for the §4 fixed
  JMX meta-type set (`ObjectName`, `MBeanInfo` family, `Attribute`/`AttributeList`,
  `ObjectInstance`), each following the `URLSerializer`/`DateSerializer` pattern (wire form +
  `readResolve`), registered via `META-INF/jgdms/der-serializers`.
- Unit tests: round-trip every meta-type through the DER codec directly (no JERI/JMX
  involved yet) — encode/decode equality, consistent with how `jgdms-der`'s existing unit
  suites test individual `@Serializer` types.
- Resolve open question §6.1 (`Descriptor`) here — it blocks `MBeanInfo`/`MBeanAttributeInfo`
  round-tripping.

**Phase 2 — Open MBean value marshalling.**
- `@Serializer` classes for `CompositeDataSupport`/`CompositeType`,
  `TabularDataSupport`/`TabularType`, `ArrayType`, remaining `SimpleType` gaps
  (`BigDecimal`/`BigInteger`), and the `Open*Info` family (§5).
- Round-trip unit tests, including nested/recursive `CompositeData`-of-`CompositeData` and
  `TabularData` cases.

**Phase 3 — the connector skeleton (no notifications yet).**
- Server-side remote MBeanServerConnection-forwarding object, exported via
  `BasicJeriExporter` + `AtomicDerILFactory` over an existing transport (start with
  `TcpEndpoint`/`SslEndpoint` — no new transport work).
- `JMXConnectorServerProvider`/`JMXConnectorProvider` implementations wiring a `JMXServiceURL`
  to that exported endpoint, resolving §6.2 (registration/naming) and §6.3 (session mapping).
- Enforce the Open-MBean-only, fail-closed contract (§3) at the server forwarding boundary:
  reject/hide non-Open MBeans per the §6.1 decision.
- End-to-end smoke test: connect with a hand-rolled `JMXConnector` client (or point VisualVM/
  JConsole at it once the provider is `ServiceLoader`-discoverable) against a JVM exposing a
  handful of Open MBeans; exercise `getAttribute`/`setAttribute`/`invoke`/`queryNames`.

**Phase 4 — notifications.**
- Implement the polling shape first (§6.4a); add a JERI-push variant behind the same
  `NotificationListener` API once polling is green, if the latency/thread cost proves worth
  removing.

**Phase 5 — hardening / policy integration.**
- Resolve §6.5 (`JMXAuthenticator` posture) and §6.6 (POLP/`DeSerializationPermission` parity
  for the new `@Serializer` classes).
- QA-style integration test analogous to JGDMS's existing service matching tests, run against
  the DirtyChai JDK per the repo's standard build/verify convention.

**Out of scope for all phases above:** non-Open MBean support (§3); RMI/JMXMP interop/bridging
(§3); any change to core `javax.management.remote` or `jgdms-jeri`/`jgdms-der` beyond adding
`@Serializer` classes and one new consuming module.

## References

- JSR 160 (JMX Remote API) — `javax.management.remote.{JMXConnectorProvider,
  JMXConnectorServerProvider,JMXServiceURL,JMXConnector,JMXConnectorServer}`;
  `javax.management.remote.rmi` (the bundled baseline to diverge from);
  `jmx.remote.protocol.provider.pkgs` system property; historical JMXMP connector
  (proof the SPI supports a non-RMI protocol).
- `javax.management.openmbean.*` — `OpenType`, `SimpleType`, `CompositeData`/
  `CompositeDataSupport`, `CompositeType`, `TabularData`/`TabularDataSupport`, `TabularType`,
  `ArrayType`, `Open*Info` family — the targeted value universe (§5).
- `net.jini.jeri.{AtomicDerILFactory,BasicJeriExporter,BasicInvocationHandler}`,
  `net.jini.jeri.tcp.TcpEndpoint`/`net.jini.jeri.ssl.SslEndpoint` (`jgdms-jeri`) — the
  transport + DER invocation-layer wiring this connector reuses as-is.
  `BasicInvocationHandler`/`ActivatableInvocationHandler`/`org.apache.river.jeri.internal.
  runtime.Util` already special-case `javax.management.MBeanServerConnection` (identity-method
  forwarding semantics), general RPC plumbing already aware of the type — not connector logic.
- `au.net.zeus.jgdms.der.serial.DerReplacer`, `org.apache.river.api.io.Serializer` (annotation),
  `org.apache.river.api.io.{URLSerializer,DateSerializer,ThrowableSerializer,...}`
  (`jgdms-platform`/`jgdms-der`) — the substitution mechanism for third-party (JDK-owned)
  types this SOW's §4/§5 marshalling work builds on. **Not** `MarshalDelegate` (§4 correction).
- `org.apache.river.api.io.{MarshalDelegate,MarshalDelegates}`
  (`jgdms-platform/src/main/java/org/apache/river/api/io/`),
  `org.apache.river.tool.delegate.MarshalDelegateProcessor`
  (`jgdms-marshal-delegate-processor`) — the JGDMS-own-class access-broker mechanism; relevant
  only if this connector's own internal `@AtomicSerial` glue ends up package-private, not for
  marshalling JDK JMX types. See `SOW-AtomicSerial-Delegate-Marshalling.md`.
- `net.jini.lookup.entry.jmx.{JMXProperty,JMXProtocolType}`
  (`jgdms-lib-dl/src/main/java/net/jini/lookup/entry/jmx/`) — existing discovery-metadata
  extension point (§7).
- `SOW-Unix-Domain-Socket-JERI-Transport.md`, `SOW-QUIC-JERI-Transport.md` — future JERI
  transports this connector rides for free once they land (no connector-side change).
- `SOW-AtomicSerial-Delegate-Marshalling.md` — the delegate/discovery mechanics referenced and
  corrected against in §4.
