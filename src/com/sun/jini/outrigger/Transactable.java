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

/**
 * This interface is implemented by objects that represent operations
 * undertaken under a transaction.  These objects are governed by
 * a <code>TransactableMgr</code> object that manages the overall
 * transaction's state in this space.  The set of <code>Transactable</code>
 * objects managed by a <code>TransactableMgr</code> object constitute
 * the set of operations made under the transaction.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see TransactableMgr
 */
interface Transactable
    extends net.jini.core.transaction.server.TransactionConstants
{
    /**
     * Prepare to commit this object's part of the transaction.  Return
     * the prepare's status.
     */
    //      * @see TransactableMgr#prepare ??
    int prepare(TransactableMgr mgr, OutriggerServerImpl space);

    /**
     * Commit this object's part of the transaction.  The
     * <code>space</code> is the <code>OutriggerServerImpl</code> on
     * which the operation happens -- some commit operations have
     * space-wide side effects (for example, a commit of a
     * <code>write</code> operation can cause event notifications for
     * clients registered under the transaction's parent).
     */
    void commit(TransactableMgr mgr, OutriggerServerImpl space);

    /**
     * Abort this object's part of the transaction.
     */
    void abort(TransactableMgr mgr, OutriggerServerImpl space);
}
