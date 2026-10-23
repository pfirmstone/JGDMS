# Non-conformances: `org.apache.river.api.security` in JGDMS and DirtyChai (2026-10-23)

A list for the maintainer to address later. Nothing here has been fixed in DirtyChai (this work had read-only access to it, and
OpenJDK policy allows only human-written code on that side). Items marked JGDMS can be done here. The analysis behind it is the
Opus divergence analysis of 2026-10-23 and the conformance test `BundledCopyConformanceTest`; "CONFIRMED" means read in the code
or run, "PLAUSIBLE" means reasoned but not run. Commit ids are given so each claim can be re-checked.

Status of every item is OPEN unless stated. Owner is the maintainer.

## A. Provenance (OpenJDK policy: no AI-assisted code in DirtyChai)

| Id | Item | Evidence | Status |
|---|---|---|---|
| P1 | `PermissionDelegate` (DirtyChai `cea19f32b`, a new 57-line file, mostly Javadoc) is AI-assisted. | The same file was created in JGDMS by `c2d8ef94c` (56 lines, 2026-06-17 13:28 +1000, with an AI co-author trailer) and moved to DirtyChai by `cea19f32b` (17:06 the same day, message "support for PermissionDelegate needs to be moved to DirtyChai"). The two differ only in one Javadoc sentence. | **Maintainer's determination (2026-10-23): counts as AI-assisted. To be rewritten by a human in DirtyChai.** Afterwards JGDMS can take the rewritten version (human code may flow DirtyChai to JGDMS). |
| P2 | `PrincipalGrant.toString`, the comment block for the UnresolvedPermission case (DirtyChai `f8520cdaa`, 2026-05-06 11:19Z, +28/-9 lines in `PrincipalGrant`). | Matches text in JGDMS `52d23da0c` (2026-05-06 03:33Z, authored by a bot account with the maintainer as co-author, about 30 changed lines). Derivation PLAUSIBLE, timestamps CONFIRMED. | Undetermined. Maintainer to review. |
| P3 | `CertificateGrant.impliesSigners` (DirtyChai `adb890e9f`, 2026-10-18 08:03Z, a 6-line method). | Same name and logic as JGDMS `1c8ef5ba1` (07:55Z, AI co-author trailer, a much larger commit). The logic is the obvious null/empty/containsAll check. Derivation PLAUSIBLE. | Undetermined. Maintainer to review. |
| P6 | `DigestGrant` and `DigestDomainGrant` in DirtyChai versus JGDMS. `DigestGrant` originated in DirtyChai (`ce6a8e833`, 2026-05-11, human, "Implement DigestCodeSource and Policy grants that contain URL digests") and was ported to JGDMS the same day by a bot account (`c0e0fb870`, "Port DirtyChai improvements"): the direction is the allowed one. `DigestDomainGrant` was written in JGDMS on 2026-10-18 (`c490a95de`, AI co-author trailer, SOW section 7); DirtyChai's version was written on 2026-10-20 in three "Refactoring of Security Policy" commits, two days later. The analysis found them to be two independent implementations (different superclass, different builder rules), so no derivation is shown. | Dates read from git in both repositories. | **RESOLVED (maintainer, 2026-10-23): DirtyChai's `DigestDomainGrant` was written independently.** `DigestGrant` direction is the allowed one. |
| P4 | Unreviewed beyond the three above. | The analysis compared 13 files against git history in a window; it did not audit every DirtyChai line against every JGDMS commit. | A full review is open if wanted. |
| P5 | Outbound to OpenJDK needs GPLv2 + Classpath Exception under the OCA; the DirtyChai files carry ASF Apache-2.0 headers, and `UnresolvedPrincipal` is Apache Harmony derived (author A. V. Varlamov). | Headers read in the files. | A legal question for the maintainer, not decided here. |

## B. Behaviour of DirtyChai's bundled copy that differs from the JGDMS contract

These matter because on DirtyChai the JDK's copy is what runs (the JGDMS copy of the 13 shared classes runs only in
`jgdms-platform`'s own tests, under OSGi, and on a stock JDK). Each needs a human-written DirtyChai change or a decision.

