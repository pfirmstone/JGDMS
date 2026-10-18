# SOW — Mirror `org.apache.river.api.security` from DirtyChai, then grant dynamic proxy permissions by ProtectionDomain

**Status:** JGDMS side implemented 2026-10-18 (see section 6). **Revised the same
evening (section 7): the derived-codebase grant is replaced by `DigestDomainGrant`; where
section 7 conflicts with Part B or section 6, section 7 governs.** Scope written 2026-10-18
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

**Review board (2026-10-18)**

Three independent reviewers (different models) examined the branch. Findings acted on:
- The anchor's loader is the loader of the *class*, not of its `ProtectionDomain`: a
  `java.lang.reflect.Proxy` class, which is what `BasicProxyPreparer` passes to
  `Security.grant`, has the shared all-permission domain, whose loader is null. The builder's
  `clazz(Class)` now records `cl.getClassLoader()` and `DynamicPolicyProvider.getGrants`
  evaluates the domain with that loader. (The old `ClassLoaderGrant` was equally ineffective for
  Proxy classes.) Test: `proxyClassAnchorUsesTheLoaderOfTheClass`.
- A domain sharing the anchor's `CodeSource` is implied before a location Uri is required
  (anchor with no location).
- A shadowing `PermissionGrantBuilder` that rejects `CODEBASE_DOMAIN` (DirtyChai trunk, whose
  `context()` allows at most 6) now falls back to a `PROTECTIONDOMAIN` grant, with a warning,
  instead of throwing from every `Security.grant`. **DirtyChai must be updated before JGDMS
  relies on codebase-wide grants there.**
- `CodebaseDomainGrant.implies(CodeSource, ...)` is false (no loader), and the last accepted
  `CodeSource` is cached by reference so repeated checks do not rebuild the identity.

- **The "first domain seen pins the identity" rule (B.1) was removed**, on the unanimous advice of
  the three reviewers: it made the result depend on class-loading order (one jar can yield several
  domains at one location, each with its own certificates), code holding the loader could pin a
  forged identity and permanently deny the legitimate domain, and on DirtyChai
  `SecureClassLoader` probes permissions with a plain `CodeSource` before promoting it to a
  `DigestCodeSource`, so the probe would pin an identity the real domain never has. A domain is
  now implied if it has the anchor's loader and either the anchor's identity (Uri + signers +
  digest, which covers combiner rebuilds) or a location in the codebase. No privilege escalation
  was found without the pin: a forged domain backs no class, and the access-control context
  intersection still applies. **This departs from B.1's text; revert if you want it back.**
  `CodeSourceKey` now compares signers as a set.

Open for the project lead: the choice of throwing versus skipping an unparseable `codeBase` URI
(`ConcurrentPolicyFile.refresh` catches the exception, so a typo leaves the old policy in force).

**Release notes (B.4)**
- Dynamic grants to a proxy no longer apply to domains the proxy's loader
  defines from a source outside its codebase.
- `Security.grant(fromClass, toClass)` now propagates permissions from `signedBy`, `digest`,
  `codeBase` and principal grants that match the source class's `ProtectionDomain`, but only
  grants held as dynamic grants (`Security.grant(PermissionGrant)`); grants read from the
  policy file are not consulted by `getGrants`, so T5 as written ("in the policy file") is
  not met.
- Dynamic grants anchored on a jar class are no longer visible to `getGrants` of a `Proxy` class
  of the same loader (they were loader-wide).
- Jars reached through `Class-Path` manifest entries or an index are not in the loader's URLs
  and get no dynamic grant.
- An unparseable `codeBase` URI in a grant now raises `SecurityException`.

## 7. Revision (2026-10-18, evening): `DigestDomainGrant`

**Status:** design agreed with Peter Firmstone in discussion on 2026-10-18; not
implemented. Written with AI assistance (Claude). Supersedes B.1–B.3 and the parts of
section 6 listed in 7.9.

### 7.1 Why the design changed

