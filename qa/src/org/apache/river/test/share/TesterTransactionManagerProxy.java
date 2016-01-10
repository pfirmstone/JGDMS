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


import java.io.IOException;
import java.io.Serializable;
import java.rmi.RemoteException;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.transaction.CannotAbortException;
import net.jini.core.transaction.CannotCommitException;
import net.jini.core.transaction.CannotJoinException;
import net.jini.core.transaction.TimeoutExpiredException;
import net.jini.core.transaction.UnknownTransactionException;
import net.jini.core.transaction.server.CrashCountException;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.transaction.server.TransactionParticipant;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * This class is a proxy to backend servers for simulations of activatable
 * lookup services that implement the LookupSimulator interface.
 */
@AtomicSerial
public class TesterTransactionManagerProxy implements TransactionManager, 
						      Serializable 
{
    private static final long serialVersionUID = 7327572992370001498L;
    
    final TransactionManager server;
    int sid;

    static TesterTransactionManagerProxy getInstance(TransactionManager server, int sid) {
	return (server instanceof RemoteMethodControl) ?
	    new TesterTransactionManagerConstrainableProxy(server, null, sid) :
	    new TesterTransactionManagerProxy(server, sid);
    }

    /** Simple constructor. */
    public TesterTransactionManagerProxy(TransactionManager server, int sid) {
        this.server = server;
	this.sid = sid;
    }

    TesterTransactionManagerProxy(GetArg arg) throws IOException  {
	this((TransactionManager)arg.get("server", null), arg.get("sid", 0));
    }
    
    public void commit(long id, long timeout)
            throws UnknownTransactionException, CannotCommitException,
            RemoteException, TimeoutExpiredException {
        server.commit(id, timeout);
    }

    public void commit(long id)
            throws UnknownTransactionException, CannotCommitException,
            RemoteException {
        server.commit(id);
    }

    public void abort(long id, long timeout)
            throws UnknownTransactionException, CannotAbortException,
            RemoteException, TimeoutExpiredException {
        server.abort(id, timeout);
    }

    public void abort(long id)
            throws UnknownTransactionException, CannotAbortException,
            RemoteException {
        server.abort(id);
    }

    public TransactionManager.Created create(long leaseTime) 
	throws LeaseDeniedException, 
	       RemoteException 
    {
        return server.create(leaseTime);
    }

    public int getState(long id)
            throws UnknownTransactionException, RemoteException {
        return server.getState(id);
    }

    public void join(long id, TransactionParticipant part, long crashCnt)
            throws UnknownTransactionException, CannotJoinException,
            CrashCountException, RemoteException {
        server.join(id, part, crashCnt);
    }
    /** Proxies for servers with the same serviceID are considered equal. */
    public boolean equals(Object obj) {
	return (obj instanceof TesterTransactionManagerProxy &&
		sid == (((TesterTransactionManagerProxy)obj).sid));
    }//end equals

    public int hashCode() {
	return sid;
    }

}//end class LookupSimulatorProxy
