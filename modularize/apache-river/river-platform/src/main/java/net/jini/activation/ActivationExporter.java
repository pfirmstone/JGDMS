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

package net.jini.activation;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.rmi.activation.ActivationID;
import java.rmi.server.ExportException;
import java.security.Permission;
import java.util.LinkedHashSet;
import java.util.Set;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.Exporter;
import net.jini.security.proxytrust.ProxyTrust;

/**
 * An <code>Exporter</code> implementation for exporting an activatable
 * remote object using its underlying exporter.  The proxy returned by
 * the <code>export</code> method activates the remote object on
 * demand.  Each instance of <code>ActivationExporter</code> can export only
 * a single remote object.
 *
 * @author	Sun Microsystems, Inc.
 * @since 2.0
 **/
public final class ActivationExporter implements Exporter
{
    /** the activation identifier */
    private final ActivationID id;
    /** the underlying exporter */
    private final Exporter underlyingExporter;
    /** The class loader to define the proxy class in */
    private final ClassLoader loader;
    /** If true, this exporter has already been used to export an object */
    private boolean used = false;

    /** Cached getClassLoader permission */
    private static final Permission getClassLoaderPermission =
	new RuntimePermission("getClassLoader");

    /**
     * Creates an exporter for an activatable remote object with the
     * specified activation identifier, underlying exporter, and a
     * <code>null</code> class loader.
     *
     * @param	id an activation identifier
     * @param 	underlyingExporter an exporter
     * @throws	NullPointerException if <code>id</code>  or
     *		<code>underlyingExporter</code> is <code>null</code>
     **/
    public ActivationExporter(ActivationID id,
			      Exporter underlyingExporter)
    {
	this(id, underlyingExporter, null);
    }

    /**
     * Creates an exporter for an activatable remote object with the
     * specified activation identifier, underlying exporter, and
     * class loader.
     *
     * @param	id an activation identifier
     * @param 	underlyingExporter an exporter
     * @param	loader the class loader to define the proxy class in, or
     *		<code>null</code> 
     * @throws	NullPointerException if <code>id</code>  or
     *		<code>underlyingExporter</code> is <code>null</code>
     **/
    public ActivationExporter(ActivationID id,
			      Exporter underlyingExporter,
			      ClassLoader loader)
    {
	if (id == null || underlyingExporter == null) {
	    throw new NullPointerException();
	}
	this.id = id;
	this.underlyingExporter = underlyingExporter;
	this.loader = loader;
    }
    
