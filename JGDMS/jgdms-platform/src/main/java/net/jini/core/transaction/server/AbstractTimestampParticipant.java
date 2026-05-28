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
package net.jini.core.transaction.server;

import java.rmi.RemoteException;
import net.jini.core.transaction.UnknownTransactionException;

/**
 * Convenience abstract base class for {@link TransactionParticipant}
 * implementations that wish to support the Granola single-round commit
 * optimisation (Opt-3).
 *
 * <p>Subclasses must still implement {@link TransactionParticipant#prepare},
 * {@link TransactionParticipant#commit},
 * {@link TransactionParticipant#abort}, and
 * {@link TransactionParticipant#prepareAndCommit}.
 *
 * <p>This class encapsulates a {@link LamportClock} and provides a concrete
 * implementation of {@link #prepareWithTimestamp} that:
 * <ol>
 *   <li>calls {@link #prepare} to obtain the participant's vote;</li>
 *   <li>if the vote is {@code PREPARED}, advances the
 *       clock using the coordinator's {@code txnTimestamp} (Lamport
 *       observe-and-tick rule);</li>
 *   <li>calls {@link #commit} so that the participant's prepared changes are
 *       applied locally before the method returns — this is the key enabling
 *       step for the single-round optimisation;</li>
 *   <li>returns a {@link TransactionParticipant.TimestampedVote} with the
 *       updated timestamp, signalling to Mahalo that the single-round path
 *       is available for this participant.</li>
 * </ol>
 *
 * <p>A non-zero timestamp in the returned vote signals to Mahalo that this
 * participant has already applied its prepared changes.  After collecting
 * all votes with non-zero timestamps, Mahalo marks the transaction
 * {@code COMMITTED} without dispatching a second {@code CommitJob} round.
 * Any subsequent {@link #commit} call from the coordinator must be treated
 * as a no-op by the subclass (idempotent commit contract).
 *
 * <p>Participants whose {@link #commit} implementation is not idempotent, or
 * that cannot safely self-commit during the prepare phase, should
 * <em>not</em> extend this class.  Mahalo falls back to standard
 * two-phase commit for any transaction that involves at least one participant
 * returning {@link LamportClock#NO_TIMESTAMP}.
 *
 * @see LamportClock
 * @see TransactionParticipant#prepareWithTimestamp
 * @see TransactionParticipant.TimestampedVote
 */
public abstract class AbstractTimestampParticipant
        implements TransactionParticipant {

    /** The Lamport clock used to generate monotonically-increasing timestamps. */
    private final LamportClock clock;

    /**
     * Creates a new instance with a fresh {@link LamportClock} starting at 1.
     */
    protected AbstractTimestampParticipant() {
        this.clock = new LamportClock();
    }

    /**
     * Creates a new instance using the supplied {@link LamportClock},
     * which allows callers to share or persist a clock across restarts.
     *
     * @param clock the Lamport clock to use; must not be {@code null}
     */
    protected AbstractTimestampParticipant(LamportClock clock) {
        if (clock == null) throw new NullPointerException("clock");
        this.clock = clock;
    }

    /**
     * Returns the Lamport clock used by this participant.  Subclasses may
     * use the clock to observe remote timestamps received through other
     * channels.
     *
     * @return the internal {@link LamportClock}; never {@code null}
     */
    protected final LamportClock getLamportClock() {
        return clock;
    }

    /**
     * Prepares the transaction, self-commits, and returns a
     * {@link TransactionParticipant.TimestampedVote} with a Lamport timestamp,
     * enabling the Granola single-round commit optimisation.
     *
     * <p>This implementation calls {@link #prepare(TransactionManager, long)}.
     * If and only if the vote is {@code PREPARED}:
     * <ol>
     *   <li>the internal Lamport clock is advanced via
     *       {@link LamportClock#observe(long)} using {@code txnTimestamp};</li>
     *   <li>{@link #commit(TransactionManager, long)} is called to apply the
     *       prepared changes locally — enabling Mahalo to skip the
     *       {@code CommitJob} round entirely when all participants return a
     *       non-zero timestamp.</li>
     * </ol>
     * For all other votes ({@code NOTCHANGED}, {@code ABORTED})
     * {@link LamportClock#NO_TIMESTAMP} is returned so that Mahalo falls back
     * to standard two-phase commit for any transaction that involves such
     * participants.
     *
     * <p>Subclass implementations of {@link #commit} must be idempotent: if
     * Mahalo does send a {@code commit()} RPC after recovering from a crash,
     * the call must be handled gracefully (e.g. treated as a no-op).
     *
     * {@inheritDoc}
     */
    @Override
    public TimestampedVote prepareWithTimestamp(
            TransactionManager mgr, long id, long txnTimestamp)
            throws UnknownTransactionException, RemoteException {
        int vote = prepare(mgr, id);
        if (vote == PREPARED) {
            long ts = clock.observe(txnTimestamp);
            // Self-commit: apply prepared changes locally before returning.
            // This is the key step that allows Mahalo to skip the CommitJob
            // round (Granola Opt-3 single-round commit).
            commit(mgr, id);
            return new TimestampedVote(PREPARED, ts);
        }
        return new TimestampedVote(vote, LamportClock.NO_TIMESTAMP);
    }
}
