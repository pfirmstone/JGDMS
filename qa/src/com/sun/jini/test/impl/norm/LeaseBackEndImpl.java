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
package com.sun.jini.test.impl.norm;

import java.io.ObjectStreamException;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.rmi.Remote;
import java.rmi.RemoteException;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMapException;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.export.Exporter;
import java.io.Serializable;

import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;

import net.jini.export.Exporter;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.security.proxytrust.ServerProxyTrust;
import net.jini.security.TrustVerifier;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.MethodConstraints;

/**
 * Impl of the LeaseBackEnd remote interface for use by renewal service tests
 */
public class LeaseBackEndImpl implements LeaseBackEnd, ServerProxyTrust {
    /** 
     * Map of <code>TestLease</code> ids to <code>LeaseOwners</code>
     */
    final private LeaseOwner[] owners;

    /** 
     * Array of all the leases we have created.
     */
    final private TestLease[] leases;

    /** Counter to generate lease ids */
    private int idGen = 0;

    /** Our stub */
    final private LeaseBackEnd stub;

    /** Total number of calls to renew */
    private long renewCalls;

    /** Total number of calls to renewAll */
    private long renewAllCalls;

    /** Total number of leases renewed over all the renewAllCalls */
    private long totalBatchRenewals;
    

    /**
     * Simple constructor
     * @param leaseCount number of leases we expect this back end to allocate
     * @throws RemoteException if there is a problem exporting the object
     */
    public LeaseBackEndImpl(int leaseCount) throws RemoteException {
	owners = new LeaseOwner[leaseCount];
	leases = new TestLease[leaseCount];
	Exporter exporter = QAConfig.getDefaultExporter();
        try {
	    Configuration c = QAConfig.getConfig().getConfiguration();
	    if (c instanceof com.sun.jini.qa.harness.QAConfiguration) {
		exporter = (Exporter) c.getEntry("test", 
						 "leaseExporter", 
						 Exporter.class);
	    }
	} catch (ConfigurationException e) {
	    throw new RemoteException("Configuration problem", e);
	}
	stub = (LeaseBackEnd)exporter.export(this);
    }

    public Object writeReplace() throws ObjectStreamException {
	return stub;
    }

    /**
     * Create a lease with the specified owner
     */
    public synchronized TestLease newLease(LeaseOwner owner, long expiration) {
	int id = idGen++;
	owners[id] = owner;
	if (stub instanceof RemoteMethodControl) {
	    leases[id] = new ConstrainableTestLease(id, stub, expiration);
	} else {
	    leases[id] = new TestLease(id, stub, expiration);
	}
	// Use are stub directly so comparisons workout
	return leases[id];
    }

    /**
     * Create a lease with the specified owner
     */
    public TestLease newLease(LeaseOwner owner, long expiration, int index) {
	synchronized (this) {
	    owners[index] = owner;
	}
	if (stub instanceof RemoteMethodControl) {
	    leases[index] = new ConstrainableTestLease(index, stub, expiration);
	} else {
	    leases[index] = new TestLease(index, stub, expiration);
	}
	// Use are stub directly so comparisons workout
	return leases[index];
    }

    /** Given a lease return the lease's owner */
    synchronized LeaseOwner getOwner(TestLease l) {
	return owners[l.id()];
    } 

    /** Return the array of owners */
    LeaseOwner[] getOwners() {
	return owners;
    }

    /** Return the array of leases */
    public TestLease[] getLeases() {
	return leases;
    }

    // inherit doc comment
    public Object renew(int id, long extension)
	throws LeaseDeniedException, UnknownLeaseException
    {
	LeaseOwner owner;
	synchronized (this) {
	    renewCalls++;
	    owner = owners[id];
	}

	return owner.renew(extension);
    }

    // inherit doc comment
    public Throwable cancel(int id) throws UnknownLeaseException {
	LeaseOwner owner;
	synchronized (this) {
	    owner = owners[id];
	}

	return owner.cancel();
    }

    // inherit doc comment
    public Object renewAll(int[] ids, long[] extensions) {
	long[] granted	= new long[ids.length];
	List exceptions = new java.util.ArrayList();

	synchronized (this) {
	    renewAllCalls++;
	}

	for (int i = 0; i < ids.length; i++) {
	    Throwable failure = null;

	    try {
		LeaseOwner owner;
		synchronized (this) {
		    owner = owners[ids[i]];
		    totalBatchRenewals++;
		}
		granted[i] = owner.batchRenew(extensions[i]);
	    } catch (Throwable e) {
		failure = e;
	    }

	    if (failure != null) {
		exceptions.add(failure);
		granted[i] = -1;
	    }
	}

	if (exceptions.size() == 0)
	    return new LeaseBackEnd.RenewResults(granted);
	else {
	    Throwable[] es = new Throwable[exceptions.size()];
	    Iterator it = exceptions.iterator();
	    for (int i = 0; it.hasNext(); i++)
		es[i] = (Throwable) it.next();
	    return new LeaseBackEnd.RenewResults(granted, es);
	}
    }

    // inherit doc comment
    public Throwable cancelAll(int[] ids) throws LeaseMapException {
	Map map = null;
	for (int i = 0; i < ids.length; i++) {
	    try {
		LeaseOwner owner;
		synchronized (this) {
		    owner = owners[ids[i]];
		}
		owner.batchCancel();
	    } catch (UnknownLeaseException e) {
		if (map == null)
		    map = new java.util.HashMap();
		map.put(new Integer(ids[i]), e);
	    }
	}

	if (map != null)
	    throw new LeaseMapException("cancelling", map);
	return null;
    }

    /**
     * Return the total number of times renew has been called
     */
    public synchronized long getTotalRenewCalls() {
	return renewCalls;
    }

    /**
     * Return the total number of times renewAll was called
     */
    public synchronized long getTotalRenewAllCalls() {
	return renewAllCalls;
    }

    /**
     * Return the average number of leases included in each batch
     */
    public synchronized double getAverageBatchSize() {
	return (double)totalBatchRenewals/(double)renewAllCalls;
    }

    private static class VerifierImpl implements TrustVerifier, Serializable {
	public boolean isTrustedObject(Object obj, TrustVerifier.Context ctx)
	    throws RemoteException
	{
	    return (obj instanceof OurAbstractLease
		 || obj instanceof OurAbstractLeaseMap);
	}
    }

    public TrustVerifier getProxyVerifier() {
	return new VerifierImpl();
    }

}

