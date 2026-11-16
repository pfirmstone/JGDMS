# Work order 02: inventory of proxies and endpoints in DER bytes

Read `README.md`, `DESIGN-inventory.md` and `REVIEW-01-docs-and-locator.md` in this directory
first. Write your report to `RESULTS-02.md` here, in the README's results format.

## Precondition

Start only when `RESULTS-01.md` shows WO-01 tasks 1 and 2 passed (the module builds and the
locator's tests pass under Maven). This order extends that code. If they did not pass, stop.

## Why

The locator finds endpoints in the initial hand-off only. The relay must also inspect call
streams (RL-JGDMS-0083), which the locator refuses (review L1). It reports no proxies (L2),
no codebase annotations (L5) and no opaque bytes (L4). The design is `DESIGN-inventory.md`.

## Files you may change

New files in `jgdms-der/src/main/java/au/net/zeus/jgdms/der/inspect/` and its test directory.
Existing files you may change: `EndpointLocator.java`, `EndpointLocatorConfig.java`,
`EndpointFinding.java`, and only to add. Existing public methods keep their signatures and
behaviour; every WO-01 test must still pass unchanged. Nothing outside `der/inspect` is
changed by this order; if a codec class needs a change to be reusable, report it.

## Tasks

Do them in order. Task 1 changes no main source.

1. **Survey the real bytes.** Using the real codec and real platform classes, in a test with
   the providers on its classpath, produce and save as test resources (hex plus a decoded
   outline: tags, schema chains, field names, dedup productions):
   - a bootstrap proxy as returned by a `SafeServiceRegistrar.lookUp` reply stream, with at
     least two matching services, so the reply is an array;
   - a smart proxy through the real `DerProxySerializer` carrier;
   - a call reply stream holding a smart proxy and a bootstrap proxy, so that dedup emits
     both `fullChain` and `chainRef`;
   - a call whose argument is a host-exported `RemoteEventListener` stub;
   - a `MarshalledInstance` with a non-null `codebaseAnnotation`.
   Report, from the bytes and the source, the class and field names of every slot that holds
   an endpoint or an object endpoint, for every invocation handler and object-endpoint class
   in main source (at least `BasicInvocationHandler`, `AtomicDerInvocationHandler`,
   `BasicObjectEndpoint`). Report which of `StreamSchemaDedup`, `DerStreamPolicy` and
   `StreamPolicyMeter` can run without resolving a class, with file and line for any that
   cannot.

2. **Report proxies, codebases and opaque bytes** in the existing record walker:
   `ProxyFinding`, `CodebaseFinding`, `OpaqueFinding` and a `DerInventory` result, as
   described in the design, returned by new methods beside the existing ones. Link each
   endpoint to its enclosing proxy.

3. **Required slots.** Add to the default config a required slot for every endpoint-holding
   field that task 1 found. List each one in the results file with its source.

4. **Stream walker.** A new public method that takes a complete DER object stream and returns
   a `DerInventory`: the `[15]` version check, the dedup table, `[0]`, `[1]`, scalars, `[7]`,
   `[8]`, `[9]` and `[16]`. Reuse the codec's dedup and limits where task 1 found them
   class-free; otherwise implement the minimum and say why. Refuse a `chainRef` to an unseen
   id, a stream without `[15]`, an unknown item tag, and trailing bytes. Report locations as
   paths, not offsets: per the design, a stream is rewritten by re-encoding, not by splicing.

5. **Policy hooks.** Config switches for the policy table in the design, with the defaults it
   gives. The two rows that need an operator ruling (`UdsEndpoint` from a remote service;
   board-local addresses) are implemented as switches defaulting to **report**, and the
   results file says the default awaits a ruling.

6. **Tests.** Every refusal and every finding kind, against the task 1 resources, under the
   README's with-and-without rule. Adversarial cases built and run, not reasoned about:
   - nesting one past `MAX_NESTING` through each recursive path: nested record, `Any`,
     collection in collection, `[16]` in `[1]`, `MarshalledInstance` in `MarshalledInstance`;
   - a `[9]` and a `[16]` with `MAX_COLLECTION + 1` elements;
   - `chainRef` before any `fullChain`; a `chainRef` to a chain of a different class;
   - an `[8]` naming `MAX_PROXY_INTERFACES + 1` interfaces;
   - the degenerate inputs: a stream holding only `[15]`, an empty `[9]`, an empty `[16]`,
     a one-element `[9]`, a `MarshalledInstance` with a null payload;
   - the tripwire from WO-01: no class named by the data is loaded (use the same technique as
     `EndpointLocatorTest`).

7. **Full module runs.** `mvn -pl jgdms-der test` and `mvn -pl jgdms-jeri test`, totals and
   every failure, compared with RESULTS-01.

## Not in this order

The rewriter, the relay, re-encoding a stream, any change outside `der/inspect`, choosing a
default for the rulings still open, and any change to DirtyChai.

## Questions to answer in the results file

- Does any real stream carry an endpoint in a slot that task 3 did not make required?
- Did any task 1 sample fail to walk? Report it with its bytes; do not adjust the walker to
  pass a sample without saying so.
