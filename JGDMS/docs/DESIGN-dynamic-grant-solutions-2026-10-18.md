# Dynamic grants: investigated solutions to the board's points (P1–P7)

**Status:** analysis for a human reader, unreviewed. Produced 2026-10-18 by an AI sub-agent (Fable)
tasked with proposing mechanism-level solutions to the points raised in the SOW section 8 review,
then reconciled with this branch by Claude. Nothing here is implemented. Where it conflicts with
`SOW-PermissionGrant-Mirror-and-ProtectionDomain-Grants.md` sections 7–8, the SOW (and the project
lead's decisions recorded there) governs. DirtyChai items are requirements, not patches: DirtyChai's
`CLAUDE.md` requires its contributions to be human-written.

## Review of this investigation (two independent reviewers, then discussion with the investigator)

Reviewers: Opus and Sonnet (independent, read-only); they exchanged two rounds with each other and
the investigator conceded the Opus points (13 of 13, refining one). Sonnet's points reached the
investigator late and were not answered. **This section supersedes the Summary where they differ.**
Line references in the investigation were found accurate. DirtyChai items remain requirements for
human authors.

### Verdicts

| Point | Verdict | Required change |
|---|---|---|
| P1 loader sharing | sound with changes | Also include the pinned server identity in the shortcut and `Key` (the same host:port can be served later by another identity). Check the bootstrap proxy's handler is a trusted local class **before** taking the shortcut. Wording: `BasicInvocationHandler.equals` includes the server constraints but not the client constraints. |
| P2 digests | sound with changes | The premise "the SCAP verdict for hex(H) is the attestation" is **false today**: the signed verdict covers URLs, verdict and timestamp only; `checkVerdictForJar` compares no hash, no URL set, no age, and a signed SAFE can be replayed for any hash, including after a DANGEROUS. Bind the verdict first. Compute digests only inside the new-loader block (not before the `CACHE` lookup). Load from the exact bytes hashed (the loader refuses a jar whose hash differs from an expected URL→H map) rather than relying on R5. Refuse directory URLs and manifest `Class-Path` expansion. |
| P3 spoofing | sound with changes | R7 (`SecureClassLoader.issued(pd)`, implemented as `pdcache.get(key) == pd`, not an O(n) scan) first, applied to **both** `DigestDomainGrant` and `ClassLoaderGrant`. Narrow R1 to domains carrying a digest code source or principals, with a permission-gated exception (it would otherwise break Byte Buddy / cglib / Mockito style definers; not run). `readExternal` write-once, and grants work from a single snapshot of the code source. R2 + R4 need a permission-gated verified factory: `PreferredProxyCodebaseProvider` (~line 1153) and `RemoteContextCodec` use the public/unverified constructors. State the R3 migration (every non-boot `SecureClassLoader` subclass becomes unverified until policy grants the permission). R5 only opt-in (multi-release jars, `name == null`, `ByteBuffer`, weaving loaders). |
| P4 registry | **unsound as specified** | The identity that feeds the registry is caller-asserted: the provider and `resolve()` are public and take identity from any `instanceof ServerSubject` in a caller-supplied context, so local code can launder any SPIFFE identity through the privileged `record`/grant calls ("not spoofable because writing needs the permission" is false). Use a provider-private weak map with a read-only accessor on `ProxyCodebaseSpi`, not a public `record()` plus `RuntimePermission`; authenticate the provenance of context items. Store keys or clones, not mutable `DigestCodeSource`s. The preparer must also compare the entry's identity with the identity it is preparing for. A `requireVerifiedCodebase` flag would make every permission-granting preparer throw for classpath and `Proxy` proxies, which is exactly what the legacy-lookup migration produces: use a separate preparer class and define the rule for non-provider loaders. |
| P5 identity pin | **unsound as specified** (direction right) | `ServerMinPrincipal` over `SslEndpoint` accepts a single element only, and the server subject holds an X500 principal plus SPIFFE principal(s), so pinning the set is `NOT_SUPPORTED`; pin a **single SPIFFE principal** (a DN pin is not workload-unique); prove that works with a test; each pinned identity needs `AuthenticationPermission`; run it unconditionally. It binds only genuine JERI handlers: `ProxySerializer.check` and `DerProxySerializer` accept any `InvocationHandler`, so a malicious relay supplies its own handler and the pin is a no-op: check handler class identity (`==`). Pin the bootstrap proxy only, not the smart proxy (multi-identity replicas). |
| P6 inconclusive | sound with changes | Union (or list) of retained grants is right. Voiding a grant stops future grants only: loaded classes keep running and cached permissions need `policy.refresh()` and `CachingSecurityManager.clearCache`; the verdict listener is leased, so a lapse leaves a stale SAFE. Meaningful with isolation (kill the subprocess pool for the identity). Also void the static `DigestGrant`s and URI grants. |
| P7 ranking | wrong | **P7.1 withdrawn:** `BootstrapPermission` is deliberately boot-window-only and held only by lookup services and verdict registries; checking it in steady state would refuse every ordinary service. P7.6 withdrawn. Move the unbound verdict (P7.4) and the SCAP-gate bypasses to the top: directory URLs skip the verdict and digest checks, `RFC3986URLClassLoader` follows manifest `Class-Path` (absolute URLs included) so a SAFE jar pulls in unverified jars (grants fail closed, the code still runs), and the non-bootstrap `PreferredClassProvider` path and Entry annotations are the same bypass class. |

### Corrected order
1. SCAP verdict binding: URL containment and a freshness check now; a `ServerMinPrincipal` on the registry channel (`constrainRegistryProxy` currently *replaces* the preparer's constraints); then `contentHash` in the signed content.
2. Identity: derive it from the bootstrap proxy's own authenticated connection, handler class identity checked, single SPIFFE pin on the bootstrap proxy, provenance check on context items. Then the endpoint-and-identity shortcut hygiene.
3. Loader checks each jar's hash against the expected map; refuse directory URLs and `Class-Path` expansion; digests on cache miss only.
4. Registry (provider-private, read-only SPI) and the separate rejecting preparer; propagation rule.
5. DirtyChai (human-written): R7 first, then narrowed R1, write-once `readExternal`, the verified factory with R2 + R4, R6; R3 and R5 opt-in.
6. SCAP listener and isolation work.

### Resolved in discussion (final)
- The investigator conceded all of Opus's 13 points and, in the late exchange, 13 of Sonnet's 14
  (refining one); nothing remains disputed among the three. It adopted the provider-private map with
  a read-only SPI, the handler-class check, the single-SPIFFE pin on the bootstrap proxy only, the
  loader-side expected-hash map, and the corrected order above.
- **Inbound arguments.** Pinning an inbound proxy to the delivering peer's identity would refuse
  every legitimate third-party proxy. Corrected P5: the identity is the SPIFFE ID seen on the
  bootstrap proxy's **first authenticated call** (through a handler whose class is checked); that ID
  is then pinned as a single SPIFFE principal on later bootstrap calls and is the identity used for
  the loader record, the isolation pool key and the embedded principals.
  `SafeServiceRegistrar` stays the recommended route but is no longer the only one that can yield a
  verified identity.
- **Principal assertion** is not closed by R1: any `SecureClassLoader` subclass can call the
  protected `defineClass(name, b, off, len, cs, Principal[])`. Requirement moves to the source:
  DirtyChai `SecureClassLoader` merges caller-supplied principals only when the loader's own domain
  holds `DefineClassPermission`, otherwise it drops them. Until then policy authors must not rely on
  `grant principal X` entries with no codeBase or digest to grant code domains anything; static
  `DigestGrant` and principal grants also need a policy-side `issued()` call in
  `ConcurrentPolicyFile`.
- **Correction to a citation.** `SslEndpointImpl` lines ~770–778 index the *client's own* credentials
  by SPIFFE URI; that is not server verification and does not show a single-SPIFFE server pin works.
  Only `Utilities` ~687–707 supports it, partly. Whether the pin works end to end
  (`ConnectionContext` ~386 plus `FilterX509TrustManager` enforcing that exact URI) is **unproven**.
  The test must cover: (1) the right SVID succeeds; (2) another SVID with the same DN fails; (3) a
  certificate with two SPIFFE URIs, only the other one pinned, fails. If it fails, `ConnectionContext`
  needs a SPIFFE-aware branch.

### Needs the project lead
- Taking identity from the bootstrap proxy's own authenticated call (above) departs from SOW 8.2
  item 5 ("identity only from a `getServiceProxy()` response"), which is a requirement written in 8.2
  and not one of the lead's 8.3 decisions: needs sign-off.
- Whether remotely reconstructed domains (`RemoteContextCodec`, which uses the unverified
  constructor) should ever count as verified.
- Once the isolation flag is on by default the loader lives in the subprocess, so a parent-side
  preparer cannot look up loader→entry: the parent passes pool identity plus expected digests, which
  must themselves be provenance-checked. The investigator rescoped P2–P4 to in-JVM with the flag off
  and to inside the subprocess.

## Summary

| Point | Board claim | Verdict | Recommended solution (short) |
|---|---|---|---|
| P1 loader sharing | handler-equality fix is incomplete (parent chaining) | TRUE, but in-JVM parenting cannot isolate; only process isolation does | Keep only hygiene: take the shortcut only when `parent` is a loader this provider recorded and the recorded `ObjectEndpoint` equals the bootstrap proxy's (endpoint, not handler: handler equality includes `serverConstraints`); weak identity map loader→endpoint written at the two `putIfAbsent` sites. State parent-first class sharing as accepted. Route every foreign-identity download through the isolation router; unrouted today: the shortcut, the `PreferredClassProvider` path (and its permanent URI `DownloadPermission` grants), and nested foreign proxies inside the subprocess (isolation flag is off there). |
| P2 digests | only in the boot window | TRUE (`vr == null && serverPrincipals != null`) | Compute per-jar SHA-256 once, unconditionally, in the provider; the SCAP verdict for hex(H) is the attestation and H the key digest. Server digests become an optional cross-check that must equal the local ones and be exactly "SHA-256", else `SecurityException`. Build `DigestCodeSource(uri, signers, "SHA-256", H)` per non-directory jar; signers from the same `JarFile` the loader uses, or drop signers from the key (the digest covers signature files). Record identities only for the loader that won `CACHE.putIfAbsent`. |
| P3 spoofing | `defineClass(..., pd)` ungated; `unverified()` insufficient | TRUE (experiment: attacker loader defined its own bytes in a `ProtectionDomain` naming the victim loader and a trusted code source) | DirtyChai requirements R1–R7 below. |
| P4 weak path | policy cannot separate the paths | TRUE | Registry `net.jini.security.VerifiedCodebases` in jgdms-platform: `record(loader, CodeSource[], serverIdentity, hashes)` guarded by `RuntimePermission("recordVerifiedCodebase")`, recorded by the provider under `doPrivileged`; weak, identity-keyed, immutable entries; unprivileged `get()`. Preparers look up the proxy's loader: absent → `SecurityException("codebase not verified")`, present → `Security.grant(klass, entry.codeSources, ...)`; no fallback; a `requireVerifiedCodebase` flag defaulting to true. Rejected: a field on `PreferredClassLoader` (anyone with `createClassLoader` could assert identities). Stop `Security.grant(from, to)` turning digest-bound permissions loader-wide: report them separately from `getGrants` and propagate them only via the verified overload when the target's loader is registered, else drop. |
| P5 identity | taken from the delivering stream | Reviewer RIGHT on the mechanism; project lead RIGHT on the `SafeServiceRegistrar` route | Verified in code and by `PreferredProxyServerIdentityTest` and `PreferredProxyRelayStreamTest`. Fix: use a context identity only if the delivering call required `ServerAuthentication.YES`, and **pin it**: `bootstrapProxy.setConstraints(mc ∪ {ServerAuthentication.YES, ServerMinPrincipal(identity)})` before any bootstrap call, and the same on the unmarshalled smart proxy. A nested foreign proxy's server cannot authenticate as the delivering identity, so `getClassAnnotation` fails and it is refused. No `ServerSubject` (DER, plain JOSS, legacy lookup, app context) → no identity, no `DigestDomainGrant`, no `BootstrapPermission` pass; with isolation on, refuse. Pin the pool key in the subprocess too (it rebuilds `ServerSubject` from client-asserted principals after a string compare). |
| P6 inconclusive | retained grants evict each other; overload bypasses them | PARTLY | Keep the overload's refusal for marked loaders; no fallback. Keep a list of retained grants per loader (void all on invalidate) or replace with the union. A SAFE→DANGEROUS verdict change voids nothing today: wrap `DigestDomainGrant`s in `ExternallyVoidablePermissionGrant` indexed by hash, void and evict `CACHE` on DANGEROUS. |
| P7 other | — | ranked below | — |

### DirtyChai requirements (R1–R7), prose only
- **R1** `ClassLoader.preDefineClass`: if `pd.getClassLoader()` is non-null and is not the defining loader, throw `SecurityException`. This makes the loader check in `DigestDomainGrant` sound.
- **R2** `DigestCodeSource.unverified` defaults to true; cleared only by the package-private constructors `SecureClassLoader` uses; the public URL-computing constructors leave it true; `readExternal` sets it true first.
- **R3** Promotion of a plain `CodeSource` to a *verified* `DigestCodeSource` only if the loader's own class domain holds `DefineClassPermission` (or a "verifiedDigest" target); otherwise promote unverified.
- **R4** DirtyChai `DigestGrant` rejects `unverified()` (done on the JGDMS side).
- **R5** Strict mode: when defining with a verified digest domain, verify the bytes equal the jar entry for `name`, so the digest is of the bytes actually defined.
- **R6** A null computed digest (missing `file:` jar) must fail promotion.
- **R7** `public final SecureClassLoader.issued(ProtectionDomain)` (identity membership in `pdcache`); `DigestDomainGrant` requires it reflectively when present. No registries outside the loader, no public mutators.
Residual after R1–R4: `createClassLoader` plus `DefineClassPermission` is a trusted loader by policy (existing assumption); R1 can be bypassed by `Unsafe` / `JavaLangAccess` (bootstrap only).

### P7 ranked
1. Steady state (`vr != null`) runs no `BootstrapPermission` check and no server-digest cross-check; any server with a SAFE jar loads. Move `checkBootstrapPermission` out of the boot window.
2. `serverPrincipals` become principals of every domain the loader defines; with P5, B's code carries A's principal and matches `grant principal A` entries.
3. Static `DigestGrant` spoof on DirtyChai (R2, R4).
4. `RegistryVerdict.signedContent` signs urls + verdict + timestamp, not the content hash; add `contentHash` to the signed content so SCAP is a true digest attestation.
5. The `PreferredClassProvider` path and its permanent URI `DownloadPermission` grants.
6. `Key.equals` via handler constraints (duplicate loaders).
7. `tryGrantPerUriDigestGrants` static `DigestGrant`s are a second, un-revoked route to the same bytes (principal-bound; acceptable).
8. The subprocess trusts client-asserted principals after a string compare.

### Recommended order
1. JGDMS: endpoint-based shortcut and reverse map; unconditional SHA-256 digests with algorithm refusal; `VerifiedCodebases`; preparer reject; propagation rule; P6 union; move `checkBootstrapPermission` out of the boot window; tests.
2. JGDMS: P5 pinning in `resolve` and the subprocess; document `SafeServiceRegistrar` / `getServiceProxy()` as the only verified route.
3. DirtyChai (human-written): R1, R2, R4, R6 (closes the exploit), then R3, R7, R5; Zone-T tests.
4. SCAP: `contentHash` in the verdict's signed content; a verdict listener that voids and evicts on DANGEROUS; the rule that all foreign identities are isolated.
5. Isolation: route or loader-scope the `PreferredClassProvider` path, pin inside the subprocess, then turn the flag on by default.

### Self-attack
The endpoint check is only as good as `ObjectEndpoint` equality; the registry's security reduces to the policy granting one permission to the provider jar; pinning depends on `SslConnection` reporting the right principal and on the constraint being applied before the first bootstrap call; R1 can be bypassed by `Unsafe` / `JavaLangAccess` and by any loader code holding `DefineClassPermission`.

## Detail (line-level evidence, test specifications, alternatives)

# Dynamic-grant design: solutions to the board's open problems (detail)

Companion to the summary report. All line numbers are from the trees as read on 2026-10-18:
JGDMS branch `claude/sow-permissiongrant-domain-grants` (incl. `PreferredProxyServerIdentityTest`,
commit 141bef2d), DirtyChai trunk `af6bc5479`. Abbreviations: PPCP =
`jgdms-pref-class-loader/.../net/jini/loader/pref/PreferredProxyCodebaseProvider.java`;
Sec = `jgdms-platform/.../net/jini/security/Security.java`; DPP =
`.../net/jini/security/policy/DynamicPolicyProvider.java`; DC = DirtyChai
`src/java.base/share/classes`.

## Experiment

`/tmp/claude-0/-home-user/9c79f79c-26fe-586e-87d9-88adfa9eead4/scratchpad/exp1/PdLoaderGap.java`
(stock JDK, javac): an attacker-controlled `ClassLoader` subclass defines its own bytes with a
`ProtectionDomain(cs, perms, victimLoader, null)`. Output: defining loader = attacker, yet
`pd.getClassLoader() == victim` and the code source is the trusted URL. `ClassLoader.preDefineClass`
(DC `java/lang/ClassLoader.java:979-1003`) is textually identical in DirtyChai, so check 1 of
`DigestDomainGrant.implies` (loader identity by reference) is satisfiable by a forged domain. With
a *computed* `DigestCodeSource` (public ctor `DigestCodeSource(String,Certificate[],String)`,
DC `DigestCodeSource.java:402-406`, which downloads the trusted jar and leaves `unverified==false`,
field default `false` at :143) checks 3 and 4 pass too. Exploit precondition: `createClassLoader`
(and, for the static `DigestGrant` variant, nothing else).

## P1 — loader sharing (re-scoped per project lead)

Board claim: TRUE as far as it goes (shortcut compares path only, PPCP:1752-1762; fall-through
creates the child with `parent` = the delivering stream's loader, PPCP:2140-2149;
`PreferredClassLoader.loadClass` is parent-first unless preferred, :1153-1183). But in-JVM loader
parenting cannot isolate; only the per-SPIFFE-ID subprocess can.

(a) In-JVM hygiene (keep, cheap):
1. Shortcut condition becomes: `parent` is a loader this provider created/recorded AND the
   `ObjectEndpoint` of `Proxy.getInvocationHandler(bootstrapProxy)` equals the endpoint recorded
   for that loader. Implement with a weak identity map `loader -> ObjectEndpoint` written at the
   two `putIfAbsent` sites (PPCP:2152, :2255). Compare `BasicInvocationHandler.getObjectEndpoint()`
   (:1787), not the handler (`equals` includes `serverConstraints`, :1855-1859), so a self-proxy
   under other constraints is not duplicated. `Key` should likewise hold the endpoint, not the
   handler.
2. Never set `loader = parent` for a different endpoint; fall through. Fall-through for the same
   codebase under a *different* endpoint creates a child whose classes resolve parent-first to the
   first service's classes: same class identity, same domains, same grant. State this as the
   accepted in-JVM behaviour; do not fight it.
3. Because the shortcut precedes the isolation branch (PPCP:1814), fixing it also stops a foreign
   object with a familiar codebase bypassing the router.

(b) What `DigestDomainGrant` promises in-JVM: a domain gets the permissions only if it is a domain
of the granted class's loader, its code source is a loader-computed `DigestCodeSource`, and its
(Uri, signers, digest) is one the verifier supplied. It therefore denies: jars added later, changed
bytes, wrong signer sets, caller-asserted digests, domains of other loaders. It does NOT: narrow
below the loader; stop another remote object whose proxy resolves to the same classes (parent
chain or shared loader) from running that code with the grant; survive the DirtyChai gaps in P3.

(c) Required of the process-isolation path (the only trust boundary between identities):
- Every downloaded codebase of a foreign identity must reach `isolationRouter.route`; the only
  legitimate in-process returns are a `SERVICES_EXP` hit (first party) and the corrected shortcut.
- Router/pool key must come from a verified identity of the proxy's own server (P5); never from
  an unauthenticated context; refuse (do not fall through) when absent — already true at
  PPCP:1824-1831 but only when the flag is on.
- Still unrouted today: (i) the shortcut (fix above); (ii) the non-bootstrap
  `PreferredClassProvider.lookupLoader` path (:1746-1800, keyed by codebase+parent, no verdict,
  no identity, no router — any annotated class in any stream); (iii) inside the subprocess the
  flag must be off (`SubProcessReconstructionServer:138-142`), so a proxy of identity B nested in
  A's response is resolved in A's subprocess in-process; (iv) P5 — pool key from the delivering
  stream, so (iii) is keyed as A anyway. Requirement for (iii)/(iv): in the subprocess, pin the
  identity (`ServerMinPrincipal` of the pool key) on every bootstrap proxy resolved there; a
  foreign bootstrap proxy then fails authentication and is refused, and the client must obtain it
  through its own `getServiceProxy()` route (below). For (ii): either route annotated-class
  downloads through the same router or make `DownloadPermission` grants for a URL loader-specific
  (today the URI grants at PPCP:1922-1967 are permanent and apply to any later loader for that URL).
- SCAP angle: the verdict says whether code is safe to unmarshal, not whose it is. Isolation
  must be decided by identity (pool key), and only code with a SAFE verdict for the exact digest
  may be loaded in the pool. INCONCLUSIVE code must never enter a pool that also holds a verified
  grant holder (P6).

Test (outcome): two bootstrap proxies with different `ObjectEndpoint`s, same codebase; the second
arrives in a response unmarshalled with the first's loader as parent. Assert: shortcut not taken;
with isolation on, `route` is invoked with the second's identity; with isolation off, a loader
distinct from the first is created and `DigestDomainGrant` of the first does not imply a forged
same-loader domain for the second — and document (not assert) that classes are shared parent-first.

## P2 — source of verified digests

Board claim TRUE: local digests are only computed and compared inside the boot-window branch
(PPCP:1987-2125, inside `vr == null` and `serverPrincipals != null`). In steady state
(`vr != null`, :1977-1986) only `computeJarHash` runs, for the verdict lookup.

Mechanism:
1. In `resolve`, unconditionally compute `byte[][] local = computeIndividualJarDigests(codebase,
   "SHA-256")` once (:1237-1272), before any gate. Derive hex for `checkVerdictForJar` from the
   same bytes (today `computeJarHash` hashes the same raw bytes with SHA-256, :978-993, so the
   values are equal — make it one computation).
2. SCAP binding: the verdict is looked up by that hex; if SAFE, the registry has attested "bytes
   with SHA-256 H are safe". Use H as the grant key digest. Server-supplied digests
   (`getCodebaseDigest`/`getDigestOffsets`) become an *optional cross-check*: if present they must
   equal `local` and the algorithm must be exactly `"SHA-256"`, else refuse (SecurityException,
   never "proceed on BootstrapPermission alone" as :2034-2040 does).
3. Build one `CodeSource` per non-directory jar: `DigestCodeSource(uri, signers, "SHA-256", H)`
   via the 4-arg public constructor (unverified is fine for *supplied* identities; the builder only
   needs `hasDigest` and the algorithm). Signers: take them from the `JarFile` the loader will use
   (`RFC3986URLClassLoader` builds the per-entry `CodeSource` from `entry.getCertificates()`,
   :595-599). Safer alternative (board item 4): define keys with an empty signer set and let
   `CodeSourceKey.of` ignore signers when the key was built that way — the digest already covers
   the signature files. Recommend: signers excluded from the key when the provider cannot read
   them from the same JarFile; include otherwise.
4. Bind to the loader: after `CACHE.putIfAbsent` picks the winner, record
   `VerifiedCodebases.record(loader, codeSources, identity)` (P4 registry) only for the loader that
   this call created; a loser must not re-record.
5. Same-bytes guarantee on DirtyChai: `SecureClassLoader` recomputes the digest from the URL
   (DC `SecureClassLoader.java:389-392`). Both reads go through `JarResponseCache`
   (`DigestCodeSource.java:312-325`, `setUseCaches(true)` at :1024), and layer 2 `digestCache`
   (:1040-1054) refuses a changed artifact per JVM. The provider's read (`openUrlWithTimeout`,
   default `useCaches` true) also populates that cache. If any read bypasses it the check-time
   domain digest differs from H and the grant is simply not implied (fail closed), but the verdict
   gate was passed for H while H' loaded — so the stronger fix is DirtyChai R5 (P3): the loader
   verifies defined bytes against the digested jar.
6. SHA-256 end to end: builder already refuses other algorithms (`PermissionGrantBuilderImp:247`);
   add: `CodebaseAccessor.getCodebaseDigestAlgorithm()` must return `"SHA-256"` or be ignored with
   the whole server-digest cross-check refused; `CodeSourceKey.equals` compares algorithm;
   DirtyChai `SecureClassLoader` hard-codes `"SHA-256"`; recommend DirtyChai `DigestCodeSource.
   checkAlgorithm` keep its list but `SecureClassLoader` and policy parsing reject non-SHA-256
   digest grants (fail loudly in `DefaultPolicyParser`).

Defeat: an attacker who controls the codebase server and times a swap between the provider's read
and the loader's read, on a JVM where the reads are not served by one cache. Mitigated by R5.

Test: steady state (`VerdictRegistry` set), service with two jars; assert
`VerifiedCodebases.get(loader)` has two SHA-256 keys equal to the verdict hashes; swap jar B's
bytes on the server after `resolve` records and before B's first class loads → B's domain not
implied (DirtyChai) and, with R5, B's class fails to define.

## P3 — spoofing a verified DigestCodeSource

Board claims all TRUE (evidence: experiment above; DC `DigestCodeSource.java:143` default false,
:402-442 public computing ctors verified, :497-505 only 4-arg sets unverified, :777-862
`readExternal` sets `unverified = true` last at :861 with no `finally`; DC
`SecureClassLoader.java:177-182` promotes any `CodeSource` by downloading its URL at :390; DC
`DigestGrant.java:136-141` has no `unverified()` check; DC `ProtectionDomain.java:224-240`
loader is a plain field).

DirtyChai requirements (prose, human-authored):
- R1 `ClassLoader.preDefineClass`: if `pd != null && pd.getClassLoader() != null &&
  pd.getClassLoader() != this` → `SecurityException`. Also apply to the `defineClass0` path used by
  `Lookup.defineClass` (the lookup class's own domain, so already same loader) and to
  `JavaLangAccess.defineClass` callers (Proxy, LambdaForms pass null/own domains). This is the one
  change that makes check 1 of `DigestDomainGrant` sound.
- R2 `DigestCodeSource.unverified` defaults to `true`; cleared only by the package-private
  constructors `SecureClassLoader` calls. The public URL-computing constructors and
  `DigestCodeSource(CodeSource,String)` leave it `true` (they are still useful as *expected*
  identities). `readExternal` sets `unverified = true` first, and the no-arg ctor leaves it true.
- R3 `SecureClassLoader.getProtectionDomain`: promotion to a *verified* `DigestCodeSource` only
  when the loader's own class domain (`this.getClass().getProtectionDomain()`) is granted a
  permission — reuse `au.zeus.jdk.authorization.guards.DefineClassPermission` or add
  `DefineClassPermission("verifiedDigest")`; otherwise promote with `unverified = true`. The
  existing `LOAD_CLASS_ALLOW` check is about the loaded code, not the loader; both are needed.
- R4 DirtyChai `DigestGrant.implies(CodeSource,…)` rejects `unverified()` (SOW 7.6 says both trees;
  DirtyChai not done).
- R5 (strongest, optional strict mode): when a verified `DigestCodeSource` domain is used in
  `defineClass(name, bytes, …)`, verify `bytes` equals the entry `name.replace('.','/')+".class"`
  of the digested artifact (bytes available from `JarResponseCache` for remote jars, the file for
  `file:`). Then "digest of the bytes actually defined" holds exactly.
- R6 `computeDigest` returning null (missing `file:`, :1004-1015) must fail the promotion, not
  yield a verified code source with a null digest.
- R7 (cheap, defence in depth) `SecureClassLoader.issued(ProtectionDomain)`: `public final`,
  returns `pdcache.containsValue(pd)` by identity. JGDMS `DigestDomainGrant` may call it
  reflectively and require true; on a stock JDK it is absent and the grant is already void there.

Where NOT to put it: not in `ProtectionDomain` (constructing a domain must stay free); not a
"loader records the PDs it issued" registry outside the loader (spoofable); no new public mutators.

JGDMS side: `DigestDomainGrant.implies` adds step 1b: if `SecureClassLoader.issued` exists, require
it. Nothing else can be done in JGDMS without a spoofable API.

Defeat after R1-R4: code with `createClassLoader` *and* `DefineClassPermission` — then it is
trusted loader code by policy, the same assumption the JDK already makes about
`SecureClassLoader` subclasses.

Test (DirtyChai Zone T): the experiment above as a jtreg test asserting `SecurityException`;
forged-domain define into own loader with a URL-computed `DigestCodeSource` → `unverified()` true
and `DigestGrant` not implied; truncated `readExternal` stream leaves `unverified()` true.

## P4 — the weak path

Preparer learns verified identities from a registry that only trusted code can write:
`net.jini.security.VerifiedCodebases` (jgdms-platform): `record(ClassLoader, CodeSource[],
Principal[] serverIdentity, String[] contentHashes)` checks
`new RuntimePermission("recordVerifiedCodebase")` (or a dedicated `VerifiedCodebasePermission`)
against the caller's `AccessControlContext`; the provider calls it under `doPrivileged` from its
own domain, which the policy grants. Storage: `WeakHashMap<ClassLoader, Entry>` behind a lock
(`ClassLoader` has identity equals); entries immutable; `get(ClassLoader)` unprivileged. Not
spoofable because writing needs the permission and the key is the loader object. Rejected
alternative: a field/interface on `PreferredClassLoader` (anyone with `createClassLoader` can
construct a loader asserting identities; also the split package `net.jini.loader.pref` is not
sealed).

Reject: `BasicProxyPreparer.grant` / `VerifyingProxyPreparer.prepareProxy` (and the
`AdvisoryDynamicPermissions` branch) look up `proxy.getClass().getClassLoader()`; if absent →
`SecurityException("codebase not verified: refusing to grant")`; if present →
`Security.grant(klass, entry.codeSources, principals, perms)`. No fallback. `Security.grant(Class,
Permission[])` remains for explicit callers only. Make this the default in `BasicProxyPreparer`
with a constructor flag `requireVerifiedCodebase` defaulting true; the `AS_IS`/no-permissions
cases unchanged.

Propagation: `Security.grant(fromClass, toClass)` (Sec:1444-1467) re-issues as `CLASSLOADER`.
Fix in DPP: `getGrants` returns permissions tagged by grant kind (or add
`DynamicPolicy.getGrants(Class, Principal[], boolean includeDigestBound)`); `Security.grant(from,
to)` propagates digest-bound permissions only via the verified overload when `toClass`'s loader is
in `VerifiedCodebases`, otherwise drops them. Test: digest-domain grant on A; propagate to B whose
loader is unverified → B's loader-wide grant lacks those permissions; to a verified B → B's grant is
a `DigestDomainGrant`.

## P5 — server identity

Reviewer is RIGHT for the general mechanism; the project lead is RIGHT for the SafeServiceRegistrar
route. Evidence: `resolve` takes `serverPrincipals` from a `ServerSubject` in the *stream context*
(PPCP:1711-1745); that context is the delivering call's, populated by
`AtomicInvocationHandler.createMarshalInputStream` → `request.populateContext(context)`
(:282) → `SslConnection.populateContext` (:748-803) for the connection of the call whose response
is being read. `ProxySerializer(GetArg)` captures `arg.getObjectStreamContext()` (:373-387) and
`readResolve` passes it to `resolve` (:389-392). `AtomicMarshalOutputStream.defaultReplaceObject`
(:340-362) substitutes *every* `DynamicProxyCodebaseAccessor`/`ProxyAccessor` written to an Atomic
stream with an inline `ProxySerializer`, so a foreign smart proxy returned inside A's response is
resolved with A's `ServerSubject` — the lead's `PreferredProxyServerIdentityTest` shows exactly
this. The lead's later point (foreign proxies travel in a separate `MarshalledInstance`) holds only
for the *classes* (the `MarshalledInstance` inside the `ProxySerializer`), not for the identity:
that `MarshalledInstance` is unmarshalled with the delivering context (PPCP:2164). DER:
`DerProxySerializer.readResolve` (:158-167) uses a context that carries no `ServerSubject`
(`DerGetArg` only the decode-unit token), so DER-nested proxies have no identity. Plain
`BasicInvocationHandler.createMarshalInputStream` (:1405-1441) never calls `populateContext`, so
JOSS/non-atomic responses have no identity either. Legacy `ServiceRegistrar.lookup` results are
`MarshalledWrapper.get()` (RegistrarProxy:160-178) — no context.

SafeServiceRegistrar route (SDM `check`, :2022-2085): `lookUp` returns bootstrap proxies
(RegistrarProxy:182-196, local `java.lang.reflect.Proxy` classes, no download); the
`bootstrapPreparer` prepares one; `getServiceProxy()` is a call on the prepared proxy to the
service's *own* endpoint; the smart proxy comes back as a `ProxySerializer` in that response, so
the context `ServerSubject` is the service's own authenticated identity, and `mc` (the prepared
constraints) is applied to the embedded bootstrap proxy (PPCP:1722-1728). Correct.

Fix (make this the only verified route):
1. `resolve` accepts a `ServerSubject` as identity only if the delivering call's constraints
   required `ServerAuthentication.YES` *and* the identity is pinned on the embedded bootstrap proxy:
   `bootstrapProxy.setConstraints(mc ∪ {ServerAuthentication.YES, ServerMinPrincipal(identity)})`
   before any bootstrap call. Then a nested foreign proxy's own server cannot authenticate as the
   delivering identity and `getClassAnnotation()` fails → refused (fail closed). Same pinning on
   the smart proxy's constraints after unmarshal (PPCP:2173-2189) so it can only talk to that
   identity.
2. No `ServerSubject` (DER, JOSS, legacy lookup, `MarshalledInstance` with app context): no
   identity → no `DigestDomainGrant`, no `BootstrapPermission` success, and with isolation on,
   refuse (already). Document: use `SafeServiceRegistrar`.
3. Residual enforcement: a `ProxySerializer` reached other than through `getServiceProxy()` on a
   prepared bootstrap proxy still carries the delivering identity. Pinning (1) converts that into
   a refusal when identities differ, and into a correct attribution when the nested proxy really is
   the same server's object (the only case the shortcut should serve).
4. Subprocess: `SubProcessReconstructionServer` rebuilds the `ServerSubject` from
   client-asserted `req.serverPrincipals` (:294-319); apply (1) there with the pool key as the
   pinned principal, so the subprocess cannot be fed another identity's proxy.

Test pinning P5: service B's bootstrap proxy nested in A's response, A ≠ B SPIFFE IDs; assert
`resolve` throws (B cannot authenticate as A) and no loader/grant is created; the same B proxy
obtained via `getServiceProxy()` on the prepared B bootstrap proxy resolves with B's identity.

## P6 — INCONCLUSIVE and retained grants

- `Security.grant(Class, CodeSource[],…)` throws for a marked loader (Sec:1346-1349): correct, and
  the preparer must not catch and fall back.
- `grantRetainedInconclusiveLoaderGrant` replaces and voids the previous grant for the loader
  (Sec:1527-1532); `SecurityInconclusiveGrantLifecycleTest:46-66` asserts this as intended. It is
  a correctness hazard: `BasicProxyPreparer` grants P1, a later `VerifyingProxyPreparer` or the
  advisory `PERMISSIONS.LIST` path grants P2 and P1 disappears. Fix: keep a `List` per loader and
  void all on invalidate, or build the replacement from the union of the previous grant's
  permissions and the new ones (same principals), then void the old. Test: two sequential grants;
  both permissions present; `invalidateInconclusiveProxyLoaderGrants` removes both.
- Marker is per loader and permanent: a later SAFE re-verdict needs a new loader (cache eviction
  after 60 s of unreachability); acceptable, fail closed.
- Missing: SAFE → DANGEROUS. Nothing voids `DigestDomainGrant`s or evicts loaders on a verdict
  change (no `registerVerdictListener` use in the provider). Wrap each `DigestDomainGrant` in
  `ExternallyVoidablePermissionGrant`, index by content hash in `VerifiedCodebases`, and void on a
  DANGEROUS `VerdictEvent`; also evict the `CACHE` entry. `VERDICT_CACHE` (TTL 5 min, used only
  when the registry is unreachable, PPCP:1657-1668) is unaffected; an INCONCLUSIVE from cache
  still marks the loader.

## P7 — other findings, ranked

1. Steady state has no `BootstrapPermission` check and no server-digest cross-check: both live in
   the boot-window `else` (PPCP:1987-2125). With a registry set, any server (authenticated or not)
   whose jar has a SAFE verdict is loaded. Move `checkBootstrapPermission` out of the boot branch.
2. `serverPrincipals` are embedded as principals of every domain the loader defines
   (PreferredClassLoader → `defineClassWithPrincipals`, RFC3986URLClassLoader:1224-1244; DC
   `SecureClassLoader.getProtectionDomain` merges them :346-355). With P5, a nested B proxy's
   code carries A's principal, so static `grant principal A {…}` policy entries apply to B's
   code. Fixed by P5 pinning.
3. Static `DigestGrant` spoof (P3, R2/R4): exploitable today on DirtyChai by code with
   `createClassLoader`.
4. `RegistryVerdict.signedContent` (:351-366) signs urls+verdict+timestamp, not the content hash;
   the hash→verdict binding rests on the transport (`REGISTRY_METHOD_CONSTRAINTS`). For SCAP to be
   an attestation of a digest, add `contentHash` to the TBS (STD-002 change) and have the provider
   check it equals the local H.
5. `PreferredClassProvider.lookupLoader` path: annotated classes download with no verdict, no
   identity, no router; permanent URI `DownloadPermission` grants let any later loader for the URL
   define. On stock JDK there is no `digestCache` either.
6. `Key.equals` via handler includes `serverConstraints`: same object, other constraints →
   duplicate loader/class identity; compare `ObjectEndpoint` (P1).
7. `tryGrantPerUriDigestGrants` issues *static* `DigestGrant`s with `UnresolvedPermission
   net.jini.loader.LoadClassPermission` — location-independent, not revoked, and (via `Security.
   grant(PermissionGrant)`) they outlive the loader; acceptable because principal-bound, but note
   they are the second route to permissions for the same bytes.
8. Subprocess trusts client-asserted `serverPrincipals` (SubProcessReconstructionServer:309-319)
   after only a pool-key string comparison; the client is the trusted party here, but pin per P5.

## Implementation order

1. JGDMS: P1(a) endpoint-based shortcut + reverse map; P2 unconditional digests + SHA-256 refusal;
   `VerifiedCodebases` registry; preparer reject semantics (P4); `grant(from,to)` propagation
   rule; P6 union-of-grants; P7.1 move `checkBootstrapPermission` out of the boot window. Tests as
   above plus `PreferredProxyServerIdentityTest` extended with the pinning case.
2. JGDMS: P5 pinning (`ServerAuthentication.YES` + `ServerMinPrincipal`) in `resolve` and in the
   subprocess; documentation that `SafeServiceRegistrar`/`getServiceProxy()` is the only verified
   route; DER/JOSS routes yield no identity.
3. DirtyChai (human-written): R1, R2, R4, R6 first (small, closes the exploit); R3, R7 next; R5 as
   strict mode. Zone-T tests for each.
4. SCAP: add `contentHash` to `RegistryVerdict` TBS; verdict listener → void grants/evict loaders
   on DANGEROUS; decide routing-by-identity rule for which proxies must be isolated (all foreign
   identities; first party via `SERVICES_EXP` only).
5. Process isolation: route the `PreferredClassProvider` path or make `DownloadPermission` grants
   loader-scoped; in-subprocess pinning; then enable the flag by default.
