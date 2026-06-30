# DirtyChai (OpenJDK 27-internal) — Known Bugs

## BUG-001 — `Subject.getSubject(AccessControlContext)` always throws `MissingResourceException`

### Status
**Fixed** — current DirtyChai source (verified 2026-07-01) applies the restructured null-check
recommended below: the resource lookup now runs **only** when `acc == null`. Originally reproduced
against `openjdk version "27-internal" (build 27-internal-adhoc.peter.DirtyChai)`.

> **Resolution / current behaviour.** `Subject.getSubject(AccessControlContext)` (`Subject.java:338`)
> now reads:
> ```java
> public static Subject getSubject(final AccessControlContext acc) {
>     if (acc == null) {
>         throw new NullPointerException(
>             ResourcesMgr.getString("invalid.null.AccessControlContext.provided"));
>     }
>     return current();   // acc is null-checked, then IGNORED
> }
> ```
> A non-null `acc` no longer triggers the resource lookup, so there is no `MissingResourceException`.
> Note the method is now a **deprecated shim that ignores its `acc` argument and returns `current()`**
> (the scoped user subject) — it does **not** recover a subject captured in the `acc`. JGDMS code
> should prefer `Subject.current()`; the broad `catch (Exception)` workaround below is now harmless
> but unnecessary.

### Affected method
`javax.security.auth.Subject.getSubject(AccessControlContext acc)` — `java.base` module.

### Root cause
The method performs a null-check on the `acc` argument using `Objects.requireNonNull(acc, message)`,
where the message is fetched **unconditionally** from the security resource bundle:

```java
// Subject.getSubject — bytecode-reconstructed source (Subject.java ~line 342)
String msg = ResourcesMgr.getString("invalid.null.AccessControlContext.provided");
Objects.requireNonNull(acc, msg);   // only meaningful when acc == null
```

The resource key `"invalid.null.AccessControlContext.provided"` is **not present** in
DirtyChai's `sun.security.util.resources.security` resource bundle.  Because the lookup is
unconditional — it runs even when `acc` is non-null — `ResourcesMgr.getString` throws
`java.util.MissingResourceException` on **every call**, before the method does any useful work.

### Consequence
Any JGDMS code that calls `Subject.getSubject(acc)` with a legitimately non-null ACC (obtained
from `AccessController.getContext()`) will receive a `MissingResourceException` instead of the
expected `Subject` (or `null` for an unauthenticated context).

This breaks TLS server-endpoint subject resolution (`SslServerEndpointImpl.resolveSubjectIfNeeded`)
and TLS client-endpoint subject resolution (`SslEndpointImpl.getCallContext`), causing:

- `UnsupportedConstraintException: Server credentials unsuitable for TLS` on the server side.
- `UnsupportedConstraintException: Client must be logged on and caller must do as` on the client side.

### Confirmed behaviour
The following bytecode sequence was observed in the DirtyChai `java.base.jmod`
(`Subject.class`, method `getSubject`, offsets 15–21):

```
15: aload_0                                         // push acc (non-null)
16: ldc "invalid.null.AccessControlContext.provided" // push resource key
18: invokestatic  ResourcesMgr.getString(String)    // THROWS MissingResourceException
21: invokestatic  Objects.requireNonNull(Object, String)  // never reached
```

`AccessController.getContext()` itself is **not** the problem — it never returns `null`
(when the stack context is null it constructs and returns a valid empty ACC).

### Workaround applied in JGDMS
All JGDMS call sites that invoke `Subject.getSubject(acc)` wrap it in a broad
`catch (Exception e) { return null; }` guard so that `MissingResourceException` is silently
treated as "no subject available", matching the semantics of the method returning `null`:

```java
// SslServerEndpointImpl.resolveSubjectIfNeeded / SslEndpointImpl.getCallContext
try {
    AccessControlContext acc = AccessController.getContext();
    if (acc != null) {
        Subject s = Subject.getSubject(acc);
        // use s ...
    }
} catch (SecurityException | IllegalArgumentException e) {
    // ignore — no subject
} catch (Exception e) {
    // DirtyChai BUG-001: Subject.getSubject always throws MissingResourceException
    // due to missing resource key "invalid.null.AccessControlContext.provided".
    // Treat as "no ACC-derived subject available".
}
```

