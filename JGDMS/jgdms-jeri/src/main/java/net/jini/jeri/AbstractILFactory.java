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

import org.apache.river.jeri.internal.runtime.Util;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.rmi.server.ExportException;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.ExportPermission;
import net.jini.security.Security;

/**
 * An abstract implementation of {@link InvocationLayerFactory} that
 * provides a convenient way for subclasses to create proxies and
 * invocation dispatchers for remote objects.  A subclass must provide an
 * implementation for at least the {@link #createInvocationHandler
 * createInvocationHandler} and {@link #createInvocationDispatcher
 * createInvocationDispatcher} methods.  A subclass can override the
 * {@link #getProxyInterfaces getProxyInterfaces} method if its proxies
 * need to implement a different set of interfaces than the default
 * set (all remote interfaces of the remote object).
 *
 * @author	Sun Microsystems, Inc.
 * @since 2.0
 **/
public abstract class AbstractILFactory implements InvocationLayerFactory {

    /** Cached getClassLoader permission */
    private static final Permission getClassLoaderPermission =
	new RuntimePermission("getClassLoader");

    /** The class loader to define proxy classes in */
    private final ClassLoader loader;
    
    /**
     * Constructs an <code>AbstractILFactory</code> instance with a
     * <code>null</code> class loader.
     **/
    protected AbstractILFactory() {
	this(null);
    }

    /**
     * Constructs an <code>AbstractILFactory</code> instance with the
     * specified class loader.  The {@link #createInstances
     * createInstances} method uses the specified loader to define a proxy
     * class.
     *
     * @param	loader the class loader, or <code>null</code> 
     **/
    protected AbstractILFactory(ClassLoader loader) {
	this.loader = loader;
    }

    /**
     * Returns a concatenated array of the interfaces in <code>i1</code>
     * followed by the interfaces in <code>i2</code> with duplicate
     * interfaces (that is, duplicates after the first occurence of an
     * interface) eliminated. 
     *
     * @param	i1 an array of interfaces
     * @param	i2 an array of interfaces
     * @return 	a concatenated array of the interfaces with duplicates
     *		eliminated
     * @throws	NullPointerException if any element in <code>i1</code> or
     *		<code>i2</code> is null
     **/
    private static Class[] combineInterfaces(Class[] i1, Class[] i2) {
	ArrayList list = new ArrayList(i1.length + i2.length);
	addInterfaces(list, i1);
	addInterfaces(list, i2);
	return (Class[]) list.toArray(new Class[list.size()]);
    }

    /**
     * Adds non-duplicate interfaces in the array to the list.
     *
     * @throws	NullPointerException if the interfaces array contains a
     *		<code>null</code> element 
     **/
    private static void addInterfaces(ArrayList list, Class[] interfaces) {
	for (int i = 0; i < interfaces.length; i++) {
	    Class cl = interfaces[i];
	    if (cl == null) {
		throw new NullPointerException("array contains null element");
	    }
	    if (!list.contains(cl)) {
		list.add(cl);
	    }
	}
    }

    /**
     * Returns the class loader specified during construction.
     * @return the class loader
     */
    protected final ClassLoader getClassLoader() {
	return loader;
    }
    
    /**
     * Returns a new array containing the interfaces for the proxy to
     * implement.
     *
     * <p><code>AbstractILFactory</code> implements this method to return
     * an array containing all of the interfaces obtained by passing
     * <code>impl</code> to the {@link #getRemoteInterfaces
     * getRemoteInterfaces} method plus the interfaces obtained by calling
     * the {@link #getExtraProxyInterfaces getExtraProxyInterfaces} method,
     * in that order, with duplicate interfaces (that is, duplicates after
     * the first occurrence of an interface) eliminated.
     *
     * <p>A subclass can override this method if its proxies need to
     * implement a different set of interfaces than the default.
     *
     * @param	impl the remote object
     * @return	the proxy interfaces
     * @throws	ExportException if there is a problem obtaining the
     *		proxy interfaces or if <code>impl</code> does not satisfy
     *		the requirements of this factory
     * @throws	NullPointerException if <code>impl</code> is <code>null</code>
     **/
    protected Class[] getProxyInterfaces(Remote impl)
	throws ExportException
    {
	return combineInterfaces(getRemoteInterfaces(impl),
				 getExtraProxyInterfaces(impl));
    }
    
