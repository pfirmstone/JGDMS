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
import java.rmi.Remote;
import java.rmi.RemoteException;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.LeaseMapException;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.security.proxytrust.ProxyTrust;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Interface that grators of test leases implement so the test lease proxy
 * objects can communicate back to the grantor
 */
public interface LeaseBackEnd extends Remote, ProxyTrust {
    /**
     * Called by the lease when its <code>renew</code> method is called.
     *
     * @param id The id of this lease, used by the grantor to identify it
     * @param extension
     *               The duration argument passed to the
     *               <code>Lease.renew()</code> call
     * @return If the method return a <code>Long</code> this is the new
     *         expiration of the lease, if it returns a <code>Throwable</code>
     *         that object should be thrown.  If the the value is
     *         a <code>Throwable</code> it should be and <code>Error</code>,
     *         <code>RuntimeException</code>, or <code>RemoteException</code>
     */
    public Object renew(int id, long extension)
	throws LeaseDeniedException, UnknownLeaseException, RemoteException;

    /**
     * Called by the lease when its <code>cancel</code> method is called.
     *
     * @param id The id of this lease, used by the grantor to identify it
     * @return If this method returns a non-<code>null</code> value that 
     *         the caller should throw that object, other wise the caller
     *         should return normally.  If the the value is
     *         non-<code>null</code> it should be and <code>Error</code>,
     *         <code>RuntimeException</code>, or <code>RemoteException</code>.
     */
    public Throwable cancel(int id)
	throws UnknownLeaseException, RemoteException;

    /**
     * Called by the lease map when its <code>renewAll</code> method is called.
     * @param ids The ids of this leases, used by the grantor to identify them
     * @param extensions
     *               The duration argument for each lease from the map
     * @return If the method returns a <code>RenewResults</code> object the
     * object should be used to update the leases in map and/or throw
     * an appropriate <code>LeaseMapException</code>.  If the returned value
     * is a <code>Throwable</code> that object should be thrown.  If
     * the the value is <code>Throwable</code> it should be and
     * <code>Error</code>, <code>RuntimeException</code>, or
     * <code>RemoteException</code>.
     */
    public Object renewAll(int[] ids, long[] extensions)
	throws RemoteException;
       
    /**
     * Called by the lease map when its <code>cancelAll</code> method is called.
     * @param ids The ids of this leases, used by the grantor to identify them
     * @return If this method returns a non-<code>null</code> value that 
     *         the caller should throw that object, other wise the caller
     *         should return normally.  If the the value is
     *         non-<code>null</code> it should be and <code>Error</code>,
     *         <code>RuntimeException</code>, or <code>RemoteException</code>
     */
    public Throwable cancelAll(int[] ids)
	throws LeaseMapException, RemoteException;

    @AtomicSerial
    class RenewResults implements java.io.Serializable {

	/**
	 * For each id passed to <code>renewAll</code>,
	 * <code>granted[i]</code> is the granted lease time, or -1 if the
	 * renewal for that least generated an exception.  If there was
	 * an exception, the exception is held in <code>denied</code>.
	 *
	 * @see #denied
	 * @serial
	 */
	public long[] granted;

	/**
	 * The <code>i</code><sup><i>th</i></sup> -1 in <code>granted</code>
	 * was denied because of <code>denied[i]</code>.  If nothing was denied,
	 * this field is <code>null</code>.
	 *
	 * @serial
	 */
	public Exception[] denied;

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
	 * @param granted	The value for the field <code>granted</code>
	 * @param denied	The value for the field <code>denied</code>
	 */
	public RenewResults(long[] granted, Exception[] denied) {
	    this.granted = granted;
	    this.denied = denied;
	}
	
	public RenewResults(GetArg arg) throws IOException{
	    this(arg.get("granted", null, long[].class),
		 arg.get("denied", null, Exception[].class));
	}
    }
}

