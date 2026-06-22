# SOW — @AtomicSerial Per-Package Delegate Marshalling

Status: design note (not yet implemented). Author: design discussion captured 2026-06.
Scope: how `@AtomicSerial` classes participate in atomic marshalling without reflective
field access, without `setAccessible`/`opens`, and without repeating the class-loading
mistakes catalogued in M. Warres, *Class Loading Issues in Java RMI and Jini Network
Technology*, SMLI TR-2006-149 (`jgdms-platform/.../net/jini/loader/smli_tr-2006-149.pdf`).

---

## 1. Goals and non-goals

**Goals**
- Marshalling interacts with an object **only through methods and constructors** — never
  fields. State goes out through an author-written `serialize`; state comes back in through
  an invariant-enforcing `(GetArg)` constructor.
- Package-private `@AtomicSerial` classes (the normal case for proxy internals) participate
  fully, with **no `setAccessible`-on-fields, no `opens`, no published internal state**.
- The wire form (`serialForm`) is an explicit, author-written **contract** — the argument
  list of the `(GetArg)` constructor — deliberately decoupled from the implementation fields.
- Resolution is correct under JGDMS's real class-loading: codebase/preferred loaders,
  per-codebase version coexistence, and OSGi bundles.

**Non-goals (things we deliberately do NOT do — see §6)**
- No reflective field read or write; no field-injection deserialization (Java serialization's
  core mistake).
- No `opens` of any package; no broad `suppressAccessChecks` grant under POLP.
- No new code-transport or loader-management mechanism. In particular **we do not have OSGi
  manage proxy ClassLoaders** — the failed `jgdms-osgi-proxy-bundle-provider`
  (`ProxyBundleProvider` installing a bundle per codebase) is the anti-pattern.
- We do not re-implement codebase resolution, preferred-class resolution, or boomerang
  handling; we consume already-resolved `Class` objects.

---

## 2. The marshalling contract (author-written, three members)

A migratable `@AtomicSerial` class declares, **per class level** in its hierarchy:

- `public static SerialForm[] serialForm()` — the **wire ABI**: the named arguments (name +
  type, plus `unshared` where needed) that the `(GetArg)` constructor consumes. This is a
  contract, **not** a view of the fields; it is authored even when it happens to coincide
  with the fields, and it may legitimately differ from them (e.g. `AbstractLease` puts a
  `serialFormat` plus a normalized duration/absolute value while the `expiration` field is
  `transient`).
- `public static void serialize(PutArg, T)` — writes exactly those arguments. It is the one
  place that reads the class's own fields, and it does so as in-class code (legal, not
  reflection). It must agree with `serialForm`.
- `T(GetArg)` constructor — reads exactly those arguments **by name with defaults** and
  validates invariants. This is the security boundary; deserialization never bypasses it.

A class level with no own wire arguments is marked `@AtomicSerial.Stateless` and contributes
nothing; the write engine skips it (`ObjOutputStream.writeNewClassDesc`, `@Stateless` guard).

Why all three are author-written: they *are* the contract. The by-name `GetArg` + `serialForm`
ABI is what lets multiple versions of a class interoperate over the wire (added/removed/renamed
args degrade gracefully). Deriving any of them from fields would re-couple the wire to the
implementation — the precise coupling this model replaced Java serialization's field injection
with — and would make a field rename a silent wire break.

---

## 3. The per-package delegate (access broker)

The engine lives in `org.apache.river.api.io`; the `@AtomicSerial` classes are package-private
elsewhere, so the engine cannot reflectively invoke their `serialForm`/`serialize`/ctor without
`setAccessible` (cross-package). The fix is a **public delegate compiled into each package** that
needs one:

