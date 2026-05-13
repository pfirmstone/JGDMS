# Updating the Jini Entry Standard
## Lessons from `@AtomicSerial` for Entry Evolution Compatibility

**Version:** 1.0  
**Status:** Design Proposal  
**Author:** AI Agent (based on codebase analysis, May 2026)  
**Scope:** `net.jini.core.entry.Entry`, `net.jini.entry.AbstractEntry`,
`org.apache.river.reggie.proxy.EntryClass`,
`org.apache.river.reggie.proxy.ClassMapper`,
`org.apache.river.api.io.AtomicSerial`

---

## 1. The Problem

Jini `Entry` implementations are notoriously fragile to evolve after deployment.
A service registrar stores entries as opaque serialised blobs alongside a 64-bit
**type hash** that uniquely identifies the Entry class.  Any deployed service
client or space implementation that holds a reference to stored entries will stop
finding them — silently — the moment an incompatible change is made to the
Entry class, even one that appears superficially harmless.

The current evolution rules documented in `net.jini.core.entry.Entry` are
correct, but they say only what *not* to do; they give developers no positive
path forward:

```
// From Entry.java — current advisory text
// New public non-final fields added after deployment must be appended
// below existing fields to avoid breaking backward compatibility.
// If an Entry is extended, new fields cannot be added to the parent class.
// Changing field order breaks backward compatibility.
// Removing fields breaks backward compatibility.
```

This document analyses *why* these constraints exist by tracing the Entry type
identity algorithm to its source code, then describes how the `@AtomicSerial`
serialization framework in JGDMS already solves the analogous problem for
ordinary serializable objects, and proposes concrete improvements to the Entry
infrastructure that apply those same techniques.

---

## 2. How Entry Type Identity Works Today

### 2.1 The SHA-1 Type Hash — `EntryClass.computeHash()`

Every Entry class is identified at runtime by a 64-bit hash computed in
`org.apache.river.reggie.proxy.EntryClass.computeHash()`:

```java
// EntryClass.java lines 311–338
private long computeHash(ClassMapper.EntryField[] fields) throws MarshalException {
    long hash = 0;
    try {
        MessageDigest md = MessageDigest.getInstance("SHA");
        DataOutputStream out = new DataOutputStream(
            new DigestOutputStream(new ByteArrayOutputStream(127), md));
        if (superclass != null)
            out.writeLong(superclass.hash);   // ← recursive: parent hash first
        out.writeUTF(name);                   // ← fully qualified class name
        int startDeclaredFields = superclass != null ? superclass.numFields : 0;
        for (int i = startDeclaredFields; i < fields.length; i++) {
            out.writeUTF(fields[i].field.getName());      // ← Java field name
            out.writeUTF(fields[i].field.getType().getName()); // ← type name
        }
        out.flush();
        byte[] digest = md.digest();
        for (int i = Math.min(8, digest.length); --i >= 0; )
            hash += ((long)(digest[i] & 0xFF)) << (i * 8);
    } catch (Exception e) {
        throw new MarshalException("Unable to calculate type hash for " + name, e);
    }
    return hash;
}
```

The hash is therefore a deterministic function of:

1. **The superclass chain** — because the superclass hash is included first,
   any change to *any ancestor class* propagates upward and changes every
   subclass hash.
2. **The fully qualified class name** — renaming the class changes the hash.
3. **Each non-static, non-final, non-transient public field's Java name and
   declared type** — sorted alphabetically within each class
   (`ClassMapper.FieldComparator`), in super-to-subclass order.

### 2.2 Field Selection — `ClassMapper.getFields()`

Only fields satisfying the following filter are included:

```java
// ClassMapper.java lines 211–221
if ((fields[i].getModifiers() &
     (Modifier.STATIC | Modifier.FINAL | Modifier.TRANSIENT)) == 0)
{
    if (fields[i].getType().isPrimitive())
        throw new IllegalArgumentException("entry class " +
                                           cls.getName() +
                                           " has a primitive field");
    fields[len++] = fields[i];
}
```

A field participates in the hash (and in storage/retrieval) if and only if it is:
- `public`
- Not `static`
- Not `final`
- Not `transient`
- Not a primitive type

### 2.3 Field I/O — `EntryRep`

