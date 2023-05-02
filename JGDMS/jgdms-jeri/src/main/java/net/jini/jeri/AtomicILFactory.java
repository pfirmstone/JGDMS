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
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.rmi.server.ExportException;
import java.security.AccessController;
import java.security.Guard;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Iterator;
import net.jini.core.constraint.MethodConstraints;
import net.jini.export.CodebaseAccessor;
import net.jini.loader.ProxyCodebaseSpi;
import org.apache.river.resource.Service;

/**
 * <p>
 * An atomic serialization implementation of an {@link InvocationLayerFactory}.  
 * This factory is used to create a {@link java.lang.reflect.Proxy} instance with a {@link
 * AtomicInvocationHandler} and to create a {@link
 * AtomicInvocationDispatcher} for a remote object being exported.  This
 * factory is used in conjunction with the {@link BasicJeriExporter} class.
 * </p><p>
 * Serialization is performed with atomic input validation.  Class codebase
 * annotations are not appended in the stream by default, each endpoint
 * must have class visibility determined a default ClassLoader.  The
 * proxy's AtomicInvocationHandler ClassLoader will be that of the codebase
 * determined by {@link net.jini.export.CodebaseAccessor}, proxy's
 * are serialized independently and do not share state.
 * </p><p>
 * Refer to {@link org.apache.river.api.io} for Atomic
 * Serialization, any service or object exported using this factory must
 * implement {@link org.apache.river.api.io.AtomicSerial}.
 * </p><p>
 * If a service or object exported by this service uses a smart proxy,
 * that smart proxy must implement {@link net.jini.export.ProxyAccessor} and
 * the service or exported object must implement {@link net.jini.export.CodebaseAccessor},
 * allowing a client de-serializing the service proxy, to authenticate
 * the service, provision its codebase and apply any constraints.
 * </p><p>
 * If a service or object exported by this service doesn't utilise a smart proxy,
 * but still needs to a codebase to resolve certain interface classes, then
 * that service or object must implement {@link net.jini.export.DynamicProxyCodebaseAccessor}.
 * </p><p>
 * Codebases are provisioned using {@link net.jini.loader.ProxyCodebaseSpi}
 * </p>
 * 
 * @author  Peter.
 * @since 3.1
 **/
public class AtomicILFactory extends BasicILFactory {
    
    private final boolean useAnnotations;
    private final Compression compression;
    
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
     * @param	loader the class loader
     * @throws	IllegalArgumentException if the permission class is
     *		abstract, is not a subclass of {@link java.security.Permission}, or does
     *		not have a public constructor that has either one
     *		<code>String</code> parameter or one {@link java.lang.reflect.Method}
     *		parameter and has no declared exceptions
     * @throws NullPointerException if loader is null.
     **/
    public AtomicILFactory(MethodConstraints serverConstraints,
			  Class permissionClass,
			  ClassLoader loader)
    {
	this(serverConstraints, permissionClass, loader, false, Compression.NONE);
    }
    
     /**
     * Creates a <code>AtomicILFactory</code> with the specified server
     * constraints, permission class, and class loader.The server
     * constraints, if not <code>null</code>, are used to enforce minimum
     * constraints for remote calls.  The permission class, if not
     * <code>null</code>, is used to perform server-side access control on
     * incoming remote calls.  The class loader, which may be
     * <code>null</code>, is passed to the superclass constructor and is
     * used by the {@link #createInstances createInstances}
     * method.
     *
     * @param	serverConstraints the server constraints, or <code>null</code>
     * @param	permissionClass the permission class, or <code>null</code>
     * @param	loader the class loader
     * @param   compress the type of compression to use or none.
     * @throws	IllegalArgumentException if the permission class is
     *		abstract, is not a subclass of {@link java.security.Permission}, or does
     *		not have a public constructor that has either one
     *		<code>String</code> parameter or one {@link java.lang.reflect.Method}
     *		parameter and has no declared exceptions
     * @throws NullPointerException if loader is null.
     **/
    public AtomicILFactory(MethodConstraints serverConstraints,
			  Class permissionClass,
			  ClassLoader loader,
                          Compression compress)
    {
	this(serverConstraints, permissionClass, loader, false, compress);
    }
    
