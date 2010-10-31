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
package com.sun.jini.reggie;

import com.sun.jini.proxy.ConstrainableProxyUtil;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lookup.ServiceID;
import net.jini.id.Uuid;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;

/**
 * ServiceLease subclass that supports constraints.
 *
 * @author Sun Microsystems, Inc.
 *
 */
final class ConstrainableServiceLease
    extends ServiceLease implements RemoteMethodControl
{
    private static final long serialVersionUID = 2L;

    /** Mappings between Lease and Registrar methods */
    private static final Method[] methodMappings = {
	Util.getMethod(Lease.class, "cancel", new Class[0]),
	Util.getMethod(Registrar.class, "cancelServiceLease",
		       new Class[]{ ServiceID.class, Uuid.class }),

	Util.getMethod(Lease.class, "renew", new Class[]{ long.class }),
	Util.getMethod(Registrar.class, "renewServiceLease",
		       new Class[]{ ServiceID.class, Uuid.class, long.class })
    };

    /** Client constraints for this proxy, or null */
    private final MethodConstraints constraints;

    /**
     * Creates new ConstrainableServiceLease with given server reference, event
     * and lease IDs, expiration time and client constraints.
     */
    ConstrainableServiceLease(Registrar server,
			      ServiceID registrarID,
			      ServiceID serviceID,
			      Uuid leaseID,
			      long expiration,
			      MethodConstraints constraints)
    {
	super((Registrar) ((RemoteMethodControl) server).setConstraints(
		  ConstrainableProxyUtil.translateConstraints(
		      constraints, methodMappings)),
	      registrarID,
	      serviceID,
	      leaseID,
	      expiration);
	this.constraints = constraints;
    }

    /**
     * Creates a constraint-aware lease map.
     */
    public LeaseMap createLeaseMap(long duration) {
	return new ConstrainableRegistrarLeaseMap(this, duration);
    }

    /**
     * Two leases can be batched if they are both RegistrarLeases, share the
     * same server, and have compatible constraints.
     */
    public boolean canBatch(Lease lease) {
	if (!(super.canBatch(lease) && lease instanceof RemoteMethodControl)) {
	    return false;
	}
	return ConstrainableProxyUtil.equivalentConstraints(
	    ((RemoteMethodControl) lease).getConstraints(),
	    ConstrainableProxyUtil.translateConstraints(
		constraints, ConstrainableRegistrarLeaseMap.methodMappings),
	    ConstrainableRegistrarLeaseMap.methodMappings);
    }

    // javadoc inherited from RemoteMethodControl.setConstraints
    public RemoteMethodControl setConstraints(MethodConstraints constraints) {
	return new ConstrainableServiceLease(
	    server, registrarID, serviceID, leaseID, expiration, constraints);
    }

    // javadoc inherited from RemoteMethodControl.getConstraints
    public MethodConstraints getConstraints() {
	return constraints;
    }

    /**
     * Returns iterator used by ProxyTrustVerifier to retrieve a trust verifier
     * for this object.
     */
    private ProxyTrustIterator getProxyTrustIterator() {
	return new SingletonProxyTrustIterator(server);
    }

    /**
     * Verifies that the client constraints for this proxy are consistent with
     * those set on the underlying server ref.
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	ConstrainableProxyUtil.verifyConsistentConstraints(
	    constraints, server, methodMappings);
    }
}
