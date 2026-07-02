# DESIGN — make the download jar Java 8 via facade/SPI split of `net.jini.lookup`

*Status: design of record (agreed 2026-07-02). Supersedes the earlier
"rename the marshalled interfaces into a new package + bridge" draft, which broke
proxy wire identity for no benefit. This approach keeps `net.jini.lookup` whole in
the download jar and moves only the internal implementation out.*

## 1. Problem & context

`jgdms-lib-dl` is the **downloadable** jar (the proxy codebase served to clients);
it is *also* installed locally on both the server and the client. Per project
principle, `-dl` jars must compile for **Java 8** where possible, otherwise Java 11,
so a proxy loads on the widest range of client JVMs (see
[[jgdms-dl-proxy-java8-target]]). Today `jgdms-lib-dl` is compiled to **Java 21**,
and the *sole* Java-21 dependency across all 177 classes is one line:

```
net/jini/lookup/LookupCacheImpl.java:1954   Thread.ofVirtual()...
```

The whole `net.jini.lookup` package sits in `jgdms-lib-dl` — an artifact of the
ant→maven modular-build conversion, which kept the package whole in one jar
specifically to **avoid a split package**. That parked the client/server-local
discovery *implementation* (`ServiceDiscoveryManager`, `LookupCacheImpl`,
`JoinManager`, ~7,850 lines) in the downloadable jar next to the genuinely
downloadable proxy *contract* interfaces.

**Why not just downgrade the one line?** Virtual threads are a *security*
mechanism here, not merely performance: a one-shot virtual thread carries its own
isolated `AccessControlContext`/`Subject` (via `ScopedValue`) and cannot inherit a
pooled platform thread's stale security context, and DirtyChai is what makes
`SecurityManager` + authorization work on virtual threads at all
([[no-threadlocal-virtual-threads]]). So `LookupCacheImpl` must **keep** virtual
threads — which means it must live in a Java-21 jar. We **relocate the
implementation**, we do not downgrade.

## 2. Design — facade in `-dl` (Java 8), implementation in `jgdms-lib` (Java 21)

Keep `net.jini.lookup` **whole and in `jgdms-lib-dl`**. The public API surface does
not move at all. Only the private implementation moves, into a *different* package
in the Java-21 `jgdms-lib` jar — so no package is split, and there is **no public
API, source, or wire-format break**.

- **Public API stays in `net.jini.lookup` (jgdms-lib-dl, Java 8), unchanged FQCNs:**
  - `ServiceDiscoveryManager` and `JoinManager` become thin, stateless-logic
    **delegating facades**: their public constructors and methods are preserved
    byte-for-byte in signature; each resolves a `DiscoveryProviderFactory` (see §4)
    and forwards to an implementation object.
  - the accessor / registrar contract interfaces `ServiceProxyAccessor`,
    `ServiceAttributesAccessor`, `ServiceIDAccessor` (all `extends Remote`),
    `SafeServiceRegistrar` (`extends ServiceRegistrar`), `DiscoveryAdmin` — **not
    touched** (this is the whole win over the rejected rename approach: proxy wire
    identity is preserved).
  - the client-facing API types `LookupCache`, `ServiceItemFilter`,
    `ServiceDiscoveryListener`, `ServiceIDListener`, and the delivered event
    `ServiceDiscoveryEvent` (`@AtomicSerial`, `EventObject`) — unchanged.
  - **new**, small, and local: the SPI interfaces + discovery bridge (§4).

- **Implementation moves to `au.net.zeus.jgdms.discovery` (jgdms-lib, Java 21):**
  `ServiceDiscoveryManagerImpl`, `LookupCacheImpl` (keeps vthreads),
  `JoinManagerImpl`, and the internal state classes `EventReg`, `ProxyReg`,
  `ServiceItemReg`, `ServiceReg`. They all move **together** into the one new
  package, so their existing package-private cross-references are preserved among
  themselves. `au.net.zeus.jgdms.client.ServiceDiscoveryHelper` (today the only
  class in that package in `-dl`, and a client-local SDM helper) moves with them.

