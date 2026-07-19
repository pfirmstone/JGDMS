# JGDMS API Binary-Compatibility — Accepted-Breaks Ledger

**Status:** Active · **Created:** 2026-06-29

This ledger records the **intended or forced** binary-incompatible changes in the
4.0.0 line so the japicmp gate fires **only on UNCLASSIFIED breaks** — i.e. changes
that match none of the categories below and are therefore candidate *accidental*
regressions that must be adjudicated (justified → add here) or fixed (revert).

## Scope & baselines

- **Surface gated:** the cross-loader linkage contract — the ClassDep transitive
  closure of the service-API roots (platform + lib API, the JERI invocation/trust
  layer, `-dl` published interfaces/entries), minus preferred/downloaded classes.
  See memory `jgdms-api-compat-tooling`.
- **Baselines:** `au.net.zeus.jgdms:*:3.1.0` (Maven Central) for shared artifacts;
  tag `baseline/pre-ai-agents` (`72c8372b`) for service `-dl` (3.1.0 services are
  pom-only aggregators).
- **Two axes:** japicmp covers the **synchronic/linkage** axis. The **diachronic/serial**
  axis (serial form, `@SerialEntry` hashes) is the schema-tracker's job — Category A
  below is routed there, not treated as a linkage failure.

## Categories

### A — `Serializable` removal program *(serial axis, not linkage)*
Deliberate JGDMS-wide removal of Java-Serialization coupling (target version 4.0.0
or 5.0.0, TBD). The gate must **not** fail on these; they are tracked on the serial
axis by the schema-tracker.
- **Match:** `REMOVED INTERFACE: java.io.Serializable`; `serialVersionUID` removed/changed;
  private serialized-field type/`serialPersistentFields` changes; named entries below for
  removed `PutField`/`GetField`-based helpers that don't match the generic text patterns.
- **Seen:** `MarshalledInstance`, `AtomicSerial$GetArg`/`$Factory`/`$ReadObject`,
  `org.apache.river.jeri.internal.runtime.DgcClient`, the `*Serializer` `serialVersionUID` drops.
- **2026-07-19** — `org.apache.river.api.io.ArrayClassNotFoundException`: the static
  `putArgs(ObjectOutputStream.PutField, ArrayClassNotFoundException)` helper was removed.
  Surfaced by the new `baseline/pre-ai-agents` trunk-baseline diff (T3,
  docs/SOW-Compat-Gate-CI-Enforcement.md) — invisible against the 3.1.0 baseline, since
  this class postdates 3.1.0 entirely. Same Serializable-removal program as the rest of
  this category (a `PutField`-based `writeObject` helper going away as the class moves
  off `java.io.Serializable`), just a plain "REMOVED METHOD" japicmp shape rather than
  a "not serializable"/"field removed" one; named explicitly in `classify_breaks.py`
  rather than widening the generic text match.

### B — `java.rmi.activation` removal *(JDK-forced)*
OpenJDK removed `java.rmi.activation.*` (deprecated JDK 15, removed JDK 17+), forcing
migration to `net.jini.activation.arg.*`.
- **Match:** signature changes/removals swapping `java.rmi.activation.{ActivationID,
  ActivationException,ActivationSystem,UnknownGroupException}` / `java.rmi.MarshalledObject`
  for `net.jini.activation.arg.*`.
- **Seen:** `ActivatableInvocationHandler`, `ActivationExporter`, `ActivationGroup`,
  `org.apache.river.activation.ActivationAdmin`, `org.apache.river.proxy.MarshalledWrapper`.

### C — `MarshalledObject` → `MarshalledInstance`/`arg` migration
- **Match:** field/return type `java.rmi.MarshalledObject` → `MarshalledInstance` or
  `net.jini.activation.arg.MarshalledObject`.
- **Seen:** `LookupUnmarshalException.marshalledRegistrars` (private field, serial-form),
  `MarshalledWrapper`.

### D — Deliberate removals (DiscoveryV1 · SecurityManager · RemotePolicy)
- **Seen:** `IncomingUnicastResponse`, `OutgoingUnicastResponse`,
  `Discovery.PROTOCOL_VERSION_1` / `getProtocol1()`, `DiscoveryProtocolVersion.ONE`,
  `CombinerSecurityManager`, `DelegateSecurityManager`, `RemotePolicy`.

### E — Pre-AI deliberate API changes (human)
- `net.jini.core.discovery.LookupLocator.scheme()` made `final` — 2021, PR #103.
- `WakeupManager$ThreadDesc.getGroup()` removed — virtual-thread migration.

### F — Legacy-namespace relocation (`org.apache.river.api.security` → `au.net.zeus.jgdms.api.policy`)
`jgdms-platform` is progressively emptying the shared/legacy `org.apache.river.api.security`
package (standard-JDK-compatible, Apache River heritage) toward removal or DirtyChai-only
content. New/actively-developed classes are relocated to the `au.net.zeus.jgdms` namespace
instead of being added to or left in the legacy package.
- **Match:** `REMOVED CLASS/INTERFACE: org.apache.river.api.security.RemotePolicyProvider`;
  `REMOVED INTERFACE: org.apache.river.api.security.RemotePolicyService` (both re-appear,
  unchanged in shape, as `au.net.zeus.jgdms.api.policy.{RemotePolicyProvider,RemotePolicyService}`).
- **Seen:** 2026-07 — `RemotePolicyProvider`/`RemotePolicyService` pair moved together (the
  provider's javadoc references the service and vice versa); all `services/policy-service`
  callers re-pointed to the new package. Serial axis: `PolicyEventLease.server`'s declared
  field type changed package (`serial-schema.golden` updated) — `@AtomicSerial` retype is
  informative-only per `check-serial-schema.sh`, not a gate failure; wire compatibility is
  unaffected because the interface itself is never serialized, only concrete proxy
  implementations of it are.

### Benign (flagged by japicmp, not a real break)
- `DelegationAbsoluteTime` "field removed" = `private static SoftReference formatterRef`
  (impl detail; public serial form intact).

## Gate rule

```
japicmp -a public -b  (binary-incompatible, public API)
  → classify each change against A–E
  → FAIL the build only on changes matching NONE
     (UNCLASSIFIED = candidate accidental break → adjudicate or fix)
```

## Resolved accidental breaks
- **2026-06-29** — surface *expansion*: 5 `@Serializer` proxies
  (`PropertiesSerializer`, `StackTraceElementSerializer`, `ThrowableSerializer`,
  `URISerializer`, `X500PrincipalSerializer`) and `ConcurrentPermissions` had been
  made `public` during AI-era rewrites; they have no cross-package consumers and are
  reached via the same-package generated `MarshalDelegate`. **Reverted to
  package-private** (restores 3.1.0 visibility). Verified: jgdms-platform compile +
  12 marshal round-trip tests green on the DirtyChai JDK.
