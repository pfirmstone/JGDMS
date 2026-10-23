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
| D1 | `ClassLoaderGrant.implies(CodeSource, Principal[])` was inherited from `PrincipalGrant` and returned `implies(pal)`: a loader grant implied ANY CodeSource, including null, whenever the principals matched (always, for a grant without principals). A privileged grant would imply AllPermission for any CodeSource. | CONFIRMED by running the public API against both copies. Latent: the only caller, `ConcurrentPolicyFile.getPermissions(CodeSource)`, only iterates grants parsed from the policy file. Broke DirtyChai's own rule that a null CodeSource is never implied. **Re-probed 2026-10-23 after the fix, see "D1 observed and fixed" below.** | **FIXED in DirtyChai `dafbac90a` (maintainer, 2026-10-23, "ClassLoader grant shouldn't inherit super class behaviour for CodeSource and Principals"): `ClassLoaderGrant` overrides `implies(CodeSource, Principal[])` and returns false. CONFIRMED by probe on the classes of trunk `95ef6f96b`: false for every case. JGDMS's copy already did this; `DigestDomainGrantTest.classLoaderGrantNeverImpliesACodeSource` now pins it on both copies.** |
| D2 | A CLASSLOADER grant with no `clazz()` is void in DirtyChai trunk (`841105767`, 2026-10-21). JGDMS (and this container's older JDK) treated it as applying to all domains, as `DynamicPolicy.grant(null, ...)` promised. | CONFIRMED by probes of the public API, rerun 2026-10-23 on the emulated trunk (`95ef6f96b` classes; first run on `ed4691471`, same result except D1) and on the JGDMS copy; see "D2 observed" below. | **INTENDED (maintainer, 2026-10-23): the grant is tied to a ClassLoader's class and is garbage-collected when that loader is removed; "no class" is the state of a grant whose loader is gone, so it must never silently become "all domains".** JGDMS adopted it (see "D2 done in JGDMS" below). |
| D3 | Deserializing a DIGEST_DOMAIN grant throws in DirtyChai (`readResolve` no longer returns the null grant; `build()` throws `IllegalArgumentException("ClassLoader must not be null")`). JGDMS returns the void grant. | Read from the code; NOT run on DirtyChai. | **INTENDED (maintainer, 2026-10-23): serialization support is being removed from these classes because it is not good security practice, and making it throw was easier than implementing it. No DirtyChai change. See J7 for the JGDMS side.** |
| D4 | `ClassLoaderGrant.impliesEquivalent`: JGDMS compares loaders only; DirtyChai also requires equal principals and non-null loaders (stricter). `equals`/`hashCode` differ (JGDMS includes the domain reference by identity; DirtyChai compares loader, principals, permissions and, for digest grants, the sources). | Read from the code. | Decide which is intended. |
| D5 | DIGEST_DOMAIN rules: DirtyChai requires `DigestCodeSource` instances and compares signers IN ORDER (through `DigestCodeSource.equals`); JGDMS required `clazz()`, compared signers as a SET, and matched via `CodeSourceKey` including `isVerifiedDigest`. | Read from the code. | **RULED (maintainer, 2026-10-23): `DigestDomainGrant` is a dynamic grant that lasts only as long as the ClassLoader it is granted to; it applies iff the `DigestCodeSource` is one that this ClassLoader loaded, or it isn't. Signer order, signer-set comparison and `isVerifiedDigest`-based matching are NOT the identity model.** DONE in the JGDMS copy, see "D5 done in JGDMS" below. |
| D6 | `SubjectDomain` (live principals) is not visible to DirtyChai's bundled `PrincipalGrant`, which cannot see that app-loaded class, so JGDMS's live-principal support is lost on DirtyChai. | CONFIRMED by class-loading probe (`BuiltinClassLoader.loadClassOrNull` special-cases this package: java.base first, then the class path). | **RESOLVED by removal (maintainer, 2026-10-23): `SubjectDomain` was removed ("We are implementing better solutions to the problem it attempts to solve"); superseded by the multi-subject work (`JGDMS-STD-003`). No `java.base` interface is needed and nothing is lost on DirtyChai: JGDMS's `PrincipalGrant.getPrincipals` now reads `pd.getPrincipals()` only, as DirtyChai's does. Removed with it: `Security.doAs` and `Security.doAsPrivileged` (two overloads each; no callers anywhere in the repository) and the private `DistributedSubjectCombiner` and `SubjectProtectionDomain`. Accepted break, ledger category G.** |
| D7 | `java.security.Permission` in DirtyChai is not `Serializable`, so `PermissionGrantBuilderImp.writeObject` cannot serialise any ordinary JDK permission: `NotSerializableException`. | CONFIRMED by the maintainer's test run. | **INTENDED (maintainer, 2026-10-23): same decision as D3, serialization is being removed.** The JGDMS test that needed a test-local serialisable permission documents a behaviour that is going away (J7). |
| D8 | `clazz()` state: DirtyChai resets domain, loader and `hasDomain` on every call; JGDMS keeps the previous state on `clazz(null)`. | CONFIRMED. DirtyChai behaviour is the better one (see J1). | JGDMS adopts DirtyChai's. |

## C. JGDMS-side items

| Id | Item | Evidence | Action |
|---|---|---|---|
| J1 | Builder reuse bug: `clazz(X).clazz(null)` leaves the grant bound to X. | CONFIRMED. | Port DirtyChai's reset; add a builder-reuse test. |
| J2 | `PrincipalGrant.toString` puts the comma after the last principal and none between (DirtyChai fixed this in `f8520cdaa`). Both forms parse back through JGDMS's lenient parser; only DirtyChai's is standard syntax. | CONFIRMED. | Port the fix, but see P2 (the comment text) first. |
| J3 | The superclass change in commits `b9cc0212d` (`ClassLoaderGrant` and `DigestDomainGrant` now extend `PrincipalGrant`) re-implemented `ProtectionDomainGrant`'s logic inside the two classes. It removed two API divergences but grew the source diff (`ClassLoaderGrant` from +55/-35 to +86/-36) and hid the semantic gap from the API-level test. | CONFIRMED. | Treat as interim. D2 and D5 are now settled and the JGDMS copies follow them (the "all domains" mode and the domain/signer-set bookkeeping are gone from `ClassLoaderGrant` and `DigestDomainGrant`); D1 and D4 are still open, revisit when they are. |
| J4 | `jgdms-platform` compiles with `--release 21`, so DirtyChai-only APIs (`DigestCodeSource`, `CodeSource.getUriLocation()`, `SocketPermission.init()` and the `au.zeus.*` helpers) are invisible, which is why the JGDMS copies reach some of them by reflection. "Identical modulo imports" is therefore not reachable. | CONFIRMED. | A fixed, mechanical adaptation patch is the realistic residue. |
| J5 | `jgdms-platform`'s surefire run patched a hand-listed set of classes into `java.base` (`-Xbootclasspath/a` for four more). | CONFIRMED (previous test work). | **DONE (maintainer's decision, 2026-10-23):** that machinery is removed. The tests run against whichever copy the JDK loads and use public (and, for JGDMS-only subclasses, protected) API only; see "Running the jgdms-platform tests" at the end of section F. |
| J6 | Semantic differences between the two copies are not covered by any test. `BundledCopyConformanceTest` compares only public/protected API (empty allow list, 0 API divergences against the emulated trunk, still 0 on `95ef6f96b`; D1 is a behaviour change with no signature change, so it could not be seen). | CONFIRMED. | Add an unpatched execution of the public-API semantic tests with a semantic allow list; add package-private checks and a pinned-commit sync script (no serial-form or cross-copy serialization checks: serialization is being removed, J7) (recommended stages 1 to 3 below). |
| J7 | Serialization of the grant classes is being removed (maintainer, 2026-10-23, see D3 and D7). The JGDMS copies still support it (`writeReplace`/`readResolve`, `PermissionGrantBuilderImp.writeObject`/`readObject`), and some JGDMS tests assert it: `voidAfterSerialization` and `classLoaderGrantSerializesThroughItsBuilder` in `DigestDomainGrantTest`, plus any round-trip tests of the other grants. | Read from the code and tests. | Make the JGDMS copies throw as DirtyChai's do, delete or invert those tests (a test that serialization is refused), and keep serial-form checks OUT of the semantic conformance work in J6. Check who in the repository (policy service, remote policy admin, `SubProcessLocalPolicyAdmin`, qa) sends a `PermissionGrant` over serialization before removing it. |

