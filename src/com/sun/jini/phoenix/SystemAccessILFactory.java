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

package com.sun.jini.phoenix;

import java.lang.reflect.Method;
import java.rmi.Remote;
import java.rmi.activation.ActivationGroupDesc;
import java.rmi.activation.ActivationSystem;
import java.rmi.server.ExportException;
import java.util.Collection;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicInvocationDispatcher;
import net.jini.jeri.InvocationDispatcher;
import net.jini.jeri.ServerCapabilities;

/**
 * Invocation layer factory for exporting an {@link ActivationSystem}
 * to use Jini extensible remote invocation (Jini ERI), that is similar
 * to {@link BasicILFactory} except the remote object must be an
 * <code>ActivationSystem</code> instance and the returned dispatcher
 * optionally accepts calls from the local host and optionally enforces a
 * {@link GroupPolicy} on calls to {@link ActivationSystem#registerGroup
 * registerGroup} and {@link ActivationSystem#setActivationGroupDesc
 * setActivationGroupDesc}.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 * @see SystemAccessProxyTrustILFactory
 **/
public class SystemAccessILFactory extends BasicILFactory {
    /**
     * The group policy, if any.
     */
    private final GroupPolicy policy;

    /**
     * If true, check that client is calling from the local host.
     */
    private final boolean localAccessCheck;
    
    /**
     * Creates an invocation layer factory that creates an invocation
     * dispatcher with a {@link DefaultGroupPolicy} instance and a
     * <code>null</code> class loader.  This invocation dispatcher only
     * accepts calls from the local host and enforces the group policy on
     * calls to {@link ActivationSystem#registerGroup registerGroup} and
     * {@link ActivationSystem#setActivationGroupDesc
     * setActivationGroupDesc}.
     **/
    public SystemAccessILFactory() {
	this(new DefaultGroupPolicy(), null);
    }

    /**
     * Creates an invocation layer factory that creates an invocation
     * dispatcher with the specified group policy and the specified class
     * loader.  This invocation dispatcher only accepts calls from the
     * local host and enforces the specified group policy (if
     * non-<code>null</code>) on calls to {@link
     * ActivationSystem#registerGroup registerGroup} and {@link
     * ActivationSystem#setActivationGroupDesc setActivationGroupDesc}.
     *
     * @param loader the class loader, or <code>null</code>
     * @param policy the group policy, or <code>null</code>
     **/
    public SystemAccessILFactory(GroupPolicy policy, ClassLoader loader) {
	super(null, null, loader);
	this.policy = policy;
	this.localAccessCheck = true;
    }

    /**
     * Creates a factory with a <code>null</code> class loader, the
     * specified server constraints, the {@link SystemPermission}
     * permission class, and a {@link DefaultGroupPolicy} instance.
     *
     * @param serverConstraints the server constraints, or <code>null</code>
     **/
    public SystemAccessILFactory(MethodConstraints serverConstraints) {
	this(serverConstraints, SystemPermission.class,
	     new DefaultGroupPolicy(), null);
    }

    /**
     * Creates a factory with the specified server constraints, permission
     * class, group policy, and class loader.  This factory creates an
     * invocation dispatcher that enforces the specified group policy (if
     * non-<code>null</code>) on calls to {@link
     * ActivationSystem#registerGroup registerGroup} and {@link
     * ActivationSystem#setActivationGroupDesc setActivationGroupDesc}.
     *
     * @param serverConstraints the server constraints, or <code>null</code>
     * @param permClass the permission class, or <code>null</code>
     * @param policy the group policy, or <code>null</code>
     * @param loader the class loader, or <code>null</code>
     * @throws IllegalArgumentException if the permission class is abstract, is
     * not a subclass of {@link java.security.Permission}, or does not have
     * a public constructor that has either one <code>String</code> parameter
     * or one {@link Method} parameter and has no declared exceptions
     **/
    public SystemAccessILFactory(MethodConstraints serverConstraints,
				 Class permClass,
				 GroupPolicy policy,
				 ClassLoader loader)
    {
	super(serverConstraints, permClass, loader);
	this.policy = policy;
	this.localAccessCheck = false;
    }

    /**
     * Returns a {@link SystemDispatcher} instance constructed with the
     * specified methods, the class loader specified during construction,
     * the remote object, server capabilities, and the server constraints,
     * permission class, and group policy that this factory was constructed
     * with and a flag indicating whether the dispatcher should only accept
     * calls from the local host.
     *
     * @return a {@link SystemDispatcher} instance constructed with the
     * specified methods, remote object, and server capabilities, and the
     * server constraints, permission class, group policy, local host access
     * check condition, and class loader that this factory was constructed
     * with
     * @throws NullPointerException {@inheritDoc}
     **/
    protected InvocationDispatcher
	createInvocationDispatcher(Collection methods,
				   Remote impl,
				   ServerCapabilities caps)
	throws ExportException
    {
	if (impl == null) {
	    throw new NullPointerException("impl cannot be null");
	} else if (!(impl instanceof ActivationSystem)) {
	    throw new ExportException("cannot create dispatcher",
				      new IllegalArgumentException(
					  "impl must be an ActivationSystem"));
	}
	return new SystemDispatcher(methods, impl, caps,
				    getServerConstraints(),
				    getPermissionClass(), policy,
				    localAccessCheck, getClassLoader());
    }

