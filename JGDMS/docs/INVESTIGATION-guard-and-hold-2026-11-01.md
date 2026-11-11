claude-sonnet-5-5
# Static Guard for peer principal assertion, and AssertPrincipalPermission in HOLD (2026-11-01)

Worktree `...\JGDMS\.claude\worktrees\agent-ae0e713f14113e116`, detached at 34b490940, uncommitted. Patch: `SP\guard-hold.patch`, SP = `C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\guard1`. Report is in the worktree (the patch carries it).
Maven: `mvn -o -pl jgdms-platform,jgdms-der,jgdms-jeri,tools/policy-condenser test -Dtest=<classes> -Dsurefire.failIfNoSpecifiedTests=false`; never `install`.

## Changes per file
- `BasicInvocationDispatcher`: `peerMayAssert` no longer touches `Policy`. New `private static final Guard PEER_ASSERT_GUARD` (anonymous `java.security.Guard`) calls `sm.checkPermission(AssertPrincipalPermission, peerContext)`; the context is the Gate-1 one (`Security.create` of the peer's ProtectionDomain), passed in a small `PeerAssertion` holder. With `System.getSecurityManager() == null` it returns false before the Guard (a Guard would pass silently), so only JWT-derived principals survive and any other wire principal refuses the call. Javadoc and a SECURITY comment say so. `import java.security.Policy` replaced by `Guard`.
- `PermissionSets.holdReason`: class-level HOLD for `net.jini.security.AssertPrincipalPermission` (any name/actions). `danger()` skips that hold for this class so the lint catalogue is unchanged (exact grant = clean, wildcard/prefix = HIGH via `extraDanger`; existing `testCatalogueGaps` still passes).
- Tests: `PeerPolicyFixture` (installs a plain SecurityManager if none, counts `Policy.implies` calls and those whose caller is the dispatcher; `withoutSecurityManager` variant; after it installs an SM, `close()` restores a permissive policy so other classes in the JVM are not restricted); `JwtPrincipalBindingTest` +2 (SM path, and a child-JVM test whose `main` runs with no SM); platform `PermissionSetsTest` (HOLD assertions); tools `PermissionSetsTest` +1, `ProxyPolicyGeneratorTest` (hold list +1), `ListPromotionTest` (HOLD_ENTRIES +1).

## Evidence (after)
jeri/platform: JwtPrincipalBindingTest 26, BasicInvocationDispatcherMultiSubjectTest 5, JwtDispatcherFailClosedTest 15, MultiSubjectWireProtocolTest 12: `Failures: 0`; also with `-Pdirtychai`: same, BUILD SUCCESS. Tools/platform: PermissionSetsTest 12 (platform) and 2 (tools), ProxyPolicyGeneratorTest 45, ListPromotionTest 46, PolicyLintTest 49 (2 skipped, as before), PolicyLintCorpusTest 2: 0 failures. Whole `net.jini.jeri.**` suite: 301 run, 1 failure (SslServerSharedBudgetTest); pristine trunk 299 run, 1 failure (the same class, a timing test).

## Fail-first (original main code, new tests; private git-archive copy `xf`)
| test | on unmodified main |
|---|---|
| `withoutASecurityManagerOnlyTokenDerivedPrincipalsAreAccepted` (child JVM) | FAILS: "no SecurityManager, X500, permissive policy: was accepted" |
| platform `PermissionSetsTest.testHoldIsByClassAndName` | FAILS (line 110) |
| tools `PermissionSetsTest.testAssertPrincipalPermissionIsHeld` | FAILS |
| `ProxyPolicyGeneratorTest.testEscalationEntriesAreHeldBackByClassAndName` | FAILS: expected 15 but was 14 (entry not held) |
| `ListPromotionTest.testALiveHoldEntryInAnEditedList...` | FAILS: held 0 for the AssertPrincipalPermission entry |
| `thePolicyIsConsultedOnlyByTheSecurityManager` and the peer with/without permission tests | PASS on old code: with an SM installed the old code already went through the SM; they pin the behaviour, the no-SM test is what separates old from new |

## Mutants (hand, one at a time, on `x`; restore byte-compared, `restored=True` each)
| id | mutant | result |
|---|---|---|
| M1 | no-SM branch asks `Policy.getPolicy().implies` directly again | KILLED: child-JVM test fails (JwtPrincipalBindingTest 26 run, 1 failure) |
| M2 | no-SM branch returns true | KILLED: same test |
| M3 | AssertPrincipalPermission removed from HOLD | KILLED: 4 failures (platform PermissionSetsTest, tools PermissionSetsTest, ProxyPolicyGeneratorTest, ListPromotionTest) |
| M4 | Guard always passes | KILLED: 9 JwtPrincipalBindingTest failures |
Output: `SP\mutants.txt`; driver `SP\mutate.py`.

## Not verified
- No end-to-end JERI call; no Linux; full platform suite not run.
- Under a stock JDK 24+ (no SM) the SM-fixture tests would fail at `setSecurityManager`; DirtyChai only.
- The child-JVM test relies on `java.class.path` of the surefire fork (works here with the manifest jar).

## Questions
1. A client that never has an SM (stock JDK) can now not assert any non-JWT user principal at all; intended per the ruling?
2. `JwtPrincipalBindingTest` now contains a `main`; acceptable, or move it to a helper class?
