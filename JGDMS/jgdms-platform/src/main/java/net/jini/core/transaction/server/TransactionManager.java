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
import java.rmi.Remote;
import java.rmi.RemoteException;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.transaction.*;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * The interface used for managers of the two-phase commit protocol for
 * top-level transactions.  All <code>ServerTransaction</code> objects
 * are governed by a transaction manager that runs this protocol.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see NestableTransactionManager
 * @see ServerTransaction
 * @see TransactionParticipant
 *
 * @since 1.0
 */
public interface TransactionManager extends Remote, TransactionConstants {
    /** Class that holds return values from create methods. */
    @AtomicSerial
    public static class Created implements java.io.Serializable {
	static final long serialVersionUID = -4233846033773471113L;
        
        public static SerialForm[] serialForm(){
            return new SerialForm[]{
                new SerialForm("id", Long.TYPE),
                new SerialForm("lease", Lease.class)
            };
        }
        
        public static void serialize(PutArg arg, Created nt) throws IOException{
            arg.put("id", nt.id);
            arg.put("lease", nt.lease);
            arg.writeArgs();
        }

	/**
	 * The transaction ID.
	 * @serial
	 */
        public final long id;

	/**
	 * The lease.
	 * @serial
	 */
        public final Lease lease;
	
	/**
	 * AtomicSerial constructor
	 * 
	 * @param arg atomic deserialization parameter 
	 * @throws IOException if there are I/O errors while reading from GetArg's
	 *         underlying <code>InputStream</code>
	 */
	public Created(GetArg arg) throws IOException, ClassNotFoundException{
	    this(arg.get("id", 0L),
		 arg.get("lease", null, Lease.class)
	    );
	}

	/**
	 * Simple constructor.
	 *
	 * @param id the transaction ID
	 * @param lease the lease granted
	 */
        public Created(long id, Lease lease) {
            this.id = id; this.lease = lease;
        }
        
        // inherit javadoc
        public String toString() {
            return this.getClass().getName() 
                + "[lease=" + lease + ", id=" + id + "]";
        }
    }

    /**
     * Begin a new top-level transaction.
     *
     * @param lease the requested lease time for the transaction
     * @return the transaction ID and the lease granted
     *
     * @throws LeaseDeniedException if this manager is unwilling to 
     *         grant the requested lease time
     * @throws RemoteException if there is a communication error
     */
    Created create(long lease) throws LeaseDeniedException, RemoteException;
 
    /**
     * Join a transaction that is managed by this transaction manager.
     * The <code>crashCount</code> marks the state of the storage used by
     * the participant for transactions. If the participant attempts to
     * join a transaction more than once, the crash counts must be the same.
     * Each system crash or other event that destroys the state of the
     * participant's unprepared transaction storage must cause the crash
     * count to increase by at least one.
     *
     * @param id the transaction ID
     * @param part the participant joining the transaction
     * @param crashCount the participant's current crash count
     *
     * @throws UnknownTransactionException if the transaction 
     *         is unknown to the transaction manager, either 
     *         because the transaction ID is incorrect or because the 
     *         transaction is complete and its state has been 
     *         discarded by the manager.
     * @throws CannotJoinException if the transaction is known 
     *         to the manager but is no longer active.
     * @throws CrashCountException if the crash count provided 
     *         for the participant differs from the crash count 
     *         in a previous invocation of the same pairing of 
     *         participant and transaction
     * @throws RemoteException if there is a communication error
     * @see ServerTransaction#join
     *
     */
    void join(long id, TransactionParticipant part, long crashCount)
       	throws UnknownTransactionException, CannotJoinException,
	       CrashCountException, RemoteException;

    /**
     * Returns the current state of the given transaction.  The returned
     * state can be any of the <code>TransactionConstants</code> values.
     * 
     * @param id the transaction ID
     * @return an <code>int</code> representing the state of the transaction
     *
     * @throws UnknownTransactionException if the transaction 
     *         is unknown to the transaction manager, either 
     *         because the transaction ID is incorrect or because the 
     *         transaction is complete and its state has been 
     *         discarded by the manager.
     * @throws RemoteException if there is a communication error
     *
     * @see ServerTransaction#getState
     * @see TransactionConstants
     */
    int getState(long id) throws UnknownTransactionException, RemoteException;

