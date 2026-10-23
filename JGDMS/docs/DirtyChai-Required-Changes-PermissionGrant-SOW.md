# DirtyChai — changes required by the PermissionGrant SOW

**Status:** analysis for a human author. Nothing here has been applied to DirtyChai.
Written 2026-10-18 with AI assistance (Claude), against DirtyChai `trunk` at
`a6782e968`. It describes behaviour and the reason for it, it contains no code, because
DirtyChai's `CLAUDE.md` and the OpenJDK interim AI policy require contributions there to be
human-written.

Companion to `SOW-PermissionGrant-Mirror-and-ProtectionDomain-Grants.md`. The JGDMS side is
implemented on branch `claude/sow-permissiongrant-domain-grants`; use its classes and tests
(`CertificateGrant`, `URIGrant`, `DigestGrant`, `ProtectionDomainGrant`, `CodeSourceKey`,
`CodebaseDomainGrant`, `PermissionGrantBuilder`/`Imp`, `GrantSignerTest`,
`CodebaseDomainGrantTest`) as the behavioural reference. On DirtyChai the `java.base` copies run
and JGDMS's copies of the same 13 classes are shadowed, so the two must give the same answers.

**Status against DirtyChai `trunk` `af6bc5479` (checked after the project lead pushed their local
work).** Done there: items 1 and 3 (`impliesSigners` in `CertificateGrant`, used by `DigestGrant`),
most of item 2 (`URIGrant`), the shared `CodeSourceKey` (now
`au.zeus.jdk.authorization.policy.CodeSourceKey`, moved out of `SecureClassLoader`) and a
`GrantSignerTest`. Still to do: items 4, 6, 7, 8, 9 and 12, the multi-jar test of item 11, and the
two problems below.

1. **Does not compile:** `URIGrant.implies(ClassLoader, …)` (line 117) reads `certs`, which is
   `private` in `CertificateGrant` and not a field of `URIGrant`. Expose a package-private "requires
   signers" test from `CertificateGrant` (item 1) and use it. (Reproduced by compiling trunk's
   `org.apache.river.api.security` against stubs: this is the only error besides the pre-existing
   `SocketPermission.init()` one.)
2. **`CodeSourceKey` differences from what item 5 needs:** the constructor throws
   `URISyntaxException` and the key may have a null Uri (no location) which compares equal to
   another null-Uri key; signers are compared as an ordered array (`Arrays.equals`), while
   `SecureClassLoader` caches order-insensitively and JGDMS compares as a set; unsigned is `null`
   certificates in one place and possibly empty in another. For grant use, callers must catch the
   exception and treat it, and a null Uri, as "not implied". Consider a set comparison and
   treating null and empty signers alike.

> **Revised by SOW sections 7 and 8.** `CodebaseDomainGrant` and `CODEBASE_DOMAIN` are gone.
> Where this document and sections 7–8 disagree, sections 7–8 govern: in particular items 7, 8,
> 9, the "release ordering" note (6a) and the pinning discussion (13a) below are **superseded**.
> What DirtyChai needs now, in order:
> 1. Items 1–6 as below (signers; escaping; `CodeSourceKey` for grants; `impliesCodeSource`),
>    and fix `URIGrant.implies(ClassLoader, …)` so it compiles (see the status paragraph).
> 2. **`DigestDomainGrant`** and **`PermissionGrantBuilder.DIGEST_DOMAIN = 7`** with
>    `codeSources(CodeSource...)`, exactly as SOW 7.3 and 8.1 describe: the domain's loader is the
>    granted class's loader (checked first); principals; `DigestCodeSource` with `unverified()`
>    false; `CodeSourceKey` (Uri, signers, digest) in the supplied set; `implies(ClassLoader)` and
>    `implies(CodeSource)` false; void when the loader is collected and after serialisation; no
>    result cached by `CodeSource` reference. The builder refuses a missing class or loader, no
>    code sources, a code source without a digest, and any algorithm but SHA-256
>    (`IllegalArgumentException`); the supplied code sources may be unverified. Keep the value 7
>    identical to JGDMS (it is inlined).
> 3. **`ClassLoaderGrant` stays, undeprecated**, taking the loader from `clazz.getClassLoader()`
>    (the builder records it), with `implies(CodeSource)` false. Withdraw the deprecation.
> 4. **`DigestGrant` rejects `unverified()`** code sources (already on JGDMS side).
> 5. **DirtyChai defences from SOW 8.4**: gate `ClassLoader.defineClass(..., ProtectionDomain)`;
>    `unverified` default true; `readExternal` ordering; refuse a verified code source with a null
>    digest; SHA-256-only labelling.
> 6. Tests per SOW 7.11 / 8.1 (`DigestDomainGrantTest`, forged-digest cases in
>    `GrantSignerTest`), with real temp jars so the digests are computed.