```java
public interface MarshalDelegate {                 // engine package, public
    SerialForm[] serialForm(Class<?> c);
    void         serialize(Class<?> c, PutArg arg, Object o) throws IOException;
    Object       create(Class<?> c, GetArg arg) throws IOException, ClassNotFoundException;
    default String packageName() { return getClass().getPackageName(); }
}
```
```java
package org.apache.river.norm.proxy;               // one per package, public
public final class NormProxyDelegate implements MarshalDelegate {
    public SerialForm[] serialForm(Class<?> c) {
        if (c == SetProxy.class)      return SetProxy.serialForm();   // direct, in-package
        if (c == AbstractProxy.class) return AbstractProxy.serialForm();
        ...
    }
    public Object create(Class<?> c, GetArg a) throws IOException, ClassNotFoundException {
        if (c == SetProxy.class)      return new SetProxy(a);
        ...
    }
    // serialize likewise
}
```

Two hops, each on the right side of every access boundary:
- **engine → delegate:** the delegate is `public`, called through the `public` interface —
  an ordinary call, no `setAccessible`, no `opens`.
- **delegate → target:** **same package, same defining loader** — `SetProxy.serialForm()`,
  `new SetProxy(arg)` are plain Java. **No reflection, and no field access.** The delegate
  never touches a field; it only invokes the class's own methods/ctor, which read the class's
  own fields as in-class code.

Direct dispatch (as above) uses **zero reflection**, so under POLP it needs **no reflection
permission at all** — not even `accessDeclaredMembers`, and never `suppressAccessChecks`.

### 3.1 The load-bearing correctness rule: resolve by the *defining* loader

Package-private access is governed by **runtime package** = (package name, **defining class
loader**). TR-2006-149 §4.5 and its footnote 16 (JDK bug 4302406) document the failure: when a
lexical package is split across loaders — some classes local, some downloaded — the classes on
opposite sides are in *different runtime packages* and are **denied access to each other's
package-private members**.

Therefore the delegate MUST be the one **co-loaded with the class it serves** — i.e. resolved
via the **marshalled class's own defining loader** (`obj.getClass().getClassLoader()` on write;
the stream-resolved `Class`'s loader on read). A delegate found via any other loader (e.g. a
local delegate for a downloaded class) would be in a different runtime package and would hit
exactly the §4.5 wall. This is why "one physical delegate per package, loaded with its classes"
is mandatory and cannot be a shared base class in another package (the reflective/direct call's
caller would then be the wrong runtime package).

---

## 4. Discovery and resolution

