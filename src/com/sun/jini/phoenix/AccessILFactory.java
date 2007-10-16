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
import java.rmi.server.ExportException;
import java.rmi.Remote;
import java.util.Collection;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicInvocationDispatcher;
import net.jini.jeri.InvocationDispatcher;
import net.jini.jeri.ServerCapabilities;

/**
 * A basic invocation layer factory, used in exporting an
 * <code>ActivationMonitor</code> or <code>ActivationInstantiator</code> to
 * use Jini extensible remote invocation (Jini ERI), that is similar to
 * {@link BasicILFactory} except the returned invocation dispatcher only
 * accepts calls from the local host.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 **/
public class AccessILFactory extends BasicILFactory {

    /**
     * Creates an <code>AccessILFactory</code>instance with no server
     * constraints, no permission class, and a <code>null</code> class
     * loader.
     **/
    public AccessILFactory() {
    }

    /**
     * Creates an <code>AccessILFactory</code>instance with no server
     * constraints, no permission class, and the specified class loader.
     * The specified class loader is used by the {@link #createInstances
     * createInstances} method.
     *
     * @param loader the class loader, or <code>null</code>
     **/
    public AccessILFactory(ClassLoader loader) {
	super(null, null, loader);
    }

    /**
     * Returns an {@link AccessDispatcher} instance constructed with the
     * specified methods, the specified server capabilities, and the class
     * loader specified at construction.
     *
     * @return the {@link AccessDispatcher} instance
     * @throws NullPointerException {@inheritDoc}
     **/
    protected InvocationDispatcher
        createInvocationDispatcher(Collection methods,
				   Remote impl,
				   ServerCapabilities caps)
	throws ExportException
    {
	if (impl == null) {
	    throw new NullPointerException("impl is null");
	}
	return new AccessDispatcher(methods, caps, getClassLoader());
    }

    /**
     * A subclass of {@link BasicInvocationDispatcher} that only accepts
     * calls from the local host.
     */
    public static class AccessDispatcher extends BasicInvocationDispatcher {
	/**
	 * Constructs an invocation dispatcher for the specified methods.
	 *
	 * @param	methods a collection of {@link Method} instances
	 *		for the	remote methods
	 * @param	caps the transport capabilities of the server
	 * @param	loader the class loader, or <code>null</code>
	 * @throws	NullPointerException if <code>methods</code> is
	 *		<code>null</code> or if <code>methods</code> contains a
	 *		<code>null</code> elememt
	 * @throws	IllegalArgumentException if <code>methods</code>
	 *		contains an element that is not a <code>Method</code>
	 *		instance
	 */
	public AccessDispatcher(Collection methods,
				ServerCapabilities caps,
				ClassLoader loader)
	    throws ExportException
	{
	    super(methods, caps, null, null, loader);
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
	    LocalAccess.check();
	}
    }
}
