claude-sonnet-5-5
# Do user-Subject principals satisfy `principal SpiffePrincipal` grants? (read-only, 2026-11-01)

ANSWER: YES by reading. Policy matching sees one flat principal set per ProtectionDomain; worker and user principals are merged before matching and nothing marks which Subject a principal came from.
A user Subject holding SpiffePrincipal "spiffe://X" satisfies `principal SpiffePrincipal "spiffe://X"` grants, and the dispatcher's wire allowlist will build exactly that principal. Gate 1 (worker-only PD) is not affected.
Runtime effect not run; see "Could not confirm". DC = DirtyChai/src/java.base/share/classes, JG = JGDMS/JGDMS.

## 1. How principal grants match (READ)
- `PrincipalGrant.implies(Principal[])` DC/org/apache/river/api/security/PrincipalGrant.java:192-224: every grant principal must be present (resolved path `hasPrincipals.containsAll(pals)` :223-224; unresolved path `matches == pals.size()` :221). The array is `pd.getPrincipals()` (:262-264), i.e. one domain's flat set. No Subject boundary exists at this layer.
- ConcurrentPolicyFile.getP DC/au/zeus/jdk/authorization/policy/ConcurrentPolicyFile.java:~327-372 calls `ge.implies(pd)` per grant; no Subject logic.
- Where the set comes from: `AccessController.getContext()` DC/java/security/AccessController.java:967-980 calls `SubjectDomainCombiner.currentAll().combine(...)`. `currentAll()` (DC/javax/security/auth/SubjectDomainCombiner.java:~80-90) takes `Subject.currentAllNoCheck()`, i.e. the SCOPED_SUBJECT array (Subject.java:434-457).
- Constructor unions ALL Subjects' principals into one set (SubjectDomainCombiner.java:113-122). `combine` appends them to the PD's existing principals (:276-281, `pals = existing + merged`), producing a new DomainIdentity.
- The worker's SpiffePrincipal is already in the PD from class-load: SecureClassLoader.java:340-353 (`SpiffeCredentialManager...getSubject().getPrincipals()`). So the final set is worker UNION user Subjects. The class doc says so itself: "merged additively" (SubjectDomainCombiner.java:56-58).
- callAsAll is NOT nested doAs. JG/jgdms-platform/.../UserSubjectSupport.java:285-316 calls `Subject.callAs(Callable, UserSubject...)` reflectively (one call, array bound at once); single-Subject fallback otherwise. DC Subject.java:592-606 -> `callNoCheck` -> `ScopedValue.where(SCOPED_SUBJECT, subject[])` (:628). Dispatcher: BasicInvocationDispatcher.java:2201-2211.

## 2. Class comparison (READ)
- Resolved principals: `requiredPrincipal.equals(prs[i])` / Set.containsAll (PrincipalGrant.java:214, 223-224). The DC SpiffePrincipal.equals uses `instanceof SpiffePrincipal` + id equality (DC/au/zeus/jdk/authorization/spire/SpiffePrincipal.java, equals; class is `final`, so exact).
- Unresolved (class not loadable at parse time): `UnresolvedPrincipal.implies` org/apache/river/api/security/UnresolvedPrincipal.java:112-119 compares class NAME strings; wildcard `*` supported.
- DEFECT (READ, incidental): :114-118 is `WILDCARD.equals(klass) || (klass.equals(cls) && (nameWildcard || nameEq))`. `&&` binds tighter, so `principal * "anything"` matches every principal regardless of name. Probably unintended.
- Two different SpiffePrincipal classes exist: DC `au.zeus.jdk.authorization.spire.SpiffePrincipal` (ambient process worker) and JG `au.net.zeus.jgdms.spiffe.SpiffePrincipal` (jgdms-lib-dl, final, equals at :210-213). A grant names one class; it matches only principals of that class.

