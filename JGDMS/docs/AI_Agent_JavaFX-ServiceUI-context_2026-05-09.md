# JavaFX + ServiceUI Integration — Deep Dive Context Document

**Date:** 2026-05-09  
**Repository:** pfirmstone/JGDMS  
**Related repository:** pfirmstone/jfx (`authorization` branch)  
**Author:** AI Agent (Copilot)

---

## 1. Background and Motivation

The Jini/JGDMS **ServiceUI** specification (version 1.1, originally by Bill Venners) defines
a toolkit-neutral mechanism for associating a user interface with a distributed Jini service.
A `UIDescriptor` — itself a serialisable `net.jini.core.entry.Entry` stored in a service item's
attribute set — carries a marshalled UI factory together with metadata (`role`, `toolkit`,
`attributes`).  Clients deserialise the factory and call its factory method to obtain the actual
UI object.

The existing factory types are all Swing/AWT-centric:

| Module              | Interfaces                                                        | `toolkit` constant |
|---------------------|-------------------------------------------------------------------|--------------------|
| `jgdms-ui-factory`  | `JFrameFactory`, `JDialogFactory`, `JWindowFactory`              | `"javax.swing"`    |
| `jgdms-ui-factory`  | `JComponentFactory`                                               | `"javax.swing"`    |
| `jgdms-ui-factory`  | `FrameFactory`, `DialogFactory`, `WindowFactory`, `PanelFactory` | `"java.awt"`       |

JavaFX has been the preferred Java GUI toolkit since Java 8 (bundled) and is now a separate
open-source project (`openjdk/jfx`).  `pfirmstone/jfx` is a fork that specifically restores
`SecurityManager` support and `doPrivileged` calls that upstream OpenJFX removed in late 2024.
Supporting JavaFX in ServiceUI requires new factory interfaces and careful attention to:

* JavaFX's **single dedicated UI thread** (the JavaFX Application Thread, JAT)
* JavaFX's **non-serialisability** of scene-graph objects
* JavaFX's **named JPMS modules** and strict encapsulation
* JGDMS's dynamic **class-loading** model (marshalled objects, TCCL manipulation)
* The **SecurityManager / doPrivileged** context that JGDMS relies on

---

## 2. ServiceUI Architecture — Key Points for this Analysis

### 2.1 UIDescriptor

```
net.jini.lookup.entry.UIDescriptor extends AbstractEntry
  String          role        // fully qualified role interface name
  String          toolkit     // main toolkit package (e.g. "javax.swing")
  Set             attributes  // AccessibleUI, Locales, RequiredPackages, UIFactoryTypes …
  MarshalledObject factory    // marshalled UI factory object
```

`UIDescriptor.getUIFactory(ClassLoader parentLoader)` temporarily sets the **Thread Context
ClassLoader** (TCCL) to `parentLoader`, then calls `new MarshalledInstance(factory).get(false)`
to deserialise the factory.  The factory itself is a small, serialisable gateway object — not
the heavy UI component.

### 2.2 Factory interfaces

A UI factory interface:

1. Extends `java.io.Serializable` (so instances can be marshalled into a `MarshalledObject`)
2. Declares a factory method whose return type is a **concrete toolkit object** (`JFrame`,
   `JComponent`, `Node`, `Stage`, etc.)
3. Carries two convenience String constants:
   * `TOOLKIT` — the toolkit's root package (used in `UIDescriptor.toolkit`)
   * `TYPE_NAME` — the factory interface's fully qualified name (used in `UIFactoryTypes`)

The factory method is invoked **on whatever thread calls it** (typically the client's lookup
browsing thread).  For Swing this is safe because Swing objects can be created off the EDT
before `setVisible(true)`.  For JavaFX this is **not safe** (see §3 below).

### 2.3 Roles and attributes

The three standard roles (`MainUI`, `AdminUI`, `AboutUI`) are toolkit-independent; they only
state that the role object (service item) must be accepted as the factory method's first argument.
New JavaFX roles can share the same role constants.