    /**
     * Commit the transaction. Commit asks the transaction manager to
     * execute the voting process with the participants.  Returns if the
     * transaction successfully reaches either the <code>NOTCHANGED</code>
     * or the <code>COMMITTED</code> state, without waiting for the
     * transaction manager to notify all participants of the decision.
     * If the transaction must be aborted (because one or more participants
     * are unable to prepare), <code>CannotCommitException</code> is thrown
     * without waiting for the transaction manager to notify all participants
     * of the decision.
     *
     * @param id the transaction ID
     *
     * @throws UnknownTransactionException if the transaction 
     *         is unknown to the transaction manager, either 
     *         because the transaction ID is incorrect or because the 
     *         transaction is complete and its state has been 
     *         discarded by the manager.
     * @throws CannotCommitException if the transaction is being or already 
     *         has been aborted
     * @throws RemoteException if there is a communication error
     */
    void commit(long id)
	throws UnknownTransactionException, CannotCommitException,
	       RemoteException;

    /**
     * Commit the transaction, waiting for participants to be notified of
     * the decision. Commit asks the transaction manager to execute the
     * voting process with the participants.  Returns if the transaction
     * successfully reaches either the <code>NOTCHANGED</code> or the
     * <code>COMMITTED</code> state, and the transaction manager has
     * notified all participants of the decision, before the specified
     * timeout expires.  If the transaction must be aborted (because one
     * or more participants are unable to prepare),
     * <code>CannotCommitException</code> is thrown if the transaction
     * manager is able to notify all participants of the decision before
     * the specified timeout expires.  If the transaction manager reaches
     * a decision, but is unable to notify all participants of that
     * decision before the specified timeout expires, then
     * <code>TimeoutExpiredException</code> is thrown.  If the specified
     * timeout expires before the transaction manager reaches a decision,
     * <code>TimeoutExpiredException</code> is not thrown until the
     * manager reaches a decision.
     *
     * @param id the transaction ID
     * @param waitFor timeout to wait, from the start of the call until
     * all participants have been notified of the transaction manager's
     * decision
     *
     * @throws UnknownTransactionException if the transaction 
     *         is unknown to the transaction manager, either 
     *         because the transaction ID is incorrect or because the 
     *         transaction is complete and its state has been 
     *         discarded by the manager.
     * @throws CannotCommitException if the transaction is being or already 
     *         has been aborted
     * @throws TimeoutExpiredException if the transaction manager reaches
     *         a decision, but is unable to notify all participants of that
     *         decision before the specified timeout expires
     * @throws RemoteException if there is a communication error
     */
    void commit(long id, long waitFor)
        throws UnknownTransactionException, CannotCommitException,
	       TimeoutExpiredException, RemoteException;

    /**
     * Abort the transaction. This can be called at any time by
     * any object holding a reference to the transaction.  Abort asks
     * the transaction manager to abort the transaction and to notify
     * each participant of the decision, resulting in them rolling back
     * any state changes made as part of the transaction.  Returns as
     * soon as the transaction manager records the abort decision, without
     * waiting for the transaction manager to notify all participants of
     * the decision.
     *
     * @param id the transaction ID
     *
     * @throws UnknownTransactionException if the transaction 
     *         is unknown to the transaction manager, either 
     *         because the transaction ID is incorrect or because the 
     *         transaction is complete and its state has been 
     *         discarded by the manager.
     * @throws CannotAbortException if the transaction is known to have 
     *         previously reached the COMMITTED state due to an earlier 
     *         commit
     * @throws RemoteException if there is a communication error
     *
     */
    void abort(long id)
	throws UnknownTransactionException, CannotAbortException,
	       RemoteException;

    /**
     * Abort the transaction, waiting for participants to be notified of
     * the decision. This can be called at any time by any object holding
     * a reference to the transaction.  Abort asks the transaction manager
     * to abort the transaction and to notify each participant of the
     * decision, resulting in them rolling back any state changes made as
     * part of the transaction.  Returns if the transaction manager records
     * the decision and is able to notify all participants of the decision
     * before the specified timeout expires.  If the transaction manager
     * is unable to notify all participants of the decision before the
     * specified timeout expires, then <code>TimeoutExpiredException</code>
     * is thrown.
     *
     * @param id the transaction ID
     *
     * @param waitFor timeout to wait, from the start of the call until
     * all participants have been notified of the transaction manager's
     * decision
     *
     * @throws UnknownTransactionException if the transaction 
     *         is unknown to the transaction manager, either 
     *         because the transaction ID is incorrect or because the 
     *         transaction is complete and its state has been 
     *         discarded by the manager.
     * @throws CannotAbortException if the transaction is known to have 
     *         previously reached the COMMITTED state due to an earlier 
     *         commit
     * @throws TimeoutExpiredException if the timeout expires before 
     *         all participants have been notified
     * @throws RemoteException if there is a communication error
     *
     */
    void abort(long id, long waitFor)
	throws UnknownTransactionException, CannotAbortException,
	       TimeoutExpiredException, RemoteException;

