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

The two remaining Granola-inspired optimisations require new API surface:

---

## Optimisation 3 — Timestamp-Ordered Single-Round Commit
### (`TimestampAwareParticipant` new sub-interface)

### Problem

Standard 2PC costs two network round-trips (prepare + commit) per transaction.
The Granola *independent* protocol reduces this to **one round** for read-heavy
or read-only workloads by having each participant timestamp its local decision.
The coordinator only needs to collect timestamps; it never needs to issue an
explicit commit message.

### Proposed API Change

Add a new optional sub-interface:

```java
package net.jini.core.transaction.server;

/**
 * Extended participant interface for servers that support
 * Granola-style timestamp-ordered single-round commit.
 *
 * A {@code TimestampAwareParticipant} can decide its local prepare/commit
 * outcome in a single RPC by also returning a Lamport timestamp.  The
 * transaction manager collects the maximum timestamp across all participants;
 * if every participant voted {@code PREPARED} or {@code NOTCHANGED} the
 * transaction is committed at the collected timestamp without a second round.
 *
 * @since JGDMS 3.1 (proposed)
 */
public interface TimestampAwareParticipant extends TransactionParticipant {

    /**
     * Prepare and, if local decision is PREPARED or NOTCHANGED, tentatively
     * commit.  Returns both the vote and a Lamport timestamp representing the
     * earliest time at which this participant's changes may be considered
     * committed.
     *
     * @param mgr        the transaction manager
     * @param id         the transaction id
     * @param txnTimestamp the coordinator's current logical clock value;
     *                   the participant must advance its own clock to at
     *                   least {@code max(local, txnTimestamp) + 1}
     * @return a {@link TimestampedVote} carrying the vote and the
     *         participant's proposed commit timestamp
     * @throws UnknownTransactionException if the transaction is unknown
     * @throws RemoteException if a communication error occurs
     */
    TimestampedVote prepareWithTimestamp(
            TransactionManager mgr, long id, long txnTimestamp)
            throws UnknownTransactionException, RemoteException;

    /**
     * Value object returned by {@link #prepareWithTimestamp}.
     */
    final class TimestampedVote implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        /** One of {@code TransactionConstants.PREPARED}, {@code NOTCHANGED},
         *  or {@code ABORTED}. */
        public final int vote;
        /** Lamport timestamp proposed by this participant. */
        public final long timestamp;

        public TimestampedVote(int vote, long timestamp) {
            this.vote = vote;
            this.timestamp = timestamp;
        }
    }
}
```

### Integration with Mahalo

1. **`PrepareJob.doWork()`** — when the handle's participant implements
   `TimestampAwareParticipant`, call `prepareWithTimestamp()` instead of
   `prepare()`.  Record the returned timestamp in `ParticipantHandle` (new
   `commitTimestamp` field).

2. **`TxnManagerTransaction.commit()`** — after collecting prepare votes, if
   every participant used the timestamp path and all voted `PREPARED`/
   `NOTCHANGED`, skip the second round entirely: mark the transaction
   `COMMITTED` using `max(all timestamps) + 1` as the commit time.

3. **`CommitRecord`** — add an optional `commitTimestamp` field so that
   recovery can reconstruct the timestamp-ordered decision.

### Pros

- **One fewer network round-trip** for the common case where all participants
  are `TimestampAwareParticipant` and vote `PREPARED`/`NOTCHANGED`.
- Backward-compatible: existing `TransactionParticipant` implementations are
  unaffected; Mahalo falls back to standard 2PC automatically.
- Clean sub-interface: clients can query via `instanceof` without code
  breakage.

### Cons

- **Requires Lamport clock maintenance** in each participant service.  Correct
  implementation (monotonic advances, persistence across crashes) is
  non-trivial.
- **Mixed cohort** (some timestamp-aware, some not) still requires two rounds,
  offering no benefit while adding `instanceof` overhead.
- **Participant API churn**: existing participant implementations need to
  opt-in by implementing the new interface.
- `TimestampedVote` is a new serialisable type on the wire; clients and servers
  must be upgraded together unless a versioning scheme is added.
- **Requires deeper investigation** into:
  - Whether `ParticipantHandle` serialisation/persistence strategy needs
    updating for the new `commitTimestamp` field.
  - Recovery semantics: if the manager crashes after receiving timestamps but
    before writing `CommitRecord`, can it reconstruct the commit timestamp?
  - Interoperability with activatable participants (the activation framework
    may complicate the `instanceof` check on the deserialized stub).

---

## Optimisation 4 — Read-Only Transaction Hint at `create()`
### (New `createReadOnly()` method on `TransactionManager`)

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

**Option A — New method on `TransactionManager`:**

```java
/**
 * Begin a new top-level read-only transaction.  The transaction manager
 * may apply optimisations appropriate for transactions that will never
 * modify shared state (e.g., skipping durable-log writes and the 2PC
 * commit round).
 *
 * <p>If any participant calls {@code prepare()} with a non-{@code NOTCHANGED}
 * vote, the transaction manager must fall back to the full 2PC protocol
 * transparently.
 *
 * @param lease the requested lease duration
 * @param readOnly hint that this transaction will not modify shared state
 * @return transaction id and lease
 * @throws LeaseDeniedException if the manager will not grant the lease
 * @throws RemoteException if a communication error occurs
 * @since JGDMS 3.1 (proposed)
 */
Created create(long lease, boolean readOnly)
        throws LeaseDeniedException, RemoteException;
```

**Option B — Separate method:**

```java
Created createReadOnly(long lease)
        throws LeaseDeniedException, RemoteException;
```

**Option C — `TransactionConfig` parameter object** (most extensible):

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

Created create(long lease, TransactionConfig config)
        throws LeaseDeniedException, RemoteException;
```

### Integration with Mahalo

1. **`TxnManagerTransaction`** — add a `boolean readOnly` flag set at
   construction.  In `commit()`, if `readOnly && allNotChanged()`:
   - Skip `CommitRecord` write and log write entirely.
   - Transition directly to `COMMITTED`.
2. **`TxnManagerImpl.create()`** — pass the hint through to
   `TxnManagerTransaction`.
3. **`TxnManagerImplInitializer`** — may need a new `create` overload for
   the proxy.

### Pros

- **Zero disk I/O** for confirmed read-only transactions: no `CommitRecord`,
  no `invalidate()`.
- **Zero network overhead** for the commit phase when every participant voted
  `NOTCHANGED`: `CommitJob` is never created.
- Hint is advisory: if any participant votes `PREPARED`, the manager silently
  falls back to full 2PC — no change in correctness guarantees.
- Option C (`TransactionConfig`) offers the most forward-compatible surface.

### Cons

- **`TransactionManager` is a public Remote interface**: adding any method is
  a **binary-incompatible** change for all existing proxy and service
  implementations.  Every stub, skeleton and service that implements the
  interface must be recompiled.
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
  - Whether the existing proxy (`TxnMgrProxy`) must be recompiled and
    redeployed for all existing installations.
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
| 3 | `TimestampAwareParticipant` sub-interface | New interface + VO | Medium | Eliminates 2nd round for timestamp-aware participants |
| 4 | Read-only hint at `create()` | `TransactionManager` method | High | Zero disk I/O and zero 2PC for read-only transactions |
