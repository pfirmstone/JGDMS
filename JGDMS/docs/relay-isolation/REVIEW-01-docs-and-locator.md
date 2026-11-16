# Review 01: relay-isolation documents and the endpoint locator

Written 2026-11-16 by a Claude Opus 5.5 session on the operator's Linux machine, at the
operator's request, against `trunk` at f057400e0. It covers `README.md`,
`WORK-ORDER-01-verify.md`, `DESIGN-security.md` and the parts of
`jgdms-der/.../der/inspect/EndpointLocator.java` that matter for the next phase (finding
proxies and endpoints in call streams). It is not the independent code read of WO-01 task 7.

How to read the labels: **CONFIRMED** means re-checked against the source or the register in
this session. **PLAUSIBLE** means inferred from the design text and not run. Nothing was
built or run for this review. Each issue is followed by a recommendation where there is one.

## What was checked and holds

- Every file WO-01 lists exists. `@Test` counts match the claims: `EndpointLocatorTest` 23,
  `EndpointLocatorJeriTest` 4, the eight relay test classes 55 (3 + 11 + 6 + 13 + 5 + 9 + 3 +
  5). jgdms-der uses JUnit 5 and jgdms-jeri JUnit 4, as stated.
- The three permissions in WO-01 task 5 exist: `RelayCallToken.java:45`,
  `RelayChannels.java:62`, `RelayOutboundRequest.java:433`.
- Every cited class exists: `ContextPermission` (jgdms-platform), `DerProxySerializer`,
  `ProxyCodebaseSpi`, `PreferredProxyCodebaseProvider`, the `loader.isolation` package.
  `UnsupportedSubProcessLauncher` is a nested class, `SubProcessLauncher.UnsupportedSubProcessLauncher`.
- Every RL-JGDMS id cited in `DESIGN-security.md` is in
  `claim_register/RULING_INDEX.md` and IN-FORCE, and each says what the design says it says.
- The locator's `MarshalledInstance` hash check (`EndpointLocator.java:827-834`) is the same
  computation as `MarshalledInstance.computeHash` (`jgdms-platform/.../MarshalledInstance.java:207-213`),
  including the null-payload value 13. The hash is a consistency check, not an integrity
  mechanism (it is a 31-polynomial); RL-JGDMS-0086 lets the rewriter recompute it. Sound as
  designed; nothing should be built on top of it.

## Issues: process and documents

**D1. The branch rule does not match where the work is.** CONFIRMED. README rule 1 and
RL-JGDMS-0094 say orders and work pass through branch `relay-isolation`; no such branch exists
locally or on the remote. 3b4d2c67e is on `trunk` and merged (f057400e0). WO-01 line 13 says
"all new files, on this branch".
*Recommendation:* the operator decides one of: create `relay-isolation` from f057400e0 and
keep the rule, or change rule 1 and WO-01 to say `trunk`.

**D2. A commit message makes a false claim.** CONFIRMED. 3b4d2c67e says "No existing file is
modified"; it changes `docs/HANDOVER-proxy-board-review-2026-11-15.md` (35 lines). The change
is a document, but the claim is still false.
*Recommendation:* record the correction in the next results file. Do not rewrite history
(README rule 1).

**D3. WO-01 task 3 assumes `RelayEndpoint` has no serial-schema baseline.** CONFIRMED.
`serial-schema.golden` already has `ATOMIC net.jini.jeri.relay.RelayEndpoint - | destination:java.lang.String`,
added by 0507975f8. The check will pass, and the baseline was recorded from code that has
not been built under Maven.
*Recommendation:* change task 3 to "confirm the golden line for `RelayEndpoint` matches the
schema the built class produces, and report the commit that recorded it".

**D4. README rule 6 sends the implementer to the operator for something in the repository.**
CONFIRMED. `jgdms-jeri/pom.xml:246` already shows how the module's tests install a security
manager (`-Djava.security.manager=default`).
*Recommendation:* cite that line in rule 6.

