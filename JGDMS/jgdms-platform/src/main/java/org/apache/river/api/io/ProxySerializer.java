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
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
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
 * @deprecated This is JOSS -- the Java-Serialization-coupled half of the
 *     {@code @AtomicSerial} marshalling path (Java object-stream wire format).
 *     It is being superseded by the DER marshalling path
 *     ({@code au.net.zeus.jgdms.der.object.DerProxySerializer}, {@code jgdms-der}
 *     module), which carries a downloadable proxy over ASN.1 DER rather than the
 *     Java Serialization object-stream protocol. Not yet scheduled for removal --
 *     the DER path is not fully load-bearing for every JOSS consumer yet -- but
 *     new code should target DER, not this.
 */
@Deprecated
@AtomicSerial
class ProxySerializer {

    private static final String BOOTSTRAP_PROXY = "bootstrapProxy";
    private static final String SERVICE_PROXY = "serviceProxy";
    /**
     * Wire field carrying the smart proxy's declared interface closure as
     * binary class NAMES (a {@code String[]}, never a {@code Class[]}). It is
     * the advance hint an isolated reading process uses to build its thin
     * {@link Proxy} stub -- via {@link Proxy#newProxyInstance} -- WITHOUT
     * unmarshalling or classloading the proxy's real implementation. Carried as
     * names, not classes, precisely so ordinary {@code @AtomicSerial} field
     * deserialization (which runs automatically, before any routing logic) never
     * resolves them through the platform's codebase-aware, download-capable
     * class-resolution path -- the very mechanism this isolation architecture
     * exists to keep out of the client process.
     */
    private static final String PROXY_INTERFACES = "proxyInterfaces";

    private static final String[] NO_INTERFACES = new String[0];

    /**
     * Decode-bounds cap on the NUMBER of interface names. Once this field
     * travels over the wire it is attacker-influenced content, so a hostile
     * few-byte encoding must not decode into an unbounded (or very large)
     * array. A genuine proxy interface closure -- the smart proxy's own business
     * interfaces plus every inherited super-interface -- is realistically a
     * handful to low double digits; 64 is a generous ceiling that still bounds
     * the decoded structure to a small fixed size. A list exceeding it is
     * REJECTED (not truncated): a truncated hint could silently drop a business
     * interface a legitimate sender meant a stub to expose.
     *
     * <p>NB: the DER path's equivalent bound
     * ({@code au.net.zeus.jgdms.der.object.ProxyWireSupport.MAX_PROXY_INTERFACES})
     * is 127 ({@code Byte.MAX_VALUE}, mirroring {@code AtomicMarshalInputStream}'s
     * bare-{@code [8]} proxy interface-count bound). This field caps at 64 rather
     * than 127 deliberately -- 64 is already far more interfaces than a real proxy
     * closure carries -- and the difference between the two paths is acceptable:
     * each is an independent, self-contained upper bound on attacker-influenced
     * wire content, not a shared wire contract that must agree.
     */
    private static final int MAX_PROXY_INTERFACES = 64;

