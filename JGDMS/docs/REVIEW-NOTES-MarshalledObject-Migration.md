# Review notes — MarshalledObject → MarshalledInstance migration

Branch: `feature/atomicserial-marshal-delegate`. For the reviewing agent.

## TL;DR

Goal: replace `java.rmi.MarshalledObject` with `net.jini.io.MarshalledInstance`
on every **fresh-marshal** path so the DER schema (which `MarshalledObject`
drops and `MarshalledInstance`/`AtomicMarshalledInstance` carry) is preserved
end to end. **Schema-preservation is complete** — no schema-dropping
`convertToMarshalledObject()` and no stray `new MarshalledObject(...)` remain in
the core services. Total *removal* of `MarshalledObject` is a deliberate future
breaking step (see §8); the legacy methods are kept as
`@Deprecated(forRemoval=true)` bridges meanwhile.

The high-value thing to validate is **in-place upgrade**: a pre-4.0.0 persisted
log / snapshot written with `MarshalledObject` must still recover under this
branch (the dual-reads in §4). Unit compile is green on the DirtyChai JDK across
the reactor; the persisted-state and event round-trips want the QA harness.

## 1. Patterns used (so the diffs read cleanly)

- **Decision-B dual-read.** Always *write* the canonical `MarshalledInstance`
  (specifically `AtomicMarshalledInstance`, the DER form); on *read* accept
  either a legacy `MarshalledObject` or a `MarshalledInstance`, normalising via
  `el instanceof MarshalledInstance ? (MarshalledInstance) el : new
  MarshalledInstance((MarshalledObject) el)`. Old data upgrades on next write;
  no flag-day. `new MarshalledInstance(MarshalledObject)` is the *safe*
  up-convert (never throws); `convertToMarshalledObject()` is the lossy
  down-convert and is used ONLY in the deprecated bridges.
- **Polymorphic `Object` handback field.** Where a single handback is stored
  (reggie `EventReg.handback`, norm `EventType.handback`), the field is `Object`
  holding MO or MI; the event-firing path branches on type. Dual-read safe: a
  `MarshalledObject` value deserialises into an `Object` field unchanged.
- **`Object[]` field widening (LogRecord arrays).** The mercury/fiddler LogRecord
  attribute arrays were widened `MarshalledObject[]` → `Object[]` rather than
  given a dual `serialPersistentFields` slot. This is only valid because those
  JOSS classes have **explicit `serialVersionUID`** — see §4.
- **Parallel "Safe" interface vs `default` overload.** The MI sibling of a
  legacy MO method is exposed either by a separate interface
  (`SafeServiceRegistrar.notiFy`, `TupleSpace.notify`) where one already
  existed, or by a `default` overload (norm `LeaseRenewalSet`) that
  down-converts for third-party implementors while the JGDMS proxy overrides it
  to carry MI through losslessly.
- **`@Deprecated(forRemoval=true)`** on the legacy MO methods/ctors (release-21
  modules) per the directive to remove `MarshalledObject` entirely.

## 2. Commit map

| Commit | Area | Notes |
|---|---|---|
| `d28afdf26` | discovery | **Remove DiscoveryV1** (insecure: plaintext multicast + JOSS unicast). Separate security change; see `docs/SOW-MarshalledObject-Migration-RemainingGroups.md` context and the commit body. |
| `afbf5fa67` | join state | outrigger/mahalo `JoinStateManager`, norm `JoinState` attribute persistence (stream dual-read). |
| `0c983c36c` | mercury | `EventID` (JOSS path), `EventWriter`/`EventReader`. |
| `7204670c6` | fiddler/starter | `RegistrationInfo` listener; `SharedActivationGroupDescriptor` cookie. |
| `83f35c035` | lib-dl | `LeaseUnmarshalException` → `MarshalledInstance[]`; `SetProxy.getLeases` stops downgrading. |
| `11113d45d`,`70aacb9fc` | docs | remaining-groups SOW; ServiceUI MI-Entry design; the three decisions. |
| `6b9a73e53` | spaces | deprecate `JavaSpace.notify(MarshalledObject)`. |
| `d15a06b17` | norm | **LeaseRenewalSet handback** — 9-file slice incl. persisted `EventType`. |
| `c94bb2058` | interfaces | flip the 4 handback interfaces to `forRemoval`. |
| `1d69c7054` | fiddler/platform | **getRegistrars / discoveredRegsMap / RemoteDiscoveryEvent**; `LookupUnmarshalException` migrated. |
| `b867b47ca` | mercury/fiddler | **LogRecord attribute arrays** (Object[]-field) + a missed listener LogRecord. |

