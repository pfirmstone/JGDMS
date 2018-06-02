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
package net.jini.jeri;

import java.lang.reflect.InvocationHandler;
import java.rmi.Remote;
import java.rmi.server.ExportException;
import java.util.Collection;
import net.jini.core.constraint.MethodConstraints;

/**
 * An atomic serialization implementation of an {@link InvocationLayerFactory}.  
 * This factory is used to create a {@link java.lang.reflect.Proxy} instance with a {@link
 * AtomicInvocationHandler} and to create a {@link
 * AtomicInvocationDispatcher} for a remote object being exported.  This
 * factory is used in conjunction with the {@link BasicJeriExporter} class.
 * 
 * Serialization is performed with atomic input validation.  Class codebase
 * annotations are not appended in the stream, instead each endpoint
 * must have the same class visibility via the default ClassLoader.
 *
 * @author  Peter.
 * @since 3.1
 **/
public class AtomicILFactory extends BasicILFactory {
    
    /**
     * Creates a <code>AtomicILFactory</code> with the specified server
     * constraints, permission class, and class loader.  The server
     * constraints, if not <code>null</code>, are used to enforce minimum
     * constraints for remote calls. The permission class, if not
     * <code>null</code>, is used to perform server-side access control on
     * incoming remote calls.  The class loader, which may be
     * <code>null</code>, is passed to the superclass constructor and is
     * used by the {@link #createInstances createInstances}
     * method.
     *
     * @param	serverConstraints the server constraints, or <code>null</code>
     * @param	permissionClass the permission class, or <code>null</code>
     * @param	loader the class loader, or <code>null</code>
     * @throws	IllegalArgumentException if the permission class is
     *		abstract, is not a subclass of {@link java.security.Permission}, or does
     *		not have a public constructor that has either one
     *		<code>String</code> parameter or one {@link java.lang.reflect.Method}
     *		parameter and has no declared exceptions
     **/
    public AtomicILFactory(MethodConstraints serverConstraints,
			  Class permissionClass,
			  ClassLoader loader)
    {
	super(serverConstraints, permissionClass, loader);
    }
    
    /**
     * Creates a <code>AtomicILFactory</code> with the specified server
     * constraints, and proxy or service implementation class.
     * The server constraints, if not <code>null</code>, are used to enforce 
     * minimum constraints for remote calls.  The proxy or service implementation class, 
     * which cannot be <code>null</code>, is used to obtain the ClassLoader
     * to be passed to the superclass constructor and is used by the 
     * {@link #createInstances createInstances} method.
     *
     * @param	serverConstraints the server constraints, or <code>null</code>
     * @param	proxyOrServiceImplClass the class of the smart proxy 
     *		implementation or the class of the service implementation 
     *		for dynamic proxy's.
     * @throws	IllegalArgumentException if the permission class is
     *		abstract, is not a subclass of {@link java.security.Permission}, or does
     *		not have a public constructor that has either one
     *		<code>String</code> parameter or one {@link java.lang.reflect.Method}
     *		parameter and has no declared exceptions
     * @throws SecurityException if caller doesn't have {@link RuntimePermission} 
     *		"getClassLoader".
     * @throws NullPointerException if proxyorServiceImplClass is null.
     **/
    public AtomicILFactory(MethodConstraints serverConstraints,
			    Class proxyOrServiceImplClass)
    {
	this(serverConstraints, null, proxyOrServiceImplClass.getClassLoader());
    }
    
    /**
     * Creates a <code>AtomicILFactory</code> with the specified server
     * constraints, permission class, and proxy or service implementation class.
     * The server constraints, if not <code>null</code>, are used to enforce 
     * minimum constraints for remote calls. The permission class, if not
     * <code>null</code>, is used to perform server-side access control on
     * incoming remote calls.  The proxy or service implementation class, 
     * which cannot be <code>null</code>, is used to obtain the ClassLoader
     * to be passed to the superclass constructor and is used by the 
     * {@link #createInstances createInstances} method.
     *
     * @param	serverConstraints the server constraints, or <code>null</code>
     * @param	permissionClass the permission class, or <code>null</code>
     * @param	proxyOrServiceImplClass the class of the smart proxy 
     *		implementation or the class of the service implementation 
     *		for dynamic proxy's.
     * @throws	IllegalArgumentException if the permission class is
     *		abstract, is not a subclass of {@link java.security.Permission}, or does
     *		not have a public constructor that has either one
     *		<code>String</code> parameter or one {@link java.lang.reflect.Method}
     *		parameter and has no declared exceptions
     * @throws SecurityException if caller doesn't have {@link RuntimePermission} 
     *		"getClassLoader".
     * @throws NullPointerException if proxyorServiceImplClass is null.
     **/
    public AtomicILFactory(MethodConstraints serverConstraints,
			    Class permissionClass,
			    Class proxyOrServiceImplClass)
    {
	this(serverConstraints, permissionClass, proxyOrServiceImplClass.getClassLoader());
    }
    
    /**
     * Returns an invocation handler to use with a {@link java.lang.reflect.Proxy} instance
     * implementing the specified interfaces, communicating with the
     * specified remote object using the specified object endpoint.
     *
     * <p><code>AtomicILFactory</code> implements this method to
     * return a {@link BasicInvocationHandler} constructed with the
     * specified object endpoint and this factory's server constraints.
     *
     * @return a new InvocationHandler instance.
     * @throws java.rmi.server.ExportException if there is a problem creating the
     * 		invocation handler
     * @throws	NullPointerException {@inheritDoc}
     **/
    @Override
    protected InvocationHandler createInvocationHandler(Class[] interfaces,
							Remote impl,
							ObjectEndpoint oe)
	throws ExportException
    {
	for (int i = interfaces.length; --i >= 0; ) {
	    if (interfaces[i] == null) {
		throw new NullPointerException();
	    }
	}
	if (impl == null) {
	    throw new NullPointerException();
	}
	return new AtomicInvocationHandler(oe, getServerConstraints());
    }
    
    /**
     * Returns an invocation dispatcher to receive incoming remote calls
     * for the specified methods to the specified remote object, for a
     * server and transport with the specified capabilities.
     *
     * <p><code>AtomicILFactory</code> implements this method to return a
     * {@link BasicInvocationDispatcher} instance constructed with the
     * specified methods, server capabilities, and this factory's
     * server constraints, permission class, and class loader specified
     * at construction.
     * 
     * @return a new InvocationDispatcher instance.
     * @throws java.rmi.server.ExportException if there is a problem creating the
     * 		invocation dispatcher.
     * @throws 	NullPointerException {@inheritDoc}
     * @throws	IllegalArgumentException {@inheritDoc}
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
	return new AtomicInvocationDispatcher(methods, caps,
					     getServerConstraints(),
					     getPermissionClass(),
					     getClassLoader());
    }
	
    @Override
    public boolean equals(Object o){
	if (o instanceof AtomicILFactory) return super.equals(o);
	return false;
    }

    @Override
    public int hashCode() {
	int hash = 5;
	return hash ^ super.hashCode();
    }
}
