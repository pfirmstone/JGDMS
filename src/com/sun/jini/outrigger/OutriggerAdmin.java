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
 
import com.sun.jini.admin.DestroyAdmin;
import net.jini.id.Uuid;

import java.rmi.Remote;
import java.rmi.RemoteException;

import net.jini.admin.JoinAdmin;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import net.jini.space.JavaSpace;

/**
 * The interface that is used by the <code>AdminProxy</code> to talk
 * to the server.  In other words, this is the server's analog to the
 * <code>JavaSpaceAdmin</code> interface.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see JavaSpaceAdmin
 */
// @see OutriggerServerImpl#AdminProxy
interface OutriggerAdmin  extends Remote, DestroyAdmin, JoinAdmin {
    /** Return the space administered by this object. */
    JavaSpace space() throws RemoteException;
    
    /**
     * Return the remote iterator object needed by
     * <code>JavaSpaceAdmin.contents</code>.
     */
    Uuid contents(EntryRep tmpl, Transaction txn)
	throws TransactionException, RemoteException;

    /** 
     * Fetch up to <code>max</code> <code>EntryRep</code> objects from
     * the specified iteration.
     * 
     * @param iterationUuid The <code>Uuid</code> of the iteration
     *            to fetch entries from.
     * @param max Advice on the number of entries to return
     * @param entryUuid <code>Uuid</code> of the last entry received by the
     *            caller.  If this does not match the ID of the last
     *            entry sent by the iterator will re-send that last
     *            batch in place of a new batch.  May be
     *            <code>null</code> in which case a new batch will be
     *            sent.  The first call to <code>next()</code> should
     *            have <code>id</code> set to <code>null</code>
     */
    EntryRep[] nextReps(Uuid iterationUuid, int max, 
			Uuid entryUuid) 
	throws RemoteException;

    /** 
     * Delete the given entry if the given iteration is still 
     * valid and the entry was retured by the last call to 
     * <code>nextReps</code>.
     * @param iterationUuid The <code>Uuid</code> of a valid 
     *            iteration.
     * @param entryUuid the <code>Uuid</code> of the entry
     *            to be deleted.
     */
    void delete(Uuid iterationUuid, Uuid entryUuid) throws RemoteException;

    /** 
     * Forget about the indicated iteration 
     * @param iterationUuid The <code>Uuid</code> iteration to close.
     */
    void close(Uuid iterationUuid) throws RemoteException;
}

