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
package org.apache.river.test.impl.norm;

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
import net.jini.core.lease.LeaseException;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;

import net.jini.config.Configuration;
import net.jini.export.Exporter;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.ProxyTrust;
import net.jini.security.proxytrust.ServerProxyTrust;
import net.jini.security.TrustVerifier;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.MethodConstraints;

import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import java.rmi.server.ExportException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A lease implementation that is completely local for use in some of the 
 * QA test for the LeaseRenewalService
 */
class LocalLease implements Lease, Serializable {
    /** 
     * Expiration time of the lease 
     */
    private transient long expiration;

    /** 
     * Max length we will grant a renewal for
     * @serial
     */
    private long renewLimit;

    /** 
     * Two <code>LocalLeases</code> with the same bundle value can 
     * be batched together
     * @serial
     */
    private long bundle;

    /**
     * Serialization format for the expiration.
     *
     * @serial
     */
    protected int serialFormat = Lease.DURATION;

    /**
     * ID for this lease
     * @serial
     */
    private long id;

    private ProxyTrustImpl pt = getProxyTrust();
    
    private static ProxyTrustImpl getProxyTrust() {
        ProxyTrustImpl proxy = new ProxyTrustImpl();
        try {
            proxy.export();
        } catch (ExportException ex) {
            throw new RuntimeException("Problem creating verifier", ex);
        }
        return proxy;
    }

    public static LocalLease getLocalLease(long initExp, 
					   long renewLimit, 
					   long bundle, 
					   long id) 
    {
	ProxyTrustImpl pt = getProxyTrust();
	if (pt.getProxy() instanceof RemoteMethodControl) {
	    return new ConstrainableLocalLease(initExp, renewLimit, bundle, id, pt);
	} else {
	    return new LocalLease(initExp, renewLimit, bundle, id);
	}
    }

    public static FailingLocalLease getFailingLocalLease(long initExp, 
					   long renewLimit, 
					   long bundle, 
					   long id,
					   long count) 
    {
	ProxyTrustImpl pt = getProxyTrust();
	if (pt.getProxy() instanceof RemoteMethodControl) {
	    return new ConstrainableFailingLocalLease(initExp, renewLimit, bundle, id, count, pt);
	} else {
	    return new FailingLocalLease(initExp, renewLimit, bundle, id, count);
	}
    }

    public static DestructingLocalLease getDestructingLocalLease(long initExp, 
					   long renewLimit, 
					   long bundle, 
					   long id,
					   long count) 
    {
	ProxyTrustImpl pt = getProxyTrust();
	if (pt.getProxy() instanceof RemoteMethodControl) {
	    return new ConstrainableDestructingLocalLease(initExp, renewLimit, bundle, id, count, pt);
	} else {
	    return new DestructingLocalLease(initExp, renewLimit, bundle, id, count);
	}
    }

    /**
     * Create a local lease with the specified initial expiration time 
     * @param initExp    Initial expiration time
     * @param renewLimit Limit on long each renewal request can be for
     * @param bundle     Two <code>LocalLeases</code> with the same bundle
     * @param id         Uniuque ID for this lease
     * value can be batched together
     */
    LocalLease(long initExp, long renewLimit, long bundle, long id) {
	expiration = initExp;
	this.renewLimit = renewLimit;
	this.bundle = bundle;
	this.id = id;
    }

    // Inherit java doc from super type
    public synchronized long getExpiration() {
	return expiration;
    }

    protected synchronized void setExpiration(long exp) {
	expiration = exp;
    }

    // Inherit java doc from super type
    public void cancel() {
	System.err.println("***LEASE RENEWAL SERVICE CALLED CANCEL!!!!!***");
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
     * @serialData a long, which is the absolute expiration if serialFormat
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
     * correct the overflow by resetting the expiration to Long.MAX_VALUE,
     * correct underflow by resetting expiration to zero).
     */
    private synchronized void readObject(ObjectInputStream stream)
	throws IOException, ClassNotFoundException
    {
	stream.defaultReadObject();
	expiration = stream.readLong();
	if (serialFormat == Lease.DURATION) {
	    boolean canOverflow = (expiration > 0);
	    expiration += System.currentTimeMillis();

	    // If we added two positive numbers and if the result is negative
	    // we must have overflowed, truncate to Long.MAX_VALUE. Otherwise,
	    // if the result is negative must have underflowed, set to zero.
	    if (expiration < 0) {
		if (canOverflow) {
		    expiration = Long.MAX_VALUE;
		} else {
		    expiration = 0;
		}
	    }
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
        private static final long serialVersionUID = 1L;
	/** 
	 * Two <code>LocalLeases</code> with the same bundle value can 
	 * be batched together
	 * @serial
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
	public void renewAll() throws RemoteException {
	    final Set leases = map.entrySet();
	    for (Iterator i=leases.iterator(); i.hasNext(); ) {
		final Map.Entry e = (Map.Entry)i.next();
		final LocalLease l = (LocalLease)e.getKey();
		final long  d = ((Long)e.getValue()).longValue();
		try {
		    l.renewWork(d);
		} catch (LeaseException f) {
		    throw new RuntimeException(); 
		}
	    }

	    // Simulate blocking remote communications
	    Thread.yield();
	}

	// purposefully inherit doc comment from supertype
	public void cancelAll() {
	    final Set leases = map.keySet();
	    for (Iterator i=leases.iterator(); i.hasNext(); ) {
		final LocalLease l = (LocalLease)i.next();
		l.cancel();
	    }
	}
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

    public static class VerifierImpl implements TrustVerifier, Serializable {
	public boolean isTrustedObject(Object obj, TrustVerifier.Context ctx)
	    throws RemoteException
	{
	    return (obj instanceof LocalLease);
	}
    }

    public static class ProxyTrustImpl
	implements ProxyTrust, ServerProxyTrust, Serializable
    {
	private ProxyTrust proxy;
        private Exporter exporter;

	public ProxyTrustImpl() {
	    try {
		Configuration c = QAConfig.getConfig().getConfiguration();
		Exporter exporter = (Exporter) c.getEntry("test",
							  "testLeaseVerifierExporter",
							  Exporter.class,
							  null);
		if (exporter == null) {
		    return; // configuration isn't secure
		}
                this.exporter = exporter;
		
	    } catch (Exception e) {
		throw new RuntimeException("Problem creating verifier", e);
	    }
	}
        
        public synchronized void export() throws ExportException {
            if (exporter != null){
                proxy = (ProxyTrust) exporter.export(this);
            }
        }

	public synchronized ProxyTrust getProxy() {
	    return proxy;
	}

	public TrustVerifier getProxyVerifier() {
	    return new VerifierImpl();
	}
    }
}

