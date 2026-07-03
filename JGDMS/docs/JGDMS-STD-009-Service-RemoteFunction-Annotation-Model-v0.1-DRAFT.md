# JGDMS-STD-009 — Service & Remote-Function Annotation Model

**Version:** 0.1-DRAFT
**Status:** Draft (design converged in discussion 2026-07-03; **not yet built**)
**Applies to:** JGDMS 4.0.0+, DirtyChai (JDK fork), and — for the filter sidecar — non-JVM JGDMS participants
**Depends on:** JGDMS-STD-001 (@AtomicSerial), JGDMS-STD-003 (Multi-Subject Identity), JGDMS-STD-006 (DER Wire Format — hard, see §3.2)
**Related:** JGDMS-STD-007 (Codebase Compatibility Interchange), JGDMS-STD-008 (AtomicSerial Uncoupling), `SOW-service-proxy-annotation-processor.md` (implementation companion), `SOW-Unix-Domain-Socket-JERI-Transport.md`, `DESIGN-AttestedCapabilityMatching.md`, `DESIGN-CorroborationFramework.md`
**Author:** Peter Firmstone + Claude

---

> **Editorial note (v0.1-DRAFT).** This standard captures a design that converged
> during a 2026-07-03 working session. It is **normative in intent but not yet
> ratified or implemented**. Sections marked **[PROPOSED]** are settled in design
> but unbuilt; **[OPEN]** marks a question still to resolve. The annotation
> *processor* that realises §3–§7 is specified separately in
> `SOW-service-proxy-annotation-processor.md`; this document defines the **model
> and contracts** that processor (and the runtime, and the BAE) must honour.
> Key terms use RFC 2119 keywords (MUST / SHOULD / MAY).
>
> **Revised 2026-07-03 (post-review):** `@RemoteFunction` uses its own
> `{ DYNAMIC, FUNCTION }` enum (filter = `FUNCTION`, not a smart proxy); `codebase`
> dropped from `@RemoteFunction` (kept on `@JiniService`); §7 reframed on the
> `DYNAMIC`/`FUNCTION` axis; §10 split into BAE-enforced vs design/runtime; §13
> de-staled.
>
> **Validated 2026-07-03 (implementation review):** the `DYNAMIC` listener model was
> checked against the real `RemoteEventListener` implementations (`LookupCacheImpl`'s
> `LookupListener`, `PolicyUpdateListener`, and the Reggie / Mercury registration and
> delivery paths). The structural claims — constrainable `java.lang.reflect.Proxy`,
> passed *inward* as a parameter, single-method interface, no codebase, `@AtomicSerial`
> wire — were **confirmed**. The identity claim was **corrected** (§5, §7, §9.3): an
> asynchronous callback does **not** propagate a live caller Subject (a
> store-and-forward mailbox delivers under its own service identity). Two mechanisms
> the model leans on remain **design-ahead-of-code** — interface stripping (§6.4) and
> RULE-D1's refuse-non-DER gate (§10) — each flagged with an implementation-status note
> at its definition.

---

## 1. Purpose and Scope

This standard defines the annotation model by which a JGDMS developer declares
**what crosses a trust boundary** and **how**. It unifies three previously ad-hoc
concerns — service proxy generation, remote callbacks, and server-side predicate
pushdown — under one small, fail-closed taxonomy.

The governing observation is that there are exactly **two things you can place
across a trust boundary**:

- a **Service** — a *stateful* endpoint you *talk to*: discoverable, long-lived,
  many methods, a member of the djinn; and
- a **Function** — a *stateless* invocable you *apply*: ephemeral, single-purpose,
  no lookup identity.

These map to two annotations — `@JiniService` and `@RemoteFunction` — which sit on
one clean axis (stateful endpoint vs stateless invocable) rather than the category
mismatch of "service vs object" that the earlier working name `@RemoteObject`
implied. Everything else that appears to "cross" is neither: it is an ordinary
**parameter** of a shared API type (§9.1).

Out of scope: the wire encoding itself (STD-006), the AtomicSerial contract
(STD-001), and the identity/Subject machinery (STD-003). This standard *composes*
them.

---

## 2. Definitions

| Term | Meaning |
|---|---|
| **Service** | A `@JiniService`: a djinn citizen — registered in a lookup service, discoverable, with a `ServiceID`, join state, and attributes. Always Configuration-driven. |
| **Function** | A `@RemoteFunction`: a stateless invocable that crosses a boundary as a parameter. Not registered, not discovered. |
| **Listener** | The `DYNAMIC` form of a `@RemoteFunction`: an exported callback whose code runs in the *client's* JVM; the server calls back to it. Canonical example: `RemoteEventListener`. |
| **Filter** | The `FUNCTION` form of a `@RemoteFunction`: mobile code that ships to and runs *at the server* (predicate pushdown), evaluating a logical comparison over server-held candidate data. |
| **ProxyType** | The `@JiniService` enum `{ DYNAMIC, SMART }` in `jgdms-service-annotations` (`--release 8`). Selects whether the service proxy is a dynamic proxy (invoked remotely) or a smart proxy (downloaded behaviour run locally). `@RemoteFunction` has its **own** enum `{ DYNAMIC, FUNCTION }` — its non-dynamic form (`FUNCTION`) is standalone mobile code (a filter, §8), **not** a smart proxy. |
| **Codebase** | A downloadable class source (a `*-dl.jar`), identified by an httpmd URL embedding the jar digest. Required only when the receiver lacks a class it must resolve. |
| **Workload / user Subject** | STD-003: the ambient, mTLS-stamped SPIFFE *workload* identity of a connection, vs the *user* `Subject` on whose behalf a call is made. |
| **Sidecar** | A separate, minimally-privileged process that hosts a downloaded filter, reachable only over a Unix domain socket. |
| **Interface stripping** | Reconstructing an arriving dynamic proxy from only the interfaces the endpoint can resolve, dropping the rest (§6.4). Sound because an interface the endpoint cannot resolve is one it cannot use. |
| **Boomerang** | A dynamic-proxy function forwarded beyond its first recipient and reconstituted with its **full** interface set downstream; preserved by relaying the DER `MarshalledInstance` (no JDK change, no codebase) — see §6.4. |

---

## 3. The Annotation Family

Three annotations, in `jgdms-service-annotations` (compiled `--release 8` for
widest client reach):

### 3.1 `@JiniService` — the djinn citizen

A service is a discoverable, stateful djinn endpoint — lookup registration,
`JoinManager`, `ServiceID`, attributes. It **shares the constrainable dynamic-proxy
*generation*** with a `@RemoteFunction` (the processor emits proxies for both), but a
`@JiniService` is **not** a `@RemoteFunction`: they are opposite poles of the
Service-vs-Function axis (§1), realised by **separate, unrelated** base classes (§12).
A `@JiniService` **MUST** be realised `extends AbstractJiniService`
(`services/jgdms-service-support/.../AbstractJiniService.java`).

