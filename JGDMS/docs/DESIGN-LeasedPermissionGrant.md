# DESIGN — `LeasedPermissionGrant`

*Phase 3 design artefact for `SOW-AI-Agent-Authority-Support.md`. Filed 2026-06-15.
Read-only design pass: NO Java source modified, NO build/test run. Architectural
decisions noted below are settled per the SOW and are not re-litigated here.*

**Companion documents:**
- SOW: `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\docs\SOW-AI-Agent-Authority-Support.md` (§3 Phase 3)
- Review: `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\docs\agent-authority-code-review-2026-06-14.md` (Q-A)
- Memory: `C:\Users\peter\.claude\projects\E--Claude\memory\jgdms-ai-agent-authority.md`

**Settled decisions (do not reopen):**
- Primitive is a decorator implementing `org.apache.river.api.security.PermissionGrant`,
  using the existing decorator seam in `PermissionGrant` (constructor
  `PermissionGrant(PermissionGrant decorated)` and the `decorated()` accessor —
  verified at lines 194 and 241 of
  `jgdms-platform/src/main/java/org/apache/river/api/security/PermissionGrant.java`).
- Install path is `DynamicPolicy` via `DynamicPolicyProvider.grant(PermissionGrant)`
  (verified at line 684 of
  `jgdms-platform/src/main/java/net/jini/security/policy/DynamicPolicyProvider.java`);
  the existing `GrantPermission` ceiling check at line 686-687 enforces the cap.
  `RemotePolicy` is out of scope for this build.
- Lease abstraction is `net.jini.core.lease.Lease` (verified at
  `jgdms-platform/src/main/java/net/jini/core/lease/Lease.java`). Renewal is the
  caller's responsibility — this primitive only *observes* `getExpiration()`.

---

## 0. As-built note (2026-06-16)

The primitive was implemented and verified (`mvn -o -pl jgdms-platform clean test`
on a **vanilla JDK 21** — see the build note below; full platform suite green,
13 new unit tests). Three points where the as-built code differs from this design
document, recorded here so the doc and the code agree:

1. **Builder integration: Option B was chosen, NOT the Option A recommended in
   §2.2.** `PermissionGrantBuilder` is one of the JGDMS classes embedded in the
   DirtyChai JDK's `java.base`. Adding a `lease(Lease)` method to it would *not
   resolve at runtime* under DirtyChai (the `java.base` copy, without the method,
   shadows the rebuilt platform jar — confirmed by a `NoSuchMethodError` during
   testing) unless `java.base` itself is rebuilt. A new decorator class has no
   such problem. So the build is a **single new class plus its test**, no change
   to `PermissionGrantBuilder` / `PermissionGrantBuilderImp`. Callers wrap
   manually: `new LeasedPermissionGrant(builder.build(), lease)`.
   `getBuilderTemplate()` simply delegates to the wrapped grant's template
   (identical to `ExternallyVoidablePermissionGrant`); the Option-B objection in
   §2.2 about the serialization-proxy hook is moot because the class is not
   `Serializable` (§1.9).

2. **`impliesEquivalent` is conservative, not a plain delegate.** It returns true
   only for another `LeasedPermissionGrant` with the *same* `Lease`. A plain
   delegate (as the §2.1 skeleton implies) would let external permission
   consolidation treat a leased grant as equivalent to its undecorated wrapped
   grant and merge its permissions into an unleased one — stripping the lease, a
   widening path contrary to the monotone-attenuation requirement (SOW §5).

3. **`@since` is `3.1.1`, not `4.0.0`.** Trunk `jgdms-platform` is
   `3.1.1-SNAPSHOT`; the `4.0.0` in §2.1 was aspirational.

**Build note:** because DirtyChai's `java.base` embeds `org.apache.river.api.security`
and `net.jini.*`, the platform module must be compiled/tested with a *vanilla*
JDK 21 (e.g. `JAVA_HOME=C:\Program Files\Zulu\jdk-21`). Under DirtyChai +
`--release 21`, javac silently emits the `java.base`-embedded copy of any edited
platform class, dropping added members.