| Id | Item | Evidence | Proposed action |
|---|---|---|---|
| D1 | `ClassLoaderGrant.implies(CodeSource, Principal[])` is inherited from `PrincipalGrant` and returns `implies(pal)`: a loader grant implies ANY CodeSource, including null, whenever the principals match. A privileged grant would imply AllPermission for any CodeSource. | CONFIRMED by running the public API against both copies. Latent today: the only caller, `ConcurrentPolicyFile.getPermissions(CodeSource)`, only iterates grants parsed from the policy file. Breaks DirtyChai's own rule that a null CodeSource is never implied. | Override in `ClassLoaderGrant` to return false (DirtyChai's `DigestDomainGrant` already does). |
| D2 | A CLASSLOADER grant with no `clazz()` is void in DirtyChai trunk (`841105767`, 2026-10-21). JGDMS (and this container's older JDK) treats it as applying to all domains, as `DynamicPolicy.grant(null, ...)` promises. | CONFIRMED by the probes. QA `GrantNoPrincipalCase02` and `NullCases` rely on "all domains"; they would likely fail on a trunk JDK (PLAUSIBLE, not run). JGDMS tests `classLoaderGrantWithoutAClassCoversEveryDomain` and `classLoaderGrantSerializesThroughItsBuilder` fail against DirtyChai's classes. | Decide whether void is intended. If not, restore "all domains". |
| D3 | Deserializing a DIGEST_DOMAIN grant throws in DirtyChai (`readResolve` no longer returns the null grant; `build()` throws `IllegalArgumentException("ClassLoader must not be null")`). JGDMS returns the void grant. | Read from the code; NOT run on DirtyChai. | **INTENDED (maintainer, 2026-10-23): serialization support is being removed from these classes because it is not good security practice, and making it throw was easier than implementing it. No DirtyChai change. See J7 for the JGDMS side.** |
| D4 | `ClassLoaderGrant.impliesEquivalent`: JGDMS compares loaders only; DirtyChai also requires equal principals and non-null loaders (stricter). `equals`/`hashCode` differ (JGDMS includes the domain reference by identity; DirtyChai compares loader, principals, permissions and, for digest grants, the sources). | Read from the code. | Decide which is intended. |
| D5 | DIGEST_DOMAIN rules: DirtyChai requires `DigestCodeSource` instances and compares signers IN ORDER; JGDMS requires `clazz()`, compares signers as a SET, and matches via `CodeSourceKey` including `isVerifiedDigest`. | Read from the code. | Decide whether signer order is part of identity. |
| D6 | `SubjectDomain` (live principals) is not visible to DirtyChai's bundled `PrincipalGrant`, which cannot see that app-loaded class, so JGDMS's live-principal support is lost on DirtyChai. | CONFIRMED by class-loading probe (`BuiltinClassLoader.loadClassOrNull` special-cases this package: java.base first, then the class path). | Move the `SubjectDomain` interface into java.base, or accept snapshot principals. |
| D7 | `java.security.Permission` in DirtyChai is not `Serializable`, so `PermissionGrantBuilderImp.writeObject` cannot serialise any ordinary JDK permission: `NotSerializableException`. | CONFIRMED by the maintainer's test run. | **INTENDED (maintainer, 2026-10-23): same decision as D3, serialization is being removed.** The JGDMS test that needed a test-local serialisable permission documents a behaviour that is going away (J7). |
| D8 | `clazz()` state: DirtyChai resets domain, loader and `hasDomain` on every call; JGDMS keeps the previous state on `clazz(null)`. | CONFIRMED. DirtyChai behaviour is the better one (see J1). | JGDMS adopts DirtyChai's. |

## C. JGDMS-side items

| Id | Item | Evidence | Action |
|---|---|---|---|
| J1 | Builder reuse bug: `clazz(X).clazz(null)` leaves the grant bound to X. | CONFIRMED. | Port DirtyChai's reset; add a builder-reuse test. |
| J2 | `PrincipalGrant.toString` puts the comma after the last principal and none between (DirtyChai fixed this in `f8520cdaa`). Both forms parse back through JGDMS's lenient parser; only DirtyChai's is standard syntax. | CONFIRMED. | Port the fix, but see P2 (the comment text) first. |
| J3 | The superclass change in commits `b9cc0212d` (`ClassLoaderGrant` and `DigestDomainGrant` now extend `PrincipalGrant`) re-implemented `ProtectionDomainGrant`'s logic inside the two classes. It removed two API divergences but grew the source diff (`ClassLoaderGrant` from +55/-35 to +86/-36) and hid the semantic gap from the API-level test. | CONFIRMED. | Treat as interim; revisit when the DirtyChai items D1 to D5 are settled. |
| J4 | `jgdms-platform` compiles with `--release 21`, so DirtyChai-only APIs (`DigestCodeSource`, `CodeSource.getUriLocation()`, `SocketPermission.init()` and the `au.zeus.*` helpers) are invisible, which is why the JGDMS copies reach some of them by reflection. "Identical modulo imports" is therefore not reachable. | CONFIRMED. | A fixed, mechanical adaptation patch is the realistic residue. |
| J5 | `jgdms-platform`'s surefire run patched a hand-listed set of classes into `java.base` (`-Xbootclasspath/a` for four more). | CONFIRMED (previous test work). | **DONE (maintainer's decision, 2026-10-23):** that machinery is removed. The tests run against whichever copy the JDK loads and use public (and, for JGDMS-only subclasses, protected) API only; see "Running the jgdms-platform tests" at the end of section F. |
| J6 | Semantic differences between the two copies are not covered by any test. `BundledCopyConformanceTest` compares only public/protected API (empty allow list, 0 API divergences against the emulated trunk). | CONFIRMED. | Add an unpatched execution of the public-API semantic tests with a semantic allow list; add package-private checks and a pinned-commit sync script (no serial-form or cross-copy serialization checks: serialization is being removed, J7) (recommended stages 1 to 3 below). |
| J7 | Serialization of the grant classes is being removed (maintainer, 2026-10-23, see D3 and D7). The JGDMS copies still support it (`writeReplace`/`readResolve`, `PermissionGrantBuilderImp.writeObject`/`readObject`), and some JGDMS tests assert it: `voidAfterSerialization` and `classLoaderGrantSerializesThroughItsBuilder` in `DigestDomainGrantTest`, plus any round-trip tests of the other grants. | Read from the code and tests. | Make the JGDMS copies throw as DirtyChai's do, delete or invert those tests (a test that serialization is refused), and keep serial-form checks OUT of the semantic conformance work in J6. Check who in the repository (policy service, remote policy admin, `SubProcessLocalPolicyAdmin`, qa) sends a `PermissionGrant` over serialization before removing it. |

