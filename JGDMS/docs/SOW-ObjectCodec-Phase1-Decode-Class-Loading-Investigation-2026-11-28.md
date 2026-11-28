# Scope, phase 1: what a wire-named class can cause in the Atomic DER decoder (investigation and failing tests)

- **Written:** 2026-11-28 by Claude Fable 5.2, proxy-board review session 018wGHqgexvwKfVXuCVJMAzR, at the operator's request ("Yes, write the phase 1 scope first").
- **Commit read:** JGDMS trunk `45f719aa7f342f69ba224c0820ff49e839c87db9`.
- **Parent scope:** `SOW-ObjectCodec-Class-Admission-and-Code-Download-Reduction-2026-11-28.md` (revision 2). This phase is its steps A0, A1 and A2, with five questions added. Defect numbers (O1 to O18), test numbers (T1 to T13) and decision numbers (D5 to D10) are the parent's.
- **Output:** `docs/RESULTS-objectcodec-phase1-decode-class-loading.md`, and new test files. No production change.
- **Suggested agents:** an Opus coordinator; Sonnet sub-agents for the inventory, every row re-opened by the coordinator. The reviewing session reviews the results.

## 1. Why

The operator's position, 2026-11-28: `LoadClassPermission` already ensures only trusted, identified jars are loaded. `DeSerializationPermission`, with the SCAP verdict, already decides whether a class's protection domain is trusted to deserialise. Deserialisation must not break encapsulation or become an administrative task.

The question none of those answers is which trusted class may appear in a given slot. The declared type in the code that reads the slot can answer it without any list. A build-time index (RL-JGDMS-0247) is the other answer on the table.

Which is right depends on facts that have only been read, never run. The operator has said the decisions are not final until a coding agent has investigated. D5 to D10 and the fate of the index wait on this phase.

## 2. The five questions

Each answer is labelled VERIFIED (ran it; output attached), RELAYED, INFERRED or NOT CHECKED.

**Q1. Does loading a wire-named class ever run that class's code?**
Follow `ResolutionContext.loadClass` through `ClassLoading.loadClass` to every provider that can be installed: `PreferredClassProvider`, any OSGi-aware provider, and the JDK `RMIClassLoader` fallback. For each, show by test whether a static initialiser runs on load. State which provider a host, a proxy JVM and a relay each install, and how that is configured. Then list every call on a decode path that does run code of a wire-named class (`Enum.valueOf`, `Proxy.newProxyInstance`, constructors, delegate instantiation in `MarshalDelegates`) with its position relative to the admission check and to the `DeSerializationPermission("ATOMIC")` check.

**Q2. What can a load cause besides defining a class?**
- Which loader defines the class when the endpoint's assigned loader does not have it. `PreferredClassProvider` falls back to a codebase or context loader (lines 622 to 722).
- Whether a name alone can make any loader fetch a jar, open a network connection, or create a new class loader.
- Whether `LoadClassPermission` is checked on each of those paths, by whom, and against which protection domain. Show it with a test under a real policy: a class in a jar without the grant is not defined.
- What `ResolutionContext.NONE` (null loader) resolves against, at each live site that uses it (`EntryRepV2Codec.java` 609, `MarshalledInstanceCodec.java` 185).

**Q3. Where does code of a wire-named class run, or a `Class` get returned, before any check?**
This is the parent's A0 inventory. For every decode-side site in `jgdms-der` main code that turns a wire or schema string into a `Class`, give: file, line, source of the name, the check that exists, whether the check is before or after the load, before or after any code runs, and the `ResolutionContext` used. Start from O1 and O7 to O14. Then write the failing tests in section 4.

**Q4. How many slots are really `Object`?**
For every site in Q3, classify the declared type of the receiving slot:

| Class | Meaning |
|---|---|
| T-used | A declared type exists and reaches the check before construction. |
| T-late | A declared type exists at the call site but is checked only after construction (for example `GetArg.get(name, val, type)`, `readObject(Class)`). |
| T-lost | A declared type exists in the code but is dropped on the way (an overload that passes `Object.class`; reflection that fails open). |
| None | No declared type exists even in principle (`Object` field, raw collection, `Any`). |

Give the counts, and for the None rows say what the platform's own classes put there today. Count callers in `jgdms-platform` and the service modules of the typed and untyped `get` forms (a grep count is enough; label it).

**Q5. What would threading the declared type cost?**
For every T-late and T-lost row: which method signatures change, in which module, whether the change is source or binary compatible, and which existing tests pin the present behaviour (start from the parent's A.2a). Design only. No production change.

## 3. Rules

1. Tests and reading only. No change under any `src/main`. No pom change without reporting first.
2. No commit, no push. No writes to the claim register.
3. New test files only. Do not edit an existing test or fixture.
4. The security manager is always on. Do not use `-Djava.security.manager=allow`. State the policy each test ran under. Run on DirtyChai.
5. DirtyChai is not changed. Standards are not edited.
6. Each test states the invariant it defends in its Javadoc. A test expected to fail today is left failing; do not weaken it and do not mark it ignored. Put new failing tests where they do not break the operator's normal build (a separate test class per defect, named so they can be excluded), and say how to run them.
7. Record `git rev-parse HEAD` and the branch at the top of the results file. If it is not the commit above, re-find every line number.
8. Stop and report if a step cannot be done within these rules.
9. Plain language, short sentences, tables for comparisons.

## 4. Tests

The tripwire is a class whose static initialiser records that it ran, defined by a loader that records every `loadClass` and `defineClass`.

| Test | Shows | Expected today |
|---|---|---|
| P1 (Q1) | For each provider: loading a tripwire by wire name does not run its initialiser. | Unknown; this is the finding. |
| P2 (Q2) | A wire name the endpoint's loader cannot resolve does not cause a fetch, a connection or a new loader. | Unknown. |
| P3 (Q2) | A class in a jar without `LoadClassPermission` is not defined by a decode. | Unknown. |
| T1, T2, T3, T5, T8, T9, T11, T12 | As the parent scope defines them. | Fail. |
| T6 | As the parent defines it. Needs a `jgdms-platform` test. | Fails where one class is registered. |
| T10 | The ATOMIC check precedes every constructor, composed classes included. | To be shown. |
| E1 | An `enum:<name>` field value naming a tripwire enum: its initialiser does not run when the store is built. (O11) | Fails. |
| E2 | The stream `[7]` enum path with a name not assignable to anything expected: no `Enum.valueOf`. (O13) | Fails. |

T4 is `ClassEvolutionTest` line 174; run it and report, do not duplicate it. T7 and T13 are not in this phase.

For each test that passes today, show it is load-bearing: break the invariant on purpose and show the test fails.

## 5. The results file

1. Header: commit, branch, JDK, policy, models per section, which rows the coordinator re-opened.
2. One page: the answer to each of Q1 to Q5 in two or three sentences, and a table of what each answer means for D5 to D10 and for the index. State the consequence; do not make the decision.
3. Q1 to Q5 in full, with the inventory table and the Q4 counts.
4. Test table: name, file, pass or fail, output.
5. Not determined, and why.
6. Deviations from this scope.

## 6. Not in this phase

- Any fix, the admission gate, name-keyed stores, the index or its generator.
- Part B of the parent scope.
- CL-PROXYBOARD-0007's tests, which have their own scope (`SOW-CL-PROXYBOARD-0007-Proxy-Carrier-Decode-Test-2026-11-28.md`). The same agent may take both; the tripwire and recording-handler fixtures can be shared.
