# Granola-Inspired API Change Proposals for Mahalo

## Background

The [Granola paper](https://www.usenix.org/system/files/conference/atc12/atc12-final118.pdf)
("Low-Overhead Asynchronous Distributed Transactions", Cowling & Liskov, USENIX ATC 2012)
describes two protocol variants beyond standard two-phase commit (2PC) that
substantially reduce coordination overhead:

| Variant | Participants communicate with each other? | Rounds | When applicable |
|---------|-------------------------------------------|--------|-----------------|
| **Coordinated** (standard 2PC) | No | 2 | Multi-server writes |
| **Independent** (Granola ordering) | Yes (read timestamps) | 1 | Multi-server reads |
| **One-shot** (no coord.) | No | 1 | Single-server |

Two optimisations already implemented in Mahalo without API changes:

- **Opt-1** – `CommitJob`/`AbortJob` skip `NOTCHANGED`/`COMMITTED`/`ABORTED`
  handles (removes wasted thread-pool submissions).
- **Opt-2** – Durable-log write is pipelined with the prepare phase (overlaps
  disk and network I/O).

The two remaining Granola-inspired optimisations require new API surface.

---

## Default Interface Method Compatibility in Distributed Java

Before analysing the proposals, it is important to understand how default
interface methods behave in JGDMS's distributed model, because they provide
a compatibility mechanism that changes the risk/benefit calculus.

**Key properties:**

1. **Old client, new interface class never loaded** — a client JVM that never
   loads the updated interface class simply cannot call the new method.  This
   is the normal case for long-running services that have not been restarted.

2. **New client, proxy does not override the default method** — if the client
   JVM loads a newer version of the interface (containing the default method)
   but the remote proxy class was compiled against the old interface and does
   not override the default, Java will execute **the default method body
   locally** on the proxy instance, without dispatching a remote call.  The
   default method can therefore delegate to older existing remote methods to
   provide a correct but unoptimised fallback.

3. **New client, proxy overrides the method** — the proxy's invocation handler
   dispatches the call remotely as normal.  The optimisation is active.

The consequence is that adding a method **with a well-chosen default** to a
Remote interface does not break binary compatibility with existing proxy/stub
classes: they simply fall back to the default behaviour.  The critical design
question therefore becomes: **what should the default method do when it runs
locally on the proxy without the server receiving a call?**

---

## Optimisation 3 - Timestamp-Ordered Single-Round Commit (WITHDRAWN)

> **Status: withdrawn / removed 2026-06-14.** This was implemented
> (`prepareWithTimestamp()` default on `TransactionParticipant`,
> `TimestampedVote`, `LamportClock`, `AbstractTimestampParticipant`, a
> per-transaction coordinator clock in `TxnManagerTransaction`, and
> `ParticipantHandle.commitTimestamp`) and then deleted from `jgdms-platform`
> and `mahalo-service` as **not viable**. See the project review note
> `jgdms-mahalo-granola-review` for the full analysis.

### Why it was withdrawn

1. **Atomicity hazard.** `AbstractTimestampParticipant.prepareWithTimestamp()`
   self-committed during prepare (`prepare()` then `commit()`), and `PrepareJob`
   drove it for *every* participant of a multi-participant transaction. If one
   participant self-committed and another then voted `ABORTED` (or failed), the
   transaction tore - the committed participant could not roll back. In Granola,
   self-commit is safe **only** for the *independent* class (every participant
   reaches the same uniform outcome; the only negative vote is `CONFLICT`).
   Mahalo keyed the fast path off "participant extends
   `AbstractTimestampParticipant`", not off "transaction is independent", so a
   participant that could legitimately abort on local state was unsafe here with
   nothing to prevent it.

2. **The timestamps ordered nothing.** The coordinator clock was a *field of
   `TxnManagerTransaction`* - i.e. per transaction, starting at 1 - so it gave
   no cross-transaction ordering. And the protocol never agreed a single `max`
   timestamp across participants nor executed in timestamp order, so it did not
   provide Granola serializability regardless.

3. **Marginal benefit.** For a single participant, `prepareAndCommit()` is
   already a one-round commit (and single-participant uses it again now). The
   only path Opt-3 actually changed was the multi-participant one - exactly the
   unsafe case.

The serializable-ordering goal is better pursued by **Opt-5** below, which keeps
the manager as set-assembler and recovery authority, moves the commit round to
the participants, and gates self-commit on an explicit *independent* declaration.

---

## Optimisation 5 - Peer-to-Peer Independent Commit (proposal)

### Goal

Get Granola's independent-transaction win (one round, lock-free, non-blocking)
*correctly*: participants exchange timestamp votes directly, agree on a single
`max` timestamp, and commit at it - no central commit round. This is valid
**only for genuinely independent transactions** (uniform commit/abort outcome).

### Shape (manager assembles, peers vote)

Jini join is *lazy* (services join the manager as they are touched), unlike
Granola's up-front set. So the manager stays assembler and recovery authority;
only the commit round goes peer-to-peer:

1. Client declares the transaction independent (new
   `TransactionConfig.independent`, alongside the existing `readOnly`). The peer
   path runs **only** when independence is declared *and* every participant is a
   `PeerTransactionParticipant`; otherwise standard 2PC.
2. At commit the manager writes the `CommitRecord` (it remains the durable
   recovery authority), builds a `PeerSet`, and calls `beginIndependent(PeerSet)`
   on each participant.
3. Each participant prepares, proposes a Lamport timestamp, and sends its vote
   directly to every peer via `deliverVote(...)`. When all COMMIT votes are in,
   it commits locally at `max(timestamps)`. Any ABORT/CONFLICT vote -> cease
   (and for CONFLICT the client retries with a new id).
4. The manager awaits completion (or hands off to the settler) and replies.

### Sketch (JGDMS types)

```java
// Prepared peer-list payload handed to each participant by the manager.
@AtomicSerial
public final class PeerSet implements Serializable {
    long id;
    Uuid self;                                 // recipient's id in the set
    Map<Uuid,TransactionParticipant> peers;    // all participants, incl. self
    long seedTimestamp;                        // coordinator clock seed
    TransactionManager recoveryMgr;            // durable fallback authority
    MethodConstraints peerConstraints;         // how each peer must be called
}

@AtomicSerial
public final class PeerVote implements Serializable {
    enum Kind { COMMIT, ABORT, CONFLICT }      // COMMIT covers PREPARED/NOTCHANGED
    Kind kind;
    long proposedTimestamp;                    // 0 unless COMMIT
}

public interface PeerTransactionParticipant extends TransactionParticipant {
    // Manager kicks off: prepare, propose a timestamp, START peer voting.
    // Returns the proposed vote so the manager can log it for recovery.
    PeerVote beginIndependent(PeerSet peers)
        throws UnknownTransactionException, RemoteException;

    // Peer -> peer: another participant delivers its vote. Idempotent in 'from'.
    // When all COMMIT votes are in, commit locally at max(timestamp).
    void deliverVote(long id, Uuid from, PeerVote vote)
        throws UnknownTransactionException, RemoteException;

    // Recovery: resolve an in-doubt transaction without the original driver.
    PeerVote queryVote(long id)
        throws UnknownTransactionException, RemoteException;
}
```

Coordination logic ships in the downloaded transaction proxy / an
`AbstractPeerParticipant` base, so participant services change minimally (as
`AbstractTimestampParticipant` intended, but without the self-commit hazard).

### Hard parts (must be designed, not assumed)

- **Trust mesh.** Today's star (participant <-> manager) becomes a mesh
  (participant <-> participant). Each peer must authenticate/authorise the peers
  it votes with - `ProxyPreparer`/trust verification + `MethodConstraints`
  between services that may never have met. The manager (assembler) vouches via
  `PeerSet.peerConstraints`. This is the load-bearing wall under DirtyChai/POLP
  and should be prototyped first.
- **Recovery ownership.** Keep it with the manager (`recoveryMgr` + the
  `CommitRecord` + `queryVote`); do not push durability into the (possibly
  unreplicated) participants.
- **Scale.** `deliverVote` fan-out is O(n^2) and trust is N x N; build the
  2-participant slice first and measure before generalising.

### Status

Proposal only - not implemented. A real Lamport clock (manager-wide, persisted)
would be (re)introduced as part of this work; the previous per-transaction clock
was one of the reasons Opt-3 was withdrawn.
---

## Optimisation 4 — Read-Only Transaction Hint at `create()`
### (Default method on `TransactionManager`)

### Problem

Granola's *independent* protocol (single-round reads) is most useful for
transactions that are known to be read-only at creation time — e.g., analytics
queries or cache lookups.  Currently Mahalo treats every transaction as
potentially read-write until `commit()` is called, imposing full 2PC overhead
even when no participant ever wrote anything.

If the coordinator knows at `create()` time that the transaction is read-only it
can:
1. Skip writing a `CommitRecord` to the durable log (reads never need rollback).
2. Instruct participants to use `NOTCHANGED`-path optimisation eagerly.
3. Skip the entire commit phase if every participant confirms `NOTCHANGED`.

### Proposed API Change

Each option below can be implemented as a **default method**, which resolves the
binary-compatibility concern described in the previous section.

**Option A — Default overload on `TransactionManager`:**

```java
/**
 * Begin a new top-level transaction with an optional read-only hint.
 * The transaction manager may apply optimisations appropriate for
 * transactions that will never modify shared state (e.g., skipping
 * durable-log writes and the 2PC commit round).
 *
 * <p>If any participant calls {@code prepare()} with a non-{@code NOTCHANGED}
 * vote, the transaction manager must fall back to the full 2PC protocol
 * transparently.
 *
 * <p><b>Default behaviour (compatibility fallback):</b> ignores the
 * {@code readOnly} hint and delegates to {@link #create(long)}.  The
 * default body executes <em>locally on the proxy</em> when the remote
 * service does not yet implement this overload; the client obtains a
 * normal (read-write) transaction and correctness is preserved.
 *
 * @param lease    the requested lease duration
 * @param readOnly hint that this transaction will not modify shared state
 * @return transaction id and lease
 * @throws LeaseDeniedException if the manager will not grant the lease
 * @throws RemoteException if a communication error occurs
 * @since JGDMS 3.1 (proposed)
 */
default Created create(long lease, boolean readOnly)
        throws LeaseDeniedException, RemoteException {
    return create(lease);   // fallback: treat as regular read-write transaction
}
```

**Option B — Default separate method:**

```java
/**
 * Default behaviour: delegate to {@link #create(long)}, ignoring the
 * read-only hint if the remote service does not support this method.
 */
default Created createReadOnly(long lease)
        throws LeaseDeniedException, RemoteException {
    return create(lease);
}
```

**Option C — Default method with `TransactionConfig` parameter object**
(most extensible):

```java
/**
 * Configuration bag for transaction creation.
 * Additional hints (isolation level, priority, etc.) can be added in
 * future without changing the method signature.
 *
 * Uses {@code @AtomicSerial} (JGDMS convention for all wire-serialisable
 * types) for safe, atomic deserialisation.
 */
@AtomicSerial
final class TransactionConfig implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    /** True if the caller guarantees no participant will modify state. */
    public final boolean readOnly;
    /** Requested isolation level (reserved for future use). */
    public final int isolationLevel;

    /** Default constructor — read-write, serializable isolation. */
    public TransactionConfig() {
        this(false, SERIALIZABLE);
    }

    public TransactionConfig(boolean readOnly, int isolationLevel) {
        this.readOnly = readOnly;
        this.isolationLevel = isolationLevel;
    }

    /** {@code @AtomicSerial} deserialisation constructor. */
    public TransactionConfig(GetArg args) throws IOException, ClassNotFoundException {
        this(args.get("readOnly", false),
             args.get("isolationLevel", SERIALIZABLE));
    }
}

/**
 * Default behaviour: delegate to {@link #create(long)}, ignoring the
 * supplied config if the remote service does not support this method.
 */
default Created create(long lease, TransactionConfig config)
        throws LeaseDeniedException, RemoteException {
    return create(lease);
}
```

### Default Method Behaviour When the Server Does Not Receive a Call

When the client holds a proxy compiled against the old `TransactionManager`
interface (no overriding implementation of the new method), Java executes the
default method locally.  The default body calls `create(lease)`, which *is*
an existing Remote method and is dispatched normally.  The result is a
perfectly valid read-write transaction — the optimisation is simply not applied.
**Correctness is never compromised; only the performance benefit is absent.**

This behaviour is appropriate because:
- The hint is advisory, not semantic.  A `readOnly=true` request silently
  obtaining a read-write transaction is always safe.
- No new failure mode is introduced: the client code path is identical to what
  it would have called before the new method existed.

### Integration with Mahalo

1. **`TxnManagerTransaction`** — add a `boolean readOnly` flag set at
   construction.  In `commit()`, if `readOnly && allNotChanged()`:
   - Skip `CommitRecord` write and log write entirely.
   - Transition directly to `COMMITTED`.
2. **`TxnManagerImpl.create()`** — pass the hint through to
   `TxnManagerTransaction`.
3. **`TxnManagerImplInitializer`** — override the default in the proxy so that
   the hint is forwarded to the server over the wire.

### Pros

- **Zero disk I/O** for confirmed read-only transactions: no `CommitRecord`,
  no `invalidate()`.
- **Zero network overhead** for the commit phase when every participant voted
  `NOTCHANGED`: `CommitJob` is never created.
- Hint is advisory: if any participant votes `PREPARED`, the manager silently
  falls back to full 2PC — no change in correctness guarantees.
- **Binary-compatible with existing proxies**: old stubs run the default
  locally and fall back to `create(lease)` — no recompilation or
  redeployment required for existing proxy/stub classes.
- Option C (`TransactionConfig`) offers the most forward-compatible surface
  while sharing the same default-fallback mechanism.

### Cons

- **False hints are safe but wasteful**: if a client marks a transaction
  read-only but a participant does write, the manager must detect this and
  revert to full 2PC.  The detection logic adds code complexity.
- **Client API must also change**: `UserTransaction` / `Transaction.create()`
  helpers need to propagate the hint, otherwise most callers will continue to
  use the existing `create(long)` overload and gain nothing.
- **Recovery implications**: the absence of a `CommitRecord` for a read-only
  transaction must be distinguishable from a crash-before-log scenario.  One
  approach is a lightweight `ReadOnlyRecord`; another is to simply abort
  transactions with no `CommitRecord` on recovery (already the default).

### Deeper Investigation — NestableTransactionManager, Migration, and Security

#### Interaction with `NestableTransactionManager`

`TxnManagerImpl` does **not** currently implement `NestableTransactionManager`
(the nested-transaction branch is marked `//when I implement nested
transactions`, line 1078 of `TxnManagerImpl.java`).  Nested transactions are
therefore out of scope for the current Mahalo implementation.

However, `NestableTransactionManager` extends `TransactionManager`, so the
default `create(long, boolean readOnly)` / `create(long, TransactionConfig)`
methods would be inherited.  When nested transactions are eventually
implemented, the following rule must apply:

> **Rule**: any call to `NestableTransactionManager.promote(id, parts,
> crashCounts, drop)` that promotes at least one participant into a parent
> transaction marked `readOnly=true` must **immediately clear the
> `readOnly` flag** on that parent transaction, downgrading it to a
> read-write transaction.  The manager cannot know at `promote()` time whether
> the promoted participant will vote `PREPARED`, so the conservative approach
> is to downgrade on any `promote()` call.

This is a one-liner in `TxnManagerTransaction`: `this.readOnly = false;` at
the start of the promote path.  No security or correctness concern arises
because the downgrade merely causes additional durability work (log write +
commit phase) that would have been needed anyway had the transaction started
as read-write.

#### `TransactionConfig` migration story

Adoption is additive and incremental:

| Caller type | Action required | Gets the optimisation? |
|---|---|---|
| **Existing callers** using `create(long lease)` | None — existing code compiles and runs unchanged. | No (full 2PC as before). |
| **New callers** wanting the hint | Change `create(lease)` → `create(lease, new TransactionConfig(true, SERIALIZABLE))`. Requires `TransactionConfig` on the classpath (platform JAR). | Yes, if the service is also upgraded. |
| **Old proxy, new client** | The default method runs locally on the proxy and delegates to `create(lease)`. | No (graceful fallback). |
| **New proxy, new client, old service** | The proxy dispatches the call remotely; the old service would receive an unknown method. **Wire compatibility note**: in JGDMS, the remote method is identified by its full signature; if the service does not export the new overload, the invocation throws `NoSuchMethodError` / `RemoteException`. The default-method fallback does not apply here — it only runs locally when the proxy itself does not override the default. **Mitigation**: deploy the new service first; clients fall back to `create(lease)` until the service is upgraded. |

`TransactionConfig` is deployed in the platform JAR as an `@AtomicSerial`
type in `net.jini.core.transaction.server`.  It is never serialised to an
old server (the old server never receives a call with it as a parameter);
it is only serialised when the new proxy dispatches to a new server.

#### Security analysis

A malicious (or buggy) client could declare `readOnly=true` at `create()`
time but later join a writing participant.

**SettleTransactionPermission** (checked in `TxnManagerImpl.checkAllParticipantsPermission()`
at both `commit()` and `abort()` time) governs who may settle the
transaction.  This check is unaffected by the `readOnly` hint.

**The `readOnly` flag cannot bypass security checks** because:

1. The flag only controls whether Mahalo *skips work*.  It never grants any
   additional permission to any participant.
2. The decision to skip the commit phase must only be made **after** Mahalo
   has verified that every participant voted `NOTCHANGED`.  The correct
   implementation sequence is:

   ```
   run PrepareJob
   if (all NOTCHANGED) {
       // Skip CommitRecord and commit phase — participants hold no data
       // that requires rollback or commit RPC.
       modifyTxnState(COMMITTED);
       return;
   }
   // Fall back to full 2PC — ignore the readOnly hint entirely.
   write CommitRecord;
   run CommitJob;
   ```

   If the `CommitRecord` write were skipped **before** gathering all votes,
   a crash at that moment would leave a committed transaction with no durable
   record — a correctness violation.  The implementation must never do this.

3. **Attack scenario**: malicious client → `create(lease, readOnly=true)` →
   joins a `PREPARED` participant → calls `commit()`.  Mahalo receives at
   least one `PREPARED` vote, falls back to full 2PC, writes `CommitRecord`,
   runs `CommitJob`.  The hint is silently ignored.  **No security bypass.**

4. **Privilege escalation analysis**: there is none.  The `readOnly` hint
   reduces Mahalo's work for genuinely read-only transactions; it cannot
   increase the capabilities of any participant or bypass any access-control
   check.  Worst case for a false hint is wasted CPU from running
   `PrepareJob` before falling back to 2PC — identical overhead to a
   normal transaction.

---

## Summary

| # | Change | API surface | Risk | Benefit |
|---|--------|-------------|------|---------|
| 1 | Filter NOTCHANGED in createTasks() | None | Low | Removes wasted thread slots |
| 2 | Pipeline log write with prepare | None | Low | Overlaps disk + network I/O |
| 3 | ~~`prepareWithTimestamp()` single-round commit~~ | — | — | **WITHDRAWN** (not viable): multi-participant atomicity hazard + per-transaction clock ordered nothing. Implementation removed. |
| 4 | Read-only hint at `create()` as default method | Default method on `TransactionManager` | Low–Medium | Zero disk I/O + zero 2PC for read-only; binary-compatible via fallback to `create(lease)` |
| 5 | Peer-to-peer independent commit (proposal) | New `PeerTransactionParticipant` + `PeerSet`/`PeerVote` + `TransactionConfig.independent` | High | Correct one-round independent commit; manager keeps assembly + recovery; trust-mesh is the hard part. Not implemented. |
