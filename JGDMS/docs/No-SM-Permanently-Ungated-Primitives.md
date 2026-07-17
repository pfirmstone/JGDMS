# No-SM Permanently-Ungated JVM-Wide Primitives

- **Authored:** 2026-07-11.
- **Status:** Verified findings, advisory only — no DirtyChai source changes. Both findings below
  were verified against DirtyChai's actual built JDK image
  (`/home/user/GitHub/DirtyChai/build/linux-x86_64-server-release/images/jdk`, `openjdk version
  "27-internal"`), by reading real source and, where possible, by compiling and running standalone
  test programs outside the DirtyChai repo against that image — matching DirtyChai's own
  no-AI-contribution policy (read/advise only, no source or doc contributions to that repo).
- **Repos:** cross-ref = `~/GitHub/DirtyChai` (SecurityManager-capable OpenJDK 27 fork, the subject
  of both findings); `~/GitHub/JGDMS` (this document's home).
- **Companions:** `SOW-DirtyChai-Serialization-SPI-DER.md` §2.1 (finding 1, in full detail — this
  document summarizes and cross-references rather than duplicating); `SOW-JMX-JERI-DER-Connector.md`
  (proposes replacing the RMI-based JMX connector this document's finding 2 partly concerns);
  `docs/security-articles/attack-model-vs-kernel-isolation.md` (the public-facing article these
  findings are candidate evidence for).

---

## 0. Objective

Name a recurring shape, not a single bug: across two structurally unrelated areas of the JDK —
object construction and dynamic code/management control — the *only* control standing between
untrusted same-JVM code and a maximally dangerous capability is a `SecurityManager`-conditional
permission check of the form `if (sm != null) sm.checkPermission(...)`. JEP 486 ("Permanently
Disable the Security Manager," shipped JDK 24) means `sm` can never again be non-null on any vanilla
OpenJDK 24+ — so every check of this shape is not merely *usually unconfigured* (the pre-486 norm,
where an operator could in principle close the gap by installing an SM and a policy) but **dead code
by design, permanently, with no future path to ever re-enable it**. That distinction — "usually off"
versus "permanently, structurally impossible to turn on" — is the actual point of this document.
DirtyChai, by retaining a genuinely installable `SecurityManager`, keeps every one of these checks
functional; this document exists to make that contrast concrete and citable, not hypothetical.

---

## 1. Finding 1 — `Unsafe`/`ReflectionFactory` constructor bypass (summary; full detail in the companion SOW)

Verified in `SOW-DirtyChai-Serialization-SPI-DER.md` §2.1, empirically, against this same build.
Summary: `sun.misc.Unsafe.allocateInstance(Class)` and
`jdk.internal.reflect.ReflectionFactory.newConstructorForSerialization` both construct an instance of
**any** class in the JVM without ever running its constructor. Both routes have exactly one gate,
each `if (sm != null)`-conditional: `ReflectPermission("suppressAccessChecks")` (via
`AccessibleObject.checkPermission()`) and `RuntimePermission("reflectionFactoryAccess")`
respectively. With no SM installed, both are unconditionally reachable from plain classpath code,
zero flags — confirmed empirically against the DirtyChai build. See the companion SOW for full
citations, the empirical test methodology, and the resulting "gut every method, not just the
constructor" (Fix A) recommendation for OIS/OOS specifically.

---

## 2. Finding 2 — dynamic agent attach, live bytecode redefinition, and JMX MBean control

Verified 2026-07-11, same methodology, same build image. **No DirtyChai-local modifications found
anywhere in `jdk.attach`, `java.instrument`, `jdk.management.agent`, or `java.management`** —
confirmed by grep for `au.zeus`/`DirtyChai`/`CombinerSecurityManager`/`LoadClassPermission` across
those modules' source, all empty. This is unmodified stock OpenJDK behavior.

### 2.1 Self-attach: a property, not an SM permission — default off, but not part of the SM story at all

`sun.tools.attach.HotSpotVirtualMachine` (`src/jdk.attach/share/classes/sun/tools/attach/
HotSpotVirtualMachine.java:63-70`) computes `ALLOW_ATTACH_SELF` once from the
`jdk.attach.allowAttachSelf` system property — a plain boolean, no `SecurityManager` involved.
Unset, it defaults to `false`; the enforcement point (`:86-91`) throws `IOException` for a
self-attach when false. **This gate is independent of SM entirely** — it is inert-or-live regardless
of whether an SM is installed, so it doesn't get weaker under JEP 486, but it also doesn't get
stronger from DirtyChai retaining SM. Empirically confirmed: a self-attach attempt with the property
unset threw exactly this `IOException`, not a `SecurityException`.

### 2.2 Cross-process attach: two independent gates, one SM-conditional and now-dead, one OS-level and always-live but narrow

`AttachProviderImpl.attachVirtualMachine` → `HotSpotAttachProvider.checkAttachPermission()`
(`src/jdk.attach/share/classes/sun/tools/attach/HotSpotAttachProvider.java:50-58`):
`SecurityManager sm = System.getSecurityManager(); if (sm != null) sm.checkPermission(new
AttachPermission("attachVirtualMachine"));` — the same `if (sm != null)` shape as Finding 1,
permanently dead on any JEP-486 vanilla JDK. The *other* gate,
`VirtualMachineImpl.checkPermissions` (native, `src/jdk.attach/linux/native/libattach/
VirtualMachineImpl.c:127-178`), `stat()`s the attach socket file and requires matching
OS `euid`/`egid` (or root) — this is **OS-level, entirely independent of SM, and always live**, on
both DirtyChai and vanilla OpenJDK. It confines cross-process attach to the same OS user. It does
**not** defend against a same-user, same-JVM adversary — which is exactly the threat model JGDMS's
own in-process untrusted-codebase design (JERI, `ConcurrentPolicyFile`, per-`ProtectionDomain`
permissions) exists to address. For that threat model, `AttachPermission` is the only JVM-level
control, and self-attach (2.1) doesn't even reach this gate.

### 2.3 `redefineClasses`/`retransformClasses`: no SM gate at all — the entire decision is made once, at attach time

`sun.instrument.InstrumentationImpl.redefineClasses` (`src/java.instrument/share/classes/sun/
instrument/InstrumentationImpl.java:236-252`) and `retransformClasses` (`:217-226`) perform only
capability/null checks before calling the native `redefineClasses0`/`retransformClasses0`
(`:452-456`) — **no `System.getSecurityManager()`/`checkPermission` anywhere in either method**, and
none in the `java.lang.instrument.Instrumentation` interface itself. Once an agent holds an
`Instrumentation` instance (via 2.1 or 2.2), rewriting the bytecode of any already-loaded class is
completely ungated. **Empirically confirmed end-to-end** against the real DirtyChai build: with no
`SecurityManager` installed and only `-Djdk.attach.allowAttachSelf=true` set, a standalone harness
self-attached, loaded an agent, called `inst.redefineClasses(...)`, and the target method's observed
return value changed from its original value to the redefined one — live code rewriting, zero
permission checks, confirmed by direct observation, not inference. (The JVM printed its own stock
"Java agent loaded dynamically... will be disallowed by default in a future release" warning — an
orthogonal hardening trend in upstream OpenJDK, not a permission gate today.)

### 2.4 JMX: three permissions, all `if (sm != null)`, all stock, all dead without SM

- `MBeanPermission` — `com/sun/jmx/interceptor/DefaultMBeanServerInterceptor.java:1772-1785`,
  guarding MBean registration/invocation via `checkMBeanPermission` wrappers at `:469,515,580,675,767`
  (each itself `if (sm != null)`-guarded).
- `MBeanTrustPermission("register")` — same file, `checkMBeanTrustPermission` (`:1787-1806`).
- `MBeanServerPermission` — `javax/management/MBeanServerFactory.java:408-416`, reached from
  `createMBeanServer`/`newMBeanServer`/`findMBeanServer`/`releaseMBeanServer` (`:152,229,312,361`).

All three: confirmed by reading source (not separately exercised against a live `MBeanServer` under
an installed SM — flagged as not exhaustively checked). Same shape, same conclusion.

### 2.5 The contrast, stated plainly

On vanilla OpenJDK 24+ (JEP 486, SM permanently unavailable): dynamic agent attach requires either a
system property or being a same-OS-user process — neither requires defeating any SM, because none
can ever exist — and once attached, live bytecode redefinition and JMX MBean control are both
completely ungated at the JVM-permission level, forever, by design. On DirtyChai, which retains a
genuinely installable `CombinerSecurityManager` + least-privilege `ConcurrentPolicyFile`,
`AttachPermission("attachVirtualMachine")` and the three JMX permissions above are **real,
functional, withholdable-from-untrusted-codebases controls** — the same class of "never grant this
to untrusted code" permission as Finding 1's `suppressAccessChecks`/`reflectionFactoryAccess`. Not
yet spot-checked (unlike Finding 1, where the companion SOW confirmed via grep that neither
permission appears in any JGDMS production/example policy): whether any current JGDMS policy grants
`AttachPermission` or the JMX permissions outside QA scaffolding — a worthwhile, low-cost follow-up
audit, not performed here.

---

## 3. Why this belongs in one document rather than two

Findings 1 and 2 are not about the same JDK subsystem — one is object construction, the other is
dynamic code/management control — but they share the exact same logical shape (`if (sm != null)`
gate, permanently dead post-JEP-486, functional only where SM remains installable) and the exact
same practical conclusion (a correctly-scoped DirtyChai/JGDMS policy must withhold a specific,
identifiable set of permissions from untrusted codebases: `suppressAccessChecks`,
`reflectionFactoryAccess`, `attachVirtualMachine`, and the JMX MBean permissions). Documenting them
together makes the pattern legible as a pattern, not two unrelated one-off findings — and gives the
pron98 Reddit-debate thread (and any future version of it) a second, independently-verified example
beyond serialization specifically: SecurityManager's removal did not just leave classic Java
Serialization's gadget-chain risk unaddressed, it took away the only mechanism that could ever gate
live JVM bytecode rewriting and management-plane control for any JVM going forward.

---

## 4. Addendum (2026-07-11): process/container isolation, and same-VM self-attach

Two follow-on points from a design conversation about whether OS-level process isolation is a
sufficient backstop for §2's findings. Both confirmed to the same evidentiary standard as the rest
of this document — empirical where stated, inferred-from-documented-behavior where flagged as such.

### 4.1 Cross-process attach (§2.2) is weaker than this machine's own default OS hardening

Confirmed on the development machine used for this document: `/proc/sys/kernel/yama/ptrace_scope`
reads `1` — `PTRACE_SCOPE_RESTRICTED`, the Ubuntu/Debian default since ~2010 (Yama LSM). Under this
setting, even two processes owned by the *same* OS user cannot `ptrace`-attach to one another unless
one is a direct child of the other (or explicitly permitted via `prctl(PR_SET_PTRACER)`) — the
kernel's own hardening against same-user, unrelated-process code injection.

The JVM's cross-process attach gate (`VirtualMachineImpl.c:checkPermissions`, §2.2) consults none of
this — it checks only matching `euid`/`egid` on the attach socket file, with no parent-child
requirement. So on a host with the OS's own ptrace hardening already active and doing real work, a
same-UID, otherwise-unrelated sibling JVM process can still reach in via the Attach API and redefine
bytecode with zero further checks (§2.3) — a channel that bypasses hardening the OS itself already
applies to the native equivalent of the same attack.

**Refined practical consequence:** the requirement isn't strictly "one OS user per SPIFFE principal"
— it's "one PID namespace per mutually-distrusting principal." A properly namespaced container
(separate PID namespace — the Docker/Kubernetes default) closes this specific vector regardless of
UID, because the target PID isn't addressable across the namespace boundary at all. UID separation
on a *shared* PID namespace (several services on one host or VM without container isolation) is the
weak form and still needs distinct users to get an equivalent guarantee; container-per-service
already gets the strong form for free. This does **not** help the one case that matters most for
this codebase's own architecture: multiple mutually-distrusting principals deliberately co-resident
in the *same* JVM process (JGDMS's core design — many principals, one process, discriminated by
`ProtectionDomain`) gets zero benefit from any process or namespace boundary, and falls back entirely
on §4.2.

### 4.2 Self-attach, if enabled: no discrimination between callers is possible without SM

Reconstructing the check order from §2.1/§2.2's own evidence (not separately re-run, but directly
supported by the existing empirical result): `AttachProviderImpl.attachVirtualMachine` calls
`checkAttachPermission()` (SM-conditional, §2.2) *before* constructing `VirtualMachineImpl`, which —
since `VirtualMachineImpl extends HotSpotVirtualMachine` on Linux — runs `HotSpotVirtualMachine`'s
own constructor (containing the `ALLOW_ATTACH_SELF` check, §2.1) as part of that construction,
*before* `VirtualMachineImpl`'s own body runs the OS-uid `checkPermissions`. This ordering is
confirmed, not assumed: §2.1's Run 1 (property unset, no SM) observed an `IOException` from the
`ALLOW_ATTACH_SELF` check specifically, not a `SecurityException` — meaning `checkAttachPermission()`
had already run and silently passed (`if (sm != null)` false) before the self-attach guard fired.

Consequence: with no SM installed, `checkAttachPermission()` is a no-op for every caller, and if
`jdk.attach.allowAttachSelf=true` the remaining OS-uid check trivially passes for self. Nothing left
in the chain can distinguish trusted from untrusted code within the process — **any** code running in
that JVM, including a class that's supposed to be sandboxed under a restrictive `ProtectionDomain`,
can self-attach, load an agent, and use the already-confirmed-ungated `redefineClasses`/
`retransformClasses` (§2.3) to rewrite the bytecode of any other loaded class in the same process —
in principle including the very classes that would otherwise enforce authorization.
`Instrumentation.isModifiableClass()` excludes classes only by structural category (primitives,
arrays, certain hidden/dynamically-generated classes), not by package or security sensitivity;
`SecurityManager`/`AccessController`/`Policy`/`ProtectionDomain` are ordinary, non-hidden,
bootstrap-loaded classes and satisfy it under standard JDK behavior. **This specific target (rewriting
a security-critical core class's method body) was not independently re-tested against DirtyChai's
build in this pass** — it follows from general, stable JDK `redefineClasses`/`isModifiableClass`
semantics rather than a fresh empirical run, unlike the rest of this document's claims, and is flagged
here as inferred, not verified, pending a dedicated test if this becomes citable material.

On DirtyChai specifically, `checkAttachPermission()` runs *first* in this same chain, so a
correctly-scoped policy denying `AttachPermission` to untrusted code already blocks self-attach too,
independent of the `allowAttachSelf` property's value. That should not be relied on as the sole
control: the property should stay at its stock default (`false`) in any JGDMS/DirtyChai deployment
regardless of SM/policy state, since nothing in this codebase's own design needs dynamic
self-instrumentation, and defense-in-depth costs nothing here.

---

## References

- `SOW-DirtyChai-Serialization-SPI-DER.md` §2.1 — Finding 1 in full (empirical test methodology,
  full citations, the resulting Fix A recommendation).
- `src/jdk.attach/share/classes/sun/tools/attach/HotSpotVirtualMachine.java:63-70,86-91` — self-attach
  property gate.
- `src/jdk.attach/share/classes/sun/tools/attach/HotSpotAttachProvider.java:50-58` — `AttachPermission`
  SM-conditional gate.
- `src/jdk.attach/linux/native/libattach/VirtualMachineImpl.c:127-178` — OS-uid attach-socket check
  (always live, cross-process only).
- `src/java.instrument/share/classes/sun/instrument/InstrumentationImpl.java:217-252,452-456` —
  `redefineClasses`/`retransformClasses`, no SM gate.
- `com/sun/jmx/interceptor/DefaultMBeanServerInterceptor.java:1772-1806` — `MBeanPermission`/
  `MBeanTrustPermission` checks.
- `javax/management/MBeanServerFactory.java:408-416` — `MBeanServerPermission` check.
- JEP 486, "Permanently Disable the Security Manager" — the reason every check cited above is dead
  code by design on any JDK it applies to.
- `docs/security-articles/attack-model-vs-kernel-isolation.md` — public-facing article these findings
  are candidate evidence for; not yet incorporated there (open decision, not made in this document).
- `SOW-JMX-JERI-DER-Connector.md` — companion SOW proposing to obsolete the RMI-based JMX connector
  that §2.4's `MBeanServerPermission`/`MBeanPermission` findings partly concern.
