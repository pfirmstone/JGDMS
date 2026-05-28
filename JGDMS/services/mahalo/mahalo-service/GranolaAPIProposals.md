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
     *
     * <p>Uses {@code @AtomicSerial} (JGDMS convention for all wire-serialisable
     * types) so that field invariants are validated atomically before the
     * object instance is created during deserialisation.
     */
    @AtomicSerial
    final class TimestampedVote implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        /** One of {@code TransactionConstants.PREPARED}, {@code NOTCHANGED},
         *  or {@code ABORTED}. */
        public final int vote;
        /**
         * Lamport timestamp proposed by this participant, or
         * {@link LamportClock#NO_TIMESTAMP} ({@code 0L}) if this participant
         * does not support timestamp-ordered commit (i.e. the default fallback
         * was used or the transaction was aborted).
         */
        public final long timestamp;

        /** Normal constructor. */
        public TimestampedVote(int vote, long timestamp) {
            this.vote = vote;
            this.timestamp = timestamp;
        }

        /** {@code @AtomicSerial} deserialisation constructor. */
        public TimestampedVote(GetArg args) throws IOException, ClassNotFoundException {
            this(args.get("vote", 0),
                 args.get("timestamp", 0L));
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
     * {@link TimestampedVote} with {@code timestamp == LamportClock.NO_TIMESTAMP},
     * signalling to the coordinator that this participant does not support the
     * optimised path and that standard 2PC must be used.  The default method
     * body executes <em>locally on the proxy</em> — no additional remote call
     * beyond the {@code prepare()} dispatch is made.
     *
     * @param mgr          the transaction manager
     * @param id           the transaction id
     * @param txnTimestamp the coordinator's current logical clock value
     * @return a {@link TimestampedVote} carrying the vote and a proposed
     *         commit timestamp ({@code LamportClock.NO_TIMESTAMP} if not supported)
     * @throws UnknownTransactionException if the transaction is unknown
     * @throws RemoteException if a communication error occurs
     * @since JGDMS 3.1 (proposed)
     */
    default TimestampedVote prepareWithTimestamp(
            TransactionManager mgr, long id, long txnTimestamp)
            throws UnknownTransactionException, RemoteException {
        return new TimestampedVote(prepare(mgr, id), LamportClock.NO_TIMESTAMP);
    }
}
```

The `timestamp == 0` sentinel tells Mahalo that this participant ran the
compatibility path.  Because `prepare()` is itself a Remote method, it is
still dispatched remotely; the default method only avoids a *second* RPC for
the commit round.

### Utility Classes to Reduce Participant Complexity

The primary complexity burden for a participant opting in to Opt-3 is correct
Lamport clock maintenance: thread-safe atomic updates, the
`max(local, received) + 1` rule, and persistence across crashes.  Two utility
classes eliminate this burden entirely.

#### `LamportClock` — thread-safe, `@AtomicSerial`, embeddable

```java
package net.jini.core.transaction.server;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * A thread-safe Lamport logical clock.
 *
 * <p>All public methods are lock-free (based on {@link AtomicLong}).
 * Being {@code @AtomicSerial}, an instance can be embedded in a service
 * snapshot and round-tripped through JGDMS serialisation to survive crashes
 * and restarts with a monotonically correct value.
 *
 * @since JGDMS 3.1 (proposed)
 */
@AtomicSerial
public final class LamportClock implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Sentinel constant — a {@link TimestampedVote} carrying this value
     * signals that the participant did not advance a clock (compatibility
     * fallback or abort path).
     */
    public static final long NO_TIMESTAMP = 0L;

    /** @serial current logical time */
    private final long value;               // used only during (de)serialisation
    private final transient AtomicLong clock;

    /** Create a new clock starting at logical time 0. */
    public LamportClock() {
        this(0L);
    }

    /**
     * Create a clock with a given initial value.
     * Use when restoring a previously persisted clock after a crash.
     *
     * @param initialValue the last persisted clock value
     */
    public LamportClock(long initialValue) {
        this.value = initialValue;
        this.clock = new AtomicLong(initialValue);
    }

    /** {@code @AtomicSerial} deserialisation constructor. */
    public LamportClock(GetArg args) throws IOException, ClassNotFoundException {
        this(args.get("value", 0L));
    }

    /**
     * Observes a remote timestamp, advances the local clock to
     * {@code max(local, remote) + 1}, and returns the new local time.
     * This is the standard Lamport receive-event rule.
     *
     * @param remoteTimestamp timestamp received from the coordinator
     * @return the new local clock value (always {@code > remoteTimestamp})
     */
    public long observe(long remoteTimestamp) {
        return clock.updateAndGet(local -> Math.max(local, remoteTimestamp) + 1);
    }

    /**
     * Increments the clock by 1 and returns the new value.
     * Use for local send events when no remote timestamp is available.
     */
    public long tick() {
        return clock.incrementAndGet();
    }

    /** Returns the current clock value without advancing it. */
    public long get() {
        return clock.get();
    }
}
```

#### `AbstractTimestampParticipant` — zero-effort clock integration

```java
package net.jini.core.transaction.server;

