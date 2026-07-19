# Scope of Work — Remaining DER-migration / ProxyTrust-removal follow-ups

*Draft SOW — 2026-07-18, updated 2026-07-19. Residual items from the 2026-07-17/18
DER-migration and ProxyTrust-removal session (task tracker #14–#28). Originally six
independent items; as of 2026-07-19, item 1 (task #16) is PARKED and item 3 (task #20) is
DONE — see their entries below for outcome/detail. **Four items remain open**: item 2
(blocked on Peter's decision, not an agent task on its own), item 4 (test coverage gap,
not started), item 5 (test KDC, explicitly low priority), item 6 (ProxyTrust Stage 2,
largest, not started). No cross-dependencies among the open items except where noted. Each
should go through the same discipline as the work that produced this list: isolated
`git worktree`, fresh agent to implement, a separately-dispatched agent for adversarial
review before merge — do not merge any of these to trunk without review.*

## Items

1. ~~**Live `GETTING_STARTED.md` verification of tightened hello-world/archetype policies**~~
   *(task #16, PARKED 2026-07-19 per Peter's call — hello-world is example-only, lower priority
   than other work; not pursuing further live-round-trip effort here.)*
   Commit `9ac2fda97` (merged `b2dca4262`) replaced `AllPermission` grants to the Reggie `-dl` jar
   with the same minimal set `deploy/policy/host*.policy` already uses. A live-verification pass
   confirmed the tightened grant is genuinely insufficient for a live plain-TCP run (missing a
   `SocketPermission` for the registrar's remote callback plus several infra permissions,
   surfaced by `polpAudit`), but there's no production line to copy: production's socket grant
   comes from authenticated `SpiffePrincipal` grants that don't exist in the unauthenticated
   tutorial. Correct fix if this is revisited: regenerate the policy with `polpAudit` once a
   round trip can actually complete — not a hand-added permission. **The round trip itself never
   completed**, blocked by two bugs unrelated to policy or to hello-world specifically, surfaced
   as a side effect and left unfixed here (out of this task's scope, flagged separately, not
   confirmed as prioritized):
   - `JoinManagerImpl.getConfig` (`jgdms-lib/.../discovery/JoinManagerImpl.java:2656-2657`,
     verified directly against source) has a real De Morgan logic bug — `!(A) || B` where the
     error message implies the intended check was `!(A || B)` — so it throws
     `IllegalArgumentException("serviceProxy must be @AtomicSerial or an instance of
     java.lang.reflect.Proxy")` for **every** `java.lang.reflect.Proxy`-shaped service
     unconditionally, not just ones failing the stated requirement. Not hello-world-specific:
     blocks registration for any Dynamic-shape service in the reactor. Worth its own look
     independent of this task's fate.
   - A client-side `ServiceDiscoveryManager` NPE ("proxy is null") during event registration,
     unconfirmed whether it's the same root cause as the already-tracked
     [[jgdms-discovery-notserializable-proxy]] or a distinct manifestation.
   - Minor, genuinely example-only: tutorial configs use invalid `new T[0]` array-literal syntax
     (should be `new T[]{}`), and the documented classpath is missing the
     service-proxy-annotations jar.
   No code committed; worktree left clean. See
   [[jgdms-hello-world-policy-tightening-unverified-live]] for the full trace.

2. **Decide the `BoomerangProxyHandler` / constrained `ProxyPreparer` regression risk** *(task #18,
   decision-then-maybe-fix, blocked on Peter's call, not an agent task on its own).*
   Finding from the `BoomerangProxyHandler` follow-up audit (task #17,
   [[jgdms-der-proxy-tolerant-interfaces]]): both `BasicInvocationHandler.
   invokeRemoteMethodControlMethod` and `AtomicDerInvocationHandler` guard `setConstraints()` with
   `Proxy.getInvocationHandler(proxy) != this`. A `@RemoteFunction` dynamic listener/callback proxy
   that got `BoomerangProxyHandler`-wrapped during decode (because ≥1 declared interface didn't
   resolve locally) will always fail this identity check when handed to a `ProxyPreparer` configured
   with explicit method constraints — throwing `IllegalArgumentException`, loudly, not a silent
   security bypass, but a real functional/availability regression for that class of proxy.
   **This needs a decision before any code changes**: is fail-closed-with-a-loud-exception acceptable
   as the permanent behavior (the affected case — a proxy with some interfaces you couldn't even
   resolve, being handed to code that wants to tighten its constraints — may be rare/edge enough not
   to fix), or should `prepareProxy`-adjacent code unwrap a `BoomerangProxyHandler` to the real
   handler before calling `setConstraints()`. If the latter, scope is small (a one-method unwrap
   helper plus call-site updates in `ProxyPreparer`/`BasicProxyPreparer`) but touches
   constraint-enforcement code, so still needs adversarial review regardless of size.
   Does not affect the already-merged `BoomerangProxyHandler` commit itself.

3. ~~**Diagnose and fix the Linux UDS socket-permission test bug**~~ *(task #20, DONE — MERGED
   trunk `01f9e6a8d`, 2026-07-18.)*
   Root cause: `UdsServerEndpoint.restrictPermissions` used a `NOFOLLOW` `PosixFileAttributeView`
   for `setPermissions`, which the JDK implements via `open()`+`fchmod()` — but an `AF_UNIX` socket
   special file can't be `open()`ed (kernel `ENXIO`), so this failed on every Linux for any real
   bound socket, not just this box. Fixed by chmod-ing via the follow-links (path-based `chmod(2)`)
   view instead, bracketed with NOFOLLOW/lstat pre/post checks (same socket inode, not a symlink,
   exact `rwx------` read back) to preserve the anti-symlink property the NOFOLLOW view was meant
   to provide. Adversarial review caught one real (LOW-severity) gap in the first pass —
   `validateParentDirectory` rejected world-writable-non-sticky parents but not
   group-writable-non-sticky ones, leaving a narrow TOCTOU window to redirect the chmod onto a
   server-owned file via symlink race (bounded: can't escalate privilege or disclose, only
   tighten/corrupt a file the server's own uid owns) — closed in a follow-up commit, re-reviewed
   clean. Both suites re-enabled and green under DirtyChai with SecurityManager active:
   `UdsEndpointRoundTripTest` 17/17, `UdsConstraintEnforcementTest` 7/7. **Known accepted
   trade-off, not a bug**: this is a backward-incompatible tightening with no opt-out — a parent
   directory that was group-writable-non-sticky for unrelated reasons (e.g. a shared
   `RuntimeDirectory`) will now fail to bind; remediation is `chmod +t` on that directory or use an
   owner-private one. Worth a release-note mention on next release. **Not in scope, still
   outstanding**: the cross-JVM interprocess UDS isolation test
   (`docs/SOW-Unix-Domain-Socket-JERI-Transport.md` build-plan item 5) — this fix was a
   prerequisite in spirit, not a substitute. See [[jgdms-uds-linux-socket-permission-bug]].

4. **Add test coverage for the 19 `AtomicMarshalledInstance`→DER write sites** *(task #23, gap
   confirmed real and pre-existing, not introduced by this migration).*
   Commit `221fe251c` converted 19 persistence write sites (mercury/norm/fiddler/mahalo/outrigger/
   reggie) to DER encoding with `IllegalStateException` catch-widening on the read-side recovery
   loops. Confirmed during that change's review: zero test coverage exists for 4 of the 6 affected
   service modules (`mercury-service`, `norm-service`, `fiddler-service`, `reggie-service` have no
   test sources at all) — the manual round-trip probes done during implementation are the only
   verification that has ever existed for this code, and they are not part of the committed suite.
   Scope: for each of the 19 sites (see [[jgdms-atomicmarshalledinstance-der-mislabel]] for the full
   file/line list), a test that round-trips a real persisted object through the new DER encoding and
   confirms both (a) successful decode by a DER-capable reader and (b) the widened
   `IllegalStateException` catch actually triggers gracefully (not just compiles) when a legacy
   JOSS-only reader encounters DER data it can't handle, for the modules where that recovery path
   exists. Given 4 of 6 modules have no test infrastructure at all yet, expect this to include
   standing up a minimal test tree for each, not just adding test methods to an existing one — size
   this accordingly, likely one sub-task per service module rather than one bundled change.

5. **Set up a test KDC to validate Kerberos transport end-to-end** *(task #24, LOW PRIORITY per
   Peter's explicit instruction).*
   The kerberos/https `ProxyTrustILFactory`→`AtomicILFactory` migration (task #26, merged
   `66e236fb0`) was verified via static config type-checking and interface-inheritance proof, not a
   live Kerberos handshake — `KerberosEndpoint`/`KerberosServerEndpoint` have never been exercised
   against a real KDC in this project's QA harness as far as this session's audits found. Scope:
   stand up a test KDC (MIT krb5 or similar, containerized preferred for repeatability), configure
   the `qa/harness/configs/kerberos/*` service configs against it, run the harness end-to-end for at
   least one service (reggie is the natural first candidate, already the most-audited config this
   session) and confirm a real GSS-API handshake + authenticated RMI call succeeds. Explicitly low
   priority — do not schedule ahead of items 1–4 or the ProxyTrust Stage 2 removal below unless
   asked.

6. **ProxyTrust Stage 2: remove the reflective trust-verification mechanism** *(not yet a formal
   task tracker entry — create one before starting. Unblocked, not yet scheduled. Largest item on
   this list.)*
   Stage 1 (dead-code removal: `ProxyTrustExporter`, `ProxyTrustInvocationHandler`, 56 files,
   merged `bfd5e8660`) and the kerberos/https decoupling (merged `66e236fb0`) together removed the
   only two things blocking this in every service's main harness config: every non-deprecated
   `qa/harness/configs/{der,jsse,spiffe,kerberos,https}/*` service config now exports via
   `AtomicILFactory`/`BasicILFactory`/`DynamicILFactory`, never `ProxyTrustILFactory`. That is
   narrower than "zero live production consumers" — see the new sub-bullet below for 5 QA/jtreg
   spec-test config files the kerberos/https migration didn't cover, which still construct
   `ProxyTrustILFactory`/`SystemAccessProxyTrustILFactory` directly. Remaining scope, precisely
   enumerated in [[jgdms-proxytrust-removal-scoping]]:
   - Remove `getProxyTrustIterator()` from the 26 proxy classes across fiddler-dl (4), mahalo-dl
     (2), mercury-dl (4), norm-dl (3), outrigger-dl (3), reggie-dl (5), jgdms-lib-dl (4),
     phoenix-dl (1), plus `ActivatableInvocationHandler`.
   - Remove `ProxyTrustVerifier`, `ProxyTrustIterator`, `SingletonProxyTrustIterator`,
     `ProxyTrust`, `ServerProxyTrust` (`net.jini.security.proxytrust.*`).
   - `LeaseBackEnd extends ProxyTrust` (qa) must be updated first or in the same change — deleting
     `ProxyTrust` breaks its compile. **Note there are two distinct, same-named files, easy to
     conflate**: `qa/src/org/apache/river/test/impl/norm/LeaseBackEnd.java` was already fixed by
     the already-merged task #28 (`ebe476e75`, [[jgdms-leasebackend-proxytrust-removal]]) — it now
     `extends Remote` only, its test-double `check()` methods de-guarded of the `instanceof
     ProxyTrust` check. But the broader, more widely-used
     `qa/src/org/apache/river/test/share/LeaseBackEnd.java` — backing `AbstractTestLeaseFactory`,
     `TestLeaseProvider`, and `LeaseBackEndImpl` in that same package, used well beyond norm's
     tests — still reads `extends Remote, ProxyTrust` as of trunk `426776c82` and needs the
     identical fix task #28 applied to the norm copy, just scoped wider. Confirmed the same full
     pattern is present here too, unfixed: `LeaseBackEndImpl.java` `implements LeaseBackEnd,
     ServerProxyTrust` with its own `getProxyVerifier()`/`VerifierImpl`; three `IteratorImpl
     implements ProxyTrustIterator` / `getProxyTrustIterator()` pairs in
     `ConstrainableTestLease.java`, `TestLeaseMap.java`, and `ConstrainableUnreadableTestLease.java`
     (this trio also carries a `ProxyTrust`-typed field/constructor in each `IteratorImpl`, same as
     the already-fixed norm versions); plus one more `getProxyTrustIterator()` in
     `TesterTransactionManagerConstrainableProxy.java`, an unrelated proxy class in the same
     package, not part of the lease chain but the same mechanical removal. Unlike the norm fix,
     `ConstrainableTestLease`/`TestLeaseMap` here did not turn up an `instanceof ProxyTrust` guard
     in this pass — confirm during implementation rather than assume none exists.
   - **New gap, not previously enumerated**: 5 QA/jtreg config and spec-test files, outside the
     kerberos/https harness-config migration's scope (which only touched each of the 8 services'
     main `qa/harness/configs/{kerberos,https}/*.config`), still directly construct
     `ProxyTrustILFactory`/`SystemAccessProxyTrustILFactory` — 8 live sites total:
     `qa/jtreg/net/jini/activation/ActivationSystem/accessControl/rmid.config` (4: systemExporter,
     monitorExporter, activatorExporter, instantiatorExporter — mixing
     `SystemAccessProxyTrustILFactory` and `ProxyTrustILFactory`),
     `qa/src/org/apache/river/test/spec/eventmailbox/configs/{kerberos,https}/testlistener.config`
     (1 each), and
     `qa/src/org/apache/river/test/services/lookupsimulator/configs/{kerberos,https}/lookupSimulator.config`
     (1 each). Stage 2 cannot delete `ProxyTrustILFactory` without either migrating these 5 files
     to `AtomicILFactory` first (same pattern as `66e236fb0`) or making an explicit, documented
     decision to retire/leave them — otherwise these configs fail to compile/load the moment the
     class is removed. (Ruled out as a false alarm, not part of this gap:
     `JGDMS/extra/.../ExportHelper.java`'s `ProxyTrustILFactory` reference is commented-out dead
     code next to the already-adopted `AtomicILFactory` replacement — trivial cleanup only, not a
     live site.)
   - Remove the `net.jini.security.TrustVerifier` SPI registration line for `ProxyTrustVerifier`
     from both `META-INF/services/` files (`jgdms-platform`, `jgdms-resources`) — easy to miss, an
     orphaned SPI line without the class breaks `ServiceLoader` resolution.
   - Retire the QA spec suites `proxytrustverifier`, `singletonproxytrustiterator`,
     `jeri/proxytrustilfactory` (Stage-1-protected, must stay working until Stage 2 actually
     removes their targets).
   - `AbstractSmartProxy`/`AdminProxy` (`jgdms-lib-dl`) need an **explicit decision, not silent
     deletion** — they're the current base classes for the STD-009 service-proxy-processor
     code-generation path (`RemotePolicyServiceProxy` and the archetype scaffold both use them
     today), so removing their `ProxyTrust`-related surface affects newly-generated code, not just
     legacy services.
   - Pre-flight check before starting: confirm no in-repo config actually ships
     `useInsecureLookup=true` relying on this reflective chain as its only trust path (default is
     `false`; not fully audited this session, only spot-checked).
   - Not part of this removal: `BasicProxyTrustVerifier` (either the live `jgdms-lib-dl` copy or
     the deprecated `jini-2.1-compat` shim) and `SafeServiceRegistrar` — both orthogonal, already
     correct, do not touch.
   Given the size (26 proxy classes across 8 services plus the core interfaces plus 3 QA spec
   suites plus a template-class decision), this should be split into its own sub-scoped tasks
   rather than one single change — follow the same staged pattern (scope → implement → adversarial
   review → merge) used for Stage 1, per module or per logical group, not as one giant diff.

## Sequencing notes

Items 1–5 are fully independent of each other and of item 6; any can be picked up in any order.
Item 6 has one real internal dependency (the `LeaseBackEnd.java extends ProxyTrust` clause) but is
otherwise also independent of items 1–5. None of these block each other — parallelize freely across
worktrees/agents. Item 5 (test KDC) is explicitly the lowest priority of the six per Peter's own
sequencing signal; do not schedule it ahead of the others without being asked.

## Constraint

Every item that touches production, wire-format, or persistence code (all except item 1, which is
verification-only and item 5's harness/config work) must go through adversarial review by a
separately-dispatched agent before merging to trunk, per this project's standing review discipline.
Use isolated `git worktree` copies, never the shared main trunk worktree, for any mutating work.
Archive-tag and delete branches on merge per the project's branch-archival convention.
