# Design Proposal — proxy raw-wire-form retention via a synthetic decode-injected `serialForm` field

**Status:** DESIGN REVIEW (pre-implementation) · **Owner:** JGDMS (Peter) · **Supersedes:** the committed immutable-C mechanism in `077f39269` (local only, not pushed)
**Reviewers:** R1 (determinism / DER-fidelity), R2 (security-adversarial). Read `docs/JGDMS-Board-Reviewer-Guidance.md` + your lens doc under `docs/board-guidance/` first.

---

## 1. Problem

A narrowed (interface-dropped) `[8]`-decoded DER/object-stream proxy must retain the **sender's original `[8]` proxy TLV bytes** (interface count + all interface names *including the ones this node could not resolve* + the nested handler), so that if this node re-forwards the proxy it re-emits the **full** interface set verbatim rather than re-deriving from its own narrowed `getClass().getInterfaces()` (which would silently drop interfaces and forfeit the sender's `@AtomicSerial`-validated integrity — STD-009 §6.4).

The committed implementation (`077f39269`, "immutable-C") retained the bytes on the handler via a **copy constructor** `BasicInvocationHandler(BasicInvocationHandler other, byte[] rawForm)` + `withRawForm()`, which der called *after* decoding the handler. Two problems:

1. **Source-level overload ambiguity (regression).** The new `(BasicInvocationHandler, byte[])` ctor collides with the legitimate `(BasicInvocationHandler, MethodConstraints)` copy ctor: `new BasicInvocationHandler(handler, null)` now matches both. Broke `qa .../ConstructorAccessorTest:138`. japicmp passed (distinct **binary** signatures) — the gate does not catch source ambiguity under a `null` literal. (`(other, MethodConstraints)` with null is a *legitimate* call: it is the copy ctor behind `setConstraints(null)` = an unconstrained proxy.)
2. **Wrong altitude.** Retention is bolted on by a der-orchestrated post-decode copy, rather than captured as part of the handler's deserialization — the handler is the proxy's invocation handler and is the natural owner of "the bytes I was decoded from."

## 2. Proposed design (owner-directed)

Capture the retained bytes **during the handler's deserialization**, via a synthetic `serialForm` field the framework injects — no copy constructor:

1. **Declare, don't serialize.** `BasicInvocationHandler.serialForm()` declares a field `rawForm : byte[]`, **marked synthetic/injected** (a new flag on the `SerialForm` entry). `serialize()`/`putArg` never writes it.
2. **Framework special-cases the synthetic marker (generic, not a class hardcode):**
   - `SchemaGenerator` **excludes** synthetic-marked fields from the schema and `schemaDigest` (wire-neutral: every `@AtomicSerial` class's wire form and digest are unchanged).
   - The encoder **skips** synthetic-marked fields (nothing on the wire).
   - On decode, the framework populates the synthetic slot in `GetArg` from **injected decode context**, not from the wire.
3. **der supplies the value.** `decodeProxy` (DER path) / the object-stream `[8]` decode — the only code that holds the enclosing `[8]` proxy TLV and knows which interfaces dropped — **injects** those bytes into the handler's `GetArg` synthetic slot (only when narrowing occurred; otherwise the slot is absent/null).
4. **Uniform construction.** `BasicInvocationHandler(GetArg)` reads `this.rawForm = arg.get("rawForm", null, byte[].class)` into a `final` field. One construction path. No `(handler, byte[])` ctor, so **the ambiguity does not exist**.

Re-forward and the write-side fence are unchanged from the reviewed immutable-C: both `[8]` encode sites consult the retained bytes before any fresh re-encode; a retaining handler re-emits verbatim. The proxy self-check (`getInvocationHandler(proxy) == this`) still passes because the handler *is* the proxy's handler (no wrapper) — `rawForm` is wire-invisible and excluded from `equals`/`hashCode`.

## 3. Key considerations for the board to validate

1. **schemaDigest neutrality (R1).** Confirm a synthetic-marked `serialForm` entry can be excluded from `SchemaGenerator`'s schema + digest such that **no** `@AtomicSerial` class's wire form changes (esp. `BasicInvocationHandler`/`AtomicInvocationHandler`/`AtomicDerInvocationHandler`, and that a class *not* using the feature is byte-identical). This is the load-bearing property — if the synthetic field leaks into the digest, Entry matching + signatures + the serial-schema gate all break.
2. **Layering.** der/platform must **not** reference jeri (dependency is jeri → der → platform). The mechanism must be generic — the framework recognizes the *synthetic marker* and der injects the value — never a hardcoded `BasicInvocationHandler` reference in der/platform. Confirm the `SerialForm` flag + injection channel live at the right layer.
3. **Both marshalling paths.** `BasicInvocationHandler` is `@AtomicSerial` on **both** the DER codec (`ObjectCodec`) and the object-stream path (`AtomicMarshalInputStream`/`AtomicMarshalOutputStream`). The old `BoomerangProxyHandler` covered both. Confirm the synthetic-injected-field support exists on both, or scope which path narrowing applies to.
4. **GetArg injection channel.** `DerGetArg` (and the object-stream `GetArg`) need an "injected values" channel that `decodeProxy` populates before invoking the handler `(GetArg)` ctor. Assess how invasive this is and whether it is a clean addition to the `@AtomicSerial` contract.
5. **Security (R2).** The injected bytes are the **received** `[8]` bytes (decode context), re-validated on any subsequent decode. Confirm: the synthetic field cannot become an attacker byte-injection or gadget vector; it does not weaken the self-check; a synthetic-marked field on an *arbitrary* attacker-named `@AtomicSerial` class cannot be abused (who is allowed to declare a synthetic-injected field, and what can be injected?). Bound the new surface.
6. **Generality vs special-case.** Is "synthetic decode-injected `serialForm` field" a clean **general** framework capability (any `@AtomicSerial` class may declare a context-injected field), or should it be narrowly scoped to the proxy-handler retention case? A general capability is more powerful but widens the `@AtomicSerial` contract — weigh it.

## 4. Alternatives (for the record)

- **Committed immutable-C (copy ctor):** localized, no framework change, but introduces the source ambiguity and captures post-decode rather than at deserialization. This proposal supersedes it.
- **B (inner-proxy delegation), A (skip-wrap):** evaluated earlier and rejected (A not general / drops re-forward; B carries the Object-method identity trap). Not revisited here.

## 5. Deliverable requested from the board

For each lens: **APPROVE / APPROVE-WITH-CHANGES / BLOCK** on the *design* (not code), the specific mechanism for each of the six considerations (esp. #1 digest-neutrality and #2 layering), and — if you would do it differently — the concrete alternative. This gates implementation.