import java.rmi.RemoteException;
import net.jini.core.transaction.UnknownTransactionException;

/**
 * Convenience base class for {@link TransactionParticipant} implementations
 * that want to support Granola-style single-round commit (Opt-3) without
 * writing any clock-management code.
 *
 * <p>Subclasses continue to implement {@link #prepare}, {@link #commit},
 * {@link #abort}, and {@link #prepareAndCommit} as normal.  This class
 * provides a concrete {@link #prepareWithTimestamp} that:
 * <ol>
 *   <li>Calls the subclass {@code prepare()} (which is the usual Remote
 *       call path).</li>
 *   <li>If the vote is {@code PREPARED} or {@code NOTCHANGED}, advances the
 *       internal {@link LamportClock} via {@code clock.observe(txnTimestamp)}
 *       and returns a {@link TimestampedVote} with the updated timestamp
 *       (non-zero, signalling to Mahalo that the single-round path is
 *       available).</li>
 *   <li>If the vote is {@code ABORTED}, returns
 *       {@code new TimestampedVote(ABORTED, LamportClock.NO_TIMESTAMP)} — the
 *       abort path does not require a commit timestamp and Mahalo will
 *       not attempt the single-round optimisation.</li>
 * </ol>
 *
 * <h2>Crash recovery</h2>
 * <p>Services that persist their state (e.g. via a snapshot log) should
 * include the {@link LamportClock} returned by {@link #getLamportClock()} in
 * their snapshot and restore it via the
 * {@link #AbstractTimestampParticipant(LamportClock)} constructor.  This
 * guarantees that timestamps remain monotonically increasing across restarts.
 *
 * <p>For <em>transient</em> services (no persistence), construct with
 * {@code new LamportClock(System.currentTimeMillis())} to seed the clock from
 * wall time.  As long as wall time advances between restarts this avoids
 * re-issuing previously seen timestamps.
 *
 * @since JGDMS 3.1 (proposed)
 */
