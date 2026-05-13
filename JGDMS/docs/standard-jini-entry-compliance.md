# JGDMS-STD-005 — SerialEntry Compliance Standard

**Version:** 1.0  
**Status:** Active  
**Package:** `net.jini.core.entry`  
**Scope:** All `Entry` classes that adopt the
`@SerialEntry` annotation in JGDMS and downstream codebases.

---

## Purpose

This standard defines the **mandatory structure** that every
`@SerialEntry` class must follow in order to be recognised correctly by
JGDMS lookup-service clients and registrars.

A class that violates these rules will either fail to be registered, fail
to be retrieved, or produce a hash mismatch that silently prevents stored
entries from being found.

---

## Motivation — Why not plain `Entry`?

The standard Jini Entry type-identity mechanism derives a 64-bit SHA-1
hash from live reflection over `Class.getFields()`.  Any change to the
Java source of the Entry class — even a pure rename — silently breaks the
hash and makes all previously stored entries invisible.  There is no
compile-time or test-time error; the failure only manifests in production
as "entries not found".

`@SerialEntry` solves this by:

| Problem | Solution |
|---------|---------|
| Hash derived from Java field names | Hash derived from developer-controlled wire names |
| No invariant check at deserialization | Static `check()` before bridge constructor |
| `final` fields unsafe (pre-JMM `Field.set()` hazard) | Fields assigned in real constructor → JMM freeze action applies |
| SHA-1 may be removed from future JDKs | SHA-256 used for all `@SerialEntry` hashes |
| No translation point for wire schema | Static `serialize()` method provides translation |

---

## Definitions

| Term | Meaning |
|------|---------|
| `SerialEntry` | `net.jini.core.entry.SerialEntry` — the runtime annotation. |
| `EntryWireField` | `net.jini.core.entry.EntryWireField` — a single named typed wire field. |
| `GetEntryArg` | `net.jini.core.entry.GetEntryArg` — read-only named-field view for the deserialization constructor. |
| `PutEntryArg` | `net.jini.core.entry.PutEntryArg` — write-only named-field collector for the serialization method. |
| wire name | The string passed to `EntryWireField(name, type)`, used in the SHA-256 hash and in `PutEntryArg.put(name, ...)` / `GetEntryArg.get(name, ...)`. |
| check method | A `private static` (or `package-private static`) method that validates deserialized field values **before** the object is constructed. |
| bridge constructor | A `private` constructor that accepts `GetEntryArg` plus the return type of the check method, used to assign validated values to fields. |

---

## Rules

### RULE-1 — Annotation

Every class with a `(GetEntryArg)` constructor **must** be annotated
`@SerialEntry`.  A `(GetEntryArg)` constructor without the annotation is
suspicious and indicates an incomplete migration.

```java
@SerialEntry
public final class Location implements Entry { ... }
```

---

### RULE-2 — `entryForm()` method

Every `@SerialEntry` class **must** declare a public static method with
the exact signature:

```java
public static EntryWireField[] entryForm() { ... }
```

Requirements:

- The returned array must not be `null` and must have at least one element.
- Each element must be non-`null`.
- Wire names must be unique within the array.
- The array must be **stable** across JVM restarts: the hash is computed
  from it, so any change to a wire name or type is a breaking change with
  the same semantics as renaming a Java field in a plain `Entry`.
- Wire names are developer-defined strings; they do **not** need to match
  the Java field names.
- `entryForm()` declares the fields of **this class only** (not inherited
  fields); the superclass hash is included separately.

---

### RULE-3 — `(GetEntryArg)` constructor

Every `@SerialEntry` class **must** declare a public constructor whose
sole parameter is `GetEntryArg`:

```java
public Location(GetEntryArg arg) throws IOException {
    this(arg, check(arg));   // static check BEFORE bridge constructor
}
```

- The first statement must be an `INVOKESTATIC` call to the static check
  method, whose return value is threaded into the bridge constructor.
- This ensures that invariants are validated **before** any field is
  assigned.

---

### RULE-4 — Static check method before bridge constructor

