# SOW / Design — Service Proxy Annotation Processor

**Status:** Implementation companion to **JGDMS-STD-009** (partly built — see §0).
**Author:** Peter Firmstone + Claude.
**Normative model:** `JGDMS-STD-009-Service-RemoteFunction-Annotation-Model-v0.1-DRAFT.md`.

---

> **This SOW is the implementation plan; JGDMS-STD-009 is the normative model.**
> STD-009 defines the annotation family, the three proxy shapes, the
> `DYNAMIC`/`FUNCTION` axis, the compliance rules, and the two-separate-bases class
> hierarchy — and is authoritative for all of them. This document plans and tracks
> the `@JiniService` **annotation processor** that realises STD-009 §3–§7; where the
> two might appear to differ, STD-009 governs. Model contracts are *referenced* here,
> not restated. RFC-2119 keywords (MUST / SHOULD / MAY) carry their normative sense.
>
> **Editorial note — refreshed 2026-07-03.** Re-aligned to the design that converged
> in the 2026-07-03 working session and to the code now in-tree. The retired
> `generate = { BACKEND, PROXY, WRAPPER }` selector and its nested `Generate` enum
> were removed throughout (the generated shape is now derived from `proxy × codebase`,
> STD-009 §6); the annotation surface was corrected to the current
> `{ proxy, codebase, protocol, component }` shape; the single-proxy language was
> reframed onto STD-009's three proxy shapes; `@RemoteFunction` is acknowledged as a
> *separate* annotation (its own `{ DYNAMIC, FUNCTION }` enum, no `codebase`, its own
> unrelated base class) whose runtime is a later phase and out of this SOW's build
> scope; and an implementation-status section (§0) was added to record what is built,
> deferred, and later-phase. `bundledInterfaces` was never part of this model and is
> not introduced.

## 0. Implementation status (as of 2026-07-03)

The `@JiniService` processor exists and is green; the model it realises is defined by
STD-009.

**Built and green** (module `jgdms-service-proxy-processor`, 18 tests in
`ServiceProxyProcessorTest`):

- The annotation surface is reshaped to `{ proxy() : ProxyType, codebase() : boolean,
  protocol() : Class, component() : String }` in `jgdms-service-annotations`; the old
  `generate[]` / `Generate` selector is **gone**. `@SmartProxy` (delegate marker) and
  its `@State`/`@States` sub-annotations exist.
- **Shape dispatch for shapes 1 & 3** (STD-009 §6): the backend (wire) interface is
  generated for **every** `@JiniService`; the constrainable proxy **class** is generated
  **only** when `proxy() == SMART` (`ServiceProxyProcessor.processJiniService` gates
  `writeProxy` on `proxyType() == SMART`). A `DYNAMIC` service generates the backend but
  **no** proxy class (it is a runtime `java.lang.reflect.Proxy`). The generated
  `create()` factory is constrainable-only and **fails closed** (throws if `server` is
  not a `RemoteMethodControl`).
- **Validate-only mode** (`-Aserviceproxy.validateOnly`) with the §6 diagnostics.
- Signature fidelity (generics, bounded type vars, varargs, checked exceptions) in the
  emitted forwarding proxy, proven by compiling the generated sources.

**Deferred — awaiting a design decision** (`codebase()` is read and stored but does not
yet gate codegen):

- **Shape 2** (`DYNAMIC` + `codebase` → an interfaces-only `-dl` jar).
- The **SMART `-dl`-vs-shared-codebase packaging** decision.

Both touch established `-dl`-jar conventions (STD-007, the preferred-list analyzer) and
are held pending that design pass. See §8.1.

**Later phase — not in this SOW's build scope:**

- The `@SmartProxy` **shell generation** over a developer delegate (§5, P3): today
  `@SmartProxy` is validate-only regardless of mode.
- Optional **service-wrapper** generation (§10, P4).
- The load-time **`ConstrainableProxyComplianceVisitor`** in the BAE (§6.1, P5).
- Everything under `@RemoteFunction` — the annotation, `AbstractRemoteFunction`, and the
  filter (UDS sidecar) runtime — is a **separate** later phase defined by STD-009 (§8,
  §11) and is **not** part of the `@JiniService` deliverables planned here.

## 1. Problem

