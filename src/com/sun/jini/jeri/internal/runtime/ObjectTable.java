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

package com.sun.jini.jeri.internal.runtime;

import com.sun.jini.jeri.internal.runtime.ImplRefManager.ImplRef;
import com.sun.jini.logging.Levels;
import com.sun.jini.thread.NewThreadAction;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.rmi.Remote;
import java.rmi.server.ExportException;
import java.rmi.server.Unreferenced;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.export.ServerContext;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalInputStream;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.BasicInvocationDispatcher;
import net.jini.jeri.InvocationDispatcher;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.ServerCapabilities;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.security.Security;
import net.jini.security.SecurityContext;

/**
 * A table of exported remote objects.
 *
 * @author Sun Microsystems, Inc.
 **/
final class ObjectTable {

    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.BasicJeriExporter");

    private static final Collection dgcDispatcherMethods = new ArrayList(2);
    static {
	Method[] methods = DgcServer.class.getMethods();
	for (int i = 0; i < methods.length; i++) {
	    final Method m = methods[i];
	    AccessController.doPrivileged(new PrivilegedAction() {
		public Object run() {
		    m.setAccessible(true);
		    return null;
		}
	    });
	    dgcDispatcherMethods.add(m);
	}
    }

    private static final ServerCapabilities dgcServerCapabilities =
	new ServerCapabilities() {
	    public InvocationConstraints checkConstraints(
		InvocationConstraints constraints)
		throws UnsupportedConstraintException
	    {
		assert constraints.equals(InvocationConstraints.EMPTY);
		return InvocationConstraints.EMPTY;
	    }
	};

    /**
     * lock to serialize request dispatcher reservation per export, so
     * that a partial export will not cause another export to fail
     * unnecessarily
     **/
    private final Object requestDispatchersLock = new Object();

    /** table of references to impls exported with DGC */
    private final ImplRefManager implRefManager = new ImplRefManager();

    /** lock guarding keepAliveCount and keeper */
    private final Object keepAliveLock = new Object();

    /** number of objects exported with keepAlive == true */
    private int keepAliveCount = 0;

    /** thread to keep VM alive while keepAliveCount > 0 */
    private Thread keeper = null;

    /** maps client ID to Lease (lock guards leaseChecker too) */
    private final Map leaseTable = new HashMap();

    /** thread to check for expired leases */
    private Thread leaseChecker = null;

    ObjectTable() { }

    RequestDispatcher createRequestDispatcher(Unreferenced unrefCallback) {
	return new RD(unrefCallback);
    }

    boolean isReferenced(RequestDispatcher requestDispatcher) {
	return getRD(requestDispatcher).isReferenced();
    }

    Target export(Remote impl,
		  RequestDispatcher[] requestDispatchers,
		  boolean allowDGC,
		  boolean keepAlive,
		  Uuid id)
        throws ExportException
    {
	RD[] rds = new RD[requestDispatchers.length];
	for (int i = 0; i < requestDispatchers.length; i++) {
	    rds[i] = getRD(requestDispatchers[i]);
	}

	return new Target(impl, id, rds, allowDGC, keepAlive);
    }

    private RD getRD(RequestDispatcher requestDispatcher) {
	/*
	 * The following cast will throw a ClassCastException if we were
	 * passed a RequestDispatcher that was not returned by this class's
	 * createRequestDispatcher method:
	 */
	RD rd = (RD) requestDispatcher;
	if (!rd.forTable(this)) {
	    throw new IllegalArgumentException(
		"request dispatcher for different object table");
	}
	return rd;
    }

    /**
     * Increments the count of objects exported with keepAlive true,
     * starting a non-daemon thread if necessary.
     **/
    private void incrementKeepAliveCount() {
	synchronized (keepAliveLock) {
	    keepAliveCount++;

	    if (keeper == null) {
		keeper = (Thread) AccessController.doPrivileged(
		    new NewThreadAction(new Runnable() {
			public void run() {
			    try {
				while (true) {
				    Thread.sleep(Long.MAX_VALUE);
				}
			    } catch (InterruptedException e) {
				// pass away if interrupted
			    }
			}
		    }, "KeepAlive", false));
		keeper.start();
	    }
	}
    }

