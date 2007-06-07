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
import java.lang.reflect.Method;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.util.LinkedList;
import java.util.List;

import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;
import net.jini.lease.LeaseRenewalSet;
import net.jini.lease.LeaseUnmarshalException;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;

import com.sun.jini.landlord.ConstrainableLandlordLease;
import com.sun.jini.proxy.ConstrainableProxyUtil;
import com.sun.jini.proxy.ThrowThis;

/**
 * Client side proxy for Norm's lease renewal sets.  Uses an object of
 * type NormServer to communicate back to server.
 *
 * @author Sun Microsystems, Inc.
 */
class SetProxy extends AbstractProxy implements LeaseRenewalSet {
    private static final long serialVersionUID = 2;

    /** 
     * Lease for this set.
     * @serial
     */
    final Lease ourLease;
     
    /**
     * Creates a lease set proxy, returning an instance that implements
     * RemoteMethodControl if the server does.
     *
     * @param server the server proxy
     * @param id the ID of the lease set
     * @param lease the lease set's lease
     */
    static SetProxy create(NormServer server, Uuid id, Lease lease) {
	if (server instanceof RemoteMethodControl) {
	    return new ConstrainableSetProxy(server, id, lease, null);
	} else {
	    return new SetProxy(server, id, lease);
	}
    }

    /** Simple constructor. */
    private SetProxy(NormServer server, Uuid id, Lease lease) {
	super(server, id);
	if (lease == null) {
	    throw new NullPointerException("lease cannot be null");
	}
	ourLease = lease;
    }

    /** Require fields to be non-null. */
    private void readObjectNoData() throws InvalidObjectException {
	throw new InvalidObjectException(
	    "server, uuid, and ourLease must be non-null");
    }