Fields are serialised *by index* into a positional `Object[]` called `fields`
inside `EntryRep`.  There are no field names in the stored representation; the
receiver relies entirely on the runtime order of `ClassMapper.getFields()` to
match wire positions to Java fields:

```java
// EntryRep.java — serialisation (lines 172–184)
private static Object[] fields(Entry entry) throws IOException, ... {
    EntryField[] efields = ClassMapper.getFields(entry.getClass());
    Object[] fields = new Object[efields.length];
    for (int i = efields.length; --i >= 0; ) {
        EntryField f = efields[i];
        Object val = f.field.get(entry);
        if (f.marshal && val != null)
            val = new MarshalledWrapper(new AtomicMarshalledInstance(val));
        fields[i] = val;
    }
    return fields;
}

// EntryRep.java — deserialisation (lines 192–225)
public Entry get() {
    Class clazz = eclass.toClass(codebase);
    EntryField[] efields = ClassMapper.getFields(clazz);
    Entry entry = (Entry) clazz.getDeclaredConstructor().newInstance(); // no-arg ctor
    for (int i = efields.length; --i >= 0; ) {
        Object val = flds.get(i);
        EntryField f = efields[i];
        if (f.marshal && val != null)
            val = ((MarshalledWrapper) val).get();
        f.field.set(entry, val);   // reflective positional assignment
    }
    return entry;
}
```

### 2.4 Hash Matching — `EntryClass.toClass()`

Before handing a deserialised entry back to a client, Reggie checks that the
local class's hash matches the stored hash:

```java
// EntryClass.java lines 248–259
if (hash != local.hash)
    throw new UnmarshalException("incoming entry type: " + toString()
                                 + " is not assignable to the local"
                                 + " version of the type: " + local);
```

A single-bit difference in the hash causes hard failure with no recovery path.

### 2.5 Why `final` Fields Are Excluded — Pre-JMM Deserialization Hazard

Both `ClassMapper.getFields()` and `AbstractEntry.fieldInfo()` explicitly skip
`Modifier.FINAL` fields.  There are two independent reasons for this exclusion,
one mechanical and one rooted in the Java Memory Model.

#### 2.5.1 Mechanical Incompatibility With Reflective Post-Construction Assignment

The deserialization path in `EntryRep.get()` works as follows:

```java
Entry entry = (Entry) clazz.getDeclaredConstructor().newInstance(); // step 1: no-arg ctor
for (...) {
    f.field.set(entry, val);  // step 2: reflective field assignment
}
```

`java.lang.reflect.Field.set()` throws `IllegalAccessException` when invoked on
a `final` field without a preceding `setAccessible(true)` call.  In 1999,
`setAccessible` had no special override for `final` fields and was unavailable
in applet-like sandboxes anyway.  Allowing `final` Entry fields would therefore
have caused a hard `IllegalAccessException` at deserialization time for every
entry returned from a registrar.

#### 2.5.2 Java Memory Model Hazard — The Deeper Reason

The Jini Entry standard was published in 1999, with Java 1.2.  The **revised
Java Memory Model (JSR-133)** was not finalized until Java 5 (2004).

The original JMM (Java Language Specification 1st edition, 1996) was famously
incomplete: it made no reliable guarantees about when writes performed in one
thread would be visible to other threads, and it gave `final` fields no special
thread-safety treatment beyond "cannot be reassigned in source code."

JSR-133 introduced the **`final` field freeze action** (JMM §17.5):

> A freeze action on a `final` field `f` of object `o` takes place when a
> constructor of `o` in which `f` is written exits.
> A subsequent read by another thread of a reference to a fully-constructed `o`
> is guaranteed to see the correctly initialized value of `f`.

The critical constraint is **"written in the constructor"**.  The JSR-133
guarantee applies *only* to final fields set during construction.  It does not
apply to final fields set via `Field.set()` with `setAccessible(true)` after
the constructor has returned.

If `final` Entry fields were allowed and populated via `Field.set()` after the
no-arg constructor, the following would occur:

1. The no-arg constructor sets `final String host = null` (or whatever the
   default initializer produces).
