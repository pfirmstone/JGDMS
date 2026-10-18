# SOW — Mirror `org.apache.river.api.security` from DirtyChai, then grant dynamic proxy permissions by ProtectionDomain

**Status:** JGDMS side implemented 2026-10-18 (see section 6). Scope written 2026-10-18
with AI assistance (Claude), from reading the JGDMS and DirtyChai trees as they
stood that day. Line numbers are approximate and must be re-checked.

**Decision already taken (Peter Firmstone, 2026-10-18):** a dynamic grant made
to a proxy applies to each `ProtectionDomain` of the proxy's verified
codebase. It no longer applies to the class loader as a whole. Where a codebase
lists several jars, every jar's domain is covered, and no domain outside the
codebase is.

---

## 0. Why

1. **Dynamic grants are made per class loader.** `BasicProxyPreparer` and
   `VerifyingProxyPreparer` call `Security.grant(proxy.getClass(), ...)`.
   `DynamicPolicyProvider.grant(Class, Principal[], Permission[])` builds the
   grant with `.context(PermissionGrantBuilder.CLASSLOADER)`, as does
   `Security.grantRetainedInconclusiveLoaderGrant`. That context builds a
   `ClassLoaderGrant`, whose `implies(ProtectionDomain)` reduces to
   `pd.getClassLoader().equals(loader)`. The result:
   - every domain the loader defines gets the permissions, whatever its code
     source, signers or digest, including domains outside the codebase that
     was verified;
   - nothing about the grant records which code was actually trusted.
2. **Grant propagation can't see signer or digest grants.**
   `DynamicPolicyProvider.getGrants(Class, Principal[])` evaluates
   `g.implies(cl.getClassLoader(), principals)`. `CertificateGrant` and
   `DigestGrant` return `false` for a class loader, because a loader carries no
   signers or content. So `Security.grant(fromClass, toClass)` never
   propagates permissions that came from a `signedBy` or `digest` grant. It has
   to evaluate the class's `ProtectionDomain` instead.
3. **The two copies of the package have drifted.** DirtyChai's `java.base`
   defines 13 classes in `org.apache.river.api.security`. On DirtyChai, those
   are the ones that run: JGDMS's copies of the same 13 are shadowed, and only
   JGDMS-only classes load from the class path, through DirtyChai's
   split-package fall-through (see DirtyChai `JGDMS_COMPATIBILITY.md`). On a
   stock JDK, JGDMS's copies run. Today the two give different answers
   (section 1), so the same policy behaves differently depending on the JVM.

## 1. Part A — mirror DirtyChai's grant semantics into JGDMS

JGDMS must keep running on a stock JDK (17+). So "mirror" means matching
DirtyChai's behaviour, not copying its source: where DirtyChai uses a
DirtyChai-only API, JGDMS keeps or adds a reflective or portable equivalent.
The comparison below ignores whitespace and line endings (JGDMS uses CRLF,
DirtyChai LF).

### A.1 Classes in both trees that differ

| Class | DirtyChai behaviour to mirror | JGDMS constraint |
|---|---|---|
| `CertificateGrant` | `impliesSigners(CodeSource)`: null code source → false; no signers required → true; unsigned code → false (today JGDMS throws `NullPointerException` from `Arrays.asList(null)`); otherwise every signer must be present. `implies(CodeSource, Principal[])` uses it. | Pure Java; port as is. |
| `URIGrant` | `implies(CodeSource, ...)` checks `impliesSigners` before matching the location, so `codeBase` + `signedBy` enforces the signer. `implies(ClassLoader, ...)` returns false whenever `certs` is non-empty. A grant URI that fails to parse is fail-closed. | Keep `org.apache.river.api.net.Uri` and the `Uri.urlToUri(url)` conversion: `CodeSource.getUriLocation()` exists only on DirtyChai. Replace the current `printStackTrace` with fail-closed handling. Decide whether the parser skips the grant or throws, and keep that the same in both trees. |
| `DigestGrant` | Matches on principals, algorithm, digest bytes and signers. **The location is ignored**, because the URL is not identity. | JGDMS's copy currently "delegates to super for principal and URI checks", so it does match the location; remove that. Keep the reflective access to `DigestCodeSource`, which is absent on a stock JDK, and keep the fail-closed `false` when it's missing. Add the `impliesSigners` call. |
| `PermissionGrant` | `SocketPermission.init()` called directly. | Keep JGDMS's reflective `initSocketPermission`. Otherwise this is only a javadoc difference. |
| `PrincipalGrant` | — | Port in the **other direction**: JGDMS's policy-text escaping (backslash, quote, `\n`, `\r`, `\t`) is more complete than DirtyChai's (backslash and quote only). Keep JGDMS's `SubjectDomain` branch in `getPrincipals`, which is JGDMS-only. |
| `PermissionGrantBuilder` | Package-private constructor. | **Don't mirror this**: it would break public API for anyone subclassing it. Run `check-api-compat.sh`. Only javadoc otherwise. |
| `PermissionGrantBuilderImp` | — | Formatting only. |
| `UnresolvedPrincipal` | `WILDCARD` is package-private `"*"`. | Keep `public static final WILDCARD` (API), and keep `org.apache.river.impl.Messages`. |
| `PermissionDelegate`, `ScalableNestedPolicy`, `package.html` | — | Javadoc only; optional. |
| `ClassLoaderGrant`, `ProtectionDomainGrant` | — | Identical in both trees today; section B changes both. |

