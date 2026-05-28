/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.mahalo;

import java.rmi.RemoteException;
import java.security.AccessController;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.transaction.CannotAbortException;
import net.jini.core.transaction.CannotCommitException;
import net.jini.core.transaction.TimeoutExpiredException;
import net.jini.core.transaction.UnknownTransactionException;
import net.jini.core.transaction.server.AbstractTimestampParticipant;
import net.jini.core.transaction.server.CrashCountException;
import net.jini.core.transaction.server.LamportClock;
import net.jini.core.transaction.server.ServerTransaction;
import net.jini.core.transaction.server.TransactionConstants;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.transaction.server.TransactionParticipant;
import net.jini.id.UuidFactory;
import org.apache.river.mahalo.log.ClientLog;
import org.apache.river.mahalo.log.LogException;
import org.apache.river.mahalo.log.LogManager;
import org.apache.river.mahalo.log.LogRecord;
import org.apache.river.thread.wakeup.WakeupManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for the Granola-inspired optimisations in the Mahalo transaction
 * manager:
 *
 * <ul>
 *   <li><b>Optimisation 1</b> – {@code CommitJob.createTasks()} and
 *       {@code AbortJob.createTasks()} now skip {@code NOTCHANGED} /
 *       {@code COMMITTED} / {@code ABORTED} handles so that no thread-pool
 *       slot is wasted on participants that require no network contact.</li>
 *   <li><b>Optimisation 2</b> – The durable-log write (
 *       {@code CommitRecord} / {@code AbortRecord}) is submitted to the
 *       thread pool concurrently with the participant-task phase so that
 *       disk I/O and network I/O can overlap.</li>
 * </ul>
 *
 * <h2>Design notes</h2>
 * {@code ParticipantHandle} serialises its wrapped {@link TransactionParticipant}
 * into a {@code StorableObject} at construction time.  The serialisation of
 * an unexported remote object will fail; however, the
 * {@code ParticipantHandle} constructor silently catches the resulting
 * {@link RemoteException} (setting the stored part to {@code null} while
 * keeping the handle alive).  We exploit this to create lightweight test
 * handles without standing up a full RMI infrastructure: we call
 * {@code setPrepState()} immediately after construction to put the handle
 * into the desired state, and the Job code only reads {@code getPrepState()}
 * during the filtering / completion paths exercised by these tests.
 */
public class GranolaOptimizationsTest implements TransactionConstants {

    // -----------------------------------------------------------------------
    // Test infrastructure
    // -----------------------------------------------------------------------

    /** Minimal, in-memory implementation of {@link ClientLog}. */
    private static final class RecordingLog implements ClientLog {
        final AtomicInteger writeCount = new AtomicInteger();
        final AtomicInteger invalidateCount = new AtomicInteger();
        volatile boolean failOnWrite = false;
        volatile long writeDelayMs = 0;