2. The constructor exits.  The JMM freeze action fires for `host = null`.
3. `Field.set(entry, "myhost.example.com")` updates the field's raw memory slot
   via reflection.  **No freeze action is associated with this write.**
4. Any thread that subsequently reads `entry.host` may legally see `null` —
   because the only freeze action it can observe is the one from step 2, and the
   JMM provides no happens-before relationship between the `Field.set()` write
   and the subsequent read.
5. An aggressive JIT that inlined the `final null` from step 2 at a call site
   will continue returning `null` indefinitely, even on the same thread.

This would produce hard-to-diagnose, non-deterministic `NullPointerException`s
on multicore hardware or under JIT optimisation — worst exactly in production
under load.

**Pre-JMM (Java 1.0–1.4)** the situation was even worse: there was no freeze
concept at all, and `final` field reads could be freely hoisted out of loops or
inlined as compile-time constants with no JMM barrier to stop them.

The exclusion of `final` fields was therefore not merely a convenience: it was
the only way to make the Entry deserialization path correct under either the
original or the revised Java Memory Model.

#### 2.5.3 Why the Exclusion Is Still Correct for Legacy Entries

Even with a modern JVM running Java 21+:

- `Field.set()` on a `final` field with `setAccessible(true)` is explicitly
  described in the JDK documentation as producing *undefined behavior* with
  respect to the JMM final-field guarantees.
- Since Java 12, `Field.setAccessible(true)` on `final` fields in non-open
  modules emits a warning.
- Since Java 17, reflective access to fields in JDK modules requires `--add-opens`
  command-line arguments.

The legacy Entry deserialization path cannot safely support `final` fields, and
the exclusion must remain.  §4.6 below explains how `@SerialEntry`'s
constructor-based approach lifts this restriction entirely.

### 2.6 Consequence: The Full Table of Breaking Changes

| Change type | Breaks hash? | Reason |
|---|---|---|
| Add a `public` non-`final` field anywhere in the hierarchy | **Yes** | Field name+type enters the digest |
| Remove a `public` non-`final` field | **Yes** | Field name+type removed from digest |
| Rename a field | **Yes** | Old and new names differ in digest |
| Change a field's declared type | **Yes** | Old and new type names differ in digest |
| Reorder fields within a class | **No** | Sorted alphabetically, so order is deterministic |
| Rename the Entry class itself | **Yes** | Class name enters the digest |
| Move the class to a different package | **Yes** | Fully qualified name changes |
| Add/modify a field to any *ancestor* class | **Yes** | Ancestor hash is included recursively |
| Add `transient` to an existing field | **Yes** | Field drops out of digest |
| Add `final` to an existing field | **Yes** | Field drops out of digest; also silently ignored by `AbstractEntry.fieldInfo()` |
| Add a method, constructor, or private field | **No** | Not part of digest |
| Add a `static` field | **No** | `static` fields are excluded |

---

## 3. What `@AtomicSerial` Does Differently

`org.apache.river.api.io.AtomicSerial` solves the analogous evolution problem
for serializable objects in JGDMS.  Each of its design decisions maps directly
onto an Entry problem identified in §2.

### 3.1 Explicit Serial Form — Decouples Wire Identity From Class Structure

Every `@AtomicSerial` class declares its own wire schema via a **public static
`serialForm()` method** that returns a `SerialForm[]`:

```java
// Example: EntryRep.java (itself uses @AtomicSerial)
public static SerialForm[] serialForm(){
    return new SerialForm[]{
        new SerialForm("eclass",    EntryClass.class),
        new SerialForm("codebase",  String.class),
        new SerialForm("fields",    Object[].class)
    };
}
```

This declaration:

- Is **completely independent of what Java fields the class actually declares**.
  A developer can rename an internal field from `foo` to `bar` without changing
  `serialForm()` or any wire representation.
- Is **under explicit developer control**.  The compiler does not infer it and
  the runtime does not derive it from reflection.
- Is **the single source of truth** for the serial contract of the class.
- Allows fields to be **added safely**: a receiving class that knows about a
  new field simply gets a default value from `GetArg.get(name, defaultValue)`
  when reading an older stream that does not contain it.
- Allows fields to be **removed safely**: the developer catches
  `IllegalArgumentException` from `GetArg.get()` when reading a stream that
  contains a field the current code no longer needs.