## D. Questions the maintainer has to decide first

1. Is the void null-class CLASSLOADER grant in DirtyChai trunk intended (D2)?
2. Will `ClassLoaderGrant.implies(CodeSource, Principal[])` be fixed in DirtyChai (D1)?
3. Does signer order matter for digest identity (D5)?
4. `SubjectDomain` into java.base, or drop live principals (D6)?
5. Are stock-JDK and OSGi support kept for these classes?
6. What is the conclusion on P2 and P3, and how will P1 be rewritten?
7. When is serialization removed on the JGDMS side (J7), and does anything still send a `PermissionGrant` over the wire?

## E. Recommended path (from the analysis; "vendor and patch", DirtyChai as upstream)

1. Pin the DirtyChai commit (`ed4691471` at the time of the analysis) and add the sync script in report-only mode.
2. Port DirtyChai's human-written fixes into JGDMS (`toString`, the `clazz()` reset, naming, Javadoc), guarded by new
   round-trip and builder-reuse tests.
3. Add the unpatched semantic test execution with a semantic allow list.
4. Re-express JGDMS's verified-code grant as a decorator outside the 13 shared classes (via the public
   `protected PermissionGrant(PermissionGrant decorated)` constructor, which can only narrow a grant), then vendor DirtyChai's
   DIGEST_DOMAIN code.
5. Once the DirtyChai fixes for D1, D2 and D6 land, vendor `ClassLoaderGrant`, `PrincipalGrant` and the builder implementation.
6. Make the sync check fail the build.

Human code may flow DirtyChai to JGDMS (Apache-2.0 headers on both sides: keep them and name the DirtyChai commit); no
AI-assisted code should be written into DirtyChai.

## F. Package layout under consideration (maintainer, 2026-10-23)

