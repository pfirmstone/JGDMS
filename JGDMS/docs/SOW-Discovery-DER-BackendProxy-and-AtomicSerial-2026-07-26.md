# SOW — reggie discovery 0-events (DER backend-proxy codebase) + ConstrainableAdminProxy NPE + @AtomicSerial skill

**Session date:** 2026-07-26. Full research/design in the approved plan
`~/.claude/plans/dazzling-finding-liskov.md` (read it first — it has all six-agent evidence with file:line).

## UPDATE 2026-07-26 (session 2) — reconciled + Bug2/skill landed + Bug1 diagnostics placed
- **trunk reconciled:** merged `origin/trunk` (4 commits: Outrigger CEL EntryProjection) into local; local trunk
  now `13d56f0bf` = my `e7762cabf` + merge, **2 ahead of `origin/trunk`, UNPUSHED** (disjoint files, clean merge).
- **Branch `fix/reggie-discovery-backend-proxy`** off reconciled trunk holds two commits:
  `78a53e5f7` (Bug 2 ConstrainableAdminProxy fix) + `f1db486e8` (jgdms-atomicserial skill, git add -f). **UNPUSHED.**
- **Bug 1 diagnostics placed (UNCOMMITTED, working tree):** 4 `System.err.println` tagged `BUG1-DIAG` in
  RegistrarImpl.getClassAnnotation, ProxySerializer.readResolve, PreferredProxyCodebaseProvider.resolve (x2).
  Removal map + decode decision-table: `scratchpad/bug1-diagnostics-removal-map.md`. **Next: rebuild
  jgdms-platform + jgdms-pref-class-loader + reggie-service, rerun joinmanager, grep BUG1-DIAG, pin X vs Y.**
- Peter's `qa/harness/policy/default{securetest,securephoenix}.policy` (polpAudit output) still uncommitted — not ours.

## Repo state at handoff (IMPORTANT)
- **LOCAL `trunk` = `e7762cabf`** (registryFilter complete). It is **1 ahead of `origin/trunk` (UNPUSHED)**,
  and `origin/trunk` is **4 ahead** of local (Peter pushed more) → local trunk has diverged; **pull/reconcile
  needed**, and `e7762cabf` still needs pushing.
