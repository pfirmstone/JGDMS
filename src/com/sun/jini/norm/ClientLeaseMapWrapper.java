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

import com.sun.jini.proxy.ConstrainableProxyUtil;

import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lease.LeaseMapException;

/**
 * Class that wraps LeaseMap created by client Leases.  Provides hooks
 * for synchronization and data associated with each client lease while
 * allowing us to use <code>LeaseRenewalManager</code>.  Objects of this
 * class are returned by <code>createLeaseMap</code> calls made on
 * <code>ClientLeaseWrapper</code> objects that are not deformed. <p>
 * 
 * This class only allows as keys ClientLeaseWrappers that are non-deformed.
 * Internally the mapping from ClientLeaseWrappers to longs is held in two
 * Maps.  The first is a LeaseMap that is created by the client lease
 * associated with the first ClientLeaseWrapper added to the set.  The second
 * is a Map from client leases to the ClientLeaseWrappers that wrap them.
 *
 * @author Sun Microsystems, Inc.
 * @see ClientLeaseWrapper
 */
class ClientLeaseMapWrapper extends AbstractMap implements LeaseMap {
    private static final long serialVersionUID = 1L;

    /** Methods for converting lease constraints to lease map constraints. */
    private static final Method[] leaseToLeaseMapMethods;
    static {
	try {
	    Method cancel =
		Lease.class.getMethod("cancel", new Class[] { });
	    Method cancelAll =
		LeaseMap.class.getMethod("cancelAll", new Class[] { });
	    Method renew =
		Lease.class.getMethod("renew", new Class[] { long.class });
	    Method renewAll =
		LeaseMap.class.getMethod("renewAll", new Class[] { });
	    leaseToLeaseMapMethods = new Method[] {
		cancel, cancelAll, renew, renewAll };
	} catch (NoSuchMethodException e) {
	    throw new NoSuchMethodError(e.getMessage());
	}
    }

    /**
     * LeaseMap created by client lease, mapping client leases to Long
     * expiration times.
     *
     * @serial
     */
    private final LeaseMap clientLeaseMap;

    /**
     * Map from client leases to ClientLeaseWrapper instances.
     *
     * @serial
     */
    private final Map wrapperMap = new HashMap();

    /**
     * Retain initial wrapper so canContainKey can use it to determine if
     * a specified lease may be added.
     *
     * @serial
     */
    private final ClientLeaseWrapper example;

    /**
     * Create a ClientLeaseMapWrapper object that will hold
     * the specified client Lease.
     * @param wrapper a wrapper for the lease that wants to be renewed
     * @param duration the duration to associate with wrapper
     * @throws IllegalArgumentException if wrapper is deformed
     */
    ClientLeaseMapWrapper(ClientLeaseWrapper wrapper, long duration) {
	final Lease clientLease = wrapper.getClientLease();
	if (clientLease == null) {
	    throw new IllegalArgumentException("Wrapper cannot be deformed");
	}
	LeaseMap leaseMap = clientLease.createLeaseMap(duration);
	if (clientLease instanceof RemoteMethodControl &&
	    leaseMap instanceof RemoteMethodControl)
	{
	    leaseMap = (LeaseMap)
		((RemoteMethodControl) leaseMap).setConstraints(
		    ConstrainableProxyUtil.translateConstraints(
			((RemoteMethodControl) clientLease).getConstraints(),
			leaseToLeaseMapMethods));
	}
	clientLeaseMap = leaseMap;
	wrapperMap.put(clientLease, wrapper);
	example = wrapper;
    }

    // inherit javadoc
    public void cancelAll() {
	throw new UnsupportedOperationException(
	     "ClientLeaseMapWrapper.cancelAll: " + 
	     "LRS should not being canceling client leases");
    }

    /**
     * For each lease in the map, call failedRenewal
     */
    private void applyException(Throwable t) {
	for (Iterator i=wrapperMap.values().iterator(); i.hasNext(); ) {
	    final ClientLeaseWrapper clw = (ClientLeaseWrapper) i.next();
	    clw.failedRenewal(t);
	}	
    }

