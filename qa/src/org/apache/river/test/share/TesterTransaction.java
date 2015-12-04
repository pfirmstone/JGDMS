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
package org.apache.river.test.share;

import java.util.logging.Level;

// All imports
import net.jini.core.transaction.*;
import net.jini.core.transaction.server.*;
import org.apache.river.constants.TxnConstants;
import java.util.Collection;
import java.util.ArrayList;
import java.rmi.RemoteException;
import org.apache.river.qa.harness.TestException;


/**
 * This is the transaction type used by <code>TesterTransactionManager</code>.
 * Currently it only supports single-participant transactions.
 *
 * @see TesterTransactionManager
 */
public class TesterTransaction implements TransactionConstants {

    /**
     * The transaction ID as a <code>Long</code> object.  This is needed
     * in enough code that we bother to create this cache.
     */
    public final Long idObj;
    private TransactionParticipant part; // our participant
    private long crashCnt; // its crash count
    private volatile int state; // our state;
    private volatile int getStateFailCnt; // # of times to fail

    /**
     * The <code>ServerTransaction</code> for us.
     */
    public final ServerTransaction transaction;

    /**
     * Create a new <code>TesterTransaction</code>.
     */
    public TesterTransaction(TransactionManager mgr, long id) {
        idObj = new Long(id);
        transaction = new ServerTransaction(mgr, id);
        state = ACTIVE;
    }

    /**
     * Send a <code>prepareAndCommit</code> message to the participant.
     */
    public synchronized void commit()
            throws UnknownTransactionException, RemoteException {
        sendPrepareAndCommit();
    }

    /**
     * Send a <code>prepare</code> message to the participants,
     *
     * @return the state of the transaction after the messages.
     */
    public synchronized int sendPrepare()
            throws UnknownTransactionException, RemoteException {
        state = part.prepare(transaction.mgr, idObj.longValue());
        return state;
    }

    /**
     * Send a <code>prepareAndCommit</code> message to the participants,
     *
     * @return the state of the transaction after the messages.
     */
    public synchronized int sendPrepareAndCommit()
            throws UnknownTransactionException, RemoteException {
        state = part.prepareAndCommit(transaction.mgr, idObj.longValue());
        return state;
    }

    /**
     * Send a <code>commit</code> message to the participants.
     */
    public synchronized void sendCommit()
            throws UnknownTransactionException, RemoteException {
        part.commit(transaction.mgr, idObj.longValue());
        state = COMMITTED;
    }

    /**
     * Send a <code>abort</code> message to the participants.
     */
    public synchronized void sendAbort()
            throws UnknownTransactionException, RemoteException {
        part.abort(transaction.mgr, idObj.longValue());
        state = ABORTED;
    }

    /**
     * Return the current state of this transaction.
     */
    public int getState() throws RemoteException {
        synchronized (this){
            if (getStateFailCnt > 0) {
                getStateFailCnt--;
                throw new RemoteException("getState forced to fail");
            }
            return state;
        }
    }

    /**
     * Return the current state of this transaction, ignoring
     * the fail count.
     */
    public int localGetState() {
        return state;
    }

    /**
     * Set the current state of this transaction.
     */
    public void setState(int newState) {
        state = newState;
    }

    /**
     * Tell getState to throw RemoteException the next <code>cnt</code>
     * times it's invoked.
     */
    public void getStateFailCnt(int cnt) {
        getStateFailCnt = cnt;
    }

    /**
     * Set the participant.
     */
    public void join(TransactionParticipant newPart, long newCrashCnt)
            throws CannotJoinException, CrashCountException {
        synchronized (this){
            if (state != ACTIVE) {
                throw new CannotJoinException("State is "
                        + TxnConstants.getName(state));
            }

            if (part == null) {
                part = newPart;
                crashCnt = newCrashCnt;
                return;
            }

            if (!part.equals(newPart)) {
                throw new CannotJoinException("Only one participant allowed");
            }

            if (newCrashCnt != crashCnt) {
                throw new CrashCountException("crash counts unequal: old = "
                        + crashCnt + ", new = " + newCrashCnt);
            }

            // so it's the same participant with the same crash count: cool
            return;
        }
    }

    /**
     * Assert that the participant count is the one provided. Throws
     * <code>org.apache.river.qa.harness.TestException</code> if there it is not.
     */
    public void assertParticipants(int count) throws TestException {
        int actual = 0;
        synchronized (this){
            actual = (part == null ? 0 : 1);
        }
        if (count != actual) {
            throw new TestException("participant count should be " + count
                    + ", is " + actual);
        }
    }
}
