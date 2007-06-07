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
package com.sun.jini.norm;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.event.RemoteEvent;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.io.MarshalledInstance;
import net.jini.lease.LeaseRenewalSet;
import net.jini.security.ProxyPreparer;

import com.sun.jini.lease.BasicRenewalFailureEvent;
import com.sun.jini.logging.Levels;
import com.sun.jini.norm.event.EventFactory;
import com.sun.jini.proxy.ConstrainableProxyUtil;

/**
 * Class that wraps client Leases.  Provides hooks for synchronization 
 * and data associated with each client lease while allowing us to 
 * use <code>LeaseRenewalManager</code>.
 * <p>
 * This code assumes most synchronization is being done at the set level,
 * this works because no lease is in more than one set at a given time.
 * The only place where additional synchronization is going on is in
 * renewals and the logging of renewals.
 *
 * @author Sun Microsystems, Inc.
 */
class ClientLeaseWrapper implements Lease, Serializable {
    private static final long serialVersionUID = 2;

    /** Logger for logging messages for this class */
    private static final Logger logger = Logger.getLogger("com.sun.jini.norm");

    /* Map for comparing lease constraints. */
    private static final Method[] leaseToLeaseMethods;
    static {
	try {
	    Method cancelMethod =
		Lease.class.getMethod("cancel", new Class[] { });
	    Method renewMethod =
		Lease.class.getMethod("renew", new Class[] { long.class });
	    leaseToLeaseMethods = new Method[] {
		cancelMethod, cancelMethod, renewMethod, renewMethod };
	} catch (NoSuchMethodException e) {
	    throw new NoSuchMethodError(e.getMessage());
	}
    }

    /**
     * Throwable thrown by the last renew attempt on the client lease.
     * <code>null</code> if the renew has not been called yet or
     * the last renewal call succeeded.
     * @serial
     */
    private Throwable lastFailure = null;

    /**
     * Client lease in marshalled form.
     * @serial
     */
    private MarshalledInstance marshalledClientLease;

    /**
     * Most current expiration time of client Lease that we know of	
     * @serial
     */
    private long clientLeaseExpiration;

    /**
     * Sequence number that uniquely identifies this wrapper
     * @serial
     */
    private long UID;

    /**
     * Membership expiration of this lease
     * @serial
     */
    private long membershipExpiration;

    /**
     * renewDuration of this lease
     * @serial
     */
    private long renewDuration;

    /**
     * The LeaseSet we belong to
     */
    private transient LeaseSet set;

    /**
     * Transient copy of unpacked lease, <code>null</code> if we could not
     * unpack or prepare the lease.
     */
    private transient Lease clientLease;

    /**
     * The proxy preparer to use to prepare a newly unmarshalled client lease,
     * or null if this instance was created using an already prepared client
     * lease, which is how instances are created initially.
     */
    private transient ProxyPreparer recoveredLeasePreparer;

    /** 
     * Flag used to check if lease has been persisted since last
     * renewal, <code>true</code> means it has not been
     */
    private transient boolean renewalPending = false;

    /**
     * Reference to list of leases that have been renewed, but not
     * persisted
     */
    private transient List renewedList;

    /**
     * Simple constructor
     * @param clientLease lease from client that is to be renewed
     * @param UID ID number for this wrapper unique for all wrappers in a given
     *		  server
     * @param renewedList list that wrapper should go on after renewing their
     *			  client lease is renewed
     * @param leaseSet	  the <code>LeaseSet</code> this lease is in
     * @param membershipDuration 
     *                    initial membership duration for this lease
     * @param renewDuration
     *                    initial membership expiration for the lease
     * @param now         the current time
     */
    ClientLeaseWrapper(Lease clientLease, long UID, List renewedList,
		       LeaseSet leaseSet, long membershipDuration,
		       long renewDuration, long now) 
	throws IOException
    {
	this.renewedList = renewedList;
	this.UID = UID;
	this.clientLease = clientLease;

	set = leaseSet;

	clientLeaseExpiration = clientLease.getExpiration();
	clientLease.setSerialFormat(Lease.ABSOLUTE);
	marshalledClientLease = new MarshalledInstance(clientLease);

	this.renewDuration = renewDuration;
	calcMembershipExpiration(membershipDuration, now);
    }