    /**
     * Decrements the count of objects exported with keepAlive true,
     * stopping the non-daemon thread if decremented to zero.
     **/
    private void decrementKeepAliveCount() {
	synchronized (keepAliveLock) {
	    keepAliveCount--;

	    if (keepAliveCount == 0) {
		assert keeper != null;
		AccessController.doPrivileged(new PrivilegedAction() {
		    public Object run() {
			keeper.interrupt();
			return null;
		    }
		});
		keeper = null;
	    }
	}
    }

    /**
     * A Target is returned by the export method to represent the object
     * exported to this ObjectTable.  It can be used to unexport the
     * exported object.
     */
    final class Target {

	private final ImplRef implRef;
	private final Uuid id;
	private final RD[] requestDispatchers;
	private final boolean allowDGC;
	private final boolean keepAlive;
	private final SecurityContext securityContext;
	private final ClassLoader ccl;

	/** lock guarding all mutable instance state (below) */
	private final Object lock = new Object();
	private InvocationDispatcher invocationDispatcher;
	private boolean exported = false;
	private int callsInProgress = 0;
	private final Set referencedSet;
	private final Map sequenceTable;

	Target(Remote impl,
	       Uuid id,
	       RD[] requestDispatchers,
	       boolean allowDGC,
	       boolean keepAlive)
	    throws ExportException
	{
	    this.id = id;
	    this.requestDispatchers = requestDispatchers;
	    this.allowDGC = allowDGC;
	    this.keepAlive = keepAlive;

	    securityContext = Security.getContext();
	    ccl = Thread.currentThread().getContextClassLoader();

	    synchronized (requestDispatchersLock) {
		boolean success = false;
		int i = 0;
		try {
		    for (i = 0; i < requestDispatchers.length; i++) {
			requestDispatchers[i].put(this);
		    }
		    success = true;
		} finally {
		    if (!success) {
			for (int j = 0; j < i; j++) {
			    requestDispatchers[i].remove(this, false);
			}
		    }
		}
	    }

	    implRef = implRefManager.getImplRef(impl, this);

	    if (allowDGC) {
		referencedSet = new HashSet(3);
		sequenceTable = new HashMap(3);
	    } else {
		referencedSet = null;
		sequenceTable = null;
	    }

	    if (keepAlive) {
		incrementKeepAliveCount();
	    }

	    synchronized (lock) {
		exported = true;
	    }
	}

	void setInvocationDispatcher(InvocationDispatcher id) {
	    assert id != null;
	    synchronized (lock) {
		assert invocationDispatcher == null;
		invocationDispatcher = id;
	    }
	}

	boolean unexport(boolean force) {
	    synchronized (lock) {
		if (!exported) {
		    return true;
		}
		if (!force && callsInProgress > 0) {
		    return false;
		}
		exported = false;

		if (keepAlive && callsInProgress == 0) {
		    decrementKeepAliveCount();
		}

		if (allowDGC) {
		    if (!referencedSet.isEmpty()) {
			for (Iterator i = referencedSet.iterator();
			     i.hasNext();)
			{
			    Uuid clientID = (Uuid) i.next();
			    unregisterTarget(this, clientID);
			}
			referencedSet.clear();
		    }
		    sequenceTable.clear();
		}
	    }

	    implRef.release(this);

	    for (int i = 0; i < requestDispatchers.length; i++) {
		requestDispatchers[i].remove(this, false);
	    }
	    return true;
	}

	void collect() {
	    synchronized (lock) {
		if (!exported) {
		    return;
		}

		if (logger.isLoggable(Level.FINE)) {
		    logger.log(Level.FINE,
			"garbage collection of object with id {0}", id);
		}

		exported = false;

		if (keepAlive && callsInProgress == 0) {
		    decrementKeepAliveCount();
		}

		if (allowDGC) {
		    assert referencedSet.isEmpty();
		    sequenceTable.clear();
		}
	    }

	    for (int i = 0; i < requestDispatchers.length; i++) {
		requestDispatchers[i].remove(this, true);
	    }
	}

