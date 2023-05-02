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
package org.apache.river.landlord;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.Valid;

/** 
 * Interface that granters of leases must implement in order to work
 * with the <code>LandlordLease</code> implementation of the
 * <code>Lease</code> interface.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see net.jini.core.lease.Lease
 * @see LandlordLease 
 * @since 2.0
 */
public interface Landlord extends Remote {
    /**
     * Called by the lease when its {@link Lease#renew renew} method is called.
     * Renews the lease that is associated with the given <code>cookie</code>.
     *
     * @param cookie the <code>Uuid</code> associated with the lease who's 
     *               <code>renew</code> method was called
     * @param duration argument passed to the <code>renew</code> call
     * @return The new duration the lease should have 
     * @throws LeaseDeniedException if the landlord is unwilling to
     *         renew the lease
     * @throws UnknownLeaseException if landlord does not know about
     *         a lease with the specified <code>cookie</code>
     * @throws RemoteException if a communications failure occurs
     */
    public long renew(Uuid cookie, long duration)
	throws LeaseDeniedException, UnknownLeaseException, RemoteException;
       
    /**
     * Called by the lease when its {@link Lease#cancel cancel} method is 
     * called. Cancels the lease that is associated with the given
     * <code>cookie</code>.
     *
     * @param cookie the <code>Uuid</code> associated with the lease who's 
     *               <code>renew</code> method was called
     * @throws UnknownLeaseException if landlord does not know about
     *         a lease with the specified <code>cookie</code>
     * @throws RemoteException if a communications failure occurs
     */
    public void cancel(Uuid cookie)
	throws UnknownLeaseException, RemoteException;

    /**
     * Called by the lease map when its {@link LeaseMap#renewAll
     * renewAll} method is called.  Should renew the lease that is
     * associated with each element of <code>cookies</code>
     *
     * @param cookies an array of <code>Uuid</code>s, each universally and 
     *                uniquely identifying a lease granted by this
     *                <code>Landlord</code>
     * @param durations
     *                an array of longs, each representing an a
     *                duration in milliseconds that the client
     *                wants the lease associated with the <code>Uuid</code>
     *                from the corresponding element of
     *                <code>cookies</code> renewed for
     * @return A RenewResults object that contains the new
     *         duration of each lease that was successfully renewed or
     *         the exception encountered for each lease that could not
     *         be renewed
     * @throws RemoteException if a communications failure occurs
     */
    public RenewResults renewAll(Uuid[] cookies, long[] durations)
	throws RemoteException;
       
    /**
     * Called by the lease map when its {@link LeaseMap#cancelAll
     * cancelAll} method is called.  Should cancel the lease that is
     * associated with each element of <code>cookies</code>
     * 
     * This method is now default, because it is unsafe to return a Map
     * that has been serialized, without having its invariants checked during
     * deserialization.  A gadget attack could utilize the map to contain
     * other objects.
     * 
     * @param cookies an array of <code>Uuid</code>s, each universally and 
     *                uniquely identifying a lease granted by this
     *                <code>Landlord</code>
     * @return If all the leases specified in the <code>cookies</code>
     *         could be canceled return <code>null</code>.  Otherwise,
     *         return a <code>Map</code> that for each failed cancel
     *         attempt maps the corresponding cookie object to an
     *         exception describing the failure.  
     * @throws RemoteException if a communications failure occurs
     */
    public java.util.Map cancelAll(Uuid[] cookies) throws RemoteException; 

    /** 
     * Simple class that holds return values of
     * the {@link Landlord#renewAll Landlord.renewAll} method.
     * 
     * The API of this class has changed, in a non backward compatible manner
     * for security reasons.
     */
    @AtomicSerial
    public static class RenewResults implements java.io.Serializable {
	static final long serialVersionUID = 2L;
	/**
	 * For each cookie passed to {@link Landlord#renewAll renewAll},
	 * <code>granted[i]</code> is the granted lease time, or -1 if the
	 * renewal for that lease generated an exception.  If there was
	 * an exception, the exception is held in <code>denied</code>.
	 *
	 * @see #denied
	 * @serial
	 */
	private final long[] granted;

	/**
	 * The <code>i</code><sup><i>th</i></sup> -1 in <code>granted</code>
	 * was denied because of <code>denied[i]</code>.  If nothing was 
	 * denied, this field is <code>null</code>.
	 *
	 * @serial
	 */
	private final Exception[] denied;

	/**
	 * Create a <code>RenewResults</code> object setting the field
	 * <code>granted</code> to the passed value, and <code>denied</code>
	 * to <code>null</code>.
	 *
	 * @param granted	The value for the field <code>granted</code>
	 */
	public RenewResults(long[] granted) {
	    this(granted, null);
	}

	/**
	 * Create a <code>RenewResults</code> object setting the field
	 * <code>granted</code> and <code>denied</code> fields to the
	 * passed values.
	 *
	 * @param granted	the value for the field <code>granted</code>
	 * @param denied	the value for the field <code>denied</code>
	 */
	public RenewResults(long[] granted, Exception[] denied) {
	    this.granted = granted.clone();
	    this.denied = Valid.copy(denied);
	}
	
	public RenewResults(GetArg arg) throws IOException, ClassNotFoundException{
	    this(Valid.notNull(arg.get("granted", null, long[].class), "granted cannot be null"),
		arg.get("denied", null, Exception[].class));
	}
	
	/**
	 * For each cookie passed to {@link Landlord#renewAll renewAll},
	 * <code>getGranted(i)</code> is the granted lease time, or -1 if the
	 * renewal for that lease generated an exception.  If there was
	 * an exception, the exception is held in <code>denied</code>.
	 * 
	 * @param i the array element location corresponding to the cookie passed
	 * to to {@link Landlord#renewAll renewAll}
	 * @return the granted lease time  corresponding to index i of the
	 * cookie passed to {@link Landlord#renewAll renewAll}.
	 */
	public long getGranted(int i){
	    return granted[i];
	}
	
	/**
	 * For each cookie passed to {@link Landlord#renewAll renewAll},
	 * <code>getGranted(i)</code> is the granted lease time, or -1 if the
	 * renewal for that lease generated an exception.  If there was
	 * an exception, the exception is held in <code>getDenied(i)</code> 
	 * at index i, corresponding to the cookies array.
	 * 
	 * @param i the array element location corresponding to the cookie passed
	 * to to {@link Landlord#renewAll renewAll}
	 * @return the denied exception corresponding to the locations
	 * of i in the lease time array.
	 */
	public Exception getDenied(int i){
	    if (denied == null) return null;
	    return denied[i];
	}
	
	/**
	 * 
	 * @return true if no lease renewals were denied.
	 */
	public boolean noneDenied(){
	    return denied == null;
	}
    }
}
