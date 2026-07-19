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
>
> **Cross-referenced 2026-07-17 (independent convergence, not yet reconciled into the
> body text below).** A separate design thread — starting from a completely different
> question (a public r/java debate about `System.nanoTime`-class timing side channels
> in mobile code) — arrived at §8.6's general principle ("never run downloaded code in
> your own process") independently, then went substantially further into concrete,
> buildable detail for the **client-side smart-proxy tenant** specifically. See
> `SOW-Unix-Domain-Socket-JERI-Transport.md` §12 (mandatory per-SPIFFE-principal
> sidecar routing, the client/subprocess unmarshalling split, DGC-based lifecycle,
> CPU-affinity requirement), `SOW-SubProcessDynamicPolicy.md` (the concrete build-out
> of §8.6's "attenuated, leased delegation problem," left there as future work), and
> `SOW-Smart-Proxy-Isolation-Architecture-Overview.md` (index tying the set together).
> Three things worth flagging explicitly, each noted again at its own section below:
> (1) §6.4's "no live consumer today" caveat for interface stripping is likely now
> **stale** — the newer session's client-side local delegate stub is plausibly the
> first live consumer, and independently re-derived the identical
> `RMIClassLoader.loadProxyClass` all-or-nothing finding this section already
> documents; (2) §8.4's filter sidecar CPU-affinity/cache-timing-side-channel gap was
> flagged the same day and has since been **decided** (see §8.4's own note): filter
> workers for different, mutually-untrusting clients MUST run on separate physical
> cores sharing no L1/L2, confirming this was a real gap, not a deliberate scoping
> choice; (3) §8.6's closing line ("full sidecar/worker design... out of scope
> for this standard") is **only true for the filter tenant now** — the smart-proxy
> tenant has a concrete task-level design in the SOWs above. **This note flags the
> overlap for reconciliation; it does not itself resolve which document is
> authoritative where they diverge — that needs Peter's own pass.**

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
>
> **Update 2026-07-17: "no live consumer today" is likely stale.** A separate design
> thread (`SOW-Unix-Domain-Socket-JERI-Transport.md` §12 point 3(ii)) independently
> re-derived the identical finding in this note — `RMIClassLoader.loadProxyClass`
> resolves a dynamic proxy's interfaces all-or-nothing, confirmed fresh against
> `au.net.zeus.jgdms.der.stream.DerObjectStreamCodec`'s bare-`Proxy` `[8]` wire item and
> `au.net.zeus.jgdms.der.object.DerProxySerializer` — and hit the same gap from a
> different direction: a new **client-side local delegate stub** (a
> `java.lang.reflect.Proxy` forwarding calls over UDS to a smart proxy hosted in an
> isolated sidecar process) needs to be built from *whichever* of the real object's
> interfaces the client happens to have locally, with the rest silently dropped —
> exactly this section's "stripping" concept, not a new mechanism. That design proposes
> a concrete field-level realization: a new interface-name-list field on
> `org.apache.river.api.io.ProxySerializer`'s wire form (currently just
> `bootstrapProxy`/`serviceProxy`), populated at serialization time from the live
> sender-side object, resolved leniently (per-name, local-only, no download, catch and
> drop on `ClassNotFoundException`) rather than through the existing all-or-nothing
> path. If built, this becomes this section's first live consumer — the "no live
> consumer today" line should be revisited once (if) it lands. It also directly needs
> this section's **boomerang** discipline: the local delegate stub is process-bound and
> **must not** be naively re-serialized if forwarded onward (e.g. to a downstream
> ServiceAPI consumer) — the retained wire form (that stub's own `ProxySerializer`
> instance, or the `MarshalledInstance` it was built from) should be relayed instead,
> the same "relay the DER form, never re-derive from a live/stripped proxy" rule this
> section already states for the listener/filter case.
>
> **BUILT 2026-07-17 (trunk `a20455e3e`), superseding the "PROPOSED, unbuilt" status
> above for the generic bare-`[8]` Proxy case — verified directly against the merged
> source, not assumed.** `au.net.zeus.jgdms.der.object.BoomerangProxyHandler`
> (`jgdms-der`) is now the real implementation of this section's stripping + boomerang
> pattern at the DER `[8]` wire-item granularity: `DerObjectStreamCodec.decodeProxy`/
> `ObjectCodec.decodeProxy` still resolve each wire-declared interface name
> independently and drop unresolvable ones (unchanged), but the live proxy's handler is
> now wrapped in `BoomerangProxyHandler`, which retains the **raw original `[8]` TLV
> content bytes** (not a parsed name list) so a later re-forward
> (`ProxyWireSupport.wireContentForBoomerang`, checked first in both codecs' write
> paths) emits those bytes byte-for-byte instead of re-deriving fresh DER from
> `proxy.getClass().getInterfaces()` — closing an integrity gap the prior
> `TolerantProxyHandler` design had (name-based re-derivation forfeited the sender's
> `@AtomicSerial`-validated integrity guarantee across the hop). `invoke()` is pure
> passthrough to the real handler — zero behaviour change for any interface that
> resolved locally; the fast path for "nothing was ever dropped" is also explicitly
> unchanged. `BoomerangProxyHandler` deliberately has no `serialForm`/`GetArg`
> constructor — it is a decode-time-only artefact, never itself a wire type.
> **Confirmed via independent verification pass (2026-07-17): this merge does not
> affect the UDS smart-proxy isolation design** (`SOW-Unix-Domain-Socket-JERI-
> Transport.md` §12, `SOW-SubProcessDynamicPolicy.md`) — the client-side local
> delegate stub's interface selection was already designed to bypass this exact
> mechanism entirely (a dedicated new `ProxySerializer` field, not the generic
> bare-`[8]` path); `ProxySerializer`'s `bootstrapProxy` field *does* flow through
> `encodeProxy`/`decodeProxy` (its declared type is an interface,
> `Proxy.isProxyClass` dispatch is runtime-class-based, not declared-field-type-based
> — worth being precise about, a naive read could assume otherwise), but always hits
> the unchanged "nothing dropped" fast path, since its two interfaces
> (`CodebaseAccessor`, `RemoteMethodControl`) are fixed platform types every node
> always resolves; `serviceProxy`'s own content stays an opaque, undecoded
> `MarshalledInstance` at this layer regardless, per the design's own "client never
> unmarshals the smart proxy" property (§12 point 3(i)) — unaffected either way. Worth
> reconsidering, not now, not decided here: whether the still-unbuilt `ProxySerializer`
> interface-name field proposed above should be unified with `BoomerangProxyHandler`'s
> now-real byte-retention mechanism rather than inventing a parallel one — flagged for
> whoever picks up that task, not a redesign performed in this verification pass.

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

> **DECIDED 2026-07-17 (Peter), superseding the "standard JVM bytecode" premise below
> — the rest of §8 (and parts of §5, §7, §9.1, §11, §12) describe the PRE-2026-07-17
> model and need re-deriving, not just re-reading, against this decision.** Resolving
> the "does a filter need to be standard JVM bytecode at all" question raised earlier
> the same day (§8.5's design-fork note): **no — filters are not compiled Java
> classes.** They are expressed in a small, purpose-built, restricted
> predicate/expression format instead. Peter's stated reason extends further than
> "smaller/safer": **the same restricted mechanism can be used by both Rust and Java
> services** — i.e. this is not a Java-specific execution format with a Rust
> compatibility shim bolted on; it is a single, host-language-agnostic format every
> service (JVM or otherwise) implements its own small evaluator for. This has several
> immediate, direct consequences, recorded here so they aren't silently lost or left
> contradicting the untouched text below:
>
> - **§8.4's "cross-language bridge via a co-located JVM sidecar" (§8.5) is no longer
>   the *only* way a non-JVM host offers filter pushdown — it may not be needed at
>   all.** If the filter format itself is portable and Rust can host a small native
>   evaluator for it directly, a Rust data service doesn't need to spawn a JVM
>   subprocess just to run a filter — the entire "ship a stripped-down JVM" /
>   "Rust-native JVM interpreter" discussion above (§8.5's notes) may turn out to be
>   solving a problem this decision removes for the *filter* case specifically. (A
>   sidecar/process-isolation boundary may still be wanted for blast-radius
>   containment independent of language — that's a separate question from "does it
>   have to be a JVM.")
> - **Filters plausibly stop being an exception to RULE-B1 (§9.1) and become an
>   instance of it.** §9.1 currently distinguishes "a value whose class already
>   exists at the receiver" (ordinary ServiceAPI parameter, no `@RemoteFunction`
>   needed) from "behaviour that has to cross the wire" (`@RemoteFunction`,
>   escalation). A restricted expression-tree/predicate format is **data** — an AST of
>   comparison/boolean nodes over declared fields — evaluated by an interpreter every
>   receiver already has locally (platform code, not downloaded per-filter). That
>   looks structurally like the ordinary-parameter case §9.1 already describes, not
>   the code-shipping escalation `FUNCTION` was built for. Needs an explicit
>   re-derivation, not assumed either way here.
> - **RULE-F1's `@AtomicSerial`/`@Stateless` wire-form requirement (§8.1) and RULE-F3's
>   BAE load-time `@Stateless` check (§8.3) were designed for a *compiled class*
>   crossing the wire.** For a restricted expression format, statelessness/no-capture
>   is plausibly true **by construction** (if the format has no syntax for mutable
>   state or captured references, there's nothing to check for) rather than something
>   a load-time bytecode scan verifies — the same "safe by construction, not by
>   runtime gate" shift §8.5's interpreter discussion already identified. §8.1/§8.3's
>   *rules* may still hold in spirit; their *mechanism* (bytecode annotation +
>   load-time class inspection) needs redesigning for a non-bytecode wire form.
> - **§5's Filter row ("Codebase (derived): implied (code ships)") and §7's FUNCTION
>   column ("Stream: a separate codebase stream"; "Configuration: required (httpmd
>   source, digest, annotation, ILFactory)") assumed behaviour download.** If nothing
>   downloadable ships — only a data-encoded expression riding the existing DER
>   stream, evaluated by platform code every receiver already has — the FUNCTION
>   form's whole "codebase implied" premise (§7's organizing principle: "`FUNCTION`
>   means behaviour download, full stop") needs re-examining. This may also bear on
>   whether `FUNCTION` still needs a `codebase`-carrying separate stream at all, or
>   whether it can ride the caller's existing stream the way `DYNAMIC` already does
>   (§7) — genuinely open, not decided here.
> - **§11 (Reggie/Outrigger as first consumers) and §12 (`AbstractRemoteFunction` as a
>   distinct base class "for sandboxed downloaded mobile code... no export, no join")**
>   both reasoned from the compiled-class/codebase model and should be re-checked once
>   the new format's shape is designed — not necessarily wrong, but not yet verified
>   against this decision either.
>
> **What is NOT yet decided, deliberately left open here:** the concrete shape of the
> restricted expression/predicate format itself (an AST? a small stack-bytecode ISA
> of our own design? something closer to a JSON/DER-encoded query-expression tree?);
> what operator/comparison vocabulary it needs for Outrigger/Reggie's first-consumer
> use cases (§11); how BAE's role changes (verifying a restricted, purpose-built format
> is a different, likely much simpler task than verifying arbitrary Java bytecode, but
> is still a real design task); and whether a sidecar/process boundary is still wanted
> for defense-in-depth once the format itself is safe by construction. §8.1-§8.6, §9.1,
> §11, and §12 below are left as-written (the prior, now-superseded-in-part model) so
> nothing is lost — treat them as historical/needing-re-derivation, not current, where
> they conflict with this note.
>
> **Concrete format recommendation — Claude's recommendation to Peter, 2026-07-17,
> NOT a decision, informed by a dedicated research pass on current (2026) third-party
> option maturity.** Primary candidates considered: a fully custom AST/expression-tree
> format (DER-encoded, reusing STD-006); CEL (Common Expression Language); Wasm
> (wasmtime/Chicory/GraalWasm) hosting compiled logic; OPA/Rego (which itself compiles
> to Wasm); Starlark (ruled out early — a real scripting language with loops/
> recursion, wrong shape for "functional only"). Research findings, current as of
> 2026-07: **CEL is a stronger contender than a first-pass "avoid all third-party
> dependencies" instinct would suggest** — the spec was formally relocated to a
> dedicated `cel-expr` GitHub org in June 2026 pursuing CNCF Sandbox status (active
> institutionalization, not stagnation), has real, currently-maintained Rust
> (`cel-rust/cel-rust`) and Java (`cel-expr/cel-java`, commits current to January
> 2026) implementations, is non-Turing-complete and mutation-free **by design** (no
> unbounded loops/recursion, cost bounded by input+expression size — Kubernetes'
> admission-control usage even ships static cost estimation), and has deep,
> current production adoption (Kubernetes `ValidatingAdmissionPolicy`, Envoy,
> Elasticsearch). By contrast, wasmtime — the leading Rust Wasm runtime — had its
> largest-ever security-advisory batch in April 2026 (triple 2025's total, including
> a Critical Cranelift codegen bug, found via new LLM-assisted fuzzing) plus a further
> CVE this month; a large, actively-probed attack surface, and fuel-metering's
> termination guarantee has a real caveat (a loop calling host functions without
> consuming fuel can still run indefinitely — the embedding has to be careful).
> OPA/Rego is solid but is a full policy language (rule sets, partial evaluation,
> sets/objects) — more machinery than a bare predicate format needs, and its Java
> path (JNI bindings over `regorus`) isn't published/first-class. **Recommended
> middle path, not either extreme:** adopt **CEL's grammar/semantics** as the
> predicate language — well-specified, field-tested, genuinely bounded-by-design, so
> we aren't the ones inventing and proving a novel termination guarantee — but write
> **our own minimal tree-walking evaluators**, in Rust and in Java, over the existing
> DER binary encoding, rather than taking on `cel-java`/`cel-rust` as runtime
> dependencies. Reasoning: those two are independently-maintained implementations of
> a shared spec, not one shared codebase — "same semantics across hosts" isn't free
> even with them, it still needs version-pinning and running the conformance suite
> ourselves — so the dependency doesn't fully buy back the cross-language-consistency
> risk it might seem to. A small, self-written, DER-native evaluator keeps the TCB
> single-sourced and fully auditable by this team while borrowing CEL's proven
> grammar design instead of re-deriving bounded-execution semantics from scratch.
> **Still not decided** — Peter's call. **The §11 contingency is now checked
> (2026-07-17, see §11's own note): CEL-shaped vocabulary is sufficient** for
> everything found in Outrigger/Reggie's real matching code — no grammar gap. The
> research surfaced a different, real cost instead: neither service exposes typed
> field values to match against today (Outrigger: none at all, every field opaque
> `MarshalledInstance` bytes; Reggie: partial, immutable-typed attributes only) — so
> "build the filter" is mostly new typed-field-projection matching-runtime work, not
> a grammar/language risk. Doesn't change the format recommendation; does change
> what §11's eventual SOW needs to scope as its real first task.

> **Verified 2026-07-20 (the data side of the restricted-filter decision: schema-visible
> DER makes class-free filtering real — checked directly against trunk, not assumed).**
> Prompted by the question "can a filter evaluate over serialized data *contents* without
> classes — e.g. observe string and numeric field values": **yes, and the substrate is
> already built.** STD-006 §2.3/§3.11's data-independence property ("the schema is the
> key, not the class") is implemented, not aspirational: `MarshalledInstanceRecord`
> (`jgdms-der`, `.../der/marshal/MarshalledInstanceRecord.java`) unconditionally embeds
> `schemaBytes` — the Merkle-chained per-class `AtomicSerialSchemaRecord`, each carrying
> an **ordered `wireName`/`wireType` field list** — beside `payloadBytes`, whose scalar
> fields are **native DER primitives** (INTEGER for integrals, UTF8String for `String`,
> strict-canonical IEEE-754 OCTET STRING for float/double; `ObjectCodec.encodeValue`,
> `.../der/object/ObjectCodec.java:1066-1142`). And the class-free reader already
> exists: `ObjectCodec.decodeToFieldMap` (`ObjectCodec.java:892`) decodes payload bytes
> into a `className → (fieldName → value)` map using only the embedded schema — no
> `Class.forName`, no constructor, no `check(GetArg)` — exercised by
> `Std006ConformanceTest`. Direct consequences for this section's open items:
>
> - **A CEL evaluator needs no classes at the evaluation site.** It binds identifiers
>   to the schema's field names and reads typed values from the projection; CEL's type
>   set maps nearly 1:1 onto the §3.12 wire scalars. This holds identically for a
>   non-JVM host — schema-driven DER projection is language-neutral by design (§2.3's
>   "polyglot access" is this same mechanism), which is exactly the both-Rust-and-Java
>   requirement behind the 2026-07-17 decision above.
> - **Evidence toward the "does `FUNCTION` still need a codebase/separate stream"
>   bullet above (still Peter's ratification, but the evidence now points one way):
>   nothing downloads in either direction.** The filter is a DER-encoded expression
>   riding the existing stream; the candidates are schema-bearing DER read without
>   classes; the evaluator is platform code both ends already hold. The filter
>   genuinely collapses into RULE-B1's ordinary-parameter world (§9.1).
> - **No reconstruction surface at all.** The filter path reads scalar values out of
>   validated TLVs and never reconstructs objects — no `DeSerializationPermission`
>   gate crossed, no gadget surface, nothing left for the old RULE-F1/F3 bytecode
>   mechanisms to police. Because `check(GetArg)` never runs in class-free decode,
>   the filter's verdict is **pre-selection only**: the taking client still performs
>   the full gated deserialization on anything it accepts, so filter false-positives
>   cost bandwidth, never integrity. A candidate whose bytes fail canonical decode,
>   or whose schema lacks a referenced field, MUST be **no-match, fail-closed**
>   (CEL `has()` semantics cover benign schema evolution).
> - **The projection reader MUST be the same fail-closed codec** — canonical-form
>   rejection, `schemaDigest` verified against `schemaBytes` before use,
>   `maxFields`/`maxCollection` bounds — never a second, lenient "peek at the
>   strings" scanner (one-encoding-per-value, board guidance G1).
>   `decodeToFieldMap` currently decodes *all* fields; a lazy projector that skips
>   untouched fields via DER TLV lengths is a small optimization over the existing
>   reader, not new architecture.
> - **Matching-key decision needed:** a sender controls its own `schemaBytes`, so
>   "which candidates does this filter apply to" needs a deliberate key —
>   `className` string (coarse) vs `schemaDigest` (exact) — blast radius of a lying
>   schema is confined to the sender's own entries either way, but the key choice is
>   a real design decision, not a default.
> - **Honesty note — data independence cuts both ways:** a schema-bearing entry is
>   trivially readable by whoever holds the bytes (space/registrar operator, disk
>   image). Under JOSS that took the class; under DER it takes nothing.
>   Confidentiality of entry contents is a transport/storage-control property and
>   should be stated as such, not left implied.
> - **The hard dependency: class-free filtering only works over `ATOMIC_DER`-
>   marshalled entries — and today neither first consumer produces them by
>   default.** Outrigger's `EntryRep` has **no DER path at all** (plain JOSS-default
>   `MarshalledInstance` construction, `outrigger-dl/.../proxy/EntryRep.java:338,406`);
>   Reggie's exists but is config-gated **default-off** (`useDerForEntries`,
>   `RegistrarImpl.java:5262`, per-relationship via `MarshallingFormat.ATOMIC_DER`
>   constraint). This corrects §11's 2026-07-17 cost note — see the 2026-07-20
>   correction there. Migration scoped as `SOW-Entry-ATOMIC-DER-Migration.md`; the
>   evaluator primitive remains `SOW-CEL-Filter-Format.md`.

> **RATIFIED 2026-07-20 (Peter): the 2026-07-17 format recommendation above is
> adopted.** CEL grammar/semantics as the filter/transform language; self-written,
> minimal, DER-native evaluators — not `cel-java`/`cel-rust` as dependencies.
> Implementation started the same day per `SOW-CEL-Filter-Format.md`, T1 first
> (grammar/semantics spec, as `JGDMS-STD-011-CEL-Filter-Expression-Format`). The
> Rust evaluator (T4) is **deferred, not dropped**: Rust participation is planned,
> sequenced behind a Rust JERI DER implementation, which itself follows the current
> JERI-DER join-manager QA work (in flight 2026-07-20). T5's cross-language
> conformance corpus is what keeps that deferral honest — the Rust evaluator
> validates against it when it lands.

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

> **Researched 2026-07-17: how does the sidecar get permission to load and run a
> filter — two separate mechanisms, not one, following existing precedent rather than
> inventing from scratch.** The question split into (a) a server-side trust/hook point
> before the filter is handed to the sidecar loader, and (b) how a computed permission
> actually lands inside the sidecar's own separate JVM.
>
> **(a) Trust hook — a `filterPreparer` `Configuration` entry, mirroring existing
> `ProxyPreparer` precedent.** `net.jini.security.ProxyPreparer`/`BasicProxyPreparer`
> (`jgdms-platform/src/main/java/net/jini/security/BasicProxyPreparer.java`) is not a
> client-only convention — real services already use it to prepare **client-authored**
> proxies they receive, structurally the same direction as a filter: Mercury prepares
> a client's registered `RemoteEventListener` via a `"listenerPreparer"` config entry
> (`MailboxImpl.java:1157`, read at `MailboxImplInit.java:177-185`); Mahalo prepares a
> client's `TransactionParticipant` via `"participantPreparer"`
> (`TxnManagerImpl.java:712`, `TxnManagerImplInitializer.java:155-161`). A
> `filterPreparer` entry, read the same way, is the idiomatic, low-risk place for a
> service to hook filter trust decisions before load — no new configuration pattern
> to invent. It does **not** need `BasicProxyPreparer`'s `verify()`/`setConstraints()`
> behavior (a filter is `@Stateless`/`@AtomicSerial`, not a `RemoteMethodControl`
> proxy) — only the `Configuration`-entry convention is what's reusable here.
>
> **(b) Permission delivery — `SubProcessDynamicPolicy`, not
> `BasicProxyPreparer.grant()`.** `BasicProxyPreparer.grant()` *does* already perform a
> real dynamic permission grant (`grant()` at `BasicProxyPreparer.java:404-432` calls
> `Security.grant(proxy.getClass(), perms)`, `Security.java:1287-1307`, which requires
> the calling context already hold `GrantPermission` for what it grants and then calls
> `DynamicPolicy.grant(...)` on the **installed policy of the calling JVM**) — but that
> is exactly the wrong locus for this case: the grant decision is made in the
> **service's** JVM, while the permission must land in the **sidecar's** separate JVM,
> across the UDS boundary. `BasicProxyPreparer`/`Security.grant` has no mechanism to
> target a different process's policy at all. The mechanism that already solves that
> exact cross-process topology is `SubProcessDynamicPolicy`
> (`SOW-SubProcessDynamicPolicy.md`), designed this session for the client-side
> smart-proxy sidecar — same shape of problem (verdict-computed grant, pushed into an
> already-running isolated subprocess's own `DynamicPolicyProvider`, over the same
> channel the sidecar is already reached through), now understood to generalize to the
> **server-side filter sidecar too**, not a client-only mechanism. In practice the
> permission set for a filter is expected to be minimal-to-empty given "zero ambient
> authority" (§8.4) — the load-time digest gate (this section) is doing most of the
> real security work, and the grant, if any, is narrow (e.g. nothing beyond what's
> needed to read the DER field-projection socket). `SOW-SubProcessDynamicPolicy.md`'s
> own scope/non-goals should be updated to reflect this generalization rather than
> reading as client-side-only — not yet done as of this note.

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

> **Decided (Peter, 2026-07-17): yes, filter sidecars need the same CPU-affinity
> treatment as the smart-proxy sidecar.** This section had no treatment of
> cache-timing side channels (Flush+Reload/Evict+Reload/Prime+Probe-class) — flagged
> as a gap earlier the same day, now resolved rather than left open. Ruling: filter
> workers for different, mutually-untrusting clients/queries **MUST** run on
> **separate physical CPU cores that do not share L1/L2 cache** — not merely different
> processes. This closes the same gap the smart-proxy sidecar has (§8 and §12 point 2
> of `SOW-Unix-Domain-Socket-JERI-Transport.md`): "zero ambient authority" (no
> network, no filesystem) closes *capability* risk but does nothing about *side-channel
> observation* risk — a sandboxed process with no external access can still Prime+Probe
> a physical core it shares with another mutually-untrusting worker. L1/L2 are the
> operative boundary because they are core-private (or shared only across a single
> core's hyperthread siblings) on essentially all current hardware, unlike L3, which is
> normally shared platform-wide and out of scope for pinning-based defenses; disjoint
> physical-core assignment (hyperthread-aware — two mutually-untrusting workers must
> not land on sibling threads of the same core either) is therefore both necessary and
> sufficient at this layer. Whether the per-query ephemeral lifecycle (this section)
> narrows the exposure window in practice doesn't change the requirement — the ruling
> is unconditional, not lifecycle-dependent. Scope this alongside the filter runtime
> build (§13); it is the same requirement `SOW-Unix-Domain-Socket-JERI-Transport.md`
> §12 point 2 already states for the smart-proxy sidecar, not a separate mechanism to
> design twice.

### 8.5 Cross-language bridge

Because the worker decouples *where the data lives* from *what runtime the filter
needs*, a co-located JVM **filter sidecar** over UDS lets even a non-JVM data
service (e.g. Rust) offer Java-filter pushdown without the data process ever
touching foreign code. This partially lifts the §9.2 "cannot accept remote functions"
floor.

> **[OPEN, flagged 2026-07-17] Spawning mechanics when the *host* is non-JVM are
> unscoped — this paragraph asserts the pattern, it doesn't design it.** Everything
> this section (and `SOW-Unix-Domain-Socket-JERI-Transport.md` §12 point 3, and
> `SOW-SubProcessDynamicPolicy.md`) has designed so far assumes a **JVM parent**
> spawning a JVM sidecar — trivial with `ProcessBuilder`, DGC lifecycle riding JERI's
> own wire protocol, `SubProcessDynamicPolicy`'s grant delivered over a channel both
> ends already speak natively. A Rust (or other non-JVM) *data-service* host spawning
> a JVM *filter sidecar* raises several distinct, currently-unanswered questions:
>
> - **Process spawn itself is not the hard part** — any language can shell out to
>   launch a `java` process (Rust's `std::process::Command` is the direct analogue of
>   `ProcessBuilder`). The **JVM launch command/flags must be a fixed, trusted
>   constant the host cannot be tricked into varying** (e.g. it must not be possible
>   for a compromised or buggy data-plane to launch the sidecar JVM without a
>   SecurityManager, or with a weakened policy) — this is an integrity requirement on
>   the host's own spawn logic, not a JVM-side concern, and isn't designed anywhere
>   yet. Worth considering whether the sidecar should be a **self-verifying, attested**
>   worker image (memory: the role-neutral attested DirtyChai worker concept) rather
>   than one whose safety depends on trusting the parent's launch invocation.
> - **The UDS/DER channel itself is presumably already covered** by the premise that a
>   "Rust JERI implementation" exists at all — STD-006 is explicitly designed to be
>   non-JVM-implementable (§9.2: "a non-JVM service... deals only in DATA-by-value over
>   a wire protocol, classes pre-agreed at both ends"), so a genuine Rust JERI
>   implementation should already carry what's needed to speak DER/UDS to the sidecar
>   and to the `VerdictRegistry` (a network call regardless of caller language) —
>   **needs verifying, not assuming**, particularly whether such an implementation's
>   DGC support (dirty-set/lease calls) is faithful enough for the §8.4/UDS-SOW-§12-
>   point-7 lifecycle model to work unmodified from a non-JVM peer.
> - **`SubProcessDynamicPolicy`'s grant wire-shape must be DER-encodable and
>   Rust-constructible**, not merely Java-constructible — a `Permission[]`-shaped
>   payload originating in a Rust process is a real interop requirement this SOW set
>   has not yet had to consider (everything so far assumed a JVM on both ends of every
>   grant-delivery call).
> - **JVM startup cost may dominate the pushdown's own rationale.** §8's whole
>   premise is bandwidth: "evaluate at the data, ship only matches" — if the sidecar is
>   a fresh JVM spawned per query (§8.4's current lifecycle model) on a
>   resource-constrained non-JVM host (e.g. an embedded target), JIT warmup/classload/
>   heap-reservation cost could plausibly exceed the cost of just shipping the
>   candidate data, defeating the entire point. This makes STD-009's own still-open
>   §14 item ("Filter sidecar lifecycle: per-query vs pooled-per-digest") **more
>   load-bearing in the cross-language case than in the pure-JVM case**, not just an
>   optimization — a pooled, kept-warm sidecar (with CDS/AOT as a further mitigation
>   lever, per existing DirtyChai build practice) is plausibly close to *required*
>   here, not optional. None of this is decided; flagged for whoever picks this up.
>
> **Proposal raised 2026-07-17 (Peter): ship a stripped-down JVM as the sidecar
> image, addressing the startup-cost point directly.** Directionally sound and
> well-trodden — this is the same "shrink + prewarm" toolkit already used to make
> Java viable for cloud/serverless cold-start (AWS Lambda's Java runtime and
> similar), not a novel technique. Two distinct things hide under "stripped down
> JVM," and picking the wrong one would silently break the SM/POLP layer this whole
> design leans on:
>
> - **`jlink` custom runtime image (the fit).** `jlink` prunes at *module*
>   granularity only — it cannot remove classes from `java.base`, where
>   `SecurityManager`/`AccessController`/DirtyChai's own SM patches live — so a
>   `jlink`'d DirtyChai image is still a real, full-featured, SM-capable JVM, just
>   without `java.desktop`/`java.naming`/`java.sql`/etc. Given the filter's own
>   "zero ambient authority" role (§8.4 — no network, no filesystem beyond the UDS
>   socket), the module set it actually needs is plausibly close to `java.base` plus
>   whatever the DER/UDS transport requires — very little else. Combine with a
>   dedicated CDS/AppCDS archive built for that exact module set (the same
>   already-standard DirtyChai build practice, just re-scoped) for both smaller
>   footprint and faster boot.
> - **AOT/native-image (GraalVM Native Image or similar) — likely the wrong fit,
>   flagged not ruled out with full certainty.** A fundamentally different
>   execution substrate, not a DirtyChai-fork artifact at all, historically with
>   limited-to-no support for installing a real, dynamic `SecurityManager` the way
>   DirtyChai's HotSpot-based fork does. Choosing this would mean re-deriving this
>   entire session's SM/POLP-dependent analysis for a different runtime — a much
>   larger undertaking than `jlink`, and probably a non-starter given how load-
>   bearing SM is throughout this design (`SOW-BAE-Timing-Sidechannel-Denial.md`
>   §1b). Needs an explicit current-version check before ruling out entirely, but
>   `jlink` is the safer default direction.
> - **`CRaC` (Coordinated Restore at Checkpoint) as a further lever for the
>   per-query-ephemeral lifecycle specifically** — pre-warm a sidecar with the
>   filter-loading machinery already JIT'd/initialized, checkpoint it, then restore
>   (fork) a fresh instance per query near-instantly instead of a cold boot. Worth
>   investigating for §14's lifecycle question, but needs its own security review
>   before adoption, not just accepted at face value — a checkpointed process image
>   is itself a sensitive artifact (what state does it retain across restores; does
>   anything need scrubbing) and CRIU-based restore has its own attack surface.
> - **A real, currently-open landmine directly relevant to choosing `jlink`:**
>   `SOW-dirtychai-digest-codesource-locale-hazard-2026-07-06.md` (DirtyChai,
>   ADVISE-ONLY, not yet fixed) documents that `DigestCodeSource.computeDigest`'s
>   `file:`-directory path lazily initializes the CLDR locale provider via
>   `Collator.getInstance()` — **not SM-safe**, and it fires on the **first class
>   load**, aborting boot entirely under `-Djava.security.manager=default`. The
>   stock full "product image" hides this only because its CDS archive happens to
>   pre-materialize the CLDR/`Collator` classes at dump time; the hazard is real
>   whenever CDS is off or the archive doesn't cover those classes. **A custom,
>   module-pruned `jlink` image is exactly the deployment shape most likely to run
>   without a matching archive** — it needs its *own*, separately-built and
>   kept-valid CDS archive (a stock JDK's default archive doesn't apply to a
>   different module set), and any drift, rebuild-without-regeneration, or missing
>   archive silently reintroduces total bootstrap failure under SM. **This SOW
>   should land in DirtyChai before any `jlink`'d sidecar image ships to
>   production** — not an incidental detail, a real dependency.
> - **Integrity framing.** Once the sidecar ships as a *custom runtime image*, not
>   just custom application code, that image itself becomes part of the trust
>   boundary — it should be built via the same trusted DirtyChai pipeline and be an
>   attested, integrity-verified artifact in its own right (the existing
>   role-neutral attested-worker concept), not a separately-blessed, less-scrutinized
>   build variant.
> - **Honesty check for the embedded/constrained-hardware case.** A `jlink`'d image
>   is still a real JRE — real tens-of-MB-plus disk footprint and real heap, not
>   comparable to a `musl`-static Rust binary's footprint. It narrows the gap
>   significantly versus a full JDK; it does not close it. If the target hardware is
>   constrained enough that this still doesn't fit, that's a different, harder
>   question this note doesn't resolve.
>
> **Further proposal raised 2026-07-17 (Peter): a small JVM-bytecode interpreter
> written in Rust, with stripped Java libraries, instead of shipping a `jlink`'d
> DirtyChai image. Recorded here as a MAJOR OPEN DESIGN FORK — genuinely interesting,
> deliberately NOT decided, and not to be treated as a direction by default.** This is
> categorically bigger than the `jlink`-vs-`native-image` question above (a build
> configuration choice); this is "build a new, core-TCB, security-critical execution
> engine." Real considerations on both sides, not a rubber stamp either way:
>
> - **The genuinely strong argument for it:** if the interpreter's instruction/method
>   set is restricted **by construction** (no I/O opcodes, no reflection, no native
>   calls ever implemented), "zero ambient authority" (§8.4) stops being a runtime-
>   enforced property (`SecurityManager`/policy checks against a general-purpose
>   runtime that *could* do more) and becomes true by omission — the same paradigm
>   eBPF's verifier and WebAssembly's capability-free sandbox use. That is arguably a
>   **stronger** security property for this narrow case than the JVM-sidecar's own
>   SM/POLP-based model, not a weaker one. It also sidesteps the CDS/CLDR/SM bootstrap
>   hazard above entirely (no `SecureClassLoader`/`DigestCodeSource`/locale-service
>   path to have a hazard in), and BAE's static bytecode analysis stays reusable
>   either way — it parses standard `.class` files independent of what executes them.
> - **The genuinely real risks, stated plainly:** (1) it discards DirtyChai's mature,
>   carefully-reasoned `SecurityManager`/`DynamicPolicy`/`PermissionCollection`/
>   `LeasedPermissionGrant` machinery and requires an equivalent trust model built
>   from scratch, in a different language, by a much smaller team than builds
>   production JVMs — the "safe by construction" argument above only holds if the
>   restricted instruction set is actually complete and actually enforced everywhere,
>   a new correctness claim to prove, not one inherited for free. (2) Bytecode/
>   classfile-verifier correctness is the single hardest, highest-stakes part of any
>   JVM, and a from-scratch implementation concentrates risk exactly there — Rust's
>   memory safety (no buffer overflow/use-after-free in the interpreter's own code) is
>   real and valuable, but is **not** the same property as interpreted-language type
>   safety (correct operand-stack/constant-pool/type-flow handling), and does not
>   protect against logic bugs or whatever `unsafe` blocks a real interpreter likely
>   needs somewhere. Conflating "written in Rust" with "verifier-sound" would be the
>   wrong lesson to take from this. (3) This is a categorically larger, more
>   open-ended engineering commitment than a build-configuration change — closer in
>   scope to research-grade alternative JVMs (Avian, JamVM, CACAO; none
>   production-security-hardened) than to `jlink`'ing an existing runtime — worth
>   weighing explicitly against this project's own standing "lean resourcing" /
>   "do the minimum you can do confidently" principles before committing engineering
>   capacity to building and *maintaining* it indefinitely.
> - **A further, distinct fork worth naming rather than silently folding in:** does a
>   filter need to *be* standard JVM bytecode at all? Given RULE-F1/F2/F3 already
>   restrict a filter to `@Stateless`, `@AtomicSerial`, pure `(candidate, params) →
>   boolean` with no captured state, a purpose-built restricted expression/predicate
>   format — not JVM classfiles — might be an even smaller, more auditable target than
>   "a JVM, just small." That would be a real departure from this section's current
>   model (a real compiled Java class extending `AbstractRemoteFunction`), not an
>   implementation detail, and needs to be recognized as its own decision if raised
>   again, not conflated with "which interpreter do we ship."
> - **Recommendation, not a ruling:** this deserves a dedicated feasibility/scoping
>   pass of its own — separate from, and after, the `jlink`/CDS work above — before
>   being treated as a direction. Nothing above is decided.

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

> **Status update 2026-07-17.** "Out of scope for this standard" above is now only
> accurate for the **filter** tenant and the **ServiceUI kill-switch/disjoint-sidecar
> composition** material specifically — both remain genuinely open, untouched by the
> newer work. For the **smart-proxy** tenant, "the delegation credential model" and
> "full sidecar/worker design" this paragraph defers now have a concrete task-level
> answer: `SOW-Unix-Domain-Socket-JERI-Transport.md` §12 (routing, lifecycle, the
> `-api`/`-dl`-as-process-boundary mechanics this section already anticipated almost
> exactly) and `SOW-SubProcessDynamicPolicy.md` (the "attenuated, leased delegation"
> problem two paragraphs above, named here but left unbuilt — now a six-task SOW
> reusing `LeasedPermissionGrant`/`LeasedDelegation`). The lifecycle model differs by
> design from the filter's per-query teardown (§8.4): a smart proxy is long-lived and
> stateful by nature (§3.3's "any client state"), so the newer work pools one sidecar
> per remote SPIFFE principal and tears it down via JERI's own DGC (dirty-set/lease)
> liveness once nothing holds a live reference — not per-call. This is a different
> lifecycle answer for a different tenant, not a revision of §8.4's filter answer,
> which stays whatever §14's own still-open item resolves it to.

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

> **Researched 2026-07-17: does a CEL-shaped grammar (§8's format-recommendation
> note) actually cover what these two consumers need? Yes on vocabulary; the "rides
> existing infrastructure" claim above needs correcting.** Checked directly against
> both services' real matching code, not assumed from this section's prose:
>
> - **Matching in both services today is 100% opaque-byte equality — no ranges, no
>   inequality, no ordering exists anywhere in either codebase currently.** Nothing
>   found exceeds CEL's comparison/boolean/string-operator vocabulary — no aggregate,
>   no cross-record computation, nothing needing loops or recursion. CEL-shaped is
>   sufficient for everything found.
> - **This paragraph's "rides the existing Blitz field-index... exactly the §8.4
>   field-projection" claim is only half true, and overstates it for Outrigger
>   specifically.** `EntryRep` (`services/outrigger/outrigger-dl/.../EntryRep.java:77`)
>   stores **every** field — including numeric/`String` fields — as
>   `MarshalledInstance[]`; matching (`EntryRep.matches()`, lines 916-940) is raw
>   serialized-byte equality (`MarshalledInstance.equals`,
>   `jgdms-platform/.../MarshalledInstance.java:914-922`); `EntryFieldIndex`
>   (`services/outrigger/outrigger-service/.../EntryFieldIndex.java:107-215`) is a
>   hash-bucket accelerator over `MarshalledInstance.hashCode()`, not a typed-value
>   index — it narrows equality candidates, it does not decode types. A numeric field
>   is, at this layer, indistinguishable from an opaque blob. **Typed field-projection
>   for a CEL evaluator to operate on does not exist yet in Outrigger — it is new
>   matching-runtime work, not something to expose from existing infrastructure.**
> - **Reggie is materially better positioned, but not fully there either.**
>   `EntryRep.fields()`/`needsMarshal()`/`IMMUTABLE_TYPES`
>   (`services/reggie/reggie-dl/.../proxy/EntryRep.java:103-105,163-179,219-238`) leave
>   `String`/`Integer`/`Boolean`/`Long`/etc. as real, unwrapped, typed values — only
>   non-primitive-wrapper attribute types get `MarshalledWrapper`-boxed (itself
>   another opaque-byte-equals comparison, `MarshalledWrapper.java:321-325`). So
>   range/string-operator predicates over Reggie's common immutable-typed attribute
>   fields need no new deserialization plumbing — a real, usable asymmetry with
>   Outrigger — but non-immutable attribute types hit the same opaque-blob problem.
> - **"Computed" (this section's own phrase, "ranges, inequality, compound,
>   computed") is undefined anywhere in this document set.** Checked every other
>   `docs/` reference to the word — none define it in a predicate/filter sense
>   (`DESIGN-CorroborationFramework.md`'s usage is unrelated, belief-fidelity tiers).
>   Left as an open term needing a concrete example before it can be scoped — not
>   guessed at here.
>   **Concrete example confirmed 2026-07-17** (Peter, Survey-zoot application
>   discussion, see `DESIGN-CorroborationFramework.md` §8's cross-pointer note): a
>   bounded, pure, fixed-formula **value transform**, not just a boolean predicate —
>   e.g. converting raw bearing/elevation/distance into vector components at an
>   instrument, using CEL arithmetic plus a small, fixed, platform-audited set of
>   custom functions. The *application* (instrument-level filter/transform pushdown at
>   the F0 tier) is confirmed as a real fit; whether this is precisely what this
>   section's original 2026-07-03 author meant by "computed" is still not verifiable
>   (no record of their intent exists) — treat "computed ⊇ bounded value transforms" as
>   settled scope going forward, not as archaeology of the original phrase.
> - **Correction to how this section's SOW should be scoped, once written:** "build a
>   filter" is not primarily a grammar/language problem (CEL-shaped is sufficient) —
>   it is primarily **new typed-field-projection matching-runtime work in both
>   services**, disproportionately larger in Outrigger (every field opaque today)
>   than in Reggie (partial typed carve-out already exists). Whoever scopes §11 into
>   a real task breakdown should treat that as the actual first-class task, not a
>   rider on existing infrastructure the way this section currently implies.
>
> **Corrected again 2026-07-20 — the bullet above located the cost in the right
> services but the wrong layer.** "New typed-field-projection matching-runtime work"
> overstated what needs inventing: the typed-projection substrate **already exists on
> trunk** in `jgdms-der` (`ObjectCodec.decodeToFieldMap` — class-free
> `className → (fieldName → value)` decode driven by the schema embedded in every
> `ATOMIC_DER` `MarshalledInstanceRecord`; see §8's 2026-07-20 verification note for
> the full mechanism and evidence). Given `ATOMIC_DER` bytes, field names and typed
> values (strings, numerics, collections) are recoverable with **no class and no
> constructor**. The real gap is **format adoption by the entry paths**: Outrigger's
> `EntryRep` marshals every field through the JOSS-default `MarshalledInstance`
> constructor (no DER path exists), and Reggie's DER entry path exists but defaults
> off (`useDerForEntries = false`). So the first-class task is the `ATOMIC_DER`
> migration of entry marshalling (plus a lazy field projector and the matching-path
> wiring), not the invention of typed field storage — scoped in
> `SOW-Entry-ATOMIC-DER-Migration.md`.

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