Inside the `(GetEntryArg)` constructor, a **static check method must be
called before the bridge constructor** (`this(...)`).

**Correct pattern:**

```java
public Location(GetEntryArg arg) throws IOException {
    this(arg, check(arg));      // ← static check first
}

private Location(GetEntryArg arg, boolean checked) throws IOException {
    host  = arg.get("host",  null, String.class);
    floor = arg.get("floor", null, Integer.class);
}

private static boolean check(GetEntryArg arg) throws IOException {
    String host = arg.get("host", null, String.class);
    if (host == null || host.isEmpty())
        throw new InvalidObjectException("host must not be null or empty");
    return true;
}
```

- The check method signature must return a type (typically `boolean`) that
  is used as the second parameter of the bridge constructor.
- `GetEntryArg.get()` must use the **3-argument typed form** for any
  non-primitive reference field:
  `arg.get("fieldName", defaultValue, Type.class)`.

---

### RULE-5 — Bridge constructor

The bridge constructor receives `GetEntryArg` plus the check method's
return value and **assigns all fields**:

```java
private Location(GetEntryArg arg, boolean checked) throws IOException {
    host  = arg.get("host",  null, String.class);
    floor = arg.get("floor", null, Integer.class);
}
```

- Fields may be `public final` in a `@SerialEntry` class; they are
  assigned here, inside a real constructor, satisfying the JMM §17.5
  freeze action.
- The bridge constructor must be `private` (not accessible to external
  code).

---

### RULE-6 — `serialize()` method

Every `@SerialEntry` class **must** declare a public static method with
the signature:

```java
public static void serialize(PutEntryArg arg, T obj) throws IOException
```

where `T` is the annotated class.

Requirements:

- Every wire name declared in `entryForm()` must appear in exactly one
  `arg.put(name, value)` call.
- `arg.writeArgs()` must be called exactly once, at the end of the method.
- The values written must correspond to the wire names in `entryForm()`.

Example:

```java
public static void serialize(PutEntryArg arg, Location loc)
        throws IOException {
    arg.put("host",  loc.host);
    arg.put("floor", loc.floor);
    arg.writeArgs();
}
```

---

### RULE-7 — Hash stability

The SHA-256 hash of a `@SerialEntry` class is computed from:

1. The 64-bit hash of the superclass `EntryClass` (if the direct
   superclass is not `Object`).
2. The fully qualified class name (UTF-8).
3. For each `EntryWireField` in `entryForm()` order: the wire name
   (UTF-8) then the wire type name (UTF-8).

**Breaking changes** (change the hash):

- Changing a wire name in `entryForm()`.
- Changing a wire field type in `entryForm()`.
- Adding or removing a field from `entryForm()`.
- Renaming the Entry class or moving it to a different package.
- Changing any ancestor's hash.

**Wire-stable / source-compatible changes** (do not change the hash):

- Adding or removing constructors, methods, or private fields.
- Changing the implementation of `check()` or `serialize()` without
  altering the wire names or types.

**Important caveat — public field renames break binary compatibility:**

Because all Jini Entry wire-schema fields are declared `public`, renaming
a Java field (e.g. `host` → `hostname`) does **not** change the SHA-256
hash when the wire name in `entryForm()` is left unchanged, but it
**does** break binary compatibility: any pre-compiled class that accesses
`entry.host` directly will fail to link after the rename.  This applies
to `public final` fields in `@SerialEntry` classes just as much as to the
legacy mutable fields of plain `Entry` classes.

The correct interpretation is therefore:

| Change | Hash impact | Binary-compat impact |
|--------|-------------|---------------------|
| Rename Java field, keep wire name | None | **Breaking** |
| Rename Java field, rename wire name | Breaking | Breaking |
| Reorder Java fields (not wire order) | None | None |
| Add new wire field to `entryForm()` | Breaking | None |
| Remove wire field from `entryForm()` | Breaking | **Breaking** |
| Move class to different package | Breaking | Breaking |

In practice, once an Entry class is deployed the safest evolution strategy
is to extend it rather than modify it in place.

---

### RULE-8 — Backward-compatible field addition

