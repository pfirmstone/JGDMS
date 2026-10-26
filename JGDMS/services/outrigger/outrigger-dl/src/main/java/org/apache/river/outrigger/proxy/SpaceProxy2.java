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
package org.apache.river.outrigger.proxy;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.rmi.MarshalException;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.admin.Administrable;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.entry.Entry;
import net.jini.core.entry.UnusableEntryException;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import net.jini.entry.UnusableEntriesException;
import net.jini.export.ProxyAccessor;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalledInstance;
import net.jini.security.Security;
import net.jini.space.FilterRejectedException;
import net.jini.space.FilteredTupleSpace;
import net.jini.space.MatchSet;
import net.jini.space.TupleSpace;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.landlord.LandlordLease;

/**
 * This class is the client-side proxy for the Outrigger
 * implementation of a JavaSpaces<sup>TM</sup>
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
@AtomicSerial
public abstract class SpaceProxy2 implements TupleSpace, Administrable, ReferentUuid,
			     ProxyAccessor, FilteredTupleSpace
{
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
     * The marshalling format this space was born with, fixed at the
     * space's instantiation and immutable for its life (JGDMS-STD-006
     * sec.3 item 5: a space-wide format epoch inherited uniformly by
     * every client). Every entry and template this proxy marshals
     * ({@link #repFor}) uses this format -- <em>not</em> a value derived
     * from {@code ((RemoteMethodControl) space).getConstraints()}, since
     * client-set constraints are wholesale replaceable via
     * {@code setConstraints} and would make the format silently
     * droppable, reintroducing the per-relationship optionality the
     * born-immutable model forbids.
     * Package protected so it can be read by subclasses.
     * @serial
     */
    final MarshallingFormat entryFormat;

    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm("space", OutriggerServer.class),
            new SerialForm("spaceUuid", Uuid.class),
            new SerialForm("serverMaxServerQueryTimeout", Long.TYPE),
            new SerialForm("entryFormat", MarshallingFormat.class)
        };
    }

    public static void serialize(PutArg arg, SpaceProxy2 o) throws IOException {
        arg.put("space", o.space);
        arg.put("spaceUuid", o.spaceUuid);
        arg.put("serverMaxServerQueryTimeout", o.serverMaxServerQueryTimeout);
        arg.put("entryFormat", o.entryFormat);
        arg.writeArgs();
    }

    /**
     * Maximum time any sub-query should be allowed to run for.
     */
    private transient volatile long maxServerQueryTimeout;
    

    /**
     * Value (as a long) of the
     * <code>org.apache.river.outrigger.maxServerQueryTimeout</code>
     * property in this VM, or a non-positive number if it is not set.
     */
    private static final long maxServerQueryTimeoutPropertyValue =
	getMaxServerQueryTimeoutPropertyValue();

    /** 
     * Logger for logging information about operations carried out in
     * the client. Note, we hard code "org.apache.river.outrigger" so
     * we don't drag in OutriggerServerImpl to outrigger-dl.jar.
     */
    private static final Logger logger = 
	Logger.getLogger("org.apache.river.outrigger.proxy");

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
     * @param entryFormat The marshalling format this space was born with
     *              (fixed at the space's instantiation, immutable for its
     *              life); every entry/template this proxy marshals uses
     *              this format.
     * @throws NullPointerException if <code>space</code>,
     *         <code>spaceUuid</code> or <code>entryFormat</code> is
     *         <code>null</code>.
     * @throws IllegalArgumentException if
     *         <code>serverMaxServerQueryTimeout</code> is not
     *         larger than zero.
     */
    public SpaceProxy2(OutriggerServer space, Uuid spaceUuid,
		long serverMaxServerQueryTimeout, MarshallingFormat entryFormat)
    {
	this (notNull(space),
		notNull(spaceUuid),
		serverMaxServerQueryTimeout,
		notNull(entryFormat),
		setMaxServerQueryTimeout(serverMaxServerQueryTimeout));
    }

    private static <T> T notNull(T value){
	if (value == null) throw new NullPointerException();
	return value;
    }

    private SpaceProxy2( OutriggerServer space, Uuid spaceUuid,
	    long serverMaxServerQueryTimeout, MarshallingFormat entryFormat,
	    long maxServerQueryTimeout ){
	this.space = space;
	this.spaceUuid = spaceUuid;
	this.serverMaxServerQueryTimeout = serverMaxServerQueryTimeout;
	this.entryFormat = entryFormat;
	this.maxServerQueryTimeout = maxServerQueryTimeout;
    }

    private SpaceProxy2( boolean check, OutriggerServer space, Uuid spaceUuid,
	    long serverMaxServerQueryTimeout, MarshallingFormat entryFormat,
	    long maxServerQueryTimeout ){
	this(space, spaceUuid, serverMaxServerQueryTimeout, entryFormat, maxServerQueryTimeout);
    }

    SpaceProxy2(GetArg arg) throws IOException, ClassNotFoundException {
	this(serialCheck((OutriggerServer) arg.get("space", null),
			(Uuid) arg.get("spaceUuid", null),
			arg.get("serverMaxServerQueryTimeout", -1L),
			resolveEntryFormat(arg)),
		(OutriggerServer) arg.get("space", null),
		(Uuid) arg.get("spaceUuid", null),
		arg.get("serverMaxServerQueryTimeout", -1L),
		resolveEntryFormat(arg),
		setMaxServerQueryTimeout(arg.get("serverMaxServerQueryTimeout", -1L)));
    }

    /**
     * Sentinel distinguishing "the {@code entryFormat} field is absent from
     * this stream's persistent schema" from "the field is present and its
     * serialized value is {@code null}" -- {@link GetArg}'s type-checked
     * {@code get(name, val, type)} overload conflates both into the same
     * default-return path (see its {@code v == ABSENT || v == null} check),
     * so the untyped {@code get(name, Object)} overload is used here with a
     * private sentinel default instead, which only substitutes on true
     * absence.
     */
    private static final Object ENTRY_FORMAT_ABSENT = new Object();

    /**
     * Board-review fix (blocking): resolve the born {@link #entryFormat}
     * from a {@link GetArg} stream, treating an absent field (a proxy
     * serialized before this field existed, i.e. before DER support) as
     * implicit legacy {@link MarshallingFormat#JOSS} rather than rejecting
     * it (STD-006 sec.11.8 graceful degradation: an absent old-format
     * marker means implicit legacy JOSS, not a reject). A field that IS
     * present but whose serialized value is actually {@code null} is left
     * as {@code null} here -- that is corruption, and {@link #serialCheck}
     * still rejects it.
     */
    private static MarshallingFormat resolveEntryFormat(GetArg arg)
	    throws IOException, ClassNotFoundException
    {
	Object raw = arg.get("entryFormat", ENTRY_FORMAT_ABSENT);
	if (raw == ENTRY_FORMAT_ABSENT) return MarshallingFormat.JOSS;
	if (raw == null || raw instanceof MarshallingFormat) return (MarshallingFormat) raw;
	throw new InvalidObjectException("entryFormat field is a "
	    + raw.getClass().getName() + ", not assignable to MarshallingFormat");
    }

    /**
     * Validate the invariants formerly checked by {@code readObject}: a
     * non-null server reference, a non-null {@code Uuid}, a positive
     * {@code serverMaxServerQueryTimeout}, and a non-null born
     * {@code entryFormat}.
     */
    private static boolean serialCheck(OutriggerServer space, Uuid spaceUuid,
	    long serverMaxServerQueryTimeout, MarshallingFormat entryFormat)
	    throws InvalidObjectException
    {
	if (space == null)
	    throw new InvalidObjectException("null server reference");
	if (spaceUuid == null)
	    throw new InvalidObjectException("null Uuid");
	if (serverMaxServerQueryTimeout <= 0)
	    throw new InvalidObjectException("Bad serverMaxServerQueryTimeout " +
		"value:" + serverMaxServerQueryTimeout);
	if (entryFormat == null)
	    throw new InvalidObjectException("null entryFormat");
	return true;
    }

    @Override
    public String toString() {
	return getClass().getName() + " for " + spaceUuid + 
	    " (through " + space + ")";
    }

    // inherit doc comment
    @Override
    public boolean equals(Object other) {
	return ReferentUuids.compare(this, other);
    }

    // inherit doc comment
    @Override
    public int hashCode() {
	return spaceUuid.hashCode();
    }

    public Uuid getReferentUuid() {
	return spaceUuid;
    }

    /**
     * Safely read the value of 
     * <code>org.apache.river.outrigger.maxServerQueryTimeout</code>.
     * If it can't be read return -1.
     */
    private static long getMaxServerQueryTimeoutPropertyValue() {
	try {
	    final String propValue = 
		(String)Security.doPrivileged(
                    new ReadProperityPrivilegedAction(
                       "org.apache.river.outrigger.maxServerQueryTimeout"));
	    if (propValue == null)
		return -1;

	    // Note, if we throw NumberFormatException we return -1.
	    return Long.parseLong(propValue);
	} catch (Throwable t) {
	    return -1;
	}
    }

    @Override
    public Object getProxy() {
	return space;
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
    private static long setMaxServerQueryTimeout(long serverMaxServerQueryTimeout) {
	if (serverMaxServerQueryTimeout <= 0)
	    throw new 
		IllegalArgumentException("serverMaxServerQueryTimeout " +
					 "must be positive");
	/* If the org.apache.river.outrigger.maxServerQueryTimeout property
	 * was set in this VM, override the value set by the server
	 * when we were created.
	 */
	long maxServerQueryTimeout;
	if (maxServerQueryTimeoutPropertyValue > 0) 
	    maxServerQueryTimeout = maxServerQueryTimeoutPropertyValue;
	else if (serverMaxServerQueryTimeout > 0)
	    maxServerQueryTimeout = serverMaxServerQueryTimeout;
	else
	    /* should never get here, the constructor and the (GetArg)
	     * deserialization constructor check to make sure that
	     * serverMaxServerQueryTimeout is positive.
	     */
	    throw new
	        AssertionError("serverMaxServerQueryTimeout invalid:" + 
			       serverMaxServerQueryTimeout);

	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG, 
		"Outrigger proxy using {0} ms for maxServerQueryTimeout",
		Long.valueOf(maxServerQueryTimeout));
	}
	return maxServerQueryTimeout;
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
	long[] leaseData = space.write(repFor(entry, entryFormat), txn, lease);
	if (leaseData == null || leaseData.length != 3){
            StringBuilder sb = new StringBuilder(180);
            sb.append("space.write returned malformed data \n");
            int l = leaseData == null? 0 : leaseData.length;
            for (int i =0; i < l; i++){
                sb.append(leaseData[i]).append("\n");
            }
	    throw new AssertionError(sb);
        }
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
		space.read(repFor(tmpl, entryFormat), txn, serverTimeout, queryCookie);
	    if (rslt == null) {
		// should never get null from a non-ifExists query
		throw new AssertionError("space.read() returned null");
	    } else if (rslt instanceof EntryRep) {
		// Got an answer, return it
		return entryFrom((EntryRep)rslt, tmpl);
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
		space.readIfExists(repFor(tmpl, entryFormat), txn, serverTimeout,
				   queryCookie);
	    if (rslt == null) {
		// Must be no matches in the space at all
		return null;
	    } else if (rslt instanceof EntryRep) {
		// Got an answer, return it
		return entryFrom((EntryRep)rslt, tmpl);
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
		space.take(repFor(tmpl, entryFormat), txn, serverTimeout, queryCookie);
	    if (rslt == null) {
		// should never get null from a non-ifExists query
		throw new AssertionError("space.take() returned null");
	    } else if (rslt instanceof EntryRep) {
		// Got an answer, return it
		return entryFrom((EntryRep)rslt, tmpl);
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
		space.takeIfExists(repFor(tmpl, entryFormat), txn, serverTimeout, 
				   queryCookie);
	    if (rslt == null) {
		// Must be no matches in the space at all
		return null;
	    } else if (rslt instanceof EntryRep) {
		// Got an answer, return it
		return entryFrom((EntryRep)rslt, tmpl);
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
	    return new SnapshotRep(entry, entryFormat);
    }

    /**
     * Deprecated {@link java.rmi.MarshalledObject} overload, behavior
     * withdrawn in JGDMS 4.0.0 (Outrigger is ATOMIC_DER-only;
     * {@code SOW-Outrigger-DER-Only-JOSS-Rejection.md} decisions 3+4): a
     * <code>null</code> handback delegates to the
     * {@link net.jini.io.MarshalledInstance} path (nothing MO-shaped
     * exists in a null call); a non-null handback throws
     * {@link UnsupportedOperationException} -- a MarshalledObject can only
     * carry a JOSS payload, which this space rejects at registration.
     * Use {@link net.jini.space.TupleSpace#notify(Entry, Transaction,
     * RemoteEventListener, long, MarshalledInstance)} with a
     * constraint-built DER handback:
     * <pre>
     * new MarshalledInstance(obj, Collections.EMPTY_SET,
     *     new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null))
     * </pre>
     * (never the bare {@code new MarshalledInstance(obj)}, which produces
     * a JOSS payload).
     */
    public EventRegistration
	notify(Entry tmpl, Transaction txn, RemoteEventListener listener,
	       long lease, MarshalledObject handback)
	throws TransactionException, RemoteException
    {
	if (handback != null) {
	    throw new UnsupportedOperationException(
		"The MarshalledObject handback overload of notify has been "
		+ "withdrawn: this space is ATOMIC_DER-only (JGDMS 4.0.0) "
		+ "and a java.rmi.MarshalledObject can only carry a JOSS "
		+ "payload. Use the MarshalledInstance overload with a "
		+ "handback built as new MarshalledInstance(obj, "
		+ "Collections.EMPTY_SET, new InvocationConstraints("
		+ "MarshallingFormat.ATOMIC_DER, null)).");
	}
	return space.notify(repFor(tmpl, entryFormat), txn, listener, lease,
		(MarshalledInstance) null);
    }

	// inherit doc comment
    public EventRegistration
	notify(Entry tmpl, Transaction txn, RemoteEventListener listener,
	       long lease, MarshalledInstance handback)
	throws TransactionException, RemoteException
    {
	return space.notify(repFor(tmpl, entryFormat), txn, listener, lease, handback);
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

	long[] leaseData = space.write(repFor(entries, "entries", entryFormat), txn, leases);
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
	final EntryRep[] treps = repFor(tmpls, "tmpls", entryFormat);

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
		
		for (int i=0,l=reps.length; i<l ; i++) {
		    try {
			Iterator tmplsIt = tmpls.iterator();
			while (tmplsIt.hasNext()){ // Try each template for class resolution.
			    Entry tmpl = (Entry) tmplsIt.next();
			    Entry e = entryFrom(reps[i], tmpl);
			    if (e != null) {
				entries.add(e);
				break;
			    }
			}
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

    /**
     * Deprecated {@link java.rmi.MarshalledObject} overload, behavior
     * withdrawn in JGDMS 4.0.0 (Outrigger is ATOMIC_DER-only;
     * {@code SOW-Outrigger-DER-Only-JOSS-Rejection.md} decisions 3+4): a
     * <code>null</code> handback delegates to the
     * {@link net.jini.io.MarshalledInstance} path (nothing MO-shaped
     * exists in a null call); a non-null handback throws
     * {@link UnsupportedOperationException} -- a MarshalledObject can only
     * carry a JOSS payload, which this space rejects at registration.
     * Use {@link net.jini.space.TupleSpace#registerForAvailabilityEvent(
     * Collection, Transaction, boolean, RemoteEventListener, long,
     * MarshalledInstance)} with a constraint-built DER handback:
     * <pre>
     * new MarshalledInstance(obj, Collections.EMPTY_SET,
     *     new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null))
     * </pre>
     * (never the bare {@code new MarshalledInstance(obj)}, which produces
     * a JOSS payload).
     */
    public EventRegistration
	registerForAvailabilityEvent(Collection tmpls,
				     Transaction txn,
				     boolean visibilityOnly,
				     RemoteEventListener listener,
				     long leaseDuration,
				     MarshalledObject handback)
        throws TransactionException, RemoteException
    {
	if (handback != null) {
	    throw new UnsupportedOperationException(
		"The MarshalledObject handback overload of "
		+ "registerForAvailabilityEvent has been withdrawn: this "
		+ "space is ATOMIC_DER-only (JGDMS 4.0.0) and a "
		+ "java.rmi.MarshalledObject can only carry a JOSS payload. "
		+ "Use the MarshalledInstance overload with a handback "
		+ "built as new MarshalledInstance(obj, "
		+ "Collections.EMPTY_SET, new InvocationConstraints("
		+ "MarshallingFormat.ATOMIC_DER, null)).");
	}
	return space.registerForAvailabilityEvent(
	    repFor(tmpls, "tmpls", entryFormat), txn, visibilityOnly, listener,
	    leaseDuration, (MarshalledInstance) null);
    }
	
    public EventRegistration registerForAvailabilityEvent(
		Collection tmpls,
		Transaction txn,
		boolean visibilityOnly,
		RemoteEventListener listener,
		long leaseDuration,
		MarshalledInstance handback) 
	    throws TransactionException, RemoteException 
    {
	return space.registerForAvailabilityEvent(
	    repFor(tmpls, "tmpls", entryFormat), txn, visibilityOnly, listener,
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
	    space.contents(repFor(tmpls, "tmpls", entryFormat), txn, leaseDuration, maxEntries);
	return new MatchSetProxy(msd, this, space, tmpls);
    }

    /* ======================================================================
     * FilteredTupleSpace — CEL predicate pushdown (SOW Part B, unit B1).
     *
     * These mirror the unfiltered operations above but thread an explicit
     * {@code byte[] filter} (a canonical FilterEnvelope) through to the
     * server's filtered OutriggerServer overloads. The server admits the
     * filter before matching and fails LOUDLY (FilterRejectedException) on any
     * rejection; this proxy never downgrades a filtered call to an unfiltered
     * one. A {@code null} filter is a caller error (NullPointerException) — use
     * the ordinary JavaSpace/TupleSpace methods for an unfiltered query.
     * ==================================================================== */

    public Entry read(Entry tmpl, Transaction txn, long timeout, byte[] filter)
	throws UnusableEntryException, TransactionException,
	       InterruptedException, RemoteException, FilterRejectedException
    {
	if (filter == null) throw new NullPointerException("filter");
	final long endTime = calcEndTime(timeout);
	long remaining = timeout;
	OutriggerServer.QueryCookie queryCookie = null;
	do {
	    final long serverTimeout = Math.min(remaining, maxServerQueryTimeout);
	    logQuery("read(filtered)", serverTimeout, queryCookie, remaining);
	    final Object rslt =
		space.read(repFor(tmpl, entryFormat), txn, serverTimeout, queryCookie, filter);
	    if (rslt == null) {
		throw new AssertionError("space.read(filtered) returned null");
	    } else if (rslt instanceof EntryRep) {
		return entryFrom((EntryRep)rslt, tmpl);
	    } else if (rslt instanceof OutriggerServer.QueryCookie) {
		queryCookie = (OutriggerServer.QueryCookie)rslt;
	    } else {
		throw new AssertionError("Unexpected return type from space.read(filtered)");
	    }
	    remaining = endTime - System.currentTimeMillis();
	} while (remaining > 0);
	return null;
    }

    public Entry readIfExists(Entry tmpl, Transaction txn, long timeout, byte[] filter)
	throws UnusableEntryException, TransactionException,
	       InterruptedException, RemoteException, FilterRejectedException
    {
	if (filter == null) throw new NullPointerException("filter");
	final long endTime = calcEndTime(timeout);
	long remaining = timeout;
	OutriggerServer.QueryCookie queryCookie = null;
	do {
	    final long serverTimeout = Math.min(remaining, maxServerQueryTimeout);
	    logQuery("readIfExists(filtered)", serverTimeout, queryCookie, remaining);
	    final Object rslt =
		space.readIfExists(repFor(tmpl, entryFormat), txn, serverTimeout, queryCookie, filter);
	    if (rslt == null) {
		return null;
	    } else if (rslt instanceof EntryRep) {
		return entryFrom((EntryRep)rslt, tmpl);
	    } else if (rslt instanceof OutriggerServer.QueryCookie) {
		queryCookie = (OutriggerServer.QueryCookie)rslt;
	    } else {
		throw new AssertionError("Unexpected return type from space.readIfExists(filtered)");
	    }
	    remaining = endTime - System.currentTimeMillis();
	} while (remaining > 0);
	return null;
    }

    public Entry take(Entry tmpl, Transaction txn, long timeout, byte[] filter)
	throws UnusableEntryException, TransactionException,
	       InterruptedException, RemoteException, FilterRejectedException
    {
	if (filter == null) throw new NullPointerException("filter");
	final long endTime = calcEndTime(timeout);
	long remaining = timeout;
	OutriggerServer.QueryCookie queryCookie = null;
	do {
	    final long serverTimeout = Math.min(remaining, maxServerQueryTimeout);
	    logQuery("take(filtered)", serverTimeout, queryCookie, remaining);
	    final Object rslt =
		space.take(repFor(tmpl, entryFormat), txn, serverTimeout, queryCookie, filter);
	    if (rslt == null) {
		throw new AssertionError("space.take(filtered) returned null");
	    } else if (rslt instanceof EntryRep) {
		return entryFrom((EntryRep)rslt, tmpl);
	    } else if (rslt instanceof OutriggerServer.QueryCookie) {
		queryCookie = (OutriggerServer.QueryCookie)rslt;
	    } else {
		throw new AssertionError("Unexpected return type from space.take(filtered)");
	    }
	    remaining = endTime - System.currentTimeMillis();
	} while (remaining > 0);
	return null;
    }

    public Entry takeIfExists(Entry tmpl, Transaction txn, long timeout, byte[] filter)
	throws UnusableEntryException, TransactionException,
	       InterruptedException, RemoteException, FilterRejectedException
    {
	if (filter == null) throw new NullPointerException("filter");
	final long endTime = calcEndTime(timeout);
	long remaining = timeout;
	OutriggerServer.QueryCookie queryCookie = null;
	do {
	    final long serverTimeout = Math.min(remaining, maxServerQueryTimeout);
	    logQuery("takeIfExists(filtered)", serverTimeout, queryCookie, remaining);
	    final Object rslt =
		space.takeIfExists(repFor(tmpl, entryFormat), txn, serverTimeout, queryCookie, filter);
	    if (rslt == null) {
		return null;
	    } else if (rslt instanceof EntryRep) {
		return entryFrom((EntryRep)rslt, tmpl);
	    } else if (rslt instanceof OutriggerServer.QueryCookie) {
		queryCookie = (OutriggerServer.QueryCookie)rslt;
	    } else {
		throw new AssertionError("Unexpected return type from space.takeIfExists(filtered)");
	    }
	    remaining = endTime - System.currentTimeMillis();
	} while (remaining > 0);
	return null;
    }

    public EventRegistration notify(Entry tmpl, Transaction txn,
	    RemoteEventListener listener, long lease,
	    MarshalledInstance handback, byte[] filter)
	throws TransactionException, RemoteException, FilterRejectedException
    {
	if (filter == null) throw new NullPointerException("filter");
	return space.notify(repFor(tmpl, entryFormat), txn, listener, lease, handback, filter);
    }

    public EventRegistration registerForAvailabilityEvent(
	    Collection tmpls, Transaction txn, boolean visibilityOnly,
	    RemoteEventListener listener, long leaseDuration,
	    MarshalledInstance handback, byte[] filter)
	throws TransactionException, RemoteException, FilterRejectedException
    {
	if (filter == null) throw new NullPointerException("filter");
	return space.registerForAvailabilityEvent(
	    repFor(tmpls, "tmpls", entryFormat), txn, visibilityOnly, listener,
	    leaseDuration, handback, filter);
    }

    public MatchSet contents(Collection tmpls, Transaction txn,
			     long leaseDuration, long maxEntries, byte[] filter)
	throws RemoteException, TransactionException, FilterRejectedException
    {
	if (filter == null) throw new NullPointerException("filter");
	final MatchSetData msd =
	    space.contents(repFor(tmpls, "tmpls", entryFormat), txn,
			   leaseDuration, maxEntries, filter);
	return new MatchSetProxy(msd, this, space, tmpls);
    }

    public Collection take(Collection tmpls, Transaction txn,
			   long timeout, long maxEntries, byte[] filter)
	throws UnusableEntriesException, TransactionException, RemoteException,
	       FilterRejectedException
    {
	if (filter == null) throw new NullPointerException("filter");
	// Figure out the max time this query should last
	final long endTime = calcEndTime(timeout);

	long remaining = timeout;
	OutriggerServer.QueryCookie queryCookie = null;
	final EntryRep[] treps = repFor(tmpls, "tmpls", entryFormat);

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
	    logQuery("take(multiple,filtered)", serverTimeout, queryCookie, remaining);

	    final Object rslt =
		space.take(treps, txn, serverTimeout, limit, queryCookie, filter);
	    if (rslt == null) {
		// should never get null from a non-ifExists query
		throw new AssertionError("space.take<multiple>(filtered) returned null");
	    } else if (rslt instanceof EntryRep[]) {
		EntryRep[] reps = (EntryRep[])rslt;
		// Got an answer, return it
		final Collection entries = new LinkedList();
		Collection exceptions = null;

		for (int i=0,l=reps.length; i<l ; i++) {
		    try {
			Iterator tmplsIt = tmpls.iterator();
			while (tmplsIt.hasNext()){ // Try each template for class resolution.
			    Entry tmpl = (Entry) tmplsIt.next();
			    Entry e = entryFrom(reps[i], tmpl);
			    if (e != null) {
				entries.add(e);
				break;
			    }
			}
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
		    "Unexpected return type from space.take<multiple>(filtered)");
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

    /* We break up lease creation into two methods. newLease takes
     * care of converting from duration to expiration and we should
     * never need to override (so we make it final), while
     * constructLease takes care of invoking the right constructor for
     * the given context (e.g. constrainable v. not) 
     */

    /** Create a new lease with the specified id and initial duration
     * @param uuid lease id
     * @param duration lease duration in milliseconds, note actual lease duration
     *			granted may be less.
     * @return  new LandlordLease.
     */
    final protected Lease newLease(Uuid uuid, long duration) {
	long expiration = duration + System.currentTimeMillis();

	// We added two positive numbers, so if the result is negative
	// we must have overflowed, so use Long.MAX_VALUE
	if (expiration < 0)
	    expiration = Long.MAX_VALUE;
	return constructLease(uuid, expiration);
    }

    /** Create a new lease with the specified id and initial expiration
     * @param uuid lease id.
     * @param expiration time lease expires, in milliseconds since epoch.
     * @return new LandlordLease.
     */
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

    static EntryRep[] repFor(Collection entries, String argName, MarshallingFormat format)
	throws MarshalException
    {
	final EntryRep[] reps = new EntryRep[entries.size()];
	int j = 0;
	for (Iterator i=entries.iterator(); i.hasNext(); ) {
	    final Object e = i.next();
	    if (!(e == null || e instanceof Entry))
		throw new IllegalArgumentException(
		    argName + " contatins an element which is not an Entry");

	    reps[j++] = repFor((Entry)e, format);
	}

	return reps;
    }

    /**
     * Return an <code>EntryRep</code> object for the given
     * <code>Entry</code>, marshalling its field values under
     * <code>format</code> -- the caller's space's single born format
     * (see {@link #entryFormat}). A pre-computed {@link SnapshotRep} is
     * returned as-is: it was already marshalled (via {@link #snapshot})
     * under the same proxy's born format, so it is already in
     * <code>format</code>.
     */
    static EntryRep repFor(Entry entry, MarshallingFormat format) throws MarshalException {
	if (entry == null)
	    return null;
	if (entry instanceof SnapshotRep)    // snapshots are pre-calculated
	    return ((SnapshotRep) entry).rep();
	return new EntryRep(entry, format);
    }

    /**
     * Return an entry generated from the given rep.
     */
    static Entry entryFrom(EntryRep rep, Entry tmpl) throws UnusableEntryException {
	if (rep == null)
	    return null;
	rep.primeEntryClass(tmpl);
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
		new Object[] {op, Long.valueOf(serverTimeout), cookie,
			      Long.valueOf(remaining)});
	    }
    }
}
