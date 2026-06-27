# SOW — MarshalledObject → MarshalledInstance: remaining deep groups

Status note for the `feature/atomicserial-marshal-delegate` branch (Task #10).
The goal of the migration is to preserve the DER **schema** that
`java.rmi.MarshalledObject` drops but `net.jini.io.MarshalledInstance`
(specifically `AtomicMarshalledInstance`) carries.

## Done (clean sites — the "decision-B dual-read" pattern)

Each writes the canonical `MarshalledInstance` and reads either the legacy
`MarshalledObject` or the new `MarshalledInstance`, normalizing via
`instanceof`:

- `mahalo StorableObject` (@AtomicSerial dual `bytes`/`instance` serial fields),
  `outrigger StorableReference` (Externalizable) — the wrapper templates.
- `RemoteEvent` + event subclasses (MI ctor + `getRegistrationInstance()`;
  MO ctor/`getRegistrationObject()` `@Deprecated`).
- `mercury ServiceRegistration`, `norm EventType`.
- JoinState attribute persistence: `outrigger`/`mahalo JoinStateManager`,
  `norm JoinState`.
- mercury event log: `EventID` (JOSS path), `EventWriter`/`EventReader`.
- `fiddler RegistrationInfo` listener, `service-starter
  SharedActivationGroupDescriptor` cookie.
- `LeaseUnmarshalException` canonical `MarshalledInstance[]` + `SetProxy`
  stops downgrading (the producer already held MIs).
- `DiscoveryV1` removed entirely (insecure) — disposed of its convert sites.

Everything below is **not** a clean isolated stream write; each changes a
persisted field type / serial form or a remote-interface contract, so it is
brought here for review before implementation.

## Group A — persisted field-type / serial-form changes

The payload is held in a typed field (`MarshalledObject[]` or a
`Map<…,MarshalledObject>`) that is itself persisted, so the dual-read must
live at the **owning record's** serial form, not in a helper.

| Site | Field | Notes |
|---|---|---|
| `mercury MailboxImpl` LogRecord classes | `MarshalledObject[] marshalledAttrs / marshalledAttrTmpls / marshalledModAttrs` | `marshalAttributes`/`unmarshalAttributes` helpers; many `LogRecord` subclasses |
| `fiddler FiddlerImpl` LogRecord classes | same three array fields | plus `writeObject`/`readObject` at ~6864/6925 that stream a `MarshalledObject[]` |
| `fiddler FiddlerImpl.discoveredRegsMap` | `@AtomicSerial Map<ServiceRegistrar, MarshalledObject>` | values flow out via `getRegistrars()` → `RemoteDiscoveryEvent` (a **protocol** return type, `MarshalledObject[]`) |
| `net.jini.lookup.entry.UIDescriptor.factory` (+ `UIDescriptorBean`) | public `MarshalledObject factory` field | an attribute Entry; the marshalled UI factory |
| `org.apache.river.proxy.MarshalledWrapper` | `MarshalledObject instance` | already *requires* `instance instanceof MarshalledInstance`; candidate to type as MI |

### Proposed approach (Group A)

1. Change the shared `marshalAttributes`/`unmarshalAttributes` helpers to
   produce/consume `MarshalledInstance[]` (via `AtomicMarshalledInstance`),
   element-wise dual-read on the way in.
2. Change each owning LogRecord field to `MarshalledInstance[]`. Because these
   records are `@AtomicSerial`/Serializable and persisted, give each a
   **dual serial slot** (legacy `MarshalledObject[]` optional + new
   `MarshalledInstance[]` optional), exactly like `StorableObject`. Old logs
   upgrade in place on next snapshot.
3. `discoveredRegsMap`: migrate the map value type to `MarshalledInstance`,
   and make `getRegistrars()` return `MarshalledInstance[]`; the
   `RemoteDiscoveryEvent` end already dual-handles MO/MI, so the seam is the
   event boundary. **Open question:** `getRegistrars()` is a Fiddler proxy
   protocol method — confirm whether to add an MI variant + deprecate, or
   change in place (4.0.0 breaking).
4. `UIDescriptor`: public field is API; add an MI field + dual-read, deprecate
   the MO field/accessors (it is `@AtomicSerial` and an Entry on the wire).
5. `MarshalledWrapper`: low-risk type tightening to `MarshalledInstance`.

**Risk:** rewrites persisted record schemas across many subclasses and one
discovery protocol return type. Needs the QA harness to validate recovery.

## Group B — remote-interface handback

The event **handback** is passed client→server, stored, and returned inside
events. To preserve its schema it must travel as `MarshalledInstance`
end-to-end. The event base already dual-carries (`RemoteEvent.handback` MO +
`miHandback` MI); the gap is the inbound interfaces + their impls.

Interfaces taking `MarshalledObject handback`:

- `net.jini.core.lookup.ServiceRegistrar.notify(…, MarshalledObject, long)`
- `net.jini.space.JavaSpace.notify(…, MarshalledObject)`
- `net.jini.space.JavaSpace05.registerForAvailabilityEvent(…, MarshalledObject)`
- `net.jini.lease.LeaseRenewalSet.setExpirationWarningListener / setRenewalFailureListener(…, MarshalledObject)`

### Why a bare `default` overload is wrong

A `default … notify(MarshalledInstance)` that converts MI→MO and calls the
abstract MO method **loses the schema at the boundary** — defeating the point.
Preserving it requires the implementations (reggie `RegistrarImpl`, outrigger,
mercury, norm, the matching proxies) to store the MI and deliver it via
`RemoteEvent`'s MI path.

### Proposed approach (Group B)

1. Add an `MarshalledInstance`-handback overload to each of the four
   interfaces (as a `default` for source-compat) **plus** a real
   implementation override in each server/proxy that threads MI →
   `miHandback`.
2. Deprecate the `MarshalledObject`-handback methods (`@Deprecated`;
   `forRemoval=true` only on release-21 modules — `JavaSpace`/`JavaSpace05`/
   `LeaseRenewalSet` are lib-dl release 21; `ServiceRegistrar` is platform
   release 21).
3. Validate the full register→store→deliver→handback round-trip under the QA
   harness.

## Sequencing & validation

- Both groups need the **QA ant harness** (currently held by another agent) to
  validate persisted-state recovery and event round-trips; unit compile alone
  is insufficient.
- Suggested order: Group A LogRecord arrays (self-contained per service) →
  `discoveredRegsMap`/`getRegistrars` (protocol decision) → `UIDescriptor` →
  Group B (largest; touches every event-producing service).

## Decisions (resolved 2026-06-24)

1. **`getRegistrars()` / `RemoteDiscoveryEvent`** → **MI variant + deprecate**,
   landed *with Group B* (it is a public discovery SPI on the event-delivery
   path: `LookupDiscoveryRegistration.getRegistrars()`). Add
   `getRegistrarInstances()` → `MarshalledInstance[]`, deprecate
   `getRegistrars()`, migrate `discoveredRegsMap` behind it.
2. **`UIDescriptor.factory`** → **leave as `java.rmi.MarshalledObject`.** It is
   a brittle, spec-published `Entry`; mutating a field changes its serial form /
   matching. ClassLoader provisioning already works via the codebase annotation
   that survives `convertToMarshalledObject()`; only the (low-value, code-present)
   schema is lost. The schema-preserving ServiceUI path is a **new
   MarshalledInstance-native Entry** delivered with the JavaFX track — see
   `docs/DESIGN-ServiceUI-MarshalledInstance-Entry.md`. Out of scope for this SOW.
3. **Group B** → **proceed now.** The reviewing agent runs the QA harness during
   review, so the cross-service handback change is validated there rather than
   blocked on local harness availability.

## Group B execution plan (in progress)

Per service, the handback must travel as `MarshalledInstance` end-to-end
(client → proxy → server backend → persisted registration → `RemoteEvent`).
`RemoteEvent` already dual-carries (`handback` MO + `miHandback` MI +
`getRegistrationInstance()`), so the work per interface is:

1. Interface: add a `notify(..., MarshalledInstance handback, ...)` overload as a
   `default` (so third-party implementors stay source-compatible) + `@Deprecated`
   the `MarshalledObject` overload.
2. Proxy (Registrar/Space/Set proxies): override the MI overload to carry the MI
   to the server backend (no down-convert).
3. Server backend interface + impl: accept and **store** the MI handback
   (persisted registration record gets a dual MO/MI serial slot, StorableObject
   style) and construct the event via `RemoteEvent`'s MI path.

Order: `ServiceRegistrar`/reggie (reference) → `JavaSpace`/`JavaSpace05`/
outrigger → `LeaseRenewalSet`/norm. `getRegistrars()`/`discoveredRegsMap` rides
with the fiddler/discovery portion.