    /**
     * Given the current time and a membershipDuration set membershipExpiration
     * the correct value.
     */
    private void calcMembershipExpiration(long membershipDuration, long now) {
	if (membershipDuration == Lease.FOREVER) {
	    membershipExpiration = Lease.FOREVER;
	} else {
	    membershipExpiration = membershipDuration + now;
	    // Check for overflow
	    if (membershipExpiration < 0)
		membershipExpiration = Long.MAX_VALUE;
	}
    }


    /**
     * Update the membership expiration and renewDuration of this lease
     */
    void update(long membershipDuration, long renewDuration, long now) {
	this.renewDuration = renewDuration;
	calcMembershipExpiration(membershipDuration, now);
    }	

    /**
     * Return the membershipExpiration of this lease    
     */
    long getMembershipExpiration() {
	return membershipExpiration;
    }

    /**
     * Return the renewDuration of this lease    
     */
    long getRenewDuration() {
	return renewDuration;
    }

    /**
     * Atomically clear the renewalPending flag.
     */
    synchronized void clearRenewed() {
	renewalPending = false;
    }

    /**
     * Get a reference to the client lease, unmarshalling and preparing it if
     * necessary.  If it can't be unpacked or prepared, return null.
     */
    // $$$ this might want to be synchronized
    Lease getClientLease() {
	if (clientLease == null) {
	    Lease unmarshalledLease = null;
	    try {
		unmarshalledLease = (Lease) marshalledClientLease.get(false);
	    } catch (IOException e) {
		logger.log(Levels.HANDLED,
			   "Problem unmarshalling lease -- will retry later",
			   e);
	    } catch (ClassNotFoundException e) {
		logger.log(Levels.HANDLED,
			   "Problem unmarshalling lease -- will retry later",
			   e);
	    }
	    if (unmarshalledLease != null) {
		try {
		    clientLease = (Lease) recoveredLeasePreparer.prepareProxy(
			unmarshalledLease);
		} catch (RemoteException e) {
		    logger.log(Levels.HANDLED,
			       "Problem preparing lease -- will retry later",
			       e);
		} catch (SecurityException e) {
		    logger.log(Levels.HANDLED,
			       "Problem preparing lease -- will retry later",
			       e);
		}
	    }
	}
	return clientLease;
    }

    /**
     * Return the client lease in marshalled form.  If possible
     * marshal using the Lease.DURATION serialization format.
     * <p>
     * Assumes that no one else will be serializing the lease 
     * during this call.
     */
    MarshalledInstance getMarshalledClientLease() {
	final Lease cl = getClientLease();

	if (cl == null) {
	    // We are deformed, best we can do is return the (possibly
	    // stale) pre-marshalled lease we already have
	    return marshalledClientLease;
	} else {
	    try {
		cl.setSerialFormat(Lease.DURATION);
		return new MarshalledInstance(cl);
	    } catch (IOException e) {
		// Can't create a new MarshalledInstance, return the old one
		return marshalledClientLease;
	    } finally {
		cl.setSerialFormat(Lease.ABSOLUTE);
	    }
	}
    }

    // Inherit java doc from super type
    public boolean equals(Object that) {
	if (that instanceof ClientLeaseWrapper) {
	    return UID == ((ClientLeaseWrapper) that).UID;
	} else {
	    return false;
	}
    }

    // Inherit java doc from super type
    public int hashCode() {
	return (int) UID;
    }

    // Inherit java doc from super type
    public String toString() {
	return "CLW:" + UID + " exp:" + clientLeaseExpiration + " dexp:" + 
	    membershipExpiration + " dur:" + renewDuration + " failure:" +
	    lastFailure;
    }


