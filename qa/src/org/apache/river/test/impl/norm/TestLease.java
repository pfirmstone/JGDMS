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
package org.apache.river.test.impl.norm;

import java.io.IOException;
import java.rmi.RemoteException;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.export.ProxyAccessor;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Lease class use by renwal service tests when they don't want to use
 * <code>LocalLease</code>.
 */
@AtomicSerial
public class TestLease extends OurAbstractLease implements ProxyAccessor {
    /** 
     * id of the lease
     * @serial
     */
    final private int id;

    /**
     * Owner of lease
     */
    final private LeaseBackEnd home;

    /**
     * Create a new lease.
     * @param home     <code>LeaeBackEnd</code> object that will be used to
     *                 communicate renew and cancel requests to the granter
     *                 of the lease.
     * @param id       An <code>int</code> that <code>home</code> can use
     *                 to identify the leased resource.
     * @param expiration The initial expiration of the lease.
     */
    public TestLease(int id, LeaseBackEnd home, long expiration) {
	super(expiration);
	this.id   = id;
	this.home = home;
    }
    
    public TestLease(GetArg arg) throws IOException, ClassNotFoundException{
	super(check(arg));
	id = arg.get("id", 0);
	home = arg.get("home", null, LeaseBackEnd.class);
    }
    
    private static GetArg check(GetArg arg) throws IOException, ClassNotFoundException{
	arg.get("id", 0);
	arg.get("home", null, LeaseBackEnd.class);
	return arg;
    }

    // Implementation of the Lease interface

    // purposefully inherit doc comment from supertype
    public void cancel() throws UnknownLeaseException, RemoteException {
	Throwable t = home.cancel(id);
	throwIt(t);	    
    }

    // purposefully inherit doc comment from supertype
    protected long doRenew(long renewDuration)
	 throws LeaseDeniedException, UnknownLeaseException, RemoteException
    {
	Object rslt = home.renew(id, renewDuration);
	if (rslt instanceof Long)
	    return ((Long)rslt).longValue();
	throwIt(rslt);
	return 0;
    }

    static void throwIt(Object t) throws RemoteException {
	if (t == null) return;

	if (t instanceof RemoteException) {
	    throw (RemoteException)t;
	} else if (t instanceof Error) {
	    throw (Error)t;
	} else if (t instanceof RuntimeException) {
	    throw (RuntimeException)t;
	}
    }

    // purposefully inherit doc comment from supertype
    public boolean equals(Object other) {
	// Note, we do not include the expiration in the equality test.
	// If the lease is copied and ether the copy or the original
	// is renewed they are conceptually the same because they
	// still represent the same claim on the same resource
	// --however their expiration will be different

	if (other instanceof TestLease) {
	    TestLease that = (TestLease)other;
	    return (id == that.id) &&
		home.equals(that.home);
	}

	return false;
    }

    // inherit doc comment
    public boolean canBatch(Lease lease) {
	if (lease instanceof TestLease)
	    return home.equals(((TestLease)lease).home);
	return false;
    }

    /** Return the home. */
    LeaseBackEnd home() {
	return home;
    }

    /** Return the id. */
    int id() {
	return id;
    }

    // inherit doc comment
    public LeaseMap createLeaseMap(long duration) {
	return new TestLeaseMap(home, this, duration);
    }

    // purposefully inherit doc comment from supertype
    public int hashCode() {
	return  id ^ home.hashCode();
    }

    // purposefully inherit doc comment from supertype
    public String toString() {
	return "TestLease:" + id + " home:" + home + super.toString();
    }

    /** Set the expiration. */
    void setExpiration(long expiration) {
	this.expiration = expiration;
    }

    @Override
    public Object getProxy() {
	return home;
    }
}



       