	Uuid getObjectIdentifier() {
	    return id;
	}

	// used by ImplRef for invoking Unreferenced.unreferenced
	boolean getEnableDGC() {
	    return allowDGC;
	}

	// used by ImplRef for invoking Unreferenced.unreferenced
	SecurityContext getSecurityContext() {
	    return securityContext;
	}

	// used by ImplRef for invoking Unreferenced.unreferenced
	ClassLoader getContextClassLoader() {
	    return ccl;
	}

	void referenced(Uuid clientID, long sequenceNum) {
	    if (!allowDGC) {
		return;	// ignore if DGC not enabled for this object
	    }

	    synchronized (lock) {
		if (!exported) {
		    return;
		}

		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST,
			"this={0}, clientID={1}, sequenceNum={2}",
			new Object[] {
			    this, clientID, new Long(sequenceNum)
			});
		}

		/*
		 * Check current sequence number against the last
		 * recorded sequence number for the client.  If the
		 * current value is lower, then this is a "late dirty
		 * call", which should not be processed.  Otherwise,
		 * update the last recorded sequence number.
		 */
		SequenceEntry entry =
		    (SequenceEntry) sequenceTable.get(clientID);
		if (entry == null) {
		    // no record: must assume this is not a late dirty call
		    entry = new SequenceEntry(sequenceNum);
		    sequenceTable.put(clientID, entry);
		} else if (sequenceNum < entry.sequenceNum) {
		    return;	// late dirty call: ignore
		} else {
		    entry.sequenceNum = sequenceNum;
		}