    // inherit javadoc
    // This method assumes that none of the leases in the map are
    // also being renewed by some other thread.
    public void renewAll() throws LeaseMapException, RemoteException {
	LeaseMapException lme = null;
	Map newExceptionMap = null;

	// Iterate over the wrappers, causing the appropriate exceptions
	// for the ones who's sets have expired.
	final long now = System.currentTimeMillis();
	for (Iterator i=wrapperMap.values().iterator(); i.hasNext(); ) {
	    final ClientLeaseWrapper clw = (ClientLeaseWrapper) i.next();
	    if (!clw.ensureCurrent(now)) {
		// Map an exception to this lease and drop from the map

		// If necessary create newExceptionMap
		if (newExceptionMap == null)
		    newExceptionMap = new HashMap(wrapperMap.size());

		// Add to the newExceptionMap
		newExceptionMap.put(clw,
				    LRMEventListener.EXPIRED_SET_EXCEPTION);

		// Drop from both the wrapper and inner map
		i.remove();
		clientLeaseMap.remove(clw.getClientLease());

		// Note, we don't call failedRenewal() because the set is
		// dead so logging changes is pointless.  Besides, there
		// is no change to log.
	    }
	}

	// If the map is now empty don't bother calling renewAll()
	if (clientLeaseMap.isEmpty()) {
	    if (newExceptionMap == null)
		return;

	    throw new LeaseMapException("Expired Sets", newExceptionMap);
	}

	try {
	    clientLeaseMap.renewAll();
	} catch (LeaseMapException e) {
	    lme = e;
	} catch (RemoteException e) {
	    applyException(e);
	    throw e;
	} catch (Error e) {
	    applyException(e);
	    throw e;
	} catch (RuntimeException e) {
	    applyException(e);
	    throw e;
	}

	// For each Lease still in the map we need to update the wrapper
	for (Iterator i=clientLeaseMap.keySet().iterator(); i.hasNext(); ) {
	    final Lease cl = (Lease) i.next();
	    final ClientLeaseWrapper clw = 
		(ClientLeaseWrapper) wrapperMap.get(cl);

	    clw.successfulRenewal();
	}

	// If there were no errors just return
	if (lme == null && newExceptionMap == null) 
	    return;

	// If the renewAll() threw a LeaseMapException we have to
	// remove the problem leases from the wrapper and place
	// them in newExceptionMap

	if (lme != null) {
	    final Map exceptionMap = lme.exceptionMap;

	    // Create the newExceptionMap if we don't have one
	    if (newExceptionMap == null) 
		newExceptionMap = new HashMap(exceptionMap.size());

	    // Copy each lease out of the exception's map into newExceptionMap,
	    // also remove these leases from the wrapper and get the
	    // failure logged
	    for (Iterator i = exceptionMap.entrySet().iterator();
		 i.hasNext(); )
	    {
		final Map.Entry e = (Map.Entry) i.next();
		final Lease cl = (Lease) e.getKey();
		final Throwable t = (Throwable) e.getValue(); 
		final ClientLeaseWrapper clw = 
		    (ClientLeaseWrapper) wrapperMap.remove(cl);
		i.remove();

		clw.failedRenewal(t);
		newExceptionMap.put(clw, t);
	    }
	}

	// If necessary throw a LeaseMapException
	if (newExceptionMap != null) {
	    throw new LeaseMapException(
	        (lme == null) ? "Expired Sets" : lme.getMessage(),
		newExceptionMap);
	}

	return;
    }

    // inherit javadoc
    public boolean canContainKey(Object key) {
	return key instanceof Lease &&
	    example.canBatch((Lease) key);
    }

    /**
     * Check that the key is valid for this map, if it is return the client
     * lease, if not throw IllegalArgumentException.
     */
    private Lease checkKey(Object key) {
	if (canContainKey(key))
	    return ((ClientLeaseWrapper) key).getClientLease();

	throw new IllegalArgumentException(
	    "key is not valid for this LeaseMap");
    }

    /** Check that the value is a Long. */
    private static void checkValue(Object value) {
	if (!(value instanceof Long))
	    throw new IllegalArgumentException("value is not a Long");
    }

    // inherit javadoc
    public boolean containsKey(Object key) {
	final Lease cl = checkKey(key);	
	return clientLeaseMap.containsKey(cl);
    }

    // inherit javadoc
    public boolean containsValue(Object value) {
	checkValue(value);
	return clientLeaseMap.containsValue(value);
    }

    // inherit javadoc
    public Object get(Object key) {
	final Lease cl = checkKey(key);	
	return clientLeaseMap.get(cl);
    }

    // inherit javadoc
    public Object put(Object key, Object value) {
	final Lease cl = checkKey(key);
	checkValue(value);
	
	// At this point we know key is a ClientLeaseWrapper

	/*
	 * Since there is a 1:1 mapping between wrappers and client
	 * leases, and, once we get going, we never have two wrapper
	 * objects that != but are .equals(), if key is already in this
	 * map, then wrapperMap.put() will replace the existing copy of
	 * key with a == copy. This gives us the key non-replacement
	 * semantics Map.put() should have.
	 */
	wrapperMap.put(cl, key);
	return clientLeaseMap.put(cl, value);
    }