`jgdms-lib` already depends on `jgdms-lib-dl` (jgdms-lib/pom.xml:39), so the impl in
`jgdms-lib` referencing the facade/SPI/API types in `jgdms-lib-dl` is the correct
dependency direction. `-dl` must **not** gain a dependency on `jgdms-lib`; the
facade reaches the impl only through the SPI + `Service` discovery, never by a
compile-time reference to an `au.net.zeus.jgdms.discovery.*` class.

Result: `net.jini.lookup` wholly in `-dl` (Java 8); `au.net.zeus.jgdms.discovery`
wholly in `jgdms-lib` (Java 21). Neither package is split; `-dl` becomes
Java-8-clean; `LookupCacheImpl` keeps vthreads on the Java-21 local install.

## 3. Class disposition

| Class(es) | Role | Disposition |
|---|---|---|
| `ServiceProxyAccessor`, `ServiceAttributesAccessor`, `ServiceIDAccessor`, `SafeServiceRegistrar`, `DiscoveryAdmin` | proxy/registrar contract (marshalled) | **unchanged**, stay `net.jini.lookup` / `-dl` |
| `LookupCache`, `ServiceItemFilter`, `ServiceDiscoveryListener`, `ServiceIDListener` | client-facing API interfaces | **unchanged**, stay `net.jini.lookup` / `-dl` |
| `ServiceDiscoveryEvent` (`@AtomicSerial`, `EventObject`) | event delivered to local listeners | **unchanged**, stay `net.jini.lookup` / `-dl` |
| `ServiceDiscoveryManager` | client discovery entry point | → **facade** in `net.jini.lookup` / `-dl` (public API kept) |
| `JoinManager` | server-side join entry point | → **facade** in `net.jini.lookup` / `-dl` (public API kept) |
| `ServiceDiscoveryManagerImpl` *(new, = old SDM body)* | discovery impl | → `au.net.zeus.jgdms.discovery` / `jgdms-lib` |
| `JoinManagerImpl` *(new, = old JoinManager body)* | join impl | → `au.net.zeus.jgdms.discovery` / `jgdms-lib` |
| `LookupCacheImpl` (vthreads) | SDM cache impl | → `au.net.zeus.jgdms.discovery` / `jgdms-lib` |
| `EventReg`, `ProxyReg`, `ServiceItemReg`, `ServiceReg` | SDM/JoinManager internal state | → `au.net.zeus.jgdms.discovery` / `jgdms-lib` |
| `au.net.zeus.jgdms.client.ServiceDiscoveryHelper` | client-local SDM helper | → `au.net.zeus.jgdms.discovery` / `jgdms-lib` |

Confirmed: the internal state classes are truly package-internal — the only
repo-wide matches for `EventReg`/`ProxyReg`/`ServiceItemReg`/`ServiceReg` outside
`net.jini.lookup` are *unrelated* same-named classes in
`net.jini.discovery.AbstractLookupDiscoveryManager` and reggie's `RegistrarImpl`.
So moving them has zero external ripple.

## 4. The SPI + discovery bridge (reusing `org.apache.river.resource.Service`)

The facade must obtain an implementation without a compile-time dependency on
`jgdms-lib`. Use the existing JGDMS provider mechanism, **not** raw
`java.util.ServiceLoader` (which returns zero cross-bundle under OSGi —
[[jgdms-service-loader-osgi]]).

New SPI types, all in `net.jini.lookup` (jgdms-lib-dl, Java 8), local (not preferred):

- `interface DiscoveryProviderFactory` — the single registered provider. Methods
  mirror the facade constructors, returning the impl SPI types, e.g.
  `ServiceDiscoveryManagerSpi newServiceDiscoveryManager(DiscoveryManagement, LeaseRenewalManager, Configuration)`
  and `JoinManagerSpi newJoinManager(...)` for each existing public ctor arity.
