# DirtyChai (OpenJDK 27-internal) — Known Bugs

## BUG-001 — `Subject.getSubject(AccessControlContext)` always throws `MissingResourceException`

### Status
Open — reproduced against `openjdk version "27-internal" 2026-09-15 (build 27-internal-adhoc.peter.DirtyChai)`.

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