    /**
     * Returns a new array containing the remote interfaces that should be
     * implemented by the proxy.  All of the methods of a remote interface
     * are implemented as remote invocations, so all of the methods must
     * declare {@link java.rmi.RemoteException} or one of its superclasses
     * in their <code>throws</code> clauses.
     *
     * <p><code>AbstractILFactory</code> implements this method to return
     * an array containing the following ordered list of interfaces:
     *
     * <ul><li>for each superclass of <code>impl's</code> class starting
     * with <code>java.lang.Object</code> and following with each direct
     * subclass to the direct superclass of <code>impl</code>'s class, all
     * of the direct superinterfaces of the given superclass that extend
     * {@link Remote} and that do not appear previously in the list, in
     * declaration order (the order in which they are declared in the
     * class's <code>implements</code> clause), followed by
     * 
     * <li>all of the direct superinterfaces of <code>impl</code>'s
     * class that extend {@link Remote} and that do not appear previously
     * in the list, in declaration order.
     * </ul>
     *
     * and throws an <code>ExportException</code> if any method of those
     * interfaces does not have a conforming <code>throws</code> clause.
     *
     * <p>A subclass can override this method if its proxies need a
     * set of remote interfaces other than the default.
     *
     * @param	impl the remote object
     * @return	the remote interfaces implemented by <code>impl</code>
     * @throws	NullPointerException if <code>impl</code> is <code>null</code>
     * @throws	ExportException if there is a problem obtaining the remote
     * 		interfaces or if <code>impl</code> does not satisfy the
     *		requirements of this factory
     **/
    protected Class[] getRemoteInterfaces(Remote impl) throws ExportException {
	if (impl == null) {
	    throw new NullPointerException("impl is null");
	}
	try {
	    return Util.getRemoteInterfaces(impl.getClass());
	} catch (IllegalArgumentException e) {
	    throw new ExportException("cannot get proxy interfaces", e);
	}
    }
    
    /**
     * Returns a new array containing any additional interfaces that the
     * proxy should implement, beyond the interfaces obtained by passing
     * <code>impl</code> to the {@link #getRemoteInterfaces
     * getRemoteInterfaces} method.
     *
     * <p><code>AbstractILFactory</code> implements this method to return
     * an array containing the {@link RemoteMethodControl} interface.
     *
     * <p>A subclass can override this method if its proxies need
     * to implement a different set of extra interfaces than the default.
     *
     * @param	impl the remote object
     * @return	the extra proxy interfaces
     * @throws	NullPointerException if <code>impl</code> is <code>null</code>
     * @throws	ExportException if there is a problem obtaining the additional
     *		interfaces or if <code>impl</code> does not satisfy the
     *		requirements of this factory
     **/
    protected Class[] getExtraProxyInterfaces(Remote impl)
	throws ExportException
    {
	if (impl == null) {
	    throw new NullPointerException("impl is null");
	}
	return new Class[]{RemoteMethodControl.class};
    }

