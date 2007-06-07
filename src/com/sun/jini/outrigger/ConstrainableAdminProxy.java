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
import java.io.InvalidObjectException;
import java.rmi.RemoteException;

import net.jini.core.entry.Entry;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import net.jini.space.JavaSpace;
import net.jini.admin.JoinAdmin;

import net.jini.id.Uuid;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import com.sun.jini.proxy.ConstrainableProxyUtil;
import com.sun.jini.admin.DestroyAdmin;

/**
 * Constrainable subclass of <code>AdminProxy</code>
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
final class ConstrainableAdminProxy extends AdminProxy
    implements RemoteMethodControl, ConstrainableJavaSpaceAdmin
{
    static final long serialVersionUID = 1L;

    /**
     * Array containing element pairs in which each pair of elements
     * represents a mapping between two methods having the following
     * characteristics:
     * <ul>
     * <li> the first element in the pair is one of the public, remote
     *      method(s) that may be invoked by the client through 
     *      <code>AdminProxy</code>.
     * <li> the second element in the pair is the method, implemented
     *      in the backend server class, that is ultimately executed in
     *      the server's backend when the client invokes the corresponding
     *      method in this proxy.
     * </ul>
     */
    private static final Method[] methodMapArray =  {
	ProxyUtil.getMethod(JoinAdmin.class, "getLookupAttributes", 
			    new Class[] {}),
	ProxyUtil.getMethod(JoinAdmin.class, "getLookupAttributes",
			    new Class[] {}),


	ProxyUtil.getMethod(JoinAdmin.class, "addLookupAttributes", 
			    new Class[] {Entry[].class}),
	ProxyUtil.getMethod(JoinAdmin.class, "addLookupAttributes", 
			    new Class[] {Entry[].class}),


	ProxyUtil.getMethod(JoinAdmin.class, "modifyLookupAttributes", 
			    new Class[] {Entry[].class, Entry[].class}),
	ProxyUtil.getMethod(JoinAdmin.class, "modifyLookupAttributes", 
			    new Class[] {Entry[].class, Entry[].class}),


	ProxyUtil.getMethod(JoinAdmin.class, "getLookupGroups",
			    new Class[] {}),
	ProxyUtil.getMethod(JoinAdmin.class, "getLookupGroups",
			    new Class[] {}),

	ProxyUtil.getMethod(JoinAdmin.class, "setLookupGroups", 
			    new Class[] {String[].class}),
	ProxyUtil.getMethod(JoinAdmin.class, "setLookupGroups", 
			    new Class[] {String[].class}),


	ProxyUtil.getMethod(JoinAdmin.class, "removeLookupGroups", 
			    new Class[] {String[].class}),
	ProxyUtil.getMethod(JoinAdmin.class, "removeLookupGroups", 
			    new Class[] {String[].class}),


	ProxyUtil.getMethod(JoinAdmin.class, "addLookupGroups", 
			    new Class[] {String[].class}),
	ProxyUtil.getMethod(JoinAdmin.class, "addLookupGroups", 
			    new Class[] {String[].class}),


	ProxyUtil.getMethod(JoinAdmin.class, "getLookupLocators", 
			    new Class[] {}),
	ProxyUtil.getMethod(JoinAdmin.class, "getLookupLocators", 
			    new Class[] {}),



	ProxyUtil.getMethod(JoinAdmin.class, "setLookupLocators", 
			    new Class[] {LookupLocator[].class} ),
	ProxyUtil.getMethod(JoinAdmin.class, "setLookupLocators", 
			    new Class[] {LookupLocator[].class} ),


	ProxyUtil.getMethod(JoinAdmin.class, "addLookupLocators", 
			    new Class[] {LookupLocator[].class} ),
	ProxyUtil.getMethod(JoinAdmin.class, "addLookupLocators", 
			    new Class[] {LookupLocator[].class} ),


	ProxyUtil.getMethod(JoinAdmin.class, "removeLookupLocators", 
			    new Class[] {LookupLocator[].class} ),
	ProxyUtil.getMethod(JoinAdmin.class, "removeLookupLocators", 
			    new Class[] {LookupLocator[].class} ),


	ProxyUtil.getMethod(DestroyAdmin.class, "destroy", new Class[] {}),
	ProxyUtil.getMethod(DestroyAdmin.class, "destroy", new Class[] {}),


	ProxyUtil.getMethod(JavaSpaceAdmin.class, "space", new Class[] {}),
	ProxyUtil.getMethod(OutriggerAdmin.class, "space", new Class[] {}),


	ProxyUtil.getMethod(ConstrainableJavaSpaceAdmin.class, "contents", 
			    new Class[] {Entry.class,
					 Transaction.class,
					 int.class,
					 MethodConstraints.class}),
	ProxyUtil.getMethod(OutriggerAdmin.class, "contents", 
			    new Class[] {EntryRep.class,
					 Transaction.class} )
    }; // end methodMapArray

    /** 
     * Client constraints placed on this proxy (may be <code>null</code> 
     * @serial
     */
    private final MethodConstraints methodConstraints;
    
    /**
     * Create a new <code>ConstrainableAdminProxy</code>.
     * @param admin reference to remote server for the space.
     * @param spaceUuid universal unique ID for the space.
     * @param methodConstraints the client method constraints to place on
     *                          this proxy (may be <code>null</code>).
     * @throws NullPointerException if <code>admin</code> or
     *         <code>spaceUuid</code> is <code>null</code>.     
     * @throws ClassCastException if <code>admin</code>
     *         does not implement <code>RemoteMethodControl</code>.
     */
    ConstrainableAdminProxy(OutriggerAdmin admin, Uuid spaceUuid, 
			    MethodConstraints methodConstraints)
    {
	super(constrainServer(admin, methodConstraints), spaceUuid);
	this.methodConstraints = methodConstraints;
    }

    /**
     * Returns a copy of the given <code>OutriggerAdmin</code> proxy
     * having the client method constraints that result after
     * mapping defined by methodMapArray is applied.
     * @param server The proxy to attach constrains too.
     * @param constraints The source method constraints.
     * @throws NullPointerException if <code>server</code> is 
     *         <code>null</code>.
     * @throws ClassCastException if <code>server</code>
     *         does not implement <code>RemoteMethodControl</code>.
     */
    private static OutriggerAdmin constrainServer(OutriggerAdmin server,
        MethodConstraints constraints)
    {
	final MethodConstraints serverRefConstraints 
	    = ConstrainableProxyUtil.translateConstraints(constraints,
							  methodMapArray);
	final RemoteMethodControl constrainedServer = 
	    ((RemoteMethodControl)server).
	    setConstraints(serverRefConstraints);

	return (OutriggerAdmin)constrainedServer;
    }

    public RemoteMethodControl setConstraints(MethodConstraints constraints)
    {
	return new ConstrainableAdminProxy(admin, spaceUuid, constraints);
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
	return new SingletonProxyTrustIterator(admin);
    }

    private void readObject(ObjectInputStream s)  
	throws IOException, ClassNotFoundException
    {
	s.defaultReadObject();

	/* basic validation of admin and spaceUuid was performed by
	 * AdminProxy.readObject(), we just need to verify than space
	 * implements RemoteMethodControl and that it has appropriate
	 * constraints.
	 */
	ConstrainableProxyUtil.verifyConsistentConstraints(
	    methodConstraints, admin, methodMapArray);
    }

    /**
     * Override super class to create secure <code>IteratorProxy</code>s
     */
    public AdminIterator contents(Entry tmpl, Transaction tr, int fetchSize)
	throws TransactionException, RemoteException
    {
	return contents(tmpl, tr, fetchSize, null);
    }


    public AdminIterator contents(Entry tmpl, Transaction txn, int fetchSize,
				  MethodConstraints constraints)
	throws TransactionException, RemoteException
    {
	return new ConstrainableIteratorProxy(
	    admin.contents(SpaceProxy2.repFor(tmpl), txn), admin, fetchSize,
			   constraints);
    }

}
