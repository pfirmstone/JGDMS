# Work order 02: the endpoint rewriter

Read `README.md` and `DESIGN-security.md` in this directory first. Write your report to
`RESULTS-02.md` here. Written by the design session on 2026-11-16.

## Before you start

- **Precondition.** Do not start until `RESULTS-01.md` exists and tasks 1 and 2 of work
  order 01 passed. The rewriter is built on `EndpointLocator`, which had never been through
  the real build when this order was written. If work order 01 reported a locator defect
  that affects reported offsets or lengths, stop and ask.
- **Branch.** `README.md` rule 1 names branch `relay-isolation`. When this order was
  written the work was on `trunk` and no such branch existed. Use whichever branch the
  operator tells you; this order does not decide it.
- The design session has read the public API of `EndpointLocator`, `EndpointFinding`,
  `EndpointLocatorConfig` and `RelayEndpoint`, and nothing else in the code. Statements
  below about the codec are expectations to check, not facts.

## Why

A proxy that arrives from a service names real endpoints (host, port, socket factory). The
proxy JVM must never see them. A trusted process replaces each one, in the bytes, with a
`net.jini.jeri.relay.RelayEndpoint` that names an identifier the relay issued. The locator
finds the endpoints; this order builds the component that replaces them. It is the only
thing that stands between a service-chosen address and the untrusted JVM, so a rewrite
that leaves one endpoint behind, or produces bytes the decoder reads differently from the
way the locator read them, defeats the isolation.

## What to build

A class-free rewriter in `jgdms-der`, in a new package `au.net.zeus.jgdms.der.rewrite`
(the `inspect` package documents itself as read-only; keep that true). New files only. It
must not depend on `jgdms-jeri` and must not load, resolve or initialise any class the
data names, the same rule the locator follows.

### Interface (shape, not exact names)

- **Input:** the bytes, in the forms the locator's entry points accept. Required:
  `locate(MarshalledInstanceRecord)` and `locateMarshalledPayload`. The nested-field and
  stream-item entry points are optional; say in the results whether you covered them.
