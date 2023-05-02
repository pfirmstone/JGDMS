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

package org.apache.river.jeri.internal.runtime;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.rmi.RemoteException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.jeri.AtomicInvocationHandler;
import net.jini.jeri.BasicObjectEndpoint;
import net.jini.jeri.Endpoint;
import net.jini.jeri.ObjectEndpoint;
import org.apache.river.logging.Levels;

/**
 * Jeri ERI client-side DGC implementation.
 *
 * This class specializes AbstractDgcClient to use
 * net.jini.jeri.Endpoint for transport endpoints, net.jini.id.Uuid
 * for object identifiers, and net.jini.jeri.BasicObjectEndpoint for
 * live remote references.
 *
 * This class's DgcProxy implementation maps dirty and clean calls to
 * remote invocations on the DgcServer remote interface, through a
 * BasicInvocationHandler and a BasicObjectEndpoint with the
 * DgcProxy's Endpoint and the well-known Uuid for the Jini ERI
 * server-side DGC implementation remote object.
 *
 * @author Sun Microsystems, Inc.
 **/
public final class DgcClient extends AbstractDgcClient {

    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.BasicObjectEndpoint");

    private static final Class[] proxyInterfaces =
	new Class[] { DgcServer.class, RemoteMethodControl.class };

    /** unique identifier for this DgcClient as a client of DGC */
    private static final Uuid clientID = UuidFactory.generate();

    public DgcClient() {
	super();
    }

    @Override
    public void registerRefs(Endpoint endpoint, Collection<ObjectEndpoint> refs) {
	super.registerRefs(endpoint, refs);
    }

    protected DgcProxy getDgcProxy(Endpoint endpoint) {
	Endpoint e = (Endpoint) endpoint;
	ObjectEndpoint oe = new BasicObjectEndpoint(e, Jeri.DGC_ID, false);
	InvocationHandler ih = new AtomicInvocationHandler(oe, null, false);
	DgcServer proxy =
	    (DgcServer) Proxy.newProxyInstance(getClass().getClassLoader(),
					       proxyInterfaces, ih);
	return new DgcProxyImpl(proxy);
    }

    protected void freeEndpoint(Endpoint endpoint) {
	// we don't need to do anything special for freed endpoints
    }

    protected Endpoint getRefEndpoint(ObjectEndpoint ref) {
	BasicObjectEndpoint oei = (BasicObjectEndpoint) ref;
	assert oei.getEnableDGC();
	return oei.getEndpoint();
    }

    protected Uuid getRefObjectID(ObjectEndpoint ref) {
	BasicObjectEndpoint oei = (BasicObjectEndpoint) ref;
	assert oei.getEnableDGC();
	return oei.getObjectIdentifier();
    }
    
    /**
     * Set the calling context for DGC, if not yet set for this Endpoint.
     * The context should only be set if it contains a Subject that can be
     * used to authenticate this Endpoint, otherwise the context should be null. 
     * MethodConstraints are combined, with existing constraints, constraints
     * set by other ObjectEnpoint's are combined for this Endpoint.
     * 
     * @param endpoint Current Endpoint.
     * @param clientConstraints MethodConstraints to be added to this Endpoint for DGC calls.
     */
    public void constraintsApplicableToDgcServer(Endpoint endpoint, MethodConstraints clientConstraints)
    {
	EndpointEntry entry = super.getEndpointEntry(endpoint);
	entry.constraintsApplicableToDgcServer(clientConstraints);
    }

    private static class DgcProxyImpl implements DgcProxy, RemoteMethodControl {

	private final DgcServer dgcServer;

	DgcProxyImpl(DgcServer dgcServer) {
	    this.dgcServer = dgcServer;
	}

	public long dirty(final long sequenceNum, Object[] ids, long duration)
	    throws RemoteException
	{
	    // copying can be eliminated by specializing AbstractDgcClient
	    final Uuid[] idsCopy = new Uuid[ids.length];
	    System.arraycopy(ids, 0, idsCopy, 0, ids.length);

	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
		    "sequenceNum={0}, ids={1} (clientID={2})",
		    new Object[] {
			Long.valueOf(sequenceNum), Arrays.asList(idsCopy), clientID
		    });
	    }

	    // client-suggested duration is ignored
	    try {
		return dgcServer.dirty(clientID, sequenceNum,idsCopy);
	    } catch (RemoteException e){
		if (logger.isLoggable(Levels.HANDLED)) {
		    logger.log(Levels.HANDLED, "exception occurred", e);
		}
		throw e;
	    }
	}

	public void clean(final long sequenceNum,
			  Object[] ids,
			  final boolean strong)
	    throws RemoteException
	{
	    // copying can be eliminated by specializing AbstractDgcClient
	    final Uuid[] idsCopy = new Uuid[ids.length];
	    System.arraycopy(ids, 0, idsCopy, 0, ids.length);

	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
		    "sequenceNum={0}, ids={1}, strong={2} (clientID={3})",
		    new Object[] {
			Long.valueOf(sequenceNum), Arrays.asList(idsCopy),
			Boolean.valueOf(strong), clientID
		    });
	    }
	    
	    try {
		dgcServer.clean(clientID, sequenceNum, idsCopy, strong);
	    } catch (RemoteException e){
		if (logger.isLoggable(Levels.HANDLED)) {
		    logger.log(Levels.HANDLED,
			       "exception occurred", e);
		}
		throw e;
	    }
	}

	public RemoteMethodControl setConstraints(MethodConstraints constraints) {
	    DgcServer server = (DgcServer) 
		    ((RemoteMethodControl) dgcServer).setConstraints(constraints);
	    return new DgcProxyImpl(server);
	}

	public MethodConstraints getConstraints() {
	    return ((RemoteMethodControl) dgcServer).getConstraints();
	}
    }
}