**Contrast with Entry:** The Entry hash is derived entirely from live
reflection over `Class.getFields()`.  The developer does not write down a wire
contract anywhere.  There is no way to rename a Java field without changing the
wire identity.

### 3.2 Named-Field Access With Default-Value Fallback

`GetArg.get(String name, T defaultValue, Class<T> type)` retrieves a field by
**name** and returns `defaultValue` when the field is absent from the stream.
`GetArg.defaulted(String name)` distinguishes "absent from stream" from
"present but null".

```java
// AtomicSerial GetArg API
public abstract <T> T get(String name, T val, Class<T> type)
    throws IOException, ClassNotFoundException;

public abstract boolean defaulted(String name) throws IOException;
```

This makes **adding a new field backward-compatible** for an `@AtomicSerial`
class: old streams lack the field; the `(GetArg)` constructor receives the
default value instead.  No hash changes; no deployment break.

**Contrast with Entry:** Fields are accessed by positional index.  There is no
concept of "this field was not present in the stored entry; use this default."
Any structural change that shifts a field's position silently misassigns values.

### 3.3 Constructor-Time Invariant Validation (Atomic Failure)

The `@AtomicSerial` constructor pattern enforces that invariants are checked
**before the object is constructed**:

```java
@AtomicSerial
public final class MyEntry implements Serializable {

    public MyEntry(GetArg arg) throws IOException {
        this(arg, check(arg));   // static check BEFORE this(...)
    }

    private MyEntry(GetArg arg, boolean checked) throws IOException {
        this.name = arg.get("name", null, String.class);
    }

    private static boolean check(GetArg arg) throws IOException {
        String name = arg.get("name", null, String.class);
        if (name == null || name.isEmpty())
            throw new InvalidObjectException("name must not be null or empty");
        return true;
    }
}
```

If invariants fail, the object cannot be constructed.  There is no partially
initialised instance, and no reference can be stolen.

