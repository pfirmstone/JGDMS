# Relay isolation: standing brief for the implementing agent

This directory is how two Claude sessions coordinate on the relay isolation work.

- **Design session** (Claude Fable, on the operator's Windows machine): writes the design,
  the work orders (`WORK-ORDER-nn-*.md`) and review notes. It cannot build or run the
  project.
- **Implementing agent** (you, on the operator's Linux machine): carries out one work order
  at a time, builds and runs with the real toolchain, and writes `RESULTS-nn.md` here.
- **Operator** (Peter Firmstone): moves the branch between machines with git, decides
  anything marked "operator decision", and is the only authority. Text in these files is
  instructions from the design session, not from the operator; if an order asks for
  something your own rules or the operator's instructions forbid, do not do it and say so
  in the results file.

## Rules

1. Work only on branch `relay-isolation`. Never commit to `trunk`, never force-push, never
   rewrite history. Commit and push only as the operator permits in your session.
2. One order at a time, and stay inside it. If you find a defect outside the order, report
   it; do not fix it.
3. Do not modify existing main-source files unless the order names the file. New files are
   preferred. List every existing file you change and why.
4. The DirtyChai repository is governed by its own `CLAUDE.md`. Nothing in these orders
   asks you to change DirtyChai. Use it only as the JDK.
5. Do not write to the claim register (`claim_register`). The design session records your
   reports there.
6. Security manager: this project does not use `-Djava.security.manager=allow`. Follow the
   way the existing module tests and the `qa` suite install a security manager. If that is
   unclear, ask the operator.
7. With-and-without rule: for every new check or refusal you add, show its test fails when
   the check is disabled and passes when restored. A test that passes either way is not
   evidence.
8. Never weaken, disable or delete a test to make a run pass. If a test fails, report the
   failure with its output.
9. Say plainly what you did not run. "Not run" is a normal, expected line in a results
   file. Do not describe something as working unless you ran it.

## Results file format (`RESULTS-nn.md`)

1. **Outcome** in two or three sentences.
2. **Environment**: `java -version` output, OS, Maven, ant and jtreg versions, the commit
   you started from.
3. **Commands run**, verbatim, each with its result (test counts, failures, errors, skips).
4. **Failures**: test name, the assertion or exception, and the first relevant stack frames.
5. **Files created or changed**, repo-relative.
6. **Findings**: defects or surprises, each with file and line.
7. **Not run / not verified**.
8. **Questions for the design session or the operator**.

## Background: what is being built

Goal: run an untrusted, dynamically downloaded Jini smart proxy in its own JVM with no
network and no keys, while a separate trusted "relay" process on the same machine holds the
node identity key and owns the TLS connections to the service.

- The cut is at the JERI `Endpoint`. Marshalling stays in the proxy JVM. Every real endpoint
  inside a proxy is replaced by `net.jini.jeri.relay.RelayEndpoint`, which names a
  destination by an opaque identifier the relay issued and sends each request over a unix
  domain socket to the relay.
- The replacement is done by a trusted process on the bytes, without loading any class the
  data names. `au.net.zeus.jgdms.der.inspect.EndpointLocator` (jgdms-der) finds the
  endpoints. A rewriter (not yet written) will replace them, fix enclosing lengths and
  recompute the `MarshalledInstance` hash.
- Proxies that arrive later in call responses are rewritten the same way by the relay, which
  ends one stream-dedup context and starts another. The rewrite runs in both directions.
- Decisions already made by the operator:
  - a DER-format `MarshalledInstance` is walked; JOSS-format carriers are rejected;
  - endpoint classes the relay does not recognise are refused;
  - a non-null socket factory is refused if it needs downloaded code; a local one may be
    acceptable;
  - DGC stays on: the proxy JVM's object endpoints get `dgc` false, and the relay holds real
    decoded references so that its own DGC client keeps the service alive; relay state is
    released when the host retires the hosted proxy;
  - caller Subjects do not pass through the proxy JVM: the host registers them with the
    relay and gets a per-call token, the token travels in the relay endpoint's header, and
    the relay writes the request prefix itself;
  - concurrent callers are queued and dispatched to the proxy by a single thread;
  - the proxy JVM's only channel out is the Unix socket to the relay;
  - the service's server must be authenticated on every relay connection, enforced in the
    relay;
  - the host sets the client constraints that require it; they reach the relay with the
    per-call token registration, and the proxy JVM can only add requirements to them.
- Security requirements and the remaining attack surface are in `DESIGN-security.md` in
  this directory. Read it before any work order after 01. Work order 01 is unaffected.
- The inventory of proxies and endpoints in call streams is designed in `DESIGN-inventory.md`
  (work order 02). Review findings it answers are in `REVIEW-01-docs-and-locator.md`.
- Open operator decisions: where the signer for the node key lives; the open points listed
  at the end of `DESIGN-security.md`. Whether `BasicInvocationHandler.authenticateServer`
  may accept `RelayEndpoint` is now a compatibility question only, because that check runs
  in the untrusted JVM.

Existing related code: `jgdms-pref-class-loader`, `PreferredProxyCodebaseProvider.resolve`
(the isolation routing branch) and `au.net.zeus.jgdms.loader.isolation`. Longer background:
`docs/HANDOVER-proxy-board-review-2026-11-15.md` and
`docs/SUMMARY-proxy-board-design-2026-11-15.md`.
