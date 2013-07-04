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

import com.sun.jini.lease.AbstractLease;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.rmi.RemoteException;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lease.UnknownLeaseException;

/**
 * No-op implementation of <code>net.jini.core.lease.Lease</code> that works
 * with the the Landlord protocol.
 */
public class NoOpLease extends AbstractLease {
    static final long serialVersionUID = 1L;

    /**
     * Create a new <code>NoOpLease</code>.
     * @param expiration the initial expiration time of the lease in
     *                 milliseconds since the beginning of the epoch
     */
    public NoOpLease(long expiration)
    {
	super(expiration);
    }

    // Implementation of the Lease interface

    // purposefully inherit doc comment from supertype
    public void cancel() throws UnknownLeaseException, RemoteException {
	
    }

    // purposefully inherit doc comment from supertype
    protected long doRenew(long renewDuration)
	 throws LeaseDeniedException, UnknownLeaseException, RemoteException
    {
	return renewDuration;
    }

    // inherit doc comment
    public boolean canBatch(Lease lease) {
       	return false;
    }

    /** Set the expiration. */
    void setExpiration(long expiration) {
    }

    // inherit doc comment
    public LeaseMap createLeaseMap(long duration) {
	return new NoOpLeaseMap(this, duration);
    }

    // purposefully inherit doc comment from supertype
    public String toString() {
	return "NoOpLease:" + super.toString();
    }

    /** Read this object back validating state.*/
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
    }

    /** 
     * We should always have data in the stream, if this method
     * gets called there is something wrong.
     */
    private void readObjectNoData() throws InvalidObjectException {
	throw new 
	    InvalidObjectException("LandlordLease should always have data");
    }
}
