# Implementing a Jini Service

A practical, step-by-step reference for writing a new JGDMS service with
`@JiniService` — covering both proxy shapes: **DYNAMIC** (the common case) and
**SMART** with a `@SmartProxy` delegate (downloaded client-side behaviour).

This is a how-to guide, not a design note or a narrative. For the design
rationale and compliance rules behind the annotations, see
[JGDMS-STD-009](JGDMS-STD-009-Service-RemoteFunction-Annotation-Model-v0.1-DRAFT.md).
For the security story — *why* the processor refuses to compile a fail-open
smart proxy — see the blog post
[`security-baked-into-jvm-smart-proxy.adoc`](security-baked-into-jvm-smart-proxy.adoc).
This doc assumes that story and just tells you what to write.

## Contents

- [The three interface roles](#the-three-interface-roles)
- [Choosing DYNAMIC vs SMART](#choosing-dynamic-vs-smart)
- [Implementing a DYNAMIC service](#implementing-a-dynamic-service)
- [Implementing a SMART service with a delegate](#implementing-a-smart-service-with-a-delegate)
- [`@JiniService` attribute reference](#jiniservice-attribute-reference)
- [Checklist / common mistakes](#checklist--common-mistakes)

## The three interface roles

Every JGDMS service keeps three interface roles distinct (JGDMS-STD-009 §3.1):

| Role | Named by | Lives in | Notes |
|---|---|---|---|
| **Public API** | `@JiniService.api()` | `-api` jar | Pure `Remote` contract, no annotations. What `getServiceInterfaces()` returns and what clients discover. |
| **Internal wire (protocol)** | `@JiniService.protocol()` | `-service` module | The interface(s) the exported server stub actually implements. Defaults to `api()` (the thin, one-to-one case). |
| **Constrainable proxy class** | generated | api's package | Only exists for `proxy = SMART` (STD-009 §6 shape 3). DYNAMIC generates no proxy class at all — the exported JERI stub *is* the client proxy. |

`@JiniService` lives on the **implementation** class, never the API interface:
proxy type, codebase, and config component are deployment choices, so two
implementations of the same API interface may legitimately choose different
values.

## Choosing DYNAMIC vs SMART

- **DYNAMIC** (the default) — behaviour stays on the server; the client holds
  only the interface and calls remotely. Use this unless you have a specific
  reason not to. No proxy class is generated; nothing to hand-write beyond the
  service logic itself.
- **SMART** — behaviour (and optionally durable state) is downloaded and runs
  in the *client's* JVM. Only worth it when client-side behaviour genuinely
  pays for the cost of downloading and trust-verifying mobile code — e.g.
  Reggie's smart proxy does template matching locally so a lookup doesn't
  round-trip per candidate. "It's neat" is not a reason; "it avoids a
  round-trip per call" is.

If in doubt, start DYNAMIC. Moving to SMART later is a proxy-shape change, not
a wire-protocol rewrite, provided you keep the public API interface stable.

## Implementing a DYNAMIC service

This is shape 1 (or shape 2 with `codebase = true` — the interfaces travel but
still no proxy class). Worked example: the real `hello-world` service
(`au.net.zeus.jgdms.hello`).

### 1. Write the API interface

A pure `Remote` contract. No `@JiniService`, no security types, nothing else:

```java
package au.net.zeus.jgdms.api.hello;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface HelloService extends Remote {
    String sayHello(String name) throws RemoteException;
}
```

The framework appends the non-`Remote` admin interfaces
(`Administrable`/`JoinAdmin`/`DestroyAdmin`) and the bootstrap accessors
(`ServiceProxyAccessor`/`ServiceIDAccessor`/`ServiceAttributesAccessor`/
`CodebaseAccessor`) to the exported stub itself — you never declare them here.

### 2. Write the implementation

```java
package au.net.zeus.jgdms.hello;

import java.rmi.RemoteException;
import net.jini.activation.arg.ActivationID;
import au.net.zeus.jgdms.api.hello.HelloService;
import au.net.zeus.jgdms.service.annotation.JiniService;
import au.net.zeus.jgdms.service.support.AbstractJiniService;
import org.apache.river.start.lifecycle.LifeCycle;

@JiniService(
        api       = HelloService.class,         // the service (remote) API interface
        component = "au.net.zeus.jgdms.hello")  // config component for the service wrapper
public class HelloWorldServiceImpl
        extends AbstractJiniService
        implements HelloService {

    static final String COMPONENT = "au.net.zeus.jgdms.hello";

    public HelloWorldServiceImpl(ActivationID activationID, String[] data)
            throws Exception {
        super(activationID, data, COMPONENT, HelloService.class,
                HelloWorldServiceImpl.class);
    }

    public HelloWorldServiceImpl(String[] configArgs, LifeCycle lifeCycle)
            throws Exception {
        super(configArgs, lifeCycle, COMPONENT, HelloService.class,
                HelloWorldServiceImpl.class);
    }

    @Override
    public String sayHello(String name) throws RemoteException {
        getReadyState().check();
        return "Hello, " + name + "!";
    }
}
```

That's the whole service. `proxy = DYNAMIC` is the default, so it's omitted.
Note what you do **not** override:

- `createProxy(Object stub, Uuid serviceUuid)` — the `AbstractJiniService`
  default returns `stub` unchanged, which is correct for DYNAMIC: the exported
  JERI dynamic proxy *is* the client proxy.
- `getServiceInterfaces()` — reads `api()` off `@JiniService` for you.

Both convenience constructors (`(ActivationID, String[])` for Phoenix
activation, `(String[], LifeCycle)` for `ServiceStarter`) build a
`DefaultJiniServiceParameters` and delegate to
`AbstractJiniService(JiniServiceParameters, LifeCycle)`. If your service needs
extra config entries, subclass `JiniServiceParameters` and call that
constructor directly instead.

### 3. Configuration — zero-config for development

A DYNAMIC service needs **no `serverExporter` entry at all** to run in
development: the `JiniServiceParameters` default is a `BasicJeriExporter` over
plain TCP with `net.jini.jeri.DynamicILFactory`, which already carries the
admin interfaces onto the exported stub.

```groovy
'au.net.zeus.jgdms.hello' {
    initialLookupGroups     = [''] as String[]
    initialLookupLocators   = [] as net.jini.core.discovery.LookupLocator[]
    initialLookupAttributes = [] as net.jini.core.entry.Entry[]
}
```

For production, add an explicit `serverExporter` over SSL/TLS with your own
constraints — `DynamicILFactory`'s last constructor argument is still the
extra (admin) interface set:

```groovy
serverExporter = new BasicJeriExporter(
    SslServerEndpoint.getInstance(0),
    new DynamicILFactory(
        new BasicMethodConstraints(
            new InvocationConstraints([Integrity.YES, ClientAuthentication.YES], null)
        ),
        null,
        HelloService.classLoader,
        [Administrable, JoinAdmin, DestroyAdmin] as Class[]
    ),
    false, true
)
```

### 4. What gets generated

Nothing. `ServiceProxyProcessor` sees `proxy = DYNAMIC` with `protocol == api`
and emits no backend interface and no proxy class (STD-009 §6 shape 1). With
`codebase = true` the interfaces themselves become downloadable (shape 2), but
there is still no generated proxy class — a DYNAMIC service never needs one.

## Implementing a SMART service with a delegate

This is shape 3 — the *only* shape that generates a constrainable proxy
*class*. Use it when client-side behaviour must be downloaded and run in the
receiver's JVM.

### 1. Write the public API and the internal protocol

Keep them distinct when the smart proxy *translates* — the client sees a
different (usually coarser, or differently named) contract than the wire:

```java
public interface RegionTemperatureService extends Remote {
    double smoothedCelsius(String stationId) throws RemoteException;
}

public interface RegionProtocol extends Remote {
    double rawCelsius(String stationId) throws RemoteException;
}
```

`RegionServiceImpl` implements only `RegionProtocol` (the wire interface) —
`RegionTemperatureService` never appears on the wire at all. Because of that,
`api()` **must** be declared explicitly on `@JiniService` when it's a
translating SMART service with a non-empty `protocol()`: it cannot be inferred
from an implementation that only implements the wire type.
`AbstractJiniService` enforces this — a translating SMART service with an
empty `api()` fails construction with `IllegalStateException`, not a silent
misadvertisement.

If your smart proxy does *not* translate (protocol equals api, or is left
unset), you can skip writing a separate wire interface entirely.

### 2. Write the `@SmartProxy` delegate — single protocol interface

The delegate is a plain class (not `Remote`, not the serialized proxy, no
security types) implementing the public API and holding the typed server
stub. An annotation processor can only emit *new* files, never add members to
your class, so this is how you supply client-side behaviour:

```java
package com.example.region;

import java.rmi.RemoteException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import au.net.zeus.jgdms.service.annotation.SmartProxy;

@SmartProxy
public final class RegionSmartLogic implements RegionTemperatureService {

    private final RegionProtocol server;                 // the wire interface
    private final Map<String, MovingAverage> smoothed;    // client-side state, rebuilt lazily

    public RegionSmartLogic(RegionProtocol server) {
        this.server   = server;
        this.smoothed = new ConcurrentHashMap<>();
    }

    @Override
    public double smoothedCelsius(String stationId) throws RemoteException {
        MovingAverage avg = smoothed.get(stationId);
        if (avg != null && avg.isFresh()) {
            return avg.value();                           // served client-side, no wire call
        }
        double raw = server.rawCelsius(stationId);        // cache miss: one wire call
        return smoothed.computeIfAbsent(stationId, k -> new MovingAverage()).add(raw);
    }
}
```

Because there is exactly **one** protocol interface here, the delegate ctor
names it directly (`RegionProtocol`) — it's the developer's own declaration,
so there's nothing to guess.

### 3. Write the `@SmartProxy` delegate — multiple protocol interfaces

When `@JiniService.protocol()` names *more than one* interface, the framework
generates an aggregate `<Api>Backend` that extends all of them (plus the
infrastructure accessors) so you never hand-write a combining super-interface.
**That generated interface's name is an implementation detail you cannot
know in advance** — it doesn't exist until this same compilation generates
it. Do not try to guess it. Instead, declare the delegate constructor's first
parameter as `java.rmi.Remote` and cast internally to whichever protocol
interface(s) you actually need:

```java
package multi;

import java.rmi.RemoteException;
import au.net.zeus.jgdms.service.annotation.SmartProxy;

@SmartProxy
public final class HelloSmartLogic implements HelloService {

    private final WireA a;
    private final WireB b;

    public HelloSmartLogic(java.rmi.Remote server) {
        this.a = (WireA) server;   // the aggregate backend implements every protocol
        this.b = (WireB) server;
    }

    @Override
    public String greet(String name) throws RemoteException { return a.greet(name); }

    @Override
    public int ping() throws RemoteException { return b.ping(); }
}
```

```java
@JiniService(api = HelloService.class,
        protocol = { WireA.class, WireB.class }, smartProxy = HelloSmartLogic.class)
public class HelloServiceImpl implements WireA, WireB { ... }
```

No `proxy = ProxyType.SMART` needed — declaring `smartProxy()` (or, as here, a
translating `protocol()`) already implies it; either the delegate or a distinct
protocol is a strong enough signal that `proxy()` would just be restating what's
already implied. See [the attribute reference](#jiniservice-attribute-reference)
for the one case `proxy()` must still be stated explicitly.

This is always valid: every protocol interface extends `Remote` by
definition, so `Remote` is always a common supertype, and — unlike the
generated backend name — it is always resolvable regardless of
annotation-processing round order. If you get the delegate constructor wrong,
the processor's error message will tell you to declare `Remote`, not the
internal generated type.

The one exception: if you *hand-write* the aggregate backend yourself
(`@JiniService` accepts a hand-written backend instead of generating one),
you already know its name and may declare the delegate constructor against it
directly.

### 4. Durable proxy state (optional)

Most smart proxies serialize only `{ server, proxyID }` — caches and other
transient state are rebuilt lazily on the client, and the generated shell
stays `@Stateless`. If the delegate needs state that must **survive the
wire** (chosen at export time, not rebuilt lazily), declare it with
`@SmartProxy.State`:

```java
@SmartProxy
@SmartProxy.State(name = "label", type = String.class)
public final class LabelledLogic implements LabelledThermo {
    private final RawThermo server;
    private final String label;

    public LabelledLogic(RawThermo server, String label) {   // state param(s) AFTER server
        this.server = server;
        this.label  = label;
    }
    ...
}
```

The processor generates the `serialForm()`/`serialize()`/`(GetArg)` plumbing
and threads `label` through the wire. **The declared value is passed to the
delegate constructor unvalidated by the shell** — the delegate constructor is
the validation seam. Throw `IllegalArgumentException` (or
`java.io.InvalidObjectException` to signal a corrupt stream) to reject a bad
value; the atomic engine surfaces the throw and the object is never published.

For more than one durable field, wrap them in `@SmartProxy.States` (a bare
`@State` is not `@Repeatable`), in constructor-parameter order:

```java
@SmartProxy
@SmartProxy.States({
    @SmartProxy.State(name = "label", type = String.class),
    @SmartProxy.State(name = "port",  type = int.class)
})
public final class LabelledPortLogic implements ... { ... }
```

### 5. Wire it into the service implementation

```java
@JiniService(
        api        = RegionTemperatureService.class,
        codebase   = true,
        protocol   = RegionProtocol.class,
        smartProxy = RegionSmartLogic.class,
        component  = "com.example.region")
public class RegionServiceImpl
        extends AbstractJiniService
        implements RegionProtocol {

    @Override
    public double rawCelsius(String stationId) throws RemoteException {
        return readSensor(stationId);
    }
}
```

That's it — **no `createProxy` override needed**. `AbstractJiniService`'s
default `createProxy(Object, Uuid)` already knows, from `@JiniService.proxy()`,
that this is a SMART service; it resolves the generated
`Constrainable<Api>Proxy` class *reflectively*, by the same deterministic
naming convention the processor itself uses (`"Constrainable" +
api.getSimpleName() + "Proxy"`, in the api's package — off
`getServiceInterfaces()[0]`, the already-resolved primary API interface), and
invokes its static `create(server, proxyID, ...)` factory
(`AbstractSmartProxy.createFor`). The generated class name never has to appear
in your source. If `stub` isn't a `RemoteMethodControl` (not exported with a
constrainable endpoint), the generated factory throws — it never returns a
plain, constraint-dropping proxy.

**Stateful delegate?** Override `smartProxyStateArgs()` instead of
`createProxy` — return the durable values, in `@SmartProxy.State` declaration
order:

```java
@Override
protected Object[] smartProxyStateArgs() {
    return new Object[]{ "degC" };   // e.g. the label a stateful delegate needs
}
```

Note the tradeoff: `smartProxyStateArgs()` returns an untyped `Object[]`, so a
wrong type or count for the state values surfaces as a runtime
`IllegalStateException` at service start (reflection), not a `javac` compile
error — unlike the stateless case, which has nothing untyped to get wrong. If
you'd rather have the state arguments themselves compile-checked, override
`createProxy` directly instead and call the generated factory by name (see
below); either is a `protected` extension point, not a fixed contract.

Overriding `createProxy` directly remains available — for example, a
hand-written smart proxy outside the `@JiniService`/`@SmartProxy` codegen path,
or the compile-checked-state-args alternative above:

```java
@Override
protected Object createProxy(Object stub, Uuid serviceUuid) {
    return ConstrainableRegionTemperatureServiceProxy.create(
            (RegionProtocol) stub, serviceUuid);
}
```

### 6. What gets generated

For `proxy = SMART` the processor emits, in the api's package:

- the aggregate wire backend (only when `protocol()` differs from `api()`,
  i.e. a translating smart proxy, and only if not hand-written);
- exactly one proxy class: `public final`, `@AtomicSerial`
  (`@Stateless` unless it has `@SmartProxy.State` fields),
  `extends AbstractSmartProxy.ConstrainableSmartProxy`, `implements` every
  `api()` interface, forwarding each method to a delegate instance rebuilt
  from the validated `server` on **every** construction path (the public
  ctor and the `(GetArg)` deserialization ctor);
- a fail-closed `create(server, proxyID[, states...])` factory that throws
  `IllegalArgumentException` rather than degrading to a plain,
  constraint-dropping proxy when `server` isn't a `RemoteMethodControl`.

There is no non-constrainable variant, ever. A hand-written proxy that
extends the plain `AbstractSmartProxy` directly (not
`.ConstrainableSmartProxy`) is a **compile error** — you cannot compile a
fail-open smart proxy.

### 7. Server-side export config is unchanged

The exporter configuration for a SMART service's *server* looks exactly like
a DYNAMIC service's — `BasicJeriExporter` + `DynamicILFactory` (or
`AtomicILFactory`) over your chosen endpoint. `proxy = SMART` only changes
what the *client* receives (the generated proxy class instead of the raw
stub) — it does not change how the server is exported.

## `@JiniService` attribute reference

| Attribute | Default | Meaning |
|---|---|---|
| `api()` | `{}` (infer) | Public service interface(s). Must be declared explicitly for a translating SMART service (`protocol() != api()`, non-empty) — cannot be inferred. |
| `protocol()` | `{}` (= `api()`) | Internal wire interface(s) the exported stub implements. More than one → the processor generates the aggregate `<Api>Backend`. |
| `proxy()` | `ProxyType.DYNAMIC` | `DYNAMIC` or `SMART` — usually **inferred**, not stated: `smartProxy()` or a translating `protocol()` each imply `SMART` on their own (an explicit `proxy = DYNAMIC` contradicting either is a compile error). Only load-bearing on its own for a *thin* `SMART` proxy (`protocol() == api()`, no `smartProxy()`) — see [Choosing DYNAMIC vs SMART](#choosing-dynamic-vs-smart). |
| `codebase()` | `false` | Ship a downloadable `-dl` jar. Independent axis from `proxy()` (STD-009 §6 shape 2 for DYNAMIC + codebase). |
| `component()` | `""` | Config component name passed to `AbstractJiniService`. |
| `smartProxy()` | `Void.class` (none) | The `@SmartProxy` delegate class. Implies `proxy() == SMART`; contradicting that with an explicit `proxy = DYNAMIC` is a compile error. |

`@SmartProxy` (on the delegate class itself) takes no attributes of its own
beyond the nested `@SmartProxy.State`/`@SmartProxy.States` durable-field
declarations — api/protocol wiring lives entirely on `@JiniService`, named
once, to avoid duplicating it on both sides.

## Checklist / common mistakes

- **Translating SMART service with empty `api()`.** Fails fast with
  `IllegalStateException` at construction — declare `api()` explicitly.
- **Delegate doesn't implement every `api()` interface.** Compile error at
  the `@JiniService` element ("must implement its declared api").
- **Delegate constructor names the generated aggregate backend instead of
  `Remote`.** Don't — you can't know that name in advance. See
  [multiple protocol interfaces](#3-write-the-smartproxy-delegate--multiple-protocol-interfaces).
- **`smartProxy()`, or a translating `protocol()`, alongside an explicit
  `proxy = ProxyType.DYNAMIC`.** Compile error — both imply `SMART`; a dynamic
  proxy carries no downloaded behaviour and cannot bridge `api() != protocol()`,
  so an explicit contradiction is rejected rather than silently misbehaving.
  Simplest fix: just remove the explicit `proxy = DYNAMIC` (it's inferred).
- **Hand-written proxy extends `AbstractSmartProxy` directly instead of
  `.ConstrainableSmartProxy`.** Compile error — the fail-open shape is
  refused, not silently accepted.
- **Stateful `@SmartProxy` delegate, but `smartProxyStateArgs()` not
  overridden.** The default returns an empty array — the generated factory
  call fails with `IllegalStateException` at service start (wrong argument
  count), not silently. Override `smartProxyStateArgs()` to supply the actual
  values, in `@SmartProxy.State` declaration order.
- **`@SmartProxy.State` field validated by the shell.** It isn't — the
  generated `(GetArg)` ctor passes the deserialized value straight to the
  delegate constructor unvalidated. Validate it yourself, in the delegate
  constructor, and throw to reject.