## D. Questions the maintainer has to decide first

1. ~~Is the void null-class CLASSLOADER grant in DirtyChai trunk intended (D2)?~~ Yes, intended (2026-10-23). New: a PROTECTIONDOMAIN grant built without a class is still a grant to ALL domains in both copies (observed 2026-10-23: `isVoid()` false, implies any domain). Is that intended, or should it be void like the CLASSLOADER grant?
2. ~~Will `ClassLoaderGrant.implies(CodeSource, Principal[])` be fixed in DirtyChai (D1)?~~ Done in DirtyChai `dafbac90a` (2026-10-23).
3. ~~Does signer order matter for digest identity (D5)?~~ Ruled 2026-10-23, see D5. Open from the ruling: `DigestDomainGrant` still compares a domain's `DigestCodeSource` with the supplied ones through `DigestCodeSource.equals` (DirtyChai does the same), which compares certificates in order; and `ProtectionDomainGrant` (JGDMS: `CodeSourceKey`, signer SET; DirtyChai: its own `CodeSourceKey`, `Arrays.equals` on certificates, signer ORDER) was not covered by the ruling. Does the ruling extend to it, and is the `unverified()` guard (kept, see below) wanted?
4. ~~`SubjectDomain` into java.base, or drop live principals (D6)?~~ Decided 2026-10-23: removed, no `java.base` interface (see D6).
5. Are stock-JDK and OSGi support kept for these classes?
6. What is the conclusion on P2 and P3, and how will P1 be rewritten?
7. When is serialization removed on the JGDMS side (J7), and does anything still send a `PermissionGrant` over the wire?

