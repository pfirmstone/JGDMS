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

package com.sun.jini.jeri.internal.runtime;

import com.sun.jini.collection.WeakIdentityMap;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.ref.SoftReference;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.rmi.MarshalledObject;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationID;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.WeakHashMap;
import java.net.InetAddress;
import java.rmi.server.ServerNotActiveException;
import javax.security.auth.Subject;
import net.jini.export.ServerContext;
import net.jini.io.context.ClientHost;
import net.jini.io.context.ClientSubject;
import net.jini.io.context.ContextPermission;
import net.jini.io.context.IntegrityEnforcement;
import net.jini.security.proxytrust.TrustEquivalence;

/**
 * Utility methods for implementing custom remote reference types.
 *
 * @author Sun Microsystems, Inc.
 * 
 */
public class Util {
    
    /** cache of tables mapping methods to hashes */
    private static TableCache methodToHash_TableCache = new TableCache(true);
    
    /** cache of valid proxy remote methods */
    private static Map proxyRemoteMethodCache = new WeakHashMap();

    /** parameter types for activatable constructor or activate method */
    private static Class[] paramTypes = {
	ActivationID.class, MarshalledObject.class
    };

    /** name of the resource containing prohibited proxy interfaces */
    private static final String prohibitedProxyInterfacesResource =
	"com/sun/jini/proxy/resources/" +
	"InvocationHandler.moreProhibitedProxyInterfaces";

    /** names of interfaces that proxies are prohibited from implementing */
    private static final Collection prohibitedProxyInterfaces =
	getProhibitedProxyInterfaces();

    /**
     * Appends the current thread's stack trace to the stack trace of the
     * given exception.
     *
     * This method is used for exceptions that have been unmarshalled as the
     * exceptional result of a remote method invocation, so that the
     * client-side stack trace gets recorded in the exception (while
     * preserving the server-side stack trace in the exception as well).
     *
     * Note that the (somewhat odd) names of this method and the method that
     * this one internally delegates to are significant because these methods
     * visually highlight the remote call boundary between the server-side
     * and the client-side portions to readers of the combined stack trace.
     */
    public static void exceptionReceivedFromServer(Throwable t) {
	__________EXCEPTION_RECEIVED_FROM_SERVER__________(t);
    }

    private static void __________EXCEPTION_RECEIVED_FROM_SERVER__________(
	Throwable t)
    {
	StackTraceElement[] serverTrace = t.getStackTrace();
	StackTraceElement[] clientTrace = (new Throwable()).getStackTrace();
	StackTraceElement[] combinedTrace =
	    new StackTraceElement[serverTrace.length + clientTrace.length];
	System.arraycopy(serverTrace, 0, combinedTrace, 0,
			 serverTrace.length);
	System.arraycopy(clientTrace, 0, combinedTrace, serverTrace.length,
			 clientTrace.length);
	t.setStackTrace(combinedTrace);
    }

    /**
     * Clear the stack trace of the given exception by replacing it with
     * an empty StackTraceElement array, and do the same for all of its
     * chained causative exceptions.
     *
     * This method is used when it is desired for the stack trace data of
     * an exception to be suppressed before the exception gets marshalled
     * to a remote virtual machine, perhaps for reasons of confidentiality
     * or performance.
     */
    public static void clearStackTraces(Throwable t) {
	StackTraceElement[] empty = new StackTraceElement[0];
	while (t != null) {
	    t.setStackTrace(empty);
	    t = t.getCause();
	}
    }