**Contrast with Entry:** The no-arg constructor is called first, producing a
completely uninitialised entry.  Fields are then set one at a time via
`Field.set()`.  If a field assignment fails, the partially-initialised entry is
wrapped in `UnusableEntryException` and returned.  There is no hook to check
cross-field invariants (e.g. "if `port` is present, `host` must also be
present").

### 3.4 Static `serialize()` Method — Explicit Write Path

`@AtomicSerial` classes write their state through a public static method:

```java
public static void serialize(PutArg arg, MyClass obj) throws IOException {
    arg.put("name", obj.name);
    arg.put("port", obj.port);
    arg.writeArgs();
}
```

This is:
- **Statically visible** — no magic `writeObject` hidden in an instance method.
- **A place for translation** — the developer can convert internal state to a
  different wire representation without changing the wire schema.  For example,
  a field internally stored as a `UUID` can be written as a `String` for
  backward compatibility.

**Contrast with Entry:** There is no developer-written serialisation method.
`EntryRep.fields()` reads each public field value directly via `Field.get()`.
There is no point of intervention.

### 3.5 Serial Form Is Decoupled From Java Field Names — Safe Rename/Refactor

Because `serialForm()` uses **string wire names** that the developer controls,
and because `serialize()` maps internal field values to those wire names, a
developer can:

- Rename `public String host` → `public String hostname` in the Java source
- Keep `"host"` as the wire name in `serialForm()` and `serialize()`
- No hash change; no deployment break

This is impossible with the current Entry hash because the hash directly
encodes `field.getName()`.

### 3.6 Summary: AtomicSerial Technique → Entry Benefit

| `@AtomicSerial` Technique | Entry Problem Addressed | Benefit |
|---|---|---|
| `serialForm()` declares wire schema explicitly | Hash derived from live reflection; any rename breaks | Wire identity is an explicit contract; Java names are implementation details |
| Named `GetArg.get(name, default)` access | Fields accessed positionally; no default concept | Adding a field is backward-compatible; old entries return defaults |
| `defaulted(name)` check | No way to distinguish absent from null | Migration logic per field; version detection |
| Catch `IllegalArgumentException` on `get` | No way to handle removed fields | Old code handles new-format streams gracefully |
| `check()` before construction | No invariant check; partial initialisation | Fail-fast; no partially-initialised entries; cross-field validation |
| `serialize()` translates internal state | Raw `Field.get()` — no translation point | Internal representation can evolve independently of wire schema |
| Wire names decouple from Java field names | `field.getName()` in hash — rename = break | Refactor Java names freely without changing wire identity |
| Constructor-based deserialization | `Field.set()` post-construction — `final` fields unsafe (pre-JMM hazard) | `final` Entry fields are now safe; JSR-133 freeze action applies correctly |

---

## 4. Proposed Entry Evolution Extensions

The following extensions are **backward-compatible** with all existing Entry
implementations: old entries that do not adopt any of these patterns continue
to work exactly as today.  New entries that adopt these patterns gain the
evolution flexibility described in §3.

### 4.1 Explicit Wire Field Declaration — `@SerialEntry` Annotation

Introduce a new annotation, analogous to `@AtomicSerial`, that Entry
implementors can optionally place on their class:

```java
/**
 * Optional annotation for Entry classes that wish to declare an explicit
 * wire field schema independent of their Java field declarations.
 *
 * An @SerialEntry class MUST provide:
 *   public static EntryWireField[] entryForm()
 *   public static void serialize(PutEntryArg arg, T obj) throws IOException
 *   public T(GetEntryArg arg) throws IOException   // deserialization constructor
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SerialEntry { }
```

The `entryForm()` method returns a `EntryWireField[]` — an ordered list of
named wire fields that replaces live reflection as the source for hash
computation and field ordering:

```java
@SerialEntry
public class Location implements Entry {

    // Wire schema — the contract that never changes
    public static EntryWireField[] entryForm() {
        return new EntryWireField[] {
            new EntryWireField("host",    String.class),
            new EntryWireField("floor",   Integer.class),
            new EntryWireField("room",    String.class)
        };
    }

    // Java fields — implementation detail, can be renamed freely
    public String  host;
    public Integer floor;
    public String  room;

    public Location() { }   // still required for legacy path

    // Deserialization constructor — receives named fields with defaults
    public Location(GetEntryArg arg) throws IOException {
        this(arg, check(arg));
    }

    private Location(GetEntryArg arg, boolean checked) throws IOException {
        host  = arg.get("host",  null, String.class);
        floor = arg.get("floor", null, Integer.class);
        room  = arg.get("room",  null, String.class);
    }

    private static boolean check(GetEntryArg arg) throws IOException {
        String host = arg.get("host", null, String.class);
        if (host == null)
            throw new InvalidObjectException("host must not be null");
        return true;
    }

    // Serialization method — explicit write path
    public static void serialize(PutEntryArg arg, Location loc) throws IOException {
        arg.put("host",  loc.host);
        arg.put("floor", loc.floor);
        arg.put("room",  loc.room);
        arg.writeArgs();
    }
}
```

The hash for a `@SerialEntry` class is computed from `entryForm()` rather than
from live reflection, using the same SHA-1 algorithm as today but with wire
names instead of Java field names.

### 4.2 Name-Based Wire Format — Backward-Compatible Addition

The field `Object[]` stored inside `EntryRep` can be extended with an optional
name array, present only when the serialised Entry is a `@SerialEntry` instance.
The registrar reads either format:

- **Legacy format** (no `@SerialEntry`): positional `Object[]` as today.
- **Named format** (`@SerialEntry`): `String[] wireNames` + `Object[] values`,
  enabling the receiver to match by name rather than position.

This means an existing registrar that does not understand `@SerialEntry` still
reads the `Object[]` positionally and remains fully compatible.

### 4.3 Field Addition Without Hash Change

With `entryForm()` and the named wire format in place, the following evolution
becomes possible without breaking deployed entries:

```java
// Version 1 (deployed)
public static EntryWireField[] entryForm() {
    return new EntryWireField[] {
        new EntryWireField("host",  String.class),
        new EntryWireField("floor", Integer.class)
    };
}

// Version 2 (adds "room" — backward compatible)
public static EntryWireField[] entryForm() {
    return new EntryWireField[] {
        new EntryWireField("host",  String.class),
        new EntryWireField("floor", Integer.class),
        new EntryWireField("room",  String.class)   // new field appended
    };
}

// In the (GetEntryArg) constructor — handles both versions:
room = arg.get("room", null, String.class);  // null if field absent (v1 entry)
```

**The hash changes** because the wire schema changed, which is correct: the new
version is genuinely a different type.  But the change is *intentional* and
*documented* in `entryForm()`, not invisible as it is today.

For a **truly backward-compatible addition** (i.e., old and new entries match
the same template), the hash must remain stable.  This is achieved only by
keeping the `entryForm()` identical and using the `GetEntryArg` default
mechanism to populate new Java fields from derived or default values — without
adding them to the wire schema.  This gives developers a clear rule:

> *To add a field to the wire schema, append to `entryForm()` — the hash changes
> and you get a new type.*  
> *To add a computed or defaulted Java field that does not affect matching,
> add it only to Java source and populate it in the `(GetEntryArg)` constructor
> from existing fields — the hash is unchanged.*

### 4.4 Invariant Validation Gate

The `(GetEntryArg)` constructor pattern, identical to the `@AtomicSerial`
`check()` pattern, provides the first-ever invariant validation point for
Entry deserialization:

```java
// Validates cross-field invariants before the Entry is returned to the caller
private static boolean check(GetEntryArg arg) throws IOException {
    String host  = arg.get("host",  null, String.class);
    Integer floor = arg.get("floor", null, Integer.class);
    if (host == null)
        throw new InvalidObjectException("host must not be null");
    if (floor != null && floor < 0)
        throw new InvalidObjectException("floor must be non-negative");
    return true;
}
```

This replaces the current silent `null` assignment path in `EntryRep.get()`.

### 4.5 Explicit Serialization Method — Translation Point

The `serialize()` method gives developers a translation point between internal
representation and wire representation.  Example: a class that stores an IP
address internally as a `byte[4]` but wishes to be wire-compatible with a
version that stored it as a `String`:

```java
// Wire: always String (for compatibility)
public static void serialize(PutEntryArg arg, HostEntry e) throws IOException {
    arg.put("host", e.hostBytes == null ? null
                  : InetAddress.getByAddress(e.hostBytes).getHostAddress());
    arg.writeArgs();
}

// Deserialization: accepts the String wire form
private static boolean check(GetEntryArg arg) throws IOException {
    String host = arg.get("host", null, String.class);
    // validate...
    return true;
}

private HostEntry(GetEntryArg arg, boolean checked) throws IOException {
    String host = arg.get("host", null, String.class);
    this.hostBytes = host == null ? null : InetAddress.getByName(host).getAddress();
}
```

### 4.6 `final` Fields Are Safe in `@SerialEntry` Classes

The `(GetEntryArg)` constructor pattern eliminates the pre-JMM final-field hazard
described in §2.5.  Fields are assigned **inside a real constructor** — the
bridge constructor `private Location(GetEntryArg arg, boolean checked)` — not
via `Field.set()` after construction.

```java
@SerialEntry
public final class Location implements Entry {

    // These can now be final — they are set in the bridge constructor
    public final String  host;
    public final Integer floor;

    public Location(GetEntryArg arg) throws IOException {
        this(arg, check(arg));          // static check before bridge ctor
    }

    private Location(GetEntryArg arg, boolean checked) throws IOException {
        host  = arg.get("host",  null, String.class);   // ← written in ctor
        floor = arg.get("floor", null, Integer.class);   // ← written in ctor
    }

    // ... check(), serialize(), entryForm() as before
}
```

Because both `host` and `floor` are written during construction, the JSR-133
**freeze action** fires at the end of the bridge constructor.  Any thread that
subsequently obtains a reference to the fully-constructed `Location` is
guaranteed by JMM §17.5 to see the correct, non-null field values.  The JIT
cannot legally inline stale null values across this barrier.

**Benefits of `final` Entry fields:**

- Immutability of the Entry after construction — no accidental field mutation
  on the client side after a lookup result is returned.
- Safe publication without explicit synchronization — the JMM freeze action
  provides the required happens-before edge.
- Enables making Entry classes immutable value types, which is the correct
  design for any object that represents a stable lookup attribute.

The `public` visibility requirement of Entry fields still applies (the registrar
reads them via reflection for legacy-path compatibility), but `final` is no
longer a barrier.  An `@SerialEntry` class can declare its participating fields
as `public final`, populate them in the bridge constructor, and omit the no-arg
constructor entirely for the purpose of `@SerialEntry` deserialization (though
a no-arg constructor may still be needed for legacy interop with non-`@SerialEntry`
registrars).

---

## 5. Migration Path and Backward Compatibility

### 5.1 No Change Required for Existing Entries

All existing `Entry` implementations continue to work without modification.
`@SerialEntry` is opt-in.  The registrar and `EntryRep` retain the current
positional `Object[]` path for all non-annotated classes.

### 5.2 Existing Entries That Cannot Be Changed

For entries deployed in production whose source is unavailable or cannot be
changed, a thin wrapper `@SerialEntry` subclass can be used to stabilise future
evolution:

```java
@SerialEntry
public final class LocationV2 extends Location {
    public static EntryWireField[] entryForm() { ... }
    public LocationV2(GetEntryArg arg) throws IOException { ... }
    // ...
}
```

### 5.3 IDE / BAE Tooling

The Bytecode Analysis Engine (BAE, JGDMS-STD-002) can be extended with a new
`SerialEntryComplianceVisitor` analogous to `AtomicSerialComplianceVisitor`
that checks:

1. A `(GetEntryArg)` constructor is present.
2. A static `check()` call precedes the bridge constructor.
3. All `GetEntryArg.get()` calls on object-typed fields use the typed 3-argument
   form.
4. `entryForm()` and `serialize()` are both present and public static.

---

## 6. Worked Evolution Scenario

### Before (current fragile approach)

```java
public class Location implements Entry {
    public String host;
    public Integer floor;
    // DEPLOYED — hash = 0xABCD1234ABCD1234
}

// Later: developer adds a field...
public class Location implements Entry {
    public String host;
    public Integer floor;
    public String  building;   // BREAKS ALL DEPLOYED ENTRIES
    // New hash = 0x9876FEDC9876FEDC
}
```

All stored `Location` entries in every running Outrigger/Reggie now have a
different hash from the updated class.  They cannot be found or unmarshalled.
There is no error at compile time or test time — only silent runtime failure in
production.

### After (with `@SerialEntry`)

```java
// Version 1 — deployed
@SerialEntry
public class Location implements Entry {

    public static EntryWireField[] entryForm() {
        return new EntryWireField[] {
            new EntryWireField("host",  String.class),
            new EntryWireField("floor", Integer.class)
        };
    }
    // hash = SHA1(class-name + "host" + "java.lang.String"
    //             + "floor" + "java.lang.Integer")[:8]

    public String  host;
    public Integer floor;
    public Location() {}

    public Location(GetEntryArg arg) throws IOException { this(arg, check(arg)); }
    private Location(GetEntryArg arg, boolean c) throws IOException {
        host  = arg.get("host",  null, String.class);
        floor = arg.get("floor", null, Integer.class);
    }
    private static boolean check(GetEntryArg arg) throws IOException {
        if (arg.get("host", null, String.class) == null)
            throw new InvalidObjectException("host required");
        return true;
    }
    public static void serialize(PutEntryArg arg, Location l) throws IOException {
        arg.put("host", l.host); arg.put("floor", l.floor); arg.writeArgs();
    }
}
```

```java
// Version 2 — adds 'building' as a Java-only derived field (NO hash change)
@SerialEntry
public class Location implements Entry {

    public static EntryWireField[] entryForm() {
        return new EntryWireField[] {          // UNCHANGED — same hash
            new EntryWireField("host",  String.class),
            new EntryWireField("floor", Integer.class)
        };
    }

    public String  host;
    public Integer floor;
    public String  building;  // computed/defaulted; not in entryForm — NO HASH CHANGE

    public Location(GetEntryArg arg) throws IOException { this(arg, check(arg)); }
    private Location(GetEntryArg arg, boolean c) throws IOException {
        host     = arg.get("host",  null, String.class);
        floor    = arg.get("floor", null, Integer.class);
        building = deriveBuilding(host);  // synthesised from host, not from wire
    }
    // ... check(), serialize() unchanged
}
```

All stored v1 `Location` entries are found and returned correctly.  `building`
is synthesised at deserialisation time from `host`.  Zero deployment break.

---

## 7. Relationship to Existing JGDMS Standards

| Standard | Relationship |
|---|---|
| JGDMS-STD-001 — AtomicSerial Compliance | `@SerialEntry` reuses the same `GetArg`/`PutArg` API concepts; the BAE visitor pattern is directly reusable |
| JGDMS-STD-002 — Bytecode Analysis Engine | A new `SerialEntryComplianceVisitor` can enforce `@SerialEntry` rules at bytecode level |
| JGDMS-STD-004 — Policy File Syntax | No direct relationship |
| `net.jini.core.entry.Entry` | This document proposes extensions, not replacements; `Entry` remains the marker interface |
| `net.jini.entry.AbstractEntry` | `AbstractEntry.fieldInfo()` uses the same `SKIP_MODIFIERS` mask; `@SerialEntry` adds a parallel named-field path |

---

## 8. Open Questions

1. **Hash algorithm migration** — Should `@SerialEntry` classes use the same
   SHA-1 hash algorithm, or move to SHA-256?  SHA-1 is only used as a
   fingerprint here (not a security function), so collision risk is low, but
   migrating to SHA-256 would be cleaner.

2. **Registrar persistence format** — Outrigger and Reggie persist `EntryRep`
   objects to disk.  The named wire format extension must be designed so that
   existing persisted stores are still readable.  A format-version byte in the
   `EntryRep` serial form would address this.

3. **`AbstractEntry.equals()` for mixed-version comparisons** — When a v1
   entry (no `@SerialEntry`) and a v2 entry (with `@SerialEntry`) share the same
   class name and same hash, `equals()` currently uses strict class identity
   (`e1.getClass() != e2.getClass()`).  This is correct but should be confirmed
   for the case where a `@SerialEntry` class has the same hash as a legacy class
   of the same name.

4. **`UnusableEntryException` migration** — `GetEntryArg` throws
   `InvalidObjectException` rather than leaving fields null.  The registrar
   must decide whether to wrap these as `UnusableEntryException` (preserving
   the existing client API) or to surface them as hard failures.

---

## 9. References

| Source | Location in JGDMS |
|---|---|
| `EntryClass.computeHash()` | `services/reggie/reggie-dl/src/main/java/org/apache/river/reggie/proxy/EntryClass.java:311` |
| `ClassMapper.getFields()` | `services/reggie/reggie-dl/src/main/java/org/apache/river/reggie/proxy/ClassMapper.java:201` |
| `ClassMapper.FieldComparator` | `services/reggie/reggie-dl/src/main/java/org/apache/river/reggie/proxy/ClassMapper.java:234` |
| `EntryRep.fields()` (serialise) | `services/reggie/reggie-dl/src/main/java/org/apache/river/reggie/proxy/EntryRep.java:172` |
| `EntryRep.get()` (deserialise) | `services/reggie/reggie-dl/src/main/java/org/apache/river/reggie/proxy/EntryRep.java:192` |
| `AbstractEntry.fieldInfo()` | `jgdms-lib-dl/src/main/java/net/jini/entry/AbstractEntry.java:235` |
| `AtomicSerial` annotation | `jgdms-platform/src/main/java/org/apache/river/api/io/AtomicSerial.java` |
| `AtomicSerial.GetArg` | `jgdms-platform/src/main/java/org/apache/river/api/io/AtomicSerial.java:380` |
| `GetArgImpl` | `jgdms-platform/src/main/java/org/apache/river/api/io/GetArgImpl.java` |
| `Valid` utility | `jgdms-platform/src/main/java/org/apache/river/api/io/Valid.java` |
| AtomicSerial compliance standard | `docs/standard-atomic-serial-compliance.md` |
| `net.jini.core.entry.Entry` | `jgdms-platform/src/main/java/net/jini/core/entry/Entry.java` |
| `net.jini.entry.AbstractEntry` | `jgdms-lib-dl/src/main/java/net/jini/entry/AbstractEntry.java` |

---

*This document was produced from a detailed analysis of the JGDMS codebase
(May 2026).  It is intended as a design-proposal starting point for discussion
by the JGDMS / Jini community, not as an implemented specification.*