### D2 observed and done (2026-10-23)

Probed through the public API (`PermissionGrantBuilder`, `PermissionGrant`), with a throwaway program run on (a) the classes of the
pinned trunk `95ef6f96b` (which includes `841105767`; the first run of this probe was on `ed4691471`) patched over `java.base` of `/opt/dirtychai/jdk-27`, (b) this repository's
copy on a stock JDK 21 and (c) the unpatched `/opt/dirtychai/jdk-27` image. The image is OLDER than `841105767`: it has no
`DigestDomainGrant` and treats a null-class CLASSLOADER grant as "all domains", so `/opt/dirtychai/jdk-27` alone does not show the
trunk behaviour; the compiled trunk classes are needed.

Observed on the trunk (CLASSLOADER, no `clazz()`): `build()` does not throw and returns a `ClassLoaderGrant`; `isVoid()` is true;
`isDyanamic()` true; `implies` is false for a ProtectionDomain with a loader, with a null loader, for a null ProtectionDomain, for
`implies(ClassLoader, ...)` with any loader and with null; two such grants with equal principals and permissions are `equals`.
`clazz(String.class)` (a bootstrap class, no loader) is void too. One more difference, which was D1 and not D2: on `ed4691471`
`implies(CodeSource, Principal[])` of that void grant was TRUE (inherited from `PrincipalGrant`); the JGDMS copy returned false. On `95ef6f96b` it is false (D1 fixed, below); the rest of this probe's output is unchanged between the two commits.
Observed on the old image and on the unmodified JGDMS copy: `isVoid()` false, every domain implied.

JGDMS changes (the JGDMS copy only; no DirtyChai code was written or changed):

* `ClassLoaderGrant`: a grant without a domain is void and implies nothing; two such grants are `equals` as on the trunk;
  Javadoc and `toString` no longer say "all domains". `build()` still does not throw, as on the trunk (throwing would be a
  second behaviour to maintain, and a void grant is already unable to widen anything).
