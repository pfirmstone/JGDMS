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

import java.io.IOException;
import java.io.Serializable;
import net.jini.core.transaction.UnknownTransactionException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * The interface used for participants of the two-phase commit protocol.
 * The methods on this interface are called by the transaction manager.
 * Any class that wants to have operations wrapped in a transaction
 * needs to support this interface. In conjunction with the
 * <code>TransactionManager</code> interface, this interface allows a
 * two-phase commit protocol to be used between objects in a distributed
 * system.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see TransactionManager
 * @see NestableTransactionManager
 * @see ServerTransaction
 *
 * @since 1.0
 */
public interface TransactionParticipant extends Remote, TransactionConstants {
    /**
     * Requests that the participant prepare itself to commit the transaction,
     * and to vote on the outcome of the transaction. The participant responds
     * with either <code>PREPARED</code>, indicating that it is prepared;
     * <code>ABORT</code>, indicating that it will abort, or
     * <code>NOTCHANGED</code>, indicating that it did not have any state
     * changed by the transaction (i.e., it was read-only). If the response
     * is <code>PREPARED</code>, the participant must wait until it receives
     * a commit or abort call from the transaction manager; it may query the
     * transaction manager if needed as to the state of the transaction. If
     * the response is <code>ABORT</code>, the participant should roll its
     * state back to undo any changes that occurred due to operations
     * performed under the transaction; it can then discard any information
     * about the transaction. If the response is <code>NOTCHANGED</code>, the
     * participant can immediately discard any knowledge of the transaction.
     *
     * @param mgr the manager of the transaction
     * @param id the transaction ID
     *
     * @return an <code>int</code> representing this participant's state
     * @throws UnknownTransactionException if the transaction 
     *         is unknown to the transaction manager, either 
     *         because the transaction ID is incorrect or because the 
     *         transaction is complete and its state has been 
     *         discarded by the manager.
     * @throws RemoteException if there is a communication error
     *
     */
    int prepare(TransactionManager mgr, long id)
      throws UnknownTransactionException, RemoteException;

    /**
     * Requests that the participant make all of its <code>PREPARED</code>
     * changes for the specified transaction visible outside of the
     * transaction and unlock any resources locked by the transaction.
     * All state associated with the transaction can then be discarded
     * by the participant.
     *
     * @param mgr the manager of the transaction
     * @param id the transaction ID
     *
     * @throws UnknownTransactionException if the transaction 
     *         is unknown to the transaction manager, either 
     *         because the transaction ID is incorrect or because the 
     *         transaction is complete and its state has been 
     *         discarded by the manager.
     * @throws RemoteException if there is a communication error
     *
     */
    void commit(TransactionManager mgr, long id)
	throws UnknownTransactionException, RemoteException;

    /**
     * Requests that the participant roll back any changes for the specified
     * transaction and unlock any resources locked by the transaction.
     * All state associated with the transaction can then be discarded
     * by the participant.
     *
     * @param mgr the manager of the transaction
     * @param id the transaction ID
     *
     * @throws UnknownTransactionException if the transaction 
     *         is unknown to the transaction manager, either 
     *         because the transaction ID is incorrect or because the 
     *         transaction is complete and its state has been 
     *         discarded by the manager.
     * @throws RemoteException if there is a communication error
     *
     */
    void abort(TransactionManager mgr, long id)
	throws UnknownTransactionException, RemoteException;

    /**
     * A combination of <code>prepare</code> and <code>commit</code>, which
     * can be used by the manager when there is just one participant left to
     * prepare and all other participants (if any) have responded with
     * <code>NOTCHANGED</code>.  The participant's implementation of this
     * method must be equivalent to:
     * <pre>
     *	public int prepareAndCommit(TransactionManager mgr, long id)
     *	    throws UnknownTransactionException, RemoteException
     *	{
     *	    int result = prepare(mgr, id);
     *	    if (result == PREPARED) {
     *		commit(mgr, id);
     *		result = COMMITTED;
     *	    }
     *	    return result;
     *	}
     * </pre>
     *
     * @param mgr the manager of the transaction
     * @param id the transaction ID
     *
     * @return an <code>int</code> representing its state
     * @throws UnknownTransactionException if the transaction 
     *         is unknown to the transaction manager, either 
     *         because the transaction ID is incorrect or because the 
     *         transaction is complete and its state has been 
     *         discarded by the manager.
     * @throws RemoteException if there is a communication error
     *
     * @see #prepare
     * @see #commit
     */
    int prepareAndCommit(TransactionManager mgr, long id)
	throws UnknownTransactionException, RemoteException;