- `interface ServiceDiscoveryManagerSpi` — the public method surface of SDM
  (`lookup`×4, `createLookupCache`, `getDiscoveryManager`,
  `getLeaseRenewalManager`, `terminate`).
- `interface JoinManagerSpi` — the public method surface of JoinManager.
- (No `LookupCacheSpi` needed — the facade exposes `LookupCache`, the public
  interface `LookupCacheImpl` already implements; `ServiceDiscoveryManagerSpi
  .createLookupCache` returns `LookupCache`.)

`au.net.zeus.jgdms.discovery.DiscoveryProviderFactoryImpl` (jgdms-lib) implements
`DiscoveryProviderFactory`, constructing the `*Impl` objects. It is declared in
`jgdms-lib/src/main/resources/META-INF/services/net.jini.lookup.DiscoveryProviderFactory`.

Resolution (in a static holder in the facade, resolved once):

```java
Iterator<DiscoveryProviderFactory> it = Service.providers(
        DiscoveryProviderFactory.class,
        ServiceDiscoveryManager.class.getClassLoader());
```

**Why `Service.providers` and not `Service.providerNames`.** `Service` is already
OSGi-aware on the `providers(Class, ClassLoader)` path (verified 2026-07-02): under
OSGi it returns a `ChainedIterator` unioning the within-loader `META-INF/services`
scan with the OSGi service-registry lookup (`OSGiServiceIterator`, the platform
`BundleActivator`, flips the flag on `start`). That cross-bundle union is exactly
our case — facade in the `jgdms-lib-dl` bundle, factory impl in the `jgdms-lib`
bundle. `providerNames` is deliberately within-loader-only (for co-loaded
`MarshalDelegate` filtering) and would *not* find a cross-bundle factory. The
`Service` class javadoc was updated the same day to state this (it previously
carried a stale "will be updated to use ServiceLoader / OSGi registry" TODO that
the `ChainedIterator`/`OSGiServiceIterator` machinery had already satisfied).

**Two loading regimes, both covered:**
- *Plain classpath / local install (the common case).* `jgdms-lib-dl` and
  `jgdms-lib` are both installed locally and loaded by the same (app/parent) loader
  — the facade's `net.jini.lookup.*` are `Preferred: false`, so they load locally,
  co-located with `jgdms-lib`. The within-loader `META-INF/services` scan finds
  `DiscoveryProviderFactoryImpl`. No OSGi needed.
- *OSGi.* The within-loader scan won't cross bundles, but `Service.providers`
  unions the OSGi registry, so `jgdms-lib` must **publish**
  `DiscoveryProviderFactory` to the service registry (a one-line DS/activator
  registration in `jgdms-lib`). Tracked as an impl task; not required for the
  non-OSGi build/test path.

If no provider resolves, the facade throws a clear
`IllegalStateException("no net.jini.lookup.DiscoveryProviderFactory on the
classpath — add jgdms-lib")` rather than NPEing — this is the diagnostic for
"someone depends on `-dl` alone and expected discovery to work".

## 5. Backward-compatibility ramifications

Much smaller than the rejected rename approach, because no public FQCN moves.

**A. Source compatibility.** None for callers/implementors: every public
`net.jini.lookup` FQCN (interfaces, `ServiceDiscoveryManager`, `JoinManager`,
`ServiceDiscoveryEvent`) is unchanged, so `import net.jini.lookup.*` and every
`implements ServiceProxyAccessor …` on the service backends (`HelloServiceBackend`,
`Registrar`, `TxnManager`, `MailboxBackEnd`, `Fiddler`, `NormServer`,
`OutriggerServer`, the `*Backend` types, `AbstractJiniService`) compile unchanged.