- **Identity should come from what was verified, not from what loads.** The
  section 6 grant takes the codebase from `URLClassLoader.getURLs()`, which gives
  locations only. The provider already has each jar's verified digest from the
  bootstrap proxy (`CodebaseAccessor.getCodebaseDigest()` / `getDigestOffsets()`), so
  the grant can require those identities instead of inferring them.
- **Defence in depth against classes loaded later.** The provider verifies the codebase
  once, before any class loads. Afterwards a loader-wide grant covers anything the
  loader defines: a jar fetched lazily, a URL added with `URLClassLoader.addURL`, or
  bytes defined through a path that skipped the check. The permission check must
  re-establish, on every access, that the domain's code source is one that was
  verified.
- **Domains are lazy.** A jar's `ProtectionDomain` is created when its first class is
  defined, which may be mid-call (responses are unmarshalled with the proxy's loader as
  default loader) or never. So the grant matches identities as domains appear, not a
  list of domain instances.
- **Verified means a digest.** A digest proves this exact jar; signers alone prove only
  "some jar this key signed" (rollback to an older signed version, and an annotation can
  name any key unless it chains to a local trust anchor). DirtyChai computes a digest for
  every jar domain, so requiring one excludes only directory codebases and jars whose
  digest could not be computed.

### 7.2 Two paths

| Caller | Grant | Implies a `ProtectionDomain` `pd` when |
|---|---|---|
| Codebase verified with per-jar digests (new overload, 7.4) | `DigestDomainGrant` | `pd`'s loader is the proxy's loader, the principals are satisfied, and `pd`'s `CodeSourceKey` is one of the supplied keys |
| Anything else: `Security.grant(Class, Permission[])`, signers-only or location-only codebases, non-bootstrap proxies | `ClassLoaderGrant`, as before this SOW | `pd`'s loader is the proxy's loader |

"Permission is limited" on the second path is enforced by policy, not code: the caller
of `Security.grant` needs `GrantPermission` for what it grants, so the policy should
make only low-risk permissions grantable on the unverified path and keep the rest
grantable only through the verified overload. Document this in `JGDMS_COMPATIBILITY.md`
and `SECURITY_MODEL.md` (DirtyChai) and the JGDMS security docs.

### 7.3 `DigestDomainGrant` (both trees, package-private)

```java
final class DigestDomainGrant extends ProtectionDomainGrant {
    private final WeakReference<ClassLoader> loader;   // cl.getClassLoader() of the class, not of its domain
    private final Set<CodeSourceKey> verified;          // every key has a digest

    public boolean implies(ProtectionDomain pd) {
        ClassLoader l = loader.get();
        if (l == null || pd == null || pd.getClassLoader() != l) return false;   // 1. loader first
        if (!implies(getPrincipals(pd))) return false;                           // 2. principals
        CodeSource cs = pd.getCodeSource();
        if (!(cs instanceof DigestCodeSource d) || d.unverified()) return false; // 3. a computed digest
        CodeSourceKey key = keyOf(cs);                                           // null if unparseable
        return key != null && verified.contains(key);                            // 4. Uri + signers + digest
    }
    public boolean implies(ClassLoader cl, Principal[] p) { return false; }      // no code identity
    public boolean implies(CodeSource cs, Principal[] p)  { return false; }      // no loader
}
```

- **Extends `ProtectionDomainGrant`, not `ClassLoaderGrant`.** `ClassLoaderGrant` takes
  the loader from the anchor's domain (wrong for `java.lang.reflect.Proxy` classes),
  implies a bare loader, treats any same-loader grant as equivalent in
  `impliesEquivalent`, and its `equals` accepts any `instanceof ClassLoaderGrant`
  (asymmetric with a subclass). Every one of those would need overriding, and an
  `instanceof ClassLoaderGrant` check would match the narrower grant.
- **Lifetime.** The loader is held weakly; `isVoid()` is true once it is cleared.
  `SecureClassLoader.pdcache` holds domains strongly, so this is the same moment a
  `ProtectionDomainGrant` per jar domain would become void: the grant goes with the
  proxy.