**Placement — on the implementation, not the interface.** `@JiniService`
**MUST** annotate the concrete service *implementation* class (the
`AbstractJiniService` subclass), **not** the API interface. Its attributes —
`proxy`, `codebase`, `component`, `protocol` — are *implementation and deployment*
choices: two implementations of one interface may legitimately pick `DYNAMIC` vs
`SMART`, different codebases, or different config components. The API interface
therefore stays a pure `Remote` contract carrying **no** annotation. The service
(remote) API interface(s) are named by the **`api()`** element (or **inferred**
from the impl's implemented interfaces minus the JGDMS infrastructure set when
`api()` is empty). The processor **MUST** emit a compile **error** if `@JiniService`
is found on an interface. The annotation is `@Retention(RUNTIME)`: besides driving
the processor at compile time, `AbstractJiniService.getServiceInterfaces()`
reflects it at runtime to resolve the service API (§4).

Keys:

```java
@JiniService(
    api       = FooService.class,    // service (remote) API interface(s); empty = infer
    proxy     = ProxyType.DYNAMIC,   // DYNAMIC (default) | SMART
    codebase  = false,               // ship a downloadable -dl jar?  default false
    protocol  = Void.class,          // internal wire interface; Void = same as api()
    component = "")                  // config component for the generated wrapper
```

The earlier `generate = { BACKEND, PROXY, WRAPPER }` selector is **RETIRED**: the
generated shape is derived from `proxy × codebase` (§6), not enumerated by hand.

There is **no** `bundledInterfaces` element. It would have populated an
interface-resolution codebase for evolution (bundling newly-added interfaces so old
clients avoid CNFE), but §6.4 eliminates that job: an old client **strips** interfaces
it cannot use (no CNFE, no download), and the DER schema preserves the full interface
identity for forwarding. When `codebase = true`, the `-dl` jar simply carries the
service's *declared* interface(s); the developer never enumerates them.

### 3.2 `@RemoteFunction` — the stateless invocable

The wire-crossing **primitive**: client-authored, flows *inward* as a
parameter/argument, no lookup or join. It does **not** extend `AbstractJiniService`.

- Its target **SHOULD** be a *functional interface* (a single abstract method): a
  function, not a bag of methods.
- Its **sole** selector is its own enum `{ DYNAMIC, FUNCTION }`. It has **no**
  `codebase` element: the codebase (and its separate-stream / workload-identity
  consequences) is *implied* by `FUNCTION` and absent for `DYNAMIC` (§7), so a
  standalone `codebase` flag would carry no independent information on a
  `@RemoteFunction`.
- It **MUST** be transmitted over a **DER stream** (STD-006); a non-DER endpoint
  **MUST** refuse it (**RULE-D1**, §10). This applies to the *whole* object stream
  carrying the `@RemoteFunction`: even a `DYNAMIC` listener riding the caller's
  existing stream requires that stream to be DER — not only the codebase hop of a
  `FUNCTION`. Every distinctive `@RemoteFunction`
  capability — boomerang identity-preservation (§6.4), the filter's gadget-free
  `@Stateless` marshalling and DER field-projections (§8) — depends on DER, so DER is
  a **hard requirement**, not a degradation axis. Without a Java-serialization path to
  fall back to, `@RemoteFunction` has no "unsafe legacy" mode.
- It is restricted (§5) to two forms until a third use case is revealed.
- It is renamed from the earlier `@RemoteObject`; "Object" became a misnomer once
  the pure-value (`DATA`) case was found to be an ordinary parameter (§9.1), not a
  member of this annotation at all.

### 3.3 `@SmartProxy` — the delegate marker

Marks the developer's client-side logic delegate: the class that implements the
public API and holds `server` typed as the internal interface plus any client
state. A generated constrainable shell (§6, shape 3) wraps it. Unchanged from
`SOW-service-proxy-annotation-processor.md`.

---

## 4. `@JiniService` Contract

A `@JiniService` **MUST always** be Configuration-driven (`net.jini.config`):
because it joins the djinn, its discovery groups, exporter, and attributes are
configured **regardless of codebase**. This is why it *always* extends
`AbstractJiniService` (§12), whereas a function's base class is conditional (§7).

The three interface roles from the SOW **MUST** remain distinct: public API
interface (`-api`, named by `api()`), internal service interface (wire methods; may
differ from the API — cf. Reggie's `Registrar` vs `ServiceRegistrar`; named by
`protocol()`), and the generated constrainable wrapping (`-dl`). Conflating them is
what produced the original four `AbstractSmartProxy` round-trip flaws.

**Runtime resolution in `AbstractJiniService`.** Because the annotation is now
`RUNTIME`-retained and impl-side (§3.1), two former template methods are
**concrete** on the base class:

- `getServiceInterfaces()` is **generated behaviour, not hand-written**: it reflects
  `@JiniService` off the concrete class (walking up the superclass chain, since the
  annotation is not `@Inherited`), returns `api()` when non-empty, and otherwise
  **infers** the service API from the class's implemented interfaces **minus** the
  infrastructure set (`Administrable`, `JoinAdmin`, `DestroyAdmin`, the bootstrap
  accessors `ServiceProxyAccessor` / `ServiceIDAccessor` /
  `ServiceAttributesAccessor` / `CodebaseAccessor`, `RemoteMethodControl`, and the
  bare `Remote` marker). The result is resolved **once** and cached (no per-call
  recomputation, no `ThreadLocal` — this codebase targets virtual threads). A class
  with **no** `@JiniService` fails fast at construction. The inference rule is
  **symmetric** with the processor's (§6, §3.1).
- `createProxy(stub, uuid)` has a **default body** `return stub;` — correct for a
  `DYNAMIC` service (shapes 1 & 2), whose exported JERI stub *is* the client proxy. A
  `SMART` service **overrides** it to return its generated/hand-written smart proxy;
  the method stays overridable (not `final`).

---

## 5. `@RemoteFunction` Forms (restricted)

**[PROPOSED]** `@RemoteFunction` **MUST** support exactly two forms until a further
use case is revealed. The processor **MUST** reject any other shape **fail-closed**
(compile error). A future form is an *additive, non-breaking* widening — a new enum
constant earned by a real consumer, never a speculative open slot.

| Form | `ProxyType` | Runs in | Identity | Codebase (derived) | Risk |
|---|---|---|---|---|---|
| **Listener** (callback) | `DYNAMIC` | the client's JVM | the proxy's own export-time constraints — registration runs under the caller's Subject, but an **async callback does not propagate a live Subject** (§7) | **none (derived)** — unresolvable ifaces are stripped locally (§6.4); boomerang needs no codebase (the relayed DER form preserves the full interface set) | low |
| **Filter** (predicate) | `FUNCTION` | the server-side sidecar | SPIFFE *workload* only | **implied (code ships)** | high → sandbox |

