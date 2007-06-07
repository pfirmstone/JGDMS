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

import java.io.ObjectInputStream;
import java.io.Serializable;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.rmi.RemoteException;

import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.UnknownTransactionException;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.transaction.server.TransactionParticipant;

import net.jini.id.Uuid;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;

/**
 * Object Outrigger hands to transaction managers on join.
 * Passes through <code>TransactionParticipant</code> calls
 * to the inner proxy and provides implementations of <code>equals</code>
 * and <code>hashCode</code> that do reference equality.
 *
 * @author Sun Microsystems, Inc.
 */
class ParticipantProxy implements TransactionParticipant, ReferentUuid,
				  Serializable
{
    static final long serialVersionUID = 1L;

    /**
     * The remote server this proxy works with.
     * Package protected so it can be read by subclasses and proxy verifier.
     * @serial
     */
    final TransactionParticipant space;

    /** 
     * The <code>Uuid</code> that identifies the space this proxy is for.
     * Package protected so it can be read by subclasses and proxy verifier.
     * @serial
     */
    final Uuid spaceUuid;

    /**
     * Create a new <code>ParticipantProxy</code> for the given space.
     * @param space The an inner proxy that implements 
     *              <code>TransactionParticipant</code> for the 
     *              space.
     * @param spaceUuid The universally unique ID for the
     *              space
     * @throws NullPointerException if <code>space</code> or
     *         <code>spaceUuid</code> is <code>null</code>.
     */
    ParticipantProxy(TransactionParticipant space, Uuid spaceUuid) {
	if (space == null)
	    throw new NullPointerException("space must be non-null");

	if (spaceUuid == null) 
	    throw new NullPointerException("spaceUuid must be non-null");

	this.space = space;
	this.spaceUuid = spaceUuid;
    }

    /**
     * Read this object back and validate state.
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();

	if (space == null) 
	    throw new InvalidObjectException("null server reference");
	    
	if (spaceUuid == null)
	    throw new InvalidObjectException("null Uuid");
    }

    /** 
     * We should always have data in the stream, if this method
     * gets called there is something wrong.
     */
    private void readObjectNoData() throws InvalidObjectException {
	throw new 
	    InvalidObjectException("SpaceProxy should always have data");
    }

    public String toString() {
	return getClass().getName() + " for " + spaceUuid + 
	    " (through " + space + ")";
    }

    public boolean equals(Object other) {
	return ReferentUuids.compare(this, other);
    }

    public int hashCode() {
	return spaceUuid.hashCode();
    }

    public Uuid getReferentUuid() {
	return spaceUuid;
    }

    public int prepare(TransactionManager mgr, long id)
	throws UnknownTransactionException, RemoteException
    {
	return space.prepare(mgr, id);
    }

    public void commit(TransactionManager mgr, long id)
	throws UnknownTransactionException, RemoteException
    {
	space.commit(mgr, id);
    }

    public void abort(TransactionManager mgr, long id)
	throws UnknownTransactionException, RemoteException
    {
	space.abort(mgr, id);
    }

    public int prepareAndCommit(TransactionManager mgr, long id)
	throws UnknownTransactionException, RemoteException
    {
	return space.prepareAndCommit(mgr, id);
    }
}