    private AtomicILFactory(MethodConstraints serverConstraints,
			    Class permissionClass,
			    ClassLoader loader,
			    boolean useAnnotations,
                            Compression compress)
    {
	super(serverConstraints, permissionClass, notNull(loader));
	this.useAnnotations = useAnnotations;
        this.compression = compress;
    }
    
    private static <T> T notNull(T object) throws NullPointerException{
	if (object == null) throw new NullPointerException();
	return object;
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
     *		implementation or the class of the service interface 
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
     *		implementation or the class of the service interface 
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
     * Creates a <code>AtomicILFactory</code> with the specified server
     * constraints, permission class, and proxy or service implementation class.
     * The server constraints, if not <code>null</code>, are used to enforce 
     * minimum constraints for remote calls.
     * The permission class, if not <code>null</code>, is used to perform 
     * server-side access control on incoming remote calls.  
     * The proxy or service implementation class, which cannot be 
     * <code>null</code>, is used to obtain the ClassLoader
     * to be passed to the superclass constructor and is used by the 
     * {@link #createInstances createInstances} method.
     *
     * @param	serverConstraints the server constraints, or <code>null</code>
     * @param	permissionClass the permission class, or <code>null</code>
     * @param	proxyOrServiceImplClass the class of the smart proxy 
     *		implementation or the class of the service interface 
     *		for dynamic proxy's.
     * @param   compress the compression format or none.
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
			    Class proxyOrServiceImplClass,
                            Compression compress)
    {
	this(serverConstraints, permissionClass, proxyOrServiceImplClass.getClassLoader(), compress);
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
     * This constructor is deprecated due to the problems that occur when attempting
     * to resolve classes using codebase annotations appended to the stream.
     * 
     * <a href="https://dl.acm.org/doi/pdf/10.5555/1698139">
     * Class Loading Issues in Java™ RMI and Jini™ Network Technology</a>
     * 
     * Appending codebase annotations in the stream is strongly discouraged.
     *
     * @param	serverConstraints the server constraints, or <code>null</code>
     * @param	permissionClass the permission class, or <code>null</code>
     * @param	proxyOrServiceImplClass the class of the smart proxy 
     *		implementation or the class of the service interface 
     *		for dynamic proxy's, the ClassLoader of this class determines
     *		class visibility and resolution for deserialized objects.
     * @param useAnnotations if true, write codebase annotations to
     *		the stream.  If the service or remote object, accepts parameter
     *		classes that are not part of the Service API and not resolved
     *		by the smart proxy or Remote Object stub's ClassLoader, then if
     *		useAnnotations is true the stream will be annotated with a 
     *		codebase, from which additional classes can be resolved.
     *		If useAnnotations is true, care should be taken to ensure
     *		downloaded codebases are trusted, such as by utilizing
     *		net.jini.loader.pref.RequireDlPermProvider. 
     *		See {@link net.jini.loader.ClassLoading ClassLoading} for details.
     *		It is advisable to sign the codebase or utilize
     *		{@link net.jini.core.constraint.Integrity Integrrity} constraints.
     *		
     * @throws	IllegalArgumentException if the permission class is
     *		abstract, is not a subclass of {@link java.security.Permission}, or does
     *		not have a public constructor that has either one
     *		<code>String</code> parameter or one {@link java.lang.reflect.Method}
     *		parameter and has no declared exceptions
     * @throws SecurityException if caller doesn't have {@link RuntimePermission} 
     *		"getClassLoader".
     * @throws NullPointerException if proxyorServiceImplClass is null.
     **/
    @Deprecated
    public AtomicILFactory(MethodConstraints serverConstraints,
			    Class permissionClass,
			    Class proxyOrServiceImplClass,
			    boolean useAnnotations)
			    
    {
	this(serverConstraints, permissionClass, proxyOrServiceImplClass.getClassLoader(), useAnnotations, Compression.NONE);
    }
    
    /**
     * Creates a <code>AtomicILFactory</code> with the specified server
     * constraints, permission class, and proxy or service implementation class.
     * The server constraints, if not <code>null</code>, are used to enforce 
     * minimum constraints for remote calls.
     * The permission class, if not <code>null</code>, is used to perform
     * server-side access control on incoming remote calls.  
     * The proxy or service implementation class, 
     * which cannot be <code>null</code>, is used to obtain the ClassLoader
     * to be passed to the superclass constructor and is used by the 
     * {@link #createInstances createInstances} method.
     * 
     * This constructor is deprecated due to the problems that occur when attempting
     * to resolve classes using codebase annotations appended to the stream.
     * 
     * <a href="https://dl.acm.org/doi/pdf/10.5555/1698139">
     * Class Loading Issues in Java™ RMI and Jini™ Network Technology</a>
     * 
     * Appending codebase annotations in the stream is strongly discouraged.
     *
     * @param	serverConstraints the server constraints, or <code>null</code>
     * @param	permissionClass the permission class, or <code>null</code>
     * @param	proxyOrServiceImplClass the class of the smart proxy 
     *		implementation or the class of the service interface 
     *		for dynamic proxy's, the ClassLoader of this class determines
     *		class visibility and resolution for deserialized objects.
     * @param useAnnotations if true, write codebase annotations to
     *		the stream.  If the service or remote object, accepts parameter
     *		classes that are not part of the Service API and not resolved
     *		by the smart proxy or Remote Object stub's ClassLoader, then if
     *		useAnnotations is true the stream will be annotated with a 
     *		codebase, from which additional classes can be resolved.
     *		If useAnnotations is true, care should be taken to ensure
     *		downloaded codebases are trusted, such as by utilizing
     *		net.jini.loader.pref.RequireDlPermProvider. 
     *		See {@link net.jini.loader.ClassLoading ClassLoading} for details.
     *		It is advisable to sign the codebase or utilize
     *		{@link net.jini.core.constraint.Integrity Integrrity} constraints.
     * @param compress compression algorithm to use.
     *		
     * @throws	IllegalArgumentException if the permission class is
     *		abstract, is not a subclass of {@link java.security.Permission}, or does
     *		not have a public constructor that has either one
     *		<code>String</code> parameter or one {@link java.lang.reflect.Method}
     *		parameter and has no declared exceptions
     * @throws SecurityException if caller doesn't have {@link RuntimePermission} 
     *		"getClassLoader".
     * @throws NullPointerException if proxyorServiceImplClass is null.
     **/
    @Deprecated
    public AtomicILFactory(MethodConstraints serverConstraints,
			    Class permissionClass,
			    Class proxyOrServiceImplClass,
			    boolean useAnnotations,
                            Compression compress)
			    
    {
	this(serverConstraints, permissionClass, proxyOrServiceImplClass.getClassLoader(), useAnnotations, compress);
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
	return new AtomicInvocationHandler(oe, getServerConstraints(), useAnnotations, compression);
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
					     getClassLoader(), 
					     useAnnotations,
                                             compression);
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
    
    @Override
    public Instances createInstances(Remote impl,
				     ObjectEndpoint oe,
				     ServerCapabilities caps)
    	throws ExportException 
    {
        Instances inst = super.createInstances(impl, oe, caps);
        Remote proxy = inst.getProxy();
        if (proxy instanceof CodebaseAccessor){
            InvocationHandler handler = Proxy.getInvocationHandler(proxy);
            ProxyCodebaseSpi provider = getProvider(getClassLoader());
            if (provider != null){
                provider.record((CodebaseAccessor) impl, handler, getClassLoader());
            }
        }
        return inst;
    }
    
    private static final Guard CLASSLOADER_GUARD = new RuntimePermission("getClassLoader");
    
    private static ProxyCodebaseSpi getProvider(final ClassLoader loader){
	ProxyCodebaseSpi result =
	    AccessController.doPrivileged(new PrivilegedAction<ProxyCodebaseSpi>(){
		public ProxyCodebaseSpi run(){
		    Iterator<ProxyCodebaseSpi> spit = 
			    Service.providers(
				ProxyCodebaseSpi.class, 
				loader
			    );
		    CLASSLOADER_GUARD.checkGuard(null);
		    while (spit.hasNext()){
			return spit.next();
		    }
		    return null;
		}
	    }
	);
	return result;
    }
}
