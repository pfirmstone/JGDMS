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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.rmi.RemoteException;
import net.jini.admin.Administrable;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.id.Uuid;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;

/**
 * Defines a client-side proxy for a Norm server.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
class NormProxy extends AbstractProxy
    implements LeaseRenewalService, Administrable
{
    private static final long serialVersionUID = 1;

    /**
     * Creates a Norm server proxy, returning an instance that implements
     * RemoteMethodControl if the server does.
     *
     * @param server the server
     * @param serverUuid the unique ID for the server
     */
    static NormProxy create(NormServer server, Uuid serverUuid) {
	if (server instanceof RemoteMethodControl) {
	    return new ConstrainableNormProxy(server, serverUuid);
	} else {
	    return new NormProxy(server, serverUuid);
	}
    }

    /** Creates an instance of this class. */
    NormProxy(NormServer server, Uuid serverUuid) {
	super(server, serverUuid);
    }

    /** Require fields to be non-null. */
    private void readObjectNoData() throws InvalidObjectException {
	throw new InvalidObjectException(
	    "server and uuid must be non-null");
    }

    /* -- Implement LeaseRenewalService -- */

    /** inherit javadoc */
    public LeaseRenewalSet createLeaseRenewalSet(long leaseDuration) 
	throws RemoteException
    {
	return server.createLeaseRenewalSet(leaseDuration);
    }

    /* -- Implement Administrable -- */

    /** inherit javadoc */
    public Object getAdmin() throws RemoteException {
	return server.getAdmin();
    }

    /** Defines a subclass of NormProxy that implements RemoteMethodControl. */
    static final class ConstrainableNormProxy extends NormProxy
	implements RemoteMethodControl
    {
	private static final long serialVersionUID = 1;

	/** Creates an instance of this class. */
	ConstrainableNormProxy(NormServer server, Uuid serverUuid) {
	    super(server, serverUuid);
	    if (!(server instanceof RemoteMethodControl)) {
		throw new IllegalArgumentException(
		    "server must implement RemoteMethodControl");
	    }
	}

	/** Require server to implement RemoteMethodControl. */
	private void readObject(ObjectInputStream in)
	    throws IOException, ClassNotFoundException
	{
	    in.defaultReadObject();
	    if (!(server instanceof RemoteMethodControl)) {
		throw new InvalidObjectException(
		    "server must implement RemoteMethodControl");
	    }
	}

	/* inherit javadoc */
	public RemoteMethodControl setConstraints(
	    MethodConstraints constraints)
	{
	    NormServer constrainedServer = (NormServer)
		((RemoteMethodControl) server).setConstraints(constraints);
	    return new ConstrainableNormProxy(constrainedServer, uuid);
	}

	/* inherit javadoc */
	public MethodConstraints getConstraints() {
	    return ((RemoteMethodControl) server).getConstraints();
	}

	/**
	 * Returns a proxy trust iterator that yields this object's server.
	 */
	private ProxyTrustIterator getProxyTrustIterator() {
	    return new SingletonProxyTrustIterator(server);
	}
    }
}
