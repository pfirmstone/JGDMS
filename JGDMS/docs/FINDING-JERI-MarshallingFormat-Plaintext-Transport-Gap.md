# Board Finding — `MarshallingFormat` is unsatisfiable over EVERY JERI transport (incomplete STD-008 rollout)

**Status:** **RESOLVED 2026-07-27 — fix implemented and merged to trunk (merge `a96e677d7`).** All three
questions ratified by Peter; Q3 implemented. `MarshallingFormat` is now DEFERRED (never claimed) by all four
JERI transports — `tcp`/`http`/`uds` via `supportedClasses` `PARTIAL_SUPPORT`, `ssl` via a `ConnectionContext`
`MARSHALLING` verdict carrying the actual constraint — with regression coverage (`MarshallingFormatDeferralTest`:
per-transport positive DER round-trip + JOSS-prevention, 8/8; full jgdms-jeri suite 69 green). Originally
submitted to the board 2026-07-26; scope corrected 2026-07-27 (see §0). Surfaced by the Outrigger CEL-filter
demo7 (§8), the first end-to-end exercise of a **DER-only proxy making real invocations over a JERI endpoint.**
Not a filter defect — a pre-existing transport-layer gap, now closed.

## 0. Correction to the first draft

The first version of this finding claimed the gap was *plaintext-only* and that "SSL/HTTPS tolerate it (qa `der`
config green)." **That was an unverified assumption and it is wrong.** Empirical evidence from demo7 (below):
the SSL transport rejects `MarshallingFormat` exactly as the plaintext transports do — its own
`ConnectionContext.supported()` had no case for it and returned `NOT_SUPPORTED`. **The gap is in all four JERI
transports** (`tcp`, `http`, `uds`, and `ssl`/`https`). The corrected mechanism is in §1–§2.

## 1. The finding

`net.jini.core.constraint.MarshallingFormat` is a **DER-era invocation constraint** (STD-008). Every DER proxy
**requires** `MarshallingFormat.ATOMIC_DER` — `AtomicDerInvocationHandler.marshallingFormat()` pins it
(STD-008 sec.18.3).

STD-008 wired the **invocation layer** to handle that requirement on both ends:
- **Client:** `BasicInvocationHandler` (line ~975) iterates the request's *unfulfilled* constraints and, on a
  `MarshallingFormat`, calls `requireMarshallingFormat(...)` to satisfy it against the proxy's configured codec.
- **Server:** `BasicInvocationDispatcher.verifyAndStripMarshallingFormat(...)` verifies and strips it at dispatch.

**But STD-008 never updated the transport layer.** Each transport distills constraints through a support table
that classifies every constraint as NO_SUPPORT / FULL_SUPPORT / PARTIAL_SUPPORT. **None of the four classify
`MarshallingFormat`:**

| Transport | Distiller | Classifies `MarshallingFormat` on HEAD? |
|---|---|---|
| tcp   | `net/jini/jeri/tcp/Constraints.java` (`supportedClasses`)  | **No** |
| http  | `net/jini/jeri/http/Constraints.java` (`supportedClasses`) | **No** |
| uds   | `net/jini/jeri/uds/Constraints.java` (`supportedClasses`)  | **No** |
| ssl / https | `net/jini/jeri/ssl/ConnectionContext.java` (`supported()`) | **No** (returns `NOT_SUPPORTED` for it) |

An unclassified **required** constraint is NO_SUPPORT, so `distill` / `endpoint.newRequest(...)` throws
**`UnsupportedConstraintException` before the invocation layer ever runs** — line ~975 of
`BasicInvocationHandler`, which is *waiting* to satisfy the deferred `MarshallingFormat`, is never reached. The
invocation-layer half of STD-008 is dead code for any real DER invocation until the transport defers the
constraint.

**This is a latent regression / incomplete rollout, not a design flaw in the fix.** It is uncovered: no existing
test drives a real DER-proxy invocation over a JERI endpoint (demo7 is the first), so the suite stayed green.
**Board action item:** confirm whether any qa `der` integration test actually exercises a live DER-proxy
invocation — if one does and is green, reconcile how; if none does, this path has zero regression coverage.

## 2. Empirical evidence (demo7)

- Over **tcp**, no fix: `UnsupportedConstraintException: Constraints not supported: ... {Integrity.YES,
  MarshallingFormat[JGDMS-STD-006/ATOMIC-DER]}` at first filtered call.
