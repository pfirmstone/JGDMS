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
package com.sun.jini.test.share;

import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.rmi.Remote;
import java.rmi.RemoteException;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMapException;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;
import java.lang.reflect.Constructor;
import java.io.Serializable;

import com.sun.jini.qa.harness.QATest;
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
     * Index into lease and owner arrays 
     */
    int leaseIndex = 0;

    /** 
     * Map of <code>TestLease</code> ids to <code>LeaseOwners</code>
     */
    final private LeaseOwner[] owners;

    /** 
     * Array of all the leases we have created.
     */
    final private TestLease[] leases;

    /**
     * The TestLease factory used to create TestLease instances
     */
    final private AbstractTestLeaseFactory testLeaseFactory;

    /**
     * Simple constructor
     * @param leaseCount number of leases we expect this back end to allocate
     * @throws RemoteException if there is a problem exporting the object
     */
    public LeaseBackEndImpl(int leaseCount) throws RemoteException {
	this(leaseCount, TestLeaseFactory.class);
    }

    /**
     * Constructor requiring lease count and TestLeaseFactory class.
     * @param leaseCount number of leases we expect this back end to allocate
     * @param factoryClass  the class of the object that creates TestLeases
     * @throws RemoteException if there is a problem exporting the object
     */
    public LeaseBackEndImpl(int leaseCount, Class factoryClass) 
	      throws RemoteException {

	// factoryClass must be subclass of AbstractTestLeaseFactory
	boolean validFactoryType = 
	    AbstractTestLeaseFactory.class.isAssignableFrom(factoryClass);
	if (! validFactoryType) {
	    String message = "Factory class must be assignable from " +
		"AbstractTestLeaseFactory.";
	    throw new RemoteException(message,
				      new IllegalArgumentException(message));
	}

	owners = new LeaseOwner[leaseCount];
	leases = new TestLease[leaseCount];
	Configuration c = QAConfig.getConfig().getConfiguration();
	Exporter exporter = QAConfig.getDefaultExporter();
	if (c instanceof com.sun.jini.qa.harness.QAConfiguration) {
	    try {
		exporter = (Exporter) c.getEntry("test",
						 "leaseExporter",
						 Exporter.class);
	    } catch (ConfigurationException e) {
		throw new RemoteException("Configuration error", e);
	    }
	}
	LeaseBackEnd stub = 
	    (LeaseBackEnd) exporter.export(this);

	// create the lease factory
	try {
	    Class[] args = new Class[] { LeaseBackEnd.class };
	    Constructor constr = factoryClass.getConstructor(args);
	    testLeaseFactory = (AbstractTestLeaseFactory)
		constr.newInstance(new Object[] { stub });
	} catch (Exception ex) {
	    String message = "Error constructing TestLeaseFactory instance.";
	    throw new RemoteException(message, ex);
	}	
    }

    /**
     * Create a lease with the specified owner
     */
    public synchronized TestLease newLease(LeaseOwner owner, long expiration) {
	TestLease newLease = testLeaseFactory.getNewLeaseInstance(expiration);
	int leaseSlotNumber = newLease.id();
	owners[leaseSlotNumber] = owner;
	leases[leaseSlotNumber] = newLease;
	// Use are stub directly so comparisons workout
	return leases[leaseSlotNumber];
    }

    /** Given a lease return the lease's owner */
    synchronized LeaseOwner getOwner(TestLease l) {
	return owners[l.id()];
    } 

    /** Return the array of owners */
    public LeaseOwner[] getOwners() {
	return owners;
    }

    /** Return the array of leases */
    TestLease[] getLeases() {
	return leases;
    }

    // inherit doc comment
    public Object renew(int id, long extension)
	throws LeaseDeniedException, UnknownLeaseException
    {
	LeaseOwner owner;
	synchronized (this) {
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

	for (int i = 0; i < ids.length; i++) {
	    Exception failure = null;

	    try {
		LeaseOwner owner;
		synchronized (this) {
		    owner = owners[ids[i]];
		}
		granted[i] = owner.batchRenew(extensions[i]);
	    } catch (LeaseDeniedException e) {
		failure = e;
	    } catch (UnknownLeaseException e) {
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
	    Exception[] es = new Exception[exceptions.size()];
	    Iterator it = exceptions.iterator();
	    for (int i = 0; it.hasNext(); i++)
		es[i] = (Exception) it.next();
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