    /**
     * Begin a new top-level transaction with the supplied configuration.
     *
     * <p>This default implementation ignores the configuration and delegates
     * to {@link #create(long)}, providing backward compatibility for proxies
     * and servers that have not yet been updated to support
     * {@link TransactionConfig}.  Upgraded servers should override this method
     * to honour the configuration (e.g. the {@link TransactionConfig#readOnly}
     * hint for Opt-4C).
     *
     * <p><b>Wire compatibility note:</b> new proxy stubs that override this
     * method will dispatch it as a remote call to the server.  An old server
     * that does not export this method will fail with a
     * {@link RemoteException}.  Deploy the upgraded server before updating
     * clients.
     *
     * @param lease  the requested lease time for the transaction
     * @param config the transaction configuration; must not be {@code null}
     * @return the transaction ID and the lease granted
     *
     * @throws LeaseDeniedException if this manager is unwilling to grant the
     *         requested lease time
     * @throws RemoteException if there is a communication error
     */
    default Created create(long lease, TransactionConfig config)
            throws LeaseDeniedException, RemoteException {
        return create(lease);
    }

    /**
     * Parameter object that carries hints for transaction creation.
     *
     * <p>Clients pass a {@code TransactionConfig} to
     * {@link TransactionManager#create(long, TransactionConfig)} to indicate
     * properties of the transaction they are about to begin.  Hints are
     * advisory only; the transaction manager may ignore any of them.
     *
     * <p>Currently defined hints:
     * <ul>
     *   <li>{@link #readOnly} — the client intends to perform only reads.
     *       Mahalo uses this to skip the {@code CommitRecord} write and the
     *       {@code CommitJob} round-trip when all participants vote
     *       {@code NOTCHANGED} (Opt-4C).</li>
     *   <li>{@link #isolationLevel} — the requested isolation level;
     *       currently only {@link #SERIALIZABLE} is defined.</li>
     * </ul>
     */
    @AtomicSerial
    final class TransactionConfig implements java.io.Serializable {

        private static final long serialVersionUID = 1L;

        /** Serializable isolation-level constant: full serialisability. */
        public static final int SERIALIZABLE = 0;

        /** @return serialisation field descriptors */
        public static SerialForm[] serialForm() {
            return new SerialForm[] {
                new SerialForm("readOnly",       Boolean.TYPE),
                new SerialForm("isolationLevel", Integer.TYPE)
            };
        }

        /** @AtomicSerial serialise method. */
        public static void serialize(PutArg arg, TransactionConfig tc)
                throws IOException {
            arg.put("readOnly",       tc.readOnly);
            arg.put("isolationLevel", tc.isolationLevel);
            arg.writeArgs();
        }

        /** @AtomicSerial deserialisation constructor. */
        public TransactionConfig(GetArg arg)
                throws IOException, ClassNotFoundException {
            this(arg.get("readOnly",       false),
                 checkIsolationLevel(arg.get("isolationLevel", SERIALIZABLE)));
        }

        private static int checkIsolationLevel(int level) throws IOException {
            if (level != SERIALIZABLE)
                throw new IOException(
                        "TransactionConfig: unknown isolationLevel: " + level);
            return level;
        }

        /**
         * Creates a default configuration (read-write, {@link #SERIALIZABLE}).
         */
        public TransactionConfig() {
            this(false, SERIALIZABLE);
        }

        /**
         * Creates a configuration with explicit settings.
         *
         * @param readOnly       {@code true} if the client intends to
         *                       perform only reads
         * @param isolationLevel one of the isolation-level constants defined
         *                       on this class (currently only
         *                       {@link #SERIALIZABLE})
         */
        public TransactionConfig(boolean readOnly, int isolationLevel) {
            this.readOnly       = readOnly;
            this.isolationLevel = isolationLevel;
        }

        /**
         * {@code true} if the client declares this transaction to be
         * read-only.  When {@code true} and all participants vote
         * {@code NOTCHANGED}, Mahalo can skip both the {@code CommitRecord}
         * write and the {@code CommitJob} entirely (Opt-4C).
         *
         * <p>This is a hint only: if any participant votes {@code PREPARED},
         * Mahalo falls back to full two-phase commit regardless of this flag.
         *
         * @serial
         */
        public final boolean readOnly;

        /**
         * The requested isolation level.  Only {@link #SERIALIZABLE} is
         * currently defined.
         *
         * @serial
         */
        public final int isolationLevel;
    }
}