		if (!referencedSet.contains(clientID)) {
		    if (referencedSet.isEmpty()) {
			Remote impl = implRef.getImpl();
			if (impl == null) {
			    return;	// too late if impl was collected
			}
			implRef.pin(this);
		    }
		    referencedSet.add(clientID);

		    registerTarget(this, clientID);
		}
	    }
	}

	void unreferenced(Uuid clientID, long sequenceNum, boolean strong) {
	    if (!allowDGC) {
		return;	// ignore if DGC not enabled for this object
	    }

	    synchronized (lock) {
		if (!exported) {
		    return;
		}

		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST,
			"this={0}, clientID={1}, sequenceNum={2}, strong={3}",
			new Object[] {
			    this, clientID, new Long(sequenceNum),
			    Boolean.valueOf(strong)
			});
		}

		/*
		 * Check current sequence number against the last
		 * recorded sequence number for the client.  If the
		 * current value is lower, then this is a "late clean
		 * call", which should not be processed.  Otherwise:
		 * if this is for a strong clean call, then update the
		 * last recorded sequence number; if no strong clean
		 * call has been processed for this client, discard
		 * its sequence number record.
		 */
		SequenceEntry entry =
		    (SequenceEntry) sequenceTable.get(clientID);
		if (entry == null) {
		    // no record: must assume this is not a late clean call
		    if (strong) {
			entry = new SequenceEntry(sequenceNum);
			sequenceTable.put(clientID, entry);
			entry.keep = true;
		    }
		} else if (sequenceNum < entry.sequenceNum) {
		    return;	// late clean call: ignore
		} else if (strong) {
		    entry.sequenceNum = sequenceNum;
		    entry.keep = true;	// strong clean: retain sequence number
		} else if (!entry.keep) {
		    sequenceTable.remove(clientID);
		}

		unregisterTarget(this, clientID);

		if (referencedSet.remove(clientID) &&
		    referencedSet.isEmpty())
		{
		    implRef.unpin(this);
		}
	    }
	}

	void leaseExpired(Uuid clientID) {
	    assert allowDGC;

	    synchronized (lock) {
		if (!exported) {
		    return;
		}

		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST,
			"this={0}, clientID={1}",
			new Object[] { this, clientID });
		}

		SequenceEntry entry =
		    (SequenceEntry) sequenceTable.get(clientID);
		if (entry != null && !entry.keep) {
		    /*
		     * REMIND: We could be removing the sequence number
		     * for a more recent lease, thus allowing a "late
		     * clean call" to be inappropriately processed?
		     * (See 4848840 Comments.)
		     */
		    sequenceTable.remove(clientID);
		}

		if (referencedSet.remove(clientID) &&
		    referencedSet.isEmpty())
		{
		    implRef.unpin(this);
		}
	    }
	}

	void dispatch(InboundRequest request)
	    throws IOException, NoSuchObject
	{
	    InvocationDispatcher id;
	    synchronized (lock) {
		if (!exported || invocationDispatcher == null) {
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(Level.FINEST,
			    "this={0}, not exported", this);
		    }
		    throw new NoSuchObject();
		}
		id = invocationDispatcher; // save for reference outside lock
		callsInProgress++;
	    }
	    try {
		Remote impl = implRef.getImpl();
		if (impl == null) {
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(Level.FINEST,
			    "this={0}, garbage collected", this);
		    }
		    throw new NoSuchObject();
		}

		dispatch(request, id, impl);

	    } finally {
		synchronized (lock) {
		    assert callsInProgress > 0;
		    callsInProgress--;

		    if (keepAlive && !exported && callsInProgress == 0) {
			decrementKeepAliveCount();
		    }
		}
	    }
	}

	private void dispatch(final InboundRequest request,
			      final InvocationDispatcher id,
			      final Remote impl)
	    throws IOException, NoSuchObject
	{
	    Thread t = Thread.currentThread();
	    ClassLoader savedCcl = t.getContextClassLoader();
	    try {
		if (ccl != savedCcl) {
		    t.setContextClassLoader(ccl);
		}
		AccessController.doPrivileged(securityContext.wrap(
		    new PrivilegedExceptionAction() {
			public Object run() throws IOException {
			    dispatch0(request, id, impl);
			    return null;
			}
		    }), securityContext.getAccessControlContext());
			    
	    } catch (java.security.PrivilegedActionException e) {
		throw (IOException) e.getException();
	    } finally {
		if (ccl != savedCcl || savedCcl != t.getContextClassLoader()) {
		    t.setContextClassLoader(savedCcl);
		}
	    }
	}

	private void dispatch0(final InboundRequest request,
			       final InvocationDispatcher id,
			       final Remote impl)
	    throws IOException
	{
	    request.checkPermissions();

	    OutputStream out = request.getResponseOutputStream();
	    out.write(Jeri.OBJECT_HERE);

	    final Collection context = new ArrayList(5);
	    request.populateContext(context);

	    ServerContext.doWithServerContext(new Runnable() {
		public void run() {
		    id.dispatch(impl, request, context);
		}
	    }, Collections.unmodifiableCollection(context));
	}

	public String toString() {	// for logging
	    return "Target@" + Integer.toHexString(hashCode()) +
		"[" + id + "]";
	}
    }

    private static final class SequenceEntry {
	long sequenceNum;
	boolean keep;

	SequenceEntry(long sequenceNum) {
	    this.sequenceNum = sequenceNum;
	}
    }

    void registerTarget(Target target, Uuid clientID) {
	synchronized (leaseTable) {
	    Lease lease = (Lease) leaseTable.get(clientID);
	    if (lease == null) {
		target.leaseExpired(clientID);
	    } else {
		synchronized (lease.notifySet) {
		    lease.notifySet.add(target);
		}
	    }
	}
    }

    void unregisterTarget(Target target, Uuid clientID) {
	synchronized (leaseTable) {
	    Lease lease = (Lease) leaseTable.get(clientID);
	    if (lease != null) {
		synchronized (lease.notifySet) {
		    lease.notifySet.remove(target);
		}
	    }
	}
    }

    /**
     * RequestDispatcher implementation.
     **/
    private class RD implements RequestDispatcher {

	private final Unreferenced unrefCallback;

	private final Map idTable = new HashMap();
	private int dgcEnabledCount = 0;	// guarded by idTable lock

	private final InvocationDispatcher dgcDispatcher;
	private final DgcServerImpl dgcServerImpl;

	RD(Unreferenced unrefCallback) {
	    this.unrefCallback = unrefCallback;
	    try {
		dgcDispatcher =
		    new BasicInvocationDispatcher(
			dgcDispatcherMethods, dgcServerCapabilities,
			null, null, this.getClass().getClassLoader())
		    {
			protected ObjectInputStream createMarshalInputStream(
			    Object impl,
			    InboundRequest request,
			    boolean integrity,
			    Collection context)
			    throws IOException
			{
			    ClassLoader loader = getClassLoader();
			    return new MarshalInputStream(
				request.getRequestInputStream(),
				loader, integrity, loader,
				Collections.unmodifiableCollection(context));
			    // useStreamCodebases() not invoked
			}
		    };
	    } catch (ExportException e) {
		throw new AssertionError();
	    }
	    dgcServerImpl = new DgcServerImpl();
	}

	boolean forTable(ObjectTable table) {
	    return ObjectTable.this == table;
	}

	boolean isReferenced() {
	    synchronized (idTable) {
		return !idTable.isEmpty();
	    }
	}

	Target get(Uuid id) {
	    synchronized (idTable) {
		return (Target) idTable.get(id);
	    }
	}

	void put(Target target) throws ExportException {
	    synchronized (idTable) {
		Uuid id = target.getObjectIdentifier();
		if (id.equals(Jeri.DGC_ID)) {
		    throw new ExportException(
			"object identifier reserved for DGC");
		}
		if (idTable.containsKey(id)) {
		    throw new ExportException(
			"object identifier already in use");
		}
		idTable.put(id, target);
		if (target.getEnableDGC()) {
		    dgcEnabledCount++;
		}
	    }
	}

	void remove(Target target, boolean gc) {
	    boolean empty = false;
	    synchronized (idTable) {
		Uuid id = target.getObjectIdentifier();
		assert idTable.get(id) == target;
		idTable.remove(id);
		if (target.getEnableDGC()) {
		    dgcEnabledCount--;
		    assert dgcEnabledCount >= 0;
		}

		if (idTable.isEmpty()) {
		    empty = true;
		}
	    }

	    if (gc && empty) {
		/*
		 * We have to be careful to make this callback without holding
		 * the lock for idTable, because the callback implementation
		 * will likely be code that calls this object's isReferenced
		 * method in its own synchronized block.
		 */
		unrefCallback.unreferenced();
	    }
	}

	private boolean hasDgcEnabledTargets() {
	    synchronized (idTable) {
		return dgcEnabledCount > 0;
	    }
	}

	public void dispatch(InboundRequest request) {
	    try {
		InputStream in = request.getRequestInputStream();
		Uuid id = UuidFactory.read(in);

		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "id={0}", id);
		}

		try {
		    /*
		     * The DGC object identifier is hardwired here,
		     * rather than install it in idTable; this
		     * eliminates the need to worry about not counting
		     * the DGC server as an exported object in the
		     * table, and it doesn't need all of the machinery
		     * that Target provides.
		     */
		    if (id.equals(Jeri.DGC_ID)) {
			dispatchDgcRequest(request);
			return;
		    }

		    Target target = (Target) get(id);
		    if (target == null) {
			logger.log(Level.FINEST, "id not in table");
			throw new NoSuchObject();
		    }
		    target.dispatch(request);

		} catch (NoSuchObject e) {
		    in.close();
		    OutputStream out = request.getResponseOutputStream();
		    out.write(Jeri.NO_SUCH_OBJECT);
		    out.close();

		    if (logger.isLoggable(Levels.FAILED)) {
			logger.log(Levels.FAILED, "no such object: {0}", id);
		    }
		}
	    } catch (IOException e) {
		request.abort();

		if (logger.isLoggable(Levels.FAILED)) {
		    logger.log(Levels.FAILED,
			       "I/O exception dispatching request", e);
		}
	    }
	}

	private void dispatchDgcRequest(final InboundRequest request)
	    throws IOException, NoSuchObject
	{
	    if (!hasDgcEnabledTargets()) {
		logger.log(Level.FINEST, "no DGC-enabled targets");
		throw new NoSuchObject();
	    }

	    OutputStream out = request.getResponseOutputStream();
	    out.write(Jeri.OBJECT_HERE);

	    final Collection context = new ArrayList(5);
	    request.populateContext(context);

	    ServerContext.doWithServerContext(new Runnable() {
		public void run() {
		    dgcDispatcher.dispatch(dgcServerImpl, request, context);
		}
	    }, Collections.unmodifiableCollection(context));
	}

	private class DgcServerImpl implements DgcServer {

	    public long dirty(Uuid clientID,
			      long sequenceNum,
			      Uuid[] ids)
	    {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST,
			"clientID={0}, sequenceNum={1}, ids={2}",
			new Object[] {
			    clientID, new Long(sequenceNum), Arrays.asList(ids)
			});
		}

		long duration = Jeri.leaseValue;

		synchronized (leaseTable) {
		    Lease lease = (Lease) leaseTable.get(clientID);
		    if (lease == null) {
			leaseTable.put(clientID,
				       new Lease(clientID, duration));
			if (leaseChecker == null) {
			    leaseChecker =
				(Thread) AccessController.doPrivileged(
				    new NewThreadAction(new LeaseChecker(),
					"DGC Lease Checker", true));
			    leaseChecker.start();
			}
		    } else {
			lease.renew(duration);
		    }
		}

		for (int i = 0; i < ids.length; i++) {
		    Target target = get(ids[i]);
		    if (target != null) {
			target.referenced(clientID, sequenceNum);
		    }
		}

		return duration;
	    }

	    public void clean(Uuid clientID,
			      long sequenceNum,
			      Uuid[] ids,
			      boolean strong)
	    {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST,
			"clientID={0}, sequenceNum={1}, ids={2}, strong={3}",
			new Object[] {
			    clientID, new Long(sequenceNum),
			    Arrays.asList(ids), Boolean.valueOf(strong)
			});
		}

		for (int i = 0; i < ids.length; i++) {
		    Target target = get(ids[i]);
		    if (target != null) {
			target.unreferenced(clientID, sequenceNum, strong);
		    }
		}
	    }
	}
    }

    private class LeaseChecker implements Runnable {

	public void run() {
	    boolean done = false;
	    do {
		try {
		    Thread.sleep(Jeri.leaseCheckInterval);
		} catch (InterruptedException e) {
		    // REMIND: shouldn't happen, OK to ignore?
		}

		long now = System.currentTimeMillis();

		Collection expiredLeases = new ArrayList();

		synchronized (leaseTable) {
		    for (Iterator i = leaseTable.values().iterator();
			 i.hasNext();)
		    {
			Lease lease = (Lease) i.next();
			if (lease.hasExpired(now)) {
			    expiredLeases.add(lease);
			    i.remove();
			}
		    }

		    if (leaseTable.isEmpty()) {
			leaseChecker = null;
			done = true;
		    }
		}

		if (expiredLeases.isEmpty()) {
		    continue;
		}

		for (Iterator i = expiredLeases.iterator(); i.hasNext();) {
		    Lease lease = (Lease) i.next();
		    if (lease.notifySet.isEmpty()) {
			continue;
		    }

		    for (Iterator i2 = lease.notifySet.iterator();
			 i2.hasNext();)
		    {
			Target target = (Target) i2.next();
			target.leaseExpired(lease.getClientID());
		    }
		}
	    } while (!done);
	}
    }

    private static class Lease {

	private final Uuid clientID;
	final Set notifySet = new HashSet(3);	// guarded?
	private long expiration;		// guarded by leaseTable lock

	Lease(Uuid clientID, long duration) {
	    this.clientID = clientID;
	    expiration = System.currentTimeMillis() + duration;
	}

	Uuid getClientID() {
	    return clientID;
	}

	void renew(long duration) {
	    long newExpiration = System.currentTimeMillis() + duration;
	    if (newExpiration > expiration) {
		expiration = newExpiration;
	    }
	}

	boolean hasExpired(long now) {
	    return expiration < now;
	}
    }

    private static class NoSuchObject extends Exception { }
}