To add a Java field that participates in matching (i.e. is stored in the
registrar), append a new `EntryWireField` to `entryForm()`.  This
**changes** the hash, creating a new distinct type.

To add a computed or defaulted Java field that does **not** affect
matching (and does not change the hash), populate it in the bridge
constructor from existing wire fields or a constant — do **not** add it
to `entryForm()`.

---

### RULE-9 — Default value handling

When reading fields from a stored entry that was written by an older
version of the class:

- `GetEntryArg.get(name, defaultValue, Type.class)` returns
  `defaultValue` when the field is absent.
- `GetEntryArg.defaulted(name)` returns `true` when the field was absent.

Every `GetEntryArg.get()` call in the `check()` method and bridge
constructor **must** supply a meaningful `defaultValue` (typically
`null`), not throw an unchecked exception on absence.

---

### RULE-10 — `final` fields are permitted

`@SerialEntry` classes may declare their wire-schema fields `public final`.
Because the fields are assigned inside the bridge constructor (a real
constructor), the JMM §17.5 freeze action fires at the end of that
constructor, guaranteeing safe publication without additional
synchronization.

Legacy `Entry` classes may **not** use `final` for wire fields (they are
populated via `Field.set()` after the no-arg constructor, which does not
provide freeze-action semantics).

---

## Canonical example

```java
@SerialEntry
public final class Location implements Entry {

    // ── Wire schema ────────────────────────────────────────────────────
    public static EntryWireField[] entryForm() {
        return new EntryWireField[] {
            new EntryWireField("host",  String.class),
            new EntryWireField("floor", Integer.class),
            new EntryWireField("room",  String.class)
        };
    }

    // ── Java fields (may be final) ─────────────────────────────────────
    public final String  host;
    public final Integer floor;
    public final String  room;

    // ── Deserialization entry point ────────────────────────────────────
    public Location(GetEntryArg arg) throws IOException {
        this(arg, check(arg));
    }

    // ── Bridge constructor — fields assigned here (JMM freeze applies) ─
    private Location(GetEntryArg arg, boolean checked) throws IOException {
        host  = arg.get("host",  null, String.class);
        floor = arg.get("floor", null, Integer.class);
        room  = arg.get("room",  null, String.class);
    }

    // ── Invariant validation ───────────────────────────────────────────
    private static boolean check(GetEntryArg arg) throws IOException {
        if (arg.get("host", null, String.class) == null)
            throw new InvalidObjectException("host must not be null");
        return true;
    }

    // ── Serialization ─────────────────────────────────────────────────
    public static void serialize(PutEntryArg arg, Location loc)
            throws IOException {
        arg.put("host",  loc.host);
        arg.put("floor", loc.floor);
        arg.put("room",  loc.room);
        arg.writeArgs();
    }

    // ── Legacy no-arg constructor (for interop with non-@SerialEntry
    //    registrars, if required) ───────────────────────────────────────
    public Location() {
        host  = null;
        floor = null;
        room  = null;
    }
}
```

---

## Relationship to existing standards

| Standard | Relationship |
|----------|-------------|
| JGDMS-STD-001 — AtomicSerial Compliance | `@SerialEntry` reuses the same `check()`-before-bridge-constructor idiom.  Developers familiar with `@AtomicSerial` will recognise the pattern immediately. |
| JGDMS-STD-002 — Bytecode Analysis Engine | A future `SerialEntryComplianceVisitor` can enforce these rules at bytecode level (analogous to `AtomicSerialComplianceVisitor`). |
| JGDMS-STD-004 — Policy File Syntax | No direct relationship. |
| `net.jini.core.entry.Entry` | `@SerialEntry` is an extension, not a replacement.  The `Entry` marker interface is still required. |
| `net.jini.entry.AbstractEntry` | `AbstractEntry.fieldInfo()` includes `final` fields for `@SerialEntry` instances, enabling `equals()`, `hashCode()`, and `toString()` to work correctly. |
| Java records | See dedicated section below. |

---

## Java records and `@SerialEntry`

Java 16+ records are natural candidates for `@SerialEntry` implementations
because:

- **Immutable components** — record components are implicitly `final`,
  which is explicitly permitted and recommended for `@SerialEntry` classes
  (RULE-10).
- **No spurious fields** — a record can only expose its declared components
  as public members, preventing accidental addition of mutable `public`
  fields.
- **Canonical constructor** — a record's canonical constructor assigns all
  components atomically, satisfying the JMM §17.5 freeze action without
  any extra effort.
- **`equals`, `hashCode`, `toString`** — records provide these for free
  over their components, consistent with `AbstractEntry`'s semantics.

### Constraints and workarounds

| Record limitation | `@SerialEntry` workaround |
|-------------------|--------------------------|
| The canonical constructor signature is fixed (`(T1 c1, T2 c2, …)`) — it cannot accept `GetEntryArg`. | Provide a compact canonical constructor for normal construction, plus a separate `public RecordEntry(GetEntryArg arg)` constructor that delegates: `this(arg.get("c1", null, T1.class), …)`. |
| A record cannot extend another class (`extends AbstractEntry`). | Implement `Entry` directly; provide `equals()`, `hashCode()`, and `toString()` either explicitly or via the default record implementations. |
| The legacy no-arg constructor required by plain `Entry` registrars cannot be added to a record. | If legacy interop is not required, omit the no-arg constructor; the `(GetEntryArg)` constructor is sufficient for `@SerialEntry`-aware registrars. |

### Minimal record example

```java
@SerialEntry
public record LocationRecord(String host, Integer floor) implements Entry {

    // ── Wire schema ─────────────────────────────────────────────────────
    public static EntryWireField[] entryForm() {
        return new EntryWireField[] {
            new EntryWireField("host",  String.class),
            new EntryWireField("floor", Integer.class),
        };
    }

    // ── Deserialization constructor ──────────────────────────────────────
    public LocationRecord(GetEntryArg arg) throws IOException {
        this(Objects.requireNonNull(arg.get("host",  null, String.class), "host must not be null"),
             arg.get("floor", null, Integer.class));
    }

    // ── Serialization ────────────────────────────────────────────────────
    public static void serialize(PutEntryArg arg, LocationRecord r)
            throws IOException {
        arg.put("host",  r.host());
        arg.put("floor", r.floor());
        arg.writeArgs();
    }
}
```

> **Note:** Invariant validation for a record should be performed inside
> the arguments to the delegating `this(…)` call (e.g. with
> `Objects.requireNonNull`), so that the check runs *before* component
> assignment rather than after the object has already been constructed.

---

## References

| Source | Location in JGDMS |
|--------|-------------------|
| Design analysis that motivated this standard | `JGDMS/docs/standard-jini-entry-evolution.md` |
| `@SerialEntry` annotation | `jgdms-platform/src/main/java/net/jini/core/entry/SerialEntry.java` |
| `EntryWireField` | `jgdms-platform/src/main/java/net/jini/core/entry/EntryWireField.java` |
| `GetEntryArg` | `jgdms-platform/src/main/java/net/jini/core/entry/GetEntryArg.java` |
| `PutEntryArg` | `jgdms-platform/src/main/java/net/jini/core/entry/PutEntryArg.java` |
| `EntryClass.computeSerialEntryHash()` | `services/reggie/reggie-dl/src/main/java/org/apache/river/reggie/proxy/EntryClass.java` |
| `ClassMapper.toEntryClassBase()` | `services/reggie/reggie-dl/src/main/java/org/apache/river/reggie/proxy/ClassMapper.java` |
| `EntryRep.fieldsViaSerialEntry()` | `services/reggie/reggie-dl/src/main/java/org/apache/river/reggie/proxy/EntryRep.java` |
| `EntryRep.getViaSerialEntry()` | `services/reggie/reggie-dl/src/main/java/org/apache/river/reggie/proxy/EntryRep.java` |
| `AbstractEntry.fieldInfo()` | `jgdms-lib-dl/src/main/java/net/jini/entry/AbstractEntry.java` |

---

*Standard JGDMS-STD-005 — Version 1.0 — May 2026*