    /**
     * Marshals <code>value</code> to an <code>ObjectOutput</code> stream,
     * <code>out</code>, using RMI's serialization format for arguments or
     * return values.  For primitive types, the primitive type's class should
     * be specified (i.e., for the type <code>int</code>, specify
     * <code>int.class</code>), and the primitive value should be wrapped in
     * instances of the appropriate wrapper class, such as
     * <code>java.lang.Integer</code> or <code>java.lang.Boolean</code>.
     *
     * @param	type <code>Class</code> object for the value to be marshalled
     * @param	value value to marshal
     * @param	out stream to which the value is marshalled
     * @throws	IOException if an I/O error occurs marshalling
     *		the value to the output stream
     */
    public static void marshalValue(Class type, Object value, ObjectOutput out)
	throws IOException
    {
	if (type.isPrimitive()) {
	    if (type == int.class) {
		out.writeInt(((Integer) value).intValue());
	    } else if (type == boolean.class) {
		out.writeBoolean(((Boolean) value).booleanValue());
	    } else if (type == byte.class) {
		out.writeByte(((Byte) value).byteValue());
	    } else if (type == char.class) {
		out.writeChar(((Character) value).charValue());
	    } else if (type == short.class) {
		out.writeShort(((Short) value).shortValue());
	    } else if (type == long.class) {
		out.writeLong(((Long) value).longValue());
	    } else if (type == float.class) {
		out.writeFloat(((Float) value).floatValue());
	    } else if (type == double.class) {
		out.writeDouble(((Double) value).doubleValue());
	    } else {
		throw new AssertionError(
		    "Unrecognized primitive type: " + type);
	    }
	} else {
	    out.writeObject(value);
	}
    }

    /**
     * Unmarshals a value of the specified <code>type</code> from the
     * <code>ObjectInput</code> stream, <code>in</code>, using RMI's
     * serialization format for arguments or return values and returns the
     * result.  For primitive types, the primitive type's class should be
     * specified (i.e., for the primitive type <code>int</code>, specify
     * <code>int.class</code>).
     *
     * @param	type <code>Class</code> object for the value to be unmarshalled
     * @param	in stream from which the value is unmarshalled
     * @return	value unmarshalled from the input stream
     * @throws	IOException if an I/O error occurs marshalling
     *		the value to the output stream
     * @throws	ClassNotFoundException if the <code>type</code>'s
     *		class could not	be found
     */
    public static Object unmarshalValue(Class type, ObjectInput in)
	throws IOException, ClassNotFoundException
    {
	if (type.isPrimitive()) {
	    if (type == int.class) {
		return new Integer(in.readInt());
	    } else if (type == boolean.class) {
		return Boolean.valueOf(in.readBoolean());
	    } else if (type == byte.class) {
		return new Byte(in.readByte());
	    } else if (type == char.class) {
		return new Character(in.readChar());
	    } else if (type == short.class) {
		return new Short(in.readShort());
	    } else if (type == long.class) {
		return new Long(in.readLong());
	    } else if (type == float.class) {
		return new Float(in.readFloat());
	    } else if (type == double.class) {
		return new Double(in.readDouble());
	    } else {
		throw new AssertionError(
		    "Unrecognized primitive type: " + type);
	    }
	} else {
	    return in.readObject();
	}
    }

    /**
     * Computes the "method hash" of a remote method, <code>m</code>.  The
     * method hash is a <code>long</code> containing the first 64 bits of the
     * SHA digest from the UTF encoded string of the method name followed by
     * its "method descriptor".  See section 4.3.3 of The Java(TM) Virtual
     * Machine Specification for the definition of a "method descriptor".
     *
     * @param	m remote method
     * @return	the method hash
     */
    private static long computeMethodHash(Method m) {
	long hash = 0;
	ByteArrayOutputStream sink = new ByteArrayOutputStream(127);
	try {
	    MessageDigest md = MessageDigest.getInstance("SHA");
	    DataOutputStream out = new DataOutputStream(
		new DigestOutputStream(sink, md));

	    String s = getMethodNameAndDescriptor(m);
	    out.writeUTF(s);

	    // use only the first 64 bits of the digest for the hash
	    out.flush();
	    byte hasharray[] = md.digest();
	    for (int i = 0; i < Math.min(8, hasharray.length); i++) {
		hash += ((long) (hasharray[i] & 0xFF)) << (i * 8);
	    }
	} catch (IOException ignore) {
	    /* can't happen, but be deterministic anyway. */
	    hash = -1;
	} catch (NoSuchAlgorithmException complain) {
	    throw new SecurityException(complain.getMessage());
	}
	return hash;
    }

    /**
     * Returns the method hash for the method <code>m</code>.  Subsequent
     * calls to <code>getMethodHash</code> passing the same method argument
     * should be faster since this method caches internally the result of the
     * method to method hash mapping.  The method hash is calculated using the
     * <code>computeMethodHash</code> method.
     *
     * @param	m the remote method
     * @return	the method hash for the method <code>m</code>
     */
    public static long getMethodHash(Method m) {
	Map table = methodToHash_TableCache.getTable(m.getDeclaringClass());
	Long hash = (Long) table.get(m);
	return hash.longValue();
    }