**D5. Two reasons are given for having no swap.** CONFIRMED. R16e says the board has no
writable storage; R16f and RL-JGDMS-0107 say swap was rejected as a timing source.
*Recommendation:* cite RL-JGDMS-0107 as the reason in both places; keep "no writable storage"
only if it is also a ruling.

**D6. R16f mixes an operator ruling with an unchecked figure.** CONFIRMED. The 16 GB and core
details come from "an unchecked subagent report" under ruling ids that say only "i.MX 95",
"ECC", "no swap", "no VM".
*Recommendation:* move the hardware figures to a separate line marked unverified.

**D7. The R16 sub-items are out of order** (R16, c, d, a, b, e, f, g). CONFIRMED.
*Recommendation:* reorder by letter; renumbering would break cross-references in other files.

**D8. The README's "Decisions already made" restates `DESIGN-security.md`.** CONFIRMED. Two
copies of the same rulings will drift (board guidance G8).
*Recommendation:* replace the README list with the ruling ids and a pointer to the design.

## Issues: security design (`DESIGN-security.md`)

**S1. The relay can be told to connect to the board itself.** PLAUSIBLE, HIGH if confirmed.
The default locator config recognises `UdsEndpoint` (`EndpointLocatorConfig.java`, defaults),
and R11 treats an address policy as optional. A service can supply a `UdsEndpoint` naming a
socket path on the board (the relay's own socket, a signer, another subprocess) or a TCP
endpoint on loopback or the board's own addresses. The relay holds real endpoints and opens
the connection (README, DGC bullet), so it acts as a deputy for the service. R11's residue
statement covers only "a TCP connection ... to an address the service named" and omits both
cases. R1 does not obviously apply to a Unix socket, which authenticates by peer credentials,
not by TLS server principals.
*Recommendation:* make it a requirement, not optional hardening: the rewriter refuses
`UdsEndpoint` in any proxy that came from a remote service, and refuses loopback, link-local,
unspecified and board-own addresses for every host-and-port endpoint. Needs an operator ruling.

**S2. Objects the host passes in as arguments are not covered.** PLAUSIBLE. R13 covers the
return path. When the host passes one of its own exported objects (a `RemoteEventListener`,
for example) as an argument, its stub carries a real endpoint into the proxy JVM, contrary to
"the proxy never holds a host or port". The README says the rewrite "runs in both
directions", but no requirement names this direction or says who performs it.
*Recommendation:* add a requirement that the host (trusted) inventories and rewrites
arguments before they reach the proxy JVM, and that the service's later calls back to such a
listener go through the relay. Settle where inbound calls terminate.

**S3. Destination ids and tokens are bound to a subprocess, not to a proxy.** PLAUSIBLE. R7
binds ids, and R6 binds tokens, to a subprocess. R16a reads the queue as per subprocess, so
one JVM may host several proxies. Inside a shared JVM, proxy A can build a `RelayEndpoint`
with proxy B's id and spend the caller's token at B's service during A's call. R3 then
authenticates B, so the call goes through.
*Recommendation:* either one service per subprocess (state it in R16a and open point 4), or
bind each token to the destination ids of the proxy being invoked and have the relay refuse
others.

**S4. R2 states the outcome but not the mechanism.** CONFIRMED (text). "May only add
requirements" needs the relay to decode the proxy's constraints blob (R9) and form the
conjunction with the registered constraints; the text does not say so.
*Recommendation:* add one sentence: the relay decodes the blob with local classes only (R9),
discards preferences, and enforces registered requirements AND blob requirements.

**S5. R3 does not say how third-party references are handled.** CONFIRMED (text); no defect.
Under `SafeServiceRegistrar` a third service's proxy arrives as a bootstrap proxy. If it
passes through the proxy JVM, its rewritten ids are pinned to the supplier's principals and
fail closed if used there; the intended route is back to the host under R13, which
authenticates it normally.
*Recommendation:* say this in R3 so the next reader does not reopen it.

## Issues: endpoint locator, for the next phase

**L1. The locator cannot read a call stream.** CONFIRMED. JERI arguments and replies are DER
object streams: a `[15]` version octet, then items with mandatory schema-chain dedup
(`DerObjectStreamCodec.java:98-110`). `locateStreamItem` handles only record-level items and
refuses dedup productions (`EndpointLocator.java:226-231`); `[9]` arrays and `[16]`
collections are refused (`EndpointLocator.java:886-887`). `SafeServiceRegistrar.lookUp`
returns `Object[]`, which is a top-level array item. So no reply the relay must inspect under
RL-JGDMS-0083 can be read today.
*Recommendation:* the next phase adds a stream walker. See `DESIGN-inventory.md` and
`WORK-ORDER-02-inventory.md`.

**L2. Proxies are walked but not reported.** CONFIRMED. `walkFieldProxy` and
`walkStreamProxy` read the interface names and discard them (`EndpointLocator.java:478`,
`935`). Nothing tells the caller a proxy was there, what its handler is, or which endpoints
belong to it. R13, S5 and the codebase question need that.
*Recommendation:* report a proxy finding. Note that `[8]` interface names are written by the
sender and resolved tolerantly by the receiver (`DerObjectStreamCodec.java:118-122`), so they
can describe a proxy but must never be the basis of a trust decision.

**L3. RL-JGDMS-0085 is enforced only in one slot.** CONFIRMED. An unrecognised endpoint class
is refused only in a configured required slot; the only default is `BasicObjectEndpoint#ep`
(`EndpointLocator.java:464`). Anywhere else, an endpoint record is recognised only if its leaf
class name is configured (`:526-527`). Since the declared type does not travel, a class-free
walker cannot tell that an arbitrary record is an endpoint. A handler holding an
`ObjectEndpoint` other than `BasicObjectEndpoint` escapes the check. Given RL-JGDMS-0096 (no
network) a missed endpoint is a broken proxy rather than a connection, except through a Unix
socket path (R15, S1).
*Recommendation:* record in the design that RL-JGDMS-0085 is enforced by required slots plus
the empty network namespace, and add a required slot for every handler and object-endpoint
layout the platform ships. Their field names must come from the survey in WO-02, not from
memory.

**L4. Opaque bytes: the rulings and the locator may disagree.** CONFIRMED (code); the
disagreement depends on a reading. RL-JGDMS-0084 rejects "opaque bytes that could hide an
endpoint"; RL-JGDMS-0088 "narrows" it to rejecting JOSS carriers. The locator descends only
into classes named exactly `net.jini.io.MarshalledInstance` and treats every other `byte[]`
as opaque (`EndpointLocator.java:84-85`). A smart proxy can carry its own DER payload in any
`byte[]` field and decode it in the proxy JVM.
*Recommendation:* ask the operator whether RL-JGDMS-0088 replaces RL-JGDMS-0084 or narrows
only its carrier clause. Meanwhile, report non-empty `byte[]` fields outside
`MarshalledInstance` as findings, so a policy can refuse them without another change to the
walker.

**L5. Codebase annotations are not inspected.** CONFIRMED. `walkMarshalledInstance` reads
`payloadBytes`, `schemaBytes`, `payloadFormat` and `hash` (`EndpointLocator.java:794`), not
`codebaseAnnotation`. A codebase URL is a network address chosen by the service, and it bears
on open point 5 (who fetches the codebase).
*Recommendation:* report it as a finding.

**L6. Two entry points do not copy their input.** CONFIRMED. `locateMarshalledPayload` clones
its buffer (`:180`); `locateNestedField` and `locateStreamItem` use the caller's array and
document "must not be modified during the call" (`:206`, `:233`). Low: the relay owns its
buffers. The returned offsets go stale if the array changes between locate and rewrite.
*Recommendation:* none needed now; the rewriter should locate and rewrite on one private copy.

## Not checked

- No build or test run; WO-01 has no results yet.
- The code read was limited to the walker paths named above.
- S1 to S3 are design-level and have not been tried against code that does not exist yet.