All paths below are under `src/java.base/share/classes/org/apache/river/api/security/`.

---

## 1. `CertificateGrant` — signer check (Part A)

**Today:** `implies(CodeSource, Principal[])` calls `Arrays.asList(codeSource.getCertificates())`.
Unsigned code has `null` certificates, so this throws `NullPointerException`.

**Required:** one package-private signer check, used by every CodeSource-based grant:
- null `CodeSource` → not implied;
- grant requires no signers → implied for any non-null `CodeSource`;
- code is unsigned (null or empty certificates) → not implied;
- otherwise every required signer must be present (order irrelevant).

`implies(CodeSource, Principal[])` is the principal check followed by this signer check. Also
expose a package-private "requires signers" test, which `URIGrant` needs (item 2).

## 2. `URIGrant`

**Today:**
1. `implies(CodeSource, …)` matches the location without checking signers, so `codeBase` +
   `signedBy` does not enforce the signer.
2. `implies(ClassLoader, …)` returns true for an empty or null location even when the grant
   requires signers. A loader carries no signers.
3. The constructor already throws `SecurityException` on an unparseable URI (good, keep it).

**Required:** in `implies(CodeSource, …)` apply the signer check (item 1) *before* the location
match, after the null-CodeSource test. In `implies(ClassLoader, …)` return false whenever signers
are required. Keep the existing `getUriLocation()`-based location match. Treat a null
`getUriLocation()` as not implied.

## 3. `DigestGrant`

**Today:** matches principals, algorithm and digest bytes. It correctly ignores the location. It
does not check signers, so `digest` + `signedBy` ignores the signer. The javadoc says certificate
checks are delegated to super, which the code does not do.

**Required:** after the `DigestCodeSource` type test, apply the signer check (item 1). Correct
the javadoc: principal check only, location ignored, signers enforced.

## 4. `PrincipalGrant` — escaping (reverse port)

**Today:** for a `FilePermission` name, only backslash and double quote are escaped.

**Required:** also escape `\n`, `\r` and `\t` (as the two-character sequences), after the backslash
and quote escapes, as JGDMS does, so a name containing control characters survives a round trip
through the policy tokenizer. Do **not** port JGDMS's `SubjectDomain` branch in `getPrincipals`:
it was JGDMS-only. (Update 2026-10-23: removed from JGDMS at the maintainer's direction, superseded by the multi-subject work; nothing to port or to keep in sync, and `getPrincipals` is now `pd.getPrincipals()` in both trees.)

## 5. New `CodeSourceKey` (package-private, final)

The identity of a `CodeSource` for grant comparison: location `Uri`, signers, and for a
`DigestCodeSource` the algorithm and digest bytes. Requirements:
- built from `getUriLocation()`; if that is null the factory returns null, and **every caller
  treats null as "not implied"** (fail closed);
- signers compared as a list of certificates, absent and empty treated alike;
- digest bytes copied defensively; algorithm and bytes both part of equality;
- `equals`/`hashCode` cover all three parts; no `CodeSource.equals` or `URL.equals` (DNS).

JGDMS's version reads `DigestCodeSource` reflectively because it must run on a stock JDK; on
DirtyChai use the class directly.

## 6. `ProtectionDomainGrant.impliesCodeSource`

**Today:** after the same-reference fast paths it compares certificates only if the grant's
domain is signed, then compares `toURI()` forms, falling back to URL string comparison when
`toURI()` fails; it ignores the digest and throws `NullPointerException` if either location is
null.

**Required:** after the same-reference and both-null fast paths, return false if exactly one
side is null, then compare `CodeSourceKey`s; return false if either key cannot be built. Make
the method package-private (not private) so `CodebaseDomainGrant` can reuse it. Remove the
now-unused imports.

## 6a. Release ordering (board finding)

`CODEBASE_DOMAIN` is a compile-time constant, inlined into JGDMS's bytecode. On DirtyChai the
`java.base` builder shadows JGDMS's, and DirtyChai trunk's `context(int)` rejects anything above
6. JGDMS now catches that rejection and falls back to a grant for the granted class's own
`ProtectionDomain` (logged at warning), so it keeps working but with narrower grants: proxy
jars other than the one holding the class lose their dynamic grants. Items 7 and 8 must ship in
DirtyChai for codebase-wide grants to work there.

## 7. New `CodebaseDomainGrant` (package-private, extends `ProtectionDomainGrant`)

Semantics (SOW B.1, without the pinning rule — see section 13a): a `ProtectionDomain pd` is
implied iff
1. `pd` is the anchor domain, **or**
2. `pd.getClassLoader()` is the granted class's loader (reference equality, non-null) **and**
   `pd`'s code-source Uri is in the codebase set (signers and digest are not compared), **or**
3. same loader and `pd`'s code source has the anchor's own identity (a domain rebuilt by a
   `DomainCombiner`), even if the codebase set is empty or the anchor has no location;

and the grant's principals are satisfied. Details to get right:
- Domains are matched lazily at check time; the grant never stores a list of domains.
- The "anchor's loader" is the loader of the **class** passed to the builder, not the loader of
  the class's `ProtectionDomain`: a `java.lang.reflect.Proxy` class (what `Security.grant` is
  usually given) has the shared all-permission domain, whose loader is null. The builder records
  `cl.getClassLoader()` weakly and the grant carries it; the grant is void when that loader is
  collected. Rule 3 (anchor identity) compares code-source identity only, using that loader.