The classes in `org.apache.river.api.security` that are not of Apache River origin could move to `au.net.zeus.jgdms.api.policy`, to
minimise what shares the package with DirtyChai's bundled classes. By the date each file was first added in this repository's
history (which includes the River history), the JGDMS-only classes added in 2026 are: `ExternallyVoidablePermissionGrant`
(2026-05-14), `LocalPrincipalProvider` (05-25), `LeasedDelegation` and `LeasedPermissionGrant` (06-16), `OneShot` and
`OneShotLeasedPermissionGrant` (07-06), `UserSubjectSupport` (08-18) and `CodeSourceKey` (10-18). The leased grants are not in
DirtyChai (maintainer). Open points: whether the River-origin JGDMS-only classes (for example `ConcurrentPolicyFile`,
`DefaultPolicyParser`, `PolicyUtils`) stay, since while any JGDMS class stays in the package it remains split across the boot and
app loaders; the effect on references (policy files, DER/serializer registries, bnd `-exportcontents` and `packageinfo`, the
compat-gate ledger, `qa`); and whether the 2026 classes are in the compat-gate baseline at all.

### F. Status (2026-10-23): seven of the eight classes moved

`ExternallyVoidablePermissionGrant`, `LocalPrincipalProvider`, `LeasedDelegation`, `LeasedPermissionGrant`, `OneShot`,
`OneShotLeasedPermissionGrant` and `UserSubjectSupport` now live in `au.net.zeus.jgdms.api.policy` (module jgdms-platform, beside
`RemotePolicyProvider`, `RemotePolicyService` and `VerdictPermissionMapper`), moved with `git mv` so their history follows. What was
checked, all CONFIRMED by reading or running:

- The answer to "whether the 2026 classes are in the compat-gate baseline at all": none of the seven exists in the release 3.1.0
  jar or at the tag `baseline/pre-ai-agents`, so the gate sees neither a removal nor an addition, and no deprecated forwarding class
  was left in the old package (a forwarder would keep a JGDMS-only class in the shared package, which is what the move avoids).
- DirtyChai names none of the seven (its `java.base` copies of the package do not reference them), so the move cannot break it.
  `JGDMS_COMPATIBILITY.md` in DirtyChai still lists two of them (`ExternallyVoidablePermissionGrant`, `LeasedPermissionGrant`) as
  JGDMS-only classes of `org.apache.river.api.security`; that prose is the maintainer's to update.
- None is `Serializable`, none is named in a policy file, serializer registry, `META-INF` list, golden file or module descriptor.
- They use only public and protected members of the classes that stay (`PermissionGrant`, `PermissionGrantBuilder`,
  `RevocablePolicy`), and no class that stays uses them. The package-private test seams of the moved classes (the `Clock`
  constructors and overloads, `UserSubjectSupport.lookupCallAsMulti` and `lookupConstructor`) stay package-private, so the tests
  that use them moved into the new package.
- The OSGi export list of jgdms-platform (`bnd.bnd`, `-exportcontents`) now names `au.net.zeus.jgdms.api.policy`; jgdms-jeri and
  jgdms-pref-class-loader import it.

`CodeSourceKey` stays in `org.apache.river.api.security` for now. It is package-private and used by four classes that stay and
are shared with DirtyChai (`DigestDomainGrant`, `DigestGrant`, `PermissionGrantBuilderImp`, `ProtectionDomainGrant`). Moving it
would make it and four of its members public, and would make those four shared classes depend on a JGDMS-only package. A later change deals with it.

Still open from this section: whether the River-origin JGDMS-only classes (for example `ConcurrentPolicyFile`,
`DefaultPolicyParser`, `PolicyUtils`) stay. While any JGDMS class stays in the package it remains split across the boot and app
loaders.

### Running the jgdms-platform tests (J5 resolved)

The surefire run no longer patches anything into `java.base`. On a JDK that bundles the 13 classes (DirtyChai) the tests run
against the JDK's copy, on any other JDK against the copies on the class path; they use public API only (protected for the
JGDMS-only subclasses), and tests of behaviour that only one kind of copy has skip themselves with an `Assume`. To run the tests
against the classes of a NEWER JDK than the one that runs Maven, optionally, give a directory of class files:
`mvn -pl jgdms-platform test -Djgdms.dirtychai.patch=DIR` (the `dirtychai-patch` profile in the pom, active only when the property is
given; it adds `--patch-module=java.base=DIR` and `--add-exports java.base/org.apache.river.api.security=ALL-UNNAMED`). Add
`-Djgdms.conformance.jdkClasses=DIR2` so that `BundledCopyConformanceTest` compares against the classes in `DIR2` (it reads the
runtime image otherwise, which does not show a patch).