## 3. Review hardest here (the careful spots)

These change a **persisted / wire serial form**; the dual-read is where a subtle
bug would silently drop or fail to recover old data.

1. **norm `EventType`** (`@AtomicSerial`, persisted) — `handback` field
   `MarshalledObject` → `Object`; `serialForm` + `(GetArg)` use `Object.class`.
   `EventFactory.createEvent` + its impls in `LeaseSet`/`ClientLeaseWrapper`
   branch `instanceof MarshalledInstance`. Check the `(GetArg)` reads a legacy
   `MarshalledObject` value into the `Object` field (it does — generic read).
2. **`RemoteDiscoveryEvent`** (`@AtomicSerial`) — `marshalledRegs`
   `List<MarshalledObject>` → `List<MarshalledInstance>`, normalised in BOTH the
   `check()` validating helper (checkedList element type) AND the `(GetArg)`
   ctor. `serialForm` stays `List.class`.
3. **fiddler `RegistrationInfo.discoveredRegsMap`** (`@AtomicSerial`, persisted)
   — `Map` value `MarshalledObject` → `MarshalledInstance`, normalised in BOTH
   `check()` (checkedMap value type) AND the `(GetArg)` ctor. `serialForm` stays
   `Map.class`. Key-based map ops (`containsKey`/`remove` by `ServiceRegistrar`)
   were left untouched — only `.values()`/`.put`/serial care about the value type.
4. **mercury/fiddler LogRecord `Object[]` arrays** (JOSS, default-serialized) —
   the `Object[]` widening is correct **only because** these classes have
   explicit `serialVersionUID` (mercury `1L`; fiddler `4983778…`/`2671139…`).
   The UID is unchanged, so an old record's `MarshalledObject[]` value
   default-deserialises into the `Object[]` field via array covariance
   (`MarshalledObject[]` *is-a* `Object[]`), and `unmarshalAttributes(Object[])`
   normalises each element. **If any of these classes lacked an explicit UID, the
   widening would change the computed UID and reject old records — so verify the
   UIDs are still present and unchanged.** `recoverSnapshot()` stream reads in
   both services were widened to `(Object[]) stream.readObject()`.
5. **`ConstrainableSetProxy` reflective method-maps** (norm) — `methodMap1` gained
   the new MI client→server pairs (`RemoteEventListener.class, long.class,
   MarshalledInstance.class` → the `Uuid`-prefixed server signature). These are
   `Class.getMethod(...)` lookups resolved at class-init; a signature typo throws
   `NoSuchMethodError` on first use, not at compile. They compile and match the
   declared overrides — worth a sanity check that the `clear*Listener` paths stay
   on the MO server method via the `(MarshalledObject) null` disambiguation cast.

## 4. QA focus

The unit suites compile; these want the harness:

- **In-place upgrade.** Recover a service from a **pre-4.0.0 persistent log /
  snapshot** (mercury mailbox, fiddler lookup-discovery, norm renewal set,
  outrigger/reggie join state) — the dual-reads in §3 must reconstruct the old
  `MarshalledObject`-form attributes/handbacks/registrars without loss.