- **Discovery:** JGDMS's own service lookup, `org.apache.river.resource.Service.providers(
  MarshalDelegate.class, c.getClassLoader())` — the explicit-`ClassLoader` overload (predates
  `java.util.ServiceLoader`; `@since` Jini 2.0). It returns an `Iterator<MarshalDelegate>` of
  instantiated providers (public zero-arg ctor; classes resolved via `net.jini.loader.LoadClass.
  forName(cn, true, loader)` from `META-INF/services/<MarshalDelegate FQN>`). `Service` already
  carries an OSGi bridge (a static `osgi` flag flips lookup to `OSGiServiceIterator.providers(
  service)`, the OSGi registry) and its own TODO declares it the intended indirection layer over
  both `java.util.ServiceLoader` and OSGi — so routing through `Service` is forward-compatible and
  works uniformly on classpath, JPMS, and OSGi. (Plain `java.util.ServiceLoader` would not see the
  OSGi-registered delegates.)
  - **Selection by defining loader, not just package name.** The classpath path is loader-scoped,
    but the OSGi `OSGiServiceIterator` path is registry-wide and **ignores the loader argument** —
    and in OSGi the same delegate package may be registered by several bundles (several versions).
    So select the provider `d` with `d.getClass().getClassLoader() == c.getClassLoader()` (i.e. the
    delegate defined in the target's own runtime package). This single predicate gives correct
    access (same runtime package, §3.1), the correct **version** (same defining loader, §5), and is
    robust to parent-loader visibility (a config named in a parent but defined elsewhere is filtered
    out). Cache per `(loader)` → delegate, or per `Class`.
  - `META-INF/services` (classpath) / OSGi `provides` registration lets a delegate be instantiated
    **without `exports`/`opens` of its package** — so the package-private classes stay encapsulated.
- **Resolution ownership:** each JERI `Endpoint` has an assigned `ClassLoader` that is *solely*
  responsible for resolving classes unmarshalled from its stream (handling codebase annotations,
  preferred classes, and boomerangs via `PreferredClassProvider`). By the time the engine holds
  a `Class`, resolution is already done — so we simply read its defining loader and look up the
  delegate. We **consume** the resolved loader; we never resolve, install, or manage loaders
  ourselves.
- **Bundles:** a shared class that resolves into an OSGi bundle reports the **bundle** as its
  defining loader (the Endpoint loader merely delegates to it). Scoping the delegate lookup to
  that defining loader lands on the bundle that owns the package and registered the delegate.
  We marshal *any* object visible to the Endpoint loader, not just the proxy, so per-object
  defining-loader resolution is what reaches bundle-defined classes correctly.

---

## 5. Versioning (distributed systems, not JPMS)

JPMS does not manage version coexistence — a module layer cannot hold two versions of a package.
In JGDMS, coexistence is **classloader-based** (codebase/preferred loaders) and, in OSGi,
**bundle-based**. The design keys everything on the class's defining loader, which aligns:

- Each coexisting version of a class is a distinct `Class` under a distinct loader, so
  loader-scoped delegate lookup gives each version *its own* delegate — no cross-version mixing.
- The **wire is the version seam**: `serialForm` (an authored ABI) + `GetArg`-by-name-with-defaults
  let v1 and v2 interoperate (missing arg → default, extra arg → ignored). This only works because
  `serialForm` is a contract decoupled from fields (§2).
- Version *translation* happens on the wire and is reconciled by whichever local version's
  `(GetArg)` constructor consumes the args; the delegate that drives it is always the one matching
  the local class's loader.

---

## 6. Non-goals, grounded in TR-2006-149 and the failed bundle provider

The design must not re-introduce the documented failure modes:

- **§4.2 codebase annotation loss / §4.3 annotation mixing** — the delegate is an *access broker
  only*. Codebase annotation and class resolution stay entirely with the existing Endpoint
  loader / `PreferredClassProvider`. The delegate changes neither, so it cannot cause annotation
  loss or mixing.
- **§4.5 undesired local resolution / package-access divide (JDK 4302406)** — the resolution rule
  (§3.1): delegate resolved strictly via the marshalled class's defining loader, so delegate and
  target share one runtime package. Never a local delegate for a downloaded class.
- **§5.3 preferred-class guidance** — package-private `@AtomicSerial` implementation classes are
  exactly the classes the paper says should be *preferred* (loaded from codebase, distinct type,
  not locally resolved); public APIs should be abstract types. Our delegate serves the
  implementation classes; the public surface is the interfaces (`MarshalDelegate`, proxy
  interfaces). Consistent.
- **No OSGi-managed proxy loaders** — `jgdms-osgi-proxy-bundle-provider`'s `ProxyBundleProvider`
  tried to make each codebase an installed OSGi bundle (`bc.installBundle(...)` +
  `BundleDelegatingClassLoader`) and use it as the proxy loader. That forces OSGi's bundle
  lifecycle/resolver/one-version-per-framework onto the dynamic, multi-version, GC-able codebase
  loader model JERI needs, and failed. Proxy loaders stay JGDMS-managed; bundles participate only
  as the *defining loaders of already-resolved shared classes*. The dead module is retained but
  must not be used.
- **No field access** — Java serialization's field injection (construct-then-set, bypassing the
  constructor) is the root of its invariant-bypass / gadget-chain vulnerabilities and demands a
  privileged `setAccessible`-on-fields grant. We refuse it entirely; deserialization is
  always via the validating `(GetArg)` constructor.

---

## 7. Annotation processor — minimise developer boilerplate

An annotation processor keyed on `@AtomicSerial` can generate the **mechanical, semantics-free**
layer, and *check* (not write) the contract:

- **Generate:** the per-package delegate (direct dispatch to each class's three members) and, on
  the classpath, the `META-INF/services` registration (`Filer`). On a modularized build the
  `provides MarshalDelegate with …;` line must be added to `module-info.java` by hand (annotation
  processors cannot edit `module-info`) — still `exports`-free / `opens`-free.
- **Do NOT generate:** `serialForm`, `serialize`, `(GetArg)` — these are the authored contract,
  and `serialize` in particular must stay in-class because only in-class code may read the class's
  own fields *and we refuse to generate field access*.
- **Check (the real DX win):** from declarations (standard `javax.lang.model`) — a fieldful level
  missing `serialize`, a fieldless level that should be `@Stateless`, a field type that is neither
  `@AtomicSerial` nor a registered serializer. Deeper — using the `javac` `com.sun.source.util.Trees`
  API (à la Error Prone) — verify `serialForm`/`serialize`/`(GetArg)` agree on names and types,
  turning the wire/field-mismatch class of bug (e.g. `ConsistentMap`, `mercury ServiceRegistration`)
  into **compile errors** instead of marshal-time failures.

Net developer surface: `@AtomicSerial` + the three contract members. The delegate, registration,
and contract validation are generated/enforced; nothing touches a field reflectively.

---

## 8. Relationship to current code / migration path

- The SOW #12 Phase 0 migration added the three contract members to the production `@AtomicSerial`
  classes (hand-written; this is the per-class contract that stays).
- Two interim engine fixes are on trunk so package-private `@AtomicSerial` classes work *today*,
  before delegates exist:
  - `setAccessible(true)` on the reflective `serialForm`/`serialize` invokes (commit on trunk) —
    a **stopgap** that the delegate model **retires**: once a package has a delegate, the engine
    routes through it and never reflects into the foreign package-private class.
  - `serialForm` lookup `getMethod` → `getDeclaredMethod`, so each level must declare its own and
    cannot silently inherit a superclass's (the `serialize` lookup is already self-guarded by its
    `theClass` parameter).
- Evolution: introduce `MarshalDelegate` + JGDMS-ServiceLoader resolution + the processor; generate
  delegates per package; then make delegates mandatory for non-public `@AtomicSerial` classes and
  remove the `setAccessible` stopgap. Public `@AtomicSerial` classes need no delegate.

---

## 9. Open decisions

- Per-level wire framing (current: per-class descriptors on the wire) vs a flatter name-keyed
  schema — kept as-is unless a deliberate format change is undertaken.
- Hand-written vs processor-generated delegates (processor preferred once available).
- Whether to ship the `Trees`-based contract checker (javac-coupled) or stop at declaration-level
  checks.

## References

- M. Warres, *Class Loading Issues in Java RMI and Jini Network Technology*, SMLI TR-2006-149
  (esp. §2.3 type identity, §4.1–4.7 the failure modes, §4.5 + JDK 4302406 the package-access
  divide, §5 preferred classes / §5.3 selection guidance).
- `jgdms-osgi-proxy-bundle-provider` (`ProxyBundleProvider`) — the failed OSGi-managed-proxy-loader
  approach; retained, not to be used.
- `org.apache.river.api.io.{ObjOutputStream, AtomicMarshalInputStream, AtomicExternal}` — current
  engine; `AtomicExternal.Factory.instantiate` already does `getDeclaredConstructor` +
  `setAccessible` for the `(GetArg)` ctor (the pattern the delegate replaces for the static
  members).
- `org.apache.river.resource.Service` — JGDMS's service lookup (pre-`java.util.ServiceLoader`);
  `providers(Class, ClassLoader)` + the built-in OSGi bridge (`osgi` flag → `OSGiServiceIterator`).
  This is the discovery mechanism for `MarshalDelegate` (§4); its standing TODO is to become the
  indirection layer over `java.util.ServiceLoader` + OSGi, which this design relies on.
