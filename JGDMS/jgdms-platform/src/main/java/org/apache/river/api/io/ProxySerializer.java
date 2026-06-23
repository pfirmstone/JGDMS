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
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.resource.Service;

/**
 *
 * @author peter
 */
@AtomicSerial
class ProxySerializer {

    private static final String BOOTSTRAP_PROXY = "bootstrapProxy";
    private static final String SERVICE_PROXY = "serviceProxy";

    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            new SerialForm(BOOTSTRAP_PROXY, CodebaseAccessor.class),
	    new SerialForm(SERVICE_PROXY, MarshalledInstance.class)
        };
    }
    
    public static void serialize(PutArg arg, ProxySerializer ps) throws IOException{
        arg.put(BOOTSTRAP_PROXY, ps.bootstrapProxy);
        arg.put(SERVICE_PROXY, ps.serviceProxy);
        arg.writeArgs();
    }
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
    private final /*transient*/ ClassLoader defaultLoader;
    private final /*transient*/ ClassLoader verifierLoader;
    
    ProxySerializer(CodebaseAccessor p, DynamicProxyCodebaseAccessor a, Collection context) throws IOException{
	this(p, new AtomicMarshalledInstance(a, context, false), null, null, null);
	
    }
    
    ProxySerializer(CodebaseAccessor p, ProxyAccessor a, Collection context) throws IOException{
	this(p, new AtomicMarshalledInstance(a, context, false), null, null, null);
	
    }
    
    ProxySerializer(CodebaseAccessor p, MarshalledInstance m, Collection context, ClassLoader defaultLoader, ClassLoader verifierLoader){
	bootstrapProxy = p;
	serviceProxy = m;
	this.context = context;
	this.defaultLoader = defaultLoader;
	this.verifierLoader = verifierLoader;
    }

    private ProxySerializer(CodebaseAccessor p, MarshalledInstance m, Collection context, ClassLoader[] loaders){
	this(p, m, context, loaders[0], loaders[1]);
    }

    private static CodebaseAccessor check(CodebaseAccessor c) throws InvalidObjectException{
	if (Proxy.isProxyClass(c.getClass())) return c;
	throw new InvalidObjectException(
	    "bootstrap proxy must be a dynamically generated instance of java.lang.reflect.Proxy");
    }
    
    ProxySerializer(GetArg arg) throws IOException, ClassNotFoundException{
	this(check(Valid.notNull(
		arg.get(BOOTSTRAP_PROXY, null, CodebaseAccessor.class),
		"bootstrapProxy cannot be null")),
	    Valid.notNull(
		    arg.get(SERVICE_PROXY, null, MarshalledInstance.class),
		    "serviceProxy cannot be null"),
	    arg.getObjectStreamContext(),
	    streamLoaders(arg)
	);
    }
    
    Object readResolve() throws IOException, ClassNotFoundException {
	return getProvider(defaultLoader).resolve(bootstrapProxy, serviceProxy, defaultLoader,
		verifierLoader, context);
    }

    /**
     * Extracts the unmarshalling stream's {default, verifier} class loaders from
     * the package-private {@link GetArgImpl}, under doPrivileged. Kept in this
     * trusted platform class -- and reached through GetArgImpl rather than
     * broadcast via getObjectStreamContext() -- because class loaders are
     * security-sensitive capabilities that must not be exposed to every object
     * in the graph. Returns {null, null} on any non-JOSS path (e.g. DER).
     */
    private static ClassLoader[] streamLoaders(GetArg arg){
	if (arg instanceof GetArgImpl){
	    final ObjectInput in = ((GetArgImpl) arg).in;
	    if (in instanceof MarshalInputStream){
		return AccessController.doPrivileged(new PrivilegedAction<ClassLoader[]>(){
		    public ClassLoader[] run(){
			MarshalInputStream mis = (MarshalInputStream) in;
			return new ClassLoader[]{ mis.getDefaultClassLoader(), mis.getVerifierClassLoader() };
		    }
		});
	    }
	}
	return new ClassLoader[]{ null, null };
    }
}