    /**
     * Returns a new, modifiable collection of {@link Method} objects,
     * containing all remote methods for which the invocation
     * dispatcher should accept incoming remote calls.
     *
     * <p><code>AbstractILFactory</code> implements this method to return a
     * {@link Set} containing all of the methods of the interfaces obtained
     * by passing <code>impl</code> to the {@link #getRemoteInterfaces
     * getRemoteInterfaces} method and satisfying the following
     * requirements:
     *
     * <ul>
     * <li>No duplicate methods with the same signature and return
     * type are contained in the set returned.  If the interfaces contain
     * more than one method with the same signature and return type, the
     * method in the returned collection will be a member of the foremost
     * of the interfaces that contains a method with that signature and
     * return type.
     *
     * <li>For each interface, if a security manager exists, its
     * <code>checkPackageAccess</code> method is invoked with the package
     * name of the interface; this invocation may throw a
     * <code>SecurityException</code>.
     *
     * <li>For each interface, if the interface is non-public and a
     * security manager exists, the security manager's
     * <code>checkPermission</code> method is invoked with the permission
     * (@link ExportPermission} constructed with the string
     * "exportRemoteInterface." concatenated with the fully qualified
     * interface name; this invocation may throw a
     * <code>SecurityException</code>.  If the security check passes, each
     * <code>Method</code> object of the non-public interface has its
     * accessibility flag set to suppress language access checks.
     * </ul>
     *
     * <p>A subclass can override this method if it needs to control the
     * selection of the set of methods for the dispatcher to handle, or if
     * it needs to control the implementation of the collection returned.
     *
     * @param	impl the remote object
     * @return	the remote methods
     * @throws	NullPointerException if <code>impl</code> is <code>null</code>
     * @throws	ExportException if there is a problem obtaining the remote
     *		methods or if <code>impl</code> does not satisfy the
     *		requirements of this factory
     **/
    protected Collection getInvocationDispatcherMethods(Remote impl)
	throws ExportException
    {
	Class[] interfaces = getRemoteInterfaces(impl);
	Map methodMap = new HashMap();

	for (int i = interfaces.length-1; i >= 0; i--) {
	    Class intf = interfaces[i];
	    boolean nonpublic = checkNonPublicInterface(intf);
	    Util.checkPackageAccess(intf);
	    Method interfaceMethods[] = intf.getMethods();
	    for (int j = interfaceMethods.length-1; j >=0; j--) {
		final Method m = interfaceMethods[j];
		/*
		 * If the interface is non-public, set this Method object
		 * to override language access checks so that the
		 * dispatcher can invoke methods from non-public remote
		 * interfaces.  This is okay because the
		 * checkNonPublicInterface method checked a permission for
		 * this action.
		 */
		if (nonpublic) {
		    Security.doPrivileged(new PrivilegedAction() {
			public Object run() {
			    m.setAccessible(true);
			    return null;
			}
		    });
		}
		methodMap.put(Util.getMethodNameAndDescriptor(m), m);
	    }
	}
	
	Collection methods = new HashSet();
	methods.addAll(methodMap.values());
	return methods;
    }

    /**
     * Returns <code>true</code> if and only if the specified interface is
     * non-public.
     *
     * <p>If the interface is non-public and a security manager exists, the
     * security manager's <code>checkPermission</code> method is invoked
     * with the permission (@link ExportPermission} constructed with the
     * string "exportRemoteInterface." concatenated with the fully
     * qualified interface name; this invocation may throw a
     * <code>SecurityException</code>.
     *
     * @return <code>true</code> if the specified interface is non-public;
     * 		otherwise returns <code>false</code> 
     * @throws  SecurityException if the specified interface is non-public
     *		and the security check fails
     **/
    private static boolean checkNonPublicInterface(Class intf) {
	if (!Modifier.isPublic(intf.getModifiers())) {
	    SecurityManager sm = System.getSecurityManager();
	    if (sm != null) {
		String pString = "exportRemoteInterface." + intf.getName();
		sm.checkPermission(new ExportPermission(pString));
	    }
	    return true;
	}
	return false;
    }
    
   /**
     * Returns an invocation handler to use with a {@link Proxy} instance
     * implementing the specified interfaces, communicating with the
     * specified remote object using the specified object endpoint.
     *
     * <p>A subclass must override this method to create an
     * <code>InvocationHandler</code> for the specified interfaces, remote
     * object, and object endpoint.
     *
     * @param	interfaces an array of proxy interfaces
     * @param	impl a remote object this invocation handler
     *		is being created for
     * @param	oe an object endpoint used to communicate with
     *		the remote object
     * @return	the invocation handler for the remote object's proxy
     * @throws	ExportException if there is a problem creating the
     * 		invocation handler
     * @throws	NullPointerException if any argument is <code>null</code>,
     *		or if <code>interfaces</code> contains a <code>null</code>
     *		element
     **/
    protected abstract InvocationHandler
	createInvocationHandler(Class[] interfaces,
				Remote impl,
				ObjectEndpoint oe)
	throws ExportException;

