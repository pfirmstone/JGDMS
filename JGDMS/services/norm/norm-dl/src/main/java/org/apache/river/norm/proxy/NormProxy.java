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
package org.apache.river.norm.proxy;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.rmi.RemoteException;
import net.jini.admin.Administrable;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.ProxyAccessor;
import net.jini.id.Uuid;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.Stateless;

/**
 * Defines a client-side proxy for a Norm server.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
@AtomicSerial
@Stateless
public abstract class NormProxy extends AbstractProxy
    implements LeaseRenewalService, Administrable
{
    /**
     * Creates a Norm server proxy, returning an instance that implements
     * RemoteMethodControl if the server does.
     *
     * @param server the server
     * @param serverUuid the unique ID for the server
     */
    public static NormProxy create(NormServer server, Uuid serverUuid) {
	// Always constrainable; fail closed when the server was not exported
	// with a constrainable endpoint.  The constrainable proxy is the only
	// concrete wire form (this class is abstract).
	if (!(server instanceof RemoteMethodControl)) {
	    throw new IllegalArgumentException(
		"service must be exported with a constrainable endpoint: "
		+ "server does not implement RemoteMethodControl");
	}
	return new ConstrainableNormProxy(server, serverUuid);
    }

    /** Creates an instance of this class. */
    NormProxy(NormServer server, Uuid serverUuid) {
	super(server, serverUuid);
    }

    NormProxy(GetArg arg) throws IOException, ClassNotFoundException {
	super(arg);
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
    @AtomicSerial
    @Stateless
    static final class ConstrainableNormProxy extends NormProxy
	implements RemoteMethodControl
    {
	/** Creates an instance of this class. */
	ConstrainableNormProxy(NormServer server, Uuid serverUuid) {
	    super(server, serverUuid);
	    if (!(server instanceof RemoteMethodControl)) {
		throw new IllegalArgumentException(
		    "server must implement RemoteMethodControl");
	    }
	}

	ConstrainableNormProxy(GetArg arg) throws IOException, ClassNotFoundException {
	    super(check(arg));
	}
	
	private static GetArg check(GetArg arg) throws IOException, ClassNotFoundException {
	    // Read the superclass field directly rather than constructing a plain
	    // NormProxy (now abstract); super(arg) still runs AbstractProxy checks.
	    Object server = arg.get("server", null, NormServer.class);
	    if (!(server instanceof RemoteMethodControl)) {
		throw new InvalidObjectException(
		    "server must implement RemoteMethodControl");
	    }
	    return arg;
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
    }
}