    private static class TableCache extends WeakHashMap {
	/**
	 * if true, the tables map methods to method hashes; if false,
	 * the tables map method hashes to methods
	 */
	private boolean mapsMethodToHash;

	public TableCache(boolean mapsMethodToHash) {
	    super();
	    this.mapsMethodToHash = mapsMethodToHash;
	}
	
	public Map getTable(Class remoteClass) {
	    SoftReference[] tableRef;

	    /*
	     * Method tables for remote classes are cached in a hash table
	     * using weak references to hold the Class object keys, so that
	     * the cache does not prevent the class from being unloaded, and
	     * using soft references to hold the values, so that the computed
	     * method tables will generally persist while no objects of the
	     * remote class are exported, but their storage may be reclaimed
	     * if necessary, and accidental reachability of the remote class
	     * through its interfaces is avoided.
	     */
	    synchronized (this) {
		/*
		 * Look up class in cache; add entry if not found.
		 */
		tableRef = (SoftReference[]) get(remoteClass);
		if (tableRef == null) {
		    tableRef = new SoftReference[] { null };
		    put(remoteClass, tableRef);
		}
	    }

	    /*
	     * Check cached reference to method table for this class;
	     * if it is null, go and create the table.
	     */
	    synchronized (tableRef) {
		Map table = null;
		if (tableRef[0] != null) {
		    table = (Map) tableRef[0].get();
		}
		if (table == null) {
		    if (mapsMethodToHash) {
			/*
			 * REMIND: if we hand out this table directly, via a
			 * public API, we need to make this map "unmodifiable".
			 */
			table = new LazyMethodToHash_Map();
		    } else {
			throw new UnsupportedOperationException();
		    }
		    tableRef[0] = new SoftReference(table);
		}
		return table;
	    }
	}
    }


    /**
     * Verifies that the supplied method has at least one declared exception
     * type that is RemoteException or one of its superclasses.  If not,
     * then this method throws IllegalArgumentException.
     *
     * @throws IllegalArgumentException if m is an illegal remote method
     */
    private static void checkMethod(Method m) {
	Class[] ex = m.getExceptionTypes();
	for (int i = 0; i < ex.length; i++) {
	    if (ex[i].isAssignableFrom(RemoteException.class))
		return;
	}
	throw new IllegalArgumentException(
	    "illegal remote method encountered: " + m);
    }
    
    private static class LazyMethodToHash_Map extends WeakHashMap {

	public LazyMethodToHash_Map() {
	    super();
	}

	public synchronized Object get(Object key) {
	    Object hash = super.get(key);
	    if (hash == null) {
		Method method = (Method) key;
		hash = new Long(computeMethodHash(method));
		put(method, hash);
	    }
	    return (Long) hash;
	}
    }
	    
    /*
     * The following static methods are related to the creation of the
     * "method table" for a remote class, which maps method hashes to
     * the appropriate Method objects for the class's remote methods.
     */

    /**
     * Returns a string consisting of the given method's name followed by
     * its "method descriptor", as appropriate for use in the computation
     * of the "method hash".
     *
     * See section 4.3.3 of The Java(TM) Virtual Machine Specification for
     * the definition of a "method descriptor".
     */
    public static String getMethodNameAndDescriptor(Method m) {
	StringBuffer desc = new StringBuffer(m.getName());
	desc.append('(');
	Class[] paramTypes = m.getParameterTypes();
	for (int i = 0; i < paramTypes.length; i++) {
	    desc.append(getTypeDescriptor(paramTypes[i]));
	}
	desc.append(')');
	Class returnType = m.getReturnType();
	if (returnType == void.class) {	// optimization: handle void here
	    desc.append('V');
	} else {
	    desc.append(getTypeDescriptor(returnType));
	}
	return desc.toString();
    }

