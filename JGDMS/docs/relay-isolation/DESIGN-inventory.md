# Relay isolation: proxy and endpoint inventory

Written 2026-11-16 by a Claude Opus 5.5 session on the operator's Linux machine, at the
operator's request. It designs the next phase: inspecting DER bytes for every proxy and
endpoint they carry, not only the endpoints in the initial hand-off. Nothing here is built.
Operator rulings are cited by register id; everything else is a proposal. The issues it
answers are in `REVIEW-01-docs-and-locator.md` (L1 to L5, S1, S2).

## Goal

For any DER bytes that cross into or out of a proxy JVM, a trusted process can list, without
loading a class the data names:

- every endpoint, with its location (today's `EndpointFinding`);
- every proxy, with its handler and the endpoints inside it;
- every codebase annotation;
- every `byte[]` it could not look inside.

The output is an inventory. Deciding what to refuse is policy, applied to the inventory, so a
change of ruling does not need a change to the walker. Rewriting is a later phase.

## Where the bytes come from

| # | Bytes | Who inspects | Format | Today |
|---|---|---|---|---|
| I1 | Initial hand-off: the proxy carrier | trusted host or relay | `MarshalledInstance` record, or a record-level `[1]` item | supported |
| I2 | Service reply to a call made by the proxy | relay (RL-JGDMS-0083) | DER object stream | refused (L1) |
| I3 | Arguments of a call made by the proxy | relay | DER object stream | refused (L1) |
| I4 | Arguments the host passes to the hosted proxy | host (proposal, S2) | DER object stream | refused (L1) |
| I5 | Values the hosted proxy returns to the host | host (R13) | DER object stream | refused (L1) |

I3 matters even though the proxy wrote it: the proxy can only pass ids it was given, but the
relay should confirm that before the bytes leave the board (R7, R13 in reverse).

A JOSS-format stream on any of these paths is refused (RL-JGDMS-0088 for carriers;
proposal for whole streams): the relay requires `MarshallingFormat` DER for relayed calls,
through the host's registration (R2).

## What the stream format requires

From `DerObjectStreamCodec` (`jgdms-der/.../der/stream/DerObjectStreamCodec.java:63-122`):

- Every stream starts with the `[15]` version octet `8F 01 01`; any other start is refused.
- Items are `[0]` null, `[1]` `@AtomicSerial` record, scalar items, `[7]` enum, `[8]` bare
  proxy, `[9]` top-level array (array wire type then element SEQUENCE), `[16]` top-level
  collection or map (collection wire type then the collection TLV).
- Schema-chain dedup is mandatory at every chain site (`[1]` items, nested records, `[9]`
  `@AtomicSerial` elements): the first occurrence of a chain is `fullChain [0]`, later ones a
  32-byte `chainRef [1]`. `[8]` proxy interiors and `[16]` interiors are excluded from dedup
  and are written in record-level form.
- A proxy that needs a codebase is not a bare `[8]`: a `ProxySerializer` substitutes it and it
  rides `[1]` (`DerProxySerializer` carrier).

Consequences:

1. The walker must hold the per-stream chain table, expand `chainRef`s, and refuse a
   `chainRef` to an id not yet seen. It should reuse the codec's own dedup and limits
   (`StreamSchemaDedup`, `DerStreamPolicy`, `StreamPolicyMeter`) if they can run without
   resolving classes, rather than reimplement them (board guidance G8). WO-02 finds out.
2. Offsets in a deduplicated stream are not enough for a rewrite. Changing an endpoint
   changes its chain, which changes which occurrence is first. So a stream is rewritten by
   decoding it class-free, rewriting, and re-encoding with a fresh dedup context, as the
   README already says ("ends one stream-dedup context and starts another"). The splice
   rewrite with `Location` and `Enclosing` stays for I1.
3. Inside an `[8]` or `[16]` region the existing record-level walker applies unchanged.

## What is reported

Proposal: one result type, `DerInventory`, holding four lists in document order.

- **Endpoints**: today's `EndpointFinding`, plus the index of the proxy finding it belongs to,
  if any.
- **Proxies** (`ProxyFinding`): path; form (`[8]` field form, `[8]` stream item, or a `[1]`
  carrier whose class chain is a configured proxy-serializer class); the interface names as
  written; the handler's class chain; the endpoints inside it; location. The interface names
  are the sender's claim. The receiver drops names it cannot resolve
  (`DerObjectStreamCodec.java:118-122`), so names describe a proxy and never decide trust.
  Whether a proxy is a bootstrap proxy is decided by structure: a bare `[8]` whose handler is
  a configured local handler class and whose endpoints all sit in required slots.
- **Codebases** (`CodebaseFinding`): the `codebaseAnnotation` of each `MarshalledInstance`,
  with path and location (L5).
- **Opaque bytes** (`OpaqueFinding`): every non-null, non-empty `byte[]` field or `[5]` item
  outside a `MarshalledInstance` payload, with path, length and location (L4). Report-only
  until the operator rules; the config can refuse them.

`EndpointLocator`'s existing methods keep their behaviour and return types, so WO-01's tests
stay valid.

## Recognising endpoints (RL-JGDMS-0085)

A class-free walker cannot tell that an arbitrary record is an `Endpoint`, because a field's
declared type does not travel (`EndpointLocatorConfig` javadoc). The design therefore enforces
RL-JGDMS-0085 by two means and states both:

1. **Required slots.** Every slot the platform uses to hold an endpoint is a required slot.
   The default config grows from `BasicObjectEndpoint#ep` to every handler and
   object-endpoint layout the platform ships. The list comes from the WO-02 survey of the real
   classes, not from memory.
2. **No network** (RL-JGDMS-0096). An endpoint in an unrecognised slot cannot connect from the
   proxy JVM, so missing one breaks the proxy rather than opening a connection. This holds
   only if Unix socket paths are closed too (R15) and the relay refuses board-local
   destinations (S1).

## Policy applied to the inventory

Fail-closed, applied by the caller after the walk, configured in `EndpointLocatorConfig`:

| Finding | Default | Source |
|---|---|---|
| Unrecognised record in a required endpoint slot | refuse | RL-JGDMS-0085 |
| Socket factory needing downloaded code | refuse | RL-JGDMS-0089 |
| JOSS-format carrier or stream | refuse | RL-JGDMS-0088 |
| Stream item kind or tag not understood | refuse | board guidance G6 |
| `UdsEndpoint` in bytes from a remote service | refuse | proposal S1, needs ruling |
| Loopback, link-local, unspecified or board-own address | refuse | proposal S1, needs ruling |
| Opaque `byte[]` | report | L4, needs ruling |
| Codebase annotation | report | open point 5 |

## Limits

The same ceilings as the decoder, threaded through every recursion and not only checked at
entry (board guidance G10): `ObjectCodec.MAX_NESTING`, `ObjectCodec.MAX_COLLECTION`,
`ProxyWireSupport.MAX_PROXY_INTERFACES`, `DerInputLimits.maxMarshalledInstanceNesting`, and
the stream totals the codec's `StreamPolicyMeter` enforces. Each limit gets an adversarial
test that builds the oversized or over-deep input and runs it (G13), plus empty and
one-element cases (G11): an empty stream after `[15]`, an empty `[9]`, an empty `[16]`.

## Open questions for the operator

1. S1: refuse `UdsEndpoint` and board-local addresses in bytes from a remote service?
2. L4: does RL-JGDMS-0088 replace RL-JGDMS-0084, or narrow only its carrier clause? That
   decides whether opaque `byte[]` is refused or only reported.
3. S2: does the host inventory and rewrite I4 arguments, and where do the service's calls back
   to a host-exported listener terminate?
4. Whole JOSS-format streams on a relayed call: refuse (proposal), as with JOSS carriers?