`RequiredPackages` lets a client pre-check for compatible packages before attempting to
instantiate a UI.  For JavaFX, service providers would list `javafx.graphics`, `javafx.controls`,
etc. in a `RequiredPackages` attribute.

---

## 3. JavaFX Architecture Constraints

### 3.1 JavaFX Application Thread (JAT)

JavaFX enforces a **single UI thread**.  All `Node`, `Scene`, and `Stage` objects must be
**created and mutated** on the JAT.  Violating this causes `IllegalStateException`.

The JAT is started by one of:
* Extending `Application` and calling `Application.launch()`
* Calling `Platform.startup(Runnable)` (JDK 9+) — starts the toolkit without an `Application`
* Calling `new JFXPanel()` (from Swing) — boots JavaFX implicitly

Once started, `Platform.runLater(Runnable)` schedules work on the JAT, and `Platform.isFxApplicationThread()` tests the current thread.

### 3.2 Non-serialisability of scene-graph objects

`javafx.scene.Node`, `javafx.scene.Scene`, `javafx.stage.Stage` — none implement
`java.io.Serializable`.  This is **by design**; they contain native peer handles, property
listeners, and render-thread state.

Consequence: a JavaFX UI factory **cannot return a pre-built Node**.  The factory object stored
in the `MarshalledObject` must be a small, serialisable descriptor/builder that, when its factory
method is called, **creates** JavaFX objects on the JAT.

### 3.3 JPMS module encapsulation

JavaFX is distributed as named JPMS modules: `javafx.base`, `javafx.graphics`,
`javafx.controls`, `javafx.fxml`, `javafx.swing`, etc.  Any module that uses JavaFX public API
must declare `requires javafx.controls;` (or the appropriate module) in its `module-info.java`.

`jgdms-ui-factory` currently compiles with `--release 8`, so it has no `module-info.java`.
Adding JavaFX factory interfaces to this module would force it to a higher release and require
module-path configuration at runtime.  It is cleaner to introduce a **separate module**
`jgdms-ui-factory-javafx`.

### 3.4 SecurityManager and doPrivileged — the pfirmstone/jfx fork

Upstream OpenJFX (late 2024) merged:
* `8341090` — Remove support for SecurityManager from JavaFX
* `8342453` — Remove doPrivileged in `javafx.graphics/com.sun.javafx.tk`
* `8342912` — Remove doPrivileged in FXML
* `8342913` — Remove doPrivileged in media

`pfirmstone/jfx` (`authorization` branch) **reverts all four** of these commits.  This is
critical for JGDMS, which uses a SecurityManager-based policy (`DynamicPolicy`) and relies on
`doPrivileged` transitions to grant code loaded from remote class servers the correct
permissions.  Without these reversions:
* A JavaFX UI loaded from a remote codebase would run entirely under the client's ACC, with no
  privilege barrier between untrusted UI code and the client JVM.
* `GrantPermission` grants and `PolicyUpdateListener` ACLs have no meaningful effect on JavaFX
  internal operations.

**Summary:** Integration with JGDMS requires `pfirmstone/jfx` or an equivalent build that
restores `doPrivileged` and SecurityManager support.

### 3.5 Class-loading under JavaFX

`FXMLLoader` and CSS loading use the TCCL or an explicit ClassLoader passed to the loader.
JGDMS's `UIDescriptor.getUIFactory()` already sets the TCCL to `parentLoader` during
deserialisation.  Service developers should propagate this TCCL into `FXMLLoader` factory
method bodies explicitly.

---

## 4. Design Options

### Option A — New Native JavaFX Factory Interfaces (recommended)

Introduce a new Maven module `jgdms-ui-factory-javafx` (release 21, JPMS-aware) with
interfaces mirroring the Swing set:

| Proposed interface       | Returns          | `TOOLKIT` constant        |
|--------------------------|------------------|---------------------------|
| `NodeFactory`            | `javafx.scene.Node`     | `"javafx.scene"`   |
| `ParentFactory`          | `javafx.scene.Parent`   | `"javafx.scene"`   |
| `StageFactory`           | `javafx.stage.Stage`    | `"javafx.stage"`   |
| `DialogFactory` (jfx)    | `javafx.scene.control.Dialog<?>` | `"javafx.controls"` |

Factory method signatures must be **asynchronous-aware**, returning `CompletableFuture` or
requiring callers to invoke via `Platform.runLater`:

```java
// Option A1 — synchronous, caller must be on JAT
public interface NodeFactory extends java.io.Serializable {
    String TOOLKIT   = "javafx.scene";
    String TYPE_NAME = "net.jini.lookup.ui.factory.javafx.NodeFactory";
    Node getNode(Object roleObject);
}

// Option A2 — returns Future; factory dispatches to JAT internally
public interface NodeFactory extends java.io.Serializable {
    String TOOLKIT   = "javafx.scene";
    String TYPE_NAME = "net.jini.lookup.ui.factory.javafx.NodeFactory";
    CompletableFuture<Node> getNode(Object roleObject);
}
```

**A1 pros:** Simple, mirrors the Swing pattern exactly.  
**A1 cons:** Caller must be on the JAT or must wrap the call in `Platform.runLater` itself;
breaks the "call factory method, get object" contract if caller is not on JAT.

**A2 pros:** Factory always works off-JAT; safer for generic browsing clients.  
**A2 cons:** Breaks the simple synchronous contract; adds complexity for service developers.

**Recommended approach — A1 + a thread-checking helper:**  
Define A1-style interfaces.  Provide a `ServiceUIHelper.createOnJAT(NodeFactory, Object)`
utility that transparently wraps the call in `Platform.runLater` and waits with a
`CompletableFuture`.  This keeps individual factory implementations simple while giving
clients a safe entry point.

**StageFactory** is special: `Stage` must also be created on the JAT, and it must not be shown
until the client has configured it (matching the contract in `MainUI`/`AdminUI` Javadocs).
The `StageFactory.getStage(Object)` method should return the `Stage` hidden (not shown), exactly
as `JFrameFactory` returns a hidden `JFrame`.

**Pros of Option A overall:**
* Clean, native JavaFX — full access to Scene Builder, FXML, CSS, animations, rich controls.
* Service UIs can use modern JavaFX idioms (MVVM, property bindings, observable collections).
* Separate module means no impact on existing Swing/AWT users.
* `UIDescriptor.toolkit = "javafx.scene"` or `"javafx.controls"` clearly identifies JavaFX UIs.
* Clients can filter `UIDescriptor` by toolkit before deserialising the factory, avoiding
  JavaFX runtime startup on headless or Swing-only clients.

**Cons of Option A:**
* JavaFX application thread must already be running when the factory method is called.  If no
  JavaFX toolkit is started, the client must call `Platform.startup()` first.  Existing
  browsing clients (e.g. a Jini browser written in Swing) must be upgraded to start JavaFX.
* Requires `pfirmstone/jfx` (or equivalent) for SecurityManager compatibility.
* `StageFactory` results cannot be easily embedded in a larger application window; they are
  independent top-level windows.
* `NodeFactory`/`ParentFactory` results require a `Scene` and a `Stage` to be useful; the
  client must compose these.
* JavaFX is not available on all platforms (though support has widened significantly).

---

### Option B — JavaFX Embedded in Swing via JFXPanel / SwingNode

Use the existing `JComponentFactory` / `JFrameFactory` interfaces.  The factory method body:
1. Creates a `JFXPanel` (or `JFrame` containing a `JFXPanel`).
2. Uses `Platform.runLater` internally to populate the `JFXPanel` with a JavaFX `Scene`.
3. Returns the Swing component to the caller.