    /**
     * Returns the descriptor of a particular type, as appropriate for either
     * a parameter type or return type in a method descriptor.
     */
    private static String getTypeDescriptor(Class type) {
	if (type.isPrimitive()) {
	    if (type == int.class) {
		return "I";
	    } else if (type == boolean.class) {
		return "Z";
	    } else if (type == byte.class) {
		return "B";
	    } else if (type == char.class) {
		return "C";
	    } else if (type == short.class) {
		return "S";
	    } else if (type == long.class) {
		return "J";
	    } else if (type == float.class) {
		return "F";
	    } else if (type == double.class) {
		return "D";
	    } else if (type == void.class) {
		return "V";
	    } else {
		throw new Error("unrecognized primitive type: " + type);
	    }
	} else if (type.isArray()) {
	    /*
	     * According to JLS 20.3.2, the getName() method on Class does
	     * return the virtual machine type descriptor format for array
	     * classes (only); using that should be quicker than the otherwise
	     * obvious code:
	     *
	     *     return "[" + getTypeDescriptor(type.getComponentType());
	     */
	    return type.getName().replace('.', '/');
	} else {
	    return "L" + type.getName().replace('.', '/') + ";";
	}
    }

    /**
     * Returns an array containing the remote interfaces implemented
     * by the given class.
     *
     * @throws	IllegalArgumentException if remoteClass implements
     * 		any illegal remote interfaces
     * @throws	NullPointerException if remoteClass is null
     */
    public static Class[] getRemoteInterfaces(Class remoteClass) {
	ArrayList list = new ArrayList();
	getRemoteInterfaces(list, remoteClass);
	return (Class []) list.toArray(new Class[list.size()]);
    }

    /**
     * Fills the given array list with the remote interfaces implemented
     * by the given class.
     *
     * @throws	IllegalArgumentException if the specified class implements
     * 		any illegal remote interfaces
     * @throws	NullPointerException if the specified class or list is null
     */
    private static void getRemoteInterfaces(ArrayList list, Class cl) {
	Class superclass = cl.getSuperclass();
	if (superclass != null) {
	    getRemoteInterfaces(list, superclass);
	}
	
	Class[] interfaces = cl.getInterfaces();
	for (int i = 0; i < interfaces.length; i++) {
	    Class intf = interfaces[i];
	    /*
	     * If it is a remote interface (if it extends from
	     * java.rmi.Remote) and is not already in the list,
	     * then add the interface to the list.
	     */
	    if (Remote.class.isAssignableFrom(intf)) {
		if (!(list.contains(intf))) {
		    Method[] methods = intf.getMethods();
		    for (int j = 0; j < methods.length; j++) {
			checkMethod(methods[j]);
		    }
		    list.add(intf);
		}
	    }
	}
    }

    /**
     * Throws IllegalArgumentException if any superinterface of c declares a
     * method with the same name and parameter types as m that does not
     * declare RemoteException or a superclass in its throws clause, or if
     * any superinterface of c has its name in prohibitedProxyInterfaces.
     */
    public static void checkProxyRemoteMethod(Class c, Method m) {
	WeakIdentityMap map;
	synchronized (proxyRemoteMethodCache) {
	    SoftReference ref = (SoftReference) proxyRemoteMethodCache.get(c);
	    map = (ref == null) ? null : (WeakIdentityMap) ref.get();
	    if (map == null && ref != null) {
		map = new WeakIdentityMap();
		proxyRemoteMethodCache.put(c, new SoftReference(map));
	    }
	}
	if (map == null) {
	    checkProhibitedProxyInterfaces(c);
	    synchronized (proxyRemoteMethodCache) {
		SoftReference ref =
		    (SoftReference) proxyRemoteMethodCache.get(c);
		map = (ref == null) ? null : (WeakIdentityMap) ref.get();
		if (map == null) {
		    map = new WeakIdentityMap();
		    proxyRemoteMethodCache.put(c, new SoftReference(map));
		}
	    }
	}
	synchronized (map) {
	    if (map.get(m) != null) {
		return;
	    }
	}
	checkExceptions(c, m.getName(), m.getParameterTypes());
	synchronized (map) {
	    map.put(m, Boolean.TRUE);
	}
    }