The two are **mirror images by where they execute**: the listener runs
client-side (the server reaches *out*), the filter runs server-side (client code
is pushed *in*). The security gradient is *inverted from frequency*: the common
case (listener) is the safe one; the rare case (filter) is the dangerous one that
earns the sandbox (§8).

> **Naming guard.** A `@RemoteClosure` is explicitly **not** used: a closure
> *captures* state, which §8 forbids. The word is `Function` precisely because
> there is no capture.

The `DYNAMIC`/`FUNCTION` distinction is a **functional duality**: `DYNAMIC` = bring
the call to the code (invoke remotely, at the author); `FUNCTION` = bring the code to
the data (ship it, run locally). The pure-value `DATA` form is **excluded** in this
version (§9.1 shows why most "data" is a plain parameter).

---

## 6. The Three Proxy Shapes

Derived from `proxy × codebase`. Only **shape 3** generates a proxy *class*, so it
is the only shape the BAE polices (§10) and the only one that can be a smart proxy.
The interface set a proxy carries is orthogonal to *who may call it*: which methods a
caller may actually invoke is decided by **server-side permission checks**, not by
which interfaces the proxy declares. A dynamic proxy may therefore safely carry its
**full interface set** — the service API plus administrative interfaces
(`Administrable`, `JoinAdmin`, `DestroyAdmin`) plus accessors plus
`RemoteMethodControl` — with **no** named backend/aggregate `Remote` super-interface
needed to drag them onto the stub.

1. **`java.lang.reflect.Proxy`, no codebase.** The client already holds every
   interface the proxy needs. The exported stub is a dynamic proxy over its full
   interface set, enumerated at export by a small **generated `AtomicILFactory` subclass** (§14) rather
   than by a downloadable `Remote` aggregate; there is **no** downloadable backend
   interface, **no** `*-dl.jar`, **no** generated proxy class, and nothing to download
   (the admin/accessor interfaces are platform classes the client already holds). Constrainable by JERI construction
   (§6.1). For a service designed this way, all client-facing interfaces live in the
   `-api` jar (§6.5).
2. **`java.lang.reflect.Proxy` + codebase.** As shape 1, but for a client that does
   **not** hold (all of) the interfaces. The classes are downloaded via
   `net.jini.export.DynamicProxyCodebaseAccessor` (`extends CodebaseAccessor`), and the
   download jar is simply the service's own **`*-api.jar`** served as the codebase (no
   hand-authored `-dl` module; §6.5). Loading is **local-first**: the client's existing
   (possibly older) copies of shared interface classes load first, and only the
   **additional** classes it lacks are fetched from the codebase (the
   shared-never-preferred rule, §6.2). Used when a receiver genuinely needs to **use**
   an interface it lacks — a client invoking a `@JiniService` it discovered but never
   compiled against; an older client of an **additively-evolved** API; or a **generic
   client whose dynamically-discovered ServiceUI** drives the service, where the client
   may hold **none** of the API classes and the UI (itself downloaded) resolves them
   from the codebase (§8.6). It is the **developer's** call to set `codebase = true` —
   when API evolution means clients may lack classes, or when a ServiceUI / generic
   consumer is an intended recipient (then shipping the `*-api.jar` in the codebase
   downloads is good practice); the framework does not infer it. (The old-client-CNFE case where the
   interface is genuinely *unused* is instead handled by *stripping* (§6.4), not by this
   shape.) Still no generated proxy class.
3. **Smart proxy + codebase.** Behaviour/state download via `ProxyAccessor` +
   `CodebaseAccessor`. A single top-level, constrainable-only, `@AtomicSerial`
   shell wraps the `@SmartProxy` delegate (§3.3), downloaded from the service's
   `*-dl.jar`. **The only shape that emits a proxy class**, hence the only one the
   `ConstrainableProxyComplianceVisitor` analyses. A generated **backend** wire
   interface appears only here, and only when the smart proxy translates the API into a
   distinct internal protocol (`protocol() != api()`); a do-nothing proxy (shapes 1–2)
   needs no backend interface.

The filter (`@RemoteFunction` `FUNCTION`) is **not** one of these three shapes: it is
standalone mobile code (§8), not a proxy. These "three proxy shapes" are proxy forms
only — a dynamic reference or a smart proxy — whereas a `FUNCTION` ships a whole
`@Stateless` invocable that runs at the receiver, with no `server` to forward to.

### 6.1 Constrainable-only (RULE-C1, §10)

The generated proxy **MUST** be the sole `RemoteMethodControl` form; there is **no**
non-constrainable variant. Every JERI stub already implements `RemoteMethodControl`
by construction (`AbstractILFactory.getExtraProxyInterfaces`, `BasicILFactory`,
`AtomicILFactory`), so the non-constrainable branch of legacy `create()` factories
is unreachable for a JERI export. Its only reachable use was wrapping a pre-Jini-2.0
(Davis, 2004) JRMP stub — a compatibility accommodation JGDMS 4.0.0 (JERI-only)
does not need. The generated factory **MUST fail closed** (throw) if `server` is not
a `RemoteMethodControl` (RULE-C2).

### 6.2 The preferred boundary is the `-api` / `-dl` split (RULE-C3)

Preferred status is decided by a jar's **role**, cleanly by module: **`*-api.jar`
classes are never `preferred`** (interfaces — the shared contract, assignability-
critical) and **`*-dl.jar` classes are always `preferred`** (downloaded
implementation/behaviour — isolated per codebase). A jar's preferred-list is therefore
mechanical and all-or-nothing by module, not a per-class analysis. This disentangles
the two purposes long conflated in `preferred`: **isolation** is served by preferring
*implementation* (`-dl`), while **version skew** is served by *shared, local-first*
interface loading (`-api`) — not by preferring interfaces. It also confirms shape 1
(no `-dl`) has *no* preferred classes at all.

Interface classes **MUST** be shared, never `preferred` — preferring breaks
assignability (`ClassCastException`). A shape-2 codebase (the service's `*-api.jar`
served for download, §6.5) is interface-only and therefore preferred-free by
construction. Because the classes are shared, loading is **local-first**: a client's
existing copy of a shared interface is authoritative and only *additional* classes are
drawn from the codebase — which is what lets an older client keep working against an
**additively-evolved** service API (new classes download; existing class identity is
preserved). Changing an *existing* class is therefore **not** an evolution this
mechanism covers — that remains the developer's API/binary-compatibility discipline
(STD-007 and the api-compat gate). See STD-007 and the preferred-list analyzer.

### 6.3 Proxy identity

Proxy identity is **endpoint + codebase**, not the codebase annotation alone.
`httpmd` embeds the jar digest in the codebase URL, which (a) fixes stale-cache when
a `*-dl.jar` is updated, (b) provides integrity, and (c) is the unit that
`LoadClassPermission`-per-digest and the BAE authorise.

