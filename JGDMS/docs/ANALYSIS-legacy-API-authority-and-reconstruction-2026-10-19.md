# Analysis: legacy Jini/River APIs, authority handling and object reconstruction

**Status: analysis only (2026-10-19). No work is scheduled.** This records what was discussed and found so it is not lost. Nothing here has been
implemented except where marked "done". Branch `claude/focused-bohr-t6bwal`, HEAD at the time of writing `5ea881d73`.

## 1. Maintainer positions recorded in this discussion

1. **A name is not an identity.** Like a URL, a class name is a claim; a digest is an identity. Class-name allow/deny lists break encapsulation
   (implementation classes would have to be published to whoever configures the policy) and cannot see codebase or signer, so **permission checks decide
   what may be reconstructed, not name lists.** (Done: the lists built in B5 were removed, `5ea881d73`.)
2. **Gadget attacks succeed by deserializing into a privileged context.** The privileged context has been reduced to `java.base`, far smaller than the OpenJDK 21
   trusted computing base. The context that must not be privileged is the **set of class domains that run constructors, `check(GetArg)` and input validation**, which
   is exactly what the `DeSerializationPermission` ATOMIC/PROXY gates already build (a context from the ProtectionDomains of the classes about to be constructed, not the caller's stack).
3. **Privileged is more than `AllPermission`:** also creating or setting a SecurityManager, creating a ClassLoader, etc. A privileged class (for example any `ClassLoader` subclass,
   including a child loader) must not be deserializable. After validation completes it is safe to have privilege again, so construction could run with an unprivileged domain injected onto the stack.
4. **Residual risk:** a deserializing class may call `doPrivileged`, to reduce or to escalate. Static analysis (SCAP, bytecode) can find the presence of `doPrivileged` but cannot decide which, because the
   `AccessController` API does not allow it; the API should have the quality that safety can be determined by static analysis. New API, including breaking changes, is acceptable; **breaking compatibility with OpenJDK
   is acceptable**, as are removing old Jini APIs or implementations that are not secure.
5. **Handling per category.** Candidates are sorted into (A) reachable from untrusted input, (B) able to escalate privilege, (C) cannot be made statically analyzable, to help decide how to handle each.

## 2. Design thread: a privilege API that static analysis can decide

**Why today's API is undecidable.** `doPrivileged(action, ctx)` has authority `dom(caller) ∩ ctx`, and whether that reduces or escalates depends on a runtime `AccessControlContext` value (Rice's theorem). The fix is a new API, not a smarter analysis.

**Sketch (board, formal-properties seat; the proof is an unmechanised sketch):**
- `restrict(Ceiling, Reduced action)`: takes no context, always intersects with the live stack, never cuts it. The single ambient primitive. The kernel is the only caller of the raw primitive.
- `Elevation.run(Site, Ceiling, Elevated action)`: the only escalation; per-site registry (`@ElevationSite`, build-generated list), per-site permission checked on the unelevated stack, refused while a decode flag (`ScopedValue`) is bound.
- `resume(captured)`: re-run a captured context; the capture object is final, opaque, non-serializable, not implementable by callers.
- Distinct action types so the argument type tells reader and analyzer which kind of call it is. Typed kernel services (property read, loader get) preferred over lambda elevation.
- Per-class bytecode rules (linear, no call graph): no raw privilege calls; `invokedynamic` only for `LambdaMetafactory`, `StringConcatFactory`, `ObjectMethods`, `SwitchBootstraps`; no reflection, `MethodHandle`, `Unsafe`, `defineClass`, `ClassLoader` subclasses, native methods; supertypes also vetted. Fail closed on anything unparsable.
- Policy side: dangerous permissions only to vetted code (identified by class-bytes digest); the guarded construction runs under class domains plus an unprivileged marker domain.
- The JDK's limited-privilege `doPrivileged(action, ctx, Permission...)` is the one existing shape bytecode analysis can classify (1-arg unbounded, 2-arg context transplant, 3-arg bounded).
- Proposed home: a new zero-dependency module (`jgdms-privilege`, java.base only) shared by the analyzer and the codebase; `net.jini.security.Security` kept as thin deprecated delegates; a build-time ratchet that freezes the current call sites and forbids new ones.