- A key that cannot be built, a different loader, or a Uri outside the codebase → not implied,
  except by rule 3 (which also covers an anchor whose `CodeSource` has no location).
- `implies(CodeSource, Principal[])` returns false: a dynamic grant applies to domains.
- Cache the last accepted `CodeSource` by reference (weakly) so repeated checks do not rebuild
  the key; building it on every check was measured at ~25x slower than the old loader grant with
  thousands of grants.
- `implies(ClassLoader, …)` returns false: a loader has no code identity.
- Not serialisable: `writeReplace` returns the builder template with the new context;
  `readObject` throws `InvalidObjectException`. The codebase and loader are transient.
- `equals`/`hashCode` include the codebase set, and `equals` requires the same class.
- `impliesEquivalent`: same codebase set plus the superclass test. `toString` lists the codebase.
- `getBuilderTemplate`: superclass template with context `CODEBASE_DOMAIN`.
- Add the `serial` suppression DirtyChai uses on other grants.

## 8. `PermissionGrantBuilder` and `PermissionGrantBuilderImp`

- Add `public static final int CODEBASE_DOMAIN = 7;`. **The value must equal JGDMS's.**
- Deprecate `CLASSLOADER` in javadoc and with `@Deprecated`; keep it working.
- `Imp.context(int)`: raise the upper bound from 6 to 7 (and the message).
- `Imp.build()`: new case `CODEBASE_DOMAIN`:
  - the loader comes from `clazz(Class)` (the class's own loader), falling back to the anchor
    domain's loader when only `setDomain` was used; a package-private setter is needed so
    `CodebaseDomainGrant.getBuilderTemplate` can pass it on (do not add public API);
  - no domain at all → a `ProtectionDomainGrant` with no domain (applies to all domains, as a
    `ClassLoaderGrant` with no domain did);
  - otherwise collect the codebase from the anchor loader: if it is a `URLClassLoader`, convert
    each `getURLs()` entry with `Uri.urlToUri` (never `URL.equals`); skip, at debug level, any URL
    that fails to convert (fail closed); for any other loader (or none) leave the set empty and
    log once at informational level (this is SOW B.2's "log at CONFIG"; `java.base` must use
    `System.Logger`, not `java.util.logging`);
  - build a `CodebaseDomainGrant`.
- `Imp.readResolve()`: a deserialised builder that carried a domain and has context
  `CODEBASE_DOMAIN` must return the null grant, exactly as for `CLASSLOADER` and
  `PROTECTIONDOMAIN`.

## 9. `ClassLoaderGrant`

Javadoc only: deprecated, retained for deserialising old builders and explicit callers, nothing
in JGDMS creates one. No behaviour change.

## 10. Split-package constraints (SOW A.2)

- JGDMS-only classes (`ExternallyVoidablePermissionGrant`, `LeasedPermissionGrant`,
  `OneShotLeasedPermissionGrant`, `ConcurrentPolicyFile`, …) are defined by the application loader
  and can use only public/protected members of the `java.base` classes. Confirm none of them
  calls the new package-private `impliesSigners`, "requires signers" or `impliesCodeSource`.
- `CodebaseDomainGrant` must exist in **both** trees and be built only through the builder.
  A JGDMS-only subclass of a package-private grant fails with `IllegalAccessError` on DirtyChai.

## 11. Tests (jtreg, `test/jdk/ai/org/apache/river/api/security/`)

Port, as human-authored or per `AI_POLICY.md` §4.1 (Zone T) rules, the behaviour of JGDMS's
`GrantSignerTest` (T1, T2) and `CodebaseDomainGrantTest` (T4):
- signedBy: all/one/other signer, unsigned (no NPE), null code source, class loader alone,
  protection domain; no signers required;
- codeBase + signedBy: right/wrong location × right/no/other signer; class loader alone is false;
  an unparseable codeBase URI throws `SecurityException`;
- digest + signedBy: same digest at another location is implied, other digest/other signer/
  unsigned/plain `CodeSource` with the same URL are not;
- multi-jar loader: A implied; B implied including when its first class loads *after* the grant;
  a location outside the codebase, and a second loader over identical jars, are not; a
  combiner-rebuilt domain of A and of B is implied; a same-location domain with different
  signers is not; principals honoured; a loader alone is not implied;
- each test must be shown to fail when its invariant is broken.

T3 (parity): run the JGDMS tests with JGDMS on the class path on DirtyChai and compare with a
stock-JDK run.

## 12. Documentation

Add a section to `JGDMS_COMPATIBILITY.md` covering: dynamic grants are per `ProtectionDomain` of
the verified codebase; `CODEBASE_DOMAIN = 7`; `CLASSLOADER` deprecated; signer enforcement and
unsigned-code behaviour; fail-closed codeBase URI parsing; the rule that new grant classes exist
in both trees. Update `SECURITY_MODEL.md` if it states dynamic grants are per class loader.

## 13. Behaviour changes to put in release notes (SOW B.4)

- Domains the proxy's loader defines from a location outside its codebase no longer receive
  dynamic grants.
- `Security.grant(fromClass, toClass)` now propagates permissions that came from `signedBy` and
  `digest` grants.
- An unparseable `codeBase` URI raises `SecurityException`.

## 13a. Open decisions that affect both trees

1. **Pinning (SOW B.1) — decided: removed in JGDMS** (all three reviewers advised it; see SOW
   section 6). Do **not** implement pinning in DirtyChai. A domain is implied iff it has the
   anchor's loader and either the anchor's code-source identity or a code-source location in the
   codebase set. On DirtyChai pinning would also break outright: `SecureClassLoader` calls
   `getPermissions(plainCodeSource)` (and `PreferredClassLoader` builds a probe domain and checks
   `DownloadPermission` against it) *before* promoting to a `DigestCodeSource`, so a probe would
   pin an identity the real domain never has.
2. **Unparseable `codeBase` URI.** Throwing is fail-closed, but `ConcurrentPolicyFile.refresh()`
   catches the exception and keeps the old grants, so a typo in an edited policy leaves removed
   privileges in force. Alternatives: log at SEVERE (minimum), or make only that grant match
   nothing. JGDMS currently throws, as DirtyChai does.
3. **Uri derivation parity.** JGDMS builds the codebase set with `Uri.urlToUri` on the loader's
   URLs and the key from the same conversion; DirtyChai's key comes from `getUriLocation()`.
   Check the two agree for `file:` and `httpmd:` jar URLs.

## 14. Not verified

`DigestCodeSource` paths (the reflective key, `DigestGrant` signer check, T2 positive case) are
not exercised on a stock JDK and need DirtyChai-only tests.

The JGDMS side was compiled and tested with plain `javac`/JUnit (Maven could not resolve a
dependency in the sandbox). Nothing was built against DirtyChai. Review the independent board
findings (if any are recorded in this branch's SOW section 6) before treating the JGDMS
behaviour as final; items above that mirror JGDMS should follow any change made there.