### 6.4 Interface stripping and boomerang (dynamic proxies)

When a dynamic-proxy function (a listener) arrives at an endpoint that lacks some of
its interface classes, the endpoint **MAY strip** the unresolvable interfaces and
reconstruct the proxy from only those it holds — **no codebase required**. This is
sound because an interface the endpoint cannot resolve is one it cannot reference,
cast to, or invoke; it is, at that endpoint, **unused**. Stripping keeps the function
on the existing stream (no separate codebase/workload-identity stream is opened, §7)
and *reduces* attack surface (fewer callable methods, no foreign class-resolution
stream opened).

Stripping **MUST** retain (a) the JERI control interfaces (`RemoteMethodControl`,
`TrustEquivalence`) so the proxy stays constrainable (RULE-C1), and (b) the
registration interface through which the endpoint invokes the function.

Stripping the **live proxy** is destructive: a stripped live proxy is **terminal** —
*it* cannot *boomerang* (be forwarded to a downstream endpoint that would use the
fuller type, e.g. a capability check `x instanceof PriorityListener`). This is why an
intermediary never forwards the stripped live proxy: it forwards the retained DER form
(the `MarshalledInstance`) instead, which preserves the full interface set — no
codebase needed. Boomerang is thus a property of the relayed wire form, not of the
local live proxy.

**Boomerang preservation is a DER-stream capability [PROPOSED].** Boomerang is not a
bespoke proxy hack; it is a property of the wire format. A dynamic proxy's DER
encoding **must** list its interface set by name — that is its type schema — and
STD-006 §2.3/§7.8 already make that schema recoverable **without** the classes. So an
intermediary that **relays the received DER form** (rather than re-deriving it from a
live, stripped proxy) forwards the *complete* interface-name set, stripped entries
included; a downstream endpoint that **holds** those classes re-resolves them and
`instanceof` succeeds there. The names ride the schema, not the live proxy; the
intermediary never loads the foreign class. This is STD-006 §2.3 ("the name is the
universal key; identity survives without the class") applied to interface types.

Consequences:

- **DER always present.** Because a `@RemoteFunction` MUST travel over DER by rule
  (§3.2 / RULE-D1), a non-DER stream never arises: the full interface schema is
  **always** on the wire, so boomerang is **always** available — there is no
  degradation choice to make. A class-lacking endpoint still strips its *local* live
  proxy (it cannot resolve those interfaces), but the *relayed DER form* it forwards
  always preserves the full interface-name set. Full boomerang is thus a native
  property of this standard's hard STD-006 dependency, not a conditional one.
- **Same opaque-relay discipline as §9.2.** The intermediary forwards the marshalled
  (DER) form without re-deriving it — exactly the opaque-marshalled-relay pattern the
  registrar and the JavaSpace already use. It MAY hold a live *stripped* proxy for
  its own local calls **and** relay the intact DER blob onward.
- **Integrity for free.** The schema is part of the DER-encoded, `@AtomicSerial`-
  validated, Integrity-constrained wire form, so the interface-name list is already
  tamper-evident under the constrainable proxy's Integrity guarantee. An intermediary
  cannot forge a name to spoof a downstream capability without detection — no bespoke
  plumbing.
- **Identity, not manufacture.** DER schema preserves type *identity* across endpoints
  that do not use (or already hold) the class; it does **not** grant a class to an
  endpoint that *wants but lacks* one — that still needs a codebase (shape 2) or
  graceful degradation by stripping.

**No JDK change required [PROPOSED].** Boomerang is achieved by treating a
`@RemoteFunction` as a **retained `MarshalledInstance`** (its DER form). The receiver
**KEEPS** the received `MarshalledInstance` — whose DER schema lists the full
interface set — materialises a live (possibly *stripped*) proxy from it **only** to
invoke the function locally, and to boomerang simply **FORWARDS the retained
`MarshalledInstance` verbatim**. The live proxy is never forwarded, so it never needs
to remember stripped interface names. This is the same `MarshalledInstance`-first +
opaque-relay discipline the registrar and the JavaSpace already use (§9.2). The
previously-proposed `java.lang.reflect.Proxy` name-carrying modification (a DirtyChai
JDK change) is therefore **dropped**: it only served a "re-derive the wire form from
an already-stripped live proxy" case, which is both *avoidable* (retain the
`MarshalledInstance` instead of re-deriving) and *not sensible* (a wrapped/transformed
proxy is a new object with its own interface set, not the original). The **only**
residual cost is a JGDMS plumbing requirement, **not** a JDK change: the framework
**MUST** retain the received `MarshalledInstance` and materialise the live proxy
on demand.

> **Implementation status [PROPOSED, unbuilt].** Interface *stripping* is not yet
> implemented. The current runtime resolves a dynamic proxy **all-or-nothing**:
> `RMIClassLoader.loadProxyClass` (via `ClassLoading.loadProxyClass`) throws
> `ClassNotFoundException` if **any** interface name fails to resolve — there is no
> strip-the-unresolvable-and-reconstruct path. The wire substrate boomerang needs
> already exists (a dynamic proxy's interface set travels **by name** in the stream, so
> a relayed `MarshalledInstance` carries the full type schema), but the receiver-side
> stripping is a proposed capability, not code. It also has **no live consumer today**:
> the listeners that implement *extra* interfaces (e.g. `PolicyUpdateListener` →
> `LeaseListener`) declare only **non-`Remote`, local** interfaces, which never cross to
> the receiver, so nothing currently needs stripping. Stripping/boomerang first bites
> when a listener ships an extra **`Remote`** interface a receiver may lack.

### 6.5 Downloadable artifacts, module structure, and client reach

The **`*-dl.jar` is a smart-proxy (shape 3) artifact**: only downloaded *behaviour*
(a proxy class) justifies a downloadable *proxy* jar. Shape 1 downloads nothing; shape
2 downloads only *interface classes*, and its download jar is the service's own
`*-api.jar` served as a codebase (§6.2) — not a separate hand-authored module.

The Maven module structure therefore follows `proxy`:

| `proxy` | Modules | Downloadable proxy jar |
|---|---|---|
| `DYNAMIC` | `-api` (all client-facing interfaces) + `-service` (+ `-client`) | **none** — the `*-api.jar` itself is served as the codebase iff shape 2 applies |
| `SMART` | `-api` + `-dl` (proxy class) + `-service` (+ `-client`) | the `-dl` module's jar |

Two archetypes realise this: the existing `jgdms-service-archetype` scaffolds a
**smart-proxy** service (`-api` + `-dl` + `-service` + `-client`); a sibling
**dynamic-proxy** archetype scaffolds `-api` + `-service` + `-client` with **no `-dl`
module**, all interfaces in `-api`.