### Fix required in DirtyChai
Add the missing resource entry to the security resource bundle, e.g. in
`src/java.base/share/classes/sun/security/util/resources/security.properties`:

```properties
invalid.null.AccessControlContext.provided=invalid null AccessControlContext provided
```

Alternatively, restructure the null-check to avoid the unconditional resource lookup:

```java
if (acc == null) {
    throw new NullPointerException(
        ResourcesMgr.getString("invalid.null.AccessControlContext.provided"));
}
```

### Fix required in DirtyChai — repository pointer
File to patch: `src/java.base/share/classes/javax/security/auth/Subject.java`, method `getSubject`.
Resource bundle: `src/java.base/share/classes/sun/security/util/resources/security.properties` (or `.java`).

---

## BUG-002 — `AccessControlContext.optimize()` throws `ArrayIndexOutOfBoundsException` on an empty assigned context

### Status
**Fixed — 2026-07-01** (guard added: `acc.context.length > 0`). A latent **inherited** OpenJDK
defect exposed by DirtyChai's `ScopedValue` subject-propagation rework; never reachable on stock
OpenJDK.

### Affected method
`java.security.AccessControlContext.optimize()` — `java.base` module. Reached from
`AccessController.getContext()` → `optimize()`, i.e. from any permission check under an installed
`SecurityManager`.

### Root cause
`optimize()` carried an old OpenJDK shortcut that dereferences `acc.context[0]` without a length
check:

```java
// before fix (AccessControlContext.java ~line 766)
if ((slen == 1) && (context[0] == acc.context[0])) { ... }   // acc.context may be length 0
```

Upstream OpenJDK never reached this with an **empty** assigned context, because `Subject.doAs`/
`doAsPrivileged` attach a `SubjectDomainCombiner` to the ACC, so `optimize()` takes the combiner
branch instead. DirtyChai carries the user subject on a `ScopedValue` and folds at `getContext()`
(no combiner retained on the ACC), so the non-combiner branch is taken; and
`Subject.doAsPrivileged(subject, action, null)` builds an **empty** assigned context
(`NULL_PD_ARRAY`). The first permission check inside the action (e.g. `Subject.current()` →
`checkPermission` → `getContext` → `optimize`) then dereferenced `context[0]` on a zero-length
array.

### Consequence
Under an installed `SecurityManager`, `Subject.doAsPrivileged(subject, action, null)` crashed with
`ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0` (a fail-stop, **not** a
`SecurityException`) whenever the `doPrivileged`-truncated stack had exactly one domain and the
action performed a permission check. Every JGDMS `doAsPrivileged(…, null)` call site (Browser,
DestroySharedGroup, ServiceStarter, AbstractActivationGroup, Activation) was affected. Surefire did
not catch it (no SM installed there); it only surfaced under jtreg with a real SM.

### Fix applied in DirtyChai
Guard the shortcut with a length check (stricter than upstream's own `assigned != null` test,
which would still miss a non-null but empty array):

```java
// AccessControlContext.java:766 (fixed)
if ((slen == 1) && acc.context.length > 0 && (context[0] == acc.context[0])) { ... }
```

An empty assigned context falls through to the general combine path, which already handles
`acc.context.length == 0`.

### Regression
`qa/jtreg/org/apache/river/api/security/doAsPrivNullAcc/DoAsPrivilegedNullAccTest`
(`-Djava.security.manager=default`): `doAsPrivileged(plain, Subject::current, null)` standalone and
nested in `callAs(USER)` — both green, no AIOOBE, user replaced.

### Repository pointer
File: `src/java.base/share/classes/java/security/AccessControlContext.java`, method `optimize()`
(~line 766). Background: DirtyChai `SECURITY_MODEL.md` §10.4 (ScopedValue fold vs upstream
combiner-on-ACC).