- **Event round-trips.** Register with a handback and confirm delivery:
  `ServiceRegistrar`/reggie notify, `JavaSpace`/outrigger notify +
  availability, `LeaseRenewalSet`/norm expiration-warning + renewal-failure.
  The handback you set should come back intact (try both the MO and the new MI
  client overloads).
- **Discovery.** fiddler `getRegistrars` → `RemoteDiscoveryEvent` →
  client unmarshal; and confirm V2-only discovery still works after DiscoveryV1
  removal (a V1 peer should simply not be discovered — that is intended).

## 5. Decisions to agree with or push back on

1. **`forRemoval` on core spec methods.** `ServiceRegistrar.notify`,
   `JavaSpace.notify`, `JavaSpace05.registerForAvailabilityEvent`,
   `LeaseRenewalSet.set*Listener` are now `@Deprecated(forRemoval=true)`. This
   follows the directive to remove `MarshalledObject` entirely. If you'd rather
   not commit these *spec* methods to removal yet, downgrade to plain
   `@Deprecated` — trivial revert.
2. **`UIDescriptor.factory` left as `MarshalledObject`.** It's a brittle
   spec-published `Entry`; the schema-preserving path is a **new** MI-native
   Entry on the JavaFX track (`docs/DESIGN-ServiceUI-MarshalledInstance-Entry.md`),
   not a mutation. ClassLoader provisioning already works via the codebase
   annotation that survives `convertToMarshalledObject()`; only the (low-value,
   code-present) schema is lost.
3. **`Object[]` widening vs dual-slot** for the LogRecord arrays — chosen for
   minimality given the explicit UIDs (§4.4). If you prefer an explicit dual
   `serialPersistentFields` slot (à la mahalo `StorableObject`) for clarity,
   that's a reasonable alternative.
4. **`LeaseUnmarshalException` / `LookupUnmarshalException`** were migrated so the
   canonical form is `MarshalledInstance[]`; the `getMarshalled*()` MO accessors
   are `forRemoval` and down-convert on demand (and are documented to fail on a
   non-JOSS/DER payload). Both had ~zero internal callers, so blast radius is the
   deprecated public API only.

## 6. Known non-fatal warnings

Flipping the interfaces to `forRemoval` makes their implementors/callers
(`RegistrarProxy`, `SpaceProxy2`, `ServiceDiscoveryManager`, `JoinManager`, the
discovery managers, `Browser`) emit **removal** warnings. These are intentional —
they mark exactly the call sites that step 1 of §8 will delete — and non-fatal:
the build has no `-Werror`. The `default` MI methods that knowingly call the
deprecated MO methods carry `@SuppressWarnings({"deprecation","removal"})`.

## 7. Sub-agent provenance

The two larger slices (norm handback `d15a06b17`, getRegistrars `1d69c7054`) and
the LogRecord arrays (`b867b47ca`) were implemented by sub-agents against precise
specs; the careful spots in §3 were human-reviewed and each was recompiled
broadly before commit. The getRegistrars agent independently found that
`LookupUnmarshalException` needed the same treatment as its sibling and applied
the established template.

## 8. Out of scope / remaining for total `MarshalledObject` removal

The 6 remaining `convertToMarshalledObject()` are all intentional:

- **`forRemoval` down-convert bridges** — `RemoteEvent.getRegistrationObject`,
  `Lease`/`LookupUnmarshalException` MO accessors, the `LeaseRenewalSet` default
  fallbacks. Deleting the `forRemoval` methods removes ~5 of the 6.
- **`MarshalledObjectSerializer`** — the `@AtomicSerial` representation that lets
  a legacy `MarshalledObject` value still be *read* during the dual-read window;
  removed last, once no old persisted data needs reading.
- **Separate tracks:** **phoenix-activation** (`java.rmi.activation`'s own
  `MarshalledObject` usage) and the ServiceUI **new Entry** (§5.2).

Total removal = (1) delete the `forRemoval` methods + their bridges, (2) drop
`MarshalledObjectSerializer`, with phoenix and the ServiceUI Entry as their own
efforts. None of that is in this branch.