**Client-reach rule.** Any jar that can be **downloaded to a client** **MUST** compile
for Java 8 (or, where 8 is infeasible, Java 11) for widest client reach. Because a
shape-2 service serves its `*-api.jar` as the codebase, the **`-api` (Service API)
module itself MUST** meet the Java 8/11 bar — not merely the higher default build
target. This extends to the Service API the rule that already governs `-dl` jars.

---

## 7. The `DYNAMIC` / `FUNCTION` Axis — Four Correlated Consequences

The single `proxy` choice (`DYNAMIC` vs `FUNCTION`) on a `@RemoteFunction` determines
four things at once. They are not independent knobs; they are one boundary seen four
ways. `codebase` is **no longer a separate flag** on a `@RemoteFunction` — it is
implied by `FUNCTION` (and absent for `DYNAMIC`), which is why this axis is named for
the enum, not the (former) flag.

| | `DYNAMIC` (listener) | `FUNCTION` (filter) |
|---|---|---|
| **Base class** | none — a plain functional-interface impl | **`extends AbstractRemoteFunction`** |
| **Stream** | the caller's *existing* object stream | a *separate* codebase stream |
| **Identity** | the proxy's **own export-time constraints** — the registration hop runs under the caller's user Subject, but an **async callback does not propagate a live caller Subject** (see rationale) | **SPIFFE workload** identity only |
| **Execution site** | the caller's JVM (listener) | the server-side sidecar (filter) |
| **Configuration** | none of its own (uses the ambient exporter) | **required** (httpmd source, digest, annotation, ILFactory) |

Rationale for the identity row (STD-003). The `DYNAMIC` story has **two hops**, and
they differ:

- **Inbound registration** (client → service, the listener passed as a parameter)
  travels in the caller's *existing* stream and so runs under the caller's user
  Subject — which is why no codebase and no separate stream is needed.