* `PermissionGrantBuilder` (`CLASSLOADER`, `clazz`) and `DynamicPolicy` (`grant`, `getGrants`) and `Security.grant` Javadoc: the
  "null means all domains" promises are replaced by "null grants nothing" (`getGrants(null, ...)` returns an empty array).
* `DynamicPolicyProvider.grant(null, ...)`: still checks `GrantPermission` and null elements, then stores nothing (the void grant
  would only sit there until `refresh()`); it does not throw. A caller that relied on a universal grant now gets none, which fails
  closed. No caller in this repository passes a null class (`Security.grant`, the proxy preparers, `ActivatableInvocationHandler`,
  the policy providers all pass the class of a proxy); `ConcurrentPolicyFile` and the policy parsers have no `classLoader` grant
  syntax, so a policy file cannot ask for it.
* Tests: `DigestDomainGrantTest.classLoaderGrantWithoutAClassIsVoid` (replaces `classLoaderGrantWithoutAClassCoversEveryDomain`;
  runs on both copies, skipped only on a copy older than `DIGEST_DOMAIN`), `dynamicPolicyGrantWithoutAClassGrantsNothing` (new),
  and `classLoaderGrantSerializesThroughItsBuilder` (JGDMS copy only, serialization, J7: a no-class grant is void before and after).
* QA (not run here, the harness needs the Ant build, the HTTP class server and the QA jars; changed by reading, and the
  dynamic-policy assertions were reproduced in a small stand-alone program on both copies): `GrantNoPrincipalCase01/02`,
  `RefreshNoPrincipal`, `GetGrantsNoPrincipal` (through `checkGetGrants`), `NullCases`, and
  `LoaderSplitPolicyProviderTest` now expect that a grant with a null class grants nothing (the null ProtectionDomain is no
  longer implied; `getGrants(null, ...)` is empty); the loader-split test grants to the classes of the loaders instead of null.
  `SubPoliciesTest` and `NullCasesTest` (aggregate provider) only check which exception a `grant(null, ...)` call throws and
  needed no change. `GrantTest1/2/3` and `Grant_Test` use a mock provider.

### D1 observed and fixed (2026-10-23)

Probed through the public API with a throwaway program (not kept; not in the build): a `CLASSLOADER` grant built with
`PermissionGrantBuilder`, with and without `clazz()`, with grant principals none or `[a]`; `implies(CodeSource, Principal[])` called
with a null CodeSource, a plain CodeSource, the CodeSource of the granted class, and principals none, `[a]`, `[b]`, `[a,b]`
(7 calls per grant, 28 per run).

| Classes | `implies(CodeSource, Principal[])` of a loader grant |
|---|---|
| trunk `ed4691471` patched over `java.base` | TRUE in 22 of the 28 calls: every call for a grant without principals (with or without a class), and every call whose principals include `a` for a grant to `[a]`, null CodeSource included |
| trunk `95ef6f96b` (`dafbac90a` and later) patched over `java.base` | FALSE in all 28, with a class and without |
| this repository's copy, stock JDK 21 | FALSE in all 28 (output identical to that of `95ef6f96b`, apart from which loader loads `PermissionGrantBuilder`) |

`BundledCopyConformanceTest` could not see D1 (the JGDMS override was already there and the method is public in both: an override
changes behaviour, not a signature), so it reported 0 divergences before and after the fix. A behaviour test was missing, and is
now `DigestDomainGrantTest.classLoaderGrantNeverImpliesACodeSource` (public API only; runs on this repository's copy and on a bundled copy that has
`DIGEST_DOMAIN`; skipped on an older bundled copy such as the unpatched `/opt/dirtychai/jdk-27` image). Checked by mutation: it fails
on the `ed4691471` classes ("without class, grantee []: implies(null, [])") and on this repository's copy with its override replaced by
`super.implies(codeSource, pal)`; it passes on the `95ef6f96b` classes and on the unmodified copy.

