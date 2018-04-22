/*
 * Copyright 2018 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.phoenix.common;

import java.lang.reflect.Method;
import java.rmi.Remote;
import java.rmi.server.ExportException;
import java.util.Collection;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.AtomicILFactory;
import net.jini.jeri.AtomicInvocationDispatcher;
import net.jini.jeri.InvocationDispatcher;
import net.jini.jeri.ServerCapabilities;

/**
 *
 * @author peter
 */
public class AccessAtomicILFactory extends AtomicILFactory {
    
    /**
     * Creates an <code>AccessILFactory</code>instance with no server
     * constraints, no permission class, and the specified class loader.
     * The specified class loader is used by the {@link #createInstances
     * createInstances} method.
     *
     * @param loader the class loader, or <code>null</code>
     **/
    public AccessAtomicILFactory(ClassLoader loader) {
	super(null, null, loader);
    }
    
    /**
     * Creates an <code>AccessILFactory</code>instance with no server
     * constraints, no permission class, and the specified smart proxy
     * or service implementation class, used to determined the ClassLoader.
     * The specified class loader of proxyOrService is used by the 
     * {@link #createInstancescreateInstances} method.
     *
     * @param proxyOrService class of the smart proxy or service implementation.
     * @throws NullPointerException if proxyOrService is null.
     **/
    public AccessAtomicILFactory(Class proxyOrService){
	this(proxyOrService.getClassLoader());
    }

    /**
     * Returns an {@link AccessDispatcher} instance constructed with the
     * specified methods, the specified server capabilities, and the class
     * loader specified at construction.
     *
     * @return the {@link AccessDispatcher} instance
     * @throws NullPointerException {@inheritDoc}
     **/
    @Override
    protected InvocationDispatcher
        createInvocationDispatcher(Collection methods,
				   Remote impl,
				   ServerCapabilities caps)
	throws ExportException
    {
	if (impl == null) {
	    throw new NullPointerException("impl is null");
	}
	return new AccessILFactory.AccessDispatcher(methods, caps, getClassLoader());
    }
	
    /**
     * A subclass of {@link AtomicInvocationDispatcher} that only accepts
     * calls from the local host.
     */
    public static class AccessDispatcher extends AtomicInvocationDispatcher {
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
	 * @throws ExportException doesn't throw ExportException as there are no
	 * constraints.
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
	@Override
	protected void checkAccess(Remote impl,
				   Method method,
				   InvocationConstraints constraints,
				   Collection context)
	{
	    LocalAccess.check();
	}
    }
}
