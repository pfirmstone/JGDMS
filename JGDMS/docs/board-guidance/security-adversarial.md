# Adversarial-Security Board Review — Guidance for Future Board Members

**Purpose.** This is the distilled playbook for the *adversary-in-the-room* seat on a JGDMS
design/merge review board: how to attack untrusted-network deserialization and transport
designs, how to turn a spec's stated *outcome* into "is the enforcing *mechanism* actually
present?", the JGDMS-specific gotchas, the principles that make the seat effective, and the
concrete war stories that produced the catches. It is written to be actionable by someone
who did not sit in the original reviews.

**Status:** guidance, not normative. Cites specific findings from the collection-codec merge
gate (STD-006 DER), the DER "any" element-form review (STD-006 type model), the QUIC-TLS
endpoint review, and the STD-010 QUIC-JERI transport review (v0.1 → rev.3).

**The one-sentence job.** On untrusted-network code the reviewer's job is to assume the peer
is hostile *after* a valid handshake and the bytes are hostile *always*, and to check that
every trust the design extends is bound by an enforced mechanism, not by an assumption or a
stated intention.

---

## 1. The recurring attack-shapes to hunt for

These are the shapes that recurred across every review. Hunt for each by name; each has a
worked instance in §5.

### 1.1 Confused-deputy (the highest-value hunt on any authz-bearing transport)
A component with authority X is induced to act on behalf of a party with authority Y, and
executes with X. On a transport the classic form is: code running *inside* a privileged
process dispatches work requested by a remote party but runs it under the *process's* ambient
authority rather than the *requester's*. **Tell:** the design specifies which identity the
authorization *check* uses, but is silent on which identity the *execution* runs under. Those
are different questions and the gap between them is the vulnerability (STD-010 §4.7, war story
§5.4).

### 1.2 Role-reversal authorization (a new trust *direction*)
Any time a design adds a *new direction* of request — server-initiated push, callbacks over a
client-opened connection, a mirror of an existing gate — the standing assumption "caller =
connection initiator" inverts. Every authz decision keyed on the old assumption is now
potentially wrong. **Attack it hardest.** Ask: on the reversed path, who is the caller, whose
privileges execute, whose identity is stamped, and can the *newly-trusted* direction be abused
to make the *other* party act under the wrong identity? (STD-010 §4.6/§4.7 — the server-push
role reversal was the single richest finding surface in any review.)

### 1.3 Decode-surface DoS (the deserialization reviewer's bread and butter)
Untrusted bytes drive allocation, recursion, or quadratic work *before* any semantic check
runs. Three sub-shapes:
- **Unbounded recursion** — nesting depth driven by wire data with no depth counter on the
  recursive path (war story §5.2, the nested-`Any` StackOverflow).
- **Unbounded/uncapped allocation** — element/entry counts not capped before building the
  container (the missing `maxCollection` cap, war story §5.1).
- **Algorithmic amplification** — an O(n²) check on n attacker-supplied elements within a
  legitimately-sized payload (the O(n²) duplicate scan, war story §5.1).
**The invariant that matters:** *bytes-in-flight bounds are not decoded-structure bounds.* A
transport (TCP window, QUIC `MAX_DATA`) caps bytes; a small number of bytes can decode into a
huge or deeply-nested structure. The decoder's own size-bound-before-allocation discipline is
undiminished by any transport flow control (STD-010 §8.2 got this right and said so explicitly;
verify future specs do too).

### 1.4 Ungated reconstruction doors ("second door to the same machinery")
A new wire form, tag, or path that reaches an object-reconstruction mechanism *bypassing the
gate that the primary path goes through*. **Tell:** the primary `@AtomicSerial` path runs the
`DeSerializationPermission("ATOMIC")` gate + endpoint `ResolutionContext` + `check(GetArg)`;
a new form (e.g. an `Any` tag=atomicSerialObject body) reaches the same reconstruction but the
design never states the gate applies to it (war story §5.3). A polymorphic/self-describing
form that lets the *wire* name the class to reconstruct is exactly where capability escalation
hides.