    // Inherit java doc from super type
    public long getExpiration() {
	final Lease cl = getClientLease();
	if (cl == null) {
	    // $$$ Why is this synchronized?
	    synchronized (this) {		
		return clientLeaseExpiration;
	    }
	} else {
	    return cl.getExpiration();
	}
    }

    /**
     * Always throws <code>UnsupportedOperationException</code> since
     * a lease renewal service should never cancel a client lease.  
     */
    public void cancel() {
	throw new UnsupportedOperationException("ClientLeaseWrapper.cancel:" + 
	     "LRS should not being canceling client leases ");
    }

    /**
     * Return the exception (if any) that occured at the last renewal attempt
     */
    Throwable getLastFailure() {
	return lastFailure;
    }

    /**
     * Atomically test and set the renewalPending flag.
     * @return the state of the renewalPending flag before it was set
     */
    private synchronized boolean testAndSetRenewalPending() {
	final boolean result = renewalPending;
	renewalPending = true;
	return result;
    }

    /**
     * Called when a renewal thread changes the persistent state of the 
     * this wrapper.  If necessary places this object on the queue of 
     * wrappers to be persisted.
     */
    private void changed() {
	if (!testAndSetRenewalPending()) {
	    synchronized (renewedList) {
		renewedList.add(this);
		renewedList.notifyAll();
	    }
	}
    }

    /**
     * Called by ClientLeaseMapWrapper to see if the set associated with
     * this wrapper is still valid. Returns true if it still is
     */
    boolean ensureCurrent(long now) {
	return set.ensureCurrent(now);
    }

    /**
     * Sets lastFailure and adds the this to the renewed list so it 
     * so the change can be logged. Note this is only called from
     * from renew methods that are renewing the client lease associated
     * with this wrapper so additional synchronization should not be
     * necessary.
     */
    void failedRenewal(Throwable t) {
	lastFailure = t;
	changed();
    }

    /**
     * Log a successful lease renewalNote this is only called from
     * from renew methods that are renewing the client lease associated
     * with this wrapper so additional synchronization should not be
     * necessary.
     */
    void successfulRenewal() {
	lastFailure = null;

	// Update the marshalled version of the lease 
	// $$$ We could only do this in writeObject but then we would have
	// to make sure it had changed since the last serialization, the 
	// renewalPending flag almost does this but not quite (maybe it should 
	// and we don't have it quite right...maybe writeObject should
	// clear the  flag instead of explicit clearing it after writing
	// the clw out?).  Not clear doing it here is significantly less
	// efficient.
	try {
	    marshalledClientLease = new MarshalledInstance(clientLease);
	} catch (IOException e) {
	    // $$$ Besides printing a message drop this on the floor,
	    // exception in some pathological cases we are extremely
	    // unlikely to get this exception, further more this is really
	    // just a logging error, which our current policy is to drop.
	    // In the abstract it would be better to do this in writeObject
	    // since this really is an error while trying to persist the 
	    // state of this object, however, that solution would require
	    // more synchronization.  Lastly this method may go away if
	    // we decide to stop using the LRM.
	    logger.log(Level.WARNING,
		       "IOException while marshalling client lease " +
		       "after renewal",
		       e);
	}

	// synchronize in case long assignment is not atomic
	synchronized (this) {
	    clientLeaseExpiration = clientLease.getExpiration();
	}

	// If necessary notify rest of system that this lease has to 
	// be persisted
	changed();
    }