### A.2 Constraints from the split package (DirtyChai)

- **Same-package access doesn't cross the split.** JGDMS-only classes in this
  package (`ExternallyVoidablePermissionGrant`, `LeasedPermissionGrant`,
  `OneShotLeasedPermissionGrant`, `ConcurrentPolicyFile`, ...) are defined by
  the application loader, which makes them a different runtime package from
  `java.base`'s classes of the same name. They can use only `public`/`protected`
  members of `PermissionGrant` and its relatives. Check that none of them
  calls a package-private member or extends a package-private grant class.
  `PrincipalGrant`, `ProtectionDomainGrant` and the other concrete grants are
  package-private.
- **Any new grant class must exist in both trees.** It has to live in DirtyChai
  `java.base` and in JGDMS, and be built through `PermissionGrantBuilder`, not
  constructed directly. A JGDMS-only subclass of a package-private grant would
  fail with `IllegalAccessError` on DirtyChai.

### A.3 Done when

- Both trees give the same answers for the same inputs (section 4, tests T1–T3),
  on DirtyChai and on a stock JDK 21.
- `check-api-compat.sh` passes, or every break is recorded in
  `JGDMS-API-Compatibility-Accepted-Breaks.md`.

## 2. Part B — dynamic grants by ProtectionDomain

### B.1 Required semantics

A dynamic grant made through `Security.grant(Class cl, ...)` (and
`DynamicPolicy.grant`) applies to a `ProtectionDomain` `pd` if and only if all
of these hold:

1. `pd.getClassLoader() == cl.getClassLoader()` (same loader instance);
2. `pd`'s code source belongs to the codebase that loader was created for,
   which is the codebase the proxy preparer verified;
3. the grant's principals are satisfied, as now.

There is one more requirement, for code-source identity. The first domain seen
for each codebase location pins that location's identity: Uri, signers, and
digest when the code source is a `DigestCodeSource`. A domain at the same
location with a different identity is not implied. That can't happen with a
single `SecureClassLoader`, which caches one domain per code source, but it
also covers domains rebuilt by a combiner such as `SubjectDomainCombiner`.

Domains are created lazily, on the first class defined from each jar, so a jar
listed in the codebase may have no domain yet when the grant is made. The grant
therefore has to match domains as they are checked, not be a fixed list of
domain instances. Each domain that matches the rules above is covered when it
appears.

### B.2 Where the codebase comes from

- `PreferredClassLoader` extends `RFC3986URLClassLoader` extends
  `URLClassLoader`, so `getURLs()` gives the codebase locations. Convert them
  with `Uri.urlToUri`, never `URL.equals`, which can make DNS calls.
- **For any other loader, fall back to the strictest grant:** only
  `cl.getProtectionDomain()`. Log the fallback at `CONFIG`.
- **The proxy preparer does not need to pass the codebase,** because the loader
  is the record of what was verified. If a future preparer verifies jars
  individually (for example from content digests), pass the identities through
  a new `Security.grant` overload. That's out of scope here.

### B.3 Changes

