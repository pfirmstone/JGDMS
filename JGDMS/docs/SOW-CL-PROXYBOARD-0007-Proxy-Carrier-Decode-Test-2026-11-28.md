# Scope: a test that settles CL-PROXYBOARD-0007 (proxy carrier decoded from an untrusted stream)

Written 2026-11-28 by Claude Fable 5.2, proxy-board review session 018wGHqgexvwKfVXuCVJMAzR, at the operator's request ("You can write a scope for the coding agent.").
Source commit read: 45f719aa7f342f69ba224c0820ff49e839c87db9 (trunk).

## 1. The claim

CL-PROXYBOARD-0007 says: an Atomic DER stream can carry a `DerProxySerializer` in any slot, and decoding it makes the receiver call `getClassAnnotation()` on an endpoint named in the stream and can then build a codebase loader; with `net.jini.loader.pref.smartProxyIsolation.enabled=true` the provider fails closed only after that call.

The claim is settled by a test, not by reading. This scope is for that test only.

## 2. What reading shows (by this session, 2026-11-28; nothing run)

Paths are under `jgdms-der/src/main/java/au/net/zeus/jgdms/der/object/` unless a module is named.

| Step | Where | What it does |
|---|---|---|
| 1 | `ObjectCodec.java` 594 to 609, `admissibleConstructClass` | Clause 3 admits any class that implements `Resolve`, whatever the slot's declared type. Its own comment (near 582) names `DerProxySerializer` as the one such class with an active `readResolve()`. |
| 2 | `ObjectCodec.java` 2363 to 2367 | After the nested decode, `DerReplacer.resolve(decoded)` calls `readResolve()`. The slot's typed cast comes after this, in the caller. |
| 3 | `DerProxySerializer.java` 149 to 158 | The decode constructor reads `handler` (an `InvocationHandler` slot) and `serviceProxy` (a `MarshalledInstance`) from the stream. |
| 4 | `DerProxySerializer.java` 175 to 185 | `readResolve()` builds a `CodebaseAccessor` proxy around the decoded handler and calls the registered `ProxyCodebaseSpi.resolve`. With no provider registered, the default (260 to 266) calls `serviceProxy.get(parentLoader, true, verifierLoader, context)`. |
| 5 | jgdms-pref-class-loader `net/jini/loader/pref/PreferredProxyCodebaseProvider.java` 1928 to 1934 | `setConstraints` on the bootstrap proxy when the context carries constraints. |
| 6 | same file 1960, and `ServerIdentityPin.java` 100 to 135 | `ServerIdentityPin.probe`: if the handler is a platform JERI handler class it invokes `authenticateServer` on it. If it is not, probe returns null and logs a warning. |
| 7 | same file 1969 | `bootstrapProxy.getClassAnnotation()`. |
| 8 | same file 2040 to 2060 | The isolation check. It refuses here, after steps 6 and 7. |

Two things the claim's wording does not say, both INFERRED from reading:

- With a platform JERI handler, step 6 comes before step 7. If `authenticateServer` opens a connection, the first outbound contact is the probe, not `getClassAnnotation()`. NOT CHECKED: whether `authenticateServer` connects.
- With a handler class that is not a platform JERI handler, step 7 invokes a handler object the stream supplied. Which handler classes can be decoded into the `InvocationHandler` slot is NOT CHECKED.

NOT CHECKED: which permission checks run before step 4 under a security manager, and whether any of them would stop an untrusted stream's carrier. The claim's note says the `DeSerializationPermission`, integrity and signer checks and the digest-gated `LoadClassPermission` apply.

## 3. Rules

- Tests only. No change to any file under `src/main`. If a test cannot be written without a production change, stop and report.
- No commit and no push. Leave the work in the tree and report.
- Do not write to the claim register. This session records the result.
- New test files only; do not edit an existing test or fixture. Existing tests to read first: `ClauseThreeResolveProxyTest`, `DerProxyProviderFailLoudTest`, `DerProxyMarshalledInstanceTest`, `DerProxyRequiredFormatTest` and the fixture `marshal/CapturingProxyCodebaseProvider`, all under `jgdms-der/src/test`.
- The security manager is always on. Do not use `-Djava.security.manager=allow`. State in the report which policy each test ran under.
- Label every statement in the report VERIFIED (ran it, output attached), RELAYED, INFERRED or NOT CHECKED. Record the commit.
- Each test states the invariant it defends in its Javadoc and cites CL-PROXYBOARD-0007.

## 4. Steps

**S0. Find the home for the tests.** Report whether `jgdms-der`'s test classpath includes `jgdms-pref-class-loader`. If it does not, name the module whose tests can see both `ObjectCodec` and `PreferredProxyCodebaseProvider`, and put the tests there. Do not add a dependency to a pom without reporting first.

**S1. Build the stream.** Encode a `DerProxySerializer` carrier whose handler is a recording handler: a test class that counts every `invoke` and records the method name, and makes no network call. Report how the handler was made decodable (which annotations, which permissions).

**S2. The tests.** Each asserts the invariant *decoding an untrusted Atomic DER stream makes no remote call and loads no class from a codebase*. Each is expected to fail today if the claim is true. A failing test is the result; do not weaken an assertion to make it pass, and do not mark it ignored.

| Test | Stream | Receiver | Asserts |
|---|---|---|---|
| C1 | Carrier in an `Object`-typed slot | `PreferredProxyCodebaseProvider` registered, isolation property unset | Handler `invoke` count is 0 when decode returns or throws |
| C2 | Carrier in a narrowly typed slot whose type the resolved value cannot satisfy | as C1 | Decode fails, and handler `invoke` count is 0 at the time it fails |
| C3 | as C1 | as C1, with `net.jini.loader.pref.smartProxyIsolation.enabled=true` | Decode fails closed, and handler `invoke` count is 0 |
| C4 | as C1 | No `ProxyCodebaseSpi` registered | Report what the default does with `serviceProxy`; assert no class is defined by any loader other than the test's own |
| C5 | as C1 | as C1, with a tripwire parent loader that records every `loadClass` and `defineClass` | No codebase loader is created and no class is defined from the stream's codebase |
| C6 (if S3 says it is possible) | Carrier whose handler is a platform JERI handler pointing at a local `ServerSocket` opened by the test | as C3 | The socket accepts no connection |

For every test, record the order of events observed (constructor, `readResolve`, probe, `getClassAnnotation`, isolation refusal), with the stack trace of the first handler call.

**S3. The probe.** Read `authenticateServer` on the platform JERI handler class and report whether it opens a connection. If it does and a JERI handler can be built in a test without a live service, write C6. If not, say why and stop there.

**S4. Show each test is load-bearing.** For each test that passes today, show it fails when its invariant is broken on purpose (for example, a recording handler that is called once by the test itself). For each test that fails today, attach the failure output; that is the evidence.

## 5. Report

One file, `docs/RESULTS-CL-PROXYBOARD-0007-proxy-carrier-decode-test.md`: the commit, the module chosen in S0, the policy used, a row per test (pass or fail, the first handler call's stack, the event order), the S3 answer, and anything that was not run and why.

## 6. Not in this scope

- Any fix. Closing clause 3, moving the isolation check ahead of the probe, or keeping carriers out of a host's decode path are design decisions for the operator. The admission gate is rewritten under Part A of `SOW-ObjectCodec-Class-Admission-and-Code-Download-Reduction-2026-11-28.md` (step A3); these tests can be reused there.
- The JOSS-side `ProxySerializer` in jgdms-platform.
- DirtyChai. Nothing there is changed.