public abstract class AbstractTimestampParticipant
        implements TransactionParticipant {

    private final LamportClock clock;

    /** Create with a fresh clock starting at 0. */
    protected AbstractTimestampParticipant() {
        this(new LamportClock());
    }

    /**
     * Create with a previously persisted clock.
     *
     * @param clock the clock restored from a snapshot
     */
    protected AbstractTimestampParticipant(LamportClock clock) {
        if (clock == null) throw new NullPointerException("clock");
        this.clock = clock;
    }

    /**
     * Returns the Lamport clock managed by this participant.
     * Persist this value in the service snapshot and pass it back
     * to the {@link #AbstractTimestampParticipant(LamportClock)}
     * constructor on recovery.
     */
    protected final LamportClock getLamportClock() {
        return clock;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation calls {@link #prepare(TransactionManager, long)},
     * advances the Lamport clock, and wraps the result.  Subclasses must
     * NOT override this method; override {@code prepare()} instead.
     */
    @Override
    public final TransactionParticipant.TimestampedVote prepareWithTimestamp(
            TransactionManager mgr, long id, long txnTimestamp)
            throws UnknownTransactionException, RemoteException {
        int vote = prepare(mgr, id);
        if (vote == ABORTED) {
            return new TransactionParticipant.TimestampedVote(
                    ABORTED, LamportClock.NO_TIMESTAMP);
        }
        long ts = clock.observe(txnTimestamp);
        return new TransactionParticipant.TimestampedVote(vote, ts);
    }
}
```

**Participant adoption recipe** — existing services need only three steps:

1. Change `implements TransactionParticipant` →
   `extends AbstractTimestampParticipant`.
2. If the service has a persistence snapshot, add one line:
   `snapshot.set("lamportClock", getLamportClock());`
   and restore with
   `super(snapshot.get("lamportClock", LamportClock.class));`.
3. No changes to `prepare()`, `commit()`, `abort()`, or `prepareAndCommit()`.

### Integration with Mahalo

1. **`PrepareJob.doWork()`** — always call `prepareWithTimestamp()` instead of
   `prepare()`.  No `instanceof` check needed.  Record the returned timestamp
   in `ParticipantHandle` (new `commitTimestamp` field).

2. **`TxnManagerTransaction.commit()`** — after collecting prepare votes, if
   **every** participant returned `timestamp > 0` and voted `PREPARED`/
   `NOTCHANGED`, skip the second round: mark the transaction `COMMITTED` at
   `max(all timestamps) + 1`.  If any participant returned `timestamp == 0`
   (`LamportClock.NO_TIMESTAMP`), fall back to standard `CommitJob`.

3. **`CommitRecord`** — add an optional `commitTimestamp` field so that
   recovery can reconstruct the timestamp-ordered decision.

### Pros

- **One fewer network round-trip** for the common case where all participants
  support the optimised path and vote `PREPARED`/`NOTCHANGED`.
- **No `instanceof` check required** — Mahalo always calls
  `prepareWithTimestamp()`; participants that don't override it signal
  fallback via `timestamp == 0` (`NO_TIMESTAMP`).
- **No mixed-cohort penalty beyond the fallback itself** — Mahalo detects at
  the earliest possible moment (during prepare) that 2PC is needed; no wasted
  second-round RPCs to timestamp-aware participants.
- **Binary-compatible with existing proxy/stub classes** — proxies compiled
  against the old interface run the default locally, emitting a regular
  `prepare()` call with no behaviour change for the remote server.
- Existing `TransactionParticipant` implementations need no changes to
  continue working correctly.
- **`AbstractTimestampParticipant` + `LamportClock` reduce opt-in to
  three lines of change in an existing service**: subclass swap, one
  snapshot save, one snapshot restore.  All clock logic is encapsulated.

### Cons

- **Mixed cohort** still requires two rounds; no optimization is gained when
  even one participant returns `timestamp == 0`.
- `TimestampedVote` and `LamportClock` are new `@AtomicSerial` wire types;
  upgrading the participant interface requires coordinating class availability
  on both sides of the wire.
- **Requires deeper investigation** into:
  - Whether `ParticipantHandle` serialisation/persistence strategy needs
    updating for the new `commitTimestamp` field.
  - Recovery semantics: if the manager crashes after receiving timestamps but
    before writing `CommitRecord`, can it reconstruct the commit timestamp?
    (Possible approach: Mahalo conservatively falls back to 2PC on recovery,
    which is already safe since all participants have already run `prepare()`.)

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
- **Requires deeper investigation** into:
  - Interaction with `NestableTransactionManager` (nested transactions may
    upgrade read-only to read-write).
  - `TransactionConfig` (Option C) uses `@AtomicSerial` as required by
    JGDMS convention for all wire-serialisable types.  The migration story
    (how existing callers adopt the new type) needs to be defined.
  - Security: a malicious client could declare read-only but register a
    writing participant; the manager's fallback logic must handle this without
    privilege escalation.

---

## Summary

| # | Change | API surface | Risk | Benefit |
|---|--------|-------------|------|---------|
| 1 | Filter NOTCHANGED in createTasks() | None | Low | Removes wasted thread slots |
| 2 | Pipeline log write with prepare | None | Low | Overlaps disk + network I/O |
| 3 | `prepareWithTimestamp()` default on `TransactionParticipant` + `LamportClock` + `AbstractTimestampParticipant` utilities | Default method + new VO + 2 utility classes | Low–Medium | Eliminates 2nd round; binary-compatible via fallback; participants opt in with 3-line change |
| 4 | Read-only hint at `create()` as default method | Default method on `TransactionManager` | Low–Medium | Zero disk I/O + zero 2PC for read-only; binary-compatible via fallback to `create(lease)` |