- **On DirtyChai** step 3 uses `DigestCodeSource` directly; **in JGDMS** it is
  reflective (as `DigestGrant` and `CodeSourceKey` already are) and fails closed on a
  stock JDK, where no domain carries a digest. On a stock JDK the provider therefore
  uses the `ClassLoaderGrant` path.
- **Key comparison.** `CodeSourceKey.equals` compares certificates with
  `Arrays.equals`, which is order-sensitive. The provider must build the supplied keys'
  certificates in the order `CodeSource` gets them from `getCodeSigners()`, or
  `CodeSourceKey` must compare certificates as a set (that also changes `pdcache`, but
  only to merge domains with identical signers). Decide one; test it.
- **Mixed-signer jars fail closed.** `URLClassLoader.defineClass` builds the code
  source per entry from that entry's signers, so a jar whose entries carry different
  signer sets has several domains at one location. Only those whose key was supplied
  are implied.
- **`equals` / `hashCode` / `impliesEquivalent`** include the key set and the loader
  identity.
- **Serialization** uses the builder proxy pattern and deserialises **void**, never
  as a grant without the loader (that would silently widen it to every loader).
- **Builder.** Context constant `DIGEST_DOMAIN = 7`, same value in both trees (JGDMS
  inlines it). Section 6 named it `CODEBASE_DOMAIN`; rename it if that branch has not
  been released. A builder method supplies the code sources, for example
  `codeSources(CodeSource... cs)`. Building with `DIGEST_DOMAIN` throws
  `IllegalArgumentException` if any supplied code source has no digest, so "verified
  means a digest" is enforced where the grant is built. `clazz(Class)` still supplies
  the anchor domain and, from the class, the loader.

### 7.4 API

- `net.jini.security.Security.grant(Class<?> cl, CodeSource[] codebase, Principal[]
  principals, Permission[] permissions)`: one `DigestCodeSource` per non-directory jar.
- `DynamicPolicy`: the matching method as a `default` that throws
  `UnsupportedOperationException`, so existing implementations still compile;
  `DynamicPolicyProvider` and the delegating providers (`AggregatePolicyProvider`,
  `LoaderSplitPolicyProvider`, `DebugDynamicPolicyProvider`) implement or pass it
  through.
- **Caller:** `PreferredProxyCodebaseProvider.resolve`, after it has authenticated the
  server and checked the codebase, builds the code sources from the bootstrap proxy's
  per-jar digests (`getCodebaseDigest()` split by `getDigestOffsets()`, in codebase
  order, directories excluded) plus the jars' signers. Confirm the bootstrap digests are
  the same computation as `DigestCodeSource` (SHA-256 over the whole jar file); if not,
  keys will never match. Proxy preparers that grant permissions then use the overload
  where the provider recorded verified identities for the proxy's loader, and
  `Security.grant(Class, Permission[])` otherwise.

### 7.5 `ClassLoaderGrant` stays

- **Not deprecated.** Withdraw item 9 of the DirtyChai changes document and the section 6
  deprecation in JGDMS. It is the unverified path.
- **Loader from the class.** It must take the loader from `clazz.getClassLoader()`, not
  from the anchor domain, or it is ineffective for `Proxy` classes (the section 6 review
  finding). Same fix in both trees.
- `DynamicPolicyProvider.grant(Class, Principal[], Permission[])` and
  `Security.grantRetainedInconclusiveLoaderGrant` return to `CLASSLOADER`.
- `DynamicPolicyProvider.getGrants(Class, Principal[])` keeps evaluating the class's
  `ProtectionDomain` (section 6): that is what lets `signedBy` and `digest` grants
  propagate, and is independent of this revision.

### 7.6 `DigestGrant` rejects unverified code sources (both trees)

`DigestCodeSource(String, Certificate[], String, byte[])` is public and accepts any
digest, and `ClassLoader.defineClass(..., ProtectionDomain)` bypasses
`SecureClassLoader.getProtectionDomain`. Code with `createClassLoader` can therefore
define its own bytes in a domain carrying a trusted jar's digest, which matches every
static `DigestGrant` in the policy. `unverified()` is true for that constructor and after
`readExternal`, false for a digest `SecureClassLoader` computed; the field is private and
the class final. So:

```java
if (!(codeSource instanceof DigestCodeSource dcs)) return false;
if (dcs.unverified()) return false;   // a digest asserted by a caller is not identity
```

A combiner-rebuilt domain reuses the same `CodeSource` instance and stays verified.

### 7.7 Loader isolation: the "unmarshalled itself" shortcut

`DigestDomainGrant` can be no narrower than the loader it is attached to.
`PreferredProxyCodebaseProvider` keys its loaders by `Key(InvocationHandler, List<Uri>
codebase, parent)`, and `BasicInvocationHandler.equals` compares the `ObjectEndpoint` and
server constraints, so on the bootstrap path there is one loader per remote object. One
shortcut in `resolve` bypasses that:

```java
String loaderPath = PreferredClassProvider.getLoaderAnnotation(parent, false, null);
if (loader == null && path != null && path.equals(loaderPath)) { // "Has it unmarshalled itself?"
    loader = parent;
}
```

It compares the codebase path only. A response from service A is unmarshalled with A's
proxy loader as `parent`, so a bootstrap proxy for a **different** remote object B with
the same codebase passes, resolves in A's loader, and shares A's `DigestDomainGrant`.

**Change:** reuse `parent` only when it is the loader recorded for this handler: keep the
`Key` with each loader in `CACHE` / `SERVICES_EXP` (or a weak reverse map from loader to
key) and require `key.handler.equals(Proxy.getInvocationHandler(bootstrapProxy))`.
Otherwise fall through and create a loader keyed by B's handler.

Related, not changed here:
- `BasicInvocationHandler.equals` has client-constraint comparison commented out (TODO),
  so one remote object under different client constraints shares a loader. Same code and
  service, so acceptable, but record it.
- Proxies not sent in bootstrap form (not `DynamicProxyCodebaseAccessor` /
  `ProxyAccessor` + `CodebaseAccessor`, or written through another stream) resolve
  through `PreferredClassProvider`, keyed by codebase and parent only, so may share
  loaders across services. They get only the `ClassLoaderGrant` path.

### 7.8 DirtyChai items to check (not designed here)

- Whether `ClassLoader.defineClass(..., ProtectionDomain)` has any `LoadClassPermission`
  gate. If not, explicit-domain definition bypasses `SecureClassLoader`'s checks in
  general, not only digests.
- `DigestCodeSource.computeDigest` returns a null digest for a `file:` codebase that does
  not exist; `SecureClassLoader` could refuse it rather than create a domain whose
  `DigestCodeSource` has no digest.
- Remote jar bytes and digests come from one `JarResponseCache` entry only while the
  loader fetches with caches enabled; check that `PreferredClassLoader` /
  `RFC3986URLClassLoader` never call `setUseCaches(false)` on codebase connections.

### 7.9 Changes to the implemented JGDMS branch (section 6)

| Section 6 | Revision |
|---|---|
| `CodebaseDomainGrant`, codebase from `getURLs()`, non-`URLClassLoader` fallback | Replace with `DigestDomainGrant` (7.3); remove the `getURLs()` collection and the fallback from the builder |
| `PermissionGrantBuilder.CODEBASE_DOMAIN = 7` | Rename `DIGEST_DOMAIN`, value 7; add the code-source builder method |
| `CLASSLOADER` / `ClassLoaderGrant` deprecated | Not deprecated; loader taken from the class (7.5) |
| `DynamicPolicyProvider.grant`, retained INCONCLUSIVE grant use `CODEBASE_DOMAIN` | Back to `CLASSLOADER`; the verified path is the new overload (7.4) |
| `DynamicPolicyProvider.getGrants` evaluates the domain | Keep |
| `CodeSourceKey`, `impliesCodeSource` via it | Keep (item 6) |
| Shadowing-builder fallback to `PROTECTIONDOMAIN` when the context is rejected | Remove. Neither tree has been released, so both gain `DIGEST_DOMAIN` together; a mismatched development build should fail loudly rather than silently change grant scope |
| Tests `CodebaseDomainGrantTest`, `PreferredClassLoaderCodebaseGrantTest` | Rewrite for supplied identities (7.11) |
| — | New: `Security.grant` overload and `DynamicPolicy` default (7.4); `DigestGrant` `unverified()` check (7.6); provider shortcut fix and digest supply (7.4, 7.7) |

