claude-opus-5-5

# Survey: lazily-initialised state that untrusted input can trigger (JGDMS, read-only)

AS-OF 2026-10-31, JGDMS HEAD `5fd08a183` (working tree dirty with other sessions' policy-condenser edits; none in the files cited). DirtyChai read at `344ba66bca1`+.
Skills loaded: handover-composition, production-code. No file other than this report was written; nothing built, run or committed.
**Epistemic tiers.** Nothing here is MEASURED: no JVM was run. VERIFIED means I read the code path end to end, from where untrusted input enters to where the state is initialised. PLAUSIBLE means the code is VERIFIED but whether it can be exploited depends on how the JVM is deployed (HYPOTHESIS). UNDETERMINED means I did not read enough to decide. Citations are `file:line` lines I opened.

## The model being matched (DirtyChai)
`System.initPhase3` (System.java:2468-2490) calls `ensureClassInitialized` on singletons such as CleanerImpl, VirtualThread, Poller and ThreadPool, in the privileged bootstrap window. `setSecurityManager` warms ContextCache and `ScopedValue$Cache$Constants` (:544-547, :2558-2576). There are two hazards: (a) a delay that an observer can time on first use; (b) a one-time initialisation that runs at a time the attacker chooses, on the attacker's stack. A third effect follows from (b): under `polpAudit`, the permission needed by a late initialisation is recorded against whatever domains happen to be on the stack at that moment.
The JGDMS repo already has the fix pattern. DerStreamPolicy.java:189-191 says *"privileged, so the answer does not depend on which caller's protection domain happened to read it first"*, and TimeoutScheduler.java:72-74 calls `StackWalker.getInstance` inside `doPrivileged`. The candidates below are places where that pattern was **not** applied.

## Key enabling fact (VERIFIED)
On a client, `BasicInvocationHandler` creates the response stream inside `doPrivileged` (BasicInvocationHandler.java:1431-1449). It then **decodes the return value outside that block**, in `unmarshalReturn` → `Util.unmarshalValue` (:1627). So any first-time initialisation triggered while decoding a return value runs on the caller's full stack. That stack includes the `$Proxy` frame, and also includes a downloaded smart proxy's own domain whenever that proxy is the caller (e.g. a lookup-service proxy calling `lookup`). In both cases the peer supplies both the bytes and the timing.
On a server, dispatch runs under the export-time context and the export-time thread context class loader (TCCL) (Target.java:458-469). So on a server the stack at first initialisation is the exporting service's own stack, unless decoding is nested inside a downloaded class's code.

## Ranked candidates (strongest first)

**1. JWT verification cache survives installing a verifier.**
- Where: BasicInvocationDispatcher.java:296-298 (static `JWT_VERIFICATION_CACHE`); :2402-2403 (no verifier installed → `DEFAULT_JWT_VERIFIER`, which checks claim structure only, not signatures); :2405-2407 (a cache hit skips verification); :2419-2432 (entry cached until the token's own `exp` claim); :442-448 (`setJwtVerifier` does **not** clear the cache).
- Trigger: `readUserSubjects` (:2335-2337), the user-Subject block of protocol 0x02, sent by any peer able to open a call.
- Effect: a security check runs once, at a time the attacker chooses, and the cached verdict outlives the operator's change. A forged token presented before `setJwtVerifier` is installed passes the structural check. It is then cached until an `exp` value the attacker wrote, and keeps bypassing the real verifier after it is installed. The same applies when a verifier is replaced at runtime.
- Warmed at start-up? No. The javadoc (:429-430) asks for the verifier to be installed before export, but nothing enforces that order.
- Tier: code path VERIFIED; the precondition (wire input accepted before `setJwtVerifier`) is PLAUSIBLE. Minor: the javadoc at :2393-2394 promises an `Instant.MIN` sentinel, but the code at :2420-2422 caches nothing in that case.
- Wrong if: no deployment exposes a remote object before the verifier is set, and no deployment ever replaces the verifier. Warming does not fix this. It needs a clear-on-set, or the cache keyed on the verifier as well as the token.

**2. `AtomicSerial.GetArg` creates its StackWalker at class initialisation, without `doPrivileged`.**
- Where: AtomicSerial.java:333-334. DirtyChai's `StackWalker.getInstance(Option)` (StackWalker.java:370-371 → :396-402 → :464-471) checks `RuntimePermission("getStackWalkerWithClassReference")` against the **current stack**.
- Trigger: construction of the first `GetArg` subclass in the JVM, i.e. the first atomic decode by JOSS (`AtomicMarshalInputStream`) or DER (`DerGetArg`).
- Effect: if that first decode happens with an under-privileged domain on the stack (the client return path above), the check throws. The class then fails initialisation (`ExceptionInInitializerError`), and every later atomic decode in that JVM fails with `NoClassDefFoundError`, so atomic deserialisation is permanently down JVM-wide. Under `polpAudit`, the permission is instead recorded against the peer's codebase.
- Warmed at start-up? Only by accident: whichever atomic decode comes first (discovery, recovering persistent state, or the first call) initialises it. On a client, unicast discovery usually wins.
- Tier: code VERIFIED both ends; exploitability PLAUSIBLE.
- Wrong if: every policy grants that permission to downloaded and `$Proxy` domains, or the first atomic decode always happens on a fully privileged stack.

**3. System properties read during class initialisation without `doPrivileged`, falling back to defaults on `SecurityException`.**
These tie a JVM-lifetime configuration value to whichever caller happens to be first.
- DerInputLimits.java:56-63 (reads at :299-308): the `SecurityException` is swallowed **silently**, so `DEFAULT` becomes 16 MiB / nesting 16 regardless of the configured `au.net.zeus.jgdms.der.maxInputBytes`. An operator's tighter cap is silently loosened.
  - Warmed on servers: the AtomicDerILFactory constructors (AtomicDerILFactory.java:64, :80, :116) touch it at export.
  - Not warmed on clients: the first touch is decoding a DER proxy, at AtomicDerInvocationHandler.java:82, or in DerMarshalInstanceInput.java:120/:153. That decode can sit on a peer-driven stack (enabling fact).
- StreamSchemaDedup.java:313-324 (reads at :336-347): the same pattern, but the fallback is logged at WARNING.
- PreferredProxyCodebaseProvider has 9 `load*` methods; source: `grep 'private static .* load[A-Z]'` gives 10 hits minus `probeDigestCodeSourceCtor`. All read without privilege. One fails **open**: `verdictMaxAgeMs` becomes 0, meaning no age limit (:317-327). Strict mode fails secure (:899-910). The verdict key fails closed (:934-940).
  - Its decode-route first touches are all inside `doPrivileged` (ProxySerializer.java:225, DerProxySerializer.java:234, AtomicILFactory.java:507), so on those routes the reads do not depend on the caller (VERIFIED). What remains is a delay plus a key-file read on first proxy decode in a client JVM.
- Tier: VERIFIED code; the client-side exposure is PLAUSIBLE.

**4. `MarshalDelegates.CACHE` caches a failed lookup for good.**
- Where: MarshalDelegates.java:100-107; `resolve` :183-227 runs on the caller's stack (no `doPrivileged`). It catches `Throwable` (:211) and `ServiceConfigurationError` (:220), and returns null (:226), which is then cached as `NONE` (:105).
- Trigger: the first encode or decode of each class. When decoding, the **sender names the class and picks the moment** (ObjectCodec.java:719 → :784).
- Effect: if that first lookup fails for a reason that depends on the caller or on the moment, the failure sticks. Such reasons include a delegate's constructor or static initialiser being refused on a weak stack, a `StackOverflowError` at the depth the attacker chose, or an `OutOfMemoryError`. The class then has no delegate for the rest of the JVM's life. A non-public `@AtomicSerial` class is then refused by `strictBlockCtor` (:162-167) both ways, for every peer, which is a persistent per-class denial of service.
- Tier: VERIFIED code path; trigger PLAUSIBLE; whether any shipped delegate needs a permission is UNDETERMINED.

**5. `MarshalledInstance` provider registry: the first caller's TCCL decides it.**
- Where: MarshalledInstance.java:110 and :596-614. `ServiceLoader.load(Class)` at :607 uses the TCCL (DirtyChai ServiceLoader.java:1696).
- Trigger: any non-JOSS `payloadFormat` string from the sender, even an unknown one, reaching `get()` (:920 → :572-573 → :583-594). The preference path at :671 also triggers it.
- Not warmed: `grep chooseMarshalFactory(` and 3-argument `new MarshalledInstance(` over jgdms-*/src/main and services/*/*/src/main found **0 production callers**. First link where this check could miss a caller: WIRING — a call spread over several lines or made by reflection would evade the regex.
- Effect: a delay on first use, plus pinning. ServiceStarter gives each service its own `ExportClassLoader` as TCCL (NonActivatableServiceDescriptor.java:633-660), so in a JVM hosting several services, the first service to decode decides which providers every service sees. The initialisation is double-checked without a lock, so two threads with different TCCLs racing produce a nondeterministic set. The providers themselves hold no state (DerMarshalFactoryProvider.java:46-72), so no protection domain is captured in them.
- Tier: VERIFIED; the pinning effect is PLAUSIBLE.

**6. (DirtyChai side, read-only) `DigestCodeSource.digestCache` trusts the first digest it sees.**
- Where: DigestCodeSource.java:375-376; the first digest is pinned for the JVM's life at :1032-1045, and a later mismatch throws `SecurityException`. The cache is populated from the first `ProtectionDomain` created per codebase (SecureClassLoader.java:333-334, :389-392).
- Trigger: the first class loaded from a codebase URI that a peer named.
- Effect: the peer picks when the trust anchor is set. Whatever the URI serves at that moment becomes the anchor, and a later legitimate redeploy is refused until restart.
- Tier: VERIFIED code; exploitability PLAUSIBLE, and needs control of the URI's content at that moment.
- Not a JGDMS fix. A JGDMS warm-up could pre-load the expected codebases at start-up.

**7. Delay on first use only (weak).**
- CEL classes initialise on the first filtered operation. `FilterAdmission` is first touched only inside remote methods (OutriggerServerImpl.java:2371 onwards); the JFR probe is FilterEval.java:105, and whether JFR event registration needs a permission is UNDETERMINED.
- EntryRep `CODEC` (outrigger EntryRep.java:313-338) is warmed only if recovery restores at least one entry (:1354-1355).
- SHA-256 prototypes are created at class init (AtomicSerialSchemaRecord.java:401-408, StreamSchemaDedup.java:2232-2239). JCA is PLAUSIBLY already warm, because SecureClassLoader digests the main class's codebase (:390).
- RemoteContextCodec reflection (:112-158) and `SslConnection.LIMIT_SALT` (:101) are also first-use only.
- **Refuted for the current wiring:** CrMath's pi cache (CrMath.java:343-376) would be a cross-tenant timing oracle whose cost the attacker scales, held under a global lock. It cannot be reached today: FilterEval.java:267 builds `new Evaluator()`, which uses `MathProvider.none()` (Evaluator.java:107), and that fails closed at MathProvider.java:83-92. It becomes live if anything wires in `MathProviders.correctlyRounded()`.

## Checked and cleared (VERIFIED)
- **Discovery** builds its provider sets at construction: RegistrarImpl.java:5417, AbstractLookupDiscovery.java:212, DiscoveryV2.java:610-639 (privileged).
- **Dispatcher principal allowlist** is resolved when the class loads (BasicInvocationDispatcher.java:223-249), and the dispatcher is built at export.
- **Lazily created threads** do not inherit the first caller's context. Each is created with `doPrivileged` + `Subject.callAs(null)`: TimeoutScheduler.java:72-117, HandshakeCompute.java:81-94, jgdms-collections ThreadPool.java:143-179. This is the JGDMS counterpart of initPhase3.
- **Other privileged lookups:**
  - `DerStreamPolicy.defaults` (:118-130).
  - The trust-verifier cache (Security.java:1704-1749), keyed by class loader.
  - `ClassLoading.<clinit>` (ClassLoading.java:103-137).
  - `ServerContext.getProviders` (:53-72), with a caveat: it uses the TCCL although its javadoc (:131-141) says the system class loader. It is reached only outside a dispatch (:167-174).
- **`ObjectStreamClassContainer.deSerializationPermitted`** (:91-151) checks on every call, against the class's own domains, not the caller's.

## Where a warm-up would go (proposal, not written)
- REUSE check: `grep -i 'warm|ensureClassInitialized|eagerInit'` over the scoped src/main trees gave 1 hit, an unrelated comment. There is no existing concept to extend.
- **Call site:** `NonActivatableServiceDescriptor.create` (service-starter), after `ensureSecurityManager` (:602) and the policy install (:647-653), and before the service constructor runs (:682) and the per-service TCCL is set (:660). It should run once per JVM, on the starter's own TCCL. Client JVMs need the same call in `main`, before any discovery manager is constructed. I read the descriptor but have not verified that every start route goes through it.
- **Shape:** one static, idempotent `warm()` per module. The starter calls them in dependency order. Modules cannot reference upward, so there is no single cross-module class.
- **Classes to touch, per module:**
  - **jgdms-platform:**
    - `AtomicSerial.GetArg`: force class initialisation, ideally after moving `WALKER` into `doPrivileged`.
    - `MarshalledInstance`: call `providers()` through a package-private hook, on the starter's TCCL.
    - `MarshalDelegates.delegateFor`: for the service's known `@AtomicSerial` closure.
    - `ClassLoading`.
  - **jgdms-der:**
    - `DerInputLimits`, `stream.StreamSchemaDedup`, `stream.DerStreamPolicy.defaults()`.
    - `schema.AtomicSerialSchemaRecord`.
    - `schema.SchemaGenerator` `CHAIN_CACHE`, plus `object.ObjectCodec` `GETARG_CTOR` and `SERIALIZE_METHOD`, all for the known closure.
    - `marshal.DerMarshalFactoryProvider`, reached through MarshalledInstance.
  - **jgdms-jeri:**
    - `BasicInvocationDispatcher`, `RemoteContextCodec`, `internal.TimeoutScheduler`.
    - `ssl.SslConnection` and `ssl.Utilities`'s SecureRandom, for client TLS.
  - **jgdms-pref-class-loader:** `PreferredProxyCodebaseProvider`, `PreferredClassProvider`.
  - **jgdms-cel:** `wire.FunctionRegistry`, `verifier.StaticTypeChecker`, `verifier.CelVerifier`, `eval.Evaluator`.
  - **outrigger:** `proxy.EntryRep` (`codec()`), `FilterAdmission`, `FilterEval` (its JFR event class).
  - **reggie, jgdms-lib, jgdms-lib-dl:** nothing found that untrusted input can reach. The `FactoryHolder`s at ServiceDiscoveryManager.java:102-114 and LeaseRenewalManager.java:314-326 run during client start-up.
- **Warming is necessary but not sufficient.**
  - #1 needs clear-on-set.
  - #3 needs `doPrivileged`, matching DerStreamPolicy.java:191.
  - #4 must not cache a `Throwable`, because the set of classes is unbounded and cannot all be warmed.
  - #2 needs `doPrivileged` around `getInstance`, matching TimeoutScheduler.java:72-74.
  - Without these, the outcome still depends on which caller or moment comes first, warm or not.

## Open items
- **Decision-blocking:** whether any production JVM accepts wire calls before `setJwtVerifier`, or replaces the verifier at runtime. That decides whether #1 is live today.
- **Next most important:** a Zone-T-eligible test under policy for #2. A `GetArg` first initialised under a `$Proxy` plus downloaded-domain stack should fail, and the test should show that.
- **Not decision-blocking:** whether any shipped `MarshalDelegate` constructor or static initialiser needs a permission (#4).

## Not covered
- jgdms-discovery-providers: the signature and key code behind discovery decoding.
- jgdms-security-jwt: `DefaultJwtVerifier` internals.
- reggie DER paths beyond grep.
- `AtomicMarshalInputStream` per-class caches beyond `ObjectStreamClassContainer`.
- HTTP endpoints, including `HttpSettings.java:212` `Pattern.compile`.
- Kerberos endpoints.
- `LogDispatch` and `ServiceDiscoveryManagerImpl` virtual-thread executors (each task's thread inherits the submitter's context, so nothing is captured once).
- Whether DirtyChai gives `$Proxy` classes an unprivileged domain. Assumed from the RemoteContextCodec.java:179-186 javadoc, not read in DirtyChai.
- `E:\Claude\TRAINING_DOCS_INDEX.md` was not swept, because the brief forbids `E:\`.
- No runtime confirmation: no build, test or `polpAudit` run.
