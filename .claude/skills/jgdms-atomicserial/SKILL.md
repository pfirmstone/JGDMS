---
name: jgdms-atomicserial
description: How JGDMS @AtomicSerial deserialization works and the per-class private GetArg namespace rule that agents keep getting wrong. Load before writing, editing, or reviewing any @AtomicSerial class — especially a subclass whose (GetArg) constructor needs a field declared by a superclass. Covers the serialForm()/serialize(PutArg)/(GetArg)-constructor contract, why arg.get("superclassField") returns null (wrong namespace) and NPEs, the super(arg)-first fix recipe, and the Constrainable*Proxy sibling backlog. Triggers on "@AtomicSerial", "GetArg", "arg.get", "serialForm", "PutArg", "Constrainable*Proxy", "wrong namespace", "pre-super", GetArg NPE, "server is null" in a proxy constructor, or any AtomicSerial serialization work.
---

# JGDMS `@AtomicSerial` deserialization + the per-class private GetArg namespace

`@AtomicSerial` is JGDMS's deterministic, side-effect-free replacement for `java.io.Serializable`
(the DER/atomic wire stack, STD-006/STD-008). This skill exists because **one specific rule keeps
being violated by agents and humans alike**: in a class hierarchy, **each class has its own PRIVATE
`GetArg` namespace**. Reading a superclass's field from a subclass's `GetArg` returns `null` and
usually NPEs at teardown/deserialization. Read the rule, then the fix recipe.

## The `@AtomicSerial` contract (3 static/ctor members)

An `@AtomicSerial` class declares its wire fields and how to read/write them:

```java
@AtomicSerial
public class Foo extends Bar {
    private static final String X = "x";

    // 1. serialForm(): the wire fields THIS class contributes (name + type). This is the
    //    schema, digest-covered (Layer 1). It lists ONLY this class's own fields, never a
    //    superclass's — the superclass declares its own in its own serialForm().
    public static SerialForm[] serialForm() {
        return new SerialForm[]{ new SerialForm(X, SomeType.class) };
    }

    // 2. serialize(PutArg, Foo): write THIS class's fields. arg.writeArgs() commits the frame.
    public static void serialize(PutArg arg, Foo f) throws IOException {
        arg.put(X, f.x);
        arg.writeArgs();
    }

    private final SomeType x;

    // 3. (GetArg) constructor: read THIS class's fields; run super(arg) to read the superclass's.
    public Foo(GetArg arg) throws IOException, ClassNotFoundException {
        super(arg);                       // reads Bar's fields in Bar's frame
        this.x = arg.get(X, null, SomeType.class);   // reads Foo's field in Foo's frame
    }
}
```

Notes: field validation / invariant checks belong in the constructor (Layer 2 — behaviour), never
in `serialForm()` (Layer 1 — structure). Variants: `@AtomicExternal` (external form),
`@Stateless` (no instance fields — no serialForm/serialize needed).

## THE RULE: each class has its own private GetArg namespace

The `GetArg` passed to `Foo(GetArg)` resolves `arg.get(name)` **against `Foo`'s serialForm frame
only**. It CANNOT see `Bar`'s fields. `super(arg)` re-dispatches the SAME logical args to
`Bar(GetArg)`, whose `arg.get(name)` resolves against `Bar`'s frame.

Consequence: **you cannot read a superclass field via `arg.get("superField")` in a subclass.** It
resolves against the subclass frame (which doesn't declare it) → returns the default (usually
`null`). If you then use that `null` (e.g. pass it to a validator), you get an NPE — at
deserialization, often only exercised on a specific path (e.g. a proxy's `getAdmin()` at teardown),
so it stays latent until that path runs.

## The anti-pattern (BROKEN) and the fix (super-first)

**BROKEN** — reads the superclass `server` field pre-`super(arg)` from the wrong namespace → null:

```java
// ConstrainableAdminProxy extends AdminProxy; AdminProxy.serialForm() declares "server",
// ConstrainableAdminProxy.serialForm() declares only "constraints".
private static MethodConstraints checkConstraints(GetArg arg) throws ... {
    MethodConstraints c = arg.get("constraints", null, MethodConstraints.class);
    Registrar server = (Registrar) arg.get("server", null);   // WRONG frame -> null
    verifyConsistentConstraints(c, server);                   // NPE: server is null
    return c;
}
public ConstrainableAdminProxy(GetArg arg) throws ... {
    this(arg, checkConstraints(arg));   // checkConstraints runs BEFORE super(arg) populates server
}
```

**FIXED** — `super(arg)` first (populates the inherited field in the superclass frame), then check
against the inherited field:

```java
private static MethodConstraints check(GetArg arg, Registrar server) throws ... {
    MethodConstraints c = arg.get("constraints", null, MethodConstraints.class);
    // ... use the passed-in inherited server (now populated) ...
    verifyConsistentConstraints(c, server);
    return c;
}
public ConstrainableAdminProxy(GetArg arg) throws ... {
    super(arg);                       // AdminProxy(GetArg) reads/validates the inherited `server`
    this.constraints = check(arg, server);   // `server` is the inherited (populated) field
}
```

Recipe: (1) call `super(arg)` FIRST; (2) access the superclass field as the **inherited field**
(`server`), never via `arg.get("server")`; (3) assign the subclass's own `final` fields in the
constructor body after super. The inherited field must be visible (package-private/protected).
`ConstrainableRegistrarProxy` (fixed by commit `1491e2b80`) is the canonical reference — mirror it.

## The sibling backlog — check when touching any `Constrainable*Proxy`

The same pre-`super` anti-pattern was flagged across ~20 `Constrainable*Proxy` classes; only some
are fixed. Known-broken siblings include `ConstrainableEventLease`, `ConstrainableRegistration`, and
(pre-this-fix) `ConstrainableAdminProxy`. When editing or reviewing any `Constrainable*Proxy`,
verify its `(GetArg)` constructor calls `super(arg)` before reading any superclass-declared field.
See memory `jgdms-getarg-namespace-proxy-regression`.

## How to test an `@AtomicSerial` (GetArg) round-trip

Drive the real codec, not a hand-rolled GetArg — only the real machinery reproduces the per-class
namespace:

```java
ByteArrayOutputStream baos = new ByteArrayOutputStream();
ObjectOutputStream oos = new AtomicMarshalOutputStream(baos, null);  // Atomic-JOSS form
oos.writeObject(original); oos.flush();
ObjectInputStream ois = AtomicMarshalInputStream.create(
        new ByteArrayInputStream(baos.toByteArray()), null, false, null, null, false);
Object recovered = ois.readObject();   // exercises the (GetArg) constructor
```

Reference: `EventRegListenerDerRoundTripTest` (reggie-service). Tests that reconstruct via `GetArg`
need an SM-capable JDK with `-Djava.security.manager=allow` (they hit
`DeSerializationPermission("MARSHALL").checkGuard`) — already in the relevant modules' surefire
argLine.

## Related

- Layers: G5 in `jgdms-board-review` (structure on the wire vs behaviour in the constructor).
- Memory: `jgdms-getarg-namespace-proxy-regression`, `jgdms-atomicserial-phase0-halfmigrated`,
  `jgdms-der-std006`.