**A JDK built before `dafbac90a` skips the D1 test.** `classLoaderGrantNeverImpliesACodeSource` first probes the JDK's bundled copy; if a void
CLASSLOADER grant still implies a null CodeSource (D1 present) it is skipped with that reason (found when the maintainer's JDK, newer than the
image's but older than the fix, failed it: 473 run, 1 failure). It runs and passes on `dafbac90a` and later and on the JGDMS copy. The cost: a
regression of D1 on a later DirtyChai would skip it, not fail it; the skip message names the commit.

**Commit `95ef6f96b` did not compile as committed.** `ClassLoaderGrant.java` (changed by `dafbac90a`) used `CodeSource` in the new
override without `import java.security.CodeSource;`. The maintainer fixed that in trunk `29d03fd1c` (2026-10-23). The same 33 classes compiled
from unmodified `29d03fd1c` are identical to the emulation built earlier from `95ef6f96b` plus the one import, so the results below stand for
`29d03fd1c` without a re-run.

### D5 inventory and what was done (2026-10-23)

Every use of `CodeSourceKey` (package-private, `org.apache.river.api.security`), before the change:

| User | What for | Needed for D5? |
|---|---|---|
| `PermissionGrantBuilderImp.buildDigestDomainGrant` | validated each supplied code source (usable location, has a digest, algorithm SHA-256) and built the `Set<CodeSourceKey>` handed to `DigestDomainGrant` | yes |
| `DigestDomainGrant` | held the keys as the grant's identity (`verified`); `implies(pd)` took `CodeSourceKey.of(pd.getCodeSource())` and tested set membership after `isVerifiedDigest`; keys also in `equals`, `hashCode`, `impliesEquivalent`, `toString` | yes |
| `DigestGrant` | `CodeSourceKey.isVerifiedDigest(codeSource)` only: a policy-file digest grant (a `URIGrant` subclass, not bound to a loader) ignores digests asserted by a caller | no |
| `ProtectionDomainGrant.impliesCodeSource` | compares the CodeSource of a domain rebuilt by a DomainCombiner with that of the granted domain without `CodeSource.equals` (DNS): location Uri, signers (as a set) and digest | no |
| `CodeSourceKey` itself | the key, plus `isVerifiedDigest` | n/a |

Compare DirtyChai `95ef6f96b` (no change to this point since `ed4691471`, apart from the unused import that `dafbac90a` removed from `DigestDomainGrant`), read only: its `DigestDomainGrant` imports its `CodeSourceKey` (`au.zeus.jdk.authorization.policy`)
but does not use it; it keeps the supplied `DigestCodeSource[]` and tests `dcs.equals(supplied)` after `dcs.unverified()`; that
`CodeSourceKey` is used by `SecureClassLoader` (domain cache), `ProtectionDomainGrant` and `SecurityPolicyWriter`.

D5 done in JGDMS. The maintainer's update of the same day: `DigestCodeSource` is public, so the JGDMS copy can recognise it by
the NAME of its class (`java.security.DigestCodeSource`, final, so `cs.getClass().getName()` equal to that and a null class
loader) and call its public accessors by reflection; no stubs are needed for this. `DigestDomainGrant` is now
`(WeakReference<ClassLoader>, List<CodeSource>, Principal[], Permission[])`, as in DirtyChai:

* lasts as long as its ClassLoader (no `ProtectionDomain` reference any more: it is void when the loader is collected);
* `implies(pd)`: same loader by reference; principals; `pd`'s CodeSource is a `DigestCodeSource` and is one of the supplied ones
  by `DigestCodeSource.equals`; no `CodeSourceKey`, no signer set, no location or digest comparison of its own;
* the constructor refuses (NPE / IAE) a null loader, no code sources, a code source that is not a `DigestCodeSource`, a digest
  algorithm other than SHA-256, or no digest; `build()` refuses a missing class or loader as before;
* kept on purpose, as DirtyChai does (`dcs.unverified()`): a domain whose digest was asserted by a caller, not computed, is not
  implied. It is a guard, not identity, and is one line (`isComputed`) to drop if the maintainer rules that it must go.

`CodeSourceKey` is NOT deleted: `ProtectionDomainGrant` and `DigestGrant` still need it for reasons other than D5 (rows above).
What changed in it: `hasDigest()` and `digestAlgorithm()` (used only by the builder) are removed and `uri` is private.
What remains and why: `ProtectionDomainGrant` needs a DNS-free CodeSource comparison (DirtyChai has its own public key class in
`au.zeus.jdk.authorization.policy`, which the JGDMS copy cannot use: it is not visible to a `--release 21` compile and does not
exist on a stock JDK); `DigestGrant` needs `isVerifiedDigest`. Both go when the planned
stubs (section G) make DirtyChai's own classes the only ones that run.

Verification: `DigestDomainGrantTest` passes on plain, emulated trunk and stock JDK 21, but on none of those does the JGDMS
`DigestDomainGrant` meet a real `DigestCodeSource` (stock has none; the others load DirtyChai's `DigestDomainGrant`). To run it
once, as an experiment kept out of the build, the classes of the JGDMS copy that the trunk bundles (plus `CodeSourceKey`,
`PermissionComparator`, `org.apache.river.api.net.*`) were compiled and patched over `java.base` together with the trunk's
`java.security.DigestCodeSource`, and the same test run: 23 tests, 4 skipped (the tests of this module's copy only, which skip themselves on a boot-loaded copy), 0 failed.
Mutations of the patched `DigestDomainGrant` were caught: dropping the `unverified` guard fails
`aDigestAssertedByACallerIsNotIdentity`, dropping the loader check fails `anotherLoaderOverIdenticalJarsIsNotImplied` and
`aProxyClassUsesTheLoaderOfTheClass`. Dropping the DigestCodeSource name check alone is not caught, because the `unverified`
guard and `equals` also fail closed for a plain CodeSource (redundant guards).

## E. Recommended path (from the analysis; "vendor and patch", DirtyChai as upstream)

1. Pin the DirtyChai commit (`29d03fd1c` at the last refresh, 2026-10-23; `ed4691471` at the time of the analysis) and add the sync script in report-only mode.
2. Port DirtyChai's human-written fixes into JGDMS (`toString`, the `clazz()` reset, naming, Javadoc), guarded by new
   round-trip and builder-reuse tests.
3. Add the unpatched semantic test execution with a semantic allow list.
4. Re-express JGDMS's verified-code grant as a decorator outside the 13 shared classes (via the public
   `protected PermissionGrant(PermissionGrant decorated)` constructor, which can only narrow a grant), then vendor DirtyChai's
   DIGEST_DOMAIN code.
5. Once the DirtyChai fixes for D2 and D6 land (D1 has landed, `dafbac90a`), vendor `ClassLoaderGrant`, `PrincipalGrant` and the builder implementation.
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

`SubjectDomain` (public marker interface, `org.apache.river.api.security`) was the one JGDMS-only public type in the package that was
not moved: it was removed instead on 2026-10-23, at the maintainer's direction, together with its only implementation and users
(see D6), so it no longer appears in the package layout. `PrincipalGrant` therefore has no JGDMS-only branch left in `getPrincipals`.

`CodeSourceKey` stays in `org.apache.river.api.security` for now. It is package-private and was used by four classes that stay and
are shared with DirtyChai (`DigestDomainGrant`, `DigestGrant`, `PermissionGrantBuilderImp`, `ProtectionDomainGrant`); since D5 only
`DigestGrant` and `ProtectionDomainGrant` use it. Moving it
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
older than the pinned DirtyChai trunk. Tests of behaviour that only this module's copy has (the `toString` text of `DigestDomainGrant`, its package-private
constructor, and every serialization round trip: J7; the null-class CLASSLOADER grant is void in both copies since D2 and is tested on either)
skip themselves when the JDK's copy is loaded; tests of fixes that an older bundled copy lacks (signer checks of `CertificateGrant` and
`URIGrant` for code without signers, a `PrincipalGrant` that matches a domain without a `CodeSource`, `DIGEST_DOMAIN`) skip themselves
on such a copy. Emulating the trunk (refreshed 2026-10-23 at DirtyChai `95ef6f96b`): compile, with `/opt/dirtychai/jdk-27`'s `javac`,
`--patch-module java.base=<dirtychai>/src/java.base/share/classes -implicit:none -d OUT` over the 13 sources of
`org/apache/river/api/security`, `au/zeus/jdk/authorization/policy/{CodeSourceKey,PolicyUtils}`, `java/security/{DigestCodeSource,CodeSource}`,
`sun/security/provider/InternalDigests` and `au/zeus/jdk/net/{Uri,UriParser,URIEncoderDecoder}` (33 class files; the image is older
than the trunk and lacks some of them). At `95ef6f96b` `ClassLoaderGrant.java` lacked its `CodeSource` import (fixed in `29d03fd1c`, see "D1 observed
and fixed"); from `29d03fd1c` on no overlay is needed.
Then `JAVA_HOME=/opt/dirtychai/jdk-27 mvn -o -pl jgdms-platform test -Djgdms.dirtychai.patch=OUT -Djgdms.conformance.jdkClasses=OUT`.
Result on `95ef6f96b`: 473 tests, 2 failures, 10 skipped, `BundledCopyConformanceTest` 15 classes compared, 0 divergences. The two
failures are the same as on `ed4691471` (`CombinerDebugRecursionTest.combinerDebugDoesNotRecurseOrLeakPrivateCredentials` and
`MultiSubjectGrantAuthorizationTest.stockCheckPermissionPrimitiveFoldsBoundSubjects`, both of the `jdk-27` image's combiner and
`AccessController`, neither of this package; their cause was not investigated again). The 10 skips: six of behaviour that only this module's copy has (the
`toString` text, the non-public `DigestDomainGrant` constructor, `voidAfterSerialization`, `classLoaderGrantSerializesThroughItsBuilder`,
two `DigestGrantTest` serialization round trips: J7) and four of the multi-Subject API that the `jdk-27` image lacks. On a stock
JDK 21 (`-Djvm=/usr/lib/jvm/java-21-openjdk-amd64/bin/java`) the run is 473 tests, 0 failures, 35 skipped.
This module's copies are exercised by running the tests on a JDK that bundles nothing, for example
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
  `jgdms-dirtychai-stubs` holds them, with `DIRTYCHAI-COMMIT` pinned to the DirtyChai commit of the day (`ed4691471e3469418ec82c42bccf87af40160b7e` in the experiment; `95ef6f96b0c00c2226df352624672e30342911bd` now) and a
  conformance test; the dependency is `provided` scope (not transitive, so seven poms declared it; better once in the parent pom).