**Weaknesses found when the sketch was tested against the code (two independent reviews):**
- An empty marker domain is bypassed by ordinary code: a constructor needing even a property read calls plain `doPrivileged`, which cuts the stack and drops the marker, so benign code and a gadget look the same. A non-truncatable ceiling barrier with a non-empty ceiling needs a change in the DirtyChai `AccessController` itself (a fork change; possible given compatibility with OpenJDK may be broken).
- Three incompatible combiners coexist (JDK `SubjectDomainCombiner`, `DistributedSubjectCombiner`, and the domains `RemoteContextCodec` rebuilds from peer-claimed codebases); only one is an intersection. "Run as client" in `BasicInvocationDispatcher` is elevation by combination.
- The combiner the real stack carries can widen domains after the check (time-of-check/time-of-use); build and run construction under the same combiner-free context.
- A denylist of dangerous permissions fails open (misses file write/execute, sockets, property write, `loadLibrary`, `exitVM`, `modifyThread`, JMX, `GrantPermission`, `DelegationPermission`); use a ceiling allowlist enforced at use, keep the list only as early rejection.
- A policy granting by class digest can be forged over the wire: `DigestGrant.implies` never looks at the class loader and `RemoteContextCodec.readDigest` builds a digest code source from any bytes the peer sends.
- The marker-domain construction cannot work until decode stops doing privileged work (fetching codebases, remote calls in `readResolve`, `Security.grant`, DGC dirty calls): it needs a **two-phase decode** (decode values, admit, then resolve proxies and codebases outside the constructor).
- Dynamic and leased grants make authority depend on grant history; thread hand-off sheds the submitter's context unless captured; helpers accepting arbitrary actions become the elevation point.
- With no SecurityManager (stock JDK 24+) nothing enforces any of this; the analysis is then an audit certificate. A platform-independent rule that survives: refuse classes assignable to `PrivilegedAction`, `PrivilegedExceptionAction`, `DomainCombiner`, `Policy`, `ClassLoader`, `SecurityManager` (a type check, not a name check).
- Impossible in any API: reasoning through virtual dispatch on attacker-chosen types, reflection and generated code, user callback bodies, trusting `java.base`.

**A plan for "check the class-domain context, then inject an unprivileged domain" is documented** in `docs/PLAN-check-and-inject-unprivileged-construction-2026-10-19.md` (not scheduled, not implemented). The reviews above say it needs the barrier, the combiner handling and the two-phase decode before it is sound.

