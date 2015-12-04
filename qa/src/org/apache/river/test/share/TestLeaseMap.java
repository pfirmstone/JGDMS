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

import net.jini.core.lease.*;

import java.rmi.RemoteException;
import java.util.Map;
import java.util.Iterator;
import java.util.HashMap;
import java.util.ConcurrentModificationException;

import net.jini.config.Configuration;
import net.jini.export.Exporter;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.ProxyTrust;
import net.jini.security.TrustVerifier;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.MethodConstraints;

/**
 * Implementaion of <code>LeaseMap</code> for <code>TestLease</code>.
 *
 * @see LandlordLease
 * @see net.jini.core.lease.LeaseMap
 */
class TestLeaseMap extends OurAbstractLeaseMap implements RemoteMethodControl {
    /**
     * Home which this map will talk to.
     *
     * @serial
     */
    private LeaseBackEnd home;

    /**
     * Create a new <code>TestLeaseMap</code>.
     * @param home     Owner of the leases that go in this map.
     * @param lease    First lease to be placed in the map.  It is
     *                 assumed that <code>canContainKey(lease)</code>
     *                 is <code>true</code>.  Must work with the 
     *                 lease backend protocol.
     * @param duration The duration the lease should be renewed for if 
     *                 <code>renewAll</code> is called.
     */
    TestLeaseMap(LeaseBackEnd home, Lease lease, long duration) {
	super(lease, duration);
	this.home = home;
    }

    // inherit doc comment
    public boolean canContainKey(Object key) {
	if (key instanceof TestLease)
	    return home.equals(((TestLease)key).home());

	return false;
    }

    // inherit doc comment
    public void cancelAll() throws LeaseMapException, RemoteException {
	int[] ids = new int[size()];
	TestLease[] leases = new TestLease[ids.length];
	Iterator it = keySet().iterator();
	for (int i = 0; it.hasNext(); i++) {
	    TestLease lease = (TestLease)it.next();
	    leases[i] = lease;
	    ids[i] = lease.id();
	}

	try {
	    Object rslt = home.cancelAll(ids);
	    TestLease.throwIt(rslt);
	} catch (LeaseMapException e) {
	    e.printStackTrace();
	    // translate the cookie->exception map into a lease->exception map
	    Map map = e.exceptionMap;
	    int origSize = map.size();
	    for (int i = 0; i < ids.length; i++) {
		Object exception = map.remove(new Integer(ids[i]));
		                                           // harmless if no map
		if (exception != null) {		   // if there was a map
		    map.put(leases[i], exception);	   // put back as lease
		    remove(leases[i]);			   // remove from map
		}
	    }
	    if (origSize != map.size())		// some cookie wasn't found
		throw new ConcurrentModificationException();
	    throw e;
	}
    }

    // inherit doc comment
    public void renewAll() throws LeaseMapException, RemoteException {
	int[] ids          = new int[size()];
	long[] extensions  = new long[ids.length];
	TestLease[] leases = new TestLease[ids.length];
	Iterator it = keySet().iterator();
	for (int i = 0; it.hasNext(); i++) {
	    TestLease lease = (TestLease)it.next();
	    leases[i] = lease;
	    ids[i] = lease.id();
	    extensions[i] = ((Long)get(lease)).longValue();
	}

	long now = System.currentTimeMillis();
	Object rslt = home.renewAll(ids, extensions);
	if (rslt instanceof Throwable) 
	    TestLease.throwIt(rslt);

	LeaseBackEnd.RenewResults results = (LeaseBackEnd.RenewResults)rslt;

	Map bad = null;
	int d = 0;
	for (int i = 0; i < ids.length; i++) {
	    if (results.granted[i] != -1) {
		long newExp = now + results.granted[i];
		if (newExp < 0)
		    newExp = Long.MAX_VALUE;
		leases[i].setExpiration(newExp);
	    } else {
		if (bad == null) {
		    bad = new HashMap(results.denied.length +
				      results.denied.length / 2);
		}
		Object badTime = remove(leases[i]);	// remove from this map
		if (badTime == null)			// better be in there
		    throw new ConcurrentModificationException();
		bad.put(leases[i], results.denied[d++]);// add to "bad" map
	    }
	}

	if (bad != null)
	    throw new LeaseMapException("renewing", bad);
    }

    protected class IteratorImpl implements ProxyTrustIterator {
	private boolean hasNextFlag = true;
	private ProxyTrust proxy;

	IteratorImpl(ProxyTrust proxy) {
	    this.proxy = proxy;
	}

	public boolean hasNext() {
	    return hasNextFlag;
	}

	public Object next() throws RemoteException {
	    hasNextFlag = false;
	    return proxy;
	}

	public void setException(RemoteException e) {
	}
    }

    protected ProxyTrustIterator getProxyTrustIterator() {
	return new IteratorImpl(home);
    }

    public RemoteMethodControl setConstraints(MethodConstraints constraints) {
	((RemoteMethodControl) home).setConstraints(constraints);
	return this;
    }

    public MethodConstraints getConstraints() {
	return ((RemoteMethodControl) home).getConstraints();
    }
}