    /**
     * A subclass of {@link BasicInvocationDispatcher} for
     * <code>ActivationSystem</code> instances that optionally enforces a
     * {@link GroupPolicy} on calls to <code>registerGroup</code> and
     * <code>setActivationGroupDesc</code>.
     */
    public static class SystemDispatcher extends BasicInvocationDispatcher {
	/**
	 * The group policy, if any.
	 */
	private final GroupPolicy policy;

	/**
	 * If true, check that client is calling from the local host.
	 */
	private final boolean localAccessCheck;

	/**
	 * Creates an invocation dispatcher to receive incoming remote calls
	 * for the specified methods, for a server and transport with the
	 * specified capabilities, enforcing the specified constraints, and
	 * performing preinvocation access control using the specified
	 * permission class and group policy.  The specified class loader
	 * is used by the {@link #createMarshalInputStream
	 * createMarshalInputStream} method.
	 * 
	 * <p>For each combination of constraints that might need to be
	 * enforced (obtained by calling the {@link
	 * MethodConstraints#possibleConstraints possibleConstraints}
	 * method on the specified server constraints, or using an empty
	 * constraints instance of the specified server constraints
	 * instance is <code>null</code>), calling the {@link
	 * ServerCapabilities#checkConstraints checkConstraints}
	 * method of the specified capabilities object with those
	 * constraints must return <code>true</code>, or an
	 * <code>ExportException</code> is thrown.
	 *
	 * @param methods a collection of {@link Method} instances for the
	 * remote methods
	 * @param impl the remote object
	 * @param serverCaps the transport capabilities of the server
	 * @param serverConstraints the server constraints, or
	 * <code>null</code>
	 * @param permClass the permission class, or <code>null</code>
	 * @param policy the group policy, or <code>null</code>
	 * @param localAccessCheck if <code>true</code>, calls are only
	 * accepted from the local host
	 * @param loader the class loader, or <code>null</code>
	 * @throws IllegalArgumentException if <code>impl</code> is not an
	 * instance of {@link ActivationSystem} or if the permission class is
	 * abstract, is not a subclass of {@link java.security.Permission},
	 * or does not have a public constructor that has either one
	 * <code>String</code> parameter or one {@link Method} parameter and
	 * has no declared exceptions, or if any element of
	 * <code>methods</code> is not a {@link Method} instance
	 * @throws NullPointerException if <code>impl</code>,
	 * <code>methods</code> or <code>serverCaps</code> is
	 * <code>null</code>, or if <code>methods</code> contains a
	 * <code>null</code> element 
	 * @throws ExportException if any of the possible server constraints
	 * cannot be satisfied according to the specified server capabilities
	 **/
	public SystemDispatcher(Collection methods,
				Remote impl,
				ServerCapabilities serverCaps,
				MethodConstraints serverConstraints,
				Class permClass,
				GroupPolicy policy,
				boolean localAccessCheck,
				ClassLoader loader)
	    throws ExportException
	{
	    super(methods, serverCaps, serverConstraints, permClass, loader);
	    if (impl == null) {
		throw new NullPointerException("impl is null");
	    }
	    if (!(impl instanceof ActivationSystem)) {
		throw new IllegalArgumentException(
		    "impl not an ActivationSystem instance");
	    }
	    this.policy = policy;
	    this.localAccessCheck = localAccessCheck;
	}

	/**
	 * Checks that the client is calling from the local host.
	 *
	 * @throws java.security.AccessControlException if the client is not
	 * calling from the local host
	 */
	protected void checkAccess(Remote impl,
				   Method method,
				   InvocationConstraints constraints,
				   Collection context)
	{
	    if (localAccessCheck) {
		LocalAccess.check();
	    } else {
		super.checkAccess(impl, method, constraints, context);
	    }
	}

	/**
	 * Checks the group policy as necessary, and then calls the superclass
	 * <code>invoke</code> method with the same arguments, and returns
	 * the result. If the policy is not <code>null</code> and the
	 * specified method is <code>ActivationSystem.registerGroup</code> or
	 * <code>ActivationSystem.setActivationGroupDesc</code>, the policy
	 * is checked by calling its {@link GroupPolicy#checkGroup checkGroup}
	 * method with the <code>ActivationGroupDesc</code> argument.
	 *
	 * @throws SecurityException if the caller is not permitted to use
	 * the specified group descriptor
	 */
	protected Object invoke(Remote impl,
				Method method,
				Object[] args,
				Collection context)
	    throws Throwable
	{
	    if (policy != null) {
		String op = method.getName();
		if (op.equals("registerGroup") ||
		    op.equals("setActivationGroupDesc"))
		{
		    policy.checkGroup(
				  (ActivationGroupDesc) args[args.length - 1]);
		}
	    }
	    return super.invoke(impl, method, args, context);
	}
    }
}