**B. Binary / wire compatibility.** Preserved. Exported stubs still carry the same
`net.jini.lookup.*` interface names, so proxy type identity is unchanged and a 3.x
peer and a 4.0 peer still exchange proxies. `check-api-compat.sh` (japicmp vs
3.1.0) should see **no** removed/changed public type in `net.jini.lookup` — if it
flags `ServiceDiscoveryManager`/`JoinManager` (e.g. a now-`final` field, a removed
package-private member surfaced by tooling), review and allowlist narrowly rather
than rebaselining wholesale. The serial-schema gate covers only `ServiceDiscoveryEvent`,
which is unchanged.

**C. Jar dependency.** The impl moves from `jgdms-lib-dl` to `jgdms-lib`. Anything
that depended on `jgdms-lib-dl` **alone** and actually ran discovery locally must
also depend on `jgdms-lib` (almost everything already does, transitively). The qa
harness uses SDM/JoinManager heavily — verify it resolves them at runtime (facade
from `-dl`, impl from `jgdms-lib`, both on the harness classpath).

**D. `PREFERRED.LIST`.** No new entry needed: `net/jini/-  Preferred: false`
already covers the new facade/SPI classes (they are in `net.jini.lookup`). The moved
impl is in `jgdms-lib`, which is not a download jar and has no `PREFERRED.LIST`. The
unrelated legacy `org/apache/river/start/ServiceProxyAccessor` entry is untouched.

**E. Docs.** The Part-1 blog example is unaffected (all FQCNs unchanged). No
migration note required beyond a release-note line: "discovery implementation moved
from `jgdms-lib-dl` to `jgdms-lib`; add a `jgdms-lib` dependency if you used
`jgdms-lib-dl` alone".

## 6. Net effect

- `jgdms-lib-dl` → **Java 8** (`<release>8`), containing only genuinely
  downloadable proxy code, contract interfaces, and thin local facades/SPI.
- `hello-world-dl` → **Java 8** (trivial; no 9+ API).
- Discovery implementation → `au.net.zeus.jgdms.discovery` in `jgdms-lib` at
  **Java 21**, retaining virtual threads and their security properties.
- The ant→maven split-avoidance constraint is honoured (each package in one jar),
  and the download jar stops silently forcing Java 21 on clients — with **zero**
  public-API / source / wire-format break.

## 7. Implementation plan (verify each build before the next; heed the
single-`mvn` and shared-worktree traps — work in an isolated worktree)

1. **SPI + facade (`-dl`).** Add `DiscoveryProviderFactory`,
   `ServiceDiscoveryManagerSpi`, `JoinManagerSpi` in `net.jini.lookup`. Convert
   `ServiceDiscoveryManager` and `JoinManager` to delegating facades (preserve every
   public signature; static one-time `Service.providers` resolution; clear
   `IllegalStateException` if unresolved).
2. **Impl move (`jgdms-lib`).** Create `au.net.zeus.jgdms.discovery`; move + rename
   the old SDM/JoinManager bodies to `*Impl implements *Spi`, move `LookupCacheImpl`,
   the four `*Reg`, and `ServiceDiscoveryHelper`; add `DiscoveryProviderFactoryImpl`
   and its `META-INF/services` file. Fix the now-cross-package references (they go
   through the SPI / public API types).
3. **Compile core:** `jgdms-platform` (Service javadoc only) → `jgdms-lib-dl` →
   `jgdms-lib`. Then set `jgdms-lib-dl` and `hello-world-dl` `<release>` to `8` and
   recompile.
4. **Downstream + qa:** reactor build; confirm qa resolves SDM/JoinManager at
   runtime.
5. **Verify:** re-run the class-version census (expect `jgdms-lib-dl` all ≤ v52),
   `check-api-compat.sh` (expect clean or narrow allowlist), and the serial-schema
   gate. Do **not** push without a green build ([[verification-discipline]]).
6. **OSGi:** register `DiscoveryProviderFactory` in `jgdms-lib`'s bundle
   activator/DS so cross-bundle resolution works under OSGi.
7. **Guard:** add a `-dl` bytecode-version check (class major > 52 ⇒ fail, allow 55
   for the Java-11 fallback) to the API-compat tooling so this regresses loudly.