    /**
     * Requests that the participant prepare itself to commit the transaction
     * and return a Lamport timestamp for use in the Granola single-round
     * commit optimisation (Opt-3).
     *
     * <p>This is a default method for backward compatibility.  Participants
     * that do not override it receive a standard {@link #prepare} remote
     * call; the returned {@link TimestampedVote} will carry
     * {@link LamportClock#NO_TIMESTAMP} ({@code 0}), telling Mahalo to fall
     * back to the standard two-phase-commit second round.
     *
     * <p>Participants that wish to enable the single-round optimisation
     * should extend {@link AbstractTimestampParticipant} and override this
     * method (or the whole class), returning a non-zero timestamp in the
     * vote.  The coordinator collects all timestamps, computes
     * {@code max + 1} as the global commit timestamp, and marks the
     * transaction {@code COMMITTED} without dispatching a {@code CommitJob}.
     *
     * @param mgr the manager of the transaction
     * @param id the transaction ID
     * @param txnTimestamp the coordinator's current Lamport timestamp
     *        (may be {@link LamportClock#NO_TIMESTAMP} if the coordinator
     *        does not maintain a clock)
     * @return a {@link TimestampedVote} carrying this participant's vote
     *         ({@code PREPARED}, {@code NOTCHANGED}, or {@code ABORTED})
     *         and a Lamport timestamp; a timestamp of
     *         {@link LamportClock#NO_TIMESTAMP} signals that the
     *         single-round optimisation is not available
     * @throws UnknownTransactionException if the transaction is unknown
     * @throws RemoteException if there is a communication error
     */
    default TimestampedVote prepareWithTimestamp(
            TransactionManager mgr, long id, long txnTimestamp)
            throws UnknownTransactionException, RemoteException {
        int vote = prepare(mgr, id);
        return new TimestampedVote(vote, LamportClock.NO_TIMESTAMP);
    }

    /**
     * Value object returned by {@link #prepareWithTimestamp} carrying both
     * the participant's vote and its Lamport timestamp.
     *
     * <p>A {@link #timestamp} of {@link LamportClock#NO_TIMESTAMP} ({@code 0})
     * means the participant does not support timestamp-ordering and the
     * Granola single-round optimisation is not available.
     */
    @AtomicSerial
    final class TimestampedVote implements Serializable {

        private static final long serialVersionUID = 1L;

        /** The participant's vote: {@code PREPARED}, {@code NOTCHANGED}, or {@code ABORTED}. */
        public final int vote;

        /**
         * The participant's Lamport timestamp, or
         * {@link LamportClock#NO_TIMESTAMP} if timestamp-ordering is not
         * supported.
         */
        public final long timestamp;

        /** @return serialisation field descriptors */
        public static SerialForm[] serialForm() {
            return new SerialForm[] {
                new SerialForm("vote",      Integer.TYPE),
                new SerialForm("timestamp", Long.TYPE)
            };
        }

        /** @AtomicSerial serialise method. */
        public static void serialize(PutArg arg, TimestampedVote tv)
                throws IOException {
            arg.put("vote",      tv.vote);
            arg.put("timestamp", tv.timestamp);
            arg.writeArgs();
        }

        /** @AtomicSerial deserialisation constructor. */
        public TimestampedVote(GetArg arg)
                throws IOException, ClassNotFoundException {
            this(checkVote(arg.get("vote",      NOTCHANGED)),
                 checkTimestamp(arg.get("timestamp", LamportClock.NO_TIMESTAMP)));
        }

        private static int checkVote(int vote) throws IOException {
            if (vote != PREPARED && vote != NOTCHANGED && vote != ABORTED)
                throw new IOException(
                        "TimestampedVote: invalid vote value: " + vote);
            return vote;
        }

        private static long checkTimestamp(long timestamp) throws IOException {
            if (timestamp < 0)
                throw new IOException(
                        "TimestampedVote: timestamp must be >= 0, got: " + timestamp);
            return timestamp;
        }

        /**
         * @param vote      the participant's vote
         * @param timestamp the Lamport timestamp ({@link LamportClock#NO_TIMESTAMP}
         *                  if not applicable)
         * @throws IllegalArgumentException if {@code vote} is not one of
         *         {@code PREPARED}, {@code NOTCHANGED}, or {@code ABORTED}, or
         *         if {@code timestamp} is negative
         */
        public TimestampedVote(int vote, long timestamp) {
            if (vote != PREPARED && vote != NOTCHANGED && vote != ABORTED)
                throw new IllegalArgumentException(
                        "TimestampedVote: invalid vote value: " + vote);
            if (timestamp < 0)
                throw new IllegalArgumentException(
                        "TimestampedVote: timestamp must be >= 0, got: " + timestamp);
            this.vote      = vote;
            this.timestamp = timestamp;
        }
    }
}