    /**
     * Exports an activatable remote object.  This exporter exports
     * <code>impl</code> by calling the <code>export</code> method on the
     * underlying exporter (supplied during construction of this exporter)
     * to obtain an underlying proxy.  It then constructs and returns a
     * {@link Proxy} instance where:
     *
     * <ul>
     * <li>If the class loader specified at construction is not
     * <code>null</code>, the proxy's class is defined by the specified
     * loader.  Otherwise, if a security manager exists, its {@link
     * SecurityManager#checkPermission checkPermission} method is invoked
     * with the permission <code>{@link
     * RuntimePermission}{"getClassLoader")</code>; this invocation may
     * throw a <code>SecurityException</code>.  If the above security check
     * succeeds, the proxy's class is defined by the class loader of the
     * underlying proxy's class.
     *
     * <li>The proxy implements the following ordered list of interfaces
     * (except if the underlying proxy is an instance of {@link
     * RemoteMethodControl}, the interface {@link ProxyTrust} is not among
     * the interfaces implemented by the proxy):
     *
     * <p>for each superclass of the underlying proxy's class, starting
     * with <code>java.lang.Object</code> and following with each direct
     * subclass to the direct superclass of the underlying proxy's class,
     * all of the direct superinterfaces of the given superclass that do
     * not appear previously in the list, in declaration order (the order
     * in which they are declared in the class's <code>implements</code>
     * clause), followed by
     * 
     * <p>all of the direct superinterfaces of the underlying proxy's class
     * that do not appear previously in the list, in declaration order.
     *
     * <li>The proxy's invocation handler is an {@link
     * ActivatableInvocationHandler} instance constructed with the {@link
     * ActivationID} and underlying proxy  supplied during construction of
     * this exporter. 
     * </ul>
     *
     * @throws	NullPointerException {@inheritDoc}
     * @throws	IllegalStateException {@inheritDoc}
     * @throws	ExportException if a problem occurs exporting
     *		<code>impl</code> or if the underlying proxy's class
     *		is non-<code>public</code> and implements
     *		non-<code>public</code> interfaces
     **/
    public synchronized Remote export(Remote impl)
	throws ExportException
    {
	/*
	 * Check if object is already exported; disallow exporting more
	 * than once via this exporter.
	 */
	if (used) {
	    throw new IllegalStateException(
		"object already exported via this exporter");
	}

	if (impl == null) {
	    throw new NullPointerException("impl is null");
	}

	/*
	 * Export the remote object using the underlying exporter.
	 */
	Remote uproxy = underlyingExporter.export(impl);
	used = true;
	boolean success = false;
	
	try {
	    /*
	     * Choose class loader for proxy's class.
	     */
	    Class uproxyClass = uproxy.getClass();
	    ClassLoader proxyLoader;
	    if (loader != null) {
		proxyLoader = loader;
	    } else {
		SecurityManager security = System.getSecurityManager();
		if (security != null) {
		    security.checkPermission(getClassLoaderPermission);
		}
		proxyLoader = uproxyClass.getClassLoader();
	    }
	
	    /*
	     * Get superinterfaces of underlying proxy.
	     */
	    Set interfaceList = new LinkedHashSet();
	    boolean isConstrainable = uproxy instanceof RemoteMethodControl;
	    boolean checkPublic =
		!Modifier.isPublic(uproxyClass.getModifiers());
	    getSuperinterfaces(interfaceList, uproxyClass,
			       isConstrainable, checkPublic);
	    Class[] proxyInterfaces = (Class[])
		interfaceList.toArray(new Class[interfaceList.size()]);
	    
	    /*
	     * Create a dynamic proxy with an ActivatableInvocationHandler.
	     */
	    InvocationHandler handler =
		new ActivatableInvocationHandler(id, uproxy);
	    
	    Remote proxy = (Remote)
		Proxy.newProxyInstance(proxyLoader,
				       proxyInterfaces,
				       handler);
	    success = true;
	    return proxy;
	    
	} catch (IllegalArgumentException e) {
	    throw new ExportException("proxy creation failed", e);
	    
	} finally {
	    if (!success) {
		unexport(true);
	    }
	}
    }

    /**
     * Unexports the activatable remote object that was previously exported
     * via the <code>export</code> method of the underlying exporter
     * supplied during construction of this exporter.  Returns the result
     * of unexporting the remote object by calling the
     * <code>unexport</code> method on the underlying exporter passing
     * <code>force</code> as the argument.
     *
     * @throws	IllegalStateException {@inheritDoc}
     **/
    public synchronized boolean unexport(boolean force) {
	
	if (!used) {
	    throw new IllegalStateException(
		"an object has not been exported via this exporter");
	}

	return underlyingExporter.unexport(force);
    }

    /**
     * Fills the given array list with the superinterfaces implemented by
     * the given class eliminating duplicates.  If isConstrainable is true,
     * then the ProxyTrust interface won't be added to the list.
     *
     * @param	list the list to fill with interfaces
     * @param	cl the class to get the superinterfaces of
     * @param	isConstrainable true if proxy class implements
     *		RemoteMethodControl
     * @param	checkPublic true if proxy has a non-public class
     * @throws	IllegalArgumentException if the specified class implements
     * 		any illegal remote interfaces
     * @throws	NullPointerException if the specified class or list is null
     * @throws	ExportException if proxy has a non-public class and
     *		implements a non-public interface
     **/
    private static void getSuperinterfaces(Set list,
					   Class cl,
					   boolean isConstrainable,
					   boolean checkPublic)
	throws ExportException
    {
	Class superclass = cl.getSuperclass();
	if (superclass != null) {
	    getSuperinterfaces(list, superclass, isConstrainable, checkPublic);
	}
	
	Class[] interfaces = cl.getInterfaces();
	for (int i = 0; i < interfaces.length; i++) {
	    Class intf = interfaces[i];
	    /*
	     * Complain if the underlying proxy has a non-public class and
	     * implements non-public interfaces.
	     */
	    if (checkPublic && !Modifier.isPublic(intf.getModifiers())) {
		throw new ExportException(
		    "proxy implements non-public interface " + intf.getName());
	    }
	    
	    /*
	     * Don't add ProxyTrust remote interface to list of proxy
	     * interfaces if proxy is "constrainable" (that is implements
	     * RemoteMethodControl) and don't add duplicates.
	     */
	    if (!isConstrainable || intf != ProxyTrust.class) {
		list.add(intf);
	    }
	}
    }
}
