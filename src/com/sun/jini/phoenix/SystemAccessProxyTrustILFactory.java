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
import java.rmi.activation.ActivationSystem;
import java.rmi.server.ExportException;
import java.util.Collection;
import net.jini.core.constraint.MethodConstraints;
import net.jini.jeri.InvocationDispatcher;
import net.jini.jeri.ProxyTrustILFactory;
import net.jini.jeri.ServerCapabilities;

/**
 * Invocation layer factory for exporting an {@link ActivationSystem}
 * to use Jini extensible remote invocation (Jini ERI), that is similar
 * to {@link ProxyTrustILFactory} except the remote object must be an
 * <code>ActivationSystem</code> instance and the returned dispatcher
 * optionally accepts calls from the local host and optionally enforces a
 * {@link GroupPolicy} on calls to {@link ActivationSystem#registerGroup
 * registerGroup} and {@link ActivationSystem#setActivationGroupDesc
 * setActivationGroupDesc}.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0.1
 * @see SystemAccessILFactory
 **/
public class SystemAccessProxyTrustILFactory extends ProxyTrustILFactory {
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
    public SystemAccessProxyTrustILFactory() {
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
    public SystemAccessProxyTrustILFactory(GroupPolicy policy,
					   ClassLoader loader)
    {
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
    public SystemAccessProxyTrustILFactory(MethodConstraints serverConstraints)
    {
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
    public SystemAccessProxyTrustILFactory(MethodConstraints serverConstraints,
					   Class permClass,
					   GroupPolicy policy,
					   ClassLoader loader)
    {
	super(serverConstraints, permClass, loader);
	this.policy = policy;
	this.localAccessCheck = false;
    }

    /**
     * Returns a
     * {@link com.sun.jini.phoenix.SystemAccessILFactory.SystemDispatcher}
     * instance constructed with the specified methods, the class loader
     * specified during construction, the remote object, server capabilities,
     * and the server constraints, permission class, and group policy that
     * this factory was constructed with and a flag indicating whether the
     * dispatcher should only accept calls from the local host.
     *
     * @return a
     * {@link com.sun.jini.phoenix.SystemAccessILFactory.SystemDispatcher}
     * instance constructed with the specified methods, remote object, and
     * server capabilities, and the server constraints, permission class,
     * group policy, local host access check condition, and class loader that
     * this factory was constructed with
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
	return new SystemAccessILFactory.SystemDispatcher(
				    methods, impl, caps,
				    getServerConstraints(),
				    getPermissionClass(), policy,
				    localAccessCheck, getClassLoader());
    }
}