- Over **ssl**, no fix: **identical** failure — proving ssl is not special.
- With the defer-classification added to the transport (tcp *or* ssl), the DER filtered calls (§8.2/§8.4)
  succeed: the constraint lands in the unfulfilled set and `BasicInvocationHandler` satisfies it via
  `requireMarshallingFormat`, exactly as STD-008 intended.

## 3. The fix (defer to the invocation layer, per transport)

For `tcp`/`http`/`uds`, add to the `supportedClasses` static block (import `MarshallingFormat`):
```java
// STD-008 sec.18.3: MarshallingFormat is an invocation-layer constraint the transport DEFERS
// (satisfied by BasicInvocationHandler.requireMarshallingFormat / BasicInvocationDispatcher).
// PARTIAL_SUPPORT puts it in the unfulfilled-requirements set instead of throwing at distill.
supportedClasses.put(MarshallingFormat.class, Boolean.TRUE); // PARTIAL_SUPPORT
```
For `ssl`/`https`, `ConnectionContext.supported()` gains a `MarshallingFormat` case returning a new `MARSHALLING`
"defer to higher layer" verdict (never `OK`); the actual constraint(s) are collected and carried verbatim into
the client's unfulfilled requirements (`CallContext`/`SslEndpointImpl`), because `MarshallingFormat` is not a
singleton and its format must reach the invocation-layer check. A `MarshallingFormat` inside a
`ConstraintAlternatives` is fail-closed to `NOT_SUPPORTED` (a choice cannot be deferred through the single-format
check). **As implemented (merge `a96e677d7`):** `tcp`/`http`/`uds` use the one-line `PARTIAL_SUPPORT` entry above;
`ssl` uses the `ConnectionContext` deferral. No transport returns a "supported/OK" verdict for `MarshallingFormat`
— that would skip the invocation-layer check and let an Atomic-JOSS codec bypass a required `ATOMIC_DER`.
Regression: `MarshallingFormatDeferralTest` (8/8); full `jgdms-jeri` suite 69 green.

## 4. Questions carried to the board

1. **[RATIFIED — Peter 2026-07-26] Is plaintext-transport DER a supported deployment?** **Ruling: supported for
   TESTING PURPOSES ONLY.** Therefore the defer-fix is the correct direction (it is what makes the test-only
   plaintext path, `uds` local-trust, and demos work); "production DER over TLS" is a **policy/deployment**
   expectation, **not** a `distill`-time rejection — the transports must be able to negotiate `MarshallingFormat`
   for the test-only path.
2. **[RATIFIED — Peter 2026-07-27] Is deferral (PARTIAL_SUPPORT) the right level** vs FULL_SUPPORT? **Ruling:
   PARTIAL_SUPPORT (defer).** FULL_SUPPORT would drop the constraint so the invocation-layer check never runs —
   a required `ATOMIC_DER` would no longer prevent an Atomic-JOSS codec. Deferral keeps `requireMarshallingFormat`
   /`verifyAndStripMarshallingFormat` as the enforcement point, matching the existing `Integrity`/
   `AtomicInputValidation` deferral precedent. The JOSS-prevention regression test is the guard (it would fail
   under FULL_SUPPORT).
3. **[RATIFIED + IMPLEMENTED — Peter 2026-07-27] Fix all four transports together + add regression coverage.**
   Done in merge `a96e677d7`: `tcp`/`http`/`uds` (`supportedClasses` PARTIAL_SUPPORT) and `ssl` (`ConnectionContext`
   `MARSHALLING` deferral carrying the constraint), plus `MarshallingFormatDeferralTest` — per-transport positive
   DER round-trip and JOSS-prevention (JOSS codec + required `ATOMIC_DER` → `UnsupportedConstraintException`), 8/8.
   The ssl path handles it deliberately (a dedicated `MARSHALLING` verdict, never `OK`), and a `MarshallingFormat`
   in a `ConstraintAlternatives` is fail-closed. Full `jgdms-jeri` suite 69 green.

## 5. References

- STD-008 (MarshallingFormat / DER wire-format constraint).
- `AtomicDerInvocationHandler.marshallingFormat()` (pins the requirement); `BasicInvocationHandler` ~line 975
  (client satisfies deferred constraint); `BasicInvocationDispatcher.verifyAndStripMarshallingFormat` (server).
- The gap: `tcp/Constraints.java`, `http/Constraints.java`, `uds/Constraints.java`, `ssl/ConnectionContext.java`.
- Surfaced by `examples/wire-protocol-showcase/demo7-filter-pushdown` (Outrigger CEL filter §8 proof).