### 7.10 DirtyChai changes document

Revise `DirtyChai-Required-Changes-PermissionGrant-SOW.md`:
- **Item 6** (`impliesCodeSource` via `CodeSourceKey`): unchanged.
- **Items 7 and 8:** `DigestDomainGrant` and `DIGEST_DOMAIN = 7` with the code-source
  builder method, per 7.3, instead of `CodebaseDomainGrant`.
- **Item 9:** withdrawn; replace with the `ClassLoaderGrant` loader-from-class fix.
- **New:** `DigestGrant` rejects `unverified()` (7.6); the 7.8 checks.
- **Item 11's** location-only bullet and **13a** (no pinning) no longer apply: there is no
  derived identity to pin.

### 7.11 Tests

DirtyChai `test/jdk/ai/` (AI-authored, Zone T) and the JGDMS equivalents:
- **`DigestDomainGrantTest`** (replaces `CodebaseDomainGrantTest`): with one loader over
  jars A and B and supplied digests for both:
  - A and B implied, B's first class loaded after the grant;
  - a jar added with `addURL` not implied;
  - a jar whose digest differs from the supplied one not implied;
  - a domain in the same loader with a forged (`unverified()`) `DigestCodeSource`
    carrying A's digest not implied;
  - a mixed-signer jar: only the supplied signer set implied;
  - a second loader over identical jars not implied;
  - a `Proxy` class anchor uses the class's loader;
  - principals honoured; `implies(ClassLoader)` and `implies(CodeSource)` false;
  - void after serialization and after the loader is collected;
  - the builder rejects a code source with no digest;
  - controls, run every time: a `ClassLoaderGrant` passes the `addURL` case and a
    `PROTECTIONDOMAIN` grant fails the jar B case, so the cases distinguish the
    semantics.
- **`GrantSignerTest`, `ProtectionDomainGrantIdentityTest`:** build digest code sources
  from real temp jars (verified) instead of the four-argument constructor, and add a
  forged-digest case that must be denied.
- **JGDMS provider test:** two bootstrap proxies for different remote objects with the
  same codebase, the second returned in a response unmarshalled by the first: they must
  get different loaders. Today they share one (7.7).

### 7.12 Relation to per-SPIFFE-ID process isolation

Planned: downloaded proxies run in subprocesses, one per server SPIFFE ID, holding all of
that identity's service proxies (`SmartProxyIsolationRouterImpl`, pooled by
`IsolationPoolingKey.derive(serverPrincipals)`). This complements section 7; it does not
replace it.

- **The process boundary separates identities.** Proxies of different SPIFFE IDs cannot
  share a JVM, loader, dynamic grant or heap, including proxies outside the bootstrap
  path. It also brings compatibility benefits (each process can be configured for the
  code it hosts).
- **The isolating JVM is itself the highest-risk component.** It is the one that
  downloads and runs foreign code, and it is still an entry point to the system: it holds
  connections, credentials for its identity, and whatever permissions it is granted. So
  least privilege inside it matters more, not less. `DigestDomainGrant` still decides
  which code in that JVM gets each proxy's permissions (later jars, changed jars and
  forged code sources stay denied), and the 7.7 fix still keeps grants per remote object
  among proxies of the same identity.
- **Policy for the isolating JVM** should grant the process only what its proxies need,
  with the verified overload (7.4) as the only route to anything beyond the limited
  `ClassLoaderGrant` path.

## 8. Implementation of section 7, and the second board review (2026-10-18)

Three independent reviewers (different models) reviewed section 7 as a design; the platform
side was then implemented on this branch. This section records both.

### 8.1 Implemented (JGDMS, `jgdms-platform`, `service-starter`)

