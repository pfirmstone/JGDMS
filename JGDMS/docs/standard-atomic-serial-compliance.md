# JGDMS-STD-001 — AtomicSerial Compliance Standard

**Version:** 1.0  
**Status:** Active  
**Package:** `org.apache.river.api.io.AtomicSerial`  
**Scope:** All classes that are `Serializable` or annotated `@AtomicSerial` in JGDMS and
downstream codebases.

---

## Purpose

This standard defines the **mandatory structure** that every `@AtomicSerial` class must
follow in order to be accepted as safe by the JGDMS Bytecode Analysis Engine (BAE).
It is the machine-checkable contract that the `AtomicSerialComplianceVisitor` enforces
at bytecode level.

A class that violates any rule in this standard will receive a non-`COMPLIANT`
`AtomicSerialVerdict` from the BAE, causing the `JarAnalysisReport` to derive a
`VerdictType` of `DANGEROUS` — meaning the codebase will be refused by clients.

---

## Motivation — Why not plain `Serializable`?

Standard Java Serialization has three well-known attack surfaces:

1. **Arbitrary-class instantiation** — an attacker can supply a serial stream that
   causes any `Serializable` class on the classpath (including `ClassLoader`) to be
   instantiated without calling a normal constructor.
2. **Denial of service** — manipulated streams can trigger `OutOfMemoryError` before
   any application-level filter runs.
3. **Pre-construction invariant violation** — object fields are set after the default
   constructor runs, so `readObject` sees a half-initialised object; a stolen reference
   can escape before invariants are checked.

`@AtomicSerial` fixes all three by making invariant checking **mandatory before the
object is constructed**, not after.

---

## Definitions

| Term | Meaning |
|------|---------|
| `GetArg` | `org.apache.river.api.io.AtomicSerial.GetArg` — the read-only view of the serial stream passed to the deserialization constructor. |
| `PutArg` | `org.apache.river.api.io.AtomicSerial.PutArg` — the write view used during serialization. |
| `SerialForm` | `org.apache.river.api.io.AtomicSerial.SerialForm` — an `ObjectStreamField` subclass used to declare the serial schema. |
| check method | A `private static` (or `package-private static`) method that validates deserialized state **before** the object is constructed. |
| bridge constructor | A `private` constructor whose signature includes `GetArg` plus the return type of the check method, used to thread validated state from the check method into the final field assignments. |

---

## Rules

### RULE-1 — Annotation (`NOT_ANNOTATED` verdict if violated)

Every class with a `(GetArg)` constructor **must** be annotated with `@AtomicSerial`.
A `(GetArg)` constructor without the annotation is suspicious and indicates an
incomplete migration.

```java
@AtomicSerial
public final class MyClass implements Serializable { ... }
```

### RULE-2 — GetArg constructor (`MISSING_CONSTRUCTOR` verdict if violated)

Every `@AtomicSerial` class **must** declare a public constructor whose sole parameter
is `GetArg`:

```java
public MyClass(GetArg arg) throws IOException {
    this(arg, check(arg));   // check before bridge ctor
}
```

> **AI-agent note:** `GetArg` is the type `org.apache.river.api.io.AtomicSerial.GetArg`.
> The constructor descriptor is `(Lorg/apache/river/api/io/AtomicSerial$GetArg;)V`.

### RULE-3 — Validation before construction (`VALIDATION_ORDER` verdict if violated)

Inside the `(GetArg)` constructor, a **static check method must be called before the
superclass constructor** (`this(...)` or `super(...)`).  The static call provides
validated data to the bridge constructor so that invariants are confirmed before any
field is set.

**Correct pattern (most common):**

```java
public MyClass(GetArg arg) throws IOException {
    this(arg, check(arg));      // INVOKESTATIC check before INVOKESPECIAL this
}

private MyClass(GetArg arg, boolean checked) throws IOException {
    this.value = arg.get("value", null, String.class);
}

private static boolean check(GetArg arg) throws IOException {
    String v = arg.get("value", null, String.class);
    if (v == null) throw new InvalidObjectException("value must not be null");
    return true;
}
```

**Alternative (check method returns the validated GetArg):**

```java
public MyClass(GetArg arg) throws IOException {
    super(check(arg));           // INVOKESTATIC check passes GetArg to super
}

private static GetArg check(GetArg arg) throws IOException {
    if (arg.get("id", 0L) == 0) throw new InvalidObjectException("invalid id");
    return arg;
}
```

