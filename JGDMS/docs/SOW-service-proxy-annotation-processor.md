# SOW / Design — Service Proxy Annotation Processor

*Status: DRAFT design note. Not yet scheduled. Author: Peter Firmstone + Claude.*

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
case only works if they are not conflated:

| Role | Example (hello-world) | Lives in | Who writes it |
|---|---|---|---|
| **Public API interface** — the clean, client-facing contract | `HelloService` (`greet`) | `-api` jar | developer |
| **Internal service interface** — the remote (wire) methods the exported stub implements and the proxy invokes on `server`; may be coarser/finer than the public API | Reggie's `Registrar` vs its public `ServiceRegistrar` | `-dl` jar | developer (or defaults to the public API) |
| **Constrainable wrapping** — the (sole) `RemoteMethodControl` proxy | `ConstrainableHelloServiceProxy` | `-dl` jar | **generated** |

A **smart proxy** is a proxy that carries its own implementation (client-side
behaviour and/or state) rather than forwarding one-to-one. When the proxy is
smart, the public API and the internal service interface genuinely differ: the
proxy *translates* public-API calls into internal-interface calls (batching,
caching, local computation, partial-failure handling, retry/failover), so the two
interfaces must be modelled independently.

### 2.1 Design decision — every generated proxy is constrainable

The processor generates **only** the constrainable proxy; there is no
non-constrainable variant. Rationale:

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
closed** — if `server` is not a `RemoteMethodControl` it throws rather than
degrading to a plain proxy. `AbstractSmartProxy` is retained as the shared
server/`proxyID`/`ReferentUuid`/`Administrable` base (`ConstrainableSmartProxy
extends AbstractSmartProxy`); we simply never generate a non-constrainable
*concrete* subclass. Because there is a single proxy variant, the plain/
constrainable forwarding-duplication problem disappears entirely.

> Follow-up (separate, out of scope here): retire the non-constrainable branch
> from the *existing* hand-written proxies (hello-world, Reggie/Mahalo/Mercury/
> Fiddler), the archetype, and the Part-1 blog. Low risk — the branch is provably
> unreachable for JERI — and aligned with 4.0.0's legacy-removal theme.

## 3. What the processor owns vs. what the developer owns

A single generated proxy (constrainable — see §2.1) removes the plain/
constrainable forwarding-duplication problem. One inheritance constraint remains
relevant to the smart case: APT can **only emit new files, never add members to
the developer's class**, so a developer cannot hand-write methods *into* the
generated proxy. Smart behaviour is therefore supplied through a shared **delegate**
that the generated proxy holds and calls (§5); thin forwarding is generated
directly into the proxy.

**The processor generates (new files only — never edits the developer's class):**

- The **internal service / backend interface**: `<Api>Backend extends Remote,
  <ServiceProtocol>, ServiceProxyAccessor, ServiceAttributesAccessor,
  ServiceIDAccessor, CodebaseAccessor, Administrable, JoinAdmin, DestroyAdmin`.
  Fixed template; omitting one infra interface silently breaks admin-over-wire.
- The **constrainable proxy** `Constrainable<Api>Proxy extends
  AbstractSmartProxy.ConstrainableSmartProxy implements <Api>` — the sole proxy
  variant. It is `@AtomicSerial`; `@Stateless` **iff** the proxy declares no extra
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

```java
// Common case — thin, forwarding proxy. Put on the public API interface.
@JiniService(
    protocol  = HelloService.class,   // internal wire interface (default: the annotated API itself)
    component = "net.example.hello",  // config component for the generated wrapper
    generate  = { BACKEND, PROXY, WRAPPER }   // default: all three
)
public interface HelloService extends Remote {
    String greet(String name) throws RemoteException;
}
```

Thin case: nothing else to write. The processor emits the backend interface, the
plain + constrainable proxy pair (forwarding every API method 1:1 to
`(protocol) server`), and the wrapper.

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

The processor then generates the (single, constrainable) `@AtomicSerial` proxy
**shell** that:

1. serializes `{ server, proxyID }` **plus** any fields the developer declares as
   proxy-serialized state (via `@SmartProxy.State` records — reconstructed through
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

Mirroring `marshaldelegate.validateOnly`, ship a **validate-only** capability
before any generation is trusted. It emits compile warnings/errors for:

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

### 6.1 Load-time companion — a BAE constrainable-proxy visitor

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
   non-`RemoteMethodControl` stub, is `CONSTRAINTS_STRIPPED`.

**SCAP treatment.** Any verdict other than `COMPLIANT`/`NA` is **BLOCKING** — the
codebase is refused before any class loads, fail-secure, matching how BAE already
treats an unreadable class. Together with the validate-only mode above, this covers
both the code we build and the code we receive.

## 7. Verification — golden diff against hello-world

The processor has a built-in oracle: generate the backend interface + proxy pair
for the existing `HelloService`, and **diff against the hand-written
`HelloServiceBackend` / `HelloServiceProxy`** already in the tree and trusted by
the round-trip test. Generation is correct when the emitted source matches
(modulo formatting) the files we already ship. Then flip hello-world to use the
generated types and confirm `HelloServiceProxyRoundTripTest` still passes.

## 8. Module & build placement

- New module `jgdms-service-proxy-processor`, sibling to
  `jgdms-marshal-delegate-processor`; same `META-INF/services/…Processor`
  registration and `javax.lang.model` machinery.
- Inherits the annotation-processor-path build handling already worked out for the
  marshal processor (processor SNAPSHOT must be on the `-pl` reactor path; cap
  emitted class version for the ASM/JDK constraints).
- Annotations (`@JiniService`, `@SmartProxy`) go in a tiny `-api`-side module so
  service interfaces can depend on them without pulling in the processor.

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

1. **P0** — annotations module + `@JiniService`/`@SmartProxy` + validate-only mode.
2. **P1** — backend-interface generation; golden-diff vs `HelloServiceBackend`.
3. **P2** — thin constrainable-proxy generation (single variant, fail-closed
   `create()`, §2.1); golden-diff vs `HelloServiceProxy`'s constrainable variant;
   flip hello-world; confirm round-trip test green.
4. **P3** — smart-proxy shell over a developer delegate (§5), incl. declared
   serialized state through `MarshalDelegateProcessor`.
5. **P4** — optional service-wrapper generation; retire the archetype's three
   `// TODO` proxy/wrapper templates in favour of the annotations.
6. **P5** (BAE module, independent — can land first as pure defence) — the
   `ConstrainableProxyComplianceVisitor` + `ConstrainableProxyVerdict` (§6.1),
   wired into `JarAnalyzer`; non-compliant ⇒ BLOCKING.
