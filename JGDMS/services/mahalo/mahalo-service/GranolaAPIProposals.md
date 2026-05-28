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

## Optimisation 3 — Timestamp-Ordered Single-Round Commit
### (Default method on `TransactionParticipant`)

### Problem

Standard 2PC costs two network round-trips (prepare + commit) per transaction.
The Granola *independent* protocol reduces this to **one round** for read-heavy
or read-only workloads by having each participant timestamp its local decision.
The coordinator only needs to collect timestamps; it never needs to issue an
explicit commit message.

### Proposed API Change

Add `prepareWithTimestamp()` as a **default method directly on
`TransactionParticipant`** (rather than requiring a new sub-interface):

```java
package net.jini.core.transaction.server;

public interface TransactionParticipant extends Remote {

    // --- existing methods unchanged ---

    /**
     * Value object returned by {@link #prepareWithTimestamp}.
     */
    final class TimestampedVote implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        /** One of {@code TransactionConstants.PREPARED}, {@code NOTCHANGED},
         *  or {@code ABORTED}. */
        public final int vote;
        /**
         * Lamport timestamp proposed by this participant, or {@code 0} if
         * this participant does not support timestamp-ordered commit (i.e.
         * the default fallback was used).
         */
        public final long timestamp;

        public TimestampedVote(int vote, long timestamp) {
            this.vote = vote;
            this.timestamp = timestamp;
        }
    }

    /**
     * Prepare and, if local decision is PREPARED or NOTCHANGED, tentatively
     * commit with a Lamport timestamp.  The transaction manager collects the
     * maximum timestamp across all participants; if every participant returns
     * a non-zero timestamp and voted {@code PREPARED} or {@code NOTCHANGED},
     * the second commit round is skipped entirely.
     *
     * <p><b>Default behaviour (compatibility fallback):</b> calls
     * {@link #prepare(TransactionManager, long)} and returns a
     * {@link TimestampedVote} with {@code timestamp == 0}, signalling to the
     * coordinator that this participant does not support the optimised path
     * and that standard 2PC must be used.  The default method body executes
     * <em>locally on the proxy</em> — no additional remote call beyond the
     * {@code prepare()} dispatch is made.
     *
     * @param mgr          the transaction manager
     * @param id           the transaction id
     * @param txnTimestamp the coordinator's current logical clock value
     * @return a {@link TimestampedVote} carrying the vote and a proposed
     *         commit timestamp (0 if not supported)
     * @throws UnknownTransactionException if the transaction is unknown
     * @throws RemoteException if a communication error occurs
     * @since JGDMS 3.1 (proposed)
     */
    default TimestampedVote prepareWithTimestamp(
            TransactionManager mgr, long id, long txnTimestamp)
            throws UnknownTransactionException, RemoteException {
        return new TimestampedVote(prepare(mgr, id), 0L);
    }
}
```

The `timestamp == 0` sentinel tells Mahalo that this participant ran the
compatibility path.  Because `prepare()` is itself a Remote method, it is
still dispatched remotely; the default method only avoids a *second* RPC for
the commit round.

### Integration with Mahalo

1. **`PrepareJob.doWork()`** — always call `prepareWithTimestamp()` instead of
   `prepare()`.  No `instanceof` check needed.  Record the returned timestamp
   in `ParticipantHandle` (new `commitTimestamp` field).

2. **`TxnManagerTransaction.commit()`** — after collecting prepare votes, if
   **every** participant returned `timestamp > 0` and voted `PREPARED`/
   `NOTCHANGED`, skip the second round: mark the transaction `COMMITTED` at
   `max(all timestamps) + 1`.  If any participant returned `timestamp == 0`,
   fall back to standard `CommitJob`.

3. **`CommitRecord`** — add an optional `commitTimestamp` field so that
   recovery can reconstruct the timestamp-ordered decision.

### Pros

- **One fewer network round-trip** for the common case where all participants
  support the optimised path and vote `PREPARED`/`NOTCHANGED`.
- **No `instanceof` check required** — Mahalo always calls
  `prepareWithTimestamp()`; participants that don't override it signal
  fallback via `timestamp == 0`.
- **No mixed-cohort penalty beyond the fallback itself** — Mahalo detects at
  the earliest possible moment (during prepare) that 2PC is needed; no wasted
  second-round RPCs to timestamp-aware participants.
- **Binary-compatible with existing proxy/stub classes** — proxies compiled
  against the old interface run the default locally, emitting a regular
  `prepare()` call with no behaviour change for the remote server.
- Existing `TransactionParticipant` implementations need no changes to
  continue working correctly.

### Cons

- **Requires Lamport clock maintenance** in each participant that opts in.
  Correct implementation (monotonic advances, persistence across crashes) is
  non-trivial.
- **Mixed cohort** still requires two rounds; no optimization is gained when
  even one participant returns `timestamp == 0`.
- `TimestampedVote` is a new serialisable type; upgrading the participant
  interface requires coordinating class availability on the wire.
- **Requires deeper investigation** into:
  - Whether `ParticipantHandle` serialisation/persistence strategy needs
    updating for the new `commitTimestamp` field.
  - Recovery semantics: if the manager crashes after receiving timestamps but
    before writing `CommitRecord`, can it reconstruct the commit timestamp?
  - Whether `timestamp == 0` is an adequate sentinel or a reserved constant
    should be defined (e.g. `TimestampedVote.NO_TIMESTAMP`).

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
 */
final class TransactionConfig implements java.io.Serializable {
    /** True if the caller guarantees no participant will modify state. */
    public boolean readOnly = false;
    /** Requested isolation level (reserved for future use). */
    public int isolationLevel = SERIALIZABLE;
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
- **Requires deeper investigation** into:
  - Interaction with `NestableTransactionManager` (nested transactions may
    upgrade read-only to read-write).
  - Whether `TransactionConfig` (Option C) should be `AtomicSerial`-annotated
    for JGDMS serialisation safety, and what the migration story is.
  - Security: a malicious client could declare read-only but register a
    writing participant; the manager's fallback logic must handle this without
    privilege escalation.

---

## Summary

| # | Change | API surface | Risk | Benefit |
|---|--------|-------------|------|---------|
| 1 | Filter NOTCHANGED in createTasks() | None | Low | Removes wasted thread slots |
| 2 | Pipeline log write with prepare | None | Low | Overlaps disk + network I/O |
| 3 | `prepareWithTimestamp()` default on `TransactionParticipant` | Default method + new VO | Low–Medium | Eliminates 2nd round; binary-compatible via fallback to `prepare()` |
| 4 | Read-only hint at `create()` as default method | Default method on `TransactionManager` | Low–Medium | Zero disk I/O + zero 2PC for read-only; binary-compatible via fallback to `create(lease)` |