---

## 1. Design note

### 1.1 Lease semantics — when does `isVoid()` flip true?

`net.jini.core.lease.Lease.getExpiration()` is specified (line 69-77 of
`Lease.java`) as *"a long that indicates the time that the lease will expire …
represented as milliseconds from the beginning of the epoch, **relative to the
local clock**"*. The decorator's contract is therefore the simplest, strictest
local-clock rule:

> Convention: the grant is **void** when `clock.millis() >= lease.getExpiration()`
> (using `>=`, not `>`). Boundary chosen for fail-secure: at the exact expiry
> millisecond, the grant is treated as already past it. The off-by-one favours
> attenuation over availability.

A grant becomes void *immediately* on that condition — there is no grace window,
no rounding, no sweeper dependency for *enforcement*. The sweeper (§1.6) is
purely a cleanup mechanism, not the gate that makes a grant void: any call to
`isVoid()`, `implies(...)`, or any other decorator method observes the local
clock at the moment of the call.

### 1.2 Clock skew

`Lease.getExpiration()` returns local-clock millis. If the lease was issued by a
remote landlord with a clock skewed relative to the holder, this decorator
**does nothing to compensate**. The contract is:

> The lease grantor MUST account for any clock skew in the expiry value it
> issues. The holder trusts its own local clock. Skew compensation is a landlord
> responsibility, not a decorator responsibility.

Implications:
- Short-TTL leases (seconds) become unsafe across hosts with un-synchronised
  clocks: a 5 s lease issued by a landlord 30 s ahead of the holder is already
  expired on arrival. Deployments using leased delegation should require NTP
  (or equivalent) synchronisation between landlord and holder, and SHOULD use
  TTLs significantly larger than the worst-case observed skew.
- This is a *deliberate* simplification: trying to track skew inside the
  decorator would require a separate clock-sync protocol and would complicate
  the security analysis (a faulty skew estimate could *extend* authority past
  true expiry — a widening path). Trusting the local clock is fail-secure under
  skew.

### 1.3 Renewal

Renewal is **entirely external to this decorator**. The caller (whoever holds
the renewal credential / `Lease` reference) is responsible for calling
`Lease.renew(long)` before expiry. Per `Lease.java` line 95-101, *"If the
renewal is granted this is reflected in value returned by `getExpiration`"* — so
after a successful renewal, the next `isVoid()` call simply re-reads the
expiration and observes the new (later) value. The decorator holds no
heartbeat thread, no scheduled callback, no internal expiry cache — it
re-queries `lease.getExpiration()` on every check.

The wrapper is **passive about renewal**. It cannot itself attempt renewal
(it has no credential), and it does not need notification of renewal — the
`Lease` object is the source of truth.

### 1.4 Cancellation

`Lease.cancel()` (line 79-93 of `Lease.java`) is specified as *"the overall
effect of a cancel call is the same as lease expiration, but instead of
happening at the end of a pre-agreed duration it happens immediately"*. The
specification does **not** state that `getExpiration()` is updated to a
sentinel value after cancellation, nor that `cancel()` is observable through
any `Lease` getter. Standard `Lease` implementations in JGDMS treat `cancel`
as a one-way notification to the landlord.

Therefore: the decorator **cannot reliably detect remote-side cancellation**
via the `Lease` interface alone. Two consequences follow:

1. The decorator MUST expose its **own** `cancel()` method that flips a
   `volatile boolean cancelled` (visible to all `isVoid()` readers without
   locking). The agent-authority layer (or any party with the right capability)
   calls this when it wants immediate revocation.
2. `LeasedPermissionGrant.cancel()` is a *local* void operation only — it does
   NOT call `lease.cancel()`. Whether to also notify the landlord is the
   *caller's* policy decision (e.g. the agent-authority layer may choose to
   call both; a unit test may cancel only locally).