- `DigestDomainGrant` (package-private) and `PermissionGrantBuilder.DIGEST_DOMAIN = 7` with
  `codeSources(CodeSource...)` (a non-abstract method that throws `UnsupportedOperationException`,
  so existing builder subclasses still compile). `CodebaseDomainGrant`, `CODEBASE_DOMAIN`, the
  `getURLs()` collection and the shadowing-builder fallback are removed.
- Implication: the domain's loader is the granted **class's** loader (checked first), principals,
  a `DigestCodeSource` that is computed rather than asserted (`unverified()` false), and its
  `CodeSourceKey` (Uri + signers as a set + digest) is one supplied. `implies(ClassLoader)` and
  `implies(CodeSource)` are false. Void when the loader is collected and after serialisation.
  No result is cached by `CodeSource` reference (`DigestCodeSource` is mutable through its public
  `readExternal`).
- The supplied code sources are expected identities and **may be unverified** digest code
  sources (the verifier supplies the digest); only the domain presented at check time must be
  verified. The builder refuses (`IllegalArgumentException`) a missing class, a class without a
  loader, no code sources, a code source with no digest, or a digest algorithm other than
  SHA-256, so a grant is never wider than the identities given. On a JVM without
  `DigestCodeSource` nothing can be built.
- `ClassLoaderGrant` is back, not deprecated, taking the loader from the **class** (so it works
  for `java.lang.reflect.Proxy` classes, whose domain has no loader); `implies(CodeSource)` false.
  `DynamicPolicyProvider.grant(Class, ...)` and the retained INCONCLUSIVE grant use it again.
- `Security.grant(Class, CodeSource[], Principal[], Permission[])`,
  `DynamicPolicy.grant(Class, CodeSource[], ...)` (a `default` that throws
  `UnsupportedOperationException`: nothing is granted, callers must not fall back),
  `DynamicPolicyProvider`, `AggregatePolicyProvider`, `LoaderSplitPolicyProvider`. The overload
  throws `IllegalStateException` for a loader marked inconclusive. Null class, null/empty
  codebase are `IllegalArgumentException`, never a universal grant.
- `DigestGrant` rejects an unverified digest (7.6). `DynamicPolicyProvider.getGrants` unchanged.
- Tests: `DigestDomainGrantTest` (16, with controls), `GrantSignerTest` (computed vs forged
  digests from real jars), `SecurityInconclusiveGrantLifecycleTest`. The positive digest cases
  need `java.security.DigestCodeSource`; here they were run against a stub patched into
  `java.base` (`--patch-module`) as well as on a stock JDK, where they skip. Removing the
  verified check, the loader check or the key check each fails tests.

### 8.2 Not implemented, and why

The provider and preparer changes of 7.4 and 7.7 (`PreferredProxyCodebaseProvider.resolve`
supplying the digests and choosing the overload; the shortcut fix) are **not** done. The
reviewers found problems the section 7 text does not cover, and the trust story depends on them:

1. **Loader parenting is not a defect to fix; process isolation is the boundary.** (Decision,
   project lead.) The reviewers observed that the loader a falling-through shortcut creates for
   service B is parented on service A's proxy loader and is parent-first unless a resource is
   preferred, so B's classes can resolve to A's classes in A's domains, covered by A's grant.
   That is the natural class-loader hierarchy of the JVM, and **only process isolation can give
   true class isolation**; no loader layout in one JVM can. So:
   - `DigestDomainGrant` promises, in one JVM, that unexpected, unverified or forged code does
     not receive the grant; it does not promise that two services cannot reach each other's
     classes through a shared parent chain. It is no narrower than the code the loader reaches.
   - What remains worth doing on the in-JVM path is hygiene: the shortcut should compare the
     `ObjectEndpoint` rather than only the codebase path, and must not reuse a loader recorded for
     a different handler. It also skips the digest, certificate and `BootstrapPermission` checks
     and the isolation router; that gap is the real concern (below).
   - A trust boundary between server identities must therefore be enforced by routing to the
     per-identity subprocess (7.12), and the router path must never fall through to in-JVM for a
     foreign identity. Today unrouted: the shortcut (it runs before the router), the
     non-bootstrap `PreferredClassProvider` path, nested proxies of another identity inside a
     subprocess, and lookup results (no identity, item 5).