Every JGDMS service is built from a fixed set of cooperating types, most of which
are mechanical and derivable from the service's interface. The Maven archetype
(`jgdms-service-archetype`) scaffolds them once, then leaves the developer to keep
them in sync **by hand** — the templates literally carry three
`// TODO: Add all methods from <X>Service, delegating to …` markers (plain proxy,
constrainable proxy, and the service wrapper). Hand-maintaining the same method
list across three sites is exactly what produced the four `AbstractSmartProxy`
round-trip flaws when the hello-world example was first written.

We already accept compile-time code generation as the cure for this class of
boilerplate: `jgdms-marshal-delegate-processor` is a `javax.annotation.processing`
processor that generates per-package `MarshalDelegate`s for `@AtomicSerial`
classes, validates the `@AtomicSerial` contract, and registers results in
`META-INF/services` — **without modifying the annotated class**. This SOW proposes
a sibling processor that does the same for the service-proxy boilerplate.

## 2. The three roles (must stay distinct)

The design turns on keeping three interface/impl roles separate — the smart-proxy
case only works if they are not conflated (STD-009 §4 makes this normative):

| Role | Example (hello-world) | Lives in | Who writes it |
|---|---|---|---|
| **Public API interface** — the clean, client-facing contract | `HelloService` (`greet`) | `-api` jar | developer |
| **Internal service interface** — the remote (wire) methods the exported stub implements and the proxy invokes on `server`; may be coarser/finer than the public API | Reggie's `Registrar` vs its public `ServiceRegistrar` | `-dl` jar | developer (or defaults to the public API) |
| **Constrainable wrapping** — the (sole) `RemoteMethodControl` proxy | `ConstrainableHelloServiceProxy` | `-dl` jar | **generated (for a `SMART` service only — STD-009 §6 shape 3)** |

The third role is a *class* only for a smart proxy. A `DYNAMIC` service has no proxy
class at all: the client invokes it through a runtime `java.lang.reflect.Proxy` over
the interfaces it already holds (STD-009 §6, shapes 1 & 2). The three **proxy shapes**
(§2.2, below, mirroring STD-009 §6) are derived from `proxy × codebase`, not enumerated
by hand.

A **smart proxy** is a proxy that carries its own implementation (client-side
behaviour and/or state) rather than forwarding one-to-one. When the proxy is
smart, the public API and the internal service interface genuinely differ: the
proxy *translates* public-API calls into internal-interface calls (batching,
caching, local computation, partial-failure handling, retry/failover), so the two
interfaces must be modelled independently.

### 2.1 Design decision — the generated proxy class is constrainable-only

When the processor generates a proxy *class* at all (the `SMART` shape — STD-009 §6
shape 3), it generates **only** the constrainable form; there is no non-constrainable
variant. This is STD-009 **RULE-C1/C2** (constrainable-only, fail-closed `create()`);
the rationale is reproduced here because it motivates the processor's fail-closed
`create()` emission:

- **It is not dead weight we're dropping — it is a fail-open path we're closing.**
  Every stub a JGDMS service exports via JERI already implements
  `RemoteMethodControl` by construction:
  `AbstractILFactory.getExtraProxyInterfaces` always adds it, `BasicILFactory`
  adds `RemoteMethodControl` + `TrustEquivalence`, and `AtomicILFactory` inherits
  that (nothing overrides it to drop it). So `server instanceof RemoteMethodControl`
  is *always* true for a JERI export — the non-constrainable branch in today's
  `create()` factories is unreachable in practice.
- **The only way to reach it** is to wrap a non-JERI reference (a raw JRMP
  `java.rmi` stub). The constraint model (`net.jini.core.constraint`,
  `RemoteMethodControl`) arrived in **Jini 2.0 (Davis)**; 1.x used plain JRMP.
  The non-constrainable proxy is that pre-2.0 compatibility accommodation, which
  JGDMS 4.0.0 (JERI-only) no longer requires.