- **UNCOMMITTED (this session):**
  - `services/reggie/reggie-dl/.../proxy/ConstrainableAdminProxy.java` — Bug 2 fix (compiles clean).
  - `.claude/skills/jgdms-atomicserial/SKILL.md` — new skill, **untracked** (`.claude` is gitignored →
    needs `git add -f`).
  - (also present, NOT ours: `qa/harness/policy/default{securetest,securephoenix}.policy` = Peter's polpAudit output.)
  - **TODO next session:** commit Bug 2 + `git add -f` the skill (branch off reconciled trunk), then Bug 1.

## Registry-filter saga — COMPLETE this session (the big win)
Phoenix `registry.bind` now succeeds; shared group + reggie come up. Merged to local trunk (see
memory `jgdms-jrmp-registry-rejects-jeri-proxy-filter`): daemon-thread fix, EC/ECDSA keyType fix
(`jgdms-rmi-tls`), and the JRMP registryFilter allow-list — final value derived by real serialize/deserialize
(`net.jini.core.constraint.*;net.jini.security.proxytrust.TrustEquivalence;net.jini.jeri.**;net.jini.constraint.*;net.jini.id.*;java.lang.Enum;java.lang.reflect.Proxy`).
Lesson: to allow-list a JERI/dynamic-proxy graph through a JOSS ObjectInputFilter, serialize a REAL instance
through a logging filter — superclasses, enum bases, java.lang.reflect.Proxy, concrete impls all get checked.

## What this session did (post-registry)
1. **Bug 2 (DONE, uncommitted):** `ConstrainableAdminProxy(GetArg)` read the superclass `server` field
   pre-`super(arg)` from the wrong per-class namespace → null → `verifyConsistentConstraints` NPE at teardown.
   Fixed to `super(arg)` first, then `check(arg, server)` against the inherited field — mirrors the landed
   `ConstrainableRegistrarProxy` fix (`1491e2b80`). Compiles. (No standalone unit test: needs a ~40-method
   serializable Registrar double; covered by the e2e joinmanager teardown path.)
2. **@AtomicSerial skill (DONE, untracked):** `.claude/skills/jgdms-atomicserial/SKILL.md` — the per-class
   private GetArg namespace rule, the pre-`super(arg)` anti-pattern (broken vs fixed), super-first recipe,
   sibling backlog. Shared (git add -f like existing jgdms-* skills). Peter's request.
3. **Bug 1 (NOT STARTED — the primary):** see below.

## Bug 1 — NEXT SESSION'S MAIN WORK (discovery 0-events)
**Symptom:** joinmanager test fails `0 discovery events`; repeated `ProxyWireSupport.resolveTolerant WARNING:
DER [8] proxy: interface "org.apache.river.reggie.proxy.Registrar" is not locally resolvable; dropping it`.

**Root (evidence-backed):** reggie's smart proxy arrives via the CodebaseAccessor bootstrap, but its nested
backend `[8]` proxy is DER-decoded against a **codebase-less loader** (not a reggie-dl `PreferredClassLoader`),
so `resolveTolerant` drops `Registrar` → `RegistrarProxy.server` can't rebuild → unmarshal fails → 0 events.
The drop is at `serviceProxy.get(loader)` (`PreferredProxyCodebaseProvider.java:~2164`) where `loader` is NOT
reggie-dl. Two candidate roots — **PIN with runtime data before fixing:**
- (Y, leading) reggie's `getClassAnnotation()` returns EMPTY. Config entry `codebaseAnnotation` defaults `""`
  (qa `reggie.config` doesn't set it) → falls to `CodebaseProvider.getClassAnnotation(RegistrarProxy.class)`,
  which yields nothing unless the export codebase reached the reggie group VM — and the startup
  `"Codebase is null"` warning says it didn't. (`RegistrarImpl.getClassAnnotation:700-702`, codebase field
  from config `:5372`.)
- (X) `getProvider(defaultLoader)` finds no provider → no-op SPI (`ProxySerializer.java:243-262`) decodes
  against the base loader.

**Recommended next step:** add targeted diagnostic logging (reggie `getClassAnnotation()` return value; client
`PreferredProxyCodebaseProvider.resolve` chosen SPI + `loader` + annotation), Peter rebuilds+reruns, pin Y vs X.
Then the one-focused fix: make the terminal `serviceProxy.get(...)`/DER `[8]` decode use the smart proxy's
codebase-bearing **endpoint ClassLoader** (Peter's principle: "all stream code resolved through the endpoint
ClassLoader"). Same fix covers the live path AND the dormant isolation path (which reuses the identical
`resolve()`→`serviceProxy.get()` decode verbatim — `SubProcessReconstructionServer:333`). Same family as
commit `af6e5bd13`.

**Design decision pending (Peter, STD-009 §6.4):** whether `resolveTolerant` should ALSO fail-loud (not
silently narrow) when a codebase context is present. Optional safety net; doctrine call.

## Follow-ups / deferred (tracked, NOT this plan)
- **Discovery Atomic-JOSS → Atomic-DER migration** (Peter's direction): move `Plaintext.writeV2/readV2UnicastResponse`
  from `AtomicMarshalOutputStream/InputStream` to the DER codec. Coupled to Bug 1 (backend `[8]` already DER),
  but a separate scoped workstream (both ends, wire version, per-endpoint ClassLoader). Bug 1 first.
- **Constrainable*Proxy sibling backlog** (~20, pre-super namespace): `ConstrainableEventLease:100`,
  `ConstrainableRegistration:59`, etc. The new skill documents it. (memory `jgdms-getarg-namespace-proxy-regression`.)

## Lessons this session
- The reject STACK LINE is diagnostic: `readProxyDesc` = a proxy INTERFACE, `readNonProxyDesc`/`readOrdinaryObject`
  = a class in the graph (incl. superclasses/enum bases/java.lang.reflect.Proxy). Don't enumerate a serial
  graph by inspection — serialize a real instance through a logging filter.
- qa harness `Resolver` treats `$` as a variable sigil → `Class$Inner` in a serverjvmargs value is silently
  eaten; use package patterns, never nested-class binary names.
- `net.jini.security.Security.checkPermission` returns early when SM==null; SM-gated checks are no-ops without
  an SM (SM-off is TEST-ONLY; JGDMS deploys on DirtyChai WITH SM). See memory `jgdms-sm-independent-enforcement-sites`.
