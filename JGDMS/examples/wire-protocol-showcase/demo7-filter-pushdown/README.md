# Demonstration 7: Filter records inside a live space, server-side, without the class

## What you will see

A single Java program starts a **live in-memory space** (a shared store that
programs read from and write to) and a **transaction manager**, and drives a
client that filters the space's records by a **written value rule** —
`temperature above 20 and station name starts with "North"` — evaluated **inside
the space, without the space ever loading the record class**.

The class-free property is made concrete with **two class loaders in one JVM**:

- The space and transaction manager run in the application class loader, whose
  classpath **deliberately lacks** the record class `WeatherReading` — proved by
  a `Class.forName(...)` that must throw "class not found". From then on the
  space works only from each record's own on-the-wire DER field description.
- The client logic runs in a **child** class loader that *does* have
  `WeatherReading`. It authors the rule and drives the filtered operations.

The space proxy is shared as a live object (no proxy download, no lookup
service), but the filtered operations are still **real**: they make genuine JERI
calls over a loopback TCP endpoint, so records are DER-marshalled over the wire
and the space evaluates the rule over each candidate's schema class-free. Sharing
the proxy across two class loaders instead of two processes is *stronger* for the
class-free claim — no marshalling can smuggle the class into the space's loader.

The program checks each claim and exits non-zero if any fails:

1. **Class-free, value-rule filtering, server-side (§8.2).** A "return the
   contents that match" query and a "take the contents that match" query each
   return **exactly** the warm-northern readings — computed inside the space,
   which cannot load the record class. A subclass reading
   (`NorthStationReading`) is matched by the fields it inherits (§8.4b). The
   plain template match spaces normally use can only test a field for *equality*
   with a fixed value; it cannot express "temperature **above** 20" or "name
   **starts with** North". The written rule can, and the space runs it.

2. **The rule ran, and ran fail-closed (§8.2 / §8.4c).** The space's operator
   counters show more candidates were *evaluated* than *passed* (some were
   evaluated and honestly rejected) and that **none** failed to decode on the
   happy path. A separate reading with a station name larger than the 64 KiB
   projection budget is **excluded** and counted (§8.4c) — a hostile oversized
   record cannot make the space do unbounded work.

3. **A blocking query resolves only on a genuine match (§8.4a).** A filtered
   read that finds nothing yet, then waits, is *not* satisfied by a later
   cold-northern write (the rule evaluates it to false); it resolves only when a
   genuine warm-northern reading arrives — proving the wait path evaluates the
   rule too, and never falls open.

## The confused-deputy step (§8.3) is best-effort

The design's security headline (§8.3) is the *confused-deputy* invariant: a
standing filtered query registered by a client **P** must never even have the
rule *evaluated* against an entry written under another client **Q**'s
uncommitted transaction, because P is not entitled to see it; the rule runs
exactly when — and not before — P becomes entitled (on commit).

That invariant **is enforced** by the server: the watcher's transactional
entitlement gate runs *before* `FilterEval` ever evaluates a candidate. It is
covered by `SiteFStructuralTest` and the availability-/event-watcher unit tests,
and demo7 additionally shows it **server-side** — during the attempt below the
orchestrator observes a **zero `filter.evaluated` delta and zero
`FilterEvaluation` JFR events** while Q's transaction is uncommitted.

Demonstrating the *client-observable* end-to-end flow additionally requires
handing two **exported proxies** to the space — client P's `RemoteEventListener`
stub (for the standing query callback) and Mahalo's transaction-manager proxy
(embedded in the `ServerTransaction`). Both must be reconstructed at the space's
endpoint, which the JGDMS 4.0 smart-proxy transport does by resolving a bootstrap
proxy through an **integrity-verified codebase** — a lookup service or an httpmd
class server. This demonstration deliberately runs without that download
infrastructure (its whole point is that *filtering* needs none), so the live §8.3
step cannot complete: the program **attempts** it and prints a clear
`[LIMITATION]` note with the root cause, and this does **not** affect the exit
code. The failure is **transport-independent** (identical over plaintext tcp and
ssl) and orthogonal to the CEL filter. Standing up a class server / lookup
service purely to move those proxies between two in-JVM endpoints is out of scope
for a "no download needed" filtering demo.

## How to run it

```
# Windows
./run.ps1

# macOS / Linux / Git Bash
./run.sh
```

The launcher builds the space, transaction-manager and rule-language modules it
needs from the current source tree (one offline Maven build), compiles the entry
class and the client logic into `out/entry` (loaded by the child loader) and the
orchestrator into `out/app`, then runs one JVM whose application classpath
**excludes** `out/entry` (so `WeatherReading` is absent from the space's loader)
and passes `out/entry` as an argument for the child loader. Both services are
exported over plain TCP with no transport security — this is a same-host
single-JVM demonstration.

## Why it matters

**No class needed, where the data lives.** A running space can answer "which of
the records I hold match this rule?" without the record's class. It reads the
fields straight from the bytes, guided by the shape description that travelled
with each record. There is no class to download on the space side, so nothing to
secure and no reconstruction code for a hostile record to attack.

**The rule is bounded and fail-closed.** The value rule has no loops and no
recursion, is cost-checked when registered, and every per-record evaluation runs
under a fixed decode budget. A record that will not decode, lacks a field, or is
oversized is treated as a non-match — never a crash, never unbounded work, never
a wrong answer — and the exclusion is counted for the operator, never leaked to
the client.

**Entitlement precedes evaluation.** The rule is only ever evaluated against a
record the requesting client is already entitled to observe (INV-1, §8.3),
enforced as a *mechanism* in the watcher — the gate runs before the rule. See
the best-effort note above for why the live end-to-end demonstration of this
needs infrastructure beyond this demo.

## A note on what the checks read

The client checks the *identities* of the records returned. The counter and
JFR-event proofs — "evaluated but not passed", "the oversized record was
budget-excluded" — are read **in the space's JVM**, because those operator
counters are deliberately never sent to a client (an exclusion count is itself a
faint signal about data the client cannot see, so it stays operator-only). The
orchestrator exits non-zero if any of these checks fail.

## Framework dependency (a gap this demo surfaced)

demo7 is the first end-to-end exercise of a **DER-only Outrigger proxy making
real filtered calls over a plaintext `tcp` JERI endpoint**. Every DER Outrigger
proxy pins `MarshallingFormat.ATOMIC_DER` as a wire-layer requirement, and
`BasicInvocationHandler` documents that the transport *defers* that
invocation-layer constraint and satisfies it at the invocation layer
(`requireMarshallingFormat()` / `BasicInvocationDispatcher`) — but the JERI
transports never learned to defer it: they reject an unrecognised requirement at
constraint-distill time. This is an **incomplete JGDMS-STD-008 sec.18.3 rollout
that affects all four transports** (`tcp`, `http`, `uds`, `ssl`), not just
plaintext.

demo7 depends on a **one-line `tcp` defer-fix** in
`jgdms-jeri/.../net/jini/jeri/tcp/Constraints.java` (recognise
`MarshallingFormat` as a partial/deferred constraint so it goes to the
unfulfilled-requirements set rather than throwing). This is ratified as a
**test-only, plaintext-DER dependency**. The board finding
`docs/FINDING-JERI-MarshallingFormat-Plaintext-Transport-Gap.md` tracks fixing
all four transports properly.
