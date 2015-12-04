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

package net.jini.jeri;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.rmi.server.ExportException;
import java.security.Permission;
import java.util.Collection;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.proxytrust.TrustEquivalence;

/**
 * A basic implementation of an {@link InvocationLayerFactory}.  This
 * factory is used to create a {@link Proxy} instance with a {@link
 * BasicInvocationHandler} and to create a {@link
 * BasicInvocationDispatcher} for a remote object being exported.  This
 * factory is used in conjunction with the {@link BasicJeriExporter} class.
 *
 * @author	Sun Microsystems, Inc.
 * @since 2.0
 **/
public class BasicILFactory extends AbstractILFactory {

    /**
     * The server constraints, or null;
     */
    private final MethodConstraints serverConstraints;
    /**
     * The permission class, or null.
     */
    private final Class permissionClass;

    /**
     * Creates a <code>BasicILFactory</code> instance with no server
     * constraints, no permission class, and a <code>null</code> class
     * loader.
     **/
    public BasicILFactory() {
	this.serverConstraints = null;
	this.permissionClass = null;
    }

    /**
     * Creates a <code>BasicILFactory</code> with the specified server
     * constraints, permission class, and a <code>null</code> class
     * loader.
     *
     * @param	serverConstraints the server constraints, or <code>null</code>
     * @param	permissionClass the permission class, or <code>null</code>
     * @throws	IllegalArgumentException if the permission class is
     *		abstract, is not a subclass of {@link Permission}, or does
     *		not have a public constructor that has either one
     *		<code>String</code> parameter or one {@link Method}
     *		parameter and has no declared exceptions
     **/
    public BasicILFactory(MethodConstraints serverConstraints,
			  Class permissionClass)
    {
	this(serverConstraints, permissionClass, null);
    }
    
    /**
     * Creates a <code>BasicILFactory</code> with the specified server
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
     *		abstract, is not a subclass of {@link Permission}, or does
     *		not have a public constructor that has either one
     *		<code>String</code> parameter or one {@link Method}
     *		parameter and has no declared exceptions
     **/
    public BasicILFactory(MethodConstraints serverConstraints,
			  Class permissionClass,
			  ClassLoader loader)
    {
	super(loader);
	BasicInvocationDispatcher.checkPermissionClass(permissionClass);
	this.serverConstraints = serverConstraints;
	this.permissionClass = permissionClass;
    }
    
    /**
     * Returns an invocation handler to use with a {@link Proxy} instance
     * implementing the specified interfaces, communicating with the
     * specified remote object using the specified object endpoint.
     *
     * <p><code>BasicILFactory</code> implements this method to
     * return a {@link BasicInvocationHandler} constructed with the
     * specified object endpoint and this factory's server constraints.
     *
     * @throws	NullPointerException {@inheritDoc}
     **/
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
	return new BasicInvocationHandler(oe, serverConstraints);
    }
    
    /**
     * Returns a new array containing any additional interfaces that the
     * proxy should implement, beyond the interfaces obtained by passing
     * <code>impl</code> to the {@link #getRemoteInterfaces
     * getRemoteInterfaces} method.
     *
     * <p><code>BasicILFactory</code> implements this method to return a
     * new array containing the {@link RemoteMethodControl} and {@link
     * TrustEquivalence} interfaces, in that order.
     *
     * @throws NullPointerException {@inheritDoc}
     **/
    protected Class[] getExtraProxyInterfaces(Remote impl) {
	if (impl == null) {
	    throw new NullPointerException();
	}
	return new Class[]{RemoteMethodControl.class, TrustEquivalence.class};
    }

    /**
     * Returns an invocation dispatcher to receive incoming remote calls
     * for the specified methods to the specified remote object, for a
     * server and transport with the specified capabilities.
     *
     * <p><code>BasicILFactory</code> implements this method to return a
     * {@link BasicInvocationDispatcher} instance constructed with the
     * specified methods, server capabilities, and this factory's
     * server constraints, permission class, and class loader specified
     * at construction.
     * 
     * @throws 	NullPointerException {@inheritDoc}
     * @throws	IllegalArgumentException {@inheritDoc}
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
	return new BasicInvocationDispatcher(methods, caps,
					     serverConstraints,
					     permissionClass,
					     getClassLoader());
    }
    
    /**
     * Returns the server constraints, if any.
     *
     * @return the server constraints, or <code>null</code>
     **/
    public final MethodConstraints getServerConstraints() {
	return serverConstraints;
    }

    /**
     * Returns the permission class, if any.
     *
     * @return the permission class, or <code>null</code>
     */
    public final Class getPermissionClass() {
	return permissionClass;
    }

    /**
     * Returns a hash code value for this factory.
     **/
    @Override
    public int hashCode() {
	int h = super.hashCode();
	if (serverConstraints != null) {
	    h += serverConstraints.hashCode();
	}
	if (permissionClass != null) {
	    h += permissionClass.hashCode();
	}
	return h;
    }

    /**
     * Compares the specified object with this invocation layer factory for
     * equality.
     *
     * <p><code>BasicILFactory</code> implements this method to return
     * <code>true</code> if and only if invoking the superclass's
     * <code>equals</code> method passing the specified object returns
     * <code>true</code>, the specified object has the same class as this
     * object, and the server constraints and permission class are equal to
     * the ones in this object.
     **/
    @Override
    public boolean equals(Object obj) {
	if (!super.equals(obj)) {
	    return false;
	}
	BasicILFactory ilf = (BasicILFactory) obj;
	return (permissionClass == ilf.permissionClass &&
		(serverConstraints == ilf.serverConstraints ||
		 (serverConstraints != null &&
		  serverConstraints.equals(ilf.serverConstraints))));
    }

    /**
     * Returns a string representation of this factory.
     **/
    public String toString() {
        StringBuilder sb = new StringBuilder(100);
        sb.append(super.toString())
          .append('[')
          .append(serverConstraints);
        if (permissionClass != null) 
            sb.append(", ").append(permissionClass.getName());      
        sb.append(']');
        return sb.toString();
    }
}
