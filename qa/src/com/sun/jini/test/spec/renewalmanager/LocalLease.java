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
package com.sun.jini.test.spec.renewalmanager;

import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;

import java.rmi.RemoteException;

import java.util.Map;
import java.util.Set;
import java.util.Iterator;

import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lease.LeaseMapException;
import net.jini.core.lease.LeaseException;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;

import com.sun.jini.test.share.OurAbstractLeaseMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A lease implementation that is completely local for use in some of the 
 * QA test for the LeaseRenewalManager
 */
class LocalLease implements Lease {
    /** 
     * Expiration time of the lease 
     */
    private long expiration;

    /** 
     * Max length we will grant a renewal for
     */
    private long renewLimit;

    /** 
     * Two <code>LocalLeases</code> with the same bundle value can 
     * be batched together
     */
    private long bundle;

    /**
     * Serialization format for the expiration.
     */
    protected int serialFormat = Lease.DURATION;

    /**
     * ID for this lease
     */
    protected long id;

    /**
     * Object to notify if there is a failure
     */
    private Object notifyOnFailure;

    /**
     * Set to a non-<code>null</code> if the Lease Renewal Manager
     * screws up
     */
    private String rslt = null;

    /**
     * Create a local lease with the specified initial expiration time 
     * @param initExp    Initial expiration time
     * @param renewLimit Limit on long each renewal request can be for
     * @param bundle     Two <code>LocalLeases</code> with the same bundle
     *                   value can be batched together
     * @param id         Uniuque ID for this lease
     * @param notifyOnFailure Object to notify if we detect an early failure
     */
    LocalLease(long initExp, long renewLimit, long bundle, long id,
	       Object notifyOnFailure)
    {
	expiration = initExp;
	this.renewLimit = renewLimit;
	this.bundle = bundle;
	this.id = id;
	this.notifyOnFailure = notifyOnFailure;
    }

    /**
     * Set rslt string if it is not already set
     */
    protected void setRsltIfNeeded(String newResult) {	
	synchronized (this) {
	    if (rslt == null) {
		rslt = newResult;
	    }
	}

	if (notifyOnFailure == null)
	    return;

	synchronized (notifyOnFailure) {
	    notifyOnFailure.notifyAll();
	}
    }

    /**
     * Return <code>null</code> if the lease assocated with this 
     * owner had all the right things and an error message otherwise.
     */
    public synchronized String didPass() {
	return rslt;
    }

    /**
     * Get the result
     */
    protected synchronized String getRslt() {
	return rslt;
    }

    // Inherit java doc from super type
    public synchronized long getExpiration() {
	return expiration;
    }

    protected synchronized void setExpiration(long exp) {
	expiration = exp;
    }

    // Inherit java doc from super type
    public synchronized void cancel() throws RemoteException, UnknownLeaseException {
        try {
            // Simulate blocking remote communications
            Thread.sleep(10000L);
        } catch (InterruptedException ex) {
            RemoteException e = new RemoteException();
            e.initCause(ex);
            throw e;
        }
    }

    protected synchronized void renewWork(long duration) 
	throws RemoteException, LeaseDeniedException, UnknownLeaseException
    {
	if (duration > renewLimit)
	    duration = renewLimit;

	expiration = System.currentTimeMillis() + duration;
	// Check for overflow
	if (expiration < 0) 
	    expiration = Long.MAX_VALUE;
    }

    // Inherit java doc from super type
    public void renew(long duration) 
	throws RemoteException, LeaseDeniedException, UnknownLeaseException
    {
	renewWork(duration);
	// Simulate blocking remote communications
	Thread.yield();
    }

    // Inherit java doc from super type
    public synchronized void setSerialFormat(int format) {
	if (format != Lease.DURATION && format != Lease.ABSOLUTE)
	    throw new IllegalArgumentException("invalid serial format");
	serialFormat = format;
    }

    // Inherit java doc from super type
    public synchronized int getSerialFormat() {
	return serialFormat;
    }

    // Inherit java doc from super type
    public LeaseMap createLeaseMap(long duration) {
	return new LocalLeaseMap(bundle, this, duration);
    }