    // Inherit java doc from super type
    // This method assumes that only this thread is renewing this lease
    public void renew(long duration)
	throws LeaseDeniedException, UnknownLeaseException, RemoteException 
    {
	// Check to make sure the set has not expired
	if (!set.ensureCurrent(System.currentTimeMillis())) {
	    // The set has expired, throw an exception that will
	    // tell the failure logging thread to ignore this failure
	    throw LRMEventListener.EXPIRED_SET_EXCEPTION;

	    // Note, we don't call failedRenewal() because the set is
	    // dead so logging changes is pointless (besides, there
	    // is no change to log.)
	}
	
	// Do we have the real lease in unmarshalled form?
	if (clientLease == null) {
	    // try to unmarshal client lease
	    Lease unmarshalledLease;
	    try {
		unmarshalledLease = (Lease) marshalledClientLease.get(false);
	    } catch (IOException e) {
		UnmarshalException t = new UnmarshalException(
		    "ClientLeaseWrapper.renew:Could not unmarshal client " +
		    "lease for renewal", e);
		failedRenewal(t);
		throw t;
	    } catch (ClassNotFoundException e) {
		UnmarshalException t = new UnmarshalException(
		    "ClientLeaseWrapper.renew:Could not unmarshal client " +
		    "lease for renewal", e);
		failedRenewal(t);
		throw t;
	    }
	    // Try to prepare the client lease
	    try {
		clientLease = (Lease) recoveredLeasePreparer.prepareProxy(
		    unmarshalledLease);
	    } catch (RemoteException e) {
		failedRenewal(e);
		throw e;
	    } catch (SecurityException e) {
		failedRenewal(e);
		throw e;
	    }
	}

	// If we get here clientLease must be non-null

	// $$$ do we want to ensure that the lease on the set is
	// current here or just rely on the expiration thread.
	try {
	    clientLease.renew(duration);
	} catch (LeaseDeniedException e) {
	    failedRenewal(e);
	    throw e;
	} catch (UnknownLeaseException e) {
	    failedRenewal(e);
	    throw e;
	} catch (RemoteException e) {
	    failedRenewal(e);
	    throw e;
	} catch (RuntimeException e) {
	    failedRenewal(e);
	    throw e;
	} catch (Error e) {
	    failedRenewal(e);
	    throw e;
	}

	// If we get here we must have successfully renewed the lease
	successfulRenewal();
    }

    /**
     * Always throws <code>UnsupportedOperationException</code>.  The
     * <code>LeaseRenewalManager</code> should never call this method and 
     * norm always serializes the wrapper with absolute times.
     */
    public void setSerialFormat(int format) {
	throw new UnsupportedOperationException(
	     "ClientLeaseWrapper.setSerialFormat:" + 
	     "LRS should not be setting serial format through the wrapper");
    }


    /**
     * Always throws <code>UnsupportedOperationException</code>.  The
     * <code>LeaseRenewalManager</code> should never call this method and 
     * norm always serializes the wrapper with absolute times.
     */
    public int getSerialFormat() {
	throw new UnsupportedOperationException(
	     "ClientLeaseWrapper.setSerialFormat:" + 
	     "LRS should not be setting serial format through the wrapper");
    }

    // Inherit java doc from super type
    public LeaseMap createLeaseMap(long duration) {
	if (isDeformed()) {
	    return new DeformedClientLeaseMapWrapper(this, duration);
	} else {
	    return new ClientLeaseMapWrapper(this, duration);
	}
    }

    /**
     * Another lease can be batched with this one if it is a
     * ClientLeaseMapWrapper, if it is either a member of the same lease
     * renewal set or sets are not isolated, if neither lease is deformed, if
     * the leases either both have the same client constraints or do not
     * implement RemoteMethodControl, and if the underlying client leases can
     * be batched.
     */
    public boolean canBatch(Lease lease) {
	if (!(lease instanceof ClientLeaseWrapper)) 
	    return false;

	final ClientLeaseWrapper clw = (ClientLeaseWrapper) lease;
	
	if (set.isolateSets() && !set.equals(clw.set))
	    return false;

	if (isDeformed() || clw.isDeformed())
	    return false;

	Lease clientLease = getClientLease();
	Lease otherClientLease = clw.getClientLease();
	return sameConstraints(clientLease, otherClientLease) &&
	    clientLease.canBatch(otherClientLease);
    }