## 3. Can a SpiffePrincipal in a user Subject satisfy the grant? (READ)
- Wire path: `readUserSubjects` JG/jgdms-jeri/.../BasicInvocationDispatcher.java:2299-2345 reads class+name pairs chosen by the peer; `instantiatePrincipal` :2491-2495 uses allowlist :225-229, which INCLUDES `au.net.zeus.jgdms.spiffe.SpiffePrincipal`. It is placed in a `UserSubject` (:2339-2340) and bound at :2211. No check rejects SPIFFE (grep of that file: Spiffe appears only at :227,236,240,1614).
- The TLS layer attaches the worker's SpiffePrincipal using the SAME class: Utilities.spiffePrincipalsFromCertificate JG/jgdms-jeri/.../ssl/Utilities.java:282-312 (reflection on `au.net.zeus.jgdms.spiffe.SpiffePrincipal`, comment :289-293: "PrincipalGrant matching keys on runtime class name (and equals)"). Dispatcher Gate-1 PD built from client Subject :1614-1622.
- So a user Subject [SpiffePrincipal(Y)] has equal class and equal id to the worker's. After the merge in item 1 a grant `principal au.net.zeus.jgdms.spiffe.SpiffePrincipal "Y"` is satisfied. Peer P (valid SVID, own id Z) can send Y = any workload id.
- Grants naming the DC class (`au.zeus.jdk.authorization.spire.SpiffePrincipal`, e.g. docs/blog-post-4-authorization.md:201) are NOT reachable from the wire: that class is not in the allowlist, so it becomes RemotePrincipal (:2493,2618), which never equals it. But that class is reachable by any in-JVM code that can build a UserSubject and has AuthPermission callAs (Subject.java:592-606).
- Conjunctive grants (SpiffePrincipal AND JwtPrincipal) are weakened the same way: both can be supplied in user Subjects.

## 4. Anything distinguishing worker from user at match time? (READ - absent)
- A type split exists but only at binding: `Subject` is sealed to `WorkerSubject`/`UserSubject` (Subject.java:99); `callAs`/`doAs` reject WorkerSubject (:556-559, 726-731, 776-781, 842-847, 931-936); `UserSubject` is only a type (UserSubject.java, 4 members).
- It is erased before matching: the combiner copies `getPrincipals()` into a bare `Principal[]` (SubjectDomainCombiner.java:116-122, 276-281) and `DomainIdentity`/ProtectionDomain carry only `Principal[]`. No marker, read-only flag, or credential is consulted.
- Proof of absence: `grep -rlE "UserSubject|WorkerSubject" --include=*.java DC` over 3220 .java files returned 8: HttpsClientAuthPolicyParser (:107 argument guard), SpiffePolicyFile (:43 comment), SpiffeCredentialManager, AccessController (import only), RemoteSubject, Subject, UserSubject, WorkerSubject. NONE of SubjectDomainCombiner, ProtectionDomain, PrincipalGrant, ConcurrentPolicyFile, UnresolvedPrincipal.
- JGDMS ConcurrentPolicyFile (jgdms-platform/.../ConcurrentPolicyFile.java:344,372,435,461) also calls `ge.implies(pd)` on the same package's grant classes (INFERRED same PrincipalGrant logic; the diff I ran was misaligned, not a proof).

## 5. Smallest mitigations (describe only)
1. Dispatcher (JGDMS, other owner): reject or downgrade to RemotePrincipal any SpiffePrincipal (configurable class set) in `readUserSubjects`; remove :227 from the allowlist, or accept only when its id equals the authenticated peer's. Smallest and sufficient for the wire path.
2. Distinct user identity class (e.g. a non-SPIFFE `UserPrincipal` wrapper) so grants for workloads and users can never share a class.
3. DirtyChai combiner (human): when merging SCOPED_SUBJECT subjects, drop (or throw on) principals whose class is in a protected set (SpiffePrincipal both classes). Or tag principals by origin and require worker-origin in `PrincipalGrant` for protected classes.
4. Parser rule: forbid `principal *` wildcard class, and fix the :114-118 precedence.

## Could not confirm by reading
- That a built DirtyChai JVM actually yields a merged set (e.g. DomainIdentity is what ProtectionDomain.implies consults). Smallest Zone T test, test/jdk/ai/: install CombinerSecurityManager with a policy granting a marker permission to `principal <SpiffePrincipal> "spiffe://t/worker"` only; run `Subject.callAs(action, new UserSubject(true, Set.of(new SpiffePrincipal(that id)), ...))` and assert the check passes (documents the bug); the load-bearing twin asserts the same call is DENIED once mitigation 3 exists.
- Dispatcher end-to-end: a real mTLS peer sending a SpiffePrincipal in the user block (needs a JGDMS integration test; owner is the JGDMS side).
- Whether any deployed policy uses the unresolved/wildcard path (INFERRED from the parser, not traced).
