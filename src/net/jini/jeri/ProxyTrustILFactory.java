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

import java.rmi.Remote;
import java.rmi.server.ExportException;
import net.jini.core.constraint.MethodConstraints;
import net.jini.security.proxytrust.ProxyTrust;
import net.jini.security.proxytrust.ServerProxyTrust;

/**
 * Invocation layer factory for remote objects exported to use Jini
 * extensible remote invocation (Jini ERI) that produces proxies that
 * additionally implement the {@link ProxyTrust} interface. The remote object
 * being exported must be an instance of {@link ServerProxyTrust}, and the
 * {@link ProxyTrust#getProxyVerifier ProxyTrust.getProxyVerifier} remote
 * method of the proxy is implemented in the invocation dispatcher by
 * invoking the {@link ServerProxyTrust#getProxyVerifier
 * ServerProxyTrust.getProxyVerifier} local method of the remote object.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 * @see net.jini.security.proxytrust.ProxyTrustExporter
 */
public class ProxyTrustILFactory extends BasicILFactory {

    /**
     * Creates a factory with the specified server constraints, permission
     * class, and a <code>null</code> class loader. The server constraints,
     * if not <code>null</code>, are used to enforce minimum constraints
     * for remote calls. The permission class, if not <code>null</code>, is
     * used to perform server-side access control on incoming remote calls.
     *
     * @param serverConstraints the server constraints, or <code>null</code>
     * @param permissionClass the permission class, or <code>null</code>
     * @throws IllegalArgumentException if the permission class is abstract, is
     * not a subclass of {@link java.security.Permission}, or does not have
     * a public constructor that has either one <code>String</code> parameter
     * or one {@link java.lang.reflect.Method} parameter and has no declared
     * exceptions
     **/
    public ProxyTrustILFactory(MethodConstraints serverConstraints,
			       Class permissionClass)
    {
	this(serverConstraints, permissionClass, null);
    }
    
    /**
     * Creates a factory with the specified server constraints, permission
     * class, and class loader. The server constraints, if not
     * <code>null</code>, are used to enforce minimum constraints for
     * remote calls. The permission class, if not <code>null</code>, is
     * used to perform server-side access control on incoming remote calls.
     * The specified loader is passed to the superclass constructor and
     * used by the {@link #createInstances createInstances} method.
     *
     * @param serverConstraints the server constraints, or <code>null</code>
     * @param permissionClass the permission class, or <code>null</code>
     * @param loader the class loader, or <code>null</code>
     * @throws IllegalArgumentException if the permission class is abstract, is
     * not a subclass of {@link java.security.Permission}, or does not have
     * a public constructor that has either one <code>String</code> parameter
     * or one {@link java.lang.reflect.Method} parameter and has no declared
     * exceptions
     **/
    public ProxyTrustILFactory(MethodConstraints serverConstraints,
			       Class permissionClass,
			       ClassLoader loader)

    {
	super(serverConstraints, permissionClass, loader);
    }

    /**
     * Returns a new array containing the remote interfaces that should be
     * implemented by the proxy.
     *
     * <p><code>ProxyTrustILFactory</code> implements this method to return
     * an array containing the interfaces obtained by invoking {@link
     * BasicILFactory#getRemoteInterfaces super.getRemoteInterfaces}
     * passing <code>impl</code> followed by the {@link ProxyTrust}
     * interface.
     *
     * @throws NullPointerException {@inheritDoc}
     * @throws ExportException if the remote object is not an instance of
     * {@link ServerProxyTrust} or implements any illegal remote interfaces
     **/
    protected Class[] getRemoteInterfaces(Remote impl) throws ExportException {
	if (impl != null && !(impl instanceof ServerProxyTrust)) {
	    throw new ExportException("impl must implement ServerProxyTrust");
	}
	Class[] ifs = super.getRemoteInterfaces(impl);
	if (ifs == null) {
	    return new Class[]{ProxyTrust.class};
	}
	Class[] nifs = new Class[ifs.length + 1];
	System.arraycopy(ifs, 0, nifs, 0, ifs.length);
	nifs[ifs.length] = ProxyTrust.class;
	return nifs;
    }
}