    /**
     * Returns true if the two leases both implement RemoteMethodControl and
     * have the same constraints for Lease methods, or both don't implement
     * RemoteMethodControl, else returns false.
     */
    private static boolean sameConstraints(Lease l1, Lease l2) {
	if (!(l1 instanceof RemoteMethodControl)) {
	    return !(l2 instanceof RemoteMethodControl);
	} else if (!(l2 instanceof RemoteMethodControl)) {
	    return false;
	} else {
	    return ConstrainableProxyUtil.equivalentConstraints(
		((RemoteMethodControl) l1).getConstraints(),
		((RemoteMethodControl) l2).getConstraints(),
		leaseToLeaseMethods);
	}
    }

    /**
     * After recovering a lease wrapper call this method before using
     * any other of the wrappers methods and before allocating any new
     * wrappers can not recover itself.
     * @param renewedList List that wrapper should go on after renewing their
     *	      client lease is renewed
     * @param generator ID generator being used to generate IDs client lease
     *	      wrappers
     * @param leaseSet the set this wrapper is associated with
     * @param recoveredLeasePreparer the proxy preparer to use to prepare
     *	      client leases recovered from persistent storage
     */
    void recoverTransient(List renewedList, UIDGenerator generator, 
			  LeaseSet leaseSet,
			  ProxyPreparer recoveredLeasePreparer) 
    {
	this.renewedList = renewedList;
	set = leaseSet;
	generator.inUse(UID);
	this.recoveredLeasePreparer = recoveredLeasePreparer;
	// Try to get the client lease unpacked
	getClientLease();
	// Add to set's lease table now that the transient state is
	// restored and we have tried unpacking once
	set.addToLeaseTable(this);
    }

    /**
     * Return true if the underlying client lease has not yet been deserialized
     */
    boolean isDeformed() {
	return getClientLease() == null;
    }

    /**
     * The <code>LeaseSet</code> this lease is in
     */
    LeaseSet getLeaseSet() {
	return set;
    }

    /**
     * Return an <code>EventFactory</code> that will create an
     * appropriate <code>RenewalFailureEvent</code> for the client
     * lease associated with this wrapper.  This method assumes
     * that no one else will be setting the serialization format
     * of the lease during the course of the call.
     * @param source Source object for the event
     * @throws IOException if the client lease could not be pre
     * marshalled.
     */
    EventFactory newFailureFactory(LeaseRenewalSet source) throws IOException {
	final MarshalledInstance ml = getMarshalledClientLease();

	MarshalledInstance mt = null;
	if (lastFailure != null)
	    mt = new MarshalledInstance(lastFailure);

	return new FailureFactory(source, ml, mt); 
    }

    /**
     * Nested top-level implementation of <code>EventFactory</code> that
     * generates  <code>RenewalFailureEvent</code> events
     */
    // $$$ We could have this object share state with wrapper, but weird
    // things could happen if the wrapper then changes -- right now
    // the wrapper won't change after this method is called but in the 
    // future who is to say.
    private class FailureFactory implements EventFactory {
	/** Source for event */
	private LeaseRenewalSet source;
	
	/** Client lease that could not be renewed in marshalled form */
	final private MarshalledInstance marshalledLease;

	/** 
	 * Throwable (if any) that was thrown when we tried to renew the lease
	 * in marshalled form
	 */
	final private MarshalledInstance marshalledThrowable;

	/**
	 * Simple constructor
	 * @param source event source
	 * @param marshalledLease client lease that could not be renewed
	 *        in marshalled form
	 * @param marshalledThrowable exception (if any) that was thrown when
	 *        the lease could not be renewed
	 */
	private FailureFactory(LeaseRenewalSet  source,
			       MarshalledInstance marshalledLease,
			       MarshalledInstance marshalledThrowable)
	{
	    this.source = source;
	    this.marshalledLease = marshalledLease;
	    this.marshalledThrowable = marshalledThrowable;
	}

	// Inherit java doc from super type
	public RemoteEvent createEvent(long             eventID, 
				       long             seqNum, 
				       MarshalledObject handback) 
	{
	    return new BasicRenewalFailureEvent(source, seqNum,
	        handback, marshalledLease, marshalledThrowable);
	}
    }
}
