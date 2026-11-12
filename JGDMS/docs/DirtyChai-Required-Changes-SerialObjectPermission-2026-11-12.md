# DirtyChai — SerialObjectPermission before class initialisation

**Status:** the required change is done, in DirtyChai `trunk` `31fef43207e` ("proxy
deserialization gap: SerialObjectPermission #213"), written by the project lead. It was verified
against that commit on 2026-11-12 (section 4). Section 5's follow-up, exempting array classes, is
done in `fe3464b3740` and verified the same day.
Analysis written with AI assistance (Claude). It contains no DirtyChai code: DirtyChai's
`CLAUDE.md` and the OpenJDK interim AI policy require contributions there to be human-written.

## 1. The defect

`SerialObjectPermission(className)` was checked in two places in `java.io.ObjectInputStream`:

- `readProxyDesc` (dynamic proxy classes), right after `filterCheck(cl, -1)`;
- `readOrdinaryObject` (each ordinary object), just before the instance is created.

`readNonProxyDesc` (the descriptor of every ordinary class) had no check. After `resolveClass`
and `filterCheck` it calls `desc.initNonProxy(...)`, which reaches `ObjectStreamClass.lookup` →
`getDeclaredSUID`. That method reads the class's static `serialVersionUID` field reflectively, and
a reflective static field read **initialises the class**. So the static initialiser of any class
named in a stream ran before `SerialObjectPermission` was checked. When the check in
`readOrdinaryObject` then refused the object, the initialiser had already run.

Loading is not initialising. `resolveClass` returns a loaded class without running its initialiser.
`ObjectInputStream.resolveClass` uses `Class.forName(name, false, loader)`. JGDMS's streams
(`MarshalInputStream`, `AtomicMarshalInputStream`) resolve through `ClassLoading.loadClass`, which
goes to `PreferredClassProvider` or `RMIClassLoader`. `PreferredClassProvider` passes
`initialize=false` everywhere (`PreferredClassProvider.java:622, 654, 705, 722, 1484`). The one
exception, `Class.forName(name)` at `:605`, is only reached for arrays of primitive types, which have
no initialiser. A check placed after `resolveClass` and `filterCheck` therefore runs before any
initialiser.

### Why it matters

DirtyChai's other gates did hold in the same tests:

- The codebase of a class must hold `URLPermission "GET:"` and `LoadClassPermission` before a class
  is defined from it (`SecureClassLoader`).
- The deserialising stack must hold `SerialObjectPermission` for the object.

But code from any codebase the policy lets load could run its static initialiser while
deserialisation of it was being refused. The same applied to the initialiser of any local class
named in a stream.

## 2. Evidence (before the fix)

DirtyChai `trunk` `4c71278f1`, release build, two JVMs:

- The server exports a TCP bootstrap proxy with codebase `http://localhost:<port>/boot.jar`. Its
  service proxy's stream carries a class `Payload` annotated with a second codebase,
  `httpmd://localhost:<port>/payload.jar;sha=…`. `Payload`'s static initialiser prints its stack.
- The client runs under a SecurityManager. Its policy:
  - grants the experiment's own code everything it needs except `SerialObjectPermission "Payload"`;
  - grants `BootstrapPermission` to `boot.jar`;
  - grants `URLPermission` and `LoadClassPermission` to every domain, so that only the
    deserialisation gate stands between `payload.jar` and the client.

Result: `AccessControlException: SerialObjectPermission "Payload"`. But the static initialiser had
already run in the client, with this stack (abridged):

```
Payload.<clinit>
  Unsafe.ensureClassInitialized
  MethodHandleAccessorFactory.newFieldAccessor
  Field.getLong
  ObjectStreamClass.getDeclaredSUID
  ObjectStreamClass.<init> / lookup
  ObjectStreamClass.initNonProxy
  ObjectInputStream.readNonProxyDesc        (initNonProxy call, :2049 before the fix)
  ObjectInputStream.readClassDesc
  ObjectInputStream.readOrdinaryObject
```

With a policy that grants `URLPermission` and `LoadClassPermission` only to `boot.jar`'s URL, or
grants them to no downloaded code, `payload.jar` was refused at definition: no class was defined
from it.

## 3. The change (as made in `31fef43207e`)

1. In `readNonProxyDesc`: check `SerialObjectPermission(cl.getName())` immediately after
   `filterCheck(cl, -1)`, before `skipCustomData()` and `initNonProxy`, when `cl != null`. Each
   superclass descriptor is read through `readNonProxyDesc` again (the `readClassDesc(false)`
   argument to `initNonProxy`), so each gets its own check before anything initialises it.
2. In `readProxyDesc`: the same `cl != null` guard. When class resolution fails, `cl` is null and
   the stream records a `ClassNotFoundException` for that object. Without the guard, `cl.getName()`
   threw `NullPointerException`: still fail-closed, but the wrong exception, and the stream could
   not skip the unresolvable object.
3. The check in `readOrdinaryObject` stays. A descriptor met again through a back-reference does not
   pass through `readNonProxyDesc`, and the later check runs for every object read, against the
   stack at that time.

Limit: this holds only while the stream's `resolveClass` does not initialise. `ObjectInputStream`
cannot enforce that for a subclass that overrides `resolveClass` with `Class.forName(name)` or that
touches the class. JGDMS's streams do not (section 1). Third-party subclasses are not covered.

## 4. Verification of `31fef43207e`

Same experiment, DirtyChai built from `31fef43207e`:

| Client policy | Result | Client-side initialiser runs |
|---|---|---|
| no `SerialObjectPermission "Payload"` (and `"[B"` granted, see section 5) | refused: `SerialObjectPermission "Payload"` | **0** (1 before the fix) |
| control: `SerialObjectPermission "Payload"` granted | resolved, `Payload` in `PreferredClassLoader[httpmd://…/payload.jar…]` | 1 |

## 5. Array class descriptors are exempt (done in `fe3464b3740`)

Array classes are described in the stream by a class descriptor (`[B`, `[Ljava.lang.String;`), so
they also pass through `readNonProxyDesc`. Arrays are read by `readArray`, not
`readOrdinaryObject`, so they were never checked before. They are now.

- Observed: with the fix, the first run of the experiment above was refused at
  `SerialObjectPermission "[B"`, before `Payload` was reached. JOSS `MarshalledInstance` has
  `byte[]` fields, so any JOSS stream containing a `MarshalledInstance` needs it.
- The repository's policies (QA, benchmarks, `*.grantperm`, mostly recorded by polpAudit) grant
  `SerialObjectPermission` by exact class name and never for an array type. Under a SecurityManager
  on `31fef43207e` they will be refused where they were not, until they are re-recorded.
- An array class has no static initialiser. Resolving an array descriptor loads the component class
  but does not initialise it, and each element object is read and checked on its own.

The decision for the project lead:

- **(a)** exempt array classes from the new check (`!cl.isArray()`), which keeps the policy surface
  as it was; or
- **(b)** keep checking them and re-record the policies with polpAudit. `SerialObjectPermission` is
  a `BasicPermission`, so a name like `[B` can only be granted exactly: a trailing `.*` wildcard does
  not cover array names.

Recommendation: (a). The check exists to stop initialisers running and objects being created
before the permission is checked. An array descriptor does neither, and the elements stay
individually checked.

**Decision (project lead, 2026-11-12): (a), arrays are exempt.** The change is in
`readNonProxyDesc` only: the new check's condition becomes `cl != null && !cl.isArray()`.

- `isArray()` also covers nested arrays (`[[B`).
- `readProxyDesc` never sees an array class, and `readOrdinaryObject` never reads arrays.
- For an object array (`[Lcom.example.X;`), resolving the descriptor loads `X` without
  initialising it. Each element is read by `readOrdinaryObject` and checked there under its own
  class name. An element's own descriptor also passes the new `readNonProxyDesc` check, unless it
  was already read earlier in the stream.

Verified on DirtyChai built from `fe3464b3740`, with no `"[B"` grant in either policy:

| Client policy | Result | Client-side initialiser runs |
|---|---|---|
| no `SerialObjectPermission "Payload"` | refused: `SerialObjectPermission "Payload"` | 0 |
| control: `SerialObjectPermission "Payload"` granted | resolved | 1 |

The JGDMS suites on the same build:

- jgdms-identity-it: 33 run, 0 failed.
- jgdms-pref-class-loader: 244 run, 0 failed, plus `ResolvePinnedPermissionTest`, 2 run, 0 failed.
- jgdms-jeri: 311 run, 1 failed, `SslServerSharedBudgetTest`, a CPU-budget timing test. It
  passed 3 of 3 runs on its own, so it is load on a 4-core machine, not the change.

## 6. Related observations (not part of this change)

- Policies recorded by polpAudit grant `SerialObjectPermission` to generated proxy class names such
  as `jdk.proxy6.$Proxy15` (a `.grantperm` file). These names depend on class-loading order and
  differ between runs, so such a grant is not portable. `readProxyDesc` checks the proxy class name.
- JGDMS's own Atomic and DER decoders have their own `DeSerializationPermission` checks. Whether
  any of them initialises a class before its check was not examined here.
- JGDMS side, same investigation: per-JAR digest grants named a non-existent
  `net.jini.loader.LoadClassPermission`, so they never let a JAR define classes on DirtyChai. Fixed
  in JGDMS `3be43210d`, which grants `au.zeus.jdk.authorization.guards.LoadClassPermission "ALLOW"`
  and adds `DigestGrantPermissionsTest`.