### 1.5 Side-channels and non-canonical-form escapes
- **Canonical-form breaks:** two encodings of the same value (non-minimal integer/enumerated,
  trailing-zero pad, tag-order freedom) → byte-equality and signature stability break. On a
  DER/canonical wire this is a correctness *and* a security property (Entry byte-matching,
  signature verification). Brute-force the comparator; don't trust "it's sorted" (war story §5.5).
- **Reject-non-canonical on decode:** a true canonical decoder must *reject* non-canonical
  input, not merely produce canonical output. Silently accepting unsorted/non-minimal input is
  a fail-secure gap even when the decoded object is value-correct (war story §5.1, the original
  pre-fix state).
- **Timing/correlation channels:** in-band vs out-of-band signals (acks, control channels) leak
  different metadata; prefer the design whose correlation is *structural and unforgeable* over
  one that carries an explicit correlator a malicious peer can misattribute (war story §5.6).

### 1.6 The "authenticated peer ⇒ trusted bytes / trusted actions" fallacy
Authentication of a peer is *necessary but not sufficient* for authorization. A peer that
completed a valid mTLS handshake is still hostile in these ways:
- it can send you **malformed/oversized decode input** (authentication doesn't sanitize bytes);
- it can invoke **operations it was never authorized for** (handshake ≠ per-operation authz);
- it can push **events/requests you never subscribed to or asked for** (war story §5.4b);
- it can be **compromised after the handshake** (the identity is pinned; the behaviour isn't).
Whenever a design leans on "the peer is authenticated," ask what *additional* binding
authorizes *this specific action* — a subscription, a capability, a policy keyed to the exact
resource. If the only gate is "is authenticated," that's a hole.

---

## 2. Review heuristics — turning a stated outcome into "is the mechanism present?"

This is the core technique. Specs (especially good ones) state the desired security *outcome*
crisply: "never authorized with the client's own privileges," "reject non-canonical," "fail
closed." A stated outcome is a *claim*, not a mechanism. The reviewer's value is converting
each claim into: **what concrete, enforced construct guarantees this, and is it in the design?**

### 2.1 The outcome→mechanism conversion, step by step
1. **Find the security claim** (usually a MUST/MUST NOT sentence, or a "guard" / "invariant").
2. **Name the mechanism that would enforce it** — the specific code construct: a depth counter
   checked before recursion, a permission check against a specific ACC, a `Subject.doAs`
   boundary, a strictly-ascending order assertion on decode, a keyed MAC.
3. **Search the design for that mechanism.** If it's there and correct → discharged. If the
   spec asserts the outcome but the mechanism is absent, wrong, or "left to the implementer" →
   that's your finding. **The gap between a stated outcome and an enforced mechanism is where
   the defects live** (this single heuristic produced both STD-010 HIGH server-push findings).
4. **Check the *default*.** Ask: if the implementer does the obvious/naive thing, does the
   claim hold by default, or does it require positive enforcement the spec must mandate? The
   confused-deputy execution-subject finding turned entirely on this: the *default* JERI/JAAS
   execution context inside the client process is the *client's* ambient subject, so "never the
   client's privileges" is an active requirement, not a passive property (§5.4).

### 2.2 Where to look first
- **The two-layer boundary.** JGDMS separates *structural/wire validation* (schema, decode
  bounds) from *semantic validation* (`check(GetArg)` in the constructor). Attacks live in the
  seam: a form that hands Layer 2 something it can't safely validate, or that shifts a guarantee
  from the wire to developer discipline without flagging it (DER "any" downgrade, §5.3b).
- **Every recursive decode path.** Trace whether `depth`/`MAX_NESTING` is threaded on *each*
  recursion, and — critically — whether the recursion is bounded by the *fixed schema token* or
  by *attacker-controlled wire data*. When a design moves dispatch from the token to the bytes,
  the old depth-safety assumption silently dies (§5.2).
- **Every new tag / union / self-describing form.** Enumerate: unknown tag → reject? tag/body
  type-consistency validated? each body category routed through the *same* gate as its typed
  equivalent? canonical (minimal) tag encoding required? (DER "any" review, §5.3.)
- **Every new request/trust direction.** Re-derive caller, execution subject, stamped principal,
  and the abuse question "can the newly-trusted side make the other act under the wrong identity?"
- **Every "rely on the platform" claim.** "QUIC flow control bounds it," "the engine doesn't
  implement 0-RTT so it's moot," "AEAD prevents splicing." Some are true and load-bearing (AEAD
  anti-splicing genuinely prevents peer-splice on migration — don't invent redundant mechanism).
  Some are false comfort (flow control ≠ decode bounds; "engine doesn't implement it yet" is
  fail-*open*-by-absence, not fail-closed-by-construction, §5 QUIC 0-RTT). Distinguish them.

### 2.3 Independent verification over trust
When you *can* run it, run it — don't trust the self-report.
- **Reproduce the test tally yourself** on the correct toolchain (collection codec: independently
  reproduced 468/468 on DirtyChai, confirmed the top commit was docs-only so the green state was
  genuinely set by the fix commit, and checked no other agent's build was running first).
- **Brute-force the property, don't eyeball it.** For the §11.6 octet-sort comparator I wrote a
  harness over an adversarial byte-array universe and checked antisymmetry, transitivity,
  zero-iff-equal, and that Java's TimSort accepts it across 200 shuffles — a signed-byte bug or a
  bad trailing-zero tie-break would have shown as a violation or a TimSort contract exception
  (§5.5). Eyeballing "looks unsigned" would not have proven transitivity.
- **Run the adversarial input against the built classes.** I compiled a probe that fed a
  non-canonical (reverse-sorted) set into the decoder and *observed* it was accepted — turning a
  suspicion into a confirmed finding with a concrete failure trace (§5.1).
- **Empty search ≠ absence; compile ≠ validated; "the tests pass" ≠ the property holds.** Verify
  against the real runtime path, not a self-built oracle.

---

## 3. JGDMS-specific gotchas

### 3.1 The `DeSerializationPermission("ATOMIC")` gate — semantics and where it's bypassable
- **What it is:** before any `@AtomicSerial (GetArg)` constructor runs, every class in the
  hierarchy whose constructor will execute must have `DeSerializationPermission("ATOMIC")`
  granted to *its protection domain* (checked against an ACC built from the classes' domains, so
  the grant must sit with the class's codebase, not the caller's). It lets a deployment restrict
  *which classes* may be reconstructed from an untrusted stream, independent of the parameter
  objects (which `check(GetArg)` validates separately).
- **The critical caveat:** **it is a NO-OP when no SecurityManager is installed.** Never rely on
  it as the sole gate in an SM-less deployment; it is one layer, and the disciplined decode
  bounds + `check(GetArg)` must stand on their own.
- **Where it's bypassable (hunt here):** any *new* reconstruction door that reaches the
  `@AtomicSerial` path without routing through `checkAtomicDeSerializationPermitted` and the
  endpoint-assigned `ResolutionContext`. The DER "any" `tag=atomicSerialObject` body is exactly
  such a door — a wire-named, fully-polymorphic reconstruction slot. Require it go through the
  *identical* gate + ResolutionContext + `check(GetArg)` as a declared element (§5.3). A second
  ungated door negates the whole point of the gate.
- **ResolutionContext discipline:** class resolution must use the *endpoint-assigned* loader, never
  the thread-context loader (the ambient-resolution failure class: wrong local copy, same-name
  type conflicts, breaks under OSGi). Any new decode path must thread the ResolutionContext.

### 3.2 Ambient-subject leakage (the confused-deputy's JGDMS form)
Code that dispatches a remote-requested action runs inside a process that has its *own* ambient
`Subject` / `AccessControlContext`. If the dispatch doesn't establish a `Subject.doAs`/`callAs`
boundary with the *requester's* principals (and a correspondingly reduced ACC), the requester's
action executes with the *process's* authority. This is the execution-time confused-deputy.
- **On a server** dispatching a client request today, this is handled: dispatch runs as the client.
- **On the reversed path** (client dispatching a server-pushed event), it is *not* automatic — the
  default execution context is the client's own, so the design must *positively require* the
  execution-subject swap (STD-010 §4.7 rule 5). "The permission check uses the server identity" is
  not enough; the *execution* must run as the server, or downstream authority leaks (§5.4).
- Related discipline: a privileged op that isn't wrapped in `doPrivileged` goes *viral* up the
  stack to the constrained infrastructure ACC; the cure is `doPrivileged` at the responsible frame,
  never a broader grant. And never broadcast ClassLoaders/capabilities through a channel the whole
  object graph can read (e.g. `getObjectStreamContext`); use a narrow guarded channel.

### 3.3 The "outcome vs enforced mechanism" gap — the JGDMS pattern
JGDMS specs are high-quality and state outcomes precisely, which paradoxically makes the
outcome-vs-mechanism gap the dominant defect class: the claim is so clearly stated it reads as
done, but the enforcing construct is missing or deferred. Both STD-010 HIGH findings were this;
the DER "any" findings were this (memo said "reject-non-canonical still applies" but was silent on
the *depth bound* and the *ATOMIC gate* through the `Any` path). **When a JGDMS spec states a
security outcome, assume the mechanism is absent until you find it, then confirm the mechanism is
tested with a NEGATIVE assertion** (the rev.3 fixes were only fully discharged once the harness
asserted the *execution* subject and the *unsubscribed-push rejection*, not just the positive path).

### 3.4 Canonical-form load-bearing sites
DER canonicity is load-bearing in three JGDMS places — signature verification over a TBS,
`@AtomicSerial` schema digests (content addresses), and Jini Entry byte-matching (matches stored
vs template bytes *without decoding*). A canonical-form break isn't cosmetic; it breaks one of
those. Any new element form (like `Any`) must preserve: octet-sort over complete TLVs, minimal
tag/length encoding, and a deterministic value→encoding mapping. Verify all three.

### 3.5 Environment / toolchain traps for the verifying reviewer
- Build/run on the **DirtyChai JDK** for `jgdms-der` (`--release 25`, needs the SM-capable JDK to
  run); confirm `JAVA_HOME` via `mvn -v` before trusting a build.
- **Check for another agent's running `mvn`/`java` before you build** — the toolchain is shared;
  two concurrent Maven builds is a known wedge. Redirect test output to file to avoid the
  surefire console-flush wedge.
- A green reactor can *mask* a real problem or *be masked by* an unrelated env flake — validate
  the module in isolation and confirm which commit actually set the green state (docs-only top
  commits don't).

---

## 4. The principles that made the reviews effective

1. **Assume hostile bytes always, hostile peer after handshake.** Authentication changes *who*,
   never *whether the input is safe*. Every decode is adversarial input; every authenticated peer
   is a potential post-compromise adversary.
2. **Outcome is a claim; mechanism is the proof.** Never accept a stated security property without
   locating the enforced construct. The gap is the finding.
3. **Check the default.** If the naive implementation violates the claim, the spec must *mandate*
   the enforcement — a property that requires positive action won't happen by itself.
4. **Necessary ≠ sufficient.** Authentication ≠ authorization; canonical *output* ≠ *reject
   non-canonical*; check-subject ≠ execution-subject; concurrency-bound ≠ rate-bound.
5. **Verify against the real runtime path, independently.** Reproduce tallies, brute-force
   properties, run adversarial inputs against built classes. Don't trust the self-report; don't
   trust a self-built oracle. Empty search ≠ absence; compile ≠ validated.
6. **Repair, don't amputate; report faithfully.** Rank findings by real exploitability with a
   concrete attacker scenario each; distinguish CONFIRMED from PLAUSIBLE; say when a mechanism is
   genuinely sound (AEAD anti-splicing) so the team doesn't build redundant defense. A finding
   without a concrete failure scenario is noise; a blocking call without exploitability is
   over-reach.
7. **Trace new directions and new doors exhaustively.** A new request direction re-opens every
   authz assumption; a new wire form re-opens every decode-gate assumption. Enumerate them.
8. **Least privilege at the seam.** Reduced ACC on dispatch, narrow guarded channels, gate every
   reconstruction door, bind every trust to the *specific* resource (subscription, capability),
   never to "is authenticated."

---

## 5. War stories — the defect and the tell that surfaced it

### 5.1 Collection codec: non-canonical input silently accepted + O(n²) dup scan + no size cap
**Defect (CONFIRMED):** the DER canonicalise-set/map decoder rejected only *duplicate* element
encodings; it never verified *ascending octet order*, so a peer that sent a validly-encoded but
*unsorted* set was silently accepted (a DER canonical decoder must reject non-canonical order).
Additionally the dup check was O(n²) (linear scan of seen-encodings per element) and there was no
`maxCollection` element-count cap on the decode loops.
**The tell:** the decode path checked `compareOctets(prev, cur) == 0` (dup) but never `< 0`
(order). I noticed the code produced canonical *output* but didn't *reject* non-canonical *input*
— the "canonical output ≠ reject non-canonical" asymmetry. **Confirmed by running it:** I compiled
a probe that fed a reverse-sorted `set:int` to the decoder and observed `[3,2,1]` accepted.
**Fix (verified in rev): ** strictly-ascending `< 0` decode check (subsumes the dup check, O(n)
predecessor-only), `bag:` non-decreasing, `MAX_COLLECTION=65536` on all six decode loops, plus the
Option-A SET(0x31)/SEQUENCE(0x30) tag with wrong-tag rejection both directions. I re-verified all
of it independently against the built classes (unsorted → rejected, dup → rejected, 65536 accepted
/ 65537 rejected, wrong tag both directions rejected) — not from the branch's own tests.

### 5.2 DER "any" form: nested-`Any` StackOverflow DoS (the depth-through-data catch)
**Defect (HIGH):** the *existing* decoder doesn't increment `depth` when recursing into a nested
collection — which was *safe* only because the element wire-type was a *fixed schema token*
(`set:int` can only contain ints; nesting is statically bounded by the token string, which is
digest-covered and not attacker-editable). The proposed `Any` form breaks exactly that: with
`set:Any`, each element's *body* carries its own discipline/token, so decode dispatch is driven by
**attacker-controlled wire data**, not the fixed token. A `set:Any` of tag=collection body=`set:Any`
of tag=collection … nested thousands deep recurses to StackOverflow before any `check()` runs; the
`maxCollection` cap doesn't help (one element per level — depth, not breadth).
**The tell:** I traced the depth threading in the *current* decoder, saw nested-collection recursion
passes `depth` unchanged, and asked *why that was ever safe* — the answer ("token bounds it") is the
exact assumption the new form invalidates. **The heuristic:** when dispatch moves from a fixed,
digest-covered token to attacker-controlled bytes, every bound that relied on the token silently
dies. Required fence: thread `MAX_NESTING` through every `Any`→collection/@AtomicSerial recursion.

### 5.3 DER "any" form: ungated `@AtomicSerial` reconstruction door + tag/body confusion
**Defect (a) (HIGH):** an `Any` `tag=atomicSerialObject(20)` body reaches the `@AtomicSerial`
reconstruction path and can name *any* schema-digest/class the attacker chooses — a fully
polymorphic reconstruction slot. The memo never stated it goes through the *same*
`DeSerializationPermission("ATOMIC")` gate + endpoint `ResolutionContext` + `check(GetArg)` as the
typed path. If it bypasses the gate, `Any` becomes a second, ungated door to reconstruct classes a
deployment's policy would deny on the typed path — a capability escalation.
**The tell:** "self-describing form where the wire names the class" is a reflex trigger to ask
"does it go through the gate the typed path goes through?" (§1.4).
**Defect (b) (MEDIUM):** tag/body *type* consistency (tag=scalarInt but body=a collection) and
*unknown tag* handling weren't specified as fail-secure rejects; and octet-sort determinism requires
the `AnyTag` ENUMERATED be canonical (minimal) — a non-minimal tag encoding gives two byte-forms of
the same value, breaking byte-equality/signature stability.
**Downgrade note:** `Any` shifts per-element *type* validation entirely onto the developer's
`check()` (the schema no longer commits element types) — a guarantee moved from wire to developer
discipline; the memo must flag it so a constructor relying on `Set<Foo>` homogeneity validates
element types when the field can be `Any`.

### 5.4 STD-010 server push: execution-subject swap (the outcome-vs-mechanism HIGH)
**Defect (HIGH):** §4.7 correctly *named* the confused-deputy and said a pushed event must be
authorized as the server "never the client's own privileges." But the dispatch runs *inside the
client process*, on client threads, under the client's ambient `Subject`/ACC. The spec specified
which identity the *permission check* uses — not which identity the dispatch *executes* under.
Default JAAS execution context = the client's own → downstream actions the pushed listener takes
(codebase download, onward call, guarded resource) execute with the *client's* authority.
**The tell:** the spec's authz rule talked about the *check* subject and was silent on the
*execution* subject — two different questions (§2.1 step 4, the "check the default" heuristic). The
*default* execution context is the vulnerability.
**Fix (verified in rev.3):** §4.7 rule 5 now mandates the dispatch **execute** under the server's
subject via `Subject.doAs`/`callAs` + a reduced ACC, forbids the client's ambient Subject/ACC from
leaking in, and the harness asserts the *execution* subject (not just the check) with a
no-client-leak negative (a listener probing for client privileges must fail to obtain them).

### 5.4b STD-010 server push: subscription-correlation (the "authenticated ⇒ trusted" HIGH)
**Defect (HIGH):** the client accepted any `0x01` push from the connection's *authenticated* server
peer and dispatched it to whatever listener matched — with no requirement that the event correspond
to something the client actually *subscribed to*. An authenticated (or post-compromise) mesh peer
could push forged events the client never asked for (spurious lease-expiration to force teardown,
fabricated `ServiceEvent`), or flood distinct events.
**The tell:** the only gate was "is the peer authenticated." Authentication is necessary but not
sufficient (§1.6); I asked "what binds *this event* to something the client authorized?" — nothing.
**Fix (verified in rev.3):** §4.6.3 requires a push be dispatched only to a listener the client holds
an *outstanding subscription* for, bound to `⟨authenticated peer, connection, listener⟩`; unsubscribed
push rejected fail-closed (stream reset, listener not invoked); subscription-A cannot invoke
subscription-B's listener; and §9.5 clarified that this subscription *security* admission is NOT the
forbidden application-level flow-control rationing (it would otherwise appear to conflict, and an
implementer might feel barred from the very defense needed). Harness carries both negatives.

### 5.5 Collection codec: the §11.6 octet-sort comparator (brute-force over eyeball)
**What I did:** rather than read the comparator and conclude "looks like a correct unsigned sort,"
I compiled a harness over an adversarial byte-array universe (lengths 0–3 over {0x00,0x01,0x7F,0x80,
0xFF}) and checked antisymmetry (0 violations), transitivity (0 violations), zero-iff-`Arrays.equals`
(0 key collisions), and that Java TimSort accepted it across 200 shuffles (it did). This positively
proves it's a consistent total order — a signed-byte bug (`byte` is signed in Java; must mask `&0xFF`)
would surface as an antisymmetry/transitivity violation, and a bad trailing-zero tie-break would
surface as a TimSort "comparison method violates its general contract" exception.
**The lesson:** a comparator's correctness is a *property to be proven*, not a *code shape to be
recognized*. Brute-force is cheap and definitive.

### 5.6 STD-010 DGC ack: the in-band-vs-separate-stream ruling (structural correlation wins)
**The question (Peter's):** does the DGC ack ride in-band on the request's own stream (chosen) or a
separate ack stream? **Ruling: in-band is the more secure choice.** Decisive axes:
- **Correlation-integrity:** in-band binds the ack to exactly one request by the stream it arrives
  on — unforgeable, structural. A separate ack stream needs an explicit request-correlator, a new
  forgeable field a malicious peer can misattribute (ack request A while claiming B → advance DGC
  lease state for an unprocessed request). This alone settles it.
- **Control-channel reintroduction:** a separate ack stream *is* a mux-like control channel — the
  thing the transport exists to retire; it adds cross-stream ordering + shared-fate concerns.
- **Stream-exhaustion:** in-band consumes zero extra streams (bytes on an existing stream);
  per-request ack streams double `MAX_STREAMS` pressure.
- **Side-channel:** in-band's timing leak (processing-completion time) is no worse than any
  after-processing ack; a separate stream leaks the same timing plus stream-open metadata.
**One fence:** the in-band marker must be position-defined (recognized only at the exact
post-response-object position, from the DER structure), never content-scanned — else a response body
whose trailing bytes match the marker is misread as an ack, or a real ack is suppressed/forged.
**The lesson:** when choosing between signal-carriage designs, prefer the one whose correlation is
*structural and unforgeable* over one carrying an explicit correlator a peer can lie about.

---

## 6. A compact checklist for the seat

For any untrusted-network deserialization/transport design, walk this list:

- [ ] **Decode DoS:** every recursive path threads a depth bound? recursion bounded by fixed
      digest-covered token or by attacker bytes? element/entry counts capped *before* allocation?
      any O(n²) on attacker-supplied n? bytes-bound conflated with structure-bound anywhere?
- [ ] **Canonical form:** decoder *rejects* non-canonical (not just emits canonical)? minimal
      integer/enumerated/length? deterministic value→encoding? comparator brute-forced?
- [ ] **Reconstruction doors:** every path to object reconstruction routed through the ATOMIC gate
      + endpoint ResolutionContext + `check(GetArg)`? any wire-named/self-describing class slot? any
      new tag/union: unknown-tag reject? tag/body consistency? each body category gated as its typed
      equivalent?
- [ ] **New trust directions:** caller re-derived by initiator? execution subject swapped (doAs) —
      not just the check subject? stamped principal from authenticated peer, never wire, never
      ambient, never anonymous? can the newly-trusted side make the other act under the wrong identity?
- [ ] **Authenticated ≠ trusted:** every trust bound to a *specific* resource (subscription,
      capability), not merely to "is authenticated"? unsubscribed/unauthorized action rejected
      fail-closed? admission gate distinguished from forbidden rationing where relevant?
- [ ] **Platform-reliance claims:** each "the platform handles it" true and load-bearing, or false
      comfort? "not implemented yet" = fail-open-by-absence, not fail-closed-by-construction?
- [ ] **Outcome→mechanism:** every MUST/guard/invariant → named enforcing construct located in the
      design → tested with a NEGATIVE assertion, not just the happy path?
- [ ] **Independent verification:** tally reproduced on correct toolchain? properties brute-forced?
      adversarial input run against built classes? which commit set the green state?

---

*Written as the retirement capstone of the adversarial-security board seat, 2026-07-05.
Findings cited are from the collection-codec merge gate, the DER "any" element-form review, the
QUIC-TLS endpoint review, and the STD-010 QUIC-JERI transport review (v0.1 → rev.3). Guidance,
not normative; the enforcing normative text lives in the respective STD-* documents and their
conformance/fuzz harnesses.*
