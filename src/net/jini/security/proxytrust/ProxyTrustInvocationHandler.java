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

package net.jini.security.proxytrust;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import net.jini.core.constraint.RemoteMethodControl;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.jeri.internal.runtime.Util;

/**
 * Invocation handler for remote objects, supporting proxy trust verification
 * by clients using {@link ProxyTrustVerifier}. This invocation handler
 * contains both an underlying main proxy and a bootstrap proxy; the main
 * proxy is not expected to be considered trusted directly by clients, but
 * the bootstrap proxy is. The main proxy must be an instance of both
 * {@link RemoteMethodControl} and {@link TrustEquivalence}, and the bootstrap
 * proxy must be an instance of {@link ProxyTrust},
 * <code>RemoteMethodControl</code>, and <code>TrustEquivalence</code>. This
 * invocation handler handles most method invocations by delegating to the
 * main proxy. The bootstrap proxy is produced by the iterator returned by
 * the {@link #getProxyTrustIterator getProxyTrustIterator} method, as
 * required by <code>ProxyTrustVerifier</code>.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
@Deprecated
@AtomicSerial
public final class ProxyTrustInvocationHandler
		 implements InvocationHandler, TrustEquivalence, Serializable
{
    private static final long serialVersionUID = -3270029468290295063L;
    private static final Class[] consArgs =
					new Class[]{InvocationHandler.class};

    /**
     * The main proxy.
     *
     * @serial
     */
    private final RemoteMethodControl main;
    /**
     * The bootstrap proxy.
     *
     * @serial
     */
    private final ProxyTrust boot;

    /**
     * Creates an instance with the specified main proxy and bootstrap proxy.
     *
     * @param main the main proxy
     * @param boot the bootstrap proxy
     * @throws NullPointerException if any argument is <code>null</code>
     * @throws IllegalArgumentException if the main proxy is not an instance
     * of {@link TrustEquivalence}, or the bootstrap proxy is not an instance
     * of <code>RemoteMethodControl</code> or <code>TrustEquivalence</code>
     */
    public ProxyTrustInvocationHandler(RemoteMethodControl main,
				       ProxyTrust boot)
    {
	this(main, boot, check(main, boot));
    }
    
    public ProxyTrustInvocationHandler(GetArg arg) throws InvalidObjectException, IOException
    {
	this(true,
	     arg.get("main", null, RemoteMethodControl.class),
	     arg.get("boot", null, ProxyTrust.class)
	);
    }
    
    private ProxyTrustInvocationHandler(boolean atomicSerial,
					RemoteMethodControl main,
				        ProxyTrust boot) throws InvalidObjectException
    {
	this(main, boot, verify(main, boot));
    }
    
    private ProxyTrustInvocationHandler(RemoteMethodControl main,
					ProxyTrust boot,
					boolean check)
    {
	this.main = main;
	this.boot = boot;
    }
    
    private static boolean check(RemoteMethodControl main,
			 ProxyTrust boot)
    {
	if (main == null || boot == null) {
	    throw new NullPointerException("arguments cannot be null");
	} else if (!(main instanceof TrustEquivalence)) {
	    throw new IllegalArgumentException(
			   "main proxy must implement TrustEquivalence");
	} else if (!(boot instanceof RemoteMethodControl)) {
	    throw new IllegalArgumentException(
			 "bootstrap proxy must implement RemoteMethodControl");
	} else if (!(boot instanceof TrustEquivalence)) {
	    throw new IllegalArgumentException(
			   "bootstrap proxy must implement TrustEquivalence");
	}
	return true;
    }
  
    /**
     * Executes the specified method with the specified arguments on the
     * specified proxy, and returns the return value, if any.
     * <p>
     * If the specified method is <code>Object.equals</code>, returns
     * <code>true</code> if the argument (<code>args[0]</code>) is an
     * instance of a dynamic proxy class (that is, a class generated by
     * {@link Proxy}) that implements the same interfaces as the specified
     * proxy and this invocation handler is equal to the invocation handler
     * of that argument, and returns <code>false</code> otherwise.
     * <p>
     * If the specified method is <code>Object.toString</code>, returns
     * a string representation of the specified proxy object.
     * <p>
     * If the specified method is <code>Object.hashCode</code>, returns
     * a hash code for the specified proxy object.
     * <p>
     * If the specified method is {@link RemoteMethodControl#setConstraints
     * RemoteMethodControl.setConstraints}, returns a new proxy (an instance
     * of the same class as the specified proxy) containing an instance of
     * this class with a new main proxy and the same bootstrap proxy from
     * this handler. The new main proxy is obtained by delegating to the
     * existing main proxy of this handler (as described below). An exception
     * is thrown if the specified proxy is not an instance of a dynamic
     * proxy class containing this invocation handler.
     * <p>
     * If the specified method is
     * {@link TrustEquivalence#checkTrustEquivalence
     * TrustEquivalence.checkTrustEquivalence}, returns <code>true</code> if
     * the argument (<code>args[0]</code>) is an instance of a dynamic proxy
     * class that implements the same interfaces as the specified proxy and
     * calling the {@link #checkTrustEquivalence checkTrustEquivalence} method
     * of this invocation handler with the invocation handler of that argument
     * returns <code>true</code>, and returns <code>false</code> otherwise.
     * <p>
     * For all other methods, returns the object obtained by delegating to
     * the main proxy: the specified method is reflectively invoked on the
     * main proxy with the specified arguments, unless the method's declaring
     * class is not public but the main proxy is an instance of that
     * declaring class and the main proxy's class is public, in which case
     * the corresponding method of the main proxy's class is reflectively
     * invoked instead.
     * <p>
     * The semantics of this method are unspecified if the arguments could
     * not have been produced by an instance of some valid dynamic proxy
     * class containing this invocation handler.
     *
     * @param proxy the proxy object
     * @param method the method being invoked
     * @param args the arguments to the specified method
     * @return the value returned by executing the specified method on
     * the specified proxy with the specified arguments, or <code>null</code>
     * if the method has <code>void</code> return type
     * @throws Throwable the exception thrown by executing the specified
     * method
     * @throws IllegalArgumentException if the declaring class of the
     * specified method is not public and either the main proxy is not an
     * instance of that declaring class or the main proxy's class is not
     * public
     */
    public Object invoke(Object proxy, Method method, Object[] args)
	throws Throwable
    {
	Class decl = method.getDeclaringClass();
	if (decl == Object.class) {
	    String name = method.getName();
	    if (name.equals("equals")) {
		Object obj = args[0];
		if (proxy == obj ||
		    (obj != null && Util.sameProxyClass(proxy, obj) &&
		     equals(Proxy.getInvocationHandler(obj))))
		{
		    return Boolean.TRUE;
		}
		return Boolean.FALSE;
	    } else if (name.equals("toString")) {
		return proxyToString(proxy);
	    } else if (name.equals("hashCode")) {
		return Integer.valueOf(hashCode());
	    }
	    throw new IllegalArgumentException("unexpected Object method");
	} else if (decl == RemoteMethodControl.class &&
		   method.getName().equals("setConstraints"))
	{
	    if (Proxy.getInvocationHandler(proxy) != this) {
		throw new IllegalArgumentException("wrong invocation handler");
	    }
	    InvocationHandler handler =
		new ProxyTrustInvocationHandler(
			   (RemoteMethodControl) invoke0(method, args), boot);
	    return Proxy.newProxyInstance(proxy.getClass().getClassLoader(),
					  proxy.getClass().getInterfaces(),
					  handler);
	} else if (decl == TrustEquivalence.class) {
	    if (!method.getName().equals("checkTrustEquivalence")) {
		throw new AssertionError("unknown TrustEquivalence method");
	    }
	    Object obj = args[0];
	    if (proxy == obj ||
		(obj != null && Util.sameProxyClass(proxy, obj) &&
		 checkTrustEquivalence(Proxy.getInvocationHandler(obj))))
	    {
		return Boolean.TRUE;
	    }
	    return Boolean.FALSE;
	}
	return invoke0(method, args);
    }

    /**
     * Reflectively invoke the method on the main proxy, unless the method's
     * declaring class is not public but the main proxy's class is public, in
     * which case invoke the corresponding method from the main proxy's class
     * instead.
     */
    private Object invoke0(Method m, Object[] args) throws Throwable {
	Class iface = m.getDeclaringClass();
	if (!Modifier.isPublic(iface.getModifiers())) {
	    Class impl = main.getClass();
	    if (Modifier.isPublic(impl.getModifiers()) &&
		iface.isInstance(main))
	    {
		try {
		    m = impl.getMethod(m.getName(), m.getParameterTypes());
		} catch (Exception e) {
		}
	    }
	}
	try {
	    return m.invoke(main, args);
	} catch (InvocationTargetException e) {
	    throw e.getTargetException();
	} catch (IllegalAccessException e) {
	    throw new IllegalArgumentException().initCause(e);
	}
    }

    /**
     * Returns <code>true</code> if the argument is an instance of this
     * class, and calling the <code>checkTrustEquivalence</code> method on
     * the main proxy of this invocation handler, passing the main proxy of
     * the argument, returns <code>true</code>, and calling the
     * <code>checkTrustEquivalence</code> method on the bootstrap proxy of
     * this invocation handler, passing the bootstrap proxy of the argument,
     * returns <code>true</code>, and returns <code>false</code> otherwise.
     */
    public boolean checkTrustEquivalence(Object obj) {
	if (this == obj) {
	    return true;
	} else if (!(obj instanceof ProxyTrustInvocationHandler)) {
	    return false;
	}
	ProxyTrustInvocationHandler oh = (ProxyTrustInvocationHandler) obj;
	return (((TrustEquivalence) main).checkTrustEquivalence(oh.main) &&
		((TrustEquivalence) boot).checkTrustEquivalence(oh.boot));
    }

    /**
     * Returns <code>true</code> if the argument is an instance of this
     * class with the same main proxy and the same bootstrap proxy, and
     * <code>false</code> otherwise.
     */
    public boolean equals(Object obj) {
	if (this == obj) {
	    return true;
	} else if (!(obj instanceof ProxyTrustInvocationHandler)) {
	    return false;
	}
	ProxyTrustInvocationHandler oh = (ProxyTrustInvocationHandler) obj;
	return main.equals(oh.main) && boot.equals(oh.boot);
    }

    /**
     * Returns a hash code value for this object.
     */
    public int hashCode() {
	return main.hashCode() + boot.hashCode();
    }

    /**
     * Returns a string representation of this object.
     */
    public String toString() {
	return ("ProxyTrustInvocationHandler[main: " + main +
		", boot: " + boot + "]");
    }

    /**
     * Returns a string representation for a proxy that uses this invocation
     * handler.
     */
    private String proxyToString(Object proxy) {
	Class[] interfaces = proxy.getClass().getInterfaces();
	Class iface = null;
	for (int i = interfaces.length; --i >= 0; iface = interfaces[i]) {
	    if (interfaces[i] == RemoteMethodControl.class) {
		break;
	    }
	}
	if (iface == null) {
	    return "Proxy[" + this + "]";
	}
	String n = iface.getName();
	int dot = n.lastIndexOf('.');
	if (dot >= 0) {
	    n = n.substring(dot + 1);
	}
	return "Proxy[" + n + "," + this + "]";
    }

    /**
     * Returns an iterator that produces the bootstrap proxy as the only
     * element of the iteration.
     *
     * @return an iterator that produces the bootstrap proxy as the only
     * element of the iteration
     */
    protected ProxyTrustIterator getProxyTrustIterator() {
	return new SingletonProxyTrustIterator(boot);
    }

    /**
     * Verifies that the main proxy is an instance of
     * {@link TrustEquivalence}, and that the bootstrap proxy is an instance
     * of both {@link RemoteMethodControl} and <code>TrustEquivalence</code>.
     *
     * @throws InvalidObjectException if the main proxy is not an instance of
     * <code>TrustEquivalence</code>, or the bootstrap proxy is not an
     * instance of both <code>RemoteMethodControl</code> and
     * <code>TrustEquivalence</code>
     */
    private void readObject(ObjectInputStream s)
	throws IOException, ClassNotFoundException
    {
	s.defaultReadObject();
	verify(main, boot);
    }
      
    private static boolean verify(RemoteMethodControl main,
				  ProxyTrust boot) throws InvalidObjectException
    {
	if (!(main instanceof TrustEquivalence)) {
	    throw new InvalidObjectException(
			   "main proxy must implement TrustEquivalence");
	} else if (!(boot instanceof RemoteMethodControl)) {
	    throw new InvalidObjectException(
			"bootstrap proxy must implement RemoteMethodControl");
	} else if (!(boot instanceof TrustEquivalence)) {
	    throw new InvalidObjectException(
			   "bootstrap proxy must implement TrustEquivalence");
	}
	return true;
    }

}