- **Security:** a constrainable proxy lets the client demand
  `ServerAuthentication`/`Integrity`/`Confidentiality` per method and **fail
  closed** (`UnsupportedConstraintException`) when the transport can't meet them.
  A non-constrainable proxy cannot even *express* those requirements — and cannot
  participate in JGDMS proxy-trust verification, which itself requires
  `Integrity` + `ServerAuthentication` on the `ProxyTrust` call. Constrainability
  costs nothing (the stub is always `RMC` regardless of transport; plain TCP just
  can't satisfy the constraints), so there is no reason to offer the weaker form.

Consequences: the generated `create(<Api> server, Uuid proxyID)` factory **fails
closed** (STD-009 RULE-C2) — if `server` is not a `RemoteMethodControl` it throws
rather than degrading to a plain proxy. `AbstractSmartProxy` is retained as the shared
server/`proxyID`/`ReferentUuid`/`Administrable` base (`ConstrainableSmartProxy
extends AbstractSmartProxy`); we simply never generate a non-constrainable
*concrete* subclass. Because there is a single concrete proxy variant, the plain/
constrainable forwarding-duplication problem disappears entirely.

> **Implemented.** `ServiceProxyProcessor.writeProxy` emits exactly this: an
> `@AtomicSerial` (`@Stateless`) `final class Constrainable<Api>Proxy extends
> AbstractSmartProxy.ConstrainableSmartProxy implements <Api>`, with a `create()` that
> throws `IllegalArgumentException` when `server` is not a `RemoteMethodControl`. It is
> emitted only when `proxy() == SMART` (`processJiniService`).

> Follow-up (separate, out of scope here): retire the non-constrainable branch
> from the *existing* hand-written proxies (hello-world, Reggie/Mahalo/Mercury/
> Fiddler), the archetype, and the Part-1 blog. Low risk — the branch is provably
> unreachable for JERI — and aligned with 4.0.0's legacy-removal theme.

### 2.2 The three proxy shapes (STD-009 §6)

The generated shape is derived from `proxy × codebase` — see STD-009 §6 for the
normative definitions; summarised here because the processor's shape dispatch is the
core of this SOW:

1. **`DYNAMIC`, no codebase** → **no** proxy class. A runtime
   `java.lang.reflect.Proxy` over interfaces the client already holds, constrainable by
   JERI construction. *Built* — the processor emits the backend interface and no proxy
   class.
2. **`DYNAMIC` + codebase** → an **interfaces-only** `-dl` jar (the receiver downloads
   the interface classes via `DynamicProxyCodebaseAccessor`); still no generated proxy
   class. **Deferred** — see §0 / §8.1.
3. **`SMART`** (with a codebase, own or shared) → the sole **constrainable proxy
   class** wrapping the `@SmartProxy` delegate; `@AtomicSerial`, fail-closed `create()`.
   *Built* — `writeProxy` emits it, gated on `proxy() == SMART`.

Only shape 3 emits a proxy class, so it is the only shape the load-time BAE visitor
(§6.1) polices. The `codebase` axis (shape 2's interfaces-only packaging, and shape 3's
`-dl`-vs-shared packaging) is **not yet acted on**: `model.codebase()` is read and
stored but does not gate generation, pending the design pass in §8.1.

## 3. What the processor owns vs. what the developer owns

A single generated proxy class (constrainable — see §2.1), for the `SMART` shape only,
removes the plain/constrainable forwarding-duplication problem. One inheritance
constraint remains relevant to the smart case: APT can **only emit new files, never add
members to the developer's class**, so a developer cannot hand-write methods *into* the
generated proxy. Smart behaviour is therefore supplied through a shared **delegate**
that the generated proxy holds and calls (§5); thin forwarding is generated
directly into the proxy.

**The processor generates (new files only — never edits the developer's class):**

- The **internal service / backend interface**, for **every** service:
  `<Api>Backend extends Remote, <ServiceProtocol>, ServiceProxyAccessor,
  ServiceAttributesAccessor, ServiceIDAccessor, CodebaseAccessor, Administrable,
  JoinAdmin, DestroyAdmin`. Fixed template; omitting one infra interface silently
  breaks admin-over-wire.
- The **constrainable proxy** `Constrainable<Api>Proxy extends
  AbstractSmartProxy.ConstrainableSmartProxy implements <Api>` — the sole *concrete*
  proxy variant, generated **only for a `SMART` service** (STD-009 §6 shape 3; a
  `DYNAMIC` service has no proxy class). It is `@AtomicSerial`; `@Stateless` **iff** the
  proxy declares no extra
  serialized state (otherwise a generated `serialForm()` / `serialize(PutArg, …)`
  / by-name reads in the `(GetArg)` ctor for the declared state); it carries the
  `create(<Api> server, Uuid proxyID)` factory, which **fails closed** — returning
  the constrainable proxy (preserving the stub's `getConstraints()`) and *throwing*
  if `server` is not a `RemoteMethodControl` rather than degrading to a plain
  proxy; the protected `(server, proxyID, constraints)` ctor; the `(GetArg)` ctor
  delegating to the base; and `setConstraints` returning a re-constrained copy via
  `getReferentUuid()`.
- Optionally the **service wrapper** `<X>ServiceImpl extends AbstractJiniService
  implements <Api>Backend`: the two constructors (activatable
  `(ActivationID, String[])` and non-activatable `(String[], LifeCycle)`, both
  `super(…, COMPONENT, <Api>.class)`), `createProxy()` returning
  `<Api>Proxy.create(...)`, and `getServiceInterfaces()`.

**The developer owns:**

- The **public API interface** and (if different) the **internal service
  interface**.
- The **business logic** — as a plain POJO (`<X>Impl`) for the server side, exactly
  as the archetype already splits `HelloServiceImpl` (POJO) from
  `HelloWorldServiceImpl` (wrapper).
- For a **smart proxy**, the **client-side logic delegate** (see §5).

## 4. Annotation surface (minimal)

The current `@JiniService` shape (in `jgdms-service-annotations`; see STD-009 §3.1 for
the normative definition) has **four** elements. There is **no** per-artifact
`generate[]`/`Generate` selector — that was retired: what gets generated is derived
from `proxy × codebase` (the three shapes of §2.2 / STD-009 §6), not opted out by hand.
There is likewise **no** `bundledInterfaces` element (STD-009 §3.1 explains why: an old
client *strips* interfaces it cannot use, and the DER schema preserves the full
interface identity for forwarding).

```java
// Common case — a DYNAMIC service the client already has the interface for.
// Put the annotation on the PUBLIC API interface.
@JiniService(
    proxy     = ProxyType.DYNAMIC,    // DYNAMIC (default) | SMART
    codebase  = false,                // ship a downloadable -dl jar?  (default false)
    protocol  = HelloService.class,   // internal wire interface (default: Void → the annotated API)
    component = "net.example.hello")  // config component for the generated wrapper
public interface HelloService extends Remote {
    String greet(String name) throws RemoteException;
}
```

- **`proxy()`** — `ProxyType.DYNAMIC` (default) or `ProxyType.SMART`. Selects whether
  the behaviour is invoked remotely (a `java.lang.reflect.Proxy`, no generated class)
  or downloaded and run locally (a generated constrainable smart-proxy class). This is
  the `@JiniService` enum; `@RemoteFunction` has its **own**, unrelated
  `{ DYNAMIC, FUNCTION }` enum (§4.1).
- **`codebase()`** — whether the service ships a downloadable `-dl` jar. An
  **independent** axis from `proxy()` (STD-009 §3.1, §7): a service may or may not ship
  a codebase regardless of proxy type. *Read and stored today, but not yet acted on for
  packaging* — see §0 / §8.1.
- **`protocol()`** — the internal wire interface the generated proxy invokes on
  `server`; defaults to `Void.class`, which the processor reads as "same as the
  annotated API" (the thin, 1:1 forwarding case).
- **`component()`** — the config component name passed to the generated wrapper's
  `AbstractJiniService` constructors.

Thin `DYNAMIC` case: nothing else to write. The processor emits the backend interface
(forwarding is via a runtime dynamic proxy; no proxy class is generated). For a `SMART`
service it additionally emits the sole constrainable proxy class (forwarding every API
method 1:1 to `(protocol) server`). The wrapper is a later phase (§10, P4).

### 4.1 `@RemoteFunction` is a *separate* annotation (out of this SOW's build scope)

STD-009 defines a second annotation, **`@RemoteFunction`**, for the *other* thing that
can cross a trust boundary — a stateless invocable (a client callback **listener**, or
a server-side predicate **filter**). It is **not** part of the `@JiniService`
deliverables planned here and is called out only so the boundary is clear:

- It has its **own** enum `{ DYNAMIC, FUNCTION }` — a filter is a `FUNCTION`, **not** a
  `SMART` proxy (it ships whole mobile code that runs at the receiver, with no `server`
  to forward to). Do not conflate it with `ProxyType`.
- It has **no `codebase` element**: for a `@RemoteFunction` the codebase is *implied* by
  `FUNCTION` and absent for `DYNAMIC` (STD-009 §7), so a standalone flag would be
  redundant.
- A `@JiniService` **is not** a `@RemoteFunction`, and their base classes are **separate
  and unrelated** — `AbstractJiniService` (the existing
  `services/jgdms-service-support/.../AbstractJiniService.java`) does **not** extend
  `AbstractRemoteFunction`, nor vice versa (STD-009 §12).
- The annotation, `AbstractRemoteFunction`, and the filter (UDS-sidecar) runtime are a
  **later phase** defined by STD-009 §8/§11 — neither exists in-tree yet.

## 5. Smart-proxy case (proxy with an implementation)

When the proxy needs its own behaviour or state, the developer writes a
**client-side logic delegate** — symmetric with the server-side POJO+wrapper split:

```java
// Developer-written: the smart logic. A plain class, NOT the serialized proxy,
// NOT remote. Constructed from the internal-interface stub + any client state.
@SmartProxy(api = HelloService.class, protocol = HelloProtocol.class)
public final class HelloSmartLogic implements HelloService {

    private final HelloProtocol server;      // the internal wire interface
    private final Cache<String,String> cache; // client-side state (not serialized; rebuilt post-deser)

    public HelloSmartLogic(HelloProtocol server) {
        this.server = server;
        this.cache  = new Cache<>();
    }

    @Override public String greet(String name) throws RemoteException {
        String hit = cache.get(name);
        if (hit != null) return hit;                 // served entirely client-side
        String r = server.greetRemote(name).text();  // translate public API -> internal protocol
        cache.put(name, r);
        return r;
    }
}
```

> **Status — later phase (P3), not yet built.** The `@SmartProxy` annotation (with its
> `@State` / `@States` declarations) exists in `jgdms-service-annotations`, and the
> processor **validates** a `@SmartProxy` delegate (it must implement its declared
> `api`). Shell **generation** over the delegate is deferred to P3: today `@SmartProxy`
> is validate-only regardless of processor mode.

The processor then generates the (single, constrainable) `@AtomicSerial` proxy
**shell** that:

1. serializes `{ server, proxyID }` **plus** any fields the developer declares as
   proxy-serialized state (via `@SmartProxy.State` declarations — reconstructed through
   validated atomic deserialization, then fed to the delegate's constructor);
2. reconstructs a `HelloSmartLogic` from the deserialized `server` (typed as
   `HelloProtocol`) after `checkServer(GetArg)` has validated it;
3. delegates every **public API** method to that logic instance;
4. supplies `create()` (fail-closed, §2.1) and `setConstraints`.

Because the smart logic lives in the shared delegate, the developer never writes
the method list into the proxy at all — the duplication that the archetype's
`// TODO` proxy markers represent disappears.

**Serialized-state rule.** Behaviour ≠ serialized state. Most smart proxies have
rich behaviour but serialize only `{ server, proxyID }` (caches/endpoints are
rebuilt lazily on the client) → the shell stays `@Stateless`. Only *durable*
proxy state (config chosen at export time) needs a declared serial form; the
processor then generates the `serialForm()`/`serialize()`/`(GetArg)` plumbing and
routes it through the existing `MarshalDelegateProcessor` (the generated shell is
itself `@AtomicSerial`, picked up in a later APT round).

## 6. Validation mode first (do the minimum, confidently)

Mirroring `marshaldelegate.validateOnly`, the processor ships a **validate-only**
capability (`-Aserviceproxy.validateOnly`) so the annotations can be adopted before
generation is trusted. *Built and green.* It emits compile warnings/errors for:

- a `@JiniService`/`@SmartProxy` API method that does not `throws RemoteException`;
- a hand-written backend interface missing one of the required infra interfaces,
  or not extending `Remote`;
- a hand-written proxy missing a forwarding method present on the API;
- a `create()` factory (or export path) that could yield a non-constrainable
  proxy — i.e. a `server` reference that is not a `RemoteMethodControl` (§2.1);
- a stateful proxy annotated `@Stateless`, or a `@Stateless` proxy that declares
  fields.

This is independently useful (it would have caught the original four flaws) and
lets us adopt the annotations without committing to generation on day one.

### 6.1 Load-time companion — a BAE constrainable-proxy visitor (P5, not yet built)

> **Status — later phase (P5), design only.** The visitor below is **not** built. It
> realises STD-009's **BAE-enforced** rules **RULE-C1** (`NON_CONSTRAINABLE`) and
> **RULE-C2** (`CONSTRAINTS_NOT_APPLIED` — no silent downgrade, fail-closed `create()`);
> STD-009 §10 is authoritative for the verdicts. It lands in the BAE module, can land
> independently of the processor, and complements the compile-time validate-only mode.

The annotation processor only protects code **we** compile. A downloaded proxy jar
is third-party bytecode the processor never saw, so the constrainable-proxy
contract must *also* be enforced at load time — exactly as `@AtomicSerial` is
checked both by `MarshalDelegateProcessor` (compile) *and* by the Bytecode Analysis
Engine's `AtomicSerialComplianceVisitor` (load, inside SCAP). The companion is a
sibling ASM visitor, `ConstrainableProxyComplianceVisitor`, returning a new
`ConstrainableProxyVerdict` in `au.net.zeus.jgdms.api.codebase`, wired into
`JarAnalyzer` per class alongside the existing `AtomicSerialVerdict` /
`ClinitVerdict`. **It lands in the BAE module, not the processor module.**

**Gate — which classes it applies to.** Only smart proxies. Using the jar's
superclass map (BAE already indexes every class for the clinit call-graph), a class
that transitively extends `au/net/zeus/jgdms/proxy/AbstractSmartProxy` is a smart
proxy and must honour the contract; everything else is `NA`.

**Contract checks (bytecode-verifiable, ranked by security value):**

1. **Must be constrainable.** The concrete proxy must reach
   `AbstractSmartProxy.ConstrainableSmartProxy` (hence `RemoteMethodControl`) in its
   superclass chain. A concrete proxy extending `AbstractSmartProxy` *directly* (the
   old plain variant) is `NON_CONSTRAINABLE` — the client cannot impose
   `Integrity`/`ServerAuthentication`/`Confidentiality`, and the proxy cannot take
   part in proxy-trust verification.
2. **`setConstraints` must not silently downgrade** — the strongest check, and one
   the compiler cannot make for downloaded code. `setConstraints(MethodConstraints)`
   must construct and return a *new instance of the same class* with the constraints
   applied. Bytecode that returns `this` (`ALOAD_0; ARETURN`), returns `null`, or
   never invokes the `(…, MethodConstraints)` ctor is `CONSTRAINTS_NOT_APPLIED`: a
   client that calls `setConstraints(Confidentiality.YES)` would believe it hardened
   the call while the proxy quietly ignored it — a downgrade attack.
3. **`getConstraints` / `create` must not strip constraints** — `getConstraints`
   overridden to return empty/null, or a `create()` that does not fail closed on a
   non-`RemoteMethodControl` stub. STD-009 folds this into **RULE-C2**
   (`CONSTRAINTS_NOT_APPLIED`); that verdict is authoritative.

**SCAP treatment.** Any verdict other than `COMPLIANT`/`NA` is **BLOCKING** (STD-009
§10: it marks the codebase `DANGEROUS`) — the codebase is refused before any class
loads, fail-secure, matching how BAE already treats an unreadable class. Together with
the validate-only mode above, this covers both the code we build and the code we
receive.

## 7. Verification — golden diff against hello-world

The processor has a built-in oracle: for the existing `HelloService`, generate the
backend interface (every service) and — for the `SMART` shape — the constrainable proxy
**class**, and **diff against the hand-written `HelloServiceBackend` /
`HelloServiceProxy`** already in the tree and trusted by the round-trip test. Generation
is correct when the emitted source matches (modulo formatting) the files we already
ship. Then flip hello-world to use the generated types and confirm
`HelloServiceProxyRoundTripTest` still passes.

The current in-memory harness (`ProcessorHarness`) already exercises this oracle:
`generatesConstrainableProxy`, `generatedProxyCompiles`, and
`generatedProxyPreservesGenericsVarargsAndThrows` assert on the emitted proxy class and
compile it; `dynamicGeneratesBackendButNoProxyClass` confirms a `DYNAMIC` service emits
the backend and **no** proxy class (the shape-1 golden case).

## 8. Module & build placement

- Module `jgdms-service-proxy-processor`, sibling to
  `jgdms-marshal-delegate-processor`; same `META-INF/services/…Processor`
  registration and `javax.lang.model` machinery. (Built.)
- Inherits the annotation-processor-path build handling already worked out for the
  marshal processor (processor SNAPSHOT must be on the `-pl` reactor path; cap
  emitted class version for the ASM/JDK constraints).
- Annotations (`@JiniService`, `@SmartProxy`, `ProxyType`) live in the small
  `jgdms-service-annotations` module (compiled `--release 8` for widest client reach —
  STD-009 §3) so service interfaces can depend on them without pulling in the processor.

### 8.1 Deferred — the `codebase` / `-dl` packaging design pass

The `codebase()` flag is **read and stored but not yet acted on** (§0, §6). Two
questions are held for a dedicated design decision, because both touch established
`-dl`-jar conventions (STD-007, the preferred-list analyzer):

- **Shape 2** (`DYNAMIC` + codebase): emit an **interfaces-only** `-dl` jar and wire the
  `DynamicProxyCodebaseAccessor` download path. Interfaces MUST be **shared, never
  preferred** (STD-009 RULE-C3), which an interfaces-only jar satisfies by construction.
- **Shape 3 packaging** (`SMART`): whether the generated proxy class ships in the
  service's **own** `-dl` jar (`codebase = true`) or a **shared** proxy codebase
  (`codebase = false`), and how that interacts with `PREFERRED.LIST` generation.

Until then the processor's shape dispatch is complete for *generation* (shapes 1 & 3);
only the codebase-driven *packaging* is outstanding.

## 9. Caveats / non-goals

- **APT emits new files; it never fills in the developer's class.** The contract is
  "generate-and-use," matching the existing POJO+wrapper split — not "annotate your
  impl and we complete it."
- **Signature fidelity is the real work:** generics, bounded type variables,
  varargs, overloads, and checked exceptions must be reproduced exactly in the
  forwarding shells. `javax.lang.model` supports this; it is where the effort lives.
- **Not a DI framework and not a sandbox.** It generates the fixed Jini
  scaffolding only; it does not touch security policy, exporters, or business logic.

## 10. Phasing

Status keys: **[DONE]** built and green; **[DEFERRED]** held for the §8.1 design pass;
**[LATER]** a scheduled later phase, not yet started. See §0 for the summary.

1. **P0** **[DONE]** — `jgdms-service-annotations` (`@JiniService`, `ProxyType`,
   `@SmartProxy`) reshaped to `{ proxy, codebase, protocol, component }` (`generate[]`
   retired) + validate-only mode.
2. **P1** **[DONE]** — backend-interface generation for **every** service; golden-diff
   vs `HelloServiceBackend`.
3. **P2** **[DONE]** — the constrainable proxy **class** for the `SMART` shape (STD-009
   §6 shape 3), constrainable-only, fail-closed `create()` (§2.1); a `DYNAMIC` service
   generates **no** proxy class. Golden-diff vs `HelloServiceProxy`'s constrainable
   variant. *Remaining:* flip hello-world onto the generated types and confirm
   `HelloServiceProxyRoundTripTest` green.
   - **P2a** **[DEFERRED]** — the `codebase`-driven `-dl` packaging: shape 2
     (interfaces-only `-dl` jar) and the SMART `-dl`-vs-shared decision (§8.1).
4. **P3** **[LATER]** — smart-proxy shell over a `@SmartProxy` developer delegate (§5),
   incl. declared serialized state routed through the sibling marshal-delegate
   machinery. Today `@SmartProxy` is validate-only.
5. **P4** **[LATER]** — optional service-wrapper generation; retire the archetype's
   three `// TODO` proxy/wrapper templates in favour of the annotations.
6. **P5** **[LATER]** (BAE module, independent — can land first as pure defence) — the
   `ConstrainableProxyComplianceVisitor` + `ConstrainableProxyVerdict` (§6.1),
   realising STD-009 RULE-C1/C2, wired into `JarAnalyzer`; non-compliant ⇒ BLOCKING.

The `@RemoteFunction` annotation, `AbstractRemoteFunction`, and the filter (UDS-sidecar)
runtime are a **separate** track defined by STD-009 (§8, §11); they are **out of this
SOW's build scope** and not phased here.