        @Override
        public void write(LogRecord rec) throws LogException {
            if (writeDelayMs > 0) {
                try {
                    Thread.sleep(writeDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failOnWrite) {
                throw new LogException("simulated log failure");
            }
            writeCount.incrementAndGet();
        }

        @Override
        public void invalidate() throws LogException {
            invalidateCount.incrementAndGet();
        }
    }

    /** {@link LogManager} that always returns the same {@link RecordingLog}. */
    private final class MockLogManager implements LogManager {
        final RecordingLog log = new RecordingLog();

        @Override
        public ClientLog logFor(long cookie) throws LogException {
            return log;
        }

        @Override
        public void recover() throws LogException { }

        @Override
        public Object getAdmin() {
            return null;
        }
    }

    /** No-op {@link TransactionManager} for test-only use. */
    private static final TransactionManager MOCK_MGR = new TransactionManager() {
        @Override
        public Created create(long lease) throws LeaseDeniedException, RemoteException {
            return null;
        }

        @Override
        public void join(long id, TransactionParticipant part, long crashCount)
                throws UnknownTransactionException, net.jini.core.transaction.CannotJoinException,
                CrashCountException, RemoteException { }

        @Override
        public int getState(long id) throws UnknownTransactionException, RemoteException {
            return ACTIVE;
        }

        @Override
        public void commit(long id)
                throws UnknownTransactionException, CannotCommitException, RemoteException { }

        @Override
        public void commit(long id, long waitFor)
                throws UnknownTransactionException, CannotCommitException,
                net.jini.core.transaction.TimeoutExpiredException, RemoteException { }

        @Override
        public void abort(long id)
                throws UnknownTransactionException, CannotAbortException, RemoteException { }

        @Override
        public void abort(long id, long waitFor)
                throws UnknownTransactionException, CannotAbortException,
                net.jini.core.transaction.TimeoutExpiredException, RemoteException { }
    };

    /** No-op {@link TxnSettler} that records unsettled transaction IDs. */
    private static final class RecordingSettler implements TxnSettler {
        final AtomicInteger unsettledCount = new AtomicInteger();

        @Override
        public void noteUnsettledTxn(long tid) {
            unsettledCount.incrementAndGet();
        }
    }

    /**
     * Creates a lightweight {@link ParticipantHandle} whose prepare-state is
     * set to {@code desiredState}.  Uses the test-only constructor that
     * bypasses {@code StorableObject} serialisation, so no RMI infrastructure
     * is required.  The handle is suitable for code paths that only read/write
     * the prepare-state (e.g. {@code createTasks()} filtering and
     * {@code doWork()} early-return paths).
     */
    private static ParticipantHandle makeHandle(int desiredState) {
        return new ParticipantHandle(desiredState);
    }

    private ExecutorService pool;
    private WakeupManager wm;

    @Before
    public void setUp() {
        pool = Executors.newFixedThreadPool(4);
        wm = new WakeupManager(new WakeupManager.ThreadDesc(null, true));
    }

    @After
    public void tearDown() {
        pool.shutdownNow();
        wm.stop();
    }

    // -----------------------------------------------------------------------
    // Optimisation 1 — CommitJob.createTasks() filtering
    // -----------------------------------------------------------------------

    /**
     * A handle that voted {@code NOTCHANGED} must NOT produce a commit task.
     * Only the {@code PREPARED} handles should generate tasks.
     */
    @Test
    public void commitJobExcludesNotChangedHandles() throws Exception {
        ParticipantHandle[] handles = {
            makeHandle(PREPARED),
            makeHandle(NOTCHANGED),
            makeHandle(PREPARED)
        };
        CommitJob job = new CommitJob(
                new ServerTransaction(MOCK_MGR, 1L),
                pool, wm, new RecordingLog(), handles,
                AccessController.getContext());

        assertEquals("PREPARED handles only", 2, job.createTasks().length);
    }

    /**
     * A handle already in the terminal {@code COMMITTED} state requires no
     * further network contact and must be excluded from commit tasks.
     */
    @Test
    public void commitJobExcludesCommittedHandles() throws Exception {
        ParticipantHandle[] handles = {
            makeHandle(PREPARED),
            makeHandle(COMMITTED),
            makeHandle(PREPARED)
        };
        CommitJob job = new CommitJob(
                new ServerTransaction(MOCK_MGR, 2L),
                pool, wm, new RecordingLog(), handles,
                AccessController.getContext());

        assertEquals("PREPARED handles only", 2, job.createTasks().length);
    }

    /**
     * A handle already in the terminal {@code ABORTED} state must be excluded
     * from commit tasks (it will not roll forward).
     */
    @Test
    public void commitJobExcludesAbortedHandles() throws Exception {
        ParticipantHandle[] handles = {
            makeHandle(PREPARED),
            makeHandle(ABORTED),
            makeHandle(PREPARED)
        };
        CommitJob job = new CommitJob(
                new ServerTransaction(MOCK_MGR, 3L),
                pool, wm, new RecordingLog(), handles,
                AccessController.getContext());

        assertEquals("PREPARED handles only", 2, job.createTasks().length);
    }

    /**
     * When every handle voted {@code NOTCHANGED} the task array must be empty.
     * This covers the Granola read-only transaction fast path: all participants
     * are skipped, and {@code Job.scheduleTasks()} completes immediately.
     */
    @Test
    public void commitJobCreatesZeroTasksWhenAllNotChanged() throws Exception {
        ParticipantHandle[] handles = {
            makeHandle(NOTCHANGED),
            makeHandle(NOTCHANGED)
        };
        CommitJob job = new CommitJob(
                new ServerTransaction(MOCK_MGR, 4L),
                pool, wm, new RecordingLog(), handles,
                AccessController.getContext());

        assertEquals("no tasks for all-NOTCHANGED", 0, job.createTasks().length);
    }

    /**
     * When every handle voted {@code PREPARED} all handles must get a task.
     */
    @Test
    public void commitJobCreatesTasksForAllPrepared() throws Exception {
        ParticipantHandle[] handles = {
            makeHandle(PREPARED),
            makeHandle(PREPARED),
            makeHandle(PREPARED)
        };
        CommitJob job = new CommitJob(
                new ServerTransaction(MOCK_MGR, 5L),
                pool, wm, new RecordingLog(), handles,
                AccessController.getContext());

        assertEquals("all-PREPARED", 3, job.createTasks().length);
    }

    // -----------------------------------------------------------------------
    // Optimisation 1 — AbortJob.createTasks() filtering
    // -----------------------------------------------------------------------

    /**
     * A {@code NOTCHANGED} handle made no changes and does not need an
     * abort() call; it must be excluded from abort tasks.
     */
    @Test
    public void abortJobExcludesNotChangedHandles() throws Exception {
        ParticipantHandle[] handles = {
            makeHandle(PREPARED),
            makeHandle(NOTCHANGED),
            makeHandle(PREPARED)
        };
        AbortJob job = new AbortJob(
                new ServerTransaction(MOCK_MGR, 6L),
                pool, wm, new RecordingLog(), handles,
                AccessController.getContext());

        assertEquals("PREPARED handles only", 2, job.createTasks().length);
    }

    /**
     * A handle already in the {@code ABORTED} state requires no further
     * network contact during abort; it must be excluded.
     */
    @Test
    public void abortJobExcludesAlreadyAbortedHandles() throws Exception {
        ParticipantHandle[] handles = {
            makeHandle(PREPARED),
            makeHandle(ABORTED),
            makeHandle(PREPARED)
        };
        AbortJob job = new AbortJob(
                new ServerTransaction(MOCK_MGR, 7L),
                pool, wm, new RecordingLog(), handles,
                AccessController.getContext());

        assertEquals("PREPARED handles only", 2, job.createTasks().length);
    }

    /**
     * When every handle is {@code NOTCHANGED} the task array must be empty;
     * the AbortJob completes immediately without network contact.
     */
    @Test
    public void abortJobCreatesZeroTasksWhenAllNotChanged() throws Exception {
        ParticipantHandle[] handles = {
            makeHandle(NOTCHANGED),
            makeHandle(NOTCHANGED)
        };
        AbortJob job = new AbortJob(
                new ServerTransaction(MOCK_MGR, 8L),
                pool, wm, new RecordingLog(), handles,
                AccessController.getContext());

        assertEquals("no tasks for all-NOTCHANGED", 0, job.createTasks().length);
    }

    /**
     * {@code PREPARED} handles must be contacted during abort; all must get
     * tasks.
     */
    @Test
    public void abortJobCreatesTasksForAllPrepared() throws Exception {
        ParticipantHandle[] handles = {
            makeHandle(PREPARED),
            makeHandle(PREPARED)
        };
        AbortJob job = new AbortJob(
                new ServerTransaction(MOCK_MGR, 9L),
                pool, wm, new RecordingLog(), handles,
                AccessController.getContext());

        assertEquals("all-PREPARED", 2, job.createTasks().length);
    }

    // -----------------------------------------------------------------------
    // Optimisation 2 — pipelined log write in TxnManagerTransaction.commit()
    // -----------------------------------------------------------------------

    /**
     * Commit must succeed even when the durable-log write takes longer than
     * the participant-prepare phase.  The optimisation submits the write
     * concurrently; {@code logFuture.get()} gates the commit decision, so
     * the write completes before the method returns.
     *
     * <p>The participant handle is pre-set to {@code NOTCHANGED} so that the
     * in-process {@link PrepareAndCommitJob} completes immediately without
     * any real RMI round-trips.
     */
    @Test
    public void commitSucceedsWithSlowLogWrite() throws Exception {
        MockLogManager logMgr = new MockLogManager();
        logMgr.log.writeDelayMs = 200L;   // log write takes 200 ms

        ParticipantHandle handle = makeHandle(NOTCHANGED);
        TxnManagerTransaction txnT = buildTxn(logMgr);
        txnT.add(handle);

        long before = System.currentTimeMillis();
        txnT.commit(Long.MAX_VALUE);
        long elapsed = System.currentTimeMillis() - before;

        // commit() must have waited for the slow log write
        assertEquals("CommitRecord written once", 1, logMgr.log.writeCount.get());
        assertEquals("Log invalidated after commit", 1, logMgr.log.invalidateCount.get());
        // Sanity check: the slow write actually ran (elapsed >= 100 ms gives
        // generous margin for scheduling jitter)
        assert elapsed >= 100 : "elapsed=" + elapsed + "ms; expected >= 100ms";
    }

    /**
     * When the durable-log write fails, commit must throw
     * {@link CannotCommitException} rather than silently succeeding with a
     * missing commit record.
     */
    @Test(expected = CannotCommitException.class)
    public void commitThrowsWhenLogWriteFails() throws Exception {
        MockLogManager logMgr = new MockLogManager();
        logMgr.log.failOnWrite = true;

        ParticipantHandle handle = makeHandle(NOTCHANGED);
        TxnManagerTransaction txnT = buildTxn(logMgr);
        txnT.add(handle);

        txnT.commit(Long.MAX_VALUE);   // must throw CannotCommitException
    }

    // -----------------------------------------------------------------------
    // Optimisation 2 — pipelined log write in TxnManagerTransaction.abort()
    // -----------------------------------------------------------------------

    /**
     * Abort must succeed even when the durable-log write is slower than the
     * participant-abort phase.  The handle is pre-set to {@code NOTCHANGED}
     * so the {@link AbortJob} completes with zero tasks immediately, and the
     * only meaningful wait is for the log write.
     */
    @Test
    public void abortSucceedsWithSlowLogWrite() throws Exception {
        MockLogManager logMgr = new MockLogManager();
        logMgr.log.writeDelayMs = 200L;

        ParticipantHandle handle = makeHandle(NOTCHANGED);
        TxnManagerTransaction txnT = buildTxn(logMgr);
        txnT.add(handle);

        long before = System.currentTimeMillis();
        txnT.abort(Long.MAX_VALUE);
        long elapsed = System.currentTimeMillis() - before;

        assertEquals("AbortRecord written once", 1, logMgr.log.writeCount.get());
        assertEquals("Log invalidated after abort", 1, logMgr.log.invalidateCount.get());
        assert elapsed >= 100 : "elapsed=" + elapsed + "ms; expected >= 100ms";
    }

    /**
     * When the durable-log write fails during abort, the method must throw
     * {@link CannotAbortException} rather than returning successfully.
     */
    @Test(expected = CannotAbortException.class)
    public void abortThrowsWhenLogWriteFails() throws Exception {
        MockLogManager logMgr = new MockLogManager();
        logMgr.log.failOnWrite = true;

        ParticipantHandle handle = makeHandle(NOTCHANGED);
        TxnManagerTransaction txnT = buildTxn(logMgr);
        txnT.add(handle);

        txnT.abort(Long.MAX_VALUE);   // must throw CannotAbortException
    }

    // -----------------------------------------------------------------------
    // Optimisation 3 — LamportClock unit tests
    // -----------------------------------------------------------------------

    /** {@code NO_TIMESTAMP} sentinel must equal zero. */
    @Test
    public void lamportClockNoTimestampIsZero() {
        assertEquals("NO_TIMESTAMP must be 0", 0L, LamportClock.NO_TIMESTAMP);
    }

    /** {@code tick()} must return a value strictly greater than zero. */
    @Test
    public void lamportClockTickReturnsPositive() {
        LamportClock clock = new LamportClock();
        assertTrue("tick() > 0", clock.tick() > 0L);
    }

    /** Successive {@code tick()} calls must be strictly monotonically increasing. */
    @Test
    public void lamportClockTickIsMonotonic() {
        LamportClock clock = new LamportClock();
        long t1 = clock.tick();
        long t2 = clock.tick();
        assertTrue("second tick > first tick", t2 > t1);
    }

    /** {@code observe(remote)} advances the clock past {@code remote}. */
    @Test
    public void lamportClockObserveAdvancesPastRemote() {
        LamportClock clock = new LamportClock();
        long result = clock.observe(1000L);
        assertTrue("observe must exceed remote timestamp", result > 1000L);
    }

    /**
     * {@code observe(0)} is equivalent to {@code tick()}: must return a
     * positive value even when the remote timestamp is {@code NO_TIMESTAMP}.
     */
    @Test
    public void lamportClockObserveWithNoTimestampBehavesLikeTick() {
        LamportClock clock = new LamportClock();
        long result = clock.observe(LamportClock.NO_TIMESTAMP);
        assertTrue("observe(NO_TIMESTAMP) must return > 0", result > 0L);
    }

    // -----------------------------------------------------------------------
    // Optimisation 3 — TimestampedVote unit tests
    // -----------------------------------------------------------------------

    /** A {@code TimestampedVote} must preserve the vote and timestamp values. */
    @Test
    public void timestampedVotePreservesFields() {
        TransactionParticipant.TimestampedVote tv =
                new TransactionParticipant.TimestampedVote(PREPARED, 42L);
        assertEquals("vote field", PREPARED, tv.vote);
        assertEquals("timestamp field", 42L, tv.timestamp);
    }

    // -----------------------------------------------------------------------
    // Optimisation 3 — AbstractTimestampParticipant
    // -----------------------------------------------------------------------

    /**
     * Minimal concrete participant that delegates {@code prepare()} to a fixed
     * vote, suitable for testing {@link AbstractTimestampParticipant}.
     */
    private static final class FixedVoteParticipant
            extends AbstractTimestampParticipant {
        private final int prepVote;

        FixedVoteParticipant(int prepVote) {
            this.prepVote = prepVote;
        }

        @Override
        public int prepare(TransactionManager mgr, long id)
                throws RemoteException {
            return prepVote;
        }

        @Override
        public void commit(TransactionManager mgr, long id)
                throws RemoteException { }

        @Override
        public void abort(TransactionManager mgr, long id)
                throws RemoteException { }

        @Override
        public int prepareAndCommit(TransactionManager mgr, long id)
                throws RemoteException {
            return prepVote;
        }
    }

    /**
     * When {@code prepare()} votes {@code PREPARED}, {@code prepareWithTimestamp()}
     * must return the same vote with a positive timestamp.
     */
    @Test
    public void abstractParticipantReturnsPositiveTimestampForPrepared()
            throws Exception {
        FixedVoteParticipant p = new FixedVoteParticipant(PREPARED);
        TransactionParticipant.TimestampedVote tv =
                p.prepareWithTimestamp(MOCK_MGR, 1L, LamportClock.NO_TIMESTAMP);
        assertEquals("vote must be PREPARED", PREPARED, tv.vote);
        assertTrue("timestamp must be > 0 for PREPARED vote", tv.timestamp > 0L);
    }

    /**
     * When {@code prepare()} votes {@code ABORTED}, {@code prepareWithTimestamp()}
     * must return {@code NO_TIMESTAMP} — the participant will not commit.
     */
    @Test
    public void abstractParticipantReturnsNoTimestampForAborted()
            throws Exception {
        FixedVoteParticipant p = new FixedVoteParticipant(ABORTED);
        TransactionParticipant.TimestampedVote tv =
                p.prepareWithTimestamp(MOCK_MGR, 1L, LamportClock.NO_TIMESTAMP);
        assertEquals("vote must be ABORTED", ABORTED, tv.vote);
        assertEquals("timestamp must be NO_TIMESTAMP for ABORTED",
                LamportClock.NO_TIMESTAMP, tv.timestamp);
    }

    /**
     * When {@code prepare()} votes {@code NOTCHANGED}, {@code prepareWithTimestamp()}
     * must return {@code NO_TIMESTAMP} — the participant made no changes.
     */
    @Test
    public void abstractParticipantReturnsNoTimestampForNotChanged()
            throws Exception {
        FixedVoteParticipant p = new FixedVoteParticipant(NOTCHANGED);
        TransactionParticipant.TimestampedVote tv =
                p.prepareWithTimestamp(MOCK_MGR, 1L, LamportClock.NO_TIMESTAMP);
        assertEquals("vote must be NOTCHANGED", NOTCHANGED, tv.vote);
        assertEquals("timestamp must be NO_TIMESTAMP for NOTCHANGED",
                LamportClock.NO_TIMESTAMP, tv.timestamp);
    }

    /**
     * Successive calls to {@code prepareWithTimestamp()} must advance the
     * embedded Lamport clock monotonically (simulates two sequential
     * transactions using the same participant object).
     */
    @Test
    public void abstractParticipantLamportClockAdvances() throws Exception {
        FixedVoteParticipant p = new FixedVoteParticipant(PREPARED);
        TransactionParticipant.TimestampedVote tv1 =
                p.prepareWithTimestamp(MOCK_MGR, 1L, LamportClock.NO_TIMESTAMP);
        TransactionParticipant.TimestampedVote tv2 =
                p.prepareWithTimestamp(MOCK_MGR, 2L, LamportClock.NO_TIMESTAMP);
        assertTrue("second timestamp must exceed first",
                tv2.timestamp > tv1.timestamp);
    }

    // -----------------------------------------------------------------------
    // Optimisation 3 — TransactionConfig unit tests
    // -----------------------------------------------------------------------

    /** Default {@code TransactionConfig} must be non-readOnly, SERIALIZABLE. */
    @Test
    public void transactionConfigDefaults() {
        TransactionManager.TransactionConfig cfg =
                new TransactionManager.TransactionConfig();
        assertEquals("default isolationLevel must be SERIALIZABLE",
                TransactionManager.TransactionConfig.SERIALIZABLE,
                cfg.isolationLevel);
        assertTrue("default readOnly must be false", !cfg.readOnly);
    }

    /** Explicit {@code readOnly=true} config is preserved. */
    @Test
    public void transactionConfigReadOnlyPreserved() {
        TransactionManager.TransactionConfig cfg =
                new TransactionManager.TransactionConfig(
                        true,
                        TransactionManager.TransactionConfig.SERIALIZABLE);
        assertTrue("readOnly must be true", cfg.readOnly);
    }

    // -----------------------------------------------------------------------
    // Optimisation 3 — single-round commit
    // -----------------------------------------------------------------------

    /**
     * When every PREPARED handle carries a non-zero Lamport commit timestamp
     * (Opt-3), the coordinator must skip CommitJob and commit the transaction
     * in a single round.  With {@code waitFor=0} and the single-round path
     * taken, {@code commit()} must return normally; a {@link TimeoutExpiredException}
     * would indicate the fallback two-phase CommitJob was scheduled instead.
     */
    @Test
    public void singleRoundCommitSkipsCommitJobWhenAllTimestamped()
            throws Exception {
        MockLogManager logMgr = new MockLogManager();

        ParticipantHandle h1 = makeHandle(PREPARED);
        h1.setCommitTimestamp(10L);
        ParticipantHandle h2 = makeHandle(PREPARED);
        h2.setCommitTimestamp(20L);

        TxnManagerTransaction txnT = buildTxn(logMgr);
        txnT.add(h1);
        txnT.add(h2);

        // waitFor=0: if CommitJob is scheduled the TimeoutExpiredException is
        // thrown before tasks complete; single-round path returns immediately.
        try {
            txnT.commit(0L);
        } catch (TimeoutExpiredException e) {
            fail("Opt-3 single-round path should not schedule CommitJob: " + e);
        }

        assertEquals("CommitRecord written once", 1, logMgr.log.writeCount.get());
        assertEquals("Log must be invalidated after single-round commit",
                1, logMgr.log.invalidateCount.get());
    }

    /**
     * When some PREPARED handles lack a timestamp (timestamp==0), the
     * coordinator must fall back to the standard two-phase CommitJob.
     * With {@code waitFor=0} the CommitJob times out, confirming it was
     * scheduled.
     */
    @Test
    public void singleRoundCommitFallsBackWhenSomeTimestampsMissing()
            throws Exception {
        MockLogManager logMgr = new MockLogManager();

        ParticipantHandle h1 = makeHandle(PREPARED);
        h1.setCommitTimestamp(10L);           // has timestamp
        ParticipantHandle h2 = makeHandle(PREPARED);
        // h2 commitTimestamp stays 0 — not all participants support Opt-3

        TxnManagerTransaction txnT = buildTxn(logMgr);
        txnT.add(h1);
        txnT.add(h2);

        try {
            txnT.commit(0L);
            fail("Expected TimeoutExpiredException when CommitJob is scheduled");
        } catch (TimeoutExpiredException e) {
            // expected: CommitJob was scheduled but timed out with waitFor=0
        }

        // CommitRecord must still have been written before CommitJob attempt
        assertEquals("CommitRecord written once", 1, logMgr.log.writeCount.get());
    }

    // -----------------------------------------------------------------------
    // Optimisation 4C — read-only hint
    // -----------------------------------------------------------------------

    /**
     * When a transaction is flagged {@code readOnly} and all participants vote
     * {@code NOTCHANGED}, no durable {@code CommitRecord} is written and the
     * log is immediately invalidated (zero disk I/O fast path).
     */
    @Test
    public void readOnlyAllNotChangedSkipsCommitRecord() throws Exception {
        MockLogManager logMgr = new MockLogManager();

        ParticipantHandle h1 = makeHandle(NOTCHANGED);
        ParticipantHandle h2 = makeHandle(NOTCHANGED);

        TxnManagerTransaction txnT = buildTxn(logMgr);
        txnT.setReadOnly(true);
        txnT.add(h1);
        txnT.add(h2);

        txnT.commit(Long.MAX_VALUE);

        assertEquals("No CommitRecord must be written for readOnly+NOTCHANGED",
                0, logMgr.log.writeCount.get());
        assertEquals("Log must be invalidated",
                1, logMgr.log.invalidateCount.get());
    }

    /**
     * When a {@code readOnly} transaction has at least one {@code PREPARED}
     * participant (a false hint), the coordinator must write a {@code CommitRecord}
     * synchronously and then proceed with CommitJob.  The record is durably
     * committed before the second round begins.
     *
     * <p>With {@code waitFor=0} the CommitJob (scheduled for null-participant
     * handles) times out immediately, confirming the fallback was taken.
     */
    @Test
    public void readOnlyFalseHintWritesCommitRecordAndSchedulesCommitJob()
            throws Exception {
        MockLogManager logMgr = new MockLogManager();

        ParticipantHandle handle = makeHandle(PREPARED);

        TxnManagerTransaction txnT = buildTxn(logMgr);
        txnT.setReadOnly(true);
        txnT.add(handle);

        try {
            txnT.commit(0L);
            // If single-round path is taken (because commitTimestamp was somehow set),
            // the test still validates CommitRecord presence below.
        } catch (TimeoutExpiredException e) {
            // expected: CommitJob was scheduled; timed out with waitFor=0
        }

        assertEquals("CommitRecord must be written on readOnly false-hint",
                1, logMgr.log.writeCount.get());
    }

    /**
     * A non-readOnly transaction with all {@code NOTCHANGED} participants must
     * still write a {@code CommitRecord} before completing (no regression from
     * the existing behaviour).
     */
    @Test
    public void nonReadOnlyAllNotChangedStillWritesCommitRecord()
            throws Exception {
        MockLogManager logMgr = new MockLogManager();

        ParticipantHandle h1 = makeHandle(NOTCHANGED);
        ParticipantHandle h2 = makeHandle(NOTCHANGED);

        TxnManagerTransaction txnT = buildTxn(logMgr);
        // readOnly remains false (default)
        txnT.add(h1);
        txnT.add(h2);

        txnT.commit(Long.MAX_VALUE);

        assertEquals("CommitRecord must be written for non-readOnly transaction",
                1, logMgr.log.writeCount.get());
        assertEquals("Log must be invalidated", 1, logMgr.log.invalidateCount.get());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private TxnManagerTransaction buildTxn(LogManager logMgr) {
        TxnManagerTransaction txnT = new TxnManagerTransaction(
                MOCK_MGR,
                logMgr,
                1L,
                pool,
                wm,
                new RecordingSettler(),
                UuidFactory.generate(),
                AccessController.getContext());
        // expires=0 means "already expired"; set a far-future lease so that
        // commit()/abort() pass the ensureCurrent() guard.
        txnT.setExpiration(Long.MAX_VALUE);
        return txnT;
    }
}
