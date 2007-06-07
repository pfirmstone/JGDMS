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
package com.sun.jini.outrigger;

import net.jini.id.Uuid;

/**
 * Methods that log an operation.  These are used
 * when writing to the store. <p>
 *
 * The <code>writeOp</code> and <code>takeOp</code> methods can
 * be called under a transaction. For these methods an in-progress
 * transaction is indicated by a non-null <code>txnId</code> parameter.
 * The <code>txnId</code> is an identifier for a transaction.
 * Each unique transaction must have a unique identifier and all
 * write or take operations under the same transaction should
 * use the same identifier. The store does not interpret the
 * identifier in any way. <p>
 *
 * <i>Note: Because the transaction identifier
 * must be unique, it can not be <code>ServerTransaction.id</code>.
 * Instead this identifier is the <code>Txn</code> ID. </i> <p>
 *
 * When a transaction is closed, the <code>prepareOp</code>,
 * <code>commitOp</code>, and <code>abortOp</code> methods are
 * passed the identifier (<code>txnId</code>) for that transaction. If
 * <code>prepareOp</code> is called and there is a restart, the
 * <code>txnId</code> passed to the write and take operations will
 * be passed back to the server via the <code>Recover.recoverWrite</code>
 * and <code>Recover.recoverTake</code> methods. Likewise the same
 * identifier will be passed to <code>Recover.recoverTransaction</code>.
 *
 * @see Store
 * @see Recover
 *
 * @author Sun Microsystems, Inc.
 *
 */
public interface LogOps {

    /**
     * Log a server boot (first time start or any reactivation).
     *
     * @see Recover#recoverSessionId
     *
     * @param time stamp for this boot
     *
     * @param sessionId of this boot
     */
    void bootOp(long time, long sessionId);

    /**
     * Log an update to the join state
     *
     * @see Recover#recoverJoinState
     *
     * @param state to be logged
     *
     */
    void joinStateOp(StorableObject state);

    /**
     * Log a <code>write</code> operation. If the operation was
     * performed under a transaction the <code>txnId</code> is
     * the identifier for that transaction.
     *
     * @see Recover#recoverWrite
     *
     * @param entry to be logged
     *
     * @param txnId transaction identifier or <code>null</code> if
     *        no transaction is active for this write
     */
    void writeOp(StorableResource entry, Long txnId);

    /**
     * Log a batch <code>write</code> operation. If the operation was
     * performed under a transaction the <code>txnId</code> is
     * the identifier for that transaction.
     *
     * @see Recover#recoverWrite
     * @param entries to be logged
     * @param txnId transaction identifier or <code>null</code> if
     *        no transaction is active for this write
     */
    void writeOp(StorableResource entries[], Long txnId);

    /**
     * Log a <code>take</code> operation. If the operation was
     * performed under a transaction the <code>txnId</code> is
     * the identifier for that transaction.
     *
     * @see Recover#recoverTake
     *
     * @param cookie ID identifying the entry target to be taken
     *
     * @param txnId transaction identifier or <code>null</code> if
     *        no transaction is active for this take
     */
    void takeOp(Uuid cookie, Long txnId);

    /**
     * Log a batch <code>take</code> operation. If the operation was
     * performed under a transaction the <code>txnId</code> is
     * the identifier for that transaction.
     *
     * @see Recover#recoverTake
     *
     * @param cookies IDs identifying the entries to be taken
     *
     * @param txnId transaction identifier or <code>null</code> if
     *        no transaction is active for this take
     */
    void takeOp(Uuid[] cookies, Long txnId);

    /**
     * Log a <code>notify</code> operation.  Notifications under
     * transactions are lost at the end of the transaction, so the
     * only ones that are logged are those that are under no
     * transaction.
     *
     * @see Recover#recoverRegister
     *
     * @param registration to be logged
     *
     * @param type of registration, passed back via <code>type</code>
     *             parameter of corresponding <code>recoverRegister</code>
     *             call
     *
     * @param templates associated with this registration 
     */
    void registerOp(StorableResource registration, String type,
		    StorableObject[] templates);

    /**
     * Log a <code>renew</code> operation.  We use the expiration, not
     * the extension, because we don't want to calculate the
     * expiration relative to when we read the log -- we want to use
     * the exact expiration granted.
     *
     * @see StoredResource
     *
     * @param cookie ID of the entry or registration associated with this
     *               renew
     *
     * @param expiration time 
     */
    void renewOp(Uuid cookie, long expiration);

    /**
     * Log a <code>cancel</code> and entry or registration. The entry or
     * registration associated with <code>cookie</code> will no longer
     * be recoverable and may be removed from the log records.
     *
     * @param cookie ID of the entry or registration to cancel
     * @param expired is true if the cancel was due to a lease expiration
     */
    void cancelOp(Uuid cookie, boolean expired);

    /**
     * Log a transaction <code>prepare</code>. If there is a restart
     * before either <code>commitOp</code> or <code>abortOp</code> is
     * called for the transaction identified by <code>txnId</code>,
     * all write and take operations associated with <code>txnId</code>
     * will be recovered and <code>Recover.recoverTransaction</code>
     * called with the the same <code>txnId</code>.
     *
     * @see Recover#recoverTransaction
     *
     * @param txnId identifier of the transaction to be prepared
     *
     * @param transaction object associated with this transaction
     */
    void prepareOp(Long txnId, StorableObject transaction);

    /**
     * Log a transaction <code>commit</code> or
     * <code>prepareAndCommit</code>.  The store will commit the write
     * and take operations associated with <code>txnId</code>. A call
     * to <code>prepareOP</code> is not required for
     * <code>commitOp</code> to be called.
     *
     * @param txnId identifier of the transaction to be committed 
     */
    void commitOp(Long txnId);

    /**
     * Log a transaction <code>abort</code>. Any write and take operations
     * associated with <code>txnId</code> will no longer be recoverable
     * and may be removed from the log records.
     *
     * @param txnId identifier of the transaction to be aborted
     */
    void abortOp(Long txnId);

    /**
     * Log the <code>Uuid</code> that identifies the space as a whole.
     * @see Recover#recoverUuid
     * @param uuid The <code>Uuid</code> to be stored.
     */
    void uuidOp(Uuid uuid);
}