    /**
     * Returns an invocation dispatcher to receive incoming remote calls
     * for the specified methods to the specified remote object, for a
     * server and transport with the specified capabilities.
     *
     * <p>A subclass must override this method to create an
     * <code>InvocationDispatcher</code> for the specified methods, remote
     * object, and server capabilities.
     *
     * @param	methods a collection of {@link Method} instances for the
     *		remote methods
     * @param	impl a remote object that the dispatcher is being created for
     * @param	caps the transport capabilities of the server
     * @return	the invocation dispatcher for the remote object
     * @throws	ExportException if there is a problem creating the
     *		dispatcher 
     * @throws	IllegalArgumentException if <code>methods</code> contains
     *		an element that is not a <code>Method</code> instance
     * @throws	NullPointerException if any argument is <code>null</code>,
     *		or if <code>methods</code> contains a <code>null</code> element
     **/
    protected abstract InvocationDispatcher
	createInvocationDispatcher(Collection methods,
				   Remote impl,
				   ServerCapabilities caps)
	throws ExportException;

 
    /**
     * {@inheritDoc}
     *
     * <p><code>AbstractILFactory</code> implements this method to return a
     * {@link Proxy} instance where:
     * <ul>
     *
     * <li>If the class loader specified during construction is not
     * <code>null</code>, the proxy's class is defined by the specified
     * loader.  Otherwise, if a security manager exists, its {@link
     * SecurityManager#checkPermission checkPermission} method is invoked
     * with the <code>{@link RuntimePermission}("getClassLoader")</code>
     * permission; this invocation may throw a
     * <code>SecurityException</code>.  If the above security check
     * succeeds, the proxy's class is defined by the class loader of
     * <code>impl</code>'s class.
     *
     * <li>The proxy implements the set of interfaces obtained by calling
     * the {@link #getProxyInterfaces getProxyInterfaces} method, passing
     * <code>impl</code> as the argument.  If a security manager exists,
     * for each interface returned from the <code>getProxyInterfaces</code>
     * method,  the security manager's {@link
     * SecurityManager#checkPackageAccess checkPackageAccess} method is
     * invoked with the package name of the interface. Such an invocation
     * may throw a <code>SecurityException</code>.
     *
     * <li>The invocation handler of the proxy instance is obtained by calling
     * the {@link #createInvocationHandler createInvocationHandler} method,
     * passing the proxy interfaces (as above), <code>impl</code>, and
     * <code>oe</code> as arguments. 
     * </ul>
     *
     * <p>The returned invocation dispatcher is obtained by calling the
     * {@link #createInvocationDispatcher createInvocationDispatcher}
     * method, passing a collection of methods, <code>impl</code>, and
     * <code>caps</code> as arguments. The collection of methods is
     * obtained by calling the {@link #getInvocationDispatcherMethods
     * getInvocationDispatcherMethods} method, passing <code>impl</code> as
     * the argument.
     *
     * @throws	NullPointerException {@inheritDoc}
     **/
    public Instances createInstances(Remote impl,
				     ObjectEndpoint oe,
				     ServerCapabilities caps)
    	throws ExportException
    {
	if (impl == null || oe == null || caps == null) {
	    throw new NullPointerException();
	}
 	Class[] interfaces = getProxyInterfaces(impl);
	InvocationHandler handler =
	    createInvocationHandler(interfaces, impl, oe);
	
	ClassLoader proxyLoader;
	if (loader != null) {
	    proxyLoader = loader;
	} else {
	    SecurityManager security = System.getSecurityManager();
	    if (security != null) {
		security.checkPermission(getClassLoaderPermission);
	    }
	    proxyLoader = impl.getClass().getClassLoader();
	}

	for (int i = 0; i < interfaces.length; i++) {
	    Util.checkPackageAccess(interfaces[i].getClass());
	}
	
	Remote proxy;
	try {
	    proxy = (Remote) Proxy.newProxyInstance(proxyLoader,
						    interfaces,
						    handler);
	} catch (IllegalArgumentException e) {
	    throw new ExportException("unable to create proxy", e);
	}
	InvocationDispatcher dispatcher =
	    createInvocationDispatcher(getInvocationDispatcherMethods(impl),
				       impl, caps);
	return new Instances(proxy, dispatcher);
    }
    
    /**
     * Returns a hash code value for this factory.
     **/
    public int hashCode() {
	return getClass().hashCode();
    }

    /**
     * Compares the specified object with this invocation layer factory for
     * equality.
     *
     * <p><code>AbstractILFactory</code> implements this method to return
     * <code>true</code> if and only if the specified object has the same
     * class as this object and the loader in the specified object is equal
     * to the loader in this object.
     *
     * <p>A subclass should override this method if it adds instance state
     * that affects equality.
     **/
    @Override
    public boolean equals(Object obj) {
	return (obj == this ||
		(obj != null &&
		 obj.getClass() == getClass() &&
		 ((AbstractILFactory) obj).loader == loader));
    }

    /**
     * Returns a string representation for this factory.
     **/
    public String toString() {
	String name = getClass().getName();
	return name.substring(name.lastIndexOf('.') + 1);
    }
}