**Superseded (2026-10-19, revised 2026-10-22) by DirtyChai `docs/DESIGN-privilege-ceiling-2026-10-19.md` (r3 + §12).**
The `restrict`/`Elevation`/`resume` API and the per-class bytecode rules above are replaced. The load-bearing change is
converting every platform `doPrivileged` (java.base ~406 sites; JGDMS's ~190 true elevations) to the policy-reducing
three-argument form with empty permissions, so no elevation anywhere reaches full authority — per-class least privilege by
policy, generated by polpAudit. That note's §12 records the maintainer's conclusion that this may be sufficient on its own.
The *ceiling* (a `ScopedValue` in `AccessController`, caller codesource + `CeilingPrincipal` message, caps elevating forms
only, inherited by virtual threads as `Subject` is) is retained there as the documented option for unconverted or
third-party elevations, not to be built without evidence of need. The marker-domain weakness (shed by any `doPrivileged`)
is answered by never putting a ceiling in a context: the native stack walk cannot see a `ScopedValue`. The data half —
authenticate the caller, validate, the finite `@AtomicSerial` set with an escape rule on constructors, the type gate,
two-phase decode — is unchanged and remains required. Section 4 steps 8–9 are replaced by that note's §12 revised order.

## 3. Verified findings (two fresh reviews on the named models; counts are FILE counts from greps)

Provenance: Fable (`claude-fable-5-1`) verified section A, Opus (`claude-opus-5-5`) verified sections B and C; both transcripts were checked and stayed on their model throughout. The first-pass lists (a Sonnet-class agent) were unverified and several entries were corrected.

### 3.1 Reconstruction and input handling (Fable, ranked)

| # | Finding | Status |
|---|---|---|
| A6 | Every authorization and reconstruction gate is `if (sm == null) return` (`BasicInvocationDispatcher.checkClientPermission` :1585, `ObjectCodec` ATOMIC/PROXY gates :235/:271, JOSS `ObjectStreamClassContainer` :92, `PreferredClassProvider.check` :431, phoenix `DefaultGroupPolicy`). With no SecurityManager every item below is reachable by any connected peer. | confirmed |
| A1 | JOSS JERI layer: `BasicILFactory`, `MarshalInputStream` (a plain `ObjectInputStream`, codebase annotations, no `ObjectInputFilter` anywhere in main). Client side has no gate. 0 application uses left in main; 169 qa files (111 configs, 58 Java). A gate script `check-no-joss-invocation.sh` exists. | confirmed |
| A2 | `AtomicILFactory`/`AtomicMarshalInputStream` is the **mainline** exporter (not deprecated; 20 sites: reggie, mahalo, mercury, norm, fiddler, group, ...). Doors: any `Externalizable` class is built and `readExternal`'d (:2736-2755, :2812); any non-`@AtomicSerial` `Serializable` class is built by trying every declared constructor with `setAccessible(true)` and synthesised arguments (`ObjectStreamClassContainer` :217-263, :285), fields populated, `readResolve` run (:2760-2786, :2828, :2845). | defect real |
| A3 | A `MarshalledInstance` inside a DER stream takes `payloadFormat` from the wire, default `"JOSS"` (:238), decoded later with codebase annotations; nothing in `jgdms-der` rejects it; DER admission cannot see inside. 24 `get(false)` (no integrity) sites in main. | confirmed |
| N1 | The authorization-context block is decoded **before** authorization (`dispatch` :1030-1056, `RemoteContextCodec.unmarshal` :298-331, untyped `in.readObject()` :377). With the Atomic codec the A2 doors are reachable pre-authorization. Default JWT verifier does no signature check (:250-255). | confirmed |
| A7 | phoenix `registerGroup` spawns a process from the registered descriptor's command, options and `-D` properties; the only gate is `ExecPermission` (no-op without SM). | confirmed |
| A10 | Entry reconstruction from wire-named classes with a codebase (outrigger `EntryRep.entry` :847, reggie `EntryClass.toClass` :276): class downloaded before the shape hash is checked. A SOW exists (`SOW-Entry-ATOMIC-DER-Migration.md`), reggie half not landed. | confirmed |
| A4 | Codebase-annotation download (`ClassLoading.loadClass`, `PreferredClassProvider` constructor leaves `requireDlPerm=false` :384). Disappears with A1/A2/A10. | confirmed |
| A5 | DER downloadable proxies make remote calls during decode (intended authenticate-then-download); concrete gap: `readResolve` substitutes an empty context for null (:163), so the bootstrap call runs with the peer-supplied handler's own constraints. | partly |
| A8 | Unicast discovery reads an unbounded group count (`Plaintext` :476) then a proxy before trust. | confirmed |

Dropped or reframed: `HttpsIntegrityVerifier` (the Jini spec's TLS policy; only httpmd's URL-chosen digest, e.g. `md5=`, is a defect), `CodebaseDownloaderImpl` (already refuses redirects, caps size, allow-lists schemes; internal-address filtering missing), A9 multicast (real mitigations, lowest), A11/A12 (subsumed by A3). Found in unsearched areas: JERI mux framing and TLS/Kerberos identity derivation looked correct; `jgdms-iiop` (RMI-IIOP value serialization outside any JGDMS stream) is a candidate for deletion; `ServiceStarter` falls back to plain JRMP `Naming.lookup` when `allowInsecureConnections` is set.

### 3.2 Authority and analyzability (Opus)

| ID | Finding | Rating |
|---|---|---|
| M3 (from B6) | Method-level authorization (Gate 1) checks the permission against the protection domains the **client claims** and nothing else (`BasicInvocationDispatcher` :1657-1674; `RemoteContextCodec.unmarshal` accepts any URL, certificate bytes and digest, filtering only `jrt:/java.base`). Shipped policies grant `AllPermission` to codebases with no principal (`hello-service.policy`:55-56, service archetype), so one claimed domain passes every per-method check. | HIGH |
| M1 | `PolicyUtils.instantiatePermission` (:454-487, via `AdvisoryPermissionParser`/`DefaultPolicyParser`) never checks the named class is a `Permission` before `ctor.newInstance`, and loads with initialisation: a downloaded jar's `PERMISSIONS.LIST` or the policy service's text can make the client run any public constructor taking `()`, `(String)` or `(String,String)` with privileges, before any trust check (e.g. `new FileOutputStream(path)`). Not tested. | HIGH |
| M2 | `PreferredClassProvider` builds the loader (:1779), which fetches `PREFERRED.LIST`/`PERMISSIONS.LIST` from the annotation URL, **before** `checkLoader`. | HIGH |
| B1/M4 | `Target.dispatch` runs argument decoding under the exporter's captured context (:463-469, `ObjectTable` :98); at invocation `doPrivileged(action, remoteIdentityContext)` (:2159-2173) **replaces** the exporter context and drops the worker-Subject combiner. | confirmed |
| B2 | Dynamic grants are loader-wide and persistent; `BasicProxyPreparer` grants even when `verify==false`, including the downloaded jar's advisory permissions (:334-335, :419-430). Bound is only the caller's `GrantPermission`. | confirmed |
| B4 | `RFC3986URLClassLoader` (and `PreferredClassLoader`) public constructors take an arbitrary context and unverified principals stamped into every domain (:1182-1244). | confirmed (needs `createClassLoader`) |
| B5 | `Security.doPrivileged` is correct (caller found properly, gives only the caller's domain). Real defects: `DistributedSubjectCombiner.combine` (:1860-1877) drops any existing SubjectDomain (a nested `doAs` removes an outer restricting domain) and NPEs on null; `Security.doAs` has no permission check; `doAs`/`doAsPrivileged` have zero callers. | partly |
| M5-M8 | `RemotePolicyProvider` "Remote" gate checks nested-class domains that `PrincipalGrant`/`CertificateGrant` do not have (no-op); `PolicyUpdateListener` turns remote policy text into local grants with no authenticated-service requirement and fetches `keystore` URLs; `InMemoryPolicyServiceImpl.replace` parses before authorizing; `VerdictRegistryImpl` `registerAnalysisEngine`/`reportPinning` unauthenticated under the default plain-TCP exporter (anyone can forge a SAFE quorum or condemn a codebase). | HIGH (default config) |
| M9, M11, M13 | `AggregatePolicyProvider`/`LoaderSplitPolicyProvider` choose policy by the thread's context class loader (deprecated, unused in main); `DistributedSubjectCombiner` dead/harmful; `jgdms-iiop` single class. | remove |
| B3, B9, B7, B13, C13 | B3 and B9 are availability, not authority; B7 is a trust-decision problem (C8); B13 belongs under A; C13 only in benchmarks; C2's "134 files" overcounts (27 files create or extend `ObjectInputStream`). | re-categorised |

doPrivileged inventory (regex-based, about ±10%): about 340-372 call sites in 143-156 main files; roughly 90 mechanical (property/env, loader getters, reflection metadata), about 60 context-restore (need named baselines such as "as exporter", "restore caller"), 31 `Subject.doAs` (identity binding, a separate API), about 190 true elevations to review (about 35 are the `*EndpointInternalsAccess` back-doors that probably need no privilege).

## 4. If this work is ever started: suggested order (Opus, with Fable's fixes)

Each step removes constructs the next would otherwise have to model. Dependents are file counts.

1. M1: check the class is a `Permission` before construction, load without initialising (3 callers).
2. M3/M4: require every remote domain and the principal-only domain to imply the permission; run under exporter ∩ remote, not instead of it; policy lint for codebase-only grants (jeri about 8 files; qa 205 policy files, 138 codebase-only by heuristic; behaviour change for shipped policies).
3. Remove `Security.doAs`, `doAsPrivileged`, `DistributedSubjectCombiner`; scope the two policy-state mutators.
4. M2/B2: build loaders only after `checkLoader`; advisory permissions as unresolved only; `BasicProxyPreparer` must not grant when not verifying (42 main, 125 qa).
5. Policy-service/verdict-registry: authenticate by default, authorize before parsing, ban `keystore` in remote text.
6. Remove the two policy providers, `jgdms-iiop`, the jini-2.1-compat copies.
7. Make the loader constructors taking a context and principals package-private factories.
8. Add `restrict`/`resume`/`elevate` and port the call sites.
9. Two-phase decode, then marker-domain construction.
10. Activation (305 qa files mention it).

Independently of those: fail the gates closed without a SecurityManager; delete `BasicILFactory`/`MarshalInputStream` from the invocation layer; close the `Externalizable`/`Serializable` doors in `AtomicMarshalInputStream` and type the pre-authorization context reads; refuse a JOSS `MarshalledInstance` payload at reconstruction platform-wide.

## 5. Open questions (undecided)

- Whether the DirtyChai changes (ceiling barrier, `restrict` as the only ambient primitive, gated old forms, runtime-owned combiner semantics) are designed in this repo or specified for a separate project.
- Whether deleting `DeSerializationPermission` in favour of a policy object (a reviewer's suggestion) is acceptable: it conflicts with the position that permission checks decide, so it is not recommended here.
- Breaking shipped policies that grant `AllPermission` to codebases alone (step 2).
- Whether a type gate (refuse privileged types by assignability) should land independently of any fork work.
- Digest-keyed identity rather than names, if any per-class control is wanted (the schema digest already identifies a class's shape).

## 6. Process notes

- Board seats: a Fable and an Opus seat were downgraded to Sonnet by a policy flag in an earlier session (at about 07:52 UTC on 2026-10-18, both within a second), before they were resumed in this one; reports from those seats after that point were Sonnet output. Fresh agents started with an explicit model and checked against their transcripts stayed on their model for the whole run. Briefs for those seats are framed as correctness and robustness reviews. Re-check the recorded model of any agent at the start and end of its run.
- Unverified items remain unverified: nothing here was executed against hostile input; every finding is from reading code. Not examined by anyone: the DirtyChai JDK's `AccessController` internals, `PreferredClassLoader` `DownloadPermission` enforcement, `BootstrapPermission` with no SecurityManager, Kerberos/HTTPS endpoints beyond principal derivation, `jgdms-cel` adversarially, the contents (as opposed to the counts) of the qa tests.