`tests.support.SecurityCopy` tells the tests which copy is loaded, by public API: `PermissionGrantBuilder.class.getClassLoader() != null`
means this module's copy, and the presence of `PermissionGrantBuilder.DIGEST_DOMAIN` with `codeSources(CodeSource...)` means a copy not
older than the pinned DirtyChai trunk. Tests of behaviour that only this module's copy has (the null-class CLASSLOADER grant that applies
to every domain, the `toString` text of `DigestDomainGrant`, its package-private constructor, and every serialization round trip: J7)
skip themselves when the JDK's copy is loaded; tests of fixes that an older bundled copy lacks (signer checks of `CertificateGrant` and
`URIGrant` for code without signers, a `PrincipalGrant` that matches a domain without a `CodeSource`, `DIGEST_DOMAIN`) skip themselves
on such a copy. This module's copies are exercised by running the tests on a JDK that bundles nothing, for example
`mvn -pl jgdms-platform test -Djvm=/path/to/jdk-21/bin/java`.

## G. Planned for the future: compile-time stubs instead of copies (maintainer, 2026-10-23)

Not done yet. For now `jgdms-platform` keeps its full copies of the 13 classes it shares with DirtyChai, and its tests run against
whichever copy the JDK loads (see J5 and the note below). The intent of having the classes in JGDMS is that JGDMS compiles on OpenJDK. Stock JDK 21 to 23 is
compile-only: it cannot run this security code safely (no digest support, code identity by name, which can be DNS-spoofed, no
class-loading gates). OSGi runs on DirtyChai and must use DirtyChai's classes. So the copies are to be replaced by generated,
signature-only stubs used at compile time only, with DirtyChai's classes the only ones that run.

An experiment was done on a throwaway local branch (`stubs-experiment`, commit `91b1531c3` on top of `67bd83fba`, never pushed;
worktree `/tmp/claude-0/stubs-wt` in the 2026-10-23 session, which is not durable). Its findings:

* A generator (`tools/stubgen/StubGen.java`, JDK 24+ `java.lang.classfile`) writes the 13 stubs from DirtyChai's compiled classes
  or `jrt:/`: 488 lines (188 of code) against 2781 lines of copies. Regeneration takes about 2 s. A new module
  `jgdms-dirtychai-stubs` holds them, with `DIRTYCHAI-COMMIT` pinned to `ed4691471e3469418ec82c42bccf87af40160b7e` and a
  conformance test; the dependency is `provided` scope (not transitive, so seven poms declared it; better once in the parent pom).
* `jgdms-platform` and the dependents that build offline compiled on a stock JDK 21.0.12 with the stubs and the copies deleted.
* With DirtyChai's classes supplying the real behaviour (an emulation), the platform tests were test-for-test the same as with
  the copies except three tests of copy-only behaviour (the null-class CLASSLOADER "all domains" grant, a serialization round
  trip, the `toString` text), which are the behaviours expected to go (D2, J7).
* The platform bundle's `Export-Package` and `Import-Package` for the shared package were unchanged. An OSGi framework on
  DirtyChai probably needs `org.osgi.framework.bootdelegation=org.apache.river.api.security` (not `system.packages.extra`, which
  would make the system bundle a second exporter). Not tested: no framework was available offline. Felix 7.0.5 is downloadable
  from Maven Central through the session proxy, so a resolution test is possible.
* To do before adopting: declare the stubs dependency once in the parent pom; decide the conformance-test policy when the CI JDK is
  older than the pin; wire `DirtyChaiCheck` (a fail-fast check that the 13 classes come from the boot loader) into the policy
  entry points and add an API fingerprint check; a compat-gate ledger entry (the five public shared classes would show as
  removed); update `GETTING_STARTED.md` (stock OpenJDK is compile-only), the `jwt-integration-tests.yml` Temurin 21 leg, and
  `findbugs-exclude.xml`; settle `CodeSourceKey` (delete once its four users in the shared package are gone, unless the
  verified-code decorator still needs it).
