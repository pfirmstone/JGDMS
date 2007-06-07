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
package com.sun.jini.outrigger;

import java.lang.reflect.Method;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.rmi.MarshalledObject;
import java.util.Collection;

import net.jini.core.entry.Entry;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.transaction.Transaction;
import net.jini.core.lease.Lease;
import net.jini.space.JavaSpace;
import net.jini.space.JavaSpace05;
import net.jini.admin.Administrable;

import net.jini.id.Uuid;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import com.sun.jini.proxy.ConstrainableProxyUtil;
import com.sun.jini.landlord.ConstrainableLandlordLease;

/**
 * Constrainable subclass of <code>SpaceProxy2</code>
 */
final class ConstrainableSpaceProxy2 extends SpaceProxy2
    implements RemoteMethodControl 
{
    static final long serialVersionUID = 1L;

    /**
     * Array containing element pairs in which each pair of elements
     * represents a mapping between two methods having the following
     * characteristics:
     * <ul>
     * <li> the first element in the pair is one of the public, remote
     *      method(s) that may be invoked by the client through 
     *      <code>SpaceProxy2</code>.
     * <li> the second element in the pair is the method, implemented
     *      in the backend server class, that is ultimately executed in
     *      the server's backend when the client invokes the corresponding
     *      method in this proxy.
     * </ul>
     */
    private static final Method[] methodMapArray =  {
	ProxyUtil.getMethod(Administrable.class, "getAdmin", new Class[] {}),
	ProxyUtil.getMethod(Administrable.class, "getAdmin", new Class[] {}),

	ProxyUtil.getMethod(JavaSpace.class, "write", 
			    new Class[] {Entry.class,
					 Transaction.class,
					 long.class}),
	ProxyUtil.getMethod(OutriggerServer.class, "write",
			    new Class[] {EntryRep.class,
					 Transaction.class,
					 long.class}),


	ProxyUtil.getMethod(JavaSpace.class, "read", 
			    new Class[] {Entry.class,
					 Transaction.class,
					 long.class}),
	ProxyUtil.getMethod(OutriggerServer.class, "read",
			    new Class[] {EntryRep.class,
					 Transaction.class,
					 long.class,
					 OutriggerServer.QueryCookie.class}),


	ProxyUtil.getMethod(JavaSpace.class, "take", 
			    new Class[] {Entry.class,
					 Transaction.class,
					 long.class}),
	ProxyUtil.getMethod(OutriggerServer.class, "take",
			    new Class[] {EntryRep.class,
					 Transaction.class,
					 long.class,
					 OutriggerServer.QueryCookie.class}),


	ProxyUtil.getMethod(JavaSpace.class, "readIfExists", 
			    new Class[] {Entry.class,
					 Transaction.class,
					 long.class}),
	ProxyUtil.getMethod(OutriggerServer.class, "readIfExists",
			    new Class[] {EntryRep.class,
					 Transaction.class,
					 long.class,
					 OutriggerServer.QueryCookie.class}),


	ProxyUtil.getMethod(JavaSpace.class, "takeIfExists", 
			    new Class[] {Entry.class,
					 Transaction.class,
					 long.class}),
	ProxyUtil.getMethod(OutriggerServer.class, "takeIfExists",
			    new Class[] {EntryRep.class,
					 Transaction.class,
					 long.class,
					 OutriggerServer.QueryCookie.class}),


	ProxyUtil.getMethod(JavaSpace.class, "notify", 
			    new Class[] {Entry.class,
					 Transaction.class,
					 RemoteEventListener.class,
					 long.class,
					 MarshalledObject.class}),
	ProxyUtil.getMethod(OutriggerServer.class, "notify",
			    new Class[] {EntryRep.class,
					 Transaction.class,
					 RemoteEventListener.class,
					 long.class,
					 MarshalledObject.class}),

	ProxyUtil.getMethod(JavaSpace05.class, "contents", 
			    new Class[] {Collection.class,
					 Transaction.class,
					 long.class,
					 long.class}), 
	ProxyUtil.getMethod(OutriggerServer.class, "contents",
			    new Class[] {EntryRep[].class,
					 Transaction.class,
					 long.class,
					 long.class}), 


	// Use the same constants for nextBatch as contents
	ProxyUtil.getMethod(JavaSpace05.class, "contents", 
			    new Class[] {Collection.class,
					 Transaction.class,
					 long.class,
					 long.class}), 
	ProxyUtil.getMethod(OutriggerServer.class, "nextBatch",
			    new Class[] {Uuid.class,
					 Uuid.class}) 
    };//end methodMapArray

    /** 
     * Client constraints placed on this proxy (may be <code>null</code> 
     * @serial
     */
    private final MethodConstraints methodConstraints;
    
    /**
     * Create a new <code>ConstrainableSpaceProxy2</code>.
     * @param space The <code>OutriggerServer</code> for the 
     *              space.
     * @param spaceUuid The universally unique ID for the
     *              space
     * @param serverMaxServerQueryTimeout The value this proxy
     *              should use for the <code>maxServerQueryTimeout</code>
     *              if no local value is provided.
     * @param methodConstraints the client method constraints to place on
     *                          this proxy (may be <code>null</code>).
     * @throws NullPointerException if <code>space</code> or
     *         <code>spaceUuid</code> is <code>null</code>.
     * @throws IllegalArgumentException if 
     *         <code>serverMaxServerQueryTimeout</code> is not
     *         larger than zero.     
     * @throws ClassCastException if <code>server</code>
     *         does not implement <code>RemoteMethodControl</code>.
     */
    ConstrainableSpaceProxy2(OutriggerServer space, Uuid spaceUuid, 
			    long serverMaxServerQueryTimeout, 
			    MethodConstraints methodConstraints)
    {
	super(constrainServer(space, methodConstraints),
	      spaceUuid, serverMaxServerQueryTimeout);
	this.methodConstraints = methodConstraints;
    }

    /**
     * Returns a copy of the given <code>OutriggerServer</code> proxy
     * having the client method constraints that result after
     * mapping defined by methodMapArray is applied.
     * @param server The proxy to attach constrains too.
     * @param constraints The source method constraints.
     * @throws NullPointerException if <code>server</code> is 
     *         <code>null</code>.
     * @throws ClassCastException if <code>server</code>
     *         does not implement <code>RemoteMethodControl</code>.
     */
    private static OutriggerServer constrainServer(OutriggerServer server,
        MethodConstraints constraints)
    {
	final MethodConstraints serverRefConstraints 
	    = ConstrainableProxyUtil.translateConstraints(constraints,
							  methodMapArray);
	final RemoteMethodControl constrainedServer = 
	    ((RemoteMethodControl)server).
	    setConstraints(serverRefConstraints);

	return (OutriggerServer)constrainedServer;
    }

    public RemoteMethodControl setConstraints(MethodConstraints constraints)
    {
	return new ConstrainableSpaceProxy2(space, spaceUuid, 
					   serverMaxServerQueryTimeout,
					   constraints);
    }

    public MethodConstraints getConstraints() {
	return methodConstraints;
    }

    /** 
     * Returns a proxy trust iterator that is used in 
     * <code>ProxyTrustVerifier</code> to retrieve this object's
     * trust verifier.
     */
    private ProxyTrustIterator getProxyTrustIterator() {
	return new SingletonProxyTrustIterator(space);
    }

    private void readObject(ObjectInputStream s)  
	throws IOException, ClassNotFoundException
    {
	s.defaultReadObject();

	/* Basic validation of space, spaceUuid, and 
	 * serverMaxServerQueryTimeout was performed by
	 * SpaceProxy2.readObject(), we just need to verify than
	 * space implements RemoteMethodControl and that it has
	 * appropriate constraints. 
	 */
	ConstrainableProxyUtil.verifyConsistentConstraints(
	    methodConstraints, space, methodMapArray);
    }

    protected Lease constructLease(Uuid uuid, long expiration) {
	return new ConstrainableLandlordLease(uuid, space, spaceUuid,
					      expiration, null);
    }
}

