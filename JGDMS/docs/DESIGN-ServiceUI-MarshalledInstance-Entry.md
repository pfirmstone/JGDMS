# DESIGN — A MarshalledInstance-native ServiceUI Entry

**Status:** design note (forward work, *not* part of the mechanical Task #10
MarshalledObject→MarshalledInstance migration).
**Relates to:** `docs/AI_Agent_JavaFX-ServiceUI-context_2026-05-09.md`,
`docs/SOW-MarshalledObject-Migration-RemainingGroups.md`.

## Problem

`net.jini.lookup.entry.UIDescriptor` (the Bill Venners ServiceUI 1.1 Entry)
declares its UI factory as `java.rmi.MarshalledObject factory`. Providers are
told (JavaFX context doc §2.1/§6.1/§7.3) to build a `MarshalledInstance` and
call `convertToMarshalledObject()` to fit that field — the exact schema-dropping
downgrade the rest of 4.0.0 is removing.

We **must not** flip `UIDescriptor.factory` to `MarshalledInstance`:

- An `Entry` is marshalled field-by-field for partial template matching, and is
  matched by field equality across a heterogeneous federation. Changing a
  field's type changes the serial form + `serialVersionUID` and breaks every
  stored `UIDescriptor` and every lookup template already in the wild.
- `UIDescriptor` is a published spec type; existing Swing/AWT clients and
  third-party services depend on its exact shape.

Entries are brittle by design — so we add a **new** Entry rather than mutating
the old one.

## What the schema actually buys here

The factory class is loaded by a `PreferredClassLoader` reconstructed from the
**codebase annotation** embedded in the marshalled bytes (JavaFX doc §3.5/§6.1);
the factory then resolves FXML/CSS/resources via
`this.getClass().getClassLoader()`, never the TCCL. That codebase annotation
already survives `convertToMarshalledObject()`, so **ClassLoader provisioning
works today** even through the legacy `MarshalledObject` field. What the
downgrade loses is the **DER schema** — which, for a *code-present* Java UI
factory, is low value (the code is on the provisioned loader). The schema earns
its keep for non-Java / archaeology consumers, which a UI factory is not.

Conclusion: leave `UIDescriptor` alone; the MI-native Entry is a forward
improvement that rides with the JavaFX work, not an urgent migration target.

## Proposed Entry

A new Entry in the JavaFX factory module (`jgdms-ui-factory-javafx`, release 21,
JPMS) — kept out of `jgdms-lib-dl` so the legacy module stays release-8 and
spec-pure:

```
net.jini.lookup.entry.ui.MarshalledUIDescriptor extends AbstractEntry   // @AtomicSerial
  String             role          // same role constants as UIDescriptor
  String             toolkit       // e.g. "javafx.scene"
  Set                attributes    // UIFactoryTypes, RequiredPackages, AccessibleUI, …
  MarshalledInstance factory       // canonical — carries schema AND codebase, no downgrade
```

- `factory` is held as `MarshalledInstance` directly — providers do
  `new MarshalledInstance(myFactory)` (or `AtomicMarshalledInstance` for DER)
  with **no** `convertToMarshalledObject()` step.
- A `getUIFactory(ClassLoader)` accessor deserialises via an **explicit**
  ClassLoader passed to the resolution machinery (the JavaFX doc §11.6
  improvement) — dropping the legacy TCCL juggling that `UIDescriptor` still does.
- `@AtomicSerial` so the Entry itself participates in atomic/DER marshalling.

## Coexistence (no break)

Services publish the new Entry **alongside** the legacy `UIDescriptor` — the
dual-descriptor pattern (JavaFX doc Option C). Swing/AWT browsers keep reading
`UIDescriptor`; JavaFX-aware browsers filter on the new Entry / `toolkit =
"javafx.scene"`. No existing client or stored attribute set changes.

## Why it lives with the JavaFX track

The new Entry only earns its complexity once there is a non-Swing factory family
to carry (the `NodeFactory`/`StageFactory` set in `jgdms-ui-factory-javafx`,
which needs `pfirmstone/jfx` for SecurityManager/`doPrivileged` support). Built
together, the new Entry + the explicit-ClassLoader `getUIFactory` + the JavaFX
factories form one coherent, schema-preserving, TCCL-free ServiceUI path.

## Out of scope for Task #10

The mechanical migration leaves `UIDescriptor` as `java.rmi.MarshalledObject`.
This note is the record that the schema-preserving ServiceUI path is a *new
Entry* delivered with the JavaFX integration, not a mutation of the existing
spec Entry.