Once `cancelled == true`, `isVoid()` returns true forever — cancellation is
monotone (cannot be undone). This is the dead-man-switch property the SOW
relies on (§1, §5).

### 1.5 Composition with existing void conditions

The wrapped `PermissionGrant` may already be void for its own reasons:
- `PrincipalGrant.isVoid()` (line 313-316 of `PrincipalGrant.java`) returns
  true if permissions are empty.
- `ProtectionDomainGrant.isVoid()` (line 191-196 of `ProtectionDomainGrant.java`)
  returns true if `super.isVoid()` OR the `WeakReference<ProtectionDomain>` has
  cleared (GC).

The decorator's `isVoid()` MUST short-circuit `true` if **any** of:
1. The wrapped grant's `isVoid()` returns true.
2. The local `cancelled` flag is set.
3. `clock.millis() >= lease.getExpiration()`.

In symbols: `void_decorator == void_wrapped || cancelled || expired`.

This preserves GC-scoping (claim 1 of the review): GC of the wrapped proxy's
ProtectionDomain still voids the grant even while the lease is live. The
SOW (§3) calls out that the decorator provides **two independent dead-man
switches** — GC + lease — and this composition is exactly what realises that.

### 1.6 Sweeper interaction

`DynamicPolicyProvider.createSweeper()` (line 355-392 of
`DynamicPolicyProvider.java`) runs `sweepVoidGrants()` (line 403-422) on a
60-second fixed-delay schedule. `sweepVoidGrants` iterates
`dynamicPolicyGrants`, calls `pg.isVoid()` on each (line 409), and removes
voided ones (line 410).

**No sweeper changes are required.** Leased grants integrate transparently:
- *Enforcement* of expiry is **immediate**: any `implies(...)` call between
  sweeps will observe `isVoid() == true` (the sweeper is not the gate).
  Specifically, `getGrants()` (line 643) and `getPermissionGrants()` (line 677)
  filter through `implies()`, which is delegated to the wrapped grant — see
  §1.10 for the implication chain.
- *Cleanup* (removal from the live set) happens at the next sweep cycle,
  bounded by 60 s. The grant continues to be reported by `isVoid() == true` in
  the interim; no permission is granted on the basis of an expired lease.

The expected steady state: an expired LeasedPermissionGrant sits in the active
set returning `true` from `isVoid()` until the next sweep evicts it. Memory
overhead is bounded by `sweep_period × max_grant_install_rate`.

### 1.7 `GrantPermission` ceiling

`DynamicPolicyProvider.grant(PermissionGrant)` (line 684-688 of
`DynamicPolicyProvider.java`) reads:

```java
public boolean grant(PermissionGrant p) {
    Collection<Permission> perms = p.getPermissions();
    GrantPermission guard = new GrantPermission(perms.toArray(new Permission [perms.size()]));
    guard.checkGuard(null);
    return dynamicPolicyGrants.add(p);
}
```

The ceiling is enforced via `p.getPermissions()`. `PermissionGrant.getPermissions()`
(line 316-319 of `PermissionGrant.java`) delegates to the decorated grant when
`decorated != null`:

```java
public final Collection<Permission> getPermissions(){
    if (decorated != null) return decorated().getPermissions();
    return perms;
}
```

Because `getPermissions()` is `final` and delegates to the wrapped grant, **a
`LeasedPermissionGrant` cannot conceal or expand the permission set its wrapped
grant carries**. The `GrantPermission` guard sees the same permissions it
would have seen for an undecorated grant of the same content. Wrapping a
`PrincipalGrant` carrying `[FilePermission "/scope/-", "read"]` in a
`LeasedPermissionGrant` requires the installer to hold
`GrantPermission(FilePermission "/scope/-", "read")` — identical to installing
the bare `PrincipalGrant`.

**Documented invariant:** the lease wrapper does not bypass, narrow, or widen
the `GrantPermission` ceiling.

### 1.8 Composition with the conjunctive principal model