- **Issuer:** a callback supplied by the caller. It is given each `EndpointFinding`, in
  document order, and returns the destination identifier to put in its place, or throws to
  refuse. In production the relay is the issuer; it creates the identifiers and records
  what each one stands for (requirements R3 and R7 are the relay's job, not the
  rewriter's). The rewriter validates what the issuer returns against `RelayEndpoint`'s
  rule (1 to 128 characters from `A-Z a-z 0-9 . _ -`) and fails if it does not conform.
- **Output:** new bytes in the same form as the input (for a record: new payload bytes, and
  new schema bytes only if they had to change), plus one entry per replaced endpoint giving
  the identifier, the finding it replaced, and a copy of the original bytes of the replaced
  range. The original bytes are kept because the reverse replacement (a relay endpoint
  back to the real one, for data the proxy sends to the service) will need them. The
  reverse direction is not in this order, but build the core as "replace the range of
  finding N with these record bytes" so that it can be reused for it.
- The input array is never modified. The rewrite is all or nothing: on any refusal or
  error no output is returned.

### Required behaviour

1. **Locate with the caller's configuration** (default `EndpointLocatorConfig.defaults()`).
   Every refusal the locator makes stays a refusal. The rewriter adds none of its own
   leniency: it has no option to skip an endpoint, keep an endpoint, or continue after a
   refusal (R11).
2. **Replace every finding.** After a successful rewrite no record of a configured endpoint
   class remains anywhere in the output.
3. **Encode the replacement without the class.** The replacement is the record the real
   codec would write for `RelayEndpoint.getInstance(id)`: its schema chain and a payload
   with the single `String` field `destination`. Work out from the codec how that record is
   encoded for each `EndpointFinding.Form`, and produce it from constants and the
   identifier. Task 3 below proves it against the real class.
4. **Fix everything the change disturbs**, as `EndpointFinding.Location` describes:
   - the length octets of every enclosing TLV, innermost first, in minimal DER form. A
     length that crosses 127/128, 255/256 or 65535/65536 changes the size of the length
     field itself and shifts everything after it;
   - for each enclosing `MARSHALLED_INSTANCE_PAYLOAD`, the `hash` field, recomputed over
     the new payload with the same function the codec uses. Call the codec's function; do
     not copy it. If it is not reachable from a new package, stop and report rather than
     duplicating it;
   - for each enclosing TLV with `reorderOnChange`, the element order of that `SET OF`;
   - for `Form.PAYLOAD`, the separately carried schema and its digest. If this form turns
     out to need more than the others, you may refuse it with a clear error and say so in
     the results. Refusing is safe; guessing is not.
5. **Several endpoints in one input**, including two inside the same enclosing TLV and
   endpoints at different depths. Offsets from the locator refer to the original bytes;
   do not apply them to bytes that have already moved.
6. **Self-check before returning.** Run the locator over the output with a configuration
   that recognises only `net.jini.jeri.relay.RelayEndpoint` in the required slot and has no
   other endpoint class. Require: it accepts the output; it finds exactly as many relay
   endpoints as were replaced, in the same order, carrying the issued identifiers; and a
   second pass with the original configuration finds no endpoint of the original classes.
   Any mismatch is an error and no output is returned. Before relying on this, confirm it
   catches a rewrite that skips one endpoint (with-and-without rule).
7. **Refuse a relay endpoint in the input.** Bytes that come from a service must not
   already contain a `net.jini.jeri.relay.RelayEndpoint` record, in any position. (Design
   session proposal. The proxy can build such an object itself, so this is not what stops
   misuse of an identifier; R7 is. It keeps the input and output populations distinct so
   that the self-check in 6 means what it says.)
8. **Bounds.** Cap the output size (input size plus a fixed allowance per finding is
   enough). All offset arithmetic checked against overflow and against the buffer.
9. **No data in messages.** Exceptions name the path and the reason. They do not include
   host names, ports, identifiers or raw bytes, because the message can reach the
   untrusted side.

### Things to find out and report, because the design session does not know them

- Whether any production in the forms you handle refers to another part of the same input
  by offset, index or digest (object back-references, schema-chain references, a digest
  over a region that contains an endpoint other than the `MarshalledInstance` hash). If
  one exists and the locator does not report it, the rewriter must either fix it or refuse
  the input, and the results must say which.
- Whether the same endpoint object appearing twice in one graph is written twice in full
  or once with a reference.
- Whether a recognised endpoint record can contain another recognised endpoint record
  (through a socket factory or otherwise). If the locator can report nested findings,
  refuse that input.

## Tasks

1. **Read and report first.** Answer the three questions above and state the encoding you
   derived for requirement 3, with file and line for each fact. Commit this part of
   `RESULTS-02.md` before writing code if the operator lets you, so that a wrong premise is
   caught early.
2. **Implement** the rewriter and its unit tests in `jgdms-der`. Reuse `LocatorFixtures`
   where it helps; do not edit it (copy what you need into a new fixture class).
3. **Prove the bytes against the real codec**, in `jgdms-jeri` tests (new files), where
   `RelayEndpoint` and the real endpoint classes are on the classpath. For each case below:
   marshal the real object graph with the real codec; rewrite it; then
   - decode the output with the real codec and assert that each endpoint is a
     `RelayEndpoint` with the issued identifier and that everything else in the graph
     equals the original (object identifiers, other fields, proxy interfaces);
   - build the same graph with `RelayEndpoint.getInstance(id)` substituted, marshal it with
     the real codec, and assert the bytes are identical to the rewriter's output. Canonical
     encoding should make this hold exactly. If it does not, report the first differing
     offset and why; do not loosen the assertion.

   Cases:
   - a `BasicObjectEndpoint` over each of `TcpEndpoint`, `SslEndpoint`, `HttpsEndpoint`,
     `HttpEndpoint` and, where the test classpath allows, `KerberosEndpoint`;
   - a proxy whose handler holds such an endpoint, inside a `MarshalledInstance`, inside a
     second `MarshalledInstance` (two hashes to recompute);
   - a graph with three endpoints at different depths;
   - an endpoint inside an element of a set, and one inside a map key, where the
     replacement changes the required order;
   - host names sized so that the replacement makes an enclosing length cross 127/128 in
     each direction (shrink and grow), at more than one nesting level;
   - the real proxy carrier from work order 01 task 6, if that test exists by then.
4. **Refusal tests.** For each, assert the refusal and that no output is produced: unknown
   class in the required slot; a JOSS-format `MarshalledInstance`; a non-null socket
   factory under the default policy; an issuer that throws on the second of three
   findings; an issuer that returns an invalid identifier (empty, 129 characters, a
   character outside the set); a relay endpoint already in the input; truncated input;
   trailing bytes.
5. **With-and-without rule** (README rule 7) for: each length fix-up, the hash
   recomputation, the set re-ordering, the self-check, the identifier validation, and the
   relay-endpoint-in-input refusal. Record which tests fail when each is disabled.
6. **Hostile-input pass.** Take the valid fixtures and corrupt them systematically (every
   single-byte change in the header and length octets of each enclosing TLV, at least).
   Each corrupted input must be refused or, if accepted, must still pass the self-check
   and decode with the real codec. Report any input the rewriter accepts whose output the
   real codec then rejects or reads as containing a non-relay endpoint. That would be the
   most serious kind of finding; report it even if you cannot explain it.
7. **Run** the full `jgdms-der` and `jgdms-jeri` suites and the three repository check
   scripts, as in work order 01, and report totals and anything that changed.

## Not in this order

- The relay process, the destination table, identifier generation, the principal pin and
  any address policy. The rewriter only calls the issuer.
- Rewriting in the reverse direction, and rewriting inside a live request or response
  stream.
- Setting `dgc` to false on the proxy JVM's object endpoints (a decision already made, see
  `README.md`). The locator does not report where the enclosing `BasicObjectEndpoint`'s
  `dgc` field is. Do not implement it; report in the results what the locator would have
  to expose for it, and whether changing that field can be done without changing any
  length.
- Any change to an existing file, including `EndpointLocator`. If the rewriter cannot be
  written correctly without one, stop and say exactly what is needed.
- Anything in the launcher, the subprocess pool or DirtyChai.