    /**
     * Throws IllegalArgumentException if any superinterface of c declares a
     * method with the given name and parameter types that does not declare
     * RemoteException or a superclass in its throws clause.
     */
    private static void checkExceptions(Class c, String name, Class[] types) {
	Class[] ifaces = c.getInterfaces();
	for (int i = ifaces.length; --i >= 0; ) {
	    try {
		checkMethod(ifaces[i].getMethod(name, types));
		checkExceptions(ifaces[i], name, types);
	    } catch (NoSuchMethodException e) {
	    }
	}
    }

    /**
     * Returns collection of prohibited proxy interfaces read from resources.
     */
    private static Collection getProhibitedProxyInterfaces() {
	Collection names = new HashSet();
	names.add("javax.management.MBeanServerConnection");
	Enumeration resources;
	try {
	    resources = ClassLoader.getSystemResources(
					  prohibitedProxyInterfacesResource);
	} catch (IOException e) {
	    throw new ExceptionInInitializerError(
			new IOException(
			     "problem getting resources: " +
			     prohibitedProxyInterfacesResource).initCause(e));
	}
	while (resources.hasMoreElements()) {
	    URL url = (URL) resources.nextElement();
	    try {
		InputStream in = url.openStream();
		try {
		    BufferedReader r =
			new BufferedReader(new InputStreamReader(in, "utf-8"));
		    while (true) {
			String s = r.readLine();
			if (s == null) {
			    break;
			}
			int i = s.indexOf('#');
			if (i >= 0) {
			    s = s.substring(0, i);
			}
			s = s.trim();
			int n = s.length();
			if (n == 0) {
			    continue;
			}
			char prev = '.';
			for (i = 0; i < n; i++) {
			    char c = s.charAt(i);
			    if (prev == '.' ?
				!Character.isJavaIdentifierStart(c) :
				!(Character.isJavaIdentifierPart(c) ||
				  (c == '.' && i < n - 1)))
			    {
				throw new ExceptionInInitializerError(
					      "illegal interface name in " +
					      url + ": " + s);
			    }
			    prev = c;
			}
			names.add(s);
		    }
		} finally {
		    try {
			in.close();
		    } catch (IOException e) {
		    }
		}
	    } catch (IOException e) {
		throw new ExceptionInInitializerError(
		      new IOException("problem reading " + url).initCause(e));
	    }
	}
	return names;
    }

    /**
     * Throws IllegalArgumentException if any superinterface of c has its
     * name in prohibitedProxyInterfaces.
     */
    private static void checkProhibitedProxyInterfaces(Class c) {
	Class[] ifaces = c.getInterfaces();
	for (int i = ifaces.length; --i >= 0; ) {
	    String name = ifaces[i].getName();
	    if (prohibitedProxyInterfaces.contains(name)) {
		throw new IllegalArgumentException(
		       "prohibited proxy interface encountered: " + name);
	    }
	    checkProhibitedProxyInterfaces(ifaces[i]);
	}
    }

    /**
     * Returns the binary name of the given type without package
     * qualification.  Nested types are treated no differently from
     * top-level types, so for a nested type, the returned name will
     * still be qualified with the simple name of its enclosing
     * top-level type (and perhaps other enclosing types), the
     * separator will be '$', etc.
     **/
    public static String getUnqualifiedName(Class c) {
	String binaryName = c.getName();
	return binaryName.substring(binaryName.lastIndexOf('.') + 1);
    }

    /**
     * Returns true either if both arguments are null or if an
     * invocation of Object.equals on "subject" with "object" as the
     * argument returns true; returns false otherwise;
     **/
    public static boolean equals(Object subject, Object object) {
	return subject == null ? object == null : subject.equals(object);
    }

    /**
     * Returns true either if both arguments are null or if both
     * arguments refer to objects of the same class and an invocation
     * of Object.equals on "subject" with "object" as the argument
     * returns true; returns false otherwise.
     *
     * This method is used to compare to possibly-null references for
     * object equality when neither object's class is trusted, with
     * the restriction that only objects of the same class can be
     * considered equal.
     **/
    public static boolean sameClassAndEquals(Object subject, Object object) {
	return subject == null ? object == null :
	    object != null &&
	    subject.getClass() == object.getClass() &&
	    subject.equals(object);
    }