The BAE `GetArgCtorAnalyzer` accepts any pattern where `INVOKESTATIC` precedes the
first `INVOKESPECIAL <init>` in the `(GetArg)` constructor bytecode.

### RULE-4 — Typed GetArg access (`UNTYPED_GET` verdict if violated)

Every `Object`-returning `GetArg.get` call in the static check method **must** be
followed by a type assertion before the reference is stored or tested.  Two safe
patterns are accepted:

**Pattern A — 3-argument typed form (preferred):**

```java
String host = arg.get("host", null, String.class);
// GetArg internally throws InvalidObjectException if the field is not a String
```

**Pattern B — 2-argument form with immediate CHECKCAST:**

```java
String[] urls = (String[]) arg.get("urls", null);
// Explicit cast in the check method — CHECKCAST fires before construction
```

**Anti-pattern (detected as `UNTYPED_GET`):**

```java
Object o = arg.get("value", null);   // 2-arg form, result stored untyped
if (o == null) throw ...;            // IFNULL without preceding CHECKCAST
// Type is never verified in the static method — CCE deferred to bridge ctor
```

**Anti-pattern for subclass `Object`-typed superclass fields:**

```java
MyProxy p = new MyProxy(arg, true);   // bridge ctor builds private copy
if (p.server == null) throw ...;      // GETFIELD on Object-typed field + IFNULL
// Type never checked — should use instanceof or CHECKCAST first
```

Safe alternatives:

```java
if (!(p.server instanceof ExpectedType)) throw ...;   // INSTANCEOF — safe
ExpectedType t = (ExpectedType) p.server;             // CHECKCAST — safe
```

### RULE-5 — Serial form declaration (`MISSING_SERIAL_FORM` verdict if violated)

Every `@AtomicSerial` class that declares **at least one non-static, non-transient
instance field** must provide:

```java
public static SerialForm[] serialForm() {
    return new SerialForm[] {
        new SerialForm("fieldName", FieldType.class),
        ...
    };
}

@SuppressWarnings("unused")
private static final ObjectStreamField[] serialPersistentFields = serialForm();
```

Classes with no serialized state (e.g. stateless proxy subclasses that add no new
fields beyond their parent) are exempt from this rule; `serialForm()` is inherited.

> **AI-agent note:** The BAE `visitField` hook tracks `hasNonStaticInstanceFields`.
> `MISSING_SERIAL_FORM` is only issued when that flag is `true` and `serialForm()` is
> absent.

### RULE-6 — Serialization method

Every `@AtomicSerial` class should provide a public static `serialize` method with the
following signature:

```java
public static void serialize(PutArg arg, MyClass obj) throws IOException {
    arg.put("fieldName", obj.fieldName);
    arg.writeArgs();
}
```

This method is the **only** place where field values are written to the serial stream.
Omitting it is not currently flagged as `DANGEROUS` by the BAE but is strongly
discouraged — it leaves the serialization path unsecured.

---

## Verdict Reference

| Verdict | Meaning | BAE disposition |
|---------|---------|-----------------|
| `COMPLIANT` | All rules satisfied | Counts toward `SAFE` quorum |
| `NOT_ANNOTATED` | `(GetArg)` ctor present, annotation missing | `INCONCLUSIVE` |
| `MISSING_CONSTRUCTOR` | `@AtomicSerial` present, no `(GetArg)` ctor | `DANGEROUS` |
| `VALIDATION_ORDER` | Static check not before bridge ctor | `DANGEROUS` |
| `MISSING_SERIAL_FORM` | Instance fields declared, no `serialForm()` | `DANGEROUS` |
| `UNTYPED_GET` | Object field accessed without type-check in check method | `DANGEROUS` |
| `NA` | Class is not `Serializable` (or is `@Stateless`) | Not counted |

---

## Complete Compliant Example