The wrapped `PermissionGrant` retains *full* responsibility for principal
matching. `LeasedPermissionGrant.implies(...)` delegates the underlying check
to the wrapped grant; it adds only an `&& !isLeaseVoid()` conjunction. For a
wrapped `PrincipalGrant` requiring principals `{A, B}`:

- `PrincipalGrant.implies(Principal[]):203` continues to evaluate
  `containsAll` (line 235) / `matches == pals.size()` (line 232).
- The decorator does NOT pre-filter principals, does NOT relax the all-present
  conjunction, and does NOT widen the principal set.
- A lease wrapping a multi-principal grant is **strictly more restrictive**
  than the wrapped grant alone (adds a time bound on top of the existing
  conjunction), never less restrictive. Monotone attenuation, as required by
  SOW §5.

### 1.9 Equality / `hashCode` / serialization

Two `LeasedPermissionGrant`s wrapping the *same* wrapped grant with *different*
`Lease` instances are **distinct** — the lease IS part of identity, not just
state. Rationale: two leases with different expiries are not interchangeable;
removing one from the active set must not remove the other.

- `equals(o)`: `o` is a `LeasedPermissionGrant`, AND wrapped grants are equal,
  AND `this.lease == o.lease` (identity equality on lease — Lease instances are
  unique handles to landlord state, equals/hashCode on Lease implementations
  is not contract-mandated).