- **The asynchronous callback** (service → listener, `notify(...)`) does **not**
  propagate a live caller Subject. By delivery time the registrant may be **offline**,
  so there is no caller stream to inherit: the callback authenticates by the
  **listener proxy's export-time embedded constraints**, and a store-and-forward
  intermediary (an event mailbox) delivers under **its own** service identity, not the
  registrant's. *(Verified against JGDMS: Mercury re-invokes `listener.notify()` under
  the mailbox's service-init `AccessControlContext` — `MailboxImpl` `doPrivileged(new
  NotifyListener(...), context)`, `context` captured at `MailboxImplInit` init — not
  the registrant's Subject; Reggie's async dispatch authenticates per the proxy's
  constraints.)*

The contrast with `FUNCTION` is therefore not "user Subject vs workload Subject" but
**whose credentials govern**: a listener is the client's *own* code running in the
client's JVM, reached via a constrainable proxy whose authority is fixed at export; a
filter is *foreign* code shipped to run at the server, which a codebase forces onto a
*separate* stream carrying only the mTLS-stamped SPIFFE workload identity.
`RemoteContextCodec` transmits **codebases-only, mTLS-stamped principals** by
construction — it will not smuggle an arbitrary user Subject across. So:

> **RULE-U1 (user Subject).** A `FUNCTION` `@RemoteFunction` (filter) **MUST NOT**
> require a user Subject; it runs under the workload identity only. A filter that
> needs user authority is a design smell — it is really a *client-side
> smart proxy* (where the user Subject is ambient in the user's own JVM, no
> transport needed) or a *service*, not a filter. For a filter, pass what it needs
> as **data** and keep it pure (§8).

Because a `FUNCTION` always ships code (a codebase, by implication), a filter is
**always** `AbstractRemoteFunction`, **always** separate-stream, **always**
workload-identity, **always** sidecar-executed. A codebase-free listener takes on
none of that weight: the cheap case stays cheap.

A codebase required *only* to **resolve interface classes** for a dynamic proxy is
**avoidable**: the receiver strips what it cannot use (§6.4), and because a
`@RemoteFunction` always travels over DER (RULE-D1), the DER schema always preserves
interface identity for a **boomerang with no codebase** (the receiver retains and
relays the `MarshalledInstance`, §6.4). So the `FUNCTION` / separate-stream /
workload-identity path is reserved for a **single** case that genuinely needs it:
**behaviour download** (the filter). On a `@RemoteFunction`, `FUNCTION`
means behaviour download, full stop.

`codebase` remains an **independent** flag on `@JiniService` (§3.1, §6 shape 2) — a
service may or may not ship a downloadable proxy regardless of its `proxy` type. Only
on `@RemoteFunction` does `codebase` collapse into the `DYNAMIC`/`FUNCTION` choice and
so is dropped as a separate element.

---

## 8. The Filter Contract (server-side predicate pushdown)

A filter is client-authored logic that executes **in the server's JVM** against
server-held candidate data, returning a boolean per candidate. Its purpose is
bandwidth: evaluate at the data, ship only matches. It carries the highest
confused-deputy risk (foreign code beside server data), so it is the most tightly
constrained construct in this standard.

### 8.1 Wire form — `@AtomicSerial @Stateless` (RULE-F1)

A filter **MUST** be `@AtomicSerial` (it crosses the wire; the atomic engine is the
only sanctioned path now that JOSS is severed — STD-008) and **MUST** be
`@Stateless`. `@Stateless` is a *security* property, not merely a marker: a
stateless instance captures nothing, so its wire form collapses to class-identity +
digest — no object graph rides along to smuggle a gadget, a captured reference, or a
secret. A *stateful* filter's "state" would be a serialized object graph, i.e.
exactly the deserialization surface STD-001/STD-008 exist to close.

### 8.2 Shape — a pure function (RULE-F2)

A filter **MUST** be a pure function `(candidate, declarativeParams) → boolean`:
all inputs arrive as **data arguments** (candidate fields plus declarative query
parameters — §9.1), and **nothing is captured**. This makes it deterministic, safe
to run in parallel across candidates, and trivially instantiated/discarded in an
ephemeral worker. It is the same `@Stateless` discipline as `ConstrainableSmartProxy`.

### 8.3 Load-time verification (RULE-F3)

For **downloaded** filter code the compile-time annotation is only a *claim*. The
BAE **MUST** load-verify `@Stateless` by rejecting any filter that declares
non-static instance fields (a hostile client could annotate `@Stateless` yet declare
fields to smuggle a graph). Compile-gate **plus** load-gate, exactly as for the
constrainable proxy (§10).

### 8.4 Runtime — process isolation over a Unix domain socket **[PROPOSED]**

A `FUNCTION` filter **SHOULD** run in a **separate process** with zero ambient
authority (no network, no filesystem; seccomp/namespaced), reachable only over a
**Unix domain socket**. The process boundary dissolves "foreign code beside data":
the code is not beside the data — it is in a worker that only sees what the server
serves it. UDS is the correct pipe because:

- **Local** — the pushdown's bandwidth win survives: the data→filter hop stays
  on-host (kernel IPC, no wire); only matches leave the box.
- **Credentialed** — `SO_PEERCRED` gives mutual peer authentication, tying the
  worker to a SPIFFE `WorkerSubject` (STD-003).
- **Data-mediated** — the worker never touches the heap; it *pulls candidates*, and
  the server serves only the **fields the predicate touches** (the Outrigger/Blitz
  field-index already knows which). Least *data* authority, and minimal IPC volume.
- **DATA on the hot path, never code** — filter bytecode loads into the worker
  **once** at install (under the per-digest load gate); per-candidate the socket
  carries only **DER field projections** (STD-006) and boolean verdicts. The foreign
  code is fed data over a codec that cannot carry a Java gadget.

**Tradeoff (stated, not hidden):** a separate process pays per-candidate IPC
marshalling versus in-JVM heap access. For a selective filter this is a large net
win over shipping everything to the client; for a non-selective one it costs more
but stays *local*, still beating the wire. Field projection bounds the volume.

This transport is a **lighter sibling** of `SOW-Unix-Domain-Socket-JERI-Transport.md`:
a local, peer-cred'd, DER-only channel likely needs **no** SSLEngine migration
(there is no network to protect). The worker is the role-neutral attested DirtyChai
worker: spun up ephemerally per query, digest-gated on which filter it will load,
torn down after so no filter contaminates the next.

### 8.5 Cross-language bridge

Because the worker decouples *where the data lives* from *what runtime the filter
needs*, a co-located JVM **filter sidecar** over UDS lets even a non-JVM data
service (e.g. Rust) offer Java-filter pushdown without the data process ever
touching foreign code. This partially lifts the §9.2 "cannot accept remote functions"
floor.

### 8.6 Generalization: the sidecar as a mobile-code container [PROPOSED]

The §8.4 sidecar is not filter-specific; it is the platform's general answer to
**received foreign code**: never run downloaded code in your own process — quarantine
it in a sidecar with only the capabilities it needs, over UDS. Foreign code is untrusted
by whoever *receives* it, so the mechanism serves **both** directions:

- **Server-side (filter, §8.4):** the server hosts a client's `FUNCTION` — zero
  authority, a pure predicate fed data.
- **Client-side (smart proxy / ServiceUI):** a client MAY host a downloaded
  `@JiniService` smart proxy — or a **ServiceUI** — in the same kind of sidecar rather
  than in its own JVM, quarantining the service author's code from the client's heap,
  secrets, display, and other windows; the client application drives it over UDS.

The client-app↔sidecar link is nearly free, because a service is defined by its
**Service API** (an interface): the application holds a plain `java.lang.reflect.Proxy`
— a `DYNAMIC` proxy (shape 1) — over the **UDS JERI transport**, no codegen and no
download. The sidecar exports the smart proxy over UDS JERI; the app imports it as a
dynamic proxy; the smart proxy forwards to the real service over the network — two JERI
hops (UDS app↔sidecar, network sidecar↔service), reusing the pluggable transport
wholesale (only the endpoint swaps from TCP to UDS). The payoff: this **downgrades the
application's foreign-code exposure from shape 3 to shape 1** — without the sidecar the
app downloads and runs the smart proxy in its own JVM (foreign code in-process); with
it, the app holds only a locally-generated dynamic proxy to the Service API it already
has (**zero download**), while *all* the downloaded foreign code is quarantined in the
sidecar. The riskiest shape becomes the safest, from the application's point of view.

**Provisioning follows the same `-api` / `-dl` split (§6.2), now as a *process*
boundary.** Even a *generic* client that lacks the Service API locally does not weaken
this. To hold the UDS dynamic proxy the client **provisions only the interface
codebase** — the service's `*-api.jar` (shape 2, §6) — into a ClassLoader, resolving
the dynamic-proxy *interface types* and nothing more. It **never deserialises the smart
proxy**: the `*-dl.jar` behaviour is downloaded and run **only inside the sidecar**
process. So the executable foreign code the client's own heap ever holds is effectively
none — the most it provisions is inert, shared, never-`preferred` interface types
(modulo interface default methods, a residual §8-style concern). Interfaces provision
into the client; behaviour provisions into the sidecar; the `-api`/`-dl` split and the
UDS process boundary **coincide**.

The tenants differ only in the **capability** the sidecar grants, and that is the crux.
A filter needs nothing. A smart proxy must make **authenticated remote calls** to its
service, so it needs a *narrow* network capability to that endpoint plus a way to
authenticate **as the user without holding the user's full credentials** — an
*attenuated, leased* delegation (the user Subject stays in the client application; the
sidecar gets a scoped capability for exactly the service and operations it needs). This
is the same attenuated-delegation problem as AI-agent authority, and the same DirtyChai
role-neutral attested worker hosts either tenant.

**ServiceUI is the compelling client-side exemplar, and it surfaces a third property
beyond security and bandwidth: robustness/control — a *kill switch*.** A ServiceUI is
long-lived, interactive, resource-heavy foreign code that today runs on the client's UI
thread; a hostile or merely buggy one can freeze, crash, leak, or screen-scrape the
whole client, and in-JVM foreign code **cannot be cleanly killed** (no safe
`Thread.stop`; a wedged EDT/JavaFX thread takes the client with it). In a sidecar it is
contained *and* terminable: if anything goes wrong the **user kills the process** — its
window closes, its resources are reclaimed, the client application is untouched. This is
what in-JVM policy fundamentally cannot provide: a SecurityManager can forbid actions,
but it cannot give a clean "close this and reclaim everything" for code that misbehaves
*within* its permissions or simply wedges. Only a process boundary does.

Because a ServiceUI decorates a service purely through the **Service API**, the UI and
the smart proxy can be *separate* sidecars — the UI calls the proxy over UDS JERI (a
dynamic proxy typed as the Service API) just as the app would. This decouples
presentation from the proxy and hands the user real agency: the shipped `UIDescriptor`
becomes a *suggestion*, and the user may pair any **API-compatible** UI they trust or
prefer with any service they found on the lookup. The two sidecars then have *disjoint*
capabilities — the UI sidecar needs a display + UDS-to-proxy and **no network**; the
proxy sidecar needs network-to-service + UDS-serving-API and **no display, no secrets** —
so compromising one yields nothing of the other's, and either is independently killable.
(This composition/selection model, the delegation credential model, and full
sidecar/worker design are their own design surface, out of scope for this standard.)

---

## 9. Boundary and Security Model

### 9.1 What is *not* a `@RemoteFunction` (RULE-B1)

A declarative predicate (or any value) whose class **already exists at the
receiver** is **not** a `@RemoteFunction`. It is an ordinary **ServiceAPI
parameter**: plain Java, no codebase, no annotation, no download; the server
evaluates it with its own trusted code because the type is the server's own. The
JavaSpace template match (exact field-equality) is the degenerate case.

Therefore `@RemoteFunction` is the annotation for **"the class or behaviour has to
cross the wire."** If the type is already shared in the API, there is nothing to
annotate. The **safe default** for a server-side comparison is *not* a
`@RemoteFunction` at all — express the predicate as a shared ServiceAPI type;
`@RemoteFunction` is the escalation only when the class cannot be pre-shared
(and then, for a filter, it ships as `@Stateless` mobile code, §8).

### 9.2 Acceptance is opt-in and fail-closed (RULE-B2)

Accepting a `@RemoteFunction` parameter is a capability the **receiving service
grants**; a client cannot force it. A service **MUST** default to accepting **no**
remote-function parameters unless its API explicitly declares them. Two baseline
refuser classes, both fail-closed:

- **Cannot** — a non-JVM service (Rust, …) has no JVM to download a class into or
  invoke a callback on; it deals only in DATA-by-value over a wire protocol (STD-006),
  classes pre-agreed at both ends.
- **Will not** — the **lookup service** (registrar), for security: downloading or
  executing a registrant's proxy or a poisoned argument class would make it a
  **confused deputy**. It **MUST** relay registered proxies as **opaque marshalled
  blobs** (`ServiceItem`/`MarshalledObject`) — never calling `.get()`, never
  resolving the class. Download-and-execute (and the LoadClassPermission-per-digest
  and BAE gates) happen at the **looking-up client**.

Consequently, *"is a service"* is **orthogonal** to *"accepts functions"*: the
registrar is the ultimate `@JiniService`, yet accepts zero remote functions. The
same opaque-relay discipline generalises to the JavaSpace, which matches on
marshalled entry fields without deserializing the payload.

### 9.3 Identity

Per §7 and STD-003: a `DYNAMIC` listener carries the **listener proxy's own
export-time constraints** — the inbound registration hop runs under the caller's user
Subject, but an **asynchronous callback does not propagate a live caller Subject** (the
registrant may be offline; a store-and-forward mailbox delivers under its own service
identity). A `FUNCTION` filter runs under the **SPIFFE workload** identity only. The
*absence* of a user Subject on the codebase path is a feature — foreign filter code
runs under a machine identity with no user principals to abuse.

---

## 10. Compliance Rules

The rules split into two groups by how they are enforced.

**BAE-enforced (bytecode).** These are machine-checkable at bytecode level, in the
manner of STD-001. A violation yields a non-`COMPLIANT` verdict and marks the codebase
`DANGEROUS` (refused by clients).

| Rule | Verdict on violation | Statement |
|---|---|---|
| **RULE-C1** | `NON_CONSTRAINABLE` | A generated smart proxy MUST transitively reach `ConstrainableSmartProxy` / `RemoteMethodControl`. |
| **RULE-C2** | `CONSTRAINTS_NOT_APPLIED` | `setConstraints` MUST build a **new same-class instance**, never return `this`/`null`; `getConstraints`/`create` MUST NOT strip constraints. Generated `create()` MUST throw if `server` is not `RemoteMethodControl`. |
| **RULE-C3** | (preferred-list gate) | Interface classes MUST be shared, never `preferred`. |
| **RULE-D1** | `NON_DER_TRANSPORT` | A `@RemoteFunction` MUST be transmitted over a DER stream (STD-006) — the **whole** object stream carrying it, listener's existing stream included, not only the codebase hop; a non-DER endpoint MUST refuse it. |
| **RULE-F1** | `NOT_ATOMICSERIAL` | A filter MUST be `@AtomicSerial` (no `java.io.Serializable` dual path). |
| **RULE-F2** | `NOT_PURE_FUNCTION` | A filter MUST be a pure function `(candidate, declarativeParams) → boolean`: all inputs arrive as data arguments and **nothing is captured** (§8.2). |
| **RULE-F3** | `NOT_STATELESS` | A downloaded `@Stateless` filter MUST declare **no non-static instance fields**. |

RULE-C1/C2 are enforced by the `ConstrainableProxyComplianceVisitor`
(`ConstrainableProxyVerdict`, `au.net.zeus.jgdms.api.codebase`), wired into the
`JarAnalyzer` per-class exactly like `AtomicSerialComplianceVisitor`. It gates only
classes transitively extending `AbstractSmartProxy`.

> **Implementation status — RULE-D1 [unbuilt].** RULE-D1's *refuse-non-DER* obligation
> is a **runtime / transport** property, not a bytecode fact — the BAE can flag that a
> proxy *type* requires DER, but "this stream is DER" is only knowable at transport
> time. Today the real listener path already rides the **hardened `@AtomicSerial`
> engine** (codebase-annotation-free, `useCodebaseAnnotations = false`, and
> gadget-hardened) and the STD-006 `jgdms-der` codec exists, but **no runtime gate yet
> refuses** a non-DER stream carrying a `@RemoteFunction`. RULE-D1 is thus normative in
> intent with enforcement pending; because it is a transport property, consider
> relocating it to the design/runtime group below when its placement is confirmed.

**Design / runtime rules (not bytecode-checkable).** These are design and runtime
obligations, not statically decidable from bytecode; they are enforced by design
review and by fail-closed runtime behaviour, not by the BAE.

| Rule | Verdict on violation | Statement |
|---|---|---|
| **RULE-U1** | (design review) | A `FUNCTION` `@RemoteFunction` (filter) MUST NOT require a user Subject; it runs under the workload identity only (§7). |
| **RULE-B1** | (design review) | A value whose class **already exists at the receiver** is **not** a `@RemoteFunction` — it is an ordinary ServiceAPI parameter (§9.1). |
| **RULE-B2** | (runtime, fail-closed) | Accepting a `@RemoteFunction` parameter is **opt-in**: a service MUST default to accepting **none** unless its API explicitly declares them (§9.2). |

---

## 11. First Consumers — Reggie & Outrigger

The filter (§8) is the rare, hard, extract-from-evidence part. Its first two
consumers are the two core template matchers, whose exact-field-equality model has
been the classic 20-year limitation; they **define** the filter contract rather than
letting us guess it.

- **Outrigger (JavaSpace).** A filter upgrades template matching → predicate matching
  (ranges, inequality, compound, computed). It rides the existing Blitz field-index,
  which already matches *marshalled* fields and knows which fields a predicate
  touches — exactly the §8.4 field-projection. The space already matches entries
  opaquely, so the sidecar fits the existing non-deserializing-matcher architecture.
- **Reggie (registrar).** A filter upgrades attribute-set-template matching → rich
  attribute matching. The sidecar preserves the §9.2 rule: Reggie itself still never
  deserializes or executes a stored proxy — the filter sees only **attribute**
  field-projections over UDS, never the proxies. **Boundary:** attribute predicates
  push down; proxy-*inspecting* filters stay client-side (the existing
  `ServiceItemFilter`). The pushdown payoff is larger for Reggie, because
  downloading a proxy is itself a trust-and-deserialization event.

This promotes `@RemoteFunction` from a client convenience to a **substrate
matching-extension mechanism** — the functional gate of
`DESIGN-AttestedCapabilityMatching.md` and the `Test` predicate of
`DESIGN-CorroborationFramework.md`, realised over the two core matchers. That is
what justifies the sidecar / `@Stateless` / BAE machinery: it is foundational, not a
bolt-on.

---

## 12. Class Hierarchy — two separate bases

A `@JiniService` is **not** a `@RemoteFunction` (§1): they are opposite poles of the
Service-vs-Function axis — a stateful endpoint you *talk to* vs a stateless invocable
you *apply*. Their base classes are therefore **separate and unrelated**; neither
subclasses the other.

> `AbstractJiniService` **MUST NOT** extend `AbstractRemoteFunction` (nor vice versa).
> The inheritance would assert a false "a service is a kind of function": a service is
> stateful, registered, and outward-facing where a function is stateless, unregistered,
> and inward-facing — not substitutable (Liskov).

- **`@JiniService`** → the **existing** `AbstractJiniService` (join / lookup /
  attributes / exporter / Configuration). Unchanged.
- **`@RemoteFunction`:**
  - `DYNAMIC` (listener) → extends **nothing**: a plain functional-interface
    impl that keeps the caller's user Subject.
  - `FUNCTION` (filter) → extends **`AbstractRemoteFunction`**, a **distinct,
    filter-specific** base for sandboxed downloaded mobile code (workload identity,
    `@Stateless`, no export, no join). Built **later, from evidence** (§11).

`AbstractRemoteFunction` is **not a subset of** `AbstractJiniService`: because
`FUNCTION` reduced to filter-only (§7), its content is sandboxed-mobile-code
machinery a service does not have, and even the shared-sounding "codebase" concern is
directionally opposite — a service *provides* a downloadable proxy (clients pull it),
a filter's code is *downloaded to* a sidecar (the server pulls it). What the two do
share is the constrainable-proxy **generation tooling** (the processor), not a runtime
base class. Any genuinely shared low-level plumbing is reached by **composition / a
shared utility**, never a common superclass that implies IS-A.

---

## 13. Build Sequence (informative)

The annotation *processor* work (per the SOW) proceeds:

1. **Annotation API** — add `ProxyType { DYNAMIC, SMART }`, the `codebase` boolean,
   and the `api()` `Class<?>[]` element to `@JiniService` in
   `jgdms-service-annotations`, and make it `@Retention(RUNTIME)` (so
   `AbstractJiniService` can reflect it); retire `generate[]`. `--release 8`. Both
   the `api()` addition and the impl-side placement are **coupled to the processor**
   (`ServiceProxyProcessor` / `ServiceModel` and their tests read the annotation off
   the impl and resolve `api()`) and to the runtime (`AbstractJiniService`'s
   now-concrete `getServiceInterfaces()` / `createProxy()`), so this step is **not**
   annotation-only despite touching the annotation module.
2. **Shape dispatch** — derive the generated shape from `proxy × codebase` (§6).
3. **Smart shell** — the constrainable-only `@AtomicSerial` delegate-wrapper (shape 3).
4. **`-dl` jar packaging + `PREFERRED.LIST`** — the `-dl` jar carries the service's
   *declared* interface(s), **shared never preferred** (RULE-C3); reuse the
   marshal-delegate `META-INF/services` merge pattern for any registration.
5. **Wire into the build** and migrate hello-world — `@JiniService(api =
   HelloService.class, proxy = DYNAMIC)` on the `HelloWorldServiceImpl`
   *implementation* (shape 1), the API interface left a bare `Remote` contract — as
   the golden-diff oracle.

`@JiniService` is built **first**; the listener form of `@RemoteFunction` rides the
same dynamic-proxy plumbing almost for free. The **filter runtime** (§8) is the
later, meatier phase, and its shape is derived from Reggie/Outrigger (§11).

---

## 14. Open Questions

- **[OPEN]** Exact `net.jini.config.Configuration` surface owned by
  `AbstractRemoteFunction` vs inherited by `AbstractJiniService`.
- **[OPEN]** The declarative query-parameter type(s) a filter receives alongside the
  candidate (§8.2) — a shared ServiceAPI predicate/expression type, per §9.1.
- **[OPEN]** Whether a `DATA` form is ever re-admitted (§5), and by which consumer.
- **[OPEN]** How and where the framework retains the received `MarshalledInstance`
  for a `@RemoteFunction` argument, and materialises the live proxy on demand (§6.4) —
  the one residual plumbing requirement of the retain-and-relay boomerang mechanism.
- **[OPEN]** Filter sidecar lifecycle: per-query vs pooled-per-digest; teardown
  guarantees; resource accounting.
- **[RESOLVED 2026-07-03]** How a **dynamic** proxy is given its full interface set —
  service API + non-`Remote` admin/accessor + `RemoteMethodControl` — *without* a
  `Remote` aggregate (§6): the generator emits a small `AtomicILFactory` **subclass
  overriding `getRemoteInterfaces`** to append the admin interfaces (`Administrable` /
  `JoinAdmin` / `DestroyAdmin`) directly — the pattern `ProxyTrustILFactory` already
  uses for `ProxyTrust`. That single hook feeds **both** the stub's interface set (cast)
  and the server **dispatch** set (`AbstractILFactory.getInvocationDispatcherMethods`
  draws only from `Remote`-reachable interfaces), so admin methods are invocable;
  `getExtraProxyInterfaces` alone gives identity but **not** dispatch. The aggregate is
  a convention, not a JERI requirement — retiring it needs no change to shared base
  code, and the subclass is server-side (not downloaded). Evidence:
  `Util.getRemoteInterfaces:491` (`Remote`-filter), `AbstractILFactory:151-156,282`,
  `ProxyTrustILFactory:106-119`.

---

## 15. Normative & Informative References

- JGDMS-STD-001 — AtomicSerial Compliance
- JGDMS-STD-003 — Multi-Subject Identity Architecture
- JGDMS-STD-006 — DER Wire Format
- JGDMS-STD-007 — Codebase Compatibility Interchange
- JGDMS-STD-008 — AtomicSerial Serialization Uncoupling
- `SOW-service-proxy-annotation-processor.md` — the implementation companion
- `SOW-Unix-Domain-Socket-JERI-Transport.md` — the heavier UDS transport
- `DESIGN-AttestedCapabilityMatching.md`, `DESIGN-CorroborationFramework.md`
- `net.jini.export.DynamicProxyCodebaseAccessor`, `CodebaseAccessor`, `ProxyAccessor`
