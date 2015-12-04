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

import java.util.logging.Level;

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
import net.jini.security.TrustVerifier;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.MethodConstraints;


/**
 * A lease implementation that is completely local for use in some of the 
 * QA test for the LeaseRenewalService
 */
class ConstrainableLocalLease extends LocalLease implements RemoteMethodControl
{

     ProxyTrustImpl pt;

    /**
     * Create a local lease with the specified initial expiration time 
     * @param initExp    Initial expiration time
     * @param renewLimit Limit on long each renewal request can be for
     * @param bundle     Two <code>LocalLeases</code> with the same bundle
     * @param id         Uniuque ID for this lease
     * value can be batched together
     */
    ConstrainableLocalLease(long initExp, 
			    long renewLimit, 
			    long bundle, 
			    long id, 
			    ProxyTrustImpl pt) 
    {
	super(initExp, renewLimit, bundle, id);
	this.pt = pt;
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
	return new IteratorImpl(pt.getProxy());
    }

    public RemoteMethodControl setConstraints(MethodConstraints constraints) {
	((RemoteMethodControl) pt.getProxy()).setConstraints(constraints);
	return this;
    }

    public MethodConstraints getConstraints() {
	return ((RemoteMethodControl) pt.getProxy()).getConstraints();
    }

    private static class VerifierImpl implements TrustVerifier, Serializable {
	public boolean isTrustedObject(Object obj, TrustVerifier.Context ctx)
	    throws RemoteException
	{
	    return (obj instanceof LocalLease);
	}
    }
}