* `jgdms-platform` and the dependents that build offline compiled on a stock JDK 21.0.12 with the stubs and the copies deleted.
* With DirtyChai's classes supplying the real behaviour (an emulation), the platform tests were test-for-test the same as with
  the copies except three tests of copy-only behaviour (the null-class CLASSLOADER "all domains" grant, a serialization round
  trip, the `toString` text), which are the behaviours expected to go (D2, J7). (D2 has since been settled and the first of the
  three removed from the JGDMS copy.)
* The platform bundle's `Export-Package` and `Import-Package` for the shared package were unchanged. An OSGi framework on
  DirtyChai probably needs `org.osgi.framework.bootdelegation=org.apache.river.api.security` (not `system.packages.extra`, which
  would make the system bundle a second exporter). Not tested: no framework was available offline. Felix 7.0.5 is downloadable
  from Maven Central through the session proxy, so a resolution test is possible.
* To do before adopting: declare the stubs dependency once in the parent pom; decide the conformance-test policy when the CI JDK is
  older than the pin; wire `DirtyChaiCheck` (a fail-fast check that the 13 classes come from the boot loader) into the policy
  entry points and add an API fingerprint check; a compat-gate ledger entry (the five public shared classes would show as
  removed); update `GETTING_STARTED.md` (stock OpenJDK is compile-only), the `jwt-integration-tests.yml` Temurin 21 leg, and
  `findbugs-exclude.xml`; settle `CodeSourceKey` (after D5 two users remain in the shared package, `ProtectionDomainGrant` and `DigestGrant`;
  delete once those are gone).
