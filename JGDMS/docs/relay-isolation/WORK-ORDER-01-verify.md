# Work order 01: verify the locator and the relay endpoint

Read `README.md` in this directory first. Write your report to `RESULTS-01.md` here.

## Why

Two components were written by agents that could not build the project. They compiled with
JDK 21 preview flags and ran their tests with hand-written JUnit stand-ins. Nothing has been
through Maven, real JUnit, release 25, or a JVM with a security manager. The design session
has not read the code either. This order replaces those claims with real runs and an
independent read.

## What exists (all new files, on this branch)

- `jgdms-der/src/main/java/au/net/zeus/jgdms/der/inspect/`: `EndpointLocator`,
  `EndpointLocatorConfig`, `EndpointFinding`, `package-info`.
- `jgdms-der/src/test/java/au/net/zeus/jgdms/der/inspect/`: `EndpointLocatorTest` (JUnit 5,
  23 tests claimed), `LocatorFixtures`.
- `jgdms-jeri/src/test/java/au/net/zeus/jgdms/der/inspect/EndpointLocatorJeriTest.java`
  (JUnit 4, 4 tests claimed).
- `jgdms-jeri/src/main/java/net/jini/jeri/relay/`: `RelayEndpoint`, `RelayChannelFactory`,
  `RelayChannels`, `RelayOutboundRequest`, `RelayCallToken`.
- `jgdms-jeri/src/test/java/net/jini/jeri/relay/`: eight `RelayEndpoint*Test` classes
  (JUnit 4, 55 tests claimed) and the fixture `RelayEndpointTestRelay`.

Before this work, `mvn -pl jgdms-der test` on the operator's machine ran 1536 tests with 0
failures.

## Tasks

Do them in order. Tasks 1 to 3 change nothing. If task 1 or 2 fails to compile, stop and
report; do not repair main code under this order.

1. **Environment and build.** Record the environment (see the results format). Build with
   DirtyChai as the JDK, the way the project is normally built. Record the exact commands.

2. **Module tests.** Run the complete test suites of `jgdms-der` and `jgdms-jeri`. Report
   totals and every failure, error and skip. State separately the results for the new test
   classes listed above, and whether any previously passing test now fails.

3. **Repository checks.** Run `check-serial-schema.sh`, `check-api-compat.sh` and
   `check-no-joss-invocation.sh` and report their output. Do not regenerate
   `serial-schema.golden`; report what the check says about `RelayEndpoint`.

4. **Spot-check that the tests are load-bearing**, with real JUnit. In a scratch copy or by
   a temporary edit that you revert, break each of these one at a time, run the relevant
   test class, and record which tests fail:
   - locator: the required-slot check for an unrecognised record; the JOSS-format refusal;
     the `MarshalledInstance` hash check; one reported offset shifted by one;
   - locator: make it load the leaf class by name (the tripwire tests must fail);
   - relay endpoint: treat a refusal status as acceptance; bypass the principal class
     allow-list; remove the handshake watchdog; drop `Integrity.YES` from the unfulfilled
     constraints; capture the call token in `next()` instead of `newRequest`.
   Confirm the tree is clean of these edits afterwards (`git status`, `git diff`).

5. **Security manager checks, never yet exercised.** Add tests (new files only) that run
   with a security manager installed in the project's normal way and show, for each of the
   following, that code without the permission is refused and code with it succeeds:
   - `RuntimePermission("setRelayCallToken")` in `RelayCallToken.callWith`;
   - `RuntimePermission("setRelayChannelFactory")` in `RelayChannels.setFactory`;
   - `ContextPermission("net.jini.io.context.ServerSubject.getServerSubject")` on the
     `ServerSubject` that `RelayEndpoint`'s request adds to the context.
   Put them where such tests live in this project (a module test with a forked JVM, or
   `qa/jtreg/net/jini/jeri/relay`). Apply the with-and-without rule.

6. **The real proxy carrier.** The locator was tested against a fixture shaped like
   `au.net.zeus.jgdms.der.object.DerProxySerializer`, never the real one, because the real
   carrier only appears when a `ProxyCodebaseSpi` provider is registered. Add a test, in
   whichever module or qa suite has the provider on its classpath, that marshals a
   downloadable proxy so that the real carrier is produced, runs `EndpointLocator` with
   default configuration, and asserts the endpoints found (both the bootstrap handler's and
   the one inside the nested `serviceProxy`). Report the top-level form you observed. If the
   locator refuses or misses something, report it; do not change the locator.

7. **Independent read.** Read the new main classes as a reviewer. Report defects and doubts
   with file and line; do not fix them. Look in particular at:
   - `EndpointLocator`: any path on which a class name from the data could reach a class
     loader; integer overflow or unchecked lengths in offset arithmetic; recursion not
     covered by the depth limits; ways a hostile record could make it report a location that
     is not the endpoint's own TLV; behaviour on a recognised endpoint class appearing only
     as a superclass in a chain;
   - `RelayOutboundRequest` and `RelayChannels`: the watchdog race between timeout and
     normal completion; channel leaks on every failure path; whether token bytes can reach a
     log, an exception message or a `toString`; whether the principal names from the relay
     reply are bounded and validated before use; thread-safety of the set-once factory;
   - `RelayCallToken`: whether untrusted code in the same JVM can read or bind a token by
     any route, including reflection permitted by the module's normal policy;
   - `RelayEndpoint`: `equals`, `hashCode` and trust equivalence; the `GetArg` constructor's
     validation; that it is deliberately not `Serializable`.

8. **One end-to-end call (if time allows).** A real `BasicInvocationHandler` (or
   `AtomicDerInvocationHandler`) call through `RelayEndpoint` to a stub relay that forwards
   the bytes to a real exported object over `SslEndpoint`. The stub relay belongs in test
   code only. Report whether the call returns, whether a required `Integrity.YES` is
   honoured, and what `authenticateServer` does (it is expected to reject `RelayEndpoint`
   as an untrusted endpoint class; that is a known open decision, so report, do not patch).

## Not in this order

The rewriter, the relay process, the hand-off integration, any change to existing main
source, and any change to DirtyChai.