```java
public class MyServiceJFXFactory implements JComponentFactory {
    public JComponent getJComponent(Object roleObject) {
        JFXPanel panel = new JFXPanel(); // boots JavaFX if not already running
        Platform.runLater(() -> {
            Scene scene = buildScene(roleObject);
            panel.setScene(scene);
        });
        return panel;  // returned before Scene is set — caller must handle async layout
    }
}
```

**Pros of Option B:**
* No new factory interfaces or modules — works with existing `jgdms-ui-factory` and existing
  browsing clients.
* `JFXPanel` constructor boots JavaFX implicitly — no explicit `Platform.startup()` required.
* Result (`JComponent`) is composable in any Swing container.
* Swing ↔ JavaFX bridge is a well-understood pattern (`javafx.swing` module).

**Cons of Option B:**
* Significant rendering overhead: two toolkits running simultaneously (EDT + JAT).
* The returned `JComponent` is initially empty; the JavaFX scene is populated asynchronously.
  Callers that immediately call `setVisible(true)` may show a blank panel briefly.
* Accessibility bridging between Swing and JavaFX accessibility trees is incomplete.
* `SwingNode` (the reverse — JavaFX hosting a Swing component) has known threading issues on
  some platforms; `JFXPanel` (Swing hosting JavaFX) is more stable but still carries overhead.
* `toolkit` field must be set to `"javax.swing"` — misleads clients that filter by toolkit.
  Alternatively, set it to `"javafx.scene"` and the factory also implements `JComponentFactory`
  — but the `UIFactoryTypes` attribute resolves this ambiguity properly.
* Cannot use `StageFactory` pattern — all JavaFX content is sandwiched inside Swing.
* Loses many JavaFX benefits (GPU-accelerated rendering pipeline, FXML integration, CSS
  theming at the FX level) due to Swing host.

---

### Option C — Mixed: New Factory Interfaces + Optional SwingNode Embedding

Service providers publish **two** `UIDescriptor` entries per service:
1. A native `NodeFactory`/`StageFactory` descriptor (`toolkit = "javafx.scene"`)
2. A fallback `JComponentFactory` descriptor using `JFXPanel` embedding (`toolkit = "javax.swing"`)

Clients choose based on their capabilities:
* A pure-JavaFX client browser picks the `javafx.scene` descriptor.
* A legacy Swing browser picks the `javax.swing` descriptor.

**Pros of Option C:**
* Maximum compatibility — works with existing Swing browsers and future JavaFX browsers.
* Service provider has full control over which UI experience each client type gets.
* Leverages `UIFactoryTypes.isAssignableTo()` for clean capability negotiation.

**Cons of Option C:**
* Service providers must implement and maintain two UI factories per service.
* Increases size of marshalled objects in service items.
* Browsing clients still need logic to prefer one descriptor over another.

---

### Option D — Toolkit-Neutral Scene Description (Future/Research)

Define a toolkit-neutral factory that returns a **declarative description** of the UI (e.g.
FXML string, JSON schema, or a custom `SceneDescriptor` value object).  The client
instantiates the actual UI using its own preferred toolkit.

This is largely academic at this time — there is no standard toolkit-neutral UI description
that covers both Swing and JavaFX.  Not recommended for near-term implementation.

---

## 5. Impact on JGDMS Modules

### 5.1 New module: `jgdms-ui-factory-javafx`

```
JGDMS/jgdms-ui-factory-javafx/
  pom.xml                        (release 21, depends on javafx.controls)
  module-info.java               (requires javafx.controls; requires javafx.graphics; ...)
  src/main/java/net/jini/lookup/ui/factory/javafx/
    NodeFactory.java
    ParentFactory.java
    StageFactory.java
    FxDialogFactory.java
    ServiceUIHelper.java         (Platform.runLater wrapper utilities)
```

`jgdms-ui-factory-javafx` would have an **optional** Maven dependency on `jgdms-ui-factory`
(for shared constants) and a **required** dependency on `javafx.controls`.