| Where | Change |
|---|---|
| DirtyChai `java.base` and JGDMS: `org.apache.river.api.security` | New grant kind with the semantics of B.1, for example `CodebaseDomainGrant extends ProtectionDomainGrant`, plus a new `PermissionGrantBuilder` context constant, for example `CODEBASE_DOMAIN = 7`, with the same value in both trees. It holds a weak reference to the loader, the set of codebase `Uri`s, and a concurrent map from `Uri` to pinned code-source identity. Serialise it through the builder proxy pattern, as the existing grants do. |
| `ProtectionDomainGrant.impliesCodeSource` (both trees) | Compare code-source identity as Uri + signers + digest. On DirtyChai, use `CodeSourceKey`, which is now shared, rather than `URL.toURI()`. In JGDMS, use `Uri.urlToUri`, plus reflective digest access when `DigestCodeSource` is present. Today the digest is ignored, and `toURI()` failures fall through. |
| `DynamicPolicyProvider.grant(Class, ...)` | Build with the new context rather than `CLASSLOADER`. `pgb.clazz(cl)` stays: it provides the anchor domain and the loader. Collecting the codebase (B.2) belongs in the builder, so every caller gets it. |
| `Security.grantRetainedInconclusiveLoaderGrant` and `inconclusiveProxyLoaders` | Same context change. The marker can stay keyed by loader: it records which loader loaded an INCONCLUSIVE codebase. The retained grant itself becomes domain-scoped. |
| `DynamicPolicyProvider.getGrants(Class, Principal[])` | Evaluate `g.implies(cl.getProtectionDomain())`, not `g.implies(loader, principals)`, so that `signedBy` and `digest` grants are seen. Principals must still be honoured: pass a domain carrying the supplied principals, as `getPermissionGrants` does. |
| `ClassLoaderGrant` | Keep it for compatibility: deserialising old builders, and explicit callers. Deprecate it in javadoc. Nothing in JGDMS should create one after this change. |
| `service-starter` `AggregatePolicyProvider` / `LoaderSplitPolicyProvider`, `tools/security-policy-debug` `DebugDynamicPolicyProvider` | These delegate `grant`/`getGrants`; check that they pass the class through rather than reducing it to a loader. |
| `VerifyingProxyPreparer` advisory branch (`AdvisoryDynamicPermissions`) | No change: it calls `Security.grant(klass, ...)` and inherits the new scope. |

### B.4 Behaviour changes to document

- **Domains outside the codebase lose dynamic grants.** That means classes the
  proxy's loader defines through `defineClass` from a source not in its URLs,
  which today inherit the loader grant. That's intended.
- **Propagation now carries signer and digest permissions.**
  `Security.grant(fromClass, toClass)` will propagate permissions that came from
  `signedBy` or `digest` grants, which it silently dropped before. That's
  intended, but it widens what propagation hands on, so note it in the release
  notes.

## 3. Out of scope

- Content-addressed dynamic grants (a dynamic grant expressed as a
  `DigestGrant`). Rejected: a dynamic grant vouches for a specific verified
  service's proxy, and shared proxy jars would carry one service's permissions
  to another service's proxy in a different loader.
- Moving `SecureClassLoader`'s pre-promotion read check to the loader's
  context. That's a separate decision, discussed 2026-10-18.

## 4. Tests

The AI-authored jtreg tests in DirtyChai `test/jdk/ai/` cover the DirtyChai
side. JGDMS needs equivalents in its own test tree that also run on a stock
JDK.

- **T1, signers (exists):** DirtyChai `test/jdk/ai/org/apache/river/api/security/GrantSignerTest.java`.
  It covers `signedBy`, `codeBase` + `signedBy` and `digest` + `signedBy`
  against unsigned, other-signer and null code sources, `implies(ClassLoader)`,
  and an unresolved alias. Port it to JGDMS. On a stock JDK the digest cases
  must report "not implied", never throw.
- **T2, digest location independence:** the same digest at another location
  is implied; a different digest, or a plain `CodeSource`, is not. On a stock
  JDK: not implied (fail-closed).
- **T3, parity:** run T1 and T2 on DirtyChai with JGDMS on the class path and
  confirm the results match a stock-JDK run.
- **T4, multi-jar codebase:** one `PreferredClassLoader` over jars A and B;
  grant to a class in A. Expect:
  - A's domain is implied;
  - B's domain is implied, including when B's first class is loaded **after**
    the grant;
  - a domain the same loader creates for a location outside the codebase is
    not;
  - a second loader over identical jars is not;
  - a `SubjectDomainCombiner`-rebuilt domain of A is.
- **T5, propagation:** a `digest` grant and a `signedBy` grant in the policy
  file, then `Security.grant(fromClass, toClass)`. Their permissions must reach
  `toClass`; today they don't.
- **T6, inconclusive path:** a retained INCONCLUSIVE grant is domain-scoped, and
  `invalidateInconclusiveProxyLoaderGrants()` still voids it.