```java
@AtomicSerial
public final class ServiceDescriptor implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String SERVICE_ID = "serviceId";
    private static final String HOST       = "host";
    private static final String PORT       = "port";

    @SuppressWarnings("unused")
    private static final ObjectStreamField[] serialPersistentFields = serialForm();

    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm(SERVICE_ID, String.class),
            new SerialForm(HOST,       String.class),
            new SerialForm(PORT,       Integer.TYPE)
        };
    }

    public static void serialize(PutArg arg, ServiceDescriptor d) throws IOException {
        arg.put(SERVICE_ID, d.serviceId);
        arg.put(HOST,       d.host);
        arg.put(PORT,       d.port);
        arg.writeArgs();
    }

    private final String serviceId;
    private final String host;
    private final int    port;

    // RULE-2 + RULE-3: (GetArg) ctor calls static check first
    public ServiceDescriptor(GetArg arg) throws IOException {
        this(arg, check(arg));
    }

    private ServiceDescriptor(GetArg arg, boolean checked) throws IOException {
        serviceId = arg.get(SERVICE_ID, null, String.class);  // RULE-4: typed
        host      = arg.get(HOST,       null, String.class);  // RULE-4: typed
        port      = arg.get(PORT,       0);                   // primitive, safe
    }

    // RULE-3: static check method validates before any field is set
    private static boolean check(GetArg arg) throws IOException {
        String id   = arg.get(SERVICE_ID, null, String.class);  // typed 3-arg
        String host = arg.get(HOST,       null, String.class);  // typed 3-arg
        int    port = arg.get(PORT,       0);
        if (id == null || id.isEmpty())
            throw new InvalidObjectException("serviceId must not be null or empty");
        if (host == null || host.isEmpty())
            throw new InvalidObjectException("host must not be null or empty");
        if (port < 1 || port > 65535)
            throw new InvalidObjectException("port out of range: " + port);
        return true;
    }

    // Standard constructor
    public ServiceDescriptor(String serviceId, String host, int port) {
        if (serviceId == null || serviceId.isEmpty())
            throw new IllegalArgumentException("serviceId");
        if (host == null || host.isEmpty())
            throw new IllegalArgumentException("host");
        if (port < 1 || port > 65535)
            throw new IllegalArgumentException("port out of range: " + port);
        this.serviceId = serviceId;
        this.host      = host;
        this.port      = port;
    }
}
```

---

## Known Compliant Patterns in JGDMS

The following table shows real JGDMS classes and the specific patterns they exemplify.
AI agents and developers can use them as authoritative references.

| Class | Pattern exercised |
|-------|-------------------|
| `au.net.zeus.jgdms.api.codebase.AnalysisRequest` | Pack200-compressed serial field; `check(GetArg)` returns `byte[]` (not `boolean`) and is passed directly to bridge ctor |
| `au.net.zeus.jgdms.api.codebase.SignedVerdict` | `(String[]) arg.get(2-arg)` with explicit CHECKCAST; typed 3-arg for `VerdictType` |
| `au.net.zeus.jgdms.api.codebase.CrashReport` | Multiple typed fields; `byte[]` signature retrieved with explicit cast |
| `net.jini.id.Uuid` | Check method reads only primitive `long` fields; standard `(GetArg, boolean)` bridge |
| `net.jini.core.lookup.ServiceID` | Two private static helpers (`mostSig`, `leastSig`) each return `long`; no object-type access |
| `net.jini.core.event.EventRegistration` | `arg.get("source", null)` stored to local via `ASTORE` before null-check — `pendingUntypedGet` reset |
| `net.jini.core.lookup.ServiceEvent` | `check(GetArg)` returns `GetArg` (not `boolean`); `super(check(arg))` pattern |
| `au.net.zeus.jgdms.bae.proxy.BytecodeAnalysisEngineProxy` | `instanceof` check on inherited `Object server` field before any branch |

---

## Known Detector Boundary Conditions

AI agents generating or reviewing `@AtomicSerial` classes should be aware of the
following bytecode-level behaviours in the BAE detector:

1. **`ASTORE` resets `pendingUntypedGet`** — if a 2-arg `GetArg.get` result is stored
   to a local variable before a conditional, the flag is cleared.  Only a
   `IFNULL`/`IFNONNULL` immediately after the `get` call (with no intervening
   instruction) is flagged.

2. **Check method return type** — `CheckMethodAnalyzer` is only applied to static
   methods whose descriptor ends with `)Z` (returns `boolean`).  A check method
   returning `GetArg` or another type bypasses the UNTYPED_GET analysis.

3. **INVOKEVIRTUAL-only constructor chain** — if the `(GetArg)` constructor routes
   entirely through `INVOKEVIRTUAL` primitive getters (like `arg.get("ttl", -1)`)
   followed by `this(int)` with no `INVOKESTATIC`, `getArgCtorValidationOk` is `false`
   → `VALIDATION_ORDER`.  The validation must be visible at the `(GetArg)` constructor
   level to satisfy RULE-3.

---

*Document maintained alongside `AtomicSerialComplianceVisitor.java` and
`AtomicSerialComplianceVisitorTest.java`.*