The `javafx.controls` JAR must be provided by `pfirmstone/jfx` builds, not from Maven Central,
to get SecurityManager support.

### 5.2 Changes to `jgdms-ui-factory`

No changes required.  The existing module remains at `--release 8` with no JavaFX dependency.

### 5.3 `jgdms-lib-dl`

`UIDescriptor`, `UIFactoryTypes`, `RequiredPackages`, `AccessibleUI` — all in `jgdms-lib-dl` —
require no changes.  They work with any string-based `toolkit` and `role` values.

### 5.4 Browsing clients (e.g. a Jini browser)

A Jini service browser that wants to display JavaFX UIs must:
1. Start the JavaFX platform: `Platform.startup(() -> {})` before lookup/browsing begins (or
   embed a `JFXPanel` dummy to boot it).
2. Filter `UIDescriptor` entries by `toolkit = "javafx.scene"` (or check
   `UIFactoryTypes.isAssignableTo(NodeFactory.class)`).
3. Call `ServiceUIHelper.createOnJAT(factory, serviceItem)` which wraps the factory call in
   `Platform.runLater` and returns `CompletableFuture<Node>`.
4. Embed the resulting `Node` in a `Scene` / `Stage` or inside a `BorderPane` / `TabPane`.

---

## 6. SecurityManager / doPrivileged Integration Details

### 6.1 Factory deserialisation

`UIDescriptor.getUIFactory(parentLoader)` temporarily sets TCCL and calls
`MarshalledInstance.get(false)`.  The `false` flag means class verification is skipped
(no codebase URL integrity check at this point).  The factory's class must already be
loadable from `parentLoader`.

Under JGDMS's `DynamicPolicy`, the factory object arrives in a `MarshalledObject` whose
codebase annotation points to the service's HTTP class server.  The class loader created by
`PreferredClassProvider` (or `BasicJeriExporter`'s internal mechanism) will be that factory's
defining loader.  **All JavaFX code that runs inside the factory method runs as that loader's
protection domain.**

If `pfirmstone/jfx` retains `doPrivileged` wrappers, internal JavaFX operations (font loading,
native peer allocation, CSS parsing) execute with JavaFX's **own** permissions, not the
factory's restricted permissions.  This is the correct isolation model.

### 6.2 Subject / GrantPermission

If the service's `UIDescriptor` is retrieved by an authenticated user (Subject present via
`Subject.current()` from DirtyChai), and the factory invokes `GrantPermission.checkGuard()`,
the current Subject's principals are correctly resolved because `doPrivileged` inside JavaFX
does not clear the Subject scope (Subject is a ScopedValue in JDK 21, not stored in the ACC).

This means **user identity is correctly threaded through JavaFX factory method calls** —
a significant advantage over naive JavaFX usage without JGDMS's DirtyChai Subject propagation.

### 6.3 JavaFX Application Thread and Subject propagation

`Platform.runLater(Runnable)` dispatches runnables to the JAT.  In standard JavaFX, the JAT
has no Subject.  In a JGDMS-integrated environment, `ServiceUIHelper` should wrap the runnable
in `Subject.callAs(currentSubject, callable)` so that the Subject is propagated to the JAT:

```java
Subject user = Subject.current();
Platform.runLater(() -> Subject.callAs(user, () -> {
    Node node = factory.getNode(roleObject);
    futureResult.complete(node);
    return null;
}));
```

This ensures that any permission checks inside the factory method body (e.g.
`GrantPermission.checkGuard`) resolve the correct user Subject even on the JAT.

---

## 7. Serialisation Compatibility

### 7.1 Factory objects must be serialisable

All JavaFX factory implementations must implement `java.io.Serializable` (as required by the
ServiceUI spec).  Typical pattern:

```java
public class MyNodeFactory implements NodeFactory {
    private static final long serialVersionUID = /* generated */L;
    private final String fxmlResourcePath;  // serialisable configuration

    // transient fields for JAT-local state — not serialised
    transient Node cachedNode;

    @Override
    public Node getNode(Object roleObject) {
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource(fxmlResourcePath));
        try { return loader.load(); } catch (IOException e) { throw new RuntimeException(e); }
    }
}
```

### 7.2 FXML vs programmatic scene graphs

FXML-based factories are simpler to serialise: only the resource path (a String) or the FXML
content itself (a String) needs to be stored.  Programmatic scene-graph builders are also fine
as long as all fields are serialisable primitives / Strings.

### 7.3 AtomicSerial compatibility

JGDMS uses `AtomicSerial` for safe deserialisation of platform objects.  The JavaFX factory
classes themselves (owned by service providers) do not need to implement `AtomicSerial` unless
the provider chooses to.  They go through standard Java deserialisation, which is acceptable
for trusted classes loaded from a known codebase.

---

## 8. Module System Considerations

### 8.1 JPMS and ServiceUI's dynamic class loading

ServiceUI relies on URLs embedded in codebase annotations and `PreferredClassProvider` to load
factory classes at runtime.  Under JPMS, unnamed modules (classpath classes) can read named
modules freely, but named module classes have stricter access rules.

For JavaFX factory classes in service JARs:
* If the service JAR is on the **classpath** (unnamed module): can use JavaFX API freely (reads
  `javafx.controls` because unnamed modules read all named modules that are in the module graph).
* If the service JAR is itself a **named module**: must declare `requires javafx.controls;`.

Most JGDMS service UIs will be on the classpath (unmarshalled into a PreferredClassProvider-
managed loader that delegates to boot/ext/classpath), so JPMS should not be a blocking issue.

### 8.2 `jgdms-ui-factory-javafx` module descriptor

```java
module net.jini.lookup.ui.factory.javafx {
    requires javafx.base;
    requires javafx.graphics;
    requires javafx.controls;
    requires transitive net.jini.lookup.ui.factory;  // existing module (if modularised)
    exports net.jini.lookup.ui.factory.javafx;
}
```

---

## 9. Comparison Matrix

| Concern                             | Option A (Native FX)      | Option B (JFXPanel)        | Option C (Both)            |
|-------------------------------------|---------------------------|----------------------------|----------------------------|
| Clean JavaFX native rendering        | ✅ Yes                    | ⚠️ Partial (inside Swing)   | ✅ Yes (A path)            |
| No changes to existing clients       | ❌ Clients need FX start  | ✅ Works with Swing clients  | ⚠️ Depends on path chosen  |
| No new factory interfaces            | ❌ New interfaces needed  | ✅ Reuses JComponentFactory  | ❌ New interfaces needed   |
| SecurityManager compatibility        | ✅ With pfirmstone/jfx    | ✅ With pfirmstone/jfx      | ✅ With pfirmstone/jfx     |
| Subject propagation on JAT           | ✅ With ServiceUIHelper   | ⚠️ Needs explicit wrapping  | ✅ With ServiceUIHelper    |
| GPU-accelerated rendering            | ✅ Full FX pipeline       | ⚠️ FX inside Swing overhead | ✅ Full FX pipeline        |
| FXML / CSS / animations              | ✅ Full                   | ✅ Full (inside JFXPanel)    | ✅ Full                    |
| Service provider effort              | Medium                    | Low                        | High                       |
| Client upgrade required              | Yes                       | No                         | Partial                    |
| Accessibility                        | FX accessibility tree     | Swing+FX bridged (limited)  | FX tree (A path)           |
| Headless / server clients            | ❌ FX needs display       | ❌ FX needs display          | ❌ FX needs display        |
| `toolkit` field clarity              | ✅ "javafx.scene"         | ⚠️ Misleading "javax.swing" | ✅ Clear per descriptor    |
| Long-term strategic fit              | ✅ Modern, forward-looking | ❌ Legacy Swing dependency  | ✅ Transition path         |

---

## 10. Recommended Approach