- **T7, regression:** the existing JGDMS QA policy-provider tests
  (`dynamicPolicyProvider` and related) pass unchanged except where B.4
  intends a change. Record each expected difference in the test that covers it.

## 5. Order of work

1. A.1 in DirtyChai first, where it isn't done already: the reverse port of
   `PrincipalGrant` escaping, and `DigestGrant` signer handling. Then JGDMS. Then
   T1–T3.
2. The new grant kind and the `impliesCodeSource` identity fix, in both trees
   with the same context constant. T4.
3. Switch `DynamicPolicyProvider.grant`, `Security`'s retained grant and
   `getGrants`. T5 and T6.
4. Run the full QA suite on DirtyChai and on a stock JDK (T7), then update
   `JGDMS_COMPATIBILITY.md` and the release notes (B.4).

## 6. Implementation status (JGDMS, 2026-10-18)

Implemented with AI assistance (Claude) on branch
`claude/sow-permissiongrant-domain-grants`.

**Part A (JGDMS)**
- `CertificateGrant.impliesSigners` added; `URIGrant` and `DigestGrant` use it.
  Unsigned code no longer throws `NullPointerException`; `codeBase` + `signedBy`
  and `digest` + `signedBy` enforce the signer. `URIGrant.implies(ClassLoader)`
  is false whenever signers are required.
- `URIGrant` fails closed on an unparseable URI (`SecurityException`, as
  DirtyChai does). Skipping it would have widened the grant, to every code
  source if it was the only one.
- `DigestGrant` already ignored the location; javadoc corrected.
- Not mirrored, as the SOW directs: `PermissionGrantBuilder` constructor
  visibility, `initSocketPermission`, `UnresolvedPrincipal.WILDCARD`.

**Part B (JGDMS)**
- New `CodebaseDomainGrant` (package-private) and
  `PermissionGrantBuilder.CODEBASE_DOMAIN = 7`; `CLASSLOADER` and
  `ClassLoaderGrant` are deprecated/documented, nothing creates one.
- New package-private `CodeSourceKey` (Uri + signers + digest, reflective
  digest access). `ProtectionDomainGrant.impliesCodeSource` uses it and fails
  closed when identity cannot be established.
- The builder collects the codebase from `URLClassLoader.getURLs()`; any other
  loader gets the anchor-domain-only grant, logged at `CONFIG`.
- `DynamicPolicyProvider.grant`, `Security.grantRetainedInconclusiveLoaderGrant`
  use `CODEBASE_DOMAIN`; `DynamicPolicyProvider.getGrants` evaluates the class's
  `ProtectionDomain` (carrying the supplied principals). `AggregatePolicyProvider`,
  `LoaderSplitPolicyProvider` and `DebugDynamicPolicyProvider` pass the `Class`
  through unchanged, so need no change.
- `CodebaseDomainGrant.implies(ClassLoader, ...)` is false: a loader carries no
  code identity.

**Tests:** `GrantSignerTest` (T1, T2), `CodebaseDomainGrantTest` (T4, T5),
`SecurityInconclusiveGrantLifecycleTest` (T6), and
`PreferredClassLoaderCodebaseGrantTest` (T4 with a real `PreferredClassLoader`).
The existing `org.apache.river.api.security`, `net.jini.security` and
`api.net` unit tests pass unchanged (T7, unit level).

**DirtyChai side**

DirtyChai trunk (`a6782e968`) does not contain the behaviour section 1 describes
(`impliesSigners`, `CodeSourceKey`, `GrantSignerTest`). DirtyChai's `CLAUDE.md` requires
contributions there to be human-written, so no DirtyChai code was committed. The changes
needed are described in `DirtyChai-Required-Changes-PermissionGrant-SOW.md`.

**Not done / not verifiable here**
- The QA harness (`qa/`) and a Maven build: the sandbox cannot resolve the
  GitHub Packages `Pack200-ex-openjdk` dependency, so tests were compiled and
  run with plain `javac`/JUnit.
- T3 (parity run on DirtyChai with JGDMS on the class path) needs a DirtyChai
  build.
- `check-api-compat.sh` needs built jars; the change is additive (one new
  constant, one deprecation, new package-private classes).

**Release notes (B.4)**
- Dynamic grants to a proxy no longer apply to domains the proxy's loader
  defines from a source outside its codebase.
- `Security.grant(fromClass, toClass)` now propagates permissions that came from
  `signedBy` and `digest` grants.
- An unparseable `codeBase` URI in a grant now raises `SecurityException`.
