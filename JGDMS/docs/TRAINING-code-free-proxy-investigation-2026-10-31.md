# Training: investigating whether a service can use a code-free proxy

Written 2026-10-31 from the Derek (DER-only registrar) work. Read this before investigating any service.
Primary record: `DESIGN-class-free-lookup-and-registrar-split-2026-10-31.md` (the "memo"); section numbers below refer to it.
All paths in briefs and reports are absolute. A relative path failed a reviewer on 2026-10-30.

## 1. The question

A smart proxy is downloaded code that runs in the client. A code-free proxy carries only data:

| Tier | What crosses the wire | Runs in the client |
|---|---|---|
| Dynamic proxy | interface names, an `InvocationHandler` that is itself `@AtomicSerial` data (endpoint + constraints) | no downloaded code; the client already has the interfaces |
| DER data | `@AtomicSerial` values decoded by DER | no |
| CEL expression | an expression string evaluated by a fixed local evaluator | the evaluator only |
| Smart proxy | a class downloaded from a codebase | yes |

For each service, find out which tier its client-visible behaviour needs, and what stops it from being code-free today.

## 2. What the Derek work established (and how it is labelled)

Labels: VERIFIED = read in code or run; RELAYED = from an agent report not re-checked; PLAUSIBLE; UNDETERMINED. Use them.

1. **The format is chosen by the sender.** A nested `MarshalledInstance` names its own payload format on the wire (default JOSS). A DER call that required `ATOMIC_DER` still decoded a JOSS payload inside a `DerProxySerializer` carrier. VERIFIED by a unit test with no SecurityManager. Fixed on 2026-10-31 (commit `62670b2ad`): the required format now travels from the call (`BasicInvocationHandler.invokeRemoteMethodOnce`) through `AtomicDerInvocationHandler`, `ResolutionContext` and `DerGetArg` to `DerProxySerializer`, and `MarshalledInstance.get` rejects a payload that does not satisfy it. The DER dispatcher requires ATOMIC_DER for call arguments. Memo 11.1 and 11.2.
2. **Enforcement exists only where a requirement was set.** A client that asks for no format is unprotected. The service wrapper must set `ATOMIC_DER`. Nothing here sets it by default.
3. **Fail-open is the recurring defect.** An error, a missing setting or an unknown input leaves the host running code or trusting decoded data. Memo section 11 lists 13 paths and the board review added 9. Examples: a null filter downloads the proxy; discovery trusts a self-asserted ServiceID; swallowed `Error`s turn a hostile entry into a wildcard; trust verifiers are OR'd.
4. **`Resolve` classes are admitted in any slot** by `ObjectCodec.admissibleConstructClass`, and `readResolve` runs before the handler type check. Unchanged; still open.
5. **Do not drop a layer without auditing it.** Interface stripping cannot remove `RemoteMethodControl`; JOSS providers cannot be removed by one jar (24 providers in two modules). Claims of this kind were wrong in my first draft and were caught by reviewers.
6. **Dynamic proxy needs the interfaces locally.** Anything the client must call that is not in a locally present interface needs a different tier or stays a smart proxy.

## 3. How to investigate one service

Answer each of these with a file:line, a command, or the words NOT ON DISK. A grep hit is not a citation: read the line.

1. What does the service export today? Name the proxy class(es) and the exporter configuration (`ProxyAccessor`, `ConstrainableXProxy`, admin proxies, event listeners/leases).
2. Does the client-visible proxy contain logic beyond forwarding calls to the endpoint? List it (caching, local state, retry, entry handling, `readResolve`, `equals`/`hashCode` rules, trust verification).
3. Which interfaces must exist on the client? Are they all in a library jar the client has, or only in the service's download jar (`-dl`)?
4. Which arguments, return values and exceptions cross the wire? For each: is it `@AtomicSerial` or `Serializable` only? Name any that are not (count them by noun: types, fields, call sites).
5. Which proxies are themselves passed as values (leases, registrations, admins, listeners)? Each is another proxy that must also be code-free.
6. Does any path load a class from a codebase annotation (`MarshalledInstance`, `RMIClassLoader`, `ProxyCodebaseSpi`)? Name it. Which of the fail-open paths in memo section 11 apply to this service?
7. What tests exist, and what would a test that fails when code-free is violated look like? (Pattern: a test with the enforcement removed must fail. See the three tests added on 2026-10-31.)
8. Verdict: CAN (dynamic proxy already sufficient), CAN WITH CHANGES (list them, smallest first), CANNOT (name the behaviour that needs downloaded code). Separate what you verified from what you inferred.

Do not write production changes. Report only. If a question can be settled by the operator looking at an artefact, stop and surface it.

## 4. Traps measured in this project

- **Relative paths.** Always absolute in briefs.
- **Maven.** Run from the reactor root `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS` with `-o -pl <modules>`. Testing `jgdms-jeri` alone picks up a stale `jgdms-der` jar from the local repository: use `-pl jgdms-platform,jgdms-der,jgdms-jeri`.
- **A test that passes both ways proves nothing.** Break the mechanism, show the test fail, restore, show it pass (`cmp` against a backup).
- **A fake that fails the wrong way.** A fake response that returned a new stream on each call made the control case fail too. Check the control before trusting the failing case.
- **Counts need nouns and sources.** "24 JOSS providers in two modules" is a count with its noun and scope; "many" is not.
- **Reviewer model must differ from the producer's.** Sonnet findings go to Opus and Fable.
- **DirtyChai changes are human-authored** (OpenJDK policy). JGDMS has no such rule.

## 5. Report format

One file per service at `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\docs\INVESTIGATION-code-free-<service>-2026-10-31.md`: the eight answers in section 3, each labelled VERIFIED / RELAYED / PLAUSIBLE / UNDETERMINED, then a verdict and a "smallest next change" list. Under 250 lines.