    /** Require lease to be non-null. */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	if (ourLease == null) {
	    throw new InvalidObjectException("ourLease cannot be null");
	}
    }

    /* -- Implement LeaseRenewalSet -- */

    // Inherit java doc from super type
    public void renewFor(Lease leaseToRenew, long membershipDuration)
	throws RemoteException 
    {
	try {
	    server2().renewFor(uuid, leaseToRenew, membershipDuration,
			       Lease.FOREVER);
	} catch (ThrowThis e) {
	    // The server wants to throw some remote exception
	    e.throwRemoteException();
	}
    }

    // Inherit java doc from super type
    public void renewFor(Lease leaseToRenew, long membershipDuration,
			 long  renewDuration)
	throws RemoteException 
    {
	try {
	    server.renewFor(uuid, leaseToRenew, membershipDuration, 
			    renewDuration);
	} catch (ThrowThis e) {
	    // The server wants to throw some remote exception
	    e.throwRemoteException();
	}
    }

    // Inherit java doc from super type
    public Lease remove(Lease leaseToRemove) throws RemoteException {
	try {
	    return server.remove(uuid, leaseToRemove);
	} catch (ThrowThis e) {
	    // The server wants to throw some remote exception
	    e.throwRemoteException();
	    // Need this because compiler does not know that
	    // throwRemoteException is not going to return
	    return null;
	}
    }

    // Inherit java doc from super type
    public Lease[] getLeases() throws LeaseUnmarshalException, RemoteException
    {
	try {
	    GetLeasesResult result = server.getLeases(uuid);
	    MarshalledInstance mls[] = result.marshalledLeases;

	    if (mls == null || mls.length == 0) 
		return new Lease[0];
	    
	    final List leases = new LinkedList();
	    final List problems = new LinkedList();
	    final List exceptions = new LinkedList();
	    for (int i=0; i<mls.length; i++) {
		Lease l;
		final MarshalledInstance ml = mls[i];
		try {
		    l = (Lease) ml.get(result.verifyCodebaseIntegrity());
		    leases.add(l);
		} catch (Throwable t) {
		    problems.add(ml.convertToMarshalledObject());
		    exceptions.add(t);
		}		
	    }

	    final Lease[] rslt =
		(Lease[]) leases.toArray(new Lease[leases.size()]);

	    if (problems.isEmpty())
		return rslt;
	    
	    final MarshalledObject sml[] =
		(MarshalledObject[]) problems.toArray(
                    new MarshalledObject[problems.size()]);
	    final Throwable es[] =
		(Throwable[]) exceptions.toArray(
		    new Throwable[exceptions.size()]);

	    throw new LeaseUnmarshalException("Problem unmarshalling lease(s)",
		rslt, sml, es);
	    
	} catch (ThrowThis e) {
	    // The server wants to throw some remote exception
	    e.throwRemoteException();
	    // Need this because compiler does not know that
	    // throwRemoteException is not going to return
	    return null;
	}
    }

    // Inherit java doc from super type
    public EventRegistration setExpirationWarningListener(
	                         RemoteEventListener listener, 
				 long                minWarning, 
				 MarshalledObject    handback)
	throws RemoteException    
    {
	// Do some client side error checking
	if (listener == null) {
	    throw new NullPointerException(
	        "LeaseRenewalSet.setExpirationWarningListener:Must " +
		"pass a non-null listener");
	}

	try {
	    return server.setExpirationWarningListener(uuid,
	        listener, minWarning, handback);
	} catch (ThrowThis e) {
	    // The server wants to throw some remote exception
	    e.throwRemoteException();
	    // Need this because compiler does not know that
	    // throwRemoteException is not going to return
	    return null;
	}
    }

    // Inherit java doc from super type
    public void clearExpirationWarningListener() throws RemoteException {
	try {
	    server2().setExpirationWarningListener(
		uuid, null, NormServer.NO_LISTENER, null);
	} catch (ThrowThis e) {
	    // The server wants to throw some remote exception
	    e.throwRemoteException();
	}
    }

    // Inherit java doc from super type
    public EventRegistration setRenewalFailureListener(
				 RemoteEventListener listener, 
				 MarshalledObject    handback)
	throws RemoteException
    {
	// Do some client side error checking
	if (listener == null) {
	    throw new NullPointerException(
	        "LeaseRenewalSet.setRenewalFailureListener:Must " +
		"pass a non-null listener");
	}

	try {
	    return server.setRenewalFailureListener(uuid, listener, handback);
	} catch (ThrowThis e) {
	    // The server wants to throw some remote exception
	    e.throwRemoteException();
	    // Need this because compiler does not know that
	    // throwRemoteException is not going to return
	    return null;
	}
    }


    // Inherit java doc from super type
    public void clearRenewalFailureListener() throws RemoteException {
	try {
	    server2().setRenewalFailureListener(uuid, null, null);
	} catch (ThrowThis e) {
	    // The server wants to throw some remote exception
	    e.throwRemoteException();
	}
    }

    /**
     * Returns a second server proxy for use by methods that represent a second
     * client-side method for a single server-side method.  The distinction
     * only matters for the constrainable subclass, where the different server
     * proxies need different client constraints.
     */
    NormServer server2() {
	return server;
    }

    // Inherit java doc from super type
    public Lease getRenewalSetLease() {
	return ourLease;
    }

    /** Defines a subclass of SetProxy that implements RemoteMethodControl. */
    static final class ConstrainableSetProxy extends SetProxy
	implements RemoteMethodControl
    {
	private static final long serialVersionUID = 1;

	/**
	 * Mappings from client to server methods, using the client method with
	 * more arguments for each server method when more than one client
	 * method maps to a single server method.
	 */
	private static final Method[] methodMap1;
	static {
	    try {
		methodMap1 = new Method[] {
		    LeaseRenewalSet.class.getMethod(
			"renewFor",
			new Class[] { Lease.class, long.class, long.class }),
		    NormServer.class.getMethod(
			"renewFor",
			new Class[] { Uuid.class, Lease.class, long.class,
				      long.class }),
		    LeaseRenewalSet.class.getMethod(
			"remove", new Class[] { Lease.class }),
		    NormServer.class.getMethod(
			"remove",
			new Class[] { Uuid.class, Lease.class }),
		    LeaseRenewalSet.class.getMethod(
			"getLeases", new Class[] { }),
		    NormServer.class.getMethod(
			"getLeases", new Class[] { Uuid.class }),
		    LeaseRenewalSet.class.getMethod(
			"setExpirationWarningListener",
			new Class[] { RemoteEventListener.class,
				      long.class, MarshalledObject.class }),
		    NormServer.class.getMethod(
			"setExpirationWarningListener",
			new Class[] { Uuid.class, RemoteEventListener.class,
				      long.class, MarshalledObject.class }),
		    LeaseRenewalSet.class.getMethod(
			"setRenewalFailureListener",
			new Class[] { RemoteEventListener.class,
				      MarshalledObject.class }),
		    NormServer.class.getMethod(
			"setRenewalFailureListener",
			new Class[] { Uuid.class, RemoteEventListener.class,
				      MarshalledObject.class })
		};
	    } catch (NoSuchMethodException e) {
		throw new NoSuchMethodError(e.getMessage());
	    }
	}

	/**
	 * Second set of mappings from client to server method names, for
	 * server methods with a second associated client method.
	 */
	private static final Method[] methodMap2;
	static {
	    try {
		methodMap2 = new Method[] {
		    LeaseRenewalSet.class.getMethod(
			"renewFor",
			new Class[] { Lease.class, long.class }),
		    NormServer.class.getMethod(
			"renewFor",
			new Class[] { Uuid.class, Lease.class, long.class,
				      long.class } ),
		    LeaseRenewalSet.class.getMethod(
			"clearExpirationWarningListener", new Class[] { }),
		    NormServer.class.getMethod(
			"setExpirationWarningListener",
			new Class[] { Uuid.class, RemoteEventListener.class,
				      long.class, MarshalledObject.class }),
		    LeaseRenewalSet.class.getMethod(
			"clearRenewalFailureListener", new Class[] { }),
		    NormServer.class.getMethod(
			"setRenewalFailureListener",
			new Class[] { Uuid.class, RemoteEventListener.class,
				      MarshalledObject.class })
		};
	    } catch (NoSuchMethodException e) {
		throw new NoSuchMethodError(e.getMessage());
	    }
	}

	/**
	 * The client constraints placed on this proxy or <code>null</code>.
	 *
	 * @serial
	 */
	private MethodConstraints methodConstraints;

	/**
	 * A second inner proxy to use when the client constraints for
	 * different smart proxy methods implemented by the same inner proxy
	 * methods have different constraints.  This proxy is used for the
	 * renewFor(Lease, long), clearExpirationWarningListener, and
	 * clearRenewalFailureListener methods.
	 */
	private transient NormServer server2;

	/** Creates an instance of this class. */
	ConstrainableSetProxy(NormServer server,
			      Uuid id,
			      Lease lease,
			      MethodConstraints methodConstraints)
	{
	    super(constrainServer(server, methodConstraints, methodMap1),
		  id, lease);
	    if (!(lease instanceof ConstrainableLandlordLease)) {
		throw new IllegalArgumentException(
		    "lease must be a ConstrainableLandlordLease");
	    }
	    this.methodConstraints = methodConstraints;
	    server2 = constrainServer(server, methodConstraints, methodMap2);
	}

	/**
	 * Verifies that ourLease is a ConstrainableLandlordLease, and that
	 * server implements RemoteMethodControl and has the appropriate method
	 * constraints.  Also sets the server2 field.
	 */
	private void readObject(ObjectInputStream s)
	    throws IOException, ClassNotFoundException
	{
	    s.defaultReadObject();
	    if (!(server instanceof RemoteMethodControl)) {
		throw new InvalidObjectException(
		    "server does not implement RemoteMethodControl");
	    } else if (!(ourLease instanceof ConstrainableLandlordLease)) {
		throw new InvalidObjectException(
		    "ourLease is not a ConstrainableLandlordLease");
	    }
	    ConstrainableProxyUtil.verifyConsistentConstraints(
		methodConstraints, server, methodMap1);
	    server2 = constrainServer(server, methodConstraints, methodMap2);
	}


	/**
	 * Returns a copy of the server proxy with the specified client
	 * constraints and methods mapping.
	 */
	private static NormServer constrainServer(
	    NormServer server,
	    MethodConstraints methodConstraints,
	    Method[] mappings)
	{
	    return (NormServer) ((RemoteMethodControl) server).setConstraints(
		ConstrainableProxyUtil.translateConstraints(
		    methodConstraints, mappings));
	}

	/** inherit javadoc */
	public RemoteMethodControl setConstraints(
	    MethodConstraints constraints)
	{
	    return new ConstrainableSetProxy(
		server, uuid, ourLease, constraints);
	}

	/** inherit javadoc */
	public MethodConstraints getConstraints() {
	    return methodConstraints;
	}

	/** Returns the second server proxy. */
	NormServer server2() {
	    return server2;
	}

	/**
	 * Returns a proxy trust iterator that supplies the server, for use by
	 * ProxyTrustVerifier.
	 */
	private ProxyTrustIterator getProxyTrustIterator() {
	    return new SingletonProxyTrustIterator(server);
	}
    }
}