    /**
     * Returns true either if both arguments are null of if "subject"
     * is an instance of TrustEquivalence and an invocation of
     * TrustEquivalence.checkTrustEquivalence on "subject" with
     * "object" as the argument returns true; returns false otherwise.
     **/
    public static boolean checkTrustEquivalence(Object subject,
						Object object)
    {
	return subject == null ? object == null :
	    subject instanceof TrustEquivalence &&
	    ((TrustEquivalence) subject).checkTrustEquivalence(object);
    }

    /**
     * Returns true if proxy2 is a generated Proxy (proxy1 is assumed to
     * be one) and the classes of both proxies implement the same ordered
     * list of interfaces, and returns false otherwise.
     */
    public static boolean sameProxyClass(Object proxy1, Object proxy2) {
	return (proxy1.getClass() == proxy2.getClass() ||
		(Proxy.isProxyClass(proxy2.getClass()) &&
		 equalInterfaces(proxy1, proxy2)));
    }

    /**
     * Returns true if the interfaces implemented by obj1's class
     * are the same (and in the same order) as obj2's class.
     */
    public static boolean equalInterfaces(Object obj1, Object obj2) {
	Class[] intf1 = obj1.getClass().getInterfaces();
	Class[] intf2 = obj2.getClass().getInterfaces();
	if (intf1.length != intf2.length) {
	    return false;
	} else {
	    for (int i = 0; i < intf1.length; i++) {
		if (intf1[i] != intf2[i]) {
		    return false;
		}
	    }
	    return true;
	}
    }
    
    public static void populateContext(Collection context, InetAddress addr) {
	if (context == null) {
	    throw new NullPointerException("context is null");
	}
	if (addr != null) {
	    context.add(new ClientHostImpl(addr));
	}
    }
    
    public static void populateContext(Collection context, Subject s) {
	if (context == null) {
	    throw new NullPointerException("context is null");
	}
	context.add(new ClientSubjectImpl(s));
    }

    public static void populateContext(Collection context, boolean integrity) {
	if (context == null) {
	    throw new NullPointerException("context is null");
	}
	context.add(new IntegrityEnforcementImpl(integrity));
    }

    private static class ClientHostImpl
    	implements ClientHost
    {
	private final InetAddress addr;
	public ClientHostImpl(InetAddress addr) { this.addr = addr; }
	public InetAddress getClientHost() { return addr; }
    }

    private static class ClientSubjectImpl
    	implements ClientSubject
    {
	private final Subject s;
	private static final Permission getClientSubjectPerm =
	    new ContextPermission("net.jini.io.context.ClientSubject.getClientSubject");

	public ClientSubjectImpl(Subject s) { this.s = s; }
	public Subject getClientSubject() {
	    SecurityManager sm = System.getSecurityManager();
	    if (sm != null) {
		sm.checkPermission(getClientSubjectPerm);
	    }
	    return s;
	}
    }

    private static class IntegrityEnforcementImpl
    	implements IntegrityEnforcement
    {
	private final boolean integrity;
	public IntegrityEnforcementImpl(boolean integrity) {
	    this.integrity = integrity;
	}
	public boolean integrityEnforced() { return integrity; }
    }

    public static InetAddress getClientHost() throws ServerNotActiveException {
	ClientHost ch = (ClientHost)
	    ServerContext.getServerContextElement(ClientHost.class);
	return (ch != null) ? ch.getClientHost() : null;
    }

    public static String getClientHostString()
	throws ServerNotActiveException
    {
	InetAddress addr = getClientHost();
	return (addr != null) ? addr.toString() : null;
    }

    public static Subject getClientSubject() throws ServerNotActiveException {
	ClientSubject cs = (ClientSubject)
	    ServerContext.getServerContextElement(ClientSubject.class);
	return (cs != null) ? cs.getClientSubject() : null;
    }

    /**
     * Check for permission to access the package of the specified class.
     *
     * @throws SecurityException if a security manager exists and invoking
     * its <code>checkPackageAccess</code> method with the package name of
     * the specified class throws a <code>SecurityException</code>
     */
    public static void checkPackageAccess(Class type) {
	SecurityManager security = System.getSecurityManager();
	if (security != null) {
	    String name = type.getName();
	    int i = name.lastIndexOf('.');
	    if (i != -1) {
		security.checkPackageAccess(name.substring(0, i));
	    }
	}
    }
}
