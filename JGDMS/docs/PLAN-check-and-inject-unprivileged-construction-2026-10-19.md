# Plan (documented, NOT scheduled): refuse to deserialize into a privileged context, then construct unprivileged

Status: **documentation only.** The maintainer has said this work is not planned to start; this records the design so it
can be picked up later. Nothing here is implemented. Read with
`docs/ANALYSIS-legacy-API-authority-and-reconstruction-2026-10-19.md` §2, whose reviews say the plan below is **not sound on its
own** until the three prerequisites in §6 exist.

## 1. Maintainer positions this plan rests on

- Deserialization gadget attacks have succeeded by deserializing *into a privileged context*. In JGDMS the privileged context is
  reduced to `java.base`, much smaller than the OpenJDK 21 trusted computing base.
- The context that matters is **the class domains that run the constructors, `check(GetArg)` and input validation**: the classes
  that must be trusted to deserialize, check invariants and validate input. That is the context that must not be privileged. It
  is not the caller's stack, and it is what the existing `DeSerializationPermission` ATOMIC/PROXY gates already build (a context
  made from the `ProtectionDomain`s of the classes about to be constructed).
- "Privileged" is not only `AllPermission`: also creating or setting a `SecurityManager`, creating a `ClassLoader`, etc. A
  `ClassLoader` subclass (including a child loader) is caught because its domain would need `createClassLoader`; no type list is
  needed. Name lists are rejected (a name is not an identity; permission checks decide what may be reconstructed).
- Principals are not usually granted these permissions, so refusing a privileged class-domain context costs little.
- After validation completes it is safe to have privileges again, so construction can run with an unprivileged domain injected
  onto the stack.
- Residual risk, accepted and to be documented: a deserializing class may itself call `doPrivileged`, to reduce or to escalate.
  Static analysis can find the presence of `doPrivileged` but not whether it reduces or escalates; the `AccessController` API
  does not allow that to be decided statically. This can only be made visible, not eliminated (see the analysis doc §2 for the
  API redesign that would make it decidable).

## 2. What exists today (found by exploration)

- The gates are `SecurityManager`-only: with no `SecurityManager` installed (stock JDK 24+) they are no-ops.
- Nothing anywhere asks "is this context privileged".
- Nothing in the DER read path calls `doPrivileged`.
- The server decodes arguments under the *exporter's* captured context (`Target.dispatch`, `ObjectTable`); the client reads
  responses under the caller's context.
- Gate seams: `jgdms-der/.../object/ObjectCodec.java` (`checkAtomicDeSerializationPermitted`, `checkProxyDeSerializationPermitted`,
  ~lines 213-283; construction sites `ctor.newInstance` ~330-345, ~778-800, ~2512-2530; proxy ~2410) and
  `jgdms-der/.../stream/DerObjectStreamCodec.java` (PROXY gate ~2147-2175, call ~1747). Line numbers are approximate and will have
  drifted.

## 3. Approach: extend the existing gate seams, add no new layer

1. **Check (reject).** In the seam that builds the class-domain context, after the existing `DeSerializationPermission` check, test
   that context against a fixed, hard-coded dangerous-permission set by asking the `SecurityManager`
   (`sm.checkPermission(p, ctx)`, as the gates already do, so audit and combiner managers see it). If any permission is granted,
   throw `SecurityException` ("deserializing classes hold privileged permission X"). The set: `AllPermission`; `RuntimePermission`
   `createClassLoader`, `setSecurityManager`, `setContextClassLoader`, `getClassLoader`, `setFactory`; `SecurityPermission` for
   policy, provider and `createAccessControlContext`; `ReflectPermission` `suppressAccessChecks`; `AuthPermission` `doAsPrivileged`.
   `java.base` domains are the only exemption. **Not configurable**: fail-closed, operators cannot weaken it.
2. **Inject (shed).** Run each guarded construction under
   `AccessController.doPrivileged(action, restricted)` where `restricted` is `Security.create(classDomains + one unprivileged
   marker domain)` (reuse `net.jini.security.Security.create`, `Security.doPrivileged`, `withContext`). The marker domain (no
   `CodeSource`, empty permissions) caps the intersection, so the constructors run unprivileged; privilege returns when
   `newInstance` returns, i.e. after validation.
3. **No `SecurityManager`:** a no-op, documented, like every other DER gate.
4. **Document the residual** in the SOW and Javadoc: a class that calls `doPrivileged` during construction can escape the
   injected domain. Optionally flag `AccessController.doPrivileged` in `@AtomicSerial` constructors by build-time analysis, but
   make no runtime claim.

## 4. Open points to settle before coding

- Test the context with `checkPermission` (throws; audit-friendly, but cannot say which permission without probing each, so the
  fixed set is probed one by one) versus a policy `implies` query.
- Whether the injected domain also needs the `Subject` combiner preserved (`Security.createPrivilegedContext` keeps the combiner)
  so principals-based checks inside constructors still work.
- JGDMS framework jars are conventionally granted `AllPermission`; the rule makes least-privilege grants mandatory for deserialized
  classes (the repo's polpAudit / PolicyCondenser tooling supports producing them). The `jgdms-jeri` `test.policy` grants
  `AllPermission` and would need a stricter policy for the real-`SecurityManager` tests.

## 5. Verification plan

- Unit, in the style of `DeSerializationPermissionGateTest` with injected `SecurityManager` subclasses: a context granting
  `AllPermission`, then each dangerous permission alone, is rejected; a least-privilege context passes; `java.base` is exempt; no
  `SecurityManager` is a no-op. The same for the PROXY gate.
- Injection: a fixture `@AtomicSerial` class whose constructor records `AccessController.getContext()` and attempts a privileged
  action (`createClassLoader`) under a real `SecurityManager`; it must be denied with injection on and allowed with it off (shows
  the test bites). Pattern: `AgentDelegationCeilingTest` (real `SecurityManager` with restore) plus surefire
  `-Djava.security.manager=allow`.
- Mutation: remove each probe and the injection, and require a failing test.
- Full `jgdms-der`, `jgdms-jeri`, `jgdms-cel` suites; benchmarks unaffected without a `SecurityManager` (check no regression).

## 6. Why this is not sound alone (from the two board reviews; analysis doc §2)

1. **An empty marker domain is bypassed by ordinary code**: a constructor needing even a property read calls plain
   `doPrivileged`, which cuts the stack and drops the marker. A non-truncatable ceiling barrier with a non-empty ceiling needs a
   change in the DirtyChai `AccessController` itself (a fork change; acceptable if OpenJDK compatibility may be broken).
2. **Combiner handling**: three incompatible combiners coexist; the combiner on the real stack can widen domains after the check
   (time-of-check/time-of-use), so build and run construction under the same combiner-free context.
3. **Two-phase decode**: the marker-domain construction cannot work until decode stops doing privileged work (codebase fetching,
   remote calls in `readResolve`, `Security.grant`, DGC dirty calls): decode values, admit, then resolve proxies and codebases
   outside the constructor.

Also: a denylist of dangerous permissions fails open (it misses file write/execute, sockets, property write, `loadLibrary`,
`exitVM`, `modifyThread`, JMX, `GrantPermission`, `DelegationPermission`); a ceiling allowlist enforced at use is the sound form,
and the fixed set above is only an early rejection.
