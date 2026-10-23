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
| P4 | Unreviewed beyond the three above. | The analysis compared 13 files against git history in a window; it did not audit every DirtyChai line against every JGDMS commit. | A full review is open if wanted. |
| P5 | Outbound to OpenJDK needs GPLv2 + Classpath Exception under the OCA; the DirtyChai files carry ASF Apache-2.0 headers, and `UnresolvedPrincipal` is Apache Harmony derived (author A. V. Varlamov). | Headers read in the files. | A legal question for the maintainer, not decided here. |

## B. Behaviour of DirtyChai's bundled copy that differs from the JGDMS contract

These matter because on DirtyChai the JDK's copy is what runs (the JGDMS copy of the 13 shared classes runs only in
`jgdms-platform`'s own tests, under OSGi, and on a stock JDK). Each needs a human-written DirtyChai change or a decision.

| Id | Item | Evidence | Proposed action |
|---|---|---|---|
| D1 | `ClassLoaderGrant.implies(CodeSource, Principal[])` is inherited from `PrincipalGrant` and returns `implies(pal)`: a loader grant implies ANY CodeSource, including null, whenever the principals match. A privileged grant would imply AllPermission for any CodeSource. | CONFIRMED by running the public API against both copies. Latent today: the only caller, `ConcurrentPolicyFile.getPermissions(CodeSource)`, only iterates grants parsed from the policy file. Breaks DirtyChai's own rule that a null CodeSource is never implied. | Override in `ClassLoaderGrant` to return false (DirtyChai's `DigestDomainGrant` already does). |
| D2 | A CLASSLOADER grant with no `clazz()` is void in DirtyChai trunk (`841105767`, 2026-10-21). JGDMS (and this container's older JDK) treats it as applying to all domains, as `DynamicPolicy.grant(null, ...)` promises. | CONFIRMED by the probes. QA `GrantNoPrincipalCase02` and `NullCases` rely on "all domains"; they would likely fail on a trunk JDK (PLAUSIBLE, not run). JGDMS tests `classLoaderGrantWithoutAClassCoversEveryDomain` and `classLoaderGrantSerializesThroughItsBuilder` fail against DirtyChai's classes. | Decide whether void is intended. If not, restore "all domains". |
| D3 | Deserializing a DIGEST_DOMAIN grant: DirtyChai's `readResolve` no longer returns the null grant for DIGEST_DOMAIN and its template carries no loader, so `build()` throws `IllegalArgumentException("ClassLoader must not be null")` during deserialization. JGDMS returns the void grant. | Read from the code; NOT run on DirtyChai. | Return the null grant for DIGEST_DOMAIN in `readResolve`. |
| D4 | `ClassLoaderGrant.impliesEquivalent`: JGDMS compares loaders only; DirtyChai also requires equal principals and non-null loaders (stricter). `equals`/`hashCode` differ (JGDMS includes the domain reference by identity; DirtyChai compares loader, principals, permissions and, for digest grants, the sources). | Read from the code. | Decide which is intended. |
| D5 | DIGEST_DOMAIN rules: DirtyChai requires `DigestCodeSource` instances and compares signers IN ORDER; JGDMS requires `clazz()`, compares signers as a SET, and matches via `CodeSourceKey` including `isVerifiedDigest`. | Read from the code. | Decide whether signer order is part of identity. |
| D6 | `SubjectDomain` (live principals) is not visible to DirtyChai's bundled `PrincipalGrant`, which cannot see that app-loaded class, so JGDMS's live-principal support is lost on DirtyChai. | CONFIRMED by class-loading probe (`BuiltinClassLoader.loadClassOrNull` special-cases this package: java.base first, then the class path). | Move the `SubjectDomain` interface into java.base, or accept snapshot principals. |
| D7 | `java.security.Permission` in DirtyChai is not `Serializable` (`public abstract class Permission implements Guard`), so `PermissionGrantBuilderImp.writeObject` cannot serialise any ordinary JDK permission (for example `RuntimePermission`): `NotSerializableException`. | CONFIRMED by the maintainer's test run; the JGDMS test uses a test-local serialisable permission. | Decide whether this is intended; it limits what JGDMS grants can be serialised on DirtyChai. |
| D8 | `clazz()` state: DirtyChai resets domain, loader and `hasDomain` on every call; JGDMS keeps the previous state on `clazz(null)`. | CONFIRMED. DirtyChai behaviour is the better one (see J1). | JGDMS adopts DirtyChai's. |

## C. JGDMS-side items

| Id | Item | Evidence | Action |
|---|---|---|---|
| J1 | Builder reuse bug: `clazz(X).clazz(null)` leaves the grant bound to X. | CONFIRMED. | Port DirtyChai's reset; add a builder-reuse test. |
| J2 | `PrincipalGrant.toString` puts the comma after the last principal and none between (DirtyChai fixed this in `f8520cdaa`). Both forms parse back through JGDMS's lenient parser; only DirtyChai's is standard syntax. | CONFIRMED. | Port the fix, but see P2 (the comment text) first. |
| J3 | The superclass change in commits `b9cc0212d` (`ClassLoaderGrant` and `DigestDomainGrant` now extend `PrincipalGrant`) re-implemented `ProtectionDomainGrant`'s logic inside the two classes. It removed two API divergences but grew the source diff (`ClassLoaderGrant` from +55/-35 to +86/-36) and hid the semantic gap from the API-level test. | CONFIRMED. | Treat as interim; revisit when the DirtyChai items D1 to D5 are settled. |
| J4 | `jgdms-platform` compiles with `--release 21`, so DirtyChai-only APIs (`DigestCodeSource`, `CodeSource.getUriLocation()`, `SocketPermission.init()` and the `au.zeus.*` helpers) are invisible, which is why the JGDMS copies reach some of them by reflection. "Identical modulo imports" is therefore not reachable. | CONFIRMED. | A fixed, mechanical adaptation patch is the realistic residue. |
| J5 | `jgdms-platform`'s surefire run patches a hand-listed set of classes into `java.base` (`-Xbootclasspath/a` for four more). A new dependency of a patched class has to be added by hand, and a project path containing a space would break the argLine. | CONFIRMED (previous test work). | Keep the list in step with the shared classes. |
| J6 | Semantic differences between the two copies are not covered by any test. `BundledCopyConformanceTest` compares only public/protected API (empty allow list, 0 API divergences against the emulated trunk). | CONFIRMED. | Add an unpatched execution of the public-API semantic tests with a semantic allow list; add package-private, serial-form and cross-copy serialization checks and a pinned-commit sync script (recommended stages 1 to 3 below). |

## D. Questions the maintainer has to decide first

1. Is the void null-class CLASSLOADER grant in DirtyChai trunk intended (D2)?
2. Will `ClassLoaderGrant.implies(CodeSource, Principal[])` be fixed in DirtyChai (D1)?
3. Does signer order matter for digest identity (D5)?
4. `SubjectDomain` into java.base, or drop live principals (D6)?
5. Are stock-JDK and OSGi support kept for these classes?
6. What is the conclusion on P2 and P3, and how will P1 be rewritten?

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