    /**
     * Decode-bounds cap on the LENGTH (chars) of each interface name. Java
     * binary class names are ultimately bounded by the class-file
     * {@code CONSTANT_Utf8} limit (65535 bytes), but realistic fully-qualified
     * interface names -- even deeply nested or generated ones -- sit well under
     * 256 chars; 1024 gives generous headroom while still capping each element
     * so a single wire string cannot be huge. A name exceeding it is REJECTED
     * (not truncated): a truncated name is simply a different, meaningless name.
     */
    private static final int MAX_PROXY_INTERFACE_NAME_LENGTH = 1024;

    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            new SerialForm(BOOTSTRAP_PROXY, CodebaseAccessor.class),
	    new SerialForm(SERVICE_PROXY, MarshalledInstance.class),
	    new SerialForm(PROXY_INTERFACES, String[].class)
        };
    }

    public static void serialize(PutArg arg, ProxySerializer ps) throws IOException{
        arg.put(BOOTSTRAP_PROXY, ps.bootstrapProxy);
        arg.put(SERVICE_PROXY, ps.serviceProxy);
        arg.put(PROXY_INTERFACES, ps.proxyInterfaces);
        arg.writeArgs();
    }

    /**
     * Computes the full interface closure of {@code proxyClass} as binary class
     * NAMES -- the directly declared interfaces plus every inherited
     * super-interface, walked the same way {@link Proxy#newProxyInstance}
     * requires a complete interface list. Names only, never {@code Class}
     * objects: this hint must never itself force class resolution. A later
     * consumer (stub construction) resolves these names against its OWN local,
     * non-downloading classpath and silently drops whatever it cannot resolve
     * (dropping is always safe -- an unresolvable interface is one the process
     * cannot use). Order is deterministic (breadth-first, de-duplicated).
     */
    static String[] interfaceClosureNames(final Class proxyClass){
	if (proxyClass == null) return NO_INTERFACES;
	Set<String> names = new LinkedHashSet<String>();
	Deque<Class> queue = new ArrayDeque<Class>();
	for (Class i : proxyClass.getInterfaces()) queue.add(i);
	while (!queue.isEmpty()){
	    Class i = queue.poll();
	    if (names.add(i.getName())){ // first time seen -> expand its supers
		for (Class s : i.getInterfaces()) queue.add(s);
	    }
	}
	return names.isEmpty() ? NO_INTERFACES : names.toArray(new String[names.size()]);
    }

    /**
     * Validates the decoded {@link #PROXY_INTERFACES} hint against the decode
     * bounds and returns a defensive copy. Both the element count
     * ({@link #MAX_PROXY_INTERFACES}) and each element's length
     * ({@link #MAX_PROXY_INTERFACE_NAME_LENGTH}) are capped because this is
     * attacker-influenced wire content; a violation is REJECTED with
     * {@link InvalidObjectException}, never truncated. A {@code null}/empty
     * (or absent) field decodes cleanly as the empty "no advance hint" array.
     *
     * <p><b>Resolution boundary.</b> This method -- and this class -- only
     * bounds, copies and stores STRINGS. It never resolves a name to a
     * {@code Class}: no {@code Class.forName}, no
     * {@code ClassLoader.loadClass}, no reflection on the contents. T3 carries
     * names and returns exactly the names given to it, with no special-casing.
     * That is what makes the later privileged-interface gate meaningful: the
     * decision to bundle a privileged/administrative interface (e.g.
     * {@code au.net.zeus.jgdms.*.SubProcessAdministrable}) onto a stub happens
     * at resolution time in a separate consumer, never as a side effect of a
     * wire name merely matching here.
     */
    private static String[] boundInterfaceNames(String[] names) throws InvalidObjectException {
	if (names == null || names.length == 0) return NO_INTERFACES;
	if (names.length > MAX_PROXY_INTERFACES){
	    throw new InvalidObjectException(
		"proxyInterfaces hint carries " + names.length
		+ " names, exceeding the decode cap of " + MAX_PROXY_INTERFACES);
	}
	String[] copy = new String[names.length];
	for (int i = 0; i < names.length; i++){
	    String n = names[i];
	    if (n == null){
		throw new InvalidObjectException(
		    "proxyInterfaces hint contains a null name at index " + i);
	    }
	    if (n.length() > MAX_PROXY_INTERFACE_NAME_LENGTH){
		throw new InvalidObjectException(
		    "proxyInterfaces hint name at index " + i + " has length "
		    + n.length() + ", exceeding the decode cap of "
		    + MAX_PROXY_INTERFACE_NAME_LENGTH);
	    }
	    copy[i] = n;
	}
	return copy;
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
	    // Interface closure computed from the LIVE sender-side proxy class,
	    // before the proxy is wrapped into the lazy serviceProxy below.
	    return new ProxySerializer(
		(CodebaseAccessor) Proxy.newProxyInstance(getProxyLoader(proxyClass),
		    BOOTSTRAP_PROXY_INTERFACES,
		    h
		),
		proxy,
		interfaceClosureNames(proxyClass),
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
	    // Interface closure computed from the LIVE sender-side proxy class,
	    // before the proxy is wrapped into the lazy serviceProxy below.
	    return new ProxySerializer(
		(CodebaseAccessor) Proxy.newProxyInstance(getProxyLoader(proxyClass),
		    BOOTSTRAP_PROXY_INTERFACES,
		    h
		),
		svc,
		interfaceClosureNames(proxyClass),
		context
	    );

	}
	return svc;
    }
    
    private final CodebaseAccessor bootstrapProxy;
    /**
     * The smart proxy's declared interface closure, as binary class NAMES.
     * EAGER -- unlike {@link #serviceProxy}, which stays lazy/unresolved until
     * {@link #readResolve()} -- so routing/stub-construction logic can read the
     * hint before any decision about {@code serviceProxy} is made. Never
     * {@code null} (empty means "no advance hint"); never resolved by this
     * class. See {@link #interfaceClosureNames} and {@link #boundInterfaceNames}.
     */
    private final String[] proxyInterfaces;
    private final MarshalledInstance serviceProxy;
    private final /*transient*/ Collection context;
    private final /*transient*/ ClassLoader defaultLoader;
    private final /*transient*/ ClassLoader verifierLoader;

    ProxySerializer(CodebaseAccessor p, DynamicProxyCodebaseAccessor a, String[] proxyInterfaces, Collection context) throws IOException{
	this(p, new AtomicMarshalledInstance(a, context, false), proxyInterfaces, null, null, null);

    }

    ProxySerializer(CodebaseAccessor p, ProxyAccessor a, String[] proxyInterfaces, Collection context) throws IOException{
	this(p, new AtomicMarshalledInstance(a, context, false), proxyInterfaces, null, null, null);

    }

    ProxySerializer(CodebaseAccessor p, MarshalledInstance m, String[] proxyInterfaces, Collection context, ClassLoader defaultLoader, ClassLoader verifierLoader){
	bootstrapProxy = p;
	serviceProxy = m;
	this.proxyInterfaces = (proxyInterfaces != null) ? proxyInterfaces : NO_INTERFACES;
	this.context = context;
	this.defaultLoader = defaultLoader;
	this.verifierLoader = verifierLoader;
    }

    private ProxySerializer(CodebaseAccessor p, MarshalledInstance m, String[] proxyInterfaces, Collection context, ClassLoader[] loaders){
	this(p, m, proxyInterfaces, context, loaders[0], loaders[1]);
    }

    /**
     * The smart proxy's declared interface closure as binary class NAMES
     * (defensive copy). Consumed by stub-construction logic to pick which of
     * the proxy's OWN business interfaces a reading process's stub exposes;
     * these names are never resolved by {@code ProxySerializer} itself.
     */
    String[] proxyInterfaces(){
	return (proxyInterfaces.length == 0) ? NO_INTERFACES : proxyInterfaces.clone();
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
	    // Decode-bounded: this hint is attacker-influenced wire content. An
	    // absent field (older stream) decodes to the empty default. The names
	    // are only bounded/copied here -- never resolved to Classes.
	    boundInterfaceNames(arg.get(PROXY_INTERFACES, NO_INTERFACES, String[].class)),
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