2. **Digests exist in the provider only on the first resolve.** The comparison of server digests
   with local downloads is inside `if (vr != null)`'s `else` and `serverPrincipals != null`, so in
   steady state no comparison runs. The verified overload must be fed by the provider's own
   locally computed digests, unconditionally, not by server claims.
3. **Digest algorithm.** `DigestCodeSource` always computes SHA-256 whatever it is told, and
   `SecureClassLoader` hard-codes "SHA-256"; a server may report any algorithm. Require
   "SHA-256", fail loudly otherwise. (The builder already refuses other algorithms.)
4. **Signers in the key.** The digest already covers the signature files; signers add
   silent-mismatch risk (the provider checks jar-level certificates with `containsAll`, the
   server's certificates are not the flattened chain `CodeSource.getCertificates()` yields).
   Consider building keys without signers or from the `JarFile` entries.
5. **Server identity — settled by test.** `PreferredProxyServerIdentityTest` drives the real
   `resolve()`. `serverPrincipals` is the `ServerSubject` found in the **unmarshalling stream's
   context** (`ProxySerializer` passes `arg.getObjectStreamContext()`); nothing is learned from the
   bootstrap proxy's own connection. That context is filled by the transport that delivered the
   bytes (`AtomicInvocationHandler.invokeRemoteMethod` calls `request.populateContext(context)` on
   the response; `SslConnection` adds the delivering server's `ServerSubject`). Therefore:
   - a proxy delivered **directly by its own service** gets that service's identity: correct;
   - a bootstrap proxy for service B nested in a **response delivered by service A** (A returns a
     reference to B) is attributed to A: two different servers' proxies in one stream share one
     identity, one isolation pool key and one `BootstrapPermission` subject (test 1 and 3);
   - a proxy taken from a **lookup result** is unmarshalled by `MarshalledWrapper.get()` with no
     context (only an `IntegrityEnforcement`, `MarshalledInstance.get`), so it has **no identity**:
     with isolation on `resolve` fails closed (test 2); with isolation off the
     `serverPrincipals != null` gates (the `BootstrapPermission` and per-digest checks) are
     skipped for it.
   The project lead's statement (the bootstrap proxy always talks to its own server) is true of
   the calls made through it, but the provider does not use those calls to establish identity.
   **`SafeServiceRegistrar` should be used** (project lead), and it closes the gap for the
   intended path: `lookUp(...)` returns *bootstrap* proxies as authentication tokens; the client's
   bootstrap preparer prepares and authenticates the bootstrap proxy; then
   `ServiceProxyAccessor.getServiceProxy()` is called on the **prepared** bootstrap proxy
   (`ServiceDiscoveryManagerImpl`, around lines 2025–2085). The smart proxy therefore arrives in
   the response to a call made over the service's **own** authenticated connection, so the
   stream's `ServerSubject` is that service's identity. The two other delivery routes remain
   unresolved: legacy `ServiceRegistrar.lookup` results (unmarshalled with no context, so no
   identity) and a proxy nested in another service's response (attributed to the delivering
   server). **The nested case is a legacy-stream residue** (project lead): in Atomic marshal
   streams a foreign service proxy is not inlined in the delivering server's response; it is
   marshalled separately in a `MarshalledInstance` and unmarshalled separately by the receiver,
   with a context the receiver chooses (as `getServiceProxy()` does for the proxy's own service).
   Some of the old design remains (a `ProxySerializer` inlined in a JOSS stream resolves with that
   stream's context), so the test below still pins the legacy behaviour, but it should not occur
   on Atomic streams. Requirement: the provider must not rely on this; an identity may come only
   from a `getServiceProxy()` response, and a `ProxySerializer` reached by any other route is not
   trusted for identity. Requirement: delivery through `getServiceProxy()` on a prepared bootstrap proxy is the
   only route that may yield an identity, isolation pool key, `BootstrapPermission` subject or
   verified-path grant; for any other route the provider must refuse or route to process
   isolation without a verified grant, never run the in-JVM unverified path silently.

### 8.3 Decisions recorded

- **The weak path.** Both overloads check the same `GrantPermission`, so policy cannot say "only
  via the verified overload". Decision (project lead): **the proxy preparer rejects a proxy that
  has no verified identities**; there is no automatic fall back to the loader-wide grant. A
  caller that wants the weak path calls `Security.grant(Class, Permission[])` explicitly. Still
  open: `Security.grant(fromClass, toClass)` re-issues propagated permissions as a loader-wide
  grant in the target loader, so a verified-path permission can become loader-wide there;
  consider excluding digest-domain grants from propagation.
- **Inconclusive.** The overload refuses (does not grant) for a loader marked inconclusive; an
  inconclusive verdict is not verified code. Retained inconclusive grants stay loader-wide.
- **No key pinning, no `getURLs()`** (section 6 outcomes) stand.

- **Migration path for legacy lookup (project lead's suggestion, not implemented).** Change the
  legacy `ServiceRegistrar.lookup` so it returns only reflective (`java.lang.reflect.Proxy`)
  proxies that need no codebase, and not smart proxies; smart proxies are reached only through
  `SafeServiceRegistrar` (bootstrap proxy, authenticate, `getServiceProxy()`). Notes from reading
  the code, to be checked when it is designed:
  - It removes the case this SOW cannot otherwise cover: a legacy lookup result is unmarshalled
    with no context, so it has no identity and the in-JVM path would download and run code for it.
    A proxy with no codebase downloads nothing, so there is nothing to grant and no code identity
    to verify; its classes (the service interfaces and the local `BasicInvocationHandler` etc.)
    come from the client's own class path.
  - It can be enforced client-side in `RegistrarProxy` / `Item.get()` before
    `MarshalledWrapper.get()`: refuse an item whose marshalled service has a non-empty codebase
    annotation, or whose service object is not a `java.lang.reflect.Proxy`. Reggie also stores a
    `bootstrapProxy` in each `Item`, so the lookup service already has the data the safe path
    needs; no wire change is required to start.
  - The cost: legacy clients stop seeing smart-proxy services (they must move to
    `SafeServiceRegistrar`), and a reflective proxy whose interfaces are not on the client's
    class path now fails (the existing `Item.get()` behaviour is to set the service to null). The
    migration is therefore fail-closed for legacy clients, which is the point. A property or a
    per-client switch can ease rollout.
  - It does not by itself authenticate the reflective proxy's server: that is still done by
    constraints and proxy trust, as before.

### 8.4 DirtyChai requirements raised by the review (not JGDMS code)

- **`ClassLoader.defineClass(..., ProtectionDomain)` has no gate.** `preDefineClass` does not
  check that `pd.getClassLoader()` is the defining loader, and a `ProtectionDomain`'s loader is
  just a field. Code with `createClassLoader` can define its own bytes in a domain naming the
  victim's loader and carrying a computed `DigestCodeSource`: all of check 1, 3 and 4 then pass,
  and every static `DigestGrant` applies. `unverified()` does not help: the public
  `DigestCodeSource(String, Certificate[], String)` constructors and
  `SecureClassLoader.defineClass(name, bytes, CodeSource)` compute the digest of the **URL's**
  content, not of the bytes being defined. `readExternal` sets `unverified` last, with no
  `finally`, so an `ObjectInput` that throws part-way leaves chosen fields and `unverified()`
  false. Required: make `unverified` default to true and cleared only by the computing
  constructors; allow an explicit-domain `defineClass` with a `DigestCodeSource` domain only for
  a domain the loader itself issued (or behind a permission); consider letting
  `DigestDomainGrant` ask the loader whether it issued the domain.
- `DigestCodeSource.computeDigest` for a missing `file:` jar returns a *verified* code source
  with a null digest; `SecureClassLoader` should refuse it.
- `Lookup.defineClass` / hidden classes inherit the lookup class's domain: same-domain code or
  `suppressAccessChecks` holders only.
- `PreferredClassLoader.addURL` is final and throws, so the "jar added later" test needs another
  loader (as `DigestDomainGrantTest` does).
