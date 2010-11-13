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
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.rmi.MarshalException;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.admin.Administrable;
import net.jini.core.entry.Entry;
import net.jini.core.entry.UnusableEntryException;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import net.jini.entry.UnusableEntriesException;
import net.jini.space.JavaSpace05;
import net.jini.space.MatchSet;

import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.security.Security;

import com.sun.jini.landlord.LandlordLease;

/**
 * This class is the client-side proxy for the Outrigger
 * implementation of a JavaSpaces<sup><font size=-2>TM</font></sup>
 * service.  <code>OutriggerServerImpl</code> implements the
 * <code>OutriggerSpace</code> interface, and each
 * <code>SpaceProxy2</code> object holds a reference to the remote
 * OutriggerSpace server it represents to the client. The client makes
 * calls from the <code>JavaSpace</code> interface, which the
 * <code>SpaceProxy2</code> translates into appropriate
 * <code>OutriggerSpace</code> calls to the
 * <code>OutriggerServerImpl</code> server.
 *
 * @author Sun Microsystems, Inc.
 *
 */
// @see OutriggerSpace
class SpaceProxy2 implements JavaSpace05, Administrable, ReferentUuid,
			     Serializable 
{
    static final long serialVersionUID = 1L;

    /**
     * The remote server this proxy works with.
     * Package protected so it can be read by subclasses and proxy verifier
     * @serial
     */
    final OutriggerServer space;

    /** 
     * The <code>Uuid</code> that identifies the space this proxy is for.
     * Package protected so it can be read by subclasses and proxy verifier
     * @serial
     */
    final Uuid spaceUuid;

    /**
     * The value to use for maxServerQueryTimeout if no
     * local value is provided. Package protected so it can be read by
     * subclasses.
     * @serial
     */
    final long serverMaxServerQueryTimeout;

    /**
     * Maximum time any sub-query should be allowed to run for.
     */
    private transient long maxServerQueryTimeout;
    

    /**
     * Value (as a long) of the
     * <code>com.sun.jini.outrigger.maxServerQueryTimeout</code>
     * property in this VM, or a non-positive number if it is not set.
     */
    private static final long maxServerQueryTimeoutPropertyValue =
	getMaxServerQueryTimeoutPropertyValue();

    /** 
     * Logger for logging information about operations carried out in
     * the client. Note, we hard code "com.sun.jini.outrigger" so
     * we don't drag in OutriggerServerImpl to outrigger-dl.jar.
     */
    private static final Logger logger = 
	Logger.getLogger("com.sun.jini.outrigger.proxy");

    // --------------------------------------------------
    //		Construction
    // --------------------------------------------------

    /**
     * Create a new <code>SpaceProxy2</code> for the given space.
     * @param space The <code>OutriggerServer</code> for the 
     *              space.
     * @param spaceUuid The universally unique ID for the
     *              space
     * @param serverMaxServerQueryTimeout The value this proxy
     *              should use for the <code>maxServerQueryTimeout</code>
     *              if no local value is provided.
     * @throws NullPointerException if <code>space</code> or
     *         <code>spaceUuid</code> is <code>null</code>.
     * @throws IllegalArgumentException if 
     *         <code>serverMaxServerQueryTimeout</code> is not
     *         larger than zero.
     */
    SpaceProxy2(OutriggerServer space, Uuid spaceUuid, 
		long serverMaxServerQueryTimeout)
    {
	if (space == null)
	    throw new NullPointerException("space must be non-null");
	if (spaceUuid == null) 
	    throw new NullPointerException("spaceUuid must be non-null");
	if (serverMaxServerQueryTimeout <= 0)
	    throw new 
		IllegalArgumentException("serverMaxServerQueryTimeout " +
					 "must be positive");
	this.space = space;
	this.spaceUuid = spaceUuid;
	this.serverMaxServerQueryTimeout = serverMaxServerQueryTimeout;
	setMaxServerQueryTimeout();
    }

    public String toString() {
	return getClass().getName() + " for " + spaceUuid + 
	    " (through " + space + ")";
    }

    // inherit doc comment
    public boolean equals(Object other) {
	return ReferentUuids.compare(this, other);
    }

    // inherit doc comment
    public int hashCode() {
	return spaceUuid.hashCode();
    }

    public Uuid getReferentUuid() {
	return spaceUuid;
    }

    /**
     * Safely read the value of 
     * <code>com.sun.jini.outrigger.maxServerQueryTimeout</code>.
     * If it can't be read return -1.
     */
    private static long getMaxServerQueryTimeoutPropertyValue() {
	try {
	    final String propValue = 
		(String)Security.doPrivileged(
                    new ReadProperityPrivilegedAction(
                       "com.sun.jini.outrigger.maxServerQueryTimeout"));
	    if (propValue == null)
		return -1;

	    // Note, if we throw NumberFormatException we return -1.
	    return Long.parseLong(propValue);
	} catch (Throwable t) {
	    return -1;
	}
    }

    /**
     * PrivilegedAction for reading a property. Returns a string.
     */
    private static class ReadProperityPrivilegedAction 
	implements PrivilegedAction 
    {
	/** Property to read */
	final private String propName;

	/** 
	 * Construct a ReadProperityPrivilegedAction that will read
	 * the specified property.
	 */
	ReadProperityPrivilegedAction(String propName) {
	    this.propName = propName;
	}

	public Object run() {
	    try {
		return System.getProperty(propName);
	    } catch (SecurityException e) {
		return null;
	    }
	}
    }

    /**
     * Set <code>maxServerQueryTimeout</code> based on the values
     * of <code>serverMaxServerQueryTimeout</code> and 
     * <code>maxServerQueryTimeoutPropertyValue</code>.
     */
    private void setMaxServerQueryTimeout() {
	/* If the com.sun.jini.outrigger.maxServerQueryTimeout property
	 * was set in this VM, override the value set by the server
	 * when we were created.
	 */
	if (maxServerQueryTimeoutPropertyValue > 0) 
	    maxServerQueryTimeout = maxServerQueryTimeoutPropertyValue;
	else if (serverMaxServerQueryTimeout > 0)
	    maxServerQueryTimeout = serverMaxServerQueryTimeout;
	else 
	    /* should never get here, the constructor and readObject
	     * check to make sure that serverMaxServerQueryTimeout
	     * is positive.
	     */
	    throw new 
	        AssertionError("serverMaxServerQueryTimeout invalid:" + 
			       serverMaxServerQueryTimeout);

	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG, 
		"Outrigger proxy using {0} ms for maxServerQueryTimeout",
		new Long(maxServerQueryTimeout));
	}
    }

    /**
     * Read this object back setting the <code>maxServerQueryTimeout</code>
     * field and validate state.
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();

	if (space == null) 
	    throw new InvalidObjectException("null server reference");
	    
	if (spaceUuid == null)
	    throw new InvalidObjectException("null Uuid");

	if (serverMaxServerQueryTimeout <= 0)
	    throw new 
		InvalidObjectException("Bad serverMaxServerQueryTimeout " +
		    "value:" + serverMaxServerQueryTimeout);

	setMaxServerQueryTimeout();
    }

    /** 
     * We should always have data in the stream, if this method
     * gets called there is something wrong.
     */
    private void readObjectNoData() throws InvalidObjectException {
	throw new 
	    InvalidObjectException("SpaceProxy2 should always have data");
    }

    // --------------------------------------------------
    //		JavaSpace method implementations
    // --------------------------------------------------

    // inherit doc comment
    public Lease write(Entry entry, Transaction txn, long lease)
	throws TransactionException, RemoteException
    {
	if (entry == null)
	    throw new NullPointerException("Cannot write null Entry");
	long[] leaseData = space.write(repFor(entry), txn, lease);
	if (leaseData == null || leaseData.length != 3)
	    throw new AssertionError("space.write returned malformed data" + 
				     leaseData);
	return newLease(UuidFactory.create(leaseData[1], leaseData[2]),
			leaseData[0]);
    }

    public Entry read(Entry tmpl, Transaction txn, long timeout)
	throws UnusableEntryException, TransactionException,
	       InterruptedException, RemoteException
    {
	// Figure out the max time this query should last
	final long endTime = calcEndTime(timeout);
    
	long remaining = timeout;
	OutriggerServer.QueryCookie queryCookie = null;
	
 	// Loop util timeout or we get an answer (call at least once!)
	do {
	    final long serverTimeout = 
		Math.min(remaining, maxServerQueryTimeout);
	    logQuery("read", serverTimeout, queryCookie, remaining);

	    final Object rslt = 
		space.read(repFor(tmpl), txn, serverTimeout, queryCookie);
	    if (rslt == null) {
		// should never get null from a non-ifExists query
		throw new AssertionError("space.read() returned null");
	    } else if (rslt instanceof EntryRep) {
		// Got an answer, return it
		return entryFrom((EntryRep)rslt);
	    } else if (rslt instanceof OutriggerServer.QueryCookie) {
		/* Will still want to go on if there is time, but pass
		 * the new cookie
		 */
		queryCookie = (OutriggerServer.QueryCookie)rslt;
	    } else {
		throw new AssertionError(
                    "Unexpected return type from space.read()");
	    }

	    /* Update remaining and loop, checking to see if the timeout has
	     * expired.
	     */
	    remaining = endTime - System.currentTimeMillis();
	} while (remaining > 0);

	/* If we get here then there must not have been an entry available
	 * to us before the endTime.
	 */
	return null;
    }

    // inherit doc comment, use internal routine for common code
    public Entry readIfExists(Entry tmpl, Transaction txn, long timeout)
	throws UnusableEntryException, TransactionException,
	       InterruptedException, RemoteException
    {
	// Figure out the max time this query should last
	final long endTime = calcEndTime(timeout);
    
	long remaining = timeout;
	OutriggerServer.QueryCookie queryCookie = null;
	
 	// Loop util timeout or we get an answer (call at least once!)
	do {
	    final long serverTimeout = 
		Math.min(remaining, maxServerQueryTimeout);
	    logQuery("readIfExists", serverTimeout, queryCookie, remaining);

	    final Object rslt = 
		space.readIfExists(repFor(tmpl), txn, serverTimeout,
				   queryCookie);
	    if (rslt == null) {
		// Must be no matches in the space at all
		return null;
	    } else if (rslt instanceof EntryRep) {
		// Got an answer, return it
		return entryFrom((EntryRep)rslt);
	    } else if (rslt instanceof OutriggerServer.QueryCookie) {
		/* Will still want to go on if there is time, but pass
		 * the new cookie
		 */
		queryCookie = (OutriggerServer.QueryCookie)rslt;
	    } else {
		throw new AssertionError(
                    "Unexpected return type from space.readIfExists()");
	    }

	    /* Update remaining and loop, checking to see if the timeout has
	     * expired.
	     */
	    remaining = endTime - System.currentTimeMillis();
	} while (remaining > 0);

	/* If we get here then there must not have been an entry available
	 * to us before the endTime.
	 */
	return null;
    }

    // inherit doc comment, use internal routine for common code
    public Entry take(Entry tmpl, Transaction txn, long timeout)
	throws UnusableEntryException, TransactionException,
	       InterruptedException, RemoteException
    {
	// Figure out the max time this query should last
	final long endTime = calcEndTime(timeout);
    
	long remaining = timeout;
	OutriggerServer.QueryCookie queryCookie = null;
	
 	// Loop util timeout or we get an answer (call at least once!)
	do {
	    final long serverTimeout = 
		Math.min(remaining, maxServerQueryTimeout);
	    logQuery("take", serverTimeout, queryCookie, remaining);

	    final Object rslt = 
		space.take(repFor(tmpl), txn, serverTimeout, queryCookie);
	    if (rslt == null) {
		// should never get null from a non-ifExists query
		throw new AssertionError("space.take() returned null");
	    } else if (rslt instanceof EntryRep) {
		// Got an answer, return it
		return entryFrom((EntryRep)rslt);
	    } else if (rslt instanceof OutriggerServer.QueryCookie) {
		/* Will still want to go on if there is time, but pass
		 * the new cookie
		 */
		queryCookie = (OutriggerServer.QueryCookie)rslt;
	    } else {
		throw new AssertionError(
                    "Unexpected return type from space.take()");
	    }

	    /* Update remaining and loop, checking to see if the timeout has
	     * expired.
	     */
	    remaining = endTime - System.currentTimeMillis();
	} while (remaining > 0);

	/* If we get here then there must not have been an entry available
	 * to us before the endTime.
	 */
	return null;
    }

    // inherit doc comment, use internal routine for common code
    public Entry takeIfExists(Entry tmpl, Transaction txn, long timeout)
	throws UnusableEntryException, TransactionException,
	       InterruptedException, RemoteException
    {
	// Figure out the max time this query should last
	final long endTime = calcEndTime(timeout);
    
	long remaining = timeout;
	OutriggerServer.QueryCookie queryCookie = null;
	
 	// Loop util timeout or we get an answer (call at least once!)
	do {
	    final long serverTimeout = 
		Math.min(remaining, maxServerQueryTimeout);
	    logQuery("takeIfExists", serverTimeout, queryCookie, remaining);

	    final Object rslt = 
		space.takeIfExists(repFor(tmpl), txn, serverTimeout, 
				   queryCookie);
	    if (rslt == null) {
		// Must be no matches in the space at all
		return null;
	    } else if (rslt instanceof EntryRep) {
		// Got an answer, return it
		return entryFrom((EntryRep)rslt);
	    } else if (rslt instanceof OutriggerServer.QueryCookie) {
		/* Will still want to go on if there is time, but pass
		 * the new cookie
		 */
		queryCookie = (OutriggerServer.QueryCookie)rslt;
	    } else {
		throw new AssertionError(
                    "Unexpected return type from space.takeIfExists()");
	    }

	    /* Update remaining and loop, checking to see if the timeout has
	     * expired.
	     */
	    remaining = endTime - System.currentTimeMillis();
	} while (remaining > 0);

	/* If we get here then there must not have been an entry available
	 * to us before the endTime.
	 */
	return null;
    }

    // inherit doc comment
    public Entry snapshot(Entry entry) throws MarshalException {
	if (entry == null)
	    return null;
	else
	    return new SnapshotRep(entry);
    }

    // inherit doc comment
    public EventRegistration
	notify(Entry tmpl, Transaction txn, RemoteEventListener listener,
	       long lease, MarshalledObject handback)
	throws TransactionException, RemoteException
    {
	return space.notify(repFor(tmpl), txn, listener, lease, handback);
    }

    public List write(List entries, Transaction txn, List leaseDurations)
        throws RemoteException, TransactionException 
    {
	final long[] leases = new long[leaseDurations.size()];
	int j = 0;
	for (Iterator i=leaseDurations.iterator(); i.hasNext(); ) {
	    final Object l = i.next();

	    if (l == null)
		throw new NullPointerException(
		    "leaseDurations contatins a null element");

	    if (!(l instanceof Long))
		throw new IllegalArgumentException(
		    "leaseDurations contatins an element which is not a Long");

	    leases[j++] = ((Long)l).longValue();
	}

	long[] leaseData = space.write(repFor(entries, "entries"), txn, leases);
	if (leaseData == null)
	    throw new AssertionError("space.write<multiple> returned null");

	final List rslt = new ArrayList(leaseData.length/3);
	try {
	    int m=0;
	    while (m<leaseData.length) {
		final long duration = leaseData[m++];
		final long high = leaseData[m++];
		final long low = leaseData[m++];
		final Uuid uuid = UuidFactory.create(high, low);
		rslt.add(newLease(uuid, duration));
	    }
	} catch (ArrayIndexOutOfBoundsException e) {
	    throw new 
		AssertionError("space.write<multiple> returned malformed data");
	}

	return rslt;
    }

    public Collection take(Collection tmpls, Transaction txn,
			   long timeout, long maxEntries)
        throws UnusableEntriesException, TransactionException, RemoteException 
    {
	// Figure out the max time this query should last
	final long endTime = calcEndTime(timeout);
    
	long remaining = timeout;
	OutriggerServer.QueryCookie queryCookie = null;
	final EntryRep[] treps = repFor(tmpls, "tmpls");

	final int limit;
	if (maxEntries < 1) {
	    throw new IllegalArgumentException("maxEntries must be positive");
	} else if (maxEntries <= Integer.MAX_VALUE) {
	    limit = (int)maxEntries;
	} else {
	    limit = Integer.MAX_VALUE; // ok to return fewer than requested
	}
	
 	// Loop util timeout or we get an answer (call at least once!)
	do {
	    final long serverTimeout = 
		Math.min(remaining, maxServerQueryTimeout);
	    logQuery("take(multiple)", serverTimeout, queryCookie, remaining);
	

	    final Object rslt = 
		space.take(treps, txn, serverTimeout, limit, queryCookie);
	    if (rslt == null) {
		// should never get null from a non-ifExists query
		throw new AssertionError("space.take<multiple>() returned null");
	    } else if (rslt instanceof EntryRep[]) {
		EntryRep[] reps = (EntryRep[])rslt;
		// Got an answer, return it
		final Collection entries = new LinkedList();
		Collection exceptions = null;
		
		for (int i=0;i<reps.length;i++) {
		    try {
			entries.add(entryFrom(reps[i]));
		    } catch (UnusableEntryException e) {
			if (exceptions == null)
			    exceptions = new LinkedList();

			exceptions.add(e);
		    }
		}

		if (exceptions == null) {
		    return entries;
		} else {
		    throw new UnusableEntriesException(
                        "some of the removed entries could not be unmarshalled", 
			entries, exceptions);
		}			
	    } else if (rslt instanceof OutriggerServer.QueryCookie) {
		/* Will still want to go on if there is time, but pass
		 * the new cookie
		 */
		queryCookie = (OutriggerServer.QueryCookie)rslt;
	    } else {
		throw new AssertionError(
                    "Unexpected return type from space.take<multiple>()");
	    }

	    /* Update remaining and loop, checking to see if the timeout has
	     * expired.
	     */
	    remaining = endTime - System.currentTimeMillis();
	} while (remaining > 0);

	/* If we get here then there must not have been any entries available
	 * to us before the endTime.
	 */
	return Collections.EMPTY_LIST;
    }

    public EventRegistration 
	registerForAvailabilityEvent(Collection tmpls,
				     Transaction txn, 
				     boolean visibilityOnly,
				     RemoteEventListener listener,
				     long leaseDuration, 
				     MarshalledObject handback)
        throws TransactionException, RemoteException
    {
	return space.registerForAvailabilityEvent(
	    repFor(tmpls, "tmpls"), txn, visibilityOnly, listener,
	    leaseDuration, handback);
    }


    // inherit doc comment
    public MatchSet contents(Collection tmpls,		      
			     Transaction txn,
			     long leaseDuration,
			     long maxEntries)
	throws RemoteException, TransactionException
    {
	final MatchSetData msd = 
	    space.contents(repFor(tmpls, "tmpls"), txn, leaseDuration, maxEntries);
	return new MatchSetProxy(msd, this, space);
    }

    /* We break up lease creation into two methods. newLease takes
     * care of converting from duration to expiration and we should
     * never need to override (so we make it final), while
     * constructLease takes care of invoking the right constructor for
     * the given context (e.g. constrainable v. not) 
     */

    /** Create a new lease with the specified id and initial duration */
    final protected Lease newLease(Uuid uuid, long duration) {
	long expiration = duration + System.currentTimeMillis();

	// We added two positive numbers, so if the result is negative
	// we must have overflowed, so use Long.MAX_VALUE
	if (expiration < 0)
	    expiration = Long.MAX_VALUE;
	return constructLease(uuid, expiration);
    }

    /** Create a new lease with the specified id and initial expiration */
    protected Lease constructLease(Uuid uuid, long expiration) {
	return new LandlordLease(uuid, space, spaceUuid, expiration);
    }

    // --------------------------------------------------
    //          Administrable methods implementation
    // --------------------------------------------------
    // inherit doc comment
    public Object getAdmin() throws RemoteException {
	return space.getAdmin();
    }

    // --------------------------------------------------
    //		Private implementation
    // --------------------------------------------------

    /**
     * Utility method to calculate the absolute end time of a query.
     * @param timeout relative timeout of query.
     * @return timeout plus the current time, or 
     *         <code>Long.MAX_VALUE</code> if timeout plus the current time
     *         is larger than <code>Long.MAX_VALUE</code>.
     */
    private long calcEndTime(long timeout) {
	if (timeout < 0)
	    throw new IllegalArgumentException("timeout must be non-negative");
	final long now = System.currentTimeMillis();
	if (Long.MAX_VALUE - timeout <= now)
	    return Long.MAX_VALUE;
	else
	    return now + timeout;
    }

    static EntryRep[] repFor(Collection entries, String argName)
	throws MarshalException 
    {
	final EntryRep[] reps = new EntryRep[entries.size()];
	int j = 0;
	for (Iterator i=entries.iterator(); i.hasNext(); ) {
	    final Object e = i.next();
	    if (!(e == null || e instanceof Entry))
		throw new IllegalArgumentException(
		    argName + " contatins an element which is not an Entry");

	    reps[j++] = repFor((Entry)e);
	}

	return reps;
    }

    /**
     * Return an <code>EntryRep</code> object for the given
     * <code>Entry</code>.  
     */
    static EntryRep repFor(Entry entry) throws MarshalException {
	if (entry == null)
	    return null;
	if (entry instanceof SnapshotRep)    // snapshots are pre-calculated
	    return ((SnapshotRep) entry).rep();
	return new EntryRep(entry);
    }

    /**
     * Return an entry generated from the given rep.
     */
    static Entry entryFrom(EntryRep rep) throws UnusableEntryException {
	if (rep == null)
	    return null;
	return rep.entry();
    }

    /** Log query call to server */
    private void logQuery(String op, long serverTimeout, 
			  OutriggerServer.QueryCookie cookie, long remaining) 
    {
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "Outrigger calling {0} on server with " +
		"timeout of {1} ms for serverTimeout, using QueryCookie " +
		"{2}, {3} ms remaining on query",
		new Object[] {op, new Long(serverTimeout), cookie,
			      new Long(remaining)});
	    }
    }
}