- `hashCode()`: combine wrapped grant's hash with `System.identityHashCode(lease)`.
- **Serialization:** `PermissionGrant` is non-serializable by contract
  (`PermissionGrant.java` line 46-47). Subclasses that *do* serialize use the
  Serializable Builder Pattern. **Recommendation: `LeasedPermissionGrant` is
  not Serializable.** A leased grant is intrinsically local — its lease handle
  is tied to a particular landlord on a particular network, and shipping the
  wrapper over the wire would require also shipping a serializable Lease proxy
  + reconstructing the dead-man-switch semantics across address spaces. Out of
  scope for this primitive. (When STD-007 federation needs serialized leased
  grants, that's a RemotePolicy-layer concern.)

### 1.10 Renewal race window

Scenario: expiration is T. The agent's renewal logic schedules renewal at
T - 5 s. The renewal RPC takes 6 s. At T + 1 ms, an `implies()` call observes
`clock.millis() >= lease.getExpiration() == T` → `isVoid() == true`. At
T + 10 ms the `renew()` call returns successfully and `lease.getExpiration()`
is updated to T + 60 s.

**Documented as correct behaviour:**

> Holders MUST renew BEFORE expiry, never after. A brief void window during a
> missed renewal is the dead-man switch operating as intended. There is no
> "grace period" or "renewal in flight" state; the lease is either valid (per
> `getExpiration()` on the local clock) or void.

A late-arriving renewal does NOT retroactively grant authority for actions
checked during the void window — those checks already returned deny. After the
late renewal lands, subsequent `isVoid()` calls return false and the grant
applies again. This is acceptable: the agent's renewal logic SHOULD use a
safety margin (renew at T - margin where margin >> max_renewal_RTT +
max_clock_skew).

### 1.11 Edge cases

(a) **Landlord unreachable / renewal RPC fails** — `Lease.renew()` throws
    `RemoteException` or `LeaseDeniedException`. The lease's `getExpiration()`
    is unchanged. At T the grant goes void. **Fail-secure**: this is the
    dead-man switch firing on network partition. Correct, documented behaviour.

(b) **`Lease.FOREVER`** — `Lease.FOREVER == Long.MAX_VALUE` (line 43). A grant
    wrapping a lease with `getExpiration() == Long.MAX_VALUE` would never go
    void on the expiry axis (only on cancel or wrapped-grant-void). This
    **defeats the entire purpose** of the leased primitive: there is no
    dead-man switch on expiry. The SOW §5 acceptance criterion *"Lease expiry
    kills escalated authority"* is unsatisfiable for a forever lease.

    **Recommendation: reject `Lease.FOREVER` at install time** (constructor
    throws `IllegalArgumentException` if `lease.getExpiration() == Lease.FOREVER`
    or if the lease implementation is statically known to be a forever lease).
    A caller who wants forever-authority should install an undecorated grant —
    the lease wrapper is exclusively for time-bounded authority.

(c) **Negative or zero duration on install** — i.e. `lease.getExpiration() <=
    clock.millis()` at the moment of construction. The grant is *born void*.
    Two valid options:
    - Reject at construction (throw `IllegalArgumentException` with message
      *"lease already expired at construction"*). This is the recommended
      default — installing a grant that is immediately void is almost
      certainly a bug.
    - Allow and let the next `isVoid()` flip true. Less defensive.

    **Recommendation: reject in the constructor.** Defence-in-depth against
    accidental misconfiguration; the sweeper would evict it anyway.

(d) **`Lease.ANY (== -1)`** — sentinel for "no particular time desired" used at
    request time (line 50). A lease whose `getExpiration()` returned `-1` would
    be even more degenerate than a negative duration. Treat the same as (c):
    reject.

(e) **Clock source under test** — the constructor accepts a `java.time.Clock`
    (or equivalent supplier). Production callers pass `Clock.systemUTC()` (or
    `System::currentTimeMillis` indirectly); unit tests pass a fixed/mutable
    clock to make boundary tests deterministic without `Thread.sleep`. See
    §3 test cases T2 and T9.

---

## 2. Class skeleton (signatures only — no bodies, not a `.java` file)

### 2.1 The new class

Package: `org.apache.river.api.security` (same package as `PermissionGrant`,
required so the package-private decorator constructor at line 194 of
`PermissionGrant.java` is accessible).

```java
package org.apache.river.api.security;

import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.time.Clock;
import net.jini.core.lease.Lease;

/**
 * Decorator that adds a Lease-driven dead-man switch to a wrapped
 * PermissionGrant. The grant is void whenever ANY of:
 *   (a) the wrapped grant's isVoid() returns true (preserves existing
 *       void conditions, including ProtectionDomainGrant GC-scoping);
 *   (b) cancel() has been called on this wrapper;
 *   (c) clock.millis() >= lease.getExpiration().
 *
 * Renewal is the caller's responsibility — this wrapper passively
 * re-reads getExpiration() on every check. Lease.FOREVER and already-
 * expired leases are rejected at construction.
 *
 * Install via DynamicPolicyProvider.grant(PermissionGrant). The
 * GrantPermission ceiling at DynamicPolicyProvider.grant:686-687 still
 * applies (the decorator's getPermissions() delegates to the wrapped
 * grant via the final method in PermissionGrant:316-319).
 *
 * @since 4.0.0
 */
public final class LeasedPermissionGrant extends PermissionGrant {

    // Primary constructor — system clock.
    public LeasedPermissionGrant(PermissionGrant wrapped, Lease lease);

    // Test/SPI constructor — pluggable clock for deterministic boundary tests.
    LeasedPermissionGrant(PermissionGrant wrapped, Lease lease, Clock clock);

    // PermissionGrant abstract methods — all delegate to wrapped, then
    // intersect with !isLeaseVoid().
    @Override public boolean implies(ProtectionDomain pd);
    @Override public boolean implies(ClassLoader cl, Principal[] pal);
    @Override public boolean implies(CodeSource codeSource, Principal[] pal);
    @Override public boolean impliesEquivalent(PermissionGrant grant);
    @Override public boolean isDyanamic();         // delegate (note: existing typo)
    @Override public boolean isVoid();              // wrapped.isVoid() || cancelled || expired
    @Override public PermissionGrantBuilder getBuilderTemplate();

    // New API — local cancellation (does NOT call lease.cancel()).
    public void cancel();

    // Lease accessor — for diagnostics / sweeper logging; SHOULD NOT be
    // used by policy decisions (use isVoid() instead).
    public Lease getLease();

    // Equality includes lease identity — distinct leases ⇒ distinct grants.
    @Override public boolean equals(Object o);
    @Override public int hashCode();

    @Override public String toString();
}
```

### 2.2 Builder integration

> **Superseded by §0 (as-built): Option B was chosen, not Option A.** The
> options below are retained for the design rationale; see §0 for why extending
> `PermissionGrantBuilder` (Option A) is unsafe on the DirtyChai runtime.

Two options were evaluated.

**Option A (originally recommended, least invasive): extend `PermissionGrantBuilder`
with a `lease(Lease)` method.**

`PermissionGrantBuilder` is abstract (line 48 of
`PermissionGrantBuilder.java`); adding a method is a *source-compatible*
change only if existing concrete subclasses pick up a no-op default. Add the
method with a default implementation:

```java
// In PermissionGrantBuilder (existing class — illustrative addition only,
// NOT applied in this design phase):
//
// public PermissionGrantBuilder lease(Lease lease) { return this; }
//
// Default: no-op. PermissionGrantBuilderImp overrides to record the lease.
// .build() inspects whether a lease was supplied; if so, wraps the produced
// grant in a LeasedPermissionGrant before returning.
```

`PermissionGrantBuilderImp.build()` (existing) returns the appropriate
`PrincipalGrant` / `URIGrant` / `ProtectionDomainGrant` / `DigestGrant`. The
override would add, just before return:

```java
// Pseudocode (not applied):
//   PermissionGrant g = /* existing build path */;
//   if (this.lease != null) {
//       g = new LeasedPermissionGrant(g, this.lease);
//   }
//   return g;
```

The lease is stored as a transient field on `PermissionGrantBuilderImp`.
`reset()` (line 203 of `PermissionGrantBuilder.java`) clears the lease along
with everything else.

**Option B (rejected): caller wraps manually.**

The agent-authority layer would call
`new LeasedPermissionGrant(builder.build(), lease)` directly. This is fine
for low-level callers but bypasses the builder's serialization-proxy hook
(`writeReplace()` in each grant calls `getBuilderTemplate()`). Wrapping
externally means `LeasedPermissionGrant.getBuilderTemplate()` would need to
return a builder pre-loaded with the wrapped grant's template *plus* the
lease — duplicating what Option A does anyway.

**Recommendation (revised, as-built):** Option B. Although Option A is the
tidier construction path in the abstract, `PermissionGrantBuilder` is embedded
in the DirtyChai `java.base`, so a new method on it cannot be relied upon at
runtime (§0). Option B keeps the change to a single new decorator class. The
serialization-proxy objection to Option B does not apply: `LeasedPermissionGrant`
is not `Serializable` (§1.9), and `getBuilderTemplate()` delegates to the wrapped
grant's template.

### 2.3 `DynamicPolicyProvider.grant()` — no change required

A `LeasedPermissionGrant` is a `PermissionGrant`. Both grant paths accept it
unchanged:

- `DynamicPolicyProvider.grant(Class, Principal[], Permission[])` (line 597)
  builds via the builder — Option A above means a caller wanting a leased
  grant simply uses the builder directly (no overload needed on this method).
- `DynamicPolicyProvider.grant(PermissionGrant)` (line 684) accepts any
  `PermissionGrant`, including a `LeasedPermissionGrant`. The `GrantPermission`
  ceiling check at line 686-687 reads `p.getPermissions()`, which the final
  `getPermissions()` on `PermissionGrant` (line 316-319) delegates to the
  wrapped grant — so the ceiling sees the real permissions.

**No source change to `DynamicPolicyProvider` is required.** The decorator
slots in transparently.

### 2.4 Notes on the decorator-base constructor

`PermissionGrant`'s decorator constructor (line 194-196) requires:
1. The caller has `RuntimePermission("getProtectionDomain")` AND
   `RuntimePermission("getClassLoader")` (line 208-209).
2. The wrapped grant is NOT privileged (line 210-211: *"Privileged
   PermissionGrant cannot be decorated"* — i.e. cannot wrap an
   `AllPermission`-carrying grant). This is a deliberate anti-recursion
   guard; it also matches the agent-authority threat model (a leased lease
   on `AllPermission` is a contradiction — `AllPermission` is the ceiling,
   not something to lease).

Both invariants are upheld by simply chaining to `super(wrapped)` in
`LeasedPermissionGrant`'s constructor. Document the AllPermission rejection
in the public constructor's Javadoc as inherited.

---

## 3. Test contract

Each test states *pre / action / post*. Bullet form; full JUnit code is left
for the build phase. A `Clock` parameter is threaded through all time-sensitive
tests so no `Thread.sleep` is needed.

### T1 — Active grant implies before expiry
- **Pre:** `Lease` with expiration = clock.now + 60_000 ms. Wrap a
  `PrincipalGrant` for principal `P` and permission `FilePermission("/scope/-",
  "read")`. Install via `DynamicPolicyProvider.grant(g)`.
- **Action:** Call `policy.implies(pd_with_P, FilePermission("/scope/file",
  "read"))`.
- **Post:** Returns true. `isVoid()` returns false.

### T2 — Grant void immediately at expiry boundary (off-by-one)
- **Pre:** Test clock at t=1000. Lease expiration E=2000. Construct
  `LeasedPermissionGrant`.
- **Action 1:** Advance clock to t=1999. Call `isVoid()`. **Post 1:** false.
- **Action 2:** Advance clock to t=2000 (E itself). Call `isVoid()`.
  **Post 2:** true. (Documents the `>=` convention — at exact expiry, void.)
- **Action 3:** Advance clock to t=2001. Call `isVoid()`. **Post 3:** true.

### T3 — Sweeper evicts expired grant
- **Pre:** `DynamicPolicyProvider` constructed with sweep period = 1 s
  (set `net.jini.security.policy.DynamicPolicyProvider.voidGrantSweepPeriodSeconds`
  before construction). Lease expiration = clock.now + 100 ms. Install grant.
  Confirm `getPermissionGrants(pd)` includes it.
- **Action:** Advance clock past expiry; wait ≥ 1 sweep period (real time, or
  inject a test-friendly sweeper schedule).
- **Post:** `getPermissionGrants(pd)` no longer includes the grant.
  (The grant returns `isVoid() == true` instantly per T2; sweeper removes it
  within one period.)

### T4 — Renewal extends life
- **Pre:** Lease expiration E1=clock.now+1000. Wrap, install. Confirm
  `isVoid() == false`.
- **Action:** Advance clock to E1 - 100 (still valid). Caller calls
  `lease.renew(60_000)` (mock landlord updates `getExpiration()` to
  clock.now + 60_000 = E2). Advance clock past E1 but before E2.
- **Post:** `isVoid() == false`. `implies(...)` continues to return true for
  permissions covered by the wrapped grant.

### T5 — Explicit `cancel()` voids immediately
- **Pre:** Lease expiration E=clock.now + 60_000 (far in future). Wrap,
  install. Confirm `isVoid() == false`.
- **Action:** Call `leasedGrant.cancel()`. Do NOT advance clock.
- **Post:** `isVoid() == true`. `implies(...)` returns false. `isVoid()`
  continues to return true on every subsequent call (cancellation is monotone).
  Lease's `getExpiration()` is unchanged (we did not call `lease.cancel()`).

### T6 — `GrantPermission` ceiling enforced on install
- **Pre:** Caller domain holds `GrantPermission(FilePermission("/foo/-",
  "read"))` but NOT `GrantPermission(FilePermission("/secret/-", "read"))`.
  Construct a `LeasedPermissionGrant` wrapping a `PrincipalGrant` with
  permission `FilePermission("/secret/-", "read")`.
- **Action:** Call `policy.grant(leasedGrant)` from the caller's domain.
- **Post:** `AccessControlException` (or equivalent) is thrown by the
  `GrantPermission.checkGuard(null)` call at `DynamicPolicyProvider.grant`:687.
  Grant is NOT added to `dynamicPolicyGrants`. (Confirms wrapping does not
  bypass the ceiling — §1.7.)

### T7 — Conjunctive principal matching preserved
- **Pre:** Wrapped grant is a `PrincipalGrant` requiring principals
  `{A, B}` (both must be present per
  `PrincipalGrant.implies(Principal[]):203-235`). Lease is valid.
- **Action 1:** Call `leasedGrant.implies(cl, new Principal[]{A})`.
  **Post 1:** false (missing B).
- **Action 2:** Call `leasedGrant.implies(cl, new Principal[]{A, B})`.
  **Post 2:** true.
- **Action 3:** Same as Action 2 but after expiry. **Post 3:** false (the lease
  void short-circuits even when principals match — monotone attenuation).

### T8 — GC of wrapped proxy voids decorator while lease is live
- **Pre:** Wrap a `ProtectionDomainGrant` holding a `WeakReference<ProtectionDomain>`
  to a transient PD. Lease expiration = clock.now + 60_000 (far in future).
  Confirm `leasedGrant.isVoid() == false`.
- **Action:** Drop strong reference to the PD. Force GC (test harness:
  `System.gc()` + a `WeakReference` sentinel to confirm collection happened).
- **Post:** `leasedGrant.isVoid() == true` even though the lease is live.
  (`ProtectionDomainGrant.isVoid()` at line 191-196 returns true on cleared
  WeakReference; the decorator's `isVoid()` short-circuits — §1.5 condition (a).)

### T9 — `Lease.FOREVER` rejected at construction
- **Pre:** Mock `Lease` whose `getExpiration()` returns `Lease.FOREVER`
  (`Long.MAX_VALUE`).
- **Action:** `new LeasedPermissionGrant(wrapped, foreverLease)`.
- **Post:** `IllegalArgumentException` thrown with a message explaining that
  `LeasedPermissionGrant` rejects `Lease.FOREVER` (per §1.11 (b)).

### T10 — Already-expired lease rejected at construction
- **Pre:** Test clock at t=1000. Lease expiration E=500 (already past).
- **Action:** `new LeasedPermissionGrant(wrapped, expiredLease, clock)`.
- **Post:** `IllegalArgumentException` thrown — §1.11 (c).

### T11 — Pluggable clock enables deterministic boundary tests
- **Pre:** Test uses a `Clock` implementation that exposes `setMillis(long)`
  (no `Thread.sleep`).
- **Action:** All time-sensitive tests (T2, T3, T4, T8) use this clock.
- **Post:** Tests run in <100 ms total and are deterministic across CI hosts
  with varying clock skew.

### T12 — Equality and `hashCode` distinguish leases
- **Pre:** Two distinct `Lease` instances `L1`, `L2` with equal expiration
  values (but different identities). One wrapped grant `g`.
- **Action:** `new LeasedPermissionGrant(g, L1)` vs
  `new LeasedPermissionGrant(g, L2)`.
- **Post:** Not equal; `hashCode`s differ (with high probability — both depend
  on `System.identityHashCode(lease)`). (Confirms §1.9: lease is identity.)

### T13 — Decorator rejects privileged (AllPermission) wrapped grant
- **Pre:** A `PermissionGrant` whose `isPrivileged() == true` (contains
  `AllPermission`).
- **Action:** `new LeasedPermissionGrant(privilegedGrant, validLease)`.
- **Post:** `IllegalArgumentException` thrown by `PermissionGrant`'s
  `checkInvariants` at line 210-211 — inherited behaviour, but should be
  asserted to document that the leased primitive cannot wrap AllPermission.

### T14 — `getPermissions()` delegates (ceiling visibility)
- **Pre:** Wrap a grant carrying permissions `{P1, P2}`.
- **Action:** Call `leasedGrant.getPermissions()`.
- **Post:** Returns `{P1, P2}` — the wrapped grant's set, NOT empty.
  (Confirms §1.7: the `GrantPermission` ceiling check at
  `DynamicPolicyProvider.grant`:686 sees the real perms.)

---

*End of design.*