1. **Implement Option A** as the primary path: create `jgdms-ui-factory-javafx` with
   `NodeFactory`, `ParentFactory`, `StageFactory`, and `FxDialogFactory`.

2. **Add `ServiceUIHelper`** with static utility methods that safely bridge the non-JAT calling
   convention of ServiceUI clients into JAT-required JavaFX construction, including Subject
   propagation via `Subject.callAs`.

3. **Document Option B** (JFXPanel embedding) as a migration pattern for service developers
   who have existing Swing clients and want to start introducing JavaFX content incrementally.

4. **Depend on `pfirmstone/jfx`** (`authorization` branch) for any JGDMS build that includes
   JavaFX integration, to ensure SecurityManager and doPrivileged compatibility.

5. **Publish the JavaFX factory JARs** via a dedicated HTTP class server so that client
   JVMs without the `jgdms-ui-factory-javafx` module can load them dynamically via
   `PreferredClassProvider`, consistent with the JGDMS class-loading model.

6. **Update `RequiredPackages` usage guidance**: service providers should always include
   `javafx.graphics`, `javafx.controls` (and any other JavaFX modules used) in the
   `RequiredPackages` attribute so that clients can fail-fast before deserialising a factory
   that requires an unavailable toolkit.

---

## 11. Open Questions and Future Work

1. **Platform.exit() and shared-VM lifecycle** — If multiple services are browsed in the same
   JVM and each tries to manage the JavaFX platform lifecycle, `Platform.exit()` from one
   service's UI could kill all JavaFX UIs.  `Platform.setImplicitExit(false)` should be the
   default in a multi-service browser.

2. **JavaFX on mobile and embedded** — JavaFX has Gluon Mobile ports; JGDMS's Jini services
   could potentially have mobile UIs.  The factory pattern already supports this since the
   factory object is platform-agnostic.

3. **Web embedding** — JPro and other products run JavaFX in a browser via WebSockets.  A
   `WebNodeFactory` variant pointing at a JPro session is conceivable.

4. **FXML loading with AtomicSerial** — If the FXML document itself is stored in the
   `MarshalledObject` as a byte array (rather than a resource path), AtomicSerial can validate
   the FXML bytes before deserialisation.  This would allow policy-based FXML content control.

5. **pfirmstone/jfx Maven publishing** — The `authorization` branch currently has no Maven
   artifact published to Maven Central or a public repo.  For JGDMS to depend on it in CI,
   either a JGDMS-local Maven repository or a GitHub Packages publication of `pfirmstone/jfx`
   artifacts is needed.

---

## 12. References

* ServiceUI specification v1.1 — `JGDMS/src/site/resources/old-static-site/doc/specs/html/serviceui-spec.html`
* `net.jini.lookup.entry.UIDescriptor` — `JGDMS/jgdms-lib-dl/src/main/java/net/jini/lookup/entry/UIDescriptor.java`
* `net.jini.lookup.ui.factory.*` — `JGDMS/jgdms-ui-factory/src/main/java/net/jini/lookup/ui/factory/`
* `net.jini.lookup.ui.MainUI`, `AdminUI`, `AboutUI` — `JGDMS/jgdms-lib-dl/src/main/java/net/jini/lookup/ui/`
* pfirmstone/jfx `authorization` branch — https://github.com/pfirmstone/jfx/tree/authorization
* Reverted upstream commits:
  * `8341090` — Remove SecurityManager support from JavaFX
  * `8342453` — Remove doPrivileged in javafx.graphics/com.sun.javafx.tk
  * `8342912` — Remove doPrivileged in fxml
  * `8342913` — Remove doPrivileged in media
* DirtyChai / Subject.callAs integration — `JGDMS/JGDMS/AI-agent-JGDMS-DirtyChai-context.md`
* JGDMS Security Architecture — `JGDMS/docs/Big picture security architecture/AI_Agent_JGDMS-GrantPermission-RoleManagement-context_8.md`
