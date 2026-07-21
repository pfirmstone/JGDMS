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
package org.apache.river.outrigger;

import net.jini.id.Uuid;

/**
 * Methods that recover the state of the space after a restart.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see LogOps
 * @see OutriggerServerImpl
 */
public interface Recover {

    /**
     * Recover the session id from the previous session.
     *
     * @see LogOps#bootOp
     *
     * @param sessionId is the value of the session id recorded from
     *        the last call to <code>booOp</code>
     */
    public void recoverSessionId(long sessionId);

    /**
     * Recover the join state from the previous session.
     *
     * @throws java.lang.Exception if the state can't be recovered.
     * @see LogOps#joinStateOp
     *
     * @param state is the stored join state
     */
    public void recoverJoinState(StoredObject state) throws Exception;

    /**
     * Recover a write operation. The recovered <code>entry</code> is the
     * stored form of the entry passed into <code>writeOp</code>. If the
     * original write was done under a transaction, and the transaction
     * was prepared <code>txnId</code> will be non-null.
     *
     * @see LogOps#writeOp
     *
     * @param entry stored from of the written entry
     *
     * @param txnId transaction identifier or <code>null</code>
     *
     * @exception Exception is thrown if any error occurs recovering the
     *            write
     */
    public void recoverWrite(StoredResource entry, Long txnId)
	throws Exception;

    /**
     * Recover a take operation. If the original take was done under
     * a transaction, and the transaction was prepared, <code>txnId</code>
     * will be non-null.
     *
     * @see LogOps#takeOp
     *
     * @param cookie identifier of the entry to take
     *
     * @param txnId transaction identifier or <code>null</code>
     *
     * @exception Exception is thrown if any error occurs recovering the take
     */
    public void recoverTake(Uuid cookie, Long txnId) throws Exception;

    /**
     * Recover an event registration. The recovered
     * <code>registration</code> is the stored form of the
     * registration passed into <code>registerOp</code>. The recovered
     * <code>template</code> is the stored form of the template.
     *
     * @see LogOps#registerOp
     *
     * @param registration stored from of the logged registration
     *
     * @param type of registration, same value that was passed into
     *             corresponding <code>registerOp</code> call
     * 
     * @param templates stored from of the logged templates
     *
     * @exception Exception is thrown if any error occurs recovering the
     *            registration 
     */
    public void recoverRegister(StoredResource registration, String type,
				StoredObject[] templates)
	throws Exception;

    /**
     * Recover a prepared transaction. The recovered
     * <code>transaction</code> is the stored form of the transaction
     * passed into <code>prepareOp</code>.
     *
     * @see LogOps#prepareOp
     *
     * @param txnId transaction identifier
     *
     * @param transaction stored from of the prepared transaction
     *
     * @exception Exception is thrown if any error occurs recovering the
     *            transaction 
     */
    public void recoverTransaction(Long txnId, StoredObject transaction)
	throws Exception;

    /**
     * Recover the <code>Uuid</code> for the service as a whole.
     * Will only be called if a <code>Uuid</code> has be stored during
     * a previous incarnation.
     * @see LogOps#uuidOp
     * @param uuid The <code>Uuid</code> being recovered.
     */
    public void recoverUuid(Uuid uuid);

    /**
     * Recover the marshalling format the store was born with. Outrigger is
     * DER-only in JGDMS 4.0.0 (JGDMS-STD-006 sec.3 item 5, strengthened by
     * {@code SOW-Outrigger-DER-Only-JOSS-Rejection.md}): implementations
     * MUST refuse -- unconditionally, not relative to any configuration --
     * a store whose persisted format is not
     * {@link net.jini.core.constraint.MarshallingFormat#ATOMIC_DER},
     * by throwing the <em>checked</em>
     * {@link IncompatibleStoreException} (checked so the startup
     * cleanup path runs: fail-loud must also be fail-clean). Called once,
     * before any entry/registration recovery is dispatched and before any
     * log consumption mutates the store, so a refused store is left
     * pristine on disk for offline conversion. Will only be called if a
     * format has been stored during a previous incarnation -- an empty
     * store (no snapshot yet) has no prior format and is a legal birth.
     *
     * @param format the marshalling format token recovered from the store
     * @throws IncompatibleStoreException if the recovered format is
     *         not {@code ATOMIC_DER}
     */
    public void recoverEntryFormat(String format)
        throws IncompatibleStoreException;
}

