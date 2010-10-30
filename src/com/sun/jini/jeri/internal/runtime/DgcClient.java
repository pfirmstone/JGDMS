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

package com.sun.jini.jeri.internal.runtime;

import com.sun.jini.logging.Levels;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.rmi.RemoteException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.jeri.BasicInvocationHandler;
import net.jini.jeri.BasicObjectEndpoint;
import net.jini.jeri.Endpoint;
import net.jini.jeri.ObjectEndpoint;

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
	new Class[] { DgcServer.class };

    /** unique identifier for this DgcClient as a client of DGC */
    private static final Uuid clientID = UuidFactory.generate();

    public DgcClient() {
	super();
    }

    public void registerRefs(Endpoint endpoint, Collection refs) {
	super.registerRefs(endpoint, refs);
    }

    protected DgcProxy getDgcProxy(Object endpoint) {
	Endpoint e = (Endpoint) endpoint;
	ObjectEndpoint oe = new BasicObjectEndpoint(e, Jeri.DGC_ID, false);
	InvocationHandler ih = new BasicInvocationHandler(oe, null);
	DgcServer proxy =
	    (DgcServer) Proxy.newProxyInstance(getClass().getClassLoader(),
					       proxyInterfaces, ih);
	return new DgcProxyImpl(proxy);
    }

    protected void freeEndpoint(Object endpoint) {
	// we don't need to do anything special for freed endpoints
    }

    protected Object getRefEndpoint(Object ref) {
	BasicObjectEndpoint oei = (BasicObjectEndpoint) ref;
	assert oei.getEnableDGC();
	return oei.getEndpoint();
    }

    protected Object getRefObjectID(Object ref) {
	BasicObjectEndpoint oei = (BasicObjectEndpoint) ref;
	assert oei.getEnableDGC();
	return oei.getObjectIdentifier();
    }

    private class DgcProxyImpl implements DgcProxy {

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
			new Long(sequenceNum), Arrays.asList(idsCopy), clientID
		    });
	    }

	    // client-suggested duration is ignored
	    try {
		return ((Long) AccessController.doPrivileged(
		    new PrivilegedExceptionAction() {
			public Object run() throws RemoteException {
			    long l = dgcServer.dirty(clientID, sequenceNum,
						     idsCopy);
			    return new Long(l);
			}
		    }, null)).longValue();	// ensure no Subject
	    } catch (PrivilegedActionException e) {
		if (logger.isLoggable(Levels.HANDLED)) {
		    logger.log(Levels.HANDLED,
			       "exception occurred", e.getCause());
		}
		throw (RemoteException) e.getCause();
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
			new Long(sequenceNum), Arrays.asList(idsCopy),
			Boolean.valueOf(strong), clientID
		    });
	    }

	    try {
		AccessController.doPrivileged(
		    new PrivilegedExceptionAction() {
			public Object run() throws RemoteException {
			    dgcServer.clean(clientID, sequenceNum,
					    idsCopy, strong);
			    return null;
			}
		    }, null);	// ensure no Subject
	    } catch (PrivilegedActionException e) {
		if (logger.isLoggable(Levels.HANDLED)) {
		    logger.log(Levels.HANDLED,
			       "exception occurred", e.getCause());
		}
		throw (RemoteException) e.getCause();
	    }
	}
    }
}
