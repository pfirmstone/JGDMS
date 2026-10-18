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

**Finding on trunk.** The SOW describes DirtyChai as already having `impliesSigners`, a shared
`CodeSourceKey` and `GrantSignerTest`. `trunk` has none of them (`SecureClassLoader` has only a
private nested `CodeSourceKey`; `test/jdk/ai/org/apache/river/api/security/` holds only
`PrincipalGrantConjunctiveTest`). If that work exists on an unpushed branch, use it and compare
against the items below.

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
it is JGDMS-only and must stay in JGDMS.

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

## 7. New `CodebaseDomainGrant` (package-private, extends `ProtectionDomainGrant`)

Semantics (SOW B.1): a `ProtectionDomain pd` is implied iff
1. `pd` is the anchor domain, **or**
2. `pd.getClassLoader()` is the anchor domain's loader (reference equality, non-null) **and**
   `pd`'s code-source Uri is in the codebase set **and** its `CodeSourceKey` equals the identity
   pinned for that Uri (first domain seen pins it; the anchor's identity is pinned at
   construction if its Uri is in the codebase), **or**
3. same loader and `pd`'s code source has the anchor's own identity (a domain rebuilt by a
   `DomainCombiner`), even if the codebase set is empty;

and the grant's principals are satisfied. Details to get right:
- Domains are matched lazily at check time; the grant never stores a list of domains.
- A key that cannot be built, a different loader, or a Uri outside the codebase → not implied.
- The pin map is keyed only by Uris from the (bounded) codebase set, so a hostile domain cannot
  grow it. Use a concurrent map with put-if-absent semantics.
- `implies(ClassLoader, …)` returns false: a loader has no code identity.
- Inherited `implies(CodeSource, …)` keeps the anchor-identity behaviour.
- Not serialisable: `writeReplace` returns the builder template with the new context;
  `readObject` throws `InvalidObjectException`. The codebase and pin map are transient.
- `equals`/`hashCode` include the codebase set, and `equals` requires the same class.
- `impliesEquivalent`: same codebase set plus the superclass test. `toString` lists the codebase.
- `getBuilderTemplate`: superclass template with context `CODEBASE_DOMAIN`.
- Add the `serial` suppression DirtyChai uses on other grants.

## 8. `PermissionGrantBuilder` and `PermissionGrantBuilderImp`

- Add `public static final int CODEBASE_DOMAIN = 7;`. **The value must equal JGDMS's.**
- Deprecate `CLASSLOADER` in javadoc and with `@Deprecated`; keep it working.
- `Imp.context(int)`: raise the upper bound from 6 to 7 (and the message).
- `Imp.build()`: new case `CODEBASE_DOMAIN`:
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

## 14. Not verified

The JGDMS side was compiled and tested with plain `javac`/JUnit (Maven could not resolve a
dependency in the sandbox). Nothing was built against DirtyChai. Review the independent board
findings (if any are recorded in this branch's SOW section 6) before treating the JGDMS
behaviour as final; items above that mirror JGDMS should follow any change made there.