    // inherit javadoc
    public Object remove(Object key) {
	final Lease cl = checkKey(key);

	wrapperMap.remove(cl);
	return clientLeaseMap.remove(cl);
    }

    // inherit javadoc
    public void putAll(Map m) {
	Iterator iter = m.entrySet().iterator();
	while (iter.hasNext()) {
	    Map.Entry e = (Map.Entry) iter.next();
	    put(e.getKey(), e.getValue());
	}
    }

    // inherit javadoc
    public void clear() {
	clientLeaseMap.clear();
	wrapperMap.clear();
    }

    // inherit javadoc
    public boolean equals(Object o) {
	return clientLeaseMap.equals(o); // XXX should sameDestination matter?
    }

    // inherit javadoc
    public int hashCode() {
	return clientLeaseMap.hashCode();
    }

    // inherit javadoc
    public Set entrySet() {
	return new EntrySet();
    }

    /*
     * Classes that we use to implement entrySet()
     */

    /**
     * An implementation of Set backed by the ClientLeaseMapWrapper's
     * mappings, which are from wrapperMap's values to clientLeaseMap
     */
    private final class EntrySet extends AbstractSet {
	// inherit javadoc
	public Iterator iterator() {
	    return new EntryIterator();
	}

	/**
	 * If the passed object is a Map.Entry that is in the
	 * ClientMapWrapper return the client lease associated with it,
	 * otherwise return null
	 */
	private Lease getClientLease(Object o) {
	    if (!(o instanceof Map.Entry))
		return null;

	    final Map.Entry e = (Map.Entry) o;
	    final Object eValue = e.getValue();

	    if (!(e.getKey() instanceof ClientLeaseWrapper) ||
		!(eValue instanceof Long) ||
		(eValue == null))
	    {
		return null;
	    }

	    final ClientLeaseWrapper clw = (ClientLeaseWrapper) e.getKey();
	    // Note if clw is deformed this call will return null, this is
	    // the right thing since a deformed lease can't be in this map
	    return clw.getClientLease();
	}

	// inherit javadoc
	public boolean contains(Object o) {
	    final Lease cl = getClientLease(o);

	    if (cl == null)
		return false;

	    final Object eValue = ((Map.Entry) o).getValue();
	    final Object value = clientLeaseMap.get(cl);
	    if (value == null)
		return false;

	    return value.equals(eValue);
	}

	// inherit javadoc
	public boolean remove(Object o) {
	    final Lease cl = getClientLease(o);

	    if (cl == null)
		return false;

	    final Object eValue = ((Map.Entry) o).getValue();
	    final Object value = clientLeaseMap.get(cl);
	    if (value == null || !value.equals(eValue))
		return false;

	    
	    // Use cl to remove the data from the clientLeaseMap
	    clientLeaseMap.remove(cl);
	    wrapperMap.remove(cl);

	    return true;
	}

	// inherit javadoc
	public int size() {
	    return clientLeaseMap.size();
	}

	// inherit javadoc
	public void clear() {
	    wrapperMap.clear();
	    clientLeaseMap.clear();
	}
    }

    /** Our implementation of Map.Entry */
    private final class Entry implements Map.Entry {
	/** The key */
	private final ClientLeaseWrapper key;

	public Entry(ClientLeaseWrapper key) {
	    this.key = key;
	}

	public Object getKey() {
	    return key;
	}

	public Object getValue() {
	    return clientLeaseMap.get(key.getClientLease());
	}

	public Object setValue(Object value) {
	    checkValue(value);
	    return clientLeaseMap.put(key.getClientLease(), value);
	}

	public boolean equals(Object o) {
	    if (o instanceof Entry) {
		final Entry that = (Entry) o;
		return that.key.equals(key);
	    }

	    return false;
	}

	public int hashCode() {
	    return key.hashCode();
	}
    }

    /**
     * An implementation of Iterator backed by the ClientMapWrapper's mappings,
     * which are from wrapperMap's values to clientLeaseMap
     */
    private final class EntryIterator implements Iterator {
	/** Iterator over the wrapperMap values */
	private final Iterator iter;

	/** Lease associated with the last value returned by next() */
	private Lease last;

	public EntryIterator() {
	    iter = wrapperMap.entrySet().iterator();
	}

	public boolean hasNext() {
	    return iter.hasNext();
	}

	public Object next() {
	    final Map.Entry e = (Map.Entry) iter.next();	    	    
	    last = (Lease) e.getKey();		    
	    return new Entry((ClientLeaseWrapper) e.getValue());
	}

	public void remove() {
	    // Use last to remove the data from the clientLeaseMap
	    clientLeaseMap.remove(last);
	    // use iter.remove() to remove from wrapperMap so we don't
	    // get a concurrent access exception
	    iter.remove();
	}
    }
}
