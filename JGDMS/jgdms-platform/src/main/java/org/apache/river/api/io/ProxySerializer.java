/*
 * Copyright 2018 The Apache Software Foundation.
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
package org.apache.river.api.io;

import net.jini.loader.ProxyCodebaseSpi;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInput;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.security.AccessController;
import java.security.Guard;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.ProxyAccessor;
import net.jini.export.CodebaseAccessor;
import net.jini.export.DynamicProxyCodebaseAccessor;
import net.jini.io.MarshalInputStream;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.ReadObject;
import org.apache.river.resource.Service;

/**
 *
 * @author peter
 */
@AtomicSerial
class ProxySerializer implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * By defining serial persistent fields, we don't need to use transient fields.
     * All fields can be final and this object becomes immutable.
     */
    private static final ObjectStreamField[] serialPersistentFields = 
	{
	    new ObjectStreamField("bootstrapProxy", CodebaseAccessor.class),
	    new ObjectStreamField("serviceProxy", MarshalledInstance.class)
	};
    /**
     * The bootstrap proxy must be limited to the following interfaces, in case
     * additional interfaces implemented by the proxy aren't available remotely.
     */
    private static final Class[] BOOTSTRAP_PROXY_INTERFACES = 
	{
	    CodebaseAccessor.class,
	    RemoteMethodControl.class
	};
    
    private static final Logger LOGGER = Logger.getLogger("org.apache.river.api.io");
    
    
    private static final Guard CLASSLOADER_GUARD = new RuntimePermission("getClassLoader");
    /**
     * Returns the class loader for the specified proxy class.
     */
    private static ClassLoader getProxyLoader(final Class proxyClass) {
	return (ClassLoader)
	    AccessController.doPrivileged(new PrivilegedAction() {
		public Object run() {
		    return proxyClass.getClassLoader();
		}
	    });
    }
    
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
	if (result != null) return result;
	// By default, if no provider is available, doesn't attempt to
	// download codebase or substitute.
	return new ProxyCodebaseSpi(){

	    public Object resolve(
		    CodebaseAccessor bootstrapProxy,
		    MarshalledInstance smartProxy,
		    ClassLoader parentLoader, 
		    ClassLoader verifierLoader, 
		    Collection context) throws IOException, ClassNotFoundException 
	    {
		return smartProxy.get(parentLoader, true, verifierLoader, context);
	    }

	    public boolean substitute(
		    Class serviceClass, 
		    ClassLoader streamLoader) 
	    {
		return false;
	    }
	    
	};
    }
    
    public static Object create(DynamicProxyCodebaseAccessor proxy, ClassLoader streamLoader, Collection context) throws IOException {
	Class proxyClass = proxy.getClass();
	if (proxy instanceof RemoteMethodControl //JERI
	    && Proxy.isProxyClass(proxyClass)
	    && getProvider(streamLoader).substitute(proxyClass, streamLoader)
	    ) 
	{
	    // REMIND: InvocationHandler must be available locally, for now
	    // it must be an instance of BasicInvocationHandler.
	    InvocationHandler h = Proxy.getInvocationHandler(proxy);
	    return new ProxySerializer(
		(CodebaseAccessor) Proxy.newProxyInstance(getProxyLoader(proxyClass),
		    BOOTSTRAP_PROXY_INTERFACES,
		    h
		), 
		proxy,
		context
	    );
	    
	} 
	return proxy;
    }
    
    public static Object create(ProxyAccessor svc, ClassLoader streamLoader, Collection context) throws IOException{
	Object proxy = svc.getProxy();	
	Class proxyClass = proxy != null ? proxy.getClass() : null;
	if (proxyClass == null ) LOGGER.log(Level.FINE, "Warning Proxy was null for {0}", svc.getClass());
	if (proxy instanceof RemoteMethodControl //JERI
	    && proxy instanceof CodebaseAccessor
	    && getProvider(streamLoader).substitute(proxyClass, streamLoader)
	    ) 
	{
	    // REMIND: InvocationHandler must be available locally, for now
	    // it must be an instance of BasicInvocationHandler.
	    InvocationHandler h = Proxy.getInvocationHandler(proxy); // throws IllegalArgumentException if not a proxy.
	    return new ProxySerializer(
		(CodebaseAccessor) Proxy.newProxyInstance(getProxyLoader(proxyClass),
		    BOOTSTRAP_PROXY_INTERFACES,
		    h
		), 
		svc,
		context
	    );
	    
	} 
	return svc;
    }
    
    private final CodebaseAccessor bootstrapProxy;
    private final MarshalledInstance serviceProxy;
    private final /*transient*/ Collection context;
    private final /*transient*/ RO read;
    
    ProxySerializer(CodebaseAccessor p, DynamicProxyCodebaseAccessor a, Collection context) throws IOException{
	this(p, new AtomicMarshalledInstance(a, context, false), null, null);
	
    }
    
    ProxySerializer(CodebaseAccessor p, ProxyAccessor a, Collection context) throws IOException{
	this(p, new AtomicMarshalledInstance(a, context, false), null, null);
	
    }
    
    ProxySerializer(CodebaseAccessor p, MarshalledInstance m, Collection context, RO read){
	bootstrapProxy = p;
	serviceProxy = m;
	this.context = context;
	this.read = read;
    }

    private static CodebaseAccessor check(CodebaseAccessor c) throws InvalidObjectException{
	if (Proxy.isProxyClass(c.getClass())) return c;
	throw new InvalidObjectException(
	    "bootstrap proxy must be a dynamically generated instance of java.lang.reflect.Proxy");
    }
    
    ProxySerializer(GetArg arg) throws IOException, ClassNotFoundException{
	this(check(Valid.notNull(
		arg.get("bootstrapProxy", null, CodebaseAccessor.class),
		"bootstrapProxy cannot be null")),
	    Valid.notNull(
		    arg.get("serviceProxy", null, MarshalledInstance.class),
		    "serviceProxy cannot be null"),
	    arg.getObjectStreamContext(),
	    (RO) arg.getReader()
	);
    }
    
    Object readResolve() throws IOException, ClassNotFoundException {
	return getProvider(read.defaultLoader).resolve(bootstrapProxy, serviceProxy, read.defaultLoader,
		read.verifierLoader, context);
    }
    
    // So we can implement ReadObject
    private void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();
    }
   
    @AtomicSerial.ReadInput
    static ReadObject getReader(){
	return new RO();
    }
    
    private static class RO implements ReadObject {
	
	private ClassLoader defaultLoader = null;
	private ClassLoader verifierLoader = null;

	public void read(final ObjectInput input) throws IOException, ClassNotFoundException {
	    if (input instanceof MarshalInputStream){
		defaultLoader = AccessController.doPrivileged(new PrivilegedAction<ClassLoader>(){
		    public ClassLoader run() {
			return ((MarshalInputStream) input).getDefaultClassLoader();
		    }
		});
		verifierLoader = AccessController.doPrivileged(new PrivilegedAction<ClassLoader>(){
		    public ClassLoader run() {
			return ((MarshalInputStream) input).getVerifierClassLoader();
		    }
		});
	    }
	}
	
    }
}
