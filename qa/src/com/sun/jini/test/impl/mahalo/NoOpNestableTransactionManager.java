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
package com.sun.jini.test.impl.mahalo;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.rmi.RemoteException;

import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import com.sun.jini.lease.AbstractLease;
import net.jini.core.transaction.server.NestableTransactionManager;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.transaction.CannotAbortException;
import net.jini.core.transaction.CannotCommitException;
import net.jini.core.transaction.CannotJoinException;
import net.jini.core.transaction.CannotNestException;
import net.jini.core.transaction.TimeoutExpiredException;
import net.jini.core.transaction.TransactionException;
import net.jini.core.transaction.UnknownTransactionException;
import net.jini.core.transaction.server.TransactionParticipant;
import net.jini.core.transaction.server.CrashCountException;

/**
 * No-op implementation of 
 * <code> net.jini.core.transaction.server.NestableTransactionManager</code>.
 */
public class NoOpNestableTransactionManager 
    implements NestableTransactionManager 
{
    /**
     * Create a new <code>NoOpNestableTransactionManager</code>.
     */
    public NoOpNestableTransactionManager()
    {
    }
    
    public TransactionManager.Created create(
        NestableTransactionManager parentMgr,
	long parentID, long lease)
        throws UnknownTransactionException, CannotJoinException,
	       LeaseDeniedException, RemoteException
    {
        return new TransactionManager.Created(
            1000L,
            NestableTransactionCreatedToStringTest.noOpLease);
    }
    
     public void promote(long id, TransactionParticipant[] parts,
		 long[] crashCounts, TransactionParticipant drop)
       	throws UnknownTransactionException, CannotJoinException,
	       CrashCountException, RemoteException
     {
         // do nothing
     }
     
    public TransactionManager.Created create(long lease) 
        throws LeaseDeniedException, RemoteException
    {
        return new TransactionManager.Created(
            2000L,
            NestableTransactionCreatedToStringTest.noOpLease);
    }
 
    public void join(long id, TransactionParticipant part, long crashCount)
       	throws UnknownTransactionException, CannotJoinException,
	       CrashCountException, RemoteException
    {
        // do nothing
    }

    public int getState(long id) throws UnknownTransactionException, RemoteException
    {
        return 0;
    }

    public void commit(long id)
	throws UnknownTransactionException, CannotCommitException,
	       RemoteException
    {
        //do nothing
    }

    public void commit(long id, long waitFor)
        throws UnknownTransactionException, CannotCommitException,
	       TimeoutExpiredException, RemoteException
    {
        //do nothing
    }

    public void abort(long id)
	throws UnknownTransactionException, CannotAbortException,
	       RemoteException
    {
        // do nothing
    }

    public void abort(long id, long waitFor)
	throws UnknownTransactionException, CannotAbortException,
	       TimeoutExpiredException, RemoteException
    {
        // do nothing
    }
}
