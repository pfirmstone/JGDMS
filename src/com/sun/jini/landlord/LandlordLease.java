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
package com.sun.jini.landlord;

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

/**
 * Basic implementation of <code>net.jini.core.lease.Lease</code> that works
 * with the the Landlord protocol.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see Landlord
 * @since 2.0
 */
public class LandlordLease extends AbstractLease implements ReferentUuid {
    static final long serialVersionUID = 2L;

    /**
     * A universally unique ID that identifies the lease this object
     * represents.
     * @serial 
     */
    final private Uuid cookie;

    /**
     * Owner of the resource associated with this lease.
     * @serial
     */
    final private Landlord landlord;

    /**
     * The <code>Uuid</code> of the <code>landlord</code>. Used
     * to determine if two leases can be batched together.
     * @serial
     */
    final private Uuid landlordUuid;

    /**
     * Create a new <code>LandlordLease</code>.
     * @param cookie a <code>Uuid</code> that universally and uniquely
     *                 identifies the lease this object is to be a proxy for
     * @param landlord <code>Landlord</code> object that will be used to
     *                 communicate renew and cancel requests to the granter
     *                 of the lease
     * @param landlordUuid a universally unique id that has been
     *                 assigned to the server granting of the lease.
     *                 Ideally the <code>Uuid</code> {@link
     *                 ReferentUuid#getReferentUuid landlord.getUuid} would
     *                 return if <code>landlord</code> implemented
     *                 {@link ReferentUuid}. Used to determine when
     *                 two leases can be batched together.
     * @param expiration the initial expiration time of the lease in
     *                 milliseconds since the beginning of the epoch
     * @throws NullPointerException if <code>landlord</code>, 
     *                 <code>landlordUuid</code> or <code>cookie</code>
     *                 is null  
     */
    public LandlordLease(Uuid cookie, Landlord landlord, Uuid landlordUuid,
			 long expiration)
    {
	super(expiration);

	if (cookie == null) 
	    throw new NullPointerException("Can't create a LandlordLease " +
					   "with a null cookie");

	if (landlord == null)
	    throw new NullPointerException("Can't create a LandlordLease " +
					   "with a null landlord");

	if (landlordUuid == null)
	    throw new NullPointerException("Can't create a LandlordLease " +
					   "with a null landlordUuid");

	this.cookie   = cookie;
	this.landlord = landlord;
	this.landlordUuid = landlordUuid;
    }

    // Implementation of the Lease interface

    // purposefully inherit doc comment from supertype
    public void cancel() throws UnknownLeaseException, RemoteException {
	landlord.cancel(cookie);
    }

    // purposefully inherit doc comment from supertype
    protected long doRenew(long renewDuration)
	 throws LeaseDeniedException, UnknownLeaseException, RemoteException
    {
	if (renewDuration < 0 && !(renewDuration == Lease.FOREVER ||
				   renewDuration == Lease.ANY))
	{
	    throw new IllegalArgumentException("Lease renewal: " +
	        "Asked for a negative duration");
	}
	return landlord.renew(cookie, renewDuration);
    }

    // purposefully inherit doc comment from supertype
    public Uuid getReferentUuid() {
	return cookie;
    }

    // purposefully inherit doc comment from supertype
    public boolean equals(Object other) {
	return ReferentUuids.compare(this, other);

	// Note, we do not include the expiration in the equality test.
	// If the lease is copied and ether the copy or the original
	// is renewed they are conceptually the same because they
	// still represent the same claim on the same resource
	// --however their expiration will be different
    }

    // inherit doc comment
    public boolean canBatch(Lease lease) {
	if (lease instanceof LandlordLease) {
  	    return landlordUuid.equals(((LandlordLease)lease).landlordUuid);
	}

       	return false;
    }

    /** Return the landlord. */
    Landlord landlord() {
	return landlord;
    }

    /** Return the landlord's Uuid. */
    Uuid landlordUuid() {
	return landlordUuid;
    }

    /** Return the cookie. */
    Uuid cookie() {
	return cookie;
    }

    /** Set the expiration. */
    void setExpiration(long expiration) {
	this.expiration = expiration;
    }

    // inherit doc comment
    public LeaseMap createLeaseMap(long duration) {
	return new LandlordLeaseMap(landlord, landlordUuid, this, duration);
    }

    // purposefully inherit doc comment from supertype
    public int hashCode() {
	return cookie.hashCode();
    }

    // purposefully inherit doc comment from supertype
    public String toString() {
	return "LandlordLease:" + cookie + " landlord:" + landlord +
	    " landlordUuid:" + landlordUuid + " " + super.toString();
    }

    /** Read this object back validating state.*/
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();

	if (cookie == null)
	    throw new InvalidObjectException("null cookie reference");

	if (landlord == null)
	    throw new InvalidObjectException("null landlord reference");

	if (landlordUuid == null)
	    throw new InvalidObjectException("null landlordUuid reference");
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