    // Inherit java doc from super type
    public boolean canBatch(Lease lease) {
	if (lease instanceof LocalLease) {
	    final LocalLease other = (LocalLease)lease;
	    return other.bundle == bundle;
	}

	return false;
    }

    /**
     * Data a long, which is the absolute expiration if serialFormat
     * is ABSOLUTE, or the relative duration if serialFormat is DURATION
     */
    private synchronized void writeObject(ObjectOutputStream stream)
	throws IOException 
    {
	stream.defaultWriteObject();
	stream.writeLong(serialFormat == Lease.ABSOLUTE ?
			 expiration : expiration - System.currentTimeMillis());
    }

    /**
     * If serialFormat is DURATION, add the current time to the expiration,
     * to make it absolute (and if the result of the addition is negative,
     * correct the overflow by resetting the expiration to Long.MAX_VALUE).
     */
    private synchronized void readObject(ObjectInputStream stream)
	throws IOException, ClassNotFoundException
    {
	stream.defaultReadObject();
	expiration = stream.readLong();
	if (serialFormat == Lease.DURATION) {
	    expiration += System.currentTimeMillis();

	    // We added two positive numbers, so if the result is negative
	    // we must have overflowed, truncate to Long.MAX_VALUE
	    if (expiration < 0) 
		expiration = Long.MAX_VALUE;
	}
    }


    // purposefully inherit doc comment from supertype
    public boolean equals(Object other) {
	// Note, we do not include the expiration in the equality test.
	// If the lease is copied and ether the copy or the original
	// is renewed they are conceptually the same because they
	// still represent the same claim on the same resource
	// --however their expiration will be different

	if (other instanceof LocalLease) {
	    final LocalLease that = (LocalLease)other;
	    return that.id == id && that.bundle == bundle;
	}

	return false;
    }

    // purposefully inherit doc comment from supertype
    public int hashCode() {
	return (int)id;
    }

    private static class LocalLeaseMap extends OurAbstractLeaseMap {
	/** 
	 * Two <code>LocalLeases</code> with the same bundle value can 
	 * be batched together
	 */
	final private long bundle;

	/**
	 * Simple constructor
	 * @param bundle Local leases of what bundle can be placed in this
	 *               map
	 * @param first  First lease to be placed in map
	 * @param duration Requested duration for first
	 */
	private LocalLeaseMap(long bundle, LocalLease first, long duration) {
	    super(first, duration);
	    this.bundle = bundle;
	}

	// purposefully inherit doc comment from supertype
	public boolean canContainKey(Object key) {
	    if (key instanceof LocalLease) {
		final LocalLease l = (LocalLease)key;
		return bundle == l.bundle;
	    }
	    return false;
	}

	// purposefully inherit doc comment from supertype
	public void renewAll() throws LeaseMapException, RemoteException {
	    final Set leases = map.entrySet();
	    Map exceptionMap = null;
	    for (Iterator i=leases.iterator(); i.hasNext(); ) {
		final Map.Entry e = (Map.Entry)i.next();
		final LocalLease l = (LocalLease)e.getKey();
		final long  d = ((Long)e.getValue()).longValue();
		try {
		    l.renewWork(d);
		} catch (LeaseException f) {
		    if (exceptionMap == null) 
			exceptionMap = new java.util.HashMap();

		    exceptionMap.put(l, f);
		    i.remove();
		}
	    }

	    // Simulate blocking remote communications
	    Thread.yield();

	    if (exceptionMap != null) 
		throw new LeaseMapException("Failure", exceptionMap);
	}

	// purposefully inherit doc comment from supertype
	public void cancelAll() throws RemoteException, LeaseMapException {
	    final Set leases = map.keySet();
	    Map exceptionMap = null;
	    for (Iterator i=leases.iterator(); i.hasNext(); ) {
		final LocalLease l = (LocalLease)i.next();
		try {
		    l.cancel();
		} catch (LeaseException f) {
		    if (exceptionMap == null) 
			exceptionMap = new java.util.HashMap();

		    exceptionMap.put(l, f);
		    i.remove();
		}		    
	    }

	    // Simulate blocking remote communications
	    Thread.yield();
	    
	    if (exceptionMap != null) 
		throw new LeaseMapException("Failure", exceptionMap);
	}
    }
}
